package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quest goals and stage machine (DESIGN 38.S.1; owner 38ST). A quest stage names goals as {@code "<jenis> <arg> <arg>..."};
 * progress comes from events ({@code DuniaEvents}, deaths, duels, site discovery, talking) and from other modules through
 * {@code CeritaApi.peristiwa(player, jenisPeristiwa, id)}. Each goal kind has a registered {@link Tujuan} that says
 * whether an event matches; 38ST registers {@code pergi}, {@code bicara}, {@code kalahkan}, {@code antar},
 * {@code kawal}, {@code duel}, {@code racik}, {@code tempa}, {@code donasi}, {@code bebaskan}, {@code tanda}, {@code misi_gilda},
 * {@code ujian} ({@code tunggu} is
 * counted by the clock); 38TK registers {@code naik_lift}, {@code ziarah_selesai}, {@code lentera_dilepas},
 * {@code kapal_lintas}, ... (38.T.14.3). A goal kind that is not registered here but is a {@link Syarat} id is met while
 * the condition holds ({@code punya cerita:surat_bibi_hong}, {@code bendera x}, {@code alur 3}).
 *
 * <p>Stage numbers: 0 never started, 1..n current stage (1-based), {@link TempatEvents#QUEST_SELESAI} finished,
 * {@link TempatEvents#QUEST_GAGAL} failed. Every change fires {@link TempatEvents#QUEST}.
 */
public final class QuestTracker {

	/** Says whether event {@code (jenisPeristiwa, id)} advances a goal written with {@code args}. */
	@FunctionalInterface
	public interface Tujuan {
		boolean cocok(List<String> args, String jenisPeristiwa, String id);
	}

	private static final Map<String, Tujuan> TUJUAN = new ConcurrentHashMap<>();
	/** Guards against effects that start or advance quests from inside an advance (bounded recursion). */
	private static int kedalaman;

	/** Registers goal kind {@code jenis} (a second registration replaces the first with a warning). */
	public static void daftarTujuan(String jenis, Tujuan tujuan) {
		if (TUJUAN.put(jenis, tujuan) != null) {
			Kultivator.LOGGER.warn("Quest goal kind {} registered twice; the later one wins", jenis);
		}
	}

	public static boolean terdaftar(String jenis) {
		return TUJUAN.containsKey(jenis);
	}

	/** True when goal {@code "<jenis> <args>"} matches the event. */
	public static boolean cocok(String jenis, List<String> args, String jenisPeristiwa, String id) {
		Tujuan t = TUJUAN.get(jenis);
		if (t == null) {
			return false;
		}
		try {
			return t.cocok(args, jenisPeristiwa, id);
		} catch (RuntimeException e) {
			Kultivator.LOGGER.warn("Quest goal kind {} failed", jenis, e);
			return false;
		}
	}

	/** The built-in goal kinds (from {@code Cerita.init}). */
	static void daftarDasar() {
		daftarTujuan("pergi", (a, j, id) -> j.equals("pergi") && !a.isEmpty() && id.startsWith(a.get(0)));
		daftarTujuan("bicara", (a, j, id) -> j.equals("bicara") && !a.isEmpty() && id.equals(a.get(0)));
		daftarTujuan("kalahkan", (a, j, id) -> j.equals("kalahkan") && !a.isEmpty() && (a.get(0).equals("*") || id.equals(a.get(0))));
		daftarTujuan("antar", (a, j, id) -> j.equals("antar") && a.size() >= 2 && id.equals(a.get(0) + "@" + a.get(1)));
		daftarTujuan("kawal", (a, j, id) -> j.equals("kawal") && a.size() >= 2 && id.startsWith(a.get(0) + "@" + a.get(1)));
		daftarTujuan("duel", (a, j, id) -> j.equals("duel") && !a.isEmpty() && id.equals(a.get(0)));
		daftarTujuan("racik", (a, j, id) -> j.equals("racik") && !a.isEmpty() && (a.get(0).equals("*") || id.equals(a.get(0))));
		daftarTujuan("tempa", (a, j, id) -> {
			if (!j.equals("tempa") || a.isEmpty()) {
				return false;
			}
			int colon = id.lastIndexOf(':');
			String shape = colon < 0 ? id : id.substring(0, colon);
			int grade = colon < 0 ? 0 : (int) Isian.parse(id.substring(colon + 1), 0L);
			return (a.get(0).equals("*") || a.get(0).equals(shape)) && grade >= (a.size() > 1 ? Isian.parse(a.get(1), 0L) : 0L);
		});
		daftarTujuan("donasi", (a, j, id) -> j.equals("donasi") && Isian.parse(id, 0L) >= (a.isEmpty() ? 1L : Isian.parse(a.get(0), 1L)));
		daftarTujuan("bebaskan", (a, j, id) -> j.equals("bebaskan") && (a.isEmpty() || id.equals(a.get(0))));
		daftarTujuan("tunggu", (a, j, id) -> false);
		// a story mark fired by a dialog effect "peristiwa tanda <id>" (gathering support, clues...): "tanda <id> xN"
		daftarTujuan("tanda", (a, j, id) -> j.equals("tanda") && !a.isEmpty() && id.equals(a.get(0)));
		// a finished guild mission of 38EK (CeritaApi.peristiwa(player, "misi_gilda", jenis)): "misi_gilda <jenis|*>"
		daftarTujuan("misi_gilda", (a, j, id) -> j.equals("misi_gilda") && (a.isEmpty() || a.get(0).equals("*") || id.equals(a.get(0))));
		// a passed trial of 38GU (CeritaApi.peristiwa(player, "ujian", "pertapa:meditasi" | "makam:<dao>:<tier>" |
		// "cermin_hati")): "ujian <id>" matches the id itself or any id below it ("ujian makam" = any tomb)
		daftarTujuan("ujian", (a, j, id) -> j.equals("ujian") && !a.isEmpty() && (id.equals(a.get(0)) || id.startsWith(a.get(0) + ":")));
	}

	// ================================================================== state queries

	public static int tahap(ServerPlayer player, String quest) {
		CompoundTag record = CeritaData.get(player.level().getServer()).quest(player.getUUID(), quest);
		return record.getIntOr("tahap", 0);
	}

	/** Id of the current (or last) stage, "" when never started. */
	public static String simpul(ServerPlayer player, String quest) {
		return CeritaData.get(player.level().getServer()).quest(player.getUUID(), quest).getStringOr("simpul", "");
	}

	/** Ending stage id of a finished quest ("" otherwise). */
	public static String hasil(ServerPlayer player, String quest) {
		return CeritaData.get(player.level().getServer()).quest(player.getUUID(), quest).getStringOr("hasil", "");
	}

	/** The place a quest was given at (0 none). */
	public static long situs(ServerPlayer player, String quest) {
		return CeritaData.get(player.level().getServer()).quest(player.getUUID(), quest).getLongOr("situs", 0L);
	}

	public static boolean aktif(ServerPlayer player, String quest) {
		int stage = tahap(player, quest);
		return stage > 0 && stage < TempatEvents.QUEST_SELESAI;
	}

	// ================================================================== start / advance

	/** Starts quest {@code id} for {@code player} at place {@code situs}; false when unknown, running or done (not repeatable). */
	public static boolean mulai(ServerPlayer player, String id, long situs) {
		Quest quest = CeritaReload.quest(id);
		if (quest == null || !CeritaConfig.jalan() || quest.alur() && !CeritaConfig.get().alur) {
			return false;
		}
		int stage = tahap(player, id);
		if (stage > 0 && stage < TempatEvents.QUEST_SELESAI || stage == TempatEvents.QUEST_SELESAI && !quest.ulang()) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		long day = Kota.day(server);
		CompoundTag record = new CompoundTag();
		record.putLong("mulai", day);
		record.putLong("situs", situs);
		CeritaData data = CeritaData.get(server);
		data.putQuest(player.getUUID(), id, record);
		if (data.tracked(player.getUUID()).isEmpty()) {
			data.setTracked(player.getUUID(), id);
		}
		Msg.chat(player, Component.translatable("kultivator.cerita.quest.baru", Component.translatable(quest.kunciNama()).withStyle(ChatFormatting.GOLD))
				.withStyle(ChatFormatting.YELLOW));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
		masuk(player, quest, quest.tahap().get(0));
		return true;
	}

	/** Completes the current stage of {@code id} now (effect {@code quest_maju}, tests); false when not running. */
	public static boolean maju(ServerPlayer player, String id) {
		Quest quest = CeritaReload.quest(id);
		if (quest == null || !aktif(player, id)) {
			return false;
		}
		Quest.Tahap stage = quest.tahap(simpul(player, id));
		if (stage == null) {
			return false;
		}
		selesaiTahap(player, quest, stage);
		return true;
	}

	/** Jumps to stage {@code stageId} (tests: {@code uji cerita quest <id> <tahap>}); starts the quest when needed. */
	public static boolean lompat(ServerPlayer player, String id, String stageId) {
		Quest quest = CeritaReload.quest(id);
		if (quest == null) {
			return false;
		}
		if (Quest.SELESAI.equals(stageId) || Quest.GAGAL.equals(stageId)) {
			akhiri(player, quest, simpul(player, id), Quest.SELESAI.equals(stageId));
			return true;
		}
		Quest.Tahap stage = quest.tahap(stageId);
		if (stage == null) {
			return false;
		}
		if (!aktif(player, id)) {
			CompoundTag record = new CompoundTag();
			record.putLong("mulai", Kota.day(player.level().getServer()));
			CeritaData.get(player.level().getServer()).putQuest(player.getUUID(), id, record);
		}
		masuk(player, quest, stage);
		return true;
	}

	/** Forgets a quest record (tests). */
	public static void lupakan(ServerPlayer player, String id) {
		CeritaData.get(player.level().getServer()).removeQuest(player.getUUID(), id);
	}

	private static void masuk(ServerPlayer player, Quest quest, Quest.Tahap stage) {
		MinecraftServer server = player.level().getServer();
		CeritaData data = CeritaData.get(server);
		CompoundTag record = data.quest(player.getUUID(), quest.id()).copy();
		int index = quest.indeks(stage.id());
		record.putInt("tahap", index);
		record.putString("simpul", stage.id());
		record.putLong("hari", Kota.day(server));
		record.putIntArray("hitung", new int[stage.tujuan().size()]);
		data.putQuest(player.getUUID(), quest.id(), record);
		TempatEvents.fireQuest(player, quest.id(), index);
		if (index > 1) {
			Msg.chat(player, Component.translatable("kultivator.cerita.quest.tahap_baru", Component.translatable(quest.kunciNama()).withStyle(ChatFormatting.GOLD),
					Component.translatable(quest.kunciTahap(stage.id())).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.YELLOW));
		} else {
			Msg.chat(player, Component.translatable(quest.kunciTahap(stage.id())).withStyle(ChatFormatting.GRAY));
		}
		jalankanAkibat(player, stage.akibatMasuk());
		periksaKondisi(player, quest);
	}

	private static void selesaiTahap(ServerPlayer player, Quest quest, Quest.Tahap stage) {
		jalankanAkibat(player, stage.akibat());
		if (!stage.id().equals(simpul(player, quest.id())) || !aktif(player, quest.id())) {
			return; // an effect already moved or ended the quest
		}
		String next = stage.ke();
		for (Quest.Cabang branch : stage.cabang()) {
			if (Syarat.semua(player, branch.syarat())) {
				next = branch.ke();
				break;
			}
		}
		pindah(player, quest, stage, next);
	}

	private static void pindah(ServerPlayer player, Quest quest, Quest.Tahap from, String next) {
		if (Quest.SELESAI.equals(next) || Quest.GAGAL.equals(next)) {
			akhiri(player, quest, from.id(), Quest.SELESAI.equals(next));
			return;
		}
		Quest.Tahap target = quest.tahap(next);
		if (target == null) {
			Kultivator.LOGGER.warn("Quest {} stage {} leads to unknown stage {}; finishing", quest.id(), from.id(), next);
			akhiri(player, quest, from.id(), true);
			return;
		}
		masuk(player, quest, target);
	}

	private static void akhiri(ServerPlayer player, Quest quest, String lastStage, boolean done) {
		MinecraftServer server = player.level().getServer();
		CeritaData data = CeritaData.get(server);
		CompoundTag record = data.quest(player.getUUID(), quest.id()).copy();
		int stage = done ? TempatEvents.QUEST_SELESAI : TempatEvents.QUEST_GAGAL;
		record.putInt("tahap", stage);
		record.putString("hasil", lastStage);
		record.putLong("selesai", Kota.day(server));
		data.putQuest(player.getUUID(), quest.id(), record);
		if (quest.id().equals(data.tracked(player.getUUID()))) {
			data.setTracked(player.getUUID(), "");
		}
		TempatEvents.fireQuest(player, quest.id(), stage);
		Component name = Component.translatable(quest.kunciNama()).withStyle(ChatFormatting.GOLD);
		Msg.chat(player, Component.translatable(done ? "kultivator.cerita.quest.selesai" : "kultivator.cerita.quest.gagal", name)
				.withStyle(done ? ChatFormatting.GREEN : ChatFormatting.RED));
		Msg.chat(player, Component.translatable(quest.kunciHasil(lastStage)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		player.level().playSound(null, player.blockPosition(), done ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BASS.value(),
				SoundSource.PLAYERS, 0.6F, done ? 1.2F : 0.6F);
	}

	private static void jalankanAkibat(ServerPlayer player, List<String> effects) {
		if (effects.isEmpty()) {
			return;
		}
		if (kedalaman > 8) {
			Kultivator.LOGGER.warn("Quest effects nest too deep; skipped {}", effects);
			return;
		}
		kedalaman++;
		try {
			Akibat.semua(player, effects);
		} finally {
			kedalaman--;
		}
	}

	// ================================================================== events

	/** An event of {@code player} (from {@code CeritaApi.peristiwa} and the built-in listeners): advances matching goals. */
	public static void peristiwa(ServerPlayer player, String jenisPeristiwa, String id) {
		if (!CeritaConfig.jalan() || player.isRemoved()) {
			return;
		}
		CeritaData data = CeritaData.get(player.level().getServer());
		for (String questId : data.questIds(player.getUUID())) {
			Quest quest = CeritaReload.quest(questId);
			if (quest == null || !aktif(player, questId)) {
				continue;
			}
			Quest.Tahap stage = quest.tahap(simpul(player, questId));
			if (stage == null || stage.tujuan().isEmpty()) {
				continue;
			}
			CompoundTag record = data.quest(player.getUUID(), questId).copy();
			int[] counts = counts(record, stage);
			boolean changed = false;
			for (int i = 0; i < stage.tujuan().size(); i++) {
				Quest.Tujuan goal = stage.tujuan().get(i);
				if (counts[i] < goal.jumlah() && cocok(goal.jenis(), goal.args(), jenisPeristiwa, id)) {
					counts[i]++;
					changed = true;
					if (goal.jumlah() > 1 && counts[i] < goal.jumlah()) {
						Msg.actionBar(player, Component.translatable("kultivator.cerita.quest.hitung", Component.translatable(quest.kunciNama()),
								counts[i], goal.jumlah()).withStyle(ChatFormatting.YELLOW));
					}
				}
			}
			if (changed) {
				record.putIntArray("hitung", counts);
				data.putQuest(player.getUUID(), questId, record);
				if (lengkap(player, stage, counts, record)) {
					selesaiTahap(player, quest, stage);
				}
			}
		}
	}

	/** Re-checks the condition goals ({@link Syarat} kinds), waits and deadlines of every running quest (story tick). */
	static void periksa(ServerPlayer player) {
		CeritaData data = CeritaData.get(player.level().getServer());
		long day = Kota.day(player.level().getServer());
		for (String questId : data.questIds(player.getUUID())) {
			Quest quest = CeritaReload.quest(questId);
			if (quest == null || !aktif(player, questId)) {
				continue;
			}
			Quest.Tahap stage = quest.tahap(simpul(player, questId));
			if (stage == null) {
				continue;
			}
			CompoundTag record = data.quest(player.getUUID(), questId);
			if (stage.batasHari() > 0 && day - record.getLongOr("hari", day) >= stage.batasHari()) {
				Msg.chat(player, Component.translatable("kultivator.cerita.quest.waktu_habis", Component.translatable(quest.kunciNama()))
						.withStyle(ChatFormatting.RED));
				pindah(player, quest, stage, stage.gagalKe());
				continue;
			}
			periksaKondisi(player, quest);
		}
	}

	private static void periksaKondisi(ServerPlayer player, Quest quest) {
		if (!aktif(player, quest.id())) {
			return;
		}
		Quest.Tahap stage = quest.tahap(simpul(player, quest.id()));
		if (stage == null || stage.tujuan().isEmpty()) {
			return;
		}
		CompoundTag record = CeritaData.get(player.level().getServer()).quest(player.getUUID(), quest.id());
		if (lengkap(player, stage, counts(record, stage), record)) {
			selesaiTahap(player, quest, stage);
		}
	}

	private static int[] counts(CompoundTag record, Quest.Tahap stage) {
		int[] stored = record.getIntArray("hitung").orElse(new int[0]);
		int[] counts = new int[stage.tujuan().size()];
		System.arraycopy(stored, 0, counts, 0, Math.min(stored.length, counts.length));
		return counts;
	}

	/** True when every goal of the stage is met (event goals by count, waits by the clock, condition goals now). */
	private static boolean lengkap(ServerPlayer player, Quest.Tahap stage, int[] counts, CompoundTag record) {
		long day = Kota.day(player.level().getServer());
		for (int i = 0; i < stage.tujuan().size(); i++) {
			Quest.Tujuan goal = stage.tujuan().get(i);
			if ("tunggu".equals(goal.jenis())) {
				long days = goal.args().isEmpty() ? 1L : Isian.parse(goal.args().get(0), 1L);
				if (day - record.getLongOr("hari", day) < days) {
					return false;
				}
			} else if (terdaftar(goal.jenis())) {
				if (counts[i] < goal.jumlah()) {
					return false;
				}
			} else if (Syarat.terdaftar(goal.jenis()) || goal.jenis().startsWith("!")) {
				if (!Syarat.cek(player, goal.baris())) {
					return false;
				}
			} else {
				return false;
			}
		}
		return true;
	}

	// ================================================================== journal helpers

	/** The goal line a player sees for the current stage (the stage text), or null. */
	public static @Nullable Component baris(ServerPlayer player, String questId) {
		Quest quest = CeritaReload.quest(questId);
		if (quest == null || !aktif(player, questId)) {
			return null;
		}
		return Component.translatable(quest.kunciTahap(simpul(player, questId)));
	}

	/** The site the journal needle points at for a running quest, or null. */
	public static @Nullable DuniaSites.Site arah(ServerPlayer player, String questId) {
		Quest quest = CeritaReload.quest(questId);
		if (quest == null || !aktif(player, questId)) {
			return null;
		}
		Quest.Tahap stage = quest.tahap(simpul(player, questId));
		String kind = stage == null ? "" : stage.arah();
		if (kind.isEmpty()) {
			for (Quest.Tujuan goal : stage == null ? List.<Quest.Tujuan>of() : stage.tujuan()) {
				if ("pergi".equals(goal.jenis()) && !goal.args().isEmpty()) {
					kind = goal.args().get(0);
					break;
				}
			}
		}
		if (kind.isEmpty()) {
			return null;
		}
		DuniaSites.Site best = null;
		double bestD = Double.MAX_VALUE;
		for (DuniaSites.Site site : DuniaSites.known(player)) {
			if (site.kind().startsWith(kind)) {
				double d = site.distance(player.level().dimension(), player.blockPosition());
				if (d < bestD) {
					bestD = d;
					best = site;
				}
			}
		}
		if (best == null && player.level() instanceof ServerLevel level) {
			List<DuniaSites.Site> near = DuniaSites.near(level, player.blockPosition(), 2000, kind);
			best = near.isEmpty() ? null : near.get(0);
		}
		return best;
	}

	private QuestTracker() {
	}
}
