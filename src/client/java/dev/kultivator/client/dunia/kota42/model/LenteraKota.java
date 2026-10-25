package dev.kultivator.client.dunia.kota42.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.kota42.render.KotaModel;
import dev.kultivator.client.epic.ShaderCompat;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The paper lanterns of the living towns (DESIGN 42.12.4 "Lentera kota", 42.9.2 {@code LenteraKota}): five kinds of
 * cuboid lantern ({@link #BULAT} round red, {@link #PANJANG} long white, {@link #LABU} gourd for the Ghost Market,
 * {@link #KELINCI} the Mid-Autumn rabbit on its stick, {@link #HEKSA} hexagonal silk), each a frame mesh
 * ({@code KotaMesh.lentera}: caps, hooks, tassels) and a paper mesh ({@code KotaMesh.lenteraKertas}) painted pale with
 * bamboo ribs and tinted with the lantern colour, so one model serves red, white, green, blue and a sect's colour.
 * <p>
 * This class only draws; 42V-F decides where (titik {@code lentera_gantung}), when (lit 18:00 in a wave from the gate,
 * out at 06:00) and how many (EffectQuality budgets). {@link #gambar} expects the pose stack at the hook point in world
 * units, draws the lantern hanging below it, swings it +-4 degrees in the wind and, while lit, adds the emissive paper pass
 * (vanilla {@code eyes} type: full bright, no post chain) with a 0.9-1.0 candle flicker; nothing in a shadow pass.
 * Drawing is deferred by the collector, so every call carries its own pose state. Client thread only.
 */
public final class LenteraKota {

	public static final int BULAT = 0;
	public static final int PANJANG = 1;
	public static final int LABU = 2;
	public static final int KELINCI = 3;
	public static final int HEKSA = 4;
	public static final int JENIS = 5;

	/** The design's colours (42.9.2): ordinary red, mourning white, Ghost Market green, Water Lantern blue. */
	public static final int MERAH = 0xD8382A;
	public static final int PUTIH = 0xF2EEE4;
	public static final int HIJAU = 0x5AD07A;
	public static final int BIRU = 0x4F8AE0;

	private static final Identifier TEX_RANGKA = Kultivator.id("textures/entity/kota42/lentera.png");
	private static final Identifier TEX_KERTAS = Kultivator.id("textures/entity/kota42/lentera_kertas.png");
	private static final RenderType RANGKA = RenderTypes.entityCutout(TEX_RANGKA);
	private static final RenderType KERTAS = RenderTypes.entityCutout(TEX_KERTAS);
	private static final RenderType PANCAR = RenderTypes.eyes(Kultivator.id("textures/entity/kota42/lentera_kertas_pancar.png"));

	/** One lantern's pose, captured per call (the collector poses the model when it flushes). */
	public static final class Keadaan extends EntityRenderState {
		int jenis;
		float ayunX;
		float ayunZ;
	}

	private static @Nullable KotaModel<Keadaan> rangka;
	private static @Nullable KotaModel<Keadaan> kertas;

	/**
	 * Draws one lantern hanging from the pose stack's origin (world units; the stack is left as it was).
	 *
	 * @param jenis {@link #BULAT} .. {@link #HEKSA}
	 * @param rgb paper colour 0xRRGGBB
	 * @param nyala lit strength 0 (out: paper in daylight) .. 1 (fully lit)
	 * @param light packed light at the lantern
	 * @param waktu game time with partial ticks (sway and flicker)
	 * @param fase a stable per-lantern phase 0..2pi so neighbours never swing in step
	 * @param jauh far away: no sway, no flicker
	 */
	public static void gambar(PoseStack poseStack, SubmitNodeCollector collector, int jenis, int rgb, float nyala, int light, float waktu, float fase,
			boolean jauh) {
		if (rangka == null || kertas == null) {
			rangka = new KotaModel<>(KotaMesh.lentera().bakeRoot(), LenteraKota::pose);
			kertas = new KotaModel<>(KotaMesh.lenteraKertas().bakeRoot(), LenteraKota::pose);
		}
		Keadaan k = new Keadaan();
		k.jenis = Mth.clamp(jenis, 0, JENIS - 1);
		if (!jauh) {
			k.ayunZ = KotaModel.ayun(waktu, fase, 4.0F * Mth.DEG_TO_RAD, 0.045F);
			k.ayunX = KotaModel.ayun(waktu, fase + 2.1F, 2.5F * Mth.DEG_TO_RAD, 0.037F);
		}
		poseStack.pushPose();
		// model space: y down from the hook (a 180 degree turn about z, like the mob model flip)
		poseStack.rotateDegrees(Axis.ZP, 180.0F);
		float lit = Mth.clamp(nyala, 0.0F, 1.0F);
		int paperLight = lit > 0.5F ? LightCoordsUtil.FULL_BRIGHT : light;
		collector.submitModel(rangka, k, poseStack, RANGKA, light, OverlayTexture.NO_OVERLAY, -1, null, 0);
		collector.submitModel(kertas, k, poseStack, KERTAS, paperLight, OverlayTexture.NO_OVERLAY, ARGB.opaque(rgb), null, 0);
		if (lit > 0.02F && !ShaderCompat.shadowPass()) {
			float flick = jauh ? 0.95F : 0.95F + 0.03F * Mth.sin(waktu * 0.61F + fase) + 0.02F * Mth.sin(waktu * 1.37F + fase * 2.3F);
			int a = Math.round(255.0F * lit * flick * (EffectQuality.minimal() ? 0.8F : 1.0F));
			collector.order(1).submitModel(kertas, k, poseStack, PANCAR, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ARGB.color(a, rgb & 0xFFFFFF),
					null, 0);
		}
		poseStack.popPose();
	}

	private static void pose(KotaModel<Keadaan> m, Keadaan k) {
		for (int i = 0; i < JENIS; i++) {
			var part = m.p("jenis_" + i);
			part.visible = i == k.jenis;
			if (i == k.jenis) {
				part.zRot = k.ayunZ;
				part.xRot = k.ayunX;
			}
		}
	}

	private LenteraKota() {
	}
}
