package dev.kultivator.sekte.langit.main;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kapal.KapalBajakEntity;
import dev.kultivator.dunia.kapal.KapalEntities;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.flow.Ledger;
import dev.kultivator.sect.event.Storehouse;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bajak Awan, the cloud pirates, and Raksasa Longbo (DESIGN 37.7.4). Named after the existing "Perahu Bajak Langit"
 * (audit 4.8): the same {@code kultivator:kapal_bajak} skiffs ({@link KapalBajakEntity#berthAt}) rise out of the cloud sea
 * ({@link LangitIds#FX_BAJAK}) and lie beside the island's docks while a band of rogue cultivators
 * ({@link RogueCultivators#spawnBand}, already tagged {@code rogue}) lands on the dock or inside the rim fence. 1 / 2 / 3
 * skiffs for tiers 3-5 / 6-8 / 9. Driven off (every band cleared): 150 renown and a chest of spoils into the Lumbung.
 * Nobody fights them for {@link #TIMEOUT_TICKS}: they leave with 5 % of one material category of the Gudang Bahan (at
 * most 300 units, through the public {@code Ledger}). One in four raids on an A7+ island at tier 8+ brings Raksasa Longbo,
 * a giant (scale 2.5) tier-4 rogue who wants the island's turtle (<i>Liezi</i>, "Tang Wen": the giants of Longbo caught six
 * of the turtles carrying the floating mountains): 400 renown and the trophy {@code cangkang_kura_langit}.
 * <p>
 * A hostile or warring NPC sect (module D) sends them under its own name and loses 2 standing per raid held off. The
 * enemy pressure of {@code util.Sibuk} is respected by the scheduler ({@link LangitPeristiwa}). No block is ever touched.
 */
public final class LangitBajak {

	public static final int TIMEOUT_TICKS = 20 * 60 * 10;
	public static final int RENOWN = 150;
	public static final int RENOWN_LONGBO = 400;
	public static final double LOOT_SHARE = 0.05D;
	public static final long LOOT_CAP_UNITS = 300L;
	public static final double LONGBO_SCALE = 2.5D;
	public static final int LONGBO_CHANCE = 4;
	public static final int MAX_SHIPS = 3;
	/** Story beats (EXTRA sekte range; 190-192 left to module W, which uses none). */
	public static final int BEAT_BAJAK = dev.kultivator.epic.EpicIds.EXTRA_SEKTE_FIRST + 93;
	public static final int BEAT_BAJAK_MENANG = dev.kultivator.epic.EpicIds.EXTRA_SEKTE_FIRST + 94;
	public static final int BEAT_LONGBO = dev.kultivator.epic.EpicIds.EXTRA_SEKTE_FIRST + 95;
	public static final int BEAT_LONGBO_KALAH = dev.kultivator.epic.EpicIds.EXTRA_SEKTE_FIRST + 96;
	/** Tag of the live event's mobs (the island guard counts them as foes, the fall check removes them). */
	public static final String TAG = "kultivator.bajak_awan";

	/** One live pirate raid on a sky hall. */
	static final class Raid {
		final UUID owner;
		final ServerLevel level;
		final long started;
		final List<KapalBajakEntity> ships = new ArrayList<>();
		final List<UUID> mobs = new ArrayList<>();
		final boolean test;
		int bands;
		int cleared;
		int lost;
		boolean longbo;
		@Nullable UUID boss;
		boolean bossDown;
		@Nullable String sectId;
		boolean over;

		Raid(UUID owner, ServerLevel level, boolean test) {
			this.owner = owner;
			this.level = level;
			this.test = test;
			this.started = level.getGameTime();
		}
	}

	private static final Map<UUID, Raid> LIVE = new HashMap<>();

	public static boolean active(UUID owner) {
		return LIVE.containsKey(owner);
	}

	static void clear() {
		for (Raid raid : List.copyOf(LIVE.values())) {
			vanishAll(raid);
		}
		LIVE.clear();
	}

	// ================================================================== start

	/**
	 * Starts a pirate raid on {@code owner}'s standing sky hall: {@code ships} 1-3 (0 = by tier), {@code longbo} forces the
	 * giant (tests), {@code test} skips the rewards' log line. Null when it started, else the reason.
	 */
	static @Nullable Component start(MinecraftServer server, UUID owner, int ships, boolean longbo, boolean test) {
		SkyHallView view = LangitApi.get().skyHall(owner);
		if (view == null || view.tethered()) {
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_aula");
		}
		if (LIVE.containsKey(owner)) {
			return Component.translatable("kultivator.sekte_langit.bajak.sudah");
		}
		ServerLevel level = server.getLevel(view.dim());
		if (level == null) {
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_aula");
		}
		RandomSource random = level.getRandom();
		int count = ships > 0 ? Mth.clamp(ships, 1, MAX_SHIPS) : view.tier() >= 9 ? 3 : view.tier() >= 6 ? 2 : 1;
		List<Vec3> landings = landings(view, random, count);
		if (landings.isEmpty()) {
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_tempat");
		}
		Raid raid = new Raid(owner, level, test);
		raid.sectId = hostileSect(owner, random);
		ServerPlayer target = server.getPlayerList().getPlayer(owner);
		int realm = target != null ? Cultivators.get(target).realm().ordinal() : 3;
		int rogueTier = Mth.clamp(1 + realm / 2, 1, 4);
		for (Vec3 landing : landings) {
			KapalBajakEntity ship = carrier(level, view, landing, random);
			if (ship != null) {
				raid.ships.add(ship);
			}
			int[] tiers = band(rogueTier, view.tier());
			int spawned = RogueCultivators.spawnBand(level, landing, tiers, realm, RogueCultivators.ROLE_AMBUSH, view.origin(), cleared -> bandDone(owner, cleared));
			if (spawned > 0) {
				raid.bands++;
				tagNew(level, raid, landing);
			}
		}
		if (raid.bands == 0) {
			vanishAll(raid);
			return Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_tempat");
		}
		boolean bigIsland = view.template() >= 17 && view.tier() >= 8;
		if (longbo || bigIsland && dev.kultivator.sekte.langit.api.LangitConfig.get().longbo && random.nextInt(LONGBO_CHANCE) == 0) {
			raid.longbo = summonLongbo(level, view, raid, realm, random);
		}
		LIVE.put(owner, raid);
		Component who = raiders(raid.sectId);
		Component line = raid.longbo ? Component.translatable("kultivator.sekte_langit.bajak.longbo_datang", who)
				: Component.translatable("kultivator.sekte_langit.bajak.datang", who, raid.bands);
		tellIsland(server, view, line.copy().withStyle(ChatFormatting.RED));
		Halls.log(owner, line);
		LangitData.get(server).chronicle(owner, LangitPeristiwa.today(server), line);
		level.playSound(null, view.origin().getX(), view.origin().getY(), view.origin().getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6.0F,
				0.7F);
		if (target != null) {
			LangitPeristiwa.scene(level, raid.longbo ? BEAT_LONGBO : BEAT_BAJAK, raid.boss == null ? null : level.getEntity(raid.boss), target,
					Vec3.atCenterOf(view.origin()), raid.bands);
		}
		return null;
	}

	/** The rogue tiers of one landing band: a leader one above, the rest at the rogue tier; four from island tier 6. */
	static int[] band(int rogueTier, int islandTier) {
		int size = islandTier >= 6 ? 4 : 3;
		int[] tiers = new int[size];
		tiers[0] = Math.min(4, rogueTier + 1);
		for (int i = 1; i < size; i++) {
			tiers[i] = Math.max(1, rogueTier - (i % 2));
		}
		return tiers;
	}

	/** Tags the mobs that just spawned around {@code landing} (the band of this raid) and names its leader. */
	private static void tagNew(ServerLevel level, Raid raid, Vec3 landing) {
		AABB box = new AABB(landing, landing).inflate(6.0D, 4.0D, 6.0D);
		boolean named = false;
		for (RogueCultivatorEntity mob : level.getEntitiesOfClass(RogueCultivatorEntity.class, box, m -> !m.entityTags().contains(TAG))) {
			mob.addTag(TAG);
			raid.mobs.add(mob.getUUID());
			if (!named && raid.bands == 1) {
				named = true;
				mob.setCustomName(Component.translatable("kultivator.sekte_langit.bajak.kapten"));
				LangitSuaraServer.say(mob, "sekte_langit.bajak_datang", "rogue.ambush");
			}
		}
	}

	private static boolean summonLongbo(ServerLevel level, SkyHallView view, Raid raid, int realm, RandomSource random) {
		BlockPos turtle = LangitApi.get().anchor(view.owner(), "kura_kura");
		Vec3 at = turtle != null ? Vec3.atBottomCenterOf(turtle) : landings(view, random, 1).stream().findFirst().orElse(null);
		if (at == null) {
			return false;
		}
		Mob boss = RogueCultivators.spawn(level, at, 4, Math.min(realm + 1, dev.kultivator.data.Realm.values().length - 1),
				RogueCultivators.VARIANT_ROGUE, RogueCultivators.ROLE_AMBUSH, view.origin());
		if (boss == null) {
			return false;
		}
		AttributeInstance scale = boss.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(LONGBO_SCALE);
		}
		boss.setCustomName(Component.translatable("kultivator.sekte_langit.bajak.longbo"));
		boss.addTag(TAG);
		raid.boss = boss.getUUID();
		raid.mobs.add(boss.getUUID());
		LangitSuaraServer.say(boss, "sekte_langit.longbo_datang", "boss.awaken");
		return true;
	}

	// ================================================================== ships

	/** Where bands land: the dock anchors, then the landing anchors, then points on the plaza ring. */
	static List<Vec3> landings(SkyHallView view, RandomSource random, int count) {
		List<Vec3> points = new ArrayList<>();
		List<BlockPos> anchors = new ArrayList<>();
		for (Map.Entry<String, BlockPos> entry : LangitApi.get().anchors(view.owner()).entrySet()) {
			if (entry.getKey().startsWith("dermaga_") || entry.getKey().startsWith("darat_")) {
				anchors.add(entry.getValue());
			}
		}
		for (int i = 0; i < anchors.size() && points.size() < count; i++) {
			BlockPos pos = anchors.get(Math.floorMod(i + random.nextInt(Math.max(1, anchors.size())), anchors.size()));
			Vec3 point = Vec3.atBottomCenterOf(pos);
			if (!points.contains(point)) {
				points.add(point);
			}
		}
		double half = Math.max(4.0D, Math.min(view.islandBox().getXSpan(), view.islandBox().getZSpan()) * 0.18D);
		double turn = random.nextDouble() * Math.PI * 2.0D;
		for (int i = 0; points.size() < count && i < 8; i++) {
			double angle = turn + Math.PI * 2.0D * i / Math.max(1, count);
			points.add(new Vec3(view.origin().getX() + 0.5D + Math.cos(angle) * half, view.yP(), view.origin().getZ() + 0.5D + Math.sin(angle) * half));
		}
		return points;
	}

	/**
	 * A skiff that rises out of the cloud sea and lies beside {@code landing}, a little outside the island's edge (scenery:
	 * never hit, never saved). Null when the budget or the chunk refuses.
	 */
	static @Nullable KapalBajakEntity carrier(ServerLevel level, SkyHallView view, Vec3 landing, RandomSource random) {
		if (!dev.kultivator.dunia.DuniaBudget.mayAdd(level, dev.kultivator.dunia.DuniaBudget.PART)) {
			return null;
		}
		Vec3 centre = Vec3.atBottomCenterOf(view.origin());
		Vec3 out = landing.subtract(centre).multiply(1.0D, 0.0D, 1.0D);
		if (out.lengthSqr() < 1.0E-3D) {
			double a = random.nextDouble() * Math.PI * 2.0D;
			out = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
		}
		out = out.normalize();
		double edge = Math.max(view.islandBox().getXSpan(), view.islandBox().getZSpan()) * 0.5D + 6.0D;
		Vec3 berth = new Vec3(centre.x + out.x * edge, landing.y + 1.0D, centre.z + out.z * edge);
		Vec3 from = new Vec3(berth.x + out.x * 36.0D, view.skyFloorY() - 24.0D, berth.z + out.z * 36.0D);
		if (!level.isPositionEntityTicking(BlockPos.containing(berth))) {
			return null;
		}
		KapalBajakEntity ship = KapalEntities.BAJAK.create(level, EntitySpawnReason.EVENT);
		if (ship == null) {
			return null;
		}
		float yaw = (float) (Mth.atan2(-out.z, -out.x) * Mth.RAD_TO_DEG) - 90.0F;
		ship.berthAt(berth, yaw + 90.0F);
		ship.snapTo(from.x, Mth.clamp(from.y, level.getMinY() + 4.0D, level.getMaxY() - 20.0D), from.z, yaw, 0.0F);
		if (!level.addFreshEntity(ship)) {
			return null;
		}
		dev.kultivator.dunia.DuniaBudget.track(dev.kultivator.dunia.DuniaBudget.PART, ship);
		LangitPayloads.fxNear(level, LangitIds.FX_BAJAK, BlockPos.containing(from), BlockPos.containing(berth), 256.0D);
		return ship;
	}

	// ================================================================== the fight

	private static void bandDone(UUID owner, Boolean cleared) {
		Raid raid = LIVE.get(owner);
		if (raid == null || raid.over) {
			return;
		}
		if (Boolean.TRUE.equals(cleared)) {
			raid.cleared++;
		} else {
			raid.lost++;
		}
	}

	/** A tracked death (from {@code ServerLivingEntityEvents.AFTER_DEATH}): the giant fell. */
	static void onDeath(LivingEntity entity) {
		if (!entity.entityTags().contains(TAG)) {
			return;
		}
		for (Raid raid : LIVE.values()) {
			if (entity.getUUID().equals(raid.boss)) {
				raid.bossDown = true;
				String line = LangitSuaraServer.pick("sekte_langit.longbo_kalah", "boss.defeat");
				if (line != null) {
					dev.kultivator.voice.Voice.sayHere(entity, line, null);
				}
			}
		}
	}

	/** Once a second: a raid ends when every band is gone, the giant is down (if he came) or the time ran out. */
	static void second(MinecraftServer server) {
		if (LIVE.isEmpty()) {
			return;
		}
		for (Raid raid : List.copyOf(LIVE.values())) {
			try {
				step(server, raid);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sekte Langit pirate raid failed for {}", raid.owner, exception);
				finish(server, raid, false, true);
			}
		}
	}

	private static void step(MinecraftServer server, Raid raid) {
		SkyHallView view = LangitApi.get().skyHall(raid.owner);
		if (view == null) {
			finish(server, raid, false, true);
			return;
		}
		// raiders knocked off the island are "thrown to the clouds": they are gone and count as slain
		for (UUID id : List.copyOf(raid.mobs)) {
			if (raid.level.getEntity(id) instanceof LivingEntity mob && mob.isAlive() && mob.getY() < view.skyFloorY() - 16) {
				mob.kill(raid.level);
			}
		}
		boolean bandsDone = raid.cleared + raid.lost >= raid.bands;
		boolean bossDone = !raid.longbo || raid.bossDown || raid.boss == null || !(raid.level.getEntity(raid.boss) instanceof LivingEntity alive)
				|| !alive.isAlive();
		if (bandsDone && bossDone) {
			finish(server, raid, raid.lost == 0 || raid.cleared > 0, false);
			return;
		}
		if (raid.level.getGameTime() - raid.started >= TIMEOUT_TICKS) {
			finish(server, raid, false, false);
		}
	}

	/** Ends the raid: {@code won} pays out, otherwise the pirates take their cut; {@code quiet} ends without either. */
	static void finish(MinecraftServer server, Raid raid, boolean won, boolean quiet) {
		if (raid.over) {
			return;
		}
		raid.over = true;
		LIVE.remove(raid.owner);
		vanishAll(raid);
		if (quiet) {
			return;
		}
		SkyHallView view = LangitApi.get().skyHall(raid.owner);
		Component who = raiders(raid.sectId);
		long day = LangitPeristiwa.today(server);
		ServerPlayer owner = server.getPlayerList().getPlayer(raid.owner);
		if (won) {
			int renown = RENOWN + (raid.longbo && raid.bossDown ? RENOWN_LONGBO : 0);
			Halls.addRenown(raid.owner, renown, "kultivator.sekte_langit.bajak.renown");
			Component spoils = spoils(server, raid, view);
			Component line = Component.translatable("kultivator.sekte_langit.bajak.menang", who, renown).append(" ").append(spoils);
			SekteLog.log(server, raid.owner, "langit", line);
			LangitData.get(server).chronicle(raid.owner, day, line);
			if (view != null) {
				tellIsland(server, view, line.copy().withStyle(ChatFormatting.GOLD));
				for (ServerPlayer player : islandPlayers(server, view)) {
					Advancements.grant(player, LangitIds.ADV + "bajak_awan");
					if (raid.longbo && raid.bossDown) {
						Advancements.grant(player, LangitIds.ADV + "longbo");
					}
				}
				if (owner != null) {
					LangitPeristiwa.scene(raid.level, raid.longbo && raid.bossDown ? BEAT_LONGBO_KALAH : BEAT_BAJAK_MENANG, owner, owner,
							Vec3.atCenterOf(view.origin()), renown);
				}
			}
			if (raid.sectId != null) {
				SekteApi.diplomasi().adjust(raid.owner, raid.sectId, -2, "langit.bajak_ditahan");
			}
		} else {
			Component loss = plunder(raid.owner);
			Component line = Component.translatable("kultivator.sekte_langit.bajak.kalah", who).append(" ").append(loss);
			SekteLog.log(server, raid.owner, "langit", line);
			LangitData.get(server).chronicle(raid.owner, day, line);
			if (view != null) {
				tellIsland(server, view, line.copy().withStyle(ChatFormatting.DARK_RED));
			}
			if (owner != null && (view == null || !view.inVolume(owner.position()))) {
				Msg.chat(owner, line.copy().withStyle(ChatFormatting.DARK_RED));
			}
		}
	}

	private static void vanishAll(Raid raid) {
		for (UUID id : raid.mobs) {
			if (raid.level.getEntity(id) instanceof RogueCultivatorEntity mob && mob.isAlive()) {
				mob.vanish(true);
			}
		}
		for (KapalBajakEntity ship : raid.ships) {
			if (!ship.isRemoved()) {
				ship.leave();
			}
		}
	}

	/** The spoils chest: spirit stones by the island's tier into the Lumbung (the rest to the owner), the trophy for Longbo. */
	private static Component spoils(MinecraftServer server, Raid raid, @Nullable SkyHallView view) {
		int band = view == null ? 2 : Mth.clamp(1 + view.tier() / 3, 1, 4);
		List<ItemStack> loot = new ArrayList<>();
		switch (band) {
			case 1 -> {
				loot.add(SpiritStones.stack(SpiritStones.LOW, 8));
				loot.add(SpiritStones.stack(SpiritStones.MID, 1));
			}
			case 2 -> {
				loot.add(SpiritStones.stack(SpiritStones.LOW, 4));
				loot.add(SpiritStones.stack(SpiritStones.MID, 3));
			}
			case 3 -> {
				loot.add(SpiritStones.stack(SpiritStones.MID, 6));
				loot.add(SpiritStones.stack(SpiritStones.HIGH, 1));
			}
			default -> {
				loot.add(SpiritStones.stack(SpiritStones.MID, 4));
				loot.add(SpiritStones.stack(SpiritStones.HIGH, 2));
			}
		}
		if (raid.longbo && raid.bossDown) {
			loot.add(new ItemStack(LangitItems.CANGKANG_KURA_LANGIT));
		}
		net.minecraft.network.chat.MutableComponent list = Component.empty();
		boolean first = true;
		ServerPlayer owner = server.getPlayerList().getPlayer(raid.owner);
		for (ItemStack stack : loot) {
			if (stack.isEmpty()) {
				continue;
			}
			list.append(Component.literal(first ? "" : ", ")).append(Component.literal(stack.getCount() + "× ")).append(stack.getHoverName());
			first = false;
			ItemStack rest = Storehouse.insert(raid.owner, stack);
			if (!rest.isEmpty() && owner != null) {
				give(owner, rest);
			}
		}
		return Component.translatable("kultivator.sekte_langit.bajak.rampasan", list);
	}

	/** The pirates' cut: 5 % of the fullest material category, at most 300 units. */
	private static Component plunder(UUID owner) {
		Bahan best = null;
		long most = 0L;
		for (Bahan cat : Bahan.values()) {
			long have = Ledger.INSTANCE.centi(owner, cat);
			if (have > most) {
				most = have;
				best = cat;
			}
		}
		if (best == null) {
			return Component.translatable("kultivator.sekte_langit.bajak.gudang_kosong");
		}
		long want = Math.min((long) Math.floor(most * LOOT_SHARE), LOOT_CAP_UNITS * Bahan.CENTI);
		long taken = want <= 0L ? 0L : Ledger.INSTANCE.take(owner, best, want);
		return Component.translatable("kultivator.sekte_langit.bajak.dirampas", taken / Bahan.CENTI, best.displayName());
	}

	/** A hostile or warring NPC sect sends the raid (named after it), else null. */
	private static @Nullable String hostileSect(UUID owner, RandomSource random) {
		DiplomasiService d = SekteApi.diplomasi();
		List<String> hostile = new ArrayList<>();
		for (String id : d.sects()) {
			dev.kultivator.sekte.api.Sikap sikap = d.stance(owner, id);
			if (sikap == dev.kultivator.sekte.api.Sikap.PERANG || sikap == dev.kultivator.sekte.api.Sikap.BERMUSUHAN) {
				hostile.add(id);
			}
		}
		return hostile.isEmpty() ? null : hostile.get(random.nextInt(hostile.size()));
	}

	static Component raiders(@Nullable String sectId) {
		Component name = sectId == null ? null : SekteApi.diplomasi().name(sectId);
		return name == null ? Component.translatable("kultivator.sekte_langit.bajak.nama") : Component.translatable("kultivator.sekte_langit.bajak.nama_sekte", name);
	}

	// ================================================================== helpers

	/** Into the bag; what does not fit lands at the player's feet (no block is touched). */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty() || player.getInventory().add(stack) || stack.isEmpty()) {
			return;
		}
		net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY() + 0.5D,
				player.getZ(), stack);
		drop.addTag(dev.kultivator.util.NoGrief.TAG);
		drop.setPickUpDelay(0);
		player.level().addFreshEntity(drop);
	}

	static List<ServerPlayer> islandPlayers(MinecraftServer server, SkyHallView view) {
		List<ServerPlayer> out = new ArrayList<>();
		ServerLevel level = server.getLevel(view.dim());
		if (level == null) {
			return out;
		}
		for (ServerPlayer player : level.players()) {
			if (view.inVolume(player.position())) {
				out.add(player);
			}
		}
		return out;
	}

	static void tellIsland(MinecraftServer server, SkyHallView view, Component line) {
		for (ServerPlayer player : islandPlayers(server, view)) {
			Msg.chat(player, line);
		}
	}

	private LangitBajak() {
	}
}
