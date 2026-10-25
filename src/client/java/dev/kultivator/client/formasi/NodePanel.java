package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.spec.FormationSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Simpul sub of Sekte › Formasi (DESIGN 24.7.1): the transport nodes (M3, L5) with name, grade, Daya, distance and the loaded flag;
 * <i>Ganti Nama</i> for the owner (the kit's {@code InkField} in place, Enter or <i>Simpan</i> saves, Esc cancels) and <i>Peta</i>, which opens the node map from that node.
 */
final class NodePanel {

	static final int ROW_H = 24;

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
		CompoundTag b = page.b();
		page.title(g, Component.translatable("kultivator.formasiui.nodes.title"), x + 2.0F, y + 1.0F, w - 6.0F, 1.0F);
		float top = y + 15.0F;
		float view = h - 15.0F;
		List<CompoundTag> nodes = new ArrayList<>();
		for (Tag tag : b.getListOrEmpty("nodes")) {
			if (tag instanceof CompoundTag node) {
				nodes.add(node);
			}
		}
		if (!b.getBooleanOr("transport", true)) {
			float used = page.wrap(Component.translatable("kultivator.formasiui.nodes.disabled"), x + 4.0F, top + 1.0F, w - 10.0F, Palette.DANGER) + 3.0F;
			top += used;
			view -= used;
		}
		Component hint = Component.translatable("kultivator.formasiui.nodes.hint");
		float hintH = page.font().split(hint, Math.max(10, w - 10)).size() * 9.0F + 6.0F;
		float content = Math.max(1, nodes.size()) * ROW_H + hintH;
		float offset = page.clampScroll(3, content, view);
		page.scrollArea(x, Math.round(top), x + w, Math.round(top + view));
		page.klip(x, Math.round(top), x + w, Math.round(top + view));
		float cursor = top - offset;
		if (nodes.isEmpty()) {
			page.wrap(Component.translatable("kultivator.formasiui.nodes.empty"), x + 4.0F, cursor + 2.0F, w - 10.0F, Palette.INK_SOFT);
			cursor += ROW_H;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		for (CompoundTag node : nodes) {
			if (cursor + ROW_H >= top && cursor <= top + view) {
				row(page, g, node, player, x, cursor, w, mouseX, mouseY);
			}
			cursor += ROW_H;
		}
		page.wrap(hint, x + 4.0F, cursor + 4.0F, w - 10.0F, Palette.INK_FAINT);
		page.lepasKlip();
		FormasiPage.bead(g, x + w, Math.round(top), Math.round(top + view), offset, Math.max(0.0F, content - view));
	}

	private static void row(FormasiPage page, GuiGraphicsExtractor g, CompoundTag node, LocalPlayer player, int x, float ry, int w, int mouseX,
			int mouseY) {
		Font font = page.font();
		int id = node.getIntOr("id", -1);
		FormationSpec spec = FormationRegistry.byId(node.getStringOr("spec", ""));
		int state = node.getIntOr("st", 0);
		g.fill(x, Math.round(ry), x + w - 3, Math.round(ry + ROW_H - 1), Palette.argb(0x28, Palette.PAPER_DEEP));
		boolean ancient = spec != null && "ancient_transfer".equals(spec.id());
		int color = ancient ? 0xFFE8D8FF : 0xFFB89CE8;
		Brush.disc(g, x + 8.0F, ry + ROW_H / 2.0F - 0.5F, 5.5F, Palette.mix(color, Palette.INK, 0.25F));
		Brush.disc(g, x + 8.0F, ry + ROW_H / 2.0F - 0.5F, 3.0F, color);
		boolean bad = state == 1 || state == 2 || state == 0;
		if (bad) {
			Brush.disc(g, x + 12.0F, ry + 5.0F, 2.2F, Palette.SEAL);
		}
		Component mapLabel = Component.translatable("kultivator.formasiui.nodes.map");
		boolean renaming = page.renaming() == id;
		Component renameLabel = Component.translatable(renaming ? "kultivator.formasiui.nodes.save" : "kultivator.formasiui.nodes.rename");
		int mapW = TalismanButton.lebar(font, mapLabel, 36, 90);
		int renameW = page.canEdit() ? TalismanButton.lebar(font, renameLabel, 36, 90) : 0;
		float buttons = mapW + (page.canEdit() ? renameW + 3 : 0);
		float nameX = x + 17.0F;
		float textW = w - (nameX - x) - buttons - 8.0F;
		if (renaming) {
			page.drawRenameField(Math.round(nameX - 1.0F), Math.round(ry + 1.0F), Math.round(nameX + textW), Math.round(ry + 13.0F));
		} else {
			page.line(Brush.bold(Component.literal(node.getStringOr("name", "?"))), nameX, ry + 2.0F, textW, Palette.INK);
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
		page.line(line, nameX, ry + 14.0F, textW, Palette.INK_SOFT);
		List<Component> tip = new ArrayList<>();
		tip.add(Component.literal(node.getStringOr("name", "?")));
		if (spec != null) {
			tip.add(spec.name());
		}
		tip.add(FormasiPage.stateName(state).copy().withColor(FormasiPage.stateColor(state) & 0xFFFFFF));
		tip.add(Component.translatable("kultivator.formasiui.cores.at", node.getIntOr("x", 0), node.getIntOr("y", 0), node.getIntOr("z", 0)));
		if (!renaming) {
			page.hit(x, ry, nameX + textW, ry + ROW_H - 1, null, tip);
		}
		float bx = x + w - buttons - 4.0F;
		float by = ry + (ROW_H - FormasiPage.BUTTON_H) / 2.0F;
		page.talisman(g, bx, by, mapW, FormasiPage.BUTTON_H, mapLabel, true, () -> page.openNodeMap(id), mouseX, mouseY,
				List.of(Component.translatable("kultivator.formasiui.nodes.map.tip")));
		if (page.canEdit()) {
			page.talisman(g, bx + mapW + 3.0F, by, renameW, FormasiPage.BUTTON_H, renameLabel, true, () -> {
				if (page.renaming() == id) {
					page.send("node:" + id + ":" + page.renameField().text().trim());
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
