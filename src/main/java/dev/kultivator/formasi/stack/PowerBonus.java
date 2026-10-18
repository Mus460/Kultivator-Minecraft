package dev.kultivator.formasi.stack;

import dev.kultivator.formasi.core.CoreView;

/** An amplifier term A of power P (DESIGN 24.2.9); module B registers A1 (+0.15) and A2 (+0.03 × N). */
@FunctionalInterface
public interface PowerBonus {
	double bonus(CoreView target);
}
