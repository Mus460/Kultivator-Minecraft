package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.harta.HartaText;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Surat Wesel, "flying money" (DESIGN 38.M.2.2): the Rumah Uang writes a bearer note for {@code nb} NB at a 2 % fee (min
 * 1), paid from the deposit (else from the stones carried); any Rumah Uang cashes it once (its serial is remembered for
 * the world, a copied note is worthless). No interest, no profit: the note only moves value.
 */
public final class SuratWesel {

	public static final double BIAYA = 0.02D;
	public static final long MIN_NB = 10L;
	public static final long MAKS_NB = 100_000L;
	private static final String DICAIRKAN = "wesel_cair";

	/** The fee of a note of {@code nb}. */
	public static long biaya(long nb) {
		return Math.max(1L, Math.round(nb * BIAYA));
	}

	/** Writes a note of {@code nb} for the player; false (nothing taken) when he cannot pay it and its fee. */
	static boolean tulis(ServerPlayer player, long nb) {
		if (nb < MIN_NB || nb > MAKS_NB) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.wesel.batas", MIN_NB, MAKS_NB), false);
			return false;
		}
		MinecraftServer server = player.level().getServer();
		long total = nb + biaya(nb);
		if (!RumahUang.ambilSaldo(player, total) && !NilaiBatu.pay(player, total)) {
			Lembar.kurangUang(player, total);
			return false;
		}
		EkonomiData data = EkonomiData.get(server);
		long serial = data.serial("wesel");
		ItemStack note = new ItemStack(EkonomiItems.SURAT_WESEL);
		CompoundTag tag = new CompoundTag();
		tag.putLong("nb", nb);
		tag.putLong("id", serial);
		tag.putLong("hari", Barang.hari(server));
		tag.putString("penulis", player.getName().getString());
		note.set(Barang.DATA, tag);
		Barang.beri(player, note);
		KasKota.tambah(server, RumahUang.situsTerakhir(player), biaya(nb));
		RumahUang.riwayat(player, -total, "kultivator.tempat_ekonomi.bank.riwayat.wesel", serial);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.wesel.ditulis", NilaiBatu.text(nb), serial), true);
		return true;
	}

	/** Cashes the note {@code stack} (consumed) into stones; false when it was already cashed or is not a note. */
	static boolean cair(ServerPlayer player, ItemStack stack) {
		if (!(stack.getItem() instanceof WeselItem)) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.wesel.tak_ada"), false);
			return false;
		}
		CompoundTag tag = stack.getOrDefault(Barang.DATA, new CompoundTag());
		long nb = tag.getLongOr("nb", 0L);
		long id = tag.getLongOr("id", 0L);
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		long[] cashed = data.global().getLongArray(DICAIRKAN).orElse(new long[0]);
		if (nb <= 0L || id <= 0L || Arrays.stream(cashed).anyMatch(each -> each == id)) {
			stack.shrink(1);
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.wesel.palsu"), false);
			return false;
		}
		long[] grown = Arrays.copyOf(cashed, cashed.length + 1);
		grown[cashed.length] = id;
		data.global().putLongArray(DICAIRKAN, grown);
		data.touch();
		stack.shrink(1);
		NilaiBatu.give(player, nb);
		RumahUang.riwayat(player, nb, "kultivator.tempat_ekonomi.bank.riwayat.cair", id);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.bank.wesel.dicairkan", NilaiBatu.text(nb)), true);
		return true;
	}

	/** The note's value (0 when not a note). */
	static long nilai(ItemStack stack) {
		return stack.getItem() instanceof WeselItem ? stack.getOrDefault(Barang.DATA, new CompoundTag()).getLongOr("nb", 0L) : 0L;
	}

	/** The note item: shows its value, serial and writer. */
	public static final class WeselItem extends Item {

		public WeselItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			CompoundTag tag = stack.getOrDefault(Barang.DATA, new CompoundTag());
			long nb = tag.getLongOr("nb", 0L);
			if (nb > 0L) {
				builder.accept(Component.translatable("kultivator.tempat_ekonomi.wesel.nilai", NilaiBatu.text(nb)));
				builder.accept(Component.translatable("kultivator.tempat_ekonomi.wesel.seri", tag.getLongOr("id", 0L), tag.getStringOr("penulis", "?"),
						tag.getLongOr("hari", 0L)).withStyle(ChatFormatting.GRAY));
			} else {
				builder.accept(Component.translatable("kultivator.tempat_ekonomi.wesel.kosong").withStyle(ChatFormatting.GRAY));
			}
			builder.accept(Component.translatable("item.kultivator.surat_wesel.tooltip").withColor(HartaText.USE_COLOR));
			HartaText.sources("item.kultivator.surat_wesel", builder);
		}
	}

	private SuratWesel() {
	}
}
