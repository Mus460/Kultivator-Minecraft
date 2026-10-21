package dev.kultivator.dunia.kota;

import dev.kultivator.alam.Alam;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.musuh.LootPouches;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kultivator Pengembara (DESIGN 34.5.2): wandering cultivators. For every eligible player (Qi Refining+, Overworld or
 * Alam Abadi, under the open sky, not meditating, duelling or in a secret realm, no enemy near) a roll every 6 000 ticks,
 * chance {@code (0.10 + 0.01 × realm) × kota.wandererChance} (at most 0.20 at scale 1), one in-game day of cooldown after
 * a meeting; plus 0-2 wanderers seated in each loaded teahouse. A wanderer walks up from 16-24 blocks, greets, and a
 * "[Bicara]" chip opens the meeting: Bertukar Pengetahuan (an offering, then Diskusi Dao, three rounds of Gerak / Diam /
 * Selaras with a hint from the wanderer's Dao), Tanding (a duel that stops at 10 % health), Tantang (to the end) and Ajak
 * Berkelana (Ramah, Golden Core+, one in-game day). Seeded name, Dao, the player's realm with stage ±1 and a temperament
 * (Ramah 50 %, Angkuh 30 %, Licik 20 %). It leaves after 5 idle minutes or 64 blocks away.
 */
public final class Pengembara {

	public static final int ROLL_TICKS = 6000;
	public static final int IDLE_TICKS = 6000;
	public static final int DAY = 24000;
	public static final int RAMAH = 0;
	public static final int ANGKUH = 1;
	public static final int LICIK = 2;
	public static final int GERAK = 0;
	public static final int DIAM = 1;
	public static final int SELARAS = 2;
	public static final int ROUNDS = 3;
	public static final String[] OFFERS = {"giok", "herba", "pil", "pengalaman"};
	public static final double EXPERIENCE_SHARE = 0.05D;
	public static final double SPAR_XP = 0.02D;
	public static final int DUEL_TICKS = 1200;

	/** Seated wanderers by teahouse seat anchor. */
	private static final Map<UUID, KultivatorNpc> SEATED = new HashMap<>();
	/** The one companion of each player. */
	private static final Map<UUID, KultivatorNpc> COMPANIONS = new HashMap<>();
	/** The offering of a running discussion, by wanderer (given back on a lost discussion). */
	private static final Map<UUID, ItemStack> OFFERED = new HashMap<>();

	static void init() {
	}

	static void clear() {
		SEATED.clear();
		COMPANIONS.clear();
		OFFERED.clear();
	}

	static void onLeave(ServerPlayer player) {
		KultivatorNpc companion = COMPANIONS.remove(player.getUUID());
		if (companion != null && !companion.isRemoved()) {
			companion.vanish();
		}
	}

	// ================================================================== the roll

	static void tick(MinecraftServer server, long tick) {
		if (tick % 200L != 0L || !Kota.enabled() || !Kota.config().wanderers || DuniaBudget.overloaded(server)) {
			return;
		}
		int slots = ROLL_TICKS / 200;
		int turn = (int) ((tick / 200L) % slots);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Math.floorMod(player.getUUID().hashCode(), slots) != turn) {
				continue;
			}
			if (!eligible(player)) {
				continue;
			}
			KotaData data = KotaData.get(server);
			if (server.overworld().getGameTime() < data.getLong(player, "wander_cd", 0L)) {
				continue;
			}
			int realm = Cultivators.get(player).realm().ordinal();
			double chance = Math.min(0.20D, 0.10D + 0.01D * realm) * Kota.config().wandererChance;
			if (player.getRandom().nextDouble() >= chance) {
				continue;
			}
			KultivatorNpc npc = spawnFor(player, null, -1, -1, -1);
			if (npc != null) {
				data.putLong(player, "wander_cd", server.overworld().getGameTime() + DAY);
			}
		}
	}

	static boolean eligible(ServerPlayer player) {
		if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
			return false;
		}
		Level level = player.level();
		boolean world = level.dimension() == Level.OVERWORLD || Kota.inAlamAbadi(player);
		if (!world || Alam.inRun(player) || DuelRules.inDuel(player) || Meditation.isMeditating(player)) {
			return false;
		}
		if (Cultivators.get(player).realm().ordinal() < Realm.QI_REFINING.ordinal() || !level.canSeeSky(player.blockPosition().above())) {
			return false;
		}
		if (DuniaApi.souls().isSoul(player) || DuniaApi.routes().aboard(player)) {
			return false;
		}
		return level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(16.0D),
				mob -> mob instanceof net.minecraft.world.entity.monster.Enemy || mob.getTarget() == player).isEmpty();
	}

	/**
	 * A wanderer for {@code player} 16-24 blocks away (or at {@code seat}). {@code dao} / {@code realm} / {@code temper}
	 * -1 = seeded. Null when the budget refuses or there is no spot.
	 */
	static @Nullable KultivatorNpc spawnFor(ServerPlayer player, @Nullable Vec3 seat, int daoIndex, int realmIndex, int temper) {
		ServerLevel level = (ServerLevel) player.level();
		RandomSource random = level.getRandom();
		Vec3 at = seat;
		if (at == null) {
			for (int attempt = 0; attempt < 6 && at == null; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double distance = 16.0D + random.nextDouble() * 8.0D;
				Vec3 near = player.position().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
				if (level.isPositionEntityTicking(BlockPos.containing(near))) {
					at = near;
				}
			}
		}
		if (at == null || !DuniaBudget.mayAdd(level, DuniaBudget.NPC, at)) {
			return null;
		}
		KultivatorNpc npc = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (npc == null) {
			return null;
		}
		Vec3 spot = seat != null ? seat : Targeting.safeSpot(level, npc, at);
		if (spot == null) {
			return null;
		}
		CultivationData data = Cultivators.get(player);
		int look = random.nextInt();
		Dao dao = daoIndex >= 0 ? Dao.byOrdinal(daoIndex) : Dao.values()[random.nextInt(Dao.COUNT)];
		int realm = realmIndex >= 0 ? realmIndex : data.realm().ordinal();
		int stage = Mth.clamp(data.rank().stage() + random.nextInt(3) - 1, 0, 3);
		int temperament = temper >= 0 ? temper : random.nextInt(10) < 5 ? RAMAH : random.nextInt(5) < 3 ? ANGKUH : LICIK;
		MutableComponent plate = Component.translatable("kultivator.kota.pengembara.papan", KotaNames.person(look),
				Component.translatable("kultivator.kota.watak." + temperament)).withColor(dao == null ? 0xC0D0E0 : dao.color());
		npc.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
		npc.setup(NpcRole.PENGEMBARA, look, realm, stage, dao, Math.floorMod(look >>> 5, KultivatorNpc.VOICES.length), 0, plate);
		npc.setTemperament(temperament);
		npc.setLonely(64.0D, 200);
		npc.memory().putString("for", player.getUUID().toString());
		npc.memory().putLong("idle", level.getGameTime());
		npc.setHome(BlockPos.containing(spot));
		if (seat != null) {
			npc.setFlag(KultivatorNpc.FLAG_SITTING, true);
			npc.memory().putBoolean("hold", true);
			npc.memory().putBoolean("greeted", true);
		}
		if (!level.addFreshEntity(npc)) {
			return null;
		}
		DuniaBudget.track(DuniaBudget.NPC, npc);
		NpcSkills.arriveFx(level, npc);
		return npc;
	}

	/** A teahouse seat: once per in-game day, half the time, a wanderer sits there. */
	static void seat(ServerLevel level, DuniaAnchorEntity anchor, long town, ServerPlayer nearest) {
		if (!Kota.config().wanderers) {
			return;
		}
		KultivatorNpc seated = SEATED.get(anchor.getUUID());
		if (seated != null && !seated.isRemoved() && seated.isAlive()) {
			return;
		}
		long day = Kota.day(level.getServer());
		if (anchor.state().getLongOr("seat_day", -1L) == day) {
			return;
		}
		anchor.state().putLong("seat_day", day);
		if (level.getRandom().nextBoolean() || Cultivators.get(nearest).realm().ordinal() < Realm.QI_REFINING.ordinal()) {
			return;
		}
		KultivatorNpc npc = spawnFor(nearest, Vec3.atBottomCenterOf(anchor.blockPosition()), -1, -1, -1);
		if (npc != null) {
			npc.setTown(town);
			npc.setYRot(anchor.getYRot());
			npc.setYHeadRot(anchor.getYRot());
			npc.setLonely(64.0D, 600);
			npc.memory().putLong("idle", level.getGameTime() + IDLE_TICKS * 3L);
			SEATED.put(anchor.getUUID(), npc);
		}
	}

	/** Seated wanderers within 16 blocks of the tea master. */
	static int seatedNear(KultivatorNpc master) {
		int n = 0;
		for (KultivatorNpc npc : SEATED.values()) {
			if (!npc.isRemoved() && npc.isAlive() && npc.level() == master.level() && npc.distanceToSqr(master) <= 16.0D * 16.0D) {
				n++;
			}
		}
		return n;
	}

	// ================================================================== the wanderer's own pass

	static void tickNpc(ServerLevel level, KultivatorNpc npc, long now) {
		if (npc.companionOf() != null) {
			return;
		}
		if (now - npc.memory().getLongOr("idle", now) > IDLE_TICKS && !DuelRules.inDuel(npc) && npc.getTarget() == null) {
			Voice.say(npc, npc.voiceKey("farewell"), null);
			npc.vanish();
			return;
		}
		ServerPlayer player = target(level, npc);
		if (player == null || npc.memory().getBooleanOr("greeted", false) || npc.getTarget() != null) {
			return;
		}
		double distance = npc.distanceTo(player);
		if (distance > 5.0D) {
			npc.getNavigation().moveTo(player, 0.8D);
			return;
		}
		npc.memory().putBoolean("greeted", true);
		npc.getNavigation().stop();
		npc.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Voice.say(npc, npc.voiceKey("greet"), "kultivator.kota.pengembara.suara.dao");
		greetChip(player, npc);
	}

	private static void greetChip(ServerPlayer player, KultivatorNpc npc) {
		MutableComponent line = Component.empty().append(npc.plate()).append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
				.append(Component.translatable("kultivator.kota.pengembara.sapa." + npc.temperament() + "." + npc.getRandom().nextInt(3))
						.withStyle(ChatFormatting.WHITE));
		MutableComponent chip = Component.translatable("kultivator.kota.pengembara.bicara").withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withBold(true).withClickEvent(new ClickEvent.RunCommand("/kultivasi bicara"))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("kultivator.kota.pengembara.bicara_tip").append("\n\n")
						.append(Component.translatable("kultivator.kota.pengembara.source").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)))));
		Msg.chat(player, line.append(Component.literal(" ")).append(chip));
	}

	private static @Nullable ServerPlayer target(ServerLevel level, KultivatorNpc npc) {
		try {
			UUID id = UUID.fromString(npc.memory().getStringOr("for", ""));
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			return player != null && player.level() == level ? player : null;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/** {@code /kultivasi bicara}: opens the meeting with the nearest wanderer within 8 blocks. */
	static int talkNearest(ServerPlayer player) {
		KultivatorNpc best = null;
		double bestSq = 8.0D * 8.0D;
		for (KultivatorNpc npc : player.level().getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(8.0D),
				each -> each.role() == NpcRole.PENGEMBARA && each.isAlive())) {
			double d = npc.distanceToSqr(player);
			if (d < bestSq) {
				bestSq = d;
				best = npc;
			}
		}
		if (best == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.pengembara.tidak_ada").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		return talk(player, best) ? 1 : 0;
	}

	static boolean talk(ServerPlayer player, KultivatorNpc npc) {
		if (npc.isHostileTo(player.getUUID()) || DuelRules.inDuel(npc)) {
			return false;
		}
		npc.memory().putLong("idle", npc.level().getGameTime());
		npc.memory().putString("for", player.getUUID().toString());
		npc.memory().putBoolean("greeted", true);
		npc.getNavigation().stop();
		Advancements.grant(player, "dunia/wanderer_talk");
		Kota.open(player, npc, KotaNet.TAB_PERTEMUAN, true);
		return true;
	}

	// ================================================================== the meeting

	static boolean action(ServerPlayer player, KultivatorNpc npc, String verb, String[] parts) {
		if (npc.role() != NpcRole.PENGEMBARA || npc.isHostileTo(player.getUUID())) {
			return false;
		}
		npc.memory().putLong("idle", npc.level().getGameTime());
		switch (verb) {
			case "tukar" -> offer(player, npc, Kota.arg(parts, 1, -1));
			case "diskusi" -> round(player, npc, Kota.arg(parts, 1, -1));
			case "tanding" -> spar(player, npc);
			case "tantang" -> challenge(player, npc);
			case "ajak" -> invite(player, npc);
			case "pamit" -> {
				Voice.say(npc, npc.voiceKey("farewell"), null);
				npc.vanish();
				return false;
			}
			default -> {
				return false;
			}
		}
		return !npc.isRemoved();
	}

	/** Step 1 of the exchange: the offering (taken now; given back on a lost discussion, except experience). */
	private static void offer(ServerPlayer player, KultivatorNpc npc, int kind) {
		if (npc.memory().contains("offer") || kind < 0 || kind >= OFFERS.length) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		long day = Kota.day(server);
		KotaData data = KotaData.get(server);
		if (data.dailyCount(player.getUUID(), day, "diskusi") >= Kota.config().discussionsPerDay) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.diskusi.batas", Kota.config().discussionsPerDay).withStyle(ChatFormatting.RED));
			return;
		}
		ItemStack taken = ItemStack.EMPTY;
		if (kind == 3) {
			CultivationData cultivation = Cultivators.get(player);
			double cost = cultivation.rank().xpRequired() * EXPERIENCE_SHARE;
			if (cultivation.xp() < cost) {
				Msg.actionBar(player, Component.translatable("kultivator.kota.diskusi.kurang_pengalaman").withStyle(ChatFormatting.RED));
				return;
			}
			cultivation.setXp(cultivation.xp() - cost);
			Cultivators.dirty(player);
		} else {
			taken = takeOffering(player, kind);
			if (taken.isEmpty()) {
				Msg.actionBar(player, Component.translatable("kultivator.kota.diskusi.tidak_punya." + OFFERS[kind]).withStyle(ChatFormatting.RED));
				return;
			}
		}
		data.addDaily(player.getUUID(), day, "diskusi", 1);
		npc.memory().putInt("offer", kind);
		if (!taken.isEmpty()) {
			OFFERED.put(npc.getUUID(), taken);
		}
		npc.memory().putInt("round", 0);
		npc.memory().putInt("wins", 0);
		nextLean(npc);
		Voice.say(npc, "elder.advice", "kultivator.kota.pengembara.suara.dao");
	}

	private static ItemStack takeOffering(ServerPlayer player, int kind) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			boolean match = switch (kind) {
				case 0 -> "jade_slip".equals(id);
				case 1 -> SpiritHerbs.isHerb(stack);
				default -> id.endsWith("_pill");
			};
			if (match) {
				ItemStack one = stack.copyWithCount(1);
				stack.shrink(1);
				inventory.setChanged();
				return one;
			}
		}
		return ItemStack.EMPTY;
	}

	/** The wanderer's lean for the next round (the hint tells it; Licik ones lie more often). */
	private static void nextLean(KultivatorNpc npc) {
		RandomSource random = npc.getRandom();
		int lean = random.nextInt(3);
		npc.memory().putInt("lean", lean);
		int hint = random.nextInt(2);
		npc.memory().putInt("hint", hint);
		boolean honest = random.nextFloat() < (npc.temperament() == LICIK ? 0.5F : npc.temperament() == ANGKUH ? 0.65F : 0.8F);
		npc.memory().putInt("stance", honest ? lean : (lean + 1 + random.nextInt(2)) % 3);
	}

	/** True when {@code a} beats {@code b}: Gerak > Diam > Selaras > Gerak. */
	static boolean beats(int a, int b) {
		return a == GERAK && b == DIAM || a == DIAM && b == SELARAS || a == SELARAS && b == GERAK;
	}

	/** A round of Diskusi Dao. */
	private static void round(ServerPlayer player, KultivatorNpc npc, int stance) {
		if (!npc.memory().contains("offer") || stance < 0 || stance > 2) {
			return;
		}
		int round = npc.memory().getIntOr("round", 0);
		if (round >= ROUNDS) {
			return;
		}
		int theirs = npc.memory().getIntOr("stance", 0);
		int wins = npc.memory().getIntOr("wins", 0);
		int result = beats(stance, theirs) ? 1 : stance == theirs ? 0 : -1;
		if (result > 0) {
			wins++;
			if (player.level() instanceof ServerLevel level) {
				for (ServerPlayer viewer : level.players()) {
					if (viewer.distanceToSqr(npc) <= 32.0D * 32.0D) {
						KotaNet.fx(viewer, KotaNet.FX_SWIRL, npc.getX(), npc.getY() + 1.2D, npc.getZ(), NpcSkills.color(npc), 0, 30, null);
					}
				}
				level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.3F);
			}
		}
		npc.memory().putInt("wins", wins);
		npc.memory().putInt("last", result);
		npc.memory().putInt("last_theirs", theirs);
		npc.memory().putInt("round", round + 1);
		if (round + 1 < ROUNDS) {
			nextLean(npc);
			return;
		}
		finishDiscussion(player, npc, wins);
	}

	private static void finishDiscussion(ServerPlayer player, KultivatorNpc npc, int wins) {
		ItemStack offered = OFFERED.getOrDefault(npc.getUUID(), ItemStack.EMPTY);
		OFFERED.remove(npc.getUUID());
		npc.memory().putBoolean("discussed", true);
		Dao dao = npc.dao();
		CultivationData data = Cultivators.get(player);
		if (wins >= 2) {
			Voice.say(npc, npc.voiceKey("cheer"), null);
			boolean own = dao != null && (data.dao() == null || data.dao() == dao);
			if (own) {
				double amount = 2.0D + npc.getRandom().nextInt(5);
				double gained = Comprehension.grant(player, dao, amount);
				npc.memory().putString("reward", "paham");
				npc.memory().putDouble("reward_n", gained);
			} else {
				ItemStack note = new ItemStack(KotaItems.CATATAN_PENGEMBARA);
				if (!player.getInventory().add(note)) {
					Kota.drop(player, note);
				}
				npc.memory().putString("reward", "catatan");
			}
		} else {
			Voice.say(npc, "elder.advice", null);
			npc.memory().putString("reward", "lore");
			npc.memory().putInt("lore", npc.getRandom().nextInt(6));
			if (!offered.isEmpty() && !player.getInventory().add(offered)) {
				Kota.drop(player, offered);
			}
		}
	}

	private static void spar(ServerPlayer player, KultivatorNpc npc) {
		if (DuelRules.inDuel(player) || DuelRules.inDuel(npc) || npc.memory().getBooleanOr("sparred", false)) {
			return;
		}
		boolean started = DuelRules.begin(player, npc, DUEL_TICKS, (winner, loser, reason) -> {
			if (!(npc.level() instanceof ServerLevel level)) {
				return;
			}
			npc.setTarget(null);
			npc.memory().putBoolean("sparred", true);
			npc.memory().putLong("idle", level.getGameTime());
			ServerPlayer online = level.getServer().getPlayerList().getPlayer(player.getUUID());
			if (online == null) {
				return;
			}
			if (winner == online) {
				CultivationData data = Cultivators.get(online);
				double xp = data.rank().xpRequired() * SPAR_XP;
				Progression.grantXp(online, xp, XpSource.COMBAT);
				ItemStack badge = new ItemStack(KotaItems.LENCANA_PENGEMBARA);
				if (!online.getInventory().add(badge)) {
					Kota.drop(online, badge);
				}
				long town = Towns.near(level, online.blockPosition(), 3000);
				if (town != 0L) {
					KotaData.get(level.getServer()).addReputation(town, online.getUUID(), 3);
				}
				KotaData kd = KotaData.get(level.getServer());
				kd.putInt(online, "lencana", kd.getInt(online, "lencana", 0) + 1);
				Voice.say(npc, npc.voiceKey("tired"), null);
				Msg.chat(online, Component.translatable("kultivator.kota.tanding.menang", npc.plate()).withStyle(ChatFormatting.GREEN));
			} else if (reason != DuelRules.Reason.CANCELLED) {
				Voice.say(npc, npc.voiceKey("victory"), null);
				Msg.chat(online, Component.translatable("kultivator.kota.tanding.kalah", npc.plate()).withStyle(ChatFormatting.GRAY));
			}
		});
		if (started) {
			npc.setTarget(player);
			npc.memory().putBoolean("hold", false);
			npc.setFlag(KultivatorNpc.FLAG_SITTING, false);
			Voice.say(npc, npc.voiceKey("attack"), null);
			Msg.chat(player, Component.translatable("kultivator.kota.tanding.mulai").withStyle(ChatFormatting.GOLD));
		}
	}

	private static void challenge(ServerPlayer player, KultivatorNpc npc) {
		npc.setFlag(KultivatorNpc.FLAG_SITTING, false);
		npc.memory().putBoolean("hold", false);
		npc.memory().putBoolean("challenged", true);
		npc.markHostile(player);
		Voice.say(npc, npc.temperament() == LICIK ? "rogue.ambush" : npc.voiceKey("attack"), null);
		Msg.chat(player, Component.translatable("kultivator.kota.tantang.mulai", npc.plate()).withStyle(ChatFormatting.RED));
	}

	private static void invite(ServerPlayer player, KultivatorNpc npc) {
		if (!Kota.config().companion || npc.temperament() != RAMAH || Cultivators.get(player).realm().ordinal() < Realm.GOLDEN_CORE.ordinal()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.ajak.tolak").withStyle(ChatFormatting.GRAY));
			return;
		}
		KultivatorNpc old = COMPANIONS.get(player.getUUID());
		if (old != null && !old.isRemoved() && old != npc) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.ajak.sudah").withStyle(ChatFormatting.RED));
			return;
		}
		npc.setFlag(KultivatorNpc.FLAG_SITTING, false);
		npc.memory().putBoolean("hold", false);
		npc.setCompanion(player.getUUID(), npc.level().getGameTime() + DAY);
		npc.setLonely(96.0D, 1200);
		COMPANIONS.put(player.getUUID(), npc);
		Voice.say(npc, npc.voiceKey("loyal"), null);
		Msg.chat(player, Component.translatable("kultivator.kota.ajak.ikut", npc.plate()).withStyle(ChatFormatting.GREEN));
	}

	/** The companion's day is over (or the escort merchant's): it leaves with a small gift. */
	static void companionEnd(KultivatorNpc npc) {
		UUID owner = npc.companionOf();
		npc.setCompanion(null, 0L);
		if (!(npc.level() instanceof ServerLevel level) || owner == null) {
			npc.vanish();
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (npc.memory().getBooleanOr("escort", false)) {
			if (player != null) {
				PapanMisi.fail(player, Component.translatable("kultivator.kota.misi.kawal_waktu"));
			}
			npc.vanish();
			return;
		}
		COMPANIONS.remove(owner, npc);
		if (player != null) {
			ItemStack gift = npc.getRandom().nextBoolean() ? new ItemStack(KotaItems.CATATAN_PENGEMBARA)
					: SpiritHerbs.randomFor(null, npc.getRandom(), 100, 300);
			if (!player.getInventory().add(gift)) {
				Kota.drop(player, gift);
			}
			Msg.chat(player, Component.translatable("kultivator.kota.ajak.pergi", npc.plate()).withStyle(ChatFormatting.GOLD));
		}
		Voice.say(npc, npc.voiceKey("farewell"), null);
		npc.vanish();
	}

	/** A player hit a wanderer outside a duel: it fights back (the same as a challenge). */
	static void attacked(ServerPlayer player, KultivatorNpc npc) {
		if (!npc.isHostileTo(player.getUUID())) {
			challenge(player, npc);
		}
		// Licik ones flee at 30 % and may return with a rival group
		if (npc.temperament() == LICIK && npc.getHealth() <= npc.getMaxHealth() * 0.3F && !npc.memory().getBooleanOr("fled", false)) {
			npc.memory().putBoolean("fled", true);
			Voice.say(npc, "rogue.flee", null);
			Saingan.wandererGrudge(player);
			npc.vanish();
		}
	}

	/** A wanderer died: its pouch; −10 Nama Baik when a townsperson within 32 blocks saw it. */
	static void slain(ServerLevel level, KultivatorNpc npc, @Nullable ServerPlayer killer) {
		ItemStack pouch = LootPouches.pouch(2, npc.realm(), npc.look());
		ItemEntity item = new ItemEntity(level, npc.getX(), npc.getY() + 0.3D, npc.getZ(), pouch);
		item.setDefaultPickUpDelay();
		item.addTag(NoGrief.TAG);
		level.addFreshEntity(item);
		if (killer == null) {
			return;
		}
		List<KultivatorNpc> witnesses = level.getEntitiesOfClass(KultivatorNpc.class, new AABB(npc.blockPosition()).inflate(32.0D),
				each -> each != npc && each.role().townsfolk() && each.town() != 0L);
		if (!witnesses.isEmpty()) {
			long town = witnesses.getFirst().town();
			int value = KotaData.get(level.getServer()).addReputation(town, killer.getUUID(), -10);
			Msg.chat(killer, Component.translatable("kultivator.kota.nama_baik.saksi", 10, value).withStyle(ChatFormatting.RED));
		}
	}


	/** {@code uji pengembara diskusi menang|kalah}: ends the discussion with that result (an offering is not needed). */
	static void forceDiscussion(ServerPlayer player, KultivatorNpc npc, boolean win) {
		if (!npc.memory().contains("offer")) {
			npc.memory().putInt("offer", 3);
		}
		npc.memory().putInt("round", ROUNDS);
		npc.memory().putInt("wins", win ? 3 : 0);
		finishDiscussion(player, npc, win ? 3 : 0);
		Kota.open(player, npc, KotaNet.TAB_PERTEMUAN, true);
	}

	// ================================================================== view

	static CompoundTag view(ServerPlayer player, KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		tag.store("name", ComponentSerialization.CODEC, KotaNames.person(npc.look()));
		tag.putInt("dao", npc.dao() == null ? -1 : npc.dao().ordinal());
		tag.putInt("realm", npc.realm());
		tag.putInt("stage", npc.stage());
		tag.putInt("temper", npc.temperament());
		CultivationData data = Cultivators.get(player);
		MinecraftServer server = player.level().getServer();
		long day = Kota.day(server);
		tag.putInt("disc_left", Math.max(0, Kota.config().discussionsPerDay - KotaData.get(server).dailyCount(player.getUUID(), day, "diskusi")));
		tag.putBoolean("discussing", npc.memory().contains("offer") && !npc.memory().getBooleanOr("discussed", false));
		tag.putBoolean("discussed", npc.memory().getBooleanOr("discussed", false));
		tag.putInt("round", npc.memory().getIntOr("round", 0));
		tag.putInt("wins", npc.memory().getIntOr("wins", 0));
		tag.putInt("lean", npc.memory().getIntOr("lean", 0));
		tag.putInt("hint", npc.memory().getIntOr("hint", 0));
		tag.putInt("last", npc.memory().getIntOr("last", 9));
		tag.putInt("last_theirs", npc.memory().getIntOr("last_theirs", 0));
		tag.putString("reward", npc.memory().getStringOr("reward", ""));
		tag.putDouble("reward_n", npc.memory().getDoubleOr("reward_n", 0.0D));
		tag.putInt("lore", npc.memory().getIntOr("lore", 0));
		tag.putBoolean("sparred", npc.memory().getBooleanOr("sparred", false));
		tag.putBoolean("can_invite", Kota.config().companion && npc.temperament() == RAMAH && data.realm().ordinal() >= Realm.GOLDEN_CORE.ordinal());
		tag.putBoolean("own_dao", npc.dao() != null && (data.dao() == null || data.dao() == npc.dao()));
		tag.putBoolean("has_giok", has(player, 0));
		tag.putBoolean("has_herba", has(player, 1));
		tag.putBoolean("has_pil", has(player, 2));
		tag.putBoolean("has_pengalaman", data.xp() >= data.rank().xpRequired() * EXPERIENCE_SHARE);
		return tag;
	}

	private static boolean has(ServerPlayer player, int kind) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			if (kind == 0 && "jade_slip".equals(id) || kind == 1 && SpiritHerbs.isHerb(stack) || kind == 2 && id.endsWith("_pill")) {
				return true;
			}
		}
		return false;
	}

	private Pengembara() {
	}
}
