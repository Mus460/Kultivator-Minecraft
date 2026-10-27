package dev.kultivator.client.dunia.tempat.perbaikan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.dunia.tempat.perbaikan.PerancahNet;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The repair scaffolding of DESIGN 38.R.3, drawn on the client only (never blocks) around every repair job the server
 * lists in {@code kultivator:perancah} ({@link PerancahNet}). A putlog scaffold of the kind still raised in Asian
 * towns: a ring of bamboo standards one block outside the damage, ledgers and a hand rail at every storey (two blocks),
 * short putlogs from each standard into the wall carrying a plank deck, a diagonal brace per bay, a leaning ladder, a
 * reed mat (an indigo tarp when a hired crew works, a worn grey mat when the craftsmen are afraid) hung on one side and
 * flapping in the wind, the "Mohon Bantuan" notice with a red ribbon that grows with the progress, the crew pennants of a
 * hired crew, a paper lantern at night, dust and sawdust while the work goes on, soot and blackened bamboo after a fire.
 * A new job's scaffold rises from the ground in 1.5 s; a finished one comes down the same way.
 * <p>
 * Budget (38.0 rule 6): {@link EffectQuality} picks the detail (Minimal: crossed-quad standards and the top deck only;
 * Normal: the whole frame; Maximal: lashings, a second mat, sawdust, more dust), distance lowers it (Normal beyond 40
 * blocks, Minimal beyond 64), and a per-frame quad cap drops the remaining jobs to Minimal. Two vanilla entity render
 * types on one 64 x 64 atlas ({@code entityCutout} lit by the job's sampled light, {@code entityTranslucent} for dust),
 * nothing in Iris's shadow pass, no post chain. Idle cost: one map check.
 */
final class Perancah {

	static final Identifier ATLAS = Kultivator.id("textures/dunia/tempat/perancah.png");
	private static final String P = "kultivator.tempat_perbaikan.perancah.";
	/** The atlas cells ({@code tools/gen_tempat_perancah.py}). */
	enum Sel {
		BAMBU(0, 0), BAMBU_HANGUS(1, 0), PAPAN(2, 0), TALI(3, 0),
		TIKAR(0, 1), TERPAL(1, 1), PENGUMUMAN(2, 1), DEBU(3, 1),
		TANGGA(0, 2), PANJI(1, 2), JELAGA(2, 2), SERBUK(3, 2),
		PAPAN_HANGUS(0, 3), TIKAR_LUSUH(1, 3), PITA(2, 3), LENTERA(3, 3);

		final float u0;
		final float v0;
		final float u1;
		final float v1;

		Sel(int col, int row) {
			u0 = col / 4.0F;
			v0 = row / 4.0F;
			u1 = (col + 1) / 4.0F;
			v1 = (row + 1) / 4.0F;
		}
	}

	private static final int[] QUAD_CAP = {500, 3000, 6000};
	private static final int[] PUFFS = {0, 2, 4};
	private static final float GROW_TICKS = 30.0F;
	/** Distance from the damage box to the line of standards. */
	private static final float OUT = 1.0F;
	private static final int MAX_JOBS = PerancahNet.MAKS * 2;

	private static final class Job {
		final long id;
		final int seed;
		int minX;
		int minY;
		int minZ;
		int maxX;
		int maxY;
		int maxZ;
		int flags;
		float progress;
		float shown;
		float grow;
		float growPrev;
		boolean leaving;
		int light = LightCoordsUtil.pack(0, 15);
		long lightAt = Long.MIN_VALUE;

		Job(long id) {
			this.id = id;
			this.seed = (int) (id ^ (id >>> 32)) * 0x9E3779B1;
		}

		boolean has(int flag) {
			return (flags & flag) != 0;
		}
	}

	private static final Map<Long, Job> JOBS = new HashMap<>();
	private static PerancahSink solid;
	private static PerancahSink soft;
	private static long ticks;
	private static ClientLevel seenLevel;

	// ================================================================== state

	/** {@code kultivator:perancah}: the full list of jobs near this player (an empty list takes every scaffold down). */
	static void onPayload(CompoundTag data) {
		ListTag list = data.getListOrEmpty("j");
		Set<Long> seen = new HashSet<>();
		for (Tag t : list) {
			if (!(t instanceof CompoundTag c)) {
				continue;
			}
			int[] b = c.getIntArray("b").orElse(null);
			if (b == null || b.length < 6) {
				continue;
			}
			long id = c.getLongOr("id", 0L);
			Job job = JOBS.get(id);
			if (job == null) {
				if (JOBS.size() >= MAX_JOBS) {
					continue;
				}
				job = new Job(id);
				JOBS.put(id, job);
				job.shown = c.getFloatOr("p", 0.0F);
			}
			job.minX = Math.min(b[0], b[3]);
			job.minY = Math.min(b[1], b[4]);
			job.minZ = Math.min(b[2], b[5]);
			job.maxX = Math.max(b[0], b[3]);
			job.maxY = Math.max(b[1], b[4]);
			job.maxZ = Math.max(b[2], b[5]);
			job.progress = Mth.clamp(c.getFloatOr("p", 0.0F), 0.0F, 1.0F);
			job.flags = c.getByteOr("f", (byte) 0) & 0xFF;
			job.leaving = false;
			seen.add(id);
		}
		for (Job job : JOBS.values()) {
			if (!seen.contains(job.id) && !KAVLING.contains(job.id)) {
				job.leaving = true;
			}
		}
	}

	/** Ids of the plot scaffolds of Kota yang Hidup (42.9.4): a repair list never takes them down. */
	private static final Set<Long> KAVLING = new HashSet<>();

	/**
	 * The plots under construction (stage 2-4) near this player: the same scaffold as a repair job (a hired crew's indigo
	 * tarp), rising with the stage. The full set each call: a plot that is not listed any more comes down.
	 */
	static void kavling(java.util.Collection<PerbaikanClient.Lahan> lahan) {
		Set<Long> sekarang = new HashSet<>();
		for (PerbaikanClient.Lahan l : lahan) {
			long id = ~l.id();
			Job job = JOBS.get(id);
			if (job == null) {
				if (JOBS.size() >= MAX_JOBS) {
					continue;
				}
				job = new Job(id);
				JOBS.put(id, job);
				job.shown = l.progres();
			}
			job.minX = Math.min(l.min().getX(), l.max().getX());
			job.minY = Math.min(l.min().getY(), l.max().getY());
			job.minZ = Math.min(l.min().getZ(), l.max().getZ());
			job.maxX = Math.max(l.min().getX(), l.max().getX());
			job.maxY = Math.max(l.min().getY(), l.max().getY());
			job.maxZ = Math.max(l.min().getZ(), l.max().getZ());
			job.progress = Mth.clamp(l.progres(), 0.0F, 1.0F);
			job.flags = PerancahNet.F_SEWA;
			job.leaving = false;
			sekarang.add(id);
		}
		for (long id : KAVLING) {
			Job job = JOBS.get(id);
			if (job != null && !sekarang.contains(id)) {
				job.leaving = true;
			}
		}
		KAVLING.clear();
		KAVLING.addAll(sekarang);
	}

	static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level != seenLevel) {
			seenLevel = level;
			JOBS.clear();
		}
		if (JOBS.isEmpty() || level == null) {
			return;
		}
		ticks++;
		for (Iterator<Job> it = JOBS.values().iterator(); it.hasNext(); ) {
			Job job = it.next();
			job.growPrev = job.grow;
			job.grow = Mth.clamp(job.grow + (job.leaving ? -1.0F : 1.0F) / GROW_TICKS, 0.0F, 1.0F);
			if (job.leaving && job.grow <= 0.0F) {
				it.remove();
				continue;
			}
			job.shown += (job.progress - job.shown) * 0.1F;
			if (ticks - job.lightAt >= 20L) {
				job.lightAt = ticks;
				job.light = sampleLight(level, job);
			}
		}
	}

	static void clear() {
		JOBS.clear();
		KAVLING.clear();
		seenLevel = null;
	}

	/** The brightest of five samples round the scaffold (the standards stand in the open, not inside the wall). */
	private static int sampleLight(ClientLevel level, Job job) {
		int midY = (job.minY + job.maxY) / 2 + 1;
		int light = LightCoordsUtil.getLightCoords(level, new BlockPos(job.minX - 1, midY, job.minZ - 1));
		light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(level, new BlockPos(job.maxX + 1, midY, job.minZ - 1)));
		light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(level, new BlockPos(job.minX - 1, midY, job.maxZ + 1)));
		light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(level, new BlockPos(job.maxX + 1, midY, job.maxZ + 1)));
		light = LightCoordsUtil.max(light, LightCoordsUtil.getLightCoords(level, new BlockPos((job.minX + job.maxX) / 2, job.maxY + 2, (job.minZ + job.maxZ) / 2)));
		return light;
	}

	// ================================================================== render

	/** The {@code COLLECT_SUBMITS} listener. */
	static void render(LevelRenderContext context) {
		if (JOBS.isEmpty() || ShaderCompat.shadowPass()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		long start = System.nanoTime();
		if (solid == null) {
			solid = new PerancahSink(RenderTypes.entityCutout(ATLAS), 4096);
			soft = new PerancahSink(RenderTypes.entityTranslucent(ATLAS), 256);
		}
		solid.reset();
		soft.reset();
		CameraRenderState camera = context.levelState().cameraRenderState;
		float partial = context.levelState().worldPartialTicks;
		Frame f = FRAME;
		f.level = level;
		f.cam = camera.pos;
		f.time = ticks + partial;
		f.orientation = camera.orientation;
		camera.orientation.transform(1.0F, 0.0F, 0.0F, f.right);
		camera.orientation.transform(0.0F, 1.0F, 0.0F, f.up);
		f.dark = level.isDarkOutside();
		f.boards.clear();
		int quality = EffectQuality.level().ordinal();
		int cap = QUAD_CAP[quality];
		for (Job job : JOBS.values()) {
			double cx = (job.minX + job.maxX + 1) * 0.5D;
			double cz = (job.minZ + job.maxZ + 1) * 0.5D;
			double dx = cx - f.cam.x;
			double dz = cz - f.cam.z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > PerancahNet.JARAK + 24.0D || solid.quads() > cap * 2) {
				continue;
			}
			int tier = quality;
			if (dist > 64.0D || solid.quads() > cap) {
				tier = 0;
			} else if (dist > 40.0D) {
				tier = Math.min(tier, 1);
			}
			float g = Mth.lerp(partial, job.growPrev, job.grow);
			draw(f, job, tier, g * g * (3.0F - 2.0F * g), dist);
		}
		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		solid.submit(collector, pose);
		soft.submit(collector, pose);
		for (Board board : f.boards) {
			board(pose, collector, board, f.orientation);
		}
		EpicBudget.addCpu(System.nanoTime() - start);
	}

	private record Board(Component text, float x, float y, float z, int color) {
	}

	private static final class Frame {
		ClientLevel level;
		Vec3 cam;
		float time;
		boolean dark;
		Quaternionf orientation;
		final Vector3f right = new Vector3f();
		final Vector3f up = new Vector3f();
		final List<Board> boards = new ArrayList<>();
	}

	private static final Frame FRAME = new Frame();

	/** One side of the ring: start point on the line of standards, direction along it, outward normal, length. */
	private static final class Side {
		float sx;
		float sz;
		float dx;
		float dz;
		float nx;
		float nz;
		float len;
		int bays;

		float x(float t, float inward) {
			return sx + dx * t - nx * inward;
		}

		float z(float t, float inward) {
			return sz + dz * t - nz * inward;
		}
	}

	private static final Side[] SIDES = {new Side(), new Side(), new Side(), new Side()};

	private static void draw(Frame f, Job job, int tier, float grow, double dist) {
		if (grow <= 0.001F) {
			return;
		}
		// camera-relative frame of the scaffold
		float x0 = (float) (job.minX - OUT - f.cam.x);
		float x1 = (float) (job.maxX + 1 + OUT - f.cam.x);
		float z0 = (float) (job.minZ - OUT - f.cam.z);
		float z1 = (float) (job.maxZ + 1 + OUT - f.cam.z);
		float yb = (float) (job.minY - f.cam.y);
		int levels = Mth.clamp((job.maxY + 1 - job.minY + 1) / 2, 1, 12);
		float height = levels * 2.0F + 1.0F;
		float shownTop = yb + height * grow;
		boolean fire = job.has(PerancahNet.F_API);
		boolean afraid = job.has(PerancahNet.F_TAKUT);
		boolean hired = job.has(PerancahNet.F_SEWA);
		Sel pole = fire ? Sel.BAMBU_HANGUS : Sel.BAMBU;
		Sel deck = fire ? Sel.PAPAN_HANGUS : Sel.PAPAN;
		int light = job.light;
		int white = 0xFFFFFFFF;

		setSide(SIDES[0], x0, z0, 1.0F, 0.0F, 0.0F, -1.0F, x1 - x0);
		setSide(SIDES[1], x1, z0, 0.0F, 1.0F, 1.0F, 0.0F, z1 - z0);
		setSide(SIDES[2], x1, z1, -1.0F, 0.0F, 0.0F, 1.0F, x1 - x0);
		setSide(SIDES[3], x0, z1, 0.0F, -1.0F, -1.0F, 0.0F, z1 - z0);
		// the side facing the viewer carries the notice, the pennants and the lantern
		int front = 0;
		float best = -Float.MAX_VALUE;
		float mx = (x0 + x1) * 0.5F;
		float mz = (z0 + z1) * 0.5F;
		for (int s = 0; s < 4; s++) {
			float dot = -mx * SIDES[s].nx - mz * SIDES[s].nz;
			if (dot > best) {
				best = dot;
				front = s;
			}
		}

		// ---- standards
		for (int s = 0; s < 4; s++) {
			Side side = SIDES[s];
			for (int i = 0; i < side.bays; i++) {
				if (tier == 0 && i != 0 && i != side.bays / 2) {
					continue;
				}
				float t = side.len * i / side.bays;
				float px = side.x(t, 0.0F);
				float pz = side.z(t, 0.0F);
				float half = i == 0 ? 0.08F : 0.065F;
				boolean tall = hired && s == front && i == 0 || hired && s == (front + 1) % 4 && i == 0;
				float top = Math.min(shownTop, yb + height + (tall && grow > 0.999F ? 1.3F : 0.0F));
				batang(px, yb, pz, px, top, pz, half, pole, 2.0F, tier == 0, light, white);
			}
		}

		// ---- storeys: ledgers, hand rails, putlogs, decks, lashings
		for (int k = 1; k <= levels; k++) {
			float y = yb + k * 2.0F;
			if (y > shownTop) {
				break;
			}
			boolean deckHere = tier > 0 ? !afraid || k <= (levels + 1) / 2 : k == levels;
			for (int s = 0; s < 4; s++) {
				Side side = SIDES[s];
				if (tier > 0 || k == levels) {
					batang(side.x(0.0F, -0.12F), y - 0.08F, side.z(0.0F, -0.12F), side.x(side.len, -0.12F), y - 0.08F, side.z(side.len, -0.12F), 0.05F,
							pole, 2.0F, tier == 0, light, white);
				}
				if (tier > 0 && y + 1.0F <= shownTop) {
					batang(side.x(0.0F, -0.12F), y + 1.0F, side.z(0.0F, -0.12F), side.x(side.len, -0.12F), y + 1.0F, side.z(side.len, -0.12F), 0.04F,
							pole, 2.0F, true, light, white);
				}
				if (tier > 0) {
					for (int i = 0; i < side.bays; i++) {
						float t = side.len * i / side.bays;
						batang(side.x(t, -0.12F), y - 0.02F, side.z(t, -0.12F), side.x(t, OUT + 0.1F), y - 0.02F, side.z(t, OUT + 0.1F), 0.04F, pole,
								2.0F, true, light, white);
						if (tier == 2) {
							tali(side, t, y - 0.08F, light);
						}
					}
				}
				if (deckHere) {
					// x-running sides span the corners, z-running sides only the wall between them (no z-fighting)
					float from = s % 2 == 0 ? 0.0F : OUT;
					float to = s % 2 == 0 ? side.len : side.len - OUT;
					papan(side, from, to, -0.1F, OUT - 0.04F, y + 0.06F, deck, light, white);
				}
			}
		}

		if (tier > 0) {
			// ---- diagonal braces, one per storey, zigzagging up each side
			for (int s = 0; s < 4; s++) {
				Side side = SIDES[s];
				float bay = side.len / side.bays;
				for (int k = 0; k < levels; k++) {
					float ya = yb + k * 2.0F;
					float yc = ya + 2.0F;
					if (yc > shownTop) {
						break;
					}
					int b = (k + s + (job.seed & 3)) % side.bays;
					float ta = bay * b;
					float tb = bay * (b + 1);
					if (k % 2 == 1) {
						float swap = ta;
						ta = tb;
						tb = swap;
					}
					batang(side.x(ta, -0.2F), ya + 0.1F, side.z(ta, -0.2F), side.x(tb, -0.2F), yc - 0.1F, side.z(tb, -0.2F), 0.04F, pole, 3.0F, true, light,
							white);
				}
			}
			// ---- the ladder, leaning on the side after the front, up to the top storey
			Side ls = SIDES[(front + 1) % 4];
			float ladderTop = Math.min(shownTop, yb + (tier == 2 ? levels : 1) * 2.0F + 1.0F);
			tangga(ls, 0.45F, 1.25F, yb, ladderTop, light);
		}

		// ---- the mat or tarp, taken down when the work is nearly done
		if (tier > 0 && job.shown < 0.85F && grow > 0.999F) {
			Sel cloth = hired ? Sel.TERPAL : afraid ? Sel.TIKAR_LUSUH : Sel.TIKAR;
			int sideA = Math.floorMod(job.seed, 4);
			if (sideA == front) {
				sideA = (sideA + 1) % 4;
			}
			tikar(f, SIDES[sideA], yb, height, cloth, light, job.seed, tier);
			if (tier == 2) {
				int sideB = (sideA + 2) % 4;
				if (sideB != front) {
					tikar(f, SIDES[sideB], yb, height, cloth, light, job.seed + 7, tier);
				}
			}
		}

		Side fs = SIDES[front];
		float noticeT = fs.len * (fs.bays / 2) / fs.bays;
		if (grow > 0.999F) {
			// ---- the notice (help wanted) or only the progress ribbon, on the front side's middle standard
			if (job.has(PerancahNet.F_BANTUAN)) {
				lembar(fs, noticeT, -0.15F, yb + 1.45F, 0.62F, 0.62F, Sel.PENGUMUMAN, light, white);
			}
			float ribbon = 0.15F + 0.6F * Mth.clamp(job.shown, 0.0F, 1.0F);
			float sway = 0.05F * Mth.sin(f.time * 0.09F + job.seed);
			pita(fs, noticeT, yb + 1.12F, ribbon, sway, light);
			// ---- crew pennants
			if (hired && tier > 0) {
				panji(f, fs, 0.0F, yb + height + 1.25F, light, job.seed);
				panji(f, SIDES[(front + 1) % 4], 0.0F, yb + height + 1.25F, light, job.seed + 3);
			}
			// ---- a paper lantern under the top ledger at night
			if (tier > 0 && f.dark) {
				float ly = yb + levels * 2.0F - 0.55F;
				float swing = 0.05F * Mth.sin(f.time * 0.05F + job.seed);
				float lx = fs.x(0.35F, -0.45F) + fs.dx * swing;
				float lz = fs.z(0.35F, -0.45F) + fs.dz * swing;
				salib(lx, ly, lz, 0.16F, 0.21F, Sel.LENTERA, LightCoordsUtil.FULL_BRIGHT, white);
				billboard(f, lx, ly, lz, 0.55F, Sel.DEBU, ARGB.color(70, 0xFFB060), LightCoordsUtil.FULL_BRIGHT);
			}
		}

		// ---- dust, soot and sawdust while the work goes on
		int puffs = tier == 0 ? 0 : PUFFS[tier];
		if (ShaderCompat.packOn()) {
			puffs = puffs / 2;
		}
		if (!job.leaving && grow > 0.999F) {
			float bx0 = (float) (job.minX - f.cam.x);
			float bz0 = (float) (job.minZ - f.cam.z);
			float bw = job.maxX + 1 - job.minX;
			float bd = job.maxZ + 1 - job.minZ;
			float bh = job.maxY + 1 - job.minY;
			for (int i = 0; i < puffs; i++) {
				float speed = afraid ? 0.012F : 0.02F;
				float cycle = f.time * speed + i * 0.37F + (job.seed & 0xFF) / 255.0F;
				int n = (int) Math.floor(cycle);
				float phase = cycle - n;
				int h = hash(job.seed, i, n);
				boolean sooty = fire && (h & 1) == 0;
				float px = bx0 + ((h >>> 3) & 0xFF) / 255.0F * bw;
				float pz = bz0 + ((h >>> 11) & 0xFF) / 255.0F * bd;
				float py = yb + ((h >>> 19) & 0xFF) / 255.0F * bh + phase * (sooty ? 2.0F : 0.8F);
				float size = 0.25F + phase * (sooty ? 0.8F : 0.45F);
				int alpha = Math.round((1.0F - phase) * phase * 4.0F * (sooty ? 120.0F : 100.0F));
				billboard(f, px, py, pz, size, sooty ? Sel.JELAGA : Sel.DEBU, ARGB.color(Mth.clamp(alpha, 0, 255), 0xFFFFFF), light);
			}
			if (tier == 2 && !afraid) {
				for (int i = 0; i < 2; i++) {
					float cycle = f.time * 0.03F + i * 0.5F + (job.seed & 0x7F) / 127.0F;
					int n = (int) Math.floor(cycle);
					float phase = cycle - n;
					int h = hash(job.seed, 10 + i, n);
					Side side = SIDES[h & 3];
					float t = ((h >>> 4) & 0xFF) / 255.0F * side.len;
					float y = yb + Math.min(levels, 1 + ((h >>> 12) & 3)) * 2.0F - phase * 1.8F;
					billboard(f, side.x(t, 0.4F), y, side.z(t, 0.4F), 0.18F, Sel.SERBUK, ARGB.color(Math.round(230 * (1.0F - phase)), 0xFFFFFF), light);
				}
			}
		}

		// ---- the words above the notice (only near)
		if (tier > 0 && dist < 20.0D && grow > 0.999F) {
			float tx = fs.x(noticeT, -0.6F);
			float tz = fs.z(noticeT, -0.6F);
			float ty = yb + 2.45F;
			int percent = Math.round(Mth.clamp(job.shown, 0.0F, 1.0F) * 100.0F);
			if (fire) {
				f.boards.add(new Board(Component.translatable(P + "api"), tx, ty, tz, 0xFFE0A070));
				ty += 0.3F;
			}
			if (afraid) {
				f.boards.add(new Board(Component.translatable(P + "takut"), tx, ty, tz, 0xFFB8B8C8));
				ty += 0.3F;
			}
			if (hired) {
				f.boards.add(new Board(Component.translatable(P + "sewa"), tx, ty, tz, 0xFFFFD27A));
				ty += 0.3F;
			}
			if (job.has(PerancahNet.F_BANTUAN)) {
				f.boards.add(new Board(Component.translatable(P + "bantuan"), tx, ty, tz, 0xFFFF8A70));
				ty += 0.3F;
			}
			f.boards.add(new Board(Component.translatable(P + "kemajuan", percent).withStyle(ChatFormatting.BOLD), tx, ty, tz, 0xFFFFF2D0));
		}
	}

	private static void setSide(Side side, float sx, float sz, float dx, float dz, float nx, float nz, float len) {
		side.sx = sx;
		side.sz = sz;
		side.dx = dx;
		side.dz = dz;
		side.nx = nx;
		side.nz = nz;
		side.len = len;
		side.bays = Mth.clamp(Mth.ceil(len / 2.5F), 1, 10);
	}

	private static int hash(int seed, int slot, int n) {
		int h = seed ^ (slot * 0x27D4EB2D) ^ (n * 0x165667B1);
		h ^= h >>> 15;
		h *= 0x85EBCA77;
		h ^= h >>> 13;
		h *= 0xC2B2AE3D;
		return h ^ (h >>> 16);
	}

	// ================================================================== shapes

	/**
	 * A bamboo (or any pole) from a to b, {@code half} thick: four faces (or two crossed planes when {@code cheap}),
	 * cut into pieces of {@code segment} blocks so the atlas cell's nodes repeat along it.
	 */
	private static void batang(float ax, float ay, float az, float bx, float by, float bz, float half, Sel sel, float segment, boolean cheap, int light,
							   int color) {
		float dx = bx - ax;
		float dy = by - ay;
		float dz = bz - az;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 0.02F) {
			return;
		}
		dx /= len;
		dy /= len;
		dz /= len;
		// s1 = d x ref, s2 = d x s1
		float rx = Math.abs(dy) > 0.9F ? 1.0F : 0.0F;
		float ry = Math.abs(dy) > 0.9F ? 0.0F : 1.0F;
		float s1x = dy * 0.0F - dz * ry;
		float s1y = dz * rx - dx * 0.0F;
		float s1z = dx * ry - dy * rx;
		float s1l = Mth.sqrt(s1x * s1x + s1y * s1y + s1z * s1z);
		s1x /= s1l;
		s1y /= s1l;
		s1z /= s1l;
		float s2x = dy * s1z - dz * s1y;
		float s2y = dz * s1x - dx * s1z;
		float s2z = dx * s1y - dy * s1x;
		int pieces = Math.max(1, Mth.ceil(len / segment));
		for (int j = 0; j < pieces; j++) {
			float t0 = j * segment;
			float t1 = Math.min(len, t0 + segment);
			float vTop = sel.v1 - (sel.v1 - sel.v0) * (t1 - t0) / segment;
			float pax = ax + dx * t0;
			float pay = ay + dy * t0;
			float paz = az + dz * t0;
			float pbx = ax + dx * t1;
			float pby = ay + dy * t1;
			float pbz = az + dz * t1;
			if (cheap) {
				float w = half * 1.4F;
				face(pax, pay, paz, pbx, pby, pbz, -s1x * w, -s1y * w, -s1z * w, s1x * w, s1y * w, s1z * w, sel, vTop, s2x, s2y, s2z, light, color);
				face(pax, pay, paz, pbx, pby, pbz, -s2x * w, -s2y * w, -s2z * w, s2x * w, s2y * w, s2z * w, sel, vTop, s1x, s1y, s1z, light, color);
				continue;
			}
			for (int k = 0; k < 4; k++) {
				// corners e_k = (+-s1 +-s2) * half, going round: (+,+) (-,+) (-,-) (+,-)
				float c0 = k == 0 || k == 3 ? 1.0F : -1.0F;
				float c1 = k < 2 ? 1.0F : -1.0F;
				// next corner
				int kn = (k + 1) % 4;
				float d0 = kn == 0 || kn == 3 ? 1.0F : -1.0F;
				float d1 = kn < 2 ? 1.0F : -1.0F;
				float ex = (s1x * c0 + s2x * c1) * half;
				float ey = (s1y * c0 + s2y * c1) * half;
				float ez = (s1z * c0 + s2z * c1) * half;
				float fx = (s1x * d0 + s2x * d1) * half;
				float fy = (s1y * d0 + s2y * d1) * half;
				float fz = (s1z * d0 + s2z * d1) * half;
				// face normal: the sum of the two corners (along s2, -s1, -s2, s1)
				float nx = k == 0 ? s2x : k == 1 ? -s1x : k == 2 ? -s2x : s1x;
				float ny = k == 0 ? s2y : k == 1 ? -s1y : k == 2 ? -s2y : s1y;
				float nz = k == 0 ? s2z : k == 1 ? -s1z : k == 2 ? -s2z : s1z;
				face(pax, pay, paz, pbx, pby, pbz, ex, ey, ez, fx, fy, fz, sel, vTop, nx, ny, nz, light, color);
			}
		}
	}

	/** One face of a pole piece from pa to pb, its edges offset by e (u0 edge) and f (u1 edge). */
	private static void face(float pax, float pay, float paz, float pbx, float pby, float pbz, float ex, float ey, float ez, float fx, float fy, float fz,
							 Sel sel, float vTop, float nx, float ny, float nz, int light, int color) {
		solid.quad(pax + ex, pay + ey, paz + ez, pax + fx, pay + fy, paz + fz, pbx + fx, pby + fy, pbz + fz, pbx + ex, pby + ey, pbz + ez,
				sel.u0, vTop, sel.u1, sel.v1, nx, ny, nz, color, light);
	}

	/** The plank deck of one side from {@code from} to {@code to} along it, {@code in0..in1} inward of the standards. */
	private static void papan(Side side, float from, float to, float in0, float in1, float y, Sel sel, int light, int color) {
		float length = to - from;
		if (length <= 0.05F) {
			return;
		}
		float piece = 1.5F;
		int pieces = Math.max(1, Mth.ceil(length / piece));
		for (int j = 0; j < pieces; j++) {
			float ta = from + j * piece;
			float tb = Math.min(to, ta + piece);
			float uEnd = sel.u0 + (sel.u1 - sel.u0) * (tb - ta) / piece;
			solid.quad(side.x(ta, in0), y, side.z(ta, in0), side.x(tb, in0), y, side.z(tb, in0), side.x(tb, in1), y, side.z(tb, in1),
					side.x(ta, in1), y, side.z(ta, in1), sel.u0, sel.v0, uEnd, sel.v1, 0.0F, 1.0F, 0.0F, color, light);
		}
	}

	/** A sheet standing on the side's face at {@code t}, centred at {@code cy}, {@code w} x {@code h}, facing outward. */
	private static void lembar(Side side, float t, float inward, float cy, float w, float h, Sel sel, int light, int color) {
		float ax = side.x(t - w * 0.5F, inward);
		float az = side.z(t - w * 0.5F, inward);
		float bx = side.x(t + w * 0.5F, inward);
		float bz = side.z(t + w * 0.5F, inward);
		float y0 = cy - h * 0.5F;
		float y1 = cy + h * 0.5F;
		solid.quad(ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az, sel.u0, sel.v0, sel.u1, sel.v1, side.nx, 0.0F, side.nz, color, light);
	}

	/** The red progress ribbon tied under the notice, {@code length} long, swaying. */
	private static void pita(Side side, float t, float top, float length, float sway, int light) {
		Sel sel = Sel.PITA;
		float ax = side.x(t - 0.06F, -0.14F);
		float az = side.z(t - 0.06F, -0.14F);
		float bx = side.x(t + 0.06F, -0.14F);
		float bz = side.z(t + 0.06F, -0.14F);
		float ox = side.dx * sway;
		float oz = side.dz * sway;
		float vEnd = sel.v0 + (sel.v1 - sel.v0) * Math.min(1.0F, length / 0.75F);
		solid.quad(ax + ox, top - length, az + oz, bx + ox, top - length, bz + oz, bx, top, bz, ax, top, az, sel.u0, sel.v0, sel.u1, vEnd, side.nx, 0.0F,
				side.nz, 0xFFFFFFFF, light);
	}

	/** Rope lashings at a standard-ledger joint: two crossed quads. */
	private static void tali(Side side, float t, float y, int light) {
		Sel sel = Sel.TALI;
		float h = 0.13F;
		float cx = side.x(t, -0.07F);
		float cz = side.z(t, -0.07F);
		float ax = cx - side.dx * h;
		float az = cz - side.dz * h;
		float bx = cx + side.dx * h;
		float bz = cz + side.dz * h;
		solid.quad(ax, y - h, az, bx, y - h, bz, bx, y + h, bz, ax, y + h, az, sel.u0, sel.v0, sel.u1, sel.v1, side.nx, 0.0F, side.nz, 0xFFFFFFFF, light);
		float px = cx - side.nx * h;
		float pz = cz - side.nz * h;
		float qx = cx + side.nx * h;
		float qz = cz + side.nz * h;
		solid.quad(px, y - h, pz, qx, y - h, qz, qx, y + h, qz, px, y + h, pz, sel.u0, sel.v0, sel.u1, sel.v1, side.dx, 0.0F, side.dz, 0xFFFFFFFF, light);
	}

	/** A ladder leaning against the side from {@code t0} to {@code t1}, foot 0.6 out, head 0.25 out, one block per cell. */
	private static void tangga(Side side, float t0, float t1, float y0, float y1, int light) {
		Sel sel = Sel.TANGGA;
		float rise = y1 - y0;
		if (rise < 0.5F) {
			return;
		}
		int pieces = Math.max(1, Mth.ceil(rise));
		for (int j = 0; j < pieces; j++) {
			float ya = y0 + j;
			float yc = Math.min(y1, ya + 1.0F);
			float oa = -0.6F + 0.35F * (ya - y0) / rise;
			float oc = -0.6F + 0.35F * (yc - y0) / rise;
			float vTop = sel.v1 - (sel.v1 - sel.v0) * (yc - ya);
			solid.quad(side.x(t0, oa), ya, side.z(t0, oa), side.x(t1, oa), ya, side.z(t1, oa), side.x(t1, oc), yc, side.z(t1, oc), side.x(t0, oc), yc,
					side.z(t0, oc), sel.u0, vTop, sel.u1, sel.v1, side.nx, 0.25F, side.nz, 0xFFFFFFFF, light);
		}
	}

	/** The mat (or tarp) hung outside one side from the top down four and a half blocks, flapping. */
	private static void tikar(Frame f, Side side, float yb, float height, Sel sel, int light, int seed, int tier) {
		float yTop = yb + height - 0.3F;
		float yBottom = Math.max(yb + 0.6F, yTop - 4.5F);
		int cols = Mth.clamp(Mth.ceil(side.len), 1, 24);
		int rows = Mth.clamp(Mth.ceil(yTop - yBottom), 1, 5);
		float colW = side.len / cols;
		float rowH = (yTop - yBottom) / rows;
		int color = sel == Sel.TIKAR_LUSUH ? 0xFFD8D8D8 : 0xFFFFFFFF;
		for (int c = 0; c < cols; c++) {
			for (int r = 0; r < rows; r++) {
				float ta = c * colW;
				float tb = ta + colW;
				float yUp = yTop - r * rowH;
				float yDn = yUp - rowH;
				float wUL = wave(f, seed, c, r, rows, tier);
				float wUR = wave(f, seed, c + 1, r, rows, tier);
				float wDL = wave(f, seed, c, r + 1, rows, tier);
				float wDR = wave(f, seed, c + 1, r + 1, rows, tier);
				float out = -0.22F;
				solid.quad(side.x(ta, out - wDL), yDn, side.z(ta, out - wDL), side.x(tb, out - wDR), yDn, side.z(tb, out - wDR),
						side.x(tb, out - wUR), yUp, side.z(tb, out - wUR), side.x(ta, out - wUL), yUp, side.z(ta, out - wUL),
						sel.u0, sel.v0, sel.u1, sel.v1, side.nx, 0.0F, side.nz, color, light);
			}
		}
	}

	/** How far the mat billows out at grid point (c, r): the lower rows swing more, the top edge is tied. */
	private static float wave(Frame f, int seed, int c, int r, int rows, int tier) {
		float hang = (float) r / rows;
		float big = 0.09F * Mth.sin(f.time * 0.06F + c * 0.8F + (seed & 0xFF) * 0.1F);
		float small = tier == 2 ? 0.025F * Mth.sin(f.time * 0.17F + c * 1.7F + r) : 0.0F;
		return (big + small) * hang;
	}

	/** A hired crew's pennant on the corner standard's tip, fluttering along the side. */
	private static void panji(Frame f, Side side, float t, float top, int light, int seed) {
		Sel sel = Sel.PANJI;
		float w = 0.6F;
		float h = 0.95F;
		float px = side.x(t, -0.05F);
		float pz = side.z(t, -0.05F);
		float mid = 0.5F;
		float vMid = sel.v0 + (sel.v1 - sel.v0) * mid;
		// the short cross-pole the pennant hangs from
		batang(px, top + 0.03F, pz, px + side.dx * (w + 0.08F), top + 0.03F, pz + side.dz * (w + 0.08F), 0.03F, Sel.BAMBU, 2.0F, true, light, 0xFFFFFFFF);
		for (int row = 0; row < 2; row++) {
			float ya = top - h * (row == 0 ? 0.0F : mid);
			float yc = top - h * (row == 0 ? mid : 1.0F);
			// the sleeve on the cross-pole is still; the cloth below swings out, the tail most, the edge by the standard least
			float ft = flutter(f, seed, row);
			float fb = flutter(f, seed, row + 1);
			float va = row == 0 ? sel.v0 : vMid;
			float vc = row == 0 ? vMid : sel.v1;
			// a = lower corner at the standard, b = lower free corner, c = upper free corner, d = upper corner at the standard
			solid.quad(px - side.nx * fb * 0.4F, yc, pz - side.nz * fb * 0.4F, px + side.dx * w - side.nx * fb, yc, pz + side.dz * w - side.nz * fb,
					px + side.dx * w - side.nx * ft, ya, pz + side.dz * w - side.nz * ft, px - side.nx * ft * 0.4F, ya, pz - side.nz * ft * 0.4F,
					sel.u0, va, sel.u1, vc, side.nx, 0.0F, side.nz, 0xFFFFFFFF, light);
		}
	}

	private static float flutter(Frame f, int seed, int row) {
		return 0.06F * row * Mth.sin(f.time * 0.21F + (seed & 0xFF) * 0.3F + row * 0.9F);
	}

	/** Two crossed upright quads centred at (x, y, z), half sizes {@code hw} x {@code hh}. */
	private static void salib(float x, float y, float z, float hw, float hh, Sel sel, int light, int color) {
		solid.quad(x - hw, y - hh, z, x + hw, y - hh, z, x + hw, y + hh, z, x - hw, y + hh, z, sel.u0, sel.v0, sel.u1, sel.v1, 0.0F, 0.0F, 1.0F, color, light);
		solid.quad(x, y - hh, z - hw, x, y - hh, z + hw, x, y + hh, z + hw, x, y + hh, z - hw, sel.u0, sel.v0, sel.u1, sel.v1, 1.0F, 0.0F, 0.0F, color, light);
	}

	/** A camera-facing soft square (dust, soot, sawdust, the lantern's glow), half size {@code half}. */
	private static void billboard(Frame f, float x, float y, float z, float half, Sel sel, int color, int light) {
		if (ARGB.alpha(color) <= 2) {
			return;
		}
		float rx = f.right.x * half;
		float ry = f.right.y * half;
		float rz = f.right.z * half;
		float ux = f.up.x * half;
		float uy = f.up.y * half;
		float uz = f.up.z * half;
		soft.quad(x - rx - ux, y - ry - uy, z - rz - uz, x + rx - ux, y + ry - uy, z + rz - uz, x + rx + ux, y + ry + uy, z + rz + uz,
				x - rx + ux, y - ry + uy, z - rz + uz, sel.u0, sel.v0, sel.u1, sel.v1, 0.0F, 1.0F, 0.0F, color, light);
	}

	/** A line of text with a dark backing, facing the camera. */
	private static void board(PoseStack poseStack, SubmitNodeCollector collector, Board board, Quaternionf rotation) {
		Font font = Minecraft.getInstance().font;
		FormattedCharSequence seq = board.text().getVisualOrderText();
		float width = font.width(seq);
		float s = 0.26F / 9.0F;
		poseStack.pushPose();
		poseStack.translate(board.x(), board.y(), board.z());
		poseStack.rotate(rotation);
		poseStack.scale(s, -s, s);
		collector.submitTextBackground(poseStack, -width / 2.0F - 2.0F, -5.5F, width / 2.0F + 2.0F, 5.0F, 0x77000000, Font.DisplayMode.NORMAL,
				LightCoordsUtil.FULL_BRIGHT);
		collector.submitText(poseStack, -width / 2.0F, -4.5F, seq, false, Font.DisplayMode.NORMAL, LightCoordsUtil.FULL_BRIGHT, board.color(), 0, 0);
		poseStack.popPose();
	}

	private Perancah() {
	}
}
