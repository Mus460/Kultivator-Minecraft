package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.BalaiLelang;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.ekonomi.ProfilLelang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lelang Gelap (DESIGN 38.T.7.6): the black auction of the Balai Lelang Gelap, every world day with {@code d % 9 == 4}
 * from 18000 to 20000 (calendar event {@code lelang_gelap}). It is the single auction engine of 38EK
 * ({@code BalaiLelang.mulai} with the profile below, never a third auction system): 5 lots of the table
 * {@code data/kultivator/tempat/lelang/gelap.json} (rare poisons, forbidden scrolls of the shadow pool, demonic
 * treasures of grade 2-3, Secret Realm keys, the Kontrak Kepala), 2 consignments per player, 15 % commission, bidders from
 * the rival groups and the city's own shadow brokers ({@code iblis_tokoh}). The commission is a sink: 38EK books it into
 * the town treasury ({@code KasKota}) of the auction key; when a session this class started ends, the commission that
 * came in during it is moved from that treasury into the Kas Kota Iblis (never minted), so the arena's prizes keep a real
 * source.
 * <p>
 * The auctioneer is the {@code JURU_LELANG} body of anchor {@code kota_tema/lelang_gelap} ({@link IblisAnchors}); a session
 * starts only while a player is in the hall (the engine's own presence rule), and is announced to the players in town.
 */
public final class LelangGelap {

	public static final String ACARA = "lelang_gelap";
	public static final int DARI = 18000;
	public static final int SAMPAI = 20000;
	public static final int SIKLUS = 9;
	public static final int SISA = 4;
	/** 5 lots, 2 consignments, 15 % commission, rival groups + the shadow brokers, lot table {@code gelap}. */
	public static final ProfilLelang PROFIL = new ProfilLelang(5, 2, 0.15D, List.of("saingan", "iblis_tokoh"), "gelap");

	/** Town id -> the auctioneer body of the hall (from the anchor). */
	private static final Map<Long, KultivatorNpc> JURU = new HashMap<>();
	/** Towns whose session this class started (to settle the purse share at the end). */
	private static final Map<Long, Long> DIMULAI = new HashMap<>();

	private LelangGelap() {
	}

	static void init() {
		TemaKalender.daftar(IblisData.TEMA, ACARA, DARI, SAMPAI, TemaKalender.setiap(SIKLUS, SISA), new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				buka(level, situsId, dipaksa);
			}

			@Override
			public void tick(ServerLevel level, long situsId, int tickKe) {
				if (!DIMULAI.containsKey(situsId)) {
					buka(level, situsId, false);
				}
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				tutup(level, situsId);
			}
		});
	}

	static void clear() {
		JURU.clear();
		DIMULAI.clear();
	}

	/** Anchor {@code kota_tema/lelang_gelap}: remembers the auctioneer and lets him count coins between sessions. */
	static void nearby(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, @Nullable KultivatorNpc juru, List<ServerPlayer> within) {
		if (juru == null) {
			return;
		}
		JURU.put(kota.situsId(), juru);
		boolean running = BalaiLelang.berjalan(BalaiLelang.kunci(juru));
		if (!running && juru.getTarget() == null && (level.getGameTime() + juru.getId()) % 200L == 0L) {
			HidupApi.klip(juru, IblisKota.malam(level) ? GerakId.HITUNG_KOIN_GELAP : GerakId.SIKAP_TANGAN_BELAKANG, 0, 0);
		}
	}

	/** True when today is a black-auction day (the calendar's own rule, for screens and rumours). */
	public static boolean hariLelang(long hari) {
		return Math.floorMod(hari, (long) SIKLUS) == SISA;
	}

	/** Days until the next black auction (0 = today). */
	public static int hariLagi(long hari) {
		return (int) Math.floorMod(SISA - hari, (long) SIKLUS);
	}

	private static void buka(ServerLevel level, long situsId, boolean dipaksa) {
		KultivatorNpc juru = JURU.get(situsId);
		if (juru == null || juru.isRemoved() || !juru.isAlive()) {
			return;
		}
		if (IblisData.kota(level.getServer(), situsId).getLongOr("pasar_tutup_sampai", -1L) >= IblisKota.hari(level) && !dipaksa) {
			return;
		}
		if (!BalaiLelang.mulai(level, juru, PROFIL)) {
			return;
		}
		DIMULAI.put(situsId, KasKota.saldo(level.getServer(), BalaiLelang.kunci(juru)));
		IblisKota.timpa(level, situsId, "tema." + ACARA, SAMPAI - DARI);
		HidupApi.klip(juru, GerakId.BICARA_TANGAN, 0, 80);
		Voice.say(juru, "tema.iblis.broker", null);
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), situsId);
		if (kota == null) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (kota.berisi(p.getBlockX(), p.getBlockZ())) {
				Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.lelang.mulai").withStyle(ChatFormatting.DARK_PURPLE));
			}
		}
	}

	private static void tutup(ServerLevel level, long situsId) {
		IblisKota.lepasTimpa(level, situsId, "tema." + ACARA);
		Long before = DIMULAI.remove(situsId);
		KultivatorNpc juru = JURU.get(situsId);
		if (before == null || juru == null) {
			return;
		}
		long key = BalaiLelang.kunci(juru);
		long komisi = KasKota.saldo(level.getServer(), key) - before;
		if (komisi > 0L) {
			IblisData.tambahKas(level.getServer(), situsId, KasKota.ambil(level.getServer(), key, komisi));
		}
	}

	/** Test helper ({@code uji tema iblis lelang}): starts a session at the hall now if its auctioneer stands. */
	static boolean uji(ServerPlayer player) {
		TemaKota.Kota kota = IblisKota.di(player);
		if (kota == null) {
			return false;
		}
		KultivatorNpc juru = JURU.get(kota.situsId());
		if (juru == null || juru.isRemoved()) {
			return false;
		}
		DIMULAI.put(kota.situsId(), KasKota.saldo(player.level().getServer(), BalaiLelang.kunci(juru)));
		BalaiLelang.mulaiUji(player, juru, PROFIL);
		return true;
	}
}
