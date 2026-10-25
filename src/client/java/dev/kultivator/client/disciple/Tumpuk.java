package dev.kultivator.client.disciple;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Several subs of Murid › Murid stacked in one scroll (43.0.4: grouping inside a sub is stacked headings in one scroll,
 * never a fourth tab bar): Kekuatan = Bakat over Pusaka, Jalan = its four parts, Jalan Hidup = Ikhtisar over Kebijakan.
 * Each part keeps its own layout in a band at least as tall as the view ({@link MuridSub#tinggiMin()} when larger); the
 * wheel scrolls the stack when the part under the mouse does not use it. Mouse and keys go to the part under the mouse
 * (keys: the part last clicked); PgUp/PgDn/Home/End scroll the stack when that part does not use them. A single tall sub
 * (Status, Hati, Hubungan...) sits in a one-part stack to get the same scroll at small GUI sizes (43.0.6, 43.18.1).
 */
final class Tumpuk extends MuridSub {

	private static final int SELA = 7;
	private static final int RODA = 24;

	private final Component judul;
	private final Component pendek;
	private final String bantuan;
	private final List<MuridSub> bagian;
	private final int[] atas;
	private final int[] tinggi;
	private int total;
	private int gulir;
	private int aktif;

	Tumpuk(MuridPage murid, Component judul, Component pendek, String bantuan, List<MuridSub> bagian) {
		super(murid);
		this.judul = judul;
		this.pendek = pendek;
		this.bantuan = bantuan;
		this.bagian = bagian;
		this.atas = new int[bagian.size()];
		this.tinggi = new int[bagian.size()];
	}

	@Override
	public Component title() {
		return judul;
	}

	@Override
	public Component shortName() {
		return pendek;
	}

	@Override
	public String helpTopic() {
		return bantuan;
	}

	@Override
	protected void build() {
		int top = 0;
		for (int i = 0; i < bagian.size(); i++) {
			MuridSub part = bagian.get(i);
			if (screen != null) {
				part.pasang(screen);
			}
			atas[i] = top;
			tinggi[i] = Math.max(h, part.tinggiMin());
			top += tinggi[i] + SELA;
		}
		total = Math.max(0, top - SELA);
		gulir = Math.max(0, Math.min(gulir, total - h));
		tataBagian();
	}

	private void tataBagian() {
		for (int i = 0; i < bagian.size(); i++) {
			bagian.get(i).layout(x, y + atas[i] - gulir, w, tinggi[i]);
		}
	}

	private boolean tampak(int i) {
		int top = y + atas[i] - gulir;
		return top < y + h && top + tinggi[i] > y;
	}

	/** The part whose band holds {@code mouseY} (inside the view), or -1. */
	private int di(double mouseX, double mouseY) {
		if (!inside(mouseX, mouseY)) {
			return -1;
		}
		for (int i = 0; i < bagian.size(); i++) {
			int top = y + atas[i] - gulir;
			if (mouseY >= top && mouseY < top + tinggi[i]) {
				return i;
			}
		}
		return -1;
	}

	private @Nullable MuridSub terpilih() {
		return aktif >= 0 && aktif < bagian.size() ? bagian.get(aktif) : null;
	}

	// ================================================================== life

	@Override
	public void onView() {
		for (MuridSub part : bagian) {
			part.onData();
		}
	}

	@Override
	public boolean needsRebuild() {
		boolean any = false;
		for (MuridSub part : bagian) {
			any |= part.needsRebuild();
		}
		return any;
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		for (MuridSub part : bagian) {
			part.onShow(null);
		}
	}

	@Override
	public void onHide() {
		for (MuridSub part : bagian) {
			part.onHide();
		}
	}

	@Override
	public void tick() {
		for (MuridSub part : bagian) {
			part.tick();
		}
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		MuridSub open = terpilih();
		if (open != null && open.modal()) {
			// a part's confirm seal is centred in the part's band: bring that centre to the middle of the view
			gulirKe(atas[aktif] + tinggi[aktif] / 2 - h / 2);
		}
		boolean in = inside(mouseX, mouseY);
		g.enableScissor(x, y, x + w, y + h);
		for (int i = 0; i < bagian.size(); i++) {
			if (!tampak(i)) {
				continue;
			}
			bagian.get(i).render(g, in ? mouseX : -10000, in ? mouseY : -10000, seconds);
			if (i > 0) {
				// A gold rule with a seal dot between two parts.
				float ry = y + atas[i] - gulir - SELA / 2.0F;
				Brush.rect(g, x + 8.0F, ry, x + w - 8.0F, ry + 0.8F, Palette.argb(0xA0, Palette.GOLD));
				Brush.disc(g, x + w / 2.0F, ry + 0.4F, 2.0F, Palette.SEAL);
			}
		}
		g.disableScissor();
		if (total > h) {
			// The jade bead of the stack's scroll at the right edge.
			float bead = y + 3.0F + (h - 6.0F) * (gulir / (float) (total - h));
			Brush.disc(g, x + w - 1.5F, bead, 2.2F, Palette.JADE);
			Brush.disc(g, x + w - 2.0F, bead - 0.6F, 0.8F, Palette.JADE_PALE);
		}
		// A part that measures its lines while drawing (Jalan) may now need another height: lay the stack out again.
		for (int i = 0; i < bagian.size(); i++) {
			if (Math.max(h, bagian.get(i).tinggiMin()) != tinggi[i]) {
				build();
				break;
			}
		}
	}

	private void gulirKe(int next) {
		next = Math.max(0, Math.min(Math.max(0, total - h), next));
		if (next != gulir) {
			gulir = next;
			tataBagian();
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		MuridSub open = terpilih();
		if (open != null && open.modal()) {
			return open.mouseClicked(event, doubleClick);
		}
		int i = di(event.x(), event.y());
		if (i < 0) {
			return false;
		}
		aktif = i;
		return bagian.get(i).mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean used = false;
		for (MuridSub part : bagian) {
			used |= part.mouseReleased(event);
		}
		return used;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		MuridSub open = terpilih();
		return open != null && open.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int i = di(mouseX, mouseY);
		if (i >= 0 && bagian.get(i).mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
		if (total <= h || !inside(mouseX, mouseY)) {
			return false;
		}
		int next = Math.max(0, Math.min(total - h, gulir - (int) Math.round(scrollY * RODA)));
		if (next != gulir) {
			gulir = next;
			tataBagian();
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		MuridSub open = terpilih();
		if (open != null && open.keyPressed(event)) {
			return true;
		}
		if (total <= h) {
			return false;
		}
		int page = Math.max(RODA, h - 20);
		switch (event.key()) {
			case InputConstants.KEY_PAGEUP -> gulirKe(gulir - page);
			case InputConstants.KEY_PAGEDOWN -> gulirKe(gulir + page);
			case InputConstants.KEY_HOME -> gulirKe(0);
			case InputConstants.KEY_END -> gulirKe(total);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		MuridSub open = terpilih();
		return open != null && open.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		MuridSub open = terpilih();
		return open != null && open.charTyped(event);
	}

	@Override
	public boolean mauTombol() {
		MuridSub open = terpilih();
		return open != null && open.mauTombol();
	}

	@Override
	public boolean modal() {
		MuridSub open = terpilih();
		return open != null && open.modal();
	}

	@Override
	public boolean mundur() {
		MuridSub open = terpilih();
		return open != null && open.mundur();
	}

	@Override
	public boolean rinci() {
		MuridSub open = terpilih();
		return open != null && open.rinci();
	}

	/** Tab walks the part in view at the top (the one last clicked when it is in view). */
	@Override
	public boolean fokusTab(boolean back) {
		if (aktif < 0 || aktif >= bagian.size() || !tampak(aktif)) {
			for (int i = 0; i < bagian.size(); i++) {
				if (tampak(i)) {
					aktif = i;
					break;
				}
			}
		}
		MuridSub open = terpilih();
		return open != null && open.fokusTab(back);
	}
}
