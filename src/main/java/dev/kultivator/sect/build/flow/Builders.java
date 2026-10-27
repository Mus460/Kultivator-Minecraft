package dev.kultivator.sect.build.flow;

import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pekerja Bangunan (DESIGN 21.14 item 4): up to four disciples of any rank set on the Pekerja panel. They are optional
 * helpers: with no builders the sect still builds completely.
 * <ul>
 * <li>A builder gets activity 10 BUILD at {@code EngineApi.workSpot(owner)} (a perimeter cell of the open component that
 * moves every 20 s); one whose path fails for 10 s cloud-steps there.</li>
 * <li>Each builder within 24 blocks of the work spot adds 25% to the rate (passed with the session speed and the FPS hint
 * through {@code EngineApi.setRateMultiplier}).</li>
 * <li>It swings its hand every 15-25 ticks with material dust; it earns +1 PK per 2,400 working ticks (at most 30 a day)
 * and 0.5 XP a minute ({@code XpSource.SECT}).</li>
 * <li>While the site is paused, waiting or complete, builders meditate at the plaza (activity 8 at a plaza cell).</li>
 * </ul>
 * Source: an adaptation of the chore model of <i>Renegade Immortal</i> (Wang Lin's water vats) and <i>A Will Eternal</i>
 * (the Ovens); no canon about construction work is claimed.
 */
public final class Builders {

	public static final int MAX = 4;
	public static final double RATE_EACH = 0.25D;
	public static final double WORK_RANGE = 24.0D;
	public static final int PK_TICKS = 2400;
	public static final int PK_DAILY = 30;
	public static final double XP_PER_MINUTE = 0.5D;
	/** A builder that came no closer for this long cloud-steps to its spot (ticks). */
	public static final int STUCK_TICKS = 200;
	public static final double FPS_FACTOR = 0.5D;
	private static final int THREAD_COLOR = 0xFFD54A;

	/** Work state of a builder body this session. */
	private static final class Worker {
		final DiscipleEntity entity;
		final UUID owner;
		int nextSwing;
		double bestDistance = Double.MAX_VALUE;
		int stuckSince;
		int activitySet;
		boolean working;

		Worker(DiscipleEntity entity, UUID owner) {
			this.entity = entity;
			this.owner = owner;
		}
	}

	private static final Map<UUID, Worker> WORKERS = new HashMap<>();
	private static final Map<UUID, Double> SPEED = new HashMap<>();
	private static final Map<UUID, Boolean> FPS_LOW = new HashMap<>();
	private static final Map<UUID, Double> SENT_FACTOR = new HashMap<>();
	private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

	// ================================================================== the list

	public static List<UUID> souls(UUID owner) {
		List<UUID> souls = new ArrayList<>();
		for (Tag tag : FlowData.of(owner).getListOrEmpty("builders")) {
			if (tag instanceof CompoundTag entry) {
				try {
					souls.add(UUID.fromString(entry.getStringOr("soul", "")));
				} catch (IllegalArgumentException ignored) {
					// a broken entry is skipped
				}
			}
		}
		return souls;
	}

	public static boolean isBuilder(UUID owner, UUID soul) {
		return souls(owner).contains(soul);
	}

	private static @Nullable CompoundTag entry(UUID owner, UUID soul) {
		for (Tag tag : FlowData.of(owner).getListOrEmpty("builders")) {
			if (tag instanceof CompoundTag entry && soul.toString().equals(entry.getStringOr("soul", ""))) {
				return entry;
			}
		}
		return null;
	}

	/** Why this disciple cannot become a builder, or null. */
	public static @Nullable Component blocker(DiscipleEntity disciple) {
		DiscipleData data = disciple.data();
		if (!disciple.isAlive() || disciple.isReleasing() || disciple.role() != DiscipleEntity.Role.DISCIPLE || data.breakingThrough()
				|| DuelRules.inDuel(disciple)) {
			return Component.translatable("kultivator.sectbuild.builders.busy", disciple.getDisplayName());
		}
		if (Duties.dutyOf(data) != null || disciple.activity() != DiscipleEntity.ACTIVITY_NONE) {
			return Component.translatable("kultivator.sectbuild.builders.on_duty", disciple.getDisplayName());
		}
		return null;
	}

	/** Sets a disciple on the Pekerja panel (at most four). */
	public static boolean assign(ServerPlayer player, UUID owner, DiscipleEntity disciple) {
		if (isBuilder(owner, disciple.soulId())) {
			return true;
		}
		if (souls(owner).size() >= MAX) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.builders.full", MAX).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Component why = blocker(disciple);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
			return false;
		}
		CompoundTag entry = new CompoundTag();
		entry.putString("soul", disciple.soulId().toString());
		entry.store("name", ComponentSerialization.CODEC, disciple.getDisplayName());
		entry.putLong("ticks", 0L);
		FlowData.list(FlowData.of(owner), "builders").add(entry);
		FlowData.dirty();
		Msg.chat(player, Component.translatable("kultivator.sectbuild.builders.assigned", disciple.getDisplayName()).withStyle(ChatFormatting.AQUA));
		if (souls(owner).size() >= MAX) {
			Advancements.grant(player, "four_builders");
		}
		return true;
	}

	/** Takes a disciple off the list; its body (when loaded) stops working. */
	public static void release(UUID owner, UUID soul, boolean tell) {
		ListTag list = FlowData.list(FlowData.of(owner), "builders");
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i) instanceof CompoundTag entry && soul.toString().equals(entry.getStringOr("soul", ""))) {
				list.remove(i);
				FlowData.dirty();
			}
		}
		Worker worker = WORKERS.remove(soul);
		DiscipleEntity body = worker != null ? worker.entity : find(owner, soul);
		if (body != null && !body.isRemoved() && (body.activity() == DiscipleEntity.ACTIVITY_BUILD
				|| worker != null && body.activity() == worker.activitySet)) {
			body.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		ServerPlayer master = tell ? Ceremonies.online(owner) : null;
		if (master != null && body != null) {
			Msg.chat(master, Component.translatable("kultivator.sectbuild.builders.released", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Releases every builder (the template is gone). */
	public static void releaseAll(UUID owner) {
		for (UUID soul : souls(owner)) {
			release(owner, soul, false);
		}
	}

	private static @Nullable DiscipleEntity find(UUID owner, UUID soul) {
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.soulId().equals(soul) && disciple.role() == DiscipleEntity.Role.DISCIPLE) {
				return disciple;
			}
		}
		return null;
	}

	// ================================================================== session values

	/** {@code uji sekte kecepatan}: the session rate multiplier (1-50). */
	public static void setSpeed(UUID owner, double speed) {
		SPEED.put(owner, Math.max(1.0D, Math.min(50.0D, speed)));
		SENT_FACTOR.remove(owner);
	}

	public static double speed(UUID owner) {
		return SPEED.getOrDefault(owner, 1.0D);
	}

	/** The FPS hint (DESIGN 21.15): the client reports its frame rate stayed below 30 for 5 s. */
	public static void setFpsLow(UUID owner, boolean low) {
		if (low) {
			FPS_LOW.put(owner, true);
		} else {
			FPS_LOW.remove(owner);
		}
		SENT_FACTOR.remove(owner);
	}

	public static int activeCount(UUID owner) {
		return ACTIVE.getOrDefault(owner, 0);
	}

	/** The flow's factor on the rate: builders, the session speed and the FPS hint. */
	public static double factor(UUID owner) {
		double fps = FPS_LOW.getOrDefault(owner, false) && dev.kultivator.KultivatorConfig.get().sectBuildFpsHint ? FPS_FACTOR : 1.0D;
		return (1.0D + RATE_EACH * activeCount(owner)) * speed(owner) * fps;
	}

	// ================================================================== ticking

	/** Once a second per owner with a hall: work spots, plaza meditation, rewards and the rate factor. */
	static void tick20(MinecraftServer server, UUID owner) {
		EngineApi engine = EngineApi.get();
		List<UUID> souls = souls(owner);
		SiteView site = engine.template(owner) > 0 ? engine.site(owner) : null;
		if (site == null) {
			if (!souls.isEmpty()) {
				releaseAll(owner);
			}
			ACTIVE.remove(owner);
			pushFactor(owner);
			return;
		}
		ServerLevel level = server.getLevel(site.dim());
		boolean running = site.status() == SiteView.STATUS_RUNNING;
		BlockPos spot = running ? engine.workSpot(owner) : null;
		int active = 0;
		int tick = server.getTickCount();
		for (int index = 0; index < souls.size(); index++) {
			UUID soul = souls.get(index);
			DiscipleEntity body = find(owner, soul);
			if (body == null || level == null || body.level() != level) {
				WORKERS.remove(soul);
				continue;
			}
			Worker worker = WORKERS.get(soul);
			if (worker == null || worker.entity != body) {
				worker = new Worker(body, owner);
				worker.nextSwing = tick + 15;
				WORKERS.put(soul, worker);
			}
			if (body.data().breakingThrough() || DuelRules.inDuel(body) || body.isReleasing()) {
				continue;
			}
			int activity = body.activity();
			if (activity != DiscipleEntity.ACTIVITY_NONE && activity != worker.activitySet) {
				// Another module took this disciple for something else (a duty, the tower, a tournament): it waits.
				worker.working = false;
				continue;
			}
			if (spot != null) {
				BlockPos target = spread(spot, index);
				body.setActivity(DiscipleEntity.ACTIVITY_BUILD, target);
				// it faces the layer being built; DiscipleEntity plays the mending hammer there and the plank carry on the way
				body.setActivityFocus(spot);
				worker.activitySet = DiscipleEntity.ACTIVITY_BUILD;
				double distance = Math.sqrt(body.distanceToSqr(Vec3.atBottomCenterOf(target)));
				if (distance <= WORK_RANGE) {
					active++;
					worker.working = true;
					reward(owner, body, soul);
				} else {
					worker.working = false;
				}
				unstick(worker, level, target, distance, tick);
			} else {
				worker.working = false;
				BlockPos plaza = plaza(site.stele(), index);
				if (activity == DiscipleEntity.ACTIVITY_SPECTATOR && !plaza.equals(body.activitySpot())) {
					// Moved to another spectator spot by someone else (a tournament): it is theirs for now.
					continue;
				}
				if (activity != DiscipleEntity.ACTIVITY_SPECTATOR) {
					body.setActivity(DiscipleEntity.ACTIVITY_SPECTATOR, plaza);
				}
				body.setActivityFocus(site.stele());
				worker.activitySet = DiscipleEntity.ACTIVITY_SPECTATOR;
				worker.bestDistance = Double.MAX_VALUE;
			}
		}
		ACTIVE.put(owner, active);
		if (active >= MAX) {
			ServerPlayer master = Ceremonies.online(owner);
			if (master != null && !site.test()) {
				Advancements.grant(master, "four_builders");
			}
		}
		pushFactor(owner);
	}

	/** Every tick: the swing pose and the material dust of working builders. */
	static void tick(MinecraftServer server) {
		if (WORKERS.isEmpty()) {
			return;
		}
		int tick = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Worker>> it = WORKERS.entrySet().iterator(); it.hasNext(); ) {
			Worker worker = it.next().getValue();
			DiscipleEntity body = worker.entity;
			if (body.isRemoved()) {
				it.remove();
				continue;
			}
			if (!worker.working || tick < worker.nextSwing || !(body.level() instanceof ServerLevel level)) {
				continue;
			}
			worker.nextSwing = tick + 15 + body.getRandom().nextInt(11);
			// the hammer blow is the body clip's (PERBAIKI_PALU, DiscipleEntity.animateDuty); the dust and sound follow it
			if (body.getTarget() != null || SosokApi.gerakSekarang(body) != GerakId.PERBAIKI_PALU) {
				continue;
			}
			BlockPos spot = body.activitySpot();
			Vec3 hand = body.position().add(body.getLookAngle().scale(0.6D)).add(0.0D, 0.9D, 0.0D);
			Vec3 layer = spot == null ? hand : Vec3.atCenterOf(spot);
			Fx.send(level, hand, layer, FxType.SECT_BUILD, Ceremonies.FX_WORK, THREAD_COLOR, 1.0F);
			if (body.getRandom().nextInt(3) == 0) {
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 0.25F, 0.9F
						+ body.getRandom().nextFloat() * 0.3F);
			}
		}
	}

	/** Builders stand around the spot, not on one cell. */
	private static BlockPos spread(BlockPos spot, int index) {
		int[][] offsets = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}};
		int[] offset = offsets[index % offsets.length];
		return spot.offset(offset[0], 0, offset[1]);
	}

	/** A cell on the plaza ring (3 blocks from the stele) for an idle builder. */
	private static BlockPos plaza(BlockPos stele, int index) {
		double angle = Math.PI / 4.0D + index * Math.PI / 2.0D;
		return BlockPos.containing(stele.getX() + 0.5D + Math.cos(angle) * 3.0D, stele.getY(), stele.getZ() + 0.5D + Math.sin(angle) * 3.0D);
	}

	/** Path failing for 10 s: a cloud step to a safe spot by the work spot (read-only spot search). */
	private static void unstick(Worker worker, ServerLevel level, BlockPos target, double distance, int tick) {
		if (distance <= 3.0D) {
			worker.bestDistance = distance;
			worker.stuckSince = tick;
			return;
		}
		if (distance < worker.bestDistance - 1.0D) {
			worker.bestDistance = distance;
			worker.stuckSince = tick;
			return;
		}
		if (tick - worker.stuckSince < STUCK_TICKS || !level.isLoaded(target)) {
			return;
		}
		DiscipleEntity body = worker.entity;
		Vec3 safe = Targeting.safeSpot(level, body, Vec3.atBottomCenterOf(target));
		if (safe == null) {
			worker.stuckSince = tick;
			return;
		}
		Vec3 old = body.position();
		Fx.burst(level, old.add(0.0D, 1.0D, 0.0D), 0xBFE8FF, 16, 0.8F);
		body.getNavigation().stop();
		body.snapTo(safe.x, safe.y, safe.z, body.getYRot(), body.getXRot());
		Fx.burst(level, safe.add(0.0D, 1.0D, 0.0D), 0xBFE8FF, 16, 0.8F);
		level.playSound(null, safe.x, safe.y, safe.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.5F, 1.3F);
		worker.bestDistance = Math.sqrt(body.distanceToSqr(Vec3.atBottomCenterOf(target)));
		worker.stuckSince = tick;
	}

	/** One working second: active ticks, a PK every 2,400 (30 a day), 0.5 XP a minute. */
	private static void reward(UUID owner, DiscipleEntity body, UUID soul) {
		CompoundTag entry = entry(owner, soul);
		if (entry == null) {
			return;
		}
		long ticks = entry.getLongOr("ticks", 0L) + 20L;
		entry.putLong("ticks", ticks);
		long day = FlowData.day();
		if (entry.getLongOr("pk_day", Long.MIN_VALUE) != day) {
			entry.putLong("pk_day", day);
			entry.putInt("pk_today", 0);
		}
		if (ticks % PK_TICKS == 0L && entry.getIntOr("pk_today", 0) < PK_DAILY) {
			SectRanks.addPk(body.data(), 1, "kultivator.sectbuild.pk.build");
			entry.putInt("pk_today", entry.getIntOr("pk_today", 0) + 1);
		}
		if (ticks % 1200L == 0L) {
			DiscipleCultivation.addXp(body, XP_PER_MINUTE, XpSource.SECT);
		}
		FlowData.dirty();
	}

	/** Sends the flow's rate factor to the engine when it changed. */
	private static void pushFactor(UUID owner) {
		double factor = factor(owner);
		Double sent = SENT_FACTOR.get(owner);
		if (sent == null || Math.abs(sent - factor) > 1.0E-6D) {
			SENT_FACTOR.put(owner, factor);
			EngineApi.get().setRateMultiplier(owner, factor);
		}
	}

	/** Entity ids of the owner's working builders in {@code level} (the client draws their qi threads). */
	static int[] workingIds(UUID owner, ServerLevel level) {
		List<Integer> ids = new ArrayList<>();
		for (Worker worker : WORKERS.values()) {
			if (worker.owner.equals(owner) && worker.working && worker.entity.level() == level && !worker.entity.isRemoved()) {
				ids.add(worker.entity.getId());
			}
		}
		return ids.stream().mapToInt(Integer::intValue).toArray();
	}

	// ================================================================== for the Pekerja panel

	/** The builders and the disciples that could become one (the owner's loaded disciples). */
	static CompoundTag info(ServerPlayer viewer, UUID owner) {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		List<UUID> souls = souls(owner);
		for (Tag each : FlowData.of(owner).getListOrEmpty("builders")) {
			if (!(each instanceof CompoundTag entry)) {
				continue;
			}
			CompoundTag out = new CompoundTag();
			out.putString("soul", entry.getStringOr("soul", ""));
			entry.read("name", ComponentSerialization.CODEC).ifPresent(name -> out.store("name", ComponentSerialization.CODEC, name));
			Worker worker = null;
			try {
				worker = WORKERS.get(UUID.fromString(entry.getStringOr("soul", "")));
			} catch (IllegalArgumentException ignored) {
				// broken entry
			}
			out.putBoolean("loaded", worker != null && !worker.entity.isRemoved());
			out.putBoolean("working", worker != null && worker.working);
			out.putInt("eid", worker != null ? worker.entity.getId() : -1);
			out.putLong("ticks", entry.getLongOr("ticks", 0L));
			list.add(out);
		}
		tag.put("list", list);
		ListTag candidates = new ListTag();
		if (viewer.getUUID().equals(owner)) {
			for (DiscipleEntity disciple : Disciples.loaded(owner)) {
				if (disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.isReleasing() || souls.contains(disciple.soulId())) {
					continue;
				}
				CompoundTag out = new CompoundTag();
				out.putInt("eid", disciple.level() == viewer.level() ? disciple.getId() : -1);
				out.putString("soul", disciple.soulId().toString());
				out.store("name", ComponentSerialization.CODEC, disciple.getDisplayName());
				DiscipleData data = disciple.data();
				out.putInt("rank", SectRanks.rank(data));
				out.putInt("realm", data.realm().ordinal());
				out.putInt("stage", data.stage());
				out.putBoolean("free", blocker(disciple) == null);
				out.putBoolean("gather_ok", GatherExpeditions.blocker(disciple, dev.kultivator.sect.build.api.Bahan.BATU) == null);
				out.putDouble("power", GatherExpeditions.memberPower(data));
				String duty = Duties.dutyOf(data);
				out.putString("duty", duty == null ? "" : duty);
				candidates.add(out);
			}
		}
		tag.put("cands", candidates);
		tag.putInt("active", activeCount(owner));
		tag.putDouble("speed", speed(owner));
		tag.putDouble("factor", factor(owner));
		return tag;
	}

	static void clearSession() {
		WORKERS.clear();
		SPEED.clear();
		FPS_LOW.clear();
		SENT_FACTOR.clear();
		ACTIVE.clear();
	}

	/** The component builders work on, for messages (null when none is open). */
	static @Nullable ComponentView open(UUID owner) {
		for (ComponentView component : EngineApi.get().components(owner)) {
			if (component.state() == ComponentView.STATE_OPEN) {
				return component;
			}
		}
		return null;
	}

	private Builders() {
	}
}
