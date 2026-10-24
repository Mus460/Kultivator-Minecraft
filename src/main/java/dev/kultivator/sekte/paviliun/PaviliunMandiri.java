package dev.kultivator.sekte.paviliun;

import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bridge of Murid Mandiri (DESIGN 39.5.5, module 39B) into the Paviliun Kitab of module A: the planner's BELAJAR goes
 * through here instead of opening A's package-private study.
 * <ul>
 *   <li><b>Rack</b> ({@link #mintaBaca}): the disciple asks for a seat now; A's own machine does the rest
 *   ({@code BelajarMandiri}: the seat, {@code ACT_STUDY}, the study points, one technique a day, the memory, the bus).
 *   Unlike A's idle roll it is not left to chance: the planner already chose to study.</li>
 *   <li><b>Bag</b> ({@link #bacaTas}, {@link #langkahTas}): a jade slip or technique book the disciple owns (bought with
 *   its PK at the Paviliun Yue Lu, or loot) is read where it sits, the slip pressed to the forehead (pose
 *   {@code ACT_MERENUNG} 53), with A's study rate and need ({@code BelajarMandiri.rate}, {@code need}), A's gates
 *   ({@code RakKitab.blocker}; a legacy art asks the teach hooks without the master, see
 *   {@code DiscipleCultivation.teachBlockerSelf}) and A's ending ({@code BelajarMandiri.learn}: one a day, memory, bus,
 *   honour). A loyal (Setia) reader then lays the slip on the rack for the sect when there is room.</li>
 * </ul>
 * Its own bag-reading state lives in {@code extra.mandiri_tumbuh.baca} (never in A's record). Server side; no block.
 */
public final class PaviliunMandiri {

	/** {@code DiscipleData.extra()} section of module 39B (outside the 39A schema). */
	public static final String SECTION = "mandiri_tumbuh";
	private static final String BACA = "baca";
	private static final String SPEC = "spec";
	private static final String POINTS = "pts";
	/** True while the disciple sits reading from its bag (the record itself stays for another day). */
	private static final String DUDUK = "duduk";
	/** Pose of reading from the bag (39.4.8: contemplating, the slip at the forehead). */
	public static final int ACT_BACA_TAS = 53;

	// ================================================================== rack

	/** True when the hall has a built pavilion ({@code library} anchor) and something on its rack. */
	public static boolean adaRak(UUID owner) {
		return owner != null && Paviliun.active() && SectBuild.anchor(owner, "library") != null && !PaviliunData.rack(owner).isEmpty();
	}

	/** True when the disciple may still learn today and the rack holds something it may study now. */
	public static boolean bisaBaca(DiscipleEntity entity) {
		UUID owner = entity.ownerId();
		if (owner == null || !adaRak(owner) || doneToday(entity)) {
			return false;
		}
		MinecraftServer server = entity.level().getServer();
		ServerPlayer master = server == null ? null : server.getPlayerList().getPlayer(owner);
		return RakKitab.choose(entity, owner, master) != null;
	}

	/**
	 * The planner sends the disciple to read from the rack now: a free seat around the {@code library} anchor (at most
	 * {@code pembacaMaks} readers), the book it was reading before or A's own pick. True when it is on its way.
	 */
	public static boolean mintaBaca(DiscipleEntity entity) {
		UUID owner = entity.ownerId();
		if (owner == null || !(entity.level() instanceof ServerLevel level) || !adaRak(owner) || doneToday(entity)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		List<DiscipleEntity> readers = BelajarMandiri.readers(owner);
		if (readers.size() >= Paviliun.config().pembacaMaks) {
			return false;
		}
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		RakKitab.Pick pick = RakKitab.choose(entity, owner, master);
		CompoundTag record = entity.data().extra().getCompound(SekteIds.EXTRA_A).orElse(null);
		if (record != null) {
			String id = record.getStringOr(PaviliunData.SPEC, "");
			int book = record.getIntOr(PaviliunData.BOOK, -1);
			TechniqueSpec before = id.isEmpty() ? null : TechniqueRegistry.byId(id);
			if (before != null && RakKitab.holds(owner, book, id) && RakKitab.blocker(entity, before, master) == null) {
				pick = new RakKitab.Pick(before, book);
			}
		}
		if (pick == null) {
			return false;
		}
		BlockPos library = SectBuild.anchor(owner, "library");
		List<BlockPos> taken = new ArrayList<>();
		for (DiscipleEntity reader : readers) {
			if (reader.activitySpot() != null) {
				taken.add(reader.activitySpot());
			}
		}
		BlockPos seat = library == null ? null : Tempat.seat(level, library, taken);
		if (seat == null) {
			return false;
		}
		BelajarMandiri.start(server, owner, entity, pick, seat);
		return true;
	}

	/**
	 * One past day of the off-screen catch-up (DESIGN 39.4.7 step 4): the disciple spent about {@code jam} hours at the
	 * rack; the chance to finish one art is the points it would have gathered ({@code BelajarMandiri.rate} per second,
	 * 50 seconds a world hour) over the art's need, at most one art per catch-up (A's one-a-day rule). A's own ending
	 * runs ({@code BelajarMandiri.learn}: memory, bus, honour, the master's line). Returns the learned art, or null.
	 */
	public static @Nullable TechniqueSpec belajarKejar(DiscipleEntity entity, double jam, net.minecraft.util.RandomSource random) {
		UUID owner = entity.ownerId();
		MinecraftServer server = entity.level().getServer();
		if (owner == null || server == null || jam <= 0.0D || !adaRak(owner) || doneToday(entity) || SectRanks.slotsFull(entity.data())) {
			return null;
		}
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		RakKitab.Pick pick = RakKitab.choose(entity, owner, master);
		if (pick == null) {
			return null;
		}
		double points = BelajarMandiri.rate(owner, entity.data()) * 50.0D * jam;
		double chance = Math.min(0.9D, points / Math.max(1.0D, BelajarMandiri.need(pick.spec())));
		if (random.nextDouble() >= chance) {
			return null;
		}
		BelajarMandiri.learn(server, owner, entity, pick.spec(), master);
		return entity.data().knows(pick.spec().id()) ? pick.spec() : null;
	}

	/** True while the disciple sits at the rack ({@code ACT_STUDY}). */
	public static boolean sedangBaca(DiscipleEntity entity) {
		return entity.activity() == SekteIds.ACT_STUDY;
	}

	/** The technique being read at the rack, or "". */
	public static String bacaanRak(DiscipleData data) {
		return PaviliunData.studying(data);
	}

	/** Progress 0-1 of the rack study (A's points). */
	public static double kemajuanRak(DiscipleData data) {
		return BelajarMandiri.progress(data);
	}

	/** Ends a reading of either kind (the rack points stay, the bag points stay). */
	public static void berhenti(DiscipleEntity entity) {
		if (entity.activity() == SekteIds.ACT_STUDY) {
			BelajarMandiri.stop(entity);
		} else if (sedangBacaTas(entity)) {
			entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		lepasDuduk(entity.data());
	}

	/**
	 * True while the disciple sits reading from its bag right now (pose 53 and the session flag); the bag record with its
	 * points stays after a stop, so the shared pose of contemplation is told apart by this, not by {@link #bacaanTas}.
	 */
	public static boolean sedangBacaTas(DiscipleEntity entity) {
		return entity.activity() == ACT_BACA_TAS
				&& entity.data().extra().getCompoundOrEmpty(SECTION).getCompoundOrEmpty(BACA).getBooleanOr(DUDUK, false);
	}

	/** Clears the "sitting with a slip" flag (a contemplation takes the same pose over). */
	public static void lepasDuduk(DiscipleData data) {
		CompoundTag baca = data.extra().getCompoundOrEmpty(SECTION).getCompoundOrEmpty(BACA);
		if (baca.getBooleanOr(DUDUK, false)) {
			baca(data).putBoolean(DUDUK, false);
		}
	}

	/** True when the disciple already learned a technique today (A's one-a-day rule, rack and bag together). */
	public static boolean doneToday(DiscipleEntity entity) {
		MinecraftServer server = entity.level().getServer();
		CompoundTag record = entity.data().extra().getCompound(SekteIds.EXTRA_A).orElse(null);
		return record != null && server != null && record.getLongOr(PaviliunData.DAY_DONE, -1L) == Ingatan.today(server);
	}

	// ================================================================== bag

	/** The technique a slip or book in the bag lets the disciple learn now (its own Dao first, lowest grade), or null. */
	public static @Nullable TechniqueSpec pilihTas(DiscipleEntity entity) {
		if (entity.ownerId() == null || doneToday(entity) || SectRanks.slotsFull(entity.data())) {
			return null;
		}
		DiscipleData data = entity.data();
		boolean ambitious = SekteApi.hati().has(data, Sifat.AMBISIUS);
		TechniqueSpec best = null;
		int bestScore = Integer.MIN_VALUE;
		for (ItemStack stack : MandiriApi.tas(data, entity.level().registryAccess())) {
			for (String id : RakKitab.specsOf(stack)) {
				TechniqueSpec spec = TechniqueRegistry.byId(id);
				if (spec == null || !bolehDariTas(entity, spec)) {
					continue;
				}
				int grade = spec.grade().tier();
				int score = (spec.dao() != null && spec.dao() == data.dao() ? 100 : 0) + (ambitious ? grade * 10 : -grade * 10);
				if (score > bestScore) {
					bestScore = score;
					best = spec;
				}
			}
		}
		return best;
	}

	/** A's rack gates for the bag; a legacy art's "needs the master" is answered by the hooks asked without one. */
	private static boolean bolehDariTas(DiscipleEntity entity, TechniqueSpec spec) {
		String why = RakKitab.blocker(entity, spec, null);
		return why == null || "master".equals(why) && DiscipleCultivation.teachBlockerSelf(entity, spec) == null;
	}

	/**
	 * Starts reading {@code spec} from the bag at {@code seat} (the disciple's own spot when null): the pose, the points
	 * carried over when it is the same technique as before. True when it began.
	 */
	public static boolean bacaTas(DiscipleEntity entity, TechniqueSpec spec, @Nullable BlockPos seat) {
		if (spec == null || entity.ownerId() == null || !bolehDariTas(entity, spec) || !punyaTas(entity, spec.id())) {
			return false;
		}
		CompoundTag baca = baca(entity.data());
		if (!spec.id().equals(baca.getStringOr(SPEC, ""))) {
			baca.putDouble(POINTS, 0.0D);
		}
		baca.putString(SPEC, spec.id());
		baca.putBoolean(DUDUK, true);
		entity.setActivity(ACT_BACA_TAS, seat == null ? entity.blockPosition() : seat);
		if (entity.level() instanceof ServerLevel level) {
			level.playSound(null, entity.getX(), entity.getY() + 1.0D, entity.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.4F, 1.6F);
		}
		return true;
	}

	/** The technique being read from the bag, or "". */
	public static String bacaanTas(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(SECTION).getCompoundOrEmpty(BACA).getStringOr(SPEC, "");
	}

	/** Progress 0-1 of the bag study. */
	public static double kemajuanTas(DiscipleData data) {
		CompoundTag baca = data.extra().getCompoundOrEmpty(SECTION).getCompoundOrEmpty(BACA);
		TechniqueSpec spec = TechniqueRegistry.byId(baca.getStringOr(SPEC, ""));
		return spec == null ? 0.0D : Math.max(0.0D, Math.min(1.0D, baca.getDoubleOr(POINTS, 0.0D) / BelajarMandiri.need(spec)));
	}

	/**
	 * One second of reading from the bag (called once a second while the pose holds): A's study rate while seated at the
	 * spot. Returns 1 when the technique was learned now, 0 while it goes on, -1 when it stopped (slip gone, gate closed,
	 * pose taken over).
	 */
	public static int langkahTas(DiscipleEntity entity) {
		UUID owner = entity.ownerId();
		DiscipleData data = entity.data();
		String id = bacaanTas(data);
		TechniqueSpec spec = id.isEmpty() ? null : TechniqueRegistry.byId(id);
		if (owner == null || spec == null || entity.activity() != ACT_BACA_TAS || !punyaTas(entity, id) || !bolehDariTas(entity, spec)
				|| !(entity.level() instanceof ServerLevel level)) {
			lepasDuduk(data);
			return -1;
		}
		BlockPos spot = entity.activitySpot();
		if (spot != null && entity.distanceToSqr(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D) > 2.25D) {
			return 0;
		}
		CompoundTag baca = baca(data);
		double points = baca.getDoubleOr(POINTS, 0.0D) + BelajarMandiri.rate(owner, data);
		if (points < BelajarMandiri.need(spec)) {
			baca.putDouble(POINTS, points);
			if (entity.tickCount % 160 < 20) {
				Vec3 at = entity.position().add(0.0D, 1.7D, 0.0D);
				Fx.burst(level, at, spec.dao() == null ? 0x9FF5DC : spec.dao().color(), 3, 0.25F);
			}
			return 0;
		}
		MinecraftServer server = level.getServer();
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		// A's learn() clears the rack record's study; keep the rack book's progress for another day.
		CompoundTag record = PaviliunData.study(data);
		String rackSpec = record.getStringOr(PaviliunData.SPEC, "");
		double rackPoints = record.getDoubleOr(PaviliunData.POINTS, 0.0D);
		int rackBook = record.getIntOr(PaviliunData.BOOK, -1);
		data.extraSection(SECTION).remove(BACA);
		BelajarMandiri.learn(server, owner, entity, spec, master);
		if (!rackSpec.isEmpty()) {
			CompoundTag again = PaviliunData.study(data);
			again.putString(PaviliunData.SPEC, rackSpec);
			again.putDouble(PaviliunData.POINTS, rackPoints);
			again.putInt(PaviliunData.BOOK, rackBook);
		}
		if (SekteApi.hati().has(data, Sifat.SETIA)) {
			setorKeRak(entity, owner, id);
		}
		return 1;
	}

	/** True when a stack in the bag teaches {@code specId}. */
	private static boolean punyaTas(DiscipleEntity entity, String specId) {
		for (ItemStack stack : MandiriApi.tas(entity.data(), entity.level().registryAccess())) {
			if (RakKitab.specsOf(stack).contains(specId)) {
				return true;
			}
		}
		return false;
	}

	/** A loyal reader lays the slip it learned from on the sect's rack (room permitting): out of its bag, onto the shelf. */
	private static void setorKeRak(DiscipleEntity entity, UUID owner, String specId) {
		if (Halls.hallOf(owner) == null || PaviliunData.rack(owner).size() >= RakKitab.capacity(owner)
				|| !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack slip = MandiriApi.ambilTas(entity.data(), level.registryAccess(), stack -> RakKitab.specsOf(stack).contains(specId), 1);
		if (slip.isEmpty()) {
			return;
		}
		List<String> specs = RakKitab.specsOf(slip);
		CompoundTag item = new CompoundTag();
		item.store("item", ItemStack.CODEC, RakKitab.ops(level.getServer()), slip);
		String kind = slip.is(dev.kultivator.technique.source.JadeSlips.JADE_SLIP) ? RakKitab.SLIP : RakKitab.BOOK;
		PaviliunData.addBook(owner, kind, RakKitab.title(specs), specs, item, entity.getName().getString(), Ingatan.today(level.getServer()));
		level.playSound(null, entity.getX(), entity.getY() + 1.0D, entity.getZ(), SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, SoundSource.NEUTRAL, 0.7F, 1.0F);
	}

	private static CompoundTag baca(DiscipleData data) {
		CompoundTag section = data.extraSection(SECTION);
		CompoundTag baca = section.getCompoundOrEmpty(BACA);
		if (!section.contains(BACA)) {
			section.put(BACA, baca);
		}
		return baca;
	}

	private PaviliunMandiri() {
	}
}
