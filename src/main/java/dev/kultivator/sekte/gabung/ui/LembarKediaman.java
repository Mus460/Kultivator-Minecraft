package dev.kultivator.sekte.gabung.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.hidup.Kediaman;
import dev.kultivator.sekte.gabung.hidup.KolamRoh;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Cave dwellings and spirit pools ({@code gabung_kediaman}, DESIGN 41.6.6 and 41.10.7, keys {@link GabungLembar.Kediaman}),
 * filled from 41C-K's {@code hidup/Kediaman} and {@code hidup/KolamRoh}.
 * <ul>
 * <li>Tab Gua: the rented dwelling ({@code hunian}: name, paid-to day, meditation factor, Jimat Pulang, the nine-slot chest,
 * the rent of one more period); absent while the member sleeps in the dormitory ({@link #BILIK} then says so).</li>
 * <li>Tab Pilih: every dwelling of the sect with its rank, rent, factor, who lives there and why it is closed.</li>
 * <li>Tab Kolam: the meditation seats of the compound the player stands in, taken or free, the free Inti session.</li>
 * </ul>
 * Default ops: {@code perpanjang}, {@code pindah:TITIK}, {@code sewa:TITIK}, {@code lepas}, {@code sesi:TITIK},
 * {@code peti_ambil:SLOT}, {@code peti_taruh}, {@link #OP_JIMAT}; each calls the matching {@code Kediaman}/{@code KolamRoh}
 * method (those confirm in chat; the chest gets a short line here).
 */
public final class LembarKediaman {

	/** B the member has no cave, only the dormitory cell (tab Gua shows the dormitory card). */
	public static final String BILIK = "bilik";
	/** T why tab Kolam is empty (not standing in the own sect's compound). */
	public static final String KOLAM_KOSONG = "kolam_kosong";
	/** Op: make the dwelling's seat the Jimat Pulang target ({@code Kediaman.jadikanJimat}; inside the cave). */
	public static final String OP_JIMAT = "jimat";

	private static final String K = "kultivator.gabung.ui2.kediaman.";

	static void init() {
		String layar = GabungLembar.Kediaman.LAYAR;
		GabungLayar.daftar(layar, LembarKediaman::bangun);
		GabungLayar.bawaan(layar, GabungLembar.Kediaman.OP_PERPANJANG, (p, npcId, op) -> LembarPapan.hasil(p, Kediaman.perpanjang(p), null));
		GabungLayar.bawaan(layar, GabungLembar.Kediaman.OP_PINDAH, (p, npcId, op) -> LembarPapan.hasil(p, Kediaman.pindah(p, LembarKunci.arg(op, 1)), null));
		GabungLayar.bawaan(layar, GabungLembar.Kediaman.OP_SEWA, (p, npcId, op) -> LembarPapan.hasil(p, Kediaman.sewa(p, LembarKunci.arg(op, 1)), null));
		GabungLayar.bawaan(layar, GabungLembar.Kediaman.OP_LEPAS, (p, npcId, op) -> LembarPapan.hasil(p, Kediaman.lepas(p), null));
		GabungLayar.bawaan(layar, GabungLembar.Kediaman.OP_SESI, (p, npcId, op) -> LembarPapan.hasil(p, KolamRoh.pesan(p, LembarKunci.arg(op, 1)), null));
		GabungLayar.bawaan(layar, GabungLembar.Kediaman.OP_PETI_AMBIL, (p, npcId, op) -> LembarPapan.hasil(p,
				Kediaman.petiAmbil(p, LembarKunci.argInt(op, 1, -1)), Component.translatable(K + "peti.diambil")));
		GabungLayar.bawaan(layar, GabungLembar.Kediaman.OP_PETI_TARUH, (p, npcId, op) -> LembarPapan.hasil(p, Kediaman.petiTaruh(p),
				Component.translatable(K + "peti.ditaruh")));
		GabungLayar.bawaan(layar, OP_JIMAT, (p, npcId, op) -> LembarPapan.hasil(p, Kediaman.jadikanJimat(p), Component.translatable(K + "jimat.ok")));
	}

	static CompoundTag bangun(ServerPlayer player, int npcId) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		CompoundTag tag = GabungLayar.kepala(player, sekte, Component.translatable(K + "judul"));
		if (sekte == null) {
			return LembarPapan.bukanAnggota(tag);
		}
		HolderLookup.Provider reg = player.level().getServer().registryAccess();
		Kediaman.Hunian h = Kediaman.milik(player);
		if (h != null) {
			CompoundTag hun = new CompoundTag();
			hun.putString(LembarKunci.JENIS, h.jenis().id());
			LembarKunci.teks(hun, LembarKunci.NAMA, h.nama());
			hun.putInt(GabungLembar.Kediaman.NOMOR, h.nomor());
			hun.putLong(GabungLembar.Kediaman.SAMPAI, h.sewaSampai());
			hun.putFloat(GabungLembar.Kediaman.FAKTOR, (float) h.jenis().faktor());
			hun.putBoolean(GabungLembar.Kediaman.JIMAT, Kediaman.jimat(player));
			ListTag peti = new ListTag();
			List<ItemStack> isi = Kediaman.peti(player);
			for (int i = 0; i < isi.size() && i < Kediaman.PETI_SLOT; i++) {
				CompoundTag slot = new CompoundTag();
				slot.putInt(LembarKunci.SLOT, i);
				ItemStack s = isi.get(i);
				if (!s.isEmpty()) {
					LembarKunci.teks(slot, LembarKunci.NAMA, s.getHoverName());
					LembarKunci.barang(slot, s, reg);
					slot.putInt(LembarKunci.JUMLAH, s.getCount());
				}
				peti.add(slot);
			}
			hun.put(GabungLembar.Kediaman.PETI, peti);
			hun.putInt(GabungLembar.Kediaman.SEWA, Kediaman.hargaPerpanjang(player));
			hun.putInt(LembarKunci.SISA_HARI, (int) Math.max(0L, h.sewaSampai() - GabungApi.hari(player.level().getServer())));
			tag.put(GabungLembar.Kediaman.HUNIAN, hun);
		} else {
			tag.putBoolean(BILIK, true);
		}
		ListTag daftar = new ListTag();
		for (Kediaman.Pilihan p : Kediaman.daftar(player)) {
			if (daftar.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, p.titik());
			e.putString(LembarKunci.JENIS, p.jenis().id());
			LembarKunci.teks(e, LembarKunci.NAMA, Kediaman.nama(p.jenis(), p.titik()));
			e.putInt(GabungLembar.Kediaman.NOMOR, p.nomor());
			e.putInt(GabungLembar.PANGKAT, p.jenis().pangkatMin());
			e.putInt(LembarKunci.HARGA, p.harga());
			e.putFloat(GabungLembar.Kediaman.FAKTOR, (float) p.jenis().faktor());
			e.putString(LembarKunci.KEADAAN, p.keadaan());
			e.putBoolean(LembarKunci.ANDA, p.milikmu());
			if (p.pemakai() != null) {
				LembarKunci.teks(e, GabungLembar.Kediaman.PEMAKAI, p.pemakai());
			}
			if (p.alasan() != null) {
				LembarKunci.teks(e, LembarKunci.ALASAN, p.alasan());
			}
			daftar.add(e);
		}
		tag.put(LembarKunci.DAFTAR, daftar);
		ListTag kolam = new ListTag();
		boolean gratis = KolamRoh.gratis(player);
		long sisa = KolamRoh.sisa(player);
		for (KolamRoh.Kolam c : KolamRoh.daftar(player)) {
			if (kolam.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, c.id());
			LembarKunci.teks(e, LembarKunci.NAMA, Component.translatable(K + "kolam.nama", c.nomor()));
			e.putBoolean(GabungLembar.Kediaman.TERISI, c.terisi());
			if (c.pemakai() != null) {
				LembarKunci.teks(e, GabungLembar.Kediaman.PEMAKAI, c.pemakai());
			}
			e.putInt(LembarKunci.HARGA, Kediaman.KOLAM_HARGA);
			e.putBoolean(GabungLembar.Kediaman.GRATIS, gratis);
			e.putBoolean(LembarKunci.ANDA, c.milikmu());
			if (c.milikmu() && sisa > 0L) {
				e.putLong(LembarKunci.SISA_TICK, sisa);
			}
			e.putInt(LembarKunci.X, c.pos().getX());
			e.putInt(LembarKunci.Z, c.pos().getZ());
			kolam.add(e);
		}
		tag.put(GabungLembar.Kediaman.KOLAM, kolam);
		if (kolam.isEmpty()) {
			LembarKunci.teks(tag, KOLAM_KOSONG, Component.translatable(K + "kolam.jauh"));
		}
		return tag;
	}

	private LembarKediaman() {
	}
}
