package dev.kultivator.musuh;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.data.Element;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.registry.ModItems;
import dev.kultivator.talisman.TalismanKind;
import dev.kultivator.talisman.Talismans;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Kantong Rampasan (DESIGN 25.4.6; Sumber RMJ ch. 190, 273): the slain foe's storage pouch. {@code loot_pouch} drops from
 * rogues (one roll per tier), {@code demonic_loot_pouch} is the raid reward (two tier-3 rolls, a Mid-High stone and a
 * core of the sect's element). Using one rolls its contents and consumes it. {@code demonic_banner} (Panji Sekte Iblis) is
 * a display-only trophy, one per slain Patriarch. The roll lives in code because it depends on the pouch's tier, realm
 * and sect, kept in the component {@code kultivator:rampasan}.
 */
public final class LootPouches {

	/** The pouch's tier (1-4), realm ordinal and sect. */
	public record PouchData(int tier, int realm, int sect) {
		public static final Codec<PouchData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ExtraCodecs.intRange(1, 4).fieldOf("tier").forGetter(PouchData::tier),
				ExtraCodecs.intRange(0, 32).fieldOf("realm").forGetter(PouchData::realm),
				ExtraCodecs.intRange(0, RogueCultivatorEntity.SECTS - 1).fieldOf("sect").forGetter(PouchData::sect)
		).apply(instance, PouchData::new));
		public static final StreamCodec<ByteBuf, PouchData> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, PouchData::tier, ByteBufCodecs.VAR_INT, PouchData::realm, ByteBufCodecs.VAR_INT, PouchData::sect, PouchData::new);
	}

	public static final DataComponentType<PouchData> RAMPASAN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("rampasan"), DataComponentType.<PouchData>builder()
					.persistent(PouchData.CODEC)
					.networkSynchronized(PouchData.STREAM_CODEC)
					.build());

	public static final Item LOOT_POUCH = ModItems.register("loot_pouch", properties -> new PouchItem(properties, false),
			new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final Item DEMONIC_LOOT_POUCH = ModItems.register("demonic_loot_pouch", properties -> new PouchItem(properties, true),
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
	public static final Item DEMONIC_BANNER = ModItems.register("demonic_banner", TrophyItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	public static final double HERB_CHANCE = 0.30D;
	public static final double TALISMAN_CHANCE = 0.25D;
	public static final double SLIP_CHANCE = 0.05D;
	public static final double FRAGMENT_CHANCE = 0.03D;
	private static final TalismanKind[] TALISMANS = {TalismanKind.THUNDER, TalismanKind.FIRE_CLOUD, TalismanKind.SHIELD, TalismanKind.BINDING};

	/** Loads the class (registering everything above). */
	static void init() {
	}

	/** A rogue's pouch of {@code tier} (1-4), realm ordinal {@code realm} and sect. */
	public static ItemStack pouch(int tier, int realm, int sect) {
		ItemStack stack = new ItemStack(LOOT_POUCH);
		stack.set(RAMPASAN, new PouchData(Mth.clamp(tier, 1, 4), Mth.clamp(realm, 0, 32), Math.floorMod(sect, RogueCultivatorEntity.SECTS)));
		return stack;
	}

	/** The raid reward pouch of a sect at raid level {@code raidLevel}. */
	public static ItemStack demonic(int sect, int raidLevel) {
		ItemStack stack = new ItemStack(DEMONIC_LOOT_POUCH);
		stack.set(RAMPASAN, new PouchData(3, Mth.clamp(raidLevel, 0, 32), Math.floorMod(sect, RogueCultivatorEntity.SECTS)));
		return stack;
	}

	/** The trophy of a slain Patriarch of {@code sect}. */
	public static ItemStack banner(int sect) {
		ItemStack stack = new ItemStack(DEMONIC_BANNER);
		stack.set(RAMPASAN, new PouchData(4, 0, Math.floorMod(sect, RogueCultivatorEntity.SECTS)));
		return stack;
	}

	/** What one pouch holds (rolled now). */
	static List<ItemStack> roll(RandomSource random, PouchData data, boolean demonic) {
		List<ItemStack> out = new ArrayList<>();
		if (demonic) {
			rollOnce(random, 3, data.realm(), out);
			rollOnce(random, 3, data.realm(), out);
			out.add(SpiritStones.stack(data.realm() >= 5 ? SpiritStones.HIGH : SpiritStones.MID, 1));
			int grade = data.realm() <= 2 ? BeastCores.LOW : data.realm() <= 4 ? BeastCores.MID : data.realm() <= 7 ? BeastCores.HIGH : BeastCores.KING;
			out.add(BeastCores.create(RogueCultivatorEntity.sectElement(data.sect()), grade));
		} else {
			for (int i = 0; i < data.tier(); i++) {
				rollOnce(random, data.tier(), data.realm(), out);
			}
		}
		out.removeIf(ItemStack::isEmpty);
		return out;
	}

	/** One roll of DESIGN 25.4.6. */
	private static void rollOnce(RandomSource random, int tier, int realm, List<ItemStack> out) {
		out.add(SpiritStones.stack(tier >= 3 ? SpiritStones.MID : SpiritStones.LOW, 1 + random.nextInt(3)));
		if (random.nextDouble() < HERB_CHANCE) {
			out.add(SpiritHerbs.randomFor(null, random, 50 * tier, 150 * tier));
		}
		if (random.nextDouble() < TALISMAN_CHANCE) {
			out.add(Talismans.create(TALISMANS[random.nextInt(TALISMANS.length)], Mth.clamp(realm, 1, 9), "Kultivator Liar"));
		}
		if (random.nextDouble() < SLIP_CHANCE) {
			out.add(slip(random, realm));
		}
		if (tier >= 2 && random.nextDouble() < FRAGMENT_CHANCE) {
			out.add(RogueCultivators.byId("heavenvoid_fragment"));
		}
	}

	/** A jade slip of a random learnable spec, Kuning to Bumi by realm (empty when none fits). */
	private static ItemStack slip(RandomSource random, int realm) {
		TechniqueGrade grade = realm <= 2 ? TechniqueGrade.HUANG : realm <= 4 ? TechniqueGrade.XUAN : TechniqueGrade.DI;
		List<TechniqueSpec> pool = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.ofGrade(grade)) {
			if (spec.slipAllowed()) {
				pool.add(spec);
			}
		}
		return pool.isEmpty() ? ItemStack.EMPTY : JadeSlips.stack(pool.get(random.nextInt(pool.size())).id());
	}

	static Component sectName(int sect) {
		return Component.translatable("kultivator.musuh.sect." + Math.floorMod(sect, RogueCultivatorEntity.SECTS));
	}

	// ================================================================== items

	/** A pouch: use to open it. */
	public static class PouchItem extends Item {

		private final boolean demonic;

		public PouchItem(Item.Properties properties, boolean demonic) {
			super(properties);
			this.demonic = demonic;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			ItemStack stack = player.getItemInHand(hand);
			PouchData data = stack.getOrDefault(RAMPASAN, new PouchData(demonic ? 3 : 1, 1, 0));
			List<ItemStack> loot = roll(serverPlayer.getRandom(), data, demonic);
			stack.consume(1, player);
			for (ItemStack each : loot) {
				give(serverPlayer, each);
			}
			serverPlayer.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.9F, 0.9F);
			Msg.actionBar(serverPlayer, Component.translatable("kultivator.musuh.pouch.opened", loot.size()).withStyle(ChatFormatting.GOLD));
			return InteractionResult.SUCCESS_SERVER;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			PouchData data = stack.get(RAMPASAN);
			if (data != null) {
				if (demonic) {
					builder.accept(Component.translatable("kultivator.musuh.pouch.demonic_of", sectName(data.sect())).withStyle(ChatFormatting.DARK_RED));
				} else {
					builder.accept(Component.translatable("kultivator.musuh.pouch.tier", RogueCultivatorEntity.title(data.tier(), RogueCultivators.VARIANT_ROGUE,
							data.sect())).withStyle(ChatFormatting.GRAY));
				}
			}
			builder.accept(Component.translatable("kultivator.musuh.pouch.tooltip_use").withStyle(ChatFormatting.GOLD));
			builder.accept(Component.translatable("kultivator.musuh.pouch.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Panji Sekte Iblis: a trophy, nothing to use. */
	public static class TrophyItem extends Item {

		public TrophyItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			PouchData data = stack.get(RAMPASAN);
			if (data != null) {
				builder.accept(Component.translatable("kultivator.musuh.banner.of", sectName(data.sect())).withStyle(ChatFormatting.DARK_RED));
			}
			builder.accept(Component.translatable("kultivator.musuh.banner.tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.musuh.banner.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Into the inventory, or dropped at the feet as a block-safe item. */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			item.setPortalCooldown();
			player.level().addFreshEntity(item);
		}
	}

	/** The element of a sect (for the core of a demonic pouch). */
	static Element element(int sect) {
		return RogueCultivatorEntity.sectElement(sect);
	}

	private LootPouches() {
	}
}
