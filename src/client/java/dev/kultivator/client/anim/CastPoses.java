package dev.kultivator.client.anim;

import dev.kultivator.client.anim.model.ArtModels;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.TechniqueType;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The cast animation registry (DESIGN 27.2, polish plan C3): which pose each player or disciple is playing, fed by every
 * {@code TechFx} event ({@link #onTechFx}, called first thing by {@code SpecFx.play}) and by the disciple power and path
 * effects ({@link #start}). Timing per pose: a windup (the anticipation), the strike (easeOutBack), a hold (instant casts
 * 4 ticks, beams and fronts their travel time up to 30, channels and domains their duration up to 200 with a lighter
 * "aura idle" after the first two seconds), then a 6 tick recovery; END recovers in 6 and ABORT in 4. Charges hold the
 * charge pose until release (the TRAVEL or the first event after the charge), then cross-fade into the release pose.
 * IMPACT, PULSE, TRAVEL and EXTRA events of the same cast accent the running pose (at most one per 4 ticks); alternating
 * poses (fists) swap arms on each accent.
 * <p>
 * Times are entity ages in ticks ({@code tickCount + partialTick}), so a pose plays the same for every viewer and freezes
 * with the entity. Entries are dropped when they finish, on a level change and when the entity is gone. Render thread
 * and client tick only.
 */
public final class CastPoses {

	/** Recovery ticks after the hold, after END and after ABORT. */
	private static final int RECOVER = 6;
	private static final int RECOVER_ABORT = 4;
	/** Cross-fade from the previous pose into a new one. */
	private static final float CROSS = 3.0F;
	/** At most one accent per this many ticks. */
	private static final float ACCENT_GAP = 4.0F;
	private static final int MAX_HOLD = 200;

	/** One playing pose. */
	static final class Active {
		final Pose pose;
		final PoseLibrary.Def def;
		final int specNet;
		float start = Float.NaN;
		float hold;
		float intensity;
		boolean charging;
		@Nullable Pose release;
		@Nullable Pose then;
		boolean mirror;
		boolean baseMirror;
		boolean linger;
		float accentAt = -1.0E9F;
		float stopAt = Float.NaN;
		int recover = RECOVER;
		// cross-fade snapshot of the pose this one replaced
		@Nullable float[] fromVal;
		@Nullable float[] fromW;
		float crossStart = Float.NaN;
		/** Prune passes spent waiting for an entity that was not loaded yet. */
		int waits;

		Active(Pose pose, PoseLibrary.Def def, int specNet) {
			this.pose = pose;
			this.def = def;
			this.specNet = specNet;
		}

		float holdEnd() {
			return def.windTicks() + def.strikeTicks() + hold;
		}
	}

	/** A frame's evaluated pose for one entity (written into the render state by the extract mixins). */
	public record Snapshot(Pose pose, float[] val, float[] w, @Nullable float[] fromVal, @Nullable float[] fromW, float cross,
			boolean twoHanded) {

		/** The final value of a whole-body channel (LIFT, TILT, LEAN, TWIST) over a neutral 0. */
		public float body(int channel) {
			float now = Float.isNaN(val[channel]) ? 0.0F : val[channel] * w[channel];
			if (fromVal == null || fromW == null || cross >= 1.0F) {
				return now;
			}
			float before = Float.isNaN(fromVal[channel]) ? 0.0F : fromVal[channel] * fromW[channel];
			return Mth.lerp(cross, before, now);
		}

		/** The strongest weight of any channel (0 = nothing drives the model). */
		public float strength() {
			float max = 0.0F;
			for (int c = 0; c < w.length; c++) {
				max = Math.max(max, w[c]);
			}
			if (fromW != null && cross < 1.0F) {
				for (int c = 0; c < fromW.length; c++) {
					max = Math.max(max, fromW[c] * (1.0F - cross));
				}
			}
			return max;
		}
	}

	private static final Map<Integer, Active> ACTIVE = new HashMap<>();

	// ================================================================== API (contract C3)

	/**
	 * Feeds one technique event: starts, accents, releases or ends the caster's pose (any phase, END and ABORT too), and
	 * lets {@link ArtModels} place the 3D props this module owns. Never throws on odd input.
	 */
	public static void onTechFx(Payloads.TechFx p, TechniqueSpec spec, @Nullable Entity caster) {
		Phase phase = Phase.byOrdinal(p.phase());
		ArtModels.onTechFx(p, spec, phase, caster);
		ClientForms.onTechFx(p, spec, phase, caster);
		if (!(caster instanceof LivingEntity living) || !humanoid(caster)) {
			return;
		}
		int id = caster.getId();
		float age = living.tickCount;
		String name = phase.name();
		if ("ABORT".equals(name)) {
			stopAt(id, age, RECOVER_ABORT);
			return;
		}
		if (phase == Phase.END) {
			Active active = ACTIVE.get(id);
			if (active != null && active.specNet == spec.netId()) {
				if ("overlord_fury".equals(spec.id()) || active.pose == Pose.DASH_LEAN && spec.type() == TechniqueType.GERAK) {
					// a leap or dash ends on its feet: a light landing kneel
					begin(living, Pose.KNEEL_STRIKE, 3, 0.6F, spec.netId(), null, null, false);
				} else {
					stopAt(id, age, RECOVER);
				}
			}
			return;
		}
		if (phase == Phase.MASS_HIT || p.has(Payloads.TechFx.FLAG_DOT)) {
			return;
		}
		Active active = ACTIVE.get(id);
		boolean same = active != null && active.specNet == spec.netId() && Float.isNaN(active.stopAt);
		Pose authored = authored(spec, phase);
		int duration = Math.max(0, p.duration());

		if (phase == Phase.CAST || phase == Phase.CHARGE) {
			if (phase == Phase.CAST && same && active.charging) {
				release(living, active, spec, age);
				return;
			}
			boolean charged = spec.charge() != null && (phase == Phase.CHARGE || !same);
			if (charged) {
				if (same && active.charging) {
					active.hold = Math.max(active.hold, duration);
					return;
				}
				Pose chargePose = chargePose(spec);
				Pose release = releasePose(spec, chargePose);
				begin(living, chargePose, duration > 0 ? duration : 60, 1.0F, spec.netId(), release, null, true);
				return;
			}
			if (phase == Phase.CHARGE) {
				return;
			}
			Pose pose = authored != Pose.AUTO ? authored : castPose(spec);
			if (p.has(Payloads.TechFx.FLAG_BLINK)) {
				pose = Pose.DASH_LEAN;
			}
			if (!pose.explicit()) {
				return;
			}
			begin(living, pose, holdFor(spec, pose, duration), 1.0F, spec.netId(), null, thenPose(spec, pose), false);
			return;
		}

		// later phases of the cast
		if (p.has(Payloads.TechFx.FLAG_BLINK) && phase == Phase.TRAVEL) {
			begin(living, Pose.DASH_LEAN, Math.max(4, duration), 1.0F, spec.netId(), null, null, false);
			return;
		}
		if (authored.explicit()) {
			begin(living, authored, Math.max(4, Math.min(30, duration)), 1.0F, spec.netId(), null, null, false);
			return;
		}
		if (!same) {
			return;
		}
		if (active.charging) {
			release(living, active, spec, age);
			return;
		}
		if (active.then != null && (phase == Phase.TRAVEL || phase == Phase.IMPACT || phase.ordinal() >= Phase.EXTRA0.ordinal()
				&& phase.ordinal() <= Phase.EXTRA7.ordinal() || p.has(Payloads.TechFx.FLAG_PRE_IMPACT))) {
			Pose next = active.then;
			active.then = null;
			begin(living, next, Math.max(4, Math.min(30, duration)), 1.0F, spec.netId(), null, null, false);
			return;
		}
		if (phase == Phase.HIT && active.pose != Pose.FINGER_POINT && active.pose != Pose.SEAL) {
			return;
		}
		accentEntry(active, age);
	}

	/** Starts {@code pose} on an entity for {@code holdTicks} (disciple powers, paths, epic scenes). */
	public static void start(int entityId, Pose pose, int holdTicks) {
		start(entityId, pose, holdTicks, 1.0F);
	}

	public static void start(int entityId, Pose pose, int holdTicks, float intensity) {
		if (pose == null || !pose.explicit() || PoseLibrary.def(pose) == null) {
			return;
		}
		Entity entity = entity(entityId);
		if (entity instanceof LivingEntity living && humanoid(entity)) {
			begin(living, pose, Mth.clamp(holdTicks, 0, MAX_HOLD * 3), Mth.clamp(intensity, 0.1F, 1.5F), -1, null, null, false);
		} else {
			// not loaded yet: start when it is first drawn
			PoseLibrary.Def def = PoseLibrary.def(pose);
			Active active = new Active(pose, def, -1);
			active.hold = Mth.clamp(holdTicks, 0, MAX_HOLD * 3);
			active.intensity = Mth.clamp(intensity, 0.1F, 1.5F);
			ACTIVE.put(entityId, active);
		}
	}

	/** A short snap of the running pose (one blow of a combo, one bolt of a storm). */
	public static void accent(int entityId) {
		Active active = ACTIVE.get(entityId);
		Entity entity = entity(entityId);
		if (active != null && entity != null) {
			accentEntry(active, entity.tickCount);
		}
	}

	/** Recovers out of the running pose (fast). */
	public static void stop(int entityId) {
		Entity entity = entity(entityId);
		if (entity == null) {
			ACTIVE.remove(entityId);
			return;
		}
		stopAt(entityId, entity.tickCount, RECOVER_ABORT);
	}

	/** True while a pose drives this entity (including its recovery). */
	public static boolean active(int entityId) {
		return ACTIVE.containsKey(entityId);
	}

	/** The playing pose of an entity, or null. */
	public static @Nullable Pose poseOf(int entityId) {
		Active active = ACTIVE.get(entityId);
		return active == null ? null : active.pose;
	}

	/** Pure sample for giant figures and avatars (see {@link PoseLibrary#sample}). */
	public static PoseAngles sample(Pose pose, float t01, float ageTicks) {
		return PoseLibrary.sample(pose, t01, ageTicks);
	}

	// ================================================================== evaluation

	/**
	 * The pose of this entity at {@code age} (its tickCount plus the partial tick), or null when none plays. Finished
	 * entries are removed here.
	 */
	public static @Nullable Snapshot snapshot(Entity entity, float age) {
		Active active = ACTIVE.get(entity.getId());
		if (active == null) {
			return null;
		}
		if (Float.isNaN(active.start)) {
			active.start = age;
			if (entity instanceof LivingEntity living && living.getMainArm() == HumanoidArm.LEFT) {
				active.baseMirror = true;
				active.mirror = true;
			}
		}
		if (age + 1.0F < active.start) {
			// the entity's age went backwards (respawned with the same id)
			ACTIVE.remove(entity.getId());
			return null;
		}
		float[] val = new float[PoseLibrary.CHANNELS];
		float[] w = new float[PoseLibrary.CHANNELS];
		float weight = evaluate(active, age, val);
		if (weight <= 0.0F && age - active.start > 1.0F) {
			ACTIVE.remove(entity.getId());
			return null;
		}
		fillWeights(active, weight, val, w);
		float cross = 1.0F;
		if (active.fromVal != null && !Float.isNaN(active.crossStart)) {
			cross = Mth.clamp((age - active.crossStart) / CROSS, 0.0F, 1.0F);
			cross = PoseLibrary.easeInOutSine(cross);
		}
		return new Snapshot(active.pose, val, w, cross < 1.0F ? active.fromVal : null, cross < 1.0F ? active.fromW : null, cross,
				active.def.twoHanded());
	}

	/** Key values into {@code val}; returns the overall weight 0..1. */
	private static float evaluate(Active a, float age, float[] val) {
		float t = Math.max(0.0F, age - a.start);
		float end = a.holdEnd();
		float keyT = Math.min(t, end);
		PoseLibrary.keyAt(a.def, keyT, a.hold, age, a.mirror, val);
		// accent: a 2 tick snap back towards the windup, then a 2.5 tick return
		float since = age - a.accentAt;
		if (since >= 0.0F && since < 4.5F) {
			float amount = (since < 2.0F ? since / 2.0F : 1.0F - (since - 2.0F) / 2.5F) * 0.35F * Math.min(1.0F, a.intensity);
			float[] wind = new float[PoseLibrary.CHANNELS];
			PoseLibrary.keyAt(a.def, 0.0F, a.hold, age, a.mirror, wind);
			for (int c = 0; c < val.length; c++) {
				if (!Float.isNaN(val[c]) && !Float.isNaN(wind[c])) {
					val[c] = Mth.lerp(amount, val[c], wind[c]);
				}
			}
		}
		float weight;
		if (!Float.isNaN(a.stopAt)) {
			float before = weightAt(a, a.stopAt - a.start);
			weight = before * (1.0F - PoseLibrary.easeInOutSine((age - a.stopAt) / Math.max(1, a.recover)));
		} else if (t <= end) {
			weight = weightAt(a, t);
		} else {
			float before = weightAt(a, end);
			weight = before * (1.0F - PoseLibrary.easeInOutSine((t - end) / Math.max(1, a.recover)));
		}
		return Mth.clamp(weight, 0.0F, 1.0F) * Math.min(1.0F, a.intensity);
	}

	private static float weightAt(Active a, float t) {
		int windup = Math.max(1, a.def.windTicks());
		if (t < windup) {
			return PoseLibrary.easeOutCubic(t / windup);
		}
		if (a.linger) {
			float into = t - windup - a.def.strikeTicks() - 40.0F;
			if (into > 0.0F) {
				// the aura idle: a lighter presence after the first two seconds of a long domain
				return Mth.lerp(Math.min(1.0F, into / 20.0F), 1.0F, 0.4F);
			}
		}
		return 1.0F;
	}

	private static void fillWeights(Active a, float weight, float[] val, float[] w) {
		for (int c = 0; c < val.length; c++) {
			w[c] = Float.isNaN(val[c]) ? 0.0F : weight;
		}
		if (a.linger && weight < 0.99F) {
			// the arms blend at half, the legs go back to the walk
			for (int c : new int[] {PoseLibrary.RLX, PoseLibrary.RLZ, PoseLibrary.LLX, PoseLibrary.LLZ, PoseLibrary.RLY, PoseLibrary.LLY}) {
				w[c] *= 0.5F;
			}
		}
	}

	// ================================================================== transitions

	private static void begin(LivingEntity entity, Pose pose, int hold, float intensity, int specNet, @Nullable Pose release,
			@Nullable Pose then, boolean charging) {
		PoseLibrary.Def def = PoseLibrary.def(pose);
		if (def == null) {
			return;
		}
		int id = entity.getId();
		float age = entity.tickCount;
		Active previous = ACTIVE.get(id);
		Active next = new Active(pose, def, specNet);
		next.start = age;
		next.hold = Mth.clamp(hold, 0, MAX_HOLD * 3);
		next.intensity = intensity;
		next.release = release;
		next.then = then;
		next.charging = charging;
		next.linger = hold > 60 && !charging;
		next.baseMirror = entity.getMainArm() == HumanoidArm.LEFT;
		next.mirror = next.baseMirror;
		if (previous != null && !Float.isNaN(previous.start)) {
			// remember where the old pose was, so the new one starts from there
			float[] val = new float[PoseLibrary.CHANNELS];
			float[] w = new float[PoseLibrary.CHANNELS];
			float weight = evaluate(previous, age, val);
			fillWeights(previous, weight, val, w);
			next.fromVal = val;
			next.fromW = w;
			next.crossStart = age;
			if (previous.pose == pose && def.alternate()) {
				next.mirror = !previous.mirror;
			}
			// a pose already up (the same raise again) skips most of its windup
			if (previous.pose == pose && Float.isNaN(previous.stopAt)) {
				next.start = age - def.windTicks();
			}
		}
		ACTIVE.put(id, next);
	}

	private static void release(LivingEntity entity, Active active, TechniqueSpec spec, float age) {
		Pose release = active.release != null ? active.release : releasePose(spec, active.pose);
		if (release == null || release == active.pose) {
			active.charging = false;
			active.hold = Math.max(0.0F, age - active.start - active.def.windTicks() - active.def.strikeTicks()) + 6.0F;
			accentEntry(active, age);
			return;
		}
		begin(entity, release, 6, 1.0F, active.specNet, null, null, false);
		Active next = ACTIVE.get(entity.getId());
		if (next != null) {
			// the body is already wound up: go straight into the strike
			next.start = age - Math.max(0, next.def.windTicks() - 1);
		}
	}

	private static void accentEntry(Active active, float age) {
		if (age - active.accentAt < ACCENT_GAP) {
			return;
		}
		if (!Float.isNaN(active.stopAt)) {
			return;
		}
		active.accentAt = age;
		if (active.def.alternate()) {
			active.mirror = !active.mirror;
			active.fromVal = null;
		}
		// an accent late in the hold keeps the pose up a little longer
		float t = age - active.start;
		float end = active.holdEnd();
		if (t > end - 4.0F) {
			active.hold += Math.min(8.0F, t - end + 6.0F);
		}
	}

	private static void stopAt(int id, float age, int recover) {
		Active active = ACTIVE.get(id);
		if (active == null || !Float.isNaN(active.stopAt)) {
			return;
		}
		if (Float.isNaN(active.start)) {
			ACTIVE.remove(id);
			return;
		}
		active.stopAt = age;
		active.recover = recover;
	}

	// ================================================================== pose choice

	/** The explicitly authored pose of a phase (M1's {@code .pose}), else AUTO. */
	private static Pose authored(TechniqueSpec spec, Phase phase) {
		Pose pose = spec.pose(phase);
		if (pose != null && pose.explicit()) {
			return pose;
		}
		if (phase == Phase.CAST) {
			Pose[] table = TABLE.get(spec.id());
			if (table != null && table[0] != null) {
				return table[0];
			}
		}
		return Pose.AUTO;
	}

	/** The CAST pose: authored, the plan's table, the shared resolver, else this module's own choice. */
	static Pose castPose(TechniqueSpec spec) {
		Pose authored = authored(spec, Phase.CAST);
		if (authored.explicit()) {
			return authored;
		}
		Pose resolved = spec.resolvedPose(Phase.CAST);
		if (resolved != null && resolved.explicit()) {
			return refine(spec, resolved);
		}
		return autoPose(spec);
	}

	/** The plan's per-Dao refinements of the shared resolver (CONE fire and ice push with both palms...). */
	private static Pose refine(TechniqueSpec spec, Pose pose) {
		Shape.Kind kind = firstKind(spec);
		Dao dao = spec.dao();
		if (kind == Shape.Kind.CONE && (dao == Dao.FIRE || dao == Dao.ICE) && pose == Pose.PALM_THRUST) {
			return Pose.TWIN_PALM;
		}
		return pose;
	}

	private static Pose chargePose(TechniqueSpec spec) {
		Pose authored = spec.pose(Phase.CHARGE);
		if (authored != null && authored.explicit()) {
			return authored;
		}
		Pose resolved = spec.resolvedPose(Phase.CHARGE);
		if (resolved != null && resolved.explicit()) {
			return resolved;
		}
		Pose cast = castPose(spec);
		return cast == Pose.CHARGE_OVERHEAD || cast == Pose.SWORD_SKY || cast == Pose.SUMMON_RAISE ? cast : Pose.CHARGE_BACK;
	}

	/** The strike after a charge: the CAST pose when it differs from the charge pose, else the natural follow-up. */
	private static @Nullable Pose releasePose(TechniqueSpec spec, Pose chargePose) {
		Pose cast = castPose(spec);
		if (cast.explicit() && cast != chargePose && cast != Pose.CHARGE_BACK && cast != Pose.CHARGE_OVERHEAD) {
			return cast;
		}
		Pose[] table = TABLE.get(spec.id());
		if (table != null && table[1] != null) {
			return table[1];
		}
		Dao dao = spec.dao();
		return switch (chargePose) {
			case CHARGE_OVERHEAD, SUMMON_RAISE -> Pose.TWIN_PALM;
			case SWORD_SKY -> Pose.SWORD_SLASH;
			case SKY_RAISE -> Pose.PALM_THRUST;
			case CHARGE_BACK -> dao == Dao.SWORD ? Pose.SWORD_THRUST : dao == Dao.BODY ? Pose.PUNCH : Pose.PALM_THRUST;
			default -> null;
		};
	}

	/** The plan's second pose of a two-beat cast, unless the spec authors poses for later phases itself. */
	private static @Nullable Pose thenPose(TechniqueSpec spec, Pose castPose) {
		for (Phase phase : Phase.values()) {
			if (phase != Phase.CAST && phase != Phase.CHARGE) {
				Pose pose = spec.pose(phase);
				if (pose != null && pose.explicit()) {
					return null;
				}
			}
		}
		Pose[] table = TABLE.get(spec.id());
		if (table != null && table[1] != null && table[1] != castPose) {
			return table[1];
		}
		return null;
	}

	/** Hold ticks of a CAST pose: instant 4, beams and fronts their travel, channels and domains their duration. */
	private static int holdFor(TechniqueSpec spec, Pose pose, int duration) {
		Shape.Kind kind = firstKind(spec);
		if (spec.channel() != null || spec.toggle() != null || kind.lasting() || spec.type() == TechniqueType.WILAYAH) {
			return Mth.clamp(duration > 0 ? duration : 100, 20, MAX_HOLD);
		}
		if (pose == Pose.MEDITATE_FLOAT || pose == Pose.PRAY || pose == Pose.FLAME_CUP) {
			return Mth.clamp(duration > 0 ? duration : 60, 20, MAX_HOLD);
		}
		if (spec.has(TechniqueSpec.Flag.TITAH)) {
			// Phase 10b (29.5): a decree holds its call until its next beat (the throw, the seal's rise, the stars' fall)
			return Mth.clamp(duration > 0 ? Math.min(duration, 40) : 20, 10, 40);
		}
		return switch (kind) {
			case BEAM, FRONT, WAVE, RAIN, VOLLEY, CHAIN -> Mth.clamp(duration > 0 ? duration : 10, 6, 30);
			case ZONE, PILLAR -> Mth.clamp(duration > 0 ? duration : 12, 8, 30);
			case PATH -> Mth.clamp(duration > 0 ? duration : 8, 4, 20);
			case SELF -> Mth.clamp(duration > 0 ? Math.min(duration, 24) : 8, 6, 24);
			default -> 4;
		};
	}

	/** This module's own choice when nothing else names a pose (plan 27.2.4 table). */
	static Pose autoPose(TechniqueSpec spec) {
		Shape.Kind kind = firstKind(spec);
		Dao dao = spec.dao();
		TechniqueType type = spec.type();
		if (type == TechniqueType.WILAYAH || kind.lasting()) {
			return Pose.ARMS_SPREAD;
		}
		if (type == TechniqueType.PANGGIL) {
			return Pose.SUMMON_RAISE;
		}
		if (type == TechniqueType.GERAK || kind == Shape.Kind.PATH) {
			return Pose.DASH_LEAN;
		}
		if (type == TechniqueType.UBAH) {
			return Pose.SUMMON_RAISE;
		}
		return switch (kind) {
			case STRIKE -> dao == Dao.BODY ? Pose.PUNCH : dao == Dao.SWORD ? Pose.SWORD_SLASH : Pose.PALM_THRUST;
			case TARGET -> Pose.FINGER_POINT;
			case CONE -> dao == Dao.FIRE || dao == Dao.ICE ? Pose.TWIN_PALM : Pose.PALM_THRUST;
			case BEAM -> dao == Dao.SWORD ? Pose.SWORD_THRUST : dao == Dao.LIGHTNING ? Pose.SKY_RAISE : Pose.TWIN_PALM;
			case FRONT, WAVE -> dao == Dao.SWORD ? Pose.SWORD_SLASH : Pose.TWIN_PALM;
			case NOVA -> dao == Dao.BODY || dao == Dao.ICE || dao == Dao.LIFE ? Pose.STOMP : Pose.ARMS_SPREAD;
			case ZONE, PILLAR, RAIN -> dao == Dao.SWORD ? Pose.SWORD_SKY : Pose.SKY_RAISE;
			case CHAIN -> Pose.SKY_RAISE;
			case VOLLEY -> dao == Dao.SWORD ? Pose.SWORD_THRUST : Pose.PALM_THRUST;
			case ALL, RECALL -> Pose.SEAL;
			case SELF -> type == TechniqueType.PULIH ? Pose.MEDITATE_FLOAT : Pose.SEAL;
			default -> Pose.SEAL;
		};
	}

	private static Shape.Kind firstKind(TechniqueSpec spec) {
		for (Step step : spec.timeline()) {
			Shape.Kind kind = step.shape().kind();
			if (kind != Shape.Kind.SELF) {
				return kind;
			}
		}
		return Shape.Kind.SELF;
	}

	/** Players and disciples (the PlayerModel casters); other mobs have no cast poses. */
	private static boolean humanoid(Entity entity) {
		return entity instanceof Avatar || entity instanceof DiscipleEntity;
	}

	private static @Nullable Entity entity(int id) {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? null : level.getEntity(id);
	}

	/** Forgets entities that are gone (called now and then from the client tick). */
	public static void prune(@Nullable ClientLevel level) {
		if (level == null) {
			ACTIVE.clear();
			return;
		}
		for (Iterator<Map.Entry<Integer, Active>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Active> entry = it.next();
			Entity entity = level.getEntity(entry.getKey());
			Active active = entry.getValue();
			if (entity == null) {
				if (!Float.isNaN(active.start) || ++active.waits > 4) {
					it.remove();
				}
			} else if (entity.isRemoved()) {
				it.remove();
			} else if (!Float.isNaN(active.start) && entity.tickCount - active.start > active.holdEnd() + 40.0F && !active.charging) {
				it.remove();
			} else if (active.charging && entity.tickCount - active.start > active.holdEnd() + 200.0F) {
				// a charge whose release never arrived
				it.remove();
			}
		}
	}

	public static void clear() {
		ACTIVE.clear();
	}

	// ================================================================== the plan's pose table (27 section 7)

	/** spec id -> {CAST pose, second beat or release pose}. Used when the spec authors no pose of its own. */
	private static final Map<String, Pose[]> TABLE = new HashMap<>();

	private static void t(String id, Pose cast) {
		TABLE.put(id, new Pose[] {cast, null});
	}

	private static void t(String id, Pose cast, Pose then) {
		TABLE.put(id, new Pose[] {cast, then});
	}

	static {
		// BodyArts
		t("octane_blast", Pose.PUNCH);
		t("worldwalking", Pose.KNEEL_STRIKE);
		t("throbbing_pulse", Pose.STOMP);
		t("disorderly_cloak_hammer", Pose.OVERHEAD_SLAM);
		t("bright_king_art", Pose.SEAL);
		t("great_way_of_buddha", Pose.PRAY);
		t("dragonblood_warrior", Pose.ROAR);
		t("six_paths_fist", Pose.PUNCH);
		t("eighth_hex_body", Pose.SEAL, Pose.OVERHEAD_SLAM);
		t("undying_emperor_fist", Pose.PUNCH);
		t("golden_ape_roar", Pose.CHEST_BEAT, Pose.ROAR);
		t("cosmic_giant_form", Pose.SUMMON_RAISE);
		t("ancient_god_trample", Pose.STOMP);
		// FireArts
		t("phoenix_fire_line", Pose.ROAR);
		t("phoenix_bathing_fire", Pose.SUMMON_RAISE);
		t("flame_splitting_tsunami", Pose.SWORD_SLASH);
		t("phoenix_arrow", Pose.PALM_THRUST);
		t("ten_thousand_crow_vase", Pose.HOLD_ITEM_UP);
		t("heaven_fire_transformation", Pose.SUMMON_RAISE);
		t("phoenix_sky_roaring_strike", Pose.ROAR);
		t("nine_dragon_fire_cover", Pose.ARMS_SPREAD);
		t("samadhi_true_fire", Pose.SUMMON_RAISE, Pose.TWIN_PALM);
		t("eight_trigrams_furnace", Pose.SEAL);
		t("phoenix_flame_sears_heavens", Pose.SUMMON_RAISE, Pose.DASH_LEAN);
		t("golden_emperor_flame", Pose.ARMS_SPREAD);
		t("tongtian_divine_fire_pillars", Pose.SKY_RAISE);
		t("nine_suns_heavens_fury", Pose.CHARGE_OVERHEAD, Pose.TWIN_PALM);
		t("emperor_flame", Pose.SUMMON_RAISE);
		// IceArts
		t("ice_empress_pincer", Pose.TWIN_PALM);
		t("star_anise_ice_coagulation", Pose.PALM_THRUST);
		t("frozen_cloud_art", Pose.TWIN_PALM);
		t("tongtian_river_freeze", Pose.KNEEL_STRIKE);
		t("qianlan_ice_flame", Pose.PALM_THRUST);
		t("ice_snow_emperors_pride", Pose.SUMMON_RAISE);
		t("cold_ice_formation", Pose.SEAL, Pose.SKY_RAISE);
		t("broken_moon_shadow", Pose.DASH_LEAN, Pose.SWORD_SLASH);
		t("bone_chilling_flame", Pose.ARMS_SPREAD);
		t("unparalleled_chill", Pose.SWORD_THRUST);
		t("frozen_end_divine_art", Pose.ARMS_SPREAD);
		t("ice_phoenix_frost_seal", Pose.SKY_RAISE);
		t("ice_phoenix_descent", Pose.SUMMON_RAISE);
		t("freeze_mount_qi", Pose.ARMS_SPREAD);
		// ImmortalArts
		t("ninth_hex", Pose.SEAL);
		t("five_elements_mountain", Pose.SKY_RAISE, Pose.OVERHEAD_SLAM);
		t("tai_chi_diagram", Pose.SEAL, Pose.TWIN_PALM);
		t("heaven_trampling", Pose.STOMP);
		// LifeArts
		t("eternal_spring_art", Pose.MEDITATE_FLOAT);
		t("blue_silver_entangle", Pose.KNEEL_STRIKE);
		t("earth_vitality", Pose.KNEEL_STRIKE);
		t("blue_silver_parasite", Pose.FINGER_POINT);
		t("clear_heart_melody", Pose.FLUTE);
		t("seven_treasure_pagoda", Pose.HOLD_ITEM_UP);
		t("gold_devouring_beetles", Pose.SEAL);
		t("willow_god_branches", Pose.SUMMON_RAISE);
		t("blood_transforming_formation", Pose.ARMS_SPREAD);
		t("blood_clones", Pose.SUMMON_RAISE);
		t("nezha_lotus_body", Pose.MEDITATE_FLOAT);
		t("guanyin_sweet_dew", Pose.PRAY);
		t("life_death_domain", Pose.ARMS_SPREAD);
		t("bodhisattva_tree", Pose.MEDITATE_FLOAT);
		// LightningArts
		t("zidian_whip", Pose.SWORD_SLASH);
		t("lightning_flash_step", Pose.DASH_LEAN);
		t("lightning_mark_armor", Pose.GATE_FLEX);
		t("three_thunder_styles", Pose.PALM_THRUST);
		t("five_thunder_method", Pose.SEAL, Pose.SKY_RAISE);
		t("lightning_emperor_art", Pose.SKY_RAISE, Pose.PALM_THRUST);
		t("golden_light_formation", Pose.SEAL);
		t("extinction_thundercloud", Pose.SKY_RAISE);
		t("divine_sword_imperial_thunder", Pose.SWORD_SKY, Pose.SWORD_THRUST);
		t("lei_zhenzi_wings", Pose.SUMMON_RAISE);
		t("tribulation_thunder_sword", Pose.SWORD_SKY);
		t("thunderbolt_ancestral_symbol", Pose.SEAL);
		t("tribulation_thunder_emperor_array", Pose.SKY_RAISE);
		t("nine_heavens_thunder_venerable", Pose.SUMMON_RAISE);
		// ShadowArts
		t("shadow_devil_fusion", Pose.SEAL);
		t("samadhi_wind", Pose.TWIN_PALM);
		t("star_god_broken_shadow", Pose.DASH_LEAN, Pose.SWORD_SLASH);
		t("wraith_butterflies", Pose.SUMMON_RAISE);
		t("banana_leaf_fan", Pose.FAN_SWING);
		t("wind_roaring_formation", Pose.ARMS_SPREAD);
		t("soul_falling_formation", Pose.SEAL);
		t("billion_soul_flag", Pose.HOLD_ITEM_UP);
		t("stygian_tiger_seal", Pose.FLUTE);
		t("kunpeng_art", Pose.CHARGE_BACK, Pose.DASH_LEAN);
		t("karma_domain", Pose.ARMS_SPREAD);
		// SpaceArts
		t("space_blade", Pose.SWORD_SLASH);
		t("immortal_binding_rope", Pose.FINGER_POINT);
		t("qiankun_ring", Pose.PALM_THRUST);
		t("gravitational_space", Pose.ARMS_SPREAD);
		t("moon_blade", Pose.SWORD_SLASH);
		t("purple_gold_gourd", Pose.HOLD_ITEM_UP);
		t("heaven_turning_seal", Pose.SKY_RAISE, Pose.OVERHEAD_SLAM);
		t("spatial_ancestral_symbol", Pose.SEAL);
		t("yellow_brow_bag", Pose.HOLD_ITEM_UP);
		t("heavenly_restriction", Pose.SEAL);
		t("void_mirror", Pose.HOLD_ITEM_UP);
		t("time_spirit_domain", Pose.ARMS_SPREAD);
		t("stellar_black_hole", Pose.CHARGE_OVERHEAD);
		// SwordArts
		t("azure_essence_sword", Pose.SWORD_THRUST);
		t("sword_shadow_split", Pose.SWORD_THRUST);
		t("giant_sword_light", Pose.SWORD_SKY, Pose.SWORD_SLASH);
		t("heavenly_wolf_slash", Pose.DASH_LEAN, Pose.SWORD_SLASH);
		t("bloodmoon_immortal_slaying_sword", Pose.SWORD_SKY);
		t("overlord_fury", Pose.DASH_LEAN, Pose.KNEEL_STRIKE);
		t("immortal_tribulation_sword", Pose.SWORD_SKY, Pose.SWORD_THRUST);
		t("grass_symbol_sword", Pose.SWORD_THRUST);
		t("heavenbreaker_stance", Pose.SWORD_SKY, Pose.OVERHEAD_SLAM);
		t("lu_ya_gourd", Pose.HOLD_ITEM_UP);
		t("blood_drop_stance", Pose.SWORD_SLASH);
		t("astral_sword_intent", Pose.ARMS_SPREAD);
		t("zhuxian_sword_formation", Pose.SWORD_SKY);
		t("heaven_punishing_sword", Pose.SWORD_SKY);
		// UniversalArts
		t("ghost_shadow_track", Pose.DASH_LEAN);
		t("mysterious_jade_hand", Pose.SEAL);
		t("purple_demon_eye", Pose.FINGER_POINT);
		t("pear_blossom_needles", Pose.PALM_THRUST);
		t("sleep_insects", Pose.SEAL);
		t("immobilization_spell", Pose.FINGER_POINT);
		t("emperor_seal_jue", Pose.SKY_RAISE, Pose.OVERHEAD_SLAM);
		t("fiery_golden_eyes", Pose.SEAL);
		t("finger_of_death", Pose.FINGER_POINT);
		t("body_beyond_body", Pose.SUMMON_RAISE);
		t("netherworld_white_tiger", Pose.ROAR);
		t("somersault_cloud", Pose.SEAL);
		t("cloud_travel", Pose.SEAL);
		t("qilin_sacred_palace", Pose.ARMS_SPREAD);
		t("seventy_two_transformations", Pose.SEAL);
		t("three_heads_six_arms", Pose.SUMMON_RAISE);
		t("blood_death_world", Pose.ARMS_SPREAD);
		t("bloodshadow_evasion", Pose.DASH_LEAN);
		t("moon_star_restoration", Pose.SEAL);
		t("ji_realm", Pose.TWIN_PALM);
		t("profound_mystery_fusion", Pose.CHARGE_OVERHEAD, Pose.TWIN_PALM);
		t("buddha_wrath_tang_lotus", Pose.CHARGE_OVERHEAD, Pose.TWIN_PALM);
		t("wind_thunder_wings", Pose.DASH_LEAN);
		t("proud_golden_body", Pose.SEAL, Pose.CHEST_BEAT);
		t("fa_tian_xiang_di", Pose.SUMMON_RAISE, Pose.STOMP);
		t("burning_heart", Pose.GATE_FLEX);
		t("life_death_hex", Pose.SEAL);
		t("ruyi_staff", Pose.CHARGE_OVERHEAD, Pose.OVERHEAD_SLAM);
		t("three_life_saving_hairs", Pose.SEAL);
		t("dragon_soul_domain", Pose.ARMS_SPREAD);
		t("myriad_cauldron", Pose.SKY_RAISE, Pose.OVERHEAD_SLAM);
		t("xing_secret", Pose.SEAL);
		t("karmic_hex", Pose.FINGER_POINT);
		t("six_dao_reincarnation", Pose.SEAL);
		t("heavenly_fate_finger", Pose.FINGER_POINT);
		// Phase 10b Titah Langit (DESIGN 29.5): fallbacks when TitahArts authors no pose of its own. The second beat plays on
		// the decree's next event (Tiang: the javelin throw of the iron pillar into heaven).
		t("titah_pedang", Pose.SWORD_SKY);
		t("titah_tiang", Pose.CHARGE_OVERHEAD, Pose.JAVELIN_THROW);
		t("titah_bintang", Pose.PRAY, Pose.FINGER_POINT);
		t("titah_gunung", Pose.HOLD_ITEM_UP, Pose.SEAL);
		// legacy techniques (TechniqueRegistry.legacy)
		t("qi_palm", Pose.PALM_THRUST);
		t("flame_palm", Pose.PALM_THRUST);
		t("thunder_palm", Pose.SKY_RAISE, Pose.PALM_THRUST);
		t("mountain_stomp", Pose.STOMP);
		t("dao_domain", Pose.ARMS_SPREAD);
		t("spring_of_life", Pose.MEDITATE_FLOAT);
		t("life_drain", Pose.FINGER_POINT);
		t("gale", Pose.FAN_SWING);
		t("space_prison", Pose.SEAL);
		t("buddha_palm", Pose.SKY_RAISE, Pose.OVERHEAD_SLAM);
		t("thousand_swords", Pose.SWORD_SKY);
		t("sword_qi", Pose.SWORD_SLASH);
		t("phoenix_flame", Pose.TWIN_PALM);
		t("frost_lotus", Pose.SEAL);
		t("glacier_prison", Pose.TWIN_PALM);
		t("heavenly_thunder", Pose.SKY_RAISE);
		t("iron_body", Pose.GATE_FLEX);
		t("dharma_form", Pose.SUMMON_RAISE);
		t("divine_sense", Pose.SEAL);
		t("shadow_step", Pose.DASH_LEAN);
		t("space_blink", Pose.DASH_LEAN);
		t("flying_sword", Pose.SEAL);
	}

	private CastPoses() {
	}
}
