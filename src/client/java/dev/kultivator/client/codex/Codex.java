package dev.kultivator.client.codex;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.data.TechniqueSpec;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * The client codex contract (DESIGN 22.18.3, 43.5): the Teknik section of the Gulungan Utama (Kodeks, Cincin Segel,
 * Kehancuran) and feeding the Roda Segel with mouse movement and the mouse wheel ({@code MouseHandlerMixin}).
 */
public final class Codex {

	/** Registers the three Teknik pages (43.5); called once from {@code KehancuranClient.init}. */
	public static void daftarHalaman() {
		HubPages.daftar(HubNav.TEKNIK, Urutan.ID_KODEKS, Urutan.KODEKS, KodeksPage::new);
		HubPages.daftar(HubNav.TEKNIK, Urutan.ID_CINCIN_SEGEL, Urutan.CINCIN_SEGEL, LoadoutPage::new);
		HubPages.daftar(HubNav.TEKNIK, Urutan.ID_KEHANCURAN, Urutan.KEHANCURAN, KehancuranPage::new);
	}

	/** "Pasang ke Cincin" (G48): Teknik › Cincin Segel with {@code spec} carried, the next socket clicked takes it. */
	public static void pasang(@Nullable TechniqueSpec spec) {
		LoadoutPage.bawa(spec);
		HubNav.buka(Tujuan.of(HubNav.TEKNIK, Urutan.ID_CINCIN_SEGEL));
	}

	/** Mouse movement while the seal wheel is open; true when consumed (the camera does not turn). */
	public static boolean wheelInput(double dx, double dy) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!SealWheel.isOpen() || minecraft.gui.screen() != null) {
			return false;
		}
		return SealWheel.input(dx, dy);
	}

	/** The mouse wheel while the seal wheel is open; true when consumed (the hotbar slot does not change). */
	public static boolean wheelScroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!SealWheel.isOpen() || minecraft.gui.screen() != null) {
			return false;
		}
		return SealWheel.scroll(amount);
	}

	private Codex() {
	}
}
