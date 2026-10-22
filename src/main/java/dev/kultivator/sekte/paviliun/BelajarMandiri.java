package dev.kultivator.sekte.paviliun;

import dev.kultivator.dao.TechniqueBooks;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Belajar Mandiri, self-study in the Paviliun Kitab (DESIGN 36.2.5). Once every 15 s per disciple (spread by soul) an idle
 * disciple of a sect whose pavilion stands ({@code library} anchor built) and whose Rak Kitab holds something it may learn
 * ({@link RakKitab#choose}) walks to a reading seat around the anchor (at most {@code pembacaMaks} readers) and sits
 * reading ({@code ACT_STUDY}, a scroll in its hands). Seated it gains study points every second:
 * <pre>1 x (comprehension / 50 + 0.5) x workFactor (P) x officeFactor(TETUA_AJAR) (E) x 1.1 (Rendah Hati) x kecepatanBelajar</pre>
 * and needs {@code poinPerTeknik x (grade + 1)} (a Kuning art about ten real minutes, a Langit one about forty). Points stay
 * on the disciple when it is called away, so the book is picked up again another time. When done it learns the
 * technique for free ({@code DiscipleData.learn}: no qi of the master, no Bond), fires {@code DISCIPLE_STUDIED}, keeps a
 * +3 memory, and may learn no other technique that world day.
 * <p>
 * Lazy Malas disciples only sometimes sit down to read; Rajin ones always when they can; readers do not read at night.
 */
final class BelajarMandiri {

	/** Beat of the quiet "comprehended" stamp (no camera move). */
	static final int BEAT_LEARNED = EpicIds.EXTRA_SEKTE_FIRST + 43;
	private static final int CHECK_SECONDS = 15;
	private static final int WALK_LIMIT_SECONDS = 60;

	/** Seconds each reader has been on its way (to give up an unreachable seat); cleared on stop. */
	private static final Map<UUID, Integer> WALKING = new HashMap<>();

	static void clear() {
		WALKING.clear();
	}

	/** Once a second per disciple (from {@code Paviliun.tick20}). */
	static void tick20(MinecraftServer server, UUID owner, DiscipleEntity entity) {
		if (entity.activity() == SekteIds.ACT_STUDY) {
			read(server, owner, entity);
			return;
		}
		int phase = (int) Math.floorMod(entity.soulId().getLeastSignificantBits(), CHECK_SECONDS);
		if ((entity.tickCount / 20) % CHECK_SECONDS == phase) {
			maybeStart(server, owner, entity);
		}
	}

	// ================================================================== starting

	private static void maybeStart(MinecraftServer server, UUID owner, DiscipleEntity entity) {
		if (!(entity.level() instanceof ServerLevel level) || !Tempat.free(entity, false, DiscipleEntity.ACTIVITY_NONE)) {
			return;
		}
		long dayTime = level.getOverworldClockTime() % 24000L;
		if (dayTime > 13000L && dayTime < 23500L) {
			// night: the pavilion's lamps are out
			return;
		}
		DiscipleData data = entity.data();
		long today = Ingatan.today(server);
		CompoundTag record = data.extra().getCompound(SekteIds.EXTRA_A).orElse(null);
		if (record != null && record.getLongOr(PaviliunData.DAY_DONE, -1L) == today) {
			return;
		}
		BlockPos library = SectBuild.anchor(owner, "library");
		if (library == null || PaviliunData.rack(owner).isEmpty()) {
			return;
		}
		List<DiscipleEntity> readers = readers(owner);
		if (readers.size() >= Paviliun.config().pembacaMaks) {
			return;
		}
		double chance = SekteApi.hati().has(data, Sifat.RAJIN) ? 1.0D : SekteApi.hati().has(data, Sifat.MALAS) ? 0.35D : 0.7D;
		if (level.getRandom().nextDouble() >= chance) {
			return;
		}
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		RakKitab.Pick pick = continuing(entity, owner, master);
		if (pick == null) {
			pick = RakKitab.choose(entity, owner, master);
		}
		if (pick == null) {
			return;
		}
		List<BlockPos> taken = new ArrayList<>();
		for (DiscipleEntity r : readers) {
			if (r.activitySpot() != null) {
				taken.add(r.activitySpot());
			}
		}
		BlockPos seat = Tempat.seat(level, library, taken);
		if (seat == null) {
			return;
		}
		start(server, owner, entity, pick, seat);
	}

	/** The book it was reading before (its points kept), when it is still on the shelf and still allowed. */
	private static @Nullable RakKitab.Pick continuing(DiscipleEntity entity, UUID owner, @Nullable ServerPlayer master) {
		CompoundTag record = entity.data().extra().getCompound(SekteIds.EXTRA_A).orElse(null);
		if (record == null) {
			return null;
		}
		String id = record.getStringOr(PaviliunData.SPEC, "");
		int book = record.getIntOr(PaviliunData.BOOK, -1);
		TechniqueSpec spec = id.isEmpty() ? null : TechniqueRegistry.byId(id);
		if (spec == null || !RakKitab.holds(owner, book, id) || RakKitab.blocker(entity, spec, master) != null) {
			return null;
		}
		return new RakKitab.Pick(spec, book);
	}

	/** Sends {@code entity} to read {@code pick} at {@code seat} (also the test command's entry). */
	static void start(MinecraftServer server, UUID owner, DiscipleEntity entity, RakKitab.Pick pick, BlockPos seat) {
		CompoundTag record = PaviliunData.study(entity.data());
		if (!pick.spec().id().equals(record.getStringOr(PaviliunData.SPEC, ""))) {
			record.putDouble(PaviliunData.POINTS, 0.0D);
		}
		record.putString(PaviliunData.SPEC, pick.spec().id());
		record.putInt(PaviliunData.BOOK, pick.book());
		entity.setActivity(SekteIds.ACT_STUDY, seat);
		WALKING.put(entity.soulId(), 0);
		if (entity.level().getRandom().nextInt(3) == 0) {
			VoiceDisciples.line(entity, "cultivate", 600);
		}
	}

	// ================================================================== reading

	private static void read(MinecraftServer server, UUID owner, DiscipleEntity entity) {
		BlockPos seat = entity.activitySpot();
		DiscipleData data = entity.data();
		CompoundTag record = data.extra().getCompound(SekteIds.EXTRA_A).orElse(null);
		String id = record == null ? "" : record.getStringOr(PaviliunData.SPEC, "");
		TechniqueSpec spec = id.isEmpty() ? null : TechniqueRegistry.byId(id);
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (seat == null || spec == null || !Tempat.free(entity, false, SekteIds.ACT_STUDY)
				|| !RakKitab.holds(owner, record.getIntOr(PaviliunData.BOOK, -1), id)) {
			stop(entity);
			return;
		}
		String why = RakKitab.blocker(entity, spec, master);
		if (why != null && !"master".equals(why)) {
			stop(entity);
			return;
		}
		boolean seated = entity.distanceToSqr(seat.getX() + 0.5D, seat.getY(), seat.getZ() + 0.5D) <= 2.25D;
		if (!seated) {
			int walked = WALKING.merge(entity.soulId(), 1, Integer::sum);
			if (walked > WALK_LIMIT_SECONDS) {
				stop(entity);
			}
			return;
		}
		WALKING.remove(entity.soulId());
		if (why != null) {
			// a legacy art waits for its master's hooks: the reader keeps its place but gains nothing
			return;
		}
		double points = record.getDoubleOr(PaviliunData.POINTS, 0.0D) + rate(owner, data);
		double need = need(spec);
		if (points >= need) {
			learn(server, owner, entity, spec, master);
			return;
		}
		record.putDouble(PaviliunData.POINTS, points);
		if (entity.tickCount % 200 < 20 && entity.level() instanceof ServerLevel level) {
			Vec3 at = entity.position();
			level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.5F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		}
	}

	/** Study points per second of a seated reader. */
	static double rate(UUID owner, DiscipleData data) {
		double rate = data.comprehension() / 50.0D + 0.5D;
		rate *= clamp(SekteApi.hati().workFactor(data), 0.25D, 3.0D);
		rate *= clamp(SekteApi.tetua().officeFactor(owner, Jabatan.TETUA_AJAR), 0.5D, 3.0D);
		if (SekteApi.hati().has(data, Sifat.RENDAH_HATI)) {
			rate *= 1.1D;
		}
		return rate * Paviliun.config().kecepatanBelajar;
	}

	/** Points a technique needs: {@code poinPerTeknik x (grade + 1)}. */
	static double need(TechniqueSpec spec) {
		return (double) Paviliun.config().poinPerTeknik * (spec.grade().tier() + 1);
	}

	/** The reader learns {@code spec}: free, one a day, a memory, the bus, the master's word and a quiet stamp. */
	static void learn(MinecraftServer server, UUID owner, DiscipleEntity entity, TechniqueSpec spec, @Nullable ServerPlayer master) {
		DiscipleData data = entity.data();
		long today = Ingatan.today(server);
		CompoundTag record = PaviliunData.study(data);
		record.remove(PaviliunData.SPEC);
		record.remove(PaviliunData.POINTS);
		record.remove(PaviliunData.BOOK);
		record.putLong(PaviliunData.DAY_DONE, today);
		record.putInt(PaviliunData.TOTAL, record.getIntOr(PaviliunData.TOTAL, 0) + 1);
		boolean learned = data.learn(spec.id());
		if (learned) {
			slot(entity, spec);
		}
		entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		WALKING.remove(entity.soulId());
		entity.refreshSynced();
		if (!learned) {
			return;
		}
		PaviliunData.bump(owner, PaviliunData.LEARNED);
		Component techniqueName = spec.displayName().copy().withColor(spec.grade().ribbon());
		Ingatan.remember(server, data, SekteIds.A + ".belajar", null, 3, "kultivator.sekte.paviliun.ingat.belajar", spec.displayName().getString());
		SekteBus.DISCIPLE_STUDIED.invoker().onStudied(server, owner, entity.soulId(), spec.id(), true);
		SekteBus.honored(server, owner, entity.soulId(), SekteIds.A + ".belajar", 2);
		SekteLog.log(server, owner, SekteIds.A, Component.translatable("kultivator.sekte.paviliun.log.learned", entity.getName(), techniqueName));
		if (entity.level() instanceof ServerLevel level) {
			Vec3 at = entity.position();
			Fx.send(level, at, FxType.DAO_CHOSEN, spec.dao() == null ? -1 : spec.dao().ordinal(), TechniqueBooks.FX_LEARNED, 0.0F);
			Fx.pillar(level, at, spec.dao() == null ? TechniqueBooks.UNIVERSAL_COLOR : spec.dao().color(), 2.5F);
			level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1.0F, 1.2F);
			level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.9F, 1.5F);
			if (master != null && master.level() == level && master.distanceToSqr(entity) <= 32.0D * 32.0D && Paviliun.config().adegan) {
				EpicMoments.send(level, EpicIds.EXTRA, BEAT_LEARNED, entity, master, at, level.getGameTime(), spec.grade().tier(), 0, 0.0F, 1, 0);
			}
		}
		VoiceDisciples.line(entity, "thanks", 200);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.belajar.done", entity.getName(), techniqueName)
					.withColor(0x9FF5DC));
			Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + "paviliun_belajar");
		}
	}

	/** A newly learned art takes the first empty combat socket (as a master's teaching does). */
	private static void slot(DiscipleEntity entity, TechniqueSpec spec) {
		String[] slots = DiscipleCultivation.combatSlots(entity.data());
		for (String s : slots) {
			if (s.equals(spec.id())) {
				return;
			}
		}
		for (int i = 0; i < slots.length; i++) {
			if (slots[i].isEmpty()) {
				DiscipleCultivation.setCombatSlot(entity, i, spec.id());
				return;
			}
		}
	}

	/** Closes the book: back to idle, the points kept for another time. */
	static void stop(DiscipleEntity entity) {
		WALKING.remove(entity.soulId());
		if (entity.activity() == SekteIds.ACT_STUDY) {
			entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	/** Book {@code id} left the shelf: whoever reads it stops. */
	static void bookRemoved(UUID owner, int id) {
		for (DiscipleEntity reader : readers(owner)) {
			CompoundTag record = reader.data().extra().getCompound(SekteIds.EXTRA_A).orElse(null);
			if (record != null && record.getIntOr(PaviliunData.BOOK, -1) == id) {
				stop(reader);
			}
		}
	}

	/** The owner's loaded readers. */
	static List<DiscipleEntity> readers(UUID owner) {
		List<DiscipleEntity> list = new ArrayList<>();
		for (DiscipleEntity d : Disciples.loaded(owner)) {
			if (d.activity() == SekteIds.ACT_STUDY) {
				list.add(d);
			}
		}
		return list;
	}

	/** The technique a loaded disciple is reading right now, or null. */
	static @Nullable String studying(UUID owner, UUID soul) {
		for (DiscipleEntity d : Disciples.loaded(owner)) {
			if (d.soulId().equals(soul)) {
				String id = PaviliunData.studying(d.data());
				return d.activity() == SekteIds.ACT_STUDY && !id.isEmpty() ? id : null;
			}
		}
		return null;
	}

	/** Progress 0-1 of a disciple's current study (0 when none). */
	static double progress(DiscipleData data) {
		CompoundTag record = data.extra().getCompound(SekteIds.EXTRA_A).orElse(null);
		String id = record == null ? "" : record.getStringOr(PaviliunData.SPEC, "");
		TechniqueSpec spec = id.isEmpty() ? null : TechniqueRegistry.byId(id);
		return spec == null ? 0.0D : Math.max(0.0D, Math.min(1.0D, record.getDoubleOr(PaviliunData.POINTS, 0.0D) / need(spec)));
	}

	private static double clamp(double v, double lo, double hi) {
		return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : 1.0D;
	}

	private BelajarMandiri() {
	}
}
