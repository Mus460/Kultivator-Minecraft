package dev.kultivator.client.hub;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.Keys;
import dev.kultivator.client.codex.SealWheel;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.CultivationData;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Akses Cepat (DESIGN 33.3.1): one key beside WASD, V by default. A tap (under 250 ms) opens the Gulungan Utama on its
 * last section; holding it opens this wheel around the crosshair, drawn as a HUD element (GUI fills only, no new
 * rendering). The mouse moves a virtual pointer instead of the camera ({@code MouseHandlerMixin}), WASD keeps working.
 * Releasing the key on a slice chooses it, releasing in the centre opens the hub, right click (use) or Esc cancels, the
 * hotbar keys 1-8 choose a slice directly, the attack key chooses the hovered slice. Eight slices, clockwise from the
 * top: Meditasi, Pedang Terbang, Kitab Teknik, Murid, Cincin, Dunia, Keabadian, Kitab Panduan.
 * <p>
 * The wheel and the Roda Segel are never open together; the wheel never opens while a screen is open, while a camera
 * shot runs (7b) or while the tribulation HUD is live (danger). The hub (a tap) never opens during a camera shot.
 */
public final class QuickWheel implements HudElement {

	public static final long HOLD_MILLIS = 250L;
	public static final int SLICES = 8;
	public static final int MEDITASI = 0;
	public static final int PEDANG = 1;
	public static final int KITAB_TEKNIK = 2;
	public static final int MURID = 3;
	public static final int CINCIN = 4;
	public static final int DUNIA = 5;
	public static final int KEABADIAN = 6;
	public static final int PANDUAN = 7;

	private static final String[] NAMES = {"meditasi", "pedang", "kodeks", "murid", "cincin", "dunia", "keabadian", "panduan"};
	private static final int[] GLYPHS = {Glyphs.STATUS, Glyphs.TECHNIQUES, Glyphs.CODEX, Glyphs.TALENT, Glyphs.RING, Glyphs.PATH, Glyphs.SEAL,
			Glyphs.HELP};
	private static final double DEADZONE = 10.0D;
	/** After the quick key closed a screen, its own release must not count as a new tap. */
	private static final long SUPPRESS_MILLIS = 350L;

	private static boolean open;
	private static boolean held;
	private static long pressedAt;
	private static long openedAt;
	private static long suppressUntil;
	private static boolean warned;
	private static double pointerX;
	private static double pointerY;
	private static int hovered = -1;
	private static int screenHeight = 240;

	/** Registers the tick hooks and the HUD element (called once from {@code KultivatorClient}). */
	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(QuickWheel::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(QuickWheel::endTick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("quick_wheel"), new QuickWheel());
		ClientDisconnect.register((handler, client) -> client.execute(() -> {
			open = false;
			held = false;
			hovered = -1;
		}));
	}

	public static boolean isOpen() {
		return open;
	}

	/** Closes without choosing. */
	public static void close() {
		open = false;
		hovered = -1;
	}

	/** The quick key just closed a screen: ignore its clicks for a moment so the release does not reopen the hub. */
	static void suppress() {
		suppressUntil = Util.getMillis() + SUPPRESS_MILLIS;
		held = false;
	}

	/** True while the tribulation HUD is live (its phase is above 0): the wheel stays shut in danger. */
	public static boolean danger() {
		if (!ClientCultivation.received()) {
			return false;
		}
		return ClientCultivation.data().sessionExtra().getCompoundOrEmpty("trib").getIntOr("phase", 0) > 0;
	}

	private static boolean blocked() {
		return CinematicDirector.active() || SealWheel.isOpen() || danger();
	}

	// ================================================================== input

	/** Before vanilla's key handling: the open wheel takes the hotbar keys 1-8, use (cancel) and attack (choose). */
	private static void startTick(Minecraft minecraft) {
		if (!open) {
			return;
		}
		if (minecraft.gui.screen() != null || minecraft.player == null) {
			close();
			return;
		}
		KeyMapping[] hotbar = minecraft.options.keyHotbarSlots;
		int direct = -1;
		for (int i = 0; i < SLICES && i < hotbar.length; i++) {
			while (hotbar[i].consumeClick()) {
				direct = i;
			}
		}
		boolean cancel = false;
		while (minecraft.options.keyUse.consumeClick()) {
			cancel = true;
		}
		boolean choose = false;
		while (minecraft.options.keyAttack.consumeClick()) {
			choose = true;
		}
		if (cancel) {
			close();
			held = false;
		} else if (direct >= 0) {
			close();
			held = false;
			run(direct);
		} else if (choose) {
			int slice = hovered;
			close();
			held = false;
			if (slice >= 0) {
				run(slice);
			}
		}
	}

	private static void endTick(Minecraft minecraft) {
		long now = Util.getMillis();
		boolean free = Keys.connected() && minecraft.gui.screen() == null && minecraft.player != null;
		int clicks = 0;
		while (Keys.QUICK.consumeClick()) {
			clicks++;
		}
		boolean down = Keys.QUICK.isDown();
		if (now < suppressUntil) {
			held = false;
			return;
		}
		if (open && (!free || CinematicDirector.active() || danger())) {
			close();
			held = false;
			return;
		}
		if (clicks > 0 && !held && free) {
			held = true;
			pressedAt = now;
			warned = false;
		}
		if (!held) {
			return;
		}
		if (!free && !open) {
			held = false;
			return;
		}
		if (!down) {
			held = false;
			if (open) {
				release();
			} else if (now - pressedAt < HOLD_MILLIS) {
				HubNav.buka(Tujuan.bagian(HubNav.lastSection()));
			}
		} else if (!open && now - pressedAt >= HOLD_MILLIS) {
			if (!blocked()) {
				openWheel();
			} else if (danger() && !warned && minecraft.player != null) {
				warned = true;
				minecraft.player.sendOverlayMessage(Component.translatable("kultivator.hub.wheel.danger").withColor(0xFF8A7A));
			}
		}
	}

	private static void openWheel() {
		SealWheel.close(false);
		open = true;
		openedAt = Util.getMillis();
		pointerX = 0.0D;
		pointerY = 0.0D;
		hovered = -1;
		UiSound.tab();
	}

	/** The key was released while open: the hovered slice, or the hub from the centre. */
	private static void release() {
		int slice = hovered;
		close();
		if (slice >= 0) {
			run(slice);
		} else {
			HubNav.buka(Tujuan.bagian(HubNav.lastSection()));
		}
	}

	/** Runs slice {@code slice} (0-7). */
	public static void run(int slice) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		UiSound.press();
		switch (slice) {
			case MEDITASI -> Keys.send(Payloads.Action.of(Actions.MEDITATE_TOGGLE));
			case PEDANG -> Keys.send(Payloads.Action.of(Actions.FLYING_SWORD_TOGGLE));
			// The navigation slices open the Gulungan like their section keys (DESIGN 43.10.3; old opener until the page exists).
			case KITAB_TEKNIK -> HubNav.bukaKunci(Keys.CODEX);
			case MURID -> HubNav.bukaKunci(Keys.MURID);
			case CINCIN -> Keys.send(new Payloads.Action(Actions.TEMPA, 0, "ring:open"));
			case DUNIA -> HubNav.bukaKunci(Keys.DUNIA);
			case KEABADIAN -> HubNav.bukaKunci(Keys.ABADI);
			case PANDUAN -> HubNav.bukaKunci(Keys.GUIDE);
			default -> {
			}
		}
	}

	/** Mouse movement while open (from {@code MouseHandlerMixin}); true when consumed (the camera does not turn). */
	public static boolean input(double dx, double dy) {
		if (!open || Minecraft.getInstance().gui.screen() != null) {
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
		int index = -1;
		if (length > DEADZONE) {
			double angle = Math.atan2(pointerY, pointerX) + Math.PI / 2.0D;
			index = Math.floorMod((int) Math.floor(angle / (Math.PI * 2.0D) * SLICES + 0.5D), SLICES);
		}
		if (index != hovered) {
			hovered = index;
			UiSound.hover();
		}
		return true;
	}

	/** The mouse wheel while open: one slice around; true when consumed (the hotbar slot does not change). */
	public static boolean scroll(double amount) {
		if (!open || Minecraft.getInstance().gui.screen() != null) {
			return false;
		}
		if (amount != 0.0D) {
			hovered = Math.floorMod((hovered < 0 ? 0 : hovered) + (amount > 0.0D ? -1 : 1), SLICES);
			double angle = -Math.PI / 2.0D + Math.PI * 2.0D * hovered / SLICES;
			pointerX = Math.cos(angle) * radius() * 0.8D;
			pointerY = Math.sin(angle) * radius() * 0.8D;
			UiSound.hover();
		}
		return true;
	}

	private static double radius() {
		return screenHeight < 260 ? 44.0D : 54.0D;
	}

	// ================================================================== drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!open || minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		Font font = minecraft.font;
		screenHeight = g.guiHeight();
		float cx = g.guiWidth() / 2.0F;
		float cy = g.guiHeight() / 2.0F;
		float r = (float) radius();
		float grow = Math.min(1.0F, (Util.getMillis() - openedAt) / 120.0F);
		float ring = r * (0.7F + 0.3F * grow);
		float tile = r < 50.0F ? 12.0F : 14.0F;
		Brush.disc(g, cx, cy, ring + tile + 6.0F, Palette.argb(Math.round(0x70 * grow), Palette.NIGHT));
		Brush.ring(g, cx, cy, ring - 0.5F, ring + 0.5F, Palette.argb(0xA0, Palette.GOLD));
		CultivationData data = ClientCultivation.data();
		for (int i = 0; i < SLICES; i++) {
			double angle = -Math.PI / 2.0D + Math.PI * 2.0D * i / SLICES;
			float sx = cx + (float) Math.cos(angle) * ring;
			float sy = cy + (float) Math.sin(angle) * ring;
			boolean on = i == MEDITASI && data.meditating() || i == PEDANG && data.flyingSword();
			drawSlice(g, font, i, sx, sy, tile, i == hovered, on);
		}
		Brush.dot(g, cx + (float) pointerX, cy + (float) pointerY, 1.4F, Palette.GOLD_LIGHT);
		float inner = ring - tile - 2.0F;
		Brush.disc(g, cx, cy, inner, Palette.argb(0xD0, 0x101824));
		Brush.ring(g, cx, cy, inner - 0.8F, inner, Palette.GOLD_DARK);
		// 43.0.5: all text at 1.0. The centre holds the name and its number key; the title sits above the wheel and the
		// slice's hint under it, where a whole line fits (the centre is only ~50 px wide at GUI scale 4).
		float room = inner * 1.8F;
		float outer = ring + tile + 6.0F;
		float wide = Math.min(g.guiWidth() - 16.0F, 240.0F);
		Component name = hovered >= 0 ? sliceName(hovered) : Component.translatable("kultivator.hub.wheel.centre");
		Component tip = hovered >= 0 ? Component.translatable("kultivator.hub.wheel." + NAMES[hovered] + ".tip")
				: Component.translatable("kultivator.hub.wheel.hint");
		Brush.fitCentered(g, font, Component.translatable("kultivator.hub.wheel.title"), cx, cy - outer - 11.0F, wide, Palette.GOLD, 1.0F, 1.0F, true);
		Brush.fitCentered(g, font, Brush.bold(name), cx, cy - (hovered >= 0 ? 9.0F : 4.0F), room, Palette.JADE_PALE, 1.0F, 1.0F, true);
		if (hovered >= 0) {
			Brush.fitCentered(g, font, Component.translatable("kultivator.hub.wheel.number", hovered + 1), cx, cy + 2.0F, room, Palette.GOLD_LIGHT,
					1.0F, 1.0F, true);
		}
		Brush.fitCentered(g, font, tip, cx, cy + outer + 3.0F, wide, Palette.QI_LIGHT, 1.0F, 1.0F, true);
	}

	private static Component sliceName(int slice) {
		return Component.translatable("kultivator.hub.wheel." + NAMES[slice]);
	}

	private static void drawSlice(GuiGraphicsExtractor g, Font font, int slice, float cx, float cy, float size, boolean hover, boolean on) {
		float half = size / 2.0F + 1.0F;
		if (hover) {
			Brush.halo(g, cx, cy, half + 1.0F, 3.0F, 0xFFE07A, 0.9F);
		}
		Brush.disc(g, cx + 0.5F, cy + 1.0F, half, 0x50000000);
		Brush.disc(g, cx, cy, half, on ? Palette.GOLD : Palette.JADE_DARK);
		Brush.disc(g, cx, cy, half - 1.0F, hover ? Palette.JADE_LIGHT : Palette.JADE);
		Glyphs.tab(g, GLYPHS[slice], cx, cy, size * 0.6F, hover ? Palette.GOLD_LIGHT : Palette.JADE_PALE);
		// the number key at 1.0 just outside the tile's upper left (the name shows in the centre while hovered)
		Brush.text(g, font, Component.literal(String.valueOf(slice + 1)), cx - half - 4.0F, cy - half - 4.0F, Palette.GOLD_LIGHT, 1.0F, true);
	}
}
