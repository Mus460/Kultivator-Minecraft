package dev.kultivator.client.sekte.tetua;

import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.sekte.SektePoses;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.tetua.TetuaPayloads;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The two poses module E owns (DESIGN 36.3.3, C36.L6) and the succession poses:
 * <ul>
 * <li>{@code ACT_OFFICE} (17): an elder at the desk of its office, the opened register in the left hand and the brush in
 * the right; it writes a column, lifts its head to think, and writes on (a 9 s cycle, each elder offset by its id).
 * During a Succession Ceremony the same activity carries the rows and the heir there, so a body the server named in
 * E's {@code Ceremony} payload bows instead (the rows: a deep bow with joined hands; the heir: kneeling, then risen and
 * receiving the robe and bowl with both hands raised).</li>
 * <li>{@code ACT_BOW} (23, pose only; A and T use it too): the formal bow with the cupped-fist salute, rising slowly.</li>
 * </ul>
 * Poses only move the body model's parts toward a target by the blend {@code s} (the lead's DutyPoses does the blending
 * and stops it while walking, fighting or casting), so nothing here costs more than a few trig calls per visible body.
 */
public final class TetuaPoses {

	/** The bowing rows and the heir of the ceremony near this client (entity ids), until {@link #ceremonyUntil}. */
	private static final Set<Integer> ROWS = new HashSet<>();
	private static int heirId = -1;
	private static int phase = -1;
	private static long ceremonyUntil;
	private static long clientTicks;

	static void init() {
		SektePoses.register(SekteIds.ACT_OFFICE, TetuaPoses::office, List.of(
				new SektePoses.Prop("sekte_tetua_brush", false, 0.55F, 20.0F),
				new SektePoses.Prop("sekte_tetua_scroll", true, 0.6F, -15.0F)));
		SektePoses.register(SekteIds.ACT_BOW, TetuaPoses::bow, List.of());
	}

	// ================================================================== ceremony state

	static void onCeremony(TetuaPayloads.Ceremony p) {
		ROWS.clear();
		if (p.phase() >= 3 || p.life() <= 0) {
			heirId = -1;
			phase = -1;
			ceremonyUntil = 0L;
			return;
		}
		for (int id : p.ids()) {
			ROWS.add(id);
		}
		heirId = p.heirId();
		phase = p.phase();
		ceremonyUntil = clientTicks + p.life();
	}

	static void tick() {
		clientTicks++;
		if (ceremonyUntil != 0L && clientTicks > ceremonyUntil) {
			ROWS.clear();
			heirId = -1;
			phase = -1;
			ceremonyUntil = 0L;
		}
	}

	static void clear() {
		ROWS.clear();
		heirId = -1;
		phase = -1;
		ceremonyUntil = 0L;
	}

	// ================================================================== poses

	private static void office(PlayerModel model, DiscipleRenderState state, float s, float t) {
		if (state.id == heirId) {
			heir(model, s, t);
			return;
		}
		if (ROWS.contains(state.id)) {
			// rows bow while the heir kneels; during the gathering they stand at attention, hands joined
			bowDeep(model, s, t, phase >= 1 ? 1.0F : 0.35F);
			return;
		}
		// the desk: write a column, look up, think, write on
		float cycle = (t + state.id * 37.0F) % 180.0F;
		boolean thinking = cycle > 130.0F;
		float stroke = Mth.sin(t * 0.55F) * 0.07F;
		float down = thinking ? 0.05F : 0.55F;
		model.head.xRot = Mth.lerp(s, model.head.xRot, down);
		model.head.yRot = Mth.lerp(s, model.head.yRot, thinking ? Mth.sin(t * 0.05F) * 0.25F : -0.12F);
		pose(model.leftArm, s, -1.05F, 0.35F, 0.05F);
		if (thinking) {
			// the brush lifted, the free hand at the chin
			pose(model.rightArm, s, -1.9F, -0.55F, 0.1F);
		} else {
			pose(model.rightArm, s, -0.95F + stroke, -0.3F + Mth.cos(t * 0.55F) * 0.05F, 0.0F);
		}
		model.body.xRot = Mth.lerp(s, model.body.xRot, thinking ? 0.0F : 0.12F);
	}

	/** ACT_BOW: a slow bow with the cupped-fist salute that rises again (2.5 s cycle, then held upright). */
	private static void bow(PlayerModel model, DiscipleRenderState state, float s, float t) {
		float k = Mth.clamp(Mth.sin(Math.min(t % 120.0F, 50.0F) / 50.0F * Mth.PI), 0.0F, 1.0F);
		salute(model, s);
		model.body.xRot = Mth.lerp(s, model.body.xRot, 0.45F * k);
		model.head.xRot = Mth.lerp(s, model.head.xRot, 0.35F * k);
		lean(model, s * k * 0.8F);
	}

	private static void bowDeep(PlayerModel model, float s, float t, float depth) {
		salute(model, s);
		float breath = Mth.sin(t * 0.06F) * 0.02F;
		model.body.xRot = Mth.lerp(s, model.body.xRot, 0.55F * depth + breath);
		model.head.xRot = Mth.lerp(s, model.head.xRot, 0.5F * depth);
		lean(model, s * depth);
	}

	/** The heir: kneels while the rows bow, then rises and raises the robe and bowl with both hands. */
	private static void heir(PlayerModel model, float s, float t) {
		if (phase >= 2) {
			float lift = Mth.sin(t * 0.08F) * 0.05F;
			pose(model.rightArm, s, -1.7F + lift, -0.35F, 0.0F);
			pose(model.leftArm, s, -1.7F + lift, 0.35F, 0.0F);
			model.head.xRot = Mth.lerp(s, model.head.xRot, -0.25F);
			return;
		}
		// kneeling: the hips 6 px lower, so a 12 px leg at 60 degrees just reaches the ground: the right knee back, the
		// left foot forward, the body low and bowed
		float k = s;
		model.rightLeg.xRot = Mth.lerp(k, model.rightLeg.xRot, 1.05F);
		model.leftLeg.xRot = Mth.lerp(k, model.leftLeg.xRot, -1.05F);
		model.rightLeg.y += 6.0F * k;
		model.leftLeg.y += 6.0F * k;
		model.body.y += 6.0F * k;
		model.head.y += 6.0F * k;
		model.rightArm.y += 6.0F * k;
		model.leftArm.y += 6.0F * k;
		salute(model, s);
		model.body.xRot = Mth.lerp(s, model.body.xRot, 0.35F);
		model.head.xRot = Mth.lerp(s, model.head.xRot, 0.55F);
	}

	/** The cupped-fist salute: both forearms forward, hands meeting in front of the chest. */
	private static void salute(PlayerModel model, float s) {
		pose(model.rightArm, s, -1.2F, -0.42F, 0.0F);
		pose(model.leftArm, s, -1.2F, 0.42F, 0.0F);
	}

	/** A bowing body takes the head and shoulders forward and down with it (like the duty poses' bend). */
	private static void lean(PlayerModel model, float k) {
		model.head.y += 3.6F * k;
		model.body.y += 2.6F * k;
		model.rightArm.y += 2.6F * k;
		model.leftArm.y += 2.6F * k;
		model.rightLeg.z += 3.2F * k;
		model.leftLeg.z += 3.2F * k;
	}

	private static void pose(ModelPart part, float s, float x, float y, float z) {
		part.xRot = Mth.lerp(s, part.xRot, x);
		part.yRot = Mth.lerp(s, part.yRot, y);
		part.zRot = Mth.lerp(s, part.zRot, z);
	}

	private TetuaPoses() {
	}
}
