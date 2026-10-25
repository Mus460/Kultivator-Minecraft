package dev.kultivator.sekte.kebun;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.build.flow.LumbungOverflow;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.kebun.Kebun.Aula;
import dev.kultivator.util.Advancements;
import dev.kultivator.sekte.SekteSuara;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
 * The miners' shifts (DESIGN 36.2.10): 2 / 3 / 4 / 6 miners by depth, each holding a spot in the adit or the hidden
 * cavity with activity {@code ACT_MINE}; output is computed lazily like {@code Duties} (one roll of the depth table per
 * {@value #MINUTES_PER_ROLL} work minutes, times the miner's {@code workFactor} (P), the Treasury elder's
 * {@code officeFactor(TETUA_PUSAKA)} (E) and 1.1 for an Earth or Metal root) into the Lumbung. A shift is a world day;
 * when a day begins:
 * <ul>
 * <li>every worker's streak moves on ({@code extra.kerja}: W's garden and mine, and the old duty posts of
 * {@code Duties}, read only). Past {@code hariLibur} days in a row each further day is forced work: once per day
 * {@code SekteBus.DISCIPLE_MISTREATED(DIPAKSA_KERJA)}, a memory and a Catatan line. "Liburkan" gives a day off;</li>
 * <li>the finished shift reports {@code WORK_DONE};</li>
 * <li>from depth 5 a cave-in may hurt one miner ({@code runtuhPersen}): it is carried out of the world for a day
 * ({@code Halls.storeAway}, back at the mouth). Nobody dies and no block falls.</li>
 * </ul>
 */
public final class GiliranTambang {

	public static final double MINUTES_PER_ROLL = 4.0D;
	public static final double ROOT_FACTOR = 1.1D;
	/** At most this many rolls per miner per computation (a full banked day is about five). */
	private static final int MAX_ROLLS = 32;

	/** Works the mine by {@code elapsed} ticks. */
	static void work(Aula aula, long elapsed) {
		int level = TambangRoh.level(aula.owner());
		if (level <= 0) {
			return;
		}
		List<DiscipleEntity> miners = refresh(aula, level);
		if (elapsed <= 0L || miners.isEmpty()) {
			return;
		}
		CompoundTag slots = TambangRoh.slotsLive(aula.sec());
		int worst = LumbungOverflow.LUMBUNG;
		Component example = null;
		int total = 0;
		boolean core = false;
		DiscipleEntity coreFinder = null;
		for (DiscipleEntity miner : miners) {
			int slot = Kebun.slotOf(slots, miner.soulId());
			CompoundTag entry = slots.getCompoundOrEmpty(String.valueOf(slot));
			DiscipleData data = miner.data();
			double factor = SekteApi.hati().workFactor(data) * SekteApi.tetua().officeFactor(aula.owner(), Jabatan.TETUA_PUSAKA)
					* (Kebun.stoneRoot(data) ? ROOT_FACTOR : 1.0D);
			if (!Double.isFinite(factor) || factor <= 0.0D) {
				factor = 1.0D;
			}
			double progress = entry.getDoubleOr("prog", 0.0D) + elapsed / 1200.0D / MINUTES_PER_ROLL * Math.min(3.0D, factor);
			int rolls = (int) Math.min(MAX_ROLLS, Math.floor(progress));
			entry.putDouble("prog", progress - rolls);
			for (int i = 0; i < rolls; i++) {
				ItemStack found = TabelHasil.roll(level, aula.random());
				if (found.isEmpty()) {
					continue;
				}
				if (found.is(KebunItems.INTI_ROH_GUNUNG)) {
					core = true;
					coreFinder = miner;
				}
				int where = LumbungOverflow.store(aula.owner(), found.copy());
				if (where > worst) {
					worst = where;
					example = found.getHoverName();
				}
			}
			entry.putInt("today", entry.getIntOr("today", 0) + rolls);
			entry.putInt("total", entry.getIntOr("total", 0) + rolls);
			CompoundTag kerja = Kebun.kerja(data);
			kerja.putInt("total", kerja.getIntOr("total", 0) + rolls);
			total += rolls;
		}
		LumbungOverflow.tell(aula.player(), aula.owner(), worst, example);
		if (total <= 0) {
			return;
		}
		CompoundTag stats = Pertumbuhan.stats(aula.sec());
		boolean first = stats.getIntOr("gali", 0) == 0;
		stats.putInt("gali", stats.getIntOr("gali", 0) + total);
		Halls.setDirty();
		ServerPlayer owner = aula.player();
		if (owner != null) {
			Advancements.grant(owner, SekteIds.ADVANCEMENT_FOLDER + "kebun_tambang");
		}
		BlockPos mouth = TambangRoh.mouth(aula.owner());
		Vec3 at = mouth != null ? Vec3.atBottomCenterOf(mouth) : miners.get(0).position();
		KebunPayloads.fx(aula.level(), KebunPayloads.FX_HAUL, at, Math.min(64, total));
		aula.level().playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.6F, 0.8F);
		if (first) {
			Kebun.note(aula.owner(), Component.translatable("kultivator.sekte.kebun.log.first_ore", total), true);
		}
		if (core) {
			stats.putInt("inti", stats.getIntOr("inti", 0) + 1);
			Component name = coreFinder.getDisplayName();
			Component line = Component.translatable("kultivator.sekte.kebun.log.core", name, level);
			Kebun.note(aula.owner(), line, true);
			Kebun.tell(owner, line, ChatFormatting.GOLD);
			SekteBus.honored(aula.server(), aula.owner(), coreFinder.soulId(), "kebun.inti", 4);
			Ingatan.remember(aula.server(), coreFinder.data(), SekteIds.W + ".inti", null, 5, "kultivator.sekte.kebun.ingat.inti", "");
			aula.level().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.6F);
			KebunPayloads.fx(aula.level(), KebunPayloads.FX_INTI, at, 0xFFD27A);
			Pertumbuhan.scene(aula, Kebun.BEAT_CORE, coreFinder, at, level);
			SekteSuara.line(coreFinder, "ore_found", "cheer", 200);
		}
	}

	/**
	 * Checks the miner slots like {@code Duties} checks its posts: a soul called away or gone leaves its slot, slots past
	 * the depth's count are freed. Returns the loaded miners at work.
	 */
	static List<DiscipleEntity> refresh(Aula aula, int level) {
		int max = TabelHasil.slots(level);
		CompoundTag slots = TambangRoh.miners(aula.sec());
		List<DiscipleEntity> out = new ArrayList<>();
		for (String key : List.copyOf(slots.keySet())) {
			int slot;
			try {
				slot = Integer.parseInt(key);
			} catch (NumberFormatException exception) {
				slots.remove(key);
				continue;
			}
			UUID soul = Kebun.uuid(slots.getCompoundOrEmpty(key).getStringOr("soul", ""));
			if (soul == null || slot < 1 || slot > max) {
				release(aula, slot, false, false);
				continue;
			}
			DiscipleEntity disciple = Kebun.bySoul(aula.owner(), soul);
			if (disciple == null) {
				if (!Kebun.stillListed(aula.player(), soul)) {
					release(aula, slot, false, false);
				}
				continue;
			}
			if (disciple.isReleasing() || disciple.activity() != SekteIds.ACT_MINE) {
				Component name = disciple.getDisplayName();
				release(aula, slot, false, false);
				Kebun.note(aula.owner(), Component.translatable("kultivator.sekte.kebun.log.miner_left", name), false);
				continue;
			}
			out.add(disciple);
		}
		return out;
	}

	/** Puts {@code disciple} on miner slot {@code slot}; null on success, else why not. */
	static @Nullable Component assign(Aula aula, int slot, DiscipleEntity disciple) {
		int level = TambangRoh.level(aula.owner());
		if (level <= 0) {
			return Component.translatable("kultivator.sekte.kebun.mine.not_built");
		}
		if (slot < 1 || slot > TabelHasil.slots(level)) {
			return Component.translatable("kultivator.sekte.kebun.job.no_slot");
		}
		Component refused = Kebun.refuse(aula, disciple);
		if (refused != null) {
			return refused;
		}
		Kebun.releaseEverywhere(aula, disciple.soulId());
		CompoundTag slots = TambangRoh.slotsLive(aula.sec());
		if (slots.contains(String.valueOf(slot))) {
			release(aula, slot, false, false);
		}
		CompoundTag entry = new CompoundTag();
		entry.putString("soul", disciple.soulId().toString());
		slots.put(String.valueOf(slot), entry);
		Kebun.kerja(disciple.data()).putString("job", "tambang");
		Kebun.kerja(disciple.data()).remove("mandiri"); // a job the master gives is no longer self-chosen (KebunMandiri re-marks its own)
		BlockPos spot = Kebun.minerSpot(aula, slot);
		disciple.setActivity(SekteIds.ACT_MINE, spot);
		Halls.setDirty();
		BlockPos mouth = TambangRoh.mouth(aula.owner());
		Vec3 at = mouth != null ? Vec3.atBottomCenterOf(mouth) : Vec3.atBottomCenterOf(spot);
		aula.level().playSound(null, at.x, at.y, at.z, SoundEvents.TUFF_HIT, SoundSource.NEUTRAL, 0.8F, 0.8F);
		Kebun.note(aula.owner(), Component.translatable("kultivator.sekte.kebun.log.miner", disciple.getDisplayName(), slot), false);
		KebunPayloads.syncWorld(aula.owner());
		return null;
	}

	/**
	 * Frees miner slot {@code slot}; {@code rest} gives a day off (the streak ends), {@code quietly} skips the world sync.
	 */
	static void release(Aula aula, int slot, boolean rest, boolean quietly) {
		CompoundTag slots = TambangRoh.miners(aula.sec());
		UUID soul = Kebun.uuid(slots.getCompoundOrEmpty(String.valueOf(slot)).getStringOr("soul", ""));
		slots.remove(String.valueOf(slot));
		if (soul != null) {
			DiscipleEntity disciple = Kebun.bySoul(aula.owner(), soul);
			if (disciple != null) {
				CompoundTag kerja = Kebun.kerja(disciple.data());
				kerja.putString("job", "");
				if (rest) {
					kerja.putLong("rest_until", aula.now() + 24000L);
					kerja.putInt("streak", 0);
				}
				if (disciple.activity() == SekteIds.ACT_MINE) {
					disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				}
			}
		}
		Halls.setDirty();
		if (!quietly) {
			KebunPayloads.syncWorld(aula.owner());
		}
	}

	// ================================================================== the day

	/** A new world day {@code day} began: streaks, the shift report, the cave-in roll. */
	static void endOfDay(Aula aula, long day) {
		for (DiscipleEntity disciple : Disciples.loaded(aula.owner())) {
			if (disciple.role() == DiscipleEntity.Role.DISCIPLE) {
				streak(aula, disciple, day);
			}
		}
		CompoundTag slots = TambangRoh.miners(aula.sec());
		for (String key : slots.keySet()) {
			CompoundTag entry = slots.getCompoundOrEmpty(key);
			int today = entry.getIntOr("today", 0);
			UUID soul = Kebun.uuid(entry.getStringOr("soul", ""));
			if (soul != null && today > 0) {
				SekteBus.WORK_DONE.invoker().onWorkDone(aula.server(), aula.owner(), soul, "tambang", today);
			}
			entry.putInt("today", 0);
		}
		int level = TambangRoh.level(aula.owner());
		KebunConfig cfg = KebunConfig.get();
		if (level >= TambangRoh.DANGER_LEVEL && cfg.runtuhPersen > 0 && aula.random().nextInt(100) < cfg.runtuhPersen) {
			caveIn(aula);
		}
		Halls.setDirty();
	}

	/**
	 * Moves the disciple's work streak on for day {@code day}: worked = holds a W job or an old duty post. Past
	 * {@code hariLibur} days in a row the day counts as forced work (once a day).
	 */
	static void streak(Aula aula, DiscipleEntity disciple, long day) {
		DiscipleData data = disciple.data();
		CompoundTag kerja = Kebun.kerja(data);
		boolean worked = !kerja.getStringOr("job", "").isEmpty() || Duties.dutyOf(data) != null;
		long lastDay = kerja.getLongOr("last_day", -10L);
		int streak = kerja.getIntOr("streak", 0);
		if (worked) {
			streak = lastDay == day - 1L ? streak + 1 : lastDay == day ? streak : 1;
			kerja.putLong("last_day", day);
		} else if (day - lastDay >= 2L && streak != 0) {
			streak = 0;
		}
		kerja.putInt("streak", streak);
		KebunConfig cfg = KebunConfig.get();
		if (worked && streak > cfg.hariLibur && kerja.getLongOr("forced_day", -1L) != day) {
			kerja.putLong("forced_day", day);
			Component line = Component.translatable("kultivator.sekte.kebun.log.forced", disciple.getDisplayName(), streak);
			Kebun.note(aula.owner(), line, true);
			Kebun.tell(aula.player(), line, ChatFormatting.YELLOW);
			if (cfg.laporDipaksa) {
				SekteBus.mistreated(aula.server(), aula.owner(), disciple.soulId(), Perlakuan.DIPAKSA_KERJA, "kebun.kerja");
			}
			Ingatan.remember(aula.server(), data, SekteIds.W + ".dipaksa", null, -2, "kultivator.sekte.kebun.ingat.dipaksa", Integer.toString(streak));
			dev.kultivator.voice.VoiceDisciples.line(disciple, "tired", 1200);
		}
	}

	/** A cave-in: one miner at work is carried out hurt for a world day (never killed); dust and a rumble at the mouth. */
	static boolean caveIn(Aula aula) {
		List<DiscipleEntity> miners = refresh(aula, TambangRoh.level(aula.owner()));
		BlockPos mouth = TambangRoh.mouth(aula.owner());
		Vec3 at = mouth != null ? Vec3.atBottomCenterOf(mouth) : Vec3.atBottomCenterOf(aula.hall().pos());
		aula.level().playSound(null, at.x, at.y, at.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.4F, 0.5F);
		aula.level().playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_DIG, SoundSource.BLOCKS, 0.8F, 0.7F);
		KebunPayloads.fx(aula.level(), KebunPayloads.FX_CAVE_IN, at, 0);
		if (miners.isEmpty()) {
			Kebun.note(aula.owner(), Component.translatable("kultivator.sekte.kebun.log.cave_empty"), true);
			return false;
		}
		DiscipleEntity hurt = miners.get(aula.random().nextInt(miners.size()));
		Component name = hurt.getDisplayName();
		int slot = Kebun.slotOf(TambangRoh.miners(aula.sec()), hurt.soulId());
		if (slot > 0) {
			release(aula, slot, false, true);
		}
		Ingatan.remember(aula.server(), hurt.data(), SekteIds.W + ".runtuh", null, -3, "kultivator.sekte.kebun.ingat.runtuh", "");
		dev.kultivator.voice.VoiceDisciples.line(hurt, "injured", 200);
		CompoundTag info = new CompoundTag();
		info.putString("cause", "runtuh");
		Halls.storeAway(aula.owner(), hurt, aula.now() + 24000L, Kebun.REASON_HURT, info);
		Component line = Component.translatable("kultivator.sekte.kebun.log.cave_in", name);
		Kebun.note(aula.owner(), line, true);
		Kebun.tell(aula.player(), line, ChatFormatting.RED);
		KebunPayloads.syncWorld(aula.owner());
		return true;
	}

	private GiliranTambang() {
	}
}
