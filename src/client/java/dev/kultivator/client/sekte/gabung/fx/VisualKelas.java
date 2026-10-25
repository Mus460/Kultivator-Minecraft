package dev.kultivator.client.sekte.gabung.fx;

import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.sekte.gabung.GabungClient;
import dev.kultivator.client.sekte.seragam.LambangCache;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.seragam.SeragamPayloads;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The class of an NPC sect made visible (DESIGN 41.4.6, module 41V): no block ever changes, the difference between a
 * Minor School and a Holy Land is drawn on the client at the sect's gate.
 * <table>
 * <caption>per class (from the known sect's class line {@code GabungClient.kelas}; MEROSOT shows one class lower)</caption>
 * <tr><th>class</th><th>at the gate</th></tr>
 * <tr><td>1 Kecil</td><td>the 2 emblem standards of 36S (unchanged)</td></tr>
 * <tr><td>2 Menengah</td><td>4 standards, 4 spirit lanterns floating up the approach</td></tr>
 * <tr><td>3 Besar</td><td>6 standards, 6 lanterns, a thin formation dome (Normal and up)</td></tr>
 * <tr><td>4 Agung</td><td>8 standards, 8 lanterns, the dome, a flock of cranes by day, a pillar of light at dusk</td></tr>
 * <tr><td>5 Abadi</td><td>all of it plus a crown of golden cloud over the peak (Maksimal)</td></tr>
 * </table>
 * <b>Where.</b> The client knows an NPC sect's gate through the 36S emblem sync ({@code LambangCache} key
 * {@code npc:<sect>}): its two standards were chosen by the server from a fixed table of spots round the gate
 * ({@code Seragam.findStandards}, mirrored in {@link #SPOTS}), so the pair gives the gate's exact column. The extra
 * standards are added to that entry's banner list, so the 36S renderer draws them with the sect's real emblem cloth,
 * pole, finial, wind and LOD; they are spots 4 blocks apart continuing the line of the first two, each checked on the
 * client's own chunk data (dry solid ground within 6 blocks of the gate's height, 5 free blocks above). When the
 * server resends or forgets the entry its list is replaced and the extra standards are added again (or not).
 * {@link #titik} lets a later anchor payload put the dome over the main hall and the crown over the summit hall instead
 * of the gate.
 * <p>
 * <b>Budget (41.12.3).</b> At most 8 standards per sect (the 36S cap {@code maxBanners} also applies); animated within
 * 48 blocks, still up to 128, nothing beyond; Minimal: still standards only (no lanterns, no dome); the dome is one band
 * of 32 (Normal) or 48 (Maksimal) quads per ring; every group takes its quads from {@code EpicBudget}; the pillar takes
 * a 7b pillar slot. Recomputed once a second on the client tick; drawing reads cached positions only.
 */
public final class VisualKelas {

	/** The spots round an NPC gate the server tries for its two standards, in order (mirror of {@code Seragam.findStandards}). */
	static final int[][] SPOTS = {{7, 0}, {-7, 0}, {0, 7}, {0, -7}, {6, 6}, {-6, -6}, {6, -6}, {-6, 6}, {8, 3}, {-8, -3}};
	/** Standards per class 1-5 (the first two are the 36S ones). */
	static final int[] PANJI = {2, 4, 6, 8, 8};
	/** Spirit lanterns per class. */
	static final int[] LENTERA = {0, 4, 6, 8, 8};
	/** Dome radius per class (0 = no dome). */
	static final float[] KUBAH = {0.0F, 0.0F, 26.0F, 32.0F, 38.0F};
	static final double ANIMASI = 48.0D;
	static final double TAMPAK = 128.0D;
	private static final int JEDA = 20;
	private static final int GOLD = 0xFFD27A;
	private static final int WARM = 0xFFC070;

	/** One known NPC sect at its gate. */
	static final class Gerbang {
		final String sekte;
		/** The server's banner list we built on, and the list we put in its place (by identity). */
		List<SeragamPayloads.Banner> dasar = List.of();
		@Nullable List<SeragamPayloads.Banner> milikku;
		boolean ada;
		double gx;
		double gy;
		double gz;
		/** Unit vector across the gate (from one standard to the other) and the approach (perpendicular). */
		double ux;
		double uz;
		int kelas;
		int warna;
		boolean merosot;
		int target;
		boolean lengkap;
		final double[] lentera = new double[8 * 3];
		int lenteraN;
		@Nullable BlockPos aula;
		@Nullable BlockPos puncak;
		long seed;

		Gerbang(String sekte) {
			this.sekte = sekte;
			this.seed = sekte.hashCode() * 0x9E3779B97F4A7C15L;
		}

		int kelasTampak() {
			return Math.max(1, Math.min(5, merosot ? kelas - 1 : kelas));
		}
	}

	private static final Map<String, Gerbang> GERBANG = new HashMap<>();
	/** Anchors from {@link #titik} (kept apart so a resync of the gate does not drop them). */
	private static final Map<String, BlockPos[]> TITIK = new HashMap<>();

	/** True when something may be drawn this frame. */
	static boolean any() {
		return !GERBANG.isEmpty() && GabungFxClient.saklar(GabungFxClient.VISUAL_KELAS);
	}

	/**
	 * The compound anchors of sect {@code sekte} ({@code kompleks/aula}, {@code kompleks/puncak}; null = unknown): the dome
	 * then stands over the main hall and the crown over the summit hall instead of over the gate. For a payload of the
	 * server (none sends it yet; see {@code docs/progres/m2-41V-P.md}).
	 */
	public static void titik(String sekte, @Nullable BlockPos aula, @Nullable BlockPos puncak) {
		if (sekte == null) {
			return;
		}
		if (aula == null && puncak == null) {
			TITIK.remove(sekte);
		} else {
			TITIK.put(sekte, new BlockPos[]{aula, puncak});
		}
	}

	// ================================================================== tick (once a second)

	static void tick(ClientLevel level, long ticks) {
		if (ticks % JEDA != 0L) {
			return;
		}
		if (!GabungFxClient.saklar(GabungFxClient.VISUAL_KELAS)) {
			lepasSemua();
			return;
		}
		String dim = level.dimension().identifier().toString();
		Set<String> dilihat = new HashSet<>();
		for (LambangCache.Entry entry : LambangCache.all()) {
			if (!entry.key.startsWith("npc:") || !dim.equals(entry.dim)) {
				continue;
			}
			String sekte = entry.key.substring(4);
			GabungPayloads.EntriKelas kelas = GabungClient.kelas(sekte);
			if (kelas == null) {
				// not in the player's directory yet: the class stays a secret until the gate is visited
				continue;
			}
			dilihat.add(sekte);
			Gerbang g = GERBANG.computeIfAbsent(sekte, Gerbang::new);
			g.kelas = kelas.kelas();
			g.warna = kelas.warna() & 0xFFFFFF;
			g.merosot = kelas.merosot();
			BlockPos[] titik = TITIK.get(sekte);
			g.aula = titik == null ? null : titik[0];
			g.puncak = titik == null ? null : titik[1];
			if (entry.banners != g.milikku) {
				// new from the server (or forgotten): rebuild on the server's list
				g.dasar = entry.banners;
				g.milikku = null;
				g.lengkap = false;
				g.ada = gerbang(g, g.dasar);
				g.lenteraN = 0;
			}
			if (!g.ada) {
				continue;
			}
			int target = Math.min(entry.maxBanners, PANJI[g.kelasTampak() - 1]);
			if (target != g.target || !g.lengkap) {
				g.target = target;
				List<SeragamPayloads.Banner> tambah = tambahan(level, g, target - g.dasar.size());
				List<SeragamPayloads.Banner> semua = new ArrayList<>(g.dasar.size() + tambah.size());
				semua.addAll(g.dasar);
				semua.addAll(tambah);
				g.milikku = Collections.unmodifiableList(semua);
				entry.banners = g.milikku;
			}
			if (g.lenteraN < LENTERA[g.kelasTampak() - 1]) {
				lentera(level, g);
			}
		}
		for (Iterator<Map.Entry<String, Gerbang>> it = GERBANG.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Gerbang> e = it.next();
			if (!dilihat.contains(e.getKey())) {
				lepas(e.getValue());
				it.remove();
			}
		}
	}

	/** Gives every augmented entry its server list back and forgets all (switch off, disconnect). */
	static void lepasSemua() {
		for (Gerbang g : GERBANG.values()) {
			lepas(g);
		}
		GERBANG.clear();
	}

	private static void lepas(Gerbang g) {
		LambangCache.Entry entry = LambangCache.get("npc:" + g.sekte);
		if (entry != null && g.milikku != null && entry.banners == g.milikku) {
			entry.banners = g.dasar;
		}
	}

	static void clear() {
		GERBANG.clear();
		TITIK.clear();
	}

	/**
	 * The gate's column from the server's two standards: the first pair of spots {@code i < j} of {@link #SPOTS} whose
	 * difference is the standards' difference (the second standard is never on the first one's side). False when there
	 * are fewer than two standards.
	 */
	static boolean gerbang(Gerbang g, List<SeragamPayloads.Banner> banners) {
		if (banners.size() < 2) {
			return false;
		}
		BlockPos a = banners.get(0).pos();
		BlockPos b = banners.get(1).pos();
		double gx = (a.getX() + b.getX()) * 0.5D;
		double gz = (a.getZ() + b.getZ()) * 0.5D;
		int dx = b.getX() - a.getX();
		int dz = b.getZ() - a.getZ();
		search:
		for (int i = 0; i < SPOTS.length; i++) {
			for (int j = i + 1; j < SPOTS.length; j++) {
				if (SPOTS[j][0] - SPOTS[i][0] == dx && SPOTS[j][1] - SPOTS[i][1] == dz
						&& SPOTS[i][0] * SPOTS[j][0] + SPOTS[i][1] * SPOTS[j][1] <= 0) {
					gx = a.getX() - SPOTS[i][0];
					gz = a.getZ() - SPOTS[i][1];
					break search;
				}
			}
		}
		g.gx = gx + 0.5D;
		g.gz = gz + 0.5D;
		g.gy = (a.getY() + b.getY()) * 0.5D;
		double len = Math.sqrt((double) dx * dx + (double) dz * dz);
		g.ux = len < 1.0E-3D ? 1.0D : dx / len;
		g.uz = len < 1.0E-3D ? 0.0D : dz / len;
		return true;
	}

	/**
	 * {@code n} more standards: pairs on both sides continuing the line of the first two, 4 blocks apart, each nudged
	 * up to 4 blocks along the approach when its own spot is not fit. Spots in unloaded chunks are retried next second.
	 */
	private static List<SeragamPayloads.Banner> tambahan(ClientLevel level, Gerbang g, int n) {
		List<SeragamPayloads.Banner> out = new ArrayList<>();
		if (n <= 0) {
			g.lengkap = true;
			return out;
		}
		double r = 0.0D;
		for (SeragamPayloads.Banner b : g.dasar) {
			r = Math.max(r, Math.hypot(b.pos().getX() + 0.5D - g.gx, b.pos().getZ() + 0.5D - g.gz));
		}
		boolean semuaDimuat = true;
		int[] geser = {0, 2, -2, 4, -4};
		for (int k = 0; out.size() < n && k < 8; k++) {
			double jarak = r + 4.0D * (k / 2 + 1);
			double sisi = k % 2 == 0 ? 1.0D : -1.0D;
			BlockPos found = null;
			for (int off : geser) {
				double x = g.gx + g.ux * jarak * sisi - g.uz * off;
				double z = g.gz + g.uz * jarak * sisi + g.ux * off;
				int bx = (int) Math.floor(x);
				int bz = (int) Math.floor(z);
				if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
					semuaDimuat = false;
					break;
				}
				BlockPos at = layak(level, bx, bz, g.gy);
				if (at != null) {
					found = at;
					break;
				}
			}
			if (found != null) {
				out.add(new SeragamPayloads.Banner(found, yaw(found, g.gx, g.gz), SeragamPayloads.BANNER_STANDARD));
			}
		}
		g.lengkap = semuaDimuat;
		return out;
	}

	/** The standing spot of column (x, z), or null: dry solid ground within 6 blocks of the gate, 5 free blocks above. */
	private static @Nullable BlockPos layak(ClientLevel level, int x, int z, double gateY) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos at = new BlockPos(x, y, z);
		BlockState ground = level.getBlockState(at.below());
		if (Math.abs(y - gateY) > 6.0D || ground.isAir() || !ground.getFluidState().isEmpty()) {
			return null;
		}
		for (int i = 0; i < 5; i++) {
			if (!level.isEmptyBlock(at.above(i))) {
				return null;
			}
		}
		return at;
	}

	/** Yaw toward the gate in 1/256 turns, 0 = +z (as {@code Seragam.yaw}). */
	private static int yaw(BlockPos from, double tx, double tz) {
		double dx = tx - (from.getX() + 0.5D);
		double dz = tz - (from.getZ() + 0.5D);
		if (Math.abs(dx) < 1.0E-3D && Math.abs(dz) < 1.0E-3D) {
			return 0;
		}
		double degrees = Math.toDegrees(Math.atan2(-dx, dz));
		return (int) Math.round(degrees / 360.0D * 256.0D) & 0xFF;
	}

	/** Lantern spots: pairs flanking the approach on both sides of the gate, 2.6 blocks over the ground they stand on. */
	private static void lentera(ClientLevel level, Gerbang g) {
		int want = LENTERA[g.kelasTampak() - 1];
		double px = -g.uz;
		double pz = g.ux;
		int n = 0;
		for (int k = 0; n < want && k < 8; k++) {
			double along = 5.0D * (k / 4 + 1) * (k % 4 < 2 ? 1.0D : -1.0D);
			double side = k % 2 == 0 ? 2.8D : -2.8D;
			double x = g.gx + px * along + g.ux * side;
			double z = g.gz + pz * along + g.uz * side;
			int bx = (int) Math.floor(x);
			int bz = (int) Math.floor(z);
			if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
				return;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
			if (Math.abs(y - g.gy) > 14.0D) {
				y = (int) Math.round(g.gy);
			}
			g.lentera[n * 3] = x;
			g.lentera[n * 3 + 1] = y + 2.6D;
			g.lentera[n * 3 + 2] = z;
			n++;
		}
		g.lenteraN = n;
	}

	// ================================================================== drawing

	static void draw(ClientLevel level) {
		if (!any()) {
			return;
		}
		int mutu = GabungFxClient.mutu();
		if (mutu <= 0) {
			// Minimal: the still standards of the 36S renderer only
			return;
		}
		float dark = Math.min(1.0F, level.getSkyDarken() / 11.0F);
		long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
		boolean siang = level.getSkyDarken() < 4;
		for (Gerbang g : GERBANG.values()) {
			if (!g.ada) {
				continue;
			}
			int kelas = g.kelasTampak();
			double dist = GabungFxClient.jarak(g.gx, g.gy + 4.0D, g.gz);
			float radius = KUBAH[kelas - 1];
			if (dist - radius > TAMPAK) {
				continue;
			}
			boolean hidup = dist - radius <= ANIMASI;
			lanterns(g, dist, hidup, dark);
			if (radius > 0.0F) {
				dome(g, radius, hidup, mutu);
			}
			if (kelas >= 4) {
				if (siang) {
					cranes(g, radius, mutu);
				}
				duskPillar(g, day);
			}
			if (kelas >= 5 && mutu >= 2) {
				crown(g, radius, hidup);
			}
		}
	}

	private static void lanterns(Gerbang g, double dist, boolean hidup, float dark) {
		if (g.lenteraN == 0 || dist > TAMPAK || !EpicBudget.takeQuads(g.lenteraN * 2)) {
			return;
		}
		int glowRgb = GabungFxClient.mix(WARM, g.warna, 0.3F);
		float glowA = (g.merosot ? 0.15F : 0.3F) + 0.4F * dark;
		for (int i = 0; i < g.lenteraN; i++) {
			double x = g.lentera[i * 3];
			double y = g.lentera[i * 3 + 1];
			double z = g.lentera[i * 3 + 2];
			if (hidup) {
				y += Math.sin(GabungFxClient.now * 0.045D + i * 1.3D) * 0.18D;
				x += Math.sin(GabungFxClient.now * 0.031D + i * 2.1D) * 0.06D;
			}
			float flicker = hidup ? 0.9F + 0.1F * (float) Math.sin(GabungFxClient.now * 0.37D + i * 3.7D) : 1.0F;
			GabungFxClient.sprite(GabungFxClient.LUNAK, x, y - 0.05D, z, 1.25F, GabungFxClient.argb(glowA * flicker, glowRgb), GabungFxClient.SEL_GLOW, 0.0F);
			GabungFxClient.tegak(GabungFxClient.SPRITE, x, y, z, 0.42F, 0.42F, GabungFxClient.argb(1.0F, 0xFFFFFF), GabungFxClient.SEL_LENTERA);
		}
	}

	/** The thin half-dome of the sect's ward: rings of lattice quads in the sect's colour, breathing, a pulse climbing. */
	private static void dome(Gerbang g, float radius, boolean hidup, int mutu) {
		int segments = mutu >= 2 ? 48 : 32;
		int rings = mutu >= 2 ? 9 : 6;
		if (!EpicBudget.takeQuads(segments * (rings + 1))) {
			return;
		}
		double cx = g.aula != null ? g.aula.getX() + 0.5D : g.gx;
		double cy = g.aula != null ? g.aula.getY() : g.gy - 1.0D;
		double cz = g.aula != null ? g.aula.getZ() + 0.5D : g.gz;
		float t = (float) GabungFxClient.now / 20.0F;
		float breath = hidup ? 0.8F + 0.2F * (float) Math.sin(t * Math.PI * 2.0D / 5.0D) : 0.9F;
		float spin = hidup ? t * 0.01F : 0.0F;
		int rgb = GabungFxClient.mix(g.warna, 0xFFFFFF, 0.35F);
		float u0 = GabungFxClient.u0(GabungFxClient.SEL_KISI);
		float v0 = GabungFxClient.v0(GabungFxClient.SEL_KISI);
		float u1 = u0 + 0.25F;
		float v1 = v0 + 0.25F;
		float maxPhi = (float) Math.toRadians(84.0D);
		for (int k = 0; k < rings; k++) {
			float lo = maxPhi * k / rings;
			float hi = maxPhi * (k + 1) / rings;
			float climb = hidup ? 0.5F + 0.5F * (float) Math.sin(t * 1.1F - k * 0.8F) : 0.5F;
			float a = (0.07F + 0.09F * climb) * breath * (1.0F - 0.35F * k / rings);
			int c = GabungFxClient.argb(a, rgb);
			float rLo = radius * (float) Math.cos(lo);
			float rHi = radius * (float) Math.cos(hi);
			double yLo = cy + radius * Math.sin(lo);
			double yHi = cy + radius * Math.sin(hi);
			int seg = Math.max(8, Math.round(segments * (float) Math.cos(lo)));
			for (int i = 0; i < seg; i++) {
				double t0 = (double) i / seg * Math.PI * 2.0D + spin;
				double t1 = (double) (i + 1) / seg * Math.PI * 2.0D + spin;
				quad(GabungFxClient.LUNAK,
						cx + Math.cos(t0) * rLo, yLo, cz + Math.sin(t0) * rLo, u0, v1,
						cx + Math.cos(t1) * rLo, yLo, cz + Math.sin(t1) * rLo, u1, v1,
						cx + Math.cos(t1) * rHi, yHi, cz + Math.sin(t1) * rHi, u1, v0,
						cx + Math.cos(t0) * rHi, yHi, cz + Math.sin(t0) * rHi, u0, v0, c);
			}
		}
		// the rim: a low band of light where the ward meets the ground
		int rim = GabungFxClient.argb(0.32F * breath, GabungFxClient.mix(g.warna, 0xFFFFFF, 0.5F));
		for (int i = 0; i < segments; i++) {
			double t0 = (double) i / segments * Math.PI * 2.0D + spin;
			double t1 = (double) (i + 1) / segments * Math.PI * 2.0D + spin;
			GabungFxClient.beam(GabungFxClient.CAHAYA, cx + Math.cos(t0) * radius, cy + 0.6D, cz + Math.sin(t0) * radius,
					cx + Math.cos(t1) * radius, cy + 0.6D, cz + Math.sin(t1) * radius, 1.2F, rim, rim);
		}
		if (mutu >= 2 && EpicBudget.takeQuads(1)) {
			// the rune ring at the apex, turning
			GabungFxClient.datar(GabungFxClient.SPRITE, cx, cy + radius * Math.sin(maxPhi) + 0.2D, cz, radius * 0.12F,
					GabungFxClient.argb(0.45F * breath, rgb), GabungFxClient.SEL_CINCIN, spin * 6.0D);
		}
	}

	private static void quad(GabungFxClient.Sink sink, double x0, double y0, double z0, float u0, float v0, double x1, double y1, double z1, float u1,
			float v1, double x2, double y2, double z2, float u2, float v2, double x3, double y3, double z3, float u3, float v3, int argb) {
		double cx = GabungFxClient.camX;
		double cy = GabungFxClient.camY;
		double cz = GabungFxClient.camZ;
		sink.vertex((float) (x0 - cx), (float) (y0 - cy), (float) (z0 - cz), u0, v0, argb, 0.0F, 1.0F, 0.0F);
		sink.vertex((float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz), u1, v1, argb, 0.0F, 1.0F, 0.0F);
		sink.vertex((float) (x2 - cx), (float) (y2 - cy), (float) (z2 - cz), u2, v2, argb, 0.0F, 1.0F, 0.0F);
		sink.vertex((float) (x3 - cx), (float) (y3 - cy), (float) (z3 - cz), u3, v3, argb, 0.0F, 1.0F, 0.0F);
	}

	/** Red-crowned cranes circling over the ward in a loose V by day (5 Normal, 9 Maksimal). */
	private static void cranes(Gerbang g, float radius, int mutu) {
		int birds = mutu >= 2 ? 9 : 5;
		if (!EpicBudget.takeQuads(birds)) {
			return;
		}
		double cx = g.aula != null ? g.aula.getX() + 0.5D : g.gx;
		double cz = g.aula != null ? g.aula.getZ() + 0.5D : g.gz;
		double baseY = (g.aula != null ? g.aula.getY() : g.gy) + Math.max(14.0F, radius * 0.8F) + 6.0D;
		double loop = radius + 10.0D;
		float t = (float) GabungFxClient.now / 20.0F;
		double phase = (g.seed & 0xFF) / 255.0D * Math.PI * 2.0D;
		double a = t / 70.0D * Math.PI * 2.0D + phase;
		double lx = cx + Math.cos(a) * loop;
		double lz = cz + Math.sin(a) * loop * 0.8D;
		double ly = baseY + Math.sin(a * 2.0D) * 3.0D;
		// heading = derivative of the loop
		double vx = -Math.sin(a) * loop;
		double vz = Math.cos(a) * loop * 0.8D;
		double vl = Math.max(1.0E-3D, Math.sqrt(vx * vx + vz * vz));
		vx /= vl;
		vz /= vl;
		double sx = -vz;
		double sz = vx;
		int tint = 0xFFFFFF;
		for (int i = 0; i < birds; i++) {
			int rank = (i + 1) / 2;
			double side = i == 0 ? 0.0D : (i % 2 == 0 ? 1.0D : -1.0D);
			double bx = lx - vx * rank * 2.4D + sx * side * rank * 1.9D;
			double bz = lz - vz * rank * 2.4D + sz * side * rank * 1.9D;
			double by = ly - rank * 0.45D + Math.sin(t * 0.7F + i) * 0.3D;
			boolean glide = Math.sin(t * 0.23F + i * 1.7F) > 0.4D;
			int frame = glide ? 0 : (Math.sin((t * 2.4F + i * 0.9F) * Math.PI) > 0.0D ? 0 : 1);
			boolean flip = vx * GabungFxClient.RIGHT.x + vz * GabungFxClient.RIGHT.z < 0.0D;
			float u0 = frame * 0.5F;
			float u1 = u0 + 0.5F;
			if (flip) {
				float tmp = u0;
				u0 = u1;
				u1 = tmp;
			}
			double d = GabungFxClient.jarak(bx, by, bz);
			float fade = 1.0F - GabungFxClient.smooth((float) ((d - 110.0D) / 40.0D));
			GabungFxClient.billboard(GabungFxClient.BURUNG, bx, by, bz, 1.25F, 0.625F, GabungFxClient.argb(fade, tint), u0, 0.0F, u1, 1.0F, 0.0F);
		}
	}

	/** A pillar of golden light over the hall at dusk (day ticks 11800-13600), rising and fading. */
	private static void duskPillar(Gerbang g, long day) {
		if (day < 11800L || day > 13600L) {
			return;
		}
		float k = day < 12400L ? (day - 11800L) / 600.0F : day > 13000L ? (13600L - day) / 600.0F : 1.0F;
		if (k <= 0.05F || !EpicBudget.takePillar()) {
			return;
		}
		double cx = g.aula != null ? g.aula.getX() + 0.5D : g.gx;
		double cy = g.aula != null ? g.aula.getY() : g.gy;
		double cz = g.aula != null ? g.aula.getZ() + 0.5D : g.gz;
		int rgb = GabungFxClient.mix(GOLD, g.warna, 0.25F);
		GabungFxClient.pilar(cx, cy, cz, GabungFxClient.argb(k, rgb));
	}

	/** The crown of golden cloud over the summit hall of an Immortal Sect: twelve cloud puffs turning round a halo. */
	private static void crown(Gerbang g, float radius, boolean hidup) {
		int puffs = 12;
		if (!EpicBudget.takeQuads(puffs + 2)) {
			return;
		}
		double cx = g.puncak != null ? g.puncak.getX() + 0.5D : (g.aula != null ? g.aula.getX() + 0.5D : g.gx);
		double cz = g.puncak != null ? g.puncak.getZ() + 0.5D : (g.aula != null ? g.aula.getZ() + 0.5D : g.gz);
		double base = g.puncak != null ? g.puncak.getY() + 26.0D : (g.aula != null ? g.aula.getY() : g.gy) + radius + 10.0D;
		float t = (float) GabungFxClient.now / 20.0F;
		double spin = hidup ? t * 0.08D : 0.0D;
		GabungFxClient.sprite(GabungFxClient.LUNAK, cx, base, cz, 9.0F, GabungFxClient.argb(0.35F, GOLD), GabungFxClient.SEL_GLOW, 0.0F);
		GabungFxClient.datar(GabungFxClient.SPRITE, cx, base - 0.5D, cz, 6.5F, GabungFxClient.argb(0.5F, GOLD), GabungFxClient.SEL_CINCIN, -spin);
		for (int i = 0; i < puffs; i++) {
			double a = i * Math.PI * 2.0D / puffs + spin;
			double y = base + Math.sin(a * 3.0D + t * 0.5F) * 0.5D + (i % 2) * 0.8D;
			GabungFxClient.sprite(GabungFxClient.SPRITE, cx + Math.cos(a) * 6.0D, y, cz + Math.sin(a) * 6.0D, 2.0F + (i % 3) * 0.3F,
					GabungFxClient.argb(0.85F, 0xFFE8B0), GabungFxClient.SEL_AWAN, 0.0F);
		}
	}

	private VisualKelas() {
	}
}
