package dev.kultivator.client.epic.film;

import org.jetbrains.annotations.Nullable;

/**
 * One position key of a film shot (DESIGN 30.2.5): at shot fraction {@code t} the camera stands at ({@code x, y, z}) in
 * {@code space} (prop key {@code prop} for {@link FilmSpace#P}); {@code ease} times the segment from this key to the next.
 */
public record FilmKey(float t, FilmSpace space, @Nullable String prop, double x, double y, double z, Ease ease) {

	public FilmKey withEase(Ease next) {
		return new FilmKey(t, space, prop, x, y, z, next);
	}
}
