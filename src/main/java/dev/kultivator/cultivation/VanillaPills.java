package dev.kultivator.cultivation;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla items used as pills (DESIGN section 9, phase 1). Food is reported by the LivingEntity mixin when eating
 * finishes; the other items are absorbed with sneak + use (one is consumed, not in creative). Every pill gives its
 * effect scaled by {@link PillToxicity#effectiveness}, then adds its toxin.
 */
public final class VanillaPills {

	/** {@link Pill#qi()} value meaning "fill the qi pool completely". */
	public static final int FULL_QI = -1;
	/** Item cooldown after a sneak-use pill, in ticks. */
	public static final int USE_COOLDOWN = 10;
	/** Sub-compound of {@code CultivationData.extra()} owned by this module (shared with {@link PillToxicity}). */
	static final String EXTRA = "pills";
	/** Flag in {@link #EXTRA}: honey already shortened the current Qi Deviation (cleared when it ends). */
	static final String HONEY_USED = "honey_used";

	private static final double ENCHANTED_TRIBULATION_FACTOR = 0.85D;
	private static final double NETHER_STAR_BREAKTHROUGH_BONUS = 0.1D;
	private static final double HONEY_DEVIATION_FACTOR = 0.8D;
	private static final double ECHO_SPACE_COMPREHENSION = 1.0D;

	/** Every vanilla item that works as a pill, with its numbers from the design table. */
	public enum Pill {
		GOLDEN_CARROT(Items.GOLDEN_CARROT, 8, 0, 2, 0xFFC93C, 1, false, false),
		GLOW_BERRIES(Items.GLOW_BERRIES, 2, 5, 2, 0xFFE27A, 1, false, false),
		HONEY_BOTTLE(Items.HONEY_BOTTLE, 0, 0, 3, 0xFFA928, 1, false, false),
		GOLDEN_APPLE(Items.GOLDEN_APPLE, 30, 30, 6, 0xFFD54A, 2, false, false),
		ENCHANTED_GOLDEN_APPLE(Items.ENCHANTED_GOLDEN_APPLE, 250, FULL_QI, 20, 0xE98BFF, 3, false, false),
		GLISTERING_MELON_SLICE(Items.GLISTERING_MELON_SLICE, 0, 20, 3, 0xFF7484, 1, true, false),
		AMETHYST_SHARD(Items.AMETHYST_SHARD, 0, 15, 3, 0xB57CFF, 1, true, false),
		ECHO_SHARD(Items.ECHO_SHARD, 60, 0, 8, 0x2CC7C0, 2, true, false),
		DRAGON_BREATH(Items.DRAGON_BREATH, 400, 0, 25, 0xD94DFF, 3, true, true),
		NETHER_STAR(Items.NETHER_STAR, 1500, 0, 40, 0xF2F6FF, 3, true, true),
		EXPERIENCE_BOTTLE(Items.EXPERIENCE_BOTTLE, 15, 0, 2, 0x9CFF4A, 1, true, false);

		private static final Pill[] ALL = values();

		private final Item item;
		private final int xp;
		private final int qi;
		private final int toxin;
		private final int color;
		private final int strength;
		private final boolean sneakUse;
		private final boolean forbidden;

		Pill(Item item, int xp, int qi, int toxin, int color, int strength, boolean sneakUse, boolean forbidden) {
			this.item = item;
			this.xp = xp;
			this.qi = qi;
			this.toxin = toxin;
			this.color = color;
			this.strength = strength;
			this.sneakUse = sneakUse;
			this.forbidden = forbidden;
		}

		public Item item() {
			return item;
		}

		/** Cultivation XP before toxicity and multipliers. */
		public int xp() {
			return xp;
		}

		/** Qi restored, or {@link #FULL_QI}. */
		public int qi() {
			return qi;
		}

		public int toxin() {
			return toxin;
		}

		/** RGB of the pill's effect and tooltip line. */
		public int color() {
			return color;
		}

		/** 1..3, how grand the effect looks and sounds. */
		public int strength() {
			return strength;
		}

		/** Absorbed with sneak + use instead of being eaten. */
		public boolean sneakUse() {
			return sneakUse;
		}

		/** Dragon's breath and the nether star: "forbidden pills". */
		public boolean forbidden() {
			return forbidden;
		}

		/**
		 * The short effect description shared by the tooltip (base numbers, before toxicity and multipliers), e.g.
		 * "+30 Cultivation, +30 Qi".
		 */
		public List<Component> effectParts() {
			List<Component> parts = new ArrayList<>();
			if (xp > 0) {
				parts.add(Component.translatable("kultivator.pills.part.xp", Integer.toString(xp)));
			}
			if (qi == FULL_QI) {
				parts.add(Component.translatable("kultivator.pills.part.qi_all"));
			} else if (qi > 0) {
				parts.add(Component.translatable("kultivator.pills.part.qi", Integer.toString(qi)));
			}
			switch (this) {
				case HONEY_BOTTLE -> parts.add(Component.translatable("kultivator.pills.part.deviation_short"));
				case ENCHANTED_GOLDEN_APPLE -> parts.add(Component.translatable("kultivator.pills.part.tribulation"));
				case NETHER_STAR -> parts.add(Component.translatable("kultivator.pills.part.breakthrough"));
				default -> {
				}
			}
			return parts;
		}

		/** The pill {@code stack} is, or null. */
		public static @Nullable Pill of(ItemStack stack) {
			if (stack.isEmpty()) {
				return null;
			}
			for (Pill pill : ALL) {
				if (stack.is(pill.item)) {
					return pill;
				}
			}
			return null;
		}
	}

	public static void init() {
		UseItemCallback.EVENT.register(VanillaPills::onUseItem);
	}

	/** True for the pills that are eaten or drunk (the mixin only remembers these). */
	public static boolean isEdiblePill(ItemStack stack) {
		Pill pill = Pill.of(stack);
		return pill != null && !pill.sneakUse();
	}

	/** Called (by the LivingEntity mixin) when the player finished eating/drinking {@code stack} (a copy). */
	public static void onConsumed(ServerPlayer player, ItemStack stack) {
		Pill pill = Pill.of(stack);
		if (pill == null || pill.sneakUse()) {
			return;
		}
		try {
			apply(player, pill, stack);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Could not apply the pill effect of {}", stack, exception);
		}
	}

	/** "a, b, c" with the translated separator. */
	public static MutableComponent join(List<Component> parts) {
		MutableComponent line = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				line.append(Component.translatable("kultivator.pills.separator"));
			}
			line.append(parts.get(i));
		}
		return line;
	}

	/**
	 * Sneak + use on both sides. The client answers SUCCESS so the use packet is sent, the arm swings and vanilla's
	 * own use (throwing the experience bottle) never runs; the server does the work. Never touches anything when the
	 * player is not sneaking.
	 */
	private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
		if (player.isSpectator() || !player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		Pill pill = Pill.of(stack);
		if (pill == null || !pill.sneakUse() || player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack taken = stack.copyWithCount(1);
		// The cooldown group comes from the stack, so set it before the stack may become empty.
		player.getCooldowns().addCooldown(stack, USE_COOLDOWN);
		if (!player.hasInfiniteMaterials()) {
			if (pill == Pill.DRAGON_BREATH) {
				ItemStack result = ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE), false);
				if (result != stack) {
					player.setItemInHand(hand, result);
				}
			} else {
				stack.shrink(1);
			}
		}
		try {
			apply(serverPlayer, pill, taken);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Could not apply the pill effect of {}", taken, exception);
		}
		return InteractionResult.SUCCESS;
	}

	/** The pill's effect, feedback, achievements and finally its toxin. */
	private static void apply(ServerPlayer player, Pill pill, ItemStack stack) {
		CultivationData data = Cultivators.get(player);
		double efficacy = Math.max(0.0D, Math.min(1.0D, PillToxicity.effectiveness(data)));
		List<Component> parts = new ArrayList<>();

		if (pill.xp() > 0) {
			double amount = pill.xp() * efficacy;
			double shown = amount * Progression.xpMultiplier(player, XpSource.PILL);
			Progression.addXp(player, amount, XpSource.PILL);
			parts.add(Component.translatable("kultivator.pills.part.xp", Msg.number(Math.max(1.0D, shown))));
		}
		if (pill.qi() != 0) {
			double before = Cultivators.get(player).qi();
			Qi.restore(player, pill.qi() == FULL_QI ? Qi.max(Cultivators.get(player)) : pill.qi());
			double gained = Cultivators.get(player).qi() - before;
			parts.add(gained >= 0.5D
					? Component.translatable("kultivator.pills.part.qi", Msg.number(gained + 1.0E-6D))
					: Component.translatable("kultivator.pills.part.qi_full"));
		}

		data = Cultivators.get(player);
		switch (pill) {
			case HONEY_BOTTLE -> {
				// Honey shortens a Qi Deviation once; more bottles during the same deviation only soothe.
				CompoundTag own = data.extra().getCompoundOrEmpty(EXTRA);
				if (data.deviating() && !own.getBooleanOr(HONEY_USED, false)) {
					data.setDeviationTicks((int) Math.round(data.deviationTicks() * HONEY_DEVIATION_FACTOR));
					own.putBoolean(HONEY_USED, true);
					data.extra().put(EXTRA, own);
					parts.add(Component.translatable("kultivator.pills.part.deviation", Msg.number(data.deviationTicks() / 20.0D)));
				} else if (data.deviating()) {
					parts.add(Component.translatable("kultivator.pills.part.honey_used"));
				} else {
					parts.add(Component.translatable("kultivator.pills.part.soothed"));
				}
			}
			case ENCHANTED_GOLDEN_APPLE -> {
				data.setNextTribulationFactor(Math.min(data.nextTribulationFactor(), ENCHANTED_TRIBULATION_FACTOR));
				parts.add(Component.translatable("kultivator.pills.part.tribulation"));
			}
			case ECHO_SHARD -> {
				if (data.dao() == Dao.SPACE) {
					data.addComprehension(Dao.SPACE, ECHO_SPACE_COMPREHENSION);
					parts.add(Component.translatable("kultivator.pills.part.comprehension", Msg.number(ECHO_SPACE_COMPREHENSION),
							Dao.SPACE.displayName()));
				}
				echoHint(player, data);
			}
			case NETHER_STAR -> {
				data.setNextBreakthroughBonus(data.nextBreakthroughBonus() + NETHER_STAR_BREAKTHROUGH_BONUS);
				parts.add(Component.translatable("kultivator.pills.part.breakthrough"));
			}
			default -> {
			}
		}
		if (pill.xp() > 0 && efficacy < 0.995D) {
			parts.add(Component.translatable("kultivator.pills.part.efficacy", Msg.number(efficacy * 100.0D + 1.0E-6D)));
		}
		countPill(data);
		Cultivators.dirty(player);

		Msg.actionBar(player, Component.translatable("kultivator.pills.taken", stack.getItemName(), join(parts)).withColor(pill.color()));
		Fx.send(player.level(), player.position(), FxType.PILL, pill.color(), pill.strength(), (float) efficacy);
		playSounds(player, pill);

		Advancements.grant(player, "first_pill");
		if (pill.forbidden()) {
			Advancements.grant(player, "forbidden_pill");
		}
		PillToxicity.add(player, pill.toxin());
	}

	/** The first echo shard whispers of Divine Sense once. */
	private static void echoHint(ServerPlayer player, CultivationData data) {
		CompoundTag own = data.extra().getCompoundOrEmpty(EXTRA);
		if (own.getBooleanOr("echo_hint", false)) {
			return;
		}
		own.putBoolean("echo_hint", true);
		data.extra().put(EXTRA, own);
		Msg.chat(player, Component.translatable("kultivator.pills.echo_hint").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
	}

	private static void countPill(CultivationData data) {
		CompoundTag own = data.extra().getCompoundOrEmpty(EXTRA);
		own.putInt("taken", own.getIntOr("taken", 0) + 1);
		data.extra().put(EXTRA, own);
	}

	private static void playSounds(ServerPlayer player, Pill pill) {
		ServerLevel level = player.level();
		double x = player.getX();
		double y = player.getY() + 1.0D;
		double z = player.getZ();
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.0F + 0.15F * pill.strength());
		if (pill.strength() >= 2) {
			level.playSound(null, x, y, z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 1.3F);
		}
		if (pill.strength() >= 3) {
			level.playSound(null, x, y, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.25F);
		}
		if (pill == Pill.DRAGON_BREATH) {
			level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.3F, 1.6F);
		} else if (pill == Pill.NETHER_STAR) {
			level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.5F);
		}
	}

	private VanillaPills() {
	}
}
