package dev.kultivator.formasi.own;

import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Ownership and membership of sect formations (DESIGN 24.2.6). Members of a core are its owner, the owner's {@code /sect}
 * mates and the owner's disciples; everyone else is a stranger. Pets and spirit beasts of members are allies (healing)
 * but never members.
 */
public final class Ownership {

	public static final int KIND_PERSONAL = 0;
	public static final int KIND_SECT = 1;

	/** The owner of the active hall territory containing {@code pos}, or null outside every territory. */
	public static @Nullable UUID territoryOwnerAt(ServerLevel level, Vec3 pos) {
		MinecraftServer server = level.getServer();
		HallData data = HallData.get(server);
		if (data.isEmpty()) {
			return null;
		}
		for (UUID owner : data.owners()) {
			if (Halls.inTerritory(owner, level, pos)) {
				return owner;
			}
		}
		return null;
	}

	/** True when {@code other} is {@code owner} or in the owner's {@code /sect}. */
	public static boolean memberUuid(MinecraftServer server, UUID owner, @Nullable UUID other) {
		if (other == null) {
			return false;
		}
		if (other.equals(owner)) {
			return true;
		}
		Sect sect = SectData.get(server).sectOf(owner);
		return sect != null && sect.isMember(other);
	}

	/** True when {@code a} and {@code b} are different players of one sect. */
	public static boolean mates(MinecraftServer server, UUID a, UUID b) {
		if (a.equals(b)) {
			return false;
		}
		Sect sect = SectData.get(server).sectOf(a);
		return sect != null && sect.isMember(b);
	}

	/** True for the owner, a sect mate (players) or one of the owner's disciples (role DISCIPLE). */
	public static boolean isMember(MinecraftServer server, UUID owner, @Nullable Entity entity) {
		if (entity instanceof Player player) {
			return memberUuid(server, owner, player.getUUID());
		}
		if (entity instanceof DiscipleEntity disciple) {
			return disciple.role() == DiscipleEntity.Role.DISCIPLE && owner.equals(disciple.ownerId());
		}
		return false;
	}

	/** True for a tamed pet or spirit beast whose owner is a member of {@code owner}'s cores. */
	public static boolean petOfMember(MinecraftServer server, UUID owner, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity) || entity instanceof Player || entity instanceof DiscipleEntity) {
			return false;
		}
		if (entity instanceof OwnableEntity ownable) {
			EntityReference<LivingEntity> reference = ownable.getOwnerReference();
			if (reference != null && memberUuid(server, owner, reference.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** True for a spirit beast (never touched by hostile effects, healed by M4 when its owner is a member). */
	public static boolean spiritBeast(Entity entity) {
		return SpiritBeasts.isSpiritBeast(entity);
	}

	private Ownership() {
	}
}
