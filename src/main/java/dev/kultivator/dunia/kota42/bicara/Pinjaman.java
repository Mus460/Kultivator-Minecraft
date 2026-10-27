package dev.kultivator.dunia.kota42.bicara;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kenangan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Cakap;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kotabesar.Hukum;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Loans between players and residents (DESIGN 42.5.7), kept in {@code KotaBicara.Data} section {@code pinjam}:
 * {@code L} list of {@code {i id, s situs, w warga, p player, a outstanding NB, u interest % per 7 days (borrowing) or the
 * agreed total % (lending), d day made, t due day, r 0 the player borrowed / 1 the player lent, st 0 open / 1 ready to pay
 * back / 2 defaulted-sued, g sued flag}}, {@code n} next id, {@code b_<uuid>} interest received this week. The resident's
 * {@code Ingatan.utang} always mirrors the signed sum (+ the resident owes the player).
 * <ul>
 * <li><b>Borrowing from a resident</b>: rich traders, bankers and officials (prosperity &ge; 60, impression &ge; 20) lend at
 * most {@code min(wealth, 50 x (b+1))} NB, 5 % per 7 days, due in 7 days. The money comes out of the place's Kas Kota (never
 * minted); a town too poor to pay refuses. Repaying ({@code pinjamkan:NB} while in debt) goes back to the Kas Kota. A missed
 * due day: impression -20 with the {@code UTANG_MANGKIR} memory, the {@code INGKAR} gossip, and in a capital a suit at the
 * Yamen (crime points worth the debt + 20 %), once.</li>
 * <li><b>Lending to a resident</b> (request {@code pinjam_uang}): on the due day the resident pays back unless it defaults
 * by temperament (jujur 2 %, ramah 5 %, licik 30 %, serakah 20 %, others 8 %); the principal comes back from the household,
 * the interest from the Kas Kota, at most {@code 10 x (b+1)} NB of interest per player and week (faucet 42.6.0). The money
 * is handed over the next time the player talks to the resident.</li>
 * <li>Debts are inherited (42.4.6): when a debtor dies the loan moves to a living household member.</li>
 * </ul>
 */
public final class Pinjaman {

	/** Interest per 7 days when borrowing (42.5.7). */
	public static final int BUNGA_PINJAM = 5;
	/** Days until due. */
	public static final int JATUH_TEMPO = 7;
	/** Lending interest cap per player and week: 10 x (b+1) NB. */
	public static final int BUNGA_MINGGU = 10;
	/** Lender requirements. */
	public static final int KEMAKMURAN_MIN = 60;
	public static final int KESAN_MIN = 20;
	/** Default chance of other temperaments when paying back. */
	private static final double MANGKIR_LAIN = 0.08D;

	static final int ARAH_PINJAM = 0;
	static final int ARAH_PINJAMKAN = 1;
	static final int BUKA = 0;
	static final int SIAP = 1;
	static final int MANGKIR = 2;

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		KotaBus.WAFAT.register((server, situs, warga, sebab) -> waris(server, situs, warga));
	}

	// ================================================================== borrowing from a resident

	/** The most a player may borrow from {@code w} now (0 when not allowed). */
	static long batasPinjam(ServerPlayer player, Warga w) {
		MinecraftServer server = player.level().getServer();
		long situs = WargaData.get(server).situsDari(w.id);
		int kaya = Suasana.kemakmuranRumahTangga(server, situs, w);
		long cap = Math.min(kaya * 2L, 50L * (Barang.pita(player) + 1));
		return Math.max(0L, Math.min(cap, KasKota.saldo(server, situs)));
	}

	/** Why {@code w} will not lend to the player, or null when it will. */
	static @Nullable Component alasanPinjam(ServerPlayer player, Warga w) {
		init();
		if (!KotaConfig.bicara().pinjaman) {
			return Component.translatable("kultivator.kota42.bicara.pinjam.mati");
		}
		MinecraftServer server = player.level().getServer();
		long situs = WargaData.get(server).situsDari(w.id);
		NpcRole role = w.peranNpc();
		boolean kaya = role.kelompok == NpcRole.Kelompok.PEDAGANG || role == NpcRole.BANKIR || role == NpcRole.PIALANG
				|| role.kelompok == NpcRole.Kelompok.PEJABAT;
		if (!kaya || WargaApi.umur(w) < 18) {
			return Component.translatable("kultivator.kota42.bicara.pinjam.bukan_pemodal");
		}
		if (Suasana.kemakmuranRumahTangga(server, situs, w) < KEMAKMURAN_MIN) {
			return Component.translatable("kultivator.kota42.bicara.pinjam.tak_mampu");
		}
		if (WargaApi.ingatan(w, player.getUUID()).kesan < KESAN_MIN) {
			return Component.translatable("kultivator.kota42.bicara.pinjam.belum_percaya", KESAN_MIN);
		}
		if (cari(server, w.id, player.getUUID(), ARAH_PINJAM) != null) {
			return Component.translatable("kultivator.kota42.bicara.pinjam.masih_utang");
		}
		if (batasPinjam(player, w) < 5L) {
			return Component.translatable("kultivator.kota42.bicara.pinjam.kas_kosong");
		}
		return null;
	}

	/** [Pinjam NB]: the resident lends {@code nb} (capped) out of the town chest. */
	static BicaraWarga.Balasan pinjam(ServerPlayer player, KultivatorNpc npc, Warga w, long nb) {
		Component why = alasanPinjam(player, w);
		if (why != null) {
			return BicaraWarga.Balasan.gagal(why);
		}
		MinecraftServer server = player.level().getServer();
		long situs = WargaData.get(server).situsDari(w.id);
		long amount = Math.min(Math.max(1L, nb), batasPinjam(player, w));
		if (!KasKota.bayar(server, situs, amount, "pinjaman")) {
			return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.pinjam.kas_kosong"));
		}
		NilaiBatu.give(player, amount);
		long hari = KotaHidup.hari(server);
		simpan(server, situs, w, player.getUUID(), amount, BUNGA_PINJAM, hari, ARAH_PINJAM);
		Ingatan.ubahUtang(WargaData.get(server), w, player.getUUID(), (int) -amount);
		Cakap.janji(w, player, "BERUTANG", hari + JATUH_TEMPO, "uang");
		HidupApi.klip(npc, GerakId.HITUNG_SEMPOA, 0, 60);
		Component line = Component.translatable("kultivator.kota42.bicara.pinjam.beri." + Math.floorMod(w.id, 3), amount, JATUH_TEMPO, BUNGA_PINJAM);
		npc.say(line, 120);
		return new BicaraWarga.Balasan(line, Component.translatable("kultivator.kota42.bicara.pinjam.ok", amount, JATUH_TEMPO), true);
	}

	/**
	 * [Pinjamkan NB]: money from the player to the resident. While the player owes this resident it is a repayment (back
	 * into the town chest); otherwise there is nothing to pay (lending happens through the {@code pinjam_uang} request).
	 */
	static BicaraWarga.Balasan pinjamkan(ServerPlayer player, KultivatorNpc npc, Warga w, long nb) {
		init();
		MinecraftServer server = player.level().getServer();
		CompoundTag loan = cari(server, w.id, player.getUUID(), ARAH_PINJAM);
		if (loan == null) {
			return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.pinjam.tak_ada_utang"));
		}
		long owed = loan.getLongOr("a", 0L);
		long pay = Math.min(owed, Math.max(1L, nb));
		pay = Math.min(pay, NilaiBatu.total(player));
		if (pay <= 0L || !NilaiBatu.pay(player, pay)) {
			return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.aksi.tanpa_uang"));
		}
		long situs = loan.getLongOr("s", 0L);
		KasKota.pajak(server, situs, pay, "pinjaman");
		Ingatan.ubahUtang(WargaData.get(server), w, player.getUUID(), (int) pay);
		loan.putLong("a", owed - pay);
		KotaBicara.Data.get(server).ubah();
		Component line;
		if (owed - pay <= 0L) {
			hapus(server, loan.getIntOr("i", 0));
			WargaApi.peristiwaPemain(player, situs, "utang_lunas", w);
			Cakap.tepati(w, player, "uang");
			TujuanKota.bayarUtang(player, w);
			line = Component.translatable("kultivator.kota42.bicara.pinjam.lunas." + Math.floorMod(w.id, 2));
			HidupApi.klip(npc, GerakId.HORMAT, 0, 40);
		} else {
			line = Component.translatable("kultivator.kota42.bicara.pinjam.cicil", pay, owed - pay);
			HidupApi.klip(npc, GerakId.ANGGUK_DENGAR, 0, 40);
		}
		npc.say(line, 100);
		return new BicaraWarga.Balasan(line, Component.translatable("kultivator.kota42.bicara.pinjam.dibayar", pay), true);
	}

	// ================================================================== lending to a resident (request pinjam_uang)

	/** The player lent {@code nb} to {@code w} (request {@code pinjam_uang} done), {@code bunga} % agreed in total. */
	static void catatPinjamkan(ServerPlayer player, Warga w, int nb, int bunga) {
		init();
		if (nb <= 0) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		long situs = WargaData.get(server).situsDari(w.id);
		long hari = KotaHidup.hari(server);
		simpan(server, situs, w, player.getUUID(), nb, Math.max(0, Math.min(10, bunga)), hari, ARAH_PINJAMKAN);
		Ingatan.ubahUtang(WargaData.get(server), w, player.getUUID(), nb);
	}

	/** When the player talks to a resident who is ready to pay back, the money changes hands. */
	static void bicara(ServerPlayer player, KultivatorNpc npc, Warga w) {
		MinecraftServer server = player.level().getServer();
		CompoundTag loan = cari(server, w.id, player.getUUID(), ARAH_PINJAMKAN);
		if (loan == null || loan.getIntOr("st", BUKA) != SIAP) {
			return;
		}
		long situs = loan.getLongOr("s", 0L);
		long pokok = loan.getLongOr("a", 0L);
		long bunga = Math.round(pokok * loan.getIntOr("u", 0) / 100.0D);
		long cap = BUNGA_MINGGU * (Barang.pita(player) + 1L);
		CompoundTag part = KotaBicara.Data.get(server).bagian(KotaBicara.Data.PINJAM);
		CompoundTag wk = part.getCompoundOrEmpty("b_" + player.getUUID());
		long minggu = KotaHidup.hari(server) / 7L;
		if (wk.getLongOr("w", -1L) != minggu) {
			wk.putLong("w", minggu);
			wk.putLong("nb", 0L);
		}
		long room = Math.max(0L, cap - wk.getLongOr("nb", 0L));
		long paidInterest = KasKota.ambil(server, situs, Math.min(bunga, room));
		wk.putLong("nb", wk.getLongOr("nb", 0L) + paidInterest);
		dev.kultivator.dunia.kota42.ekonomi.Investasi.catatKeran(server, player.getUUID(), dev.kultivator.dunia.kota42.ekonomi.Investasi.K_BUNGA, paidInterest);
		part.put("b_" + player.getUUID(), wk);
		NilaiBatu.give(player, pokok + paidInterest);
		Ingatan.ubahUtang(WargaData.get(server), w, player.getUUID(), (int) -pokok);
		hapus(server, loan.getIntOr("i", 0));
		WargaApi.ubahKesan(w, player, 5, "pinjaman_lunas", Kenangan.DIBANTU);
		Component line = Component.translatable("kultivator.kota42.bicara.pinjam.kembali." + Math.floorMod(w.id, 2), pokok, paidInterest);
		npc.say(line, 120);
		HidupApi.klip(npc, GerakId.HORMAT, 0, 40);
		Msg.chat(player, Component.translatable("kultivator.kota42.bicara.pinjam.kembali_chat", TokohKota.nama(w), pokok, paidInterest)
				.withStyle(ChatFormatting.GREEN));
	}

	// ================================================================== daily pass

	static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
		init();
		ListTag list = list(server);
		boolean changed = false;
		for (int i = 0; i < list.size(); i++) {
			CompoundTag loan = list.getCompoundOrEmpty(i);
			if (loan.getLongOr("s", 0L) != s.id) {
				continue;
			}
			Warga w = WargaApi.byId(loan.getIntOr("w", 0));
			UUID player = uuid(loan);
			if (w == null || player == null) {
				continue;
			}
			long made = loan.getLongOr("d", hari);
			if (loan.getIntOr("r", 0) == ARAH_PINJAM) {
				// compound 5 % every 7 days on what is still owed
				if (hari > made && (hari - made) % 7L == 0L && loan.getLongOr("a", 0L) > 0L) {
					long owed = loan.getLongOr("a", 0L);
					long add = Math.max(1L, Math.round(owed * loan.getIntOr("u", BUNGA_PINJAM) / 100.0D));
					loan.putLong("a", owed + add);
					Ingatan.ubahUtang(WargaData.get(server), w, player, (int) -add);
					changed = true;
				}
				if (hari > loan.getLongOr("t", made + JATUH_TEMPO) && loan.getIntOr("st", BUKA) == BUKA) {
					mangkir(server, s.id, loan, w, player, hari);
					changed = true;
				}
				if (loan.getIntOr("st", BUKA) == MANGKIR && !loan.getBooleanOr("g", false)) {
					changed |= gugat(server, s.id, loan, player);
				}
			} else if (loan.getIntOr("st", BUKA) == BUKA && hari >= loan.getLongOr("t", made + JATUH_TEMPO)) {
				RandomSource r = RandomSource.create(loan.getIntOr("i", 0) * 0x9E3779B97F4A7C15L ^ hari);
				double p = switch (PermintaanWarga.watak(w)) {
					case "jujur" -> 0.02D;
					case "ramah" -> 0.05D;
					case "licik" -> 0.30D;
					case "serakah" -> 0.20D;
					default -> MANGKIR_LAIN;
				};
				if (r.nextDouble() < p) {
					// the household cannot or will not pay: the money is lost, the shame is gossip
					WargaData data = WargaData.get(server);
					Ingatan.ubahUtang(data, w, player, (int) -loan.getLongOr("a", 0L));
					list.remove(i);
					i--;
					GosipWarga.lahir(server.overworld(), s.id, "INGKAR", Fakta.Rujuk.warga(w.id), Fakta.Rujuk.pemain(data.indeksPemain(player)));
					ServerPlayer online = server.getPlayerList().getPlayer(player);
					if (online != null) {
						Msg.chat(online, Component.translatable("kultivator.kota42.bicara.pinjam.warga_mangkir", TokohKota.nama(w)).withStyle(ChatFormatting.RED));
					}
				} else {
					loan.putInt("st", SIAP);
				}
				changed = true;
			}
		}
		if (changed) {
			KotaBicara.Data.get(server).ubah();
		}
	}

	private static void mangkir(MinecraftServer server, long situs, CompoundTag loan, Warga w, UUID player, long hari) {
		loan.putInt("st", MANGKIR);
		Ingatan ing = WargaApi.ingatan(w, player);
		ing.ubah(Kenangan.UTANG_MANGKIR.delta, Kenangan.UTANG_MANGKIR, false, hari);
		WargaData data = WargaData.get(server);
		data.ubah();
		int idx = data.indeksPemain(player);
		GosipWarga.lahir(server.overworld(), situs, "INGKAR", Fakta.Rujuk.pemain(idx), Fakta.Rujuk.warga(w.id));
		Cakap.peristiwa(w, "janji_diingkari", Fakta.Rujuk.pemain(idx), 1.0F);
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) {
			Msg.chat(online, Component.translatable("kultivator.kota42.bicara.pinjam.mangkir", TokohKota.nama(w), loan.getLongOr("a", 0L))
					.withStyle(ChatFormatting.RED));
		}
	}

	/** In a capital a defaulted debt goes to the Yamen once: crime points worth the debt + 20 % (the debt is then the fine). */
	private static boolean gugat(MinecraftServer server, long situs, CompoundTag loan, UUID player) {
		if (!Towns.isKotaBesar(server, situs)) {
			loan.putBoolean("g", true);
			return true;
		}
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online == null) {
			return false;
		}
		long owed = loan.getLongOr("a", 0L);
		long perPoint = 3L * (Barang.pita(online) + 1);
		int points = (int) Math.max(1L, (owed * 12L / 10L + perPoint - 1L) / perPoint);
		Hukum.kejahatan(online, situs, Hukum.TOLAK_TAGIHAN, points, online.blockPosition(), false);
		Warga w = WargaApi.byId(loan.getIntOr("w", 0));
		if (w != null) {
			Ingatan.ubahUtang(WargaData.get(server), w, player, (int) owed);
		}
		loan.putBoolean("g", true);
		hapus(server, loan.getIntOr("i", 0));
		Msg.chat(online, Component.translatable("kultivator.kota42.bicara.pinjam.digugat", owed).withStyle(ChatFormatting.DARK_RED));
		return true;
	}

	/** A dead debtor's loans pass to a living member of the household (42.4.6); with nobody left they lapse. */
	private static void waris(MinecraftServer server, long situs, Warga dead) {
		ListTag list = list(server);
		WargaData data = WargaData.get(server);
		WargaData.Situs s = data.peek(situs);
		Warga heir = null;
		if (s != null) {
			for (Warga k : s.warga) {
				if (k != dead && k.status == Warga.Status.HIDUP && dead.rumahTangga != 0 && k.rumahTangga == dead.rumahTangga && WargaApi.umur(k) >= 16) {
					heir = k;
					break;
				}
			}
		}
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag loan = list.getCompoundOrEmpty(i);
			if (loan.getIntOr("w", 0) != dead.id) {
				continue;
			}
			UUID player = uuid(loan);
			if (heir == null || player == null) {
				list.remove(i);
				continue;
			}
			loan.putInt("w", heir.id);
			long signed = loan.getIntOr("r", 0) == ARAH_PINJAM ? -loan.getLongOr("a", 0L) : loan.getLongOr("a", 0L);
			Ingatan.ubahUtang(data, heir, player, (int) signed);
		}
		KotaBicara.Data.get(server).ubah();
	}

	// ================================================================== storage

	private static ListTag list(MinecraftServer server) {
		CompoundTag part = KotaBicara.Data.get(server).bagian(KotaBicara.Data.PINJAM);
		ListTag list = part.getListOrEmpty("L");
		if (!part.contains("L")) {
			part.put("L", list);
		}
		return list;
	}

	private static void simpan(MinecraftServer server, long situs, Warga w, UUID player, long nb, int bunga, long hari, int arah) {
		CompoundTag part = KotaBicara.Data.get(server).bagian(KotaBicara.Data.PINJAM);
		int id = part.getIntOr("n", 1);
		part.putInt("n", id + 1);
		CompoundTag c = new CompoundTag();
		c.putInt("i", id);
		c.putLong("s", situs);
		c.putInt("w", w.id);
		c.putString("p", player.toString());
		c.putLong("a", nb);
		c.putInt("u", bunga);
		c.putLong("d", hari);
		c.putLong("t", hari + JATUH_TEMPO);
		c.putInt("r", arah);
		c.putInt("st", BUKA);
		list(server).add(c);
		KotaBicara.Data.get(server).ubah();
	}

	private static @Nullable CompoundTag cari(MinecraftServer server, int warga, UUID player, int arah) {
		ListTag list = list(server);
		String p = player.toString();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag c = list.getCompoundOrEmpty(i);
			if (c.getIntOr("w", 0) == warga && c.getStringOr("p", "").equals(p) && c.getIntOr("r", 0) == arah) {
				return c;
			}
		}
		return null;
	}

	private static void hapus(MinecraftServer server, int id) {
		ListTag list = list(server);
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.getCompoundOrEmpty(i).getIntOr("i", -1) == id) {
				list.remove(i);
			}
		}
		KotaBicara.Data.get(server).ubah();
	}

	private static @Nullable UUID uuid(CompoundTag c) {
		try {
			return UUID.fromString(c.getStringOr("p", ""));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** What the player owes {@code w} (negative) or is owed (positive), from the open loans; 0 when none. */
	public static long saldo(MinecraftServer server, Warga w, UUID player) {
		long sum = 0L;
		CompoundTag a = cari(server, w.id, player, ARAH_PINJAM);
		CompoundTag b = cari(server, w.id, player, ARAH_PINJAMKAN);
		if (a != null) {
			sum -= a.getLongOr("a", 0L);
		}
		if (b != null && b.getIntOr("st", BUKA) != MANGKIR) {
			sum += b.getLongOr("a", 0L);
		}
		return sum;
	}

	private Pinjaman() {
	}
}
