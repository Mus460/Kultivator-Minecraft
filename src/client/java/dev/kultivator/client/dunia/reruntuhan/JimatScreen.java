package dev.kultivator.client.dunia.reruntuhan;

import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.reruntuhan.RNet;
import dev.kultivator.dunia.reruntuhan.penjara.RajaIblisEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The warning of a seal talisman of the Kuil Segel (DESIGN 34.5.6, {@code op: "warn"}) as the Di Sini prompt "Jimat"
 * (DESIGN 43.8.2; was its own screen): on the dark wood paper the talisman itself painted down the left, the seal's
 * strength and the five talismans (torn ones grey), the demon king sealed below with its Sumber line, what a pull costs
 * and gives, and the temptation of Marshal Hong Xin, in one scroll. "Cabut Jimat" asks the kit's medium confirm seal (hold
 * 1.5 s, like the old seal) and only then sends {@code RNet.Action "cabut"}; "Biarkan", Esc or closing the Gulungan leaves
 * the talisman. A heartbeat beats while the warning shows, faster while the seal is held open. The server checks everything
 * again (distance, state); the page only asks.
 */
public class JimatScreen extends Page {

	private static final String M = "kultivator.gui43.m13.jimat.";
	private static final int RED = 0xFFD8261A;

	private final int n;
	private final int integrity;
	private final int pulled;
	private final int king;
	private final long openedAt = Util.getMillis();
	private long lastBeat;
	private boolean selesai;

	private record Row(FormattedCharSequence teks, int warna) {
	}

	private final List<Row> rows = new ArrayList<>();
	private final DaftarGulir daftar = new DaftarGulir();

	private JimatScreen(CompoundTag tag) {
		this.n = Mth.clamp(tag.getIntOr("n", 0), 0, 4);
		this.integrity = Mth.clamp(tag.getIntOr("integrity", 0), 0, 100);
		this.pulled = Mth.clamp(tag.getIntOr("pulled", 0), 0, 5);
		this.king = Mth.clamp(tag.getIntOr("king", 0), 0, 2);
	}

	/** A talisman warning arrived ({@code op: "warn"}): it shows under Di Sini (waiting on the tablet while another screen is open). */
	static void tampil(CompoundTag tag) {
		DiSini.prompt("jimat_segel", new JimatScreen(tag), judul(), Glyphs.SEAL);
	}

	private static Component judul() {
		return Component.translatable("kultivator.reruntuhan.jimat.ui.title");
	}

	@Override
	public Component title() {
		return judul();
	}

	@Override
	public int glyph() {
		return Glyphs.SEAL;
	}

	@Override
	public String helpTopic() {
		return "penjara_iblis";
	}

	@Override
	public Palette.Gaya gaya() {
		return Palette.Gaya.KAYU;
	}

	@Override
	public void onHide() {
		// Leaving the Gulungan leaves the talisman (the old Esc); the server sends a new warning on the next touch.
		selesai = true;
		DiSini.lepas(this);
	}

	@Override
	public void tick() {
		long now = Util.getMillis();
		long interval = modal() ? 600L : 1400L;
		if (now - lastBeat >= interval && now - openedAt > 150L) {
			lastBeat = now;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, modal() ? 1.1F : 0.8F, 0.5F));
		}
	}

	@Override
	protected void build() {
		// Immediate page: everything is drawn and hit-tested in lukis().
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		// the talisman painted down the left: yellow paper, red border, the seal script and 封
		int tw = 30;
		int tx0 = x + 4;
		int ty0 = y + 2;
		int ty1 = y + h - 2;
		float flutter = (float) Math.sin(Util.getMillis() / 420.0D) * 1.5F;
		g.fill(tx0, ty0, tx0 + tw, ty1, Palette.TALISMAN);
		g.fillGradient(tx0, ty0 + (ty1 - ty0) / 2, tx0 + tw, ty1, Palette.TALISMAN, Palette.TALISMAN_LOW);
		Brush.frame(g, tx0, ty0, tx0 + tw, ty1, Palette.SEAL);
		Brush.frame(g, tx0 + 2, ty0 + 2, tx0 + tw - 2, ty1 - 2, Palette.argb(0x90, Palette.SEAL));
		Brush.centered(g, font, Component.literal("封"), tx0 + tw / 2.0F, ty0 + 6.0F, Palette.SEAL, 1.5F, false);
		int marks = Math.max(2, Math.min(6, (ty1 - ty0 - 40) / 14));
		for (int i = 0; i < marks; i++) {
			float yy = ty0 + 28.0F + i * ((ty1 - ty0 - 40.0F) / marks);
			Brush.line(g, tx0 + tw / 2.0F - 5.0F + flutter * 0.2F, yy, tx0 + tw / 2.0F + 5.0F, yy + 4.0F, 1.4F, Palette.SEAL);
			Brush.line(g, tx0 + tw / 2.0F, yy - 2.0F, tx0 + tw / 2.0F, yy + 7.0F, 1.2F, Palette.SEAL);
		}
	}

	@Override
	protected void lukis(Lukis l) {
		int cx0 = x + 42;
		int room = x + w - 4 - cx0;
		l.teks(Brush.bold(Component.translatable("kultivator.reruntuhan.jimat.ui.title_n", n + 1)), cx0, y + 2, room, 0xFFFFD27A);
		l.g.fill(cx0, y + 13, x + w - 4, y + 14, Palette.argb(0xA0, 0x7A1A10));
		// the seal's strength and the five talismans
		Component strength = Component.translatable("kultivator.reruntuhan.jimat.ui.strength", integrity);
		int iconsW = 5 * 11;
		l.teks(strength, cx0, y + 17, room - iconsW - 6, l.gaya.teks);
		int bx1 = x + w - 4 - iconsW - 6;
		l.g.fill(cx0, y + 28, bx1, y + 32, 0xFF3A0A08);
		l.g.fill(cx0, y + 28, cx0 + (bx1 - cx0) * integrity / 100, y + 32, Palette.mix(RED, 0xFFE8C050, integrity / 100.0F));
		for (int i = 0; i < 5; i++) {
			int px = bx1 + 6 + i * 11;
			boolean torn = i < pulled;
			boolean mine = i == n;
			l.g.fill(px, y + 17, px + 7, y + 32, torn ? 0xFF6A6058 : mine ? 0xFFFFE07A : Palette.TALISMAN);
			l.g.fill(px + 3, y + 20, px + 4, y + 29, torn ? 0xFF3A3430 : Palette.SEAL);
			if (mine) {
				Brush.frame(l.g, px - 1, y + 16, px + 8, y + 33, 0xFFFFFFFF);
			}
		}
		if (l.di(bx1 + 4, y + 15, x + w - 4, y + 34)) {
			l.tip(Component.translatable(M + "jimat", pulled));
		}
		// the story and the price, in one scroll above the two talismans
		int buttonsY = y + h - 16;
		rows.clear();
		Component kingName = Component.translatable("entity.kultivator.raja_iblis." + RajaIblisEntity.IDS[king]).withStyle(ChatFormatting.RED);
		line(l, Component.translatable("kultivator.reruntuhan.jimat.ui.below", kingName), l.gaya.teks, room);
		line(l, Component.translatable("kultivator.reruntuhan.penjara." + RajaIblisEntity.IDS[king] + ".source").withStyle(ChatFormatting.ITALIC),
				l.gaya.teksLemah, room);
		line(l, Component.translatable("kultivator.reruntuhan.jimat.ui.cost"), l.gaya.teks, room);
		line(l, Component.translatable("kultivator.reruntuhan.jimat.ui.break"), l.gaya.teks, room);
		line(l, Component.translatable("kultivator.reruntuhan.jimat.ui.tempt").withStyle(ChatFormatting.ITALIC), 0xFFFF8A6A, room);
		daftar.gambar(l, "jimat", cx0, y + 37, x + w - 4, buttonsY - 4, rows.size(), 10,
				(lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> Brush.text(lk.g, lk.font, rows.get(i).teks(), rx0, ry0, rows.get(i).warna(), 1.0F, false),
				null, null);
		Component leave = Component.translatable("kultivator.reruntuhan.jimat.ui.leave");
		Component pull = Component.translatable(M + "cabut");
		int half = (room - 6) / 2;
		TalismanButton.gambar(l, "jimat.biarkan", cx0, buttonsY, half, 14, leave, !selesai, null, this::biarkan);
		TalismanButton.gambar(l, "jimat.cabut", cx0 + half + 6, buttonsY, room - half - 6, 14, pull, !selesai, null,
				() -> konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, Component.translatable(M + "yakin", n + 1, kingName), this::cabut), true, false);
	}

	private void line(Lukis l, Component text, int warna, int room) {
		for (FormattedCharSequence s : l.font.split(text, Math.max(20, room - 8))) {
			rows.add(new Row(s, warna));
		}
		rows.add(new Row(FormattedCharSequence.EMPTY, warna));
	}

	private void biarkan() {
		UiSound.close();
		akhiri();
	}

	private void cabut() {
		if (selesai) {
			return;
		}
		ClientPlayNetworking.send(new RNet.Action("cabut", n, 0));
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.0F));
		akhiri();
	}

	/** The warning is answered: the prompt goes and the Gulungan closes to the world when it showed it. */
	private void akhiri() {
		selesai = true;
		boolean diSini = Minecraft.getInstance().gui.screen() instanceof CultivationScreen hub && hub.section() == HubNav.DI_SINI
				&& DiSini.halaman() == this;
		DiSini.tutupPrompt(this);
		if (diSini) {
			HubNav.tutupSemua();
		}
	}
}
