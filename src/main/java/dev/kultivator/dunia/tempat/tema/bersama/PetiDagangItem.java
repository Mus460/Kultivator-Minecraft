package dev.kultivator.dunia.tempat.tema.bersama;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * {@code kultivator:dagang/peti} Peti Dagang (DESIGN 38.T.10.1): a sealed crate of 8 units of ONE trade good (seal 1 NB,
 * opened in any town). Used by Kontrak Muatan and the sect caravans; never sold to an ordinary shop as a filled
 * container (38.0 butir 16), and a crate opened on the road fails its contract. The contents ride in the
 * {@link #ISI} component; an empty crate (no component) is a fresh one from a merchant.
 * <p>
 * Behaviour (sealing, opening, contract checks) lands in W2 (38TK inti, {@code Dagang}/{@code KontrakMuatan}).
 */
public class PetiDagangItem extends Item {

	/** Units of one good in a sealed crate. */
	public static final int ISI_PETI = 8;

	/**
	 * {@code kultivator:peti_dagang}: the sealed contents.
	 *
	 * @param barang  the trade-good path without namespace ({@code dagang/sutra_awan})
	 * @param jumlah  units inside (1-8)
	 * @param asal    the site id of the town that sealed it (0 = unknown)
	 * @param kontrak the cargo-contract id it belongs to ("" = none)
	 */
	public record Isi(String barang, int jumlah, long asal, String kontrak) {

		public static final Codec<Isi> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("barang").forGetter(Isi::barang),
				Codec.INT.optionalFieldOf("jumlah", ISI_PETI).forGetter(Isi::jumlah),
				Codec.LONG.optionalFieldOf("asal", 0L).forGetter(Isi::asal),
				Codec.STRING.optionalFieldOf("kontrak", "").forGetter(Isi::kontrak)
		).apply(instance, Isi::new));

		public static final StreamCodec<ByteBuf, Isi> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Isi::barang,
				ByteBufCodecs.VAR_INT, Isi::jumlah,
				ByteBufCodecs.VAR_LONG, Isi::asal,
				ByteBufCodecs.STRING_UTF8, Isi::kontrak,
				Isi::new);

		public Isi {
			barang = barang == null ? "" : barang;
			jumlah = Math.max(1, Math.min(ISI_PETI, jumlah));
			kontrak = kontrak == null ? "" : kontrak;
		}
	}

	public static final DataComponentType<Isi> ISI = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("peti_dagang"), DataComponentType.<Isi>builder().persistent(Isi.CODEC).networkSynchronized(Isi.STREAM_CODEC).build());

	public PetiDagangItem(Properties properties) {
		super(properties);
	}
}
