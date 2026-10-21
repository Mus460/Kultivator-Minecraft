package dev.kultivator.client.epic.kosmik.surga;

import dev.kultivator.client.epic.kosmik.Kosmik3D;
import dev.kultivator.client.epic.kosmik.KosmikStage;
import dev.kultivator.client.epic.kosmik.StageAnchor;
import dev.kultivator.client.epic.kosmik.StageContext;
import dev.kultivator.client.epic.kosmik.StageFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Heaven stage of Film Kosmik 3D (DESIGN 32.3, module H): the Southern Heaven Gate (Nantianmen) with its dragon
 * pillars and triple roof, the heavenly court and the Lingxiao Hall, floating islands with pavilions, pagodas, jade
 * bridges and peach trees, the Four Heavenly Kings and the celestial guard, the cloud bank and the great sea of clouds,
 * the Nine Heavens as nine glowing rings, golden light shafts, waterfalls of light and falling petals, under the heavenly
 * sky dome. It plays the beats of 32.3.3 for Titah Tiang and Titah Gunung (and their calm Penuh beats and the previews).
 * <p>
 * Presentation only, for the caster's own client: nothing here touches a block, an entity, the weather, the time or the
 * camera (the core owns the camera and the pocket). Per frame the stage plans every layer, asks
 * {@link StageFrame#take} per budget group in the priority order of 32.3.1 (dome, gate, hall, props, cloud sea, rings,
 * islands, shafts, guards, pavilions and pagodas, waterfalls, petals) and then writes the granted ones: models, the soft
 * layers far to near, the additive lights far to near. A group refused once stays off for the rest of the shot (no
 * flicker). Animation is a pure function of the beat, the shot time, the tier and the seed.
 */
public final class SurgaStage implements KosmikStage {

	private static final List<String> BEATS = List.of(SurgaChoreo.TIANG_GERBANG, SurgaChoreo.TIANG_LEPAS, SurgaChoreo.TIANG_JATUH,
			SurgaChoreo.GUNUNG_SEGEL, SurgaChoreo.TIANG_TENANG, SurgaChoreo.GUNUNG_TENANG, SurgaChoreo.PREVIEW_A, SurgaChoreo.PREVIEW_B,
			SurgaChoreo.PREVIEW_C);

	private final SurgaPalace palace = new SurgaPalace();
	private final SurgaSky sky = new SurgaSky();
	private final SurgaLights lights = new SurgaLights();
	private final SurgaProps props = new SurgaProps();
	private final SurgaDraw view = new SurgaDraw();
	private final SurgaChoreo now = new SurgaChoreo();
	private final SurgaChoreo query = new SurgaChoreo();

	private boolean prepared;
	private String beat = SurgaChoreo.PREVIEW_A;
	private int ticks = 20;
	private double fs = 1.0D;
	private long seed;
	private final double[] cross = new double[9];
	/** Scratch of the slab test (client thread only). */
	private final double[] range = new double[2];

	private final int[] groupQuads = new int[SurgaLayout.GROUPS];
	private final boolean[] granted = new boolean[SurgaLayout.GROUPS];
	private final boolean[] refused = new boolean[SurgaLayout.GROUPS];
	private double lastDoors;
	private int lastQuality = -1;
	private int lastQuads;
	private int lastInstances;
	private long lastNanos;

	/** The stage (contract C32.1); nothing is built until {@link #prepare}. */
	public static KosmikStage create() {
		return new SurgaStage();
	}

	private SurgaStage() {
	}

	@Override
	public Kosmik3D.StageId id() {
		return Kosmik3D.StageId.SURGA;
	}

	@Override
	public void prepare(StageContext ctx) {
		if (prepared) {
			return;
		}
		try {
			palace.load();
		} catch (RuntimeException exception) {
			// a broken model file leaves an empty palace; the sky, clouds and lights still play
			palace.release();
		}
		prepared = true;
	}

	@Override
	public boolean ready() {
		return prepared;
	}

	@Override
	public List<String> beats() {
		return BEATS;
	}

	@Override
	public void beat(String beat, StageContext ctx, int ticks, boolean continued) {
		this.beat = beat == null ? SurgaChoreo.PREVIEW_A : beat;
		this.ticks = Math.max(1, ticks);
		this.fs = ctx == null ? 1.0D : Math.max(0.5D, Math.min(5.0D, ctx.fs()));
		this.seed = ctx == null ? 0L : ctx.seed();
		SurgaChoreo.crossings(this.beat, this.ticks, fs, cross);
		sky.beat(ctx == null || ctx.colors() == null ? 0x79C05A : ctx.colors().grass(), seed);
		for (int i = 0; i < refused.length; i++) {
			refused[i] = false;
		}
	}

	@Override
	public boolean anchor(String name, double t, StageAnchor out) {
		if (name == null) {
			return false;
		}
		SurgaChoreo c = query;
		c.compute(beat, Double.isFinite(t) ? Math.max(0.0D, Math.min(ticks, t)) : 0.0D, ticks, fs, cross);
		switch (name) {
			case "gerbang" -> out.at(0.0D, SurgaLayout.GATE_Y, 0.0D, 1.0D);
			case "pintu" -> out.at(0.0D, SurgaLayout.GATE_Y, -1.0D, 1.0D);
			case "aula" -> out.at(0.0D, 12.0D, SurgaLayout.HALL_Z + 18.0D, 1.0D);
			case "tiang" -> out.at(c.px, c.py, c.pz, fs);
			case "javelin" -> out.at(c.jx, c.jy, c.jz, fs);
			case "segel" -> out.at(c.sx, c.sy + SurgaChoreo.SEAL_HEIGHT * 10.0D * fs * 0.5D, c.sz, fs);
			case "laut" -> out.at(0.0D, SurgaLayout.SEA_Y, SurgaLayout.COLUMN_Z, 1.0D);
			case "lembah" -> out.at(0.0D, SurgaLayout.LAND_Y, SurgaLayout.COLUMN_Z, 1.0D);
			default -> {
				if (name.length() == 7 && name.startsWith("cincin")) {
					int k = name.charAt(6) - '0';
					if (k >= 1 && k <= 9) {
						out.at(0.0D, SurgaLayout.ringY(k), SurgaLayout.COLUMN_Z, 1.0D);
						return true;
					}
				}
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean occluded(double px, double py, double pz, double ex, double ey, double ez) {
		// the closed doors (shut below about 35°), the gate's frame and roof, the platform, the court, the hall
		if (lastDoors < 35.0D && segmentHits(ex, ey, ez, px, py, pz, -13.0D, 0.0D, -1.0D, 13.0D, 60.0D, 1.0D)) {
			return true;
		}
		return segmentHits(ex, ey, ez, px, py, pz, -56.0D, 0.0D, -5.0D, -13.0D, 110.0D, 5.0D)
				|| segmentHits(ex, ey, ez, px, py, pz, 13.0D, 0.0D, -5.0D, 56.0D, 110.0D, 5.0D)
				|| segmentHits(ex, ey, ez, px, py, pz, -13.0D, 60.0D, -5.0D, 13.0D, 110.0D, 5.0D)
				|| segmentHits(ex, ey, ez, px, py, pz, -56.0D, -12.0D, -16.0D, 56.0D, 0.0D, 16.0D)
				|| segmentHits(ex, ey, ez, px, py, pz, -40.0D, -14.0D, 16.0D, 40.0D, 0.0D, SurgaLayout.HALL_Z)
				|| segmentHits(ex, ey, ez, px, py, pz, -50.0D, 0.0D, SurgaLayout.HALL_Z, 50.0D, 66.0D, SurgaLayout.HALL_Z + 96.0D);
	}

	/** Segment E→P (up to just short of P) against an axis-aligned box: the slab test, no allocation. */
	private boolean segmentHits(double ex, double ey, double ez, double px, double py, double pz, double x0, double y0, double z0, double x1,
								double y1, double z1) {
		range[0] = 0.0D;
		range[1] = 0.98D;
		return slab(ex, px - ex, x0, x1, range) && slab(ey, py - ey, y0, y1, range) && slab(ez, pz - ez, z0, z1, range);
	}

	/** One axis of the slab test: narrows [range0, range1] of the segment parameter; false = no overlap. */
	private static boolean slab(double origin, double delta, double lo, double hi, double[] range) {
		if (Math.abs(delta) < 1.0E-9D) {
			return origin >= lo && origin <= hi;
		}
		double a = (lo - origin) / delta;
		double b = (hi - origin) / delta;
		if (a > b) {
			double s = a;
			a = b;
			b = s;
		}
		range[0] = Math.max(range[0], a);
		range[1] = Math.min(range[1], b);
		return range[0] <= range[1];
	}

	@Override
	public void render(StageFrame frame) {
		long start = System.nanoTime();
		int quality = Math.max(0, Math.min(2, frame.quality));
		StageContext ctx = frame.ctx;
		boolean ringan = ctx != null && ctx.ringan();
		boolean pack = frame.pack;
		double time = Double.isFinite(frame.time) ? frame.time : 0.0D;
		lastQuality = quality;

		now.compute(beat, time, ticks, fs, cross);
		lastDoors = now.doors;
		view.begin(frame);

		// the backdrop first: the heavenly sky (its quads are taken by the dome itself)
		frame.dome(SurgaLayout.SKY[quality], 0.0F);

		// plan every group, then ask the budget in priority order
		for (int i = 0; i < groupQuads.length; i++) {
			groupQuads[i] = 0;
		}
		palace.plan(frame, view, ringan, pack, now.doors, groupQuads);
		groupQuads[SurgaLayout.G_PROPS] += props.count(now, view, fs);
		groupQuads[SurgaLayout.G_CLOUD] += sky.countClouds(frame, view, quality, pack, time);
		groupQuads[SurgaLayout.G_RINGS] += lights.countRings(frame, view, quality, pack, time);
		groupQuads[SurgaLayout.G_SHAFTS] += lights.countShafts(frame, view, quality, pack, now.shafts, now.gateLight);
		groupQuads[SurgaLayout.G_FALLS] += lights.countFalls(view, quality);
		groupQuads[SurgaLayout.G_PETALS] += sky.countPetals(frame, view, quality, ringan, pack, now.petals, time);
		int quads = 0;
		for (int g = 0; g < SurgaLayout.GROUPS; g++) {
			if (refused[g]) {
				granted[g] = false;
				continue;
			}
			granted[g] = frame.take(groupQuads[g]);
			if (granted[g]) {
				quads += groupQuads[g];
			} else if (groupQuads[g] > 0) {
				refused[g] = true;
			}
		}

		// models (cutout, depth write) and the item models
		float light = (float) (0.94D + 0.08D * now.gateLight);
		palace.draw(frame, granted, light);
		if (granted[SurgaLayout.G_PROPS]) {
			props.drawItems(frame, now, fs);
		}
		// soft layers far to near: land, sea, bank, puffs; petals last
		if (granted[SurgaLayout.G_CLOUD]) {
			sky.drawClouds(frame, view, quality, pack, time);
		}
		if (granted[SurgaLayout.G_PETALS]) {
			sky.drawPetals(frame, view, quality, ringan, pack, now.petals, time);
		}
		// additive light far to near by layer: rings, waterfalls, gate glow and shafts, the props' light
		if (granted[SurgaLayout.G_RINGS]) {
			lights.drawRings(frame, view, quality, pack, time, now.ringBoost);
		}
		if (granted[SurgaLayout.G_FALLS]) {
			lights.drawFalls(frame, view, quality, time);
		}
		if (granted[SurgaLayout.G_SHAFTS]) {
			lights.drawShafts(frame, view, quality, pack, now.shafts, now.gateLight, time, seed);
		}
		if (granted[SurgaLayout.G_PROPS]) {
			props.drawLight(frame, view, now, fs, time);
		}
		lastQuads = quads;
		lastInstances = palace.drawn(granted);
		lastNanos = System.nanoTime() - start;
	}

	@Override
	public void end() {
		for (int i = 0; i < refused.length; i++) {
			refused[i] = false;
		}
		lastDoors = 0.0D;
	}

	@Override
	public void release() {
		palace.release();
		prepared = false;
	}

	@Override
	public List<String> info() {
		List<String> lines = new ArrayList<>();
		lines.add(String.format(Locale.ROOT, "heaven: prepared=%s models=%d/%d beat=%s ticks=%d fs=%.2f", prepared, palace.loadedModels(),
				SurgaLayout.INSTANCES.length, beat, ticks, fs));
		lines.add(String.format(Locale.ROOT, "heaven last frame: quality=%d quads=%d instances=%d cpu=%.2f ms", lastQuality, lastQuads, lastInstances,
				lastNanos / 1_000_000.0D));
		StringBuilder groups = new StringBuilder("heaven groups:");
		for (int g = 0; g < SurgaLayout.GROUPS; g++) {
			groups.append(' ').append(SurgaLayout.GROUP_NAMES[g]).append('=').append(groupQuads[g]);
			if (refused[g]) {
				groups.append("(refused)");
			}
		}
		lines.add(groups.toString());
		return lines;
	}
}
