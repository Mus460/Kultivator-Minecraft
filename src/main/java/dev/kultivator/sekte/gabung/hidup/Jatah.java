package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.ui.GabungLayar;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The weekly allowance of DESIGN 41.6.7: once per 7 world days a member collects PK and pills at the Paviliun Jasa (in
 * the own compound, or within 32 blocks of the pavilion titik of a sect without one; the button of the membership card
 * works there too). It does not pile up beyond two weeks. The PK is times the class factor {@code kf} (Tetua Tamu: a
 * flat 100); the pills are the alchemy items of the pavilion's own tap (MID quality, 41.6.5), never spirit stones:
 * <pre>
 * Pelayan   0 PK    2 Pil Penyembuh + canteen food      Inti      80 PK   1 Pil Fondasi (Kecil/Menengah) or the pill
 * Tercatat  5 PK    2 Pil Penyembuh                                        one below the class's best
 * Luar     10 PK    2 Pil Pengumpul Qi                  Pewaris/Tetua 200 PK  the pill one below the class's best
 * Dalam    30 PK    3 Pil Pengumpul Qi                  Wakil    300 PK   the class's best pill
 *                                                       Ketua    500 PK   the class's best pill
 * </pre>
 */
public final class Jatah {

	/** World days between two allowances, and the most weeks that pile up. */
	public static final int HARI = 7;
	public static final int MINGGU_MAKS = 2;
	public static final int PK_TAMU = 100;

	/** PK of rank {@code p} before {@code kf} (Tamu: flat, see {@link #PK_TAMU}). */
	public static int pkDasar(Pangkat p) {
		return switch (p) {
			case PELAYAN -> 0;
			case TERCATAT -> 5;
			case LUAR -> 10;
			case DALAM -> 30;
			case INTI -> 80;
			case PEWARIS, TETUA -> 200;
			case WAKIL -> 300;
			case KETUA -> 500;
			case TAMU -> PK_TAMU;
		};
	}

	/** True when the allowance of {@code k} is ready on day {@code hari} (the card's {@code jatah} flag). */
	public static boolean siap(Keanggotaan k, long hari) {
		return k.status.anggota() && (k.jatahHari < 0L || hari - k.jatahHari >= HARI);
	}

	/** Weeks the allowance covers when collected on day {@code hari} (1-2; 0 when not ready). */
	public static int minggu(Keanggotaan k, long hari) {
		if (!siap(k, hari)) {
			return 0;
		}
		return k.jatahHari < 0L ? 1 : (int) Math.min(MINGGU_MAKS, (hari - k.jatahHari) / HARI);
	}

	/** The PK of one week for {@code k} at {@code sekte}. */
	public static int pk(Keanggotaan k, String sekte) {
		if (k.pangkat == Pangkat.TAMU) {
			return PK_TAMU;
		}
		return (int) Math.round(pkDasar(k.pangkat) * GabungApi.kelasSekte(sekte).kf());
	}

	/** The goods of one week for {@code k} at {@code sekte} (made fresh). */
	public static List<ItemStack> barang(Keanggotaan k, String sekte) {
		int kelas = GabungApi.kelas(sekte);
		List<ItemStack> out = new ArrayList<>();
		switch (k.pangkat) {
			case PELAYAN -> {
				out.add(StokPaviliun.pilJatah("healing_pill", 2));
				out.add(new ItemStack(Items.BREAD, 3));
				out.add(new ItemStack(Items.BAKED_POTATO, 2));
			}
			case TERCATAT -> out.add(StokPaviliun.pilJatah("healing_pill", 2));
			case LUAR -> out.add(StokPaviliun.pilJatah("qi_gathering_pill", 2));
			case DALAM -> out.add(StokPaviliun.pilJatah("qi_gathering_pill", 3));
			case INTI -> out.add(StokPaviliun.pilJatah(kelas <= KelasSekte.MENENGAH.nomor() ? "foundation_pill" : StokPaviliun.pilKelas(kelas, 1), 1));
			case PEWARIS, TETUA -> out.add(StokPaviliun.pilJatah(StokPaviliun.pilKelas(kelas, 1), 1));
			case WAKIL, KETUA -> out.add(StokPaviliun.pilJatah(StokPaviliun.pilKelas(kelas, 0), 1));
			case TAMU -> {
			}
		}
		out.removeIf(ItemStack::isEmpty);
		return out;
	}

	/** True when {@code player} stands where the allowance is handed out. */
	static boolean diTempat(ServerPlayer player, String sekte) {
		if (TugasHarian.situs(player, sekte) >= 0L) {
			return true;
		}
		BlockPos jasa = GabungApi.titik(sekte, TitikGabung.JASA);
		return jasa != null && player.distanceToSqr(jasa.getX() + 0.5D, player.getY(), jasa.getZ() + 0.5D) <= 32.0D * 32.0D;
	}

	/** Collects the allowance (the card's {@code jatah} op, the pavilion keeper). Null on success, else the reason. */
	public static @Nullable Component ambil(ServerPlayer player) {
		return ambil(player, false);
	}

	/** {@link #ambil(ServerPlayer)}; {@code diMana} skips the place check ({@code uji gabung jatah}). */
	public static @Nullable Component ambil(ServerPlayer player, boolean diMana) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !GabungApi.aktif()) {
			return Component.translatable("kultivator.gabung.hidup.bukan_anggota");
		}
		long hari = Kota.day(server);
		int weeks = minggu(k, hari);
		if (weeks <= 0) {
			return Component.translatable("kultivator.gabung.jatah.belum", HARI - (hari - k.jatahHari));
		}
		if (!diMana && !diTempat(player, sekte)) {
			return Component.translatable("kultivator.gabung.jatah.tempat");
		}
		int pk = pk(k, sekte) * weeks;
		List<ItemStack> goods = new ArrayList<>();
		for (int w = 0; w < weeks; w++) {
			goods.addAll(barang(k, sekte));
		}
		k.jatahHari = hari;
		k.catat(hari, "kultivator.gabung.riwayat.jatah", Integer.toString(weeks), Integer.toString(pk));
		GabungData.get(server).setDirty();
		if (pk > 0) {
			GabungApi.tambahPk(player, pk, "gabung.jatah");
		}
		MutableComponent list = Component.empty();
		for (int i = 0; i < goods.size(); i++) {
			ItemStack stack = goods.get(i);
			list.append(Component.literal((i > 0 ? ", " : "") + stack.getCount() + "× ")).append(stack.getHoverName());
			Barang.beri(player, stack);
		}
		Component line = Component.translatable(weeks > 1 ? "kultivator.gabung.jatah.ambil_dua" : "kultivator.gabung.jatah.ambil", pk, list);
		Msg.chat(player, line.copy().withStyle(ChatFormatting.GOLD));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7F, 1.0F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0D, player.getZ(), 6, 0.4D, 0.5D, 0.4D, 0.0D);
		GabungLayar.pesan(player, line, LembarKunci.PESAN_OK);
		return null;
	}

	/** Lazy upkeep: tells a member at login that the allowance waits. */
	static void sentuh(ServerPlayer player, Keanggotaan k, long hari) {
		if (k.sekteAktif() != null && siap(k, hari)) {
			Msg.chat(player, Component.translatable("kultivator.gabung.jatah.siap", GabungLayar.namaSekte(k.sekte)).withStyle(ChatFormatting.AQUA));
		}
	}

	private Jatah() {
	}
}
