package dev.kultivator.client.tempat.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.dunia.tempat.ui.PapanHidup;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/**
 * World text of DESIGN 38.U.4: notice boards, wanted posters, mission slips, road milestones, shop name boards and the
 * names on a Batu Catatan, drawn by the client with the vanilla {@link Font} in the world (the billboard pattern of 36 A),
 * each on a nine-slice panel of the kit ({@code textures/gui/tempat/panel_<gaya>.png}) so the ink stays legible. Only
 * vanilla render types ({@code entityCutout} for panels and faces, the world text pipeline for glyphs): no post chain,
 * Sodium and Iris safe, nothing drawn in the Iris shadow pass.
 *
 * <h2>Budget</h2>
 * At most {@link #maks()} boards carry text at once (12; 6 on Minimal), the nearest ones of the previous frame first,
 * and only within {@link #jarak()} blocks (24; 16 on Minimal). Small body lines only within {@link #jarakRinci()}
 * (12; 8 on Minimal); further away a board shows its titles only. Every caller asks {@link #izin} once per board per
 * frame before drawing; a board refused keeps its plain model (the papers stay, only the writing is skipped).
 *
 * <h2>Spaces</h2>
 * The primitives ({@link #panel}, {@link #baris}, {@link #wajah}, {@link #segel}, {@link #garis}) draw in the caller's
 * current pose, y pointing <em>down</em>, with {@code nz} the z direction toward the viewer (+1 in the vanilla name-tag
 * space after {@code scale(s, -s, s)}, -1 in an entity model space such as the 38MD board). {@link #papan} and
 * {@link #papanHadap} are the ready billboards: a title line in the style's accent colour, the other lines in its ink,
 * centred on a panel sized to the text, either turned toward the camera about the vertical axis or fixed to a facing.
 *
 * <p>Also registers the renderer of {@code kultivator:papan_hidup} ({@link PapanHidupTeksRenderer}, the 38MD board model
 * plus this text). Wired from {@link UiTempatClient#init()}. Client code only.
 */
public final class TeksDunia {

	/** Text distance (blocks), Normal / Maximal. */
	public static final double JARAK = 24.0D;
	/** Boards with text at once, Normal / Maximal. */
	public static final int MAKS = 12;
	/** Body-line distance (blocks), Normal / Maximal. */
	public static final double JARAK_RINCI = 12.0D;

	/** Height of one font line in font pixels (glyphs 8 + 1 leading). */
	public static final float BARIS = 9.0F;

	private static final Map<Object, Double> CALON = new HashMap<>();
	private static final Set<Object> IZIN = new HashSet<>();
	private static final List<Map.Entry<Object, Double>> URUT = new ArrayList<>();
	private static int dipakai;
	private static int baru;

	private static final Map<Palette.Gaya, RenderType> PANEL = new EnumMap<>(Palette.Gaya.class);
	private static final Identifier STEMPEL = Kultivator.id("textures/gui/tempat/stempel.png");
	private static RenderType stempel;
	private static boolean siap;

	/** Where a line sits on its x coordinate. */
	public enum Rata {
		KIRI, TENGAH, KANAN
	}

	/** A seal of {@code stempel.png} (128 × 64). */
	public enum Segel {
		/** Round vermilion seal (guild, Yamen). */
		BULAT(0, 0, 48, 48),
		/** Square seal (palace, bank). */
		PERSEGI(48, 0, 48, 48),
		/** Wax padlock (locked, confiscated). */
		LILIN(96, 0, 32, 32),
		/** Star seal (reward, rank). */
		BINTANG(96, 32, 32, 32);

		final float u0;
		final float v0;
		final float u1;
		final float v1;

		Segel(int x, int y, int w, int h) {
			this.u0 = x / 128.0F;
			this.v0 = y / 64.0F;
			this.u1 = (x + w) / 128.0F;
			this.v1 = (y + h) / 64.0F;
		}
	}

	/** Registers the frame budget, the cleanup and the board renderer. Idempotent. */
	public static void init() {
		if (siap) {
			return;
		}
		siap = true;
		LevelRenderEvents.START_MAIN.register(context -> mulaiFrame());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> bersihkan()));
		// the 38MD board model with the sheet texts of this class (a later register of the same type would win)
		EntityRenderers.register(PapanHidup.TYPE, PapanHidupTeksRenderer::new);
	}

	// ================================================================== budget

	/** Text distance for the current quality (blocks). */
	public static double jarak() {
		return EffectQuality.minimal() ? 16.0D : JARAK;
	}

	/** Body-line distance for the current quality (blocks). */
	public static double jarakRinci() {
		return EffectQuality.minimal() ? 8.0D : JARAK_RINCI;
	}

	/** Boards with text at once for the current quality. */
	public static int maks() {
		return EffectQuality.minimal() ? 6 : MAKS;
	}

	/**
	 * Asks a text slot for board {@code kunci} (a stable key: the entity id, a {@code BlockPos}, a string) at squared
	 * distance {@code jarakSq} from the camera, for this frame. True: draw the text now. Boards ranked among the
	 * {@link #maks()} nearest of the previous frame always get their slot; new boards take the free ones.
	 */
	public static boolean izin(Object kunci, double jarakSq) {
		double r = jarak();
		if (kunci == null || jarakSq > r * r || ShaderCompat.shadowPass()) {
			return false;
		}
		Double ada = CALON.get(kunci);
		if (ada != null && ada <= jarakSq) {
			// asked twice in one frame (a second pass): answer as the first time without a second slot
			return IZIN.contains(kunci) || dipakai < maks();
		}
		CALON.put(kunci, jarakSq);
		int n = maks();
		if (dipakai >= n) {
			return false;
		}
		boolean terpilih = IZIN.contains(kunci);
		if (!terpilih && IZIN.size() + baru >= n) {
			return false;
		}
		if (!terpilih) {
			baru++;
		}
		dipakai++;
		return true;
	}

	/** Whether body lines are drawn at squared distance {@code jarakSq}. */
	public static boolean rinci(double jarakSq) {
		double r = jarakRinci();
		return jarakSq <= r * r;
	}

	/** Start of a level frame: rank last frame's boards, nearest first, and open the slots. */
	private static void mulaiFrame() {
		IZIN.clear();
		int n = maks();
		if (CALON.size() <= n) {
			IZIN.addAll(CALON.keySet());
		} else {
			URUT.clear();
			URUT.addAll(CALON.entrySet());
			URUT.sort(Map.Entry.comparingByValue());
			for (int i = 0; i < n; i++) {
				IZIN.add(URUT.get(i).getKey());
			}
			URUT.clear();
		}
		CALON.clear();
		dipakai = 0;
		baru = 0;
	}

	private static void bersihkan() {
		CALON.clear();
		IZIN.clear();
		URUT.clear();
		dipakai = 0;
		baru = 0;
	}

	// ================================================================== layout helpers

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Width of {@code text} in font pixels. */
	public static int lebar(Component text) {
		return font().width(text);
	}

	/** {@code text} wrapped to {@code lebar} font pixels. */
	public static List<FormattedCharSequence> bungkus(FormattedText text, int lebar) {
		return font().split(text, Math.max(8, lebar));
	}

	/**
	 * {@code text} wrapped to {@code lebar} font pixels and cut to {@code maksBaris} lines; the last kept line ends with
	 * an ellipsis when text was cut.
	 */
	public static List<FormattedCharSequence> bungkus(FormattedText text, int lebar, int maksBaris) {
		List<FormattedCharSequence> lines = bungkus(text, lebar);
		if (maksBaris <= 0) {
			return List.of();
		}
		if (lines.size() <= maksBaris) {
			return lines;
		}
		List<FormattedCharSequence> cut = new ArrayList<>(lines.subList(0, maksBaris));
		FormattedCharSequence elipsis = FormattedCharSequence.forward("…", Style.EMPTY);
		int last = maksBaris - 1;
		FormattedCharSequence line = cut.get(last);
		// trim the last line until the ellipsis fits
		List<FormattedCharSequence> shorter = font().split(FormattedText.of(sebagai(line)), Math.max(8, lebar - font().width(elipsis) - 1));
		FormattedCharSequence keep = shorter.isEmpty() ? FormattedCharSequence.EMPTY : shorter.get(0);
		cut.set(last, FormattedCharSequence.composite(keep, elipsis));
		return cut;
	}

	/** The plain characters of a sequence (styles dropped; only used to re-wrap a cut line). */
	private static String sebagai(FormattedCharSequence seq) {
		StringBuilder b = new StringBuilder();
		seq.accept((index, style, codepoint) -> {
			b.appendCodePoint(codepoint);
			return true;
		});
		return b.toString();
	}

	/** The rotation turning a y-up billboard toward the camera about the vertical axis. */
	public static Quaternionf hadapKamera(CameraRenderState camera) {
		return Mth.rotationAroundAxis(Mth.Y_AXIS, camera.orientation, new Quaternionf());
	}

	// ================================================================== primitives (y down, nz toward the viewer)

	/**
	 * A nine-slice panel of style {@code gaya} from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}) at depth
	 * {@code z}, corners {@code sudut} units wide, tinted {@code warna} (ARGB, -1 none).
	 */
	public static void panel(PoseStack poseStack, SubmitNodeCollector collector, Palette.Gaya gaya, float x0, float y0, float x1, float y1,
							 float z, float nz, float sudut, int light, int warna) {
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		float c = Math.min(sudut, Math.min((x1 - x0) / 2.0F, (y1 - y0) / 2.0F));
		float[] xs = {x0, x0 + c, x1 - c, x1};
		float[] ys = {y0, y0 + c, y1 - c, y1};
		float[] uv = {0.0F, 0.25F, 0.75F, 1.0F};
		RenderType type = PANEL.computeIfAbsent(gaya, g -> RenderTypes.entityCutout(Kultivator.id("textures/gui/tempat/panel_" + g.id + ".png")));
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
			for (int j = 0; j < 3; j++) {
				for (int i = 0; i < 3; i++) {
					quad(pose, buffer, xs[i], ys[j], xs[i + 1], ys[j + 1], z, nz, uv[i], uv[j], uv[i + 1], uv[j + 1], warna, light);
				}
			}
		});
	}

	/** A thin rule (ink line) from {@code x0} to {@code x1} at {@code y}, {@code tebal} thick, drawn as a text background. */
	public static void garis(PoseStack poseStack, SubmitNodeCollector collector, float x0, float x1, float y, float tebal, int warna, int light) {
		collector.submitTextBackground(poseStack, x0, y, x1, y + tebal, warna, Font.DisplayMode.POLYGON_OFFSET, light);
	}

	/**
	 * One text line whose top sits at {@code y}, {@code tinggi} units tall (one {@link #BARIS}), placed on {@code x} by
	 * {@code rata}; {@code nz} as for the panel. The text is pulled toward the viewer by polygon offset.
	 */
	public static void baris(PoseStack poseStack, SubmitNodeCollector collector, FormattedCharSequence seq, float x, float y, float tinggi, Rata rata,
							 float nz, int warna, int light) {
		float s = tinggi / BARIS;
		float w = font().width(seq);
		float left = switch (rata) {
			case KIRI -> 0.0F;
			case TENGAH -> -w / 2.0F;
			case KANAN -> -w;
		};
		poseStack.pushPose();
		poseStack.translate(x, y, 0.0F);
		// font space: x right, y down, viewer toward +z; the caller's viewer side is nz
		poseStack.scale(s, s, nz > 0.0F ? s : -s);
		collector.submitText(poseStack, left, 0.5F, seq, false, Font.DisplayMode.POLYGON_OFFSET, light, warna, 0, 0);
		poseStack.popPose();
	}

	/** {@link #baris} of a component. */
	public static void baris(PoseStack poseStack, SubmitNodeCollector collector, Component text, float x, float y, float tinggi, Rata rata, float nz,
							 int warna, int light) {
		baris(poseStack, collector, text.getVisualOrderText(), x, y, tinggi, rata, nz, warna, light);
	}

	/**
	 * The face (and hat layer) of player {@code nama}, {@code ukuran} square with its top-left at ({@code x}, {@code y}).
	 * A player in the tab list shows their own skin in full colour; anyone else (an NPC, an offline name) the default
	 * skin of that name drawn in sepia ink, like a poster sketch.
	 */
	public static void wajah(PoseStack poseStack, SubmitNodeCollector collector, String nama, float x, float y, float ukuran, float z, float nz,
							 int light) {
		if (nama == null || nama.isEmpty()) {
			return;
		}
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(nama);
		PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(UUID.nameUUIDFromBytes(("OfflinePlayer:" + nama).getBytes(StandardCharsets.UTF_8)));
		int tint = info != null ? -1 : 0xFFC8A87C;
		RenderType type = RenderTypes.entityCutout(skin.body().texturePath());
		float hat = ukuran * 0.06F;
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
			// face 8,8-16,16 and hat 40,8-48,16 of the 64 × 64 skin
			quad(pose, buffer, x, y, x + ukuran, y + ukuran, z, nz, 8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F, tint, light);
			quad(pose, buffer, x - hat, y - hat, x + ukuran + hat, y + ukuran + hat, z + nz * Math.max(0.01F, hat * 0.5F), nz, 40.0F / 64.0F,
					8.0F / 64.0F, 48.0F / 64.0F, 16.0F / 64.0F, tint, light);
		});
	}

	/** A seal of {@code stempel.png}, {@code ukuran} square, its top-left at ({@code x}, {@code y}). */
	public static void segel(PoseStack poseStack, SubmitNodeCollector collector, Segel segel, float x, float y, float ukuran, float z, float nz,
							 int light) {
		if (stempel == null) {
			stempel = RenderTypes.entityCutout(STEMPEL);
		}
		collector.submitCustomGeometry(poseStack, stempel, (pose, buffer) ->
				quad(pose, buffer, x, y, x + ukuran, y + ukuran, z, nz, segel.u0, segel.v0, segel.u1, segel.v1, -1, light));
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float x1, float y1, float z, float nz, float u0, float v0,
							 float u1, float v1, int warna, int light) {
		buffer.addVertex(pose, x0, y0, z).setColor(warna).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, nz);
		buffer.addVertex(pose, x0, y1, z).setColor(warna).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, nz);
		buffer.addVertex(pose, x1, y1, z).setColor(warna).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, nz);
		buffer.addVertex(pose, x1, y0, z).setColor(warna).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0F, 0.0F, nz);
	}

	// ================================================================== ready billboards

	/**
	 * A billboard at ({@code x}, {@code y}, {@code z}) of the current pose (camera-relative world in a
	 * {@code COLLECT_SUBMITS} listener, entity-local in a renderer), turned toward the camera about the vertical axis:
	 * {@code baris.get(0)} the title in the style's accent, the rest in its ink, {@code tinggi} blocks per line, the
	 * panel's centre on the point. The caller asks {@link #izin} first.
	 */
	public static void papan(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, double x, double y, double z,
							 List<Component> baris, Palette.Gaya gaya, float tinggi, int light) {
		papanHadap(poseStack, collector, hadapKamera(camera), x, y, z, baris, gaya, tinggi, light);
	}

	/** {@link #papan} with a fixed facing ({@code arah} turns a y-up board whose front looks toward +z). */
	public static void papanHadap(PoseStack poseStack, SubmitNodeCollector collector, Quaternionfc arah, double x, double y, double z,
								  List<Component> baris, Palette.Gaya gaya, float tinggi, int light) {
		if (baris == null || baris.isEmpty()) {
			return;
		}
		Font font = font();
		int w = 0;
		for (Component line : baris) {
			w = Math.max(w, font.width(line));
		}
		float pad = 5.0F;
		float lineH = BARIS + 1.0F;
		float h = baris.size() * lineH + (baris.size() > 1 ? 2.0F : 0.0F);
		float s = tinggi / lineH;
		float x0 = -w / 2.0F - pad;
		float x1 = w / 2.0F + pad;
		float y0 = -h / 2.0F - pad + 1.0F;
		float y1 = h / 2.0F + pad - 1.0F;
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.rotate(arah);
		poseStack.scale(s, -s, s);
		panel(poseStack, collector, gaya, x0, y0, x1, y1, -0.4F, 1.0F, 6.0F, light, -1);
		float ty = -h / 2.0F;
		for (int i = 0; i < baris.size(); i++) {
			int warna = i == 0 ? gaya.aksen : gaya.teks;
			baris(poseStack, collector, baris.get(i), 0.0F, ty, BARIS, Rata.TENGAH, 1.0F, warna, light);
			ty += lineH;
			if (i == 0 && baris.size() > 1) {
				garis(poseStack, collector, -w / 2.0F, w / 2.0F, ty - 0.5F, 0.6F, (gaya.teksSamar & 0x00FFFFFF) | 0x90000000, light);
				ty += 2.0F;
			}
		}
		poseStack.popPose();
	}

	private TeksDunia() {
	}
}
