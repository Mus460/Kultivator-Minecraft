package dev.kultivator.sect.build.blueprint;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One construction unit of a template (DESIGN 21.2 rule 1): plot terrain + structure + plot landscaping, built as a whole
 * once its whole bill is reserved. A component has a stable id (saved forever: never rename one, DESIGN 21.9), the tier
 * it belongs to, a kind id (the UI and the audit), a display name key, a scheduling group and a list of {@link Part}s
 * emitted in order (later parts overwrite earlier ones cell by cell).
 * <p>
 * Placement order inside a tier (DESIGN 21.9): {@link Group#AXIS} first, then {@link Group#MAIN} by axis priority (gate
 * side first), {@link Group#SIDE} from the inside out, {@link Group#BOUNDARY} clockwise from the gate,
 * {@link Group#GARDEN}, and the tier's {@link Group#ORNAMENT} last. {@code order} breaks ties (lower first); when it is
 * left at {@link Integer#MIN_VALUE} it is derived from the component's box.
 */
public final class Component {

	/** Scheduling groups, in placement order. */
	public enum Group {
		AXIS,
		MAIN,
		SIDE,
		BOUNDARY,
		GARDEN,
		ORNAMENT
	}

	private final String id;
	private final int tier;
	private final String kind;
	private final String nameKey;
	private final Group group;
	private final int order;
	private final List<Part> parts;

	private Component(Builder builder) {
		this.id = builder.id;
		this.tier = builder.tier;
		this.kind = builder.kind;
		this.nameKey = builder.nameKey;
		this.group = builder.group;
		this.order = builder.order;
		this.parts = List.copyOf(builder.parts);
	}

	/** Starts a component with a stable id (lower_snake, unique in its template) in tier 1-9. */
	public static Builder of(String id, int tier) {
		return new Builder(id, tier);
	}

	public String id() {
		return id;
	}

	public int tier() {
		return tier;
	}

	public String kind() {
		return kind;
	}

	/** The full translation key of the display name ({@code kultivator.blueprint.comp.<name>}). */
	public String nameKey() {
		return nameKey;
	}

	public net.minecraft.network.chat.Component name() {
		return net.minecraft.network.chat.Component.translatable(nameKey);
	}

	public Group group() {
		return group;
	}

	public int order() {
		return order;
	}

	public List<Part> parts() {
		return parts;
	}

	/** The template-local box of all parts, or null without parts. */
	public @Nullable BoundingBox bounds(Ctx ctx) {
		BoundingBox box = null;
		for (Part part : parts) {
			BoundingBox each = part.bounds(ctx);
			if (each != null) {
				box = box == null ? each : BoundingBox.encapsulating(box, each);
			}
		}
		return box;
	}

	/** Emits every part into {@code sink}. */
	public void emit(Ctx ctx, Sink sink) {
		for (Part part : parts) {
			part.emit(ctx, sink);
		}
	}

	/** Builds a {@link Component}. */
	public static final class Builder {
		private final String id;
		private final int tier;
		private String kind = "unit";
		private String nameKey;
		private Group group = Group.MAIN;
		private int order = Integer.MIN_VALUE;
		private final List<Part> parts = new ArrayList<>();

		private Builder(String id, int tier) {
			this.id = id;
			this.tier = Math.max(1, Math.min(9, tier));
			this.nameKey = "kultivator.blueprint.comp.unit";
		}

		/** The kind id (also the default name: {@code kultivator.blueprint.comp.<kind>}). */
		public Builder kind(String kind) {
			this.kind = kind;
			if ("kultivator.blueprint.comp.unit".equals(nameKey)) {
				this.nameKey = "kultivator.blueprint.comp." + kind;
			}
			return this;
		}

		/** The display name: {@code kultivator.blueprint.comp.<name>} (defined in {@code lang-src/blueprint.*}). */
		public Builder name(String name) {
			this.nameKey = "kultivator.blueprint.comp." + name;
			return this;
		}

		public Builder group(Group group) {
			this.group = group;
			return this;
		}

		public Builder group(Group group, int order) {
			this.group = group;
			this.order = order;
			return this;
		}

		public Builder add(Part part) {
			if (part != null) {
				parts.add(part);
			}
			return this;
		}

		public Builder add(Part... more) {
			for (Part part : more) {
				add(part);
			}
			return this;
		}

		public Component build() {
			return new Component(this);
		}
	}
}
