package dev.kultivator.client.anim.model;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.film.FilmDirector;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The 3D choreography of the Sword, Lightning, Ice and Space Dao ultimates (DESIGN 30.4.2-30.4.5, module U1), the same
 * shape as {@link FireUltimates}: one clock (client ticks, paused with the game, held by a hit-stop), timed beats on the
 * server's events with clear build-up, release, impact and aftermath, and no random size change anywhere: props grow in
 * once, unfold or fold by eased tilts, turn at a steady speed, breathe by light and leave by one fade, one SHATTER or one
 * DISSOLVE. Positions and counts come from the event and its seed. Props that move along a path (the formation swords,
 * the gate doors, the punishing sword, the phoenix and its wings, the clock and its hands, the debris) are driven here
 * every tick ({@code moveTo}/{@code rotateTo} with a one-tick ease, keyed by {@link ModelFx} through {@code DisplayKey});
 * every other prop runs on its own animation steps.
 * <ul>
 * <li><b>Formasi Pedang Zhuxian</b>: plinths rise at the four corners, the four swords fall onto them point down, the
 * formation map unfolds, light seams join the corners; each pulse runs a light wave round the seams, each corner pillar
 * lifts and slams its sword; at the release the swords tilt inward and the sky sword descends, at the climax it lands in
 * the centre and the map shatters.</li>
 * <li><b>Seni Pedang Hukuman Langit</b>: the Southern Heavenly Gate opens in the sky, chains of law fall on the stun nova,
 * the sword slides out of the gate with its rune halo, turns to the beam and rides it to its end.</li>
 * <li><b>Formasi Kaisar Petir Kesengsaraan</b>: the Luo Shu grid unfolds, the whirlpool arcs gather, nine drums rise;
 * each strike lights the next palace (1-4, 6-9), the ninth lands on palace 5.</li>
 * <li><b>Tianzun Guntur Sembilan Langit</b>: the black qilin rears behind the caster, the gold whip appears, nine sky rings
 * stack up; each nova lights two rings, the deities strike where the phantoms strike, the whip comes down at the
 * climax.</li>
 * <li><b>Turunnya Phoenix Es</b>: the phoenix forms and circles, dives onto each dense point (six-fold ring, quills),
 * climbs back, spreads over the caster and shatters at the nova.</li>
 * <li><b>Membekukan Gunung Qi</b>: the icicle crown, a crystal peak per pulse on the rim, flakes (and at Maximal ice
 * coffins) on the mass freeze, the peaks crack and burst inward at the recall.</li>
 * <li><b>Cermin Hampa</b>: the mirror rises from the palm, ripples and stores a shard per blow, tilts to the aim and
 * recoils on the release.</li>
 * <li><b>Wilayah Roh Waktu</b>: the clock unfolds, gears and sand stop dead, the hands crawl backwards, droplets hang,
 * the hourglass flips, the hands race forward and the gears burst at the release.</li>
 * <li><b>Lubang Hitam</b>: a young star collapses into the black core, the accretion disc unfolds and speeds up with every
 * pulse, debris spirals in, the jets fire at the collapse.</li>
 * </ul>
 * Budgets: every prop through {@link ModelFx} (its per-tick and alive caps; CORE for the identity prop of each beat,
 * DETAIL for the rest; Minimal or a loaded server keeps only identity props), every particle through {@link FxBudget}
 * (DETAIL), counts scaled by {@link EffectQuality}; others' casts follow "Efek pemain lain". Each run reports its props
 * to the film ({@link FilmDirector#track}) and its beats ({@link FilmDirector#cue}); the choreography never depends on
 * whether a film plays.
 */
public final class UltimatesA {

	private static final Set<String> SPECS = Set.of("zhuxian_sword_formation", "heaven_punishing_sword", "tribulation_thunder_emperor_array",
			"nine_heavens_thunder_venerable", "ice_phoenix_descent", "freeze_mount_qi", "void_mirror", "time_spirit_domain", "stellar_black_hole");

	private static final List<Run> RUNS = new ArrayList<>();
	private static long clock;
	private static int warnings;

	// ================================================================== API (DESIGN 30.5 C30.1)

	/** The spec ids this driver owns. */
	public static boolean handles(String specId) {
		return specId != null && SPECS.contains(specId);
	}

	/** Every phase of an owned spec (ABORT too). */
	public static void onTechFx(Payloads.TechFx p, TechniqueSpec spec, Phase phase, @Nullable Entity caster) {
		try {
			String id = spec.id();
			if (!handles(id)) {
				return;
			}
			if (phase == Phase.ABORT) {
				abort(p.caster());
				return;
			}
			if (caster != null && caster != Minecraft.getInstance().player && caster instanceof Player && EffectQuality.othersScale() < 0.5F) {
				return;
			}
			ClientLevel level = Minecraft.getInstance().level;
			if (level == null) {
				return;
			}
			Ev ev = new Ev(p, spec, phase, caster);
			switch (id) {
				case "zhuxian_sword_formation" -> zhuxian(ev, level);
				case "heaven_punishing_sword" -> punishing(ev, level);
				case "tribulation_thunder_emperor_array" -> tribulation(ev, level);
				case "nine_heavens_thunder_venerable" -> venerable(ev, level);
				case "ice_phoenix_descent" -> phoenix(ev, level);
				case "freeze_mount_qi" -> frozen(ev, level);
				case "void_mirror" -> mirror(ev);
				case "time_spirit_domain" -> time(ev);
				case "stellar_black_hole" -> blackHole(ev);
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			if (warnings++ < 5) {
				Kultivator.LOGGER.warn("Kultivator: ultimate choreography error for {}", spec.id(), exception);
			}
		}
	}

	/** Once per unpaused client tick (AnimClient, after FireUltimates). */
	public static void tick(ClientLevel level) {
		if (dev.kultivator.client.epic.EpicClock.frozen() && !RUNS.isEmpty()) {
			// a hit-stop holds the choreography's clock (σ = 0) like the props it moves
			return;
		}
		for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
			Run run = it.next();
			boolean keep;
			try {
				keep = run.tick(level);
			} catch (RuntimeException exception) {
				keep = false;
				if (warnings++ < 5) {
					Kultivator.LOGGER.warn("Kultivator: ultimate choreography error", exception);
				}
			}
			if (!keep) {
				run.endProps(6);
				it.remove();
			}
		}
		clock++;
	}

	/** Ends every run of a caster (idempotent; props fade over 6 ticks). */
	public static void abort(int casterId) {
		for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
			Run run = it.next();
			if (run.casterId == casterId) {
				run.endProps(6);
				it.remove();
			}
		}
	}

	/** Drops every run (world change, disconnect; the props themselves are cleared by ModelFx). */
	public static void clear() {
		for (Run run : RUNS) {
			run.untrack();
		}
		RUNS.clear();
	}

	/** The Info rows of this module's models (scale, axis, glow, particle hint colours, lowest point, parts). */
	public static void models(FxModels.Sink sink) {
		FxModels.Axis up = FxModels.Axis.UP;
		FxModels.Axis north = FxModels.Axis.NORTH;
		// Sword: Zhuxian (swords: grip at the anchor, blade up; standing props rise from the anchor)
		sink.put("zx_sword_zhu", 8.0F, up, true, 0xB0102A, 0xFFFFFF, -0.4F);
		sink.put("zx_sword_lu", 8.0F, up, true, 0x6CFF8A, 0xFFFFFF, -0.4F);
		sink.put("zx_sword_xian", 8.0F, up, true, 0x9FF3FF, 0xFFFFFF, -0.4F);
		sink.put("zx_sword_jue", 8.0F, up, true, 0xB57CFF, 0xFFFFFF, -0.4F);
		sink.put("zx_plinth", 3.2F, north, false, 0x7D7566, 0xFFD54A, 0.0F);
		sink.put("zx_map", 24.0F, north, true, 0x400010, 0xFF3A4A, 0.0F);
		sink.put("zx_sky_sword", 14.0F, up, true, 0xE8ECF4, 0xB0102A, -0.47F);
		sink.put("zx_seam", 16.0F, north, true, 0xFF6A6A, 0xFFFFFF, -0.04F);
		// Sword: the heaven-punishing sword
		sink.put("hps_sword", 18.0F, up, true, 0xFFF1C0, 0xC7A2FF, -0.43F);
		sink.put("hps_gate", 20.0F, north, true, 0xB0201A, 0xFFD54A, -0.08F);
		sink.put("hps_gate_door_l", 20.0F, north, true, 0xB0201A, 0xFFD54A, 0.0F);
		sink.put("hps_gate_door_r", 20.0F, north, true, 0xB0201A, 0xFFD54A, 0.0F);
		sink.put("hps_halo", 22.0F, north, true, 0xFFFFFF, 0xFFE88A, -0.5F);
		sink.put("hps_chain", 2.0F, up, true, 0xFFF1C0, 0xC0182A, 0.0F);
		// Lightning: the tribulation array
		sink.put("tte_drum", 4.0F, north, false, 0xB0201A, 0xFFD54A, 0.0F);
		sink.put("tte_drum_lit", 4.0F, north, true, 0xE8D8FF, 0xB57CFF, 0.0F);
		sink.put("tte_grid", 20.0F, north, true, 0x3A2A50, 0xB57CFF, 0.0F);
		sink.put("tte_bolt", 6.0F, up, true, 0xF0E8FF, 0xB57CFF, 0.0F);
		sink.put("tte_vortex_rim", 16.0F, north, false, 0x4A4660, 0xB57CFF, -0.15F);
		// Lightning: the nine heavens (the qilin's tail lashes, the deity's hammer sways)
		sink.put("nhtv_ring", 12.0F, north, true, 0xFFD54A, 0xE8ECF4, -0.05F);
		sink.put("nhtv_ring_lit", 12.0F, north, true, 0xE8D8FF, 0xB57CFF, -0.05F);
		sink.put("nhtv_qilin", 6.0F, north, true, 0x1A1A22, 0xB57CFF, 0.0F,
				new FxModels.Part("nhtv_qilin_tail", 0.0F, 8.5F, 1.5F, Anim.SWING, 20.0F, 24.0F));
		sink.put("nhtv_qilin_tail", 6.0F, north, true, 0x1A1A22, 0xB57CFF, -0.05F);
		sink.put("nhtv_whip", 1.4F, up, true, 0xFFD54A, 0xFFFFFF, -0.25F);
		sink.put("nhtv_deity", 2.5F, north, true, 0x5A5A70, 0xC7A2FF, 0.0F,
				new FxModels.Part("nhtv_deity_hammer", 2.9F, 16.0F, 0.0F, Anim.SWING, 24.0F, 30.0F));
		sink.put("nhtv_deity_hammer", 2.5F, up, true, 0xFFD54A, 0xC7A2FF, -0.25F);
		// Ice: the phoenix
		sink.put("ipd_phoenix", 10.0F, north, true, 0xBFF6FF, 0xFFFFFF, -0.25F);
		sink.put("ipd_wing_l", 10.0F, north, true, 0xBFF6FF, 0xFFFFFF, -0.05F);
		sink.put("ipd_wing_r", 10.0F, north, true, 0xBFF6FF, 0xFFFFFF, -0.05F);
		sink.put("ipd_tail", 10.0F, north, true, 0xBFF6FF, 0x9FF3FF, -0.02F);
		sink.put("ipd_quill", 1.5F, north, true, 0xDFF6FF, 0xFFFFFF, -0.1F);
		sink.put("ipd_hexring", 6.0F, north, true, 0xDFF6FF, 0xFFFFFF, 0.0F);
		// Ice: the frozen mountain
		sink.put("fmq_peak", 8.0F, north, true, 0x9FF3FF, 0x3A6AC8, 0.0F);
		sink.put("fmq_peak_cracked", 8.0F, north, true, 0x9FF3FF, 0xFFFFFF, 0.0F);
		sink.put("fmq_crown", 3.0F, north, true, 0xDFF6FF, 0xFFFFFF, 0.0F);
		sink.put("fmq_coffin", 1.5F, north, true, 0xCFEFFF, 0xFFFFFF, 0.0F);
		sink.put("fmq_flake", 1.6F, north, true, 0xFFFFFF, 0xCFEFFF, 0.0F);
		// Space: the void mirror
		sink.put("vm_mirror", 3.2F, north, true, 0xA86A32, 0x7A4AFF, -0.4F);
		sink.put("vm_mirror_ripple", 3.2F, north, true, 0xC8B8FF, 0x7A4AFF, -0.4F);
		sink.put("vm_rune_ring", 3.2F, north, true, 0xC8B8FF, 0x7A4AFF, -0.75F);
		sink.put("vm_shard", 0.6F, north, true, 0x08060C, 0x7A4AFF, -0.2F);
		// Space: the time domain
		sink.put("tsd_clock", 6.0F, north, true, 0xF2C24A, 0xFFF4C8, -0.5F);
		sink.put("tsd_hand_long", 6.0F, north, true, 0x2A2A33, 0xF2C24A, -0.12F);
		sink.put("tsd_hand_short", 6.0F, north, true, 0x2A2A33, 0xF2C24A, -0.12F);
		sink.put("tsd_gear_l", 3.0F, north, true, 0xC8A040, 0xF2C24A, -0.4F);
		sink.put("tsd_gear_s", 1.5F, north, true, 0xC8A040, 0xF2C24A, -0.3F);
		sink.put("tsd_hourglass", 2.0F, north, true, 0x8A5A2A, 0xF0D080, -0.4F);
		sink.put("tsd_drop", 0.4F, north, true, 0xC8D8FF, 0xFFFFFF, -0.05F);
		// Space: the black hole
		sink.put("sbh_star", 3.0F, north, true, 0xFFFFF0, 0xFFD54A, -0.35F);
		sink.put("sbh_core", 4.0F, north, true, 0x08060C, 0xFFF0C0, -0.25F,
				new FxModels.Part("sbh_photon_ring", 0.0F, 0.0F, 0.0F, Anim.NONE, 0.0F, 0.0F));
		sink.put("sbh_photon_ring", 4.0F, north, true, 0xFFF0C0, 0xFFFFFF, -0.4F);
		sink.put("sbh_disk", 10.0F, north, true, 0xFF8A20, 0x7A3AC8, -0.02F);
		sink.put("sbh_jet", 12.0F, up, true, 0xC8E0FF, 0xFFFFFF, 0.0F);
		sink.put("sbh_debris", 0.8F, north, false, 0x5A5048, 0xFF8A20, -0.1F);
	}

	// ================================================================== the event view

	private record Ev(Payloads.TechFx p, TechniqueSpec spec, Phase phase, @Nullable Entity caster) {

		Vec3 pos() {
			return new Vec3(p.x(), p.y(), p.z());
		}

		Vec3 to() {
			return new Vec3(p.tx(), p.ty(), p.tz());
		}

		boolean hasTo() {
			return to().distanceToSqr(pos()) > 0.04D;
		}

		int casterId() {
			return p.caster();
		}

		int duration() {
			return Math.max(0, p.duration());
		}

		boolean preImpact() {
			return p.has(Payloads.TechFx.FLAG_PRE_IMPACT);
		}

		/** The ticks until a pre-impact visual lands (at least 4). */
		int fall() {
			return Math.max(4, p.duration() > 0 ? p.duration() : 12);
		}

		Vec3 casterPos() {
			return caster != null ? caster.position() : pos();
		}

		float yaw() {
			if (caster != null) {
				return caster.getYRot();
			}
			return hasTo() ? ModelFx.yawTo(pos(), to()) : 0.0F;
		}

		Vec3 forward() {
			float yaw = yaw() * Mth.DEG_TO_RAD;
			return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
		}

		boolean own() {
			return caster != null && caster == Minecraft.getInstance().player;
		}

		/** Minimal quality or a loaded server: identity props only. */
		boolean lean() {
			return EffectQuality.minimal() || p.has(Payloads.TechFx.FLAG_LOW_LOAD);
		}

		/** The spec's climax tick after the cast (its Dampak row), or {@code fallback}. */
		int climaxAt(int fallback) {
			Dampak dampak = spec.dampak();
			return dampak != null && dampak.climaxAt() > 0 ? dampak.climaxAt() : fallback;
		}

		/** A size factor for the caster's grade (like ArtModels): +12 % per tier above the third. */
		float grade() {
			return 1.0F + 0.12F * Math.max(0, p.gradeTier() - 2);
		}
	}

	private static <T extends Run> @Nullable T find(int casterId, Class<T> kind) {
		for (int i = RUNS.size() - 1; i >= 0; i--) {
			Run run = RUNS.get(i);
			if (run.casterId == casterId && kind.isInstance(run)) {
				return kind.cast(run);
			}
		}
		return null;
	}

	private static <T extends Run> T start(T run) {
		for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
			Run old = it.next();
			if (old.casterId == run.casterId && old.getClass() == run.getClass()) {
				old.endProps(6);
				it.remove();
			}
		}
		RUNS.add(run);
		return run;
	}

	/** Starts the spec's film for the own caster (DESIGN 30.5 C30.2); a refused film changes nothing here. */
	private static void film(Ev ev, Vec3 aim, int climaxIn, int duration) {
		if (!ev.own()) {
			return;
		}
		try {
			FilmDirector.play(ev.spec().id(), new FilmDirector.Ctx(ev.casterId(), ev.spec().id(), ev.casterPos(), aim, ev.yaw(), ev.p().seed(), climaxIn,
					duration));
		} catch (RuntimeException exception) {
			if (warnings++ < 5) {
				Kultivator.LOGGER.warn("Kultivator: film start failed for {}", ev.spec().id(), exception);
			}
		}
	}

	// ================================================================== dispatch per spec

	private static void zhuxian(Ev ev, ClientLevel level) {
		Phase phase = ev.phase();
		if (phase == Phase.CAST) {
			Zhuxian run = start(new Zhuxian(ev, level));
			film(ev, run.centre, run.climax, run.climax + 60);
			return;
		}
		Zhuxian run = find(ev.casterId(), Zhuxian.class);
		if (run == null) {
			return;
		}
		if (phase == Phase.PULSE) {
			run.pulse();
		} else if (phase == Phase.IMPACT && ev.p().of() == 4 && ev.p().index() >= 0) {
			run.pillar(ev.p().index());
		} else if (phase == Phase.EXTRA0) {
			run.climax(ev.pos());
		}
	}

	private static void punishing(Ev ev, ClientLevel level) {
		Phase phase = ev.phase();
		if (phase == Phase.CAST) {
			Punish run = start(new Punish(ev, level));
			film(ev, run.aim, run.climax, run.climax + 60);
			return;
		}
		Punish run = find(ev.casterId(), Punish.class);
		if (run == null) {
			return;
		}
		if (phase == Phase.IMPACT) {
			run.nova();
		} else if (phase == Phase.HIT) {
			run.hit(ev.pos());
		} else if (phase == Phase.TRAVEL && !ev.preImpact()) {
			run.travel(ev.pos(), ev.hasTo() ? ev.to() : ev.pos().add(ev.forward().scale(64.0D)), ev.duration(), ev.p().length());
		}
	}

	private static void tribulation(Ev ev, ClientLevel level) {
		Phase phase = ev.phase();
		if (phase == Phase.CAST) {
			Tribulation run = start(new Tribulation(ev, level));
			film(ev, run.centre, run.climax, run.climax + 60);
			return;
		}
		Tribulation run = find(ev.casterId(), Tribulation.class);
		if (run == null) {
			return;
		}
		if (phase == Phase.EXTRA0) {
			run.strike();
		} else if (phase == Phase.EXTRA1) {
			run.ninth();
		}
	}

	private static void venerable(Ev ev, ClientLevel level) {
		Phase phase = ev.phase();
		if (phase == Phase.CAST) {
			Venerable run = start(new Venerable(ev));
			film(ev, run.origin.add(0.0D, 24.0D, 0.0D), run.climax, run.climax + 60);
			return;
		}
		Venerable run = find(ev.casterId(), Venerable.class);
		if (run == null) {
			return;
		}
		if (phase == Phase.EXTRA0) {
			run.nova();
		} else if (phase == Phase.EXTRA1) {
			run.impact();
		} else if (phase == Phase.PHANTOM) {
			run.phantom(ev.p().index(), ev.pos(), ev.to(), !ev.preImpact());
		}
	}

	private static void phoenix(Ev ev, ClientLevel level) {
		Phase phase = ev.phase();
		if (phase == Phase.CAST) {
			Phoenix run = start(new Phoenix(ev));
			film(ev, run.home, run.climax, run.climax + 60);
			return;
		}
		Phoenix run = find(ev.casterId(), Phoenix.class);
		if (phase == Phase.EXTRA0) {
			if (run == null) {
				// the cast was not seen (joined late): the nova's rings only
				run = start(new Phoenix(ev));
				run.formed = false;
			}
			run.nova(ev.pos());
			return;
		}
		if (run == null) {
			return;
		}
		if (phase == Phase.TRAVEL && ev.hasTo()) {
			// the band front from the sky: pos = high over the line's start (aux = that height x 100), to = the line's far end,
			// duration = the sweep's ticks; the bird dives onto the start and skims the line to its end
			double sky = ev.p().aux() > 0 ? ev.p().aux() / 100.0D : 0.0D;
			Vec3 land = sky > 0.0D ? ev.pos().add(0.0D, -sky, 0.0D) : ev.to();
			run.dive(land, sky > 0.0D ? ev.to() : land, ev.fall());
		} else if (phase == Phase.IMPACT) {
			run.landed(ev.pos());
		}
	}

	private static void frozen(Ev ev, ClientLevel level) {
		Phase phase = ev.phase();
		if (phase == Phase.CAST) {
			FrozenMount run = start(new FrozenMount(ev, level));
			film(ev, run.origin, run.climax, run.climax + 60);
			return;
		}
		FrozenMount run = find(ev.casterId(), FrozenMount.class);
		if (run == null) {
			return;
		}
		if (phase == Phase.PULSE) {
			run.pulse();
		} else if (phase == Phase.EXTRA0) {
			run.freeze();
		} else if (phase == Phase.HIT) {
			run.hit(ev.p().target(), ev.pos());
		} else if (phase == Phase.EXTRA1) {
			run.recall();
		}
	}

	private static void mirror(Ev ev) {
		Phase phase = ev.phase();
		if (phase == Phase.CAST) {
			Mirror run = start(new Mirror(ev));
			film(ev, ev.casterPos().add(ev.forward().scale(16.0D)), -1, 200);
			return;
		}
		Mirror run = find(ev.casterId(), Mirror.class);
		if (run == null) {
			return;
		}
		if (phase == Phase.EXTRA6 || phase == Phase.EXTRA7) {
			run.blow();
		} else if (phase == Phase.EXTRA0) {
			run.release(ev.hasTo() ? ev.to() : ev.pos());
		}
	}

	private static void time(Ev ev) {
		Phase phase = ev.phase();
		if (phase == Phase.CAST) {
			TimeDomain run = start(new TimeDomain(ev));
			film(ev, run.origin, run.climax, run.climax + 40);
			return;
		}
		TimeDomain run = find(ev.casterId(), TimeDomain.class);
		if (run == null) {
			return;
		}
		if (phase == Phase.HIT) {
			run.hit(ev.pos());
		} else if (phase == Phase.EXTRA0) {
			run.resume();
		}
	}

	private static void blackHole(Ev ev) {
		Phase phase = ev.phase();
		if (phase == Phase.CAST) {
			BlackHole run = start(new BlackHole(ev));
			film(ev, run.core, run.climax, run.climax + 50);
			return;
		}
		BlackHole run = find(ev.casterId(), BlackHole.class);
		if (run == null) {
			return;
		}
		if (phase == Phase.PULSE) {
			run.pulse();
		} else if (phase == Phase.EXTRA0) {
			run.collapse();
		}
	}

	// ================================================================== a run: timed beats, props, tracks

	private abstract static class Run {
		final int casterId;
		final long start = clock;
		final boolean own;
		final boolean lean;
		final boolean max;
		final long seed;
		final @Nullable Entity caster;
		private final List<Beat> beats = new ArrayList<>();
		final List<ModelHandle> props = new ArrayList<>();
		private final List<String> keys = new ArrayList<>(4);

		Run(Ev ev) {
			this.casterId = ev.casterId();
			this.own = ev.own();
			this.lean = ev.lean();
			this.max = EffectQuality.maximal() && !this.lean;
			this.seed = ev.p().seed();
			this.caster = ev.caster();
		}

		int age() {
			return (int) (clock - start);
		}

		/** Runs {@code action} at run tick {@code at} (at the next pass when it is already past). */
		void at(int at, Runnable action) {
			beats.add(new Beat(at, action));
		}

		boolean tick(ClientLevel level) {
			int age = age();
			for (int i = 0; i < beats.size(); i++) {
				Beat beat = beats.get(i);
				if (!beat.done && beat.at <= age) {
					beat.done = true;
					beat.action.run();
				}
			}
			beats.removeIf(beat -> beat.done);
			drive(level, age);
			emit(level, age);
			props.removeIf(handle -> !handle.alive());
			boolean keep = !finished(age);
			if (!keep) {
				untrack();
			}
			return keep;
		}

		/** Poses the driven props of this tick. */
		void drive(ClientLevel level, int age) {
		}

		abstract void emit(ClientLevel level, int age);

		abstract boolean finished(int age);

		/** A prop at {@code pos} (CORE for identity props, DETAIL for the rest). */
		@Nullable ModelHandle show(String id, Vec3 pos, float scale, float yaw, float pitch, float roll, int ticks, boolean core, AnimStep... anims) {
			ModelHandle handle = ModelFx.show(new ModelRequest(id, pos, scale, yaw, pitch, roll, Math.max(1, ticks), List.of(anims), -1, Vec3.ZERO, false,
					FxModels.info(id).glow(), casterId, core ? FxLayer.Priority.CORE : FxLayer.Priority.DETAIL));
			if (handle != null) {
				props.add(handle);
			}
			return handle;
		}

		/** A prop riding {@code entity} at {@code offset} (x left, y up, z forward, turned with its body). */
		@Nullable ModelHandle ride(String id, Entity entity, Vec3 offset, float scale, float yaw, float pitch, int ticks, boolean core, AnimStep... anims) {
			ModelHandle handle = ModelFx.show(new ModelRequest(id, entity.position(), scale, yaw, pitch, 0.0F, Math.max(1, ticks), List.of(anims), entity.getId(),
					offset, true, FxModels.info(id).glow(), casterId, core ? FxLayer.Priority.CORE : FxLayer.Priority.DETAIL));
			if (handle != null) {
				props.add(handle);
			}
			return handle;
		}

		/** A driven prop: posed every tick by the run, its pose also the film's track. */
		Rig rig(String id, Vec3 pos, float scale, float yaw, float pitch, float roll, int ticks, boolean core, AnimStep... anims) {
			return new Rig(show(id, pos, scale, yaw, pitch, roll, ticks, core, anims), pos, yaw, pitch);
		}

		void track(String key, FilmDirector.Track track) {
			if (!own) {
				return;
			}
			keys.add(key);
			try {
				FilmDirector.track(casterId, key, track);
			} catch (RuntimeException ignored) {
				// the film is presentation only
			}
		}

		void cue(String beat, Vec3 at) {
			if (!own) {
				return;
			}
			try {
				FilmDirector.cue(casterId, beat, at);
			} catch (RuntimeException ignored) {
				// the film is presentation only
			}
		}

		void untrack() {
			if (keys.isEmpty()) {
				return;
			}
			for (String key : keys) {
				try {
					FilmDirector.track(casterId, key, null);
				} catch (RuntimeException ignored) {
					// the film is presentation only
				}
			}
			keys.clear();
		}

		void endProps(int fade) {
			for (ModelHandle handle : props) {
				handle.end(fade);
			}
			props.clear();
			untrack();
		}

		/** Ends {@code handle} at run tick {@code at} with a fade. */
		void endAt(@Nullable ModelHandle handle, int at, int fade) {
			if (handle != null) {
				at(at, () -> handle.end(fade));
			}
		}

		/** The caster's body yaw (degrees), or {@code fallback} when it is gone. */
		float bodyYaw(float fallback) {
			if (caster == null || caster.isRemoved()) {
				return fallback;
			}
			return caster instanceof LivingEntity living ? living.yBodyRot : caster.getYRot();
		}

		boolean casterHere() {
			return caster != null && !caster.isRemoved();
		}
	}

	private static final class Beat {
		final int at;
		final Runnable action;
		boolean done;

		Beat(int at, Runnable action) {
			this.at = at;
			this.action = action;
		}
	}

	/** A pose remembered over two ticks: the film's track of a prop (interpolated by the frame's partial tick). */
	private static final class Pose3 implements FilmDirector.Track {
		Vec3 prev;
		Vec3 cur;
		float prevYaw;
		float yaw;
		float prevPitch;
		float pitch;
		@Nullable ModelHandle handle;
		boolean ended;

		Pose3(Vec3 at, float yaw, float pitch) {
			this.prev = this.cur = at;
			this.prevYaw = this.yaw = yaw;
			this.prevPitch = this.pitch = pitch;
		}

		void set(Vec3 at, float newYaw, float newPitch) {
			prev = cur;
			prevYaw = yaw;
			prevPitch = pitch;
			cur = at;
			yaw = newYaw;
			pitch = newPitch;
		}

		@Override
		public Vec3 pos(float partial) {
			return prev.lerp(cur, Mth.clamp(partial, 0.0F, 1.0F));
		}

		@Override
		public float yaw(float partial) {
			return Mth.rotLerp(Mth.clamp(partial, 0.0F, 1.0F), prevYaw, yaw);
		}

		@Override
		public float pitch(float partial) {
			return Mth.lerp(Mth.clamp(partial, 0.0F, 1.0F), prevPitch, pitch);
		}

		@Override
		public boolean alive() {
			return !ended && (handle == null || handle.alive());
		}
	}

	/** A driven prop and its pose. */
	private static final class Rig {
		final @Nullable ModelHandle handle;
		final Pose3 pose;

		Rig(@Nullable ModelHandle handle, Vec3 at, float yaw, float pitch) {
			this.handle = handle;
			this.pose = new Pose3(at, yaw, pitch);
			this.pose.handle = handle;
		}

		void place(Vec3 at, float yaw, float pitch, float roll) {
			if (!finite(at)) {
				return;
			}
			pose.set(at, yaw, pitch);
			if (handle != null && handle.alive()) {
				handle.moveTo(at, 1);
				handle.rotateTo(yaw, pitch, roll, 1);
			}
		}

		boolean alive() {
			return handle != null && handle.alive();
		}

		void play(AnimStep step) {
			if (handle != null) {
				handle.play(step);
			}
		}

		void end(int fade) {
			pose.ended = true;
			if (handle != null) {
				handle.end(fade);
			}
		}
	}

	// ================================================================== Formasi Pedang Zhuxian

	private static final class Zhuxian extends Run {
		private static final String[] SWORDS = {"zx_sword_zhu", "zx_sword_lu", "zx_sword_xian", "zx_sword_jue"};
		private static final double[][] SIGNS = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
		/** The bell cues of the spec ring at 0/6/12/18; each sword appears on its bell and lands 4 ticks later. */
		private static final int FALL = 4;
		private static final float PLINTH = 3.2F;

		final Vec3 centre;
		final float radius;
		final int climax;
		final float swordScale;
		final float skyScale;
		final Vec3[] corner = new Vec3[4];
		final float[] cornerYaw = new float[4];
		final double[] rest = new double[4];
		final Rig[] swords = new Rig[4];
		final int[] swordAt = {-1, -1, -1, -1};
		final int[] liftAt = {-100, -100, -100, -100};
		final ModelHandle[] seams = new ModelHandle[4];
		final ModelHandle[] plinths = new ModelHandle[4];
		final double groundY;
		@Nullable ModelHandle map;
		@Nullable Rig sky;
		double skyFrom;
		double skyTo;
		int releaseAt = -1;
		int fallAt = -1;
		int fallTicks = 10;
		int landedAt = -1;
		int pulses;

		Zhuxian(Ev ev, ClientLevel level) {
			super(ev);
			this.centre = ev.pos();
			this.radius = Mth.clamp(ev.p().radius() > 1.0F ? ev.p().radius() : 24.0F, 12.0F, 40.0F);
			this.climax = ev.climaxAt(240);
			this.swordScale = Mth.clamp(radius * 0.33F, 6.0F, 10.0F) * (lean ? 0.85F : 1.0F);
			this.skyScale = Mth.clamp(radius * 0.55F, 10.0F, 16.0F);
			this.groundY = ground(level, centre.x, centre.z, centre.y);
			for (int i = 0; i < 4; i++) {
				double x = centre.x + SIGNS[i][0] * radius;
				double z = centre.z + SIGNS[i][1] * radius;
				corner[i] = new Vec3(x, ground(level, x, z, centre.y), z);
				cornerYaw[i] = ModelFx.yawTo(corner[i], centre);
				// the tip (1.5 x scale below the grip when point down) rests 0.6 b deep in the socket
				double top = lean ? corner[i].y - 0.2D : corner[i].y + 0.625D * PLINTH - 0.6D;
				rest[i] = top + 1.5D * swordScale;
			}
			for (int i = 0; i < 4; i++) {
				int k = i;
				if (!lean) {
					at(0, () -> plinth(k));
				}
				at(6 * k, () -> sword(k));
				at(6 * k + FALL, () -> landed(k));
			}
			if (!lean) {
				at(4, this::map);
				for (int i = 0; i < 4; i++) {
					int k = i;
					at(20 + 8 * k, () -> seam(k));
				}
			}
			at(40, () -> {
				for (int i = 0; i < 4; i++) {
					if (swords[i] != null && swords[i].handle != null) {
						swords[i].handle.lightCycle(9, 60, 15 * i);
					}
				}
			});
			at(climax - 20, this::release);
			at(climax - 10, () -> fall(10));
		}

		private void plinth(int k) {
			plinths[k] = show("zx_plinth", corner[k], PLINTH, cornerYaw[k], 0.0F, 0.0F, climax + 40, false, step(Anim.RISE, 0, 10, 3.0F, 0.0F));
		}

		private void sword(int k) {
			swordAt[k] = age();
			Vec3 at = new Vec3(corner[k].x, rest[k] + 30.0D, corner[k].z);
			swords[k] = rig(SWORDS[k], at, swordScale, cornerYaw[k], 180.0F, 0.0F, climax + 60, true);
			track("corner_" + k, swords[k].pose);
		}

		private void landed(int k) {
			Rig sword = swords[k];
			if (sword != null) {
				sword.play(step(Anim.STAMP, 0, 2, 0.0F, 0.0F));
			}
			Vec3 at = corner[k];
			spend(Qi.groundRing(0xB0102A, 1.5F, 9.0F, 12), at.x, at.y + 0.2D, at.z, 0.0D, 0.0D, 0.0D);
			sparks(at.add(0.0D, 1.5D, 0.0D), 0xFFB0B0, 10, 0.4D);
		}

		private void map() {
			// the plate is 32 px wide: scale = the half side
			map = show("zx_map", new Vec3(centre.x, groundY + 0.06D, centre.z), radius, 0.0F, 0.0F, 0.0F, climax + 20, false,
					step(Anim.OPEN, 0, 20, 0.0F, 0.0F));
			if (map != null) {
				map.lightCycle(10, 80, 0);
			}
		}

		private void seam(int k) {
			Vec3 a = corner[k];
			Vec3 b = corner[(k + 1) % 4];
			Vec3 mid = a.add(b).scale(0.5D).add(0.0D, 1.0D, 0.0D);
			boolean alongX = Math.abs(a.x - b.x) > Math.abs(a.z - b.z);
			// 48 px long: scale = side / 3
			seams[k] = show("zx_seam", new Vec3(mid.x, groundY + 1.0D, mid.z), 2.0F * radius / 3.0F, alongX ? 0.0F : 90.0F, 0.0F, 0.0F, climax + 20 - age(), false,
					step(Anim.GROW, 0, 8, 0.1F, 0.0F));
			if (seams[k] != null) {
				ModelHandle seam = seams[k];
				at(age() + 30, () -> seam.lightTo(7, 10));
			}
		}

		void pulse() {
			pulses++;
			int now = age();
			if (now < 40 || now > climax - 20) {
				return;
			}
			// a light wave running round the square, a quarter side per 5 ticks
			for (int k = 0; k < 4; k++) {
				ModelHandle seam = seams[Math.floorMod(k + pulses, 4)];
				if (seam == null) {
					continue;
				}
				at(now + 5 * k, () -> seam.lightTo(15, 2));
				at(now + 5 * k + 4, () -> seam.lightTo(7, 8));
			}
		}

		/** A corner pillar step: that corner's sword lifts 2 b and slams back with a shock ring. */
		void pillar(int index) {
			int k = Math.floorMod(index, 4);
			int now = age();
			if (now > climax - 22) {
				return;
			}
			liftAt[k] = now + 2 * k;
			at(now + 2 * k + 9, () -> {
				Rig sword = swords[k];
				if (sword != null) {
					sword.play(step(Anim.STAMP, 0, 2, 0.0F, 0.0F));
				}
				Vec3 at = corner[k];
				spend(Qi.groundRing(0xFF3A4A, 2.0F, 14.0F, 14), at.x, at.y + 0.2D, at.z, 0.0D, 0.0D, 0.0D);
				sparks(at.add(0.0D, 1.0D, 0.0D), 0xFF6A6A, 8, 0.5D);
			});
			if (k == 0) {
				cue("step", centre);
			}
		}

		private void release() {
			if (releaseAt >= 0) {
				return;
			}
			releaseAt = age();
			cue("release", centre);
			for (ModelHandle seam : seams) {
				if (seam != null) {
					seam.lightTo(15, 6);
				}
			}
			// the sky sword appears point down, its tip 26 b over the centre
			skyFrom = groundY + 26.0D + 1.5D * skyScale;
			skyTo = groundY - 2.0D + 1.5D * skyScale;
			sky = rig("zx_sky_sword", new Vec3(centre.x, skyFrom, centre.z), skyScale, ModelFx.yawTo(centre, corner[0]) + 45.0F, 180.0F, 0.0F, 80, true,
					step(Anim.GROW, 0, 10, 0.3F, 0.0F));
			track("sky_sword", sky.pose);
		}

		private void fall(int ticks) {
			if (fallAt >= 0) {
				return;
			}
			fallAt = age();
			fallTicks = ticks;
			at(fallAt + ticks, this::land);
		}

		void climax(Vec3 at) {
			if (releaseAt < 0) {
				release();
			}
			if (fallAt < 0) {
				fall(4);
			} else if (landedAt < 0 && age() < fallAt + fallTicks - 4) {
				// the climax came early: the sword finishes its drop in 4 ticks
				int done = age() - fallAt;
				fallTicks = done + 4;
				at(fallAt + fallTicks, this::land);
			}
		}

		private void land() {
			if (landedAt >= 0) {
				return;
			}
			landedAt = age();
			cue("impact", centre);
			if (sky != null) {
				sky.play(step(Anim.STAMP, 0, 2, 0.0F, 0.0F));
				// it hands over to the Dampak monument (planted in the same spot) by a short crossfade
				at(landedAt + 8, () -> sky.end(6));
			}
			if (map != null) {
				map.lightTo(15, 1);
				map.play(step(Anim.SHATTER, 1, 3, 0.0F, 0.0F));
			}
			for (int i = 0; i < 4; i++) {
				Rig sword = swords[i];
				if (sword != null) {
					at(landedAt + 12 + 6 * i, () -> sword.end(6));
				}
				endAt(plinths[i], landedAt + 16 + 6 * i, 10);
				endAt(seams[i], landedAt + 2, 4);
			}
			spend(Qi.ring(0xFFFFFF, 2.0F, 26.0F, 8), centre.x, groundY + 3.0D, centre.z, 0.0D, 0.0D, 0.0D);
			spend(Qi.groundRing(0xB0102A, 4.0F, radius * 2.0F, 20), centre.x, groundY + 0.2D, centre.z, 0.0D, 0.0D, 0.0D);
			spend(Qi.groundRing(0xFFB0B0, 3.0F, radius * 1.3F, 14), centre.x, groundY + 0.4D, centre.z, 0.0D, 0.0D, 0.0D);
			sparks(new Vec3(centre.x, groundY + 1.0D, centre.z), 0xFFE0E0, 36, 1.2D);
		}

		@Override
		void drive(ClientLevel level, int age) {
			for (int i = 0; i < 4; i++) {
				Rig sword = swords[i];
				if (sword == null || swordAt[i] < 0) {
					continue;
				}
				double y = rest[i];
				int a = age - swordAt[i] + 1;
				if (a < FALL) {
					float p = a / (float) FALL;
					y += 30.0D * (1.0D - p * p);
				}
				int l = age - liftAt[i] + 1;
				if (l >= 0 && l < 9) {
					y += l < 6 ? 2.0D * cubic(l / 6.0F) : 2.0D * (1.0D - square((l - 6) / 3.0F));
				}
				float pitch = 180.0F;
				if (releaseAt >= 0) {
					float k = smooth((age - releaseAt + 1) / 12.0F);
					y += 3.0D * k;
					// the tips turn in towards the centre (pitch 135: point down and inward)
					pitch = 180.0F - 45.0F * k;
				}
				sword.place(new Vec3(corner[i].x, y, corner[i].z), cornerYaw[i], pitch, 0.0F);
			}
			if (sky != null) {
				double y = skyFrom;
				if (fallAt >= 0) {
					float p = Mth.clamp((age - fallAt + 1) / (float) Math.max(1, fallTicks), 0.0F, 1.0F);
					y = skyFrom + (skyTo - skyFrom) * p * p;
				}
				sky.place(new Vec3(centre.x, y, centre.z), sky.pose.yaw, 180.0F, 0.0F);
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (age < 30) {
				// qi gathering at the corners
				for (int i = 0; i < 4; i++) {
					if (random.nextInt(3) != 0) {
						continue;
					}
					Vec3 c = corner[i];
					double a = random.nextDouble() * Math.PI * 2.0D;
					spend(Qi.mote(0xFF6A6A, 0.5F, 20), c.x + Math.cos(a) * 3.0D, c.y + 0.5D, c.z + Math.sin(a) * 3.0D, -Math.cos(a) * 0.12D, 0.12D,
							-Math.sin(a) * 0.12D);
				}
				return;
			}
			if (releaseAt >= 0 && landedAt < 0 && sky != null) {
				// red light streaming up the four corners into the descending sword
				Vec3 tip = sky.pose.cur.add(0.0D, -1.5D * skyScale, 0.0D);
				for (int i = 0, n = EffectQuality.scale(4); i < n; i++) {
					Vec3 c = corner[random.nextInt(4)].add(0.0D, 3.0D, 0.0D);
					Vec3 v = tip.subtract(c).scale(0.06D);
					spend(Qi.spark(0xFF8A8A, 0.45F, 16), c.x, c.y, c.z, v.x, v.y, v.z);
				}
				return;
			}
			if (landedAt < 0 && age % 3 == 0) {
				// mist sliding along the seams
				for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
					int k = random.nextInt(4);
					Vec3 a = corner[k];
					Vec3 b = corner[(k + 1) % 4];
					double t = random.nextDouble();
					Vec3 p = a.add(b.subtract(a).scale(t));
					spend(Qi.mist(0x400010, 1.6F, 30), p.x, groundY + 0.6D, p.z, 0.0D, 0.02D, 0.0D);
				}
			} else if (landedAt >= 0 && age < landedAt + 30 && age % 2 == 0) {
				for (int i = 0, n = EffectQuality.scale(3); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = 2.0D + random.nextDouble() * 6.0D;
					spend(Qi.smoke(0x5A4040, 1.4F, 30), centre.x + Math.cos(a) * r, groundY + 0.5D, centre.z + Math.sin(a) * r, Math.cos(a) * 0.08D, 0.06D,
							Math.sin(a) * 0.08D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return landedAt >= 0 ? age > landedAt + 50 : age > climax + 80;
		}
	}

	// ================================================================== Seni Pedang Hukuman Langit

	private static final class Punish extends Run {
		private static final double GATE_HEIGHT = 34.0D;

		final Vec3 aim;
		final Vec3 gatePos;
		final float gateYaw;
		final float gateScale;
		final float swordScale;
		final int climax;
		@Nullable ModelHandle gate;
		@Nullable Rig doorL;
		@Nullable Rig doorR;
		@Nullable Rig sword;
		@Nullable Rig halo;
		final Pose3 gateTrack;
		final List<Vec3> hits = new ArrayList<>();
		int swordAt = -1;
		int novaAt = -1;
		int novas;
		int closeAt = -1;
		int travelAt = -1;
		int travelTicks;
		Vec3 swordFrom = Vec3.ZERO;
		Vec3 swordTo = Vec3.ZERO;
		float travelYaw;
		float travelPitch;
		boolean planted;

		Punish(Ev ev, ClientLevel level) {
			super(ev);
			Vec3 origin = ev.casterPos();
			Vec3 target = ev.hasTo() ? ev.to() : origin.add(ev.forward().scale(16.0D));
			Vec3 flat = new Vec3(target.x - origin.x, 0.0D, target.z - origin.z);
			if (flat.length() > 32.0D) {
				target = origin.add(flat.normalize().scale(32.0D));
			}
			this.aim = new Vec3(target.x, ground(level, target.x, target.z, target.y), target.z);
			this.gatePos = aim.add(0.0D, GATE_HEIGHT, 0.0D);
			this.gateYaw = flat.lengthSqr() > 1.0D ? ModelFx.yawTo(aim, origin) : ev.yaw() + 180.0F;
			this.gateScale = lean ? 14.0F : 20.0F;
			this.swordScale = 18.0F * Math.min(1.2F, ev.grade());
			this.climax = ev.climaxAt(60);
			this.gateTrack = new Pose3(gatePos.add(0.0D, 0.75D * gateScale, 0.0D), gateYaw, 0.0F);
			if (!lean) {
				gate = show("hps_gate", gatePos, gateScale, gateYaw, 0.0F, 0.0F, climax + 60, false, step(Anim.GROW, 0, 12, 0.3F, 0.0F));
				gateTrack.handle = gate;
				track("gate", gateTrack);
				at(1, () -> {
					doorR = rig("hps_gate_door_r", gatePos, gateScale, gateYaw, 0.0F, 0.0F, climax + 60, false, step(Anim.GROW, 0, 11, 0.3F, 0.0F));
					doorL = rig("hps_gate_door_l", gatePos, gateScale, gateYaw, 0.0F, 0.0F, climax + 60, false, step(Anim.GROW, 0, 11, 0.3F, 0.0F));
				});
			}
			at(20, this::draw);
		}

		/** The gate's grown fraction at run tick {@code age} (its GROW: 0.3 -> 1 over 12 ticks, easeOutCubic). */
		private float grown(int age) {
			return 0.3F + 0.7F * cubic(Mth.clamp((age + 1) / 12.0F, 0.0F, 1.0F));
		}

		private void draw() {
			swordAt = age();
			Vec3 at = gatePos.add(0.0D, 1.0D * gateScale, 0.0D);
			sword = rig("hps_sword", at, swordScale, gateYaw, 180.0F, 0.0F, climax + 60, true, step(Anim.GROW, 0, 8, 0.3F, 0.0F));
			track("sword", sword.pose);
			if (!lean) {
				halo = rig("hps_halo", at, 22.0F, gateYaw, 0.0F, 0.0F, climax + 20, false, step(Anim.GROW, 0, 10, 0.2F, 0.0F),
						step(Anim.SPIN, 0, climax + 20, 2.0F, 2.0F));
			}
		}

		void nova() {
			int now = age();
			if (now < 10 || novas >= 2) {
				return;
			}
			novas++;
			cue("nova", aim);
			if (novas == 1) {
				novaAt = now;
				hits.clear();
				// the chains fall the tick after, onto the foes the nova struck (their HIT events come with it)
				if (!lean) {
					at(now + 1, this::chains);
				}
			} else if (halo != null && halo.handle != null) {
				ModelHandle h = halo.handle;
				h.lightTo(15, 1);
				at(now + 6, () -> h.lightCycle(9, 30, 0));
			}
			spend(Qi.groundRing(novas == 1 ? 0x302040 : 0xFFF1C0, 3.0F, 40.0F, 16), aim.x, aim.y + 0.2D, aim.z, 0.0D, 0.0D, 0.0D);
		}

		void hit(Vec3 at) {
			if (novaAt >= 0 && age() - novaAt <= 1 && hits.size() < 8) {
				hits.add(at);
			}
		}

		private void chains() {
			RandomSource random = RandomSource.create(seed);
			List<Vec3> points = new ArrayList<>(hits);
			for (int i = points.size(); i < 8; i++) {
				double a = Math.PI * 2.0D * i / 8.0D + 0.2D;
				points.add(aim.add(Math.cos(a) * 10.0D, 1.0D, Math.sin(a) * 10.0D));
			}
			int n = max ? 8 : 6;
			for (int i = 0; i < Math.min(n, points.size()); i++) {
				Vec3 p = points.get(i);
				Vec3 foot = p.add(0.0D, -1.0D, 0.0D);
				float height = (float) Mth.clamp(gatePos.y - foot.y, 16.0D, 40.0D);
				ModelHandle chain = show("hps_chain", foot, 2.0F, random.nextFloat() * 360.0F, 0.0F, 0.0F, 50, false, step(Anim.FALL, 0, 8, height, 0.0F),
						step(Anim.STAMP, 8, 2, 0.0F, 0.0F), step(Anim.DISSOLVE, 38, 10, 0.0F, 0.0F));
				if (chain == null) {
					break;
				}
				int land = age() + 8;
				at(land, () -> spend(Qi.groundRing(0xFFF1C0, 0.8F, 4.0F, 10), foot.x, foot.y + 0.2D, foot.z, 0.0D, 0.0D, 0.0D));
			}
		}

		void travel(Vec3 from, Vec3 to, int duration, float length) {
			if (travelAt >= 0) {
				return;
			}
			if (sword == null) {
				draw();
			}
			travelAt = age();
			Rig s = sword;
			swordFrom = s.pose.cur;
			Vec3 dir = to.subtract(swordFrom);
			double distance = dir.length();
			if (distance < 1.0E-3D) {
				dir = new Vec3(0.0D, -1.0D, 0.0D);
				distance = 1.0D;
			}
			Vec3 unit = dir.scale(1.0D / distance);
			// blade direction for (yaw, pitch) is turned (0, cos pitch, sin pitch): point it along the flight
			travelYaw = (float) Math.toDegrees(Math.atan2(-unit.x, unit.z));
			travelPitch = (float) Math.toDegrees(Math.acos(Mth.clamp(unit.y, -1.0D, 1.0D)));
			// the tip leads and sinks 2 b at the end: the grip stops (1.5 x scale - 2) short of the beam's end
			swordTo = to.subtract(unit.scale(1.5D * swordScale - 2.0D));
			int byLength = (int) Math.round((length > 1.0F ? length : distance) / 8.0D);
			travelTicks = Mth.clamp(duration > 0 ? duration : byLength, 8, 24);
			cue("release", from);
			if (halo != null) {
				Rig h = halo;
				at(travelAt + 4, () -> h.end(8));
			}
			closeAt = travelAt + 6;
			at(travelAt + 6 + travelTicks, () -> plant(to));
			if (gate != null) {
				endAt(gate, travelAt + 26, 12);
				at(travelAt + 26, () -> {
					if (doorL != null) {
						doorL.end(12);
					}
					if (doorR != null) {
						doorR.end(12);
					}
					gateTrack.ended = true;
				});
			}
		}

		private void plant(Vec3 end) {
			if (planted) {
				return;
			}
			planted = true;
			cue("impact", end);
			if (sword != null) {
				sword.play(step(Anim.STAMP, 0, 2, 0.0F, 0.0F));
				Rig s = sword;
				at(age() + 10, () -> s.end(6));
			}
			spend(Qi.ring(0xFFF8E0, 2.0F, 20.0F, 8), end.x, end.y + 2.0D, end.z, 0.0D, 0.0D, 0.0D);
			spend(Qi.groundRing(0xFFF1C0, 3.0F, 24.0F, 18), end.x, end.y + 0.2D, end.z, 0.0D, 0.0D, 0.0D);
			sparks(end.add(0.0D, 1.0D, 0.0D), 0xFFF1C0, 30, 1.0D);
		}

		@Override
		void drive(ClientLevel level, int age) {
			float g = grown(age);
			// the doors turn on their hinges: open 4-20, closed again from the release
			float open = smooth((age - 4 + 1) / 16.0F);
			if (closeAt >= 0) {
				open *= 1.0F - smooth((age - closeAt + 1) / 16.0F);
			}
			if (doorR != null) {
				doorR.place(local(gatePos, gateYaw, 0.0F, 0.0F, gateScale * g, -5.0D, 0.0D, 0.0D), gateYaw - 45.0F * open, 0.0F, 0.0F);
			}
			if (doorL != null) {
				doorL.place(local(gatePos, gateYaw, 0.0F, 0.0F, gateScale * g, 5.0D, 0.0D, 0.0D), gateYaw + 45.0F * open, 0.0F, 0.0F);
			}
			if (sword == null || swordAt < 0) {
				return;
			}
			Vec3 start = gatePos.add(0.0D, 1.0D * gateScale, 0.0D);
			Vec3 pos;
			float yaw = gateYaw;
			float pitch = 180.0F;
			if (travelAt < 0) {
				// slides 10 b down out of the doorway (easeOut over 20 ticks)
				float k = cubic((age - swordAt + 1) / 20.0F);
				pos = start.add(0.0D, -10.0D * k, 0.0D);
			} else {
				int t = age - travelAt + 1;
				if (t <= 6) {
					// turns to the beam's axis where it hangs
					float k = smooth(t / 6.0F);
					pos = swordFrom;
					yaw = gateYaw + Mth.wrapDegrees(travelYaw - gateYaw) * k;
					pitch = Mth.lerp(k, 180.0F, travelPitch);
				} else {
					float k = Mth.clamp((t - 6) / (float) travelTicks, 0.0F, 1.0F);
					pos = swordFrom.add(swordTo.subtract(swordFrom).scale(k * k));
					yaw = travelYaw;
					pitch = travelPitch;
				}
			}
			sword.place(pos, yaw, pitch, 0.0F);
			if (halo != null && halo.alive()) {
				// the halo stands behind the guard, facing the caster
				Vec3 behind = new Vec3(Mth.sin(gateYaw * Mth.DEG_TO_RAD), 0.0D, -Mth.cos(gateYaw * Mth.DEG_TO_RAD)).scale(1.5D);
				halo.place(pos.add(behind).add(0.0D, -0.6D * swordScale / 16.0D, 0.0D), gateYaw, 0.0F, 0.0F);
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (age < 20 && age % 2 == 0) {
				// golden light pouring from the opening doorway
				for (int i = 0, n = EffectQuality.scale(3); i < n; i++) {
					double s = (random.nextDouble() - 0.5D) * gateScale * 0.5D;
					Vec3 side = new Vec3(Mth.cos(gateYaw * Mth.DEG_TO_RAD), 0.0D, Mth.sin(gateYaw * Mth.DEG_TO_RAD)).scale(s);
					Vec3 p = gatePos.add(side).add(0.0D, random.nextDouble() * gateScale * 0.9D, 0.0D);
					spend(Qi.mote(0xFFF1C0, 0.6F, 24), p.x, p.y, p.z, 0.0D, -0.15D, 0.0D);
				}
			}
			if (sword != null && travelAt >= 0 && !planted) {
				// the wake of the riding sword
				Vec3 p = sword.pose.cur;
				for (int i = 0, n = EffectQuality.scale(6); i < n; i++) {
					spend(Qi.spark(i % 2 == 0 ? 0xFFFFFF : 0xC7A2FF, 0.6F, 12), p.x + (random.nextDouble() - 0.5D) * 3.0D, p.y + (random.nextDouble() - 0.5D) * 3.0D,
							p.z + (random.nextDouble() - 0.5D) * 3.0D, 0.0D, 0.0D, 0.0D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			if (planted) {
				return age > travelAt + travelTicks + 50;
			}
			return age > climax + 120;
		}
	}

	// ================================================================== Formasi Kaisar Petir Kesengsaraan

	private static final class Tribulation extends Run {
		private static final int[][] LUOSHU = {{4, 9, 2}, {3, 5, 7}, {8, 1, 6}};
		/** Strikes 1-8 light palaces 1-4 and 6-9; the ninth lands on palace 5. */
		private static final int[] ORDER = {1, 2, 3, 4, 6, 7, 8, 9};
		private static final float SIDE = 20.0F;
		private static final double RIM = 30.0D;

		final Vec3 centre;
		final double groundY;
		final int climax;
		final Vec3[] palace = new Vec3[10];
		final ModelHandle[] drums = new ModelHandle[10];
		final ModelHandle[] rims = new ModelHandle[8];
		@Nullable Rig grid;
		int strikes;
		int ninthAt = -1;

		Tribulation(Ev ev, ClientLevel level) {
			super(ev);
			this.centre = ev.pos();
			this.groundY = ground(level, centre.x, centre.z, centre.y);
			this.climax = ev.climaxAt(198);
			float cell = SIDE / 3.0F;
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					double x = centre.x + (col - 1) * cell;
					double z = centre.z + (row - 1) * cell;
					palace[LUOSHU[row][col]] = new Vec3(x, groundY, z);
				}
			}
			if (!lean) {
				grid = rig("tte_grid", new Vec3(centre.x, groundY + SIDE * 0.5D, centre.z), SIDE, 180.0F, 90.0F, 0.0F, climax + 20, false);
				for (int k = 0; k < 8; k++) {
					int index = k;
					at(k / 2, () -> rim(index));
				}
			}
			for (int n = 1; n <= 9; n++) {
				int number = n;
				if (!lean || n == 5) {
					at(6 + 2 * (n - 1), () -> drum(number));
				}
			}
			at(climax - 10, this::release);
		}

		private void rim(int k) {
			rims[k] = show("tte_vortex_rim", centre.add(0.0D, RIM, 0.0D), 16.0F, 45.0F * k, 0.0F, 0.0F, climax + 60, false, step(Anim.GROW, 0, 12, 0.2F, 0.0F),
					step(Anim.SPIN, 0, climax + 60, 1.0F, 0.0F));
			if (k == 0 && rims[0] != null) {
				Pose3 pose = new Pose3(centre.add(0.0D, RIM, 0.0D), 0.0F, 0.0F);
				pose.handle = rims[0];
				track("rim", pose);
			}
		}

		private void drum(int n) {
			drums[n] = show("tte_drum", palace[n], 4.0F, 180.0F + 40.0F * n, 0.0F, 0.0F, climax + 50, n == 5, step(Anim.RISE, 0, 8, 2.0F, 0.0F));
			if (drums[n] != null) {
				ModelHandle drum = drums[n];
				at(age() + 8, () -> drum.lightCycle(10, 50, 5 * n));
				Pose3 pose = new Pose3(palace[n].add(0.0D, 2.2D, 0.0D), 0.0F, 0.0F);
				pose.handle = drum;
				track("drum_" + n, pose);
			}
		}

		void strike() {
			if (strikes >= ORDER.length) {
				return;
			}
			int n = ORDER[strikes++];
			Vec3 top = palace[n].add(0.0D, 2.2D, 0.0D);
			cue("strike", top);
			ModelHandle drum = drums[n];
			if (drum != null) {
				drum.swap("tte_drum_lit");
				drum.lightTo(15, 2);
				drum.play(step(Anim.PULSE, 0, 6, 0.08F, 0.0F));
			}
			bolt(top, 6.0F, 20.0F, 4, (float) Math.floorMod(seed + 37L * n, 360L));
			if (max) {
				int now = age();
				at(now + 1, () -> bolt(top, 6.0F, 20.0F, 4, (float) Math.floorMod(seed + 37L * n + 90L, 360L)));
			}
			spend(Qi.groundRing(0xB57CFF, 1.0F, 7.0F, 12), palace[n].x, groundY + 0.15D, palace[n].z, 0.0D, 0.0D, 0.0D);
			sparks(top, 0xE8D8FF, 10, 0.6D);
		}

		private void bolt(Vec3 at, float scale, float height, int fall, float yaw) {
			ModelHandle bolt = show("tte_bolt", at, scale, yaw, 0.0F, 0.0F, fall + 6, false, step(Anim.FALL, 0, fall, height, 0.0F));
			endAt(bolt, age() + fall + 3, 3);
		}

		private void release() {
			cue("release", centre);
			for (ModelHandle drum : drums) {
				if (drum != null) {
					drum.play(step(Anim.TILT, 0, 3, 8.0F, 0.0F));
					drum.play(step(Anim.TILT, 3, 5, -8.0F, 0.0F));
				}
			}
			for (ModelHandle rim : rims) {
				if (rim != null) {
					rim.play(step(Anim.SPIN, 0, 80, 2.0F, 0.0F));
				}
			}
		}

		void ninth() {
			if (ninthAt >= 0) {
				return;
			}
			ninthAt = age();
			Vec3 top = palace[5].add(0.0D, 2.2D, 0.0D);
			cue("impact", top);
			ModelHandle big = show("tte_bolt", top, 18.0F, (float) Math.floorMod(seed, 360L), 0.0F, 0.0F, 14, true, step(Anim.FALL, 0, 3, 12.0F, 0.0F));
			endAt(big, ninthAt + 9, 3);
			for (int n = 1; n <= 9; n++) {
				ModelHandle drum = drums[n];
				if (drum == null) {
					continue;
				}
				drum.swap("tte_drum_lit");
				drum.lightTo(15, 1);
				ModelHandle d = drum;
				Vec3 down = palace[n].add(0.0D, -2.0D, 0.0D);
				at(ninthAt + 10 + n, () -> d.play(new AnimStep(Anim.SLIDE, 0, 10, 0.0F, 2.0F, down)));
				at(ninthAt + 20 + n, () -> d.end(4));
			}
			if (grid != null && grid.handle != null) {
				ModelHandle g = grid.handle;
				g.lightTo(15, 1);
				at(ninthAt + 6, () -> g.play(step(Anim.SHATTER, 0, 3, 0.0F, 0.0F)));
			}
			for (int k = 0; k < 8; k++) {
				ModelHandle rim = rims[k];
				if (rim != null) {
					at(ninthAt + 12 + 3 * k, () -> rim.play(step(Anim.DISSOLVE, 0, 10, 0.0F, 0.0F)));
				}
			}
			spend(Qi.ring(0xF0E8FF, 2.0F, 24.0F, 8), top.x, top.y + 2.0D, top.z, 0.0D, 0.0D, 0.0D);
			spend(Qi.groundRing(0xB57CFF, 4.0F, SIDE * 2.2F, 20), centre.x, groundY + 0.2D, centre.z, 0.0D, 0.0D, 0.0D);
			sparks(top, 0xFFFFFF, 30, 1.2D);
		}

		@Override
		void drive(ClientLevel level, int age) {
			if (grid != null && age <= 15) {
				// the grid falls flat like a door: its low edge stays on the ground while it tilts 90 -> 0
				float tilt = 90.0F * (1.0F - smooth((age + 1) / 14.0F));
				double lift = 0.06D + SIDE * 0.5D * Mth.sin(tilt * Mth.DEG_TO_RAD);
				grid.place(new Vec3(centre.x, groundY + lift, centre.z), 180.0F, tilt, 0.0F);
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (ninthAt >= 0 || age > climax + 10) {
				return;
			}
			if (age % 2 == 0) {
				// sparks crawling down from the whirlpool's rim
				for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = 18.0D + random.nextDouble() * 4.0D;
					spend(Qi.spark(0xE8D8FF, 0.4F, 14), centre.x + Math.cos(a) * r, centre.y + RIM - 2.0D, centre.z + Math.sin(a) * r, 0.0D, -0.4D, 0.0D);
				}
			}
			if (age > climax - 30 && age % 2 == 0) {
				// the eye gathers: motes streaming up into it
				for (int i = 0, n = EffectQuality.scale(3); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = random.nextDouble() * 8.0D;
					spend(Qi.mote(0xB57CFF, 0.5F, 20), centre.x + Math.cos(a) * r, groundY + 1.0D, centre.z + Math.sin(a) * r, 0.0D, 0.6D, 0.0D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return ninthAt >= 0 ? age > ninthAt + 50 : age > climax + 80;
		}
	}

	// ================================================================== Tianzun Guntur Sembilan Langit

	private static final class Venerable extends Run {
		final Vec3 origin;
		final int climax;
		final float yaw;
		final ModelHandle[] rings = new ModelHandle[9];
		final Map<Integer, Deity> deities = new HashMap<>();
		final Pose3 qilinTrack;
		@Nullable ModelHandle qilin;
		@Nullable ModelHandle whip;
		int novas;
		int impactAt = -1;
		boolean released;

		Venerable(Ev ev) {
			super(ev);
			this.origin = ev.casterPos();
			this.climax = ev.climaxAt(400);
			this.yaw = ev.yaw();
			int life = climax + 60;
			Vec3 behind = new Vec3(0.0D, 0.0D, -9.0D);
			this.qilinTrack = new Pose3(origin.add(behind.yRot(-yaw * Mth.DEG_TO_RAD)), yaw, 0.0F);
			if (!lean) {
				float scale = max ? 7.0F : 6.0F;
				AnimStep[] steps = {step(Anim.RISE, 0, 12, 2.0F, 0.0F), step(Anim.TILT, 12, 10, -22.5F, 0.0F)};
				qilin = casterHere() ? ride("nhtv_qilin", caster, behind, scale, 0.0F, 0.0F, life, false, steps)
						: show("nhtv_qilin", qilinTrack.cur, scale, yaw, 0.0F, 0.0F, life, false, steps);
				qilinTrack.handle = qilin;
				track("qilin", qilinTrack);
				if (casterHere()) {
					at(2, () -> whip = ride("nhtv_whip", caster, new Vec3(-0.42D, 1.25D, 0.3D), 1.4F, 0.0F, 20.0F, life, false,
							step(Anim.GROW, 0, 6, 0.2F, 0.0F)));
				}
			}
			for (int k = 0; k < 9; k++) {
				int index = k;
				if (!lean || k % 4 == 0) {
					at(4 + 8 * k, () -> ring(index));
				}
			}
			at(climax - 20, this::release);
		}

		private void ring(int k) {
			int life = climax + 60 - age();
			Vec3 at = origin.add(0.0D, 8.0D + 4.0D * k, 0.0D);
			rings[k] = show("nhtv_ring", at, 10.0F + 0.6F * k, 40.0F * k, 0.0F, 0.0F, life, true, step(Anim.GROW, 0, 12, 0.4F, 0.0F),
					step(Anim.SPIN, 0, life, k % 2 == 0 ? 0.5F : -0.5F, 0.0F));
			if (rings[k] != null) {
				rings[k].lightCycle(11, 60, 7 * k);
				Pose3 pose = new Pose3(at, 0.0F, 0.0F);
				pose.handle = rings[k];
				track("ring_" + (k + 1), pose);
			}
			spend(Qi.ring(0xE8ECF4, 2.0F, 12.0F + k, 10), at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
		}

		void nova() {
			novas++;
			cue("nova", origin);
			for (int k = 2 * novas - 2; k <= 2 * novas - 1 && k < 9; k++) {
				light(k);
			}
			spend(Qi.groundRing(0xB57CFF, 3.0F, 40.0F, 18), origin.x, origin.y + 0.2D, origin.z, 0.0D, 0.0D, 0.0D);
		}

		private void light(int k) {
			ModelHandle ring = rings[k];
			if (ring != null) {
				ring.swap("nhtv_ring_lit");
				ring.lightCycle(12, 30, 0);
			}
		}

		void phantom(int index, Vec3 at, Vec3 victim, boolean strike) {
			if (lean || impactAt >= 0) {
				return;
			}
			int slot = Math.floorMod(index, 16);
			if (slot >= (max ? 8 : 4)) {
				return;
			}
			Deity deity = deities.get(slot);
			float face = ModelFx.yawTo(at, victim);
			if (deity == null) {
				// a little larger than PhantomFx's translucent copy on the same phantom, which then reads as its violet aura
				Rig rig = rig("nhtv_deity", at, 2.8F, face, 0.0F, 0.0F, climax + 40 - age(), false, step(Anim.GROW, 0, 8, 0.2F, 0.0F));
				if (rig.handle == null) {
					return;
				}
				deity = new Deity(rig, at, face);
				deities.put(slot, deity);
				track("deity_" + (slot + 1), rig.pose);
			}
			deity.target = at;
			deity.face = face;
			if (strike) {
				// a hammer lunge: forward 25 degrees, back over 5 ticks
				deity.rig.play(step(Anim.TILT, 0, 3, 25.0F, 0.0F));
				deity.rig.play(step(Anim.TILT, 3, 5, -25.0F, 0.0F));
			}
		}

		private void release() {
			released = true;
			cue("release", origin);
			for (int k = 0; k < 9; k++) {
				light(k);
				if (rings[k] != null) {
					rings[k].play(step(Anim.SPIN, 0, 60, k % 2 == 0 ? 1.5F : -1.5F, 0.0F));
				}
			}
			if (qilin != null) {
				qilin.play(step(Anim.PULSE, 0, 10, 0.06F, 0.0F));
			}
		}

		void impact() {
			if (impactAt >= 0) {
				return;
			}
			impactAt = age();
			cue("impact", origin);
			if (whip != null) {
				whip.play(step(Anim.TILT, 0, 4, 45.0F, 0.0F));
				endAt(whip, impactAt + 14, 6);
			}
			for (int k = 0; k < 9; k++) {
				ModelHandle ring = rings[k];
				if (ring == null) {
					continue;
				}
				ring.lightTo(15, 1);
				ring.play(step(Anim.PULSE, 0, 6, 0.08F, 0.0F));
				// the rings end top-down, 4 ticks apart
				endAt(ring, impactAt + 10 + 4 * (8 - k), 8);
			}
			for (Deity deity : deities.values()) {
				deity.rig.play(step(Anim.SHATTER, 0, 3, 0.0F, 0.0F));
			}
			if (qilin != null) {
				ModelHandle q = qilin;
				at(impactAt + 10, () -> q.play(step(Anim.DISSOLVE, 0, 12, 0.0F, 0.0F)));
			}
			spend(Qi.ring(0xF0E8FF, 3.0F, 30.0F, 8), origin.x, origin.y + 6.0D, origin.z, 0.0D, 0.0D, 0.0D);
			sparks(origin.add(0.0D, 2.0D, 0.0D), 0xFFFFFF, 30, 1.0D);
		}

		@Override
		void drive(ClientLevel level, int age) {
			if (casterHere()) {
				float body = bodyYaw(yaw);
				qilinTrack.set(caster.position().add(new Vec3(0.0D, 0.0D, -9.0D).yRot(-body * Mth.DEG_TO_RAD)), body, 0.0F);
			}
			for (Deity deity : deities.values()) {
				if (!deity.rig.alive()) {
					continue;
				}
				deity.pos = deity.pos.add(deity.target.subtract(deity.pos).scale(0.35D));
				deity.yaw = deity.yaw + Mth.wrapDegrees(deity.face - deity.yaw) * 0.35F;
				deity.rig.place(deity.pos, deity.yaw, 0.0F, 0.0F);
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (impactAt >= 0 || qilin == null || !casterHere() || age % 3 != 0) {
				return;
			}
			// violet flame licking off the qilin
			Vec3 q = qilinTrack.cur;
			for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
				spend(Qi.flame(0xB57CFF, 0.8F, 18), q.x + (random.nextDouble() - 0.5D) * 3.0D, q.y + 1.0D + random.nextDouble() * 4.0D,
						q.z + (random.nextDouble() - 0.5D) * 3.0D, 0.0D, 0.06D, 0.0D);
			}
			if (released) {
				for (int i = 0, n = EffectQuality.scale(3); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					spend(Qi.spark(0xF0E8FF, 0.5F, 16), origin.x + Math.cos(a) * 3.0D, origin.y + 2.0D, origin.z + Math.sin(a) * 3.0D, 0.0D, 1.2D, 0.0D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return impactAt >= 0 ? age > impactAt + 60 : age > climax + 80;
		}
	}

	private static final class Deity {
		final Rig rig;
		Vec3 pos;
		Vec3 target;
		float yaw;
		float face;

		Deity(Rig rig, Vec3 at, float face) {
			this.rig = rig;
			this.pos = at;
			this.target = at;
			this.yaw = face;
			this.face = face;
		}
	}

	// ================================================================== Turunnya Phoenix Es

	private static final class Phoenix extends Run {
		private static final double RADIUS = 14.0D;
		private static final double HEIGHT = 18.0D;
		private static final int FORM = 30;

		final Vec3 origin;
		final Vec3 home;
		final int climax;
		final float scale;
		final double phase0;
		final List<ModelHandle> rings = new ArrayList<>();
		@Nullable Rig body;
		@Nullable Rig wingL;
		@Nullable Rig wingR;
		@Nullable Rig tail;
		boolean formed = true;
		Vec3 pos;
		float yaw;
		float pitch;
		int diveAt = -1;
		int diveTicks;
		int skimTicks;
		Vec3 diveFrom = Vec3.ZERO;
		Vec3 diveTo = Vec3.ZERO;
		Vec3 skimTo = Vec3.ZERO;
		int climbAt = -1;
		Vec3 climbFrom = Vec3.ZERO;
		int releaseAt = -1;
		Vec3 releaseFrom = Vec3.ZERO;
		int novaAt = -1;
		int lastLanding = -100;

		Phoenix(Ev ev) {
			super(ev);
			this.origin = ev.casterPos();
			this.home = origin.add(0.0D, HEIGHT, 0.0D);
			this.climax = ev.climaxAt(240);
			this.scale = (lean ? 8.0F : 10.0F) * Math.min(1.15F, ev.grade());
			this.phase0 = Math.floorMod(seed, 360L) * Mth.DEG_TO_RAD;
			this.pos = circle(0);
			at(0, this::form);
			at(climax - 20, this::release);
		}

		private void form() {
			if (!formed) {
				return;
			}
			int life = climax + 20;
			AnimStep grow = step(Anim.GROW, 0, FORM, 0.3F, 0.0F);
			body = rig("ipd_phoenix", pos, scale, 0.0F, 0.0F, 0.0F, life, true, grow);
			track("phoenix", body.pose);
			if (!lean) {
				wingR = rig("ipd_wing_r", pos, scale, 0.0F, 0.0F, 0.0F, life, false, grow);
				wingL = rig("ipd_wing_l", pos, scale, 0.0F, 0.0F, 0.0F, life, false, grow);
				tail = rig("ipd_tail", pos, scale, 0.0F, 0.0F, 0.0F, life, false, grow);
			}
		}

		private Vec3 circle(int age) {
			double a = phase0 + age * Mth.DEG_TO_RAD;
			return home.add(Math.cos(a) * RADIUS, 0.0D, Math.sin(a) * RADIUS);
		}

		void dive(Vec3 land, Vec3 far, int sweep) {
			if (releaseAt >= 0 || novaAt >= 0) {
				return;
			}
			diveAt = age();
			boolean skims = far.distanceToSqr(land) > 4.0D;
			int total = Mth.clamp(sweep, 8, 40);
			diveTicks = skims ? Mth.clamp(total / 3, 6, 12) : Mth.clamp(total, 6, 30);
			skimTicks = skims ? Mth.clamp(total - diveTicks, 4, 20) : 0;
			diveFrom = pos;
			diveTo = land.add(0.0D, 1.5D, 0.0D);
			skimTo = far.add(0.0D, 2.0D, 0.0D);
			climbAt = -1;
			cue("dive", land);
			Vec3 ground = land;
			at(diveAt + diveTicks, () -> landing(ground));
			if (skims) {
				at(diveAt + diveTicks + skimTicks, () -> {
					climbAt = age();
					climbFrom = pos;
				});
			}
		}

		void landed(Vec3 at) {
			if (age() - lastLanding > 20 && (diveAt < 0 || age() > diveAt + diveTicks + 20)) {
				// an impact without a dive seen: the ring and quills only
				landing(at);
			}
		}

		private void landing(Vec3 at) {
			lastLanding = age();
			if (skimTicks <= 0 || diveAt < 0) {
				climbAt = age();
				climbFrom = pos;
			}
			hexring(at, 6.0F, 40);
			if (!lean) {
				int n = max ? 6 : 4;
				for (int i = 0; i < n; i++) {
					float yaw = 360.0F * i / n + (float) Math.floorMod(seed + i * 17L, 30L);
					show("ipd_quill", at.add(0.0D, 1.0D, 0.0D), 1.5F, yaw, -12.0F, 0.0F, 14, false, step(Anim.THRUST, 0, 12, 5.0F, 0.0F));
				}
			}
			spend(Qi.groundRing(0xDFF6FF, 2.0F, 16.0F, 14), at.x, at.y + 0.2D, at.z, 0.0D, 0.0D, 0.0D);
			shards(at.add(0.0D, 1.0D, 0.0D), 18);
		}

		private void hexring(Vec3 at, float size, int life) {
			ModelHandle ring = show("ipd_hexring", at.add(0.0D, 0.05D, 0.0D), size, (float) Math.floorMod(seed, 60L), 0.0F, 0.0F, life, true,
					step(Anim.GROW, 0, 6, 0.2F, 0.0F));
			if (ring != null) {
				rings.add(ring);
				ModelHandle r = ring;
				at(age() + 8, () -> r.lightTo(8, life - 12));
			}
		}

		private void release() {
			if (novaAt >= 0) {
				return;
			}
			releaseAt = age();
			releaseFrom = pos;
			cue("release", origin);
		}

		void nova(Vec3 at) {
			if (novaAt >= 0) {
				return;
			}
			novaAt = age();
			cue("impact", at);
			for (Rig part : new Rig[] {body, wingL, wingR, tail}) {
				if (part != null) {
					part.play(step(Anim.SHATTER, 0, 3, 0.0F, 0.0F));
					part.pose.ended = true;
				}
			}
			hexring(at, 6.0F, 30);
			if (!lean) {
				at(novaAt + 3, () -> hexring(at, 9.0F, 50));
				at(novaAt + 6, () -> hexring(at, 12.0F, 70));
			}
			Vec3 from = body != null ? body.pose.cur : at.add(0.0D, 10.0D, 0.0D);
			shards(from, 60);
			spend(Qi.ring(0xFFFFFF, 3.0F, 30.0F, 8), at.x, at.y + 3.0D, at.z, 0.0D, 0.0D, 0.0D);
			spend(Qi.groundRing(0xCFEFFF, 4.0F, 64.0F, 22), at.x, at.y + 0.2D, at.z, 0.0D, 0.0D, 0.0D);
		}

		@Override
		void drive(ClientLevel level, int age) {
			if (body == null || novaAt >= 0) {
				return;
			}
			if (age % 4 == 0 && body.alive()) {
				// PhantomFx draws the summoned phoenix phantom with the old "ice_phoenix" model: this bird replaces it
				ModelFx.Prop old = ModelFx.find(casterId, "ice_phoenix");
				if (old != null) {
					old.end(4);
				}
			}
			int t = age + 1;
			float grown = 0.3F + 0.7F * cubic(Mth.clamp(t / (float) FORM, 0.0F, 1.0F));
			float bank = 0.0F;
			float fold;
			float flap = 20.0F * Mth.sin((float) (Math.PI * 2.0D * t / 40.0D));
			if (releaseAt >= 0) {
				// rises 10 b over the caster and spreads to full span, wings held
				float k = smooth((t - releaseAt) / 20.0F);
				Vec3 over = origin.add(0.0D, 10.0D, 0.0D);
				pos = releaseFrom.add(over.subtract(releaseFrom).scale(k));
				yaw = yaw + Mth.wrapDegrees(bodyYaw(yaw) - yaw) * 0.2F;
				pitch = -22.5F * k;
				fold = 0.0F;
				flap *= 1.0F - k;
			} else if (diveAt >= 0 && t < diveAt + diveTicks) {
				float k = Mth.clamp((t - diveAt) / (float) diveTicks, 0.0F, 1.0F);
				pos = diveFrom.add(diveTo.subtract(diveFrom).scale(k * k));
				Vec3 dir = diveTo.subtract(diveFrom);
				yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
				pitch = (float) Math.toDegrees(-Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
				fold = smooth((t - diveAt) / 4.0F);
			} else if (diveAt >= 0 && skimTicks > 0 && t < diveAt + diveTicks + skimTicks && climbAt < 0) {
				// skimming low along the line, wings half open
				float k = Mth.clamp((t - diveAt - diveTicks) / (float) skimTicks, 0.0F, 1.0F);
				pos = diveTo.add(skimTo.subtract(diveTo).scale(k));
				Vec3 dir = skimTo.subtract(diveTo);
				if (dir.lengthSqr() > 1.0E-4D) {
					yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
				}
				pitch = 0.0F;
				fold = 0.45F;
			} else if (climbAt >= 0 && t < climbAt + 16) {
				float k = cubic((t - climbAt) / 16.0F);
				Vec3 back = circle(age);
				Vec3 before = pos;
				pos = climbFrom.add(back.subtract(climbFrom).scale(k));
				Vec3 dir = pos.subtract(before);
				if (dir.lengthSqr() > 1.0E-4D) {
					yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
				}
				pitch = -30.0F * (1.0F - k);
				fold = 1.0F - k;
			} else {
				pos = circle(age);
				double a = phase0 + age * Mth.DEG_TO_RAD;
				yaw = (float) Math.toDegrees(Math.atan2(Math.sin(a), Math.cos(a)));
				pitch = 0.0F;
				bank = 12.0F;
				// the wings unfold while the bird forms (45 degrees raised -> spread)
				float open = smooth(t / (float) FORM);
				fold = 0.75F * (1.0F - open);
				flap *= open;
			}
			float raise = fold * 60.0F + (1.0F - fold) * flap;
			body.place(pos, yaw, pitch, bank);
			float size = scale * grown;
			if (wingR != null) {
				wingR.place(local(pos, yaw, pitch, bank, size, 2.0D, 1.6D, 0.0D), yaw, pitch, bank - raise);
			}
			if (wingL != null) {
				wingL.place(local(pos, yaw, pitch, bank, size, -2.0D, 1.6D, 0.0D), yaw, pitch, bank + raise);
			}
			if (tail != null) {
				float sway = 8.0F * Mth.sin((float) (Math.PI * 2.0D * t / 50.0D));
				tail.place(local(pos, yaw, pitch, bank, size, 0.0D, 0.0D, 7.0D), yaw, pitch + sway, bank);
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (novaAt >= 0) {
				return;
			}
			if (age < FORM + 10) {
				// a snow swirl spiralling into the forming bird
				for (int i = 0, n = EffectQuality.scale(4); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = 4.0D + random.nextDouble() * 6.0D;
					Vec3 p = pos.add(Math.cos(a) * r, (random.nextDouble() - 0.5D) * 4.0D, Math.sin(a) * r);
					Vec3 v = pos.subtract(p).scale(0.08D).add(-Math.sin(a) * 0.2D, 0.0D, Math.cos(a) * 0.2D);
					spend(Qi.snow(0xFFFFFF, 0.5F, 24), p.x, p.y, p.z, v.x, v.y, v.z);
				}
				return;
			}
			if (age % 2 == 0) {
				// frost falling from the wings
				for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
					spend(Qi.snow(0xDFF6FF, 0.4F, 30), pos.x + (random.nextDouble() - 0.5D) * scale, pos.y - 0.5D, pos.z + (random.nextDouble() - 0.5D) * scale,
							0.0D, -0.05D, 0.0D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return novaAt >= 0 ? age > novaAt + 80 : age > climax + 100;
		}
	}

	// ================================================================== Membekukan Gunung Qi

	private static final class FrozenMount extends Run {
		final Vec3 origin;
		final float radius;
		final int climax;
		final Vec3[] peakAt = new Vec3[6];
		final ModelHandle[] peaks = new ModelHandle[6];
		final List<ModelHandle> coffins = new ArrayList<>();
		final List<int[]> frozen = new ArrayList<>();
		final List<Vec3> frozenAt = new ArrayList<>();
		@Nullable ModelHandle crown;
		int raised;
		int freezeAt = -1;
		int recallAt = -1;

		FrozenMount(Ev ev, ClientLevel level) {
			super(ev);
			this.origin = ev.casterPos();
			this.radius = Mth.clamp(ev.p().radius() > 1.0F ? ev.p().radius() : 40.0F, 16.0F, 48.0F);
			this.climax = ev.climaxAt(400);
			double r = radius * 0.75D;
			for (int n = 0; n < 6; n++) {
				double a = Math.toRadians(30.0D + 60.0D * n);
				double x = origin.x + Math.cos(a) * r;
				double z = origin.z + Math.sin(a) * r;
				peakAt[n] = new Vec3(x, ground(level, x, z, origin.y), z);
			}
			if (casterHere()) {
				crown = ride("fmq_crown", caster, new Vec3(0.0D, 0.02D, 0.0D), 3.0F, 0.0F, 0.0F, climax + 20, true, step(Anim.RISE, 0, 12, 1.5F, 0.0F));
			} else {
				crown = show("fmq_crown", origin, 3.0F, 0.0F, 0.0F, 0.0F, climax + 20, true, step(Anim.RISE, 0, 12, 1.5F, 0.0F));
			}
			at(climax - 20, this::release);
		}

		void pulse() {
			if (age() < 5 || raised >= 6 || recallAt >= 0) {
				return;
			}
			int n = raised++;
			if (lean && n % 2 == 1) {
				return;
			}
			Vec3 at = peakAt[n];
			peaks[n] = show("fmq_peak", at, max ? 9.0F : 8.0F, ModelFx.yawTo(at, origin), 0.0F, 0.0F, climax + 30 - age(), true,
					step(Anim.RISE, 0, 16, 6.0F, 0.0F));
			if (peaks[n] != null) {
				ModelHandle peak = peaks[n];
				at(age() + 16, () -> peak.lightCycle(10, 80, 13 * n));
				Pose3 pose = new Pose3(at.add(0.0D, 6.0D, 0.0D), 0.0F, 0.0F);
				pose.handle = peak;
				track("peak_" + (n + 1), pose);
			}
			spend(Qi.groundRing(0xDFF6FF, 1.5F, 12.0F, 14), at.x, at.y + 0.2D, at.z, 0.0D, 0.0D, 0.0D);
			shards(at.add(0.0D, 1.0D, 0.0D), 10);
		}

		void freeze() {
			if (freezeAt >= 0) {
				return;
			}
			freezeAt = age();
			frozen.clear();
			frozenAt.clear();
			cue("freeze", origin);
			at(freezeAt + 1, this::flakes);
		}

		void hit(int target, Vec3 at) {
			if (freezeAt >= 0 && age() - freezeAt <= 1 && frozenAt.size() < 16) {
				frozen.add(new int[] {target});
				frozenAt.add(at);
			}
		}

		private void flakes() {
			if (lean) {
				return;
			}
			ClientLevel level = Minecraft.getInstance().level;
			int flakes = Math.min(frozenAt.size(), max ? 16 : 8);
			for (int i = 0; i < flakes; i++) {
				Entity entity = level == null ? null : level.getEntity(frozen.get(i)[0]);
				Vec3 foot = entity != null ? entity.position() : frozenAt.get(i).add(0.0D, -1.0D, 0.0D);
				ModelHandle flake = show("fmq_flake", foot.add(0.0D, 0.05D, 0.0D), 1.6F, 30.0F * i, 0.0F, 0.0F, 30, false, step(Anim.GROW, 0, 6, 0.2F, 0.0F));
				if (flake == null) {
					break;
				}
				flake.lightTo(6, 30);
				if (max && entity != null && coffins.size() < 8) {
					float size = Math.max(entity.getBbWidth() * 1.25F, entity.getBbHeight() * 1.05F);
					ModelHandle coffin = ModelFx.show(new ModelRequest("fmq_coffin", entity.position(), Mth.clamp(size, 0.8F, 6.0F), entity.getYRot(), 0.0F, 0.0F,
							160, List.of(step(Anim.GROW, 0, 6, 0.3F, 0.0F), step(Anim.SHATTER, 157, 3, 0.0F, 0.0F)), entity.getId(), Vec3.ZERO, false, true, casterId,
							FxLayer.Priority.DETAIL));
					if (coffin != null) {
						props.add(coffin);
						coffins.add(coffin);
					}
				}
			}
		}

		private void release() {
			cue("release", origin);
			for (int n = 0; n < 6; n++) {
				ModelHandle peak = peaks[n];
				if (peak != null) {
					at(age() + 3 * n, () -> peak.swap("fmq_peak_cracked"));
				}
			}
		}

		void recall() {
			if (recallAt >= 0) {
				return;
			}
			recallAt = age();
			cue("impact", origin);
			for (int n = 0; n < 6; n++) {
				ModelHandle peak = peaks[n];
				if (peak == null) {
					continue;
				}
				Vec3 in = peakAt[n].add(origin.subtract(peakAt[n]).normalize().scale(4.0D));
				peak.swap("fmq_peak_cracked");
				peak.play(new AnimStep(Anim.SLIDE, 0, 4, 0.0F, 2.0F, in));
				peak.play(step(Anim.SHATTER, 4, 3, 0.0F, 0.0F));
				Vec3 burst = in.add(0.0D, 4.0D, 0.0D);
				at(recallAt + 5, () -> shards(burst, 24));
			}
			for (ModelHandle coffin : coffins) {
				coffin.play(step(Anim.SHATTER, 0, 3, 0.0F, 0.0F));
			}
			if (crown != null) {
				crown.play(step(Anim.SHATTER, 2, 3, 0.0F, 0.0F));
			}
			spend(Qi.ring(0xFFFFFF, 3.0F, 30.0F, 8), origin.x, origin.y + 2.0D, origin.z, 0.0D, 0.0D, 0.0D);
			spend(Qi.groundRing(0xCFEFFF, 4.0F, radius * 1.6F, 20), origin.x, origin.y + 0.2D, origin.z, 0.0D, 0.0D, 0.0D);
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (recallAt >= 0 && age > recallAt + 60) {
				return;
			}
			if (age % 2 == 0) {
				// snowfall over the domain (it keeps falling after the recall)
				Vec3 c = casterHere() ? caster.position() : origin;
				for (int i = 0, n = EffectQuality.scale(4); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = Math.sqrt(random.nextDouble()) * radius * 0.6D;
					spend(Qi.snow(0xFFFFFF, 0.45F, 40), c.x + Math.cos(a) * r, c.y + 8.0D + random.nextDouble() * 4.0D, c.z + Math.sin(a) * r, 0.02D, -0.12D,
							0.01D);
				}
			}
			if (recallAt < 0 && age > climax - 20 && age % 2 == 0) {
				// frost streaming from the cracking peaks to the caster
				for (int n = 0; n < 6; n++) {
					if (peaks[n] == null) {
						continue;
					}
					Vec3 p = peakAt[n].add(0.0D, 6.0D, 0.0D);
					Vec3 v = origin.add(0.0D, 1.5D, 0.0D).subtract(p).scale(0.05D);
					spend(Qi.spark(0xDFF6FF, 0.45F, 20), p.x, p.y, p.z, v.x, v.y, v.z);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return recallAt >= 0 ? age > recallAt + 60 : age > climax + 80;
		}
	}

	// ================================================================== Cermin Hampa

	private static final class Mirror extends Run {
		private static final double UP_FROM = 1.7D;
		private static final double UP_TO = 3.2D;
		private static final double AHEAD = 1.6D;

		final Vec3 origin;
		final float yaw;
		final List<ModelHandle> shards = new ArrayList<>();
		final Pose3 mirrorTrack;
		@Nullable ModelHandle mirror;
		@Nullable ModelHandle ring;
		int releaseAt = -1;
		int ripple = -100;
		int blows;

		Mirror(Ev ev) {
			super(ev);
			this.origin = ev.casterPos();
			this.yaw = ev.yaw();
			this.mirrorTrack = new Pose3(origin.add(0.0D, UP_TO, 0.0D), yaw, 0.0F);
			int life = 230;
			if (casterHere()) {
				mirror = ride("vm_mirror", caster, new Vec3(0.0D, UP_FROM, AHEAD), 3.2F, 0.0F, 0.0F, life, true, step(Anim.GROW, 0, 8, 0.1F, 0.0F));
				if (!lean) {
					ring = ride("vm_rune_ring", caster, new Vec3(0.0D, UP_FROM, AHEAD - 0.15D), 3.2F, 0.0F, 0.0F, life, false, step(Anim.GROW, 0, 10, 0.1F, 0.0F),
							step(Anim.SPIN, 0, life, 1.5F, 2.0F));
				}
			} else {
				Vec3 at = origin.add(ev.forward().scale(AHEAD)).add(0.0D, UP_TO, 0.0D);
				mirror = show("vm_mirror", at, 3.2F, yaw, 0.0F, 0.0F, life, true, step(Anim.GROW, 0, 8, 0.1F, 0.0F));
			}
			mirrorTrack.handle = mirror;
			track("mirror", mirrorTrack);
		}

		/** The mirror's offset (x left, y up, z ahead) at run tick {@code t}: rising from the palm, recoiling on the release. */
		private Vec3 offset(int t) {
			double up = UP_FROM + (UP_TO - UP_FROM) * cubic(t / 8.0F);
			double ahead = AHEAD;
			if (releaseAt >= 0) {
				int r = t - releaseAt;
				ahead -= r < 3 ? 0.6D * smooth(r / 3.0F) : 0.6D * (1.0D - smooth((r - 3) / 6.0F));
			}
			return new Vec3(0.0D, up, ahead);
		}

		void blow() {
			if (releaseAt >= 0) {
				return;
			}
			blows++;
			int now = age();
			cue("ripple", mirrorTrack.cur);
			if (mirror != null) {
				mirror.swap("vm_mirror_ripple");
				ripple = now;
				ModelHandle m = mirror;
				at(now + 3, () -> {
					if (age() - ripple >= 3) {
						m.swap("vm_mirror");
					}
				});
			}
			if (!lean && casterHere() && shards.size() < (max ? 12 : 6)) {
				Vec3 at = offset(Math.max(8, now));
				ModelHandle shard = ride("vm_shard", caster, at, 0.6F, 45.0F * blows, 0.0F, 230 - now, false,
						step(Anim.GROW, 0, 4, 0.2F, 0.0F), step(Anim.ORBIT, 0, 230, 1.6F, 90.0F + 10.0F * (blows % 3)), step(Anim.SPIN, 0, 230, 6.0F, 1.0F));
				if (shard != null) {
					shard.lightCycle(8, 30, 5 * blows);
					shards.add(shard);
				}
			}
		}

		void release(Vec3 to) {
			if (releaseAt >= 0) {
				return;
			}
			releaseAt = age();
			cue("release", mirrorTrack.cur);
			cue("impact", to);
			float pitch = casterHere() ? caster.getXRot() : 0.0F;
			if (mirror != null) {
				mirror.swap("vm_mirror_ripple");
				mirror.rotateTo(0.0F, pitch, 0.0F, 4);
				endAt(mirror, releaseAt + 30, 6);
			}
			if (ring != null) {
				ring.rotateTo(0.0F, pitch, 0.0F, 4);
				ring.play(step(Anim.SPIN, 0, 20, 6.5F, 2.0F));
				endAt(ring, releaseAt + 12, 8);
			}
			for (ModelHandle shard : shards) {
				shard.end(4);
			}
			shards.clear();
			Vec3 p = mirrorTrack.cur;
			sparks(p, 0xC8B8FF, 16, 0.5D);
		}

		@Override
		void drive(ClientLevel level, int age) {
			if (!casterHere()) {
				return;
			}
			int t = age + 1;
			Vec3 off = offset(t);
			float body = bodyYaw(yaw);
			mirrorTrack.set(caster.position().add(off.yRot(-body * Mth.DEG_TO_RAD)), body, releaseAt >= 0 ? caster.getXRot() : 0.0F);
			if (t <= 9 || releaseAt >= 0 && t - releaseAt <= 10) {
				if (mirror != null) {
					mirror.follow(caster.getId(), off, true);
				}
				if (ring != null) {
					ring.follow(caster.getId(), off.add(0.0D, 0.0D, -0.15D), true);
				}
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (releaseAt >= 0 || age % 3 != 0) {
				return;
			}
			// portal motes drifting into the glass
			Vec3 c = mirrorTrack.cur;
			for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
				double a = random.nextDouble() * Math.PI * 2.0D;
				Vec3 p = c.add(Math.cos(a) * 2.0D, (random.nextDouble() - 0.5D) * 2.0D, Math.sin(a) * 2.0D);
				Vec3 v = c.subtract(p).scale(0.08D);
				spend(Qi.mote(0x7A4AFF, 0.4F, 16), p.x, p.y, p.z, v.x, v.y, v.z);
			}
		}

		@Override
		boolean finished(int age) {
			return releaseAt >= 0 ? age > releaseAt + 40 : age > 240;
		}
	}

	// ================================================================== Wilayah Roh Waktu

	private static final class TimeDomain extends Run {
		private static final Vec3 CLOCK = new Vec3(0.0D, 4.6D, -3.0D);
		private static final float SIZE = 6.0F;
		private static final int UNFOLD = 10;

		final Vec3 origin;
		final int climax;
		final float yaw;
		final List<Vec3> hits = new ArrayList<>();
		final List<ModelHandle> drops = new ArrayList<>();
		final List<Vec3> dropAt = new ArrayList<>();
		final Pose3 clockTrack;
		@Nullable ModelHandle clockFace;
		@Nullable ModelHandle longHand;
		@Nullable ModelHandle shortHand;
		@Nullable ModelHandle gearL;
		@Nullable ModelHandle gearS;
		@Nullable ModelHandle hourglass;
		float longRoll;
		float shortRoll;
		float gearRoll;
		int resumeAt = -1;

		TimeDomain(Ev ev) {
			super(ev);
			this.origin = ev.casterPos();
			this.climax = ev.climaxAt(160);
			this.yaw = ev.yaw();
			this.clockTrack = new Pose3(origin.add(CLOCK.yRot(-yaw * Mth.DEG_TO_RAD)), yaw + 180.0F, 0.0F);
			int life = climax + 40;
			if (casterHere()) {
				clockFace = ride("tsd_clock", caster, CLOCK, SIZE, 180.0F, 90.0F, life, true);
				longHand = ride("tsd_hand_long", caster, CLOCK, SIZE, 180.0F, 90.0F, life, true);
				shortHand = ride("tsd_hand_short", caster, CLOCK, SIZE, 180.0F, 90.0F, life, false);
				if (!lean) {
					at(2, () -> {
						gearL = ride("tsd_gear_l", caster, gearOffset(9.0D, -5.0D, 90.0F), 3.0F, 180.0F, 90.0F, life, false, step(Anim.GROW, 0, 8, 0.1F, 0.0F));
						gearS = ride("tsd_gear_s", caster, gearOffset(-8.0D, -6.0D, 90.0F), 1.5F, 180.0F, 90.0F, life, false, step(Anim.GROW, 0, 8, 0.1F, 0.0F));
					});
					at(4, () -> hourglass = ride("tsd_hourglass", caster, new Vec3(1.3D, 2.3D, 0.9D), 2.0F, 0.0F, 0.0F, life, false,
							step(Anim.GROW, 0, 8, 0.1F, 0.0F)));
				}
			} else {
				clockFace = show("tsd_clock", clockTrack.cur, SIZE, yaw + 180.0F, 0.0F, 0.0F, life, true, step(Anim.GROW, 0, 10, 0.1F, 0.0F));
			}
			clockTrack.handle = clockFace;
			track("clock", clockTrack);
			at(UNFOLD, this::drops);
			at(climax - 10, this::flip);
		}

		/** A gear's body-frame offset: its pixel spot on the clock face, turned with the clock's tilt. */
		private Vec3 gearOffset(double px, double py, float tilt) {
			Vector3f d = new Vector3f((float) (-px / 16.0D * SIZE), (float) (py / 16.0D * SIZE), (float) (-1.0D / 16.0D * SIZE));
			new Quaternionf().rotateY(-180.0F * Mth.DEG_TO_RAD).rotateX(tilt * Mth.DEG_TO_RAD).transform(d);
			return CLOCK.add(d.x, d.y, d.z);
		}

		void hit(Vec3 at) {
			if (age() <= 30 && hits.size() < 12) {
				hits.add(at);
			}
		}

		private void drops() {
			if (lean) {
				return;
			}
			int cap = max ? 24 : 12;
			RandomSource random = RandomSource.create(seed);
			List<Vec3> spots = new ArrayList<>();
			if (hits.isEmpty()) {
				for (int i = 0; i < 6; i++) {
					double a = Math.PI * 2.0D * i / 6.0D + random.nextDouble() * 0.5D;
					double r = 3.0D + random.nextDouble() * 3.0D;
					spots.add(origin.add(Math.cos(a) * r, 1.0D + random.nextDouble() * 2.0D, Math.sin(a) * r));
				}
			} else {
				for (Vec3 hit : hits) {
					for (int k = 0; k < 2 && spots.size() < cap; k++) {
						spots.add(hit.add((random.nextDouble() - 0.5D) * 2.4D, 0.4D + random.nextDouble() * 1.6D, (random.nextDouble() - 0.5D) * 2.4D));
					}
				}
			}
			for (Vec3 spot : spots) {
				ModelHandle drop = show("tsd_drop", spot, 0.4F, 0.0F, 0.0F, 0.0F, climax + 30 - age(), false, step(Anim.GROW, 0, 6, 0.2F, 0.0F));
				if (drop == null) {
					break;
				}
				drops.add(drop);
				dropAt.add(spot);
			}
		}

		private void flip() {
			cue("release", clockTrack.cur);
			if (hourglass != null) {
				hourglass.rotateTo(0.0F, 0.0F, 180.0F, 8);
			}
		}

		void resume() {
			if (resumeAt >= 0) {
				return;
			}
			resumeAt = age();
			cue("impact", clockTrack.cur);
			for (int i = 0; i < drops.size(); i++) {
				drops.get(i).play(new AnimStep(Anim.SLIDE, 0, 8, 0.0F, 2.0F, dropAt.get(i).add(0.0D, -2.0D, 0.0D)));
				endAt(drops.get(i), resumeAt + 9, 2);
			}
			endAt(gearL, resumeAt + 8, 8);
			endAt(gearS, resumeAt + 8, 8);
			endAt(hourglass, resumeAt + 10, 10);
			// it hands over to the clock monument by a short crossfade
			endAt(clockFace, resumeAt + 24, 6);
			endAt(longHand, resumeAt + 24, 6);
			endAt(shortHand, resumeAt + 24, 6);
			at(resumeAt + 24, () -> clockTrack.ended = true);
			Vec3 c = clockTrack.cur;
			spend(Qi.ring(0xFFF4C8, 2.0F, 18.0F, 8), c.x, c.y, c.z, 0.0D, 0.0D, 0.0D);
			spend(Qi.groundRing(0xE0E8FF, 3.0F, 48.0F, 18), origin.x, origin.y + 0.2D, origin.z, 0.0D, 0.0D, 0.0D);
		}

		@Override
		void drive(ClientLevel level, int age) {
			if (!casterHere()) {
				return;
			}
			int t = age + 1;
			float tilt = 90.0F * (1.0F - smooth(t / (float) UNFOLD));
			if (resumeAt >= 0 && t - resumeAt <= 10) {
				// time resumes: the hands race forward (three turns and one in 10 ticks)
				longRoll -= 108.0F;
				shortRoll -= 36.0F;
			} else if (t <= UNFOLD) {
				longRoll -= 18.0F;
				shortRoll -= 1.5F;
				gearRoll += 6.0F;
			} else if (resumeAt < 0) {
				// stopped time: gears and sand stand still, the hands crawl backwards
				longRoll += 0.5F;
				shortRoll += 0.04F;
			}
			float body = bodyYaw(yaw);
			clockTrack.set(caster.position().add(CLOCK.yRot(-body * Mth.DEG_TO_RAD)), body + 180.0F, tilt);
			if (clockFace != null) {
				clockFace.rotateTo(180.0F, tilt, 0.0F, 1);
			}
			if (longHand != null) {
				longHand.rotateTo(180.0F, tilt, longRoll, 1);
			}
			if (shortHand != null) {
				shortHand.rotateTo(180.0F, tilt, shortRoll, 1);
			}
			boolean burst = resumeAt >= 0;
			float out = burst ? 6.0F * cubic((t - resumeAt) / 8.0F) : 0.0F;
			if (gearL != null && (t <= UNFOLD + 1 || burst)) {
				gearL.follow(caster.getId(), gearOffset(9.0D + out * 16.0D / SIZE, -5.0D, tilt), true);
				gearL.rotateTo(180.0F, tilt, gearRoll, 1);
			}
			if (gearS != null && (t <= UNFOLD + 1 || burst)) {
				gearS.follow(caster.getId(), gearOffset(-8.0D - out * 16.0D / SIZE, -6.0D, tilt), true);
				gearS.rotateTo(180.0F, tilt, -gearRoll * 1.6F, 1);
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (resumeAt >= 0) {
				return;
			}
			if (age > UNFOLD && age % 4 == 0) {
				// grey motes hanging still in the stopped air
				for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = 2.0D + random.nextDouble() * 10.0D;
					spend(Qi.mote(0xC8C8C0, 0.35F, 40), origin.x + Math.cos(a) * r, origin.y + 0.5D + random.nextDouble() * 4.0D, origin.z + Math.sin(a) * r,
							0.0D, 0.0D, 0.0D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return resumeAt >= 0 ? age > resumeAt + 40 : age > climax + 60;
		}
	}

	// ================================================================== Lubang Hitam

	private static final class BlackHole extends Run {
		private static final int MAX_SPINUPS = 12;

		final Vec3 core;
		final int climax;
		final List<Debris> debris = new ArrayList<>();
		final Pose3 coreTrack;
		final Pose3 diskTrack;
		@Nullable ModelHandle coreHandle;
		@Nullable ModelHandle disk;
		int pulses;
		int lastPulse = -100;
		int collapseAt = -1;
		boolean released;

		BlackHole(Ev ev) {
			super(ev);
			this.core = ev.pos().add(0.0D, 4.0D, 0.0D);
			this.climax = ev.climaxAt(120);
			this.coreTrack = new Pose3(core, 0.0F, 0.0F);
			this.diskTrack = new Pose3(core, 0.0F, 10.0F);
			track("core", coreTrack);
			if (!lean) {
				show("sbh_star", core, 3.0F, 0.0F, 0.0F, 0.0F, 21, false, step(Anim.GROW, 0, 10, 0.1F, 0.0F), step(Anim.FLATTEN, 12, 4, 0.1F, 1.6F),
						step(Anim.SHRINK, 16, 4, 0.0F, 0.0F));
			}
			at(16, this::core);
			if (!lean) {
				at(18, this::disk);
			}
			at(climax - 10, this::release);
		}

		private void core() {
			int life = climax + 40 - age();
			coreHandle = show("sbh_core", core, 4.0F, 0.0F, 0.0F, 0.0F, life, true, step(Anim.GROW, 0, 10, 0.05F, 0.0F), step(Anim.SPIN, 0, life, 2.0F, 0.0F));
			coreTrack.handle = coreHandle;
			spend(Qi.ring(0xFFFFF0, 2.0F, 12.0F, 8), core.x, core.y, core.z, 0.0D, 0.0D, 0.0D);
		}

		private void disk() {
			int life = climax + 10 - age();
			// stands on edge, unfolds to a 10 degree tilt, then spins in its own plane (TILT before SPIN)
			disk = show("sbh_disk", core, 10.0F, 0.0F, 90.0F, 0.0F, life, false, step(Anim.GROW, 0, 10, 0.2F, 0.0F), step(Anim.TILT, 0, 16, -80.0F, 0.0F),
					step(Anim.SPIN, 0, life, 3.0F, 0.0F));
			diskTrack.handle = disk;
			track("disk", diskTrack);
		}

		void pulse() {
			int now = age();
			if (now - lastPulse < 5 || collapseAt >= 0) {
				return;
			}
			lastPulse = now;
			pulses++;
			if (disk != null && pulses <= MAX_SPINUPS) {
				disk.play(step(Anim.SPIN, 0, Math.max(1, climax + 10 - now), 0.5F, 0.0F));
			}
			if (!lean) {
				int n = max ? 4 : 2;
				for (int i = 0; i < n && debris.size() < 12; i++) {
					long s = seed + 31L * pulses + 7L * i;
					double a = Math.floorMod(s * 47L, 360L) * Mth.DEG_TO_RAD;
					double r = 12.0D + Math.floorMod(s, 5L);
					double h = (Math.floorMod(s >> 2, 5L) - 2) * 1.0D;
					Vec3 at = core.add(Math.cos(a) * r, h, Math.sin(a) * r);
					Rig rig = rig("sbh_debris", at, 0.8F, 0.0F, 0.0F, 0.0F, 30, false, step(Anim.SPIN, 0, 30, 9.0F, 1.0F));
					if (rig.handle == null) {
						break;
					}
					debris.add(new Debris(rig, now, a, r, h));
				}
			}
		}

		private void release() {
			released = true;
			cue("release", core);
			if (disk != null) {
				disk.play(step(Anim.FLATTEN, 0, 8, 0.35F, 1.1F));
			}
			if (coreHandle != null) {
				coreHandle.lightTo(15, 2);
			}
		}

		void collapse() {
			if (collapseAt >= 0) {
				return;
			}
			collapseAt = age();
			cue("impact", core);
			float jet = max ? 14.0F : 12.0F;
			for (float pitch : new float[] {0.0F, 180.0F}) {
				ModelHandle j = show("sbh_jet", core, jet, 0.0F, pitch, 0.0F, 30, true, step(Anim.GROW, 0, 6, 0.1F, 0.0F));
				endAt(j, collapseAt + 16, 10);
			}
			if (disk != null) {
				disk.play(step(Anim.SHATTER, 0, 3, 0.0F, 0.0F));
				diskTrack.ended = true;
			}
			if (coreHandle != null) {
				coreHandle.play(step(Anim.PULSE, 0, 6, 0.1F, 0.0F));
				ModelHandle c = coreHandle;
				// shrinks to the hovering monument's size, then crossfades to it
				at(collapseAt + 6, () -> c.scaleTo(1.0F, 20));
				at(collapseAt + 30, () -> {
					c.end(6);
					coreTrack.ended = true;
				});
			}
			for (Debris d : debris) {
				d.rig.end(3);
			}
			debris.clear();
			spend(Qi.ring(0xFFE8C0, 3.0F, 32.0F, 8), core.x, core.y, core.z, 0.0D, 0.0D, 0.0D);
			spend(Qi.groundRing(0xFF8A20, 4.0F, 48.0F, 20), core.x, core.y - 3.8D, core.z, 0.0D, 0.0D, 0.0D);
			sparks(core, 0xFFF0C0, 40, 1.4D);
		}

		@Override
		void drive(ClientLevel level, int age) {
			for (Iterator<Debris> it = debris.iterator(); it.hasNext(); ) {
				Debris d = it.next();
				float u = Mth.clamp((age - d.born + 1) / 24.0F, 0.0F, 1.0F);
				if (u >= 1.0F || !d.rig.alive()) {
					d.rig.end(3);
					it.remove();
					continue;
				}
				double r = d.radius * Math.pow(1.0D - u, 1.2D) + 0.8D;
				double a = d.angle + (age - d.born) * 8.0D * Mth.DEG_TO_RAD * (1.0D + 2.0D * u);
				d.rig.place(core.add(Math.cos(a) * r, d.height * (1.0D - u), Math.sin(a) * r), (float) Math.toDegrees(a), 0.0F, 0.0F);
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (collapseAt >= 0 || age < 16) {
				return;
			}
			// matter streaming round the disc and falling in
			for (int i = 0, n = EffectQuality.scale(released ? 5 : 3); i < n; i++) {
				double a = random.nextDouble() * Math.PI * 2.0D;
				double r = 6.0D + random.nextDouble() * 6.0D;
				double x = core.x + Math.cos(a) * r, z = core.z + Math.sin(a) * r;
				double speed = 0.5D;
				spend(Qi.spark(i % 2 == 0 ? 0xFF8A20 : 0xFFF0C0, 0.45F, 14), x, core.y + (random.nextDouble() - 0.5D) * 0.8D, z,
						-Math.sin(a) * speed + (core.x - x) * 0.04D, 0.0D, Math.cos(a) * speed + (core.z - z) * 0.04D);
			}
		}

		@Override
		boolean finished(int age) {
			return collapseAt >= 0 ? age > collapseAt + 40 : age > climax + 60;
		}
	}

	private static final class Debris {
		final Rig rig;
		final int born;
		final double angle;
		final double radius;
		final double height;

		Debris(Rig rig, int born, double angle, double radius, double height) {
			this.rig = rig;
			this.born = born;
			this.angle = angle;
			this.radius = radius;
			this.height = height;
		}
	}

	// ================================================================== helpers

	private static AnimStep step(Anim kind, int start, int duration, float a, float b) {
		return AnimStep.of(kind, start, duration, a, b);
	}

	private static float smooth(float x) {
		float t = Mth.clamp(x, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/** easeOutCubic. */
	private static float cubic(float x) {
		float u = 1.0F - Mth.clamp(x, 0.0F, 1.0F);
		return 1.0F - u * u * u;
	}

	private static float square(float x) {
		float t = Mth.clamp(x, 0.0F, 1.0F);
		return t * t;
	}

	private static boolean finite(Vec3 v) {
		return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
	}

	/**
	 * Where model pixel {@code (px, py, pz)} (from the anchor) of a prop at {@code base} turned by {@code yaw, pitch, roll}
	 * and shown at {@code scale} lands in the world (the item renderer turns models 180 degrees about y, as ModelFx's parts).
	 */
	private static Vec3 local(Vec3 base, float yaw, float pitch, float roll, float scale, double px, double py, double pz) {
		Vector3f v = new Vector3f((float) (-px / 16.0D * scale), (float) (py / 16.0D * scale), (float) (-pz / 16.0D * scale));
		new Quaternionf().rotateY(-yaw * Mth.DEG_TO_RAD).rotateX(pitch * Mth.DEG_TO_RAD).rotateZ(roll * Mth.DEG_TO_RAD).transform(v);
		return base.add(v.x, v.y, v.z);
	}

	/** The top of the ground at (x, z) on the client, or {@code fallback} when it is far off (a cliff, an unloaded chunk). */
	private static double ground(ClientLevel level, double x, double z, double fallback) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
		if (top <= level.getMinY() || Math.abs(top - fallback) > 10.0D) {
			return fallback;
		}
		return top;
	}

	private static void sparks(Vec3 at, int rgb, int count, double speed) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int i = 0, n = EffectQuality.scale(count); i < n; i++) {
			double a = Math.PI * 2.0D * i / n + random.nextDouble() * 0.3D;
			double s = speed * (0.5D + random.nextDouble() * 0.6D);
			if (!spend(Qi.spark(i % 3 == 0 ? 0xFFFFFF : rgb, 0.5F, 16), at.x, at.y, at.z, Math.cos(a) * s, 0.1D + random.nextDouble() * 0.4D * speed,
					Math.sin(a) * s)) {
				return;
			}
		}
	}

	private static void shards(Vec3 at, int count) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int i = 0, n = EffectQuality.scale(count); i < n; i++) {
			double a = Math.PI * 2.0D * i / n + random.nextDouble() * 0.2D;
			double s = 0.4D + random.nextDouble() * 0.6D;
			if (!spend(Qi.shard(i % 3 == 0 ? 0xFFFFFF : 0xCFEFFF, 0.6F, 30), at.x, at.y + random.nextDouble() * 2.0D, at.z, Math.cos(a) * s,
					0.1D + random.nextDouble() * 0.3D, Math.sin(a) * s)) {
				return;
			}
		}
	}

	/** One particle within the shared budget (DETAIL: it never starves the spec's own core layers). */
	private static boolean spend(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			return false;
		}
		int life = Qi.lifeOf(particle);
		int cost = FxBudget.cost(life > 0 ? life : 20);
		int granted = FxBudget.take(cost, FxBudget.Priority.DETAIL);
		if (granted < cost) {
			FxBudget.refund(granted);
			return false;
		}
		level.addParticle(particle, true, false, x, y, z, vx, vy, vz);
		return true;
	}

	private UltimatesA() {
	}
}
