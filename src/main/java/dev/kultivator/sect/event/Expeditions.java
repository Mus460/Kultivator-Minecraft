package dev.kultivator.sect.event;

import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.AlchemyItems;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.dao.TechniqueBooks;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.disciple.power.DisciplePowers;
import dev.kultivator.sect.build.flow.LumbungOverflow;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ekspedisi (DESIGN 20.3.10): the mission board of the Misi tablet, rolled each day at sunrise, teams of 1-3 disciples
 * sent away as pure data, the success roll on return, bar shares, PK, renown and loot for the storehouse, and the
 * Kunpeng bone fragments ("Satukan" teaches the wings through module C). A failed team comes back hurt (30% health, a
 * 3-minute Qi Deviation, half the XP share) and nobody ever dies.
 * <p>
 * Sumber: Battle Through the Heavens (Magical Beast Mountain Range, magic cores); Soul Land (Star Dou Great Forest and
 * Hunting Spirit Forest); Coiling Dragon Book 19 (Planar Battlefield badges exchanged at a stele); Perfect World
 * ch. 258-347 (the Kunpeng bone split into six parts, a third of the legacy).
 * <p>
 * Sekte yang Hidup (DESIGN 36.4, module X) reads this class through two public facades only:
 * {@link #lootTable} (the loot table, shared read-only by the story missions) and {@link #releaseErrands} (frees a
 * disciple from its duty post, the tower and the void queue before it leaves on a story mission). Nothing else of the
 * board changes.
 */
public final class Expeditions {

	/** Expedition type ids for {@link #lootTable}: Patroli, Herba, Karavan, Perburuan, Alam Rahasia. */
	public static final int LOOT_PATROL = 0;
	public static final int LOOT_HERBS = 1;
	public static final int LOOT_CARAVAN = 2;
	public static final int LOOT_HUNT = 3;
	public static final int LOOT_SECRET = 4;

	/**
	 * The loot of a successful expedition of {@code type} ({@code LOOT_*}) and {@code tier} 1-9 (DESIGN 20.3.10 table and
	 * the phase 7 rows), freshly created; read-only use by module X (no board state is touched).
	 */
	public static List<ItemStack> lootTable(int type, int tier, RandomSource random) {
		return loot(Math.max(0, Math.min(TYPES.length - 1, type)), Math.max(1, Math.min(9, tier)), random);
	}

	/** Frees {@code soul} from a duty post, the Blazing Tower and the Void Realm queue (as an expedition does). */
	public static void releaseErrands(UUID owner, UUID soul) {
		if (owner == null || soul == null) {
			return;
		}
		Duties.release(owner, soul, null);
		BlazingTower.stop(owner, soul, false);
		VoidRealm.dequeue(owner, soul);
	}

	/** Mission types: Patroli Wilayah, Mencari Herba Roh, Mengawal Karavan, Perburuan Binatang Iblis, Alam Rahasia. */
	static final String[] TYPES = {"patrol", "herbs", "caravan", "hunt", "secret_realm"};
	static final int[] MIN_TIER = {1, 1, 2, 3, 6};
	static final int[] MAX_TIER = {3, 5, 6, 8, 9};
	static final long[] DURATION = {12000L, 12000L, 24000L, 24000L, 48000L};
	static final int TYPE_HERBS = 1;
	static final int TYPE_CARAVAN = 2;
	static final int TYPE_HUNT = 3;
	static final int TYPE_SECRET = 4;
	/** Missions offered per day by hall level 1-9. */
	static final int[] MISSIONS_PER_DAY = {3, 3, 3, 4, 4, 4, 4, 5, 5};
	static final int MAX_PARTY = 3;
	/** Murid Luar lead missions of tiers 1-2, Murid Dalam up to tier 5, Murid Inti and above every tier (DESIGN 20.3.6). */
	static final int OUTER_MAX_TIER = 2;
	static final int INNER_MAX_TIER = 5;
	/** Success = clamp(0.45 + 0.12 x (P - (d - 1)) + 0.05 with an Inti+ member, 0.10, 0.95). */
	static final double BASE_CHANCE = 0.45D;
	static final double CHANCE_PER_POWER = 0.12D;
	static final double LEADER_BONUS = 0.05D;
	static final double MIN_CHANCE = 0.10D;
	static final double MAX_CHANCE = 0.95D;
	static final double KUNPENG_CHANCE = 0.08D;
	/** Phase 8 (DESIGN 25.2 item 14): the Alam Rahasia mission's chance of a {@code kultivator:heavenvoid_fragment}. */
	static final double HEAVENVOID_FRAGMENT_CHANCE = 0.15D;
	/** A failed team returns at this share of health, with this Qi Deviation (ticks). */
	static final float FAIL_HEALTH = 0.30F;
	static final int FAIL_DEVIATION = 3600;

	private static final String BOARD = "board";

	// ================================================================== board

	static int missionsPerDay(int level) {
		return MISSIONS_PER_DAY[Math.max(1, Math.min(9, level)) - 1];
	}

	/** T* = clamp(1 + the strongest disciple's realm ordinal, 1, 9) over loaded and away disciples. */
	static int strongestTier(UUID owner) {
		int best = 0;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			best = Math.max(best, disciple.data().realm().ordinal());
		}
		for (DiscipleData data : AwayTeams.awayData(owner)) {
			best = Math.max(best, data.realm().ordinal());
		}
		return Math.max(1, Math.min(9, 1 + best));
	}

	/** Rolls today's board when the day changed (sunrise, missed days never stack). */
	static void ensureBoard(UUID owner, int hallLevel, RandomSource random) {
		CompoundTag section = EventUtil.section(owner);
		long day = EventUtil.day();
		if (section.getLongOr("board_day", Long.MIN_VALUE) == day && section.contains(BOARD)) {
			return;
		}
		roll(owner, hallLevel, random);
	}

	static void roll(UUID owner, int hallLevel, RandomSource random) {
		CompoundTag section = EventUtil.section(owner);
		int top = strongestTier(owner);
		ListTag board = new ListTag();
		for (int i = 0; i < missionsPerDay(hallLevel); i++) {
			int tier = Math.max(1, Math.min(9, top - 1 + random.nextInt(3)));
			List<Integer> fits = new ArrayList<>();
			for (int type = 0; type < TYPES.length; type++) {
				if (tier >= MIN_TIER[type] && tier <= MAX_TIER[type]) {
					fits.add(type);
				}
			}
			int type = fits.isEmpty() ? 0 : fits.get(random.nextInt(fits.size()));
			CompoundTag offer = new CompoundTag();
			offer.putInt("type", type);
			offer.putInt("tier", Math.max(MIN_TIER[type], Math.min(MAX_TIER[type], tier)));
			offer.putBoolean("taken", false);
			board.add(offer);
		}
		section.put(BOARD, board);
		section.putLong("board_day", EventUtil.day());
		EventUtil.dirty();
	}

	static ListTag board(UUID owner) {
		return EventUtil.section(owner).getListOrEmpty(BOARD).copy();
	}

	// ================================================================== departure

	/** The reason of {@link #refuses}: a defiant disciple refuses the duty (S13). */
	public static final String REFUSES = "kultivator.sekte_sambung.membangkang";

	/**
	 * S13: a defiant disciple (Membangkang, module P) refuses the old event systems too, as the new ones already do
	 * (expeditions, the talent scout, the Forbidden Ground, the Void Realm, the Ark, the tournament's entrants).
	 */
	public static boolean refuses(DiscipleEntity disciple) {
		return disciple != null && dev.kultivator.sekte.api.SekteApi.hati().defiant(disciple.data());
	}

	/** Why {@code members} cannot take offer {@code type} / {@code tier}, or null when they can. */
	static @Nullable String blocker(int type, int tier, List<DiscipleEntity> members) {
		if (members.isEmpty() || members.size() > MAX_PARTY) {
			return "kultivator.sectevent.mission.party_size";
		}
		boolean leader = false;
		int highest = 0;
		for (DiscipleEntity member : members) {
			if (refuses(member)) {
				return REFUSES;
			}
			int rank = SectRanks.rank(member.data());
			highest = Math.max(highest, rank);
			if (rank < 1) {
				return "kultivator.sectevent.mission.need_outer";
			}
			if ((type == TYPE_HUNT || type == TYPE_CARAVAN && tier >= 3) && rank < 2) {
				return "kultivator.sectevent.mission.need_inner";
			}
			if (rank >= 3 && member.data().realm().atLeast(Realm.NASCENT_SOUL)) {
				leader = true;
			}
		}
		if (type == TYPE_SECRET && !leader) {
			return "kultivator.sectevent.mission.need_leader";
		}
		if (tier > maxTier(highest)) {
			return "kultivator.sectevent.mission.need_rank_tier";
		}
		return null;
	}

	/** The highest mission tier a team led by a disciple of sect rank {@code rank} may take (DESIGN 20.3.6 perks). */
	static int maxTier(int rank) {
		return rank >= 3 ? 9 : rank == 2 ? INNER_MAX_TIER : OUTER_MAX_TIER;
	}

	/** Sends a team on board offer {@code index}. */
	static boolean send(ServerPlayer player, int index, List<DiscipleEntity> members) {
		UUID owner = player.getUUID();
		ListTag board = EventUtil.section(owner).getListOrEmpty(BOARD);
		if (index < 0 || index >= board.size() || !(board.get(index) instanceof CompoundTag offer) || offer.getBooleanOr("taken", false)) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.mission.gone").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		int type = Math.max(0, Math.min(TYPES.length - 1, offer.getIntOr("type", 0)));
		int tier = offer.getIntOr("tier", 1);
		String why = blocker(type, tier, members);
		if (why != null) {
			EventUtil.chat(player, Component.translatable(why).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		CompoundTag record = new CompoundTag();
		record.putInt("type", type);
		record.putInt("tier", tier);
		if (AwayTeams.depart(player, AwayTeams.KIND_EXPEDITION, members, DURATION[type], record) == null) {
			return false;
		}
		offer.putBoolean("taken", true);
		EventUtil.dirty();
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.3F);
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.mission.sent", missionName(type), tier,
				days(DURATION[type])).withStyle(ChatFormatting.AQUA));
		return true;
	}

	static Component missionName(int type) {
		return Component.translatable("kultivator.sectevent.mission." + TYPES[Math.max(0, Math.min(TYPES.length - 1, type))]);
	}

	/** "0.5" days for the chat line. */
	static String days(long ticks) {
		return String.format(java.util.Locale.ROOT, "%.1f", ticks / (double) EventUtil.DAY);
	}

	// ================================================================== resolution

	/** Member power m = realm ordinal + stage / 4 + layer / 9 + stars / 9 (DESIGN 20.3.10). */
	static double memberPower(DiscipleData data) {
		Rank rank = data.rank();
		return rank.realm().ordinal() + rank.stage() / 4.0D + rank.layer() / 9.0D + DisciplePaths.stars(data) / 9.0D;
	}

	/** The success chance of a team on a mission of {@code tier}. */
	static double chance(List<DiscipleData> team, int tier) {
		double best = 0.0D;
		double sum = 0.0D;
		int strongestStars = 0;
		boolean core = false;
		DiscipleData strongest = null;
		for (DiscipleData data : team) {
			double m = memberPower(data);
			sum += m;
			if (strongest == null || m > best) {
				best = m;
				strongest = data;
			}
			core |= SectRanks.rank(data) >= 3;
		}
		if (strongest != null) {
			strongestStars = DisciplePaths.stars(strongest);
		}
		double party = best + 0.5D * (sum - best);
		double difficulty = tier + (tier == 9 ? strongestStars / 9.0D : 0.0D);
		double chance = BASE_CHANCE + CHANCE_PER_POWER * (party - (difficulty - 1.0D)) + (core ? LEADER_BONUS : 0.0D);
		return Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, chance));
	}

	/** Bar share of a success: 15% (tier 1-3), 25% (4-6), 35% (7-9), +10% for Alam Rahasia. */
	static double share(int type, int tier) {
		double share = tier <= 3 ? 0.15D : tier <= 6 ? 0.25D : 0.35D;
		return share + (type == TYPE_SECRET ? 0.10D : 0.0D);
	}

	static void resolve(UUID owner, CompoundTag team, @Nullable ServerPlayer player, RandomSource random) {
		int type = Math.max(0, Math.min(TYPES.length - 1, team.getIntOr("type", 0)));
		int tier = Math.max(1, Math.min(9, team.getIntOr("tier", 1)));
		ListTag members = team.getListOrEmpty("members");
		List<DiscipleData> datas = new ArrayList<>();
		for (Tag tag : members) {
			if (tag instanceof CompoundTag member) {
				datas.add(AwayTeams.data(member));
			}
		}
		boolean success = random.nextDouble() < chance(datas, tier);
		double share = share(type, tier);
		int index = 0;
		for (Tag tag : members) {
			if (!(tag instanceof CompoundTag member)) {
				continue;
			}
			DiscipleData data = datas.get(index++);
			if (success) {
				DisciplePaths.grantBarShare(data, share);
				SectRanks.addPk(data, 10 * tier, "kultivator.sectevent.pk.mission");
			} else {
				DisciplePaths.grantBarShare(data, share * 0.5D);
				data.setDeviationTicks(Math.max(data.deviationTicks(), FAIL_DEVIATION));
				member.putFloat("arrive_hp", FAIL_HEALTH);
			}
			AwayTeams.setData(member, data);
		}
		if (player != null) {
			Advancements.grant(player, "first_expedition");
		}
		if (!success) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.mission.failed", missionName(type), tier).withStyle(ChatFormatting.RED));
			return;
		}
		Halls.addPool(owner, 5 * tier);
		Halls.addRenown(owner, 10 * tier, "kultivator.sectevent.renown.mission");
		Halls.log(owner, Component.translatable("kultivator.sectevent.log.mission", missionName(type), tier));
		List<ItemStack> loot = loot(type, tier, random);
		if (type == TYPE_SECRET && random.nextDouble() < KUNPENG_CHANCE) {
			loot.add(SectEventItems.fragment(1 + random.nextInt(6)));
		}
		// Into the Lumbung; what does not fit goes to the Peti Galian or waits for room (never lost).
		int worst = LumbungOverflow.LUMBUNG;
		Component example = null;
		for (ItemStack stack : loot) {
			Component name = stack.getHoverName();
			int where = LumbungOverflow.store(owner, stack);
			if (where > worst) {
				worst = where;
				example = name;
			}
		}
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.mission.success", missionName(type), tier, 10 * tier,
				(int) Math.round(share * 100.0D)).withStyle(ChatFormatting.GOLD));
		LumbungOverflow.tell(player, owner, worst, example);
		if (type == TYPE_SECRET && player != null) {
			Advancements.grant(player, "secret_realm");
		}
	}

	/** The loot of a successful mission of {@code tier} (DESIGN 20.3.10). */
	static List<ItemStack> loot(int type, int tier, RandomSource random) {
		List<ItemStack> loot = new ArrayList<>();
		if (tier <= 2) {
			Item[] kinds = {Items.BONE, Items.STRING, Items.IRON_INGOT};
			loot.add(new ItemStack(kinds[random.nextInt(kinds.length)], 2 + random.nextInt(5)));
		} else if (tier <= 4) {
			Item[] kinds = {Items.GOLD_INGOT, Items.EMERALD, Items.LAPIS_LAZULI};
			loot.add(new ItemStack(kinds[random.nextInt(kinds.length)], 2 + random.nextInt(4)));
		} else if (tier <= 6) {
			loot.add(new ItemStack(Items.DIAMOND, 1 + random.nextInt(3)));
			loot.add(new ItemStack(Items.ENDER_PEARL, 2 + random.nextInt(3)));
			loot.add(new ItemStack(Items.GOLDEN_APPLE));
		} else {
			loot.add(new ItemStack(Items.ECHO_SHARD, 1 + random.nextInt(2)));
			if (random.nextDouble() < 0.10D) {
				loot.add(new ItemStack(Items.NETHERITE_SCRAP));
			}
			if (random.nextDouble() < 0.15D) {
				ItemStack book = mappedBook(random);
				if (!book.isEmpty()) {
					loot.add(book);
				}
			}
			if (tier >= 9 && random.nextDouble() < 0.01D) {
				loot.add(new ItemStack(Items.NETHER_STAR));
			}
		}
		if (tier >= 4 && random.nextDouble() < 0.20D) {
			loot.add(AlchemyComponents.withQuality(new ItemStack(AlchemyItems.QI_GATHERING_PILL), PillQuality.MID));
		}
		if (type == TYPE_HERBS) {
			// Mencari Herba Roh: twice the garden's ingredients.
			Item[] herbs = {Items.GLOW_BERRIES, Items.GOLDEN_CARROT, Items.GLISTERING_MELON_SLICE, Items.SWEET_BERRIES};
			int count = 2 * (1 + random.nextInt(3));
			for (int i = 0; i < count; i++) {
				loot.add(new ItemStack(herbs[random.nextInt(herbs.length)]));
			}
		}
		addPhase7Loot(loot, type, tier, random);
		return loot;
	}

	/**
	 * Phase 7 loot rows (DESIGN 19.3.2-19.3.4, P7-harta): Mengawal Karavan +2-5 Low stones, Alam Rahasia +1 Mid (+5% High
	 * at tier 7+), Perburuan Binatang Iblis +1 core of a random element (grade by tier: 1-3 Low, 4-6 Mid, 7-8 High, 9 Raja
	 * at 10% else High), Mencari Herba Roh +1-2 spirit herbs aged 50 x tier to 150 x tier years.
	 */
	private static void addPhase7Loot(List<ItemStack> loot, int type, int tier, RandomSource random) {
		switch (type) {
			case TYPE_CARAVAN -> loot.add(dev.kultivator.harta.SpiritStones.stack(dev.kultivator.harta.SpiritStones.LOW, 2 + random.nextInt(4)));
			case TYPE_SECRET -> {
				loot.add(dev.kultivator.harta.SpiritStones.stack(dev.kultivator.harta.SpiritStones.MID, 1));
				if (tier >= 7 && random.nextDouble() < 0.05D) {
					loot.add(dev.kultivator.harta.SpiritStones.stack(dev.kultivator.harta.SpiritStones.HIGH, 1));
				}
				// Phase 8 (DESIGN 25.2 item 14): 15% a Heavenvoid map fragment, looked up by id (absent until module A registers it).
				if (random.nextDouble() < HEAVENVOID_FRAGMENT_CHANCE) {
					net.minecraft.world.item.Item fragment = net.minecraft.core.registries.BuiltInRegistries.ITEM
							.getValue(dev.kultivator.Kultivator.id("heavenvoid_fragment"));
					if (fragment != net.minecraft.world.item.Items.AIR) {
						loot.add(new ItemStack(fragment));
					}
				}
			}
			case TYPE_HUNT -> {
				int grade = tier <= 3 ? dev.kultivator.harta.BeastCores.LOW : tier <= 6 ? dev.kultivator.harta.BeastCores.MID
						: tier <= 8 || random.nextDouble() >= 0.10D ? dev.kultivator.harta.BeastCores.HIGH : dev.kultivator.harta.BeastCores.KING;
				dev.kultivator.data.Element[] elements = dev.kultivator.data.Element.values();
				loot.add(dev.kultivator.harta.BeastCores.create(elements[random.nextInt(elements.length)], grade));
			}
			case TYPE_HERBS -> {
				int herbs = 1 + random.nextInt(2);
				for (int i = 0; i < herbs; i++) {
					loot.add(dev.kultivator.harta.SpiritHerbs.randomFor(null, random, 50 * tier, 150 * tier));
				}
			}
			default -> {
			}
		}
	}

	/** An enchanted book whose enchantment teaches a technique (DESIGN 7), empty when none exists. */
	static ItemStack mappedBook(RandomSource random) {
		MinecraftServer server = EventUtil.server();
		if (server == null) {
			return ItemStack.EMPTY;
		}
		List<Holder<Enchantment>> mapped = new ArrayList<>();
		server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(holder -> {
			if (TechniqueBooks.techniqueFor(holder) != null) {
				mapped.add(holder);
			}
		});
		if (mapped.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return EnchantmentHelper.createBook(new EnchantmentInstance(mapped.get(random.nextInt(mapped.size())), 1));
	}

	// ================================================================== Kunpeng bone

	/** Bits 1-6 of the pieces found in the storehouse and the master's inventory (bit n-1 for piece n). */
	static int pieces(UUID owner, @Nullable ServerPlayer player) {
		int mask = 0;
		for (ItemStack stack : Storehouse.contents(owner)) {
			int piece = SectEventItems.pieceOf(stack);
			if (piece > 0) {
				mask |= 1 << (piece - 1);
			}
		}
		if (player != null) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				int piece = SectEventItems.pieceOf(player.getInventory().getItem(i));
				if (piece > 0) {
					mask |= 1 << (piece - 1);
				}
			}
		}
		return mask;
	}

	/** "Satukan": six different pieces teach an Inti+ disciple the Kunpeng Wings (module C). */
	static boolean unite(ServerPlayer player, DiscipleEntity disciple) {
		UUID owner = player.getUUID();
		if (pieces(owner, player) != 0x3F) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.kunpeng.missing").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (SectRanks.rank(disciple.data()) < 3) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.kunpeng.need_core", disciple.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!DisciplePowers.teachKunpeng(disciple)) {
			EventUtil.chat(player, Component.translatable("kultivator.sectevent.kunpeng.refused", disciple.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		for (int piece = 1; piece <= 6; piece++) {
			int wanted = piece;
			if (Storehouse.remove(owner, stack -> SectEventItems.pieceOf(stack) == wanted, 1) == 0) {
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					ItemStack stack = player.getInventory().getItem(i);
					if (SectEventItems.pieceOf(stack) == wanted) {
						stack.shrink(1);
						break;
					}
				}
			}
		}
		player.getInventory().setChanged();
		if (disciple.level() instanceof ServerLevel level) {
			EventUtil.fx(level, disciple.position(), SectEventFxIds.ARK_BOARD, 0, 0.0F);
			level.playSound(null, disciple.getX(), disciple.getY(), disciple.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 1.0F, 0.6F);
		}
		EventUtil.chat(player, Component.translatable("kultivator.sectevent.kunpeng.united", disciple.getDisplayName()).withStyle(ChatFormatting.GOLD));
		return true;
	}

	private Expeditions() {
	}
}
