package dev.kultivator.sekte.gabung.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.Aturan;
import dev.kultivator.sekte.gabung.BalaiHukum;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Hukum;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A hearing of the Balai Hukum ({@code gabung_hukum}, DESIGN 41.6.11 and 41.10.9, keys {@link Hukum}), from 41A's
 * {@code BalaiHukum.sidang} (the same record the chat card reads): the Discipline Elder as speaker, the record and the
 * offences heard, the witnesses, the sentence asked, "Bela diri" with its one Ujian Hati question, "Minta Shifu membela"
 * (bond &ge; 50), and the buy-off of a running confinement. An A2 case (a fellow member killed) only offers to accept the
 * expulsion or to flee ({@link #OP_LARI}: desertion). Without an open case the sheet shows the record and the
 * confinement only.
 * <p>
 * Default ops: {@code terima}, {@code bela} (asks the question, shown in the next sheet), {@code jawab:NO},
 * {@code minta_shifu}, {@code tebus}, {@link #OP_LARI}; each calls the matching {@code BalaiHukum} method (which speaks in
 * chat) and leaves a short line for the footer.
 */
public final class LembarHukum {

	/** B the case is an A2 trial (expulsion or flight). */
	public static final String A2 = "a2";
	/** T the rule of the case ("A3 · jangan mencuri dari Paviliun"). */
	public static final String PERKARA = "perkara";
	/** B a case is open (else only the record and the confinement are shown). */
	public static final String SIDANG = "sidang";
	/** B "Bela diri" is still allowed in this case. */
	public static final String BELA = "bela";
	/** Op: flee an A2 trial (desertion, 41.7.3). */
	public static final String OP_LARI = "lari";

	private static final String K = "kultivator.gabung.ui2.hukum.";
	private static final int CATATAN_MAKS = 12;

	/** The question asked by op {@code bela}, until answered (answers are lang keys; index = the {@code jawab} number). */
	private static final Map<UUID, BalaiHukum.Soal> SOAL = new HashMap<>();

	static void init() {
		GabungLayar.daftar(Hukum.LAYAR, LembarHukum::bangun);
		GabungLayar.bawaan(Hukum.LAYAR, Hukum.OP_TERIMA, (p, npcId, op) -> {
			SOAL.remove(p.getUUID());
			pesan(p, BalaiHukum.terimaHukuman(p), "terima");
		});
		GabungLayar.bawaan(Hukum.LAYAR, Hukum.OP_BELA, (p, npcId, op) -> {
			BalaiHukum.Soal soal = BalaiHukum.belaDiri(p);
			if (soal != null) {
				SOAL.put(p.getUUID(), soal);
			}
			pesan(p, soal != null, "bela");
		});
		GabungLayar.bawaan(Hukum.LAYAR, Hukum.OP_JAWAB, (p, npcId, op) -> {
			if (SOAL.remove(p.getUUID()) == null) {
				pesan(p, false, "jawab");
				return;
			}
			boolean benar = BalaiHukum.jawab(p, LembarKunci.argInt(op, 1, -1));
			GabungLayar.pesan(p, Component.translatable(K + (benar ? "jawab.benar" : "jawab.salah"), BalaiHukum.BELA_POIN),
					benar ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA);
		});
		GabungLayar.bawaan(Hukum.LAYAR, Hukum.OP_MINTA_SHIFU, (p, npcId, op) -> pesan(p, BalaiHukum.mintaShifu(p), "shifu"));
		GabungLayar.bawaan(Hukum.LAYAR, Hukum.OP_TEBUS, (p, npcId, op) -> pesan(p, BalaiHukum.tebus(p), "tebus"));
		GabungLayar.bawaan(Hukum.LAYAR, OP_LARI, (p, npcId, op) -> {
			SOAL.remove(p.getUUID());
			pesan(p, BalaiHukum.lari(p), "lari");
		});
	}

	static CompoundTag bangun(ServerPlayer player, int npcId) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		CompoundTag tag = GabungLayar.kepala(player, sekte, Component.translatable(K + "judul"));
		if (sekte == null) {
			return LembarPapan.bukanAnggota(tag);
		}
		MinecraftServer server = player.level().getServer();
		int aliran = Math.max(0, SekteApi.diplomasi().aliran(sekte));
		TokohSekte.Tokoh tetua = TokohSekte.jabatan(server, sekte, Jabatan.TETUA_HUKUM);
		CompoundTag npc = new CompoundTag();
		LembarKunci.teks(npc, LembarKunci.NAMA, tetua != null ? Component.literal(tetua.nama()) : BalaiHukum.namaTetuaHukum(server, sekte));
		LembarKunci.teks(npc, LembarKunci.GELAR, Component.translatable(K + "tetua_hukum"));
		if (tetua != null) {
			npc.putString(LembarKunci.TOKOH_ID, tetua.id());
		}
		npc.putInt(LembarKunci.POTRET, npcId);
		tag.put(LembarKunci.NPC, npc);
		tag.putInt(Hukum.PELANGGARAN, k.pelanggaran);
		tag.put(Hukum.CATATAN_HUKUM, catatan(k, aliran));
		tag.putInt(GabungLembar.IKATAN, k.ikatanShifu);
		BalaiHukum.Sidang s = BalaiHukum.sidang(player);
		tag.putBoolean(SIDANG, s != null);
		if (s != null) {
			tag.putBoolean(A2, s.a2());
			LembarKunci.teks(tag, Hukum.HUKUMAN, s.hukuman());
			Aturan a = Aturan.byId(s.aturan());
			if (a != null) {
				LembarKunci.teks(tag, PERKARA, Component.literal(a.id().toUpperCase(Locale.ROOT) + " · ").append(a.teks(aliran)));
			}
			ListTag saksi = new ListTag();
			for (String nama : s.saksi().split(",")) {
				if (!nama.isBlank() && saksi.size() < 6) {
					CompoundTag w = new CompoundTag();
					LembarKunci.teks(w, LembarKunci.NAMA, Component.literal(nama.trim()));
					w.putBoolean(GabungLembar.PEMAIN, server.getPlayerList().getPlayerByName(nama.trim()) != null);
					saksi.add(w);
				}
			}
			tag.put(Hukum.SAKSI, saksi);
			tag.putBoolean(BELA, s.bolehBela());
			tag.putBoolean(Hukum.SHIFU, s.bolehShifu());
			if (s.shifu() != null) {
				LembarKunci.teks(tag, GabungLembar.Kartu.SHIFU, s.shifu());
			}
			BalaiHukum.Soal soal = SOAL.get(player.getUUID());
			if (soal != null) {
				CompoundTag q = new CompoundTag();
				LembarKunci.teks(q, LembarKunci.TEKS, Component.translatable(soal.kunci()));
				ListTag pilihan = new ListTag();
				for (int i = 0; i < soal.jawaban().size(); i++) {
					CompoundTag c = new CompoundTag();
					c.putInt(LembarKunci.NO, i);
					LembarKunci.teks(c, LembarKunci.TEKS, Component.translatable(soal.jawaban().get(i)));
					pilihan.add(c);
				}
				q.put(Hukum.PILIHAN, pilihan);
				tag.put(Hukum.SOAL, q);
			}
		} else {
			SOAL.remove(player.getUUID());
		}
		int sisa = BalaiHukum.sisaDetik(player);
		if (sisa > 0) {
			CompoundTag t = new CompoundTag();
			t.putLong(LembarKunci.SISA_TICK, sisa * 20L);
			t.putInt(LembarKunci.HARGA, BalaiHukum.TEBUS_PK);
			int biaya = BalaiHukum.biayaTebus(sisa);
			t.putInt(LembarKunci.BIAYA, biaya);
			t.putBoolean(LembarKunci.BISA, k.pk >= biaya);
			if (k.pk < biaya) {
				LembarKunci.teks(t, LembarKunci.ALASAN, Component.translatable(K + "tebus.kurang", biaya, k.pk));
			}
			tag.put(Hukum.TEBUS, t);
		}
		return tag;
	}

	/** The offences on record, newest first (41A writes them as {@code kultivator.gabung.riwayat.langgar} lines). */
	private static ListTag catatan(Keanggotaan k, int aliran) {
		ListTag out = new ListTag();
		for (int i = k.riwayat.size() - 1; i >= 0 && out.size() < CATATAN_MAKS; i--) {
			Keanggotaan.Catatan c = k.riwayat.get(i);
			if (!"kultivator.gabung.riwayat.langgar".equals(c.kunci())) {
				continue;
			}
			String id = c.isian().isEmpty() ? "" : c.isian().getFirst().toLowerCase(Locale.ROOT);
			Aturan a = Aturan.byId(id);
			CompoundTag row = new CompoundTag();
			row.putLong(LembarKunci.HARI, c.hari());
			LembarKunci.teks(row, LembarKunci.TEKS, a != null ? Component.literal(a.id().toUpperCase(Locale.ROOT) + " · ").append(a.teks(aliran))
					: Component.translatable("kultivator.gabung.aturan." + id));
			int poin = a != null ? a.poin() : 0;
			if (c.isian().size() > 1) {
				try {
					poin = Integer.parseInt(c.isian().get(1));
				} catch (NumberFormatException ignored) {
					// keep the rule's own points
				}
			}
			row.putInt(GabungLembar.Kartu.POIN, poin);
			out.add(row);
		}
		return out;
	}

	private static void pesan(ServerPlayer player, boolean ok, String apa) {
		GabungLayar.pesan(player, Component.translatable(K + apa + (ok ? ".ok" : ".tidak")), ok ? LembarKunci.PESAN_OK : LembarKunci.PESAN_PERINGATAN);
	}

	private LembarHukum() {
	}
}
