package dev.kultivator.dunia.kota42.cakap;

import com.google.gson.JsonObject;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.bicara.KotaBicara;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.util.Advancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import java.util.UUID;

/**
 * The conversation state machine of a player talking to a townsperson (DESIGN 42.5A.6, 42.5A.7):
 * <pre>
 * PEMBUKA -> BASA_BASI -> TOPIK <-> LANJUTAN -> PERMINTAAN -> REAKSI -> (TOPIK | KILAS_BALIK | PENUTUP)
 *    '-- KILAS_BALIK (promise due, debt, a strong event since the last meeting, a long absence) --'
 * any state: POTONG (danger nearby) -> PENUTUP
 * </pre>
 * Patience starts at {@code 40 + X/4 + impression/4 - busy x 30}, costs 8 per turn and 20 for a disliked topic, gains 10
 * for a topic of interest or a joke that lands; at 0 the townsperson closes politely. Each player choice is an intent and
 * a tone; the tone check ({@link PenilaiNada}) decides success and its effects flow into impressions, feelings, prices,
 * gossip, the law and Infami. The NPC answers with 1-3 utterances (X) and may carry the talk on along the topics'
 * {@code lanjut} chain. Live sessions are server memory only; the history per (warga, player) is {@link Pengetahuan}'s.
 */
public final class KeadaanPercakapan {

	/** Option count shown at most (42.5A.7: 3-6). */
	public static final int OPSI_MAKS = 6;
	/** Question topics a player can ask about (42.5.3 tablet Bicara), in display order. */
	public static final List<String> TANYA_TOPIK = List.of("kabar", "kerja", "keluarga", "dagang", "kota", "sekte", "cita");

	/** The live part of a session the frozen {@link SesiCakap} does not hold. */
	static final class Jalan {
		final SesiCakap sesi;
		final long situs;
		final int pemainIdx;
		@Nullable Kilasan.Kilas kilas;
		/** What the townsperson asked for or offered ({@code obat}, {@code uang}, a promise word), null when nothing pends. */
		@Nullable String kebutuhan;
		boolean rahasia;
		boolean info;
		@Nullable Niat niatTerakhir;
		@Nullable Nada nadaTerakhir;
		int hasil;
		final List<String> ditanya = new ArrayList<>();

		Jalan(SesiCakap sesi, long situs, int pemainIdx) {
			this.sesi = sesi;
			this.situs = situs;
			this.pemainIdx = pemainIdx;
		}
	}

	private static final Map<UUID, Jalan> JALAN = new HashMap<>();

	static void bersihkan() {
		JALAN.clear();
	}

	/** The live session of a player, or null. */
	static @Nullable SesiCakap aktif(UUID player) {
		Jalan j = JALAN.get(player);
		return j == null ? null : j.sesi;
	}

	// ================================================================== opening

	/** Opens a conversation (PEMBUKA or KILAS_BALIK) and plays the townsperson's first turn. */
	static SesiCakap buka(ServerPlayer player, KultivatorNpc npc, Warga w) {
		MinecraftServer server = player.level().getServer();
		Pengetahuan pk = Pengetahuan.get(server);
		WargaData data = WargaData.get(server);
		long now = server.overworld().getGameTime();
		long hari = KotaHidup.hari(server);
		long situs = npc.penduduk() ? npc.situsPenduduk() : npc.town();
		int idx = data.indeksPemain(player.getUUID());
		Ingatan ing = WargaApi.ingatan(w, player.getUUID());
		Topik.Situasi s = Topik.Situasi.bangun(server, w, player, null, situs, null);
		int sibuk = sibuk(s) ? 1 : 0;
		int kesabaran = 40 + s.sifat().x() / 4 + ing.kesan / 4 - sibuk * 30;
		SesiCakap sesi = new SesiCakap(player.getUUID(), npc.getId(), w, hari, Math.max(15, kesabaran));
		Jalan j = new Jalan(sesi, situs, idx);
		JALAN.put(player.getUUID(), j);
		Pengetahuan.Riwayat r = pk.riwayat(w, idx);
		SplittableRandom rng = acak(server, w, idx, hari, 0);
		List<Ujaran> turn = new ArrayList<>();
		if (bahaya(player, npc)) {
			sesi.tahap = SesiCakap.Tahap.POTONG;
			turn.add(ucap(server, j, Niat.POTONG, "bahaya", null, Map.of(), player, rng));
			sesi.tahap = SesiCakap.Tahap.PENUTUP;
		} else {
			Kilasan.Kilas kilas = KotaConfig.bicara().mesinLanjut ? Kilasan.cari(pk, w, r, ing, hari) : null;
			if (kilas != null) {
				sesi.tahap = SesiCakap.Tahap.KILAS_BALIK;
				j.kilas = kilas;
				turn.add(ucap(server, j, Niat.KILAS_BALIK, kilas.topik(), kilas.fakta(), kilas.isian(), player, rng));
				Kilasan.tandai(kilas);
				if (kilas.janji() != null) {
					j.kebutuhan = kilas.janji().apa.isEmpty() ? "janji" : kilas.janji().apa;
				} else if (kilas.jenis().equals("utang_pemain")) {
					j.kebutuhan = "uang";
				}
			} else {
				sesi.tahap = SesiCakap.Tahap.PEMBUKA;
				turn.add(ucap(server, j, Niat.SAPA, "sapa", null, Map.of(), player, rng));
				if (Giliran.panjang(s.sifat(), rng) > 1) {
					sesi.tahap = SesiCakap.Tahap.BASA_BASI;
					turn.add(ucap(server, j, Niat.BASA_BASI, basaBasiTopik(s), null, Map.of(), player, rng));
				}
			}
		}
		sesi.terakhir.clear();
		sesi.terakhir.addAll(turn);
		r.temuTick = now;
		siapkanOpsi(server, j, player);
		if (sesi.selesai()) {
			tutup(server, j);
		}
		pk.ubah();
		return sesi;
	}

	/** The small-talk topic of the moment: weather when it rains or storms, the season, else work. */
	private static String basaBasiTopik(Topik.Situasi s) {
		if (!s.cuaca().equals("cerah")) {
			return "cuaca";
		}
		return s.bekerja() ? "kerja" : "musim";
	}

	/** True when the townsperson is at work now (shopkeepers, workers, guards in daytime) or held by an event. */
	static boolean sibuk(Topik.Situasi s) {
		boolean siang = s.jam().equals("pagi") || s.jam().equals("siang") || s.jam().equals("sore");
		return siang && (s.kelompok().equals("pedagang") || s.kelompok().equals("pekerja") || s.kelompok().equals("penjaga"))
				|| !s.acara().isEmpty() && s.acara().stream().anyMatch(a -> a.contains("upacara") || a.contains("nikah") || a.contains("makam"));
	}

	private static boolean bahaya(ServerPlayer player, KultivatorNpc npc) {
		return player.level() instanceof ServerLevel level && HidupApi.bahayaDi(level, npc.blockPosition()) != null;
	}

	// ================================================================== a player choice

	/** Plays one player choice (intent + its topic + tone) and the townsperson's answer. */
	static Hasil pilih(SesiCakap sesi, Niat niat, @Nullable String topikOpsi, Nada nada) {
		Jalan j = JALAN.get(sesi.pemain);
		MinecraftServer server = KotaHidup.server();
		ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(sesi.pemain);
		if (j == null || j.sesi != sesi || player == null || sesi.selesai()) {
			return Hasil.tutup();
		}
		Warga w = sesi.warga;
		Pengetahuan pk = Pengetahuan.get(server);
		long hari = KotaHidup.hari(server);
		long now = server.overworld().getGameTime();
		sesi.giliran++;
		SplittableRandom rng = acak(server, w, j.pemainIdx, hari, sesi.giliran);
		Pengetahuan.Riwayat r = pk.riwayat(w, j.pemainIdx);
		Topik.Situasi s = Topik.Situasi.bangun(server, w, player, null, j.situs, null);
		if (!sesi.nadaBoleh.contains(nada)) {
			nada = Nada.SOPAN;
		}
		j.niatTerakhir = niat;
		j.nadaTerakhir = nada;
		sesi.kesabaran -= 8;
		// ---- the tone check (42.5A.7)
		boolean cek = !niat.equals(Niat.PAMIT);
		PenilaiNada.Faktor faktor = faktor(server, player, s, r, nada, hari);
		double peluang = cek ? PenilaiNada.peluang(nada, faktor) : -1.0D;
		boolean berhasil = !cek || rng.nextDouble() < peluang;
		if (cek) {
			r.pakaiNada(nada, hari);
			if (berhasil) {
				lidahPerak(server, player, nada);
			}
		}
		int kesan = 0;
		Component isyarat = null;
		PenilaiNada.Baris baris = CakapReload.nada().nada().get(nada);
		if (cek && baris != null) {
			Akibat ak = terapkan(server, player, j, s, berhasil ? baris.berhasil() : baris.gagal(), nada, berhasil, rng, hari, now);
			kesan += ak.kesan;
			isyarat = ak.isyarat;
		}
		if (nada == Nada.MENYUAP && berhasil) {
			NilaiBatu.pay(player, suap(player));
		}
		// ---- the intent and the answer turn
		List<Ujaran> turn = new ArrayList<>();
		kesan += jawab(server, player, j, s, niat, topikOpsi, nada, berhasil, rng, turn, hari, now);
		if (!sesi.selesai() && player.level().getEntity(sesi.npcId) instanceof KultivatorNpc body && bahaya(player, body)) {
			sesi.tahap = SesiCakap.Tahap.POTONG;
			turn.add(ucap(server, j, Niat.POTONG, "bahaya", null, Map.of(), player, rng));
			sesi.tahap = SesiCakap.Tahap.PENUTUP;
		}
		if (!sesi.selesai() && sesi.kesabaran <= 0) {
			sesi.tahap = SesiCakap.Tahap.PENUTUP;
			turn.add(ucap(server, j, Niat.TUTUP, "sabar_habis", null, Map.of(), player, rng));
		}
		if (!sesi.selesai()) {
			lanjutan(server, player, j, s, rng, turn);
		}
		sesi.terakhir.clear();
		sesi.terakhir.addAll(turn);
		j.hasil += Integer.signum(kesan);
		if (isyarat == null) {
			isyarat = isyarat(sesi, Topik.Situasi.bangun(server, w, player, null, j.situs, null));
		}
		siapkanOpsi(server, j, player);
		if (sesi.selesai()) {
			tutup(server, j);
		}
		pk.ubah();
		return new Hasil(berhasil, turn, kesan, sesi.tahap, sesi.selesai() ? List.of() : sesi.opsi, isyarat, (float) peluang, sesi.selesai());
	}

	/** Remembers the tones {@code player} has pulled off; the fourth different one grants {@code kota42_lidah_perak}. */
	private static void lidahPerak(MinecraftServer server, ServerPlayer player, Nada nada) {
		CompoundTag part = KotaBicara.Data.get(server).bagian("nada");
		String key = player.getUUID().toString();
		int had = part.getIntOr(key, 0);
		int bits = had | 1 << nada.ordinal();
		if (bits != had) {
			part.putInt(key, bits);
			KotaBicara.Data.get(server).ubah();
		}
		if (Integer.bitCount(bits) >= 4) {
			Advancements.grant(player, "tempat/kota42_lidah_perak");
		}
	}

	/** The NB a bribe costs: 5 x (realm band + 1), at least the {@code nb_min} of {@code nada.json}. */
	static long suap(ServerPlayer player) {
		int b = Cultivators.get(player).realm().ordinal() / 3;
		return Math.max(2L, 5L * (b + 1));
	}

	private static PenilaiNada.Faktor faktor(MinecraftServer server, ServerPlayer player, Topik.Situasi s, Pengetahuan.Riwayat r, Nada nada,
			long hari) {
		int namaBaik = KotaData.get(server).reputation(s.situs(), player.getUUID());
		int saksi = 0;
		if (player.level() instanceof ServerLevel level) {
			saksi = Math.max(0, level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(8.0D), KultivatorNpc::isAlive).size() - 1);
		}
		return new PenilaiNada.Faktor(s, namaBaik, TemaApi.infami(player), saksi, NilaiBatu.total(player), r.ulang(nada, hari), 30);
	}

	/** What the tone effects changed (for the result). */
	record Akibat(int kesan, @Nullable Component isyarat) {
	}

	/** Applies a tone's success/failure effects ({@code nada.json} effect keys). */
	static Akibat terapkan(MinecraftServer server, ServerPlayer player, Jalan j, Topik.Situasi s, JsonObject e, Nada nada, boolean berhasil,
			SplittableRandom rng, long hari, long now) {
		Warga w = j.sesi.warga;
		Pengetahuan pk = Pengetahuan.get(server);
		int kesan = CakapReload.angka(e, "kesan", 0);
		if (nada == Nada.BERCANDA && !berhasil && (s.label().equals("sedih") || s.hubungan().contains("duka"))) {
			kesan = Math.min(kesan, -10);
		}
		if (kesan != 0) {
			WargaApi.ubahKesan(w, player, kesan, "cakap_" + nada.id(), null);
		}
		j.sesi.kesabaran += CakapReload.angka(e, "kesabaran", 0);
		j.rahasia |= CakapReload.benar(e, "rahasia", false);
		j.info |= CakapReload.benar(e, "info", false);
		float harga = CakapReload.pecahan(e, "harga", 0.0F);
		if (harga != 0.0F) {
			pk.riwayat(w, j.pemainIdx).ubahHarga(harga, hari);
		}
		String emosi = CakapReload.teksAtauNull(e, "emosi");
		if (emosi != null) {
			Cakap.peristiwa(w, emosi, Fakta.Rujuk.pemain(j.pemainIdx), 1.0F);
		}
		String fakta = CakapReload.teksAtauNull(e, "fakta");
		if (fakta != null) {
			Predikat p = Predikat.dari(fakta);
			Fakta f = new Fakta(fakta, Fakta.Rujuk.pemain(j.pemainIdx), Fakta.Rujuk.warga(w.id), 0, Fakta.Kebenaran.BENAR, 100, Fakta.Asal.SAKSI,
					hari, hari, Lupa.kekuatanAwal(Fakta.Asal.SAKSI, p.valensi(), p, s.sifat().c()), 0, p.valensi(), -1);
			pk.simpan(w, f, hari);
			if (CakapReload.benar(e, "gosip", false) && player.level() instanceof ServerLevel level) {
				GosipWarga.lahir(level, j.situs, fakta, Fakta.Rujuk.pemain(j.pemainIdx), Fakta.Rujuk.warga(w.id));
			}
			if (nada == Nada.MERAYU) {
				// the spouse or family of the courted townsperson takes offence
				Warga spouse = w.pasangan == Warga.TANPA ? null : WargaApi.byId(w.pasangan);
				if (spouse != null) {
					WargaApi.ubahKesan(spouse, player, -8, "cakap_rayu_pasangan", null);
				}
			}
		}
		int hukum = CakapReload.angka(e, "hukum", 0);
		if (hukum > 0 && s.tempat().contains("kota_besar") && faktorSaksi(player) > 0) {
			KotaBesarApi.kejahatan(player, j.situs, "ancam", hukum);
		}
		int infami = CakapReload.angka(e, "infami", 0);
		if (infami != 0) {
			TemaApi.tambahInfami(player, infami, "cakap_" + nada.id());
		}
		String isy = CakapReload.teksAtauNull(e, "isyarat");
		return new Akibat(kesan, isy == null ? null : Component.translatable(KUNCI + "isyarat." + isy));
	}

	private static int faktorSaksi(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		return Math.max(0, level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(12.0D), KultivatorNpc::isAlive).size() - 1);
	}

	/** Prefix of 42K's own UI keys (fragment {@code kota_hidup_cakap_mesin}). */
	public static final String KUNCI = "kultivator.kota42.cakap.";

	/**
	 * The townsperson's answer to the intent (first utterance of its turn) and the state change; returns the impression
	 * change the intent itself caused.
	 */
	private static int jawab(MinecraftServer server, ServerPlayer player, Jalan j, Topik.Situasi s, Niat niat, @Nullable String topikOpsi,
			Nada nada, boolean berhasil,
			SplittableRandom rng, List<Ujaran> turn, long hari, long now) {
		SesiCakap sesi = j.sesi;
		Warga w = sesi.warga;
		Pengetahuan pk = Pengetahuan.get(server);
		String id = niat.id();
		int kesan = 0;
		switch (id) {
			case "PAMIT" -> {
				sesi.tahap = SesiCakap.Tahap.PENUTUP;
				turn.add(ucap(server, j, Niat.TUTUP, "pamit", null, Map.of(), player, rng));
			}
			case "TANYA" -> {
				String topik = topikOpsi;
				j.ditanya.add(topik == null ? "kabar" : topik);
				if (!berhasil && nada != Nada.SOPAN) {
					sesi.tahap = SesiCakap.Tahap.REAKSI;
					turn.add(ucap(server, j, Niat.TOLAK, topik, null, Map.of(), player, rng));
				} else {
					Fakta f = faktaUntuk(pk, w, s, topik, j, hari);
					String t = f != null ? Predikat.dari(f.predikat()).topik() : topik;
					sesi.tahap = sesi.tahap == SesiCakap.Tahap.TOPIK ? SesiCakap.Tahap.LANJUTAN : SesiCakap.Tahap.TOPIK;
					masukTopik(sesi, t);
					turn.add(ucap(server, j, Niat.BERI_KABAR, t, f, Map.of(), player, rng));
					if (f != null) {
						pk.ganti(w, f, Lupa.diceritakan(f, hari));
						pk.catat(j.pemainIdx, f, hari);
					}
					Topik.Data data = t == null ? null : CakapReload.topik().topik().get(t);
					if (data != null && minat(data, s)) {
						sesi.kesabaran += 10;
					} else if (data != null && data.golongan().contains("konflik") && s.sifat().a() > 20) {
						sesi.kesabaran -= 20;
					}
					if (t != null && (t.equals("kesehatan") || t.equals("uang")) && f != null && Pengetahuan.keluarga(w, f.subjek())) {
						j.kebutuhan = t.equals("kesehatan") ? "obat" : "uang";
					}
				}
			}
			case "BERI_KABAR" -> {
				String topik = topikOpsi;
				Fakta note = catatanBaru(pk, w, j, topik, hari);
				sesi.tahap = SesiCakap.Tahap.REAKSI;
				if (note != null) {
					Fakta.Asal asal = Fakta.Asal.pemain(j.pemainIdx);
					int trust = pk.kepercayaan(w, asal);
					Predikat p = Predikat.dari(note.predikat());
					pk.simpan(w, new Fakta(note.predikat(), note.subjek(), note.objek(), note.nilai(), note.kebenaran(), trust * note.yakin() / 100,
							asal, note.hariKejadian(), hari, Lupa.kekuatanAwal(asal, note.valensi(), p, s.sifat().c()), 0, note.valensi(),
							note.gosipId()), hari);
					if (note.gosipId() >= 0) {
						GosipWarga.kenal(w, note.gosipId());
					}
					kesan += 1;
					WargaApi.ubahKesan(w, player, 1, "cakap_kabar", null);
				}
				turn.add(ucap(server, j, Niat.REAKSI, note == null ? topik : Predikat.dari(note.predikat()).topik(), note, Map.of(), player, rng));
			}
			case "PERKENALAN" -> {
				Ingatan ing = WargaApi.ingatan(w, player.getUUID());
				ing.nama = true;
				WargaData.get(server).ubah();
				sesi.tahap = SesiCakap.Tahap.BASA_BASI;
				turn.add(ucap(server, j, Niat.REAKSI, "kenalan", null, Map.of("nama", player.getName()), player, rng));
			}
			case "PUJI" -> {
				sesi.tahap = SesiCakap.Tahap.REAKSI;
				if (berhasil) {
					Cakap.peristiwa(w, "dipuji", Fakta.Rujuk.pemain(j.pemainIdx), 1.0F);
					turn.add(ucap(server, j, Niat.TERIMA_KASIH, "puji", null, Map.of(), player, rng));
				} else {
					turn.add(ucap(server, j, Niat.REAKSI, "puji_ragu", null, Map.of(), player, rng));
				}
			}
			case "HINA" -> {
				sesi.tahap = SesiCakap.Tahap.REAKSI;
				Cakap.peristiwa(w, "dihina", Fakta.Rujuk.pemain(j.pemainIdx), 1.0F);
				WargaApi.ubahKesan(w, player, -10, "cakap_hina", null);
				kesan -= 10;
				sesi.kesabaran -= 20;
				turn.add(ucap(server, j, Niat.REAKSI, "hina", null, Map.of(), player, rng));
				if (WargaApi.ingatan(w, player.getUUID()).kesan <= -40) {
					sesi.tahap = SesiCakap.Tahap.PENUTUP;
					turn.add(ucap(server, j, Niat.TUTUP, "marah", null, Map.of(), player, rng));
				}
			}
			case "MINTA" -> {
				sesi.tahap = SesiCakap.Tahap.PERMINTAAN;
				if (berhasil) {
					pk.riwayat(w, j.pemainIdx).ubahHarga(-0.02F, hari);
					turn.add(ucap(server, j, new Niat("SEPAKAT"), "minta", null, Map.of(), player, rng));
				} else {
					turn.add(ucap(server, j, Niat.TOLAK, "minta", null, Map.of(), player, rng));
				}
			}
			case "TAWAR" -> {
				sesi.tahap = SesiCakap.Tahap.PERMINTAAN;
				if (berhasil) {
					float cut = -0.03F - rng.nextInt(6) / 100.0F;
					pk.riwayat(w, j.pemainIdx).ubahHarga(cut, hari);
					turn.add(ucap(server, j, new Niat("SEPAKAT"), "dagang", null, Map.of("persen", Math.round(-cut * 100)), player, rng));
				} else {
					sesi.kesabaran -= 5;
					turn.add(ucap(server, j, Niat.TAWAR, "dagang", null, Map.of(), player, rng));
				}
			}
			case "JANJI" -> {
				sesi.tahap = SesiCakap.Tahap.REAKSI;
				String apa = j.kebutuhan == null ? "" : j.kebutuhan;
				Pengetahuan.Riwayat r = pk.riwayat(w, j.pemainIdx);
				Pengetahuan.Janji lama = null;
				for (Pengetahuan.Janji x : r.janji) {
					if (x.terbuka() && x.apa.equals(apa)) {
						lama = x;
					}
				}
				if (lama != null) {
					// extending a promise: a day more, a little disappointment (42.5A.10)
					lama.batas = Math.max(lama.batas, hari) + 1L;
					WargaApi.ubahKesan(w, player, -2, "cakap_janji_ulur", null);
					kesan -= 2;
					Cakap.peristiwa(w, "janji_diundur", Fakta.Rujuk.pemain(j.pemainIdx), 1.0F);
				} else {
					Cakap.janji(w, player, "MEMBAWA_BARANG", hari + 2L, apa);
				}
				j.kebutuhan = null;
				turn.add(ucap(server, j, Niat.TERIMA_KASIH, "janji", null, Map.of(), player, rng));
			}
			case "TOLAK" -> {
				sesi.tahap = SesiCakap.Tahap.REAKSI;
				j.kebutuhan = null;
				WargaApi.ubahKesan(w, player, -1, "cakap_tolak", null);
				kesan -= 1;
				turn.add(ucap(server, j, Niat.REAKSI, "ditolak", null, Map.of(), player, rng));
			}
			default -> {
				sesi.tahap = SesiCakap.Tahap.REAKSI;
				turn.add(ucap(server, j, Niat.REAKSI, topikOpsi, null, Map.of(), player, rng));
			}
		}
		if (nada == Nada.BERCANDA && berhasil) {
			sesi.kesabaran += 10;
		}
		return kesan;
	}

	/** The townsperson goes on talking: 0-2 more utterances along the topic chain (42.5A.5 "Rantai lanjutan"). */
	private static void lanjutan(MinecraftServer server, ServerPlayer player, Jalan j, Topik.Situasi s, SplittableRandom rng, List<Ujaran> turn) {
		SesiCakap sesi = j.sesi;
		int more = Giliran.panjang(s.sifat(), rng) - 1;
		if (more <= 0 || sesi.kesabaran <= 20) {
			return;
		}
		Pengetahuan pk = Pengetahuan.get(server);
		for (int i = 0; i < more && turn.size() < 3; i++) {
			PerencanaTopik.Kandidat k = PerencanaTopik.pilih(kandidat(server, pk, j, s, player), s.sifat(), rng);
			if (k == null) {
				return;
			}
			Topik.Data data = CakapReload.topik().topik().get(k.topik());
			boolean tanya = data != null && data.golongan().contains("tanya");
			sesi.tahap = SesiCakap.Tahap.LANJUTAN;
			masukTopik(sesi, k.topik());
			turn.add(ucap(server, j, tanya ? Niat.TANYA : Niat.BERI_KABAR, k.topik(), k.fakta(), Map.of(), player, rng));
			if (k.fakta() != null) {
				pk.ganti(sesi.warga, k.fakta(), Lupa.diceritakan(k.fakta(), s.hari()));
				pk.catat(j.pemainIdx, k.fakta(), s.hari());
				if (k.fakta().predikat().equals("SAKIT") && Pengetahuan.keluarga(sesi.warga, k.fakta().subjek())) {
					j.kebutuhan = "obat";
				}
			}
		}
	}

	private static void masukTopik(SesiCakap sesi, @Nullable String topik) {
		if (topik != null) {
			sesi.topik = topik;
			sesi.topikRantai.add(topik);
		}
	}

	/** The planner candidates of the townsperson for this player now. */
	static List<PerencanaTopik.Kandidat> kandidat(MinecraftServer server, Pengetahuan pk, Jalan j, Topik.Situasi s, ServerPlayer player) {
		SesiCakap sesi = j.sesi;
		Pengetahuan.Riwayat r = pk.riwayat(sesi.warga, j.pemainIdx);
		Ingatan ing = WargaApi.ingatan(sesi.warga, player.getUUID());
		List<String> lanjut = new ArrayList<>();
		if (sesi.topik != null) {
			Topik.Data last = CakapReload.topik().topik().get(sesi.topik);
			if (last != null) {
				lanjut.addAll(last.lanjut());
			}
		}
		Map<String, Long> sejak = new LinkedHashMap<>();
		for (String t : CakapReload.topik().topik().keySet()) {
			long v = pk.sejakTopikPemain(j.pemainIdx, t, s.now());
			if (v != Long.MAX_VALUE) {
				sejak.put(t, v);
			}
		}
		return PerencanaTopik.kandidat(s, pk, new PerencanaTopik.Pendengar(r, ing, null, sejak, lanjut, sesi.topikRantai, j.rahasia));
	}

	/** The fact the townsperson answers a question topic with, or null for a context answer. */
	private static @Nullable Fakta faktaUntuk(Pengetahuan pk, Warga w, Topik.Situasi s, @Nullable String tanya, Jalan j, long hari) {
		List<String> topik = switch (tanya == null ? "kabar" : tanya) {
			case "keluarga" -> List.of("keluarga", "kesehatan", "duka");
			case "dagang" -> List.of("harga", "dagang", "kafilah", "uang");
			case "kota" -> List.of("kota", "acara", "bahaya", "tetangga", "pasar_hantu");
			case "sekte" -> List.of("sekte");
			case "kerja", "cita" -> List.of();
			default -> null;
		};
		if (topik != null && topik.isEmpty()) {
			return null;
		}
		Fakta best = null;
		double score = 0.0D;
		double rel = (s.nilaiKesan() + 100) / 200.0D;
		for (Fakta f : pk.ingat(w, hari)) {
			Predikat p = Predikat.dari(f.predikat());
			if (topik != null && !topik.contains(p.topik())) {
				continue;
			}
			if (p.rahasia() && rel < 0.7D && !j.rahasia) {
				continue;
			}
			if (tanya != null && tanya.equals("keluarga") && !Pengetahuan.keluarga(w, f.subjek()) && !Pengetahuan.keluarga(w, f.objek())) {
				continue;
			}
			double v = Lupa.menonjol(f, hari, 1.0D, false) * (j.info ? 1.5D : 1.0D);
			if (v > score) {
				score = v;
				best = f;
			}
		}
		return best;
	}

	/** A note of the player about {@code topik} the townsperson does not know yet, or null. */
	private static @Nullable Fakta catatanBaru(Pengetahuan pk, Warga w, Jalan j, @Nullable String topik, long hari) {
		List<Fakta> notes = pk.catatan(j.pemainIdx);
		for (int i = notes.size() - 1; i >= 0; i--) {
			Fakta f = notes.get(i);
			if ((topik == null || Predikat.dari(f.predikat()).topik().equals(topik)) && pk.sama(w, f) == null) {
				return f;
			}
		}
		return null;
	}

	private static boolean minat(Topik.Data t, Topik.Situasi s) {
		for (String m : t.minat()) {
			String key = m.toLowerCase(java.util.Locale.ROOT);
			if (key.equals(s.kelompok()) || key.equals(s.peran()) || s.kebiasaan().contains(key)) {
				return true;
			}
		}
		return false;
	}

	/** A mood hint instead of a percent (42.5A.7). */
	private static @Nullable Component isyarat(SesiCakap sesi, Topik.Situasi s) {
		String id = null;
		if (sesi.kesabaran < 25 && !sesi.selesai()) {
			id = "tidak_sabar";
		} else if (s.label().equals("marah")) {
			id = "marah";
		} else if (s.label().equals("cemas")) {
			id = "gelisah";
		} else if (s.label().equals("sedih")) {
			id = "murung";
		} else if (s.label().equals("gembira") || s.label().equals("puas")) {
			id = "senang";
		}
		return id == null ? null : Component.translatable(KUNCI + "isyarat." + id);
	}

	// ================================================================== options

	/** Builds the options and the allowed tones of the next choice. */
	private static void siapkanOpsi(MinecraftServer server, Jalan j, ServerPlayer player) {
		SesiCakap sesi = j.sesi;
		sesi.opsi.clear();
		sesi.nadaBoleh.clear();
		sesi.nadaBoleh.add(Nada.SOPAN);
		if (sesi.selesai()) {
			return;
		}
		Warga w = sesi.warga;
		Pengetahuan pk = Pengetahuan.get(server);
		long hari = KotaHidup.hari(server);
		Topik.Situasi s = Topik.Situasi.bangun(server, w, player, null, j.situs, null);
		Pengetahuan.Riwayat r = pk.riwayat(w, j.pemainIdx);
		PenilaiNada.Faktor f = faktor(server, player, s, r, Nada.SOPAN, hari);
		for (Nada n : Nada.values()) {
			if (PenilaiNada.boleh(n, f)) {
				sesi.nadaBoleh.add(n);
			}
		}
		List<SesiCakap.Opsi> head = new ArrayList<>();
		List<SesiCakap.Opsi> tail = new ArrayList<>();
		if (j.kebutuhan != null) {
			head.add(opsi(Niat.JANJI, j.kebutuhan, false));
			head.add(opsi(Niat.TOLAK, null, false));
		}
		Ingatan ing = WargaApi.ingatan(w, player.getUUID());
		if (!ing.nama) {
			tail.add(opsi(new Niat("PERKENALAN"), null, false));
		}
		Fakta note = catatanBaru(pk, w, j, null, hari);
		if (note != null) {
			tail.add(opsi(Niat.BERI_KABAR, Predikat.dari(note.predikat()).topik(), false));
		}
		if (w.peranNpc().kelompok == NpcRole.Kelompok.PEDAGANG) {
			tail.add(opsi(Niat.TAWAR, "dagang", false));
		}
		tail.add(opsi(Niat.PUJI, null, false));
		if (ing.kesan < 0 || sesi.tahap == SesiCakap.Tahap.REAKSI) {
			tail.add(opsi(Niat.HINA, null, false));
		}
		int room = OPSI_MAKS - 1 - head.size();
		List<SesiCakap.Opsi> tanya = new ArrayList<>();
		for (String t : TANYA_TOPIK) {
			if (j.ditanya.contains(t) || (t.equals("dagang") && w.peranNpc().kelompok != NpcRole.Kelompok.PEDAGANG && s.sifat().o() < 0)
					|| (t.equals("keluarga") && w.pasangan == Warga.TANPA && w.anak.length == 0 && w.ayah == Warga.TANPA)) {
				continue;
			}
			tanya.add(opsi(Niat.TANYA, t, false));
		}
		List<SesiCakap.Opsi> out = new ArrayList<>(head);
		int tanyaMaks = Math.max(1, Math.min(3, room - Math.min(tail.size(), room - 1)));
		for (int i = 0; i < tanya.size() && i < tanyaMaks; i++) {
			out.add(tanya.get(i));
		}
		for (SesiCakap.Opsi o : tail) {
			if (out.size() < OPSI_MAKS - 1) {
				out.add(o);
			}
		}
		out.add(opsi(Niat.PAMIT, null, false));
		sesi.opsi.addAll(out);
	}

	private static SesiCakap.Opsi opsi(Niat niat, @Nullable String topik, boolean terkunci) {
		String key = KUNCI + "opsi." + niat.kunci() + (topik == null ? "" : "." + topikLabel(niat, topik));
		return new SesiCakap.Opsi(niat, topik, Component.translatable(key), terkunci);
	}

	/** Label topic of an option: question topics as is, any other fact topic as {@code lain}. */
	private static String topikLabel(Niat niat, String topik) {
		if (niat.equals(Niat.TANYA) && TANYA_TOPIK.contains(topik)) {
			return topik;
		}
		if (niat.equals(Niat.TAWAR)) {
			return "dagang";
		}
		if (niat.equals(Niat.JANJI)) {
			return OPSI_JANJI.contains(topik) ? topik : "lain";
		}
		return OPSI_KABAR.contains(topik) ? topik : "lain";
	}

	/** Promise labels with their own text; others read "I'll take care of it". */
	static final List<String> OPSI_JANJI = List.of("obat", "uang", "janji");
	/** News topics with their own option text. */
	static final List<String> OPSI_KABAR = List.of("harga", "bahaya", "acara", "sekte", "kejahatan", "pasar_hantu", "duka", "keluarga");

	// ================================================================== utterances and closing

	/** One utterance of the townsperson in this session (anti-repeat rings and topic memory updated). */
	private static Ujaran ucap(MinecraftServer server, Jalan j, Niat niat, @Nullable String topik, @Nullable Fakta fakta,
			Map<String, Object> isian, ServerPlayer player, SplittableRandom rng) {
		Warga w = j.sesi.warga;
		Topik.Situasi s = Topik.Situasi.bangun(server, w, player, null, j.situs, fakta == null ? null : fakta.predikat());
		Ujaran u = Giliran.ujaran(new Giliran.Permintaan(niat, topik, s, fakta, isian, player, null, rng));
		Pengetahuan pk = Pengetahuan.get(server);
		int jam = (int) (s.now() / Emosi.JAM);
		String key = u.akar().kunci();
		if (!key.isEmpty()) {
			pk.cincinWarga(w).catat(key, jam);
			pk.cincinPemain(j.pemainIdx).catat(key, jam);
		}
		if (topik != null) {
			pk.topikDibahas(j.pemainIdx, topik, s.now());
		}
		return u;
	}

	/** Ends a session: history entry, last meeting. */
	static void tutup(MinecraftServer server, Jalan j) {
		Pengetahuan pk = Pengetahuan.get(server);
		SesiCakap sesi = j.sesi;
		sesi.tahap = SesiCakap.Tahap.PENUTUP;
		Pengetahuan.Riwayat r = pk.riwayat(sesi.warga, j.pemainIdx);
		long now = server.overworld().getGameTime();
		r.tambah(new Pengetahuan.Catatan(KotaHidup.hari(server), now, sesi.topikRantai, j.niatTerakhir == null ? "" : j.niatTerakhir.id(),
				j.nadaTerakhir == null ? "" : j.nadaTerakhir.id(), Integer.signum(j.hasil)));
		r.temuHari = KotaHidup.hari(server);
		r.temuTick = now;
		JALAN.remove(sesi.pemain, j);
		pk.ubah();
	}

	/** Ends the live session of a player (screen closed, walked away). */
	static void tutup(MinecraftServer server, UUID player) {
		Jalan j = JALAN.get(player);
		if (j != null) {
			tutup(server, j);
		}
	}

	private static SplittableRandom acak(MinecraftServer server, Warga w, int pemain, long hari, int giliran) {
		return Cakap.acak(server.overworld().getSeed(), w.id, pemain, hari, giliran, "cakap");
	}

	private KeadaanPercakapan() {
	}
}
