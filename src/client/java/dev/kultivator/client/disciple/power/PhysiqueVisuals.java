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

	/**
	 * The natal treasure looks, made on first use (item components are not bound while renderers are created). Since the
	 * polish pass (DESIGN 27.3) they are our 3D cuboid models: the azure bamboo jian, the parasol (closed on the back,
	 * open over the head as the Eternal Parasol), Bai Xiaochun's turtle-shell wok and the bell, plain, silver and gold.
	 */
	private static final class Stacks {
		private static final ItemStack SWORD = fx("natal_sword", false);
		private static final ItemStack SWORD_SILVER = fx("natal_sword_silver", true);
		private static final ItemStack SWORD_GOLD = fx("natal_sword_gold", true);
		private static final ItemStack PARASOL = model("natal_parasol", false);
		private static final ItemStack PARASOL_SILVER = model("natal_parasol", true);
		private static final ItemStack PARASOL_GOLD = model("natal_parasol_gold", true);
		private static final ItemStack PARASOL_OPEN = fx("natal_parasol_open", false);
		private static final ItemStack PARASOL_OPEN_GOLD = fx("natal_parasol_open_gold", true);
		private static final ItemStack WOK = model("natal_wok", false);
		private static final ItemStack WOK_SILVER = model("natal_wok", true);
		private static final ItemStack WOK_GOLD = model("natal_wok_gold", true);
		private static final ItemStack BELL = fx("natal_bell", false);
		private static final ItemStack BELL_SILVER = fx("natal_bell_silver", true);
		private static final ItemStack BELL_GOLD = fx("natal_bell_gold", true);
		private static final ItemStack QI_SWORD = fx("light_sword", false);
	}

	private static ItemStack fx(String name, boolean glint) {
		ItemStack stack = new ItemStack(Items.STICK);
		stack.set(DataComponents.ITEM_MODEL, Kultivator.id("fx/" + name));
		return glint ? glint(stack) : stack;
	}

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
			case PowerIds.NATAL_PARASOL -> tier >= 3 ? Stacks.PARASOL_GOLD : tier == 2 ? Stacks.PARASOL_SILVER : Stacks.PARASOL;
			case PowerIds.NATAL_WOK -> tier >= 3 ? Stacks.WOK_GOLD : tier == 2 ? Stacks.WOK_SILVER : Stacks.WOK;
			case PowerIds.NATAL_BELL -> tier >= 3 ? Stacks.BELL_GOLD : tier == 2 ? Stacks.BELL_SILVER : Stacks.BELL;
			default -> tier >= 3 ? Stacks.SWORD_GOLD : tier == 2 ? Stacks.SWORD_SILVER : Stacks.SWORD;
		};
	}

	/** The opened parasol held over the head while the Eternal Parasol is out (empty otherwise). */
	public static ItemStack openParasol(int power) {
		if ((power & PowerIds.BIT_NATAL_ORBIT) == 0 || PowerIds.natalForm(power) != PowerIds.NATAL_PARASOL || PowerIds.natalTier(power) <= 0) {
			return ItemStack.EMPTY;
		}
		return PowerIds.natalTier(power) >= 3 ? Stacks.PARASOL_OPEN_GOLD : Stacks.PARASOL_OPEN;
	}

	/** The sword a disciple holds in a sword pose: its natal sword, else a qi sword. */
	public static ItemStack handSword(int power) {
		if (PowerIds.natalTier(power) > 0 && PowerIds.natalForm(power) == PowerIds.NATAL_SWORD) {
			return natalStack(power);
		}
		return Stacks.QI_SWORD;
	}

	// ------------------------------------------------------------------ Divine Clone fade-out (DESIGN 27, plan 6.14 [237])

	/** Per clone entity id: the client game tick its fade-out began and how long it lasts. */
	private static final java.util.Map<Integer, long[]> FADES = new java.util.HashMap<>();

	/**
	 * A Divine Clone starts fading out over {@code ticks} (called by the path effects on {@code PathFxIds.CLONE} with
	 * {@code b = 1}).
	 */
	public static void fadeOut(int entityId, int ticks) {
		net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
		if (level != null) {
			FADES.put(entityId, new long[] {level.getGameTime(), Math.max(1, ticks)});
		}
	}

	/** Like {@link #fadeOut(int, int)} for the clone nearest {@code pos} (within 2 blocks), when the effect has no id. */
	public static void fadeOutNear(net.minecraft.world.phys.Vec3 pos, int ticks) {
		net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
		if (level == null || pos == null) {
			return;
		}
		DiscipleEntity best = null;
		double bestDistance = 4.0D;
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(2.0D))) {
			double d = disciple.position().distanceToSqr(pos);
			if (disciple.syncedRole() == DiscipleEntity.Role.CLONE && d < bestDistance) {
				best = disciple;
				bestDistance = d;
			}
		}
		if (best != null) {
			fadeOut(best.getId(), ticks);
		}
	}

	/** 0..1 how solid a fading clone still is (1 when it is not fading). */
	public static float cloneFadeOut(int entityId, float partialTicks) {
		long[] fade = FADES.get(entityId);
		net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
		if (fade == null || level == null) {
			return 1.0F;
		}
		float since = level.getGameTime() - fade[0] + partialTicks;
		if (since > fade[1] + 40L || since < -20.0F) {
			FADES.remove(entityId);
			return 1.0F;
		}
		return Mth.clamp(1.0F - since / fade[1], 0.0F, 1.0F);
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
		int alpha = translucent(state) ? Math.min(normal, GHOST_ALPHA) : normal;
		return state.cloneFade < 1.0F ? Math.round(alpha * Mth.clamp(state.cloneFade, 0.0F, 1.0F)) : alpha;
	}

	/** The trim colour of the disciple's sect rank (none for candidates, rivals and phantoms). */
	public static int trimColor(DiscipleRenderState state) {
		return RANK_TRIM[Mth.clamp(state.sectRank, 0, RANK_TRIM.length - 1)];
	}

	private PhysiqueVisuals() {
	}
}
