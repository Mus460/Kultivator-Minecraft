package dev.kultivator.sect.build.flow;

import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The offering of the mountain's lifting (Sesaji Angkat Gunung, DESIGN 37.3.3, the unified rates of audit 4.10): spirit
 * stones paid through {@link NilaiBatu#pay} (1 / 9 / 81 / 729 NB) and rare items, taken from the owner's inventory when
 * the sky design is confirmed and given back whole when it is cancelled before its first block stands. Building
 * materials are billed per tier from the sect's storehouse like on the ground; the offering is the rite. A9 asks for four
 * Supreme stones (2,916 NB), more than A7-A8's 24 High (1,944 NB), as the audit asked. Config
 * {@code sekteLangit.sesajiSkala} scales the counts (rounded up, at least 1 when above 0).
 */
public final class LangitSesaji {

	/** One bill: the stones (grade, count) and the items. */
	public record Bill(int grade, int stones, Map<Item, Integer> items) {
		public long nb() {
			return NilaiBatu.VALUE[grade] * stones;
		}
	}

	/** The bill of sky template {@code sky} (11-19) or sibling 1-9, scaled by the config. */
	public static Bill bill(int sky) {
		int n = sky > 10 ? sky - 10 : sky;
		double scale = LangitConfig.get().sesajiSkala;
		Map<Item, Integer> items = new LinkedHashMap<>();
		int grade;
		int stones;
		if (n <= 3) {
			grade = SpiritStones.MID;
			stones = 16;
			items.put(Items.PHANTOM_MEMBRANE, 8);
			items.put(Items.ENDER_PEARL, 8);
			items.put(Items.FEATHER, 16);
		} else if (n <= 6) {
			grade = SpiritStones.HIGH;
			stones = 8;
			items.put(Items.PHANTOM_MEMBRANE, 24);
			items.put(Items.ENDER_PEARL, 16);
			items.put(Items.ECHO_SHARD, 4);
		} else if (n <= 8) {
			grade = SpiritStones.HIGH;
			stones = 24;
			items.put(Items.PHANTOM_MEMBRANE, 48);
			items.put(Items.GHAST_TEAR, 4);
			items.put(Items.ECHO_SHARD, 8);
			items.put(Items.HEART_OF_THE_SEA, 1);
		} else {
			grade = SpiritStones.SUPREME;
			stones = 4;
			items.put(Items.PHANTOM_MEMBRANE, 64);
			items.put(Items.ECHO_SHARD, 16);
			items.put(Items.NETHER_STAR, 1);
		}
		Map<Item, Integer> scaled = new LinkedHashMap<>();
		items.forEach((item, count) -> {
			int c = scale(count, scale);
			if (c > 0) {
				scaled.put(item, c);
			}
		});
		return new Bill(grade, scale(stones, scale), scaled);
	}

	private static int scale(int count, double scale) {
		if (scale <= 0.0D) {
			return 0;
		}
		return Math.max(1, (int) Math.ceil(count * scale));
	}

	/** The bill as one chat line: "16 Batu Roh Menengah, 8 Membran Phantom, ...". */
	public static MutableComponent describe(Bill bill) {
		MutableComponent line = Component.empty();
		if (bill.stones() > 0) {
			line.append(Component.literal(bill.stones() + " ")).append(SpiritStones.name(bill.grade()));
		}
		for (Map.Entry<Item, Integer> entry : bill.items().entrySet()) {
			if (!line.getSiblings().isEmpty()) {
				line.append(Component.literal(", "));
			}
			line.append(Component.literal(entry.getValue() + " ")).append(Component.translatable(entry.getKey().getDescriptionId()));
		}
		return line;
	}

	/** What is missing from the player's inventory (null when the bill is covered). */
	public static @Nullable MutableComponent missing(ServerPlayer player, Bill bill) {
		MutableComponent line = null;
		if (NilaiBatu.total(player) < bill.nb()) {
			line = Component.empty().append(NilaiBatu.text(bill.nb() - NilaiBatu.total(player)));
		}
		for (Map.Entry<Item, Integer> entry : bill.items().entrySet()) {
			int have = count(player.getInventory(), entry.getKey());
			if (have < entry.getValue()) {
				if (line == null) {
					line = Component.empty();
				} else {
					line.append(Component.literal(", "));
				}
				line.append(Component.literal((entry.getValue() - have) + " ")).append(Component.translatable(entry.getKey().getDescriptionId()));
			}
		}
		return line;
	}

	/** Takes the offering of {@code sky} from the player (all or nothing) and remembers it for a refund. */
	public static boolean take(ServerPlayer player, int sky) {
		Bill bill = bill(sky);
		MutableComponent missing = missing(player, bill);
		if (missing != null) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.sesaji.kurang", missing).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!NilaiBatu.pay(player, bill.nb())) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.sesaji.kurang", NilaiBatu.text(bill.nb())).withStyle(ChatFormatting.RED));
			return false;
		}
		for (Map.Entry<Item, Integer> entry : bill.items().entrySet()) {
			remove(player.getInventory(), entry.getKey(), entry.getValue());
		}
		CompoundTag paid = new CompoundTag();
		paid.putLong("nb", bill.nb());
		CompoundTag items = new CompoundTag();
		bill.items().forEach((item, count) -> items.putInt(BuiltInRegistries.ITEM.getKey(item).toString(), count));
		paid.put("items", items);
		paid.putInt("sky", sky);
		LangitApiImpl.data(player.getUUID()).put("sesaji", paid);
		FlowData.dirty();
		Msg.chat(player, Component.translatable("kultivator.sekte_langit.sesaji.diambil", describe(bill)).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** True while an offering is held for a refund (the design has no block yet). */
	public static boolean held(UUID owner) {
		return !LangitApiImpl.data(owner).getCompoundOrEmpty("sesaji").isEmpty();
	}

	/** Gives the held offering back (the design was cancelled before its first block). */
	public static void refund(ServerPlayer player) {
		CompoundTag data = LangitApiImpl.data(player.getUUID());
		CompoundTag paid = data.getCompoundOrEmpty("sesaji");
		if (paid.isEmpty()) {
			return;
		}
		data.remove("sesaji");
		FlowData.dirty();
		NilaiBatu.give(player, paid.getLongOr("nb", 0L));
		CompoundTag items = paid.getCompoundOrEmpty("items");
		for (String key : items.keySet()) {
			Identifier id = Identifier.tryParse(key);
			Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			int count = items.getIntOr(key, 0);
			while (item != null && item != Items.AIR && count > 0) {
				int each = Math.min(count, item.getDefaultMaxStackSize());
				count -= each;
				ItemStack stack = new ItemStack(item, each);
				if (!player.getInventory().add(stack) && !stack.isEmpty()) {
					player.spawnAtLocation(player.level(), stack);
				}
			}
		}
		Msg.chat(player, Component.translatable("kultivator.sekte_langit.sesaji.kembali").withStyle(ChatFormatting.GRAY));
	}

	/** The offering is spent for good (the first block of the sky site stood, or the Ascension happened). */
	public static void settle(UUID owner) {
		CompoundTag data = LangitApiImpl.data(owner);
		if (data.contains("sesaji")) {
			data.remove("sesaji");
			FlowData.dirty();
		}
	}

	private static int count(Inventory inventory, Item item) {
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void remove(Inventory inventory, Item item, int count) {
		int left = count;
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				int use = Math.min(left, stack.getCount());
				stack.shrink(use);
				left -= use;
			}
		}
		inventory.setChanged();
	}

	static void clear() {
	}

	private LangitSesaji() {
	}
}
