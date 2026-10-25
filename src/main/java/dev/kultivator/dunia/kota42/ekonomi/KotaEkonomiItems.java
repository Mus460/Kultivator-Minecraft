package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.registry.ModItems;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.UUID;

/**
 * The town-economy items and item components of DESIGN 42 (42.12.3, 42.6.1-42.6.7), registered always by
 * {@link #register()} before {@code ModItems.init()}; no {@code ItemStack} is made here (lazy stacks). Models and textures
 * are 42V's ({@code tools/gen_kota_hidup_assets.py}, {@code item/kota42/**}); names and tooltip lines live in 42C's lang
 * fragment {@code kota_hidup_ekonomi} (keys listed in {@code docs/progres/m2-42R0.md}). Behaviour (issue, bind, use) is 42C's.
 *
 * <h2>Items</h2>
 * <ul>
 *   <li>{@code kultivator:akta_rumah} Akta Rumah: the deed of a bought or rented house, carries {@link #RUMAH}; bound to its
 *   owner, re-printed at the Kantor Tanah for 5 NB (42.6.1).</li>
 *   <li>{@code kultivator:kunci_rumah} Kunci Rumah: one per house, carries {@link #RUMAH}; a 3 s channel takes the owner to
 *   the house door (42.6.1).</li>
 *   <li>{@code kultivator:topeng_pasar_hantu} Topeng Pasar Hantu: white paper mask with red stripes, worn on the head
 *   (equippable HEAD, 42V head model); 10 % off and no Nama Baik loss at the Ghost Market (42.6.7).</li>
 *   <li>{@code kultivator:lencana_pejabat} Lencana Pejabat: an official's badge, carries {@link #JABATAN} (site, rank 1-5);
 *   the tooltip shows the title (42.6.6).</li>
 *   <li>{@code kultivator:surat_kontrak} Surat Kontrak: a trade contract, carries {@link #KONTRAK}; information only, bound
 *   (42.6.5).</li>
 * </ul>
 *
 * <h2>Components (persistent and synced)</h2>
 * <ul>
 *   <li>{@code kultivator:rumah} {@link Rumah}: site id, house id inside the site, owner, the place name at issue.</li>
 *   <li>{@code kultivator:asal_toko} {@link AsalToko}: bought in a town shop on world day {@code hari}; shops refuse to buy it
 *   back for {@link AsalToko#TOLAK_JUAL_HARI} days and pay at most 0.5 (42.0 rule, 38.M.5).</li>
 *   <li>{@code kultivator:jabatan} {@link LencanaJabatan}: site, rank (1-5) and place name of an office.</li>
 *   <li>{@code kultivator:kontrak} {@link Kontrak}: contract id in 42C's data, a one-line summary and the holder.</li>
 * </ul>
 */
public final class KotaEkonomiItems {

	/** Tooltip key prefix (lang fragment {@code kota_hidup_ekonomi}). */
	public static final String P = "kultivator.kota42.tanda.";

	// ================================================================== components

	/** A house: site, house id within the site, owner and the place's display name at issue. */
	public record Rumah(long situs, int rumah, UUID pemilik, Component namaTempat) {

		public static final Codec<Rumah> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("situs").forGetter(Rumah::situs),
				Codec.INT.fieldOf("rumah").forGetter(Rumah::rumah),
				UUIDUtil.CODEC.fieldOf("pemilik").forGetter(Rumah::pemilik),
				ComponentSerialization.CODEC.optionalFieldOf("tempat", Component.empty()).forGetter(Rumah::namaTempat)
		).apply(instance, Rumah::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, Rumah> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, Rumah::situs,
				ByteBufCodecs.VAR_INT, Rumah::rumah,
				UUIDUtil.STREAM_CODEC, Rumah::pemilik,
				ComponentSerialization.STREAM_CODEC, Rumah::namaTempat,
				Rumah::new);

		public Rumah {
			namaTempat = namaTempat == null ? Component.empty() : namaTempat;
		}
	}

	/** Bought in a town shop of site {@code situs} on world day {@code hari}. */
	public record AsalToko(long situs, long hari) {

		/** Days a shop refuses to buy the item back. */
		public static final int TOLAK_JUAL_HARI = 3;

		public static final Codec<AsalToko> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("situs").forGetter(AsalToko::situs),
				Codec.LONG.fieldOf("hari").forGetter(AsalToko::hari)
		).apply(instance, AsalToko::new));

		public static final StreamCodec<io.netty.buffer.ByteBuf, AsalToko> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, AsalToko::situs,
				ByteBufCodecs.VAR_LONG, AsalToko::hari,
				AsalToko::new);

		/** True while a shop must refuse it on world day {@code hariIni}. */
		public boolean tolakJual(long hariIni) {
			return hariIni - hari < TOLAK_JUAL_HARI;
		}
	}

	/** An office: site, rank 1-5 (42.6.6) and the place's display name. */
	public record LencanaJabatan(long situs, int pangkat, Component namaTempat) {

		public static final int PANGKAT_MAKS = 5;

		public static final Codec<LencanaJabatan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("situs").forGetter(LencanaJabatan::situs),
				Codec.INT.optionalFieldOf("pangkat", 1).forGetter(LencanaJabatan::pangkat),
				ComponentSerialization.CODEC.optionalFieldOf("tempat", Component.empty()).forGetter(LencanaJabatan::namaTempat)
		).apply(instance, LencanaJabatan::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, LencanaJabatan> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, LencanaJabatan::situs,
				ByteBufCodecs.VAR_INT, LencanaJabatan::pangkat,
				ComponentSerialization.STREAM_CODEC, LencanaJabatan::namaTempat,
				LencanaJabatan::new);

		public LencanaJabatan {
			pangkat = Math.max(1, Math.min(PANGKAT_MAKS, pangkat));
			namaTempat = namaTempat == null ? Component.empty() : namaTempat;
		}
	}

	/** A trade contract: id in 42C's data, a one-line summary and the holder. */
	public record Kontrak(int id, Component ringkasan, UUID pemilik) {

		public static final Codec<Kontrak> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("id").forGetter(Kontrak::id),
				ComponentSerialization.CODEC.optionalFieldOf("ringkasan", Component.empty()).forGetter(Kontrak::ringkasan),
				UUIDUtil.CODEC.fieldOf("pemilik").forGetter(Kontrak::pemilik)
		).apply(instance, Kontrak::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, Kontrak> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Kontrak::id,
				ComponentSerialization.STREAM_CODEC, Kontrak::ringkasan,
				UUIDUtil.STREAM_CODEC, Kontrak::pemilik,
				Kontrak::new);

		public Kontrak {
			ringkasan = ringkasan == null ? Component.empty() : ringkasan;
		}
	}

	public static final DataComponentType<Rumah> RUMAH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("rumah"), DataComponentType.<Rumah>builder()
					.persistent(Rumah.CODEC)
					.networkSynchronized(Rumah.STREAM_CODEC)
					.build());

	public static final DataComponentType<AsalToko> ASAL_TOKO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("asal_toko_kota"), DataComponentType.<AsalToko>builder()
					.persistent(AsalToko.CODEC)
					.networkSynchronized(AsalToko.STREAM_CODEC)
					.build());

	public static final DataComponentType<LencanaJabatan> JABATAN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("jabatan"), DataComponentType.<LencanaJabatan>builder()
					.persistent(LencanaJabatan.CODEC)
					.networkSynchronized(LencanaJabatan.STREAM_CODEC)
					.build());

	public static final DataComponentType<Kontrak> KONTRAK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("kontrak"), DataComponentType.<Kontrak>builder()
					.persistent(Kontrak.CODEC)
					.networkSynchronized(Kontrak.STREAM_CODEC)
					.build());

	// ================================================================== items

	public static final Item AKTA_RUMAH = ModItems.register("akta_rumah", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item KUNCI_RUMAH = ModItems.register("kunci_rumah", KunciRumah::new,
			new Item.Properties().stacksTo(1));
	public static final Item TOPENG_PASAR_HANTU = ModItems.register("topeng_pasar_hantu", Item::new,
			new Item.Properties().stacksTo(1).equippable(EquipmentSlot.HEAD));
	public static final Item LENCANA_PEJABAT = ModItems.register("lencana_pejabat", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());
	public static final Item SURAT_KONTRAK = ModItems.register("surat_kontrak", Item::new,
			new Item.Properties().stacksTo(1));

	private static boolean registered;

	/** Loads the entries above and adds the component tooltip lines (shown on the client only); safe to call twice. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ItemComponentTooltipProviderRegistry.addLast(RUMAH, rumah -> (context, consumer, flag, components) -> {
			consumer.accept(Component.translatable(P + "rumah", rumah.namaTempat(), rumah.rumah()).withStyle(ChatFormatting.GOLD));
			consumer.accept(Component.translatable(P + "terikat").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		});
		ItemComponentTooltipProviderRegistry.addLast(ASAL_TOKO, asal -> (context, consumer, flag, components) ->
				consumer.accept(Component.translatable(P + "asal_toko", asal.hari()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		ItemComponentTooltipProviderRegistry.addLast(JABATAN, jabatan -> (context, consumer, flag, components) -> {
			consumer.accept(Component.translatable(P + "jabatan." + jabatan.pangkat()).withStyle(ChatFormatting.GOLD));
			consumer.accept(Component.translatable(P + "jabatan_tempat", jabatan.namaTempat()).withStyle(ChatFormatting.GRAY));
		});
		ItemComponentTooltipProviderRegistry.addLast(KONTRAK, kontrak -> (context, consumer, flag, components) -> {
			consumer.accept(kontrak.ringkasan().copy().withStyle(ChatFormatting.GRAY));
			consumer.accept(Component.translatable(P + "terikat").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		});
	}

	private KotaEkonomiItems() {
	}
}
