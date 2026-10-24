package dev.kultivator.dunia.langit;

import dev.kultivator.Kultivator;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.network.Net;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Peristiwa Langit Acak (DESIGN 34.5.8): the random sky events of the Overworld and Alam Abadi. At each sunset one roll
 * ({@code chancePerNight}, at least {@code minNightsBetween} nights after the last event) picks Hujan Meteor Roh, Gerhana
 * Darah or Bulan Kembar for the coming night (13 000-23 000 of the overworld clock; sleeping through the night ends it).
 * The omen line goes out at sunset, the event starts at nightfall and ends at dawn; every player of the dimension is
 * synced through {@link LangitSkyPayload} and {@code DuniaEvents.SKY_EVENT} fires at the start and the end.
 * <p>
 * Server effects only (W3: no weather, time or tick-rate change, no {@code LightningBolt}): meteors that fall near
 * outdoor players ({@link MeteorRohEntity}, a Batu Meteor Roh where they land, never a block), the eclipse-touched
 * monsters (+25 % health and attack through attribute modifiers on natural spawns near players), and the meditation
 * sites of {@link LangitSites}. Everything a player sees in the sky is drawn by the client.
 */
public final class SkyEvents {

	public static final int SUNSET = 12000;
	public static final int NIGHT_START = 13000;
	public static final int NIGHT_END = 23000;
	public static final int DAY = 24000;
	/** A meteor every 30-60 s per outdoor player. */
	public static final int METEOR_GAP_MIN = 600;
	public static final int METEOR_GAP_MAX = 1200;
	/** Meteors land 24-96 blocks from the player. */
	public static final int METEOR_NEAR = 24;
	public static final int METEOR_FAR = 96;
	/** The Batu Meteor Roh stays 10 minutes (extended lifetime). */
	public static final int METEOR_SITE_TICKS = 12000;
	/** Eclipse marks are sent for touched monsters within this range. */
	public static final double MARK_RANGE = 48.0D;
	/** Natural spawns this close to a player may be touched by the eclipse. */
	public static final double TOUCH_RANGE = 96.0D;
	public static final String TOUCHED_TAG = "kultivator.gerhana";
	public static final Identifier HEALTH_ID = Kultivator.id("gerhana_health");
	public static final Identifier ATTACK_ID = Kultivator.id("gerhana_attack");

	/** Bulan Kembar: spirit XP ×1.5, so +0.5 of the base again. */
	public static final double TWIN_BEAST_BONUS = 0.5D;
	/** The values of {@code SpiritBeasts} (private there): the owner's kill share and its range. */
	private static final double OWNER_KILL_SHARE = 0.5D;
	private static final double KILL_SHARE_RANGE = 16.0D;
	private static final Identifier HEART_DEMON = Kultivator.id("heart_demon");

	/** Weights of the three kinds when all are enabled (meteor showers are the most common). */
	private static final int[] WEIGHTS = {45, 30, 25};

	/** Dimensions whose event already fired {@code SKY_EVENT(started)} in this server session. */
	private static final Set<ResourceKey<Level>> STARTED = new HashSet<>();
	/** Forced (test) events end at this game time too, so a frozen clock cannot keep them forever. */
	private static final Map<ResourceKey<Level>, Long> FORCED_END = new HashMap<>();
	/** Per player: the next meteor game time and how many fell for the event with this seed. */
	private static final Map<UUID, long[]> METEORS = new HashMap<>();
	/** Players who already got the witness advancement check for the running event seed. */
	private static final Map<UUID, Integer> WITNESSED = new HashMap<>();
	/** Natural spawns waiting for the eclipse touch at the end of the tick (modifiers are added after finalizeSpawn). */
	private static final List<Mob> TOUCH = new ArrayList<>();
	/** Players who were sent a non-empty eclipse mark list (they get an empty one when it clears). */
	private static final Set<UUID> MARKED = new HashSet<>();

	// ================================================================== queries

	/** The levels that have sky events: the Overworld and Alam Abadi. */
	static boolean eligible(Level level) {
		return level.dimension() == Level.OVERWORLD || level.dimension().equals(Langit.ALAM_ABADI);
	}

	/** The event running now in {@code level} (started), or null. */
	public static @Nullable SkyKind active(ServerLevel level) {
		if (!eligible(level) || !STARTED.contains(level.dimension())) {
			return null;
		}
		LangitData.Dim dim = LangitData.get(level.getServer()).peek(key(level));
		if (dim == null || dim.kind < 0 || dim.kind >= SkyKind.values().length) {
			return null;
		}
		return SkyKind.values()[dim.kind];
	}

	/** True while {@code kind} runs in {@code level}. */
	public static boolean is(ServerLevel level, SkyKind kind) {
		return active(level) == kind;
	}

	static String key(Level level) {
		return level.dimension().identifier().toString();
	}

	static long clock(ServerLevel level) {
		return level.getOverworldClockTime();
	}

	// ================================================================== the server tick

	/** Every 20 ticks (staggered) from {@link Langit}. */
	static void tick(MinecraftServer server) {
		LangitConfig config = LangitConfig.get();
		LangitData data = LangitData.get(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (!eligible(level)) {
				continue;
			}
			LangitData.Dim dim = data.dim(key(level));
			long ct = clock(level);
			long day = Math.floorDiv(ct, DAY);
			int tod = Math.floorMod(ct, DAY);
			if (dim.kind >= 0) {
				Long forcedEnd = FORCED_END.get(level.dimension());
				boolean over = ct >= dim.end || dim.kind >= SkyKind.values().length
						|| (dim.forced && (forcedEnd == null || level.getGameTime() >= forcedEnd))
						|| (!dim.forced && (!config.events || !kindEnabled(config, SkyKind.values()[dim.kind]) || !DuniaSpine.enabled()));
				if (over) {
					end(level, dim, data);
				} else if (ct >= dim.start && !STARTED.contains(level.dimension())) {
					begin(level, dim);
				}
			} else if (config.events && DuniaSpine.enabled() && tod >= SUNSET && tod < NIGHT_START && dim.rollDay != day
					&& !DuniaBudget.overloaded(server)) {
				// the sunset roll (postponed while the server is overloaded, it retries within the sunset hour)
				dim.rollDay = day;
				data.changed();
				RandomSource random = level.getRandom();
				SkyKind kind = pick(config, random);
				if (kind != null && day - dim.lastEventDay >= config.minNightsBetween && random.nextDouble() < config.chancePerNight) {
					schedule(level, dim, data, kind, day * DAY + NIGHT_START, day * DAY + NIGHT_END, false);
				}
			}
			SkyKind running = active(level);
			if (running != null) {
				tickRunning(level, running, config, dim);
			}
			marks(level);
		}
	}

	/** End of every server tick: the eclipse touch for the natural spawns queued by {@link #onSpawn}. */
	static void endTick() {
		if (TOUCH.isEmpty()) {
			return;
		}
		double buff = LangitConfig.get().eclipseMobBuff;
		for (Mob mob : TOUCH) {
			if (mob.isAlive() && !mob.isRemoved()) {
				touch(mob, buff);
			}
		}
		TOUCH.clear();
	}

	/**
	 * {@code ServerEntityEvents.ALLOW_LOAD}: a natural monster spawn near a player during the eclipse may be touched; a
	 * treasure guardian loaded from disk after its birth ended is not loaded at all.
	 */
	static boolean onSpawn(Entity entity, ServerLevel level, @Nullable EntitySpawnReason reason, boolean loadedFromDisk) {
		if (loadedFromDisk && entity.entityTags().contains(TreasureBirths.GUARDIAN_TAG) && !TreasureGuardians.known(entity)) {
			// a guardian saved with its chunk outlives its treasure: it returns to the earth instead of loading
			return false;
		}
		if (loadedFromDisk || reason != EntitySpawnReason.NATURAL || !(entity instanceof Mob mob) || !(entity instanceof Enemy)) {
			return true;
		}
		if (!is(level, SkyKind.BLOOD_ECLIPSE)) {
			return true;
		}
		double buff = LangitConfig.get().eclipseMobBuff;
		if (!(buff > 0.0D) || TOUCH.size() >= 64) {
			return true;
		}
		if (level.getNearestPlayer(entity, TOUCH_RANGE) == null) {
			return true;
		}
		if (level.getRandom().nextDouble() < 0.25D) {
			TOUCH.add(mob);
		}
		return true;
	}

	/** Tersentuh Gerhana: +{@code buff} health and attack (permanent modifiers, saved with the mob), a red glow on clients. */
	static void touch(Mob mob, double buff) {
		if (mob.entityTags().contains(TOUCHED_TAG)) {
			return;
		}
		mob.addTag(TOUCHED_TAG);
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && !health.hasModifier(HEALTH_ID)) {
			health.addPermanentModifier(new AttributeModifier(HEALTH_ID, buff, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			mob.setHealth(mob.getMaxHealth());
		}
		AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null && !attack.hasModifier(ATTACK_ID)) {
			attack.addPermanentModifier(new AttributeModifier(ATTACK_ID, buff, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/**
	 * {@code ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY}: under Bulan Kembar spirit beasts gain ×1.5 spirit XP, so
	 * half of what {@code SpiritBeasts} already gave is added again (its own kill: the victim's max health; the owner's kill
	 * nearby: half of it within 16 blocks, the same numbers as {@code SpiritBeasts.onKill}).
	 */
	static void onKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		if (killer instanceof ServerPlayer slayer && !(slayer instanceof FakePlayer) && killed.entityTags().contains(TOUCHED_TAG)) {
			eclipseSpoils(level, slayer, killed);
		}
		if (!is(level, SkyKind.TWIN_MOONS) || HEART_DEMON.equals(BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()))) {
			return;
		}
		double base = killed.getMaxHealth();
		if (killer instanceof Mob beast && SpiritBeasts.isSpiritBeast(beast)) {
			SpiritBeasts.addXp(beast, base * TWIN_BEAST_BONUS);
			return;
		}
		if (killer instanceof ServerPlayer player && !(player instanceof FakePlayer) && killed instanceof Enemy) {
			double share = base * OWNER_KILL_SHARE * TWIN_BEAST_BONUS;
			for (Mob beast : SpiritBeasts.loadedBeasts(player.getUUID())) {
				if (beast != killed && beast.level() == player.level() && beast.distanceToSqr(player) <= KILL_SHARE_RANGE * KILL_SHARE_RANGE) {
					SpiritBeasts.addXp(beast, share);
				}
			}
		}
	}

	/**
	 * An eclipse-touched monster slain by a player: {@link LangitItems#ECLIPSE_DROP} a Kristal Gerhana where it fell, a red
	 * burst for the slayer, and the count towards {@code dunia/eclipse_hunter}.
	 */
	private static void eclipseSpoils(ServerLevel level, ServerPlayer slayer, LivingEntity killed) {
		LangitFeats.eclipseSlain(slayer);
		Net.send(slayer, LangitFxPayload.at(LangitFxPayload.KIND_GUARD_FALL, killed.getX(), killed.getY(), killed.getZ(), 0));
		if (level.getRandom().nextDouble() >= LangitItems.eclipseDrop(slayer)) {
			return;
		}
		ItemEntity crystal = new ItemEntity(level, killed.getX(), killed.getY() + 0.5D, killed.getZ(), new ItemStack(LangitItems.KRISTAL_GERHANA));
		crystal.setPickUpDelay(10);
		level.addFreshEntity(crystal);
		level.playSound(null, killed.getX(), killed.getY() + 0.5D, killed.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 0.7F, 0.6F);
	}

	private static void tickRunning(ServerLevel level, SkyKind kind, LangitConfig config, LangitData.Dim dim) {
		long now = level.getGameTime();
		if (kind == SkyKind.METEOR_SHOWER) {
			GreatMeteors.tick(level, dim, config);
		}
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (player.isSpectator() || player.isRemoved()) {
				continue;
			}
			boolean outdoors = level.canSeeSky(player.blockPosition().above());
			if (outdoors && WITNESSED.getOrDefault(player.getUUID(), 0) != dim.seed) {
				WITNESSED.put(player.getUUID(), dim.seed);
				Advancements.grant(player, "dunia/sky_event");
				LangitFeats.witnessed(player, kind);
			}
			if (kind == SkyKind.METEOR_SHOWER && outdoors && (config.meteorShower || dim.forced)) {
				long[] state = METEORS.computeIfAbsent(player.getUUID(), id -> new long[]{0L, 0L, 0L});
				if (state[2] != dim.seed) {
					state[0] = now + 100 + level.getRandom().nextInt(400);
					state[1] = 0L;
					state[2] = dim.seed;
				}
				if (now >= state[0] && state[1] < config.meteorsPerPlayer) {
					state[0] = now + METEOR_GAP_MIN + level.getRandom().nextInt(METEOR_GAP_MAX - METEOR_GAP_MIN + 1);
					if (fall(level, player, -1.0D, false) != null) {
						state[1]++;
					}
				}
			}
		}
	}

	// ================================================================== schedule, begin, end

	/** Plans {@code kind} for [start, end) of the overworld clock and sends the omen (or starts it at once). */
	static void schedule(ServerLevel level, LangitData.Dim dim, LangitData data, SkyKind kind, long start, long end, boolean forced) {
		if (STARTED.contains(level.dimension())) {
			end(level, dim, data);
		}
		dim.kind = kind.ordinal();
		dim.start = start;
		dim.end = end;
		dim.seed = level.getRandom().nextInt() | 1;
		dim.forced = forced;
		dim.lastEventDay = Math.floorDiv(start, DAY);
		data.changed();
		if (!forced) {
			for (ServerPlayer player : List.copyOf(level.players())) {
				Msg.chat(player, Component.translatable("kultivator.langit.omen." + kind.id()).withColor(color(kind)));
			}
		}
		syncAll(level);
		if (clock(level) >= start) {
			begin(level, dim);
		}
	}

	private static void begin(ServerLevel level, LangitData.Dim dim) {
		SkyKind kind = SkyKind.values()[dim.kind];
		STARTED.add(level.dimension());
		syncAll(level);
		// a night already running when the server started again: no second announcement
		boolean resumed = clock(level) - dim.start > 400L;
		for (ServerPlayer player : resumed ? List.<ServerPlayer>of() : List.copyOf(level.players())) {
			Msg.chat(player, Component.translatable("kultivator.langit.begin." + kind.id()).withColor(color(kind)));
			// the new lines of DESIGN 34.8 once the voice module adds them, else the existing ones
			Langit.heaven(player, "langit." + kind.id(),
					kind == SkyKind.BLOOD_ECLIPSE ? "wrath" : kind == SkyKind.TWIN_MOONS ? "secret_realm" : "witness");
			// what the night means at the player's own realm (four bands, the treasure tiers)
			int band = TreasureBirths.tierOf(Cultivators.get(player).realm());
			Msg.chat(player, Component.translatable("kultivator.langit.sense." + kind.id() + "." + band)
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		Kultivator.LOGGER.info("Sky event {} began in {}", kind.id(), key(level));
		DuniaEvents.fireSkyEvent(level, kind, true);
	}

	static void end(ServerLevel level, LangitData.Dim dim, LangitData data) {
		boolean started = STARTED.remove(level.dimension());
		int kindIndex = dim.kind;
		dim.kind = -1;
		dim.forced = false;
		data.changed();
		FORCED_END.remove(level.dimension());
		syncAll(level);
		if (started && kindIndex >= 0 && kindIndex < SkyKind.values().length) {
			SkyKind kind = SkyKind.values()[kindIndex];
			for (ServerPlayer player : List.copyOf(level.players())) {
				Msg.chat(player, Component.translatable("kultivator.langit.end." + kind.id()).withColor(0xB8C4D8));
			}
			DuniaEvents.fireSkyEvent(level, kind, false);
		}
	}

	/** {@code uji langit <kind> [menit]}: starts {@code kind} now for {@code minutes} (also by day, also when switched off). */
	static void force(ServerLevel level, SkyKind kind, int minutes) {
		LangitData data = LangitData.get(level.getServer());
		LangitData.Dim dim = data.dim(key(level));
		long ct = clock(level);
		long ticks = Math.max(1, minutes) * 1200L;
		FORCED_END.put(level.dimension(), level.getGameTime() + ticks);
		schedule(level, dim, data, kind, ct, ct + ticks, true);
	}

	/** {@code uji langit berhenti}: ends the event of {@code level}; true when one was announced or running. */
	static boolean stop(ServerLevel level) {
		LangitData data = LangitData.get(level.getServer());
		LangitData.Dim dim = data.dim(key(level));
		if (dim.kind < 0) {
			return false;
		}
		end(level, dim, data);
		return true;
	}

	/** {@code uji langit jadwal}: the schedule of {@code level}. */
	static Component schedule(ServerLevel level) {
		LangitConfig config = LangitConfig.get();
		LangitData.Dim dim = LangitData.get(level.getServer()).dim(key(level));
		long ct = clock(level);
		long day = Math.floorDiv(ct, DAY);
		Component state;
		if (dim.kind >= 0 && dim.kind < SkyKind.values().length) {
			SkyKind kind = SkyKind.values()[dim.kind];
			state = Component.translatable(STARTED.contains(level.dimension()) ? "kultivator.langit.cmd.running" : "kultivator.langit.cmd.tonight",
					name(kind), Math.max(0L, (dim.end - ct) / 20L));
		} else {
			long nextOk = Math.max(day, dim.lastEventDay + config.minNightsBetween);
			state = Component.translatable("kultivator.langit.cmd.next", nextOk - day, Math.round(config.chancePerNight * 100.0D));
		}
		return Component.translatable("kultivator.langit.cmd.schedule", key(level), day, Math.floorMod(ct, DAY),
				config.events ? Component.translatable("options.on") : Component.translatable("options.off"), state);
	}

	// ================================================================== meteors

	/**
	 * Sends a Meteor Roh at {@code player}: it lands {@code distance} blocks ahead ({@code < 0}: 24-96 blocks in a random
	 * direction) on the heightmap top of a loaded chunk. Null when no loaded spot was found.
	 */
	static @Nullable MeteorRohEntity fall(ServerLevel level, ServerPlayer player, double distance) {
		return fall(level, player, distance, false);
	}

	/**
	 * {@link #fall(ServerLevel, ServerPlayer, double)} for a common meteor or ({@code great}) a Meteor Agung: 48-140 blocks
	 * away when no distance is given, from 150-180 blocks up, 25-40 degrees off vertical, over {@link MeteorRohEntity#GREAT_FALL}.
	 */
	static @Nullable MeteorRohEntity fall(ServerLevel level, ServerPlayer player, double distance, boolean great) {
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle;
			double reach;
			if (distance >= 0.0D) {
				Vec3 look = player.getLookAngle();
				angle = Math.atan2(look.z, look.x) + (attempt == 0 ? 0.0D : (random.nextDouble() - 0.5D) * 0.6D);
				reach = distance;
			} else {
				angle = random.nextDouble() * Math.PI * 2.0D;
				reach = great ? GreatMeteors.NEAR + random.nextDouble() * (GreatMeteors.FAR - GreatMeteors.NEAR)
						: METEOR_NEAR + random.nextDouble() * (METEOR_FAR - METEOR_NEAR);
			}
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * reach);
			int z = (int) Math.floor(player.getZ() + Math.sin(angle) * reach);
			if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
				continue;
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (top <= level.getMinY() + 1 || top >= level.getMaxY() - 8) {
				continue;
			}
			BlockPos landing = new BlockPos(x, top, z);
			MeteorRohEntity meteor = LangitItems.METEOR.create(level, EntitySpawnReason.EVENT);
			if (meteor == null) {
				return null;
			}
			// from 90-120 blocks up, 15-35 degrees off vertical, from a random side (great: higher and flatter)
			double height = great ? 150.0D + random.nextDouble() * 30.0D : 90.0D + random.nextDouble() * 30.0D;
			double tilt = Math.toRadians(great ? 25.0D + random.nextDouble() * 15.0D : 15.0D + random.nextDouble() * 20.0D);
			double side = random.nextDouble() * Math.PI * 2.0D;
			double across = Math.tan(tilt) * height;
			Vector3f offset = new Vector3f((float) (Math.cos(side) * across), (float) height, (float) (Math.sin(side) * across));
			int fallTicks = great ? MeteorRohEntity.GREAT_FALL : MeteorRohEntity.DEFAULT_FALL + random.nextInt(20);
			meteor.setup(landing, offset, fallTicks, random.nextInt(), great);
			level.addFreshEntity(meteor);
			return meteor;
		}
		return null;
	}

	/** The meteor reached the ground: the impact visual and sound, the stone, a live map site. Nothing else changes. */
	static void meteorLanded(ServerLevel level, MeteorRohEntity meteor) {
		if (meteor.great()) {
			GreatMeteors.landed(level, meteor);
			return;
		}
		BlockPos landing = meteor.landing();
		Vec3 at = Vec3.atBottomCenterOf(landing);
		LangitFxPayload fx = LangitFxPayload.at(LangitFxPayload.KIND_IMPACT, at.x, at.y, at.z, 0);
		for (ServerPlayer viewer : PlayerLookup.around(level, at, 192.0D)) {
			Net.send(viewer, fx);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.AMBIENT, 3.0F, 0.55F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.AMBIENT, 1.6F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.4F, 0.6F);
		// a gentle shove for whatever stood right there (no damage)
		for (Entity entity : level.getEntities((Entity) null, new AABB(at, at).inflate(3.0D), e -> e.isAlive() && e.isPushable())) {
			Vec3 away = entity.position().subtract(at);
			double length = Math.max(0.3D, away.horizontalDistance());
			entity.push(away.x / length * 0.4D, 0.25D, away.z / length * 0.4D);
		}
		ItemEntity stone = new ItemEntity(level, at.x, at.y + 0.4D, at.z, new ItemStack(LangitItems.BATU_METEOR_ROH));
		stone.setPickUpDelay(20);
		stone.setExtendedLifetime();
		stone.setDeltaMovement(0.0D, 0.25D, 0.0D);
		level.addFreshEntity(stone);
		DuniaSites.publishLive(level, "langit/meteor", landing, 1, level.getGameTime() + METEOR_SITE_TICKS);
	}

	// ================================================================== eclipse marks

	/** Every 20 ticks: tells each player of {@code level} which touched monsters near them glow red (only on change). */
	private static void marks(ServerLevel level) {
		if (level.getServer().getTickCount() % 40 >= 20) {
			return;
		}
		for (ServerPlayer player : List.copyOf(level.players())) {
			List<Mob> touched = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(MARK_RANGE),
					mob -> mob.isAlive() && mob.entityTags().contains(TOUCHED_TAG));
			if (touched.isEmpty()) {
				if (MARKED.remove(player.getUUID())) {
					Net.send(player, new LangitFxPayload(LangitFxPayload.KIND_MARKS, 0.0D, 0.0D, 0.0D, 0, new int[0]));
				}
				continue;
			}
			int[] ids = new int[Math.min(32, touched.size())];
			for (int i = 0; i < ids.length; i++) {
				ids[i] = touched.get(i).getId();
			}
			MARKED.add(player.getUUID());
			Net.send(player, new LangitFxPayload(LangitFxPayload.KIND_MARKS, 0.0D, 0.0D, 0.0D, ids.length, ids));
		}
	}

	// ================================================================== sync

	/** The payload describing {@code level}'s event for its players. */
	static LangitSkyPayload payload(ServerLevel level) {
		if (!eligible(level)) {
			return LangitSkyPayload.none();
		}
		LangitData.Dim dim = LangitData.get(level.getServer()).peek(key(level));
		if (dim == null || dim.kind < 0 || dim.kind >= SkyKind.values().length) {
			return LangitSkyPayload.none();
		}
		int state = STARTED.contains(level.dimension()) ? LangitSkyPayload.ACTIVE : LangitSkyPayload.OMEN;
		return new LangitSkyPayload(dim.kind, state, dim.seed, dim.start, dim.end);
	}

	static void syncAll(ServerLevel level) {
		LangitSkyPayload payload = payload(level);
		for (ServerPlayer player : List.copyOf(level.players())) {
			Net.send(player, payload);
		}
	}

	static void sync(ServerPlayer player) {
		Net.send(player, payload(player.level()));
	}

	// ================================================================== helpers

	static boolean kindEnabled(LangitConfig config, SkyKind kind) {
		return switch (kind) {
			case METEOR_SHOWER -> config.meteorShower;
			case BLOOD_ECLIPSE -> config.bloodEclipse;
			case TWIN_MOONS -> config.twinMoons;
		};
	}

	private static @Nullable SkyKind pick(LangitConfig config, RandomSource random) {
		int total = 0;
		for (SkyKind kind : SkyKind.values()) {
			if (kindEnabled(config, kind)) {
				total += WEIGHTS[kind.ordinal()];
			}
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (SkyKind kind : SkyKind.values()) {
			if (!kindEnabled(config, kind)) {
				continue;
			}
			roll -= WEIGHTS[kind.ordinal()];
			if (roll < 0) {
				return kind;
			}
		}
		return null;
	}

	public static Component name(SkyKind kind) {
		return Component.translatable("kultivator.langit.kind." + kind.id()).withColor(color(kind));
	}

	public static int color(SkyKind kind) {
		return switch (kind) {
			case METEOR_SHOWER -> 0x8FD8FF;
			case BLOOD_ECLIPSE -> 0xFF4A3A;
			case TWIN_MOONS -> 0xD8E2FF;
		};
	}

	static void forget(ServerPlayer player) {
		METEORS.remove(player.getUUID());
		WITNESSED.remove(player.getUUID());
		MARKED.remove(player.getUUID());
	}

	static void clear() {
		STARTED.clear();
		FORCED_END.clear();
		METEORS.clear();
		WITNESSED.clear();
		TOUCH.clear();
		MARKED.clear();
	}

	private SkyEvents() {
	}
}
