package dev.kultivator.mixin.duel;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The goal selector of a mob (DESIGN 35.7.1): the sword duel adds its {@code DuelistGoal} (priority 0, MOVE | LOOK) to
 * every non-player duellist when a session starts and removes it at the end.
 */
@Mixin(Mob.class)
public interface MobGoalSelectorAccessor {

	@Accessor("goalSelector")
	GoalSelector kultivator$goalSelector();
}
