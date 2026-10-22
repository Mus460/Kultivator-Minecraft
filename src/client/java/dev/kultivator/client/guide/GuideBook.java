package dev.kultivator.client.guide;

import dev.kultivator.data.Realm;
import dev.kultivator.guide.GuideChapters;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's read-only view of the shared {@link GuideChapters} list: the chapters in display order
 * ({@link GuideChapters#displayOrder()}, so "Bab N" is the N-th row of the book), each with its steps (global index in
 * GuideChapters order, which is the bit index of the {@code GuideSync} bitsets), the lang keys
 * {@code kultivator.guide.<id>.title} / {@code .body}, the optional hint command, whether the step is ticked by hand (no
 * automatic check) and whether it is a side goal that never holds the chapter. Built once, on first use (never in a static
 * initializer of a screen).
 * <p>
 * Every {@code chapter} argument of this class, of {@link GuideClient} and of {@link GuideScreen} is a display position
 * (0-based); {@link Chapter#index()} is the save index, used only for lang keys and the server's current chapter.
 */
public final class GuideBook {

	/** One step of the guide. */
	public record Step(int index, String id, int chapter, String titleKey, String bodyKey, @Nullable String hint, boolean manual,
			boolean optional) {

		/** True for a step that holds the current chapter until it is done (checked automatically, not optional). */
		public boolean core() {
			return !manual && !optional;
		}
	}

	/** One chapter: its 0-based save index, its 0-based display position, its steps in order and the realm it is for. */
	public record Chapter(int index, int position, List<Step> steps, @Nullable String minRealm, boolean needsHall) {
		/** The chapter's name from the lang files, or "Bab N" when it has none. */
		public Component title() {
			return chapterTitle(position);
		}

		/** The chapter's one-line summary, or null when the lang files have none. */
		public @Nullable Component desc() {
			String key = GuideChapters.chapter(index).descKey();
			return Language.getInstance().has(key) ? Component.translatable(key) : null;
		}

		/** The realm the chapter is meant for, or null when it is for everyone. */
		public @Nullable Realm realm() {
			return minRealm == null ? null : Realm.byId(minRealm);
		}
	}

	private static @Nullable List<Chapter> chapters;
	private static @Nullable List<Step> steps;
	private static int[] positions;

	private static void build() {
		if (chapters != null) {
			return;
		}
		List<Step> all = new ArrayList<>();
		List<Chapter> list = new ArrayList<>();
		int[] where = new int[GuideChapters.CHAPTER_COUNT];
		for (GuideChapters.Chapter chapter : GuideChapters.displayOrder()) {
			int position = list.size();
			List<Step> own = new ArrayList<>();
			for (GuideChapters.Step step : chapter.steps()) {
				Step view = new Step(step.index(), step.id(), position, step.titleKey(), step.bodyKey(), step.hint(), step.manual(), step.optional());
				own.add(view);
				all.add(view);
			}
			String requires = chapter.requires();
			boolean hall = requires != null && (requires.equals("hall") || requires.startsWith("hall_level") || requires.contains("tier:"));
			list.add(new Chapter(chapter.index(), position, List.copyOf(own), chapter.minRealm(), hall));
			if (chapter.index() >= 0 && chapter.index() < where.length) {
				where[chapter.index()] = position;
			}
		}
		all.sort((a, b) -> Integer.compare(a.index(), b.index()));
		steps = List.copyOf(all);
		positions = where;
		chapters = List.copyOf(list);
	}

	public static int chapterCount() {
		build();
		return chapters.size();
	}

	/** The chapter at display {@code position}, clamped into range (an empty chapter when there are none). */
	public static Chapter chapter(int position) {
		build();
		if (chapters.isEmpty()) {
			return new Chapter(0, 0, List.of(), null, false);
		}
		return chapters.get(Math.max(0, Math.min(chapters.size() - 1, position)));
	}

	/** The display position of save index {@code index} (the server's current chapter); the last row when out of range. */
	public static int positionOf(int index) {
		build();
		if (index < 0 || index >= positions.length) {
			return Math.max(0, chapters.size() - 1);
		}
		return positions[index];
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

	/** The name of the chapter at display {@code position} (0-based). */
	static Component chapterTitle(int position) {
		Chapter chapter = chapter(position);
		String key = GuideChapters.chapter(chapter.index()).titleKey();
		if (Language.getInstance().has(key)) {
			return Component.translatable(key);
		}
		return Component.translatable("kultivator.guide_client.chapter.fallback", position + 1);
	}

	/**
	 * Lines another module keeps in its own fragment and the book shows under a step's body, by step id: Kekuatan &amp;
	 * Kultivasi Selaras' note that Immortal Ascension layer 8 opens no new technique (integration audit R7).
	 */
	private static final java.util.Map<String, String> EXTRA_LINES = java.util.Map.of("c10.layer9", "kultivator.kekuatan.guide.ia8");

	/** The body of {@code step}, with its extra line (see {@link #EXTRA_LINES}) when the lang files have it. */
	static Component body(Step step) {
		Component body = text(step.bodyKey(), "");
		String extra = EXTRA_LINES.get(step.id());
		if (extra == null || !Language.getInstance().has(extra)) {
			return body;
		}
		return Component.empty().append(body).append("\n").append(Component.translatable(extra));
	}

	/** A translated text, or {@code fallback} as plain text when the lang files do not have {@code key}. */
	static Component text(String key, String fallback) {
		return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(fallback);
	}

	private GuideBook() {
	}
}
