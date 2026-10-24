package dev.kultivator.dunia.tempat.tema.bersama;

/**
 * The synced animation state every themed-town entity exposes to its renderer (38TV-M reads only these getters; it never
 * changes behaviour). Implemented by {@link TemaMekanismeEntity} (mechanisms, boats, gates) and by the three creatures
 * ({@code UntaKafilahEntity}, {@code KalajengkingRajaEntity}, {@code KeraGunungEntity}).
 * <p>
 * Meaning per class: {@link #varian()} the model/texture variant (constants {@code VARIAN_*} of the class),
 * {@link #keadaan()} the current state ({@code KEADAAN_*}), {@link #mulai()} the game tick (low 31 bits of
 * {@code level.getGameTime()}) when that state began, so the client derives the phase of a one-shot animation as
 * {@code (gameTime & 0x7fffffff) - mulai + partial} without per-tick packets, and {@link #nilai()} one free 0..1 (or
 * stage) number the class documents (sail furl, build stage, cable progress, rope length).
 */
public interface TemaAnimasi {

	/** Model/texture variant; 0 = the base look. */
	int varian();

	/** Current state; 0 = idle. */
	int keadaan();

	/** Low 31 bits of the game time when {@link #keadaan()} last changed. */
	int mulai();

	/** The class-defined free value (progress 0..1 or a stage number). */
	float nilai();

	/** Ticks since the state began, at {@code gameTime} (non-negative). */
	default int umurKeadaan(long gameTime) {
		int now = (int) (gameTime & 0x7fffffffL);
		int age = now - mulai();
		return age < 0 ? age + Integer.MAX_VALUE : age;
	}
}
