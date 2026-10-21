package dev.kultivator.client.dunia;

import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.epic.EpicIds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * World moments of the Dunia modules as 7b scenes (DESIGN 34.4.9, contract C34.S5): a treasure birth, a seal break, a
 * subdued dragon, a beast evolution, an arrival in a dimension, a ship launch, a sky event, a tower milestone. Each key
 * has a cooldown (default 60 s) so a moment never stacks; shots go through {@link CinematicDirector#shoot}, which applies
 * ShotGuard (no shot in real danger), Kamera sinematik Penuh / Singkat / Mati and the 4 s rule of the caller; nothing
 * starts while a Film Spektakuler holds the camera. Title cards obey "Kartu judul" ({@link TitleCard#show}).
 */
public final class DuniaScenes {

	public static final int BEAT_TREASURE = 40;
	public static final int BEAT_SEAL = 41;
	public static final int BEAT_DRAGON = 42;
	public static final int BEAT_EVOLVE = 43;
	public static final int BEAT_ARRIVE = 44;
	public static final int BEAT_SHIP = 45;
	public static final int BEAT_SKY = 46;
	public static final int BEAT_TOWER = 47;
	public static final int DEFAULT_COOLDOWN = 1200;
	/** A shot of a world moment lasts at most this long (rule V4). */
	public static final float MAX_SHOT_SECONDS = 4.0F;

	private static final Map<String, Integer> BEATS = new HashMap<>();
	private static final Map<String, Integer> COOLDOWNS = new HashMap<>();
	private static final Map<String, Long> LAST = new HashMap<>();

	static {
		BEATS.put("harta", BEAT_TREASURE);
		BEATS.put("segel", BEAT_SEAL);
		BEATS.put("naga", BEAT_DRAGON);
		BEATS.put("evolusi", BEAT_EVOLVE);
		BEATS.put("tiba", BEAT_ARRIVE);
		BEATS.put("kapal", BEAT_SHIP);
		BEATS.put("langit", BEAT_SKY);
		BEATS.put("menara", BEAT_TOWER);
	}

	/**
	 * A scene for world moment {@code key} at {@code pos}, or null (no level, the key's cooldown, a film running). The key
	 * names the beat by its first part ({@code "harta"}, {@code "segel/pecah"}, {@code "tiba/laut"}, ...; unknown ones use
	 * beat 40); the cooldown counts per full key. The scene ends by itself after {@code lifeTicks}.
	 */
	public static @Nullable Scene world(String key, Vec3 pos, int seed, int lifeTicks, boolean own) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || key == null || CinematicDirector.filmShooting()) {
			return null;
		}
		long now = minecraft.level.getGameTime();
		Long last = LAST.get(key);
		int cooldown = COOLDOWNS.getOrDefault(key, DEFAULT_COOLDOWN);
		if (last != null && now - last < cooldown && now >= last) {
			return null;
		}
		LAST.put(key, now);
		String head = key.contains("/") ? key.substring(0, key.indexOf('/')) : key;
		int beat = BEATS.getOrDefault(head, BEAT_TREASURE);
		return EpicDirector.worldScene(beat, pos, seed, Math.max(20, lifeTicks), own);
	}

	/** Sets the cooldown of {@code key} in ticks (0 = none). */
	public static void cooldown(String key, int ticks) {
		COOLDOWNS.put(key, Math.max(0, ticks));
	}

	/** Forgets every cooldown (disconnect, tests). */
	public static void reset() {
		LAST.clear();
	}

	/** A safe-beat shot of the scene after {@code delay} ticks, kept within {@link #MAX_SHOT_SECONDS}. */
	public static void shot(Scene scene, int delay, Supplier<Shots.Shot> shot) {
		if (scene == null || !scene.own) {
			return;
		}
		scene.at(Math.max(0, delay), () -> CinematicDirector.shoot(scene, shot.get(), CinematicDirector.KIND_EXTRA, scene.priority, true));
	}

	/** An orbit shot (Penuh: up to 4 s; Singkat: the director shortens it; Mati: nothing). */
	public static void orbit(Scene scene, int delay, float radius, float height, float degrees, float seconds) {
		float s = Math.min(MAX_SHOT_SECONDS, Math.max(0.5F, seconds));
		shot(scene, delay, () -> Shots.orbit(radius, height, degrees, s));
	}

	/** A pull-back shot (an arrival, a launch). */
	public static void pullback(Scene scene, int delay, float r0, float r1, float h0, float h1, float seconds) {
		float s = Math.min(MAX_SHOT_SECONDS, Math.max(0.5F, seconds));
		shot(scene, delay, () -> Shots.pullback(r0, r1, h0, h1, s));
	}

	/**
	 * The moment's card: a FULL card with Kamera Penuh, a STAMP with Singkat and Mati (cards still obey "Kartu judul");
	 * only for one's own scene.
	 */
	public static void card(Scene scene, int delay, Component title, @Nullable Component sub, String seal, int sealRgb) {
		if (scene == null || !scene.own) {
			return;
		}
		scene.at(Math.max(0, delay), () -> {
			if (CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
				TitleCard.Card card = TitleCard.full(title, sub, seal, sealRgb, 70);
				card.safeBeat(scene.has(EpicIds.FLAG_SAFE));
				TitleCard.show(card);
			} else {
				TitleCard.show(TitleCard.stamp(title, seal, sealRgb));
			}
		});
	}

	/** A banner line (no camera, no seal), e.g. "Kota Batu Giok". */
	public static void banner(Component title) {
		TitleCard.show(TitleCard.banner(title));
	}

	private DuniaScenes() {
	}
}
