package dev.kultivator.dunia.naga;

import dev.kultivator.cultivation.Progression;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.fx.Fx;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wild Jiao in the sky (DESIGN 34.5.11): every 2 400 ticks each player of Nascent Soul+ (config {@code minRealm}) who
 * stands outdoors in the Overworld rolls {@code chance} (6 %, doubled during a meteor shower or twin moons of module L);
 * the world keeps at most {@code worldCap} (2) and at most one within 256 blocks of a player; never in Peaceful, never
 * while the server's average tick is over the MSPT guard. The kind follows the place: a thunderstorm (vanilla weather,
 * read only) brings Jiao Petir, rivers and seas Jiao Air, mountains above y 140 and dense Qi mist Jiao Awan. A sighting
 * is published as a live map site {@code naga/naga} for 10 minutes and heard from afar (a roar, the voice of a boss).
 * <p>
 * A slain Jiao gives its slayer 3 Sisik Jiao, a Mutiara Jiao one time in five and cultivation XP (1 500 × its health
 * factor); nothing drops on the ground in the sky.
 */
final class WildDragons {

	/** One roll per player this often (2 minutes). */
	static final int ROLL_EVERY = 2400;
	static final double PLAYER_RANGE = 256.0D;
	static final int SIGHT_TICKS = 12000;
	static final double SLAIN_XP = 1500.0D;

	/** The sky events of module L running per level (meteor shower and twin moons double the chance). */
	private static final Map<ResourceKey<Level>, Set<SkyKind>> SKY = new HashMap<>();
	/** Players whose next roll comes early (a dense mist arrived over them). */
	private static final Map<java.util.UUID, Long> EARLY = new HashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(WildDragons::tick);
		DuniaEvents.SKY_EVENT.register((level, kind, started) -> {
			Set<SkyKind> set = SKY.computeIfAbsent(level.dimension(), key -> EnumSet.noneOf(SkyKind.class));
			if (started) {
				set.add(kind);
			} else {
				set.remove(kind);
			}
		});
		DuniaEvents.WEATHER_CHANGED.register((level, cell, weather) -> {
			// Jiao Awan loves dense Qi mist: a player under a fresh dense mist rolls again within a minute
			if (weather != SpiritWeather.DENSE_MIST || level.dimension() != Level.OVERWORLD) {
				return;
			}
			long due = level.getGameTime() + 1200L;
			for (ServerPlayer player : level.players()) {
				if (DuniaApi.sky().weatherAt(level, player.blockPosition()) == SpiritWeather.DENSE_MIST) {
					EARLY.put(player.getUUID(), due);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SKY.clear();
			EARLY.clear();
		});
	}

	/** The chance multiplier of {@code level} now (2 during a meteor shower or twin moons). */
	static double skyFactor(ServerLevel level) {
		Set<SkyKind> set = SKY.get(level.dimension());
		SkyKind active = DuniaApi.sky().active(level);
		boolean boost = set != null && (set.contains(SkyKind.METEOR_SHOWER) || set.contains(SkyKind.TWIN_MOONS))
				|| active == SkyKind.METEOR_SHOWER || active == SkyKind.TWIN_MOONS;
		return boost ? 2.0D : 1.0D;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 == 0) {
			broadcastFar();
		}
		NagaConfig config = Naga.config();
		if (!config.wild || !DuniaSpine.enabled() || DuniaBudget.overloaded(server)) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld == null || overworld.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		long now = overworld.getGameTime();
		for (ServerPlayer player : List.copyOf(overworld.players())) {
			Long early = EARLY.get(player.getUUID());
			boolean due = Math.floorMod(now + player.getId() * 97L, ROLL_EVERY) == 0;
			if (early != null && now >= early) {
				EARLY.remove(player.getUUID());
				due = true;
			}
			if (due) {
				roll(overworld, player, config);
			}
		}
	}

	private static void roll(ServerLevel level, ServerPlayer player, NagaConfig config) {
		if (player.isSpectator() || !player.isAlive() || !Cultivators.get(player).realm().atLeast(config.minRealm())) {
			return;
		}
		if (!level.canSeeSky(player.blockPosition().above())) {
			return;
		}
		DuniaBudget.setCap(DuniaBudget.DRAGON, config.worldCap);
		if (!DuniaBudget.mayAdd(level, DuniaBudget.DRAGON) || nearPlayer(level, player.position()) != null) {
			return;
		}
		NagaKind kind = kindFor(level, player);
		double chance = config.chance * skyFactor(level);
		if (kind == NagaKind.AWAN && DuniaApi.sky().weatherAt(level, player.blockPosition()) == SpiritWeather.DENSE_MIST) {
			chance *= 1.5D;
		}
		if (player.getRandom().nextDouble() >= chance) {
			return;
		}
		Vec3 pos = spawnPoint(level, player);
		if (pos != null) {
			spawn(level, player, kind, pos, false, false);
		}
	}

	/** The kind the place calls for. */
	static NagaKind kindFor(ServerLevel level, ServerPlayer player) {
		BlockPos at = player.blockPosition();
		var biome = level.getBiome(at);
		boolean water = biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_BEACH);
		boolean high = at.getY() > 140 || biome.is(BiomeTags.IS_MOUNTAIN) || DuniaApi.sky().weatherAt(level, at) == SpiritWeather.DENSE_MIST;
		if (level.isThundering() && player.getRandom().nextFloat() < 0.75F) {
			return NagaKind.PETIR;
		}
		if (water && (!high || player.getRandom().nextBoolean())) {
			return NagaKind.AIR;
		}
		if (high) {
			return NagaKind.AWAN;
		}
		return player.getRandom().nextBoolean() ? NagaKind.AIR : NagaKind.AWAN;
	}

	/** A point 80-120 blocks from the player at y 120-200, over loaded ground (never loads a chunk). */
	static @Nullable Vec3 spawnPoint(ServerLevel level, ServerPlayer player) {
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
			double radius = 80.0D + player.getRandom().nextDouble() * 40.0D;
			int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);
			if (!level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			double y = Mth.clamp(Math.max(ground + 40.0D, 120.0D + player.getRandom().nextDouble() * 80.0D), level.getMinY() + 16.0D,
					level.getMaxY() - 24.0D);
			return new Vec3(x + 0.5D, y, z + 0.5D);
		}
		return null;
	}

	/** Spawns a wild Jiao of {@code kind} at {@code pos} for {@code player}'s area. Null when the level refused it. */
	static @Nullable NagaLiarEntity spawn(ServerLevel level, @Nullable ServerPlayer near, NagaKind kind, Vec3 pos, boolean test, boolean lemah) {
		NagaLiarEntity dragon = NagaEntities.NAGA_LIAR.create(level, test ? EntitySpawnReason.COMMAND : EntitySpawnReason.NATURAL);
		if (dragon == null) {
			return null;
		}
		int realm = near == null ? Realm.NASCENT_SOUL.ordinal() : Math.max(Realm.NASCENT_SOUL.ordinal(), Cultivators.get(near).realm().ordinal());
		dragon.setUpWild(kind, pos, realm, test);
		if (!level.addFreshEntity(dragon)) {
			return null;
		}
		DuniaBudget.track(DuniaBudget.DRAGON, dragon);
		long until = level.getGameTime() + SIGHT_TICKS;
		dragon.siteId = DuniaSites.publishLive(level, "naga/naga", BlockPos.containing(pos), kind.ordinal() + 1, until).id();
		if (lemah) {
			dragon.setHealth(dragon.getMaxHealth() * 0.2F);
			dragon.setMode(NagaLiarEntity.Mode.LEMAH);
		}
		announce(level, dragon);
		return dragon;
	}

	/** The distant roar and the omen line for everyone within 256 blocks. */
	private static void announce(ServerLevel level, NagaLiarEntity dragon) {
		Vec3 at = dragon.position();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0F, 1.35F);
		Voice.boom(dragon, "boss.roar", PLAYER_RANGE);
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (player.position().distanceToSqr(at) > PLAYER_RANGE * PLAYER_RANGE) {
				continue;
			}
			Component where = direction(player.position(), at);
			Msg.actionBar(player, Component.translatable("kultivator.naga.sighted", dragon.kind().displayName(), where)
					.withColor(dragon.kind().glow));
			NagaPayloads.send(player, new NagaPayloads.Moment(NagaPayloads.MOMENT_SIGHTED, dragon.getId(), at.x, at.y, at.z,
					dragon.kind().ordinal(), 3, 2, dragon.getId(), false));
		}
	}

	/** Far silhouettes are sent to players between these distances (nearer ones see the entity itself). */
	static final double FAR_MIN = 48.0D;
	static final double FAR_MAX = 640.0D;

	/** Once a second: where each wild Jiao flies, to the players too far to track it (at most 2 Jiao, a few bytes each). */
	private static void broadcastFar() {
		for (NagaLiarEntity dragon : all()) {
			if (!(dragon.level() instanceof ServerLevel level)) {
				continue;
			}
			Vec3 at = dragon.position();
			Vec3 velocity = dragon.getDeltaMovement();
			NagaPayloads.Far far = null;
			for (ServerPlayer player : level.players()) {
				double distanceSq = player.position().distanceToSqr(at);
				if (distanceSq < FAR_MIN * FAR_MIN || distanceSq > FAR_MAX * FAR_MAX) {
					continue;
				}
				if (far == null) {
					far = new NagaPayloads.Far(dragon.getId(), dragon.kind().ordinal(), (float) at.x, (float) at.y, (float) at.z,
							(float) velocity.x, (float) velocity.y, (float) velocity.z, dragon.flags());
				}
				NagaPayloads.send(player, far);
			}
		}
	}

	/** "di utara" / "di barat daya" ... from {@code from} toward {@code to}. */
	static Component direction(Vec3 from, Vec3 to) {
		double angle = Math.toDegrees(Math.atan2(to.x - from.x, -(to.z - from.z)));
		int index = Math.floorMod((int) Math.round(angle / 45.0D), 8);
		String[] keys = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
		return Component.translatable("kultivator.naga.dir." + keys[index]);
	}

	/** A wild Jiao within 256 blocks of {@code at}, or null. */
	static @Nullable NagaLiarEntity nearPlayer(ServerLevel level, Vec3 at) {
		for (Entity entity : DuniaBudget.tracked(DuniaBudget.DRAGON)) {
			if (entity instanceof NagaLiarEntity dragon && !dragon.isTamed() && dragon.level() == level
					&& dragon.position().distanceToSqr(at) < PLAYER_RANGE * PLAYER_RANGE) {
				return dragon;
			}
		}
		return null;
	}

	/** Every wild Jiao alive (any level). */
	static List<NagaLiarEntity> all() {
		List<NagaLiarEntity> list = new java.util.ArrayList<>();
		for (Entity entity : DuniaBudget.tracked(DuniaBudget.DRAGON)) {
			if (entity instanceof NagaLiarEntity dragon && !dragon.isTamed() && !dragon.isRemoved()) {
				list.add(dragon);
			}
		}
		return list;
	}

	/** The sighting ends (tamed, slain, gone): its live map site goes. */
	static void forget(NagaLiarEntity dragon) {
		if (dragon.siteId != 0L) {
			DuniaSites.endLive(dragon.siteId);
			dragon.siteId = 0L;
		}
	}

	// ================================================================== slain

	static void onKilled(ServerLevel level, NagaLiarEntity dragon, DamageSource source) {
		forget(dragon);
		ServerPlayer slayer = slayer(level, dragon, source);
		Vec3 at = dragon.position();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 3.0F, 1.4F);
		Fx.burst(level, at, dragon.kind().glow, 80, 5.0F);
		Voice.boom(dragon, "boss.defeat", 128.0D);
		if (slayer == null) {
			return;
		}
		NagaItems.give(slayer, new ItemStack(NagaItems.SISIK_JIAO, 3));
		boolean pearl = !dragon.test && slayer.getRandom().nextFloat() < 0.2F;
		if (pearl) {
			NagaItems.give(slayer, new ItemStack(NagaItems.MUTIARA_JIAO));
		}
		if (!dragon.test) {
			Progression.addXp(slayer, SLAIN_XP * dragon.healthFactor, XpSource.COMBAT);
		}
		Msg.chat(slayer, Component.translatable(pearl ? "kultivator.naga.slain.pearl" : "kultivator.naga.slain", dragon.kind().displayName())
				.withColor(dragon.kind().glow));
		Advancements.grant(slayer, "dunia/dragon_slain");
	}

	private static @Nullable ServerPlayer slayer(ServerLevel level, NagaLiarEntity dragon, DamageSource source) {
		Entity entity = source.getEntity();
		if (entity instanceof ServerPlayer player) {
			return player;
		}
		if (entity instanceof OwnableEntity ownable && ownable.getOwner() instanceof ServerPlayer owner) {
			return owner;
		}
		if (entity instanceof net.minecraft.world.entity.Mob mob && dev.kultivator.beast.SpiritBeasts.ownerOf(mob) != null) {
			return dev.kultivator.beast.SpiritBeasts.ownerOf(mob);
		}
		return level.getNearestPlayer(dragon, 48.0D) instanceof ServerPlayer near && !near.isSpectator() ? near : null;
	}

	private WildDragons() {
	}
}
