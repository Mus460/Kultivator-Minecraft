package dev.kultivator.technique.spec.run;

import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Training dummies (DESIGN 22.16.2): husks with no AI, silent, never breaking doors, tagged {@code kultivator.dummy} and
 * {@code NoGrief.TAG}, never saved (refused in ALLOW_LOAD), 1,024 health and undying (refilled; an ALLOW_DEATH refusal),
 * the last raw and final damage on the name tag. {@code bos} adds {@code kultivator.dummy_boss}, so the boss caps apply.
 * No mastery, kill credit, feats or comprehension. Removed after 5 minutes, on {@code uji boneka hapus} or when the owner
 * leaves.
 */
public final class Dummies {

	public static final String TAG = "kultivator.dummy";
	public static final int MAX = 6;
	public static final float HEALTH = 1024.0F;
	public static final int LIFETIME = 20 * 60 * 5;

	private record Dummy(UUID owner, long until, ServerLevel level) {
	}

	private static final Map<UUID, Dummy> DUMMIES = new HashMap<>();

	static void init() {
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD || !entity.entityTags().contains(TAG));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (isDummy(entity)) {
				entity.setHealth(entity.getMaxHealth());
				return false;
			}
			return true;
		});
		ServerPlayerEvents.LEAVE.register(player -> removeOf(player.getUUID(), player.level().getServer()));
		ServerTickEvents.END_SERVER_TICK.register(Dummies::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID id : List.copyOf(DUMMIES.keySet())) {
				remove(server, id);
			}
		});
	}

	public static boolean isDummy(Entity entity) {
		return entity.entityTags().contains(TAG);
	}

	/** Spawns up to {@code count} (≤ 6) dummies in front of the player; returns how many came. */
	public static int spawn(ServerPlayer player, int count, boolean boss) {
		ServerLevel level = player.level();
		Vec3 flat = player.getViewVector(1.0F).horizontal();
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
		int made = 0;
		for (int i = 0; i < Math.min(MAX, Math.max(1, count)); i++) {
			Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
			if (husk == null) {
				break;
			}
			Vec3 at = player.position().add(flat.scale(6.0D)).add(side.scale((i - (count - 1) / 2.0D) * 2.5D));
			Vec3 spot = Targeting.safeSpot(level, husk, at);
			if (spot == null) {
				continue;
			}
			husk.snapTo(spot.x, spot.y, spot.z, player.getYRot() + 180.0F, 0.0F);
			husk.setNoAi(true);
			husk.setSilent(true);
			husk.setCanBreakDoors(false);
			husk.setCanPickUpLoot(false);
			husk.addTag(TAG);
			husk.addTag(NoGrief.TAG);
			if (boss) {
				husk.addTag(TechniquePower.TAG_DUMMY_BOSS);
			}
			AttributeInstance health = husk.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(HEALTH);
			}
			husk.setHealth(HEALTH);
			husk.setCustomName(Component.translatable(boss ? "kultivator.teknik.dummy.name_boss" : "kultivator.teknik.dummy.name_idle"));
			husk.setCustomNameVisible(true);
			if (level.addFreshEntity(husk)) {
				DUMMIES.put(husk.getUUID(), new Dummy(player.getUUID(), level.getGameTime() + LIFETIME, level));
				made++;
			}
		}
		return made;
	}

	/** Shows the last raw (before the clamps) and final damage on the dummy's name tag. */
	static void record(LivingEntity dummy, double raw, double dealt) {
		dummy.setCustomName(Component.translatable(dummy.entityTags().contains(TechniquePower.TAG_DUMMY_BOSS) ? "kultivator.teknik.dummy.name_boss_hit"
				: "kultivator.teknik.dummy.name_hit", Msg.number(raw), Msg.number(Math.max(0.0D, dealt))));
	}

	/** Removes the player's dummies; returns how many. */
	public static int removeOf(UUID owner, MinecraftServer server) {
		int count = 0;
		for (Map.Entry<UUID, Dummy> entry : List.copyOf(DUMMIES.entrySet())) {
			if (entry.getValue().owner().equals(owner)) {
				remove(server, entry.getKey());
				count++;
			}
		}
		return count;
	}

	private static void remove(MinecraftServer server, UUID id) {
		Dummy dummy = DUMMIES.remove(id);
		if (dummy == null) {
			return;
		}
		Entity entity = dummy.level().getEntity(id);
		if (entity != null) {
			entity.discard();
		}
	}

	private static void tick(MinecraftServer server) {
		if (DUMMIES.isEmpty() || server.getTickCount() % 10 != 0) {
			return;
		}
		for (Map.Entry<UUID, Dummy> entry : List.copyOf(DUMMIES.entrySet())) {
			Dummy dummy = entry.getValue();
			Entity entity = dummy.level().getEntity(entry.getKey());
			if (entity == null || !entity.isAlive() || dummy.level().getGameTime() >= dummy.until()
					|| server.getPlayerList().getPlayer(dummy.owner()) == null) {
				remove(server, entry.getKey());
				continue;
			}
			if (entity instanceof LivingEntity living && living.getHealth() < living.getMaxHealth()) {
				living.setHealth(living.getMaxHealth());
			}
		}
	}

	static int count() {
		return DUMMIES.size();
	}

	private Dummies() {
	}
}
