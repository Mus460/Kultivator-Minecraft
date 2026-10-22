package dev.kultivator.sekte.hubungan;

import dev.kultivator.data.Rank;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The effects of ties (DESIGN 36.2.2), through the existing {@code DiscipleHooks}; each disciple uses only its strongest
 * tie (Pasangan Dao, then Saudara Angkat, Kekasih, Sahabat, Dendam, Saingan; the closest one of that kind):
 * <ul>
 * <li>Sahabat within 16 blocks: damage x1.05. Saudara Angkat within 16 blocks: damage x1.08, and a sibling that is struck
 * is defended (the other takes the attacker as its target).</li>
 * <li>Kekasih or Pasangan Dao within 8 blocks: meditation XP x1.10. A Dao companion who stands within 8 blocks of a
 * breakthrough guards it like a master: quality +0.05, tribulation strikes x0.9 (no bond is given); when the
 * breakthrough starts it walks over.</li>
 * <li>Saingan: combat and trial XP x1.06 (the rival spurs it on).</li>
 * <li>Saving each other: a disciple that kills the monster hunting a hurt sect mate gains +8 with that mate.</li>
 * </ul>
 * The strongest tie and the partner's body are cached per soul for 10 s (and dropped whenever the pair changes), so the
 * hooks stay O(1) per call (36.3.9).
 */
final class EfekIkatan {

	private static final long TTL = 200L;
	private static final double FIGHT_RANGE = 16.0D;
	private static final double CLOSE_RANGE = 8.0D;

	private record Effect(Ikatan kind, @Nullable UUID partner, WeakReference<DiscipleEntity> body, long until) {
		@Nullable DiscipleEntity partnerNear(DiscipleEntity self, double range) {
			DiscipleEntity other = body.get();
			if (other == null || other.isRemoved() || !other.isAlive() || other.level() != self.level()) {
				return null;
			}
			return other.distanceToSqr(self) <= range * range ? other : null;
		}
	}

	private static final Effect NONE = new Effect(Ikatan.NONE, null, new WeakReference<>(null), Long.MAX_VALUE);
	private static final Map<UUID, Effect> CACHE = new HashMap<>();
	/** Sworn kin that came to a sibling's defence recently (soul -> game time). */
	private static final Map<UUID, Long> DEFENDED = new HashMap<>();

	static void init() {
		DiscipleHooks.addDamageFactor(EfekIkatan::damage);
		DiscipleHooks.addXpFactor(EfekIkatan::xp);
		DiscipleHooks.addOnHurt(EfekIkatan::onHurt);
		DiscipleHooks.addOnKill(EfekIkatan::onKill);
		DiscipleHooks.addQualityBonus(EfekIkatan::quality);
		DiscipleHooks.addStrikeFactor(EfekIkatan::strike);
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onStart(DiscipleEntity entity, DiscipleBreakthrough.Kind kind, Rank target) {
				guardBreakthrough(entity);
			}
		});
	}

	static void invalidate(@Nullable UUID soul) {
		if (soul != null) {
			CACHE.remove(soul);
		}
	}

	static void clear() {
		CACHE.clear();
		DEFENDED.clear();
	}

	private static boolean on() {
		return Hubungan.active() && HubunganConfig.get().efekAktif;
	}

	private static Effect of(DiscipleEntity entity) {
		if (entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return NONE;
		}
		long now = entity.level().getGameTime();
		UUID soul = entity.soulId();
		Effect cached = CACHE.get(soul);
		if (cached != null && cached.until() > now) {
			return cached;
		}
		UUID owner = entity.ownerId();
		Effect effect = NONE;
		if (owner != null && TabelPasangan.available(owner)) {
			TabelPasangan.Pair best = null;
			for (TabelPasangan.Pair pair : TabelPasangan.of(owner, soul)) {
				int rank = Kecocokan.effectRank(pair.kind());
				if (rank <= 0) {
					continue;
				}
				if (best == null || rank > Kecocokan.effectRank(best.kind())
						|| rank == Kecocokan.effectRank(best.kind()) && Math.abs(pair.affinity()) > Math.abs(best.affinity())) {
					best = pair;
				}
			}
			if (best != null) {
				UUID partner = best.other(soul);
				DiscipleEntity body = null;
				for (DiscipleEntity d : dev.kultivator.disciple.Disciples.loaded(owner)) {
					if (d.soulId().equals(partner)) {
						body = d;
						break;
					}
				}
				effect = new Effect(best.kind(), partner, new WeakReference<>(body), now + TTL);
			} else {
				effect = new Effect(Ikatan.NONE, null, new WeakReference<>(null), now + TTL);
			}
		}
		if (CACHE.size() > 512) {
			CACHE.clear();
		}
		CACHE.put(soul, effect);
		return effect;
	}

	// ================================================================== hooks

	private static double damage(DiscipleEntity entity, LivingEntity target, boolean technique) {
		if (!on()) {
			return 1.0D;
		}
		Effect effect = of(entity);
		if (effect.kind() == Ikatan.SAUDARA_ANGKAT && effect.partnerNear(entity, FIGHT_RANGE) != null) {
			return 1.08D;
		}
		if (effect.kind() == Ikatan.SAHABAT && effect.partnerNear(entity, FIGHT_RANGE) != null) {
			return 1.05D;
		}
		return 1.0D;
	}

	private static double xp(@Nullable DiscipleEntity entity, DiscipleData data, XpSource source) {
		if (entity == null || !on()) {
			return 1.0D;
		}
		Effect effect = of(entity);
		if (source == XpSource.MEDITATION && (effect.kind() == Ikatan.KEKASIH || effect.kind() == Ikatan.PASANGAN_DAO)
				&& effect.partnerNear(entity, CLOSE_RANGE) != null) {
			return 1.10D;
		}
		if ((source == XpSource.COMBAT || source == XpSource.TRIAL) && effect.kind() == Ikatan.SAINGAN) {
			return 1.06D;
		}
		return 1.0D;
	}

	private static void onHurt(DiscipleEntity entity, DamageSource source, float damage) {
		if (!on() || !HubunganConfig.get().saudaraMembela || damage <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker)) {
			return;
		}
		if (attacker instanceof DiscipleEntity || attacker instanceof ServerPlayer player && entity.isOwnedBy(player)) {
			return;
		}
		if (DuelSessions.byEntity(entity) != null || DuelRules.inDuel(entity)) {
			return;
		}
		Effect effect = of(entity);
		if (effect.kind() != Ikatan.SAUDARA_ANGKAT) {
			return;
		}
		DiscipleEntity sibling = effect.partnerNear(entity, FIGHT_RANGE);
		if (sibling == null || sibling.getTarget() != null || sibling.data().breakingThrough() || DuelSessions.byEntity(sibling) != null
				|| !sibling.mayAttack(attacker)) {
			return;
		}
		long now = entity.level().getGameTime();
		Long last = DEFENDED.get(sibling.soulId());
		if (last != null && now - last < 60L) {
			return;
		}
		DEFENDED.put(sibling.soulId(), now);
		if (DEFENDED.size() > 256) {
			DEFENDED.clear();
		}
		sibling.setTarget(attacker);
		VoiceDisciples.line(sibling, "protect", 600);
	}

	private static void onKill(DiscipleEntity entity, LivingEntity victim) {
		if (!Hubungan.active() || !(victim instanceof Mob mob) || !(mob.getTarget() instanceof DiscipleEntity saved) || saved == entity) {
			return;
		}
		UUID owner = entity.ownerId();
		if (owner == null || !owner.equals(saved.ownerId()) || saved.role() != DiscipleEntity.Role.DISCIPLE
				|| saved.getHealth() > saved.getMaxHealth() * 0.6F) {
			return;
		}
		int applied = Hubungan.change(owner, entity.soulId(), saved.soulId(), 8, SekteIds.R + ".selamat", Hubungan.EVENT);
		if (applied > 0) {
			HubunganPayloads.bubble(entity, saved, HubunganPayloads.ICON_BINTANG, 60);
		}
	}

	private static double quality(DiscipleEntity entity, Rank target) {
		if (!on()) {
			return 0.0D;
		}
		Effect effect = of(entity);
		return effect.kind() == Ikatan.PASANGAN_DAO && effect.partnerNear(entity, CLOSE_RANGE) != null ? 0.05D : 0.0D;
	}

	private static double strike(DiscipleEntity entity, Rank target, int index) {
		if (!on()) {
			return 1.0D;
		}
		Effect effect = of(entity);
		return effect.kind() == Ikatan.PASANGAN_DAO && effect.partnerNear(entity, CLOSE_RANGE) != null ? 0.9D : 1.0D;
	}

	/** A breakthrough begins: the Dao companion (within 32 blocks, free) walks over to stand guard. */
	private static void guardBreakthrough(DiscipleEntity entity) {
		if (!on()) {
			return;
		}
		invalidate(entity.soulId());
		Effect effect = of(entity);
		if (effect.kind() != Ikatan.PASANGAN_DAO) {
			return;
		}
		DiscipleEntity partner = effect.partnerNear(entity, 32.0D);
		if (partner == null || partner.getTarget() != null || partner.data().breakingThrough() || partner.activity() != 0
				|| DuelSessions.byEntity(partner) != null) {
			return;
		}
		partner.getNavigation().moveTo(entity, 1.1D);
		VoiceDisciples.line(partner, "protect", 600);
		ServerPlayer master = entity.owner();
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.hubungan.jaga", partner.getName(), entity.getName()).withColor(0xFF9CC4));
		}
	}

	private EfekIkatan() {
	}
}
