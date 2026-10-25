package dev.kultivator.client.screen.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The single filter row of the kit (DESIGN 43.7, 43.2.3): a search field ({@link InkField} in search mode, {@code /}
 * starts it), a row of kind chips ("Semua" first) and an optional sort cycle. It replaces the filter rows of the Kodeks,
 * the Direktori, Paviliun Jasa, the weapon shop and the Kronik, and stands in for category tabs at the third level, so a
 * page never needs a fourth tab bar (43.0.4). Filtering stays client side: the page asks {@link #cocok} per entry.
 */
public final class Saring {

	/** One kind chip or sort order: id and name. */
	public record Pilihan(String id, Component label) {
	}

	public static final String SEMUA = "";
	private static final int TINGGI = 14;

	private final InkField cari;
	private final TabBar chips = new TabBar();
	private final Runnable onChange;
	private List<Pilihan> jenis = List.of();
	private List<Pilihan> urut = List.of();
	private String jenisAktif = SEMUA;
	private int urutAktif;

	/** {@code onChange} runs on every change of the text, the kind or the order. */
	public Saring(Runnable onChange) {
		this.onChange = onChange;
		this.cari = new InkField(0, 0, 80, TINGGI, 40, null).cari(Component.translatable("kultivator.gui43.kit.cari"), t -> onChange.run());
	}

	/** The kind chips (without "Semua", which is added first). */
	public Saring jenis(List<Pilihan> jenis) {
		this.jenis = jenis;
		List<TabBar.Tab> tabs = new ArrayList<>();
		tabs.add(TabBar.Tab.of(SEMUA, Component.translatable("kultivator.gui43.kit.semua")));
		for (Pilihan p : jenis) {
			tabs.add(TabBar.Tab.of(p.id(), p.label()));
		}
		chips.setTabs(tabs);
		if (!SEMUA.equals(jenisAktif) && jenis.stream().noneMatch(p -> p.id().equals(jenisAktif))) {
			jenisAktif = SEMUA;
		}
		return this;
	}

	/** The sort orders (the first is the default; empty = no sort control). */
	public Saring urut(List<Pilihan> urut) {
		this.urut = urut;
		urutAktif = Math.min(urutAktif, Math.max(0, urut.size() - 1));
		return this;
	}

	public String teks() {
		return cari.text();
	}

	/** The chosen kind id ({@link #SEMUA} = all). */
	public String jenisAktif() {
		return jenisAktif;
	}

	public void setJenis(String id) {
		jenisAktif = id == null ? SEMUA : id;
	}

	/** The chosen sort id (empty when there are no orders). */
	public String urutAktif() {
		return urut.isEmpty() ? "" : urut.get(urutAktif).id();
	}

	/** The search field (for a page that wants to place focus or read it). */
	public InkField kolom() {
		return cari;
	}

	/** True when an entry named {@code nama} of kind {@code jenisId} passes the filter. */
	public boolean cocok(Component nama, @Nullable String jenisId) {
		return cari.cocok(nama) && (SEMUA.equals(jenisAktif) || jenisAktif.equals(jenisId));
	}

	/** Clears text and kind. */
	public void reset() {
		cari.setText("");
		jenisAktif = SEMUA;
		onChange.run();
	}

	/**
	 * Draws the row from (x0, y) to x1; returns the height used (one row of 14 when wide enough, else two: the search and
	 * sort above, the chips below).
	 */
	public int gambar(Lukis l, String kunci, int x0, int y, int x1) {
		int w = x1 - x0;
		boolean adaChip = !jenis.isEmpty();
		boolean duaBaris = adaChip && w < 260;
		int urutW = 0;
		if (!urut.isEmpty()) {
			Component label = Component.translatable("kultivator.gui43.kit.urut", urut.get(urutAktif).label());
			urutW = Math.min(110, TalismanButton.lebar(l.font, label, 50, 110) + 10);
			Urut u = l.fokusRing().simpan("saring:urut:" + kunci, Urut::new);
			u.saring = this;
			u.label = label;
			u.widget.x = x1 - urutW;
			u.widget.y = y;
			u.widget.w = urutW;
			u.widget.h = TINGGI;
			l.widget(kunci + ":urut", u.widget);
		}
		int cariW = duaBaris || !adaChip ? w - urutW - (urutW > 0 ? 4 : 0) : Math.max(80, Math.min(140, w / 3));
		cari.x = x0;
		cari.y = y;
		cari.w = Math.max(TalismanButton.MIN_W, cariW);
		cari.h = TINGGI;
		l.widget(kunci + ":cari", cari);
		if (!adaChip) {
			return TINGGI;
		}
		int cy = duaBaris ? y + TINGGI + 3 : y;
		int cx0 = duaBaris ? x0 : x0 + cari.w + 4;
		int cx1 = duaBaris ? x1 : x1 - urutW - (urutW > 0 ? 4 : 0);
		chips.gambar(l, kunci + ":jenis", cx0, cy, cx1, cy + TINGGI, false, TabBar.Rupa.SUB, true, jenisAktif, id -> {
			jenisAktif = id;
			onChange.run();
		});
		return duaBaris ? TINGGI * 2 + 3 : TINGGI;
	}

	private void majuUrut(int arah) {
		if (urut.isEmpty()) {
			return;
		}
		urutAktif = Math.floorMod(urutAktif + arah, urut.size());
		onChange.run();
	}

	/** The sort cycle talisman kept across frames. */
	private static final class Urut {
		@Nullable Saring saring;
		Component label = Component.empty();
		final TalismanButton widget = new TalismanButton(0, 0, 50, TINGGI, () -> label);

		Urut() {
			widget.siklus(() -> {
				if (saring != null) {
					saring.majuUrut(1);
				}
			}, () -> {
				if (saring != null) {
					saring.majuUrut(-1);
				}
			});
		}
	}
}
