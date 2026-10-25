package dev.kultivator.dunia.kota42.cakap;

import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The forgetting curve (DESIGN 42.5A.4; Ebbinghaus 1885): recall chance {@code R = e^(-dt / S)}, {@code dt} in world hours
 * since the fact was last recalled ({@link Fakta#hariTahu}), {@code S} the memory strength in world hours. A heard fact
 * starts at 24 h; seeing it oneself x1.5, a strong feeling (|valence| >= 2) x2 (flashbulb), every retelling x1.6
 * (spacing), the predicate's own {@code kuat}, conscientiousness x(1 + C/200) and the config scale {@code lupaSkala}.
 * Facts with R &lt; 0.1 are dropped by the daily pass, except family matters and strong memories.
 * <p>
 * Also the salience used by the topic planner: {@code sal = R x (1 + |valence|) x novelty x relevance x self} with
 * {@code novelty = 1 / (1 + ageDays / 3)}.
 * <p>
 * Also the daily pass of 42K ({@link #init}): at each world day change every place is queued and one place per tick runs
 * its gossip cooling and off-screen spreading ({@link GosipWarga#harian}), the forgetting of every resident
 * ({@link Pengetahuan#harian}) and the broken promises (three days past the deadline: {@code janji_diingkari}, impression
 * -5, a gossip {@code INGKAR}).
 */
public final class Lupa {

	/** Facts below this recall are forgotten by the daily pass. */
	public static final double AMBANG = 0.1D;
	/** Strength multiplier of a retelling. */
	public static final float ULANG = 1.6F;

	/** Recall chance of {@code f} on world day {@code hari}. */
	public static double ingat(Fakta f, long hari) {
		double dt = Math.max(0L, hari - f.hariTahu()) * 24.0D;
		return Math.exp(-dt / Math.max(1.0F, f.kekuatan()));
	}

	/** The starting strength S (world hours) of a fact a holder with conscientiousness {@code c} learns from {@code asal}. */
	public static float kekuatanAwal(Fakta.Asal asal, int valensi, Predikat p, int c) {
		float s = Fakta.KEKUATAN_DENGAR;
		if (asal.sumber() == Fakta.Sumber.SAKSI || asal.sumber() == Fakta.Sumber.DIRI) {
			s *= 1.5F;
		}
		if (Math.abs(valensi) >= 2) {
			s *= 2.0F;
		}
		s *= Math.max(0.1F, p.kuat());
		s *= 1.0F + c / 200.0F;
		return (float) (s * KotaConfig.bicara().lupaSkala);
	}

	/** The fact after its holder told it on {@code hari}: S x1.6, one more telling, recalled today. */
	public static Fakta diceritakan(Fakta f, long hari) {
		return new Fakta(f.predikat(), f.subjek(), f.objek(), f.nilai(), f.kebenaran(), f.yakin(), f.asal(), f.hariKejadian(), hari,
				f.kekuatan() * ULANG, f.kaliDiceritakan() + 1, f.valensi(), f.gosipId());
	}

	/** The fact recalled (thought about) on {@code hari} without strengthening: the curve restarts. */
	public static Fakta diingat(Fakta f, long hari) {
		return new Fakta(f.predikat(), f.subjek(), f.objek(), f.nilai(), f.kebenaran(), f.yakin(), f.asal(), f.hariKejadian(), hari,
				f.kekuatan(), f.kaliDiceritakan(), f.valensi(), f.gosipId());
	}

	/**
	 * Salience for topic choice (42.5A.4).
	 *
	 * @param relevansi 2 when the listener knows the subject (it is the listener, family, friend), 1.5 a neighbour, 1 else
	 * @param diri      true when the fact is about the speaker
	 */
	public static double menonjol(Fakta f, long hari, double relevansi, boolean diri) {
		double novelty = 1.0D / (1.0D + Math.max(0L, hari - f.hariKejadian()) / 3.0D);
		return ingat(f, hari) * (1.0D + Math.abs(f.valensi())) * novelty * relevansi * (diri ? 2.0D : 1.0D);
	}

	/** Days past a promise's deadline before it counts as broken. */
	public static final int INGKAR_HARI = 3;

	private static final ArrayDeque<Long> ANTRE = new ArrayDeque<>();
	private static long hariTerakhir = Long.MIN_VALUE / 2;

	/** Registers the daily pass (from {@code Cakap.init}). */
	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Lupa::tik);
	}

	static void bersihkan() {
		ANTRE.clear();
		hariTerakhir = Long.MIN_VALUE / 2;
	}

	private static void tik(MinecraftServer server) {
		if (server.getTickCount() % 20 == 0 && KotaHidup.server() == server) {
			long hari = KotaHidup.hari(server);
			if (hari != hariTerakhir) {
				hariTerakhir = hari;
				List<Long> ids = new ArrayList<>();
				for (WargaData.Situs s : WargaData.get(server).semuaSitus()) {
					ids.add(s.id);
				}
				ids.sort(null);
				ANTRE.clear();
				ANTRE.addAll(ids);
			}
		}
		Long situs = ANTRE.poll();
		if (situs != null && KotaConfig.bicara().aktif && dev.kultivator.dunia.kota42.api.WargaApi.aktif()) {
			harian(server.overworld(), situs);
		}
	}

	/** The daily pass of one place (also {@code uji kota_hidup gosip sebar}). */
	public static void harian(ServerLevel level, long situs) {
		MinecraftServer server = level.getServer();
		long hari = KotaHidup.hari(server);
		long now = server.overworld().getGameTime();
		GosipWarga.harian(level, situs, hari);
		WargaData data = WargaData.get(server);
		WargaData.Situs s = data.peek(situs);
		if (s == null) {
			return;
		}
		Pengetahuan pk = Pengetahuan.get(server);
		for (Warga w : new ArrayList<>(s.warga)) {
			pk.harian(w, hari, now);
			Pengetahuan.Entri e = pk.peek(w.id);
			if (e == null) {
				continue;
			}
			for (Pengetahuan.Riwayat r : e.riwayat.values()) {
				for (Pengetahuan.Janji j : r.janji) {
					if (j.terbuka() && hari > j.batas + INGKAR_HARI) {
						j.status = Pengetahuan.StatusJanji.INGKAR;
						Cakap.peristiwa(w, "janji_diingkari", Fakta.Rujuk.pemain(r.pemain), 1.0F);
						UUID uuid = data.pemain(r.pemain);
						if (uuid != null) {
							Ingatan i = Ingatan.untuk(data, w, uuid);
							i.setKesan(i.kesan - 5);
						}
						GosipWarga.lahir(level, situs, "INGKAR", Fakta.Rujuk.pemain(r.pemain), Fakta.Rujuk.warga(w.id));
						pk.ubah();
					}
				}
			}
		}
	}

	private Lupa() {
	}
}
