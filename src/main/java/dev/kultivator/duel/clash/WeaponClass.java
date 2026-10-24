package dev.kultivator.duel.clash;

import dev.kultivator.Kultivator;
import dev.kultivator.tempa.ArtifactItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * The weapon classes of the clash layer (DESIGN 35.3.1), shared by server and client. The ordinal is sent in
 * {@code DuelSwing.weaponClass} / {@code DuelFighterSync.weaponClass}: never reorder.
 * <p>
 * Timings are ticks: {@code lightWindup} for a light cut, a heavy cut is charged at least {@code heavyMin} and at most
 * {@code heavyFull} ticks and then swings for {@code heavyRelease} more. {@code postureLight/Heavy} are the L and H of
 * the posture table 35.3.5 for a victim with 100 max posture. {@code parryWindowDelta} narrows the deflect window of a
 * defender holding this class (axe and spear −1). {@code sparkRgb} is the base spark colour.
 */
public enum WeaponClass {
	PEDANG(6, 6, 16, 4, 3.0D, 14.0F, 30.0F, true, 0, 0xFFE7B0),
	PEDANG_BERAT(8, 8, 18, 5, 3.3D, 20.0F, 42.0F, true, 0, 0xFFD08A),
	KAPAK(8, 8, 16, 5, 2.9D, 18.0F, 40.0F, true, -1, 0xFFC070),
	TOMBAK(7, 6, 16, 4, 3.8D, 12.0F, 28.0F, true, -1, 0xFFF0C8),
	GADA(9, 10, 20, 5, 2.8D, 24.0F, 50.0F, false, 0, 0xFFB060),
	TANGAN_KOSONG(4, 4, 10, 3, 2.5D, 6.0F, 14.0F, false, 0, 0xFFFFFF);

	/** The training sword of module A ({@code kultivator:pedang_latih}); looked up lazily, it may not exist yet. */
	private static final Identifier TRAINING_SWORD = Kultivator.id("pedang_latih");
	/** Kultivator maces (also in {@code #minecraft:axes} for tools): a gada fights as GADA, not KAPAK. */
	private static final TagKey<Item> SENJATA_GADA = TagKey.create(Registries.ITEM, Kultivator.id("senjata_gada"));

	public final int lightWindup;
	public final int heavyMin;
	public final int heavyFull;
	public final int heavyRelease;
	public final double reach;
	public final float postureLight;
	public final float postureHeavy;
	public final boolean parriesBlades;
	public final int parryWindowDelta;
	public final int sparkRgb;

	WeaponClass(int lightWindup, int heavyMin, int heavyFull, int heavyRelease, double reach, float postureLight, float postureHeavy,
			boolean parriesBlades, int parryWindowDelta, int sparkRgb) {
		this.lightWindup = lightWindup;
		this.heavyMin = heavyMin;
		this.heavyFull = heavyFull;
		this.heavyRelease = heavyRelease;
		this.reach = reach;
		this.postureLight = postureLight;
		this.postureHeavy = postureHeavy;
		this.parriesBlades = parriesBlades;
		this.parryWindowDelta = parryWindowDelta;
		this.sparkRgb = sparkRgb;
	}

	/** True for the classes with an edge or point that strike sparks (all but the bare hand). */
	public boolean blade() {
		return this != TANGAN_KOSONG;
	}

	/** True for the classes that can meet another blade in a clash or a bind (sword, great sword, axe, spear). */
	public boolean edged() {
		return this == PEDANG || this == PEDANG_BERAT || this == KAPAK || this == TOMBAK;
	}

	public static WeaponClass byOrdinal(int ordinal) {
		WeaponClass[] values = values();
		return ordinal < 0 || ordinal >= values.length ? TANGAN_KOSONG : values[ordinal];
	}

	/** The class of what {@code entity} holds in its main hand (mobs too: a zombie with a sword is a PEDANG). */
	public static WeaponClass of(LivingEntity entity) {
		return entity == null ? TANGAN_KOSONG : of(entity.getMainHandItem());
	}

	/** The class of {@code stack} (35.3.1): forged swords, the training sword, the vanilla tags, the mace, the trident. */
	public static WeaponClass of(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return TANGAN_KOSONG;
		}
		if (ArtifactItem.is(stack)) {
			// Forged artifacts are not in #swords: only the sword form is a blade (the umbrella, wok and bell are not).
			return ArtifactItem.PEDANG.equals(ArtifactItem.form(ArtifactItem.data(stack))) ? PEDANG : TANGAN_KOSONG;
		}
		if (stack.is(Items.MACE) || stack.is(SENJATA_GADA)) {
			return GADA;
		}
		if (stack.is(Items.TRIDENT) || stack.is(ItemTags.SPEARS)) {
			return TOMBAK;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (stack.is(ItemTags.SWORDS) || TRAINING_SWORD.equals(id)) {
			return heavyName(id) ? PEDANG_BERAT : PEDANG;
		}
		if (stack.is(ItemTags.AXES)) {
			return KAPAK;
		}
		return TANGAN_KOSONG;
	}

	/**
	 * Two-handed blades of other mods (Better Combat's claymores and great swords live in {@code #swords}): their item
	 * path names them. Katanas and long swords stay PEDANG.
	 */
	private static boolean heavyName(Identifier id) {
		if (id == null) {
			return false;
		}
		String path = id.getPath().toLowerCase(Locale.ROOT);
		return path.contains("claymore") || path.contains("greatsword") || path.contains("great_sword") || path.contains("zweihander")
				|| path.contains("broadsword") || path.contains("heavy_sword");
	}
}
