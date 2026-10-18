package dev.kultivator.formasi.effect.b;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.effect.AlarmKind;
import dev.kultivator.formasi.effect.CoreContext;
import dev.kultivator.formasi.effect.FormationEffect;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * K1 Larangan Peringatan and the detection half of M2 (DESIGN 24.4.7). Two stages: APPROACH (intruders and strangers in
 * the ring from the radius to radius + 8, or within the approach band outside the territory) and BREACH (inside). Each
 * mob or stranger is reported once per 60 s per core; the text goes through {@code ctx.alert} (which throttles chat and
 * queues the Catatan log while the owner is offline); this handler adds the bell at the eye (never "rung", so no raid
 * glow), the HUD arrow for members, the red rune ring on the zone edge and the elite/boss title. Concealment never hides
 * intruders from the alarm; Dao Protectors are only labelled.
 */
public final class AlarmEffect implements FormationEffect {

	/** Approach ring outside the core radius (K1). */
	public static final double RING = 8.0D;
	/** Shadow or Wind keeper affinity: radius +8 (K1). */
	public static final double AFFINITY_RADIUS = 8.0D;
	/** M2 approach band outside the territory; 24 with a Shadow keeper. */
	public static final double M2_BAND = 16.0D;
	public static final double M2_BAND_SHADOW = 24.0D;
	public static final int ARROW_SECONDS = 8;
	public static final int MAX_OUTLINED = 8;
	private static final String[] SKIP_TAGS = {"kultivator.trial_guard", "kultivator.inheritance", "kultivator.dummy", "kultivator.dummy_boss"};

	private static final Map<BUtil.CoreKey, Map<UUID, long[]>> SEEN = new HashMap<>();
	private static final Map<BUtil.CoreKey, Long> LAST_TITLE = new HashMap<>();
	private static final Map<BUtil.CoreKey, long[]> LAST_KIND = new HashMap<>();
	private static final Map<BUtil.CoreKey, AlarmKind> TEST = new HashMap<>();

	@Override
	public void pulse(CoreContext ctx) {
		CoreView core = ctx.core();
		boolean territory = BUtil.keeperRank(core, SectRanks.DALAM) || BUtil.runningSpecs(ctx.owner()).contains(ConcealEffect.M2);
		detect(ctx, territory, territory ? M2_BAND : RING);
	}

	/** A sample alarm queued by {@code uji formasi alarm} for the next pulse of this core (the real delivery path). */
	public static void queueTest(CoreView core, AlarmKind kind) {
		TEST.put(BUtil.CoreKey.of(core), kind);
	}

	/**
	 * One detection pass of a core: {@code territory} uses the shared territory sweep (band = approach band outside the
	 * edge), otherwise the core radius plus the ring.
	 */
	static void detect(CoreContext ctx, boolean territory, double band) {
		ServerLevel level = ctx.level();
		CoreView core = ctx.core();
		BUtil.CoreKey key = BUtil.CoreKey.of(core);
		long now = ctx.now();
		AlarmKind test = TEST.remove(key);
		if (test != null) {
			sendTest(ctx, test);
		}
		double radius = ctx.radius();
		if (!territory && BUtil.keeperDao(core, Dao.SHADOW)) {
			radius += AFFINITY_RADIUS;
		}
		Vec3 centre = ctx.centre();
		Map<UUID, long[]> seen = SEEN.computeIfAbsent(key, k -> new HashMap<>());
		if (seen.size() > 256) {
			seen.values().removeIf(time -> now - Math.max(time[0], time[1]) > Stacking.ALARM_MOB_TICKS);
		}

		List<Mob> candidates;
		if (territory) {
			candidates = TerritorySweep.sweep(level, ctx.owner());
		} else {
			double reach = radius + band;
			candidates = level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(reach, Math.max(8.0D, reach / 2.0D), reach),
					mob -> intruder(level.getServer(), ctx.owner(), mob));
			if (candidates.size() > TerritorySweep.MAX_PER_QUERY) {
				candidates = candidates.subList(0, TerritorySweep.MAX_PER_QUERY);
			}
		}
		List<Mob> breach = new ArrayList<>();
		List<Mob> approach = new ArrayList<>();
		for (Mob mob : candidates) {
			double out = territory ? TerritorySweep.outsideBy(ctx.owner(), mob.position()) : horizontal(mob.position(), centre) - radius;
			if (Double.isNaN(out) || out > band) {
				continue;
			}
			boolean inside = out <= 0.0D;
			long[] last = seen.computeIfAbsent(mob.getUUID(), id -> new long[]{Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
			// Each stage once per 60 s; a mob seen in the ring is reported again when it breaks in.
			int stage = inside ? 1 : 0;
			if (now - last[stage] < Stacking.ALARM_MOB_TICKS || !inside && now - last[1] < Stacking.ALARM_MOB_TICKS) {
				continue;
			}
			last[stage] = now;
			(inside ? breach : approach).add(mob);
		}
		List<ServerPlayer> strangers = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || BUtil.member(level.getServer(), ctx.owner(), player)) {
				continue;
			}
			double out = territory ? TerritorySweep.outsideBy(ctx.owner(), player.position()) : horizontal(player.position(), centre) - radius;
			if (Double.isNaN(out) || out > band) {
				continue;
			}
			long[] last = seen.computeIfAbsent(player.getUUID(), id -> new long[]{Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
			if (now - last[1] < Stacking.ALARM_MOB_TICKS) {
				continue;
			}
			last[1] = now;
			strangers.add(player);
		}

		FormationSpec spec = ctx.spec();
		boolean names = keeperRealm(core, Realm.FOUNDATION);
		if (!breach.isEmpty()) {
			AlarmKind kind = AlarmKind.BREACH;
			for (Mob mob : breach) {
				if (BUtil.boss(mob)) {
					kind = AlarmKind.BOSS;
				} else if (kind != AlarmKind.BOSS && EliteMonsters.isElite(mob)) {
					kind = AlarmKind.ELITE;
				}
			}
			Vec3 at = centroid(breach);
			Component text = mobText(spec, breach, at, centre, names, territory, true);
			deliver(ctx, kind, text, at, breach.size());
			outline(ctx, breach);
			if (kind == AlarmKind.ELITE || kind == AlarmKind.BOSS) {
				title(ctx, kind, text);
			}
		}
		if (!approach.isEmpty()) {
			Vec3 at = centroid(approach);
			deliver(ctx, AlarmKind.APPROACH, mobText(spec, approach, at, centre, names, territory, false), at, approach.size());
		}
		for (ServerPlayer stranger : strangers) {
			Vec3 at = stranger.position();
			Component text = Component.translatable("kultivator.formasib.alarm.stranger", spec.name(), stranger.getDisplayName(),
					BUtil.direction(at.x - centre.x, at.z - centre.z), Math.round(horizontal(at, centre)));
			deliver(ctx, AlarmKind.STRANGER, text, at, 1);
		}
	}

	/** "⚠ Larangan Peringatan: 3 monster dari barat laut (Zombie ×2, ...) · 18 blok dari mata formasi". */
	private static Component mobText(FormationSpec spec, List<Mob> mobs, Vec3 at, Vec3 centre, boolean names, boolean territory, boolean breach) {
		Component dir = BUtil.direction(at.x - centre.x, at.z - centre.z);
		MutableComponent text;
		if (territory && !breach) {
			text = Component.translatable("kultivator.formasib.alarm.edge", spec.name(), dir, mobs.size());
		} else {
			text = Component.translatable(breach ? "kultivator.formasib.alarm.breach" : "kultivator.formasib.alarm.approach", spec.name(), mobs.size(),
					dir, Math.round(horizontal(at, centre)));
		}
		if (names) {
			text.append(Component.translatable("kultivator.formasib.alarm.names", BUtil.mobList(mobs)));
		}
		return text;
	}

	/** Text through the core, plus the bell, the rune ring and the members' HUD arrows (throttled per core and kind). */
	private static void deliver(CoreContext ctx, AlarmKind kind, Component text, Vec3 from, int count) {
		ctx.alert(kind, text, from);
		BUtil.CoreKey key = BUtil.CoreKey.of(ctx.core());
		long[] last = LAST_KIND.computeIfAbsent(key, k -> new long[AlarmKind.values().length]);
		long now = ctx.now();
		if (last[kind.ordinal()] != 0L && now - last[kind.ordinal()] < Stacking.ALARM_CORE_KIND_TICKS) {
			return;
		}
		last[kind.ordinal()] = now;
		ServerLevel level = ctx.level();
		Vec3 eye = ctx.centre();
		boolean loud = kind != AlarmKind.APPROACH;
		level.playSound(null, eye.x, eye.y + 1.0D, eye.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, loud ? 1.0F : 0.35F, loud ? 0.9F : 1.4F);
		Vec3 edge = edgePoint(eye, from, ctx.radius());
		Fx.send(level, edge, eye, FxType.FORMASI, FxB.ALARM_RING, kind.ordinal(), (float) ctx.radius());
		for (ServerPlayer member : BUtil.onlineMembers(level.getServer(), ctx.owner())) {
			if (member.level() == level) {
				Fx.sendTo(member, from, eye, FxType.FORMASI, FxB.ALARM, kind.ordinal() | Math.min(255, count) << 4, ARROW_SECONDS);
			}
			if (kind != AlarmKind.APPROACH && kind != AlarmKind.STRANGER) {
				Advancements.grant(member, "alarm_breach");
			}
		}
	}

	private static void title(CoreContext ctx, AlarmKind kind, Component text) {
		BUtil.CoreKey key = BUtil.CoreKey.of(ctx.core());
		Long last = LAST_TITLE.get(key);
		if (last != null && ctx.now() - last < Stacking.ALARM_TITLE_TICKS) {
			return;
		}
		LAST_TITLE.put(key, ctx.now());
		Component title = Component.translatable(kind == AlarmKind.BOSS ? "kultivator.formasib.alarm.title_boss" : "kultivator.formasib.alarm.title_elite")
				.withColor(0xFF5A4A);
		for (ServerPlayer member : BUtil.onlineMembers(ctx.level().getServer(), ctx.owner())) {
			if (member.level() == ctx.level()) {
				Msg.title(member, title, text, 5, 50, 15);
			}
		}
	}

	/** Inti Emas+ keeper: breach intruders are outlined for the members for 10 s (client FX, never Glowing). */
	private static void outline(CoreContext ctx, List<Mob> mobs) {
		if (!keeperRealm(ctx.core(), Realm.GOLDEN_CORE)) {
			return;
		}
		for (ServerPlayer member : BUtil.onlineMembers(ctx.level().getServer(), ctx.owner())) {
			if (member.level() != ctx.level()) {
				continue;
			}
			int shown = 0;
			for (Mob mob : mobs) {
				if (shown++ >= MAX_OUTLINED) {
					break;
				}
				Fx.sendTo(member, mob.position(), mob.position(), FxType.FORMASI, FxB.OUTLINE, mob.getId(), 10.0F);
			}
		}
	}

	private static void sendTest(CoreContext ctx, AlarmKind kind) {
		Vec3 centre = ctx.centre();
		double angle = ctx.random().nextDouble() * Math.PI * 2.0D;
		Vec3 from = centre.add(Math.cos(angle) * ctx.radius() * 0.8D, 0.0D, Math.sin(angle) * ctx.radius() * 0.8D);
		Component text = Component.translatable("kultivator.formasib.alarm.test", ctx.spec().name(),
				Component.translatable("kultivator.formasib.alarm.kind." + kind.name().toLowerCase(java.util.Locale.ROOT)),
				BUtil.direction(from.x - centre.x, from.z - centre.z));
		LAST_KIND.remove(BUtil.CoreKey.of(ctx.core()));
		deliver(ctx, kind, text, from, kind == AlarmKind.STRANGER ? 1 : 3);
		if (kind == AlarmKind.ELITE || kind == AlarmKind.BOSS) {
			LAST_TITLE.remove(BUtil.CoreKey.of(ctx.core()));
			title(ctx, kind, text);
		}
	}

	/** True when a keeper of {@code core} is at or above {@code realm}. */
	static boolean keeperRealm(CoreView core, Realm realm) {
		for (DiscipleEntity keeper : BUtil.keepers(core)) {
			if (keeper.data().realm().ordinal() >= realm.ordinal()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * An intruder for alarms (24.2.10 without the field test): alive with AI, an {@code Enemy} (not a calm neutral) or a mob
	 * targeting a member; never a disciple, villager, pet, named, leashed or riding mob, or a trial or dummy mob.
	 */
	public static boolean intruder(MinecraftServer server, UUID owner, Mob mob) {
		if (!mob.isAlive() || mob.isNoAi() || mob.isInvulnerable() || mob instanceof DiscipleEntity || mob instanceof Npc || mob.hasCustomName()
				|| mob.isLeashed() || mob.isPassenger()) {
			return false;
		}
		if (mob instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
			return false;
		}
		for (String tag : SKIP_TAGS) {
			if (mob.entityTags().contains(tag)) {
				return false;
			}
		}
		LivingEntity target = mob.getTarget();
		if (target != null && BUtil.member(server, owner, target)) {
			return true;
		}
		if (mob instanceof NeutralMob neutral && !neutral.isAngry()) {
			return false;
		}
		return mob instanceof Enemy;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static Vec3 centroid(List<Mob> mobs) {
		double x = 0.0D;
		double y = 0.0D;
		double z = 0.0D;
		for (Mob mob : mobs) {
			x += mob.getX();
			y += mob.getY();
			z += mob.getZ();
		}
		int n = Math.max(1, mobs.size());
		return new Vec3(x / n, y / n, z / n);
	}

	/** The point on the zone edge toward {@code from} (where the runes flare). */
	private static Vec3 edgePoint(Vec3 eye, Vec3 from, double radius) {
		double dx = from.x - eye.x;
		double dz = from.z - eye.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.01D) {
			return eye;
		}
		double r = Math.min(radius, Math.max(len, 1.0D));
		return new Vec3(eye.x + dx / len * r, eye.y, eye.z + dz / len * r);
	}

	/** Forgets the marks of a core (it stopped). */
	static void forget(BUtil.CoreKey key) {
		SEEN.remove(key);
		LAST_KIND.remove(key);
		LAST_TITLE.remove(key);
	}

	static void clearAll() {
		SEEN.clear();
		LAST_KIND.clear();
		LAST_TITLE.clear();
		TEST.clear();
	}

	@Override
	public void onState(CoreContext ctx, dev.kultivator.formasi.core.CoreState from, dev.kultivator.formasi.core.CoreState to) {
		if (!to.running()) {
			forget(BUtil.CoreKey.of(ctx.core()));
		}
	}

	/** Never: alarms always drain fully (24.2.7). */
	@Override
	public boolean idle(CoreContext ctx) {
		return false;
	}
}
