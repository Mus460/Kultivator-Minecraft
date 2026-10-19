package dev.kultivator.tempa;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

/**
 * The two menus of P8-tempa (DESIGN 25.0, 25.5.2, 25.7.3): the forge, opened with its block position, and the ring, opened
 * with {@link RingOpen}. Both are Fabric {@code ExtendedMenuType}s, not {@code OpenScreen} ids.
 */
public final class TempaMenus {

	/**
	 * How a ring menu opens: the ring's inventory slot (locked while open), its container rows and its tier.
	 */
	public record RingOpen(int slot, int rows, int tier) {
		public static final StreamCodec<ByteBuf, RingOpen> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, RingOpen::slot,
				ByteBufCodecs.VAR_INT, RingOpen::rows,
				ByteBufCodecs.VAR_INT, RingOpen::tier,
				RingOpen::new);
	}

	public static final ExtendedMenuType<ForgeMenu, BlockPos> FORGE = Registry.register(BuiltInRegistries.MENU, Kultivator.id("earthfire_forge"),
			new ExtendedMenuType<ForgeMenu, BlockPos>(ForgeMenu::new, BlockPos.STREAM_CODEC));

	public static final ExtendedMenuType<RingMenu, RingOpen> RING = Registry.register(BuiltInRegistries.MENU, Kultivator.id("storage_ring"),
			new ExtendedMenuType<RingMenu, RingOpen>(RingMenu::new, RingOpen.STREAM_CODEC));

	/** Loads the class (registration happens in the static fields). */
	public static void init() {
	}

	private TempaMenus() {
	}
}
