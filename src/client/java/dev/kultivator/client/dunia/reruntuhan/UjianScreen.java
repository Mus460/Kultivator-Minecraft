package dev.kultivator.client.dunia.reruntuhan;

import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarPilihan;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.dunia.reruntuhan.RNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ujian Hati, the trial of the heart on the seventh floor of a band of the Menara Seratus Lantai (DESIGN 34.5.7,
 * {@code op: "riddle"}), as the Di Sini prompt "Teka-teki" (DESIGN 43.8.2; was its own screen): one Dao riddle, its
 * source, and the three answers in the order the server shuffled them as the kit's numbered choice list (click, ↑↓ +
 * Enter, or the keys 1-3 while under Di Sini, 43.17 K3). Two right answers pass, two wrong ones fail the floor; a wrong
 * answer costs the formation backlash. After an answer the page waits for the next question ({@code riddle}) or the end
 * ({@code riddle_end} / {@code scene_end}), which closes the prompt. Closing the Gulungan only hides it: the question waits
 * on the Di Sini tablet with a line in the action bar, and nothing reopens by itself (R11).
 */
public class UjianScreen extends Page {

	private static final String M = "kultivator.gui43.m13.ujian.";

	/** The live riddle page (null when no question waits). */
	private static @Nullable UjianScreen aktif;

	private final int question;
	private final int[] order;
	private final int number;
	private final int right;
	/** The answer was sent; waiting for the server's next step. */
	private boolean terkirim;
	/** Ended (the trial is over); hiding must not keep it on the tablet. */
	private boolean selesai;

	private UjianScreen(CompoundTag tag) {
		this.question = Mth.clamp(tag.getIntOr("q", 0), 0, 11);
		int[] opts = tag.getIntArray("opts").orElse(new int[]{0, 1, 2});
		this.order = opts.length == 3 ? opts : new int[]{0, 1, 2};
		this.number = Mth.clamp(tag.getIntOr("n", 1), 1, 3);
		this.right = Mth.clamp(tag.getIntOr("right", 0), 0, 3);
	}

	/** A question arrived ({@code op: "riddle"}): it replaces the one before and shows under Di Sini. */
	static void tampil(CompoundTag tag) {
		UjianScreen page = new UjianScreen(tag);
		aktif = page;
		DiSini.prompt("ujian_hati", page, judul(), Glyphs.HATI);
	}

	/** The trial ended ({@code riddle_end}, {@code scene_end}): the prompt goes; back to the world when it was on screen. */
	static void akhir() {
		UjianScreen page = aktif;
		aktif = null;
		if (page != null) {
			page.selesai = true;
			boolean diSini = Minecraft.getInstance().gui.screen() instanceof CultivationScreen hub && hub.section() == HubNav.DI_SINI
					&& DiSini.halaman() == page;
			DiSini.tutupPrompt(page);
			if (diSini) {
				HubNav.tutupSemua();
			}
		}
	}

	/** Forgets the question (disconnect). */
	static void reset() {
		aktif = null;
	}

	private static Component judul() {
		return Component.translatable("kultivator.reruntuhan.ujian.ui.title");
	}

	@Override
	public Component title() {
		return judul();
	}

	@Override
	public int glyph() {
		return Glyphs.HATI;
	}

	@Override
	public String helpTopic() {
		return "menara_seratus";
	}

	@Override
	public void onHide() {
		// The Gulungan closed with the question still open: it waits on the tablet (43.8.1.5), it never reopens itself.
		if (!selesai && aktif == this && !CultivationScreen.adaKonteks()) {
			CultivationScreen.konteks(this, judul(), Glyphs.HATI);
			// The same waiting card DiSini.buka sends (one key, one notice).
			dev.kultivator.client.hud.Kabar.kirim(dev.kultivator.client.hud.Kabar.kartu(
					Component.translatable("kultivator.gui43.konteks.menunggu"), Component.translatable(M + "menunggu")).kunci("di_sini.menunggu"));
		} else if (aktif != this) {
			DiSini.lepas(this);
		}
	}

	@Override
	protected void build() {
		// Immediate page: everything is drawn and hit-tested in lukis().
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		int left = x + 4;
		int room = w - 8;
		// "Pertanyaan n/3" and three beads: right answers so far
		l.teks(Brush.bold(Component.translatable(M + "nomor", number)), left, y + 2, room - 34, l.gaya.aksen);
		for (int i = 0; i < 3; i++) {
			Brush.disc(l.g, x + w - 8.0F - i * 9.0F, y + 6.0F, 3.0F, i < right ? Palette.JADE : Palette.argb(0x60, Palette.INK_FAINT));
		}
		if (l.di(x + w - 30, y, x + w, y + 12)) {
			l.tip(Component.translatable(M + "benar", right));
		}
		l.garis(left, x + w - 4, y + 13);
		List<DaftarPilihan.Pilihan> pilihan = new ArrayList<>(3);
		String key = "kultivator.reruntuhan.menara.ujian.q" + question;
		for (int i = 0; i < 3; i++) {
			pilihan.add(DaftarPilihan.Pilihan.of(i + 1, Component.translatable(key + ".a" + order[i])));
		}
		int blok = DaftarPilihan.tinggi(3, 1, 15);
		int pilihanTop = y + h - blok - (terkirim ? 11 : 0);
		int ty = y + 18;
		int src = 10;
		ty += Math.round(l.bungkus(Component.translatable(key), left, ty, room, l.gaya.teks, Math.max(9, pilihanTop - ty - src - 4))) + 2;
		l.teks(Component.translatable(key + ".src").withStyle(ChatFormatting.ITALIC), left, ty, room, l.gaya.teksSamar);
		DaftarPilihan.gambar(l, "ujian", pilihan, left, pilihanTop, x + w - 4, pilihanTop + blok, terkirim, p -> jawab(p.no() - 1));
		if (terkirim) {
			l.tengah(Component.translatable(M + "terkirim"), x + w / 2.0F, y + h - 9, room, l.gaya.teksLemah);
		}
	}

	private void jawab(int shown) {
		if (terkirim || selesai || shown < 0 || shown > 2) {
			return;
		}
		terkirim = true;
		UiSound.press();
		ClientPlayNetworking.send(new RNet.Action("jawab", shown, 0));
	}
}
