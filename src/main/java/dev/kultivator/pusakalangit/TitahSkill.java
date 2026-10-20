package dev.kultivator.pusakalangit;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.kehancuran.BulkShape;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.kehancuran.Shapes;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.EndReason;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

import static dev.kultivator.technique.spec.dsl.Dsl.bind;
import static dev.kultivator.technique.spec.dsl.Dsl.dot;
import static dev.kultivator.technique.spec.dsl.Dsl.eff;
import static dev.kultivator.technique.spec.dsl.Dsl.ignite;

/**
 * {@code titah}, the special of the four Titah Langit decrees (DESIGN 29.3.1, 29.5): reads the cast's {@link Titah.Order}
 * (like {@code OwnArt} reads the creation) and builds the decree's storyboard with {@code ctx.schedule}. Every authored
 * tick t (Kecepatan 1) becomes round(t × f), where f puts the climax at clamp(round(climax / Kecepatan), 30, 300).
 * <p>
 * Damage goes only through the 6c choke point ({@code ctx.hurt}) to {@code Targeting.isHostileTo} foes; Kerusakan scales
 * the authored coefficient (0 = pure spectacle: no damage, no control); Kekuatan changes the size only. Per target and
 * cast at most 28 × P (Pedang 12 + 16, Tiang 18 + 10, Bintang 3 × 6 + 5 × 1.0, Gunung 20). Blocks change only through
 * {@code Kehancuran.submitTitah} (Tingkat 1) or {@code Kehancuran.submitTitahBulk} (Tingkat 2-9) at the climax and only
 * when the order is real (the caster's own switch on and "Hancurkan tanah" = Ikut); without it everything is visual. The
 * planted weapons are the saved {@link TitahMonumentEntity} (never a block).
 * <p>
 * Tingkat (DESIGN 29.16): s(t) = 1.6^(t − 1) scales the world (R, the damage AREA, the carve, the weapons; depths and
 * fall heights by sqrt(s)), timing never. Tingkat 1 is exactly the cast of before. Damage reaches loaded chunks only (the
 * entity sections), at most {@code titahMaxTargets} targets per area hit (bosses first, then nearest), 64 hits per tick
 * (the rest on the next ticks, still inside the cast).
 */
public final class TitahSkill implements SpecialTechnique {

	public static final String ID = "titah";
	/** The instance {@code TitahArts} registers. */
	public static final TitahSkill INSTANCE = new TitahSkill();

	// ---- authored numbers (29.5), coefficients × P
	public static final double PEDANG_NOVA = 12.0D;
	public static final double PEDANG_NOVA_RADIUS = 10.0D;
	public static final int PEDANG_STUN = 20;
	public static final double PEDANG_CLEAVE = 16.0D;
	public static final double CLEAVE_HALF_WIDTH = 1.5D;
	/** The cleaving front runs this many blocks per tick at Tingkat 1 (29.16.1: max(8, side / 40) above). */
	public static final double CLEAVE_SPEED = 8.0D;
	public static final double TIANG_CORE = 18.0D;
	public static final double TIANG_CORE_RADIUS = 6.0D;
	public static final double TIANG_FIRE_SECONDS = 6.0D;
	public static final double TIANG_WAVE = 10.0D;
	public static final double TIANG_KNOCK = 1.5D;
	public static final double BINTANG_STAR = 6.0D;
	public static final double BINTANG_STAR_RADIUS = 8.0D;
	/** One target takes at most this many stars per cast. */
	public static final int BINTANG_STAR_MAX = 3;
	public static final double BINTANG_DOT = 1.0D;
	public static final int BINTANG_DOT_SECONDS = 5;
	/** Half the DOT strip's width (w 2). */
	public static final double BINTANG_STRIP_HALF = 1.0D;
	public static final double GUNUNG_SLAM = 20.0D;
	public static final int GUNUNG_LEVITATION = 30;
	public static final int GUNUNG_WEAKNESS_TICKS = 100;

	/** Heights the falling weapons start from at Tingkat 1 (29.5). */
	public static final double PEDANG_FALL = 120.0D;
	public static final double TIANG_FALL = 160.0D;
	public static final double STAR_FALL = 80.0D;
	public static final double SEAL_FALL = 96.0D;

	// ---- Tingkat (DESIGN 29.16.1, 29.16.4)
	/** The tier scale's base: s(t) = 1.6^(t − 1). */
	public static final double TIER_BASE = 1.6D;
	/** Largest monument / falling weapon model scale and fall height. */
	public static final float MAX_MODEL_SCALE = 1100.0F;
	public static final double MAX_FALL = 1024.0D;
	/** Planted weapon length per model scale (the renderer's {@code SWORD_TIP} / {@code PILLAR_HALF}). */
	public static final double SWORD_LENGTH_PER_SCALE = 1.87D;
	public static final double PILLAR_LENGTH_PER_SCALE = 3.0D;
	/** Area hits per tick at most; the rest follows on the next ticks. */
	public static final int HITS_PER_TICK = 64;
	/** HIT events of one phase at most (the first targets only). */
	public static final int HIT_FX_PER_PHASE = 24;

	private static final String KEY = "kultivator.pusaka_langit.";
	private static final Map<CastContext, Run> RUNS = new IdentityHashMap<>();

	private TitahSkill() {
	}

	// ================================================================== sizes (29.3.2, 29.16.1)

	/** R_vis of {@code skill} at {@code kekuatan} and Tingkat 1: base × K clamped to [4, {@code titahVisualRadiusMax}]. */
	public static float visualRadius(Titah.Skill skill, float kekuatan) {
		float r = TitahArts.BASE_RADIUS[skill.ordinal()] * Math.max(0.0F, kekuatan);
		return Math.max(4.0F, Math.min(KultivatorConfig.get().titahVisualRadiusMax, r));
	}

	/** The real carve radius at Tingkat 1: min(0.25 × R_vis, {@code titahCarveRadiusMax}), at least 2. */
	public static int carveRadius(float visualRadius) {
		return Math.max(2, Math.min(KultivatorConfig.get().titahCarveRadiusMax, Math.round(0.25F * visualRadius)));
	}

	/** s(t) = 1.6^(t − 1) for t in 1-9 (1, 1.6, 2.56, 4.10, 6.55, 10.49, 16.78, 26.84, 42.95). */
	public static double tierScale(int tier) {
		return Math.pow(TIER_BASE, Math.max(1, Math.min(TitahSettings.T_MAX, tier)) - 1);
	}

	/** R(t) = min({@code titahMaxRadius}, R1 × s(t)), never below R1 (Tingkat 1 is exactly R_vis). */
	public static float visualRadius(Titah.Skill skill, float kekuatan, int tier) {
		float r1 = visualRadius(skill, kekuatan);
		if (tier <= 1) {
			return r1;
		}
		return (float) Math.max(r1, Math.min(KultivatorConfig.get().titahMaxRadius, r1 * tierScale(tier)));
	}

	/** dmgR(t) = max(1, min(R(t), {@code titahDamageRadiusMax} × s(t))): only the area grows. */
	public static double damageRadius(float rVis, int tier) {
		return Math.max(1.0D, Math.min(rVis, KultivatorConfig.get().titahDamageRadiusMax * tierScale(tier)));
	}

	/** carveR(t) = max(2, min(round(0.25 × R(t)), {@code titahCarveRadiusMax} × s(t))): the requested real carve radius. */
	public static int carveRadius(float rVis, int tier) {
		if (tier <= 1) {
			return carveRadius(rVis);
		}
		double cap = Math.floor(KultivatorConfig.get().titahCarveRadiusMax * tierScale(tier));
		return (int) Math.max(2L, Math.min(Math.round(0.25D * rVis), (long) cap));
	}

	/** The planted / falling weapon's model scale: today's clamp(12·√K, 6, 24) (sword) or clamp(10·√K, 5, 20) (pillar) × s(t), ≤ 1100. */
	public static float monumentScale(Titah.Skill skill, float kekuatan, int tier) {
		float k = (float) Math.sqrt(Math.max(0.0F, kekuatan));
		float base = skill == Titah.Skill.TIANG ? clamp(10.0F * k, 5.0F, 20.0F) : clamp(12.0F * k, 6.0F, 24.0F);
		return tier <= 1 ? base : (float) Math.min(MAX_MODEL_SCALE, base * tierScale(tier));
	}

	/** The planted weapon's length in blocks (Pedang ≈ 1.87 × scale, Tiang ≈ 3 × scale; 0 for Bintang and Gunung). */
	public static int weaponLength(Titah.Skill skill, float kekuatan, int tier) {
		return switch (skill) {
			case PEDANG -> (int) Math.round(SWORD_LENGTH_PER_SCALE * monumentScale(skill, kekuatan, tier));
			case TIANG -> (int) Math.round(PILLAR_LENGTH_PER_SCALE * monumentScale(skill, kekuatan, tier));
			default -> 0;
		};
	}

	/** fall(t) = min(1024, fall1 × sqrt(s(t))); exactly {@code base} at Tingkat 1. */
	public static double fallHeight(double base, int tier) {
		return tier <= 1 ? base : Math.min(MAX_FALL, base * Math.sqrt(tierScale(tier)));
	}

	// ================================================================== SpecialTechnique

	@Override
	public @Nullable Component blocker(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return Component.translatable(KEY + "refuse.seal_only");
		}
		Titah.Order order = Titah.pending(player);
		if (order == null || !order.skill().specId().equals(ctx.spec().id())) {
			// a decree is cast only through the seal (Titah.cast), never from a loadout or a raw cast action
			return Component.translatable(KEY + "refuse.seal_only");
		}
		return null;
	}

	@Override
	public boolean cast(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return false;
		}
		Titah.Order order = Titah.pending(player);
		if (order == null || !order.skill().specId().equals(ctx.spec().id())) {
			return false;
		}
		Run run = new Run(ctx, player, order);
		RUNS.put(ctx, run);
		Titah.markActive(player, true);
		try {
			ctx.markEnd(order.impact());
			switch (order.skill()) {
				case PEDANG -> pedang(run);
				case TIANG -> tiang(run);
				case BINTANG -> bintang(run);
				case GUNUNG -> gunung(run);
			}
		} catch (RuntimeException exception) {
			// the runner drops a cast whose handler threw without calling end(): clean up here
			RUNS.remove(ctx);
			Titah.markActive(player, false);
			throw exception;
		}
		return true;
	}

	@Override
	public void end(CastContext ctx, EndReason reason) {
		Run run = RUNS.remove(ctx);
		ServerPlayer player = ctx.player();
		if (player != null) {
			Titah.markActive(player, false);
		}
		if (run == null || player == null || run.climaxDone || reason == EndReason.DONE || run.order.practice()) {
			return;
		}
		// 29.3.1 abort before the climax: no strike, the qi stays spent, the cooldown is halved
		CultivationData data = Cultivators.get(player);
		long now = player.level().getGameTime();
		data.setCooldown(run.skill.specId(), now, Titah.cooldownTicks(run.skill, run.settings.kekuatan(), run.tier) / 2);
		Cultivators.dirty(player);
	}

	// ================================================================== the run of one cast

	/** The resolved numbers of one cast (sizes, timing, aim) and its helpers. */
	private static final class Run {
		final CastContext ctx;
		final ServerPlayer player;
		final ServerLevel level;
		final Titah.Order order;
		final Titah.Skill skill;
		final Titah.Settings settings;
		final KultivatorConfig config;
		/** Tingkat 1-9, s(t) and q(t) = sqrt(s(t)) (29.16.1). */
		final int tier;
		final double s;
		final double q;
		final float rVis;
		final double dmgR;
		final int carveR;
		final int climax;
		final double factor;
		final Vec3 impact;
		final Vec3 castPos;
		final Vec3 dir;
		final int seed;
		final boolean monument;
		final boolean real;
		final int aux;
		final double share;
		final int maxTargets;
		boolean climaxDone;
		/** HIT events sent per phase slot (the first {@link #HIT_FX_PER_PHASE} only). */
		final Map<Integer, Integer> hitFx = new HashMap<>();

		Run(CastContext ctx, ServerPlayer player, Titah.Order order) {
			this.ctx = ctx;
			this.player = player;
			this.level = ctx.level();
			this.order = order;
			this.skill = order.skill();
			this.settings = order.settings();
			this.config = KultivatorConfig.get();
			this.tier = order.tier();
			this.s = tierScale(tier);
			this.q = Math.sqrt(s);
			this.rVis = visualRadius(skill, settings.kekuatan(), tier);
			this.dmgR = damageRadius(rVis, tier);
			this.carveR = carveRadius(rVis, tier);
			int authored = TitahArts.CLIMAX[skill.ordinal()];
			float speed = Math.max(0.3F, Math.min(3.0F, settings.kecepatan()));
			this.climax = Math.max(30, Math.min(300, Math.round(authored / speed)));
			this.factor = climax / (double) authored;
			this.impact = order.impact();
			this.castPos = order.castPos();
			Vec3 flat = new Vec3(impact.x - castPos.x, 0.0D, impact.z - castPos.z);
			if (flat.lengthSqr() < 1.0E-4D) {
				Vec3 look = ctx.look();
				flat = new Vec3(look.x, 0.0D, look.z);
			}
			this.dir = flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
			long sd = ctx.seed();
			this.seed = (int) (sd ^ sd >>> 32);
			this.monument = skill.plantsWeapon() && settings.monumen();
			this.real = order.real();
			this.aux = TitahArts.packAux(speed, monument, real, skill.ordinal(), tier);
			this.share = Math.max(0.0D, Math.min(1.0D, settings.kerusakan()));
			this.maxTargets = Math.max(1, config.titahMaxTargets);
		}

		/** An authored tick at this cast's speed. */
		int t(int authored) {
			return Math.max(0, (int) Math.round(authored * factor));
		}

		/** Runs {@code action} at the authored tick (now for 0). */
		void at(int authored, Runnable action) {
			ctx.schedule(t(authored), action);
		}

		/** Ticks between two authored ticks at this speed (at least 1). */
		int span(int from, int to) {
			return Math.max(1, t(to) - t(from));
		}

		/** A fall height scaled by sqrt(s) (29.16.1), exactly {@code base} at Tingkat 1. */
		double fallOf(double base) {
			return fallHeight(base, tier);
		}

		void fx(Phase phase, Vec3 at, Vec3 to, float radius, float length, int count, int duration) {
			ctx.fx(phase, at, to, radius, length, count, duration, -1, 0, 1, aux, 0);
		}

		void fx(Phase phase, Vec3 at, Vec3 to, float radius, float length, int count, int duration, int index, int of, int flags) {
			ctx.fx(phase, at, to, radius, length, count, duration, -1, index, of, aux, flags);
		}

		/** The pre-impact TRAVEL of a falling weapon: from {@code height} above {@code to} down to it in {@code ticks}. */
		void fall(Vec3 to, double height, int ticks, int index, int of) {
			fx(Phase.TRAVEL, to.add(0.0D, height, 0.0D), to, rVis, (float) height, of, ticks, index, of, Payloads.TechFx.FLAG_PRE_IMPACT);
		}

		/** The decree's jade title card (7b EXTRA, the own client stamps it). */
		void card() {
			EpicHooks.extra(level, EpicIds.EXTRA_TITAH, player, player, impact, skill.ordinal(), EpicIds.TITAH_CARD);
		}

		/**
		 * Hostiles within {@code radius} of {@code centre} (never beyond the damage radius; loaded chunks only, the entity
		 * sections), cut to {@code titahMaxTargets}: bosses first, then nearest.
		 */
		List<LivingEntity> foes(Vec3 centre, double radius) {
			return capTargets(Targeting.hostilesAround(player, centre, Math.max(0.5D, Math.min(radius, dmgR)), false), maxTargets);
		}

		/**
		 * Runs {@code action(target, index)} for every target: {@link #HITS_PER_TICK} now, the rest on the following ticks
		 * (scheduled, so the cast stays alive until the last batch). A target gone meanwhile is skipped.
		 */
		void each(List<LivingEntity> targets, ObjIntConsumer<LivingEntity> action) {
			List<LivingEntity> list = List.copyOf(targets);
			int n = list.size();
			for (int start = 0; start < n; start += HITS_PER_TICK) {
				int from = start;
				int to = Math.min(n, start + HITS_PER_TICK);
				int delay = start / HITS_PER_TICK;
				Runnable batch = () -> {
					for (int i = from; i < to; i++) {
						LivingEntity target = list.get(i);
						if (delay > 0 && (!target.isAlive() || target.isRemoved() || target.level() != level)) {
							continue;
						}
						action.accept(target, i);
					}
				};
				if (delay == 0) {
					batch.run();
				} else {
					ctx.schedule(delay, batch);
				}
			}
		}

		/** {@code DMG coef} × Kerusakan through the choke point; a HIT event for the first targets of {@code slot}'s phase. */
		boolean hurt(LivingEntity target, double coef, int slot) {
			if (share <= 0.0D || !target.isAlive()) {
				return false;
			}
			boolean landed = ctx.hurt(target, coef * share, false);
			if (landed) {
				int sent = hitFx.getOrDefault(slot, 0);
				if (sent < HIT_FX_PER_PHASE) {
					hitFx.put(slot, sent + 1);
					ctx.fx(Phase.HIT, target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), impact, 1.0F, 0.0F, 1, 0, target.getId(), 0, 1,
							aux, 0);
				}
			}
			return landed;
		}

		/** A control or DOT payload (none at Kerusakan 0: pure spectacle). */
		void apply(LivingEntity target, Payload.Hit payload) {
			if (share > 0.0D && target.isAlive()) {
				ctx.apply(target, payload);
			}
		}

		/** The ground under {@code point} (the column's top, read-only), or the point itself when unloaded or far off. */
		Vec3 ground(Vec3 point) {
			BlockPos column = BlockPos.containing(point);
			if (!level.isLoaded(column)) {
				return point;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			return Math.abs(top - point.y) > 48.0D ? point : new Vec3(point.x, top, point.z);
		}

		/** The planted weapon (29.6.1), or -1 when none is planned or the caps refused it. */
		int plant(byte model, float scale, float tilt) {
			if (!monument) {
				return -1;
			}
			Vec3 at = ground(impact);
			float yaw = (float) Math.toDegrees(Math.atan2(-(castPos.x - at.x), castPos.z - at.z));
			return TitahMonuments.create(level, player, model, at, yaw, scale, tilt, order.practice(), false, tier);
		}

		/**
		 * Submits the decree's carve at the climax (only when real). Tingkat 1: the classic {@code submitTitah} with the
		 * record and the block monument, exactly as before. Tingkat 2-9: the bulk carve (29.16.6) of {@code bulk}, no block
		 * monument (the giant planted weapon is the monument); Tingkat 6-9 only as confirmed permanent carves.
		 */
		void carve(List<Shapes.Placed> shapes, int recordId, @Nullable MonumentPlan plan, Supplier<BulkShape> bulk) {
			if (!real) {
				return;
			}
			Dampak dampak = ctx.spec().dampak();
			Dampak.Palette palette = dampak == null ? Dampak.Palette.NONE : dampak.palette();
			int flags = dampak == null ? 0 : dampak.debrisFlags();
			if (tier >= 2) {
				BulkShape shape = bulk.get();
				if (shape == null || shape.isEmpty()) {
					return;
				}
				Kehancuran.submitTitahBulk(player, ctx.spec().id(), shape, new Kehancuran.TitahBulk(tier, order.permanent(), order.confirmed(), palette,
						flags, recordId, castPos));
				return;
			}
			if (shapes.isEmpty()) {
				return;
			}
			boolean blocks = plan != null && recordId >= 0 && config.titahMonumentBlocks > 0 && config.kehancuranUndoMinutes > 0;
			Kehancuran.TitahPlan titah = new Kehancuran.TitahPlan(config.titahBlocksPerCast, carveR, palette, flags, recordId,
					blocks ? plan.cells() : new long[0], blocks ? plan.kinds() : new byte[0]);
			Kehancuran.submitTitah(player, ctx.spec().id(), shapes, titah);
		}

		/** The impact block under {@code point} (read-only) for a bulk profile. */
		BlockPos impactBlock(Vec3 point) {
			BlockPos anchor = BlockPos.containing(point);
			return new BlockPos(anchor.getX(), BulkShape.impactY(level, anchor), anchor.getZ());
		}

		void climax() {
			climaxDone = true;
		}
	}

	/** {@code sorted} (nearest first) cut to {@code max}: bosses ({@code TAG_BOSS}) first, then the nearest others. */
	static List<LivingEntity> capTargets(List<LivingEntity> sorted, int max) {
		if (sorted.size() <= max) {
			return sorted;
		}
		List<LivingEntity> out = new ArrayList<>(max);
		for (LivingEntity entity : sorted) {
			if (out.size() >= max) {
				return out;
			}
			if (entity.entityTags().contains(TechniquePower.TAG_BOSS)) {
				out.add(entity);
			}
		}
		for (LivingEntity entity : sorted) {
			if (out.size() >= max) {
				break;
			}
			if (!entity.entityTags().contains(TechniquePower.TAG_BOSS)) {
				out.add(entity);
			}
		}
		return out;
	}

	// ================================================================== Pedang Pemisah Langit (29.5.1)

	private static void pedang(Run r) {
		float half = r.rVis;
		double fall = r.fallOf(PEDANG_FALL);
		r.at(0, () -> {
			r.fx(Phase.CAST, r.castPos, r.impact, r.rVis, half * 2.0F, 0, r.climax);
			r.card();
		});
		Vec3 sky = r.impact.add(0.0D, fall, 0.0D);
		r.at(20, () -> r.fx(Phase.EXTRA1, r.impact, sky, r.rVis, 0.0F, 0, r.span(20, 100)));
		r.at(60, () -> {
			r.fx(Phase.EXTRA2, sky, r.impact, r.rVis, 0.0F, 0, r.span(60, 100));
			r.fall(r.impact, fall, r.span(60, 100), 0, 1);
		});
		r.at(100, () -> {
			r.climax();
			List<LivingEntity> nova = r.foes(r.impact, PEDANG_NOVA_RADIUS * r.s);
			int[] hits = {0};
			r.each(nova, (foe, i) -> {
				if (r.hurt(foe, PEDANG_NOVA, 0)) {
					hits[0]++;
				}
				r.apply(foe, bind(Payload.BindKind.STUN, PEDANG_STUN));
			});
			r.fx(Phase.IMPACT, r.impact, r.castPos, r.rVis, half * 2.0F, nova.size() > HITS_PER_TICK ? nova.size() : hits[0], 0);
			float scale = monumentScale(Titah.Skill.PEDANG, r.settings.kekuatan(), r.tier);
			int record = r.plant(TitahMonumentEntity.MODEL_SWORD, scale, 10.0F);
			BlockPos anchor = BlockPos.containing(r.impact);
			List<Shapes.Placed> shapes = new ArrayList<>();
			if (r.tier <= 1) {
				shapes.add(new Shapes.Placed(Shapes.Spec.bowl(6), anchor, r.dir));
				int length = (int) Math.max(4, Math.min(half, 2.0F * r.config.titahCarveRadiusMax));
				int depth = Math.max(1, Math.min(12, (int) Math.ceil(length / 8.0D)));
				shapes.add(new Shapes.Placed(Shapes.Spec.fissure(length, 3, depth), anchor, r.dir));
				shapes.add(new Shapes.Placed(Shapes.Spec.fissure(length, 3, depth), anchor, r.dir.reverse()));
				shapes.add(new Shapes.Placed(Shapes.Spec.shave(r.carveR, 6), anchor, r.dir));
			}
			r.carve(shapes, record, r.tier <= 1 ? cradle(BlockPos.containing(r.ground(r.impact))) : null, () -> pedangBulk(r));
		});
		r.at(104, () -> {
			// 29.16.1: side = min(R, 2 × dmgR) at max(8, side / 40) blocks per tick, so the front always takes at most 40 ticks
			int side = (int) Math.ceil(Math.min(half, 2.0D * r.dmgR));
			double speed = Math.max(CLEAVE_SPEED, side / 40.0D);
			int steps = Math.max(1, (int) Math.ceil(side / speed));
			r.fx(Phase.EXTRA3, r.impact, r.impact.add(r.dir.scale(side)), r.rVis, side, 0, steps);
			Set<Integer> cleaved = new HashSet<>();
			for (int k = 0; k < steps; k++) {
				double from = k * speed;
				double to = Math.min(side, (k + 1) * speed);
				r.ctx.schedule(k, () -> cleave(r, from, to, cleaved));
			}
		});
		r.at(140, () -> r.fx(Phase.EXTRA4, r.impact, r.castPos, r.rVis * 1.4F, half * 2.0F, 0, r.span(140, 200)));
	}

	/** One tick of the cleaving front: the segment [from, to] on both sides of the impact, each foe hit once per cast. */
	private static void cleave(Run r, double from, double to, Set<Integer> cleaved) {
		double halfWidth = CLEAVE_HALF_WIDTH * r.q;
		List<LivingEntity> hit = new ArrayList<>();
		for (int sign = -1; sign <= 1; sign += 2) {
			Vec3 a = r.impact.add(r.dir.scale(sign * from));
			Vec3 b = r.impact.add(r.dir.scale(sign * to));
			AABB box = new AABB(a, b).inflate(halfWidth + 1.0D, 8.0D, halfWidth + 1.0D);
			for (LivingEntity foe : r.level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive() && r.ctx.mayHit(entity, false))) {
				if (Math.abs(foe.getY() - r.impact.y) > 8.0D || distanceXZ(foe.position(), a, b) > halfWidth + foe.getBbWidth() * 0.5D) {
					continue;
				}
				if (cleaved.size() < r.maxTargets && cleaved.add(foe.getId())) {
					hit.add(foe);
				}
			}
		}
		r.each(hit, (foe, i) -> r.hurt(foe, PEDANG_CLEAVE, 1));
	}

	/**
	 * Pedang at Tingkat 2-9 (29.16.7 table): Bowl r = 6s, depth 4q; two Fissures both ways, L = min(R, 2 ×
	 * {@code titahCarveRadiusMax} × s), width max(3, round(3q)), depth min(ceil(L / 8), round(12q)); Shave r = carveR, h = round(6q).
	 */
	private static BulkShape pedangBulk(Run r) {
		BlockPos impact = r.impactBlock(r.impact);
		double cx = impact.getX() + 0.5D;
		double cz = impact.getZ() + 0.5D;
		int iy = impact.getY();
		double length = Math.max(4.0D, Math.min(r.rVis, 2.0D * r.config.titahCarveRadiusMax * r.s));
		double width = Math.max(3.0D, Math.round(3.0D * r.q));
		double depth = Math.max(1.0D, Math.min(Math.ceil(length / 8.0D), Math.round(12.0D * r.q)));
		Vec3 start = new Vec3(cx, iy, cz);
		List<BulkShape.Profile> profiles = new ArrayList<>();
		profiles.add(new BulkShape.Bowl(cx, cz, iy, 6.0D * r.s, 4.0D * r.q));
		profiles.add(new BulkShape.Fissure(start, r.dir, length, width, depth));
		profiles.add(new BulkShape.Fissure(start, r.dir.reverse(), length, width, depth));
		profiles.add(new BulkShape.Shave(cx, cz, iy, r.carveR, (int) Math.max(1L, Math.round(6.0D * r.q))));
		return BulkShape.of(impact, r.dir, profiles);
	}

	// ================================================================== Tiang Penenang Samudra (29.5.2)

	private static void tiang(Run r) {
		double fall = r.fallOf(TIANG_FALL);
		Vec3 sky = r.impact.add(0.0D, fall, 0.0D);
		r.at(0, () -> {
			r.fx(Phase.CAST, r.castPos, r.impact, r.rVis, 0.0F, 0, r.climax);
			r.card();
		});
		r.at(10, () -> {
			Vec3 hand = r.player.getEyePosition();
			r.fx(Phase.EXTRA0, hand, hand.add(0.0D, fall, 0.0D), r.rVis, (float) fall, 0, r.span(10, 30));
		});
		r.at(30, () -> r.fx(Phase.EXTRA1, sky, r.impact, r.rVis, 0.0F, 0, r.span(30, 90)));
		r.at(90, () -> {
			r.fx(Phase.EXTRA2, sky, r.impact, r.rVis, 0.0F, 0, r.span(90, 120));
			r.fall(r.impact, fall, r.span(90, 120), 0, 1);
		});
		r.at(120, () -> {
			r.climax();
			List<LivingEntity> core = r.foes(r.impact, TIANG_CORE_RADIUS * r.s);
			int[] hits = {0};
			r.each(core, (foe, i) -> {
				if (r.hurt(foe, TIANG_CORE, 0)) {
					hits[0]++;
				}
				r.apply(foe, ignite(TIANG_FIRE_SECONDS));
			});
			r.fx(Phase.IMPACT, r.impact, r.castPos, r.rVis, 0.0F, core.size() > HITS_PER_TICK ? core.size() : hits[0], 0);
			float scale = monumentScale(Titah.Skill.TIANG, r.settings.kekuatan(), r.tier);
			int record = r.plant(TitahMonumentEntity.MODEL_PILLAR, scale, 0.0F);
			BlockPos anchor = BlockPos.containing(r.impact);
			List<Shapes.Placed> shapes = new ArrayList<>();
			if (r.tier <= 1) {
				int shave = Math.max(2, Math.min(Math.round(r.carveR * 1.5F), Math.round(r.config.titahCarveRadiusMax * 1.5F)));
				int strip = Math.max(2, Math.min(r.carveR * 2, 32));
				shapes.add(new Shapes.Placed(Shapes.Spec.bowl(r.carveR), anchor, r.dir));
				shapes.add(new Shapes.Placed(Shapes.Spec.shave(shave, 8), anchor, r.dir));
				shapes.add(new Shapes.Placed(Shapes.Spec.strip(strip), anchor, r.dir));
			}
			r.carve(shapes, record, r.tier <= 1 ? ring(BlockPos.containing(r.ground(r.impact))) : null, () -> tiangBulk(r));
		});
		r.at(128, () -> {
			r.fx(Phase.EXTRA3, r.impact, r.castPos, (float) r.dmgR, 0.0F, 0, r.span(128, 160));
			r.each(r.foes(r.impact, r.dmgR), (foe, i) -> {
				r.hurt(foe, TIANG_WAVE, 1);
				if (r.share > 0.0D && !(foe instanceof Player) && r.ctx.moveAllowed(foe)) {
					// the flattening wave throws foes outward from the impact (never a player)
					Vec3 out = new Vec3(foe.getX() - r.impact.x, 0.0D, foe.getZ() - r.impact.z);
					out = out.lengthSqr() < 1.0E-4D ? r.dir : out.normalize();
					foe.push(out.x * TIANG_KNOCK, 0.35D, out.z * TIANG_KNOCK);
				}
			});
		});
		r.at(160, () -> r.fx(Phase.EXTRA4, r.impact, r.castPos, r.rVis, 0.0F, 0, 1200));
	}

	/**
	 * Tiang at Tingkat 2-9 (29.16.7 table): Bowl r = carveR, depth = ceil(0.6 × carveR / s) × q; Shave r = min(1.5 carveR,
	 * 1.5 × {@code titahCarveRadiusMax} × s), h = round(8q); Strip r = min(2 carveR, 32 s).
	 */
	private static BulkShape tiangBulk(Run r) {
		BlockPos impact = r.impactBlock(r.impact);
		double cx = impact.getX() + 0.5D;
		double cz = impact.getZ() + 0.5D;
		int iy = impact.getY();
		double depth = Math.ceil(0.6D * r.carveR / r.s) * r.q;
		double shave = Math.max(2.0D, Math.min(1.5D * r.carveR, 1.5D * r.config.titahCarveRadiusMax * r.s));
		double strip = Math.max(2.0D, Math.min(2.0D * r.carveR, 32.0D * r.s));
		List<BulkShape.Profile> profiles = new ArrayList<>();
		profiles.add(new BulkShape.Bowl(cx, cz, iy, r.carveR, Math.max(1.0D, depth)));
		profiles.add(new BulkShape.Shave(cx, cz, iy, shave, (int) Math.max(1L, Math.round(8.0D * r.q))));
		profiles.add(new BulkShape.Strip(cx, cz, strip));
		return BulkShape.of(impact, r.dir, profiles);
	}

	// ================================================================== Tujuh Bintang Biduk Utara (29.5.3)

	private static void bintang(Run r) {
		double yaw = SigilPattern.yawFor(r.castPos, r.impact, r.seed);
		List<Vec3> raw = SigilPattern.BEIDOU_SEVEN.nodes(r.impact, yaw, r.rVis, r.seed);
		List<Vec3> stars = new ArrayList<>(raw.size());
		for (Vec3 node : raw) {
			stars.add(r.ground(node));
		}
		List<SigilPattern.Segment> lines = SigilPattern.BEIDOU_SEVEN.segments(r.impact, yaw, r.rVis, r.seed);
		int n = stars.size();
		double starFall = r.fallOf(STAR_FALL);
		double starRadius = BINTANG_STAR_RADIUS * r.s;
		r.at(0, () -> {
			r.fx(Phase.CAST, r.castPos, r.impact, r.rVis, 0.0F, n, r.climax);
			r.card();
		});
		for (int i = 0; i < n; i++) {
			int index = i;
			Vec3 star = stars.get(i);
			Vec3 previous = stars.get(Math.max(0, i - 1));
			r.at(20 + 6 * i, () -> r.fx(Phase.EXTRA1, star, previous, r.rVis, 0.0F, n, r.span(20 + 6 * index, 140), index, n, 0));
		}
		r.at(60, () -> r.fx(Phase.EXTRA2, r.impact.add(0.0D, Math.min(MAX_FALL, 120.0D * r.q), 0.0D), r.impact, r.rVis, 0.0F, n, r.span(60, 140)));
		Map<Integer, Integer> taken = new HashMap<>();
		for (int i = 0; i < n; i++) {
			int index = i;
			Vec3 star = stars.get(i);
			r.at(90 + 6 * i, () -> r.fall(star, starFall, r.span(90 + 6 * index, 100 + 6 * index), index, n));
			r.at(100 + 6 * i, () -> {
				List<LivingEntity> struck = new ArrayList<>();
				for (LivingEntity foe : r.foes(star, starRadius)) {
					int count = taken.getOrDefault(foe.getId(), 0);
					if (count >= BINTANG_STAR_MAX) {
						continue;
					}
					taken.put(foe.getId(), count + 1);
					struck.add(foe);
				}
				int[] hits = {0};
				r.each(struck, (foe, k) -> {
					if (r.hurt(foe, BINTANG_STAR, 8 + index)) {
						hits[0]++;
					}
				});
				r.fx(Phase.EXTRA0, star, r.impact, (float) Math.min(starRadius, r.dmgR), 0.0F, struck.size() > HITS_PER_TICK ? struck.size() : hits[0], 0,
						index, n, 0);
			});
		}
		r.at(140, () -> {
			r.climax();
			double strip = BINTANG_STRIP_HALF * r.q;
			List<LivingEntity> burning = new ArrayList<>();
			for (LivingEntity foe : r.foes(r.impact, r.dmgR)) {
				double reach = strip + foe.getBbWidth() * 0.5D;
				for (SigilPattern.Segment line : lines) {
					if (distanceXZ(foe.position(), line.from(), line.to()) <= reach) {
						burning.add(foe);
						break;
					}
				}
			}
			r.each(burning, (foe, k) -> r.apply(foe, dot(BINTANG_DOT * r.share, BINTANG_DOT_SECONDS, Payload.DotKind.SOUL)));
			r.fx(Phase.IMPACT, r.impact, stars.get(n - 1), r.rVis, 0.0F, burning.size(), BINTANG_DOT_SECONDS * 20);
			List<Shapes.Placed> shapes = new ArrayList<>();
			if (r.tier <= 1) {
				for (Vec3 star : stars) {
					shapes.add(new Shapes.Placed(Shapes.Spec.scatter(3), BlockPos.containing(star), r.dir));
				}
				for (SigilPattern.Segment line : lines) {
					Vec3 along = line.to().subtract(line.from());
					int length = Math.max(2, (int) Math.round(along.horizontalDistance()) + 1);
					shapes.add(new Shapes.Placed(Shapes.Spec.trench(length, 2, 2), BlockPos.containing(line.from()), along));
				}
			}
			r.carve(shapes, -1, null, () -> bintangBulk(r, stars, lines));
		});
	}

	/**
	 * Bintang at Tingkat 2-9 (29.16.7 table): 7 Bowls (the stars) r = 3s, depth 2q; the Dipper's Trenches width
	 * max(2, round(2q)), depth round(2q).
	 */
	private static BulkShape bintangBulk(Run r, List<Vec3> stars, List<SigilPattern.Segment> lines) {
		BlockPos impact = r.impactBlock(r.impact);
		List<BulkShape.Profile> profiles = new ArrayList<>();
		for (Vec3 star : stars) {
			BlockPos block = r.impactBlock(star);
			profiles.add(new BulkShape.Bowl(block.getX() + 0.5D, block.getZ() + 0.5D, block.getY(), 3.0D * r.s, Math.max(1.0D, 2.0D * r.q)));
		}
		double width = Math.max(2.0D, Math.round(2.0D * r.q));
		int depth = (int) Math.max(1L, Math.round(2.0D * r.q));
		for (SigilPattern.Segment line : lines) {
			Vec3 from = new Vec3(Math.floor(line.from().x) + 0.5D, line.from().y, Math.floor(line.from().z) + 0.5D);
			Vec3 to = new Vec3(Math.floor(line.to().x) + 0.5D, line.to().y, Math.floor(line.to().z) + 0.5D);
			profiles.add(new BulkShape.Trench(from, to, width, depth));
		}
		return BulkShape.of(impact, r.dir, profiles);
	}

	// ================================================================== Titah Pemindah Gunung (29.5.4)

	private static void gunung(Run r) {
		float half = r.rVis;
		// 29.16.1: about 8 cells per side at every Tingkat
		int cell = (int) Math.max(2L, Math.min(Math.round(16.0D * r.s), Math.round(2.0F * half / 8.0F)));
		int cells = Math.max(1, Math.round(2.0F * half / cell));
		double fall = r.fallOf(SEAL_FALL);
		Vec3 sky = r.impact.add(0.0D, fall, 0.0D);
		r.at(0, () -> {
			r.fx(Phase.CAST, r.castPos, r.impact, r.rVis, half, cells, r.climax);
			r.card();
		});
		r.at(30, () -> r.fx(Phase.EXTRA1, r.impact, r.impact, r.rVis, half, cells, r.span(30, 135)));
		r.at(60, () -> {
			r.fx(Phase.EXTRA2, r.impact, sky, r.rVis, half, cells, r.span(60, 90));
			r.fall(r.impact, fall, r.span(60, 90), 0, 1);
		});
		List<LivingEntity> lifted = new ArrayList<>();
		r.at(90, () -> {
			r.climax();
			double reach = Math.min(half, r.dmgR);
			AABB square = new AABB(r.impact.x - reach, r.impact.y - 16.0D, r.impact.z - reach, r.impact.x + reach, r.impact.y + 16.0D, r.impact.z + reach);
			if (r.share > 0.0D) {
				List<LivingEntity> found = r.level.getEntitiesOfClass(LivingEntity.class, square, entity -> entity.isAlive() && r.ctx.mayHit(entity, false));
				found.sort(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(r.impact)));
				lifted.addAll(capTargets(found, r.maxTargets));
				r.each(lifted, (foe, i) -> r.apply(foe, eff(Payload.Eff.LEVITATION, 1, GUNUNG_LEVITATION)));
			}
			r.fx(Phase.IMPACT, r.impact, r.castPos, r.rVis, half, lifted.size(), 0);
			BlockPos anchor = BlockPos.containing(r.impact);
			List<Shapes.Placed> shapes = new ArrayList<>();
			if (r.tier <= 1) {
				int pit = Math.max(2, Math.min(Math.round(half), r.carveR));
				int steps = Math.max(3, Math.min(5, 3 + pit / 12));
				shapes.add(new Shapes.Placed(Shapes.Spec.steppedPit(pit, steps, 2), anchor, r.dir));
			}
			r.carve(shapes, -1, null, () -> gunungBulk(r, half));
		});
		r.at(95, () -> r.fx(Phase.EXTRA3, r.impact, r.impact.add(0.0D, Math.min(MAX_FALL, 60.0D * r.q), 0.0D), r.rVis, half, cells, r.span(95, 135)));
		r.at(125, () -> {
			double reach = Math.min(half, r.dmgR) + 8.0D;
			int[] hits = {0};
			List<LivingEntity> slam = new ArrayList<>();
			for (LivingEntity foe : lifted) {
				if (!foe.isAlive() || foe.isRemoved() || foe.level() != r.level || Math.abs(foe.getX() - r.impact.x) > reach
						|| Math.abs(foe.getZ() - r.impact.z) > reach) {
					continue;
				}
				slam.add(foe);
			}
			r.each(slam, (foe, i) -> {
				if (r.hurt(foe, GUNUNG_SLAM, 0)) {
					hits[0]++;
				}
				r.apply(foe, eff(Payload.Eff.WEAKNESS, 2, GUNUNG_WEAKNESS_TICKS));
			});
			r.fx(Phase.EXTRA4, r.impact, r.castPos, r.rVis, half, slam.size() > HITS_PER_TICK ? slam.size() : hits[0], 0);
		});
	}

	/**
	 * Gunung at Tingkat 2-9 (29.16.7 table): SteppedPit half = min(H, carveR), steps = clamp(3 + half1 / 12, 3, 5)
	 * + (t ≥ 4 ? 2 : 0) + (t ≥ 7 ? 2 : 0) (≤ 9), terrace depth round(2q).
	 */
	private static BulkShape gunungBulk(Run r, float half) {
		BlockPos impact = r.impactBlock(r.impact);
		float r1 = visualRadius(r.skill, r.settings.kekuatan());
		int pit1 = Math.max(2, Math.min(Math.round(r1), carveRadius(r1)));
		int steps = Math.max(3, Math.min(5, 3 + pit1 / 12)) + (r.tier >= 4 ? 2 : 0) + (r.tier >= 7 ? 2 : 0);
		steps = Math.min(9, steps);
		double pitHalf = Math.max(2.0D, Math.min(half, r.carveR));
		int depth = (int) Math.max(1L, Math.round(2.0D * r.q));
		List<BulkShape.Profile> profiles = List.of(new BulkShape.SteppedPit(impact.getX() + 0.5D, impact.getZ() + 0.5D, impact.getY(), pitHalf, steps,
				depth));
		return BulkShape.of(impact, r.dir, profiles);
	}

	// ================================================================== block monument plans (29.6.3)

	/** Planned monument cells ({@code BlockPos.asLong(x, layer, z)}) and their kinds (0 Batu Pusaka, 1 Urat Pusaka). */
	record MonumentPlan(long[] cells, byte[] kinds) {
	}

	/**
	 * Penyangga Pedang: the 7×7 cradle around the planted column (the inner ring glowing veins, the outer two stone; 48
	 * blocks) plus 8 vein lines of 4 running out from it (32): 80. The planted column itself stays free.
	 */
	static MonumentPlan cradle(BlockPos column) {
		List<Long> cells = new ArrayList<>();
		List<Byte> kinds = new ArrayList<>();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int c = Math.max(Math.abs(dx), Math.abs(dz));
				if (c == 0) {
					continue;
				}
				cells.add(BlockPos.asLong(column.getX() + dx, 0, column.getZ() + dz));
				kinds.add(c == 1 ? (byte) 1 : (byte) 0);
			}
		}
		int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
		for (int[] way : ways) {
			for (int step = 4; step <= 7; step++) {
				cells.add(BlockPos.asLong(column.getX() + way[0] * step, 0, column.getZ() + way[1] * step));
				kinds.add((byte) 1);
			}
		}
		return pack(cells, kinds);
	}

	/**
	 * Alas Penenang: an octagonal ring of radius 3 around the planted column, 2 high, with 8 glowing veins in its lower
	 * course under the pillar's hoops.
	 */
	static MonumentPlan ring(BlockPos column) {
		List<Long> cells = new ArrayList<>();
		List<Byte> kinds = new ArrayList<>();
		for (int layer = 0; layer <= 1; layer++) {
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					int a = Math.abs(dx);
					int b = Math.abs(dz);
					double oct = Math.max(a, b) + 0.41421356D * Math.min(a, b);
					if (oct < 2.5D || oct >= 3.5D) {
						continue;
					}
					boolean vein = layer == 0 && (a == 3 && b == 0 || a == 0 && b == 3 || a == 2 && b == 2);
					cells.add(BlockPos.asLong(column.getX() + dx, layer, column.getZ() + dz));
					kinds.add(vein ? (byte) 1 : (byte) 0);
				}
			}
		}
		return pack(cells, kinds);
	}

	private static MonumentPlan pack(List<Long> cells, List<Byte> kinds) {
		long[] c = new long[cells.size()];
		byte[] k = new byte[kinds.size()];
		for (int i = 0; i < c.length; i++) {
			c[i] = cells.get(i);
			k[i] = kinds.get(i);
		}
		return new MonumentPlan(c, k);
	}

	// ================================================================== geometry

	/** The horizontal distance from {@code p} to the segment a-b. */
	static double distanceXZ(Vec3 p, Vec3 a, Vec3 b) {
		double abx = b.x - a.x;
		double abz = b.z - a.z;
		double len = abx * abx + abz * abz;
		double t = len < 1.0E-9D ? 0.0D : ((p.x - a.x) * abx + (p.z - a.z) * abz) / len;
		t = Math.max(0.0D, Math.min(1.0D, t));
		double dx = p.x - (a.x + abx * t);
		double dz = p.z - (a.z + abz * t);
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
