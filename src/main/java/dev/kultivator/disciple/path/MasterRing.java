package dev.kultivator.disciple.path;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Technique;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.registry.ModItems;
import dev.kultivator.technique.DaoTechniqueSub;
import dev.kultivator.technique.Techniques;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Cincin Jiwa Guru, the Master's Soul Ring (DESIGN 20.3.20). Sumber: <i>Battle Through the Heavens</i> (Yao Lao's soul
 * lives in the ring, secretly absorbs Xiao Yan's Dou Qi for three years, then becomes his master, lending soul power or
 * taking over in deadly fights) and <i>Coiling Dragon</i> (Doehring Cowart's soul in the Coiling Dragon Ring teaches and
 * guards Linley).
 * <p>
 * Given only to the heir, one per heir; it stays in the disciple's data (and so in its Soul Tablet). For 72,000 ticks it
 * takes 50% of the heir's meditation XP, then it bonds: Bimbingan Jiwa (the master-meditation share at any distance and
 * in any dimension), Perlindungan Jiwa (once a day a lethal blow leaves 1 health and Resistance IV for 3 s) and Jiwa Guru
 * Turun (below 30% health in a fight, or ordered: for 20 s the heir uses the master's power for its techniques, a Buddha
 * Palm and Dao Domain pulses of the master's Dao, monsters only; the master pays 40% of max qi and 2 qi a second and is
 * slowed; cooldown 10 minutes).
 */
public final class MasterRing {

	public static final int AWAKEN_TICKS = 72_000;
	public static final double AWAKEN_XP = 0.5D;
	public static final int PROTECT_TICKS = 60;
	public static final int DESCEND_TICKS = 400;
	public static final int DESCEND_COOLDOWN = 12_000;
	public static final double DESCEND_QI = 0.40D;
	public static final double DESCEND_QI_PER_SECOND = 2.0D;
	public static final double DESCEND_HEALTH = 0.30D;
	public static final double DOMAIN_RADIUS = 6.0D;
	public static final double DOMAIN_POWER = 0.6D;
	public static final double PALM_POWER = 2.5D;
	public static final double PALM_RADIUS = 5.0D;
	private static final int PALM_DELAY = 20;
	private static final int PULSE_INTERVAL = 40;

	/** Master's Soul Ring (shapeless: gold ingot, echo shard, amethyst shard, ender pearl). */
	public static final Item ITEM = ModItems.register("master_soul_ring", SoulRingItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

	/** A running Jiwa Guru Turun. */
	private record Descent(DiscipleEntity entity, UUID master, long until, int[] ticks) {
	}

	private static final Map<UUID, Descent> DESCENTS = new HashMap<>();

	/** Loads the class (registers the item). */
	static void init() {
	}

	static void register() {
		// Jiwa Guru Turun: "Slowness I and no casting meanwhile" for the master (DESIGN 20.3.20).
		Techniques.addCastBlocker(player -> {
			for (Descent descent : DESCENTS.values()) {
				if (descent.master().equals(player.getUUID())) {
					return Component.translatable(DisciplePaths.PREFIX + "ring.no_cast");
				}
			}
			return null;
		});
		DiscipleHooks.addXpFactor((entity, data, source) -> source == XpSource.MEDITATION && awakening(data) ? AWAKEN_XP : 1.0D);
		DiscipleHooks.addMasterShareAnyRange(MasterRing::bonded);
		DiscipleHooks.addOnDeath(MasterRing::protect);
		DiscipleHooks.addDamageFactor(MasterRing::damage);
		DiscipleHooks.addCombatCast((entity, target) -> {
			if (entity.role() == DiscipleEntity.Role.DISCIPLE && bonded(entity.data()) && !descending(entity)
					&& entity.getHealth() < entity.getMaxHealth() * DESCEND_HEALTH) {
				ServerPlayer master = entity.owner();
				return master != null && start(master, entity, true);
			}
			return false;
		});
		DiscipleHooks.addFeedHandler((entity, master, stack) -> {
			if (!stack.is(ITEM)) {
				return false;
			}
			give(master, entity, stack);
			return true;
		});
	}

	static CompoundTag ring(DiscipleData data) {
		return DisciplePaths.tag(data).getCompoundOrEmpty("ring");
	}

	public static boolean has(DiscipleData data) {
		return DisciplePaths.tag(data).contains("ring");
	}

	/** True while the ring still awakens (it takes half the meditation XP). */
	public static boolean awakening(DiscipleData data) {
		return has(data) && ring(data).getIntOr("stage", 0) == 0;
	}

	/** True once the ring has bonded. */
	public static boolean bonded(DiscipleData data) {
		return has(data) && ring(data).getIntOr("stage", 0) >= 1;
	}

	public static boolean descending(DiscipleEntity entity) {
		Descent descent = DESCENTS.get(entity.soulId());
		return descent != null && descent.entity() == entity;
	}

	// ================================================================== giving and awakening

	/** The ring handed to a disciple (use on it, or the feed button): only the heir, one ring per heir. */
	static void give(ServerPlayer master, DiscipleEntity entity, ItemStack stack) {
		DiscipleData data = entity.data();
		Component name = DisciplePaths.name(entity);
		String soul = entity.soulId().toString();
		String why = null;
		if (!DisciplePaths.isHeir(data) || !soul.equals(Heir.heirSoul(master))) {
			why = "why.ring_not_heir";
		} else if (has(data)) {
			why = "why.ring_has";
		} else if (soul.equals(DisciplePaths.playerTag(master).getStringOr("ring_given", ""))) {
			why = "why.ring_given";
		}
		if (why != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + why, name).withColor(DisciplePaths.REFUSE));
			return;
		}
		if (!master.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		CompoundTag ring = new CompoundTag();
		ring.putInt("stage", 0);
		ring.putLong("since", DisciplePaths.now(master.level().getServer()));
		DisciplePaths.section(data).put("ring", ring);
		DisciplePaths.playerSection(master).putString("ring_given", soul);
		Cultivators.dirty(master);
		if (entity.level() instanceof ServerLevel level) {
			Fx.send(level, entity.position(), FxType.STAR, PathFxIds.RING_GHOST, 0, 0.5F);
			DisciplePaths.sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.5F);
		}
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "ring.given", name, AWAKEN_TICKS / 24000).withColor(DisciplePaths.JADE));
	}

	/** Seconds until the ring bonds (0 when bonded or no ring). */
	static int awakenSecondsLeft(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (!awakening(data) || !(entity.level() instanceof ServerLevel level)) {
			return 0;
		}
		return DisciplePaths.secondsLeft(level.getServer(), ring(data).getLongOr("since", 0L) + AWAKEN_TICKS);
	}

	/** Once a second: the ring bonds when its time is up (told when the master is online). */
	static void tick20(DiscipleEntity entity, @Nullable ServerPlayer master) {
		DiscipleData data = entity.data();
		if (!has(data) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		CompoundTag ring = ring(data);
		if (ring.getIntOr("stage", 0) == 0 && DisciplePaths.now(level.getServer()) >= ring.getLongOr("since", 0L) + AWAKEN_TICKS) {
			DisciplePaths.child(DisciplePaths.section(data), "ring").putInt("stage", 1);
			DisciplePaths.child(DisciplePaths.section(data), "ring").putBoolean("told", false);
			Fx.send(level, entity.position(), FxType.STAR, PathFxIds.RING_GHOST, 1, 1.0F);
			DisciplePaths.sound(level, entity.position(), SoundEvents.BELL_RESONATE, 1.0F, 0.6F);
			DisciplePaths.syncPower(entity);
		}
		if (master != null && bonded(data) && !ring(data).getBooleanOr("told", true)) {
			DisciplePaths.child(DisciplePaths.section(data), "ring").putBoolean("told", true);
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "ring.bonded", DisciplePaths.name(entity)).withStyle(ChatFormatting.GOLD));
			Advancements.grant(master, "master_in_ring");
		}
	}

	// ================================================================== Perlindungan Jiwa

	private static boolean protect(DiscipleEntity entity, DamageSource source) {
		DiscipleData data = entity.data();
		if (!bonded(data) || !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		long day = DisciplePaths.day(level.getServer());
		if (ring(data).getLongOr("protect_day", -1L) == day) {
			return false;
		}
		DisciplePaths.child(DisciplePaths.section(data), "ring").putLong("protect_day", day);
		entity.setHealth(1.0F);
		entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, PROTECT_TICKS, 3));
		Fx.send(level, entity.position(), FxType.STAR, PathFxIds.RING_GHOST, 2, 1.0F);
		DisciplePaths.sound(level, entity.position(), SoundEvents.TOTEM_USE, 0.8F, 1.3F);
		ServerPlayer master = entity.owner();
		if (master != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "ring.protected", DisciplePaths.name(entity)).withColor(DisciplePaths.GOLD));
		}
		return true;
	}

	// ================================================================== Jiwa Guru Turun

	/** Why Jiwa Guru Turun cannot start now (a translation key), or null. */
	static @Nullable String blocker(ServerPlayer master, DiscipleEntity entity) {
		DiscipleData data = entity.data();
		String p = DisciplePaths.PREFIX;
		if (!bonded(data)) {
			return p + "why.ring_not_bonded";
		}
		if (descending(entity)) {
			return p + "why.descend_active";
		}
		if (master.level() != entity.level() || master.isSpectator()) {
			return p + "why.descend_far";
		}
		long ready = DisciplePaths.tag(data).getCompoundOrEmpty("cd").getLongOr("descend", 0L);
		if (DisciplePaths.now(master.level().getServer()) < ready) {
			return p + "why.descend_cooldown";
		}
		if (Cultivators.get(master).qi() + 1.0E-6D < Qi.max(Cultivators.get(master)) * DESCEND_QI) {
			return p + "why.descend_qi";
		}
		return null;
	}

	/** The "Jiwa Guru Turun" talisman. */
	static void order(ServerPlayer master, DiscipleEntity entity) {
		start(master, entity, false);
	}

	private static boolean start(ServerPlayer master, DiscipleEntity entity, boolean automatic) {
		String why = blocker(master, entity);
		if (why != null) {
			if (!automatic) {
				Msg.chat(master, Component.translatable(why, DisciplePaths.name(entity)).withColor(DisciplePaths.REFUSE));
			}
			return false;
		}
		ServerLevel level = master.level();
		if (!Qi.trySpend(master, Qi.max(Cultivators.get(master)) * DESCEND_QI)) {
			return false;
		}
		long now = DisciplePaths.now(level.getServer());
		DisciplePaths.child(DisciplePaths.section(entity.data()), "cd").putLong("descend", now + DESCEND_COOLDOWN);
		DESCENTS.put(entity.soulId(), new Descent(entity, master.getUUID(), now + DESCEND_TICKS, new int[]{0}));
		int color = Cultivators.get(master).realm().color();
		Fx.send(level, entity.position(), FxType.STAR, PathFxIds.DESCEND, color, DESCEND_TICKS / 20.0F);
		DisciplePaths.sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 0.7F);
		DisciplePaths.sound(level, entity.position(), SoundEvents.BELL_RESONATE, 1.0F, 0.5F);
		LivingEntity target = entity.getTarget();
		if (target != null && target.isAlive()) {
			buddhaPalm(entity, master, level, target);
		}
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "ring.descend", DisciplePaths.name(entity)).withStyle(ChatFormatting.GOLD));
		DisciplePaths.syncPower(entity);
		return true;
	}

	static void tick(MinecraftServer server) {
		if (DESCENTS.isEmpty()) {
			return;
		}
		long now = DisciplePaths.now(server);
		for (Map.Entry<UUID, Descent> entry : List.copyOf(DESCENTS.entrySet())) {
			Descent descent = entry.getValue();
			DiscipleEntity entity = descent.entity();
			ServerPlayer master = server.getPlayerList().getPlayer(descent.master());
			int ticks = ++descent.ticks()[0];
			boolean over = entity.isRemoved() || !entity.isAlive() || master == null || master.level() != entity.level() || now >= descent.until();
			if (!over && ticks % 20 == 0) {
				// The master's upkeep: 2 qi a second and a heavy step; without qi the soul withdraws.
				over = !Qi.trySpend(master, DESCEND_QI_PER_SECOND);
				if (!over) {
					master.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, false, false, true));
				}
			}
			if (over) {
				DESCENTS.remove(entry.getKey());
				DisciplePaths.syncPower(entity);
				if (master != null) {
					Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + "ring.descend_end", DisciplePaths.name(entity)).withColor(DisciplePaths.MUTED));
				}
				continue;
			}
			if (ticks % PULSE_INTERVAL == 0 && entity.level() instanceof ServerLevel level) {
				domainPulse(entity, master, level);
				Fx.send(level, entity.position(), FxType.STAR, PathFxIds.DESCEND, Cultivators.get(master).realm().color(),
						(descent.until() - now) / 20.0F);
			}
		}
	}

	/** The master's D (Techniques.power) for the heir's techniques while the soul descends. */
	private static double damage(DiscipleEntity entity, LivingEntity target, boolean technique) {
		if (!technique || !descending(entity)) {
			return 1.0D;
		}
		Descent descent = DESCENTS.get(entity.soulId());
		if (descent == null || !(entity.level() instanceof ServerLevel level)) {
			return 1.0D;
		}
		ServerPlayer master = level.getServer().getPlayerList().getPlayer(descent.master());
		if (master == null) {
			return 1.0D;
		}
		double own = Math.max(1.0D, DiscipleTechniques.power(entity.data()));
		return Math.max(1.0D, Techniques.power(Cultivators.get(master)) / own);
	}

	/** A Buddha Palm of the master's power on the heir's target (monsters only, after 1 s). */
	private static void buddhaPalm(DiscipleEntity entity, ServerPlayer master, ServerLevel level, LivingEntity aim) {
		Vec3 point = aim.position();
		Fx.send(level, point, entity.position(), FxType.TECHNIQUE, Technique.BUDDHA_PALM.ordinal(), DaoTechniqueSub.CAST, (float) PALM_RADIUS);
		DisciplePaths.sound(level, point.add(0.0D, 6.0D, 0.0D), SoundEvents.BELL_BLOCK, 1.4F, 0.6F);
		double damage = PALM_POWER * Techniques.power(Cultivators.get(master));
		PathTasks.schedule(entity, PALM_DELAY, () -> {
			for (LivingEntity target : DestroyerArts.targets(entity, point, PALM_RADIUS)) {
				DestroyerArts.hurt(entity, target, damage);
				if (target.isAlive()) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), entity);
				}
			}
			Fx.send(level, point, point, FxType.TECHNIQUE, Technique.BUDDHA_PALM.ordinal(), DaoTechniqueSub.HIT, (float) PALM_RADIUS);
			DisciplePaths.sound(level, point, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.3F, 0.6F);
		});
	}

	/** A 6-block Dao Domain pulse of the master's Dao around the heir (monsters only). */
	private static void domainPulse(DiscipleEntity entity, ServerPlayer master, ServerLevel level) {
		CultivationData masterData = Cultivators.get(master);
		Dao dao = masterData.dao();
		double damage = DOMAIN_POWER * Techniques.power(masterData);
		List<LivingEntity> targets = DestroyerArts.targets(entity, entity.position(), DOMAIN_RADIUS);
		if (dao != null) {
			Fx.send(level, entity.position(), entity.position(), FxType.TECHNIQUE, flavour(dao).ordinal(), DaoTechniqueSub.DOMAIN, (float) DOMAIN_RADIUS);
		} else {
			Fx.ring(level, entity.position().add(0.0D, 0.1D, 0.0D), masterData.realm().color(), (float) DOMAIN_RADIUS);
		}
		for (LivingEntity target : targets) {
			if (!(target instanceof Player)) {
				target.addTag(NoGrief.TAG);
			}
			DestroyerArts.hurt(entity, target, damage);
		}
		DisciplePaths.sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.5F);
	}

	/** The Dao technique whose particles draw a Dao's domain pulse (as the master's own Dao Domain does). */
	private static Technique flavour(Dao dao) {
		return switch (dao) {
			case SWORD -> Technique.SWORD_QI;
			case FIRE -> Technique.FLAME_PALM;
			case ICE -> Technique.FROST_LOTUS;
			case LIGHTNING -> Technique.THUNDER_PALM;
			case BODY -> Technique.IRON_BODY;
			case LIFE -> Technique.SPRING_OF_LIFE;
			case SHADOW -> Technique.SHADOW_STEP;
			case SPACE -> Technique.SPACE_BLINK;
		};
	}

	/** Seconds until Jiwa Guru Turun can come again. */
	static int descendSecondsLeft(DiscipleEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return 0;
		}
		return DisciplePaths.secondsLeft(level.getServer(), DisciplePaths.tag(entity.data()).getCompoundOrEmpty("cd").getLongOr("descend", 0L));
	}

	static void clear() {
		DESCENTS.clear();
	}

	// ================================================================== the item

	/** Cincin Jiwa Guru: use it on your heir. */
	public static class SoulRingItem extends Item {

		public SoulRingItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer serverPlayer) {
				Msg.actionBar(serverPlayer, Component.translatable(DisciplePaths.PREFIX + "ring.item_hint").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.PASS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable(DisciplePaths.PREFIX + "ring.tooltip").withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable(DisciplePaths.PREFIX + "ring.tooltip_effect").withStyle(ChatFormatting.DARK_AQUA));
			builder.accept(Component.translatable(DisciplePaths.PREFIX + "ring.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private MasterRing() {
	}
}
