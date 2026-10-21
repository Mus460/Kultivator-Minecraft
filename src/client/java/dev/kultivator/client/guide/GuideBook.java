package dev.kultivator.client.guide;

import dev.kultivator.guide.GuideChapters;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's read-only view of the shared {@link GuideChapters} list: the chapters in play order, each with its steps
 * (global index in GuideChapters order, which is the bit index of the {@code GuideSync} bitsets), the lang keys
 * {@code kultivator.guide.<id>.title} / {@code .body}, the optional hint command and whether the step is ticked by hand
 * (no automatic check). Built once, on first use (never in a static initializer of a screen).
 */
public final class GuideBook {

	/** One step of the guide. */
	public record Step(int index, String id, int chapter, String titleKey, String bodyKey, @Nullable String hint, boolean manual) {
	}

	/** One chapter: its 0-based index and its steps in order. */
	public record Chapter(int index, List<Step> steps) {
		/** The chapter's name from the lang files, or "Bab N" when it has none. */
		public Component title() {
			return chapterTitle(index);
		}

		/** The chapter's one-line summary, or null when the lang files have none. */
		public @Nullable Component desc() {
			String key = GuideChapters.chapter(index).descKey();
			return Language.getInstance().has(key) ? Component.translatable(key) : null;
		}
	}

	private static @Nullable List<Chapter> chapters;
	private static @Nullable List<Step> steps;

	private static void build() {
		if (chapters != null) {
			return;
		}
		List<Step> all = new ArrayList<>();
		List<Chapter> list = new ArrayList<>();
		for (GuideChapters.Chapter chapter : GuideChapters.chapters()) {
			List<Step> own = new ArrayList<>();
			for (GuideChapters.Step step : chapter.steps()) {
				Step view = new Step(step.index(), step.id(), chapter.index(), step.titleKey(), step.bodyKey(), step.hint(), step.manual());
				own.add(view);
				all.add(view);
			}
			list.add(new Chapter(chapter.index(), List.copyOf(own)));
		}
		all.sort((a, b) -> Integer.compare(a.index(), b.index()));
		steps = List.copyOf(all);
		chapters = List.copyOf(list);
	}

	public static int chapterCount() {
		build();
		return chapters.size();
	}

	/** Chapter {@code index}, clamped into range (an empty chapter when there are none). */
	public static Chapter chapter(int index) {
		build();
		if (chapters.isEmpty()) {
			return new Chapter(0, List.of());
		}
		return chapters.get(Math.max(0, Math.min(chapters.size() - 1, index)));
	}

	public static int stepCount() {
		build();
		return steps.size();
	}

	public static @Nullable Step step(int index) {
		build();
		if (index >= 0 && index < steps.size() && steps.get(index).index() == index) {
			return steps.get(index);
		}
		for (Step step : steps) {
			if (step.index() == index) {
				return step;
			}
		}
		return null;
	}

	/** True when the step at {@code index} has no automatic check (the player ticks it). */
	public static boolean isManual(int index) {
		Step step = step(index);
		return step != null && step.manual();
	}

	/** The name of chapter {@code index} (0-based). */
	static Component chapterTitle(int index) {
		String key = GuideChapters.chapter(index).titleKey();
		if (Language.getInstance().has(key)) {
			return Component.translatable(key);
		}
		return Component.translatable("kultivator.guide_client.chapter.fallback", index + 1);
	}

	/** A translated text, or {@code fallback} as plain text when the lang files do not have {@code key}. */
	static Component text(String key, String fallback) {
		return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(fallback);
	}

	private GuideBook() {
	}
}
