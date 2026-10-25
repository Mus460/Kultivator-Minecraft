package dev.kultivator.dunia.kota42.bicara;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ekonomi.PasarHantu;
import dev.kultivator.dunia.kota42.ekonomi.RumahKota;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Kebiasaan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.tema.bersama.Dagang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Asking the way (DESIGN 42.5.5, contract C42.B1). Tablet Jalan holds three lists, each row a full spoken answer:
 * <ul>
 * <li><b>Layanan di tempat ini</b>: the workplaces of the place's keepers (eating house, inn, healer, money house, Yamen,
 * warehouse, guilds, library, temple...), the 42 titik ({@code sumur}, {@code rekrut}, {@code lumbung}, {@code panggung},
 * {@code sidang}, {@code meja_dadu}), tonight's Ghost Market (only a sly teller, a gambler, a peeper or a pickpocket knows),
 * and the player's own houses here;</li>
 * <li><b>Orang</b>: named characters of the place and the residents the player has met, answered from their schedule
 * right now ("jam segini dia di rumah makan");</li>
 * <li><b>Tempat lain</b>: towns, villages, sect gates and trading posts within 3000 blocks of the place, and caves within
 * 1500 that the world already knows.</li>
 * </ul>
 * An answer is a direction of 8 (from where the teller stands), a landmark (district, a well, a stage, the chess tree...)
 * and a distance in langkah (under 300 blocks, rounded) or li (500 blocks). Children and newcomers under 7 days sometimes do
 * not know and send the player to the well gossip; a sly teller wants 2 NB first. [Tandai] puts a 5-minute marker
 * {@code kota/tujuan} on the world map and keeps an action-bar compass to it for those 5 minutes.
 */
public final class TanyaJalan {

	/** Blocks of one li (42.5.5). */
	public static final int LI = 500;
	/** Under this many blocks the answer counts langkah. */
	public static final int LANGKAH_MAKS = 300;
	/** Marker life in ticks (5 minutes). */
	public static final int TANDA_TICK = 5 * 60 * 20;
	/** NB a sly teller asks. */
	public static final long BIAYA_LICIK = 2L;
	/** Site kind of the marker ({@code DuniaMarkers}). */
	public static final String JENIS_TANDA = "kota/tujuan";

	/** What [Tandai] did, for the sheet. */
	record Tanda(@Nullable Component teks, @Nullable Component pesan, boolean ok, boolean dibayar) {
	}

	/** One target before it is phrased: id, name, icon, position, and for people the warga id. */
	private record Sasaran(String id, Component nama, int ikon, BlockPos pos, int warga, @Nullable Component kegiatan) {
	}

	/** The service a keeper's work spot stands for: its name key and the 38 UI icon. */
	private record Layanan(String id, int ikon) {
	}

	private static final Map<NpcRole, Layanan> LAYANAN = new HashMap<>();
	private static final Map<String, Layanan> LAYANAN_TITIK = new HashMap<>();

	static {
		layanan(NpcRole.KOKI, "rumah_makan", LembarKunci.Ikon.RUMAH_MAKAN);
		layanan(NpcRole.PEMILIK_PENGINAPAN, "penginapan", LembarKunci.Ikon.PENGINAPAN);
		layanan(NpcRole.TABIB, "tabib", LembarKunci.Ikon.TABIB);
		layanan(NpcRole.BANKIR, "rumah_uang", LembarKunci.Ikon.BANK);
		layanan(NpcRole.PIALANG, "rumah_dagang", LembarKunci.Ikon.BANK);
		layanan(NpcRole.HAKIM, "yamen", LembarKunci.Ikon.YAMEN);
		layanan(NpcRole.PENJAGA_GUDANG, "gudang", LembarKunci.Ikon.KAFILAH);
		layanan(NpcRole.ALKEMIS, "gilda_alkemis", LembarKunci.Ikon.GILDA_ALKEMIS);
		layanan(NpcRole.EMPU, "gilda_tempa", LembarKunci.Ikon.GILDA_TEMPA);
		layanan(NpcRole.PEDAGANG_SENJATA, "toko_senjata", LembarKunci.Ikon.TOKO_SENJATA);
		layanan(NpcRole.PETUGAS_GILDA, "gilda_misi", LembarKunci.Ikon.GILDA_MISI);
		layanan(NpcRole.PUSTAKAWAN, "perpustakaan", LembarKunci.Ikon.PERPUSTAKAAN);
		layanan(NpcRole.PENJAGA_FORMASI, "teleport", LembarKunci.Ikon.TELEPORT);
		layanan(NpcRole.PEDAGANG_BINATANG, "pasar_binatang", LembarKunci.Ikon.PASAR_BINATANG);
		layanan(NpcRole.BANDAR_BATU, "judi_batu", LembarKunci.Ikon.JUDI_BATU);
		layanan(NpcRole.JURU_LELANG, "lelang", LembarKunci.Ikon.LELANG);
		layanan(NpcRole.PEMILIK_TEH, "kedai_teh", LembarKunci.Ikon.RUMAH_MAKAN);
		layanan(NpcRole.PENDONGENG, "kedai_teh", LembarKunci.Ikon.RUMAH_MAKAN);
		layanan(NpcRole.PEDAGANG_PIL, "toko_pil", LembarKunci.Ikon.PIL);
		layanan(NpcRole.PANDAI_PUSAKA, "pandai", LembarKunci.Ikon.GILDA_TEMPA);
		layanan(NpcRole.PANDAI_DESA, "pandai", LembarKunci.Ikon.GILDA_TEMPA);
		layanan(NpcRole.KEPALA_BIARA, "kuil", LembarKunci.Ikon.KUIL);
		layanan(NpcRole.PENCERAMAH_FANA, "perpustakaan", LembarKunci.Ikon.PERPUSTAKAAN);
		layanan(NpcRole.SYAHBANDAR, "dermaga", LembarKunci.Ikon.DERMAGA);
		layanan(NpcRole.PENJUAL_IKAN, "pasar_ikan", LembarKunci.Ikon.RUMAH_MAKAN);
		layanan(NpcRole.PEDAGANG_BAZAR, "bazar", LembarKunci.Ikon.LELANG);
		layanan(NpcRole.AHLI_TEH, "kedai_teh", LembarKunci.Ikon.RUMAH_MAKAN);
		layanan(NpcRole.NYONYA_SUTRA, "toko_sutra", LembarKunci.Ikon.LELANG);
		LAYANAN_TITIK.put(KotaTitik.SUMUR, new Layanan("sumur", LembarKunci.Ikon.TIDAK_ADA));
		LAYANAN_TITIK.put(KotaTitik.REKRUT, new Layanan("kantor_rekrut", LembarKunci.Ikon.PANGKAT_E));
		LAYANAN_TITIK.put(KotaTitik.LUMBUNG, new Layanan("lumbung", LembarKunci.Ikon.KAFILAH));
		LAYANAN_TITIK.put(KotaTitik.PANGGUNG, new Layanan("panggung", LembarKunci.Ikon.LONCENG));
		LAYANAN_TITIK.put(KotaTitik.SIDANG, new Layanan("sidang", LembarKunci.Ikon.TIMBANGAN));
		LAYANAN_TITIK.put(KotaTitik.MEJA_DADU, new Layanan("guanpu", LembarKunci.Ikon.JUDI_BATU));
	}

	private static void layanan(NpcRole role, String id, int ikon) {
		LAYANAN.put(role, new Layanan(id, ikon));
	}

	/** Landmark titik kinds an answer may name, nearest within {@link #PATOKAN_JARAK} of the target. */
	private static final List<String> PATOKAN = List.of(KotaTitik.SUMUR, KotaTitik.PANGGUNG, KotaTitik.CATUR, KotaTitik.LUMBUNG,
			KotaTitik.NAMA_JALAN, KotaTitik.PANJI);
	private static final int PATOKAN_JARAK = 24;

	/** Live compass of a player toward a marked target. */
	private record Kompas(BlockPos pos, Component nama, long sampai, long situs) {
	}

	private static final Map<UUID, Kompas> KOMPAS = new HashMap<>();
	/** Place targets cached per site for 10 seconds (services, other places); people are always live. */
	private record Cache(long tick, List<Sasaran> daftar) {
	}

	private static final Map<Long, Cache> CACHE_LAYANAN = new HashMap<>();
	private static final Map<Long, Cache> CACHE_TEMPAT = new HashMap<>();
	private static final int CACHE_TICK = 200;

	static void lupakan(UUID player) {
		KOMPAS.remove(player);
	}

	static void lupakanSemua() {
		KOMPAS.clear();
		CACHE_LAYANAN.clear();
		CACHE_TEMPAT.clear();
	}

	// ================================================================== C42.B1

	/**
	 * The answer of {@code npc} for target {@code tujuan}: a titik kind or service id ({@code tabib}, {@code sumur}), a
	 * resident's name (or part of it), or a site id / place name. The "I do not know" line when nothing matches or the
	 * teller does not know.
	 */
	public static Component jawab(KultivatorNpc npc, String tujuan) {
		Warga w = npc == null ? null : WargaApi.dari(npc);
		MinecraftServer server = KotaHidup.server();
		if (w == null || server == null || tujuan == null || !(npc.level() instanceof ServerLevel level)) {
			return Component.translatable("kultivator.kota42.jalan.tidak_tahu");
		}
		if (!tahu(server, w)) {
			return tolakan(server, w);
		}
		String q = tujuan.toLowerCase(Locale.ROOT).trim();
		long situs = BicaraWarga.situs(npc);
		List<Sasaran> all = new ArrayList<>();
		all.addAll(layananTempat(level, situs, w, null));
		all.addAll(orang(level, situs, w, null));
		all.addAll(tempatLain(server, situs));
		for (Sasaran s : all) {
			if (s.id.equalsIgnoreCase(q) || s.id.endsWith(":" + q) || s.nama.getString().toLowerCase(Locale.ROOT).contains(q)) {
				return teks(level, npc, s, situs);
			}
		}
		return Component.translatable("kultivator.kota42.jalan.tidak_tahu");
	}

	// ================================================================== sheet

	/** Tablet Jalan ({@link KotaLembar.Dialog#JALAN}). {@code dibayar}: the sly teller's 2 NB were paid this talk. */
	static CompoundTag lembar(ServerPlayer player, KultivatorNpc npc, Warga w, boolean dibayar) {
		CompoundTag c = new CompoundTag();
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level();
		boolean tahu = tahu(server, w);
		c.putBoolean(KotaLembar.Dialog.TAHU, tahu);
		if (!tahu) {
			LembarKunci.teks(c, LembarKunci.TEKS, tolakan(server, w));
			return c;
		}
		long biaya = biaya(w);
		boolean terkunci = biaya > 0L && !dibayar;
		if (biaya > 0L) {
			c.putLong(LembarKunci.BIAYA, dibayar ? 0L : biaya);
		}
		long situs = BicaraWarga.situs(npc);
		c.put(KotaLembar.Dialog.LAYANAN, baris(level, npc, situs, layananTempat(level, situs, w, player.getUUID()), terkunci));
		c.put(KotaLembar.Dialog.ORANG, baris(level, npc, situs, orang(level, situs, w, player), terkunci));
		c.put(KotaLembar.Dialog.TEMPAT_LAIN, baris(level, npc, situs, tempatLain(server, situs), terkunci));
		if (terkunci) {
			LembarKunci.teks(c, LembarKunci.TEKS, Component.translatable("kultivator.kota42.bicara.jalan.minta_uang", biaya));
		}
		return c;
	}

	private static ListTag baris(ServerLevel level, KultivatorNpc npc, long situs, List<Sasaran> list, boolean terkunci) {
		ListTag out = new ListTag();
		for (Sasaran s : list) {
			if (out.size() >= 12) {
				break;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, s.id);
			LembarKunci.teks(row, LembarKunci.NAMA, s.nama);
			row.putInt(LembarKunci.IKON, s.ikon);
			if (s.warga > 0) {
				row.putInt(KotaLembar.WARGA, s.warga);
				if (s.kegiatan != null) {
					LembarKunci.teks(row, KotaLembar.SAMPAI, s.kegiatan);
				}
			}
			if (terkunci) {
				LembarKunci.teks(row, LembarKunci.TEKS, Component.translatable("kultivator.kota42.bicara.jalan.terkunci"));
				row.putBoolean(LembarKunci.TERKUNCI, true);
			} else {
				int dx = s.pos.getX() - npc.getBlockX();
				int dz = s.pos.getZ() - npc.getBlockZ();
				row.putInt(KotaLembar.ARAH, sektor(dx, dz));
				row.putInt(LembarKunci.JARAK, (int) Math.sqrt((double) dx * dx + (double) dz * dz));
				Component patokan = patokan(level, situs, s.pos);
				if (patokan != null) {
					LembarKunci.teks(row, KotaLembar.PATOKAN, patokan);
				}
				LembarKunci.teks(row, LembarKunci.TEKS, teks(level, npc, s, situs));
				row.putInt(LembarKunci.X, s.pos.getX());
				row.putInt(LembarKunci.Z, s.pos.getZ());
			}
			out.add(row);
		}
		return out;
	}

	/** Rows the tablet would list (test command). */
	static int jumlah(ServerPlayer player, KultivatorNpc npc, Warga w) {
		long situs = BicaraWarga.situs(npc);
		ServerLevel level = player.level();
		return layananTempat(level, situs, w, player.getUUID()).size() + orang(level, situs, w, player).size()
				+ tempatLain(level.getServer(), situs).size();
	}

	// ================================================================== who knows

	/**
	 * False for a child under 10 or a newcomer of less than 7 days on half of the days (deterministic by warga and day),
	 * true otherwise.
	 */
	static boolean tahu(MinecraftServer server, Warga w) {
		long hari = KotaHidup.hari(server);
		int umur = WargaApi.umur(w);
		boolean baru = w.datang >= 0L && hari - w.datang < 7L;
		if (umur >= 10 && !baru) {
			return true;
		}
		long h = (w.id * 0x9E3779B97F4A7C15L) ^ (hari * 0xBF58476D1CE4E5B9L);
		return ((h ^ (h >>> 29)) & 1L) == 0L;
	}

	/** The refusal: a newcomer points to the well's gossip (by name when one lives here), a child says it is not allowed. */
	private static Component tolakan(MinecraftServer server, Warga w) {
		if (WargaApi.umur(w) < 10) {
			return Component.translatable("kultivator.kota42.bicara.jalan.anak_tidak_tahu");
		}
		Warga gosip = null;
		for (Warga other : WargaApi.wargaTempat(WargaData.get(server).situsDari(w.id))) {
			if (other != w && other.status == Warga.Status.HIDUP && (Kebiasaan.punya(other, "gosip_sumur") || "bibi_wang".equals(other.tokoh))) {
				gosip = other;
				if ("bibi_wang".equals(other.tokoh)) {
					break;
				}
			}
		}
		return gosip == null ? Component.translatable("kultivator.kota42.bicara.jalan.baru_umum")
				: Component.translatable("kultivator.kota42.bicara.jalan.baru_tanya", TokohKota.nama(gosip));
	}

	/** 2 NB from a sly ({@code licik}) teller, else nothing. */
	static long biaya(Warga w) {
		return "licik".equals(Tokoh.WATAK.get(Math.floorMod(w.watak, Tokoh.WATAK.size()))) ? BIAYA_LICIK : 0L;
	}

	/** True for a teller who knows tonight's Ghost Market. */
	private static boolean tahuPasarHantu(Warga w) {
		NpcRole role = w.peranNpc();
		return biaya(w) > 0L || role == NpcRole.PENCOPET || role == NpcRole.MAKELAR_GELAP || Kebiasaan.punya(w, "penjudi")
				|| Kebiasaan.punya(w, "pengintip");
	}

	// ================================================================== targets

	private static List<Sasaran> layananTempat(ServerLevel level, long situs, Warga teller, @Nullable UUID player) {
		long now = level.getGameTime();
		Cache cached = CACHE_LAYANAN.get(situs);
		if (cached == null || now - cached.tick() >= CACHE_TICK || now < cached.tick()) {
			cached = new Cache(now, layananBaru(level, situs));
			CACHE_LAYANAN.put(situs, cached);
		}
		List<Sasaran> out = new ArrayList<>(cached.daftar());
		TempatHuni huni = Pemanggil.huni(situs);
		MinecraftServer server = level.getServer();
		if (huni != null && tahuPasarHantu(teller)) {
			int n = PasarHantu.lokasi(situs, KotaHidup.hari(server));
			if (n > 0) {
				List<Titik> spot = huni.titik(KotaTitik.PASAR_HANTU + n);
				if (!spot.isEmpty()) {
					out.add(new Sasaran("l:pasar_hantu", Component.translatable("kultivator.kota42.bicara.jalan.layanan.pasar_hantu",
							PasarHantu.namaLokasi(PasarHantu.jenisLokasi(n))), LembarKunci.Ikon.MATA_BATIN, spot.get(0).pos(), 0, null));
				}
			}
		}
		if (player != null) {
			for (RumahKota.Rumah r : RumahKota.milik(player)) {
				if (r.situs() == situs) {
					BlockPos c = r.kotak().getCenter();
					out.add(new Sasaran("l:rumah_" + r.id(), Component.translatable("kultivator.kota42.bicara.jalan.layanan.rumahku"),
							LembarKunci.Ikon.PENGINAPAN, c, 0, null));
				}
			}
		}
		return out;
	}

	/** Services of a place: the work spot of each service keeper (one per service), then the 42 titik. */
	private static List<Sasaran> layananBaru(ServerLevel level, long situs) {
		List<Sasaran> out = new ArrayList<>();
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni == null) {
			return out;
		}
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Penduduk p : huni.penduduk()) {
			Layanan l = LAYANAN.get(p.peran());
			if (l == null || seen.contains(l.id)) {
				continue;
			}
			Titik t = huni.kerja(p);
			if (t == null) {
				continue;
			}
			seen.add(l.id);
			out.add(new Sasaran("l:" + l.id, Component.translatable("kultivator.kota42.bicara.jalan.layanan." + l.id), l.ikon, t.pos(), 0, null));
		}
		for (Map.Entry<String, Layanan> e : LAYANAN_TITIK.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
			List<Titik> spots = huni.titik(e.getKey());
			if (!spots.isEmpty() && seen.add(e.getValue().id)) {
				out.add(new Sasaran("l:" + e.getValue().id, Component.translatable("kultivator.kota42.bicara.jalan.layanan." + e.getValue().id),
						e.getValue().ikon, spots.get(0).pos(), 0, null));
			}
		}
		return out;
	}

	/** Named characters of the place first, then residents the player met, then the teller's family. */
	private static List<Sasaran> orang(ServerLevel level, long situs, Warga teller, @Nullable ServerPlayer player) {
		List<Sasaran> out = new ArrayList<>();
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni == null) {
			return out;
		}
		List<Warga> pilih = new ArrayList<>();
		List<Warga> semua = WargaApi.wargaTempat(situs);
		for (Warga w : semua) {
			if (w != teller && w.status == Warga.Status.HIDUP && !w.tokoh.isEmpty()) {
				pilih.add(w);
			}
		}
		if (player != null) {
			for (Warga w : semua) {
				if (w == teller || w.status != Warga.Status.HIDUP || pilih.contains(w)) {
					continue;
				}
				int idx = WargaData.get(level.getServer()).cariPemain(player.getUUID());
				Ingatan i = idx < 0 ? null : w.ingatanDari(idx);
				if (i != null && i.kenal > 0) {
					pilih.add(w);
				}
			}
		}
		for (Warga w : semua) {
			boolean family = w != teller && w.status == Warga.Status.HIDUP && (w.id == teller.pasangan || w.id == teller.ayah || w.id == teller.ibu
					|| teller.rumahTangga != 0 && w.rumahTangga == teller.rumahTangga);
			if (family && !pilih.contains(w)) {
				pilih.add(w);
			}
		}
		long waktu = level.getServer().overworld().getOverworldClockTime();
		long now = level.getGameTime();
		int jam = (int) Math.floorMod(waktu / 1000L + 6L, 24L);
		for (Warga w : pilih) {
			if (out.size() >= 12) {
				break;
			}
			BlockPos pos = null;
			Component kegiatan = null;
			if (w.slot != Warga.TANPA_SLOT) {
				KultivatorNpc body = huni.tubuhDari(w.slot);
				Penduduk p = huni.orang(w.slot);
				if (p != null) {
					Kegiatan k = huni.kegiatan(p, waktu, now);
					kegiatan = kegiatan(k);
					if (body == null) {
						TempatHuni.Tujuan t = huni.tujuan(level, p, k, jam, false);
						pos = t != null ? t.pos() : huni.rumahPos(p);
					}
				}
				if (body != null) {
					pos = body.blockPosition();
				}
			}
			if (pos != null) {
				out.add(new Sasaran("o:" + w.id, TokohKota.nama(w), LembarKunci.Ikon.TIDAK_ADA, pos, w.id, kegiatan));
			}
		}
		return out;
	}

	/** "di rumah", "sedang bekerja", "di kedai", "di pasar", else "sedang berjalan-jalan". */
	private static Component kegiatan(Kegiatan k) {
		String to = k.tujuan();
		String id;
		if (Kegiatan.KASUR.equals(to) || Kegiatan.RUMAH.equals(to) || k.dalam()) {
			id = "rumah";
		} else if (Kegiatan.KERJA.equals(to)) {
			id = "kerja";
		} else if (Kegiatan.KEDAI.equals(to)) {
			id = "kedai";
		} else if (to.contains("lapak") || to.contains("pasar") || to.contains("kios")) {
			id = "pasar";
		} else if (to.contains("kuil") || to.contains("doa")) {
			id = "kuil";
		} else {
			id = "jalan";
		}
		return Component.translatable("kultivator.kota42.bicara.jalan.kegiatan." + id);
	}

	/** Towns, villages, sect gates and posts within 3000 blocks; known caves within 1500. Nearest first, at most 12. */
	private static List<Sasaran> tempatLain(MinecraftServer server, long situs) {
		long now = server.overworld().getGameTime();
		Cache cached = CACHE_TEMPAT.get(situs);
		if (cached != null && now - cached.tick() < CACHE_TICK && now >= cached.tick()) {
			return cached.daftar();
		}
		BlockPos here = Dagang.posSitus(server, situs);
		List<Sasaran> out = new ArrayList<>();
		if (here != null) {
			List<DuniaSites.Site> sites = new ArrayList<>();
			for (DuniaSites.Site s : DuniaSites.all(server)) {
				if (s.id() == situs || s.dim() != Level.OVERWORLD) {
					continue;
				}
				String k = s.kind();
				boolean place = k.startsWith("kota/") || k.startsWith("kota_tema/") || k.startsWith("kampung/") || k.equals("sekte/gerbang")
						|| k.equals("pos/dagang") || k.equals("pos/singgah_besar");
				boolean cave = k.startsWith("gua/");
				double d2 = s.pos().distSqr(here);
				if (place && d2 <= 3000L * 3000L || cave && d2 <= 1500L * 1500L) {
					sites.add(s);
				}
			}
			sites.sort((a, b) -> Double.compare(a.pos().distSqr(here), b.pos().distSqr(here)));
			for (DuniaSites.Site s : sites) {
				if (out.size() >= 12) {
					break;
				}
				Component nama = s.kind().startsWith("kota") || s.kind().startsWith("pos/") ? Dagang.namaSitus(server, s.id())
						: Component.translatable("kultivator.dunia.marker." + s.kind().replace('/', '.'));
				out.add(new Sasaran("t:" + s.id(), nama, LembarKunci.Ikon.TIDAK_ADA, s.pos(), 0, null));
			}
		}
		List<Sasaran> list = List.copyOf(out);
		CACHE_TEMPAT.put(situs, new Cache(now, list));
		return list;
	}

	// ================================================================== phrasing

	/** "Ke timur, lewat Pasar Timur dekat sumur, kira-kira 200 langkah." */
	private static Component teks(ServerLevel level, KultivatorNpc npc, Sasaran s, long situs) {
		int dx = s.pos.getX() - npc.getBlockX();
		int dz = s.pos.getZ() - npc.getBlockZ();
		int jarak = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
		if (jarak < 8) {
			return s.warga > 0 ? Component.translatable("kultivator.kota42.bicara.jalan.di_sini_orang", s.nama)
					: Component.translatable("kultivator.kota42.bicara.jalan.di_sini", s.nama);
		}
		Component arah = Component.translatable("kultivator.cerita.arah." + ARAH[sektor(dx, dz)]);
		Component jauh = jarak < LANGKAH_MAKS ? Component.translatable("kultivator.kota42.bicara.jalan.langkah", Math.max(10, Math.round(jarak / 10.0F) * 10))
				: Component.translatable("kultivator.kota42.bicara.jalan.li", Math.max(1, Math.round(jarak / (float) LI)));
		Component patokan = patokan(level, situs, s.pos);
		if (s.warga > 0 && s.kegiatan != null) {
			return patokan == null ? Component.translatable("kultivator.kota42.bicara.jalan.orang", s.nama, s.kegiatan, arah, jauh)
					: Component.translatable("kultivator.kota42.bicara.jalan.orang_patokan", s.nama, s.kegiatan, arah, patokan, jauh);
		}
		return patokan == null ? Component.translatable("kultivator.kota42.bicara.jalan.arah", s.nama, arah, jauh)
				: Component.translatable("kultivator.kota42.bicara.jalan.arah_patokan", s.nama, arah, patokan, jauh);
	}

	private static final String[] ARAH = {"utara", "timur_laut", "timur", "tenggara", "selatan", "barat_daya", "barat", "barat_laut"};

	/** 0 = north (-z), clockwise. */
	static int sektor(int dx, int dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		return (int) Math.floorMod(Math.round(angle / 45.0D), 8L);
	}

	/** "lewat Pasar Timur, dekat sumur": the district of the target and the nearest landmark titik within 24 blocks. */
	private static @Nullable Component patokan(ServerLevel level, long situs, BlockPos pos) {
		TempatInfo info = TempatApi.tempatDi(level, pos);
		Component distrik = info == null || info.distrik() == null || info.situsId() != situs ? null
				: Component.translatable("kultivator.kota_besar.distrik." + info.distrik());
		Component dekat = null;
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni != null) {
			double best = PATOKAN_JARAK * PATOKAN_JARAK;
			for (String kind : PATOKAN) {
				for (Titik t : huni.titik(kind)) {
					double d = t.pos().distSqr(pos);
					if (d < best && d > 4.0D) {
						best = d;
						dekat = Component.translatable("kultivator.kota42.bicara.jalan.patokan." + kind);
					}
				}
			}
		}
		if (distrik != null && dekat != null) {
			return Component.translatable("kultivator.kota42.bicara.jalan.patokan_dua", distrik, dekat);
		}
		if (distrik != null) {
			return Component.translatable("kultivator.kota42.bicara.jalan.patokan_distrik", distrik);
		}
		return dekat;
	}

	// ================================================================== [Tandai]

	/**
	 * [Tandai] on row {@code id}: pays a sly teller first (2 NB to the town chest), then speaks the answer pointing that way,
	 * puts the 5-minute map marker {@code kota/tujuan} and starts the action-bar compass.
	 */
	static Tanda tandai(ServerPlayer player, KultivatorNpc npc, Warga w, String id, boolean dibayar) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level();
		if (!tahu(server, w)) {
			sayTidakTahu(npc);
			return new Tanda(tolakan(server, w), null, false, false);
		}
		long situs = BicaraWarga.situs(npc);
		boolean bayar = false;
		long biaya = biaya(w);
		if (biaya > 0L && !dibayar) {
			if (!NilaiBatu.pay(player, biaya)) {
				return new Tanda(null, Component.translatable("kultivator.kota42.bicara.jalan.kurang_uang", biaya), false, false);
			}
			KasKota.pajak(server, situs, biaya, "tip");
			bayar = true;
		}
		Sasaran target = null;
		List<Sasaran> all = new ArrayList<>(layananTempat(level, situs, w, player.getUUID()));
		all.addAll(orang(level, situs, w, player));
		all.addAll(tempatLain(server, situs));
		for (Sasaran s : all) {
			if (s.id.equals(id)) {
				target = s;
				break;
			}
		}
		if (target == null) {
			return new Tanda(null, Component.translatable("kultivator.kota42.bicara.jalan.hilang"), false, bayar);
		}
		Component answer = teks(level, npc, target, situs);
		npc.say(answer, 120);
		HidupApi.klip(npc, GerakId.TUNJUK, 0, Math.max(40, GerakId.durasiSekali(GerakId.TUNJUK, 0)));
		if (VoiceEvents.exists("kota42.tanya_jalan")) {
			Voice.say(npc, "kota42.tanya_jalan", null);
		}
		long until = level.getGameTime() + TANDA_TICK;
		DuniaSites.Site site = DuniaSites.publishLive(level, JENIS_TANDA, target.pos, 0, until);
		DuniaSites.discover(player, site.id());
		KOMPAS.put(player.getUUID(), new Kompas(target.pos.immutable(), target.nama, until, situs));
		return new Tanda(answer, Component.translatable("kultivator.kota42.bicara.jalan.ditandai", target.nama), true, bayar);
	}

	private static void sayTidakTahu(KultivatorNpc npc) {
		if (VoiceEvents.exists("kota42.tidak_tahu")) {
			Voice.say(npc, "kota42.tidak_tahu", null);
		}
		HidupApi.klip(npc, GerakId.ANGGUK_DENGAR, 0, 40);
	}

	/** Every second: the compass of each marked target in the action bar (every 2 s), ended on arrival or after 5 minutes. */
	static void tick(MinecraftServer server, long now) {
		if (KOMPAS.isEmpty() || now % 40L != 0L) {
			return;
		}
		List<UUID> done = new ArrayList<>();
		for (Map.Entry<UUID, Kompas> e : KOMPAS.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Kompas k = e.getValue();
			if (player == null || now >= k.sampai || player.level().dimension() != Level.OVERWORLD) {
				done.add(e.getKey());
				continue;
			}
			int dx = k.pos.getX() - player.getBlockX();
			int dz = k.pos.getZ() - player.getBlockZ();
			int jarak = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
			if (jarak <= 6) {
				Msg.actionBar(player, Component.translatable("kultivator.kota42.bicara.jalan.sampai", k.nama).withStyle(ChatFormatting.GREEN));
				done.add(e.getKey());
				continue;
			}
			if (TempatLayar.layarTerbuka(player) != null) {
				continue;
			}
			Msg.actionBar(player, Component.translatable("kultivator.kota42.bicara.jalan.kompas", k.nama, jarak,
					Component.translatable("kultivator.cerita.arah." + ARAH[sektor(dx, dz)])).withStyle(ChatFormatting.GOLD));
		}
		for (UUID id : done) {
			KOMPAS.remove(id);
		}
	}

	private TanyaJalan() {
	}
}
