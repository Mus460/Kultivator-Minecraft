package dev.kultivator.client.tempa;

import dev.kultivator.client.Keys;
import dev.kultivator.client.dunia.DuniaClient;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.data.Element;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.Forging;
import dev.kultivator.tempa.WeaponSpirit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Diri › Pusaka (DESIGN 25.10, 43.5, G53; the only place for the natal treasure's summary): the natal treasure as a large
 * seal (the artifact itself, its grade ribbon, +refine, element frame and a spirit-years ring like the dantian rings) with
 * its lines from the real constants in a scroll beside it, the Ikat / Lepas, Tautkan and Lepas Tautan talismans, "Buka
 * Cincin" (the storage ring container screen; Esc comes back here, 43.8.5 / R2: {@link RingScreen} registers the way back itself), the
 * two linked-artifact slots and the forging skill bar. Asks the tempa module for its data while shown (43.0.12). All text
 * is drawn at 1.0 (43.0.5).
 */
public class PusakaPage extends Page {

	private static final String K = "kultivator.gui43.m01.pusaka.";
	private static final int BUTTON_H = 14;
	private static final int ROW_H = 14;
	private static final int LINE_H = 10;
	private static final float SEAL_R = 34.0F;

	private ItemStack natalStack = ItemStack.EMPTY;
	private CompoundTag natalArt = new CompoundTag();
	/** The tempa data object last read (a new sync replaces the object). */
	private @Nullable CompoundTag seen;

	/** The information lines beside the seal, wrapped to the column (rebuilt each frame). */
	private record Line(FormattedCharSequence text, int color) {
	}

	private final List<Line> lines = new ArrayList<>();
	private final DaftarGulir info = new DaftarGulir().judul(i -> true, LINE_H);

	// layout
	private float sealX;
	private float sealY;
	private float sealR;
	private int colX;
	private int topY1;
	private int linkedY;

	public PusakaPage() {
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine8.tab.pusaka");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine8.tab.pusaka.short");
	}

	@Override
	public int glyph() {
		return Glyphs.TREASURE;
	}

	@Override
	public String helpTopic() {
		return "pusaka_diri";
	}

	private static CompoundTag tempa() {
		return TempaClient.data();
	}

	private static boolean bound() {
		return tempa().getCompound("natal").isPresent();
	}

	@Override
	protected void build() {
		int bottom = y + h;
		linkedY = bottom - 16 - ROW_H - 2;
		int buttonY = linkedY - 3 - BUTTON_H;
		topY1 = buttonY - 3;
		sealR = Math.max(18.0F, Math.min(SEAL_R, (topY1 - y - 16) / 2.0F));
		sealX = x + sealR + 8.0F;
		sealY = y + 2.0F + sealR;
		colX = Math.round(sealX + sealR + 10.0F);

		int gap = 4;
		int bw = Math.max(TalismanButton.MIN_W, (w - gap * 3) / 4);
		int bx = x;
		add(new TalismanButton(bx, buttonY, bw, BUTTON_H, () -> Component.translatable(bound() ? "kultivator.tempa.page.unbind" : "kultivator.tempa.page.bind"))
				.tooltip(() -> List.of(Component.translatable(bound() ? "kultivator.tempa.page.unbind.tip" : "kultivator.tempa.page.bind.tip")))
				.enabledWhen(Keys::connected)
				.alasanWhen(PusakaPage::luring)
				.onPress(() -> send(Actions.TEMPA, 0, bound() ? "natal:unbind" : "natal:bind")));
		bx += bw + gap;
		add(new TalismanButton(bx, buttonY, bw, BUTTON_H, () -> Component.translatable("kultivator.tempa.page.link"))
				.tooltip(() -> List.of(Component.translatable("kultivator.tempa.page.link.tip")))
				.enabledWhen(Keys::connected)
				.alasanWhen(PusakaPage::luring)
				.onPress(() -> send(Actions.TEMPA, 0, "link")));
		bx += bw + gap;
		add(new TalismanButton(bx, buttonY, bw, BUTTON_H, () -> Component.translatable("kultivator.tempa.page.unlink"))
				.tooltip(() -> List.of(Component.translatable("kultivator.tempa.page.unlink.tip")))
				.enabledWhen(Keys::connected)
				.alasanWhen(PusakaPage::luring)
				.onPress(() -> send(Actions.TEMPA, 0, "unlink")));
		bx += bw + gap;
		// The storage ring opens as its container screen; Esc there comes back to this page (R2).
		add(new TalismanButton(bx, buttonY, x + w - bx, BUTTON_H, () -> Component.translatable(K + "cincin"))
				.tooltip(() -> List.of(Component.translatable(K + "cincin"), Component.translatable(K + "cincin.tip")))
				.enabledWhen(Keys::connected)
				.alasanWhen(PusakaPage::luring)
				.onPress(() -> send(Actions.TEMPA, 0, "ring:open")));
	}

	/** Why the talismans are dim: no server with the mod (the same reason as the Teknik pages). */
	private static Component luring() {
		return Component.translatable("kultivator.gui43.m02.luring");
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		super.onShow(fokus);
		send(Actions.TEMPA, 0, "sync");
	}

	@Override
	public void tick() {
		if (tempa() != seen) {
			onData();
		}
	}

	@Override
	public void onData() {
		seen = tempa();
		CompoundTag art = tempa().getCompoundOrEmpty("natal").getCompoundOrEmpty("art");
		if (art.isEmpty()) {
			natalStack = ItemStack.EMPTY;
			natalArt = new CompoundTag();
		} else if (!art.equals(natalArt)) {
			natalArt = art.copy();
			natalStack = ArtifactItem.fromData(art);
		}
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		drawSeal(g, sealX, sealY);
		drawLinked(g, x, linkedY, w);
		drawSkill(g, x, y + h - 4, w);
	}

	/** The lines beside the seal in a scroll (wheel, rail; never shrunk). */
	@Override
	protected void lukis(Lukis l) {
		int colW = x + w - colX;
		lines.clear();
		if (DuniaClient.tooBig(Payloads.DuniaSync.CH_TEMPA)) {
			line(Component.translatable("kultivator.sect_fix_feat.too_big"), Palette.SEAL_DARK, colW);
		}
		if (natalArt.isEmpty()) {
			line(Component.translatable("kultivator.tempa.page.none"), Palette.INK_SOFT, colW);
			line(Component.translatable("kultivator.tempa.page.how"), Palette.INK_FAINT, colW);
		} else {
			String form = ArtifactItem.form(natalArt);
			int grade = ArtifactItem.grade(natalArt);
			double ap = ArtifactItem.powerOf(natalArt) * ArtifactItem.NATAL_FACTOR;
			line(Brush.bold(ArtifactItem.displayName(natalArt)), Palette.opaque(ArtifactItem.GRADE_COLORS[grade]), colW);
			line(Component.translatable("kultivator.tempa.page.ap", ArtifactItem.fmt(ap)), Palette.INK, colW);
			line(ArtifactItem.effectLine(form, grade, ap), Palette.JADE_DARK, colW);
			int stage = ArtifactItem.spiritStage(natalArt);
			line(Component.translatable("kultivator.tempa.page.spirit", ArtifactItem.spiritName(stage), (int) ArtifactItem.spiritYears(natalArt)), 0xFF7A4CC0, colW);
			line(Component.translatable("kultivator.tempa.page.next." + Math.min(3, stage)), Palette.INK_FAINT, colW);
			boolean tempered = tempa().getCompoundOrEmpty("natal").getBooleanOr("tempered", false);
			line(Component.translatable(tempered ? "kultivator.tempa.preview.tempered" : "kultivator.tempa.preview.untempered"), Palette.INK_SOFT, colW);
		}
		long cd = tempa().getLongOr("bind_cd", 0L);
		if (cd > 0L) {
			line(Component.translatable("kultivator.tempa.natal.cooldown", cd / 20 / 60 + 1), Palette.WARN, colW);
		}
		info.gambar(l, "pusaka_info", colX, y, x + w, topY1, lines.size(), LINE_H,
				(lk, i, x0, y0, x1, y1, over, chosen) -> Brush.text(lk.g, font, lines.get(i).text(), x0, y0 + 1.0F, lines.get(i).color(), 1.0F, false),
				null, null);
	}

	private void line(Component text, int color, int width) {
		for (FormattedCharSequence line : font.split(text, Math.max(10, width - 6))) {
			lines.add(new Line(line, color));
		}
	}

	/** The seal: the spirit-years ring, the element frame, the artifact and the grade ribbon. */
	private void drawSeal(GuiGraphicsExtractor g, float cx, float cy) {
		float k = sealR / SEAL_R;
		Brush.disc(g, cx, cy, 34.0F * k, Palette.argb(0x50, Palette.PAPER_DEEP));
		Brush.ring(g, cx, cy, 30.0F * k, 33.0F * k, Palette.argb(0x60, Palette.INK_FAINT));
		if (!natalArt.isEmpty()) {
			int stage = ArtifactItem.spiritStage(natalArt);
			double years = ArtifactItem.spiritYears(natalArt);
			double goal = stage == 0 ? WeaponSpirit.SEED_YEARS : stage == 1 ? WeaponSpirit.ELEMENTARY_YEARS : WeaponSpirit.COMPLETE_YEARS;
			float fill = (float) Math.max(0.0D, Math.min(1.0D, years / goal));
			Brush.arc(g, cx, cy, 30.0F * k, 33.0F * k, 0.0F, Math.max(0.01F, fill), 0xFFAE6CFF);
			Element element = ArtifactItem.element(natalArt);
			int frame = element == null ? Palette.GOLD : Palette.opaque(element.color());
			Brush.ring(g, cx, cy, 25.0F * k, 27.0F * k, frame);
			Brush.disc(g, cx, cy, 24.0F * k, Palette.argb(0xC0, Palette.JADE_PALE));
			if (!natalStack.isEmpty()) {
				float s = k >= 0.75F ? 2.0F : 1.0F;
				g.pose().pushMatrix();
				g.pose().translate(cx - 8.0F * s, cy - 8.0F * s);
				g.pose().scale(s, s);
				g.item(natalStack, 0, 0);
				g.pose().popMatrix();
			}
			int grade = ArtifactItem.grade(natalArt);
			float ribbonY = cy + sealR + 2.0F;
			float half = sealR + 6.0F;
			Brush.rect(g, cx - half, ribbonY, cx + half, ribbonY + 11.0F, Palette.opaque(ArtifactItem.GRADE_COLORS[grade]));
			Component ribbon = Component.translatable("kultivator.tempa.page.ribbon", ArtifactItem.gradeName(grade).getString(),
					ArtifactItem.subName(ArtifactItem.sub(natalArt)), ArtifactItem.refine(natalArt));
			Brush.fitCentered(g, font, ribbon, cx, ribbonY + 2.0F, half * 2.0F - 4.0F, Palette.INK, 1.0F, 1.0F, false);
		} else {
			Brush.disc(g, cx, cy, 24.0F * k, Palette.argb(0x60, Palette.LOCKED_PAPER));
			Glyphs.tab(g, Glyphs.TREASURE, cx, cy, 18.0F * k, Palette.LOCKED);
		}
	}

	/** "Pusaka tertaut" and the two linked artifacts (or empty slots) in one row. */
	private void drawLinked(GuiGraphicsExtractor g, int lx, int ly, int lw) {
		Component label = Component.translatable("kultivator.tempa.page.linked");
		int labelW = Math.min(lw / 3, font.width(label) + 4);
		Brush.fit(g, font, label, lx, ly + 3.0F, labelW - 2, Palette.SEAL_DARK, 1.0F, 1.0F, false);
		ListTag linked = tempa().getListOrEmpty("linked");
		float slotW = (lw - labelW - 4.0F) / 2.0F;
		for (int i = 0; i < 2; i++) {
			int x0 = Math.round(lx + labelW + i * (slotW + 4.0F));
			panel(g, x0, ly, Math.round(x0 + slotW), ly + ROW_H);
			Component text;
			if (i < linked.size() && linked.get(i) instanceof CompoundTag entry) {
				CompoundTag art = entry.getCompoundOrEmpty("art");
				text = art.isEmpty() ? Component.translatable("kultivator.tempa.page.linked_away") : ArtifactItem.displayName(art);
			} else {
				text = Component.translatable("kultivator.tempa.page.linked_empty");
			}
			Brush.fit(g, font, text, x0 + 4.0F, ly + 3.0F, slotW - 8.0F, Palette.INK, 1.0F, 1.0F, false);
		}
	}

	/** The forging skill bar (label above, 4 px bar at {@code sy}). */
	private void drawSkill(GuiGraphicsExtractor g, float sx, float sy, float sw) {
		CompoundTag data = tempa();
		int level = data.getIntOr("skill", 0);
		double xp = data.getDoubleOr("xp", 0.0D);
		double from = data.getDoubleOr("xp_from", 0.0D);
		double to = data.getDoubleOr("xp_to", 10.0D);
		float fill = level >= 10 ? 1.0F : (float) Math.max(0.0D, Math.min(1.0D, (xp - from) / Math.max(1.0D, to - from)));
		Component label = Component.translatable("kultivator.tempa.page.skill", Forging.title(level), data.getIntOr("made", 0), data.getIntOr("refined", 0));
		Brush.fit(g, font, label, sx, sy - 10.0F, sw, Palette.INK_SOFT, 1.0F, 1.0F, false);
		g.fill(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + 4.0F), Palette.argb(0x60, Palette.INK));
		g.fill(Math.round(sx), Math.round(sy), Math.round(sx + sw * fill), Math.round(sy + 4.0F), Palette.GOLD);
	}

	private static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, Palette.argb(0x38, Palette.PAPER_DEEP));
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(0xC0, Palette.GOLD));
		g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0x30FFFFFF);
	}
}
