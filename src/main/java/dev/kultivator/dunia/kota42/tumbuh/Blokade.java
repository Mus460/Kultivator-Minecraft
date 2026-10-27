package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.kotabesar.GelombangBinatang;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * War and siege without a single block changed (DESIGN 42.9.5, last bullet): a raid on a town ({@code KotaBus.SERBUAN}, 42E's
 * Panggilan Bela Kota) puts it under blockade. While it lasts
 * <ul>
 *   <li>production halves and the cart that always came stops ({@link BahanKota#produksi}); {@link #aktif} is the flag
 *       caravans and other modules may read;</li>
 *   <li>the families of the houses by the wall (outside it: the outermost {@code bangunan} titik, 2-6) flee to the refugee
 *       tents on the square ({@link BangunKembali} pitches them, the households sleep there, their own beds are vetoed);</li>
 *   <li>mood falls through {@code Suasana} (it listens to the same bus event), nothing is repaired because nothing was
 *       really damaged: no repair job, no rebuild project, no bill.</li>
 * </ul>
 * A won raid lifts the blockade at the end of that day, a lost one after three days. Stored in
 * {@code KotaTumbuhData.situs("blokade", situs)}: {@code sampai} last world day, {@code rumah} the refugee house boxes
 * (6 ints each), {@code hari0} the day it began.
 */
public final class Blokade {

	/** Days the blockade lasts after a raid ended: won / lost. */
	public static final int HARI_MENANG = 0;
	public static final int HARI_KALAH = 3;
	/** Days the blockade lasts while the raid is under way (the raid's own end sets the real length). */
	public static final int HARI_SERBUAN = 2;
	/** A house this close to the edge of the walled place counts as outside the wall. */
	private static final int JARAK_TEMBOK = 12;
	private static final int RUMAH_MAKS = 6;

	/** True while place {@code situs} is blockaded. */
	public static boolean aktif(long situs) {
		MinecraftServer server = KotaHidup.server();
		return server != null && aktif(server, situs);
	}

	public static boolean aktif(MinecraftServer server, long situs) {
		if (!KotaConfig.tumbuh().aktif) {
			return false;
		}
		CompoundTag c = bagian(server).getCompoundOrEmpty("kota_" + situs);
		return c.contains("sampai") && c.getLongOr("sampai", -1L) >= KotaHidup.hari(server);
	}

	/** Production factor of a place: 0.5 while blockaded. */
	public static double produksi(MinecraftServer server, long situs) {
		return aktif(server, situs) ? 0.5D : 1.0D;
	}

	/** Boxes of the houses whose families are war refugees now (empty when none). */
	public static List<BoundingBox> rumah(MinecraftServer server, long situs) {
		List<BoundingBox> out = new ArrayList<>();
		if (!aktif(server, situs)) {
			return out;
		}
		for (Tag t : bagian(server).getCompoundOrEmpty("kota_" + situs).getListOrEmpty("rumah")) {
			if (t instanceof IntArrayTag a && a.getAsIntArray().length == 6) {
				int[] v = a.getAsIntArray();
				out.add(new BoundingBox(v[0], v[1], v[2], v[3], v[4], v[5]));
			}
		}
		return out;
	}

	/** Places with war refugees now. */
	static List<Long> situsPengungsi(MinecraftServer server) {
		List<Long> out = new ArrayList<>();
		CompoundTag part = bagian(server);
		for (String key : part.keySet()) {
			if (!key.startsWith("kota_")) {
				continue;
			}
			try {
				long situs = Long.parseLong(key.substring(5));
				if (!part.getCompoundOrEmpty(key).getListOrEmpty("rumah").isEmpty() && aktif(server, situs)) {
					out.add(situs);
				}
			} catch (NumberFormatException ignored) {
				// not a place key
			}
		}
		return out;
	}

	// ================================================================== the bus

	/** {@code KotaBus.SERBUAN}: a raid began or ended at {@code situs}. */
	static void serbuan(MinecraftServer server, long situs, boolean mulai, boolean menang) {
		if (!KotaConfig.tumbuh().aktif || situs == 0L) {
			return;
		}
		long hari = KotaHidup.hari(server);
		if (mulai) {
			mulai(server, situs, hari + HARI_SERBUAN);
		} else {
			CompoundTag c = data(server, situs);
			c.putLong("sampai", hari + (menang ? HARI_MENANG : HARI_KALAH));
			KotaTumbuhData.get(server).ubah();
			if (!menang) {
				pesan(server, situs, "kultivator.kota42.tumbuh.blokade.lanjut", ChatFormatting.DARK_RED);
			}
		}
	}

	/** Starts (or extends) a blockade to world day {@code sampai}; also {@code uji kota_hidup tumbuh blokade}. */
	static void mulai(MinecraftServer server, long situs, long sampai) {
		CompoundTag c = data(server, situs);
		boolean baru = !aktif(server, situs);
		c.putLong("sampai", Math.max(sampai, c.getLongOr("sampai", -1L)));
		if (baru) {
			c.putLong("hari0", KotaHidup.hari(server));
			List<BoundingBox> houses = rumahLuar(server.overworld(), situs);
			ListTag list = new ListTag();
			for (BoundingBox b : houses) {
				list.add(new IntArrayTag(new int[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}));
			}
			c.put("rumah", list);
			pesan(server, situs, houses.isEmpty() ? "kultivator.kota42.tumbuh.blokade.mulai" : "kultivator.kota42.tumbuh.blokade.mulai_pengungsi",
					ChatFormatting.RED);
			BangunKembali.segarKasur(situs);
		}
		KotaTumbuhData.get(server).ubah();
	}

	/** Ends a blockade at once ({@code uji kota_hidup tumbuh blokade 0}). */
	static void akhiri(MinecraftServer server, long situs) {
		CompoundTag c = data(server, situs);
		if (c.contains("sampai")) {
			c.putLong("sampai", -1L);
			berakhir(server, situs, c);
		}
	}

	/** Once a second (from {@link BangunKembali#tick}): lifts expired blockades and sends the refugees home. */
	static void tick(MinecraftServer server) {
		CompoundTag part = bagian(server);
		long hari = KotaHidup.hari(server);
		for (String key : List.copyOf(part.keySet())) {
			CompoundTag c = part.getCompoundOrEmpty(key);
			if (!key.startsWith("kota_") || !c.contains("sampai") || c.getLongOr("sampai", -1L) >= hari) {
				continue;
			}
			try {
				berakhir(server, Long.parseLong(key.substring(5)), c);
			} catch (NumberFormatException exception) {
				Kultivator.LOGGER.debug("Kultivator: bad blockade key {}", key);
			}
		}
	}

	private static void berakhir(MinecraftServer server, long situs, CompoundTag c) {
		boolean ada = !c.getListOrEmpty("rumah").isEmpty();
		c.remove("rumah");
		c.remove("sampai");
		KotaTumbuhData.get(server).ubah();
		BangunKembali.segarKasur(situs);
		pesan(server, situs, ada ? "kultivator.kota42.tumbuh.blokade.selesai_pengungsi" : "kultivator.kota42.tumbuh.blokade.selesai", ChatFormatting.GOLD);
	}

	// ================================================================== houses outside the wall

	/**
	 * The houses by the wall: the {@code bangunan} titik nearest the edge of the walled place, those within
	 * {@link #JARAK_TEMBOK} blocks of it (at most {@link #RUMAH_MAKS}); when none is that close, the two outermost.
	 */
	static List<BoundingBox> rumahLuar(ServerLevel level, long situs) {
		BoundingBox box = GelombangBinatang.kotakTempat(level, situs);
		List<Titik> all = new ArrayList<>(TempatApi.titik(level, situs, "bangunan"));
		List<BoundingBox> out = new ArrayList<>();
		if (box == null || all.isEmpty()) {
			return out;
		}
		all.sort(Comparator.comparingInt((Titik t) -> tepi(box, t.kotak())).thenComparingLong(t -> t.pos().asLong()));
		for (int i = 0; i < all.size() && out.size() < RUMAH_MAKS; i++) {
			if (tepi(box, all.get(i).kotak()) <= JARAK_TEMBOK || out.size() < 2) {
				out.add(all.get(i).kotak());
			}
		}
		return out;
	}

	/** Blocks between the centre of {@code b} and the nearest side of the walled place (negative when outside it). */
	private static int tepi(BoundingBox box, BoundingBox b) {
		int cx = (b.minX() + b.maxX()) / 2;
		int cz = (b.minZ() + b.maxZ()) / 2;
		return Math.min(Math.min(cx - box.minX(), box.maxX() - cx), Math.min(cz - box.minZ(), box.maxZ() - cz));
	}

	// ================================================================== helpers

	private static CompoundTag bagian(MinecraftServer server) {
		return KotaTumbuhData.get(server).bagian(KotaTumbuhData.BLOKADE);
	}

	private static CompoundTag data(MinecraftServer server, long situs) {
		return KotaTumbuhData.get(server).situs(KotaTumbuhData.BLOKADE, situs);
	}

	private static void pesan(MinecraftServer server, long situs, String key, ChatFormatting warna) {
		BangunKembali.siar(server.overworld(), situs, Component.translatable(key).withStyle(warna));
	}

	private Blokade() {
	}
}
