package dev.kultivator.dunia.kapal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * The keel items of DESIGN 34.5.15 (Lunas Kapal Roh, one per class) and the component that carries a docked ship's state
 * while it is an item. The hold rides in the vanilla {@code minecraft:container} component. Loaded by {@link Kapal#init()}
 * before {@code ModItems.init()}; no ItemStack is made here.
 */
public final class KapalItems {

	/**
	 * {@code kultivator:kapal_state}: the stowed ship (hull points, fuel ticks left, banner colour, broken flag, its given
	 * name, its hull livery {@link KapalStyle}). A keel without it is a new ship (full hull, no fuel, Kayu Hitam).
	 */
	public record State(int hull, long fuel, int color, boolean broken, String name, int style) {

		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("hull").forGetter(State::hull),
				Codec.LONG.optionalFieldOf("fuel", 0L).forGetter(State::fuel),
				Codec.INT.optionalFieldOf("color", KapalRohEntity.DEFAULT_COLOR).forGetter(State::color),
				Codec.BOOL.optionalFieldOf("broken", false).forGetter(State::broken),
				Codec.STRING.optionalFieldOf("name", "").forGetter(State::name),
				Codec.INT.optionalFieldOf("style", 0).forGetter(State::style)
		).apply(instance, State::new));

		public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, State::hull,
				ByteBufCodecs.VAR_LONG, State::fuel,
				ByteBufCodecs.INT, State::color,
				ByteBufCodecs.BOOL, State::broken,
				ByteBufCodecs.STRING_UTF8, State::name,
				ByteBufCodecs.VAR_INT, State::style,
				State::new);

		public State {
			name = name == null ? "" : name;
			style = Math.max(0, Math.min(KapalStyle.values().length - 1, style));
		}

		/** A state in the default livery. */
		public State(int hull, long fuel, int color, boolean broken, String name) {
			this(hull, fuel, color, broken, name, 0);
		}
	}

	public static final DataComponentType<State> STATE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Kultivator.id("kapal_state"),
			DataComponentType.<State>builder().persistent(State.CODEC).networkSynchronized(State.STREAM_CODEC).build());

	public static final Item LUNAS_PERAHU_AWAN = ModItems.register("lunas_perahu_awan", properties -> new LunasItem(properties, KapalClass.PERAHU),
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant());
	public static final Item LUNAS_KAPAL_SEKTE = ModItems.register("lunas_kapal_sekte", properties -> new LunasItem(properties, KapalClass.SEKTE),
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());
	public static final Item LUNAS_BAHTERA_LANGIT = ModItems.register("lunas_bahtera_langit",
			properties -> new LunasItem(properties, KapalClass.BAHTERA), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

	/** Loads the class so the entries above are registered. */
	static void init() {
	}

	/** The keel item of a class. */
	public static Item keel(KapalClass kind) {
		return switch (kind) {
			case PERAHU -> LUNAS_PERAHU_AWAN;
			case SEKTE -> LUNAS_KAPAL_SEKTE;
			case BAHTERA -> LUNAS_BAHTERA_LANGIT;
		};
	}

	private KapalItems() {
	}
}
