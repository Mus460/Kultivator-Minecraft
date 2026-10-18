package dev.kultivator.disciple.power;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.registry.ModItems;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.technique.DaoTechniques;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Benih Api Surgawi, the Heavenly Flame seeds (DESIGN 20.3.16; BTTH: the ranked Heavenly Flames and the Flame Mantra that
 * grows by devouring them). Seeds drop as an item at the victim (blaze 0.5% and stray 0.3%; the Elder Guardian and the
 * Wither 100% on a player's first kill, then 25%; the Ender Dragon always). A Fire-root, Fire-Dao or Nascent Soul disciple
 * absorbs one in a 20 s ritual; without Hu Fa (the master within 8 blocks, meditating or still and sneaking) it fails 40%
 * of the time: 3 minutes of Qi Deviation, the seed comes back. Each flame (max 3, +1 per 9 stars): fire technique damage
 * +20%, melee sets monsters burning for 4 s, immune to fire and lava; coloured flame orbs in its aura.
 */
final class HeavenlyFlames {

	public static final int RITUAL_TICKS = 400;
	public static final double UNGUARDED_FAIL = 0.4D;
	public static final int FAIL_DEVIATION = 3600;
	public static final int BASE_MAX = 3;
	public static final int STARS_PER_EXTRA = 9;
	public static final double FIRE_TECHNIQUE = 0.2D;
	public static final float MELEE_FIRE = 4.0F;
	public static final double HU_FA_RANGE = 8.0D;
	/** Seconds of Hu Fa a ritual needs to count as guarded. */
	public static final int GUARDED_SECONDS = 15;
	/** Module C key: the flame kind of a running ritual (saved, so an interrupted ritual gives its seed back). */
	private static final String RITUAL_SEED = "ritual_seed";

	/** {@code kultivator:flame_kind}: the flame of a seed, 0-4 ({@link PowerIds#FLAME_IDS}). */
	public static final DataComponentType<Integer> FLAME_KIND = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("flame_kind"), DataComponentType.<Integer>builder()
					.persistent(ExtraCodecs.intRange(0, PowerIds.FLAME_IDS.length - 1))
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	public static final Item SEED = ModItems.register("heavenly_flame_seed", SeedItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant().component(FLAME_KIND, 0));

	static void init() {
	}

	static void register() {
		DiscipleHooks.addFeedHandler(HeavenlyFlames::feed);
		DiscipleHooks.addDamageFactor((entity, target, technique) -> {
			if (!technique || !PowerCore.powered(entity) || entity.data().dao() != Dao.FIRE) {
				return 1.0D;
			}
			return 1.0D + FIRE_TECHNIQUE * DisciplePowers.flames(entity.data());
		});
	}

	// ================================================================== seeds

	/** A seed of {@code kind} with its name and model. */
	static ItemStack seed(int kind) {
		int index = Math.max(0, Math.min(PowerIds.FLAME_IDS.length - 1, kind));
		ItemStack stack = new ItemStack(SEED);
		stack.set(FLAME_KIND, index);
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.kultivator.heavenly_flame_seed." + PowerIds.FLAME_IDS[index]));
		stack.set(DataComponents.ITEM_MODEL, Kultivator.id("heavenly_flame_seed_" + PowerIds.FLAME_IDS[index]));
		return stack;
	}

	static int kindOf(ItemStack stack) {
		return stack.getOrDefault(FLAME_KIND, 0);
	}

	static Component flameName(int kind) {
		int index = Math.max(0, Math.min(PowerIds.FLAME_IDS.length - 1, kind));
		return Component.translatable("item.kultivator.heavenly_flame_seed." + PowerIds.FLAME_IDS[index]).withColor(PowerIds.FLAME_RGB[index]);
	}

	/** A monster died: its seed, maybe (DESIGN 20.3.16 drop table). */
	static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!(victim.level() instanceof ServerLevel level)) {
			return;
		}
		Entity killer = source.getEntity();
		ServerPlayer player = killer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
		boolean disciple = killer instanceof DiscipleEntity d && d.role() == DiscipleEntity.Role.DISCIPLE;
		double roll = victim.getRandom().nextDouble();
		int kind = -1;
		if (victim.getType() == EntityTypes.BLAZE && player != null && roll < 0.005D) {
			kind = PowerIds.FLAME_GREEN_LOTUS;
		} else if (victim.getType() == EntityTypes.STRAY && (player != null || disciple) && roll < 0.003D) {
			kind = PowerIds.FLAME_BONE_CHILLING;
		} else if (victim.getType() == EntityTypes.ELDER_GUARDIAN && player != null && firstOr(player, "seed_sea_heart", roll)) {
			kind = PowerIds.FLAME_SEA_HEART;
		} else if (victim.getType() == EntityTypes.WITHER && player != null && firstOr(player, "seed_fallen_heart", roll)) {
			kind = PowerIds.FLAME_FALLEN_HEART;
		} else if (victim.getType() == EntityTypes.ENDER_DRAGON) {
			kind = PowerIds.FLAME_PURE_LOTUS;
		}
		if (kind < 0) {
			return;
		}
		// The dragon dies over the void's edge: its seed appears at the killer's feet when there is one.
		Vec3 at = kind == PowerIds.FLAME_PURE_LOTUS && player != null && player.level() == level ? player.position().add(0.0D, 0.5D, 0.0D)
				: victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
		ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, seed(kind));
		item.setDefaultPickUpDelay();
		item.setGlowingTag(true);
		item.addTag(NoGrief.TAG);
		item.setPortalCooldown();
		level.addFreshEntity(item);
		PowerCore.sound(level, at, SoundEvents.BLAZE_SHOOT, 1.0F, 0.5F);
		if (player != null) {
			Msg.chat(player, PowerCore.key("flame.dropped", flameName(kind)).withStyle(ChatFormatting.GOLD));
		}
	}

	/** True on the player's first kill of that boss (remembered in the player's data), else 25%. */
	private static boolean firstOr(ServerPlayer player, String key, double roll) {
		CultivationData data = Cultivators.get(player);
		CompoundTag extra = data.extra();
		CompoundTag mine = extra.getCompound(DisciplePowers.EXTRA).orElse(null);
		if (mine == null) {
			mine = new CompoundTag();
			extra.put(DisciplePowers.EXTRA, mine);
		}
		if (!mine.getBooleanOr(key, false)) {
			mine.putBoolean(key, true);
			Cultivators.dirty(player);
			return true;
		}
		return roll < 0.25D;
	}

	// ================================================================== absorbing

	/** Flames absorbed (kinds, in order). */
	static ListTag list(DiscipleData data) {
		return PowerCore.tag(data).getListOrEmpty("flames");
	}

	static boolean hasKind(DiscipleData data, int kind) {
		ListTag list = list(data);
		for (int i = 0; i < list.size(); i++) {
			if (list.getIntOr(i, -1) == kind) {
				return true;
			}
		}
		return false;
	}

	/** Bits per kind owned (synced to the client for the flame orbs). */
	static int kindBits(DiscipleData data) {
		ListTag list = list(data);
		int bits = 0;
		for (int i = 0; i < list.size(); i++) {
			int kind = list.getIntOr(i, -1);
			if (kind >= 0 && kind < PowerIds.FLAME_IDS.length) {
				bits |= 1 << kind;
			}
		}
		return bits;
	}

	/** At most 3 flames, one more per 9 stars (and never more than the five kinds). */
	static int max(DiscipleData data) {
		return Math.min(PowerIds.FLAME_IDS.length, BASE_MAX + DisciplePaths.stars(data) / STARS_PER_EXTRA);
	}

	/** Who can take in a Heavenly Flame: a Fire root or the Fire Dao, or anyone from Nascent Soul. */
	static boolean eligible(DiscipleData data) {
		return (data.rootElements() & Element.FIRE.bit()) != 0 || data.dao() == Dao.FIRE || data.realm().atLeast(Realm.NASCENT_SOUL);
	}

	/** Why the disciple cannot absorb a seed of {@code kind} now (reason key without the prefix), or null. */
	static @Nullable String blocker(DiscipleEntity entity, int kind) {
		DiscipleData data = entity.data();
		if (!eligible(data)) {
			return "why.flame_unfit";
		}
		if (DisciplePowers.live(entity).ritualKind >= 0) {
			return "why.flame_busy";
		}
		if (hasKind(data, kind)) {
			return "why.flame_known";
		}
		if (DisciplePowers.flames(data) >= max(data)) {
			return "why.flame_full";
		}
		if (data.breakingThrough()) {
			return "why.busy";
		}
		return null;
	}

	/** The master feeds a seed: the 20 s ritual begins (the seed is used up now, and comes back on a failure). */
	private static boolean feed(DiscipleEntity entity, ServerPlayer master, ItemStack stack) {
		if (!stack.is(SEED)) {
			return false;
		}
		int kind = kindOf(stack);
		String why = blocker(entity, kind);
		if (why != null) {
			Msg.chat(master, PowerCore.key("flame.refuse", PowerCore.name(entity), PowerCore.key(why)).withColor(0xFF9A7A));
			return true;
		}
		if (!master.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		DisciplePowers.Live live = DisciplePowers.live(entity);
		live.ritualKind = kind;
		live.ritualEnd = PowerCore.now(entity) + RITUAL_TICKS;
		live.ritualGuarded = 0;
		live.ritualMaster = master.position();
		// Saved with the disciple: a ritual cut off by an unload, a restart or a departure gives the seed back later.
		PowerCore.edit(entity.data()).putInt(RITUAL_SEED, kind);
		if (entity.level() instanceof ServerLevel level) {
			PowerCore.fx(level, entity.position(), PowerIds.FX_FLAME_RITUAL, kind, RITUAL_TICKS / 20.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.FIRECHARGE_USE, 1.0F, 0.6F);
			PowerCore.sound(level, entity.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 0.6F);
		}
		Msg.chat(master, PowerCore.key("flame.ritual", PowerCore.name(entity), flameName(kind), RITUAL_TICKS / 20).withColor(0xFFB43A));
		return true;
	}

	/** Hu Fa (DESIGN 20.3.18): the master within 8 blocks, meditating, or standing still while sneaking. */
	static boolean guarded(DiscipleEntity entity, @Nullable ServerPlayer master, @Nullable Vec3 lastMasterPos) {
		if (master == null || master.level() != entity.level() || master.distanceToSqr(entity) > HU_FA_RANGE * HU_FA_RANGE) {
			return false;
		}
		if (Cultivators.get(master).meditating()) {
			return true;
		}
		return master.isShiftKeyDown() && (lastMasterPos == null || lastMasterPos.distanceToSqr(master.position()) < 0.04D);
	}

	/** Once a second while a ritual runs: counts Hu Fa, ends it after 20 s. */
	static void tick(DiscipleEntity entity, DisciplePowers.Live live) {
		if (live.ritualKind < 0) {
			settleStaleRitual(entity);
			return;
		}
		ServerPlayer master = entity.owner();
		// Module D's Hu Fa (DESIGN 20.3.18) counts as well: while it holds, the absorption never fails.
		if (guarded(entity, master, live.ritualMaster) || dev.kultivator.disciple.path.HuFa.active(entity)) {
			live.ritualGuarded++;
		}
		live.ritualMaster = master == null ? null : master.position();
		if (PowerCore.now(entity) < live.ritualEnd || !(entity.level() instanceof ServerLevel level)) {
			if (entity.level() instanceof ServerLevel lvl && entity.tickCount % 80 < 20) {
				PowerCore.sound(lvl, entity.position(), SoundEvents.BLAZE_BURN, 0.6F, 0.7F);
			}
			return;
		}
		int kind = live.ritualKind;
		live.ritualKind = -1;
		boolean guarded = live.ritualGuarded >= GUARDED_SECONDS;
		DiscipleData data = entity.data();
		PowerCore.edit(data).remove(RITUAL_SEED);
		if (!guarded && entity.getRandom().nextDouble() < UNGUARDED_FAIL) {
			data.setDeviationTicks(Math.max(data.deviationTicks(), FAIL_DEVIATION));
			PowerCore.fx(level, entity.position(), PowerIds.FX_FLAME_RESULT, kind, 0.0F);
			PowerCore.sound(level, entity.position(), SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.6F);
			returnSeed(entity, master, kind);
			PowerCore.tell(entity, PowerCore.key("flame.fail", PowerCore.name(entity), flameName(kind), FAIL_DEVIATION / 20)
					.withStyle(ChatFormatting.RED));
			entity.refreshSynced();
			return;
		}
		ListTag list = PowerCore.edit(data).getListOrEmpty("flames").copy();
		list.add(IntTag.valueOf(kind));
		PowerCore.edit(data).put("flames", list);
		entity.refreshSynced();
		dev.kultivator.epic.EpicHooks.extra(level, dev.kultivator.epic.EpicIds.EXTRA_FLAME_ABSORBED, master, entity, entity.position(), kind, 0);
		DisciplePowers.syncPower(entity);
		PowerCore.fx(level, entity.position(), PowerIds.FX_FLAME_RESULT, kind, 1.0F);
		PowerCore.sound(level, entity.position(), SoundEvents.BLAZE_SHOOT, 1.0F, 0.4F);
		PowerCore.sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 0.9F);
		PowerCore.tell(entity, PowerCore.key(guarded ? "flame.success_guarded" : "flame.success", PowerCore.name(entity), flameName(kind),
				DisciplePowers.flames(data), max(data)).withColor(PowerIds.FLAME_RGB[kind]));
		if (master != null) {
			Advancements.grant(master, "heavenly_flame");
		}
	}

	/**
	 * The disciple left the world mid-ritual (unload, death, departure): nothing is spawned now (its chunk may be closing).
	 * The {@code ritual_seed} marker saved with it gives the seed back on its next tick ({@link #settleStaleRitual}).
	 */
	static void interrupted(DiscipleEntity entity, DisciplePowers.Live live) {
		live.ritualKind = -1;
	}

	/** A ritual cut off earlier (its marker is still in the data, no ritual runs): the seed comes back now. */
	private static void settleStaleRitual(DiscipleEntity entity) {
		CompoundTag section = PowerCore.tag(entity.data());
		if (!section.contains(RITUAL_SEED)) {
			return;
		}
		int kind = section.getIntOr(RITUAL_SEED, 0);
		PowerCore.edit(entity.data()).remove(RITUAL_SEED);
		ServerPlayer master = entity.owner();
		ItemStack seed = seed(kind);
		if (master != null && master.getInventory().add(seed)) {
			return;
		}
		UUID owner = entity.ownerId();
		if (owner != null) {
			seed = Storehouse.insert(owner, seed);
		}
		if (!seed.isEmpty()) {
			returnSeed(entity, null, seed);
		}
	}

	private static void returnSeed(DiscipleEntity entity, @Nullable ServerPlayer master, int kind) {
		returnSeed(entity, master, seed(kind));
	}

	private static void returnSeed(DiscipleEntity entity, @Nullable ServerPlayer master, ItemStack seed) {
		if (master != null && master.getInventory().add(seed)) {
			return;
		}
		if (entity.level() instanceof ServerLevel level) {
			ItemEntity item = new ItemEntity(level, entity.getX(), entity.getY() + 0.5D, entity.getZ(), seed);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			item.setPortalCooldown();
			level.addFreshEntity(item);
		}
	}

	/** On a melee hit with a flame inside: the struck monster burns for 4 s (entity fire only). */
	static void onMeleeHit(DiscipleEntity entity, LivingEntity target) {
		if (target.isAlive() && DisciplePowers.flames(entity.data()) > 0) {
			DaoTechniques.ignite(target, MELEE_FIRE);
		}
	}

	// ================================================================== the item

	/** A seed: its flame in the tooltip and who can absorb it. */
	public static class SeedItem extends Item {

		public SeedItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
									TooltipFlag flag) {
			int kind = kindOf(stack);
			builder.accept(flameName(kind));
			builder.accept(Component.translatable("kultivator.dpower.flame.tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("kultivator.dpower.flame.tooltip_use").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	private HeavenlyFlames() {
	}
}
