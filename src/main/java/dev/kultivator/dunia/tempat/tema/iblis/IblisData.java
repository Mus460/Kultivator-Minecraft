package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.tema.KotaTemaData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The persistent state of the Kota Iblis (DESIGN 38.T.7), kept inside {@code KotaTemaData.bagian("iblis")} (theme
 * agents never edit {@link KotaTemaData}; matrix row 23). New worlds only: no version field, no migration.
 * <pre>
 * bagian("iblis")
 *   pemain.&lt;uuid&gt;          global per player: gelar (titles), rute (quest route), pemburu_hari (last righteous
 *                          interception day), racun (coating: level, hits left), jeda_* (scroll cooldown ends)
 *   kota_&lt;situsId&gt;          one Kota Iblis (KotaTemaData.kota("iblis", situsId))
 *     kas                  L   the city purse (Kas Kota Iblis): arena fees and auction commission go in, prizes come out
 *     penguasa             S   xue_yao | tu_gang | ye_wuhen (the ruler after chapter 7)
 *     kandang_tutup        B   Lurus route: the pens are closed for good
 *     rumah_tebusan        B   Penebusan route: the pens are the House of Ransom (half price, NPC escort)
 *     segel_terbuka        B   Darah route: floor 9 open for the player who walked it (see pemain gelar tamu_darah)
 *     kitab_stok           I   Kitab Darah Senja left (1 per town)
 *     tawanan              []  captives (KandangTawanan)          tawanan_seq I, tawanan_hari L
 *     buronan              []  named demonic fugitives (BuronanIblis)
 *     kosong_sampai        L   world day until which the pens stay empty (righteous siege won)
 *     pasar_tutup_sampai   L   world day until which the black market is shut
 *     upeti_naik_sampai    L   world day until which the gate toll is x1.5 (the city held the siege)
 *     serbu_hari           L   next siege day (PenyerbuanLurus)
 *     p.&lt;uuid&gt;              per player in this town: upeti_hari, peringkat 1-9, laga_hari/laga_n, tebus_hari/tebus_n,
 *                          pil_hari/pil_n, peta_hari, penyusup_sampai, zp_larang_sampai, langgar_sampai, berburu_hari,
 *                          gentar_hari (last decay day), taruhan (bets of tonight), palu (prize given)
 * </pre>
 */
public final class IblisData {

	public static final String TEMA = "iblis";

	public static final String GELAR_JUARA = "juara_tangga_darah";
	public static final String GELAR_PEMUTUS = "pemutus_rantai";
	public static final String GELAR_FAJAR = "pembawa_fajar";
	public static final String GELAR_TAMU = "tamu_darah";
	public static final String GELAR_DITAKUTI = "ditakuti";

	public static final String PENGUASA_XUE = "xue_yao";
	public static final String PENGUASA_TU = "tu_gang";
	public static final String PENGUASA_YE = "ye_wuhen";

	/** Starting purse of a new Kota Iblis (NB). */
	public static final long KAS_AWAL = 2400L;

	private IblisData() {
	}

	public static KotaTemaData data(MinecraftServer server) {
		return KotaTemaData.get(server);
	}

	/** The theme section {@code bagian("iblis")}. */
	public static CompoundTag bagian(MinecraftServer server) {
		return data(server).bagian(TEMA);
	}

	/** One town's compound; the purse and the ruler are seeded on first use. */
	public static CompoundTag kota(MinecraftServer server, long situsId) {
		CompoundTag town = data(server).kota(TEMA, situsId);
		if (!town.contains("kas")) {
			town.putLong("kas", KAS_AWAL);
			town.putString("penguasa", PENGUASA_XUE);
			town.putInt("kitab_stok", 1);
			data(server).ubah();
		}
		return town;
	}

	/** The player's compound inside one town ({@code p.<uuid>}). */
	public static CompoundTag pemainKota(MinecraftServer server, long situsId, UUID player) {
		return sub(kota(server, situsId), "p." + player);
	}

	/** The player's global compound ({@code pemain.<uuid>}). */
	public static CompoundTag pemain(MinecraftServer server, UUID player) {
		return sub(sub(bagian(server), "pemain"), player.toString());
	}

	public static void ubah(MinecraftServer server) {
		data(server).ubah();
	}

	/** A child compound, created and attached on first use. */
	public static CompoundTag sub(CompoundTag parent, String key) {
		CompoundTag child = parent.getCompoundOrEmpty(key);
		if (!parent.contains(key)) {
			parent.put(key, child);
		}
		return child;
	}

	/** A child list of compounds, created and attached on first use. */
	public static ListTag daftar(CompoundTag parent, String key) {
		ListTag list = parent.getListOrEmpty(key);
		if (!parent.contains(key)) {
			parent.put(key, list);
		}
		return list;
	}

	// ------------------------------------------------------------------ purse

	public static long kas(MinecraftServer server, long situsId) {
		return kota(server, situsId).getLongOr("kas", KAS_AWAL);
	}

	public static void tambahKas(MinecraftServer server, long situsId, long delta) {
		CompoundTag town = kota(server, situsId);
		town.putLong("kas", Math.max(0L, town.getLongOr("kas", KAS_AWAL) + delta));
		ubah(server);
	}

	/** Takes up to {@code want} from the purse; returns what was taken (never mints money). */
	public static long ambilKas(MinecraftServer server, long situsId, long want) {
		CompoundTag town = kota(server, situsId);
		long have = town.getLongOr("kas", KAS_AWAL);
		long take = Math.max(0L, Math.min(have, want));
		town.putLong("kas", have - take);
		ubah(server);
		return take;
	}

	// ------------------------------------------------------------------ titles and route

	public static boolean gelar(ServerPlayer player, String gelar) {
		return pemain(player.level().getServer(), player.getUUID()).getCompoundOrEmpty("gelar").getBooleanOr(gelar, false);
	}

	/** Gives a title; true when it is new. */
	public static boolean beriGelar(ServerPlayer player, String gelar) {
		MinecraftServer server = player.level().getServer();
		CompoundTag titles = sub(pemain(server, player.getUUID()), "gelar");
		if (titles.getBooleanOr(gelar, false)) {
			return false;
		}
		titles.putBoolean(gelar, true);
		ubah(server);
		return true;
	}

	/** The player's route of the chain {@code tema_iblis_rantai}: "" (none yet), lurus, penebusan or darah. */
	public static String rute(ServerPlayer player) {
		return pemain(player.level().getServer(), player.getUUID()).getStringOr("rute", "");
	}

	public static void setRute(ServerPlayer player, String rute) {
		MinecraftServer server = player.level().getServer();
		pemain(server, player.getUUID()).putString("rute", rute == null ? "" : rute);
		ubah(server);
	}

	public static String penguasa(MinecraftServer server, long situsId) {
		return kota(server, situsId).getStringOr("penguasa", PENGUASA_XUE);
	}

	// ------------------------------------------------------------------ per-day counters

	/** The count of {@code what} the player used today in this town ({@code <what>_hari}/{@code <what>_n}). */
	public static int hariIni(MinecraftServer server, long situsId, UUID player, String what, long hari) {
		CompoundTag p = pemainKota(server, situsId, player);
		return p.getLongOr(what + "_hari", -1L) == hari ? p.getIntOr(what + "_n", 0) : 0;
	}

	public static void hitungHariIni(MinecraftServer server, long situsId, UUID player, String what, long hari) {
		CompoundTag p = pemainKota(server, situsId, player);
		int n = p.getLongOr(what + "_hari", -1L) == hari ? p.getIntOr(what + "_n", 0) : 0;
		p.putLong(what + "_hari", hari);
		p.putInt(what + "_n", n + 1);
		ubah(server);
	}

	/** A game-time deadline flag of the player in this town ({@code penyusup_sampai}, ...). */
	public static boolean sampai(MinecraftServer server, long situsId, UUID player, String key) {
		return pemainKota(server, situsId, player).getLongOr(key, 0L) > server.overworld().getGameTime();
	}

	public static void setSampai(MinecraftServer server, long situsId, UUID player, String key, long ticks) {
		pemainKota(server, situsId, player).putLong(key, server.overworld().getGameTime() + ticks);
		ubah(server);
	}

	public static void hapus(MinecraftServer server, long situsId, UUID player, String key) {
		CompoundTag p = pemainKota(server, situsId, player);
		if (p.contains(key)) {
			p.remove(key);
			ubah(server);
		}
	}

	/** Removes the entry with {@code id} from a list of compounds; true when found. */
	public static boolean buang(ListTag list, int id) {
		for (int i = 0; i < list.size(); i++) {
			Tag t = list.get(i);
			if (t instanceof CompoundTag c && c.getIntOr("id", -1) == id) {
				list.remove(i);
				return true;
			}
		}
		return false;
	}

	/** The entry with {@code id} of a list of compounds, or null. */
	public static CompoundTag cari(ListTag list, int id) {
		for (Tag t : list) {
			if (t instanceof CompoundTag c && c.getIntOr("id", -1) == id) {
				return c;
			}
		}
		return null;
	}
}
