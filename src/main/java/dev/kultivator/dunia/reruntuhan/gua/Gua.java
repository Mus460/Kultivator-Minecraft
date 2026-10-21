package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.api.TreasureBirth;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.dunia.reruntuhan.Runs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reruntuhan Gua Leluhur (idea 55, DESIGN 34.5.5): the anchor handler of the {@code reruntuhan/*} anchors the three
 * cave structures carry (gate, riddle stele, puzzle rooms, guardian chamber, inheritance hall; the {@code situs} anchor
 * is published by the spine itself). Nothing here ever places or breaks a block.
 */
public final class Gua {

	public static final String MODULE = "reruntuhan";

	/** From {@code Reruntuhan.init()}. */
	public static void init() {
		DuniaAnchors.register(MODULE, new AnchorHandler() {
			@Override
			public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
				switch (anchor.what()) {
					case "teka" -> RoomRun.maybeReseal(anchor, level);
					case "penjaga" -> GuardianRun.maybeReseal(anchor, level);
					default -> {
					}
				}
			}

			@Override
			public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
				if (!Reruntuhan.config().ruins) {
					return;
				}
				Runs.touch(anchor, level, a -> create(a, level));
			}

			@Override
			public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
				Runs.drop(anchor);
			}
		});
		DuniaEvents.TREASURE_BORN.register(Gua::onTreasureBorn);
	}

	private static @Nullable AnchorRun create(DuniaAnchorEntity anchor, ServerLevel level) {
		return switch (anchor.what()) {
			case "teka" -> new RoomRun(anchor, level);
			case "gerbang" -> new GateRun(anchor, level);
			case "prasasti" -> new SteleRun(anchor, level);
			case "penjaga" -> new GuardianRun(anchor, level);
			case "warisan" -> new HallRun(anchor, level);
			default -> null;
		};
	}

	/** A treasure born within 64 blocks of a ruin's guardian chamber wakes the guardian (DESIGN 34.6 R). */
	private static void onTreasureBorn(ServerLevel level, TreasureBirth birth) {
		if (!Reruntuhan.config().ruins || !birth.dim().equals(level.dimension())) {
			return;
		}
		Vec3 at = Vec3.atCenterOf(birth.pos());
		for (DuniaAnchorEntity anchor : DuniaAnchors.loaded(level, MODULE + "/penjaga")) {
			if (anchor.position().distanceToSqr(at) <= 64.0D * 64.0D) {
				GuardianRun.wake(anchor);
			}
		}
	}

	/** The nearest live run of {@code type} within {@code range} of the player (tests). */
	public static <T extends AnchorRun> @Nullable T nearest(ServerPlayer player, Class<T> type, double range) {
		T best = null;
		double bestSq = range * range;
		for (T run : Runs.all(type)) {
			if (run.level != player.level()) {
				continue;
			}
			double d = run.anchor.position().distanceToSqr(player.position());
			if (d < bestSq) {
				bestSq = d;
				best = run;
			}
		}
		return best;
	}

	private Gua() {
	}
}
