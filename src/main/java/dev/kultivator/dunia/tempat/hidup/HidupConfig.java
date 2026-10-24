package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.dunia.DuniaConfig;
import net.minecraft.util.Mth;

/**
 * Section {@code tempatHidup} of {@code config/kultivator-dunia.json} (DESIGN 38.N.8, table 38.D.9 row {@code tempat.hidup};
 * owner 38NL), clamped on load. The switches gate behaviour only: the payload, the commands and the data loader are
 * always registered (38.0 rule 17).
 */
public final class HidupConfig {

	/** NPC life of every 38 place: residents, schedules, bodies, far crowds. */
	public boolean aktif = true;
	/** All resident bodies at once, world-wide (38.N.7, 24-256). */
	public int tubuhGlobal = 160;
	/** Resident bodies within 96 blocks of one player (6-96). */
	public int perPemain = 56;
	/** Far crowds (L2 impostors, 48-128 blocks) are sent to clients. */
	public boolean impostor = true;
	/** Far crowd entries sent to one player at most (the client further limits by its EffectQuality, 0-48). */
	public int impostorMaks = 48;
	/** Seconds between two chats started by one body (gestured four-line talks, 38.N.4 butir 8). */
	public int obrolanDetik = 40;
	/** Market-stall bargaining between residents (a show; the player's economy never changes, 38.N.4 butir 3). */
	public boolean transaksiAntarNpc = true;
	/** Rain, storms, snow, Qi mist and sky events change the daily round (38.N.3). */
	public boolean reaksiCuaca = true;
	/** Per-resident schedule shift in world minutes (+-, 0-90; 40 = 38.N.3). */
	public int jadwalGeser = 40;

	/** Body caps per kind of place (38.N.7 "Tubuh maks"). */
	public int tubuhKotaBesar = 64;
	public int tubuhKota = 16;
	public int tubuhKampung = 18;
	public int tubuhKompleks = 20;
	public int tubuhPos = 6;
	public int tubuhTema = 44;

	HidupConfig clamp() {
		tubuhGlobal = Mth.clamp(tubuhGlobal, 24, 256);
		perPemain = Mth.clamp(perPemain, 6, 96);
		impostorMaks = Mth.clamp(impostorMaks, 0, 48);
		obrolanDetik = Mth.clamp(obrolanDetik, 10, 600);
		jadwalGeser = Mth.clamp(jadwalGeser, 0, 90);
		tubuhKotaBesar = Mth.clamp(tubuhKotaBesar, 12, 96);
		tubuhKota = Mth.clamp(tubuhKota, 0, 32);
		tubuhKampung = Mth.clamp(tubuhKampung, 0, 40);
		tubuhKompleks = Mth.clamp(tubuhKompleks, 0, 40);
		tubuhPos = Mth.clamp(tubuhPos, 0, 12);
		tubuhTema = Mth.clamp(tubuhTema, 12, 72);
		return this;
	}

	/** The body cap of a place kind ({@code kota/kota_besar}, {@code kampung/desa}, {@code kota_tema/air}...). */
	public int tubuhUntuk(String jenis) {
		if (jenis.equals("kota/kota_besar")) {
			return tubuhKotaBesar;
		}
		if (jenis.startsWith("kota_tema/")) {
			return tubuhTema;
		}
		if (jenis.startsWith("kota/")) {
			return tubuhKota;
		}
		if (jenis.startsWith("kampung/")) {
			return tubuhKampung;
		}
		if (jenis.startsWith("sekte/")) {
			return tubuhKompleks;
		}
		if (jenis.startsWith("pos/")) {
			return tubuhPos;
		}
		return 4;
	}

	private static volatile HidupConfig instance;

	/** The live section (refreshed in place by {@code uji jelajah muat}). */
	public static HidupConfig get() {
		HidupConfig c = instance;
		if (c == null) {
			c = DuniaConfig.section("tempatHidup", HidupConfig.class, HidupConfig::new, HidupConfig::clamp);
			instance = c;
		}
		return c;
	}
}
