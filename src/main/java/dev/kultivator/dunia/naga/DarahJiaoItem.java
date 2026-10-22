package dev.kultivator.dunia.naga;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.fx.Fx;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/**
 * Darah Jiao (detail pass of DESIGN 34.5.11): the blood of a slain wild Jiao, in a jade vial, carrying the element of its
 * kind ({@code naga_kind} in the stack's custom data). Drunk (use): +{@link #INSIGHT} comprehension of the drinker's own
 * Dao, {@link #INSIGHT_MATCH} when the kind matches it (water: Ice, cloud: Shadow (wind), thunder: Lightning), Qi refilled
 * by a quarter, Regeneration II for 10 s; before a Dao is chosen it gives a Dunia milestone of
 * {@code XpSources.MILESTONE_JIAO_BLOOD} (5% of the bar; audit M4 S4, it was a flat {@link #XP}) instead. One vial per
 * 5 minutes (item cooldown). Balanced to the hunters of Nascent Soul and above: a Jiao is a 600-1 800 health sky boss
 * that appears to a player about once in 30 minutes, and a technique book gives 5 comprehension.
 */
public class DarahJiaoItem extends Item {

	static final String KIND = "naga_kind";
	public static final double INSIGHT = 3.0D;
	public static final double INSIGHT_MATCH = 5.0D;
	public static final double XP = 600.0D;
	public static final int COOLDOWN = 6000;

	public DarahJiaoItem(Item.Properties properties) {
		super(properties);
	}

	/** A vial of {@code kind}'s blood (the item model tints the blood layer by custom_model_data colour 0). */
	static ItemStack of(NagaKind kind) {
		ItemStack stack = new ItemStack(NagaItems.DARAH_JIAO);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(KIND, kind.ordinal()));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(bloodColor(kind))));
		return stack;
	}

	/** The colour of a kind's blood in the vial: sea blue, cloud jade-white, storm violet. */
	static int bloodColor(NagaKind kind) {
		return switch (kind) {
			case AIR -> 0x3A5CE8;
			case AWAN -> 0xD8F2EC;
			case PETIR -> 0xB040FF;
		};
	}

	static NagaKind kindOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return NagaKind.byOrdinal(data == null ? 0 : data.copyTag().getIntOr(KIND, 0));
	}

	/** The Dao a kind's blood speaks to. */
	static Dao daoOf(NagaKind kind) {
		return switch (kind) {
			case AIR -> Dao.ICE;
			case AWAN -> Dao.SHADOW;
			case PETIR -> Dao.LIGHTNING;
		};
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		drink(serverPlayer, stack);
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		return InteractionResult.SUCCESS;
	}

	private static void drink(ServerPlayer player, ItemStack stack) {
		NagaKind kind = kindOf(stack);
		ServerLevel level = player.level();
		CultivationData data = Cultivators.get(player);
		Dao own = data.dao();
		if (own != null) {
			double amount = own == daoOf(kind) ? INSIGHT_MATCH : INSIGHT;
			double gained = Comprehension.grant(player, own, amount);
			Msg.chat(player, Component.translatable(own == daoOf(kind) ? "kultivator.naga.darah.match" : "kultivator.naga.darah.dao",
					kind.displayName(), own.displayName(), Msg.number(gained)).withColor(kind.glow));
		} else {
			// audit M4 S4 (approved): a share of the bar (5%) instead of a flat 600 XP, so the vial keeps its worth at high realms
			dev.kultivator.cultivation.XpSources.milestone(player, dev.kultivator.cultivation.XpSources.MILESTONE_JIAO_BLOOD,
					Component.translatable("kultivator.kekuatan.milestone.jiao"));
		}
		data.setQi(Math.min(Qi.max(data), data.qi() + Qi.max(data) * 0.25D));
		Cultivators.dirty(player);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, true));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
		Fx.burst(level, player.position().add(0.0D, 1.0D, 0.0D), kind.glow, 20, 1.6F);
		Fx.ring(level, player.position().add(0.0D, 0.1D, 0.0D), kind.glow, 2.5F);
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		Advancements.grant(player, "dunia/darah_jiao");
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		NagaKind kind = kindOf(stack);
		builder.accept(Component.translatable("item.kultivator.darah_jiao.kind", kind.displayName()).withColor(kind.glow));
		builder.accept(Component.translatable("item.kultivator.darah_jiao.desc").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.kultivator.darah_jiao.use", Integer.toString((int) INSIGHT),
				Integer.toString((int) INSIGHT_MATCH), daoOf(kind).displayName()).withStyle(ChatFormatting.DARK_AQUA));
		NagaItems.sumber(builder, false);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
