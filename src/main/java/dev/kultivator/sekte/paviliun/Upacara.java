package dev.kultivator.sekte.paviliun;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.sekte.SekteSuara;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Upacara, the ancestral rites of module A (DESIGN 36.2.5; {@code ANCESTOR_RITE} kind 0 incense, 1 Qingming, 2 funeral):
 * <ul>
 * <li><b>Dupa harian</b>: the master offers a stick of Dupa ({@link Dupa}) at the altar, once a world day; smoke rises
 * from the censer for two minutes and the disciples nearby keep the moment.</li>
 * <li><b>Qingming</b>: every {@code qingmingHari} days (started by itself when the master is home in daylight and no
 * monster is near, or from the Makam screen) every free disciple in the territory walks to rows before the altar (or
 * the cemetery's stele) and kneels for sixty seconds: three kowtows (drawn by A's own kneeling pose, timed by the rite
 * payload, so nobody has to leave its spot), incense, the graves swept with light, then a memory and the rite on the
 * bus. Scene 140 opens it for the master.</li>
 * <li><b>Pemakaman</b>: when a grave is made the dead's friends who are home kneel at the tombstone until the next
 * morning (at most {@code berkabungMenitMaks} real minutes); scene 141.</li>
 * </ul>
 * Rites wait for the world clock; they never change the time or the weather (DESIGN 36.0 rule 2). A kneeling disciple
 * that is attacked or called away simply leaves the rite. Runs on the server tick: the funeral and Qingming lists are
 * walked once a second, the Qingming check once every 10 s per online master.
 * <p>
 * Sumber: Qingming, hari menyapu makam dan sembahyang leluhur (adat Tiongkok); papan arwah leluhur.
 */
final class Upacara {

	static final int KIND_INCENSE = 0;
	static final int KIND_QINGMING = 1;
	static final int KIND_FUNERAL = 2;
	static final int BEAT_QINGMING = EpicIds.EXTRA_SEKTE_FIRST + 40;
	static final int BEAT_FUNERAL = EpicIds.EXTRA_SEKTE_FIRST + 41;

	static final int INCENSE_TICKS = 2400;
	static final int GATHER_TICKS = 400;
	static final int QINGMING_TICKS = 1200;
	/** The three kowtows of the Qingming rite, in ticks after it begins (the client times its pose by them). */
	static final int[] BOWS = {200, 500, 800};
	static final int SWEEP_TICK = 1000;

	/** One running rite of a hall. */
	static final class Rite {
		final UUID owner;
		final int kind;
		final ServerLevel level;
		final BlockPos centre;
		final Map<UUID, DiscipleEntity> who = new HashMap<>();
		final List<UUID> attended = new ArrayList<>();
		long started;
		long riteStart = -1L;
		long ends;
		@Nullable Component name;

		Rite(UUID owner, int kind, ServerLevel level, BlockPos centre) {
			this.owner = owner;
			this.kind = kind;
			this.level = level;
			this.centre = centre;
		}
	}

	private static final Map<UUID, Rite> QINGMING = new HashMap<>();
	private static final List<Rite> FUNERALS = new ArrayList<>();

	static void clear() {
		QINGMING.clear();
		FUNERALS.clear();
	}

	static @Nullable Rite qingming(UUID owner) {
		return QINGMING.get(owner);
	}

	/** Every running rite of {@code owner} (for the world sync). */
	static List<Rite> rites(UUID owner) {
		List<Rite> list = new ArrayList<>();
		Rite q = QINGMING.get(owner);
		if (q != null) {
			list.add(q);
		}
		for (Rite f : FUNERALS) {
			if (f.owner.equals(owner)) {
				list.add(f);
			}
		}
		return list;
	}

	// ================================================================== daily incense

	/** The master offers Dupa at the altar: true when the rite happened (one stick used). */
	static boolean incense(ServerPlayer master, ItemStack stack) {
		UUID owner = master.getUUID();
		MinecraftServer server = master.level().getServer();
		long today = Ingatan.today(server);
		if (PaviliunData.getLong(owner, PaviliunData.INCENSE_DAY, -1L) == today) {
			Msg.actionBar(master, Component.translatable("kultivator.sekte.paviliun.dupa.today").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!master.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		PaviliunData.putLong(owner, PaviliunData.INCENSE_DAY, today);
		AltarLeluhur.rite(server, owner);
		BlockPos censer = Tempat.censer(owner);
		Vec3 at = censer == null ? master.position() : Vec3.atBottomCenterOf(censer);
		List<UUID> attendees = new ArrayList<>();
		for (DiscipleEntity d : Disciples.loaded(owner)) {
			if (d.level() == master.level() && d.distanceToSqr(at) <= 16.0D * 16.0D && d.isAlive()) {
				attendees.add(d.soulId());
				Ingatan.remember(server, d.data(), SekteIds.A + ".dupa", null, 1, "kultivator.sekte.paviliun.ingat.dupa", "");
			}
		}
		ServerLevel level = (ServerLevel) master.level();
		level.playSound(null, at.x, at.y + 0.5D, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4F, 1.6F);
		level.playSound(null, at.x, at.y + 0.5D, at.z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.0F);
		level.playSound(null, at.x, at.y + 0.5D, at.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.5F, 1.4F);
		Fx.ring(level, at.add(0.0D, 0.1D, 0.0D), 0xFFC878, 1.2F);
		PaviliunSync.smoke(owner, censer, level.getGameTime() + INCENSE_TICKS);
		SekteBus.ANCESTOR_RITE.invoker().onAncestorRite(server, owner, KIND_INCENSE, attendees);
		SekteLog.log(server, owner, SekteIds.A, Component.translatable("kultivator.sekte.paviliun.log.incense", master.getDisplayName()));
		Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.dupa.done", Math.round(AltarLeluhur.blessing(owner) * 100.0D),
				attendees.size()).withColor(0xFFD9A0));
		Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + "paviliun_upacara");
		return true;
	}

	// ================================================================== funeral

	/** A grave was made: the mourners kneel at it until the morning. */
	static void funeral(MinecraftServer server, UUID owner, PaviliunData.Grave grave, @Nullable BlockPos stone, List<DiscipleEntity> mourners) {
		ServerLevel level = Makam.hallLevel(server, owner);
		BlockPos centre = stone != null ? stone : Tempat.riteCentre(owner);
		List<UUID> souls = new ArrayList<>();
		for (DiscipleEntity d : mourners) {
			souls.add(d.soulId());
			Ingatan.remember(server, d.data(), SekteIds.A + ".berkabung", grave.soul(), -3, "kultivator.sekte.paviliun.ingat.berkabung",
					grave.name().getString());
		}
		AltarLeluhur.rite(server, owner);
		if (level != null && centre != null && level.isLoaded(centre) && !mourners.isEmpty()) {
			Rite rite = new Rite(owner, KIND_FUNERAL, level, centre);
			rite.name = grave.name();
			rite.started = level.getGameTime();
			rite.riteStart = rite.started;
			long dayTime = Math.floorMod(level.getOverworldClockTime(), 24000L);
			long untilMorning = 24000L - dayTime;
			rite.ends = rite.started + Math.min(untilMorning, Paviliun.config().berkabungMenitMaks * 1200L);
			List<BlockPos> spots = Tempat.rows(level, centre, mourners.size(), 2);
			for (int i = 0; i < mourners.size() && i < spots.size(); i++) {
				DiscipleEntity d = mourners.get(i);
				d.setActivity(SekteIds.ACT_MOURN, spots.get(i));
				rite.who.put(d.soulId(), d);
				rite.attended.add(d.soulId());
				if (i == 0) {
					SekteSuara.line(d, "mourn", "farewell", 400);
				}
			}
			if (!rite.who.isEmpty()) {
				FUNERALS.add(rite);
				PaviliunSync.smoke(owner, centre, rite.ends);
			}
			ServerPlayer master = server.getPlayerList().getPlayer(owner);
			if (master != null && master.level() == level && master.distanceToSqr(Vec3.atCenterOf(centre)) <= 48.0D * 48.0D && Paviliun.config().adegan) {
				EpicMoments.send(level, EpicIds.EXTRA, BEAT_FUNERAL, null, master, Vec3.atBottomCenterOf(centre), level.getGameTime(), souls.size(), 0,
						0.0F, 2, 0);
			}
		}
		SekteBus.ANCESTOR_RITE.invoker().onAncestorRite(server, owner, KIND_FUNERAL, souls);
		PaviliunSync.dirty(owner);
	}

	// ================================================================== Qingming

	/** True when the Qingming is due for {@code owner} today. */
	static boolean due(MinecraftServer server, UUID owner) {
		long today = Ingatan.today(server);
		long last = PaviliunData.getLong(owner, PaviliunData.QINGMING_DAY, -1L);
		if (last < 0L) {
			long founded = PaviliunData.getLong(owner, PaviliunData.FOUNDED_DAY, -1L);
			if (founded < 0L) {
				PaviliunData.putLong(owner, PaviliunData.FOUNDED_DAY, today);
				return false;
			}
			last = founded;
		}
		return today - last >= Paviliun.config().qingmingHari;
	}

	/** Days until the next Qingming (0 when due). */
	static long daysLeft(MinecraftServer server, UUID owner) {
		long today = Ingatan.today(server);
		long last = PaviliunData.getLong(owner, PaviliunData.QINGMING_DAY, PaviliunData.getLong(owner, PaviliunData.FOUNDED_DAY, today));
		return Math.max(0L, last + Paviliun.config().qingmingHari - today);
	}

	/** True when the sect has something to honour (a grave, a lineage record or a built altar). */
	static boolean honourable(UUID owner) {
		return !PaviliunData.graves(owner).isEmpty() || !PaviliunData.lineage(owner).isEmpty()
				|| SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_ALTAR) != null;
	}

	/** Starts the Qingming for {@code owner} now; null on success, else a lang key of why not. */
	static @Nullable String startQingming(MinecraftServer server, UUID owner, boolean force) {
		if (QINGMING.containsKey(owner)) {
			return "kultivator.sekte.paviliun.qingming.running";
		}
		if (!force && !due(server, owner)) {
			return "kultivator.sekte.paviliun.qingming.not_due";
		}
		ServerLevel level = Makam.hallLevel(server, owner);
		BlockPos centre = Tempat.riteCentre(owner);
		if (level == null || centre == null || !level.isLoaded(centre)) {
			return "kultivator.sekte.paviliun.qingming.no_place";
		}
		Rite rite = new Rite(owner, KIND_QINGMING, level, centre);
		rite.started = level.getGameTime();
		rite.ends = rite.started + GATHER_TICKS + QINGMING_TICKS;
		List<DiscipleEntity> free = new ArrayList<>();
		for (DiscipleEntity d : Disciples.loaded(owner)) {
			if (d.level() == level && Tempat.free(d, true, DiscipleEntity.ACTIVITY_NONE)) {
				free.add(d);
			}
		}
		Vec3 c = Vec3.atCenterOf(centre);
		free.sort(Comparator.comparingDouble(d -> d.distanceToSqr(c)));
		int max = Paviliun.config().qingmingBarisan;
		List<BlockPos> spots = Tempat.rows(level, centre, Math.min(max, free.size()), 3, Tempat.front(owner));
		for (int i = 0; i < free.size() && i < spots.size(); i++) {
			DiscipleEntity d = free.get(i);
			d.setActivity(SekteIds.ACT_MOURN, spots.get(i));
			rite.who.put(d.soulId(), d);
			if (i == 0) {
				SekteSuara.line(d, "qingming", "loyal", 400);
			}
		}
		QINGMING.put(owner, rite);
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.qingming.begin", rite.who.size()).withColor(0xBFE8C8));
		}
		SekteLog.log(server, owner, SekteIds.A, Component.translatable("kultivator.sekte.paviliun.log.qingming_begin", rite.who.size()));
		PaviliunSync.dirty(owner);
		return null;
	}

	/** Every 10 s per online master: start a due Qingming when the master is home, in daylight, with no monster near. */
	static void autoCheck(MinecraftServer server, ServerPlayer master) {
		UUID owner = master.getUUID();
		if (!Paviliun.config().qingmingOtomatis || QINGMING.containsKey(owner) || !due(server, owner) || !honourable(owner)) {
			return;
		}
		if (!Halls.inTerritory(owner, master.level(), master.position()) || master.hurtTime > 0) {
			return;
		}
		long dayTime = Math.floorMod(master.level().getOverworldClockTime(), 24000L);
		if (dayTime > 9000L) {
			return;
		}
		AABB box = master.getBoundingBox().inflate(16.0D);
		if (!master.level().getEntitiesOfClass(Monster.class, box, m -> m.isAlive()).isEmpty()) {
			return;
		}
		startQingming(server, owner, false);
	}

	// ================================================================== tick (once a second)

	static void tick(MinecraftServer server) {
		for (Iterator<Rite> it = FUNERALS.iterator(); it.hasNext(); ) {
			Rite rite = it.next();
			long now = rite.level.getGameTime();
			prune(rite);
			if (now >= rite.ends || rite.who.isEmpty()) {
				release(rite);
				it.remove();
				PaviliunSync.dirty(rite.owner);
			}
		}
		for (Iterator<Map.Entry<UUID, Rite>> it = QINGMING.entrySet().iterator(); it.hasNext(); ) {
			Rite rite = it.next().getValue();
			if (Halls.hallOf(rite.owner) == null) {
				release(rite);
				it.remove();
				continue;
			}
			if (stepQingming(server, rite)) {
				it.remove();
				PaviliunSync.dirty(rite.owner);
			}
		}
	}

	/** One second of a Qingming; true when it is over. */
	private static boolean stepQingming(MinecraftServer server, Rite rite) {
		long now = rite.level.getGameTime();
		prune(rite);
		if (rite.riteStart < 0L) {
			boolean all = true;
			for (DiscipleEntity d : rite.who.values()) {
				BlockPos spot = d.activitySpot();
				if (spot != null && d.distanceToSqr(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D) > 2.25D) {
					all = false;
					break;
				}
			}
			if (all || now - rite.started >= GATHER_TICKS) {
				begin(server, rite, now);
			}
			return false;
		}
		long t = now - rite.riteStart;
		for (int bow : BOWS) {
			if (t >= bow && t < bow + 20) {
				Vec3 at = Vec3.atBottomCenterOf(rite.centre);
				rite.level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.7F, 0.8F);
			}
		}
		if (t >= SWEEP_TICK && t < SWEEP_TICK + 20) {
			sweep(rite);
		}
		if (t >= QINGMING_TICKS) {
			finish(server, rite);
			return true;
		}
		return false;
	}

	private static void begin(MinecraftServer server, Rite rite, long now) {
		rite.riteStart = now;
		rite.ends = now + QINGMING_TICKS;
		rite.attended.addAll(rite.who.keySet());
		BlockPos censer = Tempat.censer(rite.owner);
		PaviliunSync.smoke(rite.owner, censer, rite.ends);
		PaviliunSync.dirty(rite.owner);
		Vec3 at = Vec3.atBottomCenterOf(rite.centre);
		rite.level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 0.7F);
		ServerPlayer master = server.getPlayerList().getPlayer(rite.owner);
		if (master != null && master.level() == rite.level && master.distanceToSqr(at) <= 48.0D * 48.0D && Paviliun.config().adegan) {
			EpicMoments.send(rite.level, EpicIds.EXTRA, BEAT_QINGMING, null, master, at, now, rite.who.size(), 0, 0.0F, 3, 0);
		}
	}

	/** The graves are swept: a breath of pale light over each built tombstone. */
	private static void sweep(Rite rite) {
		for (PaviliunData.Grave g : PaviliunData.graves(rite.owner)) {
			if (g.slot() < 0) {
				continue;
			}
			BlockPos stone = SectBuild.anchor(rite.owner, PaviliunKomponen.ANCHOR_GRAVE + g.slot());
			if (stone != null && rite.level.isLoaded(stone)) {
				Fx.burst(rite.level, Vec3.atBottomCenterOf(stone).add(0.0D, 0.6D, 0.0D), 0xCFF5D8, 10, 0.6F);
			}
		}
	}

	private static void finish(MinecraftServer server, Rite rite) {
		long today = Ingatan.today(server);
		PaviliunData.putLong(rite.owner, PaviliunData.QINGMING_DAY, today);
		AltarLeluhur.rite(server, rite.owner);
		for (UUID soul : rite.attended) {
			DiscipleEntity d = rite.who.get(soul);
			if (d != null) {
				Ingatan.remember(server, d.data(), SekteIds.A + ".qingming", null, 2, "kultivator.sekte.paviliun.ingat.qingming", "");
			}
		}
		release(rite);
		SekteBus.ANCESTOR_RITE.invoker().onAncestorRite(server, rite.owner, KIND_QINGMING, List.copyOf(rite.attended));
		SekteLog.log(server, rite.owner, SekteIds.A, Component.translatable("kultivator.sekte.paviliun.log.qingming_end", rite.attended.size()));
		ServerPlayer master = server.getPlayerList().getPlayer(rite.owner);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.qingming.end", rite.attended.size(),
					Math.round(AltarLeluhur.blessing(rite.owner) * 100.0D), Paviliun.config().qingmingHari).withColor(0xBFE8C8));
			if (Halls.inTerritory(rite.owner, master.level(), master.position())) {
				Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + "paviliun_upacara");
			}
		}
	}

	/** Drops the disciples that left (attacked, called away, unloaded, another activity). */
	private static void prune(Rite rite) {
		rite.who.values().removeIf(d -> d.isRemoved() || !d.isAlive() || d.activity() != SekteIds.ACT_MOURN || d.getTarget() != null);
	}

	private static void release(Rite rite) {
		for (DiscipleEntity d : rite.who.values()) {
			if (!d.isRemoved() && d.activity() == SekteIds.ACT_MOURN) {
				d.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			}
		}
		rite.who.clear();
	}

	/** Ends every rite of {@code owner} at once (test command, module switched off). */
	static void stopAll(UUID owner) {
		Rite q = QINGMING.remove(owner);
		if (q != null) {
			release(q);
		}
		for (Iterator<Rite> it = FUNERALS.iterator(); it.hasNext(); ) {
			Rite f = it.next();
			if (f.owner.equals(owner)) {
				release(f);
				it.remove();
			}
		}
		PaviliunSync.dirty(owner);
	}

	/** True while {@code entity} kneels in one of A's rites (so the study and others leave it alone). */
	static boolean inRite(DiscipleEntity entity) {
		UUID owner = entity.ownerId();
		if (owner == null) {
			return false;
		}
		Rite q = QINGMING.get(owner);
		if (q != null && q.who.containsKey(entity.soulId())) {
			return true;
		}
		for (Rite f : FUNERALS) {
			if (f.owner.equals(owner) && f.who.containsKey(entity.soulId())) {
				return true;
			}
		}
		return false;
	}

	private Upacara() {
	}
}
