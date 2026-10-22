package dev.kultivator.client.sekte;

import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.sekte.SekteIds;
import net.minecraft.client.model.player.PlayerModel;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The pose and prop hook of Sekte yang Hidup's disciple activities 13-24 ({@code SekteIds.ACT_*}, DESIGN 36.3.3,
 * contract C36.L6). {@code DutyPoses.apply} blends a registered pose in and out exactly like the four duty poses (only
 * while the disciple stands still and calm, never seated, walking, fighting or casting) and {@code DiscipleHeldItemLayer}
 * draws the registered props (FormModels pieces, {@code assets/kultivator/items/fx/<piece>.json}) while the pose is mostly
 * on. Each activity has one owner module, which registers from its client init; registering an id twice replaces it.
 */
public final class SektePoses {

	/** A pose: lerp the model parts toward the target by {@code s} (0-1 blend), {@code t} the age in ticks. */
	@FunctionalInterface
	public interface Pose {
		void apply(PlayerModel model, DiscipleRenderState state, float s, float t);
	}

	/**
	 * A prop in a hand: {@code piece} the FormModels piece id, {@code left} which hand, {@code scale} and {@code pitch}
	 * (degrees) as in {@code DiscipleHeldItemLayer.inHand}.
	 */
	public record Prop(String piece, boolean left, float scale, float pitch) {
	}

	private static final int SIZE = SekteIds.ACT_LAST - SekteIds.ACT_FIRST + 1;
	private static final Pose[] POSES = new Pose[SIZE];
	@SuppressWarnings("unchecked")
	private static final List<Prop>[] PROPS = new List[SIZE];

	/** Registers the pose (and props, may be empty) of activity {@code activity} 13-24. */
	public static synchronized void register(int activity, Pose pose, List<Prop> props) {
		int i = activity - SekteIds.ACT_FIRST;
		if (i < 0 || i >= SIZE) {
			throw new IllegalArgumentException("Sekte activity out of range: " + activity);
		}
		POSES[i] = pose;
		PROPS[i] = props == null ? List.of() : List.copyOf(props);
	}

	/** True when {@code activity} is a Sekte activity with a registered pose. */
	public static boolean handles(int activity) {
		int i = activity - SekteIds.ACT_FIRST;
		return i >= 0 && i < SIZE && POSES[i] != null;
	}

	/** Called by {@code DutyPoses.apply} for a handled activity with the blend already computed. */
	public static void apply(PlayerModel model, DiscipleRenderState state, float s, float t) {
		Pose pose = pose(state.activity);
		if (pose != null) {
			pose.apply(model, state, s, t);
		}
	}

	/** The props of {@code activity} (empty when none). */
	public static List<Prop> props(int activity) {
		int i = activity - SekteIds.ACT_FIRST;
		if (i < 0 || i >= SIZE) {
			return List.of();
		}
		List<Prop> props = PROPS[i];
		return props == null ? List.of() : props;
	}

	private static @Nullable Pose pose(int activity) {
		int i = activity - SekteIds.ACT_FIRST;
		return i >= 0 && i < SIZE ? POSES[i] : null;
	}

	private SektePoses() {
	}
}
