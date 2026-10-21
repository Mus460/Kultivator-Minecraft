package dev.kultivator.client.epic.kosmik.orbit;

import dev.kultivator.client.epic.kosmik.Kosmik3D;
import dev.kultivator.client.epic.kosmik.KosmikStage;
import dev.kultivator.client.epic.kosmik.StageAnchor;
import dev.kultivator.client.epic.kosmik.StageContext;
import dev.kultivator.client.epic.kosmik.StageFrame;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Orbit stage of Film Kosmik 3D (DESIGN 32.2, module O): this world seen from space. A planet whose land around home
 * is recoloured from the caster's biome ({@link PlanetBake}), its night lights, a cloud shell turning faster than the
 * ground, the atmosphere (fresnel shells and the limb halo), the moon with craters, the sun with its corona, the bright
 * stars and the Milky Way dome, and the props of the decree beats: the Heaven-Splitting Sword over the planet, its dive
 * and burning re-entry, the seven Northern Dipper cores and their beams, Gunung's lifted land.
 * <p>
 * Presentation only, for the caster's own client: nothing here touches a block, an entity, the weather, the time or the
 * tick rate; the sun and the moon are drawn. Layers ask the budget in the priority order of 32.2.1 (dome, planet, sun,
 * limb, props, moon, atmosphere, clouds, night lights, local patch, bright stars); a layer the budget refuses stays off
 * for the rest of the shot (no flicker at the cap). Additive layers write depth, so their sinks are opened far to near
 * (sky, night side, props, limb halo, atmosphere shells).
 */
public final class OrbitStage implements KosmikStage {

	private static final List<String> BEATS = List.of(OrbitBeats.IDS);
	private static boolean tickHooked;

	private final OrbitBeats beats = new OrbitBeats();
	private final OrbitBodies bodies = new OrbitBodies(beats);
	private final OrbitProps props = new OrbitProps(beats);
	private boolean ready;
	/** Layers the budget refused earlier in this shot stay off until the next shot (no flicker at the cap). */
	private int off;
	private String lastBeat = "-";
	private static final int PLANET = 1;
	private static final int SUN = 1 << 1;
	private static final int LIMB = 1 << 2;
	private static final int MOON = 1 << 3;
	private static final int ATMOSPHERE = 1 << 4;
	private static final int CLOUDS = 1 << 5;
	private static final int NIGHT = 1 << 6;
	private static final int PATCH = 1 << 7;
	private static final int STARS = 1 << 8;
	private int lastQuads;
	private float lastMs;

	/** The stage (from {@code Kosmik3D}); hooks the planet bake's client tick once (idle cost: one int compare). */
	public static KosmikStage create() {
		if (!tickHooked) {
			tickHooked = true;
			ClientTickEvents.END_CLIENT_TICK.register(client -> PlanetBake.tick());
		}
		return new OrbitStage();
	}

	private OrbitStage() {
	}

	@Override
	public Kosmik3D.StageId id() {
		return Kosmik3D.StageId.ORBIT;
	}

	@Override
	public void prepare(StageContext ctx) {
		bodies.prepare();
		ready = true;
		if (ctx != null) {
			PlanetBake.request(ctx);
		}
	}

	@Override
	public boolean ready() {
		return ready;
	}

	@Override
	public List<String> beats() {
		return BEATS;
	}

	@Override
	public void beat(String beat, StageContext ctx, int ticks, boolean continued) {
		beats.start(OrbitBeats.indexOf(beat), ctx, ticks, continued);
		bodies.latch();
		props.reset();
		off = 0;
		lastBeat = beat;
	}

	@Override
	public boolean anchor(String name, double t, StageAnchor out) {
		beats.at(t);
		return beats.anchor(name, out);
	}

	@Override
	public boolean occluded(double px, double py, double pz, double ex, double ey, double ez) {
		return OrbitBeats.occluded(px, py, pz, ex, ey, ez);
	}

	@Override
	public void render(StageFrame frame) {
		if (!ready) {
			return;
		}
		long start = System.nanoTime();
		beats.at(frame.time);
		int q = beats.quality;
		boolean pack = beats.pack;
		// additive sinks write depth wherever their texture shows: open them far to near (the sky, the night side, the props,
		// the limb halo on the silhouette plane, then the atmosphere shells that lie in front of it)
		frame.add(OrbitTex.SUN_CORONA);
		frame.add(OrbitTex.SUN_CORE);
		frame.add(OrbitTex.STAR_SPIKE);
		if (q >= 1 && !pack) {
			frame.add(OrbitTex.PLANET_NIGHT[q]);
		}
		frame.add(OrbitTex.RUNE_RING);
		frame.add(OrbitTex.PLASMA);
		frame.add(OrbitTex.TRAIL);
		frame.add(OrbitTex.BEAM);
		frame.add(OrbitTex.STAR_LINE);
		frame.add(OrbitTex.LIMB_GLOW);
		frame.add(OrbitTex.ATMO);
		// translucent sinks: the cloud shell (far) before the local decal and decks
		if (q >= 1 && !pack) {
			frame.glow(OrbitTex.CLOUDS[q]);
		}
		frame.glow(OrbitTex.PATCH_LAND[q]);
		// the backdrop first: black space, soft stars and the Milky Way band
		frame.dome(OrbitTex.SPACE[q], 0.0F);
		// the priority order of 32.2.1; a layer refused once stays off for the rest of the shot
		if ((off & PLANET) == 0 && bodies.planet(frame) < 0) {
			off |= PLANET;
		}
		if ((off & SUN) == 0 && bodies.sun(frame)) {
			off |= SUN;
		}
		if ((off & LIMB) == 0 && bodies.limb(frame)) {
			off |= LIMB;
		}
		props.render(frame);
		if ((off & MOON) == 0 && bodies.moon(frame)) {
			off |= MOON;
		}
		if ((off & ATMOSPHERE) == 0 && bodies.atmosphere(frame)) {
			off |= ATMOSPHERE;
		}
		if ((off & CLOUDS) == 0 && bodies.clouds(frame)) {
			off |= CLOUDS;
		}
		if ((off & NIGHT) == 0 && bodies.nightLights(frame)) {
			off |= NIGHT;
		}
		if ((off & PATCH) == 0 && bodies.patch(frame)) {
			off |= PATCH;
		}
		if ((off & STARS) == 0 && bodies.stars(frame)) {
			off |= STARS;
		}
		lastQuads = frame.quadsWritten();
		lastMs = (System.nanoTime() - start) / 1_000_000.0F;
	}

	@Override
	public void end() {
		lastBeat = "-";
	}

	@Override
	public void release() {
		PlanetBake.release();
	}

	@Override
	public List<String> info() {
		List<String> lines = new ArrayList<>();
		lines.add(String.format(Locale.ROOT, "orbit: beat=%s quality=%d pack=%b tier=%d fs=%.2f home=%.0fN", lastBeat, beats.quality, beats.pack,
				beats.tier, beats.fs, OrbitBeats.homeLatitude(beats.colors)));
		lines.add(bodies.gridInfo() + ", " + PlanetBake.info());
		lines.add(String.format(Locale.ROOT, "last orbit frame: %d quads, %.2f ms; refused this shot: layers %s, props %s", lastQuads, lastMs,
				Integer.toBinaryString(off), Integer.toBinaryString(props.off())));
		return lines;
	}
}
