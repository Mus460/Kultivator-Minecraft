package dev.kultivator.musuh;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.XpSources;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.elite.BattleFatigue;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.KillCredit;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The public API of the rogue cultivators (DESIGN 25.4.2, 25.15, owned by P8-musuh): the entity type
 * {@code kultivator:rogue_cultivator}, spawning inside the world cap ({@code rogueWorldCap}), bands with a cleared callback,
 * the tracked set the skill pass walks, the despawn rules (quest and test rogues: no player within 64 blocks for 30 s;
 * camp rogues: none within 48 for 2 minutes), the Patriarch's boss bar and the kill rewards (XP, Battle Fatigue, Kantong
 * Rampasan, keys, counters and advancements, through {@link KillCredit}).
 */
public final class RogueCultivators {

	/** Variants (synced data). */
	public static final int VARIANT_ROGUE = 0;
	public static final int VARIANT_GHOST = 1;
	public static final int VARIANT_ENERGY = 2;
	public static final int VARIANT_DEMONIC = 3;
	/** Roles (synced data). */
	public static final int ROLE_AMBUSH = 0;
	public static final int ROLE_RAID = 1;
	public static final int ROLE_CAMP = 2;
	public static final int ROLE_QUEST = 3;
	public static final int ROLE_REALM = 4;
	public static final int ROLE_TEST = 5;

	/** XP by tier (index 1-4): max(combatXp × these, bar × {@link #XP_BAR} × realm gap), DESIGN 25.4.2. */
	public static final double[] XP_COMBAT = {1.0D, 3.0D, 5.0D, 8.0D, 12.0D};
	public static final double[] XP_BAR = {0.0D, 0.01D, 0.025D, 0.05D, 0.08D};
	public static final double[] DISCIPLE_XP = {0.0D, 2.0D, 4.0D, 7.0D, 10.0D};
	/** Battle Fatigue per rogue is 3: the kill itself already adds 1. */
	public static final double EXTRA_FATIGUE = 2.0D;
	public static final double POUCH_TIER1 = 0.40D;
	public static final double FRAGMENT_TIER3 = 0.20D;
	public static final double FRAGMENT_TIER4 = 0.60D;
	public static final double CRYSTAL_TIER4 = 0.25D;
	/** Despawn when no player is within this range for {@link #LONELY_TICKS}. */
	public static final double LONELY_RANGE = 64.0D;
	public static final int LONELY_TICKS = 600;
	/** Players within this range see a Patriarch's boss bar. */
	public static final double BAR_RANGE = 32.0D;

	private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, Kultivator.id("rogue_cultivator"));

	/** {@code kultivator:rogue_cultivator}: never saved, cannot be summoned, no loot table. */
	public static final EntityType<RogueCultivatorEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
			EntityType.Builder.of(RogueCultivatorEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.noSave()
					.noSummon()
					.noLootTable()
					.clientTrackingRange(10)
					.build(KEY));

	/** A live rogue and its per-mob state (skills, stuck check, band). */
	static final class Tracked {
		final RogueCultivatorEntity mob;
		@Nullable Band band;
		long lonelySince;
		double bestDistance = Double.MAX_VALUE;
		long progressAt;
		long nextLine;
		long nextBurst;
		long nextMark;
		long nextSeal;
		/** The Patriarch's boss bar (tier 4 only). */
		@Nullable ServerBossEvent bar;

		Tracked(RogueCultivatorEntity mob, long now) {
			this.mob = mob;
			this.lonelySince = now;
			this.progressAt = now;
			this.nextLine = now + 40L + mob.getRandom().nextInt(40);
			this.nextBurst = now + 100L + mob.getRandom().nextInt(60);
			this.nextMark = now + 120L + mob.getRandom().nextInt(60);
			this.nextSeal = now + 160L;
		}
	}

	/** Rogues spawned together; {@link #onCleared} is told once when the last one is gone. */
	static final class Band {
		final Set<UUID> members = new HashSet<>();
		final List<RogueCultivatorEntity> mobs = new ArrayList<>();
		final Consumer<Boolean> onCleared;
		boolean failed;
		boolean done;

		Band(Consumer<Boolean> onCleared) {
			this.onCleared = onCleared;
		}

		int alive() {
			int n = 0;
			for (RogueCultivatorEntity mob : mobs) {
				if (!mob.isRemoved() && mob.isAlive()) {
					n++;
				}
			}
			return n;
		}

		/** Removes every member that is still there (despawn: the band fails). */
		void dismiss() {
			for (RogueCultivatorEntity mob : List.copyOf(mobs)) {
				if (!mob.isRemoved()) {
					mob.vanish(false);
				}
			}
		}
	}

	private static final Map<UUID, Tracked> TRACKED = new LinkedHashMap<>();

	/** Registers the attributes, the load refusal, the kill rewards and the cleanup; called from {@link Musuh#init()}. */
	static void init() {
		FabricDefaultAttributeRegistry.register(TYPE, RogueCultivatorEntity.createAttributes());
		// Never back from disk (the type is noSave; this is the safety net of DESIGN 25.4.2).
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD
				|| !(entity instanceof RogueCultivatorEntity) && !entity.entityTags().contains(TechniquePower.TAG_ROGUE));
		ServerLivingEntityEvents.AFTER_DEATH.register(RogueCultivators::afterDeath);
		DiscipleHooks.addOnKill(RogueCultivators::discipleKill);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			for (Tracked tracked : TRACKED.values()) {
				if (tracked.bar != null) {
					tracked.bar.removeAllPlayers();
				}
			}
			TRACKED.clear();
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			for (Tracked tracked : TRACKED.values()) {
				if (tracked.bar != null) {
					tracked.bar.removePlayer(player);
				}
			}
		});
	}

	// ================================================================== public API (DESIGN 25.15)

	/** True for a rogue cultivator (any tier, the Patriarch too). */
	public static boolean isRogue(@Nullable Entity entity) {
		return entity != null && entity.entityTags().contains(TechniquePower.TAG_ROGUE);
	}

	/** The rogue tier 1-4, 0 for anything else. */
	public static int tier(@Nullable Entity entity) {
		return entity instanceof RogueCultivatorEntity rogue ? rogue.tier() : 0;
	}

	/**
	 * Spawns one rogue of {@code tier} (1-4) at realm ordinal {@code realm} with {@code variant} and {@code role} on a safe
	 * spot near {@code pos} in an entity-ticking chunk, inside the world cap. Null when refused (cap, Peaceful, no spot).
	 */
	public static @Nullable Mob spawn(ServerLevel level, Vec3 pos, int tier, int realm, int variant, int role, @Nullable BlockPos home) {
		return spawnRogue(level, pos, tier, realm, variant, role, home, -1);
	}

	/**
	 * Spawns a band (one rogue per entry of {@code tiers}) around {@code center}; {@code onCleared} is told once when the
	 * band is gone: true when all were slain or driven off, false when one despawned (no player near, chunk unloaded, its
	 * event ended). Returns how many spawned (0: nothing, and {@code onCleared} is not called). One sect for the band.
	 */
	public static int spawnBand(ServerLevel level, Vec3 center, int[] tiers, int realm, int role, @Nullable BlockPos home, Consumer<Boolean> onCleared) {
		Band band = band(level, center, tiers, realm, role, home, -1, VARIANT_ROGUE, onCleared);
		return band == null ? 0 : band.mobs.size();
	}

	/** How many rogues are alive in the world (the cap counts these). */
	public static int alive() {
		int n = 0;
		for (Tracked tracked : TRACKED.values()) {
			if (!tracked.mob.isRemoved() && tracked.mob.isAlive()) {
				n++;
			}
		}
		return n;
	}

	// ================================================================== spawning

	static @Nullable RogueCultivatorEntity spawnRogue(ServerLevel level, Vec3 pos, int tier, int realm, int variant, int role, @Nullable BlockPos home,
			int sect) {
		if (level.getDifficulty() == Difficulty.PEACEFUL || alive() >= KultivatorConfig.get().rogueWorldCap) {
			return null;
		}
		if (!level.isPositionEntityTicking(BlockPos.containing(pos))) {
			return null;
		}
		RogueCultivatorEntity mob = TYPE.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		RandomSource random = level.getRandom();
		Vec3 spot = Targeting.safeSpot(level, mob, pos);
		for (int attempt = 0; spot == null && attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			Vec3 near = pos.add(Math.cos(angle) * 2.0D, 0.0D, Math.sin(angle) * 2.0D);
			if (level.isPositionEntityTicking(BlockPos.containing(near))) {
				spot = Targeting.safeSpot(level, mob, near);
			}
		}
		if (spot == null) {
			return null;
		}
		mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
		int chosenSect = sect >= 0 ? sect : random.nextInt(RogueCultivatorEntity.SECTS);
		mob.setup(tier, realm, random.nextInt(4), variant, chosenSect, role, home, random.nextInt(9), random.nextBoolean());
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		TRACKED.put(mob.getUUID(), new Tracked(mob, level.getGameTime()));
		Fx.send(level, mob.position(), FxType.MUSUH, MusuhFxIds.ARRIVE, mob.element().color(), mob.tier());
		return mob;
	}

	/** A band with one sect (random when {@code sect} is negative); null when none could spawn. */
	static @Nullable Band band(ServerLevel level, Vec3 center, int[] tiers, int realm, int role, @Nullable BlockPos home, int sect, int variant,
			Consumer<Boolean> onCleared) {
		if (tiers == null || tiers.length == 0) {
			return null;
		}
		RandomSource random = level.getRandom();
		int chosenSect = sect >= 0 ? sect : random.nextInt(RogueCultivatorEntity.SECTS);
		Band band = new Band(onCleared == null ? cleared -> {
		} : onCleared);
		double base = random.nextDouble() * Math.PI * 2.0D;
		for (int i = 0; i < tiers.length; i++) {
			double angle = base + Math.PI * 2.0D * i / tiers.length;
			double distance = tiers.length == 1 ? 0.0D : 1.5D + 0.5D * i;
			Vec3 pos = center.add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
			RogueCultivatorEntity mob = spawnRogue(level, pos, tiers[i], realm, variant, role, home, chosenSect);
			if (mob == null) {
				continue;
			}
			band.members.add(mob.getUUID());
			band.mobs.add(mob);
			Tracked tracked = TRACKED.get(mob.getUUID());
			if (tracked != null) {
				tracked.band = band;
			}
		}
		return band.mobs.isEmpty() ? null : band;
	}

	// ================================================================== tracking

	static @Nullable Tracked trackedOf(RogueCultivatorEntity mob) {
		return TRACKED.get(mob.getUUID());
	}

	/** The tracked rogues (read-only use: the skill pass and commands). */
	static Iterable<Tracked> tracked() {
		return TRACKED.values();
	}

	/** Every live rogue in {@code level} within {@code range} of {@code pos}. */
	static List<RogueCultivatorEntity> near(ServerLevel level, Vec3 pos, double range) {
		List<RogueCultivatorEntity> list = new ArrayList<>();
		for (Tracked tracked : TRACKED.values()) {
			RogueCultivatorEntity mob = tracked.mob;
			if (!mob.isRemoved() && mob.level() == level && mob.position().distanceToSqr(pos) <= range * range) {
				list.add(mob);
			}
		}
		return list;
	}

	/**
	 * Every 10 ticks: forgets removed rogues (telling their band: a despawn fails it, a kill or a retreat does not) and
	 * applies the lonely despawn to roles whose event does not handle it (ambush, quest, test).
	 */
	static void tick(MinecraftServer server, long tick) {
		if (tick % 10L != 0L || TRACKED.isEmpty()) {
			return;
		}
		List<Band> finished = new ArrayList<>();
		Iterator<Tracked> iterator = TRACKED.values().iterator();
		while (iterator.hasNext()) {
			Tracked tracked = iterator.next();
			RogueCultivatorEntity mob = tracked.mob;
			if (mob.isRemoved() || mob.isDeadOrDying()) {
				iterator.remove();
				if (tracked.bar != null) {
					tracked.bar.removeAllPlayers();
					tracked.bar = null;
				}
				Band band = tracked.band;
				if (band != null && !band.done) {
					boolean killed = mob.getRemovalReason() == Entity.RemovalReason.KILLED || mob.isDeadOrDying();
					if (!killed && !mob.fled()) {
						band.failed = true;
					}
					band.members.remove(mob.getUUID());
					if (band.members.isEmpty()) {
						band.done = true;
						finished.add(band);
					}
				}
				continue;
			}
			if (mob.tier() >= 4) {
				updateBar(tracked);
			}
			int role = mob.role();
			// Raids, realms and ambushes end their rogues themselves (an ambush after 30 s beyond 96 blocks); a camp's band
			// leaves after 2 minutes with no player within 48 blocks (DESIGN 25.4.4), quest and test rogues after 30 s beyond 64.
			if (role == ROLE_RAID || role == ROLE_REALM || role == ROLE_AMBUSH || !(mob.level() instanceof ServerLevel level)) {
				continue;
			}
			boolean camp = role == ROLE_CAMP;
			double range = camp ? Camps.TRIGGER_RANGE : LONELY_RANGE;
			long now = level.getGameTime();
			if (level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), range, entity -> !entity.isSpectator()) != null) {
				tracked.lonelySince = now;
			} else if (now - tracked.lonelySince >= (camp ? Camps.EMPTY_TICKS : LONELY_TICKS)) {
				mob.vanish(false);
			}
		}
		for (Band band : finished) {
			try {
				band.onCleared.accept(!band.failed);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A rogue band callback failed", exception);
			}
		}
	}

	/** The Patriarch's boss bar for players within {@link #BAR_RANGE} (DESIGN 25.17 item 11). */
	private static void updateBar(Tracked tracked) {
		RogueCultivatorEntity mob = tracked.mob;
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (tracked.bar == null) {
			tracked.bar = new ServerBossEvent(UUID.randomUUID(), mob.plate(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
		}
		ServerBossEvent bar = tracked.bar;
		bar.setProgress(Mth.clamp(mob.getHealth() / Math.max(1.0F, mob.getMaxHealth()), 0.0F, 1.0F));
		for (ServerPlayer shown : List.copyOf(bar.getPlayers())) {
			if (shown.level() != level || shown.distanceToSqr(mob) > BAR_RANGE * BAR_RANGE) {
				bar.removePlayer(shown);
			}
		}
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(mob) <= BAR_RANGE * BAR_RANGE) {
				bar.addPlayer(player);
			}
		}
	}

	// ================================================================== rewards (DESIGN 25.4.2)

	private static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof RogueCultivatorEntity rogue) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		SectRaids.onRogueDeath(rogue);
		KillCredit.Credit credit = KillCredit.resolve(victim, source);
		if (credit == null || credit.owner() instanceof FakePlayer) {
			return;
		}
		ServerPlayer owner = credit.owner();
		int tier = rogue.tier();
		boolean realmRun = rogue.role() == ROLE_REALM;
		Vec3 pos = victim.position();
		RandomSource random = level.getRandom();
		if (!realmRun) {
			if (tier >= 2 || random.nextDouble() < POUCH_TIER1) {
				drop(level, pos, LootPouches.pouch(tier, rogue.realm(), rogue.sect()));
			}
			if (tier == 3 && random.nextDouble() < FRAGMENT_TIER3) {
				drop(level, pos, byId("heavenvoid_fragment"));
			}
			if (tier >= 4) {
				if (random.nextDouble() < FRAGMENT_TIER4) {
					drop(level, pos, byId("heavenvoid_fragment"));
				}
				if (random.nextDouble() < CRYSTAL_TIER4) {
					drop(level, pos, byId("inheritance_crystal"));
				}
				if (rogue.realm() >= Realm.IMMORTAL_ASCENSION.ordinal()) {
					drop(level, pos, byId("tomb_sigil"));
				}
			}
		}
		if (credit.kind() != KillCredit.Kind.DISCIPLE) {
			double combat = XpSources.combatXp(victim);
			double xp = rogueXp(owner, rogue, combat);
			// A player's own blow was already paid combatXp by XpSources: only the difference is added.
			double extra = credit.kind() == KillCredit.Kind.PLAYER ? Math.max(0.0D, xp - combat) : xp;
			double fatigue = BattleFatigue.factor(owner);
			if (extra > 0.0D) {
				XpSources.showKillGain(owner, Progression.grantXp(owner, extra * fatigue, XpSource.COMBAT));
			}
			BattleFatigue.set(owner, BattleFatigue.saturation(owner) + EXTRA_FATIGUE);
		}
		count(owner, tier);
	}

	/** max(combatXp × {3, 5, 8, 12}, bar × {1%, 2.5%, 5%, 8%} × clamp(1 + 0.5 × (R − killer realm), 0.25, 2)). */
	static double rogueXp(ServerPlayer killer, RogueCultivatorEntity rogue, double combat) {
		CultivationData data = Cultivators.get(killer);
		int tier = rogue.tier();
		double gap = Mth.clamp(1.0D + 0.5D * (rogue.realm() - data.realm().ordinal()), 0.25D, 2.0D);
		double bar = data.rank().xpRequired() * XP_BAR[tier] * gap;
		return Math.max(combat * XP_COMBAT[tier], bar);
	}

	/** A disciple's kill: the disciple's extra XP (its master gets the counters through {@link #afterDeath}). */
	private static void discipleKill(DiscipleEntity disciple, LivingEntity victim) {
		if (!(victim instanceof RogueCultivatorEntity rogue)) {
			return;
		}
		double extra = XpSources.combatXp(victim) * DiscipleCultivation.KILL_XP * DISCIPLE_XP[rogue.tier()];
		if (extra > 0.0D) {
			DiscipleCultivation.addXp(disciple, extra, XpSource.COMBAT);
		}
	}

	/** {@code extra.dunia.rogue_kills} and the advancements (also for the master of a disciple's kill). */
	private static void count(ServerPlayer player, int tier) {
		CompoundTag dunia = Musuh.dunia(Cultivators.get(player));
		dunia.putInt("rogue_kills", dunia.getIntOr("rogue_kills", 0) + 1);
		Cultivators.dirty(player);
		Advancements.grant(player, "rogue_slain");
		if (tier >= 4) {
			Advancements.grant(player, "demonic_patriarch");
		}
	}

	/** An item of another module by id (empty while it is not registered). */
	static ItemStack byId(String path) {
		Item item = BuiltInRegistries.ITEM.getValue(Kultivator.id(path));
		return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
	}

	/** A block-safe item entity at {@code pos}. */
	static void drop(ServerLevel level, Vec3 pos, @Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(level, pos.x, pos.y + 0.3D, pos.z, stack);
		item.setDefaultPickUpDelay();
		item.addTag(NoGrief.TAG);
		item.setPortalCooldown();
		level.addFreshEntity(item);
	}

	private RogueCultivators() {
	}
}
