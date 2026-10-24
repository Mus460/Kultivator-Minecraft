package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.tempat.tema.KotaTemaData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Persistent state of the Kota Gunung (KotaTemaData part {@code "gunung"}; a new world starts empty). Layout:
 * <pre>
 * p.&lt;uuid&gt;     one player
 *   zs I        pilgrimage status 0 none, 1 climbing, 2 done, 3 cancelled      zt L  start game time
 *   zb I        landings lit (bit n-1 = landing n)                                zk L  town of the climb
 *   zw S        cancel reason key                                                 zn I  pilgrimages completed
 *   rp L        Restu Puncak lasts until (game time)                              rj L  next Restu allowed
 *   rc L        next Restu Cahaya allowed                                          rs B  one free Restu (quest reward)
 *   hd L        world day of the herb bits                                         hb.&lt;situs&gt; I picked herbs (bits)
 *   pp I        Pemetik Tebing points                                              tp B  a climbing rope armed
 *   lg B        cable car free for life (Penjaga Tali Awan)                       tt D  porter deliveries
 *                 {barang C (item), tujuan S biara|aula_emas, siap L, situs L}
 * k.&lt;situs&gt;    one town
 *   tb B        the new cable is blessed (quest stage 6)                          mo B  Gou Tie's herb monopoly (+20 %)
 *   sp B        Shan Ling left the town                                            gt I  Gou Tie 0 free, 1 penitent porter, 2 driven out
 *   ra L        world day the Rumput Awan stock returns
 * </pre>
 */
public final class GunungData {

	public static final String TEMA = "gunung";

	public static final int Z_TIDAK = 0;
	public static final int Z_BERJALAN = 1;
	public static final int Z_SELESAI = 2;
	public static final int Z_BATAL = 3;

	private GunungData() {
	}

	private static KotaTemaData data(MinecraftServer server) {
		return KotaTemaData.get(server);
	}

	/** The live compound of one player (created on first use). */
	public static CompoundTag pemain(MinecraftServer server, UUID id) {
		CompoundTag root = data(server).bagian(TEMA);
		CompoundTag all = root.getCompoundOrEmpty("p");
		root.put("p", all);
		String key = id.toString();
		CompoundTag mine = all.getCompoundOrEmpty(key);
		all.put(key, mine);
		return mine;
	}

	public static CompoundTag pemain(ServerPlayer player) {
		return pemain(player.level().getServer(), player.getUUID());
	}

	/** The live compound of one town. */
	public static CompoundTag kota(MinecraftServer server, long situsId) {
		CompoundTag root = data(server).bagian(TEMA);
		CompoundTag all = root.getCompoundOrEmpty("k");
		root.put("k", all);
		String key = Long.toString(situsId);
		CompoundTag mine = all.getCompoundOrEmpty(key);
		all.put(key, mine);
		return mine;
	}

	/** Marks the theme data dirty (call after every change). */
	public static void ubah(MinecraftServer server) {
		data(server).ubah();
	}

	// ------------------------------------------------------------------ small typed helpers

	public static boolean liftGratis(ServerPlayer player) {
		return pemain(player).getBooleanOr("lg", false);
	}

	public static int poinPemetik(ServerPlayer player) {
		return pemain(player).getIntOr("pp", 0);
	}

	public static void tambahPoinPemetik(ServerPlayer player, int n) {
		CompoundTag p = pemain(player);
		p.putInt("pp", Math.max(0, p.getIntOr("pp", 0) + n));
		ubah(player.level().getServer());
	}

	/** Pemetik Tebing rank 1-5 from points (0, 10, 30, 70, 150). */
	public static int pangkatPemetik(int poin) {
		return poin >= 150 ? 5 : poin >= 70 ? 4 : poin >= 30 ? 3 : poin >= 10 ? 2 : 1;
	}

	/** Points needed for the next rank (0 at the top). */
	public static int poinNaik(int poin) {
		int[] batas = {10, 30, 70, 150};
		for (int b : batas) {
			if (poin < b) {
				return b;
			}
		}
		return 0;
	}

	public static boolean monopoli(MinecraftServer server, long situsId) {
		return kota(server, situsId).getBooleanOr("mo", false);
	}

	public static boolean shanLingPergi(MinecraftServer server, long situsId) {
		return kota(server, situsId).getBooleanOr("sp", false);
	}

	public static ListTag titipan(ServerPlayer player) {
		CompoundTag p = pemain(player);
		ListTag list = p.getListOrEmpty("tt");
		p.put("tt", list);
		return list;
	}
}
