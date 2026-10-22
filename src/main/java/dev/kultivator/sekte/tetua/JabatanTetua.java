package dev.kultivator.sekte.tetua;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The offices themselves (DESIGN 36.2.6, contract C36.E1): who may hold which, appointing and relieving, the bonus an
 * office gives its domain module, the desk where its holder sits during office hours, and the once-a-second check that
 * keeps the hall's table and the holder's own {@code extra.jabatan} in step.
 * <p>
 * An office is held by a disciple of the existing ranks Tetua or Tetua Agung ({@code SectRanks}); a Murid Inti may serve as
 * a <b>Wakil</b> (deputy, half the bonus) so a small sect can use them. The Grand Elder's seat wants a Tetua Agung (a
 * Tetua deputises). One office per disciple, one holder per office. {@code officeFactor = 1 + 0.05 × clamp(realm − 2,
 * 0, 5)} (Foundation 1.00 … Immortal Ascension 1.25), a deputy {@code 1 + (f − 1) / 2}, vacant 1.00; after the first
 * succession every filled office gains +0.05 under the Ketua Generasi Kedua.
 * <p>
 * Sumber: the offices of a Chan monastery in the <i>Baizhang Qinggui</i> (Tang dynasty), the root of every abbey's roll of
 * duties; the Grand Elder presiding over the sect (<i>I Shall Seal the Heavens</i> ch. 27-31).
 */
final class JabatanTetua {

	/** Seal glyph and colour of each office (the screen, the FX, the log). */
	static final String[] SEALS = {"宗", "律", "經", "庫", "使", "藥"};
	static final int[] COLOURS = {0xFFD54A, 0xC8463C, 0x4A8CFF, 0xE0A84A, 0x3FC48A, 0x6CFF8A};

	/** Where each office keeps its desk: an anchor of the built plan and an offset from it. */
	private static final String[] ANCHORS = {"main_hall", "main_hall", "library", "treasury", "arrival", "post_garden"};
	private static final int[][] OFFSETS = {{2, 1}, {-2, 1}, {1, 2}, {-1, 2}, {2, -1}, {1, 1}};

	static final int ELIGIBLE_NONE = 0;
	static final int ELIGIBLE_DEPUTY = 1;
	static final int ELIGIBLE_FULL = 2;

	// ================================================================== reads

	static int seal(Jabatan office) {
		return office.ordinal();
	}

	static int colour(Jabatan office) {
		return COLOURS[office.ordinal()];
	}

	/** 2 full, 1 deputy (Wakil), 0 not eligible: by the disciple's sect rank. */
	static int eligible(DiscipleData data, Jabatan office) {
		int rank = SectRanks.rank(data);
		if (office == Jabatan.TETUA_AGUNG) {
			return rank >= SectRanks.TETUA_AGUNG ? ELIGIBLE_FULL : rank == SectRanks.TETUA ? ELIGIBLE_DEPUTY : ELIGIBLE_NONE;
		}
		return rank >= SectRanks.TETUA ? ELIGIBLE_FULL : rank == SectRanks.INTI ? ELIGIBLE_DEPUTY : ELIGIBLE_NONE;
	}

	/** E's published bonus (C36.E1). */
	static double factor(UUID owner, Jabatan office) {
		if (owner == null || office == null) {
			return 1.0D;
		}
		CompoundTag entry = TetuaData.office(owner, office);
		if (entry.getStringOr("soul", "").isEmpty()) {
			return 1.0D;
		}
		return factorOf(entry.getIntOr("realm", Realm.FOUNDATION.ordinal()), entry.getBooleanOr("wakil", false), Generasi.generation(owner) >= 2);
	}

	static double factorOf(int realmOrdinal, boolean deputy, boolean secondGeneration) {
		double f = 1.0D + 0.05D * Math.max(0, Math.min(5, realmOrdinal - 2));
		if (deputy) {
			f = 1.0D + (f - 1.0D) / 2.0D;
		}
		if (secondGeneration) {
			f += 0.05D;
		}
		return f;
	}

	/** "×1.15" */
	static String factorText(double factor) {
		return "×" + String.format(java.util.Locale.ROOT, "%.2f", factor);
	}

	static MutableComponent officeName(Jabatan office, boolean deputy) {
		MutableComponent name = Component.translatable(office.key());
		return deputy ? Component.translatable("kultivator.sekte.tetua.wakil", name) : name;
	}

	// ================================================================== appoint, relieve

	/** Appoints {@code entity} to {@code office}; the reason it cannot (a component) or null when done. */
	static @Nullable Component appoint(ServerPlayer master, DiscipleEntity entity, Jabatan office, boolean force) {
		UUID owner = master.getUUID();
		if (entity.role() != DiscipleEntity.Role.DISCIPLE || !owner.equals(entity.ownerId()) || entity.isReleasing()) {
			return Component.translatable("kultivator.sekte.tetua.why.not_yours");
		}
		if (Halls.hallOf(owner) == null) {
			return Component.translatable("kultivator.sekte.tetua.why.no_hall");
		}
		DiscipleData data = entity.data();
		UUID soul = entity.soulId();
		if (Generasi.isMaster(owner, soul)) {
			return Component.translatable("kultivator.sekte.tetua.why.is_master", entity.getName());
		}
		int eligible = eligible(data, office);
		if (eligible == ELIGIBLE_NONE && !force) {
			return Component.translatable(office == Jabatan.TETUA_AGUNG ? "kultivator.sekte.tetua.why.rank_agung" : "kultivator.sekte.tetua.why.rank",
					entity.getName(), SectRanks.rankName(office == Jabatan.TETUA_AGUNG ? SectRanks.TETUA : SectRanks.INTI));
		}
		boolean deputy = eligible != ELIGIBLE_FULL;
		MinecraftServer server = master.level().getServer();
		long day = Ingatan.today(server);
		// the disciple's old office (it moves) and the office's old holder (replaced)
		Jabatan old = TetuaData.officeOf(owner, soul);
		if (old == office) {
			return Component.translatable("kultivator.sekte.tetua.why.already", entity.getName(), officeName(office, deputy));
		}
		if (old != null) {
			TetuaData.clearOffice(owner, old);
			SekteBus.ELDER_APPOINTED.invoker().onElderAppointed(server, owner, soul, null);
		}
		UUID previous = TetuaData.holder(owner, office);
		if (previous != null && !previous.equals(soul)) {
			release(server, owner, previous, office, "kultivator.sekte.tetua.memory.replaced", -3, entity.getName());
		}
		TetuaData.setOffice(owner, office, soul, entity.getName(), data.realm().ordinal(), deputy, day, eligible == ELIGIBLE_NONE);
		CompoundTag mine = TetuaData.discipleSection(data);
		mine.putString(TetuaData.D_OFFICE, office.id());
		mine.putBoolean(TetuaData.D_DEPUTY, deputy);
		mine.putLong(TetuaData.D_SINCE, day);
		Halls.setDirty();
		Ingatan.remember(server, data, "tetua.diangkat", owner, 6, "kultivator.sekte.tetua.memory.appointed." + office.id(), "");
		SekteBus.honored(server, owner, soul, "tetua.diangkat", 4);
		SekteBus.ELDER_APPOINTED.invoker().onElderAppointed(server, owner, soul, office);
		Component line = Component.translatable("kultivator.sekte.tetua.log.appointed", entity.getName(), officeName(office, deputy),
				factorText(factor(owner, office)));
		SekteLog.log(server, owner, SekteIds.E, line);
		Msg.chat(master, Component.translatable("kultivator.sekte.tetua.appointed", entity.getName(), officeName(office, deputy),
				factorText(factor(owner, office))).withColor(colour(office)));
		if (deputy) {
			Msg.chat(master, Component.translatable("kultivator.sekte.tetua.appointed.deputy", entity.getName()).withStyle(ChatFormatting.GRAY));
		}
		celebrate(master, entity, office);
		Advancements.grant(master, "sekte/tetua_jabatan");
		return null;
	}

	/** The master relieves the holder of {@code office}; false when it was vacant. */
	static boolean dismiss(MinecraftServer server, UUID owner, Jabatan office, @Nullable ServerPlayer master) {
		UUID holder = TetuaData.holder(owner, office);
		if (holder == null) {
			return false;
		}
		Component name = TetuaData.name(TetuaData.office(owner, office), "name");
		release(server, owner, holder, office, "kultivator.sekte.tetua.memory.dismissed", -4, null);
		SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.log.dismissed", name, Component.translatable(office.key())));
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.tetua.dismissed", name, Component.translatable(office.key()))
					.withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	/** Empties the office, tells the bus and (when loaded) clears the disciple's own mark and its desk. */
	static void release(MinecraftServer server, UUID owner, UUID soul, Jabatan office, @Nullable String memoryKey, int weight,
			@Nullable Component successor) {
		TetuaData.clearOffice(owner, office);
		DiscipleEntity entity = loaded(owner, soul);
		if (entity != null) {
			clearMark(entity);
			if (memoryKey != null) {
				Ingatan.remember(server, entity.data(), "tetua.dicopot", owner, weight, memoryKey,
						successor == null ? office.id() : successor.getString());
			}
		}
		SekteBus.ELDER_APPOINTED.invoker().onElderAppointed(server, owner, soul, null);
	}

	static void clearMark(DiscipleEntity entity) {
		CompoundTag mine = entity.data().extra().getCompound(SekteIds.EXTRA_E).orElse(null);
		if (mine != null) {
			mine.remove(TetuaData.D_OFFICE);
			mine.remove(TetuaData.D_DEPUTY);
			mine.remove(TetuaData.D_SINCE);
		}
		if (entity.activity() == SekteIds.ACT_OFFICE && !Tetua.busy(entity)) {
			entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	private static void celebrate(ServerPlayer master, DiscipleEntity entity, Jabatan office) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 pos = entity.position();
		int rgb = colour(office);
		Fx.ring(level, pos.add(0.0D, 0.1D, 0.0D), rgb, 1.6F);
		Fx.burst(level, pos.add(0.0D, 1.2D, 0.0D), rgb, 18, 0.9F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.9F, 0.9F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0F, 0.8F);
		VoiceDisciples.line(entity, "loyal", 600);
		if (master.level() == level && master.distanceToSqr(entity) <= 32.0D * 32.0D) {
			// the small stamp "Diangkat Tetua" (beat 152) when the master stands near
			EpicHooks.extra(level, Tetua.BEAT_APPOINT, master, entity, pos, office.ordinal(), 0);
		}
	}

	// ================================================================== the once-a-second check

	/**
	 * The holder's half of the table: its mark must match the hall's entry (else the hall gave the office to someone
	 * else meanwhile), its rank must still allow it (else it is relieved), and the realm snapshot and deputy flag the
	 * bonus reads are refreshed. Returns the office it still holds, or null.
	 */
	static @Nullable Jabatan refresh(MinecraftServer server, UUID owner, DiscipleEntity entity) {
		DiscipleData data = entity.data();
		Jabatan mark = TetuaData.discipleOffice(data);
		Jabatan table = TetuaData.officeOf(owner, entity.soulId());
		if (mark == null && table == null) {
			return null;
		}
		if (table == null || mark != table) {
			if (table == null) {
				clearMark(entity);
				return null;
			}
			// a table entry without the mark (a restore, a copy): the table wins
			CompoundTag mine = TetuaData.discipleSection(data);
			mine.putString(TetuaData.D_OFFICE, table.id());
		}
		int eligible = eligible(data, table);
		if (eligible == ELIGIBLE_NONE && !TetuaData.office(owner, table).getBooleanOr("uji", false)) {
			Component name = entity.getName();
			release(server, owner, entity.soulId(), table, "kultivator.sekte.tetua.memory.lost_rank", -2, null);
			SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.log.lost_rank", name, Component.translatable(table.key())));
			return null;
		}
		CompoundTag entry = TetuaData.child(TetuaData.child(TetuaData.hall(owner), TetuaData.OFFICES), table.id());
		boolean deputy = eligible != ELIGIBLE_FULL;
		int realm = data.realm().ordinal();
		if (entry.getIntOr("realm", -1) != realm || entry.getBooleanOr("wakil", false) != deputy) {
			entry.putInt("realm", realm);
			entry.putBoolean("wakil", deputy);
			TetuaData.putName(entry, "name", entity.getName());
			TetuaData.discipleSection(data).putBoolean(TetuaData.D_DEPUTY, deputy);
			TetuaData.dirty();
		}
		return table;
	}

	// ================================================================== the desk

	/** Where the holder of {@code office} sits: its anchor of the built plan, else a ring around the stele. */
	static @Nullable BlockPos desk(UUID owner, Jabatan office, Level level) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !level.dimension().equals(hall.dim())) {
			return null;
		}
		int i = office.ordinal();
		BlockPos anchor = SectBuild.anchor(owner, ANCHORS[i]);
		BlockPos base;
		if (anchor != null) {
			base = anchor.offset(OFFSETS[i][0], 0, OFFSETS[i][1]);
		} else {
			double angle = Math.PI * 2.0D * i / Jabatan.values().length + Math.PI / 6.0D;
			base = hall.pos().offset((int) Math.round(Math.cos(angle) * 4.0D), 0, (int) Math.round(Math.sin(angle) * 4.0D));
		}
		BlockPos spot = standable(level, base);
		if (spot == null || !Halls.inTerritory(owner, level, Vec3.atBottomCenterOf(spot))) {
			return null;
		}
		return spot;
	}

	/** A spot near {@code pos} with air for a body and something under the feet (a read, never a block change). */
	static @Nullable BlockPos standable(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
		for (int dy : new int[]{0, 1, -1, 2, -2}) {
			for (int[] d : around) {
				BlockPos p = pos.offset(d[0], dy, d[1]);
				if (level.isEmptyBlock(p) && level.isEmptyBlock(p.above()) && !level.isEmptyBlock(p.below())) {
					return p;
				}
			}
		}
		return null;
	}

	static @Nullable DiscipleEntity loaded(UUID owner, UUID soul) {
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.soulId().equals(soul)) {
				return disciple;
			}
		}
		return null;
	}

	private JabatanTetua() {
	}
}
