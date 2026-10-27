package dev.kultivator.dunia.kota42.cakap;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.util.Advancements;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Facade of the conversation engine (DESIGN 42.5A; module 42K; no LLM, no network: deterministic algorithms over authored
 * data, every shown sentence assembled from lang keys of {@code kota_hidup_cakap*}). Other modules talk to 42K only through
 * this class, {@link GosipWarga#lahir} and {@link Penilaian#daftar} (contract C42.K1, frozen in 42-G0).
 * <p>
 * Every method is server-thread only and safe when the engine is switched off ({@code kotaBicara.mesinLanjut} false) or the
 * NPC is not a stored warga: then {@link #sapa} falls back to the 38 greeting, {@link #sesi} returns null, {@link #ujaran}
 * returns {@link Ujaran#KOSONG} and the effect methods do nothing.
 * <p>
 * Determinism (42.5A.0 butir 1): every choice draws from {@link #acak} seeded by {@link #benih}; never {@code Math.random},
 * wall time or {@code HashMap} iteration order.
 * <p>
 * Parts: {@link Kepribadian} (HEXACO), {@link Emosi} + {@link Penilaian} (PAD, OCC appraisal), {@link Pengetahuan} +
 * {@link Lupa} (facts, forgetting curve, history, promises, anti-repeat rings; SavedData {@code kultivator:kota_cakap}),
 * {@link GosipWarga} (rumours), {@link Topik} + {@link PerencanaTopik} (context, utility topics), {@link KeadaanPercakapan}
 * + {@link Kilasan} (state machine, callbacks), {@link PenilaiNada} (tones), {@link Giliran} (turns, realiser seam),
 * {@link ObrolanWarga} (NPC-NPC chats), {@link Lupa} (also the daily pass), {@link CakapUji} (test commands).
 */
public final class Cakap {

	/** The eight NPC-NPC chat kinds (42.5A.9); scripts in {@code percakapan/obrolan/<id>.json}. */
	public enum JenisObrolan {
		GOSIP, DEBAT, MERAYU, DAGANG, AJAR, CURHAT, CANDA, SAPA;

		/** Lower-case id = script file name and lang segment ({@code kultivator.cakap.obrolan.<id>.*}). */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The kind of an id, or null. */
		public static @Nullable JenisObrolan dari(String id) {
			for (JenisObrolan j : values()) {
				if (j.id().equals(id)) {
					return j;
				}
			}
			return null;
		}
	}

	/**
	 * What an utterance is about when another module asks for one ({@link #ujaran}).
	 *
	 * @param pendengar listening player (register, address form, anti-repeat ring), null for NPC-NPC or nobody
	 * @param lawan     the other townsperson in NPC-NPC talk, null otherwise
	 * @param topik     topic id of {@code percakapan/topik.json}, null to let the planner choose
	 * @param fakta     fact to talk about, null for none
	 * @param situs     site id of the place where it is said (dialect, place slots), 0 when unknown
	 * @param isian     extra typed slot values by slot name without {@code $} (values: {@link Component}, {@link Integer},
	 *                  {@link Long}, {@link String} lexicon word); insertion order is kept
	 */
	public record Konteks(@Nullable ServerPlayer pendengar, @Nullable Warga lawan, @Nullable String topik,
			@Nullable Fakta fakta, long situs, Map<String, Object> isian) {
		public Konteks {
			isian = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(isian));
		}

		public static Konteks untuk(@Nullable ServerPlayer pendengar, long situs) {
			return new Konteks(pendengar, null, null, null, situs, Map.of());
		}
	}

	private static boolean initialised;

	/** Registers the reload listener, hooks and commands of 42K (called once from {@code KotaHidup}). */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		CakapReload.register();
		ObrolanWarga.init();
		CakapUji.init();
		Lupa.init();
		PerangkaiTata.daftar();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PerangkaiTata.lupakan();
			KeadaanPercakapan.bersihkan();
			ObrolanWarga.bersihkan();
			Lupa.bersihkan();
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			MinecraftServer server = KotaHidup.server();
			if (server != null) {
				KeadaanPercakapan.tutup(server, player.getUUID());
			}
		});
		daftarBus();
	}

	/** True while the advanced engine runs ({@code kotaBicara.aktif} and {@code mesinLanjut}, residents on). */
	public static boolean aktif() {
		return KotaConfig.bicara().aktif && KotaConfig.bicara().mesinLanjut && WargaApi.aktif();
	}

	/** True when 42K speaks for this body (engine on and the body stands for a stored warga): {@code Kalimat.sapa} forwards. */
	public static boolean menangani(@Nullable KultivatorNpc npc) {
		return npc != null && aktif() && WargaApi.dari(npc) != null;
	}

	/**
	 * A one-line greeting for a speech bubble or the greeting sheet ({@code Kalimat.sapa} forwards here when the engine is
	 * on, 42.5A.11). Never null.
	 */
	public static Component sapa(ServerPlayer player, KultivatorNpc npc) {
		Warga w = aktif() ? WargaApi.dari(npc) : null;
		MinecraftServer server = KotaHidup.server();
		if (w == null || server == null) {
			return Component.translatable("kultivator.cerita.sapa.umum");
		}
		long situs = npc.penduduk() ? npc.situsPenduduk() : npc.town();
		int idx = WargaData.get(server).indeksPemain(player.getUUID());
		long now = server.overworld().getGameTime();
		SplittableRandom r = acak(server.overworld().getSeed(), w.id, idx, KotaHidup.hari(server), (int) (now / Emosi.JAM), "sapa");
		Topik.Situasi s = Topik.Situasi.bangun(server, w, player, null, situs, null);
		return Giliran.ujaran(new Giliran.Permintaan(Niat.SAPA, "sapa", s, null, Map.of(), player, null, r)).komponen();
	}

	/** Starts (or resumes) a conversation; null when the engine is off or the NPC is not a stored warga. */
	public static @Nullable SesiCakap sesi(ServerPlayer player, KultivatorNpc npc) {
		if (!aktif() || player == null || npc == null) {
			return null;
		}
		Warga w = WargaApi.dari(npc);
		if (w == null) {
			return null;
		}
		SesiCakap live = KeadaanPercakapan.aktif(player.getUUID());
		if (live != null && live.npcId() == npc.getId() && !live.selesai()) {
			return live;
		}
		MinecraftServer server = KotaHidup.server();
		if (server != null && live != null) {
			KeadaanPercakapan.tutup(server, player.getUUID());
		}
		return KeadaanPercakapan.buka(player, npc, w);
	}

	/**
	 * Plays one player choice: intent + tone (42.5A.7). Applies all effects and returns what to show next. With several
	 * options of the same intent (questions on different topics) the first is taken; prefer
	 * {@link #pilih(SesiCakap, SesiCakap.Opsi, Nada)}.
	 */
	public static Hasil pilih(SesiCakap sesi, Niat niat, Nada nada) {
		if (sesi == null || niat == null) {
			return Hasil.tutup();
		}
		String topik = null;
		for (SesiCakap.Opsi o : sesi.opsi) {
			if (o.niat().equals(niat)) {
				topik = o.topik();
				break;
			}
		}
		return KeadaanPercakapan.pilih(sesi, niat, topik, nada == null ? Nada.SOPAN : nada);
	}

	/** Plays the option the player picked (intent with its topic) in a tone. */
	public static Hasil pilih(SesiCakap sesi, SesiCakap.Opsi opsi, Nada nada) {
		if (sesi == null || opsi == null || opsi.terkunci()) {
			return Hasil.tutup();
		}
		return KeadaanPercakapan.pilih(sesi, opsi.niat(), opsi.topik(), nada == null ? Nada.SOPAN : nada);
	}

	/** Ends the player's live conversation (dialog closed, walked away). */
	public static void tutup(ServerPlayer player) {
		MinecraftServer server = KotaHidup.server();
		if (server != null && player != null) {
			KeadaanPercakapan.tutup(server, player.getUUID());
		}
	}

	/** One utterance of a warga for an intent in a context (bubbles, dialog inserts {@code "cakap": "<niat>"}). */
	public static Ujaran ujaran(Warga warga, Niat niat, Konteks konteks) {
		MinecraftServer server = KotaHidup.server();
		if (!aktif() || warga == null || niat == null || konteks == null || server == null) {
			return Ujaran.KOSONG;
		}
		WargaData data = WargaData.get(server);
		long situs = konteks.situs() != 0L ? konteks.situs() : data.situsDari(warga.id);
		ServerPlayer p = konteks.pendengar();
		int lawan = p != null ? data.indeksPemain(p.getUUID()) : konteks.lawan() != null ? konteks.lawan().id : 0;
		long now = server.overworld().getGameTime();
		SplittableRandom r = acak(server.overworld().getSeed(), warga.id, lawan, KotaHidup.hari(server), (int) (now / Emosi.JAM),
				"ujaran:" + niat.id());
		Topik.Situasi s = Topik.Situasi.bangun(server, warga, p, konteks.lawan(), situs,
				konteks.fakta() == null ? null : konteks.fakta().predikat());
		String topik = konteks.topik();
		Fakta fakta = konteks.fakta();
		Niat.Info info = niat.info();
		if (topik == null && info != null && info.topik()) {
			PerencanaTopik.Kandidat k = PerencanaTopik.pilih(PerencanaTopik.kandidat(s, Pengetahuan.get(server),
					new PerencanaTopik.Pendengar(null, null, konteks.lawan(), Map.of(), List.of(), List.of(), false)), s.sifat(), r);
			if (k != null) {
				topik = k.topik();
				fakta = fakta == null ? k.fakta() : fakta;
			}
		}
		return Giliran.ujaran(new Giliran.Permintaan(niat, topik, s, fakta, konteks.isian(), p, konteks.lawan(), r));
	}

	/**
	 * Reports an event to a warga's appraisal (42.5A.3): PAD push of {@code jenis} ({@code percakapan/emosi.json} or
	 * {@link Penilaian#daftar}) times {@code intensitas} (0-2, 1 = table value).
	 *
	 * @param sasaran who caused it / whom the emotion points at, null for undirected
	 */
	public static void peristiwa(Warga warga, String jenis, @Nullable Fakta.Rujuk sasaran, float intensitas) {
		MinecraftServer server = KotaHidup.server();
		if (warga == null || jenis == null || server == null || !KotaConfig.bicara().aktif || !WargaApi.aktif()) {
			return;
		}
		Emosi.Peristiwa ev = peristiwaData(jenis);
		if (ev == null) {
			return;
		}
		Pengetahuan pk = Pengetahuan.get(server);
		long now = server.overworld().getGameTime();
		Fakta.Rujuk target = sasaran == null ? Fakta.Rujuk.TIADA : sasaran;
		pk.dorong(warga, ev, Math.max(0.0F, Math.min(2.0F, intensitas)), target, now);
		if (ev.fakta() != null && target.jenis() != Fakta.Jenis.TIADA) {
			long hari = KotaHidup.hari(server);
			Predikat p = Predikat.dari(ev.fakta());
			pk.simpan(warga, new Fakta(ev.fakta(), target, Fakta.Rujuk.warga(warga.id), 0, Fakta.Kebenaran.BENAR, 100, Fakta.Asal.SAKSI, hari,
					hari, Lupa.kekuatanAwal(Fakta.Asal.SAKSI, p.valensi(), p, pk.sifat(warga).c()), 0, p.valensi(), -1), hari);
		}
	}

	/** The appraisal row of an event kind: data ({@code emosi.json}) first, then a code registration, else null. */
	public static @Nullable Emosi.Peristiwa peristiwaData(String jenis) {
		Emosi.Peristiwa ev = CakapReload.emosi().peristiwa().get(jenis);
		if (ev != null) {
			return ev;
		}
		Penilaian.Dorongan d = Penilaian.kode(jenis);
		return d == null ? null : new Emosi.Peristiwa(jenis, d.p(), d.a(), d.d(), null, true, Map.of(), null);
	}

	/** The facts a warga currently remembers (recall >= 0.1), most salient first. */
	public static List<Fakta> fakta(Warga warga) {
		MinecraftServer server = KotaHidup.server();
		if (warga == null || server == null) {
			return List.of();
		}
		long hari = KotaHidup.hari(server);
		List<Fakta> list = new ArrayList<>(Pengetahuan.get(server).ingat(warga, hari));
		list.sort((x, y) -> Double.compare(Lupa.menonjol(y, hari, 1.0D, diri(warga, y)), Lupa.menonjol(x, hari, 1.0D, diri(warga, x))));
		return List.copyOf(list);
	}

	private static boolean diri(Warga w, Fakta f) {
		return f.subjek().jenis() == Fakta.Jenis.WARGA && f.subjek().id() == w.id;
	}

	/** A warga learns a fact (confidence from the source, 42.5A.4); contradictions resolved by confidence. */
	public static void tahu(Warga warga, Fakta fakta, Fakta.Asal asal) {
		MinecraftServer server = KotaHidup.server();
		if (warga == null || fakta == null || asal == null || server == null || !WargaApi.aktif()) {
			return;
		}
		Pengetahuan pk = Pengetahuan.get(server);
		long hari = KotaHidup.hari(server);
		int trust = pk.kepercayaan(warga, asal);
		double hop = asal.sumber() == Fakta.Sumber.DENGAR ? 0.85D : 1.0D;
		int yakin = (int) Math.round(trust * fakta.yakin() / 100.0D * hop);
		Predikat p = Predikat.dari(fakta.predikat());
		float s = Lupa.kekuatanAwal(asal, fakta.valensi(), p, pk.sifat(warga).c());
		pk.simpan(warga, new Fakta(fakta.predikat(), fakta.subjek(), fakta.objek(), fakta.nilai(), fakta.kebenaran(), yakin, asal,
				fakta.hariKejadian(), hari, s, 0, fakta.valensi(), fakta.gosipId()), hari);
		if (fakta.gosipId() >= 0) {
			GosipWarga.kenal(warga, fakta.gosipId());
			WargaData.get(server).ubah();
		}
	}

	/**
	 * Records a promise of the player to a warga (42.5A.6; <= 3 open per warga and player).
	 *
	 * @param predikat  predicate of the promised deed ({@code percakapan/predikat.json}, e.g. {@code MEMBAWA_BARANG})
	 * @param batasHari world day by which it must be kept
	 */
	public static void janji(Warga warga, ServerPlayer player, String predikat, long batasHari) {
		janji(warga, player, predikat, batasHari, "");
	}

	/** {@link #janji} with the promised thing as a lexicon word ({@code obat}, {@code uang}...). */
	public static void janji(Warga warga, ServerPlayer player, String predikat, long batasHari, String apa) {
		MinecraftServer server = KotaHidup.server();
		if (warga == null || player == null || server == null || !WargaApi.aktif()) {
			return;
		}
		Pengetahuan pk = Pengetahuan.get(server);
		long hari = KotaHidup.hari(server);
		int idx = WargaData.get(server).indeksPemain(player.getUUID());
		String thing = apa == null ? "" : apa;
		pk.riwayat(warga, idx).janji(new Pengetahuan.Janji(predikat == null ? "MEMBAWA_BARANG" : predikat, thing, hari, batasHari));
		Predikat p = Predikat.dari("BERJANJI");
		pk.simpan(warga, new Fakta("BERJANJI", Fakta.Rujuk.pemain(idx), thing.isEmpty() ? Fakta.Rujuk.warga(warga.id) : Fakta.Rujuk.kata(thing),
				(int) batasHari, Fakta.Kebenaran.BENAR, 100, Fakta.Asal.SAKSI, hari, hari,
				Lupa.kekuatanAwal(Fakta.Asal.SAKSI, p.valensi(), p, pk.sifat(warga).c()) * 2.0F, 0, p.valensi(), -1), hari);
		pk.ubah();
	}

	/**
	 * The player kept a promise to a warga ({@code apa} as given to {@link #janji}, "" = the oldest open one): feelings,
	 * impression +5, and the next meeting thanks instead of reminding. Returns true when an open promise was found.
	 */
	public static boolean tepati(Warga warga, ServerPlayer player, String apa) {
		MinecraftServer server = KotaHidup.server();
		if (warga == null || player == null || server == null) {
			return false;
		}
		Pengetahuan pk = Pengetahuan.get(server);
		int idx = WargaData.get(server).indeksPemain(player.getUUID());
		for (Pengetahuan.Janji j : pk.riwayat(warga, idx).janji) {
			if (j.terbuka() && (apa == null || apa.isEmpty() || j.apa.equals(apa))) {
				j.status = Pengetahuan.StatusJanji.DITEPATI;
				peristiwa(warga, "janji_ditepati", Fakta.Rujuk.pemain(idx), 1.0F);
				WargaApi.ubahKesan(warga, player, 5, "janji_ditepati", null);
				Advancements.grant(player, "tempat/kota42_janji");
				pk.ubah();
				return true;
			}
		}
		return false;
	}

	/**
	 * Starts an NPC-NPC chat (42.5A.9) now; effects always apply, text only when a player is within
	 * {@code obrolanTampilJarak}.
	 *
	 * @param jenis chat kind, null to pick from relationship, knowledge and emotion
	 * @return true when a chat started
	 */
	public static boolean obrol(Warga a, Warga b, @Nullable JenisObrolan jenis) {
		MinecraftServer server = KotaHidup.server();
		if (a == null || b == null || server == null) {
			return false;
		}
		return ObrolanWarga.tanpaTubuh(server.overworld(), a, b, jenis) != null;
	}

	/** The personal price factor of a trading warga toward a player (1 +- the tone effects of 42.5A.7, kept 3 days). */
	public static double faktorHarga(Warga warga, ServerPlayer player) {
		MinecraftServer server = KotaHidup.server();
		if (warga == null || player == null || server == null) {
			return 1.0D;
		}
		int idx = WargaData.get(server).cariPemain(player.getUUID());
		Pengetahuan.Riwayat r = idx < 0 ? null : Pengetahuan.get(server).peekRiwayat(warga, idx);
		return r == null ? 1.0D : 1.0D + r.harga(KotaHidup.hari(server));
	}

	/** Facts the player overheard or was told (Buku Kota tab Kabar, 42.5A.9 "Menguping"), oldest first. */
	public static List<Fakta> catatan(ServerPlayer player) {
		MinecraftServer server = KotaHidup.server();
		if (player == null || server == null) {
			return List.of();
		}
		int idx = WargaData.get(server).cariPemain(player.getUUID());
		return idx < 0 ? List.of() : Pengetahuan.get(server).catatan(idx);
	}

	// ================================================================== world events -> feelings and gossip

	private static void daftarBus() {
		KotaBus.NIKAH.register((server, situs, pria, wanita) -> {
			peristiwa(pria, "pernikahan_di_tempat", Fakta.Rujuk.warga(wanita.id), 2.0F);
			peristiwa(wanita, "pernikahan_di_tempat", Fakta.Rujuk.warga(pria.id), 2.0F);
			GosipWarga.lahir(server.overworld(), situs, "NIKAH", Fakta.Rujuk.warga(pria.id), Fakta.Rujuk.warga(wanita.id));
		});
		KotaBus.LAHIR.register((server, situs, bayi, ibu) -> {
			if (ibu == null) {
				return;
			}
			peristiwa(ibu, "kelahiran", Fakta.Rujuk.warga(bayi.id), 1.0F);
			Warga ayah = ibu.pasangan == Warga.TANPA ? null : WargaApi.byId(ibu.pasangan);
			if (ayah != null) {
				peristiwa(ayah, "kelahiran", Fakta.Rujuk.warga(bayi.id), 1.0F);
			}
			GosipWarga.lahir(server.overworld(), situs, "LAHIR", Fakta.Rujuk.warga(ibu.id), Fakta.Rujuk.warga(bayi.id));
		});
		KotaBus.WAFAT.register((server, situs, warga, sebab) -> {
			WargaData.Situs s = WargaData.get(server).peek(situs);
			if (s != null) {
				for (Warga w : s.warga) {
					if (w != warga && w.hidup()) {
						boolean fam = Pengetahuan.keluarga(w, Fakta.Rujuk.warga(warga.id));
						if (fam || w.hubunganDengan(warga.id) != 0L) {
							peristiwa(w, fam ? "duka_keluarga" : "duka_kenalan", Fakta.Rujuk.warga(warga.id), 1.0F);
						}
					}
				}
			}
			GosipWarga.lahir(server.overworld(), situs, "WAFAT", 0, Fakta.Kebenaran.BENAR, List.of(sebab == null ? "" : sebab), null,
					Fakta.Rujuk.warga(warga.id));
			Pengetahuan.get(server).lupakan(warga.id);
		});
		KotaBus.PINDAH.register((server, warga, asal, tujuan) -> {
			if (asal != 0L) {
				GosipWarga.lahir(server.overworld(), asal, "PINDAH", Fakta.Rujuk.warga(warga.id), Fakta.Rujuk.tempat(tujuan));
			}
		});
		KotaBus.HARGA_BERUBAH.register((server, situs, kategori, lama, baru) -> {
			int persen = lama <= 0.0F ? 0 : Math.round((baru / lama - 1.0F) * 100.0F);
			if (Math.abs(persen) >= 5) {
				GosipWarga.lahir(server.overworld(), situs, persen > 0 ? "HARGA_NAIK" : "HARGA_TURUN", Math.abs(persen), Fakta.Kebenaran.BENAR,
						List.of(kategori.id()), null, Fakta.Rujuk.tempat(situs), Fakta.Rujuk.kata(kategori.id()));
			}
		});
		KotaBus.SERBUAN.register((server, situs, mulai, menang) -> {
			if (!mulai) {
				return;
			}
			WargaData.Situs s = WargaData.get(server).peek(situs);
			if (s != null) {
				for (Warga w : s.warga) {
					if (w.status == Warga.Status.HIDUP) {
						peristiwa(w, "serangan_dekat", null, 1.0F);
					}
				}
			}
			GosipWarga.lahir(server.overworld(), situs, "BAHAYA", Fakta.Rujuk.tempat(situs), Fakta.Rujuk.kata("serbuan"));
		});
		KotaBus.PAKTA_BERUBAH.register((server, situs, lama, baru) -> {
			if (baru != null) {
				GosipWarga.lahir(server.overworld(), situs, "PELINDUNG_BARU", Fakta.Rujuk.tempat(situs), Fakta.Rujuk.kata("pelindung"));
			}
		});
	}

	// ================================================================== determinism

	/**
	 * The seed of one decision (42.5A.0 butir 1): a stable mix of world seed, speaker, listener (player index or warga id),
	 * world day, turn number and a purpose salt. Same inputs, same seed, on every machine.
	 */
	public static long benih(long benihDunia, int wargaId, int lawan, long hari, int giliran, String garam) {
		long h = campur(benihDunia ^ 0x6A09E667F3BCC909L);
		h = campur(h ^ wargaId);
		h = campur(h ^ ((long) lawan << 32 | (giliran & 0xFFFFFFFFL)));
		h = campur(h ^ hari);
		return campur(h ^ (garam == null ? 0 : garam.hashCode()));
	}

	/** A fresh deterministic generator for {@link #benih}. */
	public static SplittableRandom acak(long benihDunia, int wargaId, int lawan, long hari, int giliran, String garam) {
		return new SplittableRandom(benih(benihDunia, wargaId, lawan, hari, giliran, garam));
	}

	/** 64-bit finaliser (MurmurHash3 fmix64 constants). */
	private static long campur(long z) {
		z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
		z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
		return z ^ (z >>> 33);
	}

	private Cakap() {
	}
}
