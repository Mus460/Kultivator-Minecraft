package dev.kultivator.disciple.power;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Pusaka Natal, the natal treasure and its spirit enhancement (DESIGN 20.3.17; A Will Eternal ch. 5, 29-38, 637: Bai
 * Xiaochun's folds of spirit enhancement fuelled by the three-coloured Spirit Tail flame, the Eternal Parasol at its
 * fourteenth enhancement; Fanren: Han Li's 72 swords are his life-bound natal treasure). A virtual treasure (no item) in
 * {@code extra.power.natal}: the form is chosen once from Foundation (1 diamond + 8 Bulu Ekor Roh, the master inside the
 * hall territory); fold n costs n feathers and n x 40 pool PK, succeeds with {@code 0.95 - 0.07 (n - 1)} (+0.15 Jari
 * Penempa Roh, +0.05 Fire Dao, +0.05 hall level 5+, at least 0.10) and loses a fold on a failure from fold 3. Each fold
 * +5% melee and technique damage; fold 3 silver, fold 9 gold and the form's ability, fold 14 "Payung Abadi" (the ability
 * doubles and the treasure orbits).
 */
final class NatalTreasure {

	public static final int REFINE_DIAMONDS = 1;
	public static final int REFINE_FEATHERS = 8;
	public static final int FOLD_PK = 40;
	public static final int MAX_FOLDS = 18;
	public static final double BASE_CHANCE = 0.95D;
	public static final double CHANCE_STEP = 0.07D;
	public static final double ENHANCER_BONUS = 0.15D;
	public static final double FIRE_BONUS = 0.05D;
	public static final double HALL_BONUS = 0.05D;
	public static final double MIN_CHANCE = 0.10D;
	/** A failed attempt at this fold or a later one loses one fold (DESIGN 20.3.17: "a failure at n >= 3"). */
	public static final int LOSS_FROM = 3;
	public static final double FOLD_DAMAGE = 0.05D;
	public static final int SILVER = 3;
	public static final int GOLD = 9;
	public static final int ETERNAL = 14;
	public static final double SWORD_TECHNIQUE = 0.10D;
	public static final int PARASOL_INTERVAL = 60;
	public static final double PARASOL_RADIUS = 4.0D;
	public static final int PARASOL_TICKS = 200;
	public static final double WOK_GUARD = 0.8D;
	public static final double WOK_GUARD_ETERNAL = 0.6D;
	public static final double BELL_RADIUS = 6.0D;
	public static final int BELL_TICKS = 60;

	/** Bulu Ekor Roh (module B); a vanilla feather stands in when that item does not exist. */
	private static final Identifier FEATHER = Kultivator.id("spirit_tail_feather");

	static void register() {
		DiscipleHooks.addDamageFactor((entity, target, technique) -> {
			if (!PowerCore.powered(entity)) {
				return 1.0D;
			}
			DiscipleData data = entity.data();
			int folds = folds(data);
			if (form(data) < 0 || folds <= 0) {
				return 1.0D;
			}
			double factor = 1.0D + FOLD_DAMAGE * folds;
			if (technique && form(data) == PowerIds.NATAL_SWORD && folds >= GOLD) {
				factor *= 1.0D + SWORD_TECHNIQUE * (folds >= ETERNAL ? 2.0D : 1.0D);
			}
			return factor;
		});
		DiscipleHooks.addHurtFactor((entity, source) -> {
			if (!PowerCore.powered(entity)) {
				return 1.0D;
			}
			DiscipleData data = entity.data();
			if (form(data) != PowerIds.NATAL_WOK || folds(data) < GOLD) {
				return 1.0D;
			}
			// Bai Xiaochun's turtle-shell wok.
			return folds(data) >= ETERNAL ? WOK_GUARD_ETERNAL : WOK_GUARD;
		});
	}

	// ================================================================== reads

	/** The form (-1 before refining). */
	static int form(DiscipleData data) {
		CompoundTag natal = PowerCore.tag(data).getCompoundOrEmpty("natal");
		return natal.contains("form") ? Math.max(0, Math.min(3, natal.getIntOr("form", 0))) : -1;
	}

	static int folds(DiscipleData data) {
		return Math.max(0, PowerCore.tag(data).getCompoundOrEmpty("natal").getIntOr("folds", 0));
	}

	static int maxFolds(DiscipleData data) {
		return MAX_FOLDS + (PowerCore.talent(data, PowerIds.TALENT_SPIRIT_ENHANCER) ? 1 : 0);
	}

	/** The chance of enhancing fold {@code n}. */
	static double chance(DiscipleData data, @Nullable UUID owner, int n) {
		double chance = BASE_CHANCE - CHANCE_STEP * (n - 1);
		if (PowerCore.talent(data, PowerIds.TALENT_SPIRIT_ENHANCER)) {
			chance += ENHANCER_BONUS;
		}
		if (data.dao() == Dao.FIRE) {
			chance += FIRE_BONUS;
		}
		if (owner != null && Halls.level(owner) >= 5) {
			chance += HALL_BONUS;
		}
		return Math.max(MIN_CHANCE, Math.min(1.0D, chance));
	}

	/** Synced look: tier 1 plain, 2 silver, 3 gold (0 none). */
	static int tier(DiscipleData data) {
		if (form(data) < 0) {
			return 0;
		}
		int folds = folds(data);
		return folds >= GOLD ? 3 : folds >= SILVER ? 2 : 1;
	}

	static Item feather() {
		return BuiltInRegistries.ITEM.containsKey(FEATHER) ? BuiltInRegistries.ITEM.getValue(FEATHER) : Items.FEATHER;
	}

	// ================================================================== refining and enhancing

	/** Why the form cannot be refined now (reason key without the prefix), or null. */
	static @Nullable String refineBlocker(DiscipleEntity entity, ServerPlayer master) {
		DiscipleData data = entity.data();
		if (form(data) >= 0) {
			return "why.natal_has";
		}
		if (!data.realm().atLeast(Realm.FOUNDATION)) {
			return "why.natal_realm";
		}
		if (!Halls.inTerritory(master.getUUID(), master.level(), master.position())) {
			return "why.natal_territory";
		}
		if (!master.hasInfiniteMaterials() && (count(master, Items.DIAMOND) < REFINE_DIAMONDS || count(master, feather()) < REFINE_FEATHERS)) {
			return "why.natal_materials";
		}
		return null;
	}

	/** Refines the natal treasure in {@code form}; null on success, else a reason key. */
	static @Nullable String refine(DiscipleEntity entity, ServerPlayer master, int form) {
		String why = refineBlocker(entity, master);
		if (why != null) {
			return why;
		}
		int chosen = Math.max(0, Math.min(3, form));
		if (!master.hasInfiniteMaterials()) {
			take(master, Items.DIAMOND, REFINE_DIAMONDS);
			take(master, feather(), REFINE_FEATHERS);
		}
		CompoundTag natal = PowerCore.editSub(entity.data(), "natal");
		natal.putInt("form", chosen);
		natal.putInt("folds", 0);
		DisciplePowers.syncPower(entity);
		if (entity.level() instanceof ServerLevel level) {
			PowerCore.fx(level, entity.position(), PowerIds.FX_NATAL_REFINE, chosen, 0.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.ANVIL_USE, 0.8F, 1.2F);
			PowerCore.sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F);
		}
		Msg.chat(master, PowerCore.key("natal.refined", PowerCore.name(entity), PowerCore.key("natal.form." + PowerIds.NATAL_IDS[chosen]))
				.withColor(0xFFD54A));
		return null;
	}

	/** Why the next fold cannot be tried now (reason key without the prefix), or null. */
	static @Nullable String enhanceBlocker(DiscipleEntity entity, ServerPlayer master) {
		DiscipleData data = entity.data();
		if (form(data) < 0) {
			return "why.natal_none";
		}
		int n = folds(data) + 1;
		if (n > maxFolds(data)) {
			return "why.natal_max";
		}
		if (!master.hasInfiniteMaterials() && count(master, feather()) < n) {
			return "why.natal_feathers";
		}
		return null;
	}

	/** One fold of spirit enhancement; null when it was tried (success or not), else a reason key. */
	static @Nullable String enhance(DiscipleEntity entity, ServerPlayer master) {
		String why = enhanceBlocker(entity, master);
		if (why != null) {
			return why;
		}
		DiscipleData data = entity.data();
		int n = folds(data) + 1;
		if (!Halls.spendPool(master.getUUID(), n * FOLD_PK) && !master.hasInfiniteMaterials()) {
			return "why.natal_pool";
		}
		if (!master.hasInfiniteMaterials()) {
			take(master, feather(), n);
		}
		boolean success = entity.getRandom().nextDouble() < chance(data, master.getUUID(), n);
		CompoundTag natal = PowerCore.editSub(data, "natal");
		ServerLevel level = entity.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		if (success) {
			natal.putInt("folds", n);
			if (level != null) {
				PowerCore.fx(level, entity.position(), PowerIds.FX_NATAL_FOLD, n, 1.0F);
				PowerCore.sound(level, entity.position(), SoundEvents.ANVIL_USE, 0.8F, 1.0F + 0.03F * n);
				PowerCore.sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F + 0.04F * n);
			}
			Msg.chat(master, PowerCore.key("natal.fold_ok", PowerCore.name(entity), n).withColor(n >= GOLD ? 0xFFD54A : n >= SILVER ? 0xDDE6F0 : 0xC8D8C0));
			if (n == SILVER || n == GOLD || n == ETERNAL) {
				Msg.chat(master, PowerCore.key("natal.milestone." + n, PowerCore.name(entity),
						PowerCore.key("natal.form." + PowerIds.NATAL_IDS[form(data)])).withStyle(ChatFormatting.GOLD));
			}
			if (n >= GOLD) {
				Advancements.grant(master, "natal_fold_9");
			}
			if (n >= ETERNAL) {
				Advancements.grant(master, "payung_abadi");
			}
		} else {
			boolean lost = n >= LOSS_FROM;
			if (lost) {
				natal.putInt("folds", n - 2);
			}
			if (level != null) {
				// Smoke and a pop, nothing else: no block, no explosion.
				PowerCore.fx(level, entity.position(), PowerIds.FX_NATAL_FOLD, folds(data), 0.0F);
				PowerCore.sound(level, entity.position(), SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);
				PowerCore.sound(level, entity.position(), SoundEvents.ITEM_BREAK.value(), 0.8F, 0.8F);
			}
			Msg.chat(master, PowerCore.key(lost ? "natal.fold_lost" : "natal.fold_fail", PowerCore.name(entity), n, folds(data))
					.withColor(0xFF9A7A));
		}
		DisciplePowers.syncPower(entity);
		return null;
	}

	// ================================================================== abilities

	/** Once a second: the Parasol shelters (every 60 s), the Bell rings on each new cast. */
	static void tick(DiscipleEntity entity, DisciplePowers.Live live) {
		DiscipleData data = entity.data();
		int form = form(data);
		int folds = folds(data);
		int casts = data.techniqueCasts();
		boolean newCast = live.lastCasts >= 0 && casts > live.lastCasts;
		live.lastCasts = casts;
		if (form < 0 || folds < GOLD || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		boolean eternal = folds >= ETERNAL;
		ServerPlayer master = entity.owner();
		if (form == PowerIds.NATAL_PARASOL && ++live.parasolSeconds >= PARASOL_INTERVAL) {
			live.parasolSeconds = 0;
			int amplifier = eternal ? 3 : 1;
			if (master != null && master.level() == level && master.distanceToSqr(entity) <= PARASOL_RADIUS * PARASOL_RADIUS) {
				master.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, PARASOL_TICKS, amplifier), entity);
			}
			UUID owner = entity.ownerId();
			for (DiscipleEntity ally : level.getEntitiesOfClass(DiscipleEntity.class, entity.getBoundingBox().inflate(PARASOL_RADIUS),
					other -> other.isAlive() && owner != null && owner.equals(other.ownerId()) && PowerCore.powered(other))) {
				ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, PARASOL_TICKS, amplifier), entity);
			}
			PowerCore.fx(level, entity.position(), PowerIds.FX_PAYUNG, eternal ? 1 : 0, (float) PARASOL_RADIUS);
			PowerCore.sound(level, entity.position(), SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.0F, 0.8F);
		}
		if (form == PowerIds.NATAL_BELL && newCast && master != null) {
			int amplifier = eternal ? 3 : 1;
			boolean any = false;
			for (LivingEntity victim : PowerCore.hostiles(entity, master, entity.position(), BELL_RADIUS)) {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BELL_TICKS, amplifier), entity);
				any = true;
			}
			if (any) {
				PowerCore.fx(level, entity.position(), PowerIds.FX_LONCENG, eternal ? 1 : 0, (float) BELL_RADIUS);
				PowerCore.sound(level, entity.position(), SoundEvents.BELL_BLOCK, 1.0F, eternal ? 0.6F : 0.9F);
			}
		}
	}

	// ================================================================== the master's inventory

	private static int count(ServerPlayer player, Item item) {
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void take(ServerPlayer player, Item item, int amount) {
		Inventory inventory = player.getInventory();
		int left = amount;
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				int used = Math.min(left, stack.getCount());
				stack.shrink(used);
				left -= used;
			}
		}
		inventory.setChanged();
	}

	/** Info for the Pusaka tab. */
	static CompoundTag info(DiscipleEntity entity, ServerPlayer master) {
		DiscipleData data = entity.data();
		CompoundTag tag = new CompoundTag();
		int form = form(data);
		int folds = folds(data);
		tag.putInt("form", form);
		tag.putInt("folds", folds);
		tag.putInt("max_folds", maxFolds(data));
		int n = folds + 1;
		tag.putInt("next_feathers", n);
		tag.putInt("next_pk", n * FOLD_PK);
		tag.putDouble("chance", chance(data, master.getUUID(), n));
		tag.putBoolean("loss_risk", n >= LOSS_FROM);
		String refine = refineBlocker(entity, master);
		tag.putString("refine_reason", refine == null ? "" : DisciplePowers.PREFIX + refine);
		String enhance = enhanceBlocker(entity, master);
		tag.putString("enhance_reason", enhance == null ? "" : DisciplePowers.PREFIX + enhance);
		tag.putInt("feathers", count(master, feather()));
		tag.putInt("diamonds", count(master, Items.DIAMOND));
		tag.putBoolean("creative", master.hasInfiniteMaterials());
		tag.putString("feather_item", BuiltInRegistries.ITEM.getKey(feather()).toString());
		return tag;
	}

	private NatalTreasure() {
	}
}
