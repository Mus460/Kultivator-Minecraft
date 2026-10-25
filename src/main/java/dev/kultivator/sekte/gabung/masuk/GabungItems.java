package dev.kultivator.sekte.gabung.masuk;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.UUID;

/**
 * The items and item components of DESIGN 41 (41.11.4, 41.5.6, 41.6.5, 41.6.8, 41.7.1), registered always (rule K1) from
 * {@link Masuk#registerContent()} before {@code ModItems.init()}; no {@code ItemStack} is made here (lazy stacks). Models
 * and textures are 41V's ({@code tools/gen_gabung_assets.py}); names and tooltip lines live in 41B's lang fragment
 * {@code sekte_gabung_masuk} (keys listed in {@code docs/progres/m2-41S0.md}).
 *
 * <h2>Items</h2>
 * <ul>
 *   <li>{@code kultivator:lencana_anggota} Lencana Anggota: square jade with the sect emblem, tinted with the sect colour,
 *   three frames by rank group; carries {@link #ANGGOTA}; never sold, bound to its owner, re-issued at the Balai Hukum.</li>
 *   <li>{@code kultivator:tanda_calon} Tanda Calon: small plain jade "Calon &lt;sekte&gt;" while the application runs
 *   (carries {@link #ANGGOTA} with the sect and owner, rank {@link Anggota#CALON}); removed when the status ends.</li>
 *   <li>{@code kultivator:token_naik} Token Naik Sekte (Han Li's 升仙令): golden cloud plate; with {@link #SEKTE_TOKEN} it
 *   opens door TOKEN of that sect, without it of any sect of class &le; 3; used up.</li>
 *   <li>{@code kultivator:surat_undangan} Surat Undangan: sealed scroll of an envoy, opens door UNDANGAN (with
 *   {@link #SEKTE_TOKEN} naming the sect that invites).</li>
 *   <li>{@code kultivator:surat_restu} Surat Restu: red stamped scroll of a teacher's blessing, carries {@link #RESTU}.</li>
 *   <li>{@code kultivator:batu_pikul} Batu Pikul: corded stone of the TUBUH corak test "Pikul Gunung"; only exists during
 *   that test (41B-C gives Slowness II + Mining Fatigue while held, refuses storing and dropping).</li>
 * </ul>
 * The water buckets of the "Pikul Air" duty are prop 152 on a clip, not an item.
 *
 * <h2>Components (persistent and synced)</h2>
 * <ul>
 *   <li>{@code kultivator:anggota} {@link Anggota}: sect id, owner, rank ordinal ({@code api/Pangkat}), the sect's display
 *   name at issue and its ink colour (the 41V item tint reads {@link Anggota#warna()}).</li>
 *   <li>{@code kultivator:sekte_token} {@link SekteToken}: the sect a token or invitation belongs to, with its name.</li>
 *   <li>{@code kultivator:asal_sekte} {@link AsalSekte}: bought at a sect pavilion (sect, world day); 38 shops refuse to buy
 *   it back for 3 days and pay at most 0.5 (38.M.5).</li>
 *   <li>{@code kultivator:restu} {@link Restu}: the blessing of a Surat Restu (sect, rank at leaving, world day, name).</li>
 *   <li>{@code kultivator:warisan_sekte} sect id: a Teknik Warisan jade slip (41.6.5, rule 41.7.4).</li>
 *   <li>{@code kultivator:kuota_sekte} sect id: a secret-realm key handed out as the sect's quota (41.6.8).</li>
 * </ul>
 */
public final class GabungItems {

	/** Tooltip key prefix (lang fragment {@code sekte_gabung_masuk}). */
	public static final String P = "kultivator.gabung.tanda.";

	// ================================================================== components

	/** Membership on a badge or candidate token. {@code pangkat} is the {@code api/Pangkat} ordinal, {@link #CALON} = -1. */
	public record Anggota(String sekte, UUID pemilik, int pangkat, Component namaSekte, int warna) {

		/** Rank value of a Tanda Calon. */
		public static final int CALON = -1;

		public static final Codec<Anggota> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("sekte").forGetter(Anggota::sekte),
				UUIDUtil.CODEC.fieldOf("pemilik").forGetter(Anggota::pemilik),
				Codec.INT.optionalFieldOf("pangkat", 0).forGetter(Anggota::pangkat),
				ComponentSerialization.CODEC.optionalFieldOf("nama", Component.empty()).forGetter(Anggota::namaSekte),
				Codec.INT.optionalFieldOf("warna", 0xFFFFFF).forGetter(Anggota::warna)
		).apply(instance, Anggota::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, Anggota> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(16), Anggota::sekte,
				UUIDUtil.STREAM_CODEC, Anggota::pemilik,
				ByteBufCodecs.VAR_INT, Anggota::pangkat,
				ComponentSerialization.STREAM_CODEC, Anggota::namaSekte,
				ByteBufCodecs.INT, Anggota::warna,
				Anggota::new);

		public Anggota {
			sekte = sekte == null ? "" : sekte;
			namaSekte = namaSekte == null ? Component.empty() : namaSekte;
			pangkat = Math.max(CALON, Math.min(9, pangkat));
			warna &= 0xFFFFFF;
		}

		public boolean calon() {
			return pangkat == CALON;
		}
	}

	/** The sect a Token Naik Sekte or Surat Undangan belongs to. */
	public record SekteToken(String sekte, Component namaSekte) {

		public static final Codec<SekteToken> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("sekte").forGetter(SekteToken::sekte),
				ComponentSerialization.CODEC.optionalFieldOf("nama", Component.empty()).forGetter(SekteToken::namaSekte)
		).apply(instance, SekteToken::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, SekteToken> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(16), SekteToken::sekte,
				ComponentSerialization.STREAM_CODEC, SekteToken::namaSekte,
				SekteToken::new);

		public SekteToken {
			sekte = sekte == null ? "" : sekte;
			namaSekte = namaSekte == null ? Component.empty() : namaSekte;
		}
	}

	/** Bought at the Paviliun Jasa of {@code sekte} on world day {@code hari}. */
	public record AsalSekte(String sekte, long hari) {

		public static final Codec<AsalSekte> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("sekte").forGetter(AsalSekte::sekte),
				Codec.LONG.fieldOf("hari").forGetter(AsalSekte::hari)
		).apply(instance, AsalSekte::new));

		public static final StreamCodec<io.netty.buffer.ByteBuf, AsalSekte> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(16), AsalSekte::sekte,
				ByteBufCodecs.VAR_LONG, AsalSekte::hari,
				AsalSekte::new);

		/** Days a 38 shop refuses to buy the item back (38.M.5). */
		public static final int TOLAK_JUAL_HARI = 3;

		public AsalSekte {
			sekte = sekte == null ? "" : sekte;
		}

		/** True while a shop must refuse it on world day {@code hariIni}. */
		public boolean tolakJual(long hariIni) {
			return hariIni - hari < TOLAK_JUAL_HARI;
		}
	}

	/** A teacher's blessing (41.7.1): the sect left, the rank held then ({@code api/Pangkat} ordinal), the day, the teacher. */
	public record Restu(String sekte, int pangkat, long hari, Component namaSekte, String shifu) {

		public static final Codec<Restu> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("sekte").forGetter(Restu::sekte),
				Codec.INT.optionalFieldOf("pangkat", 1).forGetter(Restu::pangkat),
				Codec.LONG.optionalFieldOf("hari", 0L).forGetter(Restu::hari),
				ComponentSerialization.CODEC.optionalFieldOf("nama", Component.empty()).forGetter(Restu::namaSekte),
				Codec.STRING.optionalFieldOf("shifu", "").forGetter(Restu::shifu)
		).apply(instance, Restu::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, Restu> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(16), Restu::sekte,
				ByteBufCodecs.VAR_INT, Restu::pangkat,
				ByteBufCodecs.VAR_LONG, Restu::hari,
				ComponentSerialization.STREAM_CODEC, Restu::namaSekte,
				ByteBufCodecs.stringUtf8(64), Restu::shifu,
				Restu::new);

		public Restu {
			sekte = sekte == null ? "" : sekte;
			namaSekte = namaSekte == null ? Component.empty() : namaSekte;
			shifu = shifu == null ? "" : shifu.length() > 64 ? shifu.substring(0, 64) : shifu;
		}
	}

	public static final DataComponentType<Anggota> ANGGOTA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("anggota"), DataComponentType.<Anggota>builder()
					.persistent(Anggota.CODEC)
					.networkSynchronized(Anggota.STREAM_CODEC)
					.build());

	public static final DataComponentType<SekteToken> SEKTE_TOKEN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("sekte_token"), DataComponentType.<SekteToken>builder()
					.persistent(SekteToken.CODEC)
					.networkSynchronized(SekteToken.STREAM_CODEC)
					.build());

	public static final DataComponentType<AsalSekte> ASAL_SEKTE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("asal_sekte"), DataComponentType.<AsalSekte>builder()
					.persistent(AsalSekte.CODEC)
					.networkSynchronized(AsalSekte.STREAM_CODEC)
					.build());

	public static final DataComponentType<Restu> RESTU = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("restu"), DataComponentType.<Restu>builder()
					.persistent(Restu.CODEC)
					.networkSynchronized(Restu.STREAM_CODEC)
					.build());

	public static final DataComponentType<String> WARISAN_SEKTE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("warisan_sekte"), DataComponentType.<String>builder()
					.persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.stringUtf8(16))
					.build());

	public static final DataComponentType<String> KUOTA_SEKTE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("kuota_sekte"), DataComponentType.<String>builder()
					.persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.stringUtf8(16))
					.build());

	// ================================================================== items

	public static final Item LENCANA_ANGGOTA = ModItems.register("lencana_anggota", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant());
	public static final Item TANDA_CALON = ModItems.register("tanda_calon", Item::new,
			new Item.Properties().stacksTo(1));
	public static final Item TOKEN_NAIK = ModItems.register("token_naik", Item::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE).fireResistant());
	public static final Item SURAT_UNDANGAN = ModItems.register("surat_undangan", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item SURAT_RESTU = ModItems.register("surat_restu", Item::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item BATU_PIKUL = ModItems.register("batu_pikul", Item::new,
			new Item.Properties().stacksTo(1));

	private static boolean registered;

	/** Loads the entries above and adds the component tooltip lines (shown on the client only); safe to call twice. */
	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ItemComponentTooltipProviderRegistry.addLast(ANGGOTA, anggota -> (context, consumer, flag, components) -> {
			consumer.accept(Component.translatable(anggota.calon() ? P + "calon" : P + "anggota", anggota.namaSekte())
					.withColor(anggota.warna()));
			if (!anggota.calon()) {
				consumer.accept(Component.translatable(P + "pangkat", Component.translatable(P + "pangkat." + anggota.pangkat()))
						.withStyle(ChatFormatting.GRAY));
			}
			consumer.accept(Component.translatable(P + "terikat").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		});
		ItemComponentTooltipProviderRegistry.addLast(SEKTE_TOKEN, token -> (context, consumer, flag, components) ->
				consumer.accept(Component.translatable(P + "milik_sekte", token.namaSekte()).withStyle(ChatFormatting.GOLD)));
		ItemComponentTooltipProviderRegistry.addLast(ASAL_SEKTE, asal -> (context, consumer, flag, components) ->
				consumer.accept(Component.translatable(P + "asal_sekte", asal.hari()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		ItemComponentTooltipProviderRegistry.addLast(RESTU, restu -> (context, consumer, flag, components) -> {
			consumer.accept(Component.translatable(P + "restu", restu.namaSekte()).withStyle(ChatFormatting.RED));
			if (!restu.shifu().isEmpty()) {
				consumer.accept(Component.translatable(P + "restu_shifu", restu.shifu()).withStyle(ChatFormatting.GRAY));
			}
		});
		ItemComponentTooltipProviderRegistry.addLast(WARISAN_SEKTE, sekte -> (context, consumer, flag, components) ->
				consumer.accept(Component.translatable(P + "warisan").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC)));
		ItemComponentTooltipProviderRegistry.addLast(KUOTA_SEKTE, sekte -> (context, consumer, flag, components) ->
				consumer.accept(Component.translatable(P + "kuota").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC)));
	}

	private GabungItems() {
	}
}
