package dev.kultivator.client.codex;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.Keys;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Roda Segel (DESIGN 22.12.7): the 12 loadout seals (or the first 12 castable arts by grade while the ring is empty)
 * around the crosshair, radius 56 GUI px (46 under 260 px tall). The mouse moves a virtual pointer instead of the camera
 * ({@code MouseHandlerMixin}), the wheel steps around the ring, WASD keeps working. Each seal shows its glyph, grade
 * ribbon, scaled qi, a cooldown sweep and greys out when unaffordable; the centre shows the hovered name, qi, cooldown,
 * mastery and one line of description. Release selects (or casts with "Lepas = rapal"); Escape or right click closes
 * without choosing; 1-4 pins the hovered seal to quick seal 1-4 (saved on the server). It never opens together with the
 * Akses Cepat wheel ({@code client.hub.QuickWheel}) nor during a camera shot (DESIGN 33.3.1).
 */
public final class SealWheel {

	public static final long HOLD_MILLIS = 250L;
	private static final double DEADZONE = 9.0D;

	private static boolean open;
	private static boolean fromCast;
	private static long openedAt;
	private static double pointerX;
	private static double pointerY;
	private static int hovered = -1;
	private static final List<TechniqueSpec> SEALS = new ArrayList<>();

	public static boolean isOpen() {
		return open;
	}

	/** True when the cast key opened it (its release then chooses). */
	public static boolean fromCast() {
		return fromCast;
	}

	public static void open(boolean byCastKey) {
		// UI/UX Terpadu (DESIGN 33.3.1): never together with the Akses Cepat wheel, never during a camera shot (7b).
		if (dev.kultivator.client.hub.QuickWheel.isOpen() || dev.kultivator.client.epic.camera.CinematicDirector.active()) {
			return;
		}
		SEALS.clear();
		List<TechniqueSpec> ring = TeknikSession.ring();
		for (int i = 0; i < Math.min(12, ring.size()); i++) {
			SEALS.add(ring.get(i));
		}
		if (SEALS.isEmpty()) {
			return;
		}
		open = true;
		fromCast = byCastKey;
		openedAt = Util.getMillis();
		pointerX = 0.0D;
		pointerY = 0.0D;
		hovered = -1;
		String selected = ClientCultivation.data().selectedId();
		for (int i = 0; i < SEALS.size(); i++) {
			if (SEALS.get(i).id().equals(selected)) {
				hovered = i;
				aimAt(i);
			}
		}
		UiSound.tab();
	}

	/** Closes; {@code choose} selects (or casts) the hovered seal. */
	public static void close(boolean choose) {
		if (!open) {
			return;
		}
		open = false;
		TechniqueSpec spec = hoveredSpec();
		if (choose && spec != null) {
			if (EffectQuality.releaseCast()) {
				Keys.send(new Payloads.Action(Actions.CAST, 0, spec.id()));
			} else if (!spec.id().equals(ClientCultivation.data().selectedId())) {
				Keys.send(new Payloads.Action(Actions.SELECT_TECHNIQUE, 0, spec.id()));
			}
			UiSound.press();
		}
		SEALS.clear();
		hovered = -1;
	}

	/** Pins the hovered seal to loadout slot {@code slot} (0-3). */
	public static void pin(int slot) {
		TechniqueSpec spec = hoveredSpec();
		if (!open || spec == null) {
			return;
		}
		Keys.send(new Payloads.Action(Actions.TECHNIQUE, 0, "slot:" + slot + ":" + spec.id()));
		UiSound.toggle(true);
		CodexHud.toast(Component.translatable("kultivator.codex.wheel.pinned", spec.displayName(), slot + 1), spec);
	}

	public static @Nullable TechniqueSpec hoveredSpec() {
		return hovered >= 0 && hovered < SEALS.size() ? SEALS.get(hovered) : null;
	}

	/** Mouse movement while open: moves the pointer (in GUI-ish pixels) and picks the sector under it. */
	static boolean input(double dx, double dy) {
		if (!open) {
			return false;
		}
		pointerX += dx * 0.35D;
		pointerY += dy * 0.35D;
		double length = Math.sqrt(pointerX * pointerX + pointerY * pointerY);
		double max = radius() * 1.1D;
		if (length > max) {
			pointerX *= max / length;
			pointerY *= max / length;
			length = max;
		}
		if (length > DEADZONE && !SEALS.isEmpty()) {
			double angle = Math.atan2(pointerY, pointerX) + Math.PI / 2.0D;
			int n = SEALS.size();
			int index = (int) Math.floor((angle / (Math.PI * 2.0D) * n) + 0.5D);
			index = Math.floorMod(index, n);
			if (index != hovered) {
				hovered = index;
				UiSound.hover();
			}
		}
		return true;
	}

	/** The mouse wheel while open: one seal around the ring. */
	static boolean scroll(double amount) {
		if (!open || SEALS.isEmpty() || amount == 0.0D) {
			return open;
		}
		int n = SEALS.size();
		hovered = Math.floorMod((hovered < 0 ? 0 : hovered) + (amount > 0.0D ? -1 : 1), n);
		aimAt(hovered);
		UiSound.hover();
		return true;
	}

	private static void aimAt(int index) {
		double angle = -Math.PI / 2.0D + Math.PI * 2.0D * index / Math.max(1, SEALS.size());
		pointerX = Math.cos(angle) * radius() * 0.8D;
		pointerY = Math.sin(angle) * radius() * 0.8D;
	}

	private static int screenHeight = 240;

	private static double radius() {
		return screenHeight < 260 ? 46.0D : 56.0D;
	}

	// ================================================================== drawing

	static void render(GuiGraphicsExtractor g, Font font, float partialTick) {
		if (!open || SEALS.isEmpty()) {
			return;
		}
		screenHeight = g.guiHeight();
		CultivationData data = ClientCultivation.data();
		float cx = g.guiWidth() / 2.0F;
		float cy = g.guiHeight() / 2.0F;
		float r = (float) radius();
		float grow = Math.min(1.0F, (Util.getMillis() - openedAt) / 120.0F);
		float ring = r * (0.7F + 0.3F * grow);
		int n = SEALS.size();
		Brush.disc(g, cx, cy, ring + 14.0F, Palette.argb(Math.round(0x70 * grow), Palette.NIGHT));
		Brush.ring(g, cx, cy, ring - 0.5F, ring + 0.5F, Palette.argb(0xA0, Palette.GOLD));
		float tile = r < 50.0F ? 15.0F : 17.0F;
		for (int i = 0; i < n; i++) {
			TechniqueSpec spec = SEALS.get(i);
			double angle = -Math.PI / 2.0D + Math.PI * 2.0D * i / n;
			float sx = cx + (float) Math.cos(angle) * ring;
			float sy = cy + (float) Math.sin(angle) * ring;
			drawSeal(g, font, data, spec, sx, sy, tile, i == hovered, i);
		}
		// The pointer.
		Brush.dot(g, cx + (float) pointerX, cy + (float) pointerY, 1.4F, Palette.GOLD_LIGHT);
		// The centre: hovered name, qi, cooldown, mastery, one line of description.
		float inner = ring - tile / 2.0F - 3.0F;
		Brush.disc(g, cx, cy, inner, Palette.argb(0xD0, 0x101824));
		Brush.ring(g, cx, cy, inner - 0.8F, inner, Palette.GOLD_DARK);
		TechniqueSpec spec = hoveredSpec();
		if (spec == null) {
			Brush.fitCentered(g, font, Component.translatable("kultivator.codex.wheel.hint"), cx, cy - 3.0F, inner * 1.7F, Palette.JADE_PALE, 0.6F, 0.4F,
					true);
			return;
		}
		float room = inner * 1.75F;
		Brush.fitCentered(g, font, Brush.bold(spec.displayName()), cx, cy - inner * 0.55F, room, Palette.opaque(spec.dao() == null ? 0xFFE6B3
				: spec.dao().color()), 0.7F, 0.4F, true);
		Component costs = Component.translatable("kultivator.codex.wheel.costs", SpecInfo.number(SpecInfo.qi(data, spec)),
				SpecInfo.seconds(SpecInfo.cooldown(data, spec)));
		Brush.fitCentered(g, font, costs, cx, cy - inner * 0.2F, room, Palette.QI_LIGHT, 0.6F, 0.4F, true);
		Brush.fitCentered(g, font, SpecInfo.masteryName(SpecInfo.mastery(data, spec)), cx, cy + inner * 0.08F, room, Palette.GOLD, 0.55F, 0.4F, true);
		List<FormattedCharSequence> desc = font.split(spec.description(), Math.max(20, (int) (room / 0.5F)));
		if (!desc.isEmpty()) {
			FormattedCharSequence line = desc.get(0);
			float scale = Math.min(0.5F, room / Math.max(1, font.width(line)));
			Brush.text(g, font, line, cx - font.width(line) * scale / 2.0F, cy + inner * 0.38F, Palette.JADE_PALE, scale, true);
		}
	}

	private static void drawSeal(GuiGraphicsExtractor g, Font font, CultivationData data, TechniqueSpec spec, float cx, float cy, float size,
			boolean hover, int index) {
		boolean affordable = SpecInfo.affordable(data, spec);
		float half = size / 2.0F;
		int x0 = Math.round(cx - half);
		int y0 = Math.round(cy - half);
		int x1 = Math.round(cx + half);
		int y1 = Math.round(cy + half);
		if (hover) {
			Brush.halo(g, cx, cy, half + 1.0F, 3.0F, 0xFFE07A, 0.9F);
		}
		int paper = affordable ? Palette.TALISMAN : Palette.grey(Palette.TALISMAN, 0.7F);
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xC0101824);
		g.fill(x0, y0, x1, y1, hover ? Palette.lighten(paper, 0.15F) : paper);
		long left = SpecInfo.cooldownLeft(spec);
		int total = SpecInfo.cooldown(data, spec);
		if (left > 0L && total > 0) {
			g.enableScissor(x0, y0, x1, y1);
			Brush.pie(g, cx, cy, size * 0.75F, 1.0F - Math.min(1.0F, left / (float) total), 1.0F, Palette.mix(paper, 0xFF1A2230, 0.6F));
			g.disableScissor();
		}
		Brush.frame(g, x0, y0, x1, y1, affordable ? Palette.SEAL : 0xFF6A6A6A);
		int ribbon = SpecInfo.ribbon(spec.grade());
		for (int i = 0; i < 4; i++) {
			g.fill(x1 - 4 + i, y0 + i, x1, y0 + i + 1, ribbon);
		}
		Glyphs.spec(g, spec, cx, cy, size * 0.58F, affordable ? SpecInfo.ink(spec) : 0xFF6A6A6A);
		Component cost = Component.literal(SpecInfo.compact(SpecInfo.qi(data, spec)));
		Brush.centered(g, font, cost, cx, y1 + 1.5F, affordable ? Palette.QI_LIGHT : 0xFFFF7A6A, 0.5F, true);
		String[] slots = TeknikSession.slots();
		if (index < 4 && index < slots.length && spec.id().equals(slots[index])) {
			Brush.text(g, font, Component.literal(String.valueOf(index + 1)), x0 + 1.0F, y0 + 1.0F, Palette.GOLD_DARK, 0.45F, false);
		}
	}

	private SealWheel() {
	}
}
