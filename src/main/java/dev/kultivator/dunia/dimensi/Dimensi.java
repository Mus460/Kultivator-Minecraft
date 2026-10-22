package dev.kultivator.dunia.dimensi;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaPacks;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.dimensi.bawah.BawahAnchors;
import dev.kultivator.dunia.dimensi.bawah.BawahPeople;
import dev.kultivator.dunia.dimensi.bawah.Bawah;
import dev.kultivator.dunia.dimensi.bawah.SoulJourney;
import dev.kultivator.dunia.dimensi.laut.Laut;
import dev.kultivator.dunia.dimensi.laut.LautAnchors;
import dev.kultivator.dunia.dimensi.laut.SeaGate;
import dev.kultivator.dunia.dimensi.laut.SeaPeople;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Module D, Dimensi baru (DESIGN 34.5.13 Dunia Bawah, 34.5.14 Laut Tanpa Batas, 34.6 D): the init wired by ONE line in
 * {@code DuniaContent.init()}. Registers the module's config section ({@code dimensi}), items, entities, payloads,
 * anchor handlers ({@code bawah}, {@code laut}), the SoulService, the ship route, the test commands and the world
 * generation pack {@code kultivator:dunia_dimensi} (the overworld sets: Kuil Gerbang Hantu and Pusaran Laut). The two
 * dimensions and their own structure sets are base data.
 * <p>
 * Listens to module L's {@code SKY_EVENT} ({@link DimensiSky}: the blood eclipse opens the ghost gates and thickens the
 * hungry dead, the twin moons raise the spirit tide) and module T's {@code VOYAGE} (the ship's arrival at sea).
 * <p>
 * Block safety (DESIGN 34.0 W1): nothing in this module places, breaks or changes a block at runtime. Its towns of the
 * dead and palaces of the sea come only from world generation in new chunks; gates, books, whirlpools and the turtle's
 * deck are entities. No LightningBolt, no weather, time or tick-rate change.
 */
public final class Dimensi {

	private record Task(long due, Runnable run) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
	private static long ticks;
	private static boolean initialised;

	/** From {@code DuniaContent.init()} (both physical sides), before {@code ModItems.init()}. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		DimensiConfig.init();
		DimensiItems.init();
		DimensiEntities.init();
		DimensiNet.register();
		DuniaPacks.worldgen("dimensi", DimensiConfig.get().worldgen);
		DuniaAnchors.register("bawah", new BawahAnchors());
		DuniaAnchors.register("laut", new LautAnchors());
		Bawah.init();
		Laut.init();
		DimensiCommands.init();
		ServerTickEvents.END_SERVER_TICK.register(Dimensi::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				LAST_HURT.put(player.getUUID(), player.level().getServer().overworld().getGameTime());
			}
		});
		ServerPlayerEvents.JOIN.register(player -> schedule(20, () -> BawahPeople.applyPerk(player)));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> schedule(2, () -> BawahPeople.applyPerk(newPlayer)));
		ServerPlayerEvents.LEAVE.register(player -> LAST_HURT.remove(player.getUUID()));
		DuniaEvents.SKY_EVENT.register(Dimensi::onSkyEvent);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TASKS.clear();
			LAST_HURT.clear();
			ticks = 0L;
			DimensiSky.clear();
			Bawah.clear();
			Laut.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		if (!TASKS.isEmpty()) {
			List<Task> due = new ArrayList<>();
			TASKS.removeIf(task -> {
				if (task.due() <= ticks) {
					due.add(task);
					return true;
				}
				return false;
			});
			for (Task task : due) {
				try {
					task.run().run();
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Kultivator: a Dimensi task failed", exception);
				}
			}
		}
		try {
			Bawah.tick(server, ticks);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: the Dunia Bawah tick failed", exception);
		}
		try {
			Laut.tick(server, ticks);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: the Laut Tanpa Batas tick failed", exception);
		}
	}

	/** Runs {@code task} on the server thread after {@code delay} ticks. */
	public static void schedule(int delay, Runnable task) {
		TASKS.add(new Task(ticks + Math.max(1, delay), task));
	}

	/** True when the player was hurt within the last 10 s. */
	public static boolean recentlyHurt(ServerPlayer player) {
		Long at = LAST_HURT.get(player.getUUID());
		return at != null && player.level().getServer().overworld().getGameTime() - at < 200L;
	}

	/** A stack of the item with registry id {@code id} (made now, never cached); empty when unknown. */
	public static ItemStack itemById(String id, int count) {
		Identifier key = Identifier.tryParse(id);
		if (key == null) {
			return ItemStack.EMPTY;
		}
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(key);
		if (item.isEmpty() || item.get() == Items.AIR) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(item.get(), Math.max(1, count));
	}

	/** Stands up a mark of {@code kind} at {@code at} for an anchor (see {@link TandaEntity}). */
	public static @Nullable TandaEntity mark(ServerLevel level, Vec3 at, int kind, @Nullable UUID anchor) {
		return mark(level, at, 0.0F, kind, anchor);
	}

	/** {@link #mark(ServerLevel, Vec3, int, UUID)} turned to {@code yaw} (the anchor's: its piece's rotation). */
	public static @Nullable TandaEntity mark(ServerLevel level, Vec3 at, float yaw, int kind, @Nullable UUID anchor) {
		TandaEntity mark = DimensiEntities.TANDA.create(level, EntitySpawnReason.EVENT);
		if (mark == null) {
			return null;
		}
		mark.setKind(kind);
		mark.setAnchor(anchor);
		mark.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		return level.addFreshEntity(mark) ? mark : null;
	}

	/** A mark was right-clicked (or punched). */
	static void useTanda(ServerPlayer player, TandaEntity mark) {
		if (player.distanceToSqr(mark) > 8.0D * 8.0D) {
			return;
		}
		DuniaAnchorEntity anchor = null;
		if (mark.anchor() != null && player.level().getEntity(mark.anchor()) instanceof DuniaAnchorEntity found) {
			anchor = found;
		}
		switch (mark.kind()) {
			case TandaEntity.GERBANG -> {
				if (DimensiLevels.isBawah(player.level())) {
					if (SoulJourney.isSoul(player)) {
						SoulJourney.returnToBody(player, SoulJourney.BY_GATE);
					} else {
						DimensiLevels.sendHome(player, null);
					}
				} else if (DimensiLevels.isLaut(player.level())) {
					SeaGate.leave(player);
				}
			}
			case TandaEntity.BUKU -> BawahPeople.openBook(player, mark.getId());
			case TandaEntity.HERBA -> SeaPeople.harvest(player, mark, anchor);
			case TandaEntity.DIZANG -> Bawah.dizangBlessing(player, mark);
			case TandaEntity.ALTAR -> Msg.chat(player, Component.translatable("kultivator.dimensi.altar.info",
					DimensiConfig.get().incenseMinutes).withColor(0xB9A3D9));
			case TandaEntity.MATA_AIR -> Msg.actionBar(player, Component.translatable("kultivator.dimensi.penglai.mata_air").withColor(0xFFE9A8));
			default -> {
			}
		}
	}

	/**
	 * A sea or underworld monster of this module was slain by a player: quests, the tier-4 deed, and the death throes on
	 * the water ({@code tier} 0-4, -1 when it has none).
	 */
	public static void monsterSlain(ServerLevel level, ServerPlayer killer, Entity victim, String what, int tier) {
		SeaPeople.questKill(killer, victim, what);
		if (tier >= 4) {
			Advancements.grant(killer, "dunia/sea_tier4");
		}
		if (tier >= 0) {
			DimensiNet.fx(level, DimensiNet.FX_MONSTER_DOWN, victim.position(), tier, 64.0D);
		}
	}

	/** The serpent's water spout at {@code at} (a client effect). */
	public static void spout(ServerLevel level, Vec3 at) {
		DimensiNet.fx(level, DimensiNet.FX_SPOUT, at, 0, 64.0D);
	}

	private static void onSkyEvent(ServerLevel level, SkyKind kind, boolean started) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (kind == SkyKind.BLOOD_ECLIPSE && started) {
			// The ghost gates stand open: every player near a Kuil Gerbang Hantu hears it.
			for (DuniaAnchorEntity altar : DuniaAnchors.loaded(level, "bawah/altar")) {
				for (ServerPlayer player : DuniaAnchors.playersNear(level, altar.position(), 64.0D)) {
					Msg.chat(player, Component.translatable("kultivator.dimensi.gerhana.gerbang").withColor(0xD05050));
				}
			}
		}
		// the souls below and the voyagers beyond feel it too
		ServerLevel bawah = DimensiLevels.bawah(server);
		if (bawah != null && kind == SkyKind.BLOOD_ECLIPSE) {
			for (ServerPlayer player : bawah.players()) {
				Msg.chat(player, Component.translatable(started ? "kultivator.dimensi.gerhana.bawah" : "kultivator.dimensi.gerhana.bawah_usai")
						.withColor(0xD05050));
				if (started) {
					// the voice of Heaven reaches below too (module L's line when this build has it)
					DimensiVoice.heaven(player, "heaven.langit.blood_eclipse", "heaven.calamity");
				}
				SoulJourney.sendState(player);
			}
		}
		ServerLevel laut = DimensiLevels.laut(server);
		if (laut != null) {
			for (ServerPlayer player : laut.players()) {
				Msg.chat(player, Component.translatable("kultivator.dimensi.langit_laut." + kind.id() + (started ? "" : "_usai"))
						.withColor(0x9FE8F0));
				if (started) {
					DimensiVoice.heaven(player, "heaven.langit." + kind.id(), "heaven.witness");
				}
				SoulJourney.sendState(player);
			}
		}
	}

	/** The tick counter of the module (for staggering). */
	public static long ticks() {
		return ticks;
	}

	private Dimensi() {
	}
}
