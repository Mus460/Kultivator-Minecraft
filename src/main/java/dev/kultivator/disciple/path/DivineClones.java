package dev.kultivator.disciple.path;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Klon Ilahi, the Divine Clones of the Bintang Abadi path (DESIGN 20.3.19). Sumber: <i>Coiling Dragon</i>: Linley refines
 * divine sparks into Divine Clones that fight beside him.
 * <p>
 * From ★28 (Planet) one clone, from ★46 (Gemintang) two: a role-CLONE copy ({@link Disciples#spawnClone}) with 50% max
 * health and attack and techniques at 50% U, lasting 60 s, cooldown 5 minutes. It comes by itself when a boss is within
 * 24 blocks or the disciple drops below half health in a fight. Never saved, never listed, drawn translucent (renderer).
 */
public final class DivineClones {

	public static final int FIRST_CLONE_STAR = 28;
	public static final int SECOND_CLONE_STAR = 46;
	public static final int LIFETIME_TICKS = 1200;
	public static final int COOLDOWN_TICKS = 6000;
	public static final double STRENGTH = 0.5D;
	public static final double BOSS_RANGE = 24.0D;
	public static final double LOW_HEALTH = 0.5D;

	private static final Identifier HEALTH_ID = Kultivator.id("path_clone_health");
	private static final Identifier ATTACK_ID = Kultivator.id("path_clone_attack");

	private record Clone(DiscipleEntity body, DiscipleEntity source, long until, boolean fading) {
		Clone(DiscipleEntity body, DiscipleEntity source, long until) {
			this(body, source, until, false);
		}
	}

	/** Ticks the fade-out (PathFxIds.CLONE b = 1) runs on the clients before the body goes (DESIGN 27.6). */
	public static final int FADE_TICKS = 10;

	/** Clone entity UUID -> its record. */
	private static final Map<UUID, Clone> CLONES = new HashMap<>();

	static void register() {
		DiscipleHooks.addStats(DivineClones::stats);
		DiscipleHooks.addDamageFactor((entity, target, technique) ->
				entity.role() == DiscipleEntity.Role.CLONE && technique ? STRENGTH : 1.0D);
	}

	/** Clones a disciple with {@code stars} stars may raise at once (0, 1 or 2). */
	public static int allowed(int stars) {
		return stars >= SECOND_CLONE_STAR ? 2 : stars >= FIRST_CLONE_STAR ? 1 : 0;
	}

	private static void stats(DiscipleEntity entity, DiscipleHooks.StatSink sink) {
		if (entity.role() == DiscipleEntity.Role.CLONE) {
			sink.add(Attributes.MAX_HEALTH, HEALTH_ID, -STRENGTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			sink.add(Attributes.ATTACK_DAMAGE, ATTACK_ID, -STRENGTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}

	/** Seconds until the disciple's clones can come again. */
	static int cooldownSeconds(DiscipleEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return 0;
		}
		return DisciplePaths.secondsLeft(level.getServer(), DisciplePaths.tag(entity.data()).getCompoundOrEmpty("cd").getLongOr("clone", 0L));
	}

	/** Once a second in a fight: the clones come when a boss is near or the disciple is badly hurt. */
	static void tick20(DiscipleEntity entity, @Nullable ServerPlayer master) {
		DiscipleData data = entity.data();
		int count = allowed(DisciplePaths.stars(data));
		LivingEntity target = entity.getTarget();
		if (count <= 0 || master == null || target == null || !target.isAlive() || data.breakingThrough() || Calamity.running(entity)
				|| !(entity.level() instanceof ServerLevel level) || cooldownSeconds(entity) > 0 || active(entity) > 0) {
			return;
		}
		boolean hurt = entity.getHealth() < entity.getMaxHealth() * LOW_HEALTH;
		if (!hurt && bossNear(entity, BOSS_RANGE) == null) {
			return;
		}
		summon(entity, level, master, count, target);
	}

	private static void summon(DiscipleEntity source, ServerLevel level, ServerPlayer master, int count, LivingEntity target) {
		long now = DisciplePaths.now(level.getServer());
		int made = 0;
		for (int i = 0; i < count; i++) {
			double angle = source.getRandom().nextDouble() * Math.PI * 2.0D;
			Vec3 near = source.position().add(Math.cos(angle) * 2.0D, 0.0D, Math.sin(angle) * 2.0D);
			if (!level.isLoaded(BlockPos.containing(near))) {
				continue;
			}
			Vec3 spot = Targeting.safeSpot(level, source, near);
			if (spot == null) {
				continue;
			}
			DiscipleEntity clone = Disciples.spawnClone(source, spot);
			if (clone == null) {
				continue;
			}
			clone.refreshSynced();
			clone.setHealth(clone.getMaxHealth());
			if (clone.mayAttack(target)) {
				clone.setTarget(target);
			}
			CLONES.put(clone.getUUID(), new Clone(clone, source, now + LIFETIME_TICKS));
			Fx.send(level, spot, FxType.STAR, PathFxIds.CLONE, 0, 1.0F);
			made++;
		}
		if (made == 0) {
			return;
		}
		DisciplePaths.child(DisciplePaths.section(source.data()), "cd").putLong("clone", now + COOLDOWN_TICKS);
		DisciplePaths.sound(level, source.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.8F);
		DisciplePaths.sound(level, source.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 1.5F);
		Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + "clone.summoned", DisciplePaths.name(source), made)
				.withColor(DisciplePaths.JADE));
	}

	/** The disciple's clones in the world right now. */
	static int active(DiscipleEntity source) {
		int count = 0;
		for (Clone clone : CLONES.values()) {
			if (clone.source() == source && !clone.body().isRemoved()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Every half second: clones fade when their time is up or their source is gone. The fade packet goes out
	 * {@link #FADE_TICKS} before the body is removed, so the clients ramp the clone's alpha down instead of a pop.
	 */
	static void tick(MinecraftServer server) {
		if (CLONES.isEmpty() || server.getTickCount() % FADE_TICKS != 0) {
			return;
		}
		long now = DisciplePaths.now(server);
		for (Map.Entry<UUID, Clone> entry : List.copyOf(CLONES.entrySet())) {
			Clone clone = entry.getValue();
			DiscipleEntity body = clone.body();
			boolean sourceGone = clone.source().isRemoved() || !clone.source().isAlive() || clone.source().level() != body.level();
			if (body.isRemoved() || !body.isAlive()) {
				CLONES.remove(entry.getKey());
				continue;
			}
			if (clone.fading() && now >= clone.until()) {
				CLONES.remove(entry.getKey());
				vanish(body, false);
			} else if (!clone.fading() && (now + FADE_TICKS >= clone.until() || sourceGone)) {
				if (body.level() instanceof ServerLevel level) {
					Fx.send(level, body.position(), FxType.STAR, PathFxIds.CLONE, 1, FADE_TICKS);
				}
				CLONES.put(entry.getKey(), new Clone(body, clone.source(), now + FADE_TICKS, true));
			} else if (body.getTarget() == null && clone.source().getTarget() != null && body.mayAttack(clone.source().getTarget())) {
				body.setTarget(clone.source().getTarget());
			}
		}
	}

	private static void vanish(DiscipleEntity body, boolean fadePacket) {
		if (body.level() instanceof ServerLevel level) {
			if (fadePacket) {
				Fx.send(level, body.position(), FxType.STAR, PathFxIds.CLONE, 1, 1.0F);
			}
			DisciplePaths.sound(level, body.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 1.4F);
		}
		body.discard();
	}

	static void onDeath(LivingEntity victim) {
		if (victim instanceof DiscipleEntity body && body.role() == DiscipleEntity.Role.CLONE) {
			CLONES.remove(body.getUUID());
		}
	}

	/** A vanilla boss (DESIGN 1): Ender Dragon, Wither, Warden, Elder Guardian. */
	static boolean isBoss(@Nullable Entity entity) {
		return entity instanceof EnderDragon || entity instanceof WitherBoss || entity instanceof Warden || entity instanceof ElderGuardian;
	}

	/** A living boss the disciple may fight within {@code range} (one box query). */
	static @Nullable LivingEntity bossNear(DiscipleEntity entity, double range) {
		List<LivingEntity> found = entity.level().getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(range),
				other -> isBoss(other) && other.isAlive() && entity.mayAttack(other) && other.distanceToSqr(entity) <= range * range);
		return found.isEmpty() ? null : found.get(0);
	}

	static void clear() {
		CLONES.clear();
	}

	private DivineClones() {
	}
}
