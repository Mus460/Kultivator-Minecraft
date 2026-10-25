package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.ekonomi.Jabatan;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Growth plots (DESIGN 42.9.4, contract C42.F1): the {@code kavling} titik 42W planned in new chunks (box point at the min
 * corner of the footprint, ground row, data {@code rencana} = plan kind, {@code gaya}, {@code kelas}; hadap = street side).
 * A plot is found the first time its piece is loaded ({@link PekerjaanTumbuh}), then remembered; it is built in five
 * cumulative stages (foundation, frame, walls, roof, furnishing) from {@code structure/kota_tumbuh/<gaya>/<jenis>/tahap_<n>},
 * only through the 38.R crews ({@code PerbaikanApi.kerjakan}, i.e. {@code WorldBreaker.submitRestore}, reason "tumbuh")
 * and only after the 42.12.1A checks; a failed structure or ground check makes it {@link #TIDAK_LAYAK} for good (K11: no
 * growth outside planned plots). Stored in {@code KotaTumbuhData.situs("kavling", situs)["L"]}.
 * <p>
 * Plot keys: {@code i} id (1.., per place), {@code p} min corner, {@code h} facing, {@code dx}/{@code dz} box size - 1,
 * {@code g} style, {@code k} class, {@code j} planned kind, {@code r} chosen plan id ({@code gaya/jenis}), {@code t} stage
 * 0-5 or -1, {@code s} state ({@code KotaLembar.Kavling.KV_*}), {@code a} reason lang suffix of a block or refusal,
 * {@code an} blocked cells, {@code job} crew job id, {@code u} material units used, {@code dn} donations (uuid -> NB value),
 * {@code nm} donor names, {@code m0}/{@code m1} world day started / finished, {@code co} retries of the current stage.
 */
public final class Kavling {

	/** {@link Info#tahap} of a plot that may never be built (foreign structure, rough ground). */
	public static final int TIDAK_LAYAK = -1;
	/** {@link Info#tahap} of a finished plot. */
	public static final int SELESAI = 5;

	/** One plot: id in its place, titik position and facing, style, building plan, stage 0-5 or {@link #TIDAK_LAYAK}. */
	public record Info(int id, BlockPos pos, Direction hadap, String gaya, String jenis, int tahap) {

		public boolean layak() {
			return tahap != TIDAK_LAYAK;
		}

		public boolean selesai() {
			return tahap >= SELESAI;
		}
	}

	/**
	 * The full state of a plot for screens and commands: footprint box (ground row up to the plan height), plan id, class,
	 * state ({@code KotaLembar.Kavling.KV_*}), reason ({@code kultivator.kota42.tumbuh.alasan.<a>}, "" none), blocked
	 * cells, crew job, material units used, donations (player -> NB value) and the name on the finished sign ("" none).
	 */
	public record Rinci(Info info, BoundingBox kotak, String rencana, String kelas, String keadaan, String alasan, int terhalang, long job,
			int[] bahanTerpakai, Map<UUID, Long> dana, Map<UUID, String> namaDana, String papanNama, long mulai, long selesaiHari) {
	}

	// ================================================================== reading

	/** The plots of place {@code situs} (empty before the server started or before any was found). */
	public static List<Info> daftar(long situs) {
		MinecraftServer server = KotaHidup.server();
		return server == null ? List.of() : daftar(server, situs);
	}

	public static List<Info> daftar(MinecraftServer server, long situs) {
		List<Info> out = new ArrayList<>();
		for (CompoundTag c : semua(server, situs)) {
			out.add(info(c));
		}
		return out;
	}

	/** The details of plot {@code id}, or null. */
	public static @Nullable Rinci rinci(MinecraftServer server, long situs, int id) {
		CompoundTag c = mentah(server, situs, id);
		return c == null ? null : rinci(c);
	}

	/** The details of every plot of a place. */
	public static List<Rinci> semuaRinci(MinecraftServer server, long situs) {
		List<Rinci> out = new ArrayList<>();
		for (CompoundTag c : semua(server, situs)) {
			out.add(rinci(c));
		}
		return out;
	}

	/** Places that know at least one plot. */
	public static List<Long> tempat(MinecraftServer server) {
		List<Long> out = new ArrayList<>();
		for (String key : KotaTumbuhData.get(server).bagian(KotaTumbuhData.KAVLING).keySet()) {
			if (key.startsWith("kota_")) {
				try {
					out.add(Long.parseLong(key.substring(5)));
				} catch (NumberFormatException ignored) {
					// not a place key
				}
			}
		}
		return out;
	}

	static Info info(CompoundTag c) {
		String r = c.getStringOr("r", "");
		int slash = r.indexOf('/');
		Direction d = Direction.byName(c.getStringOr("h", "south"));
		return new Info(c.getIntOr("i", 0), BlockPos.of(c.getLongOr("p", 0L)), d == null || d.getAxis().isVertical() ? Direction.SOUTH : d,
				c.getStringOr("g", ""), slash >= 0 ? r.substring(slash + 1) : c.getStringOr("j", ""), c.getIntOr("t", 0));
	}

	static Rinci rinci(CompoundTag c) {
		Map<UUID, Long> dana = new LinkedHashMap<>();
		Map<UUID, String> nama = new LinkedHashMap<>();
		CompoundTag dn = c.getCompoundOrEmpty("dn");
		CompoundTag nm = c.getCompoundOrEmpty("nm");
		for (String key : dn.keySet()) {
			try {
				UUID u = UUID.fromString(key);
				dana.put(u, dn.getLongOr(key, 0L));
				nama.put(u, nm.getStringOr(key, "?"));
			} catch (IllegalArgumentException ignored) {
				// not a player key
			}
		}
		int[] used = c.getIntArray("u").orElse(new int[BahanKota.JENIS.size()]);
		return new Rinci(info(c), kotak(c), c.getStringOr("r", ""), c.getStringOr("k", "kecil"), c.getStringOr("s", KotaLembar.Kavling.KV_KOSONG),
				c.getStringOr("a", ""), c.getIntOr("an", 0), c.getLongOr("job", 0L), used, dana, nama, papanNama(c), c.getLongOr("m0", -1L),
				c.getLongOr("m1", -1L));
	}

	/** The footprint box of a plot: ground row up to the tallest stage. */
	static BoundingBox kotak(CompoundTag c) {
		BlockPos p = BlockPos.of(c.getLongOr("p", 0L));
		return new BoundingBox(p.getX(), p.getY(), p.getZ(), p.getX() + c.getIntOr("dx", 6), p.getY() + 17, p.getZ() + c.getIntOr("dz", 6));
	}

	/** The template origin and turn of a plot's plan. */
	static BlockPos asal(CompoundTag c, RencanaTumbuh.Rencana r) {
		return RencanaTumbuh.asal(BlockPos.of(c.getLongOr("p", 0L)), r.lebar(), r.panjang(), rotasi(c));
	}

	static Rotation rotasi(CompoundTag c) {
		return RencanaTumbuh.rotasi(info(c).hadap());
	}

	/** The donor named on the finished sign: the biggest giver with at least 25 % of the cost, else "". */
	static String papanNama(CompoundTag c) {
		long total = Math.max(1L, c.getLongOr("biaya", 0L));
		CompoundTag dn = c.getCompoundOrEmpty("dn");
		String best = "";
		long bestNb = 0L;
		for (String key : dn.keySet()) {
			long nb = dn.getLongOr(key, 0L);
			if (nb > bestNb) {
				bestNb = nb;
				best = key;
			}
		}
		return !best.isEmpty() && bestNb * 4L >= total ? c.getCompoundOrEmpty("nm").getStringOr(best, "") : "";
	}

	// ================================================================== storage (package)

	static ListTag list(MinecraftServer server, long situs) {
		CompoundTag site = KotaTumbuhData.get(server).situs(KotaTumbuhData.KAVLING, situs);
		ListTag l = site.getListOrEmpty("L");
		if (!site.contains("L")) {
			site.put("L", l);
		}
		return l;
	}

	static List<CompoundTag> semua(MinecraftServer server, long situs) {
		List<CompoundTag> out = new ArrayList<>();
		for (Tag t : list(server, situs)) {
			if (t instanceof CompoundTag c) {
				out.add(c);
			}
		}
		return out;
	}

	static @Nullable CompoundTag mentah(MinecraftServer server, long situs, int id) {
		for (CompoundTag c : semua(server, situs)) {
			if (c.getIntOr("i", -1) == id) {
				return c;
			}
		}
		return null;
	}

	/** Adds the plot of titik {@code t} unless known (same corner); returns its compound. */
	static CompoundTag temukan(MinecraftServer server, long situs, Titik t) {
		long p = t.pos().asLong();
		ListTag l = list(server, situs);
		int max = 0;
		for (Tag tag : l) {
			if (tag instanceof CompoundTag c) {
				if (c.getLongOr("p", 0L) == p) {
					return c;
				}
				max = Math.max(max, c.getIntOr("i", 0));
			}
		}
		String gaya = t.data().getOrDefault("gaya", "kota_kultivator");
		String jenis = t.data().getOrDefault("rencana", "rumah_kecil");
		CompoundTag c = new CompoundTag();
		c.putInt("i", max + 1);
		c.putLong("p", p);
		c.putString("h", t.hadap().getSerializedName());
		c.putInt("dx", t.angka("dx", 6));
		c.putInt("dz", t.angka("dz", 6));
		c.putString("g", gaya);
		c.putString("k", t.data().getOrDefault("kelas", "kecil"));
		c.putString("j", jenis);
		c.putString("r", gaya + "/" + jenis);
		c.putInt("t", 0);
		c.putString("s", KotaLembar.Kavling.KV_KOSONG);
		l.add(c);
		KotaTumbuhData.get(server).ubah();
		return c;
	}

	static void keadaan(CompoundTag c, String keadaan, String alasan) {
		c.putString("s", keadaan);
		c.putString("a", alasan == null ? "" : alasan);
	}

	/** The plot can never be built (42.12.1A): stage -1, state {@code tidak_layak}, reason kept for {@code kompat periksa}. */
	static void matikan(MinecraftServer server, long situs, CompoundTag c, String alasan) {
		c.putInt("t", TIDAK_LAYAK);
		c.putLong("job", 0L);
		keadaan(c, KotaLembar.Kavling.KV_TIDAK_LAYAK, alasan);
		KotaTumbuhData.get(server).ubah();
	}

	// ================================================================== player actions (42.11.13 ops)

	/**
	 * Office rank 4+ (Wakil, Wali Kota) picks the plan of a plot not started yet ({@code rencana:ID}); the plan must be of
	 * the plot's style and class. Null on success, else the refusal.
	 */
	public static @Nullable Component pilihRencana(ServerPlayer player, long situs, int id, String rencanaId) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = mentah(server, situs, id);
		if (c == null) {
			return Component.translatable("kultivator.kota42.tumbuh.tak_ada");
		}
		if (Jabatan.pangkat(player.getUUID(), situs) < Jabatan.WAKIL) {
			return Component.translatable("kultivator.kota42.tumbuh.tak_berhak");
		}
		return pilih(server, situs, c, rencanaId);
	}

	/** Sets the plan without a rank check (tests, the deputy's standing choice). */
	static @Nullable Component pilih(MinecraftServer server, long situs, CompoundTag c, String rencanaId) {
		if (c.getIntOr("t", 0) != 0) {
			return Component.translatable("kultivator.kota42.tumbuh.sudah_mulai");
		}
		RencanaTumbuh.Rencana r = RencanaTumbuh.get(server, rencanaId);
		if (r == null || !r.kelas().equals(c.getStringOr("k", "")) || !r.gaya().equals(c.getStringOr("g", ""))) {
			return Component.translatable("kultivator.kota42.tumbuh.rencana_salah", rencanaId == null ? "?" : rencanaId);
		}
		c.putString("r", r.id());
		c.putLong("biaya", PekerjaanTumbuh.biaya(server, situs, r));
		KotaTumbuhData.get(server).ubah();
		return null;
	}

	/**
	 * Gives {@code nb} Nilai Batu toward plot {@code id} ({@code sumbang_nb:NB}): paid into Kas Kota, recorded as the
	 * player's share (25 % of the cost puts his name on the finished sign). Null on success, else the refusal.
	 */
	public static @Nullable Component sumbangNb(ServerPlayer player, long situs, int id, long nb) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = mentah(server, situs, id);
		if (c == null || !info(c).layak() || info(c).selesai()) {
			return Component.translatable("kultivator.kota42.tumbuh.tak_ada");
		}
		if (nb <= 0L || !EkonomiApi.bayar(player, nb, "kavling")) {
			return Component.translatable("kultivator.kota42.tumbuh.tak_cukup_uang");
		}
		KasKota.tambah(server, situs, nb);
		catatDana(server, c, player, nb);
		return null;
	}

	/** Records {@code nb} of value given by {@code player} to plot {@code c}. */
	static void catatDana(MinecraftServer server, CompoundTag c, ServerPlayer player, long nb) {
		CompoundTag dn = c.getCompoundOrEmpty("dn");
		String key = player.getUUID().toString();
		dn.putLong(key, dn.getLongOr(key, 0L) + nb);
		c.put("dn", dn);
		CompoundTag nm = c.getCompoundOrEmpty("nm");
		nm.putString(key, player.getGameProfile().name());
		c.put("nm", nm);
		KotaTumbuhData.get(server).ubah();
	}

	/** The plot being built (or waiting) in place {@code situs} that donations go to, or null. */
	static @Nullable CompoundTag berjalan(MinecraftServer server, long situs) {
		for (CompoundTag c : semua(server, situs)) {
			int t = c.getIntOr("t", 0);
			String s = c.getStringOr("s", "");
			if (t >= 0 && t < SELESAI && !KotaLembar.Kavling.KV_KOSONG.equals(s)) {
				return c;
			}
		}
		return null;
	}

	private Kavling() {
	}
}
