package dev.kultivator.cultivation;

import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.talisman.TalismanFxIds;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Perkokoh Fondasi / Strengthen Foundation (DESIGN 19.3.11): raises the lowest recorded breakthrough quality for Stored
 * Qi. Started while meditating on a qi formation of tier 2+, not deviating and not breaking through; a 30 s ritual that
 * is cancelled at no cost when the meditation stops. At the end it pays {@code 3 × Rank(realm, 0, 3).xpRequired()} of
 * the target realm from Stored Qi; Cracked→Normal always, Normal→Perfect 60%, Perfect→Heavenly 25%; a failure refunds
 * half.
 * <p>
 * Sumber: ISSTH Book 2 ch. 134 (Meng Hao already has a Perfect Foundation and still lacks a fourth Dao Pillar, so a
 * foundation can gain pillars after it is built); grades ISSTH ch. 76 and 100. Costs and chances are game design.
 */
public final class Reforge {

	/** Length of the ritual. */
	public static final int RITUAL_TICKS = 600;
	/** Cost multiplier of {@code Rank(target, 0, 3).xpRequired()}. */
	public static final double COST_FACTOR = 3.0D;
	/** The qi formation tier the seat needs. */
	public static final int MIN_FORMATION_TIER = 2;
	/** Success chance by the current quality (Cracked, Normal, Perfect). */
	public static final double[] CHANCE = {1.0D, 0.6D, 0.25D};
	/** Share of the cost given back after a failure. */
	public static final double REFUND = 0.5D;
	private static final String REFORGES_KEY = "reforges";

	private record Ritual(Realm target, long end, Vec3 pos) {
	}

	private static final Map<UUID, Ritual> RITUALS = new HashMap<>();

	/** Called by {@link ImmortalPerks#init()}. */
	public static void init() {
		ServerPlayerEvents.LEAVE.register(player -> RITUALS.remove(player.getUUID()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				RITUALS.remove(player.getUUID());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RITUALS.clear());
	}

	/** Stored Qi one attempt costs (0 when nothing can be strengthened). */
	public static double cost(CultivationData data) {
		Realm target = target(data);
		return target == null ? 0.0D : costOf(target);
	}

	/** {@code 3 × Rank(realm, 0, 3).xpRequired()}: Golden Core 9,225, Dao Union 184,500, Immortal Ascension 369,000. */
	public static double costOf(Realm realm) {
		return COST_FACTOR * new Rank(realm, 0, Rank.PEAK).xpRequired();
	}

	/** The realm whose quality an attempt would raise (the lowest recorded, earliest on ties), null when all are Heavenly. */
	public static @Nullable Realm target(CultivationData data) {
		Realm best = null;
		int lowest = Integer.MAX_VALUE;
		for (Realm realm : Realm.values()) {
			if (realm.isMortal() || !data.realm().atLeast(realm)) {
				continue;
			}
			BreakthroughQuality quality = data.quality(realm);
			if (quality == null || quality == BreakthroughQuality.HEAVENLY) {
				continue;
			}
			if (quality.ordinal() < lowest) {
				lowest = quality.ordinal();
				best = realm;
			}
		}
		return best;
	}

	/** Success chance of raising {@code quality} one step. */
	public static double chance(BreakthroughQuality quality) {
		return quality.ordinal() < CHANCE.length ? CHANCE[quality.ordinal()] : 0.0D;
	}

	/** Finished strengthening rituals so far, successful or not ({@code extra.kultivasi.reforges}). */
	public static int count(CultivationData data) {
		return CaveAbode.section(data).getIntOr(REFORGES_KEY, 0);
	}

	/** True while the player's ritual runs. */
	public static boolean active(ServerPlayer player) {
		return RITUALS.containsKey(player.getUUID());
	}

	/** Action {@code reforge} and {@code fondasi perkokoh}: starts the 30 s ritual or tells why not. */
	public static boolean start(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (active(player)) {
			return refuse(player, Component.translatable("kultivator.kultivasi.reforge.running"));
		}
		if (!data.meditating() || data.formationTier() < MIN_FORMATION_TIER) {
			return refuse(player, Component.translatable("kultivator.kultivasi.reforge.need_formation", MIN_FORMATION_TIER));
		}
		if (data.deviating()) {
			return refuse(player, Component.translatable("kultivator.kultivasi.reforge.deviating"));
		}
		if (data.breakingThrough() || Breakthrough.isBusy(player)) {
			return refuse(player, Component.translatable("kultivator.kultivasi.reforge.busy"));
		}
		Realm target = target(data);
		if (target == null) {
			return refuse(player, Component.translatable("kultivator.kultivasi.reforge.nothing"));
		}
		double cost = costOf(target);
		if (data.storedQi() + 1.0E-6D < cost) {
			return refuse(player, Component.translatable("kultivator.kultivasi.reforge.cost", Msg.number(data.storedQi()), Msg.number(cost)));
		}
		ServerLevel level = player.level();
		long end = level.getGameTime() + RITUAL_TICKS;
		Vec3 pos = player.position();
		RITUALS.put(player.getUUID(), new Ritual(target, end, pos));
		data.sessionExtra().putLong("reforge_end", end);
		Cultivators.dirty(player);
		Fx.send(level, pos, FxType.BREAKTHROUGH_START, target.ordinal(), RITUAL_TICKS, 0.0F);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9F, 0.8F);
		BreakthroughQuality quality = data.quality(target);
		Msg.chat(player, Component.translatable("kultivator.kultivasi.reforge.start", target.displayName().copy().withColor(target.color()),
				quality == null ? Component.empty() : quality.displayName().copy().withColor(quality.color()),
				Math.round(chance(quality == null ? BreakthroughQuality.CRACKED : quality) * 100.0D), Msg.number(cost), RITUAL_TICKS / 20)
				.withColor(0xFFD54A));
		return true;
	}

	/** Every tick for each player (from {@link ImmortalPerks#tick}): the ritual's sounds, cancellation and end. */
	public static void tick(ServerPlayer player, CultivationData data) {
		Ritual ritual = RITUALS.get(player.getUUID());
		if (ritual == null) {
			return;
		}
		ServerLevel level = player.level();
		if (!data.meditating() || data.deviating() || data.breakingThrough() || !player.isAlive()) {
			cancel(player, data, ritual);
			return;
		}
		long left = ritual.end() - level.getGameTime();
		if (left % 20 == 0 && left > 0) {
			float progress = 1.0F - (float) left / RITUAL_TICKS;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS,
					0.4F + progress * 0.5F, 0.6F + progress * 0.8F);
		}
		if (left <= 0) {
			finish(player, data, ritual);
		}
	}

	private static void cancel(ServerPlayer player, CultivationData data, Ritual ritual) {
		RITUALS.remove(player.getUUID());
		data.sessionExtra().remove("reforge_end");
		Cultivators.dirty(player);
		Fx.send(player.level(), ritual.pos(), FxType.BREAKTHROUGH_START, 0, 0, 0.0F);
		if (player.isAlive()) {
			Msg.chat(player, Component.translatable("kultivator.kultivasi.reforge.cancelled").withStyle(ChatFormatting.GRAY));
		}
	}

	private static void finish(ServerPlayer player, CultivationData data, Ritual ritual) {
		RITUALS.remove(player.getUUID());
		data.sessionExtra().remove("reforge_end");
		ServerLevel level = player.level();
		Fx.send(level, ritual.pos(), FxType.BREAKTHROUGH_START, 0, 0, 0.0F);
		Realm target = ritual.target();
		BreakthroughQuality quality = data.quality(target);
		double cost = costOf(target);
		if (quality == null || quality == BreakthroughQuality.HEAVENLY) {
			Msg.chat(player, Component.translatable("kultivator.kultivasi.reforge.nothing").withStyle(ChatFormatting.GRAY));
			Cultivators.dirty(player);
			return;
		}
		if (data.storedQi() + 1.0E-6D < cost) {
			Msg.chat(player, Component.translatable("kultivator.kultivasi.reforge.cost", Msg.number(data.storedQi()), Msg.number(cost))
					.withColor(0xFF9A7A));
			Cultivators.dirty(player);
			return;
		}
		data.setStoredQi(Math.max(0.0D, data.storedQi() - cost));
		CaveAbode.section(data).putInt(REFORGES_KEY, count(data) + 1);
		Vec3 pos = player.position();
		if (player.getRandom().nextDouble() < chance(quality)) {
			BreakthroughQuality next = BreakthroughQuality.values()[quality.ordinal() + 1];
			data.setQuality(target, next);
			Stats.apply(player);
			Qi.clamp(player);
			Fx.send(level, pos, FxType.TALISMAN, TalismanFxIds.REFORGE, next.ordinal(), 0.0F);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.1F, 0.9F);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
			Msg.title(player, Component.translatable("kultivator.kultivasi.reforge.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.translatable("kultivator.kultivasi.reforge.subtitle", target.displayName(), next.displayName().copy()
							.withColor(next.color())), 10, 60, 20);
			Msg.chat(player, Component.translatable("kultivator.kultivasi.reforge.success", target.displayName().copy().withColor(target.color()),
					quality.displayName().copy().withColor(quality.color()), next.displayName().copy().withColor(next.color()),
					Msg.number(cost)).withColor(0xFFD54A));
			Advancements.grant(player, "foundation_strengthened");
		} else {
			double refund = cost * REFUND;
			data.setStoredQi(data.storedQi() + refund);
			Stats.apply(player);
			Qi.clamp(player);
			Fx.send(level, pos, FxType.TALISMAN, TalismanFxIds.REFORGE, -1, 0.0F);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
			Msg.chat(player, Component.translatable("kultivator.kultivasi.reforge.failed", target.displayName().copy().withColor(target.color()),
					Msg.number(cost - refund), Msg.number(refund)).withColor(0xFF9A7A));
		}
		Cultivators.refreshVisual(player);
		Cultivators.syncNow(player);
	}

	private static boolean refuse(ServerPlayer player, Component reason) {
		Msg.chat(player, reason.copy().withColor(0xFF9A7A));
		return false;
	}

	private Reforge() {
	}
}
