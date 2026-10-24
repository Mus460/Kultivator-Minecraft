package dev.kultivator.dunia.tempat.tema.bersama;

import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.cerita.Syarat;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.KotaTemaData;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The story words of the themed towns for the 38ST engine (DESIGN 38.T.11 rows Rumor/Advancement, 38.T.14.3 row 38ST):
 * conditions, effects and goal kinds that quest and dialog JSON of every theme may use. Registered once by
 * {@code KotaTema.init()}; theme agents add only their own words (never these ids).
 * <pre>
 * syarat  infami &lt;min&gt; [maks]        the player's Infami is in range (0-100)
 *         gentar &lt;min&gt;               Gentar in the Kota Iblis the player stands in (else the nearest within 600)
 *         pita_dagang &lt;min&gt;          realm band (Harga band 0-8) at least min (shop rows "pita &gt;= n")
 *         kota_tema &lt;tema|*&gt;         the player stands inside a themed town of that theme
 *         kota_tema_dikenal &lt;n&gt;      the player has entered at least n different themes (1-5)
 *         acara &lt;tema&gt; &lt;id&gt;          that event runs now in the town the player stands in
 * akibat  infami &lt;delta&gt; [alasan]    TemaApi.tambahInfami
 *         gentar &lt;delta&gt;             Gentar in the Kota Iblis the player stands in (or nearest within 600)
 *         acara &lt;tema&gt; &lt;id&gt; [akhir]  forces the event to start (or end) in the town the player stands in
 * tujuan  naik_lift [gantung|katrol]  kapal_lintas            lentera_dilepas [situs]   ziarah_selesai
 *         tebus [kampung]             kontrak_muatan [tujuan]  masuk_kota_tema &lt;tema|*&gt;
 *         dagang_laba &lt;nb&gt;            (id of the event = the player's total trade profit so far, in NB)
 * </pre>
 * Theme systems report goals with {@code CeritaApi.peristiwa(player, KIND, id)} using the constants below.
 */
public final class TemaCerita {

	public static final String NAIK_LIFT = "naik_lift";
	public static final String ZIARAH_SELESAI = "ziarah_selesai";
	public static final String LENTERA_DILEPAS = "lentera_dilepas";
	public static final String KAPAL_LINTAS = "kapal_lintas";
	public static final String TEBUS = "tebus";
	public static final String DAGANG_LABA = "dagang_laba";
	public static final String KONTRAK_MUATAN = "kontrak_muatan";
	public static final String MASUK_KOTA_TEMA = "masuk_kota_tema";

	/** All goal kinds of this class. */
	public static final List<String> TUJUAN = List.of(NAIK_LIFT, ZIARAH_SELESAI, LENTERA_DILEPAS, KAPAL_LINTAS, TEBUS, DAGANG_LABA,
			KONTRAK_MUATAN, MASUK_KOTA_TEMA);

	/** Radius in which {@code gentar} looks for a Kota Iblis when the player is not inside one. */
	private static final int JARAK_GENTAR = 600;

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// ------------------------------------------------------------------ conditions
		Syarat.daftar("infami", (player, args) -> {
			int v = TemaApi.infami(player);
			return v >= angka(args, 0, 0) && v <= angka(args, 1, 100);
		});
		Syarat.daftar("gentar", (player, args) -> {
			TemaKota.Kota kota = kotaIblis(player);
			return kota != null && TemaApi.gentar(player, kota.situsId()) >= angka(args, 0, 1);
		});
		Syarat.daftar("pita_dagang", (player, args) -> Barang.pita(player) >= angka(args, 0, 0));
		Syarat.daftar("kota_tema", (player, args) -> {
			TemaKota.Kota kota = TemaKota.di(player.level(), player.blockPosition());
			if (kota == null) {
				return false;
			}
			String want = args.isEmpty() ? "*" : args.getFirst();
			return "*".equals(want) || kota.tema() == Tema.dari(want);
		});
		Syarat.daftar("kota_tema_dikenal", (player, args) -> Integer.bitCount(kunjungan(player)) >= angka(args, 0, 1));
		Syarat.daftar("acara", (player, args) -> {
			if (args.size() < 2) {
				return false;
			}
			TemaKota.Kota kota = TemaKota.di(player.level(), player.blockPosition());
			return kota != null && TemaKalender.berjalan(kota.situsId(), args.get(0), args.get(1));
		});
		// ------------------------------------------------------------------ effects
		Akibat.daftar("infami", (player, args) -> TemaApi.tambahInfami(player, angka(args, 0, 0), args.size() > 1 ? args.get(1) : "cerita"));
		Akibat.daftar("gentar", (player, args) -> {
			TemaKota.Kota kota = kotaIblis(player);
			if (kota != null) {
				KotaTemaData data = KotaTemaData.get(player.level().getServer());
				data.setGentar(player.getUUID(), kota.situsId(), data.gentar(player.getUUID(), kota.situsId()) + angka(args, 0, 0));
			}
		});
		Akibat.daftar("acara", (player, args) -> {
			if (args.size() < 2) {
				return;
			}
			TemaKota.Kota kota = TemaKota.di(player.level(), player.blockPosition());
			if (kota == null) {
				kota = TemaKota.terdekat(player.level(), player.blockPosition(), Tema.dari(args.get(0)), 400);
			}
			if (kota != null) {
				boolean akhir = args.size() > 2 && "akhir".equals(args.get(2));
				TemaKalender.paksa(player.level(), kota.situsId(), args.get(0), args.get(1), !akhir);
			}
		});
		// ------------------------------------------------------------------ goal kinds
		for (String kind : TUJUAN) {
			if (DAGANG_LABA.equals(kind)) {
				QuestTracker.daftarTujuan(kind, (args, jenis, id) -> jenis.equals(DAGANG_LABA) && parse(id, 0) >= angka(args, 0, 1));
			} else {
				QuestTracker.daftarTujuan(kind, (args, jenis, id) -> jenis.equals(kind) && (args.isEmpty() || "*".equals(args.getFirst())
						|| id.equals(args.getFirst()) || id.startsWith(args.getFirst() + ":")));
			}
		}
	}

	/** Captives ransomed for advancement {@code tempat/tema_pembebas}. */
	public static final int PEMBEBAS = 10;

	/**
	 * Reports a themed goal event: {@code CeritaApi.peristiwa(player, kind, id)} plus the guide advancement of that kind
	 * ({@code kapal_lintas} tema_lintas_laut, {@code ziarah_selesai} tema_ziarah, {@code lentera_dilepas}
	 * tema_lentera_air, {@code tebus} tema_tebus and, at the {@value #PEMBEBAS}th captive, tema_pembebas). Theme systems
	 * call this instead of {@code CeritaApi.peristiwa} for the kinds of this class.
	 */
	public static void lapor(ServerPlayer player, String kind, String id) {
		if (player == null || kind == null) {
			return;
		}
		dev.kultivator.dunia.tempat.cerita.CeritaApi.peristiwa(player, kind, id == null ? "" : id);
		switch (kind) {
			case KAPAL_LINTAS -> Advancements.grant(player, "tempat/tema_lintas_laut");
			case ZIARAH_SELESAI -> Advancements.grant(player, "tempat/tema_ziarah");
			case LENTERA_DILEPAS -> Advancements.grant(player, "tempat/tema_lentera_air");
			case TEBUS -> {
				Advancements.grant(player, "tempat/tema_tebus");
				KotaTemaData data = KotaTemaData.get(player.level().getServer());
				CompoundTag count = data.inti().getCompoundOrEmpty("tebus");
				int n = count.getIntOr(player.getUUID().toString(), 0) + 1;
				count.putInt(player.getUUID().toString(), n);
				data.inti().put("tebus", count);
				data.ubah();
				if (n >= PEMBEBAS) {
					Advancements.grant(player, "tempat/tema_pembebas");
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Records that {@code player} entered a town of {@code tema}. True exactly once: when the fifth different theme was
	 * just entered (advancement {@code tempat/tema_lima_kota}).
	 */
	public static boolean catatKunjungan(KotaTemaData data, ServerPlayer player, Tema tema) {
		CompoundTag inti = data.inti();
		CompoundTag visits = inti.getCompoundOrEmpty("kunjungan");
		String key = player.getUUID().toString();
		int before = visits.getIntOr(key, 0);
		int after = before | (1 << tema.ordinal());
		if (after == before) {
			return false;
		}
		visits.putInt(key, after);
		inti.put("kunjungan", visits);
		data.ubah();
		int all = (1 << Tema.values().length) - 1;
		return after == all;
	}

	/** Bit mask of the themes the player has entered (bit = {@code Tema.ordinal()}). */
	public static int kunjungan(ServerPlayer player) {
		return KotaTemaData.get(player.level().getServer()).inti().getCompoundOrEmpty("kunjungan").getIntOr(player.getUUID().toString(), 0);
	}

	/** The Kota Iblis the player stands in, else the nearest born one within {@value #JARAK_GENTAR} blocks, or null. */
	public static @Nullable TemaKota.Kota kotaIblis(ServerPlayer player) {
		ServerLevel level = player.level();
		TemaKota.Kota kota = TemaKota.di(level, player.blockPosition());
		if (kota != null && kota.tema() == Tema.IBLIS) {
			return kota;
		}
		return TemaKota.terdekat(level, player.blockPosition(), Tema.IBLIS, JARAK_GENTAR);
	}

	private static int angka(List<String> args, int index, int fallback) {
		return index < args.size() ? (int) parse(args.get(index), fallback) : fallback;
	}

	private static long parse(String text, long fallback) {
		try {
			return Long.parseLong(text.trim());
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private TemaCerita() {
	}
}
