package dev.kultivator.client.duel.anim;

import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.client.duel.DuelBus;
import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.duel.DuelClock;
import dev.kultivator.client.duel.compat.BetterCombatBridge;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.blade.DuelPose;
import dev.kultivator.duel.blade.DuelPoseTable;
import dev.kultivator.duel.clash.SwingDir;
import dev.kultivator.duel.clash.SwingKind;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.net.DuelPayloads;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

/**
 * The duel pose of every fighter in range (DESIGN 35.5, contract C35.A3): the duel's counterpart of {@code CastPoses}.
 * It listens to {@link DuelBus} (swings, clashes, blade locks, fighter states, sessions, results, previews) and answers
 * {@link #snapshot} for {@code PoseRenderData.extract}, which hands the snapshot to {@code PoseApplier} like any cast pose
 * (kind {@code Pose.DUEL}).
 * <p>
 * <b>One pose at a time, blended.</b> Each fighter has one running {@link DuelPose}: a cut, a reaction, a ceremony or a
 * held stance. A new pose always starts from the channels the body shows at that moment ({@code from}), so a parry
 * snapping out of a windup, a chain of light cuts or a guard changing height flow without a jump. When a cut or a
 * reaction ends, the fighter settles into the stance its state asks for (guard high, middle or low as the opponent
 * threatens, the blade lock, the stagger, the opening stance in the countdown, a light middle guard while fighting);
 * without one the pose fades back to vanilla and the entry is dropped.
 * <p>
 * <b>Time</b> is the presentation clock of 7b ({@link EpicClock#time}): hit-stops freeze the poses together with the
 * sparks, trails and the film camera, slow motion slows them. A server swing starts as many ticks in the past as it is
 * old ({@code DuelSwing.startTick} against {@link DuelClock#serverTickNow}) so its strike lands on the server's impact; the
 * local player's own predicted swing starts at once and is warped over 3 ticks when the server's echo disagrees by more
 * than 2 ticks (35.8.2), softened back on {@code REJECTED} or a feint. A cut holds near its strike key until its
 * {@code DuelClash} arrives (at most 6 ticks), so the contact is seen in the right pose.
 * <p>
 * <b>Better Combat</b> (35.5.6): a swing flagged {@code BC}, or the local player while Better Combat's swing runs, gives
 * the arms to Better Combat's animation; body, head and legs stay ours. The blades are still recorded by
 * {@link BladeProbeLayer}, so the sparks fall on the blade Better Combat draws.
 * <p>
 * Client thread only. Idle cost: one empty-map check per rendered PlayerModel entity.
 */
public final class DuelPoses {

	/** Ticks to blend in from the vanilla pose and back out to it. */
	private static final float FADE_IN = 3.0F;
	private static final float FADE_OUT = 5.0F;
	/** The longest a cut waits at its strike for the server's result (35.8.2). */
	private static final float RESULT_HOLD = 6.0F;
	/** How fast the follow-through creeps on while it waits (a slow-down into the contact, never a dead stop). */
	private static final float HOLD_CREEP = 0.15F;
	/** The local echo window and the warp of a predicted swing (35.8.2). */
	private static final float ECHO_WINDOW = 15.0F;
	private static final float WARP_TOLERANCE = 2.0F;
	private static final float WARP_TICKS = 3.0F;
	/** A reaction started this recently is not restarted by the fighter state saying the same. */
	private static final float REACT_GUARD = 4.0F;
	/** The idle middle guard during a fight is shown lighter (the arms stay a little looser). */
	private static final float IDLE_GUARD_WEIGHT = 0.8F;
	/** Client ticks a track with nothing to show lives before it is pruned. */
	private static final int STALE_TICKS = 100;
	/** The pose preview plays each pose this long (uji duel pose semua). */
	private static final int PREVIEW_TICKS = 30;

	/** What the running pose is for. */
	enum Role {
		/** A cut of a swing. */
		CUT,
		/** A one-shot reaction (parry, recoil, hit, dodge...). */
		REACT,
		/** A held stance (guards, the lock, the stagger, the charge). */
		STANCE,
		/** A ceremony (salute, draw, sheathe, kneel) until {@code until}. */
		CEREMONY
	}

	/** One fighter. */
	static final class Track {
		final int id;
		@Nullable DuelPose pose;
		Role role = Role.STANCE;
		double start;
		int windup;
		@Nullable float[] from;
		/** The swing this cut belongs to ({@code DuelSwing.swingId}; negative while only predicted). */
		int swingId = Integer.MIN_VALUE;
		boolean predicted;
		boolean bcArms;
		/** Presentation time of the result of this cut (NaN: none yet). */
		double resultAt = Double.NaN;
		/** Warp of a predicted swing towards the server's start (35.8.2). */
		double warpFrom = Double.NaN;
		double warpTo;
		double warpAt;
		/** The charge of a heavy cut: when it began and its full charge in ticks. */
		double chargeStart = Double.NaN;
		int chargeFull = 16;
		/** A ceremony or preview ends here (presentation time, NaN: holds). */
		double until = Double.NaN;
		/** The stance after this pose (null: the state's stance). */
		@Nullable DuelPose then;
		/** The stance the fighter state, the session or a ceremony asks for, and how strongly. */
		@Nullable DuelPose stance;
		float stanceWeight = 1.0F;
		/** A stance set by the intro schedule (opening stance after one's own over-the-shoulder shot). */
		@Nullable DuelPose introStance;
		final DuelPoseTable.Ctx ctx = new DuelPoseTable.Ctx();
		/** The channels of the last evaluation (authored right-handed, wrist not folded). */
		final float[] out = new float[DuelPoseTable.CHANNELS];
		boolean valid;
		/** Overall weight: fading in from vanilla, fading out to it. */
		double fadeInAt = Double.NaN;
		double fadeOutAt = Double.NaN;
		float weightNow;
		/** The off hand on the hilt, eased 0..1. */
		float offAim;
		double offAimAt;
		/** The current pose's own weight multiplier (the light idle guard), easing towards {@code poseTarget}. */
		float poseWeight = 1.0F;
		float poseTarget = 1.0F;
		double poseWeightAt = Double.NaN;
		/** The last fighter state seen (to fire reactions on entering RECOIL / DODGE). */
		int lastState = -1;
		/** Presentation time a reaction began (REACT_GUARD). */
		double reactAt = -1.0E9D;
		/** Client tick of the last event or wanted stance. */
		long touched;
		/** Evaluated at this presentation time already (both hands and the extract share one evaluation per frame). */
		double evaluatedAt = Double.NaN;
		@Nullable CastPoses.Snapshot snapshot;
		boolean hideItem;

		Track(int id) {
			this.id = id;
		}
	}

	/** Per session: the ceremony schedule (35.4.8 intro, 35.4.9 finisher). */
	static final class SessionView {
		int phase = -1;
		long phaseStart;
		int phaseLength;
		boolean film;
		int decisiveWinner = -1;
		int decisiveLoser = -1;
		int introStep;
		boolean finisherDone;
		long lastSeen;
	}

	private static final Int2ObjectMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<SessionView> SESSIONS = new Int2ObjectOpenHashMap<>();
	private static final DuelPose[] ALL = DuelPose.all();
	/** {@code uji duel pose semua}: the next pose index and when it plays (client ticks), -1 when off. */
	private static int previewIndex = -1;
	private static long previewNext;
	private static boolean registered;

	// ================================================================== API (C35.A3)

	/**
	 * The duel pose of {@code entity} at {@code age} ({@code tickCount + partialTick}), or null when none plays. Evaluated
	 * once per frame per entity.
	 */
	public static @Nullable CastPoses.Snapshot snapshot(Entity entity, float age) {
		if (TRACKS.isEmpty()) {
			return null;
		}
		Track tr = TRACKS.get(entity.getId());
		if (tr == null) {
			return null;
		}
		float partial = age - entity.tickCount;
		double now = EpicClock.time(clampPartial(partial));
		evaluate(tr, entity, now);
		return tr.weightNow > 0.001F ? tr.snapshot : null;
	}

	/** The channels the entity shows now (authored right-handed, wrist not folded), or null; for the blade fallback. */
	public static @Nullable float[] channels(int entityId) {
		Track tr = TRACKS.get(entityId);
		if (tr == null || !tr.valid || tr.weightNow <= 0.5F) {
			return null;
		}
		return tr.out.clone();
	}

	/** The channels of the last evaluation whatever their weight (the first-person view scales them itself), or null. */
	public static @Nullable float[] authored(int entityId) {
		Track tr = TRACKS.get(entityId);
		return tr == null || !tr.valid ? null : tr.out.clone();
	}

	/** The pose {@code entityId} shows (null: none), for the first-person view and the debug lines. */
	public static @Nullable DuelPose poseOf(int entityId) {
		Track tr = TRACKS.get(entityId);
		return tr == null || tr.weightNow <= 0.001F ? null : tr.pose;
	}

	/** The weight of the duel pose of {@code entityId} at its last evaluation (0..1). */
	public static float weightOf(int entityId) {
		Track tr = TRACKS.get(entityId);
		return tr == null ? 0.0F : tr.weightNow;
	}

	/** True while the arms of {@code entityId} belong to Better Combat (35.5.6). */
	public static boolean armsHandedOver(int entityId) {
		Track tr = TRACKS.get(entityId);
		return tr != null && armWeight(tr) <= 0.01F;
	}

	/** True while the sheathing pose has put the blade away (the held item is not drawn). */
	public static boolean hidesItem(int entityId) {
		Track tr = TRACKS.get(entityId);
		return tr != null && tr.hideItem && tr.weightNow > 0.3F;
	}

	/** True when any duel pose plays. */
	public static boolean any() {
		return !TRACKS.isEmpty();
	}

	/**
	 * Plays {@code pose} on an entity now (previews, {@code /kultivasi duel hormat}, other modules): a cut with its
	 * canonical timing, anything else held for {@code holdTicks} (0: the pose's own length) before the fighter settles.
	 */
	public static void play(int entityId, DuelPose pose, int holdTicks) {
		if (pose == null) {
			return;
		}
		Track tr = track(entityId);
		double now = now();
		Role role = pose.cut() ? Role.CUT : pose.kind() == DuelPose.Kind.REACT ? Role.REACT : Role.CEREMONY;
		begin(tr, pose, role, now, -1);
		int length = DuelPoseTable.length(pose, -1);
		tr.until = role == Role.CEREMONY ? now + Math.max(length, holdTicks > 0 ? holdTicks : length + 10) : Double.NaN;
		if (pose == DuelPose.ANGKAT_BERAT) {
			tr.chargeStart = now;
			tr.chargeFull = 16;
		}
	}

	// ================================================================== evaluation

	private static void evaluate(Track tr, Entity entity, double now) {
		if (now == tr.evaluatedAt) {
			return;
		}
		tr.evaluatedAt = now;
		if (tr.start > now + 2.0D) {
			// the clock went backwards (a fresh world): start over from where the body is
			tr.start = now;
		}
		LivingEntity living = entity instanceof LivingEntity l ? l : null;
		WeaponClass weapon = living == null ? WeaponClass.TANGAN_KOSONG : BladeTracker.weaponOf(living);
		advance(tr, now);
		float[] out = tr.out;
		tr.hideItem = false;
		if (tr.pose != null) {
			float t = (float) (now - effectiveStart(tr, now));
			t = Math.max(0.0F, t);
			DuelPoseTable.Ctx ctx = tr.ctx;
			ctx.age = (float) (now % 100000.0D);
			ctx.charge = Double.isNaN(tr.chargeStart) ? 0.0F : (float) Math.min(1.0D, (now - tr.chargeStart) / Math.max(1, tr.chargeFull));
			if (tr.role == Role.CUT) {
				t = holdForResult(tr, t, now);
			}
			DuelPoseLibrary.sample(tr.pose, t, tr.windup, tr.from, ctx, out);
			tr.valid = true;
			int hideAt = DuelPoseTable.def(tr.pose).hideItemAt();
			tr.hideItem = hideAt >= 0 && t >= hideAt;
		}
		// the overall weight
		float weight = 1.0F;
		if (!Double.isNaN(tr.fadeInAt)) {
			weight = DuelPoseTable.easeInOutSine((float) ((now - tr.fadeInAt) / FADE_IN));
			if (weight >= 1.0F) {
				tr.fadeInAt = Double.NaN;
			}
		}
		if (!Double.isNaN(tr.fadeOutAt)) {
			weight *= 1.0F - DuelPoseTable.easeInOutSine((float) ((now - tr.fadeOutAt) / FADE_OUT));
		}
		// the pose's own weight eases (a cut ending in the lighter idle guard does not jump)
		double since = Double.isNaN(tr.poseWeightAt) ? 0.0D : Math.max(0.0D, Math.min(2.0D, now - tr.poseWeightAt));
		tr.poseWeightAt = now;
		tr.poseWeight = approach(tr.poseWeight, tr.poseTarget, (float) since * 0.25F);
		weight *= tr.poseWeight;
		tr.weightNow = Math.max(0.0F, weight);
		if (!tr.valid || tr.weightNow <= 0.001F) {
			tr.snapshot = null;
			return;
		}
		// the off hand on the hilt eases in and out over 3 ticks
		boolean wantsOff = DuelPoseLibrary.wantsOffHand(tr.pose, weapon) && tr.role != Role.CEREMONY;
		if (tr.offAimAt == 0.0D) {
			tr.offAimAt = now;
		}
		float dt = (float) Math.max(0.0D, Math.min(2.0D, now - tr.offAimAt));
		tr.offAimAt = now;
		tr.offAim = DuelPoseLibrary.clamp01(tr.offAim + (wantsOff ? dt : -dt) / 3.0F);
		boolean left = living != null && living.getMainArm() == HumanoidArm.LEFT;
		boolean slim = BladeTracker.slim(entity);
		boolean sword3d = entity instanceof dev.kultivator.disciple.DiscipleEntity && sword3dHint(entity);
		float[] model = DuelPoseLibrary.modelChannels(out, left, slim, weapon, sword3d, tr.offAim);
		tr.snapshot = DuelPoseLibrary.snapshot(model, tr.weightNow, armWeight(tr), tr.offAim > 0.5F);
	}

	/** The disciples' natal / qi sword is drawn as a 3D model in the fist; the probe tells whether it was. */
	private static boolean sword3dHint(Entity entity) {
		return BladeProbeLayer.drewSword3d(entity.getId());
	}

	/** Moves a track on: finished cuts, reactions and ceremonies give way to the stance or fade out. */
	private static void advance(Track tr, double now) {
		if (tr.pose == null) {
			if (tr.stance != null) {
				begin(tr, tr.stance, Role.STANCE, now, -1);
			}
			return;
		}
		double t = now - effectiveStart(tr, now);
		switch (tr.role) {
			case CUT -> {
				double end = DuelPoseTable.length(tr.pose, tr.windup) + extraHold(tr, now);
				if (t >= end) {
					settle(tr, now);
				}
			}
			case REACT -> {
				DuelPoseTable.Def def = DuelPoseTable.def(tr.pose);
				if (t >= def.windTicks() + def.strikeTicks() + def.followTicks()) {
					settle(tr, now);
				}
			}
			case CEREMONY -> {
				if (!Double.isNaN(tr.until) && now >= tr.until) {
					tr.until = Double.NaN;
					settle(tr, now);
				}
			}
			case STANCE -> {
				DuelPose want = tr.stance;
				if (tr.pose == DuelPose.ANGKAT_BERAT && !Double.isNaN(tr.chargeStart)) {
					// a held charge stays until its release swing (or a long silence: the server forgot it)
					if (now - tr.chargeStart > 100.0D) {
						tr.chargeStart = Double.NaN;
						settle(tr, now);
					}
					return;
				}
				if (want != tr.pose) {
					if (want == null) {
						fadeOut(tr, now);
					} else {
						begin(tr, want, Role.STANCE, now, -1);
						tr.poseTarget = tr.stanceWeight;
					}
				} else {
					tr.poseTarget = tr.stanceWeight;
					if (!Double.isNaN(tr.fadeOutAt) && want != null) {
						tr.fadeOutAt = Double.NaN;
					}
				}
			}
		}
	}

	/** After a cut, reaction or ceremony: the next pose asked for, the stance, or a fade back to vanilla. */
	private static void settle(Track tr, double now) {
		DuelPose next = tr.then != null ? tr.then : tr.stance;
		tr.then = null;
		if (next != null) {
			begin(tr, next, Role.STANCE, now, -1);
			tr.poseTarget = tr.stance == next ? tr.stanceWeight : 1.0F;
		} else {
			tr.role = Role.STANCE;
			fadeOut(tr, now);
		}
	}

	private static void fadeOut(Track tr, double now) {
		if (Double.isNaN(tr.fadeOutAt)) {
			tr.fadeOutAt = now;
		}
		// hold the last frame while it fades: without a pose the evaluation keeps the channels as they are
		tr.pose = null;
		tr.role = Role.STANCE;
		tr.chargeStart = Double.NaN;
	}

	/** Starts {@code pose} from what the body shows now. */
	private static void begin(Track tr, DuelPose pose, Role role, double now, int windup) {
		if (tr.valid && tr.weightNow > 0.001F) {
			tr.from = tr.out.clone();
			if (!Double.isNaN(tr.fadeOutAt)) {
				// caught while fading out: fade back in from the weight it had, without a jump
				float w = Math.min(1.0F, tr.weightNow / Math.max(0.01F, tr.poseWeight));
				tr.poseWeight = Math.max(tr.poseWeight, 0.01F);
				tr.fadeInAt = now - FADE_IN * w;
			}
		} else {
			tr.from = null;
			tr.fadeInAt = now;
		}
		tr.fadeOutAt = Double.NaN;
		tr.pose = pose;
		tr.role = role;
		tr.start = now;
		tr.windup = windup;
		tr.resultAt = Double.NaN;
		tr.warpFrom = Double.NaN;
		tr.until = Double.NaN;
		tr.poseTarget = 1.0F;
		tr.bcArms = false;
		if (pose != DuelPose.ANGKAT_BERAT) {
			tr.chargeStart = Double.NaN;
		}
		if (role == Role.REACT) {
			tr.reactAt = now;
		}
		if (role != Role.CUT) {
			tr.swingId = Integer.MIN_VALUE;
			tr.predicted = false;
		}
		tr.touched = DuelClock.clientTicks();
		if (role != Role.STANCE && PalBridge.active()) {
			ClientLevel level = Minecraft.getInstance().level;
			if (level != null && level.getEntity(tr.id) instanceof net.minecraft.client.player.AbstractClientPlayer player) {
				PalBridge.play(player, pose);
			}
		}
	}

	/** The start of the running pose with the predicted-swing warp applied. */
	private static double effectiveStart(Track tr, double now) {
		if (Double.isNaN(tr.warpFrom)) {
			return tr.start;
		}
		double k = Math.min(1.0D, Math.max(0.0D, (now - tr.warpAt) / WARP_TICKS));
		if (k >= 1.0D) {
			tr.start = tr.warpTo;
			tr.warpFrom = Double.NaN;
			return tr.start;
		}
		return tr.warpFrom + (tr.warpTo - tr.warpFrom) * k;
	}

	/** The follow-through of a cut creeps while its result is on the way (≤ 6 ticks), then runs on. */
	private static float holdForResult(Track tr, float t, double now) {
		int impact = tr.windup > 0 ? tr.windup : DuelPoseTable.impactTick(tr.pose);
		if (t <= impact) {
			return t;
		}
		float hold = (float) extraHold(tr, now);
		float after = t - impact;
		if (after <= hold) {
			return impact + after * HOLD_CREEP;
		}
		return impact + hold * HOLD_CREEP + (after - hold);
	}

	/** How long the cut has waited (or will have waited) at its strike for the result. */
	private static double extraHold(Track tr, double now) {
		if (!waitsForResult(tr)) {
			return 0.0D;
		}
		int impact = tr.windup > 0 ? tr.windup : DuelPoseTable.impactTick(tr.pose);
		double impactAt = effectiveStart(tr, now) + impact;
		double until = Double.isNaN(tr.resultAt) ? now : tr.resultAt;
		return Math.max(0.0D, Math.min(RESULT_HOLD, until - impactAt));
	}

	/** Only cuts of a formal duel near their opponent wait for a result (a whiff in the open just swings through). */
	private static boolean waitsForResult(Track tr) {
		return tr.role == Role.CUT && tr.swingId != Integer.MIN_VALUE && DuelClient.partnerOf(tr.id) >= 0;
	}

	private static float armWeight(Track tr) {
		if (tr.bcArms) {
			return 0.0F;
		}
		if (BetterCombatBridge.installed()) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null && player.getId() == tr.id && BetterCombatBridge.swingInProgress()) {
				return 0.0F;
			}
			// Better Combat's idle poses of two-handed weapons own the arms of players at rest (35.5.6)
			if (tr.role == Role.STANCE && tr.pose != DuelPose.KUNCI_BILAH && tr.pose != DuelPose.TERHUYUNG && tr.pose != DuelPose.ANGKAT_BERAT
					&& isPlayerWithTwoHander(tr.id)) {
				return 0.0F;
			}
		}
		return 1.0F;
	}

	private static boolean isPlayerWithTwoHander(int id) {
		ClientLevel level = Minecraft.getInstance().level;
		Entity entity = level == null ? null : level.getEntity(id);
		if (!(entity instanceof Player player)) {
			return false;
		}
		WeaponClass weapon = BladeTracker.weaponOf(player);
		return weapon == WeaponClass.PEDANG_BERAT || weapon == WeaponClass.TOMBAK;
	}

	// ================================================================== events (DuelBus)

	static final DuelBus.Listener LISTENER = new DuelBus.Listener() {
		@Override
		public void swing(DuelPayloads.DuelSwing p, boolean predicted) {
			onSwing(p, predicted);
		}

		@Override
		public void clash(DuelPayloads.DuelClash p, boolean predicted) {
			onClash(p, predicted);
		}

		@Override
		public void bind(DuelPayloads.DuelBindSync p) {
			onBind(p);
		}

		@Override
		public void fighter(DuelPayloads.DuelFighterSync p) {
			onFighter(p);
		}

		@Override
		public void session(DuelPayloads.DuelSessionSync p) {
			onSession(p);
		}

		@Override
		public void result(DuelPayloads.DuelResult p) {
			onResult(p);
		}

		@Override
		public void preview(DuelPayloads.DuelPreview p) {
			onPreview(p);
		}
	};

	static void register() {
		if (!registered) {
			registered = true;
			DuelBus.register(LISTENER);
		}
	}

	private static void onSwing(DuelPayloads.DuelSwing p, boolean predicted) {
		Track tr = track(p.entityId());
		double now = now();
		boolean feint = (p.flags() & DuelIds.SW_FEINT_CANCEL) != 0;
		boolean rejected = (p.flags() & DuelIds.SW_REJECTED) != 0;
		boolean local = DuelClient.isLocal(p.entityId());
		if (feint || rejected) {
			// the swing is called off: soften straight into the stance (35.3.8: the opponent sees only the cancel)
			if (tr.role == Role.CUT || tr.pose == DuelPose.ANGKAT_BERAT) {
				tr.chargeStart = Double.NaN;
				settle(tr, now);
			}
			return;
		}
		boolean charging = p.windup() == DuelIds.WINDUP_CHARGING;
		SwingKind kind = SwingKind.byOrdinal(p.kind());
		SwingDir dir = SwingDir.byOrdinal(p.dir());
		DuelPose pose = charging ? DuelPose.ANGKAT_BERAT : DuelPose.forSwing(kind, dir);
		WeaponClass weapon = WeaponClass.byOrdinal(p.weaponClass());
		// how old the swing already is on the server (never the predicted ones: they start now)
		double elapsed = 0.0D;
		if (!predicted && DuelClock.known()) {
			elapsed = DuelClock.serverTickNow(partial()) - p.startTick();
			elapsed = Math.max(0.0D, Math.min(charging ? 40.0D : Math.max(0, p.windup() - 1), elapsed));
		}
		double serverStart = now - elapsed;
		if (!predicted && local && tr.predicted && now - tr.start < ECHO_WINDOW) {
			// the server's echo of the local player's own swing (35.8.2)
			boolean sameStep = charging ? tr.pose == DuelPose.ANGKAT_BERAT : tr.role == Role.CUT && tr.pose == pose;
			if (sameStep) {
				tr.swingId = p.swingId();
				tr.predicted = false;
				tr.bcArms = (p.flags() & DuelIds.SW_BC) != 0;
				if (!charging && p.windup() > 0) {
					tr.windup = p.windup();
				}
				if (Math.abs(serverStart - tr.start) > WARP_TOLERANCE) {
					tr.warpFrom = effectiveStart(tr, now);
					tr.warpTo = serverStart;
					tr.warpAt = now;
				}
				return;
			}
			if (charging && tr.role == Role.CUT) {
				// the charge's echo after the predicted release: already past it
				return;
			}
		}
		if (charging) {
			if (tr.pose == DuelPose.ANGKAT_BERAT && tr.swingId == p.swingId() && !Double.isNaN(tr.chargeStart)) {
				return;
			}
			begin(tr, DuelPose.ANGKAT_BERAT, Role.STANCE, serverStart, -1);
			tr.chargeStart = serverStart;
			tr.chargeFull = Math.max(1, weapon.heavyFull);
			tr.swingId = p.swingId();
			tr.predicted = predicted;
			tr.touched = DuelClock.clientTicks();
			return;
		}
		begin(tr, pose, Role.CUT, serverStart, Math.max(1, p.windup()));
		tr.swingId = p.swingId();
		tr.predicted = predicted;
		tr.bcArms = (p.flags() & DuelIds.SW_BC) != 0;
	}

	private static void onClash(DuelPayloads.DuelClash p, boolean predicted) {
		if (!predicted && (p.flags() & DuelIds.CF_PREDICTED_MATCH) != 0) {
			// the defender already shows this result (35.8.2); only the attacker's cut learns it has landed
			markResult(p.aId(), p.swingA());
			return;
		}
		double now = now();
		int a = p.aId();
		int b = p.bId();
		SwingDir dirA = SwingDir.byOrdinal(p.dirA());
		float side = sideOf(dirA);
		float alt = dirA.high() ? 1.0F : 0.0F;
		markResult(a, p.swingA());
		if (p.swingB() >= 0) {
			markResult(b, p.swingB());
		}
		if ((p.flags() & DuelIds.CF_DECISIVE) != 0) {
			DuelPayloads.DuelSessionSync s = DuelClient.session(a);
			if (s != null) {
				SessionView view = view(s.sessionId());
				view.decisiveWinner = a;
				view.decisiveLoser = b;
			}
		}
		switch (p.outcome()) {
			case DuelIds.OUT_HIT -> react(b, DuelPose.TERKENA, side, 0.0F, now);
			case DuelIds.OUT_BLOCK, DuelIds.OUT_DEFLECT, DuelIds.OUT_QI_DEFLECT -> react(b, DuelPose.TANGKIS, side, alt, now);
			case DuelIds.OUT_PERFECT, DuelIds.OUT_QI_REFLECT -> {
				react(b, DuelPose.TANGKIS_SEMPURNA, side, alt, now);
				react(a, DuelPose.HENTAK, -side, 0.0F, now);
			}
			case DuelIds.OUT_CLASH, DuelIds.OUT_CLASH_MENANG -> {
				react(a, DuelPose.HENTAK, sideOf(SwingDir.byOrdinal(p.dirB())), 0.0F, now);
				react(b, DuelPose.HENTAK, side, 0.0F, now);
			}
			case DuelIds.OUT_BIND -> {
				hold(a, DuelPose.KUNCI_BILAH, now);
				hold(b, DuelPose.KUNCI_BILAH, now);
			}
			case DuelIds.OUT_BIND_MENANG_A -> {
				react(b, DuelPose.TERPENTAL, 0.0F, 0.0F, now);
				react(a, DuelPose.TANGKIS_SEMPURNA, 0.0F, 0.0F, now);
			}
			case DuelIds.OUT_BIND_MENANG_B -> {
				react(a, DuelPose.TERPENTAL, 0.0F, 0.0F, now);
				react(b, DuelPose.TANGKIS_SEMPURNA, 0.0F, 0.0F, now);
			}
			case DuelIds.OUT_BIND_LEPAS -> {
				react(a, DuelPose.TERPENTAL, 0.0F, 0.0F, now);
				react(b, DuelPose.TERPENTAL, 0.0F, 0.0F, now);
			}
			case DuelIds.OUT_BIND_RELEASE_TRICK -> {
				// the one who let go steps aside, the one pushing stumbles through
				react(a, DuelPose.MENGHINDAR, 0.6F, 0.0F, now);
				react(b, DuelPose.TERPENTAL, 0.0F, 0.0F, now);
			}
			case DuelIds.OUT_GUARD_BREAK, DuelIds.OUT_POSTURE_BREAK -> {
				react(b, DuelPose.TERPENTAL, side, 0.0F, now);
				then(b, DuelPose.TERHUYUNG);
			}
			case DuelIds.OUT_DEATHBLOW -> react(b, DuelPose.TERKENA, side, 0.0F, now);
			case DuelIds.OUT_EVADE -> react(b, DuelPose.MENGHINDAR, -side, 0.0F, now);
			case DuelIds.OUT_MIKIRI -> {
				react(b, DuelPose.INJAK, 0.0F, 0.0F, now);
				react(a, DuelPose.HENTAK, 0.0F, 0.0F, now);
			}
			default -> {
			}
		}
	}

	/** The side (−1 left, +1 right, in the defender's own frame) a cut of {@code dir} from the attacker arrives from. */
	static float sideOf(SwingDir dir) {
		return switch (dir) {
			// the attacker's right is the defender's left when they face each other
			case KANAN, ATAS_KANAN, BAWAH_KANAN -> -1.0F;
			case KIRI, ATAS_KIRI, BAWAH_KIRI -> 1.0F;
			default -> 0.0F;
		};
	}

	private static void markResult(int entityId, int swingId) {
		Track tr = TRACKS.get(entityId);
		if (tr != null && tr.role == Role.CUT && Double.isNaN(tr.resultAt) && (tr.swingId == swingId || tr.predicted)) {
			tr.resultAt = now();
		}
	}

	private static void react(int entityId, DuelPose pose, float side, float alt, double now) {
		if (entityId < 0) {
			return;
		}
		Track tr = track(entityId);
		begin(tr, pose, pose.kind() == DuelPose.Kind.STANCE ? Role.STANCE : Role.REACT, now, -1);
		tr.ctx.side = side;
		tr.ctx.alt = alt;
	}

	private static void hold(int entityId, DuelPose pose, double now) {
		if (entityId < 0) {
			return;
		}
		Track tr = track(entityId);
		tr.stance = pose;
		tr.stanceWeight = 1.0F;
		if (tr.pose != pose) {
			begin(tr, pose, Role.STANCE, now, -1);
		}
	}

	private static void then(int entityId, DuelPose pose) {
		Track tr = TRACKS.get(entityId);
		if (tr != null) {
			tr.then = pose;
		}
	}

	private static void onBind(DuelPayloads.DuelBindSync p) {
		double now = now();
		float pressure = Math.max(-1.0F, Math.min(1.0F, p.pressure() / 100.0F));
		boolean end = (p.flags() & DuelIds.BF_END) != 0;
		for (int i = 0; i < 2; i++) {
			int id = i == 0 ? p.aId() : p.bId();
			Track tr = track(id);
			tr.ctx.pressure = i == 0 ? pressure : -pressure;
			if (end) {
				if (tr.stance == DuelPose.KUNCI_BILAH) {
					tr.stance = null;
				}
				continue;
			}
			tr.stance = DuelPose.KUNCI_BILAH;
			tr.stanceWeight = 1.0F;
			if (tr.pose != DuelPose.KUNCI_BILAH && tr.role != Role.REACT) {
				begin(tr, DuelPose.KUNCI_BILAH, Role.STANCE, now, -1);
			}
		}
	}

	private static void onFighter(DuelPayloads.DuelFighterSync p) {
		Track tr = TRACKS.get(p.entityId());
		DuelPayloads.DuelSessionSync session = DuelClient.session(p.entityId());
		if (tr == null) {
			// only fighters with something to show get a track (a mob's posture sync alone does not)
			if (session == null && p.state() != DuelIds.ST_GUARD && p.state() != DuelIds.ST_STAGGER && p.state() != DuelIds.ST_BIND) {
				return;
			}
			tr = track(p.entityId());
		}
		double now = now();
		int state = p.state();
		if (state != tr.lastState) {
			boolean recent = now - tr.reactAt < REACT_GUARD;
			if (state == DuelIds.ST_RECOIL && !recent && tr.role != Role.REACT) {
				react(p.entityId(), DuelPose.HENTAK, 0.0F, 0.0F, now);
			} else if (state == DuelIds.ST_DODGE && !recent) {
				react(p.entityId(), DuelPose.MENGHINDAR, 0.0F, 0.0F, now);
			}
			tr.lastState = state;
		}
		tr.touched = DuelClock.clientTicks();
	}

	private static void onSession(DuelPayloads.DuelSessionSync p) {
		SessionView view = view(p.sessionId());
		view.lastSeen = DuelClock.clientTicks();
		if (p.phase() != view.phase) {
			view.phase = p.phase();
			view.phaseLength = Math.max(1, p.phaseTicksLeft());
			view.phaseStart = DuelClock.clientTicks();
			view.film = (p.flags() & DuelIds.SF_INTRO_FILM) != 0;
			view.introStep = 0;
			if (p.phase() == DuelIds.PHASE_INTRO) {
				track(p.aId()).introStance = null;
				track(p.bId()).introStance = null;
			} else if (p.phase() >= DuelIds.PHASE_COUNTDOWN && p.phase() <= DuelIds.PHASE_FINISHER) {
				// both fighters get a track from the countdown on (also after a relog in the middle of a fight)
				track(p.aId());
				track(p.bId());
			}
		}
	}

	private static void onResult(DuelPayloads.DuelResult p) {
		SessionView view = SESSIONS.get(p.sessionId());
		if (view == null || view.finisherDone) {
			return;
		}
		view.finisherDone = true;
		double now = now();
		if (p.reason() == DuelIds.RES_SERI) {
			ceremony(p.winnerId(), DuelPose.HORMAT, now, 40);
			ceremony(p.loserId(), DuelPose.HORMAT, now, 40);
		} else if (p.reason() != DuelIds.RES_BATAL) {
			ceremony(p.winnerId(), DuelPose.SARUNG, now, 50);
			ceremony(p.loserId(), DuelPose.KALAH_BERLUTUT, now, 60);
		}
	}

	private static void onPreview(DuelPayloads.DuelPreview p) {
		switch (p.kind()) {
			case DuelIds.PV_POSE -> {
				// rgb = 0: the local player; otherwise the entity id + 1 (a salute broadcast); f = hold ticks
				int id = p.rgb() != 0 ? p.rgb() - 1 : localId();
				if (id >= 0) {
					DuelPose pose = DuelPose.byOrdinal(p.arg());
					play(id, pose, p.f() > 0 ? Math.round(p.f()) : 0);
					if (p.rgb() == 0) {
						announce(pose);
					}
				}
			}
			case DuelIds.PV_ALL_POSES -> {
				previewIndex = 0;
				previewNext = DuelClock.clientTicks();
				LocalPlayer player = Minecraft.getInstance().player;
				if (player != null) {
					player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("kultivator.duel.pose.all_start", ALL.length));
				}
			}
			case DuelIds.PV_BLADE_DEBUG -> BladeDebug.set(p.arg() != 0);
			default -> {
			}
		}
	}

	private static void announce(DuelPose pose) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("kultivator.duel.pose.playing", pose.ordinal(),
					net.minecraft.network.chat.Component.translatable(pose.langKey())));
		}
	}

	// ================================================================== the tick: stances and ceremonies

	/** Once per client tick (from {@link DuelAnim}). */
	static void tick(Minecraft minecraft) {
		if (minecraft.level == null) {
			return;
		}
		long ticks = DuelClock.clientTicks();
		double now = now();
		// the ceremonies of every session in range
		for (DuelPayloads.DuelSessionSync s : DuelClient.sessions()) {
			schedule(s, view(s.sessionId()), ticks, now);
		}
		// what each fighter's state asks for
		for (Track tr : TRACKS.values()) {
			wantStance(tr, ticks);
		}
		// uji duel pose semua
		if (previewIndex >= 0 && ticks >= previewNext) {
			int id = localId();
			if (previewIndex >= ALL.length || id < 0) {
				previewIndex = -1;
				LocalPlayer player = minecraft.player;
				if (player != null) {
					player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("kultivator.duel.pose.all_done"));
				}
			} else {
				DuelPose pose = ALL[previewIndex++];
				play(id, pose, PREVIEW_TICKS - 4);
				announce(pose);
				previewNext = ticks + PREVIEW_TICKS;
			}
		}
	}

	private static void wantStance(Track tr, long ticks) {
		DuelPayloads.DuelFighterSync fighter = DuelClient.fighter(tr.id);
		DuelPayloads.DuelSessionSync session = DuelClient.session(tr.id);
		DuelPose want = null;
		float weight = 1.0F;
		int state = fighter == null ? DuelIds.ST_IDLE : fighter.state();
		switch (state) {
			case DuelIds.ST_GUARD -> want = guardFor(tr.id);
			case DuelIds.ST_STAGGER -> want = DuelPose.TERHUYUNG;
			case DuelIds.ST_BIND -> want = DuelPose.KUNCI_BILAH;
			default -> {
			}
		}
		if (want == null && DuelClient.bind(tr.id) != null) {
			want = DuelPose.KUNCI_BILAH;
		}
		if (want == null && session != null) {
			switch (session.phase()) {
				case DuelIds.PHASE_INTRO -> want = tr.introStance;
				case DuelIds.PHASE_COUNTDOWN -> want = DuelPose.SIKAP_PEMBUKA;
				case DuelIds.PHASE_FIGHT -> {
					if (session.pair() != DuelIds.PAIR_LATIH || fighter != null) {
						want = DuelPose.KUDA_TENGAH;
						weight = IDLE_GUARD_WEIGHT;
					}
				}
				default -> {
				}
			}
		}
		if (want == null && previewIndex >= 0 && tr.id == localId()) {
			want = DuelPose.KUDA_TENGAH;
		}
		if (want != null) {
			tr.touched = ticks;
		}
		tr.stance = want;
		tr.stanceWeight = weight;
	}

	/** The guard against what the opponent shows (35.5.2): high against a raised heavy cut, low against a low sweep. */
	private static DuelPose guardFor(int entityId) {
		int partner = DuelClient.partnerOf(entityId);
		DuelPayloads.DuelSwing swing = partner >= 0 ? DuelClient.swing(partner) : null;
		if (swing != null && DuelClock.known() && DuelClock.serverTickNow(0.0F) - swing.startTick() < Math.max(swing.windup(), 30) + 6) {
			SwingDir dir = SwingDir.byOrdinal(swing.dir());
			if ((swing.flags() & DuelIds.SW_PERILOUS_SAPU) != 0 || dir.low()) {
				return DuelPose.KUDA_RENDAH;
			}
			if (swing.windup() == DuelIds.WINDUP_CHARGING || (swing.flags() & DuelIds.SW_PERILOUS_GUNUNG) != 0 || dir.high()) {
				return DuelPose.KUDA_TINGGI;
			}
		}
		return DuelPose.KUDA_TENGAH;
	}

	/** The intro (35.4.8) and finisher (35.4.9) poses of a session, by the ticks into its phase. */
	private static void schedule(DuelPayloads.DuelSessionSync s, SessionView view, long ticks, double now) {
		view.lastSeen = ticks;
		long into = ticks - view.phaseStart;
		if (view.phase == DuelIds.PHASE_INTRO) {
			if (view.film) {
				// 0-30 wide shot: the salute; 30-55 over A's shoulder: B takes the stance; 55-80: A; 92: both draw
				if (view.introStep == 0 && into >= 4) {
					ceremony(s.aId(), DuelPose.HORMAT, now, 24);
					ceremony(s.bId(), DuelPose.HORMAT, now, 24);
					view.introStep = 1;
				} else if (view.introStep == 1 && into >= 34) {
					setIntroStance(s.bId(), DuelPose.SIKAP_PEMBUKA);
					view.introStep = 2;
				} else if (view.introStep == 2 && into >= 58) {
					setIntroStance(s.aId(), DuelPose.SIKAP_PEMBUKA);
					view.introStep = 3;
				} else if (view.introStep == 3 && into >= 88) {
					draw(s.aId(), now);
					draw(s.bId(), now);
					view.introStep = 4;
				}
			} else {
				// the short intro: salute, stance, draw within 40 ticks
				if (view.introStep == 0 && into >= 0) {
					ceremony(s.aId(), DuelPose.HORMAT, now, 14);
					ceremony(s.bId(), DuelPose.HORMAT, now, 14);
					view.introStep = 1;
				} else if (view.introStep == 1 && into >= 16) {
					draw(s.aId(), now);
					draw(s.bId(), now);
					view.introStep = 4;
				}
			}
		} else if (view.phase == DuelIds.PHASE_FINISHER && !view.finisherDone && into >= 24) {
			if (view.decisiveWinner >= 0) {
				view.finisherDone = true;
				ceremony(view.decisiveWinner, DuelPose.SARUNG, now, 40);
				ceremony(view.decisiveLoser, DuelPose.KALAH_BERLUTUT, now, 70);
			} else if ((s.flags() & DuelIds.SF_SURRENDER) != 0) {
				// a surrender: the result names the sides; wait for it (onResult)
				return;
			}
		}
	}

	private static void setIntroStance(int entityId, DuelPose pose) {
		if (entityId >= 0) {
			track(entityId).introStance = pose;
		}
	}

	/** Drawing the sword, then the opening stance. */
	private static void draw(int entityId, double now) {
		if (entityId < 0) {
			return;
		}
		Track tr = track(entityId);
		tr.introStance = DuelPose.SIKAP_PEMBUKA;
		ceremony(entityId, DuelPose.HUNUS, now, DuelPoseTable.length(DuelPose.HUNUS, -1));
	}

	private static void ceremony(int entityId, DuelPose pose, double now, int ticks) {
		if (entityId < 0) {
			return;
		}
		Track tr = track(entityId);
		begin(tr, pose, Role.CEREMONY, now, -1);
		tr.until = now + Math.max(4, ticks);
	}

	// ================================================================== housekeeping

	private static Track track(int entityId) {
		Track tr = TRACKS.get(entityId);
		if (tr == null) {
			tr = new Track(entityId);
			tr.touched = DuelClock.clientTicks();
			TRACKS.put(entityId, tr);
		}
		return tr;
	}

	private static SessionView view(int sessionId) {
		SessionView view = SESSIONS.get(sessionId);
		if (view == null) {
			view = new SessionView();
			view.lastSeen = DuelClock.clientTicks();
			SESSIONS.put(sessionId, view);
		}
		return view;
	}

	/** Drops tracks of entities that left and tracks that faded out (every 20 ticks, {@code AnimClient}). */
	public static void prune(@Nullable ClientLevel level) {
		long ticks = DuelClock.clientTicks();
		Iterator<Int2ObjectMap.Entry<Track>> it = TRACKS.int2ObjectEntrySet().iterator();
		while (it.hasNext()) {
			Track tr = it.next().getValue();
			Entity entity = level == null ? null : level.getEntity(tr.id);
			boolean gone = entity == null || entity.isRemoved();
			boolean idle = tr.stance == null && tr.pose == null && tr.weightNow <= 0.001F && ticks - tr.touched > 10;
			boolean stale = ticks - tr.touched > STALE_TICKS && tr.stance == null && tr.role != Role.CEREMONY;
			if (gone && ticks - tr.touched > 40 || idle || stale) {
				it.remove();
			}
		}
		Iterator<Int2ObjectMap.Entry<SessionView>> sv = SESSIONS.int2ObjectEntrySet().iterator();
		while (sv.hasNext()) {
			if (ticks - sv.next().getValue().lastSeen > 400) {
				sv.remove();
			}
		}
	}

	/** Forgets everything (disconnect, world change: {@code AnimClient.clearAll}). */
	public static void clear() {
		TRACKS.clear();
		SESSIONS.clear();
		previewIndex = -1;
	}

	private static double now() {
		return EpicClock.time(partial());
	}

	private static float partial() {
		return clampPartial(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	private static float clampPartial(float partial) {
		return partial < 0.0F ? 0.0F : partial > 1.0F ? 1.0F : partial;
	}

	private static int localId() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? -1 : player.getId();
	}

	private static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	private DuelPoses() {
	}
}
