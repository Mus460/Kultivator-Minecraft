package dev.kultivator.client.disciple.power;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleRenderState;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.power.PowerIds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * How the disciple renderer draws module C's looks (DESIGN 20.3.22): role tints (candidate, rival, phantom), the
 * translucent spirit body (Void Realm) and Divine Clones, the golden-body glint, the dragon form, the Heretic gate veins
 * and the poison rampage, the robe trim of each sect rank, and the natal treasure's item.
 */
public final class PhysiqueVisuals {

	/** Robe trim per sect rank: grey, white, blue, jade, violet, white-gold (DESIGN 20.3.6). */
	public static final int[] RANK_TRIM = {0x9A9A9A, 0xF2F2F2, 0x4F8FFF, 0x3FBF8A, 0xA060E0, 0xFFF2C0};
	/** Alpha of a translucent body (spirit body, clone, phantom). */
	private static final int GHOST_ALPHA = 140;

	private static final ItemStack SWORD = new ItemStack(Items.IRON_SWORD);
	private static final ItemStack SWORD_SILVER = glint(new ItemStack(Items.IRON_SWORD));
	private static final ItemStack SWORD_GOLD = glint(new ItemStack(Items.GOLDEN_SWORD));
	private static final ItemStack PARASOL = model("natal_parasol", false);
	private static final ItemStack PARASOL_SILVER = model("natal_parasol", true);
	private static final ItemStack PARASOL_GOLD = model("natal_parasol_gold", true);
	private static final ItemStack WOK = model("natal_wok", false);
	private static final ItemStack WOK_SILVER = model("natal_wok", true);
	private static final ItemStack WOK_GOLD = model("natal_wok_gold", true);
	private static final ItemStack BELL = new ItemStack(Items.BELL);
	private static final ItemStack BELL_SILVER = glint(new ItemStack(Items.BELL));

	private static ItemStack glint(ItemStack stack) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static ItemStack model(String name, boolean glint) {
		ItemStack stack = new ItemStack(Items.STICK);
		stack.set(DataComponents.ITEM_MODEL, Kultivator.id(name));
		return glint ? glint(stack) : stack;
	}

	/** The natal treasure's look for the synced power bits (empty when it has none). */
	public static ItemStack natalStack(int power) {
		int tier = PowerIds.natalTier(power);
		if (tier <= 0) {
			return ItemStack.EMPTY;
		}
		return switch (PowerIds.natalForm(power)) {
			case PowerIds.NATAL_PARASOL -> tier >= 3 ? PARASOL_GOLD : tier == 2 ? PARASOL_SILVER : PARASOL;
			case PowerIds.NATAL_WOK -> tier >= 3 ? WOK_GOLD : tier == 2 ? WOK_SILVER : WOK;
			case PowerIds.NATAL_BELL -> tier >= 2 ? BELL_SILVER : BELL;
			default -> tier >= 3 ? SWORD_GOLD : tier == 2 ? SWORD_SILVER : SWORD;
		};
	}

	/** True when the body is drawn see-through: the Void Realm spirit body, a clone, a phantom. */
	public static boolean translucent(DiscipleRenderState state) {
		return state.role == DiscipleEntity.Role.CLONE.ordinal() || state.role == DiscipleEntity.Role.PHANTOM.ordinal()
				|| state.activity == DiscipleEntity.ACTIVITY_VOID_BODY;
	}

	/** The model tint (ARGB; -1 for none) of module C's looks, or {@code fallback} when none applies. */
	public static int modelTint(DiscipleRenderState state, int fallback) {
		float pulse = 0.5F + 0.5F * Mth.sin(state.ageInTicks * 0.2F);
		if (state.role == DiscipleEntity.Role.PHANTOM.ordinal()) {
			return ARGB.color(GHOST_ALPHA + 40, 70, 55, 95);
		}
		if (state.role == DiscipleEntity.Role.CLONE.ordinal()) {
			return ARGB.color(GHOST_ALPHA, 205, 230, 255);
		}
		if (state.activity == DiscipleEntity.ACTIVITY_VOID_BODY) {
			return ARGB.color(GHOST_ALPHA - 10, 190, 245, 255);
		}
		if (fallback != -1) {
			return fallback;
		}
		if (state.role == DiscipleEntity.Role.RIVAL.ordinal()) {
			return ARGB.color(255, 255, 222, 210);
		}
		if (state.role == DiscipleEntity.Role.CANDIDATE.ordinal()) {
			return ARGB.color(255, 238, 236, 226);
		}
		int power = state.power;
		if ((power & PowerIds.BIT_RAMPAGE) != 0) {
			boolean heretic = DiscipleEntity.talentPhysique(state.talent) == PowerIds.PHYSIQUE_HERETIC;
			return heretic ? ARGB.color(255, 255, (int) Mth.lerp(pulse, 150.0F, 200.0F), (int) Mth.lerp(pulse, 150.0F, 200.0F))
					: ARGB.color(255, (int) Mth.lerp(pulse, 200.0F, 235.0F), 190, 255);
		}
		if ((power & PowerIds.BIT_DRAGON) != 0) {
			return ARGB.color(255, 235, 200, 255);
		}
		if ((power & PowerIds.BIT_GOLD) != 0) {
			return ARGB.color(255, 255, (int) Mth.lerp(pulse, 236.0F, 250.0F), (int) Mth.lerp(pulse, 185.0F, 225.0F));
		}
		return -1;
	}

	/** Robe cloth alpha for the current look (see-through bodies wear see-through robes). */
	public static int clothAlpha(DiscipleRenderState state, int normal) {
		return translucent(state) ? Math.min(normal, GHOST_ALPHA) : normal;
	}

	/** The trim colour of the disciple's sect rank (none for candidates, rivals and phantoms). */
	public static int trimColor(DiscipleRenderState state) {
		return RANK_TRIM[Mth.clamp(state.sectRank, 0, RANK_TRIM.length - 1)];
	}

	private PhysiqueVisuals() {
	}
}
