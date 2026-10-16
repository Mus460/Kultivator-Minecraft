package dev.kultivator.technique;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Technique;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
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
 * Indra Ilahi / Divine Sense (Nascent Soul+): the soul's sense spreads out. Monsters within 48 blocks glow for 12 s and
 * the ores within 12 blocks are read (read-only) and sent to the caster's client as pings shown for 10 s, with a chat
 * summary.
 */
final class DivineSense {

	private static final double MONSTER_RANGE = 48.0D;
	private static final int ORE_RADIUS = 12;
	private static final int GLOW_TICKS = 240;
	private static final int PING_TICKS = 200;

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

	private record Found(BlockPos pos, Ore ore, double distSq) {
	}

	static boolean cast(ServerPlayer player, CultivationData data) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();

		List<LivingEntity> monsters = Targeting.hostilesAround(player, eye, MONSTER_RANGE);
		for (LivingEntity monster : monsters) {
			monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false), player);
		}

		List<Found> found = scan(level, player.blockPosition(), eye);
		int[] counts = new int[Ore.values().length];
		for (Found each : found) {
			counts[each.ore().ordinal()]++;
		}
		found.sort(Comparator.comparingDouble(Found::distSq));
		int limit = Math.min(Payloads.OrePings.MAX, found.size());
		List<BlockPos> positions = new ArrayList<>(limit);
		List<Integer> colors = new ArrayList<>(limit);
		for (int i = 0; i < limit; i++) {
			positions.add(found.get(i).pos());
			colors.add(found.get(i).ore().color);
		}
		Net.send(player, new Payloads.OrePings(positions, colors, PING_TICKS));

		List<Component> parts = new ArrayList<>();
		for (Ore ore : Ore.values()) {
			if (counts[ore.ordinal()] > 0) {
				parts.add(Component.translatable("kultivator.techniques.ore." + ore.id, String.valueOf(counts[ore.ordinal()]))
						.withColor(ore.color));
			}
		}
		Component ores = parts.isEmpty()
				? Component.translatable("kultivator.techniques.divine_sense.no_ores").withColor(Techniques.MUTED)
				: ComponentUtils.formatList(parts, Component.literal(", ").withColor(Techniques.MUTED));
		Msg.chat(player, Component.translatable("kultivator.techniques.divine_sense.summary", Techniques.name(Technique.DIVINE_SENSE),
				ores).withColor(0xC9B8FF));
		Msg.chat(player, Component.translatable("kultivator.techniques.divine_sense.monsters", String.valueOf(monsters.size()),
				String.valueOf((int) MONSTER_RANGE)).withColor(Techniques.MUTED));

		Techniques.fx(player, Technique.DIVINE_SENSE, 0, player.position(), eye, (float) MONSTER_RANGE);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.5F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		Advancements.grant(player, "divine_sense");
		return true;
	}

	/** Every ore block within {@link #ORE_RADIUS} of {@code center} (a sphere). Only reads blocks. */
	private static List<Found> scan(ServerLevel level, BlockPos center, Vec3 eye) {
		List<Found> found = new ArrayList<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int radiusSq = ORE_RADIUS * ORE_RADIUS;
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		for (int dy = -ORE_RADIUS; dy <= ORE_RADIUS; dy++) {
			int y = center.getY() + dy;
			if (y < minY || y > maxY) {
				continue;
			}
			for (int dx = -ORE_RADIUS; dx <= ORE_RADIUS; dx++) {
				for (int dz = -ORE_RADIUS; dz <= ORE_RADIUS; dz++) {
					if (dx * dx + dy * dy + dz * dz > radiusSq) {
						continue;
					}
					cursor.set(center.getX() + dx, y, center.getZ() + dz);
					Ore ore = classify(level.getBlockState(cursor));
					if (ore != null) {
						double distSq = eye.distanceToSqr(cursor.getX() + 0.5D, cursor.getY() + 0.5D, cursor.getZ() + 0.5D);
						found.add(new Found(cursor.immutable(), ore, distSq));
					}
				}
			}
		}
		return found;
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
