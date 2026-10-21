package dev.kultivator.dunia.reruntuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaConfig;
import dev.kultivator.dunia.DuniaPacks;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.reruntuhan.gua.FreeHost;
import dev.kultivator.dunia.reruntuhan.gua.Gua;
import dev.kultivator.dunia.reruntuhan.menara.Menara;
import dev.kultivator.dunia.reruntuhan.penjara.Prisons;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Module R of DESIGN 34 (Reruntuhan, Penjara &amp; Menara: ideas 55, 62, 65), wired by its one line in
 * {@code DuniaContent.init()} (both physical sides, before {@code ModItems.init()}). It registers its own config section,
 * items, entities, payloads, anchor handlers, guards, commands and the built-in world generation pack
 * {@code kultivator:dunia_reruntuhan}; the structures (ruins, prisons, the pagoda) come only from world generation in
 * new chunks, and nothing of this module ever places, breaks or changes a block at runtime (rule W1): doors, seals,
 * puzzles and the tower's scenery are entities and client Displays.
 */
public final class Reruntuhan {

	public static final String SECTION = "reruntuhan";
	private static RConfig config = new RConfig();
	private static boolean initialised;

	private record Task(long due, Runnable run) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static long ticks;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		config = DuniaConfig.section(SECTION, RConfig.class, RConfig::new, RConfig::clamp);
		RItems.init();
		REntities.init();
		RNet.init();
		RGuard.init();
		Gua.init();
		Prisons.init();
		Menara.init();
		RTests.init();
		DuniaPacks.worldgen(SECTION, config.worldgen);
		ServerTickEvents.END_SERVER_TICK.register(Reruntuhan::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Runs.clear();
			FreeHost.clearAll();
			Menara.shutdown(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TASKS.clear();
			ticks = 0L;
		});
	}

	/** The module's config section (the same object after a reload). */
	public static RConfig config() {
		return config;
	}

	/** True while the spine's master switch is on. */
	public static boolean enabled() {
		return DuniaSpine.enabled();
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
					Kultivator.LOGGER.error("Kultivator: a ruin task failed", exception);
				}
			}
		}
		if (!enabled()) {
			return;
		}
		Runs.tick(server);
		FreeHost.tickAll(server);
		Prisons.tick(server);
		Menara.tick(server);
	}

	/** Runs {@code task} on the server thread after {@code delay} ticks. */
	public static void schedule(int delay, Runnable task) {
		TASKS.add(new Task(ticks + Math.max(1, delay), task));
	}

	// ================================================================== actions (client to server)

	static void onAction(ServerPlayer player, RNet.Action action) {
		if (player == null || player.isRemoved() || player.isSpectator()) {
			return;
		}
		try {
			switch (action.op()) {
				case "cabut" -> Prisons.confirmPull(player, action.a());
				case "jawab" -> Menara.answer(player, action.a());
				case "menara_info" -> Menara.sendInfo(player);
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: a ruin action {} failed", action.op(), exception);
		}
	}

	// ================================================================== shared rules

	/**
	 * The formation backlash of DESIGN 34.5.5: {@code share} of max health as magic damage, never below 1 HP (a wrong
	 * move never kills), with a push away and a red burst. Creative players feel only the push.
	 */
	public static void backlash(ServerPlayer player, double share) {
		float amount = (float) (player.getMaxHealth() * Math.max(0.0D, share));
		float room = player.getHealth() - 1.0F;
		RFx.at(player.level(), player.position().add(0.0D, 1.0D, 0.0D), RFx.BACKLASH, 0, 0, 1.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.4F, 1.4F);
		Vec3 push = player.getLookAngle().multiply(-1.0D, 0.0D, -1.0D).normalize().scale(0.6D).add(0.0D, 0.3D, 0.0D);
		player.push(push.x, push.y, push.z);
		player.needsSync = true;
		if (player.isCreative() || room <= 0.0F || !(amount > 0.0F)) {
			return;
		}
		RGuard.hazard = true;
		try {
			player.hurtServer(player.level(), player.level().damageSources().magic(), Math.min(amount, room));
		} finally {
			RGuard.hazard = false;
		}
	}

	/** The player's {@code extra.reruntuhan} compound (made when missing). */
	public static CompoundTag data(ServerPlayer player) {
		CompoundTag extra = Cultivators.get(player).extra();
		CompoundTag own = extra.getCompound(SECTION).orElse(null);
		if (own == null) {
			own = new CompoundTag();
			extra.put(SECTION, own);
		}
		return own;
	}

	/** Counts a cleared ruin of {@code tier} (the Dunia page and the guide read it). */
	public static void markRuin(ServerPlayer player, int tier) {
		CompoundTag own = data(player);
		own.putInt("ruins", own.getIntOr("ruins", 0) + 1);
		own.putInt("ruin_best", Math.max(own.getIntOr("ruin_best", 0), tier));
		Cultivators.dirty(player);
	}

	private Reruntuhan() {
	}
}
