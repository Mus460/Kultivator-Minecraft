package dev.kultivator.disciple.power;

import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Nadi Dewa Sesat, the Heretic God Veins (DESIGN 20.3.5; Against the Gods ch. 45-47): ordinary-looking veins (TRASH /
 * Merah, exempt from the red cap) revealed at the Golden Core breakthrough as Nila with a special factor of 2.0. Seven
 * gates open at Golden Core, Nascent Soul, Deity Transformation, Dao Union, Immortal Ascension layer 5, True Immortal and
 * star 9, each +15% damage. "Ledakan Gerbang" (automatic below half health in a boss fight, or ordered): 20 s at
 * damage x(1 + 0.25 x gates), then +30% damage taken and Weakness for 30 s; cooldown 5 minutes.
 */
final class HereticGates {

	public static final double SPECIAL_FACTOR = 2.0D;
	public static final int MAX_GATES = 7;
	public static final double GATE_DAMAGE = 0.15D;
	public static final int BURST_TICKS = 400;
	public static final double BURST_PER_GATE = 0.25D;
	public static final int EXHAUST_TICKS = 600;
	public static final double EXHAUST_TAKEN = 1.3D;
	public static final int BURST_COOLDOWN = 6000;
	public static final double BOSS_RANGE = 24.0D;

	static void register() {
		DiscipleHooks.addDamageFactor((entity, target, technique) -> {
			if (!PowerCore.powered(entity) || PowerCore.physique(entity.data()) != PowerIds.PHYSIQUE_HERETIC) {
				return 1.0D;
			}
			int gates = gates(entity.data());
			double factor = 1.0D + GATE_DAMAGE * gates;
			if (bursting(entity)) {
				factor *= 1.0D + BURST_PER_GATE * gates;
			}
			return factor;
		});
		DiscipleHooks.addHurtFactor((entity, source) -> PowerCore.powered(entity) && exhausted(entity) ? EXHAUST_TAKEN : 1.0D);
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onEnd(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
				if (success && PowerCore.physique(entity.data()) == PowerIds.PHYSIQUE_HERETIC) {
					update(entity);
				}
			}
		});
	}

	static boolean revealed(DiscipleData data) {
		return PowerCore.tag(data).getBooleanOr("heretic_revealed", false);
	}

	static int gates(DiscipleData data) {
		return Math.max(0, Math.min(MAX_GATES, PowerCore.tag(data).getIntOr("gates", 0)));
	}

	/** Gates the rank and stars allow now (DESIGN 20.3.5). */
	static int gatesFor(DiscipleData data) {
		Rank rank = data.rank();
		Realm realm = rank.realm();
		int gates = 0;
		if (realm.atLeast(Realm.GOLDEN_CORE)) {
			gates++;
		}
		if (realm.atLeast(Realm.NASCENT_SOUL)) {
			gates++;
		}
		if (realm.atLeast(Realm.DEITY_TRANSFORMATION)) {
			gates++;
		}
		if (realm.atLeast(Realm.DAO_UNION)) {
			gates++;
		}
		if (realm == Realm.IMMORTAL_ASCENSION && rank.layer() >= 4 || realm.atLeast(Realm.TRUE_IMMORTAL)) {
			gates++;
		}
		if (realm.atLeast(Realm.TRUE_IMMORTAL)) {
			gates++;
		}
		if (DisciplePaths.stars(data) >= 9) {
			gates++;
		}
		return gates;
	}

	static boolean bursting(DiscipleEntity entity) {
		return !PowerCore.ready(entity.data(), "burst_end", PowerCore.now(entity));
	}

	static boolean exhausted(DiscipleEntity entity) {
		long now = PowerCore.now(entity);
		return PowerCore.ready(entity.data(), "burst_end", now) && !PowerCore.ready(entity.data(), "exhaust_end", now)
				&& PowerCore.physique(entity.data()) == PowerIds.PHYSIQUE_HERETIC;
	}

	/** Reveal at Golden Core and open the gates the disciple has reached (FX and a line per gate). */
	static void update(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (PowerCore.physique(data) != PowerIds.PHYSIQUE_HERETIC || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		CompoundTag tag = PowerCore.edit(data);
		if (!revealed(data)) {
			if (!data.realm().atLeast(Realm.GOLDEN_CORE)) {
				return;
			}
			// Yun Che's veins looked absolutely ordinary until they opened.
			tag.putBoolean("heretic_revealed", true);
			TalentEffects.liftColour(entity, PowerIds.COLOUR_INDIGO);
			PowerCore.fx(level, entity.position(), PowerIds.FX_REVEAL, 2, 0.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.WITHER_SPAWN, 0.6F, 1.6F);
			PowerCore.sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
			PowerCore.tell(entity, PowerCore.key("heretic.reveal", PowerCore.name(entity)).withColor(0xD8263A));
			ServerPlayer master = entity.owner();
			if (master != null) {
				Advancements.grant(master, "heretic_gates");
			}
		}
		int now = gates(data);
		int target = gatesFor(data);
		if (target <= now) {
			return;
		}
		tag.putInt("gates", target);
		for (int gate = now + 1; gate <= target; gate++) {
			PowerCore.fx(level, entity.position(), PowerIds.FX_GATE_OPEN, gate, 0.0F);
			PowerCore.tell(entity, PowerCore.key("heretic.gate", PowerCore.name(entity), gate, PowerCore.key("gate." + gate))
					.withColor(0xFF4A4A));
		}
		PowerCore.sound(level, entity.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2F, 0.7F);
		PowerCore.sound(level, entity.position(), SoundEvents.FIRECHARGE_USE, 0.8F, 0.5F);
	}

	/** Why "Ledakan Gerbang" cannot be ordered now, or null when it starts. */
	static @Nullable String burst(DiscipleEntity entity, boolean ordered) {
		DiscipleData data = entity.data();
		if (PowerCore.physique(data) != PowerIds.PHYSIQUE_HERETIC || !revealed(data) || gates(data) <= 0) {
			return "why.no_gates";
		}
		long now = PowerCore.now(entity);
		if (!PowerCore.ready(data, "burst", now)) {
			return "why.cooldown";
		}
		if (data.breakingThrough()) {
			return "why.busy";
		}
		if (!(entity.level() instanceof ServerLevel level)) {
			return "why.busy";
		}
		PowerCore.setCooldown(data, "burst", now + BURST_COOLDOWN);
		PowerCore.setCooldown(data, "burst_end", now + BURST_TICKS);
		PowerCore.setCooldown(data, "exhaust_end", now + BURST_TICKS + EXHAUST_TICKS);
		int gates = gates(data);
		PowerCore.fx(level, entity.position(), PowerIds.FX_GATE_BURST, gates, BURST_TICKS);
		PowerCore.sound(level, entity.position(), SoundEvents.RAVAGER_ROAR, 0.7F, 1.4F);
		PowerCore.sound(level, entity.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.2F);
		PowerCore.tell(entity, PowerCore.key(ordered ? "heretic.burst_ordered" : "heretic.burst", PowerCore.name(entity), gates,
				Math.round((1.0D + BURST_PER_GATE * gates) * 100.0D) + "%").withStyle(ChatFormatting.RED));
		DisciplePowers.syncPower(entity);
		PowerPulses.later(entity, BURST_TICKS, e -> {
			// The price of opening the gates too wide: weakness and a body that takes more damage.
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EXHAUST_TICKS, 0, false, true, true), e);
			DisciplePowers.syncPower(e);
			PowerCore.tell(e, PowerCore.key("heretic.exhausted", PowerCore.name(e)).withColor(0xB06A6A));
		});
		return null;
	}

	/** In a fight: below half health with a boss within 24 blocks, the gates burst open by themselves. */
	static boolean combatCast(DiscipleEntity entity, LivingEntity target) {
		DiscipleData data = entity.data();
		ServerPlayer master = entity.owner();
		if (master == null || PowerCore.physique(data) != PowerIds.PHYSIQUE_HERETIC || !revealed(data)
				|| entity.getHealth() >= entity.getMaxHealth() * 0.5F || !PowerCore.ready(data, "burst", PowerCore.now(entity))) {
			return false;
		}
		if (!PowerCore.isBoss(target) && !PowerCore.bossNear(entity, master, BOSS_RANGE)) {
			return false;
		}
		return burst(entity, false) == null;
	}

	private HereticGates() {
	}
}
