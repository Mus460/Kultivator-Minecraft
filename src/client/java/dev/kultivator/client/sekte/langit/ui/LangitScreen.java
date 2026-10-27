package dev.kultivator.client.sekte.langit.ui;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.InkField;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihOrang;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.sekte.langit.LangitClient;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sekte.langit.main.LangitUiPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sekte › Wilayah › Langit (DESIGN 37.7.9, 43.5, 43.12.1: was the "Sekte Langit" screen of module 37G). A living diorama of
 * the hall's island drawn from the synced numbers (the inverted cone, the satellites and jade bridges of its template, the
 * pagoda that grows with the tier, the mist falls from tier 4, the net pillars from tier 5, the crystals and the Gate of
 * the Nine Heavens at tier 9, the tether pillar while tethered, the cloud sea below) with the nine tier seals and the
 * island's numbers; beside it (below it in a narrow box) one scroll with stacked headings (43.0.4, no tabs): the sky's
 * state, the event clock, the Heaven Court order with its "lay the stones" talisman and the NPC sky sects (the sky
 * chronicle is a source of Sekte › Ikhtisar › Catatan, 43.11 G37). Foot talismans: Feri, Gerbang, Undang, Rancang (Sekte › Bangun) and Pemandangan (Pengaturan › Tampilan, the
 * switches of {@code LangitClientConfig.rows()}). Undang (R30) picks an online player from the kit's people picker, with a
 * name field for anyone else. All numbers come from the server ({@code LangitUi}, kept by {@link LangitUiClient}), asked
 * for when shown and every 3 s while shown. Text 1.0.
 */
public class LangitScreen extends Page {

	private static final String K = "kultivator.sekte_langit.layar.";
	private static final String M = "kultivator.gui43.m10.langit.";
	private static final String P = "kultivator.gui43.p5.";
	private static final int KAKI = 16;
	private static final long SEGAR_MS = 3000L;

	private static @Nullable LangitScreen tampil;

	private final GulirIsi gulir = new GulirIsi();
	private final PemilihOrang pemain = new PemilihOrang(1);
	private final InkField nama = new InkField(0, 0, 80, 14, 16, this::undangNama);
	private final long openedAt = Util.getMillis();
	private long askedAt;
	private boolean undang;

	public LangitScreen() {
		nama.hint(Component.translatable(K + "undang_nama").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** True while this page is on screen. */
	static boolean ditampilkan() {
		return tampil != null;
	}

	// ================================================================== identity and life

	@Override
	public Component title() {
		CompoundTag d = LangitUiClient.data();
		return d != null && d.contains("nama") ? Component.translatable(K + "judul_aula", d.getStringOr("nama", "")) : Component.translatable(K + "judul");
	}

	@Override
	public Component shortName() {
		return Component.translatable(K + "judul");
	}

	@Override
	public int glyph() {
		return Glyphs.BUILD;
	}

	@Override
	public String helpTopic() {
		return "sekte_langit";
	}

	@Override
	protected void build() {
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		tampil = this;
		minta();
		super.onShow(fokus);
	}

	@Override
	public void onHide() {
		if (tampil == this) {
			tampil = null;
		}
		tutupUndang();
	}

	@Override
	public void tick() {
		if (Util.getMillis() - askedAt > SEGAR_MS) {
			minta();
		}
	}

	private void minta() {
		askedAt = Util.getMillis();
		LangitUiClient.minta(LangitUiPayloads.Minta.ASK, "");
	}

	@Override
	public boolean rinci() {
		return undang;
	}

	@Override
	public boolean mundur() {
		if (undang) {
			tutupUndang();
			return true;
		}
		return false;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	// ================================================================== drawing

	@Override
	protected void lukis(Lukis l) {
		long now = Util.getMillis();
		CompoundTag d = LangitUiClient.data();
		if (d == null) {
			int dots = (int) ((now - openedAt) / 300L % 4L);
			l.tengah(Component.translatable(K + "memuat").append(".".repeat(dots)), x + w / 2.0F, y + h / 2.0F - 4.0F, w - 8, l.gaya.teksLemah);
			return;
		}
		int bawah = y + h - KAKI - 2;
		int cy = y;
		if (!d.getBooleanOr("aktif", true)) {
			cy += Math.round(l.bungkus(Component.translatable(K + "mati"), x + 2, cy, w - 4, Palette.DANGER)) + 2;
		}
		if (w >= 300) {
			// two columns: the island stays, the right column scrolls
			int split = x + Mth.clamp(Math.round(w * 0.42F), 130, 210);
			pulau(l, d, x + 2, cy, split - 6, bawah, now, Math.min(110, Math.round((bawah - cy) * 0.55F)));
			l.g.fill(split - 2, cy, split - 1, bawah, Palette.argb(0x60, Palette.JADE_DARK));
			int ry = gulir.mulai(l, split + 4, cy, x + w - 5, bawah);
			ry = isi(l, d, split + 4, ry, x + w - 7, now);
			gulir.selesai(l, ry);
		} else {
			int ry = gulir.mulai(l, x, cy, x + w - 5, bawah);
			ry = pulau(l, d, x + 2, ry, x + w - 7, Integer.MAX_VALUE, now, 80) + 6;
			ry = isi(l, d, x + 2, ry, x + w - 7, now);
			gulir.selesai(l, ry);
		}
		kaki(l, d);
		if (undang) {
			undang(l, d);
		}
	}

	/**
	 * The stacked right column (or the rest of the narrow scroll): events and neighbours. The sky chronicle is in
	 * Sekte › Ikhtisar › Catatan (a source of the one Kronik, registered by {@link LangitUiClient}).
	 */
	private int isi(Lukis l, CompoundTag d, int left, int y, int right, long now) {
		y = peristiwa(l, d, left, y, right, now) + 6;
		return tetangga(l, d, left, y, right);
	}

	// ------------------------------------------------------------------ the island diorama

	/** The diorama ({@code tinggi} high), the tier seals and the island's numbers; returns the y below them. */
	private int pulau(Lukis l, CompoundTag d, int left, int top, int right, int batas, long now, int tinggi) {
		float dioBottom = top + tinggi;
		float cx = (left + right) / 2.0F;
		float wide = right - left;
		boolean sky = d.getBooleanOr("langit", false);
		CompoundTag island = d.getCompoundOrEmpty("pulau");
		int template = island.getIntOr("templat", 11);
		int tier = sky ? island.getIntOr("tingkat", 0) : 0;
		boolean tethered = island.getBooleanOr("tertambat", false);
		int n = Mth.clamp(template - 10, 1, 9);
		GuiGraphicsExtractor g = l.g;
		// the window: dusk sky, sun, cloud sea
		g.fillGradient(left, top, right, Math.round(dioBottom), 0xFF1C2D52, 0xFFD8936A);
		l.klip(left, top, right, Math.round(dioBottom));
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
			l.tengah(Component.translatable(K + "belum_pulau"), cx, top + 4.0F, wide - 8.0F, 0xFFF4FAFF);
		}
		l.lepasKlip();
		// the nine tier seals
		int sealY = Math.round(dioBottom) + 4;
		float step = Math.min(14.0F, wide / 9.0F);
		float sx0 = cx - step * 4.0F;
		for (int i = 1; i <= 9; i++) {
			boolean done = i <= tier;
			float scx = sx0 + (i - 1) * step;
			Brush.disc(g, scx, sealY + 5.0F, step * 0.42F, done ? Palette.SEAL : Palette.argb(0x60, Palette.INK_FAINT));
			l.tengah(Component.literal(String.valueOf(i)), scx, sealY + 1, step, done ? 0xFFFFF4E0 : l.gaya.teksSamar);
		}
		// the island's numbers
		int y = sealY + 14;
		int room = right - left;
		if (sky) {
			y = baris(l, Component.translatable(K + "templat", SectTemplates.name(template)), left, y, room, l.gaya.teks, batas);
			y = baris(l, Component.translatable(K + "tingkat", tier), left, y, room, l.gaya.teksLemah, batas);
			y = baris(l, Component.translatable(K + "ketinggian", island.getIntOr("yP", 0), island.getIntOr("dasar", 0)), left, y, room, l.gaya.teksLemah,
					batas);
			y = baris(l, Component.translatable(tethered ? K + "tertambat" : K + "berdiri"), left, y, room, tethered ? Palette.WARN : Palette.JADE_DARK,
					batas);
			y = baris(l, Component.translatable(K + "faktor", String.format(java.util.Locale.ROOT, "%.3f", island.getDoubleOr("faktor", 1.0D))), left, y,
					room, Palette.QI, batas);
		} else {
			y += Math.round(l.bungkus(Component.translatable(d.getBooleanOr("aula", false) ? K + "jalan_a" : K + "jalan_b"), left, y, room,
					l.gaya.teksLemah, Math.max(9, batas - y)));
		}
		return y;
	}

	/** One line of the island's numbers while it fits above {@code batas} (the fixed left column). */
	private static int baris(Lukis l, Component text, int x, int y, int room, int color, int batas) {
		if (y + 9 > batas) {
			return y;
		}
		l.teks(text, x, y, room, color);
		return y + 10;
	}

	/** An inverted cone with a grass top: {@code r} half width, {@code depth} below the plaza. */
	private static void cone(GuiGraphicsExtractor g, float cx, float top, float r, float depth, int alpha) {
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

	// ------------------------------------------------------------------ the stacked sections

	private int teks(Lukis l, Component text, int x, int y, int room, int color) {
		return y + Math.round(l.bungkus(text, x, y, room, color)) + 1;
	}

	/** The sky's state, the event clock, the Heaven Court order. Returns the y below it. */
	private int peristiwa(Lukis l, CompoundTag d, int left, int top, int right, long now) {
		int room = right - left;
		int y = top;
		boolean sky = d.getBooleanOr("langit", false);
		if (!sky) {
			y += l.kepala(Component.translatable(K + "cara"), left, y, right);
			y = teks(l, Component.translatable(K + "cara.1"), left, y, room, l.gaya.teks);
			y = teks(l, Component.translatable(K + "cara.2"), left, y, room, l.gaya.teks);
			y = teks(l, Component.translatable(K + "cara.3"), left, y, room, l.gaya.teks);
			return teks(l, Component.translatable(K + "cara.4"), left, y, room, l.gaya.teksLemah);
		}
		CompoundTag island = d.getCompoundOrEmpty("pulau");
		CompoundTag ev = d.getCompoundOrEmpty("peristiwa");
		y += l.kepala(Component.translatable(K + "keadaan"), left, y, right);
		y = tanda(l, Component.translatable(K + "formasi_agung"), island.getBooleanOr("formasi", false), left, y, room);
		y = tanda(l, Component.translatable(K + "jaring_awan"), island.getBooleanOr("jaring", false), left, y, room);
		y = tanda(l, Component.translatable(K + "gerbang_terbuka"), !island.getBooleanOr("tertutup", false), left, y, room);
		y = tanda(l, Component.translatable(K + "tangga_awan"), island.getBooleanOr("tangga", false), left, y, room);
		long acknowledged = ev.getLongOr("diakui", -1L);
		if (acknowledged >= 0L) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 400.0D);
			y = teks(l, Component.translatable(K + "diakui", acknowledged), left, y, room, Palette.mix(Palette.GOLD_DARK, Palette.GOLD, pulse));
		}
		y += 4;
		y += l.kepala(Component.translatable(K + "jadwal"), left, y, right);
		if (ev.getBooleanOr("bajak_kini", false) || ev.getBooleanOr("serbu_kini", false)) {
			float pulse = 0.5F + 0.5F * (float) Math.sin(now / 200.0D);
			y = teks(l, Component.translatable(ev.getBooleanOr("bajak_kini", false) ? K + "bajak_kini" : K + "serbu_kini"), left, y, room,
					Palette.mix(Palette.SEAL_DARK, Palette.SEAL_GLOW, pulse));
		}
		y = kapan(l, "bajak", ev, island.getIntOr("tingkat", 0) >= 3, left, y, room);
		y = kapan(l, "utusan", ev, ev.getBooleanOr("utusan_layak", false), left, y, room);
		y = kapan(l, "tamu", ev, true, left, y, room);
		if (ev.getBooleanOr("pesta_kini", false)) {
			y = teks(l, Component.translatable(K + "pesta_kini"), left, y, room, Palette.GOLD_DARK);
		} else {
			y = kapan(l, "pesta", ev, true, left, y, room);
		}
		String pending = ev.getStringOr("tunda", "");
		if (!pending.isEmpty()) {
			y = teks(l, Component.translatable(K + "tunda", Component.translatable(K + "jenis." + pending)), left, y, room, l.gaya.teksSamar);
		}
		int refusals = ev.getIntOr("tolak", 0);
		if (refusals > 0) {
			y = teks(l, Component.translatable(K + "tolak", refusals), left, y, room, refusals >= 3 ? Palette.DANGER : l.gaya.teksLemah);
		}
		y += 4;
		y += l.kepala(Component.translatable(K + "perintah"), left, y, right);
		CompoundTag order = d.getCompoundOrEmpty("perintah");
		if (order.getIntOr("status", 0) != 1) {
			return teks(l, Component.translatable(K + "perintah.kosong"), left, y, room, l.gaya.teksSamar);
		}
		int kills = order.getIntOr("kills", 0);
		int need = Math.max(1, order.getIntOr("perlu", 12));
		y = teks(l, Component.translatable(K + "perintah.isi", need, order.getLongOr("sisa", 0L)), left, y, room, l.gaya.teks);
		Component hitung = Component.literal(kills + " / " + need);
		float bar = Mth.clamp(kills / (float) need, 0.0F, 1.0F);
		int barRight = right - font.width(hitung) - 4;
		Brush.rect(l.g, left, y + 3.0F, barRight, y + 7.0F, Palette.argb(0x50, Palette.INK_FAINT));
		Brush.rect(l.g, left, y + 3.0F, left + (barRight - left) * bar, y + 7.0F, Palette.GOLD);
		l.kanan(hitung, right, y, l.gaya.teks);
		y += 12;
		if (d.getBooleanOr("pemilik", false)) {
			TalismanButton.gambar(l, "setor", left, y, Math.min(room, 140), 14, Component.translatable(K + "setor"), true, null,
					() -> LangitUiClient.minta(LangitUiPayloads.Minta.SETOR, ""));
			y += 17;
		}
		return y;
	}

	private int tanda(Lukis l, Component text, boolean on, int x, int y, int room) {
		Brush.disc(l.g, x + 3.0F, y + 4.0F, 2.5F, on ? Palette.JADE : Palette.argb(0x70, Palette.INK_FAINT));
		l.teks(text, x + 9, y, room - 9, on ? l.gaya.teks : l.gaya.teksSamar);
		return y + 10;
	}

	private int kapan(Lukis l, String what, CompoundTag ev, boolean possible, int x, int y, int room) {
		Component text;
		int color = l.gaya.teksLemah;
		if (!possible) {
			text = Component.translatable(K + "jadwal." + what + ".belum");
			color = l.gaya.teksSamar;
		} else if (!ev.contains(what)) {
			text = Component.translatable(K + "jadwal." + what + ".nanti");
		} else {
			long days = ev.getLongOr(what, 0L);
			text = days <= 0L ? Component.translatable(K + "jadwal." + what + ".siap") : Component.translatable(K + "jadwal." + what, days);
			if (days <= 1L) {
				color = Palette.SEAL_DARK;
			}
		}
		return teks(l, text, x, y, room, color);
	}

	/**
	 * The NPC sky sects: only a count and a link. Their cards (tier, stance, standing, distance, diplomacy) live in the one
	 * sect list, Sekte › Luar › Sekte Dunia, marked with the sky icon (43.12.1, G36).
	 */
	private int tetangga(Lukis l, CompoundTag d, int left, int top, int right) {
		int room = right - left;
		int y = top + l.kepala(Component.translatable(K + "tetangga"), left, top, right);
		ListTag list = d.getListOrEmpty("sekte");
		if (list.isEmpty()) {
			return teks(l, Component.translatable(K + "tetangga.kosong"), left, y, room, l.gaya.teksSamar);
		}
		int kenal = 0;
		for (Tag tag : list) {
			if (tag instanceof CompoundTag s && s.getBooleanOr("kenal", false)) {
				kenal++;
			}
		}
		y = teks(l, Component.translatable(P + "tetangga_ringkas", list.size(), kenal), left, y, room, l.gaya.teksLemah) + 2;
		TalismanButton.gambar(l, "tetangga", left, y, Math.min(room, 150), 14, Component.translatable(P + "ke_sekte_dunia"), true, null,
				() -> dev.kultivator.client.sekte.diplomasi.LuarPage.bukaSekte(""));
		return y + 16;
	}

	// ------------------------------------------------------------------ the foot talismans

	private void kaki(Lukis l, CompoundTag d) {
		boolean sky = d.getBooleanOr("langit", false);
		boolean owner = d.getBooleanOr("pemilik", false);
		boolean hall = d.getBooleanOr("aula", false);
		CompoundTag island = d.getCompoundOrEmpty("pulau");
		boolean standing = sky && !island.getBooleanOr("tertambat", true);
		boolean closed = island.getBooleanOr("tertutup", false);
		Component bukanLangit = Component.translatable(M + "bukan_langit");
		Component bukanPemilik = Component.translatable(M + "bukan_pemilik");
		int n = 5;
		int gap = 4;
		int each = (w - (n - 1) * gap) / n;
		int by = y + h - KAKI + 1;
		int bx = x;
		seal(l, "feri", bx, by, each, K + "feri", K + "feri.tip", standing, sky ? Component.translatable(M + "tertambat") : bukanLangit,
				() -> LangitUiClient.minta(LangitUiPayloads.Minta.FERI, ""));
		bx += each + gap;
		seal(l, "gerbang", bx, by, each, closed ? K + "gerbang_buka" : K + "gerbang_tutup", K + "gerbang.tip", sky && owner,
				sky ? bukanPemilik : bukanLangit, () -> LangitUiClient.minta(LangitUiPayloads.Minta.GERBANG, ""));
		bx += each + gap;
		seal(l, "undang", bx, by, each, K + "undang", K + "undang.tip", sky && owner, sky ? bukanPemilik : bukanLangit, this::bukaUndang);
		bx += each + gap;
		seal(l, "rancang", bx, by, each, K + "rancang", K + "rancang.tip", hall && owner, Component.translatable(M + "tanpa_aula"),
				() -> tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_BANGUN)));
		bx += each + gap;
		seal(l, "pemandangan", bx, by, x + w - bx, K + "pemandangan", K + "pemandangan.tip", true, null, LangitClient::openSettings);
	}

	private void seal(Lukis l, String kunci, int x0, int y0, int lebar, String label, String tip, boolean aktif, @Nullable Component alasan,
			Runnable aksi) {
		Component nama = Component.translatable(label);
		if (TalismanButton.gambar(l, kunci, x0, y0, lebar, 14, nama, aktif, aktif ? null : alasan, aksi)) {
			l.tip(List.of(nama.copy().withStyle(ChatFormatting.GOLD), Component.translatable(tip)));
		}
	}

	// ------------------------------------------------------------------ Undang (R30)

	private void bukaUndang() {
		undang = true;
		nama.setText("");
		nama.setFocused(false);
		pemain.terpilih().clear();
	}

	private void tutupUndang() {
		undang = false;
		nama.setFocused(false);
	}

	/** The online players but this one, by name. */
	private List<PemilihOrang.Orang> pemainDaring() {
		List<PemilihOrang.Orang> out = new ArrayList<>();
		ClientPacketListener connection = minecraft.getConnection();
		String diri = minecraft.player == null ? "" : minecraft.player.getName().getString();
		if (connection == null) {
			return out;
		}
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			String n = info.getProfile().name();
			if (n != null && !n.isEmpty() && !n.equals(diri)) {
				out.add(PemilihOrang.Orang.of(n, Component.literal(n), Palette.QI));
			}
		}
		out.sort(Comparator.comparing(o -> o.id().toLowerCase(java.util.Locale.ROOT)));
		return out;
	}

	/** The pop-up card: the online players, a name field for anyone else, Undang and Batal. */
	private void undang(Lukis l, CompoundTag d) {
		l.g.fill(l.kx0, l.ky0, l.kx1, l.ky1, 0x90101418);
		l.zona("undang#luar", l.kx0, l.ky0, l.kx1, l.ky1, this::tutupUndang, true, null, false);
		int cw = Math.min(300, l.kx1 - l.kx0 - 16);
		int ch = Math.min(210, l.ky1 - l.ky0 - 12);
		int cx0 = (l.kx0 + l.kx1 - cw) / 2;
		int cy0 = (l.ky0 + l.ky1 - ch) / 2;
		int cx1 = cx0 + cw;
		int cy1 = cy0 + ch;
		l.g.fill(cx0 + 3, cy0 + 3, cx1 + 3, cy1 + 3, 0x60000000);
		l.g.fillGradient(cx0, cy0, cx1, cy1, Palette.PAPER_TOP, Palette.PAPER_BOTTOM);
		Brush.frame(l.g, cx0, cy0, cx1, cy1, Palette.GOLD);
		l.zona("undang#kartu", cx0, cy0, cx1, cy1, null, true, null, false);
		Palette.Gaya lama = l.gaya;
		l.gaya = Palette.Gaya.KERTAS;
		int cy = cy0 + 5 + l.kepala(Component.translatable(K + "undang"), cx0 + 6, cy0 + 5, cx1 - 26);
		TalismanButton.gambar(l, "undang:tutup", cx1 - 22, cy0 + 3, 16, 12, Component.literal("×"), true, null, this::tutupUndang);
		// the name field (anyone, also players out of sight) and the invite talisman
		l.samar(Component.translatable(K + "undang_label"), cx0 + 6, cy, cw - 12);
		cy += 10;
		List<PemilihOrang.Orang> semua = pemainDaring();
		String pilih = pemain.pilihan(semua).isEmpty() ? "" : pemain.pilihan(semua).get(0).id();
		String sasaran = !nama.text().isBlank() ? nama.text().strip() : pilih;
		int tombolW = Math.max(56, font.width(Component.translatable(M + "undang_kirim")) + 20);
		nama.x = cx0 + 6;
		nama.y = cy;
		nama.w = cw - 12 - tombolW - 4;
		nama.h = 14;
		l.widget("undang:nama", nama);
		TalismanButton.gambar(l, "undang:kirim", cx1 - 6 - tombolW, cy, tombolW, 14, Component.translatable(M + "undang_kirim"), !sasaran.isEmpty(),
				Component.translatable(M + "undang_kosong"), () -> kirimUndang(sasaran), true, false);
		cy += 18;
		l.samar(Component.translatable(M + "daring"), cx0 + 6, cy, cw - 12);
		cy += 10;
		pemain.gambar(l, "undang:daftar", semua, cx0 + 6, cy, cx1 - 6, cy1 - 6);
		l.gaya = lama;
	}

	private void undangNama(String text) {
		if (text != null && !text.isBlank()) {
			kirimUndang(text.strip());
		}
	}

	private void kirimUndang(String sasaran) {
		if (sasaran.isEmpty()) {
			return;
		}
		// the server only takes player names (LangitUi: [A-Za-z0-9_]{1,16}); say so instead of failing silently
		if (!sasaran.matches("[A-Za-z0-9_]{1,16}")) {
			pesan(Component.translatable(M + "undang_salah", sasaran), Palette.DANGER);
			return;
		}
		LangitUiClient.minta(LangitUiPayloads.Minta.UNDANG, sasaran);
		pesan(Component.translatable(M + "undang_terkirim", sasaran), Palette.JADE_DARK);
		tutupUndang();
	}

	// ================================================================== input

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
		return !undang && gulir.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		return !undang && gulir.tombol(event);
	}
}
