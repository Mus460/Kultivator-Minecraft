package dev.kultivator.client.screen;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.data.Technique;
import dev.kultivator.network.Actions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Root and Dao: on the left the five-element pentagram (Fire, Earth, Metal, Water, Wood around the circle of
 * generation, the star of overcoming inside) with the four mutant elements beneath it, lit by the player's root, and
 * the root's grade; on the right the Bagua octagon of the eight Daos around a turning yin-yang. A trigram is read on
 * hover and chosen with a click that must be repeated within 3 s to swear by that Dao.
 */
final class RootDaoPage extends Page {

	/** Clockwise from the top, so neighbours generate each other and the star lines overcome. */
	private static final Element[] WU_XING = {Element.FIRE, Element.EARTH, Element.METAL, Element.WATER, Element.WOOD};
	private static final Element[] MUTANTS = {Element.LIGHTNING, Element.ICE, Element.WIND, Element.SPACE};
	private static final long CONFIRM_MILLIS = 3000L;
	private static final int YANG = 0xFFF3EEDC;
	private static final int YIN = 0xFF1B2521;

	private final Eased[] tileGlow = new Eased[8];
	private final Eased comprehension = new Eased(4.0F);
	private int hoveredTile = -1;
	private int pendingTile = -1;
	private long pendingUntil;
	private long swornAt;
	private boolean builtRootButton;

	private int half;
	private float starX;
	private float starY;
	private float starR;
	private float nodeR;
	private float mutantY;
	private float gradeY;
	private float baguaX;
	private float baguaY;
	private float baguaR;
	private float tileH;
	private float tileW;
	private float captionY;

	RootDaoPage(CultivationScreen screen) {
		super(screen);
		for (int i = 0; i < tileGlow.length; i++) {
			tileGlow[i] = new Eased(12.0F);
		}
	}

	@Override
	Component title() {
		return Component.translatable("kultivator.screen.tab.root_dao");
	}

	@Override
	Component shortName() {
		return Component.translatable("kultivator.screen.tab.root_dao.short");
	}

	@Override
	protected void build() {
		half = (w - 6) / 2;
		float header = 13.0F;
		// Left: pentagram, mutant row, grade line.
		float availStar = h - header - 20.0F - 16.0F;
		float byHeight = (availStar - 16.0F) / 2.17F;
		float byWidth = (half / 2.0F - 15.0F) / 1.13F;
		starR = Math.max(16.0F, Math.min(byHeight, byWidth));
		nodeR = Math.max(4.5F, Math.min(9.0F, starR * 0.18F));
		float used = 1.81F * starR + 2.0F * nodeR + 16.0F + 20.0F + 16.0F;
		float top = y + header + Math.max(0.0F, (h - header - used) / 2.0F);
		starX = x + half / 2.0F;
		starY = top + 8.0F + nodeR + starR;
		mutantY = starY + 0.81F * starR + nodeR + 8.0F + 9.0F;
		gradeY = Math.min(y + h - 15.0F, mutantY + 14.0F);

		builtRootButton = StatusPage.mortalUntested(data());
		if (builtRootButton) {
			int bw = Math.min(110, half - 8);
			TalismanButton test = new TalismanButton(x + (half - bw) / 2, Math.round(gradeY) - 2, bw, 15,
					() -> Component.translatable("kultivator.screen.btn.root_test"));
			test.enabledWhen(() -> StatusPage.mortalUntested(data()));
			test.urgentWhen(() -> StatusPage.mortalUntested(data()));
			test.onPress(() -> send(Actions.ROOT_TEST));
			test.tooltip(() -> List.of(Component.translatable("kultivator.screen.btn.root_test"),
					Component.translatable("kultivator.screen.btn.root_test.tip")));
			add(test);
		}

		// Right: the Bagua.
		int rx = x + half + 6;
		int rw = w - half - 6;
		float caption = 22.0F;
		float diameter = Math.max(60.0F, Math.min(rw - 4.0F, h - header - caption - 2.0F));
		tileH = Math.max(9.0F, Math.min(16.0F, diameter * 0.13F));
		baguaR = diameter / 2.0F - tileH / 2.0F - 1.0F;
		tileW = 2.0F * baguaR * (float) Math.tan(Math.PI / 8.0D) * 0.88F;
		baguaX = rx + rw / 2.0F;
		baguaY = y + header + 1.0F + diameter / 2.0F;
		captionY = baguaY + diameter / 2.0F + 3.0F;
	}

	@Override
	boolean needsRebuild() {
		return builtRootButton != StatusPage.mortalUntested(data());
	}

	@Override
	void onData() {
		if (data().dao() != null) {
			pendingTile = -1;
		}
	}

	private static boolean eligible(CultivationData data) {
		return data.dao() == null && data.realm().atLeast(Realm.GOLDEN_CORE);
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		CultivationData data = data();
		long now = Util.getMillis();
		if (pendingTile >= 0 && now > pendingUntil) {
			pendingTile = -1;
		}
		heading(g, Component.translatable("kultivator.screen.root.header"), x + 2.0F, y, half - 4.0F, 0.85F);
		heading(g, Component.translatable("kultivator.screen.dao.header"), x + half + 8.0F, y, w - half - 10.0F, 0.85F);
		drawPentagram(g, data, mouseX, mouseY, now);
		g.nextStratum();
		drawBagua(g, data, mouseX, mouseY, seconds, now);
	}

	private void drawPentagram(GuiGraphicsExtractor g, CultivationData data, int mouseX, int mouseY, long now) {
		int mask = data.rootTested() ? data.rootElements() : 0;
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 400.0D);
		float[] px = new float[5];
		float[] py = new float[5];
		for (int i = 0; i < 5; i++) {
			double angle = -Math.PI / 2.0D + i * Math.PI * 2.0D / 5.0D;
			px[i] = starX + (float) Math.cos(angle) * starR;
			py[i] = starY + (float) Math.sin(angle) * starR;
		}
		// The circle of generation and the star of overcoming.
		Brush.ring(g, starX, starY, starR - 0.5F, starR + 0.5F, Palette.argb(0x60, Palette.INK_FAINT));
		for (int i = 0; i < 5; i++) {
			int j = (i + 2) % 5;
			boolean both = (mask & WU_XING[i].bit()) != 0 && (mask & WU_XING[j].bit()) != 0;
			Brush.line(g, px[i], py[i], px[j], py[j], both ? 1.3F : 0.8F, both ? Palette.GOLD : Palette.mix(Palette.PAPER_BOTTOM, Palette.INK_FAINT, 0.45F));
		}
		// The grade seal in the middle.
		float seal = Math.max(5.0F, nodeR * 1.15F);
		if (data.rootTested()) {
			RootGrade grade = data.rootGrade();
			Brush.disc(g, starX, starY, seal + 1.0F, Palette.GOLD_DARK);
			Brush.band(g, starX, starY, seal, -seal, seal, Palette.lighten(Palette.opaque(grade.color()), 0.2F), Palette.darken(Palette.opaque(grade.color()), 0.25F));
			Component multiplier = Component.literal("×" + trim(grade.multiplier()));
			Brush.fitCentered(g, font, multiplier, starX, starY - 2.5F, seal * 1.8F, Palette.INK, 0.6F, 0.4F, false);
		} else {
			Brush.disc(g, starX, starY, seal + 1.0F, Palette.PAPER_DEEP);
			Brush.disc(g, starX, starY, seal, Palette.PAPER_SHADE);
			Brush.centered(g, font, Brush.bold(Component.literal("?")), starX, starY - 3.5F, Palette.INK_SOFT, 0.9F, false);
		}

		Element hovered = null;
		for (int i = 0; i < 5; i++) {
			Element element = WU_XING[i];
			boolean lit = (mask & element.bit()) != 0;
			drawNode(g, element, px[i], py[i], nodeR, lit, pulse, false);
			float labelY = i == 0 ? py[i] - nodeR - 7.5F : py[i] + nodeR + 2.0F;
			Brush.fitCentered(g, font, element.displayName(), px[i], labelY, 40.0F, lit ? Palette.onPaper(element.color()) : Palette.INK_FAINT, 0.6F, 0.45F, false);
			if (near(mouseX, mouseY, px[i], py[i], nodeR + 2.0F)) {
				hovered = element;
			}
		}
		float spacing = Math.min(28.0F, (half - 10.0F) / 4.0F);
		float mutantSize = Math.max(3.5F, nodeR * 0.62F);
		for (int i = 0; i < MUTANTS.length; i++) {
			Element element = MUTANTS[i];
			float mx = starX + (i - 1.5F) * spacing;
			boolean lit = (mask & element.bit()) != 0;
			drawNode(g, element, mx, mutantY, mutantSize, lit, pulse, true);
			Brush.fitCentered(g, font, element.displayName(), mx, mutantY + mutantSize + 2.5F, spacing - 2.0F,
					lit ? Palette.onPaper(element.color()) : Palette.INK_FAINT, 0.5F, 0.4F, false);
			if (near(mouseX, mouseY, mx, mutantY, mutantSize + 2.0F)) {
				hovered = element;
			}
		}
		if (!builtRootButton) {
			Component grade = data.rootTested()
					? Component.translatable("kultivator.screen.root.grade", data.rootGrade().displayName(), trim(data.rootGrade().multiplier()))
					: Component.translatable("kultivator.screen.root.untested");
			int color = data.rootTested() ? Palette.onPaper(data.rootGrade().color()) : Palette.INK_FAINT;
			Brush.fitCentered(g, font, grade, starX, gradeY, half - 8.0F, color, 0.85F, 0.5F, false);
		}
		if (hovered != null) {
			screen.showTooltip(elementTip(data, hovered));
		}
	}

	private void drawNode(GuiGraphicsExtractor g, Element element, float cx, float cy, float r, boolean lit, float pulse, boolean diamond) {
		int color = Palette.opaque(element.color());
		if (lit) {
			Brush.halo(g, cx, cy, r + 0.5F, 3.5F, element.color(), 0.55F + 0.35F * pulse);
		}
		if (diamond) {
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate((float) (Math.PI / 4.0D));
			float s = r * 0.8F;
			Brush.rect(g, -s - 1.0F, -s - 1.0F, s + 1.0F, s + 1.0F, lit ? Palette.darken(color, 0.4F) : Palette.PAPER_DEEP);
			Brush.rect(g, -s, -s, s, s, lit ? color : Palette.PAPER_SHADE);
			if (lit) {
				Brush.rect(g, -s, -s, s * 0.1F, -s * 0.4F, Palette.argb(0x70, 0xFFFFFF));
			}
			g.pose().popMatrix();
			return;
		}
		Brush.disc(g, cx, cy, r + 1.0F, lit ? Palette.darken(color, 0.45F) : Palette.PAPER_DEEP);
		if (lit) {
			Brush.band(g, cx, cy, r, -r, r, Palette.lighten(color, 0.3F), Palette.darken(color, 0.2F));
			Brush.disc(g, cx - r * 0.35F, cy - r * 0.4F, r * 0.25F, 0x70FFFFFF);
		} else {
			Brush.disc(g, cx, cy, r, Palette.PAPER_SHADE);
			Brush.ring(g, cx, cy, r * 0.45F, r * 0.45F + 0.6F, Palette.argb(0x60, Palette.INK_FAINT));
		}
	}

	private List<Component> elementTip(CultivationData data, Element element) {
		List<Component> lines = new ArrayList<>();
		lines.add(element.displayName().copy().withColor(element.color()));
		if (element.mutant()) {
			lines.add(Component.translatable("kultivator.screen.root.mutant").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		boolean has = data.rootTested() && (data.rootElements() & element.bit()) != 0;
		lines.add(Component.translatable(!data.rootTested() ? "kultivator.screen.root.unknown"
				: has ? "kultivator.screen.root.has" : "kultivator.screen.root.lacks").withStyle(has ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		MutableComponent daos = Component.empty();
		boolean first = true;
		for (Dao dao : Dao.values()) {
			if (dao.hasAffinity(element.bit())) {
				if (!first) {
					daos.append(Component.literal(", "));
				}
				daos.append(dao.displayName().copy().withColor(dao.color()));
				first = false;
			}
		}
		if (!first) {
			lines.add(Component.translatable("kultivator.screen.root.affinity", daos));
		}
		return lines;
	}

	private void drawBagua(GuiGraphicsExtractor g, CultivationData data, int mouseX, int mouseY, float seconds, long now) {
		Dao chosen = data.dao();
		boolean eligible = eligible(data);
		int hover = tileAt(mouseX, mouseY);
		if (hover != hoveredTile) {
			hoveredTile = hover;
			if (hover >= 0) {
				UiSound.hover();
			}
		}
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 150.0D);

		// Double octagon outline joining the tiles.
		float outer = baguaR + tileH / 2.0F + 2.0F;
		float inner = baguaR - tileH / 2.0F - 2.0F;
		octagon(g, outer, Palette.GOLD_DARK);
		octagon(g, inner, Palette.argb(0xFF, Palette.mix(Palette.PAPER_BOTTOM, Palette.GOLD_DARK, 0.6F)));

		// The turning yin-yang, ringed by the chosen Dao's comprehension.
		float taiji = Math.max(6.0F, inner - 6.0F);
		float compT = comprehension.to(chosen == null ? 0.0F : (float) (data.comprehension(chosen) / 100.0D), seconds);
		if (chosen != null) {
			Brush.arc(g, baguaX, baguaY, taiji + 1.5F, taiji + 3.5F, 0.0F, 1.0F, Palette.mix(Palette.PAPER_BOTTOM, Palette.opaque(chosen.color()), 0.25F));
			Brush.arc(g, baguaX, baguaY, taiji + 1.5F, taiji + 3.5F, 0.0F, compT, Palette.opaque(chosen.color()));
		}
		float spin = (float) ((now % 20000L) / 20000.0D * Math.PI * 2.0D);
		drawTaiji(g, baguaX, baguaY, taiji, spin);

		for (int i = 0; i < 8; i++) {
			Dao dao = Glyphs.BAGUA[i];
			boolean isChosen = dao == chosen;
			float glow = tileGlow[i].to(i == hoveredTile ? 1.0F : 0.0F, seconds);
			int base = Palette.opaque(dao.color());
			int fill;
			if (chosen == null) {
				fill = Palette.mix(base, Palette.PAPER_TOP, 0.3F - 0.25F * glow);
			} else {
				fill = isChosen ? base : Palette.mix(base, Palette.PAPER_SHADE, 0.62F - 0.3F * glow);
			}
			g.pose().pushMatrix();
			g.pose().translate(baguaX, baguaY);
			g.pose().rotate((float) (i * Math.PI / 4.0D));
			g.pose().translate(0.0F, -baguaR - glow * 1.5F);
			float hw = tileW / 2.0F;
			float hh = tileH / 2.0F;
			if (isChosen) {
				Brush.rect(g, -hw - 3.0F, -hh - 3.0F, hw + 3.0F, hh + 3.0F, Palette.argb(Math.round(60 + 60 * pulse), Palette.GOLD_LIGHT));
			}
			if (i == pendingTile) {
				Brush.rect(g, -hw - 3.0F, -hh - 3.0F, hw + 3.0F, hh + 3.0F, Palette.argb(Math.round(70 + 110 * pulse), Palette.SEAL_GLOW));
			}
			Brush.rect(g, -hw + 0.8F, -hh + 1.2F, hw + 0.8F, hh + 1.2F, 0x40000000);
			Brush.rect(g, -hw, -hh, hw, hh, isChosen ? Palette.GOLD : Palette.darken(fill, 0.35F));
			Brush.rect(g, -hw + 1.0F, -hh + 1.0F, hw - 1.0F, hh - 1.0F, fill);
			int ink = Palette.luminance(fill) > 0.5F ? Palette.INK : YANG;
			Glyphs.trigram(g, 0.0F, 0.0F, tileW * 0.62F, Math.max(1.2F, tileH * 0.13F), tileH * 0.25F, Glyphs.trigramOf(dao), ink);
			g.pose().popMatrix();
		}

		// Caption under the octagon.
		float captionRoom = w - half - 10.0F;
		Component line1;
		Component line2 = null;
		int color1;
		if (chosen != null) {
			line1 = Component.translatable("kultivator.screen.dao.chosen", chosen.displayName(), Math.round(data.comprehension(chosen)));
			color1 = Palette.onPaper(chosen.color());
			line2 = Dao.comprehensionName(data.comprehension(chosen));
		} else if (pendingTile >= 0) {
			line1 = Component.translatable("kultivator.screen.dao.confirm", Glyphs.BAGUA[pendingTile].displayName());
			color1 = Palette.mix(Palette.SEAL, Palette.SEAL_GLOW, pulse);
		} else if (now - swornAt < 4000L) {
			line1 = Component.translatable("kultivator.screen.dao.sworn");
			color1 = Palette.GOLD_DARK;
		} else if (eligible) {
			line1 = Component.translatable("kultivator.screen.dao.choose");
			color1 = Palette.SEAL_DARK;
		} else {
			line1 = Component.translatable("kultivator.screen.dao.locked");
			color1 = Palette.INK_FAINT;
		}
		if (line2 == null && hoveredTile >= 0) {
			line2 = Glyphs.BAGUA[hoveredTile].displayName();
		}
		float cx = baguaX;
		Brush.fitCentered(g, font, line1, cx, captionY, captionRoom, color1, 0.8F, 0.5F, false);
		if (line2 != null) {
			int color2 = hoveredTile >= 0 && chosen == null ? Palette.onPaper(Glyphs.BAGUA[hoveredTile].color()) : Palette.INK_SOFT;
			Brush.fitCentered(g, font, line2, cx, captionY + 9.0F, captionRoom, color2, 0.7F, 0.45F, false);
		}
		if (hoveredTile >= 0) {
			screen.showTooltip(daoTip(data, Glyphs.BAGUA[hoveredTile], hoveredTile));
		}
	}

	private List<Component> daoTip(CultivationData data, Dao dao, int tile) {
		List<Component> lines = new ArrayList<>();
		lines.add(dao.displayName().copy().withColor(dao.color()));
		lines.add(Component.translatable("kultivator.screen.trigram." + dao.id()).withStyle(ChatFormatting.GRAY));
		double value = data.comprehension(dao);
		lines.add(Component.translatable("kultivator.screen.dao.comprehension", Math.round(value), Dao.comprehensionName(value)));
		if (data.rootTested() && dao.hasAffinity(data.rootElements())) {
			lines.add(Component.translatable("kultivator.screen.dao.affinity").withStyle(ChatFormatting.GREEN));
		}
		MutableComponent arts = Component.empty();
		boolean first = true;
		for (Technique technique : Technique.values()) {
			if (technique.dao() == dao) {
				if (!first) {
					arts.append(Component.literal(", "));
				}
				arts.append(technique.displayName());
				first = false;
			}
		}
		if (!first) {
			lines.add(Component.translatable("kultivator.screen.dao.arts", arts));
		}
		Dao chosen = data.dao();
		if (dao == chosen) {
			lines.add(Component.translatable("kultivator.screen.dao.yours").withStyle(ChatFormatting.GOLD));
		} else if (chosen != null) {
			lines.add(Component.translatable("kultivator.screen.dao.other").withStyle(ChatFormatting.DARK_GRAY));
		} else if (!data.realm().atLeast(Realm.GOLDEN_CORE)) {
			lines.add(Component.translatable("kultivator.screen.dao.locked").withStyle(ChatFormatting.RED));
		} else if (tile == pendingTile) {
			lines.add(Component.translatable("kultivator.screen.dao.confirm_tip").withStyle(ChatFormatting.RED));
		} else {
			lines.add(Component.translatable("kultivator.screen.dao.click").withStyle(ChatFormatting.YELLOW));
		}
		return lines;
	}

	/** An octagon whose edges touch the circle of radius {@code apothem}, flat sides facing each tile. */
	private void octagon(GuiGraphicsExtractor g, float apothem, int color) {
		float corner = apothem / (float) Math.cos(Math.PI / 8.0D);
		for (int k = 0; k < 8; k++) {
			double a0 = k * Math.PI / 4.0D - Math.PI / 8.0D;
			double a1 = a0 + Math.PI / 4.0D;
			float x0 = baguaX + (float) Math.sin(a0) * corner;
			float y0 = baguaY - (float) Math.cos(a0) * corner;
			float x1 = baguaX + (float) Math.sin(a1) * corner;
			float y1 = baguaY - (float) Math.cos(a1) * corner;
			Brush.line(g, x0, y0, x1, y1, 1.0F, color);
		}
	}

	/** A yin-yang of radius {@code r} turned by {@code angle}, made of spans in the rotated frame. */
	private static void drawTaiji(GuiGraphicsExtractor g, float cx, float cy, float r, float angle) {
		Brush.disc(g, cx, cy, r + 1.0F, Palette.GOLD_DARK);
		int fine = Brush.FINE;
		int big = Math.max(2, Math.round(r * fine));
		int small = big / 2;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(angle);
		g.pose().scale(1.0F / fine, 1.0F / fine);
		for (int row = -big; row < big; row++) {
			float yc = row + 0.5F;
			int outer = halfSpan(big, yc);
			if (outer <= 0) {
				continue;
			}
			if (row < 0) {
				// Top half: the small yang circle reaches into the yin side.
				int bulge = halfSpan(small, yc + small);
				fillSpan(g, -outer, -bulge, row, YIN);
				fillSpan(g, -bulge, outer, row, YANG);
			} else {
				// Bottom half: the small yin circle reaches into the yang side.
				int bulge = halfSpan(small, yc - small);
				fillSpan(g, -outer, bulge, row, YIN);
				fillSpan(g, bulge, outer, row, YANG);
			}
		}
		g.pose().popMatrix();
		float dot = Math.max(0.8F, r / 7.0F);
		double c = Math.cos(angle);
		double s = Math.sin(angle);
		// The eyes sit on the turned vertical axis, half a radius from the middle.
		float ex = (float) (-s * (-r / 2.0F));
		float ey = (float) (c * (-r / 2.0F));
		Brush.disc(g, cx + ex, cy + ey, dot, YIN);
		Brush.disc(g, cx - ex, cy - ey, dot, YANG);
	}

	private static int halfSpan(int radius, float offset) {
		float s = radius * (float) radius - offset * offset;
		return s <= 0.0F ? 0 : Math.round((float) Math.sqrt(s));
	}

	private static void fillSpan(GuiGraphicsExtractor g, int from, int to, int row, int color) {
		if (to > from) {
			g.fill(from, row, to, row + 1, color);
		}
	}

	// ================================================================== input

	private int tileAt(double mouseX, double mouseY) {
		double dx = mouseX - baguaX;
		double dy = mouseY - baguaY;
		double distance = Math.sqrt(dx * dx + dy * dy);
		if (Math.abs(distance - baguaR) > tileH / 2.0F + 2.0F) {
			return -1;
		}
		double angle = Math.atan2(dx, -dy);
		if (angle < 0.0D) {
			angle += Math.PI * 2.0D;
		}
		return (int) Math.round(angle / (Math.PI / 4.0D)) % 8;
	}

	@Override
	protected boolean clickedElsewhere(double mouseX, double mouseY) {
		int tile = tileAt(mouseX, mouseY);
		if (tile < 0) {
			return false;
		}
		CultivationData data = data();
		if (!eligible(data)) {
			UiSound.denied();
			return true;
		}
		long now = Util.getMillis();
		if (tile == pendingTile && now <= pendingUntil) {
			send(Actions.CHOOSE_DAO, Glyphs.BAGUA[tile].ordinal(), "");
			UiSound.press();
			pendingTile = -1;
			swornAt = now;
		} else {
			pendingTile = tile;
			pendingUntil = now + CONFIRM_MILLIS;
			UiSound.ask();
		}
		return true;
	}

	private static boolean near(double mouseX, double mouseY, float cx, float cy, float r) {
		double dx = mouseX - cx;
		double dy = mouseY - cy;
		return dx * dx + dy * dy <= r * r;
	}

	private static String trim(double value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}
}
