package dev.kultivator.sect;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.fx.Fx;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /sect} and its alias {@code /sekte} (the same tree built twice): create, invite, join, leave, kick, disband,
 * info, list, chat and spar, plus an op-only {@code debug addmember} for testing alone. Invitations (5 minutes) and
 * disband confirmations (10 seconds) are only kept in memory.
 */
final class SectCommands {

	static final int INVITE_TICKS = 5 * 60 * 20;
	static final int DISBAND_CONFIRM_TICKS = 10 * 20;
	private static final int GREEN = 0x6CFF8A;

	/** Invited player -> (sect key -> server tick when the invitation runs out). */
	private static final Map<UUID, Map<String, Long>> INVITES = new HashMap<>();
	/** Sect Master -> server tick until which repeating {@code disband} confirms it. */
	private static final Map<UUID, Long> DISBAND_CONFIRM = new HashMap<>();

	private static final SuggestionProvider<CommandSourceStack> SECT_NAMES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(SectData.get(ctx.getSource().getServer()).all().stream().map(Sect::name), builder);

	private static final SuggestionProvider<CommandSourceStack> INVITED = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> names = new ArrayList<>();
		if (player != null) {
			MinecraftServer server = ctx.getSource().getServer();
			Map<String, Long> invites = INVITES.get(player.getUUID());
			if (invites != null) {
				SectData data = SectData.get(server);
				long now = server.getTickCount();
				invites.forEach((key, until) -> {
					Sect sect = data.byName(key);
					if (sect != null && until > now) {
						names.add(sect.name());
					}
				});
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> MEMBERS = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> names = new ArrayList<>();
		if (player != null) {
			Sect sect = SectData.get(ctx.getSource().getServer()).sectOf(player.getUUID());
			if (sect != null) {
				for (Sect.Member member : sect.members()) {
					if (!member.id().equals(player.getUUID())) {
						names.add(member.name());
					}
				}
			}
		}
		return SharedSuggestionProvider.suggest(names, builder);
	};

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(build("sect"));
		dispatcher.register(build("sekte"));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).executes(ctx -> overview(ctx.getSource()));
		root.then(Commands.literal("help").executes(ctx -> help(ctx.getSource())));
		root.then(Commands.literal("create")
				.then(Commands.argument("name", StringArgumentType.greedyString())
						.executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));
		root.then(Commands.literal("invite")
				.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> invite(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));
		root.then(Commands.literal("join")
				.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(INVITED)
						.executes(ctx -> join(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));
		root.then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())));
		root.then(Commands.literal("kick")
				.then(Commands.argument("player", StringArgumentType.word()).suggests(MEMBERS)
						.executes(ctx -> kick(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));
		root.then(Commands.literal("disband").executes(ctx -> disband(ctx.getSource())));
		root.then(Commands.literal("info")
				.executes(ctx -> infoOwn(ctx.getSource()))
				.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(SECT_NAMES)
						.executes(ctx -> infoNamed(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));
		root.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));
		root.then(Commands.literal("chat")
				.then(Commands.argument("message", StringArgumentType.greedyString())
						.executes(ctx -> chat(ctx.getSource(), StringArgumentType.getString(ctx, "message")))));
		root.then(Commands.literal("spar")
				.then(Commands.literal("accept").executes(ctx -> Sparring.accept(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("decline").executes(ctx -> Sparring.decline(ctx.getSource().getPlayerOrException())))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> Sparring.challenge(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player")))));
		root.then(Commands.literal("debug")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("addmember")
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(ctx -> debugAddMember(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))));
		return root;
	}

	// ================================================================== overview and help

	private static int overview(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Sect sect = SectData.get(source.getServer()).sectOf(player.getUUID());
		return sect == null ? help(source) : info(source, sect, player);
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.sects.help.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		helpLine(source, "create", "/sect create ");
		helpLine(source, "invite", "/sect invite ");
		helpLine(source, "join", "/sect join ");
		helpLine(source, "leave", "/sect leave");
		helpLine(source, "kick", "/sect kick ");
		helpLine(source, "disband", "/sect disband");
		helpLine(source, "info", "/sect info");
		helpLine(source, "list", "/sect list");
		helpLine(source, "chat", "/sect chat ");
		helpLine(source, "spar", "/sect spar ");
		helpLine(source, "spar_answer", "/sect spar accept");
		source.sendSuccess(() -> Component.translatable("kultivator.sects.help.footer").withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	/** One help line; clicking it puts the command into the chat box. */
	private static void helpLine(CommandSourceStack source, String key, String suggestion) {
		MutableComponent line = Component.translatable("kultivator.sects.help." + key).withStyle(style -> style.withColor(Sects.ACCENT)
				.withClickEvent(new ClickEvent.SuggestCommand(suggestion))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(suggestion.trim()))));
		source.sendSuccess(() -> line, false);
	}

	// ================================================================== founding and membership

	private static int create(CommandSourceStack source, String rawName) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		SectData data = SectData.get(server);
		Sect current = data.sectOf(player.getUUID());
		if (current != null) {
			return fail(player, Component.translatable("kultivator.sects.already_member", current.coloredName()));
		}
		String name = Sect.normalizeName(rawName);
		if (!Sect.validName(name)) {
			return fail(player, Component.translatable("kultivator.sects.create.invalid_name", Sect.MIN_NAME, Sect.MAX_NAME));
		}
		Sect existing = data.byName(name);
		if (existing != null) {
			return fail(player, Component.translatable("kultivator.sects.create.taken", existing.coloredName()));
		}
		Sect sect = data.create(name, player.getUUID(), player.getPlainTextName(), gameTime(server));
		INVITES.remove(player.getUUID());
		Msg.chat(player, Component.translatable("kultivator.sects.create.done", sect.coloredName()).withColor(Sects.ACCENT));
		Component news = Component.translatable("kultivator.sects.create.broadcast", player.getDisplayName(), sect.tag()).withColor(Sects.ACCENT);
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != player) {
				Msg.chat(other, news);
			}
		}
		Advancements.grant(player, "sect_founded");
		celebrate(player, sect, true);
		return 1;
	}

	private static int invite(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		SectData data = SectData.get(server);
		Sect sect = data.sectOf(player.getUUID());
		if (sect == null) {
			return fail(player, Component.translatable("kultivator.sects.not_in_sect"));
		}
		if (!sect.isMaster(player.getUUID())) {
			return fail(player, Component.translatable("kultivator.sects.not_master"));
		}
		if (target == player) {
			return fail(player, Component.translatable("kultivator.sects.invite.self"));
		}
		Sect theirs = data.sectOf(target.getUUID());
		if (theirs == sect) {
			return fail(player, Component.translatable("kultivator.sects.invite.already_member", target.getDisplayName()));
		}
		if (theirs != null) {
			return fail(player, Component.translatable("kultivator.sects.invite.other_sect", target.getDisplayName(), theirs.coloredName()));
		}
		INVITES.computeIfAbsent(target.getUUID(), id -> new HashMap<>()).put(sect.key(), server.getTickCount() + (long) INVITE_TICKS);
		int minutes = INVITE_TICKS / 1200;
		Msg.chat(player, Component.translatable("kultivator.sects.invite.sent", target.getDisplayName(), sect.coloredName(), minutes)
				.withColor(Sects.ACCENT));
		Msg.chat(target, Component.translatable("kultivator.sects.invite.received", player.getDisplayName(), sect.coloredName(), minutes)
				.withColor(Sects.ACCENT));
		target.sendSystemMessage(Component.literal("   ").append(Sects.button("kultivator.sects.invite.button", "/sect join " + sect.name(),
				Component.translatable("kultivator.sects.invite.button_hover", sect.name()), GREEN)));
		target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, 0.8F, 1.0F);
		return 1;
	}

	private static int join(CommandSourceStack source, String rawName) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		SectData data = SectData.get(server);
		Sect current = data.sectOf(player.getUUID());
		if (current != null) {
			return fail(player, Component.translatable("kultivator.sects.already_member", current.coloredName()));
		}
		Sect sect = data.byName(rawName);
		if (sect == null) {
			return fail(player, Component.translatable("kultivator.sects.unknown", Sect.normalizeName(rawName)));
		}
		Map<String, Long> invites = INVITES.get(player.getUUID());
		Long until = invites == null ? null : invites.get(sect.key());
		if (until == null || until <= server.getTickCount()) {
			return fail(player, Component.translatable("kultivator.sects.join.no_invite", sect.coloredName()));
		}
		INVITES.remove(player.getUUID());
		data.addMember(sect, player.getUUID(), player.getPlainTextName(), gameTime(server));
		Msg.chat(player, Component.translatable("kultivator.sects.join.done", sect.coloredName()).withColor(Sects.ACCENT));
		Sects.tell(server, sect, Component.translatable("kultivator.sects.join.announce", player.getDisplayName()), Set.of(player.getUUID()));
		Advancements.grant(player, "sect_joined");
		Sects.checkFlourishing(server, sect);
		celebrate(player, sect, false);
		return 1;
	}

	private static int leave(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		SectData data = SectData.get(server);
		Sect sect = data.sectOf(player.getUUID());
		if (sect == null) {
			return fail(player, Component.translatable("kultivator.sects.not_in_sect"));
		}
		DISBAND_CONFIRM.remove(player.getUUID());
		SectData.LeaveResult result = data.leave(sect, player.getUUID());
		if (result.disbanded()) {
			forgetSect(sect.key());
			Msg.chat(player, Component.translatable("kultivator.sects.leave.disbanded", sect.coloredName()).withColor(Sects.ACCENT));
			return 1;
		}
		Msg.chat(player, Component.translatable("kultivator.sects.leave.done", sect.coloredName()).withColor(Sects.ACCENT));
		Sects.tell(server, sect, Component.translatable("kultivator.sects.leave.announce", player.getDisplayName()), Set.of(player.getUUID()));
		Sect.Member heir = result.newMaster();
		if (heir != null) {
			Sects.tell(server, sect, Component.translatable("kultivator.sects.leave.new_master", Sect.memberName(heir)),
					Set.of(player.getUUID(), heir.id()));
			ServerPlayer heirPlayer = Sects.online(server, heir.id());
			if (heirPlayer != null) {
				Msg.chat(heirPlayer, Component.translatable("kultivator.sects.leave.you_master", sect.coloredName()).withColor(0xFFD86A));
			}
		}
		return 1;
	}

	private static int kick(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		SectData data = SectData.get(server);
		Sect sect = data.sectOf(player.getUUID());
		if (sect == null) {
			return fail(player, Component.translatable("kultivator.sects.not_in_sect"));
		}
		if (!sect.isMaster(player.getUUID())) {
			return fail(player, Component.translatable("kultivator.sects.not_master"));
		}
		Sect.Member member = sect.memberByName(name);
		if (member == null) {
			return fail(player, Component.translatable("kultivator.sects.kick.not_member", name));
		}
		if (member.id().equals(player.getUUID())) {
			return fail(player, Component.translatable("kultivator.sects.kick.self"));
		}
		data.removeMember(sect, member.id());
		Component who = Component.literal(member.name());
		Msg.chat(player, Component.translatable("kultivator.sects.kick.done", who).withColor(Sects.ACCENT));
		Sects.tell(server, sect, Component.translatable("kultivator.sects.kick.announce", who), Set.of(player.getUUID()));
		ServerPlayer kicked = Sects.online(server, member.id());
		if (kicked != null) {
			Msg.chat(kicked, Component.translatable("kultivator.sects.kick.you", sect.coloredName()).withStyle(ChatFormatting.RED));
		}
		return 1;
	}

	private static int disband(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		SectData data = SectData.get(server);
		Sect sect = data.sectOf(player.getUUID());
		if (sect == null) {
			return fail(player, Component.translatable("kultivator.sects.not_in_sect"));
		}
		if (!sect.isMaster(player.getUUID())) {
			return fail(player, Component.translatable("kultivator.sects.not_master"));
		}
		long now = server.getTickCount();
		Long until = DISBAND_CONFIRM.get(player.getUUID());
		if (until == null || until < now) {
			DISBAND_CONFIRM.put(player.getUUID(), now + DISBAND_CONFIRM_TICKS);
			Msg.chat(player, Component.translatable("kultivator.sects.disband.confirm", sect.coloredName(), DISBAND_CONFIRM_TICKS / 20)
					.withStyle(ChatFormatting.RED));
			player.sendSystemMessage(Component.literal("   ").append(Sects.button("kultivator.sects.disband.button", "/sect disband",
					Component.translatable("kultivator.sects.disband.button_hover", sect.name()), 0xFF7A6A)));
			return 1;
		}
		DISBAND_CONFIRM.remove(player.getUUID());
		List<ServerPlayer> members = Sects.onlineMembers(server, sect);
		data.disband(sect);
		forgetSect(sect.key());
		Component line = Component.translatable("kultivator.sects.disband.done", sect.coloredName()).withColor(Sects.ACCENT);
		for (ServerPlayer member : members) {
			Msg.chat(member, line);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.9F);
		return 1;
	}

	// ================================================================== info and list

	private static int infoOwn(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Sect sect = SectData.get(source.getServer()).sectOf(player.getUUID());
		if (sect == null) {
			return fail(player, Component.translatable("kultivator.sects.info.none"));
		}
		return info(source, sect, player);
	}

	private static int infoNamed(CommandSourceStack source, String rawName) {
		Sect sect = SectData.get(source.getServer()).byName(rawName);
		if (sect == null) {
			source.sendFailure(Component.translatable("kultivator.sects.unknown", Sect.normalizeName(rawName)));
			return 0;
		}
		return info(source, sect, source.getPlayer());
	}

	private static int info(CommandSourceStack source, Sect sect, ServerPlayer viewer) {
		MinecraftServer server = source.getServer();
		long days = Math.max(0L, (gameTime(server) - sect.created()) / 24000L);
		source.sendSuccess(() -> Component.translatable("kultivator.sects.info.header", sect.coloredName()), false);
		source.sendSuccess(() -> Component.translatable("kultivator.sects.info.master",
				Component.literal(sect.masterName()).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GRAY), false);
		source.sendSuccess(() -> Component.translatable("kultivator.sects.info.age", days).withStyle(ChatFormatting.GRAY), false);
		source.sendSuccess(() -> Component.translatable("kultivator.sects.info.members", sect.size()).withStyle(ChatFormatting.GRAY), false);
		for (Sect.Member member : sect.members()) {
			boolean online = Sects.online(server, member.id()) != null;
			Component status = online
					? Component.translatable("kultivator.sects.info.online").withStyle(ChatFormatting.GREEN)
					: Component.translatable("kultivator.sects.info.offline").withStyle(ChatFormatting.DARK_GRAY);
			Component title = member.titleName().copy().withStyle(member.isMaster() ? ChatFormatting.GOLD : ChatFormatting.AQUA);
			source.sendSuccess(() -> Component.translatable("kultivator.sects.info.member", Sect.memberName(member), title, status)
					.withStyle(ChatFormatting.GRAY), false);
		}
		if (viewer != null && sect.isMember(viewer.getUUID())) {
			source.sendSuccess(() -> Component.translatable("kultivator.sects.info.bonus", (int) Sects.MEDITATION_RANGE)
					.withColor(Sects.ACCENT), false);
		}
		return 1;
	}

	private static int list(CommandSourceStack source) {
		List<Sect> sects = new ArrayList<>(SectData.get(source.getServer()).all());
		if (sects.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("kultivator.sects.list.empty").withColor(Sects.ACCENT), false);
			return 1;
		}
		sects.sort(Comparator.comparingInt(Sect::size).reversed().thenComparing(Sect::name, String.CASE_INSENSITIVE_ORDER));
		int count = sects.size();
		source.sendSuccess(() -> Component.translatable("kultivator.sects.list.header", count).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (Sect sect : sects) {
			MutableComponent line = Component.literal(" ").append(sect.tag()).append(Component.literal(" "))
					.append(Component.translatable("kultivator.sects.list.line", sect.masterName(), sect.size()).withStyle(ChatFormatting.GRAY));
			source.sendSuccess(() -> line, false);
		}
		return count;
	}

	// ================================================================== chat

	private static int chat(CommandSourceStack source, String message) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		Sect sect = SectData.get(server).sectOf(player.getUUID());
		if (sect == null) {
			return fail(player, Component.translatable("kultivator.sects.not_in_sect"));
		}
		String text = message.trim();
		if (text.isEmpty()) {
			return 0;
		}
		Sect.Member me = sect.member(player.getUUID());
		MutableComponent name = me == null ? Component.literal(player.getPlainTextName()) : Sect.memberName(me);
		MutableComponent line = Component.empty().append(sect.tag()).append(Component.literal(" "))
				.append(Component.translatable("kultivator.sects.chat.format", name, Component.literal(text).withStyle(ChatFormatting.WHITE))
						.withStyle(ChatFormatting.GRAY));
		List<ServerPlayer> members = Sects.onlineMembers(server, sect);
		for (ServerPlayer member : members) {
			member.sendSystemMessage(line);
		}
		server.sendSystemMessage(line);
		if (members.size() <= 1) {
			Msg.chat(player, Component.translatable("kultivator.sects.chat.alone").withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	// ================================================================== debug

	/** Adds an offline test member (a made-up player) to the caller's sect, to try sect features alone. */
	private static int debugAddMember(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		SectData data = SectData.get(server);
		Sect sect = data.sectOf(player.getUUID());
		if (sect == null) {
			return fail(player, Component.translatable("kultivator.sects.not_in_sect"));
		}
		UUID id = UUIDUtil.createOfflinePlayerUUID("KultivatorTest-" + name);
		if (data.sectOf(id) != null || sect.memberByName(name) != null) {
			return fail(player, Component.translatable("kultivator.sects.invite.already_member", Component.literal(name)));
		}
		data.addMember(sect, id, name, gameTime(server));
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.sects.debug.added", name, sect.coloredName())), false);
		Sects.checkFlourishing(server, sect);
		return 1;
	}

	// ================================================================== helpers

	/** Forgets everything about a player that only lasts while they are online. */
	static void forget(UUID id) {
		DISBAND_CONFIRM.remove(id);
	}

	static void clear() {
		INVITES.clear();
		DISBAND_CONFIRM.clear();
	}

	/** Drops the invitations to a sect that no longer exists. */
	private static void forgetSect(String key) {
		INVITES.values().forEach(map -> map.remove(key));
		INVITES.values().removeIf(Map::isEmpty);
	}

	private static int fail(ServerPlayer player, Component message) {
		Msg.chat(player, message.copy().withStyle(ChatFormatting.RED));
		return 0;
	}

	private static long gameTime(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** A small flourish in the sect's colour for founding (big) or joining (small). */
	private static void celebrate(ServerPlayer player, Sect sect, boolean founded) {
		ServerLevel level = player.level();
		Vec3 pos = player.position().add(0.0D, 1.0D, 0.0D);
		Fx.burst(level, pos, sect.color(), founded ? 50 : 24, founded ? 2.5F : 1.5F);
		Fx.ring(level, player.position(), sect.color(), founded ? 3.0F : 2.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), founded ? SoundEvents.BELL_BLOCK : SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.PLAYERS, 1.0F, founded ? 0.8F : 1.2F);
	}

	private SectCommands() {
	}
}
