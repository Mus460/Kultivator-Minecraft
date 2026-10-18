package dev.kultivator.sect.hall;

import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.PillItem;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.power.DisciplePowers;
import dev.kultivator.item.RootItems;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.technique.source.LearnRules;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Poin Kontribusi of the hall pool (DESIGN 20.3.7): the Kantor Misi where the master (or a sect mate, as a donation) offers
 * stacks graded by an elder, and the Paviliun Yue Lu that sells for pool PK at the hall's price factor. Pills come out as
 * the existing alchemy items at Normal quality.
 * <p>
 * Sources: <i>A Will Eternal</i> ch. 9 and 27 (the Missions Office grades spirit plants and pays merit points),
 * <i>Martial Peak</i> (the Contribution Hall), <i>A Record of a Mortal's Journey</i> ch. 153 (Yue Lu Hall supplies
 * manuals, recipes and materials).
 */
final class Contribution {

	/** At most this much pool PK per in-game day comes from offerings. */
	public static final int OFFER_DAILY_CAP = 300;

	/** Paviliun Yue Lu wares: id, base price, minimum hall level, whether it needs a disciple (or a technique) as target. */
	enum Ware {
		HEALING("healing", 40, 1, false),
		QI_GATHERING("qi_gathering", 60, 1, false),
		AMETHYST("amethyst", 40, 1, false),
		INVITE("invite", 150, 1, false),
		SERMON("sermon", 150, 1, true),
		FOUNDATION("foundation", 250, 1, false),
		ENVOY("envoy", 300, 1, false),
		/** Slip Giok Teknik (DESIGN 22.8.4): the price and hall level follow the chosen art's grade; 200 / 1 is the cheapest. */
		TECHNIQUE("technique", 200, 1, false),
		WARDING("warding", 600, 3, false),
		GOLDEN_CORE("golden_core", 800, 4, false),
		MARROW("marrow", 900, 6, false),
		CODEX("codex", 1200, 3, true);

		final String id;
		final int price;
		final int level;
		final boolean disciple;

		Ware(String id, int price, int level, boolean disciple) {
			this.id = id;
			this.price = price;
			this.level = level;
			this.disciple = disciple;
		}

		static @Nullable Ware byId(String id) {
			for (Ware ware : values()) {
				if (ware.id.equals(id)) {
					return ware;
				}
			}
			return null;
		}
	}

	/** Ceramah Kitab: comprehension for one disciple (AWE scripture sermons). */
	public static final double SERMON_COMPREHENSION = 5.0D;
	public static final int AMETHYST_COUNT = 8;
	/** The envoys' rest (module B's {@code Envoys.COOLDOWN_DAYS}, package-private there). */
	public static final int ENVOY_COOLDOWN_DAYS = 5;

	/** Slip Giok Teknik: at most this many slips per in-game day (DESIGN 22.8.4). */
	public static final int SLIPS_PER_DAY = 2;

	/**
	 * The legacy book techniques (DESIGN 7), kept for the hall screen's art picker; the pavilion now sells any of them, and
	 * every other {@link #sellable} spec, as a jade slip.
	 */
	static final Map<Technique, ResourceKey<Enchantment>> BOOKS = new LinkedHashMap<>();

	static {
		BOOKS.put(Technique.THOUSAND_SWORDS, Enchantments.SHARPNESS);
		BOOKS.put(Technique.PHOENIX_FLAME, Enchantments.FIRE_ASPECT);
		BOOKS.put(Technique.GLACIER_PRISON, Enchantments.FROST_WALKER);
		BOOKS.put(Technique.HEAVENLY_THUNDER, Enchantments.CHANNELING);
		BOOKS.put(Technique.MOUNTAIN_STOMP, Enchantments.PROTECTION);
		BOOKS.put(Technique.LIFE_DRAIN, Enchantments.MENDING);
		BOOKS.put(Technique.GALE, Enchantments.SWIFT_SNEAK);
		BOOKS.put(Technique.SPACE_PRISON, Enchantments.INFINITY);
		BOOKS.put(Technique.BUDDHA_PALM, Enchantments.SMITE);
	}

	// ================================================================== Kantor Misi

	/** PK one item of {@code stack} is worth at the Kantor Misi (0 when it is not accepted). */
	static double unitValue(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0D;
		}
		if (stack.is(Items.GLOW_BERRIES)) {
			return 0.5D;
		}
		if (stack.is(Items.AMETHYST_SHARD) || stack.is(Items.GOLDEN_CARROT) || stack.is(Items.GOLD_INGOT)) {
			return 1.0D;
		}
		if (stack.is(Items.GLISTERING_MELON_SLICE)) {
			return 2.0D;
		}
		if (stack.is(Items.EMERALD)) {
			return 6.0D;
		}
		if (stack.is(Items.DIAMOND)) {
			return 8.0D;
		}
		if (stack.is(Items.ECHO_SHARD)) {
			return 15.0D;
		}
		if (stack.getItem() instanceof PillItem) {
			return switch (AlchemyComponents.quality(stack)) {
				case LOW -> 5.0D;
				case MID -> 10.0D;
				case HIGH -> 20.0D;
				case PERFECT -> 40.0D;
			};
		}
		return 0.0D;
	}

	/** The offering slots of the viewer's inventory: {slot, item, count, quality, unit} for every accepted stack. */
	static ListTag offers(ServerPlayer viewer) {
		ListTag list = new ListTag();
		Inventory inventory = viewer.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			double unit = unitValue(stack);
			if (unit <= 0.0D) {
				continue;
			}
			CompoundTag entry = new CompoundTag();
			entry.putInt("slot", slot);
			entry.putString("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			entry.putInt("count", stack.getCount());
			entry.putInt("quality", stack.getItem() instanceof PillItem ? AlchemyComponents.quality(stack).ordinal() : -1);
			entry.putDouble("unit", unit);
			list.add(entry);
		}
		return list;
	}

	/** Today's offered PK of the hall (resets each day). */
	static int offeredToday(CompoundTag core) {
		return core.getLongOr("pool_day", -1L) == Halls.dayIndex() ? core.getIntOr("offered_today", 0) : 0;
	}

	/** Offers the stack in {@code slot} (up to the daily cap); the elder grades it. */
	static void offer(ServerPlayer player, UUID owner, CompoundTag core, int slot) {
		if (slot < 0 || slot >= Inventory.INVENTORY_SIZE) {
			return;
		}
		ItemStack stack = player.getInventory().getItem(slot);
		double unit = unitValue(stack);
		if (unit <= 0.0D) {
			Msg.chat(player, Component.translatable("kultivator.hall.offer.refused").withStyle(ChatFormatting.GRAY));
			return;
		}
		long day = Halls.dayIndex();
		if (core.getLongOr("pool_day", -1L) != day) {
			core.putLong("pool_day", day);
			core.putInt("offered_today", 0);
		}
		int room = OFFER_DAILY_CAP - core.getIntOr("offered_today", 0);
		if (room <= 0) {
			Msg.chat(player, Component.translatable("kultivator.hall.offer.cap", OFFER_DAILY_CAP).withStyle(ChatFormatting.YELLOW));
			return;
		}
		int count = Math.min(stack.getCount(), (int) Math.floor(room / unit + 1.0E-6D));
		if (count <= 0) {
			Msg.chat(player, Component.translatable("kultivator.hall.offer.cap", OFFER_DAILY_CAP).withStyle(ChatFormatting.YELLOW));
			return;
		}
		int value = (int) Math.floor(count * unit + 1.0E-6D);
		if (value <= 0) {
			Msg.chat(player, Component.translatable("kultivator.hall.offer.too_little").withStyle(ChatFormatting.GRAY));
			return;
		}
		Component itemName = stack.getHoverName();
		stack.shrink(count);
		player.getInventory().setChanged();
		core.putInt("offered_today", core.getIntOr("offered_today", 0) + value);
		Halls.addPool(owner, value);
		String grade = unit >= 15.0D ? "excellent" : unit >= 5.0D ? "high" : unit >= 1.0D ? "good" : "plain";
		Msg.chat(player, Component.translatable("kultivator.hall.offer.grade." + grade).withStyle(ChatFormatting.DARK_AQUA));
		Msg.chat(player, Component.translatable("kultivator.hall.offer.done", count, itemName, value).withStyle(ChatFormatting.AQUA));
		if (!player.getUUID().equals(owner)) {
			Halls.log(owner, Component.translatable("kultivator.hall.log.donation", player.getName(), value));
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
	}

	// ================================================================== Paviliun Yue Lu

	static int price(Ware ware, int level) {
		return (int) Math.round(ware.price * HallLevel.price(level));
	}

	// ---- Slip Giok Teknik (DESIGN 22.8.4)

	/**
	 * Specs the pavilion sells as jade slips: those a slip may hold that are flagged or routed for the pavilion
	 * ({@code TechniqueSpec.pavilion()}), plus the legacy book techniques.
	 */
	static boolean sellable(TechniqueSpec spec) {
		if (!spec.slipAllowed() || spec.grade().pavilionPrice() <= 0) {
			return false;
		}
		if (spec.pavilion()) {
			return true;
		}
		if (spec.isLegacy()) {
			for (Route route : spec.routes()) {
				if (route.kind() == Route.Kind.BOOK) {
					return true;
				}
			}
		}
		return false;
	}

	/** Every sellable spec in grade order. */
	static List<TechniqueSpec> slips() {
		List<TechniqueSpec> list = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (sellable(spec)) {
				list.add(spec);
			}
		}
		list.sort(LearnRules.GRADE_ORDER);
		return list;
	}

	/** Grade price (Kuning 200 · Misterius 400 · Bumi 900 · Langit 2,000 · Abadi 4,000) × the hall's price factor. */
	static int slipPrice(TechniqueSpec spec, int level) {
		return (int) Math.round(spec.grade().pavilionPrice() * HallLevel.price(level));
	}

	/** Slips bought today (resets each in-game day). */
	static int slipsToday(CompoundTag core) {
		return core.getLongOr("slip_day", -1L) == Halls.dayIndex() ? core.getIntOr("slips_today", 0) : 0;
	}

	/** The Paviliun list for the screen: {id, price, need, ok, target}; the slip row also lists {slips: [{id, price, need, ok}]}. */
	static ListTag shop(UUID owner, CompoundTag core) {
		ListTag list = new ListTag();
		int level = Halls.level(owner);
		int pool = core.getIntOr("pool", 0);
		for (Ware ware : Ware.values()) {
			CompoundTag entry = new CompoundTag();
			entry.putString("id", ware.id);
			entry.putInt("price", price(ware, level));
			entry.putInt("need", ware.level);
			boolean unlocked = level >= ware.level;
			boolean today = ware == Ware.INVITE ? core.getLongOr("invite_day", -1L) != Halls.dayIndex()
					: ware != Ware.TECHNIQUE || slipsToday(core) < SLIPS_PER_DAY;
			entry.putBoolean("ok", unlocked && today && pool >= price(ware, level));
			entry.putBoolean("locked", !unlocked);
			entry.putBoolean("used_today", !today);
			entry.putBoolean("target", ware.disciple);
			if (ware == Ware.TECHNIQUE) {
				ListTag slips = new ListTag();
				for (TechniqueSpec spec : slips()) {
					CompoundTag slip = new CompoundTag();
					slip.putString("id", spec.id());
					slip.putInt("price", slipPrice(spec, level));
					slip.putInt("need", spec.grade().pavilionHallLevel());
					slip.putBoolean("ok", today && level >= spec.grade().pavilionHallLevel() && pool >= slipPrice(spec, level));
					slips.add(slip);
				}
				entry.put("slips", slips);
				entry.putInt("slips_left", Math.max(0, SLIPS_PER_DAY - slipsToday(core)));
			}
			list.add(entry);
		}
		return list;
	}

	/** Buys {@code id}; {@code param} is the target disciple's soul (Sermon, Codex) or the spec id (Slip Giok Teknik). */
	static void buy(ServerPlayer player, UUID owner, CompoundTag core, String id, String param) {
		Ware ware = Ware.byId(id);
		if (ware == null) {
			return;
		}
		int level = Halls.level(owner);
		if (level < ware.level) {
			Msg.chat(player, Component.translatable("kultivator.hall.shop.locked", ware.level).withStyle(ChatFormatting.YELLOW));
			return;
		}
		int cost = price(ware, level);
		if (core.getIntOr("pool", 0) < cost) {
			Msg.chat(player, Component.translatable("kultivator.hall.pk.pool_short", cost).withStyle(ChatFormatting.YELLOW));
			return;
		}
		DiscipleEntity target = null;
		if (ware.disciple) {
			target = Halls.findDisciple(owner, param);
			if (target == null) {
				Msg.chat(player, Component.translatable("kultivator.hall.shop.pick_disciple").withStyle(ChatFormatting.YELLOW));
				return;
			}
		}
		TechniqueSpec slip = null;
		if (ware == Ware.TECHNIQUE) {
			// Slip Giok Teknik: param is the spec id (legacy ids too); its grade sets the price and the hall level.
			slip = TechniqueRegistry.byId(param);
			if (slip == null) {
				Msg.chat(player, Component.translatable("kultivator.hall.shop.pick_technique").withStyle(ChatFormatting.YELLOW));
				return;
			}
			if (!sellable(slip)) {
				Msg.chat(player, Component.translatable("kultivator.sumber.pavilion.not_sold", LearnRules.name(slip)).withStyle(ChatFormatting.YELLOW));
				return;
			}
			if (level < slip.grade().pavilionHallLevel()) {
				Msg.chat(player, Component.translatable("kultivator.sumber.pavilion.level", LearnRules.name(slip), slip.grade().displayName(),
						slip.grade().pavilionHallLevel()).withStyle(ChatFormatting.YELLOW));
				return;
			}
			if (slipsToday(core) >= SLIPS_PER_DAY) {
				Msg.chat(player, Component.translatable("kultivator.sumber.pavilion.daily", SLIPS_PER_DAY).withStyle(ChatFormatting.YELLOW));
				return;
			}
			cost = slipPrice(slip, level);
			if (core.getIntOr("pool", 0) < cost) {
				Msg.chat(player, Component.translatable("kultivator.hall.pk.pool_short", cost).withStyle(ChatFormatting.YELLOW));
				return;
			}
		}
		if (ware == Ware.INVITE && core.getLongOr("invite_day", -1L) == Halls.dayIndex()) {
			Msg.chat(player, Component.translatable("kultivator.hall.shop.invite_used").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (ware == Ware.CODEX && target != null && SectRanks.hasCodexManual(target.data())) {
			Msg.chat(player, Component.translatable("kultivator.hall.shop.codex_known", target.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (ware == Ware.ENVOY && (!core.contains("envoy_day") || Halls.dayIndex() >= core.getLongOr("envoy_day", 0L) + ENVOY_COOLDOWN_DAYS)) {
			// No envoy is resting: nothing to reset, no PK taken.
			Msg.chat(player, Component.translatable("kultivator.hall.shop.envoy_ready").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (ware == Ware.INVITE && !Candidates.invite(owner)) {
			Msg.chat(player, Component.translatable("kultivator.hall.shop.invite_failed").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (!Halls.spendPool(owner, cost)) {
			return;
		}
		switch (ware) {
			case HEALING -> give(player, pill(AlchemyItems.HEALING_PILL));
			case QI_GATHERING -> give(player, pill(AlchemyItems.QI_GATHERING_PILL));
			case AMETHYST -> give(player, new ItemStack(Items.AMETHYST_SHARD, AMETHYST_COUNT));
			case INVITE -> core.putLong("invite_day", Halls.dayIndex());
			case SERMON -> {
				DiscipleData data = target.data();
				data.addComprehension(SERMON_COMPREHENSION);
				Msg.chat(player, Component.translatable("kultivator.hall.shop.sermon_done", target.getDisplayName(), (int) SERMON_COMPREHENSION)
						.withStyle(ChatFormatting.AQUA));
			}
			case FOUNDATION -> give(player, pill(AlchemyItems.FOUNDATION_PILL));
			case ENVOY -> {
				core.remove("envoy_day");
				Msg.chat(player, Component.translatable("kultivator.hall.shop.envoy_done").withStyle(ChatFormatting.AQUA));
			}
			case TECHNIQUE -> {
				ItemStack stack = JadeSlips.stack(slip.id());
				if (stack.isEmpty()) {
					Halls.addPool(owner, cost);
					return;
				}
				long day = Halls.dayIndex();
				if (core.getLongOr("slip_day", -1L) != day) {
					core.putLong("slip_day", day);
					core.putInt("slips_today", 0);
				}
				core.putInt("slips_today", core.getIntOr("slips_today", 0) + 1);
				give(player, stack);
				Msg.chat(player, Component.translatable("kultivator.sumber.pavilion.bought", LearnRules.name(slip), cost,
						Math.max(0, SLIPS_PER_DAY - slipsToday(core))).withStyle(ChatFormatting.AQUA));
			}
			case WARDING -> give(player, pill(AlchemyItems.TRIBULATION_WARDING_PILL));
			case GOLDEN_CORE -> give(player, pill(AlchemyItems.GOLDEN_CORE_PILL));
			case MARROW -> give(player, new ItemStack(RootItems.MARROW_CLEANSING_PILL));
			case CODEX -> {
				SectRanks.setCodexManual(target.data());
				// Opens the codex on module C's side too (a first Poin Tempa).
				DisciplePowers.addTempering(target.data(), 1);
				Msg.chat(player, Component.translatable("kultivator.hall.shop.codex_done", target.getDisplayName()).withStyle(ChatFormatting.AQUA));
			}
		}
		Halls.log(owner, Component.translatable("kultivator.hall.log.bought", Component.translatable("kultivator.hall.ware." + ware.id), cost));
		Msg.chat(player, Component.translatable("kultivator.hall.shop.bought", Component.translatable("kultivator.hall.ware." + ware.id), cost)
				.withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.6F, 1.1F);
	}

	private static ItemStack pill(Item item) {
		return AlchemyComponents.withQuality(new ItemStack(item), PillQuality.MID);
	}

	/** Into the player's inventory, the rest at their feet. */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ServerLevel level = player.level();
			ItemEntity item = new ItemEntity(level, player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.setNoPickUpDelay();
			item.addTag(NoGrief.TAG);
			level.addFreshEntity(item);
		}
	}

	private Contribution() {
	}
}
