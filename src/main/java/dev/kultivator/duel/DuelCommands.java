package dev.kultivator.duel;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.blade.DuelPose;
import dev.kultivator.duel.clash.Posture;
import dev.kultivator.duel.net.DuelNet;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRecords;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player commands of the sword duel (DESIGN 35.8.4): {@code /kultivasi duel tantang [entitas] [bebas|handicap|tekan]},
 * {@code terima [id]}, {@code tolak [id]}, {@code tarik}, {@code menyerah}, {@code tunda}, {@code hormat},
 * {@code catatan [pemain]}, {@code layar}, {@code murid <murid1> <murid2>}. Without a target, {@code tantang} takes
 * the entity the player looks at; the full challenge page is Gulungan › Dunia › Duel (DESIGN 43.5).
 */
final class DuelCommands {

	static void add(LiteralArgumentBuilder<CommandSourceStack> root) {
		root.then(Commands.literal("duel")
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("tantang")
						.executes(ctx -> challengeLooked(self(ctx), null))
						.then(Commands.argument("target", EntityArgument.entity())
								.executes(ctx -> challenge(self(ctx), EntityArgument.getEntity(ctx, "target"), null))
								.then(Commands.argument("ranah", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("bebas", "handicap", "tekan"), builder))
										.executes(ctx -> challenge(self(ctx), EntityArgument.getEntity(ctx, "target"),
												StringArgumentType.getString(ctx, "ranah"))))))
				.then(Commands.literal("terima")
						.executes(ctx -> answer(self(ctx), 0, DuelIds.ANSWER_TERIMA))
						.then(Commands.argument("id", IntegerArgumentType.integer(0))
								.executes(ctx -> answer(self(ctx), IntegerArgumentType.getInteger(ctx, "id"), DuelIds.ANSWER_TERIMA))))
				.then(Commands.literal("tolak")
						.executes(ctx -> answer(self(ctx), 0, DuelIds.ANSWER_TOLAK))
						.then(Commands.argument("id", IntegerArgumentType.integer(0))
								.executes(ctx -> answer(self(ctx), IntegerArgumentType.getInteger(ctx, "id"), DuelIds.ANSWER_TOLAK))))
				.then(Commands.literal("tarik").executes(ctx -> answer(self(ctx), 0, DuelIds.ANSWER_TARIK)))
				.then(Commands.literal("menyerah").executes(ctx -> answer(self(ctx), 0, DuelIds.ANSWER_MENYERAH)))
				.then(Commands.literal("tunda").executes(ctx -> answer(self(ctx), 0, DuelIds.ANSWER_TUNDA)))
				.then(Commands.literal("hormat").executes(ctx -> bow(self(ctx))))
				.then(Commands.literal("catatan")
						.executes(ctx -> records(ctx.getSource(), self(ctx)))
						.then(Commands.argument("pemain", EntityArgument.player())
								.executes(ctx -> records(ctx.getSource(), EntityArgument.getPlayer(ctx, "pemain")))))
				.then(Commands.literal("layar").executes(ctx -> screen(self(ctx))))
				.then(Commands.literal("murid")
						.then(Commands.argument("murid1", StringArgumentType.string())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ownDiscipleNames(ctx.getSource()), builder))
								.then(Commands.argument("murid2", StringArgumentType.string())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ownDiscipleNames(ctx.getSource()), builder))
										.executes(ctx -> watch(self(ctx), StringArgumentType.getString(ctx, "murid1"),
												StringArgumentType.getString(ctx, "murid2")))))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.duel.cmd.help").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	// ================================================================ challenge

	private static int challengeLooked(ServerPlayer player, @Nullable String realm) {
		LivingEntity looked = looked(player, DuelConfig.get().challengeRange);
		if (looked == null) {
			Msg.chat(player, Component.translatable("kultivator.duel.cmd.no_look").withColor(0xE0A060));
			return 0;
		}
		return challenge(player, looked, realm);
	}

	private static int challenge(ServerPlayer player, Entity target, @Nullable String realm) {
		if (!(target instanceof LivingEntity living)) {
			Msg.chat(player, Component.translatable("kultivator.duel.msg.refused", Component.translatable("kultivator.duel.reason.no_target"))
					.withColor(0xE0A060));
			return 0;
		}
		DuelRuleSet rules = DuelRuleSet.suggested(Posture.realm(player) - Posture.realm(living)).withRadius(DuelConfig.get().ringRadiusDefault);
		DuelRuleSet.RealmRule rule = realmRule(realm);
		if (rule != null) {
			rules = rules.withRealm(rule);
		}
		Component why = DuelChallenges.challenge(player, living, null, rules);
		if (why != null) {
			Msg.chat(player, Component.translatable("kultivator.duel.msg.refused", why).withColor(0xE0A060));
			return 0;
		}
		return 1;
	}

	static @Nullable DuelRuleSet.RealmRule realmRule(@Nullable String id) {
		if (id == null) {
			return null;
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "bebas", "free" -> DuelRuleSet.RealmRule.BEBAS;
			case "handicap" -> DuelRuleSet.RealmRule.HANDICAP;
			case "tekan", "tekan_ranah", "suppress" -> DuelRuleSet.RealmRule.TEKAN_RANAH;
			default -> null;
		};
	}

	/** The living entity {@code player} looks at within {@code range} (an eye ray; nothing is changed). */
	static @Nullable LivingEntity looked(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range));
		AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1.0D);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
				entity -> entity instanceof LivingEntity && entity.isAlive() && entity != player && !entity.isSpectator(), range * range);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	private static int answer(ServerPlayer player, int id, int answer) {
		DuelChallenges.answer(player, id, answer);
		return 1;
	}

	// ================================================================ watched duel of two disciples

	private static List<String> ownDiscipleNames(CommandSourceStack source) {
		List<String> names = new ArrayList<>();
		if (source.getEntity() instanceof ServerPlayer player) {
			for (DiscipleEntity disciple : ownDisciples(player)) {
				String name = disciple.getName().getString();
				names.add(name.contains(" ") ? "\"" + name + "\"" : name);
			}
		}
		return names;
	}

	private static List<DiscipleEntity> ownDisciples(ServerPlayer player) {
		double range = DuelConfig.get().challengeRange;
		return player.level().getEntitiesOfClass(DiscipleEntity.class, player.getBoundingBox().inflate(range),
				d -> d.isAlive() && d.isOwnedBy(player) && d.role() == DiscipleEntity.Role.DISCIPLE);
	}

	private static @Nullable DiscipleEntity byName(ServerPlayer player, String name) {
		for (DiscipleEntity disciple : ownDisciples(player)) {
			if (disciple.getName().getString().equalsIgnoreCase(name)) {
				return disciple;
			}
		}
		return null;
	}

	private static int watch(ServerPlayer player, String first, String second) {
		DiscipleEntity a = byName(player, first);
		DiscipleEntity b = byName(player, second);
		if (a == null || b == null || a == b) {
			Msg.chat(player, Component.translatable("kultivator.duel.cmd.no_disciple").withColor(0xE0A060));
			return 0;
		}
		DuelRuleSet rules = DuelRuleSet.suggested(Posture.realm(a) - Posture.realm(b)).withRadius(DuelConfig.get().ringRadiusDefault)
				.withoutStake().withCrowd(true);
		Component why = DuelChallenges.challenge(player, a, b, rules);
		if (why != null) {
			Msg.chat(player, Component.translatable("kultivator.duel.msg.refused", why).withColor(0xE0A060));
			return 0;
		}
		return 1;
	}

	// ================================================================ the rest

	/** The salute pose (fist and palm) for the player's own view and the duel opponent's client. */
	private static int bow(ServerPlayer player) {
		DuelPayloads.DuelPreview pose = new DuelPayloads.DuelPreview(DuelIds.PV_POSE, DuelPose.HORMAT.ordinal(), player.getId(), 0);
		DuelNet.send(player, pose);
		DuelSession session = DuelSessions.byEntity(player);
		if (session != null && session.partnerOf(player) instanceof ServerPlayer other) {
			Msg.chat(other, Component.translatable("kultivator.duel.msg.bow", player.getDisplayName()).withColor(0xE8D9A8));
		}
		return 1;
	}

	/** Opens Gulungan › Dunia › Duel (43.14 S1); the chat line stays for a client without the mod. */
	private static int screen(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, Payloads.OpenScreen.TYPE)) {
			ServerPlayNetworking.send(player, new Payloads.OpenScreen(Payloads.OpenScreen.DUNIA, Payloads.OpenScreen.DUNIA_DUEL));
		} else {
			Msg.chat(player, Component.translatable("kultivator.duel.cmd.screen").withColor(0xE8D9A8));
		}
		return 1;
	}

	private static int records(CommandSourceStack source, ServerPlayer who) {
		DuelRecords records = DuelRecords.get(source.getServer());
		DuelRecords.Entry entry = records.entry(who.getUUID());
		int honour = records.honour(who.getUUID());
		if (entry == null) {
			source.sendSuccess(() -> Component.translatable("kultivator.duel.cmd.records_none", who.getDisplayName(), honour,
					DuelRecords.title(honour)).withStyle(ChatFormatting.GRAY), false);
			return 1;
		}
		source.sendSuccess(() -> Component.translatable("kultivator.duel.cmd.records", who.getDisplayName(), entry.wins, entry.losses, entry.draws,
				honour, DuelRecords.title(honour)).withStyle(ChatFormatting.GOLD), false);
		source.sendSuccess(() -> Component.translatable("kultivator.duel.cmd.records_more", entry.perfects, entry.deathblows, entry.bindsWon,
				entry.maxChain).withStyle(ChatFormatting.YELLOW), false);
		for (DuelRecords.Recent recent : entry.recent) {
			String key = recent.result() > 0 ? "kultivator.duel.cmd.recent_win" : recent.result() < 0 ? "kultivator.duel.cmd.recent_loss"
					: "kultivator.duel.cmd.recent_draw";
			long days = Math.max(0L, (who.level().getGameTime() - recent.time()) / 24000L);
			source.sendSuccess(() -> Component.translatable(key, recent.name(), days).withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private DuelCommands() {
	}
}
