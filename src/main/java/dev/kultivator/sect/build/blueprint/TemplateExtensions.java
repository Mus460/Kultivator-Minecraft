package dev.kultivator.sect.build.blueprint;

import dev.kultivator.Kultivator;
import dev.kultivator.sect.build.api.SectTemplates;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The template registry hook of DESIGN 36.3.5 (contract C36.L5): other systems add components (and anchors, zones and
 * kept cells) to the nine templates without editing their classes. {@link TemplateSpec.Builder#build()} runs every
 * extension of its template, in registration order, right before the spec is frozen, so extension components are sorted,
 * planned, billed from deposited materials, scheduled and built by the engine exactly like the template's own ones; a
 * site already past their tier builds them first as "Tambahan" (the Scheduler rule) and never loses its tier.
 * <p>
 * Rules: register from the module's init (before the first {@code Templates.get}; a later registration is refused and
 * logged). Component ids are {@code sx_<module>_<what>} ({@code sekte.SekteIds.componentId}), stable forever. Parts stay
 * inside the template's footprint column (the only space the engine may change) and in the plot DESIGN 36.4 assigns to
 * the module; check with {@code /kultivasi uji sekte audit <n>} and {@code uji sekte pamer <n> tingkat <t>}. An
 * extension that throws is logged and skipped (the template stays as it was).
 */
public final class TemplateExtensions {

	/**
	 * Every sky template 11-19 (DESIGN 37.4.9 item 6). {@code 0} means every <b>ground</b> template 1-9 only: the plots
	 * DESIGN 36 extensions use are ground coordinates, so they never reach the Awan family (which places the DESIGN 36
	 * components with the same {@code sx_*} ids and anchors itself).
	 */
	public static final int SKY_ALL = -1;

	/** One extension: {@code owner} names it in logs, {@code template} 1-9, 11-19, 0 (every ground) or {@link #SKY_ALL}. */
	private record Extension(String owner, int template, Consumer<TemplateSpec.Builder> adder) {
	}

	private static final List<Extension> EXTENSIONS = new ArrayList<>();
	private static volatile boolean frozen;

	/**
	 * Adds {@code adder} to template {@code template} (1-9 or 11-19; 0 for all nine ground templates; {@link #SKY_ALL}
	 * for all nine sky templates; it then reads {@code builder.template()}). Returns false when refused (too late, bad
	 * template, null adder).
	 */
	public static synchronized boolean register(String owner, int template, Consumer<TemplateSpec.Builder> adder) {
		if (adder == null || template != 0 && template != SKY_ALL && !SectTemplates.validAny(template)) {
			return false;
		}
		if (frozen) {
			Kultivator.LOGGER.warn("Template extension {} registered after the templates were built; ignored", owner);
			return false;
		}
		EXTENSIONS.add(new Extension(owner == null ? "?" : owner, template, adder));
		return true;
	}

	/** Called by {@link TemplateSpec.Builder#build()}: runs the extensions of {@code builder}'s template. */
	static void apply(TemplateSpec.Builder builder) {
		List<Extension> mine;
		synchronized (TemplateExtensions.class) {
			frozen = true;
			mine = List.copyOf(EXTENSIONS);
		}
		for (Extension extension : mine) {
			int wanted = extension.template();
			boolean match = wanted == builder.template() || wanted == 0 && SectTemplates.valid(builder.template())
					|| wanted == SKY_ALL && SectTemplates.isSky(builder.template());
			if (!match) {
				continue;
			}
			try {
				extension.adder().accept(builder);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Template extension {} failed on template {}", extension.owner(), builder.template(), exception);
			}
		}
	}

	private TemplateExtensions() {
	}
}
