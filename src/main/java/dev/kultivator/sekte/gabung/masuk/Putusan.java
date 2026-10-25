package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.RootGrade;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.api.SyaratMasuk;
import dev.kultivator.sekte.gabung.ui.GabungLayar;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The verdict of DESIGN 41.5.5. {@code Skor = 35 S1 + 35 S2 + 20 S3 + 5 x root + 5 x min(2, excess realm) - 10 x (Bone Age
 * over the limit)}; a stage the door skips counts 0.8. Below 35 refused (the 3-day pause; the servant door is offered
 * when the class is at most 3), 35-54 Murid Tercatat, 55-79 Murid Luar, 80 or more Murid Luar with 40 PK, or a <b>Calon
 * Istimewa</b> (root HIGH or better, or a perfect style test): Murid Luar with {@code 100 x kf} PK, two or three elders
 * vie to teach them (41.5.7) and the place hears of the talent. The door caps the rank ({@code Pintu.pangkatMaks}); the
 * servant door always gives Murid Pelayan, the guest trial Tetua Tamu; a hall owner of level 5 or more starts one rank
 * higher (R5, at most Murid Dalam).
 * <p>
 * Shown as the title card (2 s), the {@code gabung_putusan} sheet (built here; drawn by 41U-2) and chat lines. An accepted
 * candidate stays CALON with a "passed" note ({@code Keanggotaan.bagian("masuk")}) until the oath; the oath
 * ({@link UpacaraSumpah}) makes the membership through {@code GabungApi.terima}.
 */
public final class Putusan {

	/** Score bands (41.5.5). */
	public static final int TOLAK = 35;
	public static final int LUAR = 55;
	public static final int ISTIMEWA = 80;
	/** PK given at 80+: Luar 40, Calon Istimewa 100 x class factor. */
	public static final int BONUS_LUAR = 40;
	public static final int BONUS_ISTIMEWA = 100;

	/** Keys of the passed note in {@code Keanggotaan.bagian("masuk")}. */
	static final String B = "masuk";
	static final String L_SEKTE = "lulus_sekte", L_PANGKAT = "lulus_pangkat", L_PINTU = "lulus_pintu", L_BONUS = "lulus_bonus",
			L_ISTIMEWA = "lulus_istimewa", L_SIFAT = "sifat", L_SKOR = "lulus_skor";

	/** One term of the score formula as the card lists it. */
	public record Rincian(Component teks, int nilai) {
	}

	/** The verdict: total score, rank given (null = refused), Calon Istimewa, PK bonus, terms and the servant offer. */
	public record Hasil(int skor, @Nullable Pangkat pangkat, boolean istimewa, int bonus, List<Rincian> rincian, boolean tawarPelayan) {

		public boolean diterima() {
			return pangkat != null;
		}
	}

	static void init() {
		TempatLayar.daftar(GabungLembar.Putusan.LAYAR, Putusan::aksi, Putusan::bangun);
		// the membership card (41U-1) lists the candidate traits the interview revealed
		GabungLayar.addPengisi(GabungLembar.Kartu.LAYAR, (player, npcId, lembar) -> {
			CompoundTag kartu = lembar.getCompoundOrEmpty(GabungLembar.Kartu.KARTU);
			if (kartu.contains(GabungLembar.Kartu.SIFAT)) {
				return;
			}
			ListTag rows = new ListTag();
			for (Component t : sifatTeks(GabungApi.anggota(player))) {
				CompoundTag row = new CompoundTag();
				LembarKunci.teks(row, LembarKunci.TEKS, t);
				rows.add(row);
			}
			if (!rows.isEmpty()) {
				kartu.put(GabungLembar.Kartu.SIFAT, rows);
				lembar.put(GabungLembar.Kartu.KARTU, kartu);
			}
		});
	}

	// ================================================================== the score

	static Hasil hitung(ServerPlayer player, HariUjian.Sesi s) {
		List<Rincian> terms = new ArrayList<>();
		if (s.pintu == Pintu.PELAYAN) {
			terms.add(new Rincian(Masuk.t("putusan.r.pelayan"), 0));
			return new Hasil(0, Pangkat.PELAYAN, false, 0, terms, false);
		}
		if (s.pintu == Pintu.TAMU) {
			boolean lulus = s.s3 >= 0.5D;
			terms.add(new Rincian(Masuk.t("putusan.r.tamu"), lulus ? 100 : 0));
			return new Hasil(lulus ? 100 : 0, lulus ? Pangkat.TAMU : null, false, 0, terms, false);
		}
		CultivationData cd = Cultivators.get(player);
		RootGrade akar = cd.rootGrade();
		KelasSekte kelas = KelasSekte.byNomor(s.kelas);
		int lebih = Math.max(0, cd.realm().ordinal() - SyaratMasuk.ranahMin(kelas, s.pintu).ordinal());
		int batas = SyaratMasuk.batasUsiaTulang(kelas, cd.realm());
		boolean tua = GabungConfig.get().usiaTulang && !s.pintu.tanpaUsiaTulang() && batas >= 0 && SyaratMasuk.usiaTulang(player) > batas;
		double total = 0.0D;
		int[] bobot = {0, 35, 35, 20};
		for (int t = 1; t <= 3; t++) {
			double v = s.skor(t) * bobot[t];
			total += v;
			terms.add(new Rincian(Masuk.t(s.dilewati[t] ? "putusan.r.tahap_lewat" : "putusan.r.tahap", HariUjian.namaTahap(t, s.corak),
					(int) Math.round(s.skor(t) * 100.0D)), (int) Math.round(v)));
		}
		total += 5 * akar.ordinal();
		terms.add(new Rincian(Masuk.t("putusan.r.akar", akar.displayName()), 5 * akar.ordinal()));
		total += 5 * Math.min(2, lebih);
		terms.add(new Rincian(Masuk.t("putusan.r.ranah", Math.min(2, lebih)), 5 * Math.min(2, lebih)));
		if (tua) {
			total -= 10;
			terms.add(new Rincian(Masuk.t("putusan.r.tua", SyaratMasuk.usiaTulang(player), batas), -10));
		}
		int skor = (int) Math.round(Math.max(0.0D, Math.min(100.0D, total)));
		if (skor < TOLAK) {
			return new Hasil(skor, null, false, 0, terms, kelas.nomor() <= 3);
		}
		Pangkat p = skor < LUAR ? Pangkat.TERCATAT : Pangkat.LUAR;
		boolean istimewa = skor >= ISTIMEWA && (akar.ordinal() >= RootGrade.HIGH.ordinal() || s.s2 >= 1.0D);
		int bonus = 0;
		if (skor >= ISTIMEWA) {
			bonus = istimewa ? (int) Math.round(BONUS_ISTIMEWA * kelas.kf()) : BONUS_LUAR;
		}
		if (Halls.level(player.getUUID()) >= 5 && p.ordinal() < Pangkat.DALAM.ordinal()) {
			p = Pangkat.byOrdinal(p.ordinal() + 1);
			terms.add(new Rincian(Masuk.t("putusan.r.aula"), 0));
		}
		Pangkat cap = s.pintu.pangkatMaks();
		if (s.pintu != Pintu.UNDANGAN && p.ordinal() > cap.ordinal() && !(Halls.level(player.getUUID()) >= 5)) {
			p = cap;
		}
		if (p.ordinal() > Pangkat.DALAM.ordinal()) {
			p = Pangkat.DALAM;
		}
		return new Hasil(skor, p, istimewa, bonus, terms, false);
	}

	// ================================================================== announcing

	static void umumkan(ServerPlayer player, HariUjian.Sesi s, Hasil h) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		CompoundTag bag = k.bagian(B);
		bag.putInt("putusan_skor", h.skor());
		bag.putBoolean("putusan_terima", h.diterima());
		bag.putString("putusan_sekte", s.sekte);
		bag.putInt("putusan_pangkat", h.pangkat() == null ? -1 : h.pangkat().ordinal());
		bag.putBoolean("putusan_istimewa", h.istimewa());
		bag.putInt("putusan_bonus", h.bonus());
		bag.putBoolean("putusan_pelayan", h.tawarPelayan());
		bag.putString("putusan_pintu", s.pintu.id());
		ListTag stages = new ListTag();
		for (int t = 1; t <= 3; t++) {
			CompoundTag st = new CompoundTag();
			st.putInt("t", t);
			st.putDouble("s", s.skor(t));
			st.putBoolean("l", s.dilewati[t]);
			st.putString("c", s.corak.id());
			stages.add(st);
		}
		bag.put("putusan_tahap", stages);
		ListTag terms = new ListTag();
		for (Rincian r : h.rincian()) {
			CompoundTag rt = new CompoundTag();
			LembarKunci.teks(rt, LembarKunci.TEKS, r.teks());
			rt.putInt(LembarKunci.NILAI, r.nilai());
			terms.add(rt);
		}
		bag.put("putusan_rincian", terms);
		ListTag traits = new ListTag();
		for (String t : s.sifat) {
			traits.add(StringTag.valueOf(t));
		}
		bag.put(L_SIFAT, traits);
		if (s.uji && s.hanyaTahap != 0) {
			data.setDirty();
			return;
		}
		Component sekteNama = Masuk.nama(s.sekte);
		KultivatorNpc juru = UjianHati.tubuhTetua(player, s);
		if (juru == null) {
			juru = HariUjian.penjaga(player.level(), s.sekte, false);
		}
		if (h.diterima()) {
			bag.putString(L_SEKTE, s.sekte);
			bag.putInt(L_PANGKAT, h.pangkat().ordinal());
			bag.putString(L_PINTU, s.pintu.id());
			bag.putInt(L_BONUS, h.bonus());
			bag.putBoolean(L_ISTIMEWA, h.istimewa());
			bag.putInt(L_SKOR, h.skor());
			if (!s.uji) {
				k.calonSampai = Math.max(k.calonSampai, GabungApi.hari(server) + 3L);
			} else {
				k.sekte = s.sekte;
				k.status = StatusAnggota.CALON;
				k.pintu = s.pintu;
				k.calonSampai = GabungApi.hari(server) + 3L;
			}
			Msg.title(player, Masuk.t("putusan.judul_terima").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					Masuk.t("putusan.sub_terima", h.pangkat().nama(), sekteNama), 5, 40, 10);
			Msg.chat(player, Masuk.t(h.istimewa() ? "putusan.chat_istimewa" : "putusan.chat_terima", sekteNama, h.pangkat().nama(), h.skor())
					.withStyle(ChatFormatting.GREEN));
			if (juru != null) {
				Masuk.ucap(juru, h.istimewa() ? "gabung.istimewa" : "gabung.diterima", "elder.welcome",
						Masuk.t(h.istimewa() ? "ucap.istimewa" : "ucap.diterima"));
			}
			Advancements.grant(player, "sekte/gabung_diterima");
			if (h.istimewa()) {
				Advancements.grant(player, "sekte/gabung_istimewa");
				CeritaApi.kabar(player.level(), Masuk.situs(player.level(), s.pusat), "gabung_bakat_baru", player.getName(), sekteNama);
			}
			Msg.chat(player, tombol(Masuk.t("putusan.ke_sumpah"), "/kultivasi gabung sumpah mulai", Masuk.t("putusan.ke_sumpah_hover")));
		} else {
			if (!s.uji) {
				Lamaran.tolak(player, s.sekte);
			}
			Msg.title(player, Masuk.t("putusan.judul_tolak").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD),
					Masuk.t("putusan.sub_tolak", sekteNama, h.skor()), 5, 40, 10);
			Msg.chat(player, Masuk.t("putusan.chat_tolak", sekteNama, h.skor(), GabungConfig.get().jedaLamarHari).withStyle(ChatFormatting.YELLOW));
			if (juru != null) {
				Masuk.ucap(juru, "gabung.ujian_gagal", "elder.welcome", Masuk.t("ucap.ujian_gagal"));
			}
			if (h.tawarPelayan()) {
				bag.putString("tawar_pelayan", s.sekte);
				Msg.chat(player, tombol(Masuk.t("putusan.tawar_pelayan"), "/kultivasi gabung lamar " + s.sekte + " pelayan",
						Masuk.t("putusan.tawar_pelayan_hover")));
			}
		}
		data.setDirty();
		CompoundTag sheet = bangun(player, -1);
		if (sheet != null) {
			TempatLayar.bukaDi(player, GabungLembar.Putusan.LAYAR, player.blockPosition(), sheet);
		}
	}

	static MutableComponent tombol(Component teks, String perintah, Component hover) {
		return Component.literal("[").append(teks).append("]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand(perintah)).withHoverEvent(new HoverEvent.ShowText(hover)));
	}

	// ================================================================== the gabung_putusan sheet

	/** Builds the verdict card from the last verdict note (null when there is none). */
	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		Keanggotaan k = GabungApi.anggota(player);
		CompoundTag bag = k.bagian(B);
		String sekte = bag.getStringOr("putusan_sekte", "");
		if (sekte.isEmpty()) {
			return null;
		}
		CompoundTag tag = new CompoundTag();
		kepala(tag, player, sekte);
		boolean terima = bag.getBooleanOr("putusan_terima", false);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Masuk.t(terima ? "putusan.judul_terima" : "putusan.judul_tolak"));
		tag.putBoolean(GabungLembar.Putusan.DITERIMA, terima);
		int pangkat = bag.getIntOr("putusan_pangkat", -1);
		if (terima && pangkat >= 0) {
			tag.putInt(GabungLembar.PANGKAT, pangkat);
		}
		tag.putInt(LembarKunci.SKOR, bag.getIntOr("putusan_skor", 0));
		tag.putBoolean(GabungLembar.Putusan.ISTIMEWA, bag.getBooleanOr("putusan_istimewa", false));
		tag.putString(GabungLembar.PINTU, bag.getStringOr("putusan_pintu", Pintu.GERBANG.id()));
		ListTag stages = new ListTag();
		ListTag src = bag.getListOrEmpty("putusan_tahap");
		for (int i = 0; i < src.size(); i++) {
			CompoundTag st = src.getCompoundOrEmpty(i);
			CompoundTag row = new CompoundTag();
			int t = st.getIntOr("t", 1);
			LembarKunci.teks(row, LembarKunci.NAMA, HariUjian.namaTahap(t, dev.kultivator.sekte.gabung.api.Corak.byId(st.getStringOr("c", ""),
					GabungApi.corak(sekte))));
			row.putFloat(LembarKunci.SKOR, (float) st.getDoubleOr("s", 0.0D));
			row.putBoolean(GabungLembar.Putusan.DILEWATI, st.getBooleanOr("l", false));
			stages.add(row);
		}
		tag.put(GabungLembar.Putusan.TAHAP, stages);
		tag.put(GabungLembar.Putusan.RINCIAN, bag.getListOrEmpty("putusan_rincian").copy());
		tag.putInt(GabungLembar.Putusan.BONUS, bag.getIntOr("putusan_bonus", 0));
		boolean pelayan = bag.getBooleanOr("putusan_pelayan", false);
		tag.putBoolean(GabungLembar.Putusan.PELAYAN, pelayan && !terima);
		LembarKunci.teks(tag, GabungLembar.SARAN, terima ? Masuk.t("putusan.saran_sumpah")
				: pelayan ? Masuk.t("putusan.saran_pelayan") : Masuk.t("putusan.saran_ulang", GabungConfig.get().jedaLamarHari));
		return tag;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String verb = LembarKunci.verb(op);
		switch (verb) {
			case GabungLembar.Putusan.OP_SUMPAH -> {
				TempatLayar.tutup(player);
				Component why = UpacaraSumpah.mulai(player);
				if (why != null) {
					Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
				}
			}
			case GabungLembar.Putusan.OP_PELAYAN -> {
				Keanggotaan k = GabungApi.anggota(player);
				String sekte = k.bagian(B).getStringOr("tawar_pelayan", "");
				TempatLayar.tutup(player);
				if (!sekte.isEmpty()) {
					Component why = Lamaran.ajukan(player, sekte, Pintu.PELAYAN);
					if (why != null) {
						Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
					}
				}
			}
			default -> {
			}
		}
	}

	/** The sect header of every 41 sheet (keys of {@code GabungLembar}). */
	static void kepala(CompoundTag tag, ServerPlayer player, String sekte) {
		DiplomasiService d = SekteApi.diplomasi();
		int aliran = d.aliran(sekte);
		tag.putString(LembarKunci.GAYA, GabungLayar.gaya(aliran));
		tag.putString(GabungLembar.SEKTE_ID, sekte);
		LembarKunci.teks(tag, GabungLembar.NAMA_SEKTE, Masuk.nama(sekte));
		tag.putInt(GabungLembar.WARNA, d.color(sekte));
		tag.putInt(GabungLembar.LAMBANG, d.emblem(sekte).pack());
		tag.putInt(GabungLembar.ALIRAN, aliran);
		tag.putInt(GabungLembar.KELAS, GabungApi.kelas(sekte));
		tag.putInt(GabungLembar.TINGKAT, Math.max(1, d.tier(sekte)));
		tag.putString(GabungLembar.CORAK, GabungApi.corak(sekte).id());
		Keanggotaan k = GabungApi.anggota(player);
		tag.putInt(GabungLembar.PANGKAT_ANGGOTA, k.anggotaDi(sekte) ? k.pangkat.ordinal() : -1);
		tag.putInt(LembarKunci.PK, k.anggotaDi(sekte) ? k.pk : 0);
		tag.putInt(GabungLembar.PK_TOTAL, (int) Math.min(Integer.MAX_VALUE, k.anggotaDi(sekte) ? k.pkTotal : 0L));
	}

	/** The candidate traits the interview revealed (lang keys {@code kultivator.gabung.masuk.sifat.<id>}), for the card. */
	public static List<String> sifat(Keanggotaan k) {
		List<String> out = new ArrayList<>();
		ListTag list = k.bagian(B).getListOrEmpty(L_SIFAT);
		for (int i = 0; i < list.size(); i++) {
			String s = list.getStringOr(i, "");
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}

	/** The traits as shown text. */
	public static List<Component> sifatTeks(Keanggotaan k) {
		List<Component> out = new ArrayList<>();
		for (String s : sifat(k)) {
			out.add(Masuk.t("sifat." + s));
		}
		return out;
	}

	/** True when the player passed the verdict of {@code sekte} and has not sworn yet. */
	public static boolean lulus(Keanggotaan k, String sekte) {
		return k.status == StatusAnggota.CALON && sekte != null && sekte.equals(k.bagian(B).getStringOr(L_SEKTE, ""));
	}

	/** Forgets the passed note (after the oath or when the candidacy lapses). */
	static void lupakan(Keanggotaan k) {
		CompoundTag bag = k.bagian(B);
		bag.remove(L_SEKTE);
		bag.remove(L_PANGKAT);
		bag.remove(L_PINTU);
		bag.remove(L_BONUS);
		bag.remove(L_ISTIMEWA);
		bag.remove(L_SKOR);
	}

	private Putusan() {
	}
}
