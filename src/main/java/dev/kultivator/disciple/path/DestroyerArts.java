package dev.kultivator.disciple.path;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Dao;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.power.DisciplePowers;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.kehancuran.Shapes;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Jurus Pemusnah, the world-destroying ultimates (DESIGN 20.3.21), for disciples that passed calamity 4 (the Second
 * Trial). One shared cooldown of 10 minutes. In normal mode (the default) every art is block-safe: big particles and
 * damage to monsters only ({@code mayHit}), cast automatically against a boss within 32 blocks or ordered from the Jalan
 * tab. Only an ordered cast with all three locks of Mode Kehancuran open also changes blocks, through
 * {@link Kehancuran} and its {@code WorldBreaker}; automatic casts are always block-safe.
 * <p>
 * Sumber per art: <i>A Record of a Mortal's Journey</i> (the Xuantian Slaying Spirit Sword), <i>Battle Through the
 * Heavens</i> (the Buddha's Fury Flame Lotus), <i>Coiling Dragon</i> (the earth mystery of the Pulse), <i>Martial
 * Universe</i> (the Great Desolation Scripture, the Great Desolate Imprisoning Heavenly Hand, the Devouring Ancestral
 * Symbol), <i>Soul Land</i> (the Great Sumeru Hammer of the Clear Sky Sect), <i>Against the Gods</i> (the Hell Monarch
 * realm of the seventh Heretic God gate), <i>I Shall Seal the Heavens</i> (the Mountain and Sea Lord's Nine Hexes).
 */
public final class DestroyerArts {

	public static final int MIN_CALAMITIES = 4;
	public static final int COOLDOWN_TICKS = 12_000;
	public static final double AUTO_BOSS_RANGE = 32.0D;
	/** An art is never centred within this many blocks of the master or a villager. */
	public static final double SAFE_CENTRE = 4.0D;
	public static final int MIN_FLAMES = 2;
	public static final int HELL_GATE = 7;
	public static final int SUMERU_BLOWS = 9;
	public static final int SUMERU_INTERVAL = 8;
	public static final int DESOLATION_BUFF_TICKS = 600;
	public static final double DESOLATION_BUFF = 1.3D;
	public static final double ORDER_RANGE = 48.0D;

	/** The arts; the ordinal is sent to clients (never reorder). */
	public enum Art {
		XUANTIAN("xuantian", 0xD8E6FF, Shapes.Kind.TRENCH),
		LOTUS("lotus", 0xFF6A2A, Shapes.Kind.SPHERE),
		QUAKE("quake", 0xD9A05B, Shapes.Kind.DISC),
		DESOLATION("desolation", 0x6CFF8A, Shapes.Kind.WITHER),
		PRISON_HAND("prison_hand", 0x7A5CFF, Shapes.Kind.PIT),
		SUMERU("sumeru", 0xB57CFF, Shapes.Kind.SPHERE),
		HELL_MONARCH("hell_monarch", 0xC21F1F, Shapes.Kind.SPHERE),
		DEVOURING("devouring", 0x5B2A86, Shapes.Kind.COLUMN),
		MOUNTAIN_SEA("mountain_sea", 0xFFD54A, Shapes.Kind.NINE);

		private final String id;
		private final int color;
		private final Shapes.Kind kind;

		Art(String id, int color, Shapes.Kind kind) {
			this.id = id;
			this.color = color;
			this.kind = kind;
		}

		public String id() {
			return id;
		}

		public int color() {
			return color;
		}

		public Shapes.Kind kind() {
			return kind;
		}

		public Component displayName() {
			return Component.translatable("kultivator.dpath.art." + id);
		}

		public static @Nullable Art byId(String id) {
			for (Art art : values()) {
				if (art.id.equals(id)) {
					return art;
				}
			}
			return null;
		}

		public static @Nullable Art byOrdinal(int ordinal) {
			Art[] values = values();
			return ordinal < 0 || ordinal >= values.length ? null : values[ordinal];
		}
	}

	/** Soul id -> game time until which the Great Desolation Scripture's +30% damage lasts. */
	private static final Map<UUID, Long> DESOLATION_BUFFS = new HashMap<>();

	static void register() {
		DiscipleHooks.addCombatCast(DestroyerArts::combatCast);
		DiscipleHooks.addDamageFactor((entity, target, technique) -> {
			Long until = DESOLATION_BUFFS.get(entity.soulId());
			if (until == null || !(entity.level() instanceof ServerLevel level)) {
				return 1.0D;
			}
			if (DisciplePaths.now(level.getServer()) >= until) {
				DESOLATION_BUFFS.remove(entity.soulId());
				return 1.0D;
			}
			return DESOLATION_BUFF;
		});
	}

	// ================================================================== which arts

	/** True once the disciple passed calamity 4. */
	public static boolean eligible(DiscipleData data) {
		return DisciplePaths.calamitiesPassed(data) >= MIN_CALAMITIES;
	}

	/** The arts this disciple commands (its Dao's art and the special ones), empty before calamity 4. */
	public static List<Art> available(DiscipleData data) {
		List<Art> list = new ArrayList<>();
		if (!eligible(data)) {
			return list;
		}
		Dao dao = data.dao();
		if (dao == null) {
			list.add(Art.SUMERU);
		} else {
			switch (dao) {
				case SWORD -> list.add(Art.XUANTIAN);
				case FIRE -> {
					if (DisciplePowers.flames(data) >= MIN_FLAMES) {
						list.add(Art.LOTUS);
					}
				}
				case BODY -> list.add(Art.QUAKE);
				case LIFE -> list.add(Art.DESOLATION);
				case SPACE -> list.add(Art.PRISON_HAND);
				case ICE, LIGHTNING, SHADOW -> list.add(Art.SUMERU);
			}
		}
		if (DisciplePowers.gates(data) >= HELL_GATE) {
			list.add(Art.HELL_MONARCH);
		}
		if (Talents.physique(data) == DisciplePaths.PHYSIQUE_DEVOURING) {
			list.add(Art.DEVOURING);
		}
		if (DisciplePaths.isHeir(data) && Heir.hexes(data).size() >= Heir.MAX_HEXES) {
			list.add(Art.MOUNTAIN_SEA);
		}
		return list;
	}

	/** The art the master chose on the Jalan tab (the first available when none or no longer available). */
	public static @Nullable Art chosen(DiscipleData data) {
		List<Art> list = available(data);
		if (list.isEmpty()) {
			return null;
		}
		Art saved = Art.byId(DisciplePaths.tag(data).getStringOr("art", ""));
		return saved != null && list.contains(saved) ? saved : list.get(0);
	}

	/**
	 * The Mode Kehancuran shape of {@code art} for {@code stars} stars with the server's maximum radius R
	 * (DESIGN 20.3.21), or the same numbers for the preview.
	 */
	public static Shapes.Spec spec(Art art, int stars) {
		int r = KultivatorConfig.get().kehancuranMaxRadius;
		int s = Math.max(0, stars);
		return switch (art) {
			case XUANTIAN -> Shapes.Spec.trench(Math.min(48, 16 + s / 2), 3, 4);
			case LOTUS, SUMERU -> Shapes.Spec.sphere(Math.min(r, 4 + s / 9));
			case QUAKE -> Shapes.Spec.disc(Math.min(r + 4, 6 + s / 6), 3, true);
			case DESOLATION -> Shapes.Spec.wither(Math.min(r + 8, 8 + s / 6));
			case PRISON_HAND -> Shapes.Spec.pit(4, Math.min(12, 3 + s / 18));
			case HELL_MONARCH -> Shapes.Spec.sphere(Math.min(r, 4 + s / 5));
			case DEVOURING -> Shapes.Spec.column(3, 16);
			case MOUNTAIN_SEA -> Shapes.Spec.nine(Math.min(4, r), 8);
		};
	}

	/** Seconds until the shared cooldown ends. */
	public static int cooldownSeconds(DiscipleEntity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return 0;
		}
		return DisciplePaths.secondsLeft(level.getServer(), DisciplePaths.tag(entity.data()).getCompoundOrEmpty("cd").getLongOr("art", 0L));
	}

	/** Starts the shared 10-minute cooldown. */
	public static void startCooldown(DiscipleEntity entity) {
		if (entity.level() instanceof ServerLevel level) {
			DisciplePaths.child(DisciplePaths.section(entity.data()), "cd").putLong("art", DisciplePaths.now(level.getServer()) + COOLDOWN_TICKS);
		}
	}

	// ================================================================== ordering

	static void choose(ServerPlayer master, DiscipleEntity entity, String id) {
		Art art = Art.byId(id);
		if (art == null || !available(entity.data()).contains(art)) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "why.art_none", DisciplePaths.name(entity)).withColor(DisciplePaths.REFUSE));
			return;
		}
		DisciplePaths.section(entity.data()).putString("art", art.id());
		Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + "art.chosen", DisciplePaths.name(entity), art.displayName()));
	}

	/**
	 * The "Jurus Pemusnah" talisman: with all three locks of Mode Kehancuran open the master aims a destructive cast;
	 * otherwise the block-safe form strikes the disciple's foe, the nearest monster or where the master looks.
	 */
	static void order(ServerPlayer master, DiscipleEntity entity) {
		DiscipleData data = entity.data();
		Art art = chosen(data);
		Component name = DisciplePaths.name(entity);
		String why = null;
		if (!eligible(data)) {
			why = "why.art_locked";
		} else if (art == null) {
			why = "why.art_none";
		} else if (cooldownSeconds(entity) > 0) {
			why = "why.art_cooldown";
		} else if (data.breakingThrough() || Calamity.running(entity)) {
			why = "why.busy";
		}
		if (why != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + why, name, cooldownSeconds(entity)).withColor(DisciplePaths.REFUSE));
			return;
		}
		if (Kehancuran.destructiveAllowed(master, entity)) {
			Kehancuran.beginAim(master, entity, art);
			return;
		}
		LivingEntity aim = entity.getTarget();
		if (aim == null || !aim.isAlive()) {
			List<LivingEntity> near = targets(entity, entity.position(), 24.0D);
			aim = near.isEmpty() ? null : near.get(0);
		}
		Vec3 centre = aim != null ? aim.position() : Targeting.aimPoint(master, ORDER_RANGE);
		if (!castSafe(entity, art, centre, false)) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "why.art_centre", name).withColor(DisciplePaths.REFUSE));
			return;
		}
		startCooldown(entity);
	}

	/** Automatic: against a boss within 32 blocks, block-safe always. */
	private static boolean combatCast(DiscipleEntity entity, LivingEntity target) {
		if (entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return false;
		}
		DiscipleData data = entity.data();
		if (!eligible(data) || data.deviating() || cooldownSeconds(entity) > 0 || entity.owner() == null) {
			return false;
		}
		Art art = chosen(data);
		if (art == null) {
			return false;
		}
		LivingEntity boss = DivineClones.isBoss(target) && entity.mayAttack(target) ? target : DivineClones.bossNear(entity, AUTO_BOSS_RANGE);
		if (boss == null || !castSafe(entity, art, boss.position(), true)) {
			return false;
		}
		startCooldown(entity);
		ServerPlayer master = entity.owner();
		if (master != null) {
			Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + "art.auto", DisciplePaths.name(entity), art.displayName())
					.withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	// ================================================================== the block-safe forms

	/**
	 * The block-safe form of {@code art} at {@code centre}: particles and damage to monsters only. False (nothing done)
	 * when the centre lies within 4 blocks of the master or a villager. Also called by Mode Kehancuran at its cast.
	 */
	public static boolean castSafe(DiscipleEntity entity, Art art, Vec3 centre, boolean automatic) {
		ServerPlayer master = entity.owner();
		if (master == null || !(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		boolean selfCentred = art == Art.DESOLATION || art == Art.HELL_MONARCH;
		if (!selfCentred && unsafeCentre(level, master, centre)) {
			return false;
		}
		DiscipleData data = entity.data();
		double u = DiscipleTechniques.power(data);
		double range = SoulLamps.rangeFactor(data);
		int stars = DisciplePaths.stars(data);
		Shapes.Spec spec = spec(art, stars);
		Vec3 origin = entity.position().add(0.0D, 1.0D, 0.0D);
		switch (art) {
			case XUANTIAN -> {
				Vec3 flat = new Vec3(centre.x - entity.getX(), 0.0D, centre.z - entity.getZ());
				Vec3 dir = flat.lengthSqr() < 1.0E-4D ? entity.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize() : flat.normalize();
				Vec3 start = origin.add(dir.scale(3.0D));
				Vec3 end = start.add(dir.scale(spec.length() * range));
				fx(level, start, end, art, 2.0F);
				for (LivingEntity target : targets(entity, start.add(end).scale(0.5D), start.distanceTo(end) / 2.0D + 3.0D)) {
					if (target.getBoundingBox().inflate(2.0D).clip(start, end).isPresent() || target.getBoundingBox().inflate(2.0D).contains(start)) {
						hurt(entity, target, 8.0D * u);
					}
				}
				DisciplePaths.sound(level, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.5F);
				DisciplePaths.sound(level, origin, SoundEvents.TRIDENT_THUNDER.value(), 1.0F, 1.4F);
			}
			case LOTUS -> {
				double radius = (spec.radius() + 2.0D) * range;
				fx(level, centre, origin, art, (float) radius);
				for (LivingEntity target : targets(entity, centre, radius)) {
					if (hurt(entity, target, 8.0D * u) && target.isAlive()) {
						target.igniteForSeconds(8.0F);
					}
				}
				DisciplePaths.sound(level, centre, SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 0.6F);
				DisciplePaths.sound(level, centre, SoundEvents.FIRECHARGE_USE, 1.2F, 0.5F);
			}
			case QUAKE -> {
				double radius = spec.radius() * range;
				fx(level, centre, origin, art, (float) radius);
				for (LivingEntity target : targets(entity, centre, radius)) {
					if (hurt(entity, target, 6.0D * u) && target.isAlive()) {
						target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 3), entity);
						if (!DivineClones.isBoss(target) && !(target instanceof Player)) {
							target.addTag(NoGrief.TAG);
							target.setPortalCooldown();
							target.setDeltaMovement(target.getDeltaMovement().x, 0.8D, target.getDeltaMovement().z);
							target.syncVelocity = true;
						}
					}
				}
				DisciplePaths.sound(level, centre, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.6F, 0.5F);
				DisciplePaths.sound(level, centre, SoundEvents.ANVIL_LAND, 0.8F, 0.4F);
			}
			case DESOLATION -> {
				double radius = 10.0D * range;
				fx(level, entity.position(), origin, art, (float) radius);
				for (LivingEntity target : targets(entity, entity.position(), radius)) {
					hurt(entity, target, 2.0D * u);
				}
				entity.heal(entity.getMaxHealth());
				if (master.level() == level && master.distanceToSqr(entity) <= radius * radius) {
					master.heal(master.getMaxHealth() * 0.3F);
				}
				for (DiscipleEntity ally : Disciples.loaded(master.getUUID())) {
					if (ally != entity && ally.level() == level && ally.distanceToSqr(entity) <= radius * radius) {
						ally.heal(ally.getMaxHealth() * 0.3F);
					}
				}
				DESOLATION_BUFFS.put(entity.soulId(), DisciplePaths.now(level.getServer()) + DESOLATION_BUFF_TICKS);
				DisciplePaths.sound(level, entity.position(), SoundEvents.ZOMBIE_VILLAGER_CURE, 0.8F, 0.6F);
			}
			case PRISON_HAND -> {
				double radius = 4.5D * range;
				fx(level, centre, origin, art, (float) radius);
				for (LivingEntity target : targets(entity, centre, radius + 1.0D)) {
					if (Math.abs(target.getX() - centre.x) <= radius && Math.abs(target.getZ() - centre.z) <= radius
							&& hurt(entity, target, 8.0D * u) && target.isAlive()) {
						target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 6), entity);
						target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2), entity);
					}
				}
				DisciplePaths.sound(level, centre, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.2F, 0.5F);
				DisciplePaths.sound(level, centre, SoundEvents.CHAIN_PLACE, 1.2F, 0.5F);
			}
			case SUMERU -> {
				double radius = (spec.radius() + 4.0D) * range;
				for (int blow = 1; blow <= SUMERU_BLOWS; blow++) {
					int index = blow;
					PathTasks.schedule(entity, blow * SUMERU_INTERVAL, () -> {
						Fx.send(level, entity.position(), FxType.STAR, PathFxIds.ART_CHARGE, index, (float) radius);
						DisciplePaths.sound(level, entity.position(), SoundEvents.ANVIL_LAND, 0.5F + 0.05F * index, 0.5F + 0.08F * index);
					});
				}
				PathTasks.schedule(entity, (SUMERU_BLOWS + 1) * SUMERU_INTERVAL, () -> {
					fx(level, centre, entity.position().add(0.0D, 1.0D, 0.0D), art, (float) radius);
					for (LivingEntity target : targets(entity, centre, radius)) {
						hurt(entity, target, 15.0D * u);
					}
					DisciplePaths.sound(level, centre, SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.5F);
					DisciplePaths.sound(level, centre, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.4F);
				});
			}
			case HELL_MONARCH -> {
				double radius = 24.0D * range;
				fx(level, entity.position(), origin, art, (float) radius);
				for (LivingEntity target : targets(entity, entity.position(), radius)) {
					hurt(entity, target, 6.0D * u);
				}
				DisciplePaths.sound(level, entity.position(), SoundEvents.WITHER_SPAWN, 0.8F, 0.6F);
				DisciplePaths.sound(level, entity.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.8F, 0.5F);
			}
			case DEVOURING -> {
				double radius = 8.0D * range;
				fx(level, centre, origin, art, (float) radius);
				for (int pulse = 0; pulse < 8; pulse++) {
					boolean damage = pulse % 2 == 1;
					PathTasks.schedule(entity, 10 * (pulse + 1), () -> {
						for (LivingEntity target : targets(entity, centre, radius)) {
							pull(target, centre);
							if (damage) {
								hurt(entity, target, 1.0D * u);
							}
						}
					});
				}
				DisciplePaths.sound(level, centre, SoundEvents.PORTAL_TRIGGER, 1.0F, 0.6F);
			}
			case MOUNTAIN_SEA -> {
				double radius = spec.radius() * range;
				int index = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						Vec3 seal = centre.add(dx * spec.spacing(), 0.0D, dz * spec.spacing());
						PathTasks.schedule(entity, 1 + 4 * index, () -> {
							fx(level, seal, entity.position(), art, (float) radius);
							for (LivingEntity target : targets(entity, seal, radius)) {
								hurt(entity, target, 3.0D * u);
							}
							DisciplePaths.sound(level, seal, SoundEvents.BELL_BLOCK, 1.0F, 0.7F);
						});
						index++;
					}
				}
			}
		}
		DisciplePaths.sound(level, origin, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.6F);
		return true;
	}

	private static void fx(ServerLevel level, Vec3 pos, Vec3 target, Art art, float size) {
		Fx.send(level, pos, target, FxType.STAR, PathFxIds.ART, art.ordinal(), size);
	}

	/** Within 4 blocks of the master or a villager (an art is never centred there). */
	private static boolean unsafeCentre(ServerLevel level, ServerPlayer master, Vec3 centre) {
		if (master.level() == level && master.position().distanceToSqr(centre) <= SAFE_CENTRE * SAFE_CENTRE) {
			return true;
		}
		return !level.getEntitiesOfClass(AbstractVillager.class, new AABB(centre, centre).inflate(SAFE_CENTRE)).isEmpty();
	}

	/** Living things the disciple may hit within {@code radius} of {@code centre} (monsters only; none without a master). */
	static List<LivingEntity> targets(DiscipleEntity entity, Vec3 centre, double radius) {
		ServerPlayer master = entity.owner();
		if (master == null) {
			return List.of();
		}
		return DiscipleTechniques.hostilesAround(entity, master, centre, Math.min(64.0D, radius));
	}

	/**
	 * Technique damage from the disciple (a mob attack, so kills are its own) times its {@code damageFactor} hooks, only
	 * on targets {@code mayHit} accepts. True when it landed.
	 */
	static boolean hurt(DiscipleEntity entity, LivingEntity target, double amount) {
		ServerPlayer master = entity.owner();
		if (!(entity.level() instanceof ServerLevel level) || !DiscipleTechniques.mayHit(entity, master, target)) {
			return false;
		}
		float damage = (float) (amount * DiscipleHooks.damageFactor(entity, target, true));
		if (!(damage > 0.0F) || !Float.isFinite(damage)) {
			return false;
		}
		if (target.damageCooldownTime > 10) {
			target.damageCooldownTime = 10;
		}
		if (!(target instanceof Player)) {
			// Never on a player (the master in a duel): a saved tag would keep him from portals and plates.
			target.addTag(NoGrief.TAG);
			target.setPortalCooldown();
		}
		DamageSource source = level.damageSources().mobAttack(entity);
		boolean landed = target.hurtServer(level, source, damage);
		if (landed) {
			entity.setLastHurtMob(target);
		}
		return landed;
	}

	/** Draws a monster towards the vortex (never the dragon or a player). */
	private static void pull(LivingEntity target, Vec3 centre) {
		if (target instanceof EnderDragon || target instanceof Player) {
			return;
		}
		Vec3 to = centre.subtract(target.position());
		double length = to.length();
		if (length < 0.5D) {
			return;
		}
		Vec3 push = to.scale(0.35D / length);
		target.addTag(NoGrief.TAG);
		target.setPortalCooldown();
		target.setDeltaMovement(target.getDeltaMovement().scale(0.5D).add(push.x, 0.05D, push.z));
		target.syncVelocity = true;
	}

	static void clear() {
		DESOLATION_BUFFS.clear();
	}

	private DestroyerArts() {
	}
}
