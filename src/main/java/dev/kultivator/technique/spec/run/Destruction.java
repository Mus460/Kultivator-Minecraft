package dev.kultivator.technique.spec.run;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Cultivators;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.kehancuran.Shapes;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.DShape;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The runner side of Mode Kehancuran for the player's own casts (DESIGN 22.11.3, 22.11.4): scales each KH shape of the
 * spec by K × mastery area, clamps it, resolves its anchors (the first impact, chain hops, impacts for SCATTER, the beam's
 * first block, the dash path, field corners and edges, release) and hands it to {@code Kehancuran.submitSelf}, which is
 * the only entry to block removal. Shapes resolved in the same server tick go together in one submit (one job, one undo
 * entry, 22.11.5). The cast's block cap is split in proportion to each shape's uncut size. Nothing happens unless the
 * cast's Kehancuran flag was set at the snapshot, and never during the target precheck (a dry run).
 */
final class Destruction {

	private static final int MAX_POINTS = TechniquePower.SCATTER_POINTS;
	/** Casts with shapes waiting for the end-of-tick submit. */
	private static final List<Destruction> PENDING = new ArrayList<>();

	private final Caster cast;
	private boolean ready;
	private boolean enabled;
	private double k;
	private double areaFactor;
	private int maxRadius;
	private int[] caps = new int[0];
	private boolean[] submitted = new boolean[0];
	private final List<List<Vec3>> points = new ArrayList<>();
	private final List<List<Vec3>> directions = new ArrayList<>();
	private @Nullable Vec3 beamStart;
	private @Nullable Vec3 pathFrom;
	private @Nullable Vec3 pathTo;
	private @Nullable Fields.Area field;
	/** Shapes resolved this tick, submitted together by {@link #flushAll}. */
	private final List<Shapes.Placed> batch = new ArrayList<>();
	private int batchCap;
	/** Phase 10 (DESIGN 28.9.3): the fixed 15 % of the cast's block cap kept for the sigil trenches, and whether they went. */
	private int trenchCap;
	private boolean sigilDone;
	/** The sigil trenches waiting for this tick's submit (after the climax's own shapes, so the job centre stays theirs). */
	private final List<Shapes.Placed> trenches = new ArrayList<>();
	/** The share of the block cap the sigil trenches take before the existing split. */
	static final double TRENCH_SHARE = 0.15D;

	Destruction(Caster cast) {
		this.cast = cast;
	}

	private boolean on() {
		if (cast.precheck) {
			// the precheck evaluates shapes before anything is spent: never carve, and never cache the switch here
			return false;
		}
		if (!ready) {
			ready = true;
			List<DShape> shapes = cast.spec.destruction();
			enabled = cast.kehancuran && cast.caster instanceof ServerPlayer && !shapes.isEmpty();
			if (enabled) {
				ServerPlayer player = (ServerPlayer) cast.caster;
				k = TechniquePower.kScale(Cultivators.get(player));
				areaFactor = TechniquePower.masteryArea(cast.mastery);
				maxRadius = KultivatorConfig.get().kehancuranMaxRadius;
				int total = TechniquePower.blockCap(Cultivators.get(player));
				int cap = total;
				Dampak dampak = cast.spec.dampak();
				if (dampak != null && dampak.sigilTrench()) {
					// the sigil trenches' fixed share comes off the top: the cast's total never exceeds blockCap
					trenchCap = (int) Math.floor(total * TRENCH_SHARE);
					total = Math.max(1, total - trenchCap);
				}
				int n = Math.min(TechniquePower.MAX_DESTRUCTION_SHAPES, shapes.size());
				caps = new int[n];
				submitted = new boolean[n];
				double sum = 0.0D;
				double[] sizes = new double[n];
				for (int i = 0; i < n; i++) {
					sizes[i] = Math.max(1.0D, uncut(shapes.get(i)));
					sum += sizes[i];
				}
				int stageCount = cast.spec.stages() == null ? 1 : Math.max(1, cast.spec.stages().count() - 1);
				for (int i = 0; i < n; i++) {
					int share = Math.max(1, (int) Math.floor(total * sizes[i] / sum));
					// a shape that carves at every stage shares its part of the one cap over the stages
					caps[i] = shapes.get(i).perStage() ? Math.max(1, share / stageCount) : share;
					points.add(new ArrayList<>());
					directions.add(new ArrayList<>());
				}
				enabled = k > 0.0D && cap > 0;
			}
		}
		return enabled;
	}

	/** A rough uncut volume at the cast's K (for the cap split). */
	private double uncut(DShape shape) {
		double a = dim(shape.a());
		double b = dim(shape.b());
		double c = dim(shape.c());
		int copies = shape.kind() == DShape.Kind.SCATTER ? MAX_POINTS : Math.max(1, shape.copies());
		double volume = switch (shape.kind()) {
			case BOWL, SCATTER, SPHERE -> 2.1D * a * a * a;
			case RING -> Math.PI * (a * a - b * b) * Math.max(1.0D, c);
			case TUNNEL -> Math.PI * a * a * b;
			case FISSURE, TRENCH -> a * Math.max(1.0D, b) * Math.max(1.0D, c);
			case ARC -> Math.toRadians(shape.angle()) * a * Math.max(1.0D, b) * Math.max(1.0D, c);
			case FAN -> Math.toRadians(shape.angle()) * a * a * Math.max(1.0D, c);
			case DISC, COLUMN -> Math.PI * a * a * Math.max(1.0D, c);
			case PIT -> (2 * a + 1) * (2 * a + 1) * Math.max(1.0D, c);
			case NINE -> 9 * 4.2D * a * a * a;
			case STRIP, WITHER -> Math.PI * a * a;
			case STRIP_FAN -> Math.toRadians(shape.angle()) * a * a;
		};
		return volume * copies;
	}

	private double dim(double base) {
		return Math.max(0.0D, base * k * areaFactor);
	}

	// ================================================================== events from the runner

	/** A damaging impact landed at {@code at} (first impact for default shapes; SCATTER and ×n points). */
	void impact(Vec3 at, Vec3 direction, boolean pointOnly) {
		impactPoints(List.of(at), direction, !pointOnly);
	}

	/**
	 * One landed evaluation: every point feeds the collecting shapes (SCATTER, ×n, one per crescent); on the cast's first
	 * impact (or at each stage for {@code eachStage} shapes) the default IMPACT shapes go at the first point.
	 */
	void impactPoints(List<Vec3> at, Vec3 direction, boolean firstImpact) {
		if (at.isEmpty() || !on()) {
			return;
		}
		List<DShape> shapes = cast.spec.destruction();
		for (int i = 0; i < caps.length; i++) {
			DShape shape = shapes.get(i);
			if (submitted[i] || shape.stage() > 0 && shape.stage() != cast.stage) {
				continue;
			}
			boolean collects = shape.kind() == DShape.Kind.SCATTER || shape.copies() > 1 || shape.perEach();
			if (collects) {
				// SCATTER, ×n and one-per-crescent copies collect their points; they go together (one shared cap) when the
				// count is reached or the cast ends.
				int limit = shape.kind() == DShape.Kind.SCATTER || shape.perEach() ? MAX_POINTS : Math.max(1, shape.copies());
				for (Vec3 point : at) {
					if (near(points.get(i), point)) {
						// the same impact reported twice (a rain impact and its hit, a chain hop and its delivery)
						continue;
					}
					if (points.get(i).size() < MAX_POINTS) {
						points.get(i).add(point);
						directions.get(i).add(direction);
					}
					if (points.get(i).size() >= limit) {
						flush(i);
						break;
					}
				}
			} else if ((firstImpact || shape.perStage()) && shape.when() == DShape.When.IMPACT && shape.along() == DShape.Along.DEFAULT) {
				submitAt(i, List.of(at.get(0)), List.of(direction), true);
			}
		}
	}

	private static boolean near(List<Vec3> list, Vec3 point) {
		for (Vec3 each : list) {
			if (each.distanceToSqr(point) < 4.0D) {
				return true;
			}
		}
		return false;
	}

	/** A STAGES recast advanced: shapes that carve at every stage may go again. */
	void stageAdvanced() {
		if (!ready || !enabled) {
			return;
		}
		List<DShape> shapes = cast.spec.destruction();
		for (int i = 0; i < caps.length; i++) {
			if (shapes.get(i).perStage()) {
				submitted[i] = false;
			}
		}
	}

	/** A beam stopped at its first block (TUNNEL and "along the beam"). */
	void beam(Vec3 from, Vec3 direction, Vec3 firstBlock) {
		if (!on()) {
			return;
		}
		beamStart = firstBlock;
		List<DShape> shapes = cast.spec.destruction();
		for (int i = 0; i < caps.length; i++) {
			DShape shape = shapes.get(i);
			if (!submitted[i] && (shape.kind() == DShape.Kind.TUNNEL || shape.along() == DShape.Along.BEAM)
					&& shape.when() == DShape.When.IMPACT && (shape.stage() == 0 || shape.stage() == cast.stage)) {
				Vec3 start = shape.kind() == DShape.Kind.TUNNEL ? firstBlock : from;
				submitAt(i, List.of(start), List.of(direction), true);
			}
		}
	}

	/** A dash or blink travelled (TRENCH path). */
	void path(Vec3 from, Vec3 to) {
		if (!on()) {
			return;
		}
		pathFrom = from;
		pathTo = to;
		List<DShape> shapes = cast.spec.destruction();
		for (int i = 0; i < caps.length; i++) {
			DShape shape = shapes.get(i);
			if (!submitted[i] && shape.along() == DShape.Along.PATH && shape.when() == DShape.When.IMPACT) {
				submitAt(i, List.of(from), List.of(to.subtract(from)), true);
			}
		}
	}

	/** A FIELD was placed (corners and edges). */
	void field(Fields.Area area) {
		if (!on()) {
			return;
		}
		field = area;
		List<DShape> shapes = cast.spec.destruction();
		for (int i = 0; i < caps.length; i++) {
			DShape shape = shapes.get(i);
			if (submitted[i] || shape.when() != DShape.When.IMPACT) {
				continue;
			}
			if (shape.along() == DShape.Along.EDGES) {
				submitEdges(i, area);
			} else if (shape.copies() > 1 && shape.kind() != DShape.Kind.SCATTER) {
				List<Vec3> corners = area.corners();
				submitAt(i, corners.subList(0, Math.min(corners.size(), shape.copies())), List.of(cast.look), true);
			}
		}
	}

	/** True when a TICK shape waits for step tick {@code tick} (the anchor is only resolved then). */
	boolean wantsTick(int tick) {
		if (!on()) {
			return false;
		}
		List<DShape> shapes = cast.spec.destruction();
		for (int i = 0; i < caps.length; i++) {
			DShape shape = shapes.get(i);
			if (!submitted[i] && shape.when() == DShape.When.TICK && shape.atTick() == tick) {
				return true;
			}
		}
		return false;
	}

	/** A step at tick {@code tick} ran with its anchor at {@code at}. */
	void tick(int tick, Vec3 at) {
		if (!on()) {
			return;
		}
		List<DShape> shapes = cast.spec.destruction();
		for (int i = 0; i < caps.length; i++) {
			DShape shape = shapes.get(i);
			if (!submitted[i] && shape.when() == DShape.When.TICK && shape.atTick() == tick) {
				submitAt(i, List.of(at), List.of(cast.look), true);
			}
		}
	}

	/** {@code destroy(anchor, direction)} of a special: every shape not submitted yet, at that anchor. */
	boolean manual(Vec3 at, Vec3 direction) {
		if (!on()) {
			return false;
		}
		boolean any = false;
		for (int i = 0; i < caps.length; i++) {
			if (!submitted[i]) {
				any |= submitAt(i, List.of(at), List.of(direction), true);
			}
		}
		return any;
	}

	/**
	 * The climax of a Dampak spec with {@code sigilTrench} (DESIGN 28.9.3): the pattern's major lines at the radius the
	 * clients draw, those within twice {@code kehancuranMaxRadius} of the centre, become TRENCH shapes 1 block wide and 1 deep (2 at Dewa) on
	 * the surface, added to this tick's submit (one job, one undo entry) with their fixed 15 % share of the cast's cap.
	 * {@code castPos} / {@code centre} are the CAST event's position and aim as sent, {@code seed} the event seed, so the
	 * clients draw the same lines ({@link SigilPattern}).
	 */
	void sigil(Vec3 castPos, Vec3 centre, int seed) {
		if (sigilDone || !on()) {
			return;
		}
		Dampak dampak = cast.spec.dampak();
		if (dampak == null || !dampak.sigilTrench() || trenchCap <= 0) {
			return;
		}
		sigilDone = true;
		// the trenches follow the lines the clients draw (the spec's own sigil radius), so the glow and the burn line up;
		// the reach is limited by dropping lines beyond 2 x kehancuranMaxRadius instead of shrinking the pattern
		double radius = dampak.sigilRadius();
		double limit = maxRadius * 2.0D;
		if (radius < 2.0D || limit < 2.0D) {
			return;
		}
		double yaw = SigilPattern.yawFor(castPos, centre, seed);
		List<SigilPattern.Segment> lines = new ArrayList<>();
		for (SigilPattern.Segment line : SigilPattern.majorSegments(dampak.sigil(), centre, yaw, radius, seed, 256)) {
			if (lines.size() >= Kehancuran.MAX_SIGIL_TRENCHES) {
				break;
			}
			if (horizontal(line.from(), centre) <= limit && horizontal(line.to(), centre) <= limit) {
				lines.add(line);
			}
		}
		int depth = cast.spec.dewa() ? 2 : 1;
		boolean natural = cast.spec.naturalOnly();
		List<Shapes.Placed> placed = new ArrayList<>();
		for (SigilPattern.Segment line : lines) {
			Vec3 dir = line.to().subtract(line.from());
			int length = Math.max(2, (int) Math.round(dir.horizontalDistance()) + 1);
			Shapes.Spec spec = Shapes.Spec.trench(length, 1, depth);
			if (natural) {
				spec = spec.natural();
			}
			placed.add(new Shapes.Placed(spec, BlockPos.containing(line.from()), dir));
		}
		if (placed.isEmpty()) {
			return;
		}
		trenches.addAll(placed);
		batchCap += trenchCap;
		if (!PENDING.contains(this)) {
			PENDING.add(this);
		}
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The cast ended: release shapes, and what the collected points still hold. */
	void release() {
		if (!on()) {
			return;
		}
		List<DShape> shapes = cast.spec.destruction();
		for (int i = 0; i < caps.length; i++) {
			if (submitted[i]) {
				continue;
			}
			DShape shape = shapes.get(i);
			if (!points.get(i).isEmpty()) {
				flush(i);
			} else if (shape.when() == DShape.When.RELEASE || shape.when() == DShape.When.IMPACT && cast.hitAny && !shape.perStage()) {
				Vec3 at = shape.kind() == DShape.Kind.RING || shape.kind() == DShape.Kind.ARC || shape.kind() == DShape.Kind.FAN
						|| shape.kind() == DShape.Kind.STRIP_FAN ? cast.caster.position() : cast.end;
				if (shape.along() == DShape.Along.EDGES && field != null) {
					submitEdges(i, field);
				} else {
					submitAt(i, List.of(at), List.of(cast.look), true);
				}
			}
		}
	}

	private void flush(int index) {
		List<Vec3> list = new ArrayList<>(points.get(index));
		List<Vec3> dirs = new ArrayList<>(directions.get(index));
		points.get(index).clear();
		directions.get(index).clear();
		submitAt(index, list, dirs, true);
	}

	private void submitEdges(int index, Fields.Area area) {
		List<Vec3> corners = area.corners();
		List<Vec3> starts = new ArrayList<>();
		List<Vec3> dirs = new ArrayList<>();
		for (int c = 0; c < corners.size(); c++) {
			Vec3 from = corners.get(c);
			Vec3 to = corners.get((c + 1) % corners.size());
			starts.add(from);
			dirs.add(to.subtract(from));
		}
		submitAt(index, starts, dirs, true);
	}

	/**
	 * {@code nearGround(n)}: the anchor has a solid block within n blocks below it (one read-only scan of at most n
	 * blocks, loaded chunks only); true when the shape has no such condition.
	 */
	private boolean nearGround(DShape shape, Vec3 at) {
		if (shape.nearGround() <= 0.0D) {
			return true;
		}
		BlockPos base = BlockPos.containing(at);
		int depth = (int) Math.ceil(shape.nearGround());
		for (int d = 0; d <= depth; d++) {
			BlockPos pos = base.below(d);
			if (!cast.level.isLoaded(pos)) {
				return false;
			}
			BlockState state = cast.level.getBlockState(pos);
			if (state.isSolid()) {
				return true;
			}
		}
		return false;
	}

	/** Builds the placed shapes of KH shape {@code index} at the anchors and adds them to this tick's submit with its cap share. */
	private boolean submitAt(int index, List<Vec3> anchors, List<Vec3> dirs, boolean last) {
		if (anchors.isEmpty() || !(cast.caster instanceof ServerPlayer)) {
			return false;
		}
		DShape shape = cast.spec.destruction().get(index);
		if (last) {
			submitted[index] = true;
		}
		List<Shapes.Placed> placed = new ArrayList<>();
		int safe = KultivatorConfig.get().kehancuranCasterSafeRadius;
		Vec3 look = cast.look.horizontal().lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : cast.look.horizontal().normalize();
		double bonus = Boons.khRadius(cast.caster);
		int lengthCap = maxRadius * (cast.spec.pamungkas() && cast.spec.dewa() ? 6 : 4);
		int count = Math.min(MAX_POINTS, anchors.size());
		for (int n = 0; n < count; n++) {
			Vec3 at = anchors.get(n);
			if (!nearGround(shape, at)) {
				continue;
			}
			Vec3 dir = n < dirs.size() ? dirs.get(n) : look;
			Vec3 flat = dir.horizontal().lengthSqr() < 1.0E-6D ? look : dir.horizontal().normalize();
			int r = radius(shape.a(), bonus);
			int d = depth(shape.c());
			Shapes.Spec spec;
			BlockPos anchor = BlockPos.containing(at);
			Vec3 direction = flat;
			switch (shape.kind()) {
				case BOWL, SCATTER -> spec = Shapes.Spec.bowl(r);
				case RING -> {
					int ro = radius(shape.a(), bonus);
					int ri = Math.max(0, (int) Math.round(dim(shape.b())));
					boolean onCaster = at.distanceTo(cast.caster.position()) < 2.0D;
					if (onCaster) {
						ri = Math.max(ri, safe + TechniquePower.MIN_RING_INNER_EXTRA);
					}
					if (ro <= ri) {
						ro = ri + 1;
					}
					spec = Shapes.Spec.ring(Math.min(maxRadius, ro), ri, d);
				}
				case TUNNEL -> {
					spec = Shapes.Spec.tunnel(Math.min(maxRadius, r), length(shape.b(), lengthCap));
					direction = dir.lengthSqr() < 1.0E-6D ? cast.look : dir.normalize();
				}
				case FISSURE -> {
					spec = Shapes.Spec.fissure(length(shape.a(), lengthCap), Math.max(1, Math.min(3, width(shape.b()))), d);
					if (at.distanceTo(cast.caster.position()) < safe + 3.0D) {
						at = cast.caster.position().add(flat.scale(safe + 3.0D));
						anchor = BlockPos.containing(at);
					}
				}
				case ARC -> {
					spec = Shapes.Spec.arc(Math.max(safe + 1 + width(shape.b()), radius(shape.a(), bonus)), width(shape.b()), d, shape.angle());
					anchor = BlockPos.containing(cast.caster.position());
				}
				case FAN -> {
					spec = Shapes.Spec.fan(length(shape.a(), lengthCap), d, shape.angle());
					anchor = BlockPos.containing(cast.caster.position());
				}
				case STRIP -> spec = Shapes.Spec.strip(r);
				case STRIP_FAN -> {
					spec = Shapes.Spec.stripFan(length(shape.a(), lengthCap), shape.angle() <= 0.0D ? 35.0D : shape.angle());
					anchor = BlockPos.containing(cast.caster.position());
				}
				case TRENCH -> {
					int length = length(shape.a(), lengthCap);
					if (shape.along() == DShape.Along.PATH && pathFrom != null && pathTo != null) {
						length = Math.max(2, Math.min(lengthCap, (int) Math.round(pathFrom.distanceTo(pathTo))));
						at = pathFrom;
						flat = pathTo.subtract(pathFrom).horizontal().lengthSqr() < 1.0E-6D ? flat : pathTo.subtract(pathFrom).horizontal().normalize();
					} else if (shape.along() == DShape.Along.EDGES) {
						length = Math.max(2, Math.min(lengthCap, (int) Math.round(dir.horizontal().length())));
					} else if (shape.along() == DShape.Along.BEAM && beamStart != null) {
						at = cast.caster.position().add(flat.scale(safe + 3.0D));
					} else if (at.distanceTo(cast.caster.position()) < safe + 3.0D || shape.ahead() > 0.0D) {
						at = cast.caster.position().add(flat.scale(Math.max(safe + 3.0D, shape.ahead())));
					}
					anchor = BlockPos.containing(at);
					direction = flat;
					spec = Shapes.Spec.trench(length, Math.max(1, width(shape.b())), d);
				}
				case SPHERE -> spec = Shapes.Spec.sphere(r);
				case DISC -> spec = Shapes.Spec.disc(r, d, shape.naturalOnly() || cast.spec.naturalOnly());
				case PIT -> spec = Shapes.Spec.pit(r, d);
				case COLUMN -> spec = Shapes.Spec.column(r, d);
				case NINE -> spec = Shapes.Spec.nine(r, Math.max(1, Math.min(12, (int) Math.round(dim(shape.b())))));
				case WITHER -> spec = Shapes.Spec.wither(r);
				default -> spec = Shapes.Spec.bowl(r);
			}
			if (shape.naturalOnly() || cast.spec.naturalOnly()) {
				spec = spec.natural();
			}
			placed.add(new Shapes.Placed(spec, anchor, direction));
		}
		if (placed.isEmpty()) {
			return false;
		}
		batch.addAll(placed);
		batchCap += caps[index];
		if (!PENDING.contains(this)) {
			PENDING.add(this);
		}
		return true;
	}

	/** Submits this cast's shapes of the tick as one {@code Kehancuran.submitSelf} (one job, one undo entry). */
	private void submitBatch() {
		if (batch.isEmpty() && trenches.isEmpty()) {
			return;
		}
		List<Shapes.Placed> all = new ArrayList<>(batch);
		all.addAll(trenches);
		List<Shapes.Placed> placed = List.copyOf(all);
		int cap = batchCap;
		batch.clear();
		trenches.clear();
		batchCap = 0;
		if (cast.caster instanceof ServerPlayer player && !player.isRemoved()) {
			Kehancuran.submitSelf(player, cast.spec.id(), placed, cap);
		}
	}

	/** End of the server tick (and right after a cast's own tick work): every waiting cast submits once. */
	static void flushAll() {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Destruction> list = List.copyOf(PENDING);
		PENDING.clear();
		for (Destruction destruction : list) {
			destruction.submitBatch();
		}
	}

	/** Server stop: nothing waits across worlds. */
	static void clearAll() {
		for (Destruction destruction : PENDING) {
			destruction.batch.clear();
			destruction.trenches.clear();
			destruction.batchCap = 0;
		}
		PENDING.clear();
	}

	private int radius(double base, double bonus) {
		return Math.max(1, Math.min(maxRadius, (int) Math.round(dim(base) + bonus)));
	}

	private int width(double base) {
		return Math.max(1, Math.min(maxRadius, (int) Math.round(dim(base))));
	}

	private int depth(double base) {
		return Math.max(1, Math.min(TechniquePower.DEPTH_CAP, (int) Math.round(dim(base))));
	}

	private int length(double base, int cap) {
		return Math.max(2, Math.min(cap, (int) Math.round(dim(base))));
	}
}
