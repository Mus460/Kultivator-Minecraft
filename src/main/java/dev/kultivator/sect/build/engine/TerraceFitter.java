package dev.kultivator.sect.build.engine;

import dev.kultivator.sect.build.blueprint.TemplateSpec;
import dev.kultivator.sect.build.blueprint.TerrainView;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fits the terrain zones of a template to the snapshot (DESIGN 21.9 pipeline, 21.10 earthwork): each zone with a range
 * gets the median of the natural standing levels of its columns, clamped to its range and kept at least {@code minStep}
 * above its parent zone; fixed zones (the plaza) keep their level. The result is frozen at confirmation. Pure function of
 * immutable inputs (runs off the server thread).
 */
public final class TerraceFitter {

	public static Map<String, Integer> fit(TemplateSpec spec, TerrainView terrain) {
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (TemplateSpec.Zone zone : spec.zones().values()) {
			int level;
			if (zone.lo() == zone.hi()) {
				level = zone.lo();
			} else {
				int x0 = Math.min(zone.x0(), zone.x1());
				int x1 = Math.max(zone.x0(), zone.x1());
				int z0 = Math.min(zone.z0(), zone.z1());
				int z1 = Math.max(zone.z0(), zone.z1());
				int[] samples = new int[(x1 - x0 + 1) * (z1 - z0 + 1)];
				int n = 0;
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						if (terrain.known(x, z)) {
							samples[n++] = terrain.ground(x, z) + 1;
						}
					}
				}
				if (n == 0) {
					level = zone.design();
				} else {
					int[] used = Arrays.copyOf(samples, n);
					Arrays.sort(used);
					level = used[n / 2];
				}
				level = Math.max(zone.lo(), Math.min(zone.hi(), level));
			}
			if (zone.parent() != null && levels.containsKey(zone.parent())) {
				level = Math.max(level, levels.get(zone.parent()) + zone.minStep());
				level = Math.min(level, Math.max(zone.hi(), levels.get(zone.parent()) + zone.minStep()));
			}
			levels.put(zone.id(), level);
		}
		return levels;
	}

	/** Cut and fill block counts of the zones against the terrain (the earthwork line of the preview). */
	public static long[] earthwork(TemplateSpec spec, TerrainView terrain, Map<String, Integer> levels) {
		long cut = 0L;
		long fill = 0L;
		for (TemplateSpec.Zone zone : spec.zones().values()) {
			int level = levels.getOrDefault(zone.id(), zone.design());
			for (int x = Math.min(zone.x0(), zone.x1()); x <= Math.max(zone.x0(), zone.x1()); x++) {
				for (int z = Math.min(zone.z0(), zone.z1()); z <= Math.max(zone.z0(), zone.z1()); z++) {
					if (!terrain.known(x, z)) {
						continue;
					}
					int ground = terrain.ground(x, z);
					if (ground >= level) {
						cut += ground - level + 1;
					} else if (ground < level - 1) {
						fill += Math.min(16, level - 1 - ground);
					}
				}
			}
		}
		return new long[]{cut, fill};
	}

	private TerraceFitter() {
	}
}
