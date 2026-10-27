package dev.kultivator.client.hud;

/**
 * The right-side event scroll slot of the HUD (DESIGN 43.9, G31): one place for the scrolls that follow something going
 * on around the player, and only one of them shows at a time, by priority: the sect entrance test ({@code HudUjian}) >
 * the Lei Tai ring ({@code LeiTaiHud}) > the stair trial ({@code TanggaHud}) > the capital's gate defence
 * ({@code KotaBesarHud}) > the guide tracker ({@code GuideTracker}); the others wait until it is over. The tribulation
 * stays on the left (a danger, never queued).
 * <p>
 * Each content stays its own HUD element: it registers here once ({@link #daftar}, from its constructor) and draws only
 * when {@link #giliran} says it is its turn, in the column {@link #kanan} / {@link #atas} (under the {@link Kabar} stack,
 * above the hotbar), on the slip {@link Kabar#slip}.
 */
public final class SlotSisi {

	public static final int UJIAN = 0;
	public static final int LEI_TAI = 1;
	public static final int TANGGA = 2;
	public static final int GERBANG = 3;
	public static final int PANDUAN = 4;

	/** A content of the slot: true while it has something to show. */
	@FunctionalInterface
	public interface Isi {
		boolean aktif();
	}

	private static final Isi[] ISI = new Isi[PANDUAN + 1];
	/** The Pengaturan › HUD switch of each content ({@code SettingsRows.HUD_SAKLAR}; the guide keeps its own coin). */
	private static final String[] SAKLAR = {"ujian", "lei_tai", "tangga", "gerbang", null};
	/** The height of the slip drawn last and when ({@link #tinggiAktif}: the Kabar stack stops its rows above it). */
	private static int tinggiTerakhir;
	private static long tinggiSejak;

	/** Registers the content of priority {@code prioritas} (0 is the highest). */
	public static void daftar(int prioritas, Isi isi) {
		ISI[prioritas] = isi;
	}

	/** True when the content of {@code prioritas} is active and no content above it is. */
	public static boolean giliran(int prioritas) {
		for (int i = 0; i < prioritas; i++) {
			if (aktif(i)) {
				return false;
			}
		}
		return aktif(prioritas);
	}

	/** A switched-off content gives up its turn. */
	private static boolean aktif(int i) {
		return ISI[i] != null && (SAKLAR[i] == null || dev.kultivator.client.screen.SettingsRows.hud(SAKLAR[i])) && ISI[i].aktif();
	}

	/** The right edge of the column. */
	public static int kanan(int guiWidth) {
		return guiWidth - 6;
	}

	/**
	 * The top of a slip {@code tinggi} px tall: under the notices (or a fifth down the screen), never so low that it runs
	 * into the hotbar row.
	 */
	public static int atas(int guiHeight, int tinggi) {
		tinggiTerakhir = tinggi;
		tinggiSejak = net.minecraft.util.Util.getMillis();
		int y = Math.max(Kabar.bawah() + 4, Math.round(guiHeight * 0.2F));
		return Math.max(6, Math.min(y, guiHeight - 44 - tinggi));
	}

	/** The height of the slip shown now (drawn in the last 250 ms), else 0. */
	public static int tinggiAktif() {
		return net.minecraft.util.Util.getMillis() - tinggiSejak <= 250L ? tinggiTerakhir : 0;
	}

	private SlotSisi() {
	}
}
