package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.client.anim.PoseLibrary;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.technique.spec.dsl.Pose;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

import static dev.kultivator.client.anim.PoseLibrary.HX;
import static dev.kultivator.client.anim.PoseLibrary.HY;
import static dev.kultivator.client.anim.PoseLibrary.LAX;
import static dev.kultivator.client.anim.PoseLibrary.LAY;
import static dev.kultivator.client.anim.PoseLibrary.LAZ;
import static dev.kultivator.client.anim.PoseLibrary.LEAN;
import static dev.kultivator.client.anim.PoseLibrary.LLZ;
import static dev.kultivator.client.anim.PoseLibrary.RAX;
import static dev.kultivator.client.anim.PoseLibrary.RAY;
import static dev.kultivator.client.anim.PoseLibrary.RAZ;
import static dev.kultivator.client.anim.PoseLibrary.RLZ;

/**
 * The body language of module K's people (detail pass), as a cast-pose snapshot in the render state
 * ({@code PoseRenderData.POSE}): the PlayerModel mixin puts it on every PlayerModel of the NPC (the skin, the disciple
 * robe, the gear), so sleeves, armour and sheaths follow. Only when no cast or duel pose plays, it does not swing, sit,
 * swim or ride. Weights ease in and out per NPC (about 0.3 s).
 * <ul>
 * <li>Idle stances by role: hands behind the back (auctioneers, lecturer, envoy), arms folded (the broker, a rival
 * leader), hands clasped in the sleeves (immortals, merchants, the tea master), the guard's spear at the ready, the
 * smith's hammer resting, the wanderer's left hand on the hilt, a slow breath for all.</li>
 * <li>{@code FLAG_BOW}: the cupped-fist salute (baoquan) with a bow from the hips.</li>
 * <li>{@code FLAG_TALK}: the speaking hand and a nodding head (wider for the auctioneer and the lecturer).</li>
 * <li>{@code FLAG_GAZE}: the head tips back to the sky; talking while gazing points up.</li>
 * <li>{@code FLAG_WORK}: the abacus, the hammer, the teapot, the morning exercise.</li>
 * </ul>
 * Model space (PoseLibrary): arm {@code xRot < 0} raises forward; right arm {@code yRot < 0} and left {@code yRot > 0}
 * swing inward; right {@code zRot > 0} and left {@code zRot < 0} outward; head channels add to the look.
 */
final class NpcPoses {

	private static final int C = PoseLibrary.CHANNELS;
	private static final Map<KultivatorNpc, Blend> BLENDS = new WeakHashMap<>();

	private static final class Blend {
		final float[] val = new float[C];
		final float[] w = new float[C];
		float age = Float.NaN;

		Blend() {
			Arrays.fill(val, Float.NaN);
		}
	}

	/** The NPC's body-language snapshot this frame, or null when nothing drives the model. */
	static @Nullable CastPoses.Snapshot snapshot(KultivatorNpc npc, DiscipleRenderState state, float age) {
		float[] tv = new float[C];
		float[] tw = new float[C];
		Arrays.fill(tv, Float.NaN);
		boolean blocked = MeditationPose.strength(state) > 0.3F || state.isPassenger || state.swimAmount > 0.1F
				|| state.currentSwing != null && state.swingAnimation > 0.0F;
		if (!blocked) {
			target(npc, state, age, tv, tw);
		}
		Blend blend = BLENDS.computeIfAbsent(npc, key -> new Blend());
		float dt = Float.isNaN(blend.age) ? 20.0F : Mth.clamp(age - blend.age, 0.0F, 20.0F);
		blend.age = age;
		float a = 1.0F - (float) Math.pow(0.72D, dt);
		float strongest = 0.0F;
		for (int c = 0; c < C; c++) {
			if (tw[c] > 0.0F) {
				blend.val[c] = Float.isNaN(blend.val[c]) || blend.w[c] < 0.02F ? tv[c] : Mth.lerp(a, blend.val[c], tv[c]);
			}
			blend.w[c] = Mth.lerp(a, blend.w[c], tw[c]);
			if (blend.w[c] < 0.01F) {
				blend.w[c] = 0.0F;
			}
			strongest = Math.max(strongest, blend.w[c]);
		}
		if (strongest <= 0.0F) {
			return null;
		}
		return new CastPoses.Snapshot(Pose.NONE, blend.val, blend.w, null, null, 1.0F, false);
	}

	private static void set(float[] tv, float[] tw, int channel, float value, float weight) {
		tv[channel] = value;
		tw[channel] = weight;
	}

	private static void target(KultivatorNpc npc, DiscipleRenderState state, float t, float[] tv, float[] tw) {
		int flags = npc.flags();
		NpcRole role = npc.role();
		boolean walking = state.walkAnimationSpeed > 0.15F;
		boolean talk = (flags & KultivatorNpc.FLAG_TALK) != 0;
		boolean gaze = (flags & KultivatorNpc.FLAG_GAZE) != 0;
		if ((flags & KultivatorNpc.FLAG_BOW) != 0) {
			// baoquan: the right fist in the left palm before the chest, a bow from the hips
			set(tv, tw, RAX, -1.3F, 1.0F);
			set(tv, tw, RAY, -0.62F, 1.0F);
			set(tv, tw, RAZ, 0.0F, 1.0F);
			set(tv, tw, LAX, -1.25F, 1.0F);
			set(tv, tw, LAY, 0.62F, 1.0F);
			set(tv, tw, LAZ, 0.0F, 1.0F);
			if (!walking) {
				set(tv, tw, LEAN, 0.32F, 1.0F);
			}
			set(tv, tw, HX, 0.3F, 1.0F);
			return;
		}
		if (gaze && talk) {
			// pointing up at what is in the sky
			set(tv, tw, RAX, -2.5F, 1.0F);
			set(tv, tw, RAY, -0.1F, 1.0F);
			set(tv, tw, RAZ, 0.1F, 1.0F);
			set(tv, tw, HX, -0.7F, 1.0F);
			return;
		}
		if (gaze) {
			set(tv, tw, HX, -0.8F, 1.0F);
			set(tv, tw, RAZ, 0.12F, 0.6F);
			set(tv, tw, LAZ, -0.12F, 0.6F);
			return;
		}
		if ((flags & KultivatorNpc.FLAG_WORK) != 0 && !walking) {
			work(role, t, tv, tw);
			return;
		}
		if (talk) {
			boolean grand = role == NpcRole.JURU_LELANG || role == NpcRole.PENCERAMAH || role == NpcRole.JURU_LELANG_ABADI;
			float base = grand ? -1.45F : -0.6F;
			set(tv, tw, RAX, base + (grand ? 0.4F : 0.25F) * Mth.sin(t * 0.33F), 0.9F);
			set(tv, tw, RAY, -0.25F + 0.1F * Mth.sin(t * 0.17F), 0.9F);
			set(tv, tw, RAZ, 0.14F + 0.08F * Mth.sin(t * 0.21F), 0.9F);
			set(tv, tw, HX, 0.07F * Mth.sin(t * 0.5F), 1.0F);
			set(tv, tw, HY, 0.09F * Mth.sin(t * 0.13F), 1.0F);
			stance(npc, role, state, t, tv, tw, true, walking);
			return;
		}
		stance(npc, role, state, t, tv, tw, false, walking);
	}

	/** The role's resting stance; {@code leftOnly} keeps the right arm for a gesture. */
	private static void stance(KultivatorNpc npc, NpcRole role, DiscipleRenderState state, float t, float[] tv, float[] tw, boolean leftOnly,
			boolean walking) {
		float w = 0.85F;
		switch (role) {
			case JURU_LELANG, PENCERAMAH, UTUSAN, JURU_LELANG_ABADI -> {
				// hands behind the back, even while walking
				if (!leftOnly) {
					set(tv, tw, RAX, 0.5F, w);
					set(tv, tw, RAZ, -0.22F, w);
				}
				set(tv, tw, LAX, 0.5F, w);
				set(tv, tw, LAZ, 0.22F, w);
			}
			case MAKELAR_GELAP -> folded(tv, tw, w, leftOnly);
			case SAINGAN -> {
				if (npc.hasFlag(KultivatorNpc.FLAG_LEADER) && !walking) {
					folded(tv, tw, w, leftOnly);
				} else if (!walking) {
					hilt(tv, tw, w);
				}
			}
			case PENGEMBARA -> {
				if (!walking && state.rightHandItemState.isEmpty()) {
					hilt(tv, tw, w);
				}
			}
			case ABADI, PEDAGANG_ABADI, PENJAGA_KEBUN, PEMILIK_TEH, PEDAGANG_PIL -> {
				if (walking && role != NpcRole.ABADI) {
					break;
				}
				// hands clasped in the sleeves before the belly
				float lift = role == NpcRole.ABADI ? -0.72F : -0.5F;
				if (!leftOnly) {
					set(tv, tw, RAX, lift, w);
					set(tv, tw, RAY, -0.5F, w);
				}
				set(tv, tw, LAX, lift, w);
				set(tv, tw, LAY, 0.5F, w);
			}
			case PENJAGA -> {
				if (!walking && !leftOnly) {
					// the spear at the ready, the other hand on the belt
					set(tv, tw, RAX, -0.42F, w);
					set(tv, tw, RAZ, 0.06F, w);
				}
				if (!walking) {
					set(tv, tw, LAX, -0.12F, w);
					set(tv, tw, LAZ, 0.12F, w);
				}
			}
			case PANDAI_PUSAKA -> {
				if (!walking && !leftOnly) {
					set(tv, tw, RAX, -0.12F, w);
				}
			}
			default -> {
			}
		}
		if (!walking) {
			set(tv, tw, LEAN, 0.025F * Mth.sin(t * 0.09F), 1.0F);
		}
	}

	private static void folded(float[] tv, float[] tw, float w, boolean leftOnly) {
		if (!leftOnly) {
			set(tv, tw, RAX, -1.0F, w);
			set(tv, tw, RAY, -0.78F, w);
			set(tv, tw, RAZ, 0.06F, w);
		}
		set(tv, tw, LAX, -0.95F, w);
		set(tv, tw, LAY, 0.78F, w);
		set(tv, tw, LAZ, -0.06F, w);
	}

	/** The left hand rests on the sword at the left hip. */
	private static void hilt(float[] tv, float[] tw, float w) {
		set(tv, tw, LAX, -0.38F, w);
		set(tv, tw, LAY, 0.32F, w);
		set(tv, tw, LAZ, 0.16F, w);
	}

	private static void work(NpcRole role, float t, float[] tv, float[] tw) {
		switch (role) {
			case PANDAI_PUSAKA -> {
				// the hammer: a slow rise, a quick fall on the anvil
				float s = 0.5F + 0.5F * Mth.sin(t * 0.3F);
				set(tv, tw, RAX, -0.5F - 1.7F * s * s * s, 1.0F);
				set(tv, tw, RAZ, 0.08F, 1.0F);
				set(tv, tw, LAX, -0.7F, 1.0F);
				set(tv, tw, LAY, 0.3F, 1.0F);
				set(tv, tw, HX, 0.35F, 1.0F);
				set(tv, tw, LEAN, 0.12F, 1.0F);
			}
			case PEMILIK_TEH -> {
				// the teapot tips over a cup in the other hand
				float pour = 0.5F + 0.5F * Mth.sin(t * 0.12F);
				set(tv, tw, RAX, -1.05F, 1.0F);
				set(tv, tw, RAY, -0.25F + 0.15F * pour, 1.0F);
				set(tv, tw, RAZ, -0.25F * pour, 1.0F);
				set(tv, tw, LAX, -0.85F, 1.0F);
				set(tv, tw, LAY, 0.45F, 1.0F);
				set(tv, tw, HX, 0.3F, 1.0F);
			}
			case WARGA -> {
				// the morning exercise: slow arm circles, feet apart
				float p = t * 0.07F;
				set(tv, tw, RAX, -1.3F + 0.7F * Mth.sin(p), 1.0F);
				set(tv, tw, LAX, -1.3F + 0.7F * Mth.sin(p + Mth.PI), 1.0F);
				set(tv, tw, RAZ, 0.2F + 0.35F * Mth.cos(p), 1.0F);
				set(tv, tw, LAZ, -(0.2F + 0.35F * Mth.cos(p + Mth.PI)), 1.0F);
				set(tv, tw, RLZ, 0.12F, 1.0F);
				set(tv, tw, LLZ, -0.12F, 1.0F);
				set(tv, tw, LEAN, 0.04F * Mth.sin(p), 1.0F);
			}
			default -> {
				// the abacus (merchants, the broker counting coins)
				set(tv, tw, RAX, -0.85F + 0.12F * Mth.sin(t * 0.9F), 1.0F);
				set(tv, tw, RAY, -0.35F, 1.0F);
				set(tv, tw, LAX, -0.85F, 1.0F);
				set(tv, tw, LAY, 0.35F, 1.0F);
				set(tv, tw, HX, 0.35F, 1.0F);
			}
		}
	}

	static void clear() {
		BLENDS.clear();
	}

	private NpcPoses() {
	}
}
