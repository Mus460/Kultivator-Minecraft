package dev.kultivator.client.dunia.tempat.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.kosmik.Kosmik3D;
import dev.kultivator.client.epic.kosmik.KosmikTypes;
import dev.kultivator.dunia.tempat.ekonomi.SenjataKota;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weapon racks (DESIGN 38.O.4 pajangan, 38.M.2.1 city weapons; module 38MD), client only: a lacquered rack of dark wood
 * (two posts with gold caps, a base beam with a notched butt rail, a back rail and a red roof board) holding up to seven
 * of the 28 city weapons {@code kultivator:senjata/<form>_<grade>} upright, each standing on the floor rail at its own
 * height so a long qiang and a short iron fan look like they rest on the same rail. The frame is custom geometry over
 * {@code hias.png}; the weapons are their real item models, submitted with {@link ItemDisplayContext#NONE} (the
 * generator's anchor (8, 8, 8) = grip, +y along the blade), so a rack shows exactly what the smith sells.
 *
 * <p><b>Client API.</b> The smithy owner (38EK) calls {@link #pasang} with the racks of a shop when its sync arrives and
 * {@link #hapus} when the shop leaves the client. {@link #uji} is the 38.O.5 test {@code senjata}: four racks, one per
 * grade, with all 28 weapons, for 30 s.
 *
 * <p><b>Budget:</b> racks within 32 blocks, 2 / 6 / 12 racks per frame (Minimal / Normal / Maximal), weapons only within
 * 24 blocks; nothing at tier −1, in a shadow pass or under a Film Kosmik stage. Item stacks are made lazily on first
 * draw, never in a static initialiser.
 */
public final class RakSenjata {

	/**
	 * One rack: floor centre, facing {@code yaw} (degrees, entity convention: the rack's front, where the weapons are
	 * seen from, looks along it), up to seven weapons left to right.
	 */
	public record Rak(double x, double y, double z, float yaw, List<SenjataKota.Jenis> isi) {
		public Rak {
			isi = List.copyOf(isi.size() > SLOT ? isi.subList(0, SLOT) : isi);
		}
	}

	public static final int SLOT = 7;
	private static final int[] BATAS = {2, 6, 12};
	private static final double JARAK = 32.0D;
	private static final double JARAK_SENJATA = 24.0D;
	private static final float SKALA = 0.7F;
	private static final float LEBAR = 44.0F;
	private static final String UJI = "uji";

	/** Lowest model y (px) of each form, from tools/gen_tempat_models.py, so every butt rests on the rail. */
	private static final Map<SenjataKota.Bentuk, Float> DASAR = new EnumMap<>(SenjataKota.Bentuk.class);

	static {
		DASAR.put(SenjataKota.Bentuk.JIAN, -1.6F);
		DASAR.put(SenjataKota.Bentuk.DAO, 4.2F);
		DASAR.put(SenjataKota.Bentuk.QIANG, -7.9F);
		DASAR.put(SenjataKota.Bentuk.KAPAK, 3.2F);
		DASAR.put(SenjataKota.Bentuk.GADA, 3.2F);
		DASAR.put(SenjataKota.Bentuk.KIPAS_BESI, 6.2F);
		DASAR.put(SenjataKota.Bentuk.KEBUT, 3.9F);
	}

	private static final class Grup {
		final List<Rak> rak;
		final int[] cahaya;
		final long[] cahayaTick;
		long sampai = Long.MAX_VALUE;

		Grup(List<Rak> rak) {
			this.rak = rak;
			this.cahaya = new int[rak.size()];
			this.cahayaTick = new long[rak.size()];
			java.util.Arrays.fill(cahayaTick, Long.MIN_VALUE);
		}
	}

	private static final Map<String, Grup> GRUP = new LinkedHashMap<>();
	private static final Map<String, ItemStack> STACK = new HashMap<>();
	private static final Map<String, ItemStackRenderState> STATE = new HashMap<>();
	private static final Quaternionf Q = new Quaternionf();
	private static final Vector3f V = new Vector3f();
	private static HiasQuads kayu;
	private static boolean initialised;
	private static boolean warned;

	/** Once from {@link TempatModelClient#init()}. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(RakSenjata::collect);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> bersihkan());
	}

	// ================================================================ client API

	/** Places (or replaces) the racks of {@code kunci}. */
	public static void pasang(String kunci, List<Rak> rak) {
		GRUP.put(kunci, new Grup(List.copyOf(rak)));
	}

	public static void hapus(String kunci) {
		GRUP.remove(kunci);
	}

	public static void bersihkan() {
		GRUP.clear();
		STATE.clear();
	}

	/** The racks placed (the {@code /hiasan} report). */
	public static int jumlah() {
		int n = 0;
		for (Grup g : GRUP.values()) {
			n += g.rak.size();
		}
		return n;
	}

	/** The 38.O.5 test {@code senjata}: four racks (one per grade, seven forms each) in front of {@code pusat} for 30 s. */
	public static void uji(ClientLevel level, Vec3 pusat, float yawDeg) {
		float yr = yawDeg * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yr);
		double fz = Mth.cos(yr);
		double sx = -fz;
		double sz = fx;
		List<Rak> rak = new ArrayList<>();
		SenjataKota.Mutu[] mutu = SenjataKota.Mutu.values();
		for (int m = 0; m < mutu.length; m++) {
			List<SenjataKota.Jenis> isi = new ArrayList<>();
			for (SenjataKota.Bentuk bentuk : SenjataKota.Bentuk.values()) {
				SenjataKota.Jenis jenis = SenjataKota.jenis(bentuk, mutu[m]);
				if (jenis != null) {
					isi.add(jenis);
				}
			}
			double side = (m - 1.5D) * 3.2D;
			// the racks face the player: their front looks back along -forward
			rak.add(new Rak(pusat.x + fx * 4.5D + sx * side, Math.floor(pusat.y), pusat.z + fz * 4.5D + sz * side, yawDeg + 180.0F, isi));
		}
		Grup g = new Grup(rak);
		g.sampai = level.getGameTime() + 600L;
		GRUP.put(UJI, g);
	}

	public static void hapusUji() {
		GRUP.remove(UJI);
	}

	// ================================================================ frame

	private static void collect(LevelRenderContext context) {
		if (GRUP.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || ShaderCompat.shadowPass() || Kosmik3D.covering()) {
			return;
		}
		long start = System.nanoTime();
		try {
			EpicBudget.beginFrame();
			int tier = EpicBudget.tier();
			if (tier < 0) {
				return;
			}
			tier = Math.min(2, tier);
			if (kayu == null) {
				kayu = new HiasQuads(KosmikTypes.cutout(HiasanDunia.TEKSTUR));
			}
			kayu.reset();
			CameraRenderState camera = context.levelState().cameraRenderState;
			double cx = camera.pos.x;
			double cy = camera.pos.y;
			double cz = camera.pos.z;
			long game = level.getGameTime();
			SubmitNodeCollector collector = context.submitNodeCollector();
			PoseStack stack = context.poseStack();
			int drawn = 0;
			Iterator<Map.Entry<String, Grup>> it = GRUP.entrySet().iterator();
			while (it.hasNext()) {
				Grup g = it.next().getValue();
				if (g.sampai != Long.MAX_VALUE && game > g.sampai) {
					it.remove();
					continue;
				}
				for (int i = 0; i < g.rak.size() && drawn < BATAS[tier]; i++) {
					Rak r = g.rak.get(i);
					double dx = r.x() - cx;
					double dy = r.y() - cy;
					double dz = r.z() - cz;
					double d2 = dx * dx + dy * dy + dz * dz;
					if (d2 > JARAK * JARAK) {
						continue;
					}
					if (game - g.cahayaTick[i] >= 20L) {
						g.cahayaTick[i] = game;
						BlockPos p = BlockPos.containing(r.x(), r.y() + 1.0D, r.z());
						g.cahaya[i] = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, p), level.getBrightness(LightLayer.SKY, p));
					}
					drawn++;
					rangka((float) dx, (float) dy, (float) dz, r.yaw(), g.cahaya[i]);
					if (d2 <= JARAK_SENJATA * JARAK_SENJATA) {
						senjata(minecraft, level, stack, collector, r, (float) dx, (float) dy, (float) dz, g.cahaya[i]);
					}
				}
			}
			kayu.submit(collector, stack);
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Kultivator: weapon racks skipped a frame", exception);
			}
		} finally {
			EpicBudget.addCpu(System.nanoTime() - start);
		}
	}

	/** The frame in model pixels: posts, gold caps, base beam, notched butt rail, back rail, roof board, braces. */
	private static void rangka(float ox, float oy, float oz, float yaw, int light) {
		Q.rotationY(-yaw * Mth.DEG_TO_RAD);
		int w = 0xFFFFFFFF;
		float half = LEBAR / 2.0F;
		box(ox, oy, oz, 0.0F, 1.0F, -0.5F, half + 1.0F, 1.0F, 4.0F, 6, 0, w, light); // base beam
		for (int side = -1; side <= 1; side += 2) {
			box(ox, oy, oz, side * half, 19.0F, -2.0F, 1.2F, 17.0F, 1.2F, 6, 0, w, light); // post
			box(ox, oy, oz, side * half, 37.0F, -2.0F, 1.5F, 1.0F, 1.5F, 3, 0, w, light); // gold cap
			box(ox, oy, oz, side * half, 3.0F, 1.0F, 1.0F, 1.0F, 3.5F, 6, 0, w, light); // foot
			box(ox, oy, oz, side * (half - 2.5F), 33.0F, -2.0F, 1.6F, 0.5F, 0.5F, 3, 0, w, light); // corner brace
		}
		box(ox, oy, oz, 0.0F, 3.0F, 0.0F, half, 1.0F, 2.0F, 6, 0, w, light); // butt rail
		for (int k = 0; k < SLOT; k++) {
			box(ox, oy, oz, slotX(k), 4.1F, 0.0F, 0.4F, 0.2F, 1.6F, 3, 0, w, light); // brass notch plates
		}
		box(ox, oy, oz, 0.0F, 22.0F, -2.6F, half, 0.8F, 0.8F, 6, 0, w, light); // back rail
		box(ox, oy, oz, 0.0F, 22.0F, -1.6F, half, 0.3F, 0.3F, 3, 0, w, light); // gilt edge
		box(ox, oy, oz, 0.0F, 38.5F, -1.5F, half + 3.0F, 0.75F, 3.5F, 0, 0, 0xFFC8B0B0, light); // red roof board
		box(ox, oy, oz, 0.0F, 39.4F, -1.5F, half + 3.5F, 0.2F, 3.8F, 3, 0, w, light); // gilt roof edge
	}

	private static float slotX(int k) {
		return -LEBAR / 2.0F + (k + 0.5F) * LEBAR / SLOT;
	}

	private static void box(float ox, float oy, float oz, float lx, float ly, float lz, float hx, float hy, float hz, int col, int row,
			int argb, int light) {
		V.set(lx / 16.0F, ly / 16.0F, lz / 16.0F);
		Q.transform(V);
		kayu.kotak(ox + V.x, oy + V.y, oz + V.z, hx / 16.0F, hy / 16.0F, hz / 16.0F, Q, col, row, argb, light);
	}

	/** Each weapon upright in its slot, its lowest pixel on the rail, leaning back 6° against the back rail. */
	private static void senjata(Minecraft minecraft, ClientLevel level, PoseStack stack, SubmitNodeCollector collector, Rak r,
			float ox, float oy, float oz, int light) {
		List<SenjataKota.Jenis> isi = r.isi();
		for (int k = 0; k < isi.size(); k++) {
			SenjataKota.Jenis jenis = isi.get(k);
			ItemStackRenderState state = STATE.computeIfAbsent(jenis.id(), id -> new ItemStackRenderState());
			ItemStack item = STACK.computeIfAbsent(jenis.id(), id -> SenjataKota.stack(jenis.bentuk(), jenis.mutu()));
			minecraft.getItemModelResolver().updateForTopItem(state, item, ItemDisplayContext.NONE, level, null, 0);
			if (state.isEmpty()) {
				continue;
			}
			float dasar = DASAR.getOrDefault(jenis.bentuk(), 0.0F);
			float anchorY = 4.3F + (8.0F - dasar) * SKALA;
			stack.pushPose();
			stack.translate(ox, oy, oz);
			stack.rotateDegrees(Axis.YP, -r.yaw());
			stack.translate(slotX(k) / 16.0F, anchorY / 16.0F, -0.6F / 16.0F);
			stack.rotateDegrees(Axis.XP, -6.0F);
			// the models face north (-z); turn them to the viewer on the rack's +z side
			stack.rotateDegrees(Axis.YP, 180.0F);
			stack.scale(SKALA, SKALA, SKALA);
			state.submit(stack, collector, light, OverlayTexture.NO_OVERLAY, 0);
			stack.popPose();
		}
	}

	private RakSenjata() {
	}
}
