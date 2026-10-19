package dev.kultivator.technique.spec.run;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.technique.spec.dsl.Flow;
import dev.kultivator.technique.spec.dsl.Phase;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The flows of DESIGN 22.3.4 on a running cast: CHARGE (a fixed wind-up, slowed −90%, cast again to release early at ≥ 50%,
 * a hit of ≥ 20% of max health interrupts: half the qi back, 25% of the cooldown), CHANNEL (the steps repeat every
 * pulse; moving 1.5 blocks, casting anything else, casting again or a big hit ends it; qi drains), TOGGLE (a stance until
 * cast again or the qi runs out) and STAGES (recasts in the window advance the stage; the cooldown starts after the last
 * stage or when the window runs out).
 */
final class Flows {

	private static final Identifier CHARGE_SLOW = Kultivator.id("charge_slow");

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Flows::afterDamage);
	}

	/** Starts the flow of a new cast: the charge first, otherwise the timeline. */
	static void begin(Caster cast) {
		long now = cast.level.getGameTime();
		Flow.Charge charge = cast.spec.charge();
		if (cast.spec.stages() != null) {
			cast.stage = 1;
			if (cast.parent == null) {
				cast.stageWindowUntil = now + Math.max(1, cast.spec.stages().window() + (cast.special("stage_window") ? 20 : 0) + cast.durationTicks);
				cast.state.putLong(STAGE_AT, now);
				stageHint(cast, 1, now);
			}
		}
		// Copies (echo, extra cast, replay) run their timeline once: only the cast the caster can recast holds a stance.
		if (cast.spec.toggle() != null && cast.parent == null) {
			cast.toggled = true;
		}
		if (charge != null && cast.parent == null) {
			cast.charging = true;
			cast.chargeUntil = now + Math.max(1, charge.ticks());
			slow(cast.caster, true);
			// the build-up: at the caster, toward the locked target when there is one (a finger, a palm falling on it)
			net.minecraft.world.entity.LivingEntity locked = cast.target();
			Vec3 toward = locked != null ? locked.position() : cast.aim;
			cast.fx(Phase.CHARGE, cast.caster.position(), toward, 0.0F, (float) cast.caster.position().distanceTo(toward), 1, charge.ticks(),
					locked != null ? locked.getId() : -1, 0, 1, 0, 0);
			hint(cast, Component.translatable("kultivator.teknik.hint.charge", cast.spec.displayName(), seconds(charge.ticks())));
			return;
		}
		startTimeline(cast);
	}

	/** Legacy and special casts: only the stage window and the toggle are tracked (their own code does the rest). */
	static void beginFlags(Caster cast) {
		long now = cast.level.getGameTime();
		if (cast.spec.stages() != null) {
			cast.stage = 1;
			if (cast.parent == null) {
				cast.stageWindowUntil = now + Math.max(1, cast.spec.stages().window() + cast.durationTicks);
				cast.state.putLong(STAGE_AT, now);
			}
		}
		if (cast.spec.toggle() != null && cast.parent == null) {
			cast.toggled = true;
		}
		cast.timelineStart = now;
	}

	/** The action-bar line that tells the player how a flow wants to be used (own casts only, never copies). */
	private static void hint(Caster cast, Component line) {
		if (cast.parent == null && cast.caster instanceof ServerPlayer player) {
			dev.kultivator.util.Msg.actionBar(player, line.copy().withColor(0xFFD58A));
		}
	}

	/** "Stage 1/3: cast again within 30 s for the next", or the last stage. */
	private static void stageHint(Caster cast, int stage, long now) {
		Flow.Stages stages = cast.spec.stages();
		if (stages == null) {
			return;
		}
		if (stage >= stages.count()) {
			hint(cast, Component.translatable("kultivator.teknik.hint.stage_last", cast.spec.displayName(), stage, stages.count()));
			return;
		}
		long left = Math.max(0L, cast.stageWindowUntil - now);
		hint(cast, Component.translatable("kultivator.teknik.hint.stage_next", cast.spec.displayName(), stage, stages.count(), seconds((int) left)));
	}

	private static String seconds(int ticks) {
		return dev.kultivator.util.Msg.number(ticks / 20.0D);
	}

	/** State key: the tick the current stage began (the stage gap is measured from it). */
	private static final String STAGE_AT = "stage_at";
	/** A disciple's stance lasts at most this long, and ends earlier once no hostile is within {@link #DISCIPLE_TOGGLE_RANGE}. */
	static final int DISCIPLE_TOGGLE_TICKS = 200;
	static final double DISCIPLE_TOGGLE_RANGE = 24.0D;

	private static void startTimeline(Caster cast) {
		long now = cast.level.getGameTime();
		cast.timelineStart = now;
		Flow.Channel channel = cast.spec.channel();
		if (channel != null) {
			cast.channelUntil = now + Math.max(1, channel.ticks());
			cast.state.putDouble("channel_x", cast.caster.getX());
			cast.state.putDouble("channel_y", cast.caster.getY());
			cast.state.putDouble("channel_z", cast.caster.getZ());
			hint(cast, Component.translatable("kultivator.teknik.hint.channel", cast.spec.displayName(), seconds(channel.ticks())));
		}
		Steps.startTimeline(cast);
	}

	/** Every tick of an active cast. */
	static void tick(Caster cast, long now) {
		if (cast.charging) {
			if (now >= cast.chargeUntil) {
				release(cast, 1.0D);
			}
			return;
		}
		if (cast.channelUntil > 0L && !cast.ended) {
			Vec3 start = new Vec3(cast.state.getDoubleOr("channel_x", cast.caster.getX()), cast.state.getDoubleOr("channel_y", cast.caster.getY()),
					cast.state.getDoubleOr("channel_z", cast.caster.getZ()));
			if (now >= cast.channelUntil) {
				cast.channelUntil = 0L;
			} else if (cast.caster.position().distanceTo(start) > Flow.Channel.MOVE_LIMIT) {
				cast.channelUntil = 0L;
				hint(cast, Component.translatable("kultivator.teknik.channel.moved", cast.spec.displayName()));
				cast.end(EndReason.CANCELLED);
				return;
			} else if ((now - cast.timelineStart) % 20L == 19L && cast.spec.qiPerSecond() > 0.0D && cast.parent == null
					&& !cast.options.free() && !drain(cast, cast.spec.qiPerSecond())) {
				cast.channelUntil = 0L;
				hint(cast, Component.translatable("kultivator.teknik.channel.no_qi", cast.spec.displayName()));
				cast.end(EndReason.CANCELLED);
				return;
			}
		}
		if (cast.toggled && !cast.ended && (now - cast.startTick) % 20L == 19L && cast.parent == null && !cast.options.free()) {
			double perSecond = cast.spec.toggle() == null ? 0.0D : cast.spec.toggle().qiPerSecond();
			if (perSecond > 0.0D && !drain(cast, perSecond)) {
				cast.toggled = false;
				if (cast.caster instanceof ServerPlayer player) {
					dev.kultivator.technique.Techniques.refuse(player, Component.translatable("kultivator.teknik.toggle.no_qi", cast.spec.displayName()));
				}
			}
		}
		if (cast.toggled && !cast.ended && cast.caster instanceof dev.kultivator.disciple.DiscipleEntity && (now - cast.startTick) % 20L == 19L) {
			// a disciple cannot cast a stance off: it ends after 10 s, or once the fight around it is over
			if (now - cast.startTick >= DISCIPLE_TOGGLE_TICKS || cast.hostiles(cast.caster.position(), DISCIPLE_TOGGLE_RANGE, false).isEmpty()) {
				cast.toggled = false;
			}
		}
		if (cast.formActive && !cast.ended && cast.spec.stages() != null && cast.spec.special() == null && cast.spec.qiPerSecond() > 0.0D
				&& cast.channelUntil <= 0L && cast.parent == null && !cast.options.free() && (now - cast.startTick) % 20L == 19L
				&& !(cast.caster instanceof ServerPlayer bypassing && TechniqueRunner.bypass(bypassing))) {
			// a STAGES form ("TOG 5 qi/s" while grown, fa_tian_xiang_di) drains every second; out of qi the form ends
			if (!drain(cast, cast.spec.qiPerSecond())) {
				Forms.end(cast.caster, EndReason.CANCELLED);
				if (cast.caster instanceof ServerPlayer player) {
					dev.kultivator.technique.Techniques.refuse(player, Component.translatable("kultivator.teknik.toggle.no_qi", cast.spec.displayName()));
				}
			}
		}
		if (cast.stageWindowUntil > 0L && now >= cast.stageWindowUntil) {
			cast.stageWindowUntil = 0L;
			Steps.windowEnded(cast);
		}
	}

	/** A recast of the same spec while it runs (22.3.4). True when the recast was consumed. */
	static boolean recast(Caster cast, ServerPlayer player) {
		long now = cast.level.getGameTime();
		if (cast.charging) {
			Flow.Charge charge = cast.spec.charge();
			double share = charge == null ? 1.0D : (now - (cast.chargeUntil - charge.ticks())) / (double) Math.max(1, charge.ticks());
			release(cast, Math.max(charge == null ? 0.5D : charge.minShare(), Math.min(1.0D, share)));
			return true;
		}
		if (cast.channelUntil > now) {
			cast.channelUntil = 0L;
			hint(cast, Component.translatable("kultivator.teknik.channel.recast", cast.spec.displayName()));
			cast.end(EndReason.CANCELLED);
			return true;
		}
		if (cast.toggled) {
			cast.toggled = false;
			// the stance is cast off: its glow and held layers end on every client now (S8)
			cast.abortFx();
			return true;
		}
		Flow.Stages stages = cast.spec.stages();
		if (stages != null && cast.stageWindowUntil > now && cast.stage < stages.count()) {
			int next = cast.stage + 1;
			long since = now - cast.state.getLongOr(STAGE_AT, cast.startTick);
			if (stages.gap() > 0 && since < stages.gap()) {
				// "recast in the form (≥ 60t apart)": too soon spends nothing
				dev.kultivator.technique.Techniques.refuse(player, Component.translatable("kultivator.teknik.refuse.stage_gap", cast.spec.displayName(),
						TechniqueRunner.seconds(stages.gap() - since)));
				return true;
			}
			Realm gate = stages.realmFor(next);
			if (gate != Realm.MORTAL && !Cultivators.get(player).realm().atLeast(gate) && !cast.options.bypassGates()) {
				dev.kultivator.technique.Techniques.refuse(player, Component.translatable("kultivator.teknik.refuse.stage_realm",
						cast.spec.displayName(), gate.displayName()));
				return true;
			}
			SpecialTechnique handler = cast.spec.special() == null ? null : dev.kultivator.data.TechniqueRegistry.special(cast.spec.special());
			if (handler != null) {
				// a special's stage recast asks its blocker first, seeing the stage it would cast; a refusal spends nothing
				int current = cast.stage;
				cast.stage = next;
				Component why;
				try {
					why = handler.blocker(cast);
				} finally {
					cast.stage = current;
				}
				if (why != null) {
					dev.kultivator.technique.Techniques.refuse(player, why);
					return true;
				}
			}
			int qi = stages.qiFor(next, 0);
			if (qi > 0 && !cast.options.free() && !TechniqueRunner.bypass(player)) {
				int cost = (int) Math.ceil(qi * dev.kultivator.technique.spec.TechniquePower.masteryQi(cast.mastery));
				if (!dev.kultivator.cultivation.Qi.trySpend(player, cost)) {
					dev.kultivator.technique.Techniques.refuse(player, Component.translatable("kultivator.techniques.refuse.qi", cast.spec.displayName(),
							dev.kultivator.util.Msg.number(Cultivators.get(player).qi()), String.valueOf(cost)));
					return true;
				}
			}
			cast.stage = next;
			cast.stageWindowUntil = next >= stages.count() ? 0L : now + Math.max(1, stages.window());
			cast.state.putLong(STAGE_AT, now);
			stageHint(cast, next, now);
			Steps.startStage(cast, next);
			if (next >= stages.count()) {
				Steps.windowEnded(cast);
			}
			return true;
		}
		return false;
	}

	/** The charge completes (or is released early at {@code share}): the timeline begins. */
	static void release(Caster cast, double share) {
		cast.charging = false;
		cast.chargeShare = Math.max(0.0D, Math.min(1.0D, share));
		slow(cast.caster, false);
		startTimeline(cast);
	}

	/** A big hit interrupts a charge (half the qi back, 25% of the cooldown) or a channel. */
	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (damageTaken <= 0.0F || !TechniqueRunner.hasActive(entity)) {
			return;
		}
		double share = damageTaken / Math.max(1.0F, entity.getMaxHealth());
		for (Caster cast : TechniqueRunner.activeOf(entity)) {
			if (cast.charging && cast.spec.charge() != null && share >= cast.spec.charge().interrupt()) {
				cast.charging = false;
				slow(entity, false);
				if (entity instanceof ServerPlayer player && cast.parent == null) {
					dev.kultivator.cultivation.Qi.restore(player, cast.qiPaid * 0.5D);
					Cultivators.get(player).setCooldown(cast.spec.id(), cast.level.getGameTime(), Math.max(10, cast.cooldownSet / 4));
					Cultivators.dirty(player);
					dev.kultivator.technique.Techniques.refuse(player, Component.translatable("kultivator.teknik.charge.interrupted", cast.spec.displayName()));
				}
				cast.end(EndReason.CANCELLED);
			} else if (cast.channelUntil > cast.level.getGameTime() && cast.spec.channel() != null && share >= cast.spec.channel().interrupt()) {
				cast.channelUntil = 0L;
				cast.end(EndReason.CANCELLED);
			}
		}
	}

	/** True while a flow keeps the cast alive (charge, channel, toggle, an open stage window). */
	static boolean holding(Caster cast) {
		long now = cast.level.getGameTime();
		return cast.charging || cast.channelUntil > now || cast.toggled || cast.stageWindowUntil > now;
	}

	/** Cast end: the slow comes off; toggles and stages start their cooldown now. */
	static void ended(Caster cast, EndReason reason) {
		if (cast.charging) {
			cast.charging = false;
			slow(cast.caster, false);
		}
		if (cast.parent == null && (cast.spec.toggle() != null || cast.spec.stages() != null) && cast.caster instanceof ServerPlayer player
				&& !cast.options.free() && !TechniqueRunner.bypass(player)) {
			Cultivators.get(player).setCooldown(cast.spec.id(), cast.level.getGameTime(), cast.cooldownSet);
			Cultivators.dirty(player);
		}
	}

	private static boolean drain(Caster cast, double perSecond) {
		if (cast.caster instanceof ServerPlayer player) {
			return dev.kultivator.cultivation.Qi.trySpend(player, perSecond);
		}
		if (cast.caster instanceof dev.kultivator.disciple.DiscipleEntity disciple) {
			double cost = perSecond * dev.kultivator.technique.spec.TechniquePower.DISCIPLE_QI_FACTOR;
			if (disciple.data().qi() + 1.0E-6D < cost) {
				return false;
			}
			disciple.data().setQi(disciple.data().qi() - cost);
			return true;
		}
		return true;
	}

	private static void slow(LivingEntity who, boolean on) {
		AttributeInstance instance = who.getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return;
		}
		if (on) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(CHARGE_SLOW, -0.9D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			instance.removeModifier(CHARGE_SLOW);
		}
	}

	private Flows() {
	}
}
