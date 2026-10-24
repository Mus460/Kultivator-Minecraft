package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The capital arc "Bayangan di Balik Takhta" (DESIGN 38.S.3): one shared arc per capital, about 24 world days, seven
 * chapters. A chapter opens when its day has come and the previous one closed; it closes when a player reports its
 * outcome (effect {@code alur <bab> <hasil>} at the end of the chapter quest) or, never stuck, when
 * {@code cerita.batasBab} days pass, with the default outcome told by a rumour. Participation is per player (the
 * chapter quests); the outcome is the capital's.
 *
 * <pre>
 * bab  title               opens at day  quest (data)                default outcome
 * 1    Kedatangan          arc start     takhta_kedatangan           tiba
 * 2    Hilang di Kumuh     +1            takhta_hilang_di_kumuh      hilang
 * 3    Pewaris Sakit       +4            takhta_pewaris_sakit        lemah
 * 4    Dua Calon           +8            takhta_dua_calon            gu
 * 5    Topeng Terlepas     +12           takhta_topeng_terlepas      tersembunyi
 * 6    Malam Gerhana       +16           takhta_malam_gerhana        bertahan
 * 7    Penobatan           +20           takhta_penobatan            (crowned ruler)
 * </pre>
 * Rulers: Ruyi when chapter 4 backed her (or stayed neutral and chapter 5 unmasked Mo Ying by trial or duel) and she was
 * not left weak; Gu otherwise. Without any player Gu is crowned on day +28. Other modules read {@link #penguasa}
 * (38KS fines and raids, 38EK stock) and listen with {@link #addListener} (38KS: the special beast tide of chapter 6).
 */
public final class AlurTakhta {

	public static final int BAB_TERAKHIR = 7;
	/** Day of each chapter relative to the arc start (index = chapter). */
	private static final int[] HARI_BAB = {0, 0, 1, 4, 8, 12, 16, 20};
	/** Day the ruler is crowned without any player. */
	public static final int HARI_TANPA_PEMAIN = 28;
	private static final String[] QUEST = {"", "takhta_kedatangan", "takhta_hilang_di_kumuh", "takhta_pewaris_sakit", "takhta_dua_calon",
			"takhta_topeng_terlepas", "takhta_malam_gerhana", "takhta_penobatan"};
	private static final String[] BAWAAN = {"", "tiba", "hilang", "lemah", "gu", "tersembunyi", "bertahan", ""};
	/** Radius around the capital's site position that counts as "in the capital" when no place box is known. */
	private static final int JARI = 192;

	/** Chapter changes: {@code mulai} true when chapter {@code bab} opens, false when it closes with {@code hasil}. */
	@FunctionalInterface
	public interface Pendengar {
		void onBab(ServerLevel level, long situs, int bab, boolean mulai, String hasil);
	}

	private static final List<Pendengar> PENDENGAR = new CopyOnWriteArrayList<>();

	public static void addListener(Pendengar listener) {
		if (listener != null) {
			PENDENGAR.add(listener);
		}
	}

	// ================================================================== queries

	public static boolean ada(MinecraftServer server, long situs) {
		return situs != 0L && server != null && CeritaData.get(server).arc(situs).getIntOr("bab", 0) > 0;
	}

	/** Current chapter (0 not started, 1-7, 8 finished). */
	public static int bab(MinecraftServer server, long situs) {
		return situs == 0L || server == null ? 0 : CeritaData.get(server).arc(situs).getIntOr("bab", 0);
	}

	/** A stored arc value ({@code dukung}, {@code bab5}, {@code penguasa}); "" when unset. */
	public static String nilai(MinecraftServer server, long situs, String key) {
		return situs == 0L || server == null ? "" : CeritaData.get(server).arc(situs).getStringOr(key, "");
	}

	/** The crowned ruler ({@code ruyi} or {@code gu}), "" before chapter 7 ends. */
	public static String penguasa(MinecraftServer server, long situs) {
		return nilai(server, situs, "penguasa");
	}

	public static boolean dou(MinecraftServer server, long situs) {
		return situs != 0L && server != null && CeritaData.get(server).arc(situs).getBooleanOr("dou", false);
	}

	/** Proofs (0-3) the player gathered in this arc. */
	public static int bukti(ServerPlayer player, long situs) {
		if (situs == 0L) {
			return 0;
		}
		return CeritaData.get(player.level().getServer()).arc(situs).getCompoundOrEmpty("bukti").getIntOr(player.getUUID().toString(), 0);
	}

	/** Days left before the current chapter closes by itself. */
	public static int sisaHari(MinecraftServer server, long situs) {
		if (server == null || situs == 0L) {
			return 0;
		}
		CompoundTag arc = CeritaData.get(server).arc(situs);
		long left = arc.getLongOr("babHari", 0L) + CeritaConfig.get().batasBab - Kota.day(server);
		return (int) Math.max(0L, left);
	}

	/** The arc site nearest the player in the same dimension (0 none). */
	public static long terdekat(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CeritaData data = CeritaData.get(server);
		long best = 0L;
		long unpublished = 0L;
		double bestD = Double.MAX_VALUE;
		for (long site : data.arcSites()) {
			if (data.arc(site).getIntOr("bab", 0) <= 0) {
				continue;
			}
			DuniaSites.Site s = DuniaSites.byId(server, site);
			if (s == null) {
				unpublished = site; // a test arc without a published capital
				continue;
			}
			double d = s.distance(player.level().dimension(), player.blockPosition());
			if (d < bestD) {
				bestD = d;
				best = site;
			}
		}
		return best != 0L ? best : unpublished;
	}

	// ================================================================== start and tick

	/** The capital site the player stands in, or 0 (the 38 place box first, else a published capital site nearby). */
	static long ibuKotaDi(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
			return 0L;
		}
		TempatInfo info = TempatApi.tempatDi(level, player.blockPosition());
		if (info != null && info.jenis().startsWith("kota/kota_besar")) {
			return info.situsId();
		}
		List<DuniaSites.Site> near = DuniaSites.near(level, player.blockPosition(), JARI, "kota/kota_besar");
		return near.isEmpty() ? 0L : near.get(0).id();
	}

	/** True when the player stands in capital {@code site} (any overworld spot for a test arc without a published site). */
	static boolean diIbuKota(ServerPlayer player, long site) {
		long here = ibuKotaDi(player);
		if (here == site) {
			return true;
		}
		return here == 0L && player.level().dimension() == Level.OVERWORLD && DuniaSites.byId(player.level().getServer(), site) == null;
	}

	/** Story tick (every 100 ticks, spread): arrivals and chapter clocks. */
	static void tick(MinecraftServer server) {
		if (!CeritaConfig.jalan() || !CeritaConfig.get().alur) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long site = ibuKotaDi(player);
			if (site != 0L) {
				tiba(player, site);
			}
		}
		CeritaData data = CeritaData.get(server);
		long day = Kota.day(server);
		for (long site : data.arcSites()) {
			jam(server, site, day);
		}
	}

	/** A player in capital {@code site}: starts the arc there on the first arrival, joins the player, gives chapter 1. */
	static void tiba(ServerPlayer player, long site) {
		MinecraftServer server = player.level().getServer();
		CeritaData data = CeritaData.get(server);
		CompoundTag arc = data.arc(site);
		CompoundTag peserta = arc.getCompoundOrEmpty("peserta");
		String id = player.getUUID().toString();
		boolean baru = !peserta.getBooleanOr(id, false);
		if (baru) {
			peserta.putBoolean(id, true);
			arc.put("peserta", peserta);
			data.touch();
		}
		if (arc.getIntOr("bab", 0) <= 0) {
			long day = Kota.day(server);
			arc.putInt("bab", 1);
			arc.putLong("mulai", day);
			arc.putLong("babHari", day);
			arc.putBoolean("buka", true);
			data.setFirstCapital(site);
			data.touch();
			umumkan(server, site, 1, true, "");
			tutupBab(server, site, 1, BAWAAN[1]);
		}
		if (baru) {
			QuestTracker.mulai(player, QUEST[1], site);
			int bab = arc.getIntOr("bab", 0);
			if (bab >= 2 && bab <= BAB_TERAKHIR && arc.getBooleanOr("buka", false)) {
				QuestTracker.mulai(player, QUEST[bab], site);
			}
		}
	}

	private static void jam(MinecraftServer server, long site, long day) {
		CeritaData data = CeritaData.get(server);
		CompoundTag arc = data.arc(site);
		int bab = arc.getIntOr("bab", 0);
		if (bab <= 0 || bab > BAB_TERAKHIR) {
			return;
		}
		long start = arc.getLongOr("mulai", day);
		boolean open = arc.getBooleanOr("buka", false);
		if (!open) {
			// the previous chapter is closed: open this one when its day came
			if (day - start >= HARI_BAB[bab]) {
				bukaBab(server, site, bab);
			}
			return;
		}
		long since = day - arc.getLongOr("babHari", day);
		if (since >= CeritaConfig.get().batasBab || bab == BAB_TERAKHIR && day - start >= HARI_TANPA_PEMAIN) {
			tutupBab(server, site, bab, bab == BAB_TERAKHIR ? hitungPenguasa(arc) : BAWAAN[bab]);
		}
	}

	private static void bukaBab(MinecraftServer server, long site, int bab) {
		CeritaData data = CeritaData.get(server);
		CompoundTag arc = data.arc(site);
		arc.putInt("bab", bab);
		arc.putBoolean("buka", true);
		arc.putLong("babHari", Kota.day(server));
		data.touch();
		umumkan(server, site, bab, true, "");
		for (ServerPlayer player : peserta(server, site)) {
			QuestTracker.mulai(player, QUEST[bab], site);
		}
		ServerLevel level = server.overworld();
		KabarTempat.pemicu(level, site, "alur_bab" + bab);
		if (bab == 3) {
			adeganDekat(server, site, "ruyi_pingsan", 3);
		}
		if (bab == 6) {
			malamGerhana(server, site);
		}
		fire(level, site, bab, true, "");
	}

	/** Closes chapter {@code bab} with {@code hasil}; the next chapter opens on its day (story tick). */
	static void tutupBab(MinecraftServer server, long site, int bab, String hasil) {
		CeritaData data = CeritaData.get(server);
		CompoundTag arc = data.arc(site);
		if (arc.getIntOr("bab", 0) != bab) {
			return;
		}
		CompoundTag results = arc.getCompoundOrEmpty("hasil");
		results.putString(Integer.toString(bab), hasil);
		arc.put("hasil", results);
		switch (bab) {
			case 4 -> arc.putString("dukung", hasil);
			case 5 -> arc.putString("bab5", hasil);
			case 7 -> arc.putString("penguasa", hasil.isEmpty() ? hitungPenguasa(arc) : hasil);
			default -> {
			}
		}
		arc.putBoolean("buka", false);
		arc.putInt("bab", bab + 1);
		data.touch();
		ServerLevel level = server.overworld();
		KabarTempat.pemicu(level, site, "alur_bab" + bab + "_" + hasil);
		umumkan(server, site, bab, false, hasil);
		if (bab == BAB_TERAKHIR) {
			adeganDekat(server, site, "penobatan", 7);
		}
		fire(level, site, bab, false, hasil);
	}

	/**
	 * Effect {@code alur <bab> <hasil>}: the player's chapter quest ended with {@code hasil}. Closes the chapter when it is
	 * the open one of the player's arc; extra effects: {@code dou} (chapter 2 helped Dou), {@code bukti} (+1 proof).
	 */
	public static void lapor(ServerPlayer player, int bab, String hasil) {
		long site = TempatDialog.situsAlur(player);
		MinecraftServer server = player.level().getServer();
		if (site == 0L) {
			site = QuestTracker.situs(player, bab >= 1 && bab <= BAB_TERAKHIR ? QUEST[bab] : "");
		}
		if (site == 0L) {
			return;
		}
		CeritaData data = CeritaData.get(server);
		CompoundTag arc = data.arc(site);
		switch (hasil) {
			case "dou" -> {
				arc.putBoolean("dou", true);
				data.touch();
				return;
			}
			case "bukti" -> {
				CompoundTag proofs = arc.getCompoundOrEmpty("bukti");
				String id = player.getUUID().toString();
				proofs.putInt(id, Math.min(3, proofs.getIntOr(id, 0) + 1));
				arc.put("bukti", proofs);
				data.touch();
				return;
			}
			default -> {
			}
		}
		if (arc.getIntOr("bab", 0) == bab && arc.getBooleanOr("buka", false)) {
			tutupBab(server, site, bab, bab == BAB_TERAKHIR ? hitungPenguasa(arc) : hasil);
		}
	}

	/** Test: jumps the arc of {@code site} to chapter {@code bab} (opened now), closing the chapters before by default. */
	public static void lompat(MinecraftServer server, long site, int bab) {
		CeritaData data = CeritaData.get(server);
		CompoundTag arc = data.arc(site);
		int target = Math.max(1, Math.min(BAB_TERAKHIR, bab));
		long day = Kota.day(server);
		if (arc.getIntOr("bab", 0) <= 0) {
			data.setFirstCapital(site);
		}
		arc.putLong("mulai", day - HARI_BAB[target]);
		CompoundTag results = arc.getCompoundOrEmpty("hasil");
		for (int b = 1; b < target; b++) {
			if (!results.contains(Integer.toString(b))) {
				results.putString(Integer.toString(b), BAWAAN[b]);
			}
		}
		arc.put("hasil", results);
		if (!arc.contains("dukung") && target > 4) {
			arc.putString("dukung", BAWAAN[4]);
		}
		arc.putInt("bab", target);
		arc.putBoolean("buka", false);
		data.touch();
		bukaBab(server, site, target);
	}

	static String hitungPenguasa(CompoundTag arc) {
		String dukung = arc.getStringOr("dukung", "gu");
		String bab5 = arc.getStringOr("bab5", "tersembunyi");
		String bab3 = arc.getCompoundOrEmpty("hasil").getStringOr("3", "lemah");
		if ("c".equals(bab5)) {
			return "gu";
		}
		boolean unmasked = "a".equals(bab5) || "b".equals(bab5);
		if ("ruyi".equals(dukung) && (!"lemah".equals(bab3) || unmasked)) {
			return "ruyi";
		}
		if ("netral".equals(dukung) && unmasked) {
			return "ruyi";
		}
		return "gu";
	}

	// ================================================================== chapter 6: the eclipse night

	/**
	 * Chapter 6 opens: Mo Ying's demonic band attacks (the bodies of {@code TETUA_IBLIS} hostile to the participants in
	 * the capital; one more when chapter 5 ended in silence) and, when Dou was helped and nobody took the bribe, Kakek
	 * Lumpur arrives and shields the defenders. 38KS adds its special beast tide through {@link #addListener}.
	 */
	private static void malamGerhana(MinecraftServer server, long site) {
		CompoundTag arc = CeritaData.get(server).arc(site);
		boolean bribe = "c".equals(arc.getStringOr("bab5", ""));
		boolean master = arc.getBooleanOr("dou", false) && !bribe;
		for (ServerPlayer player : peserta(server, site)) {
			if (!diIbuKota(player, site) || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			int band = bribe ? 3 : 2;
			for (int i = 0; i < band; i++) {
				double angle = (Math.PI * 2.0D * i) / band + player.getRandom().nextDouble();
				int x = (int) Math.floor(player.getX() + Math.cos(angle) * 10.0D);
				int z = (int) Math.floor(player.getZ() + Math.sin(angle) * 10.0D);
				BlockPos ground = TempatApi.groundSpot(level, x, z, player.blockPosition().getY());
				if (ground == null) {
					continue;
				}
				KultivatorNpc demon = HidupApi.tubuh(level, site, NpcRole.TETUA_IBLIS, ground, player.getYRot() + 180.0F);
				if (demon != null) {
					demon.memory().putString("alur", "gerhana");
					demon.markHostile(player);
					demon.setTarget(player);
				}
			}
			Msg.chat(player, Component.translatable("kultivator.cerita.takhta.gerhana.serang").withStyle(ChatFormatting.DARK_RED));
			if (master) {
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1200, 1, false, true, true));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0, false, true, true));
				Msg.chat(player, Component.translatable("kultivator.cerita.takhta.gerhana.kakek").withStyle(ChatFormatting.GOLD));
				AdeganPayload.kirim(player, "kakek_lumpur", null, player.position(), 6);
			}
		}
	}

	// ================================================================== helpers

	/** Online participants of the arc of {@code site}. */
	static List<ServerPlayer> peserta(MinecraftServer server, long site) {
		CompoundTag ids = CeritaData.get(server).arc(site).getCompoundOrEmpty("peserta");
		List<ServerPlayer> out = new java.util.ArrayList<>();
		for (String key : ids.keySet()) {
			try {
				ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(key));
				if (p != null) {
					out.add(p);
				}
			} catch (IllegalArgumentException ignored) {
				// not a uuid
			}
		}
		return out;
	}

	private static void umumkan(MinecraftServer server, long site, int bab, boolean mulai, String hasil) {
		Component title = Component.translatable("kultivator.cerita.takhta.bab." + bab);
		for (ServerPlayer player : peserta(server, site)) {
			if (mulai) {
				Msg.chat(player, Component.translatable("kultivator.cerita.takhta.bab_mulai", bab, title).withStyle(ChatFormatting.GOLD));
				Msg.chat(player, Component.translatable("kultivator.cerita.takhta.bab." + bab + ".pembuka").withStyle(ChatFormatting.GRAY,
						ChatFormatting.ITALIC));
				if (diIbuKota(player, site)) {
					AdeganPayload.kirim(player, "bab", null, player.position(), bab);
				}
			} else {
				Msg.chat(player, Component.translatable("kultivator.cerita.takhta.bab_tutup", bab, title,
						Component.translatable("kultivator.cerita.takhta.hasil." + bab + "." + hasil)).withStyle(ChatFormatting.YELLOW));
			}
			// the arc's Sumber line (DESIGN 38.S.3) when the story opens and when it ends
			if ((mulai && bab == 1) || (!mulai && bab == BAB_TERAKHIR)) {
				player.sendSystemMessage(Component.translatable("kultivator.cerita.takhta.inspired").withStyle(ChatFormatting.DARK_GRAY,
						ChatFormatting.ITALIC));
			}
		}
	}

	private static void adeganDekat(MinecraftServer server, long site, String adegan, int a) {
		for (ServerPlayer player : peserta(server, site)) {
			if (diIbuKota(player, site)) {
				AdeganPayload.kirim(player, adegan, null, player.position().add(0.0D, 0.0D, 0.0D), a);
			}
		}
	}

	private static void fire(ServerLevel level, long site, int bab, boolean mulai, String hasil) {
		for (Pendengar listener : PENDENGAR) {
			try {
				listener.onBab(level, site, bab, mulai, hasil);
			} catch (RuntimeException e) {
				Kultivator.LOGGER.warn("A capital arc listener failed", e);
			}
		}
	}

	/** The quest id of chapter {@code bab} ("" outside 1-7). */
	public static String questBab(int bab) {
		return bab >= 1 && bab <= BAB_TERAKHIR ? QUEST[bab] : "";
	}

	/** Scene at a position for a single player (effect {@code adegan}). */
	static void adegan(ServerPlayer player, String id, @Nullable KultivatorNpc subject) {
		AdeganPayload.kirim(player, id, subject, subject == null ? player.position() : Vec3.ZERO, 0);
	}

	private AlurTakhta() {
	}
}
