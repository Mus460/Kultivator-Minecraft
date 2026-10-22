package dev.kultivator.client.epic;

import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.epic.EpicIds;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The 7b cinematic hook of Sekte yang Hidup (DESIGN 36.3.7, contract C36.L7). The server sends
 * {@code EpicMoments.send(level, EpicIds.EXTRA, beat, subject, viewer, pos, at, a, b, f, s, flags)} with a beat in the
 * module's own range ({@code EpicIds.EXTRA_SEKTE_FIRST + 10 * module index}, {@code sekte.SekteIds.MODULES} order, ten
 * beats each); {@code SceneCatalogCombat.extra} hands every beat of that range here. A module registers a declarative
 * {@link Recipe} per beat from its client init: one camera shot (skipped by every existing guard: Kamera sinematik
 * Penuh/Singkat/Mati, Backspace, any key, real danger), one title card (full card or stamp), optional music, sky grade and
 * flash. All the budgets of the scene system apply (scale, lite for other players, EpicBudget tiers). Nothing here runs
 * while no sekte beat arrives.
 */
public final class SekteScenes {

	/** The camera move of a recipe (centred on the scene's subject, else its position). */
	public enum Move {
		NONE, ORBIT, PUSH, PULLBACK, CRANE_UP, LOW_HERO, STILL
	}

	/** Where the title's first argument comes from. */
	public enum Arg {
		NONE, SUBJECT_NAME, NUMBER_A, NUMBER_B
	}

	/**
	 * One scene: {@code titleKey} (a full lang key, formatted with {@link Arg}), {@code subKey} (nullable, a second line
	 * under a full card), {@code seal} one CJK glyph and its colour, {@code fullCard} (a full card; else a small stamp),
	 * {@code cardTicks}, the {@code move} and its {@code seconds} (at most 6), {@code music} (nullable) with
	 * {@code musicSeconds}, {@code sky} (nullable) grade, {@code flashRgb} (-1 none) and {@code length} (ticks until the
	 * scene ends, 40-200).
	 */
	public record Recipe(String titleKey, Arg arg, @Nullable String subKey, String seal, int sealRgb, boolean fullCard, int cardTicks,
			Move move, float seconds, @Nullable MusicCue.Cue music, int musicSeconds, @Nullable SkyDirector.Grade sky, int flashRgb,
			int length) {

		/** A quiet stamp with a short orbit (the common case). */
		public static Recipe stamp(String titleKey, Arg arg, String seal, int sealRgb) {
			return new Recipe(titleKey, arg, null, seal, sealRgb, false, 50, Move.ORBIT, 3.0F, null, 0, null, -1, 80);
		}
	}

	private static final Map<Integer, Recipe> RECIPES = new HashMap<>();

	/** Registers the recipe of {@code beat} (must lie in the sekte range); a second registration replaces the first. */
	public static synchronized void register(int beat, Recipe recipe) {
		if (beat < EpicIds.EXTRA_SEKTE_FIRST || beat > EpicIds.EXTRA_SEKTE_LAST || recipe == null) {
			throw new IllegalArgumentException("Sekte scene beat out of range: " + beat);
		}
		RECIPES.put(beat, recipe);
	}

	/** Called by {@code SceneCatalogCombat.extra}; false when {@code beat} is not a known sekte beat. */
	static boolean beat(Scene s, int beat, int delay) {
		Recipe recipe;
		synchronized (SekteScenes.class) {
			recipe = RECIPES.get(beat);
		}
		if (recipe == null) {
			return false;
		}
		float seconds = Math.max(1.0F, Math.min(6.0F, recipe.seconds()));
		Supplier<Shots.Shot> shot = switch (recipe.move()) {
			case ORBIT -> () -> Shots.orbit(5.0F, 1.6F, 90.0F, seconds);
			case PUSH -> () -> Shots.push(7.0F, 3.5F, 1.6F, seconds);
			case PULLBACK -> () -> Shots.pullback(3.0F, 9.0F, 1.6F, 4.0F, seconds);
			case CRANE_UP -> () -> Shots.craneUp(4.0F, 1.0F, 8.0F, 6.0F, 5.0F, 25.0F, seconds);
			case LOW_HERO -> () -> Shots.lowHero(4.0F, 0.4F, 1.2F, 1.0F, seconds);
			case STILL -> () -> Shots.still(5.0F, 4.0F, 1.6F, seconds);
			case NONE -> null;
		};
		if (shot != null) {
			SceneCatalog.shot(s, delay, shot, CinematicDirector.KIND_EXTRA, true);
		}
		if (recipe.sky() != null) {
			SceneCatalog.sky(s, delay, recipe.sky(), 0.6F, 0.0F, 20);
			SceneCatalog.skyRelease(s, delay + 40, 30, 30);
		}
		if (recipe.flashRgb() >= 0) {
			SceneCatalog.flash(s, delay + 10, recipe.flashRgb(), 0.35F, 8, 0);
		}
		if (recipe.music() != null) {
			SceneCatalog.music(s, delay, recipe.music(), Math.max(4, Math.min(20, recipe.musicSeconds())));
		}
		Component title = Component.translatable(recipe.titleKey(), argOf(s, recipe.arg()));
		if (recipe.fullCard()) {
			String sub = recipe.subKey();
			SceneCatalog.full(s, delay + 20, title, sub == null ? null : () -> Component.translatable(sub, argOf(s, recipe.arg())),
					recipe.seal(), recipe.sealRgb(), Math.max(30, Math.min(120, recipe.cardTicks())), null);
		} else {
			SceneCatalog.stamp(s, delay + 10, title, recipe.seal(), recipe.sealRgb());
		}
		s.end(delay + Math.max(40, Math.min(200, recipe.length())));
		return true;
	}

	private static Object argOf(Scene s, Arg arg) {
		return switch (arg) {
			case SUBJECT_NAME -> {
				Entity subject = s.subject();
				yield subject != null ? subject.getDisplayName() : Component.literal("");
			}
			case NUMBER_A -> Integer.toString(s.a);
			case NUMBER_B -> Integer.toString(s.b);
			case NONE -> "";
		};
	}

	private SekteScenes() {
	}
}
