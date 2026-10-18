package dev.kultivator.harta;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Inti Binatang, a beast core of one grade (DESIGN 19.3.3); its element is the {@code kultivator:core_element}
 * component and tints the texture through {@code minecraft:custom_model_data}. Sneak + use absorbs it
 * ({@link BeastCores#absorb}); used on your spirit beast it feeds the beast.
 */
public class BeastCoreItem extends Item {

	private final int grade;

	public BeastCoreItem(Item.Properties properties, int grade) {
		super(properties);
		this.grade = grade;
	}

	/** 1 Low, 2 Mid, 3 High, 4 King. */
	public int grade() {
		return grade;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isSecondaryUseActive() || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.CONSUME;
		}
		return BeastCores.absorb(serverPlayer, player.getItemInHand(hand)) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	@Override
	public Component getName(ItemStack stack) {
		Element element = BeastCores.element(stack);
		if (element == null) {
			return super.getName(stack);
		}
		return Component.translatable("item.kultivator.beast_core.named", element.displayName(), BeastCores.gradeName(grade))
				.withColor(element.color());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return grade >= BeastCores.KING || super.isFoil(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
								TooltipFlag flag) {
		int index = grade - 1;
		Element element = BeastCores.element(stack);
		builder.accept(Component.translatable("kultivator.harta.core.tooltip.absorb", HartaText.percent(BeastCores.XP_SHARE[index]),
				HartaText.decimal(BeastCores.XP_CAP[index]), BeastCores.TOXIN[index]).withColor(element == null ? HartaText.USE_COLOR : element.color()));
		if (element != null) {
			Dao dao = BeastCores.affinity(element);
			builder.accept(Component.translatable("kultivator.harta.core.tooltip.comprehension", HartaText.decimal(BeastCores.COMPREHENSION[index]),
					dao.displayName().copy().withColor(dao.color())).withColor(HartaText.USE_COLOR));
		}
		builder.accept(Component.translatable("kultivator.harta.core.tooltip.root", HartaText.percent(BeastCores.ROOT_BONUS - 1.0D))
				.withColor(HartaText.USE_COLOR));
		builder.accept(Component.translatable("kultivator.harta.core.tooltip.beast", HartaText.decimal(BeastCores.BEAST_XP[index]),
				HartaText.decimal(BeastCores.BEAST_MATCH)).withColor(HartaText.USE_COLOR));
		builder.accept(Component.translatable("kultivator.harta.core.tooltip.catalyst", HartaText.percent(BeastCores.CATALYST_CHANCE[index]))
				.withColor(HartaText.USE_COLOR));
		dev.kultivator.alchemy.PillQuality floor = BeastCores.qualityFloor(grade);
		if (floor != null) {
			builder.accept(Component.translatable("kultivator.harta.core.tooltip.floor", floor.displayName()).withColor(HartaText.USE_COLOR));
		}
		builder.accept(Component.translatable("kultivator.harta.tooltip.mission", HartaText.decimal(BeastCores.MISSION_PK[index]))
				.withColor(HartaText.VALUE_COLOR));
		HartaText.sources(getDescriptionId(), builder);
		builder.accept(Component.translatable("kultivator.harta.core.tooltip.use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
