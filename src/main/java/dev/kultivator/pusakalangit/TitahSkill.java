package dev.kultivator.pusakalangit;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.kehancuran.Shapes;
import dev.kultivator.network.Payloads;
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
 * the authored coefficient (0 = pure spectacle: no damage, no control); Kekuatan changes the size only and every damage
 * evaluation stays inside {@code titahDamageRadiusMax}. Per target and cast at most 28 × P (Pedang 12 + 16, Tiang 18 + 10,
 * Bintang 3 × 6 + 5 × 1.0, Gunung 20). Blocks change only through {@code Kehancuran.submitTitah} at the climax and only
 * when the order is real (the caster's own switch on and "Hancurkan tanah" = Ikut); without it everything is visual.
 * The planted weapons are the saved {@link TitahMonumentEntity} (never a block).
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
	/** The cleaving front runs this many blocks per tick. */
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

	/** Heights the falling weapons start from (29.5). */
	private static final double PEDANG_FALL = 120.0D;
	private static final double TIANG_FALL = 160.0D;
	private static final double STAR_FALL = 80.0D;
	private static final double SEAL_FALL = 96.0D;

	private static final String KEY = "kultivator.pusaka_langit.";
	private static final Map<CastContext, Run> RUNS = new IdentityHashMap<>();

	private TitahSkill() {
	}

	// ================================================================== sizes (29.3.2)

	/** R_vis of {@code skill} at {@code kekuatan}: base × K clamped to [4, {@code titahVisualRadiusMax}]. */
	public static float visualRadius(Titah.Skill skill, float kekuatan) {
		float r = TitahArts.BASE_RADIUS[skill.ordinal()] * Math.max(0.0F, kekuatan);
		return Math.max(4.0F, Math.min(KultivatorConfig.get().titahVisualRadiusMax, r));
	}

	/** The real carve radius: min(0.25 × R_vis, {@code titahCarveRadiusMax}), at least 2. */
	public static int carveRadius(float visualRadius) {
		return Math.max(2, Math.min(KultivatorConfig.get().titahCarveRadiusMax, Math.round(0.25F * visualRadius)));
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
		data.setCooldown(run.skill.specId(), now, Titah.cooldownTicks(run.skill, run.settings.kekuatan()) / 2);
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
		boolean climaxDone;

		Run(CastContext ctx, ServerPlayer player, Titah.Order order) {
			this.ctx = ctx;
			this.player = player;
			this.level = ctx.level();
			this.order = order;
			this.skill = order.skill();
			this.settings = order.settings();
			this.config = KultivatorConfig.get();
			this.rVis = visualRadius(skill, settings.kekuatan());
			this.dmgR = Math.max(1.0D, Math.min(rVis, config.titahDamageRadiusMax));
			this.carveR = carveRadius(rVis);
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
			long s = ctx.seed();
			this.seed = (int) (s ^ s >>> 32);
			this.monument = skill.plantsWeapon() && settings.monumen();
			this.real = order.real();
			this.aux = TitahArts.packAux(speed, monument, real, skill.ordinal());
			this.share = Math.max(0.0D, Math.min(1.0D, settings.kerusakan()));
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

		/** Hostiles within {@code radius} of {@code centre} (never beyond the damage radius cap). */
		List<LivingEntity> foes(Vec3 centre, double radius) {
			return Targeting.hostilesAround(player, centre, Math.max(0.5D, Math.min(radius, dmgR)), false);
		}

		/** {@code DMG coef} × Kerusakan through the choke point; a HIT event where it landed. */
		boolean hurt(LivingEntity target, double coef) {
			if (share <= 0.0D || !target.isAlive()) {
				return false;
			}
			boolean landed = ctx.hurt(target, coef * share, false);
			if (landed) {
				ctx.fx(Phase.HIT, target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), impact, 1.0F, 0.0F, 1, 0, target.getId(), 0, 1, aux, 0);
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
			return TitahMonuments.create(level, player, model, at, yaw, scale, tilt, order.practice(), false);
		}

		/** Submits the decree's carve at the climax (only when real); the plan carries the record and the block monument. */
		void carve(List<Shapes.Placed> shapes, int recordId, @Nullable MonumentPlan plan) {
			if (!real || shapes.isEmpty()) {
				return;
			}
			Dampak dampak = ctx.spec().dampak();
			Dampak.Palette palette = dampak == null ? Dampak.Palette.NONE : dampak.palette();
			int flags = dampak == null ? 0 : dampak.debrisFlags();
			boolean blocks = plan != null && recordId >= 0 && config.titahMonumentBlocks > 0 && config.kehancuranUndoMinutes > 0;
			Kehancuran.TitahPlan titah = new Kehancuran.TitahPlan(config.titahBlocksPerCast, carveR, palette, flags, recordId,
					blocks ? plan.cells() : new long[0], blocks ? plan.kinds() : new byte[0]);
			Kehancuran.submitTitah(player, ctx.spec().id(), shapes, titah);
		}

		void climax() {
			climaxDone = true;
		}
	}

	// ================================================================== Pedang Pemisah Langit (29.5.1)

	private static void pedang(Run r) {
		float half = r.rVis;
		r.at(0, () -> {
			r.fx(Phase.CAST, r.castPos, r.impact, r.rVis, half * 2.0F, 0, r.climax);
			r.card();
		});
		Vec3 sky = r.impact.add(0.0D, PEDANG_FALL, 0.0D);
		r.at(20, () -> r.fx(Phase.EXTRA1, r.impact, sky, r.rVis, 0.0F, 0, r.span(20, 100)));
		r.at(60, () -> {
			r.fx(Phase.EXTRA2, sky, r.impact, r.rVis, 0.0F, 0, r.span(60, 100));
			r.fall(r.impact, PEDANG_FALL, r.span(60, 100), 0, 1);
		});
		r.at(100, () -> {
			r.climax();
			int hits = 0;
			for (LivingEntity foe : r.foes(r.impact, PEDANG_NOVA_RADIUS)) {
				if (r.hurt(foe, PEDANG_NOVA)) {
					hits++;
				}
				r.apply(foe, bind(Payload.BindKind.STUN, PEDANG_STUN));
			}
			r.fx(Phase.IMPACT, r.impact, r.castPos, r.rVis, half * 2.0F, hits, 0);
			float scale = clamp(12.0F * (float) Math.sqrt(r.settings.kekuatan()), 6.0F, 24.0F);
			int record = r.plant(TitahMonumentEntity.MODEL_SWORD, scale, 10.0F);
			BlockPos anchor = BlockPos.containing(r.impact);
			List<Shapes.Placed> shapes = new ArrayList<>();
			shapes.add(new Shapes.Placed(Shapes.Spec.bowl(6), anchor, r.dir));
			int length = (int) Math.max(4, Math.min(half, 2.0F * r.config.titahCarveRadiusMax));
			int depth = Math.max(1, Math.min(12, (int) Math.ceil(length / 8.0D)));
			shapes.add(new Shapes.Placed(Shapes.Spec.fissure(length, 3, depth), anchor, r.dir));
			shapes.add(new Shapes.Placed(Shapes.Spec.fissure(length, 3, depth), anchor, r.dir.reverse()));
			shapes.add(new Shapes.Placed(Shapes.Spec.shave(r.carveR, 6), anchor, r.dir));
			r.carve(shapes, record, cradle(BlockPos.containing(r.ground(r.impact))));
		});
		r.at(104, () -> {
			int side = (int) Math.ceil(Math.min(half, 2.0D * r.config.titahDamageRadiusMax));
			int steps = Math.max(1, (int) Math.ceil(side / CLEAVE_SPEED));
			r.fx(Phase.EXTRA3, r.impact, r.impact.add(r.dir.scale(side)), r.rVis, side, 0, steps);
			Set<Integer> cleaved = new HashSet<>();
			for (int k = 0; k < steps; k++) {
				double from = k * CLEAVE_SPEED;
				double to = Math.min(side, (k + 1) * CLEAVE_SPEED);
				r.ctx.schedule(k, () -> cleave(r, from, to, cleaved));
			}
		});
		r.at(140, () -> r.fx(Phase.EXTRA4, r.impact, r.castPos, r.rVis * 1.4F, half * 2.0F, 0, r.span(140, 200)));
	}

	/** One tick of the cleaving front: the segment [from, to] on both sides of the impact, each foe hit once per cast. */
	private static void cleave(Run r, double from, double to, Set<Integer> cleaved) {
		for (int sign = -1; sign <= 1; sign += 2) {
			Vec3 a = r.impact.add(r.dir.scale(sign * from));
			Vec3 b = r.impact.add(r.dir.scale(sign * to));
			AABB box = new AABB(a, b).inflate(CLEAVE_HALF_WIDTH + 1.0D, 8.0D, CLEAVE_HALF_WIDTH + 1.0D);
			for (LivingEntity foe : r.level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive() && r.ctx.mayHit(entity, false))) {
				if (Math.abs(foe.getY() - r.impact.y) > 8.0D || distanceXZ(foe.position(), a, b) > CLEAVE_HALF_WIDTH + foe.getBbWidth() * 0.5D) {
					continue;
				}
				if (cleaved.add(foe.getId())) {
					r.hurt(foe, PEDANG_CLEAVE);
				}
			}
		}
	}

	// ================================================================== Tiang Penenang Samudra (29.5.2)

	private static void tiang(Run r) {
		Vec3 sky = r.impact.add(0.0D, TIANG_FALL, 0.0D);
		r.at(0, () -> {
			r.fx(Phase.CAST, r.castPos, r.impact, r.rVis, 0.0F, 0, r.climax);
			r.card();
		});
		r.at(10, () -> {
			Vec3 hand = r.player.getEyePosition();
			r.fx(Phase.EXTRA0, hand, hand.add(0.0D, TIANG_FALL, 0.0D), r.rVis, (float) TIANG_FALL, 0, r.span(10, 30));
		});
		r.at(30, () -> r.fx(Phase.EXTRA1, sky, r.impact, r.rVis, 0.0F, 0, r.span(30, 90)));
		r.at(90, () -> {
			r.fx(Phase.EXTRA2, sky, r.impact, r.rVis, 0.0F, 0, r.span(90, 120));
			r.fall(r.impact, TIANG_FALL, r.span(90, 120), 0, 1);
		});
		Set<Integer> core = new HashSet<>();
		r.at(120, () -> {
			r.climax();
			int hits = 0;
			for (LivingEntity foe : r.foes(r.impact, TIANG_CORE_RADIUS)) {
				if (r.hurt(foe, TIANG_CORE)) {
					hits++;
				}
				r.apply(foe, ignite(TIANG_FIRE_SECONDS));
				core.add(foe.getId());
			}
			r.fx(Phase.IMPACT, r.impact, r.castPos, r.rVis, 0.0F, hits, 0);
			float scale = clamp(10.0F * (float) Math.sqrt(r.settings.kekuatan()), 5.0F, 20.0F);
			int record = r.plant(TitahMonumentEntity.MODEL_PILLAR, scale, 0.0F);
			BlockPos anchor = BlockPos.containing(r.impact);
			int shave = Math.max(2, Math.min(Math.round(r.carveR * 1.5F), Math.round(r.config.titahCarveRadiusMax * 1.5F)));
			int strip = Math.max(2, Math.min(r.carveR * 2, 32));
			List<Shapes.Placed> shapes = new ArrayList<>();
			shapes.add(new Shapes.Placed(Shapes.Spec.bowl(r.carveR), anchor, r.dir));
			shapes.add(new Shapes.Placed(Shapes.Spec.shave(shave, 8), anchor, r.dir));
			shapes.add(new Shapes.Placed(Shapes.Spec.strip(strip), anchor, r.dir));
			r.carve(shapes, record, ring(BlockPos.containing(r.ground(r.impact))));
		});
		r.at(128, () -> {
			r.fx(Phase.EXTRA3, r.impact, r.castPos, (float) r.dmgR, 0.0F, 0, r.span(128, 160));
			for (LivingEntity foe : r.foes(r.impact, r.dmgR)) {
				r.hurt(foe, TIANG_WAVE);
				if (r.share > 0.0D && !(foe instanceof Player) && r.ctx.moveAllowed(foe)) {
					// the flattening wave throws foes outward from the impact (never a player)
					Vec3 out = new Vec3(foe.getX() - r.impact.x, 0.0D, foe.getZ() - r.impact.z);
					out = out.lengthSqr() < 1.0E-4D ? r.dir : out.normalize();
					foe.push(out.x * TIANG_KNOCK, 0.35D, out.z * TIANG_KNOCK);
				}
			}
		});
		r.at(160, () -> r.fx(Phase.EXTRA4, r.impact, r.castPos, r.rVis, 0.0F, 0, 1200));
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
		r.at(60, () -> r.fx(Phase.EXTRA2, r.impact.add(0.0D, 120.0D, 0.0D), r.impact, r.rVis, 0.0F, n, r.span(60, 140)));
		Map<Integer, Integer> taken = new HashMap<>();
		for (int i = 0; i < n; i++) {
			int index = i;
			Vec3 star = stars.get(i);
			r.at(90 + 6 * i, () -> r.fall(star, STAR_FALL, r.span(90 + 6 * index, 100 + 6 * index), index, n));
			r.at(100 + 6 * i, () -> {
				int hits = 0;
				for (LivingEntity foe : r.foes(star, BINTANG_STAR_RADIUS)) {
					int count = taken.getOrDefault(foe.getId(), 0);
					if (count >= BINTANG_STAR_MAX) {
						continue;
					}
					taken.put(foe.getId(), count + 1);
					if (r.hurt(foe, BINTANG_STAR)) {
						hits++;
					}
				}
				r.fx(Phase.EXTRA0, star, r.impact, (float) Math.min(BINTANG_STAR_RADIUS, r.dmgR), 0.0F, hits, 0, index, n, 0);
			});
		}
		r.at(140, () -> {
			r.climax();
			int burned = 0;
			for (LivingEntity foe : r.foes(r.impact, r.dmgR)) {
				double reach = BINTANG_STRIP_HALF + foe.getBbWidth() * 0.5D;
				for (SigilPattern.Segment line : lines) {
					if (distanceXZ(foe.position(), line.from(), line.to()) <= reach) {
						r.apply(foe, dot(BINTANG_DOT * r.share, BINTANG_DOT_SECONDS, Payload.DotKind.SOUL));
						burned++;
						break;
					}
				}
			}
			r.fx(Phase.IMPACT, r.impact, stars.get(n - 1), r.rVis, 0.0F, burned, BINTANG_DOT_SECONDS * 20);
			List<Shapes.Placed> shapes = new ArrayList<>();
			for (Vec3 star : stars) {
				shapes.add(new Shapes.Placed(Shapes.Spec.scatter(3), BlockPos.containing(star), r.dir));
			}
			for (SigilPattern.Segment line : lines) {
				Vec3 along = line.to().subtract(line.from());
				int length = Math.max(2, (int) Math.round(along.horizontalDistance()) + 1);
				shapes.add(new Shapes.Placed(Shapes.Spec.trench(length, 2, 2), BlockPos.containing(line.from()), along));
			}
			r.carve(shapes, -1, null);
		});
	}

	// ================================================================== Titah Pemindah Gunung (29.5.4)

	private static void gunung(Run r) {
		float half = r.rVis;
		int cell = Math.max(2, Math.min(16, Math.round(2.0F * half / 8.0F)));
		int cells = Math.max(1, Math.round(2.0F * half / cell));
		Vec3 sky = r.impact.add(0.0D, SEAL_FALL, 0.0D);
		r.at(0, () -> {
			r.fx(Phase.CAST, r.castPos, r.impact, r.rVis, half, cells, r.climax);
			r.card();
		});
		r.at(30, () -> r.fx(Phase.EXTRA1, r.impact, r.impact, r.rVis, half, cells, r.span(30, 135)));
		r.at(60, () -> {
			r.fx(Phase.EXTRA2, r.impact, sky, r.rVis, half, cells, r.span(60, 90));
			r.fall(r.impact, SEAL_FALL, r.span(60, 90), 0, 1);
		});
		List<LivingEntity> lifted = new ArrayList<>();
		r.at(90, () -> {
			r.climax();
			double reach = Math.min(half, r.dmgR);
			AABB square = new AABB(r.impact.x - reach, r.impact.y - 16.0D, r.impact.z - reach, r.impact.x + reach, r.impact.y + 16.0D, r.impact.z + reach);
			for (LivingEntity foe : r.level.getEntitiesOfClass(LivingEntity.class, square, entity -> entity.isAlive() && r.ctx.mayHit(entity, false))) {
				if (r.share <= 0.0D) {
					break;
				}
				r.apply(foe, eff(Payload.Eff.LEVITATION, 1, GUNUNG_LEVITATION));
				lifted.add(foe);
			}
			r.fx(Phase.IMPACT, r.impact, r.castPos, r.rVis, half, lifted.size(), 0);
			BlockPos anchor = BlockPos.containing(r.impact);
			int pit = Math.max(2, Math.min(Math.round(half), r.carveR));
			int steps = Math.max(3, Math.min(5, 3 + pit / 12));
			List<Shapes.Placed> shapes = new ArrayList<>();
			shapes.add(new Shapes.Placed(Shapes.Spec.steppedPit(pit, steps, 2), anchor, r.dir));
			r.carve(shapes, -1, null);
		});
		r.at(95, () -> r.fx(Phase.EXTRA3, r.impact, r.impact.add(0.0D, 60.0D, 0.0D), r.rVis, half, cells, r.span(95, 135)));
		r.at(125, () -> {
			double reach = Math.min(half, r.dmgR) + 8.0D;
			int hits = 0;
			for (LivingEntity foe : lifted) {
				if (!foe.isAlive() || foe.isRemoved() || foe.level() != r.level || Math.abs(foe.getX() - r.impact.x) > reach
						|| Math.abs(foe.getZ() - r.impact.z) > reach) {
					continue;
				}
				if (r.hurt(foe, GUNUNG_SLAM)) {
					hits++;
				}
				r.apply(foe, eff(Payload.Eff.WEAKNESS, 2, GUNUNG_WEAKNESS_TICKS));
			}
			r.fx(Phase.EXTRA4, r.impact, r.castPos, r.rVis, half, hits, 0);
		});
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
