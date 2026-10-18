package dev.kultivator.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Actions;
import dev.kultivator.technique.spec.run.Loadout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Cincin Segel tablet (DESIGN 22.12.5): twelve sockets in a ring around a dantian, previewing the seal wheel; sockets
 * 1-4 carry a gold numeral because they are also the quick seals. Click a socket and then a card of the compact list on
 * the right, or drag a card onto a socket; right click empties a socket. "Isi otomatis" fills the strongest castable art
 * per role and "Kosongkan" clears the ring (second click within 3 s). The server keeps the ring in
 * {@code extra.teknik.slots} and checks every id.
 */
final class LoadoutPage extends CodexPage {

	private static final long CONFIRM_MILLIS = 3000L;

	private final CodexGrid grid = new CodexGrid();
	private int ringX;
	private int ringY;
	private float ringR;
	private int socket;
	private int listX;
	private int armed = -1;
	private @Nullable TechniqueSpec dragging;
	private double dragFromX;
	private double dragFromY;
	private boolean dragMoved;
	private int mouseX;
	private int mouseY;
	private long clearAskUntil;
	private long builtVersion = -1L;

	LoadoutPage(CodexScreen screen) {
		super(screen);
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.codex.tab.ring");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.codex.tab.ring.short");
	}

	@Override
	int icon() {
		return Glyphs.RING;
	}

	@Override
	protected void build() {
		int leftW = Math.max(120, Math.min(w - 90, Math.round(w * 0.56F)));
		int buttonH = 14;
		ringX = x + leftW / 2;
		ringY = y + (h - buttonH - 6) / 2 + 2;
		ringR = Math.max(34.0F, Math.min(leftW, h - buttonH - 14) / 2.0F - 12.0F);
		socket = Math.max(14, Math.min(20, Math.round(ringR * 0.36F)));
		int bw = (leftW - 6) / 2;
		TalismanButton autofill = add(new TalismanButton(x, y + h - buttonH, bw, buttonH, () -> Component.translatable("kultivator.codex.ring.autofill")));
		autofill.enabledWhen(Keys::connected);
		autofill.onPress(() -> send(Actions.TECHNIQUE, 0, "autofill"));
		autofill.tooltip(() -> List.of(Component.translatable("kultivator.codex.ring.autofill"),
				Component.translatable("kultivator.codex.ring.autofill.tip").withStyle(ChatFormatting.GRAY)));
		TalismanButton clear = add(new TalismanButton(x + bw + 6, y + h - buttonH, bw, buttonH, () -> Component.translatable(asking()
				? "kultivator.codex.ring.clear_confirm" : "kultivator.codex.ring.clear")));
		clear.enabledWhen(Keys::connected);
		clear.urgentWhen(this::asking);
		clear.onPress(() -> {
			if (asking()) {
				clearAskUntil = 0L;
				send(Actions.TECHNIQUE, 0, "clear");
			} else {
				clearAskUntil = Util.getMillis() + CONFIRM_MILLIS;
				UiSound.ask();
			}
		});
		clear.tooltip(() -> List.of(Component.translatable("kultivator.codex.ring.clear"),
				Component.translatable("kultivator.codex.ring.clear.tip").withStyle(ChatFormatting.GRAY)));
		int gx = x + leftW + 6;
		listX = gx;
		grid.layout(gx, y + 12, x + w - gx, h - 12, 26, 32);
		refresh();
	}

	private boolean asking() {
		return Util.getMillis() < clearAskUntil;
	}

	private void refresh() {
		grid.setSpecs(SpecInfo.castables(data()), false);
		builtVersion = ClientCultivation.version();
	}

	@Override
	void onData() {
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
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		CultivationData data = data();
		String[] slots = TeknikSession.slots();
		// The dantian.
		float core = Math.max(12.0F, ringR * 0.42F);
		Brush.disc(g, ringX, ringY, ringR + socket / 2.0F + 3.0F, Palette.argb(0x30, Palette.PAPER_DEEP));
		Brush.ring(g, ringX, ringY, ringR - 0.6F, ringR + 0.6F, Palette.argb(0x90, Palette.GOLD));
		Brush.disc(g, ringX, ringY, core, Palette.argb(0xB0, Palette.NIGHT_MIST));
		Brush.ring(g, ringX, ringY, core - 1.0F, core, Palette.GOLD);
		Brush.ring(g, ringX, ringY, core * 0.55F, core * 0.55F + 0.8F, Palette.argb(0xA0, Palette.GOLD_LIGHT));
		Brush.fitCentered(g, font, Brush.bold(Component.translatable("kultivator.codex.ring.core")), ringX, ringY - 7.0F, core * 1.8F, Palette.GOLD_LIGHT,
				0.6F, 0.4F, false);
		Component used = Component.literal(filled(slots) + "/" + Loadout.SLOTS);
		Brush.centered(g, font, used, ringX, ringY + 2.0F, Palette.JADE_PALE, 0.6F, false);

		int hoveredSocket = socketAt(mouseX, mouseY);
		for (int i = 0; i < Loadout.SLOTS; i++) {
			drawSocket(g, data, i, slots[i], i == hoveredSocket, i == armed);
		}
		if (hoveredSocket >= 0 && dragging == null) {
			screen.showTooltip(socketTip(data, hoveredSocket, slots[hoveredSocket]));
		}

		heading(g, Component.translatable("kultivator.codex.ring.list"), listX, y,
				Math.max(40.0F, x + w - listX - 2.0F), 0.7F);
		grid.render(g, font, dragging != null ? -1 : mouseX, dragging != null ? -1 : mouseY, seconds,
				spec -> KodeksPage.look(data, spec, slots, data.selectedId()));
		if (grid.specs().isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.codex.ring.none"), listX + 4.0F, y + 18.0F, x + w - listX - 8.0F,
					Palette.INK_SOFT, 0.65F, false);
		}
		TechniqueSpec hovered = grid.hovered();
		if (hovered != null && dragging == null) {
			List<Component> tip = new ArrayList<>(KodeksPage.tooltip(data, hovered));
			tip.set(tip.size() - 1, Component.translatable("kultivator.codex.ring.drag").withStyle(ChatFormatting.DARK_GRAY));
			screen.showTooltip(tip);
		}
		if (armed >= 0) {
			Brush.fitCentered(g, font, Component.translatable("kultivator.codex.ring.armed", armed + 1), ringX, y + h - 26.0F, ringR * 2.0F,
					Palette.SEAL_DARK, 0.6F, 0.4F, false);
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
		if (isArmed) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 160.0D);
			Brush.halo(g, cx, cy, r + 1.0F, 3.0F, 0xFF5B3A, pulse);
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
		float nx = cx + (float) Math.cos(angle) * (r + 5.0F);
		float ny = cy + (float) Math.sin(angle) * (r + 5.0F);
		Brush.centered(g, font, Component.literal(String.valueOf(index + 1)), nx, ny - 2.5F, index < 4 ? Palette.GOLD_DARK : Palette.INK_FAINT,
				index < 4 ? 0.6F : 0.5F, false);
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

	@Override
	protected boolean clickedElsewhere(double mouseX, double mouseY, int button) {
		int index = socketAt(mouseX, mouseY);
		if (index >= 0) {
			if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
				assign(index, null);
				armed = -1;
			} else {
				armed = armed == index ? -1 : index;
				UiSound.tab();
			}
			return true;
		}
		TechniqueSpec spec = grid.at(mouseX, mouseY);
		if (spec != null && button == InputConstants.MOUSE_BUTTON_LEFT) {
			if (armed >= 0) {
				assign(armed, spec);
				armed = (armed + 1) % Loadout.SLOTS;
				return true;
			}
			dragging = spec;
			dragFromX = mouseX;
			dragFromY = mouseY;
			dragMoved = false;
			return true;
		}
		armed = -1;
		return false;
	}

	@Override
	boolean mouseDragged(double mouseX, double mouseY, int button) {
		if (dragging != null && !dragMoved && Math.abs(mouseX - dragFromX) + Math.abs(mouseY - dragFromY) > 3.0D) {
			dragMoved = true;
		}
		return dragging != null;
	}

	@Override
	protected boolean releasedElsewhere(double mouseX, double mouseY, int button) {
		if (dragging == null) {
			return false;
		}
		TechniqueSpec spec = dragging;
		dragging = null;
		int index = socketAt(mouseX, mouseY);
		if (index >= 0) {
			assign(index, spec);
		} else if (!dragMoved) {
			// A plain click on a card fills the first empty socket.
			String[] slots = TeknikSession.slots();
			for (int i = 0; i < slots.length; i++) {
				if (slots[i].isEmpty()) {
					assign(i, spec);
					break;
				}
			}
		}
		return true;
	}

	@Override
	boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return grid.scrolled(mouseX, mouseY, amount);
	}

	@Override
	boolean escape() {
		if (armed >= 0 || dragging != null) {
			armed = -1;
			dragging = null;
			return true;
		}
		return false;
	}
}
