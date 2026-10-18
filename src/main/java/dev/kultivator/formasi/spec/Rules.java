package dev.kultivator.formasi.spec;

import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The pattern rule factories of DESIGN 24.2.2. Every rule only reads the state it is given. Labels of the generic rules
 * use {@code kultivator.spine7c.rule.*}; {@link #labelled} gives any rule a translated label of its own.
 */
public final class Rules {

	private static final Rotation[] ROTATIONS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};

	/** The vanilla rotation of {@code rot} clockwise quarter turns. */
	public static Rotation rotation(int rot) {
		return ROTATIONS[Math.floorMod(rot, 4)];
	}

	/** Exactly that block (any state). */
	public static Rule block(Block block) {
		return new BlockSet(List.of(block), block.getName(), null);
	}

	/** Exactly that block, which a 6b socket asks for as an exact item (eye, beacon, diamond or netherite block). */
	public static Rule exact(Block block) {
		return new BlockSet(List.of(block), block.getName(), block.asItem());
	}

	/** Any of these blocks; the first is the canonical one. */
	public static Rule anyOf(Block... blocks) {
		if (blocks.length == 0) {
			throw new IllegalArgumentException("anyOf needs a block");
		}
		MutableComponent label = blocks[0].getName().copy();
		if (blocks.length > 1) {
			label.append(" / ").append(blocks[1].getName());
		}
		if (blocks.length > 2) {
			label.append(" …");
		}
		return new BlockSet(List.of(blocks), label, null);
	}

	/** Any block in the tag; the canonical block is the tag's first entry (air before tags are loaded). */
	public static Rule tag(TagKey<Block> tag) {
		return new Rule() {
			@Override
			public boolean test(BlockState state, FluidState fluid, int rot) {
				return state.is(tag);
			}

			@Override
			public BlockState canonical(int rot) {
				for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
					return holder.value().defaultBlockState();
				}
				return Blocks.AIR.defaultBlockState();
			}

			@Override
			public Component label() {
				return Component.literal("#" + tag.location().getPath());
			}

			@Override
			public @Nullable Item exactItem() {
				return null;
			}
		};
	}

	/** That block with property {@code property} at {@code value}; facings and axes turn with the pattern. */
	public static Rule state(Block block, Property<?> property, Comparable<?> value) {
		BlockState base = with(block.defaultBlockState(), property, value);
		boolean turns = value instanceof Direction || value instanceof Direction.Axis;
		return new Rule() {
			@Override
			public boolean test(BlockState state, FluidState fluid, int rot) {
				return state.is(block) && state.hasProperty(property)
						&& state.getValue(property).equals(canonical(rot).getValue(property));
			}

			@Override
			public BlockState canonical(int rot) {
				return turns ? base.rotate(rotation(rot)) : base;
			}

			@Override
			public Component label() {
				return block.getName();
			}

			@Override
			public @Nullable Item exactItem() {
				return null;
			}

			@Override
			public boolean directional() {
				return turns;
			}
		};
	}

	/** Any oxidation, waxed or not, of a copper collection; the canonical block is waxed oxidized ("jade"). */
	public static Rule copper(WeatheringCopperCollection<?> collection) {
		List<Block> blocks = new ArrayList<>();
		for (Object each : collection.asList()) {
			if (each instanceof Block block) {
				blocks.add(block);
			}
		}
		Block canonical = collection.waxed().oxidized() instanceof Block block ? block : blocks.isEmpty() ? Blocks.AIR : blocks.get(0);
		List<Block> ordered = new ArrayList<>();
		ordered.add(canonical);
		for (Block block : blocks) {
			if (block != canonical) {
				ordered.add(block);
			}
		}
		return new BlockSet(ordered, Component.translatable("kultivator.spine7c.rule.copper", canonical.getName()), null);
	}

	/** Any standing or wall banner of any colour. */
	public static Rule banner() {
		List<Block> blocks = new ArrayList<>(Blocks.BANNER.asList());
		blocks.addAll(Blocks.WALL_BANNER.asList());
		blocks.remove(Blocks.BANNER.pick(DyeColor.WHITE));
		blocks.add(0, Blocks.BANNER.pick(DyeColor.WHITE));
		return new BlockSet(blocks, Component.translatable("kultivator.spine7c.rule.banner"), null);
	}

	/** A standing or wall banner of one colour. */
	public static Rule banner(DyeColor color) {
		return new BlockSet(List.of(Blocks.BANNER.pick(color), Blocks.WALL_BANNER.pick(color)),
				Component.translatable("kultivator.spine7c.rule.banner_color", Component.translatable("color.minecraft." + color.getName())), null);
	}

	/** A water source, including a waterlogged block (read-only). */
	public static Rule waterSource() {
		return new Rule() {
			@Override
			public boolean test(BlockState state, FluidState fluid, int rot) {
				return fluid.is(FluidTags.WATER) && fluid.isSource();
			}

			@Override
			public BlockState canonical(int rot) {
				return Blocks.WATER.defaultBlockState();
			}

			@Override
			public Component label() {
				return Component.translatable("kultivator.spine7c.rule.water");
			}

			@Override
			public @Nullable Item exactItem() {
				return null;
			}
		};
	}

	/** Any froglight, sea lantern, glowstone or shroomlight. */
	public static Rule light() {
		return new BlockSet(List.of(Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT, Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT,
				Blocks.PEARLESCENT_FROGLIGHT), Component.translatable("kultivator.spine7c.rule.light"), null);
	}

	/** {@code rule} with the translated label {@code key}. */
	public static Rule labelled(Rule rule, String key) {
		Component label = Component.translatable(key);
		return new Rule() {
			@Override
			public boolean test(BlockState state, FluidState fluid, int rot) {
				return rule.test(state, fluid, rot);
			}

			@Override
			public BlockState canonical(int rot) {
				return rule.canonical(rot);
			}

			@Override
			public Component label() {
				return label;
			}

			@Override
			public @Nullable Item exactItem() {
				return rule.exactItem();
			}

			@Override
			public boolean directional() {
				return rule.directional();
			}
		};
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState with(BlockState state, Property property, Comparable value) {
		if (!state.hasProperty(property) || !property.getValueClass().isInstance(value)) {
			throw new IllegalArgumentException("Bad state rule " + property.getName() + "=" + value);
		}
		return state.setValue(property, value);
	}

	/** A set of blocks, any state. */
	private record BlockSet(List<Block> blocks, Component label, @Nullable Item exactItem) implements Rule {
		private BlockSet {
			blocks = List.copyOf(blocks);
		}

		@Override
		public boolean test(BlockState state, FluidState fluid, int rot) {
			if (blocks.size() == 1) {
				return state.is(blocks.get(0));
			}
			return blocks.contains(state.getBlock());
		}

		@Override
		public BlockState canonical(int rot) {
			return blocks.get(0).defaultBlockState();
		}
	}

	private Rules() {
	}
}
