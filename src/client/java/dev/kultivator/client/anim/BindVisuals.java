package dev.kultivator.client.anim;

import dev.kultivator.technique.spec.run.BindKind;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What a bind does to the bound body itself (DESIGN 27.4, polish plan S21; the chains, vines and glyphs around it are
 * BindFx and ArtModels): frost, stopped time, a seal and chains freeze the walk cycle and the idle animation at the
 * moment the bind took hold; frost also shivers (the vanilla frozen shake) and tints the body ice blue, stopped time
 * greys it, a seal pulses it red-violet, a karmic link gives it a faint red cast and sleep a dim blue. Read by the
 * LivingEntityRenderer mixin (extract TAIL, model tint RETURN).
 */
public final class BindVisuals {

	public static final RenderStateDataKey<Integer> TINT = RenderStateDataKey.create(() -> "kultivator:bind_tint");

	/** Per entity: the frozen age and walk position (the first frame the freeze was seen). */
	private static final Map<Integer, float[]> FROZEN = new HashMap<>();

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		FabricRenderState data = (FabricRenderState) state;
		List<ClientBinds.Bind> binds = ClientBinds.of(entity.getId());
		if (binds.isEmpty()) {
			if (!FROZEN.isEmpty()) {
				FROZEN.remove(entity.getId());
			}
			// render states are fresh every frame: no tint stored means none (a reused state is reset)
			if (data.getData(TINT) != null) {
				data.setData(TINT, -1);
			}
			return;
		}
		int tint = -1;
		boolean freeze = false;
		boolean shiver = false;
		float age = entity.tickCount + partialTicks;
		for (ClientBinds.Bind bind : binds) {
			float in = Mth.clamp(bind.age() / 4.0F, 0.0F, 1.0F);
			float out = Mth.clamp(bind.ticks() / 6.0F, 0.0F, 1.0F);
			float s = Math.min(in, out);
			switch (bind.kind()) {
				case FREEZE -> {
					freeze = true;
					shiver = true;
					tint = combine(tint, lerpTint(s, 0xFFB8E8FF));
				}
				case TIME_STOP -> {
					freeze = true;
					tint = combine(tint, lerpTint(s, 0xFFA8A8B4));
				}
				case SEAL -> {
					freeze = true;
					float pulse = 0.5F + 0.5F * Mth.sin(age * 0.35F);
					tint = combine(tint, lerpTint(s, ARGB.color(255, 255, (int) Mth.lerp(pulse, 150.0F, 200.0F), (int) Mth.lerp(pulse, 190.0F, 230.0F))));
				}
				case CHAINS -> {
					freeze = true;
					tint = combine(tint, lerpTint(s * 0.6F, 0xFFFFE8B0));
				}
				case KARMA_LINK -> tint = combine(tint, lerpTint(s * 0.5F, 0xFFFFB0B0));
				case SLEEP -> tint = combine(tint, lerpTint(s * 0.5F, 0xFFC8D0FF));
				case ROOT_VINE -> tint = combine(tint, lerpTint(s * 0.35F, 0xFFC8FFC8));
				default -> {
				}
			}
		}
		if (freeze) {
			float[] frozen = FROZEN.computeIfAbsent(entity.getId(), id -> new float[] {state.ageInTicks, state.walkAnimationPos});
			state.ageInTicks = frozen[0];
			state.walkAnimationPos = frozen[1];
			state.walkAnimationSpeed = 0.0F;
			if (shiver) {
				state.isFullyFrozen = true;
			}
		} else {
			FROZEN.remove(entity.getId());
		}
		if (tint != -1 || data.getData(TINT) != null) {
			data.setData(TINT, tint);
		}
	}

	/** The bind tint stored in a render state (-1 = none). */
	public static int tint(LivingEntityRenderState state) {
		Integer tint = ((FabricRenderState) state).getDataOrDefault(TINT, -1);
		return tint == null ? -1 : tint;
	}

	private static int lerpTint(float s, int argb) {
		s = Mth.clamp(s, 0.0F, 1.0F);
		return ARGB.color(255, (int) Mth.lerp(s, 255.0F, ARGB.red(argb)), (int) Mth.lerp(s, 255.0F, ARGB.green(argb)),
				(int) Mth.lerp(s, 255.0F, ARGB.blue(argb)));
	}

	private static int combine(int a, int b) {
		return a == -1 ? b : ARGB.multiply(a, b);
	}

	static void prune(@Nullable ClientLevel level) {
		if (level == null) {
			FROZEN.clear();
			return;
		}
		for (Iterator<Integer> it = FROZEN.keySet().iterator(); it.hasNext(); ) {
			int id = it.next();
			Entity entity = level.getEntity(id);
			if (entity == null || entity.isRemoved() || !ClientBinds.frozen(id)) {
				it.remove();
			}
		}
	}

	static void clear() {
		FROZEN.clear();
	}

	private BindVisuals() {
	}
}
