package dev.kultivator.client.sekte.hubungan;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.Realm;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.hubungan.HubunganPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Jaring Hubungan, the web of ties of the whole sect (DESIGN 36.2.2; hub SEKTE card "Jaring Hubungan",
 * {@code /kultivasi perguruan hubungan}). The disciples sit on a ring like the seats of a council, each a jade disc in the
 * colour of its spiritual root (hollow while away), its name outside; every known pair is a thread between them: jade for
 * friends, gold double cords for sworn kin, rose for lovers, a glowing rose double cord for Dao companions, amber for
 * rivals and red for grudges, thicker the stronger the feeling, acquaintances only a faint grey hair. Hovering or
 * choosing a disciple lights its threads and lists its ties beside the ring. At most 16 discs, one quad per thread per
 * frame (36.3.9). The data arrives as {@code HubunganPayloads.Jaring} and is asked for again every 5 s while open.
 */
public class JaringScreen extends Screen {

	private static final String K = "kultivator.sekte.hubungan.jaring.";
	private static final long REFRESH_MILLIS = 5000L;

	private record Node(String soul, Component name, boolean here, int realm, int rootColor, int balance) {
	}

	private record Edge(int i, int j, Ikatan kind, int affinity, int meetings) {
	}

	private static @Nullable JaringScreen open;

	private final List<Node> nodes = new ArrayList<>();
	private final List<Edge> edges = new ArrayList<>();
	private boolean loaded;
	private boolean hall;
	private boolean on;
	private int pinned = -1;
	private int hovered = -1;
	private long openedAt;
	private long askedAt;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private float cx;
	private float cy;
	private float radius;
	private float[] nx = new float[0];
	private float[] ny = new float[0];

	public JaringScreen() {
		super(Component.translatable(K + "judul"));
	}

	/** A Jaring payload arrived: fill the open screen, or open it (the player asked through the command or the card). */
	static void receive(CompoundTag data) {
		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		if (open != null && minecraft.gui.screen() == open) {
			open.load(data);
			return;
		}
		JaringScreen screen = new JaringScreen();
		screen.load(data);
		minecraft.gui.setScreen(screen);
	}

	static void ask() {
		ClientPlayNetworking.send(new HubunganPayloads.Aksi("jaring", "", "", 0));
	}

	private void load(CompoundTag data) {
		loaded = true;
		hall = data.getBooleanOr("hall", false);
		on = data.getBooleanOr("on", false);
		String keep = pinned >= 0 && pinned < nodes.size() ? nodes.get(pinned).soul() : null;
		nodes.clear();
		edges.clear();
		ListTag n = data.getListOrEmpty("nodes");
		for (int i = 0; i < n.size(); i++) {
			CompoundTag t = n.getCompoundOrEmpty(i);
			nodes.add(new Node(t.getStringOr("s", ""), t.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?")),
					t.getBooleanOr("here", false), t.getIntOr("r", -1), t.getIntOr("c", 0xA0C8B8), t.getIntOr("bal", 0)));
		}
		ListTag e = data.getListOrEmpty("edges");
		for (int i = 0; i < e.size(); i++) {
			CompoundTag t = e.getCompoundOrEmpty(i);
			int a = t.getIntOr("i", -1);
			int b = t.getIntOr("j", -1);
			if (a >= 0 && b >= 0 && a < nodes.size() && b < nodes.size() && a != b) {
				edges.add(new Edge(a, b, Ikatan.byId(t.getStringOr("k", "none")), t.getIntOr("a", 0), t.getIntOr("m", 0)));
			}
		}
		pinned = -1;
		if (keep != null) {
			for (int i = 0; i < nodes.size(); i++) {
				if (nodes.get(i).soul().equals(keep)) {
					pinned = i;
				}
			}
		}
		layoutNodes();
	}

	@Override
	protected void init() {
		open = this;
		if (openedAt == 0L) {
			openedAt = Util.getMillis();
			UiSound.open();
		}
		int w = Math.min(420, width - 16);
		int h = Math.min(270, height - 16);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
		float ringW = w * 0.62F;
		cx = x0 + ringW / 2.0F + 8.0F;
		cy = y0 + 22.0F + (h - 34.0F) / 2.0F;
		radius = Math.max(30.0F, Math.min(ringW / 2.0F - 30.0F, (h - 34.0F) / 2.0F - 16.0F));
		layoutNodes();
	}

	private void layoutNodes() {
		int n = nodes.size();
		nx = new float[n];
		ny = new float[n];
		for (int i = 0; i < n; i++) {
			double a = -Math.PI / 2.0D + Math.PI * 2.0D * i / Math.max(1, n);
			nx[i] = cx + (float) Math.cos(a) * radius;
			ny[i] = cy + (float) Math.sin(a) * radius;
		}
	}

	@Override
	public void tick() {
		long now = Util.getMillis();
		if (now - askedAt > REFRESH_MILLIS) {
			askedAt = now;
			ask();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0xB0081018, 0xD0040A10);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		float grow = Mth.clamp((now - openedAt) / 450.0F, 0.0F, 1.0F);
		grow = grow * grow * (3.0F - 2.0F * grow);
		// the scroll: paper, rods, a gold frame
		g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x70000000);
		g.fillGradient(x0, y0, x1, y1, Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		Brush.frame(g, x0, y0, x1, y1, Palette.GOLD_DARK);
		Brush.frame(g, x0 + 3, y0 + 3, x1 - 3, y1 - 3, Palette.argb(0x90, Palette.GOLD));
		g.fill(x0 - 4, y0 - 3, x1 + 4, y0 + 1, Palette.ROD);
		g.fill(x0 - 4, y1 - 1, x1 + 4, y1 + 3, Palette.ROD);
		Brush.fit(g, font, Brush.bold(title), x0 + 10.0F, y0 + 7.0F, (x1 - x0) * 0.6F, Palette.SEAL_DARK, 1.0F, 0.6F, false);
		Brush.rect(g, x0 + 10.0F, y0 + 18.0F, x1 - 10.0F, y0 + 18.6F, Palette.argb(0xB0, Palette.GOLD));

		Component empty = !loaded ? Component.translatable(K + "memuat") : !on ? Component.translatable("kultivator.sekte.hubungan.tab.mati")
				: !hall ? Component.translatable("kultivator.sekte.hubungan.tab.tanpa_aula")
				: nodes.size() < 2 ? Component.translatable(K + "kosong") : null;
		drawLegend(g);
		if (empty != null) {
			Brush.wrap(g, font, empty, x0 + 14.0F, cy - 6.0F, (x1 - x0) * 0.55F, Palette.INK_SOFT, 0.85F, false);
			return;
		}
		hovered = nodeAt(mouseX, mouseY);
		int focus = hovered >= 0 ? hovered : pinned;
		// the faint ring the discs sit on
		Brush.ring(g, cx, cy, radius - 0.6F, radius + 0.6F, Palette.argb(0x50, Palette.GOLD_DARK));
		Brush.ring(g, cx, cy, radius * 0.18F, radius * 0.18F + 1.0F, Palette.argb(0x40, Palette.GOLD_DARK));
		Brush.fitCentered(g, font, Component.literal("宗"), cx, cy - 5.0F, 20.0F, Palette.argb(0x60, Palette.SEAL_DARK), 1.2F, 0.6F, false);
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 420.0D);
		for (Edge edge : edges) {
			drawEdge(g, edge, focus, grow, pulse);
		}
		for (int i = 0; i < nodes.size(); i++) {
			drawNode(g, i, focus, grow);
		}
		if (focus >= 0) {
			drawSide(g, focus);
		} else {
			Brush.wrap(g, font, Component.translatable(K + "petunjuk"), x0 + (x1 - x0) * 0.66F, y0 + 108.0F, (x1 - x0) * 0.3F, Palette.INK_FAINT,
					0.6F, false);
		}
		if (hovered >= 0) {
			Node node = nodes.get(hovered);
			List<Component> tip = new ArrayList<>();
			tip.add(node.name().copy().withStyle(ChatFormatting.GOLD));
			if (node.realm() >= 0 && node.realm() < Realm.values().length) {
				Realm realm = Realm.values()[node.realm()];
				tip.add(realm.displayName().copy().withColor(realm.color()));
			}
			if (!node.here()) {
				tip.add(Component.translatable(K + "jauh").withStyle(ChatFormatting.GRAY));
			}
			tip.add(Component.translatable("kultivator.sekte.hubungan.tab.suasana", (node.balance() > 0 ? "+" : "") + node.balance())
					.withStyle(ChatFormatting.GRAY));
			g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
		}
	}

	private void drawLegend(GuiGraphicsExtractor g) {
		float lx = x0 + (x1 - x0) * 0.66F;
		float ly = y0 + 26.0F;
		Ikatan[] kinds = {Ikatan.SAHABAT, Ikatan.SAUDARA_ANGKAT, Ikatan.KEKASIH, Ikatan.PASANGAN_DAO, Ikatan.SAINGAN, Ikatan.DENDAM, Ikatan.KENALAN};
		if (hovered >= 0 || pinned >= 0) {
			return;
		}
		Brush.fit(g, font, Brush.bold(Component.translatable(K + "legenda")), lx, ly, (x1 - lx) - 10.0F, Palette.SEAL_DARK, 0.7F, 0.45F, false);
		ly += 10.0F;
		for (Ikatan kind : kinds) {
			int rgb = HubunganColors.of(kind);
			Brush.line(g, lx, ly + 3.0F, lx + 14.0F, ly + 3.0F, kind == Ikatan.KENALAN ? 0.8F : 1.8F, Palette.opaque(rgb));
			if (kind == Ikatan.SAUDARA_ANGKAT || kind == Ikatan.PASANGAN_DAO) {
				Brush.line(g, lx, ly + 5.5F, lx + 14.0F, ly + 5.5F, 1.0F, Palette.opaque(rgb));
			}
			Brush.fit(g, font, Component.translatable(kind.key()), lx + 18.0F, ly, (x1 - lx) - 28.0F, Palette.onPaper(rgb), 0.62F, 0.4F, false);
			ly += 9.0F;
		}
	}

	private void drawEdge(GuiGraphicsExtractor g, Edge edge, int focus, float grow, float pulse) {
		if (edge.kind() == Ikatan.NONE) {
			return;
		}
		float ax = Mth.lerp(grow, cx, nx[edge.i()]);
		float ay = Mth.lerp(grow, cy, ny[edge.i()]);
		float bx = Mth.lerp(grow, cx, nx[edge.j()]);
		float by = Mth.lerp(grow, cy, ny[edge.j()]);
		boolean lit = focus < 0 || edge.i() == focus || edge.j() == focus;
		int rgb = HubunganColors.of(edge.kind());
		float strength = Math.abs(edge.affinity()) / 100.0F;
		float thick = edge.kind() == Ikatan.KENALAN ? 0.6F : 0.9F + 1.6F * strength;
		int alpha = lit ? (edge.kind() == Ikatan.KENALAN ? 0x70 : 0xE0) : 0x22;
		if (edge.kind() == Ikatan.PASANGAN_DAO && lit) {
			Brush.line(g, ax, ay, bx, by, thick + 3.0F, Palette.argb(Math.round(0x30 + 0x30 * pulse), rgb));
		}
		if (edge.kind() == Ikatan.SAUDARA_ANGKAT || edge.kind() == Ikatan.PASANGAN_DAO) {
			// a double cord
			float dx = by - ay;
			float dy = ax - bx;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			if (len > 0.01F) {
				dx = dx / len * 1.3F;
				dy = dy / len * 1.3F;
			}
			Brush.line(g, ax + dx, ay + dy, bx + dx, by + dy, thick * 0.7F, Palette.argb(alpha, rgb));
			Brush.line(g, ax - dx, ay - dy, bx - dx, by - dy, thick * 0.7F, Palette.argb(alpha, rgb));
		} else {
			Brush.line(g, ax, ay, bx, by, thick, Palette.argb(alpha, rgb));
		}
		if (lit && focus >= 0) {
			// the affinity written on the middle of the thread
			float mx = (ax + bx) / 2.0F;
			float my = (ay + by) / 2.0F;
			Component value = Component.literal((edge.affinity() > 0 ? "+" : "") + edge.affinity());
			Brush.disc(g, mx, my, 5.5F, Palette.argb(0xD0, Palette.PAPER_TOP));
			Brush.fitCentered(g, font, value, mx, my - 2.5F, 12.0F, Palette.onPaper(rgb), 0.55F, 0.4F, false);
		}
	}

	private void drawNode(GuiGraphicsExtractor g, int i, int focus, float grow) {
		Node node = nodes.get(i);
		float x = Mth.lerp(grow, cx, nx[i]);
		float y = Mth.lerp(grow, cy, ny[i]);
		boolean lit = focus < 0 || focus == i || connected(focus, i);
		float r = (i == focus ? 7.5F : 6.0F) * (0.4F + 0.6F * grow);
		int root = Palette.opaque(node.rootColor());
		if (i == focus) {
			Brush.halo(g, x, y, r + 1.0F, 4.0F, node.rootColor(), 0.5F);
		}
		Brush.disc(g, x, y, r + 1.2F, lit ? Palette.INK : Palette.argb(0x60, Palette.INK));
		if (node.here()) {
			Brush.disc(g, x, y, r, lit ? root : Palette.argb(0x70, root));
			Brush.disc(g, x - r * 0.3F, y - r * 0.3F, r * 0.3F, 0x80FFFFFF);
		} else {
			Brush.disc(g, x, y, r, Palette.argb(0xC0, Palette.PAPER_TOP));
			Brush.ring(g, x, y, r - 1.6F, r, lit ? root : Palette.argb(0x70, root));
		}
		// the name outside the ring
		double a = Math.atan2(y - cy, x - cx);
		float lx = x + (float) Math.cos(a) * (r + 5.0F);
		float ly = y + (float) Math.sin(a) * (r + 5.0F) - 3.0F;
		Component name = node.name();
		float tw = Math.min(70.0F, font.width(name) * 0.62F);
		float tx = Math.cos(a) < -0.3D ? lx - tw : Math.cos(a) > 0.3D ? lx : lx - tw / 2.0F;
		Brush.fit(g, font, name, tx, ly, tw, lit ? Palette.INK : Palette.INK_FAINT, 0.62F, 0.4F, false);
	}

	private boolean connected(int a, int b) {
		for (Edge edge : edges) {
			if (edge.kind() != Ikatan.NONE && (edge.i() == a && edge.j() == b || edge.i() == b && edge.j() == a)) {
				return true;
			}
		}
		return false;
	}

	/** The ties of the focused disciple, beside the ring. */
	private void drawSide(GuiGraphicsExtractor g, int focus) {
		Node node = nodes.get(focus);
		float lx = x0 + (x1 - x0) * 0.66F;
		float lw = x1 - lx - 10.0F;
		float ly = y0 + 26.0F;
		Brush.fit(g, font, Brush.bold(node.name()), lx, ly, lw, Palette.INK, 0.85F, 0.5F, false);
		ly += 11.0F;
		if (node.realm() >= 0 && node.realm() < Realm.values().length) {
			Realm realm = Realm.values()[node.realm()];
			Brush.fit(g, font, realm.displayName(), lx, ly, lw, Palette.onPaper(realm.color()), 0.62F, 0.4F, false);
			ly += 9.0F;
		}
		Brush.rect(g, lx, ly, lx + lw, ly + 0.6F, Palette.argb(0x90, Palette.GOLD));
		ly += 4.0F;
		List<Edge> mine = new ArrayList<>();
		for (Edge edge : edges) {
			if ((edge.i() == focus || edge.j() == focus) && edge.kind() != Ikatan.NONE) {
				mine.add(edge);
			}
		}
		mine.sort((p, q) -> Integer.compare(Math.abs(q.affinity()), Math.abs(p.affinity())));
		if (mine.isEmpty()) {
			Brush.wrap(g, font, Component.translatable(K + "sendiri"), lx, ly, lw, Palette.INK_SOFT, 0.6F, false);
			return;
		}
		for (Edge edge : mine) {
			if (ly > y1 - 14.0F) {
				break;
			}
			Node other = nodes.get(edge.i() == focus ? edge.j() : edge.i());
			int rgb = HubunganColors.of(edge.kind());
			Brush.disc(g, lx + 4.0F, ly + 3.5F, 3.5F, Palette.opaque(rgb));
			Brush.fit(g, font, other.name(), lx + 10.0F, ly, lw * 0.55F, Palette.INK, 0.6F, 0.4F, false);
			Component kind = Component.translatable(edge.kind().key()).append(" " + (edge.affinity() > 0 ? "+" : "") + edge.affinity());
			Brush.fit(g, font, kind, lx + 10.0F + lw * 0.55F, ly, lw * 0.45F - 10.0F, Palette.onPaper(rgb), 0.55F, 0.38F, false);
			ly += 9.0F;
		}
	}

	private int nodeAt(double mouseX, double mouseY) {
		for (int i = 0; i < nodes.size() && i < nx.length; i++) {
			double dx = mouseX - nx[i];
			double dy = mouseY - ny[i];
			if (dx * dx + dy * dy <= 81.0D) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int at = nodeAt(event.x(), event.y());
			if (at >= 0) {
				pinned = pinned == at ? -1 : at;
				UiSound.press();
				return true;
			}
			if (event.x() >= x0 && event.x() < x1 && event.y() >= y0 && event.y() < y1) {
				pinned = -1;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		if (open == this) {
			open = null;
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
