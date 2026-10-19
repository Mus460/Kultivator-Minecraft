package dev.kultivator.client.tempa;

import dev.kultivator.client.dunia.DuniaPage;
import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.data.Element;
import dev.kultivator.network.Actions;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.Forging;
import dev.kultivator.tempa.WeaponSpirit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The Pusaka tablet of the Dunia screen (DESIGN 25.10, owned by P8-tempa): the natal treasure as a large seal (the artifact
 * itself, its grade ribbon, +refine, element frame and a spirit-years ring like the dantian rings) with stat lines from the
 * real constants, the Ikat / Lepas and Tautkan / Lepas Tautan talismans, the two linked-artifact slots and the forging
 * skill bar.
 */
public class PusakaPage extends DuniaPage {

	private ItemStack natalStack = ItemStack.EMPTY;
	private CompoundTag natalArt = new CompoundTag();

	public PusakaPage(DuniaScreen screen) {
		super(screen);
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
	public int icon() {
		return Glyphs.TREASURE;
	}

	private static CompoundTag data() {
		return TempaClient.data();
	}

	private static boolean bound() {
		return data().getCompound("natal").isPresent();
	}

	@Override
	protected void build() {
		int bx = x + 4;
		int by = y + h - 38;
		add(new TalismanButton(bx, by, 60, 13, () -> Component.translatable(bound() ? "kultivator.tempa.page.unbind" : "kultivator.tempa.page.bind"))
				.tooltip(() -> List.of(Component.translatable(bound() ? "kultivator.tempa.page.unbind.tip" : "kultivator.tempa.page.bind.tip")))
				.onPress(() -> send(Actions.TEMPA, 0, bound() ? "natal:unbind" : "natal:bind")));
		add(new TalismanButton(bx + 64, by, 60, 13, () -> Component.translatable("kultivator.tempa.page.link"))
				.tooltip(() -> List.of(Component.translatable("kultivator.tempa.page.link.tip")))
				.onPress(() -> send(Actions.TEMPA, 0, "link")));
		add(new TalismanButton(bx + 128, by, 66, 13, () -> Component.translatable("kultivator.tempa.page.unlink"))
				.tooltip(() -> List.of(Component.translatable("kultivator.tempa.page.unlink.tip")))
				.onPress(() -> send(Actions.TEMPA, 0, "unlink")));
	}

	@Override
	public void onData() {
		CompoundTag natal = data().getCompoundOrEmpty("natal");
		CompoundTag art = natal.getCompoundOrEmpty("art");
		if (art.isEmpty()) {
			natalStack = ItemStack.EMPTY;
			natalArt = new CompoundTag();
		} else if (!art.equals(natalArt)) {
			natalArt = art.copy();
			natalStack = ArtifactItem.fromData(art);
		}
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		heading(g, title(), x + 2.0F, y, w - 4.0F, 0.8F);
		float sealX = x + 46.0F;
		float sealY = y + 62.0F;
		drawSeal(g, sealX, sealY);
		float colX = x + 96.0F;
		float colW = w - 100.0F;
		float ly = y + 14.0F;
		if (natalArt.isEmpty()) {
			ly += Brush.wrap(g, font, Component.translatable("kultivator.tempa.page.none"), colX, ly, colW, Palette.INK_SOFT, 0.65F, false) + 3.0F;
			ly += Brush.wrap(g, font, Component.translatable("kultivator.tempa.page.how"), colX, ly, colW, Palette.INK_FAINT, 0.55F, false) + 3.0F;
		} else {
			String form = ArtifactItem.form(natalArt);
			int grade = ArtifactItem.grade(natalArt);
			double ap = ArtifactItem.powerOf(natalArt) * ArtifactItem.NATAL_FACTOR;
			Brush.fit(g, font, Brush.bold(ArtifactItem.displayName(natalArt)), colX, ly, colW, Palette.opaque(ArtifactItem.GRADE_COLORS[grade]), 0.75F, 0.45F, false);
			ly += 9.0F;
			ly += Brush.wrap(g, font, Component.translatable("kultivator.tempa.page.ap", ArtifactItem.fmt(ap)), colX, ly, colW, Palette.INK, 0.6F, false) + 1.0F;
			ly += Brush.wrap(g, font, ArtifactItem.effectLine(form, grade, ap), colX, ly, colW, Palette.JADE_DARK, 0.55F, false) + 2.0F;
			int stage = ArtifactItem.spiritStage(natalArt);
			Component spirit = Component.translatable("kultivator.tempa.page.spirit", ArtifactItem.spiritName(stage), (int) ArtifactItem.spiritYears(natalArt));
			ly += Brush.wrap(g, font, spirit, colX, ly, colW, 0xFF7A4CC0, 0.6F, false) + 1.0F;
			ly += Brush.wrap(g, font, Component.translatable("kultivator.tempa.page.next." + Math.min(3, stage)), colX, ly, colW, Palette.INK_FAINT,
					0.5F, false) + 1.0F;
			boolean tempered = data().getCompoundOrEmpty("natal").getBooleanOr("tempered", false);
			ly += Brush.wrap(g, font, Component.translatable(tempered ? "kultivator.tempa.preview.tempered" : "kultivator.tempa.preview.untempered"),
					colX, ly, colW, Palette.INK_SOFT, 0.5F, false) + 2.0F;
		}
		long cd = data().getLongOr("bind_cd", 0L);
		if (cd > 0L) {
			ly += Brush.wrap(g, font, Component.translatable("kultivator.tempa.natal.cooldown", cd / 20 / 60 + 1), colX, ly, colW, Palette.WARN, 0.5F, false);
		}
		drawLinked(g, x + 4.0F, y + 112.0F, w - 8.0F);
		drawSkill(g, x + 4.0F, y + h - 8.0F, w - 8.0F);
	}

	/** The seal: the spirit-years ring, the element frame, the artifact and the grade ribbon. */
	private void drawSeal(GuiGraphicsExtractor g, float cx, float cy) {
		Brush.disc(g, cx, cy, 34.0F, Palette.argb(0x50, Palette.PAPER_DEEP));
		Brush.ring(g, cx, cy, 30.0F, 33.0F, Palette.argb(0x60, Palette.INK_FAINT));
		if (!natalArt.isEmpty()) {
			int stage = ArtifactItem.spiritStage(natalArt);
			double years = ArtifactItem.spiritYears(natalArt);
			double goal = stage == 0 ? WeaponSpirit.SEED_YEARS : stage == 1 ? WeaponSpirit.ELEMENTARY_YEARS : WeaponSpirit.COMPLETE_YEARS;
			float fill = (float) Math.max(0.0D, Math.min(1.0D, years / goal));
			Brush.arc(g, cx, cy, 30.0F, 33.0F, 0.0F, Math.max(0.01F, fill), 0xFFAE6CFF);
			Element element = ArtifactItem.element(natalArt);
			int frame = element == null ? Palette.GOLD : Palette.opaque(element.color());
			Brush.ring(g, cx, cy, 25.0F, 27.0F, frame);
			Brush.disc(g, cx, cy, 24.0F, Palette.argb(0xC0, Palette.JADE_PALE));
			if (!natalStack.isEmpty()) {
				g.pose().pushMatrix();
				g.pose().translate(cx - 16.0F, cy - 16.0F);
				g.pose().scale(2.0F, 2.0F);
				g.item(natalStack, 0, 0);
				g.pose().popMatrix();
			}
			int grade = ArtifactItem.grade(natalArt);
			float ribbonY = cy + 36.0F;
			Brush.rect(g, cx - 34.0F, ribbonY, cx + 34.0F, ribbonY + 10.0F, Palette.opaque(ArtifactItem.GRADE_COLORS[grade]));
			Component ribbon = Component.translatable("kultivator.tempa.page.ribbon", ArtifactItem.gradeName(grade).getString(),
					ArtifactItem.subName(ArtifactItem.sub(natalArt)), ArtifactItem.refine(natalArt));
			Brush.fitCentered(g, font, ribbon, cx, ribbonY + 2.0F, 64.0F, Palette.INK, 0.6F, 0.4F, false);
		} else {
			Brush.disc(g, cx, cy, 24.0F, Palette.argb(0x60, Palette.LOCKED_PAPER));
			Glyphs.tab(g, Glyphs.TREASURE, cx, cy, 18.0F, Palette.LOCKED);
		}
	}

	/** The two linked artifacts (or empty slots). */
	private void drawLinked(GuiGraphicsExtractor g, float lx, float ly, float lw) {
		Brush.text(g, font, Component.translatable("kultivator.tempa.page.linked"), lx, ly, Palette.SEAL_DARK, 0.6F, false);
		ListTag linked = data().getListOrEmpty("linked");
		float slotW = (lw - 4.0F) / 2.0F;
		for (int i = 0; i < 2; i++) {
			int x0 = Math.round(lx + i * (slotW + 4.0F));
			int y0 = Math.round(ly + 8.0F);
			panel(g, x0, y0, Math.round(x0 + slotW), y0 + 16);
			Component text;
			if (i < linked.size() && linked.get(i) instanceof CompoundTag entry) {
				CompoundTag art = entry.getCompoundOrEmpty("art");
				text = art.isEmpty() ? Component.translatable("kultivator.tempa.page.linked_away") : ArtifactItem.displayName(art);
			} else {
				text = Component.translatable("kultivator.tempa.page.linked_empty");
			}
			Brush.fit(g, font, text, x0 + 4.0F, y0 + 5.0F, slotW - 8.0F, Palette.INK, 0.55F, 0.35F, false);
		}
	}

	/** The forging skill bar. */
	private void drawSkill(GuiGraphicsExtractor g, float sx, float sy, float sw) {
		CompoundTag data = data();
		int level = data.getIntOr("skill", 0);
		double xp = data.getDoubleOr("xp", 0.0D);
		double from = data.getDoubleOr("xp_from", 0.0D);
		double to = data.getDoubleOr("xp_to", 10.0D);
		float fill = level >= 10 ? 1.0F : (float) Math.max(0.0D, Math.min(1.0D, (xp - from) / Math.max(1.0D, to - from)));
		Component label = Component.translatable("kultivator.tempa.page.skill", Forging.title(level), data.getIntOr("made", 0), data.getIntOr("refined", 0));
		Brush.fit(g, font, label, sx, sy - 9.0F, sw, Palette.INK_SOFT, 0.55F, 0.4F, false);
		g.fill(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + 4.0F), Palette.argb(0x60, Palette.INK));
		g.fill(Math.round(sx), Math.round(sy), Math.round(sx + sw * fill), Math.round(sy + 4.0F), Palette.GOLD);
	}
}
