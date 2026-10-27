package dev.kultivator.dunia.kota42;

import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.kota42.bicara.KotaBicara;
import dev.kultivator.dunia.kota42.ekonomi.RumahKota;
import dev.kultivator.dunia.kota42.warga.ArsipWarga;
import dev.kultivator.dunia.kota42.warga.NamaWarga;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server sender of {@code kota_titik} (DESIGN 42.13.2), a proposal of 42V-F for the finish agent: copy this file to
 * {@code src/main/java/dev/kultivator/dunia/kota42/TitikSiar.java} and call {@code TitikSiar.init()} from {@code KotaHidup.init()}.
 * It compiles against the build classes as is (checked by javac). It sends every visual titik of the place a player is in (or
 * near) once per chunk per session, within 128 blocks, packed as the client fx expect (the client class
 * {@code client/dunia/kota42/fx/TitikKode} documents the 16 bits; {@link #pak} here is the same packing, the server never
 * references the client class).
 * The titik come from the loaded pieces of the place ({@link TempatApi#titik}); the cache of a place is rebuilt every minute so
 * chunks that load later are sent as soon as they appear. House slots are the {@code RumahKota} ids (0-254; none above).
 */
public final class TitikSiar {

	private static final int KAIN_SPANDUK = 3;
	private static final int KAIN_PINTU = 4;
	private static final List<String> WARNA = List.of("merah", "putih", "hijau", "biru", "sekte");
	private static final List<String> KAIN = List.of("panji", "papan", "bendera_arak", "spanduk", "kertas_pintu");
	private static final List<String> PAJANGAN = List.of("mangkuk", "mi", "teko", "botol_pil", "herba", "kain", "pedang", "papan", "ikan",
			"bakpao", "lentera_kertas", "gulungan", "jimat", "keranjang", "jala", "guci_arak", "dadu_cawan", "bunga", "potret_almarhum",
			"uang_kertas", "dacing");

	private record Potongan(long chunk, List<KotaPayloads.EntriTitik> titik, List<String> teks) {
	}

	private record Isi(long tick, List<Potongan> potongan) {
	}

	private static final Map<Long, Isi> CACHE = new HashMap<>();
	/** Per player: the chunk and the hash of what was sent (a changed gravestone or street name is sent again). */
	private static final Map<UUID, Map<Long, Integer>> TERKIRIM = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(TitikSiar::tik);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TERKIRIM.remove(handler.getPlayer().getUUID()));
	}

	private static void tik(MinecraftServer server) {
		if (server.getTickCount() % 40L != 13L) {
			return;
		}
		ServerLevel level = server.overworld();
		for (ServerPlayer player : level.players()) {
			TempatInfo info = KotaHidupCommands.tempatPemain(player);
			if (info == null) {
				continue;
			}
			long now = server.getTickCount();
			Isi isi = CACHE.get(info.situsId());
			if (isi == null || now - isi.tick() > 1200L) {
				isi = new Isi(now, bangun(level, info));
				CACHE.put(info.situsId(), isi);
			}
			Map<Long, Integer> sudah = TERKIRIM.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
			for (Potongan p : isi.potongan()) {
				double dx = ChunkPos.getX(p.chunk()) * 16 + 8 - player.getX();
				double dz = ChunkPos.getZ(p.chunk()) * 16 + 8 - player.getZ();
				if (dx * dx + dz * dz > 128.0D * 128.0D || Integer.valueOf(p.hashCode()).equals(sudah.get(p.chunk()))) {
					continue;
				}
				sudah.put(p.chunk(), p.hashCode());
				KotaPayloads.kirim(player, new KotaPayloads.TitikVisual(info.situsId(), p.chunk(), p.titik(), p.teks()));
			}
		}
	}

	/** The visual titik of the place, grouped by chunk. */
	private static List<Potongan> bangun(ServerLevel level, TempatInfo info) {
		long situs = info.situsId();
		Map<Long, List<KotaPayloads.EntriTitik>> perChunk = new HashMap<>();
		Map<Long, List<String>> teks = new HashMap<>();
		List<RumahKota.Info> rumah = RumahKota.daftar(level.getServer(), situs);
		List<Titik> semua = TempatApi.titik(level, situs, "");
		WargaData.Situs kota = WargaData.get(level.getServer()).peek(situs);
		String jalanPemain = KotaBicara.namaJalan(level.getServer(), situs);
		Titik papanJalan = jalanPemain == null ? null : papanTengah(semua, info);
		for (Titik t : semua) {
			String j = t.jenis();
			byte jenis;
			int sub = 0;
			int ekstra = 0;
			int ref = 255;
			long chunk = ChunkPos.pack(t.pos());
			List<String> tk = teks.computeIfAbsent(chunk, k -> new ArrayList<>());
			if (j.equals(KotaTitik.LENTERA_GANTUNG)) {
				jenis = KotaPayloads.TITIK_LENTERA;
				sub = Math.max(0, WARNA.indexOf(t.data().getOrDefault("warna", "merah")));
				ekstra = Math.max(0, Math.min(4, t.angka("jenis", 0)));
				ref = slot(rumah, t);
			} else if (j.equals(KotaTitik.CEROBONG)) {
				jenis = KotaPayloads.TITIK_CEROBONG;
				sub = Math.max(0, Math.min(3, t.data().containsKey("jenis") ? t.angka("jenis", 0) : jenisCerobong(semua, t)));
				ref = slot(rumah, t);
			} else if (j.equals(KotaTitik.PANJI) || j.equals(KotaTitik.SPANDUK_A) || j.equals(KotaTitik.SPANDUK_B)) {
				boolean spanduk = !j.equals(KotaTitik.PANJI);
				jenis = spanduk ? KotaPayloads.TITIK_SPANDUK : KotaPayloads.TITIK_PANJI;
				sub = spanduk ? KAIN_SPANDUK : Math.max(0, KAIN.indexOf(t.data().getOrDefault("kain", "panji")));
				ekstra = j.equals(KotaTitik.SPANDUK_B) ? 1 : 0;
				if (sub == KAIN_PINTU) {
					ref = slot(rumah, t);
				} else if (t.data().containsKey("glif") && tk.size() < KotaPayloads.TEKS_MAKS) {
					tk.add(t.data().get("glif"));
					ref = tk.size() - 1;
				}
			} else if (j.equals(KotaTitik.NAMA_JALAN) || j.startsWith(KotaTitik.NISAN)) {
				boolean nisan = j.startsWith(KotaTitik.NISAN);
				jenis = nisan ? KotaPayloads.TITIK_NISAN : KotaPayloads.TITIK_NAMA_JALAN;
				String nama = t.data().getOrDefault(nisan ? "nama" : "jalan", "");
				if (nisan && nama.isEmpty() && kota != null) {
					nama = namaNisan(kota, KotaTitik.nomor(j));
				} else if (!nisan && t == papanJalan) {
					nama = jalanPemain;
				}
				if (!nama.isEmpty() && tk.size() < KotaPayloads.TEKS_MAKS) {
					tk.add(nama);
					ref = tk.size() - 1;
				}
			} else if (j.equals(KotaTitik.KUCING_TIDUR)) {
				jenis = KotaPayloads.TITIK_KUCING_TIDUR;
			} else if (j.startsWith(KotaTitik.PAJANGAN)) {
				int i = PAJANGAN.indexOf(j.substring(KotaTitik.PAJANGAN.length()));
				if (i < 0) {
					continue;
				}
				jenis = KotaPayloads.TITIK_PAJANGAN;
				sub = i & 7;
				ekstra = i >> 3 & 7;
			} else {
				continue;
			}
			List<KotaPayloads.EntriTitik> list = perChunk.computeIfAbsent(chunk, k -> new ArrayList<>());
			if (list.size() < KotaPayloads.TITIK_MAKS) {
				list.add(new KotaPayloads.EntriTitik((short) (t.pos().getX() & 15), (short) t.pos().getY(), (short) (t.pos().getZ() & 15), jenis,
						pak(hadap(t.hadap()), sub, ekstra, ref)));
			}
		}
		List<Potongan> out = new ArrayList<>();
		for (Map.Entry<Long, List<KotaPayloads.EntriTitik>> e : perChunk.entrySet()) {
			out.add(new Potongan(e.getKey(), List.copyOf(e.getValue()), List.copyOf(teks.get(e.getKey()))));
		}
		return out;
	}

	/** The chimney kind 0 house / 1 business / 2 smithy from the work points around it (the kit writes no {@code jenis}). */
	private static int jenisCerobong(List<Titik> semua, Titik cerobong) {
		int jenis = 0;
		for (Titik k : semua) {
			String alat = k.alat();
			if (alat.isEmpty()) {
				continue;
			}
			double dx = k.pos().getX() - cerobong.pos().getX();
			double dz = k.pos().getZ() - cerobong.pos().getZ();
			if (dx * dx + dz * dz > 64.0D || Math.abs(k.pos().getY() - cerobong.pos().getY()) > 12) {
				continue;
			}
			if (alat.equals("palu")) {
				return 2;
			}
			if (USAHA.contains(alat)) {
				jenis = 1;
			}
		}
		return jenis;
	}

	private static final List<String> USAHA = List.of("konter", "timbangan", "rak", "teko", "wajan", "kukus", "meja_tulis", "tenun", "gunting");

	/** The street-name post nearest the middle of the place: the one a player's street name goes on. */
	private static Titik papanTengah(List<Titik> semua, TempatInfo info) {
		Titik terbaik = null;
		double jauh = Double.MAX_VALUE;
		for (Titik t : semua) {
			if (!t.jenis().equals(KotaTitik.NAMA_JALAN)) {
				continue;
			}
			double d = info == null ? 0.0D : t.pos().distToCenterSqr(info.kotak().getCenter().getX(), t.pos().getY(), info.kotak().getCenter().getZ());
			if (d < jauh) {
				jauh = d;
				terbaik = t;
			}
		}
		return terbaik;
	}

	/** The name cut on gravestone {@code n} (the person resting there; Chinese characters), or "" while the stone is blank. */
	private static String namaNisan(WargaData.Situs kota, int n) {
		ArsipWarga.Entri e = ArsipWarga.diNisan(kota, n);
		if (e == null || !e.wafat()) {
			return "";
		}
		Warga hantu = new Warga(e.id());
		hantu.marga = e.marga();
		hantu.nama = e.nama();
		hantu.generasi = e.generasi();
		hantu.kelamin = e.wanita() ? Warga.WANITA : Warga.PRIA;
		return NamaWarga.hanzi(hantu);
	}

	/** The 16 bits of {@code EntriTitik.data}: hadap 0-1, sub 2-4, ekstra 5-7, ref 8-15 (255 none). */
	static short pak(int hadap, int sub, int ekstra, int ref) {
		return (short) (hadap & 3 | (sub & 7) << 2 | (ekstra & 7) << 5 | (ref & 255) << 8);
	}

	private static int hadap(Direction d) {
		return switch (d) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
	}

	/** The id (0-254) of the house box holding the titik, else 255. */
	private static int slot(List<RumahKota.Info> rumah, Titik t) {
		for (RumahKota.Info r : rumah) {
			if (r.kotak().isInside(t.pos()) && r.id() < 255) {
				return r.id();
			}
		}
		return 255;
	}

	private TitikSiar() {
	}
}
