package dev.kultivator.client.hub;

import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.tempat.ui.UiTempatClient;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The Di Sini tablet's context (DESIGN 43.8.1-43.8.2): one live world context at a time, shown under tablet 9 of the
 * Gulungan Utama or, when the function has a home page in the tree (43.12.4), on that page with the Di Sini tablet leading
 * there.
 * <ol>
 * <li>A sheet or prompt that a click in the world (or a server prompt) brings becomes a {@link Konteks} and a page.</li>
 * <li>{@link #buka}: the Gulungan opens on the page (its home page, or Di Sini) with writing active.</li>
 * <li>A new context replaces the old one; a service opened from a talk with the same NPC keeps the talk one step back
 * ({@link #bisaKembali}: the header's back control, Esc).</li>
 * <li>The server ended the session ({@link #selesai}): the tablet goes with a footer line. The Gulungan closing ends the
 * context from the page side ({@link #lepas}, the page sends {@code OP_TUTUP}). Moving to another section does not.</li>
 * <li>While another screen is open (a container, chat) nothing opens itself: the context waits on the tablet and a toast
 * says so; the player opens the Gulungan when ready (no screen ever reopens itself, R11).</li>
 * </ol>
 * Client thread only.
 */
public final class DiSini {

	private static final String K = "kultivator.gui43.konteks.";
	/** The host's page id of the Di Sini tablet ({@code CultivationScreen.DI_SINI}); its sub memory picks the sheet's tab. */
	private static final String ID_DI_SINI = "di_sini";

	private static @Nullable Konteks aktif;
	private static @Nullable Page halaman;
	/** The home page of the live context (43.12.4), null = it lives under the Di Sini tablet; the tablet jumps there. */
	private static @Nullable Tujuan rumahAktif;
	/** The talk a service was opened from (same NPC): one step back. */
	private static @Nullable Konteks sebelumnya;
	/** The context {@link #kembali} asked the server for; when it arrives it gets no step back (no ping-pong). */
	private static @Nullable Konteks kembaliKe;

	/**
	 * Shows {@code page} as the live context {@code k}: on {@code rumah} (its home page, null = the Di Sini tablet), opened
	 * on sub {@code sub} ("" = the page's own choice).
	 */
	public static void buka(Konteks k, Page page, @Nullable Tujuan rumah, String sub) {
		if (k == null || page == null) {
			return;
		}
		Konteks lama = aktif;
		boolean balik = kembaliKe != null && kembaliKe.sama(k.layar(), k.npcId());
		kembaliKe = null;
		if (!balik && lama != null && lama.npcId() >= 0 && lama.npcId() == k.npcId() && !lama.layar().equals(k.layar())
				&& LembarKunci.Dialog.LAYAR.equals(lama.layar())) {
			sebelumnya = lama;
		} else if (lama == null || lama.npcId() != k.npcId() || balik) {
			sebelumnya = null;
		}
		aktif = k;
		halaman = page;
		rumahAktif = rumah;
		String s = sub == null ? "" : sub;
		HubNav.ingatSub(ID_DI_SINI, s);
		Minecraft minecraft = Minecraft.getInstance();
		Screen layar = minecraft.gui.screen();
		if (layar != null && !(layar instanceof CultivationScreen)) {
			// 43.8.1.5: busy (container, chat): the context waits on the tablet.
			CultivationScreen.konteks(page, k.judul(), k.glyph());
			dev.kultivator.client.hud.Kabar.kirim(dev.kultivator.client.hud.Kabar.kartu(Component.translatable(K + "menunggu"),
					Component.translatable(K + "menunggu.isi", k.judul())).kunci("di_sini.menunggu"));
			return;
		}
		HubNav.bukaKonteks(page, k.judul(), k.glyph());
		if (rumah != null) {
			HubNav.buka(s.isEmpty() ? rumah : rumah.sub(s));
		}
	}

	/**
	 * A prompt without NPC (43.8.2: tower riddle, seal warning, secret-realm reward ...): {@code page} under the Di Sini tablet
	 * titled {@code judul}. The page answers the server itself; {@link #tutupPrompt} ends it.
	 */
	public static void prompt(String id, Page page, Component judul, int glyph) {
		buka(new Konteks(id, -1, judul, glyph), page, null, "");
	}

	/** Ends the prompt {@code page} (answered or expired) without a footer line. */
	public static void tutupPrompt(Page page) {
		if (page != null && page == halaman) {
			selesai(null);
		}
	}

	/** The server ended the session (closed, out of reach, the NPC left): the tablet goes; {@code pesan} in the footer. */
	public static void selesai(@Nullable Component pesan) {
		if (aktif == null) {
			return;
		}
		Page p = halaman;
		aktif = null;
		halaman = null;
		rumahAktif = null;
		sebelumnya = null;
		HubNav.tutupKonteks(pesan);
		if (p instanceof LembarHub.Halaman rumah) {
			rumah.konteksBerakhir();
		}
	}

	/** The page {@code page} left as context from its own side (the Gulungan closed, a new context replaced it). */
	public static void lepas(Page page) {
		if (page != null && page == halaman) {
			aktif = null;
			halaman = null;
			rumahAktif = null;
			sebelumnya = null;
			// the host still shows its tablet (the page left on its own): no orphan tablet 9
			if (CultivationScreen.adaKonteks()) {
				HubNav.tutupKonteks(null);
			}
		}
	}

	/** True when {@code page} is the live context page and has a talk one step back. */
	public static boolean bisaKembali(Page page) {
		return page != null && page == halaman && sebelumnya != null;
	}

	/** Back to the talk the service was opened from: the server reopens it (it becomes the context again). */
	public static boolean kembali(Page page) {
		Konteks s = sebelumnya;
		if (!bisaKembali(page) || s == null) {
			return false;
		}
		sebelumnya = null;
		kembaliKe = s;
		return UiTempatClient.kirim(s.layar(), s.npcId(), LembarKunci.OP_LIHAT);
	}

	/** The page of the live context (a sheet page or a home page holding it), or null. */
	public static @Nullable Page halaman() {
		return halaman;
	}

	/** The home page of the live context, or null when it lives under the Di Sini tablet. */
	public static @Nullable Tujuan rumah() {
		return rumahAktif;
	}

	/**
	 * Every client tick ({@code UiTempatClient.init}): a sheet whose NPC is gone or out of reach ends here too, so a waiting
	 * tablet never fails on its first action (the server checks {@link TempatLayar#JANGKAU} only on an action). The helm
	 * uses the server's 48 blocks; module K checks its own reach ({@code KotaJembatan}).
	 */
	public static void jaga(Minecraft minecraft) {
		Konteks k = aktif;
		if (k == null || k.npcId() < 0 || minecraft.player == null || minecraft.level == null) {
			return;
		}
		// only the TempatLayar session itself (a dimension talk reuses the dialogue id under its own server)
		boolean lembar = TempatLayar.idSah(k.layar()) && UiTempatClient.sesiAktif(k.layar(), k.npcId());
		double jauh = lembar ? TempatLayar.JANGKAU + 2.0D : "kemudi".equals(k.layar()) ? 50.0D : -1.0D;
		if (jauh < 0.0D) {
			return;
		}
		Entity e = minecraft.level.getEntity(k.npcId());
		if (e == null || e.isRemoved() || !e.isAlive() || minecraft.player.distanceToSqr(e) > jauh * jauh) {
			if (lembar) {
				UiTempatClient.kirim(k.layar(), k.npcId(), LembarKunci.OP_TUTUP);
			}
			selesai(Component.translatable("kultivator.gui43.konteks.berakhir"));
		}
	}

	/** Forgets everything (disconnect). */
	public static void reset() {
		aktif = null;
		halaman = null;
		rumahAktif = null;

		sebelumnya = null;
		kembaliKe = null;
	}

	private DiSini() {
	}
}
