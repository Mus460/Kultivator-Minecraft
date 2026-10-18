package dev.kultivator.formasi.spec;

import dev.kultivator.formasi.effect.FormationEffect;

/** Where modules register their effect handlers by id (DESIGN 24.2.2, 24.2.12). */
@FunctionalInterface
public interface EffectSink {
	void effect(String id, FormationEffect effect);
}
