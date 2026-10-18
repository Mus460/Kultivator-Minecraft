package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The Simpul panel (DESIGN 24.7.1): the transport nodes (M3, L5) with name, grade, Daya, distance and the loaded flag;
 * <i>Ganti Nama</i> for the owner (typed in place, Enter to save) and <i>Peta</i>, which opens the node map from that node.
 */
final class NodePanel {

	static final int ROW_H = 20;

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
		Font font = page.font();
		CompoundTag b = page.b();
		page.title(g, Component.translatable("kultivator.formasiui.nodes.title"), x + 2.0F, y + 1.0F, w - 6.0F, 0.7F);
		float top = y + 13.0F;
		float view = h - 13.0F;
		List<CompoundTag> nodes = new ArrayList<>();
		for (Tag tag : b.getListOrEmpty("nodes")) {
			if (tag instanceof CompoundTag node) {
				nodes.add(node);
			}
		}
		if (!b.getBooleanOr("transport", true)) {
			Brush.wrap(g, font, Component.translatable("kultivator.formasiui.nodes.disabled"), x + 4.0F, top + 2.0F, w - 10.0F, Palette.DANGER, 0.6F, false);
			top += 12.0F;
			view -= 12.0F;
		}
		float content = Math.max(1, nodes.size()) * ROW_H + 20.0F;
		float offset = page.clampScroll(3, content, view);
		float cursor = top - offset;
		g.enableScissor(x, Math.round(top), x + w, Math.round(top + view));
		if (nodes.isEmpty()) {
			Brush.wrap(g, font, Component.translatable("kultivator.formasiui.nodes.empty"), x + 4.0F, cursor + 2.0F, w - 10.0F, Palette.INK_SOFT, 0.6F, false);
			cursor += ROW_H;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		for (CompoundTag node : nodes) {
			if (cursor + ROW_H >= top && cursor <= top + view) {
				row(page, g, font, node, player, x, cursor, w, mouseX, mouseY, top, top + view);
			}
			cursor += ROW_H;
		}
		if (cursor + 20.0F >= top && cursor <= top + view) {
			Brush.wrap(g, font, Component.translatable("kultivator.formasiui.nodes.hint"), x + 4.0F, cursor + 3.0F, w - 10.0F, Palette.INK_FAINT, 0.5F, false);
		}
		g.disableScissor();
		FormasiPage.bead(g, x + w, Math.round(top), Math.round(top + view), offset, Math.max(0.0F, content - view));
	}

	private static void row(FormasiPage page, GuiGraphicsExtractor g, Font font, CompoundTag node, LocalPlayer player, int x, float ry, int w,
			int mouseX, int mouseY, float clipTop, float clipBottom) {
		int id = node.getIntOr("id", -1);
		FormationSpec spec = FormationRegistry.byId(node.getStringOr("spec", ""));
		int state = node.getIntOr("st", 0);
		boolean visible = ry >= clipTop - 1.0F && ry + ROW_H <= clipBottom + 1.0F;
		g.fill(x, Math.round(ry), x + w - 3, Math.round(ry + ROW_H - 1), Palette.argb(0x28, Palette.PAPER_DEEP));
		boolean ancient = spec != null && "ancient_transfer".equals(spec.id());
		int color = ancient ? 0xFFE8D8FF : 0xFFB89CE8;
		Brush.disc(g, x + 8.0F, ry + ROW_H / 2.0F - 0.5F, 5.5F, Palette.mix(color, Palette.INK, 0.25F));
		Brush.disc(g, x + 8.0F, ry + ROW_H / 2.0F - 0.5F, 3.0F, color);
		boolean bad = state == 1 || state == 2 || state == 0;
		if (bad) {
			Brush.disc(g, x + 12.0F, ry + 4.0F, 2.2F, Palette.SEAL);
		}
		float nameX = x + 17.0F;
		float buttons = page.canEdit() ? 74.0F : 38.0F;
		float textW = w - (nameX - x) - buttons - 6.0F;
		if (page.renaming() == id) {
			boolean caret = (Util.getMillis() / 400L) % 2L == 0L;
			Brush.rect(g, nameX - 1.0F, ry + 1.0F, nameX + textW, ry + 10.0F, 0xFFFFF8E0);
			Brush.frame(g, Math.round(nameX - 1.0F), Math.round(ry + 1.0F), Math.round(nameX + textW), Math.round(ry + 10.0F), Palette.SEAL);
			Brush.fit(g, font, Component.literal(page.renameText() + (caret ? "_" : "")), nameX + 1.0F, ry + 2.5F, textW - 2.0F, Palette.INK, 0.62F, 0.4F,
					false);
		} else {
			Brush.fit(g, font, Brush.bold(Component.literal(node.getStringOr("name", "?"))), nameX, ry + 2.0F, textW, Palette.INK, 0.62F, 0.4F, false);
		}
		String distance = "?";
		boolean sameDim = player != null && player.level().dimension().identifier().toString().equals(node.getStringOr("dim", ""));
		if (sameDim) {
			double dx = player.getX() - node.getIntOr("x", 0) - 0.5D;
			double dz = player.getZ() - node.getIntOr("z", 0) - 0.5D;
			distance = Long.toString(Math.round(Math.sqrt(dx * dx + dz * dz)));
		}
		Component line = Component.translatable("kultivator.formasiui.nodes.line", spec == null ? Component.literal("?") : spec.grade().label(),
				FormasiPage.daya(node.getLongOr("daya", 0L) / 100L), distance,
				Component.translatable(node.getBooleanOr("loaded", false) ? "kultivator.formasiui.nodes.loaded" : "kultivator.formasiui.nodes.unloaded"),
				Component.translatable(node.getBooleanOr("out", false) ? "kultivator.formasiui.nodes.outpost" : "kultivator.formasiui.nodes.inside"));
		Brush.fit(g, font, line, nameX, ry + 11.0F, textW, Palette.INK_FAINT, 0.45F, 0.32F, false);
		List<Component> tip = new ArrayList<>();
		tip.add(Component.literal(node.getStringOr("name", "?")));
		if (spec != null) {
			tip.add(spec.name());
		}
		tip.add(FormasiPage.stateName(state).copy().withColor(FormasiPage.stateColor(state) & 0xFFFFFF));
		tip.add(Component.translatable("kultivator.formasiui.cores.at", node.getIntOr("x", 0), node.getIntOr("y", 0), node.getIntOr("z", 0)));
		if (visible) {
			page.hit(x, ry, nameX + textW, ry + ROW_H - 1, null, tip);
		}
		float bx = x + w - buttons - 3.0F;
		if (!visible) {
			return;
		}
		page.talisman(g, bx, ry + 4.0F, 34.0F, 11.0F, Component.translatable("kultivator.formasiui.nodes.map"), true, () -> page.openNodeMap(id), mouseX,
				mouseY, List.of(Component.translatable("kultivator.formasiui.nodes.map.tip")));
		if (page.canEdit()) {
			boolean renaming = page.renaming() == id;
			page.talisman(g, bx + 37.0F, ry + 4.0F, 34.0F, 11.0F,
					Component.translatable(renaming ? "kultivator.formasiui.nodes.save" : "kultivator.formasiui.nodes.rename"), true, () -> {
						if (page.renaming() == id) {
							page.send("node:" + id + ":" + page.renameText().trim());
							page.startRename(-1, "");
						} else {
							page.startRename(id, node.getStringOr("name", ""));
						}
					}, mouseX, mouseY, List.of(Component.translatable("kultivator.formasiui.nodes.rename.tip")));
		}
	}

	private NodePanel() {
	}
}
