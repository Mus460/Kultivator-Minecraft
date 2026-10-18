package dev.kultivator.elite;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.Cultivators;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Element;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.DaoTechniques;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The entity-only skills of elites (DESIGN 19.3.1): tier-1 passives on a melee hit, the telegraphed tier-2 actives
 * against the current player target every 8-10 s (Tanda Langit, Napas Es, Hentakan Bumi, Kedipan, Duri Racun; tier 3 a
 * second one), the affixes (Perisai Unsur as a {@code TechniquePower} target factor, Penyerap Qi, Pulih Cepat) and the
 * Raja Siluman boss bar. Every effect acts on entities only: effects, knockback, entity fire and particle bolts, never
 * a block, a LightningBolt or a projectile. Actives only ever hit players in survival or adventure.
 */
public final class EliteSkills {

	/** Perisai Unsur: technique damage ×0.65 unless the technique's element overcomes the elite's. */
	public static final double WARD_FACTOR = 0.65D;
	/** Penyerap Qi: qi removed per melee hit on a player. */
	public static final double DEVOUR_QI = 8.0D;
	/** Pulih Cepat: 1 HP per second (the pass runs every 10 ticks). */
	public static final float RECOVERY_PER_PASS = 0.5F;
	/** Actives every 8-10 s. */
	public static final int SKILL_COOLDOWN = 160;
	public static final int SKILL_COOLDOWN_SPREAD = 41;
	public static final double SKILL_RANGE = 16.0D;
	public static final double MARK_RADIUS = 1.5D;
	public static final double BREATH_RANGE = 6.0D;
	/** cos of the half angle of the breath cone (about 70° wide). */
	public static final double BREATH_COS = 0.82D;
	public static final double STOMP_RADIUS = 4.0D;
	public static final double STOMP_LAUNCH = 0.6D;
	public static final double THORNS_RADIUS = 5.0D;
	public static final double EARTH_KNOCKBACK = 0.6D;
	public static final double LIGHTNING_CHANCE = 0.20D;
	public static final float LIGHTNING_DAMAGE = 2.0F;
	public static final double SPACE_CHANCE = 0.15D;
	/** Players within this range of a Raja Siluman see its boss bar. */
	public static final double BAR_RANGE = 24.0D;

	private static final int[] ACTIVES = {CombatFxIds.MARK, CombatFxIds.BREATH, CombatFxIds.STOMP, CombatFxIds.BLINK, CombatFxIds.THORNS};

	/** A skill (or a delayed passive) waiting for its moment. */
	private record Shot(Mob mob, int skill, Vec3 centre, Vec3 dir, long fireAt, @Nullable LivingEntity victim) {
	}

	private static final List<Shot> SHOTS = new ArrayList<>();
	/** Target UUID -> server tick of its last element spark. */
	private static final Map<UUID, Long> SPARKS = new HashMap<>();
	/** True while the module itself deals damage (no passive on our own skill hits). */
	private static boolean applying;

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(EliteSkills::afterDamage);
	}

	static void clearAll() {
		SHOTS.clear();
		SPARKS.clear();
	}

	// ================================================================== affixes as target factors

	/** Perisai Unsur (a {@code TechniquePower} target factor): ×0.65 unless the technique's element overcomes the elite's. */
	public static double wardFactor(LivingEntity attacker, LivingEntity target, @Nullable TechniqueSpec spec) {
		if (!KultivatorConfig.get().eliteMonsters || EliteMonsters.affix(target) != EliteData.WARD) {
			return 1.0D;
		}
		Element technique = ElementRelations.techniqueElement(attacker, spec);
		Element elite = EliteMonsters.element(target);
		return technique != null && elite != null && ElementRelations.overcomes(technique, elite) ? 1.0D : WARD_FACTOR;
	}

	/** Element sparks on a ×1.3 hit (a {@code BURST} in the technique colour), at most once per target per 10 ticks. */
	static void spark(LivingEntity target, @Nullable Element element) {
		if (element == null || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getServer().getTickCount();
		Long last = SPARKS.get(target.getUUID());
		if (last != null && now - last < 10L && now >= last) {
			return;
		}
		if (SPARKS.size() > 256) {
			SPARKS.values().removeIf(at -> now - at > 200L || at > now);
		}
		SPARKS.put(target.getUUID(), now);
		Fx.burst(level, target.getBoundingBox().getCenter(), element.color(), 10, 0.8F);
	}

	// ================================================================== passives

	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (applying || blocked || !(damageTaken > 0.0F)) {
			return;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof Mob mob) || source.getDirectEntity() != mob || !EliteMonsters.isElite(mob)) {
			return;
		}
		if (!(mob.level() instanceof ServerLevel level) || !victim.isAlive() || victim == mob) {
			return;
		}
		if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		EliteData data = EliteData.of(mob);
		if (data == null) {
			return;
		}
		long now = level.getServer().getTickCount();
		switch (data.elementValue()) {
			case FIRE -> DaoTechniques.ignite(victim, 3.0F);
			case ICE, WATER -> victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), mob);
			case WOOD -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), mob);
			case METAL -> victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), mob);
			case EARTH -> push(victim, mob.position(), EARTH_KNOCKBACK, 0.15D);
			// The bolt waits out the hit's invulnerability frames, otherwise the 2 damage would be swallowed.
			case LIGHTNING -> {
				if (mob.getRandom().nextDouble() < LIGHTNING_CHANCE) {
					SHOTS.add(new Shot(mob, CombatFxIds.BOLT, victim.position(), Vec3.ZERO, now + 11L, victim));
				}
			}
			case SPACE -> {
				if (mob.getRandom().nextDouble() < SPACE_CHANCE) {
					SHOTS.add(new Shot(mob, CombatFxIds.BLINK_HIT, victim.position(), Vec3.ZERO, now + 1L, victim));
				}
			}
			case WIND -> {
				// Wind's passive is its permanent +30% speed.
			}
		}
		if (data.affix() == EliteData.DEVOURER && victim instanceof ServerPlayer player) {
			double qi = Cultivators.get(player).qi();
			double taken = Math.min(DEVOUR_QI, qi);
			if (taken > 0.0D && Qi.trySpend(player, taken)) {
				Fx.send(level, player.position().add(0.0D, 1.0D, 0.0D), mob.position().add(0.0D, mob.getBbHeight() * 0.6D, 0.0D), FxType.BEAM,
						0x47B2FF, 0, 0.15F);
			}
		}
	}

	// ================================================================== the 10-tick pass

	/** Every 10 ticks: Pulih Cepat, the Raja Siluman bar and the actives; removes dead or unloaded elites. */
	static void pass(MinecraftServer server, long tick) {
		Iterator<EliteMonsters.Tracked> iterator = EliteMonsters.TRACKED.values().iterator();
		while (iterator.hasNext()) {
			EliteMonsters.Tracked tracked = iterator.next();
			Mob mob = tracked.mob;
			EliteData data = EliteData.of(mob);
			if (mob.isRemoved() || !mob.isAlive() || data == null || !EliteMonsters.isElite(mob) || !(mob.level() instanceof ServerLevel level)) {
				if (tracked.bar != null) {
					tracked.bar.removeAllPlayers();
				}
				iterator.remove();
				continue;
			}
			if (data.affix() == EliteData.RECOVERY && mob.getHealth() < mob.getMaxHealth()) {
				mob.heal(RECOVERY_PER_PASS);
			}
			if (data.tier() >= 3) {
				updateBar(tracked, data, level);
			}
			if (data.tier() >= 2 && tick >= tracked.nextSkillAt) {
				tryActive(tracked, data, level, tick);
			}
		}
	}

	private static void updateBar(EliteMonsters.Tracked tracked, EliteData data, ServerLevel level) {
		Mob mob = tracked.mob;
		if (tracked.bar == null) {
			tracked.bar = new ServerBossEvent(Mth.createInsecureUUID(level.getRandom()), data.title(), barColor(data.elementValue()),
					BossEvent.BossBarOverlay.PROGRESS);
		}
		ServerBossEvent bar = tracked.bar;
		bar.setProgress(Mth.clamp(mob.getHealth() / Math.max(1.0F, mob.getMaxHealth()), 0.0F, 1.0F));
		Vec3 pos = mob.position();
		double rangeSq = BAR_RANGE * BAR_RANGE;
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (player.isRemoved() || player.level() != level || player.position().distanceToSqr(pos) > rangeSq) {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.position().distanceToSqr(pos) <= rangeSq && !bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			}
		}
	}

	private static BossEvent.BossBarColor barColor(Element element) {
		return switch (element) {
			case FIRE -> BossEvent.BossBarColor.RED;
			case WATER, ICE -> BossEvent.BossBarColor.BLUE;
			case WOOD, WIND -> BossEvent.BossBarColor.GREEN;
			case EARTH -> BossEvent.BossBarColor.YELLOW;
			case METAL -> BossEvent.BossBarColor.WHITE;
			case LIGHTNING, SPACE -> BossEvent.BossBarColor.PURPLE;
		};
	}

	// ================================================================== actives

	/** The active of an element: Fire/Lightning Tanda Langit, Ice/Water Napas Es, Earth/Metal Hentakan Bumi, Wind/Space Kedipan, Wood Duri Racun. */
	public static int activeFor(Element element) {
		return switch (element) {
			case FIRE, LIGHTNING -> CombatFxIds.MARK;
			case ICE, WATER -> CombatFxIds.BREATH;
			case EARTH, METAL -> CombatFxIds.STOMP;
			case WIND, SPACE -> CombatFxIds.BLINK;
			case WOOD -> CombatFxIds.THORNS;
		};
	}

	/** A Raja Siluman's second active: one of the other four, fixed per mob. */
	static int secondary(Mob mob, int primary) {
		int[] others = new int[ACTIVES.length - 1];
		int n = 0;
		for (int skill : ACTIVES) {
			if (skill != primary) {
				others[n++] = skill;
			}
		}
		return others[(int) Math.floorMod(mob.getUUID().getLeastSignificantBits(), (long) others.length)];
	}

	private static void tryActive(EliteMonsters.Tracked tracked, EliteData data, ServerLevel level, long tick) {
		Mob mob = tracked.mob;
		if (!(mob.getTarget() instanceof ServerPlayer player) || !validVictim(player, level)
				|| mob.distanceToSqr(player) > SKILL_RANGE * SKILL_RANGE || !mob.getSensing().hasLineOfSight(player)) {
			return;
		}
		int primary = activeFor(data.elementValue());
		int skill = primary;
		if (data.tier() >= 3) {
			int second = secondary(mob, primary);
			skill = tracked.skillTurn % 2 == 0 ? primary : second;
			if (!inRange(skill, mob, player)) {
				skill = skill == primary ? second : primary;
			}
		}
		if (!inRange(skill, mob, player)) {
			return;
		}
		start(mob, skill, player, level, tick);
		tracked.skillTurn++;
		tracked.nextSkillAt = tick + SKILL_COOLDOWN + mob.getRandom().nextInt(SKILL_COOLDOWN_SPREAD);
	}

	private static boolean inRange(int skill, Mob mob, ServerPlayer player) {
		double distance = mob.distanceTo(player);
		return switch (skill) {
			case CombatFxIds.BREATH -> distance <= BREATH_RANGE;
			case CombatFxIds.STOMP -> distance <= STOMP_RADIUS + 0.5D;
			case CombatFxIds.THORNS -> distance <= THORNS_RADIUS;
			case CombatFxIds.BLINK -> distance >= 2.0D;
			default -> true;
		};
	}

	private static void start(Mob mob, int skill, ServerPlayer player, ServerLevel level, long tick) {
		int id = mob.getId();
		switch (skill) {
			case CombatFxIds.MARK -> {
				Vec3 centre = player.position();
				Fx.send(level, centre, centre, FxType.ELITE, CombatFxIds.MARK, id, (float) MARK_RADIUS);
				sound(level, centre, SoundEvents.EVOKER_PREPARE_ATTACK, 0.9F, 1.3F);
				SHOTS.add(new Shot(mob, skill, centre, Vec3.ZERO, tick + CombatFxIds.MARK_TICKS, player));
			}
			case CombatFxIds.BREATH -> {
				Vec3 from = mob.position().add(0.0D, mob.getBbHeight() * 0.6D, 0.0D);
				Vec3 dir = flat(player.position().subtract(mob.position()));
				Fx.send(level, from, from.add(dir.scale(BREATH_RANGE)), FxType.ELITE, CombatFxIds.BREATH, id, (float) BREATH_RANGE);
				sound(level, from, SoundEvents.PLAYER_HURT_FREEZE, 0.9F, 0.6F);
				SHOTS.add(new Shot(mob, skill, from, dir, tick + CombatFxIds.WINDUP_TICKS, player));
			}
			case CombatFxIds.STOMP -> {
				Vec3 centre = mob.position();
				Fx.send(level, centre, centre, FxType.ELITE, CombatFxIds.STOMP, id, (float) STOMP_RADIUS);
				sound(level, centre, SoundEvents.RAVAGER_ROAR, 0.6F, 1.4F);
				SHOTS.add(new Shot(mob, skill, centre, Vec3.ZERO, tick + CombatFxIds.WINDUP_TICKS, player));
			}
			case CombatFxIds.BLINK -> {
				Vec3 centre = mob.position();
				Fx.send(level, centre, centre, FxType.ELITE, CombatFxIds.BLINK, id, mob.getBbWidth());
				sound(level, centre, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.7F, 1.4F);
				SHOTS.add(new Shot(mob, skill, centre, Vec3.ZERO, tick + CombatFxIds.BLINK_TICKS, player));
			}
			case CombatFxIds.THORNS -> {
				Vec3 centre = mob.position();
				Fx.send(level, centre, centre, FxType.ELITE, CombatFxIds.THORNS, id, (float) THORNS_RADIUS);
				sound(level, centre, SoundEvents.SPIDER_AMBIENT, 1.0F, 0.6F);
				SHOTS.add(new Shot(mob, skill, centre, Vec3.ZERO, tick + CombatFxIds.WINDUP_TICKS, player));
			}
			default -> {
			}
		}
	}

	/** Every tick: fires the skills (and delayed passives) whose moment came. */
	static void tickPending(MinecraftServer server, long tick) {
		if (SHOTS.isEmpty()) {
			return;
		}
		List<Shot> due = new ArrayList<>();
		Iterator<Shot> iterator = SHOTS.iterator();
		while (iterator.hasNext()) {
			Shot shot = iterator.next();
			if (tick >= shot.fireAt || shot.fireAt - tick > 200L) {
				iterator.remove();
				due.add(shot);
			}
		}
		for (Shot shot : due) {
			Mob mob = shot.mob;
			if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level) || !EliteMonsters.isElite(mob)) {
				continue;
			}
			EliteData data = EliteData.of(mob);
			if (data != null) {
				fire(shot, mob, data, level);
			}
		}
	}

	private static void fire(Shot shot, Mob mob, EliteData data, ServerLevel level) {
		int id = mob.getId();
		switch (shot.skill) {
			case CombatFxIds.MARK -> {
				float damage = (float) (6.0D + 3.0D * data.tier() + 0.5D * data.realm());
				for (ServerPlayer player : playersNear(level, shot.centre, MARK_RADIUS, 2.5D)) {
					hurt(player, mob, level, damage);
				}
				Fx.send(level, shot.centre, shot.centre, FxType.ELITE, CombatFxIds.MARK_HIT, id, (float) MARK_RADIUS);
				sound(level, shot.centre, data.elementValue() == Element.FIRE ? SoundEvents.BLAZE_SHOOT : SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.0F);
			}
			case CombatFxIds.BREATH -> {
				for (ServerPlayer player : playersNear(level, shot.centre, BREATH_RANGE, 3.0D)) {
					Vec3 to = flat(player.position().subtract(shot.centre));
					if (to.dot(shot.dir) >= BREATH_COS) {
						player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3), mob);
						hurt(player, mob, level, 3.0F);
					}
				}
				Fx.send(level, shot.centre, shot.centre.add(shot.dir.scale(BREATH_RANGE)), FxType.ELITE, CombatFxIds.BREATH_HIT, id, (float) BREATH_RANGE);
				sound(level, shot.centre, SoundEvents.POWDER_SNOW_BREAK, 1.0F, 0.6F);
			}
			case CombatFxIds.STOMP -> {
				float damage = (float) (5.0D + 2.0D * data.tier());
				for (ServerPlayer player : playersNear(level, shot.centre, STOMP_RADIUS, 2.5D)) {
					hurt(player, mob, level, damage);
					push(player, shot.centre, 0.25D, STOMP_LAUNCH);
				}
				Fx.send(level, shot.centre, shot.centre, FxType.ELITE, CombatFxIds.STOMP_HIT, id, (float) STOMP_RADIUS);
				sound(level, shot.centre, SoundEvents.MACE_SMASH_GROUND, 1.0F, 0.7F);
			}
			case CombatFxIds.THORNS -> {
				for (ServerPlayer player : playersNear(level, shot.centre, THORNS_RADIUS, 2.5D)) {
					player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), mob);
				}
				Fx.send(level, shot.centre, shot.centre, FxType.ELITE, CombatFxIds.THORNS_HIT, id, (float) THORNS_RADIUS);
				sound(level, shot.centre, SoundEvents.SPLASH_POTION_BREAK, 0.8F, 0.8F);
			}
			case CombatFxIds.BLINK, CombatFxIds.BLINK_HIT -> {
				LivingEntity victim = shot.victim;
				if (victim != null && victim.isAlive() && victim.level() == level && mob.distanceToSqr(victim) <= 20.0D * 20.0D
						&& !(victim instanceof Player player && (player.isCreative() || player.isSpectator()))) {
					blinkBehind(mob, victim, level);
				}
			}
			case CombatFxIds.BOLT -> {
				LivingEntity victim = shot.victim;
				if (victim != null && victim.isAlive() && victim.level() == level && mob.distanceToSqr(victim) <= 10.0D * 10.0D
						&& !(victim instanceof Player player && (player.isCreative() || player.isSpectator()))) {
					Fx.send(level, mob.position().add(0.0D, mob.getBbHeight() * 0.8D, 0.0D), victim.getBoundingBox().getCenter(), FxType.ELITE,
							CombatFxIds.BOLT, id, 0.0F);
					sound(level, victim.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5F, 1.6F);
					hurt(victim, mob, level, LIGHTNING_DAMAGE);
				}
			}
			default -> {
			}
		}
	}

	/** Kedipan and the Space passive: the elite lands behind its victim, only on a safe spot. */
	private static void blinkBehind(Mob mob, LivingEntity victim, ServerLevel level) {
		Vec3 look = flat(victim.getLookAngle());
		if (look.lengthSqr() < 1.0E-4D) {
			look = flat(victim.position().subtract(mob.position()));
		}
		Vec3 spot = Targeting.safeSpot(level, mob, victim.position().subtract(look.scale(1.6D)));
		if (spot == null) {
			return;
		}
		Vec3 from = mob.position();
		mob.teleportTo(spot.x, spot.y, spot.z);
		float yaw = (float) (Mth.atan2(victim.getZ() - spot.z, victim.getX() - spot.x) * (180.0D / Math.PI)) - 90.0F;
		mob.setYRot(yaw);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		Fx.send(level, from, spot, FxType.ELITE, CombatFxIds.BLINK_HIT, mob.getId(), mob.getBbWidth());
		sound(level, spot, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.2F);
	}

	// ================================================================== helpers

	/** Survival or adventure players of {@code level} that are alive. */
	private static boolean validVictim(ServerPlayer player, ServerLevel level) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative() && player.level() == level;
	}

	/** Valid players within {@code radius} (horizontally) and {@code height} (vertically) of {@code centre}. */
	private static List<ServerPlayer> playersNear(ServerLevel level, Vec3 centre, double radius, double height) {
		List<ServerPlayer> list = new ArrayList<>();
		double radiusSq = radius * radius;
		for (ServerPlayer player : level.players()) {
			if (!validVictim(player, level)) {
				continue;
			}
			double dx = player.getX() - centre.x;
			double dz = player.getZ() - centre.z;
			if (dx * dx + dz * dz <= radiusSq && Math.abs(player.getY() - centre.y) <= height) {
				list.add(player);
			}
		}
		return list;
	}

	private static void hurt(LivingEntity victim, Mob mob, ServerLevel level, float amount) {
		applying = true;
		try {
			victim.hurtServer(level, mob.damageSources().mobAttack(mob), amount);
		} finally {
			applying = false;
		}
	}

	/** Pushes {@code victim} away from {@code from} (entity motion only; a pushed mob gets the no-grief tag). */
	private static void push(LivingEntity victim, Vec3 from, double horizontal, double up) {
		Vec3 away = flat(victim.position().subtract(from));
		if (away.lengthSqr() < 1.0E-4D) {
			away = new Vec3(1.0D, 0.0D, 0.0D);
		}
		if (!(victim instanceof Player)) {
			victim.addTag(NoGrief.TAG);
		}
		victim.setDeltaMovement(victim.getDeltaMovement().add(away.x * horizontal, up, away.z * horizontal));
		victim.needsSync = true;
		victim.syncVelocity = true;
	}

	private static Vec3 flat(Vec3 vector) {
		Vec3 flat = new Vec3(vector.x, 0.0D, vector.z);
		return flat.lengthSqr() < 1.0E-6D ? Vec3.ZERO : flat.normalize();
	}

	private static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	private EliteSkills() {
	}
}
