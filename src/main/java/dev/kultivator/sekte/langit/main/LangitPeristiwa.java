package dev.kultivator.sekte.langit.main;

import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.sekte.langit.npc.JaringNpc;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sky events scheduler (DESIGN 37.7.4). Once per world day, at {@link #CHECK_TIME} (mid-morning), every standing sky
 * hall whose owner or a sect mate is online within {@link #PRESENT_RANGE} blocks rolls at most one event, and at most one
 * every {@link #GAP_DAYS} days:
 * <ol>
 * <li><b>Utusan Istana Langit</b> ({@link LangitUtusan}): A7+ at tier 9 with the Gate of the Nine Heavens, every 12 days
 * (± 2), arriving at dusk.</li>
 * <li><b>Bajak Awan</b> ({@link LangitBajak}): from tier 3, on average every {@code bajakHari} (8) days.</li>
 * <li><b>Tamu Sekte Langit</b>: a friendly or allied NPC sky sect sends a party of 2-3 for five minutes (+3 standing; an
 * ally brings spirit herbs), every 10 days (± 2).</li>
 * </ol>
 * The chosen event waits as "pending" until the player is free ({@code util.Sibuk.why}; an enemy event also needs
 * {@code Sibuk.enemyRefusal} null and then counts with {@code Sibuk.enemyTaken}), asking again every
 * {@code Sibuk.RETRY_TICKS}; a pending event lapses at the next day and stays due. <b>Pesta Lentera</b> (the lantern
 * festival) is no roll: three nights after the Ascension, every 96 world days and the night after a sect tournament; it
 * only raises the festival flag of the sync (37V draws the lanterns) and calls the watching disciples. Everything here
 * is off with {@code sekteLangit.aktif}; each event has its own switch.
 */
public final class LangitPeristiwa {

	public static final long CHECK_TIME = 6000L;
	public static final double PRESENT_RANGE = 160.0D;
	public static final int GAP_DAYS = 3;
	public static final int UTUSAN_DAYS = 12;
	public static final int TAMU_DAYS = 10;
	public static final int FESTIVAL_EVERY = 96;
	public static final int FESTIVAL_AFTER_ASCENT = 3;
	public static final int VISIT_TICKS = 20 * 60 * 5;
	public static final int BEAT_TAMU = EpicIds.EXTRA_SEKTE_FIRST + 98;
	public static final int BEAT_PESTA = EpicIds.EXTRA_SEKTE_FIRST + 99;

	public static final String KIND_BAJAK = "bajak";
	public static final String KIND_UTUSAN = "utusan";
	public static final String KIND_TAMU = "tamu";

	/** A guest party of an NPC sky sect visiting a hall. */
	private record Visit(UUID owner, ServerLevel level, long until, List<DiscipleEntity> bodies) {
	}

	private static final Map<UUID, Visit> VISITS = new HashMap<>();

	public static long today(MinecraftServer server) {
		return Math.floorDiv(server.overworld().getOverworldClockTime(), 24000L);
	}

	static long timeOfDay(MinecraftServer server) {
		return Math.floorMod(server.overworld().getOverworldClockTime(), 24000L);
	}

	static void clear() {
		for (Visit visit : VISITS.values()) {
			for (DiscipleEntity body : visit.bodies()) {
				JaringNpc.vanish(body);
			}
		}
		VISITS.clear();
	}

	// ================================================================== the daily roll

	/** Once a second (server tick). */
	static void second(MinecraftServer server) {
		LangitConfig config = LangitConfig.get();
		if (!config.aktif) {
			return;
		}
		ServerLevel overworld = server.overworld();
		long today = today(server);
		long time = timeOfDay(server);
		LangitData data = LangitData.get(server);
		for (SkyHallView view : LangitApi.get().all(overworld)) {
			if (view.tethered()) {
				continue;
			}
			UUID owner = view.owner();
			CompoundTag hall = data.hall(owner);
			try {
				festivalDue(server, view, hall, today, time);
				if (time >= CHECK_TIME && hall.getLongOr("cek", Long.MIN_VALUE) != today) {
					hall.putLong("cek", today);
					data.setDirty();
					roll(server, view, hall, today, overworld.getRandom());
				}
				pending(server, view, hall, today, time);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sekte Langit events failed for {}", owner, exception);
			}
		}
		visits(server);
	}

	private static void roll(MinecraftServer server, SkyHallView view, CompoundTag hall, long today, RandomSource random) {
		LangitConfig config = LangitConfig.get();
		if (present(server, view) == null) {
			return;
		}
		if (!hall.contains("bajak_berikut")) {
			hall.putLong("bajak_berikut", today + rollDays(config.bajakHari, random));
		}
		if (!hall.contains("utusan_berikut")) {
			hall.putLong("utusan_berikut", today + UTUSAN_DAYS - 2 + random.nextInt(5));
		}
		if (!hall.contains("tamu_berikut")) {
			hall.putLong("tamu_berikut", today + TAMU_DAYS - 2 + random.nextInt(5));
		}
		if (hall.contains("tunda") || today - hall.getLongOr("terakhir", Long.MIN_VALUE / 2) < GAP_DAYS) {
			return;
		}
		String kind = null;
		if (config.utusanLangit && LangitUtusan.eligible(view) && today >= hall.getLongOr("utusan_berikut", Long.MAX_VALUE)
				&& !LangitUtusan.orderOpen(hall)) {
			kind = KIND_UTUSAN;
		} else if (config.bajakAwan && view.tier() >= 3 && today >= hall.getLongOr("bajak_berikut", Long.MAX_VALUE)) {
			kind = KIND_BAJAK;
		} else if (today >= hall.getLongOr("tamu_berikut", Long.MAX_VALUE) && friendlySect(server, view.owner()) != null) {
			kind = KIND_TAMU;
		}
		if (kind != null) {
			hall.putString("tunda", kind);
			hall.putLong("tunda_hari", today);
			hall.putLong("tunda_coba", 0L);
			LangitData.get(server).setDirty();
		}
	}

	/** Bajak days: {@code days} on average (half to one and a half). */
	static int rollDays(int days, RandomSource random) {
		int d = Math.max(2, days);
		return Math.max(2, d / 2 + random.nextInt(d + 1));
	}

	private static void pending(MinecraftServer server, SkyHallView view, CompoundTag hall, long today, long time) {
		if (!hall.contains("tunda")) {
			return;
		}
		LangitData data = LangitData.get(server);
		if (hall.getLongOr("tunda_hari", today) != today) {
			// lapsed with the day; it stays due and is rolled again tomorrow
			hall.remove("tunda");
			data.setDirty();
			return;
		}
		String kind = hall.getStringOr("tunda", "");
		if (KIND_UTUSAN.equals(kind) && !LangitSites.dusk(server.overworld())) {
			return;
		}
		long now = server.overworld().getGameTime();
		if (now - hall.getLongOr("tunda_coba", 0L) < Sibuk.RETRY_TICKS) {
			return;
		}
		hall.putLong("tunda_coba", now);
		ServerPlayer player = present(server, view);
		if (player == null || Sibuk.why(player) != null) {
			return;
		}
		boolean enemy = KIND_BAJAK.equals(kind);
		if (enemy && Sibuk.enemyRefusal(player) != null) {
			return;
		}
		RandomSource random = server.overworld().getRandom();
		Component refused = switch (kind) {
			case KIND_BAJAK -> LangitBajak.start(server, view.owner(), 0, false, false);
			case KIND_UTUSAN -> LangitUtusan.arrive(server, view.owner(), false);
			case KIND_TAMU -> tamu(server, view.owner(), false);
			default -> Component.empty();
		};
		hall.remove("tunda");
		data.setDirty();
		if (refused != null) {
			return;
		}
		hall.putLong("terakhir", today);
		switch (kind) {
			case KIND_BAJAK -> {
				hall.putLong("bajak_berikut", today + rollDays(LangitConfig.get().bajakHari, random));
				Sibuk.enemyTaken(player, "bajak_awan");
			}
			case KIND_UTUSAN -> hall.putLong("utusan_berikut", today + UTUSAN_DAYS - 2 + random.nextInt(5));
			case KIND_TAMU -> hall.putLong("tamu_berikut", today + TAMU_DAYS - 2 + random.nextInt(5));
			default -> {
			}
		}
	}

	/** The owner, else a sect mate, online in the hall's level within {@link #PRESENT_RANGE} blocks of the throne. */
	static @Nullable ServerPlayer present(MinecraftServer server, SkyHallView view) {
		ServerLevel level = server.getLevel(view.dim());
		if (level == null) {
			return null;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(view.owner());
		if (owner != null && owner.level() == level && view.horizontal(owner.position()) <= PRESENT_RANGE) {
			return owner;
		}
		for (ServerPlayer player : level.players()) {
			if (view.horizontal(player.position()) <= PRESENT_RANGE && LangitApi.get().member(view.owner(), player)) {
				return player;
			}
		}
		return null;
	}

	// ================================================================== guests (Tamu Sekte Langit)

	/** A friendly or allied NPC sky sect of the hall (its module D id), or null. */
	static @Nullable String friendlySect(MinecraftServer server, UUID owner) {
		for (LangitNpcPlan.Istana istana : LangitNpcPlan.of(server.overworld().getSeed())) {
			String id = LangitNpcPlan.sectId(istana);
			if (id == null) {
				continue;
			}
			Sikap sikap = SekteApi.diplomasi().stance(owner, id);
			if (sikap == Sikap.RAMAH || sikap == Sikap.SEKUTU) {
				return id;
			}
		}
		return null;
	}

	/** A guest party lands on the island for five minutes (null when it came, else why not). */
	static @Nullable Component tamu(MinecraftServer server, UUID owner, boolean test) {
		SkyHallView view = LangitApi.get().skyHall(owner);
		if (view == null || view.tethered()) {
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_aula");
		}
		if (VISITS.containsKey(owner)) {
			return Component.translatable("kultivator.sekte_langit.tamu.sudah");
		}
		String sectId = friendlySect(server, owner);
		if (sectId == null && !test) {
			return Component.translatable("kultivator.sekte_langit.tamu.tanpa_sekutu");
		}
		ServerLevel level = server.getLevel(view.dim());
		if (level == null) {
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_aula");
		}
		Component sect = sectId == null ? Component.translatable("kultivator.sekte_langit.npc.tanpa_nama") : SekteApi.diplomasi().name(sectId);
		if (sect == null) {
			sect = Component.translatable("kultivator.sekte_langit.npc.tanpa_nama");
		}
		BlockPos spot = landingSpot(view);
		RandomSource random = level.getRandom();
		int count = 2 + random.nextInt(2);
		List<DiscipleEntity> bodies = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2.0D * i / count;
			Vec3 at = Vec3.atBottomCenterOf(spot).add(Math.cos(angle) * 1.5D, 0.0D, Math.sin(angle) * 1.5D);
			Component name = Component.translatable(i == 0 ? "kultivator.sekte_langit.tamu.pemimpin" : "kultivator.sekte_langit.tamu.anggota", sect);
			DiscipleEntity body = JaringNpc.spawn(level, at, owner.getLeastSignificantBits() + i * 7919L, 14 + random.nextInt(10), name, spot);
			if (body != null) {
				bodies.add(body);
			}
		}
		if (bodies.isEmpty()) {
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_tempat");
		}
		VISITS.put(owner, new Visit(owner, level, level.getGameTime() + VISIT_TICKS, bodies));
		boolean allied = sectId != null && SekteApi.diplomasi().stance(owner, sectId) == Sikap.SEKUTU;
		if (sectId != null) {
			SekteApi.diplomasi().adjust(owner, sectId, 3, "langit.tamu");
		}
		Component line = Component.translatable(allied ? "kultivator.sekte_langit.tamu.datang_sekutu" : "kultivator.sekte_langit.tamu.datang", sect);
		if (allied) {
			for (int i = 0; i < 2; i++) {
				ItemStack herb = RRewards.herb(random, 200, 500);
				ItemStack rest = Storehouse.insert(owner, herb);
				ServerPlayer player = server.getPlayerList().getPlayer(owner);
				if (!rest.isEmpty() && player != null) {
					LangitBajak.give(player, rest);
				}
			}
		}
		LangitBajak.tellIsland(server, view, line.copy().withStyle(ChatFormatting.AQUA));
		SekteLog.log(server, owner, "langit", line);
		LangitData.get(server).chronicle(owner, today(server), line);
		level.playSound(null, spot.getX(), spot.getY(), spot.getZ(), SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.0F, 1.2F);
		LangitSuaraServer.say(bodies.get(0), "sekte_langit.tamu_salam", "elder.welcome");
		ServerPlayer viewer = server.getPlayerList().getPlayer(owner);
		if (viewer != null) {
			scene(level, BEAT_TAMU, bodies.get(0), viewer, Vec3.atCenterOf(spot), 0);
		}
		return null;
	}

	/** True for a guest body visiting {@code owner}'s island (37M's {@code LangitJatuh.addGuestRule}: Jaring Awan protects it). */
	static boolean guest(UUID owner, Entity entity) {
		Visit visit = VISITS.get(owner);
		return visit != null && entity instanceof DiscipleEntity body && visit.bodies().contains(body);
	}

	/** Where guests and envoys land: the ferry stop, then the arrival spot, then the throne plaza. */
	static BlockPos landingSpot(SkyHallView view) {
		for (String id : new String[]{"feri_atas", "arrival", "tahta"}) {
			BlockPos pos = LangitApi.get().anchor(view.owner(), id);
			if (pos != null) {
				return id.equals("tahta") ? pos.offset(0, 0, 4) : pos;
			}
		}
		return view.origin().offset(0, 0, 4);
	}

	private static void visits(MinecraftServer server) {
		if (VISITS.isEmpty()) {
			return;
		}
		for (Visit visit : List.copyOf(VISITS.values())) {
			JaringNpc.second(visit.level(), visit.bodies());
			if (visit.level().getGameTime() >= visit.until()) {
				VISITS.remove(visit.owner());
				for (DiscipleEntity body : visit.bodies()) {
					JaringNpc.vanish(body);
				}
				SkyHallView view = LangitApi.get().skyHall(visit.owner());
				if (view != null) {
					LangitBajak.tellIsland(server, view, Component.translatable("kultivator.sekte_langit.tamu.pamit").withStyle(ChatFormatting.GRAY));
				}
			}
		}
	}

	// ================================================================== the lantern festival

	/** True on a festival night of the owner's hall (the sync's festival flag). */
	public static boolean festival(MinecraftServer server, UUID owner) {
		CompoundTag hall = LangitData.get(server).peekHall(owner);
		if (hall == null || !LangitConfig.on()) {
			return false;
		}
		long time = timeOfDay(server);
		boolean night = time >= 12500L && time < 23000L;
		return night && today(server) <= hall.getLongOr("pesta_sampai", Long.MIN_VALUE);
	}

	/** Festival nights from today on ({@code nights} 1 = tonight). */
	static void festivalFor(MinecraftServer server, UUID owner, int nights) {
		LangitData data = LangitData.get(server);
		CompoundTag hall = data.hall(owner);
		long until = today(server) + Math.max(1, nights) - 1;
		hall.putLong("pesta_sampai", Math.max(until, hall.getLongOr("pesta_sampai", Long.MIN_VALUE)));
		data.setDirty();
		LangitSyncServer.dirty(owner);
	}

	private static void festivalDue(MinecraftServer server, SkyHallView view, CompoundTag hall, long today, long time) {
		if (!hall.contains("pesta_berikut")) {
			hall.putLong("pesta_berikut", today + FESTIVAL_EVERY);
			LangitData.get(server).setDirty();
		} else if (today >= hall.getLongOr("pesta_berikut", Long.MAX_VALUE)) {
			hall.putLong("pesta_berikut", today + FESTIVAL_EVERY);
			festivalFor(server, view.owner(), 1);
			LangitData.get(server).chronicle(view.owner(), today, Component.translatable("kultivator.sekte_langit.pesta.tahun_baru"));
		}
		// the first festival night of a run: the stamp once for a present member
		if (time >= 12500L && time < 12520L && festival(server, view.owner()) && hall.getLongOr("pesta_dilihat", Long.MIN_VALUE) != today) {
			hall.putLong("pesta_dilihat", today);
			LangitData.get(server).setDirty();
			LangitSyncServer.dirty(view.owner());
			ServerPlayer player = present(server, view);
			if (player != null) {
				Msg.chat(player, Component.translatable("kultivator.sekte_langit.pesta.mulai").withStyle(ChatFormatting.GOLD));
				scene(player.level(), BEAT_PESTA, player, player, player.position(), 0);
			}
		}
	}

	// ================================================================== scenes

	/** One story beat (7b scene: every camera guard applies; the client may only show the stamp). */
	static void scene(ServerLevel level, int beat, @Nullable Entity subject, ServerPlayer viewer, Vec3 pos, int number) {
		try {
			if (viewer.level() != level) {
				return;
			}
			EpicMoments.send(level, EpicIds.EXTRA, beat, subject, viewer, pos, level.getGameTime(), number, 0, 0.0F, 3, EpicIds.FLAG_FINAL);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Sekte Langit scene {} failed", beat, exception);
		}
	}

	/** Test hook: the pending/next days of a hall for {@code uji langit_sekte info}. */
	static CompoundTag info(MinecraftServer server, UUID owner) {
		CompoundTag hall = LangitData.get(server).peekHall(owner);
		return hall == null ? new CompoundTag() : hall.copy();
	}

	private LangitPeristiwa() {
	}
}
