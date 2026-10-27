package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.acara.PeristiwaJalan;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.bicara.PermintaanWarga;
import dev.kultivator.dunia.kota42.warga.Hubungan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.TubuhPenduduk;
import dev.kultivator.util.Advancements;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * NPC-NPC conversation (DESIGN 42.5A.9): eight chat kinds ({@link Cakap.JenisObrolan}) scripted as state machines in
 * {@code percakapan/obrolan/<jenis>.json} ({@link Naskah}). A chat is planned whole when it starts: the kind from bonds,
 * knowledge and feelings, the steps through the script's {@code lanjut} links (step conditions, then seeded weights), and
 * its effects applied at once (facts move with confidence, bonds and feelings change, a fight or a request may follow), so
 * an unseen chat costs only the effects (42.5A.0 butir 3). Text is assembled only for steps a player can hear: within
 * {@code kotaBicara.obrolanTampilJarak} blocks, at most {@code obrolanMaksTampil} chats shown per place, as speech bubbles
 * ({@code kota_ujaran}); a player who hears a chat to its end gets its fact as a note.
 * <p>
 * Driven by the 38 chat cycle through {@link TubuhPenduduk#addUjaran}: the four built-in turns of a body chat show the
 * planned steps (the built-in line is skipped); {@link Cakap#obrol} runs one without bodies (effects only).
 */
public final class ObrolanWarga {

	/** Most steps one chat may take. */
	public static final int LANGKAH_MAKS = 8;

	// ================================================================== script data

	/**
	 * One step of a script.
	 *
	 * @param id     step id
	 * @param olehA  said by the starter (A) or the other (B)
	 * @param niat   intent of the utterance
	 * @param fakta  {@code menonjol} = tells the salient fact of the chat; null = no fact
	 * @param bumbu  the telling may embellish
	 * @param syarat step condition ({@code saksi A_maks D_min P_maks ronde_maks} plus the common syarat keys)
	 * @param lanjut next step ids
	 */
	public record Langkah(String id, boolean olehA, Niat niat, @Nullable String fakta, boolean bumbu, JsonObject syarat,
			List<String> lanjut) {
	}

	/**
	 * A chat script ({@code obrolan/<jenis>.json}).
	 *
	 * @param jenis   chat kind id
	 * @param syarat  start condition ({@code hubungan_min fakta_menonjol} + common keys)
	 * @param dasar   start chance base (0.3)
	 * @param bagiX   start chance X divisor (300)
	 * @param bagiHub start chance bond divisor (400)
	 * @param jedaMin pause between turns, ticks
	 * @param jedaMaks pause between turns, ticks
	 * @param langkah steps by id, first = opening
	 * @param efek    effects of the whole chat
	 */
	public record Naskah(String jenis, JsonObject syarat, double dasar, double bagiX, double bagiHub, int jedaMin, int jedaMaks,
			Map<String, Langkah> langkah, JsonObject efek) {

		static Naskah parse(JsonObject json) {
			JsonObject mulai = CakapReload.objek(json, "mulai");
			Map<String, Langkah> steps = new LinkedHashMap<>();
			for (JsonObject o : CakapReload.objekList(json, "langkah")) {
				String id = CakapReload.teks(o, "id", "");
				if (!id.isEmpty()) {
					steps.put(id, new Langkah(id, !CakapReload.teks(o, "oleh", "A").equalsIgnoreCase("B"),
							new Niat(CakapReload.teks(o, "niat", "BASA_BASI")), CakapReload.teksAtauNull(o, "fakta"),
							CakapReload.benar(o, "bumbu", false), CakapReload.objek(o, "syarat"), CakapReload.teksList(o, "lanjut")));
				}
			}
			int min = 40;
			int max = 80;
			JsonElement jeda = json.get("jeda_tick");
			if (jeda != null && jeda.isJsonArray() && jeda.getAsJsonArray().size() == 2) {
				min = jeda.getAsJsonArray().get(0).getAsInt();
				max = Math.max(min, jeda.getAsJsonArray().get(1).getAsInt());
			}
			String jenis = CakapReload.teks(json, "jenis", "sapa");
			return new Naskah(jenis, CakapReload.objek(json, "syarat"), CakapReload.pecahan(mulai, "dasar", 0.3F),
					CakapReload.pecahan(mulai, "X", 300.0F), CakapReload.pecahan(mulai, "hubungan", 400.0F), min, max,
					java.util.Collections.unmodifiableMap(steps), CakapReload.objek(json, "efek"));
		}
	}

	// ================================================================== a planned chat

	/** One planned step: who speaks, what, about which fact. */
	record Rencana(Langkah langkah, Warga pembicara, Warga pendengar, @Nullable Fakta fakta) {
	}

	/** A running chat between two bodies. */
	static final class Sesi {
		final Warga a;
		final Warga b;
		final int npcA;
		final int npcB;
		final long situs;
		final Cakap.JenisObrolan jenis;
		final List<Rencana> langkah;
		final long dibuat;
		final boolean tampil;
		final long benih;

		Sesi(Warga a, Warga b, int npcA, int npcB, long situs, Cakap.JenisObrolan jenis, List<Rencana> langkah, long dibuat, boolean tampil,
				long benih) {
			this.a = a;
			this.b = b;
			this.npcA = npcA;
			this.npcB = npcB;
			this.situs = situs;
			this.jenis = jenis;
			this.langkah = langkah;
			this.dibuat = dibuat;
			this.tampil = tampil;
			this.benih = benih;
		}
	}

	private static final Map<Long, Sesi> SESI = new HashMap<>();
	/** Pair key -> world hour of the last chat (<= 1 per pair per world hour). */
	private static final Map<Long, Long> TERAKHIR = new HashMap<>();
	/** Last chats run (test command {@code cakap jejak}). */
	private static final List<String> JEJAK = new ArrayList<>();

	/** Registers the chat-line hook (from {@code Cakap.init}). */
	static void init() {
		TubuhPenduduk.addUjaran(ObrolanWarga::ujar);
	}

	static void bersihkan() {
		SESI.clear();
		TERAKHIR.clear();
		JEJAK.clear();
		TAMPIL.clear();
	}

	/** Last chats (newest last, at most 16). */
	public static List<String> jejak() {
		return List.copyOf(JEJAK);
	}

	private static long kunci(int x, int y) {
		return (long) Math.min(x, y) << 32 | (Math.max(x, y) & 0xFFFFFFFFL);
	}

	// ================================================================== the hook

	/**
	 * {@link TubuhPenduduk.PenyediaUjaran}: shows the planned steps of a 42K chat instead of the built-in lines (also asked by
	 * the 34 town chatter of {@code NpcLife}). Turn 0 plans the chat; false = not a 42K chat, the caller says its own line.
	 */
	public static boolean ujar(ServerLevel level, KultivatorNpc pembicara, KultivatorNpc pendengar, String topik, int langkah, long now) {
		if (!aktif() || !KotaHidup.duniaKota(level)) {
			return false;
		}
		long key = kunci(pembicara.getId(), pendengar.getId());
		Sesi sesi = SESI.get(key);
		if (langkah == 0 || sesi == null || now - sesi.dibuat > 1200L) {
			SESI.remove(key);
			if (langkah != 0) {
				return false;
			}
			sesi = mulai(level, pembicara, pendengar, topik, now);
			if (sesi == null) {
				return false;
			}
			SESI.put(key, sesi);
			if (SESI.size() > 256) {
				SESI.values().removeIf(s -> now - s.dibuat > 1200L);
			}
		}
		if (!sesi.tampil) {
			return true;
		}
		int i = langkah;
		if (i < sesi.langkah.size()) {
			// the last built-in turn also carries any remaining planned steps, so every planned line is heard
			int until = langkah == 3 ? sesi.langkah.size() : i + 1;
			for (int k = i; k < until && k < i + 2; k++) {
				tampilkan(level, sesi, sesi.langkah.get(k), k, now);
			}
			if (until == sesi.langkah.size() && langkah == 3) {
				dengarAkhir(level, sesi);
			}
		} else if (i == sesi.langkah.size()) {
			Rencana last = sesi.langkah.get(sesi.langkah.size() - 1);
			Rencana back = new Rencana(new Langkah("sambung", !last.langkah.olehA, Niat.SAMBUNG_RASA, null, false, new JsonObject(), List.of()),
					last.pendengar, last.pembicara, null);
			tampilkan(level, sesi, back, i, now);
			dengarAkhir(level, sesi);
		}
		return true;
	}

	/** Plans a chat between two bodies; null when they are no warga, the pair talked this hour, or the start roll fails. */
	private static @Nullable Sesi mulai(ServerLevel level, KultivatorNpc npcA, KultivatorNpc npcB, String topik, long now) {
		Warga a = WargaApi.dari(npcA);
		Warga b = WargaApi.dari(npcB);
		if (a == null || b == null || a == b) {
			return null;
		}
		MinecraftServer server = level.getServer();
		long situs = npcA.penduduk() ? npcA.situsPenduduk() : npcA.town();
		Pengetahuan pk = Pengetahuan.get(server);
		long hari = KotaHidup.hari(server);
		int jam = (int) (now / Emosi.JAM);
		long pair = kunci(a.id, b.id);
		Long last = TERAKHIR.get(pair);
		if (last != null && jam - last < 1L) {
			return null;
		}
		SplittableRandom r = Cakap.acak(server.overworld().getSeed(), a.id, b.id, hari, jam, "obrol");
		double chance = mulaiPeluang(pk, a, b, null);
		if (r.nextDouble() >= chance) {
			return null;
		}
		TERAKHIR.put(pair, (long) jam);
		Cakap.JenisObrolan jenis = pilihJenis(server, pk, a, b, situs, r);
		List<Rencana> plan = rencanakan(server, pk, a, b, situs, jenis, r, hari);
		if (plan.isEmpty()) {
			return null;
		}
		efek(level, pk, a, b, situs, jenis, plan, r, hari, now);
		boolean tampil = terlihat(level, npcA, situs, now);
		catatJejak(a, b, jenis, plan, tampil);
		return new Sesi(a, b, npcA.getId(), npcB.getId(), situs, jenis, plan, now, tampil, r.nextLong());
	}

	/** Start chance {@code 0.3 + X_A/300 + bond/400} (script numbers when present), clamped 0.05-0.95. */
	static double mulaiPeluang(Pengetahuan pk, Warga a, Warga b, @Nullable Naskah n) {
		double dasar = n == null ? 0.3D : n.dasar;
		double bx = n == null ? 300.0D : n.bagiX;
		double bh = n == null ? 400.0D : n.bagiHub;
		return Math.max(0.05D, Math.min(0.95D, dasar + pk.sifat(a).x() / bx + Hubungan.kekuatan(a, b) / bh));
	}

	/** Shown chats per place right now (cap {@code obrolanMaksTampil}). */
	private static final Map<Long, List<Long>> TAMPIL = new HashMap<>();

	private static boolean terlihat(ServerLevel level, KultivatorNpc npc, long situs, long now) {
		double radius = KotaConfig.bicara().obrolanTampilJarak;
		boolean dekat = false;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(npc) <= radius * radius) {
				dekat = true;
				break;
			}
		}
		if (!dekat) {
			return false;
		}
		List<Long> list = TAMPIL.computeIfAbsent(situs, k -> new ArrayList<>());
		list.removeIf(t -> now - t > 400L);
		if (list.size() >= KotaConfig.bicara().obrolanMaksTampil) {
			return false;
		}
		list.add(now);
		return true;
	}

	// ================================================================== kind and plan

	/**
	 * The chat kind (42.5A.9 table) from bonds, knowledge and feelings: every kind whose condition holds gets a weight
	 * (specific kinds 3, SAPA 1) and one is drawn with the seeded generator.
	 */
	static Cakap.JenisObrolan pilihJenis(MinecraftServer server, Pengetahuan pk, Warga a, Warga b, long situs, SplittableRandom r) {
		long now = server.overworld().getGameTime();
		long hari = KotaHidup.hari(server);
		JenisHubungan j = Hubungan.jenis(a, b);
		int kuat = Hubungan.kekuatan(a, b);
		boolean keluarga = Pengetahuan.keluarga(a, Fakta.Rujuk.warga(b.id));
		float[] padA = pk.pad(a, now);
		float[] padB = pk.pad(b, now);
		Kepribadian.Sifat sa = pk.sifat(a);
		Kepribadian.Sifat sb = pk.sifat(b);
		int umurA = WargaApi.umur(a);
		int umurB = WargaApi.umur(b);
		Map<Cakap.JenisObrolan, Integer> w = new LinkedHashMap<>();
		if ((kuat >= 0 || keluarga) && faktaMenonjol(pk, a, b, hari) != null) {
			w.put(Cakap.JenisObrolan.GOSIP, 3 + (Kepribadian.punya(a, "gosip_sumur") ? 3 : 0));
		}
		if (j == JenisHubungan.MUSUH || j == JenisHubungan.SAINGAN || opiniBerlawanan(pk, a, b, hari)) {
			w.put(Cakap.JenisObrolan.DEBAT, j == JenisHubungan.MUSUH ? 5 : 3);
		}
		if (j == JenisHubungan.KEKASIH || (Hubungan.bolehKekasih(a, b, umurA, umurB) && kuat >= 20 && padA[0] > 0.1F)) {
			w.put(Cakap.JenisObrolan.MERAYU, j == JenisHubungan.KEKASIH ? 5 : 2);
		}
		if (a.peranNpc().kelompok == NpcRole.Kelompok.PEDAGANG || b.peranNpc().kelompok == NpcRole.Kelompok.PEDAGANG) {
			w.put(Cakap.JenisObrolan.DAGANG, 2);
		}
		if (j == JenisHubungan.GURU_MURID || (a.peranNpc().kelompok == NpcRole.Kelompok.TETUA && umurB < 16)
				|| (a.peranNpc().kelompok == NpcRole.Kelompok.CENDEKIA && umurB < 18)) {
			w.put(Cakap.JenisObrolan.AJAR, 3);
		}
		if ((j == JenisHubungan.SAHABAT || keluarga || j == JenisHubungan.PASANGAN) && (padA[0] < -0.3F || padB[0] < -0.3F)) {
			w.put(Cakap.JenisObrolan.CURHAT, 4);
		}
		if (sa.x() > 20 && sb.x() > 20 && padA[0] >= 0.0F && padB[0] >= 0.0F && j != JenisHubungan.MUSUH) {
			w.put(Cakap.JenisObrolan.CANDA, 2);
		}
		w.put(Cakap.JenisObrolan.SAPA, 1);
		Map<String, Naskah> scripts = CakapReload.obrolan();
		w.keySet().removeIf(k -> k != Cakap.JenisObrolan.SAPA && !scripts.isEmpty() && !scripts.containsKey(k.id()));
		int total = 0;
		for (int v : w.values()) {
			total += v;
		}
		int roll = r.nextInt(Math.max(1, total));
		for (Map.Entry<Cakap.JenisObrolan, Integer> e : w.entrySet()) {
			roll -= e.getValue();
			if (roll < 0) {
				return e.getKey();
			}
		}
		return Cakap.JenisObrolan.SAPA;
	}

	/** The most salient fact {@code a} remembers that {@code b} does not know (gossip first), or null. */
	static @Nullable Fakta faktaMenonjol(Pengetahuan pk, Warga a, Warga b, long hari) {
		Fakta best = null;
		double score = 0.0D;
		for (Fakta f : pk.ingat(a, hari)) {
			if (pk.sama(b, f) != null || (f.gosipId() >= 0 && GosipWarga.tahu(b, f.gosipId()))) {
				continue;
			}
			Predikat p = Predikat.dari(f.predikat());
			if (p.rahasia() && Hubungan.kekuatan(a, b) < 40) {
				continue;
			}
			double rel = f.subjek().jenis() == Fakta.Jenis.WARGA && f.subjek().id() == b.id ? 2.0D
					: Pengetahuan.keluarga(b, f.subjek()) ? 2.0D : 1.0D;
			double s = Lupa.menonjol(f, hari, rel, f.subjek().jenis() == Fakta.Jenis.WARGA && f.subjek().id() == a.id);
			if (s > score) {
				score = s;
				best = f;
			}
		}
		return score >= 0.05D ? best : null;
	}

	private static boolean opiniBerlawanan(Pengetahuan pk, Warga a, Warga b, long hari) {
		for (Fakta fa : pk.ingat(a, hari)) {
			if (!fa.predikat().equals("OPINI")) {
				continue;
			}
			Fakta fb = pk.sama(b, fa);
			if (fb != null && Integer.signum(fa.nilai()) * Integer.signum(fb.nilai()) < 0 && Math.abs(fa.nilai() - fb.nilai()) >= 60) {
				return true;
			}
		}
		return false;
	}

	/** Walks the script: start step, then each step's {@code lanjut} filtered by its condition, seeded choice. */
	static List<Rencana> rencanakan(MinecraftServer server, Pengetahuan pk, Warga a, Warga b, long situs, Cakap.JenisObrolan jenis,
			SplittableRandom r, long hari) {
		Naskah n = CakapReload.obrolan().get(jenis.id());
		List<Rencana> out = new ArrayList<>();
		Fakta fakta = jenis == Cakap.JenisObrolan.GOSIP ? faktaMenonjol(pk, a, b, hari) : null;
		if (n == null || n.langkah.isEmpty()) {
			// built-in minimum: greeting + small talk (+ the fact for gossip) + reaction
			out.add(new Rencana(langkahBawaan("sapa", true, Niat.SAPA), a, b, null));
			if (fakta != null) {
				out.add(new Rencana(langkahBawaan("kabar", true, Niat.BERI_KABAR), a, b, fakta));
				out.add(new Rencana(langkahBawaan("reaksi", false, Niat.REAKSI), b, a, fakta));
			} else {
				out.add(new Rencana(langkahBawaan("basa_basi", false, Niat.BASA_BASI), b, a, null));
			}
			return out;
		}
		long now = server.overworld().getGameTime();
		Langkah cur = n.langkah.values().iterator().next();
		Map<String, Integer> ronde = new HashMap<>();
		while (cur != null && out.size() < LANGKAH_MAKS) {
			Warga sp = cur.olehA ? a : b;
			Warga li = cur.olehA ? b : a;
			out.add(new Rencana(cur, sp, li, cur.fakta != null ? fakta : null));
			ronde.merge(cur.id, 1, Integer::sum);
			List<Langkah> next = new ArrayList<>();
			for (String id : cur.lanjut) {
				Langkah l = n.langkah.get(id);
				if (l != null && bolehLangkah(server, pk, l, l.olehA ? a : b, l.olehA ? b : a, situs, fakta, ronde, now)) {
					next.add(l);
				}
			}
			cur = next.isEmpty() ? null : next.get(r.nextInt(next.size()));
		}
		return out;
	}

	private static Langkah langkahBawaan(String id, boolean olehA, Niat niat) {
		return new Langkah(id, olehA, niat, null, false, new JsonObject(), List.of());
	}

	/** Step condition: {@code saksi} (speaker saw the fact), {@code A_maks}, {@code D_min}, {@code P_maks}, {@code ronde_maks}, common keys. */
	private static boolean bolehLangkah(MinecraftServer server, Pengetahuan pk, Langkah l, Warga sp, Warga li, long situs, @Nullable Fakta fakta,
			Map<String, Integer> ronde, long now) {
		JsonObject s = l.syarat;
		if (s.isEmpty()) {
			return true;
		}
		if (s.has("saksi")) {
			boolean saw = fakta != null && saksi(pk, sp, fakta, KotaHidup.hari(server));
			if (saw != s.get("saksi").getAsBoolean()) {
				return false;
			}
		}
		Kepribadian.Sifat sifat = pk.sifat(sp);
		float[] pad = pk.pad(sp, now);
		if (s.has("A_maks") && sifat.a() > s.get("A_maks").getAsInt()) {
			return false;
		}
		if (s.has("D_min") && pad[2] < s.get("D_min").getAsFloat()) {
			return false;
		}
		if (s.has("P_maks") && pad[0] > s.get("P_maks").getAsFloat()) {
			return false;
		}
		if (s.has("ronde_maks") && ronde.getOrDefault(l.id, 0) >= s.get("ronde_maks").getAsInt()) {
			return false;
		}
		return Topik.cocok(s, Topik.Situasi.bangun(server, sp, null, li, situs, fakta == null ? null : fakta.predikat())) >= 0;
	}

	/** True when {@code w} holds its own witnessed copy of {@code f}. */
	static boolean saksi(Pengetahuan pk, Warga w, Fakta f, long hari) {
		Fakta mine = pk.sama(w, f);
		return mine != null && (mine.asal().sumber() == Fakta.Sumber.SAKSI || mine.asal().sumber() == Fakta.Sumber.DIRI);
	}

	// ================================================================== effects

	/** Applies the effects of a planned chat (42.5A.9 "Efek nyata"): always, seen or not. */
	static void efek(ServerLevel level, Pengetahuan pk, Warga a, Warga b, long situs, Cakap.JenisObrolan jenis, List<Rencana> plan,
			SplittableRandom r, long hari, long now) {
		MinecraftServer server = level.getServer();
		Naskah n = CakapReload.obrolan().get(jenis.id());
		JsonObject e = n == null ? bawaanEfek(jenis) : n.efek;
		WargaData data = WargaData.get(server);
		// fact moves (gossip through the gossip table, other facts as heard copies), corrections spread back
		if (CakapReload.benar(e, "fakta_pindah", jenis == Cakap.JenisObrolan.GOSIP)) {
			for (Rencana step : plan) {
				if (step.fakta == null || !step.langkah.niat.equals(Niat.BERI_KABAR)) {
					continue;
				}
				Fakta got;
				GosipWarga.Gosip g = step.fakta.gosipId() >= 0 ? GosipWarga.cari(server, situs, step.fakta.gosipId()) : null;
				if (g != null) {
					got = GosipWarga.ceritakan(pk, data, step.pembicara, step.pendengar, g, r, hari);
				} else {
					Fakta mine = pk.sama(step.pembicara, step.fakta);
					got = mine == null ? null : pk.simpan(step.pendengar, GosipWarga.turunan(pk, step.pembicara, step.pendengar, mine, r, hari), hari);
				}
				if (got != null && saksi(pk, step.pendengar, step.fakta, hari)) {
					// the listener saw it: its own copy wins and the teller is corrected
					Fakta truth = pk.sama(step.pendengar, step.fakta);
					if (truth != null) {
						pk.simpan(step.pembicara, GosipWarga.turunan(pk, step.pendengar, step.pembicara, truth, r, hari), hari);
					}
				}
				int kesanSubjek = CakapReload.angka(e, "kesan_subjek", 0);
				if (kesanSubjek != 0 && got != null && got.subjek().jenis() == Fakta.Jenis.WARGA) {
					Warga subj = WargaApi.byId((int) got.subjek().id());
					if (subj != null && subj != step.pendengar && got.valensi() != 0) {
						Hubungan.ubah(data, step.pendengar, subj, Integer.signum(got.valensi()) * kesanSubjek, hari, WargaApi.umur(step.pendengar),
								WargaApi.umur(subj));
					}
				}
			}
		}
		JsonElement hub = e.get("hubungan");
		if (hub != null && hub.isJsonArray() && hub.getAsJsonArray().size() == 2) {
			int lo = hub.getAsJsonArray().get(0).getAsInt();
			int hi = hub.getAsJsonArray().get(1).getAsInt();
			int delta = lo + r.nextInt(Math.max(1, Math.abs(hi - lo) + 1)) * Integer.signum(hi - lo == 0 ? 1 : hi - lo);
			if (delta != 0) {
				Hubungan.ubah(data, a, b, delta, hari, WargaApi.umur(a), WargaApi.umur(b));
			}
		}
		String emosi = CakapReload.teksAtauNull(e, "emosi");
		if (emosi != null) {
			Emosi.Peristiwa ev = Cakap.peristiwaData(emosi);
			if (ev != null) {
				pk.dorong(a, ev, 1.0F, Fakta.Rujuk.warga(b.id), now);
				pk.dorong(b, ev, 1.0F, Fakta.Rujuk.warga(a.id), now);
			}
		}
		if (CakapReload.benar(e, "perkelahian", false)) {
			Kepribadian.Sifat sa = pk.sifat(a);
			float[] pa = pk.pad(a, now);
			if (sa.a() < -40 && pa[2] > 0.2F && KotaConfig.acara().perkelahian && !PeristiwaJalan.picu(level, situs, "perkelahian")) {
				PeristiwaJalan.picu(level, situs, "tawuran");
			}
		}
		if (CakapReload.benar(e, "permintaan", false)) {
			Warga sad = pk.pad(a, now)[0] <= pk.pad(b, now)[0] ? a : b;
			PermintaanWarga.buat(sad, "curhat");
		}
		int ahli = CakapReload.angka(e, "ahli", 0);
		if (ahli != 0) {
			Warga murid = WargaApi.umur(a) < WargaApi.umur(b) ? a : b;
			murid.ahli = Math.max(0, Math.min(100, murid.ahli + ahli));
		}
		data.ubah();
		pk.ubah();
	}

	/** Effects of a kind without a script (42.5A.9 table numbers). */
	private static JsonObject bawaanEfek(Cakap.JenisObrolan jenis) {
		JsonObject e = new JsonObject();
		switch (jenis) {
			case GOSIP -> {
				e.addProperty("fakta_pindah", true);
				e.addProperty("kesan_subjek", 3);
			}
			case DEBAT -> {
				e.add("hubungan", pasang(-8, -2));
				e.addProperty("emosi", "dihina");
				e.addProperty("perkelahian", true);
			}
			case MERAYU -> e.add("hubungan", pasang(3, 6));
			case CURHAT -> {
				e.addProperty("emosi", "dihibur");
				e.addProperty("permintaan", true);
			}
			case CANDA -> {
				e.add("hubungan", pasang(2, 2));
				e.addProperty("emosi", "canda_berhasil");
			}
			case AJAR -> e.addProperty("ahli", 1);
			default -> {
			}
		}
		return e;
	}

	private static com.google.gson.JsonArray pasang(int a, int b) {
		com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
		arr.add(a);
		arr.add(b);
		return arr;
	}

	// ================================================================== showing

	private static void tampilkan(ServerLevel level, Sesi sesi, Rencana step, int index, long now) {
		MinecraftServer server = level.getServer();
		int npcId = step.pembicara == sesi.a ? sesi.npcA : sesi.npcB;
		if (!(level.getEntity(npcId) instanceof KultivatorNpc body) || !body.isAlive()) {
			return;
		}
		String topik = step.fakta != null ? Predikat.dari(step.fakta.predikat()).topik() : sesi.jenis.id();
		Topik.Situasi s = Topik.Situasi.bangun(server, step.pembicara, null, step.pendengar, sesi.situs,
				step.fakta == null ? null : step.fakta.predikat());
		Map<String, Object> isian = new LinkedHashMap<>();
		isian.put("jenis", sesi.jenis.id());
		isian.put("lawan", WargaApi.nama(step.pendengar));
		SplittableRandom r = new SplittableRandom(sesi.benih ^ (index * 0x9E3779B97F4A7C15L));
		Fakta told = step.fakta == null ? null : Pengetahuan.get(server).sama(step.pendengar, step.fakta);
		Ujaran u = Giliran.ujaran(new Giliran.Permintaan(step.langkah.niat, topik, s, told != null ? told : step.fakta, isian, null,
				step.pendengar, r));
		if (u.kosong()) {
			return;
		}
		int naskahMin = 40;
		int naskahMaks = 80;
		Naskah n = CakapReload.obrolan().get(sesi.jenis.id());
		if (n != null) {
			naskahMin = n.jedaMin;
			naskahMaks = n.jedaMaks;
		}
		int durasi = naskahMin + r.nextInt(Math.max(1, naskahMaks - naskahMin + 1));
		KotaPayloads.siarUjaran(level, body.position(), new KotaPayloads.Ujaran(npcId, u.komponen(), u.gestur(), durasi,
				u.seru() == null ? "" : u.seru(), u.wajah()));
		int otherId = npcId == sesi.npcA ? sesi.npcB : sesi.npcA;
		if (level.getEntity(otherId) instanceof KultivatorNpc other) {
			HidupApi.tatap(body, other);
			HidupApi.tatap(other, body);
		}
	}

	/** A player within hearing at the end of a chat with a fact gets it as a note (42.5A.9 "Menguping"). */
	private static void dengarAkhir(ServerLevel level, Sesi sesi) {
		Fakta fakta = null;
		for (Rencana step : sesi.langkah) {
			if (step.fakta != null) {
				fakta = step.fakta;
			}
		}
		if (fakta == null) {
			return;
		}
		double radius = KotaConfig.bicara().obrolanTampilJarak;
		if (!(level.getEntity(sesi.npcA) instanceof KultivatorNpc body)) {
			return;
		}
		MinecraftServer server = level.getServer();
		WargaData data = WargaData.get(server);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(body) <= radius * radius) {
				Pengetahuan.get(server).catat(data.indeksPemain(p.getUUID()), fakta, KotaHidup.hari(server));
				Advancements.grant(p, "tempat/kota42_nguping");
			}
		}
	}

	// ================================================================== without bodies

	/**
	 * A chat with no bodies (Cakap.obrol, Hari Warga): plan + effects only; returns the kind run, or null when none.
	 */
	static @Nullable Cakap.JenisObrolan tanpaTubuh(ServerLevel level, Warga a, Warga b, @Nullable Cakap.JenisObrolan paksa) {
		if (!aktif() || a == b) {
			return null;
		}
		MinecraftServer server = level.getServer();
		Pengetahuan pk = Pengetahuan.get(server);
		long hari = KotaHidup.hari(server);
		long now = server.overworld().getGameTime();
		long situs = WargaData.get(server).situsDari(a.id);
		SplittableRandom r = Cakap.acak(server.overworld().getSeed(), a.id, b.id, hari, (int) (now / Emosi.JAM), "obrol_tanpa");
		Cakap.JenisObrolan jenis = paksa != null ? paksa : pilihJenis(server, pk, a, b, situs, r);
		List<Rencana> plan = rencanakan(server, pk, a, b, situs, jenis, r, hari);
		if (plan.isEmpty()) {
			return null;
		}
		efek(level, pk, a, b, situs, jenis, plan, r, hari, now);
		catatJejak(a, b, jenis, plan, false);
		return jenis;
	}

	/**
	 * Realises a whole chat as text (test command): every planned step as one utterance, effects NOT applied.
	 */
	static List<Ujaran> naskahTeks(MinecraftServer server, Warga a, Warga b, Cakap.JenisObrolan jenis) {
		Pengetahuan pk = Pengetahuan.get(server);
		long hari = KotaHidup.hari(server);
		long situs = WargaData.get(server).situsDari(a.id);
		SplittableRandom r = Cakap.acak(server.overworld().getSeed(), a.id, b.id, hari, 0, "obrol_uji");
		List<Ujaran> out = new ArrayList<>();
		int i = 0;
		for (Rencana step : rencanakan(server, pk, a, b, situs, jenis, r, hari)) {
			String topik = step.fakta != null ? Predikat.dari(step.fakta.predikat()).topik() : jenis.id();
			Topik.Situasi s = Topik.Situasi.bangun(server, step.pembicara, null, step.pendengar, situs,
					step.fakta == null ? null : step.fakta.predikat());
			Map<String, Object> isian = new LinkedHashMap<>();
			isian.put("jenis", jenis.id());
			out.add(Giliran.ujaran(new Giliran.Permintaan(step.langkah.niat, topik, s, step.fakta, isian, null, step.pendengar,
					new SplittableRandom(r.nextLong() ^ i++))));
		}
		return out;
	}

	private static void catatJejak(Warga a, Warga b, Cakap.JenisObrolan jenis, List<Rencana> plan, boolean tampil) {
		StringBuilder sb = new StringBuilder();
		sb.append(a.id).append(" & ").append(b.id).append(' ').append(jenis.id()).append(tampil ? " [tampil]" : "").append(':');
		for (Rencana r : plan) {
			sb.append(' ').append(r.langkah.id).append(r.fakta != null ? "(" + r.fakta.predikat() + ")" : "");
		}
		JEJAK.add(sb.toString());
		while (JEJAK.size() > 16) {
			JEJAK.remove(0);
		}
	}

	static boolean aktif() {
		return KotaConfig.bicara().aktif && KotaConfig.bicara().mesinLanjut && KotaConfig.bicara().obrolanNpc && WargaApi.aktif();
	}

	private ObrolanWarga() {
	}
}
