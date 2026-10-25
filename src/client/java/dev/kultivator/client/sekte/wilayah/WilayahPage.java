package dev.kultivator.client.sekte.wilayah;

import dev.kultivator.client.desa.HallDesaPage;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.musuh.PertahananPage;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.sekte.kebun.KebunScreen;
import dev.kultivator.client.sekte.langit.ui.LangitScreen;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

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
 * A sub that does not apply is hidden (43.5): Pertahanan and Desa follow the hall info, Kebun and Langit need an own hall,
 * Cabang is always there (the sheet itself tells a player outside an NPC sect how to join). While shown it asks for the
 * hall info at once ({@code HallClient.minta}; HallClient repeats it every 4 s while the Sekte section shows). Registered once from
 * {@code KebunClient.init} ({@link #daftar()}).
 */
public final class WilayahPage extends Page {

	public static final String SUB_PERTAHANAN = "pertahanan";
	public static final String SUB_DESA = "desa_pakta";
	public static final String SUB_CABANG = "cabang";
	public static final String SUB_KEBUN = "kebun";
	public static final String SUB_LANGIT = "langit";
	private static final String SUB = "kultivator.gui43.sub." + Urutan.ID_WILAYAH + ".";
	private static boolean terdaftar;

	/** One sub: id, its page, when it applies. */
	private record Anak(String id, Page page, BooleanSupplier berlaku) {
	}

	private final List<Anak> anak = new ArrayList<>();
	private Anak aktif;
	private final java.util.Map<Page, String> laid = new java.util.HashMap<>();
	private boolean ditampilkan;

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
		// before the first hall info the first sub stays (so the page does not open on Cabang and jump back)
		anak.add(new Anak(SUB_PERTAHANAN, new PertahananPage(), () -> HallClient.version() == 0L || HallClient.info().contains("musuh")));
		anak.add(new Anak(SUB_DESA, new HallDesaPage(), () -> HallClient.info().getBooleanOr("has", false)
				&& HallClient.info().getCompoundOrEmpty("desa").getBooleanOr("on", false)));
		// the 41 politics sheet on its Cabang tab only; an unregistered mount (the page id stays "wilayah", one coin)
		HubPages.Entri cabang = new HubPages.Entri(HubNav.SEKTE, Urutan.ID_WILAYAH, Urutan.WILAYAH, () -> null, GabungLembar.Politik.LAYAR,
				Set.of(GabungLembar.Politik.TAB_CABANG));
		anak.add(new Anak(SUB_CABANG, LembarHub.dari(cabang), () -> true));
		anak.add(new Anak(SUB_KEBUN, new KebunScreen(), WilayahPage::punyaAula));
		anak.add(new Anak(SUB_LANGIT, new LangitScreen(), WilayahPage::punyaAula));
		aktif = anak.get(0);
	}

	/** An own hall (or the hall info has not come yet: the sub shows until it does). */
	private static boolean punyaAula() {
		CompoundTag info = HallClient.info();
		return HallClient.version() == 0L || info.getBooleanOr("has", false) && !info.getBooleanOr("readonly", false);
	}

	private List<Anak> berlaku() {
		List<Anak> out = new ArrayList<>(anak.size());
		for (Anak a : anak) {
			if (a.berlaku().getAsBoolean()) {
				out.add(a);
			}
		}
		return out;
	}

	private @Nullable Anak cari(String id) {
		for (Anak a : anak) {
			if (a.id().equals(id)) {
				return a;
			}
		}
		return null;
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

	@Override
	public String helpTopic() {
		return aktif.page().helpTopic();
	}

	@Override
	public List<Sub> subs() {
		List<Sub> subs = new ArrayList<>();
		for (Anak a : berlaku()) {
			subs.add(new Sub(a.id(), Component.translatable(SUB + a.id())));
		}
		return subs;
	}

	@Override
	public void showSub(String id) {
		Anak next = cari(id);
		if (next == null || next == aktif) {
			return;
		}
		if (ditampilkan) {
			aktif.page().onHide();
		}
		aktif = next;
		pasangAnak();
		if (ditampilkan) {
			aktif.page().onShow(null);
		}
	}

	@Override
	public boolean lebar() {
		return aktif.page().lebar();
	}

	@Override
	public long uang() {
		return aktif.page().uang();
	}

	@Override
	public boolean rinci() {
		return aktif.page().rinci();
	}

	@Override
	public Palette.Gaya gaya() {
		return aktif.page().gaya();
	}

	@Override
	public boolean angka(int n) {
		return aktif.page().angka(n);
	}

	// ================================================================== life

	/** Lays the shown sub's page into the box when the box changed since its last layout. */
	private void pasangAnak() {
		if (w <= 0 || h <= 0) {
			return;
		}
		Page p = aktif.page();
		if (screen != null) {
			p.pasang(screen);
		}
		String box = x + "," + y + "," + w + "," + h;
		if (!box.equals(laid.get(p))) {
			laid.put(p, box);
			p.layout(x, y, w, h);
		}
	}

	@Override
	protected void build() {
		laid.clear();
		pasangAnak();
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		ditampilkan = true;
		// fresh hall details now; HallClient keeps asking every 4 s while the Sekte section shows (M05)
		HallClient.minta();
		aktif.page().onShow(fokus);
	}

	@Override
	public void onHide() {
		ditampilkan = false;
		aktif.page().onHide();
	}

	@Override
	public void fokus(CompoundTag fokus) {
		aktif.page().fokus(fokus);
	}

	@Override
	public void tick() {
		// the shown sub stopped applying (the hall info changed): move to the first that does
		if (!aktif.berlaku().getAsBoolean()) {
			List<Anak> ada = berlaku();
			if (!ada.isEmpty()) {
				tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_WILAYAH).sub(ada.get(0).id()));
			}
		}
		aktif.page().tick();
	}

	@Override
	public void onData() {
		aktif.page().onData();
	}

	@Override
	public boolean needsRebuild() {
		return aktif.page().needsRebuild();
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		pasangAnak();
		aktif.page().render(g, mouseX, mouseY, seconds);
	}

	// ================================================================== input (all to the shown sub's page)

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return aktif.page().mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return aktif.page().mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return aktif.page().mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return aktif.page().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return aktif.page().keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		return aktif.page().keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return aktif.page().charTyped(event);
	}

	@Override
	public boolean mauTombol() {
		return aktif.page().mauTombol();
	}

	@Override
	public boolean modal() {
		return aktif.page().modal();
	}

	@Override
	public boolean mundur() {
		return aktif.page().mundur();
	}

	@Override
	public boolean fokusTab(boolean back) {
		return aktif.page().fokusTab(back);
	}
}
