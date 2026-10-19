package dev.kultivator.abadi;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.ascension.Stars;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.XpSources;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.elite.BattleFatigue;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.technique.source.JadeSlips;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DamageHooks;
import dev.kultivator.util.KillCredit;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Makhluk Alam Abadi, the natives (DESIGN 26.3.6): every 400 ticks per player in Alam Abadi (staggered) a group of 1-2 at a
 * safe spot 24-40 blocks away over solid ground (never over the void), in entity-ticking chunks only, while fewer than
 * {@code immortalNativesPerPlayer} are within 48 blocks and fewer than {@code immortalNativeWorldCap} live in the world. Kinds:
 * Laba-laba Benang Langit (spider, Wind), Prajurit Tulang Abadi (stray with a sword and an unbreakable helmet that never
 * drops, so daylight never burns it, Ice), Babi Taring Emas (hoglin, immune to zombification, Earth), Penjaga Tulang Kuno
 * (wither skeleton, Metal) and in 25% of groups a Kultivator Abadi Liar (a rogue cultivator). They are elites (19.3 skills)
 * with HP {@code 24 × {2.5, 4.0}[tier] × (1 + 0.35 R_eff)} and melee {@code 4 + R_eff + 1.5 × tier}; Tekanan Abadi adds 3% of
 * the victim's max HP to their melee hits. Never saved, no loot pickup, gone after 60 s with no player within 64 blocks.
 * Rewards: XP, Batu Asal Abadi, herbs, rarely a Dewa jade slip, Battle Fatigue. The 40-tick pass also keeps Tekanan Alam
 * Abadi (Weakness I) on disciples below Kenaikan Abadi. Names and numbers are game design, inspired by RMJIR.
 */
final class Natives {

	public static final String TAG = "kultivator.abadi_native";
	public static final int SPAWN_PERIOD = 400;
	public static final double NEAR_RANGE = 48.0D;
	public static final double SPAWN_MIN = 24.0D;
	public static final double SPAWN_MAX = 40.0D;
	public static final double LONELY_RANGE = 64.0D;
	public static final int LONELY_TICKS = 1200;
	public static final double ROGUE_SHARE = 0.25D;
	public static final double BASE_HEALTH = 24.0D;
	public static final double[] TIER_HEALTH = {1.0D, 1.5D, 2.5D, 4.0D};
	public static final double HEALTH_PER_REALM = 0.35D;
	public static final double PRESSURE = 0.03D;
	public static final double STONE_CHANCE = 0.5D;
	public static final double HERB_CHANCE = 0.15D;
	public static final double SLIP_CHANCE = 0.01D;
	public static final double XP_COMBAT = 6.0D;
	public static final double XP_BAR = 0.01D;
	public static final double EXTRA_FATIGUE = 2.0D;
	public static final List<String> KINDS = List.of("laba", "tulang", "babi", "penjaga", "liar");

	private static final Identifier HEALTH_ID = Kultivator.id("abadi_native");
	private static final Identifier ATTACK_ID = Kultivator.id("abadi_native_attack");

	private static final class Tracked {
		final Mob mob;
		final int tier;
		long lonelySince;

		Tracked(Mob mob, int tier, long now) {
			this.mob = mob;
			this.tier = tier;
			this.lonelySince = now;
		}
	}

	private static final Map<UUID, Tracked> TRACKED = new LinkedHashMap<>();

	static void init() {
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD || !entity.entityTags().contains(TAG));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && !entity.isRemoved()) {
				TRACKED.remove(entity.getUUID());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Natives::afterDeath);
		DamageHooks.register(DamageHooks.Stage.DR, (entity, source, amount) -> {
			Entity attacker = source.getEntity();
			if (attacker != null && attacker == source.getDirectEntity() && attacker.entityTags().contains(TAG) && !(attacker instanceof DiscipleEntity)) {
				return amount + (float) (entity.getMaxHealth() * PRESSURE);
			}
			return amount;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRACKED.clear());
	}

	// ================================================================== ticking

	static void tick(MinecraftServer server, long ticks) {
		if (ticks % 10L == 0L && !TRACKED.isEmpty()) {
			cleanup(server);
		}
		if (ticks % 40L == 0L) {
			weakenDisciples(server);
		}
		ServerLevel level = Abadi.immortalLevel(server);
		if (level == null || level.players().isEmpty()) {
			return;
		}
		for (ServerPlayer player : List.copyOf(level.players())) {
			if ((ticks + (player.getUUID().hashCode() & 0x7FFFFFFF)) % SPAWN_PERIOD == 0L) {
				trySpawn(level, player);
			}
		}
	}

	private static void cleanup(MinecraftServer server) {
		long now = Abadi.now(server);
		List<Mob> gone = new ArrayList<>();
		Iterator<Tracked> iterator = TRACKED.values().iterator();
		while (iterator.hasNext()) {
			Tracked tracked = iterator.next();
			Mob mob = tracked.mob;
			if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				iterator.remove();
				continue;
			}
			if (level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), LONELY_RANGE, entity -> !entity.isSpectator()) != null) {
				tracked.lonelySince = now;
			} else if (now - tracked.lonelySince >= LONELY_TICKS) {
				iterator.remove();
				gone.add(mob);
			}
		}
		for (Mob mob : gone) {
			mob.discard();
		}
	}

	private static void weakenDisciples(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
				Crossing.weaken(disciple);
			}
		}
	}

	/** Natives alive in the world. */
	static int alive() {
		int n = 0;
		for (Tracked tracked : TRACKED.values()) {
			if (!tracked.mob.isRemoved() && tracked.mob.isAlive()) {
				n++;
			}
		}
		return n;
	}

	/** Natives within {@code range} of the player. */
	static int near(ServerPlayer player, double range) {
		int n = 0;
		for (Tracked tracked : TRACKED.values()) {
			Mob mob = tracked.mob;
			if (!mob.isRemoved() && mob.isAlive() && mob.level() == player.level() && mob.distanceToSqr(player) <= range * range) {
				n++;
			}
		}
		return n;
	}

	private static void trySpawn(ServerLevel level, ServerPlayer player) {
		KultivatorConfig config = KultivatorConfig.get();
		if (config.immortalNativesPerPlayer <= 0 || level.getDifficulty() == Difficulty.PEACEFUL || player.isCreative() || player.isSpectator()
				|| !player.isAlive()) {
			return;
		}
		int room = Math.min(config.immortalNativesPerPlayer - near(player, NEAR_RANGE), config.immortalNativeWorldCap - alive());
		if (room <= 0) {
			return;
		}
		RandomSource random = level.getRandom();
		int group = Math.min(room, 1 + random.nextInt(2));
		Vec3 spot = groundSpot(level, player, random);
		if (spot == null) {
			return;
		}
		int realm = Abadi.effectiveRealm(player);
		boolean rogue = random.nextDouble() < ROGUE_SHARE;
		for (int i = 0; i < group; i++) {
			Vec3 at = spot.add(random.nextGaussian() * 1.5D, 0.0D, random.nextGaussian() * 1.5D);
			if (rogue) {
				spawnRogue(level, at, 2 + random.nextInt(2), realm);
			} else {
				String kind = KINDS.get(random.nextInt(4));
				spawn(level, at, kind, defaultTier(kind, random), realm);
			}
		}
	}

	/** A spot 24-40 blocks from the player on solid ground in an entity-ticking chunk (never a void column), or null. */
	private static @Nullable Vec3 groundSpot(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			if (!level.isPositionEntityTicking(new BlockPos(x, (int) player.getY(), z))) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (top <= level.getMinY() + 1) {
				continue;
			}
			return new Vec3(x + 0.5D, top, z + 0.5D);
		}
		return null;
	}

	private static int defaultTier(String kind, RandomSource random) {
		return switch (kind) {
			case "babi" -> 2 + random.nextInt(2);
			case "penjaga" -> 3;
			case "liar" -> 2 + random.nextInt(2);
			default -> 2;
		};
	}

	/** Spawns one native of {@code kind} near {@code pos} ({@code liar} = a rogue cultivator); null when refused. */
	static @Nullable Mob spawn(ServerLevel level, Vec3 pos, String kind, int tier, int realm) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return null;
		}
		if ("liar".equals(kind)) {
			return spawnRogue(level, pos, tier, realm);
		}
		EntityType<? extends Mob> type;
		Element element;
		switch (kind) {
			case "tulang" -> {
				type = EntityTypes.STRAY;
				element = Element.ICE;
			}
			case "babi" -> {
				type = EntityTypes.HOGLIN;
				element = Element.EARTH;
			}
			case "penjaga" -> {
				type = EntityTypes.WITHER_SKELETON;
				element = Element.METAL;
			}
			default -> {
				type = EntityTypes.SPIDER;
				element = Element.WIND;
			}
		}
		if (!level.isPositionEntityTicking(BlockPos.containing(pos))) {
			return null;
		}
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, mob, pos);
		if (spot == null) {
			return null;
		}
		RandomSource random = level.getRandom();
		mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
		mob.setCanPickUpLoot(false);
		mob.addTag(TAG);
		mob.addTag(TechniquePower.TAG_ABADI_FOE);
		mob.addTag(NoGrief.TAG);
		if (mob instanceof Hoglin hoglin) {
			hoglin.setImmuneToZombification(true);
		}
		if (type == EntityTypes.STRAY) {
			// The day timeline burns undead: an unbreakable helmet that never drops shields it (DESIGN 26.1).
			ItemStack helmet = new ItemStack(Items.IRON_HELMET);
			helmet.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
			mob.setItemSlot(EquipmentSlot.HEAD, helmet);
			mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
		}
		if (type == EntityTypes.WITHER_SKELETON) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
			mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		}
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		int clampedTier = Math.max(2, Math.min(3, tier));
		EliteMonsters.makeElite(mob, clampedTier, element, Math.min(realm, Realm.values().length - 1));
		// The elite stats first, then our own targets on top (HP and melee of DESIGN 26.3.6).
		double health = BASE_HEALTH * TIER_HEALTH[clampedTier] * (1.0D + HEALTH_PER_REALM * realm);
		settle(mob, Attributes.MAX_HEALTH, HEALTH_ID, health);
		settle(mob, Attributes.ATTACK_DAMAGE, ATTACK_ID, 4.0D + realm + 1.5D * clampedTier);
		mob.setHealth(mob.getMaxHealth());
		TRACKED.put(mob.getUUID(), new Tracked(mob, clampedTier, level.getGameTime()));
		return mob;
	}

	/** Adds a modifier {@code id} so the attribute ends at {@code target}. */
	private static void settle(Mob mob, Holder<Attribute> attribute, Identifier id, double target) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(id);
		double delta = target - instance.getValue();
		if (Double.isFinite(delta) && Math.abs(delta) > 1.0E-6D) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, delta, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	private static @Nullable Mob spawnRogue(ServerLevel level, Vec3 pos, int tier, int realm) {
		Mob mob = RogueCultivators.spawn(level, pos, Math.max(2, Math.min(3, tier)), realm, RogueCultivators.VARIANT_ROGUE, RogueCultivators.ROLE_QUEST, null);
		if (mob == null) {
			return null;
		}
		mob.addTag(TAG);
		mob.addTag(TechniquePower.TAG_ABADI_FOE);
		TRACKED.put(mob.getUUID(), new Tracked(mob, RogueCultivators.tier(mob), level.getGameTime()));
		return mob;
	}

	/** Removes every native within {@code range} of the player; returns how many. */
	static int clearNear(ServerPlayer player, double range) {
		int n = 0;
		for (Tracked tracked : List.copyOf(TRACKED.values())) {
			Mob mob = tracked.mob;
			if (!mob.isRemoved() && mob.level() == player.level() && mob.distanceToSqr(player) <= range * range) {
				mob.discard();
				TRACKED.remove(mob.getUUID());
				n++;
			}
		}
		return n;
	}

	// ================================================================== rewards

	private static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!victim.entityTags().contains(TAG) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		Tracked tracked = TRACKED.remove(victim.getUUID());
		KillCredit.Credit credit = KillCredit.resolve(victim, source);
		if (credit == null || credit.owner() instanceof FakePlayer) {
			return;
		}
		ServerPlayer owner = credit.owner();
		Abadi.activity(owner, Abadi.ACT_NATIVE_KILL);
		int tier = tracked != null ? tracked.tier : Math.max(EliteMonsters.tier(victim), RogueCultivators.tier(victim));
		RandomSource random = level.getRandom();
		Vec3 pos = victim.position();
		if (random.nextDouble() < STONE_CHANCE) {
			int count = tier >= 3 ? 2 + random.nextInt(3) : 1 + random.nextInt(3);
			drop(level, pos, new ItemStack(AbadiItems.IMMORTAL_ORIGIN_STONE, count));
			Advancements.grant(owner, "origin_stone");
		}
		if (random.nextDouble() < HERB_CHANCE) {
			drop(level, pos, SpiritHerbs.randomFor(EliteMonsters.element(victim), random, 500, 1500));
		}
		if (random.nextDouble() < SLIP_CHANCE) {
			drop(level, pos, dewaSlip(random));
		}
		if (credit.kind() != KillCredit.Kind.DISCIPLE) {
			double combat = XpSources.combatXp(victim);
			double xp = Math.max(combat * XP_COMBAT, XP_BAR * Stars.barSize(owner));
			double extra = credit.kind() == KillCredit.Kind.PLAYER ? Math.max(0.0D, xp - combat) : xp;
			if (extra > 0.0D) {
				XpSources.showKillGain(owner, Progression.grantXp(owner, extra * BattleFatigue.factor(owner), XpSource.COMBAT));
			}
			BattleFatigue.set(owner, BattleFatigue.saturation(owner) + EXTRA_FATIGUE);
		}
	}

	/** A jade slip of a random slip-allowed Dewa-band spec (the Dewa gate rules stay with the slip); empty when none exists. */
	static ItemStack dewaSlip(RandomSource random) {
		if (!TechniqueRegistry.frozen()) {
			return ItemStack.EMPTY;
		}
		List<TechniqueSpec> pool = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.ofGrade(TechniqueGrade.DEWA)) {
			if (spec.slipAllowed()) {
				pool.add(spec);
			}
		}
		if (pool.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return JadeSlips.stack(pool.get(random.nextInt(pool.size())).id());
	}

	private static void drop(ServerLevel level, Vec3 pos, @Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(level, pos.x, pos.y + 0.3D, pos.z, stack);
		item.setDefaultPickUpDelay();
		item.addTag(NoGrief.TAG);
		level.addFreshEntity(item);
	}

	private Natives() {
	}
}
