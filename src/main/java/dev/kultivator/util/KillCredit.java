package dev.kultivator.util;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.disciple.DiscipleEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.jetbrains.annotations.Nullable;

/**
 * Who earned a kill (DESIGN 19.2.5): the player who struck it (directly, with a technique or with a projectile), the
 * owner of the spirit beast or tamed wolf that struck it, or the master of the disciple that struck it. The direct
 * attacker of the killing blow decides; when it gives no credit, the victim's own kill credit (the last attacker that
 * hurt it) is tried. Pure lookup, nothing is changed.
 */
public final class KillCredit {

	/** How the credited player earned the kill. */
	public enum Kind {
		/** The player struck it: directly, with a technique source or with a projectile they own. */
		PLAYER,
		/** The player's spirit beast or tamed wolf struck it. */
		BEAST,
		/** The player's disciple struck it. */
		DISCIPLE
	}

	/** The online player who earns the kill, the entity that dealt it and how. */
	public record Credit(ServerPlayer owner, Entity killer, Kind kind) {
	}

	/** The credit for {@code victim}'s death by {@code source}, or null when no online player earned it (fall, lava, mobs). */
	public static @Nullable Credit resolve(LivingEntity victim, DamageSource source) {
		Credit credit = of(source.getEntity());
		if (credit == null) {
			credit = of(victim.getKillCredit());
		}
		return credit;
	}

	/**
	 * The player who killed {@code victim} themselves: the source's attacker when it is a player, else the victim's kill
	 * credit when that is a player (the rule {@code TechniqueLoot} always used). Beasts and disciples do not count here.
	 */
	public static @Nullable ServerPlayer directPlayer(LivingEntity victim, DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer player) {
			return player;
		}
		return victim.getKillCredit() instanceof ServerPlayer player ? player : null;
	}

	private static @Nullable Credit of(@Nullable Entity attacker) {
		if (attacker == null) {
			return null;
		}
		if (attacker instanceof ServerPlayer player) {
			return new Credit(player, player, Kind.PLAYER);
		}
		if (attacker instanceof DiscipleEntity disciple) {
			ServerPlayer master = disciple.owner();
			return master == null ? null : new Credit(master, disciple, Kind.DISCIPLE);
		}
		BeastData beast = SpiritBeasts.data(attacker);
		if (beast != null) {
			ServerPlayer owner = attacker.level() instanceof ServerLevel level ? level.getServer().getPlayerList().getPlayer(beast.owner()) : null;
			return owner == null ? null : new Credit(owner, attacker, Kind.BEAST);
		}
		if (attacker instanceof Wolf wolf && wolf.isTame() && wolf.getOwner() instanceof ServerPlayer owner) {
			return new Credit(owner, wolf, Kind.BEAST);
		}
		return null;
	}

	private KillCredit() {
	}
}
