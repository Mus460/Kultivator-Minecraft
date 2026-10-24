package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Grand Inter-Sect Tournament on the lei tai of the Grand Arena (DESIGN 38.B.6.4): {@code d % 14 == 9} (or the next free
 * day), 6000-11000.
 * <ul>
 * <li><b>Seats</b>: eight. Players who entered before the first round (fee {@code 50 x (band + 1)} NB, op {@code daftar})
 * take seats first as their sect's representative, then the strongest NPC sects of the single sect list that are not at
 * war with them. The bracket is drawn at the first round (6600) and shown live on the {@code acara} screen.</li>
 * <li><b>Rounds</b> at 6600, 8100 and 9600. A player's bout is a formal Sword Duel on the lei tai against the sect's
 * champion ({@link NpcRole#JUARA_SEKTE} body named for the sect, realm by the sect's tier): 90 s, stopped at 10 %, no
 * stake, Realm Suppression when two realms apart ({@code DuelChallenges.startDirect}). Two players meet through a
 * normal challenge the other must accept. Absent or busy at the bell: a walkover. NPC against NPC is decided by
 * {@code 0.5 + 0.08 x (tier A - tier B)}; with a player watching, the first such bout of a round is shown on the stage
 * (two champions sparring, the loser kneeling).</li>
 * <li><b>Prizes</b>: the champion 4 x fee, the trophy Piala Langit Agung, standing +10 with every sect beaten fairly
 * (at most +20 per tournament), Nama Baik +5 and the title "Juara Turnamen Agung"; the finalist the fee back.</li>
 * </ul>
 */
final class TurnamenAgung implements KotaBesarKalender.Acara {

	static final String ID = LembarKunci.Acara.ACARA_TURNAMEN;
	static final int KURSI = 8;
	static final long[] BABAK = {6600L, 8100L, 9600L};
	static final long BATAS_DUEL = 2400L;
	static final int DIPLOMASI_MAKS = 20;

	private record Tunggu(long town, UUID pemain, @Nullable UUID lawan, int babak, int laga, long sampai) {
	}

	private static final Map<UUID, Tunggu> TUNGGU = new HashMap<>();
	private final Map<Long, List<UUID>> pameran = new HashMap<>();

	TurnamenAgung() {
		DuelEndListeners.add(TurnamenAgung::duelSelesai);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int prioritas() {
		return 5;
	}

	@Override
	public boolean hariAsli(MinecraftServer server, long town, long day) {
		return Math.floorMod(day, 14L) == 9L;
	}

	@Override
	public long mulaiJam() {
		return 6000L;
	}

	@Override
	public long akhirJam() {
		return 11000L;
	}

	private static long biaya(ServerPlayer p) {
		return 50L * (Barang.pita(p) + 1);
	}

	private static CompoundTag data(MinecraftServer server, long town) {
		return KotaBesarData.get(server).bagian(town, "turnamen");
	}

	private static void simpan(MinecraftServer server, long town, CompoundTag tag) {
		KotaBesarData.get(server).putBagian(town, "turnamen", tag);
	}

	/** True when {@code player} stands in the Grand Arena district or at the guild's duel test floor (legal duels). */
	static boolean diLapangan(ServerLevel level, ServerPlayer player) {
		Kandidat k = KotaBesarKota.di(level, player.blockPosition());
		if (k == null) {
			return false;
		}
		if ("arena".equals(KotaBesarKota.distrik(k, player.blockPosition()))) {
			return true;
		}
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(KotaBesarKota.situs(k));
		DuniaAnchorEntity uji = kota == null ? null : kota.layanan("uji_duel");
		return uji != null && uji.blockPosition().closerThan(player.blockPosition(), 12.0D);
	}

	// ================================================================== start

	@Override
	public void mulai(ServerLevel level, Kandidat k, long town, boolean hidup) {
		MinecraftServer server = level.getServer();
		CompoundTag tag = new CompoundTag();
		tag.putLong("hari", Kota.day(server));
		tag.putInt("babak", -1);
		tag.put("daftar", new ListTag());
		simpan(server, town, tag);
		if (!hidup) {
			return;
		}
		KotaBesarPayload.sekitar(level, new Vec3(k.pusatX(), 64.0D, k.pusatZ()), "panji", panji(level, k, town, true));
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		DuniaAnchorEntity juri = kota == null ? null : kota.layanan("turnamen");
		KultivatorNpc caller = juri == null ? null : KotaBesarAnchors.tubuhDari(juri);
		if (caller != null) {
			Voice.say(caller, "kotabesar.tournament_open", null);
			caller.say(Component.translatable("kultivator.kota_besar.turnamen.buka"), 120);
		}
		for (ServerPlayer p : level.players()) {
			if (KotaBesarKota.jarak(k, p.blockPosition()) <= KotaBesarKota.HIDUP) {
				Msg.title(p, Component.translatable("kultivator.kota_besar.turnamen.judul").withStyle(ChatFormatting.GOLD),
						Component.translatable("kultivator.kota_besar.turnamen.sub", KotaBesarKalender.jam(BABAK[0])), 10, 70, 20);
			}
		}
	}

	/** The banner picture: the arena stage and the ink colours of the seated sects. */
	static CompoundTag panji(ServerLevel level, Kandidat k, long town, boolean aktif) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("aktif", aktif);
		tag.putLong("town", town);
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		DuniaAnchorEntity stage = kota == null ? null : kota.panggung("turnamen");
		BlockPos at = stage != null ? stage.blockPosition() : KotaBesarKota.pusatDistrik(level, k, "arena");
		tag.putDouble("x", at.getX() + 0.5D);
		tag.putDouble("z", at.getZ() + 0.5D);
		tag.putInt("y", at.getY());
		DiplomasiService d = SekteApi.diplomasi();
		List<String> sects = new ArrayList<>(d.sects());
		sects.sort((a, b) -> Integer.compare(d.tier(b), d.tier(a)));
		int[] colours = new int[Math.min(KURSI, sects.size())];
		for (int i = 0; i < colours.length; i++) {
			colours[i] = d.color(sects.get(i));
		}
		tag.putIntArray("warna", colours);
		return tag;
	}

	// ================================================================== rounds

	@Override
	public void tick(ServerLevel level, Kandidat k, long town, long now) {
		MinecraftServer server = level.getServer();
		CompoundTag tag = data(server, town);
		long t = Kota.timeOfDay(server);
		int babak = tag.getIntOr("babak", -1);
		int next = babak + 1;
		if (next < BABAK.length && t >= BABAK[next] && !menunggu(town)) {
			if (next == 0) {
				undi(level, town, tag);
			}
			tag.putInt("babak", next);
			simpan(server, town, tag);
			mainkanBabak(level, k, town, next);
			for (ServerPlayer p : level.players()) {
				KotaBesarKalender.segarkan(p);
			}
		}
		for (Tunggu w : List.copyOf(TUNGGU.values())) {
			if (w.town() == town && now >= w.sampai()) {
				TUNGGU.remove(w.pemain());
				hasilLaga(level, town, w.babak(), w.laga(), false, Component.translatable("kultivator.kota_besar.turnamen.waktu_habis"));
				buangLawan(level, w);
			}
		}
	}

	private static boolean menunggu(long town) {
		for (Tunggu w : TUNGGU.values()) {
			if (w.town() == town) {
				return true;
			}
		}
		return false;
	}

	/** Seats and the bracket: players first, then the strongest sects not at war with any seated player. */
	private static void undi(ServerLevel level, long town, CompoundTag tag) {
		DiplomasiService d = SekteApi.diplomasi();
		ListTag entrants = tag.getListOrEmpty("daftar");
		List<CompoundTag> seats = new ArrayList<>();
		List<UUID> owners = new ArrayList<>();
		for (int i = 0; i < entrants.size() && seats.size() < KURSI; i++) {
			CompoundTag e = entrants.getCompoundOrEmpty(i).copy();
			seats.add(e);
			try {
				owners.add(UUID.fromString(e.getStringOr("pemain", "")));
			} catch (IllegalArgumentException ignored) {
				// skip
			}
		}
		List<String> sects = new ArrayList<>(d.sects());
		sects.sort((a, b) -> d.tier(b) != d.tier(a) ? Integer.compare(d.tier(b), d.tier(a)) : a.compareTo(b));
		for (String s : sects) {
			if (seats.size() >= KURSI) {
				break;
			}
			boolean war = false;
			for (UUID o : owners) {
				war |= d.atWar(o, s);
			}
			if (war) {
				continue;
			}
			CompoundTag seat = new CompoundTag();
			seat.putString("sekte", s);
			seat.putInt("tier", Math.max(1, d.tier(s)));
			seats.add(seat);
		}
		RandomSource r = RandomSource.create(town ^ tag.getLongOr("hari", 0L) * 0x2545F4914F6CDD1DL);
		for (int i = seats.size() - 1; i > 0; i--) {
			int j = r.nextInt(i + 1);
			CompoundTag tmp = seats.get(i);
			seats.set(i, seats.get(j));
			seats.set(j, tmp);
		}
		ListTag list = new ListTag();
		list.addAll(seats);
		tag.put("kursi", list);
		ListTag rounds = new ListTag();
		ListTag first = new ListTag();
		for (int i = 0; i + 1 < seats.size(); i += 2) {
			CompoundTag m = new CompoundTag();
			m.putInt("a", i);
			m.putInt("b", i + 1);
			m.putInt("menang", -1);
			first.add(m);
		}
		if (seats.size() % 2 == 1) {
			CompoundTag bye = new CompoundTag();
			bye.putInt("a", seats.size() - 1);
			bye.putInt("b", -1);
			bye.putInt("menang", 0);
			first.add(bye);
		}
		rounds.add(first);
		tag.put("laga", rounds);
	}

	private static ListTag laga(CompoundTag tag, int babak) {
		ListTag rounds = tag.getListOrEmpty("laga");
		return babak < rounds.size() ? rounds.getListOrEmpty(babak) : new ListTag();
	}

	private static String namaKursi(CompoundTag seat) {
		return seat.getStringOr("nama", "");
	}

	private static Component nama(CompoundTag seat) {
		if (seat.contains("pemain")) {
			return Component.translatable("kultivator.kota_besar.turnamen.wakil", namaKursi(seat));
		}
		Component n = SekteApi.diplomasi().name(seat.getStringOr("sekte", ""));
		return n == null ? Component.literal(seat.getStringOr("sekte", "?")) : n;
	}

	/** Builds the matches of round {@code babak} from the winners of the round before (round 0 was drawn). */
	private void mainkanBabak(ServerLevel level, Kandidat k, long town, int babak) {
		MinecraftServer server = level.getServer();
		CompoundTag tag = data(server, town);
		if (babak > 0) {
			ListTag prev = laga(tag, babak - 1);
			List<Integer> winners = new ArrayList<>();
			for (int i = 0; i < prev.size(); i++) {
				CompoundTag m = prev.getCompoundOrEmpty(i);
				int w = m.getIntOr("menang", 0);
				winners.add(w == 1 ? m.getIntOr("b", -1) : m.getIntOr("a", -1));
			}
			ListTag now = new ListTag();
			for (int i = 0; i + 1 < winners.size(); i += 2) {
				CompoundTag m = new CompoundTag();
				m.putInt("a", winners.get(i));
				m.putInt("b", winners.get(i + 1));
				m.putInt("menang", -1);
				now.add(m);
			}
			if (winners.size() % 2 == 1) {
				CompoundTag bye = new CompoundTag();
				bye.putInt("a", winners.get(winners.size() - 1));
				bye.putInt("b", -1);
				bye.putInt("menang", 0);
				now.add(bye);
			}
			ListTag rounds = tag.getListOrEmpty("laga");
			rounds.add(now);
			tag.put("laga", rounds);
			simpan(server, town, tag);
		}
		ListTag seats = tag.getListOrEmpty("kursi");
		ListTag matches = laga(tag, babak);
		boolean shown = false;
		for (int i = 0; i < matches.size(); i++) {
			CompoundTag m = matches.getCompoundOrEmpty(i);
			if (m.getIntOr("menang", -1) >= 0) {
				continue;
			}
			CompoundTag a = seats.getCompoundOrEmpty(m.getIntOr("a", 0));
			CompoundTag b = seats.getCompoundOrEmpty(m.getIntOr("b", 0));
			ServerPlayer pa = pemain(server, a);
			ServerPlayer pb = pemain(server, b);
			if (a.contains("pemain") || b.contains("pemain")) {
				if (pa != null && pb != null) {
					mulaiPvp(level, town, babak, i, pa, pb);
				} else if (pa != null || pb != null) {
					ServerPlayer p = pa != null ? pa : pb;
					CompoundTag foe = pa != null ? b : a;
					if (!mulaiDuel(level, k, town, babak, i, p, foe)) {
						hasilLaga(level, town, babak, i, pa == null, Component.translatable("kultivator.kota_besar.turnamen.walkover", p.getDisplayName()));
					}
				} else {
					hasilLaga(level, town, babak, i, a.contains("pemain"), Component.translatable("kultivator.kota_besar.turnamen.tidak_hadir"));
				}
				continue;
			}
			int ta = a.getIntOr("tier", 1);
			int tb = b.getIntOr("tier", 1);
			double chance = Mth.clamp(0.5D + 0.08D * (ta - tb), 0.1D, 0.9D);
			RandomSource r = RandomSource.create(town ^ tag.getLongOr("hari", 0L) ^ babak * 131L + i);
			boolean aWins = r.nextDouble() < chance;
			if (!shown && ditonton(level, k, town)) {
				shown = true;
				pameran(level, k, town, a, b, aWins);
			}
			hasilLaga(level, town, babak, i, !aWins, null);
		}
	}

	private static @Nullable ServerPlayer pemain(MinecraftServer server, CompoundTag seat) {
		if (!seat.contains("pemain")) {
			return null;
		}
		try {
			return server.getPlayerList().getPlayer(UUID.fromString(seat.getStringOr("pemain", "")));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static boolean ditonton(ServerLevel level, Kandidat k, long town) {
		BlockPos at = KotaBesarKota.pusatDistrik(level, k, "arena");
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(at, 72.0D)) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable DuniaAnchorEntity panggung(long town) {
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		return kota == null ? null : kota.panggung("turnamen");
	}

	/** A sect champion body on the lei tai corner {@code sudut} (0 east-west pair, named for the sect). */
	private static @Nullable KultivatorNpc juara(ServerLevel level, long town, CompoundTag seat, int sudut) {
		DuniaAnchorEntity stage = panggung(town);
		if (stage == null) {
			return null;
		}
		List<BlockPos> corners = KotaBesarAnchors.titikPanggung(level, stage);
		BlockPos at = corners.isEmpty() ? stage.blockPosition() : corners.get(Math.min(sudut, corners.size() - 1));
		float yaw = (float) Math.toDegrees(Math.atan2(at.getX() - stage.getX(), stage.getZ() - at.getZ()));
		KultivatorNpc npc = HidupApi.tubuh(level, town, NpcRole.JUARA_SEKTE, at, yaw + 180.0F);
		if (npc == null) {
			return null;
		}
		String sect = seat.getStringOr("sekte", "");
		int tier = Math.max(1, seat.getIntOr("tier", 1));
		int look = npc.look();
		int realm = Mth.clamp(tier, 1, 7);
		npc.setup(NpcRole.JUARA_SEKTE, Mth.clamp(1 + tier / 3, 1, 4), look, realm, Math.min(3, tier % 4), Dao.values()[Math.floorMod(look >>> 11, Dao.COUNT)],
				npc.voice(), npc.robe(), Component.translatable("kultivator.kota_besar.turnamen.juara_nama", KotaNames.person(look, NpcRole.JUARA_SEKTE), nama(seat)));
		npc.memory().putString("kb_acara", ID);
		npc.memory().putString("kb_sekte", sect);
		npc.memory().putBoolean("hold", true);
		npc.setLonely(96.0D, 3600);
		npc.arm(true);
		return npc;
	}

	private static DuelRuleSet aturan(int realmGap) {
		DuelRuleSet base = DuelRuleSet.suggested(realmGap);
		return new DuelRuleSet(base.realm(), base.jurus(), DuelRuleSet.WinRule.SEPULUH_PERSEN, 9, 1800, true, DuelRuleSet.StakeKind.TANPA, 0, 0,
				null, false);
	}

	/** The player's bout against a sect champion; false when it cannot start (absent, busy). */
	private static boolean mulaiDuel(ServerLevel level, Kandidat k, long town, int babak, int laga, ServerPlayer p, CompoundTag foe) {
		DuniaAnchorEntity stage = panggung(town);
		if (stage == null || p.level() != level || !p.blockPosition().closerThan(stage.blockPosition(), 48.0D)
				|| DuelChallenges.selfProblem(p) != null) {
			return false;
		}
		KultivatorNpc npc = juara(level, town, foe, 1);
		if (npc == null) {
			return false;
		}
		List<BlockPos> corners = KotaBesarAnchors.titikPanggung(level, stage);
		if (!corners.isEmpty()) {
			BlockPos mine = corners.get(0);
			JamMalam.abaikan(p, 200L);
			p.teleportTo(mine.getX() + 0.5D, mine.getY(), mine.getZ() + 0.5D);
		}
		int gap = Cultivators.get(p).realm().ordinal() - npc.realm();
		int pair = DuelChallenges.pairOf(p, npc, null);
		Component why = pair < 0 ? Component.translatable("kultivator.duel.reason.error") : DuelChallenges.startDirect(p, p, npc, pair, aturan(gap));
		if (why != null) {
			npc.vanish();
			p.sendSystemMessage(why.copy().withStyle(ChatFormatting.RED));
			return false;
		}
		TUNGGU.put(p.getUUID(), new Tunggu(town, p.getUUID(), npc.getUUID(), babak, laga, level.getGameTime() + BATAS_DUEL));
		Msg.title(p, Component.translatable("kultivator.kota_besar.turnamen.babak." + babak).withStyle(ChatFormatting.GOLD),
				Component.translatable("kultivator.kota_besar.turnamen.lawan", nama(foe)), 5, 50, 10);
		return true;
	}

	/** Two players meet: a challenge the second must accept (a refusal or silence is his walkover). */
	private static void mulaiPvp(ServerLevel level, long town, int babak, int laga, ServerPlayer a, ServerPlayer b) {
		int gap = Cultivators.get(a).realm().ordinal() - Cultivators.get(b).realm().ordinal();
		Component why = DuelChallenges.challenge(a, b, null, aturan(gap));
		if (why != null) {
			hasilLaga(level, town, babak, laga, true, why);
			return;
		}
		TUNGGU.put(a.getUUID(), new Tunggu(town, a.getUUID(), b.getUUID(), babak, laga, level.getGameTime() + BATAS_DUEL));
	}

	private static void duelSelesai(dev.kultivator.duel.session.DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		for (Tunggu w : List.copyOf(TUNGGU.values())) {
			boolean mine = winner != null && (winner.getUUID().equals(w.pemain()) || winner.getUUID().equals(w.lawan()))
					|| loser != null && (loser.getUUID().equals(w.pemain()) || loser.getUUID().equals(w.lawan()));
			if (!mine) {
				continue;
			}
			TUNGGU.remove(w.pemain());
			ServerLevel level = winner != null ? (ServerLevel) winner.level() : loser != null ? (ServerLevel) loser.level() : null;
			if (level == null) {
				return;
			}
			boolean playerWon = winner != null && winner.getUUID().equals(w.pemain());
			// in the bracket the player's seat is "a" or "b": find which side won
			CompoundTag tag = data(level.getServer(), w.town());
			ListTag seats = tag.getListOrEmpty("kursi");
			CompoundTag m = laga(tag, w.babak()).getCompoundOrEmpty(w.laga());
			CompoundTag seatA = seats.getCompoundOrEmpty(m.getIntOr("a", 0));
			boolean playerIsA = w.pemain().toString().equals(seatA.getStringOr("pemain", ""));
			boolean bWins = playerIsA != playerWon;
			hasilLaga(level, w.town(), w.babak(), w.laga(), bWins, null);
			Voice.later(60, () -> buangLawan(level, w));
			return;
		}
	}

	private static void buangLawan(ServerLevel level, Tunggu w) {
		if (w.lawan() == null) {
			return;
		}
		Entity e = level.getEntity(w.lawan());
		if (e instanceof KultivatorNpc npc && npc.isAlive()) {
			npc.vanish();
		}
	}

	/** Writes a bout's result ({@code bMenang}: side b won) and tells the players near. */
	private static void hasilLaga(ServerLevel level, long town, int babak, int laga, boolean bMenang, @Nullable Component catatan) {
		MinecraftServer server = level.getServer();
		CompoundTag tag = data(server, town);
		ListTag rounds = tag.getListOrEmpty("laga");
		if (babak >= rounds.size()) {
			return;
		}
		ListTag matches = rounds.getListOrEmpty(babak);
		CompoundTag m = matches.getCompoundOrEmpty(laga);
		if (m.getIntOr("menang", -1) >= 0) {
			return;
		}
		m.putInt("menang", bMenang ? 1 : 0);
		simpan(server, town, tag);
		ListTag seats = tag.getListOrEmpty("kursi");
		CompoundTag w = seats.getCompoundOrEmpty(bMenang ? m.getIntOr("b", 0) : m.getIntOr("a", 0));
		CompoundTag l = seats.getCompoundOrEmpty(bMenang ? m.getIntOr("a", 0) : m.getIntOr("b", 0));
		Kandidat k = KotaBesarKota.dariSitus(server.overworld(), town);
		if (k != null) {
			BlockPos at = KotaBesarKota.pusatDistrik(level, k, "arena");
			for (ServerPlayer p : level.players()) {
				if (p.blockPosition().closerThan(at, 96.0D)) {
					p.sendSystemMessage(Component.translatable("kultivator.kota_besar.turnamen.hasil", nama(w), nama(l)).withStyle(ChatFormatting.YELLOW));
					if (catatan != null) {
						p.sendSystemMessage(catatan.copy().withStyle(ChatFormatting.GRAY));
					}
				}
			}
		}
		if (w.contains("pemain") && !l.contains("pemain") && l.contains("sekte")) {
			try {
				UUID owner = UUID.fromString(w.getStringOr("pemain", ""));
				int used = tag.getCompoundOrEmpty("diplomasi").getIntOr(owner.toString(), 0);
				int give = Math.min(10, DIPLOMASI_MAKS - used);
				if (give > 0) {
					SekteApi.diplomasi().adjust(owner, l.getStringOr("sekte", ""), give, "kotabesar.turnamen");
					CompoundTag dip = tag.getCompoundOrEmpty("diplomasi");
					dip.putInt(owner.toString(), used + give);
					tag.put("diplomasi", dip);
					simpan(server, town, tag);
				}
			} catch (IllegalArgumentException ignored) {
				// skip
			}
		}
		for (ServerPlayer p : level.players()) {
			KotaBesarKalender.segarkan(p);
		}
	}

	/** Two sect champions spar on the stage for fifteen seconds; the loser kneels, the winner salutes. */
	private void pameran(ServerLevel level, Kandidat k, long town, CompoundTag a, CompoundTag b, boolean aWins) {
		KultivatorNpc na = juara(level, town, a, 0);
		KultivatorNpc nb = juara(level, town, b, 1);
		if (na == null || nb == null) {
			if (na != null) {
				na.vanish();
			}
			if (nb != null) {
				nb.vanish();
			}
			return;
		}
		HidupApi.tatap(na, nb);
		HidupApi.tatap(nb, na);
		HidupApi.klip(na, GerakId.TANDING_A, 0, 300);
		HidupApi.klip(nb, GerakId.TANDING_B, 0, 300);
		List<UUID> bodies = pameran.computeIfAbsent(town, t -> new ArrayList<>());
		bodies.add(na.getUUID());
		bodies.add(nb.getUUID());
		KultivatorNpc winner = aWins ? na : nb;
		KultivatorNpc loser = aWins ? nb : na;
		Voice.later(300, () -> {
			if (loser.isAlive()) {
				HidupApi.klip(loser, GerakId.BERLUTUT, 0, 80);
			}
			if (winner.isAlive()) {
				HidupApi.klip(winner, GerakId.HORMAT, 0, 40);
				winner.say(Component.translatable("kultivator.kota_besar.turnamen.menang_pameran"), 80);
			}
		});
		Voice.later(420, () -> {
			if (loser.isAlive()) {
				loser.vanish();
			}
			if (winner.isAlive()) {
				winner.vanish();
			}
		});
	}

	// ================================================================== end

	@Override
	public void akhir(ServerLevel level, Kandidat k, long town, boolean hidup) {
		MinecraftServer server = level.getServer();
		CompoundTag tag = data(server, town);
		for (Tunggu w : List.copyOf(TUNGGU.values())) {
			if (w.town() == town) {
				TUNGGU.remove(w.pemain());
				buangLawan(level, w);
			}
		}
		List<UUID> shown = pameran.remove(town);
		if (shown != null) {
			for (UUID id : shown) {
				if (level.getEntity(id) instanceof KultivatorNpc npc && npc.isAlive()) {
					npc.vanish();
				}
			}
		}
		if (!tag.contains("kursi")) {
			undi(level, town, tag);
			simpan(server, town, tag);
		}
		// finish the bracket as data for every round not played
		for (int babak = Math.max(0, tag.getIntOr("babak", -1)); babak < BABAK.length; babak++) {
			if (babak > tag.getIntOr("babak", -1)) {
				tag.putInt("babak", babak);
				simpan(server, town, tag);
				mainkanData(level, town, babak);
			} else {
				ListTag matches = laga(tag, babak);
				for (int i = 0; i < matches.size(); i++) {
					if (matches.getCompoundOrEmpty(i).getIntOr("menang", -1) < 0) {
						hasilLaga(level, town, babak, i, false, null);
					}
				}
			}
			tag = data(server, town);
		}
		ListTag rounds = tag.getListOrEmpty("laga");
		if (rounds.isEmpty()) {
			return;
		}
		ListTag fin = rounds.getListOrEmpty(rounds.size() - 1);
		if (fin.isEmpty()) {
			return;
		}
		CompoundTag m = fin.getCompoundOrEmpty(0);
		ListTag seats = tag.getListOrEmpty("kursi");
		CompoundTag champ = seats.getCompoundOrEmpty(m.getIntOr("menang", 0) == 1 ? m.getIntOr("b", 0) : m.getIntOr("a", 0));
		CompoundTag runner = seats.getCompoundOrEmpty(m.getIntOr("menang", 0) == 1 ? m.getIntOr("a", 0) : m.getIntOr("b", 0));
		ServerPlayer winner = pemain(server, champ);
		if (winner != null) {
			long fee = champ.getLongOr("biaya", 0L);
			EkonomiApi.beri(winner, fee * 4L, "kotabesar.turnamen");
			Barang.beri(winner, piala(winner, KotaBesarKota.nama(server, k), Kota.day(server)));
			KotaData.get(server).addReputation(town, winner.getUUID(), 5);
			KotaBesarData.get(server).beriGelar(winner.getUUID(), "juara_turnamen");
			Msg.title(winner, Component.translatable("kultivator.kota_besar.turnamen.juara").withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.kota_besar.turnamen.juara.sub", KotaBesarKota.nama(server, k)), 10, 80, 20);
		}
		ServerPlayer second = pemain(server, runner);
		if (second != null) {
			EkonomiApi.beri(second, runner.getLongOr("biaya", 0L), "kotabesar.turnamen");
		}
		CeritaApi.kabar(level, town, "kota_besar.juara_turnamen", nama(champ));
		KotaBesarData.get(server).putString(town, "ringkasan." + ID, "kultivator.kota_besar.acara.ringkasan.turnamen");
		KotaBesarData.get(server).putString(town, "juara_turnamen", champ.contains("pemain") ? champ.getStringOr("nama", "") : champ.getStringOr("sekte", ""));
		if (hidup) {
			KotaBesarPayload.sekitar(level, new Vec3(k.pusatX(), 64.0D, k.pusatZ()), "panji", panji(level, k, town, false));
		}
	}

	/** Round {@code babak} decided as data (the day ended or nobody was near). */
	private static void mainkanData(ServerLevel level, long town, int babak) {
		MinecraftServer server = level.getServer();
		CompoundTag tag = data(server, town);
		if (babak > 0) {
			ListTag prev = laga(tag, babak - 1);
			List<Integer> winners = new ArrayList<>();
			for (int i = 0; i < prev.size(); i++) {
				CompoundTag m = prev.getCompoundOrEmpty(i);
				winners.add(m.getIntOr("menang", 0) == 1 ? m.getIntOr("b", -1) : m.getIntOr("a", -1));
			}
			ListTag now = new ListTag();
			for (int i = 0; i + 1 < winners.size(); i += 2) {
				CompoundTag m = new CompoundTag();
				m.putInt("a", winners.get(i));
				m.putInt("b", winners.get(i + 1));
				m.putInt("menang", -1);
				now.add(m);
			}
			if (winners.size() % 2 == 1) {
				CompoundTag bye = new CompoundTag();
				bye.putInt("a", winners.get(winners.size() - 1));
				bye.putInt("b", -1);
				bye.putInt("menang", 0);
				now.add(bye);
			}
			ListTag rounds = tag.getListOrEmpty("laga");
			rounds.add(now);
			tag.put("laga", rounds);
			simpan(server, town, tag);
		}
		ListTag seats = tag.getListOrEmpty("kursi");
		ListTag matches = laga(tag, babak);
		for (int i = 0; i < matches.size(); i++) {
			CompoundTag m = matches.getCompoundOrEmpty(i);
			if (m.getIntOr("menang", -1) >= 0) {
				continue;
			}
			CompoundTag a = seats.getCompoundOrEmpty(m.getIntOr("a", 0));
			CompoundTag b = seats.getCompoundOrEmpty(m.getIntOr("b", 0));
			boolean bWins;
			if (a.contains("pemain") != b.contains("pemain")) {
				bWins = a.contains("pemain");
			} else {
				double chance = Mth.clamp(0.5D + 0.08D * (a.getIntOr("tier", 1) - b.getIntOr("tier", 1)), 0.1D, 0.9D);
				bWins = RandomSource.create(town ^ babak * 977L + i).nextDouble() >= chance;
			}
			hasilLaga(level, town, babak, i, bWins, null);
		}
	}

	/** The trophy: a gilded cup (a vanilla stack with name, lore and glint), made only when won. */
	static ItemStack piala(ServerPlayer player, Component kota, long hari) {
		ItemStack stack = new ItemStack(Items.GOLD_INGOT);
		CompoundTag tag = new CompoundTag();
		tag.putLong("kultivator_piala", hari);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("kultivator.kota_besar.piala"));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kultivator.kota_besar.piala.lore", player.getDisplayName(), kota, hari),
				Component.translatable("kultivator.kota_besar.piala.lore2"))));
		stack.set(DataComponents.RARITY, Rarity.EPIC);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
		stack.set(DataComponents.MAX_STACK_SIZE, 1);
		return stack;
	}

	// ================================================================== sheet

	@Override
	public void lembar(ServerPlayer player, long town, @Nullable KultivatorNpc npc, CompoundTag tag) {
		MinecraftServer server = player.level().getServer();
		long fee = biaya(player);
		tag.putLong(LembarKunci.BIAYA, fee);
		LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.turnamen.catatan", Barang.nb(fee),
				KotaBesarKalender.jam(BABAK[0])));
		if (!KotaBesarKalender.berjalan(server, town, ID)) {
			return;
		}
		CompoundTag t = data(server, town);
		boolean registered = false;
		ListTag entrants = t.getListOrEmpty("daftar");
		for (int i = 0; i < entrants.size(); i++) {
			registered |= player.getUUID().toString().equals(entrants.getCompoundOrEmpty(i).getStringOr("pemain", ""));
		}
		tag.putBoolean(LembarKunci.Acara.TERDAFTAR, registered);
		ListTag seats = t.getListOrEmpty("kursi");
		ListTag bracket = new ListTag();
		ListTag rounds = t.getListOrEmpty("laga");
		for (int babak = 0; babak < rounds.size(); babak++) {
			ListTag matches = rounds.getListOrEmpty(babak);
			for (int i = 0; i < matches.size(); i++) {
				CompoundTag m = matches.getCompoundOrEmpty(i);
				CompoundTag row = new CompoundTag();
				row.putInt(LembarKunci.Acara.BABAK, babak);
				CompoundTag a = seats.getCompoundOrEmpty(m.getIntOr("a", 0));
				LembarKunci.teks(row, LembarKunci.Acara.A, nama(a));
				int bi = m.getIntOr("b", -1);
				CompoundTag b = bi < 0 ? new CompoundTag() : seats.getCompoundOrEmpty(bi);
				LembarKunci.teks(row, LembarKunci.Acara.B, bi < 0 ? Component.translatable("kultivator.kota_besar.turnamen.bye") : nama(b));
				row.putInt(LembarKunci.Acara.MENANG, m.getIntOr("menang", -1));
				String me = player.getUUID().toString();
				if (me.equals(a.getStringOr("pemain", "")) || me.equals(b.getStringOr("pemain", ""))) {
					row.putBoolean(LembarKunci.ANDA, true);
				}
				bracket.add(row);
			}
		}
		if (!bracket.isEmpty()) {
			tag.put(LembarKunci.Acara.BRACKET, bracket);
		}
	}

	@Override
	public void aksi(ServerPlayer player, long town, @Nullable KultivatorNpc npc, String[] op) {
		MinecraftServer server = player.level().getServer();
		if (!KotaBesarKalender.berjalan(server, town, ID)) {
			KotaBesarKalender.segarkan(player, Component.translatable("kultivator.kota_besar.acara.belum_buka"), false);
			return;
		}
		if (LembarKunci.Acara.OP_TONTON.equals(op[0])) {
			KotaBesarKalender.segarkan(player, Component.translatable("kultivator.kota_besar.turnamen.tonton"), true);
			return;
		}
		if (!LembarKunci.Acara.OP_DAFTAR.equals(op[0])) {
			return;
		}
		CompoundTag t = data(server, town);
		if (t.getIntOr("babak", -1) >= 0) {
			KotaBesarKalender.segarkan(player, Component.translatable("kultivator.kota_besar.turnamen.tutup_daftar"), false);
			return;
		}
		ListTag entrants = t.getListOrEmpty("daftar");
		for (int i = 0; i < entrants.size(); i++) {
			if (player.getUUID().toString().equals(entrants.getCompoundOrEmpty(i).getStringOr("pemain", ""))) {
				return;
			}
		}
		if (entrants.size() >= KURSI) {
			KotaBesarKalender.segarkan(player, Component.translatable("kultivator.kota_besar.turnamen.penuh"), false);
			return;
		}
		long fee = biaya(player);
		if (!EkonomiApi.bayar(player, fee, "kotabesar.turnamen")) {
			KotaBesarKalender.segarkan(player, Component.translatable("kultivator.kota_besar.hukum.kurang", Barang.nb(fee)), false);
			return;
		}
		CompoundTag e = new CompoundTag();
		e.putString("pemain", player.getUUID().toString());
		e.putString("nama", player.getGameProfile().name());
		e.putLong("biaya", fee);
		e.putInt("tier", Math.max(1, Cultivators.get(player).realm().ordinal()));
		entrants.add(e);
		t.put("daftar", entrants);
		simpan(server, town, t);
		KotaBesarKalender.segarkan(player, Component.translatable("kultivator.kota_besar.turnamen.terdaftar", KotaBesarKalender.jam(BABAK[0])), true);
	}

	static void lupakan() {
		TUNGGU.clear();
	}
}
