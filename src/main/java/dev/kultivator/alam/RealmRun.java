package dev.kultivator.alam;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One live run of a secret realm (DESIGN 25.3.3-25.3.10), session only and never saved. It hosts the party on a leased slot
 * of {@code kultivator:alam_rahasia} (or around the gate in Mode Ringan), plays the chambers one after another, keeps the
 * boss bar, tracks every run entity by UUID (discarded at the end), pushes players and mobs out of the wall grid, and at the
 * end returns the party, sweeps dropped items to their owners and sends the disciples home through {@code depart/arrive}.
 * Departed disciples wait in the owner's persistent {@code extra.dunia.alam_home} list until they arrive, so a crash never
 * loses one. No block is changed.
 */
final class RealmRun {

	static final String RUN_TAG = "kultivator.alam_run";
	static final String GUARD_TAG = "kultivator.realm_guard";
	static final String HOME_KEY = "alam_home";
	static final double SOFT_EDGE = 32.0D;
	static final double HARD_EDGE = 48.0D;
	static final int LOADING_MAX = 200;
	static final int FADE_TICKS = 20;
	static final int RETURN_DELAY = 100;
	static final int CHOICE_TICKS = 60 * 20;
	static final int LIGHT_OUTSIDE_MAX = 100;
	static final double GUARDIAN_PER_MEMBER = 0.25D;
	static final double DISCIPLE_SHARE = 0.15D;
	static final int DISCIPLE_PK = 20;
	static final int LUKA_TICKS = 5 * 60 * 20;
	static final double SWEEP_RADIUS = 80.0D;

	private static final net.minecraft.resources.Identifier GUARDIAN_SCALE = Kultivator.id("realm_guardian_scale");
	private static final net.minecraft.resources.Identifier GHOST_HEALTH = Kultivator.id("realm_ghost_health");
	private static final net.minecraft.resources.Identifier ELITE_HEALTH = Kultivator.id("elite_health");
	private static final net.minecraft.resources.Identifier ELITE_ATTACK = Kultivator.id("elite_attack");

	enum State { LOADING, PLAY, PAD, FADE, DONE, ENDED }

	enum Outcome { SUCCESS, FAIL, VOLUNTARY, LEFT }

	/** A treasure choice waiting for the player's pick (1 of 3). */
	record Choice(List<ItemStack> options, long expires) {
	}

	static final List<RealmRun> RUNS = new ArrayList<>();
	static final Map<UUID, RealmRun> BY_PLAYER = new HashMap<>();
	static final Map<UUID, Choice> CHOICES = new HashMap<>();
	/** True while the run itself deals damage (lava jets, phantom exits): Mode Ringan's REALM guard covers it. */
	static boolean hazard;

	final UUID owner;
	final RealmSpec spec;
	final boolean light;
	final ServerLevel level;
	final BlockPos origin;
	final int slot;
	final boolean free;
	final List<UUID> players = new ArrayList<>();
	/** Live disciple bodies of the party inside the realm (dimension mode). */
	final Set<UUID> disciples = new HashSet<>();
	/** Souls of departed disciples that still have to arrive on the pad. */
	final Set<UUID> incoming = new HashSet<>();
	final Set<UUID> entities = new HashSet<>();
	final Map<UUID, Vec3> lastValid = new HashMap<>();
	final Map<UUID, Integer> outside = new HashMap<>();
	final Map<UUID, List<ItemStack>> overflow = new HashMap<>();
	final Map<UUID, List<ItemStack>> shown = new HashMap<>();
	final Set<UUID> lukaFor = new HashSet<>();
	final ServerBossEvent bar;
	final RandomSource random;
	final int rBase;
	final int stars;
	int extra;
	final List<String> plan;
	final int startLayer;
	int room = -1;
	@Nullable Chambers.Chamber chamber;
	State state = State.LOADING;
	long stateAt;
	long stateUntil;
	long deadline;
	long seed;
	boolean bonus;
	int crystals;
	int halls;
	@Nullable UUID padId;

	private RealmRun(UUID owner, RealmSpec spec, boolean light, ServerLevel level, BlockPos origin, int slot, boolean free, int rBase, int stars,
			int startLayer) {
		this.owner = owner;
		this.spec = spec;
		this.light = light;
		this.level = level;
		this.origin = origin;
		this.slot = slot;
		this.free = free;
		this.rBase = rBase;
		this.stars = stars;
		this.startLayer = startLayer;
		this.random = RandomSource.create(level.getRandom().nextLong());
		this.seed = random.nextLong();
		this.plan = Chambers.plan(spec, random, startLayer, spec.endless() ? 3 : 0);
		this.bar = new ServerBossEvent(Mth.createInsecureUUID(random), spec.displayName(), barColor(spec), BossEvent.BossBarOverlay.PROGRESS);
	}

	private static BossEvent.BossBarColor barColor(RealmSpec spec) {
		return switch (spec.ordinal()) {
			case 1 -> BossEvent.BossBarColor.RED;
			case 2 -> BossEvent.BossBarColor.BLUE;
			case 3 -> BossEvent.BossBarColor.WHITE;
			default -> BossEvent.BossBarColor.PURPLE;
		};
	}

	// ================================================================== starting

	/**
	 * Starts a run for {@code ownerPlayer} with the accepted sect mates and up to 2 disciples. Dimension mode leases a slot,
	 * saves each player's return point and teleports the party onto the pad; Mode Ringan plays around the gate. Null (with a
	 * message) when no slot is free.
	 */
	static @Nullable RealmRun start(ServerPlayer ownerPlayer, RealmSpec spec, List<ServerPlayer> mates, List<DiscipleEntity> party, boolean light,
			int startLayer, boolean free) {
		MinecraftServer server = ownerPlayer.level().getServer();
		ServerLevel realm = server.getLevel(Alam.REALM_LEVEL);
		boolean useLight = light || realm == null;
		int slot = -1;
		ServerLevel level;
		BlockPos origin;
		if (useLight) {
			level = ownerPlayer.level();
			origin = ownerPlayer.blockPosition();
		} else {
			slot = Slots.lease();
			if (slot < 0) {
				Msg.chat(ownerPlayer, Component.translatable("kultivator.alam.why.slots").withStyle(ChatFormatting.GRAY));
				return null;
			}
			level = realm;
			origin = Slots.centre(slot);
		}
		List<ServerPlayer> all = new ArrayList<>();
		all.add(ownerPlayer);
		for (ServerPlayer mate : mates) {
			if (mate != ownerPlayer && !all.contains(mate) && !BY_PLAYER.containsKey(mate.getUUID())) {
				all.add(mate);
			}
		}
		int strongest = 0;
		for (ServerPlayer each : all) {
			strongest = Math.max(strongest, Cultivators.get(each).realm().ordinal());
		}
		for (DiscipleEntity disciple : party) {
			strongest = Math.max(strongest, disciple.data().realm().ordinal());
		}
		int rBase = Math.max(spec.minR(), Math.min(spec.maxR(), strongest));
		int stars = dev.kultivator.cultivation.ImmortalPerks.stars(Cultivators.get(ownerPlayer));
		RealmRun run = new RealmRun(ownerPlayer.getUUID(), spec, useLight, level, origin, slot, free, rBase, stars, Math.max(1, startLayer));
		long now = level.getServer().overworld().getGameTime();
		run.stateAt = now;
		run.deadline = now + spec.timeTicks() + LOADING_MAX;
		for (ServerPlayer each : all) {
			run.players.add(each.getUUID());
			BY_PLAYER.put(each.getUUID(), run);
			run.bar.addPlayer(each);
		}
		RUNS.add(run);
		if (!useLight) {
			Vec3 pad = run.world(0, -10);
			int i = 0;
			for (ServerPlayer each : all) {
				ReturnPoint.save(each);
				double ox = (i % 2 == 0 ? 1 : -1) * (i / 2) * 1.5D;
				each.teleport(new TeleportTransition(level, pad.add(ox, 0.0D, 0.0D), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
				each.setPortalCooldown();
				i++;
			}
			for (DiscipleEntity disciple : party) {
				CompoundTag tag = Disciples.depart(disciple, "alam");
				if (!tag.isEmpty()) {
					pushHome(ownerPlayer, tag);
					tag.read("soul", UUIDUtil.CODEC).ifPresent(run.incoming::add);
				}
			}
		} else {
			for (DiscipleEntity disciple : party) {
				run.disciples.add(disciple.getUUID());
			}
		}
		run.extra = all.size() - 1 + party.size();
		return run;
	}

	// ================================================================== accessors used by the chambers

	RealmSpec spec() {
		return spec;
	}

	ServerLevel level() {
		return level;
	}

	RandomSource random() {
		return random;
	}

	long seed() {
		return seed;
	}

	long now() {
		return level.getServer().overworld().getGameTime();
	}

	/** The Makam layer being played (realm 4), else 0. */
	int layer() {
		return spec.endless() ? startLayer + Math.max(0, room) : 0;
	}

	/** R of DESIGN 25.3.5 (realm 4 adds {@code layer/9 + stars/9}). */
	int r() {
		return spec.endless() ? rBase + layer() / 9 + stars / 9 : rBase;
	}

	/** The centre of local cell (lx, lz) on the stage floor. */
	Vec3 world(int lx, int lz) {
		return new Vec3(origin.getX() + lx + 0.5D, origin.getY(), origin.getZ() + lz + 0.5D);
	}

	int localX(Entity entity) {
		return Mth.floor(entity.getX()) - origin.getX();
	}

	int localZ(Entity entity) {
		return Mth.floor(entity.getZ()) - origin.getZ();
	}

	/** The online party players standing in the run's level. */
	List<ServerPlayer> players() {
		List<ServerPlayer> list = new ArrayList<>(players.size());
		for (UUID id : players) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null && player.level() == level && player.isAlive()) {
				list.add(player);
			}
		}
		return list;
	}

	@Nullable Entity entity(UUID id) {
		return level.getEntity(id);
	}

	boolean alive(UUID id) {
		Entity entity = level.getEntity(id);
		return entity != null && entity.isAlive() && !entity.isRemoved();
	}

	int liveMobs() {
		int n = 0;
		for (UUID id : entities) {
			if (level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
				n++;
			}
		}
		return n;
	}

	/** True for a party player (not creative or spectator) or a party disciple. */
	boolean isPartyMember(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			return players.contains(player.getUUID()) && !player.isCreative() && !player.isSpectator();
		}
		if (entity instanceof DiscipleEntity disciple) {
			return disciples.contains(disciple.getUUID()) || (light && disciple.ownerId() != null && players.contains(disciple.ownerId()));
		}
		return false;
	}

	@Nullable ServerPlayer nearestPlayer(Vec3 at) {
		ServerPlayer best = null;
		double bestSq = Double.MAX_VALUE;
		for (ServerPlayer player : players()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			double sq = player.position().distanceToSqr(at);
			if (sq < bestSq) {
				bestSq = sq;
				best = player;
			}
		}
		return best;
	}

	void message(Component text) {
		for (ServerPlayer player : players()) {
			Msg.chat(player, text);
		}
	}

	void setBonus() {
		bonus = true;
	}

	/** Damage made by the run itself (jets, phantom exits): magic, so armour and Fire Resistance do not void it. */
	void hurtHazard(ServerPlayer player, float amount) {
		if (!(amount > 0.0F)) {
			return;
		}
		hazard = true;
		try {
			player.hurtServer(level, level.damageSources().magic(), amount);
		} finally {
			hazard = false;
		}
	}

	void toEntry(ServerPlayer player) {
		if (chamber == null) {
			return;
		}
		Vec3 at = world(chamber.entry[0], chamber.entry[1]);
		if (light) {
			Vec3 spot = Targeting.safeSpot(level, player, at);
			if (spot != null) {
				at = spot;
			}
		}
		player.teleportTo(at.x, at.y, at.z);
		player.setDeltaMovement(Vec3.ZERO);
		player.syncVelocity = true;
		lastValid.put(player.getUUID(), at);
	}

	void partyToEntry() {
		for (ServerPlayer player : players()) {
			toEntry(player);
		}
		if (chamber == null) {
			return;
		}
		Vec3 at = world(chamber.entry[0], chamber.entry[1]);
		for (UUID id : disciples) {
			Entity body = level.getEntity(id);
			if (body != null && !light) {
				body.teleportTo(at.x + 1.0D, at.y, at.z);
			}
		}
	}

	// ================================================================== spawning

	private boolean canSpawnAt(Vec3 at) {
		return level.areEntitiesActuallyLoadedAndTicking(ChunkPos.containing(BlockPos.containing(at)));
	}

	private void claim(Entity entity) {
		entity.addTag(RUN_TAG);
		entities.add(entity.getUUID());
	}

	private void aim(Mob mob) {
		ServerPlayer target = nearestPlayer(mob.position());
		if (target != null) {
			mob.setTarget(target);
		}
	}

	/** A chamber guard: an allowlisted vanilla mob made elite through {@code EliteMonsters.makeElite} (DESIGN 25.3.5). */
	@Nullable Mob spawnGuard(EntityType<? extends Mob> type, int tier, @Nullable Element element, int lx, int lz) {
		Vec3 at = world(lx, lz);
		if (liveMobs() >= Chambers.MAX_LIVE || !canSpawnAt(at)) {
			return null;
		}
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, mob, at);
		if (spot == null) {
			if (light) {
				return null;
			}
			spot = at;
		}
		mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		mob.addTag(NoGrief.TAG);
		mob.addTag(GUARD_TAG);
		claim(mob);
		if (!level.addFreshEntity(mob)) {
			entities.remove(mob.getUUID());
			return null;
		}
		int realm = Math.max(0, Math.min(8, r()));
		EliteMonsters.makeElite(mob, tier, element, realm);
		int g = Math.max(1, Math.min(3, tier));
		setBase(mob, Attributes.MAX_HEALTH, ELITE_HEALTH, 24.0D * EliteMonsters.TIER_HEALTH[g] * (1.0D + 0.35D * r()));
		setBase(mob, Attributes.ATTACK_DAMAGE, ELITE_ATTACK, 4.0D + r() + 1.5D * g);
		mob.setHealth(mob.getMaxHealth());
		aim(mob);
		return mob;
	}

	/** A rogue cultivator of module B with role REALM; a vanilla elite stands in when B refuses (cap, no spot). */
	@Nullable Mob spawnRogue(int tier, int variant, int lx, int lz) {
		Vec3 at = world(lx, lz);
		if (liveMobs() >= Chambers.MAX_LIVE || !canSpawnAt(at)) {
			return null;
		}
		Mob rogue = null;
		try {
			rogue = RogueCultivators.spawn(level, at, Math.max(1, Math.min(4, tier)), Math.max(0, Math.min(8, r())), variant,
					RogueCultivators.ROLE_REALM, null);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: a realm rogue failed to spawn", exception);
		}
		if (rogue != null) {
			rogue.addTag(GUARD_TAG);
			rogue.addTag(NoGrief.TAG);
			rogue.setPersistenceRequired();
			claim(rogue);
			aim(rogue);
			return rogue;
		}
		EntityType<? extends Mob> type = variant == RogueCultivators.VARIANT_ENERGY ? EntityTypes.WITHER_SKELETON : EntityTypes.HUSK;
		Element element = variant == RogueCultivators.VARIANT_ENERGY ? Element.SPACE : null;
		Mob standIn = spawnGuard(type, Math.min(3, tier), element, lx, lz);
		if (standIn != null && variant == RogueCultivators.VARIANT_GHOST) {
			standIn.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 60 * 10, 0, false, false));
		}
		return standIn;
	}

	/** The guardian at local (0, lz): a tier-2 elite with the guardian numbers of 25.3.5, then {@code kultivator.boss}. */
	@Nullable Mob spawnGuardian(int lz) {
		Vec3 at = world(0, lz);
		if (!canSpawnAt(at)) {
			return null;
		}
		EntityType<? extends Mob> type = spec.ordinal() == 1 ? EntityTypes.HUSK : EntityTypes.WITHER_SKELETON;
		Element element = switch (spec.ordinal()) {
			case 1, 2 -> Element.FIRE;
			case 3 -> Element.ICE;
			default -> Element.SPACE;
		};
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, mob, at);
		if (spot == null) {
			spot = at;
		}
		mob.snapTo(spot.x, spot.y, spot.z, 180.0F, 0.0F);
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		mob.addTag(NoGrief.TAG);
		mob.addTag(GUARD_TAG);
		claim(mob);
		if (!level.addFreshEntity(mob)) {
			entities.remove(mob.getUUID());
			return null;
		}
		int realm = Math.max(0, Math.min(8, r()));
		EliteMonsters.makeElite(mob, 2, element, realm);
		mob.addTag(TechniquePower.TAG_BOSS);
		double health = Math.min(1024.0D, 24.0D * 8.0D * (1.0D + 0.35D * r()) * (1.0D + GUARDIAN_PER_MEMBER * extra));
		setBase(mob, Attributes.MAX_HEALTH, ELITE_HEALTH, health);
		setBase(mob, Attributes.ATTACK_DAMAGE, ELITE_ATTACK, 6.0D + 1.5D * r());
		AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.addOrReplacePermanentModifier(new AttributeModifier(GUARDIAN_SCALE, 0.4D, AttributeModifier.Operation.ADD_VALUE));
		}
		mob.setHealth(mob.getMaxHealth());
		aim(mob);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.HOSTILE, 1.2F, 0.5F);
		message(Component.translatable("kultivator.alam.guardian.appears", spec.displayName()).withStyle(ChatFormatting.DARK_RED));
		return mob;
	}

	/** The phantoms of the maze fight at half health. */
	void halveHealth(Mob mob) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.addOrReplacePermanentModifier(new AttributeModifier(GHOST_HEALTH, -0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			mob.setHealth(mob.getMaxHealth());
		}
	}

	private static void setBase(Mob mob, Holder<Attribute> attribute, net.minecraft.resources.Identifier eliteModifier, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && Double.isFinite(value)) {
			instance.removeModifier(eliteModifier);
			instance.setBaseValue(value);
		}
	}

	@Nullable RealmRelicEntity spawnRelic(int kind, int lx, int lz) {
		Vec3 at = world(lx, lz);
		RealmRelicEntity relic = AlamEntities.REALM_RELIC.create(level, EntitySpawnReason.EVENT);
		if (relic == null) {
			return null;
		}
		relic.setKind(kind);
		relic.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		relic.addTag(RealmRelicEntity.TAG);
		relic.addTag(NoGrief.TAG);
		claim(relic);
		if (!level.addFreshEntity(relic)) {
			entities.remove(relic.getUUID());
			return null;
		}
		return relic;
	}

	// ================================================================== crystals, layers, rewards

	/** Realm 3: the player absorbed a crystal (+0.25 max HP for good, a Darah Dewa Kuno for the hall). */
	void crystalAbsorbed(ServerPlayer player) {
		crystals++;
		Alam.addCrystal(player);
		for (ServerPlayer each : players()) {
			deliver(each, new ItemStack(AlamItems.ANCIENT_GOD_BLOOD));
		}
	}

	/** Gives a stack now: the inventory, the rest kept for the return point (or the Lumbung). Remembered for the treasure scroll. */
	void deliver(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		shown.computeIfAbsent(player.getUUID(), key -> new ArrayList<>()).add(stack.copy());
		ItemStack copy = stack.copy();
		if (!player.getInventory().add(copy) || !copy.isEmpty()) {
			if (!copy.isEmpty()) {
				overflow.computeIfAbsent(player.getUUID(), key -> new ArrayList<>()).add(copy);
			}
		}
	}

	/** Realm 4: a layer was cleared; its Esensi and new-best rewards go out at once (DESIGN 25.3.8). */
	private void layerCleared(int layer) {
		for (ServerPlayer player : players()) {
			int best = Alam.best(player, spec.id());
			boolean first = layer > best;
			AlamLoot.Treasure treasure = AlamLoot.layerRewards(player, layer, first);
			for (ItemStack stack : treasure.items()) {
				deliver(player, stack);
			}
			if (!treasure.choices().isEmpty()) {
				CHOICES.put(player.getUUID(), new Choice(treasure.choices(), now() + CHOICE_TICKS));
			}
			if (first) {
				Alam.setBest(player, spec.id(), layer);
			}
			Alam.fireClear(player, spec.id(), layer, first);
		}
	}

	// ================================================================== ticking

	static void tickAll(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		for (RealmRun run : List.copyOf(RUNS)) {
			try {
				run.tick();
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: secret realm run of {} failed; it ends", run.owner, exception);
				run.end(Outcome.VOLUNTARY);
			}
		}
	}

	private void tick() {
		if (state == State.ENDED) {
			return;
		}
		long now = now();
		List<ServerPlayer> here = players();
		if (here.isEmpty() && state != State.LOADING) {
			end(Outcome.LEFT);
			return;
		}
		if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL && state != State.DONE) {
			message(Component.translatable("kultivator.alam.why.peaceful").withStyle(ChatFormatting.GRAY));
			end(Outcome.VOLUNTARY);
			return;
		}
		arriveIncoming();
		guardStage(here, now);
		if (state == State.ENDED) {
			return;
		}
		if (state != State.DONE && now >= deadline && state != State.LOADING) {
			message(Component.translatable("kultivator.alam.time_up").withStyle(ChatFormatting.RED));
			lukaFor.addAll(players);
			end(Outcome.FAIL);
			return;
		}
		switch (state) {
			case LOADING -> {
				boolean ready = light || (!here.isEmpty() && here.size() == players.size() && canSpawnAt(world(0, 0)));
				if (ready || now - stateAt > LOADING_MAX) {
					room = -1;
					nextChamber(now);
				}
			}
			case PLAY -> {
				if (chamber != null) {
					chamber.tick(this, now);
					if (chamber.cleared) {
						chamberCleared(now);
					}
				}
			}
			case PAD -> {
				Entity pad = padId == null ? null : level.getEntity(padId);
				Vec3 at = pad != null ? pad.position() : chamber != null ? world(chamber.pad[0], chamber.pad[1]) : world(0, 0);
				for (ServerPlayer player : here) {
					if (player.position().distanceToSqr(at) < 1.6D * 1.6D) {
						state = State.FADE;
						stateUntil = now + FADE_TICKS;
						for (ServerPlayer each : here) {
							Fx.sendTo(each, each.position(), each.position(), FxType.ALAM, AlamFxIds.FADE, spec.ordinal(), 1.0F);
						}
						level.playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.25F, 1.6F);
						break;
					}
				}
			}
			case FADE -> {
				if (now >= stateUntil) {
					nextChamber(now);
				}
			}
			case DONE -> {
				if (now >= stateUntil) {
					end(Outcome.SUCCESS);
					return;
				}
			}
			default -> {
			}
		}
		if (now % 20L == 0L) {
			updateBar(now);
			for (UUID id : entities) {
				if (level.getEntity(id) instanceof Mob mob && mob.isAlive() && mob.getTarget() == null) {
					aim(mob);
				}
			}
		}
	}

	/** The boundary, the floor check and the wall grid (players every tick, mobs every 5 ticks), and Mode Ringan's ring. */
	private void guardStage(List<ServerPlayer> here, long now) {
		Vec3 centre = world(0, 0);
		WallGrid grid = chamber == null ? null : chamber.grid;
		for (ServerPlayer player : here) {
			if (state == State.ENDED) {
				return;
			}
			if (player.isSpectator() || !players.contains(player.getUUID())) {
				continue;
			}
			double dx = player.getX() - centre.x;
			double dz = player.getZ() - centre.z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (light) {
				if (dist > ProjectedRealm.RING) {
					int ticks = outside.merge(player.getUUID(), 1, Integer::sum);
					if (ticks % 20 == 1) {
						Msg.actionBar(player, Component.translatable("kultivator.alam.ringan.outside", Math.max(0, (LIGHT_OUTSIDE_MAX - ticks) / 20 + 1))
								.withStyle(ChatFormatting.RED));
					}
					if (ticks >= LIGHT_OUTSIDE_MAX) {
						Msg.chat(player, Component.translatable("kultivator.alam.ringan.forfeit").withStyle(ChatFormatting.GRAY));
						leave(player, false);
						continue;
					}
				} else {
					outside.remove(player.getUUID());
				}
			} else {
				if (player.getY() < origin.getY() - 9 || dist > HARD_EDGE) {
					toEntry(player);
					continue;
				}
				if (dist > SOFT_EDGE && dist > 0.001D) {
					Vec3 push = new Vec3(-dx / dist, 0.0D, -dz / dist).scale(0.8D).add(0.0D, 0.3D, 0.0D);
					player.setDeltaMovement(push);
					player.syncVelocity = true;
				}
			}
			if (grid != null && !player.isCreative()) {
				if (inWall(grid, player)) {
					Vec3 back = lastValid.get(player.getUUID());
					if (back != null) {
						player.teleportTo(back.x, player.getY(), back.z);
						player.setDeltaMovement(Vec3.ZERO);
						player.syncVelocity = true;
					}
				} else {
					lastValid.put(player.getUUID(), player.position());
				}
			}
		}
		if (grid != null && now % 5L == 0L) {
			for (UUID id : chamber.mobs) {
				if (level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
					if (inWall(grid, mob)) {
						Vec3 back = lastValid.get(id);
						if (back != null) {
							mob.teleportTo(back.x, back.y, back.z);
						}
					} else {
						lastValid.put(id, mob.position());
					}
				}
			}
		}
	}

	private boolean inWall(WallGrid grid, Entity entity) {
		double half = Math.min(0.3D, entity.getBbWidth() / 2.0D);
		for (int corner = 0; corner < 5; corner++) {
			double x = entity.getX() + (corner == 0 ? 0.0D : (corner % 2 == 0 ? half : -half));
			double z = entity.getZ() + (corner == 0 ? 0.0D : (corner < 3 ? half : -half));
			if (grid.wall(Mth.floor(x) - origin.getX(), Mth.floor(z) - origin.getZ())) {
				return true;
			}
		}
		return false;
	}

	private void arriveIncoming() {
		if (incoming.isEmpty() || light) {
			return;
		}
		ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
		if (ownerPlayer == null) {
			return;
		}
		ListTag home = homeList(ownerPlayer);
		Vec3 at = world(1, -9);
		for (int i = home.size() - 1; i >= 0; i--) {
			if (!(home.get(i) instanceof CompoundTag tag)) {
				continue;
			}
			UUID soul = tag.read("soul", UUIDUtil.CODEC).orElse(null);
			if (soul == null || !incoming.contains(soul)) {
				continue;
			}
			DiscipleEntity body = Disciples.arrive(level, at, tag, owner);
			if (body != null) {
				home.remove(i);
				incoming.remove(soul);
				disciples.add(body.getUUID());
				Cultivators.dirty(ownerPlayer);
			}
		}
	}

	private void nextChamber(long now) {
		discardTracked();
		padId = null;
		room++;
		if (room >= plan.size()) {
			succeed(now);
			return;
		}
		chamber = Chambers.create(plan.get(room), this);
		state = State.PLAY;
		stateAt = now;
		if (spec.endless()) {
			deadline = now + spec.timeTicks();
		} else if (room == 0) {
			deadline = now + spec.timeTicks();
		}
		seed = random.nextLong();
		lastValid.clear();
		chamber.start(this, now);
		partyToEntry();
		sendScene();
		updateBar(now);
		Component name = Component.translatable("kultivator.alam.chamber." + chamber.id());
		for (ServerPlayer player : players()) {
			Msg.title(player, spec.displayName(), name, 5, 40, 10);
		}
	}

	private void chamberCleared(long now) {
		Chambers.Chamber done = chamber;
		if (done == null) {
			return;
		}
		Fx.send(level, world(0, 0), FxType.ALAM, AlamFxIds.CLEAR, spec.ordinal(), 6.0F);
		if (spec.endless()) {
			if (Chambers.ENERGY_BODIES.equals(done.id()) || Chambers.GUARDIAN.equals(done.id())) {
				layerCleared(layer());
			}
		} else if (Chambers.ICE_CRYSTALS.equals(done.id())) {
			halls++;
		}
		for (ServerPlayer player : players()) {
			Alam.chamberCleared(player, done.id());
		}
		if (room >= plan.size() - 1) {
			succeed(now);
			return;
		}
		RealmRelicEntity pad = spawnRelic(RealmRelicEntity.KIND_PAD, done.pad[0], done.pad[1]);
		padId = pad == null ? null : pad.getUUID();
		Fx.send(level, world(done.pad[0], done.pad[1]), FxType.ALAM, AlamFxIds.PAD, spec.ordinal(), 1.5F);
		level.playSound(null, origin.getX(), origin.getY(), origin.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.3F);
		message(Component.translatable("kultivator.alam.chamber_cleared").withStyle(ChatFormatting.AQUA));
		state = State.PAD;
		stateAt = now;
	}

	/** The run is cleared: rewards, the treasure scroll, then the return after a short wait. */
	private void succeed(long now) {
		discardTracked();
		state = State.DONE;
		stateUntil = now + RETURN_DELAY;
		ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
		if (ownerPlayer != null) {
			EpicHooks.extra(level, EpicIds.EXTRA_REALM_CLEAR, ownerPlayer, ownerPlayer, ownerPlayer.position(), spec.ordinal(), layer());
			if (!free && !spec.endless()) {
				Alam.consumeKey(ownerPlayer, spec);
			}
		}
		for (ServerPlayer player : players()) {
			if (!spec.endless()) {
				boolean first = Alam.clears(player, spec.id()) == 0;
				AlamLoot.Treasure treasure = AlamLoot.runRewards(player, spec, first, bonus, halls);
				for (ItemStack stack : treasure.items()) {
					deliver(player, stack);
				}
				if (!treasure.choices().isEmpty()) {
					CHOICES.put(player.getUUID(), new Choice(treasure.choices(), now + CHOICE_TICKS));
				}
				Alam.addClear(player, spec.id());
				Alam.fireClear(player, spec.id(), 0, first);
			}
			Msg.title(player, Component.translatable("kultivator.alam.cleared.title").withStyle(ChatFormatting.GOLD), spec.displayName(), 10, 60, 20);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
			sendTreasure(player);
		}
		for (UUID id : disciples) {
			if (level.getEntity(id) instanceof DiscipleEntity disciple && disciple.isAlive()) {
				SectRanks.addPk(disciple.data(), DISCIPLE_PK, "kultivator.alam.pk.clear");
				DisciplePaths.grantBarShare(disciple.data(), DISCIPLE_SHARE);
				disciple.refreshSynced();
			}
		}
		Fx.send(level, world(0, 0), FxType.ALAM, AlamFxIds.CLEAR, spec.ordinal(), 10.0F);
		updateBar(now);
	}

	private void updateBar(long now) {
		Component objective;
		float progress;
		if (state == State.DONE) {
			long left = Math.max(0L, stateUntil - now);
			objective = Component.translatable("kultivator.alam.bar.returning", (left + 19L) / 20L);
			progress = (float) left / RETURN_DELAY;
		} else if (state == State.LOADING || chamber == null) {
			objective = Component.translatable("kultivator.alam.bar.loading");
			progress = 1.0F;
		} else {
			Component where;
			if (spec.endless()) {
				where = Component.translatable("kultivator.alam.bar.layer", layer());
			} else if (Chambers.GUARDIAN.equals(chamber.id())) {
				where = Component.translatable("kultivator.alam.bar.guardian");
			} else {
				where = Component.translatable("kultivator.alam.bar.room", room + 1, plan.size() - 1);
			}
			Component goal = state == State.PAD ? Component.translatable("kultivator.alam.bar.pad") : chamber.objective(this);
			long left = Math.max(0L, deadline - now);
			Component time = Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d", left / 1200L, (left / 20L) % 60L));
			objective = Component.translatable("kultivator.alam.bar.play", where, goal, time);
			float chamberProgress = chamber.progress(this);
			long total = spec.timeTicks();
			progress = chamberProgress >= 0.0F && state == State.PLAY ? chamberProgress : Math.max(0.0F, Math.min(1.0F, (float) left / total));
		}
		bar.setName(Component.translatable("kultivator.alam.bar", spec.displayName(), objective));
		bar.setProgress(Math.max(0.0F, Math.min(1.0F, progress)));
	}

	// ================================================================== client sync

	/** The chamber scenery for every party player: walls, floor tiles, the ring of Mode Ringan, the realm theme. */
	private void sendScene() {
		for (ServerPlayer player : players()) {
			Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_ALAM, sceneTag());
		}
	}

	CompoundTag sceneTag() {
		CompoundTag scene = new CompoundTag();
		scene.putInt("realm", spec.ordinal());
		scene.putString("chamber", chamber == null ? "" : chamber.id());
		scene.putInt("ox", origin.getX());
		scene.putInt("oy", origin.getY());
		scene.putInt("oz", origin.getZ());
		scene.putBoolean("light", light);
		scene.putString("dim", level.dimension().identifier().toString());
		if (chamber != null && chamber.grid != null) {
			scene.put("walls", chamber.grid.runsTag());
		}
		if (chamber != null && chamber.tiles.length > 0) {
			scene.putIntArray("tiles", chamber.tiles.clone());
		}
		CompoundTag out = new CompoundTag();
		out.put("scene", scene);
		return out;
	}

	private void sendTreasure(ServerPlayer player) {
		RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag treasure = new CompoundTag();
		treasure.putString("realm", spec.id());
		treasure.putInt("layer", spec.endless() ? layer() : 0);
		ListTag items = new ListTag();
		for (ItemStack stack : shown.getOrDefault(player.getUUID(), List.of())) {
			if (!stack.isEmpty()) {
				CompoundTag entry = new CompoundTag();
				entry.store("item", ItemStack.CODEC, ops, stack);
				items.add(entry);
			}
		}
		treasure.put("items", items);
		Choice choice = CHOICES.get(player.getUUID());
		ListTag choices = new ListTag();
		if (choice != null) {
			for (ItemStack stack : choice.options()) {
				CompoundTag entry = new CompoundTag();
				entry.store("item", ItemStack.CODEC, ops, stack);
				choices.add(entry);
			}
		}
		treasure.put("choices", choices);
		CompoundTag out = new CompoundTag();
		out.put("treasure", treasure);
		Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_ALAM, out);
	}

	/** The player picked option {@code index} of their treasure choice. */
	static void pick(ServerPlayer player, int index) {
		Choice choice = CHOICES.remove(player.getUUID());
		if (choice == null || choice.options().isEmpty()) {
			return;
		}
		ItemStack stack = choice.options().get(Math.max(0, Math.min(choice.options().size() - 1, index))).copy();
		giveOrDrop(player, stack);
		Msg.chat(player, Component.translatable("kultivator.alam.treasure.picked", stack.getHoverName()).withStyle(ChatFormatting.GOLD));
	}

	/** Choices nobody picked in time take their first option. */
	static void expireChoices(MinecraftServer server) {
		if (CHOICES.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Map.Entry<UUID, Choice> entry : List.copyOf(CHOICES.entrySet())) {
			if (now < entry.getValue().expires()) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				pick(player, 0);
			} else {
				CHOICES.remove(entry.getKey());
			}
		}
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		ItemStack copy = stack.copy();
		if (!player.getInventory().add(copy) || !copy.isEmpty()) {
			if (!copy.isEmpty()) {
				drop(player, copy);
			}
		}
	}

	/** Drops a stack at the player's feet that only they can pick up. */
	static void drop(ServerPlayer player, ItemStack stack) {
		ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
		item.setTarget(player.getUUID());
		item.setPickUpDelay(10);
		player.level().addFreshEntity(item);
	}

	// ================================================================== leaving and ending

	/** A member leaves (a lethal hit with {@code luka}, Mode Ringan's ring, a voluntary exit). The owner leaving ends the run. */
	void leave(ServerPlayer player, boolean luka) {
		if (luka) {
			lukaFor.add(player.getUUID());
		}
		if (player.getUUID().equals(owner)) {
			end(luka ? Outcome.FAIL : Outcome.VOLUNTARY);
			return;
		}
		release(player, true);
	}

	/** A member is gone from the run (left the level, logged out, died): no teleport; the run ends when the owner went. */
	void dropMember(ServerPlayer player) {
		boolean wasOwner = player.getUUID().equals(owner);
		release(player, false);
		if (wasOwner) {
			end(Outcome.LEFT);
		}
	}

	/** Takes one player out: bar, cooldown, Luka Alam, overflow; returns them when {@code sendBack}. */
	private void release(ServerPlayer player, boolean sendBack) {
		UUID id = player.getUUID();
		players.remove(id);
		BY_PLAYER.remove(id);
		bar.removePlayer(player);
		outside.remove(id);
		lastValid.remove(id);
		Alam.startCooldown(player, spec);
		if (lukaFor.contains(id)) {
			Alam.giveLuka(player);
		}
		Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_ALAM, sceneEnd());
		if (sendBack && !light && player.level() == level && player.isAlive()) {
			ReturnPoint.sendBack(player);
		} else if (player.level() != level || light) {
			ReturnPoint.clear(player);
		}
		List<ItemStack> rest = overflow.remove(id);
		if (rest != null && !rest.isEmpty()) {
			MinecraftServer server = level.getServer();
			Alam.schedule(3, () -> settleOverflow(server, id, rest));
		}
	}

	private static CompoundTag sceneEnd() {
		CompoundTag out = new CompoundTag();
		out.putBoolean("scene_end", true);
		return out;
	}

	/** Items that did not fit: the Lumbung when the player has a hall, else dropped at their feet with an owner lock. */
	private static void settleOverflow(MinecraftServer server, UUID id, List<ItemStack> rest) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null) {
			// Offline: the Lumbung keeps what it can.
			for (ItemStack stack : rest) {
				if (Halls.hallOf(id) != null) {
					Storehouse.insert(id, stack);
				}
			}
			return;
		}
		if (!player.isAlive()) {
			Alam.schedule(20, () -> settleOverflow(server, id, rest));
			return;
		}
		boolean hall = Halls.hallOf(player.getUUID()) != null;
		for (ItemStack stack : rest) {
			ItemStack left = stack;
			if (!player.getInventory().add(left) || !left.isEmpty()) {
				if (hall && !left.isEmpty()) {
					left = Storehouse.insert(player.getUUID(), left);
				}
				if (!left.isEmpty()) {
					drop(player, left);
				}
			}
		}
	}

	/** Ends the run: disciples home, entities discarded, items swept, the party returned, the slot released. */
	void end(Outcome outcome) {
		if (state == State.ENDED) {
			return;
		}
		state = State.ENDED;
		RUNS.remove(this);
		MinecraftServer server = level.getServer();
		ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner);
		if (ownerPlayer == null) {
			ownerPlayer = Alam.leavingPlayer(owner);
		}
		if (!light) {
			for (UUID id : List.copyOf(disciples)) {
				if (level.getEntity(id) instanceof DiscipleEntity disciple && disciple.isAlive() && ownerPlayer != null) {
					CompoundTag tag = Disciples.depart(disciple, "alam");
					if (!tag.isEmpty()) {
						pushHome(ownerPlayer, tag);
					}
				}
			}
		}
		disciples.clear();
		incoming.clear();
		discardTracked();
		if (!light) {
			sweepItems();
		}
		for (UUID id : List.copyOf(players)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) {
				release(player, true);
			} else {
				BY_PLAYER.remove(id);
			}
		}
		players.clear();
		bar.removeAllPlayers();
		if (slot >= 0) {
			Slots.release(slot);
		}
		if (outcome != Outcome.SUCCESS && ownerPlayer != null) {
			Msg.chat(ownerPlayer, Component.translatable(outcome == Outcome.FAIL ? "kultivator.alam.expelled" : "kultivator.alam.left", spec.displayName())
					.withStyle(ChatFormatting.GRAY));
		}
	}

	private void discardTracked() {
		for (UUID id : List.copyOf(entities)) {
			Entity entity = level.getEntity(id);
			if (entity != null && !entity.isRemoved()) {
				entity.discard();
			}
		}
		entities.clear();
		lastValid.keySet().retainAll(players);
	}

	/** Item entities within 80 blocks of the slot go to the nearest party player (DESIGN 25.3.4 step 7). */
	private void sweepItems() {
		Vec3 centre = world(0, 0);
		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(centre, centre).inflate(SWEEP_RADIUS, 64.0D, SWEEP_RADIUS), e -> e.isAlive());
		for (ItemEntity item : items) {
			ServerPlayer to = nearestPlayer(item.position());
			if (to == null) {
				ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
				to = ownerPlayer != null && players.contains(owner) ? ownerPlayer : null;
			}
			if (to != null) {
				overflow.computeIfAbsent(to.getUUID(), key -> new ArrayList<>()).add(item.getItem().copy());
			}
			item.discard();
		}
	}

	// ================================================================== disciples coming home

	/** The owner's persistent list of departed disciples waiting to arrive. */
	static ListTag homeList(ServerPlayer owner) {
		CompoundTag dunia = ReturnPoint.dunia(owner);
		ListTag list = dunia.getList(HOME_KEY).orElse(null);
		if (list == null) {
			list = new ListTag();
			dunia.put(HOME_KEY, list);
		}
		return list;
	}

	static void pushHome(ServerPlayer owner, CompoundTag tag) {
		homeList(owner).add(tag.copy());
		Cultivators.dirty(owner);
	}

	/** Every second: disciples of online owners not in a run arrive beside their master. */
	static void processHome(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (BY_PLAYER.containsKey(player.getUUID()) || player.isSpectator() || Alam.isRealmLevel(player.level())) {
				continue;
			}
			CompoundTag dunia = Cultivators.get(player).extra().getCompoundOrEmpty("dunia");
			ListTag list = dunia.getList(HOME_KEY).orElse(null);
			if (list == null || list.isEmpty()) {
				continue;
			}
			for (int i = list.size() - 1; i >= 0; i--) {
				if (list.get(i) instanceof CompoundTag tag) {
					DiscipleEntity body = Disciples.arrive(player.level(), player.position(), tag, player.getUUID());
					if (body != null) {
						list.remove(i);
						Fx.burst(player.level(), body.position().add(0.0D, 1.0D, 0.0D), 0xBFE8FF, 20, 1.0F);
						Msg.chat(player, Component.translatable("kultivator.alam.disciple_home", body.getDisplayName()).withStyle(ChatFormatting.AQUA));
					}
				} else {
					list.remove(i);
				}
			}
			Cultivators.dirty(player);
		}
	}

	/** A party disciple took a lethal hit inside the realm: it stays at 1 HP and goes home through {@code depart}. */
	static boolean saveDisciple(DiscipleEntity disciple) {
		for (RealmRun run : RUNS) {
			if (run.disciples.contains(disciple.getUUID())) {
				if (run.light) {
					// Mode Ringan: the disciple simply follows; only the run's own blows are caught.
					return false;
				}
				run.disciples.remove(disciple.getUUID());
				ServerPlayer ownerPlayer = run.level.getServer().getPlayerList().getPlayer(run.owner);
				Alam.schedule(1, () -> {
					if (!disciple.isRemoved() && ownerPlayer != null) {
						CompoundTag tag = Disciples.depart(disciple, "alam");
						if (!tag.isEmpty()) {
							pushHome(ownerPlayer, tag);
							Msg.chat(ownerPlayer, Component.translatable("kultivator.alam.disciple_saved", disciple.getDisplayName())
									.withStyle(ChatFormatting.YELLOW));
						}
					}
				});
				return true;
			}
		}
		return false;
	}

	// ================================================================== lookups

	static @Nullable RealmRun of(ServerPlayer player) {
		return BY_PLAYER.get(player.getUUID());
	}

	/** The run that tracks this entity (mob, relic or disciple), or null. */
	static @Nullable RealmRun owning(Entity entity) {
		UUID id = entity.getUUID();
		for (RealmRun run : RUNS) {
			if (run.entities.contains(id) || run.disciples.contains(id)) {
				return run;
			}
		}
		return null;
	}

	/** A party player clicked a relic (an unopened stone gate). */
	static void relicUsed(ServerPlayer player, RealmRelicEntity relic) {
		RealmRun run = BY_PLAYER.get(player.getUUID());
		if (run == null || run.state != State.PLAY || run.chamber == null || !run.entities.contains(relic.getUUID())) {
			return;
		}
		run.chamber.onRelic(run, player, relic);
	}

	/** A party player was hurt (crystal channel interruptions). */
	static void hurt(ServerPlayer player, float amount) {
		RealmRun run = BY_PLAYER.get(player.getUUID());
		if (run != null && run.state == State.PLAY && run.chamber != null) {
			run.chamber.onHurt(run, player, amount);
		}
	}

	// ================================================================== tests

	void testSkipTo(int index) {
		room = Math.max(-1, Math.min(plan.size() - 1, index - 1) - 1);
		nextChamber(now());
	}

	void testClear() {
		if (state == State.PLAY || state == State.PAD || state == State.LOADING) {
			room = plan.size() - 1;
			succeed(now());
		}
	}

	void testGuardian() {
		if (chamber != null) {
			chamber.track(spawnGuardian(6));
		}
	}

	String describe() {
		return spec.id() + " slot=" + slot + " light=" + light + " room=" + (room + 1) + "/" + plan.size() + " state=" + state + " R=" + r()
				+ " players=" + players.size() + " disciples=" + disciples.size() + " entities=" + entities.size() + " live=" + liveMobs();
	}

	static void clearAll() {
		for (RealmRun run : List.copyOf(RUNS)) {
			run.bar.removeAllPlayers();
		}
		RUNS.clear();
		BY_PLAYER.clear();
		CHOICES.clear();
	}

	/** Every run's party (for {@code debug alam info} and the slot test). */
	static Map<String, String> summary() {
		Map<String, String> map = new LinkedHashMap<>();
		for (RealmRun run : RUNS) {
			map.put(run.owner.toString(), run.describe());
		}
		return map;
	}
}
