package dev.kultivator.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Actions;
import dev.kultivator.technique.spec.run.Loadout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Teknik › Cincin Segel (DESIGN 22.12.5, 43.5, G48; a wide page and the one GUI that fills the ring): twelve sockets in a
 * ring around a dantian, previewing the seal wheel; sockets 1-4 carry a gold numeral because they are also the quick
 * seals. Click (or Tab + Enter) a socket and then a card of the compact list on the right, or drag a card onto a socket;
 * right click empties a socket. "Pasang ke Cincin" in the Kodeks slip arrives here with its card carried: the next socket
 * takes it. "Isi otomatis" fills the strongest castable art per role and "Kosongkan" clears the ring after a light
 * {@code SegelKonfirmasi}. The server keeps the ring in {@code extra.teknik.slots} and checks every id.
 * Keyboard (43.4.2): sockets are Tab stops (Enter arms, Delete/Backspace empties); the card list is one Tab stop ("kisi")
 * whose arrows move the selected card and whose Enter puts it in the armed socket, else the first empty one.
 */
public final class LoadoutPage extends Page {

	private static final String M = "kultivator.gui43.m02.";
	private static final String KISI = "kisi";
	/** A card carried over from the Kodeks slip ({@link Codex#pasang}); picked up when the page shows. */
	private static @Nullable TechniqueSpec dibawa;

	private final CodexGrid grid = new CodexGrid();
	private int ringX;
	private int ringY;
	private float ringR;
	private int socket;
	private int listX;
	private int hintY;
	private int armed = -1;
	private @Nullable TechniqueSpec carry;
	private @Nullable TechniqueSpec dragging;
	private double dragFromX;
	private double dragFromY;
	private boolean dragMoved;
	private long builtVersion = -1L;

	public LoadoutPage() {
	}

	/** Carries {@code spec} to the ring: the next socket clicked takes it (null clears). */
	static void bawa(@Nullable TechniqueSpec spec) {
		dibawa = spec;
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.teknik.cincin_segel");
	}

	@Override
	public int glyph() {
		return Glyphs.RING;
	}

	@Override
	public String helpTopic() {
		return "kitab_teknik";
	}

	@Override
	public boolean lebar() {
		return true;
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		ambil();
		super.onShow(fokus);
	}

	@Override
	public void tick() {
		ambil();
	}

	private void ambil() {
		if (dibawa != null) {
			carry = dibawa;
			dibawa = null;
			armed = -1;
		}
	}

	@Override
	protected void build() {
		int leftW = Math.max(120, Math.min(w - 90, Math.round(w * 0.56F)));
		int buttonH = 14;
		// The ring keeps clear of the hint line above the talismans: its slot numbers sit socket/2 + 11 outside the ring.
		hintY = y + h - buttonH - 12;
		int ringBox = hintY - y - 1;
		float half = Math.min(leftW, ringBox) / 2.0F;
		socket = Math.max(14, Math.min(20, Math.round((half - 21.0F) * 0.36F)));
		ringR = Math.max(30.0F, half - socket / 2.0F - 11.0F);
		ringX = x + leftW / 2;
		ringY = y + ringBox / 2;
		int bw = (leftW - 6) / 2;
		TalismanButton autofill = add(new TalismanButton(x, y + h - buttonH, bw, buttonH, () -> Component.translatable("kultivator.codex.ring.autofill")));
		autofill.enabledWhen(Keys::connected);
		autofill.alasanWhen(() -> Component.translatable(M + "luring"));
		autofill.onPress(() -> send(Actions.TECHNIQUE, 0, "autofill"));
		autofill.tooltip(() -> List.of(Component.translatable("kultivator.codex.ring.autofill"),
				Component.translatable("kultivator.codex.ring.autofill.tip").withStyle(ChatFormatting.GRAY)));
		TalismanButton clear = add(new TalismanButton(x + bw + 6, y + h - buttonH, bw, buttonH, () -> Component.translatable("kultivator.codex.ring.clear")));
		clear.enabledWhen(Keys::connected);
		clear.alasanWhen(() -> Component.translatable(M + "luring"));
		clear.onPress(() -> konfirmasi(SegelKonfirmasi.Tingkat.RINGAN, Component.translatable(M + "kosongkan.tanya"),
				() -> send(Actions.TECHNIQUE, 0, "clear")));
		clear.tooltip(() -> List.of(Component.translatable("kultivator.codex.ring.clear"),
				Component.translatable(M + "kosongkan.tip").withStyle(ChatFormatting.GRAY)));
		int gx = x + leftW + 6;
		listX = gx;
		grid.layout(gx, y + 13, x + w - gx, h - 13, 26, 32);
		refresh();
	}

	private void refresh() {
		grid.setSpecs(SpecInfo.castables(data()), false);
		builtVersion = ClientCultivation.version();
	}

	@Override
	public void onData() {
		if (ClientCultivation.version() != builtVersion) {
			refresh();
		}
	}

	private float socketX(int index) {
		double angle = -Math.PI / 2.0D + Math.PI * 2.0D * index / Loadout.SLOTS;
		return ringX + (float) Math.cos(angle) * ringR;
	}

	private float socketY(int index) {
		double angle = -Math.PI / 2.0D + Math.PI * 2.0D * index / Loadout.SLOTS;
		return ringY + (float) Math.sin(angle) * ringR;
	}

	private int socketAt(double mx, double my) {
		for (int i = 0; i < Loadout.SLOTS; i++) {
			double dx = mx - socketX(i);
			double dy = my - socketY(i);
			if (dx * dx + dy * dy <= (socket / 2.0D + 1.5D) * (socket / 2.0D + 1.5D)) {
				return i;
			}
		}
		return -1;
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		GuiGraphicsExtractor g = l.g;
		CultivationData data = data();
		String[] slots = TeknikSession.slots();
		// The dantian.
		float core = Math.max(12.0F, ringR * 0.42F);
		Brush.disc(g, ringX, ringY, ringR + socket / 2.0F + 3.0F, Palette.argb(0x30, Palette.PAPER_DEEP));
		Brush.ring(g, ringX, ringY, ringR - 0.6F, ringR + 0.6F, Palette.argb(0x90, Palette.GOLD));
		Brush.disc(g, ringX, ringY, core, Palette.argb(0xB0, Palette.NIGHT_MIST));
		Brush.ring(g, ringX, ringY, core - 1.0F, core, Palette.GOLD);
		Brush.ring(g, ringX, ringY, core * 0.55F, core * 0.55F + 0.8F, Palette.argb(0xA0, Palette.GOLD_LIGHT));
		Component coreName = Brush.bold(Component.translatable("kultivator.codex.ring.core"));
		if (font.width(coreName) <= core * 1.8F) {
			Brush.centered(g, font, coreName, ringX, ringY - 9.0F, Palette.GOLD_LIGHT, 1.0F, false);
		}
		Component used = Component.literal(filled(slots) + "/" + Loadout.SLOTS);
		Brush.centered(g, font, used, ringX, ringY + 1.0F, Palette.JADE_PALE, 1.0F, false);

		boolean free = dragging == null;
		for (int i = 0; i < Loadout.SLOTS; i++) {
			int index = i;
			float cx = socketX(i);
			float cy = socketY(i);
			int r = socket / 2 + 1;
			int x0 = Math.round(cx) - r;
			int y0 = Math.round(cy) - r;
			Lukis.Zona zona = l.zona("soket." + i, x0, y0, x0 + r * 2, y0 + r * 2, () -> pressSocket(index), true, null, true);
			if (zona != null) {
				zona.kanan = () -> {
					assign(index, null);
					armed = -1;
				};
			}
			boolean over = free && socketAt(l.mx, l.my) == i;
			drawSocket(g, data, i, slots[i], over || l.difokus("soket." + i), i == armed);
			if (over) {
				l.tip(socketTip(data, i, slots[i]));
			}
		}

		heading(g, Component.translatable("kultivator.codex.ring.list"), listX, y, Math.max(40.0F, x + w - listX - 2.0F), 1.0F);
		grid.render(g, font, free ? l.mx : -1, free ? l.my : -1, l.detik, spec -> KodeksPage.look(data, spec, slots, data.selectedId()));
		if (grid.specs().isEmpty()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.NAMPAN, Component.translatable("kultivator.codex.ring.none"),
					Component.translatable("kultivator.gui43.p2.ke_kodeks"), () -> HubNav.buka(Tujuan.of(HubNav.TEKNIK, Urutan.ID_KODEKS)), listX, y + 13,
					x + w, y + h);
		} else {
			Lukis.Zona kisi = l.zona(KISI, listX, y + 13, x + w, y + h, this::pakaiTerpilih, true, null, true);
			if (kisi != null) {
				kisi.geser = arah -> grid.tombol(arah > 0 ? InputConstants.KEY_RIGHT : InputConstants.KEY_LEFT);
			}
			int[] box = grid.kotakTerpilih();
			if (box != null) {
				l.fokusCincin(KISI, box[0], box[1], box[2], box[3]);
			}
		}
		TechniqueSpec hovered = grid.hovered();
		if (hovered != null && free) {
			List<Component> tip = new ArrayList<>(KodeksPage.tooltip(data, hovered));
			tip.set(tip.size() - 1, Component.translatable("kultivator.codex.ring.drag").withStyle(ChatFormatting.DARK_GRAY));
			l.tip(tip);
		}
		Component hint = carry != null ? Component.translatable(M + "bawa", carry.displayName())
				: armed >= 0 ? Component.translatable("kultivator.codex.ring.armed", armed + 1) : null;
		if (hint != null) {
			Brush.fitCentered(g, font, hint, ringX, hintY + 1.0F, listX - x - 6.0F, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		}
	}

	@Override
	protected void drawAbove(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (dragging != null && dragMoved) {
			g.nextStratum();
			int size = 22;
			SealCard.paint(g, font, dragging, mouseX - size / 2, mouseY - size / 2, size,
					new SealCard.Look(true, false, true, 0L, 0, SpecInfo.mastery(data(), dragging), -1, false, false), 1.0F);
		}
	}

	private static int filled(String[] slots) {
		int count = 0;
		for (String id : slots) {
			if (!id.isEmpty()) {
				count++;
			}
		}
		return count;
	}

	private void drawSocket(GuiGraphicsExtractor g, CultivationData data, int index, String id, boolean over, boolean isArmed) {
		float cx = socketX(index);
		float cy = socketY(index);
		float r = socket / 2.0F;
		TechniqueSpec spec = id.isEmpty() ? null : TechniqueRegistry.byId(id);
		boolean castable = spec != null && SpecInfo.castable(data, spec);
		if (isArmed || carry != null) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 160.0D);
			Brush.halo(g, cx, cy, r + 1.0F, 3.0F, 0xFF5B3A, isArmed ? pulse : pulse * 0.5F);
		} else if (over) {
			Brush.halo(g, cx, cy, r + 1.0F, 2.5F, 0xFFE07A, 0.8F);
		}
		Brush.disc(g, cx + 0.5F, cy + 1.0F, r, 0x40000000);
		Brush.disc(g, cx, cy, r, index < 4 ? Palette.GOLD : Palette.GOLD_DARK);
		Brush.disc(g, cx, cy, r - 1.2F, spec == null ? Palette.argb(0xFF, 0xCADBC4) : castable ? 0xFFF4E6B8 : Palette.LOCKED_PAPER);
		if (spec != null) {
			long left = SpecInfo.cooldownLeft(spec);
			int total = SpecInfo.cooldown(data, spec);
			if (left > 0L && total > 0) {
				Brush.pie(g, cx, cy, r - 1.2F, 1.0F - Math.min(1.0F, left / (float) total), 1.0F, 0xFF9FA8B0);
			}
			Glyphs.spec(g, spec, cx, cy, socket * 0.55F, castable ? SpecInfo.ink(spec) : Palette.INK_FAINT);
			Brush.arc(g, cx, cy, r - 1.2F, r - 0.4F, 0.0F, 0.25F, SpecInfo.ribbon(spec.grade()));
		}
		// The slot number just outside the socket (gold for the quick seals 1-4).
		double angle = -Math.PI / 2.0D + Math.PI * 2.0D * index / Loadout.SLOTS;
		float nx = cx + (float) Math.cos(angle) * (r + 7.0F);
		float ny = cy + (float) Math.sin(angle) * (r + 7.0F);
		Brush.centered(g, font, Component.literal(String.valueOf(index + 1)), nx, ny - 4.0F, index < 4 ? Palette.GOLD_DARK : Palette.INK_FAINT,
				1.0F, false);
	}

	private List<Component> socketTip(CultivationData data, int index, String id) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(index < 4 ? "kultivator.codex.ring.slot_quick" : "kultivator.codex.ring.slot", index + 1));
		TechniqueSpec spec = id.isEmpty() ? null : TechniqueRegistry.byId(id);
		if (spec == null) {
			lines.add(Component.translatable("kultivator.codex.ring.empty").withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("kultivator.codex.ring.empty.tip").withStyle(ChatFormatting.DARK_GRAY));
			return lines;
		}
		lines.add(spec.displayName().copy().withColor(spec.dao() == null ? 0xFFE6B3 : spec.dao().color()));
		lines.add(Component.translatable("kultivator.codex.detail.costs", SpecInfo.number(SpecInfo.qi(data, spec)),
				SpecInfo.seconds(SpecInfo.cooldown(data, spec))).withColor(0x8FD0FF));
		if (!SpecInfo.castable(data, spec)) {
			Component reason = SpecInfo.lockReason(data, spec);
			lines.add((reason == null ? Component.translatable("kultivator.codex.ring.not_castable") : reason).copy().withStyle(ChatFormatting.RED));
		}
		lines.add(Component.translatable("kultivator.codex.ring.socket.tip").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	// ================================================================== input

	private void assign(int index, @Nullable TechniqueSpec spec) {
		UiSound.press();
		send(Actions.TECHNIQUE, 0, "slot:" + index + ":" + (spec == null ? "-" : spec.id()));
	}

	/** A socket clicked (or Enter on it): the carried card goes in, else the socket is armed / disarmed. */
	private void pressSocket(int index) {
		if (carry != null) {
			assign(index, carry);
			carry = null;
			return;
		}
		armed = armed == index ? -1 : index;
	}

	/** Enter on the card list: the selected card into the armed socket (then the next one), else into the first empty socket. */
	private void pakaiTerpilih() {
		TechniqueSpec spec = grid.selected();
		if (spec == null) {
			grid.tombol(InputConstants.KEY_HOME);
			return;
		}
		if (armed >= 0) {
			assign(armed, spec);
			armed = (armed + 1) % Loadout.SLOTS;
		} else {
			isiKosong(spec);
		}
	}

	/** {@code spec} into the first empty socket; a full ring says so in the footer. */
	private void isiKosong(TechniqueSpec spec) {
		String[] slots = TeknikSession.slots();
		for (int i = 0; i < slots.length; i++) {
			if (slots[i].isEmpty()) {
				assign(i, spec);
				return;
			}
		}
		UiSound.denied();
		pesan(Component.translatable("kultivator.gui43.p2.cincin_penuh"), Palette.WARN);
	}

	private static boolean vertikal(int key) {
		return key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN || key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN
				|| key == InputConstants.KEY_HOME || key == InputConstants.KEY_END;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		String focused = fokusRing.kunci();
		if (!fokusRing.mauTombol() && focused != null && focused.startsWith("soket.")
				&& (key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE)) {
			int index = Integer.parseInt(focused.substring("soket.".length()));
			assign(index, null);
			armed = -1;
			return true;
		}
		if (!fokusRing.mauTombol() && vertikal(key) && !grid.specs().isEmpty()) {
			if (KISI.equals(focused)) {
				grid.tombol(key);
				return true;
			}
			if (focused == null && (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN)) {
				fokusRing.set(KISI);
				if (grid.selected() == null) {
					grid.tombol(InputConstants.KEY_HOME);
				}
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// The card list before the focus ring: its "kisi" zone covers the cards.
		boolean onList = !modal() && !grid.specs().isEmpty() && grid.contains(event.x(), event.y());
		if (!onList && super.mouseClicked(event, doubleClick)) {
			return true;
		}
		TechniqueSpec spec = grid.at(event.x(), event.y());
		if (onList) {
			fokusRing.set(KISI);
		}
		if (spec != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			grid.select(spec);
			if (armed >= 0) {
				assign(armed, spec);
				armed = (armed + 1) % Loadout.SLOTS;
				return true;
			}
			dragging = spec;
			dragFromX = event.x();
			dragFromY = event.y();
			dragMoved = false;
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			armed = -1;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging != null) {
			if (!dragMoved && Math.abs(event.x() - dragFromX) + Math.abs(event.y() - dragFromY) > 3.0D) {
				dragMoved = true;
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging == null) {
			return super.mouseReleased(event);
		}
		TechniqueSpec spec = dragging;
		dragging = null;
		int index = socketAt(event.x(), event.y());
		if (index >= 0) {
			assign(index, spec);
		} else if (!dragMoved) {
			// A plain click on a card fills the first empty socket.
			isiKosong(spec);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return grid.scrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mundur() {
		if (armed >= 0 || dragging != null || carry != null) {
			armed = -1;
			dragging = null;
			carry = null;
			return true;
		}
		return false;
	}
}
