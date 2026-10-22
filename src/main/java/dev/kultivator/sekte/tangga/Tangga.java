package dev.kultivator.sekte.tangga;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.hall.Candidates;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.TanggaService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module G, Ujian Tangga Seribu (DESIGN 36.2.4, 36.4): the thousand-step trial at the sect's gate. Wired with one line in
 * {@code SekteContent.init()}; everything it owns registers from {@link #init()}: the config section {@code tangga}, the
 * stair-state payload, the template component {@code sx_tangga_batu} ({@link BatuHatiKomponen}), the service
 * {@code SekteApi.tangga()} (contract C36.G1), the hook in {@code sect.hall.Candidates} that turns the stele test into a
 * climb ({@link Pendakian}), the Ujian Hati re-climb ({@link UjianHati}) and the commands ({@link TanggaCommands}).
 * <p>
 * Nothing here changes a block at runtime: the stelae are built by the sect engine from deposited materials, the stair is
 * drawn by the client, and the climbers are moved without physics along {@link JalurTangga}. The old recruitment numbers
 * (tiers, verdicts, one amethyst shard per test) are untouched; the climb only adds the steps reached and the heart
 * verdict ({@code extra.tangga}).
 * <p>
 * Sources: the Seven Mysteries Sect's three-stage climb, <i>A Record of a Mortal's Journey</i> ch. 1-5 (already cited by
 * {@code Candidates}); Mount Tai's "Eighteen Bends", about 1,600 steps up to the South Gate of Heaven; Sun Wukong waiting
 * and tested for patience before Subhuti takes him in, <i>Journey to the West</i> ch. 2.
 */
public final class Tangga implements TanggaService {

	public static final Tangga INSTANCE = new Tangga();
	/** SekteScenes beats of module G (130-139): 130 "Puncak!", 131 a disciple's Ujian Hati reaching the peak. */
	public static final int BEAT_PEAK = EpicIds.EXTRA_SEKTE_FIRST + 30;
	public static final int BEAT_HEART_PEAK = BEAT_PEAK + 1;
	/** Keys of {@code extra.tangga} (DESIGN 36.2.4). */
	public static final String K_STEPS = "l";
	public static final String K_VERDICT = "v";
	public static final String K_DAY = "d";
	public static final String K_CLIMBS = "n";
	public static final String K_BEST = "b";
	public static final String K_RECLIMB_DAY = "u";
	public static final String K_PEAK = "p";
	/** How long {@link #hasStair} remembers its answer (ticks). */
	private static final int STAIR_CACHE_TICKS = 100;

	private record Cached(boolean stair, long until) {
	}

	private static final Map<UUID, Cached> STAIR = new HashMap<>();
	private static boolean initialised;

	/** From {@code SekteContent.init()} (both physical sides). */
	public static void init() {
		if (initialised || !SekteContent.on(SekteIds.G)) {
			return;
		}
		initialised = true;
		TanggaConfig.load();
		TanggaPayloads.register();
		BatuHatiKomponen.register();
		SekteApi.setTangga(INSTANCE);
		Candidates.setStairTrial(Pendakian.TRIAL);
		ServerTickEvents.END_SERVER_TICK.register(Pendakian::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Pendakian.stopAll(server);
			STAIR.clear();
		});
		// A disciple saved in the middle of a climb (the game stopped hard) gets its gravity and AI back when it loads.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof dev.kultivator.disciple.DiscipleEntity disciple && disciple.role() == dev.kultivator.disciple.DiscipleEntity.Role.DISCIPLE) {
				Pendakian.recover(disciple);
			}
		});
		DiscipleHooks.addInfo((entity, master, tag) -> {
			CompoundTag mine = entity.data().extra().getCompound(SekteIds.EXTRA_G).orElse(null);
			if (mine != null && !mine.isEmpty()) {
				tag.put("sekte_tangga", mine.copy());
			}
		});
		TanggaCommands.register();
	}

	/** True once {@link #init()} ran with the module on (the client checks it before registering its receiver). */
	public static boolean initialised() {
		return initialised;
	}

	// ================================================================== TanggaService (contract C36.G1/G3)

	/** True when the stair stands: component {@code sx_tangga_batu} DONE, or template 1 at tier 2 or more. */
	@Override
	public boolean hasStair(UUID owner) {
		if (owner == null) {
			return false;
		}
		ServerLevel level = Candidates.hallLevel(owner);
		long now = level == null ? 0L : level.getServer().getTickCount();
		Cached cached = STAIR.get(owner);
		if (cached != null && now < cached.until()) {
			return cached.stair();
		}
		boolean stair = level != null && computeStair(owner);
		STAIR.put(owner, new Cached(stair, now + STAIR_CACHE_TICKS));
		if (STAIR.size() > 256) {
			STAIR.clear();
		}
		return stair;
	}

	private static boolean computeStair(UUID owner) {
		EngineApi engine = EngineApi.get();
		int template = engine.template(owner);
		if (template <= 0) {
			return false;
		}
		if (template == 1) {
			return engine.tier(owner) >= 2;
		}
		List<ComponentView> components = engine.components(owner);
		for (ComponentView view : components) {
			if (BatuHatiKomponen.ID.equals(view.id())) {
				return view.state() == ComponentView.STATE_DONE;
			}
		}
		return false;
	}

	/** Forgets the cached answer (a component finished, the test commands). */
	static void forgetStair(UUID owner) {
		STAIR.remove(owner);
	}

	@Override
	public boolean running(UUID owner) {
		return Pendakian.running(owner);
	}

	@Override
	public int steps(DiscipleData data) {
		CompoundTag mine = data == null ? null : data.extra().getCompound(SekteIds.EXTRA_G).orElse(null);
		return mine == null ? 0 : Math.max(0, Math.min(JalurTangga.STEPS, mine.getIntOr(K_STEPS, 0)));
	}

	@Override
	public int resolve(DiscipleData data) {
		CompoundTag mine = data == null ? null : data.extra().getCompound(SekteIds.EXTRA_G).orElse(null);
		return mine == null ? 0 : Math.max(0, Math.min(3, mine.getIntOr(K_VERDICT, 0)));
	}

	// ================================================================== the stair's frame

	/**
	 * The stair of the owner's hall: from the {@code tangga_proyektor} anchor; template 1 (or a missing anchor) projects
	 * from four blocks past the {@code arrival} anchor; without either, ten blocks from the stele toward {@code toward}.
	 * The height is fitted to the standing cell found near the anchor (the plan's level, then the real ground).
	 */
	static @Nullable JalurTangga.Frame frame(UUID owner, ServerLevel level, BlockPos stele, @Nullable Vec3 toward) {
		BlockPos projector = SectBuild.anchor(owner, BatuHatiKomponen.ANCHOR_PROJECTOR);
		double dx;
		double dz;
		BlockPos origin;
		if (projector != null) {
			dx = projector.getX() - stele.getX();
			dz = projector.getZ() - stele.getZ();
			origin = projector;
		} else {
			BlockPos arrival = SectBuild.anchor(owner, "arrival");
			if (arrival != null && (arrival.getX() != stele.getX() || arrival.getZ() != stele.getZ())) {
				dx = arrival.getX() - stele.getX();
				dz = arrival.getZ() - stele.getZ();
			} else if (toward != null) {
				dx = toward.x - (stele.getX() + 0.5D);
				dz = toward.z - (stele.getZ() + 0.5D);
			} else {
				dx = 0.0D;
				dz = 1.0D;
			}
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length < 1.0E-3D) {
				dx = 0.0D;
				dz = 1.0D;
				length = 1.0D;
			}
			BlockPos base = arrival != null ? arrival : stele;
			double reach = arrival != null ? 4.0D : 10.0D;
			origin = BlockPos.containing(base.getX() + 0.5D + dx / length * reach, base.getY(), base.getZ() + 0.5D + dz / length * reach);
		}
		if (!level.isLoaded(origin)) {
			return null;
		}
		int y = standY(level, origin.getX(), origin.getZ(), origin.getY());
		return JalurTangga.Frame.of(origin.getX() + 0.5D, y, origin.getZ() + 0.5D, dx, dz);
	}

	/**
	 * The standing height (feet y) of column (x, z) near {@code hint}: the first cell from {@code hint + 3} down to
	 * {@code hint - 6} with a colliding block under it and room for a body; {@code hint} when none.
	 */
	static int standY(ServerLevel level, int x, int z, int hint) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = hint + 3; y >= hint - 6; y--) {
			pos.set(x, y - 1, z);
			BlockState below = level.getBlockState(pos);
			if (below.getCollisionShape(level, pos).isEmpty()) {
				continue;
			}
			pos.set(x, y, z);
			BlockState feet = level.getBlockState(pos);
			if (!feet.getCollisionShape(level, pos).isEmpty() && feet.getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y) > 0.6D) {
				continue;
			}
			pos.set(x, y + 1, z);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				continue;
			}
			return y;
		}
		return hint;
	}

	private Tangga() {
	}
}
