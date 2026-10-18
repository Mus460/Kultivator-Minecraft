package dev.kultivator.harta;

import dev.kultivator.formation.ProtectiveFormations;
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
 * Batu Roh, a spirit stone of one grade (DESIGN 19.3.2). Sneak + use outside meditation absorbs its qi at once; in the
 * off hand it fuels meditation (P7-kultivasi), on a protective formation's lodestone it powers the formation, at a
 * successful breakthrough High and Supreme stones raise the quality ({@link StoneBottleneck}).
 */
public class SpiritStoneItem extends Item {

	private final int grade;

	public SpiritStoneItem(Item.Properties properties, int grade) {
		super(properties);
		this.grade = grade;
	}

	/** 0 Low, 1 Mid, 2 High, 3 Supreme. */
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
		return SpiritStones.absorb(serverPlayer, player.getItemInHand(hand), grade) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return grade >= SpiritStones.SUPREME || super.isFoil(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
								TooltipFlag flag) {
		double qi = SpiritStones.INSTANT_QI[grade];
		builder.accept((qi < 0.0D
				? Component.translatable("kultivator.harta.stone.tooltip.qi_full", SpiritStones.SUPREME_REGEN_TICKS / 20)
				: Component.translatable("kultivator.harta.stone.tooltip.qi", HartaText.decimal(qi))).withColor(SpiritStones.COLORS[grade]));
		builder.accept(Component.translatable("kultivator.harta.stone.tooltip.fuel", SpiritStones.FUEL_SECONDS[grade],
				HartaText.decimal(SpiritStones.FUEL_XP[grade])).withColor(HartaText.USE_COLOR));
		int ticks = SpiritStones.FORMATION_TICKS[grade];
		builder.accept((ticks < 0
				? Component.translatable("kultivator.harta.stone.tooltip.formation_full", ProtectiveFormations.duration(ProtectiveFormations.MAX_POWER_STONES))
				: Component.translatable("kultivator.harta.stone.tooltip.formation", ProtectiveFormations.duration(ticks))).withColor(HartaText.USE_COLOR));
		double bottleneck = StoneBottleneck.PER_STONE[grade];
		if (bottleneck > 0.0D) {
			builder.accept(Component.translatable("kultivator.harta.stone.tooltip.bottleneck", HartaText.decimal(bottleneck),
					StoneBottleneck.MAX_STONES, HartaText.decimal(StoneBottleneck.MAX_BONUS)).withColor(HartaText.USE_COLOR));
		}
		builder.accept(Component.translatable(grade == SpiritStones.LOW ? "kultivator.harta.stone.tooltip.catalyst_low"
				: "kultivator.harta.stone.tooltip.catalyst").withColor(HartaText.USE_COLOR));
		builder.accept(Component.translatable("kultivator.harta.tooltip.mission", HartaText.decimal(SpiritStones.MISSION_PK[grade]))
				.withColor(HartaText.VALUE_COLOR));
		HartaText.sources(getDescriptionId(), builder);
		builder.accept(Component.translatable("kultivator.harta.stone.tooltip.use").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
