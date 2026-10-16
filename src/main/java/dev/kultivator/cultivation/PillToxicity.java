package dev.kultivator.cultivation;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Pill toxicity (DESIGN section 9): every pill adds its toxin (0..100 in total), 1 point fades every 30 s. Pills work
 * at {@code 1 - toxicity / 120}; above 80 each pill poisons the body (Nausea + Poison I for 5 s, never lethal).
 */
public final class PillToxicity {

	/** Above this every pill poisons. */
	public static final double POISON_THRESHOLD = 80.0D;
	/** Crossing this upwards shows a warning. */
	public static final double WARNING_THRESHOLD = 50.0D;
	/** Ticks per point of toxicity that fades. */
	public static final int DECAY_INTERVAL = 600;
	/** Colour of the sickly motes (sent with strength 0 in the PILL effect). */
	public static final int SICK_COLOR = 0x8DB33A;
	private static final int POISON_TICKS = 100;

	public static void init() {
	}

	/**
	 * {@code /cultivate debug toxicity set <0..100>} for testing (merges with the spine's {@code debug toxicity clear}).
	 * Only active if the command tree calls it.
	 */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		debug.then(Commands.literal("toxicity").then(Commands.literal("set")
				.then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 100.0D)).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Cultivators.get(player).setToxicity(DoubleArgumentType.getDouble(ctx, "value"));
					Cultivators.syncNow(player);
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.core.debug.done")), false);
					return 1;
				}))));
	}

	public static void tick(ServerPlayer player, CultivationData data) {
		if (player.tickCount % 20 == 0 && !data.deviating()) {
			// The deviation is over: honey may shorten the next one again.
			CompoundTag own = data.extra().getCompoundOrEmpty(VanillaPills.EXTRA);
			if (own.contains(VanillaPills.HONEY_USED)) {
				own.remove(VanillaPills.HONEY_USED);
				data.extra().put(VanillaPills.EXTRA, own);
			}
		}
		if (player.tickCount <= 0 || player.tickCount % DECAY_INTERVAL != 0 || data.toxicity() <= 0.0D) {
			return;
		}
		data.setToxicity(data.toxicity() - 1.0D);
		Cultivators.dirty(player);
	}

	/** How well a pill works right now, 0..1 (before adding its toxin). */
	public static double effectiveness(CultivationData data) {
		return 1.0D - data.toxicity() / 120.0D;
	}

	/** Adds the pill's toxin and applies the poisoning side effects above 80. */
	public static void add(ServerPlayer player, double toxin) {
		if (toxin <= 0.0D) {
			return;
		}
		CultivationData data = Cultivators.get(player);
		double before = data.toxicity();
		data.setToxicity(before + toxin);
		double after = data.toxicity();
		Cultivators.dirty(player);
		if (after > POISON_THRESHOLD) {
			poison(player, after);
		} else if (before < WARNING_THRESHOLD && after >= WARNING_THRESHOLD) {
			Msg.chat(player, Component.translatable("kultivator.pills.toxicity.warning", Msg.number(after),
					Msg.number(effectiveness(data) * 100.0D + 1.0E-6D)).withStyle(ChatFormatting.YELLOW));
		}
	}

	private static void poison(ServerPlayer player, double toxicity) {
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, POISON_TICKS, 0));
		player.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0));
		Msg.chat(player, Component.translatable("kultivator.pills.toxicity.poisoned", Msg.number(toxicity)).withStyle(ChatFormatting.DARK_GREEN));
		Fx.send(player.level(), player.position(), FxType.PILL, SICK_COLOR, 0, 0.0F);
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.PUFFER_FISH_STING,
				SoundSource.PLAYERS, 0.7F, 0.8F);
		Advancements.grant(player, "pill_poisoned");
	}

	private PillToxicity() {
	}
}
