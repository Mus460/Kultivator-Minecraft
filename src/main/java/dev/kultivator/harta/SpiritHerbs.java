package dev.kultivator.harta;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.cultivation.SpiritVeins;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.event.SectEventItems;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tanaman Roh, spirit herbs with an age in years (DESIGN 19.3.4).
 * <p>
 * A plant the player breaks is spirit-infused when {@code hash(seed, dimension, pos, herb, overworld day) < p x mods}:
 * deterministic per spot and day (a bone-meal farm gets one roll per spot per day) and Divine Sense can show the same
 * result. mods: Wood root or Life Dao x2, the herb's own place factor (Ninecurl x2 in jungle, taiga, old growth or
 * cherry; Illusionary x3 at full moon) and x min(2, spiritual site), the site read only when the hash could pass. The
 * herb pops beside the plant through {@link Block#popResource} after the player broke it; nothing is ever planted.
 * <p>
 * Sources: age sets value (RMJ ch. 26, 164, 276); richer land gives older herbs (inspired by the Hundred Medicine
 * Garden, RMJ ch. 150-151). Each herb's own source is on its item. The hiding among ordinary plants, the hash and the
 * age formula are game design.
 */
public final class SpiritHerbs {

	/** Age of a herb that carries no age (kits, creative). */
	public static final int DEFAULT_AGE = 100;
	/** From this age the name turns gold and a find is announced. */
	public static final int MILLENNIUM = 1000;
	public static final int MILLENNIUM_COLOR = 0xFFC83C;
	/** The Foundation Herb without an age counts as this old. */
	public static final int FOUNDATION_HERB_AGE = 60;
	public static final double ROOT_FACTOR = 2.0D;
	public static final double SITE_CAP = 2.0D;
	public static final double BIOME_FACTOR = 2.0D;
	public static final double FULL_MOON_FACTOR = 3.0D;
	/** Age = round((AGE_BASE + r x AGE_SPREAD) x clamp(density, 0.5, 2) x min(2, site)), x10 with a 1% chance. */
	public static final double AGE_BASE = 10.0D;
	public static final double AGE_SPREAD = 90.0D;
	public static final int JACKPOT_IN = 100;
	public static final int JACKPOT_FACTOR = 10;
	/** Vein density each find drains. */
	public static final double VEIN_DRAIN = 0.02D;
	/** Alchemy: an auxiliary herb raises the quality roll by min(0.30, age / 3333). */
	public static final double AUX_YEARS_PER_POINT = 3333.0D;
	public static final double AUX_QUALITY_CAP = 0.30D;
	/** One server-wide line per player per this many ticks at most. */
	public static final int ANNOUNCE_COOLDOWN = 12000;
	/** {@code uji herba tampak}: the next plant broken within this many ticks counts as infused. */
	public static final int FORCE_TICKS = 1200;
	/** Divine Sense ping colour of an infused plant. */
	public static final int SENSE_COLOR = 0x6CFF8A;
	/** {@code extra.harta.herbs_found}. */
	public static final String FOUND = "herbs_found";
	/** Herbs one player can find per day (DESIGN 19.11 E25); {@code extra.harta.herb_day {day, n}}. */
	public static final int DAILY_FINDS = 12;
	public static final String HERB_DAY = "herb_day";
	/** Harvested spots remembered per dimension for the current day (oldest dropped beyond this). */
	public static final int HARVESTED_CAP = 4096;

	private static final Map<UUID, Long> FORCED = new HashMap<>();
	private static final Map<UUID, Long> ANNOUNCED = new HashMap<>();
	/** Spots already harvested today, per dimension: at most one herb per spot per day. */
	private static final Map<ResourceKey<Level>, LongLinkedOpenHashSet> HARVESTED = new HashMap<>();
	private static long harvestedDay = -1L;

	/** A herb found in a plant: its kind and age in years. */
	public record Find(HerbKind kind, int age) {
	}

	private record Candidate(HerbKind kind, double factor) {
	}

	static void init() {
		PlayerBlockBreakEvents.AFTER.register(SpiritHerbs::onBlockBroken);
	}

	// ================================================================ the stack

	/** A random spirit herb that suits {@code element} (any herb when null), aged {@code minAge}..{@code maxAge} years. */
	public static ItemStack randomFor(@Nullable Element element, RandomSource random, int minAge, int maxAge) {
		int total = 0;
		for (HerbKind kind : HerbKind.values()) {
			if (kind.suits(element)) {
				total += kind.weight();
			}
		}
		HerbKind chosen = HerbKind.SPIRIT_GRASS;
		if (total > 0) {
			int roll = random.nextInt(total);
			for (HerbKind kind : HerbKind.values()) {
				if (!kind.suits(element)) {
					continue;
				}
				roll -= kind.weight();
				if (roll < 0) {
					chosen = kind;
					break;
				}
			}
		}
		int low = Math.max(1, Math.min(minAge, maxAge));
		int high = Math.max(low, Math.max(minAge, maxAge));
		return create(chosen, low + random.nextInt(high - low + 1));
	}

	/** One herb of {@code kind} aged {@code age} years. */
	public static ItemStack create(HerbKind kind, int age) {
		ItemStack stack = new ItemStack(HartaItems.herb(kind));
		stack.set(HartaComponents.HERB_AGE, Math.max(1, Math.min(HartaComponents.MAX_HERB_AGE, age)));
		return stack;
	}

	/** True for a spirit herb (the existing foundation herb included). */
	public static boolean isHerb(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof SpiritHerbItem || stack.is(SectEventItems.FOUNDATION_HERB));
	}

	/** The herb's age in years (0 for anything else). */
	public static int age(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (stack.getItem() instanceof SpiritHerbItem) {
			return stack.getOrDefault(HartaComponents.HERB_AGE, DEFAULT_AGE);
		}
		if (stack.is(SectEventItems.FOUNDATION_HERB)) {
			return stack.getOrDefault(HartaComponents.HERB_AGE, FOUNDATION_HERB_AGE);
		}
		return 0;
	}

	/** The herb kind of a stack, or null (the foundation herb has none). */
	public static @Nullable HerbKind kind(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof SpiritHerbItem herb ? herb.kind() : null;
	}

	/** The quality-roll bonus of an auxiliary herb of {@code age} in the furnace (pure). */
	public static double auxiliaryQuality(int age) {
		return Math.min(AUX_QUALITY_CAP, Math.max(0, age) / AUX_YEARS_PER_POINT);
	}

	/** Kantor Misi points of one herb: 1 + age / 100. */
	public static double missionValue(int age) {
		return 1.0D + Math.max(0, age) / 100.0D;
	}

	// ================================================================ the infusion test

	/** True for the plants a herb can hide in (block type only, no place or time check). */
	public static boolean isPlant(BlockState state) {
		return grass(state) || state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS)
				|| state.is(BlockTags.SMALL_FLOWERS) || state.is(Blocks.SWEET_BERRY_BUSH) || nether(state) || state.is(Blocks.BAMBOO)
				|| state.is(Blocks.CHORUS_FLOWER) || state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.RED_MUSHROOM)
				|| state.is(Blocks.BROWN_MUSHROOM);
	}

	private static boolean grass(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
	}

	private static boolean nether(BlockState state) {
		return state.is(Blocks.NETHER_WART) || state.is(Blocks.CRIMSON_FUNGUS) || state.is(Blocks.WARPED_FUNGUS)
				|| state.is(Blocks.CRIMSON_ROOTS) || state.is(Blocks.WARPED_ROOTS);
	}

	/** The herbs this plant could hold right now (place, maturity and time), with their place factor. */
	private static List<Candidate> candidates(ServerLevel level, BlockPos pos, BlockState state) {
		List<Candidate> list = new ArrayList<>(2);
		ResourceKey<Level> dimension = level.dimension();
		if (grass(state)) {
			if (Level.OVERWORLD.equals(dimension)) {
				list.add(new Candidate(HerbKind.SPIRIT_GRASS, 1.0D));
			}
		} else if (state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS)) {
			if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
				list.add(new Candidate(HerbKind.NINECURL_GINSENG, ginsengBiome(level, pos) ? BIOME_FACTOR : 1.0D));
			}
		} else if (state.is(BlockTags.SMALL_FLOWERS)) {
			if (cold(level, pos)) {
				list.add(new Candidate(HerbKind.EIGHT_ANGLED_ICE_GRASS, 1.0D));
			}
			if (level.isDarkOutside() && level.canSeeSky(pos)) {
				list.add(new Candidate(HerbKind.ILLUSORY_SPIRIT_HERB, fullMoon(level, pos) ? FULL_MOON_FACTOR : 1.0D));
			}
		} else if (state.is(Blocks.SWEET_BERRY_BUSH)) {
			if (state.getValue(SweetBerryBushBlock.AGE) >= SweetBerryBushBlock.MAX_AGE && cold(level, pos)) {
				list.add(new Candidate(HerbKind.EIGHT_ANGLED_ICE_GRASS, 1.0D));
			}
		} else if (nether(state)) {
			boolean ripe = !state.is(Blocks.NETHER_WART) || state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
			if (ripe && Level.NETHER.equals(dimension)) {
				list.add(new Candidate(HerbKind.PURPLE_FLAME_FLOWER, 1.0D));
			}
		} else if (state.is(Blocks.BAMBOO)) {
			if (level.isThundering() && level.canSeeSky(pos)) {
				list.add(new Candidate(HerbKind.GOLD_LIGHTNING_BAMBOO, 1.0D));
			}
		} else if (state.is(Blocks.CHORUS_FLOWER) || state.is(Blocks.CHORUS_PLANT)) {
			if (Level.END.equals(dimension)) {
				list.add(new Candidate(HerbKind.GINSENG_FRUIT, 1.0D));
			}
		} else if (state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM)) {
			Holder<Biome> biome = level.getBiome(pos);
			if (biome.is(Biomes.MUSHROOM_FIELDS) || biome.is(Biomes.DEEP_DARK) || pos.getY() < 0) {
				list.add(new Candidate(HerbKind.YELLOW_ESSENCE_MUSHROOM, 1.0D));
			}
		}
		return list;
	}

	private static boolean cold(ServerLevel level, BlockPos pos) {
		return level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	private static boolean ginsengBiome(ServerLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		return biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_TAIGA) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)
				|| biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA) || biome.is(Biomes.CHERRY_GROVE);
	}

	private static boolean fullMoon(ServerLevel level, BlockPos pos) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos) == MoonPhase.FULL_MOON;
	}

	/**
	 * The herb hidden in this plant for {@code player} today, or null. Read-only (Divine Sense uses it too); the site is
	 * read only when the hash could pass.
	 */
	public static @Nullable Find infused(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		if (!isPlant(state)) {
			return null;
		}
		if (harvested(level, pos)) {
			return null;
		}
		List<Candidate> candidates = candidates(level, pos, state);
		if (candidates.isEmpty()) {
			return null;
		}
		CultivationData data = Cultivators.get(player);
		boolean wood = data.rootTested() && (data.rootElements() & Element.WOOD.bit()) != 0;
		double root = wood || data.dao() == Dao.LIFE ? ROOT_FACTOR : 1.0D;
		long seed = level.getSeed();
		long day = day(level);
		double site = -1.0D;
		double vein = 1.0D;
		for (Candidate candidate : candidates) {
			long h = hash(seed, level.dimension(), pos, candidate.kind(), day);
			double u = (h >>> 11) * 0x1.0p-53;
			double threshold = candidate.kind().chance() * candidate.factor() * root;
			if (u >= threshold * SITE_CAP) {
				continue;
			}
			if (site < 0.0D) {
				site = siteFactor(player);
				vein = Math.min(1.0D, Math.max(0.0D, SpiritVeins.density(level, pos)));
			}
			if (u >= threshold * site * vein) {
				continue;
			}
			double r = (mix(h ^ 0x632BE59BD9B4E019L) >>> 11) * 0x1.0p-53;
			boolean jackpot = Long.remainderUnsigned(mix(h ^ 0x5DEECE66DL), JACKPOT_IN) == 0L;
			return new Find(candidate.kind(), age(level, pos, site, r, jackpot));
		}
		return null;
	}

	private static double siteFactor(ServerPlayer player) {
		return Math.min(SITE_CAP, SpiritSites.current(player).multiplier());
	}

	private static int age(ServerLevel level, BlockPos pos, double site, double r, boolean jackpot) {
		double density = Math.max(0.5D, Math.min(2.0D, SpiritVeins.density(level, pos)));
		double years = (AGE_BASE + r * AGE_SPREAD) * density * Math.max(0.0D, Math.min(SITE_CAP, site));
		long age = Math.round(years) * (jackpot ? JACKPOT_FACTOR : 1);
		return (int) Math.max(1L, Math.min(HartaComponents.MAX_HERB_AGE, age));
	}

	/** Overworld day index (the same day for every dimension). */
	public static long day(ServerLevel level) {
		return Math.max(0L, level.getServer().overworld().getOverworldClockTime() / 24000L);
	}

	private static long hash(long seed, ResourceKey<Level> dimension, BlockPos pos, HerbKind kind, long day) {
		long h = mix(seed ^ 0x9E3779B97F4A7C15L);
		h = mix(h ^ pos.asLong());
		h = mix(h ^ (dimension.identifier().hashCode() * 0xC2B2AE3D27D4EB4FL));
		h = mix(h ^ ((kind.ordinal() + 1L) * 0x165667B19E3779F9L));
		return mix(h ^ (day * 0xD6E8FEB86659FD93L));
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	// ================================================================ harvesting

	/** True when this spot already gave a herb today. */
	private static boolean harvested(ServerLevel level, BlockPos pos) {
		rollDay(level);
		LongLinkedOpenHashSet set = HARVESTED.get(level.dimension());
		return set != null && set.contains(pos.asLong());
	}

	private static void markHarvested(ServerLevel level, BlockPos pos) {
		rollDay(level);
		LongLinkedOpenHashSet set = HARVESTED.computeIfAbsent(level.dimension(), key -> new LongLinkedOpenHashSet());
		if (set.add(pos.asLong()) && set.size() > HARVESTED_CAP) {
			set.removeFirstLong();
		}
	}

	private static void rollDay(ServerLevel level) {
		long day = day(level);
		if (day != harvestedDay) {
			harvestedDay = day;
			HARVESTED.clear();
		}
	}

	/** Herbs {@code data}'s player already found today. */
	private static int foundToday(ServerLevel level, CultivationData data) {
		CompoundTag tag = Harta.extra(data).getCompoundOrEmpty(HERB_DAY);
		return tag.getLongOr("day", -1L) == day(level) ? tag.getIntOr("n", 0) : 0;
	}

	/** {@code uji herba tampak}: the next plant {@code player} breaks within {@link #FORCE_TICKS} counts as infused. */
	public static void force(ServerPlayer player) {
		FORCED.put(player.getUUID(), player.level().getGameTime() + FORCE_TICKS);
	}

	/** The herb a forced plant gives (block type only). */
	private static @Nullable HerbKind forcedKind(ServerLevel level, BlockPos pos, BlockState state) {
		if (grass(state)) {
			return HerbKind.SPIRIT_GRASS;
		}
		if (state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS)) {
			return HerbKind.NINECURL_GINSENG;
		}
		if (state.is(BlockTags.SMALL_FLOWERS)) {
			return cold(level, pos) ? HerbKind.EIGHT_ANGLED_ICE_GRASS : HerbKind.ILLUSORY_SPIRIT_HERB;
		}
		if (state.is(Blocks.SWEET_BERRY_BUSH)) {
			return HerbKind.EIGHT_ANGLED_ICE_GRASS;
		}
		if (nether(state)) {
			return HerbKind.PURPLE_FLAME_FLOWER;
		}
		if (state.is(Blocks.BAMBOO)) {
			return HerbKind.GOLD_LIGHTNING_BAMBOO;
		}
		if (state.is(Blocks.CHORUS_FLOWER) || state.is(Blocks.CHORUS_PLANT)) {
			return HerbKind.GINSENG_FRUIT;
		}
		if (state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM)) {
			return HerbKind.YELLOW_ESSENCE_MUSHROOM;
		}
		return null;
	}

	private static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer
				|| serverPlayer.isCreative() || serverPlayer.isSpectator() || !KultivatorConfig.get().herbHarvest || !isPlant(state)) {
			return;
		}
		CultivationData data = Cultivators.get(serverPlayer);
		if (!data.realm().atLeast(Realm.QI_REFINING)) {
			return;
		}
		Find find = null;
		Long forced = FORCED.get(serverPlayer.getUUID());
		if (forced != null) {
			if (forced < serverLevel.getGameTime()) {
				FORCED.remove(serverPlayer.getUUID());
			} else {
				HerbKind kind = forcedKind(serverLevel, pos, state);
				if (kind != null) {
					FORCED.remove(serverPlayer.getUUID());
					RandomSource random = serverPlayer.getRandom();
					find = new Find(kind, age(serverLevel, pos, siteFactor(serverPlayer), random.nextDouble(), random.nextInt(JACKPOT_IN) == 0));
				}
			}
		}
		if (find == null) {
			if (foundToday(serverLevel, data) >= DAILY_FINDS) {
				return;
			}
			find = infused(serverLevel, pos, state, serverPlayer);
		}
		if (find != null) {
			harvest(serverLevel, serverPlayer, data, pos, find);
		}
	}

	private static void harvest(ServerLevel level, ServerPlayer player, CultivationData data, BlockPos pos, Find find) {
		ItemStack stack = create(find.kind(), find.age());
		Component name = stack.getHoverName().copy();
		Block.popResource(level, pos, stack);
		SpiritVeins.drain(level, pos, VEIN_DRAIN);
		markHarvested(level, pos);
		CompoundTag own = Harta.extra(data);
		own.putInt(FOUND, own.getIntOr(FOUND, 0) + 1);
		CompoundTag today = new CompoundTag();
		today.putLong("day", day(level));
		today.putInt("n", foundToday(level, data) + 1);
		own.put(HERB_DAY, today);
		Cultivators.dirty(player);

		Vec3 centre = Vec3.atCenterOf(pos);
		Fx.send(level, centre, FxType.TREASURE, HartaFxIds.HERB_FOUND, find.kind().ordinal(), find.age());
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F, 1.6F);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 0.6F, 1.4F);
		Msg.actionBar(player, Component.translatable("kultivator.harta.herb.found", name, find.age()).withColor(find.kind().color()));
		Advancements.grant(player, "first_herb");
		if (find.age() >= MILLENNIUM) {
			level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.6F);
			Advancements.grant(player, "thousand_year_herb");
			long now = level.getGameTime();
			Long last = ANNOUNCED.get(player.getUUID());
			if (last == null || now - last >= ANNOUNCE_COOLDOWN || now < last) {
				ANNOUNCED.put(player.getUUID(), now);
				Msg.broadcast(level.getServer(), Component.translatable("kultivator.harta.herb.announce", player.getDisplayName(), name, find.age())
						.withColor(MILLENNIUM_COLOR));
			}
		}
	}

	/** Advancement checks for a herb held by {@code player} (inventory scan). */
	static void checkAdvancements(ServerPlayer player, ItemStack stack) {
		if (!(stack.getItem() instanceof SpiritHerbItem)) {
			return;
		}
		Advancements.grant(player, "first_herb");
		if (age(stack) >= MILLENNIUM) {
			Advancements.grant(player, "thousand_year_herb");
		}
	}

	/** Forgets per-player marks (logout, server stop). */
	static void forget(UUID player) {
		FORCED.remove(player);
		ANNOUNCED.remove(player);
	}

	static void clear() {
		FORCED.clear();
		ANNOUNCED.clear();
		HARVESTED.clear();
		harvestedDay = -1L;
	}

	private SpiritHerbs() {
	}
}
