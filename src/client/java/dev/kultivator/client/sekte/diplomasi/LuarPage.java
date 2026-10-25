package dev.kultivator.client.sekte.diplomasi;

import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.sekte.gabung.ui.layar.LayarDirektori;
import dev.kultivator.client.sekte.mandiri.seteru.SeteruScreen;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

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
public final class LuarPage extends Page {

	public static final String SUB_SEKTE_DUNIA = "sekte_dunia";
	public static final String SUB_SETERU = "seteru";
	public static final String SUB_PERANG = "perang";
	public static final String SUB_UTUSAN = "utusan";
	private static final String SUB = "kultivator.gui43.sub." + Urutan.ID_LUAR + ".";
	private static boolean terdaftar;

	/** One sub: id, its page, when it applies. */
	private record Anak(String id, Page page, BooleanSupplier berlaku) {
	}

	private final List<Anak> anak = new ArrayList<>();
	private Anak aktif;
	private final Map<Page, String> laid = new HashMap<>();
	private boolean ditampilkan;

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
		// the 41 directory sheet on all its tabs; an unregistered mount (the page id stays "luar", one coin)
		HubPages.Entri direktori = new HubPages.Entri(HubNav.SEKTE, Urutan.ID_LUAR, Urutan.LUAR, () -> null, GabungLembar.Direktori.LAYAR, Set.of());
		anak.add(new Anak(SUB_SEKTE_DUNIA, LembarHub.dari(direktori), () -> true));
		anak.add(new Anak(SUB_SETERU, new SeteruScreen(), LuarPage::punyaAula));
		anak.add(new Anak(SUB_PERANG, new DiplomasiScreen(DiplomasiScreen.Mode.PERANG), () -> true));
		anak.add(new Anak(SUB_UTUSAN, new DiplomasiScreen(DiplomasiScreen.Mode.UTUSAN), LuarPage::punyaAula));
		aktif = anak.get(0);
	}

	/** An own hall (or the hall info has not come yet: the sub shows until it does). */
	static boolean punyaAula() {
		return HallClient.version() == 0L || HallClient.aulaSendiri();
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
		return Component.translatable("kultivator.gui43.hal.sekte." + Urutan.ID_LUAR);
	}

	@Override
	public int glyph() {
		return Glyphs.HUBUNGAN;
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
	public @Nullable Sumber sumber() {
		return aktif.page().sumber();
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
		HallClient.minta();
		if (fokus != null && fokus.contains("sekte")) {
			// the sect list may still be loading: the directory picks the sect when its sheet is there
			LayarDirektori.tuju(fokus.getStringOr("sekte", ""));
		}
		aktif.page().onShow(fokus);
	}

	@Override
	public void onHide() {
		ditampilkan = false;
		aktif.page().onHide();
	}

	@Override
	public void fokus(CompoundTag fokus) {
		if (fokus.contains("sekte")) {
			LayarDirektori.tuju(fokus.getStringOr("sekte", ""));
		}
		aktif.page().fokus(fokus);
	}

	@Override
	public void tick() {
		// the shown sub stopped applying (the hall info changed): move to the first that does
		if (!aktif.berlaku().getAsBoolean()) {
			List<Anak> ada = berlaku();
			if (!ada.isEmpty()) {
				tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_LUAR).sub(ada.get(0).id()));
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
