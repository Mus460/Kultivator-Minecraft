package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.kota42.ekonomi.RumahKota;
import dev.kultivator.dunia.kota42.ekonomi.TokoPemain;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Pekerjaan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builder of {@code rumah} (DESIGN 42.11.5, keys {@link KotaLembar.Rumah}): houses for sale or rent in the town of the
 * header, the player's houses in every town (rent paid to, unpaid tax, key, sale value, branch, shop) and household help
 * (the hired and the unemployed who could be hired, with temperament, skill and weekly wage). The selected house shows its
 * price factors, a floor plan read from the loaded world on the server (walls, floor, doors; never client chunks), the
 * neighbours whose bodies stand around it and the checklist of 42C ({@code RumahKota.syaratDaftar}).
 * <p>
 * Ids: {@code SITUS_N} (an op argument cannot hold ':', {@code LembarKunci.op}). Every action is 42C's
 * {@code RumahKota} method; a remote session only reads.
 */
public final class LembarRumah {

	private static final String L = KotaLembar.Rumah.LAYAR;
	/** Largest plan edge in cells (KotaLembar: &le; 32). */
	private static final int DENAH_MAKS = 32;
	private static final double TETANGGA_JARAK = 20.0D;

	static void init() {
		KotaLayar.daftar(L, LembarRumah::bangun);
		KotaLayar.bawaan(L, KotaLembar.OP_PILIH, (player, npcId, situs, op) -> LembarKotaDasar.setPilih(player, L, LembarKunci.arg(op, 1)));
		KotaLayar.bawaan(L, KotaLembar.Rumah.OP_BELI, (player, npcId, situs, op) -> rumah(player, op, (s, id) -> RumahKota.beli(player, s, id)));
		KotaLayar.bawaan(L, KotaLembar.Rumah.OP_SEWA, (player, npcId, situs, op) ->
				rumah(player, op, (s, id) -> RumahKota.sewa(player, s, id, minggu(op))));
		KotaLayar.bawaan(L, KotaLembar.Rumah.OP_PERPANJANG, (player, npcId, situs, op) ->
				rumah(player, op, (s, id) -> RumahKota.perpanjang(player, s, id, minggu(op))));
		KotaLayar.bawaan(L, KotaLembar.Rumah.OP_BAYAR_PAJAK, (player, npcId, situs, op) -> rumah(player, op, (s, id) -> RumahKota.bayarPajak(player, s, id)));
		KotaLayar.bawaan(L, KotaLembar.Rumah.OP_KUNCI, (player, npcId, situs, op) -> rumah(player, op, (s, id) -> RumahKota.cetakUlang(player, s, id, false)));
		KotaLayar.bawaan(L, KotaLembar.Rumah.OP_JUAL, (player, npcId, situs, op) -> {
			LembarKotaDasar.setPilih(player, L, LembarKunci.arg(op, 1));
			KotaLayar.pesan(player, Component.translatable(KotaLayar.K + "rumah.jual_tanya"), LembarKunci.PESAN_PERINGATAN);
		});
		KotaLayar.bawaan(L, KotaLembar.Rumah.OP_JUAL_YA, (player, npcId, situs, op) -> rumah(player, op, (s, id) -> RumahKota.jual(player, s, id)));
		KotaLayar.bawaan(L, KotaLembar.Rumah.OP_PEKERJAKAN, (player, npcId, situs, op) -> {
			long[] r = id(LembarKunci.arg(op, 2));
			if (r == null) {
				return;
			}
			hasil(player, RumahKota.pekerjakan(player, r[0], (int) r[1], LembarKunci.argInt(op, 1, 0)));
		});
		KotaLayar.bawaan(L, KotaLembar.Rumah.OP_BERHENTIKAN, (player, npcId, situs, op) -> {
			int warga = LembarKunci.argInt(op, 1, 0);
			for (RumahKota.Rumah r : RumahKota.milik(player.getUUID())) {
				RumahKota.Info info = RumahKota.info(player.level().getServer(), r.situs(), r.id());
				if (info != null && info.pembantu() == warga) {
					hasil(player, RumahKota.berhentikan(player, r.situs(), r.id()));
					return;
				}
			}
		});
	}

	@FunctionalInterface
	private interface Tindakan {
		RumahKota.Hasil jalan(long situs, int id);
	}

	private static void rumah(ServerPlayer player, String[] op, Tindakan t) {
		long[] r = id(LembarKunci.arg(op, 1));
		if (r == null) {
			return;
		}
		LembarKotaDasar.setPilih(player, L, r[0] + "_" + r[1]);
		hasil(player, t.jalan(r[0], (int) r[1]));
	}

	private static void hasil(ServerPlayer player, RumahKota.Hasil h) {
		KotaLayar.pesan(player, h.pesan(), h.ok() ? LembarKunci.PESAN_OK : LembarKunci.PESAN_BAHAYA);
	}

	private static int minggu(String[] op) {
		return Math.max(1, Math.min(RumahKota.MINGGU_MAKS, LembarKunci.argInt(op, 2, 1)));
	}

	/** {@code SITUS_N} (or {@code SITUS:N}) -> {situs, n}; null when malformed. */
	static @Nullable long[] id(String arg) {
		String s = arg.replace(':', '_');
		int cut = s.lastIndexOf('_');
		if (cut <= 0) {
			return null;
		}
		try {
			return new long[] {Long.parseLong(s.substring(0, cut)), Long.parseLong(s.substring(cut + 1))};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situs) {
		if (npcId >= 0 && !KotaLayar.melayani(player, npcId, L)) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, null, LembarKunci.GAYA_KAIN);
		LembarKotaDasar.kepala(tag, player, situs);
		if (situs != 0L) {
			LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable(KotaLayar.K + "rumah.judul", KotaNames.town(situs)));
		}
		List<RumahKota.Info> pasar = situs == 0L ? List.of() : RumahKota.dijual(server, situs);
		long uang = tag.getLongOr(LembarKunci.UANG, 0L);
		ListTag daftar = new ListTag();
		for (RumahKota.Info info : pasar) {
			if (daftar.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			daftar.add(baris(server, player, info, uang));
		}
		tag.put(LembarKunci.DAFTAR, daftar);
		String pilih = LembarKotaDasar.pilih(player, L);
		RumahKota.Info dipilih = null;
		long[] pid = id(pilih);
		if (pid != null) {
			dipilih = RumahKota.info(server, pid[0], (int) pid[1]);
		}
		if (dipilih == null && !pasar.isEmpty()) {
			dipilih = pasar.get(0);
		}
		if (dipilih == null) {
			RumahKota.Info punya = situs == 0L ? null : RumahKota.milikDiTempat(server, situs, player.getUUID());
			dipilih = punya;
		}
		if (dipilih != null) {
			tag.putString(KotaLembar.PILIH, dipilih.situs() + "_" + dipilih.id());
			tag.put(KotaLembar.RINCI, rinci(server, player, dipilih));
		}
		tag.put(KotaLembar.Rumah.MILIK, milik(server, player));
		tag.put(KotaLembar.Rumah.PEMBANTU, pembantu(server, player, situs));
		tag.putInt(KotaLembar.Rumah.RUMAH_MAKS, KotaConfig.ekonomi().rumahMaks);
		tag.putInt(KotaLembar.Rumah.RUMAH_JUMLAH, RumahKota.jumlah(server, player.getUUID()));
		return tag;
	}

	private static String jenis(String ukuran) {
		return switch (ukuran) {
			case "kecil" -> KotaLembar.Rumah.RJ_KECIL;
			case "besar" -> KotaLembar.Rumah.RJ_BESAR;
			case "rumah_toko", "toko" -> KotaLembar.Rumah.RJ_RUMAH_TOKO;
			case "paviliun" -> KotaLembar.Rumah.RJ_PAVILIUN;
			default -> KotaLembar.Rumah.RJ_SEDANG;
		};
	}

	private static CompoundTag baris(MinecraftServer server, ServerPlayer player, RumahKota.Info info, long uang) {
		CompoundTag t = new CompoundTag();
		t.putString(LembarKunci.ID, info.situs() + "_" + info.id());
		LembarKunci.teks(t, LembarKunci.NAMA, RumahKota.nama(server, info));
		t.putString(LembarKunci.JENIS, jenis(info.ukuran()));
		t.putInt(KotaLembar.Rumah.KASUR, info.kasur());
		t.putInt(KotaLembar.LEBAR, info.lebar());
		t.putInt(KotaLembar.PANJANG, info.panjang());
		long harga = RumahKota.harga(server, info);
		t.putLong(LembarKunci.HARGA, harga);
		t.putLong(KotaLembar.Rumah.SEWA, RumahKota.sewa(server, info));
		t.putLong(KotaLembar.Rumah.PAJAK, RumahKota.pajak(server, info));
		t.putString(KotaLembar.Rumah.SUMBER, info.sumber());
		Component alasan = null;
		for (Object[] s : RumahKota.syaratDaftar(player, info)) {
			if (!(Boolean) s[0] && alasan == null) {
				alasan = (Component) s[1];
			}
		}
		String keadaan = alasan != null ? LembarKunci.K_TERKUNCI : uang < RumahKota.sewa(server, info) ? LembarKunci.K_KURANG_UANG : LembarKunci.K_OK;
		t.putString(LembarKunci.KEADAAN, keadaan);
		if (alasan != null) {
			LembarKunci.teks(t, LembarKunci.ALASAN, alasan);
		}
		t.putInt(LembarKunci.X, info.pintu().getX());
		t.putInt(LembarKunci.Z, info.pintu().getZ());
		return t;
	}

	private static CompoundTag rinci(MinecraftServer server, ServerPlayer player, RumahKota.Info info) {
		CompoundTag r = baris(server, player, info, Long.MAX_VALUE);
		r.putFloat(KotaLembar.Rumah.FAKTOR_KOTA, (float) RumahKota.faktorTempat(server, info));
		r.putFloat(KotaLembar.Rumah.FAKTOR_MAKMUR, (float) HargaKota.kemakmuran(info.situs()));
		r.putFloat(KotaLembar.Rumah.FAKTOR_HARGA, (float) HargaKota.faktor(server, info.situs(), Kategori.BANGUNAN));
		CompoundTag denah = denah(server.overworld(), info);
		if (denah != null) {
			r.put(KotaLembar.DENAH, denah);
		}
		r.put(KotaLembar.Rumah.TETANGGA, tetangga(server.overworld(), player, info));
		ListTag syarat = new ListTag();
		for (Object[] s : RumahKota.syaratDaftar(player, info)) {
			String punya = (String) s[2];
			syarat.add(LembarKotaDasar.syarat((Boolean) s[0], (Component) s[1], punya.isEmpty() ? null : Component.literal(punya)));
		}
		r.put(LembarKunci.SYARAT, syarat);
		if (info.pemilik() != null) {
			r.putString(KotaLembar.STATUS, info.status());
		}
		return r;
	}

	/**
	 * A top view of the house box at the floor level of its door: walls (a full block one above the floor) in their map
	 * colour darkened, floors in theirs, doors gold, outside the box 0. Read only on the server and only when the box is
	 * loaded; scaled down to {@value #DENAH_MAKS} cells.
	 */
	static @Nullable CompoundTag denah(ServerLevel level, RumahKota.Info info) {
		BoundingBox box = info.kotak();
		if (!level.isLoaded(new BlockPos(box.minX(), box.minY(), box.minZ())) || !level.isLoaded(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
			return null;
		}
		int lebar = box.getXSpan();
		int panjang = box.getZSpan();
		int langkah = Math.max(1, (Math.max(lebar, panjang) + DENAH_MAKS - 1) / DENAH_MAKS);
		int w = Math.min(DENAH_MAKS, (lebar + langkah - 1) / langkah);
		int h = Math.min(DENAH_MAKS, (panjang + langkah - 1) / langkah);
		int lantai = Math.max(box.minY(), Math.min(box.maxY(), info.pintu().getY() - 1));
		int[] warna = new int[w * h];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cz = 0; cz < h; cz++) {
			for (int cx = 0; cx < w; cx++) {
				int x = box.minX() + cx * langkah;
				int z = box.minZ() + cz * langkah;
				pos.set(x, lantai + 1, z);
				BlockState dinding = level.getBlockState(pos);
				int argb;
				if (dinding.getBlock() instanceof DoorBlock) {
					argb = 0xFFD9A030;
				} else if (!dinding.isAir() && dinding.isCollisionShapeFullBlock(level, pos)) {
					argb = gelap(warna(dinding, level, pos), 0.55F);
				} else {
					pos.set(x, lantai, z);
					BlockState bawah = level.getBlockState(pos);
					argb = bawah.isAir() ? 0xFF5A4A3A : warna(bawah, level, pos);
				}
				warna[cz * w + cx] = argb;
			}
		}
		CompoundTag d = new CompoundTag();
		d.putInt(KotaLembar.LEBAR, w);
		d.putInt(KotaLembar.PANJANG, h);
		d.putIntArray(KotaLembar.WARNA, warna);
		return d;
	}

	private static int warna(BlockState state, ServerLevel level, BlockPos pos) {
		MapColor c = state.getMapColor(level, pos);
		int rgb = c == null || c == MapColor.NONE ? 0x8A7A62 : c.col;
		return 0xFF000000 | rgb;
	}

	private static int gelap(int argb, float f) {
		int r = Math.round(((argb >> 16) & 0xFF) * f);
		int g = Math.round(((argb >> 8) & 0xFF) * f);
		int b = Math.round((argb & 0xFF) * f);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static ListTag tetangga(ServerLevel level, ServerPlayer player, RumahKota.Info info) {
		ListTag list = new ListTag();
		Set<Integer> seen = new HashSet<>();
		BlockPos door = info.pintu();
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(door).inflate(TETANGGA_JARAK, 6.0D, TETANGGA_JARAK);
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, area, KultivatorNpc::isAlive)) {
			Warga w = WargaApi.dari(npc);
			if (w == null || !seen.add(w.id) || WargaApi.umur(w) < 16) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			t.putInt(KotaLembar.WARGA, w.id);
			LembarKunci.teks(t, LembarKunci.NAMA, WargaApi.nama(w));
			Ingatan ing = WargaApi.ingatan(w, player.getUUID());
			int kesan = ing == null ? 0 : ing.kesan;
			int kenal = ing == null ? 0 : ing.kenal;
			t.putInt(KotaLembar.KESAN, kesan);
			t.putString(KotaLembar.AKRAB, kesan <= -40 ? KotaLembar.AKRAB_MUSUH : kenal <= 0 ? KotaLembar.AKRAB_ASING
					: kesan >= 60 ? KotaLembar.AKRAB_SAHABAT : kesan >= 30 ? KotaLembar.AKRAB_AKRAB : KotaLembar.AKRAB_KENAL);
			list.add(t);
			if (list.size() >= 4) {
				break;
			}
		}
		return list;
	}

	private static ListTag milik(MinecraftServer server, ServerPlayer player) {
		ListTag list = new ListTag();
		Set<String> toko = new HashSet<>();
		for (TokoPemain.Toko t : TokoPemain.milik(server, player.getUUID())) {
			toko.add(LembarToko.idToko(t));
		}
		for (RumahKota.Rumah r : RumahKota.milik(player.getUUID())) {
			RumahKota.Info info = RumahKota.info(server, r.situs(), r.id());
			if (info == null) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, info.situs() + "_" + info.id());
			LembarKunci.teks(t, LembarKunci.NAMA, RumahKota.nama(server, info));
			LembarKunci.teks(t, LembarKunci.TEMPAT, KotaNames.town(info.situs()));
			String st = switch (info.status()) {
				case RumahKota.ST_SEWA -> KotaLembar.Rumah.RS_SEWA;
				case RumahKota.ST_SEGEL -> KotaLembar.Rumah.RS_SEGEL;
				default -> KotaLembar.Rumah.RS_MILIK;
			};
			t.putString(KotaLembar.STATUS, st);
			long sampai = KotaLembar.Rumah.RS_SEGEL.equals(st) ? info.segelSampai() : info.sewaSampai();
			if (sampai > 0L) {
				t.putLong(KotaLembar.SAMPAI, sampai);
			}
			t.putInt(KotaLembar.Rumah.TUNGGAK, info.tunggak());
			t.putLong(KotaLembar.Rumah.PAJAK, KotaLembar.Rumah.RS_MILIK.equals(st) ? RumahKota.pajak(server, info) : 0L);
			t.putLong(KotaLembar.Rumah.SEWA, KotaLembar.Rumah.RS_SEWA.equals(st) ? RumahKota.sewa(server, info) : 0L);
			t.putBoolean(KotaLembar.Rumah.KUNCI, true);
			t.putLong(KotaLembar.Rumah.NILAI_JUAL, RumahKota.nilaiJual(info));
			t.putBoolean(KotaLembar.Rumah.TOKO, toko.contains(info.situs() + "_r" + info.id()));
			if (info.pembantu() != 0) {
				Warga help = WargaApi.byId(info.pembantu());
				if (help != null) {
					LembarKunci.teks(t, KotaLembar.Rumah.PEMBANTU, WargaApi.nama(help));
				}
			}
			t.putInt(LembarKunci.X, info.pintu().getX());
			t.putInt(LembarKunci.Z, info.pintu().getZ());
			list.add(t);
		}
		return list;
	}

	private static ListTag pembantu(MinecraftServer server, ServerPlayer player, long situs) {
		ListTag list = new ListTag();
		List<Integer> dipekerjakan = new ArrayList<>();
		for (RumahKota.Rumah r : RumahKota.milik(player.getUUID())) {
			RumahKota.Info info = RumahKota.info(server, r.situs(), r.id());
			if (info == null || info.pembantu() == 0) {
				continue;
			}
			Warga w = WargaApi.byId(info.pembantu());
			if (w == null) {
				continue;
			}
			dipekerjakan.add(w.id);
			CompoundTag t = LembarKotaDasar.warga(w, player, true);
			t.putLong(KotaLembar.Rumah.GAJI, RumahKota.gajiPembantu(server, info));
			t.putString(KotaLembar.Rumah.RUMAH, info.situs() + "_" + info.id());
			t.putBoolean(KotaLembar.Rumah.DIPEKERJAKAN, true);
			list.add(t);
		}
		RumahKota.Info rumahDiSini = situs == 0L ? null : RumahKota.milikDiTempat(server, situs, player.getUUID());
		if (rumahDiSini == null || rumahDiSini.pembantu() != 0) {
			return list;
		}
		long gaji = RumahKota.gajiPembantu(server, rumahDiSini);
		for (Warga w : WargaApi.wargaTempat(situs)) {
			if (list.size() >= 16) {
				break;
			}
			if (w.status != Warga.Status.HIDUP || dipekerjakan.contains(w.id) || WargaApi.umur(w) < 16 || Pekerjaan.bekerja(w)
					|| w.peranNpc() != NpcRole.WARGA) {
				continue;
			}
			CompoundTag t = LembarKotaDasar.warga(w, player, true);
			if (w.watak >= 0 && w.watak < Tokoh.WATAK.size()) {
				LembarKunci.teks(t, KotaLembar.WATAK_NAMA, Component.translatable("kultivator.kota42.watak." + Tokoh.WATAK.get(w.watak)));
			}
			t.putLong(KotaLembar.Rumah.GAJI, gaji);
			t.putString(KotaLembar.Rumah.RUMAH, rumahDiSini.situs() + "_" + rumahDiSini.id());
			t.putBoolean(KotaLembar.Rumah.DIPEKERJAKAN, false);
			list.add(t);
		}
		return list;
	}

	private LembarRumah() {
	}
}
