package dev.kultivator.formasi.effect;

import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What a handler sees of one running core during a call (DESIGN 24.2.12). Implemented by module A
 * ({@code formasi/effect/CoreContextImpl}); every effect on an entity goes through {@link FormationTargets#mayAffect}.
 * Nothing here ever changes a block.
 */
public interface CoreContext {

	ServerLevel level();

	CoreView core();

	FormationSpec spec();

	UUID owner();

	/** The owner when online in any dimension, else null (offline rules of 24.2.6). */
	@Nullable ServerPlayer ownerOnline();

	/** The owner's hall level (or the pretended level of {@code uji formasi aula}). */
	int hallLevel();

	/** The effective radius after amplifiers, capped at {@code Stacking.RADIUS_CAP}. */
	double radius();

	/** The eye's centre. */
	Vec3 centre();

	/** Power P of 24.2.9. */
	double power();

	/** Server game time. */
	long now();

	RandomSource random();

	/** The owner, the owner's sect mates and disciples (24.2.6). */
	boolean isMember(Entity entity);

	/** Intruders inside the field from the shared snapshot, nearest first, at most {@code max} (≤ 24). */
	List<Mob> hostiles(int max);

	/** Members and their pets inside the field, at most {@code max} (≤ 16). */
	List<LivingEntity> allies(int max);

	/** Formation damage through the kill ledger, the per-mob caps and {@code kultivator:formation}; true when dealt. */
	boolean hurt(Mob mob, float amount);

	/** Pushes {@code mob} away from {@code from} (never a mob inside an active Pelindung). */
	void push(Mob mob, Vec3 from, double strength);

	/** Roots {@code mob} through {@code ExternalControl} (one root per mob per 15 s across formations). */
	boolean root(Mob mob, int ticks);

	/** Walks {@code mob} toward {@code waypoint} once (navigation, never no-AI). */
	boolean steer(Mob mob, Vec3 waypoint);

	/** Clears {@code mob}'s target. */
	void loseTarget(Mob mob);

	/** A one-off {@code FxType.FORMASI} event: {@code sub} from {@code FormasiFxIds}. */
	void fx(int sub, Vec3 at, int b, float f);

	/** An alarm of this core (throttled per 24.5), with an optional direction source. */
	void alert(AlarmKind kind, Component text, @Nullable Vec3 from);

	/** Spends extra Daya (a transport trip); false when it cannot be paid (nothing is spent). */
	boolean spend(double daya);

	/** Every core of the same owner (views). */
	List<CoreView> ownerCores();
}
