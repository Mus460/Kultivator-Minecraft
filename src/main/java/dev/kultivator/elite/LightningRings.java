package dev.kultivator.elite;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lingkar Petir, the telegraphed tribulation strikes (DESIGN 19.3.8; ISSTH ch. 969: cultivators avoiding or rushing past
 * the Tribulation Lightning; the ground ring itself is a game telegraph). From target Nascent Soul up (config
 * {@code tribulationTelegraph}), 30 ticks before each strike a ring of radius 1.5 is drawn at the player's feet; standing
 * more than 1.5 blocks away when it falls makes the strike graze for 40% (never zero, so DESIGN 0 rule 3 holds). Targets
 * Immortal Ascension and True Immortal get twin rings on every 4th strike. Also the Menantang Langit quality bonus: +0.12
 * when every telegraphed strike was taken in full, otherwise −0.02 per graze (floor −0.10). Pure state, no block is read
 * or changed; {@code Tribulation} calls {@link #begin}, {@link #telegraph} and {@link #impact}.
 */
public final class LightningRings {

	public static final int MODE_UNSET = -1;
	/** The normal rules (NS+ rings, IA/TI twins every 4th strike). */
	public static final int MODE_NORMAL = 0;
	/** Rings at any target ({@code uji tribulasi ... cincin}). */
	public static final int MODE_RINGS = 1;
	/** Every ring a twin ({@code uji tribulasi ... kembar}). */
	public static final int MODE_TWINS = 2;
	/** Command words of the modes. */
	public static final String[] MODE_WORDS = {"biasa", "cincin", "kembar"};

	public static final int TELEGRAPH_TICKS = CombatFxIds.RING_TICKS;
	public static final double RING_RADIUS = 1.5D;
	/** A strike outside every ring lands at this share. */
	public static final double GRAZE_FACTOR = 0.4D;
	public static final int TWIN_EVERY = 4;
	public static final double TWIN_DISTANCE = 3.0D;
	/** Balance change (flow audit): rings from target Nascent Soul (was Deity Transformation), the first hard tribulation. */
	public static final Realm RINGS_FROM = Realm.NASCENT_SOUL;
	public static final Realm TWINS_FROM = Realm.IMMORTAL_ASCENSION;
	/** Menantang Langit (DESIGN 19.3.8). */
	public static final double DEFY_BONUS = 0.12D;
	public static final double GRAZE_PENALTY = 0.02D;
	public static final double PENALTY_FLOOR = -0.10D;

	/** The rings of one tribulation (also read for the quality bonus right after it ended). */
	public static final class State {
		final boolean rings;
		final boolean twins;
		final boolean allTwins;
		final boolean test;
		int ringStrike = -1;
		Vec3 centre = Vec3.ZERO;
		@Nullable Vec3 twin;
		long strikeAt;
		int telegraphed;
		int grazes;
		boolean finished;

		State(boolean rings, boolean twins, boolean allTwins, boolean test) {
			this.rings = rings;
			this.twins = twins;
			this.allTwins = allTwins;
			this.test = test;
		}

		/** True when this tribulation telegraphs its strikes. */
		public boolean rings() {
			return rings;
		}
	}

	/** The result of one strike against the rings: the damage share and where the bolt lands. */
	public record Impact(double factor, Vec3 boltAt, boolean grazed, @Nullable Vec3 twin) {
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** {@code uji tribulasi ... biasa|cincin|kembar}: the mode of the player's next test tribulation. */
	private static final Map<UUID, Integer> FORCED = new HashMap<>();

	static void init() {
		Breakthrough.addQualityBonusProvider(new Breakthrough.QualityBonus() {
			@Override
			public double bonus(ServerPlayer p, CultivationData d, Rank target) {
				return defyBonus(p);
			}
		});
		Tribulation.addListener(new Tribulation.Listener() {
			@Override
			public void onFinish(ServerPlayer p, Rank t, @Nullable Tribulation.Result result, int strikesDone, boolean test) {
				finish(p, result, test);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STATES.clear();
			FORCED.clear();
		});
		// Tribulation.onLeave aborts a running tribulation; a player who left needs no ring state.
		ServerPlayerEvents.LEAVE.register(player -> {
			STATES.remove(player.getUUID());
			FORCED.remove(player.getUUID());
		});
	}

	/** Sets (or with {@link #MODE_UNSET} clears) the ring mode of the player's next test tribulation. */
	public static void forceNext(ServerPlayer player, int mode) {
		if (mode < MODE_NORMAL || mode > MODE_TWINS) {
			FORCED.remove(player.getUUID());
		} else {
			FORCED.put(player.getUUID(), mode);
		}
	}

	/** A tribulation of {@code strength} starts: decides its rings (called by {@code Tribulation.start}). */
	public static State begin(ServerPlayer player, Rank strength, boolean test) {
		Integer forced = FORCED.remove(player.getUUID());
		int mode = test && forced != null ? forced : MODE_NORMAL;
		boolean rings;
		boolean twins;
		boolean allTwins = false;
		if (mode == MODE_TWINS) {
			rings = true;
			twins = true;
			allTwins = true;
		} else if (mode == MODE_RINGS) {
			rings = true;
			twins = strength.realm().atLeast(TWINS_FROM);
		} else {
			rings = KultivatorConfig.get().tribulationTelegraph && strength.realm().atLeast(RINGS_FROM);
			twins = rings && strength.realm().atLeast(TWINS_FROM);
		}
		State state = new State(rings, twins, allTwins, test);
		STATES.put(player.getUUID(), state);
		return state;
	}

	/**
	 * 30 ticks before strike {@code number}: captures the ring at the player's feet (and the twin), draws it. False when
	 * this tribulation has no rings. {@code strikeAt} is the game time the strike falls.
	 */
	public static boolean telegraph(ServerPlayer player, @Nullable State state, int number, Vec3 start, int variant, long strikeAt) {
		if (state == null || !state.rings) {
			return false;
		}
		ServerLevel level = player.level();
		state.ringStrike = number;
		state.centre = player.position();
		state.strikeAt = strikeAt;
		state.twin = state.allTwins || state.twins && number % TWIN_EVERY == 0 ? twinSpot(state.centre, start, player.getRandom()) : null;
		int id = CombatFxIds.TRIB_RING + Math.max(0, Math.min(2, variant));
		Fx.send(level, state.centre, state.centre, FxType.ELITE, id, number, (float) RING_RADIUS);
		if (state.twin != null) {
			Fx.send(level, state.twin, state.twin, FxType.ELITE, id, number + CombatFxIds.TWIN_OFFSET, (float) RING_RADIUS);
		}
		level.playSound(null, state.centre.x, state.centre.y, state.centre.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.WEATHER, 0.7F, 1.6F);
		return true;
	}

	/**
	 * Strike {@code number} falls: inside a ring (or its twin) it hits in full at the player's feet, otherwise it grazes
	 * for {@link #GRAZE_FACTOR} and the bolt lands in the ring. Without a ring for this strike it is a normal full hit.
	 */
	public static Impact impact(ServerPlayer player, @Nullable State state, int number) {
		Vec3 feet = player.position();
		if (state == null || !state.rings || state.ringStrike != number) {
			return new Impact(1.0D, feet, false, null);
		}
		state.ringStrike = -1;
		state.telegraphed++;
		boolean inside = within(feet, state.centre) || state.twin != null && within(feet, state.twin);
		if (inside) {
			return new Impact(1.0D, feet, false, state.twin);
		}
		state.grazes++;
		return new Impact(GRAZE_FACTOR, new Vec3(state.centre.x, feet.y, state.centre.z), true, state.twin);
	}

	/** The extra visuals of an impact: the twin's bolt and the graze arc (particles only, never a LightningBolt). */
	public static void impactFx(ServerPlayer player, Impact impact, int variant, int number) {
		ServerLevel level = player.level();
		if (impact.twin() != null) {
			Fx.send(level, impact.twin(), impact.twin().add(0.0D, Tribulation.SKY_HEIGHT, 0.0D), FxType.TRIBULATION_STRIKE, variant, number, 1.0F);
		}
		if (impact.grazed()) {
			Fx.send(level, impact.boltAt().add(0.0D, 0.3D, 0.0D), player.position().add(0.0D, 1.0D, 0.0D), FxType.ELITE, CombatFxIds.GRAZE, variant, 0.0F);
			Msg.actionBar(player, Component.translatable("kultivator.combat.ring.grazed",
					Math.round(GRAZE_FACTOR * 100.0D)).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** The ring part of {@code sessionExtra.trib}: {@code rx, rz, rend} while a ring waits, {@code tx, tz} for a twin. */
	public static void writeHud(@Nullable State state, CompoundTag trib) {
		trib.putBoolean("tele", state != null && state.rings);
		if (state == null || !state.rings || state.ringStrike < 0) {
			return;
		}
		trib.putDouble("rx", state.centre.x);
		trib.putDouble("rz", state.centre.z);
		trib.putLong("rend", state.strikeAt);
		trib.putDouble("rr", RING_RADIUS);
		if (state.twin != null) {
			trib.putDouble("tx", state.twin.x);
			trib.putDouble("tz", state.twin.z);
		}
	}

	/** Menantang Langit: +0.12 with every telegraphed strike taken in full, else −0.02 per graze (floor −0.10). */
	public static double defyBonus(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		if (state == null || state.finished || state.telegraphed <= 0) {
			return 0.0D;
		}
		return state.grazes == 0 ? DEFY_BONUS : Math.max(PENALTY_FLOOR, -GRAZE_PENALTY * state.grazes);
	}

	private static void finish(ServerPlayer player, @Nullable Tribulation.Result result, boolean test) {
		State state = STATES.get(player.getUUID());
		if (state == null || state.finished) {
			return;
		}
		state.finished = true;
		if (result != Tribulation.Result.SUCCESS || state.telegraphed <= 0) {
			return;
		}
		if (state.grazes == 0) {
			Advancements.grant(player, "defy_heaven");
			Msg.chat(player, Component.translatable(test ? "kultivator.combat.defy.full_test" : "kultivator.combat.defy.full",
					String.format(Locale.ROOT, "+%.2f", DEFY_BONUS)).withStyle(ChatFormatting.GOLD));
		} else {
			Msg.chat(player, Component.translatable(test ? "kultivator.combat.defy.grazed_test" : "kultivator.combat.defy.grazed", state.grazes,
					String.format(Locale.ROOT, "%.2f", Math.max(PENALTY_FLOOR, -GRAZE_PENALTY * state.grazes))).withStyle(ChatFormatting.GRAY));
		}
	}

	private static boolean within(Vec3 feet, Vec3 centre) {
		double dx = feet.x - centre.x;
		double dz = feet.z - centre.z;
		return dx * dx + dz * dz <= RING_RADIUS * RING_RADIUS;
	}

	/** A spot 3 blocks from the ring that stays inside the tribulation ground (6 blocks from its start). */
	private static Vec3 twinSpot(Vec3 centre, Vec3 start, RandomSource random) {
		double limit = Tribulation.MAX_DISTANCE - 0.5D;
		double base = random.nextDouble() * Math.PI * 2.0D;
		for (int i = 0; i < 8; i++) {
			double angle = base + i * Math.PI / 4.0D;
			Vec3 spot = centre.add(Math.cos(angle) * TWIN_DISTANCE, 0.0D, Math.sin(angle) * TWIN_DISTANCE);
			double dx = spot.x - start.x;
			double dz = spot.z - start.z;
			if (dx * dx + dz * dz <= limit * limit) {
				return spot;
			}
		}
		Vec3 back = new Vec3(start.x - centre.x, 0.0D, start.z - centre.z);
		back = back.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : back.normalize();
		return centre.add(back.scale(TWIN_DISTANCE));
	}

	private LightningRings() {
	}
}
