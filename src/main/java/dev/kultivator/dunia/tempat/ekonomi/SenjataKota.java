package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.harta.HartaText;
import dev.kultivator.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Senjata kota (DESIGN 38.M.2.1): seven shapes × four grades = 28 items {@code kultivator:senjata/<bentuk>_<mutu>}, the
 * rungs between a vanilla blade and a forged artifact (forged swords stay the top: {@code SWORD_BASE 6 + 0.6/AP}, cap 22).
 * Attack is the total damage of a hit (player base 1 + the item's modifier), speed attacks per second, durability by
 * grade 250 / 600 / 1200 / 2400. The Sword Duel class (35) is read by {@code WeaponClass.of} from item tags (data):
 * jian, dao, kipas_besi, kebut in {@code #minecraft:swords} (PEDANG), qiang in {@code #minecraft:spears} (TOMBAK), kapak
 * in {@code #minecraft:axes} (KAPAK); gada in {@code #kultivator:senjata_gada} (and {@code #minecraft:axes} until the duel
 * module reads the gada tag). Never a natal treasure (only {@code forged_artifact} is). Models and textures: 38MD
 * ({@code kultivator:item/senjata/<bentuk>_<mutu>}). No ItemStack is made in a static initializer.
 */
public final class SenjataKota {

	/** Weapon shapes in shop-tab order ({@code LembarKunci.TokoSenjata.TAB_*} ids). */
	public enum Bentuk {
		JIAN("jian", "PEDANG", new int[] {5, 7, 9, 11}, 1.6D, 0.0D, new long[] {18L, 70L, 260L, 1020L}),
		DAO("dao", "PEDANG", new int[] {6, 8, 10, 12}, 1.4D, 0.0D, new long[] {18L, 72L, 280L, 1060L}),
		QIANG("qiang", "TOMBAK", new int[] {5, 7, 9, 11}, 1.2D, 0.5D, new long[] {16L, 66L, 250L, 980L}),
		KAPAK("kapak", "KAPAK", new int[] {7, 9, 11, 13}, 1.0D, 0.0D, new long[] {17L, 68L, 270L, 1000L}),
		GADA("gada", "GADA", new int[] {8, 10, 12, 14}, 0.9D, 0.0D, new long[] {20L, 80L, 300L, 1100L}),
		KIPAS_BESI("kipas_besi", "PEDANG", new int[] {4, 6, 8, 10}, 2.0D, 0.0D, new long[] {15L, 60L, 240L, 900L}),
		KEBUT("kebut", "PEDANG", new int[] {3, 5, 7, 9}, 2.2D, 0.0D, new long[] {15L, 62L, 245L, 920L});

		public final String id;
		/** Sword Duel class name (35.3.1) shown in the shop. */
		public final String kelasDuel;
		private final int[] serangan;
		public final double kecepatan;
		/** Extra entity interaction range (qiang +0.5). */
		public final double jangkauan;
		private final long[] harga;

		Bentuk(String id, String kelasDuel, int[] serangan, double kecepatan, double jangkauan, long[] harga) {
			this.id = id;
			this.kelasDuel = kelasDuel;
			this.serangan = serangan;
			this.kecepatan = kecepatan;
			this.jangkauan = jangkauan;
			this.harga = harga;
		}

		public int serangan(Mutu mutu) {
			return serangan[mutu.ordinal()];
		}

		/** Base shop price in NB before the town price scale (38.M.2.1: 15-20 / 60-80 / 240-300 / 900-1100). */
		public long harga(Mutu mutu) {
			return harga[mutu.ordinal()];
		}

		public static @Nullable Bentuk byId(String id) {
			for (Bentuk bentuk : values()) {
				if (bentuk.id.equals(id)) {
					return bentuk;
				}
			}
			return null;
		}
	}

	/** Grades: iron, bluish spirit iron, veined silver, glowing jade. */
	public enum Mutu {
		FANA("fana", 250, 0, Rarity.COMMON),
		ROH_RENDAH("roh_rendah", 600, 2, Rarity.UNCOMMON),
		ROH_MENENGAH("roh_menengah", 1200, 3, Rarity.RARE),
		ROH_TINGGI("roh_tinggi", 2400, 4, Rarity.EPIC);

		public final String id;
		public final int dayaTahan;
		/** Lowest realm band allowed to buy (38.M.2.1: 0 / 2 / 3 / 4). */
		public final int pitaMin;
		public final Rarity rarity;

		Mutu(String id, int dayaTahan, int pitaMin, Rarity rarity) {
			this.id = id;
			this.dayaTahan = dayaTahan;
			this.pitaMin = pitaMin;
			this.rarity = rarity;
		}

		public static @Nullable Mutu byId(String id) {
			for (Mutu mutu : values()) {
				if (mutu.id.equals(id)) {
					return mutu;
				}
			}
			return null;
		}
	}

	/** One registered weapon. */
	public record Jenis(Bentuk bentuk, Mutu mutu, Item item) {
		public String id() {
			return bentuk.id + "_" + mutu.id;
		}
	}

	private static final List<Jenis> SEMUA = new ArrayList<>();

	static {
		for (Bentuk bentuk : Bentuk.values()) {
			for (Mutu mutu : Mutu.values()) {
				SEMUA.add(new Jenis(bentuk, mutu, daftar(bentuk, mutu)));
			}
		}
	}

	/** Loads the class (the 28 items above are registered). */
	static void init() {
	}

	/** Every weapon, shape-major (28). */
	public static List<Jenis> semua() {
		return Collections.unmodifiableList(SEMUA);
	}

	public static @Nullable Jenis jenis(Bentuk bentuk, Mutu mutu) {
		for (Jenis jenis : SEMUA) {
			if (jenis.bentuk == bentuk && jenis.mutu == mutu) {
				return jenis;
			}
		}
		return null;
	}

	/** The weapon of {@code stack}, or null. */
	public static @Nullable Jenis dari(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof SenjataItem item)) {
			return null;
		}
		return jenis(item.bentuk, item.mutu);
	}

	/** A new stack of one weapon (made on demand). */
	public static ItemStack stack(Bentuk bentuk, Mutu mutu) {
		Jenis jenis = jenis(bentuk, mutu);
		return jenis == null ? ItemStack.EMPTY : new ItemStack(jenis.item);
	}

	/** Base NB of a weapon stack (0 when not one). */
	public static long harga(ItemStack stack) {
		Jenis jenis = dari(stack);
		return jenis == null ? 0L : jenis.bentuk.harga(jenis.mutu);
	}

	private static Item daftar(Bentuk bentuk, Mutu mutu) {
		ToolMaterial material = new ToolMaterial(mutu == Mutu.FANA ? BlockTags.INCORRECT_FOR_IRON_TOOL : BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
				mutu.dayaTahan, 6.0F, 0.0F, 14, mutu == Mutu.FANA ? ItemTags.IRON_TOOL_MATERIALS : ItemTags.DIAMOND_TOOL_MATERIALS);
		Item.Properties properties = new Item.Properties().sword(material, bentuk.serangan(mutu) - 1.0F, (float) (bentuk.kecepatan - 4.0D))
				.rarity(mutu.rarity)
				.attributes(atribut(bentuk, mutu));
		return ModItems.register("senjata/" + bentuk.id + "_" + mutu.id, p -> new SenjataItem(p, bentuk, mutu), properties);
	}

	/** Attack, speed and (qiang) reach in the main hand. */
	private static ItemAttributeModifiers atribut(Bentuk bentuk, Mutu mutu) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, bentuk.serangan(mutu) - 1.0D,
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, bentuk.kecepatan - 4.0D,
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		if (bentuk.jangkauan > 0.0D) {
			builder.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(Identifier.fromNamespaceAndPath("kultivator", "senjata_jangkauan"),
					bentuk.jangkauan, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		}
		return builder.build();
	}

	/** A city weapon: tooltip with grade, duel class, and the shape's Sumber line. */
	public static final class SenjataItem extends Item {

		public final Bentuk bentuk;
		public final Mutu mutu;

		public SenjataItem(Item.Properties properties, Bentuk bentuk, Mutu mutu) {
			super(properties);
			this.bentuk = bentuk;
			this.mutu = mutu;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.tempat_ekonomi.senjata.mutu." + mutu.id).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.tempat_ekonomi.senjata.kelas",
					Component.translatable("kultivator.tempat_ekonomi.senjata.kelas." + bentuk.kelasDuel.toLowerCase(Locale.ROOT)))
					.withColor(HartaText.USE_COLOR));
			builder.accept(Component.translatable("kultivator.tempat_ekonomi.senjata." + bentuk.id + ".deskripsi").withColor(HartaText.USE_COLOR));
			if (bentuk.jangkauan > 0.0D) {
				builder.accept(Component.translatable("kultivator.tempat_ekonomi.senjata.jangkauan", HartaText.decimal(bentuk.jangkauan))
						.withColor(HartaText.USE_COLOR));
			}
			builder.accept(Component.translatable("kultivator.tempat_ekonomi.senjata.bukan_natal").withStyle(ChatFormatting.DARK_GRAY,
					ChatFormatting.ITALIC));
			HartaText.sources("kultivator.tempat_ekonomi.senjata." + bentuk.id, builder);
		}
	}

	private SenjataKota() {
	}
}
