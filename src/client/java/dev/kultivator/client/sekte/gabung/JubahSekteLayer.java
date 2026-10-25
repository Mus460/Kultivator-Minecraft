package dev.kultivator.client.sekte.gabung;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.film.FilmDirector;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.client.sekte.seragam.JubahLook;
import dev.kultivator.client.sekte.seragam.LambangCache;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.Pangkat;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * The member's sect robe (DESIGN 41.11.5): a half-length outer robe drawn over the player (and over the cultivator robe
 * of {@code RobeLayer}) while the player belongs to an NPC sect: a back panel to mid-thigh, two open front panels, a
 * shoulder mantle, a high collar, a broad belt and wide sleeves with flared cuffs, all cut from the 40K garment cubes
 * ({@code jubah_luar}, {@code jubah_luar_bahu}, {@code kerah_tinggi}, {@code sabuk}, {@code lengan_lebar_manset}). Three
 * passes on the same cut:
 * <ol>
 * <li>the cloth ({@code textures/entity/gabung/jubah_sekte.png}, greyscale with weave, folds and quilting) tinted with
 * the sect's ink colour from the {@code gabung_lencana} payload ({@link GabungClient#lencana});</li>
 * <li>the hems and piping ({@code jubah_sekte_tepi.png}, greyscale) tinted with the rank's trim colour
 * ({@link Pangkat#warna()}: grey Pelayan ... gold Ketua);</li>
 * <li>the sect emblem 8 x 7 on the back panel from Murid Inti up, copied from the sect's embroidery sheet
 * ({@link JubahLook#embroidery}, the {@code npc:<sect>} uniform the client already holds); skipped while the sheet is not
 * known.</li>
 * </ol>
 * plus the style's ornament (untinted, {@code jubah_sekte_hiasan.png}, 41.4.3 column "gaya" through
 * {@link Corak#hiasan()}): a sword at the hip (Pedang), prayer beads (Buddha), a fur collar (Binatang), a jade tablet and
 * hanging talismans (Formasi), a floating sash (Unsur), a furnace apron and gourd (Alkimia), bare forearms with leather
 * bracers (Tubuh), a chain and blood tassel (Darah), poison pouches (Racun), a folded hood (Bayangan). The style comes
 * from the sect's class line ({@link GabungClient#kelas}); without it the robe has no ornament.
 * <p>
 * The robe is a {@link PlayerModel}, so every pose of the player model (the meditation seat, the cast poses, the Gerak
 * channels, 41V-G's oath clips through the {@code PlayerModelMixin} hook) folds it with the body; the back and front
 * panels hang straight while the body leans, sway, flare with the stride and fold up when seated.
 * <p>
 * Over armour: a thicker cut clears chest armour when the client switch {@link #KUNCI_DI_ATAS_ZIRAH} is on (default);
 * with it off the robe is not drawn while chest armour is worn. Hidden for invisible players, when the server says the
 * member hides the robe ({@code EntriLencana.jubah}), during a film ({@link FilmDirector#playing()}), in the shader
 * shadow pass and on Minimal effect quality. LOD: no emblem beyond 32 blocks, nothing beyond 64.
 * <p>
 * Name plate (41.11.5): a small billboard line "[Dalam]" in the rank's trim colour above the player's name, within 16
 * blocks, when {@link #KUNCI_NAMETAG} is on (default); text from {@code kultivator.gabung.tanda.pangkat.<n>}.
 * <p>
 * The cube tables below ({@code k(...)} and {@code h(...)} lines, one per line) are also read by
 * {@code tools/gen_gabung_assets.py}, which paints the three atlases on exactly these UV footprints: change a number here,
 * run the generator.
 */
public class JubahSekteLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

	/** Client switch (config/kultivator-client.json, row of 41V-P): robe drawn over chest armour. Default true. */
	public static final String KUNCI_DI_ATAS_ZIRAH = "gabung.jubahDiAtasZirah";
	/** Client switch: the rank line above member names. Default true. */
	public static final String KUNCI_NAMETAG = "gabung.nametagPangkat";

	private static final Identifier KAIN = Kultivator.id("textures/entity/gabung/jubah_sekte.png");
	private static final Identifier TEPI = Kultivator.id("textures/entity/gabung/jubah_sekte_tepi.png");
	private static final Identifier HIASAN = Kultivator.id("textures/entity/gabung/jubah_sekte_hiasan.png");
	private static final RenderType KAIN_TYPE = RenderTypes.entityCutout(KAIN);
	private static final RenderType TEPI_TYPE = RenderTypes.entityTranslucent(TEPI);
	private static final RenderType HIASAN_TYPE = RenderTypes.entityCutout(HIASAN);

	/** Robe offset from the skin: over the cultivator robe (0.32) and the jacket layer. */
	static final float TIPIS = 0.4F;
	/** Robe offset over chest armour (vanilla armour stands 1.0 off the body). */
	static final float TEBAL = 1.08F;

	private static final double LOD_LAMBANG_SQ = 32.0D * 32.0D;
	private static final double LOD_JUBAH_SQ = 64.0D * 64.0D;
	private static final double NAMETAG_SQ = 16.0D * 16.0D;

	/** The style ornament of this frame's state (-1 none), read by {@link Hiasan#setupAnim}. */
	static final RenderStateDataKey<Integer> CORAK = RenderStateDataKey.create(() -> Kultivator.MOD_ID + ":gabung_jubah_corak");

	static final String BADAN = "body";
	static final String KANAN = "right_arm";
	static final String KIRI = "left_arm";

	/** A cube of the robe: parent part, pivot, box, UV, mirrored, arm cut (0 both, 1 wide arms, 2 slim arms), style (-1 any). */
	record Kubus(String id, String induk, float px, float py, float pz, float x0, float y0, float z0, float w, float h, float d, int u, int v,
				 boolean cermin, int lengan, int corak) {
	}

	private static Kubus k(String id, String induk, float px, float py, float pz, float x0, float y0, float z0, float w, float h, float d,
			int u, int v, boolean cermin, int lengan) {
		return new Kubus(id, induk, px, py, pz, x0, y0, z0, w, h, d, u, v, cermin, lengan, -1);
	}

	private static Kubus h(String id, String induk, float px, float py, float pz, float x0, float y0, float z0, float w, float h, float d,
			int u, int v, boolean cermin, int lengan, int corak) {
		return new Kubus(id, induk, px, py, pz, x0, y0, z0, w, h, d, u, v, cermin, lengan, corak);
	}

	/** The cloth cut (atlas 64 x 64 of {@code jubah_sekte.png} and {@code jubah_sekte_tepi.png}). */
	static final List<Kubus> KAIN_KUBUS = List.of(
			k("punggung", BADAN, 0, 0.2F, 2.4F, -4.5F, 0, 0, 9, 15, 1, 0, 0, false, 0),
			k("depan_kanan", BADAN, -2.3F, 0.2F, -2.4F, -2, 0, -1, 4, 15, 1, 20, 0, false, 0),
			k("depan_kiri", BADAN, 2.3F, 0.2F, -2.4F, -2, 0, -1, 4, 15, 1, 20, 0, true, 0),
			k("bahu", BADAN, 0, 0, 0, -5, -0.5F, -3, 10, 3, 6, 30, 0, false, 0),
			k("kerah", BADAN, 0, 0, 0, -4.5F, -1.2F, -2.5F, 9, 3, 5, 30, 9, false, 0),
			k("sabuk", BADAN, 0, 6.5F, 0, -5, 0, -3.5F, 10, 2, 7, 0, 16, false, 0),
			k("lengan_kanan", KANAN, 0, 0, 0, -3.5F, -2.5F, -2.5F, 5, 8, 5, 34, 17, false, 1),
			k("lengan_kiri", KIRI, 0, 0, 0, -1.5F, -2.5F, -2.5F, 5, 8, 5, 34, 17, true, 1),
			k("lengan_kanan_r", KANAN, 0, 0, 0, -2.5F, -2.5F, -2.5F, 4, 8, 5, 0, 25, false, 2),
			k("lengan_kiri_r", KIRI, 0, 0, 0, -1.5F, -2.5F, -2.5F, 4, 8, 5, 0, 25, true, 2),
			k("manset_kanan", KANAN, -1, 5.5F, 0, -3, 0, -3, 6, 4, 6, 18, 30, false, 1),
			k("manset_kiri", KIRI, 1, 5.5F, 0, -3, 0, -3, 6, 4, 6, 18, 30, true, 1),
			k("manset_kanan_r", KANAN, -0.5F, 5.5F, 0, -2.5F, 0, -3, 5, 4, 6, 42, 30, false, 2),
			k("manset_kiri_r", KIRI, 0.5F, 5.5F, 0, -2.5F, 0, -3, 5, 4, 6, 42, 30, true, 2),
			k("ekor_kanan", BADAN, -2.3F, 15.2F, 2.4F, -2, 0, 0, 4, 4, 1, 0, 40, false, 0),
			k("ekor_kiri", BADAN, 2.3F, 15.2F, 2.4F, -2, 0, 0, 4, 4, 1, 0, 40, true, 0));

	/** The style ornaments (atlas 64 x 64 of {@code jubah_sekte_hiasan.png}); the last column is the {@link Corak} ordinal. */
	static final List<Kubus> HIASAN_KUBUS = List.of(
			h("sarung_pedang", BADAN, 4.6F, 8.0F, -0.5F, -1, 0, -1, 2, 12, 2, 0, 0, false, 0, 0),
			h("gagang_pedang", BADAN, 4.6F, 8.0F, -0.5F, -0.5F, -5, -0.5F, 1, 5, 1, 8, 0, false, 0, 0),
			h("pelindung_pedang", BADAN, 4.6F, 8.0F, -0.5F, -1.5F, -0.6F, -1, 3, 1, 2, 12, 0, false, 0, 0),
			h("tasbih_kanan", BADAN, -1.2F, 0.4F, -3.2F, -0.5F, 0, -0.5F, 1, 8, 1, 22, 0, false, 0, 1),
			h("tasbih_kiri", BADAN, 1.2F, 0.4F, -3.2F, -0.5F, 0, -0.5F, 1, 8, 1, 22, 0, true, 0, 1),
			h("manik_besar", BADAN, 0, 7.6F, -3.6F, -1, 0, -1, 2, 2, 2, 26, 0, false, 0, 1),
			h("bulu", BADAN, 0, -1.2F, 0, -5.5F, 0, -3.5F, 11, 3, 7, 0, 14, false, 0, 2),
			h("lempeng_giok", BADAN, -2.6F, 8.6F, -3.7F, -1, 0, -0.5F, 2, 3, 1, 34, 0, false, 0, 3),
			h("jimat_kanan", BADAN, 1.6F, 8.6F, -3.6F, -1, 0, -0.5F, 2, 5, 1, 40, 0, false, 0, 3),
			h("jimat_kiri", BADAN, 3.8F, 8.6F, -3.6F, -1, 0, -0.5F, 2, 5, 1, 40, 0, true, 0, 3),
			h("selendang_belakang", BADAN, 0, 2.0F, 3.9F, -6, 0, 0, 12, 3, 1, 36, 14, false, 0, 4),
			h("selendang_kanan", BADAN, -6.0F, 2.6F, 3.9F, -1, 0, -0.5F, 2, 10, 1, 46, 0, false, 0, 4),
			h("selendang_kiri", BADAN, 6.0F, 2.6F, 3.9F, -1, 0, -0.5F, 2, 10, 1, 46, 0, true, 0, 4),
			h("celemek", BADAN, 0, 7.4F, -3.7F, -3.5F, 0, -0.5F, 7, 9, 1, 0, 24, false, 0, 5),
			h("labu", BADAN, -5.2F, 8.6F, 0.6F, -1.5F, 0, -1.5F, 3, 4, 3, 16, 24, false, 0, 5),
			h("pelindung_kanan", KANAN, -1, 6.0F, 0, -2.5F, 0, -2.5F, 5, 5, 5, 28, 24, false, 1, 6),
			h("pelindung_kiri", KIRI, 1, 6.0F, 0, -2.5F, 0, -2.5F, 5, 5, 5, 28, 24, true, 1, 6),
			h("pelindung_kanan_r", KANAN, -0.5F, 6.0F, 0, -2, 0, -2.5F, 4, 5, 5, 28, 24, false, 2, 6),
			h("pelindung_kiri_r", KIRI, 0.5F, 6.0F, 0, -2, 0, -2.5F, 4, 5, 5, 28, 24, true, 2, 6),
			h("rantai_1", BADAN, -3.4F, 1.2F, -3.5F, -1, -0.5F, -0.5F, 2, 1, 1, 48, 24, false, 0, 7),
			h("rantai_2", BADAN, -1.8F, 2.8F, -3.5F, -1, -0.5F, -0.5F, 2, 1, 1, 48, 24, false, 0, 7),
			h("rantai_3", BADAN, -0.2F, 4.4F, -3.5F, -1, -0.5F, -0.5F, 2, 1, 1, 48, 24, false, 0, 7),
			h("rantai_4", BADAN, 1.4F, 6.0F, -3.5F, -1, -0.5F, -0.5F, 2, 1, 1, 48, 24, false, 0, 7),
			h("rumbai_darah", BADAN, 3.0F, 8.6F, -3.6F, -1, 0, -0.5F, 2, 5, 1, 54, 24, false, 0, 7),
			h("kantong_kanan", BADAN, -3.0F, 8.6F, -3.4F, -1, 0, -1, 2, 3, 2, 0, 34, false, 0, 8),
			h("kantong_kiri", BADAN, 3.0F, 8.6F, -3.4F, -1, 0, -1, 2, 3, 2, 0, 34, true, 0, 8),
			h("labu_racun", BADAN, 5.2F, 8.6F, 0.6F, -1.5F, 0, -1.5F, 3, 4, 3, 16, 24, false, 0, 8),
			h("kerudung", BADAN, 0, -0.4F, 3.4F, -4, 0, 0, 8, 5, 3, 8, 34, false, 0, 9));

	private static final ModelLayerLocation[] KAIN_LAYER = new ModelLayerLocation[4];
	private static final ModelLayerLocation[] HIASAN_LAYER = new ModelLayerLocation[4];
	private static final ModelLayerLocation[] LAMBANG_LAYER = new ModelLayerLocation[2];

	static {
		for (int i = 0; i < 4; i++) {
			String sfx = (i % 2 == 0 ? "lebar" : "ramping") + (i < 2 ? "_tipis" : "_tebal");
			KAIN_LAYER[i] = new ModelLayerLocation(Kultivator.id("gabung_jubah_" + sfx), "main");
			HIASAN_LAYER[i] = new ModelLayerLocation(Kultivator.id("gabung_jubah_hiasan_" + sfx), "main");
		}
		LAMBANG_LAYER[0] = new ModelLayerLocation(Kultivator.id("gabung_jubah_lambang_tipis"), "main");
		LAMBANG_LAYER[1] = new ModelLayerLocation(Kultivator.id("gabung_jubah_lambang_tebal"), "main");
	}

	/** Registers the layer definitions (client init, before the entity models bake). */
	public static void daftarLapisan() {
		for (int i = 0; i < 4; i++) {
			boolean ramping = i % 2 == 1;
			float g = i < 2 ? TIPIS : TEBAL;
			ModelLayerRegistry.registerModelLayer(KAIN_LAYER[i], () -> mesh(KAIN_KUBUS, ramping, g));
			ModelLayerRegistry.registerModelLayer(HIASAN_LAYER[i], () -> mesh(HIASAN_KUBUS, ramping, g));
		}
		ModelLayerRegistry.registerModelLayer(LAMBANG_LAYER[0], () -> meshLambang(TIPIS));
		ModelLayerRegistry.registerModelLayer(LAMBANG_LAYER[1], () -> meshLambang(TEBAL));
	}

	private final Jubah[] kain = new Jubah[4];
	private final Hiasan[] hiasan = new Hiasan[4];
	private final Lambang[] lambang = new Lambang[2];

	public JubahSekteLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		for (int i = 0; i < 4; i++) {
			kain[i] = new Jubah(context.bakeLayer(KAIN_LAYER[i]), i % 2 == 1);
			hiasan[i] = new Hiasan(context.bakeLayer(HIASAN_LAYER[i]), i % 2 == 1);
		}
		lambang[0] = new Lambang(context.bakeLayer(LAMBANG_LAYER[0]));
		lambang[1] = new Lambang(context.bakeLayer(LAMBANG_LAYER[1]));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || state.isSpectator || state.distanceToCameraSq > LOD_JUBAH_SQ || EffectQuality.minimal()
				|| ShaderCompat.shadowPass() || FilmDirector.playing()) {
			return;
		}
		UUID uuid = uuidOf(state);
		GabungPayloads.EntriLencana lencana = uuid == null ? null : GabungClient.lencana(uuid);
		if (lencana == null || lencana.sekte().isEmpty()) {
			return;
		}
		boolean zirah = !state.chestEquipment.isEmpty();
		if (lencana.jubah() && (!zirah || saklar(KUNCI_DI_ATAS_ZIRAH))) {
			submitJubah(poseStack, collector, lightCoords, state, lencana, zirah);
		}
		if (state.distanceToCameraSq <= NAMETAG_SQ && saklar(KUNCI_NAMETAG)) {
			submitPangkat(poseStack, collector, lightCoords, state, lencana);
		}
	}

	private void submitJubah(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state,
			GabungPayloads.EntriLencana lencana, boolean zirah) {
		int varian = (state.skin.model() == PlayerModelType.SLIM ? 1 : 0) + (zirah ? 2 : 0);
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		Pangkat pangkat = pangkat(lencana.pangkat());
		int corak = corakOf(lencana.sekte());
		state.setData(CORAK, corak);
		collector.submitModel(kain[varian], state, poseStack, KAIN_TYPE, light, overlay, ARGB.opaque(lencana.warna() & 0xFFFFFF), null,
				state.outlineColor);
		collector.order(1).submitModel(kain[varian], state, poseStack, TEPI_TYPE, light, overlay, ARGB.opaque(pangkat.warna()), null,
				state.outlineColor);
		if (corak >= 0) {
			collector.submitModel(hiasan[varian], state, poseStack, HIASAN_TYPE, light, overlay, -1, null, state.outlineColor);
		}
		if (pangkat.tingkat() >= Pangkat.INTI.tingkat() && state.distanceToCameraSq <= LOD_LAMBANG_SQ) {
			LambangCache.Entry seragam = LambangCache.get("npc:" + lencana.sekte());
			Identifier sulaman = JubahLook.embroidery(seragam);
			if (sulaman != null) {
				collector.order(2).submitModel(lambang[zirah ? 1 : 0], state, poseStack, RenderTypes.entityTranslucent(sulaman), light, overlay, -1,
						null, state.outlineColor);
			}
		}
	}

	/** "[Dalam]" in the trim colour, one line above the vanilla name tag, turned to the camera. */
	private static void submitPangkat(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state,
			GabungPayloads.EntriLencana lencana) {
		Minecraft mc = Minecraft.getInstance();
		Pangkat pangkat = pangkat(lencana.pangkat());
		Component teks = Component.literal("[").append(Component.translatable("kultivator.gabung.tanda.pangkat." + pangkat.ordinal()))
				.append("]");
		FormattedCharSequence baris = teks.getVisualOrderText();
		Font font = mc.font;
		// the layer's pose is the model space of the body (y down, 1.501 above the feet, turned with the body): take the
		// point above the name tag from it, then draw in a fresh camera-facing frame there (the vanilla name-tag frame)
		float atasKaki = state.boundingBoxHeight + 0.82F;
		Vector3f titik = poseStack.last().pose().transformPosition(new Vector3f(0.0F, 1.501F - atasKaki, 0.0F));
		poseStack.pushPose();
		Matrix4f m = poseStack.last().pose();
		m.identity().translate(titik).rotate(mc.gameRenderer.mainCamera().rotation()).scale(0.02F, -0.02F, 0.02F);
		poseStack.last().normal().identity();
		float x = -font.width(baris) / 2.0F;
		int warna = ARGB.opaque(pangkat.warna());
		int latar = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
		collector.submitText(poseStack, x, 0.0F, baris, false, Font.DisplayMode.NORMAL, light, warna, latar, 0);
		poseStack.popPose();
	}

	// ================================================================== lookups

	private static @Nullable UUID uuidOf(AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		Entity entity = mc.level.getEntity(state.id);
		return entity == null ? null : entity.getUUID();
	}

	static Pangkat pangkat(int ordinal) {
		Pangkat[] all = Pangkat.values();
		return all[Mth.clamp(ordinal, 0, all.length - 1)];
	}

	/** The {@link Corak} ordinal of {@code sekte}, or -1 while its class line is unknown. */
	static int corakOf(String sekte) {
		GabungPayloads.EntriKelas kelas = GabungClient.kelas(sekte);
		return kelas == null ? -1 : Corak.byOrdinal(kelas.corak()).ordinal();
	}

	/** A boolean client switch, true when unset (41V-P owns the rows). */
	static boolean saklar(String kunci) {
		JsonElement e = EffectQuality.get(kunci);
		try {
			return e == null || !e.isJsonPrimitive() || e.getAsBoolean();
		} catch (RuntimeException ignored) {
			return true;
		}
	}

	// ================================================================== meshes

	/** An empty player skeleton (pivots only; the robe's own parts are added on top). */
	private static MeshDefinition kerangka(boolean ramping) {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, ramping);
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO).addOrReplaceChild("jacket", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F))
				.addOrReplaceChild("right_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F))
				.addOrReplaceChild("left_sleeve", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F))
				.addOrReplaceChild("right_pants", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F))
				.addOrReplaceChild("left_pants", CubeListBuilder.create(), PartPose.ZERO);
		return mesh;
	}

	static LayerDefinition mesh(List<Kubus> kubus, boolean ramping, float g) {
		MeshDefinition mesh = kerangka(ramping);
		PartDefinition root = mesh.getRoot();
		CubeDeformation def = new CubeDeformation(g);
		for (Kubus c : kubus) {
			if (c.lengan == 1 && ramping || c.lengan == 2 && !ramping) {
				continue;
			}
			// the arm parts of the slim cut keep the wide names so the model finds one set
			String id = c.lengan == 2 ? c.id.substring(0, c.id.length() - 2) : c.id;
			CubeListBuilder b = CubeListBuilder.create().texOffs(c.u, c.v);
			if (c.cermin) {
				b.mirror();
			}
			b.addBox(c.x0, c.y0, c.z0, c.w, c.h, c.d, def);
			root.getChild(c.induk).addOrReplaceChild(id, b, PartPose.offset(c.px, c.py, c.pz));
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	/**
	 * The emblem plate: a flat 8 x 7 face on the outside of the back panel whose back (south) face samples the skin-layout
	 * embroidery sheet at (32, 20), where {@code LambangAtlas} puts the emblem on the back of the body.
	 */
	static LayerDefinition meshLambang(float g) {
		MeshDefinition mesh = kerangka(false);
		PartDefinition body = mesh.getRoot().getChild(BADAN);
		Kubus p = KAIN_KUBUS.getFirst();
		PartDefinition panel = body.addOrReplaceChild("punggung", CubeListBuilder.create(), PartPose.offset(p.px, p.py, p.pz));
		panel.addOrReplaceChild("lambang", CubeListBuilder.create().texOffs(24, 20).addBox(-4.0F, 2.2F, p.d + g + 0.06F, 8.0F, 7.0F, 0.0F),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	// ================================================================== models

	/** How the hanging panels move: straight down while the body leans, a sway, the stride's flare, folded when seated. */
	record Gantung(float x, float flare, float lipat) {

		static Gantung dari(AvatarRenderState state, ModelPart body) {
			float walk = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			float sway = Mth.sin(state.ageInTicks * 0.08F) * 0.03F;
			float seated = Math.max(MeditationPose.strength(state), state.isPassenger ? 1.0F : 0.0F);
			return new Gantung(-body.xRot * 0.9F + sway, walk * 0.32F + (state.isCrouching ? 0.15F : 0.0F), seated);
		}
	}

	/** The cloth cut. */
	static final class Jubah extends PlayerModel {

		private final ModelPart punggung;
		private final ModelPart depanKanan;
		private final ModelPart depanKiri;
		private final ModelPart ekorKanan;
		private final ModelPart ekorKiri;
		private final ModelPart mansetKanan;
		private final ModelPart mansetKiri;
		private final ModelPart lenganKanan;
		private final ModelPart lenganKiri;

		Jubah(ModelPart root, boolean ramping) {
			super(root, ramping);
			this.punggung = body.getChild("punggung");
			this.depanKanan = body.getChild("depan_kanan");
			this.depanKiri = body.getChild("depan_kiri");
			this.ekorKanan = body.getChild("ekor_kanan");
			this.ekorKiri = body.getChild("ekor_kiri");
			this.mansetKanan = rightArm.getChild("manset_kanan");
			this.mansetKiri = leftArm.getChild("manset_kiri");
			this.lenganKanan = rightArm.getChild("lengan_kanan");
			this.lenganKiri = leftArm.getChild("lengan_kiri");
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			Gantung g = Gantung.dari(state, body);
			// the back panel swings out behind the stride, the front panels part a little and trail
			punggung.xRot = g.x() + g.flare();
			depanKanan.xRot = g.x() - g.flare() * 0.8F;
			depanKiri.xRot = g.x() - g.flare() * 0.8F;
			depanKanan.zRot = 0.04F + g.flare() * 0.12F;
			depanKiri.zRot = -0.04F - g.flare() * 0.12F;
			// the split tails below the back panel flutter on their own
			float flutter = Mth.sin(state.ageInTicks * 0.21F) * 0.06F * (0.4F + g.flare());
			ekorKanan.xRot = g.flare() * 0.5F + flutter;
			ekorKiri.xRot = g.flare() * 0.5F - flutter;
			// wide cuffs hang from the forearm: counter the arm's swing a little
			mansetKanan.xRot = -rightArm.xRot * 0.35F;
			mansetKiri.xRot = -leftArm.xRot * 0.35F;
			if (g.lipat() > 0.0F) {
				float k = g.lipat();
				punggung.yScale = Mth.lerp(k, 1.0F, 0.45F);
				depanKanan.yScale = Mth.lerp(k, 1.0F, 0.5F);
				depanKiri.yScale = depanKanan.yScale;
				punggung.xRot = Mth.lerp(k, punggung.xRot, 0.9F);
				depanKanan.xRot = Mth.lerp(k, depanKanan.xRot, -1.1F);
				depanKiri.xRot = depanKanan.xRot;
				ekorKanan.visible = k < 0.5F;
				ekorKiri.visible = k < 0.5F;
			} else {
				punggung.yScale = 1.0F;
				depanKanan.yScale = 1.0F;
				depanKiri.yScale = 1.0F;
				ekorKanan.visible = true;
				ekorKiri.visible = true;
			}
			// Tubuh: the sleeveless cut shows the bracers instead
			Integer corak = state.getDataOrDefault(CORAK, -1);
			boolean tanpaLengan = corak != null && corak == Corak.TUBUH.ordinal();
			lenganKanan.visible = !tanpaLengan;
			lenganKiri.visible = !tanpaLengan;
			mansetKanan.visible = !tanpaLengan;
			mansetKiri.visible = !tanpaLengan;
		}
	}

	/** The style ornaments: only the parts of this frame's style are shown. */
	static final class Hiasan extends PlayerModel {

		private final ModelPart[] bagian;
		private final int[] corak;
		private final ModelPart selendangKanan;
		private final ModelPart selendangKiri;
		private final ModelPart selendangBelakang;
		private final ModelPart sarung;
		private final ModelPart rumbai;

		Hiasan(ModelPart root, boolean ramping) {
			super(root, ramping);
			int n = 0;
			for (Kubus c : HIASAN_KUBUS) {
				if (!(c.lengan == 1 && ramping || c.lengan == 2 && !ramping)) {
					n++;
				}
			}
			bagian = new ModelPart[n];
			corak = new int[n];
			int i = 0;
			for (Kubus c : HIASAN_KUBUS) {
				if (c.lengan == 1 && ramping || c.lengan == 2 && !ramping) {
					continue;
				}
				String id = c.lengan == 2 ? c.id.substring(0, c.id.length() - 2) : c.id;
				ModelPart parent = c.induk.equals(KANAN) ? rightArm : c.induk.equals(KIRI) ? leftArm : body;
				bagian[i] = parent.getChild(id);
				corak[i] = c.corak;
				i++;
			}
			selendangKanan = body.getChild("selendang_kanan");
			selendangKiri = body.getChild("selendang_kiri");
			selendangBelakang = body.getChild("selendang_belakang");
			sarung = body.getChild("sarung_pedang");
			rumbai = body.getChild("rumbai_darah");
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			Integer c = state.getDataOrDefault(CORAK, -1);
			int aktif = c == null ? -1 : c;
			for (int i = 0; i < bagian.length; i++) {
				bagian[i].visible = corak[i] == aktif;
			}
			Gantung g = Gantung.dari(state, body);
			float t = state.ageInTicks;
			// the Unsur sash floats: a slow wave down its tails, lifted by the stride
			selendangBelakang.xRot = 0.35F + g.flare() * 0.6F + Mth.sin(t * 0.07F) * 0.08F;
			selendangKanan.xRot = 0.25F + g.flare() + Mth.sin(t * 0.09F) * 0.12F;
			selendangKiri.xRot = 0.25F + g.flare() + Mth.sin(t * 0.09F + 1.3F) * 0.12F;
			selendangKanan.zRot = 0.18F + Mth.sin(t * 0.05F) * 0.05F;
			selendangKiri.zRot = -0.18F - Mth.sin(t * 0.05F + 0.7F) * 0.05F;
			// the sword hangs back from the hip and swings with the walk
			sarung.xRot = 0.35F + g.x() * 0.6F + Mth.sin(state.walkAnimationPos * 0.6662F) * 0.12F * Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			sarung.zRot = -0.12F;
			body.getChild("gagang_pedang").xRot = sarung.xRot;
			body.getChild("pelindung_pedang").xRot = sarung.xRot;
			body.getChild("gagang_pedang").zRot = sarung.zRot;
			body.getChild("pelindung_pedang").zRot = sarung.zRot;
			rumbai.xRot = g.x() + Mth.sin(t * 0.17F) * 0.07F;
		}
	}

	/** The emblem plate (pose of the back panel). */
	static final class Lambang extends PlayerModel {

		private final ModelPart punggung;

		Lambang(ModelPart root) {
			super(root, false);
			this.punggung = body.getChild("punggung");
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			Gantung g = Gantung.dari(state, body);
			punggung.xRot = g.x() + g.flare();
			punggung.yScale = g.lipat() > 0.0F ? Mth.lerp(g.lipat(), 1.0F, 0.45F) : 1.0F;
			if (g.lipat() > 0.0F) {
				punggung.xRot = Mth.lerp(g.lipat(), punggung.xRot, 0.9F);
			}
		}
	}
}
