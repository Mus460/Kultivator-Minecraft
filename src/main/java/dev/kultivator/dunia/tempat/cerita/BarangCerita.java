package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.Kultivator;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Story items of DESIGN 38.S (letters, proofs, the palace badge, the nameless sword): vanilla stacks carrying the story id
 * in {@code minecraft:custom_data} ({@code {kultivator_cerita: "<id>"}}), an item name {@code kultivator.cerita.barang.<id>}
 * and a lore line {@code kultivator.cerita.barang.<id>.lore}, so no registry entry, model or texture is added and the
 * item looks like what it is (paper for a letter, an ink sac for a black seal). Stacks are made only when given, never
 * in static initialisers. Effects and conditions name them {@code cerita:<id>}; any other id is a registry item
 * ({@code minecraft:copper_ingot}, {@code kultivator:...}).
 */
public final class BarangCerita {

	public static final String TAG = "kultivator_cerita";
	public static final String AWALAN = "cerita:";

	/** One story item: base item, rarity, glint, and (for the sword) enchantments by key and level. */
	private record Jenis(Identifier dasar, Rarity rarity, boolean kilau, Map<ResourceKey<Enchantment>, Integer> pesona) {
	}

	private static final Map<String, Jenis> JENIS = new LinkedHashMap<>();

	static {
		biasa("surat_bibi_hong", "paper");
		biasa("balasan_ah_lun", "paper");
		biasa("sandal_berdarah", "leather");
		biasa("kertas_jimat_hitam", "paper");
		khusus("cap_bayangan", "ink_sac", Rarity.RARE);
		khusus("catatan_pembukuan", "book", Rarity.RARE);
		khusus("surat_mata_mata", "paper", Rarity.RARE);
		biasa("resep_penawar", "paper");
		biasa("air_mata_air_roh", "potion");
		biasa("rumput_bintang", "fern");
		khusus("pil_penawar_racun_darah", "glow_berries", Rarity.RARE);
		khusus("lencana_istana", "gold_nugget", Rarity.EPIC);
		biasa("halaman_resep_kuno", "paper");
		khusus("kristal_urat", "amethyst_shard", Rarity.UNCOMMON);
		biasa("wesel_palsu", "paper");
		khusus("batu_bermata_darah", "red_dye", Rarity.UNCOMMON);
		biasa("kitab_curian", "book");
		biasa("anak_rubah_roh", "rabbit_hide");
		biasa("bekal_kafilah", "bundle");
		biasa("surat_tuan_muda", "paper");
		biasa("muatan_awan", "chest");
		biasa("gulungan_koan", "paper");
		khusus("tanda_pertapa", "bamboo", Rarity.UNCOMMON);
		biasa("air_roh_ladang", "potion");
		khusus("tanda_bebas", "string", Rarity.UNCOMMON);
		JENIS.put("pedang_tanpa_nama", new Jenis(Identifier.withDefaultNamespace("iron_sword"), Rarity.EPIC, true,
				Map.of(Enchantments.SHARPNESS, 4, Enchantments.UNBREAKING, 3)));
		khusus("jimat_lentera", "paper", Rarity.UNCOMMON);
		khusus("lencana_penyelamat", "gold_nugget", Rarity.RARE);
		khusus("cincin_mo_ying", "ender_eye", Rarity.EPIC);
	}

	private static void biasa(String id, String dasar) {
		JENIS.put(id, new Jenis(Identifier.withDefaultNamespace(dasar), Rarity.UNCOMMON, false, Map.of()));
	}

	private static void khusus(String id, String dasar, Rarity rarity) {
		JENIS.put(id, new Jenis(Identifier.withDefaultNamespace(dasar), rarity, true, Map.of()));
	}

	public static List<String> ids() {
		return List.copyOf(JENIS.keySet());
	}

	public static boolean ada(String id) {
		return JENIS.containsKey(strip(id));
	}

	/** {@code cerita:x} -> {@code x}. */
	public static String strip(String id) {
		return id.startsWith(AWALAN) ? id.substring(AWALAN.length()) : id;
	}

	/** A fresh stack of story item {@code id} (empty when unknown). */
	public static ItemStack buat(ServerPlayer player, String id, int count) {
		String key = strip(id);
		Jenis jenis = JENIS.get(key);
		if (jenis == null) {
			return ItemStack.EMPTY;
		}
		Item item = BuiltInRegistries.ITEM.getValue(jenis.dasar);
		if (item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item, Math.max(1, Math.min(item.getDefaultMaxStackSize(), count)));
		CompoundTag tag = new CompoundTag();
		tag.putString(TAG, key);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("kultivator.cerita.barang." + key));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kultivator.cerita.barang." + key + ".lore"))));
		stack.set(DataComponents.RARITY, jenis.rarity);
		if (jenis.kilau) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
		}
		if (!jenis.pesona.isEmpty()) {
			var lookup = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			for (Map.Entry<ResourceKey<Enchantment>, Integer> e : jenis.pesona.entrySet()) {
				Holder<Enchantment> holder = lookup.getOrThrow(e.getKey());
				stack.enchant(holder, e.getValue());
			}
		}
		return stack;
	}

	/** The story id of a stack ("" for a plain item). */
	public static String idDari(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(TAG, "");
	}

	/** Matches {@code stack} against {@code id}: {@code cerita:x} by story id, anything else by registry id. */
	public static boolean cocok(ItemStack stack, String id) {
		if (stack.isEmpty()) {
			return false;
		}
		if (id.startsWith(AWALAN) || JENIS.containsKey(id) && !id.contains(":")) {
			return strip(id).equals(idDari(stack));
		}
		Identifier key = Identifier.tryParse(id.contains(":") ? id : Kultivator.MOD_ID + ":" + id);
		return key != null && key.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())) && idDari(stack).isEmpty();
	}

	/** How many items matching {@code id} the player carries. */
	public static int hitung(ServerPlayer player, String id) {
		int n = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (cocok(stack, id)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Takes up to {@code count} matching items; true when all were taken. */
	public static boolean ambil(ServerPlayer player, String id, int count) {
		if (hitung(player, id) < count) {
			return false;
		}
		int left = count;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (cocok(stack, id)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		inv.setChanged();
		return true;
	}

	/** Gives {@code count} of {@code id} (story item or registry item); a full inventory drops it for the player only. */
	public static boolean beri(ServerPlayer player, String id, int count) {
		ItemStack stack;
		if (id.startsWith(AWALAN) || JENIS.containsKey(id) && !id.contains(":")) {
			stack = buat(player, id, count);
		} else {
			Identifier key = Identifier.tryParse(id.contains(":") ? id : Kultivator.MOD_ID + ":" + id);
			Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
			stack = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, Math.min(64, count)));
		}
		if (stack.isEmpty()) {
			Kultivator.LOGGER.warn("Story item {} is unknown", id);
			return false;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			drop(player, stack);
		}
		return true;
	}

	private static void drop(ServerPlayer player, @Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack.copy());
		item.setTarget(player.getUUID());
		item.setNoPickUpDelay();
		item.addTag(dev.kultivator.util.NoGrief.TAG);
		player.level().addFreshEntity(item);
		stack.setCount(0);
	}

	private BarangCerita() {
	}
}
