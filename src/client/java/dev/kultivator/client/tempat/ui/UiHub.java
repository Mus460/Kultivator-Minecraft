package dev.kultivator.client.tempat.ui;

import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubCards;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The hub cards of the 38 screens (DESIGN 38.U.1), registered with {@link HubCards#register} (no edit of
 * {@code HubCardsPage}): DUNIA "Kota Besar" (city map and directory, {@code peta_kota}) and "Catatan Perjalanan"
 * ({@code jurnal}); SEKTE, Aula sub-coin (ids without the {@code sekte_} prefix), "Buku Kas" ({@code buku_kas}) and
 * "Lapak &amp; Kafilah" (the same ledger opened on its stall tab), both carrying the hall lock. Each card asks the
 * server for the screen with {@link UiTempatClient#lihatDariHub}; the answer opens as a child of the section, read only.
 * Card texts: {@code kultivator.hub.card.<id>} and {@code .tip} in {@code lang-src/tempat_ui.*}.
 */
public final class UiHub {

	private static final long TAB_TUNGGU_MS = 3000L;
	private static @Nullable String tabLayar;
	private static @Nullable String tabId;
	private static long tabSampai;

	static void init() {
		HubCards.register(HubNav.DUNIA, "kota_besar", Glyphs.BUILD, null, () -> UiTempatClient.lihatDariHub(LembarKunci.PetaKota.LAYAR, HubNav.DUNIA));
		HubCards.register(HubNav.DUNIA, "catatan_perjalanan", Glyphs.CODEX, null,
				() -> UiTempatClient.lihatDariHub(LembarKunci.Jurnal.LAYAR, HubNav.DUNIA));
		HubCards.register(HubNav.SEKTE, "buku_kas", Glyphs.CODEX, UiHub::kunciAula,
				() -> UiTempatClient.lihatDariHub(LembarKunci.BukuKas.LAYAR, HubNav.SEKTE));
		HubCards.register(HubNav.SEKTE, "lapak_kafilah", Glyphs.STATUS, UiHub::kunciAula, () -> {
			mintaTab(LembarKunci.BukuKas.LAYAR, LembarKunci.BukuKas.TAB_LAPAK);
			UiTempatClient.lihatDariHub(LembarKunci.BukuKas.LAYAR, HubNav.SEKTE);
		});
	}

	/** The hall lock line when the hall info says there is no hall (unknown yet: open; the server answers a lock sheet). */
	private static @Nullable Component kunciAula() {
		if (HallClient.version() <= 0L) {
			return null;
		}
		return HallClient.info().getBooleanOr("has", false) ? null : Component.translatable("kultivator.hub.lock.hall");
	}

	/** A hub card wants screen {@code layar} to open on tab {@code tab} (used once, within 3 s). */
	public static void mintaTab(String layar, String tab) {
		tabLayar = layar;
		tabId = tab;
		tabSampai = Util.getMillis() + TAB_TUNGGU_MS;
	}

	/** The tab a hub card asked for screen {@code layar}, consumed; null when none. */
	public static @Nullable String ambilTab(String layar) {
		if (tabLayar == null || !tabLayar.equals(layar) || Util.getMillis() > tabSampai) {
			return null;
		}
		String t = tabId;
		tabLayar = null;
		tabId = null;
		return t;
	}

	private UiHub() {
	}
}
