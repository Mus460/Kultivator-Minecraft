package dev.kultivator.dunia.kota;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.RivalService;
import dev.kultivator.dunia.api.TreasureBirth;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.musuh.LootPouches;
import dev.kultivator.musuh.MusuhFxIds;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.util.Targeting;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saingan, the rival groups of treasure hunters (DESIGN 34.5.3, contract C34.K1 {@link RivalService}). One group per NPC
 * sect of the world ({@link SainganData}; integration audit T12: group {@code g<i>} is the travelling band of sect
 * {@code s<i>}, named after it, home region at its gate), each a leader and 2-4 members with a realm band that grows with
 * the world's days but never more than one realm above the strongest player of the last 7 days.
 * <p>
 * Race: on {@code TREASURE_BORN} 1-3 groups whose region is within 3 000 blocks get an ETA of 60-240 s. The race is
 * virtual until a player is within {@code kota.racePhysicalRadius} (128) of the birth; then each group arrives at its ETA
 * (a sword descent drawn by the client) and walks to the treasure. Two groups present fight each other (the loser flees
 * at 25 %); a group that reaches the treasure channels 5 s and claims it unless a player was faster. They attack
 * players who stand at the treasure and players who attack them, and ignore everyone else. Ambush: after a player
 * claims a treasure, a group with a grudge of 40+ (or a Licik group that raced) comes 60-300 s later when the player is
 * outdoors and outside a town: 3 s of killing intent, then the leader and up to 3 members. At most
 * {@code kota.ambushPerDay} per player per in-game day. A beaten leader drops a Kantong Saingan, the grudge resets to 20.
 */
public final class Saingan implements RivalService {

	static final Saingan SERVICE = new Saingan();
	static final int RACE_RANGE = 3000;
	static final int CHANNEL_TICKS = 100;
	static final int VIRTUAL_CLAIM_TICKS = 6000;
	static final int WARNING_TICKS = 60;
	static final float FLEE_SHARE = 0.25F;

	/** A group racing to a birth. */
	static final class Race {
		final TreasureBirth birth;
		final String group;
		final long eta;
		boolean arrived;
		final List<KultivatorNpc> members = new ArrayList<>();
		long channelSince = -1L;

		Race(TreasureBirth birth, String group, long eta) {
			this.birth = birth;
			this.group = group;
			this.eta = eta;
		}
	}

	/** A pending ambush ({@code waits}: how often it already waited for the player to be free or outside safe ground). */
	record Ambush(UUID player, String group, long at, int tier, boolean warned, int waits) {

		Ambush(UUID player, String group, long at, int tier, boolean warned) {
			this(player, group, at, tier, warned, 0);
		}
	}

	/** A band that waited this often (every {@link Sibuk#RETRY_TICKS}, 5 minutes in all) gives up; its grudge cools by 5. */
	static final int MAX_WAITS = 30;
	/** Blocks around a birth whose strongest player caps the racing band (integration audit S20). */
	static final double RACE_BAND_RADIUS = 128.0D;

	private static final List<Race> RACES = new ArrayList<>();
	private static final List<Ambush> AMBUSHES = new ArrayList<>();
	/** Player -> the members of the band that ambushed them (an enemy event while one still stands). */
	private static final Map<UUID, List<KultivatorNpc>> LIVE_AMBUSH = new HashMap<>();

	static void init() {
		// Towns and Kota Abadi are safe ground for every enemy system (integration audit T4); answered while module K is on.
		DuniaApi.addSafeGround((level, pos) -> Kota.enabled()
				&& (Towns.inTown(level, pos) || DuniaAnchors.nearest(level, Vec3.atCenterOf(pos), "kota/", Towns.SAFE_RADIUS) != null));
		// A warned or striking rival band is an enemy event of the shared pressure (one at a time, daily cap).
		Sibuk.registerEnemy("saingan", player -> ambushLive(player.getUUID()));
		if (!Kota.config().rivals) {
			return;
		}
		DuniaApi.setRivals(SERVICE);
		DuniaEvents.TREASURE_BORN.register((level, birth) -> SERVICE.raceTo(level, birth));
		DuniaEvents.TREASURE_CLAIMED.register(Saingan::onClaimed);
	}

	static void clear() {
		RACES.clear();
		AMBUSHES.clear();
		LIVE_AMBUSH.clear();
	}

	/** True while a warned ambush is about to strike the player, or one of its band still stands. */
	static boolean ambushLive(UUID player) {
		for (Ambush ambush : AMBUSHES) {
			if (ambush.player().equals(player) && ambush.warned()) {
				return true;
			}
		}
		List<KultivatorNpc> band = LIVE_AMBUSH.get(player);
		if (band == null) {
			return false;
		}
		band.removeIf(member -> member.isRemoved() || !member.isAlive());
		if (band.isEmpty()) {
			LIVE_AMBUSH.remove(player);
			return false;
		}
		return true;
	}

	/** The group's colour (0xB0B0B0 for an unknown one). */
	static int colorOf(String group) {
		MinecraftServer server = Kota.server();
		if (server == null || group == null || group.isEmpty()) {
			return 0xB0B0B0;
		}
		SainganData.Group g = SainganData.get(server).byId(group);
		return g == null ? 0xB0B0B0 : g.color;
	}

	/** The group's name component. */
	static MutableComponent groupName(SainganData.Group group) {
		return group.name().copy().withColor(group.color);
	}

	/** The realm band of the groups today: grows by one every 10 in-game days, at most one above the strongest recent player. */
	static int band(MinecraftServer server) {
		long day = Kota.day(server);
		SainganData data = SainganData.get(server);
		int byDays = (int) Math.min(Realm.TRUE_IMMORTAL.ordinal(), 1L + day / 10L);
		int cap = Math.min(Realm.TRUE_IMMORTAL.ordinal(), data.strongestRecent(day) + 1);
		return Math.max(1, Math.min(byDays, cap));
	}

	/** The realm a treasure tier is meant for (T1 Foundation, T2 Nascent Soul, T3 Dao Union, T4 True Immortal). */
	static int realmOfTier(int tier) {
		return switch (Mth.clamp(tier, 1, 4)) {
			case 1 -> Realm.FOUNDATION.ordinal();
			case 2 -> Realm.NASCENT_SOUL.ordinal();
			case 3 -> Realm.DAO_UNION.ordinal();
			default -> Realm.TRUE_IMMORTAL.ordinal();
		};
	}

	/**
	 * The band of a group racing to {@code birth} (integration audit S20): the server's band, but never above the realm
	 * the treasure tier is meant for, nor more than one realm above the strongest player within 128 blocks of it.
	 */
	static int raceBand(ServerLevel level, TreasureBirth birth) {
		int band = Math.min(band(level.getServer()), realmOfTier(birth.tier()));
		Vec3 at = Vec3.atBottomCenterOf(birth.pos());
		int strongest = -1;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.position().distanceToSqr(at) <= RACE_BAND_RADIUS * RACE_BAND_RADIUS) {
				strongest = Math.max(strongest, Cultivators.get(player).realm().ordinal());
			}
		}
		if (strongest >= 0) {
			band = Math.min(band, strongest + 1);
		}
		return Math.max(1, band);
	}

	// ================================================================== RivalService

	@Override
	public List<String> nearRivals(ServerLevel level, BlockPos pos, int radius) {
		List<String> list = new ArrayList<>();
		if (level.dimension() != Level.OVERWORLD) {
			return list;
		}
		for (SainganData.Group group : SainganData.get(level.getServer()).groups()) {
			double dx = group.region.getX() - pos.getX();
			double dz = group.region.getZ() - pos.getZ();
			if (dx * dx + dz * dz <= (double) radius * radius) {
				list.add(group.id);
			}
		}
		return list;
	}

	@Override
	public void raceTo(ServerLevel level, TreasureBirth birth) {
		if (!Kota.enabled() || !Kota.config().rivals) {
			return;
		}
		List<String> near = nearRivals(level, birth.pos(), RACE_RANGE);
		if (near.isEmpty() && level.dimension() == Level.OVERWORLD) {
			// the nearest group hears of it anyway (the whole world sees a rainbow pillar)
			SainganData.Group best = nearest(level.getServer(), birth.pos());
			if (best != null) {
				near.add(best.id);
			}
		}
		RandomSource random = level.getRandom();
		int count = Math.min(near.size(), 1 + random.nextInt(3));
		long now = level.getGameTime();
		for (int i = 0; i < count; i++) {
			String id = near.remove(random.nextInt(near.size()));
			SainganData.Group group = SainganData.get(level.getServer()).byId(id);
			if (group == null) {
				continue;
			}
			double distance = Math.sqrt(group.region.distSqr(birth.pos()));
			long eta = now + 1200L + (long) Mth.clamp(distance / RACE_RANGE * 3600.0D, 0.0D, 3600.0D) + random.nextInt(200);
			RACES.add(new Race(birth, id, eta));
		}
	}

	@Override
	public void ambush(ServerPlayer player, int tier) {
		SainganData.Group group = worstFor(player);
		if (group != null) {
			strike(player, group, Mth.clamp(tier, 1, 4));
		}
	}

	@Override
	public @Nullable Component name(String rivalId) {
		MinecraftServer server = Kota.server();
		if (server == null) {
			return null;
		}
		SainganData.Group group = SainganData.get(server).byId(rivalId);
		return group == null ? null : groupName(group);
	}

	@Override
	public List<String> all() {
		MinecraftServer server = Kota.server();
		List<String> list = new ArrayList<>();
		if (server != null) {
			for (SainganData.Group group : SainganData.get(server).groups()) {
				list.add(group.id);
			}
		}
		return list;
	}

	@Override
	public @Nullable BlockPos region(String rivalId) {
		MinecraftServer server = Kota.server();
		if (server == null) {
			return null;
		}
		SainganData.Group group = SainganData.get(server).byId(rivalId);
		return group == null ? null : group.region;
	}

	@Override
	public int color(String rivalId) {
		return colorOf(rivalId);
	}

	// ================================================================== the auction's bidders

	record Member(Component name, String group) {
	}

	/** {@code n} members of random groups ("Mo Shen dari Sekte Pedang Awan Ungu": a band carries its sect's name). */
	static List<Member> bidders(MinecraftServer server, RandomSource random, int n) {
		List<Member> list = new ArrayList<>();
		List<SainganData.Group> groups = SainganData.get(server).groups();
		for (int i = 0; i < n && !groups.isEmpty(); i++) {
			SainganData.Group group = groups.get(random.nextInt(groups.size()));
			int look = group.members[random.nextInt(group.members.length)];
			list.add(new Member(Component.translatable("kultivator.kota.saingan.dari", KotaNames.person(look, NpcRole.SAINGAN), groupName(group)), group.id));
		}
		return list;
	}

	// ================================================================== tick

	static void tick(MinecraftServer server, long tick) {
		if (tick % 20L != 0L || !Kota.enabled() || !Kota.config().rivals) {
			return;
		}
		if (tick % 1200L == 0L) {
			SainganData data = SainganData.get(server);
			long day = Kota.day(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				data.seen(player.getUUID(), Cultivators.get(player).realm().ordinal(), day);
			}
		}
		tickRaces(server);
		tickAmbushes(server);
	}

	private static void tickRaces(MinecraftServer server) {
		for (Iterator<Race> it = RACES.iterator(); it.hasNext(); ) {
			Race race = it.next();
			ServerLevel level = server.getLevel(race.birth.dim());
			if (level == null) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			boolean live = false;
			for (TreasureBirth birth : DuniaApi.treasures().live(level)) {
				if (birth.id() == race.birth.id()) {
					live = true;
					break;
				}
			}
			if (!live || now >= race.birth.ends()) {
				dismiss(race);
				it.remove();
				continue;
			}
			Vec3 target = Vec3.atBottomCenterOf(race.birth.pos());
			ServerPlayer near = nearestPlayer(level, target, Kota.config().racePhysicalRadius);
			if (!race.arrived) {
				if (near != null && now >= race.eta) {
					arrive(level, race, near);
				} else if (near == null && now >= race.eta + VIRTUAL_CLAIM_TICKS) {
					if (DuniaApi.treasures().claimByRival(level, race.birth.id(), race.group)) {
						SainganData.get(server).win(race.group);
					}
					it.remove();
				}
				continue;
			}
			race.members.removeIf(member -> member.isRemoved() || !member.isAlive());
			if (race.members.isEmpty()) {
				SainganData.get(server).loss(race.group);
				it.remove();
				continue;
			}
			// players standing at the treasure are claimants: the group turns on them
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && !player.isCreative() && player.position().distanceToSqr(target) <= 3.0D * 3.0D) {
					for (KultivatorNpc member : race.members) {
						member.markHostile(player);
					}
				}
			}
			boolean atTreasure = false;
			for (KultivatorNpc member : race.members) {
				if (member.position().distanceToSqr(target) <= 3.0D * 3.0D && member.getTarget() == null) {
					atTreasure = true;
					member.setFlag(KultivatorNpc.FLAG_CHANNEL, true);
				} else {
					member.setFlag(KultivatorNpc.FLAG_CHANNEL, false);
				}
			}
			if (atTreasure) {
				if (race.channelSince < 0L) {
					race.channelSince = now;
				} else if (now - race.channelSince >= CHANNEL_TICKS) {
					if (DuniaApi.treasures().claimByRival(level, race.birth.id(), race.group)) {
						SainganData.get(server).win(race.group);
						KultivatorNpc leader = race.members.getFirst();
						KotaVoice.say(leader, KotaVoice.RIVAL_CLAIM, "rogue.defeat", "kultivator.kota.saingan.suara.klaim");
						for (ServerPlayer player : level.players()) {
							if (player.position().distanceToSqr(target) <= 128.0D * 128.0D) {
								Msg.chat(player, Component.translatable("kultivator.kota.saingan.klaim", name(server, race.group)).withStyle(ChatFormatting.RED));
							}
						}
					}
					dismissLater(race);
					it.remove();
				}
			} else {
				race.channelSince = -1L;
			}
		}
	}

	private static void arrive(ServerLevel level, Race race, ServerPlayer witness) {
		race.arrived = true;
		SainganData.Group group = SainganData.get(level.getServer()).byId(race.group);
		if (group == null) {
			return;
		}
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		Vec3 base = Vec3.atBottomCenterOf(race.birth.pos()).add(Math.cos(angle) * 12.0D, 0.0D, Math.sin(angle) * 12.0D);
		int bx = Mth.floor(base.x);
		int bz = Mth.floor(base.z);
		if (!level.isPositionEntityTicking(new BlockPos(bx, 0, bz))) {
			base = Vec3.atBottomCenterOf(race.birth.pos());
			bx = Mth.floor(base.x);
			bz = Mth.floor(base.z);
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		Vec3 landing = new Vec3(base.x, y, base.z);
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(landing) <= 160.0D * 160.0D) {
				KotaNet.fx(player, KotaNet.FX_DESCENT, landing.x, landing.y, landing.z, group.color, 1 + group.members.length, 50, null);
			}
		}
		int band = raceBand(level, race.birth);
		List<KultivatorNpc> spawned = spawnGroup(level, group, landing, band, Math.min(3, group.members.length), true);
		race.members.addAll(spawned);
		for (KultivatorNpc member : spawned) {
			member.memory().putLong("birth", race.birth.pos().asLong());
			member.memory().putBoolean("racing", true);
		}
		// two groups at one treasure fight each other
		for (Race other : RACES) {
			if (other != race && other.arrived && other.birth.id() == race.birth.id()) {
				for (KultivatorNpc a : race.members) {
					a.memory().putString("foe", other.group);
				}
				for (KultivatorNpc b : other.members) {
					b.memory().putString("foe", race.group);
				}
			}
		}
		if (!spawned.isEmpty()) {
			KotaVoice.say(spawned.getFirst(), KotaVoice.RIVAL_CLAIM, "rogue.ambush", "kultivator.kota.saingan.suara.klaim");
			level.playSound(null, landing.x, landing.y, landing.z, SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.HOSTILE, 1.0F, 0.8F);
		}
		Msg.chat(witness, Component.translatable("kultivator.kota.saingan.datang", groupName(group)).withStyle(style -> style.withColor(ChatFormatting.GOLD)
				.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("kultivator.kota.saingan.inspired")))));
	}

	private static void dismiss(Race race) {
		for (KultivatorNpc member : race.members) {
			if (!member.isRemoved()) {
				member.vanish();
			}
		}
		race.members.clear();
	}

	/** After a rival claim the group leaves at once (a sword flight up). */
	private static void dismissLater(Race race) {
		dismiss(race);
	}

	/** The leader (when {@code leader}) and members of {@code group} at {@code at}. */
	static List<KultivatorNpc> spawnGroup(ServerLevel level, SainganData.Group group, Vec3 at, int band, int memberCount, boolean leader) {
		List<KultivatorNpc> list = new ArrayList<>();
		RandomSource random = level.getRandom();
		int total = (leader ? 1 : 0) + memberCount;
		for (int i = 0; i < total; i++) {
			boolean isLeader = leader && i == 0;
			double angle = Math.PI * 2.0D * i / Math.max(1, total);
			Vec3 near = at.add(Math.cos(angle) * (isLeader ? 0.0D : 2.0D), 0.0D, Math.sin(angle) * (isLeader ? 0.0D : 2.0D));
			if (!DuniaBudget.mayAdd(level, DuniaBudget.NPC, near)) {
				break;
			}
			KultivatorNpc npc = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
			if (npc == null) {
				break;
			}
			Vec3 spot = Targeting.safeSpot(level, npc, near);
			if (spot == null) {
				spot = near;
			}
			int look = isLeader ? group.leaderLook : group.members[(i - (leader ? 1 : 0)) % group.members.length];
			int realm = isLeader ? Math.min(Realm.TRUE_IMMORTAL.ordinal(), band) : Math.max(1, band - (random.nextInt(3) == 0 ? 1 : 0));
			Dao dao = Dao.values()[Math.floorMod(look ^ group.sect, Dao.COUNT)];
			MutableComponent plate = Component.translatable(isLeader ? "kultivator.kota.saingan.pemimpin" : "kultivator.kota.saingan.anggota",
					KotaNames.person(look, NpcRole.SAINGAN), groupName(group)).withColor(group.color);
			npc.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
			npc.setup(NpcRole.SAINGAN, isLeader ? 3 : 2, look, realm, random.nextInt(4), dao, Math.floorMod(look >>> 9, KultivatorNpc.VOICES.length),
					group.color, plate);
			npc.setGroup(group.id);
			npc.setLonely(96.0D, 900);
			npc.memory().putBoolean("leader", isLeader);
			npc.setFlag(KultivatorNpc.FLAG_LEADER, isLeader);
			if (!level.addFreshEntity(npc)) {
				break;
			}
			DuniaBudget.track(DuniaBudget.NPC, npc);
			NpcSkills.arriveFx(level, npc);
			list.add(npc);
		}
		return list;
	}

	/** Every 20 ticks per rival: walk to the treasure, flee from a lost group fight. */
	static void tickNpc(ServerLevel level, KultivatorNpc npc, long now) {
		if (!npc.memory().getStringOr("foe", "").isEmpty() && npc.getHealth() <= npc.getMaxHealth() * FLEE_SHARE
				&& npc.getLastHurtByMob() instanceof KultivatorNpc) {
			Voice.say(npc, "rogue.flee", null);
			npc.vanish();
			return;
		}
		if (npc.getTarget() == null && npc.memory().contains("birth")) {
			BlockPos birth = BlockPos.of(npc.memory().getLongOr("birth", 0L));
			if (npc.position().distanceToSqr(Vec3.atBottomCenterOf(birth)) > 2.5D * 2.5D) {
				npc.getNavigation().moveTo(birth.getX() + 0.5D, birth.getY(), birth.getZ() + 0.5D, 1.0D);
			}
		}
	}

	// ================================================================== ambush

	private static void onClaimed(ServerLevel level, TreasureBirth birth, @Nullable ServerPlayer winner, @Nullable String rivalId) {
		if (winner == null || !Kota.enabled() || !Kota.config().rivals) {
			return;
		}
		SainganData data = SainganData.get(level.getServer());
		// every group that raced for it now bears a grudge
		for (Race race : RACES) {
			if (race.birth.id() == birth.id()) {
				data.addGrudge(race.group, winner.getUUID(), 20);
			}
		}
		if (!Kota.config().rivalAmbush) {
			return;
		}
		SainganData.Group group = null;
		for (SainganData.Group each : data.groups()) {
			boolean raced = false;
			for (Race race : RACES) {
				if (race.birth.id() == birth.id() && race.group.equals(each.id)) {
					raced = true;
				}
			}
			if (each.grudge(winner.getUUID()) >= 40 || raced && "licik".equals(SainganData.STYLES[each.style])) {
				if (group == null || each.grudge(winner.getUUID()) > group.grudge(winner.getUUID())) {
					group = each;
				}
			}
		}
		if (group != null) {
			schedule(winner, group, 1200 + level.getRandom().nextInt(4800), Math.max(1, Math.min(4, birth.tier())));
		}
	}

	/** A rival ambush for {@code player} in {@code delay} ticks. */
	static void schedule(ServerPlayer player, SainganData.Group group, int delay, int tier) {
		AMBUSHES.removeIf(each -> each.player().equals(player.getUUID()));
		AMBUSHES.add(new Ambush(player.getUUID(), group.id, player.level().getGameTime() + delay, tier, false));
	}

	private static void tickAmbushes(MinecraftServer server) {
		if (AMBUSHES.isEmpty()) {
			return;
		}
		List<Ambush> next = new ArrayList<>();
		for (Ambush ambush : List.copyOf(AMBUSHES)) {
			ServerPlayer player = server.getPlayerList().getPlayer(ambush.player());
			if (player == null) {
				continue;
			}
			ServerLevel level = (ServerLevel) player.level();
			long now = level.getGameTime();
			if (now < ambush.at()) {
				next.add(ambush);
				continue;
			}
			if (player.isCreative() || player.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL) {
				continue;
			}
			boolean safe = DuniaApi.safeGround(level, player.blockPosition()) || Towns.inTown(level, player.blockPosition())
					|| !level.canSeeSky(player.blockPosition().above()) || DuniaApi.souls().isSoul(player) || inSectTerritory(level, player)
					|| !player.onGround() || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying
					|| DuniaApi.routes().aboard(player)
					// never in the middle of real danger or a ritual (the shared Sibuk door; this band itself left out)
					|| Sibuk.whyExcept(player, "saingan") != null;
			if (safe) {
				// wait outside the walls, or until the cultivator is free again (asked again in 10 s, given up after 5 min)
				if (ambush.waits() + 1 >= MAX_WAITS) {
					SainganData.get(server).addGrudge(ambush.group(), player.getUUID(), -5);
					continue;
				}
				next.add(new Ambush(ambush.player(), ambush.group(), now + Sibuk.RETRY_TICKS, ambush.tier(), false, ambush.waits() + 1));
				continue;
			}
			KotaData data = KotaData.get(server);
			long day = Kota.day(server);
			if (data.dailyCount(player.getUUID(), day, "ambush") >= Kota.config().ambushPerDay) {
				continue;
			}
			// Shared enemy pressure: the daily cap of ambushes, rival bands and raids together.
			if (!ambush.warned() && Sibuk.enemyRefusal(player) != null) {
				continue;
			}
			if (!ambush.warned()) {
				Fx.send(level, player.position().add(0.0D, 1.0D, 0.0D), FxType.MUSUH, MusuhFxIds.AMBUSH_ALERT, colorOf(ambush.group()), 3.0F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.8F);
				Msg.actionBar(player, Component.translatable("kultivator.kota.saingan.niat_membunuh").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
				next.add(new Ambush(ambush.player(), ambush.group(), now + WARNING_TICKS, ambush.tier(), true, ambush.waits()));
				continue;
			}
			SainganData.Group group = SainganData.get(server).byId(ambush.group());
			if (group != null && strike(player, group, ambush.tier())) {
				data.addDaily(player.getUUID(), day, "ambush", 1);
				Sibuk.enemyTaken(player, "saingan");
			}
		}
		AMBUSHES.clear();
		AMBUSHES.addAll(next);
	}

	/** The band strikes now: the leader and up to three members, hostile to {@code player} only. */
	static boolean strike(ServerPlayer player, SainganData.Group group, int tier) {
		ServerLevel level = (ServerLevel) player.level();
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		Vec3 at = player.position().add(Math.cos(angle) * 8.0D, 0.0D, Math.sin(angle) * 8.0D);
		int band = Math.max(1, Math.min(band(level.getServer()), Cultivators.get(player).realm().ordinal() + 1));
		List<KultivatorNpc> members = spawnGroup(level, group, at, band, Math.min(3, Math.max(1, tier)), true);
		if (members.isEmpty()) {
			return false;
		}
		for (KultivatorNpc member : members) {
			member.markHostile(player);
			member.memory().putBoolean("ambush", true);
		}
		LIVE_AMBUSH.put(player.getUUID(), new ArrayList<>(members));
		KotaVoice.say(members.getFirst(), KotaVoice.RIVAL_CLAIM, "rogue.ambush", "kultivator.kota.saingan.suara.klaim");
		Msg.chat(player, Component.translatable("kultivator.kota.saingan.sergap", groupName(group)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		return true;
	}

	// ================================================================== harm, death, rewards

	static void attacked(ServerPlayer player, KultivatorNpc npc) {
		if (!npc.group().isEmpty() && Kota.server() != null) {
			SainganData.get(Kota.server()).addGrudge(npc.group(), player.getUUID(), 1);
		}
		for (KultivatorNpc other : player.level().getEntitiesOfClass(KultivatorNpc.class, npc.getBoundingBox().inflate(16.0D),
				each -> each.group().equals(npc.group()))) {
			other.markHostile(player);
		}
	}

	static void slain(ServerLevel level, KultivatorNpc npc, @Nullable ServerPlayer killer) {
		if (killer == null || npc.group().isEmpty()) {
			return;
		}
		SainganData data = SainganData.get(level.getServer());
		SainganData.Group group = data.byId(npc.group());
		if (group == null) {
			return;
		}
		if (npc.memory().getBooleanOr("leader", false)) {
			ItemStack pouch = new ItemStack(KotaItems.KANTONG_SAINGAN);
			net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, npc.getX(), npc.getY() + 0.3D, npc.getZ(), pouch);
			item.setDefaultPickUpDelay();
			item.addTag(dev.kultivator.util.NoGrief.TAG);
			level.addFreshEntity(item);
			data.setGrudge(group.id, killer.getUUID(), 20);
			data.setBeaten(group.id, killer.getUUID());
			data.loss(group.id);
			Advancements.grant(killer, "dunia/rival_beaten");
			Msg.chat(killer, Component.translatable("kultivator.kota.saingan.kalah", groupName(group)).withStyle(ChatFormatting.GOLD));
			Voice.sayHere(npc, "rogue.defeat", null);
			boolean all = true;
			for (SainganData.Group each : data.groups()) {
				if (!each.beatenBy(killer.getUUID())) {
					all = false;
				}
			}
			if (all) {
				Advancements.grant(killer, "dunia/rival_all");
				Msg.chat(killer, Component.translatable("kultivator.kota.saingan.semua").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			}
		} else {
			data.addGrudge(group.id, killer.getUUID(), 10);
		}
	}

	/** Kantong Saingan: two loot pouch rolls of the group band and a Mid or High stone. */
	static void openPouch(ServerPlayer player, ItemStack stack) {
		int band = Kota.server() == null ? 2 : band(Kota.server());
		int tier = Mth.clamp(1 + band / 2, 1, 4);
		for (int i = 0; i < 2; i++) {
			give(player, LootPouches.pouch(tier, band, player.getRandom().nextInt(8)));
		}
		give(player, SpiritStones.stack(player.getRandom().nextInt(3) == 0 ? SpiritStones.HIGH : SpiritStones.MID, 1));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			Kota.drop(player, stack);
		}
	}

	/** A Licik wanderer fled: a cunning group remembers the player (+15). */
	static void wandererGrudge(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		for (SainganData.Group group : SainganData.get(server).groups()) {
			if ("licik".equals(SainganData.STYLES[group.style])) {
				SainganData.get(server).addGrudge(group.id, player.getUUID(), 15);
				return;
			}
		}
	}

	// ================================================================== helpers

	/** True inside any sect's territory (the same rule as the road ambushes of {@code musuh}). */
	private static boolean inSectTerritory(ServerLevel level, ServerPlayer player) {
		for (UUID owner : HallData.get(level.getServer()).owners()) {
			if (Halls.inTerritory(owner, level, player.position())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The DUNIA hub card "Saingan" (integration audit S21, drawn by module M5): every group with its name, colour, style,
	 * the viewer's grudge (0-100), whether the viewer beat its leader, and its home region once the viewer crossed it
	 * (a grudge or a beaten leader). Sent as {@link KotaNet#SCREEN_SAINGAN} / {@link KotaNet#TAB_SAINGAN}.
	 */
	static CompoundTag view(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		MinecraftServer server = player.level().getServer();
		boolean on = Kota.enabled() && Kota.config().rivals;
		if (on) {
			for (SainganData.Group group : SainganData.get(server).groups()) {
				CompoundTag g = new CompoundTag();
				g.putString("id", group.id);
				group.putName(g);
				g.putString("sekte", group.sectId());
				g.putString("style", "kultivator.kota.gaya." + SainganData.STYLES[group.style]);
				g.putInt("color", group.color);
				int grudge = group.grudge(player.getUUID());
				boolean beaten = group.beatenBy(player.getUUID());
				g.putInt("grudge", grudge);
				g.putBoolean("beaten", beaten);
				g.putInt("wins", group.wins);
				g.putInt("losses", group.losses);
				if (grudge > 0 || beaten) {
					g.putBoolean("known", true);
					g.putInt("x", group.region.getX());
					g.putInt("z", group.region.getZ());
				}
				list.add(g);
			}
			tag.putInt("band", band(server));
		}
		tag.put("groups", list);
		tag.putBoolean("on", on);
		long day = Kota.day(server);
		tag.putInt("ambush_today", KotaData.get(server).dailyCount(player.getUUID(), day, "ambush"));
		tag.putInt("ambush_cap", Kota.config().ambushPerDay);
		tag.putInt("pressure_today", Sibuk.enemyToday(player));
		tag.putInt("pressure_cap", Sibuk.DAILY_ENEMY_CAP);
		tag.putBoolean("pending", pendingFor(player.getUUID()));
		return tag;
	}

	private static boolean pendingFor(UUID player) {
		for (Ambush ambush : AMBUSHES) {
			if (ambush.player().equals(player)) {
				return true;
			}
		}
		return false;
	}

	static @Nullable SainganData.Group nearest(MinecraftServer server, BlockPos pos) {
		SainganData.Group best = null;
		double bestSq = Double.MAX_VALUE;
		for (SainganData.Group group : SainganData.get(server).groups()) {
			double d = group.region.distSqr(pos);
			if (d < bestSq) {
				bestSq = d;
				best = group;
			}
		}
		return best;
	}

	/** The group with the biggest grudge against the player (the nearest when nobody holds one). */
	static @Nullable SainganData.Group worstFor(ServerPlayer player) {
		SainganData data = SainganData.get(player.level().getServer());
		SainganData.Group best = null;
		for (SainganData.Group group : data.groups()) {
			if (best == null || group.grudge(player.getUUID()) > best.grudge(player.getUUID())) {
				best = group;
			}
		}
		if (best != null && best.grudge(player.getUUID()) == 0) {
			return nearest(player.level().getServer(), player.blockPosition());
		}
		return best;
	}

	static Component name(MinecraftServer server, String group) {
		SainganData.Group g = SainganData.get(server).byId(group);
		return g == null ? Component.literal(group) : groupName(g);
	}

	private static @Nullable ServerPlayer nearestPlayer(ServerLevel level, Vec3 at, double range) {
		ServerPlayer best = null;
		double bestSq = range * range;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			double d = player.position().distanceToSqr(at);
			if (d <= bestSq) {
				bestSq = d;
				best = player;
			}
		}
		return best;
	}

	/** The races running now (test command). */
	static List<Race> races() {
		return RACES;
	}

	/** Pending ambushes (test command). */
	static int pendingAmbushes() {
		return AMBUSHES.size();
	}

	/** {@code uji saingan perlombaan}: a test birth 60 blocks ahead through L's service, then the race (or a staged race). */
	static boolean testRace(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		BlockPos ahead = BlockPos.containing(player.getX() + look.x * 60.0D, player.getY(), player.getZ() + look.z * 60.0D);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ahead.getX(), ahead.getZ());
		BlockPos at = new BlockPos(ahead.getX(), y, ahead.getZ());
		CultivationData data = Cultivators.get(player);
		int tier = Mth.clamp(1 + data.realm().ordinal() / 2, 1, 4);
		TreasureBirth birth = DuniaApi.treasures().spawn(level, at, tier, "herba");
		if (birth == null) {
			// L is off: a staged race toward the spot (no treasure to claim)
			birth = new TreasureBirth(DuniaSites.idOf(level.dimension(), at, "langit/harta"), level.dimension(), at, tier, "herba",
					level.getGameTime(), level.getGameTime() + 6000L);
			Msg.chat(player, Component.translatable("kultivator.kota.uji.saingan.tanpa_l").withStyle(ChatFormatting.GRAY));
			SainganData.Group group = nearest(level.getServer(), at);
			if (group == null) {
				return false;
			}
			Race race = new Race(birth, group.id, level.getGameTime());
			arrive(level, race, player);
			for (KultivatorNpc member : race.members) {
				member.setLonely(96.0D, 1200);
			}
			return true;
		}
		// L fires TREASURE_BORN (the race starts there); when it did not, start it here; then make the ETAs short
		boolean racing = false;
		for (Race race : RACES) {
			if (race.birth.id() == birth.id()) {
				racing = true;
			}
		}
		if (!racing) {
			SERVICE.raceTo(level, birth);
		}
		long now = level.getGameTime();
		List<Race> fast = new ArrayList<>();
		for (Iterator<Race> it = RACES.iterator(); it.hasNext(); ) {
			Race race = it.next();
			if (race.birth.id() == birth.id()) {
				it.remove();
				fast.add(new Race(birth, race.group, now + 100L + fast.size() * 60L));
			}
		}
		RACES.addAll(fast);
		return true;
	}

	private Saingan() {
	}
}
