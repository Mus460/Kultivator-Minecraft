package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pesta Darah (DESIGN 38.T.7.10): the Blood Feast of the Kota Iblis, for as long as the sky event Gerhana Darah runs
 * ({@code DuniaApi.sky().active == BLOOD_ECLIPSE}, read only; this class never changes the sky, the weather or the time).
 * It is the calendar event {@code pesta_darah}, never chosen by day: this class starts it in every live Kota Iblis when the
 * eclipse begins and ends it when the eclipse is over ({@code uji tema acara iblis pesta_darah} forces it).
 * <ul>
 * <li>The town's lanterns burn red (the client effect of the festival packet, 38TV), floor 9 of the forbidden tower opens
 * ({@link MenaraTerlarang#segelUntuk}), the Jam Berburu comes twice a night ({@link JamBerburu#jatahMalam}), Xue Yao
 * watches from the Lord's Box over the arena.</li>
 * <li><b>Turnamen Darah</b>: 8 gladiators and the player, single elimination; the player fights three rounds (tier band,
 * band + 1, band + 2, clamped 1-8) through {@link ArenaDarah} (kind {@code TURNAMEN}: no fee per bout, not counted in the
 * 5 a day). Entry {@code 15 x (band + 1)} NB to the purse; the champion takes 4 x the entry from the purse (never
 * minted: short purse, what it holds), Gentar +15, the advancement. Registration with Tu Gang (effect
 * {@code iblis_turnamen daftar}).</li>
 * <li>A player who saw the feast to its end without breaking the Market Pact is a guest of the Blood Feast
 * (advancement {@code tempat/tema_tamu_darah}).</li>
 * </ul>
 */
public final class PestaDarah {

	public static final String ACARA = "pesta_darah";
	public static final int DARI = 13000;
	public static final int SAMPAI = 23000;
	public static final int BABAK = 3;
	public static final int BIAYA = 15;
	public static final int KALI_HADIAH = 4;
	public static final long JEDA_BABAK = 30L * 20L;
	/** Canvas spot of the Lord's Box throne (tools/gen_tempat_tema_iblis.py bilik_penguasa: cx 48, floor LY + 8, z 18). */
	static final double[] BILIK = {48.5D, IblisKota.LY + 9, 18.5D};

	/** One player's run through the tournament. */
	private record Peserta(long situs, int babak, long biaya, long lanjut) {
	}

	private static final Map<UUID, Peserta> PESERTA = new HashMap<>();
	/** Towns whose feast was started by the eclipse (ended with it). */
	private static final Set<Long> GERHANA = new HashSet<>();
	/** Town id -> Xue Yao in the Lord's Box. */
	private static final Map<Long, KultivatorNpc> PENGUASA = new HashMap<>();
	/** Players who broke the Pact during the running feast of a town (no guest advancement). */
	private static final Set<UUID> NODA = new HashSet<>();

	private PestaDarah() {
	}

	static void init() {
		TemaKalender.daftar(IblisData.TEMA, ACARA, DARI, SAMPAI, (level, hari, situs, pusat) -> false, new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				buka(level, situsId);
			}

			@Override
			public void tick(ServerLevel level, long situsId, int tickKe) {
				hadirkanPenguasa(level, situsId);
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				tutup(level, situsId);
			}
		});
	}

	static void clear() {
		PESERTA.clear();
		GERHANA.clear();
		PENGUASA.clear();
		NODA.clear();
	}

	public static boolean berjalan(long situsId) {
		return TemaKalender.berjalan(situsId, IblisData.TEMA, ACARA);
	}

	/** Every 100 ticks: follows the sky; starts the feast in live Kota Iblis when the eclipse begins, ends it after. */
	static void ikutiLangit(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		boolean eclipse = DuniaApi.sky().active(overworld) == SkyKind.BLOOD_ECLIPSE;
		for (TemaKota.Kota kota : TemaKota.hidup(server, dev.kultivator.dunia.tempat.tema.Tema.IBLIS)) {
			long situs = kota.situsId();
			if (eclipse && !berjalan(situs)) {
				if (TemaKalender.paksa(overworld, situs, IblisData.TEMA, ACARA, true)) {
					GERHANA.add(situs);
				}
			} else if (!eclipse && GERHANA.remove(situs) && berjalan(situs)) {
				TemaKalender.paksa(overworld, situs, IblisData.TEMA, ACARA, false);
			}
		}
		// pact breakers lose the guest's honour for this feast
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			TemaKota.Kota kota = IblisKota.di(p);
			if (kota != null && berjalan(kota.situsId()) && IblisData.sampai(server, kota.situsId(), p.getUUID(), "langgar_sampai")) {
				NODA.add(p.getUUID());
			}
		}
	}

	private static void buka(ServerLevel level, long situs) {
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), situs);
		if (kota == null) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (kota.berisi(p.getBlockX(), p.getBlockZ())) {
				Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.pesta.mulai").withStyle(ChatFormatting.DARK_RED),
						Component.translatable("kultivator.kota_tema.iblis.pesta.mulai.sub").withStyle(ChatFormatting.RED), 10, 80, 20);
			}
		}
		level.playSound(null, kota.pusat(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.6F, 0.6F);
		IblisKota.timpa(level, situs, "tema." + ACARA, SAMPAI - DARI);
		CeritaApi.kabar(level, situs, "tema_iblis_pesta");
		hadirkanPenguasa(level, situs);
	}

	/** Xue Yao in the Lord's Box while the feast runs (only while she rules and a player is near). */
	private static void hadirkanPenguasa(ServerLevel level, long situs) {
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), situs);
		KultivatorNpc lord = PENGUASA.get(situs);
		if (kota == null || lord != null && !lord.isRemoved() && lord.isAlive()) {
			return;
		}
		if (!IblisAnchors.hadir(level, kota, TokohIblis.XUE_YAO)) {
			return;
		}
		BlockPos at = IblisKota.blok(kota, ArenaDarah.DISTRIK, (int) BILIK[0], (int) BILIK[1], (int) BILIK[2]);
		if (at == null) {
			return;
		}
		lord = TokohIblis.panggil(level, situs, TokohIblis.XUE_YAO, at, IblisKota.yaw(kota, 0.0F));
		if (lord != null) {
			lord.memory().putBoolean("hold", true);
			lord.setDiamDiTitik(true);
			HidupApi.klip(lord, GerakId.SIKAP_ANGKUH, 0, 0);
			PENGUASA.put(situs, lord);
		}
	}

	private static void tutup(ServerLevel level, long situs) {
		GERHANA.remove(situs);
		IblisKota.lepasTimpa(level, situs, "tema." + ACARA);
		KultivatorNpc lord = PENGUASA.remove(situs);
		if (lord != null && !lord.isRemoved()) {
			lord.vanish();
		}
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), situs);
		for (Map.Entry<UUID, Peserta> e : new HashMap<>(PESERTA).entrySet()) {
			if (e.getValue().situs() == situs) {
				PESERTA.remove(e.getKey());
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
				if (p != null) {
					Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.turnamen.bubar").withStyle(ChatFormatting.GRAY));
				}
			}
		}
		if (kota == null) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (kota.berisi(p.getBlockX(), p.getBlockZ())) {
				if (!NODA.contains(p.getUUID())) {
					Advancements.grant(p, "tempat/tema_tamu_darah");
				}
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.pesta.akhir").withStyle(ChatFormatting.GRAY));
			}
		}
		NODA.clear();
	}

	// ================================================================== the Blood Tournament

	/** Dialog effect {@code iblis_turnamen daftar}: pays the entry and starts round 1 (refusal shown on the action bar). */
	static void daftar(ServerPlayer p) {
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null || !berjalan(kota.situsId())) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.turnamen.tidak").withStyle(ChatFormatting.GRAY));
			return;
		}
		MinecraftServer server = p.level().getServer();
		if (PESERTA.containsKey(p.getUUID()) || IblisData.pemainKota(server, kota.situsId(), p.getUUID()).getLongOr("turnamen_hari", -1L) == IblisKota.hari(p.level())) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.turnamen.sudah").withStyle(ChatFormatting.GRAY));
			return;
		}
		long fee = (long) BIAYA * (IblisKota.band(p) + 1L);
		Component no = ArenaDarah.alasan(p, kota, ArenaDarah.JENIS_TURNAMEN);
		if (no != null) {
			Msg.actionBar(p, no.copy().withStyle(ChatFormatting.RED));
			return;
		}
		if (!NilaiBatu.pay(p, fee)) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(fee)).withStyle(ChatFormatting.RED));
			return;
		}
		IblisData.tambahKas(server, kota.situsId(), fee);
		IblisData.pemainKota(server, kota.situsId(), p.getUUID()).putLong("turnamen_hari", IblisKota.hari(p.level()));
		IblisData.ubah(server);
		PESERTA.put(p.getUUID(), new Peserta(kota.situsId(), 1, fee, 0L));
		Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.turnamen.daftar", NilaiBatu.text(fee), NilaiBatu.text(fee * KALI_HADIAH))
				.withStyle(ChatFormatting.DARK_RED));
		Component start = ArenaDarah.mulai(p, kota, ArenaDarah.JENIS_TURNAMEN, -1);
		if (start != null) {
			PESERTA.put(p.getUUID(), new Peserta(kota.situsId(), 1, fee, p.level().getGameTime() + JEDA_BABAK));
			Msg.actionBar(p, start.copy().withStyle(ChatFormatting.RED));
		}
	}

	/** The round the player is in (1-3), 1 when not registered. */
	static int babak(UUID player) {
		Peserta e = PESERTA.get(player);
		return e == null ? 1 : e.babak();
	}

	/** Foe tier of the player's current round: band - 1 + round, clamped 1-8. */
	static int tierBabak(ServerPlayer p, long situs) {
		return Mth.clamp(IblisKota.band(p) - 1 + babak(p.getUUID()), 1, 8);
	}

	/** From {@link ArenaDarah}: a bout of this town ended; for a tournament entrant the next round, the crown or the fall. */
	static void lagaSelesai(ServerPlayer p, long situs, boolean menang) {
		Peserta e = PESERTA.get(p.getUUID());
		if (e == null || e.situs() != situs) {
			return;
		}
		if (!menang) {
			PESERTA.remove(p.getUUID());
			Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.turnamen.gugur", e.babak()).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (e.babak() >= BABAK) {
			PESERTA.remove(p.getUUID());
			juara(p, e);
			return;
		}
		PESERTA.put(p.getUUID(), new Peserta(situs, e.babak() + 1, e.biaya(), p.level().getGameTime() + JEDA_BABAK));
		Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.turnamen.lanjut", e.babak() + 1, JEDA_BABAK / 20L).withStyle(ChatFormatting.RED));
	}

	/** Every 20 ticks: the next round starts when its wait is over (the player must still be in town). */
	static void tick(MinecraftServer server) {
		if (PESERTA.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Peserta> e : new HashMap<>(PESERTA).entrySet()) {
			Peserta pe = e.getValue();
			if (pe.lanjut() <= 0L) {
				continue;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				PESERTA.remove(e.getKey());
				continue;
			}
			if (p.level().getGameTime() < pe.lanjut() || ArenaDarah.sedang(p)) {
				continue;
			}
			TemaKota.Kota kota = IblisKota.di(p);
			if (kota == null || kota.situsId() != pe.situs()) {
				PESERTA.remove(e.getKey());
				Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.turnamen.pergi").withStyle(ChatFormatting.GRAY));
				continue;
			}
			PESERTA.put(e.getKey(), new Peserta(pe.situs(), pe.babak(), pe.biaya(), 0L));
			Component no = ArenaDarah.mulai(p, kota, ArenaDarah.JENIS_TURNAMEN, -1);
			if (no != null) {
				PESERTA.put(e.getKey(), new Peserta(pe.situs(), pe.babak(), pe.biaya(), p.level().getGameTime() + JEDA_BABAK));
				Msg.actionBar(p, no.copy().withStyle(ChatFormatting.RED));
			}
		}
	}

	private static void juara(ServerPlayer p, Peserta e) {
		MinecraftServer server = p.level().getServer();
		long want = e.biaya() * KALI_HADIAH;
		long prize = IblisData.ambilKas(server, e.situs(), want);
		NilaiBatu.give(p, prize);
		IblisMeter.tambahGentar(p, e.situs(), 15, "turnamen");
		Advancements.grant(p, "tempat/tema_iblis_turnamen_darah");
		CeritaApi.peristiwa(p, IblisCerita.MENANG_ARENA, "turnamen");
		CeritaApi.kabar(p.level(), e.situs(), "tema_iblis_turnamen", p.getName().getString());
		Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.turnamen.juara").withStyle(ChatFormatting.GOLD),
				Component.translatable("kultivator.kota_tema.iblis.arena.hadiah", NilaiBatu.text(prize)).withStyle(ChatFormatting.YELLOW), 10, 80, 20);
		KultivatorNpc lord = PENGUASA.get(e.situs());
		if (lord != null && !lord.isRemoved()) {
			HidupApi.klip(lord, GerakId.TEPUK_TANGAN, 0, 60);
			Voice.say(lord, "tema.iblis.lord", null);
		}
	}

	/** {@code uji tema iblis pesta}: forces the feast in the caller's town on or off. */
	static boolean uji(ServerPlayer p, boolean on) {
		TemaKota.Kota kota = IblisKota.di(p);
		return kota != null && TemaKalender.paksa(p.level().getServer().overworld(), kota.situsId(), IblisData.TEMA, ACARA, on);
	}

	static @Nullable KultivatorNpc penguasa(long situs) {
		return PENGUASA.get(situs);
	}
}
