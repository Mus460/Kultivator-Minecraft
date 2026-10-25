package dev.kultivator.sekte.gabung.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.api.SyaratMasuk;
import dev.kultivator.sekte.gabung.masuk.GabungItems;
import dev.kultivator.sekte.gabung.masuk.HariUjian;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Lamaran;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Lamaran sheet ({@code gabung_lamaran}, DESIGN 41.10.2, keys {@link Lamaran}): one sect, its five doors as tabs
 * (41.5.2), each with the tick and cross lines of {@link SyaratMasuk#periksa}, an advice next to a failed line ("pakai
 * Token"), the stages it runs, the best rank it can give and the next test day. Doors that cannot be used at all (no
 * token or letter in the bag, guest elders switched off) are locked tabs with the reason. Tab Token lists the tokens in
 * the bag and whether this sect accepts them.
 * <p>
 * Defaults of the ops (a module may replace them with {@link GabungLayar#daftarAksi}; 41B adds the Tanda Calon and the
 * gate guard's call): {@code ajukan:PINTU} records the candidacy (status CALON for 3 days, the next test day,
 * advancement {@code gabung_lamar}); {@code batal} withdraws it.
 */
public final class LembarLamaran {

	/** The sect each player's Lamaran screen is about (set by {@link #buka}). */
	private static final Map<UUID, String> SEKTE = new HashMap<>();

	static void init() {
		GabungLayar.daftar(Lamaran.LAYAR, LembarLamaran::bangun);
		GabungLayar.bawaan(Lamaran.LAYAR, Lamaran.OP_AJUKAN, (p, npcId, op) -> ajukan(p, Pintu.byId(LembarKunci.arg(op, 1), Pintu.GERBANG)));
		GabungLayar.bawaan(Lamaran.LAYAR, Lamaran.OP_BATAL, (p, npcId, op) -> batal(p));
	}

	/**
	 * Opens the Lamaran of {@code sekte} for {@code player} from NPC {@code npcId} (-1: the directory, a command). The
	 * entry of {@code masuk/Lamaran.buka} (41B calls this).
	 */
	public static void buka(ServerPlayer player, String sekte, int npcId) {
		if (sekte == null || sekte.isEmpty() || SekteApi.diplomasi().tier(sekte) <= 0) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.syarat.sekte_tak_dikenal", sekte == null ? "?" : sekte),
					LembarKunci.PESAN_BAHAYA);
			return;
		}
		SEKTE.put(player.getUUID(), sekte);
		GabungLayar.bukaLayar(player, Lamaran.LAYAR, npcId, null);
	}

	/** The sect of the player's Lamaran screen: the one opened last, else the running application's. */
	static @Nullable String sekte(ServerPlayer player) {
		String s = SEKTE.get(player.getUUID());
		if (s != null) {
			return s;
		}
		Keanggotaan k = GabungApi.anggota(player);
		return k.status == StatusAnggota.CALON && !k.sekte.isEmpty() ? k.sekte : null;
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		String sekte = sekte(player);
		if (sekte == null || SekteApi.diplomasi().tier(sekte) <= 0) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		KelasSekte kelas = GabungApi.kelasSekte(sekte);
		CompoundTag tag = GabungLayar.kepala(player, sekte,
				Component.translatable("kultivator.gabung.ui.lamaran.judul", GabungLayar.namaSekte(sekte), kelas.nama()));
		Keanggotaan k = GabungApi.anggota(player);
		long hari = GabungApi.hari(server);
		GerbangPlan.Entry e = GabungApi.entry(server, sekte);
		long ujian = e == null ? -1L : kelas == KelasSekte.KECIL ? hari : HariUjian.berikut(e.index(), hari, GabungConfig.get().hariUjian);
		tag.putLong(GabungLembar.HARI_UJIAN, ujian);
		if (ujian >= 0L) {
			tag.putInt(LembarKunci.SISA_HARI, (int) Math.max(0L, ujian - hari));
		}
		Long jeda = k.jedaLamar.get(sekte);
		if (jeda != null && jeda > hari) {
			tag.putLong(Lamaran.JEDA, jeda);
		}
		if (k.status == StatusAnggota.CALON && !k.sekte.isEmpty()) {
			CompoundTag calon = new CompoundTag();
			LembarKunci.teks(calon, GabungLembar.NAMA_SEKTE, GabungLayar.namaSekte(k.sekte));
			calon.putString(GabungLembar.PINTU, k.pintu.id());
			calon.putLong(Lamaran.SAMPAI, k.calonSampai);
			calon.putBoolean(Lamaran.SINI, sekte.equals(k.sekte));
			tag.put(Lamaran.CALON, calon);
		}

		ListTag pintuList = new ListTag();
		CompoundTag kunci = new CompoundTag();
		Pintu terbaik = null;
		for (Pintu pintu : Pintu.values()) {
			List<SyaratMasuk.Baris> baris = SyaratMasuk.periksa(player, sekte, pintu);
			Component tutup = kunciPintu(player, sekte, kelas, pintu);
			boolean bisa = tutup == null && SyaratMasuk.lolos(baris);
			CompoundTag p = new CompoundTag();
			p.putString(LembarKunci.ID, pintu.id());
			LembarKunci.teks(p, LembarKunci.NAMA, pintu.nama());
			p.putBoolean(LembarKunci.BISA, bisa);
			Component alasan = tutup != null ? tutup : gagalPertama(baris);
			if (!bisa && alasan != null) {
				LembarKunci.teks(p, LembarKunci.ALASAN, alasan);
			}
			p.put(LembarKunci.TAHAP, tahap(pintu));
			p.putInt(GabungLembar.PANGKAT, pintu.pangkatMaks().ordinal());
			ListTag syarat = new ListTag();
			for (SyaratMasuk.Baris b : baris) {
				if ("aktif".equals(b.kunci()) && b.ok()) {
					continue;
				}
				syarat.add(GabungLayar.syarat(b.ok(), b.teks(), null, b.ok() ? null : saran(b.kunci(), pintu)));
			}
			p.put(LembarKunci.SYARAT, syarat);
			pintuList.add(p);
			if (tutup != null) {
				LembarKunci.teks(kunci, pintu.id(), tutup);
			}
			if (bisa && terbaik == null) {
				terbaik = pintu;
			}
		}
		tag.put(Lamaran.PINTU_DAFTAR, pintuList);
		if (!kunci.isEmpty()) {
			tag.put(LembarKunci.TAB_KUNCI, kunci);
		}
		tag.putString(LembarKunci.TAB, (terbaik != null ? terbaik : k.status == StatusAnggota.CALON && sekte.equals(k.sekte) ? k.pintu
				: Pintu.GERBANG).id());
		tag.put(Lamaran.TOKEN, token(player, sekte, kelas));
		return tag;
	}

	// ================================================================== parts

	/** The stage ids a door runs (41.5.2). */
	private static ListTag tahap(Pintu pintu) {
		ListTag list = new ListTag();
		switch (pintu) {
			case PELAYAN -> list.add(StringTag.valueOf(Lamaran.TAHAP_WAWANCARA));
			case TAMU -> list.add(StringTag.valueOf(Lamaran.TAHAP_TAMU));
			default -> {
				if (pintu.jalani(1)) {
					list.add(StringTag.valueOf(Lamaran.TAHAP_TANGGA));
				}
				if (pintu.jalani(2)) {
					list.add(StringTag.valueOf(Lamaran.TAHAP_CORAK));
				}
				if (pintu.jalani(3)) {
					list.add(StringTag.valueOf(Lamaran.TAHAP_HATI));
				}
			}
		}
		return list;
	}

	/** Why a door cannot be used at all (locked tab), or null: nothing in the bag for Token/Undangan, guests off. */
	private static @Nullable Component kunciPintu(ServerPlayer player, String sekte, KelasSekte kelas, Pintu pintu) {
		return switch (pintu) {
			case TOKEN -> cari(player, GabungItems.TOKEN_NAIK, sekte, kelas) == null
					? Component.translatable("kultivator.gabung.ui.lamaran.kunci.token") : null;
			case UNDANGAN -> cari(player, GabungItems.SURAT_UNDANGAN, sekte, null) == null
					? Component.translatable("kultivator.gabung.ui.lamaran.kunci.undangan") : null;
			case TAMU -> GabungConfig.get().bolehTamu ? null : Component.translatable("kultivator.gabung.syarat.tamu_mati");
			default -> null;
		};
	}

	/** A valid stack of {@code item} in the bag for {@code sekte} (general tokens: class 3 or lower), or null. */
	private static @Nullable ItemStack cari(ServerPlayer player, Item item, String sekte, @Nullable KelasSekte kelas) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty() && stack.is(item) && sah(stack, sekte, kelas)) {
				return stack;
			}
		}
		return null;
	}

	/** A token (or letter) of this sect, or a general one when {@code kelas} allows it (null kelas: any general one). */
	private static boolean sah(ItemStack stack, String sekte, @Nullable KelasSekte kelas) {
		GabungItems.SekteToken milik = stack.get(GabungItems.SEKTE_TOKEN);
		if (milik != null && !milik.sekte().isEmpty()) {
			return milik.sekte().equals(sekte);
		}
		return kelas == null || kelas.nomor() <= KelasSekte.BESAR.nomor();
	}

	/** Tab Token: every Token Naik Sekte in the bag, with whether this sect accepts it. */
	private static ListTag token(ServerPlayer player, String sekte, KelasSekte kelas) {
		ListTag list = new ListTag();
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && list.size() < 9; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty() || !stack.is(GabungItems.TOKEN_NAIK)) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			LembarKunci.barang(t, stack, player.level().registryAccess());
			boolean ok = sah(stack, sekte, kelas);
			t.putBoolean(Lamaran.SAH, ok);
			if (!ok) {
				GabungItems.SekteToken milik = stack.get(GabungItems.SEKTE_TOKEN);
				LembarKunci.teks(t, LembarKunci.ALASAN, milik != null && !milik.sekte().isEmpty()
						? Component.translatable("kultivator.gabung.ui.lamaran.token.lain", milik.namaSekte())
						: Component.translatable("kultivator.gabung.ui.lamaran.token.kelas"));
			}
			list.add(t);
		}
		return list;
	}

	private static @Nullable Component gagalPertama(List<SyaratMasuk.Baris> baris) {
		for (SyaratMasuk.Baris b : baris) {
			if (!b.ok()) {
				return b.teks();
			}
		}
		return null;
	}

	/** Advice next to a failed line (41.10.2 "→ pakai Token"), or null. */
	private static @Nullable Component saran(String kunci, Pintu pintu) {
		String k = switch (kunci) {
			case "usia_tulang" -> "usia_tulang";
			case "akar" -> "akar";
			case "ranah", "ranah_akhir", "ranah_agung", "ranah_abadi" -> pintu == Pintu.PELAYAN ? null : "ranah";
			case "standing" -> "standing";
			case "infami_lurus", "infami_netral" -> "infami_turun";
			case "infami_iblis" -> "infami_naik";
			case "kejahatan" -> "kejahatan";
			case "jeda", "riwayat" -> "tunggu";
			case "lamaran_lain" -> "batal";
			case "tamu_syarat" -> "tamu";
			default -> null;
		};
		return k == null ? null : Component.translatable("kultivator.gabung.ui.saran." + k);
	}

	// ================================================================== defaults of the ops

	/** {@code ajukan:PINTU}: checks every line again and records the candidacy (41.5.3). */
	static void ajukan(ServerPlayer player, Pintu pintu) {
		String sekte = sekte(player);
		if (sekte == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		KelasSekte kelas = GabungApi.kelasSekte(sekte);
		Component tutup = kunciPintu(player, sekte, kelas, pintu);
		List<SyaratMasuk.Baris> baris = SyaratMasuk.periksa(player, sekte, pintu);
		Component gagal = tutup != null ? tutup : gagalPertama(baris);
		if (gagal != null || !SyaratMasuk.lolos(baris)) {
			GabungLayar.pesan(player, gagal != null ? gagal : Component.translatable("kultivator.gabung.belum_tersedia"), LembarKunci.PESAN_BAHAYA);
			return;
		}
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		long hari = GabungApi.hari(server);
		GerbangPlan.Entry e = GabungApi.entry(server, sekte);
		k.sekte = sekte;
		k.status = StatusAnggota.CALON;
		k.pintu = pintu;
		k.hariUjian = e == null || kelas == KelasSekte.KECIL ? hari : HariUjian.berikut(e.index(), hari, GabungConfig.get().hariUjian);
		k.calonSampai = hari + dev.kultivator.sekte.gabung.masuk.Lamaran.CALON_HARI;
		k.catat(hari, "kultivator.gabung.ui.riwayat.lamar", sekte, pintu.id());
		data.setDirty();
		GabungApi.kenal(player, sekte);
		Advancements.grant(player, "sekte/gabung_lamar");
		long sisa = Math.max(0L, k.hariUjian - hari);
		GabungLayar.pesan(player, sisa == 0L
				? Component.translatable("kultivator.gabung.ui.lamaran.diajukan.hari_ini", GabungLayar.namaSekte(sekte), pintu.nama())
				: Component.translatable("kultivator.gabung.ui.lamaran.diajukan", GabungLayar.namaSekte(sekte), pintu.nama(), k.hariUjian, sisa),
				LembarKunci.PESAN_OK);
	}

	/** {@code batal}: withdraws the running application (no pause: nothing was tested yet). */
	static void batal(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || k.status != StatusAnggota.CALON) {
			GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.lamaran.tak_ada"), LembarKunci.PESAN_PERINGATAN);
			return;
		}
		String sekte = k.sekte;
		k.status = StatusAnggota.BEBAS;
		k.sekte = "";
		k.calonSampai = -1L;
		k.hariUjian = -1L;
		k.catat(GabungApi.hari(server), "kultivator.gabung.ui.riwayat.batal", sekte);
		data.setDirty();
		GabungLayar.pesan(player, Component.translatable("kultivator.gabung.ui.lamaran.dibatalkan", GabungLayar.namaSekte(sekte)),
				LembarKunci.PESAN_PERINGATAN);
	}

	private LembarLamaran() {
	}
}
