package dev.kultivator.client.sosok.gerak;

import dev.kultivator.client.disciple.DiscipleRenderState;
import net.minecraft.world.entity.LivingEntity;

/**
 * A client effect hook fired by a clip's {@code KAIT} event (contract C40.G1): other modules register one with
 * {@link GerakKatalog#daftarKait} (39E: {@code mandiri:asap} the cauldron smoke, {@code mandiri:busur} a sword arc,
 * {@code mandiri:batu_orbit} orbiting spirit stones, {@code mandiri:segel} floating seal paper, {@code mandiri:z} the
 * sleeping "Z"). Called on the render thread during the render-state extraction, at most once per clip cycle and tick,
 * only for bodies at full detail; it must not change the world and should spawn little (respect {@code EffectQuality}).
 */
@FunctionalInterface
public interface KaitGerak {

	/** {@code t} is the clip tick of the event. */
	void picu(LivingEntity entitas, DiscipleRenderState state, float t);
}
