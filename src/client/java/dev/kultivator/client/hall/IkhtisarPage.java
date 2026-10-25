package dev.kultivator.client.hall;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.sekte.gabung.GabungClient;
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

/**
 * Sekte › Ikhtisar (DESIGN 43.5, 43.16.2 M05): the one page that shows "my sect" (43.11 G45), from both roles:
 * <ul>
 * <li>{@code aula}* the hall ({@link AulaPage}), {@code identitas}* name, emblem, uniform and hall policy
 * ({@link IdentitasPage}), {@code catatan} the one chronicle ({@link LogPage});</li>
 * <li>{@code kartu}+ {@code hukum}+ {@code keluar}+ the member card of an NPC sect: the {@code gabung_kartu} sheet
 * ({@code LayarKartu}) mounted with those three tabs (its Shifu and Murid titipan are stacked inside Kartu).</li>
 * </ul>
 * (* hall master or a viewed hall, + member of an NPC sect.) A sub that does not apply is hidden. Without a hall and
 * without a sect the page shows only "Dirikan atau Bergabung". Each sub is a child page drawn in this page's box; the
 * sheet is a {@code LembarHub.Halaman}, so its read-only view is asked for only while shown (43.0.12).
 */
public class IkhtisarPage extends Page {

	public static final String AULA = "aula";
	public static final String IDENTITAS = "identitas";
	public static final String CATATAN = "catatan";
	public static final String KARTU = GabungLembar.Kartu.TAB_KARTU;
	public static final String HUKUM = GabungLembar.Kartu.TAB_HUKUM;
	public static final String KELUAR = GabungLembar.Kartu.TAB_KELUAR;
	/** The tabs of {@code gabung_kartu} mounted here (Shifu and Murid live inside Kartu). */
	public static final Set<String> TAB_KARTU = Set.of(KARTU, HUKUM, KELUAR);
	private static final String M = "kultivator.gui43.m05.";

	private final AulaPage aula = new AulaPage();
	private final IdentitasPage identitas = new IdentitasPage();
	private final LogPage catatan = new LogPage();
	private final LembarHub.Halaman kartu = LembarHub.dari(new HubPages.Entri(HubNav.SEKTE, Urutan.ID_IKHTISAR, Urutan.IKHTISAR, () -> null,
			GabungLembar.Kartu.LAYAR, TAB_KARTU));
	/** A sub asked for before the details said which subs apply (e.g. identitas from {@code LambangOpen}); "" = none. */
	private static String subDiminta = "";
	private String sub = "";
	private @Nullable Page anak;
	private boolean diTampilkan;

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.sekte.ikhtisar");
	}

	@Override
	public int glyph() {
		return Glyphs.HALL;
	}

	@Override
	public String helpTopic() {
		return anak != null ? anak.helpTopic() : "aula";
	}

	/** Opens the Gulungan on Sekte › Ikhtisar › {@code id}, also when the hall details have not arrived yet. */
	public static void buka(String id) {
		subDiminta = id == null ? "" : id;
		HubNav.buka(Tujuan.of(HubNav.SEKTE, Urutan.ID_IKHTISAR).sub(subDiminta));
	}

	// ================================================================== subs

	private static boolean anggota() {
		return GabungClient.lencanaSendiri() != null;
	}

	/** The subs that apply now, in tree order. */
	private List<String> berlaku() {
		List<String> ids = new ArrayList<>(6);
		boolean aulaAda = HallClient.adaAula();
		if (aulaAda) {
			ids.add(AULA);
		}
		if (HallClient.aulaSendiri()) {
			ids.add(IDENTITAS);
		}
		if (aulaAda || !HallClient.sumberCatatan().isEmpty()) {
			ids.add(CATATAN);
		}
		if (anggota()) {
			ids.add(KARTU);
			ids.add(HUKUM);
			ids.add(KELUAR);
		}
		return ids;
	}

	@Override
	public List<Sub> subs() {
		List<String> ids = berlaku();
		if (ids.size() < 2) {
			return List.of();
		}
		List<Sub> subs = new ArrayList<>(ids.size());
		for (String id : ids) {
			subs.add(new Sub(id, Component.translatable("kultivator.gui43.sub.ikhtisar." + id)));
		}
		return subs;
	}

	@Override
	public void showSub(String id) {
		List<String> ids = berlaku();
		String want = ids.contains(id) ? id : ids.isEmpty() ? "" : ids.get(0);
		Page next = halaman(want);
		if (next == anak && want.equals(sub)) {
			return;
		}
		Page old = anak;
		boolean kartuTetap = old == kartu && next == kartu;
		if (old != null && diTampilkan && !kartuTetap) {
			old.onHide();
		}
		sub = want;
		anak = next;
		if (next == null) {
			return;
		}
		if (screen != null) {
			next.pasang(screen);
		}
		if (next == kartu) {
			kartu.showSub(want);
		}
		if (w > 0 && h > 0) {
			next.layout(x, y, w, h);
		}
		if (diTampilkan && !kartuTetap) {
			next.onShow(null);
		}
	}

	private @Nullable Page halaman(String id) {
		return switch (id) {
			case AULA -> aula;
			case IDENTITAS -> identitas;
			case CATATAN -> catatan;
			case "" -> null;
			default -> kartu;
		};
	}

	// ================================================================== life

	@Override
	protected void build() {
		if (anak != null) {
			anak.layout(x, y, w, h);
		}
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		diTampilkan = true;
		HallClient.minta();
		if (anak == null) {
			showSub(sub);
		} else {
			anak.onShow(null);
		}
		super.onShow(fokus);
	}

	@Override
	public void onHide() {
		diTampilkan = false;
		if (anak != null) {
			anak.onHide();
		}
	}

	@Override
	public void tick() {
		List<String> ids = berlaku();
		if (!subDiminta.isEmpty() && ids.contains(subDiminta)) {
			String want = subDiminta;
			subDiminta = "";
			if (!want.equals(sub)) {
				showSub(want);
				tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_IKHTISAR).sub(want));
			}
		} else if (!ids.contains(sub) && !ids.isEmpty() || ids.isEmpty() && anak != null) {
			// the details arrived or the role changed (hall founded, sect joined or left): move to a sub that applies
			String first = ids.isEmpty() ? "" : ids.get(0);
			showSub(first);
			if (screen != null && !first.isEmpty()) {
				tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_IKHTISAR).sub(first));
			}
		}
		if (anak != null) {
			anak.tick();
		}
	}

	@Override
	public void onData() {
		if (anak != null) {
			anak.onData();
		}
	}

	@Override
	public boolean needsRebuild() {
		return anak != null && anak.needsRebuild();
	}

	@Override
	public boolean lebar() {
		return anak != null && anak.lebar();
	}

	@Override
	public long uang() {
		return anak != null ? anak.uang() : -1L;
	}

	@Override
	public boolean rinci() {
		return anak != null && anak.rinci();
	}

	@Override
	public Palette.Gaya gaya() {
		return anak != null ? anak.gaya() : Palette.Gaya.KERTAS;
	}

	@Override
	public boolean angka(int n) {
		return anak != null ? anak.angka(n) : super.angka(n);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (anak != null) {
			anak.render(g, mouseX, mouseY, seconds);
		}
	}

	/** Without a hall and without a sect: "Dirikan atau Bergabung" (43.5). */
	@Override
	protected void lukis(Lukis l) {
		if (anak != null) {
			return;
		}
		if (HallClient.version() == 0L) {
			Brush.fitCentered(l.g, l.font, Component.translatable("kultivator.gui43.konteks.memuat"), x + w / 2.0F, y + h / 2.0F - 4.0F, w - 8.0F,
					Palette.INK_FAINT, 1.0F, 1.0F, false);
			return;
		}
		int cy = y + 2;
		l.judul(Component.translatable(M + "mulai.judul"), x + 4, cy, w - 8, l.gaya.aksen);
		cy += 18;
		cy += Math.round(l.bungkus(Component.translatable("kultivator.hall.ui.no_hall"), x + 4, cy, w - 8, l.gaya.teks, Math.max(18, h - 70))) + 6;
		cy += Math.round(l.bungkus(Component.translatable(M + "mulai.gabung.teks"), x + 4, cy, w - 8, l.gaya.teksLemah, 27)) + 6;
		int bw = Math.max(TalismanButton.MIN_W, (w - 12) / 2);
		int by = Math.min(cy, y + h - 16);
		if (TalismanButton.gambar(l, "mulai_gabung", x + 4, by, bw, 15, Component.translatable(M + "mulai.gabung"), true, null,
				() -> tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_LUAR).sub("sekte_dunia")))) {
			l.tip(Component.translatable(M + "mulai.gabung.tip"));
		}
		if (TalismanButton.gambar(l, "mulai_dirikan", x + 8 + bw, by, w - 12 - bw, 15, Component.translatable(M + "mulai.dirikan"), true, null,
				() -> HubNav.help("aula"))) {
			l.tip(Component.translatable(M + "mulai.dirikan.tip"));
		}
	}

	// ================================================================== input (to the shown sub)

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return anak != null ? anak.mouseClicked(event, doubleClick) : super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return anak != null ? anak.mouseReleased(event) : super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return anak != null ? anak.mouseDragged(event, dx, dy) : super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return anak != null ? anak.mouseScrolled(mouseX, mouseY, scrollX, scrollY) : super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return anak != null ? anak.keyPressed(event) : super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		return anak != null && anak.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return anak != null ? anak.charTyped(event) : super.charTyped(event);
	}

	@Override
	public boolean mauTombol() {
		return anak != null ? anak.mauTombol() : super.mauTombol();
	}

	@Override
	public boolean modal() {
		return anak != null ? anak.modal() : super.modal();
	}

	@Override
	public boolean mundur() {
		return anak != null && anak.mundur();
	}

	@Override
	public boolean fokusTab(boolean back) {
		return anak != null ? anak.fokusTab(back) : super.fokusTab(back);
	}
}
