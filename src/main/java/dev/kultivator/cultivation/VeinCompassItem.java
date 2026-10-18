package dev.kultivator.cultivation;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.registry.ModItems;
import dev.kultivator.talisman.TalismanFxIds;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Kompas Urat Roh / Spirit Vein Compass (DESIGN 19.3.5): use it (5 qi, 60 s cooldown) and it searches the vein hash of
 * up to 32 regions around (about 2,048 blocks, no chunk loading) for the nearest Great vein, else the nearest Rich one.
 * The result is written as a {@code minecraft:lodestone_tracker} (not tracked), so the item model's vanilla compass
 * {@code range_dispatch} points the needle with no client code; {@code kultivator:vein_grade} keeps the grade for the
 * tooltip. Only reads; nothing in the world changes.
 * <p>
 * Sumber: <i>terinspirasi dari</i> the Source Masters of <i>Shrouding the Heavens</i>, who read dragon veins (concept).
 */
public class VeinCompassItem extends Item {

	/** Qi one search costs. */
	public static final int QI_COST = 5;
	/** Ticks before the next search. */
	public static final int COOLDOWN_TICKS = 1200;
	/** Search radius in regions (64 blocks each). */
	public static final int SEARCH_RADIUS = 32;

	/** {@code kultivator:vein_grade}: the grade of the vein the needle points at (saved and synced). */
	public static final DataComponentType<Integer> VEIN_GRADE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("vein_grade"), DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	public static final Item ITEM = ModItems.register("vein_compass", VeinCompassItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	/** Loads the class (registering the item); called from {@link ImmortalPerks#init()}. */
	public static void init() {
	}

	public VeinCompassItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!Qi.trySpend(serverPlayer, QI_COST)) {
			Msg.actionBar(serverPlayer, Component.translatable("kultivator.kultivasi.compass.no_qi", QI_COST).withColor(0xFF9A7A));
			return InteractionResult.FAIL;
		}
		search(serverPlayer, stack);
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Runs the hash search and points the needle (or clears it). */
	private static void search(ServerPlayer player, ItemStack stack) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		SpiritVeins.SearchResult result = SpiritVeins.search(level, pos, SEARCH_RADIUS);
		SpiritVeins.Found found = result.great() != null ? result.great() : result.rich();
		level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
				0.8F, found == null ? 0.6F : 1.4F);
		if (found == null) {
			stack.remove(DataComponents.LODESTONE_TRACKER);
			stack.remove(VEIN_GRADE);
			Msg.chat(player, Component.translatable("kultivator.kultivasi.compass.none", SEARCH_RADIUS * SpiritVeins.REGION_BLOCKS)
					.withColor(0xA8B5B0));
			return;
		}
		BlockPos center = SpiritVeins.regionCenter(found.regionX(), found.regionZ(), pos.getY());
		stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), center)), false));
		stack.set(VEIN_GRADE, found.grade());
		int distance = (int) Math.round(Math.sqrt(pos.distSqr(center)));
		Msg.chat(player, Component.translatable("kultivator.kultivasi.compass.found",
				SpiritVeins.gradeName(found.grade()).copy().withColor(SpiritVeins.gradeColor(found.grade())), distance,
				center.getX(), center.getZ()).withColor(0x8FE3C8));
		Vec3 eye = player.getEyePosition();
		Vec3 toward = Vec3.atCenterOf(center).subtract(eye);
		Vec3 target = eye.add(new Vec3(toward.x, 0.0D, toward.z).normalize().scale(6.0D));
		Fx.send(level, eye.subtract(0.0D, 0.4D, 0.0D), target, FxType.TALISMAN, TalismanFxIds.COMPASS, found.grade(), 0.0F);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return stack.has(VEIN_GRADE) && stack.getOrDefault(VEIN_GRADE, 0) == SpiritVeins.GREAT;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
		Integer grade = stack.get(VEIN_GRADE);
		if (grade != null) {
			builder.accept(Component.translatable("kultivator.kultivasi.compass.tooltip.points",
					SpiritVeins.gradeName(grade).copy().withColor(SpiritVeins.gradeColor(grade))).withStyle(ChatFormatting.GRAY));
		} else {
			builder.accept(Component.translatable("kultivator.kultivasi.compass.tooltip.empty").withStyle(ChatFormatting.GRAY));
		}
		builder.accept(Component.translatable("kultivator.kultivasi.compass.tooltip.use", QI_COST, COOLDOWN_TICKS / 20)
				.withStyle(ChatFormatting.DARK_GRAY));
		builder.accept(Component.translatable("item.kultivator.vein_compass.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
