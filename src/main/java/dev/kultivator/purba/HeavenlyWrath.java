package dev.kultivator.purba;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.abadi.Abadi;
import dev.kultivator.ascension.Stars;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Murka Langit (DESIGN 26.5.3; terinspirasi dari ISSTH ch. 385-386): players who passed or skipped a calamity are rolled at
 * each dusk once {@code wrath_next} is reached ({@code heavenlyWrathDays} ± 1 apart). A live descent needs an active player
 * (input in the last 60 s) who is not busy, not riding or gliding, not on Peaceful and in the Overworld or Alam Abadi; else
 * it is retried next dusk and lapses after 2 skips with no penalty. Flow: a 30 s omen (white-gold sky grade, EXTRA
 * {@code WRATH_OMEN}), three waves of 2, 3, 4 envoys (15 s apart or when the previous is cleared), then the Duel Kehendak
 * Langit with the Panglima Langit. Win: 25% star share, 6-12 Batu, 2 heaven seal shards. Lose (DeathGuard {@code LANGIT}):
 * 1 HP and Luka Langit 10 minutes, no item loss.
 * <p>
 * Integration (audit T3): a player who is busy at dusk (the shared {@code Sibuk} door: a duel, a heart demon, a realm, a
 * ritual...) is asked again every 10 s through the night instead of losing the dusk; only a night spent busy counts as a
 * skip. {@link #challenge} (Tantang Langit, Sekte Langit 37) only moves {@code wrath_next} to today: the descent still
 * comes through the dusk roll and all its refusals.
 */
public final class HeavenlyWrath {

	public static final long DUSK = 12000L;
	public static final int OMEN_TICKS = 600;
	public static final int WAVE_GAP = 300;
	public static final int[] WAVES = {2, 3, 4};
	public static final int PANGLIMA_AFTER = 600;
	public static final long ACTIVE_MILLIS = 60_000L;
	public static final int MAX_SKIPS = 2;
	public static final double WIN_SHARE = 0.25D;
	public static final int WIN_SHARDS = 2;
	public static final int LUKA_TICKS = 10 * 60 * 20;
	public static final double FAR = 64.0D;
	public static final int FAR_TICKS = 1200;

	private static final int OMEN = 0;
	private static final int WAVING = 1;
	private static final int DUEL = 2;

	/** A live descent on one player. */
	static final class Descent {
		final UUID player;
		final ServerLevel level;
		int stage = OMEN;
		long until;
		int wave;
		int cleared;
		boolean waveDone = true;
		long nextWave;
		@Nullable HeavenEnvoyEntity panglima;
		final ServerBossEvent bar;
		long farSince = -1L;
		boolean test;

		Descent(UUID player, ServerLevel level, long now) {
			this.player = player;
			this.level = level;
			this.until = now + OMEN_TICKS;
			this.bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("kultivator.purba.wrath.bar.omen"), BossEvent.BossBarColor.YELLOW,
					BossEvent.BossBarOverlay.PROGRESS);
		}
	}

	private static final Map<UUID, Descent> DESCENTS = new HashMap<>();
	/** Player -> overworld game time of the next try of a dusk roll that met a busy player. */
	private static final Map<UUID, Long> RETRY = new HashMap<>();
	/** Overworld day time when a postponed dusk roll lapses (sunrise). */
	public static final long NIGHT_END = 23000L;
	private static long lastDuskDay = Long.MIN_VALUE;

	static void clear() {
		for (Descent descent : DESCENTS.values()) {
			descent.bar.removeAllPlayers();
		}
		DESCENTS.clear();
		RETRY.clear();
		lastDuskDay = Long.MIN_VALUE;
	}

	static boolean active(ServerPlayer player) {
		return DESCENTS.containsKey(player.getUUID());
	}

	// ================================================================== schedule

	static void tick(MinecraftServer server, long tick) {
		if (tick % 20L == 0L) {
			dusk(server);
			retries(server);
		}
		if (DESCENTS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Descent>> iterator = DESCENTS.entrySet().iterator();
		while (iterator.hasNext()) {
			Descent descent = iterator.next().getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(descent.player);
			if (player == null || player.level() != descent.level || !player.isAlive() || descent.level.getDifficulty() == Difficulty.PEACEFUL) {
				iterator.remove();
				end(descent, player, false);
				continue;
			}
			if (!step(player, descent)) {
				iterator.remove();
			}
		}
	}

	/** The roll at each dusk (overworld day time 12,000) for every eligible online player. */
	private static void dusk(MinecraftServer server) {
		long clock = Purba.clock(server);
		long day = Math.floorDiv(clock, 24000L);
		long time = Math.floorMod(clock, 24000L);
		if (time < DUSK || time >= DUSK + 200L || day == lastDuskDay) {
			return;
		}
		lastDuskDay = day;
		KultivatorConfig config = KultivatorConfig.get();
		if (!config.heavenlyWrath) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			CultivationData data = Cultivators.get(player);
			if (!StarCalamity.noticed(data) || active(player)) {
				continue;
			}
			CompoundTag purba = Purba.section(data);
			long next = purba.getLongOr("wrath_next", -1L);
			if (next < 0L) {
				schedule(player, purba, day);
				continue;
			}
			if (day < next) {
				continue;
			}
			if (refusal(player) == null) {
				start(player, false);
				schedule(player, purba, day);
			} else if (Purba.busy(player)) {
				// Busy at dusk: asked again in 10 s through the night (never in the middle of real danger).
				RETRY.put(player.getUUID(), server.overworld().getGameTime() + dev.kultivator.util.Sibuk.RETRY_TICKS);
			} else {
				skip(player, purba, day);
			}
			Cultivators.dirty(player);
		}
	}

	/** One more lapsed dusk: after {@link #MAX_SKIPS} the next descent is scheduled anew (no penalty). */
	private static void skip(ServerPlayer player, CompoundTag purba, long day) {
		int skips = purba.getIntOr("wrath_skips", 0) + 1;
		if (skips >= MAX_SKIPS) {
			schedule(player, purba, day);
		} else {
			purba.putInt("wrath_skips", skips);
		}
		Cultivators.dirty(player);
	}

	/** The postponed dusk rolls: start when free, wait while busy, lapse (a skip) at sunrise or on another refusal. */
	private static void retries(MinecraftServer server) {
		if (RETRY.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		long clock = Purba.clock(server);
		long day = Math.floorDiv(clock, 24000L);
		boolean night = Math.floorMod(clock, 24000L) >= DUSK && Math.floorMod(clock, 24000L) < NIGHT_END;
		Iterator<Map.Entry<UUID, Long>> iterator = RETRY.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Long> entry = iterator.next();
			if (now < entry.getValue()) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !KultivatorConfig.get().heavenlyWrath || active(player)) {
				iterator.remove();
				continue;
			}
			CompoundTag purba = Purba.section(Cultivators.get(player));
			if (refusal(player) == null) {
				iterator.remove();
				start(player, false);
				schedule(player, purba, day);
			} else if (night && Purba.busy(player)) {
				entry.setValue(now + dev.kultivator.util.Sibuk.RETRY_TICKS);
			} else {
				iterator.remove();
				skip(player, purba, day);
			}
		}
	}

	/**
	 * Sekte Langit (37G): true when a Tantang Langit could be offered at all (Heavenly Wrath on and Heaven has noticed the
	 * player), so the Heaven Court envoy hides the button otherwise (audit 4.3). {@link #challenge} still decides.
	 */
	public static boolean canChallenge(ServerPlayer player) {
		return KultivatorConfig.get().heavenlyWrath && StarCalamity.noticed(Cultivators.get(player));
	}

	/** Sekte Langit (37G): told once for every won descent (after the rewards), tests included. */
	public static void addWinListener(java.util.function.Consumer<ServerPlayer> listener) {
		if (listener != null) {
			WIN_LISTENERS.add(listener);
		}
	}

	private static final java.util.List<java.util.function.Consumer<ServerPlayer>> WIN_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * Tantang Langit (Sekte Langit 37): the player asks Heaven to come at the next dusk. Only {@code wrath_next} moves to
	 * today; the dusk roll and every refusal (Peaceful, busy, place, AFK) still decide. Refused (the reason) when Heaven has
	 * not noticed the player yet (no calamity passed or skipped), Heavenly Wrath is switched off, or a descent is already
	 * running or called for today. Null when the challenge stands.
	 */
	public static @Nullable Component challenge(ServerPlayer player) {
		if (!KultivatorConfig.get().heavenlyWrath) {
			return Component.translatable("kultivator.sambung.murka.tantang.off");
		}
		CultivationData data = Cultivators.get(player);
		if (!StarCalamity.noticed(data)) {
			return Component.translatable("kultivator.sambung.murka.tantang.belum");
		}
		if (active(player) || RETRY.containsKey(player.getUUID())) {
			return Component.translatable("kultivator.sambung.murka.tantang.sudah");
		}
		long today = Purba.today(player.level().getServer());
		CompoundTag purba = Purba.section(data);
		long next = purba.getLongOr("wrath_next", -1L);
		if (next >= 0L && next <= today && lastDuskDay != today) {
			return Component.translatable("kultivator.sambung.murka.tantang.sudah");
		}
		// After today's dusk passed, the challenge stands for tomorrow's.
		purba.putLong("wrath_next", lastDuskDay == today ? today + 1L : today);
		purba.putInt("wrath_skips", 0);
		Cultivators.dirty(player);
		return null;
	}

	private static void schedule(ServerPlayer player, CompoundTag purba, long day) {
		int days = KultivatorConfig.get().heavenlyWrathDays;
		purba.putLong("wrath_next", day + days + player.getRandom().nextInt(3) - 1);
		purba.putInt("wrath_skips", 0);
		Cultivators.dirty(player);
	}

	/** Why a descent cannot start on the player now, or null. */
	static @Nullable Component refusal(ServerPlayer player) {
		if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
			return Component.translatable("kultivator.purba.peaceful");
		}
		if (player.isCreative() || player.isSpectator()) {
			return Component.translatable("kultivator.purba.wrath.not_survival");
		}
		if (Util.getMillis() - player.getLastActionTime() > ACTIVE_MILLIS) {
			return Component.translatable("kultivator.purba.wrath.afk");
		}
		if (Purba.busy(player) || active(player)) {
			return Component.translatable("kultivator.spine9.refuse.busy");
		}
		if (player.isPassenger() || player.isFallFlying()) {
			return Component.translatable("kultivator.purba.wrath.riding");
		}
		if (player.level().dimension() != Level.OVERWORLD && !Abadi.isImmortalLevel(player.level())) {
			return Component.translatable("kultivator.purba.wrath.place");
		}
		return null;
	}

	/** Starts a descent now (test: no checks beyond Peaceful). */
	static boolean start(ServerPlayer player, boolean test) {
		if (active(player) || player.level().getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		ServerLevel level = player.level();
		Descent descent = new Descent(player.getUUID(), level, level.getGameTime());
		descent.test = test;
		DESCENTS.put(player.getUUID(), descent);
		descent.bar.addPlayer(player);
		Fx.send(level, player.position(), FxType.PURBA, PurbaFxIds.WRATH_SKY, 1, 0.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 2.0F, 0.5F);
		Msg.chat(player, Component.translatable("kultivator.purba.wrath.omen").withStyle(ChatFormatting.YELLOW));
		Msg.chat(player, Component.translatable("kultivator.purba.wrath.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		EpicHooks.extra(level, EpicIds.EXTRA_WRATH_OMEN, player, player, player.position(), 0, 0);
		return true;
	}

	/** One tick of a descent; false when it ended. */
	private static boolean step(ServerPlayer player, Descent descent) {
		long now = descent.level.getGameTime();
		switch (descent.stage) {
			case OMEN -> {
				descent.bar.setProgress(1.0F - Mth.clamp((descent.until - now) / (float) OMEN_TICKS, 0.0F, 1.0F));
				if (now >= descent.until) {
					descent.stage = WAVING;
					descent.nextWave = now;
					descent.bar.setColor(BossEvent.BossBarColor.WHITE);
				}
			}
			case WAVING -> {
				if (descent.wave < WAVES.length && (descent.waveDone || now >= descent.nextWave)) {
					int index = descent.wave;
					descent.wave++;
					descent.waveDone = false;
					descent.nextWave = now + WAVE_GAP;
					descent.bar.setName(Component.translatable("kultivator.purba.wrath.bar.wave", descent.wave, WAVES.length));
					descent.bar.setProgress((float) descent.wave / WAVES.length);
					EnvoyAi.spawnWave(player, WAVES[index], 0, cleared -> {
						Descent live = DESCENTS.get(descent.player);
						if (live == descent) {
							descent.waveDone = true;
							if (Boolean.TRUE.equals(cleared)) {
								descent.cleared++;
							}
						}
					});
				} else if (descent.wave >= WAVES.length && (descent.waveDone || now >= descent.nextWave + PANGLIMA_AFTER - WAVE_GAP)) {
					if (descent.cleared >= WAVES.length) {
						Advancements.grant(player, "wrath_repelled");
					}
					return duel(player, descent);
				}
			}
			default -> {
				HeavenEnvoyEntity panglima = descent.panglima;
				if (panglima == null || panglima.isRemoved()) {
					if (panglima != null && panglima.isDeadOrDying()) {
						return true;
					}
					end(descent, player, false);
					return false;
				}
				descent.bar.setProgress(Mth.clamp(panglima.getHealth() / Math.max(1.0F, panglima.getMaxHealth()), 0.0F, 1.0F));
				if (player.distanceToSqr(panglima) > FAR * FAR) {
					if (descent.farSince < 0L) {
						descent.farSince = now;
					} else if (now - descent.farSince >= FAR_TICKS) {
						end(descent, player, false);
						return false;
					}
				} else {
					descent.farSince = -1L;
				}
			}
		}
		return true;
	}

	/** The Panglima Langit descends; only the challenged player's damage counts. False when it could not (the descent ends). */
	private static boolean duel(ServerPlayer player, Descent descent) {
		HeavenEnvoyEntity panglima = EnvoyAi.spawnOne(player, HeavenEnvoyEntity.PANGLIMA, Purba.realmEff(player), 0);
		if (panglima == null) {
			end(descent, player, false);
			return false;
		}
		descent.stage = DUEL;
		descent.panglima = panglima;
		descent.bar.setColor(BossEvent.BossBarColor.YELLOW);
		descent.bar.setName(Component.translatable("kultivator.purba.wrath.bar.duel", TechniquePower.playerStars(Cultivators.get(player))));
		descent.bar.setProgress(1.0F);
		Msg.chat(player, Component.translatable("kultivator.purba.wrath.duel").withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** DeathGuard {@code LANGIT}: a lethal blow during a descent is Kalah at 1 HP with Luka Langit. */
	static boolean guard(ServerPlayer player) {
		Descent descent = DESCENTS.remove(player.getUUID());
		if (descent == null) {
			return false;
		}
		player.setHealth(1.0F);
		CompoundTag purba = Purba.section(Cultivators.get(player));
		purba.putLong("langit_until", Purba.now(player) + LUKA_TICKS);
		Cultivators.dirty(player);
		Msg.chat(player, Component.translatable("kultivator.purba.wrath.lose", LUKA_TICKS / 1200).withStyle(ChatFormatting.DARK_RED));
		end(descent, player, false);
		return true;
	}

	/** The Panglima fell: Menang. */
	static void onPanglimaDeath(HeavenEnvoyEntity panglima) {
		for (Descent descent : DESCENTS.values()) {
			if (descent.panglima == panglima) {
				DESCENTS.remove(descent.player);
				ServerPlayer player = descent.level.getServer().getPlayerList().getPlayer(descent.player);
				if (player != null) {
					win(player);
				}
				end(descent, player, true);
				return;
			}
		}
	}

	private static void win(ServerPlayer player) {
		Stars.grantShare(player, WIN_SHARE);
		Purba.give(player, Purba.stones(6 + player.getRandom().nextInt(7)));
		Purba.give(player, new ItemStack(PurbaItems.HEAVEN_SEAL_SHARD, WIN_SHARDS));
		Advancements.grant(player, "heaven_general");
		Msg.chat(player, Component.translatable("kultivator.purba.wrath.win").withStyle(ChatFormatting.GOLD));
		// Phase 10b (DESIGN 29.2): the first victory only, the Panglima's decree seal falls from his hand (bound to the winner)
		dev.kultivator.pusakalangit.Titah.grantFromPanglima(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		for (java.util.function.Consumer<ServerPlayer> listener : WIN_LISTENERS) {
			try {
				listener.accept(player);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("A Heavenly Wrath win listener failed", exception);
			}
		}
	}

	/** Ends a descent: the bar and sky grade go, the envoys and the Panglima leave. */
	private static void end(Descent descent, @Nullable ServerPlayer player, boolean won) {
		descent.bar.removeAllPlayers();
		EnvoyAi.dismissFor(descent.player);
		if (descent.panglima != null && !descent.panglima.isRemoved() && descent.panglima.isAlive()) {
			descent.panglima.vanish();
		}
		if (player != null) {
			Fx.send(player.level(), player.position(), FxType.PURBA, PurbaFxIds.WRATH_SKY, 0, 0.0F);
			if (!won && player.isAlive() && !descent.test) {
				Msg.chat(player, Component.translatable("kultivator.purba.wrath.ends").withStyle(ChatFormatting.GRAY));
			}
			Purba.sync(player);
		}
	}

	/** Leaving ends a descent quietly (no penalty). */
	static void abandon(ServerPlayer player) {
		Descent descent = DESCENTS.remove(player.getUUID());
		if (descent != null) {
			descent.bar.removeAllPlayers();
			EnvoyAi.dismissFor(descent.player);
			if (descent.panglima != null && !descent.panglima.isRemoved()) {
				descent.panglima.vanish();
			}
		}
	}

	// ================================================================== tests

	/** {@code uji murka gelombang <n>}: a descent starting at wave n. */
	static boolean testWave(ServerPlayer player, int wave) {
		abandon(player);
		if (!start(player, true)) {
			return false;
		}
		Descent descent = DESCENTS.get(player.getUUID());
		descent.stage = WAVING;
		descent.wave = Mth.clamp(wave, 1, WAVES.length) - 1;
		descent.waveDone = true;
		return true;
	}

	/** {@code uji murka panglima}: the duel at once. */
	static boolean testDuel(ServerPlayer player) {
		abandon(player);
		if (!start(player, true)) {
			return false;
		}
		Descent descent = DESCENTS.get(player.getUUID());
		if (!duel(player, descent)) {
			DESCENTS.remove(player.getUUID());
			return false;
		}
		return true;
	}

	/** {@code uji murka menang|kalah}. */
	static void testForce(ServerPlayer player, boolean won) {
		Descent descent = DESCENTS.remove(player.getUUID());
		if (descent == null) {
			descent = new Descent(player.getUUID(), player.level(), player.level().getGameTime());
			descent.test = true;
		}
		if (won) {
			win(player);
		} else {
			CompoundTag purba = Purba.section(Cultivators.get(player));
			purba.putLong("langit_until", Purba.now(player) + LUKA_TICKS);
			Cultivators.dirty(player);
			Msg.chat(player, Component.translatable("kultivator.purba.wrath.lose", LUKA_TICKS / 1200).withStyle(ChatFormatting.DARK_RED));
		}
		end(descent, player, won);
	}

	/** The schedule line of {@code uji murka jadwal} and the Bencana tablet. */
	static CompoundTag info(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CompoundTag purba = Purba.section(data);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("enabled", KultivatorConfig.get().heavenlyWrath);
		tag.putBoolean("eligible", StarCalamity.noticed(data));
		tag.putLong("next", purba.getLongOr("wrath_next", -1L));
		tag.putInt("skips", purba.getIntOr("wrath_skips", 0));
		tag.putLong("today", Purba.today(player.level().getServer()));
		tag.putBoolean("live", active(player));
		return tag;
	}

	private HeavenlyWrath() {
	}
}
