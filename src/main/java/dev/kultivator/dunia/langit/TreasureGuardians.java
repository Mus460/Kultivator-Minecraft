package dev.kultivator.dunia.langit;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.api.TreasureBirth;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.network.Net;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The guardians of a born treasure (DESIGN 34.5.9 "Guardian 40 %", detail pass). When the treasure first appears, with
 * {@code guardianChance} its guardians wake 6-10 blocks from the lotus: one elite beast of the tier (T1 tier 1, T2 tier 2,
 * T3-T4 tier 3), and from Harta Langit up a second one a tier lower. The kind follows the biome (husks in deserts, strays
 * in snow, bogged in swamps, else zombies and spiders; from Harta Langit up also vindicators and skeletons). Each wears a
 * name plate with its title ("Penjaga Harta Langit · Laba-laba"), roars when it wakes, and is leashed: one that strays
 * more than 20 blocks from the lotus without a target walks back.
 * <p>
 * While a guardian lives within {@link #GUARD_RANGE} blocks of the lotus the treasure is sealed: the claim channel cannot
 * start. Slaying one grants {@code dunia/treasure_guardian}. When the birth ends the remaining guardians return to the
 * earth (discarded with a burst); a guardian saved with an unloaded chunk is not loaded again ({@link SkyEvents#onSpawn}).
 * The client draws a turning sigil under each guardian from {@link LangitFxPayload#KIND_GUARDS}.
 * <p>
 * R's stone construct and B's young Jiao (34.5.9) are not spawned here: modules never import each other, and both
 * owners would wake their own guardian from {@code DuniaEvents.TREASURE_BORN}.
 */
final class TreasureGuardians {

	/** A living guardian this close to the lotus seals the claim. */
	static final double GUARD_RANGE = 16.0D;
	/** A guardian farther than this from the lotus without a target walks back. */
	static final double LEASH = 20.0D;
	/** Players this close to the lotus learn the guardians (their sigils). */
	static final double SYNC_RANGE = 96.0D;

	/** The guardians of each dimension's live birth. */
	private static final Map<ResourceKey<Level>, List<UUID>> GUARDS = new HashMap<>();
	/** The tier of each dimension's guardians (the sigil colour). */
	private static final Map<ResourceKey<Level>, Integer> TIERS = new HashMap<>();
	/** Every guardian of a live birth (loaded or not). */
	private static final Set<UUID> KNOWN = new HashSet<>();
	/** Players who were sent a non-empty guardian list (they get an empty one when it clears). */
	private static final Set<UUID> SENT = new HashSet<>();

	// ================================================================== waking

	/** The guardians wake with the treasure (called once per birth, after the chance roll). */
	static void spawn(ServerLevel level, TreasureBirth record) {
		int tier = Math.max(1, Math.min(4, record.tier()));
		int eliteTier = Math.min(3, tier);
		int count = tier >= 3 ? 2 : 1;
		List<UUID> list = GUARDS.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
		TIERS.put(level.dimension(), tier);
		Mob first = null;
		for (int i = 0; i < count; i++) {
			Mob mob = spawnOne(level, record, i == 0 ? eliteTier : Math.max(1, eliteTier - 1), tier, i);
			if (mob != null) {
				list.add(mob.getUUID());
				KNOWN.add(mob.getUUID());
				if (first == null) {
					first = mob;
				}
			}
		}
		if (first == null) {
			return;
		}
		Vec3 at = Vec3.atCenterOf(record.pos());
		level.playSound(null, first.getX(), first.getY() + 1.0D, first.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4F, 0.7F);
		Voice.say(first, "boss.roar", "");
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (player.position().distanceTo(at) <= TreasureBirths.ABSENCE) {
				Msg.chat(player, Component.translatable("kultivator.langit.guardian.awake", TreasureBirths.tierName(tier), list.size())
						.withStyle(ChatFormatting.GOLD));
			}
		}
		sync(level, record);
	}

	/** One guardian of elite tier {@code eliteTier}, 6-10 blocks from the treasure; null when no loaded ground was found. */
	private static @Nullable Mob spawnOne(ServerLevel level, TreasureBirth record, int eliteTier, int tier, int index) {
		RandomSource random = level.getRandom();
		BlockPos pos = record.pos();
		EntityType<? extends Mob> type = type(level.getBiome(pos), tier, index, random);
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double reach = 6.0D + random.nextDouble() * 4.0D;
			int x = (int) Math.floor(pos.getX() + 0.5D + Math.cos(angle) * reach);
			int z = (int) Math.floor(pos.getZ() + 0.5D + Math.sin(angle) * reach);
			if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			Mob mob = type.create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				return null;
			}
			// facing the lotus
			float yaw = (float) Math.toDegrees(Math.atan2(-(pos.getX() + 0.5D - (x + 0.5D)), pos.getZ() + 0.5D - (z + 0.5D)));
			mob.snapTo(x + 0.5D, y, z + 0.5D, yaw, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.EVENT, null);
			mob.addTag(TreasureBirths.GUARDIAN_TAG);
			mob.setCustomName(Component.translatable("kultivator.langit.guardian.name", TreasureBirths.tierName(tier), type.getDescription())
					.withColor(TreasureRewards.tierColor(tier)));
			mob.setCustomNameVisible(true);
			if (!level.addFreshEntity(mob)) {
				return null;
			}
			// after the add, so the elite registry tracks it and its birth flash is sent
			EliteMonsters.makeElite(mob, eliteTier, null, realmNear(level, pos));
			return mob;
		}
		return null;
	}

	/** The guardian kind: by the biome first, then by the tier (from Harta Langit up the second one is a fighter of men). */
	static EntityType<? extends Mob> type(Holder<Biome> biome, int tier, int index, RandomSource random) {
		if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS)) {
			return index == 1 ? EntityTypes.PARCHED : EntityTypes.HUSK;
		}
		if (biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.ICE_SPIKES) || biome.is(Biomes.FROZEN_PEAKS) || biome.is(Biomes.SNOWY_SLOPES)) {
			return EntityTypes.STRAY;
		}
		if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
			return EntityTypes.BOGGED;
		}
		if (tier >= 3 && index == 1) {
			return random.nextBoolean() ? EntityTypes.VINDICATOR : EntityTypes.SKELETON;
		}
		return random.nextInt(3) == 0 ? EntityTypes.ZOMBIE : EntityTypes.SPIDER;
	}

	private static int realmNear(ServerLevel level, BlockPos pos) {
		Vec3 at = Vec3.atCenterOf(pos);
		int best = 0;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.position().distanceTo(at) <= TreasureBirths.ABSENCE) {
				best = Math.max(best, Cultivators.get(player).realm().ordinal());
			}
		}
		return best;
	}

	// ================================================================== while the birth lives

	/** True when {@code entity} is a guardian of a live birth. */
	static boolean known(Entity entity) {
		return KNOWN.contains(entity.getUUID());
	}

	/** The living guardians within {@link #GUARD_RANGE} blocks of the treasure. */
	static int guarding(ServerLevel level, TreasureBirth record) {
		List<UUID> list = GUARDS.get(level.dimension());
		if (list == null || list.isEmpty()) {
			return 0;
		}
		Vec3 at = Vec3.atCenterOf(record.pos());
		int count = 0;
		for (UUID id : list) {
			Entity entity = level.getEntity(id);
			if (entity instanceof Mob mob && mob.isAlive() && mob.position().distanceTo(at) <= GUARD_RANGE) {
				count++;
			}
		}
		return count;
	}

	/** Every 20 ticks while the treasure stands: forgets the gone, walks the strays back, tells nearby players (every 40). */
	static void tick(ServerLevel level, TreasureBirth record, long now) {
		List<UUID> list = GUARDS.get(level.dimension());
		if (list == null || list.isEmpty()) {
			return;
		}
		Vec3 at = Vec3.atCenterOf(record.pos());
		for (int i = list.size() - 1; i >= 0; i--) {
			Entity entity = level.getEntity(list.get(i));
			if (entity == null) {
				// unloaded with its chunk: it stays known (and is dropped on load once the birth ended)
				continue;
			}
			if (!(entity instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) {
				KNOWN.remove(list.remove(i));
				continue;
			}
			LivingEntity target = mob.getTarget();
			boolean chasing = target != null && target.isAlive() && target.position().distanceTo(at) <= LEASH + 8.0D;
			if (!chasing && mob.position().distanceTo(at) > LEASH) {
				mob.setTarget(null);
				mob.getNavigation().moveTo(at.x, at.y, at.z, 1.15D);
			}
		}
		if (now % 40L < 20L) {
			sync(level, record);
		}
	}

	/** The guardian ids to every player within {@link #SYNC_RANGE} (an empty list once to those who had some). */
	private static void sync(ServerLevel level, TreasureBirth record) {
		List<UUID> list = GUARDS.getOrDefault(level.dimension(), List.of());
		int tier = TIERS.getOrDefault(level.dimension(), record.tier());
		List<Integer> ids = new ArrayList<>();
		for (UUID id : list) {
			Entity entity = level.getEntity(id);
			if (entity != null && entity.isAlive() && ids.size() < 8) {
				ids.add(entity.getId());
			}
		}
		Vec3 at = Vec3.atCenterOf(record.pos());
		int[] array = ids.stream().mapToInt(Integer::intValue).toArray();
		for (ServerPlayer player : List.copyOf(level.players())) {
			boolean near = player.position().distanceTo(at) <= SYNC_RANGE;
			if (near && array.length > 0) {
				SENT.add(player.getUUID());
				Net.send(player, new LangitFxPayload(LangitFxPayload.KIND_GUARDS, at.x, at.y, at.z, tier, array));
			} else if (SENT.remove(player.getUUID())) {
				Net.send(player, new LangitFxPayload(LangitFxPayload.KIND_GUARDS, at.x, at.y, at.z, tier, new int[0]));
			}
		}
	}

	/** {@code ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY}: a guardian fell (the burst, the advancement, the line). */
	static void onKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		if (!KNOWN.remove(killed.getUUID())) {
			return;
		}
		List<UUID> list = GUARDS.get(level.dimension());
		if (list != null) {
			list.remove(killed.getUUID());
		}
		int tier = TIERS.getOrDefault(level.dimension(), 1);
		LangitFxPayload fx = LangitFxPayload.at(LangitFxPayload.KIND_GUARD_FALL, killed.getX(), killed.getY(), killed.getZ(), tier);
		for (ServerPlayer viewer : List.copyOf(level.players())) {
			if (viewer.distanceToSqr(killed) <= 96.0D * 96.0D) {
				Net.send(viewer, fx);
			}
		}
		if (killer instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
			Advancements.grant(player, "dunia/treasure_guardian");
			boolean last = list == null || list.isEmpty();
			Msg.chat(player, Component.translatable(last ? "kultivator.langit.guardian.cleared" : "kultivator.langit.guardian.slain")
					.withColor(TreasureRewards.tierColor(tier)));
		}
	}

	// ================================================================== the end

	/** The birth of {@code level} ended: the remaining loaded guardians return to the earth, the clients forget them. */
	static void release(ServerLevel level) {
		List<UUID> list = GUARDS.remove(level.dimension());
		Integer tier = TIERS.remove(level.dimension());
		if (list != null) {
			for (UUID id : list) {
				KNOWN.remove(id);
				Entity entity = level.getEntity(id);
				if (entity != null && entity.isAlive()) {
					LangitFxPayload fx = LangitFxPayload.at(LangitFxPayload.KIND_GUARD_FALL, entity.getX(), entity.getY(), entity.getZ(),
							tier == null ? 1 : tier);
					for (ServerPlayer viewer : List.copyOf(level.players())) {
						if (viewer.distanceToSqr(entity) <= 96.0D * 96.0D) {
							Net.send(viewer, fx);
						}
					}
					entity.discard();
				}
			}
		}
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (SENT.remove(player.getUUID())) {
				Net.send(player, new LangitFxPayload(LangitFxPayload.KIND_GUARDS, 0.0D, 0.0D, 0.0D, 0, new int[0]));
			}
		}
	}

	/** {@code uji harta_lahir penjaga}: wakes the guardians of the live birth now (also a second time). */
	static int force(ServerLevel level, TreasureBirth record) {
		spawn(level, record);
		List<UUID> list = GUARDS.get(level.dimension());
		return list == null ? 0 : list.size();
	}

	static void clearAll() {
		GUARDS.clear();
		TIERS.clear();
		KNOWN.clear();
		SENT.clear();
	}

	private TreasureGuardians() {
	}
}
