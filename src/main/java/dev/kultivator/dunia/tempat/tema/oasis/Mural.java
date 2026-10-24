package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaData;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tebing Seribu Lukisan (DESIGN 38.T.4.5, 38.T.4.7): the twelve cave murals of the painters' cliff, the Kisah Mural
 * screen {@code tema_mural} and the mural commissions of Mo Hua.
 * <ul>
 * <li>Each of the 12 niches of district {@code kuil_air} holds a titik {@code pandang} with data {@code mural=<1..12>}.
 * Right-clicking any block within 4 blocks of it (the painted wall) reads that mural: the screen opens at the cliff and,
 * the first time per mural, the reader gains +0.5 comprehension (the chosen Dao, else the mural's own Dao) and the quest
 * event {@code oasis_mural <n>}.</li>
 * <li>Mural 7 "Pendeta yang Diusir" moves for a player in stage {@code mural} of {@code tema_oasis_mata_air}
 * (FESTIVAL {@code acara=mural_bergerak} to that player: the billboard swaps two frames every 20 ticks).</li>
 * <li>Commission: 50 NB, one per player per town; the player picks one of his feats ({@link #prestasi}); three world days
 * later his mural appears in niche 12 for every player of the server (FESTIVAL {@code acara=mural}, list
 * {@code pemain [{nama, ikon}]}, sent on entering the cliff district).</li>
 * </ul>
 * Feat icons of the billboard ({@code ikon}): 0 realm, 1 Dao, 2 Raja Kalajengking, 3 Pembawa Air, 4 caravan guard,
 * 5 whirling dancer.
 */
public final class Mural {

	public static final int JUMLAH = 12;
	public static final long HARGA_PESAN = 50L;
	public static final int HARI_PESAN = 3;
	public static final double PEMAHAMAN = 0.5D;
	/** The quest of the moving mural. */
	public static final String QUEST = "tema_oasis_mata_air";
	public static final int MURAL_BERGERAK = 7;
	private static final double JANGKAU_DINDING = 4.0D;
	private static final long CACHE_TICK = 1200L;

	/** The Dao each mural teaches a reader without a Dao of his own. */
	private static final Dao[] DAO = {Dao.LIFE, Dao.BODY, Dao.SHADOW, Dao.SWORD, Dao.SPACE, Dao.SPACE, Dao.ICE, Dao.FIRE, Dao.BODY,
			Dao.SWORD, Dao.LIFE, Dao.SPACE};

	private record Cache(long tick, List<Titik> titik) {
	}

	private static final Map<Long, Cache> TITIK = new HashMap<>();
	/** Player -> mural number of the cliff screen opened at a wall (0: opened from the painter). */
	private static final Map<UUID, Integer> KONTEKS = new HashMap<>();
	private static final Map<UUID, Long> DIKIRIM = new HashMap<>();

	static void init() {
		TemaLayar.daftar(TemaLayar.Mural.LAYAR, Mural::bangun, Mural::aksi);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)
					|| !OasisKota.aktif() || sp.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			int no = muralDi(sl, hit.getBlockPos());
			if (no <= 0) {
				return InteractionResult.PASS;
			}
			bukaDinding(sp, no, hit.getBlockPos());
			return InteractionResult.SUCCESS;
		});
	}

	static void reset() {
		TITIK.clear();
		KONTEKS.clear();
		DIKIRIM.clear();
	}

	// ================================================================== the wall

	/** The mural number whose wall {@code pos} belongs to, or 0. */
	static int muralDi(ServerLevel level, BlockPos pos) {
		int ruin = Reruntuhan.muralDi(level, pos);
		if (ruin > 0) {
			return ruin;
		}
		TemaKota.Kota kota = TemaKota.di(level, pos);
		if (kota == null || kota.tema() != Tema.OASIS || !"kuil_air".equals(TemaKota.distrikDi(kota, pos))) {
			return 0;
		}
		Cache cache = TITIK.get(kota.situsId());
		long now = level.getGameTime();
		if (cache == null || now - cache.tick > CACHE_TICK || cache.titik.isEmpty()) {
			List<Titik> found = new ArrayList<>();
			for (Titik t : TemaKota.titik(level, kota, "pandang")) {
				if (t.angka("mural", 0) > 0) {
					found.add(t);
				}
			}
			cache = new Cache(now, found);
			TITIK.put(kota.situsId(), cache);
		}
		int best = 0;
		double bestD = JANGKAU_DINDING * JANGKAU_DINDING;
		for (Titik t : cache.titik) {
			double d = t.pos().distSqr(pos);
			if (d <= bestD) {
				bestD = d;
				best = t.angka("mural", 0);
			}
		}
		return Math.max(0, Math.min(JUMLAH, best));
	}

	private static void bukaDinding(ServerPlayer player, int no, BlockPos wall) {
		KONTEKS.put(player.getUUID(), no);
		baca(player, no);
		CompoundTag sheet = bangun(player, -1);
		if (sheet != null) {
			TempatLayar.bukaDi(player, TemaLayar.Mural.LAYAR, wall, sheet);
		}
	}

	/**
	 * Marks mural {@code no} read. Every reading fires the quest event {@code oasis_mural <n>} (a player who read the
	 * murals before the chain reached stage {@code mural} must still be able to finish it); the comprehension is given
	 * only the first time.
	 */
	static void baca(ServerPlayer player, int no) {
		if (no < 1 || no > JUMLAH) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag me = OasisKota.pemain(server, player.getUUID());
		int mask = me.getIntOr("mural", 0);
		int bit = 1 << (no - 1);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 0.8F);
		if ((mask & bit) != 0) {
			QuestTracker.peristiwa(player, "oasis_mural", Integer.toString(no));
			return;
		}
		me.putInt("mural", mask | bit);
		OasisKota.ubah(server);
		CultivationData data = Cultivators.get(player);
		Dao dao = data.dao() != null ? data.dao() : DAO[no - 1];
		double got = Comprehension.grant(player, dao, PEMAHAMAN);
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.mural.paham",
				Component.translatable("kultivator.kota_tema.oasis.mural." + no + ".nama"), Msg.number(got)).withStyle(ChatFormatting.AQUA));
		QuestTracker.peristiwa(player, "oasis_mural", Integer.toString(no));
	}

	/** Murals read so far (0..12). */
	public static int dibaca(ServerPlayer player) {
		return Integer.bitCount(OasisKota.pemain(player.level().getServer(), player.getUUID()).getIntOr("mural", 0));
	}

	// ================================================================== the screen

	private static boolean pelukis(@Nullable KultivatorNpc npc) {
		return npc != null && (npc.role() == NpcRole.PELUKIS_GUA || OasisTokoh.adalah(npc, OasisTokoh.MO_HUA));
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = null;
		if (npcId >= 0) {
			Entity e = player.level().getEntity(npcId);
			npc = e instanceof KultivatorNpc k ? k : null;
			if (!pelukis(npc)) {
				return null;
			}
			KONTEKS.put(player.getUUID(), 0);
		} else if (!KONTEKS.containsKey(player.getUUID())) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = OasisLayar.kepala(player, "kultivator.kota_tema.oasis.mural.judul", LembarKunci.GAYA_BATU, npc);
		int mask = OasisKota.pemain(server, player.getUUID()).getIntOr("mural", 0);
		boolean bergerak = QuestTracker.aktif(player, QUEST) && "mural".equals(QuestTracker.simpul(player, QUEST));
		ListTag kisah = new ListTag();
		for (int no = 1; no <= JUMLAH; no++) {
			CompoundTag k = new CompoundTag();
			k.putInt(LembarKunci.NO, no);
			k.putString(LembarKunci.ID, Integer.toString(no));
			LembarKunci.teks(k, LembarKunci.NAMA, Component.translatable("kultivator.kota_tema.oasis.mural." + no + ".nama"));
			LembarKunci.teks(k, LembarKunci.TEKS, Component.translatable("kultivator.kota_tema.oasis.mural." + no + ".teks"));
			k.putInt(TemaLayar.Mural.GAMBAR, no - 1);
			k.putBoolean(TemaLayar.Mural.DIBACA, (mask & (1 << (no - 1))) != 0);
			k.putBoolean(TemaLayar.Mural.BERGERAK, bergerak && no == MURAL_BERGERAK);
			kisah.add(k);
		}
		tag.put(TemaLayar.Mural.KISAH, kisah);
		int buka = KONTEKS.getOrDefault(player.getUUID(), 0);
		if (buka > 0) {
			tag.putString(LembarKunci.TAB, Integer.toString(buka));
		}
		if (npc != null) {
			tag.put(TemaLayar.Mural.PESAN_MURAL, pesanan(player, npc));
		}
		return tag;
	}

	private static CompoundTag pesanan(ServerPlayer player, KultivatorNpc npc) {
		MinecraftServer server = player.level().getServer();
		CompoundTag pesan = new CompoundTag();
		pesan.putLong(LembarKunci.HARGA, HARGA_PESAN);
		CompoundTag milik = milik(server, situsDari(npc), player.getUUID());
		ListTag pilihan = new ListTag();
		for (Prestasi f : prestasi(player)) {
			CompoundTag p = new CompoundTag();
			p.putString(LembarKunci.ID, f.id());
			LembarKunci.teks(p, LembarKunci.NAMA, f.nama());
			p.putInt(LembarKunci.IKON, f.ikon());
			pilihan.add(p);
		}
		pesan.put(TemaLayar.Mural.PILIHAN, pilihan);
		if (milik != null) {
			CompoundTag m = new CompoundTag();
			LembarKunci.teks(m, LembarKunci.NAMA, Component.literal(milik.getStringOr("nama", player.getScoreboardName())));
			m.putInt(LembarKunci.IKON, milik.getIntOr("ikon", 0));
			long sisa = Math.max(0L, milik.getLongOr("siap", 0L) - OasisKota.hari((ServerLevel) player.level()));
			m.putInt(LembarKunci.SISA_HARI, (int) sisa);
			pesan.put(TemaLayar.Mural.MILIK, m);
			pesan.putBoolean(LembarKunci.BISA, false);
			LembarKunci.teks(pesan, LembarKunci.ALASAN, Component.translatable("kultivator.kota_tema.oasis.mural.sudah_pesan"));
		} else if (NilaiBatu.total(player) < HARGA_PESAN) {
			pesan.putBoolean(LembarKunci.BISA, false);
			LembarKunci.teks(pesan, LembarKunci.ALASAN, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(HARGA_PESAN)));
		} else {
			pesan.putBoolean(LembarKunci.BISA, true);
		}
		return pesan;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		String verb = parts[0];
		if (TemaLayar.Mural.OP_BACA.equals(verb)) {
			baca(player, LembarKunci.argInt(parts, 1, 0));
			return;
		}
		if (!TemaLayar.Mural.OP_PESAN.equals(verb) || npcId < 0) {
			return;
		}
		Entity e = player.level().getEntity(npcId);
		if (!(e instanceof KultivatorNpc npc) || !pelukis(npc)) {
			return;
		}
		pesan(player, npc, LembarKunci.arg(parts, 1));
	}

	// ================================================================== commissions

	/** One feat a player may have painted (id, shown name, billboard icon). */
	record Prestasi(String id, Component nama, int ikon) {
	}

	/**
	 * The feats a player may have painted: realm always; Dao when chosen; the scorpion king, the Pembawa Air title, a
	 * guarded caravan and a passed whirling dance when earned.
	 */
	static List<Prestasi> prestasi(ServerPlayer player) {
		List<Prestasi> out = new ArrayList<>();
		CultivationData data = Cultivators.get(player);
		out.add(new Prestasi("ranah", Component.translatable("kultivator.kota_tema.oasis.mural.prestasi.ranah", data.realm().displayName()), 0));
		if (data.dao() != null) {
			out.add(new Prestasi("dao", Component.translatable("kultivator.kota_tema.oasis.mural.prestasi.dao", data.dao().displayName()), 1));
		}
		CompoundTag me = OasisKota.pemain(player.level().getServer(), player.getUUID());
		if (me.getBooleanOr("bos_kalajengking", false)) {
			out.add(new Prestasi("kalajengking", Component.translatable("kultivator.kota_tema.oasis.mural.prestasi.kalajengking"), 2));
		}
		if (CeritaData.get(player.level().getServer()).flag(player.getUUID(), "gelar.pembawa_air")) {
			out.add(new Prestasi("pembawa_air", Component.translatable("kultivator.kota_tema.oasis.mural.prestasi.pembawa_air"), 3));
		}
		if (me.getIntOr("kafilah_dikawal", 0) > 0) {
			out.add(new Prestasi("kafilah", Component.translatable("kultivator.kota_tema.oasis.mural.prestasi.kafilah"), 4));
		}
		if (me.getBooleanOr("penari", false)) {
			out.add(new Prestasi("penari", Component.translatable("kultivator.kota_tema.oasis.mural.prestasi.penari"), 5));
		}
		return out;
	}

	/** The town id of the painter's town (the site id the billboard packet reads). */
	private static long situsDari(KultivatorNpc npc) {
		if (npc.level() instanceof ServerLevel level) {
			OasisKota.Hidup kota = OasisKota.terdekat(level, npc.position(), OasisKota.JANGKAU_KOTA);
			if (kota != null) {
				return kota.situs;
			}
		}
		return npc.town();
	}

	private static @Nullable CompoundTag milik(MinecraftServer server, long situs, UUID player) {
		ListTag list = OasisKota.kota(server, situs).getListOrEmpty("mural_pemain");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag m = list.getCompoundOrEmpty(i);
			if (player.toString().equals(m.getStringOr("uuid", ""))) {
				return m;
			}
		}
		return null;
	}

	/** True when the player has commissioned his mural in the oasis he stands in (condition {@code oasis_mural_dipesan}). */
	public static boolean sudahPesan(ServerPlayer player) {
		OasisKota.Hidup kota = OasisKota.di(player);
		return kota != null && milik(player.level().getServer(), kota.situs, player.getUUID()) != null;
	}

	private static void pesan(ServerPlayer player, KultivatorNpc npc, String featId) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		long situs = situsDari(npc);
		if (milik(server, situs, player.getUUID()) != null) {
			OasisLayar.pesan(player, Component.translatable("kultivator.kota_tema.oasis.mural.sudah_pesan"), false);
			return;
		}
		Prestasi feat = null;
		for (Prestasi f : prestasi(player)) {
			if (f.id().equals(featId)) {
				feat = f;
			}
		}
		if (feat == null) {
			return;
		}
		if (!EkonomiApi.bayar(player, HARGA_PESAN, "tema.oasis.mural")) {
			OasisLayar.pesan(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(HARGA_PESAN)), false);
			return;
		}
		CompoundTag data = OasisKota.kota(server, situs);
		ListTag list = data.getListOrEmpty("mural_pemain");
		CompoundTag m = new CompoundTag();
		m.putString("uuid", player.getUUID().toString());
		m.putString("nama", player.getScoreboardName());
		m.putString("prestasi", feat.id());
		m.putInt("ikon", feat.ikon());
		m.putLong("siap", OasisKota.hari(level) + HARI_PESAN);
		list.add(m);
		data.put("mural_pemain", list);
		OasisKota.ubah(server);
		SosokApi.gerak(npc, GerakId.LUKIS_DINDING, 0, 120, PropId.KUAS_BESAR, PropId.PALET, 0);
		npc.say(Component.translatable("kultivator.kota_tema.oasis.mural.pesan_terima"), 80);
		OasisLayar.pesan(player, Component.translatable("kultivator.kota_tema.oasis.mural.dipesan", HARI_PESAN), true);
		QuestTracker.peristiwa(player, "oasis_mural_pesan", feat.id());
	}

	// ================================================================== billboard data (every 20 ticks)

	static void tick(MinecraftServer server) {
		for (OasisKota.Hidup kota : OasisKota.semua()) {
			ServerLevel level = server.getLevel(kota.dimensi);
			if (level == null || kota.satu(OasisAnchors.MURAL) == null) {
				continue;
			}
			Vec3 cliff = kota.satu(OasisAnchors.MURAL).position();
			long now = level.getGameTime();
			for (ServerPlayer p : BadaiPasir.pemainDekat(level, BlockPos.containing(cliff), 96.0D)) {
				long last = DIKIRIM.getOrDefault(p.getUUID(), Long.MIN_VALUE);
				if (now - last < 1200L) {
					continue;
				}
				DIKIRIM.put(p.getUUID(), now);
				TemaNet.kirim(p, TemaNet.FESTIVAL, kota.situs, paket(server, level, kota.situs));
				if (QuestTracker.aktif(p, QUEST) && "mural".equals(QuestTracker.simpul(p, QUEST))) {
					CompoundTag bergerak = new CompoundTag();
					bergerak.putString("acara", "mural_bergerak");
					bergerak.putBoolean("mulai", true);
					bergerak.putInt("gua", MURAL_BERGERAK);
					bergerak.putLong("tick", now);
					TemaNet.kirim(p, TemaNet.FESTIVAL, kota.situs, bergerak);
				}
			}
		}
	}

	private static CompoundTag paket(MinecraftServer server, ServerLevel level, long situs) {
		CompoundTag tag = new CompoundTag();
		tag.putString("acara", "mural");
		tag.putBoolean("mulai", true);
		tag.putInt("gua", JUMLAH);
		ListTag pemain = new ListTag();
		long hari = OasisKota.hari(level);
		ListTag list = OasisKota.kota(server, situs).getListOrEmpty("mural_pemain");
		for (int i = 0; i < list.size() && pemain.size() < 24; i++) {
			CompoundTag m = list.getCompoundOrEmpty(i);
			if (m.getLongOr("siap", Long.MAX_VALUE) > hari) {
				continue;
			}
			CompoundTag e = new CompoundTag();
			e.putString("nama", m.getStringOr("nama", "?"));
			e.putInt("ikon", m.getIntOr("ikon", 0));
			pemain.add(e);
		}
		tag.put("pemain", pemain);
		return tag;
	}

	/** Logout. */
	static void lupakan(UUID id) {
		KONTEKS.remove(id);
		DIKIRIM.remove(id);
	}

	private Mural() {
	}
}
