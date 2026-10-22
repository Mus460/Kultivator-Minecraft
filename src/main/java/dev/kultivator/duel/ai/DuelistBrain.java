package dev.kultivator.duel.ai;

import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.power.PowerIds;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.api.Duelist;
import dev.kultivator.duel.clash.ClashClock;
import dev.kultivator.duel.clash.ClashTiming;
import dev.kultivator.duel.clash.Fighter;
import dev.kultivator.duel.clash.Fighters;
import dev.kultivator.duel.clash.Posture;
import dev.kultivator.duel.clash.Swing;
import dev.kultivator.duel.clash.SwingDir;
import dev.kultivator.duel.clash.SwingKind;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.sect.hall.Talents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

/**
 * The mind of a non-player duellist (DESIGN 35.7): every tick it (1) keeps its distance by style, (2) reads the
 * opponent's swings that are already visible ({@link AttackReader}), (3) answers them after its reaction time through the
 * same windows as a player ({@code ClashTiming}, no latency compensation), (4) picks its own attacks from its style's
 * table and (5) pushes in a blade lock. All its actions go through {@code Fighters.input}, exactly the path of a
 * player's inputs, so the AI never gets a wider window than a person. Its deflect timing is
 * {@code impact − 1 + round(N(0, σ))}: Perfect Deflects come from σ, not from a magic number.
 * <p>
 * Difficulty by {@code Rank.step()} (35.7.4): T0 Mortal-Qi Refining (0-4), T1 Foundation-Golden Core (5-12), T2 Nascent
 * Soul-Deity Transformation (13-20), T3 Dao Union-Immortal Ascension (21-60), T4 True Immortal (61-64).
 */
public final class DuelistBrain {

	/** Ticks from seeing a windup to acting on it, per tier. */
	static final int[] REACTION = {7, 6, 5, 4, 4};
	/** Deflect timing spread (ticks), per tier. */
	static final float[] SIGMA = {2.0F, 1.6F, 1.2F, 0.9F, 0.7F};
	/** Chance a feint fools it, per tier. */
	static final float[] FEINT_BITE = {0.6F, 0.5F, 0.4F, 0.3F, 0.2F};
	/** Blade lock pushes per second, per tier. */
	static final int[] PUSH_RATE = {5, 6, 7, 8, 9};

	/** Test dummy modes ({@code uji duel boneka diam|jaga|serang <arah>}). */
	public static final int MODE_FIGHT = 0;
	public static final int MODE_STILL = 1;
	public static final int MODE_GUARD = 2;
	public static final int MODE_REPEAT = 3;
	public static final int REPEAT_GAP = 30;
	/** {@code JURUS_BEBAS}: a disciple casts at most once per this many ticks, from beyond 5 blocks. */
	public static final int TECHNIQUE_GAP = 120;

	private final Fighter self;
	private final Mob mob;
	private final LivingEntity partner;
	private final @Nullable DuelSession session;
	private final DuelStyle style;
	private final int tier;
	private final int reaction;
	private final float sigma;
	private final float feintBite;
	private final int pushRate;
	private final RandomSource random;
	private final AttackReader reader = new AttackReader();

	// The reaction to a visible swing.
	private @Nullable Swing watched;
	private long reactAt = -1L;
	private boolean bitten;
	private long pressAt = -1L;
	private boolean pressForward;
	private long guardUntil = -1L;
	private boolean aiGuard;
	// The own attack in progress.
	private long chargeReleaseAt = -1L;
	private boolean waveRelease;
	private long feintAt = -1L;
	private long delayedSwingAt = -1L;
	private SwingDir delayedDir = SwingDir.KANAN;
	private int delayedFlags;
	private long nextDecision;
	private int comboIndex;
	private final ArrayDeque<Integer> recentChoices = new ArrayDeque<>();
	private boolean feintNext;
	// Blade lock.
	private float pushCarry;
	// Movement.
	private long nextStrafe;
	private float strafeSide = 1.0F;
	private long lastTechnique = Long.MIN_VALUE / 4;
	// Dummy.
	private int mode = MODE_FIGHT;
	private SwingDir repeatDir = SwingDir.KANAN;
	private long nextRepeat;
	/** Perfect Deflects attempted / made, for {@code uji duel info}. */
	public int statParries;

	public DuelistBrain(Fighter self, Mob mob, LivingEntity partner, @Nullable DuelSession session, @Nullable DuelistProfile profile) {
		this.self = self;
		this.mob = mob;
		this.partner = partner;
		this.session = session;
		this.random = mob.getRandom();
		this.style = profile != null ? profile.style() : DuelStyle.BERTAHAN;
		Rank rank = Posture.rank(mob);
		int step = profile != null ? profile.rankStep() : rank != null ? rank.step() : 0;
		this.tier = tierOf(step);
		boolean saint = style == DuelStyle.SANTO_PEDANG;
		this.reaction = saint ? Math.max(3, REACTION[tier] - 1) : REACTION[tier];
		float s = SIGMA[tier] * (saint ? 0.85F : 1.0F);
		if (mob instanceof DiscipleEntity disciple && (Talents.talents(disciple.data()) & PowerIds.TALENT_SWORD_SENSE) != 0) {
			s *= 0.9F;
		}
		this.sigma = s;
		this.feintBite = FEINT_BITE[tier] * (saint ? 0.5F : 1.0F);
		this.pushRate = PUSH_RATE[tier];
		this.nextDecision = ClashClock.now() + 10;
	}

	/** The tier 0-4 of a rank step (35.7.4). */
	public static int tierOf(int step) {
		if (step <= 4) {
			return 0;
		}
		if (step <= 12) {
			return 1;
		}
		if (step <= 20) {
			return 2;
		}
		if (step <= 60) {
			return 3;
		}
		return 4;
	}

	public DuelStyle style() {
		return style;
	}

	public int tier() {
		return tier;
	}

	public AttackReader reader() {
		return reader;
	}

	/** Test dummy behaviour. */
	public void setMode(int value, @Nullable SwingDir dir) {
		mode = value;
		if (dir != null) {
			repeatDir = dir;
		}
		nextRepeat = ClashClock.now() + 20;
	}

	public int mode() {
		return mode;
	}

	// ================================================================ the answer to a challenge

	/**
	 * Whether {@code npc} accepts {@code challenger}'s duel (asked 20-40 ticks after the challenge): null yes, otherwise
	 * the reason it declines.
	 */
	public static @Nullable Component answer(LivingEntity npc, ServerPlayer challenger, DuelRuleSet rules, int pair) {
		if (npc == null || !npc.isAlive()) {
			return Component.translatable("kultivator.duel.reason.gone", npc == null ? Component.empty() : npc.getDisplayName());
		}
		if (npc instanceof Duelist duelist) {
			try {
				return duelist.acceptsDuel(challenger) ? null : Component.translatable("kultivator.duel.reason.npc_declines");
			} catch (RuntimeException exception) {
				return Component.translatable("kultivator.duel.reason.npc_declines");
			}
		}
		float health = npc.getHealth() / Math.max(1.0F, npc.getMaxHealth());
		if (npc instanceof DiscipleEntity) {
			return health >= 0.5F ? null : Component.translatable("kultivator.duel.reason.injured", npc.getDisplayName());
		}
		DuelistProfile profile = DuelistRegistry.profile(npc);
		if (profile == null) {
			return Component.translatable("kultivator.duel.reason.cannot", npc.getDisplayName());
		}
		if (health < 0.6F && profile.style() != DuelStyle.AGRESIF) {
			return Component.translatable("kultivator.duel.reason.injured", npc.getDisplayName());
		}
		int gap = Posture.realm(challenger) - Rank.ofStep(profile.rankStep()).realm().ordinal();
		if (gap >= 3 && rules.realm() != DuelRuleSet.RealmRule.TEKAN_RANAH && profile.style() != DuelStyle.AGRESIF) {
			// A wise duellist does not fight a far higher realm without Realm Suppression.
			return Component.translatable("kultivator.duel.reason.npc_realm");
		}
		return null;
	}

	// ================================================================ yielding (35.7.2)

	/** True when it bows out now: never AGRESIF, never with items at stake. */
	public boolean wantsSurrender(long now, DuelRuleSet rules) {
		if (rules.stake().items() || mode != MODE_FIGHT) {
			return false;
		}
		float health = mob.getHealth() / Math.max(1.0F, mob.getMaxHealth());
		return switch (style) {
			case AGRESIF -> false;
			case SANTO_PEDANG -> self.staggerCount() >= 2 && health < 0.20F;
			default -> style.surrender > 0.0F && health < style.surrender;
		};
	}

	// ================================================================ what it sees

	/** The opponent did something visible: a windup began, a charge, a press, a feint. */
	public void observe(Fighter other, int kind, long now) {
		if (other.entity != partner) {
			return;
		}
		switch (kind) {
			case DuelIds.IN_SWING -> {
				Swing swing = other.swing();
				if (swing == null || !swing.live() || swing.charging) {
					return;
				}
				if (reads()) {
					reader.swing(swing.dir, now);
				}
				watched = swing;
				bitten = false;
				reactAt = now + reaction;
			}
			case DuelIds.IN_PARRY -> {
				Swing own = self.swing();
				int lead = own != null && own.live() && !own.charging ? (int) (own.impact - now) : -1;
				if (reads()) {
					reader.press(lead, now);
				}
			}
			case DuelIds.IN_FEINT -> {
				// The feint shows only now: it fools this duellist with its bite chance.
				if (watched != null && watched.cancelled) {
					bitten = random.nextFloat() < feintBite;
					if (!bitten) {
						watched = null;
						reactAt = -1L;
						pressAt = -1L;
						if (aiGuard && guardUntil > now) {
							guardUntil = now + 2;
						}
					}
				}
			}
			default -> {
			}
		}
	}

	private boolean reads() {
		return style == DuelStyle.SANTO_PEDANG || style == DuelStyle.PENIPU;
	}

	// ================================================================ the tick

	/** Every server tick of FIGHT (from {@link DuelistGoal}). */
	public void tick(long now) {
		if (!mob.isAlive() || !partner.isAlive()) {
			return;
		}
		mob.getLookControl().setLookAt(partner, 30.0F, 30.0F);
		if (self.bind() != null) {
			tickBind(now);
			return;
		}
		if (mode == MODE_STILL) {
			mob.getNavigation().stop();
			return;
		}
		if (mode == MODE_GUARD) {
			mob.getNavigation().stop();
			if (!self.guarding() && self.free(now)) {
				input(DuelIds.IN_GUARD_ON, SwingDir.KANAN, 0, 0, now);
			}
			return;
		}
		if (mode == MODE_REPEAT) {
			tickRepeat(now);
			return;
		}
		// Scheduled actions first: a deflect press, a charge release, a feint, a delayed (rhythm) cut.
		if (pressAt >= 0L && now >= pressAt) {
			pressAt = -1L;
			statParries++;
			input(DuelIds.IN_PARRY, SwingDir.KANAN, pressForward ? DuelIds.INF_FORWARD : 0, 0, now);
		}
		if (aiGuard && guardUntil >= 0L && now > guardUntil) {
			aiGuard = false;
			guardUntil = -1L;
			if (self.guarding() && style != DuelStyle.BERTAHAN) {
				input(DuelIds.IN_GUARD_OFF, SwingDir.KANAN, 0, 0, now);
			}
		}
		if (chargeReleaseAt >= 0L && now >= chargeReleaseAt) {
			chargeReleaseAt = -1L;
			input(DuelIds.IN_CHARGE_RELEASE, SwingDir.KANAN, waveRelease ? DuelIds.INF_RUN : 0, 0, now);
		}
		if (feintAt >= 0L && now >= feintAt) {
			feintAt = -1L;
			Swing own = self.swing();
			if (own != null && own.live()) {
				input(DuelIds.IN_FEINT, SwingDir.KANAN, 0, 0, now);
			}
		}
		if (delayedSwingAt >= 0L && now >= delayedSwingAt) {
			delayedSwingAt = -1L;
			attackNow(delayedDir, delayedFlags, now);
		}
		if (reactAt >= 0L && now >= reactAt) {
			reactAt = -1L;
			react(now);
		}
		move(now);
		if (now >= nextDecision) {
			nextDecision = now + 4 + random.nextInt(5);
			decide(now);
		}
		tickTechnique(now);
	}

	/** The reaction to the watched swing, {@code reaction} ticks after it became visible. */
	private void react(long now) {
		Swing swing = watched;
		watched = null;
		if (swing == null) {
			return;
		}
		if (!swing.live()) {
			if (bitten && swing.cancelled) {
				// Fooled by the feint: the press goes into the air (and the spam lock follows).
				press(now, false);
			}
			return;
		}
		if (!self.free(now) && self.bind() == null) {
			return;
		}
		Swing own = self.swing();
		if (own != null && own.live() && !own.charging) {
			if (Math.abs(own.impact - swing.impact) <= ClashTiming.CLASH_WINDOW) {
				// The two cuts will meet: a clash or a lock decides it.
				return;
			}
			if (style == DuelStyle.SANTO_PEDANG && random.nextFloat() < 0.5F && now < own.impact - ClashTiming.FEINT_LAST) {
				// The saint cancels its own cut to deflect.
				input(DuelIds.IN_FEINT, SwingDir.KANAN, 0, 0, now);
			} else {
				return;
			}
		}
		int perilous = swing.perilous();
		float parry = style.parry;
		float guard = style.guard;
		float dodge = style.dodge;
		float counter = style.counter;
		if (perilous == DuelIds.PERIL_SAPU) {
			parry = 0.0F;
			guard = 0.0F;
			counter = 0.0F;
			dodge = 1.0F;
		} else if (perilous == DuelIds.PERIL_MAUT) {
			guard = 0.0F;
			counter = 0.0F;
			parry += 0.3F;
			dodge += 0.3F;
		} else if (perilous == DuelIds.PERIL_GUNUNG) {
			guard = tier == 0 ? 0.1F : 0.0F;
			parry += 0.2F;
		}
		if (swing.kind == SwingKind.PENENTU) {
			parry = 0.0F;
			guard = 0.0F;
			counter = 0.0F;
			dodge = 1.0F;
		}
		if (swing.riposte()) {
			guard = 0.0F;
		}
		SwingDir favourite = style == DuelStyle.SANTO_PEDANG ? reader.favouriteDir() : null;
		if (favourite != null && favourite == swing.dir) {
			parry += 0.25F;
		}
		WeaponClass weapon = self.weapon();
		if (!weapon.parriesBlades && swing.weapon.blade()) {
			guard += parry;
			parry = 0.0F;
		}
		int ownWindup = ClashTiming.realmWindup(weapon.lightWindup, Posture.realm(mob));
		if (counter > 0.0F && (swing.impact - now < ownWindup - ClashTiming.CLASH_WINDOW || !weapon.edged() || now < self.nextActionAt())) {
			counter = 0.0F;
		}
		if (dodge > 0.0F && now < self.dodgeReadyAt()) {
			dodge = 0.0F;
		}
		float total = parry + guard + dodge + counter;
		if (total <= 0.0F) {
			return;
		}
		float roll = random.nextFloat() * total;
		if ((roll -= parry) < 0.0F) {
			pressForward = perilous == DuelIds.PERIL_MAUT && tier >= 2 && random.nextFloat() < 0.5F;
			long target = swing.impact - 1 + Math.round(random.nextGaussian() * sigma);
			pressAt = Math.max(now, target);
			if (pressAt <= now) {
				press(now, pressForward);
			}
		} else if ((roll -= guard) < 0.0F) {
			if (!self.guarding()) {
				input(DuelIds.IN_GUARD_ON, SwingDir.KANAN, 0, 0, now);
			}
			aiGuard = true;
			guardUntil = swing.impact + 3;
		} else if ((roll -= dodge) < 0.0F) {
			if (perilous == DuelIds.PERIL_SAPU && random.nextFloat() < 0.35F && mob.onGround()) {
				mob.getJumpControl().jump();
			} else {
				input(DuelIds.IN_DODGE, SwingDir.KANAN, 0, 0, now);
				strafeSide = random.nextBoolean() ? 1.0F : -1.0F;
				mob.getMoveControl().strafe(-0.5F, 0.9F * strafeSide);
			}
		} else {
			// Counter: cut back along the same side so the blades meet (a clash, or a lock).
			attackNow(swing.dir == SwingDir.TUSUK || swing.dir == SwingDir.PUTAR ? SwingDir.KANAN : swing.dir, 0, now);
		}
	}

	private void press(long now, boolean forward) {
		statParries++;
		input(DuelIds.IN_PARRY, SwingDir.KANAN, forward ? DuelIds.INF_FORWARD : 0, 0, now);
	}

	/** Distance by style: close in, keep the reach, step back when the posture is high, step aside now and then. */
	private void move(long now) {
		double distance = mob.distanceTo(partner);
		double reach = self.weapon().reach;
		double keep = Math.max(1.6D, reach * style.keepDistance);
		float postureShare = self.posture() / Math.max(1.0F, self.postureMax());
		Swing own = self.swing();
		boolean busy = own != null && own.live() || !self.free(now);
		if (busy) {
			if (own != null && own.charging) {
				mob.getNavigation().stop();
			}
			return;
		}
		if (postureShare > style.retreat && distance < reach + 1.5D) {
			Vec3 away = mob.position().subtract(partner.position());
			Vec3 to = mob.position().add(away.normalize().scale(2.5D));
			mob.getNavigation().moveTo(to.x, to.y, to.z, 1.0D);
			return;
		}
		if (distance > keep + 0.8D) {
			mob.getNavigation().moveTo(partner, style == DuelStyle.AGRESIF ? 1.25D : 1.05D);
		} else if (distance < 1.3D) {
			Vec3 away = mob.position().subtract(partner.position());
			if (away.lengthSqr() > 1.0E-4D) {
				Vec3 to = mob.position().add(away.normalize().scale(1.5D));
				mob.getNavigation().moveTo(to.x, to.y, to.z, 0.9D);
			}
		} else {
			mob.getNavigation().stop();
			if (now >= nextStrafe) {
				nextStrafe = now + 20;
				if (random.nextFloat() < style.side) {
					strafeSide = random.nextBoolean() ? 1.0F : -1.0F;
				}
			}
			if (style.side > 0.15F) {
				mob.getMoveControl().strafe(0.0F, 0.45F * strafeSide);
			}
		}
	}

	/** One decision about its own attack. */
	private void decide(long now) {
		if (!self.free(now) || now < self.nextActionAt()) {
			return;
		}
		Swing own = self.swing();
		if (own != null && own.live() || delayedSwingAt >= 0L) {
			return;
		}
		Fighter other = Fighters.peek(partner);
		double distance = mob.distanceTo(partner);
		double reach = self.weapon().reach;
		boolean inReach = distance <= reach + 0.4D;
		if (other != null && other.staggered(now) && distance <= reach + 1.5D) {
			// The 決 mark: the Deathblow.
			attackNow(SwingDir.ATAS, 0, now);
			return;
		}
		if (self.celah(now) && inReach) {
			attackNow(SwingDir.combo(comboIndex++), 0, now);
			return;
		}
		boolean opponentWindup = other != null && other.swing() != null && other.swing().live();
		if (opponentWindup && other.swing().charging && style == DuelStyle.AGRESIF && inReach) {
			// Strike into a charge before it is full.
			attackNow(SwingDir.combo(comboIndex++), 0, now);
			return;
		}
		if (opponentWindup) {
			return;
		}
		if (other != null && other.spamLocked(now) && reader.spams() && inReach) {
			// It waited for the spam lock: now.
			attackNow(SwingDir.combo(comboIndex++), 0, now);
			return;
		}
		// The defensive styles hold a guard when idle in reach.
		if (inReach && style == DuelStyle.BERTAHAN && !self.guarding() && random.nextFloat() < style.guard * 0.5F) {
			input(DuelIds.IN_GUARD_ON, SwingDir.KANAN, 0, 0, now);
			aiGuard = true;
			guardUntil = now + 20 + random.nextInt(20);
			return;
		}
		if (!inReach) {
			return;
		}
		float pressure = style.attack * (0.7F + 0.075F * tier);
		if (random.nextFloat() >= pressure) {
			return;
		}
		chooseAttack(now);
	}

	/** Picks feint, heavy, thrust or light by the style's table (the saint avoids its last three choices). */
	private void chooseAttack(long now) {
		for (int attempt = 0; attempt < 4; attempt++) {
			float roll = random.nextFloat();
			int choice;
			SwingDir dir;
			if (feintNext || roll < style.feint) {
				choice = 0;
				dir = SwingDir.combo(comboIndex);
			} else if ((roll -= style.feint) < style.heavy) {
				choice = 1;
				dir = heavyDir();
			} else if ((roll -= style.heavy) < style.thrust) {
				choice = 2;
				dir = SwingDir.TUSUK;
			} else {
				choice = 3;
				dir = style == DuelStyle.SANTO_PEDANG || style == DuelStyle.PENIPU ? SwingDir.byOrdinal(random.nextInt(8)) : SwingDir.combo(comboIndex);
			}
			int key = choice * 16 + dir.ordinal();
			if (style == DuelStyle.SANTO_PEDANG && recentChoices.contains(key) && attempt < 3) {
				continue;
			}
			recentChoices.addLast(key);
			while (recentChoices.size() > 3) {
				recentChoices.removeFirst();
			}
			switch (choice) {
				case 0 -> {
					feintNext = false;
					attackNow(dir, 0, now);
					Swing own = self.swing();
					if (own != null && own.live() && own.impact - now > ClashTiming.FEINT_LAST + 1) {
						feintAt = Math.max(now + 1, own.impact - ClashTiming.FEINT_LAST - 1 - random.nextInt(2));
					}
				}
				case 1 -> {
					input(DuelIds.IN_CHARGE_START, dir, 0, 0, now);
					WeaponClass weapon = self.weapon();
					boolean full = random.nextFloat() < 0.7F || dir == SwingDir.ATAS || dir == SwingDir.BAWAH;
					int charge = full ? weapon.heavyFull + 1 : weapon.heavyMin + random.nextInt(Math.max(1, weapon.heavyFull - weapon.heavyMin));
					if (style == DuelStyle.SANTO_PEDANG && reader.pressesEarly()) {
						charge += 2 + random.nextInt(3);
					}
					chargeReleaseAt = now + charge;
					waveRelease = full && mob.distanceTo(partner) > 4.0D && random.nextFloat() < 0.4F;
				}
				case 2 -> attackNow(SwingDir.TUSUK, DuelIds.INF_CROUCH, now);
				default -> {
					comboIndex++;
					if (style.rhythm > 0 && self.chain() > 0 && random.nextFloat() < 0.6F) {
						// The soft sword changes its rhythm: a pause before the next cut of the combo.
						delayedSwingAt = now + 1 + random.nextInt(style.rhythm);
						delayedDir = dir;
						delayedFlags = 0;
					} else {
						attackNow(dir, 0, now);
					}
				}
			}
			return;
		}
	}

	/** The direction of a heavy cut by style: the Mountain Cleave for the heavy sword, the sweep and thrust for the soft. */
	private SwingDir heavyDir() {
		float roll = random.nextFloat();
		return switch (style) {
			case BERTAHAN -> roll < 0.5F ? SwingDir.ATAS : roll < 0.75F ? SwingDir.ATAS_KANAN : SwingDir.KANAN;
			case PENIPU -> roll < 0.3F ? SwingDir.BAWAH : roll < 0.6F ? SwingDir.TUSUK : SwingDir.KIRI;
			case AGRESIF -> roll < 0.3F ? SwingDir.ATAS : roll < 0.6F ? SwingDir.ATAS_KANAN : roll < 0.8F ? SwingDir.KANAN : SwingDir.KIRI;
			default -> SwingDir.byOrdinal(random.nextInt(9));
		};
	}

	private void attackNow(SwingDir dir, int flags, long now) {
		if (aiGuard || self.guarding()) {
			aiGuard = false;
			guardUntil = -1L;
		}
		input(DuelIds.IN_SWING, dir, flags, 0, now);
	}

	/** Blade lock: pushes at its rate ±1, the release trick when the other pushes hard. */
	private void tickBind(long now) {
		mob.getNavigation().stop();
		var bind = self.bind();
		if (bind == null) {
			return;
		}
		Fighter other = bind.other(self);
		pushCarry += (pushRate + random.nextInt(3) - 1) / 20.0F;
		if (pushCarry >= 1.0F) {
			int count = (int) pushCarry;
			pushCarry -= count;
			input(DuelIds.IN_BIND_PUSH, SwingDir.KANAN, 0, count, now);
		}
		if (now % 3 == 0 && other.pushesWithin(now, 3) >= 3) {
			float chance = 0.15F * (tier + 1) * (style == DuelStyle.SANTO_PEDANG ? 2.0F : 1.0F) / 6.0F;
			if (random.nextFloat() < chance) {
				input(DuelIds.IN_BIND_RELEASE, SwingDir.KANAN, 0, 0, now);
			}
		}
	}

	/** {@code boneka serang <arah>}: one cut in the same direction every 30 ticks (deflect practice). */
	private void tickRepeat(long now) {
		double distance = mob.distanceTo(partner);
		if (distance > self.weapon().reach + 0.3D) {
			mob.getNavigation().moveTo(partner, 0.9D);
			return;
		}
		mob.getNavigation().stop();
		if (now >= nextRepeat && self.free(now) && now >= self.nextActionAt()) {
			nextRepeat = now + REPEAT_GAP;
			input(DuelIds.IN_SWING, repeatDir, repeatDir == SwingDir.TUSUK ? DuelIds.INF_CROUCH : 0, 0, now);
		}
	}

	/** {@code JURUS_BEBAS}: a disciple casts a technique now and then from a distance. */
	private void tickTechnique(long now) {
		if (session == null || session.rules().jurus() != DuelRuleSet.JurusRule.JURUS_BEBAS || !(mob instanceof DiscipleEntity disciple)) {
			return;
		}
		if (now - lastTechnique < TECHNIQUE_GAP || mob.distanceTo(partner) <= 5.0D || !self.free(now)) {
			return;
		}
		lastTechnique = now;
		DiscipleCultivation.tryCastInCombat(disciple, partner);
	}

	/** The opponent deflected one of its cuts (habit tracking; the saint feints next time against a riposter). */
	public void ownDeflected(long now) {
		if (reads()) {
			reader.opponentDeflected(now);
			if (style == DuelStyle.SANTO_PEDANG && reader.ripostesAfterDeflect()) {
				feintNext = true;
			}
		}
	}

	private void input(int kind, SwingDir dir, int flags, int arg, long now) {
		Fighters.input(mob, kind, dir.ordinal(), flags, arg, now);
	}

	/** For {@code uji duel info}. */
	public String summary() {
		return style.id() + " T" + tier + " react=" + reaction + " sigma=" + String.format(java.util.Locale.ROOT, "%.2f", sigma) + " bite="
				+ String.format(java.util.Locale.ROOT, "%.2f", feintBite) + " push=" + pushRate + " " + reader.summary();
	}
}
