package dev.kultivator.duel.clash;

import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.util.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gelombang Qi Pedang, the Sword Qi Wave (DESIGN 35.3.10): a full QI cut ({@code ATAS}, {@code KANAN}, {@code KIRI},
 * {@code ATAS_*}) while running throws a virtual crescent (no entity) at 1 block per tick for {@code 10 + realm} blocks,
 * 2.4 wide (+0.6 from Sword Dao comprehension 60). Each tick it tests the living things in its path (the duel partner,
 * or outside a duel whatever {@code Targeting.isHostileTo} allows) and stops at the first solid block ({@code level.clip},
 * read only). A Perfect Deflect reflects it back at 0.7 power in the defender's colour, a Deflect shatters it, a guard
 * blocks it (+25 posture), a dodge lets it pass. The client draws it from the swing's {@code SW_QI_WAVE} flag and the
 * results. Nothing here touches a block.
 */
public final class QiWave {

	public static final double SPEED = 1.0D;
	public static final double WIDTH = 2.4D;
	public static final double WIDE_BONUS = 0.6D;
	public static final float POSTURE = 24.0F;
	public static final float GUARD_POSTURE = 25.0F;
	public static final float DAMAGE_FACTOR = 0.6F;
	public static final float REFLECT_POWER = 0.7F;
	public static final int MAX_WAVES = 16;

	private static final class Wave {
		final LivingEntity owner;
		final ServerLevel level;
		Vec3 pos;
		final Vec3 dir;
		double left;
		final double width;
		final float power;
		final int rgb;
		final Set<UUID> struck = new HashSet<>();
		final @Nullable DuelRuleSet rules;
		final boolean reflected;
		final int swingId;

		Wave(LivingEntity owner, ServerLevel level, Vec3 pos, Vec3 dir, double range, double width, float power, int rgb,
				@Nullable DuelRuleSet rules, boolean reflected, int swingId) {
			this.owner = owner;
			this.level = level;
			this.pos = pos;
			this.dir = dir;
			this.left = range;
			this.width = width;
			this.power = power;
			this.rgb = rgb;
			this.rules = rules;
			this.reflected = reflected;
			this.swingId = swingId;
		}
	}

	private static final List<Wave> WAVES = new ArrayList<>();

	/** A QI swing's impact: the wave leaves from the attacker's chest along its look. */
	static void launch(Swing swing) {
		LivingEntity owner = swing.owner.entity;
		if (!(owner.level() instanceof ServerLevel level) || WAVES.size() >= MAX_WAVES) {
			return;
		}
		Vec3 look = owner.getLookAngle();
		Vec3 dir = new Vec3(look.x, 0.0D, look.z);
		if (dir.lengthSqr() < 1.0E-6D) {
			return;
		}
		dir = dir.normalize();
		double range = 10.0D + Posture.realm(owner);
		double width = WIDTH + (Posture.swordComprehension(owner) >= 60.0D ? WIDE_BONUS : 0.0D);
		Vec3 start = owner.position().add(0.0D, owner.getBbHeight() * 0.55D, 0.0D).add(dir.scale(0.8D));
		WAVES.add(new Wave(owner, level, start, dir, range, width, 1.0F, swing.qiRgb, Fighters.rules(swing.owner), false, swing.id));
	}

	/** Moves every wave one block (once per server tick). */
	static void tick(long now) {
		if (WAVES.isEmpty()) {
			return;
		}
		for (Iterator<Wave> it = new ArrayList<>(WAVES).iterator(); it.hasNext(); ) {
			Wave wave = it.next();
			if (!step(wave, now)) {
				WAVES.remove(wave);
			}
		}
	}

	/** One step; false when the wave is spent. */
	private static boolean step(Wave wave, long now) {
		if (wave.left <= 0.0D || wave.owner.isRemoved() || wave.level != wave.owner.level()) {
			return false;
		}
		Vec3 from = wave.pos;
		Vec3 to = from.add(wave.dir.scale(SPEED));
		BlockHitResult wall = wave.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wave.owner));
		boolean blocked = wall.getType() != HitResult.Type.MISS;
		Vec3 end = blocked ? wall.getLocation() : to;
		double half = wave.width * 0.5D;
		AABB sweep = new AABB(from, end).inflate(half, 0.9D, half);
		for (LivingEntity victim : wave.level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e.isAlive() && e != wave.owner)) {
			if (wave.struck.contains(victim.getUUID()) || !mayHit(wave, victim)) {
				continue;
			}
			wave.struck.add(victim.getUUID());
			if (!strike(wave, victim, now)) {
				return false;
			}
		}
		wave.pos = end;
		wave.left -= SPEED;
		return !blocked;
	}

	private static boolean mayHit(Wave wave, LivingEntity victim) {
		if (DuelRules.dueling(wave.owner, victim)) {
			return true;
		}
		if (DuelRules.inDuel(wave.owner) || DuelRules.inDuel(victim)) {
			return false;
		}
		if (wave.owner instanceof ServerPlayer player) {
			return Targeting.isHostileTo(victim, player);
		}
		Fighter owner = Fighters.peek(wave.owner);
		return owner != null && owner.partner == victim;
	}

	/** The wave meets {@code victim}; false when the wave ends there (deflected, blocked, reflected or spent on a hit). */
	private static boolean strike(Wave wave, LivingEntity victim, long now) {
		Fighter y = Fighters.get(victim);
		Vec3 at = victim.position().add(0.0D, victim.getBbHeight() * 0.55D, 0.0D);
		if (y.dodging(now)) {
			return true;
		}
		boolean faces = HitTest.facing(victim, wave.owner, ClashTiming.GUARD_ARC) || HitTest.angleTo(victim, wave.pos) <= ClashTiming.GUARD_ARC;
		if (CompanionSword.tryDeflect(y, now)) {
			Fighters.sendClash(victim, wave.owner, null, null, DuelIds.OUT_COMPANION_DEFLECT, at, 160, 0);
			return false;
		}
		if (faces) {
			Fighter.Press press = Exchange.bestPress(y, now, Exchange.parryWindow(y, wave.owner, wave.rules));
			if (press != null && y.weapon.parriesBlades) {
				press.consumed = true;
				boolean perfect = !press.locked && now - press.tEff <= ClashTiming.PERFECT_WINDOW;
				if (perfect && !wave.reflected) {
					int rgb = qiColor(victim);
					Fighters.sendClash(victim, wave.owner, null, null, DuelIds.OUT_QI_REFLECT, at, 220, 0, rgb, wave.rgb);
					Vec3 back = wave.owner.position().add(0.0D, wave.owner.getBbHeight() * 0.55D, 0.0D).subtract(at);
					Vec3 dir = new Vec3(back.x, 0.0D, back.z);
					if (dir.lengthSqr() > 1.0E-6D && WAVES.size() < MAX_WAVES) {
						Wave reflected = new Wave(victim, wave.level, at.add(dir.normalize().scale(0.8D)), dir.normalize(),
								Math.max(4.0D, wave.owner.distanceTo(victim) + 2.0D), wave.width, wave.power * REFLECT_POWER, rgb, wave.rules, true, -1);
						WAVES.add(reflected);
					}
					y.statPerfects++;
					Exchange.grant(victim, "duel_first_perfect");
					return false;
				}
				Fighters.sendClash(victim, wave.owner, null, null, DuelIds.OUT_QI_DEFLECT, at, 180, 0, qiColor(victim), wave.rgb);
				y.statDeflects++;
				Exchange.grant(victim, "duel_first_deflect");
				return false;
			}
			if (y.guarding()) {
				Exchange.addPosture(y, wave.owner, GUARD_POSTURE * wave.power, wave.rules, now);
				Fighters.sendClash(wave.owner, victim, null, null, DuelIds.OUT_BLOCK, at, 160, 0, wave.rgb, qiColor(victim));
				Exchange.checkBreak(y, wave.owner, now);
				return false;
			}
		}
		// A hit.
		if (wave.rules != null && wave.rules.realm() == DuelRuleSet.RealmRule.TEKAN_RANAH) {
			ClashDamage.share(wave.owner, victim, ClashDamage.WAVE_SHARE * wave.power);
		} else {
			double attack = wave.owner.getAttribute(Attributes.ATTACK_DAMAGE) != null ? wave.owner.getAttributeValue(Attributes.ATTACK_DAMAGE) : 4.0D;
			float amount = (float) (DAMAGE_FACTOR * attack + Posture.realm(wave.owner)) * wave.power
					* ClashDamage.underdog(wave.owner, victim, wave.rules);
			ClashDamage.flat(wave.owner, victim, amount);
		}
		if (Fighters.postureOf(victim)) {
			Exchange.addPosture(y, wave.owner, POSTURE * wave.power, wave.rules, now);
			Exchange.checkBreak(y, wave.owner, now);
		}
		Fighters.sendClash(wave.owner, victim, null, null, DuelIds.OUT_HIT, at, 150, 0, wave.rgb, wave.rgb);
		return true;
	}

	/** The qi colour of a fighter: its Dao, else its root element, else a pale gold. */
	public static int qiColor(LivingEntity entity) {
		dev.kultivator.data.Dao dao = Posture.dao(entity);
		if (dao != null) {
			return dao.color();
		}
		if (entity instanceof ServerPlayer player) {
			int mask = Cultivators.get(player).rootElements();
			for (dev.kultivator.data.Element element : dev.kultivator.data.Element.values()) {
				if ((mask & element.bit()) != 0) {
					return element.color();
				}
			}
		}
		if (entity instanceof dev.kultivator.disciple.DiscipleEntity disciple) {
			return disciple.data().rootColor();
		}
		dev.kultivator.duel.ai.DuelistProfile profile = dev.kultivator.duel.ai.DuelistRegistry.profile(entity);
		return profile != null ? profile.color() : 0xFFE7B0;
	}

	static void clear() {
		WAVES.clear();
	}

	private QiWave() {
	}
}
