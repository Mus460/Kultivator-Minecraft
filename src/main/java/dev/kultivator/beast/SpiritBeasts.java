package dev.kultivator.beast;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spirit beasts (Binatang Roh, DESIGN sections 10 and 16): a wolf the player tamed, or a fox that trusts the player (a
 * cub of foxes they bred), fed a Spirit Beast Pill awakens (attachment {@link BeastData#TYPE}). It gathers spirit XP from its own kills (the victim's max health), from
 * monsters its owner kills while it is within 16 blocks (half the victim's max health) and from more pills (+40, once per
 * 30 s), and evolves at 100 / 400 / 1200 XP into tiers 1 / 2 / 3; tier 2 needs an owner at Golden Core and tier 3 at
 * Nascent Soul. Each tier adds permanent health, bite, speed and size modifiers; tier 2 brings Moon Thunder (wolf) or
 * Foxfire (fox), tier 3 a blessing for the owner. Beasts only ever hurt what {@link Targeting} lets their owner hurt.
 * Spirit foxes follow and defend their owner (see {@link BeastAi}); wolves do that through vanilla.
 */
public final class SpiritBeasts {

	/** Spirit XP of a pill fed to an awakened beast. */
	public static final double PILL_XP = 40.0D;
	/** A beast digests one extra pill per this many ticks (30 s). */
	public static final int PILL_COOLDOWN_TICKS = 600;
	/** The owner's kills feed beasts within this distance of the owner. */
	private static final double KILL_SHARE_RANGE = 16.0D;
	private static final double OWNER_KILL_SHARE = 0.5D;
	/** Debug commands act on the wolf or fox looked at within this distance. */
	private static final double LOOK_RANGE = 8.0D;

	private static final float MOON_THUNDER_CHANCE = 0.2F;
	private static final float MOON_THUNDER_BONUS = 0.5F;
	private static final float FOXFIRE_SECONDS = 4.0F;
	/** Tier 3: every 5 s the owner within 8 blocks heals 1 health and gets the beast's blessing. */
	private static final int BLESSING_INTERVAL = 100;
	private static final double BLESSING_RANGE = 8.0D;
	private static final int BLESSING_EFFECT_TICKS = 120;
	/** The owner's last attacker / target counts for spirit foxes this long (ticks). */
	private static final int DEFEND_MEMORY = 60;

	// BEAST_EVOLVE sub effects (a < 0; a >= 0 is the evolution cinematic to that tier, 0 = awakening).
	/** Moon Thunder strike: pos = victim, target = sky point. */
	public static final int FX_MOON_THUNDER = -1;
	/** Foxfire bite: pos = victim. */
	public static final int FX_FOXFIRE = -2;
	/** Tier 3 blessing: pos = beast, target = owner. */
	public static final int FX_BLESSING = -3;

	// Permanent modifiers per tier (index = tier).
	public static final Identifier HEALTH_ID = Kultivator.id("beast_health");
	public static final Identifier ATTACK_ID = Kultivator.id("beast_attack");
	public static final Identifier SPEED_ID = Kultivator.id("beast_speed");
	public static final Identifier SCALE_ID = Kultivator.id("beast_scale");
	private static final double[] HEALTH = {0.0D, 10.0D, 20.0D, 40.0D};
	private static final double[] ATTACK = {0.0D, 2.0D, 4.0D, 8.0D};
	private static final double[] SPEED = {0.0D, 0.10D, 0.20D, 0.30D};
	private static final double[] SCALE = {0.0D, 0.0D, 0.10D, 0.20D};

	private static final Identifier HEART_DEMON = Kultivator.id("heart_demon");

	private static final int[] WOLF_COLORS = {0xCFE6FF, 0x9FD0FF, 0x8FB4FF, 0xFFF1C2};
	private static final int[] FOX_COLORS = {0xFFC48A, 0xFF9E50, 0x7FE8FF, 0xFFD54A};

	/** Beast UUID -> game time of the last pill it digested. */
	private static final Map<UUID, Long> LAST_PILL = new HashMap<>();
	/** "beast uuid:tier" already told that the owner's realm is too low (once per session). */
	private static final Set<String> NOTIFIED = new HashSet<>();
	/** Moon Thunder strikes landing at the end of this tick (never inside another hurt call). */
	private static final List<Strike> PENDING_STRIKES = new ArrayList<>();
	/** Beasts that went for something they may not hurt: calmed at the end of this tick. */
	private static final List<Mob> PENDING_CALM = new ArrayList<>();
	private static boolean striking;

	private record Strike(Mob beast, LivingEntity target, float amount) {
	}

	public static void init() {
		BeastData.register();
		BeastItems.init();
		UseEntityCallback.EVENT.register(SpiritBeasts::onUseEntity);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpiritBeasts::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SpiritBeasts::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(SpiritBeasts::afterDeath);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(SpiritBeasts::onKill);
		ServerEntityEvents.ENTITY_LOAD.register(SpiritBeasts::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(SpiritBeasts::onUnload);
		ServerTickEvents.END_SERVER_TICK.register(SpiritBeasts::flush);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BeastRegistry.clear();
			LAST_PILL.clear();
			NOTIFIED.clear();
			PENDING_STRIKES.clear();
			PENDING_CALM.clear();
		});
	}

	/** Once a second per owner: evolutions waiting for the owner, target checks, fox following, tier 3 blessings. */
	public static void tick(ServerPlayer player, CultivationData data) {
		if (player.tickCount % 20 != 0 || player.isRemoved()) {
			return;
		}
		List<Mob> beasts = BeastRegistry.of(player.getUUID());
		if (beasts.isEmpty()) {
			return;
		}
		boolean blessing = player.tickCount % BLESSING_INTERVAL == 0;
		LivingEntity attacker = player.getLastHurtByMob();
		if (attacker != null && player.tickCount - player.getLastHurtByMobTimestamp() <= DEFEND_MEMORY) {
			BeastAi.defend(player, attacker, beasts);
		}
		LivingEntity attacked = player.getLastHurtMob();
		if (attacked != null && player.tickCount - player.getLastHurtMobTimestamp() <= DEFEND_MEMORY) {
			BeastAi.defend(player, attacked, beasts);
		}
		for (Mob beast : beasts) {
			checkEvolution(beast, player);
			if (beast.level() != player.level()) {
				continue;
			}
			BeastAi.checkTarget(beast, player);
			if (beast instanceof Fox fox) {
				BeastAi.follow(fox, player);
			}
			if (blessing) {
				bless(beast, player);
			}
		}
	}

	// ================================================================== public API

	/** The spirit beast data of an entity, or null for anything that is not an awakened beast. */
	public static @Nullable BeastData data(Entity entity) {
		return entity == null ? null : entity.getAttached(BeastData.TYPE);
	}

	public static boolean isSpiritBeast(Entity entity) {
		return data(entity) != null;
	}

	/** The owner's spirit beasts currently loaded on the server (any dimension). */
	public static List<Mob> loadedBeasts(UUID owner) {
		return BeastRegistry.of(owner);
	}

	/**
	 * Adds spirit XP to an awakened beast and evolves it when it may (owner online and at a high enough realm).
	 * Returns the beast's data afterwards (null when it is not a spirit beast).
	 */
	public static @Nullable BeastData addXp(Mob beast, double amount) {
		BeastData data = beast.getAttached(BeastData.TYPE);
		if (data == null || !(amount > 0.0D) || !Double.isFinite(amount)) {
			return data;
		}
		beast.setAttached(BeastData.TYPE, data.withXp(data.xp() + amount));
		checkEvolution(beast, owner(beast, data));
		return beast.getAttached(BeastData.TYPE);
	}

	// ================================================================== awakening and pills

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity,
			@Nullable EntityHitResult hitResult) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		int kind = BeastData.kindOf(entity);
		if (!stack.is(BeastItems.SPIRIT_BEAST_PILL) || kind < 0 || !(entity instanceof Mob mob) || !mob.isAlive()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			// The server decides; never let the wolf's sit toggle run for a pill.
			return InteractionResult.SUCCESS;
		}
		return feed(serverPlayer, mob, kind, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/** A pill given to a wolf or fox: awakens it, or feeds an awakened beast of the player. */
	private static boolean feed(ServerPlayer player, Mob mob, int kind, ItemStack stack) {
		BeastData data = mob.getAttached(BeastData.TYPE);
		if (data != null) {
			if (!data.ownedBy(player)) {
				Msg.chat(player, Component.translatable("kultivator.beasts.refuse.other_owner").withStyle(ChatFormatting.RED));
				return false;
			}
			return feedPill(player, mob, data, stack);
		}
		if (mob instanceof Wolf wolf) {
			if (!wolf.isTame()) {
				Msg.chat(player, Component.translatable("kultivator.beasts.refuse.wild_wolf").withStyle(ChatFormatting.YELLOW));
				return false;
			}
			if (!wolf.isOwnedBy(player)) {
				Msg.chat(player, Component.translatable("kultivator.beasts.refuse.other_owner").withStyle(ChatFormatting.RED));
				return false;
			}
		}
		if (mob instanceof Fox fox && !BeastAi.trusts(fox, player)) {
			// Only a fox that already trusts the player (so nobody else's fox can be taken with one pill).
			Msg.chat(player, Component.translatable("kultivator.beasts.refuse.untrusted_fox").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		awaken(player, mob, kind);
		stack.consume(1, player);
		return true;
	}

	private static boolean feedPill(ServerPlayer player, Mob beast, BeastData data, ItemStack stack) {
		long now = player.level().getGameTime();
		Long last = LAST_PILL.get(beast.getUUID());
		if (last != null && now - last < PILL_COOLDOWN_TICKS) {
			long seconds = (PILL_COOLDOWN_TICKS - (now - last) + 19L) / 20L;
			Msg.actionBar(player, Component.translatable("kultivator.beasts.pill.digesting", beast.getDisplayName(), seconds)
					.withStyle(ChatFormatting.YELLOW));
			return false;
		}
		LAST_PILL.put(beast.getUUID(), now);
		stack.consume(1, player);
		ServerLevel level = player.level();
		level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 1.1F);
		level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.3F);
		Fx.burst(level, beast.position().add(0.0D, beast.getBbHeight() * 0.6D, 0.0D), tierColor(data.kind(), data.tier()), 14, 1.2F);
		Component name = beast.getDisplayName();
		BeastData after = addXp(beast, PILL_XP);
		if (after == null) {
			return true;
		}
		double next = after.nextTierXp();
		Msg.chat(player, next < 0.0D
				? Component.translatable("kultivator.beasts.pill.xp_max", name, Msg.number(PILL_XP)).withStyle(ChatFormatting.AQUA)
				: Component.translatable("kultivator.beasts.pill.xp", name, Msg.number(PILL_XP), Msg.number(after.xp()), Msg.number(next))
						.withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** Binds the animal to the player as a tier 0 spirit beast (the caller consumes the pill). */
	private static BeastData awaken(ServerPlayer player, Mob mob, int kind) {
		Component before = mob.getDisplayName();
		if (mob instanceof Fox fox && BeastAi.trust(fox, player) == BeastAi.TRUST_NEW) {
			Msg.chat(player, Component.translatable("kultivator.beasts.fox_trust").withStyle(ChatFormatting.GRAY));
		}
		BeastData data = new BeastData(player.getUUID(), 0, 0.0D, kind);
		mob.setAttached(BeastData.TYPE, data);
		mob.addTag(NoGrief.TAG);
		mob.setPersistenceRequired();
		if (mob instanceof Fox fox) {
			BeastAi.tame(fox);
		}
		applyStats(mob, 0, true);
		if (!mob.hasCustomName()) {
			mob.setCustomName(data.title());
		}
		BeastRegistry.add(mob, data);

		ServerLevel level = player.level();
		Fx.send(level, mob.position(), FxType.BEAST_EVOLVE, 0, mob.getId(), kind);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.2F);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 0.9F, 0.8F);
		mob.playAmbientSound();
		Msg.chat(player, Component.translatable("kultivator.beasts.awaken", before, data.title()).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable("kultivator.beasts.awaken.bond", Msg.number(BeastData.TIER_XP[1]))
				.withStyle(ChatFormatting.GRAY));
		Advancements.grant(player, "spirit_beast");
		checkPair(player);
		return data;
	}

	/** Grants beast_pair when the player has a spirit wolf and a spirit fox loaded at the same time. */
	private static void checkPair(ServerPlayer player) {
		boolean wolf = false;
		boolean fox = false;
		for (Mob beast : BeastRegistry.of(player.getUUID())) {
			BeastData data = beast.getAttached(BeastData.TYPE);
			if (data != null) {
				wolf |= data.isWolf();
				fox |= data.isFox();
			}
		}
		if (wolf && fox) {
			Advancements.grant(player, "beast_pair");
		}
	}

	// ================================================================== evolution

	/** Evolves the beast as far as its XP and its (online) owner's realm allow; tells the owner what is missing. */
	private static void checkEvolution(Mob beast, @Nullable ServerPlayer owner) {
		BeastData data = beast.getAttached(BeastData.TYPE);
		if (data == null || data.tier() >= BeastData.MAX_TIER || owner == null) {
			return;
		}
		int tier = data.tier();
		Realm realm = Cultivators.get(owner).realm();
		while (tier < BeastData.MAX_TIER && data.xp() >= BeastData.TIER_XP[tier + 1]) {
			Realm needed = BeastData.TIER_REALM[tier + 1];
			if (!realm.atLeast(needed)) {
				if (NOTIFIED.add(beast.getUUID() + ":" + (tier + 1))) {
					Msg.chat(owner, Component.translatable("kultivator.beasts.evolve.blocked", beast.getDisplayName(), tier + 1,
							needed.displayName()).withStyle(ChatFormatting.YELLOW));
				}
				break;
			}
			tier++;
		}
		if (tier > data.tier()) {
			evolve(beast, data, data.withTier(tier), owner, true);
		}
	}

	/**
	 * Applies a new tier: stats (healed to full when it rises), the tier title as name when the beast still carries one
	 * of our titles, and for a real evolution the cinematic, sounds, the owner's title screen, new abilities and
	 * advancements.
	 */
	private static void evolve(Mob beast, BeastData before, BeastData after, @Nullable ServerPlayer owner, boolean celebrate) {
		Component oldName = beast.getDisplayName();
		beast.setAttached(BeastData.TYPE, after);
		applyStats(beast, after.tier(), after.tier() > before.tier());
		rename(beast, after);
		for (int tier = before.tier() + 1; tier <= after.tier(); tier++) {
			NOTIFIED.remove(beast.getUUID() + ":" + tier);
		}
		if (!celebrate || !(beast.level() instanceof ServerLevel level)) {
			return;
		}
		int color = tierColor(after.kind(), after.tier());
		Fx.send(level, beast.position(), FxType.BEAST_EVOLVE, after.tier(), beast.getId(), after.kind());
		double x = beast.getX();
		double y = beast.getY();
		double z = beast.getZ();
		level.playSound(null, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.9F, 0.7F + 0.1F * after.tier());
		level.playSound(null, x, y, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.8F, 1.0F);
		if (after.tier() >= 2) {
			if (after.isWolf()) {
				level.playSound(null, x, y, z, SoundEvents.TRIDENT_THUNDER, SoundSource.NEUTRAL, 0.5F, 1.3F);
			} else {
				level.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.7F, 0.8F);
			}
		}
		beast.playAmbientSound();
		if (owner == null) {
			return;
		}
		Msg.title(owner, Component.translatable("kultivator.beasts.evolve.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.translatable("kultivator.beasts.evolve.subtitle", after.title(), after.tier()).withColor(color), 10, 60, 20);
		Msg.chat(owner, Component.translatable("kultivator.beasts.evolve.chat", oldName, after.title(), after.tier()).withColor(color));
		Msg.chat(owner, Component.translatable("kultivator.beasts.ability.stronger").withStyle(ChatFormatting.GRAY));
		for (int tier = before.tier() + 1; tier <= after.tier(); tier++) {
			if (tier == 2) {
				Msg.chat(owner, Component.translatable(after.isWolf() ? "kultivator.beasts.ability.moon_thunder"
						: "kultivator.beasts.ability.foxfire").withStyle(ChatFormatting.AQUA));
			} else if (tier == 3) {
				Msg.chat(owner, Component.translatable(after.isWolf() ? "kultivator.beasts.ability.blessing_wolf"
						: "kultivator.beasts.ability.blessing_fox").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
		if (after.tier() >= 1) {
			Advancements.grant(owner, "beast_evolved");
		}
		if (after.tier() >= BeastData.MAX_TIER) {
			Advancements.grant(owner, "beast_ascended");
		}
	}

	/** Gives the tier title as name unless the player named the beast themselves. */
	private static void rename(Mob beast, BeastData data) {
		Component current = beast.getCustomName();
		if (current == null || current.getContents() instanceof TranslatableContents translatable
				&& translatable.getKey().startsWith(BeastData.TITLE_PREFIX)) {
			beast.setCustomName(data.title());
		}
	}

	/** Sets the permanent tier modifiers (health, bite, speed, size); heals to full when asked, else clamps health. */
	public static void applyStats(LivingEntity beast, int tier, boolean heal) {
		int index = Math.max(0, Math.min(BeastData.MAX_TIER, tier));
		setModifier(beast, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH[index], AttributeModifier.Operation.ADD_VALUE);
		setModifier(beast, Attributes.ATTACK_DAMAGE, ATTACK_ID, ATTACK[index], AttributeModifier.Operation.ADD_VALUE);
		setModifier(beast, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED[index], AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(beast, Attributes.SCALE, SCALE_ID, SCALE[index], AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (beast.isDeadOrDying()) {
			return;
		}
		if (heal) {
			beast.setHealth(beast.getMaxHealth());
		} else if (beast.getHealth() > beast.getMaxHealth()) {
			beast.setHealth(beast.getMaxHealth());
		}
	}

	private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0D) {
			instance.removeModifier(id);
		} else {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** Tier 3: heals the owner nearby a little and gives the night Regeneration (wolf) or Speed (fox). */
	private static void bless(Mob beast, ServerPlayer owner) {
		BeastData data = beast.getAttached(BeastData.TYPE);
		if (data == null || data.tier() < BeastData.MAX_TIER || owner.isDeadOrDying() || owner.isSpectator()
				|| beast.distanceToSqr(owner) > BLESSING_RANGE * BLESSING_RANGE) {
			return;
		}
		if (owner.getHealth() < owner.getMaxHealth()) {
			owner.heal(1.0F);
		}
		if (data.isWolf()) {
			if (owner.level().isDarkOutside()) {
				owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BLESSING_EFFECT_TICKS, 0, true, true));
			}
		} else {
			owner.addEffect(new MobEffectInstance(MobEffects.SPEED, BLESSING_EFFECT_TICKS, 0, true, true));
		}
		Fx.send(owner.level(), beast.position().add(0.0D, beast.getBbHeight() * 0.6D, 0.0D),
				owner.position().add(0.0D, owner.getBbHeight() * 0.55D, 0.0D), FxType.BEAST_EVOLVE, FX_BLESSING, beast.getId(), data.kind());
	}

	// ================================================================== combat

	/** Beasts only hurt what their owner's techniques could hurt (or a wild mob attacking them). */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof Mob beast) || beast == victim) {
			return true;
		}
		BeastData data = beast.getAttached(BeastData.TYPE);
		if (data == null || BeastAi.mayAttack(beast, victim, owner(beast, data))) {
			return true;
		}
		PENDING_CALM.add(beast);
		return false;
	}

	/** Moon Thunder and Foxfire on a beast's bite; spirit foxes join the fights of their owner. */
	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		Entity attacker = source.getEntity();
		if (!striking && attacker instanceof Mob beast && source.getDirectEntity() == beast && damageTaken > 0.0F && victim.isAlive()) {
			BeastData data = beast.getAttached(BeastData.TYPE);
			if (data != null && data.tier() >= 2 && victim.level() instanceof ServerLevel level) {
				if (data.isWolf()) {
					if (beast.getRandom().nextFloat() < MOON_THUNDER_CHANCE) {
						PENDING_STRIKES.add(new Strike(beast, victim, damageTaken * MOON_THUNDER_BONUS));
					}
				} else {
					// Fire on the entity only: a burning mob never sets blocks alight.
					victim.igniteForSeconds(FOXFIRE_SECONDS);
					Fx.send(level, victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D), FxType.BEAST_EVOLVE, FX_FOXFIRE,
							beast.getId(), data.kind());
					level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.4F, 1.6F);
				}
			}
		}
		if (victim instanceof ServerPlayer owner && attacker instanceof LivingEntity enemy && enemy != owner) {
			List<Mob> beasts = BeastRegistry.of(owner.getUUID());
			if (!beasts.isEmpty()) {
				BeastAi.defend(owner, enemy, beasts);
			}
		} else if (attacker instanceof ServerPlayer owner && victim != owner) {
			List<Mob> beasts = BeastRegistry.of(owner.getUUID());
			if (!beasts.isEmpty()) {
				BeastAi.defend(owner, victim, beasts);
			}
		}
	}

	/**
	 * End of every server tick: Moon Thunder strikes land and over-eager beasts calm down. A beast that picked a
	 * silverfish or a heart-bound creaking (a vanilla tamed wolf's owner goals can) drops it before it can bite: hurting
	 * those would change blocks, and their own hurt code runs before any damage event could stop it.
	 */
	private static void flush(MinecraftServer server) {
		BeastRegistry.forEach(BeastAi::dropUntouchableTarget);
		if (!PENDING_CALM.isEmpty()) {
			List<Mob> calm = List.copyOf(PENDING_CALM);
			PENDING_CALM.clear();
			for (Mob beast : calm) {
				if (beast.isAlive()) {
					BeastAi.calmDown(beast);
				}
			}
		}
		if (!PENDING_STRIKES.isEmpty()) {
			List<Strike> strikes = List.copyOf(PENDING_STRIKES);
			PENDING_STRIKES.clear();
			for (Strike strike : strikes) {
				strike(strike);
			}
		}
	}

	/** A particle-only thunder strike on the bitten target for +50% of the bite (the beast is the attacker). */
	private static void strike(Strike strike) {
		Mob beast = strike.beast();
		LivingEntity target = strike.target();
		if (!beast.isAlive() || !target.isAlive() || beast.level() != target.level() || !(target.level() instanceof ServerLevel level)
				|| BeastAi.untouchable(target)) {
			return;
		}
		Vec3 at = target.position();
		Fx.send(level, at, at.add(0.0D, 9.0D, 0.0D), FxType.BEAST_EVOLVE, FX_MOON_THUNDER, beast.getId(), BeastData.WOLF);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.NEUTRAL, 0.7F, 1.5F);
		striking = true;
		try {
			// The bite just started the target's short invulnerability; the strike is a second, separate hit.
			target.damageCooldownTime = 0;
			target.hurtServer(level, beast.damageSources().mobAttack(beast), strike.amount());
		} finally {
			striking = false;
		}
	}

	/** Spirit XP from kills: the beast's own (victim max health), its owner's monsters nearby (half of it). */
	private static void onKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		if (BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()).equals(HEART_DEMON)) {
			return;
		}
		if (killer instanceof Mob beast && beast.hasAttached(BeastData.TYPE)) {
			addXp(beast, killed.getMaxHealth());
			return;
		}
		if (killer instanceof ServerPlayer player && !(player instanceof FakePlayer) && killed instanceof Enemy) {
			double share = killed.getMaxHealth() * OWNER_KILL_SHARE;
			for (Mob beast : BeastRegistry.of(player.getUUID())) {
				if (beast != killed && beast.level() == player.level() && beast.distanceToSqr(player) <= KILL_SHARE_RANGE * KILL_SHARE_RANGE) {
					addXp(beast, share);
				}
			}
		}
	}

	/** Vanilla tells a wolf's owner when it dies; a spirit fox's owner is told here. Its pill cooldown is forgotten. */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		BeastData data = entity.getAttached(BeastData.TYPE);
		if (data == null) {
			return;
		}
		LAST_PILL.remove(entity.getUUID());
		if (!(entity instanceof Fox fox)) {
			return;
		}
		ServerPlayer owner = owner(fox, data);
		if (owner != null) {
			Msg.chat(owner, Component.translatable("kultivator.beasts.fallen", fox.getDisplayName()).withStyle(ChatFormatting.RED));
		}
	}

	// ================================================================== loading

	private static void onLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof Mob mob)) {
			return;
		}
		BeastData data = mob.getAttached(BeastData.TYPE);
		if (data == null) {
			return;
		}
		BeastRegistry.add(mob, data);
		mob.addTag(NoGrief.TAG);
		if (mob instanceof Fox fox) {
			BeastAi.tame(fox);
		}
		applyStats(mob, data.tier(), false);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(data.owner());
		if (owner != null) {
			checkPair(owner);
		}
	}

	private static void onUnload(Entity entity, ServerLevel level) {
		if (entity instanceof Mob mob) {
			BeastData data = mob.getAttached(BeastData.TYPE);
			if (data != null) {
				BeastRegistry.remove(mob, data);
			}
		}
	}

	private static @Nullable ServerPlayer owner(Entity beast, BeastData data) {
		if (!(beast.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getServer().getPlayerList().getPlayer(data.owner());
	}

	/** Colour of a beast's aura / cinematic at a tier (pale moon blue to heavenly gold; fox amber, soul fire, gold). */
	public static int tierColor(int kind, int tier) {
		int index = Math.max(0, Math.min(BeastData.MAX_TIER, tier));
		return kind == BeastData.FOX ? FOX_COLORS[index] : WOLF_COLORS[index];
	}

	// ================================================================== commands

	/** {@code beast list} for players; {@code debug beast xp <amount>} and {@code debug beast tier <0-3>}. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("beast")
				.then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))));
		debug.then(Commands.literal("beast")
				.then(Commands.literal("xp")
						.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D))
								.executes(ctx -> debugXp(ctx, DoubleArgumentType.getDouble(ctx, "amount")))))
				.then(Commands.literal("tier")
						.then(Commands.argument("tier", IntegerArgumentType.integer(0, BeastData.MAX_TIER))
								.executes(ctx -> debugTier(ctx, IntegerArgumentType.getInteger(ctx, "tier"))))));
	}

	private static int list(CommandSourceStack source, ServerPlayer player) {
		List<Mob> beasts = new ArrayList<>(BeastRegistry.of(player.getUUID()));
		if (beasts.isEmpty()) {
			source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.beasts.list.none").withStyle(ChatFormatting.GRAY)), false);
			return 0;
		}
		beasts.sort(Comparator.comparingDouble(beast -> beast.level() == player.level() ? beast.distanceToSqr(player) : Double.MAX_VALUE));
		source.sendSuccess(() -> Component.translatable("kultivator.beasts.list.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		for (Mob beast : beasts) {
			BeastData data = beast.getAttached(BeastData.TYPE);
			if (data == null) {
				continue;
			}
			double next = data.nextTierXp();
			Component xp = next < 0.0D
					? Component.translatable("kultivator.beasts.list.xp_max", Msg.number(data.xp()))
					: Component.translatable("kultivator.beasts.list.xp", Msg.number(data.xp()), Msg.number(next));
			Component line;
			if (beast.level() == player.level()) {
				line = Component.translatable("kultivator.beasts.list.entry", beast.getDisplayName(), data.title(), data.tier(), xp,
						Msg.number(beast.getHealth()), Msg.number(beast.getMaxHealth()), Msg.number(Math.sqrt(beast.distanceToSqr(player))));
			} else {
				line = Component.translatable("kultivator.beasts.list.elsewhere", beast.getDisplayName(), data.title(), data.tier(), xp);
			}
			int color = tierColor(data.kind(), data.tier());
			source.sendSuccess(() -> Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY).append(line.copy().withColor(color)), false);
		}
		return beasts.size();
	}

	private static int debugXp(CommandContext<CommandSourceStack> ctx, double amount) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Mob beast = debugBeast(ctx, player);
		if (beast == null) {
			return 0;
		}
		BeastData after = addXp(beast, amount);
		if (after == null) {
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.beasts.debug.xp", beast.getDisplayName(),
				Msg.number(amount), Msg.number(after.xp()), after.tier())), false);
		return 1;
	}

	private static int debugTier(CommandContext<CommandSourceStack> ctx, int tier) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Mob beast = debugBeast(ctx, player);
		if (beast == null) {
			return 0;
		}
		BeastData data = beast.getAttached(BeastData.TYPE);
		if (data == null) {
			return 0;
		}
		// Exactly the tier's XP, so the next check neither undoes a lowered tier nor climbs further on its own.
		BeastData after = data.withTier(tier).withXp(BeastData.TIER_XP[Math.max(0, Math.min(BeastData.MAX_TIER, tier))]);
		evolve(beast, data, after, owner(beast, after), after.tier() > data.tier());
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.beasts.debug.tier", beast.getDisplayName(),
				after.tier())), false);
		return 1;
	}

	/**
	 * The wolf or fox the player looks at within 8 blocks. One that is not awakened yet is awakened for the player when
	 * it could be (a wolf they tamed, or a fox), so testers need no pill.
	 */
	private static @Nullable Mob debugBeast(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		Mob beast = lookedAt(player);
		if (beast == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.beasts.debug.no_target"));
			return null;
		}
		if (beast.hasAttached(BeastData.TYPE)) {
			return beast;
		}
		boolean eligible = beast instanceof Fox || beast instanceof Wolf wolf && wolf.isTame() && wolf.isOwnedBy(player);
		if (!eligible) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.beasts.debug.not_beast"));
			return null;
		}
		awaken(player, beast, BeastData.kindOf(beast));
		return beast;
	}

	private static @Nullable Mob lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		BlockHitResult block = Targeting.blockHit(player, LOOK_RANGE);
		double maxSq = block.getType() == HitResult.Type.MISS ? LOOK_RANGE * LOOK_RANGE : block.getLocation().distanceToSqr(eye);
		Vec3 end = eye.add(look.scale(LOOK_RANGE));
		AABB sweep = player.getBoundingBox().expandTowards(look.scale(LOOK_RANGE)).inflate(1.0D);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
				entity -> BeastData.kindOf(entity) >= 0 && entity.isAlive(), maxSq);
		return hit != null && hit.getEntity() instanceof Mob mob ? mob : null;
	}

	private SpiritBeasts() {
	}
}
