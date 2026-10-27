package dev.kultivator.dunia.kota42.bicara;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.warga.ArsipWarga;
import dev.kultivator.dunia.kota42.warga.Hubungan;
import dev.kultivator.dunia.kota42.warga.Kebiasaan;
import dev.kultivator.dunia.kota42.warga.Migrasi;
import dev.kultivator.dunia.kota42.warga.PendudukTambahan;
import dev.kultivator.dunia.kota42.warga.SiklusHidup;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.kota42.warga.WargaGen;
import dev.kultivator.dunia.tempat.cerita.CeritaData;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Named town characters (DESIGN 42.5 / 42.10): the {@code data/kultivator/tempat/tokoh/*.json} registry (written by 42B-C's
 * {@code cerita_isi_kota42.py}) cast onto residents of the first capital, the first town of kind 34 and the first themed
 * city of a theme, their tagged bodies, names, titles, family links and life events.
 * <p>
 * <b>Data</b> (one file is {@code {"tokoh": [ ... ]}} or a single object; an entry): {@code id}, {@code nama_han},
 * {@code peran} ({@code NpcRole.id()}), {@code perempuan}, {@code umur}, {@code watak} (temperament id or ""), {@code suara}
 * and {@code suara_tubuh} (0-5), {@code abadi} (never ages away: false = mortal), {@code tempat} ({@code ibukota},
 * {@code keliling}: the first capital; {@code kota34}: the nearest kind-34 town to it; {@code tema_<tema>}: the first
 * themed city of that theme), {@code lokasi} (service lots the body prefers), {@code kebiasaan} (habit ids), {@code keluarga}
 * ({@code [{hubungan, tokoh}]}), {@code jadwal}, {@code hewan} (a cat: never cast, only named), {@code rupa} (a look key
 * such as {@code topeng_kertas}), optional {@code akar} (0-5: a spirit root; the story child is HEAVENLY), {@code nama},
 * {@code gelar}, {@code riwayat} (lang keys).
 * <p>
 * <b>Casting</b> (Hari Warga step, once per character per world): the best fitting living resident of the place (same
 * work role, same sex, closest age, a body of a generated slot) becomes the character: {@link Warga#tokoh} (so 42D's
 * rounds, 42B-C's dialogs and quests recognise it), {@link Warga#abadi}, age, temperament, voice, habits, root. First-degree
 * links of {@code keluarga} become real family ({@code ayah ibu anak istri suami}; households merge when one side has no
 * generated slot), the rest bonds ({@code GURU_MURID}, {@code SAHABAT}). Characters are registered into the 38
 * {@link Tokoh} table at data load so 38 dialogs, {@code jadi_tokoh} and name plates know them; bodies are tagged
 * ({@link #tandaiTubuh}) when a resident body of the character stands (every 5 s and on every talk).
 * <p>
 * State in {@code KotaBicara.Data} section {@code tokoh}: {@code w} (character id -> resident id, kept after death),
 * {@code tempat} (chosen places), {@code pens} (resident id -> world day of a story retirement, 42.10 Lao Geng).
 */
public final class TokohKota {

	/** Section keys: cast map, chosen places, story retirements. */
	private static final String K_CAST = "w";
	private static final String K_TEMPAT = "tempat";
	private static final String K_PENS = "pens";

	/** One family or social link of a character. */
	public record Rel(String hubungan, String tokoh) {
	}

	/** One character of the registry. {@code watak} -1 = none (the cat), {@code akar} -1 = leave the resident's. */
	public record Def(String id, String hanzi, String peranId, boolean perempuan, int umur, int watak, int suara, boolean abadi,
			String tempat, List<String> lokasi, List<String> kebiasaan, List<Rel> keluarga, String jadwal, boolean hewan, String rupa,
			int akar) {

		public @Nullable NpcRole peran() {
			return NpcRole.byId(peranId);
		}
	}

	private static volatile Map<String, Def> defs = Map.of();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		AkibatKota.init();
	}

	// ================================================================== registry

	/** Datapack reload: tokoh file id -> json. Registers every character into the 38 table. */
	static void muat(Map<String, JsonObject> tokoh) {
		Map<String, Def> out = new LinkedHashMap<>();
		for (JsonObject file : tokoh.values()) {
			JsonElement list = file.get("tokoh");
			if (list != null && list.isJsonArray()) {
				for (JsonElement e : list.getAsJsonArray()) {
					if (e.isJsonObject()) {
						baca(e.getAsJsonObject(), out);
					}
				}
			} else if (file.has("id")) {
				baca(file, out);
			}
		}
		defs = Collections.unmodifiableMap(out);
		for (Def d : out.values()) {
			NpcRole role = d.peran();
			Tokoh.daftarData(d.id(), role == null ? NpcRole.WARGA : role, d.suara(), d.perempuan(), Math.max(d.watak(), 0));
		}
	}

	private static void baca(JsonObject o, Map<String, Def> out) {
		String id = str(o, "id", "");
		if (id.isEmpty()) {
			return;
		}
		String watakId = str(o, "watak", "");
		int watak = Tokoh.WATAK.indexOf(watakId);
		int suara = o.has("suara_tubuh") ? o.get("suara_tubuh").getAsInt() : 0;
		List<Rel> keluarga = new ArrayList<>();
		if (o.has("keluarga") && o.get("keluarga").isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray("keluarga")) {
				if (e.isJsonObject()) {
					keluarga.add(new Rel(str(e.getAsJsonObject(), "hubungan", ""), str(e.getAsJsonObject(), "tokoh", "")));
				}
			}
		}
		int akar = o.has("akar") ? o.get("akar").getAsInt() : "xiao_yu".equals(id) ? 5 : -1;
		out.put(id, new Def(id, str(o, "nama_han", ""), str(o, "peran", "warga"), o.has("perempuan") && o.get("perempuan").getAsBoolean(),
				o.has("umur") ? o.get("umur").getAsInt() : 0, watak, Math.floorMod(suara, 6), !o.has("abadi") || o.get("abadi").getAsBoolean(),
				str(o, "tempat", "ibukota"), strings(o, "lokasi"), strings(o, "kebiasaan"), List.copyOf(keluarga), str(o, "jadwal", ""),
				o.has("hewan") && o.get("hewan").getAsBoolean(), str(o, "rupa", ""), akar));
	}

	private static String str(JsonObject o, String key, String def) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
	}

	private static List<String> strings(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		JsonElement e = o.get(key);
		if (e != null && e.isJsonArray()) {
			JsonArray array = e.getAsJsonArray();
			for (JsonElement x : array) {
				if (x.isJsonPrimitive()) {
					out.add(x.getAsString());
				}
			}
		}
		return List.copyOf(out);
	}

	/** The character {@code id}, or null. */
	public static @Nullable Def def(String id) {
		return id == null ? null : defs.get(id);
	}

	/** Every registered character in file order. */
	public static List<Def> semua() {
		return List.copyOf(defs.values());
	}

	/** The resident that is character {@code tokohId} (null before it is cast, or after it died). */
	public static @Nullable Warga warga(MinecraftServer server, String tokohId) {
		if (server == null || tokohId == null || tokohId.isEmpty()) {
			return null;
		}
		int id = sub(KotaBicara.Data.get(server), K_CAST).getIntOr(tokohId, 0);
		return id == 0 ? null : WargaData.get(server).byId(id);
	}

	/** True while character {@code tokohId} is dead: it was cast and its resident is gone or in the archive (condition {@code kota_wafat}). */
	public static boolean wafat(MinecraftServer server, String tokohId) {
		if (server == null || tokohId == null || tokohId.isEmpty()) {
			return false;
		}
		int id = sub(KotaBicara.Data.get(server), K_CAST).getIntOr(tokohId, 0);
		if (id == 0) {
			return false;
		}
		Warga w = WargaData.get(server).byId(id);
		return w == null || w.status == Warga.Status.MATI;
	}

	/** The place of character {@code tokohId} now (0 = unknown). */
	public static long situs(MinecraftServer server, String tokohId) {
		Warga w = warga(server, tokohId);
		return w == null ? 0L : WargaData.get(server).situsDari(w.id);
	}

	/** The live sub-compound {@code key} of this module's section. */
	static CompoundTag sub(KotaBicara.Data store, String key) {
		CompoundTag part = store.bagian(KotaBicara.Data.TOKOH);
		CompoundTag c = part.getCompoundOrEmpty(key);
		if (!part.contains(key)) {
			part.put(key, c);
		}
		return c;
	}

	// ================================================================== daily: casting and story deaths

	/** Daily step: story retirements due today in place {@code s}, then casts the characters that belong to it. */
	static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
		if (!KotaBicara.aktif()) {
			return;
		}
		WargaData data = WargaData.get(server);
		KotaBicara.Data store = KotaBicara.Data.get(server);
		pensiunHarian(server, data, store, s, hari);
		if (defs.isEmpty()) {
			return;
		}
		CompoundTag cast = sub(store, K_CAST);
		CompoundTag tempat = sub(store, K_TEMPAT);
		long capital = CeritaData.get(server).firstCapital();
		boolean ibuKota = capital != 0L && s.id == capital;
		String jenis = null;
		List<Def> baru = new ArrayList<>();
		for (Def d : defs.values()) {
			if (d.hewan() || cast.contains(d.id())) {
				continue;
			}
			boolean sini;
			if (d.tempat().equals("ibukota") || d.tempat().equals("keliling")) {
				sini = ibuKota;
			} else if (d.tempat().equals("kota34")) {
				jenis = jenis == null ? PajakKota.jenisTempat(server, s.id) : jenis;
				sini = PajakKota.JT_KOTA.equals(jenis) && kotaTerpilih(server, data, s, capital, tempat);
			} else if (d.tempat().startsWith("tema_")) {
				sini = temaCocok(server, s, d.tempat().substring(5), tempat);
			} else {
				sini = ibuKota;
			}
			if (sini) {
				baru.add(d);
			}
		}
		if (baru.isEmpty()) {
			return;
		}
		RandomSource random = RandomSource.create(WargaGen.mix(server.overworld().getSeed() ^ s.id * 0x9E3779B97F4A7C15L ^ 0x70C0D0L));
		Set<Integer> dipakai = new HashSet<>();
		for (String key : cast.keySet()) {
			dipakai.add(cast.getIntOr(key, 0));
		}
		Map<String, Warga> jadi = new LinkedHashMap<>();
		for (Def d : baru) {
			Warga w = pilih(s, d, hari, dipakai, random);
			if (w == null) {
				continue;
			}
			terapkan(server, data, s.id, w, d, hari, random);
			dipakai.add(w.id);
			cast.putInt(d.id(), w.id);
			jadi.put(d.id(), w);
		}
		for (Def d : baru) {
			Warga a = jadi.get(d.id());
			if (a == null) {
				continue;
			}
			for (Rel rel : d.keluarga()) {
				Warga b = data.byId(cast.getIntOr(rel.tokoh(), 0));
				if (b != null && b.id != a.id) {
					hubungkan(data, a, rel.hubungan(), b, hari);
				}
			}
		}
		if (!jadi.isEmpty()) {
			store.ubah();
			data.ubah();
			Kultivator.LOGGER.info("Town characters cast in place {}: {}", s.id, jadi.keySet());
		}
	}

	private static boolean kotaTerpilih(MinecraftServer server, WargaData data, WargaData.Situs s, long capital, CompoundTag tempat) {
		long pilih = tempat.getLongOr("kota34", 0L);
		if (pilih != 0L) {
			return pilih == s.id;
		}
		WargaData.Situs cap = capital == 0L ? null : data.peek(capital);
		WargaData.Situs best = null;
		double bestD = Double.MAX_VALUE;
		for (WargaData.Situs x : data.semuaSitus()) {
			if (!x.bangkit() || x.warga.size() < 8 || !PajakKota.JT_KOTA.equals(PajakKota.jenisTempat(server, x.id))) {
				continue;
			}
			double dist = cap == null ? 0.0D : Migrasi.jarak(cap, x);
			if (best == null || dist < bestD || dist == bestD && x.id < best.id) {
				best = x;
				bestD = dist;
			}
		}
		if (best == null || best.id != s.id) {
			return false;
		}
		tempat.putLong("kota34", s.id);
		return true;
	}

	private static boolean temaCocok(MinecraftServer server, WargaData.Situs s, String tema, CompoundTag tempat) {
		long pilih = tempat.getLongOr("tema_" + tema, 0L);
		if (pilih != 0L) {
			return pilih == s.id;
		}
		TemaKota.Kota kota = TemaKota.dari(server, s.id);
		if (kota == null || !kota.jenis().equals("kota_tema/" + tema)) {
			return false;
		}
		tempat.putLong("tema_" + tema, s.id);
		return true;
	}

	/** The best fitting resident of place {@code s} for character {@code d}, or null. */
	private static @Nullable Warga pilih(WargaData.Situs s, Def d, long hari, Set<Integer> dipakai, RandomSource random) {
		boolean anak = d.umur() > 0 && d.umur() < 13;
		int target = d.umur() > 0 ? d.umur() : 40;
		Warga best = null;
		long bestScore = Long.MAX_VALUE;
		for (Warga w : s.warga) {
			if (w.status != Warga.Status.HIDUP || !w.tokoh.isEmpty() || dipakai.contains(w.id)) {
				continue;
			}
			int umur = SiklusHidup.umurPada(w, hari);
			if (umur < 3 || anak != (umur < 13)) {
				continue;
			}
			long score = Math.abs(umur - target) * 3L + random.nextInt(25);
			if (w.pria() == d.perempuan()) {
				score += 600L;
			}
			NpcRole peran = d.peran();
			if (peran != null && !w.peran.equals(d.peranId())) {
				score += w.peranNpc().kelompok == peran.kelompok ? 60L : 150L;
			}
			if (w.slot < 0 || PendudukTambahan.tambahan(w.slot)) {
				score += 45L; // bodies of generated slots have homes and work spots
			}
			if (score < bestScore) {
				bestScore = score;
				best = w;
			}
		}
		return best;
	}

	/** Makes resident {@code w} the character {@code d}. */
	private static void terapkan(MinecraftServer server, WargaData data, long situs, Warga w, Def d, long hari, RandomSource random) {
		byte kelamin = d.perempuan() ? Warga.WANITA : Warga.PRIA;
		if (w.kelamin != kelamin) {
			cerai(data, w);
			Warga tunangan = w.tunangan == Warga.TANPA ? null : data.byId(w.tunangan);
			if (tunangan != null) {
				tunangan.tunangan = Warga.TANPA;
			}
			w.tunangan = Warga.TANPA;
			w.kelamin = kelamin;
			w.look = WargaGen.lookUntuk(w.peranNpc(), kelamin, random);
		}
		int umur = d.umur() > 0 ? d.umur() : 40;
		int hpt = Math.max(1, KotaConfig.warga().hariPerTahun);
		w.lahir = hari * 10L - ((long) umur * 10L * hpt + random.nextInt(10 * hpt));
		if (d.watak() >= 0) {
			w.watak = d.watak();
		}
		w.suara = d.suara();
		w.tokoh = d.id();
		w.abadi = d.abadi();
		if (!d.kebiasaan().isEmpty()) {
			w.kebiasaan1 = Kebiasaan.nomor(d.kebiasaan().get(0));
			w.kebiasaan2 = d.kebiasaan().size() > 1 ? Kebiasaan.nomor(d.kebiasaan().get(1)) : 0;
		}
		if (d.akar() >= 0) {
			w.akar = d.akar();
		}
		if (umur >= 16) {
			w.ahli = Math.max(w.ahli, Math.min(95, (umur - 16) * 3 + 20));
		}
		bersihkanKeluarga(data, w, hari);
		data.ubah();
		// drops cached plans and walking groups that were built for the resident as it was
		KotaBus.KERJA_BERUBAH.invoker().onKerjaBerubah(server, situs, w, w.peran, w.peran);
	}

	/** Unlinks parents and children whose ages no longer make sense after the age change. */
	private static void bersihkanKeluarga(WargaData data, Warga w, long hari) {
		int umur = SiklusHidup.umurPada(w, hari);
		for (int pid : new int[] {w.ayah, w.ibu}) {
			Warga p = pid == Warga.TANPA ? null : data.byId(pid);
			if (p != null && SiklusHidup.umurPada(p, hari) - umur < 14) {
				lepasOrangTua(w, p);
			}
		}
		for (int cid : w.anak.clone()) {
			Warga c = data.byId(cid);
			if (c != null && umur - SiklusHidup.umurPada(c, hari) < 14) {
				lepasOrangTua(c, w);
			}
		}
	}

	private static void lepasOrangTua(Warga anak, Warga orangTua) {
		if (anak.ayah == orangTua.id) {
			anak.ayah = Warga.TANPA;
		}
		if (anak.ibu == orangTua.id) {
			anak.ibu = Warga.TANPA;
		}
		int n = 0;
		int[] keep = new int[orangTua.anak.length];
		for (int c : orangTua.anak) {
			if (c != anak.id) {
				keep[n++] = c;
			}
		}
		orangTua.anak = java.util.Arrays.copyOf(keep, n);
	}

	// ------------------------------------------------------------------ family and bonds

	private static void hubungkan(WargaData data, Warga a, String hubungan, Warga b, long hari) {
		switch (hubungan) {
			case "ayah", "ibu" -> jadikanOrangTua(data, a, b, hari);
			case "anak" -> jadikanOrangTua(data, b, a, hari);
			case "istri", "suami" -> nikahkan(data, a, b, hari);
			case "murid", "guru" -> Hubungan.ikat(a, b, JenisHubungan.GURU_MURID, 60, hari);
			case "sahabat", "rekan" -> Hubungan.ikat(a, b, JenisHubungan.SAHABAT, 75, hari);
			default -> Hubungan.ikat(a, b, JenisHubungan.SAHABAT, 60, hari); // blood kin outside the tree (aunt, nephew...)
		}
	}

	private static void jadikanOrangTua(WargaData data, Warga anak, Warga orangTua, long hari) {
		if (SiklusHidup.umurPada(orangTua, hari) - SiklusHidup.umurPada(anak, hari) < 14 || orangTua.id == anak.id) {
			return;
		}
		int lama = orangTua.pria() ? anak.ayah : anak.ibu;
		Warga lamaWarga = lama == Warga.TANPA || lama == orangTua.id ? null : data.byId(lama);
		if (lamaWarga != null) {
			lepasOrangTua(anak, lamaWarga);
		}
		if (orangTua.pria()) {
			anak.ayah = orangTua.id;
		} else {
			anak.ibu = orangTua.id;
		}
		orangTua.tambahAnak(anak.id);
		satuRumah(anak, orangTua);
	}

	private static void nikahkan(WargaData data, Warga a, Warga b, long hari) {
		if (a.kelamin == b.kelamin || a.pasangan == b.id) {
			return;
		}
		cerai(data, a);
		cerai(data, b);
		a.pasangan = b.id;
		b.pasangan = a.id;
		a.tunangan = Warga.TANPA;
		b.tunangan = Warga.TANPA;
		a.menikahHari = hari - 60L;
		b.menikahHari = hari - 60L;
		Hubungan.ikat(a, b, JenisHubungan.PASANGAN, 70, hari);
		satuRumah(a, b);
	}

	/** Ends the marriage of {@code w} (the story reassigns spouses; no divorce otherwise exists). */
	static void cerai(WargaData data, Warga w) {
		Warga p = w.pasangan == Warga.TANPA ? null : data.byId(w.pasangan);
		if (p != null && p.pasangan == w.id) {
			p.pasangan = Warga.TANPA;
			p.hapusHubungan(w.id);
		}
		w.hapusHubungan(w.pasangan);
		w.pasangan = Warga.TANPA;
	}

	private static boolean asli(Warga w) {
		return w.slot >= 0 && !PendudukTambahan.tambahan(w.slot);
	}

	/** Puts the two in one household when one of them has no generated slot (bed and house of a generated slot stay put). */
	private static void satuRumah(Warga a, Warga b) {
		if (asli(a) && asli(b) || a.rumahTangga == 0 && b.rumahTangga == 0) {
			return;
		}
		Warga jangkar = asli(a) || !asli(b) && a.rumahTangga != 0 ? a : b;
		Warga pindah = jangkar == a ? b : a;
		pindah.rumahTangga = jangkar.rumahTangga;
		pindah.rumah = jangkar.rumah;
	}

	// ================================================================== story deaths (42.10 Lao Geng, Nenek Tang)

	/** Ends the character's eternity and schedules a natural death ({@code kota42.pensiun}). */
	static boolean jadwalkanWafat(MinecraftServer server, Warga w, long hari) {
		if (w == null || !w.hidup()) {
			return false;
		}
		w.abadi = false;
		int umur = WargaApi.umur(w);
		long saat = hari + (umur >= 80 ? 1L : 3L + Math.floorMod(w.id, 9));
		KotaBicara.Data store = KotaBicara.Data.get(server);
		sub(store, K_PENS).putLong(Integer.toString(w.id), saat);
		store.ubah();
		WargaData.get(server).ubah();
		return true;
	}

	private static void pensiunHarian(MinecraftServer server, WargaData data, KotaBicara.Data store, WargaData.Situs s, long hari) {
		CompoundTag pens = sub(store, K_PENS);
		if (pens.isEmpty()) {
			return;
		}
		for (Warga w : new ArrayList<>(s.warga)) {
			String key = Integer.toString(w.id);
			if (pens.contains(key) && hari >= pens.getLongOr(key, Long.MAX_VALUE) && w.status == Warga.Status.HIDUP) {
				pens.remove(key);
				store.ubah();
				SiklusHidup.wafat(server, data, s.id, w, ArsipWarga.SEBAB_USIA, hari);
			}
		}
	}

	// ================================================================== bodies, names

	static void tick(MinecraftServer server) {
		if (defs.isEmpty() || !KotaBicara.aktif()) {
			return;
		}
		CompoundTag cast = sub(KotaBicara.Data.get(server), K_CAST);
		if (cast.isEmpty()) {
			return;
		}
		WargaData data = WargaData.get(server);
		for (String key : cast.keySet()) {
			Warga w = data.byId(cast.getIntOr(key, 0));
			if (w == null || w.status != Warga.Status.HIDUP || w.slot < 0) {
				continue;
			}
			TempatHuni huni = Pemanggil.huni(data.situsDari(w.id));
			KultivatorNpc body = huni == null ? null : huni.tubuhDari(w.slot);
			if (body != null && body.isAlive() && !key.equals(Tokoh.dari(body))) {
				tandaiTubuh(body, w);
			}
		}
	}

	static void lupakanSemua() {
		// the registry is data (kept); nothing else is cached in memory
	}

	/** Tags a named character's body so its 38 data dialogs apply, wears its look and plate. */
	static void tandaiTubuh(KultivatorNpc npc, Warga w) {
		if (npc == null || w == null || w.tokoh.isEmpty()) {
			return;
		}
		Def d = defs.get(w.tokoh);
		if (d == null || Tokoh.byId(d.id()) == null) {
			return;
		}
		boolean baru = !d.id().equals(Tokoh.dari(npc));
		npc.memory().putString("tokoh", d.id());
		npc.memory().putInt("watak", Math.floorMod(w.watak, Tokoh.WATAK.size()));
		if (baru) {
			npc.setPlate(Component.translatable("kultivator.kota.papan_nama", nama(w), gelar(w)));
			NpcRole peran = d.peran();
			String rupa = d.rupa().isEmpty() ? peran != null && peran != npc.role() && peran != NpcRole.WARGA ? peran.id() : "" : d.rupa();
			if (!rupa.isEmpty() && !rupa.equals(SosokApi.rupa(npc).kunci())) {
				SosokApi.setRupa(npc, rupa);
			}
		}
		Tokoh.lihat(npc);
	}

	/** The display name of {@code w} (a character's own name once cast). */
	static Component nama(Warga w) {
		if (!w.tokoh.isEmpty() && Tokoh.byId(w.tokoh) != null) {
			return Component.translatable("kultivator.cerita.tokoh." + w.tokoh);
		}
		return WargaApi.nama(w);
	}

	/** A named character's title, or null. */
	static @Nullable Component gelar(Warga w) {
		if (w.tokoh.isEmpty() || Tokoh.byId(w.tokoh) == null) {
			return null;
		}
		return Component.translatable("kultivator.cerita.tokoh." + w.tokoh + ".gelar");
	}

	// ================================================================== tests

	/** {@code uji kota_hidup tokoh} lines. */
	static List<Component> laporan(MinecraftServer server) {
		List<Component> out = new ArrayList<>();
		WargaData data = WargaData.get(server);
		CompoundTag cast = sub(KotaBicara.Data.get(server), K_CAST);
		out.add(Component.translatable("kultivator.kota42.bicara.tokoh.laporan", defs.size(), cast.size(),
				CeritaData.get(server).firstCapital()));
		for (Def d : defs.values()) {
			int id = cast.getIntOr(d.id(), 0);
			Warga w = id == 0 ? null : data.byId(id);
			if (d.hewan()) {
				out.add(Component.translatable("kultivator.kota42.bicara.tokoh.hewan", d.id()));
			} else if (id == 0) {
				out.add(Component.translatable("kultivator.kota42.bicara.tokoh.belum", d.id(), d.tempat()));
			} else if (w == null) {
				out.add(Component.translatable("kultivator.kota42.bicara.tokoh.wafat", d.id(), id));
			} else {
				out.add(Component.translatable("kultivator.kota42.bicara.tokoh.baris", d.id(), id, data.situsDari(id), WargaApi.umur(w),
						w.slot, w.abadi ? "abadi" : "fana"));
			}
		}
		return out;
	}

	private TokohKota() {
	}
}
