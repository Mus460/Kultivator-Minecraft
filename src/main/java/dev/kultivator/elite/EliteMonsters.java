package dev.kultivator.elite;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.DaoProtectors;
import dev.kultivator.cultivation.SpiritVeins;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * P7-tempur (DESIGN 19.3.1): Binatang Iblis, elite demonic beasts made from naturally spawned vanilla monsters near a
 * Qi Refining+ player. The spine mixin hands every finished {@code Mob.finalizeSpawn} here; a successful roll is only
 * remembered and applied at the end of the server tick, after subclass equipment and loot rules ran. Elites keep every
 * vanilla rule (they still despawn: no custom name), are hardened like Dao Protectors so they cannot change blocks, and
 * carry {@link TechniquePower#TAG_ELITE} (trial-mob rule), {@link NoGrief#TAG} and the {@code kultivator:elite}
 * attachment. Loaded elites are tracked in a small map for the 10-tick skill pass. Nothing here touches a block.
 */
public final class EliteMonsters {

	/** Mobs from monster spawners: ×0.25 kill XP, no cores or herbs, never elites (DESIGN 19.3.14). */
	public static final String TAG_SPAWNER = "kultivator.spawner";

	public static final double BASE_CHANCE = 0.01D;
	public static final double CHANCE_PER_REALM = 0.005D;
	public static final double FULL_MOON_FACTOR = 2.0D;
	public static final double GREAT_VEIN_FACTOR = 1.5D;
	/** {@code SpiritVeins.grade} of a Great Spirit Vein region (DESIGN 19.8: −1 thin, 0 normal, 1 rich, 2 great). */
	public static final int GREAT_VEIN_GRADE = 2;
	public static final double CHANCE_CAP = 0.07D;
	/** The nearest non-spectator player within this range decides the roll. */
	public static final double ROLL_RANGE = 48.0D;
	public static final int MAX_NEAR_PLAYER = 3;
	public static final double NEAR_PLAYER_RANGE = 64.0D;
	public static final double SPACING = 24.0D;
	public static final int COOLDOWN_TICKS = 1200;
	public static final int CROWD_LIMIT = 10;
	public static final double CROWD_RANGE = 16.0D;
	public static final double KING_SPACING = 128.0D;
	/** Tier shares: 65% / 28% / 7%. */
	public static final double SHARE_KING = 0.07D;
	public static final double SHARE_DEMONIC = 0.28D;
	/** 75% natural element, 25% one of the nine. */
	public static final double NATURAL_ELEMENT = 0.75D;

	/** Stats by tier (index 1-3). */
	public static final double[] TIER_HEALTH = {1.0D, 1.5D, 2.5D, 4.0D};
	public static final double HEALTH_PER_REALM = 0.35D;
	public static final double ATTACK_PER_TIER = 1.5D;
	public static final double ATTACK_PER_REALM = 0.8D;
	public static final double[] KNOCKBACK = {0.0D, 0.3D, 0.5D, 0.8D};
	public static final double[] SCALE = {1.0D, 1.10D, 1.20D, 1.35D};
	public static final double[] SPEED = {0.0D, 0.05D, 0.10D, 0.15D};
	public static final double WIND_SPEED = 0.30D;

	private static final Identifier HEALTH_ID = Kultivator.id("elite_health");
	private static final Identifier ATTACK_ID = Kultivator.id("elite_attack");
	private static final Identifier ARMOR_ID = Kultivator.id("elite_armor");
	private static final Identifier KNOCKBACK_ID = Kultivator.id("elite_knockback");
	private static final Identifier SCALE_ID = Kultivator.id("elite_scale");
	private static final Identifier SPEED_ID = Kultivator.id("elite_speed");
	private static final Identifier WIND_ID = Kultivator.id("elite_wind");

	/** Natural spawns that may roll (DESIGN 19.3.1 allowlist). */
	private static final Set<EntityType<?>> ELIGIBLE = Set.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.SKELETON, EntityTypes.STRAY,
			EntityTypes.BOGGED, EntityTypes.SPIDER, EntityTypes.WITHER_SKELETON, EntityTypes.HOGLIN);
	/** Never an elite, not even as a protector: block changers, projectile users, splitters and bosses. */
	private static final Set<EntityType<?>> FORBIDDEN = Set.of(EntityTypes.CREEPER, EntityTypes.ENDERMAN, EntityTypes.GHAST, EntityTypes.BLAZE,
			EntityTypes.WITHER, EntityTypes.RAVAGER, EntityTypes.SILVERFISH, EntityTypes.ENDER_DRAGON, EntityTypes.WARDEN,
			EntityTypes.ELDER_GUARDIAN, EntityTypes.SLIME, EntityTypes.MAGMA_CUBE, EntityTypes.WITCH, EntityTypes.DROWNED,
			EntityTypes.PIGLIN, EntityTypes.PIGLIN_BRUTE, EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.EVOKER, EntityTypes.PILLAGER,
			EntityTypes.VEX, EntityTypes.SHULKER, EntityTypes.BREEZE, EntityTypes.CREAKING);
	/** {@code uji elit ... [jenis]}: the allowlist by id. */
	private static final Map<String, EntityType<? extends Mob>> TEST_TYPES = new LinkedHashMap<>();
	/** Element ids in commands (Indonesian first, the English ids also work). */
	private static final String[] ELEMENT_WORDS = {"logam", "kayu", "air", "api", "tanah", "petir", "es", "angin", "ruang"};

	static {
		TEST_TYPES.put("zombie", EntityTypes.ZOMBIE);
		TEST_TYPES.put("husk", EntityTypes.HUSK);
		TEST_TYPES.put("skeleton", EntityTypes.SKELETON);
		TEST_TYPES.put("stray", EntityTypes.STRAY);
		TEST_TYPES.put("bogged", EntityTypes.BOGGED);
		TEST_TYPES.put("spider", EntityTypes.SPIDER);
		TEST_TYPES.put("wither_skeleton", EntityTypes.WITHER_SKELETON);
		TEST_TYPES.put("hoglin", EntityTypes.HOGLIN);
	}

	/** A loaded elite and its skill and boss-bar state. */
	static final class Tracked {
		final Mob mob;
		long nextSkillAt;
		int skillTurn;
		@Nullable net.minecraft.server.level.ServerBossEvent bar;

		Tracked(Mob mob, long nextSkillAt) {
			this.mob = mob;
			this.nextSkillAt = nextSkillAt;
		}
	}

	/** A natural spawn that rolled an elite, applied at the end of the tick. */
	private record Pending(Mob mob, int tier, Element element, int realm) {
	}

	static final Map<UUID, Tracked> TRACKED = new HashMap<>();
	private static final List<Pending> PENDING = new ArrayList<>();
	/** Player -> server tick when an elite last appeared near them. */
	private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

	public static void init() {
		EliteData.register();
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.hasAttached(EliteData.TYPE)) {
				track(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> untrack(entity));
		// A converted elite (zombie -> drowned, skeleton -> stray...) is an ordinary mob again (DESIGN 19.3.1).
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, params) -> {
			if (isElite(previous) || previous.hasAttached(EliteData.TYPE)) {
				converted.removeTag(TechniquePower.TAG_ELITE);
				converted.removeAttached(EliteData.TYPE);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(EliteMonsters::serverTick);
		ServerPlayerEvents.LEAVE.register(player -> {
			COOLDOWN.remove(player.getUUID());
			BattleFatigue.forget(player.getUUID());
			for (Tracked tracked : TRACKED.values()) {
				if (tracked.bar != null) {
					tracked.bar.removePlayer(player);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (Tracked tracked : TRACKED.values()) {
				if (tracked.bar != null) {
					tracked.bar.removeAllPlayers();
				}
			}
			TRACKED.clear();
			PENDING.clear();
			COOLDOWN.clear();
			BattleFatigue.clearAll();
			EliteSkills.clearAll();
		});
		// The five elements and Perisai Unsur act on every technique choke point (DESIGN 19.2.8, 19.3.12).
		TechniquePower.addTargetFactor((attacker, target, spec) -> {
			double factor = ElementRelations.targetFactor(attacker, target, spec);
			if (factor >= ElementRelations.STRONG * 0.999D) {
				EliteSkills.spark(target, ElementRelations.techniqueElement(attacker, spec));
			}
			return factor;
		});
		TechniquePower.addTargetFactor(EliteSkills::wardFactor);
		EliteSkills.init();
		EliteRewards.init();
		LightningRings.init();
		HuFaTribulation.init();
	}

	// ================================================================== public API (DESIGN 19.8)

	/** True for an elite (it carries {@link TechniquePower#TAG_ELITE}). */
	public static boolean isElite(Entity entity) {
		return entity != null && entity.entityTags().contains(TechniquePower.TAG_ELITE);
	}

	/** The elite tier 1-3, 0 for anything else. */
	public static int tier(Entity entity) {
		EliteData data = isElite(entity) ? EliteData.of(entity) : null;
		return data == null ? 0 : data.tier();
	}

	/** The elite's element, null for anything else. */
	public static @Nullable Element element(Entity entity) {
		EliteData data = isElite(entity) ? EliteData.of(entity) : null;
		return data == null ? null : data.elementValue();
	}

	/** The elite's affix ({@link EliteData#NONE} for anything else). */
	public static int affix(Entity entity) {
		EliteData data = isElite(entity) ? EliteData.of(entity) : null;
		return data == null ? EliteData.NONE : data.affix();
	}

	/** Upgrades {@code mob} to an elite of {@code tier} (1-3), {@code element} (null = its natural one) and {@code realm}. */
	public static boolean makeElite(Mob mob, int tier, @Nullable Element element, int realm) {
		if (mob == null) {
			return false;
		}
		RandomSource random = mob.getRandom();
		int clamped = Math.max(1, Math.min(3, tier));
		return apply(mob, clamped, element == null ? rollElement(mob.getType(), random) : element, realm, rollAffix(clamped, random));
	}

	/**
	 * Called by {@code mixin/MobFinalizeSpawnMixin} at the TAIL of {@code Mob.finalizeSpawn}: SPAWNER tags the mob
	 * {@code kultivator.spawner}, NATURAL rolls for an elite, every other reason is untouched (DESIGN 19.3.1). Subclass
	 * overrides (skeleton bows, zombie loot pickup) still run after this returns, so a successful roll is applied at the
	 * end of the server tick.
	 */
	public static void onFinalizeSpawn(Mob mob, ServerLevelAccessor level, EntitySpawnReason reason) {
		if (reason == EntitySpawnReason.SPAWNER) {
			mob.addTag(TAG_SPAWNER);
			return;
		}
		if (reason != EntitySpawnReason.NATURAL || !ELIGIBLE.contains(mob.getType())) {
			return;
		}
		KultivatorConfig config = KultivatorConfig.get();
		if (!config.eliteMonsters || !(config.eliteChanceScale > 0.0D)) {
			return;
		}
		ServerLevel serverLevel = level.getLevel();
		Vec3 pos = mob.position();
		ServerPlayer nearest = nearestPlayer(serverLevel, pos);
		if (nearest == null) {
			return;
		}
		Realm realm = Cultivators.get(nearest).realm();
		if (!realm.atLeast(Realm.QI_REFINING)) {
			return;
		}
		BlockPos block = BlockPos.containing(pos);
		double chance = chance(realm, fullMoonNight(serverLevel, block), SpiritVeins.grade(serverLevel, block) >= GREAT_VEIN_GRADE,
				config.eliteChanceScale);
		RandomSource random = serverLevel.getRandom();
		if (random.nextDouble() >= chance) {
			return;
		}
		// The limits are checked only after the roll succeeded (DESIGN 19.1.2).
		long now = serverLevel.getServer().getTickCount();
		Long last = COOLDOWN.get(nearest.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS && now >= last) {
			return;
		}
		int nearPlayer = 0;
		for (Vec3 other : elitePositions(serverLevel)) {
			if (other.distanceToSqr(pos) <= SPACING * SPACING) {
				return;
			}
			if (other.distanceToSqr(nearest.position()) <= NEAR_PLAYER_RANGE * NEAR_PLAYER_RANGE) {
				nearPlayer++;
			}
		}
		if (nearPlayer >= MAX_NEAR_PLAYER) {
			return;
		}
		AABB crowd = new AABB(pos, pos).inflate(CROWD_RANGE);
		if (serverLevel.getEntitiesOfClass(Mob.class, crowd, entity -> entity != mob && entity instanceof Enemy && entity.isAlive()).size() >= CROWD_LIMIT) {
			return;
		}
		int tier = rollTier(random, realm, serverLevel, pos);
		COOLDOWN.put(nearest.getUUID(), now);
		PENDING.add(new Pending(mob, tier, rollElement(mob.getType(), random), realm.ordinal()));
	}

	/** The elite chance of one natural spawn (DESIGN 19.3.1): 0.01 + 0.005 × realm, ×2 full moon, ×1.5 Great vein, ×scale, ≤ 7%. */
	public static double chance(Realm realm, boolean fullMoon, boolean greatVein, double scale) {
		if (!realm.atLeast(Realm.QI_REFINING)) {
			return 0.0D;
		}
		double chance = BASE_CHANCE + CHANCE_PER_REALM * realm.ordinal();
		if (fullMoon) {
			chance *= FULL_MOON_FACTOR;
		}
		if (greatVein) {
			chance *= GREAT_VEIN_FACTOR;
		}
		return Math.min(CHANCE_CAP, chance * Math.max(0.0D, scale));
	}

	// ================================================================== making an elite

	/** Applies the elite: hardening, tags, attachment, stats; true when it worked. */
	static boolean apply(Mob mob, int tier, Element element, int realm, int affix) {
		if (!mob.isAlive() || isElite(mob) || !(mob instanceof Enemy) || FORBIDDEN.contains(mob.getType()) || TechniquePower.isBoss(mob)
				|| !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		int clampedTier = Math.max(1, Math.min(3, tier));
		int clampedRealm = Math.max(0, Math.min(Realm.values().length - 1, realm));
		DaoProtectors.harden(mob);
		if (mob instanceof AbstractSkeleton skeleton && !(mob instanceof WitherSkeleton)) {
			// Arrows break pots, chorus and dripstone: skeletons, strays and bogged fight with an iron sword.
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			skeleton.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			skeleton.reassessWeaponGoal();
		}
		mob.addTag(NoGrief.TAG);
		mob.addTag(TechniquePower.TAG_ELITE);
		mob.setAttached(EliteData.TYPE, new EliteData(clampedTier, element.id(), clampedRealm, affix));
		applyStats(mob, clampedTier, clampedRealm, element);
		Fx.send(level, mob.position(), mob.position().add(0.0D, mob.getBbHeight(), 0.0D), FxType.ELITE, CombatFxIds.BIRTH, mob.getId(), clampedTier);
		if (level.getEntity(mob.getUUID()) == mob) {
			track(mob);
		}
		return true;
	}

	/** The stats of DESIGN 19.3.1 as permanent modifiers {@code kultivator:elite_*}, then healed to full. */
	private static void applyStats(Mob mob, int tier, int realm, Element element) {
		modifier(mob, Attributes.MAX_HEALTH, HEALTH_ID, TIER_HEALTH[tier] * (1.0D + HEALTH_PER_REALM * realm) - 1.0D,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(mob, Attributes.ATTACK_DAMAGE, ATTACK_ID, ATTACK_PER_TIER * tier + ATTACK_PER_REALM * realm, AttributeModifier.Operation.ADD_VALUE);
		modifier(mob, Attributes.ARMOR, ARMOR_ID, realm + 2.0D * tier, AttributeModifier.Operation.ADD_VALUE);
		modifier(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, KNOCKBACK[tier], AttributeModifier.Operation.ADD_VALUE);
		modifier(mob, Attributes.SCALE, SCALE_ID, SCALE[tier] - 1.0D, AttributeModifier.Operation.ADD_VALUE);
		modifier(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED[tier], AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (element == Element.WIND) {
			modifier(mob, Attributes.MOVEMENT_SPEED, WIND_ID, WIND_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
		mob.setHealth(mob.getMaxHealth());
	}

	private static void modifier(Mob mob, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && Double.isFinite(amount) && amount != 0.0D) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** 75% the natural element of the type, 25% (or for a neutral type) a random one of the nine. */
	static Element rollElement(EntityType<?> type, RandomSource random) {
		Element natural = MobElements.natural(type);
		if (natural != null && random.nextDouble() < NATURAL_ELEMENT) {
			return natural;
		}
		Element[] all = Element.values();
		return all[random.nextInt(all.length)];
	}

	/** Tier 2+ rolls one of the three affixes; tier 1 has none. */
	static int rollAffix(int tier, RandomSource random) {
		return tier >= 2 ? 1 + random.nextInt(3) : EliteData.NONE;
	}

	/** 65 / 28 / 7, falling back when the nearest player's realm (or another king nearby) does not allow the tier. */
	private static int rollTier(RandomSource random, Realm realm, ServerLevel level, Vec3 pos) {
		double roll = random.nextDouble();
		int tier = roll < SHARE_KING ? 3 : roll < SHARE_KING + SHARE_DEMONIC ? 2 : 1;
		if (tier == 3 && (!realm.atLeast(Realm.GOLDEN_CORE) || kingNear(level, pos))) {
			tier = 2;
		}
		if (tier == 2 && !realm.atLeast(Realm.FOUNDATION)) {
			tier = 1;
		}
		return tier;
	}

	private static boolean kingNear(ServerLevel level, Vec3 pos) {
		for (Tracked tracked : TRACKED.values()) {
			if (tracked.mob.level() == level && tracked.mob.isAlive() && tier(tracked.mob) == 3
					&& tracked.mob.position().distanceToSqr(pos) <= KING_SPACING * KING_SPACING) {
				return true;
			}
		}
		for (Pending pending : PENDING) {
			if (pending.tier == 3 && pending.mob.level() == level && pending.mob.position().distanceToSqr(pos) <= KING_SPACING * KING_SPACING) {
				return true;
			}
		}
		return false;
	}

	/** Positions of the loaded and pending elites of {@code level}. */
	private static List<Vec3> elitePositions(ServerLevel level) {
		List<Vec3> list = new ArrayList<>();
		for (Tracked tracked : TRACKED.values()) {
			if (tracked.mob.level() == level && tracked.mob.isAlive()) {
				list.add(tracked.mob.position());
			}
		}
		for (Pending pending : PENDING) {
			if (pending.mob.level() == level) {
				list.add(pending.mob.position());
			}
		}
		return list;
	}

	private static @Nullable ServerPlayer nearestPlayer(ServerLevel level, Vec3 pos) {
		ServerPlayer best = null;
		double bestSq = ROLL_RANGE * ROLL_RANGE;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			double sq = player.position().distanceToSqr(pos);
			if (sq <= bestSq) {
				bestSq = sq;
				best = player;
			}
		}
		return best;
	}

	/** A visible full moon at night in the Overworld (the moon-phase environment attribute, as {@code SpiritSites}). */
	private static boolean fullMoonNight(ServerLevel level, BlockPos pos) {
		if (!level.dimension().equals(Level.OVERWORLD) || !level.isDarkOutside() || level.isRaining()) {
			return false;
		}
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, pos) == MoonPhase.FULL_MOON;
	}

	// ================================================================== tracking and the tick

	static void track(Mob mob) {
		if (mob.level() instanceof ServerLevel level && !TRACKED.containsKey(mob.getUUID())) {
			long now = level.getServer().getTickCount();
			TRACKED.put(mob.getUUID(), new Tracked(mob, now + 100L + mob.getRandom().nextInt(100)));
		}
	}

	static void untrack(Entity entity) {
		Tracked tracked = TRACKED.remove(entity.getUUID());
		if (tracked != null && tracked.bar != null) {
			tracked.bar.removeAllPlayers();
		}
	}

	/** The loaded elites (read-only view for the skill pass and commands). */
	static Collection<Tracked> tracked() {
		return TRACKED.values();
	}

	private static void serverTick(MinecraftServer server) {
		if (!PENDING.isEmpty()) {
			List<Pending> batch = new ArrayList<>(PENDING);
			PENDING.clear();
			for (Pending pending : batch) {
				Mob mob = pending.mob;
				// Only mobs the spawner really added (a failed spawn is never in the level).
				if (mob.isAlive() && !mob.isRemoved() && mob.level() instanceof ServerLevel level && level.getEntity(mob.getUUID()) == mob) {
					apply(mob, pending.tier, pending.element, pending.realm, rollAffix(pending.tier, mob.getRandom()));
				}
			}
		}
		long tick = server.getTickCount();
		if (KultivatorConfig.get().eliteMonsters) {
			EliteSkills.tickPending(server, tick);
			if (tick % 10L == 0L && !TRACKED.isEmpty()) {
				EliteSkills.pass(server, tick);
			}
		} else if (tick % 20L == 0L) {
			// Switched off (DESIGN 19.11 A3): loaded elites lose their skills and their Raja boss bars.
			for (Tracked tracked : TRACKED.values()) {
				if (tracked.bar != null) {
					tracked.bar.removeAllPlayers();
					tracked.bar = null;
				}
			}
		}
		if (tick % 20L == 0L) {
			HuFaTribulation.check(server);
		}
		if (tick % 1200L == 0L && !COOLDOWN.isEmpty()) {
			COOLDOWN.values().removeIf(at -> tick - at > COOLDOWN_TICKS || at > tick);
		}
	}

	// ================================================================== commands

	private static final SuggestionProvider<CommandSourceStack> ELEMENTS = (ctx, builder) -> {
		List<String> words = new ArrayList<>(List.of(ELEMENT_WORDS));
		words.add("alami");
		return SharedSuggestionProvider.suggest(words, builder);
	};
	private static final SuggestionProvider<CommandSourceStack> TYPES = (ctx, builder) -> SharedSuggestionProvider.suggest(TEST_TYPES.keySet(), builder);
	private static final SuggestionProvider<CommandSourceStack> AFFIXES = (ctx, builder) -> SharedSuggestionProvider.suggest(List.of(EliteData.AFFIX_IDS), builder);
	private static final SuggestionProvider<CommandSourceStack> REALMS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(java.util.Arrays.stream(Realm.values()).map(Realm::id), builder);

	/** {@code kawal <player>} and {@code debug elit info}, {@code debug kejenuhan show|reset} (DESIGN 19.4). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("kawal")
				.then(Commands.argument("player", EntityArgument.player())
						.executes(ctx -> HuFaTribulation.join(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player")) ? 1 : 0)));
		debug.then(Commands.literal("elit")
				.then(Commands.literal("info").executes(ctx -> eliteInfo(ctx.getSource().getPlayerOrException()))));
		debug.then(Commands.literal("kejenuhan")
				.then(Commands.literal("show").executes(ctx -> fatigueShow(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("reset").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					BattleFatigue.set(player, 0.0D);
					return fatigueShow(player);
				})));
	}

	/** {@code uji elit|tribulasi|kawal|kejenuhan|unsur ...} (DESIGN 19.4). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("elit")
				.then(Commands.literal("hapus").executes(ctx -> removeTest(ctx.getSource().getPlayerOrException())))
				.then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
						.executes(ctx -> spawnTest(ctx, null, null, null))
						.then(Commands.argument("unsur", StringArgumentType.word()).suggests(ELEMENTS)
								.executes(ctx -> spawnTest(ctx, StringArgumentType.getString(ctx, "unsur"), null, null))
								.then(Commands.argument("jenis", StringArgumentType.word()).suggests(TYPES)
										.executes(ctx -> spawnTest(ctx, StringArgumentType.getString(ctx, "unsur"), StringArgumentType.getString(ctx, "jenis"), null))
										.then(Commands.argument("afiks", StringArgumentType.word()).suggests(AFFIXES)
												.executes(ctx -> spawnTest(ctx, StringArgumentType.getString(ctx, "unsur"),
														StringArgumentType.getString(ctx, "jenis"), StringArgumentType.getString(ctx, "afiks"))))))));
		RequiredArgumentBuilder<CommandSourceStack, Integer> lapis = Commands.argument("lapis", IntegerArgumentType.integer(1, 9))
				.executes(ctx -> testTribulation(ctx, IntegerArgumentType.getInteger(ctx, "lapis"), LightningRings.MODE_NORMAL));
		addRingModes(lapis, ctx -> IntegerArgumentType.getInteger(ctx, "lapis"));
		RequiredArgumentBuilder<CommandSourceStack, String> ranah = Commands.argument("ranah", StringArgumentType.word()).suggests(REALMS)
				.executes(ctx -> testTribulation(ctx, 1, LightningRings.MODE_NORMAL));
		addRingModes(ranah, ctx -> 1);
		ranah.then(lapis);
		uji.then(Commands.literal("tribulasi").then(ranah));
		uji.then(Commands.literal("kawal")
				.then(Commands.argument("n", IntegerArgumentType.integer(0, HuFaTribulation.MAX_FAKE))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							int n = IntegerArgumentType.getInteger(ctx, "n");
							HuFaTribulation.setFake(player, n);
							Msg.chat(player, Component.translatable("kultivator.combat.test.kawal", n).withStyle(ChatFormatting.GOLD));
							return 1;
						})));
		uji.then(Commands.literal("kejenuhan")
				.then(Commands.argument("nilai", DoubleArgumentType.doubleArg(0.0D, BattleFatigue.MAX))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							BattleFatigue.set(player, DoubleArgumentType.getDouble(ctx, "nilai"));
							return fatigueShow(player);
						})));
		uji.then(Commands.literal("unsur").executes(ctx -> elementInfo(ctx.getSource().getPlayerOrException())));
	}

	/** The lapis argument (or 1) of a {@code uji tribulasi} node. */
	private interface LayerOf {
		int get(CommandContext<CommandSourceStack> ctx);
	}

	/** Adds the literals {@code biasa|cincin|kembar} (DESIGN 19.4) under {@code node}. */
	private static void addRingModes(ArgumentBuilder<CommandSourceStack, ?> node, LayerOf layer) {
		for (int mode = LightningRings.MODE_NORMAL; mode <= LightningRings.MODE_TWINS; mode++) {
			int chosen = mode;
			node.then(Commands.literal(LightningRings.MODE_WORDS[mode]).executes(ctx -> testTribulation(ctx, layer.get(ctx), chosen)));
		}
	}

	// ================================================================== command bodies

	private static int spawnTest(CommandContext<CommandSourceStack> ctx, @Nullable String elementArg, @Nullable String typeArg,
			@Nullable String affixArg) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		int tier = IntegerArgumentType.getInteger(ctx, "tier");
		Element element = null;
		if (elementArg != null && !elementArg.equals("alami")) {
			element = parseElement(elementArg);
			if (element == null) {
				return fail(player, "kultivator.combat.test.bad_element", elementArg);
			}
		}
		EntityType<? extends Mob> type = typeArg == null ? EntityTypes.ZOMBIE : TEST_TYPES.get(typeArg.toLowerCase(Locale.ROOT));
		if (type == null) {
			return fail(player, "kultivator.combat.test.bad_type", typeArg);
		}
		int affix = -1;
		if (affixArg != null) {
			affix = List.of(EliteData.AFFIX_IDS).indexOf(affixArg.toLowerCase(Locale.ROOT));
			if (affix < 0) {
				return fail(player, "kultivator.combat.test.bad_affix", affixArg);
			}
		}
		Mob mob = type.create(level, EntitySpawnReason.COMMAND);
		if (mob == null) {
			return fail(player, "kultivator.combat.test.no_spawn", "");
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		Vec3 spot = Targeting.safeSpot(level, mob, player.position().add(flat.scale(4.0D)));
		if (spot == null) {
			spot = player.position();
		}
		mob.snapTo(spot.x, spot.y, spot.z, player.getYRot() + 180.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(spot)), EntitySpawnReason.COMMAND,
				mob instanceof Zombie ? new Zombie.ZombieGroupData(false, false) : null);
		for (Entity passenger : List.copyOf(mob.getPassengers())) {
			// A spider jockey's skeleton was never added to the level: let it go.
			passenger.stopRiding();
			passenger.discard();
		}
		RandomSource random = level.getRandom();
		int realm = Cultivators.get(player).realm().ordinal();
		Element chosen = element == null ? rollElement(type, random) : element;
		if (!apply(mob, tier, chosen, realm, affix < 0 ? rollAffix(tier, random) : affix) || !level.addFreshEntity(mob)) {
			mob.discard();
			return fail(player, "kultivator.combat.test.no_spawn", "");
		}
		EliteData data = EliteData.of(mob);
		if (data != null) {
			Msg.chat(player, Component.translatable("kultivator.combat.test.spawned", data.title(), mob.getType().getDescription(),
					Msg.number(mob.getMaxHealth()), EliteData.affixName(data.affix())).withStyle(ChatFormatting.GOLD));
			Msg.chat(player, Component.translatable("kultivator.combat.tier." + EliteData.TIER_IDS[data.tier()] + ".source")
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			if (data.affix() != EliteData.NONE) {
				Msg.chat(player, Component.translatable("kultivator.combat.affix." + EliteData.AFFIX_IDS[data.affix()] + ".source")
						.withStyle(ChatFormatting.GRAY));
			}
		}
		return 1;
	}

	private static int removeTest(ServerPlayer player) {
		int removed = 0;
		for (Tracked tracked : List.copyOf(TRACKED.values())) {
			Mob mob = tracked.mob;
			if (mob.level() == player.level() && mob.isAlive() && mob.distanceToSqr(player) <= 32.0D * 32.0D) {
				Fx.burst(player.level(), mob.position().add(0.0D, mob.getBbHeight() * 0.5D, 0.0D), 0x303030, 16, 1.0F);
				untrack(mob);
				mob.discard();
				removed++;
			}
		}
		Msg.chat(player, Component.translatable("kultivator.combat.test.removed", removed).withStyle(ChatFormatting.GRAY));
		return removed;
	}

	private static int testTribulation(CommandContext<CommandSourceStack> ctx, int layer, int mode) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "ranah");
		Realm realm = Realm.byId(id.toLowerCase(Locale.ROOT));
		if (realm == null) {
			return fail(player, "kultivator.combat.test.bad_realm", id);
		}
		Rank target = new Rank(realm, Math.max(0, Math.min(realm.layers(), layer) - 1), 0);
		LightningRings.forceNext(player, mode);
		if (!Breakthrough.beginTest(player, target)) {
			LightningRings.forceNext(player, LightningRings.MODE_UNSET);
			return 0;
		}
		Msg.chat(player, Component.translatable("kultivator.combat.test.tribulation", target.displayName(),
				Component.translatable("kultivator.combat.test.ring_mode." + mode)).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int eliteInfo(ServerPlayer player) {
		LivingEntity target = Targeting.lookedAtHostile(player, 32.0D);
		EliteData data = target == null || !isElite(target) ? null : EliteData.of(target);
		if (data == null) {
			return fail(player, "kultivator.combat.debug.no_elite", "");
		}
		Msg.chat(player, Component.translatable("kultivator.combat.debug.info", data.title(), target.getType().getDescription(),
				Realm.byOrdinal(data.realm()).displayName(), EliteData.affixName(data.affix()), Msg.number(target.getHealth()),
				Msg.number(target.getMaxHealth())).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int fatigueShow(ServerPlayer player) {
		double s = BattleFatigue.saturation(player);
		Msg.chat(player, Component.translatable("kultivator.combat.fatigue.show", Msg.number(s), BattleFatigue.format(BattleFatigue.factorOf(s)))
				.withStyle(ChatFormatting.AQUA));
		return 1;
	}

	/** {@code uji unsur}: the technique element, the target's element, the relation, the resonance and the total. */
	private static int elementInfo(ServerPlayer player) {
		LivingEntity target = Targeting.lookedAtHostile(player, 32.0D);
		if (target == null) {
			return fail(player, "kultivator.combat.test.no_target", "");
		}
		CultivationData data = Cultivators.get(player);
		Element universal = ElementRelations.techniqueElement(data);
		Element targetElement = MobElements.of(target);
		int root = ElementRelations.rootMask(player);
		Msg.chat(player, Component.translatable("kultivator.combat.unsur.title", target.getType().getDescription()).withStyle(ChatFormatting.GOLD));
		report(player, Component.translatable("kultivator.combat.unsur.universal"), universal, targetElement, root, target, null);
		TechniqueSpec spec = TechniqueRegistry.byId(data.selectedId());
		if (spec != null) {
			report(player, Component.translatable("kultivator.combat.unsur.selected", Component.translatable(spec.nameKey())),
					ElementRelations.techniqueElement(player, spec), targetElement, root, target, spec);
		}
		return 1;
	}

	private static void report(ServerPlayer player, Component label, @Nullable Element technique, @Nullable Element target, int root,
			LivingEntity entity, @Nullable TechniqueSpec spec) {
		Component none = Component.translatable("kultivator.combat.unsur.none");
		Component techName = technique == null ? none : technique.displayName();
		Component targetName = target == null ? none : target.displayName();
		double relation = ElementRelations.factor(technique, target);
		double resonance = ElementRelations.resonance(root, technique);
		double total = TechniquePower.targetFactor(player, entity, spec);
		Msg.chat(player, Component.translatable("kultivator.combat.unsur.line", label, techName, targetName, factorText(relation),
				factorText(resonance), factorText(total)).withStyle(ChatFormatting.AQUA));
	}

	private static String factorText(double factor) {
		return String.format(Locale.ROOT, "%.2f", factor);
	}

	static @Nullable Element parseElement(String word) {
		String lower = word.toLowerCase(Locale.ROOT);
		for (int i = 0; i < ELEMENT_WORDS.length; i++) {
			if (ELEMENT_WORDS[i].equals(lower)) {
				return Element.values()[i];
			}
		}
		return Element.byId(lower);
	}

	private static int fail(ServerPlayer player, String key, String arg) {
		Msg.chat(player, Component.translatable(key, arg).withStyle(ChatFormatting.RED));
		return 0;
	}

	private EliteMonsters() {
	}
}
