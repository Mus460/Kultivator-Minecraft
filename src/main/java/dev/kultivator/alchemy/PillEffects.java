package dev.kultivator.alchemy;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Deviation;
import dev.kultivator.cultivation.PillToxicity;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the refined pills do (DESIGN section 9, phase 4). Every number is multiplied by the pill's quality; cultivation
 * XP is also multiplied by {@link PillToxicity#effectiveness} and goes through {@link Progression}. After the effect
 * the pill's toxin is added (the Immortal Pill instead purges all toxicity).
 */
public final class PillEffects {

	/** Ticks before another pill of the same kind can be swallowed. */
	public static final int COOLDOWN = 20;
	private static final int REFUSE_COLOR = 0xFF8A7A;

	/**
	 * Swallows one pill of {@code stack} (server side). Returns false (nothing consumed) when the pill cannot be taken
	 * now; the player is told why.
	 */
	public static boolean swallow(ServerPlayer player, ItemStack stack, PillKind kind) {
		if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) {
			return false;
		}
		CultivationData data = Cultivators.get(player);
		Component name = stack.getHoverName().copy();
		if (kind == PillKind.SOUL_NURTURING && !data.realm().atLeast(Realm.NASCENT_SOUL)) {
			Msg.chat(player, Component.translatable("kultivator.alchemy.pill.too_weak", name, Realm.NASCENT_SOUL.displayName())
					.withColor(REFUSE_COLOR));
			return false;
		}
		PillQuality quality = AlchemyComponents.quality(stack);
		// The cooldown group comes from the stack, so set it before the stack may become empty.
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		stack.consume(1, player);
		try {
			apply(player, kind, quality, name);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Could not apply the effect of {}", kind.id(), exception);
		}
		return true;
	}

	/** The effect, feedback, achievements and finally the toxin. */
	private static void apply(ServerPlayer player, PillKind kind, PillQuality quality, Component name) {
		CultivationData data = Cultivators.get(player);
		double m = quality.multiplier();
		double efficacy = Math.max(0.0D, Math.min(1.0D, PillToxicity.effectiveness(data)));
		double bar = data.rank().xpRequired();
		List<Component> parts = new ArrayList<>();

		switch (kind) {
			case QI_GATHERING -> xp(player, Math.min(bar * PillKind.QI_GATHERING_XP, PillKind.QI_GATHERING_MAX_XP) * m * efficacy, parts);
			case QI_RECOVERY -> qi(player, Qi.max(data) * PillKind.QI_RECOVERY_QI * m, parts);
			case FOUNDATION -> foundation(data, m, parts);
			case GOLDEN_CORE -> tribulation(data, 1.0D - PillKind.GOLDEN_CORE_REDUCTION * m, parts);
			case TRIBULATION_WARDING -> tribulation(data, 1.0D - PillKind.WARDING_REDUCTION * m, parts);
			case HEART_CALMING -> heartCalming(player, data, m, parts);
			case HEALING -> healing(player, data, quality, m, parts);
			case SOUL_NURTURING -> xp(player, Math.min(bar * PillKind.SOUL_NURTURING_XP, PillKind.SOUL_NURTURING_MAX_XP) * m * efficacy, parts);
			case IMMORTAL -> {
				xp(player, bar * PillKind.IMMORTAL_XP * m * efficacy, parts);
				Qi.fill(player);
				parts.add(Component.translatable("kultivator.alchemy.part.qi_all"));
			}
		}
		if (kind.givesXp() && efficacy < 0.995D) {
			parts.add(Component.translatable("kultivator.alchemy.part.efficacy", Msg.number(efficacy * 100.0D + 1.0E-6D)));
		}
		Cultivators.dirty(player);

		Msg.actionBar(player, Component.translatable("kultivator.alchemy.pill.taken", name, join(parts)).withColor(kind.color()));
		int strength = Math.min(3, kind.strength() + (quality == PillQuality.PERFECT ? 1 : 0));
		Fx.send(player.level(), player.position(), FxType.PILL, kind.color(), strength, (float) efficacy);
		sounds(player, kind, strength);

		Advancements.grant(player, "first_pill");
		if (kind == PillKind.IMMORTAL) {
			CultivationData after = Cultivators.get(player);
			if (after.toxicity() > 0.0D) {
				after.setToxicity(0.0D);
				Msg.chat(player, Component.translatable("kultivator.alchemy.pill.purged").withColor(kind.color()));
			}
			Cultivators.dirty(player);
		} else {
			PillToxicity.add(player, kind.toxin(quality));
		}
	}

	private static void xp(ServerPlayer player, double amount, List<Component> parts) {
		double gained = Progression.grantXp(player, amount, XpSource.PILL);
		if (gained > 0.0D) {
			parts.add(Component.translatable("kultivator.alchemy.part.xp", Msg.number(Math.max(1.0D, gained))));
		}
	}

	private static void qi(ServerPlayer player, double amount, List<Component> parts) {
		double before = Cultivators.get(player).qi();
		Qi.restore(player, amount);
		double gained = Cultivators.get(player).qi() - before;
		parts.add(gained >= 0.5D
				? Component.translatable("kultivator.alchemy.part.qi", Msg.number(gained + 1.0E-6D))
				: Component.translatable("kultivator.alchemy.part.qi_full"));
	}

	private static void foundation(CultivationData data, double m, List<Component> parts) {
		boolean best = data.realm() == Realm.QI_REFINING;
		double bonus = PillKind.FOUNDATION_BONUS * m * (best ? 1.0D : PillKind.FOUNDATION_OFF_REALM);
		double before = data.nextBreakthroughBonus();
		// Outside Qi Refining, pills alone never push the bonus past FOUNDATION_OFF_REALM_MAX (other sources still stack).
		data.setNextBreakthroughBonus(best ? before + bonus : Math.max(before, Math.min(before + bonus, PillKind.FOUNDATION_OFF_REALM_MAX)));
		double added = data.nextBreakthroughBonus() - before;
		if (added > 0.0005D) {
			parts.add(Component.translatable("kultivator.alchemy.part.breakthrough", percent(added), percent(data.nextBreakthroughBonus())));
		} else {
			parts.add(Component.translatable("kultivator.alchemy.part.breakthrough_max"));
		}
		if (!best) {
			parts.add(Component.translatable("kultivator.alchemy.part.breakthrough_weak"));
		}
	}

	private static void tribulation(CultivationData data, double factor, List<Component> parts) {
		double before = data.nextTribulationFactor();
		data.setNextTribulationFactor(before * factor);
		double after = data.nextTribulationFactor();
		parts.add(after < before - 0.0005D
				? Component.translatable("kultivator.alchemy.part.tribulation", percent(after))
				: Component.translatable("kultivator.alchemy.part.tribulation_max"));
	}

	private static void heartCalming(ServerPlayer player, CultivationData data, double m, List<Component> parts) {
		double before = data.heartDemonPressure();
		data.setHeartDemonPressure(before - PillKind.HEART_PRESSURE * m);
		double removed = before - data.heartDemonPressure();
		if (removed > 0.05D) {
			parts.add(Component.translatable("kultivator.alchemy.part.pressure", Msg.number(removed + 1.0E-6D),
					Msg.number(data.heartDemonPressure())));
		}
		boolean eased = shortenDeviation(player, data, PillKind.HEART_DEVIATION * m, parts);
		if (removed <= 0.05D && !eased) {
			parts.add(Component.translatable("kultivator.alchemy.part.calm"));
		}
	}

	private static void healing(ServerPlayer player, CultivationData data, PillQuality quality, double m, List<Component> parts) {
		float max = player.getMaxHealth();
		float before = player.getHealth();
		player.heal((float) Math.min(max, max * PillKind.HEALING_HEALTH * m));
		float healed = player.getHealth() - before;
		if (healed >= 0.5F) {
			parts.add(Component.translatable("kultivator.alchemy.part.heal", Msg.number(healed + 1.0E-4D)));
		} else {
			parts.add(Component.translatable("kultivator.alchemy.part.health_full"));
		}
		if (quality == PillQuality.PERFECT && data.deviating()) {
			Deviation.clear(player);
			parts.add(Component.translatable("kultivator.alchemy.part.deviation_cured"));
		} else {
			shortenDeviation(player, data, PillKind.HEALING_DEVIATION * m, parts);
		}
	}

	/** Cuts {@code share} of the remaining Qi Deviation; true when there was one. */
	private static boolean shortenDeviation(ServerPlayer player, CultivationData data, double share, List<Component> parts) {
		if (!data.deviating()) {
			return false;
		}
		int left = (int) Math.round(data.deviationTicks() * Math.max(0.0D, 1.0D - share));
		if (left <= 0) {
			Deviation.clear(player);
			parts.add(Component.translatable("kultivator.alchemy.part.deviation_cured"));
		} else {
			data.setDeviationTicks(left);
			parts.add(Component.translatable("kultivator.alchemy.part.deviation", Msg.number(left / 20.0D)));
		}
		return true;
	}

	private static void sounds(ServerPlayer player, PillKind kind, int strength) {
		ServerLevel level = player.level();
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.4F, 1.4F);
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.0F + 0.15F * strength);
		if (strength >= 2) {
			level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
		}
		if (strength >= 3) {
			level.playSound(null, x, y, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.3F);
		}
		if (kind == PillKind.IMMORTAL) {
			level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
			level.playSound(null, x, y, z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.9F, 1.2F);
		} else if (kind == PillKind.TRIBULATION_WARDING) {
			level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.8F);
		} else if (kind == PillKind.HEALING || kind == PillKind.HEART_CALMING) {
			level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.5F);
		}
	}

	// ================================================================ descriptions

	/** Tooltip lines describing the effect of a pill of this quality (without colour). */
	public static List<Component> describe(PillKind kind, PillQuality quality) {
		double m = quality.multiplier();
		List<Component> lines = new ArrayList<>();
		switch (kind) {
			case QI_GATHERING -> {
				lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.xp", percent(PillKind.QI_GATHERING_XP * m)));
				lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.xp_cap", Msg.number(PillKind.QI_GATHERING_MAX_XP * m)));
			}
			case QI_RECOVERY -> lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.qi", percent(Math.min(1.0D, PillKind.QI_RECOVERY_QI * m))));
			case FOUNDATION -> lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.foundation", percent(PillKind.FOUNDATION_BONUS * m)));
			case GOLDEN_CORE -> lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.tribulation", percent(PillKind.GOLDEN_CORE_REDUCTION * m)));
			case TRIBULATION_WARDING -> lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.tribulation", percent(PillKind.WARDING_REDUCTION * m)));
			case HEART_CALMING -> {
				lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.pressure", Msg.number(PillKind.HEART_PRESSURE * m + 1.0E-6D)));
				lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.deviation", percent(PillKind.HEART_DEVIATION * m)));
			}
			case HEALING -> {
				lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.heal", percent(Math.min(1.0D, PillKind.HEALING_HEALTH * m))));
				lines.add(quality == PillQuality.PERFECT
						? Component.translatable("kultivator.alchemy.tooltip.effect.deviation_cure")
						: Component.translatable("kultivator.alchemy.tooltip.effect.deviation", percent(PillKind.HEALING_DEVIATION * m)));
			}
			case SOUL_NURTURING -> {
				lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.xp", percent(PillKind.SOUL_NURTURING_XP * m)));
				lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.xp_cap", Msg.number(PillKind.SOUL_NURTURING_MAX_XP * m)));
			}
			case IMMORTAL -> {
				lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.xp", percent(PillKind.IMMORTAL_XP * m)));
				lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.qi_all"));
				lines.add(Component.translatable("kultivator.alchemy.tooltip.effect.purge"));
			}
		}
		return lines;
	}

	// ================================================================ formatting

	/** "a, b, c" with the translated separator. */
	public static MutableComponent join(List<Component> parts) {
		MutableComponent line = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				line.append(Component.translatable("kultivator.alchemy.separator"));
			}
			line.append(parts.get(i));
		}
		return line;
	}

	/** A fraction as a whole percentage ("15"). */
	public static String percent(double fraction) {
		return String.valueOf(Math.round(fraction * 100.0D));
	}

	/** "1.25", "0.8", "1": a multiplier without trailing zeros. */
	public static String multiplierText(double value) {
		return decimal2(value);
	}

	/** One decimal at most ("7.2", "6"). */
	public static String decimal(double value) {
		String text = String.format(Locale.ROOT, "%.1f", value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}

	private static String decimal2(double value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}

	private PillEffects() {
	}
}
