package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaHidupCommands;
import dev.kultivator.dunia.kota42.KotaUji;
import dev.kultivator.dunia.kota42.ekonomi.KontrakDagang;
import dev.kultivator.dunia.kota42.warga.HariWarga;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime init of 42F Tumbuh & Bangun Kembali (DESIGN 42.9.4-42.9.5), called once from {@code KotaHidup.init} (registries
 * are not here: {@link KotaTumbuhEntities} is called from {@code KotaHidup.registerContent}). Wires:
 * <ul>
 *   <li>the second of {@link PekerjaanTumbuh} (plots) and {@link BangunKembali} (projects, tents);</li>
 *   <li>a Hari Warga step after the place statistics: {@link Kemakmuran#harian} and the material production of
 *       {@link BahanKota};</li>
 *   <li>{@code TempatHuni.addSumberTitik}: titik of finished plots and refugee tent beds; {@code addPenolakTitik}: beds of
 *       destroyed houses while their project runs; {@code Suasana.addPengaruhTempat}: -20 during a project;</li>
 *   <li>{@code PerbaikanApi.setPengali}: growth speed ({@code kotaTumbuh.kecepatan}) and rebuild multipliers;</li>
 *   <li>{@code KontrakDagang.daftarPesanan}: plot material as city orders;</li>
 *   <li>events: real damage clusters ({@code TempatEvents.TEMPAT_RUSAK}), crimes, raids ({@code KotaBus.SERBUAN});</li>
 *   <li>commands: {@code uji kota_hidup tumbuh ...}, {@code uji kota_hidup kompat periksa}, {@code /kultivasi kotaku kavling}
 *       ({@link KotaTumbuhPerintah}).</li>
 * </ul>
 * Behaviour checks {@code kotaTumbuh} at run time ({@code KotaConfig}), so a reload can switch it off and on.
 */
public final class KotaTumbuh {

	/** Mood of a place while its rebuild project runs (42.9.5). */
	public static final int SUASANA_PROYEK = -20;

	private static boolean initialised;

	/** From {@code KotaHidup.init}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		RencanaTumbuh.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 11 && KotaConfig.tumbuh().aktif) {
				PekerjaanTumbuh.tick(server);
				BangunKembali.tick(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PekerjaanTumbuh.lupakan();
			BangunKembali.lupakan();
		});
		HariWarga.addLangkah(HariWarga.URUT_STAT + 10, "tumbuh", (server, data, s, hari, random) -> {
			if (KotaConfig.tumbuh().aktif) {
				Kemakmuran.harian(server, s, hari);
				BahanKota.produksi(server, s, PekerjaanTumbuh.sasaran(server, s.id));
			}
		});
		TempatHuni.addSumberTitik(KotaTumbuh::titik);
		TempatHuni.addPenolakTitik((huni, pos) -> {
			MinecraftServer server = KotaHidup.server();
			return server != null && KotaConfig.tumbuh().aktif && BangunKembali.rumahRusak(server, huni, pos);
		});
		Suasana.addPengaruhTempat(situs -> KotaConfig.tumbuh().aktif && BangunKembali.aktif(situs) ? SUASANA_PROYEK : 0);
		PerbaikanApi.setPengali((situs, tumbuh) -> {
			MinecraftServer server = KotaHidup.server();
			if (server == null || !KotaConfig.tumbuh().aktif) {
				return 1.0D;
			}
			return tumbuh ? KotaConfig.tumbuh().kecepatan : BangunKembali.pengali(server, situs);
		});
		KontrakDagang.daftarPesanan(new KontrakDagang.PenyediaPesanan() {
			@Override
			public List<KontrakDagang.Pesanan> pesanan(MinecraftServer server, long situs) {
				return KotaConfig.tumbuh().aktif ? BahanKota.pesanan(server, situs) : List.of();
			}

			@Override
			public void terima(MinecraftServer server, long situs, String id, int n) {
				BahanKota.terimaPesanan(server, situs, id, n);
			}
		});
		TempatEvents.TEMPAT_RUSAK.register((level, situs, gugus, blok, pelaku) -> BangunKembali.rusak(level, situs, gugus, blok));
		TempatEvents.KEJAHATAN.register((player, town, jenis) -> {
			if (KotaConfig.tumbuh().aktif) {
				Kemakmuran.kejahatan(player.level().getServer(), town);
			}
		});
		KotaBus.SERBUAN.register((server, situs, mulai, menang) -> {
			if (mulai && KotaConfig.tumbuh().aktif) {
				Kemakmuran.serbuan(server, situs);
			}
		});
		KotaUji.daftar("tumbuh", KotaTumbuhPerintah::uji);
		KotaUji.daftar("kompat", KotaTumbuhPerintah::kompat);
		KotaHidupCommands.daftar("kavling", KotaTumbuhPerintah::kotaku);
		KotaHidupCommands.daftar("bangun", KotaTumbuhPerintah::kotakuBangun);
		KotaUji.chips("kota_hidup tumbuh info", "kota_hidup tumbuh kavling mulai <n>", "kota_hidup tumbuh kavling pilih <n> <rencana>");
	}

	/** The extra titik of a place: finished plot buildings and refugee tent beds. */
	private static List<Titik> titik(TempatHuni huni) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || !KotaConfig.tumbuh().aktif) {
			return List.of();
		}
		List<Titik> done = PekerjaanTumbuh.titikSelesai(server, huni.situsId);
		List<Titik> tents = BangunKembali.titikTenda(server, huni);
		if (tents.isEmpty()) {
			return done;
		}
		List<Titik> out = new ArrayList<>(done);
		out.addAll(tents);
		return out;
	}

	private KotaTumbuh() {
	}
}
