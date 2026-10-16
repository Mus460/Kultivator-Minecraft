package dev.kultivator.technique;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wilayah Dao / Dao Domain (Dao Union+): for 20 s a domain of radius 10 follows the caster. Once per second the monsters
 * inside suffer the law of the caster's Dao ({@code DaoTechniques.domainPulse}); without a Dao it is plain qi pressure
 * (0.4 D and Slowness I).
 */
final class DaoDomain {

	static final double RADIUS = 10.0D;
	private static final int DURATION = 400;
	private static final int NO_DAO_COLOR = 0xE8DDB5;

	/** Game time each active domain ends. */
	private static final Map<UUID, Long> ENDS = new HashMap<>();

	static boolean cast(ServerPlayer player, CultivationData data) {
		if (data.domainActive() || ENDS.containsKey(player.getUUID())) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.dao_domain.already"));
		}
		ServerLevel level = player.level();
		ENDS.put(player.getUUID(), level.getGameTime() + DURATION);
		data.setDomainActive(true);
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);

		Dao dao = data.dao();
		Vec3 pos = player.position();
		Techniques.fx(player, Technique.DAO_DOMAIN, (dao == null ? 0 : dao.ordinal() + 1) << 4, pos, pos, (float) RADIUS);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 0.5F);
		Component name = dao == null
				? Component.translatable("kultivator.techniques.dao_domain.no_dao_name").withColor(NO_DAO_COLOR)
				: dao.displayName().copy().withColor(dao.color());
		Msg.actionBar(player, Component.translatable("kultivator.techniques.dao_domain.start", name,
				String.valueOf(DURATION / 20)).withColor(Techniques.GOLD));
		Advancements.grant(player, "dao_domain");
		pulse(player, data);
		return true;
	}

	static void tick(ServerPlayer player, CultivationData data) {
		Long end = ENDS.get(player.getUUID());
		if (end == null) {
			if (data.domainActive()) {
				end(player, false);
			}
			return;
		}
		long now = player.level().getGameTime();
		if (now >= end) {
			end(player, true);
			return;
		}
		if ((end - now) % 20 == 0) {
			pulse(player, data);
		}
	}

	/** One second of the domain's law on the monsters inside. */
	private static void pulse(ServerPlayer player, CultivationData data) {
		List<LivingEntity> inside = Targeting.hostilesAround(player, player.position(), RADIUS);
		Dao dao = data.dao();
		if (dao != null) {
			DaoTechniques.domainPulse(player, data, dao, inside);
			return;
		}
		float pressure = (float) (0.4D * Techniques.power(data));
		for (LivingEntity monster : inside) {
			if (Techniques.hurt(player, monster, pressure) && monster.isAlive()) {
				monster.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, false, true), player);
			}
		}
	}

	/** Closes the domain. {@code announce} plays the closing effect and message. */
	static void end(ServerPlayer player, boolean announce) {
		ENDS.remove(player.getUUID());
		CultivationData data = Cultivators.get(player);
		boolean was = data.domainActive();
		data.setDomainActive(false);
		if (!was) {
			return;
		}
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);
		if (!announce) {
			return;
		}
		Dao dao = data.dao();
		ServerLevel level = player.level();
		Vec3 pos = player.position();
		Techniques.fx(player, Technique.DAO_DOMAIN, 1 | (dao == null ? 0 : dao.ordinal() + 1) << 4, pos, pos, (float) RADIUS);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 0.6F);
		Msg.actionBar(player, Component.translatable("kultivator.techniques.dao_domain.end").withColor(Techniques.MUTED));
	}

	static void onLeave(ServerPlayer player) {
		end(player, false);
	}

	static void onDeath(ServerPlayer player) {
		end(player, false);
	}

	static void clearAll() {
		ENDS.clear();
	}

	private DaoDomain() {
	}
}
