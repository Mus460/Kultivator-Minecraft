package dev.kultivator.disciple.path;

import dev.kultivator.Kultivator;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Bintang Abadi, the endless stars beyond True Immortal (DESIGN 20.3.19). Sumber: <i>Renegade Immortal</i> (Ancient Gods
 * grow by stars), <i>Stellar Transformations</i> (the stage ladder, Qin Yu absorbing star power under the night sky) and
 * <i>Battle Through the Heavens</i> (Dou grades in stars); the numbers are game design.
 * <p>
 * At True Immortal Peak every further XP becomes Stored Qi, shown as Qi Bintang. Star n costs
 * {@code min(5.0E9, 500,000 x 1.07^(n-1))}; with "Otomatis" on a star condenses as soon as the Stored Qi suffices and no
 * calamity gate ({@link Calamity}) blocks it. Each star adds max health, attack, technique damage and meditation XP
 * (scaled by the trial factor T); every nine stars complete a stage that multiplies technique and melee damage.
 */
public final class Stars {

	public static final double BASE_COST = 500_000.0D;
	public static final double COST_GROWTH = 1.07D;
	public static final double MAX_COST = 5.0E9D;
	public static final int STARS_PER_STAGE = 9;
	public static final double HEALTH_PER_STAR = 4.0D;
	public static final double ATTACK_PER_STAR = 0.6D;
	public static final double TECHNIQUE_PER_STAR = 0.04D;
	public static final double STAGE_FACTOR = 1.15D;
	public static final double MAX_QI_PER_STAR = 0.02D;
	public static final double MEDITATION_PER_STAR = 0.04D;
	/** Meditation under the open night sky doubles the star bonus (Qin Yu absorbs star power). */
	public static final double NIGHT_SKY_FACTOR = 2.0D;
	/** Finishing a Trial (calamity 2, 4 and 7) multiplies every star bonus by this (T up to 1.331). */
	public static final double TRIAL_FACTOR = 1.1D;
	public static final double ANCIENT_BLOOD_HEALTH = 1.5D;
	/** The max health attribute stops here; more converts to damage reduction. */
	public static final double HEALTH_CAP = 1024.0D;
	/** +0.25% damage reduction per 4 health over the cap, at most 80%. */
	public static final double REDUCTION_PER_4_HEALTH = 0.0025D;
	public static final double MAX_REDUCTION = 0.8D;
	/** The whole server hears of a disciple reaching this many stars. */
	public static final int ANNOUNCE_STAR = 36;
	/** Renown for every ninth star (DESIGN 20.3.1). */
	public static final int RENOWN_PER_NINE = 50;

	static final Identifier HEALTH_ID = Kultivator.id("path_star_health");
	static final Identifier ATTACK_ID = Kultivator.id("path_star_attack");

	static void register() {
		DiscipleHooks.addStats(Stars::stats);
		DiscipleHooks.addDamageFactor(Stars::damageFactor);
		DiscipleHooks.addXpFactor(Stars::xpFactor);
		DiscipleHooks.addHurtFactor(Stars::hurtFactor);
		DiscipleHooks.addMaxQiFactor(data -> maxQiFactor(DisciplePaths.stars(data)));
	}

	// ================================================================== rules (pure)

	/** Qi Bintang that star {@code n} costs. */
	public static double cost(int n) {
		if (n <= 1) {
			return BASE_COST;
		}
		return Math.min(MAX_COST, BASE_COST * Math.pow(COST_GROWTH, n - 1));
	}

	/** T = 1.1 per finished Trial (0-3). */
	public static double trialFactor(int trials) {
		return Math.pow(TRIAL_FACTOR, Math.max(0, Math.min(3, trials)));
	}

	public static double trialFactor(DiscipleData data) {
		return trialFactor(DisciplePaths.tag(data).getIntOr("trials", 0));
	}

	/** Completed nine-star stages. */
	public static int stages(int stars) {
		return Math.max(0, stars) / STARS_PER_STAGE;
	}

	/** Technique damage factor {@code (1 + 0.04 n T) x 1.15^stages}. */
	public static double techniqueFactor(int stars, double trialFactor) {
		if (stars <= 0) {
			return 1.0D;
		}
		return (1.0D + TECHNIQUE_PER_STAR * stars * trialFactor) * Math.pow(STAGE_FACTOR, stages(stars));
	}

	/** Melee damage factor {@code 1.15^stages}. */
	public static double meleeFactor(int stars) {
		return Math.pow(STAGE_FACTOR, stages(stars));
	}

	/** Max qi factor {@code 1 + 0.02 n} (shown on the Jalan tab). */
	public static double maxQiFactor(int stars) {
		return 1.0D + MAX_QI_PER_STAR * Math.max(0, stars);
	}

	/** True with Darah Dewa Kuno (Ancient God Blood). */
	static boolean ancientBlood(DiscipleData data) {
		return (Talents.talents(data) & DisciplePaths.TALENT_ANCIENT_GOD_BLOOD) != 0;
	}

	/**
	 * The star of the first calamity: 6 (average quality Normal or lower), 7 (Perfect) or 8 (Heavenly, a Sempurna
	 * Foundation or Darah Dewa Kuno; Tu Si met his first trial at eight stars).
	 */
	public static int firstGate(DiscipleData data) {
		double quality = data.qualityFactor();
		if (quality >= 1.29D || FoundationTypes.type(data) == FoundationTypes.SEMPURNA || ancientBlood(data)) {
			return 8;
		}
		return quality >= 1.149D ? 7 : 6;
	}

	/** The star calamity {@code k} (1-7) gates: {@code f + 9(k - 1)}. */
	public static int gateStar(DiscipleData data, int k) {
		return firstGate(data) + STARS_PER_STAGE * (k - 1);
	}

	/** The calamity (1-7) that must be passed before the next star can condense, or 0. */
	public static int pendingCalamity(DiscipleData data) {
		int passed = DisciplePaths.calamitiesPassed(data);
		if (passed >= Calamity.COUNT) {
			return 0;
		}
		int k = passed + 1;
		return gateStar(data, k) <= DisciplePaths.stars(data) + 1 ? k : 0;
	}

	/** Why no star can condense now (a translation key), or null. */
	static @Nullable String condenseBlocker(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (!data.rank().isMax()) {
			return DisciplePaths.PREFIX + "why.not_peak";
		}
		if (data.breakingThrough() || Calamity.running(entity)) {
			return DisciplePaths.PREFIX + "why.busy";
		}
		if (pendingCalamity(data) > 0) {
			return DisciplePaths.PREFIX + "why.calamity";
		}
		if (data.storedQi() + 1.0E-6D < cost(DisciplePaths.stars(data) + 1)) {
			return DisciplePaths.PREFIX + "why.star_qi";
		}
		return null;
	}

	// ================================================================== condensing

	/** One star condenses from the Stored Qi. True when it did. */
	static boolean condense(DiscipleEntity entity) {
		if (condenseBlocker(entity) != null) {
			return false;
		}
		DiscipleData data = entity.data();
		int before = DisciplePaths.stars(data);
		data.setStoredQi(data.storedQi() - cost(before + 1));
		DisciplePaths.section(data).putInt("stars", before + 1);
		afterStarChange(entity, before, before + 1, true);
		return true;
	}

	/**
	 * Everything a new star brings: the condensing effect, renown and a hex for the heir every ninth star, the title,
	 * the Divine Clones, the server announcement at ★36, the soul lamps after Penginjak Langit, the synced count.
	 * {@code announce} is false for commands (no renown, no hexes, no chat).
	 */
	static void afterStarChange(DiscipleEntity entity, int before, int after, boolean announce) {
		DiscipleData data = entity.data();
		entity.refreshSynced();
		DisciplePaths.syncPower(entity);
		if (!announce || after <= before || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer master = entity.owner();
		UUID owner = entity.ownerId();
		Vec3 chest = entity.position().add(0.0D, 1.1D, 0.0D);
		Fx.send(level, chest, FxType.STAR, PathFxIds.CONDENSE, after, 1.0F);
		DisciplePaths.sound(level, chest, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.4F);
		DisciplePaths.sound(level, chest, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.8F);
		Component name = DisciplePaths.name(entity);
		for (int star = before + 1; star <= after; star++) {
			if (star % STARS_PER_STAGE == 0) {
				if (owner != null) {
					Halls.addRenown(owner, RENOWN_PER_NINE, DisciplePaths.PREFIX + "renown.star9");
				}
				Heir.onNinthStar(entity);
				DisciplePaths.sound(level, chest, SoundEvents.BELL_RESONATE, 1.0F, 1.2F);
			}
			if (master != null && StarTitles.tier(star) != StarTitles.tier(star - 1)) {
				Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "star.new_title", name, StarTitles.tierName(star))
						.withColor(StarTitles.color(star)));
			}
			if (master != null && (star == DivineClones.FIRST_CLONE_STAR || star == DivineClones.SECOND_CLONE_STAR)) {
				Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "clone.unlocked", name, DivineClones.allowed(star))
						.withColor(DisciplePaths.JADE));
			}
			if (star == ANNOUNCE_STAR) {
				Msg.broadcast(level.getServer(), Component.translatable(DisciplePaths.PREFIX + "star.announce", name, star)
						.withStyle(ChatFormatting.GOLD));
			}
			SoulLamps.onStar(entity, star);
		}
		if (master != null) {
			Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + "star.condensed", name, StarTitles.title(after)));
			Advancements.grant(master, "first_disciple_star");
		}
	}

	/** Once a second: condenses one star while "Otomatis" is on, and tells the master once when a calamity gate is reached. */
	static void tick20(DiscipleEntity entity, @Nullable ServerPlayer master) {
		DiscipleData data = entity.data();
		if (!data.rank().isMax()) {
			return;
		}
		CompoundTag tag = DisciplePaths.tag(data);
		if (tag.getBooleanOr("auto", true) && condense(entity)) {
			return;
		}
		int k = pendingCalamity(data);
		if (k > 0 && master != null && tag.getIntOr("gate_told", 0) != k
				&& data.storedQi() + 1.0E-6D >= cost(DisciplePaths.stars(data) + 1)) {
			DisciplePaths.section(data).putInt("gate_told", k);
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "calamity.gate", DisciplePaths.name(entity), k,
					DisciplePaths.stars(data) + 1).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	/** The "Padatkan Bintang" talisman. */
	static void order(DiscipleEntity entity, ServerPlayer master) {
		String why = condenseBlocker(entity);
		if (why != null) {
			Msg.chat(master, Component.translatable(why, DisciplePaths.name(entity)).withColor(DisciplePaths.REFUSE));
			return;
		}
		condense(entity);
	}

	static void setAuto(DiscipleEntity entity, ServerPlayer master, boolean on) {
		DisciplePaths.section(entity.data()).putBoolean("auto", on);
		Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + (on ? "star.auto_on" : "star.auto_off"), DisciplePaths.name(entity)));
	}

	// ================================================================== bonuses (hooks)

	private static void stats(DiscipleEntity entity, DiscipleHooks.StatSink sink) {
		DiscipleData data = entity.data();
		int n = DisciplePaths.stars(data);
		if (n <= 0) {
			return;
		}
		double t = trialFactor(data);
		double health = HEALTH_PER_STAR * n * t * (ancientBlood(data) ? ANCIENT_BLOOD_HEALTH : 1.0D);
		sink.add(Attributes.MAX_HEALTH, HEALTH_ID, health, AttributeModifier.Operation.ADD_VALUE);
		sink.add(Attributes.ATTACK_DAMAGE, ATTACK_ID, ATTACK_PER_STAR * n * t, AttributeModifier.Operation.ADD_VALUE);
	}

	private static double damageFactor(DiscipleEntity entity, LivingEntity target, boolean technique) {
		DiscipleData data = entity.data();
		int n = DisciplePaths.stars(data);
		if (n <= 0) {
			return 1.0D;
		}
		return technique ? techniqueFactor(n, trialFactor(data)) : meleeFactor(n);
	}

	private static double xpFactor(@Nullable DiscipleEntity entity, DiscipleData data, XpSource source) {
		if (source != XpSource.MEDITATION) {
			return 1.0D;
		}
		int n = DisciplePaths.stars(data);
		if (n <= 0) {
			return 1.0D;
		}
		double factor = 1.0D + MEDITATION_PER_STAR * n;
		if (entity != null && nightSky(entity)) {
			factor *= NIGHT_SKY_FACTOR;
		}
		return factor;
	}

	/** Open night sky above the disciple (read-only light check). */
	static boolean nightSky(DiscipleEntity entity) {
		Level level = entity.level();
		return level.isDarkOutside() && level.canSeeSky(entity.blockPosition().above());
	}

	/** Health beyond the 1,024 cap turns into damage reduction (DESIGN 20.3.19). */
	private static double hurtFactor(DiscipleEntity entity, DamageSource source) {
		if (source.is(DamageTypes.GENERIC_KILL) || DisciplePaths.stars(entity.data()) <= 0) {
			return 1.0D;
		}
		double reduction = overflowReduction(entity);
		return reduction <= 0.0D ? 1.0D : 1.0D - reduction;
	}

	/** 0..0.8: the damage reduction from max health over the attribute cap. */
	static double overflowReduction(DiscipleEntity entity) {
		double raw = rawMaxHealth(entity);
		if (raw <= HEALTH_CAP) {
			return 0.0D;
		}
		return Math.min(MAX_REDUCTION, REDUCTION_PER_4_HEALTH * (raw - HEALTH_CAP) / 4.0D);
	}

	/** Max health before the attribute's 1,024 clamp (base and every modifier). */
	static double rawMaxHealth(LivingEntity entity) {
		AttributeInstance instance = entity.getAttribute(Attributes.MAX_HEALTH);
		if (instance == null) {
			return entity.getMaxHealth();
		}
		double value = instance.getBaseValue();
		double base = 0.0D;
		double total = 1.0D;
		for (AttributeModifier modifier : instance.getModifiers()) {
			switch (modifier.operation()) {
				case ADD_VALUE -> value += modifier.amount();
				case ADD_MULTIPLIED_BASE -> base += modifier.amount();
				case ADD_MULTIPLIED_TOTAL -> total *= 1.0D + modifier.amount();
			}
		}
		return Math.max(0.0D, value * (1.0D + base) * total);
	}

	private Stars() {
	}
}
