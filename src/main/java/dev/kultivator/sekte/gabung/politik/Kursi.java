package dev.kultivator.sekte.gabung.politik;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import dev.kultivator.sekte.gabung.dunia.TokohAnchors;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Limited seats of DESIGN 41.6.1 and 41.9 (contract C41.D1, owner 41D). Ranks with a seat count
 * ({@code Pangkat.kursi(KelasSekte)}: Inti by class, Pewaris 1, Tetua the class's elder seats, Wakil 1, Ketua 1, Tamu 2) are
 * shared by players and named characters; a rank earned while every seat is taken waits. A seat holder may be challenged
 * to a friendly Duel Pedang under sect rules once per {@link #JEDA_TANTANG_HARI} days on the lei tai: the winner takes the
 * seat, a beaten character drops one rank (attitude -10, may become a rival). Two players: the challenger picks a day within
 * 3 days; a holder offline longer has the seat frozen until back (at most {@link #BEKU_MAKS_HARI} days), never lost
 * unfought.
 * <p>
 * Who sits where: players are stored in {@code DataSekteGabung.kursi} (Inti, Tetua, Tamu), {@code pewaris},
 * {@code wakil} and {@code ketuaPemain} (kept in step with every rank change by the {@code PANGKAT_NAIK}/{@code KELUAR}
 * listeners); the remaining seats are filled by the sect's characters in a fixed order (Inti: the 39F roster's Eldest
 * Senior, then its Core disciples; Pewaris: the Eldest Senior; Tetua: 41W's elders; Wakil: the Grand Elder; Ketua: the
 * NPC master). A character beaten for its seat is "tergeser" (listed in the sect's {@code politik} compound) and no longer
 * fills seats.
 * <p>
 * Also the duel door of 41D ({@link #tandingTokoh}, {@link #tandingPemain}): the heir election, the succession duel and the
 * guest test fight through it, so one duel-end listener settles every 41D duel on the next tick.
 */
public final class Kursi {

	/** Days between two challenges of the same seat. */
	public static final int JEDA_TANTANG_HARI = 7;
	/** Days a player may schedule a seat duel ahead. */
	public static final int JADWAL_MAKS_HARI = 3;
	/** A seat of an offline player is frozen at most this long. */
	public static final int BEKU_MAKS_HARI = 10;
	/** Ticks of a seat duel (three minutes) and the distance the sect body appears at. */
	public static final int TANDING_TICKS = 20 * 180;
	private static final double JARAK_ARENA = 64.0D;

	private static final String KEY = "kultivator.gabung.politik.kursi.";

	/** How a 41D duel ended for the player: won, the duel's result code, the opponent (may be removed). */
	@FunctionalInterface
	public interface Hasil {
		void selesai(ServerPlayer pemain, boolean menang, int alasan, @Nullable LivingEntity lawan);
	}

	/** {@code tubuh}: a temporary sect body to remove afterwards (null for a live character body or a player). */
	private record Tanding(UUID pemain, @Nullable UUID lawanPemain, @Nullable UUID lawan, @Nullable DiscipleEntity tubuh, Hasil hasil, long sampai) {
	}

	/** Open 41D duels by the player's UUID. */
	private static final Map<UUID, Tanding> TANDING = new HashMap<>();
	private static boolean initialised;

	// ================================================================== who holds the seats

	/**
	 * Who holds the seats of {@code pangkat} in {@code sekte}: player UUID strings and tokoh ids ({@code s3#t2}, roster
	 * {@code s3#4}), in seat order (players first). Empty for ranks without seats or an unknown sect.
	 */
	public static List<String> pemegang(MinecraftServer server, String sekte, Pangkat pangkat) {
		List<String> out = new ArrayList<>();
		if (server == null || sekte == null || pangkat == null || GabungApi.tier(sekte) <= 0) {
			return out;
		}
		int n = pangkat.kursi(GabungApi.kelasSekte(sekte));
		if (n <= 0) {
			return out;
		}
		for (String p : pemain(server, sekte, pangkat)) {
			if (out.size() < n && !out.contains(p)) {
				out.add(p);
			}
		}
		String tergeser = "," + DiplomasiGabung.bagian(server, sekte).getStringOr("tergeser", "") + ",";
		for (String t : tokohIsi(server, sekte, pangkat)) {
			if (out.size() >= n) {
				break;
			}
			if (!out.contains(t) && !tergeser.contains("," + t + ",")) {
				out.add(t);
			}
		}
		return out;
	}

	/** True when a seat of {@code pangkat} is free now (ranks without a seat limit: always). */
	public static boolean bebas(MinecraftServer server, String sekte, Pangkat pangkat) {
		int n = pangkat == null ? -1 : pangkat.kursi(GabungApi.kelasSekte(sekte));
		return n < 0 || pemegang(server, sekte, pangkat).size() < n;
	}

	/** The rank whose seat {@code pemegang} holds in {@code sekte}, or null. */
	public static @Nullable Pangkat kursiDari(MinecraftServer server, String sekte, String pemegang) {
		for (Pangkat p : Pangkat.values()) {
			if (p.kursi(GabungApi.kelasSekte(sekte)) > 0 && pemegang(server, sekte, p).contains(pemegang)) {
				return p;
			}
		}
		return null;
	}

	/** The players sitting on {@code pangkat}'s seats (UUID strings) as stored. */
	private static List<String> pemain(MinecraftServer server, String sekte, Pangkat pangkat) {
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.peekSekte(sekte);
		List<String> out = new ArrayList<>();
		if (d == null) {
			return out;
		}
		switch (pangkat) {
			case PEWARIS -> tambahJikaPemain(data, sekte, d.pewaris, pangkat, out);
			case WAKIL -> tambahJikaPemain(data, sekte, d.wakil, pangkat, out);
			case KETUA -> {
				if (d.ketuaPemain != null) {
					out.add(d.ketuaPemain.toString());
				}
			}
			default -> {
				for (String id : d.kursi(pangkat.id())) {
					tambahJikaPemain(data, sekte, id, pangkat, out);
				}
			}
		}
		return out;
	}

	private static void tambahJikaPemain(GabungData data, String sekte, String id, Pangkat pangkat, List<String> out) {
		UUID uuid = uuid(id);
		Keanggotaan k = uuid == null ? null : data.peek(uuid);
		if (k != null && k.anggotaDi(sekte) && k.pangkat == pangkat) {
			out.add(id);
		}
	}

	/** The characters that fill vacant seats of {@code pangkat}, in order. */
	private static List<String> tokohIsi(MinecraftServer server, String sekte, Pangkat pangkat) {
		List<String> out = new ArrayList<>();
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(sekte);
		switch (pangkat) {
			case INTI -> {
				for (String peran : new String[]{"kakak", "inti"}) {
					for (Roster.Tokoh t : Roster.daftar(server, sekte)) {
						if (t.aktif() && peran.equals(t.peran())) {
							out.add(t.id());
						}
					}
				}
			}
			case PEWARIS -> {
				if (d != null && !d.pewaris.isEmpty() && uuid(d.pewaris) == null) {
					out.add(d.pewaris);
				}
				for (Roster.Tokoh t : Roster.daftar(server, sekte)) {
					if (t.aktif() && "kakak".equals(t.peran()) && !out.contains(t.id())) {
						out.add(t.id());
					}
				}
			}
			case TETUA -> {
				for (TokohSekte.Tokoh t : TokohSekte.tetua(server, sekte)) {
					out.add(t.id());
				}
			}
			case WAKIL -> {
				if (d != null && !d.wakil.isEmpty() && uuid(d.wakil) == null) {
					out.add(d.wakil);
				}
				TokohSekte.Tokoh agung = TokohSekte.jabatan(server, sekte, Jabatan.TETUA_AGUNG);
				if (agung != null && !out.contains(agung.id())) {
					out.add(agung.id());
				}
			}
			case KETUA -> {
				if (d == null || d.ketuaPemain == null) {
					out.add(TokohSekte.idKetua(sekte));
				}
			}
			default -> {
			}
		}
		return out;
	}

	/** The display name of a seat holder (player name, character name, or the id). */
	public static Component namaPemegang(MinecraftServer server, String pemegang) {
		UUID uuid = uuid(pemegang);
		if (uuid != null) {
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			if (p != null) {
				return p.getName();
			}
			var profile = server.services().nameToIdCache().get(uuid);
			return Component.literal(profile.map(net.minecraft.server.players.NameAndId::name).orElse("?"));
		}
		if (TokohSekte.idSah(pemegang)) {
			TokohSekte.Tokoh t = TokohSekte.tokoh(server, pemegang);
			if (t != null) {
				return Component.literal(t.nama());
			}
			if (pemegang.endsWith("#k")) {
				Component master = dev.kultivator.sekte.api.SekteApi.diplomasi().master(TokohSekte.sekteDari(pemegang));
				if (master != null) {
					return master;
				}
			}
			return Component.literal(pemegang);
		}
		Roster.Tokoh r = Roster.tokoh(server, pemegang);
		return r == null ? Component.literal(pemegang) : r.nama();
	}

	// ================================================================== challenges

	/**
	 * {@code penantang} challenges {@code pemegang} (a UUID string or tokoh id from {@link #pemegang}) for the seat. Returns
	 * null when the duel was set up, else the reason it was refused (rank too low, cooldown, holder away, busy).
	 */
	public static @Nullable Component tantang(ServerPlayer penantang, String pemegang) {
		MinecraftServer server = penantang.level().getServer();
		if (!Gabung.aktif()) {
			return Component.translatable("kultivator.gabung.cmd.mati");
		}
		Keanggotaan k = GabungApi.anggota(penantang);
		String sekte = k.sekteAktif();
		if (sekte == null) {
			return Component.translatable(KEY + "bukan_anggota");
		}
		Pangkat kursi = kursiDari(server, sekte, pemegang);
		if (kursi == null || kursi == Pangkat.TAMU || kursi == Pangkat.KETUA) {
			return Component.translatable(KEY + "bukan_kursi");
		}
		if (pemegang.equals(penantang.getUUID().toString())) {
			return Component.translatable(KEY + "diri_sendiri");
		}
		if (k.pangkat.turun() == k.pangkat || kursi.turun() != k.pangkat) {
			return Component.translatable(KEY + "pangkat", kursi.nama(), kursi.turun().nama());
		}
		int tier = GabungApi.tier(sekte);
		KelasSekte kelas = GabungApi.kelasSekte(sekte);
		Realm perlu = kursi.syaratRanah(kelas, tier);
		if (Cultivators.get(penantang).realm().ordinal() < perlu.ordinal()) {
			return Component.translatable(KEY + "ranah", perlu.displayName());
		}
		long hari = GabungApi.hari(server);
		CompoundTag b = DiplomasiGabung.bagian(server, sekte);
		long lalu = b.getCompoundOrEmpty("tantang").getLongOr(pemegang, -100L);
		if (hari < lalu + JEDA_TANTANG_HARI) {
			return Component.translatable(KEY + "jeda", lalu + JEDA_TANTANG_HARI - hari);
		}
		String busy = Sibuk.why(penantang);
		if (busy != null) {
			return Sibuk.text(busy);
		}
		Hasil hasil = (p, menang, alasan, lawan) -> hasilKursi(p, sekte, kursi, pemegang, menang);
		UUID holder = uuid(pemegang);
		Component why;
		if (holder != null) {
			ServerPlayer lawan = server.getPlayerList().getPlayer(holder);
			if (lawan == null) {
				CompoundTag janji = b.getCompoundOrEmpty("janji");
				CompoundTag j = new CompoundTag();
				j.putString("penantang", penantang.getUUID().toString());
				j.putLong("sampai", hari + JADWAL_MAKS_HARI);
				j.putString("pangkat", kursi.id());
				janji.put(pemegang, j);
				b.put("janji", janji);
				GabungData.get(server).setDirty();
				return Component.translatable(KEY + "beku", namaPemegang(server, pemegang), JADWAL_MAKS_HARI);
			}
			why = tandingPemain(penantang, lawan, hasil);
		} else {
			if (lukaTokoh(server, pemegang, hari)) {
				return Component.translatable(KEY + "luka", namaPemegang(server, pemegang));
			}
			why = tandingTokoh(penantang, sekte, pemegang, namaPemegang(server, pemegang), langkahTokoh(server, sekte, pemegang),
					TANDING_TICKS, hasil);
		}
		if (why == null) {
			CompoundTag t = b.getCompoundOrEmpty("tantang");
			t.putLong(pemegang, hari);
			b.put("tantang", t);
			GabungData.get(server).setDirty();
			penantang.sendSystemMessage(Component.translatable(KEY + "mulai", namaPemegang(server, pemegang), kursi.nama()).withStyle(ChatFormatting.GOLD));
		}
		return why;
	}

	/** The seat duel ended: the winner sits, the loser drops one rank (a character: attitude -10, a rival, 2 days hurt). */
	private static void hasilKursi(ServerPlayer p, String sekte, Pangkat kursi, String pemegang, boolean menang) {
		MinecraftServer server = p.level().getServer();
		if (!menang) {
			p.sendSystemMessage(Component.translatable(KEY + "kalah", namaPemegang(server, pemegang), JEDA_TANTANG_HARI).withStyle(ChatFormatting.RED));
			return;
		}
		GabungData data = GabungData.get(server);
		UUID holder = uuid(pemegang);
		if (holder != null) {
			ServerPlayer lawan = server.getPlayerList().getPlayer(holder);
			if (lawan != null) {
				GabungApi.setPangkat(lawan, kursi.turun(), "gabung.kursi_kalah");
				lawan.sendSystemMessage(Component.translatable(KEY + "digeser", p.getName(), kursi.nama()).withStyle(ChatFormatting.RED));
			} else {
				Keanggotaan k = data.peek(holder);
				if (k != null && k.anggotaDi(sekte)) {
					k.pangkat = kursi.turun();
					k.hariPangkat = GabungApi.hari(server);
				}
			}
		} else {
			CompoundTag b = DiplomasiGabung.bagian(server, sekte);
			String list = b.getStringOr("tergeser", "");
			b.putString("tergeser", list.isEmpty() ? pemegang : list + "," + pemegang);
			if (TokohSekte.idSah(pemegang)) {
				TokohSekte.ubahSikap(server, pemegang, p.getUUID(), -10);
			} else {
				Roster.ubahSikap(server, pemegang, p.getUUID(), -10);
				Roster.lukai(server, pemegang, 2);
				Keanggotaan k = GabungApi.anggota(p);
				if (k.saingan.isEmpty()) {
					k.saingan = pemegang;
				}
			}
		}
		data.setDirty();
		GabungApi.setPangkat(p, kursi, "gabung.kursi");
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "menang", p.getName(), namaPemegang(server, pemegang), kursi.nama()),
				ChatFormatting.GOLD);
	}

	private static boolean lukaTokoh(MinecraftServer server, String id, long hari) {
		if (TokohSekte.idSah(id)) {
			TokohSekte.Tokoh t = TokohSekte.tokoh(server, id);
			return t != null && t.terluka(hari);
		}
		Roster.Tokoh r = Roster.tokoh(server, id);
		return r != null && r.luka(hari);
	}

	/** The rank step of a character's duel body (roster: its rank; elder: its realm; master: the sect master's step). */
	public static int langkahTokoh(MinecraftServer server, String sekte, String id) {
		if (TokohSekte.idSah(id)) {
			if (id.endsWith("#k")) {
				return DiplomasiGabung.langkahKetua(server, sekte);
			}
			TokohSekte.Tokoh t = TokohSekte.tokoh(server, id);
			return t != null ? new Rank(t.ranah(), 0, 1).step() : Math.max(2, DiplomasiGabung.langkahKetua(server, sekte) - 2);
		}
		Roster.Tokoh r = Roster.tokoh(server, id);
		return r != null ? r.rank().step() : DiplomasiGabung.langkahAnggota(server, sekte, 0);
	}

	// ================================================================== the duel door of 41D

	/**
	 * A friendly Sword Duel of {@code pemain} against character {@code tokohId} of {@code sekte} (stop at 10 %, nobody
	 * dies): the character's live body when 41W gave it one near the player, else a temporary sect body that appears beside
	 * the player (on the arena when within 64 blocks) and leaves after the duel. {@code ticks} is the time limit.
	 * {@code hasil} runs on the next tick after the duel ends. Null when it started, else why not.
	 */
	public static @Nullable Component tandingTokoh(ServerPlayer pemain, String sekte, String tokohId, Component nama, int step, int ticks, Hasil hasil) {
		if (TANDING.containsKey(pemain.getUUID())) {
			return Component.translatable(KEY + "sedang");
		}
		Component self = DuelChallenges.selfProblem(pemain);
		if (self != null) {
			return self;
		}
		ServerLevel level = pemain.level();
		LivingEntity tubuh = null;
		DiscipleEntity sementara = null;
		LivingEntity hidup = TokohSekte.idSah(tokohId) ? TokohAnchors.tubuh(level, sekte, tokohId) : null;
		if (hidup != null && hidup.isAlive() && hidup.distanceToSqr(pemain) < 24.0D * 24.0D) {
			tubuh = hidup;
		}
		if (tubuh == null) {
			BlockPos arena = GabungApi.titik(sekte, "arena");
			BlockPos dekat = arena != null && arena.distSqr(pemain.blockPosition()) < JARAK_ARENA * JARAK_ARENA ? arena : pemain.blockPosition();
			Vec3 arah = pemain.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize().scale(4.0D);
			BlockPos spot = DiplomasiGabung.tanah(level, BlockPos.containing(dekat.getX() + arah.x, dekat.getY(), dekat.getZ() + arah.z));
			sementara = DiplomasiGabung.tubuh(level, Vec3.atBottomCenterOf(spot), sekte, step, 97, nama, false);
			tubuh = sementara;
		}
		if (tubuh == null) {
			return Component.translatable(KEY + "tak_hadir", nama);
		}
		tubuh.setHealth(tubuh.getMaxHealth());
		DuelRuleSet base = DuelRuleSet.defaults().withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN).withJurus(DuelRuleSet.JurusRule.JURUS_BEBAS)
				.withIntro(true).withoutStake();
		DuelRuleSet rules = new DuelRuleSet(base.realm(), base.jurus(), base.win(), base.radius(), ticks, base.introFilm(), base.stake(), 0, 0, null,
				true);
		Component why = DuelChallenges.startDirect(pemain, pemain, tubuh, DuelIds.PAIR_NPC, rules);
		if (why != null) {
			DiplomasiGabung.lenyap(sementara);
			return why;
		}
		TANDING.put(pemain.getUUID(), new Tanding(pemain.getUUID(), null, tubuh.getUUID(), sementara, hasil,
				level.getGameTime() + ticks + 20L * 60L));
		Voice.say(tubuh, "elder.welcome", null);
		return null;
	}

	/**
	 * A seat duel between two players: the challenge invite of the Sword Duel ({@code DuelChallenges.challenge}); when the
	 * other player accepts and the duel ends, {@code hasil} runs for the challenger. Null when the invite went out.
	 */
	public static @Nullable Component tandingPemain(ServerPlayer penantang, ServerPlayer lawan, Hasil hasil) {
		if (TANDING.containsKey(penantang.getUUID())) {
			return Component.translatable(KEY + "sedang");
		}
		DuelRuleSet base = DuelRuleSet.defaults().withWin(DuelRuleSet.WinRule.SEPULUH_PERSEN).withoutStake().withIntro(true);
		DuelRuleSet rules = new DuelRuleSet(base.realm(), base.jurus(), base.win(), base.radius(), TANDING_TICKS, base.introFilm(), base.stake(), 0, 0,
				null, true);
		Component why = DuelChallenges.challenge(penantang, lawan, null, rules);
		if (why != null) {
			return why;
		}
		TANDING.put(penantang.getUUID(), new Tanding(penantang.getUUID(), lawan.getUUID(), lawan.getUUID(), null, hasil,
				penantang.level().getGameTime() + 20L * 60L * 10L));
		lawan.sendSystemMessage(Component.translatable(KEY + "undangan", penantang.getName()).withStyle(ChatFormatting.GOLD));
		return null;
	}

	/** True while {@code player} has an open 41D duel (or invite). */
	public static boolean bertanding(ServerPlayer player) {
		return TANDING.containsKey(player.getUUID());
	}

	private static void onDuelEnd(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		LivingEntity a = session.a();
		LivingEntity b = session.b();
		for (LivingEntity side : new LivingEntity[]{a, b}) {
			if (!(side instanceof ServerPlayer player)) {
				continue;
			}
			Tanding t = TANDING.get(player.getUUID());
			if (t == null) {
				continue;
			}
			LivingEntity other = side == a ? b : a;
			if (t.lawan() != null && (other == null || !t.lawan().equals(other.getUUID()))) {
				continue;
			}
			TANDING.remove(player.getUUID());
			boolean menang = winner != null && winner.getUUID().equals(player.getUUID());
			int alasan = DuelEndListeners.reason();
			Voice.later(1, () -> {
				try {
					t.hasil().selesai(player, menang, alasan, other);
				} finally {
					if (t.tubuh() != null) {
						t.tubuh().setTarget(null);
						Voice.later(40, () -> DiplomasiGabung.lenyap(t.tubuh()));
					}
				}
			});
			return;
		}
	}

	/** Once a second: stale duels (an invite never answered, a body gone) are dropped. */
	static void detik(MinecraftServer server) {
		if (TANDING.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		TANDING.entrySet().removeIf(e -> {
			Tanding t = e.getValue();
			boolean basi = now > t.sampai() || t.tubuh() != null && t.tubuh().isRemoved();
			if (basi && t.tubuh() != null) {
				DiplomasiGabung.lenyap(t.tubuh());
			}
			return basi;
		});
	}

	// ================================================================== seat bookkeeping (listeners)

	/** Registers the listeners once (from {@code Politik.init}). */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		DuelEndListeners.add(Kursi::onDuelEnd);
		GabungBus.PANGKAT_NAIK.register((player, sekte, lama, baru) -> sinkron(player.level().getServer(), player.getUUID(), sekte, baru));
		GabungBus.DITERIMA.register((player, sekte, pangkat) -> sinkron(player.level().getServer(), player.getUUID(), sekte, pangkat));
		GabungBus.KELUAR.register((player, sekte, status) -> keluar(player.level().getServer(), player.getUUID(), sekte, status));
		ServerPlayerEvents.JOIN.register(Kursi::masuk);
	}

	/** Keeps the stored seats in step with the player's rank. */
	static void sinkron(MinecraftServer server, UUID uuid, String sekte, Pangkat baru) {
		if (server == null || sekte == null) {
			return;
		}
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.sekte(sekte);
		String id = uuid.toString();
		for (List<String> list : d.kursi.values()) {
			list.remove(id);
		}
		if (id.equals(d.pewaris) && baru != Pangkat.PEWARIS) {
			d.pewaris = "";
		}
		if (id.equals(d.wakil) && baru != Pangkat.WAKIL) {
			d.wakil = "";
		}
		switch (baru) {
			case INTI, TETUA, TAMU -> d.kursi(baru.id()).add(id);
			case PEWARIS -> d.pewaris = id;
			case WAKIL -> d.wakil = id;
			default -> {
			}
		}
		data.setDirty();
	}

	private static void keluar(MinecraftServer server, UUID uuid, String sekte, StatusKeluar status) {
		if (server == null) {
			return;
		}
		sinkron(server, uuid, sekte, Pangkat.PELAYAN);
		CompoundTag janji = DiplomasiGabung.bagian(server, sekte).getCompoundOrEmpty("janji");
		janji.remove(uuid.toString());
	}

	/** A holder back online: a scheduled seat duel still inside its window is offered again. */
	private static void masuk(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		String sekte = server == null || !Gabung.aktif() ? null : GabungApi.sekte(player.getUUID());
		if (sekte == null) {
			return;
		}
		CompoundTag b = DiplomasiGabung.bagian(server, sekte);
		CompoundTag janji = b.getCompoundOrEmpty("janji");
		CompoundTag j = janji.getCompoundOrEmpty(player.getUUID().toString());
		if (j.isEmpty()) {
			return;
		}
		janji.remove(player.getUUID().toString());
		GabungData.get(server).setDirty();
		long hari = GabungApi.hari(server);
		UUID dari = uuid(j.getStringOr("penantang", ""));
		ServerPlayer penantang = dari == null ? null : server.getPlayerList().getPlayer(dari);
		if (hari > j.getLongOr("sampai", 0L) || penantang == null) {
			player.sendSystemMessage(Component.translatable(KEY + "janji_lewat").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.sendSystemMessage(Component.translatable(KEY + "janji", penantang.getName()).withStyle(ChatFormatting.GOLD));
		Pangkat kursi = Pangkat.byOrdinal(0);
		for (Pangkat p : Pangkat.values()) {
			if (p.id().equals(j.getStringOr("pangkat", ""))) {
				kursi = p;
			}
		}
		Pangkat k = kursi;
		String holder = player.getUUID().toString();
		Component why = tandingPemain(penantang, player, (p, menang, alasan, lawan) -> hasilKursi(p, sekte, k, holder, menang));
		if (why != null) {
			penantang.sendSystemMessage(why.copy().withStyle(ChatFormatting.YELLOW));
		}
	}

	/** Restores every seat to the characters ({@code uji gabung kursi pulih}). */
	static void pulih(MinecraftServer server, String sekte) {
		DiplomasiGabung.bagian(server, sekte).putString("tergeser", "");
		GabungData.get(server).setDirty();
	}

	static @Nullable UUID uuid(@Nullable String s) {
		if (s == null || s.length() != 36) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static void clear() {
		for (Tanding t : TANDING.values()) {
			if (t.tubuh() != null) {
				t.tubuh().discard();
			}
		}
		TANDING.clear();
	}

	private Kursi() {
	}
}
