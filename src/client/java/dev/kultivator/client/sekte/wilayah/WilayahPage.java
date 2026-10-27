package dev.kultivator.client.sekte.wilayah;

import dev.kultivator.client.desa.HallDesaPage;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.musuh.PertahananPage;
import dev.kultivator.client.screen.PageKomposit;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.sekte.gabung.GabungClient;
import dev.kultivator.client.sekte.kebun.KebunScreen;
import dev.kultivator.client.sekte.langit.ui.LangitScreen;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Sekte › Wilayah (DESIGN 43.5, module M10 of 43.16.2): the sect's lands as one page with five subs, each sub a page of
 * its own module shown in the content box (the composition pattern of {@code LembarHub.Halaman}):
 * <ul>
 * <li>{@code pertahanan}: {@link PertahananPage} (raids, defence, raid log);</li>
 * <li>{@code desa_pakta}: {@link HallDesaPage} (vassal villages; the 42 pakta are mounted here when 42 resumes, 43.16.6);</li>
 * <li>{@code cabang}: tab Cabang of the sheet {@code gabung_politik} (41 Politik Cabang), mounted through {@link LembarHub}
 * without a second registration, so the read-only view and its ops work from here;</li>
 * <li>{@code kebun}: {@link KebunScreen} (Kebun + Tambang stacked);</li>
 * <li>{@code langit}: {@link LangitScreen} (the sky sect).</li>
 * </ul>
 * A sub that does not apply is hidden (43.5): Pertahanan and Desa follow their own page's {@code tampil()}, Kebun and
 * Langit need an own hall, Cabang a member's badge of an NPC sect; without a hall and without a sect the whole page is hidden. While shown it asks for the
 * hall info at once ({@code HallClient.minta}; HallClient repeats it every 4 s while the Sekte section shows). Registered once from
 * {@code KebunClient.init} ({@link #daftar()}).
 */
public final class WilayahPage extends PageKomposit {

	public static final String SUB_PERTAHANAN = "pertahanan";
	public static final String SUB_DESA = "desa_pakta";
	public static final String SUB_CABANG = "cabang";
	public static final String SUB_KEBUN = "kebun";
	public static final String SUB_LANGIT = "langit";
	private static boolean terdaftar;

	/** Registers the page (once). */
	public static void daftar() {
		if (terdaftar) {
			return;
		}
		terdaftar = true;
		HubPages.daftar(HubNav.SEKTE, Urutan.ID_WILAYAH, Urutan.WILAYAH, WilayahPage::new);
		// the branch tab of the NPC-sect politics sheet lands on Sekte › Wilayah › Cabang (P5, 43.12.4)
		HubPages.daftarRumah(GabungLembar.Politik.LAYAR, Set.of(GabungLembar.Politik.TAB_CABANG), HubNav.SEKTE, Urutan.ID_WILAYAH, SUB_CABANG, null);
	}

	private WilayahPage() {
		super(HubNav.SEKTE, Urutan.ID_WILAYAH);
		// before the first hall info the first sub stays (so the page does not open on Cabang and jump back)
		PertahananPage pertahanan = new PertahananPage();
		tambahAnak(SUB_PERTAHANAN, pertahanan, () -> HallClient.version() == 0L || pertahanan.tampil());
		HallDesaPage desa = new HallDesaPage();
		tambahAnak(SUB_DESA, desa, desa::tampil);
		// the 41 politics sheet on its Cabang tab only; an unregistered mount (the page id stays "wilayah", one coin)
		HubPages.Entri cabang = new HubPages.Entri(HubNav.SEKTE, Urutan.ID_WILAYAH, Urutan.WILAYAH, () -> null, GabungLembar.Politik.LAYAR,
				Set.of(GabungLembar.Politik.TAB_CABANG));
		tambahAnak(SUB_CABANG, LembarHub.dari(cabang), () -> GabungClient.lencanaSendiri() != null);
		tambahAnak(SUB_KEBUN, new KebunScreen(), WilayahPage::punyaAula);
		tambahAnak(SUB_LANGIT, new LangitScreen(), WilayahPage::punyaAula);
	}

	/** An own hall (or the hall info has not come yet: the sub shows until it does). */
	private static boolean punyaAula() {
		CompoundTag info = HallClient.info();
		return HallClient.version() == 0L || info.getBooleanOr("has", false) && !info.getBooleanOr("readonly", false);
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.sekte." + Urutan.ID_WILAYAH);
	}

	@Override
	public int glyph() {
		return Glyphs.HALL;
	}

	/** Hidden without a hall and without a sect (43.5); shown while the hall info is still unknown. */
	@Override
	public boolean tampil() {
		return HallClient.version() == 0L || HallClient.adaAula() || GabungClient.lencanaSendiri() != null;
	}

	// ================================================================== life

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		// fresh hall details now; HallClient keeps asking every 4 s while the Sekte section shows (M05)
		HallClient.minta();
		super.onShow(fokus);
	}
}
