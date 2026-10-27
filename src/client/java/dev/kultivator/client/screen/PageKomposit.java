package dev.kultivator.client.screen;

import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.screen.ui.Palette;
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
import java.util.function.BooleanSupplier;

/**
 * The one composite page base (DESIGN 43.11 G02): a page whose subs are child pages drawn in its own box. Everything the
 * page does not override goes to the shown child ({@link #anak()}), or to this page's own ring when there is none, so
 * every composite answers Tab, keys and the header the same way.
 * <p>
 * Two uses: <b>list mode</b> ({@link #tambahAnak}: Sekte › Luar, Wilayah), where this base keeps the sub bar, the switch,
 * the lazy layout and the move to the first sub that still applies; or a page that picks its child itself (Ikhtisar,
 * Anggota, Kas, Kegiatan) by overriding {@link #anak()} and its life methods.
 */
public abstract class PageKomposit extends Page {

	/** One child of the list mode: sub id, its page, when it applies. */
	protected record Anak(String id, Page page, BooleanSupplier berlaku) {
	}

	private final int bagian;
	private final String halaman;
	private final List<Anak> daftar = new ArrayList<>();
	private final Map<Page, String> laid = new HashMap<>();
	private @Nullable Anak pilih;
	/** True between {@link #onShow} and {@link #onHide} (list mode). */
	protected boolean ditampilkan;

	/** A composite that picks its own child ({@link #anak()} overridden). */
	protected PageKomposit() {
		this(-1, "");
	}

	/** A list-mode composite on page {@code halaman} of section {@code bagian} (sub names {@code kultivator.gui43.sub.<halaman>.<id>}). */
	protected PageKomposit(int bagian, String halaman) {
		this.bagian = bagian;
		this.halaman = halaman;
	}

	/** List mode: adds a sub; the first one added is shown first. */
	protected final void tambahAnak(String id, Page page, BooleanSupplier berlaku) {
		Anak a = new Anak(id, page, berlaku);
		daftar.add(a);
		if (pilih == null) {
			pilih = a;
		}
	}

	/** The child shown now, or null (the page then draws and handles its own content). */
	protected @Nullable Page anak() {
		return pilih == null ? null : pilih.page();
	}

	private List<Anak> berlaku() {
		List<Anak> out = new ArrayList<>(daftar.size());
		for (Anak a : daftar) {
			if (a.berlaku().getAsBoolean()) {
				out.add(a);
			}
		}
		return out;
	}

	/** List mode: lays the shown child into the box when the box changed since its last layout. */
	private void pasangPilih() {
		if (pilih == null || w <= 0 || h <= 0) {
			return;
		}
		Page p = pilih.page();
		if (screen != null) {
			p.pasang(screen);
		}
		String box = x + "," + y + "," + w + "," + h;
		if (!box.equals(laid.get(p))) {
			laid.put(p, box);
			p.layout(x, y, w, h);
		}
	}

	// ================================================================== identity (to the child)

	@Override
	public String helpTopic() {
		Page a = anak();
		return a != null ? a.helpTopic() : super.helpTopic();
	}

	@Override
	public List<Sub> subs() {
		List<Sub> subs = new ArrayList<>();
		for (Anak a : berlaku()) {
			subs.add(new Sub(a.id(), Component.translatable("kultivator.gui43.sub." + halaman + "." + a.id())));
		}
		return subs;
	}

	@Override
	public void showSub(String id) {
		Anak next = null;
		for (Anak a : daftar) {
			if (a.id().equals(id)) {
				next = a;
			}
		}
		if (next == null || next == pilih) {
			return;
		}
		if (ditampilkan && pilih != null) {
			pilih.page().onHide();
		}
		pilih = next;
		pasangPilih();
		if (ditampilkan) {
			next.page().onShow(null);
		}
	}

	@Override
	public boolean lebar() {
		Page a = anak();
		return a != null && a.lebar();
	}

	@Override
	public @Nullable Sumber sumber() {
		Page a = anak();
		return a != null ? a.sumber() : null;
	}

	@Override
	public long uang() {
		Page a = anak();
		return a != null ? a.uang() : -1L;
	}

	@Override
	public boolean rinci() {
		Page a = anak();
		return a != null && a.rinci();
	}

	@Override
	public Palette.Gaya gaya() {
		Page a = anak();
		return a != null ? a.gaya() : super.gaya();
	}

	@Override
	public boolean angka(int n) {
		Page a = anak();
		return a != null ? a.angka(n) : super.angka(n);
	}

	// ================================================================== life

	@Override
	protected void build() {
		laid.clear();
		pasangPilih();
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		ditampilkan = true;
		Page a = anak();
		if (a != null) {
			a.onShow(fokus);
		}
	}

	@Override
	public void onHide() {
		ditampilkan = false;
		Page a = anak();
		if (a != null) {
			a.onHide();
		}
	}

	@Override
	public void fokus(CompoundTag fokus) {
		Page a = anak();
		if (a != null) {
			a.fokus(fokus);
		}
	}

	@Override
	public void tick() {
		// list mode: the shown sub stopped applying (the hall info changed): move to the first that does
		if (pilih != null && !pilih.berlaku().getAsBoolean()) {
			List<Anak> ada = berlaku();
			if (!ada.isEmpty()) {
				tuju(Tujuan.of(bagian, halaman).sub(ada.get(0).id()));
			}
		}
		Page a = anak();
		if (a != null) {
			a.tick();
		}
	}

	@Override
	public void onData() {
		Page a = anak();
		if (a != null) {
			a.onData();
		}
	}

	@Override
	public boolean needsRebuild() {
		Page a = anak();
		return a != null && a.needsRebuild();
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		pasangPilih();
		Page a = anak();
		if (a != null) {
			a.render(g, mouseX, mouseY, seconds);
		}
	}

	// ================================================================== input (to the child, else this page's own ring)

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Page a = anak();
		return a != null ? a.mouseClicked(event, doubleClick) : super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		Page a = anak();
		return a != null ? a.mouseReleased(event) : super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		Page a = anak();
		return a != null ? a.mouseDragged(event, dx, dy) : super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Page a = anak();
		return a != null ? a.mouseScrolled(mouseX, mouseY, scrollX, scrollY) : super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		Page a = anak();
		return a != null ? a.keyPressed(event) : super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		Page a = anak();
		return a != null ? a.keyReleased(event) : super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		Page a = anak();
		return a != null ? a.charTyped(event) : super.charTyped(event);
	}

	@Override
	public boolean mauTombol() {
		Page a = anak();
		return a != null ? a.mauTombol() : super.mauTombol();
	}

	@Override
	public boolean modal() {
		Page a = anak();
		return a != null ? a.modal() : super.modal();
	}

	@Override
	public boolean mundur() {
		Page a = anak();
		return a != null ? a.mundur() : super.mundur();
	}

	@Override
	public boolean fokusTab(boolean back) {
		Page a = anak();
		return a != null ? a.fokusTab(back) : super.fokusTab(back);
	}
}
