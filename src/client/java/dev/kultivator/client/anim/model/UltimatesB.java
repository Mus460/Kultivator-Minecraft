package dev.kultivator.client.anim.model;

import dev.kultivator.Kultivator;
import dev.kultivator.batin.CiptaanArts;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.batin.BatinClient;
import dev.kultivator.client.epic.film.FilmDirector;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module U2 of Phase 30 (DESIGN 30.4.6-30.4.9, contract C30.1): the 3D props and the choreography of the Body, Life,
 * Shadow and Immortal ultimates and of the own arts, on the same terms as {@link FireUltimates}: one clock of its own
 * (client ticks, paused with the game, held by a hit-stop), timed beats on the server's events in four clear acts
 * (build-up, release, impact, aftermath), deterministic positions and counts from the event, and no random size change:
 * props grow in once, unfold or fold by eased tilts, turn at a steady speed, breathe by light and leave by one fade, one
 * SHATTER or one DISSOLVE.
 * <ul>
 * <li><b>Wujud Kosmik Langit-Bumi</b> ({@code cosmic_giant_form}): a 36-block gold-stone colossus rises behind the caster,
 * lifts a leg on each phantom warning and stamps it down on the stomp, kneels at K-20 raising its right fist and slams
 * it at K, then dissolves upward from the feet.</li>
 * <li><b>Injakan Dewa Kuno</b> ({@code ancient_god_trample}): nine dim star marks on the brow, a footprint glyph before
 * each step and the bronze foot falling on it (one more star lights per step), then a 3x foot forming 40 blocks up and
 * stamping at K.</li>
 * <li><b>Embun Manis Guanyin</b> ({@code guanyin_sweet_dew}): the lotus seat rises, the jade vase and the willow grow on
 * it, the willow flicks a ring of dew drops onto the field each pulse, the vase tips and pours an arc of dew at K.</li>
 * <li><b>Wilayah Hidup dan Mati</b> ({@code life_death_domain}): the life-death diagram unfolds and turns under the
 * caster, soul lanterns drift round its rim, it flips from green to grey at K and a half-withered lotus grows.</li>
 * <li><b>Pohon Kuno Bodhisattva</b> ({@code bodhisattva_tree}): the trunk rises, three canopy tiers grow, a root arm
 * crawls out at every nova and a fruit lights, the fruits burst into light at K and the tree leaves top-down.</li>
 * <li><b>Segel Harimau Alam Baka</b> ({@code stygian_tiger_seal}): the flute at the lips, the tiger tally rising and
 * splitting into its halves that circle the caster and clap together on every nova.</li>
 * <li><b>Teknik Pusaka Kunpeng</b> ({@code kunpeng_art}): the Kun rises from a cloud sea, turns and becomes the Peng,
 * which rides above the dash shedding wind crescents and gives one great flap on the wave.</li>
 * <li><b>Wilayah Karma</b> ({@code karma_domain}, disciples): the karma wheel over the head ticks a spoke each pulse,
 * red knots on the linked foes.</li>
 * <li><b>Segel Kesembilan</b> ({@code ninth_hex}): talisman pillars rise on the octagram points, the eight-pointed seal
 * appears overhead and stamps down at the release, the pillars lean in and crack.</li>
 * <li><b>Gunung Lima Unsur</b> ({@code five_elements_mountain}): the Buddha's palm falls 64 blocks, the five element peaks
 * rise in generating order, the six-syllable talisman descends.</li>
 * <li><b>Diagram Taiji</b> ({@code tai_chi_diagram}): the yin-yang disc spins between the palms, flies out and unrolls
 * into yin and yang planks that flip under the struck, then folds back and stands upright.</li>
 * <li><b>Langkah Penginjak Langit</b> ({@code heaven_trampling}): a ring of light under each of the nine steps, a Dipper
 * star lit per step, a foot of light from the heavens stamping at K.</li>
 * <li><b>Jurus ciptaan</b> ({@code own_art_1..3}): the Dao seals behind the caster, one prop per form, the seals spin up
 * and shatter on the climax.</li>
 * </ul>
 * Budgets: every prop through {@link ModelFx} (its caps; CORE for the identity props, DETAIL for extras; Minimal keeps
 * the identity prop of each beat), every particle through {@link FxBudget} (DETAIL). Films are asked for on the CAST (or
 * the CHARGE) of the own caster; the choreography never depends on whether one plays.
 */
public final class UltimatesB {

	private static final Set<String> IDS = Set.of("cosmic_giant_form", "ancient_god_trample", "guanyin_sweet_dew", "life_death_domain",
			"bodhisattva_tree", "stygian_tiger_seal", "kunpeng_art", "karma_domain", "ninth_hex", "five_elements_mountain", "tai_chi_diagram",
			"heaven_trampling", "own_art_1", "own_art_2", "own_art_3");
	private static final List<Run> RUNS = new ArrayList<>();
	private static long clock;
	private static int warnings;

	// ================================================================== the driver API (contract C30.1)

	/** True for the spec ids of DESIGN 30.4.6-30.4.9 (this driver places their props). */
	public static boolean handles(String specId) {
		return specId != null && IDS.contains(specId);
	}

	/** Every TechFx event of a handled spec (ABORT included). */
	public static void onTechFx(Payloads.TechFx p, TechniqueSpec spec, Phase phase, @Nullable Entity caster) {
		try {
			if (phase == Phase.ABORT) {
				abort(p.caster());
				return;
			}
			Ev ev = new Ev(p, spec, phase, caster);
			switch (spec.id()) {
				case "cosmic_giant_form" -> Giant.on(ev);
				case "ancient_god_trample" -> Trample.on(ev);
				case "guanyin_sweet_dew" -> Dew.on(ev);
				case "life_death_domain" -> Domain.on(ev);
				case "bodhisattva_tree" -> Tree.on(ev);
				case "stygian_tiger_seal" -> Tiger.on(ev);
				case "kunpeng_art" -> Kunpeng.on(ev);
				case "karma_domain" -> Karma.on(ev);
				case "ninth_hex" -> Hex.on(ev);
				case "five_elements_mountain" -> Mountain.on(ev);
				case "tai_chi_diagram" -> Taiji.on(ev);
				case "heaven_trampling" -> Trampling.on(ev);
				default -> {
					if (CiptaanArts.slotOf(spec.id()) >= 0) {
						Own.on(ev);
					}
				}
			}
		} catch (RuntimeException exception) {
			if (warnings++ < 5) {
				Kultivator.LOGGER.warn("Kultivator: ultimate choreography (B) error for {}", spec.id(), exception);
			}
		}
	}

	/** Once per unpaused client tick, after FireUltimates.tick. */
	public static void tick(ClientLevel level) {
		if (dev.kultivator.client.epic.EpicClock.frozen() && !RUNS.isEmpty()) {
			// a hit-stop holds the choreography's clock (σ = 0) like it holds the props
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
					Kultivator.LOGGER.warn("Kultivator: ultimate choreography (B) tick error", exception);
				}
			}
			if (!keep) {
				run.close(6);
				it.remove();
			}
		}
		clock++;
	}

	/** Ends every run of a caster (idempotent; fade 6). */
	public static void abort(int casterId) {
		for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
			Run run = it.next();
			if (run.casterId == casterId) {
				boolean film = run.film;
				run.close(6);
				it.remove();
				if (film) {
					FilmDirector.abort(casterId);
				}
			}
		}
	}

	/** Drops every run (world change, disconnect; ModelFx clears the props themselves). */
	public static void clear() {
		RUNS.clear();
	}

	/** The Info rows of this driver's models (scale, axis, glow, particle hint, lowest point, parts). */
	public static void models(FxModels.Sink sink) {
		FxModels.Axis up = FxModels.Axis.UP;
		FxModels.Axis north = FxModels.Axis.NORTH;
		// Body · Wujud Kosmik Langit-Bumi
		sink.put("cgf_giant", 12.0F, north, true, 0xC8A040, 0xFFD54A, -0.41F);
		sink.put("cgf_leg_l", 12.0F, north, true, 0xC8A040, 0xFFD54A, -1.5F);
		sink.put("cgf_leg_r", 12.0F, north, true, 0xC8A040, 0xFFD54A, -1.5F);
		sink.put("cgf_arm_l", 12.0F, north, true, 0xC8A040, 0xFFD54A, -1.48F);
		sink.put("cgf_arm_r", 12.0F, north, true, 0xC8A040, 0xFFD54A, -1.48F);
		sink.put("cgf_footprint", 4.0F, north, true, 0xFFE88A, 0xFFF8D8, 0.0F);
		sink.put("cgf_ring", 12.0F, north, true, 0xFFF8E0, 0xC8A040, -0.02F);
		// Body · Injakan Dewa Kuno
		sink.put("agt_foot", 10.0F, north, true, 0xA86A32, 0xC8C8D8, 0.0F);
		sink.put("agt_brow_star", 0.15F, north, true, 0xE8ECF4, 0xFFFFFF, -0.44F);
		sink.put("agt_glyph", 4.0F, north, true, 0x8A6A20, 0xFFD878, 0.0F);
		sink.put("agt_ring", 12.0F, north, true, 0xFFFFFF, 0xC8C8D8, -0.02F);
		// Life · Embun Manis Guanyin
		sink.put("gsd_vase", 4.0F, up, true, 0xEEF2F8, 0x9FF3FF, 0.0F);
		sink.put("gsd_willow", 2.0F, up, true, 0x3FAE3A, 0xB8FFA0, 0.0F);
		sink.put("gsd_seat", 3.0F, north, true, 0xFF9EC8, 0xFFFFFF, -0.13F);
		sink.put("gsd_drop", 0.4F, north, true, 0xE8F8FF, 0xFFFFFF, -0.08F);
		sink.put("gsd_ring", 10.0F, north, true, 0xFFFFFF, 0x9FF3FF, -0.02F);
		// Life · Wilayah Hidup dan Mati
		sink.put("ldd_disc", 16.0F, north, true, 0x9AE05A, 0xA0A0A0, -0.01F);
		sink.put("ldd_lantern", 0.8F, north, true, 0xE8E0C8, 0x80C0FF, -0.5F);
		sink.put("ldd_lotus", 3.0F, north, true, 0x9AE05A, 0x707070, -0.01F);
		sink.put("ldd_ring", 12.0F, north, true, 0xFFFFFF, 0xC8D8C0, -0.02F);
		// Life · Pohon Kuno Bodhisattva
		sink.put("bt_trunk", 12.0F, north, true, 0x5A3A20, 0x3FAE3A, 0.0F);
		sink.put("bt_canopy_1", 12.0F, north, true, 0x3FAE3A, 0xFFD54A, -0.24F);
		sink.put("bt_canopy_2", 12.0F, north, true, 0x3FAE3A, 0xFFD54A, -0.23F);
		sink.put("bt_canopy_3", 12.0F, north, true, 0x3FAE3A, 0xFFD54A, -0.22F);
		sink.put("bt_root", 6.0F, north, true, 0x5A3A20, 0x9AE05A, -0.09F);
		sink.put("bt_fruit", 0.6F, north, true, 0xFFF0A0, 0xFFFFFF, -0.13F);
		sink.put("bt_ring", 12.0F, north, true, 0xFFF8C0, 0x9AE05A, -0.02F);
		// Shadow · Segel Harimau Alam Baka
		sink.put("sts_tally", 1.2F, north, true, 0x2A2A33, 0xFF6A2A, -0.25F);
		sink.put("sts_tally_l", 1.2F, north, true, 0x2A2A33, 0xFF6A2A, -0.25F);
		sink.put("sts_tally_r", 1.2F, north, true, 0x2A2A33, 0xFF6A2A, -0.25F);
		sink.put("sts_flute", 0.8F, north, true, 0x1A1A22, 0xC0201A, -0.56F);
		sink.put("sts_ring", 10.0F, north, true, 0xFF8A6A, 0x802030, -0.02F);
		// Shadow · Teknik Pusaka Kunpeng (the Peng's wings flap and its tail trails as parts)
		sink.put("kp_kun", 16.0F, north, true, 0x1A2A4A, 0xC8D8E8, -0.81F);
		sink.put("kp_peng", 20.0F, north, true, 0x3A5A9A, 0xE8F0FF, -0.28F,
				new FxModels.Part("kp_wing_r", 4.0F, 2.0F, 1.0F, Anim.FLAP, 22.0F, 10.0F),
				new FxModels.Part("kp_wing_l", -4.0F, 2.0F, 1.0F, Anim.FLAP, -22.0F, 10.0F),
				new FxModels.Part("kp_tail", 0.0F, 0.0F, 8.0F, Anim.NONE, 0.0F, 0.0F));
		sink.put("kp_wing_r", 20.0F, north, true, 0x3A5A9A, 0xE8F0FF, -0.07F);
		sink.put("kp_wing_l", 20.0F, north, true, 0x3A5A9A, 0xE8F0FF, -0.07F);
		sink.put("kp_tail", 20.0F, north, true, 0x3A5A9A, 0xE8F0FF, -0.03F);
		sink.put("kp_crescent", 3.0F, north, true, 0xE8F0FF, 0xFFFFFF, -0.01F);
		sink.put("kp_ring", 12.0F, north, true, 0xFFFFFF, 0xC8DCFF, -0.02F);
		// Shadow · Wilayah Karma
		sink.put("kd_wheel", 4.0F, north, true, 0x3A2018, 0xD04020, -0.04F);
		sink.put("kd_knot", 0.5F, north, true, 0xD04020, 0xFFD54A, -0.31F);
		// Immortal · Segel Kesembilan
		sink.put("nh_seal", 12.0F, north, true, 0xE8D8A0, 0x7A6AA0, -0.03F);
		sink.put("nh_pillar", 3.0F, north, true, 0xC8BEDC, 0xF0D890, 0.0F);
		sink.put("nh_pillar_cracked", 3.0F, north, true, 0xC8BEDC, 0xB080FF, 0.0F);
		sink.put("nh_ring", 12.0F, north, true, 0xFFF8E0, 0x7A6AA0, -0.02F);
		// Immortal · Gunung Lima Unsur
		sink.put("fem_palm", 24.0F, north, true, 0xFFD54A, 0xFFF2B0, -0.15F);
		sink.put("fem_peak_metal", 10.0F, north, true, 0xE8E8EE, 0xFFFFFF, 0.0F);
		sink.put("fem_peak_wood", 10.0F, north, true, 0x3FAE3A, 0x8AE07A, 0.0F);
		sink.put("fem_peak_water", 10.0F, north, true, 0x3A7AE8, 0xC8E8FF, 0.0F);
		sink.put("fem_peak_fire", 10.0F, north, true, 0xE03A1A, 0xFFB040, 0.0F);
		sink.put("fem_peak_earth", 10.0F, north, true, 0xD9A05B, 0xF0C880, 0.0F);
		sink.put("fem_talisman", 3.0F, up, true, 0xF0D890, 0xFFD54A, -1.0F);
		sink.put("fem_ring", 12.0F, north, true, 0xFFFFF0, 0xFFD54A, -0.02F);
		// Immortal · Diagram Taiji (the bagua ring counter-spins as a part)
		sink.put("tcd_taiji", 9.0F, north, true, 0xF8F6EC, 0x16141C, -0.54F, new FxModels.Part("tcd_bagua", 0.0F, 0.0F, 0.0F, Anim.SPIN, -9.0F, 2.0F));
		sink.put("tcd_bagua", 9.0F, north, true, 0xFFD54A, 0x16141C, -0.93F);
		sink.put("tcd_plank_yin", 3.0F, north, true, 0x16141C, 0xFFD54A, -0.05F);
		sink.put("tcd_plank_yang", 3.0F, north, true, 0xF8F6EC, 0xFFD54A, -0.05F);
		// Immortal · Langkah Penginjak Langit
		sink.put("ht_step", 2.0F, north, true, 0xFFF4D0, 0xFFFFFF, -0.02F);
		sink.put("ht_star", 0.6F, north, true, 0xFFFFFF, 0xF0E0A0, -0.5F);
		sink.put("ht_sky_foot", 12.0F, north, true, 0xFFF4D0, 0xF0E0A0, 0.0F);
		sink.put("ht_ring", 12.0F, north, true, 0xFFFFFF, 0xF0E0A0, -0.02F);
		// own arts: the Dao seals and the six forms
		for (Dao dao : Dao.values()) {
			sink.put("oa_seal_" + dao.id(), 4.0F, north, true, dao.color() & 0xFFFFFF, 0xFFD54A, -0.63F);
		}
		sink.put("oa_seal_none", 4.0F, north, true, 0xFFD54A, 0xFFFFFF, -0.63F);
		sink.put("oa_blade", 3.0F, up, true, 0xF4F4FF, 0xFFD54A, -0.25F);
		sink.put("oa_orb", 2.0F, north, true, 0xFFFFFF, 0xFFD54A, -0.44F);
		sink.put("oa_needle", 1.0F, north, true, 0xF4F4FF, 0xFFD54A, -0.06F);
		sink.put("oa_crescent", 3.0F, north, true, 0xFFFFFF, 0xFFD54A, -0.08F);
		sink.put("oa_rib", 4.0F, north, true, 0xF4F4FF, 0xFFD54A, 0.0F);
		sink.put("oa_plate", 4.0F, north, true, 0xFFD54A, 0xF4F4FF, -0.06F);
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

		float radius() {
			return Float.isFinite(p.radius()) ? p.radius() : 0.0F;
		}

		boolean preImpact() {
			return p.has(Payloads.TechFx.FLAG_PRE_IMPACT);
		}

		float yaw() {
			if (caster != null) {
				return caster.getYRot();
			}
			return hasTo() ? ModelFx.yawTo(pos(), to()) : 0.0F;
		}

		Vec3 casterPos() {
			return caster != null ? caster.position() : pos();
		}

		/** Roughly where the caster's right hand is. */
		Vec3 hand() {
			if (caster == null) {
				return pos().add(0.0D, 1.2D, 0.0D);
			}
			Vec3 eye = caster.getEyePosition();
			Vec3 look = caster.getLookAngle();
			return eye.add(0.0D, -0.35D, 0.0D).add(right(caster.getYRot()).scale(0.35D)).add(look.scale(0.5D));
		}

		boolean own() {
			Player player = Minecraft.getInstance().player;
			return player != null && caster == player;
		}
	}

	// ================================================================== a run: timed beats, driven props, tracks

	/** A prop whose base point (and turn) the run sets each tick: it moves smoothly with a one-tick interpolation. */
	private interface Path {
		@Nullable Vec3 at(float t);
	}

	private interface Turn {
		float yaw(float t);
	}

	private static final class Driven {
		final ModelHandle handle;
		final Path path;
		final @Nullable Turn turn;
		final float pitch;

		Driven(ModelHandle handle, Path path, @Nullable Turn turn, float pitch) {
			this.handle = handle;
			this.path = path;
			this.turn = turn;
			this.pitch = pitch;
		}

		void key(float t) {
			Vec3 at = path.at(t);
			if (at != null && Double.isFinite(at.x) && Double.isFinite(at.y) && Double.isFinite(at.z)) {
				handle.moveTo(at, 1);
			}
			if (turn != null) {
				float yaw = turn.yaw(t);
				if (Float.isFinite(yaw)) {
					handle.rotateTo(yaw, pitch, 0.0F, 1);
				}
			}
		}
	}

	/** A prop position the film can frame (P(key) spaces of DESIGN 30.2.5). */
	private static final class PropTrack implements FilmDirector.Track {
		final Run run;
		final Path path;
		final @Nullable ModelHandle handle;
		final float yaw;
		final float pitch;
		Vec3 last = Vec3.ZERO;

		PropTrack(Run run, Path path, @Nullable ModelHandle handle, float yaw, float pitch) {
			this.run = run;
			this.path = path;
			this.handle = handle;
			this.yaw = yaw;
			this.pitch = pitch;
		}

		@Override
		public Vec3 pos(float partial) {
			Vec3 at = path.at(run.age() + partial);
			if (at != null) {
				last = at;
			}
			return last;
		}

		@Override
		public float yaw(float partial) {
			return yaw;
		}

		@Override
		public float pitch(float partial) {
			return pitch;
		}

		@Override
		public boolean alive() {
			return RUNS.contains(run) && (handle == null || handle.alive());
		}
	}

	private abstract static class Run {
		final int casterId;
		final long start = clock;
		private final List<Beat> beats = new ArrayList<>();
		final List<ModelHandle> props = new ArrayList<>();
		private final List<Driven> driven = new ArrayList<>();
		private final List<String> tracks = new ArrayList<>(4);
		boolean film;

		Run(int casterId) {
			this.casterId = casterId;
		}

		int age() {
			return (int) (clock - start);
		}

		/** Runs {@code action} at run tick {@code at} (at once when it is already past). */
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
			for (Iterator<Driven> it = driven.iterator(); it.hasNext(); ) {
				Driven d = it.next();
				if (!d.handle.alive()) {
					it.remove();
				} else {
					d.key(age + 1.0F);
				}
			}
			emit(level, age);
			props.removeIf(handle -> !handle.alive());
			return !finished(age);
		}

		abstract void emit(ClientLevel level, int age);

		abstract boolean finished(int age);

		@Nullable ModelHandle show(String id, Vec3 pos, float scale, float yaw, float pitch, int ticks, FxLayer.Priority priority, AnimStep... anims) {
			ModelHandle handle = ModelFx.show(new ModelRequest(id, pos, scale, yaw, pitch, 0.0F, Math.max(1, ticks), List.of(anims), -1, Vec3.ZERO,
					false, FxModels.info(id).glow(), casterId, priority));
			if (handle != null) {
				props.add(handle);
			}
			return handle;
		}

		/** A prop riding an entity at {@code offset} (x left, y up, z forward, turned with the body when {@code yaw}). */
		@Nullable ModelHandle ride(String id, int entity, Vec3 at, Vec3 offset, boolean yaw, float scale, float ownYaw, int ticks, FxLayer.Priority priority,
				AnimStep... anims) {
			ModelHandle handle = ModelFx.show(new ModelRequest(id, at, scale, ownYaw, 0.0F, 0.0F, Math.max(1, ticks), List.of(anims), entity, offset, yaw,
					FxModels.info(id).glow(), casterId, priority));
			if (handle != null) {
				props.add(handle);
			}
			return handle;
		}

		/** A prop moved along {@code path} every tick (and turned by {@code turn}). */
		@Nullable ModelHandle drive(String id, Path path, @Nullable Turn turn, float scale, float pitch, int ticks, FxLayer.Priority priority,
				AnimStep... anims) {
			Vec3 at = path.at(age());
			if (at == null) {
				return null;
			}
			ModelHandle handle = show(id, at, scale, turn == null ? 0.0F : turn.yaw(age()), pitch, ticks, priority, anims);
			if (handle != null) {
				driven.add(new Driven(handle, path, turn, pitch));
			}
			return handle;
		}

		/** Stops driving a prop (it stays where it was last placed). */
		void release(@Nullable ModelHandle handle) {
			driven.removeIf(d -> d.handle == handle);
		}

		/** A shock ring: grows out once, dims, then is gone. */
		void ring(String id, Vec3 at, float scale, int grow, int delay) {
			at(age() + delay, () -> {
				ModelHandle ring = show(id, at, Mth.clamp(scale, 0.5F, 40.0F), 0.0F, 0.0F, grow + 6, FxLayer.Priority.DETAIL,
						AnimStep.of(Anim.GROW, 0, grow, 0.05F, 0.0F));
				if (ring != null) {
					int now = age();
					at(now + grow - 3, () -> ring.lightTo(6, 4));
					at(now + grow + 1, () -> ring.end(3));
				}
			});
		}

		/** Asks for this cast's film (own caster only; the choreography never waits for it). */
		void film(Ev ev, int climaxIn, int duration, Vec3 aim) {
			if (!ev.own()) {
				return;
			}
			Vec3 origin = ev.casterPos();
			film = FilmDirector.play(ev.spec().id(), new FilmDirector.Ctx(casterId, ev.spec().id(), origin, aim, ev.yaw(), ev.p().seed(), climaxIn,
					Math.max(1, duration)));
		}

		void cue(String beat, Vec3 at) {
			if (film) {
				FilmDirector.cue(casterId, beat, at);
			}
		}

		/** Registers a film track (a refused prop still gives the film its point; the track lives while the run does). */
		void track(String key, @Nullable ModelHandle handle, Path path, float yaw, float pitch) {
			if (!film) {
				return;
			}
			if (!tracks.contains(key)) {
				tracks.add(key);
			}
			FilmDirector.track(casterId, key, new PropTrack(this, path, handle, yaw, pitch));
		}

		/** Ends every prop and removes this run's tracks from the film. */
		void close(int fade) {
			for (ModelHandle handle : props) {
				handle.end(fade);
			}
			props.clear();
			driven.clear();
			for (String key : tracks) {
				FilmDirector.track(casterId, key, null);
			}
			tracks.clear();
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

	private static <T extends Run> @Nullable T find(int casterId, Class<T> kind) {
		for (int i = RUNS.size() - 1; i >= 0; i--) {
			Run run = RUNS.get(i);
			if (run.casterId == casterId && kind.isInstance(run)) {
				return kind.cast(run);
			}
		}
		return null;
	}

	/** A new run of {@code kind} for its caster (an older one of the same kind ends first). */
	private static <T extends Run> T begin(T run) {
		for (Iterator<Run> it = RUNS.iterator(); it.hasNext(); ) {
			Run old = it.next();
			if (old.casterId == run.casterId && old.getClass() == run.getClass()) {
				old.close(6);
				it.remove();
			}
		}
		RUNS.add(run);
		return run;
	}

	// ================================================================== Body · Wujud Kosmik Langit-Bumi

	private static final class Giant extends Run {
		private static final int RISE = 40;
		private static final double BEHIND = 10.0D;
		private static final float KNEEL_TILT = 75.0F;

		final float s;
		final float yaw;
		final Vec3 fwd;
		final Vec3 right;
		final Vec3 offset;
		final int climax;
		final double legLen;
		final double hipX;
		final double shoulderY;
		final double shoulderX;
		final double armLen;
		final double drop;
		Vec3 ground;
		boolean planted;
		int kneelAt = -1;
		int impactAt = -1;
		int lifted = -1;
		int warned;
		@Nullable ModelHandle torso;
		@Nullable ModelHandle legL;
		@Nullable ModelHandle legR;
		@Nullable ModelHandle armL;
		@Nullable ModelHandle armR;

		static void on(Ev ev) {
			Giant g = find(ev.casterId(), Giant.class);
			switch (ev.phase()) {
				case CAST -> begin(new Giant(ev));
				case PHANTOM -> {
					if (g != null && ev.preImpact() && ev.hasTo()) {
						g.warn(ev.to());
					}
				}
				case EXTRA0 -> {
					if (g != null) {
						g.stomp(ev.pos(), ev.radius());
					}
				}
				case EXTRA1 -> {
					if (g != null) {
						g.impact(ev.pos(), ev.radius());
					}
				}
				default -> {
				}
			}
		}

		Giant(Ev ev) {
			super(ev.casterId());
			s = EffectQuality.minimal() ? 9.0F : 12.0F;
			yaw = ev.yaw();
			fwd = fwd(yaw);
			right = right(yaw);
			offset = fwd.scale(-BEHIND);
			ground = ev.casterPos().add(offset);
			climax = climaxOf(ev.spec(), 400);
			legLen = 1.5D * s;
			hipX = 2.5D / 16.0D * s;
			shoulderY = 12.0D / 16.0D * s;
			// the arms hang just outside the chest, under the pauldrons
			shoulderX = 8.5D / 16.0D * s;
			armLen = 23.5D / 16.0D * s;
			drop = legLen * (1.0D - Math.cos(Math.toRadians(KNEEL_TILT)));
			film(ev, climax, climax + 60, ev.casterPos().add(fwd.scale(16.0D)));
			at(0, this::build);
			at(30, () -> {
				for (ModelHandle h : limbs()) {
					h.lightTo(15, 16);
				}
			});
			at(climax - 20, this::kneel);
			// the server's climax event normally lands first; a lost one still ends the giant
			at(climax + 8, () -> {
				if (impactAt < 0) {
					impact(null, 28.0F);
				}
			});
		}

		private List<ModelHandle> limbs() {
			List<ModelHandle> out = new ArrayList<>(5);
			for (ModelHandle h : new ModelHandle[] {torso, legL, legR, armL, armR}) {
				if (h != null) {
					out.add(h);
				}
			}
			return out;
		}

		/** The ground point under the giant: behind the caster until it kneels, then planted. */
		private Vec3 groundAt() {
			if (!planted) {
				Entity caster = entity(casterId);
				if (caster != null) {
					ground = caster.position().add(offset);
				}
			}
			return ground;
		}

		private double dropAt(float t) {
			if (kneelAt < 0) {
				return 0.0D;
			}
			return drop * ease(Mth.clamp((t - kneelAt) / 12.0F, 0.0F, 1.0F));
		}

		private Vec3 pelvis(float t) {
			return groundAt().add(0.0D, legLen - dropAt(t), 0.0D);
		}

		private Vec3 hip(float t, int side) {
			return pelvis(t).add(right.scale(side * hipX));
		}

		private Vec3 shoulder(float t, int side) {
			return pelvis(t).add(0.0D, shoulderY, 0.0D).add(right.scale(side * shoulderX));
		}

		private void build() {
			int life = climax + 60;
			AnimStep rise = AnimStep.of(Anim.RISE, 0, RISE, 30.0F, 0.0F);
			torso = drive("cgf_giant", this::pelvis, null, s, 0.0F, life, FxLayer.Priority.CORE, rise);
			if (torso != null) {
				torso.lightTo(4, 1);
				track("giant", torso, t -> pelvis(t).add(0.0D, 0.55D * s, 0.0D), yaw, 0.0F);
			}
			if (EffectQuality.minimal()) {
				return;
			}
			legL = drive("cgf_leg_l", t -> hip(t, -1), null, s, 0.0F, life, FxLayer.Priority.DETAIL, rise);
			legR = drive("cgf_leg_r", t -> hip(t, 1), null, s, 0.0F, life, FxLayer.Priority.DETAIL, rise);
			at(1, () -> {
				armL = drive("cgf_arm_l", t -> shoulder(t, -1), null, s, 0.0F, life - 1, FxLayer.Priority.DETAIL, AnimStep.of(Anim.RISE, -1, RISE, 30.0F, 0.0F));
				armR = drive("cgf_arm_r", t -> shoulder(t, 1), null, s, 0.0F, life - 1, FxLayer.Priority.DETAIL, AnimStep.of(Anim.RISE, -1, RISE, 30.0F, 0.0F));
				for (ModelHandle h : new ModelHandle[] {armL, armR}) {
					if (h != null) {
						h.lightTo(4, 1);
					}
				}
			});
			for (ModelHandle h : new ModelHandle[] {legL, legR}) {
				if (h != null) {
					h.lightTo(4, 1);
				}
			}
			track("foot_l", legL, t -> hip(t, -1).add(0.0D, -legLen, 0.0D), yaw, 0.0F);
			track("foot_r", legR, t -> hip(t, 1).add(0.0D, -legLen, 0.0D), yaw, 0.0F);
		}

		/** A phantom warning (10 ticks before a stomp): a leg lifts, a footprint glyph grows at the target. */
		void warn(Vec3 target) {
			if (kneelAt >= 0 || age() < RISE) {
				return;
			}
			ModelHandle leg = warned % 2 == 0 ? legL : legR;
			if (leg != null && lifted < 0) {
				leg.play(AnimStep.of(Anim.TILT, 0, 8, -30.0F, 0.0F));
				lifted = warned % 2;
			}
			warned++;
			ModelHandle print = show("cgf_footprint", target.add(0.0D, 0.06D, 0.0D), 4.0F, ModelFx.yawTo(groundAt(), target), 0.0F, 34, FxLayer.Priority.DETAIL,
					AnimStep.of(Anim.GROW, 0, 8, 0.2F, 0.0F));
			if (print != null) {
				int now = age();
				at(now + 10, () -> print.lightTo(15, 2));
				at(now + 22, () -> print.end(10));
			}
			cue("stomp_warn", target);
		}

		/** The stomp (EXTRA0): the lifted leg stamps down, a gold ring runs out. */
		void stomp(Vec3 at, float radius) {
			if (kneelAt >= 0) {
				return;
			}
			if (lifted >= 0) {
				ModelHandle leg = lifted == 0 ? legL : legR;
				if (leg != null) {
					leg.play(AnimStep.of(Anim.TILT, 0, 2, 30.0F, 0.0F));
					leg.play(AnimStep.of(Anim.STAMP, 2, 2, 0.0F, 0.0F));
				}
				lifted = -1;
			}
			ring("cgf_ring", at.add(0.0D, 0.2D, 0.0D), Mth.clamp(radius, 8.0F, 14.0F) / 1.25F, 10, 1);
			burst(at, 0xC8B080, 0xFFD54A, 18, 0.6D);
			cue("step", at);
		}

		/** K-20: the colossus kneels where it stands and raises its right fist. */
		void kneel() {
			if (kneelAt >= 0) {
				return;
			}
			groundAt();
			planted = true;
			kneelAt = age();
			float extra = lifted >= 0 ? 30.0F : 0.0F;
			if (legL != null) {
				legL.play(AnimStep.of(Anim.TILT, 0, 12, KNEEL_TILT + (lifted == 0 ? extra : 0.0F), 0.0F));
			}
			if (legR != null) {
				legR.play(AnimStep.of(Anim.TILT, 0, 12, KNEEL_TILT + (lifted == 1 ? extra : 0.0F), 0.0F));
			}
			lifted = -1;
			if (armR != null) {
				armR.play(AnimStep.of(Anim.TILT, 2, 14, -170.0F, 0.0F));
			}
			cue("release", pelvis(age()));
		}

		/** K (EXTRA1): the fist slams, rings run out, then the giant dissolves upward from the feet. */
		void impact(@Nullable Vec3 at, float radius) {
			if (impactAt >= 0) {
				return;
			}
			if (kneelAt < 0) {
				kneel();
			}
			impactAt = age();
			Vec3 fist = shoulder(impactAt + 12, 1).add(fwd.scale(armLen * Math.sin(Math.toRadians(40.0D)))).add(0.0D, -armLen * Math.cos(Math.toRadians(40.0D)), 0.0D);
			Vec3 floor = new Vec3(fist.x, ground.y + 0.3D, fist.z);
			if (armR != null) {
				armR.play(AnimStep.of(Anim.TILT, 0, 3, 130.0F, 0.0F));
				armR.play(AnimStep.of(Anim.STAMP, 3, 2, 0.0F, 0.0F));
			} else if (torso != null) {
				torso.play(AnimStep.of(Anim.STAMP, 0, 2, 0.0F, 0.0F));
			}
			float big = Mth.clamp(radius, 16.0F, 32.0F);
			ring("cgf_ring", floor, big * 0.5F / 1.25F, 10, 3);
			ring("cgf_ring", (at != null ? at : floor).add(0.0D, 0.3D, 0.0D), big / 1.25F, 14, 5);
			at(impactAt + 3, () -> burst(floor, 0xFFD54A, 0xFFFFFF, 36, 1.0D));
			cue("impact", floor);
			at(impactAt + 4, () -> dissolve(legL, legR));
			at(impactAt + 10, () -> dissolve(armL, armR));
			at(impactAt + 16, () -> dissolve(torso, null));
		}

		private void dissolve(@Nullable ModelHandle a, @Nullable ModelHandle b) {
			for (ModelHandle h : new ModelHandle[] {a, b}) {
				if (h != null) {
					h.play(AnimStep.of(Anim.DISSOLVE, 0, 10, 0.0F, 0.0F));
				}
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			Vec3 g = groundAt();
			if (age < RISE) {
				// the ground breaks open as the colossus rises
				for (int i = 0, n = EffectQuality.scale(4); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = 2.0D + random.nextDouble() * s * 0.4D;
					spend(Qi.smoke(i % 2 == 0 ? 0xA89060 : 0xC8B080, 1.4F, 30), g.x + Math.cos(a) * r, g.y + 0.3D, g.z + Math.sin(a) * r,
							Math.cos(a) * 0.05D, 0.08D + random.nextDouble() * 0.06D, Math.sin(a) * 0.05D);
				}
				return;
			}
			if (impactAt >= 0) {
				if (age < impactAt + 34) {
					// the body leaves as gold dust rising from the feet up
					double from = Math.max(0.0D, (age - impactAt - 4) * 1.1D);
					for (int i = 0, n = EffectQuality.scale(6); i < n; i++) {
						double y = from + random.nextDouble() * Math.max(2.0D, legLen * 2.0D - from);
						Vec3 p = g.add(right.scale((random.nextDouble() - 0.5D) * s * 0.9D)).add(0.0D, Math.min(y, legLen * 2.0D), 0.0D);
						spend(Qi.mote(i % 3 == 0 ? 0xFFFFFF : 0xFFD54A, 0.8F, 40), p.x, p.y, p.z, (random.nextDouble() - 0.5D) * 0.05D, 0.15D, (random.nextDouble() - 0.5D) * 0.05D);
					}
				}
				return;
			}
			if (age % 2 == 0) {
				// star specks drifting round the sash, gold motes off the shoulders
				Vec3 chest = pelvis(age).add(0.0D, 0.55D * s, 0.0D);
				for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
					Vec3 p = chest.add(right.scale((random.nextDouble() - 0.5D) * s * 0.8D)).add(fwd.scale(-0.3D * s)).add(0.0D, (random.nextDouble() - 0.5D) * s * 0.6D, 0.0D);
					spend(Qi.glint(i % 2 == 0 ? 0xB8C0FF : 0xFFF2B0, 0.6F, 24), p.x, p.y, p.z, 0.0D, 0.03D, 0.0D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return impactAt >= 0 ? age > impactAt + 40 : age > climax + 80;
		}
	}

	// ================================================================== Body · Injakan Dewa Kuno

	private static final class Trample extends Run {
		private static final int FIRST = 20;
		private static final int EVERY = 20;

		final int climax;
		final float s;
		final ModelHandle[] brow = new ModelHandle[9];
		final ModelHandle[] glyphs = new ModelHandle[9];
		int steps;
		int lit;
		@Nullable ModelHandle skyFoot;
		@Nullable Vec3 lastFoot;
		@Nullable Vec3 skyAt;
		int skyMade = -1;
		int impactAt = -1;

		static void on(Ev ev) {
			Trample t = find(ev.casterId(), Trample.class);
			switch (ev.phase()) {
				case CAST -> begin(new Trample(ev));
				case EXTRA0 -> {
					if (t != null) {
						t.step(ev.pos(), ev.radius());
					}
				}
				case EXTRA1 -> {
					if (t != null) {
						t.impact(ev.pos(), ev.radius());
					}
				}
				default -> {
				}
			}
		}

		Trample(Ev ev) {
			super(ev.casterId());
			climax = climaxOf(ev.spec(), 180);
			s = EffectQuality.minimal() ? 8.0F : 10.0F;
			film(ev, climax, climax + 50, ev.casterPos().add(fwd(ev.yaw()).scale(16.0D)));
			if (!EffectQuality.minimal()) {
				at(0, () -> brow(0, 5));
				at(1, () -> brow(5, 9));
			}
			track("brow", null, t -> browPoint(), ev.yaw(), 0.0F);
			// a footprint glyph before each of the eight steps (10 ticks ahead, where the step will land)
			for (int k = 0; k < 8; k++) {
				int index = k;
				at(FIRST + EVERY * k - 10, () -> glyph(index));
			}
			at(climax - 20, this::skyFoot);
		}

		private Vec3 browPoint() {
			Entity caster = entity(casterId);
			return caster == null ? Vec3.ZERO : caster.getEyePosition().add(0.0D, 0.2D, 0.0D);
		}

		private void brow(int from, int to) {
			Entity caster = entity(casterId);
			if (caster == null) {
				return;
			}
			double eye = caster.getEyeHeight();
			for (int k = from; k < to; k++) {
				double a = Math.toRadians((k - 4) * 11.0D);
				Vec3 offset = new Vec3(Math.sin(a) * 0.3D, eye + 0.2D + 0.03D * Math.cos(a * 3.0D), Math.cos(a) * 0.3D);
				ModelHandle star = ride("agt_brow_star", caster.getId(), caster.position(), offset, true, 0.15F, 0.0F, climax + 50, FxLayer.Priority.DETAIL,
						AnimStep.of(Anim.GROW, 0, 8, 0.2F, 0.0F));
				if (star != null) {
					star.lightTo(5, 1);
					brow[k] = star;
				}
			}
		}

		/** Where step {@code k} will land: 8 blocks further along the look for each step. */
		private void glyph(int k) {
			Entity caster = entity(casterId);
			if (caster == null || EffectQuality.minimal()) {
				return;
			}
			Vec3 look = flat(caster.getLookAngle());
			Vec3 at = caster.position().add(look.scale(8.0D * k)).add(0.0D, 0.06D, 0.0D);
			ModelHandle glyph = show("agt_glyph", at, 4.0F, caster.getYRot(), 0.0F, 40, FxLayer.Priority.DETAIL, AnimStep.of(Anim.GROW, 0, 8, 0.2F, 0.0F));
			if (glyph != null) {
				glyph.lightTo(8, 1);
				glyphs[k] = glyph;
			}
		}

		void step(Vec3 at, float radius) {
			int k = Math.min(steps, 8);
			steps++;
			lastFoot = at;
			ModelHandle foot = show("agt_foot", at, s, yawOf(casterId, 0.0F), 0.0F, 26, FxLayer.Priority.CORE, AnimStep.of(Anim.FALL, 0, 4, 12.0F, 0.0F),
					AnimStep.of(Anim.STAMP, 4, 2, 0.0F, 0.0F), AnimStep.of(Anim.DISSOLVE, 14, 10, 0.0F, 0.0F));
			track("foot", foot, t -> lastFoot, yawOf(casterId, 0.0F), 0.0F);
			ModelHandle glyph = k < glyphs.length ? glyphs[k] : null;
			if (glyph != null) {
				glyph.lightTo(15, 2);
				at(age() + 20, () -> glyph.end(10));
			}
			if (lit < brow.length && brow[lit] != null) {
				brow[lit].lightTo(15, 4);
				brow[lit].play(AnimStep.of(Anim.PULSE, 4, 8, 0.1F, 0.0F));
			}
			lit++;
			ring("agt_ring", at.add(0.0D, 0.2D, 0.0D), Mth.clamp(radius, 8.0F, 12.0F) / 1.25F, 8, 4);
			at(age() + 4, () -> burst(at, 0x8A7A5A, 0xC8C8D8, 16, 0.5D));
			cue("step", at);
		}

		/** K-20: a giant foot forms in the sky tear 40 blocks up and comes down onto the caster's ground at K. */
		private void skyFoot() {
			Entity caster = entity(casterId);
			Vec3 base = caster != null ? caster.position().add(flat(caster.getLookAngle()).scale(6.0D)) : lastFoot;
			if (base == null) {
				return;
			}
			skyAt = base;
			skyMade = age();
			float big = s * 3.0F;
			skyFoot = show("agt_foot", base, big, yawOf(casterId, 0.0F), 0.0F, 70, FxLayer.Priority.CORE, AnimStep.of(Anim.GROW, 0, 16, 0.3F, 0.0F),
					AnimStep.of(Anim.FALL, 11, 8, 40.0F, 0.0F), AnimStep.of(Anim.STAMP, 19, 2, 0.0F, 0.0F));
			int made = skyMade;
			track("sky_foot", skyFoot, t -> {
				float into = t - made - 11.0F;
				double fall = into < 0.0F ? 40.0D : 40.0D * (1.0D - Math.pow(Mth.clamp(into / 8.0F, 0.0F, 1.0F), 2.0D));
				return base.add(0.0D, fall + big * 0.4D, 0.0D);
			}, yawOf(casterId, 0.0F), 0.0F);
			cue("release", base.add(0.0D, 40.0D, 0.0D));
		}

		void impact(Vec3 at, float radius) {
			if (impactAt >= 0) {
				return;
			}
			impactAt = age();
			Vec3 centre = skyAt != null ? skyAt : at;
			for (ModelHandle star : brow) {
				if (star != null) {
					star.lightTo(15, 1);
					star.play(AnimStep.of(Anim.PULSE, 0, 8, 0.1F, 0.0F));
				}
			}
			ring("agt_ring", centre.add(0.0D, 0.3D, 0.0D), Mth.clamp(radius, 24.0F, 40.0F) / 1.25F, 14, 0);
			ring("agt_ring", centre.add(0.0D, 2.0D, 0.0D), Mth.clamp(radius, 24.0F, 40.0F) * 0.6F / 1.25F, 12, 4);
			burst(centre, 0xA89060, 0xFFE8A0, 40, 1.2D);
			cue("impact", centre);
			// the god's foot stays pressed in the crater, then hands over to the Dampak monument
			ModelHandle foot = skyFoot;
			at(impactAt + 30, () -> {
				if (foot != null) {
					foot.end(20);
				}
			});
			at(impactAt + 40, () -> {
				for (ModelHandle star : brow) {
					if (star != null) {
						star.end(10);
					}
				}
			});
		}

		@Override
		void emit(ClientLevel level, int age) {
			if (skyFoot == null || skyAt == null || impactAt >= 0 || age > skyMade + 20) {
				return;
			}
			// dust and gold sparks streaming off the descending foot
			RandomSource random = level.getRandom();
			float into = age - skyMade - 11.0F;
			double h = into < 0.0F ? 40.0D : 40.0D * (1.0D - Math.pow(Mth.clamp(into / 8.0F, 0.0F, 1.0F), 2.0D));
			for (int i = 0, n = EffectQuality.scale(4); i < n; i++) {
				double a = random.nextDouble() * Math.PI * 2.0D;
				double r = s * (0.6D + random.nextDouble() * 0.6D);
				spend(Qi.spark(i % 2 == 0 ? 0xFFE8A0 : 0xC8C8D8, 0.5F, 14), skyAt.x + Math.cos(a) * r, skyAt.y + h + random.nextDouble() * s, skyAt.z + Math.sin(a) * r,
						0.0D, 0.3D, 0.0D);
			}
		}

		@Override
		boolean finished(int age) {
			return impactAt >= 0 ? age > impactAt + 54 : age > climax + 90;
		}
	}

	// ================================================================== Life · Embun Manis Guanyin

	private static final class Dew extends Run {
		private static final double SEAT = 8.0D;
		private static final double MOUTH = 17.8D / 16.0D * 4.0D;

		final Vec3 centre;
		final float yaw;
		final Vec3 fwd;
		final int climax;
		final float radius;
		final Vec3 seat;
		final Vec3 vase;
		@Nullable ModelHandle seatProp;
		@Nullable ModelHandle vaseProp;
		@Nullable ModelHandle willow;
		int pulses;
		int impactAt = -1;
		boolean tipped;

		static void on(Ev ev) {
			Dew d = find(ev.casterId(), Dew.class);
			switch (ev.phase()) {
				case CAST -> begin(new Dew(ev));
				case PULSE -> {
					if (d != null) {
						d.pulse();
					}
				}
				case EXTRA0 -> {
					if (d != null) {
						d.pour();
					}
				}
				default -> {
				}
			}
		}

		Dew(Ev ev) {
			super(ev.casterId());
			centre = ev.pos();
			yaw = ev.yaw();
			fwd = fwd(yaw);
			climax = climaxOf(ev.spec(), 200);
			radius = Mth.clamp(ev.radius(), 16.0F, 32.0F);
			seat = centre.add(0.0D, SEAT, 0.0D);
			vase = seat.add(0.0D, 0.4D, 0.0D);
			film(ev, climax, climax + 40, centre.add(fwd.scale(8.0D)));
			int life = climax + 40;
			at(0, () -> {
				seatProp = show("gsd_seat", seat, 3.0F, yaw, 0.0F, life, FxLayer.Priority.DETAIL, AnimStep.of(Anim.RISE, 0, 20, (float) SEAT, 0.0F),
						AnimStep.of(Anim.SPIN, 0, life, 0.5F, 0.0F));
			});
			at(10, () -> {
				vaseProp = show("gsd_vase", vase, 4.0F, yaw, 0.0F, life - 10, FxLayer.Priority.CORE, AnimStep.of(Anim.GROW, 0, 16, 0.1F, 0.0F));
				track("vase", vaseProp, t -> vase.add(0.0D, 2.0D, 0.0D), yaw, 0.0F);
			});
			at(22, () -> {
				if (EffectQuality.minimal()) {
					return;
				}
				// the sprig first settles to one side, so every pulse's sweep starts where the last one ended
				willow = show("gsd_willow", vase.add(0.0D, MOUTH - 0.15D, 0.0D), 2.0F, yaw, 0.0F, life - 22, FxLayer.Priority.DETAIL,
						AnimStep.of(Anim.GROW, 0, 10, 0.1F, 0.0F), new AnimStep(Anim.SWING, 0, 6, -30.0F, 0.0F, null));
			});
			at(climax - 10, this::tip);
		}

		void pulse() {
			pulses++;
			if (willow != null && !tipped) {
				willow.play(new AnimStep(Anim.SWING, 0, 8, pulses % 2 == 1 ? 30.0F : -30.0F, 0.0F, null));
			}
			cue("pulse", centre);
			if (EffectQuality.minimal()) {
				return;
			}
			int n = EffectQuality.maximal() ? 12 : 6;
			double r = radius * 0.35D;
			for (int i = 0; i < n; i++) {
				double a = Math.toRadians(360.0D * i / n + pulses * 15.0D);
				Vec3 land = centre.add(Math.cos(a) * r, 0.05D, Math.sin(a) * r);
				int delay = i / 6;
				at(age() + delay, () -> {
					ModelHandle drop = show("gsd_drop", land, 0.4F, 0.0F, 0.0F, 13, FxLayer.Priority.DETAIL, AnimStep.of(Anim.FALL, 0, 10, 7.0F, 0.0F));
					if (drop != null) {
						at(age() + 10, () -> {
							drop.end(2);
							spend(Qi.groundRing(0xE8F8FF, 0.6F, 2.4F, 10), land.x, land.y + 0.05D, land.z, 0.0D, 0.0D, 0.0D);
						});
					}
				});
			}
		}

		/** K-10: the vase tips forward over the field, the sprig with it. */
		private void tip() {
			if (tipped) {
				return;
			}
			tipped = true;
			if (vaseProp != null) {
				vaseProp.play(AnimStep.of(Anim.TILT, 0, 10, 45.0F, 0.0F));
			}
			if (willow != null) {
				double h = MOUTH - 0.15D;
				willow.moveTo(vase.add(fwd.scale(h * Math.sin(Math.PI / 4.0D))).add(0.0D, h * Math.cos(Math.PI / 4.0D), 0.0D), 10);
				willow.play(AnimStep.of(Anim.TILT, 0, 10, 45.0F, 0.0F));
			}
			cue("release", vase);
		}

		/** The mouth of the tipped vase. */
		private Vec3 mouth() {
			return vase.add(fwd.scale(MOUTH * Math.sin(Math.PI / 4.0D))).add(0.0D, MOUTH * Math.cos(Math.PI / 4.0D), 0.0D);
		}

		/** K (EXTRA0): an arc of dew pours from the mouth to the field, a bloom ring opens where it lands. */
		void pour() {
			if (impactAt >= 0) {
				return;
			}
			if (!tipped) {
				tip();
			}
			impactAt = age();
			Vec3 m = mouth();
			Vec3 target = centre.add(fwd.scale(7.0D)).add(0.0D, 0.05D, 0.0D);
			Vec3 under = new Vec3(m.x, target.y, m.z);
			float height = (float) Math.max(1.0D, m.y - target.y);
			if (!EffectQuality.minimal()) {
				for (int i = 0; i < 8; i++) {
					at(impactAt + i * 2, () -> show("gsd_drop", under, 0.45F, 0.0F, 0.0F, 13, FxLayer.Priority.DETAIL,
							new AnimStep(Anim.SLIDE, 0, 12, 0.0F, 0.0F, target), AnimStep.of(Anim.FALL, 0, 12, height, 0.0F)));
				}
			}
			ring("gsd_ring", target, radius * 0.5F / 1.25F, 14, 12);
			at(impactAt + 12, () -> burst(target, 0xE8F8FF, 0xB8FFD0, 24, 0.35D));
			cue("impact", target);
			// the seat folds and ends; the vase hovers on a moment, then the Dampak monument stays
			at(impactAt + 6, () -> {
				if (seatProp != null) {
					seatProp.play(AnimStep.of(Anim.TILT, 0, 10, 60.0F, 0.0F));
				}
			});
			at(impactAt + 14, () -> {
				if (seatProp != null) {
					seatProp.end(8);
				}
			});
			at(impactAt + 24, () -> {
				if (vaseProp != null) {
					vaseProp.end(16);
				}
				if (willow != null) {
					willow.end(16);
				}
			});
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (impactAt >= 0) {
				if (age < impactAt + 18) {
					// a rainbow light along the pour
					Vec3 m = mouth();
					Vec3 target = centre.add(fwd.scale(7.0D));
					for (int i = 0, n = EffectQuality.scale(3); i < n; i++) {
						double k = random.nextDouble();
						Vec3 p = m.add(target.subtract(m).scale(k)).add(0.0D, Math.sin(k * Math.PI) * 1.2D, 0.0D);
						int hue = Qi.hueShift(0xFF8080, (float) (k * 300.0D));
						spend(Qi.glint(hue, 0.4F, 16), p.x, p.y, p.z, 0.0D, -0.02D, 0.0D);
					}
				}
				return;
			}
			if (age % 3 == 0) {
				// soft dew motes drifting down over the field
				for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = radius * Math.sqrt(random.nextDouble()) * 0.8D;
					spend(Qi.glint(i % 2 == 0 ? 0xE8F8FF : 0x9FF3FF, 0.35F, 40), centre.x + Math.cos(a) * r, centre.y + 4.0D + random.nextDouble() * 6.0D,
							centre.z + Math.sin(a) * r, 0.0D, -0.05D, 0.0D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return impactAt >= 0 ? age > impactAt + 46 : age > climax + 60;
		}
	}

	// ================================================================== Life · Wilayah Hidup dan Mati

	private static final class Domain extends Run {
		private static final float SPIN = 0.3F;

		final Vec3 centre;
		final float yaw;
		final int climax;
		final float radius;
		final float scale;
		final int count;
		final List<ModelHandle> lanterns = new ArrayList<>();
		@Nullable ModelHandle disc;
		int impactAt = -1;

		static void on(Ev ev) {
			Domain d = find(ev.casterId(), Domain.class);
			switch (ev.phase()) {
				case CAST -> begin(new Domain(ev));
				case PULSE -> {
					if (d != null) {
						d.flicker();
					}
				}
				case EXTRA0 -> {
					if (d != null) {
						d.flip();
					}
				}
				default -> {
				}
			}
		}

		Domain(Ev ev) {
			super(ev.casterId());
			centre = ev.pos();
			yaw = ev.yaw();
			climax = climaxOf(ev.spec(), 400);
			radius = Mth.clamp(ev.radius(), 20.0F, 32.0F);
			scale = Mth.clamp(radius * 2.0F / 3.0F, 12.0F, 21.0F);
			count = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 16 : 8;
			film(ev, climax, climax + 40, centre);
			int life = climax + 40;
			at(0, () -> {
				// the diagram stands up out of the ground and unfolds flat (TILT 90 → 0), then turns slowly until K-10
				disc = show("ldd_disc", centre.add(0.0D, 0.15D, 0.0D), scale, yaw, -90.0F, life, FxLayer.Priority.CORE,
						AnimStep.of(Anim.TILT, 0, 30, 90.0F, 0.0F), AnimStep.of(Anim.SPIN, 0, Math.max(1, climax - 10), SPIN, 0.0F));
				track("disc", disc, t -> centre.add(0.0D, 1.0D, 0.0D), yaw, 90.0F);
			});
			for (int i = 0; i < count; i++) {
				int index = i;
				at(20 + i / 4, () -> lantern(index, life));
			}
		}

		private void lantern(int i, int life) {
			float own = yaw + 360.0F * i / count;
			float ring = radius * 0.85F;
			double height = 2.5D + (i % 3) * 0.6D;
			Vec3 base = centre.add(0.0D, height, 0.0D);
			int made = age();
			ModelHandle lantern = show("ldd_lantern", base, 0.8F, own, 0.0F, life - made, FxLayer.Priority.DETAIL, AnimStep.of(Anim.GROW, 0, 10, 0.2F, 0.0F),
					AnimStep.of(Anim.REVOLVE, 0, life, 0.6F, -ring), AnimStep.of(Anim.BOB, 0, life, 0.35F, 50.0F + i * 3.0F));
			if (lantern == null) {
				return;
			}
			lanterns.add(lantern);
			if (i < 8) {
				track("lantern_" + i, lantern, t -> revolve(base, own, 0.6F, -ring, t - made), own, 0.0F);
			}
		}

		void flicker() {
			if (impactAt >= 0) {
				return;
			}
			for (ModelHandle lantern : lanterns) {
				lantern.lightTo(8, 2);
			}
			at(age() + 3, () -> {
				for (ModelHandle lantern : lanterns) {
					lantern.lightTo(15, 4);
				}
			});
		}

		/** K (EXTRA0): the diagram flips over (green to grey), the lanterns rise and go, a lotus grows at the centre. */
		void flip() {
			if (impactAt >= 0) {
				return;
			}
			impactAt = age();
			if (disc != null) {
				disc.play(AnimStep.of(Anim.TILT, 0, 6, 180.0F, 0.0F));
			}
			for (int i = 0; i < lanterns.size(); i++) {
				ModelHandle lantern = lanterns.get(i);
				int delay = i % 4;
				at(impactAt + delay, () -> {
					lantern.moveTo(centre.add(0.0D, 10.0D, 0.0D), 22);
					lantern.lightTo(15, 1);
				});
				at(impactAt + 14 + delay, () -> lantern.end(10));
			}
			ring("ldd_ring", centre.add(0.0D, 0.4D, 0.0D), radius / 1.25F, 14, 2);
			cue("impact", centre);
			at(impactAt + 4, () -> show("ldd_lotus", centre.add(0.0D, 0.2D, 0.0D), 3.0F, yaw, 0.0F, 44, FxLayer.Priority.CORE,
					AnimStep.of(Anim.GROW, 0, 12, 0.1F, 0.0F)));
			at(impactAt + 24, () -> {
				if (disc != null) {
					disc.end(16);
				}
			});
		}

		/** The diagram's life half (+x of the model) points along this direction at run tick {@code t}. */
		private Vec3 lifeSide(float t) {
			return right(yaw - SPIN * Math.min(t, climax - 10));
		}

		@Override
		void emit(ClientLevel level, int age) {
			if (impactAt >= 0 || age < 30) {
				return;
			}
			RandomSource random = level.getRandom();
			Vec3 side = lifeSide(age);
			for (int i = 0, n = EffectQuality.scale(3); i < n; i++) {
				double a = random.nextDouble() * Math.PI * 2.0D;
				double r = radius * Math.sqrt(random.nextDouble()) * 0.9D;
				double x = Math.cos(a) * r, z = Math.sin(a) * r;
				boolean life = x * side.x + z * side.z > 0.0D;
				if (life) {
					spend(Qi.mote(0x9AE05A, 0.5F, 30), centre.x + x, centre.y + 0.4D, centre.z + z, 0.0D, 0.06D, 0.0D);
				} else {
					spend(Qi.smoke(0x808080, 0.6F, 30), centre.x + x, centre.y + 0.4D, centre.z + z, 0.0D, 0.04D, 0.0D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return impactAt >= 0 ? age > impactAt + 50 : age > climax + 60;
		}
	}

	// ================================================================== Life · Pohon Kuno Bodhisattva

	private static final class Tree extends Run {
		private static final float[] TIER = {0.92F, 1.25F, 1.58F};
		private static final float[] TIER_R = {14.0F, 10.0F, 6.0F};

		final Vec3 centre;
		final Vec3 base;
		final float yaw;
		final float s;
		final int climax;
		final float radius;
		final ModelHandle[] canopy = new ModelHandle[3];
		final List<ModelHandle> fruits = new ArrayList<>();
		final List<ModelHandle> roots = new ArrayList<>();
		@Nullable ModelHandle trunk;
		int novas;
		int impactAt = -1;

		static void on(Ev ev) {
			Tree t = find(ev.casterId(), Tree.class);
			switch (ev.phase()) {
				case CAST -> begin(new Tree(ev));
				case EXTRA0 -> {
					if (t != null) {
						t.nova(ev.radius());
					}
				}
				case EXTRA1 -> {
					if (t != null) {
						t.impact(ev.radius());
					}
				}
				default -> {
				}
			}
		}

		Tree(Ev ev) {
			super(ev.casterId());
			centre = ev.pos();
			yaw = ev.yaw();
			base = centre.subtract(fwd(yaw).scale(5.0D));
			s = EffectQuality.minimal() ? 9.0F : 12.0F;
			climax = climaxOf(ev.spec(), 600);
			radius = Mth.clamp(ev.radius(), 24.0F, 40.0F);
			film(ev, climax, climax + 50, base.add(0.0D, s, 0.0D));
			int life = climax + 60;
			at(0, () -> {
				trunk = show("bt_trunk", base, s, yaw, 0.0F, life, FxLayer.Priority.CORE, AnimStep.of(Anim.RISE, 0, 30, 12.0F, 0.0F));
				track("tree", trunk, t -> base.add(0.0D, 0.75D * s, 0.0D), yaw, 0.0F);
			});
			for (int k = 0; k < 3; k++) {
				int tier = k;
				at(20 + 8 * k, () -> {
					if (EffectQuality.minimal() && tier != 1) {
						return;
					}
					ModelHandle c = show("bt_canopy_" + (tier + 1), base.add(0.0D, TIER[tier] * s, 0.0D), s, yaw + tier * 22.5F, 0.0F, life - 20 - 8 * tier,
							FxLayer.Priority.DETAIL, AnimStep.of(Anim.GROW, 0, 16, 0.1F, 0.0F));
					canopy[tier] = c;
					if (c != null) {
						at(age() + 40, () -> c.lightCycle(9, 80, tier * 20));
					}
					if (tier == 1) {
						track("canopy", c, t -> base.add(0.0D, TIER[1] * s, 0.0D), yaw, 0.0F);
					}
				});
			}
			int fruitCount = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 9 : 6;
			for (int j = 0; j < fruitCount; j++) {
				int index = j;
				at(44 + j / 3, () -> fruit(index, life));
			}
			at(climax - 20, () -> {
				for (ModelHandle fruit : fruits) {
					fruit.lightTo(15, 6);
				}
				cue("release", base.add(0.0D, TIER[1] * s, 0.0D));
			});
		}

		private void fruit(int j, int life) {
			int tier = j % 3;
			double a = Math.toRadians(j * 40.0D + 15.0D + yaw);
			double r = TIER_R[tier] / 16.0D * s * 0.7D;
			Vec3 at = base.add(Math.cos(a) * r, TIER[tier] * s - 0.32D * s / 1.5D, Math.sin(a) * r);
			ModelHandle fruit = show("bt_fruit", at, 0.6F * s / 12.0F * 1.6F, yaw, 0.0F, life - age(), FxLayer.Priority.DETAIL,
					AnimStep.of(Anim.GROW, 0, 8, 0.2F, 0.0F), AnimStep.of(Anim.BOB, 0, life, 0.08F, 60.0F + j * 4.0F));
			if (fruit != null) {
				fruit.lightTo(5, 1);
				fruits.add(fruit);
			}
		}

		void nova(float r) {
			if (impactAt >= 0) {
				return;
			}
			int n = novas++;
			if (!EffectQuality.minimal() && n < 5) {
				ModelHandle root = show("bt_root", base.add(0.0D, 0.02D, 0.0D), s * 0.5F, yaw + n * 72.0F, 0.0F, climax + 50 - age(), FxLayer.Priority.DETAIL,
						AnimStep.of(Anim.GROW, 0, 20, 0.1F, 0.0F));
				if (root != null) {
					roots.add(root);
				}
			}
			if (n < fruits.size()) {
				fruits.get(n).lightTo(15, 6);
			}
			if (n + 5 < fruits.size()) {
				fruits.get(n + 5).lightTo(15, 6);
			}
			ring("bt_ring", centre.add(0.0D, 0.3D, 0.0D), Mth.clamp(r, 24.0F, 40.0F) / 1.25F, 12, 0);
			cue("nova", centre);
		}

		/** K (EXTRA1): the fruits burst into light, the canopy flashes, the tree leaves top-down. */
		void impact(float r) {
			if (impactAt >= 0) {
				return;
			}
			impactAt = age();
			for (int i = 0; i < fruits.size(); i++) {
				ModelHandle fruit = fruits.get(i);
				at(impactAt + i % 3, () -> fruit.play(AnimStep.of(Anim.SHATTER, 0, 3, 0.0F, 0.0F)));
			}
			for (ModelHandle c : canopy) {
				if (c != null) {
					c.lightTo(15, 1);
				}
			}
			ring("bt_ring", centre.add(0.0D, 0.4D, 0.0D), Mth.clamp(r, 24.0F, 40.0F) / 1.25F, 14, 0);
			ring("bt_ring", base.add(0.0D, TIER[1] * s, 0.0D), TIER_R[0] / 16.0F * s * 1.6F / 1.25F, 10, 2);
			at(impactAt + 1, () -> burst(base.add(0.0D, TIER[1] * s, 0.0D), 0xFFF0A0, 0xFFFFFF, 40, 1.0D));
			cue("impact", base.add(0.0D, TIER[1] * s, 0.0D));
			for (int k = 2; k >= 0; k--) {
				ModelHandle c = canopy[k];
				at(impactAt + 6 + (2 - k) * 6, () -> {
					if (c != null) {
						c.end(10);
					}
				});
			}
			at(impactAt + 24, () -> {
				if (trunk != null) {
					trunk.play(AnimStep.of(Anim.DISSOLVE, 0, 10, 0.0F, 0.0F));
				}
				for (ModelHandle root : roots) {
					root.end(10);
				}
			});
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (age < 40) {
				// life spiralling up the growing trunk
				double a = age * 0.5D;
				double y = age / 40.0D * TIER[2] * s;
				spend(Qi.mote(0x9AE05A, 0.6F, 24), base.x + Math.cos(a) * 2.0D, base.y + y, base.z + Math.sin(a) * 2.0D, 0.0D, 0.05D, 0.0D);
				return;
			}
			if (impactAt >= 0) {
				return;
			}
			boolean drift = age >= climax - 20;
			if (age % (drift ? 1 : 3) == 0) {
				// leaves falling from the canopy (more as the fruits ripen)
				for (int i = 0, n = EffectQuality.scale(drift ? 3 : 1); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = TIER_R[0] / 16.0D * s * Math.sqrt(random.nextDouble());
					spend(Qi.petal(i % 3 == 0 ? 0xFFD54A : 0x6AD050, 0.5F, 50), base.x + Math.cos(a) * r, base.y + TIER[0] * s, base.z + Math.sin(a) * r,
							(random.nextDouble() - 0.5D) * 0.08D, -0.06D, (random.nextDouble() - 0.5D) * 0.08D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return impactAt >= 0 ? age > impactAt + 46 : age > climax + 80;
		}
	}

	// ================================================================== Shadow · Segel Harimau Alam Baka

	private static final class Tiger extends Run {
		private static final int SPLIT = 14;
		private static final float ORBIT = 1.4F;
		private static final float TURN = 3.0F;

		final int life;
		final float yaw0;
		@Nullable ModelHandle whole;
		@Nullable ModelHandle left;
		@Nullable ModelHandle rightHalf;
		int clapAt = -1000;
		int endAt = -1;
		int novas;

		static void on(Ev ev) {
			Tiger t = find(ev.casterId(), Tiger.class);
			switch (ev.phase()) {
				case CAST -> begin(new Tiger(ev));
				case IMPACT -> {
					if (t != null) {
						t.clap();
					}
				}
				case END -> {
					if (t != null) {
						t.rejoin();
					}
				}
				default -> {
				}
			}
		}

		Tiger(Ev ev) {
			super(ev.casterId());
			life = 520;
			yaw0 = ev.yaw();
			film(ev, climaxOf(ev.spec(), 100), 130, ev.casterPos());
			Entity caster = ev.caster();
			if (caster != null && !EffectQuality.minimal()) {
				double eye = caster.getEyeHeight();
				ride("sts_flute", caster.getId(), caster.position(), new Vec3(-0.12D, eye - 0.12D, 0.34D), true, 0.8F, 90.0F, 50, FxLayer.Priority.DETAIL,
						AnimStep.of(Anim.GROW, 0, 4, 0.3F, 0.0F));
			}
			at(0, () -> whole = drive("sts_tally", t -> hand().add(0.0D, 0.6D * ease(Mth.clamp(t / 10.0F, 0.0F, 1.0F)), 0.0D), t -> yawOf(casterId, yaw0),
					1.2F, 0.0F, EffectQuality.minimal() ? 40 : SPLIT + 4, FxLayer.Priority.CORE, AnimStep.of(Anim.GROW, 0, 6, 0.2F, 0.0F)));
			if (!EffectQuality.minimal()) {
				at(SPLIT, this::split);
			}
			at(life, this::rejoin);
		}

		private Vec3 hand() {
			Entity caster = entity(casterId);
			if (caster == null) {
				return Vec3.ZERO;
			}
			return caster.getEyePosition().add(0.0D, -0.35D, 0.0D).add(right(caster.getYRot()).scale(0.35D)).add(flat(caster.getLookAngle()).scale(0.5D));
		}

		private Vec3 middle() {
			Entity caster = entity(casterId);
			return caster == null ? Vec3.ZERO : caster.position().add(0.0D, 1.3D, 0.0D);
		}

		/** The halves' distance from the middle at {@code t}: they clap together and part again on each nova. */
		private double radius(float t) {
			if (endAt >= 0) {
				return ORBIT * (1.0D - ease(Mth.clamp((t - endAt) / 6.0F, 0.0F, 1.0F)));
			}
			float into = t - clapAt;
			if (into < 0.0F || into > 11.0F) {
				return ORBIT;
			}
			if (into < 3.0F) {
				return Mth.lerp(into / 3.0F, ORBIT, 0.12F);
			}
			if (into < 5.0F) {
				return 0.12D;
			}
			return Mth.lerp(ease((into - 5.0F) / 6.0F), 0.12F, ORBIT);
		}

		private float angle(float t) {
			return yaw0 + TURN * (t - SPLIT);
		}

		private Vec3 half(float t, int side) {
			double a = Math.toRadians(angle(t) + (side > 0 ? 180.0D : 0.0D));
			Vec3 at = endAt >= 0 ? middle().add(hand().subtract(middle()).scale(ease(Mth.clamp((t - endAt) / 6.0F, 0.0F, 1.0F)))) : middle();
			return at.add(Math.cos(a) * radius(t), 0.0D, Math.sin(a) * radius(t));
		}

		private void split() {
			if (whole != null) {
				whole.end(2);
			}
			left = drive("sts_tally_l", t -> half(t, -1), this::angle, 1.2F, 0.0F, life, FxLayer.Priority.CORE, AnimStep.of(Anim.GROW, 0, 4, 0.6F, 0.0F));
			rightHalf = drive("sts_tally_r", t -> half(t, 1), this::angle, 1.2F, 0.0F, life, FxLayer.Priority.CORE,
					AnimStep.of(Anim.GROW, 0, 4, 0.6F, 0.0F));
			track("tally", left, t -> middle(), yaw0, 0.0F);
			cue("split", middle());
		}

		/** A nova (IMPACT): the halves clap together, a resentment ring, then they part again. */
		void clap() {
			if (endAt >= 0) {
				return;
			}
			novas++;
			clapAt = age();
			Vec3 m = middle();
			Entity caster = entity(casterId);
			Vec3 floor = caster != null ? caster.position() : m;
			at(clapAt + 3, () -> {
				for (ModelHandle h : new ModelHandle[] {left, rightHalf}) {
					if (h != null) {
						h.play(AnimStep.of(Anim.STAMP, 0, 2, 0.0F, 0.0F));
					}
				}
				burst(m, 0x601020, 0xFF6A2A, 14, 0.5D);
			});
			ring("sts_ring", floor.add(0.0D, 0.25D, 0.0D), 24.0F / 1.25F, 14, 3);
			cue("nova", m);
		}

		/** The end: the halves rejoin and sink into the hand. */
		void rejoin() {
			if (endAt >= 0) {
				return;
			}
			endAt = age();
			for (ModelHandle h : new ModelHandle[] {left, rightHalf}) {
				if (h != null) {
					at(endAt + 6, () -> h.end(4));
				}
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			if (age < SPLIT || endAt >= 0 || left == null || age % 2 != 0) {
				return;
			}
			RandomSource random = level.getRandom();
			for (int side = -1; side <= 1; side += 2) {
				Vec3 p = half(age, side);
				spend(Qi.smoke(random.nextBoolean() ? 0x301018 : 0x802030, 0.5F, 20), p.x, p.y, p.z, 0.0D, 0.02D, 0.0D);
			}
		}

		@Override
		boolean finished(int age) {
			return endAt >= 0 ? age > endAt + 14 : age > life + 20;
		}
	}

	// ================================================================== Shadow · Teknik Pusaka Kunpeng

	private static final class Kunpeng extends Run {
		private static final int SWAP = 16;

		final float yaw;
		final Vec3 kunPos;
		final float s;
		@Nullable ModelHandle kun;
		@Nullable ModelHandle peng;
		int releasedAt = -1;
		int flapAt = -1;
		int climbAt = -1;
		Vec3 climbFrom = Vec3.ZERO;
		Vec3 dash = Vec3.ZERO;
		boolean swapped;

		static void on(Ev ev) {
			Kunpeng k = find(ev.casterId(), Kunpeng.class);
			switch (ev.phase()) {
				case CHARGE -> begin(new Kunpeng(ev));
				case CAST, TRAVEL -> {
					if (k == null && ev.phase() == Phase.CAST) {
						k = begin(new Kunpeng(ev));
					}
					if (k != null) {
						k.release(ev);
					}
				}
				case EXTRA0 -> {
					if (k != null) {
						k.wave(ev.pos(), ev.radius());
					}
				}
				default -> {
				}
			}
		}

		Kunpeng(Ev ev) {
			super(ev.casterId());
			yaw = ev.yaw();
			s = EffectQuality.minimal() ? 12.0F : 20.0F;
			kunPos = ev.casterPos().add(0.0D, 14.0D, 0.0D).subtract(fwd(yaw).scale(10.0D));
			film(ev, -1, 100, ev.hasTo() ? ev.to() : ev.casterPos().add(fwd(yaw).scale(32.0D)));
			at(0, () -> {
				// the Kun breaches from the cloud sea broadside, turns to the front ...
				kun = show("kp_kun", kunPos, s * 0.8F, yaw + 90.0F, 0.0F, 24, FxLayer.Priority.CORE, AnimStep.of(Anim.RISE, 0, 14, 10.0F, 0.0F));
				if (kun != null) {
					ModelHandle k = kun;
					at(4, () -> k.rotateTo(yaw, 0.0F, 0.0F, 10));
				}
			});
			// ... and becomes the Peng
			at(SWAP, this::swap);
		}

		private void swap() {
			if (swapped) {
				return;
			}
			swapped = true;
			if (kun != null) {
				kun.end(2);
			}
			peng = drive("kp_peng", this::pengAt, this::pengYaw, s, 0.0F, 200, FxLayer.Priority.CORE, AnimStep.of(Anim.GROW, 0, 4, 0.6F, 0.0F));
			track("peng", peng, this::pengAt, yaw, 0.0F);
			cue("turn", kunPos);
		}

		private Vec3 follow(float t) {
			Entity caster = entity(casterId);
			if (caster == null) {
				return kunPos;
			}
			return caster.position().add(0.0D, 7.0D, 0.0D).subtract(fwd(caster.getYRot()).scale(6.0D));
		}

		private @Nullable Vec3 pengAt(float t) {
			if (climbAt >= 0) {
				float k = Mth.clamp((t - climbAt) / 30.0F, 0.0F, 1.0F);
				return climbFrom.add(0.0D, 30.0D * k * k, 0.0D).add(dash.scale(20.0D * k));
			}
			if (releasedAt < 0) {
				return kunPos;
			}
			float k = ease(Mth.clamp((t - releasedAt) / 8.0F, 0.0F, 1.0F));
			Vec3 to = follow(t);
			return kunPos.add(to.subtract(kunPos).scale(k));
		}

		private float pengYaw(float t) {
			return releasedAt < 0 || climbAt >= 0 ? (climbAt >= 0 ? (float) Math.toDegrees(Math.atan2(-dash.x, dash.z)) : yaw) : yawOf(casterId, yaw);
		}

		/** The release (CAST or the dash TRAVEL): the Peng takes the caster's back, crescents leave its wingtips. */
		void release(Ev ev) {
			if (releasedAt >= 0) {
				return;
			}
			swap();
			releasedAt = age();
			Vec3 to = ev.hasTo() ? ev.to().subtract(ev.pos()) : fwd(ev.yaw());
			dash = flat(to);
			cue("release", ev.casterPos());
			if (!EffectQuality.minimal()) {
				int n = EffectQuality.maximal() ? 4 : 2;
				for (int i = 0; i < n; i++) {
					int side = i % 2 == 0 ? 1 : -1;
					int delay = (i / 2) * 4;
					at(releasedAt + 2 + delay, () -> {
						Vec3 at = pengAt(age());
						if (at == null) {
							return;
						}
						float y = yawOf(casterId, yaw);
						Vec3 tip = at.add(right(y).scale(side * s * 1.4D));
						show("kp_crescent", tip, 3.0F, y, 0.0F, 14, FxLayer.Priority.DETAIL, AnimStep.of(Anim.THRUST, 0, 12, 14.0F, 0.0F));
					});
				}
			}
			// a lost wave event still sends the Peng away
			at(releasedAt + 60, () -> climb(age()));
		}

		/** The wave at the front's end (EXTRA0): one great flap, a ring of wind. */
		void wave(Vec3 at, float radius) {
			if (flapAt >= 0) {
				return;
			}
			flapAt = age();
			if (peng != null) {
				peng.play(AnimStep.of(Anim.TILT, 0, 4, -45.0F, 0.0F));
				peng.play(AnimStep.of(Anim.TILT, 4, 6, 45.0F, 0.0F));
			}
			ring("kp_ring", at.add(0.0D, 0.3D, 0.0D), Mth.clamp(radius, 8.0F, 24.0F) / 1.25F, 12, 0);
			Vec3 p = pengAt(flapAt);
			if (p != null) {
				ClientLevel level = Minecraft.getInstance().level;
				if (level != null) {
					RandomSource random = level.getRandom();
					for (int i = 0, n = EffectQuality.scale(16); i < n; i++) {
						spend(Qi.feather(0xE8F0FF, 0.7F, 50), p.x + (random.nextDouble() - 0.5D) * s, p.y, p.z + (random.nextDouble() - 0.5D) * s,
								(random.nextDouble() - 0.5D) * 0.2D, -0.05D, (random.nextDouble() - 0.5D) * 0.2D);
					}
				}
			}
			cue("impact", at);
			at(flapAt + 12, () -> climb(age()));
		}

		private void climb(int now) {
			if (climbAt >= 0) {
				return;
			}
			Vec3 from = pengAt(now);
			climbFrom = from == null ? kunPos : from;
			climbAt = now;
			if (peng != null) {
				peng.play(AnimStep.of(Anim.DISSOLVE, 24, 10, 0.0F, 0.0F));
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (age < SWAP + 4) {
				// the cloud sea the Kun breaks out of
				for (int i = 0, n = EffectQuality.scale(4); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = 4.0D + random.nextDouble() * 10.0D;
					spend(Qi.mist(0xF4F8FF, 2.0F, 30), kunPos.x + Math.cos(a) * r, kunPos.y - 8.0D, kunPos.z + Math.sin(a) * r, 0.0D, 0.02D, 0.0D);
				}
				return;
			}
			if (releasedAt >= 0 && climbAt < 0 && age % 2 == 0) {
				Vec3 p = pengAt(age);
				if (p != null) {
					for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
						spend(Qi.mist(0xE8F0FF, 1.0F, 16), p.x + (random.nextDouble() - 0.5D) * s, p.y, p.z + (random.nextDouble() - 0.5D) * s,
								-dash.x * 0.4D, 0.0D, -dash.z * 0.4D);
					}
				}
			}
		}

		@Override
		boolean finished(int age) {
			return climbAt >= 0 ? age > climbAt + 40 : age > 200;
		}
	}

	// ================================================================== Shadow · Wilayah Karma (disciples)

	private static final class Karma extends Run {
		final int duration;
		final Map<Integer, ModelHandle> knots = new HashMap<>();
		@Nullable ModelHandle wheel;
		int spokes;
		boolean ended;

		static void on(Ev ev) {
			Karma k = find(ev.casterId(), Karma.class);
			switch (ev.phase()) {
				case CAST -> begin(new Karma(ev));
				case PULSE -> {
					if (k != null) {
						k.pulse();
					}
				}
				case HIT -> {
					if (k != null && ev.p().hasTarget()) {
						k.knot(ev.p().target());
					}
				}
				case END -> {
					if (k != null) {
						k.finish();
					}
				}
				default -> {
				}
			}
		}

		Karma(Ev ev) {
			super(ev.casterId());
			duration = Mth.clamp(ev.duration() > 0 ? ev.duration() : 400, 40, 1200);
			Entity caster = ev.caster();
			if (caster != null) {
				wheel = ride("kd_wheel", caster.getId(), caster.position(), new Vec3(0.0D, caster.getBbHeight() + 2.2D, 0.0D), false, 4.0F, 0.0F, duration + 20,
						FxLayer.Priority.CORE, AnimStep.of(Anim.GROW, 0, 10, 0.1F, 0.0F), AnimStep.of(Anim.SPIN, 0, duration, 1.0F, 0.0F));
			}
			at(duration, this::finish);
		}

		void pulse() {
			if (ended) {
				return;
			}
			spokes++;
			if (wheel != null) {
				wheel.rotateTo(45.0F * spokes, 0.0F, 0.0F, 4);
			}
			Entity caster = entity(casterId);
			for (Map.Entry<Integer, ModelHandle> e : knots.entrySet()) {
				e.getValue().play(AnimStep.of(Anim.PULSE, 0, 8, 0.1F, 0.0F));
				Entity foe = entity(e.getKey());
				if (caster != null && foe != null) {
					thread(caster.position().add(0.0D, caster.getBbHeight() * 0.6D, 0.0D), foe.position().add(0.0D, foe.getBbHeight() * 0.6D, 0.0D));
				}
			}
		}

		void knot(int target) {
			if (ended || knots.containsKey(target) || knots.size() >= 6 || EffectQuality.minimal()) {
				return;
			}
			Entity foe = entity(target);
			if (foe == null) {
				return;
			}
			ModelHandle knot = ride("kd_knot", target, foe.position(), new Vec3(0.0D, foe.getBbHeight() * 0.6D, 0.0D), false, 0.5F, 0.0F,
					Math.max(20, duration - age() + 10), FxLayer.Priority.DETAIL, AnimStep.of(Anim.GROW, 0, 6, 0.2F, 0.0F), AnimStep.of(Anim.SPIN, 0, duration, 2.0F, 0.0F));
			if (knot != null) {
				knots.put(target, knot);
			}
		}

		void finish() {
			if (ended) {
				return;
			}
			ended = true;
			for (ModelHandle knot : knots.values()) {
				knot.play(AnimStep.of(Anim.SHATTER, 0, 3, 0.0F, 0.0F));
			}
			if (wheel != null) {
				ModelHandle w = wheel;
				at(age() + 10, () -> w.end(10));
			}
		}

		private void thread(Vec3 a, Vec3 b) {
			int n = Math.max(4, EffectQuality.scale(10));
			for (int i = 1; i < n; i++) {
				Vec3 p = a.add(b.subtract(a).scale(i / (double) n));
				spend(Qi.mote(0xD04020, 0.3F, 14), p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
			}
		}

		@Override
		void emit(ClientLevel level, int age) {
		}

		@Override
		boolean finished(int age) {
			return age > duration + 40;
		}
	}

	// ================================================================== Immortal · Segel Kesembilan

	private static final class Hex extends Run {
		private static final double SKY = 24.0D;
		private static final float SEAL_SPIN = 1.0F;

		final Vec3 centre;
		final float yaw;
		final int channel;
		final float s;
		final double pointR;
		final List<ModelHandle> pillars = new ArrayList<>(8);
		final List<Vec3> points = new ArrayList<>(8);
		@Nullable ModelHandle hover;
		@Nullable ModelHandle seal;
		int hoverAt = -1;
		int pulses;
		int impactAt = -1;

		static void on(Ev ev) {
			Hex h = find(ev.casterId(), Hex.class);
			switch (ev.phase()) {
				case CAST -> begin(new Hex(ev));
				case PULSE -> {
					if (h != null) {
						h.pulse();
					}
				}
				case EXTRA0 -> {
					if (h != null) {
						h.release();
					}
				}
				default -> {
				}
			}
		}

		Hex(Ev ev) {
			super(ev.casterId());
			centre = ev.pos();
			yaw = ev.yaw();
			channel = Mth.clamp(ev.duration() > 0 ? ev.duration() : climaxOf(ev.spec(), 160), 20, 400);
			s = EffectQuality.minimal() ? 9.0F : 12.0F;
			// the seal's eight points lie 19.8 model pixels from its centre: the pillars stand under them
			pointR = 19.8D / 16.0D * s;
			for (int k = 0; k < 8; k++) {
				double a = Math.toRadians(yaw + 45.0D * k);
				points.add(centre.add(Math.cos(a) * pointR, 0.0D, Math.sin(a) * pointR));
			}
			film(ev, climaxOf(ev.spec(), 160), channel + 50, centre);
			at(0, this::pillar);
			at(channel - 10, this::hover);
		}

		private void pillar() {
			if (pillars.size() >= Math.min(8, points.size()) || impactAt >= 0) {
				return;
			}
			Vec3 at = points.get(pillars.size());
			ModelHandle pillar = show("nh_pillar", at, 3.0F, ModelFx.yawTo(at, centre), 0.0F, channel + 80, pillars.isEmpty() ? FxLayer.Priority.CORE : FxLayer.Priority.DETAIL,
					AnimStep.of(Anim.RISE, 0, 10, 4.0F, 0.0F));
			if (pillar == null) {
				points.remove(pillars.size());
				return;
			}
			pillars.add(pillar);
			burst(at, 0xC8BEDC, 0x7A6AA0, 8, 0.3D);
		}

		void pulse() {
			pulses++;
			if (!EffectQuality.minimal()) {
				pillar();
			}
			cue("pillar", centre);
		}

		/** K-10: the eight-pointed seal appears 24 blocks over the domain, turning slowly. */
		private void hover() {
			if (hover != null || impactAt >= 0) {
				return;
			}
			hoverAt = age();
			hover = show("nh_seal", centre.add(0.0D, SKY, 0.0D), s, yaw, 0.0F, 60, FxLayer.Priority.CORE, AnimStep.of(Anim.GROW, 0, 10, 0.3F, 0.0F),
					AnimStep.of(Anim.SPIN, 0, 60, SEAL_SPIN, 0.0F));
			track("seal", hover, t -> centre.add(0.0D, SKY, 0.0D), yaw, 0.0F);
			cue("release", centre.add(0.0D, SKY, 0.0D));
		}

		/** The release (EXTRA0, once): the seal stamps down, the pillars lean in and crack. */
		void release() {
			if (impactAt >= 0) {
				return;
			}
			impactAt = age();
			float turned = hoverAt >= 0 ? yaw + SEAL_SPIN * (impactAt - hoverAt) : yaw;
			if (hover != null) {
				hover.end(1);
			}
			seal = show("nh_seal", centre.add(0.0D, 0.2D, 0.0D), s, turned, 0.0F, 50, FxLayer.Priority.CORE, AnimStep.of(Anim.FALL, 0, 6, (float) SKY, 0.0F),
					AnimStep.of(Anim.STAMP, 6, 2, 0.0F, 0.0F));
			int made = impactAt;
			track("seal", seal, t -> centre.add(0.0D, 0.2D + SKY * (1.0D - Math.pow(Mth.clamp((t - made) / 6.0F, 0.0F, 1.0F), 2.0D)), 0.0D), turned, 0.0F);
			at(impactAt + 6, () -> {
				ring("nh_ring", centre.add(0.0D, 0.3D, 0.0D), (float) pointR * 1.4F / 1.25F, 12, 0);
				burst(centre, 0xFFFFFF, 0xE8D8A0, 40, 1.2D);
				for (ModelHandle pillar : pillars) {
					pillar.play(AnimStep.of(Anim.TILT, 0, 4, 22.5F, 0.0F));
					pillar.swap("nh_pillar_cracked");
				}
				cue("impact", centre);
			});
			at(impactAt + 20, () -> {
				for (int i = 0; i < pillars.size(); i++) {
					pillars.get(i).moveTo(points.get(i).add(0.0D, -4.6D, 0.0D), 20);
				}
			});
			at(impactAt + 34, () -> {
				for (ModelHandle pillar : pillars) {
					pillar.end(8);
				}
			});
			at(impactAt + 26, () -> {
				if (seal != null) {
					seal.end(20);
				}
			});
		}

		@Override
		void emit(ClientLevel level, int age) {
			if (impactAt >= 0) {
				return;
			}
			RandomSource random = level.getRandom();
			if (age % 3 == 0) {
				// violet motes rising inside the sealed domain
				for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = pointR * Math.sqrt(random.nextDouble());
					spend(Qi.mote(0x7A6AA0, 0.5F, 30), centre.x + Math.cos(a) * r, centre.y + 0.3D, centre.z + Math.sin(a) * r, 0.0D, 0.08D, 0.0D);
				}
			}
			if (hover != null && age % 2 == 0) {
				// gold dust shed by the hovering seal
				for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					double r = pointR * random.nextDouble();
					spend(Qi.glint(0xE8D8A0, 0.4F, 30), centre.x + Math.cos(a) * r, centre.y + SKY - 0.5D, centre.z + Math.sin(a) * r, 0.0D, -0.12D, 0.0D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			return impactAt >= 0 ? age > impactAt + 50 : age > channel + 80;
		}
	}

	// ================================================================== Immortal · Gunung Lima Unsur

	private static final class Mountain extends Run {
		/** The generating order (wood → fire → earth → metal → water); ring index i takes element ORDER[i]. */
		private static final String[] ORDER = {"wood", "fire", "earth", "metal", "water"};

		final Vec3 aim;
		final float yaw;
		final int climax;
		final float s;
		int palmAt = -1;
		int impactAt = -1;
		boolean talisman;
		@Nullable ModelHandle palm;
		final boolean[] risen = new boolean[5];

		static void on(Ev ev) {
			Mountain m = find(ev.casterId(), Mountain.class);
			switch (ev.phase()) {
				case CAST -> begin(new Mountain(ev));
				case TRAVEL -> {
					if (ev.preImpact() && ev.hasTo()) {
						if (m == null) {
							m = begin(new Mountain(ev));
						}
						m.palm(ev.to(), ev.duration());
					}
				}
				case IMPACT -> {
					if (m != null) {
						m.impact(ev.pos(), ev.radius());
					}
				}
				case EXTRA0 -> {
					if (m != null) {
						m.peak(ev.pos(), ev.p().index(), ev.p().of());
					}
				}
				default -> {
				}
			}
		}

		Mountain(Ev ev) {
			super(ev.casterId());
			aim = ev.phase() == Phase.TRAVEL ? ev.to() : ev.pos();
			yaw = ev.caster() != null ? ModelFx.yawTo(ev.caster().position(), aim) : ev.yaw();
			climax = climaxOf(ev.spec(), 40);
			s = EffectQuality.minimal() ? 16.0F : 24.0F;
			film(ev, climax, climax + 120, aim);
		}

		/** The pre-impact TRAVEL: the palm falls 64 blocks palm-down through the parting clouds. */
		void palm(Vec3 land, int fall) {
			if (palmAt >= 0) {
				return;
			}
			palmAt = age();
			int ticks = Math.max(10, fall > 0 ? fall : climax);
			Vec3 at = land.add(0.0D, 2.0D / 16.0D * s, 0.0D);
			palm = show("fem_palm", at, s, yaw, 0.0F, ticks + 50, FxLayer.Priority.CORE, AnimStep.of(Anim.FALL, 0, ticks, 64.0F, 0.0F),
					AnimStep.of(Anim.STAMP, ticks, 2, 0.0F, 0.0F));
			int made = palmAt;
			track("palm", palm, t -> at.add(0.0D, 64.0D * (1.0D - Math.pow(Mth.clamp((t - made) / ticks, 0.0F, 1.0F), 2.0D)), 0.0D), yaw, 0.0F);
			ClientLevel level = Minecraft.getInstance().level;
			if (level != null) {
				// the clouds part where the palm comes through
				for (int i = 0, n = EffectQuality.scale(24); i < n; i++) {
					double a = Math.PI * 2.0D * i / n;
					spend(Qi.mist(0xFFFFFF, 3.0F, 40), land.x + Math.cos(a) * 6.0D, land.y + 64.0D, land.z + Math.sin(a) * 6.0D, Math.cos(a) * 0.4D, 0.0D,
							Math.sin(a) * 0.4D);
				}
			}
		}

		void impact(Vec3 at, float radius) {
			if (impactAt >= 0) {
				return;
			}
			impactAt = age();
			ring("fem_ring", at.add(0.0D, 0.3D, 0.0D), Mth.clamp(radius, 12.0F, 24.0F) / 1.25F, 12, 0);
			burst(at, 0xFFE8A0, 0x8A7A60, 36, 1.0D);
			cue("impact", at);
			if (palm != null) {
				ModelHandle p = palm;
				at(impactAt + 20, () -> p.moveTo(aim.add(0.0D, 2.0D / 16.0D * s + 30.0D, 0.0D), 20));
				at(impactAt + 32, () -> p.end(10));
			}
			at(impactAt + 30, this::talisman);
		}

		/** One ring point (EXTRA0 index i of 5): its element's peak rises, in generating order. */
		void peak(Vec3 at, int index, int of) {
			int i = Math.floorMod(index, 5);
			if (risen[i]) {
				return;
			}
			risen[i] = true;
			int delay = i * 6;
			String element = ORDER[i];
			ModelHandle peak = show("fem_peak_" + element, at, 10.0F, ModelFx.yawTo(aim, at), 0.0F, 140, FxLayer.Priority.CORE,
					AnimStep.of(Anim.RISE, delay, 10, 14.0F, 0.0F));
			if (peak == null) {
				return;
			}
			int now = age();
			track("peak_" + (i + 1), peak, t -> at.add(0.0D, 7.5D, 0.0D), ModelFx.yawTo(aim, at), 0.0F);
			at(now + delay, () -> {
				burst(at, elementColour(element), 0xFFFFFF, 14, 0.5D);
				cue("peak", at);
			});
			at(now + 120, () -> peak.moveTo(at.add(0.0D, -15.0D, 0.0D), 18));
			at(now + 134, () -> peak.end(6));
		}

		/** The aftermath: the six-syllable talisman descends onto the circle. */
		private void talisman() {
			if (talisman) {
				return;
			}
			talisman = true;
			ModelHandle t = show("fem_talisman", aim.add(0.0D, 3.5D, 0.0D), 3.0F, yaw, 0.0F, 44, FxLayer.Priority.DETAIL, AnimStep.of(Anim.FALL, 0, 20, 14.0F, 0.0F));
			if (t != null) {
				at(age() + 30, () -> t.end(12));
			}
		}

		private static int elementColour(String element) {
			return switch (element) {
				case "wood" -> 0x3FAE3A;
				case "fire" -> 0xE03A1A;
				case "earth" -> 0xD9A05B;
				case "metal" -> 0xE8E8EE;
				default -> 0x3A7AE8;
			};
		}

		@Override
		void emit(ClientLevel level, int age) {
			if (palm == null || impactAt >= 0 || palmAt < 0) {
				return;
			}
			// gold sparks streaming up off the falling palm
			RandomSource random = level.getRandom();
			float k = Mth.clamp((age - palmAt) / (float) Math.max(1, climax), 0.0F, 1.0F);
			double h = 64.0D * (1.0D - k * k);
			for (int i = 0, n = EffectQuality.scale(4); i < n; i++) {
				spend(Qi.spark(0xFFD54A, 0.5F, 12), aim.x + (random.nextDouble() - 0.5D) * s, aim.y + h + 2.0D, aim.z + (random.nextDouble() - 0.5D) * s,
						0.0D, 0.6D, 0.0D);
			}
		}

		@Override
		boolean finished(int age) {
			return age > Math.max(climax, impactAt) + 160;
		}
	}

	// ================================================================== Immortal · Diagram Taiji

	private static final class Taiji extends Run {
		final float s;
		final int planksMax;
		final List<ModelHandle> planks = new ArrayList<>();
		final List<Vec3> plankAt = new ArrayList<>();
		final List<Boolean> flipped = new ArrayList<>();
		@Nullable ModelHandle disc;
		int releasedAt = -1;
		int foldAt = -1;
		int standAt = -1;
		Vec3 origin = Vec3.ZERO;
		Vec3 far = Vec3.ZERO;
		Vec3 dir = Vec3.ZERO;
		double length;
		double spacing;
		float pathYaw;

		static void on(Ev ev) {
			Taiji t = find(ev.casterId(), Taiji.class);
			switch (ev.phase()) {
				case CHARGE -> begin(new Taiji(ev));
				case CAST, TRAVEL -> {
					if (t == null) {
						t = begin(new Taiji(ev));
					}
					t.release(ev);
				}
				case HIT -> {
					if (t != null) {
						t.hit(ev.pos());
					}
				}
				default -> {
				}
			}
		}

		Taiji(Ev ev) {
			super(ev.casterId());
			s = EffectQuality.minimal() ? 6.0F : 9.0F;
			planksMax = EffectQuality.minimal() ? 0 : EffectQuality.maximal() ? 32 : 16;
			film(ev, -1, (ev.phase() == Phase.CHARGE ? ev.duration() : 0) + 90, ev.hasTo() ? ev.to() : ev.casterPos().add(fwd(ev.yaw()).scale(32.0D)));
			at(0, () -> {
				disc = drive("tcd_taiji", this::discAt, this::discYaw, 1.6F, 0.0F, 600, FxLayer.Priority.CORE, AnimStep.of(Anim.GROW, 0, 12, 0.1F, 0.0F),
						AnimStep.of(Anim.SPIN, 0, 600, 6.0F, 2.0F));
				track("disc", disc, this::discAt, ev.yaw(), 0.0F);
			});
		}

		private Vec3 palms() {
			Entity caster = entity(casterId);
			if (caster == null) {
				return origin;
			}
			return caster.getEyePosition().add(0.0D, -0.45D, 0.0D).add(fwd(caster.getYRot()).scale(1.0D));
		}

		/** Where the disc is: between the palms, then flying out 10 blocks and rolling along the unrolling planks. */
		private @Nullable Vec3 discAt(float t) {
			if (releasedAt < 0) {
				return palms();
			}
			float into = t - releasedAt;
			double lift = s * 0.5D;
			Vec3 out = origin.add(dir.scale(Math.min(10.0D, length))).add(0.0D, lift, 0.0D);
			if (into < 6.0F) {
				return palmsAtRelease.add(out.subtract(palmsAtRelease).scale(ease(into / 6.0F)));
			}
			double along = Math.min(length, Math.max(10.0D, (into / 2.0D + 1.0D) * spacing));
			return origin.add(dir.scale(along)).add(0.0D, lift, 0.0D);
		}

		Vec3 palmsAtRelease = Vec3.ZERO;

		private float discYaw(float t) {
			if (releasedAt < 0) {
				return yawOf(casterId, 0.0F);
			}
			// rolling: the disc turns side-on to the path; standing at the end: facing back down the path
			return standAt >= 0 && t >= standAt ? pathYaw + 180.0F : pathYaw + 90.0F;
		}

		/** The release (CAST at the front's origin, or the band's TRAVEL): fly out, then unroll the planks. */
		void release(Ev ev) {
			if (releasedAt >= 0) {
				return;
			}
			Vec3 from = ev.pos();
			if (ev.phase() == Phase.TRAVEL && ev.preImpact() && ev.p().aux() > 0) {
				// the band's event comes from the sky over the line: its ground is aux/100 blocks below
				from = from.add(0.0D, -ev.p().aux() / 100.0D, 0.0D);
			}
			Vec3 to = ev.hasTo() ? ev.to() : from.add(fwd(ev.yaw()).scale(48.0D));
			to = new Vec3(to.x, from.y, to.z);
			length = Mth.clamp(from.distanceTo(to), 8.0D, 128.0D);
			dir = flat(to.subtract(from));
			if (dir.lengthSqr() < 1.0E-6D) {
				dir = fwd(ev.yaw());
			}
			origin = from;
			far = from.add(dir.scale(length));
			pathYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			palmsAtRelease = palms();
			releasedAt = age();
			if (disc != null) {
				disc.scaleTo(s, 8);
			}
			cue("release", origin);
			int n = planksMax;
			spacing = n > 0 ? length / n : length / 16.0D;
			for (int i = 0; i < n; i++) {
				int index = i;
				at(releasedAt + 4 + i * 2, () -> plank(index));
			}
			int unrolled = releasedAt + 4 + Math.max(16, n) * 2 + 6;
			at(unrolled + 10, this::fold);
		}

		private void plank(int i) {
			Vec3 at = origin.add(dir.scale(spacing * (i + 0.5D))).add(0.0D, i % 2 == 0 ? 0.08D : 0.12D, 0.0D);
			ModelHandle plank = show(i % 2 == 0 ? "tcd_plank_yin" : "tcd_plank_yang", at, 3.0F, pathYaw, 0.0F, 200, FxLayer.Priority.DETAIL,
					AnimStep.of(Anim.GROW, 0, 6, 0.1F, 0.0F));
			if (plank != null) {
				planks.add(plank);
				plankAt.add(at);
				flipped.add(false);
				spend(Qi.smoke(i % 2 == 0 ? 0x16141C : 0xF8F6EC, 1.0F, 20), at.x, at.y + 0.3D, at.z, 0.0D, 0.04D, 0.0D);
			}
		}

		/** A struck foe: the plank under it flips over (yin shows yang). */
		void hit(Vec3 at) {
			int best = -1;
			double bestD = Math.max(4.0D, spacing * 1.5D);
			for (int i = 0; i < plankAt.size(); i++) {
				Vec3 p = plankAt.get(i);
				double d = Math.hypot(p.x - at.x, p.z - at.z);
				if (d < bestD && !flipped.get(i)) {
					bestD = d;
					best = i;
				}
			}
			if (best >= 0) {
				flipped.set(best, true);
				planks.get(best).play(AnimStep.of(Anim.TILT, 0, 4, 180.0F, 0.0F));
				cue("hit", plankAt.get(best));
			}
		}

		/** The aftermath: the planks fold back into the disc at the far end, which stands upright. */
		private void fold() {
			if (foldAt >= 0) {
				return;
			}
			foldAt = age();
			Vec3 end = discAt(foldAt);
			Vec3 into = end == null ? far : end;
			for (int k = 0; k < planks.size(); k++) {
				int i = planks.size() - 1 - k;
				ModelHandle plank = planks.get(i);
				at(foldAt + k * 2, () -> {
					plank.play(new AnimStep(Anim.SLIDE, 0, 6, 0.0F, 1.0F, into));
					at(age() + 6, () -> plank.end(2));
				});
			}
			standAt = foldAt + planks.size() * 2 + 6;
			at(standAt, () -> {
				burst(into, 0xFFFFFF, 0x16141C, 24, 0.6D);
				cue("impact", into);
			});
			at(standAt + 30, () -> {
				if (disc != null) {
					disc.end(20);
				}
			});
		}

		@Override
		void emit(ClientLevel level, int age) {
			RandomSource random = level.getRandom();
			if (releasedAt < 0) {
				// black and white motes spiralling into the palms
				Vec3 p = palms();
				for (int i = 0, n = EffectQuality.scale(2); i < n; i++) {
					double a = random.nextDouble() * Math.PI * 2.0D;
					spend(Qi.mote(i % 2 == 0 ? 0xFFFFFF : 0x202028, 0.4F, 12), p.x + Math.cos(a) * 1.4D, p.y + (random.nextDouble() - 0.5D), p.z + Math.sin(a) * 1.4D,
							-Math.cos(a) * 0.1D, 0.0D, -Math.sin(a) * 0.1D);
				}
				return;
			}
			if (foldAt < 0) {
				Vec3 p = discAt(age);
				if (p != null) {
					spend(Qi.spark(age % 2 == 0 ? 0xFFFFFF : 0xFFD54A, 0.5F, 12), p.x, p.y - s * 0.45D, p.z, 0.0D, 0.1D, 0.0D);
				}
			}
		}

		@Override
		boolean finished(int age) {
			if (standAt >= 0) {
				return age > standAt + 54;
			}
			return releasedAt < 0 ? age > 400 : age > releasedAt + 200;
		}
	}

	// ================================================================== Immortal · Langkah Penginjak Langit

	private static final class Trampling extends Run {
		private static final int STEPS = 9;
		private static final int EVERY = 3;
		/** The nine Dipper stars (seven of the Beidou and its two hidden attendants), in the order the steps light them. */
		private static final double[][] DIPPER = {{-4.0D, 0.0D}, {-2.8D, 0.6D}, {-1.6D, 0.8D}, {-0.4D, 0.9D}, {-0.2D, 2.0D}, {1.5D, 2.4D}, {1.7D, 1.2D},
				{-3.0D, 1.6D}, {-4.2D, 1.2D}};

		final Vec3 start;
		final float yaw;
		final int climax;
		final float s;
		Vec3 aim;
		final List<ModelHandle> rings = new ArrayList<>(9);
		final List<ModelHandle> stars = new ArrayList<>(9);
		@Nullable ModelHandle foot;
		@Nullable Vec3 footAt;
		int impactAt = -1;

		static void on(Ev ev) {
			Trampling t = find(ev.casterId(), Trampling.class);
			switch (ev.phase()) {
				case CAST -> begin(new Trampling(ev));
				case IMPACT -> {
					if (t != null) {
						t.impact(ev.pos(), ev.radius());
					}
				}
				case EXTRA0 -> {
					if (t != null) {
						t.wave(ev.pos(), ev.radius());
					}
				}
				default -> {
				}
			}
		}

		Trampling(Ev ev) {
			super(ev.casterId());
			start = ev.casterPos();
			yaw = ev.yaw();
			climax = climaxOf(ev.spec(), 30);
			s = EffectQuality.minimal() ? 9.0F : 12.0F;
			Vec3 to = ev.hasTo() ? ev.to() : start.add(fwd(yaw).scale(24.0D));
			Vec3 flatTo = new Vec3(to.x - start.x, 0.0D, to.z - start.z);
			if (flatTo.length() > 48.0D) {
				to = start.add(flatTo.normalize().scale(48.0D)).add(0.0D, to.y - start.y, 0.0D);
			}
			aim = to;
			film(ev, climax, climax + 70, aim);
			for (int k = 0; k < STEPS; k++) {
				int index = k;
				at(k * EVERY, () -> step(index));
			}
			at(climax - 4, this::skyFoot);
		}

		/** Step k: a ring of light under the caster's foot, a Dipper star lit in the sky above the climb. */
		private void step(int k) {
			Entity caster = entity(casterId);
			Vec3 feet = caster != null ? caster.position() : start.add(0.0D, 24.0D * k / STEPS, 0.0D);
			if (!EffectQuality.minimal()) {
				ModelHandle ring = show("ht_step", feet.add(0.0D, 0.05D, 0.0D), 2.0F, yaw + k * 20.0F, 0.0F, climax + 20 - age(), FxLayer.Priority.DETAIL,
						AnimStep.of(Anim.GROW, 0, 3, 0.2F, 0.0F), AnimStep.of(Anim.SPIN, 0, 60, 2.0F, 0.0F));
				if (ring != null) {
					rings.add(ring);
				}
				Vec3 right = right(yaw);
				Vec3 star = start.add(0.0D, 26.0D + DIPPER[k][1] * 3.0D, 0.0D).add(right.scale(DIPPER[k][0] * 3.0D)).add(fwd(yaw).scale(2.0D));
				ModelHandle node = show("ht_star", star, 0.6F, yaw, 0.0F, climax + 100 - age(), FxLayer.Priority.DETAIL, AnimStep.of(Anim.GROW, 0, 4, 0.2F, 0.0F),
						AnimStep.of(Anim.SPIN, 0, 200, 1.5F, 0.0F));
				if (node != null) {
					stars.add(node);
				}
			}
			spend(Qi.groundRing(0xFFF4D0, 0.8F, 3.0F, 10), feet.x, feet.y + 0.05D, feet.z, 0.0D, 0.0D, 0.0D);
			cue("step", feet);
		}

		/** The foot of light forms over the aim and comes down onto it at K. */
		private void skyFoot() {
			if (foot != null || impactAt >= 0) {
				return;
			}
			footAt = aim;
			foot = show("ht_sky_foot", aim, s, yaw, 0.0F, 36, FxLayer.Priority.CORE, AnimStep.of(Anim.GROW, 0, 3, 0.3F, 0.0F),
					AnimStep.of(Anim.FALL, 0, 4, 16.0F, 0.0F), AnimStep.of(Anim.STAMP, 4, 2, 0.0F, 0.0F), AnimStep.of(Anim.DISSOLVE, 22, 10, 0.0F, 0.0F));
			int made = age();
			Vec3 at = aim;
			track("foot", foot, t -> at.add(0.0D, s * 0.6D + 16.0D * (1.0D - Math.pow(Mth.clamp((t - made) / 4.0F, 0.0F, 1.0F), 2.0D)), 0.0D), yaw, 0.0F);
			cue("release", aim.add(0.0D, 16.0D, 0.0D));
		}

		void impact(Vec3 at, float radius) {
			if (impactAt >= 0) {
				return;
			}
			impactAt = age();
			if (foot == null || footAt == null || footAt.distanceTo(at) > 6.0D) {
				// the server's strike landed elsewhere: the foot comes down there instead
				if (foot != null) {
					foot.end(2);
				}
				aim = at;
				foot = show("ht_sky_foot", at, s, yaw, 0.0F, 30, FxLayer.Priority.CORE, AnimStep.of(Anim.FALL, 0, 3, 16.0F, 0.0F),
						AnimStep.of(Anim.STAMP, 3, 2, 0.0F, 0.0F), AnimStep.of(Anim.DISSOLVE, 18, 10, 0.0F, 0.0F));
			}
			ring("ht_ring", at.add(0.0D, 0.3D, 0.0D), Mth.clamp(radius, 24.0F, 32.0F) / 1.25F, 12, 0);
			burst(at, 0xFFF4D0, 0xF0E0A0, 40, 1.2D);
			cue("impact", at);
			// the step rings collapse down the climb and fade
			for (int i = 0; i < rings.size(); i++) {
				ModelHandle ring = rings.get(i);
				at(impactAt + i, () -> ring.moveTo(new Vec3(start.x, start.y + 0.05D, start.z), 8));
				at(impactAt + i + 6, () -> ring.end(6));
			}
			at(impactAt + 60, () -> {
				for (ModelHandle star : stars) {
					star.end(16);
				}
			});
		}

		void wave(Vec3 at, float radius) {
			ring("ht_ring", at.add(0.0D, 0.3D, 0.0D), Mth.clamp(radius, 24.0F, 40.0F) / 1.25F, 10, 0);
			cue("wave", at);
		}

		@Override
		void emit(ClientLevel level, int age) {
			if (age > STEPS * EVERY || impactAt >= 0) {
				return;
			}
			Entity caster = entity(casterId);
			if (caster == null) {
				return;
			}
			// a trail of light motes behind the climbing caster
			RandomSource random = level.getRandom();
			Vec3 p = caster.position();
			for (int i = 0, n = EffectQuality.scale(3); i < n; i++) {
				spend(Qi.mote(0xFFF4D0, 0.5F, 20), p.x + (random.nextDouble() - 0.5D), p.y + random.nextDouble() * 1.6D, p.z + (random.nextDouble() - 0.5D),
						0.0D, -0.05D, 0.0D);
			}
		}

		@Override
		boolean finished(int age) {
			return age > Math.max(climax, impactAt) + 90;
		}
	}

	// ================================================================== own arts (jurus ciptaan)

	private static final class Own extends Run {
		final @Nullable Dao daoA;
		final @Nullable Dao daoB;
		@Nullable ModelHandle sealA;
		@Nullable ModelHandle sealB;
		final List<ModelHandle> ribs = new ArrayList<>(8);
		int needles;
		int last;
		int climaxAt = -1;
		boolean cut;
		boolean burst;
		boolean wave;
		@Nullable ModelHandle plate;

		static void on(Ev ev) {
			Own o = find(ev.casterId(), Own.class);
			Phase phase = ev.phase();
			if (phase == Phase.CAST) {
				begin(new Own(ev));
				return;
			}
			if (o == null) {
				return;
			}
			int extra = phase.extraIndex();
			if (extra == 1) {
				o.climax(ev.pos());
			} else if (extra >= 2) {
				o.form(ev, extra);
			}
		}

		Own(Ev ev) {
			super(ev.casterId());
			Dao a = null;
			Dao b = null;
			if (ev.own()) {
				// the own composition, read from the Ciptaan data BatinClient keeps (read only)
				int slot = CiptaanArts.slotOf(ev.spec().id());
				CompoundTag art = BatinClient.data().getListOrEmpty("ciptaan").getCompoundOrEmpty(slot);
				if (!art.getStringOr("name", "").isEmpty()) {
					a = Dao.byOrdinal(art.getIntOr("a", -1));
					b = Dao.byOrdinal(art.getIntOr("b", -1));
				}
			}
			if (b == null && ev.p().has(Payloads.TechFx.FLAG_TWO_DAO)) {
				b = daoOfColour(ev.p().aux() & 0xFFFFFF);
			}
			daoA = a;
			daoB = b == a ? null : b;
			film(ev, -1, 120, ev.hasTo() ? ev.to() : ev.casterPos().add(fwd(ev.yaw()).scale(16.0D)));
			at(0, () -> {
				sealA = drive(sealId(daoA), t -> behind(t, 0.0D, 0.0D), t -> yawOf(casterId, 0.0F) + 180.0F, 4.0F, 0.0F, 400, FxLayer.Priority.CORE,
						AnimStep.of(Anim.GROW, 0, 10, 0.1F, 0.0F), AnimStep.of(Anim.SPIN, 0, 400, 2.0F, 2.0F));
				if (daoB != null && !EffectQuality.minimal()) {
					sealB = drive(sealId(daoB), t -> behind(t, 1.3D, 0.9D), t -> yawOf(casterId, 0.0F) + 180.0F, 2.6F, 0.0F, 400, FxLayer.Priority.DETAIL,
							AnimStep.of(Anim.GROW, 0, 10, 0.1F, 0.0F), AnimStep.of(Anim.SPIN, 0, 400, -3.0F, 2.0F));
				}
			});
		}

		private static String sealId(@Nullable Dao dao) {
			return dao == null ? "oa_seal_none" : "oa_seal_" + dao.id();
		}

		private static @Nullable Dao daoOfColour(int rgb) {
			for (Dao dao : Dao.values()) {
				if ((dao.color() & 0xFFFFFF) == rgb) {
					return dao;
				}
			}
			return null;
		}

		/** Behind the caster's back (and {@code side} to its right, {@code up} higher), facing forward. */
		private Vec3 behind(float t, double side, double up) {
			Entity caster = entity(casterId);
			if (caster == null) {
				return Vec3.ZERO;
			}
			float y = caster instanceof LivingEntity living ? living.yBodyRot : caster.getYRot();
			return caster.position().add(0.0D, 1.7D + up, 0.0D).subtract(fwd(y).scale(1.1D)).add(right(y).scale(side));
		}

		/** One form's main hit (EXTRA2 cut ... EXTRA7 seal): its prop plays its beat. */
		void form(Ev ev, int extra) {
			last = age();
			Vec3 at = ev.pos();
			switch (extra) {
				case 2 -> {
					if (cut) {
						return;
					}
					cut = true;
					Vec3 from = ev.hand();
					Vec3 to = ev.hasTo() && ev.to().distanceToSqr(from) > at.distanceToSqr(from) ? ev.to() : at;
					show("oa_blade", from, 3.0F, ev.yaw(), 0.0F, 14, FxLayer.Priority.CORE, new AnimStep(Anim.SLIDE, 0, 8, 0.0F, 1.0F, to),
							AnimStep.of(Anim.DISSOLVE, 8, 6, 0.0F, 0.0F));
				}
				case 3 -> {
					if (burst) {
						return;
					}
					burst = true;
					show("oa_orb", at, 2.4F, 0.0F, 0.0F, 14, FxLayer.Priority.CORE, AnimStep.of(Anim.GROW, 0, 6, 0.1F, 0.0F),
							AnimStep.of(Anim.SPIN, 0, 10, 8.0F, 0.0F), AnimStep.of(Anim.SHATTER, 8, 3, 0.0F, 0.0F));
				}
				case 4 -> {
					if (needles >= 6 || EffectQuality.minimal() && needles >= 2) {
						return;
					}
					needles++;
					ModelHandle needle = show("oa_needle", at, 1.2F, ev.yaw(), 90.0F, 10, needles <= 2 ? FxLayer.Priority.CORE : FxLayer.Priority.DETAIL,
							AnimStep.of(Anim.FALL, 0, 6, 12.0F, 0.0F), AnimStep.of(Anim.SHATTER, 6, 3, 0.0F, 0.0F));
					if (needle != null) {
						at(age() + 6, () -> needles = Math.max(0, needles - 1));
					}
				}
				case 5 -> {
					if (wave) {
						return;
					}
					wave = true;
					Vec3 from = ev.casterPos().add(0.0D, 1.0D, 0.0D).add(fwd(ev.yaw()).scale(1.5D));
					show("oa_crescent", from, 3.0F, ev.yaw(), 0.0F, 14, FxLayer.Priority.CORE, AnimStep.of(Anim.THRUST, 0, 12, 8.0F, 0.0F));
				}
				case 6 -> {
					if (!ribs.isEmpty()) {
						for (ModelHandle rib : ribs) {
							rib.lightTo(15, 1);
						}
						at(age() + 3, () -> {
							for (ModelHandle rib : ribs) {
								rib.lightTo(10, 6);
							}
						});
						return;
					}
					int n = EffectQuality.minimal() ? 4 : 8;
					float scale = Mth.clamp(Math.max(4.0F, ev.radius()) / 1.25F, 2.0F, 8.0F);
					for (int i = 0; i < n; i++) {
						int index = i;
						at(age() + i / 4, () -> {
							ModelHandle rib = show("oa_rib", at, scale, index * 360.0F / n, 0.0F, 170, index < 4 ? FxLayer.Priority.CORE : FxLayer.Priority.DETAIL,
									AnimStep.of(Anim.GROW, 0, 10, 0.1F, 0.0F));
							if (rib != null) {
								ribs.add(rib);
							}
						});
					}
				}
				case 7 -> {
					if (plate == null) {
						plate = show("oa_plate", at.add(0.0D, 1.6D, 0.0D), 2.6F, ev.yaw(), 0.0F, 90, FxLayer.Priority.CORE, AnimStep.of(Anim.FALL, 0, 6, 6.0F, 0.0F),
								AnimStep.of(Anim.STAMP, 6, 2, 0.0F, 0.0F));
					} else if (plate.alive()) {
						plate.play(AnimStep.of(Anim.SHATTER, 0, 3, 0.0F, 0.0F));
					}
				}
				default -> {
				}
			}
			cue("form", at);
		}

		/** The climax (EXTRA1): the seals spin up and shatter (after they have grown in). */
		void climax(Vec3 at) {
			if (climaxAt >= 0) {
				return;
			}
			climaxAt = Math.max(age(), 16);
			last = Math.max(last, climaxAt);
			at(climaxAt, () -> {
				for (ModelHandle seal : new ModelHandle[] {sealA, sealB}) {
					if (seal != null) {
						seal.play(AnimStep.of(Anim.SPIN, 0, 10, 20.0F, 2.0F));
						seal.play(AnimStep.of(Anim.SHATTER, 10, 3, 0.0F, 0.0F));
					}
				}
				cue("impact", at);
			});
		}

		@Override
		void emit(ClientLevel level, int age) {
			if (sealA == null || climaxAt >= 0 && age > climaxAt + 10 || age % 3 != 0) {
				return;
			}
			RandomSource random = level.getRandom();
			Vec3 p = behind(age, 0.0D, 0.0D);
			int colour = daoA == null ? 0xFFD54A : daoA.color() & 0xFFFFFF;
			spend(Qi.glint(colour, 0.4F, 18), p.x + (random.nextDouble() - 0.5D) * 2.0D, p.y + (random.nextDouble() - 0.5D) * 2.0D, p.z + (random.nextDouble() - 0.5D) * 2.0D,
					0.0D, 0.03D, 0.0D);
		}

		@Override
		boolean finished(int age) {
			if (climaxAt >= 0 && age > climaxAt + 20 && age > last + 40) {
				return true;
			}
			return age > Math.max(240, last + 80);
		}
	}

	// ================================================================== helpers

	/** The spec's climax tick (its Dampak row), or {@code fallback}. */
	private static int climaxOf(TechniqueSpec spec, int fallback) {
		var dampak = spec.dampak();
		return dampak != null && dampak.climaxAt() > 0 ? dampak.climaxAt() : fallback;
	}

	private static @Nullable Entity entity(int id) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || id < 0) {
			return null;
		}
		Entity entity = level.getEntity(id);
		return entity == null || entity.isRemoved() ? null : entity;
	}

	private static float yawOf(int id, float fallback) {
		Entity entity = entity(id);
		return entity == null ? fallback : entity.getYRot();
	}

	/** The world direction a Minecraft yaw (degrees) faces, flat. */
	private static Vec3 fwd(float yawDeg) {
		float r = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0.0D, Mth.cos(r));
	}

	/** The right-hand side of a Minecraft yaw (degrees), flat. */
	private static Vec3 right(float yawDeg) {
		float r = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(r), 0.0D, -Mth.sin(r));
	}

	private static Vec3 flat(Vec3 v) {
		Vec3 f = new Vec3(v.x, 0.0D, v.z);
		double l = f.length();
		return l < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : f.scale(1.0D / l);
	}

	private static float ease(float t) {
		return ModelFx.cubic(t);
	}

	/**
	 * Where a REVOLVE prop is after {@code into} ticks (the same curve as {@link ModelFx}: eased in over 40 ticks, standing
	 * |b| out along its side when b &lt; 0).
	 */
	private static Vec3 revolve(Vec3 base, float yaw, float a, float b, float into) {
		float t = Math.max(0.0F, into + 1.0F);
		float run = t < 40.0F ? t * t / 80.0F : t - 20.0F;
		float turned = yaw + a * run;
		float side = b < 0.0F ? a >= 0.0F ? -90.0F : 90.0F : 0.0F;
		return base.add(fwd(turned + side).scale(Math.abs(b)));
	}

	/** A one-off burst of dust and sparks at a landing point. */
	private static void burst(Vec3 at, int dust, int spark, int count, double speed) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		RandomSource random = level.getRandom();
		int n = EffectQuality.scale(count);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2.0D * i / Math.max(1, n) + random.nextDouble() * 0.3D;
			double v = speed * (0.5D + random.nextDouble() * 0.6D);
			if (i % 3 == 0) {
				spend(Qi.spark(spark, 0.5F, 14), at.x, at.y + 0.5D, at.z, Math.cos(a) * v, 0.15D + random.nextDouble() * 0.3D, Math.sin(a) * v);
			} else {
				spend(Qi.smoke(dust, 1.2F, 26), at.x, at.y + 0.3D, at.z, Math.cos(a) * v * 0.5D, 0.05D + random.nextDouble() * 0.08D, Math.sin(a) * v * 0.5D);
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

	private UltimatesB() {
	}
}
