package dev.kultivator.client.dunia.kapal;

import dev.kultivator.dunia.kapal.KapalStyle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The hull liveries on the client (detail pass of DESIGN 34.5.15): a run's block state in another {@link KapalStyle}
 * (warped instead of dark oak, prismarine instead of slate tiles, ...). The swapped block keeps every property both blocks
 * share (facing, half, shape, axis, type), so stairs, slabs and logs keep their pose. Cached per style; nothing is
 * placed, the swap only changes which block model the renderer draws. Also the swatch colour the helm shows.
 * Client render thread only.
 */
public final class KapalLivery {

	@SuppressWarnings("unchecked")
	private static final Map<BlockState, BlockState>[] CACHE = new Map[KapalStyle.values().length];

	/** {@code state} as drawn in {@code style} ({@code state} itself when the style leaves its block alone). */
	public static BlockState styled(BlockState state, KapalStyle style) {
		if (style == KapalStyle.KAYU_HITAM) {
			return state;
		}
		Map<BlockState, BlockState> cache = CACHE[style.ordinal()];
		if (cache == null) {
			cache = new IdentityHashMap<>();
			CACHE[style.ordinal()] = cache;
		}
		BlockState out = cache.get(state);
		if (out == null) {
			out = swap(state, style);
			cache.put(state, out);
		}
		return out;
	}

	private static BlockState swap(BlockState state, KapalStyle style) {
		Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (key == null) {
			return state;
		}
		String from = key.toString();
		String to = style.remap(from);
		if (to.equals(from)) {
			return state;
		}
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(to));
		if (block.isEmpty()) {
			return state;
		}
		BlockState out = block.get().defaultBlockState();
		for (Property<?> property : state.getProperties()) {
			if (out.hasProperty(property)) {
				out = copy(out, state, property);
			}
		}
		return out;
	}

	private static <T extends Comparable<T>> BlockState copy(BlockState into, BlockState from, Property<T> property) {
		return into.setValue(property, from.getValue(property));
	}

	/** The swatch of a livery at the helm (its hull wood). */
	public static int swatch(KapalStyle style) {
		return switch (style) {
			case KAYU_HITAM -> 0x3B2A1A;
			case GIOK_LAUT -> 0x2E8B7A;
			case MERAH_LAK -> 0x9E2B25;
			case AWAN_PUTIH -> 0xEDEBE4;
			case BAMBU_EMAS -> 0xD9B24A;
		};
	}

	/** Forgets the swapped states (resource reload, disconnect). */
	public static void clear() {
		for (int i = 0; i < CACHE.length; i++) {
			CACHE[i] = null;
		}
	}

	private KapalLivery() {
	}
}
