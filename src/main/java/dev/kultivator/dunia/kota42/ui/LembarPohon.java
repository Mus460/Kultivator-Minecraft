package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.ArsipWarga;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder of {@code warga_pohon} (DESIGN 42.11.3, keys {@link KotaLembar.Pohon}): the family tree around one person,
 * three generations up (parents, grandparents, great-grandparents with their spouses) and three down (children,
 * grandchildren, great-grandchildren with the spouses who married in), plus the centre's siblings. The living come from
 * {@code WargaData}, the dead and the departed from {@code ArsipWarga} (with their grave number). The server sends the
 * graph (at most {@value #MAKS} people); the client lays it out, pans and opens cards.
 */
public final class LembarPohon {

	private static final String L = KotaLembar.Pohon.LAYAR;
	/** Nodes per sheet (KotaLembar.Pohon: &le; 48). */
	static final int MAKS = 48;
	private static final int GEN = 3;

	static void init() {
		KotaLayar.daftar(L, LembarPohon::bangun);
		KotaLayar.bawaan(L, KotaLembar.Pohon.OP_PUSAT, (player, npcId, situs, op) -> pusatkan(player, LembarKunci.argInt(op, 1, 0)));
		KotaLayar.daftarTab(L, KotaLembar.Pohon.OP_PUSAT, (player, npcId, situs, op) -> {
			pusatkan(player, LembarKunci.argInt(op, 2, 0));
			KotaLayar.bukaJauh(player, L, null);
			return true;
		});
		KotaLayar.bawaan(L, KotaLembar.Pohon.OP_KARTU, (player, npcId, situs, op) -> bukaKartu(player, npcId, LembarKunci.argInt(op, 1, 0)));
		KotaLayar.daftarTab(L, KotaLembar.Pohon.OP_KARTU, (player, npcId, situs, op) -> bukaKartu(player, npcId, LembarKunci.argInt(op, 2, 0)));
		// the card's other tablets drawn on the tree screen jump back to the card of the centre
		for (String tab : KotaLembar.Kartu.TABS) {
			if (!KotaLembar.Kartu.TAB_POHON.equals(tab)) {
				KotaLayar.daftarTab(L, tab, (player, npcId, situs, op) -> {
					int pusat = pusat(player);
					if (pusat <= 0) {
						return false;
					}
					LembarKotaDasar.setPilih(player, KotaLembar.Kartu.LAYAR, Integer.toString(pusat));
					return LembarWarga.bukaTab(player, npcId, tab);
				});
			}
		}
	}

	private static void pusatkan(ServerPlayer player, int id) {
		if (id > 0) {
			LembarKotaDasar.setPilih(player, L, Integer.toString(id));
		}
	}

	private static boolean bukaKartu(ServerPlayer player, int npcId, int id) {
		if (id <= 0 || WargaApi.byId(id) == null) {
			KotaLayar.pesan(player, Component.translatable(KotaLayar.K + "pohon.arsip_saja"), LembarKunci.PESAN_PERINGATAN);
			return true;
		}
		return LembarWarga.buka(player, npcId, id);
	}

	private static int pusat(ServerPlayer player) {
		try {
			return Integer.parseInt(LembarKotaDasar.pilih(player, L));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situsSesi) {
		MinecraftServer server = player.level().getServer();
		WargaData data = WargaData.get(server);
		CompoundTag tag = LembarKotaDasar.lembar(player, null, LembarKunci.GAYA_KERTAS);
		int pusat = pusat(player);
		if (pusat <= 0) {
			Warga w = LembarWarga.warga(player, npcId);
			pusat = w == null ? 0 : w.id;
		}
		Warga tengah = data.byId(pusat);
		ArsipWarga.Entri tengahArsip = tengah == null ? ArsipWarga.cariDiMana(data, pusat) : null;
		if (tengah == null && tengahArsip == null) {
			LembarKotaDasar.kepala(tag, player, situsSesi);
			return LembarKotaDasar.kunci(tag, Component.translatable(KotaLayar.K + "kartu.tidak_ada"));
		}
		long situs = tengah != null ? data.situsDari(tengah.id) : 0L;
		LembarKotaDasar.kepala(tag, player, situs != 0L ? situs : situsSesi);
		CompoundTag judul = tengah != null ? null : LembarWarga.arsip(tengahArsip);
		Component nama = tengah != null ? WargaApi.nama(tengah) : LembarKunci.teks(judul, LembarKunci.NAMA);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(KotaLayar.K + "pohon.judul", nama));
		tag.putInt(KotaLembar.Pohon.PUSAT, pusat);
		Map<Integer, Integer> gen = new LinkedHashMap<>();
		boolean[] lebih = new boolean[2];
		kumpul(data, pusat, gen, lebih);
		ListTag simpul = new ListTag();
		for (Map.Entry<Integer, Integer> e : gen.entrySet()) {
			CompoundTag row = simpul(data, player, e.getKey(), e.getValue());
			if (row != null) {
				simpul.add(row);
			}
		}
		tag.put(KotaLembar.Pohon.SIMPUL, simpul);
		tag.putBoolean(KotaLembar.Pohon.NAIK, lebih[0]);
		tag.putBoolean(KotaLembar.Pohon.TURUN, lebih[1]);
		return tag;
	}

	/**
	 * Walks the family from the centre: ancestors and descendants up to {@value #GEN} generations, each one's spouse at
	 * the same generation, and the centre's siblings; {@code lebih[0]}/{@code lebih[1]} tell whether more exist above/below.
	 */
	private static void kumpul(WargaData data, int pusat, Map<Integer, Integer> gen, boolean[] lebih) {
		gen.put(pusat, 0);
		int pasangan = LembarWarga.pasanganDari(data, pusat);
		if (pasangan != Warga.TANPA) {
			gen.putIfAbsent(pasangan, 0);
		}
		Deque<int[]> naik = new ArrayDeque<>();
		naik.add(new int[] {pusat, 0});
		while (!naik.isEmpty() && gen.size() < MAKS) {
			int[] cur = naik.poll();
			int[] induk = LembarWarga.induk(data, cur[0]);
			for (int p : induk) {
				if (p == Warga.TANPA) {
					continue;
				}
				if (cur[1] - 1 < -GEN) {
					lebih[0] = true;
					continue;
				}
				if (gen.putIfAbsent(p, cur[1] - 1) == null) {
					naik.add(new int[] {p, cur[1] - 1});
				}
			}
		}
		for (int p : LembarWarga.induk(data, pusat)) {
			for (int c : LembarWarga.anakDari(data, p)) {
				if (gen.size() < MAKS) {
					gen.putIfAbsent(c, 0);
				}
			}
		}
		Deque<int[]> turun = new ArrayDeque<>();
		turun.add(new int[] {pusat, 0});
		if (pasangan != Warga.TANPA) {
			turun.add(new int[] {pasangan, 0});
		}
		while (!turun.isEmpty() && gen.size() < MAKS) {
			int[] cur = turun.poll();
			for (int c : LembarWarga.anakDari(data, cur[0])) {
				if (cur[1] + 1 > GEN) {
					lebih[1] = true;
					continue;
				}
				if (gen.size() >= MAKS) {
					lebih[1] = true;
					break;
				}
				if (gen.putIfAbsent(c, cur[1] + 1) == null) {
					turun.add(new int[] {c, cur[1] + 1});
					int ps = LembarWarga.pasanganDari(data, c);
					if (ps != Warga.TANPA && gen.size() < MAKS) {
						gen.putIfAbsent(ps, cur[1] + 1);
					}
				}
			}
		}
	}

	private static @Nullable CompoundTag simpul(WargaData data, ServerPlayer player, int id, int g) {
		CompoundTag row = LembarWarga.orang(data, player, id);
		if (row == null) {
			return null;
		}
		row.putInt(KotaLembar.Pohon.GEN, g);
		int[] induk = LembarWarga.induk(data, id);
		row.putInt(KotaLembar.Pohon.AYAH, induk[0]);
		row.putInt(KotaLembar.Pohon.IBU, induk[1]);
		row.putInt(KotaLembar.Pohon.PASANGAN, LembarWarga.pasanganDari(data, id));
		Warga w = data.byId(id);
		if (w != null) {
			row.putLong(KotaLembar.Pohon.LAHIR, LembarWarga.hariLahir(w.lahir));
			return row;
		}
		ArsipWarga.Entri e = ArsipWarga.cariDiMana(data, id);
		if (e != null) {
			row.putLong(KotaLembar.Pohon.LAHIR, LembarWarga.hariLahir(e.lahir()));
			if (e.wafat()) {
				row.putLong(KotaLembar.Pohon.WAFAT, e.akhir());
				if (e.nisan() > 0) {
					CompoundTag makam = new CompoundTag();
					long situs = situsArsip(data, id);
					LembarKunci.teks(makam, LembarKunci.NAMA, situs == 0L ? Component.translatable(KotaLayar.K + "pohon.makam")
							: Component.translatable(KotaLayar.K + "pohon.makam_di", KotaNames.town(situs)));
					makam.putInt(KotaLembar.Pohon.NOMOR, e.nisan());
					row.put(KotaLembar.Pohon.MAKAM, makam);
				}
			}
		}
		return row;
	}

	/** The place whose archive holds {@code id} (0 when none). */
	private static long situsArsip(WargaData data, int id) {
		for (WargaData.Situs s : data.semuaSitus()) {
			if (ArsipWarga.cari(s, id) != null) {
				return s.id;
			}
		}
		return 0L;
	}

	private LembarPohon() {
	}
}
