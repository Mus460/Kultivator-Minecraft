package dev.kultivator.client.dunia.tempat.model;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.epic.kosmik.Kosmik3D;
import dev.kultivator.client.epic.kosmik.KosmikTypes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The festival and world decorations of DESIGN 38.O.3 (module 38MD), client only: festival street lanterns, sky
 * lanterns, fireworks, the armillary sphere of Panggung Bintang, water wheels, banners and stall awnings, and incense
 * smoke. All of it is camera-relative custom geometry over the 8 × 8 cell atlas {@code textures/dunia/tempat/hias.png}
 * (tools/gen_tempat_textures.py) with vanilla entity render types (cutout bodies, translucent smoke, emissive glow): no
 * entities, no blocks, no post chain, so Sodium and Iris see ordinary entity geometry.
 *
 * <p><b>Client API.</b> The module that knows where decorations stand (38KS capital and its events, 38DS harvest
 * festival, 38TV themed cities, 38W mills on the roads) calls {@link #pasang} with a group key when its sync arrives,
 * {@link #festival} when an event starts or ends and {@link #hapus} when the place leaves the client. Street lanterns
 * stand all year and burn at night; sky lanterns and fireworks fly only while their group's festival is on and it is
 * night. Every position is a pure function of the group's data and the game time, so nothing is ticked and nothing is
 * stored.
 *
 * <p><b>Budget</b> (Minimal / Normal / Maximal, {@link EpicBudget#tier()}; nothing at tier −1): street lanterns
 * 12/40/80, sky lanterns 15/60/150, firework shells 2/6/12 per site every 20 s, one armillary per site, water wheels
 * 1/2/4 within 48 blocks, banners and awnings 24, incense puffs 0/2/4 per burner; a frame never exceeds 700 / 2400 /
 * 5000 quads (×0.6 under a shader pack). Never in a shader pack's shadow pass or while a Film Kosmik stage covers the
 * frame.
 */
public final class HiasanDunia {

	public static final Identifier TEKSTUR = Kultivator.id("textures/dunia/tempat/hias.png");

	/** The decoration kinds; {@code jarak} is the draw distance in blocks, {@code batas} the per-tier count cap. */
	public enum Jenis {
		/** A round festival lantern hung at a {@code jalan_lentera} point (the hook). warna tints the paper. */
		LENTERA_JALAN(96.0F, new int[] {12, 40, 80}),
		/** A launch place of sky lanterns (a plaza point), active while the group's festival is on at night. */
		LENTERA_LANGIT(192.0F, new int[] {15, 60, 150}),
		/** A firework launch place, active like the sky lanterns; batas = shells per 20 s. */
		KEMBANG_API(256.0F, new int[] {2, 6, 12}),
		/** The armillary sphere of Panggung Bintang, standing on the block at the point. */
		ARMILARI(64.0F, new int[] {1, 1, 2}),
		/** A water wheel: the point is the axle centre, yaw the axle's facing, ukuran the diameter in blocks (default 3). */
		KINCIR(48.0F, new int[] {1, 2, 4}),
		/** A town banner on its pole: the point is the pole's foot, ukuran the pole height (default 4), warna the silk. */
		PANJI(80.0F, new int[] {24, 24, 24}),
		/** A stall awning: the point is the ground below the middle of its back edge, ukuran the width (default 3). */
		TENDA(64.0F, new int[] {24, 24, 24}),
		/** Incense smoke over a burner; batas = puffs per burner. */
		ASAP_DUPA(32.0F, new int[] {0, 2, 4});

		final float jarak;
		final int[] batas;

		Jenis(float jarak, int[] batas) {
			this.jarak = jarak;
			this.batas = batas;
		}
	}

	/** One decoration. {@code warna} is an RGB tint (0xFFFFFF = as painted); {@code ukuran} 0 means the kind's default. */
	public static final class Hias {
		public final Jenis jenis;
		public final double x;
		public final double y;
		public final double z;
		public final float yaw;
		public final int warna;
		public final float ukuran;
		final int benih;
		int cahaya = LightCoordsUtil.FULL_SKY;
		long cahayaTick = Long.MIN_VALUE;

		public Hias(Jenis jenis, double x, double y, double z, float yaw, int warna, float ukuran) {
			this.jenis = jenis;
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
			this.warna = warna & 0xFFFFFF;
			this.ukuran = ukuran;
			this.benih = Mth.murmurHash3Mixer(Mth.floor(x) * 73856093 ^ Mth.floor(y) * 19349663 ^ Mth.floor(z) * 83492791 ^ jenis.ordinal());
		}

		public Hias(Jenis jenis, Vec3 at, float yaw) {
			this(jenis, at.x, at.y, at.z, yaw, 0xFFFFFF, 0.0F);
		}
	}

	private static final class Grup {
		final List<Hias> isi;
		boolean festival;
		/** Game time at which a test group goes away; Long.MAX_VALUE for a placed group. */
		long sampai = Long.MAX_VALUE;
		/** A test group shows its night-only kinds at any hour. */
		boolean paksaMalam;

		Grup(List<Hias> isi) {
			this.isi = isi;
		}
	}

	private static final int[] QUADS = {700, 2400, 5000};
	private static final int[] FESTIVAL_WARNA = {0xFF5040, 0xFFD060, 0x60F0B0, 0xC890FF, 0xA0E0FF, 0xFF90C0};
	private static final String UJI = "uji";

	private static final Map<String, Grup> GRUP = new LinkedHashMap<>();

	private static HiasQuads badan;
	private static HiasQuads asap;
	private static HiasQuads cahaya;
	private static boolean warned;
	private static boolean initialised;
	private static int quadsLast;

	// per-frame state (render thread)
	private static float camX;
	private static float camY;
	private static float camZ;
	private static double camXd;
	private static double camYd;
	private static double camZd;
	private static final Vector3f KANAN = new Vector3f();
	private static final Vector3f ATAS = new Vector3f();
	private static final Vector3f TMP = new Vector3f();
	private static final Vector3f P0 = new Vector3f();
	private static final Vector3f P1 = new Vector3f();
	private static final Vector3f P2 = new Vector3f();
	private static final Vector3f P3 = new Vector3f();
	private static final Quaternionf Q = new Quaternionf();
	private static final Quaternionf Q2 = new Quaternionf();
	private static final Quaternionf Q3 = new Quaternionf();
	private static final int[] DIPAKAI = new int[Jenis.values().length];
	private static float t;
	private static boolean malam;
	private static int tier;
	private static int capQuads;

	/** Once from {@link TempatModelClient#init()}. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(HiasanDunia::collect);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> bersihkan()));
	}

	// ================================================================ client API

	/** Places (or replaces) the decorations of group {@code grup}; the festival state of a replaced group is kept. */
	public static void pasang(String grup, List<Hias> isi) {
		Grup old = GRUP.get(grup);
		Grup baru = new Grup(new ArrayList<>(isi));
		if (old != null) {
			baru.festival = old.festival;
		}
		GRUP.put(grup, baru);
	}

	/** Starts or ends the festival of group {@code grup} (sky lanterns and fireworks fly at night while it is on). */
	public static void festival(String grup, boolean nyala) {
		Grup g = GRUP.get(grup);
		if (g != null) {
			g.festival = nyala;
		}
	}

	/** Removes group {@code grup}. */
	public static void hapus(String grup) {
		GRUP.remove(grup);
	}

	/** Forgets everything (disconnect). */
	public static void bersihkan() {
		GRUP.clear();
	}

	/** The number of placed decorations (the {@code /hiasan} report). */
	public static int jumlah() {
		int n = 0;
		for (Grup g : GRUP.values()) {
			n += g.isi.size();
		}
		return n;
	}

	public static int quadsLastFrame() {
		return quadsLast;
	}

	/**
	 * The 38.O.5 test {@code festival}: every decoration of 38.O.3 laid out in front of {@code pusat} (looking along
	 * {@code yawDeg}) for 20 s, festival on and night-only kinds shown at any hour.
	 */
	public static void uji(ClientLevel level, Vec3 pusat, float yawDeg) {
		float yr = yawDeg * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yr);
		double fz = Mth.cos(yr);
		double sx = -fz;
		double sz = fx;
		List<Hias> isi = new ArrayList<>();
		double gy = Math.floor(pusat.y);
		// a lantern street: two rows of hooks on either side, 3 blocks up
		for (int i = 0; i < 6; i++) {
			double along = 4.0D + i * 2.0D;
			for (int side = -1; side <= 1; side += 2) {
				isi.add(new Hias(Jenis.LENTERA_JALAN, pusat.x + fx * along + sx * side * 3.0D, gy + 3.4D, pusat.z + fz * along + sz * side * 3.0D,
						yawDeg, i % 3 == 2 ? 0xFFE0A0 : 0xFFFFFF, 0.0F));
			}
		}
		isi.add(new Hias(Jenis.PANJI, pusat.x + fx * 5.0D + sx * 5.0D, gy, pusat.z + fz * 5.0D + sz * 5.0D, yawDeg + 90.0F, 0xC83030, 0.0F));
		isi.add(new Hias(Jenis.PANJI, pusat.x + fx * 5.0D - sx * 5.0D, gy, pusat.z + fz * 5.0D - sz * 5.0D, yawDeg - 90.0F, 0x3A5A8C, 0.0F));
		isi.add(new Hias(Jenis.TENDA, pusat.x + fx * 9.0D + sx * 6.5D, gy, pusat.z + fz * 9.0D + sz * 6.5D, yawDeg + 90.0F, 0xFFFFFF, 3.0F));
		isi.add(new Hias(Jenis.ASAP_DUPA, pusat.x + fx * 7.0D, gy + 1.0D, pusat.z + fz * 7.0D, 0.0F, 0xFFFFFF, 0.0F));
		isi.add(new Hias(Jenis.ASAP_DUPA, pusat.x + fx * 7.0D + sx * 0.8D, gy + 1.0D, pusat.z + fz * 7.0D + sz * 0.8D, 0.0F, 0xFFFFFF, 0.0F));
		isi.add(new Hias(Jenis.ARMILARI, pusat.x + fx * 12.0D - sx * 6.0D, gy, pusat.z + fz * 12.0D - sz * 6.0D, yawDeg, 0xFFFFFF, 0.0F));
		isi.add(new Hias(Jenis.KINCIR, pusat.x + fx * 12.0D + sx * 7.0D, gy + 1.8D, pusat.z + fz * 12.0D + sz * 7.0D, yawDeg, 0xFFFFFF, 3.0F));
		isi.add(new Hias(Jenis.LENTERA_LANGIT, pusat.x + fx * 10.0D, gy, pusat.z + fz * 10.0D, 0.0F, 0xFFFFFF, 0.0F));
		isi.add(new Hias(Jenis.KEMBANG_API, pusat.x + fx * 28.0D, gy, pusat.z + fz * 28.0D, 0.0F, 0xFFFFFF, 0.0F));
		Grup g = new Grup(isi);
		g.festival = true;
		g.paksaMalam = true;
		g.sampai = level.getGameTime() + 400L;
		GRUP.put(UJI, g);
	}

	/** Ends the test early. */
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
			tier = EpicBudget.tier();
			if (tier < 0) {
				return;
			}
			tier = Math.min(2, tier);
			if (badan == null) {
				badan = new HiasQuads(KosmikTypes.cutout(TEKSTUR));
				asap = new HiasQuads(RenderTypes.entityTranslucent(TEKSTUR));
				cahaya = new HiasQuads(KosmikTypes.glow(TEKSTUR));
			}
			badan.reset();
			asap.reset();
			cahaya.reset();
			CameraRenderState camera = context.levelState().cameraRenderState;
			camXd = camera.pos.x;
			camYd = camera.pos.y;
			camZd = camera.pos.z;
			camX = (float) camXd;
			camY = (float) camYd;
			camZ = (float) camZd;
			camera.orientation.transform(1.0F, 0.0F, 0.0F, KANAN);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, ATAS);
			float pt = context.levelState().worldPartialTicks;
			long game = level.getGameTime();
			t = (float) (game % 2_400_000L) + pt;
			long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
			malam = day >= 12500L && day <= 23500L;
			capQuads = Math.round(QUADS[tier] * (ShaderCompat.packOn() ? 0.6F : 1.0F));
			java.util.Arrays.fill(DIPAKAI, 0);
			Iterator<Map.Entry<String, Grup>> it = GRUP.entrySet().iterator();
			while (it.hasNext()) {
				Grup g = it.next().getValue();
				if (g.sampai != Long.MAX_VALUE && game > g.sampai) {
					it.remove();
					continue;
				}
				for (Hias h : g.isi) {
					if (penuh()) {
						break;
					}
					gambar(level, g, h, game);
				}
			}
			var collector = context.submitNodeCollector();
			var stack = context.poseStack();
			badan.submit(collector, stack);
			asap.submit(collector, stack);
			cahaya.submit(collector, stack);
			quadsLast = badan.quads() + asap.quads() + cahaya.quads();
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Kultivator: town decorations skipped a frame", exception);
			}
		} finally {
			EpicBudget.addCpu(System.nanoTime() - start);
		}
	}

	private static boolean penuh() {
		return badan.quads() + asap.quads() + cahaya.quads() >= capQuads;
	}

	private static void gambar(ClientLevel level, Grup g, Hias h, long game) {
		Jenis j = h.jenis;
		double dx = h.x - camXd;
		double dy = h.y - camYd;
		double dz = h.z - camZd;
		double d2 = dx * dx + dy * dy + dz * dz;
		// sky lanterns and fireworks are seen from far off and above, the rest only near
		if (d2 > (double) j.jarak * j.jarak) {
			return;
		}
		if (DIPAKAI[j.ordinal()] >= j.batas[tier] && j != Jenis.ASAP_DUPA) {
			return;
		}
		if (j == Jenis.ASAP_DUPA && j.batas[tier] == 0) {
			return;
		}
		if (game - h.cahayaTick >= 20L) {
			h.cahayaTick = game;
			BlockPos p = BlockPos.containing(h.x, h.y, h.z);
			h.cahaya = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, p), level.getBrightness(LightLayer.SKY, p));
		}
		float ox = (float) dx;
		float oy = (float) dy;
		float oz = (float) dz;
		boolean malamIni = malam || g.paksaMalam;
		switch (j) {
			case LENTERA_JALAN -> {
				DIPAKAI[j.ordinal()]++;
				lenteraJalan(h, ox, oy, oz, malamIni || g.festival && malam);
			}
			case LENTERA_LANGIT -> {
				if (g.festival && malamIni) {
					lenteraLangit(h, ox, oy, oz, game);
				}
			}
			case KEMBANG_API -> {
				if (g.festival && malamIni) {
					kembangApi(h, ox, oy, oz);
				}
			}
			case ARMILARI -> {
				DIPAKAI[j.ordinal()]++;
				armilari(h, ox, oy, oz, malamIni);
			}
			case KINCIR -> {
				DIPAKAI[j.ordinal()]++;
				kincir(h, ox, oy, oz);
			}
			case PANJI -> {
				DIPAKAI[j.ordinal()]++;
				panji(h, ox, oy, oz);
			}
			case TENDA -> {
				DIPAKAI[j.ordinal()]++;
				tenda(h, ox, oy, oz);
			}
			case ASAP_DUPA -> asapDupa(h, ox, oy, oz);
		}
	}

	// ================================================================ helpers

	/** A cuboid in a decoration's local frame: centre (lx, ly, lz) and half sizes in model pixels, turned by {@code q}. */
	private static void kotak(HiasQuads sink, float ox, float oy, float oz, Quaternionf q, float lx, float ly, float lz,
			float hx, float hy, float hz, int col, int row, int argb, int light) {
		TMP.set(lx / 16.0F, ly / 16.0F, lz / 16.0F);
		q.transform(TMP);
		sink.kotak(ox + TMP.x, oy + TMP.y, oz + TMP.z, hx / 16.0F, hy / 16.0F, hz / 16.0F, q, col, row, argb, light);
	}

	/** A point of the local frame (model pixels) into {@code out}, camera-relative. */
	private static Vector3f titik(Vector3f out, float ox, float oy, float oz, Quaternionf q, float lx, float ly, float lz) {
		out.set(lx / 16.0F, ly / 16.0F, lz / 16.0F);
		q.transform(out);
		return out.add(ox, oy, oz);
	}

	private static float hash(int seed, int a, int b) {
		int h = Mth.murmurHash3Mixer(seed * 31 + a * 0x9E3779B1 + b * 0x85EBCA77);
		return (h >>> 8) / 16777216.0F;
	}

	private static float halus(float x) {
		float c = Mth.clamp(x, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	private static int argb(float alpha, int rgb) {
		return Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
	}

	private static int skala(int rgb, float f) {
		int r = Mth.clamp((int) ((rgb >> 16 & 255) * f), 0, 255);
		int gr = Mth.clamp((int) ((rgb >> 8 & 255) * f), 0, 255);
		int b = Mth.clamp((int) ((rgb & 255) * f), 0, 255);
		return r << 16 | gr << 8 | b;
	}

	private static int penuhWarna(int rgb) {
		return 0xFF000000 | rgb;
	}

	// ================================================================ the decorations

	/** Festival street lantern: cord, round paper body 8×7×8 with a wider waist, gold caps, crimson tassel; ±4° wind. */
	private static void lenteraJalan(Hias h, float ox, float oy, float oz, boolean nyala) {
		float sway = 4.0F * Mth.sin(t * Mth.TWO_PI / 90.0F + h.benih * 0.001F) + 1.5F * Mth.sin(t * Mth.TWO_PI / 37.0F + h.benih * 0.0007F);
		float sway2 = 2.0F * Mth.sin(t * Mth.TWO_PI / 71.0F + h.benih * 0.0013F);
		Q.rotationY(-h.yaw * Mth.DEG_TO_RAD).rotateZ(sway * Mth.DEG_TO_RAD).rotateX(sway2 * Mth.DEG_TO_RAD);
		int light = nyala ? LightCoordsUtil.FULL_BRIGHT : h.cahaya;
		int paper = penuhWarna(nyala ? h.warna : skala(h.warna, 0.85F));
		kotak(badan, ox, oy, oz, Q, 0.0F, -2.5F, 0.0F, 0.25F, 2.5F, 0.25F, 6, 0, 0xFF000000 | 0x9A7A5A, h.cahaya); // cord
		kotak(badan, ox, oy, oz, Q, 0.0F, -5.5F, 0.0F, 3.0F, 0.5F, 3.0F, 3, 0, 0xFFFFFFFF, light); // top cap
		kotak(badan, ox, oy, oz, Q, 0.0F, -9.5F, 0.0F, 4.0F, 3.5F, 4.0F, 0, 0, paper, light); // body
		kotak(badan, ox, oy, oz, Q, 0.0F, -9.5F, 0.0F, 4.6F, 2.2F, 4.6F, 0, 0, paper, light); // waist
		kotak(badan, ox, oy, oz, Q, 0.0F, -13.5F, 0.0F, 3.0F, 0.5F, 3.0F, 3, 0, 0xFFFFFFFF, light); // bottom cap
		kotak(badan, ox, oy, oz, Q, 0.0F, -16.0F, 0.0F, 0.6F, 2.0F, 0.6F, 3, 1, 0xFFFFFFFF, h.cahaya); // tassel
		if (nyala && tier >= 1) {
			titik(P0, ox, oy, oz, Q, 0.0F, -9.5F, 0.0F);
			float flicker = 0.85F + 0.15F * Mth.sin(t * 0.45F + h.benih);
			cahaya.billboard(P0.x, P0.y, P0.z, 0.75F, KANAN, ATAS, 7, 1, argb(0.32F * flicker, 0xFFB060), LightCoordsUtil.FULL_BRIGHT);
		}
	}

	/**
	 * Sky lanterns from a launch place: paper box 6×8×6 (four walls and a top, open below) with a flame 2×3 inside; each
	 * rises 0.03 block/tick after a slow start, sways ±6° (period 70) down a daily wind, dims after 600 ticks and is gone
	 * by 900; a lantern is a pure function of the place, its index and the time.
	 */
	private static void lenteraLangit(Hias h, float ox, float oy, float oz, long game) {
		int j = Jenis.LENTERA_LANGIT.ordinal();
		int want = Jenis.LENTERA_LANGIT.batas[tier] - DIPAKAI[j];
		if (want <= 0) {
			return;
		}
		float life = 900.0F;
		long day = Math.floorDiv(game, 24000L);
		float wind = hash(h.benih, (int) day, 7) * Mth.TWO_PI;
		float wx = Mth.cos(wind);
		float wz = Mth.sin(wind);
		for (int i = 0; i < want; i++) {
			if (penuh()) {
				return;
			}
			float age = t + hash(h.benih, i, 99) * life;
			int cycle = Mth.floor(age / life);
			float a = age - cycle * life;
			float ang = hash(h.benih, i, cycle * 3 + 1) * Mth.TWO_PI;
			float rad = 2.0F + hash(h.benih, i, cycle * 3 + 2) * 9.0F;
			float rise = 0.03F * a * halus(a / 60.0F) + 1.2F;
			float lx = Mth.cos(ang) * rad + wx * a * 0.012F + Mth.sin(a * 0.05F + i) * 0.4F;
			float lz = Mth.sin(ang) * rad + wz * a * 0.012F + Mth.cos(a * 0.04F + i) * 0.4F;
			float px = ox + lx;
			float py = oy + rise;
			float pz = oz + lz;
			float d2 = px * px + py * py + pz * pz;
			if (d2 > 160.0F * 160.0F) {
				continue;
			}
			float fade = halus(a / 20.0F) * (1.0F - halus((a - 600.0F) / 300.0F));
			if (fade < 0.05F) {
				continue;
			}
			DIPAKAI[j]++;
			float flicker = 0.85F + 0.15F * Mth.sin(t * 0.5F + i * 2.1F);
			float sway = 6.0F * Mth.sin(a * Mth.TWO_PI / 70.0F + i);
			Q.rotationY(ang).rotateZ(sway * Mth.DEG_TO_RAD);
			int paper = penuhWarna(skala(0xFFFFFF, 0.35F + 0.65F * fade * flicker));
			int full = LightCoordsUtil.FULL_BRIGHT;
			if (d2 < 48.0F * 48.0F) {
				// near: the real paper box
				kotak(badan, px, py, pz, Q, 0.0F, 4.0F, -2.75F, 3.0F, 4.0F, 0.25F, 1, 0, paper, full);
				kotak(badan, px, py, pz, Q, 0.0F, 4.0F, 2.75F, 3.0F, 4.0F, 0.25F, 1, 0, paper, full);
				kotak(badan, px, py, pz, Q, -2.75F, 4.0F, 0.0F, 0.25F, 4.0F, 2.5F, 1, 0, paper, full);
				kotak(badan, px, py, pz, Q, 2.75F, 4.0F, 0.0F, 0.25F, 4.0F, 2.5F, 1, 0, paper, full);
				kotak(badan, px, py, pz, Q, 0.0F, 8.25F, 0.0F, 3.0F, 0.25F, 3.0F, 1, 0, paper, full);
				kotak(badan, px, py, pz, Q, 0.0F, 0.25F, 0.0F, 3.0F, 0.25F, 0.25F, 2, 0, 0xFFFFFFFF, h.cahaya); // bamboo cross
				titik(P0, px, py, pz, Q, 0.0F, 1.5F, 0.0F);
				cahaya.billboard(P0.x, P0.y, P0.z, 0.11F, KANAN, ATAS, 7, 0, argb(fade, 0xFFFFFF), full);
				cahaya.billboard(P0.x, P0.y + 0.15F, P0.z, 0.7F, KANAN, ATAS, 7, 1, argb(0.28F * fade * flicker, 0xFFB060), full);
			} else {
				// far: a warm star (the paper is a few pixels across)
				cahaya.billboard(px, py + 0.25F, pz, 0.35F, KANAN, ATAS, 1, 0, argb(fade, skala(0xFFFFFF, flicker)), full);
				cahaya.billboard(px, py + 0.25F, pz, 1.2F, KANAN, ATAS, 7, 1, argb(0.25F * fade * flicker, 0xFFB060), full);
			}
		}
	}

	/**
	 * Fireworks: each 20 s window a place launches {@code batas} shells at hashed moments; a shell climbs 20 ticks with a
	 * spark trail, then bursts over 24 ticks through the eight frames of row 2, in a festival colour.
	 */
	private static void kembangApi(Hias h, float ox, float oy, float oz) {
		int shells = Jenis.KEMBANG_API.batas[tier];
		int window = Mth.floor(t / 400.0F);
		float inWindow = t - window * 400.0F;
		for (int k = 0; k < shells; k++) {
			if (penuh()) {
				return;
			}
			float launch = hash(h.benih, window, k) * 356.0F;
			float age = inWindow - launch;
			if (age < 0.0F || age >= 44.0F) {
				continue;
			}
			float sx = (hash(h.benih, window, k + 50) - 0.5F) * 14.0F;
			float sz = (hash(h.benih, window, k + 90) - 0.5F) * 14.0F;
			float peak = 22.0F + hash(h.benih, window, k + 130) * 10.0F;
			int warna = FESTIVAL_WARNA[(int) (hash(h.benih, window, k + 170) * FESTIVAL_WARNA.length) % FESTIVAL_WARNA.length];
			int full = LightCoordsUtil.FULL_BRIGHT;
			if (age < 20.0F) {
				float f = age / 20.0F;
				float y = peak * (1.0F - (1.0F - f) * (1.0F - f));
				float px = ox + sx * f;
				float pz = oz + sz * f;
				cahaya.billboard(px, oy + y, pz, 0.22F, KANAN, ATAS, 7, 1, argb(1.0F, 0xFFF0C0), full);
				for (int s = 1; s <= 3; s++) {
					float back = Math.max(0.0F, f - s * 0.05F);
					float yb = peak * (1.0F - (1.0F - back) * (1.0F - back));
					cahaya.billboard(ox + sx * back, oy + yb, oz + sz * back, 0.14F - s * 0.03F, KANAN, ATAS, 7, 1,
							argb(0.6F - s * 0.15F, 0xFFC080), full);
				}
			} else {
				float f = (age - 20.0F) / 24.0F;
				int frame = Math.min(7, (int) (f * 8.0F));
				float size = 3.0F + hash(h.benih, window, k + 210) * 2.5F;
				float px = ox + sx;
				float pz = oz + sz;
				float py = oy + peak - f * 1.5F;
				cahaya.billboard(px, py, pz, size, KANAN, ATAS, frame, 2, argb(1.0F - f * 0.25F, warna), full);
				if (tier >= 2) {
					// a second, smaller shell of another colour inside
					int inner = FESTIVAL_WARNA[(k + 3) % FESTIVAL_WARNA.length];
					cahaya.billboard(px, py, pz, size * 0.55F, KANAN, ATAS, Math.max(0, frame - 1), 2, argb(0.9F - f * 0.4F, inner), full);
				}
				if (f < 0.3F) {
					cahaya.billboard(px, py, pz, size * 0.6F, KANAN, ATAS, 7, 1, argb(0.7F * (1.0F - f / 0.3F), warna), full);
				}
			}
		}
	}

	/**
	 * The armillary sphere of Panggung Bintang: bronze base 12×2×12, column 4×14×4, gold cup, and three nested rings of
	 * twelve segments (radius 20, 17.5 and 15 px, centre 36 px up) tumbling 0.6, −0.4 and 0.25°/tick about different
	 * axes round a jade star orb 6³ that glows at night.
	 */
	private static void armilari(Hias h, float ox, float oy, float oz, boolean malamIni) {
		Q.rotationY(-h.yaw * Mth.DEG_TO_RAD);
		int light = h.cahaya;
		int white = 0xFFFFFFFF;
		kotak(badan, ox, oy, oz, Q, 0.0F, 1.0F, 0.0F, 6.0F, 1.0F, 6.0F, 4, 0, white, light);
		kotak(badan, ox, oy, oz, Q, 0.0F, 2.5F, 0.0F, 4.5F, 0.5F, 4.5F, 3, 0, white, light);
		kotak(badan, ox, oy, oz, Q, 0.0F, 9.0F, 0.0F, 2.0F, 7.0F, 2.0F, 4, 0, white, light);
		kotak(badan, ox, oy, oz, Q, 0.0F, 16.5F, 0.0F, 4.0F, 0.5F, 4.0F, 3, 0, white, light);
		// a fixed meridian half-ring (seven segments, radius 22.5) cradles the spheres from below
		titik(P1, ox, oy, oz, Q, 0.0F, 36.0F, 0.0F);
		float cx = P1.x;
		float cy = P1.y;
		float cz = P1.z;
		float mr = 22.5F;
		float mseg = Mth.TWO_PI * mr / 14.0F * 0.5F + 0.2F;
		for (int k = 0; k <= 6; k++) {
			float a = Mth.PI + k * Mth.PI / 6.0F;
			TMP.set(Mth.cos(a) * mr / 16.0F, Mth.sin(a) * mr / 16.0F, 0.0F);
			Q.transform(TMP);
			Q3.set(Q).rotateZ(a + Mth.HALF_PI);
			badan.kotak(cx + TMP.x, cy + TMP.y, cz + TMP.z, mseg / 16.0F, 0.8F / 16.0F, 1.0F / 16.0F, Q3, 4, 0, white, light);
		}
		float[] radius = {20.0F, 17.5F, 15.0F};
		int[][] cells = {{4, 0}, {3, 0}, {3, 3}};
		for (int ring = 0; ring < 3; ring++) {
			float deg;
			switch (ring) {
				case 0 -> {
					deg = t * 0.6F;
					Q2.set(Q).rotateY(deg * Mth.DEG_TO_RAD);
				}
				case 1 -> {
					deg = t * -0.4F;
					Q2.set(Q).rotateX(deg * Mth.DEG_TO_RAD).rotateZ(23.5F * Mth.DEG_TO_RAD);
				}
				default -> {
					deg = t * 0.25F;
					Q2.set(Q).rotateZ(deg * Mth.DEG_TO_RAD).rotateY(Mth.HALF_PI);
				}
			}
			float r = radius[ring];
			float seg = Mth.TWO_PI * r / 12.0F * 0.5F + 0.2F;
			for (int k = 0; k < 12; k++) {
				float a = k * Mth.TWO_PI / 12.0F;
				TMP.set(Mth.cos(a) * r / 16.0F, Mth.sin(a) * r / 16.0F, 0.0F);
				Q2.transform(TMP);
				Q3.set(Q2).rotateZ(a + Mth.HALF_PI);
				badan.kotak(cx + TMP.x, cy + TMP.y, cz + TMP.z, seg / 16.0F, 0.5F / 16.0F, 0.6F / 16.0F, Q3, cells[ring][0], cells[ring][1], white,
						malamIni && ring == 1 ? LightCoordsUtil.addSmoothBlockEmission(light, 0.4F) : light);
			}
		}
		badan.kotak(cx, cy, cz, 3.0F / 16.0F, 3.0F / 16.0F, 3.0F / 16.0F, Q2.set(Q).rotateY(t * 0.02F), 0, 3, white,
				malamIni ? LightCoordsUtil.FULL_BRIGHT : light);
		if (malamIni && tier >= 1) {
			float pulse = 0.75F + 0.25F * Mth.sin(t * 0.08F);
			cahaya.billboard(cx, cy, cz, 0.9F, KANAN, ATAS, 7, 1, argb(0.35F * pulse, 0x90F0D0), LightCoordsUtil.FULL_BRIGHT);
		}
	}

	/**
	 * A water wheel: six paddles 6×24×2 px through the hub (twelve blades) at 0-150° in steps of 30, an iron axle, a
	 * dark hub and two rims of twelve segments, scaled to {@code ukuran} blocks across, turning 6°/tick; at Maximal water
	 * sprays off the lowest blades.
	 */
	private static void kincir(Hias h, float ox, float oy, float oz) {
		float d = h.ukuran > 0.0F ? h.ukuran : 3.0F;
		float s = d / 1.5F; // the paddles are 24 px (1.5 blocks) long at scale 1
		float angle = t * 6.0F * Mth.DEG_TO_RAD;
		int light = h.cahaya;
		int white = 0xFFFFFFFF;
		Q.rotationY(-h.yaw * Mth.DEG_TO_RAD).rotateX(angle);
		for (int k = 0; k < 6; k++) {
			Q2.set(Q).rotateX(k * 30.0F * Mth.DEG_TO_RAD);
			badan.kotak(ox, oy, oz, 3.0F * s / 16.0F, 12.0F * s / 16.0F, 1.0F * s / 16.0F, Q2, 5, 0, white, light);
		}
		kotak(badan, ox, oy, oz, Q, 0.0F, 0.0F, 0.0F, 3.5F * s, 3.0F * s, 3.0F * s, 6, 0, white, light);
		kotak(badan, ox, oy, oz, Q, 0.0F, 0.0F, 0.0F, 8.0F * s, 1.0F * s, 1.0F * s, 6, 0, 0xFF8890A0, light);
		float r = 9.5F * s;
		float seg = Mth.TWO_PI * r / 12.0F * 0.5F + 0.15F * s;
		for (int side = -1; side <= 1; side += 2) {
			for (int k = 0; k < 12; k++) {
				float a = k * Mth.TWO_PI / 12.0F;
				Q2.set(Q).rotateX(a);
				TMP.set(side * 3.2F * s / 16.0F, r / 16.0F, 0.0F);
				Q2.transform(TMP);
				badan.kotak(ox + TMP.x, oy + TMP.y, oz + TMP.z, 0.5F * s / 16.0F, 0.6F * s / 16.0F, seg / 16.0F, Q2, 6, 0, white, light);
			}
		}
		if (tier >= 2) {
			for (int i = 0; i < 3; i++) {
				float ph = (t * 0.07F + i * 0.33F) % 1.0F;
				TMP.set(0.0F, -d * 0.45F + ph * 0.6F, -d * 0.2F - ph * 0.5F);
				Q2.rotationY(-h.yaw * Mth.DEG_TO_RAD);
				Q2.transform(TMP);
				asap.billboard(ox + TMP.x, oy + TMP.y, oz + TMP.z, 0.35F + ph * 0.3F, KANAN, ATAS, 1, 3, argb(0.7F * (1.0F - ph), 0xFFFFFF), light);
			}
		}
	}

	/**
	 * A town banner: dark pole (ukuran blocks, default 4) with a gold finial, a crossbar, two tassels and a silk banner of
	 * eight strips waving at 1.4 Hz, the wave growing toward the free end; tinted by {@code warna}.
	 */
	private static void panji(Hias h, float ox, float oy, float oz) {
		float tinggi = h.ukuran > 0.0F ? h.ukuran : 4.0F;
		float px = tinggi * 16.0F;
		int light = h.cahaya;
		int white = 0xFFFFFFFF;
		Q.rotationY(-h.yaw * Mth.DEG_TO_RAD);
		kotak(badan, ox, oy, oz, Q, 0.0F, px / 2.0F, 0.0F, 0.8F, px / 2.0F, 0.8F, 6, 0, white, light);
		kotak(badan, ox, oy, oz, Q, 0.0F, px + 1.5F, 0.0F, 1.0F, 1.5F, 1.0F, 3, 0, white, light);
		kotak(badan, ox, oy, oz, Q, 6.0F, px - 1.5F, 0.0F, 6.5F, 0.5F, 0.5F, 6, 0, white, light);
		kotak(badan, ox, oy, oz, Q, 12.5F, px - 3.5F, 0.0F, 0.5F, 2.0F, 0.5F, 3, 1, white, light);
		float x0 = 1.2F;
		float x1 = 12.0F;
		float top = px - 2.0F;
		float len = tinggi * 0.55F * 16.0F;
		int tint = penuhWarna(h.warna);
		float omega = Mth.TWO_PI * 1.4F / 20.0F;
		float phase = h.benih * 0.001F;
		for (int k = 0; k < 8; k++) {
			float v0 = k / 8.0F;
			float v1 = (k + 1) / 8.0F;
			float y0 = top - len * v0;
			float y1 = top - len * v1;
			float w0 = 1.6F * v0 * Mth.sin(t * omega - v0 * 3.2F + phase);
			float w1 = 1.6F * v1 * Mth.sin(t * omega - v1 * 3.2F + phase);
			float w0b = w0 + 0.6F * v0 * Mth.sin(t * omega * 1.3F + 1.1F + phase);
			float w1b = w1 + 0.6F * v1 * Mth.sin(t * omega * 1.3F + 1.1F + phase);
			titik(P0, ox, oy, oz, Q, x0, y0, w0);
			titik(P1, ox, oy, oz, Q, x0, y1, w1);
			titik(P2, ox, oy, oz, Q, x1, y1, w1b);
			titik(P3, ox, oy, oz, Q, x1, y0, w0b);
			badan.quadSub(P0, P1, P2, P3, 0, 1, 0.0F, v0, 1.0F, v1, tint, light);
		}
	}

	/**
	 * A stall awning: striped cloth ukuran blocks wide (default 3) sloping from 2.6 blocks at the wall to 2.2 at the front,
	 * eight strips deep with a breeze ripple, a fringed valance and two front poles.
	 */
	private static void tenda(Hias h, float ox, float oy, float oz) {
		float lebar = h.ukuran > 0.0F ? h.ukuran : 3.0F;
		float half = lebar * 8.0F;
		float depth = 24.0F;
		float back = 2.6F * 16.0F;
		float front = 2.2F * 16.0F;
		int light = h.cahaya;
		int tint = penuhWarna(h.warna);
		int white = 0xFFFFFFFF;
		Q.rotationY(-h.yaw * Mth.DEG_TO_RAD);
		int cols = Math.max(1, Math.round(lebar));
		float omega = Mth.TWO_PI * 1.4F / 20.0F;
		for (int k = 0; k < 8; k++) {
			float a0 = k / 8.0F;
			float a1 = (k + 1) / 8.0F;
			float z0 = -depth * a0;
			float z1 = -depth * a1;
			float y0 = back + (front - back) * a0 + 0.5F * a0 * Mth.sin(t * omega + a0 * 4.0F + h.benih * 0.001F);
			float y1 = back + (front - back) * a1 + 0.5F * a1 * Mth.sin(t * omega + a1 * 4.0F + h.benih * 0.001F);
			for (int c = 0; c < cols; c++) {
				float xa = -half + 2.0F * half * c / cols;
				float xb = -half + 2.0F * half * (c + 1) / cols;
				titik(P0, ox, oy, oz, Q, xa, y0, z0);
				titik(P1, ox, oy, oz, Q, xa, y1, z1);
				titik(P2, ox, oy, oz, Q, xb, y1, z1);
				titik(P3, ox, oy, oz, Q, xb, y0, z0);
				badan.quadSub(P0, P1, P2, P3, 1, 1, 0.0F, a0 * 0.75F, 1.0F, a1 * 0.75F, tint, light);
			}
		}
		// the valance hanging from the front edge, its fringe at the bottom of the cell
		for (int c = 0; c < cols; c++) {
			float xa = -half + 2.0F * half * c / cols;
			float xb = -half + 2.0F * half * (c + 1) / cols;
			titik(P0, ox, oy, oz, Q, xa, front, -depth);
			titik(P1, ox, oy, oz, Q, xa, front - 4.0F, -depth);
			titik(P2, ox, oy, oz, Q, xb, front - 4.0F, -depth);
			titik(P3, ox, oy, oz, Q, xb, front, -depth);
			badan.quadSub(P0, P1, P2, P3, 1, 1, 0.0F, 0.75F, 1.0F, 1.0F, tint, light);
		}
		for (int side = -1; side <= 1; side += 2) {
			kotak(badan, ox, oy, oz, Q, side * (half - 1.0F), front / 2.0F, -depth + 1.0F, 0.7F, front / 2.0F, 0.7F, 6, 0, white, light);
		}
		kotak(badan, ox, oy, oz, Q, 0.0F, back, -0.5F, half, 0.6F, 0.6F, 6, 0, white, light);
	}

	/** Incense smoke: puffs rising 1.2 blocks over a 40-tick cycle, widening, drifting and fading, over a red ember. */
	private static void asapDupa(Hias h, float ox, float oy, float oz) {
		int puffs = Jenis.ASAP_DUPA.batas[tier];
		int light = h.cahaya;
		cahaya.billboard(ox, oy + 0.05F, oz, 0.05F, KANAN, ATAS, 7, 1, argb(0.9F, 0xFF6030), LightCoordsUtil.FULL_BRIGHT);
		for (int i = 0; i < puffs; i++) {
			float ph = ((t + i * 40.0F / puffs) / 40.0F) % 1.0F;
			float y = 0.15F + ph * 1.2F;
			float drift = Mth.sin(t * 0.05F + i * 1.7F + h.benih * 0.001F) * 0.12F * ph;
			float size = 0.1F + ph * 0.32F;
			float alpha = 0.55F * (1.0F - ph) * Math.min(1.0F, ph * 5.0F);
			asap.billboard(ox + drift, oy + y, oz + drift * 0.6F, size, KANAN, ATAS, 4 + i % 3, 1, argb(alpha, 0xEEEEF4), light);
		}
	}

	private HiasanDunia() {
	}
}
