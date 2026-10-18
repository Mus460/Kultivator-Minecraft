package dev.kultivator.cultivation;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Qi Deviation (Penyimpangan Qi): the injury of a failed breakthrough (DESIGN section 3). While it lasts the
 * cultivator has no qi regeneration and half XP (read by Qi / Progression through {@code data.deviating()}), Weakness I,
 * Slowness I and now and then Nausea. The injury itself is never lethal.
 */
public final class Deviation {

	/** Damage type {@code kultivator:qi_deviation} (data/kultivator/damage_type/qi_deviation.json). */
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Kultivator.id("qi_deviation"));
	/** A deviation never lasts longer than this (10 minutes). */
	public static final int MAX_TICKS = 12000;
	/** Weakness and Slowness are re-applied this often while deviating. */
	private static final int EFFECT_PERIOD = 200;

	public static void init() {
	}

	public static void tick(ServerPlayer player, CultivationData data) {
		if (!data.deviating() || player.isDeadOrDying()) {
			return;
		}
		int left = data.deviationTicks() - 1;
		data.setDeviationTicks(left);
		if (left <= 0) {
			// Weakness and Slowness were given with durations that end right now, so nothing needs removing.
			Msg.chat(player, Component.translatable("kultivator.breakthrough.deviation.recovered").withStyle(ChatFormatting.GREEN));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
					SoundSource.PLAYERS, 0.8F, 1.2F);
			Cultivators.refreshVisual(player);
			Cultivators.syncNow(player);
			// DEVIATION_RECOVERED feats (DESIGN 22.8.9).
			dev.kultivator.technique.spec.run.FeatTracker.onDeviationRecovered(player);
			return;
		}
		if (left % EFFECT_PERIOD == 0) {
			applyEffects(player, left);
		}
		if (left % 20 == 0) {
			if (player.getRandom().nextInt(60) == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, Math.min(100, left), 0, false, false, true));
			}
			// Keeps the HUD timer right.
			Cultivators.dirty(player);
		}
	}

	/**
	 * Injures the player with a Qi Deviation (never lethal). {@code severity} 1.0 is a normal failed breakthrough; the
	 * duration and the injury scale with it. An existing deviation is extended (up to {@link #MAX_TICKS}).
	 */
	public static void apply(ServerPlayer player, double severity) {
		if (Cultivators.get(player).ascended()) {
			// The immortal gift of an ascended True Immortal (DESIGN 17): no Qi Deviation can take hold.
			Msg.chat(player, Component.translatable("kultivator.ascension.deviation_immune").withStyle(ChatFormatting.GOLD));
			return;
		}
		CultivationData data = Cultivators.get(player);
		double scale = Math.max(0.1D, severity);
		boolean already = data.deviating();
		int added = (int) Math.round((2400 + 600 * data.realm().ordinal()) * scale);
		int total = Math.min(MAX_TICKS, data.deviationTicks() + Math.max(20, added));
		data.setDeviationTicks(total);

		applyEffects(player, total);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, Math.min(100, total), 0, false, false, true));
		injure(player, player.getMaxHealth() * 0.25F * (float) Math.min(2.0D, scale));

		Component seconds = Component.literal(String.valueOf(total / 20)).withStyle(ChatFormatting.RED);
		Msg.chat(player, Component.translatable(already ? "kultivator.breakthrough.deviation.worse" : "kultivator.breakthrough.deviation.start",
				seconds).withStyle(ChatFormatting.DARK_RED));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.8F, 0.6F);
		Fx.send(level, player.position(), FxType.DEVIATION, 0, 0, (float) scale);
		Advancements.grant(player, "qi_deviation");
		Cultivators.refreshVisual(player);
		Cultivators.syncNow(player);
	}

	/** Ends a deviation at once (healing pills, commands). */
	public static void clear(ServerPlayer player) {
		Cultivators.get(player).setDeviationTicks(0);
		Cultivators.dirty(player);
		Cultivators.refreshVisual(player);
	}

	/** Ticks of deviation left (0 when healthy). */
	public static int ticksLeft(ServerPlayer player) {
		return Cultivators.get(player).deviationTicks();
	}

	/** The {@code kultivator:qi_deviation} damage source of {@code level}. */
	public static DamageSource damageSource(ServerLevel level) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE));
	}

	/** Weakness I and Slowness I, short enough to end together with the deviation. */
	private static void applyEffects(ServerPlayer player, int ticksLeft) {
		int duration = Math.min(EFFECT_PERIOD + 20, ticksLeft);
		if (duration <= 0) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0, false, false, true));
	}

	/** Hurts with the deviation damage type, always leaving at least 1 health. Creative players are spared. */
	private static void injure(ServerPlayer player, float amount) {
		if (player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable || amount <= 0.0F) {
			return;
		}
		float pool = player.getHealth() + player.getAbsorptionAmount();
		float dealt = Math.min(amount, pool - 1.0F);
		if (dealt > 0.0F && !player.hurtServer(player.level(), damageSource(player.level()), dealt) && player.isAlive()) {
			// Just respawned (spawn protection until the client has loaded): the injury still happens.
			player.setHealth(Math.max(1.0F, player.getHealth() - dealt));
		}
		if (player.isAlive() && player.getHealth() < 1.0F) {
			player.setHealth(1.0F);
		}
	}

	private Deviation() {
	}
}
