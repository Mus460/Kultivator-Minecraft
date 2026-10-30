package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.Syarat;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.Promosi;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.ui.GabungLayar;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The story conditions and effects of module 41B (38.S engine, {@code Syarat}/{@code Akibat}), used by the data dialogs
 * {@code gabung_gerbang} (the gate guard on a test day), {@code gabung_wawancara_*} (the interview) and
 * {@code gabung_ujian_tamu} (the guest trial), and open to quest chains:
 * <ul>
 * <li>conditions {@code gabung.soal <id>}, {@code gabung.wawancara}, {@code gabung.calon_sini}, {@code gabung.calon_nanti},
 * {@code gabung.lulus_sini}, {@code gabung.ujian_jalan}, {@code gabung.tamu_jalan};</li>
 * <li>effects {@code gabung.mulai_ujian}, {@code gabung.sumpah}, {@code gabung.jawab <kesan> [sifat]},
 * {@code gabung.wawancara}, {@code gabung.tamu duel|karya}, {@code gabung.undangan <sekte|terdekat>},
 * {@code gabung.kenal <sekte|terdekat>}, {@code gabung.dengar <sekte|terdekat>};</li>
 * <li>the promotion door of the sect's elders ({@code gabung_kenaikan}): condition {@code gabung.naik_siap [tetua]} (a member of
 * the sect the body serves whose next rank asks for nothing but its trial or is all met; argument {@code murid} or {@code tetua}
 * picks the branch, none means either), effects {@code gabung.naik [tetua]}
 * ({@code Promosi.ajukan}: starts the trial or the ceremony) and {@code gabung.pohon} (opens the rank tree).</li>
 * </ul>
 * "Sini" means the sect the speaking body serves ({@link Perekrut#sekteDari}). {@code gabung.beri_token} is 41W's
 * ({@code CeritaGabung}); {@link TokenNaik#beriToken} is the full form it may call.
 */
final class MasukCerita {

	static void init() {
		Syarat.daftar("gabung.soal", (p, a) -> !a.isEmpty() && UjianHati.soalIni(p, a.getFirst()));
		Syarat.daftar("gabung.wawancara", (p, a) -> UjianHati.pembuka(p));
		Syarat.daftar("gabung.ujian_jalan", (p, a) -> HariUjian.sedangUjian(p));
		Syarat.daftar("gabung.tamu_jalan", (p, a) -> {
			HariUjian.Sesi s = HariUjian.sesi(p);
			return s != null && !s.selesai && s.tahap == HariUjian.TAHAP_TAMU && !s.data.getBooleanOr(UjianHati.DUEL, false);
		});
		Syarat.daftar("gabung.calon_sini", (p, a) -> {
			String sekte = sini(p);
			Keanggotaan k = GabungApi.anggota(p);
			return sekte != null && k.status == StatusAnggota.CALON && sekte.equals(k.sekte) && !Putusan.lulus(k, sekte)
					&& !HariUjian.sedangUjian(p) && (tanpaHari(k) || HariUjian.bukaSekarang(p.level().getServer(), sekte));
		});
		Syarat.daftar("gabung.calon_nanti", (p, a) -> {
			String sekte = sini(p);
			Keanggotaan k = GabungApi.anggota(p);
			return sekte != null && k.status == StatusAnggota.CALON && sekte.equals(k.sekte) && !Putusan.lulus(k, sekte)
					&& !tanpaHari(k) && !HariUjian.bukaSekarang(p.level().getServer(), sekte);
		});
		Syarat.daftar("gabung.naik_siap", (p, a) -> {
			String sekte = sini(p);
			if (sekte == null || !sekte.equals(GabungApi.sekte(p.getUUID()))) {
				return false;
			}
			String jalan = a.isEmpty() ? "" : a.getFirst();
			return "tetua".equals(jalan) ? Promosi.siap(p, true) : "murid".equals(jalan) ? Promosi.siap(p, false)
					: Promosi.siap(p, false) || Promosi.siap(p, true);
		});
		Akibat.daftar("gabung.naik", (p, a) -> {
			TempatDialog.akhiri(p);
			Promosi.ajukan(p, !a.isEmpty() && "tetua".equals(a.getFirst()));
		});
		Akibat.daftar("gabung.pohon", (p, a) -> {
			TempatDialog.akhiri(p);
			GabungLayar.bukaLayar(p, GabungLembar.PohonPangkat.LAYAR, -1, null);
		});
		Syarat.daftar("gabung.lulus_sini", (p, a) -> {
			String sekte = sini(p);
			return sekte != null && Putusan.lulus(GabungApi.anggota(p), sekte) && !UpacaraSumpah.sedang(p);
		});

		Akibat.daftar("gabung.mulai_ujian", (p, a) -> {
			Keanggotaan k = GabungApi.anggota(p);
			TempatDialog.akhiri(p);
			Component why = HariUjian.mulai(p, k.sekte, k.pintu);
			if (why != null) {
				Msg.chat(p, why.copy().withStyle(ChatFormatting.RED));
			}
		});
		Akibat.daftar("gabung.sumpah", (p, a) -> {
			TempatDialog.akhiri(p);
			Component why = UpacaraSumpah.mulai(p);
			if (why != null) {
				Msg.chat(p, why.copy().withStyle(ChatFormatting.RED));
			}
		});
		Akibat.daftar("gabung.aturan", (p, a) -> {
			KultivatorNpc npc = TempatDialog.npc(p);
			String sekte = sini(p);
			if (npc == null || sekte == null) {
				return;
			}
			int aliran = SekteApi.diplomasi().aliran(sekte);
			TempatDialog.akhiri(p);
			TempatDialog.buka(p, npc, "gabung_aturan_" + (aliran == 2 ? "iblis" : aliran == 1 ? "netral" : "lurus"));
		});
		Akibat.daftar("gabung.jawab", UjianHati::jawab);
		Akibat.daftar("gabung.wawancara", (p, a) -> UjianHati.lanjutkan(p));
		Akibat.daftar("gabung.tamu", UjianHati::tamu);
		Akibat.daftar("gabung.undangan", TokenNaik::beriUndangan);
		Akibat.daftar("gabung.kenal", (p, a) -> {
			String sekte = sekte(p, a);
			if (sekte != null && GabungApi.kenal(p, sekte)) {
				Msg.chat(p, Masuk.t("direktori.kenal", Masuk.nama(sekte)).withStyle(ChatFormatting.AQUA));
			}
		});
		Akibat.daftar("gabung.dengar", (p, a) -> {
			String sekte = sekte(p, a);
			if (sekte != null && Direktori.dengar(p, sekte)) {
				Msg.chat(p, Masuk.t("direktori.dengar", Masuk.nama(sekte)).withStyle(ChatFormatting.GRAY));
			}
		});
	}

	/** The servant and guest doors need no test day (41.5.2). */
	private static boolean tanpaHari(Keanggotaan k) {
		return k.pintu == Pintu.PELAYAN || k.pintu == Pintu.TAMU;
	}

	/** The sect the body the player talks to serves, or null. */
	private static @Nullable String sini(ServerPlayer player) {
		KultivatorNpc npc = TempatDialog.npc(player);
		return npc == null ? null : Perekrut.sekteDari(player.level().getServer(), npc);
	}

	private static @Nullable String sekte(ServerPlayer player, List<String> args) {
		if (args.isEmpty() || "terdekat".equals(args.getFirst())) {
			return SekteApi.diplomasi().nearest(player.blockPosition(), -1);
		}
		return SekteApi.diplomasi().tier(args.getFirst()) > 0 ? args.getFirst() : null;
	}

	private MasukCerita() {
	}
}
