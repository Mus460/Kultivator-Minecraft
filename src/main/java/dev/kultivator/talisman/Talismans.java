package dev.kultivator.talisman;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.cultivation.VeinCompassItem;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Rank;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.registry.ModItems;
import dev.kultivator.technique.DaoTechniques;
import dev.kultivator.technique.Techniques;
import dev.kultivator.technique.spec.DamageContext;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * P7-kultivasi (DESIGN 19.3.6): Jimat, talismans that anyone can use, inscribed on blank talisman paper ({@link Inscribe}).
 * Offensive talismans hurt only what {@code Targeting} allows, through {@code Techniques.hurt(user,
 * DamageContext.legacy(user, target, amount))}, so the amount passes the choke point unchanged. Talisman damage
 * {@code T(p) = 4 + 2.5 × power}. Items, particles and entity effects only: no block is ever touched (fire on entities
 * only, through the guarded {@code DaoTechniques.ignite}; lightning is a particle bolt).
 * <p>
 * Sumber: RMJ ch. 242, 765, 1346, 222; AWE ch. 51; RI ch. 12 and 31 (talismans as consumables and a craft; anyone, even
 * a Mortal, can use one). Each talisman's own source is on its item.
 */
public final class Talismans {

	/** {@code extra.talisman}: {@code xp}, {@code made}, {@code ret}. */
	public static final String EXTRA_KEY = "talisman";
	/** Talisman damage {@code T(p) = DAMAGE_BASE + DAMAGE_PER_POWER × power}. */
	public static final double DAMAGE_BASE = 4.0D;
	public static final double DAMAGE_PER_POWER = 2.5D;
	/** Thunder: range, chain radius and the chained share. */
	public static final double THUNDER_RANGE = 24.0D;
	public static final double THUNDER_CHAIN_RADIUS = 5.0D;
	public static final double THUNDER_CHAIN_SHARE = 0.5D;
	/** Fire Cloud: cone length, half angle (cos 30°), damage share and burn seconds. */
	public static final double FIRE_RANGE = 6.0D;
	public static final double FIRE_CONE_COS = 0.866D;
	public static final double FIRE_SHARE = 0.75D;
	public static final float FIRE_SECONDS = 5.0F;
	/** Shield: seconds and the largest amplifier ({@code min(4, power / 2)}). */
	public static final int SHIELD_SECONDS = 60;
	public static final int SHIELD_MAX_AMPLIFIER = 4;
	/** Binding: range, base and per-power seconds, the boss share, Slowness VII and Weakness I. */
	public static final double BINDING_RANGE = 16.0D;
	public static final double BINDING_SECONDS = 3.0D;
	public static final double BINDING_PER_POWER = 0.5D;
	public static final double BINDING_BOSS_SHARE = 0.25D;
	/** Thousand Mile: Speed II for this long per use. */
	public static final int THOUSAND_MILE_SECONDS = 60;
	/** Limpidity: base and per-power seconds of invisibility, the shake-off radius and the grace of a recent hit. */
	public static final int LIMPIDITY_SECONDS = 10;
	public static final int LIMPIDITY_PER_POWER = 2;
	public static final double LIMPIDITY_RADIUS = 12.0D;
	public static final int LIMPIDITY_GRACE_TICKS = 60;
	/** Tribulation Ward: the next strike's factor and the wards per tribulation. */
	public static final double WARD_FACTOR = 0.5D;
	public static final int WARDS_PER_TRIBULATION = 2;
	/** The shared cooldown group of all talismans. */
	public static final Identifier COOLDOWN_GROUP = Kultivator.id("talisman");
	public static final float SHARED_COOLDOWN_SECONDS = 1.0F;

	/** {@code kultivator:talisman}: power, inscriber, uses (saved and synced). */
	public static final DataComponentType<TalismanData> TALISMAN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("talisman"), DataComponentType.<TalismanData>builder()
					.persistent(TalismanData.CODEC)
					.networkSynchronized(TalismanData.STREAM_CODEC)
					.build());

	/** Kertas Jimat Kosong / Blank Talisman. */
	public static final Item BLANK = ModItems.register("blank_talisman", Inscribe.BlankItem::new, new Item.Properties().stacksTo(64));

	private static final Map<TalismanKind, Item> ITEMS = registerTalismans();

	/**
	 * The talisman state of one running tribulation (from the {@code Tribulation.Listener}): strikes fallen and in total,
	 * wards used and the strike (1-based) the pending ward softens, 0 for none.
	 */
	private static final class Ward {
		int done;
		int total;
		int used;
		int pending;
	}

	private static final Map<UUID, Ward> WARDS = new HashMap<>();
	/** Player -> game time the limpidity ends. */
	private static final Map<UUID, Long> LIMPID = new HashMap<>();

	private static Map<TalismanKind, Item> registerTalismans() {
		Map<TalismanKind, Item> items = new EnumMap<>(TalismanKind.class);
		for (TalismanKind kind : TalismanKind.values()) {
			items.put(kind, ModItems.register(kind.itemId(), properties -> new TalismanItem(properties, kind), new Item.Properties()
					.stacksTo(16)
					.rarity(Rarity.UNCOMMON)
					.component(TALISMAN, new TalismanData(1, "", kind.uses()))
					.component(DataComponents.USE_COOLDOWN, new UseCooldown(SHARED_COOLDOWN_SECONDS, Optional.of(COOLDOWN_GROUP)))));
		}
		return items;
	}

	public static void init() {
		ReturnTalisman.init();
		Tribulation.addStrikeFactorProvider(Talismans::wardFactor);
		Tribulation.addListener(new Tribulation.Listener() {
			@Override
			public void onOmen(ServerPlayer p, Rank t) {
				Ward ward = new Ward();
				ward.total = Tribulation.strikeCount(t);
				WARDS.put(p.getUUID(), ward);
			}

			@Override
			public void onStrike(ServerPlayer p, Rank t, int index, int total) {
				Ward ward = WARDS.computeIfAbsent(p.getUUID(), key -> new Ward());
				ward.done = Math.max(ward.done, index);
				ward.total = total;
				if (ward.pending > 0 && ward.pending <= index) {
					ward.pending = 0;
				}
			}

			@Override
			public void onFinish(ServerPlayer p, Rank t, @Nullable Tribulation.Result result, int strikesDone, boolean test) {
				WARDS.remove(p.getUUID());
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			// Attacking ends the Limpidity Talisman.
			if (!LIMPID.isEmpty() && source.getEntity() instanceof ServerPlayer attacker && entity != attacker
					&& LIMPID.containsKey(attacker.getUUID())) {
				endLimpidity(attacker, true);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			WARDS.remove(player.getUUID());
			LIMPID.remove(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WARDS.clear();
			LIMPID.clear();
		});
	}

	/** Every server tick for each player: the Limpidity Talisman shakes off monsters every 20 ticks. */
	public static void tick(ServerPlayer player, CultivationData data) {
		if (LIMPID.isEmpty() || player.tickCount % 20 != 0) {
			return;
		}
		Long end = LIMPID.get(player.getUUID());
		if (end == null) {
			return;
		}
		if (player.level().getGameTime() >= end || !player.hasEffect(MobEffects.INVISIBILITY) || !player.isAlive()) {
			LIMPID.remove(player.getUUID());
			return;
		}
		shakeOff(player);
	}

	/** The talismans of {@code uji kit}: the 8 kinds at power 5, 16 blanks, the vein compass. */
	public static List<ItemStack> kitItems() {
		List<ItemStack> list = new ArrayList<>();
		for (TalismanKind kind : TalismanKind.values()) {
			list.add(create(kind, 5, "Uji"));
		}
		list.add(new ItemStack(BLANK, 16));
		list.add(new ItemStack(VeinCompassItem.ITEM));
		return list;
	}

	// ================================================================== stacks

	/** The item of {@code kind}. */
	public static Item item(TalismanKind kind) {
		return ITEMS.get(kind);
	}

	/** A talisman of {@code kind} with {@code power} (0-9), written by {@code inscriber}, with all its uses. */
	public static ItemStack create(TalismanKind kind, int power, String inscriber) {
		ItemStack stack = new ItemStack(item(kind));
		stack.set(TALISMAN, new TalismanData(power, inscriber, kind.uses()));
		if (power >= 7) {
			stack.set(DataComponents.RARITY, Rarity.EPIC);
		} else if (power >= 4) {
			stack.set(DataComponents.RARITY, Rarity.RARE);
		}
		return stack;
	}

	/** The talisman data of a stack (power 1 and all uses when none is set). */
	public static TalismanData data(ItemStack stack) {
		TalismanData data = stack.get(TALISMAN);
		if (data != null) {
			return data;
		}
		int uses = stack.getItem() instanceof TalismanItem talisman ? talisman.kind().uses() : 1;
		return new TalismanData(1, "", uses);
	}

	/** {@code T(p) = 4 + 2.5 × power}. */
	public static double damage(int power) {
		return DAMAGE_BASE + DAMAGE_PER_POWER * power;
	}

	/** The numbers of a kind's effect line in the tooltip. */
	static Object[] effectArgs(TalismanKind kind, int power) {
		return switch (kind) {
			case THUNDER -> new Object[]{Msg.number(damage(power)), Msg.number(damage(power) * THUNDER_CHAIN_SHARE)};
			case FIRE_CLOUD -> new Object[]{Msg.number(damage(power) * FIRE_SHARE), (int) FIRE_SECONDS};
			case SHIELD -> new Object[]{shieldAmplifier(power) + 1, SHIELD_SECONDS};
			case BINDING -> new Object[]{Msg.number(bindingSeconds(power))};
			case LIMPIDITY -> new Object[]{limpiditySeconds(power)};
			case THOUSAND_MILE -> new Object[]{THOUSAND_MILE_SECONDS};
			default -> new Object[0];
		};
	}

	static int shieldAmplifier(int power) {
		return Math.min(SHIELD_MAX_AMPLIFIER, power / 2);
	}

	static double bindingSeconds(int power) {
		return BINDING_SECONDS + BINDING_PER_POWER * power;
	}

	static int limpiditySeconds(int power) {
		return LIMPIDITY_SECONDS + LIMPIDITY_PER_POWER * power;
	}

	/** Spends one use or one talisman of the stack; the rest of a split stack of multi-use talismans goes to the player. */
	static ItemStack spend(ServerPlayer player, ItemStack stack) {
		if (player.hasInfiniteMaterials()) {
			return stack;
		}
		TalismanData data = data(stack);
		if (data.uses() > 1) {
			if (stack.getCount() == 1) {
				stack.set(TALISMAN, data.withUses(data.uses() - 1));
				return stack;
			}
			stack.shrink(1);
			ItemStack rest = stack.copyWithCount(1);
			rest.set(TALISMAN, data.withUses(data.uses() - 1));
			give(player, rest);
			return stack;
		}
		stack.shrink(1);
		return stack;
	}

	/** Puts a stack into the inventory or drops it at the feet (block-safe item entity). */
	public static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty() || player.getInventory().add(stack) || stack.isEmpty()) {
			return;
		}
		ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
		item.setDefaultPickUpDelay();
		item.addTag(NoGrief.TAG);
		item.setPortalCooldown();
		player.level().addFreshEntity(item);
	}

	/** The attached {@code extra.talisman} compound. */
	public static CompoundTag section(CultivationData data) {
		CompoundTag extra = data.extra();
		CompoundTag tag = extra.getCompoundOrEmpty(EXTRA_KEY);
		if (!extra.contains(EXTRA_KEY)) {
			extra.put(EXTRA_KEY, tag);
		}
		return tag;
	}

	// ================================================================== activation

	/** Why {@code kind} cannot be used right now (checked when the use starts), or null. */
	static @Nullable Component refusal(ServerPlayer player, TalismanKind kind) {
		if (kind == TalismanKind.RETURN) {
			return ReturnTalisman.refusal(player);
		}
		if (kind == TalismanKind.SHIELD && Tribulation.isActive(player)) {
			// Absorption would soak every strike; the capped Tribulation Ward is the tribulation's talisman.
			return Component.translatable("kultivator.talismans.shield.tribulation");
		}
		if (kind == TalismanKind.TRIBULATION_WARD) {
			Ward ward = WARDS.get(player.getUUID());
			if (!Tribulation.isActive(player) || ward == null || ward.total <= 0 || ward.done >= ward.total) {
				return Component.translatable("kultivator.talismans.ward.no_tribulation");
			}
			if (ward.pending > 0) {
				return Component.translatable("kultivator.talismans.ward.pending");
			}
			if (ward.used >= WARDS_PER_TRIBULATION) {
				return Component.translatable("kultivator.talismans.ward.limit", WARDS_PER_TRIBULATION);
			}
		}
		return null;
	}

	/** Applies the talisman's effect at the end of its use; true when it was used (and is spent). */
	static boolean activate(ServerPlayer player, ItemStack stack, TalismanKind kind) {
		Component why = refusal(player, kind);
		if (why != null) {
			Msg.actionBar(player, why.copy().withColor(0xFF9A7A));
			return false;
		}
		TalismanData data = data(stack);
		int power = data.power();
		boolean used = switch (kind) {
			case THUNDER -> thunder(player, power);
			case FIRE_CLOUD -> fireCloud(player, power);
			case SHIELD -> shield(player, power);
			case BINDING -> binding(player, power);
			case RETURN -> ReturnTalisman.finish(player);
			case TRIBULATION_WARD -> ward(player);
			case THOUSAND_MILE -> thousandMile(player);
			case LIMPIDITY -> limpidity(player, power);
		};
		if (used) {
			ServerLevel level = player.level();
			Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6D)).subtract(0.0D, 0.3D, 0.0D);
			Fx.send(level, hand, FxType.TALISMAN, TalismanFxIds.ACTIVATE, kind.ordinal(), 0.0F);
			level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35F, 1.6F);
		}
		return used;
	}

	private static boolean thunder(ServerPlayer player, int power) {
		LivingEntity target = Targeting.lookedAtHostile(player, THUNDER_RANGE);
		if (target == null) {
			Msg.actionBar(player, Component.translatable("kultivator.talismans.no_target", (int) THUNDER_RANGE).withColor(0xFF9A7A));
			return false;
		}
		ServerLevel level = player.level();
		double damage = damage(power);
		Vec3 from = player.getEyePosition().add(0.0D, 0.6D, 0.0D);
		Vec3 at = target.getBoundingBox().getCenter();
		Fx.send(level, from, at, FxType.TALISMAN, TalismanFxIds.THUNDER, 0, 0.0F);
		Techniques.hurt(player, DamageContext.legacy(player, target, damage));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.4F);
		for (LivingEntity next : Targeting.hostilesAround(player, target.position(), THUNDER_CHAIN_RADIUS)) {
			if (next != target && next.isAlive()) {
				Fx.send(level, at, next.getBoundingBox().getCenter(), FxType.TALISMAN, TalismanFxIds.THUNDER, 1, 0.0F);
				Techniques.hurt(player, DamageContext.legacy(player, next, damage * THUNDER_CHAIN_SHARE));
				break;
			}
		}
		return true;
	}

	private static boolean fireCloud(ServerPlayer player, int power) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double damage = damage(power) * FIRE_SHARE;
		for (LivingEntity target : Targeting.hostilesAround(player, eye, FIRE_RANGE, false)) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
			if (to.lengthSqr() < 1.0E-4D || to.normalize().dot(look) < FIRE_CONE_COS) {
				continue;
			}
			DaoTechniques.ignite(target, FIRE_SECONDS);
			Techniques.hurt(player, DamageContext.legacy(player, target, damage).precise(false));
		}
		Fx.send(level, eye.subtract(0.0D, 0.3D, 0.0D), eye.add(look.scale(FIRE_RANGE)), FxType.TALISMAN, TalismanFxIds.FIRE_CLOUD, 0,
				(float) FIRE_RANGE);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 0.8F);
		return true;
	}

	private static boolean shield(ServerPlayer player, int power) {
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, SHIELD_SECONDS * 20, shieldAmplifier(power)));
		Fx.send(player.level(), player.position(), FxType.TALISMAN, TalismanFxIds.SHIELD, 0, player.getBbHeight());
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.ARMOR_EQUIP_GOLD, SoundSource.PLAYERS,
				1.0F, 0.8F);
		return true;
	}

	private static boolean binding(ServerPlayer player, int power) {
		LivingEntity target = Targeting.lookedAtHostile(player, BINDING_RANGE);
		if (target == null) {
			Msg.actionBar(player, Component.translatable("kultivator.talismans.no_target", (int) BINDING_RANGE).withColor(0xFF9A7A));
			return false;
		}
		double seconds = bindingSeconds(power) * (TechniquePower.isBoss(target) ? BINDING_BOSS_SHARE : 1.0D);
		int ticks = Math.max(10, (int) Math.round(seconds * 20.0D));
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6), player);
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), player);
		if (!(target instanceof ServerPlayer)) {
			target.addTag(NoGrief.TAG);
		}
		Fx.send(player.level(), target.position(), FxType.TALISMAN, TalismanFxIds.BINDING, ticks, target.getBbHeight());
		player.level().playSound(null, target.getX(), target.getY() + 1.0D, target.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS,
				1.0F, 0.7F);
		return true;
	}

	private static boolean ward(ServerPlayer player) {
		Ward ward = WARDS.get(player.getUUID());
		if (ward == null) {
			return false;
		}
		ward.used++;
		ward.pending = ward.done + 1;
		Fx.send(player.level(), player.position(), FxType.TALISMAN, TalismanFxIds.WARD, 0, player.getBbHeight());
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS,
				0.8F, 1.4F);
		Msg.chat(player, Component.translatable("kultivator.talismans.ward.used", ward.pending, ward.used, WARDS_PER_TRIBULATION)
				.withColor(0x6CB8FF));
		return true;
	}

	/** The Tribulation Ward strike factor: ×0.5 on the strike the pending ward softens. Pure. */
	private static double wardFactor(ServerPlayer player, Rank target, int strikeIndex, boolean last) {
		Ward ward = WARDS.get(player.getUUID());
		return ward != null && ward.pending > 0 && ward.pending == strikeIndex && Tribulation.isActive(player) ? WARD_FACTOR : 1.0D;
	}

	private static boolean thousandMile(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, THOUSAND_MILE_SECONDS * 20, 1));
		Fx.send(player.level(), player.position(), FxType.TALISMAN, TalismanFxIds.THOUSAND_MILE, 0, player.getBbHeight());
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS,
				0.8F, 1.5F);
		return true;
	}

	private static boolean limpidity(ServerPlayer player, int power) {
		int seconds = limpiditySeconds(power);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, seconds * 20, 0));
		LIMPID.put(player.getUUID(), player.level().getGameTime() + seconds * 20L);
		shakeOff(player);
		Fx.send(player.level(), player.position(), FxType.TALISMAN, TalismanFxIds.LIMPIDITY, 0, player.getBbHeight());
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS,
				0.8F, 1.2F);
		Msg.actionBar(player, Component.translatable("kultivator.talismans.limpidity.on", seconds).withColor(0xC8B8FF));
		return true;
	}

	/** Monsters within 12 that target the user forget them, unless the user hurt them in the last 3 s; bosses ignore it. */
	private static void shakeOff(ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(LIMPIDITY_RADIUS);
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box, mob -> mob.getTarget() == player)) {
			if (TechniquePower.isBoss(mob)) {
				continue;
			}
			Entity lastHurt = mob.getLastHurtByMob();
			if (lastHurt == player && mob.tickCount - mob.getLastHurtByMobTimestamp() <= LIMPIDITY_GRACE_TICKS) {
				continue;
			}
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
	}

	/** Ends the limpidity (the user attacked): the invisibility is lifted. */
	private static void endLimpidity(ServerPlayer player, boolean attacked) {
		if (LIMPID.remove(player.getUUID()) == null) {
			return;
		}
		player.removeEffect(MobEffects.INVISIBILITY);
		if (attacked) {
			Msg.actionBar(player, Component.translatable("kultivator.talismans.limpidity.broken").withColor(0xA8B5B0));
		}
	}

	// ================================================================== tests

	/** {@code uji jimat <jenis|semua> [kekuatan]}: gives talismans; 0 when the id is unknown. */
	public static int testGive(ServerPlayer player, String id, int power) {
		List<TalismanKind> kinds = new ArrayList<>();
		if ("semua".equals(id) || "all".equals(id)) {
			kinds.addAll(List.of(TalismanKind.values()));
		} else {
			TalismanKind kind = TalismanKind.byId(id);
			if (kind == null) {
				return 0;
			}
			kinds.add(kind);
		}
		if (kinds.contains(TalismanKind.RETURN)) {
			// Test return talismans come with the 10-minute cooldown reset.
			ReturnTalisman.resetCooldown(player);
		}
		for (TalismanKind kind : kinds) {
			ItemStack stack = create(kind, power, player.getName().getString());
			stack.setCount(4);
			give(player, stack);
		}
		return kinds.size();
	}

	/** The test ids of {@code uji jimat}. */
	public static List<String> testIds() {
		List<String> ids = new ArrayList<>();
		for (TalismanKind kind : TalismanKind.values()) {
			ids.add(kind.testId());
		}
		ids.add("semua");
		return ids;
	}

	private Talismans() {
	}
}
