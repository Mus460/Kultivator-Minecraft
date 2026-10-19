package dev.kultivator.alam;

import dev.kultivator.data.Element;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sect.event.RivalSkills;
import dev.kultivator.technique.spec.run.Boons;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The chamber catalogue of DESIGN 25.3.6 as entity mechanics on the stage: mobs, relic entities, telegraph rings and the
 * invisible {@link WallGrid}. No block is read or written. Sumber per chamber in {@code kultivator.alam.chamber.<id>.source}
 * (Ujian Tempur RMJ ch. 160 per the fan wiki; Jalan Lava and Padang Semut Api Besi inspired by the RMJ Heavenvoid Hall arc;
 * Labirin Ilusi and Gerbang Batu Harta RMJ ch. 1126; Aula Kristal Es RI ch. 198-199; Lapisan Badan Energi BTTH ch. 1352, 1600).
 * Every number is game design.
 */
public final class Chambers {

	public static final String BATTLE = "battle";
	public static final String LAVA_ROAD = "lava_road";
	public static final String ANT_FIELD = "ant_field";
	public static final String ILLUSION_MAZE = "illusion_maze";
	public static final String STONE_GATES = "stone_gates";
	public static final String ICE_CRYSTALS = "ice_crystals";
	public static final String ENERGY_BODIES = "energy_bodies";
	public static final String GUARDIAN = "guardian";

	/** At most this many live mobs per run (DESIGN 25.13). */
	public static final int MAX_LIVE = 12;
	public static final int WAVES = 3;
	public static final int WAVE_DELAY = 60;
	public static final int LAVA_TICKS = 60 * 20;
	public static final int JET_EVERY = 40;
	public static final int JET_DELAY = 20;
	public static final double JET_RADIUS = 1.6D;
	public static final double JET_HP = 0.06D;
	public static final int JET_FIRE_TICKS = 60;
	public static final int ANT_KILLS = 12;
	public static final int ANT_ALIVE = 4;
	public static final int MAZE_TICKS = 3 * 60 * 20;
	public static final int MAZE_PHANTOM_EXITS = 4;
	public static final double PHANTOM_EXIT_HP = 0.10D;
	public static final int CRYSTAL_TICKS = 200;
	public static final int CRYSTAL_TICKS_LATE = 600;
	public static final int CRYSTAL_LATE_AFTER = 70;
	public static final double CRYSTAL_INTERRUPT = 0.15D;
	public static final int CRYSTAL_WAVE_EVERY = 240;
	public static final int SIGHT_MARK_TICKS = 120;
	public static final int GUARDIAN_SKILL_EVERY = 100;
	public static final double GUARDIAN_SLAM_RADIUS = 4.0D;
	public static final int GUARDIAN_SLAM_DELAY = 30;

	/** One chamber of a run. */
	public abstract static class Chamber {

		final String id;
		boolean cleared;
		int[] entry = {0, -10};
		int[] pad = {0, 0};
		@Nullable WallGrid grid;
		/** Floor tiles {@code x0, z0, x1, z1} drawn as magma (client). */
		int[] tiles = new int[0];
		final Set<UUID> mobs = new HashSet<>();

		Chamber(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		abstract void start(RealmRun run, long now);

		abstract void tick(RealmRun run, long now);

		/** The objective shown in the boss bar. */
		Component objective(RealmRun run) {
			return Component.translatable("kultivator.alam.chamber." + id);
		}

		/** The bar's progress (0-1), or a negative number to show the timer. */
		float progress(RealmRun run) {
			return -1.0F;
		}

		void onRelic(RealmRun run, ServerPlayer player, RealmRelicEntity relic) {
		}

		void onHurt(RealmRun run, ServerPlayer player, float amount) {
		}

		/** How many of this chamber's mobs are alive (missing ones count as gone). */
		int alive(RealmRun run) {
			int n = 0;
			for (UUID id : mobs) {
				if (run.alive(id)) {
					n++;
				}
			}
			return n;
		}

		void track(@Nullable Mob mob) {
			if (mob != null) {
				mobs.add(mob.getUUID());
			}
		}
	}

	/** A new chamber of this id; {@code layer} is the Makam layer (realm 4). */
	static Chamber create(String id, RealmRun run) {
		return switch (id) {
			case LAVA_ROAD -> new LavaRoad();
			case ANT_FIELD -> new AntField();
			case ILLUSION_MAZE -> new IllusionMaze(run.seed());
			case STONE_GATES -> new StoneGates();
			case ICE_CRYSTALS -> new IceCrystal();
			case ENERGY_BODIES -> new EnergyBodies(run.layer());
			case GUARDIAN -> new Guardian();
			default -> new Battle();
		};
	}

	/** The chamber order of a realm (DESIGN 25.3.6); realm 4 gets one entry per layer of the descent. */
	static List<String> plan(RealmSpec spec, RandomSource random, int startLayer, int layers) {
		List<String> plan = new ArrayList<>();
		switch (spec.ordinal()) {
			case 1 -> plan.addAll(List.of(BATTLE, LAVA_ROAD, BATTLE, GUARDIAN));
			case 2 -> plan.addAll(List.of(random.nextBoolean() ? LAVA_ROAD : ANT_FIELD, ILLUSION_MAZE, STONE_GATES, GUARDIAN));
			case 3 -> plan.addAll(List.of(ICE_CRYSTALS, ICE_CRYSTALS, ICE_CRYSTALS, GUARDIAN));
			default -> {
				for (int i = 0; i < layers; i++) {
					plan.add((startLayer + i) % 9 == 0 ? GUARDIAN : ENERGY_BODIES);
				}
			}
		}
		return plan;
	}

	// ================================================================== shared helpers

	/** A local cell on a ring of {@code radius} around the origin, not inside a wall. */
	static int[] ringCell(RealmRun run, Chamber chamber, double radius) {
		RandomSource random = run.random();
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = radius + random.nextDouble() * 3.0D;
			int lx = (int) Math.round(Math.cos(angle) * r);
			int lz = (int) Math.round(Math.sin(angle) * r);
			if (chamber.grid == null || !chamber.grid.wall(lx, lz)) {
				return new int[]{lx, lz};
			}
		}
		return new int[]{0, 0};
	}

	/** A chamber guard of {@code tier} for the run's realm: husks, spiders and wither skeletons, sometimes a rogue. */
	static @Nullable Mob guard(RealmRun run, Chamber chamber, int tier, int lx, int lz, boolean mayRogue) {
		RandomSource random = run.random();
		if (mayRogue && run.r() >= 2 && random.nextInt(4) == 0) {
			Mob rogue = run.spawnRogue(tier, RogueCultivators.VARIANT_ROGUE, lx, lz);
			if (rogue != null) {
				return rogue;
			}
		}
		EntityType<? extends Mob> type = switch (random.nextInt(run.r() >= 3 ? 3 : 2)) {
			case 0 -> EntityTypes.HUSK;
			case 1 -> EntityTypes.SPIDER;
			default -> EntityTypes.WITHER_SKELETON;
		};
		return run.spawnGuard(type, tier, themeElement(run), lx, lz);
	}

	/** The realm's guard element: fire, fire or ice, ice, space; null (natural) now and then. */
	static @Nullable Element themeElement(RealmRun run) {
		RandomSource random = run.random();
		if (random.nextInt(4) == 0) {
			return null;
		}
		return switch (run.spec().ordinal()) {
			case 1 -> Element.FIRE;
			case 2 -> random.nextBoolean() ? Element.FIRE : Element.ICE;
			case 3 -> Element.ICE;
			default -> Element.SPACE;
		};
	}

	/** A wave of {@code 2 + R/2} guards on the ring (one tier 2 when {@code withElite}). */
	static void wave(RealmRun run, Chamber chamber, boolean withElite) {
		int count = Math.min(2 + run.r() / 2, MAX_LIVE - run.liveMobs());
		for (int i = 0; i < count; i++) {
			int[] cell = ringCell(run, chamber, 9.0D);
			int tier = withElite && i == 0 ? 2 : 1;
			chamber.track(guard(run, chamber, tier, cell[0], cell[1], true));
		}
	}

	private static boolean near(LivingEntity entity, Vec3 at, double radius) {
		double dx = entity.getX() - at.x;
		double dz = entity.getZ() - at.z;
		return dx * dx + dz * dz <= radius * radius && Math.abs(entity.getY() - at.y) < 2.5D;
	}

	/** True when the player sees through illusions now: Pil Mata Terang or Divine Sense. */
	static boolean seesTruth(ServerPlayer player) {
		return Alam.hasClearSight(player) || Boons.divineSense(player);
	}

	// ================================================================== Ujian Tempur

	/** Survive 3 waves of {@code 2 + R/2}; the last has one tier-2 elite; each starts 3 s after the previous dies. */
	static final class Battle extends Chamber {
		int wave;
		long nextAt;

		Battle() {
			super(BATTLE);
			entry = new int[]{0, 0};
		}

		@Override
		void start(RealmRun run, long now) {
			nextAt = now + WAVE_DELAY;
		}

		@Override
		void tick(RealmRun run, long now) {
			if (alive(run) > 0) {
				return;
			}
			if (wave >= WAVES) {
				cleared = true;
				return;
			}
			if (nextAt == 0L) {
				nextAt = now + WAVE_DELAY;
			} else if (now >= nextAt) {
				mobs.clear();
				wave++;
				wave(run, this, wave == WAVES);
				nextAt = 0L;
			}
		}

		@Override
		Component objective(RealmRun run) {
			return Component.translatable("kultivator.alam.chamber.battle.wave", Component.translatable("kultivator.alam.chamber.battle"),
					Math.max(1, wave), WAVES);
		}
	}

	// ================================================================== Jalan Lava

	/** Reach the end of the 40-block lane within 60 s; a telegraphed jet every 2 s per segment. */
	static final class LavaRoad extends Chamber {
		private record Jet(Vec3 at, long strikeAt) {
		}

		final List<Jet> jets = new ArrayList<>();
		long attemptEnd;
		long nextJets;

		LavaRoad() {
			super(LAVA_ROAD);
			grid = WallGrid.lavaLane();
			entry = new int[]{-17, 0};
			pad = new int[]{17, 0};
			tiles = new int[]{-18, -2, -13, 2, -12, -2, -7, 2, -6, -2, -1, 2, 0, -2, 5, 2, 6, -2, 11, 2, 12, -2, 18, 2};
		}

		@Override
		void start(RealmRun run, long now) {
			attemptEnd = now + LAVA_TICKS;
			nextJets = now + JET_EVERY;
		}

		@Override
		void tick(RealmRun run, long now) {
			for (ServerPlayer player : run.players()) {
				if (run.localX(player) >= 16) {
					cleared = true;
					jets.clear();
					return;
				}
			}
			if (now >= attemptEnd) {
				run.message(Component.translatable("kultivator.alam.lava.retry").withStyle(ChatFormatting.GOLD));
				run.partyToEntry();
				attemptEnd = now + LAVA_TICKS;
				jets.clear();
			}
			if (now >= nextJets) {
				nextJets = now + JET_EVERY;
				RandomSource random = run.random();
				for (int segment = 0; segment < 6; segment++) {
					int lx = -18 + segment * 6 + random.nextInt(6);
					int lz = -2 + random.nextInt(5);
					Vec3 at = run.world(lx, lz);
					jets.add(new Jet(at, now + JET_DELAY));
					Fx.send(run.level(), at, new Vec3(0xFF5A1E, 0.0D, 0.0D), FxType.ALAM, AlamFxIds.TELEGRAPH, JET_DELAY, (float) JET_RADIUS);
				}
			}
			for (int i = jets.size() - 1; i >= 0; i--) {
				Jet jet = jets.get(i);
				if (now < jet.strikeAt()) {
					continue;
				}
				jets.remove(i);
				Fx.send(run.level(), jet.at(), FxType.ALAM, AlamFxIds.JET, 0, (float) JET_RADIUS);
				for (ServerPlayer player : run.players()) {
					if (near(player, jet.at(), JET_RADIUS)) {
						double factor = Alam.lavaFactor(player);
						player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), (int) Math.round(JET_FIRE_TICKS * factor)));
						run.hurtHazard(player, (float) (player.getMaxHealth() * JET_HP * factor));
					}
				}
			}
		}

		@Override
		Component objective(RealmRun run) {
			return Component.translatable("kultivator.alam.chamber.lava_road");
		}
	}

	// ================================================================== Padang Lava Semut Api Besi

	/** Kill 12 Ironfire Ants (cave spiders made tier-1 Fire elites); the magma field slows (Slowness I, no damage). */
	static final class AntField extends Chamber {
		int spawned;

		AntField() {
			super(ANT_FIELD);
			entry = new int[]{-16, 0};
			pad = new int[]{0, 0};
			tiles = new int[]{-8, -8, -1, -1, 0, -8, 7, -1, -8, 0, -1, 7, 0, 0, 7, 7};
		}

		@Override
		void start(RealmRun run, long now) {
		}

		@Override
		void tick(RealmRun run, long now) {
			int live = alive(run);
			if (spawned >= ANT_KILLS && live == 0) {
				cleared = true;
				return;
			}
			if (now % 20L == 0L) {
				for (ServerPlayer player : run.players()) {
					int lx = run.localX(player);
					int lz = run.localZ(player);
					if (lx >= -8 && lx <= 7 && lz >= -8 && lz <= 7) {
						player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, false, true));
					}
				}
			}
			if (live < ANT_ALIVE && spawned < ANT_KILLS && run.liveMobs() < MAX_LIVE && now % 10L == 0L) {
				int[] cell = ringCell(run, this, 9.0D);
				Mob ant = run.spawnGuard(EntityTypes.CAVE_SPIDER, 1, Element.FIRE, cell[0], cell[1]);
				if (ant != null) {
					track(ant);
				}
				spawned++;
			}
		}

		@Override
		Component objective(RealmRun run) {
			int killed = Math.max(0, spawned - alive(run));
			return Component.translatable("kultivator.alam.chamber.ant_field.count", Component.translatable("kultivator.alam.chamber.ant_field"),
					killed, ANT_KILLS);
		}
	}

	// ================================================================== Labirin Ilusi

	/** Find the true exit of a 25 × 25 maze within 3 min; 4 phantom exits; illusory phantoms wander; sight reveals. */
	static final class IllusionMaze extends Chamber {
		final WallGrid.Maze maze;
		@Nullable UUID trueExit;
		final Set<UUID> phantomExits = new HashSet<>();
		final Set<UUID> phantoms = new HashSet<>();
		final Map<UUID, Long> touchCooldown = new HashMap<>();
		long attemptEnd;

		IllusionMaze(long seed) {
			super(ILLUSION_MAZE);
			maze = WallGrid.maze(seed, MAZE_PHANTOM_EXITS);
			grid = maze.grid();
			entry = maze.entry();
			pad = maze.exit();
		}

		@Override
		void start(RealmRun run, long now) {
			attemptEnd = now + MAZE_TICKS;
			RealmRelicEntity exit = run.spawnRelic(RealmRelicEntity.KIND_EXIT, maze.exit()[0], maze.exit()[1]);
			trueExit = exit == null ? null : exit.getUUID();
			for (int[] cell : maze.phantoms()) {
				RealmRelicEntity fake = run.spawnRelic(RealmRelicEntity.KIND_EXIT, cell[0], cell[1]);
				if (fake != null) {
					phantomExits.add(fake.getUUID());
				}
			}
			int count = 2 + (run.r() >= 4 ? 1 : 0);
			RandomSource random = run.random();
			for (int i = 0; i < count; i++) {
				int[] room = WallGrid.roomCentre(1 + random.nextInt(WallGrid.MAZE_ROOMS - 1), random.nextInt(WallGrid.MAZE_ROOMS));
				Mob phantom = run.spawnRogue(1, RogueCultivators.VARIANT_GHOST, room[0], room[1]);
				if (phantom != null) {
					run.halveHealth(phantom);
					phantoms.add(phantom.getUUID());
					track(phantom);
				}
			}
		}

		@Override
		void tick(RealmRun run, long now) {
			if (now >= attemptEnd) {
				run.message(Component.translatable("kultivator.alam.maze.retry").withStyle(ChatFormatting.GOLD));
				run.partyToEntry();
				attemptEnd = now + MAZE_TICKS;
			}
			Entity exit = trueExit == null ? null : run.entity(trueExit);
			for (ServerPlayer player : run.players()) {
				if (exit != null && near(player, exit.position(), 1.1D)) {
					cleared = true;
					return;
				}
				Long until = touchCooldown.get(player.getUUID());
				if (until != null && now < until) {
					continue;
				}
				for (UUID id : phantomExits) {
					Entity fake = run.entity(id);
					if (fake != null && near(player, fake.position(), 1.1D)) {
						touchCooldown.put(player.getUUID(), now + 20L);
						Fx.send(run.level(), fake.position(), FxType.ALAM, AlamFxIds.PHANTOM_EXIT, 0, 1.0F);
						run.level().playSound(null, fake.getX(), fake.getY(), fake.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 0.8F, 0.6F);
						run.hurtHazard(player, (float) (player.getMaxHealth() * PHANTOM_EXIT_HP));
						Msg.actionBar(player, Component.translatable("kultivator.alam.maze.phantom").withStyle(ChatFormatting.LIGHT_PURPLE));
						run.toEntry(player);
						break;
					}
				}
			}
			if (now % 100L == 0L && exit != null) {
				for (ServerPlayer player : run.players()) {
					if (!seesTruth(player)) {
						continue;
					}
					Fx.sendTo(player, exit.position(), exit.position().add(0.0D, 6.0D, 0.0D), FxType.ALAM, AlamFxIds.TRUE_EXIT, 0, 6.0F);
					for (UUID id : phantoms) {
						if (run.entity(id) instanceof LivingEntity phantom && phantom.isAlive()) {
							phantom.addEffect(new MobEffectInstance(MobEffects.GLOWING, SIGHT_MARK_TICKS, 0, false, false));
						}
					}
				}
			}
		}

		@Override
		Component objective(RealmRun run) {
			return Component.translatable("kultivator.alam.chamber.illusion_maze");
		}
	}

	// ================================================================== Gerbang Batu Harta

	/** Open 1 of 3 stone gates: nothing ("tidak ada harta"), a trap wave, or the bonus treasure. Divine Sense shows qi on two. */
	static final class StoneGates extends Chamber {
		static final int EMPTY = 0;
		static final int TRAP = 1;
		static final int TREASURE = 2;

		final Map<UUID, Integer> contents = new HashMap<>();
		final Set<UUID> sensed = new HashSet<>();
		boolean opened;
		boolean trapping;

		StoneGates() {
			super(STONE_GATES);
			entry = new int[]{0, -8};
			pad = new int[]{0, 0};
		}

		@Override
		void start(RealmRun run, long now) {
			List<Integer> roll = new ArrayList<>(List.of(EMPTY, TRAP, TREASURE));
			RandomSource random = run.random();
			for (int i = roll.size() - 1; i > 0; i--) {
				int j = random.nextInt(i + 1);
				Integer swap = roll.get(i);
				roll.set(i, roll.get(j));
				roll.set(j, swap);
			}
			int[] xs = {-6, 0, 6};
			for (int i = 0; i < 3; i++) {
				RealmRelicEntity gate = run.spawnRelic(RealmRelicEntity.KIND_GATE, xs[i], 6);
				if (gate != null) {
					contents.put(gate.getUUID(), roll.get(i));
				}
			}
		}

		@Override
		void tick(RealmRun run, long now) {
			if (trapping) {
				if (alive(run) == 0) {
					cleared = true;
				}
				return;
			}
			if (opened || now % 40L != 0L) {
				return;
			}
			for (ServerPlayer player : run.players()) {
				if (!Boons.divineSense(player)) {
					continue;
				}
				for (Map.Entry<UUID, Integer> entry : contents.entrySet()) {
					Entity gate = run.entity(entry.getKey());
					if (gate != null && entry.getValue() != EMPTY && gate.distanceTo(player) < 24.0F) {
						Fx.sendTo(player, gate.position().add(0.0D, 2.6D, 0.0D), gate.position(), FxType.ALAM, AlamFxIds.QI_MARK, 0, 1.0F);
					}
				}
				if (sensed.add(player.getUUID())) {
					Msg.actionBar(player, Component.translatable("kultivator.alam.gates.sense").withStyle(ChatFormatting.AQUA));
				}
			}
		}

		@Override
		void onRelic(RealmRun run, ServerPlayer player, RealmRelicEntity relic) {
			Integer content = contents.get(relic.getUUID());
			if (opened || content == null) {
				return;
			}
			opened = true;
			relic.setState(1);
			Fx.send(run.level(), relic.position(), FxType.ALAM, AlamFxIds.GATE_OPEN, content, 2.0F);
			run.level().playSound(null, relic.getX(), relic.getY(), relic.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0F, 0.5F);
			run.level().playSound(null, relic.getX(), relic.getY(), relic.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
			switch (content) {
				case TRAP -> {
					run.message(Component.translatable("kultivator.alam.gates.trap").withStyle(ChatFormatting.RED));
					trapping = true;
					wave(run, this, true);
				}
				case TREASURE -> {
					run.message(Component.translatable("kultivator.alam.gates.treasure").withStyle(ChatFormatting.GOLD));
					run.setBonus();
					cleared = true;
				}
				default -> {
					run.message(Component.translatable("kultivator.alam.gates.empty").withStyle(ChatFormatting.GRAY));
					cleared = true;
				}
			}
		}

		@Override
		Component objective(RealmRun run) {
			return Component.translatable(trapping ? "kultivator.alam.gates.objective_trap" : "kultivator.alam.chamber.stone_gates");
		}
	}

	// ================================================================== Aula Kristal Es

	/** Absorb one crystal: a 10 s channel standing on it (30 s past 70 lifetime crystals); waves attack; big hits interrupt. */
	static final class IceCrystal extends Chamber {
		@Nullable UUID crystal;
		@Nullable UUID channeler;
		int progress;
		int need = CRYSTAL_TICKS;
		long pausedUntil;
		int sinceWave;

		IceCrystal() {
			super(ICE_CRYSTALS);
			entry = new int[]{0, -10};
			pad = new int[]{0, 5};
		}

		@Override
		void start(RealmRun run, long now) {
			RealmRelicEntity relic = run.spawnRelic(RealmRelicEntity.KIND_CRYSTAL, 0, 0);
			crystal = relic == null ? null : relic.getUUID();
		}

		@Override
		void tick(RealmRun run, long now) {
			Entity relic = crystal == null ? null : run.entity(crystal);
			if (!(relic instanceof RealmRelicEntity entity)) {
				cleared = true;
				return;
			}
			ServerPlayer standing = null;
			for (ServerPlayer player : run.players()) {
				if (near(player, entity.position(), 1.6D)) {
					if (player.getUUID().equals(channeler)) {
						standing = player;
						break;
					}
					if (standing == null) {
						standing = player;
					}
				}
			}
			if (standing == null) {
				return;
			}
			if (!standing.getUUID().equals(channeler)) {
				channeler = standing.getUUID();
				need = Alam.crystals(standing) > CRYSTAL_LATE_AFTER ? CRYSTAL_TICKS_LATE : CRYSTAL_TICKS;
				Msg.actionBar(standing, Component.translatable("kultivator.alam.crystal.channel", need / 20).withStyle(ChatFormatting.AQUA));
			}
			if (now < pausedUntil) {
				return;
			}
			progress++;
			sinceWave++;
			if (sinceWave >= CRYSTAL_WAVE_EVERY) {
				sinceWave = 0;
				wave(run, this, false);
			}
			if (progress % 10 == 0) {
				int percent = Math.min(99, progress * 100 / Math.max(1, need));
				entity.setState(Math.max(2, percent));
				Fx.send(run.level(), entity.position(), FxType.ALAM, AlamFxIds.CRYSTAL, percent, 1.0F);
			}
			if (progress >= need) {
				entity.setState(1);
				Fx.send(run.level(), entity.position(), FxType.ALAM, AlamFxIds.CRYSTAL, 100, 2.0F);
				run.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.6F);
				run.crystalAbsorbed(standing);
				cleared = true;
			}
		}

		@Override
		void onHurt(RealmRun run, ServerPlayer player, float amount) {
			if (player.getUUID().equals(channeler) && amount > player.getMaxHealth() * CRYSTAL_INTERRUPT && progress < need) {
				pausedUntil = run.now() + 40L;
				Msg.actionBar(player, Component.translatable("kultivator.alam.crystal.interrupted").withStyle(ChatFormatting.RED));
			}
		}

		@Override
		Component objective(RealmRun run) {
			return Component.translatable("kultivator.alam.chamber.ice_crystals.progress", Component.translatable("kultivator.alam.chamber.ice_crystals"),
					Math.min(100, progress * 100 / Math.max(1, need)));
		}
	}

	// ================================================================== Lapisan Badan Energi

	/** Destroy the layer's {@code 4 + layer/3} energy bodies (translucent ENERGY rogues). */
	static final class EnergyBodies extends Chamber {
		final int total;
		int spawned;

		EnergyBodies(int layer) {
			super(ENERGY_BODIES);
			total = 4 + Math.max(1, layer) / 3;
			entry = new int[]{0, -12};
			pad = new int[]{0, 0};
		}

		@Override
		void start(RealmRun run, long now) {
		}

		@Override
		void tick(RealmRun run, long now) {
			int live = alive(run);
			if (spawned >= total && live == 0) {
				cleared = true;
				return;
			}
			if (spawned < total && live < 6 && run.liveMobs() < MAX_LIVE && now % 10L == 0L) {
				int[] cell = ringCell(run, this, 8.0D);
				int tier = Math.min(3, 1 + run.layer() / 9);
				Mob body = run.spawnRogue(tier, RogueCultivators.VARIANT_ENERGY, cell[0], cell[1]);
				track(body);
				spawned++;
			}
		}

		@Override
		Component objective(RealmRun run) {
			return Component.translatable("kultivator.alam.chamber.energy_bodies.count", Component.translatable("kultivator.alam.chamber.energy_bodies"),
					Math.max(0, spawned - alive(run)), total);
		}
	}

	// ================================================================== the guardian

	/** The realm guardian ({@code kultivator.boss}): two telegraphed skills, a slam ring and a qi line. */
	static final class Guardian extends Chamber {
		@Nullable UUID guardian;
		long nextSkill;
		boolean slamNext = true;
		@Nullable Vec3 slamAt;
		long slamStrike;

		Guardian() {
			super(GUARDIAN);
			entry = new int[]{0, -10};
		}

		long startedAt;

		@Override
		void start(RealmRun run, long now) {
			startedAt = now;
			summon(run);
			nextSkill = now + GUARDIAN_SKILL_EVERY;
		}

		private void summon(RealmRun run) {
			Mob mob = run.spawnGuardian(8);
			if (mob != null) {
				guardian = mob.getUUID();
				track(mob);
			}
		}

		@Override
		void tick(RealmRun run, long now) {
			if (guardian == null) {
				// The guardian could not be placed yet (chunk still loading): try again, give up after 10 s.
				if (now - startedAt > 200L) {
					cleared = true;
				} else if (now % 20L == 0L) {
					summon(run);
				}
				return;
			}
			Entity entity = run.entity(guardian);
			if (!(entity instanceof Mob mob) || !mob.isAlive()) {
				cleared = true;
				return;
			}
			if (slamAt != null && now >= slamStrike) {
				Vec3 at = slamAt;
				slamAt = null;
				Fx.send(run.level(), at, FxType.ALAM, AlamFxIds.JET, 1, (float) GUARDIAN_SLAM_RADIUS);
				run.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.7F, 0.6F);
				float damage = (float) (mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 1.2D);
				for (LivingEntity target : run.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(GUARDIAN_SLAM_RADIUS),
						e -> e != mob && e.isAlive() && run.isPartyMember(e))) {
					if (near(target, at, GUARDIAN_SLAM_RADIUS)) {
						target.hurtServer(run.level(), run.level().damageSources().mobAttack(mob), damage);
					}
				}
			}
			if (now < nextSkill) {
				return;
			}
			nextSkill = now + GUARDIAN_SKILL_EVERY;
			LivingEntity target = mob.getTarget();
			if (target == null || !run.isPartyMember(target)) {
				target = run.nearestPlayer(mob.position());
			}
			if (target == null) {
				return;
			}
			if (slamNext) {
				slamAt = target.position();
				slamStrike = now + GUARDIAN_SLAM_DELAY;
				Fx.send(run.level(), slamAt, new Vec3(run.spec().color(), 0.0D, 0.0D), FxType.ALAM, AlamFxIds.TELEGRAPH, GUARDIAN_SLAM_DELAY,
						(float) GUARDIAN_SLAM_RADIUS);
			} else {
				double damage = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
				RivalSkills.qiLine(run.level(), mob, target, damage, run.spec().color(), run::isPartyMember);
			}
			slamNext = !slamNext;
		}

		@Override
		Component objective(RealmRun run) {
			Entity entity = guardian == null ? null : run.entity(guardian);
			if (entity instanceof LivingEntity living) {
				return Component.translatable("kultivator.alam.chamber.guardian.hp", Component.translatable("kultivator.alam.chamber.guardian"),
						(int) Math.ceil(living.getHealth()), (int) Math.ceil(living.getMaxHealth()));
			}
			return Component.translatable("kultivator.alam.chamber.guardian");
		}

		@Override
		float progress(RealmRun run) {
			Entity entity = guardian == null ? null : run.entity(guardian);
			if (entity instanceof LivingEntity living && living.getMaxHealth() > 0.0F) {
				return Math.max(0.0F, Math.min(1.0F, living.getHealth() / living.getMaxHealth()));
			}
			return 0.0F;
		}
	}

	private Chambers() {
	}
}
