package dev.kultivator.client.formasi;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The node map (DESIGN 24.7.2): a round jade map centred on the node we stand on, north up. Territory nodes are dots
 * placed by relative position; outposts sit on the rim with their distance. Each node shows its trip cost and Daya, and a
 * red seal when it is broken, switched off or not loaded; the trip cost and Daya are in its tooltip (text 1.0, 43.0.5). A
 * click on a linked node sends {@code travel:<from>:<to>} and closes the Gulungan on purpose so the channel ring (HUD) and
 * the light column can be seen (43.13 R32, with an action-bar line). The map is the detail view of the Simpul sub: the
 * host's back control and Esc close it. The client cost is the card rule; the server decides (keepers, Space Dao, debts).
 */
final class NodeMap {

	static void draw(FormasiPage page, GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, int fromId) {
		Font font = page.font();
		List<CompoundTag> nodes = new ArrayList<>();
		CompoundTag from = null;
		for (Tag tag : page.b().getListOrEmpty("nodes")) {
			if (tag instanceof CompoundTag node) {
				nodes.add(node);
				if (node.getIntOr("id", -1) == fromId) {
					from = node;
				}
			}
		}
		page.title(g, Component.translatable("kultivator.formasiui.map.title", from == null ? "?" : from.getStringOr("name", "?")), x + 2.0F, y + 1.0F,
				w - 4.0F, 1.0F);
		if (from == null) {
			page.wrap(Component.translatable("kultivator.formasiui.map.lost"), x + 4.0F, y + 16.0F, w - 8.0F, Palette.INK_SOFT);
			return;
		}
		float cx = x + w / 2.0F;
		float cy = y + 16.0F + (h - 30.0F) / 2.0F;
		float r = Math.max(24.0F, Math.min(w / 2.0F - 34.0F, (h - 40.0F) / 2.0F));
		// The jade disc: rim, inner field, compass marks.
		Brush.disc(g, cx, cy, r + 3.0F, Palette.GOLD_DARK);
		Brush.disc(g, cx, cy, r + 1.5F, Palette.JADE_DARK);
		Brush.disc(g, cx, cy, r, Palette.mix(Palette.JADE, Palette.JADE_DARK, 0.35F));
		Brush.ring(g, cx, cy, r * 0.5F - 0.4F, r * 0.5F + 0.4F, Palette.argb(0x60, Palette.JADE_PALE));
		Brush.ring(g, cx, cy, r - 4.0F, r - 3.4F, Palette.argb(0x80, Palette.JADE_PALE));
		Brush.centered(g, font, Component.translatable("kultivator.formasiui.map.north"), cx, cy - r + 2.0F, Palette.GOLD_LIGHT, 1.0F, true);
		boolean fromAncient = ancient(from);
		boolean fromOut = from.getBooleanOr("out", false);
		String dim = from.getStringOr("dim", "");
		int fx = from.getIntOr("x", 0);
		int fz = from.getIntOr("z", 0);
		double maxInside = 8.0D;
		for (CompoundTag node : nodes) {
			if (node != from && !node.getBooleanOr("out", false) && dim.equals(node.getStringOr("dim", ""))) {
				maxInside = Math.max(maxInside, distance(from, node));
			}
		}
		double scale = (r - 10.0D) / maxInside;
		float pulse = 0.6F + 0.4F * (float) Math.sin(Util.getMillis() / 220.0D);
		List<Component> hoverTip = null;
		for (CompoundTag node : nodes) {
			if (!dim.equals(node.getStringOr("dim", ""))) {
				continue;
			}
			boolean self = node == from;
			double dx = node.getIntOr("x", 0) - fx;
			double dz = node.getIntOr("z", 0) - fz;
			double dist = Math.sqrt(dx * dx + dz * dz);
			boolean out = node.getBooleanOr("out", false);
			float px;
			float py;
			if (self) {
				px = cx;
				py = cy;
			} else if (out || dist * scale > r - 6.0D) {
				double angle = Math.atan2(dz, dx);
				px = cx + (float) (Math.cos(angle) * (r - 4.0D));
				py = cy + (float) (Math.sin(angle) * (r - 4.0D));
			} else {
				px = cx + (float) (dx * scale);
				py = cy + (float) (dz * scale);
			}
			boolean ancient = ancient(node);
			int state = node.getIntOr("st", 0);
			boolean broken = state <= 2;
			boolean linked = !self && linked(fromAncient, fromOut, ancient, out);
			boolean reachable = linked && !broken && (fromAncient || node.getBooleanOr("loaded", false));
			int dot = self ? Palette.GOLD_LIGHT : reachable ? (ancient ? 0xFFF0E6FF : 0xFFC8B0F0) : Palette.JADE_DULL;
			float dr = self ? 4.5F : 3.5F;
			boolean over = !self && (mouseX - px) * (mouseX - px) + (mouseY - py) * (mouseY - py) <= 64.0F;
			if (self || over && reachable) {
				Brush.halo(g, px, py, dr, 3.0F, self ? 0xFFE8A0 : 0xE0D0FF, self ? pulse : 0.9F);
			}
			Brush.disc(g, px, py, dr + 1.0F, Palette.INK);
			Brush.disc(g, px, py, dr, dot);
			if (broken || !self && !node.getBooleanOr("loaded", false) && !fromAncient) {
				Brush.disc(g, px + dr, py - dr, 1.8F, Palette.SEAL);
			}
			String name = node.getStringOr("name", "?");
			long cost = self ? 0L : cost(fromAncient, from.getBooleanOr("kept", false), dist);
			long daya = node.getLongOr("daya", 0L) / 100L;
			Component label = self ? Component.translatable("kultivator.formasiui.map.here", name)
					: out ? Component.translatable("kultivator.formasiui.map.outpost", name, Math.round(dist))
					: Component.literal(name);
			Brush.fitCentered(g, font, self || out ? label : Component.literal(name), px, py + dr + 1.5F, 56.0F,
					self ? Palette.GOLD_LIGHT : reachable ? 0xFFF4EBD0 : Palette.argb(0xC0, 0xB0B0B0), 1.0F, 1.0F, true);
			if (over) {
				List<Component> tip = new ArrayList<>();
				tip.add(Component.literal(name));
				tip.add(Component.translatable("kultivator.formasiui.map.tip", Math.round(dist), cost, daya));
				tip.add(Component.translatable("kultivator.formasiui.map.cost", cost, daya));
				tip.add(reachable ? Component.translatable("kultivator.formasiui.map.go")
						: Component.translatable(broken ? "kultivator.formasiui.map.broken" : !linked ? "kultivator.formasiui.map.unlinked"
						: "kultivator.formasiui.map.unloaded").copy().withColor(0xFF7A60));
				hoverTip = tip;
				int toId = node.getIntOr("id", -1);
				page.hit(px - 8.0F, py - 8.0F, px + 8.0F, py + 8.0F, reachable ? () -> {
					page.send("travel:" + fromId + ":" + toId);
					page.closeNodeMap();
					FormasiPage.closeToWorld(Component.translatable("kultivator.gui43.m07.perjalanan", name));
				} : null, tip);
			}
		}
		if (hoverTip == null) {
			Brush.fitCentered(g, font, Component.translatable("kultivator.formasiui.map.hint"), cx, y + h - 10.0F, w - 8.0F, Palette.INK_SOFT, 1.0F, 1.0F,
					false);
		}
	}

	private static boolean ancient(@Nullable CompoundTag node) {
		return node != null && "ancient_transfer".equals(node.getStringOr("spec", ""));
	}

	private static double distance(CompoundTag a, CompoundTag b) {
		double dx = a.getIntOr("x", 0) - b.getIntOr("x", 0);
		double dz = a.getIntOr("z", 0) - b.getIntOr("z", 0);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The network rule of 24.4.10 (mirrors {@code TransportNetwork.linked}). */
	private static boolean linked(boolean fromAncient, boolean fromOut, boolean toAncient, boolean toOut) {
		if (fromAncient || toAncient) {
			return fromAncient && toAncient || !fromOut && !toOut || fromAncient && !toOut || toAncient && !fromOut;
		}
		return !fromOut && !toOut;
	}

	/** The card cost: M3 1 Daya (0 with a keeper), L5 2 Daya per started 512 blocks. */
	private static long cost(boolean fromAncient, boolean kept, double distance) {
		if (!fromAncient) {
			return kept ? 0L : 1L;
		}
		return Math.max(2L, 2L * (long) Math.ceil(distance / 512.0D));
	}

	private NodeMap() {
	}
}
