package dev.kultivator.disciple.path;

import dev.kultivator.cultivation.Progression;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Murid Melampaui Guru, the disciple surpasses the master (DESIGN 20.3.20). Sumber: <i>Battle Through the Heavens</i>
 * (Xiao Yan outgrows Yao Lao and later rescues him from the Hall of Souls) and <i>Martial Peak</i> (Meng Wu Ya, keeper of
 * the Contribution Hall, knew and healed the trial disciple Yang Kai, who later surpassed him).
 * <p>
 * Once per disciple: when {@code rank step + stars} exceeds the master's {@code rank step + stars}, or when it wins
 * Tanding dengan Guru (a duel with the master, both at 80% health or more within 16 blocks, 120 s, ending at 10% health,
 * once a day; both gain +1 comprehension and +3 bond). Then: XP factor +0.1 (see {@link Heir}), Kebanggaan Guru (the
 * master +5% XP per surpassing disciple, at most +15%), Balas Budi (once a day, when the master drops below 25% health
 * within 48 blocks, the disciple appears beside him, shields him and draws the monsters to itself) and the master's
 * Tetua Kehormatan toggle (5% of its meditation XP while meditating within 8 blocks of it).
 */
public final class Surpass {

	public static final double PRIDE_PER_DISCIPLE = 0.05D;
	public static final int PRIDE_MAX_DISCIPLES = 3;
	public static final double RESCUE_HEALTH = 0.25D;
	public static final double RESCUE_RANGE = 48.0D;
	public static final int RESCUE_RESISTANCE_TICKS = 100;
	public static final int RESCUE_ABSORPTION_TICKS = 600;
	public static final double TAUNT_RADIUS = 12.0D;
	public static final int TAUNT_TICKS = 160;
	public static final double HONOR_SHARE = 0.05D;
	public static final double HONOR_RANGE = 8.0D;
	public static final double DUEL_HEALTH = 0.8D;
	public static final double DUEL_RANGE = 16.0D;
	public static final int DUEL_TICKS = 2400;

	private record Taunt(Mob mob, DiscipleEntity entity, long until) {
	}

	private static final List<Taunt> TAUNTS = new ArrayList<>();

	static void register() {
		// The +0.1 XP factor is part of the heir's XP provider (one exact sum with the heir's +0.05).
	}

	public static boolean surpassed(DiscipleData data) {
		return DisciplePaths.tag(data).getBooleanOr("surpassed", false);
	}

	/** Kebanggaan Guru: +5% XP per disciple that surpassed the master, at most +15% (a Progression multiplier). */
	static double masterPride(ServerPlayer player) {
		ListTag surpassers = DisciplePaths.playerTag(player).getListOrEmpty("surpassers");
		if (surpassers.isEmpty()) {
			return 1.0D;
		}
		// Only surpassing disciples still living in the master's list count (not fallen, forgotten or released ones).
		java.util.Set<String> living = new java.util.HashSet<>();
		for (Tag tag : Cultivators.get(player).extra().getCompoundOrEmpty(Disciples.EXTRA).getListOrEmpty("list")) {
			if (tag instanceof CompoundTag entry && !entry.getBooleanOr("dead", false)) {
				living.add(entry.getStringOr("id", ""));
			}
		}
		int count = 0;
		for (Tag tag : surpassers) {
			if (tag.asString().map(living::contains).orElse(false)) {
				count++;
			}
		}
		return 1.0D + PRIDE_PER_DISCIPLE * Math.min(PRIDE_MAX_DISCIPLES, count);
	}

	/** The master's power on the disciples' scale: rank step plus the master's own stars. */
	static int masterPower(ServerPlayer master) {
		return Cultivators.get(master).rank().step() + DisciplePaths.masterStars(master);
	}

	// ================================================================== the trigger

	static void tick20(DiscipleEntity entity, @Nullable ServerPlayer master) {
		if (master == null) {
			return;
		}
		DiscipleData data = entity.data();
		if (DuelRules.partnerOf(entity) == master && entity.getTarget() != master) {
			entity.setTarget(master);
		}
		if (!surpassed(data) && entity.tickCount % 100 < 20 && DisciplePaths.power(data) > masterPower(master)) {
			trigger(entity, master, false);
		}
		if (surpassed(data) && DisciplePaths.tag(data).getBooleanOr("honor", false) && entity.isMeditatingNow()
				&& Cultivators.get(master).meditating() && DisciplePaths.near(master, entity, HONOR_RANGE)) {
			double rate = (0.4D + 0.2D * data.realm().ordinal()) * (entity.mode() == DiscipleEntity.Mode.MEDITATE ? 1.5D : 1.0D)
					* DiscipleCultivation.xpMultiplier(entity, data, XpSource.MEDITATION);
			Progression.addXp(master, rate * HONOR_SHARE, XpSource.SECT);
		}
	}

	private static void trigger(DiscipleEntity entity, ServerPlayer master, boolean byDuel) {
		DiscipleData data = entity.data();
		DisciplePaths.section(data).putBoolean("surpassed", true);
		CompoundTag player = DisciplePaths.playerSection(master);
		ListTag list = player.getListOrEmpty("surpassers").copy();
		String soul = entity.soulId().toString();
		boolean known = false;
		for (Tag tag : list) {
			known |= tag.asString().map(soul::equals).orElse(false);
		}
		if (!known) {
			list.add(StringTag.valueOf(soul));
			player.put("surpassers", list);
			Cultivators.dirty(master);
		}
		Component name = DisciplePaths.name(entity);
		if (entity.level() instanceof ServerLevel level) {
			Fx.send(level, entity.position(), FxType.STAR, PathFxIds.SURPASS, 0, 12.0F);
			DisciplePaths.sound(level, entity.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
			DisciplePaths.sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.4F);
			Msg.broadcast(level.getServer(), Component.translatable(DisciplePaths.PREFIX + (byDuel ? "surpass.announce_duel" : "surpass.announce"),
					name, master.getDisplayName()).withStyle(ChatFormatting.GOLD));
		}
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "surpass.effects", name).withColor(DisciplePaths.JADE));
		Advancements.grant(master, "disciple_surpasses_master");
		DisciplePaths.syncPower(entity);
	}

	// ================================================================== Balas Budi

	/** The master was hurt: below 25% every surpassing disciple within 48 blocks repays its debt (once a day each). */
	static void onMasterHurt(ServerPlayer master) {
		if (master.getHealth() >= master.getMaxHealth() * RESCUE_HEALTH || master.isSpectator()) {
			return;
		}
		long day = DisciplePaths.day(master.level().getServer());
		for (DiscipleEntity entity : Disciples.loaded(master.getUUID())) {
			DiscipleData data = entity.data();
			if (!surpassed(data) || DisciplePaths.tag(data).getLongOr("rescue_day", -1L) == day || !DisciplePaths.near(master, entity, RESCUE_RANGE)
					|| data.breakingThrough() || Calamity.running(entity) || DuelRules.inDuel(entity) || entity.isReleasing()) {
				continue;
			}
			rescue(entity, master, day);
		}
	}

	private static void rescue(DiscipleEntity entity, ServerPlayer master, long day) {
		if (entity.distanceToSqr(master) > 9.0D && !entity.teleportNear(master)) {
			return;
		}
		DisciplePaths.section(entity.data()).putLong("rescue_day", day);
		int amplifier = Math.max(0, Math.min(19, (int) Math.floor(0.25D * entity.getMaxHealth() / 4.0D) - 1));
		master.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, RESCUE_ABSORPTION_TICKS, amplifier), entity);
		master.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RESCUE_RESISTANCE_TICKS, 1), entity);
		ServerLevel level = master.level();
		long until = DisciplePaths.now(level.getServer()) + TAUNT_TICKS;
		int taunted = 0;
		for (LivingEntity monster : DiscipleTechniques.hostilesAround(entity, master, master.position(), TAUNT_RADIUS)) {
			if (monster instanceof Mob mob) {
				mob.setTarget(entity);
				TAUNTS.add(new Taunt(mob, entity, until));
				taunted++;
			}
		}
		Bond.add(entity.data(), Bond.RESCUE);
		Fx.send(level, master.position().add(0.0D, 1.0D, 0.0D), entity.position().add(0.0D, 1.0D, 0.0D), FxType.STAR, PathFxIds.REPAY, taunted, 1.0F);
		DisciplePaths.sound(level, master.position(), SoundEvents.TOTEM_USE, 0.6F, 1.5F);
		DisciplePaths.sound(level, entity.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "surpass.repay", DisciplePaths.name(entity)).withStyle(ChatFormatting.GOLD));
	}

	/** Taunted monsters keep turning to the disciple for 8 s. */
	static void tick(MinecraftServer server) {
		if (TAUNTS.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		long now = DisciplePaths.now(server);
		TAUNTS.removeIf(taunt -> {
			if (now >= taunt.until() || !taunt.mob().isAlive() || !taunt.entity().isAlive() || taunt.entity().isRemoved()
					|| taunt.mob().level() != taunt.entity().level()) {
				return true;
			}
			if (taunt.mob().getTarget() != taunt.entity()) {
				taunt.mob().setTarget(taunt.entity());
			}
			return false;
		});
	}

	// ================================================================== Tetua Kehormatan and Tanding dengan Guru

	static void setHonor(ServerPlayer master, DiscipleEntity entity, boolean on) {
		if (!surpassed(entity.data())) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "why.honor_locked", DisciplePaths.name(entity)).withColor(DisciplePaths.REFUSE));
			return;
		}
		DisciplePaths.section(entity.data()).putBoolean("honor", on);
		Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + (on ? "surpass.honor_on" : "surpass.honor_off"), DisciplePaths.name(entity)));
	}

	/** Why Tanding dengan Guru cannot start now (a translation key), or null. */
	static @Nullable String duelBlocker(ServerPlayer master, DiscipleEntity entity) {
		DiscipleData data = entity.data();
		String p = DisciplePaths.PREFIX;
		if (!DisciplePaths.near(master, entity, DUEL_RANGE)) {
			return p + "why.duel_far";
		}
		if (DuelRules.inDuel(master) || DuelRules.inDuel(entity) || data.breakingThrough() || Calamity.running(entity)) {
			return p + "why.busy";
		}
		if (DisciplePaths.tag(data).getLongOr("duel_day", -1L) == DisciplePaths.day(master.level().getServer())) {
			return p + "why.duel_today";
		}
		if (master.getHealth() < master.getMaxHealth() * DUEL_HEALTH || entity.getHealth() < entity.getMaxHealth() * DUEL_HEALTH) {
			return p + "why.duel_health";
		}
		return null;
	}

	static void startDuel(ServerPlayer master, DiscipleEntity entity) {
		String why = duelBlocker(master, entity);
		Component name = DisciplePaths.name(entity);
		if (why != null) {
			Msg.chat(master, Component.translatable(why, name).withColor(DisciplePaths.REFUSE));
			return;
		}
		UUID soul = entity.soulId();
		boolean begun = DuelRules.begin(master, entity, DUEL_TICKS, (winner, loser, reason) -> onDuelEnd(master.getUUID(), soul, entity, winner, reason));
		if (!begun) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "why.busy", name).withColor(DisciplePaths.REFUSE));
			return;
		}
		DisciplePaths.section(entity.data()).putLong("duel_day", DisciplePaths.day(master.level().getServer()));
		entity.setTarget(master);
		Vec3 centre = master.position().add(entity.position()).scale(0.5D);
		Fx.send(master.level(), centre, FxType.STAR, PathFxIds.DUEL, 0, (float) Math.max(4.0D, master.distanceTo(entity) / 2.0D + 3.0D));
		DisciplePaths.sound(master.level(), centre, SoundEvents.BELL_BLOCK, 1.2F, 1.0F);
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "surpass.duel_start", name, DUEL_TICKS / 20).withStyle(ChatFormatting.GOLD));
	}

	private static void onDuelEnd(UUID masterId, UUID soul, DiscipleEntity entity, @Nullable LivingEntity winner, DuelRules.Reason reason) {
		if (entity.isAlive()) {
			entity.setTarget(null);
		}
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer master = level.getServer().getPlayerList().getPlayer(masterId);
		Component name = DisciplePaths.name(entity);
		if (reason == DuelRules.Reason.CANCELLED || master == null) {
			if (master != null) {
				Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "surpass.duel_cancelled", name).withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		DiscipleData data = entity.data();
		if (data.dao() != null) {
			data.addComprehension(1.0D);
		}
		CultivationData masterData = Cultivators.get(master);
		Dao dao = masterData.dao();
		if (dao != null) {
			masterData.addComprehension(dao, 1.0D);
			Cultivators.dirty(master);
		}
		Bond.add(data, Bond.MASTER_DUEL);
		boolean won = winner == entity;
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + (won ? "surpass.duel_won" : "surpass.duel_lost"), name)
				.withColor(won ? DisciplePaths.GOLD : DisciplePaths.JADE));
		if (won && !surpassed(data)) {
			trigger(entity, master, true);
		}
	}

	static void clear() {
		TAUNTS.clear();
	}

	private Surpass() {
	}
}
