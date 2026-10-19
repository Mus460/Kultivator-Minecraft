package dev.kultivator.disciple.power;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.technique.DaoTechniques;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Jurus Pamungkas, the signature ultimates (DESIGN 20.3.16): unlocked at Nascent Soul for the disciple's Dao, cast by
 * themselves in a fight when a boss or 3+ monsters are within 12 blocks, or ordered from the Bakat tab. Cooldown 120 s
 * (the lotus 45 s). One effect packet per cast, damage pulses every 10 ticks with one area query each, monsters only
 * ({@code DiscipleTechniques.mayHit}), never centred within 4 blocks of the master or a villager, read-only raycasts.
 * U = {@code DiscipleTechniques.power} times every {@code damageFactor} hook (applied by {@link PowerCore#hurt}).
 * <ul>
 * <li>Pedang, Formasi Pedang Geng Agung (Fanren: Han Li's 72 Azure Bamboo Cloudswarm Swords, the Great Geng Sword
 * Formation)</li>
 * <li>Petir, Petir Pengusir Kejahatan (Fanren: the Evil-Dispelling Divine Lightning of the Golden Thunder Bamboo)</li>
 * <li>Ruang, Kurungan Larangan (Renegade Immortal ch. 216: Wang Lin's restrictions)</li>
 * <li>Tubuh, Tinju Kaisar Abadi (A Will Eternal: the Undying Emperor's Fist)</li>
 * <li>Api, Teratai Api Amarah Buddha (BTTH: Xiao Yan's lotus of fused flames), with the master's Fusi</li>
 * <li>Kehidupan, Kitab Kehancuran Agung, block-safe (Martial Universe: the Great Desolation Scripture)</li>
 * <li>Es and Bayangan: the Dao's book technique (Glacier Prison, Gale), +60% damage and twice the radius</li>
 * </ul>
 */
final class Ultimates {

	public static final int COOLDOWN = 2400;
	public static final int LOTUS_COOLDOWN = 900;
	public static final double AUTO_RANGE = 12.0D;
	public static final int AUTO_CROWD = 3;
	public static final double ORDER_RANGE = 16.0D;
	// Pedang
	public static final double SWORD_RADIUS = 5.0D;
	public static final int SWORD_TICKS = 120;
	public static final double SWORD_PULSE_U = 0.4D;
	// Petir
	public static final int BOLTS = PowerIds.ULT_BOLTS;
	public static final double BOLT_RANGE = 16.0D;
	public static final double BOLT_U = 2.5D;
	public static final double UNDEAD_FACTOR = 1.5D;
	public static final double CHAIN_RANGE = 6.0D;
	public static final double CHAIN_U = 1.25D;
	// Ruang
	public static final double PRISON_RADIUS = 7.0D;
	public static final int PRISON_TICKS = 80;
	/** 1 U per second, as two pulses. */
	public static final double PRISON_PULSE_U = 0.5D;
	// Tubuh
	public static final double FIST_ATTACK = 6.0D;
	public static final double FIST_REACH = 4.0D;
	public static final double FIST_WAVE = 4.0D;
	public static final double FIST_WAVE_U = 2.0D;
	// Api
	public static final int LOTUS_CHARGE = 80;
	public static final double LOTUS_RANGE = 24.0D;
	public static final double LOTUS_RADIUS = 4.0D;
	public static final double LOTUS_RADIUS_FLAMES = 6.0D;
	public static final double LOTUS_U = 3.0D;
	public static final float LOTUS_FIRE = 6.0F;
	public static final double FUSION_U = 5.0D;
	public static final double FUSION_RADIUS = 6.0D;
	public static final double FUSION_QI = 0.3D;
	public static final double FUSION_RANGE = 6.0D;
	// Kehidupan
	public static final double LIFE_RADIUS = 10.0D;
	public static final double LIFE_U = 1.5D;
	public static final float LIFE_ALLY_HEAL = 0.3F;
	public static final int LIFE_BUFF_TICKS = 600;
	public static final double LIFE_BUFF = 1.3D;
	// Es, Bayangan
	public static final double BOOK_POWER = 1.6D;
	public static final double BOOK_RANGE = 2.0D;

	private static final Identifier PRISON_ID = DisciplePowers.id("restriction_prison");

	static void register() {
		dev.kultivator.disciple.DiscipleHooks.addDamageFactor((entity, target, technique) -> PowerCore.powered(entity)
				&& DisciplePowers.live(entity).lifeBuffUntil > PowerCore.now(entity) ? LIFE_BUFF : 1.0D);
	}

	/** True once the disciple has its Dao's signature ultimate (Nascent Soul and a chosen Dao). */
	static boolean has(DiscipleData data) {
		return data.realm().atLeast(Realm.NASCENT_SOUL) && data.dao() != null;
	}

	static int cooldownFor(@Nullable Dao dao) {
		return dao == Dao.FIRE ? LOTUS_COOLDOWN : COOLDOWN;
	}

	/** The ultimate's translation id for the disciple's Dao. */
	static String idFor(@Nullable Dao dao) {
		return dao == null ? "none" : dao.id();
	}

	// ================================================================== triggers

	/** In a fight: a boss or 3+ monsters within 12 blocks and the ultimate ready. */
	static boolean tryAuto(DiscipleEntity entity, LivingEntity target) {
		DiscipleData data = entity.data();
		ServerPlayer master = entity.owner();
		if (master == null || !has(data) || !PowerCore.ready(data, "ult", PowerCore.now(entity)) || DisciplePowers.live(entity).lotus != null) {
			return false;
		}
		boolean worthy = PowerCore.isBoss(target) && entity.distanceToSqr(target) <= AUTO_RANGE * AUTO_RANGE;
		if (!worthy) {
			List<LivingEntity> around = PowerCore.hostiles(entity, master, entity.position(), AUTO_RANGE);
			worthy = around.size() >= AUTO_CROWD;
			for (int i = 0; !worthy && i < around.size(); i++) {
				worthy = PowerCore.isBoss(around.get(i));
			}
		}
		return worthy && cast(entity, master, target, false) == null;
	}

	/** The master's order from the Bakat tab: null when it was cast, else a reason key (without the prefix). */
	static @Nullable String order(DiscipleEntity entity, ServerPlayer master) {
		DiscipleData data = entity.data();
		if (!has(data)) {
			return data.dao() == null ? "why.ult_no_dao" : "why.ult_realm";
		}
		if (!PowerCore.ready(data, "ult", PowerCore.now(entity))) {
			return "why.cooldown";
		}
		if (data.breakingThrough() || data.deviating()) {
			return "why.busy";
		}
		LivingEntity target = entity.getTarget();
		if (target == null || !DiscipleTechniques.mayHit(entity, master, target)) {
			List<LivingEntity> around = PowerCore.hostiles(entity, master, entity.position(), ORDER_RANGE);
			target = around.isEmpty() ? null : around.get(0);
		}
		if (target == null) {
			return "why.no_target";
		}
		return cast(entity, master, target, true);
	}

	/** Casts the ultimate of the disciple's Dao at {@code target}; null when it happened, else a reason key. */
	static @Nullable String cast(DiscipleEntity entity, ServerPlayer master, LivingEntity target, boolean ordered) {
		DiscipleData data = entity.data();
		Dao dao = data.dao();
		if (dao == null || !(entity.level() instanceof ServerLevel level)) {
			return "why.ult_no_dao";
		}
		long now = level.getGameTime();
		double range = DisciplePowers.ultimateRange(entity);
		double u = DiscipleTechniques.power(data);
		String why = switch (dao) {
			case SWORD -> swordFormation(entity, master, level, target, u, range);
			case LIGHTNING -> evilDispelling(entity, master, level, u, range);
			case SPACE -> restriction(entity, master, level, target, u, range);
			case BODY -> fist(entity, master, level, target, range) ? null : "why.too_far";
			case FIRE -> startLotus(entity, master, level, target);
			case LIFE -> desolation(entity, master, level, u, range);
			case ICE -> DiscipleTechniques.castInnate(entity, Technique.GLACIER_PRISON, target, BOOK_POWER, BOOK_RANGE * range) ? null : "why.no_target";
			case SHADOW -> DiscipleTechniques.castInnate(entity, Technique.GALE, target, BOOK_POWER, BOOK_RANGE * range) ? null : "why.no_target";
		};
		if (why != null) {
			return why;
		}
		PowerCore.setCooldown(data, "ult", now + cooldownFor(dao));
		PowerCore.fx(level, entity.position(), PowerIds.FX_ULT_CAST, dao.ordinal(), 0.0F);
		// the 7b beat of a disciple's ultimate: the master sees the stamp and the sky turn (DESIGN 27.6)
		dev.kultivator.epic.EpicHooks.discipleUltimate(entity, master, dao, target.position(), 12.0D * range);
		PowerCore.sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.7F);
		Msg.actionBar(master, PowerCore.key("ult.cast", PowerCore.name(entity), PowerCore.key("ult." + dao.id()))
				.withColor(dao.color()));
		if (ordered || !PowerCore.tag(data).getBooleanOr("ult_seen", false)) {
			PowerCore.edit(data).putBoolean("ult_seen", true);
			Msg.chat(master, PowerCore.key("ult.cast_chat", PowerCore.name(entity), PowerCore.key("ult." + dao.id())).withColor(dao.color()));
		}
		Advancements.grant(master, "signature_ultimate");
		return null;
	}

	// ================================================================== Pedang

	/** Swords of light form a dome net over the target for 6 s; 0.4 U per pulse to monsters inside. */
	private static @Nullable String swordFormation(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity target,
			double u, double range) {
		Vec3 centre = target.position();
		if (entity.distanceToSqr(target) > ORDER_RANGE * ORDER_RANGE) {
			return "why.too_far";
		}
		if (!PowerCore.safeCentre(level, master, centre)) {
			return "why.unsafe_centre";
		}
		Realm realm = entity.data().realm();
		int swords = realm.atLeast(Realm.IMMORTAL_ASCENSION) ? 72 : realm.atLeast(Realm.DAO_UNION) ? 36
				: realm.atLeast(Realm.DEITY_TRANSFORMATION) ? 24 : 12;
		double radius = SWORD_RADIUS * range;
		PowerCore.fx(level, centre, new Vec3(swords, SWORD_TICKS, 0.0D), PowerIds.FX_ULT_SWORD, swords, (float) radius);
		PowerCore.sound(level, centre, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.6F);
		PowerCore.sound(level, centre, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
		double amount = u * SWORD_PULSE_U;
		PowerPulses.start(entity, 10, 10, SWORD_TICKS / 10, (e, lvl, index) -> {
			ServerPlayer owner = e.owner();
			if (owner == null) {
				return false;
			}
			boolean any = false;
			int dives = 0;
			for (LivingEntity victim : PowerCore.hostiles(e, owner, centre, radius)) {
				boolean struck = PowerCore.hurt(e, owner, victim, amount, PowerCore.HIT_ULTIMATE);
				any |= struck;
				if (struck && dives++ < 3) {
					// three swords leave the dome and dive on what they cut (DESIGN 27.6)
					PowerCore.fx(lvl, victim.getBoundingBox().getCenter(), centre, PowerIds.FX_ULT_SWORD_DIVE, victim.getId(), (float) radius);
				}
			}
			if (any && index % 2 == 0) {
				PowerCore.sound(lvl, centre, SoundEvents.TRIDENT_HIT, 0.6F, 1.5F);
			}
			return true;
		}, null);
		return null;
	}

	// ================================================================== Petir

	/** Golden particle bolts on up to 6 monsters (2.5 U, x1.5 on the undead), each chaining once for 1.25 U. */
	private static @Nullable String evilDispelling(DiscipleEntity entity, ServerPlayer master, ServerLevel level, double u, double range) {
		List<LivingEntity> around = PowerCore.hostiles(entity, master, entity.position(), BOLT_RANGE * range);
		List<LivingEntity> victims = new ArrayList<>();
		for (LivingEntity each : around) {
			if (victims.size() >= BOLTS) {
				break;
			}
			if (PowerCore.safeCentre(level, master, each.position())) {
				victims.add(each);
			}
		}
		if (victims.isEmpty()) {
			return around.isEmpty() ? "why.no_target" : "why.unsafe_centre";
		}
		Set<Integer> struck = new HashSet<>();
		for (LivingEntity victim : victims) {
			struck.add(victim.getId());
		}
		PowerCore.sound(level, entity.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7F, 1.3F);
		// One packet per cast (DESIGN 20.3.16): the client draws the bolts and chains on the monsters around.
		PowerCore.fx(level, entity.position(), entity.position(), PowerIds.FX_ULT_BOLT, PowerIds.BOLT_STORM, (float) (BOLT_RANGE * range));
		for (LivingEntity victim : victims) {
			Vec3 ground = victim.position();
			double amount = u * BOLT_U * (victim.is(EntityTypeTags.UNDEAD) ? UNDEAD_FACTOR : 1.0D);
			PowerCore.fx(level, ground, ground.add(0.0D, 20.0D, 0.0D), PowerIds.FX_ULT_BOLT, victim.getId(), 0.0F);
			PowerCore.hurt(entity, master, victim, amount, PowerCore.HIT_ULTIMATE);
			PowerCore.sound(level, ground, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.1F);
			for (LivingEntity chain : PowerCore.hostiles(entity, master, ground, CHAIN_RANGE)) {
				if (struck.add(chain.getId()) && PowerCore.safeCentre(level, master, chain.position())) {
					PowerCore.fx(level, chain.getBoundingBox().getCenter(), victim.getBoundingBox().getCenter(), PowerIds.FX_ULT_BOLT, chain.getId(), 1.0F);
					PowerCore.hurt(entity, master, chain, u * CHAIN_U, PowerCore.HIT_ULTIMATE);
					break;
				}
			}
		}
		return null;
	}

	// ================================================================== Ruang

	/** A cube of restriction glyphs (r 7) on the target: held still, Weakness V, 1 U per second for 4 s. */
	private static @Nullable String restriction(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity target,
			double u, double range) {
		Vec3 centre = target.position();
		if (entity.distanceToSqr(target) > ORDER_RANGE * ORDER_RANGE) {
			return "why.too_far";
		}
		if (!PowerCore.safeCentre(level, master, centre)) {
			return "why.unsafe_centre";
		}
		double radius = PRISON_RADIUS * range;
		PowerCore.fx(level, centre, PowerIds.FX_ULT_PRISON, PRISON_TICKS, (float) radius);
		PowerCore.sound(level, centre, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 0.5F);
		PowerCore.sound(level, centre, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0F, 0.6F);
		List<LivingEntity> held = new ArrayList<>();
		double amount = u * PRISON_PULSE_U;
		PowerPulses.start(entity, 0, 10, PRISON_TICKS / 10, (e, lvl, index) -> {
			ServerPlayer owner = e.owner();
			if (owner == null) {
				return false;
			}
			for (LivingEntity victim : PowerCore.hostiles(e, owner, centre, radius)) {
				AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null && !speed.hasModifier(PRISON_ID)) {
					speed.addOrUpdateTransientModifier(new AttributeModifier(PRISON_ID, -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
					held.add(victim);
				}
				victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 4), e);
				PowerCore.hurt(e, owner, victim, amount, PowerCore.HIT_ULTIMATE);
			}
			return true;
		}, e -> {
			for (LivingEntity victim : held) {
				AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) {
					speed.removeModifier(PRISON_ID);
				}
			}
		});
		return null;
	}

	// ================================================================== Tubuh

	/** One punch of 6 x its attack damage, then a 4-block shockwave of 2 U around the target. False when out of reach. */
	static boolean fist(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity target, double range) {
		if (entity.distanceToSqr(target) > FIST_REACH * FIST_REACH || !DiscipleTechniques.mayHit(entity, master, target)
				|| !PowerCore.safeCentre(level, master, target.position())) {
			return false;
		}
		double punch = entity.getAttributeValue(Attributes.ATTACK_DAMAGE) * FIST_ATTACK;
		// mcsrc world/entity/LivingEntity.java:2022: a vanilla stab swing every tracker sees
		entity.swing(net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.item.component.SwingAnimation(net.minecraft.world.item.SwingAnimationType.STAB, 8), false);
		PowerCore.hurt(entity, master, target, punch, PowerCore.HIT_ULTIMATE);
		double wave = FIST_WAVE * range;
		double amount = DiscipleTechniques.power(entity.data()) * FIST_WAVE_U;
		for (LivingEntity other : PowerCore.hostiles(entity, master, target.position(), wave)) {
			if (other != target) {
				PowerCore.hurt(entity, master, other, amount, PowerCore.HIT_ULTIMATE);
			}
			Vec3 away = other.position().subtract(target.position()).horizontal();
			Vec3 push = away.lengthSqr() < 1.0E-4D ? Vec3.ZERO : away.normalize().scale(0.6D);
			PowerCore.push(other, new Vec3(push.x, 0.35D, push.z));
		}
		PowerCore.fx(level, target.position(), PowerIds.FX_ULT_FIST, 0, (float) wave);
		PowerCore.sound(level, target.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.3F, 0.6F);
		PowerCore.sound(level, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
		return true;
	}

	// ================================================================== Api

	/** A running lotus charge. */
	static final class Lotus {
		final LivingEntity target;
		boolean fused;

		Lotus(LivingEntity target) {
			this.target = target;
		}
	}

	/** Starts the 4 s charge; the lotus flies on release. */
	private static @Nullable String startLotus(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity target) {
		if (entity.distanceToSqr(target) > LOTUS_RANGE * LOTUS_RANGE) {
			return "why.too_far";
		}
		DisciplePowers.Live live = DisciplePowers.live(entity);
		if (live.lotus != null) {
			return "why.busy";
		}
		live.lotus = new Lotus(target);
		DisciplePowers.syncPower(entity);
		PowerCore.fx(level, entity.position(), PowerIds.FX_LOTUS_CHARGE, entity.getId(), LOTUS_CHARGE);
		PowerCore.sound(level, entity.position(), SoundEvents.FIRECHARGE_USE, 1.0F, 0.5F);
		PowerCore.sound(level, entity.position(), SoundEvents.BLAZE_AMBIENT, 0.8F, 0.6F);
		if (Cultivators.get(master).dao() == Dao.FIRE && master.level() == level && master.distanceToSqr(entity) <= FUSION_RANGE * FUSION_RANGE) {
			Msg.actionBar(master, PowerCore.key("lotus.fusion_hint", PowerCore.name(entity)).withStyle(ChatFormatting.GOLD));
		}
		PowerPulses.later(entity, LOTUS_CHARGE, Ultimates::releaseLotus);
		return null;
	}

	private static void releaseLotus(DiscipleEntity entity) {
		DisciplePowers.Live live = DisciplePowers.live(entity);
		Lotus lotus = live.lotus;
		live.lotus = null;
		DisciplePowers.syncPower(entity);
		ServerPlayer master = entity.owner();
		if (lotus == null || master == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity target = lotus.target;
		if (!target.isAlive() || entity.distanceToSqr(target) > LOTUS_RANGE * LOTUS_RANGE || !DiscipleTechniques.mayHit(entity, master, target)) {
			List<LivingEntity> around = PowerCore.hostiles(entity, master, entity.position(), LOTUS_RANGE);
			target = around.isEmpty() ? null : around.get(0);
		}
		if (target == null || !PowerCore.safeCentre(level, master, target.position())) {
			PowerCore.tell(entity, PowerCore.key("lotus.fizzle", PowerCore.name(entity)).withColor(0xC08A6A));
			PowerCore.fx(level, entity.position().add(0.0D, 1.2D, 0.0D), PowerIds.FX_LOTUS_BLAST, 0, 0.5F);
			return;
		}
		int flames = DisciplePowers.flames(entity.data());
		double u = DiscipleTechniques.power(entity.data());
		double range = DisciplePowers.ultimateRange(entity);
		double radius = (lotus.fused ? FUSION_RADIUS : flames >= 2 ? LOTUS_RADIUS_FLAMES : LOTUS_RADIUS) * range;
		double amount = u * (lotus.fused ? FUSION_U : LOTUS_U) * Math.max(1, flames);
		Vec3 aimed = target.position();
		Vec3 hands = entity.getEyePosition().add(entity.getViewVector(1.0F).scale(0.6D)).add(0.0D, -0.3D, 0.0D);
		// the lotus leaves the hands and flies (DESIGN 27.6): the blast lands when it arrives
		int flight = (int) Math.max(6L, Math.min(8L, Math.round(hands.distanceTo(aimed) / 3.0D)));
		PowerCore.fx(level, hands, aimed, PowerIds.FX_LOTUS_RELEASE, flight, (float) radius);
		PowerCore.sound(level, hands, SoundEvents.FIRECHARGE_USE, 1.2F, 0.7F);
		LivingEntity aimedAt = target;
		boolean fused = lotus.fused;
		PowerPulses.later(entity, flight, e -> blastLotus(e, aimedAt, aimed, radius, amount, flames, fused));
	}

	private static void blastLotus(DiscipleEntity entity, LivingEntity aimedAt, Vec3 aimed, double radius, double amount, int flames, boolean fused) {
		ServerPlayer master = entity.owner();
		if (master == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 impact = aimedAt.isAlive() && aimedAt.level() == level && aimedAt.position().distanceTo(aimed) <= 4.0D ? aimedAt.position() : aimed;
		if (!PowerCore.safeCentre(level, master, impact)) {
			PowerCore.fx(level, impact.add(0.0D, 1.2D, 0.0D), PowerIds.FX_LOTUS_BLAST, 0, 0.5F);
			return;
		}
		Lotus lotus = new Lotus(aimedAt);
		lotus.fused = fused;
		for (LivingEntity victim : PowerCore.hostiles(entity, master, impact, radius)) {
			if (PowerCore.hurt(entity, master, victim, amount, PowerCore.HIT_ULTIMATE) && victim.isAlive()) {
				DaoTechniques.ignite(victim, LOTUS_FIRE);
			}
		}
		PowerCore.fx(level, impact, entity.getEyePosition(), PowerIds.FX_LOTUS_BLAST, Math.max(1, flames) + (lotus.fused ? 8 : 0), (float) radius);
		PowerCore.sound(level, impact, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.8F);
		PowerCore.sound(level, impact, SoundEvents.BLAZE_SHOOT, 1.2F, 0.5F);
		if (lotus.fused) {
			Advancements.grant(master, "buddha_lotus_fusion");
			Msg.chat(master, PowerCore.key("lotus.fused_blast", PowerCore.name(entity)).withColor(0xFF7A2A));
		}
	}

	/** The master's Fusi during the charge: null when the flames fused, else a reason key. */
	static @Nullable String fuse(DiscipleEntity entity, ServerPlayer master) {
		Lotus lotus = DisciplePowers.live(entity).lotus;
		if (lotus == null) {
			return "why.no_lotus";
		}
		if (lotus.fused) {
			return "why.fused";
		}
		CultivationData masterData = Cultivators.get(master);
		if (masterData.dao() != Dao.FIRE) {
			return "why.master_not_fire";
		}
		if (master.level() != entity.level() || master.distanceToSqr(entity) > FUSION_RANGE * FUSION_RANGE) {
			return "why.master_far";
		}
		double cost = Qi.max(masterData) * FUSION_QI;
		if (!Qi.trySpend(master, cost)) {
			return "why.master_qi";
		}
		lotus.fused = true;
		if (entity.level() instanceof ServerLevel level) {
			PowerCore.fx(level, master.position().add(0.0D, 1.2D, 0.0D), entity.position().add(0.0D, 1.2D, 0.0D), PowerIds.FX_FUSION, 0, 0.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.BLAZE_SHOOT, 1.0F, 1.4F);
			PowerCore.sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.5F);
		}
		Msg.chat(master, PowerCore.key("lotus.fused", PowerCore.name(entity), Msg.number(Math.ceil(cost))).withColor(0xFFB43A));
		return null;
	}

	// ================================================================== Kehidupan

	/** Drains monsters within 10 (1.5 U each), heals itself fully and allies by 30%, then +30% damage for 30 s. */
	private static @Nullable String desolation(DiscipleEntity entity, ServerPlayer master, ServerLevel level, double u, double range) {
		Vec3 centre = entity.position();
		double radius = LIFE_RADIUS * range;
		List<LivingEntity> victims = PowerCore.hostiles(entity, master, centre, radius);
		if (victims.isEmpty()) {
			return "why.no_target";
		}
		if (!PowerCore.safeCentre(level, master, centre)) {
			return "why.unsafe_centre";
		}
		for (LivingEntity victim : victims) {
			PowerCore.hurt(entity, master, victim, u * LIFE_U, PowerCore.HIT_ULTIMATE);
		}
		entity.heal(entity.getMaxHealth());
		if (master.level() == level && master.distanceToSqr(entity) <= radius * radius) {
			master.heal(master.getMaxHealth() * LIFE_ALLY_HEAL);
		}
		for (DiscipleEntity ally : level.getEntitiesOfClass(DiscipleEntity.class, entity.getBoundingBox().inflate(radius),
				other -> other != entity && other.isAlive() && master.getUUID().equals(other.ownerId()) && PowerCore.powered(other))) {
			ally.heal(ally.getMaxHealth() * LIFE_ALLY_HEAL);
		}
		DisciplePowers.live(entity).lifeBuffUntil = level.getGameTime() + LIFE_BUFF_TICKS;
		PowerCore.fx(level, centre, PowerIds.FX_ULT_LIFE, victims.size(), (float) radius);
		PowerCore.sound(level, centre, SoundEvents.WARDEN_SONIC_CHARGE, 0.8F, 1.2F);
		PowerCore.sound(level, centre, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.4F);
		return null;
	}

	private Ultimates() {
	}
}
