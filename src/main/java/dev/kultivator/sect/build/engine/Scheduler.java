package dev.kultivator.sect.build.engine;

import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.blueprint.Component;
import dev.kultivator.sect.build.blueprint.TemplateSpec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Placement order (DESIGN 21.9): tiers ascending, tier t+1 only when every tier-t component is DONE or SKIPPED; inside a
 * tier the spec's order (axis path, axis priority south to north, side axes inside out, boundary clockwise from the gate,
 * gardens and water, the ornament last) with the owner's {@code prio} first. Components added to already finished tiers
 * by a newer plan version ("Tambahan") are built first and never lower the tier. Waiting (blocked) and suspended
 * components are skipped here; the engine reopens them on its own clock.
 */
final class Scheduler {

	/** The next component to open, or null when nothing can open now. */
	static @Nullable Component next(Site site) {
		if (site.foot) {
			// A Mountain Foot (DESIGN 37.3.6) keeps what stands; it is never built further.
			return null;
		}
		TemplateSpec spec = site.spec();
		int limit = site.testTarget >= 0 ? Math.min(SectTemplates.TIERS, site.testTarget) : SectTemplates.TIERS;
		if (site.sky && site.tethered) {
			// Sekte Langit (DESIGN 37.3.5): a tethered sky site builds tier 1 only, then waits for the Ascension.
			limit = Math.min(limit, dev.kultivator.sect.build.flow.LangitPlan.TETHER_MAX_TIER);
		}
		// Tambahan: unfinished components of tiers the site already completed.
		for (Component component : spec.components()) {
			if (component.tier() <= site.tier && component.tier() <= limit && available(site, component)) {
				return component;
			}
		}
		// Patterns requested on Titik Formasi plots (6b, DESIGN 24.6.2 B) are always Tambahan: their plot already stands.
		for (Component component : site.polaComponents()) {
			if (component.tier() <= limit && available(site, component)) {
				return component;
			}
		}
		for (int tier = site.tier + 1; tier <= limit; tier++) {
			List<Component> list = spec.components(tier);
			Component first = null;
			boolean unfinished = false;
			for (Component component : list) {
				if (site.finished(component.id())) {
					continue;
				}
				unfinished = true;
				if (available(site, component)) {
					if (component.id().equals(site.prio)) {
						return component;
					}
					if (first == null) {
						first = component;
					}
				}
			}
			if (first != null) {
				return first;
			}
			if (unfinished) {
				return null;
			}
		}
		return null;
	}

	private static boolean available(Site site, Component component) {
		return !site.finished(component.id()) && !component.id().equals(site.openId) && !site.waiting.containsKey(component.id())
				&& !site.suspended.contains(component.id());
	}

	/**
	 * The highest tier above {@code site.tier} whose components (and all below) are DONE; the tier never falls. A skipped
	 * component lets the queue move on ({@link #next}) but never completes its tier, so skipping cannot raise the hall's
	 * building cap or bits.
	 */
	static int completedTier(Site site) {
		TemplateSpec spec = site.spec();
		int tier = site.tier;
		while (tier < SectTemplates.TIERS) {
			List<Component> list = spec.components(tier + 1);
			if (list.isEmpty()) {
				break;
			}
			boolean all = true;
			for (Component component : list) {
				if (!site.done.contains(component.id())) {
					all = false;
					break;
				}
			}
			if (!all) {
				break;
			}
			tier++;
		}
		return tier;
	}

	/** The highest tier whose components, and all below, are still DONE (deconstruction lowers the tier with it). */
	static int builtTier(Site site) {
		TemplateSpec spec = site.spec();
		int tier = 0;
		while (tier < SectTemplates.TIERS) {
			List<Component> list = spec.components(tier + 1);
			if (list.isEmpty()) {
				break;
			}
			for (Component component : list) {
				if (!site.done.contains(component.id())) {
					return tier;
				}
			}
			tier++;
		}
		return tier;
	}

	/** True when every component of every tier is finished. */
	static boolean complete(Site site) {
		for (Component component : site.components()) {
			if (!site.finished(component.id())) {
				return false;
			}
		}
		return !site.spec().isPlaceholder();
	}

	private Scheduler() {
	}
}
