package dev.kultivator.dunia.naga;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.fx.Fx;
import dev.kultivator.technique.DaoTechniques;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bloodline arts of tier 4+ beasts and the tier 6 owner passives (DESIGN 34.5.12), entity effects only:
 * <ul>
 * <li>wolf, Lolongan Bulan: while its owner fights, a moon howl puts Weakness on the monsters within 8 blocks (every 30 s);</li>
 * <li>fox, Api Rubah Langit: while its owner fights, foxfire leaps along up to 3 monsters within 10 blocks (every 12 s);</li>
 * <li>Jiao / true dragon, Napas Naga: the breath of {@link TamedJiao};</li>
 * <li>tier 6: the wolf gives +10 % cultivation XP while its owner meditates at night, the fox +5 % to every comprehension
 * gain, Yinglong +50 % flight stamina ({@link TamedJiao}).</li>
 * </ul>
 * The beasts only ever touch what their owner's techniques may hit ({@link Targeting}) and only {@code Enemy} monsters.
 */
final class BloodlineArts {

	public static final int HOWL_COOLDOWN = 600;
	public static final double HOWL_RANGE = 8.0D;
	public static final int FOXFIRE_COOLDOWN = 240;
	public static final double FOXFIRE_RANGE = 10.0D;
	public static final double WOLF_NIGHT_BONUS = 1.10D;
	public static final double FOX_COMPREHENSION_BONUS = 0.05D;
	/** Beasts this close to their owner give the tier 6 passives. */
	public static final double PASSIVE_RANGE = 32.0D;

	/** Beast UUID → game time its art is ready again. */
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** Player → comprehension per Dao seen at the last check (the fox passive adds 5 % of every rise). */
	private static final Map<UUID, Map<Dao, Double>> SEEN = new HashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(BloodlineArts::tick);
		Progression.addMultiplierProvider(BloodlineArts::wolfNight);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			READY.clear();
			SEEN.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0 || !Naga.config().arts) {
			return;
		}
		for (ServerPlayer owner : List.copyOf(server.getPlayerList().getPlayers())) {
			List<Mob> beasts = SpiritBeasts.loadedBeasts(owner.getUUID());
			boolean foxPassive = false;
			for (Mob beast : beasts) {
				BeastData data = SpiritBeasts.data(beast);
				if (data == null || data.tier() < 4 || beast.level() != owner.level()) {
					continue;
				}
				if (data.isFox() && data.tier() >= BeastData.TOP_TIER && beast.distanceToSqr(owner) <= PASSIVE_RANGE * PASSIVE_RANGE) {
					foxPassive = true;
				}
				if (!inFight(owner) || BeastTribulation.running(beast)) {
					continue;
				}
				long now = owner.level().getGameTime();
				Long ready = READY.get(beast.getUUID());
				if (ready != null && now < ready) {
					continue;
				}
				boolean used = data.isFox() ? foxfire((ServerLevel) beast.level(), beast, owner, data.tier())
						: howl((ServerLevel) beast.level(), beast, owner, data.tier());
				if (used) {
					READY.put(beast.getUUID(), now + (data.isFox() ? FOXFIRE_COOLDOWN : HOWL_COOLDOWN));
				}
			}
			if (server.getTickCount() % 100 == 0) {
				foxComprehension(owner, foxPassive);
			}
		}
		if (READY.size() > 512) {
			READY.clear();
		}
	}

	/** The owner hit something or was hit in the last 5 s. */
	private static boolean inFight(ServerPlayer owner) {
		return owner.getLastHurtMob() != null && owner.tickCount - owner.getLastHurtMobTimestamp() <= 100
				|| owner.getLastHurtByMob() != null && owner.tickCount - owner.getLastHurtByMobTimestamp() <= 100;
	}

	/** Lolongan Bulan: Weakness on the monsters within 8 blocks of the wolf (I at tier 4, II from 5). */
	private static boolean howl(ServerLevel level, Mob wolf, ServerPlayer owner, int tier) {
		List<LivingEntity> foes = Targeting.hostilesAround(owner, wolf.position(), HOWL_RANGE, false);
		foes.removeIf(foe -> !(foe instanceof Enemy) || foe == wolf);
		if (foes.isEmpty()) {
			return false;
		}
		for (LivingEntity foe : foes) {
			foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, tier >= 5 ? 1 : 0, false, true), wolf);
		}
		wolf.playAmbientSound();
		level.playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.0F, 0.55F);
		level.playSound(null, wolf.getX(), wolf.getY(), wolf.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 0.5F);
		Fx.ring(level, wolf.position().add(0.0D, 0.2D, 0.0D), SpiritBeasts.tierColor(BeastData.WOLF, tier), (float) HOWL_RANGE);
		NagaPayloads.moment(level, NagaPayloads.MOMENT_HOWL, wolf.getId(), wolf.position(), 0, tier, BeastData.WOLF, wolf.getId(), owner, 64.0D);
		return true;
	}

	/** Api Rubah Langit: foxfire leaps from the fox along up to 3 monsters (fire on the entities only, never a block). */
	private static boolean foxfire(ServerLevel level, Mob fox, ServerPlayer owner, int tier) {
		List<LivingEntity> foes = Targeting.hostilesAround(owner, fox.position(), FOXFIRE_RANGE, false);
		foes.removeIf(foe -> !(foe instanceof Enemy) || foe == fox);
		if (foes.isEmpty()) {
			return false;
		}
		int color = SpiritBeasts.tierColor(BeastData.FOX, tier);
		float damage = 4.0F + 2.0F * (tier - 3);
		Vec3 from = fox.position().add(0.0D, fox.getBbHeight() * 0.6D, 0.0D);
		int hit = 0;
		for (LivingEntity foe : foes) {
			if (hit >= 3) {
				break;
			}
			Vec3 to = foe.getBoundingBox().getCenter();
			Fx.beam(level, from, to, color, 0.14F);
			Fx.burst(level, to, color, 8, 0.6F);
			foe.hurtServer(level, fox.damageSources().mobAttack(fox), damage);
			DaoTechniques.ignite(foe, 3.0F + tier - 3);
			from = to;
			hit++;
		}
		level.playSound(null, fox.getX(), fox.getY(), fox.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.9F, 1.3F);
		NagaPayloads.moment(level, NagaPayloads.MOMENT_FOXFIRE, fox.getId(), fox.position(), 0, tier, BeastData.FOX, hit, owner, 64.0D);
		return true;
	}

	/** The wolf's tier 6 passive: +10 % cultivation XP while its owner meditates at night with it near. */
	private static double wolfNight(ServerPlayer player) {
		if (!Naga.config().arts || !player.level().isDarkOutside() || !Meditation.isMeditating(player)) {
			return 1.0D;
		}
		for (Mob beast : SpiritBeasts.loadedBeasts(player.getUUID())) {
			BeastData data = SpiritBeasts.data(beast);
			if (data != null && data.isWolf() && data.tier() >= BeastData.TOP_TIER && beast.level() == player.level()
					&& beast.distanceToSqr(player) <= PASSIVE_RANGE * PASSIVE_RANGE) {
				return WOLF_NIGHT_BONUS;
			}
		}
		return 1.0D;
	}

	/**
	 * The fox's tier 6 passive: every 5 s the comprehension rises of the last check get 5 % more (the root affinity of
	 * the original gain is not counted twice).
	 */
	private static void foxComprehension(ServerPlayer player, boolean active) {
		CultivationData data = Cultivators.get(player);
		Map<Dao, Double> now = new EnumMap<>(Dao.class);
		for (Dao dao : Dao.values()) {
			now.put(dao, data.comprehension(dao));
		}
		Map<Dao, Double> before = SEEN.put(player.getUUID(), now);
		if (!active || before == null) {
			return;
		}
		for (Dao dao : Dao.values()) {
			double rise = now.get(dao) - before.getOrDefault(dao, now.get(dao));
			if (rise > 1.0E-6D) {
				double bonus = rise * FOX_COMPREHENSION_BONUS;
				if (Comprehension.hasAffinity(data, dao)) {
					bonus /= Comprehension.AFFINITY_FACTOR;
				}
				double gained = Comprehension.grant(player, dao, bonus);
				now.put(dao, now.get(dao) + gained);
			}
		}
	}

	private BloodlineArts() {
	}
}
