package dev.kultivator.sekte.langit.main;

import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Cloud Stair as a path (DESIGN 37.4.6, 37.5.2) and where candidates arrive on Recruitment Day.
 * <ul>
 * <li>{@link #path}: the 1001 points (steps 0-1000) of the Thousand Steps trial mapped evenly along the real helix of
 * {@code aw_t3_tangga_awan} (radius 3.5 around the stair column's centre, 24 steps of half a block per turn) and then the
 * root hollow to {@code tangga_puncak}, so "the thousand steps finally lead to a real place". Module 36 G
 * ({@code TanggaService}) has no path hook yet; this is the contract it can call ({@code LangitTangga.path(owner)}),
 * the module 36 file is not edited (DESIGN 37.0 rule 11).</li>
 * <li>{@link #arrival}: a sky hall's candidates arrive on the stair's foot platform ({@code arrival_kaki}) once the stair
 * stands, so their first act is the climb; before that on the island's {@code arrival}. Registered with
 * {@code Halls.addArrivalSpotProvider} ({@code LangitArrival} in the design).</li>
 * </ul>
 */
public final class LangitTangga {

	public static final int STEPS = 1001;
	public static final double RADIUS = 3.5D;
	public static final int STEPS_PER_TURN = 24;
	public static final double RISE_PER_STEP = 0.5D;
	public static final String COMPONENT = "aw_t3_tangga_awan";

	static void register() {
		Halls.addArrivalSpotProvider(LangitTangga::arrival);
	}

	/** True when the hall's Cloud Stair component is built. */
	public static boolean stairBuilt(UUID owner) {
		for (ComponentView component : EngineApi.get().components(owner)) {
			if (COMPONENT.equals(component.id())) {
				return component.state() == ComponentView.STATE_DONE;
			}
		}
		return false;
	}

	/** The candidates' arrival spot of a standing sky hall, or null for other halls (the next provider decides). */
	public static @Nullable BlockPos arrival(UUID owner, ServerLevel level, BlockPos stele, RandomSource random) {
		if (!LangitConfig.on() || !LangitApi.get().isSkyHall(owner)) {
			return null;
		}
		BlockPos spot = stairBuilt(owner) ? LangitApi.get().anchor(owner, "arrival_kaki") : null;
		if (spot == null) {
			spot = LangitApi.get().anchor(owner, "arrival");
		}
		return spot == null ? null : spot.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
	}

	/** The 1001 points of the climb along the real stair (null when the hall has no built stair). */
	public static @Nullable List<Vec3> path(UUID owner) {
		SkyHallView view = LangitApi.get().skyHall(owner);
		if (view == null || !stairBuilt(owner)) {
			return null;
		}
		BlockPos foot = LangitApi.get().anchor(owner, "tangga_kaki");
		BlockPos top = LangitApi.get().anchor(owner, "tangga_puncak");
		BoundingBox kaki = view.kakiBox();
		if (foot == null || top == null || kaki == null) {
			return null;
		}
		double cx = (kaki.minX() + kaki.maxX() + 1) / 2.0D;
		double cz = (kaki.minZ() + kaki.maxZ() + 1) / 2.0D;
		List<Vec3> line = new ArrayList<>();
		line.add(Vec3.atBottomCenterOf(foot));
		double angle = Math.atan2(foot.getZ() + 0.5D - cz, foot.getX() + 0.5D - cx);
		double y = foot.getY();
		double ceiling = Math.min(top.getY(), view.skyFloorY()) - 1.0D;
		double turn = Math.PI * 2.0D / STEPS_PER_TURN;
		int guard = 0;
		while (y < ceiling && guard++ < 4000) {
			angle += turn;
			y += RISE_PER_STEP;
			line.add(new Vec3(cx + Math.cos(angle) * RADIUS, y, cz + Math.sin(angle) * RADIUS));
		}
		line.add(new Vec3(cx, Math.max(y, ceiling) + 1.0D, cz));
		line.add(Vec3.atBottomCenterOf(top));
		return resample(line, STEPS);
	}

	/** {@code n} points evenly spaced along the polyline. */
	static List<Vec3> resample(List<Vec3> line, int n) {
		double[] at = new double[line.size()];
		for (int i = 1; i < line.size(); i++) {
			at[i] = at[i - 1] + line.get(i).distanceTo(line.get(i - 1));
		}
		double total = at[at.length - 1];
		List<Vec3> out = new ArrayList<>(n);
		int seg = 1;
		for (int k = 0; k < n; k++) {
			double want = total * k / (n - 1);
			while (seg < line.size() - 1 && at[seg] < want) {
				seg++;
			}
			double span = at[seg] - at[seg - 1];
			double t = span <= 1.0E-9D ? 0.0D : (want - at[seg - 1]) / span;
			out.add(line.get(seg - 1).lerp(line.get(seg), Math.max(0.0D, Math.min(1.0D, t))));
		}
		return List.copyOf(out);
	}

	private LangitTangga() {
	}
}
