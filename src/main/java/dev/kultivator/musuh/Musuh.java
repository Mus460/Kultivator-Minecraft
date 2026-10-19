package dev.kultivator.musuh;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kultivator Liar and Serbuan Sekte Iblis: rogue cultivators, ambushes, bandit camps and sect raids (DESIGN 25.4, owned by
 * P8-musuh). Registers the entity, the pouches, the events and the spine providers, runs one server tick for the module
 * and answers the Dunia screen's {@code MUSUH} actions. Nothing in this package places, breaks or changes a block.
 */
public final class Musuh {

	/** Encounter kinds in the player's log ({@code extra.dunia.musuh_log}). */
	public static final int KIND_AMBUSH = 0;
	public static final int KIND_CAMP = 1;
	public static final int KIND_RAID = 2;
	public static final int ENCOUNTER_LOG = 10;

	private static @Nullable MinecraftServer server;

	/** Registers the entity, events and spine providers; called from {@code Kultivator.onInitialize} before {@code ModItems.init()}. */
	public static void init() {
		RogueCultivators.init();
		LootPouches.init();
		RogueSkills.init();
		Ambushes.init();
		Camps.init();
		SectRaids.init();
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
		ServerTickEvents.END_SERVER_TICK.register(Musuh::serverTick);
	}

	private static void serverTick(MinecraftServer current) {
		long tick = current.getTickCount();
		RogueCultivators.tick(current, tick);
		RogueSkills.tick(current, tick);
		Ambushes.tick(current, tick);
		Camps.tick(current, tick);
		SectRaids.tick(current, tick);
	}

	/** The running server (null before it started). */
	static @Nullable MinecraftServer server() {
		return server;
	}

	/** {@code /kultivasi serbuan info} and {@code debug perampok info} (DESIGN 25.11). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("serbuan")
				.then(Commands.literal("info").executes(ctx -> raidInfo(ctx.getSource().getPlayerOrException()))));
		debug.then(Commands.literal("perampok")
				.then(Commands.literal("info").executes(ctx -> rogueInfo(ctx.getSource().getPlayerOrException()))));
	}

	/** {@code /kultivasi uji perampok|sergap|serbuan ...} (DESIGN 25.11). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		MusuhTests.add(uji);
	}

	/** Action {@code MUSUH}: text = "op[:param]"; "sync" answers with {@code DuniaSync} channel {@code CH_MUSUH}. */
	public static void handleAction(ServerPlayer player, String text) {
		if ("sync".equals(text)) {
			sync(player);
		}
	}

	/** The module's items for the "Fase 8" test kit: four rogue pouches, a demonic pouch and a trophy. */
	public static List<ItemStack> kitItems() {
		List<ItemStack> list = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			list.add(LootPouches.pouch(1 + i, 3, i));
		}
		list.add(LootPouches.demonic(0, 3));
		list.add(LootPouches.banner(0));
		return list;
	}

	// ================================================================== player data

	/** The player's {@code extra.dunia} section (shared by the phase-8 modules; this one owns its own keys). */
	static CompoundTag dunia(CultivationData data) {
		CompoundTag dunia = data.extra().getCompound("dunia").orElse(null);
		if (dunia == null) {
			dunia = new CompoundTag();
			data.extra().put("dunia", dunia);
		}
		return dunia;
	}

	/** Adds a line to the player's last ten encounters (ambush, camp, raid) and refreshes the Musuh tablet. */
	static void logEncounter(ServerPlayer player, int kind, boolean won, int count) {
		CompoundTag dunia = dunia(Cultivators.get(player));
		ListTag log = dunia.getList("musuh_log").orElse(null);
		if (log == null) {
			log = new ListTag();
			dunia.put("musuh_log", log);
		}
		CompoundTag entry = new CompoundTag();
		entry.putLong("day", SectRaids.today(player.level().getServer()));
		entry.putInt("kind", kind);
		entry.putBoolean("won", won);
		entry.putInt("n", count);
		log.add(entry);
		while (log.size() > ENCOUNTER_LOG) {
			log.remove(0);
		}
		Cultivators.dirty(player);
		sync(player);
	}

	/** The Musuh tablet's data: the next raid of the player's hall, the ambush cooldown, kills and the encounter log. */
	static void sync(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		CompoundTag dunia = dunia(Cultivators.get(player));
		tag.putLong("ambush_cd", Ambushes.cooldownLeft(player));
		tag.putBoolean("ambush_on", dev.kultivator.KultivatorConfig.get().rogueAmbushes);
		tag.putInt("kills", dunia.getIntOr("rogue_kills", 0));
		tag.put("log", dunia.getListOrEmpty("musuh_log").copy());
		tag.putLong("today", SectRaids.today(player.level().getServer()));
		CompoundTag raid = SectRaids.infoTag(player.getUUID());
		raid.remove("log");
		tag.put("raid", raid);
		Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_MUSUH, tag);
	}

	// ================================================================== commands

	private static int raidInfo(ServerPlayer player) {
		UUID owner = player.getUUID();
		CompoundTag info = SectRaids.infoTag(owner);
		if (!info.getBooleanOr("enabled", false)) {
			Msg.chat(player, Component.translatable("kultivator.musuh.info.disabled").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!info.getBooleanOr("has_hall", false)) {
			Msg.chat(player, Component.translatable("kultivator.musuh.info.no_hall").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (info.getIntOr("hall_level", 0) < SectRaids.MIN_HALL_LEVEL && info.getIntOr("state", 0) == SectRaids.STATE_IDLE) {
			Msg.chat(player, Component.translatable("kultivator.musuh.info.low_hall", SectRaids.MIN_HALL_LEVEL).withStyle(ChatFormatting.GRAY));
			return 1;
		}
		long next = info.getLongOr("next_day", -1L);
		int sect = info.getIntOr("sect", -1);
		Component sectName = sect >= 0 && info.getIntOr("state", 0) == SectRaids.STATE_OMEN ? LootPouches.sectName(sect)
				: Component.translatable("kultivator.musuh.info.unknown_sect");
		Msg.chat(player, Component.translatable("kultivator.musuh.info.next", next, info.getLongOr("today", 0L), sectName).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable("kultivator.musuh.info.score", info.getIntOr("score", 0), info.getIntOr("lr", 1),
				info.getIntOr("waves", 3), info.getIntOr("chance", 10)).withStyle(ChatFormatting.AQUA));
		CompoundTag live = info.getCompoundOrEmpty("live");
		if (!live.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.musuh.info.live", live.getIntOr("wave", 1), live.getIntOr("waves", 3),
					live.getIntOr("meter", 0)).withStyle(ChatFormatting.RED));
		}
		Msg.chat(player, Component.translatable("kultivator.musuh.raid.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		return 1;
	}

	private static int rogueInfo(ServerPlayer player) {
		LivingEntity target = Targeting.lookedAtHostile(player, 48.0D);
		if (!(target instanceof RogueCultivatorEntity rogue)) {
			Msg.chat(player, Component.translatable("kultivator.musuh.debug.no_rogue").withStyle(ChatFormatting.RED));
			return 0;
		}
		BlockPos home = rogue.home();
		Msg.chat(player, Component.translatable("kultivator.musuh.debug.info", rogue.plate(), rogue.tier(), rogue.realmValue().displayName(),
				rogue.stage(), Component.translatable("kultivator.musuh.role." + rogue.role()), Msg.number(rogue.getHealth()),
				Msg.number(rogue.getMaxHealth()), home == null ? "-" : home.toShortString(), rogue.isChanneling() ? "+" : "-",
				Msg.number(RogueSkills.skillDamage(rogue))).withStyle(ChatFormatting.AQUA));
		Msg.chat(player, Component.translatable("kultivator.musuh.debug.world", RogueCultivators.alive(),
				dev.kultivator.KultivatorConfig.get().rogueWorldCap, SectRaids.liveCount(), Halls.level(player.getUUID())).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private Musuh() {
	}
}
