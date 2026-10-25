package dev.kultivator.client.sekte.hubungan;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.sekte.mandiri.MandiriBoardScreen;
import dev.kultivator.data.Realm;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.hubungan.HubunganPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * Murid › Jaring (DESIGN 43.5; was the Jaring Hubungan screen of DESIGN 36.2.2): the web of ties of the whole sect as a
 * page of the Gulungan Utama. The disciples sit on a ring like the seats of a council, each a disc in the colour of its
 * spiritual root (hollow while away); every known pair is a thread drawn by {@link PelukisIkatan}. Hovering, Tab focus or a
 * click lights a disciple's threads and lists its ties beside the ring; a second click (or Enter, or "Buka murid") opens
 * that disciple on Murid › Murid › hubungan (R18). The data ({@code HubunganPayloads.Jaring}) is asked for only while the
 * page shows, again every 5 s (43.0.12); an unasked payload ({@code /kultivasi perguruan hubungan}) opens this page through
 * {@link HubNav#buka} instead of a screen of its own (R15). At most 16 discs, one quad per thread per frame (36.3.9).
 */
public final class JaringScreen extends Page {

	private static final String K = "kultivator.sekte.hubungan.jaring.";
	private static final String M = "kultivator.gui43.m04.";
	private static final long REFRESH_MILLIS = 5000L;
	private static final long GROW_MILLIS = 450L;
	private static final int ROW = 11;
	private static final int BUTTON_H = 14;

	private record Node(String soul, Component name, boolean here, int realm, int rootColor, int balance) {
	}

	private record Edge(int i, int j, Ikatan kind, int affinity, int meetings) {
	}

	private static @Nullable CompoundTag terbaru;
	private static long terbaruAt;
	private static long versi;
	/** The page while it is on screen (an unasked payload then only refreshes it). */
	private static @Nullable JaringScreen tampil;
	/** When the shown page last asked (0 = answered): its answer never reopens a closed Gulungan. */
	private static long refreshAskedAt;

	private final List<Node> nodes = new ArrayList<>();
	private final List<Edge> edges = new ArrayList<>();
	private long dimuat = -1L;
	private boolean loaded;
	private boolean hall;
	private boolean on;
	private int pinned = -1;
	private long openedAt;
	private long askedAt;
	private float cx;
	private float cy;
	private float radius;
	private int ringX1;
	private int sideX0;
	private float[] nx = new float[0];
	private float[] ny = new float[0];

	public JaringScreen() {
		super();
	}

	/** A Jaring payload arrived: kept for the page; an unasked one opens Murid › Jaring (R15). */
	static void receive(CompoundTag data) {
		boolean refreshReply = Util.getMillis() - refreshAskedAt < 3000L;
		refreshAskedAt = 0L;
		terbaru = data;
		terbaruAt = Util.getMillis();
		versi++;
		if (tampil == null && !refreshReply) {
			HubNav.buka(Tujuan.of(HubNav.MURID, Urutan.ID_JARING));
		}
	}

	/** Forgets the data (disconnect). */
	static void bersih() {
		terbaru = null;
		versi++;
		tampil = null;
	}

	private static void ask() {
		HubunganPayloads.Aksi payload = new HubunganPayloads.Aksi("jaring", "", "", 0);
		if (ClientPlayNetworking.canSend(payload.type())) {
			refreshAskedAt = Util.getMillis();
			ClientPlayNetworking.send(payload);
		}
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable(K + "judul");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.gui43.hal.murid.jaring");
	}

	@Override
	public int glyph() {
		return Glyphs.HUBUNGAN;
	}

	@Override
	public String helpTopic() {
		// the ties between disciples are part (2) of "Sekte yang Hidup"; "ikatan" is the master's bond (Murid › Jalan)
		return "sekte_hidup";
	}

	@Override
	public boolean rinci() {
		return pinned >= 0;
	}

	@Override
	public boolean mundur() {
		if (pinned >= 0) {
			pinned = -1;
			return true;
		}
		return false;
	}

	// ================================================================== life

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		tampil = this;
		long now = Util.getMillis();
		askedAt = now;
		if (terbaru == null || now - terbaruAt > 1000L) {
			ask();
		}
		// the soul -> body map behind "Buka murid" comes from the hall board
		MandiriBoardScreen.minta();
		super.onShow(fokus);
	}

	@Override
	public void onHide() {
		if (tampil == this) {
			tampil = null;
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
	protected void build() {
		int sideW = tata() == Tata.SEMPIT ? Math.max(110, w * 45 / 100) : Math.round(w * 0.36F);
		ringX1 = x + w - sideW - 6;
		sideX0 = ringX1 + 6;
		cx = (x + ringX1) / 2.0F;
		cy = y + h / 2.0F;
		radius = Math.max(20.0F, Math.min((ringX1 - x) / 2.0F - 14.0F, h / 2.0F - 14.0F));
		layoutNodes();
	}

	private void muat(@Nullable CompoundTag data) {
		dimuat = versi;
		if (data == null) {
			loaded = false;
			nodes.clear();
			edges.clear();
			pinned = -1;
			return;
		}
		if (!loaded) {
			openedAt = Util.getMillis();
		}
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

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		if (dimuat != versi) {
			muat(terbaru);
		}
		Component empty = !loaded ? Component.translatable(K + "memuat") : !on ? Component.translatable("kultivator.sekte.hubungan.tab.mati")
				: !hall ? Component.translatable("kultivator.sekte.hubungan.tab.tanpa_aula")
				: nodes.size() < 2 ? Component.translatable(K + "kosong") : null;
		if (empty != null) {
			KeadaanKosong.gambar(l, loaded ? KeadaanKosong.Gambar.GULUNGAN : KeadaanKosong.Gambar.LENTERA, empty, null, null, x, y, x + w, y + h);
			return;
		}
		GuiGraphicsExtractor g = l.g;
		float grow = Mth.clamp((l.kini - openedAt) / (float) GROW_MILLIS, 0.0F, 1.0F);
		grow = grow * grow * (3.0F - 2.0F * grow);
		int hovered = -1;
		int focused = -1;
		for (int i = 0; i < nodes.size() && i < nx.length; i++) {
			if (l.di(Math.round(nx[i] - 9), Math.round(ny[i] - 9), Math.round(nx[i] + 9), Math.round(ny[i] + 9))) {
				hovered = i;
			}
			if (l.difokus("simpul" + i)) {
				focused = i;
			}
		}
		int focus = hovered >= 0 ? hovered : focused >= 0 ? focused : pinned;
		// the faint ring the discs sit on, the sect's seal in the middle
		Brush.ring(g, cx, cy, radius - 0.6F, radius + 0.6F, Palette.argb(0x50, Palette.GOLD_DARK));
		Brush.ring(g, cx, cy, radius * 0.18F, radius * 0.18F + 1.0F, Palette.argb(0x40, Palette.GOLD_DARK));
		Component seal = Component.literal("宗");
		Brush.text(g, font, seal, cx - font.width(seal) / 2.0F, cy - 4.0F, Palette.argb(0x60, Palette.SEAL_DARK), 1.0F, false);
		float pulse = 0.5F + 0.5F * (float) Math.sin(l.kini / 420.0D);
		for (Edge edge : edges) {
			boolean lit = focus < 0 || edge.i() == focus || edge.j() == focus;
			PelukisIkatan.benang(g, font, Mth.lerp(grow, cx, nx[edge.i()]), Mth.lerp(grow, cy, ny[edge.i()]), Mth.lerp(grow, cx, nx[edge.j()]),
					Mth.lerp(grow, cy, ny[edge.j()]), edge.kind(), edge.affinity(), lit, pulse, focus >= 0);
		}
		int n = nodes.size();
		float arc = (float) (Math.PI * 2.0D * radius / Math.max(1, n));
		boolean allNames = arc >= 34.0F;
		for (int i = 0; i < n && i < nx.length; i++) {
			drawNode(l, i, focus, grow, allNames, arc);
		}
		drawSide(l, focus);
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
			tip.add(Component.translatable(M + "jaring.klik").withStyle(ChatFormatting.DARK_GRAY));
			l.tip(tip);
		}
	}

	private void drawNode(Lukis l, int i, int focus, float grow, boolean allNames, float arc) {
		Node node = nodes.get(i);
		float px = Mth.lerp(grow, cx, nx[i]);
		float py = Mth.lerp(grow, cy, ny[i]);
		boolean lit = focus < 0 || focus == i || connected(focus, i);
		float r = (i == focus ? 7.5F : 6.0F) * (0.4F + 0.6F * grow);
		PelukisIkatan.simpul(l.g, px, py, r, node.rootColor(), node.here(), lit, i == focus);
		if (i == pinned) {
			Brush.ring(l.g, px, py, r + 2.5F, r + 3.5F, Palette.GOLD);
		}
		int index = i;
		l.zona("simpul" + i, Math.round(nx[i] - 9), Math.round(ny[i] - 9), Math.round(nx[i] + 9), Math.round(ny[i] + 9), () -> klikSimpul(index),
				true, null, true);
		l.fokusCincin("simpul" + i, Math.round(px - r - 1), Math.round(py - r - 1), Math.round(px + r + 1), Math.round(py + r + 1));
		if (!allNames && !(focus >= 0 && lit)) {
			return;
		}
		// the name outside the ring, kept inside the ring's half of the page
		double a = Math.atan2(py - cy, px - cx);
		float lx = px + (float) Math.cos(a) * (r + 5.0F);
		float ly = py + (float) Math.sin(a) * (r + 5.0F) - 4.0F;
		float tw = Math.min(font.width(node.name()), allNames ? Mth.clamp(arc - 4.0F, 28.0F, 64.0F) : 56.0F);
		float tx = Math.cos(a) < -0.3D ? lx - tw : Math.cos(a) > 0.3D ? lx : lx - tw / 2.0F;
		tx = Mth.clamp(tx, x + 1.0F, ringX1 - tw);
		ly = Mth.clamp(ly, y, y + h - 9.0F);
		l.teks(node.name(), tx, ly, tw, lit ? Palette.INK : Palette.INK_FAINT);
	}

	private boolean connected(int a, int b) {
		for (Edge edge : edges) {
			if (edge.kind() != Ikatan.NONE && (edge.i() == a && edge.j() == b || edge.i() == b && edge.j() == a)) {
				return true;
			}
		}
		return false;
	}

	/** Beside the ring: the focused disciple and its ties (each a row that moves the focus there), or the legend and the hint. */
	private void drawSide(Lukis l, int focus) {
		int sx = sideX0;
		int sw = x + w - sideX0;
		int sy = y + 2;
		int bottom = y + h;
		Brush.rect(l.g, sx - 4, y + 2, sx - 3, bottom - 2, Palette.argb(0x60, Palette.GOLD_DARK));
		if (focus < 0) {
			l.teks(Brush.bold(Component.translatable(K + "legenda")), sx, sy, sw, Palette.SEAL_DARK);
			sy += 12;
			sy += PelukisIkatan.legenda(l, sx, sy, sw, bottom) + 4;
			if (bottom - sy >= 18) {
				l.bungkus(Component.translatable(M + "jaring.petunjuk"), sx, sy, sw, Palette.INK_FAINT, bottom - sy);
			}
			return;
		}
		Node node = nodes.get(focus);
		l.teks(Brush.bold(node.name()), sx, sy, sw, Palette.INK);
		sy += 10;
		if (node.realm() >= 0 && node.realm() < Realm.values().length) {
			Realm realm = Realm.values()[node.realm()];
			l.teks(realm.displayName(), sx, sy, sw, Palette.onPaper(realm.color()));
			sy += 10;
		}
		Brush.rect(l.g, sx, sy + 1, sx + sw, sy + 1.6F, Palette.argb(0x90, Palette.GOLD));
		sy += 4;
		int buttonY = bottom - BUTTON_H - 1;
		List<Edge> mine = new ArrayList<>();
		for (Edge edge : edges) {
			if ((edge.i() == focus || edge.j() == focus) && edge.kind() != Ikatan.NONE) {
				mine.add(edge);
			}
		}
		mine.sort((p, q) -> Integer.compare(Math.abs(q.affinity()), Math.abs(p.affinity())));
		if (mine.isEmpty()) {
			l.bungkus(Component.translatable(K + "sendiri"), sx, sy, sw, Palette.INK_SOFT, buttonY - sy - 2);
		}
		for (int k = 0; k < mine.size(); k++) {
			if (sy + ROW > buttonY - 2) {
				break;
			}
			Edge edge = mine.get(k);
			int otherIndex = edge.i() == focus ? edge.j() : edge.i();
			Node other = nodes.get(otherIndex);
			int rgb = PelukisIkatan.warna(edge.kind());
			boolean over = l.di(sx, sy, sx + sw, sy + ROW);
			if (over || l.difokus("ikatan" + k)) {
				l.g.fill(sx - 1, sy - 1, sx + sw, sy + ROW - 1, Palette.argb(0x40, Palette.PAPER_DEEP));
			}
			Brush.disc(l.g, sx + 3.5F, sy + 3.5F, 3.0F, Palette.opaque(rgb));
			Component kind = Component.translatable(edge.kind().key()).append(" ").append(PelukisIkatan.nilai(edge.affinity()));
			int kindW = Math.min(font.width(kind), Math.round((sw - 10) * 0.48F));
			l.teks(other.name(), sx + 9, sy, sw - 12 - kindW, Palette.INK);
			l.teks(kind, sx + sw - kindW, sy, kindW, Palette.onPaper(rgb));
			l.zona("ikatan" + k, sx, sy - 1, sx + sw, sy + ROW - 1, () -> pinned = otherIndex, true, null, true);
			l.fokusCincin("ikatan" + k, sx, sy - 1, sx + sw, sy + ROW - 1);
			sy += ROW;
		}
		Component open = Component.translatable(M + "jaring.buka");
		TalismanButton.gambar(l, "buka", sx, buttonY, sw, BUTTON_H, open, true, null, () -> buka(focus));
	}

	// ================================================================== input

	private void klikSimpul(int i) {
		if (pinned == i) {
			buka(i);
		} else {
			pinned = i;
		}
	}

	/** Opens the disciple on Murid › Murid › hubungan (its diary when the body is out of reach). */
	private void buka(int i) {
		if (i >= 0 && i < nodes.size()) {
			MandiriBoardScreen.bukaMurid(nodes.get(i).soul(), "hubungan");
		}
	}

	@Override
	protected boolean clickedElsewhere(double mouseX, double mouseY) {
		if (mouseX >= x && mouseX < ringX1 && mouseY >= y && mouseY < y + h && pinned >= 0) {
			pinned = -1;
			return true;
		}
		return false;
	}
}
