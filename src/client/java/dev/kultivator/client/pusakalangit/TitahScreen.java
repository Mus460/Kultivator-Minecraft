package dev.kultivator.client.pusakalangit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.screen.PhotosensitivityScreen;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.JadeSlider;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.ScrollFrame;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.client.screen.ui.SlipTooltip;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Titah Langit, the jade-scroll menu of the Segel Titah Langit (Phase 10b, DESIGN 29.2.2; 5b style, fits 320 × 240).
 * Left: four decree cards (Pedang, Tiang, Bintang, Gunung) with lock state, cooldown sweep and the decree glyph
 * (劍 柱 斗 山); clicking one selects it, and the seal's right-click then casts it. Right, for the selected decree: the
 * Kekuatan, Kerusakan and Kecepatan sliders ({@link JadeSlider}; Kekuatan greyed red above the realm's limit), the
 * "Hancurkan tanah" coin (only while Mode Kehancuran is on), "Tinggalkan pusaka" (Pedang, Tiang), one "Film: Penuh ▸"
 * chip naming the client-side Film choice (it opens Pengaturan › Kamera & Film, where the four Film Titah rows live
 * since DESIGN 33.6 R10), the server's readout line and the hint line; at the bottom the talismans Pilih, Bawaan and
 * Peringatan cahaya; a "?" seal at the right end of the header opens Panduan › Ensiklopedia on {@code titah}. Every value
 * is sanitised by the server, which answers with a {@code TitahSync}.
 * <p>
 * Tingkat Titah (DESIGN 29.16.10): a "Tingkat" slider (1-9, label "Tingkat t · ×s") sits above Kekuatan; tiers above
 * the realm's limit are greyed with a red seal and its tooltip names the reason; at tier 6-9, when the cast would carve
 * for real, a red PERMANEN stamp stands right of it. The rows below moved down 19 px and still fit 320 × 240.
 */
public final class TitahScreen extends Screen {

	private static final String K = "kultivator.titah_client.screen.";
	private static final String KT = "kultivator.titah_tingkat_client.screen.";
	/** The room right of the Tingkat slider for the PERMANEN stamp. */
	private static final int STAMP_W = 44;
	private static final Identifier ICONS = Kultivator.id("textures/gui/titah/decree_icons.png");
	private static final Identifier WASH = Kultivator.id("textures/gui/titah/seal_wash.png");
	private static final int MAX_W = 312;
	private static final int MAX_H = 232;
	private static final int CARD_W = 42;
	private static final int CARD_H = 36;
	private static final int CARD_GAP = 5;
	private static final long UNROLL_MILLIS = 250L;
	/** Dewa Abadi stage of each decree (Awal, Tengah, Akhir, Puncak). */
	private static final String[] STAGES = {"awal", "tengah", "akhir", "puncak"};

	private int skill;
	private final List<Widget> widgets = new ArrayList<>();
	private @Nullable Widget pressed;
	private @Nullable JadeSlider tSlider;
	private @Nullable JadeSlider kSlider;
	private @Nullable JadeSlider dSlider;
	private @Nullable JadeSlider vSlider;
	private final Eased glow = new Eased(6.0F);
	private long openedAt;
	private long lastFrame;
	private int seenVersion = -1;
	private String layoutKey = "";
	private @Nullable List<Component> tooltip;
	private final KeyHints.HelpSeal help = new KeyHints.HelpSeal(0.0F, 0.0F, 10, () -> "titah");

	private int x0;
	private int x1;
	private int top;
	private int bottom;

	public TitahScreen(int skill) {
		super(Component.translatable(K + "title"));
		this.skill = Math.max(0, Math.min(3, skill));
	}

	// ================================================================ layout

	@Override
	protected void init() {
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			lastFrame = openedAt;
			UiSound.open();
		}
		widgets.clear();
		pressed = null;
		int w = Math.min(MAX_W, width - 8);
		int h = Math.min(MAX_H, height - 8);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		top = (height - h) / 2 + 5;
		bottom = top + h - 10;
		int inX = x0 + ScrollFrame.SIDE + 8;
		int inW = x1 - x0 - 2 * (ScrollFrame.SIDE + 8);
		int bodyTop = top + ScrollFrame.END + 22;

		// the four decree cards
		for (int i = 0; i < 4; i++) {
			int index = i;
			DecreeCard card = new DecreeCard(inX, bodyTop + i * (CARD_H + CARD_GAP), index);
			card.onPress(() -> choose(index));
			card.tooltip(() -> cardTooltip(index));
			widgets.add(card);
		}

		// the selected decree's controls
		int rx = inX + CARD_W + 10;
		int rw = inX + inW - rx;
		int y = bodyTop + 12;
		TitahClient.Decree decree = TitahClient.decree(skill);
		boolean open = decree.unlocked;
		int current = skill;
		// Tingkat (29.16.10): above Kekuatan, the stamp's room on its right
		tSlider = new JadeSlider(rx, y, Math.max(40, rw - STAMP_W), 16, TitahClient.T_STEPS, TitahClient.nearest(TitahClient.T_STEPS, decree.t),
				value -> Component.translatable(KT + "t", Math.round(value), scale(Math.round(value))),
				index -> TitahClient.set(current, TitahClient.FIELD_T, TitahClient.T_STEPS[index]))
				.limit(() -> TitahClient.tMaxIndex(current));
		tSlider.active = open;
		tSlider.sync(TitahClient.nearest(TitahClient.T_STEPS, decree.t));
		addRenderableWidget(tSlider);
		y += 19;
		kSlider = new JadeSlider(rx, y, rw, 16, TitahClient.K_STEPS, TitahClient.nearest(TitahClient.K_STEPS, decree.k),
				value -> Component.translatable(K + "k", times(value)),
				index -> TitahClient.set(current, TitahClient.FIELD_K, TitahClient.K_STEPS[index]))
				.limit(() -> TitahClient.kMaxIndex(current));
		kSlider.active = open;
		kSlider.sync(TitahClient.nearest(TitahClient.K_STEPS, decree.k));
		addRenderableWidget(kSlider);
		y += 19;
		dSlider = new JadeSlider(rx, y, rw, 16, TitahClient.D_STEPS, TitahClient.nearest(TitahClient.D_STEPS, decree.d),
				value -> Component.translatable(K + "d", Math.round(value * 100.0F)),
				index -> TitahClient.set(current, TitahClient.FIELD_D, TitahClient.D_STEPS[index]));
		dSlider.active = open;
		addRenderableWidget(dSlider);
		y += 19;
		vSlider = new JadeSlider(rx, y, rw, 16, TitahClient.V_STEPS, TitahClient.nearest(TitahClient.V_STEPS, decree.v),
				value -> Component.translatable(K + "v", times(value)),
				index -> TitahClient.set(current, TitahClient.FIELD_V, TitahClient.V_STEPS[index]));
		vSlider.active = open;
		addRenderableWidget(vSlider);
		y += 20;
		if (TitahClient.kehancuranOn()) {
			JadeCoin coin = new JadeCoin(rx, y, rw, 12, () -> Component.translatable(K + "h", Component.translatable(
					TitahClient.decree(current).h ? K + "h.on" : K + "h.off")), () -> TitahClient.decree(current).h);
			coin.onPress(() -> TitahClient.set(current, TitahClient.FIELD_H, !TitahClient.decree(current).h));
			coin.enabledWhen(() -> TitahClient.decree(current).unlocked);
			coin.tooltip(() -> List.of(Component.translatable(K + "h.tip").withStyle(ChatFormatting.GRAY)));
			widgets.add(coin);
			y += 14;
		}
		if (skill <= 1) {
			JadeCoin coin = new JadeCoin(rx, y, rw, 12, () -> Component.translatable(K + "m", Component.translatable(
					TitahClient.decree(current).m ? K + "m.on" : K + "m.off")), () -> TitahClient.decree(current).m);
			coin.onPress(() -> TitahClient.set(current, TitahClient.FIELD_M, !TitahClient.decree(current).m));
			coin.enabledWhen(() -> TitahClient.decree(current).unlocked);
			coin.tooltip(() -> List.of(Component.translatable(K + "m.tip").withStyle(ChatFormatting.GRAY)));
			widgets.add(coin);
			y += 14;
		}
		// the Film choice (client side): one chip naming it; the choice itself lives in Pengaturan › Kamera & Film (33.6 R10)
		TalismanButton film = new TalismanButton(rx, y, rw, 13, () -> Component.translatable("kultivator.uxpages.titah.film",
				Component.translatable("kultivator.titah_client.film." + EpicSettings.titahFilm(current).id())));
		film.onPress(() -> HubNav.openSettings(HubNav.GROUP_FILM));
		film.tooltip(() -> List.of(Component.translatable("kultivator.uxpages.titah.film.title"),
				Component.translatable("kultivator.titah_client.film." + EpicSettings.titahFilm(current).id() + ".tip").withStyle(ChatFormatting.GRAY),
				Component.translatable("kultivator.uxpages.titah.film.tip").withStyle(ChatFormatting.DARK_GRAY)));
		widgets.add(film);

		// the bottom talismans
		int bw = Math.max(60, (inW - 12) / 3);
		int by = bottom - ScrollFrame.END - 20;
		TalismanButton pick = new TalismanButton(inX, by, bw, 15, () -> Component.translatable(K + "pick"));
		pick.urgentWhen(() -> TitahClient.selected() != current && TitahClient.decree(current).unlocked);
		pick.enabledWhen(() -> TitahClient.decree(current).unlocked);
		pick.onPress(() -> {
			TitahClient.select(current);
			onClose();
		});
		pick.tooltip(() -> List.of(Component.translatable(K + "pick.tip").withStyle(ChatFormatting.GRAY)));
		widgets.add(pick);
		TalismanButton reset = new TalismanButton(inX + bw + 6, by, bw, 15, () -> Component.translatable(K + "reset"));
		reset.onPress(() -> {
			TitahClient.reset(current);
			EpicSettings.setTitahFilm(current, EpicSettings.TitahFilm.PENUH);
		});
		reset.tooltip(() -> List.of(Component.translatable(K + "reset.tip").withStyle(ChatFormatting.GRAY)));
		widgets.add(reset);
		TalismanButton warning = new TalismanButton(inX + 2 * (bw + 6), by, Math.max(40, inX + inW - (inX + 2 * (bw + 6))), 15,
				() -> Component.translatable(K + "warning"));
		warning.onPress(() -> minecraft.gui.setScreen(new PhotosensitivityScreen(this)));
		warning.tooltip(() -> List.of(Component.translatable(K + "warning.tip").withStyle(ChatFormatting.GRAY)));
		widgets.add(warning);
		help.moveTo(inX + inW - 4.0F, top + ScrollFrame.END + 9.0F);
		widgets.add(help);

		layoutKey = layoutKey();
		seenVersion = TitahClient.version();
	}

	/** What decides which controls exist: the decree, Mode Kehancuran and the lock (a change rebuilds the controls). */
	private String layoutKey() {
		return skill + ":" + TitahClient.kehancuranOn() + ":" + TitahClient.decree(skill).unlocked;
	}

	private void choose(int index) {
		if (index == skill) {
			return;
		}
		skill = index;
		if (TitahClient.decree(index).unlocked) {
			TitahClient.select(index);
		}
		rebuildWidgets();
	}

	@Override
	public void tick() {
		super.tick();
		if (!layoutKey.equals(layoutKey())) {
			rebuildWidgets();
			return;
		}
		if (seenVersion != TitahClient.version()) {
			// a sync arrived: follow the server's sanitised values (never under the player's hand)
			seenVersion = TitahClient.version();
			TitahClient.Decree decree = TitahClient.decree(skill);
			if (tSlider != null) {
				tSlider.sync(TitahClient.nearest(TitahClient.T_STEPS, decree.t));
			}
			if (kSlider != null) {
				kSlider.sync(TitahClient.nearest(TitahClient.K_STEPS, decree.k));
			}
			if (dSlider != null) {
				dSlider.sync(TitahClient.nearest(TitahClient.D_STEPS, decree.d));
			}
			if (vSlider != null) {
				vSlider.sync(TitahClient.nearest(TitahClient.V_STEPS, decree.v));
			}
		}
	}

	// ================================================================ drawing

	private float unroll() {
		return Eased.out(Math.min(1.0F, (Util.getMillis() - openedAt) / (float) UNROLL_MILLIS));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, Palette.argb(0xB0, Palette.NIGHT), Palette.argb(0xD0, Palette.NIGHT_MIST));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		tooltip = null;
		float open = unroll();
		int mid = (top + bottom) / 2;
		int half = Math.round((bottom - top) / 2.0F * open);
		ScrollFrame.paper(g, x0, x1, mid - half, mid + half, top, bottom);
		ScrollFrame.rod(g, x0, x1, mid - half);
		ScrollFrame.rod(g, x0, x1, mid + half);
		g.nextStratum();
		if (open < 0.95F) {
			return;
		}
		Font font = this.font;
		int inX = x0 + ScrollFrame.SIDE + 8;
		int inW = x1 - x0 - 2 * (ScrollFrame.SIDE + 8);
		int rx = inX + CARD_W + 10;
		int rw = inX + inW - rx;
		int bodyTop = top + ScrollFrame.END + 22;

		// the header: the title and a gold rule; the seal's glyph 敕 ("by decree") as a faint ink wash behind the panel
		float y = top + ScrollFrame.END + 5.0F;
		Brush.fitCentered(g, font, Brush.bold(title), (x0 + x1) / 2.0F, y, inW - 28.0F, Palette.INK, 1.0F, 0.6F, false);
		Brush.rect(g, inX, y + 11.0F, inX + inW - 12.0F, y + 11.6F, Palette.GOLD);
		int washSize = Math.min(96, rw);
		g.blit(RenderPipelines.GUI_TEXTURED, WASH, rx + rw - washSize, bodyTop + 6, 0.0F, 0.0F, washSize, washSize, 128, 128, 128, 128,
				Palette.argb(0x22, Palette.JADE_DARK));

		// the selected decree: its name, its stage and its cooldown
		TitahClient.Decree decree = TitahClient.decree(skill);
		Component name = Brush.bold(TitahClient.decreeName(skill));
		Brush.fit(g, font, name, rx, bodyTop, rw * 0.62F, decree.unlocked ? Palette.INK : Palette.INK_SOFT, 0.9F, 0.6F, false);
		Component state;
		int stateColor;
		if (!TitahClient.synced()) {
			state = Component.translatable(TitahClient.tooBig() ? "kultivator.sect_fix_feat.too_big" : K + "loading");
			stateColor = TitahClient.tooBig() ? Palette.SEAL : Palette.INK_FAINT;
		} else if (!decree.unlocked) {
			state = lockedLine(skill);
			stateColor = Palette.SEAL;
		} else if (TitahClient.cooldownLeft(skill) > 0) {
			state = Component.translatable(K + "cooldown", TitahClient.minutes((TitahClient.cooldownLeft(skill) + 19) / 20));
			stateColor = Palette.WARN;
		} else {
			state = Component.translatable(TitahClient.selected() == skill ? K + "ready_selected" : K + "ready");
			stateColor = Palette.JADE_DARK;
		}
		float stateScale = Brush.fitScale(font, state, rw * 0.38F, 0.7F, 0.5F);
		Brush.text(g, font, state, rx + rw - font.width(state) * stateScale, bodyTop + 1.5F, stateColor, stateScale, false);

		// the controls (vanilla sliders through super, the theme's own widgets after)
		super.extractRenderState(g, mouseX, mouseY, a);
		int filmY = filmRowY(bodyTop);
		for (Widget widget : widgets) {
			widget.render(g, font, mouseX, mouseY, seconds);
			if (widget.contains(mouseX, mouseY) && !widget.tooltipLines().isEmpty()) {
				tooltip = widget.tooltipLines();
			}
		}
		// the PERMANEN stamp right of the Tingkat slider (tier 6-9 and this cast would carve for real)
		int stampX = rx + Math.max(40, rw - STAMP_W) + 4;
		int stampY = bodyTop + 12;
		boolean stamp = decree.unlocked && TitahClient.permanent(skill) && rx + rw - stampX >= 24;
		if (stamp) {
			drawStamp(g, font, stampX, stampY, rx + rw - stampX);
		}
		if (tSlider != null && tSlider.isHovered()) {
			tooltip = tierTooltip(skill);
		} else if (stamp && mouseX >= stampX && mouseX < rx + rw && mouseY >= stampY && mouseY < stampY + 16) {
			tooltip = List.of(Component.translatable(KT + "perm").withStyle(ChatFormatting.RED),
					Component.translatable(KT + "perm.tip").withStyle(ChatFormatting.GRAY));
		} else if (kSlider != null && kSlider.isHovered()) {
			tooltip = List.of(Component.translatable(K + "k.tip").withStyle(ChatFormatting.GRAY),
					Component.translatable(K + "k.limit", times(TitahClient.K_STEPS[TitahClient.kMaxIndex(skill)])).withStyle(ChatFormatting.GRAY));
		} else if (dSlider != null && dSlider.isHovered()) {
			tooltip = List.of(Component.translatable(K + "d.tip").withStyle(ChatFormatting.GRAY));
		} else if (vSlider != null && vSlider.isHovered()) {
			tooltip = List.of(Component.translatable(K + "v.tip").withStyle(ChatFormatting.GRAY));
		}

		// the readout and the hint
		float ry = filmY + 17.0F;
		float pulse = glow.to(1.0F, seconds);
		Brush.fit(g, font, TitahClient.readout(skill), rx, ry, rw, Palette.mix(Palette.INK_SOFT, Palette.JADE_DARK, 0.5F * pulse), 0.7F, 0.5F, false);
		if (TitahClient.tooBig()) {
			Brush.fit(g, font, Component.translatable("kultivator.sect_fix_feat.too_big"), rx, ry + 9.0F, rw, Palette.SEAL, 0.65F, 0.5F, false);
		} else {
			Brush.fit(g, font, Component.translatable(K + "hint"), rx, ry + 9.0F, rw, Palette.INK_FAINT, 0.65F, 0.5F, false);
		}

		if (tooltip != null && !tooltip.isEmpty()) {
			g.nextStratum();
			SlipTooltip.draw(g, font, tooltip, mouseX, mouseY, width, height);
		}
	}

	/** The y of the Film row (after the sliders and the coins that exist now; Tingkat adds a row of 19). */
	private int filmRowY(int bodyTop) {
		int y = bodyTop + 12 + 19 + 19 + 19 + 20;
		if (TitahClient.kehancuranOn()) {
			y += 14;
		}
		if (skill <= 1) {
			y += 14;
		}
		return y;
	}

	/** The world scale of a tier with one decimal (×1,0 … ×43,0). */
	private static String scale(int tier) {
		return "×" + String.format(Locale.ROOT, "%.1f", Math.pow(1.6D, Math.max(1, Math.min(9, tier)) - 1)).replace('.', ',');
	}

	/** The Tingkat slider's tooltip: what it does, the limit now and why higher tiers are sealed. */
	private static List<Component> tierTooltip(int index) {
		TitahClient.Decree decree = TitahClient.decree(index);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(KT + "t.tip").withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable(KT + "t.limit", decree.tMax).withStyle(ChatFormatting.GRAY));
		if (decree.tMax < 9 && !decree.tWhy.isEmpty()) {
			lines.add(Component.translatable(decree.tWhy).withStyle(ChatFormatting.RED));
		}
		if (decree.t >= TitahClient.PERMANENT_TIER) {
			lines.add(Component.translatable(TitahClient.permanent(index) ? KT + "perm.tip" : KT + "t.visual").withStyle(
					TitahClient.permanent(index) ? ChatFormatting.RED : ChatFormatting.GRAY));
		}
		return lines;
	}

	/** A red seal stamp reading PERMANEN (its frame breathes so it is never missed). */
	private static void drawStamp(GuiGraphicsExtractor g, Font font, int x, int y, int w) {
		float pulse = 0.75F + 0.25F * (float) Math.sin(Util.getMillis() / 260.0D);
		int x1 = x + w;
		int y1 = y + 15;
		g.fill(x + 1, y + 2, x1 + 1, y1 + 1, 0x30000000);
		Brush.frame(g, x, y, x1, y1, Palette.argb(Math.round(200 * pulse), Palette.SEAL));
		Brush.frame(g, x + 1, y + 1, x1 - 1, y1 - 1, Palette.argb(0x90, Palette.SEAL));
		g.fill(x + 2, y + 2, x1 - 2, y1 - 2, Palette.argb(0x30, Palette.SEAL));
		Brush.fitCentered(g, font, Brush.bold(Component.translatable(KT + "perm")), (x + x1) / 2.0F, y + 4.0F, w - 4.0F, Palette.SEAL, 0.7F, 0.45F, false);
	}

	private static String times(float value) {
		String text = value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%s", value);
		return "×" + text.replace('.', ',');
	}

	/** Why a decree is locked: the reason the server sent (a lang key) when there is one, else the Dewa Abadi stage it needs. */
	private static Component lockedLine(int index) {
		String why = TitahClient.decree(index).why;
		if (!why.isEmpty()) {
			return Component.translatable(why);
		}
		return Component.translatable(K + "locked", Component.translatable(K + "stage." + STAGES[index]));
	}

	private List<Component> cardTooltip(int index) {
		TitahClient.Decree decree = TitahClient.decree(index);
		List<Component> lines = new ArrayList<>();
		lines.add(Brush.bold(TitahClient.decreeName(index)));
		lines.add(Component.translatable(K + "desc." + index).withStyle(ChatFormatting.GRAY));
		if (!decree.unlocked) {
			lines.add(lockedLine(index).copy().withStyle(ChatFormatting.RED));
		} else if (TitahClient.cooldownLeft(index) > 0) {
			lines.add(Component.translatable(K + "cooldown", TitahClient.minutes((TitahClient.cooldownLeft(index) + 19) / 20))
					.withStyle(ChatFormatting.GOLD));
		}
		return lines;
	}

	// ================================================================ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && unroll() >= 0.95F) {
			for (Widget widget : widgets) {
				if (widget.contains(event.x(), event.y())) {
					widget.press();
					pressed = widget;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		Widget held = pressed;
		if (held != null) {
			pressed = null;
			held.release(event.x(), event.y());
			return true;
		}
		return super.mouseReleased(event);
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

	// ================================================================ the decree card

	/**
	 * One decree as a square seal card (the 5b seal-card look, {@link SealCard}'s palette): the decree glyph over a faint
	 * silhouette of its weapon, the cooldown sweep, a pulsing gold frame when shown, a red stamp on the decree the seal
	 * casts, a padlock while the realm stage is not reached.
	 */
	private final class DecreeCard extends Widget {
		private final int index;

		DecreeCard(int x, int y, int index) {
			super(x, y, CARD_W, CARD_H);
			this.index = index;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			TitahClient.Decree decree = TitahClient.decree(index);
			boolean bright = decree.unlocked;
			boolean shown = index == skill;
			int xa = x;
			int ya = y + pressOffset(over);
			int xb = xa + w;
			int yb = ya + h;
			long now = Util.getMillis();
			int tone = DECREE_TONE[index];
			if (shown) {
				float pulse = 0.5F + 0.5F * (float) Math.sin(now / 220.0D);
				Brush.frame(g, xa - 3, ya - 3, xb + 3, yb + 3, Palette.argb(Math.round(60 + 80 * pulse), Palette.GOLD_LIGHT));
				Brush.frame(g, xa - 2, ya - 2, xb + 2, yb + 2, Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse));
				Brush.frame(g, xa - 1, ya - 1, xb + 1, yb + 1, Palette.GOLD_DARK);
			} else if (hover.get() > 0.02F) {
				Brush.frame(g, xa - 1, ya - 1, xb + 1, yb + 1, Palette.argb(Math.round(110 * hover.get()), bright ? tone : Palette.LOCKED));
			}
			g.fill(xa + 1, ya + 2, xb + 1, yb + 1, 0x40000000);
			g.fill(xa, ya, xb, yb, bright ? Palette.mix(tone, Palette.INK, 0.15F) : Palette.LOCKED);
			int paper = bright ? Palette.lighten(0xFFF4E6B8, 0.15F * hover.get()) : Palette.LOCKED_PAPER;
			g.fill(xa + 2, ya + 2, xb - 2, yb - 2, paper);
			// the weapon's silhouette, faint, behind the glyph
			int icon = Math.min(w, h) - 6;
			g.blit(RenderPipelines.GUI_TEXTURED, ICONS, (xa + xb - icon) / 2, ya + 3, index * 32.0F, 0.0F, icon, icon, 32, 32, 128, 32,
					Palette.argb(bright ? 0x58 : 0x30, bright ? tone : Palette.INK_FAINT));
			// the cooldown sweep
			int left = TitahClient.cooldownLeft(index);
			if (bright && left > 0) {
				float fraction = Math.min(1.0F, left / (float) Math.max(1, decree.cdTotal));
				g.enableScissor(xa + 2, ya + 2, xb - 2, yb - 2);
				Brush.pie(g, (xa + xb) / 2.0F, (ya + yb) / 2.0F, Math.max(w, h) * 0.75F, 1.0F - fraction, 1.0F,
						Palette.mix(paper, Palette.NIGHT_MIST, 0.4F));
				g.disableScissor();
			}
			Brush.frame(g, xa + 3, ya + 3, xb - 3, yb - 3, Palette.argb(bright ? 0x70 : 0x40, bright ? tone : Palette.INK_FAINT));
			// the decree glyph and its short name
			Component glyph = Component.literal(TitahClient.GLYPHS[index]);
			float glyphScale = 1.6F;
			Brush.centered(g, font, glyph, (xa + xb) / 2.0F, ya + 6.0F, bright ? Palette.onPaper(tone) : Palette.INK_FAINT, glyphScale, false);
			Brush.fitCentered(g, font, Component.translatable(K + "short." + index), (xa + xb) / 2.0F, yb - 9.0F, w - 6.0F,
					bright ? Palette.INK : Palette.INK_SOFT, 0.5F, 0.4F, false);
			// the decree the seal casts carries a small red stamp
			if (bright && TitahClient.selected() == index) {
				g.fill(xb - 8, ya + 3, xb - 3, ya + 8, Palette.SEAL);
				g.fill(xb - 7, ya + 4, xb - 4, ya + 7, Palette.argb(0xB0, Palette.TALISMAN));
				g.fill(xb - 6, ya + 5, xb - 5, ya + 6, Palette.SEAL);
			}
			if (!bright) {
				g.fill(xa + 2, ya + 2, xb - 2, yb - 2, 0x30FFFFFF);
				Glyphs.lock(g, (xa + xb) / 2.0F, (ya + yb) / 2.0F, 10.0F, Palette.INK_SOFT);
			}
		}
	}

	/** Card tones: white-gold sword, ember pillar, violet dipper, gold-jade mountain seal. */
	private static final int[] DECREE_TONE = {0xFFC8A030, 0xFFD2622A, 0xFF8A5AD8, 0xFF2E8963};
}
