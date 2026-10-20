package dev.kultivator.pusakalangit;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.alam.Alam;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.sect.Sparring;
import dev.kultivator.sect.event.Tournament;
import dev.kultivator.technique.Techniques;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.run.CastOptions;
import dev.kultivator.technique.spec.run.TechniqueRunner;
import dev.kultivator.technique.spec.run.TechniqueTests;
import dev.kultivator.trial.HeartDemons;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.LoadedPath;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Titah Langit decrees (DESIGN 29, contract C10b.1): the gates, the refusals and the only cast path of the four
 * {@code TITAH} specs. {@link #cast} snapshots the player's {@link Settings} (Kekuatan, Kerusakan, Kecepatan, Hancurkan
 * tanah, Tinggalkan pusaka) and the crosshair point (≤ {@code titahRange}, one read-only ray along loaded chunks), hands
 * them to {@link TitahSkill} and calls {@code TechniqueRunner.cast} with {@code CastOptions.DEFAULT} (or {@code TEST}).
 * The runner keeps every 6c rule: qi, the shared ultimate lockout, damage choke points; afterwards the decree's own
 * cooldown (× sqrt(max(1, Kekuatan))) and the shared 60 s seal recovery are written on the PLAYER, so a second seal gains
 * nothing. Server only.
 */
public final class Titah {

	/** The four decrees; ordinal = net id − 1200. */
	public enum Skill {
		PEDANG, TIANG, BINTANG, GUNUNG;

		private static final Skill[] VALUES = values();

		public String specId() {
			return TitahArts.IDS[ordinal()];
		}

		public int netId() {
			return TitahArts.NET_FIRST + ordinal();
		}

		/** The lower-case command and lang name ("pedang", "tiang", "bintang", "gunung"). */
		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** The Dewa Abadi stage that unlocks it: Awal 0, Tengah 1, Akhir 2, Puncak 3 (29.2.1). */
		public int stage() {
			return ordinal();
		}

		public @Nullable TechniqueSpec spec() {
			return TechniqueRegistry.byId(specId());
		}

		/** Pedang and Tiang leave a planted weapon (29.6). */
		public boolean plantsWeapon() {
			return this == PEDANG || this == TIANG;
		}

		public Component displayName() {
			return Component.translatable("kultivator.technique." + specId());
		}

		public static Skill byOrdinal(int ordinal) {
			return VALUES[Math.max(0, Math.min(VALUES.length - 1, ordinal))];
		}

		public static @Nullable Skill byKey(String key) {
			for (Skill skill : VALUES) {
				if (skill.key().equals(key)) {
					return skill;
				}
			}
			return null;
		}

		public static @Nullable Skill bySpec(String specId) {
			int i = TitahArts.skillOf(specId);
			return i < 0 ? null : VALUES[i];
		}
	}

	/**
	 * The per-decree settings (29.2.2), already sanitised: {@code kekuatan} the size factor (0.1-10, never above the realm
	 * limit), {@code kerusakan} the share of the authored damage (0-1; 0 = pure spectacle), {@code kecepatan} the speed
	 * (0.3-3), {@code hancur} "Hancurkan tanah" (only matters while Mode Kehancuran is on), {@code monumen} "Tinggalkan
	 * pusaka" (Pedang, Tiang).
	 */
	public record Settings(float kekuatan, float kerusakan, float kecepatan, boolean hancur, boolean monumen) {
		public static final Settings DEFAULT = new Settings(1.0F, 1.0F, 1.0F, true, true);
	}

	/** One cast in the making: what {@link TitahSkill} reads (like {@code OwnArt} reads the creation). */
	record Order(Skill skill, Settings settings, Vec3 castPos, Vec3 impact, boolean test, boolean bypass, boolean real) {
		/** No reward, never counted as a real cast (a test or a bypass cast). */
		boolean practice() {
			return test || bypass;
		}
	}

	/** The shared seal recovery after any decree (29.2.1). */
	public static final int RECOVERY_TICKS = 1200;
	private static final String KEY = "kultivator.pusaka_langit.";

	private static final Map<UUID, Order> PENDING = new HashMap<>();
	private static final Set<UUID> ACTIVE = new HashSet<>();
	private static final Set<UUID> BYPASS = new HashSet<>();

	// ================================================================== contract C10b.1

	/** The player's stored, sanitised settings of {@code skill} (Kekuatan clamped to the realm limit). */
	public static Settings settings(ServerPlayer player, Skill skill) {
		return TitahSettings.read(player, skill);
	}

	/**
	 * Why {@code player} may not cast {@code skill} now, or null. Spends nothing. Realm, stage, cooldown and the seal
	 * recovery are skipped under {@code uji pusaka_langit bypass}; qi is the runner's.
	 */
	public static @Nullable Component whyNot(ServerPlayer player, Skill skill) {
		return whyNot(player, skill, false);
	}

	/** Casts {@code skill} at the crosshair; true when it happened. {@code test}: {@code CastOptions.TEST}, no reward. */
	public static boolean cast(ServerPlayer player, Skill skill, boolean test) {
		return castWith(player, skill, test, null, null);
	}

	// ================================================================== gates

	static @Nullable Component whyNot(ServerPlayer player, Skill skill, boolean test) {
		KultivatorConfig config = KultivatorConfig.get();
		if (!config.titahEnabled) {
			return Component.translatable(KEY + "refuse.disabled");
		}
		CultivationData data = Cultivators.get(player);
		boolean free = test || bypass(player);
		if (!free) {
			Component gate = gate(data, skill);
			if (gate != null) {
				return gate;
			}
		}
		if (DuelRules.inDuel(player) || Sparring.inDuel(player)) {
			return Component.translatable(KEY + "refuse.duel");
		}
		if (Tournament.running(player.getUUID())) {
			return Component.translatable(KEY + "refuse.tournament");
		}
		if (data.breakingThrough() || Breakthrough.isBusy(player)) {
			return Component.translatable(KEY + "refuse.breakthrough");
		}
		if (HeartDemons.inTrial(player)) {
			return Component.translatable(KEY + "refuse.heart_demon");
		}
		if (Alam.inRun(player) || Alam.isRealmLevel(player.level())) {
			return Component.translatable(KEY + "refuse.realm_run");
		}
		Component blocked = Techniques.castBlocker(player);
		if (blocked != null) {
			return blocked;
		}
		if (ACTIVE.contains(player.getUUID())) {
			return Component.translatable(KEY + "refuse.active");
		}
		if (!free) {
			long now = player.level().getGameTime();
			long recover = TitahSettings.recoverUntil(data);
			if (now < recover) {
				return Component.translatable(KEY + "refuse.recover", seconds(recover - now));
			}
			long left = data.cooldownLeft(skill.specId(), now);
			if (left > 0L) {
				return Component.translatable(KEY + "refuse.cooldown", skill.displayName(), seconds(left));
			}
		}
		return null;
	}

	/** The realm and stage gate alone (for the menu's lock state): Dewa Abadi, then Awal/Tengah/Akhir/Puncak. */
	static @Nullable Component gate(CultivationData data, Skill skill) {
		if (!data.realm().atLeast(Realm.TRUE_IMMORTAL)) {
			return Component.translatable(KEY + "refuse.realm", Realm.TRUE_IMMORTAL.displayName());
		}
		if (data.realm() == Realm.TRUE_IMMORTAL && data.rank().stage() < skill.stage()) {
			return Component.translatable(KEY + "refuse.stage", skill.displayName(), Component.translatable(KEY + "stage." + skill.stage()));
		}
		return null;
	}

	/** The Kekuatan limit of the realm (29.2.1): Awal ×3, Tengah ×5, Akhir ×7, Puncak or ★27+ ×10. */
	static float kMax(CultivationData data) {
		if (!data.realm().atLeast(Realm.TRUE_IMMORTAL)) {
			return 1.0F;
		}
		if (TechniquePower.playerStars(data) >= 27) {
			return 10.0F;
		}
		return switch (data.rank().stage()) {
			case 0 -> 3.0F;
			case 1 -> 5.0F;
			case 2 -> 7.0F;
			default -> 10.0F;
		};
	}

	/** The decree's cooldown in ticks for {@code kekuatan}: the authored band × sqrt(max(1, K)). */
	static int cooldownTicks(Skill skill, float kekuatan) {
		return (int) Math.round(TitahArts.COOLDOWN_SECONDS[skill.ordinal()] * 20.0D * Math.sqrt(Math.max(1.0D, kekuatan)));
	}

	// ================================================================== the cast

	/**
	 * The cast with optional overrides ({@code uji pusaka_langit}): {@code override} settings (still sanitised; test casts
	 * may use up to ×10) and {@code aimOverride} the impact point (else the crosshair, ≤ {@code titahRange}).
	 */
	static boolean castWith(ServerPlayer player, Skill skill, boolean test, @Nullable Settings override, @Nullable Vec3 aimOverride) {
		Component why = whyNot(player, skill, test);
		if (why != null) {
			return refuse(player, why);
		}
		TechniqueSpec spec = skill.spec();
		if (spec == null) {
			return refuse(player, Component.translatable(KEY + "refuse.disabled"));
		}
		CultivationData data = Cultivators.get(player);
		boolean bypass = bypass(player);
		float limit = test || bypass ? 10.0F : kMax(data);
		Settings settings = TitahSettings.sanitize(override != null ? override : TitahSettings.read(player, skill), limit);
		Vec3 impact = aimOverride != null ? aimOverride : aim(player);
		if (impact == null) {
			return refuse(player, Component.translatable(KEY + "refuse.aim", KultivatorConfig.get().titahRange));
		}
		boolean real = settings.hancur() && Kehancuran.selfOn(player) && TechniquePower.kScale(data) > 0.0D
				&& (!test || TechniqueTests.kehancuranTests(player));
		Order order = new Order(skill, settings, player.position(), impact, test, bypass, real);
		UUID id = player.getUUID();
		PENDING.put(id, order);
		boolean done;
		try {
			done = TechniqueRunner.cast(player, spec, test ? CastOptions.TEST : CastOptions.DEFAULT);
		} finally {
			PENDING.remove(id);
		}
		if (!done) {
			return false;
		}
		long now = player.level().getGameTime();
		if (!test && !bypass) {
			// the decree's own cooldown over the runner's authored one, and the shared seal recovery (both on the player)
			data.setCooldown(skill.specId(), now, cooldownTicks(skill, settings.kekuatan()));
			TitahSettings.setRecover(data, now + RECOVERY_TICKS);
			if (TitahSettings.markCast(data, skill)) {
				Advancements.grant(player, "titah_all");
			}
			Advancements.grant(player, "titah_first");
		}
		Cultivators.dirty(player);
		HeavenDecreeSealItem.refreshView(player);
		TitahSettings.sync(player);
		return true;
	}

	/** Takes the pending order of {@code player} (TitahSkill's {@code cast}); null for any cast that did not come through here. */
	static @Nullable Order pending(ServerPlayer player) {
		return PENDING.get(player.getUUID());
	}

	static void markActive(ServerPlayer player, boolean on) {
		if (on) {
			ACTIVE.add(player.getUUID());
		} else {
			ACTIVE.remove(player.getUUID());
		}
	}

	/** {@code uji pusaka_langit bypass}: realm, cooldown and qi are skipped (with the runner's own bypass). */
	public static boolean bypass(ServerPlayer player) {
		return BYPASS.contains(player.getUUID());
	}

	static void setBypass(ServerPlayer player, boolean on) {
		if (on) {
			BYPASS.add(player.getUUID());
		} else {
			BYPASS.remove(player.getUUID());
		}
		TechniqueRunner.setBypass(player, on);
	}

	static void forget(UUID id) {
		PENDING.remove(id);
		ACTIVE.remove(id);
		BYPASS.remove(id);
	}

	static void clearAll() {
		PENDING.clear();
		ACTIVE.clear();
		BYPASS.clear();
	}

	/**
	 * The crosshair point: one read-only ray of {@code titahRange} along loaded chunks; the top face of the block it meets
	 * (a wall hit lands on the wall's top), or null when it meets nothing (a decree is never cast into the sky).
	 */
	static @Nullable Vec3 aim(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(KultivatorConfig.get().titahRange));
		BlockHitResult hit = LoadedPath.ray(level, eye, end, player);
		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}
		Vec3 at = hit.getLocation();
		if (hit.getDirection() == Direction.UP) {
			return at;
		}
		BlockPos pos = hit.getBlockPos();
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		// a wall or an overhang: land on the column's top when it is near, else on the hit block's own top
		double y = top - pos.getY() <= 8 ? top : pos.getY() + 1.0D;
		return new Vec3(at.x, y, at.z);
	}

	// ================================================================== the Panglima's seal (29.2 source 1)

	/** The first victory over the Panglima Langit: the seal, bound to the winner, once per player ({@code extra.titah.granted}). */
	public static void grantFromPanglima(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CompoundTag tag = TitahSettings.section(data);
		if (tag.getBooleanOr("granted", false)) {
			return;
		}
		tag.putBoolean("granted", true);
		Cultivators.dirty(player);
		TitahItems.give(player, TitahItems.boundSeal(player));
		Msg.chat(player, Component.translatable(KEY + "granted").withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.8F);
	}

	static boolean refuse(ServerPlayer player, Component why) {
		Msg.actionBar(player, why.copy().withColor(0xFF8A7A));
		return false;
	}

	static String seconds(long ticks) {
		return String.valueOf(Math.max(1L, (ticks + 19L) / 20L));
	}

	private Titah() {
	}
}
