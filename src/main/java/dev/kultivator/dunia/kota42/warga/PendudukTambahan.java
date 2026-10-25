package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extra resident slots (DESIGN 42.4.7 "slot tambahan", 42.4.14 {@code TempatHuni.addSumberPenduduk}): every living resident
 * of three and older without a generated slot gets a 38 slot of its own, so it has a body, a schedule and a place in the
 * crowd: children beyond the place's child quota, grown children waiting for work, newcomers and movers, apprentices
 * (wearing their master's trade), elders who retired ({@code TETUA_DESA}), residents a player employs, and the dwellers
 * of finished plot buildings. Slot id = {@link #ID_AWAL} + resident id (stable, unique, above every generated slot).
 * House and bed come from a household member holding a generated slot (sharing a bed is allowed; a household with more
 * members than beds + 1 is "crowded", -5 mood, {@link Migrasi#sesak}); work comes from the role's round or
 * {@link Pekerjaan#magang}.
 */
public final class PendudukTambahan {

	/** First extra slot id (above the largest generated place, the capital's ~650). */
	public static final int ID_AWAL = 5000;

	private static boolean ready;

	/** Registers the source with 38NL. From {@code ArsipWarga.init}; safe twice. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		TempatHuni.addSumberPenduduk(PendudukTambahan::untuk);
	}

	/** True for an extra slot id. */
	public static boolean tambahan(int slot) {
		return slot >= ID_AWAL;
	}

	/** The extra slot id of resident {@code w}. */
	public static int idUntuk(Warga w) {
		return ID_AWAL + w.id;
	}

	/** The extra slots of a place (asked by 38NL when it becomes active and every 10 s after). */
	static List<Penduduk> untuk(TempatHuni huni) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || !WargaApi.aktif() || huni.dimensi != Level.OVERWORLD) {
			return List.of();
		}
		WargaData data = WargaData.get(server);
		WargaData.Situs s = data.peek(huni.situsId);
		if (s == null) {
			return List.of();
		}
		boolean ubah = false;
		if (!s.stat.contains(Pekerjaan.S_X) || huni.titik("kasur").size() > s.stat.getIntOr(Pekerjaan.S_KASUR, 0)) {
			Pekerjaan.catatTempat(huni, s);
			ubah = true;
		}
		Map<Integer, Penduduk> tuanRumah = new HashMap<>();
		int asli = huni.jumlahAsli();
		for (Penduduk p : huni.penduduk()) {
			if (p.id() >= asli) {
				continue;
			}
			Warga w = s.diSlot(p.id());
			if (w != null && w.rumahTangga != 0 && (p.kasur() != Penduduk.TANPA || !tuanRumah.containsKey(w.rumahTangga))) {
				tuanRumah.put(w.rumahTangga, p);
			}
		}
		List<Penduduk> out = new ArrayList<>();
		for (Warga w : s.warga) {
			if (w.status != Warga.Status.HIDUP || w.slot != Warga.TANPA_SLOT && !tambahan(w.slot)) {
				continue;
			}
			int umur = WargaApi.umur(w);
			if (umur < 3) {
				continue;
			}
			int id = idUntuk(w);
			if (w.slot != id) {
				w.slot = id;
				ubah = true;
			}
			Penduduk tuan = tuanRumah.get(w.rumahTangga);
			int rumah = w.rumah != Penduduk.TANPA ? w.rumah : tuan != null ? tuan.rumah() : Penduduk.TANPA;
			int kasur = tuan != null ? tuan.kasur() : Penduduk.TANPA;
			out.add(new Penduduk(id, peran(data, w, umur), w.look, w.suara, rumah, kasur, Penduduk.TANPA, w.rumahTangga, benih(huni.situsId, w)));
		}
		if (ubah) {
			data.ubah();
		}
		return out;
	}

	/** The role an extra slot wears: child, apprentice of its master's trade, employed help, retired elder or townsfolk. */
	static NpcRole peran(WargaData data, Warga w, int umur) {
		if (umur < 13) {
			return NpcRole.ANAK;
		}
		Warga guru = Pekerjaan.guru(data, w);
		if (umur < 16) {
			return guru != null ? guru.peranNpc() : NpcRole.ANAK;
		}
		CompoundTag t = Pekerjaan.catatan(w);
		if (t != null && t.contains(Pekerjaan.K_MAJIKAN)) {
			return switch (t.getStringOr(Pekerjaan.K_TUGAS, "")) {
				case "toko" -> NpcRole.PELAYAN;
				case "gudang" -> NpcRole.PENJAGA_GUDANG;
				default -> NpcRole.WARGA;
			};
		}
		if (guru != null) {
			return guru.peranNpc();
		}
		return umur >= 60 ? NpcRole.TETUA_DESA : NpcRole.WARGA;
	}

	private static long benih(long situs, Warga w) {
		long h = (situs * 0x9E3779B97F4A7C15L) ^ (w.id * 0xC2B2AE3D27D4EB4FL) ^ w.look;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		return h ^ (h >>> 29);
	}

	/** The resident in extra slot {@code slot} of a place, or null. */
	public static @Nullable Warga warga(WargaData data, int slot) {
		return tambahan(slot) ? data.byId(slot - ID_AWAL) : null;
	}

	private PendudukTambahan() {
	}
}
