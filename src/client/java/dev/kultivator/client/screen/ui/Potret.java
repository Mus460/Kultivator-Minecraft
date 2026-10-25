package dev.kultivator.client.screen.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The single portrait of the kit (DESIGN 43.7, moved from {@code tempat/ui/kit}): the speaker of {@code GelembungDialog}, PemilihOrang rows and recruit cards (38.U.3.1): the NPC's real head (its model
 * with the 38.O / 40 face, so it blinks with its renderer) in a gold frame, rendered live as a picture-in-picture from the
 * entity id the sheet sends ({@code potret}). While the NPC "speaks" the head nods and turns a little; a frightened NPC
 * ({@code takut}, 38.R.5) looks down and trembles. Without a live entity the frame shows the sheet's icon on a paper
 * ground, so the screen still reads.
 */
public final class Potret {

	/**
	 * Draws a portrait {@code size} px square at (x, y). {@code bicara} 0..1 how much the speaker is talking now (typewriter
	 * running).
	 */
	public static void gambar(Lukis l, int x, int y, int size, int entityId, int ikon, boolean takut, float bicara) {
		Glyphs.Atlas.bingkaiPotret(l.g, x, y, size);
		int in = Math.max(2, Math.round(size * 4.0F / 56.0F));
		int wx0 = x + in;
		int wy0 = y + in;
		int wx1 = x + size - in;
		int wy1 = y + size - in;
		// window ground: dusk sky over paper
		l.g.fillGradient(wx0, wy0, wx1, wy1, 0xFF34405A, 0xFF8A7A62);
		Entity entity = entityId >= 0 && Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getEntity(entityId) : null;
		if (entity instanceof LivingEntity living && gambarKepala(l, living, wx0, wy0, wx1, wy1, takut, bicara)) {
			return;
		}
		int s = Math.max(16, (wx1 - wx0) * 2 / 3);
		if (ikon >= 0) {
			Glyphs.Atlas.ikon(l.g, ikon, (wx0 + wx1 - s) / 2, (wy0 + wy1 - s) / 2, s, 0xFFFFFFFF);
		} else {
			// a brush silhouette: head and shoulders
			float cx = (wx0 + wx1) / 2.0F;
			Brush.disc(l.g, cx, wy0 + (wy1 - wy0) * 0.42F, (wx1 - wx0) * 0.2F, Palette.argb(0xC0, 0x1B2521));
			Brush.disc(l.g, cx, wy1 + (wy1 - wy0) * 0.12F, (wx1 - wx0) * 0.42F, Palette.argb(0xC0, 0x1B2521));
		}
	}

	private static boolean gambarKepala(Lukis l, LivingEntity entity, int x0, int y0, int x1, int y1, boolean takut, float bicara) {
		EntityRenderState state;
		try {
			EntityRenderer<? super LivingEntity, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
			state = renderer.createRenderState(entity, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		} catch (RuntimeException ex) {
			return false;
		}
		state.shadowPieces.clear();
		state.outlineColor = 0;
		state.nameTag = null;
		float t = l.kini / 1000.0F;
		float headYaw = (float) Math.sin(t * 1.3F) * 6.0F * (0.3F + bicara);
		float headPitch = (float) Math.sin(t * 7.0F) * 3.0F * bicara;
		float tremble = 0.0F;
		if (takut) {
			headPitch = 22.0F;
			tremble = (float) Math.sin(t * 40.0F) * 0.6F;
		}
		float eye = state.eyeHeight > 0.0F ? state.eyeHeight : state.boundingBoxHeight * 0.85F;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F;
			living.yRot = headYaw;
			living.xRot = headPitch;
			eye = eye / Math.max(0.1F, living.scale);
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
		}
		int size = Math.min(x1 - x0, y1 - y0);
		float scale = size / 0.62F * l.skala;
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(-14.0D));
		Vector3f translation = new Vector3f(tremble * 0.01F, eye + (takut ? -0.06F : 0.02F), 0.0F);
		l.g.entity(state, scale, translation, rotation, null, l.nyata(x0), l.nyata(y0), l.nyata(x1), l.nyata(y1));
		// the frame's inner shadow over the head
		l.g.fill(x0, y0, x1, y0 + 1, 0x60000000);
		l.g.fill(x0, y0, x0 + 1, y1, 0x60000000);
		return true;
	}

	/** Size helper: the portrait size for a layout row height. */
	public static int ukuran(int tersedia) {
		return Math.max(28, Math.min(56, tersedia));
	}

	private Potret() {
	}

	/** Icon used when a sheet has no portrait or icon. */
	public static final int IKON_BAWAAN = LembarKunci.Ikon.TIDAK_ADA;
}
