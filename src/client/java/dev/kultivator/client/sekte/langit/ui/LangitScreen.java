package dev.kultivator.client.sekte.langit.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.sekte.langit.LangitClient;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.langit.main.LangitUiPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "Sekte Langit", the sky-sect screen (DESIGN 37.7.9, module 37G): a scroll over a dusk sky with drifting cloud banks.
 * Left, a living diorama of the hall's island drawn from the synced numbers (the inverted cone, the terraces, the
 * satellites and jade bridges of its template, the pagoda that grows with the tier, the mist falls from tier 4, the net
 * pillars from tier 5, the crystals and the Gate of the Nine Heavens at tier 9, the tether pillar while tethered, the
 * cloud sea below) with the nine tier seals and the island's numbers. Right, three tabs: <b>Peristiwa</b> (the sky's state,
 * the days until the next cloud pirates, envoy, guests and lantern festival, the Heaven Court order with its progress
 * and its "lay the stones" seal), <b>Kronik</b> (the hall's sky chronicle) and <b>Tetangga Langit</b> (the NPC sky sects
 * with their stance). Footer seals: Feri, Gerbang, Undang (a name box), Rancang (the build tablet), Pemandangan (37V's
 * sky view settings) and the "?" seal. All numbers come from the server ({@code LangitUi}); refreshed every 3 s. Fits a
 * 320 × 190 GUI (GUI scale 4 on 1366 × 768).
 */
public class LangitScreen extends Screen {

	private static final String K = "kultivator.sekte_langit.layar.";
	private static final int TAB_EVENTS = 0;
	private static final int TAB_CHRONICLE = 1;
	private static final int TAB_NEIGHBOURS = 2;
	private static final int SKY_TOP = 0xFF101C36;
	private static final int SKY_BOTTOM = 0xFF5B4A78;
	private static final int DUSK = 0xFFE59A6A;

	private static @Nullable LangitScreen open;

	private @Nullable CompoundTag data;
	private final long openedAt = Util.getMillis();
	private long askedAt;
	private int tab = TAB_EVENTS;
	private float scroll;
	private float contentH;
	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private int split;
	private int bodyTop;
	private int bodyBottom;
	private @Nullable EditBox inviteBox;
	private final List<Seal> seals = new ArrayList<>();
	private final KeyHints.SealCorner help = new KeyHints.SealCorner("sekte_langit");
	/** The "lay the stones" seal inside the events tab (screen coordinates, -1 when not drawn). */
	private float depositX0 = -1.0F;
	private float depositX1;
	private float depositY0;
	private float depositY1;

	/** One footer seal. */
	private static final class Seal {
		final Component label;
		final Component tip;
		final int color;
		final Runnable action;
		boolean enabled = true;
		int ax;
		int ay;
		int bx;
		int by;

		Seal(Component label, Component tip, int color, Runnable action) {
			this.label = label;
			this.tip = tip;
			this.color = color;
			this.action = action;
		}

		boolean over(double mx, double my) {
			return mx >= ax && mx <= bx && my >= ay && my <= by;
		}
	}

	public LangitScreen() {
		super(Component.translatable(K + "judul"));
	}

	static void ask(int action, String arg) {
		ClientPlayNetworking.send(new LangitUiPayloads.Minta(action, arg));
	}

	static void onData(CompoundTag tag) {
		LangitScreen screen = open;
		if (screen != null) {
			screen.data = tag.copy();
			screen.rebuildSeals();
		}
	}

	@Override
	protected void init() {
		open = this;
		int w = Math.min(460, width - 12);
		int h = Math.min(282, height - 8);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
		split = x0 + Math.round(w * 0.43F);
		bodyTop = y0 + 36;
		bodyBottom = y1 - 26;
		help.place(x1 - 10.0F, y0 + 11.0F);
		String draft = inviteBox != null ? inviteBox.getValue() : "";
		boolean typing = inviteBox != null && inviteBox.isFocused();
		inviteBox = new EditBox(font, split + 8, y1 - 40, Math.max(60, Math.min(120, x1 - split - 70)), 12, Component.translatable(K + "undang_nama"));
		inviteBox.setMaxLength(16);
		inviteBox.setHint(Component.translatable(K + "undang_nama").withStyle(ChatFormatting.DARK_GRAY));
		inviteBox.setValue(draft);
		inviteBox.visible = typing;
		addRenderableWidget(inviteBox);
		if (typing) {
			setFocused(inviteBox);
		}
		rebuildSeals();
		askedAt = Util.getMillis();
		ask(LangitUiPayloads.Minta.ASK, "");
	}

	@Override
	public void removed() {
		if (open == this) {
			open = null;
		}
		super.removed();
	}

	@Override
	public void tick() {
		if (Util.getMillis() - askedAt > 3000L) {
			askedAt = Util.getMillis();
			ask(LangitUiPayloads.Minta.ASK, "");
		}
	}

	// ================================================================== seals (footer buttons)

	private void rebuildSeals() {
		seals.clear();
		CompoundTag d = data;
		boolean sky = d != null && d.getBooleanOr("langit", false);
		boolean owner = d != null && d.getBooleanOr("pemilik", false);
		boolean hall = d != null && d.getBooleanOr("aula", false);
		CompoundTag island = d == null ? new CompoundTag() : d.getCompoundOrEmpty("pulau");
		boolean standing = sky && !island.getBooleanOr("tertambat", true);
		boolean closed = island.getBooleanOr("tertutup", false);
		Seal ferry = new Seal(Component.translatable(K + "feri"), Component.translatable(K + "feri.tip"), Palette.JADE,
				() -> ask(LangitUiPayloads.Minta.FERI, ""));
		ferry.enabled = standing;
		Seal gate = new Seal(Component.translatable(closed ? K + "gerbang_buka" : K + "gerbang_tutup"), Component.translatable(K + "gerbang.tip"),
				closed ? Palette.JADE : Palette.SEAL, () -> ask(LangitUiPayloads.Minta.GERBANG, ""));
		gate.enabled = sky && owner;
		Seal invite = new Seal(Component.translatable(K + "undang"), Component.translatable(K + "undang.tip"), Palette.GOLD_DARK, this::toggleInvite);
		invite.enabled = sky && owner;
		Seal design = new Seal(Component.translatable(K + "rancang"), Component.translatable(K + "rancang.tip"), Palette.ROD_LIGHT,
				() -> HubNav.openHall(Halls.PAGE_BANGUN));
		design.enabled = hall && owner;
		Seal views = new Seal(Component.translatable(K + "pemandangan"), Component.translatable(K + "pemandangan.tip"), 0xFF4B6FA8,
				() -> LangitClient.openSettings(this));
		seals.add(ferry);
		seals.add(gate);
		seals.add(invite);
		seals.add(design);
		seals.add(views);
		layoutSeals();
	}

	private void layoutSeals() {
		int n = seals.size();
		int room = x1 - x0 - 20;
		int each = Math.min(70, (room - (n - 1) * 4) / n);
		int ax = x0 + 10;
		int ay = y1 - 20;
		for (Seal seal : seals) {
			seal.ax = ax;
			seal.ay = ay;
			seal.bx = ax + each;
			seal.by = ay + 13;
			ax += each + 4;
		}
	}

	private void toggleInvite() {
		if (inviteBox == null) {
			return;
		}
		if (inviteBox.visible && !inviteBox.getValue().isBlank()) {
			ask(LangitUiPayloads.Minta.UNDANG, inviteBox.getValue().strip());
			inviteBox.setValue("");
			inviteBox.visible = false;
			inviteBox.setFocused(false);
			setFocused(null);
			return;
		}
		inviteBox.visible = !inviteBox.visible;
		if (inviteBox.visible) {
			setFocused(inviteBox);
			inviteBox.setFocused(true);
		} else {
			inviteBox.setFocused(false);
			setFocused(null);
		}
	}

	// ================================================================== drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		g.fillGradient(0, 0, width, height, SKY_TOP, SKY_BOTTOM);
		// a dusk glow on the horizon and the first stars
		g.fillGradient(0, height * 2 / 3, width, height, Palette.argb(0x00, DUSK), Palette.argb(0x70, DUSK));
		for (int i = 0; i < 28; i++) {
			float sx = (i * 97 % 1000) / 1000.0F * width;
			float sy = (i * 61 % 1000) / 1000.0F * height * 0.55F;
			float tw = 0.5F + 0.5F * (float) Math.sin(now / 600.0D + i * 1.7D);
			Brush.dot(g, sx, sy, 0.6F + 0.4F * tw, Palette.argb((int) (0x50 + 0x70 * tw), 0xFFF4D8));
		}
		// three drifting cloud banks at the bottom
		for (int layer = 0; layer < 3; layer++) {
			float speed = 0.004F + layer * 0.003F;
			float y = height - 18.0F - layer * 14.0F;
			int alpha = 0x70 - layer * 0x18;
			for (int i = 0; i < 9; i++) {
				float cx = ((i * 71.0F + now * speed) % (width + 80.0F)) - 40.0F;
				Brush.disc(g, cx, y + (i % 2) * 4.0F, 16.0F + (i % 3) * 5.0F, Palette.argb(alpha, layer == 0 ? 0xF0F4FF : 0xD8DDF0));
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		// the scroll: shadow, paper, rods, frame
		g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x60000000);
		g.fillGradient(x0, y0, x1, y1, Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		g.fillGradient(x0 - 4, y0 - 3, x1 + 4, y0 + 3, Palette.ROD_LIGHT, Palette.ROD_DARK);
		g.fillGradient(x0 - 4, y1 - 3, x1 + 4, y1 + 3, Palette.ROD_LIGHT, Palette.ROD_DARK);
		Brush.frame(g, x0, y0, x1, y1, Palette.JADE_DARK);
		CompoundTag d = data;
		Component title = this.title;
		if (d != null && d.contains("nama")) {
			title = Component.translatable(K + "judul_aula", d.getStringOr("nama", ""));
		}
		Brush.disc(g, x0 + 16.0F, y0 + 12.0F, 8.0F, Palette.argb(0xFF, 0x3E6FB0));
		Brush.centered(g, font, Component.literal("天"), x0 + 16.0F, y0 + 8.0F, 0xFFF4FAFF, 1.0F, false);
		Brush.fit(g, font, Brush.bold(title), x0 + 28.0F, y0 + 7.0F, x1 - x0 - 60.0F, Palette.INK, 1.05F, 0.6F, false);
		Brush.rect(g, x0 + 10.0F, y0 + 21.0F, x1 - 10.0F, y0 + 22.0F, Palette.argb(0x90, Palette.JADE_DARK));
		if (d == null) {
			int dots = (int) ((now - openedAt) / 300L % 4L);
			Brush.fitCentered(g, font, Component.translatable(K + "memuat").append(".".repeat(dots)), (x0 + x1) / 2.0F, (y0 + y1) / 2.0F, 200.0F,
					Palette.INK_SOFT, 0.9F, 0.6F, false);
			drawSeals(g, mouseX, mouseY);
			help.render(g, font, mouseX, mouseY, width, height);
			return;
		}
		if (!d.getBooleanOr("aktif", true)) {
			Brush.fitCentered(g, font, Component.translatable(K + "mati"), (x0 + x1) / 2.0F, y0 + 26.0F, x1 - x0 - 20.0F, Palette.DANGER, 0.7F,
					0.5F, false);
		}
		drawIsland(g, d, now);
		Brush.rect(g, split, y0 + 26.0F, split + 0.8F, bodyBottom, Palette.argb(0x60, Palette.JADE_DARK));
		drawTabs(g, mouseX, mouseY);
		g.enableScissor(split + 4, bodyTop, x1 - 6, bodyBottom);
		float top = bodyTop + 2.0F - scroll;
		float end = switch (tab) {
			case TAB_CHRONICLE -> chronicle(g, d, split + 8.0F, top, x1 - 12.0F);
			case TAB_NEIGHBOURS -> neighbours(g, d, split + 8.0F, top, x1 - 12.0F);
			default -> events(g, d, split + 8.0F, top, x1 - 12.0F, now, mouseX, mouseY);
		};
		g.disableScissor();
		contentH = end + scroll - bodyTop;
		float max = Math.max(0.0F, contentH - (bodyBottom - bodyTop));
		scroll = Mth.clamp(scroll, 0.0F, max);
		if (max > 0.0F) {
			float t = scroll / max;
			float barH = Math.max(10.0F, (bodyBottom - bodyTop) * (bodyBottom - bodyTop) / Math.max(1.0F, contentH));
			float by = bodyTop + t * (bodyBottom - bodyTop - barH);
			Brush.rect(g, x1 - 6.0F, by, x1 - 4.0F, by + barH, Palette.argb(0x90, Palette.JADE_DARK));
		}
		drawSeals(g, mouseX, mouseY);
		if (inviteBox != null && inviteBox.visible) {
			Brush.text(g, font, Component.translatable(K + "undang_label"), split + 8.0F, y1 - 49.0F, Palette.INK_SOFT, 0.6F, false);
		}
		help.render(g, font, mouseX, mouseY, width, height);
	}

	// ------------------------------------------------------------------ the island diorama

	private void drawIsland(GuiGraphicsExtractor g, CompoundTag d, long now) {
		float left = x0 + 10.0F;
		float right = split - 6.0F;
		float top = y0 + 26.0F;
		float dioBottom = top + (bodyBottom - top) * 0.58F;
		float cx = (left + right) / 2.0F;
		float wide = right - left;
		boolean sky = d.getBooleanOr("langit", false);
		CompoundTag island = d.getCompoundOrEmpty("pulau");
		int template = island.getIntOr("templat", 11);
		int tier = sky ? island.getIntOr("tingkat", 0) : 0;
		boolean tethered = island.getBooleanOr("tertambat", false);
		int n = Mth.clamp(template - 10, 1, 9);
		// the window: dusk sky, sun, cloud sea
		g.fillGradient((int) left, (int) top, (int) right, (int) dioBottom, 0xFF1C2D52, 0xFFD8936A);
		Brush.halo(g, right - 22.0F, dioBottom - 18.0F, 7.0F, 8.0F, 0xFFD27A, 0.8F);
		Brush.disc(g, right - 22.0F, dioBottom - 18.0F, 6.0F, 0xFFFFE2A0);
		float sea = dioBottom - 10.0F;
		for (int i = 0; i < 10; i++) {
			float px = left + ((i * 23.0F + now * 0.006F) % (wide + 30.0F)) - 15.0F;
			Brush.disc(g, px, sea + (i % 2) * 2.0F, 7.0F + (i % 3) * 2.0F, Palette.argb(0xD0, 0xF2F4FF));
		}
		Brush.rect(g, left, sea + 2.0F, right, dioBottom, Palette.argb(0xE0, 0xEDEFFA));
		float bob = (float) Math.sin(now / 900.0D) * 1.2F;
		float r = wide * (0.18F + 0.012F * n);
		float plaza = top + (dioBottom - top) * 0.42F + bob;
		if (tethered && sky) {
			// the tether pillar from the ground stele (below the cloud sea) to the throne
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 250.0D);
			Brush.rect(g, cx - 1.5F, plaza, cx + 1.5F, dioBottom, Palette.argb((int) (0x60 + 0x60 * pulse), 0xBFE8FF));
		}
		int satellites = n <= 2 ? 3 : n <= 4 ? 4 : n <= 6 ? 5 : 6;
		int shownSat = sky ? Math.min(satellites, tier >= 9 ? 6 : tier >= 8 ? satellites - (n >= 7 ? 1 : 0) : tier >= 5 ? 3 : tier >= 4 ? 2
				: tier >= 2 ? 1 : 0) : satellites;
		int ghost = sky ? 0xFF : 0x50;
		// satellites first (behind), with jade bridges
		for (int i = 0; i < satellites; i++) {
			boolean built = i < shownSat;
			float side = i % 2 == 0 ? -1.0F : 1.0F;
			float sx = cx + side * (r + 10.0F + (i / 2) * 7.0F);
			float sy = plaza + (i % 3 == 0 ? -7.0F : i % 3 == 1 ? 5.0F : -1.0F) + (float) Math.sin(now / 700.0D + i) * 0.8F;
			float sr = r * 0.32F;
			int alpha = built ? ghost : 0x40;
			Brush.line(g, cx + side * r * 0.8F, plaza, sx - side * sr, sy, 1.0F, Palette.argb(alpha, 0x5EC6A0));
			cone(g, sx, sy, sr, sr * 1.2F, alpha);
		}
		// the main island: crystals (tier 9) under the cone, the cone, the terraces, the grass
		if (tier >= 9) {
			for (int i = -2; i <= 2; i++) {
				float kx = cx + i * r * 0.28F;
				float ky = plaza + r * 1.15F - Math.abs(i) * 3.0F;
				Brush.line(g, kx, ky, kx, ky + 6.0F + (2 - Math.abs(i)) * 3.0F, 2.0F, Palette.argb(0xE0, 0xB98AE8));
			}
		}
		cone(g, cx, plaza, r, r * 1.35F, ghost);
		// mist falls from tier 4
		if (tier >= 4) {
			for (int i = 0; i < 3; i++) {
				float fx = cx + (i - 1) * r * 0.85F;
				float shift = (now / 40L % 12L);
				for (int k = 0; k < 4; k++) {
					float fy = plaza + 1.0F + ((k * 3.0F + shift) % 12.0F) * 2.0F;
					Brush.rect(g, fx - 0.6F, fy, fx + 0.6F, fy + 3.0F, Palette.argb(0x90 - k * 0x18, 0xCFE8FF));
				}
			}
		}
		// the pagoda grows with the tier
		int floors = sky ? Mth.clamp(1 + tier / 2, 1, 5) : 3;
		float py = plaza - 2.0F;
		for (int f = 0; f < floors; f++) {
			float fw = r * (0.42F - f * 0.06F);
			Brush.rect(g, cx - fw * 0.6F, py - 4.0F, cx + fw * 0.6F, py, Palette.argb(ghost, 0xEDE3D0));
			Brush.rect(g, cx - fw, py - 5.0F, cx + fw, py - 4.0F, Palette.argb(ghost, f % 2 == 0 ? 0x2F5F8A : 0x3B7A5E));
			py -= 5.0F;
		}
		Brush.dot(g, cx, py - 1.0F, 0.9F, Palette.argb(ghost, 0xFFF4C0));
		// net pillars from tier 5
		if (tier >= 5) {
			for (int side = -1; side <= 1; side += 2) {
				float nx = cx + side * r * 0.93F;
				Brush.rect(g, nx - 0.6F, plaza - 6.0F, nx + 0.6F, plaza, 0xFFE8E2D0);
				Brush.dot(g, nx, plaza - 6.5F, 1.0F, 0xFFB98AE8);
			}
		}
		// the Gate of the Nine Heavens at tier 9: nine coloured eaves
		if (tier >= 9) {
			int[] colors = {0xF2C14E, 0xF29A4E, 0xD94A3A, 0xE88AB0, 0xC8A8F0, 0x9A6AD8, 0x4A7AD8, 0x5EC6A0, 0xF4F4F4};
			float gx = cx + r * 0.55F;
			float gy = plaza - 1.0F;
			for (int i = 0; i < 9; i++) {
				float gw = 5.0F - i * 0.4F;
				Brush.rect(g, gx - gw, gy - 1.2F, gx + gw, gy, Palette.argb(0xFF, colors[i]));
				gy -= 1.6F;
			}
		}
		if (!sky) {
			Brush.fitCentered(g, font, Component.translatable(K + "belum_pulau"), cx, top + 4.0F, wide - 8.0F, 0xFFF4FAFF, 0.62F, 0.45F, true);
		}
		// the nine tier seals
		float sealY = dioBottom + 5.0F;
		float step = Math.min(13.0F, wide / 9.0F);
		float sx0 = cx - step * 4.0F;
		for (int i = 1; i <= 9; i++) {
			boolean done = i <= tier;
			float scx = sx0 + (i - 1) * step;
			Brush.disc(g, scx, sealY + 4.0F, step * 0.38F, done ? Palette.SEAL : Palette.argb(0x60, Palette.INK_FAINT));
			Brush.centered(g, font, Component.literal(String.valueOf(i)), scx, sealY + 1.2F, done ? 0xFFFFF4E0 : Palette.INK_FAINT, 0.55F, false);
		}
		// the island's numbers
		float y = sealY + 12.0F;
		float room = wide;
		if (sky) {
			y += stat(g, Component.translatable(K + "templat", SectTemplates.name(template)), left, y, room, Palette.INK, 0.62F);
			y += stat(g, Component.translatable(K + "tingkat", tier), left, y, room, Palette.INK_SOFT, 0.58F);
			y += stat(g, Component.translatable(K + "ketinggian", island.getIntOr("yP", 0), island.getIntOr("dasar", 0)), left, y, room, Palette.INK_SOFT,
					0.58F);
			y += stat(g, Component.translatable(tethered ? K + "tertambat" : K + "berdiri"), left, y, room, tethered ? Palette.WARN : Palette.JADE_DARK,
					0.58F);
			y += stat(g, Component.translatable(K + "faktor", String.format(java.util.Locale.ROOT, "%.3f", island.getDoubleOr("faktor", 1.0D))), left, y,
					room, Palette.QI, 0.58F);
		} else {
			Brush.wrap(g, font, Component.translatable(d.getBooleanOr("aula", false) ? K + "jalan_a" : K + "jalan_b"), left, y, room, Palette.INK_SOFT,
					0.56F, false);
		}
	}

	/** An inverted cone with a grass top: {@code r} half width, {@code depth} below the plaza. */
	private void cone(GuiGraphicsExtractor g, float cx, float top, float r, float depth, int alpha) {
		int rows = 7;
		for (int i = 0; i < rows; i++) {
			float t0 = i / (float) rows;
			float t1 = (i + 1) / (float) rows;
			float w = r * (1.0F - t0 * t0 * 0.9F);
			int color = i < 1 ? 0x6E5A3A : i < 3 ? 0x8C8C86 : i < 5 ? 0x77756E : 0x4E4C52;
			Brush.rect(g, cx - w, top + t0 * depth, cx + w, top + t1 * depth + 0.3F, Palette.argb(alpha, color));
		}
		Brush.rect(g, cx - r, top - 1.5F, cx + r, top + 0.6F, Palette.argb(alpha, 0x5DAA4E));
		Brush.rect(g, cx - r * 0.55F, top - 2.2F, cx + r * 0.55F, top - 1.4F, Palette.argb(alpha, 0xEDEBE4));
	}

	private float stat(GuiGraphicsExtractor g, Component text, float x, float y, float room, int color, float scale) {
		Brush.fit(g, font, text, x, y, room, color, scale, scale * 0.7F, false);
		return 8.5F * scale / 0.6F;
	}

	// ------------------------------------------------------------------ tabs

	private void drawTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		String[] keys = {"tab.peristiwa", "tab.kronik", "tab.tetangga"};
		float left = split + 8.0F;
		float each = (x1 - 12.0F - left) / keys.length;
		for (int i = 0; i < keys.length; i++) {
			float ax = left + i * each;
			boolean selected = i == tab;
			boolean hover = mouseX >= ax && mouseX <= ax + each - 3 && mouseY >= y0 + 24 && mouseY <= y0 + 34;
			int color = selected ? Palette.JADE : hover ? Palette.JADE_LIGHT : Palette.argb(0x90, Palette.JADE_DULL);
			Brush.chamfer(g, (int) ax, y0 + 24, (int) (ax + each - 3), y0 + 34, 2, color);
			Brush.fitCentered(g, font, Brush.bold(Component.translatable(K + keys[i])), ax + (each - 3) / 2.0F, y0 + 26.5F, each - 8.0F,
					selected ? 0xFFFFF8E8 : Palette.INK, 0.6F, 0.4F, false);
		}
	}

	private int tabAt(double mx, double my) {
		float left = split + 8.0F;
		float each = (x1 - 12.0F - left) / 3.0F;
		if (my < y0 + 24 || my > y0 + 34 || mx < left || mx > x1 - 12) {
			return -1;
		}
		return Mth.clamp((int) ((mx - left) / each), 0, 2);
	}

	private float heading(GuiGraphicsExtractor g, Component text, float x, float y, float room) {
		Brush.rect(g, x, y + 2.0F, x + 2.5F, y + 5.5F, Palette.SEAL);
		Brush.fit(g, font, Brush.bold(text), x + 5.0F, y, room - 5.0F, Palette.JADE_DARK, 0.7F, 0.45F, false);
		return 10.0F;
	}

	private float line(GuiGraphicsExtractor g, Component text, float x, float y, float room, int color, float scale) {
		return Brush.wrap(g, font, text, x, y, room, color, scale, false) + 1.5F;
	}

	/** The events tab: the sky's state, the event clock, the Heaven Court order. Returns the y below it. */
	private float events(GuiGraphicsExtractor g, CompoundTag d, float left, float top, float right, long now, int mouseX, int mouseY) {
		float room = right - left;
		float y = top;
		depositX0 = -1.0F;
		boolean sky = d.getBooleanOr("langit", false);
		if (!sky) {
			y += heading(g, Component.translatable(K + "cara"), left, y, room);
			y += line(g, Component.translatable(K + "cara.1"), left, y, room, Palette.INK, 0.58F);
			y += line(g, Component.translatable(K + "cara.2"), left, y, room, Palette.INK, 0.58F);
			y += line(g, Component.translatable(K + "cara.3"), left, y, room, Palette.INK, 0.58F);
			y += line(g, Component.translatable(K + "cara.4"), left, y, room, Palette.INK_SOFT, 0.56F);
			return y;
		}
		CompoundTag island = d.getCompoundOrEmpty("pulau");
		CompoundTag ev = d.getCompoundOrEmpty("peristiwa");
		y += heading(g, Component.translatable(K + "keadaan"), left, y, room);
		y += flag(g, Component.translatable(K + "formasi_agung"), island.getBooleanOr("formasi", false), left, y, room);
		y += flag(g, Component.translatable(K + "jaring_awan"), island.getBooleanOr("jaring", false), left, y, room);
		y += flag(g, Component.translatable(K + "gerbang_terbuka"), !island.getBooleanOr("tertutup", false), left, y, room);
		y += flag(g, Component.translatable(K + "tangga_awan"), island.getBooleanOr("tangga", false), left, y, room);
		long acknowledged = ev.getLongOr("diakui", -1L);
		if (acknowledged >= 0L) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 400.0D);
			y += line(g, Component.translatable(K + "diakui", acknowledged), left, y, room, Palette.mix(Palette.GOLD_DARK, Palette.GOLD, pulse), 0.62F);
		}
		y += 3.0F;
		y += heading(g, Component.translatable(K + "jadwal"), left, y, room);
		if (ev.getBooleanOr("bajak_kini", false) || ev.getBooleanOr("serbu_kini", false)) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 200.0D);
			y += line(g, Component.translatable(ev.getBooleanOr("bajak_kini", false) ? K + "bajak_kini" : K + "serbu_kini"), left, y, room,
					Palette.mix(Palette.SEAL_DARK, Palette.SEAL_GLOW, pulse), 0.62F);
		}
		y += when(g, "bajak", ev, island.getIntOr("tingkat", 0) >= 3, left, y, room);
		y += when(g, "utusan", ev, ev.getBooleanOr("utusan_layak", false), left, y, room);
		y += when(g, "tamu", ev, true, left, y, room);
		if (ev.getBooleanOr("pesta_kini", false)) {
			y += line(g, Component.translatable(K + "pesta_kini"), left, y, room, Palette.GOLD_DARK, 0.6F);
		} else {
			y += when(g, "pesta", ev, true, left, y, room);
		}
		String pending = ev.getStringOr("tunda", "");
		if (!pending.isEmpty()) {
			y += line(g, Component.translatable(K + "tunda", Component.translatable(K + "jenis." + pending)), left, y, room, Palette.INK_FAINT, 0.55F);
		}
		int refusals = ev.getIntOr("tolak", 0);
		if (refusals > 0) {
			y += line(g, Component.translatable(K + "tolak", refusals), left, y, room, refusals >= 3 ? Palette.DANGER : Palette.INK_SOFT, 0.55F);
		}
		y += 3.0F;
		y += heading(g, Component.translatable(K + "perintah"), left, y, room);
		CompoundTag order = d.getCompoundOrEmpty("perintah");
		if (order.getIntOr("status", 0) != 1) {
			y += line(g, Component.translatable(K + "perintah.kosong"), left, y, room, Palette.INK_FAINT, 0.58F);
			return y;
		}
		int kills = order.getIntOr("kills", 0);
		int need = Math.max(1, order.getIntOr("perlu", 12));
		y += line(g, Component.translatable(K + "perintah.isi", need, order.getLongOr("sisa", 0L)), left, y, room, Palette.INK, 0.58F);
		float bar = Mth.clamp(kills / (float) need, 0.0F, 1.0F);
		Brush.rect(g, left, y + 1.0F, right, y + 5.0F, Palette.argb(0x50, Palette.INK_FAINT));
		Brush.rect(g, left, y + 1.0F, left + (right - left) * bar, y + 5.0F, Palette.GOLD);
		Brush.text(g, font, Component.literal(kills + " / " + need), right - 30.0F, y - 0.5F, Palette.INK, 0.55F, false);
		y += 8.0F;
		if (d.getBooleanOr("pemilik", false)) {
			depositX0 = left;
			depositX1 = Math.min(right, left + 120.0F);
			depositY0 = y;
			depositY1 = y + 12.0F;
			boolean hover = mouseX >= depositX0 && mouseX <= depositX1 && mouseY >= depositY0 && mouseY <= depositY1 && mouseY >= bodyTop
					&& mouseY <= bodyBottom;
			Brush.chamfer(g, (int) depositX0, (int) depositY0, (int) depositX1, (int) depositY1, 2, hover ? Palette.GOLD : Palette.GOLD_DARK);
			Brush.fitCentered(g, font, Brush.bold(Component.translatable(K + "setor")), (depositX0 + depositX1) / 2.0F, depositY0 + 2.5F,
					depositX1 - depositX0 - 6.0F, 0xFFFFF8E8, 0.6F, 0.4F, false);
			y += 15.0F;
		}
		return y;
	}

	private float flag(GuiGraphicsExtractor g, Component text, boolean on, float x, float y, float room) {
		Brush.disc(g, x + 3.0F, y + 3.0F, 2.5F, on ? Palette.JADE : Palette.argb(0x70, Palette.INK_FAINT));
		Brush.fit(g, font, text, x + 9.0F, y, room - 9.0F, on ? Palette.INK : Palette.INK_FAINT, 0.58F, 0.42F, false);
		return 8.5F;
	}

	private float when(GuiGraphicsExtractor g, String what, CompoundTag ev, boolean possible, float x, float y, float room) {
		Component text;
		int color = Palette.INK_SOFT;
		if (!possible) {
			text = Component.translatable(K + "jadwal." + what + ".belum");
			color = Palette.INK_FAINT;
		} else if (!ev.contains(what)) {
			text = Component.translatable(K + "jadwal." + what + ".nanti");
		} else {
			long days = ev.getLongOr(what, 0L);
			text = days <= 0L ? Component.translatable(K + "jadwal." + what + ".siap") : Component.translatable(K + "jadwal." + what, days);
			if (days <= 1L) {
				color = Palette.SEAL_DARK;
			}
		}
		return line(g, text, x, y, room, color, 0.58F);
	}

	/** The chronicle tab: newest first, with the day. */
	private float chronicle(GuiGraphicsExtractor g, CompoundTag d, float left, float top, float right) {
		float room = right - left;
		float y = top;
		y += heading(g, Component.translatable(K + "kronik"), left, y, room);
		ListTag list = d.getListOrEmpty("kronik");
		if (list.isEmpty()) {
			return y + line(g, Component.translatable(K + "kronik.kosong"), left, y, room, Palette.INK_FAINT, 0.58F);
		}
		long today = d.getLongOr("hari", 0L);
		for (Tag tag : list) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			long ago = today - entry.getLongOr("day", today);
			Component when = ago <= 0L ? Component.translatable(K + "hari_ini") : Component.translatable(K + "hari_lalu", ago);
			Component text = entry.read("text", ComponentSerialization.CODEC).orElse(Component.empty());
			Brush.text(g, font, when, left, y, Palette.GOLD_DARK, 0.52F, false);
			y += 6.0F;
			y += line(g, text, left + 4.0F, y, room - 4.0F, Palette.INK, 0.56F) + 1.5F;
		}
		return y;
	}

	/** The neighbours tab: the NPC sky sects with tier, stance and standing. */
	private float neighbours(GuiGraphicsExtractor g, CompoundTag d, float left, float top, float right) {
		float room = right - left;
		float y = top;
		y += heading(g, Component.translatable(K + "tetangga"), left, y, room);
		ListTag list = d.getListOrEmpty("sekte");
		if (list.isEmpty()) {
			return y + line(g, Component.translatable(K + "tetangga.kosong"), left, y, room, Palette.INK_FAINT, 0.58F);
		}
		for (Tag tag : list) {
			if (!(tag instanceof CompoundTag s)) {
				continue;
			}
			boolean met = s.getBooleanOr("kenal", false);
			Component name = met ? s.read("nama", ComponentSerialization.CODEC).orElse(Component.literal("?"))
					: Component.translatable(K + "tetangga.belum_kenal");
			String stance = s.getStringOr("sikap", "netral");
			int color = switch (stance) {
				case "sekutu" -> Palette.JADE_DARK;
				case "ramah" -> Palette.JADE;
				case "dingin" -> Palette.WARN;
				case "bermusuhan", "perang" -> Palette.DANGER;
				default -> Palette.INK_SOFT;
			};
			Brush.chamfer(g, (int) left, (int) y, (int) right, (int) (y + 26.0F), 2, Palette.argb(0x30, color));
			Brush.disc(g, left + 8.0F, y + 9.0F, 5.0F, Palette.argb(0xFF, 0x3E6FB0));
			Brush.centered(g, font, Component.literal("雲"), left + 8.0F, y + 6.0F, 0xFFF4FAFF, 0.6F, false);
			Brush.fit(g, font, Brush.bold(name), left + 16.0F, y + 2.0F, room - 20.0F, Palette.INK, 0.62F, 0.42F, false);
			Brush.fit(g, font, Component.translatable(K + "tetangga.baris", s.getIntOr("tier", 1),
					Component.translatable("kultivator.sekte.sikap." + stance)), left + 16.0F, y + 10.0F, room - 20.0F, color, 0.55F, 0.4F, false);
			int standing = s.getIntOr("standing", 0);
			float mid = left + 16.0F + (room - 24.0F) / 2.0F;
			float half = (room - 24.0F) / 2.0F;
			Brush.rect(g, left + 16.0F, y + 19.0F, right - 8.0F, y + 21.0F, Palette.argb(0x40, Palette.INK_FAINT));
			float to = mid + half * Mth.clamp(standing / 100.0F, -1.0F, 1.0F);
			Brush.rect(g, Math.min(mid, to), y + 19.0F, Math.max(mid, to), y + 21.0F, color);
			Brush.rect(g, mid - 0.3F, y + 18.0F, mid + 0.3F, y + 22.0F, Palette.INK_SOFT);
			y += 29.0F;
			if (met && s.contains("pos")) {
				int[] pos = s.getIntArray("pos").orElse(new int[0]);
				if (pos.length >= 2) {
					y += line(g, Component.translatable(K + "tetangga.letak", pos[0], pos[1]), left + 4.0F, y - 2.0F, room - 4.0F, Palette.INK_FAINT, 0.5F);
				}
			}
		}
		return y;
	}

	private void drawSeals(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Seal hovered = null;
		for (Seal seal : seals) {
			boolean hover = seal.over(mouseX, mouseY);
			int color = !seal.enabled ? Palette.LOCKED : hover ? Palette.mix(seal.color, 0xFFFFFFFF, 0.25F) : seal.color;
			Brush.chamfer(g, seal.ax, seal.ay, seal.bx, seal.by, 2, color);
			Brush.fitCentered(g, font, Brush.bold(seal.label), (seal.ax + seal.bx) / 2.0F, seal.ay + 3.0F, seal.bx - seal.ax - 4.0F,
					seal.enabled ? 0xFFFFF8E8 : Palette.LOCKED_PAPER, 0.6F, 0.38F, false);
			if (hover) {
				hovered = seal;
			}
		}
		if (hovered != null) {
			g.setComponentTooltipForNextFrame(font, List.of(hovered.label.copy().withStyle(ChatFormatting.GOLD), hovered.tip), mouseX, mouseY);
		}
	}

	// ================================================================== input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (inviteBox != null && inviteBox.visible && inviteBox.isFocused() && !inviteBox.isMouseOver(mx, my)) {
			inviteBox.setFocused(false);
			setFocused(null);
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (help.click(mx, my)) {
				return true;
			}
			int clickedTab = tabAt(mx, my);
			if (clickedTab >= 0 && clickedTab != tab) {
				tab = clickedTab;
				scroll = 0.0F;
				UiSound.tab();
				return true;
			}
			if (depositX0 >= 0.0F && mx >= depositX0 && mx <= depositX1 && my >= depositY0 && my <= depositY1 && my >= bodyTop && my <= bodyBottom) {
				UiSound.press();
				ask(LangitUiPayloads.Minta.SETOR, "");
				return true;
			}
			for (Seal seal : seals) {
				if (seal.over(mx, my)) {
					if (seal.enabled) {
						UiSound.press();
						seal.action.run();
					} else {
						UiSound.denied();
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (help.release(event.x(), event.y())) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (x > split && y >= bodyTop && y <= bodyBottom) {
			scroll -= (float) scrollY * 12.0F;
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (inviteBox != null && inviteBox.visible && inviteBox.isFocused()) {
			if (event.isConfirmation()) {
				toggleInvite();
				return true;
			}
			if (event.isEscape()) {
				inviteBox.visible = false;
				inviteBox.setFocused(false);
				setFocused(null);
				return true;
			}
			return super.keyPressed(event);
		}
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
