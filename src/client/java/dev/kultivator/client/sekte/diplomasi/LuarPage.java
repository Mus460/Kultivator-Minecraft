package dev.kultivator.client.sekte.diplomasi;

import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.PageKomposit;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.sekte.gabung.ui.layar.LayarDirektori;
import dev.kultivator.client.sekte.mandiri.seteru.SeteruScreen;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Sekte › Luar (DESIGN 43.5, module M11 of 43.16.2): the world outside the sect as one page with four subs, each a page of
 * its own shown in the content box (the composition pattern of {@code LembarHub.Halaman}, as {@code WilayahPage}):
 * <ul>
 * <li>{@code sekte_dunia}: the one sect list ({@code DaftarSekte}) of the sheet {@code gabung_direktori}
 * ({@link LayarDirektori}), mounted through {@link LembarHub} without a second registration; it also carries the hall's
 * standing and diplomacy of each sect, its feuds and its named disciples (G36);</li>
 * <li>{@code seteru}*: {@link SeteruScreen} (the hall's feuds, friends and patrol);</li>
 * <li>{@code perang}: {@link DiplomasiScreen} in war mode (the hall's wars, or the NPC sect's war tab: chip Sumber);</li>
 * <li>{@code utusan}*: {@link DiplomasiScreen} in envoy mode (the envoys' open offers).</li>
 * </ul>
 * Subs marked * need an own hall and are hidden otherwise (43.5). Registered once from {@code DiplomasiClient.init}.
 */
public final class LuarPage extends PageKomposit {

	public static final String SUB_SEKTE_DUNIA = "sekte_dunia";
	public static final String SUB_SETERU = "seteru";
	public static final String SUB_PERANG = "perang";
	public static final String SUB_UTUSAN = "utusan";
	private static boolean terdaftar;

	/** Registers the page (once). */
	public static void daftar() {
		if (terdaftar) {
			return;
		}
		terdaftar = true;
		HubPages.daftar(HubNav.SEKTE, Urutan.ID_LUAR, Urutan.LUAR, LuarPage::new);
		// the sect directory from a recruiter or command lands on Sekte › Luar › Sekte Dunia
		HubPages.daftarRumah(GabungLembar.Direktori.LAYAR, Set.of(), HubNav.SEKTE, Urutan.ID_LUAR, SUB_SEKTE_DUNIA, null);
		// the war tab of the NPC-sect politics sheet lands on Sekte › Luar › Perang, Perguruanku (P5, 43.12.4)
		HubPages.daftarRumah(GabungLembar.Politik.LAYAR, Set.of(GabungLembar.Politik.TAB_PERANG), HubNav.SEKTE, Urutan.ID_LUAR, SUB_PERANG,
				DiplomasiScreen::sumberPerguruan);
	}

	/** Opens Sekte › Luar › Sekte Dunia on sect {@code id} (Langit neighbours, a hall card, a chat link). */
	public static void bukaSekte(String id) {
		CompoundTag f = new CompoundTag();
		f.putString("sekte", id == null ? "" : id);
		HubNav.buka(Tujuan.of(HubNav.SEKTE, Urutan.ID_LUAR).sub(SUB_SEKTE_DUNIA).fokus(f));
	}

	private LuarPage() {
		super(HubNav.SEKTE, Urutan.ID_LUAR);
		// the 41 directory sheet on all its tabs; an unregistered mount (the page id stays "luar", one coin)
		HubPages.Entri direktori = new HubPages.Entri(HubNav.SEKTE, Urutan.ID_LUAR, Urutan.LUAR, () -> null, GabungLembar.Direktori.LAYAR, Set.of());
		tambahAnak(SUB_SEKTE_DUNIA, LembarHub.dari(direktori), () -> true);
		tambahAnak(SUB_SETERU, new SeteruScreen(), LuarPage::punyaAula);
		tambahAnak(SUB_PERANG, new DiplomasiScreen(DiplomasiScreen.Mode.PERANG), () -> true);
		tambahAnak(SUB_UTUSAN, new DiplomasiScreen(DiplomasiScreen.Mode.UTUSAN), LuarPage::punyaAula);
	}

	/** An own hall (or the hall info has not come yet: the sub shows until it does). */
	static boolean punyaAula() {
		return HallClient.version() == 0L || HallClient.aulaSendiri();
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.sekte." + Urutan.ID_LUAR);
	}

	@Override
	public int glyph() {
		return Glyphs.HUBUNGAN;
	}

	// ================================================================== life

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		HallClient.minta();
		if (fokus != null && fokus.contains("sekte")) {
			// the sect list may still be loading: the directory picks the sect when its sheet is there
			LayarDirektori.tuju(fokus.getStringOr("sekte", ""));
		}
		super.onShow(fokus);
	}

	@Override
	public void fokus(CompoundTag fokus) {
		if (fokus.contains("sekte")) {
			LayarDirektori.tuju(fokus.getStringOr("sekte", ""));
		}
		super.fokus(fokus);
	}
}
