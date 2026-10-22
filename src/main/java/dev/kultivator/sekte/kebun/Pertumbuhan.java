package dev.kultivator.sekte.kebun;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.flow.LumbungOverflow;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.kebun.Kebun.Aula;
import dev.kultivator.sekte.kebun.TabelHasil.Benih;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.util.Advancements;
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

import java.util.List;
import java.util.UUID;

/**
 * The spirit-herb beds at work (DESIGN 36.2.10): sowing from the Lumbung or the hand, growth in four stages over
 * {@code 1 + mutu} world days (half speed without a gardener), the harvest into the Lumbung, the gardeners
 * ({@code ACT_TEND}, one per two beds) and the rare bloom.
 * <p>
 * Yield per bed: {@code hasilPetak + hall level / 3}, times the gardener's {@code workFactor} (module P), times the Medicine
 * elder's {@code officeFactor(TETUA_OBAT)} (module E), times 1.1 for a gardener with a Wood or Water root. A ripe bed with
 * its gardener present is harvested and (by default) sown again with one of its own herbs; without one it waits for the
 * master. The seed's age carries over, capped ({@link TabelHasil#SEED_AGE_CAP}), so replanting never grows millennium
 * herbs. The old garden duty (post 2 of {@code Duties}) stays as it was.
 * <p>
 * Sumber: the Hundred Medicine Garden of the Huang Feng Valley, tended by a disciple (<i>A Record of a Mortal's Journey</i>
 * ch. 150-151).
 */
public final class Pertumbuhan {

	/** Growth without a gardener. */
	public static final double UNTENDED_RATE = 0.5D;
	public static final double ROOT_FACTOR = 1.1D;

	/** Grows every bed by {@code elapsed} ticks and lets present gardeners harvest what ripened. */
	static void grow(Aula aula, long elapsed) {
		int plots = Kebun.plotCount(aula.owner());
		DiscipleEntity[] gardeners = refreshTenders(aula, plots);
		boolean[] active = new boolean[gardeners.length];
		CompoundTag tenders = aula.sec().getCompoundOrEmpty("rawat");
		for (int k = 1; k < gardeners.length; k++) {
			active[k] = tenders.contains(String.valueOf(k));
		}
		for (int i = 0; i < plots; i++) {
			PetakHerba bed = PetakHerba.of(aula.sec(), i);
			if (bed.empty()) {
				continue;
			}
			int slot = i / 2 + 1;
			boolean tended = slot < active.length && active[slot];
			bed.grow(elapsed, tended ? 1.0D : UNTENDED_RATE, tended);
			DiscipleEntity gardener = slot < gardeners.length ? gardeners[slot] : null;
			if (bed.ripe() && gardener != null) {
				harvest(aula, i, gardener, null);
			}
		}
	}

	/**
	 * Checks the gardener slots: a soul called away (another activity), released for good or past the slot count leaves its
	 * slot. Returns the loaded gardener of each slot (index 1..), null when away or empty.
	 */
	static DiscipleEntity[] refreshTenders(Aula aula, int plots) {
		int slots = Kebun.tenderSlots(plots);
		DiscipleEntity[] out = new DiscipleEntity[Math.max(1, slots + 1)];
		CompoundTag tenders = aula.sec().getCompoundOrEmpty("rawat");
		for (String key : List.copyOf(tenders.keySet())) {
			int slot;
			try {
				slot = Integer.parseInt(key);
			} catch (NumberFormatException exception) {
				tenders.remove(key);
				continue;
			}
			UUID soul = Kebun.uuid(tenders.getCompoundOrEmpty(key).getStringOr("soul", ""));
			if (soul == null || slot < 1 || slot > slots) {
				release(aula, slot, false);
				continue;
			}
			DiscipleEntity disciple = Kebun.bySoul(aula.owner(), soul);
			if (disciple == null) {
				if (!Kebun.stillListed(aula.player(), soul)) {
					release(aula, slot, false);
				}
				continue;
			}
			if (disciple.isReleasing() || disciple.activity() != SekteIds.ACT_TEND) {
				Component name = disciple.getDisplayName();
				release(aula, slot, false);
				Kebun.note(aula.owner(), Component.translatable("kultivator.sekte.kebun.log.tender_left", name), false);
				continue;
			}
			out[slot] = disciple;
		}
		return out;
	}

	// ================================================================== sowing

	/** Sows bed {@code index} with {@code seed} taken from the Lumbung, else from the player's inventory. */
	static @Nullable Component plant(Aula aula, int index, Benih seed, @Nullable ServerPlayer player) {
		if (index < 0 || index >= Kebun.plotCount(aula.owner())) {
			return Component.translatable("kultivator.sekte.kebun.bed.none");
		}
		PetakHerba bed = PetakHerba.of(aula.sec(), index);
		if (!bed.empty()) {
			return Component.translatable("kultivator.sekte.kebun.bed.busy", index + 1);
		}
		ItemStack taken = ItemStack.EMPTY;
		List<ItemStack> fromStore = Storehouse.take(aula.owner(), seed::matches, 1);
		if (!fromStore.isEmpty()) {
			taken = fromStore.get(0);
		} else if (player != null) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (seed.matches(stack)) {
					taken = stack.split(1);
					player.getInventory().setChanged();
					break;
				}
			}
		}
		if (taken.isEmpty()) {
			return Component.translatable("kultivator.sekte.kebun.bed.no_seed", seed.label());
		}
		sow(aula, index, seed, taken, player);
		return null;
	}

	/** Sows from the stack in the player's hand (a click on a bare bed with a seed). */
	static void plantFromHand(Aula aula, int index, ServerPlayer player, ItemStack held, Benih seed) {
		ItemStack taken = held.split(1);
		player.getInventory().setChanged();
		sow(aula, index, seed, taken, player);
	}

	private static void sow(Aula aula, int index, Benih seed, ItemStack taken, @Nullable ServerPlayer player) {
		int age = seed.kind() == null ? SpiritHerbs.FOUNDATION_HERB_AGE : Math.max(1, SpiritHerbs.age(taken));
		PetakHerba bed = PetakHerba.of(aula.sec(), index);
		bed.plant(seed, age);
		Halls.setDirty();
		BlockPos at = EngineApi.get().anchor(aula.owner(), KebunKomponen.ANCHOR_PLOT + (index + 1));
		if (at != null) {
			Vec3 pos = Vec3.atBottomCenterOf(at);
			aula.level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.9F, 0.9F);
			KebunPayloads.fx(aula.level(), KebunPayloads.FX_PLANT, pos, seed.color());
		}
		Kebun.tell(player, Component.translatable("kultivator.sekte.kebun.bed.sown", index + 1, seed.label(),
				1 + seed.mutu()), ChatFormatting.GREEN);
		KebunPayloads.syncWorld(aula.owner());
	}

	/** Pulls bed {@code index} up (the herb is lost unless it is ripe: then it is harvested). */
	static @Nullable Component uproot(Aula aula, int index, @Nullable ServerPlayer player) {
		PetakHerba bed = PetakHerba.of(aula.sec(), index);
		if (bed.empty()) {
			return Component.translatable("kultivator.sekte.kebun.bed.bare", index + 1);
		}
		if (bed.ripe()) {
			harvest(aula, index, null, player);
			return null;
		}
		bed.clear();
		Halls.setDirty();
		Kebun.tell(player, Component.translatable("kultivator.sekte.kebun.bed.cleared", index + 1), ChatFormatting.GRAY);
		KebunPayloads.syncWorld(aula.owner());
		return null;
	}

	// ================================================================== harvest

	/** Harvests ripe bed {@code index} into the Lumbung ({@code gardener} the one who did it, may be null). */
	static void harvest(Aula aula, int index, @Nullable DiscipleEntity gardener, @Nullable ServerPlayer byPlayer) {
		PetakHerba bed = PetakHerba.of(aula.sec(), index);
		Benih herb = bed.herb();
		if (herb == null || !bed.ripe()) {
			return;
		}
		int count = yieldOf(aula, gardener);
		int age = TabelHasil.harvestAge(bed.age(), 1 + herb.mutu(), aula.random());
		boolean replant = bed.auto() && count >= 2;
		if (replant) {
			count--;
			bed.plant(herb, age);
		} else {
			bed.clear();
		}
		int worst = LumbungOverflow.LUMBUNG;
		Component example = null;
		ItemStack crop = herb.harvest(age, count);
		int where = LumbungOverflow.store(aula.owner(), crop.copy());
		if (where > worst) {
			worst = where;
			example = crop.getHoverName();
		}
		boolean bloom = herb.kind() != null && aula.random().nextInt(100) < KebunConfig.get().mekarPersen;
		if (bloom) {
			ItemStack rare = herb.harvest(Math.min(999, age * 3), 1);
			int rareWhere = LumbungOverflow.store(aula.owner(), rare.copy());
			if (rareWhere > worst) {
				worst = rareWhere;
				example = rare.getHoverName();
			}
		}
		LumbungOverflow.tell(aula.player(), aula.owner(), worst, example);
		CompoundTag stats = stats(aula.sec());
		boolean first = stats.getIntOr("panen", 0) == 0;
		stats.putInt("panen", stats.getIntOr("panen", 0) + 1);
		stats.putInt("herba", stats.getIntOr("herba", 0) + count + (bloom ? 1 : 0));
		Halls.setDirty();
		BlockPos at = EngineApi.get().anchor(aula.owner(), KebunKomponen.ANCHOR_PLOT + (index + 1));
		Vec3 pos = at != null ? Vec3.atBottomCenterOf(at) : gardener != null ? gardener.position() : Vec3.atBottomCenterOf(aula.hall().pos());
		aula.level().playSound(null, pos.x, pos.y, pos.z, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 0.8F, 1.2F);
		KebunPayloads.fx(aula.level(), bloom ? KebunPayloads.FX_BLOOM : KebunPayloads.FX_HARVEST, pos, herb.color());
		ServerPlayer owner = aula.player();
		if (owner != null) {
			Advancements.grant(owner, SekteIds.ADVANCEMENT_FOLDER + "kebun_panen");
		}
		Component name = crop.getHoverName();
		if (byPlayer != null) {
			Kebun.tell(byPlayer, Component.translatable("kultivator.sekte.kebun.bed.harvested", index + 1, count, name), ChatFormatting.GREEN);
		}
		if (first) {
			Kebun.note(aula.owner(), Component.translatable("kultivator.sekte.kebun.log.first_harvest", count, name), true);
		} else {
			Kebun.note(aula.owner(), gardener != null
					? Component.translatable("kultivator.sekte.kebun.log.harvest_by", gardener.getDisplayName(), count, name, index + 1)
					: Component.translatable("kultivator.sekte.kebun.log.harvest", count, name, index + 1), false);
		}
		if (gardener != null) {
			DiscipleData data = gardener.data();
			CompoundTag kerja = Kebun.kerja(data);
			kerja.putInt("total", kerja.getIntOr("total", 0) + count);
			SekteBus.WORK_DONE.invoker().onWorkDone(aula.server(), aula.owner(), gardener.soulId(), SekteIds.W, count);
		}
		if (bloom) {
			Component line = Component.translatable("kultivator.sekte.kebun.log.bloom", name, Math.min(999, age * 3));
			Kebun.note(aula.owner(), line, true);
			Kebun.tell(owner, line, ChatFormatting.LIGHT_PURPLE);
			if (gardener != null) {
				SekteBus.honored(aula.server(), aula.owner(), gardener.soulId(), "kebun.mekar", 2);
				Ingatan.remember(aula.server(), gardener.data(), SekteIds.W + ".mekar", null, 3, "kultivator.sekte.kebun.ingat.mekar",
						name.getString());
			}
			scene(aula, Kebun.BEAT_BLOOM, gardener, pos, Math.min(999, age * 3));
		}
		KebunPayloads.syncWorld(aula.owner());
	}

	/** One bed's yield (at least 1). */
	static int yieldOf(Aula aula, @Nullable DiscipleEntity gardener) {
		double base = KebunConfig.get().hasilPetak + Halls.level(aula.owner()) / 3;
		double factor = SekteApi.tetua().officeFactor(aula.owner(), Jabatan.TETUA_OBAT);
		if (gardener != null) {
			DiscipleData data = gardener.data();
			factor *= SekteApi.hati().workFactor(data);
			if (Kebun.greenRoot(data)) {
				factor *= ROOT_FACTOR;
			}
		}
		if (!Double.isFinite(factor) || factor <= 0.0D) {
			factor = 1.0D;
		}
		return Math.max(1, (int) Math.round(base * Math.min(3.0D, factor)));
	}

	/** A short scene for the owner nearby (beats 190-191; every 7b guard applies on the client). */
	static void scene(Aula aula, int beat, @Nullable DiscipleEntity subject, Vec3 pos, int a) {
		ServerPlayer owner = aula.player();
		if (!KebunConfig.get().adegan || owner == null || owner.level() != aula.level() || owner.position().distanceToSqr(pos) > 48.0D * 48.0D) {
			return;
		}
		EpicMoments.send(aula.level(), EpicIds.EXTRA, beat, subject, owner, pos, aula.level().getGameTime(), a, 0, 0.0F, 3, 0);
	}

	/** The live stats compound ({@code panen}, {@code herba}, {@code gali}, {@code inti}). */
	static CompoundTag stats(CompoundTag sec) {
		CompoundTag stats = sec.getCompound("stat").orElse(null);
		if (stats == null) {
			stats = new CompoundTag();
			sec.put("stat", stats);
		}
		return stats;
	}

	// ================================================================== gardeners

	/** Puts {@code disciple} on gardener slot {@code slot} (1-based); null on success, else why not. */
	static @Nullable Component assign(Aula aula, int slot, DiscipleEntity disciple) {
		int slots = Kebun.tenderSlots(Kebun.plotCount(aula.owner()));
		if (slot < 1 || slot > slots) {
			return Component.translatable("kultivator.sekte.kebun.job.no_slot");
		}
		Component refused = Kebun.refuse(aula, disciple);
		if (refused != null) {
			return refused;
		}
		Kebun.releaseEverywhere(aula, disciple.soulId());
		CompoundTag tenders = sub(aula.sec(), "rawat");
		if (tenders.contains(String.valueOf(slot))) {
			release(aula, slot, false);
			tenders = sub(aula.sec(), "rawat");
		}
		CompoundTag entry = new CompoundTag();
		entry.putString("soul", disciple.soulId().toString());
		tenders.put(String.valueOf(slot), entry);
		Kebun.kerja(disciple.data()).putString("job", SekteIds.W);
		BlockPos spot = Kebun.tenderSpot(aula, slot);
		disciple.setActivity(SekteIds.ACT_TEND, spot);
		Halls.setDirty();
		aula.level().playSound(null, spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, SoundEvents.BONE_MEAL_USE, SoundSource.NEUTRAL, 0.7F, 1.1F);
		KebunPayloads.fx(aula.level(), KebunPayloads.FX_PLANT, Vec3.atBottomCenterOf(spot), 0x8CFFB0);
		Kebun.note(aula.owner(), Component.translatable("kultivator.sekte.kebun.log.tender", disciple.getDisplayName(), slot * 2 - 1,
				Math.min(slot * 2, Kebun.plotCount(aula.owner()))), false);
		return null;
	}

	/** Frees gardener slot {@code slot}; {@code rest} also gives the disciple a day off (the forced-work streak ends). */
	static void release(Aula aula, int slot, boolean rest) {
		CompoundTag tenders = aula.sec().getCompoundOrEmpty("rawat");
		CompoundTag entry = tenders.getCompoundOrEmpty(String.valueOf(slot));
		UUID soul = Kebun.uuid(entry.getStringOr("soul", ""));
		tenders.remove(String.valueOf(slot));
		if (soul != null) {
			DiscipleEntity disciple = Kebun.bySoul(aula.owner(), soul);
			if (disciple != null) {
				CompoundTag kerja = Kebun.kerja(disciple.data());
				kerja.putString("job", "");
				if (rest) {
					kerja.putLong("rest_until", aula.now() + 24000L);
					kerja.putInt("streak", 0);
				}
				if (disciple.activity() == SekteIds.ACT_TEND) {
					disciple.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				}
			}
		}
		Halls.setDirty();
	}

	static CompoundTag sub(CompoundTag parent, String key) {
		CompoundTag child = parent.getCompound(key).orElse(null);
		if (child == null) {
			child = new CompoundTag();
			parent.put(key, child);
		}
		return child;
	}

	private Pertumbuhan() {
	}
}
