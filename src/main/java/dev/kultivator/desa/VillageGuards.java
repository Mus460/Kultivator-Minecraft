package dev.kultivator.desa;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pelindung Desa (DESIGN 25.6.7): one disciple of any rank per vassal, kept as a data assignment through
 * {@link Disciples#depart} (no live disciple waiting in an unloaded village). The body appears at the plaque only while the
 * owner is within 64 blocks ({@link Disciples#arrive}), guards the spot there and fights raiders and quest elites; it leaves
 * again when the owner goes. A guard "slain" during a wave event at its plaque is caught at 1 HP and sent to rest
 * (DESIGN 25.2 item 10). Gives +2 loyalty per day and the renown of a repelled raid.
 */
public final class VillageGuards {

	public static final String REASON = "desa";
	public static final double APPEAR_RANGE = 64.0D;
	private static final double LEAVE_RANGE = 80.0D;
	private static final double ASSIGN_RANGE = 16.0D;
	private static final long REST_TICKS = 1200L;

	/** Guards caught by the death guard rest until this game time before they appear again. */
	private static final Map<UUID, Long> RESTING = new HashMap<>();

	// ================================================================== orders

	/** Assigns the owner's nearest own disciple (within 16 blocks of the player) to the village. */
	static boolean assign(MinecraftServer server, UUID owner, Village village, ServerPlayer player) {
		if (village.guard != null) {
			Msg.chat(player, Component.translatable("kultivator.desa.guard.has", village.name, village.guardName).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		DiscipleEntity best = null;
		double bestSq = ASSIGN_RANGE * ASSIGN_RANGE;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.departed() || disciple.level() != player.level() || isGuard(owner, disciple.soulId())
					// not one in the middle of a tribulation, a duel, a bow or a post (the same bar as a sect errand)
					|| !disciple.isAlive() || disciple.isReleasing() || disciple.data().breakingThrough() || DuelRules.inDuel(disciple)
					|| disciple.activity() != DiscipleEntity.ACTIVITY_NONE) {
				continue;
			}
			double distSq = disciple.distanceToSqr(player);
			if (distSq <= bestSq) {
				bestSq = distSq;
				best = disciple;
			}
		}
		if (best == null) {
			Msg.chat(player, Component.translatable("kultivator.desa.guard.none", (int) ASSIGN_RANGE).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		String name = best.getName().getString();
		int realm = best.data().realm().ordinal();
		UUID soul = best.soulId();
		CompoundTag tag = Disciples.depart(best, REASON);
		if (tag.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.desa.guard.busy", name).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		village.guard = soul;
		village.guardTag = tag;
		village.guardRealm = realm;
		village.guardName = name;
		Vassals.changed();
		Desa.dirty(owner);
		Msg.chat(player, Component.translatable("kultivator.desa.guard.assigned", name, village.name).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Brings the village's guard back beside the player. */
	static boolean release(MinecraftServer server, UUID owner, Village village, ServerPlayer player) {
		if (village.guard == null) {
			return false;
		}
		String name = village.guardName;
		if (!village.guardTag.isEmpty()) {
			DiscipleEntity body = Disciples.arrive(player.level(), player.position(), village.guardTag, owner);
			if (body == null && !Disciples.forgotten(server, village.guardTag)) {
				Msg.chat(player, Component.translatable("kultivator.desa.guard.no_room").withStyle(ChatFormatting.YELLOW));
				return false;
			}
		} else {
			DiscipleEntity body = loadedGuard(owner, village.guard);
			if (body == null) {
				Msg.chat(player, Component.translatable("kultivator.desa.guard.fetch", village.name).withStyle(ChatFormatting.YELLOW));
				return false;
			}
			body.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		clearFields(village);
		Vassals.changed();
		Desa.dirty(owner);
		Msg.chat(player, Component.translatable("kultivator.desa.guard.released", name, village.name).withStyle(ChatFormatting.GREEN));
		return true;
	}

	private static void clearFields(Village village) {
		RESTING.remove(village.guard);
		village.guard = null;
		village.guardTag = new CompoundTag();
		village.guardName = "";
		village.guardRealm = 0;
	}

	/** True when the soul guards one of the owner's villages. */
	public static boolean isGuard(UUID owner, UUID soul) {
		Vassals.Owner entry = Vassals.peek(owner);
		if (entry != null) {
			for (Village village : entry.villages) {
				if (soul.equals(village.guard)) {
					return true;
				}
			}
		}
		return false;
	}

	private static @Nullable DiscipleEntity loadedGuard(UUID owner, UUID soul) {
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (soul.equals(disciple.soulId())) {
				return disciple;
			}
		}
		return null;
	}

	// ================================================================== presence

	/** Every 20 ticks per owner: the guard appears near the owner, leaves when the owner goes, and keeps its post. */
	static void tick(MinecraftServer server, UUID owner, Village village, @Nullable ServerPlayer player) {
		if (village.guard == null) {
			return;
		}
		ServerLevel level = server.overworld();
		boolean near = player != null && player.level() == level
				&& player.position().distanceToSqr(Vec3.atCenterOf(village.plaque)) <= APPEAR_RANGE * APPEAR_RANGE;
		boolean far = player == null || player.level() != level
				|| player.position().distanceToSqr(Vec3.atCenterOf(village.plaque)) > LEAVE_RANGE * LEAVE_RANGE;
		if (!village.guardTag.isEmpty()) {
			Long rest = RESTING.get(village.guard);
			if (near && village.pledged && (rest == null || level.getGameTime() >= rest) && level.isPositionEntityTicking(village.plaque)) {
				DiscipleEntity body = Disciples.arrive(level, Vec3.atBottomCenterOf(village.plaque.relative(village.plaque.getX() % 2 == 0
						? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST, 2)), village.guardTag, owner);
				if (body != null) {
					RESTING.remove(village.guard);
					village.guardTag = new CompoundTag();
					body.setActivity(DiscipleEntity.ACTIVITY_FORMATION_GUARD, village.plaque);
					body.setActivityFocus(player.blockPosition());
					Vassals.changed();
				} else if (Disciples.forgotten(server, village.guardTag)) {
					// its master forgot it while it guarded: the post is free again
					clearFields(village);
					Vassals.changed();
				}
			}
			return;
		}
		DiscipleEntity body = loadedGuard(owner, village.guard);
		if (body == null) {
			return;
		}
		if (far) {
			store(owner, village, body);
		} else {
			if (body.activity() != DiscipleEntity.ACTIVITY_FORMATION_GUARD) {
				body.setActivity(DiscipleEntity.ACTIVITY_FORMATION_GUARD, village.plaque);
			}
			// it stands at its post (spear, BERDIRI_JAGA) turned to the owner it keeps watch for
			if (player != null) {
				body.setActivityFocus(player.blockPosition());
			}
		}
	}

	/** The owner logs out: every guard body standing at a plaque goes back into data. */
	static void onLeave(UUID owner) {
		Vassals.Owner entry = Vassals.peek(owner);
		if (entry == null) {
			return;
		}
		for (Village village : entry.villages) {
			if (village.guardPresent()) {
				DiscipleEntity body = loadedGuard(owner, village.guard);
				if (body != null) {
					store(owner, village, body);
				}
			}
		}
	}

	private static void store(UUID owner, Village village, DiscipleEntity body) {
		CompoundTag tag = Disciples.depart(body, REASON);
		if (!tag.isEmpty()) {
			village.guardTag = tag;
			village.guardRealm = body.data().realm().ordinal();
			Vassals.changed();
		}
	}

	/** Spine death guard (DESIGN 25.2 item 10): a guard at a plaque under attack is left at 1 HP and sent to rest. */
	static boolean save(DiscipleEntity disciple, DamageSource source) {
		UUID owner = disciple.ownerId();
		if (owner == null) {
			return false;
		}
		Vassals.Owner entry = Vassals.peek(owner);
		if (entry == null) {
			return false;
		}
		for (Village village : entry.villages) {
			if (disciple.soulId().equals(village.guard) && village.guardPresent() && VillageRaids.underSiege(owner, village.id)) {
				MinecraftServer server = Vassals.server();
				store(owner, village, disciple);
				if (server != null) {
					RESTING.put(village.guard, server.overworld().getGameTime() + REST_TICKS);
					ServerPlayer player = server.getPlayerList().getPlayer(owner);
					if (player != null) {
						Msg.chat(player, Component.translatable("kultivator.desa.guard.wounded", village.guardName, village.name).withStyle(ChatFormatting.YELLOW));
					}
				}
				Desa.dirty(owner);
				return true;
			}
		}
		return false;
	}

	/** A guard died under the normal rules (not during a wave event): the post is free again. */
	static void onDiscipleDeath(DiscipleEntity disciple) {
		UUID owner = disciple.ownerId();
		Vassals.Owner entry = owner == null ? null : Vassals.peek(owner);
		if (entry == null) {
			return;
		}
		for (Village village : entry.villages) {
			if (disciple.soulId().equals(village.guard) && village.guardPresent()) {
				clearFields(village);
				Vassals.changed();
				Desa.dirty(owner);
			}
		}
	}

	/** The village is gone: a stored guard waits to come back beside the owner, a standing one simply stops guarding. */
	static void onVillageGone(MinecraftServer server, UUID owner, Village village) {
		if (village.guard == null) {
			return;
		}
		if (!village.guardTag.isEmpty()) {
			Vassals.Owner entry = Vassals.owner(owner);
			if (entry != null) {
				entry.returns.add(village.guardTag.copy());
			}
		} else {
			DiscipleEntity body = loadedGuard(owner, village.guard);
			if (body != null) {
				body.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
		}
		clearFields(village);
		Vassals.changed();
	}

	/** Guards of villages that are gone come back beside the online owner. */
	static void tickReturns(UUID owner, ServerPlayer player) {
		Vassals.Owner entry = Vassals.peek(owner);
		if (entry == null || entry.returns.isEmpty()) {
			return;
		}
		ListTag keep = new ListTag();
		for (Tag tag : entry.returns) {
			if (tag instanceof CompoundTag stored) {
				DiscipleEntity body = Disciples.arrive(player.level(), player.position(), stored, owner);
				if (body == null && !Disciples.forgotten(player.level().getServer(), stored)) {
					keep.add(stored);
				}
			}
		}
		if (keep.size() != entry.returns.size()) {
			entry.returns = keep;
			Vassals.changed();
		}
	}

	static void clear() {
		RESTING.clear();
	}

	private VillageGuards() {
	}
}
