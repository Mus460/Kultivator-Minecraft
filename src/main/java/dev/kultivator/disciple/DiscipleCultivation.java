package dev.kultivator.disciple;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.PillItem;
import dev.kultivator.alchemy.PillKind;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.beast.SpiritBeastPillItem;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.QiFormations;
import dev.kultivator.cultivation.VanillaPills;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.data.Technique;
import dev.kultivator.data.XpSource;
import dev.kultivator.dao.TechniqueBooks;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.item.RootItems;
import dev.kultivator.sect.Sects;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A disciple's cultivation (DESIGN 18): qi, meditation, XP with the same Rank math and bottleneck as the master (times
 * the root grade and the disciple factor 0.6), kills, pills from the master (70% of the player's numbers, one pill of a
 * kind per minute, no toxicity), teaching, breakthroughs ({@link DiscipleBreakthrough}), the Dao choice, the details for
 * the disciple screen and the stat bonuses the entity applies. Everything runs on the server; effects are client
 * particles sent with {@link Fx}, sounds are played by the server.
 */
public final class DiscipleCultivation {

	/** Disciples cultivate at 60% of the master's speed. */
	public static final double XP_FACTOR = 0.6D;
	/** Pills give the disciple this share of the player's numbers. */
	public static final double PILL_SHARE = 0.7D;
	/** One pill of a kind per disciple per minute. */
	public static final int PILL_COOLDOWN = 1200;
	/** Teaching costs the master this share of their max qi. */
	public static final double TEACH_COST = 0.2D;
	/** The master must be this close to start the breakthrough. */
	public static final double BREAKTHROUGH_RANGE = 16.0D;
	/** Meditating near a meditating master shares 10% of the master's meditation gain. */
	public static final double MASTER_MEDITATION_RANGE = 8.0D;
	public static final double MASTER_SHARE = 0.1D;
	/** Comprehension per cast of a technique of the disciple's own Dao (before root affinity). */
	public static final double TECHNIQUE_COMPREHENSION = 0.15D;
	/** Comprehension per second of meditation once a Dao is chosen (before root affinity). */
	public static final double MEDITATION_COMPREHENSION = 0.01D;
	/** Comprehension per monster kill once a Dao is chosen (before root affinity). */
	public static final double KILL_COMPREHENSION = 0.05D;
	/** Comprehension the moment a Dao is chosen (before root affinity). */
	public static final double FIRST_INSIGHT = 5.0D;
	/** A mini tribulation needs at least this share of max health. */
	public static final double TRIBULATION_MIN_HEALTH = 0.5D;
	/** Kill XP: monster max health times this. */
	public static final double KILL_XP = 0.4D;

	private static final int GOLD = 0xFFD54A;
	private static final int JADE = 0x8FE3C8;
	private static final int REFUSE = 0xFF9A7A;
	private static final int MUTED = 0xA8B5B0;
	private static final String PREFIX = "kultivator.disciplecult.";

	/** Attack techniques in the order a disciple tries them (strongest and widest first). */
	private static final Technique[] ATTACK_ORDER = {
			Technique.THOUSAND_SWORDS, Technique.HEAVENLY_THUNDER, Technique.PHOENIX_FLAME, Technique.SPACE_PRISON,
			Technique.GLACIER_PRISON, Technique.MOUNTAIN_STOMP, Technique.BUDDHA_PALM, Technique.LIFE_DRAIN, Technique.SWORD_QI,
			Technique.THUNDER_PALM, Technique.FLAME_PALM, Technique.FROST_LOTUS, Technique.SHADOW_STEP, Technique.SPACE_BLINK,
			Technique.QI_PALM
	};

	// ================================================================== tick

	/** Every server tick of a disciple (cheap: most work runs once a second). */
	public static void tick(DiscipleEntity entity) {
		if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) {
			return;
		}
		DiscipleData data = entity.data();
		DiscipleBreakthrough.tick(entity, level, data);
		DiscipleTechniques.tick(entity, level);
		if (entity.tickCount % 20 != 0) {
			return;
		}
		data.tickCooldowns(20);
		boolean changed = false;

		if (data.deviating()) {
			data.setDeviationTicks(data.deviationTicks() - 20);
			if (data.deviating()) {
				entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, true, true), entity);
			} else {
				entity.removeEffect(MobEffects.WEAKNESS);
				changed = true;
				ServerPlayer master = entity.owner();
				if (master != null) {
					Msg.chat(master, Component.translatable(PREFIX + "deviation_end", name(entity)).withColor(JADE));
				}
			}
		}

		boolean meditating = entity.isMeditatingNow() && !data.breakingThrough();
		double max = maxQi(data);
		if (data.qi() > max) {
			data.setQi(max);
		} else if (data.qi() < max) {
			data.setQi(Math.min(max, data.qi() + regenPerSecond(data, meditating)));
		}

		if (meditating) {
			data.addMeditationTicks(20L);
			addXp(entity, meditationBase(entity, data), XpSource.MEDITATION);
			Dao dao = data.dao();
			if (dao != null) {
				data.addComprehension(comprehensionGain(data, dao, MEDITATION_COMPREHENSION));
			}
			if (entity.getHealth() < entity.getMaxHealth()) {
				entity.heal(entity.getMaxHealth() * 0.01F);
			}
			ServerPlayer master = entity.owner();
			if (master != null && master.level() == level && master.distanceToSqr(entity) <= MASTER_MEDITATION_RANGE * MASTER_MEDITATION_RANGE) {
				CultivationData masterData = Cultivators.get(master);
				if (masterData.meditating()) {
					addXp(entity, masterMeditationGain(master, masterData) * MASTER_SHARE * (data.deviating() ? 0.5D : 1.0D), XpSource.EXACT);
				}
			}
		}
		if (data.fledNoticePending()) {
			// A tribulation abandoned by unloading counted as failed (DiscipleData.load): the master hears it once online.
			ServerPlayer master = entity.owner();
			if (master != null) {
				data.setFledNoticePending(false);
				Component name = name(entity);
				Msg.chat(master, Component.translatable("kultivator.disciplecult.bt.fail_abandoned", name).withStyle(ChatFormatting.RED));
				Msg.chat(master, Component.translatable("kultivator.disciplecult.bt.deviation", name,
						String.valueOf(DiscipleBreakthrough.DEVIATION_TICKS / 20)).withColor(0xFF8A7A));
			}
		}
		if (changed) {
			entity.refreshSynced();
		}
	}

	// ================================================================== qi

	/** Max qi: {@code 60 + realm x 80 + stage x 20 + layer x 40}. */
	public static double maxQi(DiscipleData data) {
		Rank rank = data.rank();
		return 60.0D + rank.realm().ordinal() * 80.0D + rank.stage() * 20.0D + rank.layer() * 40.0D;
	}

	/** Qi per second: 0.6% of max, x4 while meditating, nothing during a Qi Deviation. */
	public static double regenPerSecond(DiscipleData data, boolean meditating) {
		if (data.deviating()) {
			return 0.0D;
		}
		return maxQi(data) * 0.006D * (meditating ? 4.0D : 1.0D);
	}

	// ================================================================== XP

	/** Root grade x 0.6 x server config, halved during a Qi Deviation; EXACT is always 1. */
	public static double xpMultiplier(DiscipleData data, XpSource source) {
		if (source == XpSource.EXACT) {
			return 1.0D;
		}
		double config = Math.max(0.0D, KultivatorConfig.get().xpMultiplier);
		return data.rootGrade().multiplier() * XP_FACTOR * config * (data.deviating() ? 0.5D : 1.0D);
	}

	/**
	 * What one second of the master's meditation gives the master (as {@code Meditation} computes it): {@code 0.6 + 0.25 x
	 * realm} times the spiritual site, the Qi Gathering Formation, the spirit vein density, the sect bonus and the master's
	 * own multipliers (root, breakthrough quality, server config, Qi Deviation).
	 */
	private static double masterMeditationGain(ServerPlayer master, CultivationData masterData) {
		double site = masterData.siteMultiplier() > 0.0D ? masterData.siteMultiplier() : 1.0D;
		double base = (0.6D + 0.25D * masterData.realm().ordinal()) * site * QiFormations.multiplier(masterData.formationTier())
				* Math.max(0.15D, masterData.veinDensity());
		return base * Sects.meditationBonus(master) * Progression.xpMultiplier(masterData, XpSource.MEDITATION);
	}

	/** Meditation XP per second before multipliers: {@code 0.4 + 0.2 x realm}, x1.5 when ordered to meditate. */
	private static double meditationBase(DiscipleEntity entity, DiscipleData data) {
		double base = 0.4D + 0.2D * data.realm().ordinal();
		return "MEDITATE".equals(entity.mode().name()) ? base * 1.5D : base;
	}

	/**
	 * Adds XP from {@code source} with the disciple multipliers ({@link XpSource#EXACT}: none): fills the bar, climbs
	 * stages (FX + a chat line to the master), stops at a bottleneck and keeps the rest as Stored Qi. Returns the XP
	 * actually added.
	 */
	public static double addXp(DiscipleEntity entity, double amount, XpSource source) {
		if (!(amount > 0.0D) || !Double.isFinite(amount)) {
			return 0.0D;
		}
		DiscipleData data = entity.data();
		double gain = amount * xpMultiplier(data, source);
		if (!(gain > 0.0D) || !Double.isFinite(gain)) {
			return 0.0D;
		}
		data.addTotalXp(gain);
		int stageUps = distribute(data, gain);
		ServerPlayer master = entity.owner();
		if (stageUps > 0) {
			onStageUp(entity, data, stageUps, master);
		}
		if (master != null) {
			notifyBottleneck(entity, master, data);
		}
		return gain;
	}

	/**
	 * Puts {@code amount} into the bar with the rules of DESIGN 1 (no multipliers): stage-ups below Peak with carry-over,
	 * a full bar and Stored Qi at a bottleneck, only Stored Qi at the very end. Returns the number of stage-ups.
	 */
	static int distribute(DiscipleData data, double amount) {
		double left = amount;
		int stageUps = 0;
		for (int guard = 0; left > 0.0D && guard <= Rank.MAX_STEP + 2; guard++) {
			Rank rank = data.rank();
			if (rank.isMax()) {
				data.setStoredQi(data.storedQi() + left);
				return stageUps;
			}
			double room = Math.max(0.0D, rank.xpRequired() - data.xp());
			if (rank.isBottleneck()) {
				double into = Math.min(room, left);
				data.setXp(data.xp() + into);
				left -= into;
				if (left > 0.0D) {
					data.setStoredQi(data.storedQi() + left);
				}
				return stageUps;
			}
			if (left < room) {
				data.setXp(data.xp() + left);
				return stageUps;
			}
			left -= room;
			data.setRank(rank.next());
			data.setXp(0.0D);
			stageUps++;
		}
		if (left > 0.0D) {
			data.setStoredQi(data.storedQi() + left);
		}
		return stageUps;
	}

	private static void onStageUp(DiscipleEntity entity, DiscipleData data, int count, @Nullable ServerPlayer master) {
		entity.refreshSynced();
		Rank rank = data.rank();
		if (entity.level() instanceof ServerLevel level) {
			Fx.send(level, entity.position(), FxType.STAGE_UP, rank.realm().ordinal(), rank.stage(), count);
			sound(level, entity.position().add(0.0D, 1.0D, 0.0D), SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.9F + 0.12F * rank.stage());
			sound(level, entity.position().add(0.0D, 1.0D, 0.0D), SoundEvents.PLAYER_LEVELUP, 0.25F, 1.6F);
		}
		if (master != null) {
			Msg.chat(master, Component.translatable(PREFIX + "stage_up", name(entity),
					rank.displayName().withColor(rank.realm().color())).withStyle(ChatFormatting.AQUA));
		}
	}

	/** Tells the master once per bottleneck (and once at the end of the path) that the disciple is ready. */
	static void notifyBottleneck(DiscipleEntity entity, ServerPlayer master, DiscipleData data) {
		Rank rank = data.rank();
		int step = rank.step();
		if (data.notifiedStep() == step) {
			return;
		}
		if (rank.isMax()) {
			data.setNotifiedStep(step);
			Msg.chat(master, Component.translatable(PREFIX + "peak", name(entity)).withColor(GOLD));
			return;
		}
		if (!rank.isBottleneck() || !data.barFull()) {
			return;
		}
		data.setNotifiedStep(step);
		if (rank.realm().isMortal()) {
			Msg.chat(master, Component.translatable(PREFIX + "bottleneck_awakening", name(entity)).withStyle(ChatFormatting.LIGHT_PURPLE));
		} else {
			Msg.chat(master, Component.translatable(PREFIX + "bottleneck", name(entity),
					rank.displayName().withColor(rank.realm().color())).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		master.level().playSound(null, entity.getX(), entity.getY() + 1.0D, entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.NEUTRAL, 0.9F, 0.6F);
	}

	/** Comprehension gained for {@code amount}: x1.5 when the root has the Dao's affinity. */
	public static double comprehensionGain(DiscipleData data, Dao dao, double amount) {
		return amount * (dao.hasAffinity(data.rootElements()) ? 1.5D : 1.0D);
	}

	/** Debug / commands: puts the disciple at {@code rank}, keeps what fits of the bar, re-applies its stats. */
	public static void setRank(DiscipleEntity entity, Rank rank) {
		DiscipleData data = entity.data();
		data.setRank(rank);
		data.setXp(Math.min(data.xp(), data.rank().xpRequired()));
		data.setQi(Math.min(data.qi(), maxQi(data)));
		data.setNotifiedStep(-1);
		entity.refreshSynced();
		entity.setHealth(Math.min(entity.getHealth(), entity.getMaxHealth()));
	}

	// ================================================================== combat

	/** A monster the disciple killed: {@code maxHealth x 0.4} XP, a kill counted, a little comprehension of its Dao. */
	public static void onKill(DiscipleEntity entity, LivingEntity victim) {
		if (!(victim instanceof Enemy) || victim == entity) {
			return;
		}
		DiscipleData data = entity.data();
		data.addKill();
		addXp(entity, victim.getMaxHealth() * KILL_XP, XpSource.COMBAT);
		Dao dao = data.dao();
		if (dao != null) {
			data.addComprehension(comprehensionGain(data, dao, KILL_COMPREHENSION));
		}
	}

	/**
	 * Picks a ready, affordable learned technique and casts it: Spring of Life first when the master is below half health
	 * (or the disciple below 40%), Iron Body when it is not up yet, then the attack techniques (Gale only when crowded or
	 * hurt). True when a technique was cast.
	 */
	public static boolean tryCastInCombat(DiscipleEntity entity, @Nullable LivingEntity target) {
		DiscipleData data = entity.data();
		ServerPlayer master = entity.owner();
		if (master == null || data.learnedIds().isEmpty() || data.deviating() || data.breakingThrough() || !entity.isAlive()) {
			return false;
		}
		boolean masterHurt = master.level() == entity.level() && master.distanceToSqr(entity) <= 64.0D
				&& master.getHealth() < master.getMaxHealth() * 0.5F;
		boolean selfHurt = entity.getHealth() < entity.getMaxHealth() * 0.4F;
		if ((masterHurt || selfHurt) && tryOne(entity, Technique.SPRING_OF_LIFE, target)) {
			return true;
		}
		if (target == null || !DiscipleTechniques.mayHit(entity, master, target)) {
			return false;
		}
		if (selfHurt && tryOne(entity, Technique.LIFE_DRAIN, target)) {
			return true;
		}
		if (entity.distanceToSqr(target) < 36.0D && tryOne(entity, Technique.IRON_BODY, target)) {
			return true;
		}
		if (selfHurt || DiscipleTechniques.hostilesAround(entity, master, entity.position(), 4.0D).size() >= 3) {
			if (tryOne(entity, Technique.GALE, target)) {
				return true;
			}
		}
		for (Technique technique : ATTACK_ORDER) {
			if (tryOne(entity, technique, target)) {
				return true;
			}
		}
		return false;
	}

	private static boolean tryOne(DiscipleEntity entity, Technique technique, @Nullable LivingEntity target) {
		return entity.data().knows(technique) && DiscipleTechniques.ready(entity, technique) && DiscipleTechniques.cast(entity, technique, target);
	}

	// ================================================================== pills

	private enum PillSort { VANILLA, ALCHEMY, MARROW }

	/** What a stack is as a pill for a disciple. */
	private record PillChoice(PillSort sort, @Nullable VanillaPills.Pill vanilla, @Nullable PillKind kind, PillQuality quality,
							  String key, int color, int strength) {
	}

	private static @Nullable PillChoice pillOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		VanillaPills.Pill pill = VanillaPills.Pill.of(stack);
		if (pill != null) {
			return new PillChoice(PillSort.VANILLA, pill, null, PillQuality.MID, pill.name().toLowerCase(Locale.ROOT), pill.color(), pill.strength());
		}
		if (stack.getItem() instanceof PillItem item) {
			PillKind kind = item.kind();
			PillQuality quality = AlchemyComponents.quality(stack);
			int strength = Math.min(3, kind.strength() + (quality == PillQuality.PERFECT ? 1 : 0));
			return new PillChoice(PillSort.ALCHEMY, null, kind, quality, kind.id(), kind.color(), strength);
		}
		if (stack.is(RootItems.MARROW_CLEANSING_PILL)) {
			return new PillChoice(PillSort.MARROW, null, null, PillQuality.MID, "marrow_cleansing_pill", 0x8FE3C8, 2);
		}
		return null;
	}

	/** Why the disciple cannot take {@code stack} now (a {@code why.*} key for the screen), or null when it can. */
	public static @Nullable String feedBlocker(DiscipleEntity entity, ItemStack stack) {
		if (stack.isEmpty()) {
			return PREFIX + "why.pill_none";
		}
		if (stack.getItem() instanceof SpiritBeastPillItem) {
			return PREFIX + "why.pill_beast";
		}
		PillChoice choice = pillOf(stack);
		if (choice == null) {
			return PREFIX + "why.pill_not_pill";
		}
		DiscipleData data = entity.data();
		if (data.pillCooldownLeft(choice.key()) > 0) {
			return PREFIX + "why.pill_cooldown";
		}
		if (choice.kind() == PillKind.SOUL_NURTURING && !data.realm().atLeast(Realm.NASCENT_SOUL)) {
			return PREFIX + "why.pill_too_weak";
		}
		if (choice.sort() == PillSort.MARROW && data.rootGrade() == RootGrade.HEAVENLY
				&& Element.fromMask(data.rootElements()).size() < 3) {
			return PREFIX + "why.pill_pure";
		}
		return null;
	}

	/**
	 * The master gives the disciple one pill of {@code stack}: vanilla pills, the alchemy pills and the Marrow Cleansing
	 * Pill work with 70% of the player's numbers (XP, qi, healing, deviation, breakthrough and tribulation bonuses), no
	 * toxicity, one pill of a kind per minute. One is consumed (not in creative). Anything else is refused with a message.
	 * True when the pill was taken.
	 */
	public static boolean feed(DiscipleEntity entity, ServerPlayer master, ItemStack stack) {
		String why = feedBlocker(entity, stack);
		PillChoice choice = pillOf(stack);
		if (why != null || choice == null || !(entity.level() instanceof ServerLevel level)) {
			refuseFeed(entity, master, stack, why == null ? PREFIX + "why.pill_not_pill" : why, choice);
			return false;
		}
		Component pillName = stack.getHoverName().copy();
		DiscipleData data = entity.data();
		List<Component> parts = new ArrayList<>();
		switch (choice.sort()) {
			case VANILLA -> applyVanilla(entity, data, choice.vanilla(), parts);
			case ALCHEMY -> applyAlchemy(entity, data, choice.kind(), choice.quality(), parts);
			case MARROW -> applyMarrow(entity, level, data, parts);
		}
		if (parts.isEmpty()) {
			parts.add(Component.translatable(PREFIX + "pill.part.nothing"));
		}
		data.setPillCooldown(choice.key(), PILL_COOLDOWN);
		consume(master, stack, choice);

		Vec3 pos = entity.position();
		// Common effects anchored on the disciple (the PILL effect would swirl around the nearest player: the master).
		int rgb = choice.strength() == 0 ? 0x5A6B3A : choice.color();
		Fx.burst(level, pos.add(0.0D, 1.0D, 0.0D), rgb, 10 + 8 * choice.strength(), 0.8F);
		Fx.ring(level, pos.add(0.0D, 0.1D, 0.0D), rgb, 1.0F + 0.2F * choice.strength());
		if (choice.strength() >= 2) {
			Fx.pillar(level, pos, rgb, 1.5F + choice.strength());
		}
		sound(level, pos.add(0.0D, 1.0D, 0.0D), SoundEvents.PLAYER_BURP, 0.4F, 1.4F);
		sound(level, pos.add(0.0D, 1.0D, 0.0D), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.0F + 0.15F * choice.strength());
		if (choice.strength() >= 2) {
			sound(level, pos.add(0.0D, 1.0D, 0.0D), SoundEvents.ENCHANTMENT_TABLE_USE, 0.7F, 1.3F);
		}
		if (choice.strength() >= 3) {
			sound(level, pos.add(0.0D, 1.0D, 0.0D), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.3F);
		}
		Msg.chat(master, Component.translatable(PREFIX + "pill.taken", name(entity), pillName, join(parts)).withColor(choice.color()));
		entity.refreshSynced();
		return true;
	}

	private static void refuseFeed(DiscipleEntity entity, ServerPlayer master, ItemStack stack, String why, @Nullable PillChoice choice) {
		Component name = name(entity);
		Component item = stack.isEmpty() ? Component.empty() : stack.getHoverName().copy();
		MutableComponent message = switch (why.substring(PREFIX.length())) {
			case "why.pill_none" -> Component.translatable(PREFIX + "pill.refuse.none", name);
			case "why.pill_beast" -> Component.translatable(PREFIX + "pill.refuse.beast", item);
			case "why.pill_cooldown" -> Component.translatable(PREFIX + "pill.refuse.cooldown", name, item,
					String.valueOf(choice == null ? 0 : (entity.data().pillCooldownLeft(choice.key()) + 19) / 20));
			case "why.pill_too_weak" -> Component.translatable(PREFIX + "pill.refuse.too_weak", name, item, Realm.NASCENT_SOUL.displayName());
			case "why.pill_pure" -> Component.translatable(PREFIX + "pill.refuse.pure", name);
			default -> Component.translatable(PREFIX + "pill.refuse.not_pill", item, name);
		};
		Msg.chat(master, message.withColor(REFUSE));
	}

	/** One pill used up (bottles come back for honey and dragon's breath). Creative keeps everything. */
	private static void consume(ServerPlayer master, ItemStack stack, PillChoice choice) {
		if (master.hasInfiniteMaterials()) {
			return;
		}
		boolean bottle = choice.vanilla() == VanillaPills.Pill.HONEY_BOTTLE || choice.vanilla() == VanillaPills.Pill.DRAGON_BREATH;
		if (!bottle) {
			stack.shrink(1);
			return;
		}
		ItemStack result = ItemUtils.createFilledResult(stack, master, new ItemStack(Items.GLASS_BOTTLE), false);
		if (result != stack) {
			for (InteractionHand hand : InteractionHand.values()) {
				if (master.getItemInHand(hand) == stack) {
					master.setItemInHand(hand, result);
					return;
				}
			}
			master.getInventory().placeItemBackInInventory(result, Prediction.SERVER_ONLY);
		}
	}

	private static void applyVanilla(DiscipleEntity entity, DiscipleData data, @Nullable VanillaPills.Pill pill, List<Component> parts) {
		if (pill == null) {
			return;
		}
		if (pill.xp() > 0) {
			xpPart(entity, pill.xp() * PILL_SHARE, parts);
		}
		if (pill.qi() == VanillaPills.FULL_QI) {
			qiPart(data, maxQi(data), parts);
		} else if (pill.qi() > 0) {
			qiPart(data, pill.qi() * PILL_SHARE, parts);
		}
		switch (pill) {
			case HONEY_BOTTLE -> {
				if (!shortenDeviation(entity, data, 0.2D * PILL_SHARE, false, parts)) {
					parts.add(Component.translatable(PREFIX + "pill.part.calm"));
				}
			}
			case ENCHANTED_GOLDEN_APPLE -> tribulationPart(data, 1.0D - 0.15D * PILL_SHARE, parts);
			case NETHER_STAR -> {
				double before = data.nextBreakthroughBonus();
				data.setNextBreakthroughBonus(before + 0.1D * PILL_SHARE);
				breakthroughPart(data, before, parts);
			}
			case ECHO_SHARD -> {
				if (data.dao() == Dao.SPACE) {
					double gain = comprehensionGain(data, Dao.SPACE, 1.0D * PILL_SHARE);
					data.addComprehension(gain);
					parts.add(Component.translatable(PREFIX + "pill.part.comprehension", decimal(gain), Dao.SPACE.displayName()));
				}
			}
			default -> {
			}
		}
	}

	private static void applyAlchemy(DiscipleEntity entity, DiscipleData data, @Nullable PillKind kind, PillQuality quality,
									 List<Component> parts) {
		if (kind == null) {
			return;
		}
		double m = quality.multiplier() * PILL_SHARE;
		double bar = data.xpRequired();
		switch (kind) {
			case QI_GATHERING -> xpPart(entity, Math.min(bar * PillKind.QI_GATHERING_XP, PillKind.QI_GATHERING_MAX_XP) * m, parts);
			case QI_RECOVERY -> qiPart(data, maxQi(data) * PillKind.QI_RECOVERY_QI * m, parts);
			case FOUNDATION -> {
				boolean best = data.realm() == Realm.QI_REFINING;
				double bonus = PillKind.FOUNDATION_BONUS * m * (best ? 1.0D : PillKind.FOUNDATION_OFF_REALM);
				double before = data.nextBreakthroughBonus();
				data.setNextBreakthroughBonus(best ? before + bonus
						: Math.max(before, Math.min(before + bonus, PillKind.FOUNDATION_OFF_REALM_MAX)));
				breakthroughPart(data, before, parts);
			}
			case GOLDEN_CORE -> tribulationPart(data, 1.0D - PillKind.GOLDEN_CORE_REDUCTION * m, parts);
			case TRIBULATION_WARDING -> tribulationPart(data, 1.0D - PillKind.WARDING_REDUCTION * m, parts);
			case HEART_CALMING -> {
				if (!shortenDeviation(entity, data, PillKind.HEART_DEVIATION * m, false, parts)) {
					parts.add(Component.translatable(PREFIX + "pill.part.calm"));
				}
			}
			case HEALING -> {
				float max = entity.getMaxHealth();
				float before = entity.getHealth();
				entity.heal((float) Math.min(max, max * PillKind.HEALING_HEALTH * m));
				float healed = entity.getHealth() - before;
				parts.add(healed >= 0.5F
						? Component.translatable(PREFIX + "pill.part.heal", Msg.number(healed + 1.0E-4D))
						: Component.translatable(PREFIX + "pill.part.health_full"));
				shortenDeviation(entity, data, PillKind.HEALING_DEVIATION * m, quality == PillQuality.PERFECT, parts);
			}
			case SOUL_NURTURING -> xpPart(entity, Math.min(bar * PillKind.SOUL_NURTURING_XP, PillKind.SOUL_NURTURING_MAX_XP) * m, parts);
			case IMMORTAL -> {
				xpPart(entity, bar * PillKind.IMMORTAL_XP * m, parts);
				qiPart(data, maxQi(data), parts);
			}
		}
	}

	/** Marrow cleansing: the root grade rises one step (Heavenly at most); with 3+ elements one may be washed out (25%). */
	private static void applyMarrow(DiscipleEntity entity, ServerLevel level, DiscipleData data, List<Component> parts) {
		RootGrade before = data.rootGrade();
		if (before != RootGrade.HEAVENLY) {
			data.setRootGrade(before.up());
			parts.add(Component.translatable(PREFIX + "pill.part.root", data.rootGrade().displayName().copy().withColor(data.rootGrade().color())));
		}
		List<Element> elements = Element.fromMask(data.rootElements());
		if (elements.size() >= 3 && entity.getRandom().nextFloat() < 0.25F) {
			Element lost = elements.get(entity.getRandom().nextInt(elements.size()));
			data.setRootElements(data.rootElements() & ~lost.bit());
			parts.add(Component.translatable(PREFIX + "pill.part.root_element", lost.displayName().copy().withColor(lost.color())));
		}
		// Anchored on the disciple (MARROW_CLEANSE would follow the nearest player: the master who gave the pill).
		int c = data.rootGrade().color();
		Fx.burst(level, entity.position().add(0.0D, 1.1D, 0.0D), c, 30, 1.4F);
		Fx.ring(level, entity.position().add(0.0D, 0.05D, 0.0D), c, 1.6F);
		Fx.pillar(level, entity.position(), 0xFFFFFF, 3.0F);
		sound(level, entity.position(), SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.2F);
	}

	private static void xpPart(DiscipleEntity entity, double amount, List<Component> parts) {
		double gained = addXp(entity, amount, XpSource.PILL);
		if (gained > 0.0D) {
			parts.add(Component.translatable(PREFIX + "pill.part.xp", Msg.number(Math.max(1.0D, gained))));
		}
	}

	private static void qiPart(DiscipleData data, double amount, List<Component> parts) {
		double max = maxQi(data);
		double before = data.qi();
		data.setQi(Math.min(max, before + Math.max(0.0D, amount)));
		double gained = data.qi() - before;
		parts.add(gained >= 0.5D
				? Component.translatable(PREFIX + "pill.part.qi", Msg.number(gained + 1.0E-6D))
				: Component.translatable(PREFIX + "pill.part.qi_full"));
	}

	private static void breakthroughPart(DiscipleData data, double before, List<Component> parts) {
		double added = data.nextBreakthroughBonus() - before;
		parts.add(added > 0.0005D
				? Component.translatable(PREFIX + "pill.part.breakthrough", percent(added), percent(data.nextBreakthroughBonus()))
				: Component.translatable(PREFIX + "pill.part.breakthrough_max"));
	}

	private static void tribulationPart(DiscipleData data, double factor, List<Component> parts) {
		double before = data.nextTribulationFactor();
		data.setNextTribulationFactor(before * factor);
		double after = data.nextTribulationFactor();
		parts.add(after < before - 0.0005D
				? Component.translatable(PREFIX + "pill.part.tribulation", percent(after))
				: Component.translatable(PREFIX + "pill.part.tribulation_max"));
	}

	/** Cuts {@code share} of the remaining Qi Deviation ({@code cure}: all of it); true when there was one. */
	private static boolean shortenDeviation(DiscipleEntity entity, DiscipleData data, double share, boolean cure, List<Component> parts) {
		if (!data.deviating()) {
			return false;
		}
		int left = cure ? 0 : (int) Math.round(data.deviationTicks() * Math.max(0.0D, 1.0D - share));
		data.setDeviationTicks(left);
		if (left <= 0) {
			entity.removeEffect(MobEffects.WEAKNESS);
			parts.add(Component.translatable(PREFIX + "pill.part.deviation_cured"));
		} else {
			parts.add(Component.translatable(PREFIX + "pill.part.deviation", Msg.number(left / 20.0D)));
		}
		return true;
	}

	// ================================================================== teaching

	/** Qi the master pays to teach: 20% of their max qi. */
	public static double teachCost(ServerPlayer master) {
		return Qi.max(Cultivators.get(master)) * TEACH_COST;
	}

	/** Why {@code technique} cannot be taught now (a {@code why.*} key), or null when it can. */
	public static @Nullable String teachBlocker(DiscipleEntity entity, ServerPlayer master, Technique technique) {
		DiscipleData data = entity.data();
		if (!DiscipleTechniques.teachable(technique)) {
			return PREFIX + "why.not_teachable";
		}
		if (!Cultivators.get(master).knows(technique)) {
			return PREFIX + "why.master_unknown";
		}
		if (data.knows(technique)) {
			return PREFIX + "why.known";
		}
		if (!data.realm().atLeast(technique.minRealm())) {
			return PREFIX + "why.realm";
		}
		Dao dao = technique.dao();
		if (dao != null && data.dao() == null) {
			return PREFIX + "why.no_dao";
		}
		if (dao != null && dao != data.dao()) {
			return PREFIX + "why.other_dao";
		}
		if (data.breakingThrough()) {
			return PREFIX + "why.busy";
		}
		if (Cultivators.get(master).qi() + 1.0E-6D < teachCost(master)) {
			return PREFIX + "why.qi";
		}
		return null;
	}

	/**
	 * The master passes {@code technique} on: they must know it, it must be teachable, the disciple's realm (and Dao for
	 * a Dao technique) must allow it and it must be new. Costs the master 20% of their max qi. True when it was learned.
	 */
	public static boolean teach(DiscipleEntity entity, ServerPlayer master, Technique technique) {
		String why = teachBlocker(entity, master, technique);
		double cost = teachCost(master);
		if (why == null && !Qi.trySpend(master, cost)) {
			why = PREFIX + "why.qi";
		}
		Component name = name(entity);
		MutableComponent techniqueName = technique.displayName().copy().withColor(GOLD);
		if (why != null) {
			DiscipleData data = entity.data();
			Dao dao = technique.dao();
			MutableComponent message = switch (why.substring(PREFIX.length())) {
				case "why.not_teachable" -> Component.translatable(PREFIX + "teach.refuse.not_teachable", techniqueName);
				case "why.master_unknown" -> Component.translatable(PREFIX + "teach.refuse.master_unknown", techniqueName);
				case "why.known" -> Component.translatable(PREFIX + "teach.refuse.known", name, techniqueName);
				case "why.realm" -> Component.translatable(PREFIX + "teach.refuse.realm", techniqueName,
						technique.minRealm().displayName().copy().withColor(technique.minRealm().color()), name);
				case "why.no_dao" -> Component.translatable(PREFIX + "teach.refuse.no_dao", techniqueName, daoName(dao), name);
				case "why.other_dao" -> Component.translatable(PREFIX + "teach.refuse.other_dao", techniqueName, daoName(dao), name,
						daoName(data.dao()));
				case "why.busy" -> Component.translatable(PREFIX + "teach.refuse.busy", name);
				default -> Component.translatable(PREFIX + "teach.refuse.qi", Msg.number(Math.ceil(cost)),
						Msg.number(Cultivators.get(master).qi()));
			};
			Msg.chat(master, message.withColor(REFUSE));
			return false;
		}
		DiscipleData data = entity.data();
		data.learn(technique);
		Cultivators.dirty(master);
		if (entity.level() instanceof ServerLevel level) {
			int color = technique.dao() == null ? TechniqueBooks.UNIVERSAL_COLOR : technique.dao().color();
			Vec3 from = master.position().add(0.0D, 1.2D, 0.0D);
			Vec3 to = entity.position().add(0.0D, 1.2D, 0.0D);
			if (master.level() == level) {
				Fx.beam(level, from, to, color, 0.12F);
			}
			Fx.send(level, entity.position(), FxType.DAO_CHOSEN, technique.dao() == null ? -1 : technique.dao().ordinal(),
					TechniqueBooks.FX_LEARNED, 0.0F);
			sound(level, entity.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.1F);
			sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 1.4F);
		}
		Msg.chat(master, Component.translatable(PREFIX + "teach.success", techniqueName, name, Msg.number(Math.ceil(cost)),
				technique.description().copy().withColor(MUTED)).withColor(JADE));
		Advancements.grant(master, "disciple_taught");
		return true;
	}

	// ================================================================== breakthrough

	/** Why the breakthrough cannot start now (a {@code why.*} key), or null when it can. */
	public static @Nullable String breakthroughBlocker(DiscipleEntity entity, @Nullable ServerPlayer master) {
		DiscipleData data = entity.data();
		Rank rank = data.rank();
		if (data.breakingThrough()) {
			return PREFIX + "why.bt_busy";
		}
		if (rank.isMax()) {
			return PREFIX + "why.bt_max";
		}
		if (!rank.isBottleneck()) {
			return PREFIX + "why.bt_not_peak";
		}
		if (!data.barFull()) {
			return PREFIX + "why.bt_not_full";
		}
		if (data.deviating()) {
			return PREFIX + "why.bt_deviation";
		}
		if (master == null) {
			return PREFIX + "why.bt_offline";
		}
		if (master.level() != entity.level() || master.distanceToSqr(entity) > BREAKTHROUGH_RANGE * BREAKTHROUGH_RANGE) {
			return PREFIX + "why.bt_master_far";
		}
		if (DiscipleBreakthrough.kindFor(rank) == DiscipleBreakthrough.Kind.TRIBULATION
				&& entity.getHealth() < entity.getMaxHealth() * TRIBULATION_MIN_HEALTH) {
			return PREFIX + "why.bt_injured";
		}
		return null;
	}

	/**
	 * Starts the disciple's breakthrough: the bar must be full at a bottleneck, no Qi Deviation, the master within 16
	 * blocks (and at least half health for a tribulation). True when it began; otherwise the master is told why.
	 */
	public static boolean startBreakthrough(DiscipleEntity entity, ServerPlayer master) {
		String why = breakthroughBlocker(entity, master);
		if (why != null || !(entity.level() instanceof ServerLevel level)) {
			DiscipleData data = entity.data();
			Rank rank = data.rank();
			Component name = name(entity);
			String reason = why == null ? PREFIX + "why.bt_busy" : why;
			MutableComponent message = switch (reason.substring(PREFIX.length())) {
				case "why.bt_max" -> Component.translatable(PREFIX + "bt.refuse.max", name);
				case "why.bt_not_peak" -> {
					Rank peak = new Rank(rank.realm(), rank.layer(), rank.realm().stages() - 1);
					yield Component.translatable(PREFIX + "bt.refuse.not_peak", name, peak.displayName().withColor(peak.realm().color()));
				}
				case "why.bt_not_full" -> Component.translatable(PREFIX + "bt.refuse.not_full", name, Msg.number(data.xp()),
						Msg.number(data.xpRequired()));
				case "why.bt_deviation" -> Component.translatable(PREFIX + "bt.refuse.deviation", name,
						String.valueOf((data.deviationTicks() + 19) / 20));
				case "why.bt_offline", "why.bt_master_far" -> Component.translatable(PREFIX + "bt.refuse.master_far",
						String.valueOf((int) BREAKTHROUGH_RANGE), name);
				case "why.bt_injured" -> Component.translatable(PREFIX + "bt.refuse.injured", name);
				default -> Component.translatable(PREFIX + "bt.refuse.busy", name);
			};
			Msg.chat(master, message.withColor(REFUSE));
			return false;
		}
		DiscipleBreakthrough.start(entity, level, master);
		return true;
	}

	// ================================================================== Dao

	/** Why the disciple cannot choose a Dao now (a {@code why.*} key), or null when it can. */
	public static @Nullable String daoBlocker(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (data.dao() != null) {
			return PREFIX + "why.dao_chosen";
		}
		if (!data.realm().atLeast(Realm.GOLDEN_CORE)) {
			return PREFIX + "why.dao_realm";
		}
		if (data.breakingThrough()) {
			return PREFIX + "why.dao_busy";
		}
		return null;
	}

	/** The master chooses the disciple's Dao (Golden Core or higher, none chosen yet). True when it was set. */
	public static boolean chooseDao(DiscipleEntity entity, ServerPlayer master, Dao dao) {
		String why = daoBlocker(entity);
		Component name = name(entity);
		if (why != null) {
			MutableComponent message = switch (why.substring(PREFIX.length())) {
				case "why.dao_chosen" -> Component.translatable(PREFIX + "dao.refuse.chosen", name, daoName(entity.data().dao()));
				case "why.dao_busy" -> Component.translatable(PREFIX + "dao.refuse.busy", name);
				default -> Component.translatable(PREFIX + "dao.refuse.realm", name, Realm.GOLDEN_CORE.displayName());
			};
			Msg.chat(master, message.withColor(REFUSE));
			return false;
		}
		DiscipleData data = entity.data();
		data.setDao(dao);
		// The first insight, as for a cultivator choosing a Dao (DaoPaths).
		data.setComprehension(comprehensionGain(data, dao, FIRST_INSIGHT));
		entity.refreshSynced();
		if (entity.level() instanceof ServerLevel level) {
			Vec3 pos = entity.position();
			Fx.pillar(level, pos, dao.color(), 6.0F);
			Fx.ring(level, pos.add(0.0D, 0.1D, 0.0D), dao.color(), 2.5F);
			Fx.burst(level, pos.add(0.0D, 1.2D, 0.0D), dao.color(), 30, 1.4F);
			sound(level, pos, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.3F);
			sound(level, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.9F);
		}
		Msg.chat(master, Component.translatable(PREFIX + "dao.success", name, daoName(dao)).withColor(dao.color()));
		if (dao.hasAffinity(data.rootElements())) {
			Msg.chat(master, Component.translatable(PREFIX + "dao.affinity", name, daoName(dao)).withColor(MUTED));
		}
		return true;
	}

	// ================================================================== screen details

	/**
	 * Everything the disciple screen shows: {@link DiscipleData#save()} plus live values. Keys added on top of the saved
	 * ones: {@code max_qi, qi_regen, xp_required, bar_fraction, step, bottleneck, max_rank, mode, meditating,
	 * breaking_through, deviating, deviation_seconds, xp_multiplier, meditation_rate, power, health, max_health,
	 * max_health_bonus, attack_bonus, speed_bonus, quality_factor, comprehension_level, can_breakthrough,
	 * breakthrough_reason (= reason_key, "" when allowed), breakthrough_kind (awakening | chance | tribulation | none),
	 * breakthrough_kind_key, breakthrough_chance, breakthrough_strikes, breakthrough_strike_share, next_realm,
	 * next_layer, next_stage, can_choose_dao, dao_reason, affinity_mask (bit per Dao ordinal), owner_name, teach_cost, master_qi,
	 * master_max_qi, teachable (list of {id, ok, reason_key, known, min_realm, dao, qi_cost, cooldown} for every
	 * technique the master knows), techniques (list of {id, ready, cooldown_left, qi_cost} for the disciple's own),
	 * pills_ready, pill_reason, pill_item, pill_cooldown, pill_cooldown_max}. Reason keys are translation keys.
	 */
	public static CompoundTag infoFor(DiscipleEntity entity, ServerPlayer master) {
		DiscipleData data = entity.data();
		CompoundTag tag = data.save();
		Rank rank = data.rank();
		boolean meditating = entity.isMeditatingNow();
		tag.putDouble("max_qi", maxQi(data));
		tag.putDouble("qi_regen", regenPerSecond(data, meditating));
		tag.putDouble("xp_required", rank.xpRequired());
		tag.putDouble("bar_fraction", data.barFraction());
		tag.putInt("step", rank.step());
		tag.putBoolean("bottleneck", rank.isBottleneck());
		tag.putBoolean("max_rank", rank.isMax());
		tag.putString("mode", entity.mode().name().toLowerCase(Locale.ROOT));
		tag.putBoolean("meditating", meditating);
		tag.putBoolean("breaking_through", data.breakingThrough());
		tag.putBoolean("deviating", data.deviating());
		tag.putInt("deviation_seconds", (data.deviationTicks() + 19) / 20);
		tag.putDouble("xp_multiplier", xpMultiplier(data, XpSource.MEDITATION));
		tag.putDouble("meditation_rate", meditationBase(entity, data) * xpMultiplier(data, XpSource.MEDITATION));
		tag.putDouble("power", DiscipleTechniques.power(data));
		tag.putFloat("health", entity.getHealth());
		tag.putFloat("max_health", entity.getMaxHealth());
		tag.putDouble("max_health_bonus", maxHealthBonus(data));
		tag.putDouble("attack_bonus", attackBonus(data));
		tag.putDouble("speed_bonus", speedBonus(data));
		tag.putDouble("quality_factor", data.qualityFactor());
		tag.putInt("comprehension_level", Dao.comprehensionLevel(data.comprehension()));

		String why = breakthroughBlocker(entity, master);
		tag.putBoolean("can_breakthrough", why == null);
		tag.putString("breakthrough_reason", why == null ? "" : why);
		tag.putString("reason_key", why == null ? "" : why);
		if (rank.isMax()) {
			tag.putString("breakthrough_kind", "none");
			tag.putString("breakthrough_kind_key", PREFIX + "kind.none");
		} else {
			// The breakthrough waiting at the top of this realm (or Immortal Ascension layer).
			Rank peak = rank.isBottleneck() ? rank : new Rank(rank.realm(), rank.layer(), rank.realm().stages() - 1);
			DiscipleBreakthrough.Kind kind = DiscipleBreakthrough.kindFor(peak);
			String kindId = switch (kind) {
				case AWAKENING -> "awakening";
				case GATHERING -> "chance";
				case TRIBULATION -> "tribulation";
			};
			Rank next = peak.next();
			tag.putString("breakthrough_kind", kindId);
			tag.putString("breakthrough_kind_key", PREFIX + "kind." + kindId);
			tag.putDouble("breakthrough_chance", kind == DiscipleBreakthrough.Kind.GATHERING ? DiscipleBreakthrough.chance(data) : 1.0D);
			tag.putInt("breakthrough_strikes", kind == DiscipleBreakthrough.Kind.TRIBULATION ? DiscipleBreakthrough.strikeCount(next) : 0);
			tag.putDouble("breakthrough_strike_share", DiscipleBreakthrough.strikeShare(data));
			tag.putInt("next_realm", next.realm().ordinal());
			tag.putInt("next_layer", next.layer());
			tag.putInt("next_stage", next.stage());
		}

		String daoWhy = daoBlocker(entity);
		tag.putBoolean("can_choose_dao", daoWhy == null);
		tag.putString("dao_reason", daoWhy == null ? "" : daoWhy);
		int affinity = 0;
		for (Dao each : Dao.values()) {
			if (each.hasAffinity(data.rootElements())) {
				affinity |= 1 << each.ordinal();
			}
		}
		tag.putInt("affinity_mask", affinity);

		CultivationData masterData = Cultivators.get(master);
		tag.putString("owner_name", master.getName().getString());
		tag.putDouble("teach_cost", teachCost(master));
		tag.putDouble("master_qi", masterData.qi());
		tag.putDouble("master_max_qi", Qi.max(masterData));
		ListTag teachable = new ListTag();
		for (Technique technique : masterData.learnedTechniques()) {
			CompoundTag entry = new CompoundTag();
			String teachWhy = teachBlocker(entity, master, technique);
			entry.putString("id", technique.id());
			entry.putBoolean("ok", teachWhy == null);
			entry.putString("reason_key", teachWhy == null ? "" : teachWhy);
			entry.putBoolean("known", data.knows(technique));
			entry.putInt("min_realm", technique.minRealm().ordinal());
			entry.putInt("dao", technique.dao() == null ? -1 : technique.dao().ordinal());
			entry.putDouble("qi_cost", DiscipleTechniques.qiCost(technique));
			entry.putInt("cooldown", technique.cooldownTicks());
			teachable.add(entry);
		}
		tag.put("teachable", teachable);
		ListTag own = new ListTag();
		for (Technique technique : data.learnedTechniques()) {
			CompoundTag entry = new CompoundTag();
			entry.putString("id", technique.id());
			entry.putBoolean("ready", DiscipleTechniques.ready(entity, technique));
			entry.putInt("cooldown_left", data.cooldownLeft(technique));
			entry.putDouble("qi_cost", DiscipleTechniques.qiCost(technique));
			own.add(entry);
		}
		tag.put("techniques", own);

		ItemStack hand = master.getMainHandItem();
		String pillWhy = feedBlocker(entity, hand);
		PillChoice choice = pillOf(hand);
		tag.putBoolean("pills_ready", pillWhy == null);
		tag.putString("pill_reason", pillWhy == null ? "" : pillWhy);
		tag.putString("pill_item", hand.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(hand.getItem()).toString());
		tag.putInt("pill_cooldown", choice == null ? 0 : data.pillCooldownLeft(choice.key()));
		tag.putInt("pill_cooldown_max", PILL_COOLDOWN);
		return tag;
	}

	// ================================================================== stats

	/** Extra max health: {@code (realm x 6 + stage x 1.5 + layer x 3) x breakthrough quality factor}. */
	public static double maxHealthBonus(DiscipleData data) {
		Rank rank = data.rank();
		return (rank.realm().ordinal() * 6.0D + rank.stage() * 1.5D + rank.layer() * 3.0D) * data.qualityFactor();
	}

	/** Extra attack damage: {@code realm x 1.0 + stage x 0.25 + layer x 0.5}. */
	public static double attackBonus(DiscipleData data) {
		Rank rank = data.rank();
		return rank.realm().ordinal() * 1.0D + rank.stage() * 0.25D + rank.layer() * 0.5D;
	}

	/** Movement speed, multiplied base: {@code min(0.2, realm x 0.02)}. */
	public static double speedBonus(DiscipleData data) {
		return Math.min(0.2D, data.realm().ordinal() * 0.02D);
	}

	// ================================================================== helpers

	private static Component name(DiscipleEntity entity) {
		return entity.getName().copy().withColor(0xF2E6C8);
	}

	private static Component daoName(@Nullable Dao dao) {
		if (dao == null) {
			return Component.translatable(PREFIX + "dao.none").withColor(MUTED);
		}
		return dao.displayName().copy().withColor(dao.color());
	}

	private static MutableComponent join(List<Component> parts) {
		MutableComponent line = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				line.append(Component.translatable(PREFIX + "separator"));
			}
			line.append(parts.get(i));
		}
		return line;
	}

	private static String percent(double fraction) {
		return Math.round(fraction * 100.0D) + "%";
	}

	private static String decimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, pitch);
	}

	private DiscipleCultivation() {
	}
}
