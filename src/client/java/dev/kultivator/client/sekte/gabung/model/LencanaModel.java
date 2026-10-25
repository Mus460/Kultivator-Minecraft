package dev.kultivator.client.sekte.gabung.model;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.sekte.gabung.masuk.GabungItems;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The item model hooks of the Lencana Anggota (DESIGN 41.11.4): {@code assets/kultivator/items/lencana_anggota.json}
 * (written by {@code tools/gen_gabung_assets.py}) picks one of three frames with the range property
 * {@code kultivator:pangkat_anggota} (the {@code api/Pangkat} ordinal of the {@code kultivator:anggota} component:
 * 0-2 wood, 3-4 silver, 5+ gold; Tetua Tamu 9 is gold) and tints the jade face (layer 1) with the tint source
 * {@code kultivator:warna_anggota} (the sect's ink colour stored in the component at issue). Both registered into the
 * vanilla id mappers (access-widened by Fabric) from {@link GabungModelClient#init}, before the first resource load.
 */
public final class LencanaModel {

	/** {@code kultivator:warna_anggota}: the badge's sect colour, {@code default} without the component. */
	public record WarnaAnggota(int bawaan) implements ItemTintSource {

		public static final MapCodec<WarnaAnggota> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				ExtraCodecs.RGB_COLOR_CODEC.optionalFieldOf("default", 0x5FA88C).forGetter(WarnaAnggota::bawaan)
		).apply(i, WarnaAnggota::new));

		@Override
		public int calculate(ItemStack itemStack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
			GabungItems.Anggota anggota = itemStack.get(GabungItems.ANGGOTA);
			return ARGB.opaque(anggota == null ? bawaan : anggota.warna());
		}

		@Override
		public MapCodec<WarnaAnggota> type() {
			return MAP_CODEC;
		}
	}

	/** {@code kultivator:pangkat_anggota}: the badge's rank ordinal (0 without the component, Calon -1 reads 0). */
	public record PangkatAnggota() implements RangeSelectItemModelProperty {

		public static final MapCodec<PangkatAnggota> MAP_CODEC = MapCodec.unit(new PangkatAnggota());

		@Override
		public float get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
			GabungItems.Anggota anggota = itemStack.get(GabungItems.ANGGOTA);
			return anggota == null ? 0.0F : Math.max(0, anggota.pangkat());
		}

		@Override
		public MapCodec<PangkatAnggota> type() {
			return MAP_CODEC;
		}
	}

	private static boolean registered;

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ItemTintSources.ID_MAPPER.put(Kultivator.id("warna_anggota"), WarnaAnggota.MAP_CODEC);
		RangeSelectItemModelProperties.ID_MAPPER.put(Kultivator.id("pangkat_anggota"), PangkatAnggota.MAP_CODEC);
	}

	private LencanaModel() {
	}
}
