package dev.kultivator.client.hub;

import dev.kultivator.client.screen.Page;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The page registry of the Gulungan Utama (DESIGN 43.6; replaces {@code HubCards}). Each module registers its pages from
 * its own {@code *Client} init: {@link #daftar} for a {@link Page} class, {@link #daftarLembar} for a sheet screen of 38/41
 * mounted with a set of tabs (built by the sheet mounter of 43S-C through {@link #pembuatLembar}). The host asks
 * {@link #dari} for a section's pages in {@link Urutan} order and builds each page lazily the first time it is shown. A
 * second registration with the same id replaces the first. Client thread only.
 */
public final class HubPages {

	/**
	 * One registered page. {@code buat} may return null (a sheet page whose mounter is not installed yet): the host then
	 * hides the page. {@code layar}/{@code tab} are set only for sheet pages.
	 */
	public record Entri(int bagian, String id, int urutan, Supplier<Page> buat, @Nullable String layar, Set<String> tab) {
	}

	/** Builds the page of a sheet screen {@code layar} showing only tabs {@code tab} (empty = all); installed by 43S-C. */
	@FunctionalInterface
	public interface PembuatLembar {
		@Nullable Page buat(String layar, Set<String> tab);
	}

	/**
	 * A home without a page coin of its own (43.12.4): sheet {@code layar} opened on one of tabs {@code tab} (empty = any
	 * tab) belongs to the sheet mount (same {@code layar}/{@code tab}) held by page {@code halaman} of section {@code bagian},
	 * shown on sub {@code sub} ("" = the sheet's own tab). {@code sebelum} runs before the Gulungan goes there (e.g. pick a
	 * source chip), may be null.
	 */
	public record Rumah(String layar, Set<String> tab, int bagian, String halaman, String sub, @Nullable Runnable sebelum) {
	}

	private static final List<Entri> ENTRI = new ArrayList<>();
	private static final List<Rumah> RUMAH = new ArrayList<>();
	private static @Nullable PembuatLembar pembuatLembar;
	private static long versi;

	/** Registers page {@code id} in section {@code bagian} at order {@code urutan} ({@link Urutan}). */
	public static synchronized void daftar(int bagian, String id, int urutan, Supplier<? extends Page> buat) {
		if (id == null || id.isEmpty() || buat == null || bagian < 0 || bagian >= HubNav.SECTIONS) {
			return;
		}
		tambah(new Entri(bagian, id, urutan, buat::get, null, Set.of()));
	}

	/** Registers sheet screen {@code layar} with tabs {@code tab} (empty = all tabs) as page {@code id}. */
	public static synchronized void daftarLembar(int bagian, String id, int urutan, String layar, Set<String> tab) {
		if (id == null || id.isEmpty() || layar == null || layar.isEmpty() || bagian < 0 || bagian >= HubNav.SECTIONS) {
			return;
		}
		Set<String> tabs = tab == null ? Set.of() : Set.copyOf(tab);
		tambah(new Entri(bagian, id, urutan, () -> {
			PembuatLembar p = pembuatLembar;
			return p == null ? null : p.buat(layar, tabs);
		}, layar, tabs));
	}

	/**
	 * Registers a page that holds sheet {@code layar} (tabs {@code tab}) inside one of its subs as the home of that sheet, so a
	 * world context or read-only answer of it lands there instead of under Di Sini. The page must mount the sheet through
	 * {@code LembarHub.dari} with the same {@code layar}/{@code tab}. A second home for the same sheet and tabs replaces the first.
	 */
	public static synchronized void daftarRumah(String layar, Set<String> tab, int bagian, String halaman, String sub, @Nullable Runnable sebelum) {
		if (layar == null || layar.isEmpty() || halaman == null || halaman.isEmpty() || bagian < 0 || bagian >= HubNav.SECTIONS) {
			return;
		}
		Rumah r = new Rumah(layar, tab == null ? Set.of() : Set.copyOf(tab), bagian, halaman, sub == null ? "" : sub, sebelum);
		RUMAH.removeIf(e -> e.layar().equals(r.layar()) && e.tab().equals(r.tab()));
		RUMAH.add(r);
	}

	/**
	 * The home of sheet {@code layar} on tab {@code tab} from {@link #daftarRumah}: a tab match first, else an all-tabs home,
	 * else (sheet opened without a tab) the first home of that sheet; or null.
	 */
	public static synchronized @Nullable Rumah rumah(String layar, String tab) {
		boolean tanpaTab = tab == null || tab.isEmpty();
		Rumah semua = null;
		Rumah pertama = null;
		for (Rumah r : RUMAH) {
			if (!r.layar().equals(layar)) {
				continue;
			}
			if (!tanpaTab && r.tab().contains(tab)) {
				return r;
			}
			if (r.tab().isEmpty() && semua == null) {
				semua = r;
			}
			if (pertama == null) {
				pertama = r;
			}
		}
		return semua != null ? semua : tanpaTab ? pertama : null;
	}

	/** Installs the builder of sheet pages (called once by 43S-C's {@code LembarHub}). */
	public static synchronized void pembuatLembar(@Nullable PembuatLembar pembuat) {
		pembuatLembar = pembuat;
		versi++;
	}

	private static void tambah(Entri entri) {
		ENTRI.removeIf(e -> e.id().equals(entri.id()));
		ENTRI.add(entri);
		versi++;
	}

	/** The pages of section {@code bagian} in order (a copy). */
	public static synchronized List<Entri> dari(int bagian) {
		List<Entri> list = new ArrayList<>();
		for (Entri e : ENTRI) {
			if (e.bagian() == bagian) {
				list.add(e);
			}
		}
		list.sort(Comparator.comparingInt(Entri::urutan));
		return list;
	}

	/** The registration of page {@code id}, or null. */
	public static synchronized @Nullable Entri cari(String id) {
		for (Entri e : ENTRI) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}

	/** True when page {@code id} is registered (in any section). */
	public static boolean ada(String id) {
		return cari(id) != null;
	}

	/** Changes whenever a page is registered or the sheet builder is installed (the host then rebuilds its rail). */
	public static synchronized long versi() {
		return versi;
	}

	private HubPages() {
	}
}
