package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.ekonomi.Jabatan;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rebuild projects (DESIGN 42.9.5, contract C42.F1) on top of 38.R, only for real attributed damage: the clusters 38.R
 * reports ({@code TempatEvents.TEMPAT_RUSAK}, which never fires for a change nobody caused). When one place takes more than
 * {@code kotaTumbuh.ambangProyek} (200) blocks in one world day, or one house loses more than 30 % of its shell, the open
 * clusters become one project:
 * <ul>
 *   <li>the households of the damaged houses are refugees: 2-8 {@code tenda_pengungsi} tents on the square (only while a
 *       player is near; bodies sleep there because the tents add {@code kasur} titik and the broken houses' beds are
 *       vetoed), town mood -20;</li>
 *   <li>the 38.R foremen keep the "Mohon Bantuan" sheet; {@link #sumbang} (laju x2 for a day, the donor list of the
 *       memorial), an officer's {@link #alihkanKas} (x1.5 for a day), migrant builders from the next town when the project
 *       is above 500 blocks (+1 crew, x1.33) speed the crews through {@code PerbaikanApi.setPengali};</li>
 *   <li>no plot grows while it runs ({@link #aktif});</li>
 *   <li>done (no real damage left): thanksgiving words and a gossip, refugees go home, a memorial record
 *       ({@link TuguKenangan}: date, blocks, the five biggest donors) for the client inscription on the square.</li>
 * </ul>
 * Stored in {@code KotaTumbuhData.situs("bangun", situs)}: {@code hari}/{@code rusak} today's real damage, {@code on},
 * {@code m0}, {@code tot} the project, {@code rumah} damaged house boxes, {@code dn}/{@code nm} donors, {@code x2}/{@code x15}
 * the last day of a speed-up, {@code tugu} the memorials.
 */
public final class BangunKembali {

	/** Shell share a house must lose to count as destroyed (42.9.5: more than 30 %). */
	public static final double RUMAH_RUSAK = 0.30D;
	/** Project size that brings migrant builders. */
	public static final int PENDATANG = 500;
	public static final int TENDA_MIN = 2;
	public static final int TENDA_MAKS = 8;
	/** Kas Kota an officer moves for one day at x1.5. */
	public static final long ALIH_NB = 50L;
	private static final double JARAK_TENDA = 64.0D;
	/** Anchors for the tents, most fitting first. */
	private static final List<String> ALUN = List.of("sumur", "pasar_pagi_0", "main", "obrol", "tiba");

	/** Place -> live tents (memory; tents are never saved). */
	private static final Map<Long, List<TendaPengungsiEntity>> TENDA = new HashMap<>();
	/** Place -> tent beds (memory, rebuilt with the tents). */
	private static final Map<Long, List<Titik>> KASUR = new HashMap<>();

	/** True while place {@code situs} has a rebuild project running. */
	public static boolean aktif(long situs) {
		MinecraftServer server = KotaHidup.server();
		return server != null && aktif(server, situs);
	}

	public static boolean aktif(MinecraftServer server, long situs) {
		return KotaTumbuhData.get(server).bagian(KotaTumbuhData.BANGUN).getCompoundOrEmpty("kota_" + situs).getBooleanOr("on", false);
	}

	/** True while {@code tent} is one of the live tents of a running project. */
	static boolean dipegang(TendaPengungsiEntity tent) {
		List<TendaPengungsiEntity> list = TENDA.get(tent.situsId());
		return list != null && list.contains(tent);
	}

	static CompoundTag data(MinecraftServer server, long situs) {
		return KotaTumbuhData.get(server).situs(KotaTumbuhData.BANGUN, situs);
	}

	static void lupakan() {
		TENDA.values().forEach(list -> list.forEach(TendaPengungsiEntity::discard));
		TENDA.clear();
		KASUR.clear();
		TukangPendatang.lupakan();
	}

	// ================================================================== damage in

	/** 38.R reported a real damage cluster of {@code blok} blocks at place {@code situs}. */
	static void rusak(ServerLevel level, long situs, BoundingBox gugus, int blok) {
		if (!KotaConfig.tumbuh().aktif || !KotaConfig.tumbuh().bangunKembali || !Level.OVERWORLD.equals(level.dimension()) || situs == 0L) {
			return;
		}
		MinecraftServer server = level.getServer();
		CompoundTag c = data(server, situs);
		long day = KotaHidup.hari(server);
		if (c.getLongOr("hari", Long.MIN_VALUE) != day) {
			c.putLong("hari", day);
			c.putInt("rusak", 0);
		}
		c.putInt("rusak", c.getIntOr("rusak", 0) + blok);
		boolean house = false;
		boolean baru = false;
		for (Titik t : TempatApi.titik(level, situs, "bangunan")) {
			BoundingBox b = t.kotak();
			if (!b.intersects(gugus)) {
				continue;
			}
			int w = b.getXSpan();
			int h = b.getYSpan();
			int d = b.getZSpan();
			// shortcut: the house volume is its box shell and the whole cluster counts as inside it; count real house blocks if it misfires
			int shell = 2 * (w * d + w * h + d * h);
			if (blok >= RUMAH_RUSAK * shell) {
				house = true;
				baru |= catatRumah(c, b);
			}
		}
		if (c.getBooleanOr("on", false)) {
			c.putInt("tot", c.getIntOr("tot", 0) + blok);
			if (baru) {
				segarKasur(situs);
			}
		} else if (house || c.getIntOr("rusak", 0) > KotaConfig.tumbuh().ambangProyek) {
			mulai(server, level, situs, c.getIntOr("rusak", blok));
		}
		KotaTumbuhData.get(server).ubah();
	}

	private static boolean catatRumah(CompoundTag c, BoundingBox b) {
		ListTag list = c.getListOrEmpty("rumah");
		for (Tag t : list) {
			if (t instanceof IntArrayTag a && a.getAsIntArray().length == 6 && a.getAsIntArray()[0] == b.minX() && a.getAsIntArray()[2] == b.minZ()) {
				return false;
			}
		}
		if (list.size() >= 32) {
			return false;
		}
		list.add(new IntArrayTag(new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}));
		c.put("rumah", list);
		return true;
	}

	/** Starts a project (also {@code uji kota_hidup tumbuh rusak_proyek}). */
	static void mulai(MinecraftServer server, ServerLevel level, long situs, int blok) {
		CompoundTag c = data(server, situs);
		if (c.getBooleanOr("on", false)) {
			return;
		}
		c.putBoolean("on", true);
		c.putLong("m0", KotaHidup.hari(server));
		c.putLong("t0", level.getGameTime());
		c.putInt("tot", Math.max(blok, PerbaikanApi.rusakTerbuka(situs)));
		c.remove("dn");
		c.remove("nm");
		KotaTumbuhData.get(server).ubah();
		siar(level, situs, Component.translatable("kultivator.kota42.tumbuh.bangun.mulai", c.getIntOr("tot", 0)).withStyle(ChatFormatting.RED));
		GosipWarga.lahir(level, situs, "BAHAYA", Fakta.Rujuk.tempat(situs));
		segarKasur(situs);
	}

	// ================================================================== the project's second

	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		CompoundTag all = KotaTumbuhData.get(server).bagian(KotaTumbuhData.BANGUN);
		for (String key : List.copyOf(all.keySet())) {
			CompoundTag c = all.getCompoundOrEmpty(key);
			if (!key.startsWith("kota_") || !c.getBooleanOr("on", false)) {
				continue;
			}
			long situs;
			try {
				situs = Long.parseLong(key.substring(5));
			} catch (NumberFormatException e) {
				continue;
			}
			try {
				int left = PerbaikanApi.rusakTerbuka(situs);
				// a minute at least, so clusters still being reported are part of it
				if (left == 0 && level.getGameTime() - c.getLongOr("t0", 0L) > 1200L) {
					selesai(server, level, situs, c);
				} else {
					tenda(level, situs);
					TukangPendatang.tick(server, level, situs);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: rebuild project of {} failed this second", situs, exception);
			}
		}
		try {
			Blokade.tick(server);
			for (long situs : Blokade.situsPengungsi(server)) {
				if (!aktif(server, situs)) {
					tenda(level, situs);
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kultivator: blockade refugees failed this second", exception);
		}
		TukangPendatang.bersihkan(server);
		TENDA.keySet().removeIf(situs -> {
			if (!pengungsi(server, situs)) {
				TENDA.get(situs).forEach(TendaPengungsiEntity::discard);
				return true;
			}
			return false;
		});
	}

	/** True while refugees camp on the square of {@code situs}: a rebuild project or a blockade with its refugee houses. */
	static boolean pengungsi(MinecraftServer server, long situs) {
		return aktif(server, situs) || !Blokade.rumah(server, situs).isEmpty();
	}

	/** Boxes of the houses the running project must rebuild (empty without a project). */
	static List<BoundingBox> rumahProyek(MinecraftServer server, long situs) {
		List<BoundingBox> out = new ArrayList<>();
		if (aktif(server, situs)) {
			for (Tag t : data(server, situs).getListOrEmpty("rumah")) {
				if (t instanceof IntArrayTag a && a.getAsIntArray().length == 6) {
					int[] v = a.getAsIntArray();
					out.add(new BoundingBox(v[0], v[1], v[2], v[3], v[4], v[5]));
				}
			}
		}
		return out;
	}

	/** Boxes of the houses whose households are refugees now: the project's destroyed houses and the war refugees'. */
	static List<BoundingBox> rumahPengungsi(MinecraftServer server, long situs) {
		List<BoundingBox> out = rumahProyek(server, situs);
		for (BoundingBox b : Blokade.rumah(server, situs)) {
			if (!out.contains(b)) {
				out.add(b);
			}
		}
		return out;
	}

	private static void selesai(MinecraftServer server, ServerLevel level, long situs, CompoundTag c) {
		c.putBoolean("on", false);
		List<TendaPengungsiEntity> tents = TENDA.remove(situs);
		if (tents != null) {
			tents.forEach(TendaPengungsiEntity::discard);
		}
		CompoundTag tugu = new CompoundTag();
		tugu.putLong("hari", KotaHidup.hari(server));
		tugu.putInt("blok", c.getIntOr("tot", 0));
		ListTag names = new ListTag();
		List<Map.Entry<String, Long>> donors = new ArrayList<>();
		CompoundTag dn = c.getCompoundOrEmpty("dn");
		for (String k : dn.keySet()) {
			donors.add(Map.entry(k, dn.getLongOr(k, 0L)));
		}
		donors.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
		for (int i = 0; i < Math.min(5, donors.size()); i++) {
			CompoundTag d = new CompoundTag();
			d.putString("n", c.getCompoundOrEmpty("nm").getStringOr(donors.get(i).getKey(), "?"));
			d.putLong("nb", donors.get(i).getValue());
			names.add(d);
		}
		tugu.put("dn", names);
		ListTag memorials = c.getListOrEmpty("tugu");
		memorials.add(tugu);
		while (memorials.size() > 8) {
			memorials.remove(0);
		}
		c.put("tugu", memorials);
		c.remove("rumah");
		c.putInt("rusak", 0);
		KotaTumbuhData.get(server).ubah();
		segarKasur(situs);
		siar(level, situs, Component.translatable("kultivator.kota42.tumbuh.bangun.selesai", c.getIntOr("tot", 0)).withStyle(ChatFormatting.GOLD));
		GosipWarga.lahir(level, situs, "ACARA", 0, Fakta.Kebenaran.BENAR, List.of("syukur_bangun"), null, Fakta.Rujuk.tempat(situs));
		Peresmian.syukur(server, level, situs);
		for (String key : dn.keySet()) {
			try {
				ServerPlayer donor = server.getPlayerList().getPlayer(UUID.fromString(key));
				if (donor != null) {
					Advancements.grant(donor, "tempat/kota42_bangun_kembali");
				}
			} catch (IllegalArgumentException ignored) {
				// not a player key
			}
		}
	}

	/** Keeps the refugee tents standing on the square while a player is near (one tent per refugee household). */
	private static void tenda(ServerLevel level, long situs) {
		List<TendaPengungsiEntity> tents = TENDA.computeIfAbsent(situs, k -> new ArrayList<>());
		tents.removeIf(TendaPengungsiEntity::isRemoved);
		List<Titik> beds = kasurTenda(level, situs);
		if (beds.isEmpty()) {
			return;
		}
		Vec3 centre = Vec3.atBottomCenterOf(beds.get(0).pos());
		if (level.getNearestPlayer(centre.x, centre.y, centre.z, JARAK_TENDA, false) == null) {
			tents.forEach(TendaPengungsiEntity::discard);
			tents.clear();
			return;
		}
		// the first bed of each tent stands under the cloth, the other family members' mats lie beside it
		List<Titik> spots = new ArrayList<>();
		for (Titik bed : beds) {
			if (bed.angka("anggota", 0) == 0) {
				spots.add(bed);
			}
		}
		boolean night = level.isDarkOutside();
		for (int i = tents.size(); i < spots.size(); i++) {
			if (DuniaBudget.count(KotaAcaraEntities.ANGGARAN) >= DuniaBudget.cap(KotaAcaraEntities.ANGGARAN)) {
				break;
			}
			TendaPengungsiEntity tent = KotaTumbuhEntities.TENDA_PENGUNGSI.create(level, EntitySpawnReason.EVENT);
			if (tent == null) {
				break;
			}
			Titik bed = spots.get(i);
			tent.ikat(situs, bed.pos().asLong());
			tent.pindah(Vec3.atBottomCenterOf(bed.pos()), bed.hadap().getOpposite().toYRot());
			tent.setVarian(Math.floorMod((int) (situs ^ i), TendaPengungsiEntity.VARIAN_JUMLAH));
			level.addFreshEntity(tent);
			tents.add(tent);
		}
		for (TendaPengungsiEntity tent : tents) {
			tent.setKeadaan(night ? TendaPengungsiEntity.KEADAAN_MALAM : TendaPengungsiEntity.KEADAAN_SIANG);
			tent.setNilai(night ? 1.0F : 0.0F);
		}
	}

	/** Mats per tent: a household of more than three lets the rest sleep on the floor of the cook shed (no bed). */
	private static final int ANGGOTA_MAKS = 3;

	/**
	 * The tent beds of the refugees (project houses and war refugees): one tent per refugee house (2-8), in two rows on the
	 * first square anchor found (well, morning market, play spot, chat corner, arrival point), each tent's mouth toward the
	 * anchor, and one bed per resident of that house (at most {@link #ANGGOTA_MAKS}): the first under the tent
	 * ({@code anggota=0}), the others on mats beside it. Houses beyond the eighth share the tents. Not cached while 38NL has
	 * not read the houses' residents yet (the first titik pass of a place), so the families are never lost to a race.
	 * shortcut: 38NL gives a bed to a resident by a hash of the bed's position, so which resident takes which free bed is
	 * its own matter (the beds of the vetoed houses' owners are the ones left free); exact household-to-tent binding needs a
	 * {@code TempatHuni} hook that returns the bed of a resident, then the tent index {@code tenda} in the titik data is read.
	 */
	private static List<Titik> kasurTenda(ServerLevel level, long situs) {
		List<Titik> cached = KASUR.get(situs);
		if (cached != null) {
			return cached;
		}
		BlockPos anchor = alun(level, situs);
		if (anchor == null) {
			return List.of();
		}
		List<BoundingBox> houses = rumahPengungsi(level.getServer(), situs);
		int n = Math.max(TENDA_MIN, Math.min(TENDA_MAKS, houses.size()));
		int[] anggota = new int[n];
		boolean dibaca = houses.isEmpty();
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni != null && !houses.isEmpty()) {
			for (Penduduk p : huni.penduduk()) {
				Titik rumah = huni.rumah(p);
				int idx = rumah == null ? -1 : houses.indexOf(rumah.kotak());
				if (idx >= 0) {
					dibaca = true;
					anggota[idx % n] = Math.min(ANGGOTA_MAKS, anggota[idx % n] + 1);
				}
			}
		}
		List<Titik> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			int dx = (i / 2 - (n / 2 - 1) / 2) * 3;
			int dz = (i % 2 == 0 ? -3 : 3);
			Direction mouth = dz < 0 ? Direction.NORTH : Direction.SOUTH;
			int mats = Math.max(1, anggota[i]);
			for (int m = 0; m < mats; m++) {
				// mats lie east and west of the tent, one block each
				int sx = m == 0 ? 0 : m == 1 ? 1 : -1;
				int x = anchor.getX() + dx + sx;
				int z = anchor.getZ() + dz + (m == 0 ? 0 : (dz < 0 ? 1 : -1));
				BlockPos spot = TempatApi.groundSpot(level, x, z, anchor.getY());
				if (spot == null) {
					spot = new BlockPos(x, anchor.getY(), z);
				}
				out.add(new Titik("kasur", spot, mouth, Map.of("tenda", Integer.toString(i), "anggota", Integer.toString(m))));
			}
		}
		List<Titik> done = List.copyOf(out);
		if (dibaca) {
			KASUR.put(situs, done);
		}
		return done;
	}

	/** The square of a place: the first of the well, morning market, play spot, chat corner or arrival point; null when none. */
	static @Nullable BlockPos alun(ServerLevel level, long situs) {
		for (String type : ALUN) {
			List<Titik> found = TempatApi.titik(level, situs, type);
			if (!found.isEmpty()) {
				return found.get(0).pos();
			}
		}
		return null;
	}

	static void segarKasur(long situs) {
		KASUR.remove(situs);
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni != null) {
			huni.ubahTitik();
		}
	}

	/** {@code TempatHuni.addSumberTitik}: the tent beds while refugees camp (none otherwise). */
	static List<Titik> titikTenda(MinecraftServer server, TempatHuni huni) {
		if (!pengungsi(server, huni.situsId)) {
			return List.of();
		}
		return kasurTenda(server.overworld(), huni.situsId);
	}

	/** {@code TempatHuni.addPenolakTitik}: a bed inside a refugee house is not slept in while the refugees camp. */
	static boolean rumahRusak(MinecraftServer server, TempatHuni huni, BlockPos pos) {
		if (!pengungsi(server, huni.situsId)) {
			return false;
		}
		for (BoundingBox b : rumahPengungsi(server, huni.situsId)) {
			if (b.isInside(pos)) {
				return true;
			}
		}
		return false;
	}


	// ================================================================== speed

	/** Repair multiplier of a place's crews while its project runs (the {@code PerbaikanApi.Pengali} of 42F). */
	static double pengali(MinecraftServer server, long situs) {
		if (!aktif(server, situs)) {
			return 1.0D;
		}
		CompoundTag c = data(server, situs);
		long day = KotaHidup.hari(server);
		double m = 1.0D;
		if (c.getIntOr("tot", 0) > PENDATANG) {
			m *= 4.0D / 3.0D;
		}
		if (c.getLongOr("x2", -1L) >= day) {
			m *= 2.0D;
		}
		if (c.getLongOr("x15", -1L) >= day) {
			m *= 1.5D;
		}
		return m;
	}

	/** True when migrant builders from the next town help (project above {@link #PENDATANG} blocks). */
	public static boolean pendatang(MinecraftServer server, long situs) {
		return aktif(server, situs) && data(server, situs).getIntOr("tot", 0) > PENDATANG;
	}

	/** Blocks of the running project and those still waiting ({total, left}). */
	public static int[] kemajuan(MinecraftServer server, long situs) {
		return new int[]{data(server, situs).getIntOr("tot", 0), PerbaikanApi.rusakTerbuka(situs)};
	}

	/**
	 * A player gives {@code nb} toward the project: paid into Kas Kota, crews x2 until the end of the day, his name on the
	 * donor list of the memorial. Null on success, else the refusal.
	 */
	public static @Nullable Component sumbang(ServerPlayer player, long situs, long nb) {
		MinecraftServer server = player.level().getServer();
		if (!aktif(server, situs)) {
			return Component.translatable("kultivator.kota42.tumbuh.bangun.tak_ada");
		}
		if (nb <= 0L || !EkonomiApi.bayar(player, nb, "bangun_kembali")) {
			return Component.translatable("kultivator.kota42.tumbuh.tak_cukup_uang");
		}
		KasKota.tambah(server, situs, nb);
		CompoundTag c = data(server, situs);
		CompoundTag dn = c.getCompoundOrEmpty("dn");
		String key = player.getUUID().toString();
		dn.putLong(key, dn.getLongOr(key, 0L) + nb);
		c.put("dn", dn);
		CompoundTag nm = c.getCompoundOrEmpty("nm");
		nm.putString(key, player.getGameProfile().name());
		c.put("nm", nm);
		c.putLong("x2", KotaHidup.hari(server));
		KotaTumbuhData.get(server).ubah();
		PekerjaanTumbuh.namaBaik(player, situs, (int) Math.min(10L, nb / 20L));
		return null;
	}

	/** An officer (rank 1+) moves {@link #ALIH_NB} of Kas Kota to the crews: x1.5 until the end of the day. */
	public static @Nullable Component alihkanKas(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		if (!aktif(server, situs)) {
			return Component.translatable("kultivator.kota42.tumbuh.bangun.tak_ada");
		}
		if (Jabatan.pangkat(player.getUUID(), situs) < Jabatan.JURU_TULIS) {
			return Component.translatable("kultivator.kota42.tumbuh.tak_berhak");
		}
		if (!KasKota.bayar(server, situs, ALIH_NB, "bangun_kembali")) {
			return Component.translatable("kultivator.kota42.tumbuh.kas_kurang");
		}
		data(server, situs).putLong("x15", KotaHidup.hari(server));
		KotaTumbuhData.get(server).ubah();
		return null;
	}

	static void siar(ServerLevel level, long situs, Component text) {
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni == null) {
			return;
		}
		BlockPos at = huni.pusat();
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(at) <= 160.0D * 160.0D) {
				Msg.chat(player, text);
			}
		}
	}

	/** Donors of the running project (player -> NB). */
	public static Map<UUID, Long> dana(MinecraftServer server, long situs) {
		Map<UUID, Long> out = new HashMap<>();
		CompoundTag dn = data(server, situs).getCompoundOrEmpty("dn");
		for (String k : dn.keySet()) {
			try {
				out.put(UUID.fromString(k), dn.getLongOr(k, 0L));
			} catch (IllegalArgumentException ignored) {
				// not a player key
			}
		}
		return out;
	}

	private BangunKembali() {
	}
}
