package dev.kultivator.client.dunia.kapal;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.dunia.peta.PetaClient;
import dev.kultivator.client.dunia.peta.PetaUi;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.kapal.KapalNet;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.dunia.kapal.KapalStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kemudi, the ship's helm screen (DESIGN 34.5.15): the hull, fuel, riders and disciples aboard, the spirit weather, the
 * banner colour, the hull livery (five, three of them earned) and the ship's name (owner only), the hold, repair, "Simpan"
 * (back into the keel) and the voyage list ("Berlayar ke…": the own sect hall, the routes other
 * modules registered, the waypoints, or a marker of the world map). Opened by sneak + use on the ship, or the inventory
 * key while seated; everything it does is a request the server checks.
 */
public final class HelmScreen extends Screen {

	private static final int W = 300;
	private static final int H = 236;

	private final int ship;
	private CompoundTag info;
	private final List<PetaUi.Button> buttons = new ArrayList<>();
	private int x0;
	private int y0;
	private long openedAt;
	private @Nullable List<Component> tooltip;
	private final List<PetaUi.Button> liveries = new ArrayList<>();
	private @Nullable EditBox nameBox;
	/** The name being typed (kept while the server refreshes the screen). */
	private @Nullable String nameDraft;
	/** The "?" seal: Panduan › Ensiklopedia on the ship topic (audit M5, S11). */
	private final KeyHints.SealCorner help = new KeyHints.SealCorner("kapal_terbang");

	private HelmScreen(int ship, CompoundTag info) {
		super(Component.translatable("kultivator.kapal.helm.title"));
		this.ship = ship;
		this.info = info;
	}

	static void onInfo(KapalNet.Info payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof HelmScreen open && open.ship == payload.ship()) {
			open.info = payload.info();
			open.rebuildWidgets();
			return;
		}
		if (payload.open() && minecraft.player != null) {
			minecraft.gui.setScreen(new HelmScreen(payload.ship(), payload.info()));
		}
	}

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			UiSound.open();
		}
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		help.place(x0 + W - 10.0F, y0 + 12.0F);
		buttons.clear();
		boolean owner = info.getBooleanOr("is_owner", false);
		boolean captain = info.getBooleanOr("captain", false);
		boolean docked = info.getBooleanOr("docked", false);
		int state = info.getIntOr("state", 0);
		int bx = x0 + 14;
		int by = y0 + 128;
		int bw = 62;
		add(new PetaUi.Button(Component.translatable("kultivator.kapal.helm.hold"), () -> send(KapalRohEntity.OP_HOLD, 0, "")), bx, by, bw);
		int repair = info.getIntOr("repair", 0);
		PetaUi.Button fix = add(new PetaUi.Button(Component.translatable("kultivator.kapal.helm.repair"), () -> send(KapalRohEntity.OP_REPAIR, 0, "")),
				bx + bw + 4, by, bw);
		fix.enabled = repair > 0;
		fix.tip = repair > 0 ? Component.translatable("kultivator.kapal.helm.repair.tip", repair * KapalRohEntity.REPAIR_PLANKS, repair)
				: Component.translatable("kultivator.kapal.repair.full");
		PetaUi.Button stow = add(new PetaUi.Button(Component.translatable("kultivator.kapal.helm.stow"), () -> send(KapalRohEntity.OP_STOW, 0, "")),
				bx, by + 18, bw);
		stow.enabled = owner;
		stow.tip = Component.translatable(owner ? (docked ? "kultivator.kapal.helm.stow.tip" : "kultivator.kapal.stow.land_first")
				: "kultivator.kapal.stow.owner_only");
		boolean sailing = !info.getStringOr("dest", "").isEmpty() || state == KapalRohEntity.STATE_AUTO || state == KapalRohEntity.STATE_LEAP;
		PetaUi.Button stop = add(new PetaUi.Button(Component.translatable("kultivator.kapal.helm.stop"), () -> send(KapalRohEntity.OP_STOP, 0, "")),
				bx + bw + 4, by + 18, bw);
		stop.enabled = sailing;
		// banner colours
		int current = info.getIntOr("color", KapalRohEntity.DEFAULT_COLOR);
		for (int i = 0; i < KapalRohEntity.COLORS.length; i++) {
			int index = i;
			PetaUi.Button swatch = new PetaUi.Button(Component.empty(), () -> send(KapalRohEntity.OP_COLOR, index, ""));
			swatch.at(x0 + 14 + i * 16, y0 + 172, 13, 11);
			swatch.accent = 0xFF000000 | KapalRohEntity.COLORS[i];
			swatch.selected = (KapalRohEntity.COLORS[i] & 0xFFFFFF) == (current & 0xFFFFFF);
			buttons.add(swatch);
		}
		// hull liveries (some earned): swatches of their wood
		liveries.clear();
		int styleNow = info.getIntOr("style", 0);
		int open = info.getIntOr("styles_open", 1);
		for (KapalStyle style : KapalStyle.values()) {
			int index = style.ordinal();
			PetaUi.Button swatch = new PetaUi.Button(Component.empty(), () -> send(KapalRohEntity.OP_STYLE, index, ""));
			swatch.at(x0 + 14 + index * 18, y0 + 202, 15, 11);
			swatch.accent = 0xFF000000 | KapalLivery.swatch(style);
			swatch.selected = index == styleNow;
			swatch.enabled = owner && (open & (1 << index)) != 0;
			swatch.tip = Component.translatable("kultivator.kapal.style.desc." + style.id());
			buttons.add(swatch);
			liveries.add(swatch);
		}
		// the ship's name
		int vw0 = W - 156 - 14;
		boolean typing = nameBox != null && nameBox.isFocused();
		nameBox = new EditBox(font, x0 + 156, y0 + 201, vw0 - 46, 12, Component.translatable("kultivator.kapal.helm.name"));
		nameBox.setMaxLength(KapalRohEntity.NAME_MAX);
		nameBox.setHint(Component.translatable("kultivator.kapal.helm.name.hint").withStyle(ChatFormatting.DARK_GRAY));
		nameBox.setValue(nameDraft != null ? nameDraft : info.getStringOr("name", ""));
		nameBox.setResponder(value -> nameDraft = value);
		nameBox.setEditable(owner);
		addRenderableWidget(nameBox);
		if (typing) {
			setFocused(nameBox);
		}
		PetaUi.Button rename = add(new PetaUi.Button(Component.translatable("kultivator.kapal.helm.name.save"), this::saveName), x0 + 156 + vw0 - 42,
				y0 + 200, 42);
		rename.enabled = owner;
		rename.tip = Component.translatable(owner ? "kultivator.kapal.helm.name.tip" : "kultivator.kapal.stow.owner_only");
		// voyages
		int vx = x0 + 156;
		int vy = y0 + 46;
		int vw = W - 156 - 14;
		if (info.contains("hall_x")) {
			PetaUi.Button hall = add(new PetaUi.Button(Component.translatable("kultivator.kapal.helm.dest.hall", info.getStringOr("hall_name", "")),
					() -> send(KapalRohEntity.OP_HALL, 0, "")), vx, vy, vw);
			hall.enabled = captain;
			vy += 15;
		}
		for (Tag tag : info.getListOrEmpty("routes")) {
			if (!(tag instanceof CompoundTag route) || vy > y0 + 150) {
				continue;
			}
			Component name = routeName(route);
			String id = route.getStringOr("id", "");
			PetaUi.Button b = add(new PetaUi.Button(Component.translatable("kultivator.kapal.helm.dest.route", name),
					() -> send(KapalRohEntity.OP_ROUTE, 0, id)), vx, vy, vw);
			b.enabled = captain && route.getBooleanOr("allowed", false);
			b.tip = route.getBooleanOr("allowed", false) ? Component.translatable("kultivator.kapal.helm.dest.route.tip")
					: Component.translatable("kultivator.kapal.route_closed", name);
			vy += 15;
		}
		int shown = 0;
		for (Tag tag : info.getListOrEmpty("waypoints")) {
			if (!(tag instanceof CompoundTag wp) || vy > y0 + 150 || shown >= 5) {
				continue;
			}
			int x = wp.getIntOr("x", 0);
			int z = wp.getIntOr("z", 0);
			String wpName = wp.getStringOr("name", "");
			Component label = wpName.isEmpty() ? Component.translatable("kultivator.kapal.dest.point", x, z) : Component.literal(wpName);
			PetaUi.Button b = add(new PetaUi.Button(label, () -> send(KapalRohEntity.OP_SAIL, 0, x + " " + z + " " + wpName)), vx, vy, vw);
			b.enabled = captain;
			b.accent = 0xFF000000 | wp.getIntOr("color", 0xB3281D);
			vy += 15;
			shown++;
		}
		PetaUi.Button map = add(new PetaUi.Button(Component.translatable("kultivator.kapal.helm.open_map"), () -> {
			onClose();
			PetaClient.requestOpen();
		}), vx, Math.max(vy + 2, y0 + 160), vw);
		map.accent = Palette.JADE;
	}

	private PetaUi.Button add(PetaUi.Button button, int x, int y, int w) {
		button.at(x, y, w, 13);
		buttons.add(button);
		return button;
	}

	private Component routeName(CompoundTag route) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			try {
				return route.read("name", ComponentSerialization.CODEC, minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE))
						.orElse(Component.literal(route.getStringOr("id", "?")));
			} catch (RuntimeException ignored) {
				// fall through
			}
		}
		return Component.literal(route.getStringOr("id", "?"));
	}

	private void send(int op, int arg, String text) {
		KapalClient.helm(ship, op, arg, text);
	}

	private void saveName() {
		if (nameBox == null) {
			return;
		}
		send(KapalRohEntity.OP_RENAME, 0, nameBox.getValue().strip());
		nameDraft = null;
		nameBox.setFocused(false);
		setFocused(null);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0x90, Palette.NIGHT), Palette.argb(0xB8, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		tooltip = null;
		int x1 = x0 + W;
		int y1 = y0 + H;
		ScrollFrame.paper(g, x0, x1, y0, y1, y0, y1);
		ScrollFrame.rod(g, x0, x1, y0);
		ScrollFrame.rod(g, x0, x1, y1);
		g.nextStratum();
		KapalClass kind = KapalClass.byOrdinal(info.getIntOr("class", 0));
		// header
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.kapal.class." + kind.model())), x0 + 14, y0 + 12, 140, Palette.SEAL_DARK,
				1.0F, 0.6F, false);
		int weather = info.getIntOr("weather", 0);
		boolean misty = weather > 0 && weather < SpiritWeather.values().length;
		Brush.fit(g, font, Component.translatable("kultivator.kapal.helm.owner", info.getStringOr("owner", "?")), x0 + 14, y0 + 24, misty ? 66 : 140,
				Palette.INK_SOFT, 0.65F, 0.5F, false);
		if (misty) {
			Brush.fit(g, font, Component.translatable("kultivator.kapal.helm.weather", Component.translatable("kultivator.kapal.weather.name."
					+ SpiritWeather.values()[weather].id())), x0 + 82, y0 + 24, 64, 0xFF6A4FA8, 0.6F, 0.45F, false);
		}
		String shipName = info.getStringOr("name", "");
		if (!shipName.isEmpty()) {
			Brush.fit(g, font, Component.literal("\u00AB" + shipName + "\u00BB"), x0 + 14, y0 + 2, 132, Palette.GOLD_DARK, 0.6F, 0.45F, false);
		}
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.kapal.helm.voyage")), x0 + 156, y0 + 12, W - 184, Palette.SEAL_DARK, 0.85F,
				0.6F, false);
		Brush.fit(g, font, Component.translatable(info.getBooleanOr("captain", false) ? "kultivator.kapal.helm.voyage.sub"
				: "kultivator.kapal.helm.voyage.not_captain"), x0 + 156, y0 + 24, W - 170, Palette.INK_FAINT, 0.6F, 0.45F, false);
		g.fill(x0 + 14, y0 + 36, x0 + 146, y0 + 37, Palette.argb(0x80, Palette.GOLD_DARK));
		g.fill(x0 + 156, y0 + 36, x1 - 14, y0 + 37, Palette.argb(0x80, Palette.GOLD_DARK));
		// stats
		int sx = x0 + 14;
		int sy = y0 + 44;
		int hull = info.getIntOr("hull", 0);
		int max = Math.max(1, info.getIntOr("hull_max", 1));
		Brush.fit(g, font, Component.translatable("kultivator.kapal.helm.hull", hull, max), sx, sy, 130, Palette.INK, 0.7F, 0.5F, false);
		float ratio = hull / (float) max;
		PetaUi.bar(g, sx, sy + 9, 130, 6, ratio, ratio > 0.5F ? 0xFF2E8963 : ratio > 0.25F ? 0xFFE5821C : 0xFFD5322A, 0x40000000);
		sy += 20;
		Brush.fit(g, font, Component.translatable("kultivator.kapal.helm.fuel", PetaUi.duration(info.getLongOr("fuel", 0L) / 20L)), sx, sy, 130,
				Palette.INK, 0.7F, 0.5F, false);
		sy += 11;
		Brush.fit(g, font, Component.translatable("kultivator.kapal.helm.riders", info.getIntOr("riders", 0), info.getIntOr("seats", 0),
				info.getIntOr("disciples", 0)), sx, sy, 130, Palette.INK, 0.7F, 0.5F, false);
		sy += 11;
		Brush.fit(g, font, Component.translatable("kultivator.kapal.helm.speed", info.getIntOr("speed_max", 0)), sx, sy, 130, Palette.INK_SOFT,
				0.65F, 0.5F, false);
		sy += 11;
		String dest = info.getStringOr("dest", "");
		Component status = dest.isEmpty() ? Component.translatable("kultivator.kapal.helm.status", stateName(info))
				: Component.translatable("kultivator.kapal.helm.status_dest", stateName(info), destName(dest));
		Brush.wrap(g, font, status, sx, sy, 130, Palette.JADE_DARK, 0.65F, false);
		Brush.fit(g, font, Component.translatable("kultivator.kapal.helm.colour"), x0 + 14, y0 + 162, 130, Palette.INK_SOFT, 0.6F, 0.45F, false);
		KapalStyle styleNow = KapalStyle.byOrdinal(info.getIntOr("style", 0));
		Brush.fit(g, font, Component.translatable("kultivator.kapal.helm.style").append(": ").append(Component.translatable("kultivator.kapal.style."
				+ styleNow.id())), x0 + 14, y0 + 191, 130, Palette.INK_SOFT, 0.6F, 0.45F, false);
		Brush.fit(g, font, Component.translatable("kultivator.kapal.helm.name"), x0 + 156, y0 + 191, W - 170, Palette.INK_SOFT, 0.6F, 0.45F, false);
		g.fill(x0 + 14, y0 + 186, x0 + 146, y0 + 187, Palette.argb(0x60, Palette.GOLD_DARK));
		g.fill(x0 + 156, y0 + 186, x1 - 14, y0 + 187, Palette.argb(0x60, Palette.GOLD_DARK));
		for (PetaUi.Button button : buttons) {
			if (button.label.getString().isEmpty()) {
				// a colour or livery swatch
				int x = button.x;
				int y = button.y;
				g.fill(x, y, x + button.w, y + button.h, button.accent);
				int livery = liveries.indexOf(button);
				if (livery >= 0 && !button.enabled) {
					// a locked livery: veiled, with a seal bar across
					g.fill(x, y, x + button.w, y + button.h, 0x99101010);
					g.fill(x + 2, y + button.h / 2, x + button.w - 2, y + button.h / 2 + 1, Palette.argb(0xC0, Palette.SEAL));
				}
				Brush.frame(g, x - 1, y - 1, x + button.w + 1, y + button.h + 1, button.selected ? Palette.GOLD : Palette.argb(0x80, Palette.INK));
				if (button.over(mouseX, mouseY)) {
					Brush.frame(g, x - 2, y - 2, x + button.w + 2, y + button.h + 2, Palette.GOLD_LIGHT);
					if (livery >= 0) {
						KapalStyle style = KapalStyle.values()[livery];
						List<Component> lines = new ArrayList<>();
						lines.add(Brush.bold(Component.translatable("kultivator.kapal.style." + style.id())));
						lines.add(button.tip.copy().withStyle(ChatFormatting.GRAY));
						lines.add(Component.translatable("kultivator.kapal.style.locked." + style.unlock().name().toLowerCase(Locale.ROOT))
								.withStyle(button.enabled ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
						tooltip = lines;
					}
				}
				continue;
			}
			button.draw(g, font, mouseX, mouseY);
			if (button.over(mouseX, mouseY) && button.tip != null) {
				tooltip = List.of(Brush.bold(button.label), button.tip.copy().withStyle(ChatFormatting.GRAY));
			}
		}
		Brush.fitCentered(g, font, Component.translatable("kultivator.kapal.helm.keys"), (x0 + x1) / 2.0F, y1 - 14, W - 30, Palette.INK_FAINT, 0.55F,
				0.4F, false);
		// the name field (a vanilla widget) on top of the paper
		super.extractRenderState(g, mouseX, mouseY, a);
		if (tooltip != null) {
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
		help.render(g, font, mouseX, mouseY, width, height);
	}

	private static Component stateName(CompoundTag info) {
		if (info.getBooleanOr("docked", false)) {
			return Component.translatable("kultivator.kapal.state.docked");
		}
		return Component.translatable("kultivator.kapal.state." + switch (info.getIntOr("state", 0)) {
			case KapalRohEntity.STATE_FLY -> "fly";
			case KapalRohEntity.STATE_AUTO -> "auto";
			case KapalRohEntity.STATE_LEAP -> "leap";
			case KapalRohEntity.STATE_BROKEN -> "broken";
			case KapalRohEntity.STATE_HALT -> "halt";
			default -> "idle";
		});
	}

	private static Component destName(String dest) {
		String[] parts = dest.split(" ", 3);
		if (parts.length > 2 && !parts[2].isBlank()) {
			return Component.literal(parts[2]);
		}
		return parts.length >= 2 ? Component.translatable("kultivator.kapal.dest.point", parts[0], parts[1]) : Component.literal(dest);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (nameBox != null && nameBox.isFocused() && !nameBox.isMouseOver(event.x(), event.y())) {
			nameBox.setFocused(false);
			setFocused(null);
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (help.click(event.x(), event.y())) {
				return true;
			}
			for (PetaUi.Button button : buttons) {
				if (button.over(event.x(), event.y())) {
					if (button.enabled) {
						UiSound.press();
						button.action.run();
					} else {
						UiSound.denied();
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (help.release(event.x(), event.y())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (nameBox != null && nameBox.isFocused()) {
			if (event.isConfirmation()) {
				saveName();
				return true;
			}
			if (event.isEscape()) {
				nameBox.setFocused(false);
				setFocused(null);
				return true;
			}
			// typing: the inventory key is just a letter here
			return super.keyPressed(event);
		}
		if (minecraft != null && minecraft.options.keyInventory.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		UiSound.close();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
