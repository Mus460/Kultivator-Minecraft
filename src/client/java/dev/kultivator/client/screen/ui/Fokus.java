package dev.kultivator.client.screen.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The focus ring and input engine of one page of the single kit (DESIGN 43.4.1, 43.7). One {@code Fokus} serves both kinds
 * of control a page may have: persistent {@link Widget}s (drawn through {@link Lukis#widget}) and immediate zones
 * ({@link Lukis#zona}); Tab / Shift+Tab walk them in drawing order, Enter / Space run the focused one, ←/→ go to its
 * {@code geser}, ↑↓ PgUp PgDn Home End move the focused (or first) {@link DaftarGulir}, {@code /} starts the page's search
 * field, a text field that writes takes keys and characters first. The page's {@link SegelKonfirmasi} lives here too and,
 * while open, takes every input (43.4.1 step 1).
 *
 * <p>Use per frame: {@link #mulai} → draw with the returned {@link Lukis} → {@link #selesai}. Input methods take layout
 * coordinates (the host divides by its scale) and return true when used. Escape is used only to stop a text field or
 * close the confirm; otherwise it returns false and the host steps back (43.4.2). Number keys are the host's: it calls
 * {@link #angka} only where numbers pick choices (43.17 K3).
 */
public final class Fokus {

	private final Consumer<Component> pesan;
	private final SegelKonfirmasi segel = new SegelKonfirmasi();
	private final Map<String, Object> simpan = new HashMap<>();
	private @Nullable String fokus;
	private List<Lukis.Zona> zonaTerakhir = new ArrayList<>();
	private List<DaftarGulir> daftarTerakhir = new ArrayList<>();
	private List<Pratinjau3D> pratinjauTerakhir = new ArrayList<>();
	private Map<Integer, Lukis.Zona> nomorTerakhir = new HashMap<>();
	private @Nullable DaftarGulir daftarFokus;
	private @Nullable InkField cariTerakhir;
	private @Nullable Lukis.Zona ditekan;
	private @Nullable DaftarGulir diseret;
	private @Nullable Pratinjau3D diputar;
	private long frameTerakhir = -1L;

	/** {@code pesan} shows a refusal (a disabled control's reason) in the host's footer, in the danger colour. */
	public Fokus(Consumer<Component> pesan) {
		this.pesan = pesan;
	}

	// =================================================================== frame

	/** Starts a frame for the page box {@code kx0..ky1}; mouse in layout coordinates. */
	public Lukis mulai(GuiGraphicsExtractor g, Font font, int mx, int my, float skala, Palette.Gaya gaya, int kx0, int ky0, int kx1, int ky1) {
		long now = Util.getMillis();
		float detik = frameTerakhir < 0L ? 0.0F : Math.min(0.1F, (now - frameTerakhir) / 1000.0F);
		frameTerakhir = now;
		boolean modal = segel.terbuka();
		// while the confirm is open nothing under it is hovered
		return new Lukis(this, g, font, modal ? -10000 : mx, modal ? -10000 : my, detik, now, skala, gaya, fokus, kx0, ky0, kx1, ky1);
	}

	/** Ends the frame: keeps its zones for input, draws the confirm over the page box and queues the tooltip. */
	public void selesai(Lukis l, int mx, int my) {
		zonaTerakhir = l.zona;
		daftarTerakhir = l.semuaDaftar;
		pratinjauTerakhir = l.semuaPratinjau;
		nomorTerakhir = l.nomor;
		daftarFokus = l.daftarAktif;
		cariTerakhir = l.cari;
		if (fokus != null && !adaZona(fokus)) {
			fokus = null;
		}
		if (segel.terbuka()) {
			segel.gambar(l.g, l.font, l.kx0, l.ky0, l.kx1, l.ky1, mx, my, l.detik, l.kini);
			SlipTooltip.antre(segel.tip(mx, my));
		} else if (l.tip != null && !l.tip.isEmpty()) {
			SlipTooltip.antre(l.tip);
		}
	}

	/**
	 * Per-key state of an immediate control kept across frames (a talisman's hover easing, a tab bar's lights...): the
	 * object stored under {@code kunci}, made by {@code buat} the first time.
	 */
	@SuppressWarnings("unchecked")
	public <T> T simpan(String kunci, Supplier<T> buat) {
		return (T) simpan.computeIfAbsent(kunci, k -> buat.get());
	}

	/** Forgets all per-key state and the focus (the page was rebuilt for another subject). */
	public void lupakan() {
		simpan.clear();
		fokus = null;
		ditekan = null;
		diseret = null;
		diputar = null;
	}

	// =================================================================== focus

	public @Nullable String kunci() {
		return fokus;
	}

	/** Moves the focus to {@code kunci} (null: none); a text field that loses it stops writing. */
	public void set(@Nullable String kunci) {
		if (java.util.Objects.equals(kunci, fokus)) {
			return;
		}
		Lukis.Zona lama = zonaFokus();
		if (lama != null && lama.kolom != null) {
			lama.kolom.setFocused(false);
		}
		fokus = kunci;
		Lukis.Zona baru = zonaFokus();
		if (baru != null && baru.kolom != null) {
			baru.kolom.setFocused(true);
		}
	}

	/** Tab / Shift+Tab: the next / previous focusable zone in drawing order. */
	public void geser(int arah) {
		List<String> urut = new ArrayList<>();
		for (Lukis.Zona z : zonaTerakhir) {
			if (z.fokus && z.aktif && !urut.contains(z.kunci)) {
				urut.add(z.kunci);
			}
		}
		if (urut.isEmpty()) {
			return;
		}
		int i = fokus == null ? -1 : urut.indexOf(fokus);
		int next = i < 0 ? (arah > 0 ? 0 : urut.size() - 1) : Math.floorMod(i + arah, urut.size());
		set(urut.get(next));
		UiSound.hover();
	}

	/** ↑↓ inside a group of zones (numbered choices, people rows): the next / previous one of {@code grup}, wrapping. */
	private void geserGrup(String grup, int arah) {
		List<String> urut = new ArrayList<>();
		for (Lukis.Zona z : zonaTerakhir) {
			if (grup.equals(z.grup) && z.fokus && !urut.contains(z.kunci)) {
				urut.add(z.kunci);
			}
		}
		if (urut.size() < 2) {
			return;
		}
		int i = fokus == null ? -1 : urut.indexOf(fokus);
		set(urut.get(i < 0 ? 0 : Math.floorMod(i + arah, urut.size())));
		UiSound.hover();
	}

	/** True while a text field writes or the confirm is open: the host passes keys here before its own (43.4.1). */
	public boolean mauTombol() {
		return segel.terbuka() || menulis() != null;
	}

	/** Shows a refusal in the host's footer. */
	public void tolak(Component alasan) {
		UiSound.denied();
		pesan.accept(alasan);
	}

	// =================================================================== confirm (43.7 SegelKonfirmasi)

	/** Asks for confirmation over the page; {@code ya} runs once confirmed. */
	public void konfirmasi(SegelKonfirmasi.Tingkat tingkat, Component teks, Runnable ya) {
		segel.buka(tingkat, teks, ya);
	}

	public SegelKonfirmasi segel() {
		return segel;
	}

	// =================================================================== numbered choices (K3)

	/** Picks numbered choice {@code n} (1-9) when the page drew one this frame; true when it existed. */
	public boolean angka(int n) {
		Lukis.Zona z = nomorTerakhir.get(n);
		if (z == null) {
			return false;
		}
		set(z.kunci);
		jalankan(z);
		return true;
	}

	// =================================================================== mouse

	/** A mouse button went down at layout (x, y); {@code tombol} 0 left, 1 right, 2 middle. */
	public boolean klik(double x, double y, int tombol) {
		if (segel.terbuka()) {
			segel.klik(x, y);
			return true;
		}
		Lukis.Zona z = zonaDi(x, y);
		if (tombol == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (z != null && z.kanan != null && z.aktif) {
				set(z.fokus ? z.kunci : fokus);
				z.kanan.run();
				return true;
			}
			return z != null;
		}
		if (tombol != InputConstants.MOUSE_BUTTON_LEFT) {
			return z != null;
		}
		InkField nulis = menulis();
		if (nulis != null && (z == null || z.kolom != nulis)) {
			nulis.setFocused(false);
		}
		for (DaftarGulir d : daftarTerakhir) {
			if (d.tekan(x, y)) {
				diseret = d;
				return true;
			}
		}
		for (Pratinjau3D p : pratinjauTerakhir) {
			if (p.berisi(x, y)) {
				diputar = p;
				p.mulaiSeret();
			}
		}
		if (z == null) {
			return diputar != null;
		}
		if (z.fokus) {
			set(z.kunci);
		} else {
			int hash = z.kunci.indexOf('#');
			if (hash > 0) {
				set(z.kunci.substring(0, hash));
			}
		}
		if (z.widget != null) {
			z.widget.pressAt(x, y);
			ditekan = z;
			return true;
		}
		if (z.aksi != null && z.aktif && z.kunci.contains("#")) {
			// list rows and sub-buttons make their own sound
			z.aksi.run();
		} else if (z.aksi != null || !z.aktif) {
			jalankan(z);
		}
		return true;
	}

	public boolean lepas(double x, double y) {
		if (segel.terbuka()) {
			segel.lepas();
			return true;
		}
		if (diseret != null) {
			diseret.lepas();
			diseret = null;
			return true;
		}
		if (diputar != null) {
			diputar.lepas(Util.getMillis());
			diputar = null;
		}
		if (ditekan != null) {
			Lukis.Zona z = ditekan;
			ditekan = null;
			if (z.widget != null) {
				boolean acted = z.widget.release(x, y);
				if (!acted && !z.aktif && z.berisi(x, y) && z.alasan != null) {
					pesan.accept(z.alasan);
				}
			}
			return true;
		}
		return false;
	}

	public boolean seret(double x, double y, double dx) {
		if (ditekan != null && ditekan.widget != null && ditekan.widget.dragTo(x, y)) {
			return true;
		}
		if (diseret != null) {
			diseret.seretKe(y);
			return true;
		}
		if (diputar != null) {
			diputar.seret(dx, Util.getMillis());
			return true;
		}
		return segel.terbuka();
	}

	public boolean gulir(double x, double y, double dy) {
		if (segel.terbuka()) {
			return true;
		}
		for (DaftarGulir d : daftarTerakhir) {
			if (d.berisi(x, y)) {
				d.roda(dy);
				return true;
			}
		}
		return false;
	}

	// =================================================================== keys

	public boolean tombol(KeyEvent event) {
		if (segel.terbuka()) {
			return segel.tombol(event);
		}
		InkField nulis = menulis();
		if (nulis != null && event.key() != InputConstants.KEY_TAB && nulis.keyPressed(event)) {
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_TAB) {
			geser(event.hasShiftDown() ? -1 : 1);
			return true;
		}
		if (event.isEscape()) {
			return false;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			Lukis.Zona z = zonaFokus();
			DaftarGulir d = daftarFokus;
			if (d != null && z != null && z.aksi == null && z.widget == null) {
				d.pakai();
				return true;
			}
			if (z != null) {
				jalankan(z);
				return true;
			}
			if (d != null) {
				d.pakai();
				return true;
			}
			return false;
		}
		if (key == InputConstants.KEY_SPACE) {
			Lukis.Zona z = zonaFokus();
			if (z != null && (z.widget != null || z.aksi != null)) {
				jalankan(z);
				return true;
			}
		}
		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
			Lukis.Zona z = zonaFokus();
			if (z != null && z.geser != null) {
				z.geser.accept(key == InputConstants.KEY_RIGHT ? 1 : -1);
				return true;
			}
			return false;
		}
		if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
			Lukis.Zona z = zonaFokus();
			if (z != null && z.grup != null) {
				geserGrup(z.grup, key == InputConstants.KEY_DOWN ? 1 : -1);
				return true;
			}
		}
		DaftarGulir daftar = daftarFokus != null ? daftarFokus : daftarTerakhir.isEmpty() ? null : daftarTerakhir.get(0);
		if (daftar != null) {
			int step = switch (key) {
				case InputConstants.KEY_UP -> -1;
				case InputConstants.KEY_DOWN -> 1;
				case InputConstants.KEY_PAGEUP -> -daftar.barisPerHalaman();
				case InputConstants.KEY_PAGEDOWN -> daftar.barisPerHalaman();
				case InputConstants.KEY_HOME -> -100000;
				case InputConstants.KEY_END -> 100000;
				default -> 0;
			};
			if (step != 0) {
				daftar.geserBaris(step);
				return true;
			}
		}
		if (key == InputConstants.KEY_SLASH && cariTerakhir != null) {
			cariTerakhir.mulaiDariTombol();
			for (Lukis.Zona z : zonaTerakhir) {
				if (z.kolom == cariTerakhir) {
					fokus = z.kunci;
					break;
				}
			}
			return true;
		}
		return false;
	}

	public boolean ketik(CharacterEvent event) {
		if (segel.terbuka()) {
			return true;
		}
		InkField nulis = menulis();
		return nulis != null && nulis.charTyped(event);
	}

	// =================================================================== helpers

	private @Nullable InkField menulis() {
		for (Lukis.Zona z : zonaTerakhir) {
			if (z.kolom != null && z.kolom.focused()) {
				return z.kolom;
			}
		}
		return null;
	}

	private boolean adaZona(String kunci) {
		for (Lukis.Zona z : zonaTerakhir) {
			if (z.kunci.equals(kunci) && z.fokus) {
				return true;
			}
		}
		return false;
	}

	private @Nullable Lukis.Zona zonaDi(double x, double y) {
		for (int i = zonaTerakhir.size() - 1; i >= 0; i--) {
			Lukis.Zona z = zonaTerakhir.get(i);
			if (z.berisi(x, y)) {
				return z;
			}
		}
		return null;
	}

	/** The focused zone of the last frame (its box lets a scroll stack follow Tab), or null. */
	public @Nullable Lukis.Zona zonaFokus() {
		if (fokus == null) {
			return null;
		}
		for (Lukis.Zona z : zonaTerakhir) {
			if (z.kunci.equals(fokus)) {
				return z;
			}
		}
		return null;
	}

	/** Runs a zone by keyboard or a plain click: its reason when disabled, its widget's activation or its action. */
	private void jalankan(Lukis.Zona z) {
		if (!z.aktif) {
			UiSound.denied();
			if (z.alasan != null) {
				pesan.accept(z.alasan);
			}
			return;
		}
		if (z.widget != null) {
			z.widget.activate();
			return;
		}
		if (z.aksi != null) {
			UiSound.press();
			z.aksi.run();
		}
	}
}
