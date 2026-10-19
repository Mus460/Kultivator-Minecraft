package dev.kultivator.client.fx.spec;

import dev.kultivator.Kultivator;
import dev.kultivator.client.anim.AnimClient;
import dev.kultivator.client.anim.CastPoses;
import dev.kultivator.client.anim.ClientBinds;
import dev.kultivator.client.anim.ClientForms;
import dev.kultivator.client.anim.PoseAngles;
import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.anim.model.AnimStep;
import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.anim.model.ModelRequest;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Pose;
import dev.kultivator.technique.spec.run.BindKind;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one place M2's effects call the animation and model module (polish plan C3: {@code client.anim.CastPoses},
 * {@code ModelFx}, {@code ClientForms}, {@code ClientBinds}). Every call is guarded: a failure in that module is logged a
 * few times and the effect carries on with its particle stand-in, so presentation never breaks a cast.
 */
public final class AnimBridge {

	private static int warnings;

	/** A synced bind as M2 needs it (who is bound, how, by whom). */
	public record BindView(int entity, BindKind kind, int ticks, int caster, int spec) {
	}

	/** Called once from {@code KultivatorClient}: M3's payload receivers, form layer and pruning. */
	public static void init() {
		try {
			AnimClient.init();
		} catch (RuntimeException | LinkageError exception) {
			warn("init", exception);
		}
	}

	// ================================================================== poses

	/** Feeds one technique event to the cast-pose library (first thing in {@code SpecFx.play}, also END and ABORT). */
	public static void onTechFx(Payloads.TechFx payload, TechniqueSpec spec, @Nullable Entity caster) {
		try {
			CastPoses.onTechFx(payload, spec, caster);
		} catch (RuntimeException | LinkageError exception) {
			warn("pose", exception);
		}
	}

	public static void pose(int entityId, Pose pose, int holdTicks) {
		try {
			CastPoses.start(entityId, pose, holdTicks);
		} catch (RuntimeException | LinkageError exception) {
			warn("pose start", exception);
		}
	}

	public static void pose(int entityId, Pose pose, int holdTicks, float intensity) {
		try {
			CastPoses.start(entityId, pose, holdTicks, intensity);
		} catch (RuntimeException | LinkageError exception) {
			warn("pose start", exception);
		}
	}

	public static void accent(int entityId) {
		try {
			CastPoses.accent(entityId);
		} catch (RuntimeException | LinkageError exception) {
			warn("pose accent", exception);
		}
	}

	public static void stopPose(int entityId) {
		try {
			CastPoses.stop(entityId);
		} catch (RuntimeException | LinkageError exception) {
			warn("pose stop", exception);
		}
	}

	/** The angles of {@code pose} at {@code t01} (giant figures and avatars pose with these), or null. */
	public static @Nullable PoseAngles sample(Pose pose, float t01, float ageTicks) {
		try {
			return CastPoses.sample(pose, t01, ageTicks);
		} catch (RuntimeException | LinkageError exception) {
			warn("pose sample", exception);
			return null;
		}
	}

	// ================================================================== models

	/** True when the 3D model {@code id} exists. */
	public static boolean modelKnown(String id) {
		try {
			return id != null && !id.isEmpty() && ModelFx.known(id);
		} catch (RuntimeException | LinkageError exception) {
			warn("model known", exception);
			return false;
		}
	}

	/** The item stack that shows model {@code id} on a display, or null. */
	public static @Nullable ItemStack modelStack(String id) {
		try {
			return ModelFx.stack(id);
		} catch (RuntimeException | LinkageError exception) {
			warn("model stack", exception);
			return null;
		}
	}

	/** Shows a model prop; null when the budget, the quality or the module refuses. */
	public static @Nullable ModelHandle model(String id, Vec3 pos, float scale, float yaw, float pitch, float roll, int ticks, List<AnimStep> anims,
			int follow, Vec3 followOffset, boolean followYaw, boolean glow, int casterId, FxLayer.Priority priority) {
		try {
			return ModelFx.show(new ModelRequest(id, pos, scale, yaw, pitch, roll, ticks, anims, follow, followOffset, followYaw, glow, casterId, priority));
		} catch (RuntimeException | LinkageError exception) {
			warn("model show", exception);
			return null;
		}
	}

	/** A model with one animation, standing still. */
	public static @Nullable ModelHandle model(String id, Vec3 pos, float scale, float yaw, int ticks, Anim anim, float a, float b, @Nullable Vec3 to,
			int casterId) {
		List<AnimStep> steps = new ArrayList<>(1);
		if (anim != Anim.NONE) {
			steps.add(new AnimStep(anim, 0, Math.max(1, ticks), a, b, to));
		}
		return model(id, pos, scale, yaw, 0.0F, 0.0F, ticks, steps, -1, Vec3.ZERO, false, true, casterId, FxLayer.Priority.CORE);
	}

	public static AnimStep step(Anim kind, int start, int duration, float a, float b, @Nullable Vec3 to) {
		return new AnimStep(kind, start, duration, a, b, to);
	}

	/** Models left this event may still create (0 when the module is missing). */
	public static int modelBudget() {
		try {
			return ModelFx.budgetLeft();
		} catch (RuntimeException | LinkageError exception) {
			warn("model budget", exception);
			return 0;
		}
	}

	/** Ends every model of a caster (ABORT). */
	public static void endModels(int casterId) {
		try {
			ModelFx.endAll(casterId);
		} catch (RuntimeException | LinkageError exception) {
			warn("model end", exception);
		}
	}

	public static void end(@Nullable ModelHandle handle, int fade) {
		if (handle == null) {
			return;
		}
		try {
			handle.end(fade);
		} catch (RuntimeException | LinkageError exception) {
			warn("model handle", exception);
		}
	}

	public static boolean alive(@Nullable ModelHandle handle) {
		if (handle == null) {
			return false;
		}
		try {
			return handle.alive();
		} catch (RuntimeException | LinkageError exception) {
			warn("model handle", exception);
			return false;
		}
	}

	public static void moveTo(@Nullable ModelHandle handle, Vec3 pos, int lerp) {
		if (handle == null) {
			return;
		}
		try {
			handle.moveTo(pos, lerp);
		} catch (RuntimeException | LinkageError exception) {
			warn("model handle", exception);
		}
	}

	public static void rotateTo(@Nullable ModelHandle handle, float yaw, float pitch, float roll, int lerp) {
		if (handle == null) {
			return;
		}
		try {
			handle.rotateTo(yaw, pitch, roll, lerp);
		} catch (RuntimeException | LinkageError exception) {
			warn("model handle", exception);
		}
	}

	public static void play(@Nullable ModelHandle handle, AnimStep step) {
		if (handle == null) {
			return;
		}
		try {
			handle.play(step);
		} catch (RuntimeException | LinkageError exception) {
			warn("model handle", exception);
		}
	}

	/** Swaps the model a handle shows (the prison hand closing). */
	public static void swap(@Nullable ModelHandle handle, String modelId) {
		if (handle == null) {
			return;
		}
		try {
			handle.swap(modelId);
		} catch (RuntimeException | LinkageError exception) {
			warn("model handle", exception);
		}
	}

	public static void scaleTo(@Nullable ModelHandle handle, float scale, int lerp) {
		if (handle == null) {
			return;
		}
		try {
			handle.scaleTo(scale, lerp);
		} catch (RuntimeException | LinkageError exception) {
			warn("model handle", exception);
		}
	}

	// ================================================================== forms and binds

	/** The synced form id of an entity ("" = none). */
	public static String form(int entityId) {
		try {
			var form = ClientForms.of(entityId);
			return form == null || form.id() == null ? "" : form.id();
		} catch (RuntimeException | LinkageError exception) {
			warn("forms", exception);
			return "";
		}
	}

	/** Every live bind (BindFx draws them). */
	public static List<BindView> binds() {
		List<BindView> out = new ArrayList<>();
		try {
			for (var bind : ClientBinds.all()) {
				if (bind == null || bind.kind() == null) {
					continue;
				}
				out.add(new BindView(bind.entity(), bind.kind(), bind.ticks(), bind.caster(), bind.spec()));
			}
		} catch (RuntimeException | LinkageError exception) {
			warn("binds", exception);
		}
		return out;
	}

	private static void warn(String where, Throwable exception) {
		if (warnings++ < 6) {
			Kultivator.LOGGER.warn("Kultivator: animation bridge error in {}", where, exception);
		}
	}

	private AnimBridge() {
	}
}
