package dev.kultivator.disciple.power;

import dev.kultivator.alchemy.PillItem;
import dev.kultivator.alchemy.PillKind;
import dev.kultivator.cultivation.VanillaPills;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.RootGrade;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.item.RootItems;
import dev.kultivator.sect.hall.Talents;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Soul colour and talents in effect (DESIGN 20.3.5; rolled by module A, applied here).
 * <ul>
 * <li>Warna Alam Jiwa (TDG ch. 13-14): the colour's XP factor replaces the root multiplier; Merah cannot break out of
 * Golden Core Peak and keeps only half of what overflows the full bar; a Marrow Cleansing Pill or "Keberuntungan" (1% per
 * minute meditating beside the meditating master) lifts the colour one step.</li>
 * <li>Hati Gigih (Han Li): meditation and duty XP x1.3, Foundation chance +0.08, strikes x0.9.</li>
 * <li>Darah Dewa Kuno (Tu Si): max-health bonus x1.5, melee x1.3.</li>
 * <li>Bakat Pil (Meng Hao): fed pills x1.3, pill cooldown -30%.</li>
 * <li>Takut Mati (Bai Xiaochun): retreats at 40% health and heals twice as fast while that low.</li>
 * </ul>
 * Jari Penempa Roh is read by {@link NatalTreasure}, Indra Pedang lives in {@link SwordSense}.
 */
final class TalentEffects {

	public static final double PERSEVERING_XP = 1.3D;
	public static final double PERSEVERING_FOUNDATION = 0.08D;
	public static final double PERSEVERING_STRIKES = 0.9D;
	public static final double ANCIENT_GOD_HEALTH = 0.5D;
	public static final double ANCIENT_GOD_MELEE = 1.3D;
	public static final double PILL_GIFT_XP = 1.3D;
	public static final double PILL_GIFT_COOLDOWN = 0.7D;
	public static final double FEAR_RETREAT = 0.4D;
	public static final double FEAR_REGEN = 2.0D;
	/** Merah keeps this share of the XP that overflows the full bar at Golden Core Peak. */
	public static final double RED_OVERFLOW = 0.5D;
	/** "Keberuntungan": chance per minute of shared meditation that the colour rises. */
	public static final double FORTUNE_CHANCE = 0.01D;
	public static final double FORTUNE_RANGE = 8.0D;

	private static final Identifier ANCIENT_GOD_HEALTH_ID = DisciplePowers.id("ancient_god_health");

	static void register() {
		DiscipleHooks.addPotentialFactor(TalentEffects::potential);
		DiscipleHooks.addXpFactor(TalentEffects::xpFactor);
		DiscipleHooks.addBreakthroughBlocker(TalentEffects::redCap);
		DiscipleHooks.addStrikeFactor((entity, target, index) -> {
			DiscipleData data = entity.data();
			double factor = PowerCore.talent(data, PowerIds.TALENT_PERSEVERING) ? PERSEVERING_STRIKES : 1.0D;
			return factor * SpecialCandidates.strikeFactor(data);
		});
		DiscipleHooks.addFoundationChance(entity -> PowerCore.talent(entity.data(), PowerIds.TALENT_PERSEVERING) ? PERSEVERING_FOUNDATION : 0.0D);
		DiscipleHooks.addRetreatFraction(entity -> PowerCore.powered(entity) && PowerCore.talent(entity.data(), PowerIds.TALENT_FEAR_OF_DEATH)
				? FEAR_RETREAT : 0.0D);
		DiscipleHooks.addRegenFactor(entity -> PowerCore.powered(entity) && PowerCore.talent(entity.data(), PowerIds.TALENT_FEAR_OF_DEATH)
				&& entity.getHealth() < entity.getMaxHealth() * FEAR_RETREAT ? FEAR_REGEN : 1.0D);
		DiscipleHooks.addStats((entity, sink) -> {
			if (PowerCore.powered(entity) && PowerCore.talent(entity.data(), PowerIds.TALENT_ANCIENT_GOD)) {
				sink.add(Attributes.MAX_HEALTH, ANCIENT_GOD_HEALTH_ID,
						DiscipleCultivation.maxHealthBonus(entity.data()) * ANCIENT_GOD_HEALTH, AttributeModifier.Operation.ADD_VALUE);
			}
		});
		DiscipleHooks.addDamageFactor((entity, target, technique) -> !technique && PowerCore.powered(entity)
				&& PowerCore.talent(entity.data(), PowerIds.TALENT_ANCIENT_GOD) ? ANCIENT_GOD_MELEE : 1.0D);
		DiscipleHooks.addOnFed(TalentEffects::onFed);
	}

	// ================================================================== soul colour

	/** The colour's factor (the revealed Heretic God Veins: 2.0). */
	private static Double potential(DiscipleData data) {
		if (HereticGates.revealed(data)) {
			return HereticGates.SPECIAL_FACTOR;
		}
		return PowerIds.COLOUR_FACTOR[PowerCore.colour(data)];
	}

	/** True for a Merah disciple whose colour holds it at Golden Core Peak (the Heretic God Veins are exempt). */
	static boolean redCapped(DiscipleData data) {
		if (PowerCore.colour(data) != PowerIds.COLOUR_RED || PowerCore.physique(data) == PowerIds.PHYSIQUE_HERETIC) {
			return false;
		}
		Rank rank = data.rank();
		return rank.realm() == Realm.GOLDEN_CORE && rank.isBottleneck();
	}

	private static @Nullable String redCap(DiscipleEntity entity) {
		return redCapped(entity.data()) ? DisciplePowers.PREFIX + "why.red_cap" : null;
	}

	/** The base colour of a root grade (a new grade from good fortune takes it, DESIGN 20.3.5). */
	static int baseColour(RootGrade grade) {
		return switch (grade) {
			case TRASH -> PowerIds.COLOUR_RED;
			case LOW -> PowerIds.COLOUR_ORANGE;
			case MID -> PowerIds.COLOUR_YELLOW;
			case HIGH -> PowerIds.COLOUR_CYAN;
			case HEAVENLY -> PowerIds.COLOUR_INDIGO;
		};
	}

	/** Raises the colour to {@code colour} (never lowers it) with its effects; true when it rose. */
	static boolean liftColour(DiscipleEntity entity, int colour) {
		DiscipleData data = entity.data();
		int old = PowerCore.colour(data);
		int target = Math.max(0, Math.min(PowerIds.COLOUR_INDIGO, colour));
		if (target <= old) {
			return false;
		}
		Talents.setSoulColor(data, target);
		if (entity.level() instanceof ServerLevel level) {
			PowerCore.fx(level, entity.position().add(0.0D, 1.0D, 0.0D), PowerIds.FX_COLOUR_UP, target, 0.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.5F);
			PowerCore.sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.6F);
		}
		PowerCore.tell(entity, PowerCore.key("colour.up", PowerCore.name(entity),
				colourName(target)).withColor(PowerIds.COLOUR_RGB[target]));
		DisciplePowers.syncPower(entity);
		return true;
	}

	static net.minecraft.network.chat.MutableComponent colourName(int colour) {
		int index = Math.max(0, Math.min(6, colour));
		return PowerCore.key("colour." + PowerIds.COLOUR_IDS[index]).copy().withColor(PowerIds.COLOUR_RGB[index]);
	}

	/** Once a second: "Keberuntungan" while meditating within 8 blocks of the meditating master. */
	static void tick(DiscipleEntity entity, DisciplePowers.Live live) {
		DiscipleData data = entity.data();
		ServerPlayer master = entity.owner();
		if (master == null || !entity.isMeditatingNow() || master.level() != entity.level()
				|| master.distanceToSqr(entity) > FORTUNE_RANGE * FORTUNE_RANGE || PowerCore.colour(data) >= PowerIds.COLOUR_INDIGO
				|| PowerCore.physique(data) == PowerIds.PHYSIQUE_HERETIC || SpecialCandidates.hiddenBeggar(data)) {
			return;
		}
		CultivationData masterData = Cultivators.get(master);
		if (!masterData.meditating()) {
			return;
		}
		if (++live.fortuneSeconds >= 60) {
			live.fortuneSeconds = 0;
			if (entity.getRandom().nextDouble() < FORTUNE_CHANCE && liftColour(entity, PowerCore.colour(data) + 1)) {
				PowerCore.tell(entity, PowerCore.key("colour.fortune", PowerCore.name(entity)).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
	}

	// ================================================================== XP

	/**
	 * Every XP factor of module C: talents (Hati Gigih, Bakat Pil), physiques (Taixu pills, Devouring and Supreme Bone
	 * kills, Attributeless), special candidates (Regressor, Blocked Dantian) and the Merah overflow.
	 */
	private static double xpFactor(@Nullable DiscipleEntity entity, DiscipleData data, XpSource source) {
		double factor = 1.0D;
		boolean persevering = PowerCore.talent(data, PowerIds.TALENT_PERSEVERING);
		if (persevering && (source == XpSource.MEDITATION || source == XpSource.SECT)) {
			factor *= PERSEVERING_XP;
		}
		if (source == XpSource.PILL && PowerCore.talent(data, PowerIds.TALENT_PILL_GIFT)) {
			factor *= PILL_GIFT_XP;
		}
		factor *= Physiques.xpFactor(data, source);
		factor *= SpecialCandidates.xpFactor(data, source);
		if (redCapped(data) && data.barFull()) {
			factor *= RED_OVERFLOW;
		}
		return factor;
	}

	// ================================================================== pills

	private static void onFed(DiscipleEntity entity, ServerPlayer master, ItemStack stack) {
		DiscipleData data = entity.data();
		if (stack.is(RootItems.MARROW_CLEANSING_PILL) && PowerCore.physique(data) != PowerIds.PHYSIQUE_HERETIC) {
			// Good fortune: the new grade takes its base colour (DESIGN 20.3.5).
			liftColour(entity, baseColour(data.rootGrade()));
		}
		if (stack.getItem() instanceof PillItem pill && pill.kind() == PillKind.HEALING) {
			Physiques.resetToxin(entity);
		}
		double cooldown = 1.0D;
		if (PowerCore.talent(data, PowerIds.TALENT_PILL_GIFT)) {
			cooldown *= PILL_GIFT_COOLDOWN;
		}
		cooldown *= Physiques.pillCooldownFactor(data);
		if (cooldown < 1.0D) {
			String key = pillKey(stack);
			if (key != null) {
				data.setPillCooldown(key, (int) Math.round(data.pillCooldownLeft(key) * cooldown));
			}
		}
	}

	/** The phase-5 pill cooldown key of a stack (the same keys as DiscipleCultivation), or null. */
	private static @Nullable String pillKey(ItemStack stack) {
		VanillaPills.Pill pill = VanillaPills.Pill.of(stack);
		if (pill != null) {
			return pill.name().toLowerCase(Locale.ROOT);
		}
		if (stack.getItem() instanceof PillItem item) {
			return item.kind().id();
		}
		return stack.is(RootItems.MARROW_CLEANSING_PILL) ? "marrow_cleansing_pill" : null;
	}

	private TalentEffects() {
	}
}
