package dev.kultivator.dunia.tempat.ekonomi;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.tempa.StorageRingItem;
import dev.kultivator.util.NoGrief;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Shared helpers of the money loop (DESIGN 38.M.0, 38.M.5, 38.0 rule 16): the player's band, the world day, item
 * hand-out, item (de)serialisation for SavedData, the one sell filter of every 38 shop and stall (never a stone, a ring
 * or pouch that still holds items, a filled container, or a phase-9 ware), and the {@code kultivator:asal_toko} component
 * that stops quick buy-and-resell loops: a ware bought from a 38 shop carries its purchase day and is refused by 38
 * shops and stalls for {@link #ASAL_HARI} days. No ItemStack is made in a static initializer.
 */
public final class Barang {

	/** Days a 38 shop's ware is refused for resale (38.M.5). */
	public static final int ASAL_HARI = 3;

	/** {@code kultivator:asal_toko}: the world day a 38 shop sold this stack. */
	public static final DataComponentType<Long> ASAL_TOKO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("asal_toko"), DataComponentType.<Long>builder()
					.persistent(Codec.LONG)
					.networkSynchronized(ByteBufCodecs.VAR_LONG)
					.build());

	/** {@code kultivator:ekonomi}: per-stack record of the money loop items (note value and id, raw stone id, inspection). */
	public static final DataComponentType<CompoundTag> DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("ekonomi"), DataComponentType.<CompoundTag>builder()
					.persistent(CompoundTag.CODEC)
					.networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
					.build());

	/** Loads the class (components above registered). */
	static void init() {
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Time and band

	/** The player's realm band 0-8 ({@code Harga} bands). */
	public static int pita(ServerPlayer player) {
		return Mth.clamp(Cultivators.get(player).realm().ordinal(), 0, Harga.BANDS - 1);
	}

	/** The in-game day (overworld clock / 24000, the town day). */
	public static long hari(MinecraftServer server) {
		return Kota.day(server);
	}

	/** The overworld game time (for countdowns stored in SavedData). */
	public static long waktu(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** Time of day 0-23999 (0 = 06:00). */
	public static long jamDunia(MinecraftServer server) {
		return Kota.timeOfDay(server);
	}

	/** The global price factor: town {@code priceScale} × {@code ekonomi38.pengaliHarga}. */
	public static double skala() {
		return Kota.config().priceScale * EkonomiConfig.get().pengaliHarga;
	}

	/** {@code base} NB × {@link #skala()} × {@code faktor}, at least 1. */
	public static long harga(long base, double faktor) {
		return Math.max(1L, Math.round(base * skala() * faktor));
	}

	/** Shop discounts are capped here (38.0 rule 4). */
	public static final double DISKON_MAKS = 0.15D;

	/**
	 * The player's discount at a 38 shop of town/site {@code town}: Nama Baik (Terhormat 5 %, Pahlawan 10 % at the capital;
	 * a town's own 10 % from Nama Baik 50), the guild badge (3 stars: 5 % at guild shops), capped at {@link #DISKON_MAKS}.
	 */
	public static double diskon(ServerPlayer player, long town, boolean gilda) {
		double d = 0.0D;
		int rank = dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi.pangkatNamaBaik(player, town);
		if (rank >= 5) {
			d += 0.10D;
		} else if (rank >= 4) {
			d += 0.05D;
		} else if (dev.kultivator.dunia.kota.KotaData.get(player.level().getServer()).reputation(town, player.getUUID()) >= 50) {
			d += 0.10D;
		}
		if (gilda && Lencana.bintang(player) >= Lencana.DISKON_BINTANG) {
			d += Lencana.DISKON;
		}
		return Math.min(DISKON_MAKS, d);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Hand-out

	/** Gives {@code stack} to the player, the rest dropped at his feet (protected from griefing, only he picks it up). */
	public static void beri(ServerPlayer player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemStack copy = stack.copy();
		if (!player.getInventory().add(copy) && !copy.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), copy.copy());
			item.setTarget(player.getUUID());
			item.setNoPickUpDelay();
			item.addTag(NoGrief.TAG);
			player.level().addFreshEntity(item);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// SavedData stacks

	/** {@code stack} encoded with the registries (components kept); an empty tag for an empty stack. */
	public static Tag simpan(ItemStack stack, HolderLookup.Provider registries) {
		if (stack == null || stack.isEmpty()) {
			return new CompoundTag();
		}
		return ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).result().orElseGet(CompoundTag::new);
	}

	/** The stack of {@link #simpan}; empty when unreadable (a removed item). */
	public static ItemStack muat(Tag tag, HolderLookup.Provider registries) {
		if (tag == null || tag instanceof CompoundTag compound && compound.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sell filter and resale guard

	/** True when the stack holds other items (a ring, pouch, shulker box, bundle with contents). */
	public static boolean berisi(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (StorageRingItem.unsafeToSell(stack)) {
			return true;
		}
		ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		if (container != null && container.nonEmptyItems().iterator().hasNext()) {
			return true;
		}
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		return bundle != null && !bundle.isEmpty();
	}

	/** The common 38 sell filter (38.0 rule 16): a valued ware, no stone, no filled container, no phase-9 ware. */
	public static boolean bolehDijual(ItemStack stack) {
		return !stack.isEmpty() && !SpiritStones.isStone(stack) && !berisi(stack) && !Harga.phase9(stack) && EkonomiApi.harga(stack) > 0L;
	}

	/** Marks {@code stack} as bought from a 38 shop today. */
	public static ItemStack tandaiAsal(ItemStack stack, long hari) {
		if (!stack.isEmpty()) {
			stack.set(ASAL_TOKO, hari);
		}
		return stack;
	}

	/** True while a 38 shop's ware is younger than {@link #ASAL_HARI} days (refused by 38 shops and stalls). */
	public static boolean baruDibeli(ItemStack stack, long hari) {
		Long asal = stack.get(ASAL_TOKO);
		return asal != null && hari - asal < ASAL_HARI;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Text

	/** "12 NB" in gold. */
	public static MutableComponent nb(long nb) {
		return NilaiBatu.text(nb);
	}

	/** "1 Tinggi · 4 Menengah · 3 Rendah" (the stones {@code NilaiBatu.give} would hand out, never Agung). */
	public static Component rincian(long nb) {
		long left = Math.max(0L, nb);
		long tinggi = left / NilaiBatu.VALUE[2];
		left -= tinggi * NilaiBatu.VALUE[2];
		long menengah = left / NilaiBatu.VALUE[1];
		left -= menengah * NilaiBatu.VALUE[1];
		return Component.translatable("kultivator.tempat_ekonomi.rincian", tinggi, menengah, left);
	}

	/** The player's stones by grade [rendah, menengah, tinggi, agung] (header key {@code batu}). */
	public static int[] batu(ServerPlayer player) {
		int[] out = new int[4];
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			int grade = SpiritStones.grade(stack);
			if (grade >= 0 && grade < 4) {
				out[grade] += stack.getCount();
			}
		}
		return out;
	}

	private Barang() {
	}
}
