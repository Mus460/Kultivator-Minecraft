package dev.kultivator.sekte.langit.main;

import dev.kultivator.registry.ModItems;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
 * Module 37G's one item, {@code kultivator:cangkang_kura_langit} (Cangkang Kura-kura Langit, the Sky Turtle Shell): the
 * trophy of Raksasa Longbo (DESIGN 37.7.4). Always registered (K1). Used inside its owner's own sect territory it is laid
 * under the hall's foundation as the island's "fifteenth turtle": +{@value #POOL} PK into the hall pool and
 * +{@value #RENOWN} renown, a hall log line and a ring of cloud. Elsewhere nothing happens. Its town price is
 * {@code Harga.BASE} 1200 NB, i.e. the same {@value #POOL} PK at the unified rate 1 PK = 2 NB (audit 4.10). No
 * ItemStack is made here (lazy creation). A 3D cuboid model ({@code models/item/cangkang_kura_langit.json}).
 * <p>
 * Sumber: <i>Liezi</i>, "Tang Wen": the Emperor of Heaven sent fifteen giant turtles to carry the floating mountains of
 * Guixu on their heads; the giants of Longbo caught six of them.
 */
public final class LangitItems {

	public static final int POOL = 600;
	public static final int RENOWN = 40;

	public static final Item CANGKANG_KURA_LANGIT = ModItems.register("cangkang_kura_langit", Cangkang::new,
			new Item.Properties().stacksTo(8).rarity(Rarity.EPIC).fireResistant());

	/** Loads the class (registers the item); from {@code LangitMain.init}, before {@code ModItems.init()}. */
	static void init() {
	}

	/** The Sky Turtle Shell. */
	public static final class Cangkang extends Item {

		public Cangkang(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server)) {
				return InteractionResult.CONSUME;
			}
			Halls.HallView hall = Halls.hallOf(serverPlayer.getUUID());
			if (hall == null || !hall.active() || !Halls.inTerritory(serverPlayer.getUUID(), level, player.position())) {
				Msg.actionBar(serverPlayer, Component.translatable("kultivator.sekte_langit.cangkang.tanpa_aula").withStyle(ChatFormatting.YELLOW));
				return InteractionResult.FAIL;
			}
			ItemStack stack = player.getItemInHand(hand);
			stack.consume(1, player);
			Halls.addPool(serverPlayer.getUUID(), POOL);
			Halls.addRenown(serverPlayer.getUUID(), RENOWN, "kultivator.sekte_langit.cangkang.renown");
			SekteLog.log(server.getServer(), serverPlayer.getUUID(), "langit",
					Component.translatable("kultivator.sekte_langit.cangkang.log", player.getDisplayName(), POOL));
			Vec3 at = player.position();
			server.playSound(null, at.x, at.y, at.z, SoundEvents.TURTLE_EGG_HATCH, SoundSource.PLAYERS, 1.0F, 0.6F);
			server.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 0.8F);
			for (int i = 0; i < 24; i++) {
				double angle = Math.PI * 2.0D * i / 24.0D;
				server.sendParticles(ParticleTypes.CLOUD, at.x + Math.cos(angle) * 2.5D, at.y + 0.2D, at.z + Math.sin(angle) * 2.5D, 1, 0.05D, 0.05D, 0.05D,
						0.01D);
			}
			Msg.actionBar(serverPlayer, Component.translatable("kultivator.sekte_langit.cangkang.dipakai", POOL, RENOWN).withStyle(ChatFormatting.GOLD));
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return true;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
			lines.accept(Component.translatable("kultivator.sekte_langit.cangkang.tip").withStyle(ChatFormatting.GRAY));
			lines.accept(Component.translatable("kultivator.sekte_langit.cangkang.tip2", POOL).withStyle(ChatFormatting.DARK_AQUA));
		}
	}

	private LangitItems() {
	}
}
