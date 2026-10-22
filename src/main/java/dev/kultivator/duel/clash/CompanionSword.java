package dev.kultivator.duel.clash;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Technique;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.technique.FlyingSword;
import dev.kultivator.technique.spec.run.Mastery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pedang Pendamping, the Companion Sword (DESIGN 35.3.11). A fighter whose Flying Sword mastery is 3 or more (a disciple
 * that knows the Flying Sword) keeps the sword at its shoulder during a duel (the client draws it): once per 200 ticks it
 * deflects a Sword Qi Wave or a projectile aimed at its owner ({@code COMPANION_DEFLECT}), and when the opponent is
 * staggered it darts in for a thrust ({@code SwingKind.TERBANG}, half damage) that an opponent who has recovered in time
 * can still deflect. Riding the flying sword is not allowed during a duel ({@link #ground}).
 * <p>
 * Sumber: Er Gen, I Shall Seal the Heavens, book 1 ch. 31 "Fight!": Meng Hao against Wang Tengfei in the Outer Sect
 * final, watched by the Outer Sect disciples, first a group of twenty flying swords, then a rain of about forty
 * (wuxiaworld.com, issth-book-1-chapter-31).
 */
public final class CompanionSword {

	public static final int MASTERY_NEEDED = 3;
	public static final int DEFLECT_COOLDOWN = 200;
	public static final int STRIKE_COOLDOWN = 60;
	/** The dart reaches the staggered opponent this long after it leaves the shoulder. */
	public static final int STRIKE_WINDUP = 6;
	public static final float STRIKE_FACTOR = 0.5F;

	private record Strike(Fighter owner, LivingEntity target, Swing swing, long impact) {
	}

	private static final List<Strike> STRIKES = new ArrayList<>();

	/** True when {@code entity} has the Companion Sword (asked when a fight begins). */
	public static boolean has(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			CultivationData data = Cultivators.get(player);
			return FlyingSword.granted(data) && Mastery.level(data, Technique.FLYING_SWORD.id()) >= MASTERY_NEEDED;
		}
		if (entity instanceof DiscipleEntity disciple) {
			return disciple.data().knows(Technique.FLYING_SWORD);
		}
		return false;
	}

	/** At the start of FIGHT: the sword comes down (a flying rider steps off) and becomes the companion. */
	public static void ground(LivingEntity entity, boolean announce) {
		if (entity instanceof ServerPlayer player) {
			CultivationData data = Cultivators.get(player);
			if (data.flyingSword()) {
				FlyingSword.dismount(player, data, FlyingSword.Reason.TOGGLE);
				if (announce) {
					dev.kultivator.util.Msg.actionBar(player, net.minecraft.network.chat.Component.translatable("kultivator.duel.msg.companion_ground")
							.withColor(0x8FE3C8));
				}
			}
		}
	}

	/** Uses the once-per-200-ticks deflect of {@code fighter}'s companion; true when it caught the blow. */
	static boolean tryDeflect(Fighter fighter, long now) {
		if (!fighter.companion || now < fighter.companionReadyAt || !fighter.entity.isAlive()) {
			return false;
		}
		fighter.companionReadyAt = now + DEFLECT_COOLDOWN;
		Fighters.markDirty(fighter);
		return true;
	}

	/** Every tick for a fighter with a companion in a duel: the dart at a staggered partner. */
	static void tick(Fighter fighter, long now) {
		if (!fighter.companion || fighter.partner == null || now < fighter.companionStrikeReadyAt) {
			return;
		}
		LivingEntity target = fighter.partner;
		Fighter other = Fighters.peek(target);
		if (other == null || !other.staggered(now) || !target.isAlive() || fighter.entity.distanceToSqr(target) > 12.0D * 12.0D) {
			return;
		}
		fighter.companionStrikeReadyAt = now + STRIKE_COOLDOWN;
		Swing swing = new Swing(fighter.nextSwingId(), fighter);
		swing.kind = SwingKind.TERBANG;
		swing.dir = SwingDir.TUSUK;
		swing.weapon = WeaponClass.PEDANG;
		swing.windup = STRIKE_WINDUP;
		swing.start = now;
		swing.impact = now + STRIKE_WINDUP;
		swing.resolveAt = swing.impact;
		swing.target = target;
		swing.qiRgb = QiWave.qiColor(fighter.entity);
		Fighters.sendSwing(fighter, swing, 0);
		STRIKES.add(new Strike(fighter, target, swing, swing.impact));
	}

	/** Lands the darts whose time has come (once per server tick). */
	static void tickStrikes(long now) {
		if (STRIKES.isEmpty()) {
			return;
		}
		for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
			Strike strike = it.next();
			if (now < strike.impact()) {
				continue;
			}
			it.remove();
			land(strike, now);
		}
	}

	private static void land(Strike strike, long now) {
		LivingEntity target = strike.target();
		LivingEntity owner = strike.owner().entity;
		Swing swing = strike.swing();
		swing.resolved = true;
		swing.resolvedAt = now;
		if (!target.isAlive() || !owner.isAlive() || target.level() != owner.level()) {
			return;
		}
		Fighter y = Fighters.get(target);
		Vec3 at = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
		DuelRuleSet rules = Fighters.rules(strike.owner());
		if (!y.staggered(now)) {
			Fighter.Press press = Exchange.bestPress(y, now, Exchange.parryWindow(y, owner, rules));
			if (press != null || y.guarding()) {
				if (press != null) {
					press.consumed = true;
				}
				swing.outcome = DuelIds.OUT_COMPANION_DEFLECT;
				Fighters.sendClash(target, owner, null, swing, DuelIds.OUT_COMPANION_DEFLECT, at, 120, 0);
				return;
			}
		}
		swing.outcome = DuelIds.OUT_HIT;
		ClashDamage.apply(swing, target, rules, STRIKE_FACTOR / SwingKind.TERBANG.damage);
		Fighters.sendClash(owner, target, swing, null, DuelIds.OUT_HIT, at, swing.power(), 0);
	}

	static void clear() {
		STRIKES.clear();
	}

	private CompanionSword() {
	}
}
