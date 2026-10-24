package dev.kultivator.client.sosok.gerak;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.client.render.MeditationPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The props of the Gerak system on a Sosok body (DESIGN 40.5.7): what {@link GerakData} says this frame, drawn as the
 * {@link PropKatalog} item models at their attachment points.
 * <ul>
 * <li>{@code KANAN}/{@code KIRI}: in the fist ({@code translateToHand} and the fist offset of
 * {@code DiscipleHeldItemLayer}), turned by the prop's grip ({@link PropKatalog#tangan}); the left hand mirrors it. A
 * hand that holds a real item (or the 3D sword) keeps it and shows no prop.</li>
 * <li>{@code DUA_TANGAN}: a long prop (brooms, hoes, spears) in the right hand's grip; a flat one (books, the abacus, a
 * chest, cloth) between the two fists, its width along the line from the right fist to the left one (so it tilts when
 * one hand is higher and turns with the torso).</li>
 * <li>{@code PUNGGUNG}, {@code PINGGANG_KIRI}/{@code KANAN}, {@code BAHU_KIRI}, {@code PANGKUAN} (only while seated):
 * on the body; {@code KEPALA} on the head; {@code DEPAN_KAKI} on the ground before the feet (the top spins).</li>
 * </ul>
 * Nothing while the body fights, casts or duels or beyond the KURANG level of detail ({@code GerakData.propSembunyi}); at
 * KURANG only big props. Clip props give way to the legacy duty prop ({@code propLama}, drawn by
 * {@code DiscipleHeldItemLayer}); a life path's worn props show at the PENUH level. The layer is wrapped by 40R's gate
 * (LOD, shadow pass). Nothing is allocated per frame: the pose scratch, vectors and one item state per prop index are
 * kept.
 */
public final class SosokPropLayer extends RenderLayer<DiscipleRenderState, PlayerModel> {

	private static final float PX = 1.0F / 16.0F;
	private static final ItemStackRenderState[] STATES = new ItemStackRenderState[256];

	private final PoseStack ukur = new PoseStack();
	private final Vector3f kanan = new Vector3f();
	private final Vector3f kiri = new Vector3f();
	private final Vector3f sumbuX = new Vector3f();
	private final Vector3f sumbuY = new Vector3f();
	private final Vector3f sumbuZ = new Vector3f();
	private final Matrix3f basis = new Matrix3f();
	private final Quaternionf putaran = new Quaternionf();
	private final Quaternionf lepas = new Quaternionf();

	public SosokPropLayer(RenderLayerParent<DiscipleRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, DiscipleRenderState state, float yRot, float xRot) {
		GerakData d = GerakData.dari(state);
		if (d == null || d.propSembunyi || state.isInvisible || d.lod > GerakPengendali.LOD_KURANG) {
			return;
		}
		boolean penuh = d.lod == GerakPengendali.LOD_PENUH;
		boolean duduk = MeditationPose.strength(state) > 0.5F || state.seated > 0.5F;
		if (!d.propLama) {
			if (d.propKanan != 0 && boleh(d.propKanan, penuh) && state.rightHandItemState.isEmpty() && !state.heldSword) {
				tangan(poseStack, collector, lightCoords, state, HumanoidArm.RIGHT, d.propKanan, null);
			}
			if (d.propKiri != 0 && boleh(d.propKiri, penuh) && state.leftHandItemState.isEmpty()) {
				tangan(poseStack, collector, lightCoords, state, HumanoidArm.LEFT, d.propKiri, null);
			}
			if (d.propPunggung != 0 && boleh(d.propPunggung, penuh)) {
				badan(poseStack, collector, lightCoords, state, d.propPunggung, PropKatalog.punggung(d.propPunggung), null);
			}
			if (d.pasang != null) {
				for (Klip.PropPasang p : d.pasang) {
					pasang(poseStack, collector, lightCoords, state, p, penuh, duduk);
				}
			}
		}
		if (d.pasangTetap != null && penuh) {
			for (Klip.PropPasang p : d.pasangTetap) {
				pasang(poseStack, collector, lightCoords, state, p, true, duduk);
			}
		}
	}

	private static boolean boleh(int id, boolean penuh) {
		return (penuh || PropKatalog.besar(id)) && PropKatalog.ada(id);
	}

	// ================================================================== attachment points

	private void pasang(PoseStack ps, SubmitNodeCollector c, int light, DiscipleRenderState state, Klip.PropPasang p, boolean penuh,
			boolean duduk) {
		int id = p.prop();
		if (id == 0 || !boleh(id, penuh)) {
			return;
		}
		PlayerModel model = this.getParentModel();
		switch (p.titik()) {
			case KANAN -> {
				if (state.rightHandItemState.isEmpty() && !state.heldSword) {
					tangan(ps, c, light, state, HumanoidArm.RIGHT, id, p);
				}
			}
			case KIRI -> {
				if (state.leftHandItemState.isEmpty()) {
					tangan(ps, c, light, state, HumanoidArm.LEFT, id, p);
				}
			}
			case DUA_TANGAN -> {
				if (PropKatalog.tangan(id).panjang()) {
					if (state.rightHandItemState.isEmpty() && !state.heldSword) {
						tangan(ps, c, light, state, HumanoidArm.RIGHT, id, p);
					}
				} else if (state.rightHandItemState.isEmpty() && state.leftHandItemState.isEmpty() && !state.heldSword) {
					duaTangan(ps, c, light, state, id, p);
				}
			}
			case PUNGGUNG -> badan(ps, c, light, state, id, PropKatalog.punggung(id), p);
			// at the front corners of the belt: the design's (+-4.6, 9, 0) lies inside the hanging arm (checked in the
			// D-40G grip previews), so pouches, pendants and bottles hang a little forward of the hip instead
			case PINGGANG_KIRI -> titikBadan(ps, c, light, state, id, p, 2.6F, 10.5F, -2.6F, 0.0F);
			case PINGGANG_KANAN -> titikBadan(ps, c, light, state, id, p, -2.6F, 10.5F, -2.6F, 0.0F);
			case BAHU_KIRI -> titikBadan(ps, c, light, state, id, p, 4.2F, 0.6F, -1.6F, 0.0F);
			case PANGKUAN -> {
				if (duduk) {
					titikBadan(ps, c, light, state, id, p, 0.0F, 11.5F, -4.0F, -90.0F);
				}
			}
			case KEPALA -> {
				ps.pushPose();
				model.root().translateAndRotate(ps);
				model.head.translateAndRotate(ps);
				ps.translate(0.0F, -8.6F * PX, 0.5F * PX);
				geser(ps, p.dx(), p.dy(), p.dz(), p.rx(), p.ry(), p.rz(), p.skala());
				gambar(ps, c, light, state, id);
				ps.popPose();
			}
			case DEPAN_KAKI -> {
				ps.pushPose();
				model.root().translateAndRotate(ps);
				// on the floor: undo how far setupRotations raised or lowered the body (squats, seats, bob)
				ps.translate(0.0F, 23.5F * PX + GerakApplier.naikModel(state), -7.0F * PX);
				if (id == dev.kultivator.sosok.PropId.GASING) {
					// the spinning top turns 30 degrees a tick (38.O.4)
					ps.rotateDegrees(Axis.YP, state.ageInTicks * 30.0F % 360.0F);
				}
				geser(ps, p.dx(), p.dy(), p.dz(), p.rx(), p.ry(), p.rz(), p.skala());
				gambar(ps, c, light, state, id);
				ps.popPose();
			}
		}
	}

	/** In one fist: the grip (mirrored for the left hand), then the clip's own offset. */
	private void tangan(PoseStack ps, SubmitNodeCollector c, int light, DiscipleRenderState state, HumanoidArm arm, int id,
			Klip.PropPasang p) {
		float m = arm == HumanoidArm.LEFT ? -1.0F : 1.0F;
		ps.pushPose();
		PlayerModel model = this.getParentModel();
		model.translateToHand(state, arm, ps);
		ps.translate(-m * PX, 10.5F * PX, -0.5F * PX);
		PropKatalog.Pegangan g = PropKatalog.tangan(id);
		if (g.tegak()) {
			// hanging and level props: undo the arm's turn at the fist (arms turn relative to the upright root)
			var a = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
			ps.rotate(lepas.rotationZYX(a.zRot, a.yRot, a.xRot).conjugate());
		}
		geser(ps, g.dx() * m, g.dy(), g.dz(), g.rx(), g.ry() * m, g.rz() * m, g.skala());
		if (p != null) {
			geser(ps, p.dx() * m, p.dy(), p.dz(), p.rx(), p.ry() * m, p.rz() * m, p.skala());
		}
		gambar(ps, c, light, state, id);
		ps.popPose();
	}

	/** Between the two fists, its x along the right fist to the left one, upright otherwise. */
	private void duaTangan(PoseStack ps, SubmitNodeCollector c, int light, DiscipleRenderState state, int id, Klip.PropPasang p) {
		PlayerModel model = this.getParentModel();
		kepalan(model, state, HumanoidArm.RIGHT, kanan);
		kepalan(model, state, HumanoidArm.LEFT, kiri);
		sumbuX.set(kiri).sub(kanan);
		if (sumbuX.lengthSquared() < 1.0E-6F) {
			sumbuX.set(1.0F, 0.0F, 0.0F);
		} else {
			sumbuX.normalize();
		}
		// y: model "down" made perpendicular to x; z completes the right-handed frame
		sumbuY.set(sumbuX).mul(-sumbuX.y).add(0.0F, 1.0F, 0.0F);
		if (sumbuY.lengthSquared() < 1.0E-6F) {
			sumbuY.set(0.0F, 1.0F, 0.0F);
		} else {
			sumbuY.normalize();
		}
		sumbuX.cross(sumbuY, sumbuZ);
		basis.set(sumbuX, sumbuY, sumbuZ);
		putaran.setFromNormalized(basis);
		ps.pushPose();
		ps.translate((kanan.x + kiri.x) * 0.5F, (kanan.y + kiri.y) * 0.5F, (kanan.z + kiri.z) * 0.5F);
		ps.rotate(putaran);
		PropKatalog.Pegangan g = PropKatalog.duaTangan(id);
		geser(ps, g.dx(), g.dy(), g.dz(), g.rx(), g.ry(), g.rz(), g.skala());
		geser(ps, p.dx(), p.dy(), p.dz(), p.rx(), p.ry(), p.rz(), p.skala());
		gambar(ps, c, light, state, id);
		ps.popPose();
	}

	/** The fist point of an arm after the pose, in the layer's model space (blocks). */
	private void kepalan(PlayerModel model, DiscipleRenderState state, HumanoidArm arm, Vector3f out) {
		ukur.pushPose();
		ukur.setIdentity();
		model.translateToHand(state, arm, ukur);
		ukur.translate((arm == HumanoidArm.LEFT ? 1.0F : -1.0F) * PX, 10.5F * PX, -0.5F * PX);
		ukur.last().pose().transformPosition(0.0F, 0.0F, 0.0F, out);
		ukur.popPose();
	}

	/** On the body (the back): a point and turn from the neck, then the clip's own offset. */
	private void badan(PoseStack ps, SubmitNodeCollector c, int light, DiscipleRenderState state, int id, PropKatalog.Pegangan g,
			Klip.PropPasang p) {
		PlayerModel model = this.getParentModel();
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.body.translateAndRotate(ps);
		geser(ps, g.dx(), g.dy(), g.dz(), g.rx(), g.ry(), g.rz(), g.skala());
		if (p != null) {
			geser(ps, p.dx(), p.dy(), p.dz(), p.rx(), p.ry(), p.rz(), p.skala());
		}
		gambar(ps, c, light, state, id);
		ps.popPose();
	}

	private void titikBadan(PoseStack ps, SubmitNodeCollector c, int light, DiscipleRenderState state, int id, Klip.PropPasang p, float x,
			float y, float z, float rx) {
		PlayerModel model = this.getParentModel();
		ps.pushPose();
		model.root().translateAndRotate(ps);
		model.body.translateAndRotate(ps);
		ps.translate(x * PX, y * PX, z * PX);
		if (rx != 0.0F) {
			ps.rotateDegrees(Axis.XP, rx);
		}
		geser(ps, p.dx(), p.dy(), p.dz(), p.rx(), p.ry(), p.rz(), p.skala());
		gambar(ps, c, light, state, id);
		ps.popPose();
	}

	/** Offset in model pixels, then turn x, y, z in degrees, then size. */
	private static void geser(PoseStack ps, float dx, float dy, float dz, float rx, float ry, float rz, float skala) {
		if (dx != 0.0F || dy != 0.0F || dz != 0.0F) {
			ps.translate(dx * PX, dy * PX, dz * PX);
		}
		if (rx != 0.0F) {
			ps.rotateDegrees(Axis.XP, rx);
		}
		if (ry != 0.0F) {
			ps.rotateDegrees(Axis.YP, ry);
		}
		if (rz != 0.0F) {
			ps.rotateDegrees(Axis.ZP, rz);
		}
		if (skala != 1.0F && skala > 0.0F) {
			ps.scale(skala, skala, skala);
		}
	}

	/** Draws prop {@code id} with the piece convention of {@code FormModels} (its +y up, its (8, 8, 8) at the origin). */
	private static void gambar(PoseStack ps, SubmitNodeCollector c, int light, DiscipleRenderState state, int id) {
		ItemStack stack = PropKatalog.stack(id);
		if (stack == null || id < 0 || id >= STATES.length) {
			return;
		}
		ItemStackRenderState item = STATES[id];
		if (item == null) {
			item = new ItemStackRenderState();
			STATES[id] = item;
		}
		Minecraft mc = Minecraft.getInstance();
		mc.getItemModelResolver().updateForTopItem(item, stack, ItemDisplayContext.FIXED, mc.level, null, 0);
		if (item.isEmpty()) {
			return;
		}
		ps.pushPose();
		ps.rotateDegrees(Axis.ZP, 180.0F);
		item.submit(ps, c, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
		ps.popPose();
	}

	/** Forgets the item states (resource reload, world change). */
	static void lupakan() {
		java.util.Arrays.fill(STATES, null);
	}
}
