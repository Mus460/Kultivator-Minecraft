package dev.kultivator.disciple.power;

import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Kitab Abadi Tak Mati, the Undying Live-Forever Codex (DESIGN 20.3.17; A Will Eternal: Undying Skin with Iron, Bronze,
 * Silver and Gold Skin, Undying Tendons, Undying Bones and their secret art the Undying Emperor's Fist, Undying Blood; the
 * Heavenly King level is skipped). Open to a disciple given the Manual (Paviliun Yue Lu, {@link DisciplePowers#grantCodex}),
 * and free for the Body Dao and a Blocked Dantian. Poin Tempa: 1 per 1% of max health taken in combat (200 a day, x2 with
 * Takut Mati) plus 1 per minute of Pikul Air.
 * <ul>
 * <li>Kulit Perak 150: armor +6. Kulit Emas 500: armor +12, toughness +4, immune to entity fire, gold glint.</li>
 * <li>Urat Abadi 1,200: speed +15%, a dash to its target (8 blocks, every 6 s).</li>
 * <li>Tulang Abadi 2,500: the Undying Emperor's Fist for any Dao (every 30 s).</li>
 * <li>Darah Abadi, 80 points per 1%: +0.05 health/s per 10%; at 100% once a day a lethal hit leaves it at 1 health
 * with Resistance V for 5 s and no tablet, and "Kitab Hidup Abadi" adds 1% max health per star.</li>
 * </ul>
 */
final class UndyingCodex {

	public static final int[] STAGE_POINTS = {150, 500, 1200, 2500};
	public static final int BLOOD_POINTS = 80;
	public static final int DAILY_CAP = 200;
	public static final double SILVER_ARMOR = 6.0D;
	public static final double GOLD_ARMOR = 12.0D;
	public static final double GOLD_TOUGHNESS = 4.0D;
	public static final double TENDON_SPEED = 0.15D;
	public static final double DASH_RANGE = 8.0D;
	public static final int DASH_COOLDOWN = 120;
	public static final int FIST_COOLDOWN = 600;
	public static final double BLOOD_REGEN_PER_10 = 0.05D;
	public static final int UNBROKEN_TICKS = 100;
	public static final double LIFE_PER_STAR = 0.01D;

	private static final Identifier ARMOR_ID = DisciplePowers.id("codex_armor");
	private static final Identifier TOUGHNESS_ID = DisciplePowers.id("codex_toughness");
	private static final Identifier SPEED_ID = DisciplePowers.id("codex_speed");
	private static final Identifier LIFE_ID = DisciplePowers.id("codex_life");
	private static final Identifier TRAINED_ID = DisciplePowers.id("trained_body");

	static void register() {
		DiscipleHooks.addStats((entity, sink) -> {
			if (!PowerCore.powered(entity)) {
				return;
			}
			DiscipleData data = entity.data();
			double trained = PowerCore.tag(data).getDoubleOr("trained_hp", 0.0D);
			if (trained > 0.0D) {
				sink.add(Attributes.MAX_HEALTH, TRAINED_ID, trained, AttributeModifier.Operation.ADD_VALUE);
			}
			if (!access(data)) {
				return;
			}
			int stage = stage(data);
			if (stage >= 1) {
				sink.add(Attributes.ARMOR, ARMOR_ID, stage >= 2 ? GOLD_ARMOR : SILVER_ARMOR, AttributeModifier.Operation.ADD_VALUE);
			}
			if (stage >= 2) {
				sink.add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, GOLD_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
			}
			if (stage >= 3) {
				sink.add(Attributes.MOVEMENT_SPEED, SPEED_ID, TENDON_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			if (blood(data) >= 100) {
				int stars = DisciplePaths.stars(data);
				if (stars > 0) {
					sink.add(Attributes.MAX_HEALTH, LIFE_ID, LIFE_PER_STAR * stars, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				}
			}
		});
		DiscipleHooks.addOnHurt(UndyingCodex::onHurt);
		DiscipleHooks.addOnDeath(UndyingCodex::onDeath);
	}

	// ================================================================== reads

	/** Body Dao, a Blocked Dantian, or the Manual (granted here, or bought on module A's side as {@code extra.sect.codex}). */
	static boolean access(DiscipleData data) {
		return data.dao() == Dao.BODY || SpecialCandidates.codexFree(data)
				|| PowerCore.tag(data).getCompoundOrEmpty("codex").getBooleanOr("granted", false)
				|| data.extra().getCompoundOrEmpty(SectRanks.EXTRA).getBooleanOr("codex", false);
	}

	static int points(DiscipleData data) {
		return Math.max(0, PowerCore.tag(data).getCompoundOrEmpty("codex").getIntOr("pts", 0));
	}

	/** 0 none, 1 Kulit Perak, 2 Kulit Emas, 3 Urat Abadi, 4 Tulang Abadi. */
	static int stage(DiscipleData data) {
		int points = points(data);
		int stage = 0;
		for (int threshold : STAGE_POINTS) {
			if (points >= threshold) {
				stage++;
			}
		}
		return stage;
	}

	/** Undying Blood 0-100%. */
	static int blood(DiscipleData data) {
		return Math.max(0, Math.min(100, (points(data) - STAGE_POINTS[3]) / BLOOD_POINTS));
	}

	/** Points still missing for the next stage or percent (0 at 100% blood). */
	static int nextAt(DiscipleData data) {
		int stage = stage(data);
		if (stage < STAGE_POINTS.length) {
			return STAGE_POINTS[stage];
		}
		int blood = blood(data);
		return blood >= 100 ? 0 : STAGE_POINTS[3] + (blood + 1) * BLOOD_POINTS;
	}

	// ================================================================== points

	/** Adds Poin Tempa; {@code duty} points also train the body ("Tubuh Terlatih", +1 max health per 60 minutes, +10 at most). */
	static void addPoints(DiscipleData data, int points, boolean duty) {
		if (points == 0) {
			return;
		}
		CompoundTag codex = PowerCore.editSub(data, "codex");
		codex.putInt("pts", Math.max(0, Math.min(1_000_000, codex.getIntOr("pts", 0) + points)));
		if (duty && points > 0) {
			CompoundTag tag = PowerCore.edit(data);
			int minutes = tag.getIntOr("trained_min", 0) + points;
			tag.putInt("trained_min", minutes);
			tag.putDouble("trained_hp", Math.min(10.0D, Math.floor(minutes / 60.0D)));
		}
	}

	private static void onHurt(DiscipleEntity entity, DamageSource source, float damage) {
		DiscipleData data = entity.data();
		if (!PowerCore.isDisciple(entity) || damage <= 0.0F || !access(data) || !(source.getEntity() instanceof LivingEntity)
				|| !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		// 1 point per 1% of max health taken in a fight (twice for one who fears death), 200 a day.
		DisciplePowers.Live live = DisciplePowers.live(entity);
		double gained = damage / Math.max(1.0F, entity.getMaxHealth()) * 100.0D
				* (PowerCore.talent(data, PowerIds.TALENT_FEAR_OF_DEATH) ? 2.0D : 1.0D);
		live.codexFraction += gained;
		int whole = (int) Math.floor(live.codexFraction);
		if (whole <= 0) {
			return;
		}
		live.codexFraction -= whole;
		CompoundTag codex = PowerCore.editSub(data, "codex");
		long day = PowerCore.day(level);
		if (codex.getLongOr("day", -1L) != day) {
			codex.putLong("day", day);
			codex.putInt("day_pts", 0);
		}
		int room = DAILY_CAP - codex.getIntOr("day_pts", 0);
		int add = Math.min(room, whole);
		if (add > 0) {
			codex.putInt("day_pts", codex.getIntOr("day_pts", 0) + add);
			addPoints(data, add, false);
		}
	}

	// ================================================================== once a second

	static void tick(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (!access(data) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		CompoundTag codex = PowerCore.editSub(data, "codex");
		int stage = stage(data);
		int blood = blood(data);
		int savedStage = codex.getIntOr("stage", 0);
		int savedBlood = codex.getIntOr("blood", 0);
		if (stage != savedStage || blood != savedBlood) {
			codex.putInt("stage", stage);
			codex.putInt("blood", blood);
			entity.refreshSynced();
			if (stage > savedStage) {
				PowerCore.fx(level, entity.position(), PowerIds.FX_CODEX_STAGE, stage, 0.0F);
				PowerCore.sound(level, entity.position(), SoundEvents.ANVIL_LAND, 0.5F, 0.6F + 0.1F * stage);
				PowerCore.sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 0.8F + 0.1F * stage);
				PowerCore.tell(entity, PowerCore.key("codex.stage", PowerCore.name(entity), PowerCore.key("codex.stage." + stage))
						.withColor(0xE0B050));
			}
			if (blood >= 100 && savedBlood < 100) {
				PowerCore.fx(level, entity.position(), PowerIds.FX_CODEX_STAGE, 5, 0.0F);
				PowerCore.tell(entity, PowerCore.key("codex.blood_full", PowerCore.name(entity)).withColor(0xFF4A4A));
				ServerPlayer master = entity.owner();
				if (master != null) {
					Advancements.grant(master, "undying_blood");
				}
			}
		}
		if (blood > 0 && entity.getHealth() < entity.getMaxHealth()) {
			entity.heal((float) (BLOOD_REGEN_PER_10 * blood / 10.0D));
		}
		if (stage >= 2 && entity.isOnFire()) {
			// Golden Skin: entity fire finds no hold.
			entity.clearFire();
		}
	}

	/** Kulit Emas ignores entity fire. */
	static boolean immuneTo(DiscipleEntity entity, DamageSource source) {
		return source.is(DamageTypes.ON_FIRE) && access(entity.data()) && stage(entity.data()) >= 2;
	}

	// ================================================================== combat

	/** Urat Abadi's dash and Tulang Abadi's fist, in a fight. */
	static boolean combatCast(DiscipleEntity entity, LivingEntity target) {
		DiscipleData data = entity.data();
		ServerPlayer master = entity.owner();
		if (master == null || !access(data) || !(entity.level() instanceof ServerLevel level) || !PowerCore.isDisciple(entity)) {
			return false;
		}
		int stage = stage(data);
		long now = level.getGameTime();
		double distanceSq = entity.distanceToSqr(target);
		if (stage >= 4 && distanceSq <= Ultimates.FIST_REACH * Ultimates.FIST_REACH && PowerCore.ready(data, "fist", now)
				&& (data.dao() != Dao.BODY || !PowerCore.ready(data, "ult", now))) {
			if (Ultimates.fist(entity, master, level, target, DisciplePowers.ultimateRange(entity))) {
				PowerCore.setCooldown(data, "fist", now + FIST_COOLDOWN);
				return true;
			}
		}
		if (stage >= 3 && distanceSq >= 16.0D && distanceSq <= (DASH_RANGE + 2.0D) * (DASH_RANGE + 2.0D) && PowerCore.ready(data, "dash", now)) {
			Vec3 from = entity.position();
			Vec3 spot = KunpengWings.dashSpot(entity, level, target.position(), DASH_RANGE, 1.5D);
			if (spot != null) {
				KunpengWings.moveTo(entity, spot, target.getBoundingBox().getCenter());
				PowerCore.setCooldown(data, "dash", now + DASH_COOLDOWN);
				PowerCore.fx(level, from, spot, PowerIds.FX_DASH, 0, 0.0F);
				PowerCore.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.6F);
				return true;
			}
		}
		return false;
	}

	/** Darah Abadi at 100%: once a day a lethal hit leaves the disciple at 1 health (no tablet). */
	private static boolean onDeath(DiscipleEntity entity, DamageSource source) {
		DiscipleData data = entity.data();
		if (!access(data) || blood(data) < 100 || !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		CompoundTag codex = PowerCore.editSub(data, "codex");
		long day = PowerCore.day(level);
		if (codex.getLongOr("unbroken_day", -1L) == day) {
			return false;
		}
		codex.putLong("unbroken_day", day);
		entity.setHealth(1.0F);
		entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, UNBROKEN_TICKS, 4), entity);
		PowerCore.fx(level, entity.position(), PowerIds.FX_UNDYING, 0, 0.0F);
		dev.kultivator.epic.EpicHooks.discipleSave(entity, entity.owner(), dev.kultivator.epic.EpicIds.SAVE_UNDYING_BLOOD);
		PowerCore.sound(level, entity.position(), SoundEvents.TOTEM_USE, 0.7F, 0.7F);
		PowerCore.sound(level, entity.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2F, 0.8F);
		PowerCore.tell(entity, PowerCore.key("codex.unbroken", PowerCore.name(entity)).withStyle(ChatFormatting.RED));
		return true;
	}

	private UndyingCodex() {
	}
}
