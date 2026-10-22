package dev.kultivator.client.sekte.kepribadian;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleScreen;
import dev.kultivator.client.disciple.DiscipleTab;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.kepribadian.KepribadianPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hati, module P's tab of the disciple scroll (DESIGN 36.2.1, 36.3.8, contract C36.P4): on the left the disciple's traits
 * (a carved seal per trait from {@code textures/sekte/kepribadian/sifat.png}, its name and what it does) and its six
 * strongest memories ({@code sekte.Ingatan}, green for the warm ones, red for the wounds); on the right the heart: a
 * loyalty ring with the state in its middle, the contentment and bond bars, the countdown of a disciple preparing to
 * leave, what helps today, and four talismans: Bicara (once a day), Beri Hadiah (the item in hand, three a day), Minta
 * Maaf di Aula (every three days, before the hall stele) and Usir dari Sekte (pressed twice). It reads the info key
 * {@code sekte_hati} the server adds through {@code DiscipleHooks.addInfo}; every button sends module P's own payload.
 */
public final class HatiTab extends DiscipleTab {

	static final Identifier SIFAT_ATLAS = Kultivator.id("textures/sekte/kepribadian/sifat.png");
	private static final String P = "kultivator.sekte.kepribadian.";
	private static final int BUTTON_H = 18;
	private static final long CONFIRM_MILLIS = 3000L;
	/** Axis colours: diligence, pride, loyalty, courage, ambition, heart. */
	static final int[] AXIS_RGB = {0x3F9A4E, 0xC08A1E, 0x3A6FC4, 0xC2412F, 0x7A4FC0, 0xC4527F};
	static final int[] STATE_RGB = {0x2E8963, 0xC9A227, 0xE5821C, 0xD5322A};

	private int columnW;
	private boolean viewed;
	private long expelArmedAt = -CONFIRM_MILLIS;

	public HatiTab(DiscipleScreen screen) {
		super(screen);
	}

	@Override
	public Component title() {
		return Component.translatable(P + "tab");
	}

	@Override
	public Component shortName() {
		return Component.translatable(P + "tab.short");
	}

	private CompoundTag hati() {
		return info().getCompoundOrEmpty("sekte_hati");
	}

	@Override
	protected void build() {
		columnW = Math.max(120, Math.min(200, Math.round(w * 0.48F)));
		int rightX = x + columnW + 8;
		int rightW = x + w - rightX;
		int top = y + h - BUTTON_H * 2 - 3;
		int half = (rightW - 3) / 2;
		TalismanButton talk = add(new TalismanButton(rightX, top, half, BUTTON_H, () -> Component.translatable(P + "btn.bicara")));
		talk.enabledWhen(() -> hati().getBooleanOr("talk", false));
		talk.urgentWhen(() -> hati().getBooleanOr("talk", false) && hati().getIntOr("state", 0) >= 1);
		talk.onPress(() -> sendHati(KepribadianPayloads.A_TALK, ""));
		talk.tooltip(() -> tip("btn.bicara", hati().getBooleanOr("talk", false) ? null : Component.translatable(P + "btn.bicara.sudah")));

		TalismanButton gift = add(new TalismanButton(rightX + half + 3, top, rightW - half - 3, BUTTON_H, () -> Component.translatable(P + "btn.hadiah",
				hati().getIntOr("gifts", 0))));
		gift.enabledWhen(() -> hati().getIntOr("gifts", 0) > 0);
		gift.onPress(() -> sendHati(KepribadianPayloads.A_GIFT, ""));
		gift.tooltip(() -> tip("btn.hadiah", hati().getIntOr("gifts", 0) > 0 ? null : Component.translatable(P + "btn.hadiah.cukup")));

		TalismanButton sorry = add(new TalismanButton(rightX, top + BUTTON_H + 3, half, BUTTON_H, () -> Component.translatable(P + "btn.maaf")));
		sorry.enabledWhen(() -> hati().getIntOr("maaf", 0) <= 0);
		sorry.urgentWhen(() -> hati().getIntOr("maaf", 0) <= 0 && hati().getIntOr("state", 0) >= 2);
		sorry.onPress(() -> sendHati(KepribadianPayloads.A_APOLOGY, ""));
		sorry.tooltip(() -> tip("btn.maaf", hati().getIntOr("maaf", 0) <= 0 ? null
				: Component.translatable(P + "btn.maaf.tunggu", hati().getIntOr("maaf", 0))));

		TalismanButton expel = add(new TalismanButton(rightX + half + 3, top + BUTTON_H + 3, rightW - half - 3, BUTTON_H,
				() -> Component.translatable(armed() ? P + "btn.usir.yakin" : P + "btn.usir")));
		expel.stampedWhen(this::armed);
		expel.enabledWhen(() -> !hati().getBooleanOr("sealed", false));
		expel.onPress(() -> {
			if (armed()) {
				expelArmedAt = -CONFIRM_MILLIS;
				sendHati(KepribadianPayloads.A_EXPEL, "confirm");
			} else {
				expelArmedAt = Util.getMillis();
			}
		});
		expel.tooltip(() -> tip("btn.usir", hati().getBooleanOr("sealed", false) ? Component.translatable(P + "btn.usir.terikat") : null));

		if (!viewed) {
			viewed = true;
			sendHati(KepribadianPayloads.A_VIEW, "");
		}
	}

	private boolean armed() {
		return Util.getMillis() - expelArmedAt < CONFIRM_MILLIS;
	}

	private List<Component> tip(String key, Component reason) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(P + key).withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable(P + key + ".desc"));
		if (reason != null) {
			lines.add(reason.copy().withStyle(ChatFormatting.RED));
		}
		return lines;
	}

	/** Module P's own payload (never the scroll's DISCIPLE_* actions). */
	private void sendHati(int action, String arg) {
		KepribadianPayloads.Aksi payload = new KepribadianPayloads.Aksi(screen.entityId(), action, arg);
		if (ClientPlayNetworking.canSend(payload.type())) {
			ClientPlayNetworking.send(payload);
		}
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CompoundTag hati = hati();
		if (hati.isEmpty() || !hati.getBooleanOr("known", false)) {
			heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
			Brush.wrap(g, font, Component.translatable(P + "tab.kosong"), x + 4.0F, y + 16.0F, w - 8.0F, Palette.INK_SOFT, 0.75F, false);
			return;
		}
		drawTraits(g, hati, mouseX, mouseY);
		drawHeart(g, hati, mouseX, mouseY);
	}

	private void drawTraits(GuiGraphicsExtractor g, CompoundTag hati, int mouseX, int mouseY) {
		float x0 = x + 2.0F;
		float width = columnW - 4.0F;
		heading(g, Component.translatable(P + "tab.watak"), x0, y, width, 0.8F);
		float cy = y + 14.0F;
		ListTag traits = hati.getListOrEmpty("sifat");
		for (int i = 0; i < traits.size(); i++) {
			Sifat sifat = Sifat.byId(traits.getStringOr(i, ""));
			if (sifat == null) {
				continue;
			}
			int rgb = AXIS_RGB[sifat.axis()];
			float rowH = 24.0F;
			Brush.rect(g, x0, cy, x0 + width, cy + rowH - 2.0F, Palette.argb(0x30, Palette.PAPER_DEEP));
			Brush.rect(g, x0, cy, x0 + 2.0F, cy + rowH - 2.0F, Palette.opaque(rgb));
			int icon = sifat.ordinal();
			g.blit(RenderPipelines.GUI_TEXTURED, SIFAT_ATLAS, Math.round(x0 + 4.0F), Math.round(cy + 3.0F), (icon % 4) * 16.0F, (icon / 4) * 16.0F, 16, 16,
					16, 16, 64, 48, 0xFFFFFFFF);
			float tx = x0 + 23.0F;
			float tw = x0 + width - tx - 2.0F;
			Brush.fit(g, font, Brush.bold(Component.translatable(sifat.key())), tx, cy + 2.0F, tw, Palette.onPaper(rgb), 0.78F, 0.5F, false);
			Brush.fit(g, font, Component.translatable(sifat.key() + ".desc"), tx, cy + 12.0F, tw, Palette.INK_SOFT, 0.58F, 0.4F, false);
			if (mouseX >= x0 && mouseX < x0 + width && mouseY >= cy && mouseY < cy + rowH - 2.0F) {
				screen.showTooltip(List.of(Component.translatable(sifat.key()).withColor(rgb), Component.translatable(sifat.key() + ".desc"),
						Component.translatable(P + "efek." + sifat.id()).withStyle(ChatFormatting.DARK_GREEN),
						Component.translatable(P + "sumber").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			}
			cy += rowH;
		}
		String lure = hati.getStringOr("bujuk", "");
		if (!lure.isEmpty()) {
			Brush.fit(g, font, Component.translatable(P + "tab.bujuk", lure), x0, cy, width, Palette.DANGER, 0.6F, 0.4F, false);
			cy += 9.0F;
		}
		cy += 2.0F;
		heading(g, Component.translatable(P + "tab.ingatan"), x0, cy, width, 0.7F);
		cy += 12.0F;
		ListTag memories = hati.getListOrEmpty("mem");
		if (memories.isEmpty()) {
			Brush.fit(g, font, Component.translatable(P + "tab.ingatan.kosong"), x0 + 2.0F, cy, width - 2.0F, Palette.INK_FAINT, 0.6F, 0.45F, false);
			return;
		}
		float bottom = y + h - 2.0F;
		for (int i = 0; i < memories.size() && cy + 9.0F <= bottom; i++) {
			CompoundTag m = memories.getCompoundOrEmpty(i);
			int weight = m.getIntOr("w", 0);
			int rgb = weight >= 0 ? 0x2E8963 : 0xB3281D;
			float strength = Math.min(1.0F, Math.abs(weight) / 10.0F);
			Brush.dot(g, x0 + 3.0F, cy + 3.5F, 1.4F + strength * 1.4F, Palette.opaque(rgb));
			Component text = Component.translatable(m.getStringOr("t", ""), m.getStringOr("a", ""));
			Component ago = Component.translatable(P + "tab.lalu", m.getLongOr("ago", 0L));
			float agoW = font.width(ago) * 0.5F;
			Brush.fit(g, font, text, x0 + 8.0F, cy, width - 12.0F - agoW, Palette.INK, 0.6F, 0.4F, false);
			Brush.text(g, font, ago, x0 + width - agoW, cy + 1.0F, Palette.INK_FAINT, 0.5F, false);
			if (mouseX >= x0 && mouseX < x0 + width && mouseY >= cy - 1 && mouseY < cy + 8.0F) {
				screen.showTooltip(List.of(text, Component.translatable(P + "tab.bobot", weight > 0 ? "+" + weight : Integer.toString(weight),
						m.getStringOr("m", "")).withStyle(ChatFormatting.GRAY)));
			}
			cy += 9.0F;
		}
	}

	private void drawHeart(GuiGraphicsExtractor g, CompoundTag hati, int mouseX, int mouseY) {
		float x0 = x + columnW + 8.0F;
		float width = x + w - x0 - 2.0F;
		heading(g, Component.translatable(P + "tab.hati"), x0, y, width, 0.8F);
		int state = Math.max(0, Math.min(3, hati.getIntOr("state", 0)));
		int loyalty = hati.getIntOr("loy", 0);
		int stateRgb = STATE_RGB[state];
		String stateId = new String[]{"setia", "gelisah", "membangkang", "akan_pergi"}[state];
		float r = 17.0F;
		float cx = x0 + r + 3.0F;
		float cy = y + 15.0F + r;
		float blink = state == 3 ? 0.55F + 0.45F * (float) Math.abs(Math.sin(Util.getMillis() / 300.0D)) : 1.0F;
		Brush.halo(g, cx, cy, r, 3.0F, stateRgb, 0.35F * blink);
		Brush.ring(g, cx, cy, r - 4.0F, r, Palette.argb(0x60, Palette.PAPER_DEEP));
		Brush.arc(g, cx, cy, r - 4.0F, r, 0.0F, Math.max(0.0F, Math.min(1.0F, loyalty / 100.0F)), Palette.opaque(stateRgb));
		int threshold = hati.getIntOr("thr", 15);
		for (int mark : new int[]{threshold, 30, 50}) {
			Brush.arc(g, cx, cy, r - 5.0F, r + 1.0F, mark / 100.0F - 0.004F, mark / 100.0F + 0.004F, Palette.INK_SOFT);
		}
		Brush.disc(g, cx, cy, r - 5.0F, Palette.argb(0x40, stateRgb));
		Brush.fitCentered(g, font, Brush.bold(Component.literal(Integer.toString(loyalty))), cx, cy - 6.0F, r * 1.4F, Palette.onPaper(stateRgb),
				1.0F, 0.6F, false);
		Brush.fitCentered(g, font, Component.translatable(P + "tab.setia_singkat"), cx, cy + 3.0F, r * 1.6F, Palette.INK_SOFT, 0.5F, 0.4F, false);
		if (mouseX >= cx - r && mouseX < cx + r && mouseY >= cy - r && mouseY < cy + r) {
			screen.showTooltip(List.of(Component.translatable(P + "tab.kesetiaan", loyalty).withStyle(ChatFormatting.GOLD),
					Component.translatable(P + "tab.kesetiaan.desc"), Component.translatable(P + "tab.ambang", threshold)));
		}

		float tx = cx + r + 6.0F;
		float tw = x0 + width - tx;
		Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.sekte.keadaan." + stateId)), tx, y + 15.0F, tw,
				Palette.onPaper(stateRgb), 0.95F, 0.55F, false);
		Brush.fit(g, font, Component.translatable(P + "tab.keadaan." + stateId), tx, y + 25.0F, tw, Palette.INK_SOFT, 0.55F, 0.4F, false);
		int left = hati.getIntOr("left", -1);
		int pending = hati.getIntOr("pend", 0);
		if (pending > 0) {
			Brush.fit(g, font, Component.translatable(P + "tab.pend." + pending), tx, y + 34.0F, tw, Palette.DANGER, 0.6F, 0.4F, false);
		} else if (left >= 0) {
			Brush.fit(g, font, Component.translatable(P + "tab.sisa", left), tx, y + 34.0F, tw, Palette.opaque(0xD5322A), 0.66F, 0.45F, false);
		} else if (hati.getBooleanOr("sealed", false)) {
			Brush.fit(g, font, Component.translatable(P + "tab.terikat"), tx, y + 34.0F, tw, Palette.opaque(0x8A4FC0), 0.58F, 0.4F, false);
		}
		float by = cy + r + 7.0F;
		by = bar(g, x0, by, width, Component.translatable(P + "tab.kepuasan", hati.getIntOr("k", 0)), hati.getIntOr("k", 0) / 100.0F,
				0xD9A23A, mouseX, mouseY, List.of(Component.translatable(P + "tab.kepuasan.desc")));
		by = bar(g, x0, by, width, Component.translatable(P + "tab.ikatan", hati.getIntOr("bond", 0)), hati.getIntOr("bond", 0) / 100.0F,
				0x47B2FF, mouseX, mouseY, List.of(Component.translatable(P + "tab.ikatan.desc")));
		double work = hati.getDoubleOr("wf", 1.0D);
		double care = hati.getDoubleOr("care", 0.0D);
		int neglect = hati.getIntOr("neglect", 0);
		int nd = Math.max(1, hati.getIntOr("nd", 5));
		Component workLine = Component.translatable(P + "tab.kerja", String.format(Locale.ROOT, "%.2f", work));
		Brush.fit(g, font, workLine, x0, by, width, work >= 1.0D ? Palette.JADE_DARK : Palette.SEAL_DARK, 0.6F, 0.4F, false);
		by += 8.0F;
		Component careLine = neglect >= nd - 1 && care <= 0.0D ? Component.translatable(P + "tab.abai", neglect, nd)
				: Component.translatable(P + "tab.rawat", String.format(Locale.ROOT, "%+.0f", care));
		Brush.fit(g, font, careLine, x0, by, width, neglect >= nd - 1 && care <= 0.0D ? Palette.DANGER : Palette.INK_SOFT, 0.6F, 0.4F, false);
	}

	/** A labelled bar 0-1; returns the y below it. */
	private float bar(GuiGraphicsExtractor g, float x0, float y0, float width, Component label, float value, int rgb, int mouseX, int mouseY,
			List<Component> tooltip) {
		Brush.fit(g, font, label, x0, y0, width, Palette.INK, 0.62F, 0.42F, false);
		float top = y0 + 7.0F;
		Brush.rect(g, x0, top, x0 + width, top + 4.0F, Palette.argb(0x50, Palette.PAPER_DEEP));
		float v = Math.max(0.0F, Math.min(1.0F, value));
		Brush.rect(g, x0, top, x0 + width * v, top + 4.0F, Palette.opaque(rgb));
		Brush.rect(g, x0, top, x0 + width * v, top + 1.0F, Palette.argb(0x60, 0xFFFFFF));
		if (mouseX >= x0 && mouseX < x0 + width && mouseY >= y0 && mouseY < top + 4.0F) {
			List<Component> lines = new ArrayList<>();
			lines.add(label);
			lines.addAll(tooltip);
			screen.showTooltip(lines);
		}
		return top + 8.0F;
	}
}
