package dev.kultivator.client.sekte.gabung.ui.layar;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.sekte.gabung.ui.IkonGabung;
import dev.kultivator.client.tempat.ui.kit.IkonTempat;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.client.tempat.ui.kit.Lembar;
import dev.kultivator.client.tempat.ui.kit.Lukis;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * The base of every 41 screen (DESIGN 41.10, kit 38.U read-only): the sect header strip (emblem in its ink colour,
 * class plaque and tier, alignment, corak, cloud palace, MEROSOT), the dark red wash of a demonic sect's stone panel
 * ({@code #6A1420}), tick/cross requirement rows, small bars and the confirmation of dangerous buttons (43.7: the one
 * {@code SegelKonfirmasi} of the Gulungan, held 1.5 s).
 * <p>
 * A 41 screen opened from the hub or a command is a personal anchor session on the server ({@code GabungLayar}), so it may
 * act: {@link #hanyaBaca} is false (the server still checks every op).
 */
public abstract class LayarGabungDasar extends LayarTempatDasar {

	/** Lang prefix of the 41U texts. */
	public static final String K = "kultivator.gabung.ui.";

	protected LayarGabungDasar(String layar, int npcId, CompoundTag lembar, Component judul, TempatWarna.Gaya gayaBawaan) {
		super(layar, npcId, lembar, judul, gayaBawaan);
	}

	public static MutableComponent t(String kunci, Object... args) {
		return Component.translatable(K + kunci, args);
	}

	@Override
	public boolean hanyaBaca() {
		return false;
	}

	@Override
	protected final void isi(Lukis l, int x0, int y0, int x1, int y1) {
		if (lembar.i(GabungLembar.ALIRAN, -1) == 2 && lembar.ada(GabungLembar.SEKTE_ID)) {
			// the demonic sects' stone panel washed in dark red (41.10)
			l.g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, Palette.argb(0x30, TempatWarna.IBLIS & 0xFFFFFF));
		}
		isiGabung(l, x0, y0, x1, y1);
	}

	/** The screen's content (after the alignment wash). */
	protected abstract void isiGabung(Lukis l, int x0, int y0, int x1, int y1);

	// ================================================================== sect header

	/**
	 * The sect strip of the sheet's header keys: emblem, class plaque + tier, corak, alignment, cloud palace, MEROSOT, and
	 * a thin rule in the sect's ink colour. Returns the y under it.
	 */
	protected int kepalaSekte(Lukis l, int x0, int y0, int x1) {
		if (!lembar.ada(GabungLembar.SEKTE_ID)) {
			return y0;
		}
		int y = y0 + 1;
		IkonGabung.lambang(l.g, font, lembar.i(GabungLembar.LAMBANG, 0), x0 + 6.5F, y + 6.5F, 6.5F);
		int x = x0 + 16;
		int kelas = lembar.i(GabungLembar.KELAS, 1);
		IkonGabung.gambar(l.g, IkonGabung.kelas(kelas), x, y, 12);
		x += 14;
		Component k = KelasSekte.byNomor(kelas).nama().copy().append(" · ").append(t("tier", lembar.i(GabungLembar.TINGKAT, 1)));
		l.teks(k, x, y + 2, Math.min(130, x1 - x), gaya.teks);
		x += Math.min(130, font.width(k)) + 6;
		String corak = lembar.s(GabungLembar.CORAK);
		if (!corak.isEmpty() && x + 60 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.corak(corak), x, y, 12);
			Component c = t("corak", Corak.byId(corak, Corak.PEDANG).nama());
			l.teks(c, x + 14, y + 2, 70, gaya.teksLemah);
			x += 14 + Math.min(70, font.width(c)) + 6;
		}
		int aliran = lembar.i(GabungLembar.ALIRAN, 0);
		if (x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.ALIRAN + Math.max(0, Math.min(2, aliran)), x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(t("aliran." + aliran));
			}
			x += 14;
		}
		if (lembar.b(GabungLembar.LANGIT) && x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.LANGIT, x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(t("langit"));
			}
			x += 14;
		}
		if (lembar.b(GabungLembar.MEROSOT) && x + 12 < x1) {
			IkonGabung.gambar(l.g, IkonGabung.MEROSOT, x, y, 12);
			if (l.di(x, y, x + 12, y + 12)) {
				l.tip(t("merosot"));
			}
		}
		int ink = Palette.opaque(lembar.i(GabungLembar.WARNA, 0xB0B0B0));
		l.g.fill(x0, y0 + 15, x1, y0 + 16, Palette.argb(0xB0, ink & 0xFFFFFF));
		return y0 + 19;
	}

	// ================================================================== rows and bars

	/**
	 * A requirement row: tick or cross, the line, then "punya" and the advice as a caption. Returns the height used
	 * (10, or 18 with a caption); draws nothing past {@code yMaks}.
	 */
	protected int barisSyarat(Lukis l, Lembar s, int x0, int y, int x1, int yMaks) {
		if (y + 9 > yMaks) {
			return 0;
		}
		boolean ok = s.b(LembarKunci.OK);
		IkonTempat.gambar(l.g, ok ? IkonTempat.CENTANG : IkonTempat.SILANG, x0, y, 9, 0xFFFFFFFF);
		l.teks(s.teks(LembarKunci.TEKS), x0 + 12, y + 1, x1 - x0 - 12, ok ? gaya.teks : gaya.teksLemah);
		Component punya = s.teksAtau(GabungLembar.PUNYA);
		Component saran = ok ? null : s.teksAtau(GabungLembar.SARAN);
		if ((punya != null || saran != null) && y + 18 <= yMaks) {
			MutableComponent cap = Component.empty();
			if (punya != null) {
				cap.append(t("punya", punya));
			}
			if (saran != null) {
				if (punya != null) {
					cap.append("  ");
				}
				cap.append(t("saran_panah", saran));
			}
			l.keterangan(cap, x0 + 12, y + 10, x1 - x0 - 12, saran != null ? TempatWarna.PERINGATAN : gaya.teksSamar);
			return 19;
		}
		return 11;
	}

	/** A small bar 0..1 with an optional marker at {@code tanda} (-1 none). */
	protected static void batang(Lukis l, int x0, int y, int w, int h, float isi, int warna, float tanda) {
		l.g.fill(x0, y, x0 + w, y + h, Palette.argb(0x60, 0x000000));
		l.g.fill(x0, y, x0 + Math.round(w * Math.max(0.0F, Math.min(1.0F, isi))), y + h, warna);
		l.g.fill(x0, y, x0 + w, y + 1, 0x30FFFFFF);
		if (tanda >= 0.0F) {
			int tx = x0 + Math.round(w * tanda);
			l.g.fill(tx, y - 1, tx + 1, y + h + 1, 0xFFFFF0C0);
		}
	}

	/** A bond / attitude meter in five segments ("███▒▒ 58"). */
	protected int segmen(Lukis l, int x0, int y, int nilai, int maks, int warna) {
		int n = 5;
		float per = maks / (float) n;
		for (int i = 0; i < n; i++) {
			float isi = Math.max(0.0F, Math.min(1.0F, (nilai - i * per) / per));
			int sx = x0 + i * 7;
			l.g.fill(sx, y, sx + 6, y + 6, Palette.argb(0x50, 0x000000));
			if (isi > 0.0F) {
				l.g.fill(sx, y + Math.round(6 * (1.0F - isi)), sx + 6, y + 6, warna);
			}
		}
		Component v = Component.literal(Integer.toString(nilai));
		Brush.text(l.g, font, v, x0 + n * 7 + 2, y - 1, gaya.teks, 1.0F, false);
		return n * 7 + 4 + font.width(v);
	}

	/** The attitude colour of -100..100 (red, grey, green). */
	protected static int warnaSikap(int sikap) {
		return sikap <= -30 ? 0xFFC0392B : sikap < -10 ? 0xFFE5821C : sikap < 20 ? 0xFF7A8C83 : sikap < 60 ? 0xFF2E8963 : 0xFFD4A637;
	}

	/** The name of Pangkat ordinal {@code ordinal} in its colour. */
	protected static Component namaPangkat(int ordinal) {
		return Pangkat.byOrdinal(ordinal).nama();
	}

	/** The display name of Realm ordinal {@code ordinal}. */
	protected static Component namaRanah(int ordinal) {
		Realm[] all = Realm.values();
		return all[Math.max(0, Math.min(all.length - 1, ordinal))].displayName();
	}

	/**
	 * A dangerous write button: the press opens the Gulungan's confirm seal (43.7 {@code SegelKonfirmasi}, medium: hold
	 * 1.5 s) and the op is sent once confirmed (G13; was a second press within 3 s).
	 */
	protected boolean tombolYakin(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			String op) {
		return tombol.gambar(l, kunci, x, y, w, h, label, aktif, alasan,
				() -> konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable("kultivator.gui43.konteks.yakin", label), () -> kirim(op)));
	}

	/** "hari 44 (2 hari lagi)" from a world day and today. */
	protected static Component hariKe(long hari, long hariIni) {
		long sisa = hari - hariIni;
		if (hari < 0L) {
			return t("hari.tak_tentu");
		}
		return sisa <= 0L ? t("hari.ini", hari) : sisa == 1L ? t("hari.besok", hari) : t("hari.lagi", hari, sisa);
	}

	/** Today as the client sees it (overworld clock / 24000, same as {@code Kota.day}). */
	protected long hariIni() {
		return minecraft != null && minecraft.level != null ? minecraft.level.getOverworldClockTime() / 24000L : 0L;
	}
}
