package dev.kultivator.formasi.effect;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.formasi.core.CoreRecord;
import dev.kultivator.formasi.core.CoreTicker;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.Cores;
import dev.kultivator.formasi.core.IntruderSnapshot;
import dev.kultivator.formasi.core.OwnerStore;
import dev.kultivator.formasi.own.Ownership;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.spec.Scope;
import dev.kultivator.formasi.stack.KillLedger;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.formation.ProtectiveFormations;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.run.ExternalControl;
import dev.kultivator.util.FormationDamage;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The {@link CoreContext} of one call on one core (DESIGN 24.2.12). Cheap to make; every entity effect goes through
 * {@link FormationTargets}. {@link #subject()} is the member a field is computed for (module A's handlers read it).
 */
public final class CoreContextImpl implements CoreContext {

	public static final int MAX_HOSTILES = 24;
	public static final int MAX_ALLIES = 16;
	/** Bosses are never rooted longer than this. */
	public static final int BOSS_ROOT_TICKS = 20;
	/** A steered zombie may break doors again after this many ticks without a new steer. */
	public static final int DOOR_RESTORE_TICKS = 200;

	private static final Map<Integer, Long> LAST_ROOT = new HashMap<>();
	private static final Map<Integer, Steered> STEERED = new HashMap<>();
	private static final Map<String, Long> ALERT_CORE = new HashMap<>();
	private static final Map<UUID, Long> ALERT_CHAT = new HashMap<>();

	private record Steered(Zombie zombie, boolean couldBreak, long until) {
	}

	private final ServerLevel level;
	private final CoreRecord record;
	private final FormationSpec spec;
	private final @Nullable Entity subject;
	private @Nullable CoreView view;

	public CoreContextImpl(ServerLevel level, CoreRecord record, FormationSpec spec, @Nullable Entity subject) {
		this.level = level;
		this.record = record;
		this.spec = spec;
		this.subject = subject;
	}

	/** The record behind this context (module A only). */
	public CoreRecord record() {
		return record;
	}

	/** The member a field is being computed for, or null (pulses). */
	public @Nullable Entity subject() {
		return subject;
	}

	/** True when {@code pos} is inside this core's field. */
	public boolean covers(Vec3 pos) {
		return CoreTicker.covers(level, record, spec, pos);
	}

	@Override
	public ServerLevel level() {
		return level;
	}

	@Override
	public CoreView core() {
		if (view == null) {
			view = CoreTicker.view(record);
		}
		return view;
	}

	@Override
	public FormationSpec spec() {
		return spec;
	}

	@Override
	public UUID owner() {
		return record.owner;
	}

	@Override
	public @Nullable ServerPlayer ownerOnline() {
		return level.getServer().getPlayerList().getPlayer(record.owner);
	}

	@Override
	public int hallLevel() {
		return CoreTicker.levelFor(record);
	}

	@Override
	public double radius() {
		return record.radius;
	}

	@Override
	public Vec3 centre() {
		return Vec3.atCenterOf(record.eye);
	}

	@Override
	public double power() {
		return record.power;
	}

	@Override
	public long now() {
		return level.getGameTime();
	}

	@Override
	public RandomSource random() {
		return level.getRandom();
	}

	@Override
	public boolean isMember(Entity entity) {
		return Ownership.isMember(level.getServer(), record.owner, entity);
	}

	@Override
	public List<Mob> hostiles(int max) {
		return IntruderSnapshot.hostiles(this, Math.min(MAX_HOSTILES, Math.max(0, max)));
	}

	@Override
	public List<LivingEntity> allies(int max) {
		int limit = Math.min(MAX_ALLIES, Math.max(0, max));
		if (limit == 0) {
			return List.of();
		}
		double r = Math.min(Stacking.RADIUS_CAP, record.radius);
		double dy = CoreTicker.dyLimit(spec, record);
		Vec3 centre = centre();
		MinecraftServer server = level.getServer();
		AABB box = new AABB(centre.x - r, centre.y - dy, centre.z - r, centre.x + r, centre.y + dy, centre.z + r);
		List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive() && !entity.isSpectator()
				&& (Ownership.isMember(server, record.owner, entity) || Ownership.petOfMember(server, record.owner, entity))
				&& covers(entity.position()));
		list.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(centre)));
		return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
	}

	@Override
	public boolean hurt(Mob mob, float amount) {
		if (!KultivatorConfig.get().formationDamage || amount <= 0.0F || !FormationTargets.mayAffect(mob, this)) {
			return false;
		}
		double factor = FormationTargets.damageFactor(mob);
		if (factor <= 0.0D) {
			return false;
		}
		double damage = amount * factor * KultivatorConfig.get().sectFormationDamageScale;
		damage = Math.min(damage, KillLedger.budget(level, mob, record.owner, hallLevel(), record.id));
		if (damage < 0.05D) {
			return false;
		}
		boolean hit = mob.hurtServer(level, FormationDamage.source(level), (float) damage);
		if (hit) {
			KillLedger.note(level, mob, record.id, damage);
			if (!mob.isAlive()) {
				record.stat("kills", 1);
			}
		}
		return hit;
	}

	@Override
	public void push(Mob mob, Vec3 from, double strength) {
		if (!FormationTargets.mayAffect(mob, this) || !FormationTargets.movable(mob)
				|| ProtectiveFormations.insideActiveFormation(level, mob.position())) {
			return;
		}
		Vec3 away = mob.position().subtract(from);
		double length = Math.sqrt(away.x * away.x + away.z * away.z);
		if (length < 1.0E-4D) {
			return;
		}
		double scale = Math.max(0.0D, Math.min(2.0D, strength)) / length;
		mob.push(away.x * scale, 0.15D, away.z * scale);
		mark(mob);
		record.stat("repels", 1);
	}

	@Override
	public boolean root(Mob mob, int ticks) {
		if (!FormationTargets.mayAffect(mob, this) || ticks <= 0) {
			return false;
		}
		long now = now();
		Long last = LAST_ROOT.get(mob.getId());
		if (last != null && now - last < Stacking.ROOT_COOLDOWN_TICKS) {
			return false;
		}
		int length = ticks;
		if (TechniquePower.isBoss(mob)) {
			length = Math.min(length, Stacking.ROOT_BOSS_MAX_TICKS);
		} else if (EliteMonsters.isElite(mob)) {
			length = Math.min(length, Stacking.ROOT_ELITE_MAX_TICKS);
		}
		boolean rooted = ExternalControl.root(mob, length, BOSS_ROOT_TICKS);
		if (rooted) {
			LAST_ROOT.put(mob.getId(), now);
		}
		return rooted;
	}

	@Override
	public boolean steer(Mob mob, Vec3 waypoint) {
		if (!FormationTargets.mayAffect(mob, this) || !FormationTargets.steerable(mob)) {
			return false;
		}
		if (mob instanceof Zombie zombie) {
			Steered old = STEERED.get(zombie.getId());
			boolean could = old != null ? old.couldBreak() : zombie.canBreakDoors();
			zombie.setCanBreakDoors(false);
			STEERED.put(zombie.getId(), new Steered(zombie, could, now() + DOOR_RESTORE_TICKS));
		}
		mark(mob);
		return mob.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, 1.0D);
	}

	@Override
	public void loseTarget(Mob mob) {
		if (FormationTargets.mayAffect(mob, this)) {
			mob.setTarget(null);
		}
	}

	@Override
	public void fx(int sub, Vec3 at, int b, float f) {
		Fx.send(level, at, FxType.FORMASI, sub, b, f);
	}

	@Override
	public void alert(AlarmKind kind, Component text, @Nullable Vec3 from) {
		long now = now();
		String key = record.dim.identifier() + "/" + record.id + "/" + kind.ordinal();
		Long last = ALERT_CORE.get(key);
		if (last != null && now - last < Stacking.ALARM_CORE_KIND_TICKS) {
			return;
		}
		ALERT_CORE.put(key, now);
		record.stat("alerts", 1);
		MinecraftServer server = level.getServer();
		List<ServerPlayer> receivers = new ArrayList<>();
		ServerPlayer owner = server.getPlayerList().getPlayer(record.owner);
		if (owner != null) {
			receivers.add(owner);
		}
		Sect sect = SectData.get(server).sectOf(record.owner);
		if (sect != null) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player != owner && sect.isMember(player.getUUID())) {
					receivers.add(player);
				}
			}
		}
		Component line = Component.translatable("kultivator.formasi.alert", spec.name(), text).withStyle(ChatFormatting.GOLD);
		boolean chat = KultivatorConfig.get().formationAlarmChat;
		for (ServerPlayer player : receivers) {
			Long told = ALERT_CHAT.get(player.getUUID());
			if (chat && (told == null || now - told >= Stacking.ALARM_PLAYER_CHAT_TICKS)) {
				ALERT_CHAT.put(player.getUUID(), now);
				Msg.chat(player, line);
			} else {
				Msg.actionBar(player, line);
			}
		}
		if (owner == null) {
			OwnerStore.get(server).log(record.owner, server.overworld().getGameTime() / 24000L, "kultivator.formasi.log.alert",
					spec.id(), kind.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	@Override
	public boolean spend(double daya) {
		if (daya <= 0.0D || record.virtual) {
			return true;
		}
		long centi = Math.round(daya * 100.0D);
		if (record.daya < centi) {
			return false;
		}
		record.daya -= centi;
		CoreTicker.markDirty(level);
		return true;
	}

	@Override
	public List<CoreView> ownerCores() {
		return Cores.of(record.owner);
	}

	private static void mark(Mob mob) {
		mob.setPortalCooldown();
		mob.addTag(NoGrief.TAG);
	}

	/** Lets steered zombies break doors again and forgets old roots (every 20 ticks). */
	public static void housekeeping(long now) {
		Iterator<Steered> iterator = STEERED.values().iterator();
		while (iterator.hasNext()) {
			Steered steered = iterator.next();
			if (steered.zombie().isRemoved() || now >= steered.until()) {
				if (!steered.zombie().isRemoved() && steered.couldBreak()) {
					steered.zombie().setCanBreakDoors(true);
				}
				iterator.remove();
			}
		}
		LAST_ROOT.values().removeIf(time -> now - time > Stacking.ROOT_COOLDOWN_TICKS);
		ALERT_CORE.values().removeIf(time -> now - time > Stacking.ALARM_CORE_KIND_TICKS);
		ALERT_CHAT.values().removeIf(time -> now - time > Stacking.ALARM_PLAYER_CHAT_TICKS);
	}

	/** Forgets every session map (server stop). */
	public static void clear() {
		LAST_ROOT.clear();
		STEERED.clear();
		ALERT_CORE.clear();
		ALERT_CHAT.clear();
	}

	/** True for TERRITORY-scope specs (hostile snapshot helper). */
	public boolean territory() {
		return spec.scope() == Scope.TERRITORY;
	}
}
