package dev.kultivator.dunia.reruntuhan.menara;

import dev.kultivator.Kultivator;
import dev.kultivator.alam.AlamItems;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.dunia.DuniaTickets;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.RCreature;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.RNet;
import dev.kultivator.dunia.reruntuhan.RRewards;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.dunia.reruntuhan.gua.BayanganEntity;
import dev.kultivator.dunia.reruntuhan.gua.FreeHost;
import dev.kultivator.dunia.reruntuhan.gua.Puzzle;
import dev.kultivator.dunia.reruntuhan.gua.ShadowBrain;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
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
 * One climb of the Menara Seratus Lantai (DESIGN 34.5.7): a party of up to four on a leased slot of the tower
 * dimension, one floor after another. Floor {@code f} recommends Rank step {@code s(f) = round(0.64 f)}; each band of ten
 * runs the same pattern: 1-3 combat waves (elites, a rogue band on 3), 4 a formation puzzle against the clock, 5
 * endurance (60 s of formation pulses), 6 combat with an affix, 7 Ujian Hati (three Dao riddles, or the mirror shadow on
 * odd bands), 8 meditation under pressure (45 s in the circle while qi waves push), 9 an elite of a beast king's
 * strength, 10 the band's Penjaga Lantai (floor 100: Bayangan Diri, the climber's own shadow at full strength).
 * <p>
 * A cleared floor pays its first clear (stones by {@code s(f)}, a milestone chest every tenth floor) and opens two pads:
 * up to the next floor, or back to the lobby. A failed floor sends the party to the lobby with Luka Menara (never an
 * item loss); a lethal blow leaves 1 HP and does the same for that player. Every entity of a floor is discarded when it
 * ends; nothing is built: the floor's look is client Displays.
 */
public final class TowerRun {

	public enum Type { COMBAT, PUZZLE, ENDURANCE, AFFIX, HEART, MEDITATE, ELITE, GUARDIAN }

	enum Phase { INTRO, PLAY, CLEAR, ENDED }

	public static final double ARENA = 18.0D;
	public static final int INTRO = 50;
	public static final int ENDURE = 60 * 20;
	public static final int MEDITATE_NEED = 45 * 20;
	public static final int MEDITATE_LIMIT = 90 * 20;
	public static final double CIRCLE = 3.0D;
	public static final int RIDDLES = 12;
	/** Tag of every foe a floor spawns (they are discarded with the floor and refused when loaded from disk). */
	public static final String FOE_TAG = "kultivator.menara_foe";
	private static final net.minecraft.resources.Identifier TOWER_HEALTH = Kultivator.id("menara_health");
	private static final net.minecraft.resources.Identifier TOWER_SPEED = Kultivator.id("menara_speed");

	final ServerLevel level;
	final int slot;
	final BlockPos origin;
	final List<UUID> party = new ArrayList<>();
	final boolean free;
	int floor;
	Phase phase = Phase.INTRO;
	long phaseAt;
	Type type = Type.COMBAT;
	private final ServerBossEvent bar;
	private final List<Entity> entities = new ArrayList<>();
	private final RandomSource random;
	// floor state
	private int wave;
	private int waves;
	private long nextWaveAt;
	private int affix;
	private @Nullable TekaPartEntity crystal;
	private int crystalHits;
	private @Nullable FreeHost puzzle;
	private boolean puzzleSolved;
	private @Nullable RCreature boss;
	private boolean bossDown;
	private long nextPulse;
	private final List<Vec3> pulseMarks = new ArrayList<>();
	private final Map<UUID, Integer> inside = new HashMap<>();
	private final Map<UUID, int[]> riddles = new HashMap<>();
	private final Set<UUID> heartDone = new HashSet<>();
	private @Nullable TekaPartEntity padUp;
	private @Nullable TekaPartEntity padOut;
	private boolean failing;

	TowerRun(ServerLevel level, int slot, List<ServerPlayer> members, int floor, boolean free) {
		this.level = level;
		this.slot = slot;
		this.origin = Menara.slotCentre(slot);
		this.free = free;
		this.floor = floor;
		this.random = RandomSource.create(level.getRandom().nextLong());
		for (ServerPlayer player : members) {
			if (party.size() < Menara.PARTY_MAX) {
				party.add(player.getUUID());
			}
		}
		this.bar = new ServerBossEvent(Mth.createInsecureUUID(random), Component.translatable("kultivator.reruntuhan.menara.title"),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
	}

	// ================================================================== geometry

	Vec3 centre() {
		return Vec3.atBottomCenterOf(origin);
	}

	Vec3 at(double dx, double dz) {
		return centre().add(dx, 0.0D, dz);
	}

	/** Band 0-9 of the current floor. */
	int band() {
		return Math.max(0, Math.min(9, (floor - 1) / 10));
	}

	/** Position 1-10 of the floor inside its band. */
	int step() {
		return (floor - 1) % 10 + 1;
	}

	/** The recommended Rank step of the floor (DESIGN 34.5.7). */
	static int rankStep(int floor) {
		return Math.max(0, Math.min(Rank.MAX_STEP, Math.round(0.64F * floor)));
	}

	/** The realm ordinal the floor's foes fight at. */
	int realm() {
		return Rank.ofStep(rankStep(floor)).realm().ordinal();
	}

	List<ServerPlayer> players() {
		List<ServerPlayer> list = new ArrayList<>();
		for (UUID id : party) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null && player.level() == level && player.isAlive()) {
				list.add(player);
			}
		}
		return list;
	}

	// ================================================================== flow

	void begin() {
		DuniaTickets.touch(level, ChunkPos.containing(origin), 1);
		for (ServerPlayer player : players()) {
			bar.addPlayer(player);
		}
		enterFloor();
	}

	private void enterFloor() {
		clearFloor();
		phase = Phase.INTRO;
		phaseAt = level.getGameTime();
		type = typeOf(floor);
		Vec3 spawn = at(0.0D, 13.0D);
		int i = 0;
		for (ServerPlayer player : players()) {
			double ox = (i % 2 == 0 ? 1 : -1) * (i / 2 + (i % 2)) * 1.5D;
			player.teleport(new TeleportTransition(level, spawn.add(ox, 0.0D, 0.0D), Vec3.ZERO, 180.0F, 0.0F, TeleportTransition.DO_NOTHING));
			player.setPortalCooldown();
			i++;
		}
		sendScene();
		for (ServerPlayer player : players()) {
			Msg.title(player, Component.translatable("kultivator.reruntuhan.menara.floor", floor).withStyle(ChatFormatting.GOLD),
					Component.translatable("kultivator.reruntuhan.menara.type." + type.name().toLowerCase(java.util.Locale.ROOT))
							.withStyle(ChatFormatting.GRAY), 5, 40, 10);
		}
		level.playSound(null, spawn.x, spawn.y, spawn.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F + band() * 0.08F);
		Voice.heavenNear(level, spawn, "heaven.secret_realm", 32.0D);
	}

	/** The floor pattern of DESIGN 34.5.7. */
	static Type typeOf(int floor) {
		return switch ((floor - 1) % 10 + 1) {
			case 1, 2, 3 -> Type.COMBAT;
			case 4 -> Type.PUZZLE;
			case 5 -> Type.ENDURANCE;
			case 6 -> Type.AFFIX;
			case 7 -> Type.HEART;
			case 8 -> Type.MEDITATE;
			case 9 -> Type.ELITE;
			default -> Type.GUARDIAN;
		};
	}

	void tick() {
		if (phase == Phase.ENDED) {
			return;
		}
		List<ServerPlayer> here = players();
		if (here.isEmpty()) {
			end(false, false);
			return;
		}
		long now = level.getGameTime();
		guardArena(here);
		entities.removeIf(Entity::isRemoved);
		switch (phase) {
			case INTRO -> {
				if (now - phaseAt >= INTRO) {
					phase = Phase.PLAY;
					phaseAt = now;
					startPlay();
				}
			}
			case PLAY -> play(here, now);
			case CLEAR -> clearing(here, now);
			default -> {
			}
		}
		if (now % 10L == 0L) {
			updateBar(now);
		}
	}

	/** Keeps the party (and the foes) inside the arena circle. */
	private void guardArena(List<ServerPlayer> here) {
		Vec3 c = centre();
		for (ServerPlayer player : here) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			double dx = player.getX() - c.x;
			double dz = player.getZ() - c.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (player.getY() < origin.getY() - 4 || d > ARENA + 6.0D) {
				player.teleportTo(c.x, origin.getY() + 0.2D, c.z + 12.0D);
				player.setDeltaMovement(Vec3.ZERO);
				player.needsSync = true;
			} else if (d > ARENA + 0.5D) {
				player.setDeltaMovement(new Vec3(-dx / d, 0.0D, -dz / d).scale(0.7D).add(0.0D, 0.25D, 0.0D));
				player.needsSync = true;
			}
		}
		for (Entity entity : entities) {
			if (entity instanceof Mob mob && mob.position().distanceToSqr(c) > (ARENA + 4.0D) * (ARENA + 4.0D)) {
				mob.teleportTo(c.x, origin.getY() + 0.2D, c.z);
			}
		}
	}

	private void startPlay() {
		RandomSource r = RandomSource.create(seed());
		switch (type) {
			case COMBAT -> {
				waves = 2 + (band() >= 5 ? 1 : 0);
				wave = 0;
				nextWaveAt = level.getGameTime();
			}
			case AFFIX -> {
				affix = r.nextInt(4);
				waves = 2;
				wave = 0;
				nextWaveAt = level.getGameTime();
				for (ServerPlayer player : players()) {
					Msg.chat(player, Component.translatable("kultivator.reruntuhan.menara.affix." + affix).withStyle(ChatFormatting.LIGHT_PURPLE));
				}
				if (affix == 1) {
					crystal = part(TekaPartEntity.PILLAR, centre(), 0);
					if (crystal != null) {
						crystal.setState(TekaPartEntity.IDLE);
						crystal.setListener((player, part, attack) -> hitCrystal(player));
					}
				}
			}
			case PUZZLE -> startPuzzle(r);
			case ENDURANCE -> nextPulse = level.getGameTime() + 40L;
			case HEART -> startHeart(r);
			case MEDITATE -> {
				inside.clear();
				nextPulse = level.getGameTime() + 60L;
			}
			case ELITE -> spawnElite();
			case GUARDIAN -> spawnGuardian();
		}
	}

	private long seed() {
		return ((long) floor * 0x9E3779B97F4A7C15L) ^ (level.getSeed() * 31L) ^ party.get(0).getLeastSignificantBits();
	}

	private void play(List<ServerPlayer> here, long now) {
		long age = now - phaseAt;
		switch (type) {
			case COMBAT, AFFIX -> {
				if (type == Type.AFFIX) {
					tickAffix(here, now);
				}
				if (liveMobs() == 0 && now >= nextWaveAt) {
					if (wave >= waves) {
						cleared();
						return;
					}
					spawnWave(wave);
					wave++;
					nextWaveAt = now + 40L;
				}
			}
			case PUZZLE -> {
				if (puzzleSolved) {
					cleared();
				} else if (puzzle == null || puzzle.ended()) {
					failed("kultivator.reruntuhan.menara.fail.puzzle");
				}
			}
			case ENDURANCE -> {
				tickPulses(here, now, 30, 3 + Math.min(4, band() / 2), 0.08D);
				if (age >= ENDURE && !failing) {
					cleared();
				}
			}
			case HEART -> {
				if (puzzle != null) {
					if (puzzleSolved) {
						cleared();
					} else if (puzzle.ended()) {
						failed("kultivator.reruntuhan.menara.fail.heart");
					}
				} else if (heartDone.containsAll(partyOnline(here))) {
					cleared();
				}
			}
			case MEDITATE -> tickMeditation(here, now, age);
			case ELITE, GUARDIAN -> {
				if (bossDown || (boss == null && liveMobs() == 0)) {
					cleared();
				}
			}
		}
	}

	private Set<UUID> partyOnline(List<ServerPlayer> here) {
		Set<UUID> set = new HashSet<>();
		for (ServerPlayer player : here) {
			set.add(player.getUUID());
		}
		return set;
	}

	// ================================================================== combat

	private static final EntityType<?>[][] FOES = {
			{EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER},
			{EntityTypes.HUSK, EntityTypes.STRAY, EntityTypes.SPIDER},
			{EntityTypes.HUSK, EntityTypes.WITHER_SKELETON, EntityTypes.CAVE_SPIDER},
			{EntityTypes.STRAY, EntityTypes.VINDICATOR, EntityTypes.WITHER_SKELETON},
			{EntityTypes.VINDICATOR, EntityTypes.WITHER_SKELETON, EntityTypes.PIGLIN_BRUTE},
			{EntityTypes.PIGLIN_BRUTE, EntityTypes.WITHER_SKELETON, EntityTypes.VINDICATOR},
			{EntityTypes.WITHER_SKELETON, EntityTypes.PIGLIN_BRUTE, EntityTypes.VINDICATOR},
			{EntityTypes.WITHER_SKELETON, EntityTypes.VINDICATOR, EntityTypes.PIGLIN_BRUTE},
			{EntityTypes.PIGLIN_BRUTE, EntityTypes.WITHER_SKELETON, EntityTypes.VINDICATOR},
			{EntityTypes.WITHER_SKELETON, EntityTypes.PIGLIN_BRUTE, EntityTypes.VINDICATOR}};

	private int liveMobs() {
		int n = 0;
		for (Entity entity : entities) {
			if (entity instanceof Mob mob && mob.isAlive()) {
				n++;
			}
		}
		return n;
	}

	private void spawnWave(int index) {
		int party = Math.max(1, players().size());
		int count = Math.min(8, 3 + Math.min(3, party) + step() / 2);
		boolean rogues = step() == 3 && index == 0;
		for (int i = 0; i < count && liveMobs() < 8; i++) {
			double a = Math.PI * 2.0D * i / count + random.nextDouble() * 0.4D;
			Vec3 at = at(Math.cos(a) * 9.0D, Math.sin(a) * 9.0D - 3.0D);
			if (rogues && i < 2 + party) {
				int tier = Math.max(1, Math.min(4, 1 + band() / 3));
				Mob rogue = RogueCultivators.spawn(level, at, tier, realm(), RogueCultivators.VARIANT_ROGUE, RogueCultivators.ROLE_REALM, null);
				if (rogue != null) {
					track(rogue);
					continue;
				}
			}
			EntityType<?>[] list = FOES[band()];
			spawnFoe(list[random.nextInt(list.length)], at, Math.max(1, Math.min(3, 1 + band() / 4)), 1.0D);
		}
		for (ServerPlayer player : players()) {
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.menara.wave", index + 1, waves).withStyle(ChatFormatting.RED));
		}
	}

	private @Nullable Mob spawnFoe(EntityType<?> type, Vec3 at, int tier, double healthFactor) {
		Entity made = type.create(level, EntitySpawnReason.EVENT);
		if (!(made instanceof Mob mob)) {
			if (made != null) {
				made.discard();
			}
			return null;
		}
		mob.snapTo(at.x, at.y + 0.1D, at.z, random.nextFloat() * 360.0F, 0.0F);
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		mob.addTag(NoGrief.TAG);
		mob.addTag(FOE_TAG);
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		Element[] elements = Element.values();
		EliteMonsters.makeElite(mob, tier, elements[random.nextInt(5)], Math.max(0, Math.min(8, realm())));
		if (healthFactor != 1.0D) {
			AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.addOrReplacePermanentModifier(new AttributeModifier(TOWER_HEALTH, healthFactor - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				mob.setHealth(mob.getMaxHealth());
			}
		}
		track(mob);
		aim(mob);
		return mob;
	}

	private void track(Entity entity) {
		entity.addTag(FOE_TAG);
		entities.add(entity);
	}

	private void aim(Mob mob) {
		ServerPlayer nearest = null;
		double best = Double.MAX_VALUE;
		for (ServerPlayer player : players()) {
			double d = player.distanceToSqr(mob);
			if (d < best && !player.isCreative()) {
				best = d;
				nearest = player;
			}
		}
		if (nearest != null) {
			mob.setTarget(nearest);
		}
	}

	private void tickAffix(List<ServerPlayer> here, long now) {
		switch (affix) {
			case 0 -> {
				if (now % 20L == 0L) {
					for (Entity entity : entities) {
						if (entity instanceof Mob mob && mob.isAlive()) {
							mob.heal(mob.getMaxHealth() * 0.02F);
						}
					}
				}
			}
			case 1 -> {
				if (crystal != null && !crystal.isRemoved() && now % 10L == 0L) {
					for (Entity entity : entities) {
						if (entity instanceof Mob mob && mob.isAlive()) {
							mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 2, false, false));
						}
					}
				}
			}
			case 2 -> tickPulses(here, now, 50, 2 + band() / 3, 0.06D);
			default -> {
				if (now % 40L == 0L) {
					for (Entity entity : entities) {
						if (entity instanceof Mob mob && mob.isAlive()) {
							AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
							if (speed != null && speed.getModifier(TOWER_SPEED) == null) {
								speed.addOrReplacePermanentModifier(new AttributeModifier(TOWER_SPEED, 0.4D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
							}
						}
					}
				}
			}
		}
		if (now % 20L == 0L) {
			for (Entity entity : entities) {
				if (entity instanceof Mob mob && mob.isAlive() && mob.getTarget() == null) {
					aim(mob);
				}
			}
		}
	}

	private void hitCrystal(ServerPlayer player) {
		TekaPartEntity c = crystal;
		if (c == null || c.isRemoved()) {
			return;
		}
		crystalHits++;
		RFx.at(level, c.position().add(0.0D, 1.5D, 0.0D), RFx.RING_TURN, 0, 4, 1.0F);
		level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, 0.6F + crystalHits * 0.2F);
		if (crystalHits >= 3) {
			RFx.at(level, c.position().add(0.0D, 1.5D, 0.0D), RFx.DOOR_OPEN, 3, 2, 1.0F);
			level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
			c.discard();
			crystal = null;
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.menara.affix.1.broken").withStyle(ChatFormatting.AQUA));
		}
	}

	// ================================================================== the other floor types

	private void startPuzzle(RandomSource r) {
		Puzzle.Kind[] kinds = {Puzzle.Kind.BAGUA, Puzzle.Kind.UNSUR, Puzzle.Kind.BINTANG, Puzzle.Kind.TIMBANGAN, Puzzle.Kind.CERMIN};
		Puzzle.Kind kind = kinds[r.nextInt(kinds.length)];
		int tier = 1 + Math.min(2, band() / 3);
		puzzleSolved = false;
		puzzle = FreeHost.start(level, new Frame(origin.above(), 0), kind, tier, seed(), true, solved -> puzzleSolved = solved);
	}

	private void startHeart(RandomSource r) {
		riddles.clear();
		heartDone.clear();
		if (band() % 2 == 1) {
			puzzleSolved = false;
			puzzle = FreeHost.start(level, new Frame(origin.above(), 0), Puzzle.Kind.CERMIN, 1 + Math.min(2, band() / 3), seed(), true,
					solved -> puzzleSolved = solved);
			return;
		}
		puzzle = null;
		for (ServerPlayer player : players()) {
			int[] picks = new int[3];
			int first = r.nextInt(RIDDLES);
			for (int i = 0; i < 3; i++) {
				picks[i] = (first + i * (1 + r.nextInt(3)) + i) % RIDDLES;
			}
			// state: 3 riddles, the index asked now, right answers, wrong answers, the shuffled order of the asked one
			riddles.put(player.getUUID(), new int[]{picks[0], picks[1], picks[2], 0, 0, 0, 0, 1, 2});
			ask(player);
		}
	}

	private void ask(ServerPlayer player) {
		int[] s = riddles.get(player.getUUID());
		if (s == null || s[3] >= 3) {
			return;
		}
		int q = s[s[3]];
		int[] order = {0, 1, 2};
		for (int i = 2; i > 0; i--) {
			int k = random.nextInt(i + 1);
			int t = order[i];
			order[i] = order[k];
			order[k] = t;
		}
		s[6] = order[0];
		s[7] = order[1];
		s[8] = order[2];
		CompoundTag tag = new CompoundTag();
		tag.putString("op", "riddle");
		tag.putInt("q", q);
		tag.put("opts", new IntArrayTag(order));
		tag.putInt("n", s[3] + 1);
		tag.putInt("right", s[4]);
		RNet.send(player, tag);
	}

	/** A riddle answer: the shown option {@code option} (0-2); option 0 of the lang triple is the true one. */
	void answer(ServerPlayer player, int option) {
		int[] s = riddles.get(player.getUUID());
		if (type != Type.HEART || phase != Phase.PLAY || s == null || s[3] >= 3 || option < 0 || option > 2) {
			return;
		}
		boolean right = s[6 + option] == 0;
		if (right) {
			s[4]++;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.menara.ujian.right").withStyle(ChatFormatting.GREEN));
		} else {
			s[5]++;
			Reruntuhan.backlash(player, Reruntuhan.config().puzzleBacklash);
			Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.menara.ujian.wrong").withStyle(ChatFormatting.RED));
		}
		s[3]++;
		if (s[4] >= 2) {
			heartDone.add(player.getUUID());
			CompoundTag tag = new CompoundTag();
			tag.putString("op", "riddle_end");
			RNet.send(player, tag);
			Msg.chat(player, Component.translatable("kultivator.reruntuhan.menara.ujian.passed").withStyle(ChatFormatting.GOLD));
		} else if (s[5] >= 2) {
			CompoundTag tag = new CompoundTag();
			tag.putString("op", "riddle_end");
			RNet.send(player, tag);
			failed("kultivator.reruntuhan.menara.fail.heart");
		} else {
			ask(player);
		}
	}

	/** Formation pulses (endurance, the burning affix): circles telegraphed for 20 ticks, a share each, never lethal. */
	private void tickPulses(List<ServerPlayer> here, long now, int every, int count, double share) {
		if (now >= nextPulse - 20L && pulseMarks.isEmpty()) {
			for (int i = 0; i < count; i++) {
				double a = random.nextDouble() * Math.PI * 2.0D;
				double r = random.nextDouble() * (ARENA - 2.0D);
				Vec3 m = at(Math.cos(a) * r, Math.sin(a) * r);
				pulseMarks.add(m);
				RFx.at(level, m, RFx.PULSE_WARN, 20, 0, 2.6F);
			}
			// one always under a challenger, so standing still is never safe
			if (!here.isEmpty()) {
				Vec3 m = here.get(random.nextInt(here.size())).position();
				pulseMarks.add(m);
				RFx.at(level, m, RFx.PULSE_WARN, 20, 0, 2.6F);
			}
		}
		if (now >= nextPulse) {
			for (Vec3 m : pulseMarks) {
				RFx.at(level, m, RFx.PULSE_HIT, 1, 0, 2.6F);
				for (ServerPlayer player : here) {
					if (player.position().distanceToSqr(m) <= 2.6D * 2.6D) {
						if (type == Type.ENDURANCE && player.getHealth() <= 2.0F && !player.isCreative()) {
							failing = true;
							failed("kultivator.reruntuhan.menara.fail.endure");
							return;
						}
						Reruntuhan.backlash(player, share);
					}
				}
			}
			pulseMarks.clear();
			nextPulse = now + every;
		}
	}

	private void tickMeditation(List<ServerPlayer> here, long now, long age) {
		Vec3 c = centre();
		boolean all = true;
		for (ServerPlayer player : here) {
			double d = Math.sqrt(player.distanceToSqr(c.x, player.getY(), c.z));
			int have = inside.getOrDefault(player.getUUID(), 0);
			if (d <= CIRCLE) {
				have = Math.min(MEDITATE_NEED, have + 1);
				inside.put(player.getUUID(), have);
			}
			if (have < MEDITATE_NEED) {
				all = false;
			}
			if (now % 20L == 0L) {
				Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.menara.meditate", have / 20, MEDITATE_NEED / 20)
						.withStyle(d <= CIRCLE ? ChatFormatting.AQUA : ChatFormatting.GRAY));
			}
		}
		if (all) {
			cleared();
			return;
		}
		if (now == nextPulse - 20L) {
			RFx.at(level, c, RFx.TELEGRAPH, 0x9FE0FF, 20, (float) CIRCLE + 0.5F);
		}
		if (now >= nextPulse) {
			nextPulse = now + Math.max(40L, 70L - band() * 3L);
			RFx.at(level, c, RFx.QI_WAVE, 0x9FE0FF, 0, 8.0F);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.5F, 1.6F);
			for (ServerPlayer player : here) {
				double d = Math.sqrt(player.distanceToSqr(c.x, player.getY(), c.z));
				if (d > 8.0D || player.isCreative()) {
					continue;
				}
				double force = (0.9D + 0.06D * band()) * (player.isShiftKeyDown() ? 0.35D : 1.0D);
				Vec3 dir = d < 0.01D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(player.getX() - c.x, 0.0D, player.getZ() - c.z).normalize();
				player.push(dir.x * force, 0.18D, dir.z * force);
				player.needsSync = true;
			}
		}
		if (age >= MEDITATE_LIMIT) {
			failed("kultivator.reruntuhan.menara.fail.meditate");
		}
	}

	private void spawnElite() {
		EntityType<?>[] kings = {EntityTypes.HUSK, EntityTypes.STRAY, EntityTypes.WITHER_SKELETON, EntityTypes.VINDICATOR, EntityTypes.PIGLIN_BRUTE,
				EntityTypes.WITHER_SKELETON, EntityTypes.VINDICATOR, EntityTypes.PIGLIN_BRUTE, EntityTypes.WITHER_SKELETON, EntityTypes.PIGLIN_BRUTE};
		Mob king = spawnFoe(kings[band()], at(0.0D, -4.0D), 3, 3.0D * (1.0D + 0.5D * (players().size() - 1)));
		if (king != null) {
			AttributeInstance scale = king.getAttribute(Attributes.SCALE);
			if (scale != null) {
				scale.addOrReplacePermanentModifier(new AttributeModifier(TOWER_HEALTH, 0.6D, AttributeModifier.Operation.ADD_VALUE));
			}
			level.playSound(null, king.getX(), king.getY(), king.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.7F);
		}
		if (band() >= 5) {
			spawnFoe(FOES[band()][0], at(-4.0D, -2.0D), 2, 1.0D);
			spawnFoe(FOES[band()][1], at(4.0D, -2.0D), 2, 1.0D);
		}
	}

	private void spawnGuardian() {
		bossDown = false;
		int partySize = Math.max(1, players().size());
		if (floor >= 100) {
			ServerPlayer strongest = null;
			for (ServerPlayer player : players()) {
				if (strongest == null || player.getMaxHealth() > strongest.getMaxHealth()) {
					strongest = player;
				}
			}
			if (strongest == null) {
				return;
			}
			BayanganEntity shadow = make(REntities.BAYANGAN, at(0.0D, -6.0D), 0.0F);
			if (shadow == null) {
				return;
			}
			shadow.mirror(strongest.getUUID(), strongest.getPlainTextName(), false);
			shadow.setTier(3);
			ShadowBrain.mirrorStats(shadow, strongest, 1.0D);
			shadow.setBase(Attributes.MAX_HEALTH, shadow.getMaxHealth() * (1.0D + 0.5D * (partySize - 1)) * 2.0D);
			shadow.setHealth(shadow.getMaxHealth());
			shadow.setIntakeCap(0.05D);
			shadow.setBrain(new ShadowBrain(strongest.getUUID(), centre(), ARENA, 1.0D, creature -> bossDown = true));
			if (addFoe(shadow)) {
				boss = shadow;
				RFx.big(level, shadow.position().add(0.0D, 1.0D, 0.0D), RFx.SOUL_APPEAR, 0x6A4AA0, 2, 2.0F);
				for (ServerPlayer player : players()) {
					Msg.title(player, Component.translatable("kultivator.reruntuhan.menara.diri.title").withStyle(ChatFormatting.DARK_PURPLE),
							Component.translatable("kultivator.reruntuhan.menara.diri.sub").withStyle(ChatFormatting.GRAY), 10, 60, 20);
				}
			}
			return;
		}
		PenjagaLantaiEntity guard = make(REntities.PENJAGA_LANTAI, at(0.0D, -6.0D), 0.0F);
		if (guard == null) {
			return;
		}
		int kind = Math.min(PenjagaLantaiEntity.KINDS - 1, band());
		guard.setKind(kind);
		guard.setTier(1 + band() / 3);
		double health = 300.0D * Math.pow(1.0D + band(), 1.6D) * (1.0D + 0.5D * (partySize - 1));
		guard.setBase(Attributes.MAX_HEALTH, health);
		guard.setBase(Attributes.ARMOR, 6.0D + 2.0D * band());
		guard.setHealth(guard.getMaxHealth());
		guard.setIntakeCap(0.04D);
		guard.setBrain(new FloorBrain(centre(), ARENA, creature -> bossDown = true));
		if (addFoe(guard)) {
			boss = guard;
			RFx.big(level, guard.position(), RFx.GUARDIAN_RISE, 9, kind, 3.0F);
			Voice.boom(guard, "boss.awaken", 48.0D);
			for (ServerPlayer player : players()) {
				Msg.title(player, guard.getDisplayName().copy().withStyle(ChatFormatting.GOLD),
						Component.translatable("kultivator.reruntuhan.menara.guardian.sub", band() + 1).withStyle(ChatFormatting.GRAY), 10, 50, 20);
			}
		}
	}

	private <T extends Entity> @Nullable T make(EntityType<T> type, Vec3 at, float yaw) {
		T entity = type.create(level, EntitySpawnReason.EVENT);
		if (entity == null) {
			return null;
		}
		entity.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		entity.addTag(NoGrief.TAG);
		return entity;
	}

	private boolean addFoe(Entity entity) {
		if (!level.addFreshEntity(entity)) {
			return false;
		}
		track(entity);
		return true;
	}

	private @Nullable TekaPartEntity part(int role, Vec3 at, int arg) {
		TekaPartEntity made = make(REntities.TEKA_PLAT, at, 0.0F);
		if (made == null) {
			return null;
		}
		made.setRole(role);
		made.setCenter(centre());
		made.setArg(arg);
		made.setListener((player, p, attack) -> {
		});
		made.addTag(TekaPartEntity.TAG);
		if (!level.addFreshEntity(made)) {
			return null;
		}
		entities.add(made);
		return made;
	}

	// ================================================================== clear, fail, leave

	private void cleared() {
		if (phase != Phase.PLAY) {
			return;
		}
		phase = Phase.CLEAR;
		phaseAt = level.getGameTime();
		discardFoes();
		if (puzzle != null && !puzzle.ended()) {
			puzzle.end(true);
		}
		puzzle = null;
		RFx.at(level, centre(), RFx.TOWER_CLEAR, floor, band(), 1.0F);
		level.playSound(null, centre().x, centre().y, centre().z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
		for (ServerPlayer player : players()) {
			reward(player);
		}
		if (floor % 10 == 0) {
			RFx.big(level, centre(), RFx.TOWER_MILESTONE, floor, band(), 1.0F);
			Voice.heavenNear(level, centre(), "heaven.witness", 48.0D);
		}
		if (floor >= 100) {
			return;
		}
		padUp = part(TekaPartEntity.PAD, at(0.0D, -2.0D), 1);
		padOut = part(TekaPartEntity.PAD, at(0.0D, 6.0D), 0);
	}

	private void clearing(List<ServerPlayer> here, long now) {
		long age = now - phaseAt;
		if (floor >= 100) {
			if (age >= 200L) {
				end(true, false);
			}
			return;
		}
		if (age < 30L) {
			return;
		}
		for (ServerPlayer player : here) {
			if (padUp != null && player.position().distanceToSqr(padUp.position()) < 1.3D * 1.3D) {
				floor++;
				enterFloor();
				return;
			}
			if (padOut != null && player.position().distanceToSqr(padOut.position()) < 1.3D * 1.3D) {
				end(true, false);
				return;
			}
		}
		if (age > 20L * 60L) {
			end(true, false);
		}
	}

	private void reward(ServerPlayer player) {
		int best = Menara.best(player);
		boolean first = floor > best;
		TowerRecords.get(level.getServer()).record(player, floor, level.getServer().overworld().getGameTime());
		Msg.title(player, Component.translatable("kultivator.reruntuhan.menara.cleared", floor).withStyle(ChatFormatting.GOLD),
				Component.translatable(first ? "kultivator.reruntuhan.menara.cleared.first" : "kultivator.reruntuhan.menara.cleared.again")
						.withStyle(ChatFormatting.GRAY), 5, 40, 10);
		if (!first || free) {
			if (free && first) {
				Menara.setBest(player, floor);
			}
			return;
		}
		Menara.setBest(player, floor);
		RandomSource r = player.getRandom();
		int realm = Rank.ofStep(rankStep(floor)).realm().ordinal();
		int[][] nbRange = {{1, 2}, {3, 6}, {8, 15}, {20, 40}, {40, 80}, {80, 150}, {150, 250}, {200, 400}, {300, 600}};
		int[] range = nbRange[Math.max(0, Math.min(8, realm))];
		int nb = RRewards.roll(r, range[0], range[1], Reruntuhan.config().towerRewardScale);
		RRewards.stones(player, nb);
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.menara.pay", floor, nb).withStyle(ChatFormatting.YELLOW));
		if (floor % 10 == 0) {
			List<ItemStack> chest = new ArrayList<>();
			int b = band();
			chest.add(RRewards.core(r, Math.min(4, 1 + b / 3)));
			chest.add(RRewards.core(r, Math.min(4, 1 + b / 3)));
			chest.add(RRewards.herb(r, 100 * (b + 1), 300 * (b + 1)));
			chest.add(RRewards.herb(r, 100 * (b + 1), 300 * (b + 1)));
			if (floor == 50 || floor == 90) {
				String[] themes = dev.kultivator.technique.source.InheritanceTrials.THEMES.toArray(new String[0]);
				chest.add(RRewards.jade(themes[r.nextInt(themes.length)]));
			}
			if (floor >= 100) {
				chest.add(r.nextBoolean() ? flameSeed() : new ItemStack(AlamItems.TOMB_ESSENCE, 2));
			}
			RRewards.giveAll(player, chest, "kultivator.reruntuhan.menara.chest");
		}
		if (floor >= 10) {
			Advancements.grant(player, "dunia/tower_10");
		}
		if (floor >= 100) {
			Advancements.grant(player, "dunia/tower_100");
			Reruntuhan.data(player).putBoolean("penakluk", true);
			dev.kultivator.data.Cultivators.dirty(player);
			Msg.broadcast(level.getServer(), Component.translatable("kultivator.reruntuhan.menara.conqueror", player.getDisplayName())
					.withStyle(ChatFormatting.GOLD));
		}
	}

	private void failed(String reasonKey) {
		if (phase == Phase.ENDED) {
			return;
		}
		for (ServerPlayer player : players()) {
			Msg.chat(player, Component.translatable(reasonKey).withStyle(ChatFormatting.RED));
			if (!free) {
				Menara.giveLuka(player);
			}
		}
		end(false, false);
	}

	/** A lethal blow (already at 1 HP): this player leaves to the lobby with Luka Menara. */
	void fallen(ServerPlayer player) {
		Msg.chat(player, Component.translatable("kultivator.reruntuhan.menara.fallen").withStyle(ChatFormatting.RED));
		if (!free) {
			Menara.giveLuka(player);
		}
		party.remove(player.getUUID());
		Menara.BY_PLAYER.remove(player.getUUID());
		bar.removePlayer(player);
		RNet.send(player, sceneEnd());
		Reruntuhan.schedule(1, () -> Menara.toLobby(player));
		if (players().isEmpty()) {
			end(false, false);
		}
	}

	/** A player left the level or logged out. */
	void drop(ServerPlayer player) {
		party.remove(player.getUUID());
		Menara.BY_PLAYER.remove(player.getUUID());
		bar.removePlayer(player);
		if (party.isEmpty()) {
			end(false, false);
		}
	}

	/** Ends the climb: the party goes to the lobby; {@code silent} skips the teleports (a server stop). */
	void end(boolean success, boolean silent) {
		if (phase == Phase.ENDED) {
			return;
		}
		phase = Phase.ENDED;
		clearFloor();
		Menara.RUNS.remove(this);
		for (UUID id : List.copyOf(party)) {
			Menara.BY_PLAYER.remove(id);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player == null) {
				continue;
			}
			RNet.send(player, sceneEnd());
			if (!silent) {
				Menara.toLobby(player);
				if (success) {
					Msg.chat(player, Component.translatable("kultivator.reruntuhan.menara.back", Menara.best(player)).withStyle(ChatFormatting.GOLD));
				}
			}
		}
		bar.removeAllPlayers();
		Menara.release(slot);
	}

	private void clearFloor() {
		discardFoes();
		for (Entity entity : entities) {
			if (!entity.isRemoved()) {
				entity.discard();
			}
		}
		entities.clear();
		if (puzzle != null && !puzzle.ended()) {
			puzzle.end(false);
		}
		puzzle = null;
		boss = null;
		crystal = null;
		padUp = null;
		padOut = null;
		pulseMarks.clear();
		inside.clear();
		riddles.clear();
		heartDone.clear();
		failing = false;
		crystalHits = 0;
	}

	private void discardFoes() {
		for (Entity entity : List.copyOf(entities)) {
			if (entity instanceof RCreature creature) {
				creature.setBrain(null);
			}
			if (!(entity instanceof TekaPartEntity) && !entity.isRemoved()) {
				entity.discard();
			}
		}
		entities.removeIf(Entity::isRemoved);
		boss = null;
	}

	// ================================================================== client

	private void sendScene() {
		CompoundTag scene = new CompoundTag();
		scene.putString("op", "scene");
		scene.putInt("ox", origin.getX());
		scene.putInt("oy", origin.getY());
		scene.putInt("oz", origin.getZ());
		scene.putInt("floor", floor);
		scene.putInt("band", band());
		scene.putString("type", type.name().toLowerCase(java.util.Locale.ROOT));
		scene.putFloat("arena", (float) ARENA);
		scene.putFloat("circle", type == Type.MEDITATE ? (float) CIRCLE : 0.0F);
		for (ServerPlayer player : players()) {
			RNet.send(player, scene);
		}
	}

	private static CompoundTag sceneEnd() {
		CompoundTag tag = new CompoundTag();
		tag.putString("op", "scene_end");
		return tag;
	}

	private void updateBar(long now) {
		Component objective;
		float progress;
		long age = now - phaseAt;
		switch (phase) {
			case INTRO -> {
				objective = Component.translatable("kultivator.reruntuhan.menara.bar.ready");
				progress = 1.0F - age / (float) INTRO;
			}
			case CLEAR -> {
				objective = Component.translatable(floor >= 100 ? "kultivator.reruntuhan.menara.bar.top" : "kultivator.reruntuhan.menara.bar.pads");
				progress = 1.0F;
			}
			default -> {
				switch (type) {
					case COMBAT, AFFIX -> {
						objective = Component.translatable("kultivator.reruntuhan.menara.bar.waves", Math.max(1, wave), waves, liveMobs());
						progress = waves <= 0 ? 1.0F : (wave - (liveMobs() > 0 ? 0.5F : 0.0F)) / waves;
					}
					case PUZZLE -> {
						FreeHost p = puzzle;
						objective = p == null ? Component.empty() : p.puzzle().objective();
						progress = p == null ? 0.0F : p.timeLeft() / (float) Puzzle.TIME_LIMIT;
					}
					case ENDURANCE -> {
						objective = Component.translatable("kultivator.reruntuhan.menara.bar.endure", Math.max(0L, (ENDURE - age) / 20L));
						progress = 1.0F - age / (float) ENDURE;
					}
					case HEART -> {
						FreeHost p = puzzle;
						objective = p != null ? p.puzzle().objective()
								: Component.translatable("kultivator.reruntuhan.menara.bar.heart", heartDone.size(), players().size());
						progress = p != null ? p.puzzle().progress() : heartDone.size() / (float) Math.max(1, players().size());
					}
					case MEDITATE -> {
						int least = MEDITATE_NEED;
						for (ServerPlayer player : players()) {
							least = Math.min(least, inside.getOrDefault(player.getUUID(), 0));
						}
						objective = Component.translatable("kultivator.reruntuhan.menara.bar.meditate", least / 20, MEDITATE_NEED / 20,
								Math.max(0L, (MEDITATE_LIMIT - age) / 20L));
						progress = least / (float) MEDITATE_NEED;
					}
					default -> {
						RCreature b = boss;
						Mob any = null;
						for (Entity entity : entities) {
							if (entity instanceof Mob mob && mob.isAlive()) {
								any = mob;
								break;
							}
						}
						Mob shown = b != null ? b : any;
						objective = shown == null ? Component.empty() : shown.getDisplayName();
						progress = shown == null ? 0.0F : shown.getHealth() / Math.max(1.0F, shown.getMaxHealth());
					}
				}
			}
		}
		bar.setName(Component.translatable("kultivator.reruntuhan.menara.bar", floor,
				Component.translatable("kultivator.reruntuhan.menara.type." + type.name().toLowerCase(java.util.Locale.ROOT)), objective));
		bar.setProgress(Mth.clamp(progress, 0.0F, 1.0F));
		bar.setColor(BAR_COLORS[band()]);
	}

	private static final BossEvent.BossBarColor[] BAR_COLORS = {BossEvent.BossBarColor.GREEN, BossEvent.BossBarColor.WHITE,
			BossEvent.BossBarColor.RED, BossEvent.BossBarColor.BLUE, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarColor.GREEN,
			BossEvent.BossBarColor.YELLOW, BossEvent.BossBarColor.PINK, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarColor.YELLOW};

	// ================================================================== tests

	void testClear() {
		if (phase == Phase.INTRO) {
			phase = Phase.PLAY;
		}
		cleared();
	}

	void testFail() {
		failed("kultivator.reruntuhan.menara.fail.test");
	}

	void testFloor(int target) {
		floor = Math.max(1, Math.min(100, target));
		enterFloor();
	}

	/** A Benih Api Surgawi looked up by id (the seed class is package-private), or Esensi Makam when it is missing. */
	private static ItemStack flameSeed() {
		net.minecraft.world.item.Item seed = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(dev.kultivator.Kultivator.id("heavenly_flame_seed"));
		return seed == net.minecraft.world.item.Items.AIR ? new ItemStack(AlamItems.TOMB_ESSENCE, 2) : new ItemStack(seed);
	}

	String describe() {
		return "slot=" + slot + " floor=" + floor + " type=" + type + " phase=" + phase + " party=" + party.size() + " foes=" + liveMobs();
	}
}
