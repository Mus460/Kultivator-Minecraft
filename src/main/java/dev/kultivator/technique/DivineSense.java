package dev.kultivator.technique;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.harta.HartaLoot;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Bonus;
import dev.kultivator.technique.spec.dsl.Mark;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.run.Boons;
import dev.kultivator.technique.spec.run.Caster;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Indra Ilahi / Divine Sense (DESIGN 22.10): monsters within 48→96 (at most 64, no line of sight needed) glow for 240
 * ticks → (D) (+120 at Kesempurnaan Agung; at Puncak Jurus they are also marked vulnerable +10% for 200 ticks), and the
 * ores within 12→16 blocks are read (read-only, spread over 4 ticks) and sent to the caster's client as pings for 200
 * ticks, with a chat summary.
 * <p>
 * Phase 7 (DESIGN 19.3.4, 19.3.13): the same scan pings spirit-infused plants green ({@link SpiritHerbs#infused}, the
 * very herb the caster would find when breaking it today), and once per cast the unlooted containers (block entities
 * with a pending loot table) within {@link HartaLoot#SENSE_RADIUS} blocks of loaded chunks are pinged gold. Treasure
 * pings come first within {@code OrePings.MAX}. Sources: a spiritual sense sweeping the land (RMJ ch. 1269) and Han Li
 * sweeping his sense over his abode (RMJ: Immortal Realm ch. 367). Everything here only reads blocks.
 */
final class DivineSense {

	private static final int PING_TICKS = 200;
	private static final int SCAN_TICKS = 4;

	/** Ore kinds, rarest first (the chat summary keeps this order). */
	enum Ore {
		DEBRIS("ancient_debris", 0xB0704A),
		DIAMOND("diamond", 0x5EF0E6),
		EMERALD("emerald", 0x2BE070),
		GOLD("gold", 0xFCEE4B),
		NETHER_GOLD("nether_gold", 0xFFC23A),
		LAPIS("lapis", 0x3A6BFF),
		REDSTONE("redstone", 0xFF3030),
		IRON("iron", 0xE8B89A),
		COPPER("copper", 0xE8855A),
		QUARTZ("quartz", 0xF4EEE4),
		COAL("coal", 0x707070);

		final String id;
		final int color;

		Ore(String id, int color) {
			this.id = id;
			this.color = color;
		}
	}

	private static final TagKey<Block> COAL_ORES = BlockItemTags.COAL_ORES.block();
	private static final TagKey<Block> COPPER_ORES = BlockItemTags.COPPER_ORES.block();
	private static final TagKey<Block> IRON_ORES = BlockItemTags.IRON_ORES.block();
	private static final TagKey<Block> GOLD_ORES = BlockItemTags.GOLD_ORES.block();
	private static final TagKey<Block> REDSTONE_ORES = BlockItemTags.REDSTONE_ORES.block();
	private static final TagKey<Block> LAPIS_ORES = BlockItemTags.LAPIS_ORES.block();
	private static final TagKey<Block> DIAMOND_ORES = BlockItemTags.DIAMOND_ORES.block();
	private static final TagKey<Block> EMERALD_ORES = BlockItemTags.EMERALD_ORES.block();

	/** One ping: an ore ({@code ore} set), an infused plant or an unlooted container ({@code ore} null, {@code herb} tells which). */
	private record Found(BlockPos pos, @Nullable Ore ore, boolean herb, double distSq) {
		int color() {
			return ore != null ? ore.color : herb ? SpiritHerbs.SENSE_COLOR : HartaLoot.SENSE_COLOR;
		}
	}

	static boolean cast(Caster ctx, ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		double range = ctx.area(48.0D, 96.0D);
		int glow = ctx.duration(240.0D, 480.0D);
		boolean mark = ctx.peak(Bonus.Kind.PAYLOAD);
		List<LivingEntity> monsters = ctx.hostiles(eye, range, false);
		int glowing = 0;
		for (LivingEntity monster : monsters) {
			if (glowing++ >= dev.kultivator.technique.spec.TechniquePower.GLOWING_PER_CAST) {
				break;
			}
			monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, glow, 0, false, false), player);
			if (mark) {
				ctx.apply(monster, new Payload.Marked(Mark.vulnerable(0.10D), 200));
			}
		}
		Boons.markDivineSense(player, glow);
		int radius = (int) Math.round(ctx.area(12.0D, 16.0D));
		BlockPos center = player.blockPosition();
		List<Found> found = new ArrayList<>();
		boolean herbs = !ctx.copy() && KultivatorConfig.get().herbHarvest && Cultivators.get(player).realm().atLeast(Realm.QI_REFINING);
		for (int slice = 0; slice < SCAN_TICKS; slice++) {
			int index = slice;
			ctx.schedule(slice, () -> scan(level, player, herbs, center, eye, radius, index, found));
		}
		ctx.schedule(SCAN_TICKS, () -> report(player, found, monsters.size(), (int) range));
		ctx.keepAlive(SCAN_TICKS + 1);
		Techniques.fx(player, Technique.DIVINE_SENSE, 0, player.position(), eye, (float) range);
		ctx.castFx(player.position(), eye, (float) range, 0.0F, monsters.size(), glow);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.5F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		if (!ctx.copy()) {
			Advancements.grant(player, "divine_sense");
		}
		return true;
	}

	private static void report(ServerPlayer player, List<Found> found, int monsters, int range) {
		if (player.isRemoved()) {
			return;
		}
		// Treasure sense (DESIGN 19.3.13): unlooted containers of loaded chunks, once per cast.
		Vec3 eye = player.getEyePosition();
		for (BlockPos chest : HartaLoot.unlootedContainers(player.level(), player.blockPosition(), HartaLoot.SENSE_RADIUS)) {
			found.add(new Found(chest, null, false, eye.distanceToSqr(Vec3.atCenterOf(chest))));
		}
		int[] counts = new int[Ore.values().length];
		int herbCount = 0;
		int chestCount = 0;
		for (Found each : found) {
			if (each.ore() != null) {
				counts[each.ore().ordinal()]++;
			} else if (each.herb()) {
				herbCount++;
			} else {
				chestCount++;
			}
		}
		// Treasure first, then ores, each nearest first.
		found.sort(Comparator.comparingInt((Found each) -> each.ore() == null ? 0 : 1).thenComparingDouble(Found::distSq));
		int limit = Math.min(Payloads.OrePings.MAX, found.size());
		List<BlockPos> positions = new ArrayList<>(limit);
		List<Integer> colors = new ArrayList<>(limit);
		for (int i = 0; i < limit; i++) {
			positions.add(found.get(i).pos());
			colors.add(found.get(i).color());
		}
		Net.send(player, new Payloads.OrePings(positions, colors, PING_TICKS));
		List<Component> parts = new ArrayList<>();
		for (Ore ore : Ore.values()) {
			if (counts[ore.ordinal()] > 0) {
				parts.add(Component.translatable("kultivator.techniques.ore." + ore.id, String.valueOf(counts[ore.ordinal()])).withColor(ore.color));
			}
		}
		Component ores = parts.isEmpty()
				? Component.translatable("kultivator.techniques.divine_sense.no_ores").withColor(Techniques.MUTED)
				: ComponentUtils.formatList(parts, Component.literal(", ").withColor(Techniques.MUTED));
		Msg.chat(player, Component.translatable("kultivator.techniques.divine_sense.summary", Techniques.name(Technique.DIVINE_SENSE),
				ores).withColor(0xC9B8FF));
		Msg.chat(player, Component.translatable("kultivator.techniques.divine_sense.monsters", String.valueOf(monsters),
				String.valueOf(range)).withColor(Techniques.MUTED));
		if (herbCount > 0 || chestCount > 0) {
			List<Component> treasure = new ArrayList<>(2);
			if (herbCount > 0) {
				treasure.add(Component.translatable("kultivator.harta.sense.herbs", String.valueOf(herbCount)).withColor(SpiritHerbs.SENSE_COLOR));
			}
			if (chestCount > 0) {
				treasure.add(Component.translatable("kultivator.harta.sense.chests", String.valueOf(chestCount)).withColor(HartaLoot.SENSE_COLOR));
			}
			Msg.chat(player, Component.translatable("kultivator.harta.sense.summary",
					ComponentUtils.formatList(treasure, Component.literal(", ").withColor(Techniques.MUTED))).withColor(0xC9B8FF));
		}
	}

	/** One quarter of the sphere's height (read-only, loaded chunks only); with {@code herbs} also the infused plants. */
	private static void scan(ServerLevel level, ServerPlayer player, boolean herbs, BlockPos center, Vec3 eye, int radius, int slice,
							 List<Found> found) {
		if (player.isRemoved()) {
			return;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int radiusSq = radius * radius;
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		int span = 2 * radius + 1;
		int from = -radius + slice * span / SCAN_TICKS;
		int to = -radius + (slice + 1) * span / SCAN_TICKS;
		for (int dy = from; dy < to; dy++) {
			int y = center.getY() + dy;
			if (y < minY || y > maxY) {
				continue;
			}
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dy * dy + dz * dz > radiusSq) {
						continue;
					}
					cursor.set(center.getX() + dx, y, center.getZ() + dz);
					if (!level.isLoaded(cursor)) {
						continue;
					}
					BlockState state = level.getBlockState(cursor);
					Ore ore = classify(state);
					if (ore != null) {
						double distSq = eye.distanceToSqr(cursor.getX() + 0.5D, cursor.getY() + 0.5D, cursor.getZ() + 0.5D);
						found.add(new Found(cursor.immutable(), ore, false, distSq));
					} else if (herbs && SpiritHerbs.isPlant(state) && SpiritHerbs.infused(level, cursor, state, player) != null) {
						double distSq = eye.distanceToSqr(cursor.getX() + 0.5D, cursor.getY() + 0.5D, cursor.getZ() + 0.5D);
						found.add(new Found(cursor.immutable(), null, true, distSq));
					}
				}
			}
		}
	}

	private static @Nullable Ore classify(BlockState state) {
		if (state.isAir()) {
			return null;
		}
		if (state.is(Blocks.ANCIENT_DEBRIS)) {
			return Ore.DEBRIS;
		}
		if (state.is(Blocks.NETHER_QUARTZ_ORE)) {
			return Ore.QUARTZ;
		}
		if (state.is(Blocks.NETHER_GOLD_ORE)) {
			return Ore.NETHER_GOLD;
		}
		if (state.is(DIAMOND_ORES)) {
			return Ore.DIAMOND;
		}
		if (state.is(EMERALD_ORES)) {
			return Ore.EMERALD;
		}
		if (state.is(GOLD_ORES)) {
			return Ore.GOLD;
		}
		if (state.is(LAPIS_ORES)) {
			return Ore.LAPIS;
		}
		if (state.is(REDSTONE_ORES)) {
			return Ore.REDSTONE;
		}
		if (state.is(IRON_ORES)) {
			return Ore.IRON;
		}
		if (state.is(COPPER_ORES)) {
			return Ore.COPPER;
		}
		if (state.is(COAL_ORES)) {
			return Ore.COAL;
		}
		return null;
	}

	private DivineSense() {
	}
}
