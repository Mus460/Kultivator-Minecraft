package dev.kultivator.sekte.tetua;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.HatiService;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceDisciples;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Laporan Pagi, the morning report (DESIGN 36.2.6). Every line a module writes through {@code SekteLog} reaches E on
 * {@code SekteBus.SECT_LOG} and is kept one world day in the hall's buffer; E adds its own quiet lines (the affairs that
 * found nothing to do), loyalty changes ({@code LOYALTY_CHANGED}) and the day's garden and mine totals
 * ({@code WORK_DONE}). The first time the master stands in the hall territory on a new day (the overworld's day begins at
 * 06:00), the Grand Elder (or, without one, the Ketua Generasi Kedua) walks up when it is within 20 blocks, bows and hands
 * the report over: the card on the screen grouped by module (at most 12 lines), a small stamp (beat 151), its voice. With
 * nobody to bring it, or out of range, the card simply appears; the hub's Balai Tetua keeps the last one either way.
 */
final class LaporanPagi {

	static final int MAX_LINES = 12;
	static final int PER_MODULE = 3;
	static final double WALK_RANGE = 20.0D;
	private static final int WALK_TIMEOUT = 400;
	private static final int BOW_TICKS = 40;

	/** A Grand Elder walking up to its master with the report. */
	private record Walk(DiscipleEntity elder, UUID master, int[] ticks, CompoundTag report, boolean[] handed) {
	}

	private static final Map<UUID, Walk> WALKS = new HashMap<>();

	// ================================================================== the buffer

	/** {@code SekteBus.SECT_LOG}: keep the line one world day for the report. */
	static void note(@Nullable MinecraftServer server, UUID owner, String module, Component line) {
		if (owner == null || line == null || Halls.hallOf(owner) == null) {
			return;
		}
		CompoundTag mine = TetuaData.hall(owner);
		ListTag log = TetuaData.list(mine, TetuaData.LOG);
		CompoundTag entry = new CompoundTag();
		entry.putLong("t", TetuaData.clock(server));
		entry.putString("mod", module == null ? "" : module);
		entry.store("text", ComponentSerialization.CODEC, line);
		log.add(entry);
		long cut = TetuaData.clock(server) - 24000L;
		while (!log.isEmpty() && (log.size() > TetuaData.LOG_MAX
				|| log.get(0) instanceof CompoundTag first && first.getLongOr("t", 0L) < cut)) {
			log.remove(0);
		}
		TetuaData.dirty();
	}

	/** {@code SekteBus.LOYALTY_CHANGED}: worse states and recoveries become report lines (P writes the details). */
	static void onLoyalty(MinecraftServer server, UUID owner, UUID soul, HatiService.Keadaan before, HatiService.Keadaan now) {
		if (before == now) {
			return;
		}
		DiscipleEntity disciple = JabatanTetua.loaded(owner, soul);
		Component name = disciple != null ? disciple.getName() : Component.translatable("kultivator.sekte.tetua.someone");
		String key = now.ordinal() > before.ordinal() ? "kultivator.sekte.tetua.lapor.loyalty_down" : "kultivator.sekte.tetua.lapor.loyalty_up";
		note(server, owner, SekteIds.P, Component.translatable(key, name, Component.translatable("kultivator.sekte.keadaan." + now.name().toLowerCase(java.util.Locale.ROOT))));
	}

	/** {@code SekteBus.WORK_DONE}: today's totals of the garden and the mine. */
	static void onWork(MinecraftServer server, UUID owner, UUID soul, String kind, int amount) {
		if (amount <= 0 || Halls.hallOf(owner) == null) {
			return;
		}
		CompoundTag work = TetuaData.child(TetuaData.hall(owner), TetuaData.WORK);
		long today = Ingatan.today(server);
		if (work.getLongOr("day", -1L) != today) {
			work.putLong("day", today);
			work.putInt("kebun", 0);
			work.putInt("tambang", 0);
		}
		String key = "tambang".equals(kind) ? "tambang" : "kebun";
		work.putInt(key, work.getIntOr(key, 0) + amount);
		TetuaData.dirty();
	}

	// ================================================================== building the report

	/** The report of the last world day: header data and at most 12 lines grouped by module (DESIGN order). */
	static CompoundTag build(MinecraftServer server, UUID owner) {
		CompoundTag mine = TetuaData.hall(owner);
		long now = TetuaData.clock(server);
		Map<String, List<CompoundTag>> byModule = new LinkedHashMap<>();
		byModule.put(SekteIds.E, new ArrayList<>());
		for (String module : SekteIds.MODULES) {
			byModule.putIfAbsent(module, new ArrayList<>());
		}
		byModule.put("", new ArrayList<>());
		for (Tag tag : mine.getListOrEmpty(TetuaData.LOG)) {
			if (tag instanceof CompoundTag entry && entry.getLongOr("t", 0L) >= now - 24000L) {
				String module = entry.getStringOr("mod", "");
				byModule.computeIfAbsent(byModule.containsKey(module) ? module : "", k -> new ArrayList<>()).add(entry);
			}
		}
		// newest lines of each module first, at most PER_MODULE each, then fill up to MAX_LINES in module order
		ListTag groups = new ListTag();
		int total = 0;
		int[] room = new int[byModule.size()];
		int i = 0;
		for (List<CompoundTag> lines : byModule.values()) {
			room[i++] = Math.min(PER_MODULE, lines.size());
		}
		int sum = 0;
		for (int r : room) {
			sum += r;
		}
		i = 0;
		int spare = Math.max(0, MAX_LINES - sum);
		for (List<CompoundTag> lines : byModule.values()) {
			int extra = Math.min(spare, lines.size() - room[i]);
			spare -= extra;
			room[i] += extra;
			i++;
		}
		i = 0;
		for (Map.Entry<String, List<CompoundTag>> group : byModule.entrySet()) {
			int take = Math.min(room[i++], MAX_LINES - total);
			if (take <= 0) {
				continue;
			}
			List<CompoundTag> lines = group.getValue();
			ListTag texts = new ListTag();
			for (int k = Math.max(0, lines.size() - take); k < lines.size(); k++) {
				CompoundTag line = new CompoundTag();
				line.store("text", ComponentSerialization.CODEC, lines.get(k).read("text", ComponentSerialization.CODEC).orElse(Component.empty()));
				texts.add(line);
			}
			CompoundTag g = new CompoundTag();
			g.putString("mod", group.getKey());
			g.put("lines", texts);
			g.putInt("hidden", lines.size() - take);
			groups.add(g);
			total += take;
		}
		CompoundTag report = new CompoundTag();
		report.putLong("day", Ingatan.today(server));
		report.put("groups", groups);
		report.putInt("count", total);
		// the day's garden and mine totals
		CompoundTag work = mine.getCompoundOrEmpty(TetuaData.WORK);
		long today = Ingatan.today(server);
		if (work.getLongOr("day", -1L) >= today - 1L) {
			report.putInt("kebun", work.getIntOr("kebun", 0));
			report.putInt("tambang", work.getIntOr("tambang", 0));
		}
		// the sect at a glance: disciples present, the unhappy, offices filled
		int present = 0;
		int unhappy = 0;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.role() == DiscipleEntity.Role.DISCIPLE) {
				present++;
				if (SekteApi.hati().state(disciple.data()) != HatiService.Keadaan.SETIA) {
					unhappy++;
				}
			}
		}
		report.putInt("hadir", present);
		report.putInt("gelisah", unhappy);
		report.putInt("jabatan", TetuaData.offices(owner).size());
		UUID agung = TetuaData.holder(owner, Jabatan.TETUA_AGUNG);
		Component by = agung != null ? TetuaData.name(TetuaData.office(owner, Jabatan.TETUA_AGUNG), "name")
				: Generasi.master(owner) != null ? TetuaData.name(mine, TetuaData.MASTER_NAME) : Component.translatable("kultivator.sekte.tetua.lapor.by_none");
		report.store("by", ComponentSerialization.CODEC, by);
		report.putInt("gen", Generasi.generation(owner));
		mine.put(TetuaData.REPORT, report.copy());
		TetuaData.dirty();
		return report;
	}

	// ================================================================== delivery

	/** Every 2 s: each online master standing in its own territory gets the day's report once. */
	static void tick(MinecraftServer server) {
		long today = Ingatan.today(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID owner = player.getUUID();
			Halls.HallView hall = Halls.hallOf(owner);
			if (hall == null || !hall.active() || player.isSpectator() || !Halls.inTerritory(owner, player.level(), player.position())) {
				continue;
			}
			CompoundTag mine = TetuaData.hall(owner);
			if (mine.getLongOr(TetuaData.REPORT_DAY, -1L) >= today || WALKS.containsKey(owner)) {
				continue;
			}
			mine.putLong(TetuaData.REPORT_DAY, today);
			TetuaData.dirty();
			deliver(server, player, false);
		}
	}

	/** Builds the report and has it brought (or shown at once with {@code instant}, or nobody to bring it). */
	static void deliver(MinecraftServer server, ServerPlayer player, boolean instant) {
		UUID owner = player.getUUID();
		CompoundTag report = build(server, owner);
		DiscipleEntity elder = instant || !Tetua.config().laporanAdegan ? null : bearer(owner, player);
		if (elder == null) {
			hand(player, null, report);
			return;
		}
		WALKS.put(owner, new Walk(elder, owner, new int[]{0}, report, new boolean[]{false}));
	}

	/** The Grand Elder (else the Ketua) when loaded, idle and within 20 blocks of the master. */
	private static @Nullable DiscipleEntity bearer(UUID owner, ServerPlayer player) {
		UUID soul = TetuaData.holder(owner, Jabatan.TETUA_AGUNG);
		if (soul == null) {
			soul = Generasi.master(owner);
		}
		DiscipleEntity elder = soul == null ? null : JabatanTetua.loaded(owner, soul);
		if (elder == null || elder.level() != player.level() || elder.distanceToSqr(player) > WALK_RANGE * WALK_RANGE
				|| !Tetua.free(elder, true) || !EpicMoments.safe(player, null)) {
			return null;
		}
		return elder;
	}

	/** Each tick for the elders walking up (at most one per master). */
	static void tickWalks(MinecraftServer server) {
		if (WALKS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Walk>> it = WALKS.entrySet().iterator();
		while (it.hasNext()) {
			Walk walk = it.next().getValue();
			DiscipleEntity elder = walk.elder();
			ServerPlayer player = server.getPlayerList().getPlayer(walk.master());
			int t = ++walk.ticks()[0];
			if (player == null) {
				release(elder);
				it.remove();
				continue;
			}
			boolean gone = elder.isRemoved() || !elder.isAlive() || elder.level() != player.level() || elder.getTarget() != null;
			if (walk.handed()[0]) {
				if (t >= BOW_TICKS || gone) {
					release(elder);
					it.remove();
				}
				continue;
			}
			if (gone || t > WALK_TIMEOUT || elder.distanceToSqr(player) > 48.0D * 48.0D) {
				release(elder);
				hand(player, null, walk.report());
				it.remove();
				continue;
			}
			if (elder.distanceToSqr(player) <= 2.6D * 2.6D) {
				// arrived: bow, speak, hand over
				elder.setActivity(SekteIds.ACT_BOW, null);
				elder.getNavigation().stop();
				elder.getLookControl().setLookAt(player, 30.0F, 30.0F);
				hand(player, elder, walk.report());
				walk.handed()[0] = true;
				walk.ticks()[0] = 0;
				continue;
			}
			if (t % 20 == 1) {
				// the spot beside the master, on the elder's side
				Vec3 to = elder.position().subtract(player.position());
				Vec3 dir = to.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : to.normalize();
				BlockPos target = BlockPos.containing(player.position().add(dir.scale(1.6D)));
				BlockPos spot = JabatanTetua.standable(player.level(), target);
				elder.setActivity(SekteIds.ACT_OFFICE, spot == null ? player.blockPosition() : spot);
			}
		}
	}

	private static void release(DiscipleEntity elder) {
		if (!elder.isRemoved() && (elder.activity() == SekteIds.ACT_BOW || elder.activity() == SekteIds.ACT_OFFICE)) {
			elder.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	/** True while {@code entity} is bringing a report. */
	static boolean walking(DiscipleEntity entity) {
		for (Walk walk : WALKS.values()) {
			if (walk.elder() == entity) {
				return true;
			}
		}
		return false;
	}

	/** The card, the stamp and the voice. */
	private static void hand(ServerPlayer player, @Nullable DiscipleEntity elder, CompoundTag report) {
		CompoundTag sent = report.copy();
		sent.putBoolean("brought", elder != null);
		TetuaPayloads.report(player, sent);
		if (elder != null && elder.level() instanceof ServerLevel level) {
			Vec3 pos = elder.position();
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0F, 1.0F);
			EpicHooks.extra(level, Tetua.BEAT_REPORT, player, elder, pos, report.getIntOr("count", 0), 0);
			String voice = "disciple." + VoiceDisciples.voiceOf(elder) + ".report";
			if (VoiceEvents.exists(VoiceEvents.normalize(voice))) {
				Voice.say(elder, voice, null);
			} else {
				VoiceDisciples.line(elder, "greet", 200);
			}
			Msg.actionBar(player, Component.translatable("kultivator.sekte.tetua.lapor.brought", elder.getName()));
		} else {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F, 1.1F);
		}
	}

	static void clear() {
		WALKS.clear();
	}

	private LaporanPagi() {
	}
}
