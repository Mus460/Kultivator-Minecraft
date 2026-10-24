package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.mandiri.Dorongan;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.Perencana;
import dev.kultivator.sekte.mandiri.PelaksanaDasar;
import dev.kultivator.sekte.mandiri.api.Dorong;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The visible small life of a loaded disciple (DESIGN 39.4.8, 39.8.4), one cheap step per body per second
 * ({@code DiscipleHooks.addTick20}):
 * <ul>
 * <li>a thought bubble when an activity starts (pill cauldron, sword, tea bowl, "Z" ...), each body at most one per
 * {@link #JEDA_PIKIR} ticks, and the client draws at most six within 32 blocks;</li>
 * <li>an idle thought from a strong drive (hunger for cultivation, tiredness, boredom, loneliness, a long shopping list);</li>
 * <li>the IBLIS whisper bubble and line for a tempted disciple (temptation 60+), about once in a minute and a half;</li>
 * <li>the voice lines of 39.8.4 the activity owners do not speak themselves: {@code salam_pagi} at the morning salute,
 * {@code makan}, {@code latih}, {@code iblis_bisik} (old lines as fallback until the clips exist);</li>
 * <li>the board mirror refresh, at most once a minute per body ({@code PapanMandiri.perbaruiRingkas}).</li>
 * </ul>
 * No world scan, no allocation per tick beyond the small state record per body (weak map).
 */
final class Pengamat {

	static final int JEDA_PIKIR = 1200;
	private static final int JEDA_RINGKAS = 1200;

	private static final class Keadaan {
		int act = -1;
		long pikir = -JEDA_PIKIR;
		long ringkas = -JEDA_RINGKAS;
		long salamHari = -1L;
	}

	private static final Map<DiscipleEntity, Keadaan> KEADAAN = new WeakHashMap<>();

	static void tick(DiscipleEntity body) {
		if (body.role() != DiscipleEntity.Role.DISCIPLE || body.ownerId() == null || !(body.level() instanceof ServerLevel level)
				|| !MandiriApi.aktif()) {
			return;
		}
		Keadaan k = KEADAAN.computeIfAbsent(body, b -> new Keadaan());
		long now = level.getGameTime();
		MinecraftServer server = level.getServer();
		if (now - k.ringkas >= JEDA_RINGKAS + Math.floorMod(body.getId(), 200)) {
			k.ringkas = now;
			PapanMandiri.perbaruiRingkas(server, body);
		}
		int act = body.activity();
		RandomSource random = body.getRandom();
		if (act != k.act) {
			int before = k.act;
			k.act = act;
			if (before >= 0) {
				mulai(body, act, now, k, random);
			}
		}
		if (Perencana.acara(body, PelaksanaDasar.ACARA_SALAM)) {
			long hari = now / 24000L;
			if (k.salamHari != hari) {
				k.salamHari = hari;
				if (random.nextInt(3) == 0) {
					SekteSuara.line(body, "salam_pagi", "greet_alt", 600);
				}
			}
		}
		if (MandiriApi.iblis(body.data()) >= 60 && random.nextInt(90) == 0 && now - k.pikir >= JEDA_PIKIR / 2) {
			k.pikir = now;
			MandiriPayloads.pikir(body, MandiriPayloads.PIKIR_IBLIS, "kultivator.sekte.mandiri.cerita.pikir.iblis", 80);
			SekteSuara.line(body, "iblis_bisik", "cultivate", 1800);
			return;
		}
		if (act == 0 && random.nextInt(240) == 0 && now - k.pikir >= JEDA_PIKIR) {
			int[] d = Dorongan.baca(body.data());
			int icon = -1;
			String caption = "";
			if (d[Dorong.LELAH.ordinal()] >= 75) {
				icon = MandiriPayloads.PIKIR_ZZZ;
				caption = "lelah";
			} else if (d[Dorong.KULTIVASI.ordinal()] >= 80) {
				icon = MandiriPayloads.PIKIR_QI;
				caption = "kultivasi";
			} else if (d[Dorong.BARANG.ordinal()] >= 70) {
				icon = MandiriPayloads.PIKIR_KOTA;
				caption = "barang";
			} else if (d[Dorong.SOSIAL.ordinal()] >= 75) {
				icon = MandiriPayloads.PIKIR_TEH;
				caption = "sosial";
			} else if (d[Dorong.BOSAN.ordinal()] >= 75) {
				icon = MandiriPayloads.PIKIR_BULAN;
				caption = "bosan";
			}
			if (icon >= 0) {
				k.pikir = now;
				MandiriPayloads.pikir(body, icon, "kultivator.sekte.mandiri.cerita.pikir." + caption, 100);
			}
		}
	}

	/** A new body activity began: maybe a bubble and a line. */
	private static void mulai(DiscipleEntity body, int act, long now, Keadaan k, RandomSource random) {
		int icon;
		int chance;
		switch (act) {
			case MandiriKegiatan.ACT_LATIH_JURUS, MandiriKegiatan.ACT_TANDING -> {
				icon = MandiriPayloads.PIKIR_PEDANG;
				chance = 25;
				if (act == MandiriKegiatan.ACT_LATIH_JURUS && random.nextInt(100) < 15) {
					SekteSuara.line(body, "latih", "cultivate", 1200);
				}
			}
			case MandiriKegiatan.ACT_ALKIMIA -> {
				icon = MandiriPayloads.PIKIR_KUALI;
				chance = 40;
			}
			case MandiriKegiatan.ACT_JIMAT -> {
				icon = MandiriPayloads.PIKIR_JIMAT;
				chance = 40;
			}
			case MandiriKegiatan.ACT_MENGAJAR, SekteIds.ACT_STUDY -> {
				icon = MandiriPayloads.PIKIR_GULUNGAN;
				chance = 30;
			}
			case MandiriKegiatan.ACT_TUTUP_DIRI -> {
				icon = MandiriPayloads.PIKIR_SEGEL;
				chance = 60;
			}
			case MandiriKegiatan.ACT_BERKEMAS, MandiriKegiatan.ACT_BAWA -> {
				icon = MandiriPayloads.PIKIR_BUNTALAN;
				chance = 70;
			}
			case MandiriKegiatan.ACT_MAKAN -> {
				icon = MandiriPayloads.PIKIR_MANGKUK;
				chance = 50;
				if (random.nextInt(100) < 20) {
					SekteSuara.line(body, "makan", "thanks", 1200);
				}
			}
			case MandiriKegiatan.ACT_ISTIRAHAT -> {
				icon = MandiriPayloads.PIKIR_ZZZ;
				chance = 40;
			}
			case MandiriKegiatan.ACT_MERENUNG -> {
				icon = body.level().isBrightOutside() ? MandiriPayloads.PIKIR_BINTANG : MandiriPayloads.PIKIR_BULAN;
				chance = 35;
			}
			case MandiriKegiatan.ACT_SERAP_BATU -> {
				icon = MandiriPayloads.PIKIR_BATU;
				chance = 50;
			}
			case MandiriKegiatan.ACT_JAGA_TEROBOSAN -> {
				icon = MandiriPayloads.PIKIR_HATI;
				chance = 50;
			}
			case MandiriKegiatan.ACT_MEDITASI_MANDIRI -> {
				icon = MandiriPayloads.PIKIR_QI;
				chance = 20;
			}
			case SekteIds.ACT_SOCIAL -> {
				icon = MandiriPayloads.PIKIR_TEH;
				chance = 30;
			}
			default -> {
				return;
			}
		}
		if (now - k.pikir < JEDA_PIKIR || random.nextInt(100) >= chance) {
			return;
		}
		k.pikir = now;
		MandiriPayloads.pikir(body, icon, "", 70);
	}

	static void bersih() {
		KEADAAN.clear();
	}

	private Pengamat() {
	}
}
