package dev.kultivator.harta;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.SpiritVeins;
import dev.kultivator.cultivation.XpSources;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Batu Roh, the four grades of spirit stone (DESIGN 19.3.2).
 * <p>
 * Sources: stones hold qi that cultivators absorb (RMJ ch. 214, 1156); grades low, mid, high (RMJ ch. 214) and top
 * grade (RMJ ch. 1155-1156); mining (RMJ donghua S2 ep. 1-2; Invincible ch. 1392); instant qi in a battle of attrition
 * (RMJ ch. 169); formation fuel (RMJ ch. 131); breakthrough help (RMJ ch. 1152); currency (RMJ ch. 214, RI ch. 329,
 * AWE ch. 79). The 9 to 1 crafting, every chance and every duration are game design.
 * <p>
 * Mining: own {@code PlayerBlockBreakEvents.AFTER} listener; a stone pops out of an ore only after the player broke it
 * (never from Mode Kehancuran craters or sect construction, which fire no such event).
 */
public final class SpiritStones {

	/** Grades: 0 Low (Rendah), 1 Mid (Menengah), 2 High (Tinggi), 3 Supreme (Agung). */
	public static final int LOW = 0;
	public static final int MID = 1;
	public static final int HIGH = 2;
	public static final int SUPREME = 3;

	/** Mining chance = min(MINING_CAP, MINING_FACTOR x mining XP) x clamp(vein density, 0.5, 2.0). */
	public static final double MINING_FACTOR = 0.015D;
	public static final double MINING_CAP = 0.5D;
	public static final double DENSITY_MIN = 0.5D;
	public static final double DENSITY_MAX = 2.0D;
	/** Qi of a sneak-use outside meditation; −1 = full qi plus {@link #SUPREME_REGEN_FACTOR} regen. */
	public static final double[] INSTANT_QI = {30.0D, 200.0D, 900.0D, -1.0D};
	public static final int SUPREME_REGEN_TICKS = 1200;
	public static final double SUPREME_REGEN_FACTOR = 3.0D;
	public static final int USE_COOLDOWN = 20;
	/** Meditation fuel (applied by P7-kultivasi in {@code Meditation}): one stone every n seconds, the XP factor. */
	public static final int[] FUEL_SECONDS = {30, 150, 300, 600};
	public static final double[] FUEL_XP = {1.5D, 2.0D, 2.5D, 3.0D};
	/** Protective formation power per stone in ticks (20 min, 3 h, 24 h); −1 = to the stone cap. */
	public static final int[] FORMATION_TICKS = {20 * 60 * 20, 20 * 60 * 60 * 3, 20 * 60 * 60 * 24, -1};
	/** Kantor Misi contribution points per stone. */
	public static final double[] MISSION_PK = {0.5D, 4.5D, 40.0D, 300.0D};
	/** Alchemy catalysts: a Low stone adds success, a Mid or better stone success and quality. */
	public static final double LOW_CATALYST_CHANCE = 0.05D;
	public static final double MID_CATALYST_CHANCE = 0.10D;
	public static final double MID_CATALYST_QUALITY = 0.1D;
	public static final int[] COLORS = {0x9FE0C0, 0x6FB8FF, 0xB07CFF, 0xFFE27A};
	/** {@code extra.harta.regen_until}: game time the Supreme stone's qi regen boost ends. */
	public static final String REGEN_UNTIL = "regen_until";

	private static final TagKey<Block> DIAMOND_ORES = BlockItemTags.DIAMOND_ORES.block();
	private static final TagKey<Block> EMERALD_ORES = BlockItemTags.EMERALD_ORES.block();

	static void init() {
		PlayerBlockBreakEvents.AFTER.register(SpiritStones::onBlockBroken);
	}

	/** True for a spirit stone of any grade. */
	public static boolean isStone(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof SpiritStoneItem;
	}

	/** The grade 0-3 of a spirit stone, −1 for anything else. */
	public static int grade(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof SpiritStoneItem stone ? stone.grade() : -1;
	}

	/** {@code count} stones of {@code grade} (0-3). */
	public static ItemStack stack(int grade, int count) {
		return new ItemStack(HartaItems.stoneItem(grade), Math.max(1, count));
	}

	/** "Batu Roh Rendah" in its grade colour. */
	public static Component name(int grade) {
		return new ItemStack(HartaItems.stoneItem(grade)).getHoverName().copy().withColor(COLORS[Math.max(0, Math.min(3, grade))]);
	}

	// ================================================================ mining

	private static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer
				|| serverPlayer.isCreative() || serverPlayer.isSpectator() || !KultivatorConfig.get().spiritStoneMining) {
			return;
		}
		double xp = XpSources.miningXp(state);
		if (xp <= 0.0D) {
			return;
		}
		CultivationData data = Cultivators.get(serverPlayer);
		if (!data.realm().atLeast(Realm.QI_REFINING) || hasSilkTouch(level, serverPlayer.getMainHandItem())) {
			return;
		}
		if (serverPlayer.getRandom().nextDouble() >= miningChance(xp, SpiritVeins.density(serverLevel, pos))) {
			return;
		}
		int count = state.is(DIAMOND_ORES) || state.is(EMERALD_ORES) ? 1 + serverPlayer.getRandom().nextInt(2) : 1;
		Block.popResource(level, pos, stack(LOW, count));
		Vec3 centre = Vec3.atCenterOf(pos);
		Fx.send(serverLevel, centre, FxType.TREASURE, HartaFxIds.STONE_FOUND, LOW, count);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 0.7F, 1.5F);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.3F);
		Msg.actionBar(serverPlayer, Component.translatable("kultivator.harta.stone.mined", count, name(LOW)).withColor(COLORS[LOW]));
		Advancements.grant(serverPlayer, "first_spirit_stone");
	}

	/** The chance an ore of {@code miningXp} gives a Low stone at vein {@code density} (pure). */
	public static double miningChance(double miningXp, double density) {
		return Math.min(MINING_CAP, MINING_FACTOR * miningXp) * Math.max(DENSITY_MIN, Math.min(DENSITY_MAX, density));
	}

	private static boolean hasSilkTouch(Level level, ItemStack tool) {
		if (tool.isEmpty()) {
			return false;
		}
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SILK_TOUCH)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, tool) > 0)
				.orElse(false);
	}

	// ================================================================ instant qi

	/** Sneak + use outside meditation: the stone's qi at once. False (nothing used) when it cannot be taken now. */
	static boolean absorb(ServerPlayer player, ItemStack stack, int grade) {
		if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) {
			return false;
		}
		CultivationData data = Cultivators.get(player);
		if (data.meditating()) {
			Msg.actionBar(player, Component.translatable("kultivator.harta.stone.meditating").withColor(COLORS[grade]));
			return false;
		}
		double max = Qi.max(data);
		if (grade < SUPREME && data.qi() >= max - 0.5D) {
			Msg.actionBar(player, Component.translatable("kultivator.harta.stone.qi_full").withColor(COLORS[grade]));
			return false;
		}
		Component name = stack.getHoverName().copy();
		player.getCooldowns().addCooldown(stack, USE_COOLDOWN);
		stack.consume(1, player);
		double before = data.qi();
		ServerLevel level = player.level();
		if (grade >= SUPREME) {
			Qi.fill(player);
			Harta.extra(data).putLong(REGEN_UNTIL, level.getGameTime() + SUPREME_REGEN_TICKS);
		} else {
			Qi.restore(player, INSTANT_QI[grade]);
		}
		double gained = Cultivators.get(player).qi() - before;
		Cultivators.dirty(player);
		Component line = grade >= SUPREME
				? Component.translatable("kultivator.harta.stone.absorbed_supreme", name, SUPREME_REGEN_TICKS / 20, HartaText.decimal(SUPREME_REGEN_FACTOR))
				: Component.translatable("kultivator.harta.stone.absorbed", name, Msg.number(Math.max(0.0D, gained) + 1.0E-6D));
		Msg.actionBar(player, line.copy().withColor(COLORS[grade]));
		Fx.send(level, player.position(), FxType.TREASURE, HartaFxIds.STONE_ABSORB, grade, 0.0F);
		level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
				0.8F, 1.0F + 0.15F * grade);
		level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS,
				0.6F, 1.4F);
		return true;
	}

	/** Every 20 ticks from {@code Harta}: the Supreme stone's qi regen boost (the extra regen beyond the normal one). */
	static void regenTick(ServerPlayer player, CultivationData data) {
		CompoundTag own = data.extra().getCompoundOrEmpty(Harta.EXTRA);
		long until = own.getLongOr(REGEN_UNTIL, 0L);
		if (until <= 0L) {
			return;
		}
		long now = player.level().getGameTime();
		if (now >= until || until - now > SUPREME_REGEN_TICKS) {
			own.remove(REGEN_UNTIL);
			data.extra().put(Harta.EXTRA, own);
			return;
		}
		Qi.restore(player, Qi.regenPerSecond(data) * (SUPREME_REGEN_FACTOR - 1.0D));
	}

	private SpiritStones() {
	}
}
