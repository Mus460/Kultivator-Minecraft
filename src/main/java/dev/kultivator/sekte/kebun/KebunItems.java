package dev.kultivator.sekte.kebun;

import dev.kultivator.registry.ModItems;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * Module W's one item, {@code kultivator:inti_roh_gunung} (Inti Roh Gunung, the Mountain Spirit Core): the rare find
 * of the Tambang Roh from depth level 7 (DESIGN 36.2.10). Used inside its owner's own sect territory it melts into the
 * sect's spirit vein: +{@value #POOL} PK in the hall pool and +{@value #RENOWN} renown, a Catatan line, a golden pillar.
 * Elsewhere nothing happens (a hint on the action bar). No ItemStack is made here.
 * <p>
 * Sumber: Nüwa melts five-coloured stones to mend the sky (<i>Huainanzi</i> ch. 6); the jade that grows inside the
 * mountains of the <i>Shanhaijing</i>.
 */
public final class KebunItems {

	public static final int POOL = 300;
	public static final int RENOWN = 30;

	public static final Item INTI_ROH_GUNUNG = ModItems.register("inti_roh_gunung", IntiRohGunung::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

	/** Loads the class (registers the item); from {@link Kebun#init}, before {@code ModItems.init()}. */
	static void init() {
	}

	/** The Mountain Spirit Core: melts into the sect's vein inside the owner's territory. */
	public static final class IntiRohGunung extends Item {

		public IntiRohGunung(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server)) {
				return InteractionResult.CONSUME;
			}
			Halls.HallView hall = Halls.hallOf(serverPlayer.getUUID());
			if (hall == null || !hall.active() || !Halls.inTerritory(serverPlayer.getUUID(), level, player.position())) {
				Msg.actionBar(serverPlayer, Component.translatable("kultivator.sekte.kebun.inti.no_hall").withStyle(ChatFormatting.YELLOW));
				return InteractionResult.FAIL;
			}
			ItemStack stack = player.getItemInHand(hand);
			stack.consume(1, player);
			Halls.addPool(serverPlayer.getUUID(), POOL);
			Halls.addRenown(serverPlayer.getUUID(), RENOWN, "kultivator.sekte.kebun.inti.renown");
			SekteLog.log(server.getServer(), serverPlayer.getUUID(), SekteIds.W,
					Component.translatable("kultivator.sekte.kebun.log.inti_used", player.getDisplayName(), POOL));
			Vec3 at = player.position();
			server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
			server.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.3F);
			KebunPayloads.fx(server, KebunPayloads.FX_INTI, at, 0xFFD27A);
			Msg.actionBar(serverPlayer, Component.translatable("kultivator.sekte.kebun.inti.used", POOL, RENOWN).withStyle(ChatFormatting.GOLD));
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return true;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			builder.accept(Component.translatable("item.kultivator.inti_roh_gunung.tooltip", POOL, RENOWN).withStyle(ChatFormatting.GOLD));
			builder.accept(Component.translatable("item.kultivator.inti_roh_gunung.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private KebunItems() {
	}
}
