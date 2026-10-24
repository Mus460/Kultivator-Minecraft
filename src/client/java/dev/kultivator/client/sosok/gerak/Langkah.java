package dev.kultivator.client.sosok.gerak;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.anim.PoseRenderData;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.client.sosok.SosokDetail;
import dev.kultivator.data.VisualState;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The shared locomotion polish of players and Gerak bodies: knee lift on the forward swing, a bent-elbow arm carry that
 * swings across the body, a bigger arm pump and forward lean when running (blended by real ground speed, not by
 * vanilla's saturated {@code walkAnimationSpeed}), shoulders counter-rotating against the hips, a lean into turns, a
 * forward/back lean on starting and stopping, a shorter stride backward and a side step when strafing, and a light
 * squash after a fall. Players also get the parts the walk profiles give NPCs (hip sway, base lean, body bob, idle
 * breath). Vanilla's walk is changed, not replaced; sleeves, jacket and hat are child parts and follow.
 * <p>
 * Players yield to everything that owns their body: Player Animation Library (and Better Combat, which plays through it),
 * the meditation seat, cast and duel poses, the flying sword, swimming, elytra, crouching, riding, sleeping, and a custom
 * EMF player model. First person is untouched (vanilla's hand renderer resets the arm and has its own view bob).
 */
public final class Langkah {

	/** A player's state in its render state ({@code kultivator:langkah}); Gerak bodies carry it in {@link GerakData#langkah}. */
	public static final RenderStateDataKey<Keadaan> KUNCI = RenderStateDataKey.create(() -> "kultivator:langkah");

	private static final Map<Avatar, Keadaan> PEMAIN = new WeakHashMap<>();
	private static final float D = Mth.DEG_TO_RAD;
	// a player walks 0.216 blocks/tick and sprints 0.281: the run blend ramps across that gap
	private static final float LARI_MULAI = 0.225F;
	private static final float LARI_PENUH = 0.27F;
	// NPCs: vanilla's old "run" (walkAnimationSpeed > 0.9, i.e. 0.225 blocks/tick) sits inside their ramp, full at its saturation
	private static final float NPC_MULAI = 0.2F;
	private static final float NPC_PENUH = 0.25F;
	private static final float HIP_Y = 12.0F;

	/** Per body, smoothed over frames (no allocation per frame). */
	public static final class Keadaan {
		/** 0..1 run blend, from the ground speed. */
		float lari;
		/** Direction of travel relative to the body: forward -1..1, sideways -1..1 (+ = to the body's right). */
		float maju = 1.0F, samping;
		/** Body yaw rate, degrees per tick (+ = turning right). */
		float belok;
		/** Ground speed change, blocks per tick squared. */
		float aksel;
		/** 0..1 landing squash, decaying. */
		float mendarat;
		/** The player rides the flying sword (yield). */
		boolean terbang;
		private float kecepatan, vyMin, yawLalu, ageLalu = Float.NaN;
		private double xLalu, yLalu, zLalu;
		private boolean diUdara;

		/** Forgets everything (a reused entity id). */
		void reset() {
			lari = 0.0F;
			maju = 1.0F;
			samping = 0.0F;
			belok = 0.0F;
			aksel = 0.0F;
			mendarat = 0.0F;
			kecepatan = 0.0F;
			ageLalu = Float.NaN;
		}
	}

	// ================================================================== update (extraction)

	/** Reads this frame's movement into {@code s}; the positions are the render state's interpolated ones. */
	static void perbarui(Keadaan s, HumanoidRenderState st, float age, boolean diTanah, boolean pemain) {
		if (Float.isNaN(s.ageLalu) || age < s.ageLalu - 1.0F || age - s.ageLalu > 20.0F) {
			// first frame, a reused id or a long gap: start from rest
			s.ageLalu = age;
			s.xLalu = st.x;
			s.yLalu = st.y;
			s.zLalu = st.z;
			s.yawLalu = st.bodyRot;
			s.diUdara = !diTanah;
			s.vyMin = 0.0F;
			return;
		}
		float dt = age - s.ageLalu;
		if (dt < 0.001F) {
			// a second extraction in the same frame (the inventory preview turns the body): keep this frame's values
			return;
		}
		float inv = 1.0F / dt;
		float vx = (float) (st.x - s.xLalu) * inv;
		float vy = (float) (st.y - s.yLalu) * inv;
		float vz = (float) (st.z - s.zLalu) * inv;
		if (vx * vx + vz * vz > 4.0F || Math.abs(vy) > 4.0F) {
			// a teleport is not a sprint
			vx = 0.0F;
			vy = 0.0F;
			vz = 0.0F;
		}
		float yaw = st.bodyRot * D;
		float vF = vx * -Mth.sin(yaw) + vz * Mth.cos(yaw);
		float vS = vx * -Mth.cos(yaw) + vz * -Mth.sin(yaw);
		float v = Mth.sqrt(vx * vx + vz * vz);
		float a = 1.0F - (float) Math.pow(0.7D, dt);
		float lambat = 1.0F - (float) Math.pow(0.82D, dt);
		float mulai = pemain ? LARI_MULAI : NPC_MULAI;
		float penuh = pemain ? LARI_PENUH : NPC_PENUH;
		s.lari = Mth.lerp(lambat, s.lari, Mth.clamp((v - mulai) / (penuh - mulai), 0.0F, 1.0F));
		boolean bergerak = v > 0.03F;
		s.maju = Mth.lerp(a, s.maju, bergerak ? vF / v : 1.0F);
		s.samping = Mth.lerp(a, s.samping, bergerak ? vS / v : 0.0F);
		s.belok = Mth.lerp(a, s.belok, Mth.clamp(Mth.wrapDegrees(st.bodyRot - s.yawLalu) * inv, -25.0F, 25.0F));
		s.aksel = Mth.lerp(lambat, s.aksel, Mth.clamp((v - s.kecepatan) * inv, -0.1F, 0.1F));
		s.kecepatan = v;
		// landing: back on the ground after falling faster than ~0.45 blocks/tick (about two blocks; a sprint jump stays below)
		s.mendarat *= (float) Math.pow(0.72D, dt);
		if (!diTanah) {
			s.vyMin = Math.min(s.vyMin, vy);
		} else if (s.diUdara) {
			if (s.vyMin < -0.45F) {
				s.mendarat = Math.max(s.mendarat, Mth.clamp((-s.vyMin - 0.45F) * 1.5F + 0.3F, 0.0F, 1.0F));
			}
			s.vyMin = 0.0F;
		}
		s.diUdara = !diTanah;
		s.ageLalu = age;
		s.xLalu = st.x;
		s.yLalu = st.y;
		s.zLalu = st.z;
		s.yawLalu = st.bodyRot;
	}

	/** From {@code AvatarRendererMixin.extractRenderState}: a player's (or mannequin's) movement this frame. */
	public static void ekstrakPemain(Avatar entity, AvatarRenderState state, float partialTicks) {
		if (!SosokDetail.jalanPemain()) {
			return;
		}
		Keadaan s = PEMAIN.computeIfAbsent(entity, e -> new Keadaan());
		perbarui(s, state, entity.tickCount + partialTicks, entity.onGround(), true);
		s.terbang = ClientCultivation.visual(entity).has(VisualState.FLYING_SWORD);
		((FabricRenderState) state).setData(KUNCI, s);
	}

	// ================================================================== apply (model setup)

	/** From the PlayerModel mixin, last: a player's walk (nothing for Gerak bodies or anything that owns the body). */
	public static void pemain(PlayerModel model, AvatarRenderState state) {
		Keadaan s = ((FabricRenderState) state).getData(KUNCI);
		if (s == null || s.terbang || GerakData.dari(state) != null || !SosokDetail.jalanPemain()) {
			return;
		}
		if (state.isPassenger || state.isFallFlying || state.swimAmount > 0.0F || state.isVisuallySwimming || state.isCrouching
				|| state.isAutoSpinAttack || state.isSpectator || state.deathTime > 0.0F || !state.hasPose(Pose.STANDING)) {
			return;
		}
		if (MeditationPose.strength(state) > 0.01F || PoseRenderData.pose(state) != null || emf(model) || palAktif(state)) {
			return;
		}
		boolean serang = state.swingAnimation > 0.0F;
		boolean kanan = !serang && bebas(state.rightArmPose);
		boolean kiri = !serang && bebas(state.leftArmPose);
		terapkan(model, state, s, SosokDetail.jalanKuat(), true, kanan, kiri, kanan && kiri);
	}

	/**
	 * Lays the polish on {@code m} after vanilla's walk (and a Gerak body's profile). {@code pemain}: the whole set; for a
	 * Gerak body only what the walk profiles do not already do. {@code kanan}/{@code kiri}: that arm swings freely (no
	 * item pose, no attack, not posed by a clip); {@code putar}: the shoulders may counter-rotate.
	 */
	static void terapkan(PlayerModel m, HumanoidRenderState st, Keadaan s, float kuat, boolean pemain, boolean kanan, boolean kiri,
			boolean putar) {
		if (kuat <= 0.0F) {
			return;
		}
		float a = Mth.clamp(st.walkAnimationSpeed / Math.max(0.0001F, st.speedValue), 0.0F, 1.0F);
		float phi = st.walkAnimationPos * 0.6662F;
		float sn = Mth.sin(phi);
		float cs = Mth.cos(phi);
		float r = s.lari * a;
		float mundur = Math.max(0.0F, -s.maju) * a;
		float sisi = s.samping * a;

		// legs: the knee lifts on the forward swing (the box leg cannot bend, so it swings higher), shorter backward/sideways
		if (a > 0.01F) {
			float angkat = 1.0F + kuat * (0.18F + 0.5F * r) * (1.0F - mundur);
			float pendek = 1.0F - 0.3F * mundur - 0.45F * Math.abs(sisi);
			kaki(m.rightLeg, angkat, pendek);
			kaki(m.leftLeg, angkat, pendek);
			if (Math.abs(sisi) > 0.05F) {
				// a side step: the legs open and close in the frontal plane
				float buka = 0.22F * kuat * Math.abs(sisi);
				m.rightLeg.zRot += buka * Math.max(0.0F, sn);
				m.leftLeg.zRot -= buka * Math.max(0.0F, -sn);
			}
		}

		// arms: a bent-elbow carry (hangs a little forward, swings across the body), a bigger pump with tucked elbows when running
		if (a > 0.01F && (kanan || kiri)) {
			float pompa = kuat * ((pemain ? 0.08F : 0.0F) + 0.3F * r);
			float siku = -kuat * ((pemain ? 0.05F : 0.0F) * a + 0.3F * r);
			float silang = kuat * (0.08F + 0.3F * r);
			float tekuk = kuat * 0.1F * r;
			if (kanan) {
				float ayun = -cs * a;
				m.rightArm.xRot += siku + pompa * ayun * (st.rightArmPose == HumanoidModel.ArmPose.ITEM ? 0.5F : 1.0F);
				m.rightArm.yRot -= silang * Math.max(0.0F, cs) * a;
				m.rightArm.zRot -= tekuk;
			}
			if (kiri) {
				float ayun = cs * a;
				m.leftArm.xRot += siku + pompa * ayun * (st.leftArmPose == HumanoidModel.ArmPose.ITEM ? 0.5F : 1.0F);
				m.leftArm.yRot += silang * Math.max(0.0F, -cs) * a;
				m.leftArm.zRot += tekuk;
			}
		}

		// lean about the hips: base and run lean (players), start/stop, the landing squash
		float lean = kuat * (Mth.clamp(s.aksel * 70.0F, -5.0F, 6.0F) + 8.0F * s.mendarat) * D;
		if (pemain) {
			lean += kuat * (a * (2.5F + 9.5F * r) * (1.0F - mundur) - 3.0F * mundur) * D;
		}
		if (Math.abs(lean) > 0.0005F) {
			condong(m, lean, kanan, kiri);
		}

		// shoulders against the hips: right leg forward, right shoulder back (the head keeps its own yaw)
		if (putar && a > 0.01F) {
			GerakApplier.putarBadan(m, -kuat * a * (0.07F + 0.08F * r) * cs);
		}

		// roll about the hips: into the turn, and (players) the hip sway of each step; the head stays near upright
		float roll = -kuat * Mth.clamp(s.belok * 0.45F, -9.0F, 9.0F) * a * D;
		if (pemain) {
			roll += kuat * a * 1.8F * (1.0F - 0.5F * r) * D * sn;
		}
		if (Math.abs(roll) > 0.0005F) {
			GerakApplier.goyangPinggul(m, roll);
		}

		// the upper body dips at each footfall (players), and sinks on landing; the legs open a little to catch it
		float turun = 1.6F * kuat * s.mendarat;
		if (pemain) {
			turun += kuat * a * (0.3F + 0.5F * r) * Math.abs(cs);
		}
		if (turun > 0.01F) {
			m.head.y += turun;
			m.body.y += turun;
			m.rightArm.y += turun;
			m.leftArm.y += turun;
			m.rightLeg.zRot += 0.08F * kuat * s.mendarat;
			m.leftLeg.zRot -= 0.08F * kuat * s.mendarat;
		}

		// idle breath (players; Gerak bodies breathe in GerakPengendali): the shoulders rise and fall
		if (pemain && a < 0.1F) {
			float napas = 0.3F * kuat * (1.0F - a * 10.0F) * Mth.sin(st.ageInTicks * Mth.TWO_PI / 90.0F);
			m.rightArm.y -= napas;
			m.leftArm.y -= napas;
		}
	}

	/**
	 * Bends the upper body forward by {@code d} about the hip line, keeping every offset already there (slim arms, poses,
	 * the attack swing); free arms turn with the torso, posed ones keep their aim and only ride along.
	 */
	private static void condong(PlayerModel m, float d, boolean kanan, boolean kiri) {
		float c = Mth.cos(d);
		float s = Mth.sin(d);
		putarPinggul(m.body, c, s);
		putarPinggul(m.head, c, s);
		putarPinggul(m.rightArm, c, s);
		putarPinggul(m.leftArm, c, s);
		m.body.xRot += d;
		if (kanan) {
			m.rightArm.xRot += d;
		}
		if (kiri) {
			m.leftArm.xRot += d;
		}
	}

	private static void putarPinggul(ModelPart p, float c, float s) {
		float y = p.y - HIP_Y;
		float z = p.z;
		p.y = HIP_Y + y * c - z * s;
		p.z = y * s + z * c;
	}

	private static void kaki(ModelPart leg, float angkat, float pendek) {
		leg.xRot *= leg.xRot < 0.0F ? angkat * pendek : pendek;
	}

	private static boolean bebas(HumanoidModel.ArmPose pose) {
		return pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM;
	}

	// ================================================================== compatibility

	/** An EMF custom model replaced the root (EMF only does so when a pack ships one): its animations own the parts. */
	static boolean emf(PlayerModel model) {
		return model.root().getClass() != ModelPart.class;
	}

	private static boolean palDicoba;
	private static @Nullable Method palManager;
	private static @Nullable Method palAktif;

	/**
	 * True while Player Animation Library plays on this avatar (emotes, Better Combat's attacks): reflection on its
	 * render-state accessor, no compile dependency; any failure turns the check off for the session.
	 */
	private static boolean palAktif(AvatarRenderState state) {
		if (!palDicoba) {
			palDicoba = true;
			try {
				if (FabricLoader.getInstance().isModLoaded("player_animation_library")) {
					palManager = Class.forName("com.zigythebird.playeranim.accessors.IAvatarAnimationState").getMethod("playerAnimLib$getAnimManager");
					palAktif = Class.forName("com.zigythebird.playeranimcore.animation.layered.AnimationStack").getMethod("isActive");
				}
			} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
				matikanPal(error);
			}
		}
		if (palManager == null || palAktif == null || !palManager.getDeclaringClass().isInstance(state)) {
			return false;
		}
		try {
			Object manager = palManager.invoke(state);
			return manager != null && (Boolean) palAktif.invoke(manager);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
			matikanPal(error);
			return false;
		}
	}

	private static void matikanPal(Throwable error) {
		palManager = null;
		palAktif = null;
		Kultivator.LOGGER.info("Kultivator walk: Player Animation Library check off ({})", error.toString());
	}

	private Langkah() {
	}
}
