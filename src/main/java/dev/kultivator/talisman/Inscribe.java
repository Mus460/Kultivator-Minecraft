package dev.kultivator.talisman;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/**
 * Inscribing talismans (DESIGN 19.3.6): a blank talisman in the main hand, the catalyst in the offhand, sneak-use for a
 * 40-tick channel (Qi Refining+). At the end it costs the kind's qi and one catalyst; with the talisman skill's chance
 * the blank becomes a typed talisman of power = the inscriber's realm (+1, or +2 for a High or Raja core of the matching
 * element, at most 9), otherwise blank and catalyst burn to ash. Skill {@code extra.talisman.xp}: +4 per success, +1 per
 * failure; level {@code min(10, floor(sqrt(xp / 8)))}; chance {@code 0.60 + 0.035 × level}.
 * <p>
 * Sumber: RMJ ch. 242 "Making Talismans" (blank paper from spirit herbs, cinnabar; high-grade talismans need demonic
 * beast hides), ch. 765 (three failures before one success), ch. 1346 (better materials raise a low success rate).
 */
public final class Inscribe {

	/** Ticks of the inscribing channel. */
	public static final int CHANNEL_TICKS = 40;
	/** Success chance: base + per skill level, at most the cap. */
	public static final double BASE_CHANCE = 0.60D;
	public static final double CHANCE_PER_LEVEL = 0.035D;
	public static final double MAX_CHANCE = 0.95D;
	/** Skill XP per success and per failure; the level cap. */
	public static final int XP_SUCCESS = 4;
	public static final int XP_FAILURE = 1;
	public static final int MAX_LEVEL = 10;
	/** Power from a matching core: +1, or +2 for High and Raja cores. */
	public static final int CORE_BONUS = 1;
	public static final int STRONG_CORE_BONUS = 2;
	private static final String XP_KEY = "xp";
	private static final String MADE_KEY = "made";
	/** Formation bonuses (phase 7c) never add more than this to an inscription's chance, before {@link #MAX_CHANCE}. */
	public static final double FORMATION_CHANCE_CAP = 0.10D;
	private static final List<ToDoubleFunction<ServerPlayer>> CHANCE_PROVIDERS = new ArrayList<>();

	/** Phase 7c (DESIGN 24.13 item 8): extra inscription chance where the player stands (sum, 0 to the cap). */
	public static void addChanceProvider(ToDoubleFunction<ServerPlayer> provider) {
		CHANCE_PROVIDERS.add(provider);
	}

	/** The formation bonus to the player's inscription chance now (0 without providers). */
	public static double formationChance(ServerPlayer player) {
		double sum = 0.0D;
		for (ToDoubleFunction<ServerPlayer> provider : CHANCE_PROVIDERS) {
			double value = provider.applyAsDouble(player);
			if (Double.isFinite(value)) {
				sum += value;
			}
		}
		return Math.max(0.0D, Math.min(FORMATION_CHANCE_CAP, sum));
	}

	/** What the offhand would inscribe: the kind, the power bonus and whether it is a core. */
	public record Recipe(TalismanKind kind, int bonus, boolean core) {
	}

	/** The recipe of the offhand stack (a catalyst item or a matching beast core), or null. */
	public static @Nullable Recipe recipe(ItemStack offhand) {
		TalismanKind kind = TalismanKind.byCatalyst(offhand);
		if (kind != null) {
			return new Recipe(kind, 0, false);
		}
		if (BeastCores.isCore(offhand)) {
			Element element = BeastCores.element(offhand);
			TalismanKind byCore = TalismanKind.byCore(element);
			if (byCore != null) {
				return new Recipe(byCore, BeastCores.grade(offhand) >= BeastCores.HIGH ? STRONG_CORE_BONUS : CORE_BONUS, true);
			}
		}
		return null;
	}

	/** Talisman skill XP ({@code extra.talisman.xp}). */
	public static int xp(CultivationData data) {
		return Math.max(0, data.extra().getCompoundOrEmpty(Talismans.EXTRA_KEY).getIntOr(XP_KEY, 0));
	}

	/** Talismans inscribed so far ({@code extra.talisman.made}, read by the daily chores). */
	public static int made(CultivationData data) {
		return Math.max(0, data.extra().getCompoundOrEmpty(Talismans.EXTRA_KEY).getIntOr(MADE_KEY, 0));
	}

	/** Skill level 0-10 of {@code xp}. */
	public static int level(int xp) {
		return Math.min(MAX_LEVEL, (int) Math.floor(Math.sqrt(Math.max(0, xp) / 8.0D)));
	}

	public static int level(CultivationData data) {
		return level(xp(data));
	}

	/** Success chance at a skill level. */
	public static double chance(int level) {
		return Math.min(MAX_CHANCE, BASE_CHANCE + CHANCE_PER_LEVEL * level);
	}

	/** XP that reaches {@code level} ({@code 8 × level²}). */
	public static int xpForLevel(int level) {
		int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
		return 8 * clamped * clamped;
	}

	/** Sets the skill to exactly {@code level} ({@code uji jimat tingkat}). */
	public static void setLevel(ServerPlayer player, int level) {
		CultivationData data = Cultivators.get(player);
		Talismans.section(data).putInt(XP_KEY, xpForLevel(level));
		Cultivators.dirty(player);
		if (level >= MAX_LEVEL) {
			Advancements.grant(player, "talisman_master");
		}
	}

	/** Why the player cannot inscribe with this offhand now, or null. */
	static @Nullable Component refusal(ServerPlayer player, ItemStack offhand) {
		CultivationData data = Cultivators.get(player);
		if (!data.realm().atLeast(Realm.QI_REFINING)) {
			return Component.translatable("kultivator.talismans.inscribe.mortal");
		}
		Recipe recipe = recipe(offhand);
		if (recipe == null) {
			return Component.translatable("kultivator.talismans.inscribe.no_catalyst");
		}
		if (data.qi() + 1.0E-6D < recipe.kind().inscribeQi()) {
			return Component.translatable("kultivator.talismans.inscribe.no_qi", recipe.kind().inscribeQi(), Msg.number(data.qi()));
		}
		return null;
	}

	/** The end of the channel: pays, rolls and gives the talisman (or the ash). Returns the blank stack left. */
	static ItemStack complete(ServerPlayer player, ItemStack blank) {
		ItemStack offhand = player.getOffhandItem();
		Component why = refusal(player, offhand);
		if (why != null) {
			Msg.chat(player, why.copy().withColor(0xFF9A7A));
			return blank;
		}
		Recipe recipe = recipe(offhand);
		if (recipe == null || !Qi.trySpend(player, recipe.kind().inscribeQi())) {
			return blank;
		}
		CultivationData data = Cultivators.get(player);
		int levelBefore = level(data);
		boolean infinite = player.hasInfiniteMaterials();
		if (!infinite) {
			offhand.shrink(1);
			blank.shrink(1);
		}
		ServerLevel level = player.level();
		Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6D)).subtract(0.0D, 0.35D, 0.0D);
		CompoundTag section = Talismans.section(data);
		double successChance = CHANCE_PROVIDERS.isEmpty() ? chance(levelBefore)
				: Math.min(MAX_CHANCE, chance(levelBefore) + formationChance(player));
		boolean success = player.getRandom().nextDouble() < successChance;
		if (success) {
			int power = Math.min(TalismanData.MAX_POWER, data.realm().ordinal() + recipe.bonus());
			ItemStack talisman = Talismans.create(recipe.kind(), power, player.getName().getString());
			Talismans.give(player, talisman);
			section.putInt(XP_KEY, xp(data) + XP_SUCCESS);
			section.putInt(MADE_KEY, made(data) + 1);
			Fx.send(level, hand, FxType.TALISMAN, TalismanFxIds.INSCRIBED, recipe.kind().ordinal(), 0.0F);
			level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.9F, 1.3F);
			level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.5F);
			Msg.chat(player, Component.translatable("kultivator.talismans.inscribe.success", recipe.kind().displayName().copy()
					.withColor(recipe.kind().color()), power).withColor(0xFFE9A8));
			Advancements.grant(player, "first_talisman");
		} else {
			section.putInt(XP_KEY, xp(data) + XP_FAILURE);
			Fx.send(level, hand, FxType.TALISMAN, TalismanFxIds.ASH, recipe.kind().ordinal(), 0.0F);
			level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.1F);
			Msg.chat(player, Component.translatable("kultivator.talismans.inscribe.failed", recipe.kind().displayName()).withColor(0xB0A8A0));
		}
		int levelAfter = level(data);
		if (levelAfter > levelBefore) {
			Msg.chat(player, Component.translatable("kultivator.talismans.skill.level_up", levelAfter,
					Math.round(chance(levelAfter) * 100.0D)).withStyle(ChatFormatting.GOLD));
		}
		if (levelAfter >= MAX_LEVEL) {
			Advancements.grant(player, "talisman_master");
		}
		Cultivators.dirty(player);
		return blank;
	}

	// ================================================================== the blank

	/** Kertas Jimat Kosong / Blank Talisman: sneak-use with a catalyst in the offhand to inscribe. */
	public static class BlankItem extends Item {

		public BlankItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
				if (player instanceof ServerPlayer serverPlayer) {
					Msg.actionBar(serverPlayer, Component.translatable("kultivator.talismans.inscribe.hint").withColor(0xC9B7E6));
				}
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				Component why = refusal(serverPlayer, player.getOffhandItem());
				if (why != null) {
					Msg.actionBar(serverPlayer, why.copy().withColor(0xFF9A7A));
					return InteractionResult.FAIL;
				}
			} else if (recipe(player.getOffhandItem()) == null) {
				return InteractionResult.PASS;
			}
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return CHANNEL_TICKS;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.BRUSH;
		}

		@Override
		public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
			if (entity instanceof ServerPlayer player && ticksRemaining % 10 == 0) {
				Recipe recipe = recipe(player.getOffhandItem());
				Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6D)).subtract(0.0D, 0.35D, 0.0D);
				Fx.send(player.level(), hand, FxType.TALISMAN, TalismanFxIds.INSCRIBE, recipe == null ? -1 : recipe.kind().ordinal(), 0.0F);
				player.level().playSound(null, hand.x, hand.y, hand.z, SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.5F, 1.4F);
			}
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (entity instanceof ServerPlayer player) {
				return complete(player, stack);
			}
			return stack;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.talismans.blank.tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.talismans.blank.catalysts").withStyle(ChatFormatting.DARK_GRAY));
			builder.accept(Component.translatable("item.kultivator.blank_talisman.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private Inscribe() {
	}
}
