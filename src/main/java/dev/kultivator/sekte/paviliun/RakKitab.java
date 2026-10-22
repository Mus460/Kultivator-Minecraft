package dev.kultivator.sekte.paviliun;

import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.technique.source.BookPools;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Rak Kitab of the Paviliun Kitab (DESIGN 36.2.5): a virtual shelf of technique books ({@code TechniqueBooks}: the
 * enchanted books of the 6c pools) and jade slips ({@code JadeSlips}) the master deposits by clicking the pavilion
 * ({@code library} anchor) or from the hub card. 9 / 18 / 27 books at hall levels 1-3 / 4-6 / 7-9 ({@code rakMaks} is the
 * top). Books are never used up: disciples only read them, and the master can take any book back exactly as it came.
 * <p>
 * What a disciple may study from the rack follows the existing rules of teaching ({@link #blocker}): the technique must be
 * teachable, new to it, within its realm (Abadi arts only from True Immortal, as with slips), of its Dao (or a Dao
 * bypass), past the star gates and its comprehension minimum, never during a breakthrough, and the phase-6 teach hooks of
 * the legacy arts still have their say. Unlike a master's teaching it costs no qi and adds no Bond.
 * <p>
 * Sumber: biksu penyapu Perpustakaan Sutra Shaolin yang diam-diam menguasai semua kitab (<i>Demi-Gods and Semi-Devils</i>,
 * Jin Yong).
 */
final class RakKitab {

	static final String BOOK = "buku";
	static final String SLIP = "giok";

	/** A choice of the study: the spec and the rack entry it is read from. */
	record Pick(TechniqueSpec spec, int book) {
	}

	/** Books the rack holds at the owner's hall level. */
	static int capacity(UUID owner) {
		int top = Paviliun.config().rakMaks;
		int level = Halls.level(owner);
		if (level <= 0) {
			return 0;
		}
		return level <= 3 ? Math.max(1, top / 3) : level <= 6 ? Math.max(1, top * 2 / 3) : top;
	}

	/** The technique ids an item could teach from the rack (empty when it is not a technique book or slip). */
	static List<String> specsOf(ItemStack stack) {
		List<String> ids = new ArrayList<>();
		if (stack.isEmpty()) {
			return ids;
		}
		if (stack.is(Items.ENCHANTED_BOOK)) {
			for (TechniqueSpec spec : BookPools.poolOf(stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY))) {
				if (DiscipleTechniques.usable(spec) && spec.teachable() && !ids.contains(spec.id())) {
					ids.add(spec.id());
				}
			}
		} else if (stack.is(JadeSlips.JADE_SLIP)) {
			TechniqueSpec spec = JadeSlips.specOfStack(stack);
			if (spec != null && DiscipleTechniques.usable(spec) && spec.teachable()) {
				ids.add(spec.id());
			}
		}
		return ids;
	}

	/** True when the stack is something the rack takes. */
	static boolean accepts(ItemStack stack) {
		return !specsOf(stack).isEmpty();
	}

	/**
	 * The master deposits one item of {@code stack} (their hand). Returns the lang key of why it was refused, or null on
	 * success (the item left the hand and the master was told).
	 */
	static @Nullable String deposit(ServerPlayer master, ItemStack stack) {
		UUID owner = master.getUUID();
		if (Halls.hallOf(owner) == null) {
			return "kultivator.sekte.paviliun.rak.no_hall";
		}
		List<String> specs = specsOf(stack);
		if (specs.isEmpty()) {
			return "kultivator.sekte.paviliun.rak.not_book";
		}
		List<PaviliunData.Kitab> rack = PaviliunData.rack(owner);
		if (rack.size() >= capacity(owner)) {
			return "kultivator.sekte.paviliun.rak.full";
		}
		RegistryOps<Tag> ops = ops(master.level().getServer());
		ItemStack one = stack.copyWithCount(1);
		CompoundTag item = new CompoundTag();
		item.store("item", ItemStack.CODEC, ops, one);
		String kind = stack.is(JadeSlips.JADE_SLIP) ? SLIP : BOOK;
		Component name = title(specs);
		long today = Ingatan.today(master.level().getServer());
		PaviliunData.addBook(owner, kind, name, specs, item, master.getGameProfile().name(), today);
		if (!master.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		master.level().playSound(null, master.getX(), master.getY() + 1.0D, master.getZ(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 1.0F, 0.9F);
		master.level().playSound(null, master.getX(), master.getY() + 1.0D, master.getZ(), SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED,
				SoundSource.PLAYERS, 0.8F, 1.0F);
		Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.rak.deposited", name, rack.size() + 1, capacity(owner))
				.withStyle(ChatFormatting.AQUA));
		SekteLog.log(master.level().getServer(), owner, SekteIds.A, Component.translatable("kultivator.sekte.paviliun.log.deposit",
				master.getDisplayName(), name));
		return null;
	}

	/** The master takes rack entry {@code id} back into their inventory (or at their feet). False when not there. */
	static boolean withdraw(ServerPlayer master, int id) {
		UUID owner = master.getUUID();
		PaviliunData.Kitab book = PaviliunData.book(owner, id);
		if (book == null) {
			return false;
		}
		RegistryOps<Tag> ops = ops(master.level().getServer());
		ItemStack stack = book.item().read("item", ItemStack.CODEC, ops).orElse(ItemStack.EMPTY);
		PaviliunData.removeBook(owner, id);
		if (!stack.isEmpty()) {
			master.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
		// whoever was reading it closes the book (their points stay for another day)
		BelajarMandiri.bookRemoved(owner, id);
		master.level().playSound(null, master.getX(), master.getY() + 1.0D, master.getZ(), SoundEvents.CHISELED_BOOKSHELF_PICKUP_ENCHANTED,
				SoundSource.PLAYERS, 0.8F, 1.0F);
		Msg.chat(master, Component.translatable("kultivator.sekte.paviliun.rak.withdrawn", book.name()).withStyle(ChatFormatting.GRAY));
		return true;
	}

	/** The name of a rack entry: its first (lowest-grade) technique, "+n" when the book holds more. */
	static Component title(List<String> specs) {
		TechniqueSpec first = specs.isEmpty() ? null : TechniqueRegistry.byId(specs.get(0));
		Component base = first == null ? Component.translatable("kultivator.sekte.paviliun.rak.unknown") : first.displayName();
		return specs.size() <= 1 ? base.copy() : Component.translatable("kultivator.sekte.paviliun.rak.more", base, specs.size() - 1);
	}

	// ================================================================== the study gates

	/**
	 * Why {@code entity} may not study {@code spec} from the rack now (a short reason key), or null when it may: the rules
	 * of {@code DiscipleCultivation.teachBlocker} and {@code JadeSlips.discipleBlocker} without the master's knowledge and
	 * qi (the book itself teaches).
	 */
	static @Nullable String blocker(DiscipleEntity entity, TechniqueSpec spec, @Nullable ServerPlayer master) {
		DiscipleData data = entity.data();
		if (!spec.teachable() || !DiscipleTechniques.usable(spec)) {
			return "not_teachable";
		}
		if (data.knows(spec.id())) {
			return "known";
		}
		if (!DiscipleTechniques.rankReached(data.rank(), spec.minRank())) {
			return "realm";
		}
		if (spec.grade() == TechniqueGrade.ABADI && !data.realm().atLeast(Realm.TRUE_IMMORTAL)) {
			return "realm";
		}
		Dao dao = spec.dao();
		if (dao != null && dao != data.dao() && !DiscipleHooks.daoBypass(data)) {
			return "dao";
		}
		if (spec.minComprehension() > 0.0D && data.comprehension() + 1.0E-9D < spec.minComprehension()) {
			return "comprehension";
		}
		if (!DiscipleCultivation.starsReached(data, spec)) {
			return "stars";
		}
		if (data.breakingThrough()) {
			return "busy";
		}
		Technique legacy = spec.legacy();
		if (legacy != null) {
			// the phase-6 hooks need the master; without them online a legacy art waits for another day
			if (master == null) {
				return "master";
			}
			if (DiscipleHooks.teachBlocker(entity, master, legacy) != null) {
				return "hook";
			}
		}
		return null;
	}

	/**
	 * The technique {@code entity} picks from the rack, or null when nothing fits. It picks "by itself" (DESIGN 36.2.5):
	 * its own Dao's arts first, then the lowest grade (quickest to master); an Ambisius disciple reaches for the highest
	 * grade it can, a Rendah Hati one for the humblest; ties go to the oldest book on the shelf.
	 */
	static @Nullable Pick choose(DiscipleEntity entity, UUID owner, @Nullable ServerPlayer master) {
		DiscipleData data = entity.data();
		boolean ambitious = SekteApi.hati().has(data, Sifat.AMBISIUS);
		Pick best = null;
		int bestScore = Integer.MIN_VALUE;
		for (PaviliunData.Kitab book : PaviliunData.rack(owner)) {
			for (String id : book.specs()) {
				TechniqueSpec spec = TechniqueRegistry.byId(id);
				if (spec == null || blocker(entity, spec, master) != null) {
					continue;
				}
				int grade = spec.grade().tier();
				int score = (spec.dao() != null && spec.dao() == data.dao() ? 100 : 0) + (ambitious ? grade * 10 : -grade * 10) - book.id() / 1000;
				if (score > bestScore) {
					bestScore = score;
					best = new Pick(spec, book.id());
				}
			}
		}
		return best;
	}

	/** True when rack entry {@code book} still holds {@code specId}. */
	static boolean holds(UUID owner, int book, String specId) {
		PaviliunData.Kitab k = PaviliunData.book(owner, book);
		return k != null && k.specs().contains(specId);
	}

	static RegistryOps<Tag> ops(MinecraftServer server) {
		return server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	private RakKitab() {
	}
}
