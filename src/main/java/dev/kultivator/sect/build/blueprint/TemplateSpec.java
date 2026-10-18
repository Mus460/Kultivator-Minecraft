package dev.kultivator.sect.build.blueprint;

import dev.kultivator.sect.build.api.SectTemplates;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one template is (DESIGN 21.1, 21.9): footprint and height envelope, the site rules of Table A/B (max relief,
 * earthwork cap, the disc's lodestone distance and ring radius, territory radius, tower rooms, Qi seat tier, gate
 * altars), the terrain zones with their design level and allowed range, and the components of all nine tiers in
 * placement order. Immutable and shared by every site of the template (read off the server thread).
 * <p>
 * Coordinates are template-local: the stele at (0,0,0), +z toward the gate, +x east, the plaza floor at y = -1. The
 * footprint column is {@code x0..x1 × z0..z1 × (-1 - below)..(-1 + above)}; the engine never places outside it.
 */
public final class TemplateSpec {

	/**
	 * A terrain zone (DESIGN 21.9 {@code TerrainZone}): a rectangle whose standing level is fitted to the terrain inside
	 * {@code lo..hi} (design level {@code design}), at least {@code minStep} above its {@code parent} zone when one is named.
	 * Fixed zones have {@code lo == hi}.
	 */
	public record Zone(String id, int x0, int z0, int x1, int z1, int design, int lo, int hi, @Nullable String parent, int minStep) {
		public boolean contains(int x, int z) {
			return x >= Math.min(x0, x1) && x <= Math.max(x0, x1) && z >= Math.min(z0, z1) && z <= Math.max(z0, z1);
		}
	}

	private final int template;
	private final int x0;
	private final int x1;
	private final int z0;
	private final int z1;
	private final int above;
	private final int below;
	private final int maxRelief;
	private final int earthworkCap;
	private final int discD;
	private final int discR;
	private final int territory;
	private final int towerRooms;
	private final int qiTier;
	private final int gateAltars;
	private final int planVersion;
	private final Map<String, Zone> zones;
	private final List<Component> components;
	private final LongSet keep;

	private TemplateSpec(Builder builder) {
		this.template = builder.template;
		this.x0 = builder.x0;
		this.x1 = builder.x1;
		this.z0 = builder.z0;
		this.z1 = builder.z1;
		this.above = builder.above;
		this.below = builder.below;
		this.maxRelief = builder.maxRelief;
		this.earthworkCap = builder.earthworkCap;
		this.discD = builder.discD;
		this.discR = builder.discR;
		this.territory = builder.territory;
		this.towerRooms = builder.towerRooms;
		this.qiTier = builder.qiTier;
		this.gateAltars = builder.gateAltars;
		this.planVersion = builder.planVersion;
		this.zones = Collections.unmodifiableMap(new LinkedHashMap<>(builder.zones));
		LongOpenHashSet keepCells = new LongOpenHashSet();
		// The four duty posts 5 blocks N/E/S/W of the stele stay air (feet and head) over walkable floor (DESIGN 21.9).
		for (int[] post : new int[][]{{0, -5}, {0, 5}, {5, 0}, {-5, 0}}) {
			keepCells.add(BlockPos.asLong(post[0], 0, post[1]));
			keepCells.add(BlockPos.asLong(post[0], 1, post[1]));
		}
		keepCells.addAll(builder.keep);
		this.keep = keepCells;
		// Placement order: tier, group, then the explicit or derived order key, then insertion order.
		List<Component> sorted = new ArrayList<>(builder.components);
		Ctx dry = Ctx.dry(this, Glaze.TINTA);
		Map<Component, Integer> keys = new LinkedHashMap<>();
		for (int i = 0; i < sorted.size(); i++) {
			Component component = sorted.get(i);
			keys.put(component, component.order() != Integer.MIN_VALUE ? component.order() : derivedOrder(component, dry, i));
		}
		List<Component> indexed = new ArrayList<>(sorted);
		sorted.sort(Comparator.comparingInt(Component::tier).thenComparingInt((Component c) -> c.group().ordinal())
				.thenComparingInt(keys::get).thenComparingInt(indexed::indexOf));
		this.components = List.copyOf(sorted);
	}

	/** The automatic order key of a component inside its group (DESIGN 21.9 placement order). */
	private static int derivedOrder(Component component, Ctx dry, int index) {
		BoundingBox box = component.bounds(dry);
		if (box == null) {
			return index;
		}
		int cx = (box.minX() + box.maxX()) / 2;
		int cz = (box.minZ() + box.maxZ()) / 2;
		return switch (component.group()) {
			case AXIS -> -cz;
			case MAIN -> -cz * 64 + Math.abs(cx);
			case SIDE -> Math.abs(cx) * 256 - cz;
			case BOUNDARY -> {
				// Clockwise from the gate (+z), seen from above: south, west, north, east.
				double angle = Math.atan2(-cx, cz);
				if (angle < 0) {
					angle += Math.PI * 2;
				}
				yield (int) Math.round(angle * 1000);
			}
			default -> index;
		};
	}

	public static Builder builder(int template) {
		return new Builder(template);
	}

	// ================================================================== reading

	public int template() {
		return template;
	}

	public String id() {
		return SectTemplates.id(template);
	}

	public int x0() {
		return x0;
	}

	public int x1() {
		return x1;
	}

	public int z0() {
		return z0;
	}

	public int z1() {
		return z1;
	}

	public int above() {
		return above;
	}

	public int below() {
		return below;
	}

	/** Footprint width (x). */
	public int width() {
		return x1 - x0 + 1;
	}

	/** Footprint depth (z). */
	public int depth() {
		return z1 - z0 + 1;
	}

	public int maxRelief() {
		return maxRelief;
	}

	public int earthworkCap() {
		return earthworkCap;
	}

	/** The disc's lodestone distance along +z from the stele (Table B). */
	public int discD() {
		return discD;
	}

	/** The disc's ring radius (Table B). */
	public int discR() {
		return discR;
	}

	/** Territory radius R_t (Table B). */
	public int territory() {
		return territory;
	}

	public int towerRooms() {
		return towerRooms;
	}

	public int qiTier() {
		return qiTier;
	}

	public int gateAltars() {
		return gateAltars;
	}

	public int planVersion() {
		return planVersion;
	}

	public Map<String, Zone> zones() {
		return zones;
	}

	public @Nullable Zone zone(String id) {
		return id == null ? null : zones.get(id);
	}

	/** Every component of all tiers in placement order. */
	public List<Component> components() {
		return components;
	}

	public List<Component> components(int tier) {
		List<Component> list = new ArrayList<>();
		for (Component component : components) {
			if (component.tier() == tier) {
				list.add(component);
			}
		}
		return list;
	}

	public @Nullable Component component(String id) {
		for (Component component : components) {
			if (component.id().equals(id)) {
				return component;
			}
		}
		return null;
	}

	/** Cells that must stay air (the duty posts and any the template added), template-local packed. */
	public LongSet keep() {
		return keep;
	}

	/** The footprint column in template-local coordinates (the only space the engine may change). */
	public BoundingBox column() {
		return new BoundingBox(x0, -1 - below, z0, x1, -1 + above, z1);
	}

	/** True when the empty-but-valid placeholder of a template not written yet (no components). */
	public boolean isPlaceholder() {
		return components.isEmpty();
	}

	// ================================================================== builder

	/** Builds a {@link TemplateSpec} (see {@code Templates} for the authoring guide). */
	public static final class Builder {
		private final int template;
		private int x0 = -12;
		private int x1 = 12;
		private int z0 = -16;
		private int z1 = 12;
		private int above = 14;
		private int below = 14;
		private int maxRelief = 10;
		private int earthworkCap = 3625;
		private int discD = 2;
		private int discR = 4;
		private int territory = 24;
		private int towerRooms = 3;
		private int qiTier = 1;
		private int gateAltars;
		private int planVersion = 1;
		private final Map<String, Zone> zones = new LinkedHashMap<>();
		private final List<Component> components = new ArrayList<>();
		private final LongOpenHashSet keep = new LongOpenHashSet();

		private Builder(int template) {
			this.template = template;
			zones.put("plaza", new Zone("plaza", -6, -6, 6, 6, 0, 0, 0, null, 0));
		}

		/** Footprint rectangle (Table A), inclusive. */
		public Builder footprint(int x0, int x1, int z0, int z1) {
			this.x0 = Math.min(x0, x1);
			this.x1 = Math.max(x0, x1);
			this.z0 = Math.min(z0, z1);
			this.z1 = Math.max(z0, z1);
			return this;
		}

		/** Height envelope above and below the plaza (Table A). */
		public Builder envelope(int above, int below) {
			this.above = above;
			this.below = below;
			return this;
		}

		public Builder relief(int maxRelief) {
			this.maxRelief = maxRelief;
			return this;
		}

		public Builder earthwork(int cap) {
			this.earthworkCap = cap;
			return this;
		}

		/** The Bagua disc: lodestone {@code d} blocks toward the gate from the stele, ring radius {@code r} (Table B). */
		public Builder disc(int d, int r) {
			this.discD = d;
			this.discR = r;
			return this;
		}

		public Builder territory(int radius) {
			this.territory = radius;
			return this;
		}

		public Builder towerRooms(int rooms) {
			this.towerRooms = rooms;
			return this;
		}

		public Builder qiTier(int tier) {
			this.qiTier = tier;
			return this;
		}

		public Builder gateAltars(int altars) {
			this.gateAltars = altars;
			return this;
		}

		/** Bump when blocks of unfinished components change (finished ones are never rebuilt, DESIGN 21.9). */
		public Builder planVersion(int version) {
			this.planVersion = version;
			return this;
		}

		/** A fixed-level zone (the plaza is predefined as "plaza", x/z -6..6, level 0; redefine it to resize). */
		public Builder zone(String id, int x0, int z0, int x1, int z1, int level) {
			zones.put(id, new Zone(id, x0, z0, x1, z1, level, level, level, null, 0));
			return this;
		}

		/** A fitted zone: design level {@code design}, fitted to the terrain within {@code lo..hi}. */
		public Builder zone(String id, int x0, int z0, int x1, int z1, int design, int lo, int hi) {
			zones.put(id, new Zone(id, x0, z0, x1, z1, design, Math.min(lo, hi), Math.max(lo, hi), null, 0));
			return this;
		}

		/** A fitted zone kept at least {@code minStep} above its {@code parent} zone. */
		public Builder zone(String id, int x0, int z0, int x1, int z1, int design, int lo, int hi, String parent, int minStep) {
			zones.put(id, new Zone(id, x0, z0, x1, z1, design, Math.min(lo, hi), Math.max(lo, hi), parent, minStep));
			return this;
		}

		/** Keeps a template-local cell air forever (a route, a view). */
		public Builder keep(int x, int y, int z) {
			keep.add(BlockPos.asLong(x, y, z));
			return this;
		}

		public Builder add(Component component) {
			if (component != null) {
				components.add(component);
			}
			return this;
		}

		public Builder add(Component.Builder component) {
			return add(component.build());
		}

		public TemplateSpec build() {
			return new TemplateSpec(this);
		}
	}
}
