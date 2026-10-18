package dev.kultivator.formasi;

import net.minecraft.world.level.block.Block;

/**
 * Mata Formasi, the Formation Eye (DESIGN 24.2.1): the anchor of every sect formation. No block entity, no random tick and
 * no block-state property; its glow is client FX, so no 7c code ever changes its state. Module A owns this class and its
 * sneak-use (through {@code UseBlockCallback} in {@code Formasi}).
 */
public class FormationEyeBlock extends Block {

	public FormationEyeBlock(Properties properties) {
		super(properties);
	}
}
