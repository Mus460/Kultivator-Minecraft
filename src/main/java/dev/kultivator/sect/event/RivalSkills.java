package dev.kultivator.sect.event;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.util.NoGrief;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The two arts of rivals and phantoms (DESIGN 20.3.12, 20.3.14), cast through the {@code combatCast} hook: a 12-block
 * qi line of 1 U every 6 s and an element burst of radius 3 and 1 U every 10 s. Phantoms from floor 10 cast one of them
 * every 8 s. They hit only the caster's {@link DuelRules} partner (a mob attack of the caster), entities only.
 * U = {@code DiscipleTechniques.power} of the caster (a phantom: its melee damage) times its {@code damageFactor} hooks.
 */
final class RivalSkills {

	public static final double LINE_RANGE = 12.0D;
	public static final int LINE_COOLDOWN = 120;
	public static final double BURST_RADIUS = 3.0D;
	public static final int BURST_COOLDOWN = 200;
	public static final int PHANTOM_COOLDOWN = 160;
	public static final int PHANTOM_SKILL_FLOOR = 10;

	/** Caster UUID -> game time when the line / burst / phantom art is ready again. */
	private static final Map<UUID, long[]> READY = new HashMap<>();

	/** Called once from {@link SectEvents#init()}. */
	static void init() {
		DiscipleHooks.addCombatCast(RivalSkills::cast);
	}

	static void forget(UUID caster) {
		READY.remove(caster);
	}

	static void clear() {
		READY.clear();
	}

	private static boolean cast(DiscipleEntity caster, LivingEntity target) {
		DiscipleEntity.Role role = caster.role();
		if (role != DiscipleEntity.Role.RIVAL && role != DiscipleEntity.Role.PHANTOM || !(caster.level() instanceof ServerLevel level)) {
			return false;
		}
		LivingEntity partner = DuelRules.partnerOf(caster);
		if (partner == null || partner != target || !target.isAlive()) {
			return false;
		}
		long now = level.getGameTime();
		long[] ready = READY.computeIfAbsent(caster.getUUID(), id -> new long[]{now + 40L, now + 100L, now + 60L});
		double distance = caster.distanceTo(target);
		if (role == DiscipleEntity.Role.PHANTOM) {
			int floor = VoidRealm.phantomFloor(caster);
			if (floor < PHANTOM_SKILL_FLOOR || now < ready[2] || distance > LINE_RANGE) {
				return false;
			}
			ready[2] = now + PHANTOM_COOLDOWN;
			double u = VoidRealm.phantomDamage(floor);
			return level.getRandom().nextBoolean() ? line(level, caster, target, u) : burst(level, caster, target, u);
		}
		double u = DiscipleTechniques.power(caster.data());
		if (now >= ready[1] && distance <= LINE_RANGE) {
			ready[1] = now + BURST_COOLDOWN;
			return burst(level, caster, target, u);
		}
		if (now >= ready[0] && distance <= LINE_RANGE) {
			ready[0] = now + LINE_COOLDOWN;
			return line(level, caster, target, u);
		}
		return false;
	}

	/** The qi line: from the caster's chest to the partner, 1 U. */
	private static boolean line(ServerLevel level, DiscipleEntity caster, LivingEntity target, double u) {
		Vec3 from = caster.position().add(0.0D, caster.getBbHeight() * 0.7D, 0.0D);
		Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
		EventUtil.fx(level, from, to, SectEventFxIds.RIVAL_LINE, caster.data().rootColor(), 0.0F);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.6F, 1.6F);
		face(caster, to);
		return hit(level, caster, target, u);
	}

	/** The element burst of radius 3 around the partner, 1 U. */
	private static boolean burst(ServerLevel level, DiscipleEntity caster, LivingEntity target, double u) {
		Vec3 at = target.position();
		EventUtil.fx(level, at, SectEventFxIds.RIVAL_BURST, caster.data().rootColor(), (float) BURST_RADIUS);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.5F, 1.4F);
		face(caster, at);
		return target.distanceToSqr(at) <= BURST_RADIUS * BURST_RADIUS && hit(level, caster, target, u);
	}

	private static boolean hit(ServerLevel level, DiscipleEntity caster, LivingEntity target, double u) {
		float damage = (float) (u * DiscipleHooks.damageFactor(caster, target, true));
		if (!(damage > 0.0F) || !Float.isFinite(damage) || !DuelRules.dueling(caster, target)) {
			return false;
		}
		if (!(target instanceof Player)) {
			// As in a disciple's blow: the knockback must not land it on farmland or turtle eggs, nor in a portal.
			target.addTag(NoGrief.TAG);
			target.setPortalCooldown();
		}
		target.hurtServer(level, level.damageSources().mobAttack(caster), damage);
		return true;
	}

	private static void face(DiscipleEntity caster, Vec3 point) {
		caster.getLookControl().setLookAt(point.x, point.y, point.z);
	}

	private RivalSkills() {
	}
}
