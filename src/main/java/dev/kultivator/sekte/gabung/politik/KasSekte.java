package dev.kultivator.sekte.gabung.politik;

import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.harta.Harta;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.masuk.GabungItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The sect treasury of DESIGN 41.4.7, 41.6.2, 41.8.5 and 41.9 ("Kas sekte bersama"). One economy (41.0 item 4): the
 * treasury is the sect's resource {@code sumber} in module D; members feed it, never their own PK.
 * <ul>
 *   <li><b>Donations</b> ({@code /kultivasi gabung sumbang <NB>}, the Politik screen's {@code sumbang} with the stack in
 *   hand): spirit stones count {@code NB / 2} PK (1 PK = 2 NB), goods their Kantor Misi value ({@code Harta.missionValue},
 *   else the shop value / 2). Every 50 PK of value is +1 {@code sumber}, at most 20 a day per sect from all players
 *   ({@code DiplomasiGabung.SEBAB_KAS}); the rest is kept as honour in this month's list (name + amount, 41.9). Donations
 *   never give personal PK (K8) and goods bought at the Paviliun ({@code asal_sekte}) are refused.</li>
 *   <li><b>Tribute</b> of branch halls ({@link #terimaUpeti}, 41.8.2): the same rule, own reason {@code upeti} (no cap).</li>
 *   <li><b>The master's share</b> (41.8.5): a player master receives {@code min(5 x sumber, 300 x class)} personal PK every
 *   7 days (it is PK, never NB) through {@code GabungApi.tambahPk}.</li>
 *   <li><b>Bounties</b> (41.7.3, 41D-P): the treasury in NB is {@code sumber x 2}; {@link #ambilNb} pays a bounty out of it.</li>
 * </ul>
 * State in the sect's {@code DataSekteGabung.kas} (this month) and its {@code politik} compound ({@code kasSisa}, the
 * 7-day graph {@code grafik}, {@code kasKetuaHari}). Server thread only.
 */
public final class KasSekte {

	/** PK of value per +1 resource. */
	public static final int PK_PER_SUMBER = 50;
	/** Days between two shares of a player master. */
	public static final int JEDA_KETUA = 7;
	/** Days of the resource graph. */
	public static final int GRAFIK_HARI = 7;

	private static final String KEY = "kultivator.gabung.politik.kas.";

	// ================================================================== donations

	/** Donates {@code nb} Nilai Batu from the player's stones; null when done, else why not. */
	public static @Nullable Component sumbangNb(ServerPlayer player, long nb) {
		String sekte = sekteAnggota(player);
		if (sekte == null) {
			return Component.translatable(KEY + "bukan_anggota");
		}
		if (nb < Harga.NB_PER_PK) {
			return Component.translatable(KEY + "minimal", (long) Harga.NB_PER_PK);
		}
		long bulat = nb - nb % (long) Harga.NB_PER_PK;
		if (!EkonomiApi.bayar(player, bulat, "gabung.kas")) {
			return Component.translatable(KEY + "kurang_nb", bulat);
		}
		int pk = (int) Math.min(Integer.MAX_VALUE, bulat / (long) Harga.NB_PER_PK);
		masuk(player, sekte, pk, Component.translatable(KEY + "batu", bulat));
		return null;
	}

	/** Donates the stack in the main hand (goods by their Kantor Misi value); null when done, else why not. */
	public static @Nullable Component sumbangTangan(ServerPlayer player) {
		String sekte = sekteAnggota(player);
		if (sekte == null) {
			return Component.translatable(KEY + "bukan_anggota");
		}
		ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (stack.isEmpty()) {
			return Component.translatable(KEY + "tangan_kosong");
		}
		if (stack.has(GabungItems.ASAL_SEKTE)) {
			return Component.translatable(KEY + "barang_paviliun");
		}
		int pk = nilaiPk(stack);
		if (pk <= 0) {
			return Component.translatable(KEY + "tak_bernilai", stack.getHoverName());
		}
		Component what = Component.translatable(KEY + "barang", stack.getCount(), stack.getHoverName());
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		masuk(player, sekte, pk, what);
		return null;
	}

	/** The treasury value in PK of {@code stack} (all of it): Kantor Misi value, else the shop value / 2. */
	public static int nilaiPk(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		double per = Harta.missionValue(stack);
		if (per <= 0.0D) {
			ItemStack one = stack.copyWithCount(1);
			per = EkonomiApi.harga(one) / Harga.NB_PER_PK;
		}
		return (int) Math.min(Integer.MAX_VALUE, Math.floor(per * stack.getCount()));
	}

	private static void masuk(ServerPlayer player, String sekte, int pk, Component what) {
		MinecraftServer server = player.level().getServer();
		int sumber = catat(server, sekte, player.getUUID(), pk, DiplomasiGabung.SEBAB_KAS);
		GabungApi.anggota(player).catat(GabungApi.hari(server), "kultivator.gabung.riwayat.sumbang", Integer.toString(pk), sekte);
		GabungData.get(server).setDirty();
		player.sendSystemMessage(Component.translatable(KEY + "terima", what, pk, DiplomasiGabung.nama(server, sekte), sumber)
				.withStyle(ChatFormatting.GREEN));
	}

	/**
	 * Books {@code pk} of value from {@code dari} (a player, or a branch hall's owner) into the sect: this month's list, the
	 * carried remainder, and {@code sumber} through {@code DiplomasiGabung} with reason {@code sebab}. Returns the resources
	 * actually added.
	 */
	public static int catat(MinecraftServer server, String sekte, @Nullable UUID dari, int pk, String sebab) {
		if (server == null || sekte == null || pk <= 0 || !Gabung.aktif()) {
			return 0;
		}
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.sekte(sekte);
		long bulan = Math.floorDiv(GabungApi.hari(server), 30L);
		if (d.kasBulan != bulan) {
			d.kasBulan = bulan;
			d.kas.clear();
		}
		if (dari != null) {
			d.kas.merge(dari, pk, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
		}
		CompoundTag b = d.bagian(DiplomasiGabung.BAGIAN);
		long total = (long) b.getIntOr("kasSisa", 0) + pk;
		int naik = (int) Math.min(Integer.MAX_VALUE, total / PK_PER_SUMBER);
		b.putInt("kasSisa", (int) (total % PK_PER_SUMBER));
		data.setDirty();
		return naik <= 0 ? 0 : DiplomasiGabung.tambahSumber(server, sekte, naik, sebab);
	}

	/** Tribute of a branch hall (41.8.2): booked under the owner, reason {@code upeti} (no daily cap). */
	public static int terimaUpeti(MinecraftServer server, String sekte, UUID pemilikAula, int pk) {
		return catat(server, sekte, pemilikAula, pk, DiplomasiGabung.SEBAB_UPETI);
	}

	/** This month's donations of the sect (player UUID -> PK value), largest first. */
	public static Map<UUID, Integer> sumbangan(MinecraftServer server, String sekte) {
		Map<UUID, Integer> out = new LinkedHashMap<>();
		GabungData.DataSekteGabung d = server == null ? null : GabungData.get(server).peekSekte(sekte);
		if (d == null || d.kasBulan != Math.floorDiv(GabungApi.hari(server), 30L)) {
			return out;
		}
		d.kas.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
				.forEach(e -> out.put(e.getKey(), e.getValue()));
		return out;
	}

	/** The last {@link #GRAFIK_HARI} days of the sect's resource (oldest first; 0 for days not seen). */
	public static int[] grafik(MinecraftServer server, String sekte) {
		int[] g = server == null ? new int[0] : DiplomasiGabung.bagian(server, sekte).getIntArray("grafik").orElse(new int[0]);
		int[] out = new int[GRAFIK_HARI];
		int from = Math.max(0, g.length - GRAFIK_HARI);
		System.arraycopy(g, from, out, GRAFIK_HARI - (g.length - from), g.length - from);
		return out;
	}

	/** The PK a player master would receive this week: {@code min(5 x sumber, 300 x class)}. */
	public static int kasKetua(MinecraftServer server, String sekte) {
		int kelas = KelasSekte.of(Math.max(1, GabungApi.tier(sekte)));
		return Math.max(0, Math.min(5 * DiplomasiGabung.sumber(server, sekte), 300 * kelas));
	}

	/** The treasury in NB ({@code sumber x 2}, never negative). */
	public static long kasNb(MinecraftServer server, String sekte) {
		return Math.max(0L, DiplomasiGabung.sumber(server, sekte) * (long) Harga.NB_PER_PK);
	}

	/** Pays {@code nb} out of the treasury (a bounty, 41.7.3): {@code ceil(nb / 2)} resources; false when it cannot. */
	public static boolean ambilNb(MinecraftServer server, String sekte, long nb) {
		if (nb <= 0L) {
			return true;
		}
		if (kasNb(server, sekte) < nb) {
			return false;
		}
		int sumber = (int) Math.min(Integer.MAX_VALUE, (nb + 1L) / (long) Harga.NB_PER_PK);
		DiplomasiGabung.ubahSumber(server, sekte, -sumber, "bayar");
		return true;
	}

	// ================================================================== daily (from Politik)

	/** The day's graph point and, for a player master present in the world, the weekly share. */
	static void harian(MinecraftServer server, String sekte, long hari) {
		CompoundTag b = DiplomasiGabung.bagian(server, sekte);
		int[] g = b.getIntArray("grafik").orElse(new int[0]);
		if (b.getLongOr("grafikHari", -1L) != hari) {
			int[] next = new int[Math.min(GRAFIK_HARI, g.length + 1)];
			int keep = next.length - 1;
			System.arraycopy(g, g.length - keep, next, 0, keep);
			next[keep] = DiplomasiGabung.sumber(server, sekte);
			b.putIntArray("grafik", next);
			b.putLong("grafikHari", hari);
		}
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.sekte(sekte);
		if (d.ketuaPemain != null && hari >= b.getLongOr("kasKetuaHari", -100L) + JEDA_KETUA) {
			ServerPlayer ketua = server.getPlayerList().getPlayer(d.ketuaPemain);
			Keanggotaan k = data.peek(d.ketuaPemain);
			int pk = kasKetua(server, sekte);
			if (ketua != null && k != null && k.anggotaDi(sekte) && pk > 0) {
				b.putLong("kasKetuaHari", hari);
				GabungApi.tambahPk(ketua, pk, "gabung.kas_ketua");
				ketua.sendSystemMessage(Component.translatable(KEY + "ketua", pk, DiplomasiGabung.nama(server, sekte)).withStyle(ChatFormatting.GOLD));
			}
		}
		data.setDirty();
	}

	private static @Nullable String sekteAnggota(ServerPlayer player) {
		if (!Gabung.aktif()) {
			return null;
		}
		return GabungApi.sekte(player.getUUID());
	}

	private KasSekte() {
	}
}
