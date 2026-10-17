package dev.kultivator.disciple.path;

import dev.kultivator.Kultivator;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.PillItem;
import dev.kultivator.alchemy.PillKind;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fondasi Dao, the Foundation type rolled when a disciple reaches Foundation Establishment (DESIGN 20.3.18). Sumber:
 * <i>A Will Eternal</i> (Foundation is Mortal-Dao with a pill, Earth-Dao or Heaven-Dao only by chance) and <i>I Shall
 * Seal the Heavens</i>, ch. 76 and 100 (Foundation pillars graded by their cracks; the crackless Perfect Foundation with
 * ten pillars, extraordinarily rare). The 0.01% is a game value.
 * <ul>
 *   <li>Fana (60%): 9 fractured pillars, nothing more.</li>
 *   <li>Bumi (32%): 9 cracked pillars, XP x1.1, max health +10%.</li>
 *   <li>Langit (8%): 9 flawless pillars, XP x1.25, later strikes x0.85, a golden pillar in the aura.</li>
 *   <li>Sempurna (0.01%): 10 pillars, XP x1.4, the first calamity at star 8.</li>
 * </ul>
 * The master meditating within 8 during the gathering moves +10% to Bumi and +5% to Langit; the master's own Perfect or
 * Heavenly Foundation +3% Langit; a Perfect Foundation pill fed beforehand +5% Langit; all three make Sempurna 2%.
 */
public final class FoundationTypes {

	public static final int NONE = 0;
	public static final int FANA = 1;
	public static final int BUMI = 2;
	public static final int LANGIT = 3;
	public static final int SEMPURNA = 4;

	/** Base chances in percent (Fana takes the rest). */
	public static final double BUMI_CHANCE = 32.0D;
	public static final double LANGIT_CHANCE = 8.0D;
	public static final double SEMPURNA_CHANCE = 0.01D;
	public static final double MASTER_BUMI = 10.0D;
	public static final double MASTER_LANGIT = 5.0D;
	public static final double MASTER_QUALITY_LANGIT = 3.0D;
	public static final double PILL_LANGIT = 5.0D;
	public static final double ALL_THREE_SEMPURNA = 2.0D;

	public static final double BUMI_XP = 1.1D;
	public static final double LANGIT_XP = 1.25D;
	public static final double SEMPURNA_XP = 1.4D;
	public static final double BUMI_HEALTH = 0.10D;
	public static final double LANGIT_STRIKES = 0.85D;
	/** Pillars drawn per type (index = type). */
	public static final int[] PILLARS = {0, 9, 9, 9, 10};

	private static final String[] IDS = {"none", "fana", "bumi", "langit", "sempurna"};
	private static final int[] COLORS = {0xA8B5B0, 0xB9A58A, 0x9BD17A, 0xFFE27A, 0xFFFFFF};
	private static final Identifier HEALTH_ID = Kultivator.id("path_foundation_health");

	static void register() {
		DiscipleHooks.addXpFactor((entity, data, source) -> xpFactor(data, source));
		DiscipleHooks.addStats((entity, sink) -> {
			if (type(entity.data()) == BUMI) {
				sink.add(Attributes.MAX_HEALTH, HEALTH_ID, BUMI_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			}
		});
		DiscipleHooks.addOnFed(FoundationTypes::onFed);
	}

	/** The disciple's Foundation type; a disciple that reached Foundation before phase 6 counts as Fana. */
	public static int type(DiscipleData data) {
		int stored = DisciplePaths.tag(data).getIntOr("foundation", NONE);
		if (stored > NONE && stored <= SEMPURNA) {
			return stored;
		}
		return data.realm().atLeast(Realm.FOUNDATION) ? FANA : NONE;
	}

	public static String id(int type) {
		return IDS[Math.max(0, Math.min(SEMPURNA, type))];
	}

	public static int color(int type) {
		return COLORS[Math.max(0, Math.min(SEMPURNA, type))];
	}

	public static Component displayName(int type) {
		return Component.translatable(DisciplePaths.PREFIX + "foundation." + id(type));
	}

	private static double xpFactor(DiscipleData data, XpSource source) {
		return switch (type(data)) {
			case BUMI -> BUMI_XP;
			case LANGIT -> LANGIT_XP;
			case SEMPURNA -> SEMPURNA_XP;
			default -> 1.0D;
		};
	}

	/** A Perfect Foundation Establishment Pill fed before the gathering leaves a marker (+5% Langit). */
	private static void onFed(DiscipleEntity entity, ServerPlayer master, ItemStack stack) {
		if (stack.getItem() instanceof PillItem item && item.kind() == PillKind.FOUNDATION
				&& AlchemyComponents.quality(stack) == PillQuality.PERFECT && !entity.data().realm().atLeast(Realm.FOUNDATION)) {
			DisciplePaths.section(entity.data()).putBoolean("fpill", true);
		}
	}

	/** Rolled on a successful Qi Refining to Foundation (called by {@link HuFa}'s breakthrough listener). */
	static void roll(DiscipleEntity entity, boolean masterMeditatedNear) {
		DiscipleData data = entity.data();
		ServerPlayer master = entity.owner();
		BreakthroughQuality masterFoundation = master == null ? null : Cultivators.get(master).quality(Realm.FOUNDATION);
		boolean masterQuality = masterFoundation == BreakthroughQuality.PERFECT || masterFoundation == BreakthroughQuality.HEAVENLY;
		boolean pill = DisciplePaths.tag(data).getBooleanOr("fpill", false);
		double bumi = BUMI_CHANCE + (masterMeditatedNear ? MASTER_BUMI : 0.0D);
		double langit = LANGIT_CHANCE + (masterMeditatedNear ? MASTER_LANGIT : 0.0D) + (masterQuality ? MASTER_QUALITY_LANGIT : 0.0D)
				+ (pill ? PILL_LANGIT : 0.0D);
		double sempurna = masterMeditatedNear && masterQuality && pill ? ALL_THREE_SEMPURNA : SEMPURNA_CHANCE;
		double roll = entity.getRandom().nextDouble() * 100.0D;
		int type = roll < sempurna ? SEMPURNA : roll < sempurna + langit ? LANGIT : roll < sempurna + langit + bumi ? BUMI : FANA;
		DisciplePaths.section(data).putInt("foundation", type);
		DisciplePaths.section(data).remove("fpill");
		entity.refreshSynced();
		if (entity.level() instanceof ServerLevel level) {
			Fx.send(level, entity.position(), FxType.STAR, PathFxIds.FOUNDATION, type, PILLARS[type]);
			DisciplePaths.sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.8F + 0.15F * type);
			if (type >= LANGIT) {
				DisciplePaths.sound(level, entity.position(), SoundEvents.BELL_RESONATE, 1.0F, type == SEMPURNA ? 1.5F : 1.1F);
			}
		}
		if (master != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "foundation.rolled", DisciplePaths.name(entity),
					displayName(type).copy().withColor(color(type)), PILLARS[type]).withColor(color(type)));
			if (type >= LANGIT) {
				Advancements.grant(master, "heaven_dao_foundation");
			}
			if (type == SEMPURNA) {
				Advancements.grant(master, "perfect_foundation");
			}
		}
	}

	/** True when a strike of the disciple's later breakthroughs is softened by a Heaven-Dao Foundation. */
	static boolean softensStrikes(@Nullable DiscipleData data) {
		return data != null && type(data) == LANGIT;
	}

	private FoundationTypes() {
	}
}
