package dev.kultivator.technique;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.technique.spec.run.Caster;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wilayah Dao / Dao Domain (DESIGN 22.10): a domain of radius lin(10, 2, 24) (+20% at Kesempurnaan Agung) follows the
 * caster for 400 ticks + 40 per realm above Dao Union (at most 800). Every pulse (20 ticks, 10 at Puncak Jurus; one query,
 * height ≤ 16 when the radius is above 16) the monsters inside suffer the law of the caster's Dao
 * ({@code DaoTechniques.domainPulse}, coefficients × P; 22.10.1); without a Dao it is qi pressure (0.4 × P and Slowness I).
 */
final class DaoDomain {

	static final double BASE_RADIUS = 10.0D;
	private static final int NO_DAO_COLOR = 0xE8DDB5;

	private static final class Active {
		final Caster ctx;
		final long end;
		final double radius;
		final int pulse;
		int pulses;

		Active(Caster ctx, long end, double radius, int pulse) {
			this.ctx = ctx;
			this.end = end;
			this.radius = radius;
			this.pulse = pulse;
		}
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	static boolean cast(Caster ctx, ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (data.domainActive() || ACTIVE.containsKey(player.getUUID())) {
			return Techniques.refuse(player, Component.translatable("kultivator.techniques.dao_domain.already"));
		}
		ServerLevel level = player.level();
		double radius = ctx.arena() ? BASE_RADIUS : Math.min(dev.kultivator.technique.spec.TechniquePower.radiusCap(false),
				ctx.lin(BASE_RADIUS, 2.0D, 24.0D) * (ctx.perfection(dev.kultivator.technique.spec.dsl.Bonus.Kind.AREA) ? 1.2D : 1.0D));
		int above = Math.max(0, data.realm().ordinal() - Realm.DAO_UNION.ordinal());
		int duration = ctx.arena() ? 400 : Math.min(800, 400 + 40 * above) + 0;
		int pulse = ctx.special("pulse_10") ? 10 : 20;
		ACTIVE.put(player.getUUID(), new Active(ctx, level.getGameTime() + duration, radius, pulse));
		data.setDomainActive(true);
		Cultivators.refreshVisual(player);
		Cultivators.dirty(player);
		ctx.keepAlive(duration + 1);

		Dao dao = data.dao();
		Vec3 pos = player.position();
		Techniques.fx(player, Technique.DAO_DOMAIN, (dao == null ? 0 : dao.ordinal() + 1) << 4, pos, pos, (float) radius);
		// the domain rides its caster: FLAG_FOLLOW on its CAST and PULSE events, the real radius (DESIGN 27.1 S9)
		ctx.followCaster();
		ctx.castFx(pos, pos, (float) radius, (float) Math.min(16.0D, radius), dao == null ? 0 : dao.ordinal() + 1, duration);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 0.5F);
		Component name = dao == null
				? Component.translatable("kultivator.techniques.dao_domain.no_dao_name").withColor(NO_DAO_COLOR)
				: dao.displayName().copy().withColor(dao.color());
		Msg.actionBar(player, Component.translatable("kultivator.techniques.dao_domain.start", name,
				String.valueOf(duration / 20)).withColor(Techniques.GOLD));
		if (!ctx.copy()) {
			Advancements.grant(player, "dao_domain");
		}
		pulse(player, data, ACTIVE.get(player.getUUID()));
		return true;
	}

	static void tick(ServerPlayer player, CultivationData data) {
		Active active = ACTIVE.get(player.getUUID());
		if (active == null) {
			if (data.domainActive()) {
				end(player, false);
			}
			return;
		}
		long now = player.level().getGameTime();
		if (now >= active.end || active.ctx.ended() && active.ctx.copy()) {
			end(player, true);
			return;
		}
		if ((active.end - now) % active.pulse == 0) {
			pulse(player, data, active);
		}
	}

	/** One pulse of the domain's law on the monsters inside (one query; height ≤ 16 when the radius is above 16). */
	private static void pulse(ServerPlayer player, CultivationData data, Active active) {
		double height = active.radius > 16.0D ? 16.0D : active.radius;
		List<LivingEntity> inside = new ArrayList<>();
		Vec3 centre = player.position();
		for (LivingEntity entity : active.ctx.hostiles(centre, active.radius, false)) {
			if (Math.abs(entity.getY() - centre.y) <= height) {
				inside.add(entity);
			}
		}
		DaoTechniques.domainPulse(active.ctx, data.dao(), inside, active.radius, 1.0D, active.pulses++);
		active.ctx.fx(dev.kultivator.technique.spec.dsl.Phase.PULSE, centre, centre, (float) active.radius, 0.0F, inside.size(), active.pulse);
	}

	/** True when {@code pos} lies inside the player's Dao Domain. */
	static boolean contains(ServerPlayer player, Vec3 pos) {
		Active active = ACTIVE.get(player.getUUID());
		return active != null && pos.distanceTo(player.position()) <= active.radius;
	}

	/** The domain's radius (0 when none), for the HUD and IN_AREA. */
	static double radius(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active == null ? 0.0D : active.radius;
	}

	static long endTick(ServerPlayer player) {
		Active active = ACTIVE.get(player.getUUID());
		return active == null ? 0L : active.end;
	}

	/** Closes the domain. {@code announce} plays the closing effect and message. */
	static void end(ServerPlayer player, boolean announce) {
		ACTIVE.remove(player.getUUID());
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
		Techniques.fx(player, Technique.DAO_DOMAIN, 1 | (dao == null ? 0 : dao.ordinal() + 1) << 4, pos, pos, (float) BASE_RADIUS);
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
		ACTIVE.clear();
	}

	private DaoDomain() {
	}
}
