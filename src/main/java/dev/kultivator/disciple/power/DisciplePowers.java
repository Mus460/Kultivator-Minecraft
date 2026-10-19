package dev.kultivator.disciple.power;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ToDoubleFunction;

/**
 * A disciple's own powers (DESIGN 20.3.4, 20.3.5, 20.3.8, 20.3.16, 20.3.17; module C, P6-kekuatan): talents, soul colour,
 * physiques and the special candidates in effect, the signature ultimates, the Heavenly Flames, the natal treasure, the
 * Undying Codex, the soul's flight, the Kunpeng Wings, the lost sect arts, the Seven Fairies formation and the Sword
 * Sense. Everything works through {@code DiscipleHooks} providers registered here; data lives in
 * {@code DiscipleData.extra().power}. Every effect acts on entities only (never a block) and hurts only what
 * {@code DiscipleTechniques.mayHit} accepts.
 */
public final class DisciplePowers {

	/** Sub-compound of {@code DiscipleData.extra()} owned by module C. */
	public static final String EXTRA = "power";
	/** Key of module C's compound in the disciple screen's details ("power" there is phase 5's D). */
	public static final String INFO_KEY = "dpower";
	/** Prefix of module C's translation keys. */
	public static final String PREFIX = "kultivator.dpower.";
	private static final int REFUSE = 0xFF9A7A;

	/** Session state of a loaded disciple (counters, running charges); nothing here is saved. */
	static final class Live {
		int fortuneSeconds;
		int fightSeconds;
		int goldSeconds;
		int eyeSeconds;
		int parasolSeconds;
		int plumHits;
		int lastCasts = -1;
		double codexFraction;
		long fairiesUntil;
		/** The Kunpeng wings stay spread on the clients until this tick (BIT_KUNPENG). */
		long kunpengUntil;
		long lifeBuffUntil;
		long dragonUntil;
		@Nullable Ultimates.Lotus lotus;
		int ritualKind = -1;
		long ritualEnd;
		int ritualGuarded;
		@Nullable Vec3 ritualMaster;
	}

	private static final Map<DiscipleEntity, Live> LIVE = new WeakHashMap<>();
	private static final List<ToDoubleFunction<DiscipleEntity>> ULTIMATE_RANGE = new ArrayList<>();

	private static final SuggestionProvider<CommandSourceStack> PHYSIQUES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.asList(PowerIds.PHYSIQUE_IDS), builder);
	private static final SuggestionProvider<CommandSourceStack> TALENTS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>(Arrays.asList(PowerIds.TALENT_IDS));
		ids.add("none");
		return SharedSuggestionProvider.suggest(ids, builder);
	};
	private static final SuggestionProvider<CommandSourceStack> FLAMES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.asList(PowerIds.FLAME_IDS), builder);
	private static final SuggestionProvider<CommandSourceStack> SPECIALS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Arrays.asList(PowerIds.SPECIAL_IDS), builder);

	static Identifier id(String path) {
		return Kultivator.id(path);
	}

	static Live live(DiscipleEntity entity) {
		return LIVE.computeIfAbsent(entity, key -> new Live());
	}

	/** Held in place by a lotus or a ritual. */
	public static boolean holdingStill(DiscipleEntity entity) {
		Live live = LIVE.get(entity);
		return live != null && (live.lotus != null || live.ritualKind >= 0);
	}

	/** Sitting in a heavenly-flame ritual (drawn as meditating: the FLAME_CUP pose). */
	public static boolean inRitual(DiscipleEntity entity) {
		Live live = LIVE.get(entity);
		return live != null && live.ritualKind >= 0;
	}

	/**
	 * The look bits module C adds to the disciple's synced flags (DESIGN 27.6): {@link PowerIds#BIT_KUNPENG} while the
	 * wings are spread, {@link PowerIds#BIT_FAIRIES} while the Seven Fairies are linked. Read by the client renderer.
	 */
	public static int lookFlags(DiscipleEntity entity) {
		Live live = LIVE.get(entity);
		if (live == null) {
			return 0;
		}
		long now = PowerCore.now(entity);
		return (live.kunpengUntil > now ? PowerIds.BIT_KUNPENG : 0) | (live.fairiesUntil > now ? PowerIds.BIT_FAIRIES : 0);
	}

	// ================================================================== init

	public static void init() {
		HeavenlyFlames.init();
		// First: notes what kind of blow each damage-factor call is about (read by the after-damage listener).
		DiscipleHooks.addDamageFactor((entity, target, technique) -> {
			if (PowerCore.powered(entity)) {
				PowerCore.note(entity, technique ? PowerCore.HIT_TECHNIQUE : PowerCore.HIT_MELEE);
			}
			return 1.0D;
		});
		TalentEffects.register();
		Physiques.register();
		HereticGates.register();
		SpecialCandidates.register();
		Ultimates.register();
		HeavenlyFlames.register();
		NatalTreasure.register();
		UndyingCodex.register();
		SoulFlight.register();
		SectArts.register();
		SevenFairies.register();
		SwordSense.register();
		DiscipleHooks.addCombatCast(DisciplePowers::combatCast);
		DiscipleHooks.addTick20(DisciplePowers::tick20);
		DiscipleHooks.addInfo((entity, master, tag) -> tag.put(INFO_KEY, info(entity, master)));

		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DisciplePowers::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(DisciplePowers::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(HeavenlyFlames::afterDeath);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof DiscipleEntity disciple) {
				Live live = LIVE.remove(disciple);
				if (live != null) {
					HeavenlyFlames.interrupted(disciple, live);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(PowerPulses::tick);
		// The soul's flight is the last death handler (after module D's rescues registered in their init).
		ServerLifecycleEvents.SERVER_STARTING.register(server -> SoulFlight.registerLate());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PowerPulses.clear();
			LIVE.clear();
		});
	}

	// ================================================================== API (DESIGN 20.4.5)

	/**
	 * The bonded disciple that can cast {@code netherworld_white_tiger} with {@code master} (DESIGN 22.13.5): bonded
	 * (20.3.18: bond at least {@link #PAIR_MIN_BOND}), within {@code range} in the same level, not deviating or breaking
	 * through, with at least 30% qi. The highest bond wins, then the nearest. Null when none qualifies.
	 */
	public static @Nullable DiscipleEntity pairPartner(ServerPlayer master, double range) {
		DiscipleEntity best = null;
		int bestBond = -1;
		double bestDistance = Double.MAX_VALUE;
		for (DiscipleEntity entity : Disciples.loaded(master.getUUID())) {
			if (entity.role() != DiscipleEntity.Role.DISCIPLE || !entity.isAlive() || entity.isRemoved() || entity.level() != master.level()) {
				continue;
			}
			double distance = entity.distanceToSqr(master);
			DiscipleData data = entity.data();
			int bond = DisciplePaths.bond(data);
			if (distance > range * range || bond < PAIR_MIN_BOND || data.deviating() || data.breakingThrough()
					|| data.qi() < dev.kultivator.disciple.DiscipleCultivation.maxQi(data) * PAIR_MIN_QI) {
				continue;
			}
			if (bond > bestBond || bond == bestBond && distance < bestDistance) {
				best = entity;
				bestBond = bond;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** The bond a pair partner needs (20.3.18, bonded) and the share of its max qi it must hold (22.13.5). */
	public static final int PAIR_MIN_BOND = 50;
	public static final double PAIR_MIN_QI = 0.3D;

	/**
	 * Takes {@code share} of the pair cast's qi cost from the disciple's own pool (22.13.5: the disciple pays 50%). The cost
	 * is the master's qi cost of the {@code fusion_pair} spec at the master's mastery; {@code share} is clamped to 0-1.
	 * False (nothing taken) when the disciple cannot pay.
	 */
	public static boolean spendPairQi(DiscipleEntity entity, double share) {
		DiscipleData data = entity.data();
		double part = Math.max(0.0D, Math.min(1.0D, share));
		double cost;
		ServerPlayer master = entity.owner();
		dev.kultivator.data.TechniqueSpec pair = null;
		for (dev.kultivator.data.TechniqueSpec spec : dev.kultivator.data.TechniqueRegistry.all()) {
			if ("fusion_pair".equals(spec.special())) {
				pair = spec;
				break;
			}
		}
		if (master != null && pair != null) {
			dev.kultivator.data.CultivationData masterData = dev.kultivator.data.Cultivators.get(master);
			cost = dev.kultivator.technique.spec.TechniquePower.qi(pair, masterData,
					dev.kultivator.technique.spec.run.Mastery.level(masterData, pair.id())) * part;
		} else {
			cost = dev.kultivator.disciple.DiscipleCultivation.maxQi(data) * 0.25D * part;
		}
		if (data.qi() + 1.0E-6D < cost) {
			return false;
		}
		data.setQi(Math.max(0.0D, data.qi() - cost));
		return true;
	}

	/** Adds Poin Tempa of the Undying Codex (Pikul Air duty, DESIGN 20.3.9): also trains the body (+1 max health per hour). */
	public static void addTempering(DiscipleData data, int points) {
		UndyingCodex.addPoints(data, points, true);
	}

	/** Heavenly Flames absorbed. */
	public static int flames(DiscipleData data) {
		return PowerCore.tag(data).getListOrEmpty("flames").size();
	}

	/** Heretic God gates opened (0-7). */
	public static int gates(DiscipleData data) {
		return HereticGates.gates(data);
	}

	/** Teaches the Kunpeng Wings from the six bone pieces (DESIGN 20.3.10). False when it cannot learn them. */
	public static boolean teachKunpeng(DiscipleEntity entity) {
		return KunpengWings.teach(entity);
	}

	/** True once the disciple has its Dao's signature ultimate. */
	public static boolean hasUltimate(DiscipleData data) {
		return Ultimates.has(data);
	}

	// ---- additive API (not in 20.4.5; for the other modules)

	/** The Manual Tubuh Abadi from the Paviliun Yue Lu (DESIGN 20.3.7): opens the Undying Codex for this disciple. */
	public static void grantCodex(DiscipleData data) {
		PowerCore.editSub(data, "codex").putBoolean("granted", true);
	}

	/** True when the disciple may train the Undying Codex. */
	public static boolean codexAccess(DiscipleData data) {
		return UndyingCodex.access(data);
	}

	/** True while a special candidate still hides its nature (the beggar before Foundation, the Heretic Veins before Golden Core). */
	public static boolean hidden(DiscipleData data) {
		return SpecialCandidates.hiddenBeggar(data) || PowerCore.physique(data) == PowerIds.PHYSIQUE_HERETIC && !HereticGates.revealed(data);
	}

	/** Multiplies the radius of the ultimates (module D's soul lamps); product of all providers. */
	public static void addUltimateRangeFactor(ToDoubleFunction<DiscipleEntity> provider) {
		ULTIMATE_RANGE.add(provider);
	}

	static double ultimateRange(DiscipleEntity entity) {
		double product = 1.0D;
		for (ToDoubleFunction<DiscipleEntity> provider : ULTIMATE_RANGE) {
			double value = provider.applyAsDouble(entity);
			if (Double.isFinite(value) && value > 0.0D) {
				product *= value;
			}
		}
		return Math.max(0.5D, Math.min(4.0D, product));
	}

	// ================================================================== hooks wired here

	/** Every second in a fight: a running lotus blocks other casts; then the gates, the physique, the ultimate, the codex, the wings. */
	private static boolean combatCast(DiscipleEntity entity, LivingEntity target) {
		if (!PowerCore.isDisciple(entity)) {
			return false;
		}
		if (live(entity).lotus != null) {
			return true;
		}
		return HereticGates.combatCast(entity, target) || Physiques.combatCast(entity, target) || Ultimates.tryAuto(entity, target)
				|| UndyingCodex.combatCast(entity, target) || KunpengWings.combatCast(entity, target);
	}

	/** Once a second for every loaded disciple. */
	private static void tick20(DiscipleEntity entity) {
		if (!PowerCore.isDisciple(entity) || !(entity.level() instanceof ServerLevel)) {
			return;
		}
		Live live = live(entity);
		TalentEffects.tick(entity, live);
		Physiques.tick(entity, live);
		HereticGates.update(entity);
		SpecialCandidates.tick(entity);
		HeavenlyFlames.tick(entity, live);
		NatalTreasure.tick(entity, live);
		UndyingCodex.tick(entity);
		SectArts.tick(entity);
		SevenFairies.tick(entity, live);
		if (flames(entity.data()) > 0 && entity.isOnFire()) {
			entity.clearFire();
		}
		firstSight(entity);
		syncPower(entity);
	}

	/** The first time a master sees a disciple with a rare physique: its look, a line and the advancement. */
	private static void firstSight(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		int physique = PowerCore.physique(data);
		ServerPlayer master = entity.owner();
		if (physique <= 0 || physique >= PowerIds.PHYSIQUE_IDS.length || master == null || hidden(data)
				|| PowerCore.tag(data).getBooleanOr("seen_physique", false) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		PowerCore.edit(data).putBoolean("seen_physique", true);
		PowerCore.fx(level, entity.position(), PowerIds.FX_PHYSIQUE, physique, 0.0F);
		Msg.chat(master, PowerCore.key("physique.seen", PowerCore.name(entity), PowerCore.key("physique." + PowerIds.PHYSIQUE_IDS[physique]))
				.withColor(PowerIds.PHYSIQUE_RGB[physique]));
		dev.kultivator.util.Advancements.grant(master, "rare_physique");
	}

	/** Copies what the client draws into the synced power bits (0-15) and the synced talent. */
	static void syncPower(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		Live live = live(entity);
		long now = PowerCore.now(entity);
		int physique = PowerCore.physique(data);
		int bits = HeavenlyFlames.kindBits(data) & PowerIds.BITS_FLAMES;
		if (live.dragonUntil > now) {
			bits |= PowerIds.BIT_DRAGON;
		}
		if (physique == PowerIds.PHYSIQUE_POISON && data.realm().atLeast(Realm.FOUNDATION)) {
			bits |= PowerIds.BIT_POISON;
		}
		if (physique == PowerIds.PHYSIQUE_GOLDEN_BODY && Physiques.goldStacks(data) >= 10
				|| UndyingCodex.access(data) && UndyingCodex.stage(data) >= 2) {
			bits |= PowerIds.BIT_GOLD;
		}
		if (HereticGates.bursting(entity) && physique == PowerIds.PHYSIQUE_HERETIC || Physiques.rampaging(entity)) {
			bits |= PowerIds.BIT_RAMPAGE;
		}
		int tier = NatalTreasure.tier(data);
		if (tier > 0) {
			bits |= tier << PowerIds.NATAL_TIER_SHIFT;
			bits |= NatalTreasure.form(data) << PowerIds.NATAL_FORM_SHIFT;
			if (NatalTreasure.folds(data) >= NatalTreasure.ETERNAL) {
				bits |= PowerIds.BIT_NATAL_ORBIT;
			}
		}
		if ((SectArts.arts(entity) & SectArts.ART_PURPLE_MIST) != 0 && entity.isMeditatingNow()) {
			bits |= PowerIds.BIT_PURPLE_MIST;
		}
		if (live.lotus != null) {
			bits |= PowerIds.BIT_LOTUS;
		}
		entity.setSyncedPower(DiscipleEntity.POWER_MASK_C, bits);
		entity.setSyncedTalent(syncedTalent(data));
	}

	/**
	 * The synced talent value (physique, soul colour, special) with what still hides masked: the veins before Golden Core,
	 * the beggar before Foundation are never sent to the client. Module A's {@code Talents.packed} uses it too.
	 */
	public static int syncedTalent(DiscipleData data) {
		int physique = PowerCore.physique(data);
		boolean veinsHidden = physique == PowerIds.PHYSIQUE_HERETIC && !HereticGates.revealed(data);
		return DiscipleEntity.packTalent(veinsHidden ? 0 : physique, PowerCore.colour(data),
				SpecialCandidates.hiddenBeggar(data) ? 0 : PowerCore.special(data));
	}

	/** Immunities and dodges: poison body, flames, golden skin, Double Pupils, Sword Sense. Never during /kill. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof DiscipleEntity disciple) || !PowerCore.powered(disciple) || source.is(DamageTypes.GENERIC_KILL)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (Physiques.immuneTo(disciple, source)) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_FIRE) && flames(disciple.data()) > 0 || UndyingCodex.immuneTo(disciple, source)) {
			disciple.clearFire();
			return false;
		}
		if (DuelRules.inDuel(disciple)) {
			return true;
		}
		Entity attacker = source.getEntity();
		boolean monsterMelee = attacker instanceof Enemy && attacker == source.getDirectEntity() && !source.is(DamageTypeTags.IS_PROJECTILE);
		if (monsterMelee && PowerCore.physique(disciple.data()) == PowerIds.PHYSIQUE_DOUBLE_PUPILS
				&& disciple.getRandom().nextDouble() < Physiques.PUPILS_DODGE) {
			// Shi Yi's Double Pupils see the blow before it comes.
			if (disciple.level() instanceof ServerLevel level) {
				PowerCore.fx(level, disciple.position(), PowerIds.FX_DODGE, 0, 0.0F);
				PowerCore.sound(level, disciple.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8F, 1.6F);
			}
			return false;
		}
		return !(monsterMelee && SwordSense.dodges(disciple, source));
	}

	/** After a disciple's blow landed: melee poison and flame, the Plum Blossom count. */
	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(source.getEntity() instanceof DiscipleEntity disciple)) {
			return;
		}
		int kind = PowerCore.takeNote(disciple);
		if (kind < 0 || kind == PowerCore.HIT_AREA || kind == PowerCore.HIT_ULTIMATE || blocked || damageTaken <= 0.0F
				|| !PowerCore.powered(disciple) || victim == disciple) {
			return;
		}
		if (kind == PowerCore.HIT_MELEE) {
			Physiques.onMeleeHit(disciple, victim);
			HeavenlyFlames.onMeleeHit(disciple, victim);
		}
		SectArts.onHit(disciple, victim, kind == PowerCore.HIT_TECHNIQUE);
	}

	// ================================================================== screen details

	/** The Bakat and Pusaka tabs' details, under {@link #INFO_KEY} in the disciple's info. */
	private static CompoundTag info(DiscipleEntity entity, ServerPlayer master) {
		DiscipleData data = entity.data();
		Live live = live(entity);
		long now = PowerCore.now(entity);
		CompoundTag tag = new CompoundTag();
		int colour = PowerCore.colour(data);
		tag.putInt("colour", colour);
		tag.putDouble("colour_factor", DiscipleHooks.potentialFactor(data));
		tag.putBoolean("red_capped", TalentEffects.redCapped(data));
		tag.putInt("talents", Talents.talents(data));
		tag.putInt("physique", PowerCore.physique(data));
		tag.putInt("special", PowerCore.special(data));
		tag.putBoolean("hidden", hidden(data));
		tag.putInt("tekad", Talents.tekad(data));
		tag.putInt("regressor_realm", SpecialCandidates.regressorRealm(data).ordinal());
		// The remembered realm shows as "???" until the entrance test (module A's extra.talent.tested).
		tag.putBoolean("regressor_known", data.extra().getCompoundOrEmpty(Talents.EXTRA).getBooleanOr("tested", false));
		tag.putBoolean("regressing", SpecialCandidates.regressing(data));
		tag.putInt("sect_rank", SectRanks.rank(data));
		tag.putInt("stars", DisciplePaths.stars(data));
		tag.putInt("arts", SectArts.arts(entity));
		tag.putBoolean("fairies", live.fairiesUntil > now);
		tag.putInt("sword_kills", PowerCore.tag(data).getIntOr("sword_kills", 0));
		// Physiques
		tag.putInt("gates", HereticGates.gates(data));
		tag.putBoolean("heretic_revealed", HereticGates.revealed(data));
		tag.putInt("burst_left", (int) PowerCore.cooldownLeft(data, "burst", now));
		tag.putBoolean("bursting", HereticGates.bursting(entity));
		tag.putBoolean("exhausted", HereticGates.exhausted(entity));
		tag.putInt("toxic", Physiques.toxin(data));
		tag.putBoolean("rampage", Physiques.rampaging(entity));
		tag.putInt("gold_stacks", Physiques.goldStacks(data));
		tag.putDouble("gold_hp", PowerCore.tag(data).getDoubleOr("gold_hp", 0.0D));
		tag.putInt("dragon_left", (int) PowerCore.cooldownLeft(data, "dragon", now));
		tag.putBoolean("dragon", live.dragonUntil > now);
		tag.putInt("cloud_left", (int) PowerCore.cooldownLeft(data, "cloud", now));
		Technique bone = Physiques.boneTechnique(data.rootElements());
		tag.putString("bone", bone.id());
		tag.putBoolean("bone_shed", PowerCore.tag(data).getBooleanOr("bone_shed", false));
		tag.putInt("bone_left", (int) PowerCore.cooldownLeft(data, "bone", now));
		tag.putBoolean("dou_di", PowerCore.tag(data).getBooleanOr("dou_di", false));
		// Ultimate
		tag.putBoolean("ult_has", Ultimates.has(data));
		tag.putInt("ult_dao", data.daoIndex());
		tag.putInt("ult_left", (int) PowerCore.cooldownLeft(data, "ult", now));
		tag.putInt("ult_cooldown", Ultimates.cooldownFor(data.dao()));
		String ultWhy = !Ultimates.has(data) ? (data.dao() == null ? "why.ult_no_dao" : "why.ult_realm")
				: !PowerCore.ready(data, "ult", now) ? "why.cooldown" : data.breakingThrough() || data.deviating() ? "why.busy" : null;
		tag.putString("ult_reason", ultWhy == null ? "" : PREFIX + ultWhy);
		tag.putBoolean("lotus", live.lotus != null);
		tag.putBoolean("lotus_fused", live.lotus != null && live.lotus.fused);
		tag.putBoolean("master_fire", dev.kultivator.data.Cultivators.get(master).dao() == dev.kultivator.data.Dao.FIRE);
		// Flames
		ListTag flames = new ListTag();
		ListTag own = HeavenlyFlames.list(data);
		for (int i = 0; i < own.size(); i++) {
			flames.add(IntTag.valueOf(own.getIntOr(i, 0)));
		}
		tag.put("flames", flames);
		tag.putInt("flame_max", HeavenlyFlames.max(data));
		tag.putBoolean("flame_eligible", HeavenlyFlames.eligible(data));
		tag.putInt("ritual_kind", live.ritualKind);
		tag.putInt("ritual_left", live.ritualKind < 0 ? 0 : (int) Math.max(0L, live.ritualEnd - now));
		// Natal treasure, codex, wings
		tag.put("natal", NatalTreasure.info(entity, master));
		CompoundTag codex = new CompoundTag();
		codex.putBoolean("access", UndyingCodex.access(data));
		codex.putBoolean("granted", PowerCore.tag(data).getCompoundOrEmpty("codex").getBooleanOr("granted", false));
		codex.putInt("pts", UndyingCodex.points(data));
		codex.putInt("stage", UndyingCodex.stage(data));
		codex.putInt("blood", UndyingCodex.blood(data));
		codex.putInt("next_at", UndyingCodex.nextAt(data));
		CompoundTag saved = PowerCore.tag(data).getCompoundOrEmpty("codex");
		codex.putInt("day_pts", saved.getLongOr("day", -1L) == PowerCore.day((ServerLevel) entity.level()) ? saved.getIntOr("day_pts", 0) : 0);
		codex.putInt("day_cap", UndyingCodex.DAILY_CAP);
		codex.putDouble("trained_hp", PowerCore.tag(data).getDoubleOr("trained_hp", 0.0D));
		tag.put("codex", codex);
		tag.putBoolean("kunpeng", KunpengWings.has(data));
		tag.putBoolean("kunpeng_full", KunpengWings.full(data));
		tag.putInt("kunpeng_left", (int) PowerCore.cooldownLeft(data, "kunpeng", now));
		return tag;
	}

	// ================================================================== screen actions

	/** {@code Actions.DISCIPLE_POWER}: {@code arg} = the disciple's entity id, {@code text} = "op[:param]". */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		String text = action.text() == null ? "" : action.text();
		Entity entity = player.level().getEntity(action.arg());
		if (!(entity instanceof DiscipleEntity disciple) || !disciple.isAlive() || disciple.isReleasing()
				|| disciple.role() != DiscipleEntity.Role.DISCIPLE) {
			Msg.actionBar(player, Component.translatable("kultivator.disciples.gone").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!disciple.isOwnedBy(player)) {
			Msg.chat(player, Component.translatable("kultivator.disciples.not_yours", disciple.getDisplayName()).withStyle(ChatFormatting.RED));
			return;
		}
		if (player.distanceToSqr(disciple) > Disciples.ACTION_RANGE * Disciples.ACTION_RANGE) {
			Msg.chat(player, Component.translatable("kultivator.disciples.too_far", disciple.getDisplayName(), (int) Disciples.ACTION_RANGE)
					.withStyle(ChatFormatting.YELLOW));
			return;
		}
		int colon = text.indexOf(':');
		String op = colon < 0 ? text : text.substring(0, colon);
		String param = colon < 0 ? "" : text.substring(colon + 1);
		String why = switch (op) {
			case "ultimate" -> Ultimates.order(disciple, player);
			case "fusion" -> Ultimates.fuse(disciple, player);
			case "burst" -> HereticGates.burst(disciple, true);
			case "kunpeng" -> KunpengWings.dash(disciple, null);
			case "natal_form" -> NatalTreasure.refine(disciple, player, parseInt(param, 0));
			case "natal_enhance" -> NatalTreasure.enhance(disciple, player);
			default -> null;
		};
		if (why != null) {
			Msg.chat(player, PowerCore.key("refuse", PowerCore.name(disciple), PowerCore.key(why)).withColor(REFUSE));
		}
		syncPower(disciple);
		if (disciple.isAlive() && !disciple.isRemoved()) {
			Disciples.sendInfo(player, disciple);
		}
	}

	private static int parseInt(String text, int fallback) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	// ================================================================== debug commands

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		LiteralArgumentBuilder<CommandSourceStack> murid = Commands.literal("murid");
		murid.then(Commands.literal("fisik").then(Commands.argument("id", StringArgumentType.word()).suggests(PHYSIQUES)
				.executes(DisciplePowers::debugPhysique)));
		murid.then(Commands.literal("bakat").then(Commands.argument("id", StringArgumentType.word()).suggests(TALENTS)
				.executes(DisciplePowers::debugTalent)));
		murid.then(Commands.literal("istimewa").then(Commands.argument("id", StringArgumentType.word()).suggests(SPECIALS)
				.executes(DisciplePowers::debugSpecial)));
		murid.then(Commands.literal("warna").then(Commands.argument("warna", IntegerArgumentType.integer(0, 6))
				.executes(DisciplePowers::debugColour)));
		murid.then(Commands.literal("lipat").then(Commands.argument("n", IntegerArgumentType.integer(0, NatalTreasure.MAX_FOLDS + 1))
				.executes(DisciplePowers::debugFolds)));
		murid.then(Commands.literal("api").then(Commands.argument("kind", StringArgumentType.word()).suggests(FLAMES)
				.executes(DisciplePowers::debugFlame)));
		murid.then(Commands.literal("tempa").then(Commands.argument("n", IntegerArgumentType.integer(0, 1_000_000))
				.executes(DisciplePowers::debugTempering)));
		murid.then(Commands.literal("kunpeng").executes(DisciplePowers::debugKunpeng));
		murid.then(Commands.literal("siap").executes(DisciplePowers::debugReady));
		debug.then(murid);
	}

	private static int debugPhysique(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity disciple = debugTarget(ctx);
		if (disciple == null) {
			return 0;
		}
		int physique = indexOf(PowerIds.PHYSIQUE_IDS, StringArgumentType.getString(ctx, "id"));
		if (physique < 0) {
			return fail(ctx);
		}
		// Debug only: module A's talent section, so a physique can be tried without waiting for a candidate.
		DiscipleData data = disciple.data();
		data.extraSection(Talents.EXTRA).putInt("physique", physique);
		CompoundTag power = PowerCore.edit(data);
		power.remove("seen_physique");
		power.remove("heretic_revealed");
		power.remove("dou_di");
		power.remove("bone_shed");
		power.putInt("gates", 0);
		return debugDone(ctx, disciple);
	}

	private static int debugTalent(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity disciple = debugTarget(ctx);
		if (disciple == null) {
			return 0;
		}
		String id = StringArgumentType.getString(ctx, "id");
		CompoundTag talent = disciple.data().extraSection(Talents.EXTRA);
		if ("none".equals(id)) {
			talent.putInt("talents", 0);
		} else {
			int index = indexOf(PowerIds.TALENT_IDS, id);
			if (index < 0) {
				return fail(ctx);
			}
			talent.putInt("talents", talent.getIntOr("talents", 0) ^ 1 << index);
		}
		return debugDone(ctx, disciple);
	}

	private static int debugSpecial(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity disciple = debugTarget(ctx);
		if (disciple == null) {
			return 0;
		}
		int special = indexOf(PowerIds.SPECIAL_IDS, StringArgumentType.getString(ctx, "id"));
		if (special < 0) {
			return fail(ctx);
		}
		CompoundTag talent = disciple.data().extraSection(Talents.EXTRA);
		CompoundTag tag = talent.getCompoundOrEmpty("special").copy();
		tag.putInt("type", special);
		if (special == PowerIds.SPECIAL_REGRESSOR && !tag.contains("realm_r")) {
			tag.putInt("realm_r", Realm.NASCENT_SOUL.ordinal());
		}
		talent.put("special", tag);
		PowerCore.edit(disciple.data()).remove("beggar_revealed");
		PowerCore.edit(disciple.data()).remove("beggar_dao");
		return debugDone(ctx, disciple);
	}

	private static int debugColour(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity disciple = debugTarget(ctx);
		if (disciple == null) {
			return 0;
		}
		Talents.setSoulColor(disciple.data(), IntegerArgumentType.getInteger(ctx, "warna"));
		return debugDone(ctx, disciple);
	}

	private static int debugFolds(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity disciple = debugTarget(ctx);
		if (disciple == null) {
			return 0;
		}
		CompoundTag natal = PowerCore.editSub(disciple.data(), "natal");
		if (!natal.contains("form")) {
			natal.putInt("form", PowerIds.NATAL_SWORD);
		}
		natal.putInt("folds", Math.min(NatalTreasure.maxFolds(disciple.data()), IntegerArgumentType.getInteger(ctx, "n")));
		return debugDone(ctx, disciple);
	}

	private static int debugFlame(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity disciple = debugTarget(ctx);
		if (disciple == null) {
			return 0;
		}
		int kind = indexOf(PowerIds.FLAME_IDS, StringArgumentType.getString(ctx, "kind"));
		if (kind < 0) {
			return fail(ctx);
		}
		DiscipleData data = disciple.data();
		if (!HeavenlyFlames.hasKind(data, kind)) {
			ListTag list = PowerCore.edit(data).getListOrEmpty("flames").copy();
			list.add(IntTag.valueOf(kind));
			PowerCore.edit(data).put("flames", list);
		}
		return debugDone(ctx, disciple);
	}

	private static int debugTempering(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity disciple = debugTarget(ctx);
		if (disciple == null) {
			return 0;
		}
		CompoundTag codex = PowerCore.editSub(disciple.data(), "codex");
		codex.putBoolean("granted", true);
		codex.putInt("pts", IntegerArgumentType.getInteger(ctx, "n"));
		return debugDone(ctx, disciple);
	}

	private static int debugKunpeng(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity disciple = debugTarget(ctx);
		if (disciple == null) {
			return 0;
		}
		PowerCore.editSub(disciple.data(), "kunpeng").putBoolean("has", true);
		return debugDone(ctx, disciple);
	}

	private static int debugReady(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		DiscipleEntity disciple = debugTarget(ctx);
		if (disciple == null) {
			return 0;
		}
		PowerCore.edit(disciple.data()).remove("cd");
		return debugDone(ctx, disciple);
	}

	private static int debugDone(CommandContext<CommandSourceStack> ctx, DiscipleEntity disciple) {
		disciple.refreshSynced();
		syncPower(disciple);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.disciples.debug.done", disciple.getDisplayName())), false);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendFailure(Component.translatable(PREFIX + "debug.unknown"));
		return 0;
	}

	private static int indexOf(String[] ids, String id) {
		String lower = id.toLowerCase(Locale.ROOT);
		for (int i = 0; i < ids.length; i++) {
			if (ids[i].equals(lower)) {
				return i;
			}
		}
		try {
			int number = Integer.parseInt(lower);
			return number >= 0 && number < ids.length ? number : -1;
		} catch (NumberFormatException exception) {
			return -1;
		}
	}

	/** The disciple the operator looks at within 16 blocks, else the nearest one (any master). */
	private static @Nullable DiscipleEntity debugTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		AABB box = player.getBoundingBox().inflate(Disciples.ACTION_RANGE);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		DiscipleEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (DiscipleEntity disciple : player.level().getEntitiesOfClass(DiscipleEntity.class, box,
				each -> each.isAlive() && each.role() == DiscipleEntity.Role.DISCIPLE)) {
			Vec3 to = disciple.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > Disciples.ACTION_RANGE) {
				continue;
			}
			double aligned = distance < 1.0E-3D ? 1.0D : to.scale(1.0D / distance).dot(look);
			// Looked-at disciples first (alignment), then the nearest.
			double score = aligned > 0.97D ? distance - 100.0D : distance;
			if (score < bestScore) {
				bestScore = score;
				best = disciple;
			}
		}
		if (best == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.disciples.debug.no_target"));
		}
		return best;
	}

	private DisciplePowers() {
	}
}
