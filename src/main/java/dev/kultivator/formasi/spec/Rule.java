package dev.kultivator.formasi.spec;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * What one pattern slot accepts (DESIGN 24.2.2). {@code rot} is the pattern rotation in clockwise quarter turns (0-3);
 * only directional rules care. Pure and read-only: a rule never changes a block.
 */
public interface Rule {

	/** True when the block (and fluid) read at the slot satisfies this rule in rotation {@code rot}. */
	boolean test(BlockState state, FluidState fluid, int rot);

	/** The block holograms, the bill and 6b emission show for this slot in rotation {@code rot}. */
	BlockState canonical(int rot);

	/** The translated label ("Balok Kecubung", "Tongkat End"). */
	Component label();

	/** The exact item a 6b socket asks for (eye, beacon, diamond or netherite block), or null for normal billing. */
	@Nullable Item exactItem();

	/** True when the accepted state turns with the pattern (a facing or axis property). */
	default boolean directional() {
		return false;
	}

	/** This rule or {@code other}; label and canonical state stay this rule's. */
	default Rule or(Rule other) {
		Rule self = this;
		return new Rule() {
			@Override
			public boolean test(BlockState state, FluidState fluid, int rot) {
				return self.test(state, fluid, rot) || other.test(state, fluid, rot);
			}

			@Override
			public BlockState canonical(int rot) {
				return self.canonical(rot);
			}

			@Override
			public Component label() {
				return self.label();
			}

			@Override
			public @Nullable Item exactItem() {
				return self.exactItem();
			}

			@Override
			public boolean directional() {
				return self.directional() || other.directional();
			}
		};
	}
}
