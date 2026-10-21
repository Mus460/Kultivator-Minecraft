package dev.kultivator.dunia.kota;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.musuh.Camps;
import dev.kultivator.util.KillCredit;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Papan Misi of a town (DESIGN 34.5.1): three bounties per town per day, seeded by (town, day), kept by the guard at
 * the notice board. Kinds: hunt an elite beast near the town (an elite of the existing {@code EliteMonsters} API),
 * clear a bandit camp ({@code musuh/Camps}), deliver an aged herb of at least 100 years, escort a merchant to the
 * nearest other town or village within 2 000 blocks. One bounty at a time; at most {@code kota.bountiesPerDay} per
 * player per day over all towns. Rewards: spirit stones by band (Qi Refining 6-12 NB, Foundation 15-30, Golden Core
 * 40-80, ×3 per further band), +5 Nama Baik, a jade slip at 20 %.
 */
final class PapanMisi {

	static final int HUNT = 0;
	static final int CAMP = 1;
	static final int HERB = 2;
	static final int ESCORT = 3;
	static final String[] IDS = {"buru", "kemah", "herba", "kawal"};
	static final int HERB_AGE = 100;
	static final int ESCORT_RANGE = 2000;
	static final int REPUTATION = 5;
	static final String COUNT = "bounties";

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(victim.level() instanceof ServerLevel level)) {
				return;
			}
			String tag = victim.entityTags().stream().filter(t -> t.startsWith("kultivator.misi.")).findFirst().orElse(null);
			if (tag == null) {
				return;
			}
			UUID owner;
			try {
				owner = UUID.fromString(tag.substring("kultivator.misi.".length()));
			} catch (IllegalArgumentException exception) {
				return;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
			if (player == null) {
				return;
			}
			CompoundTag misi = active(player);
			if (victim instanceof KultivatorNpc && misi.getIntOr("type", -1) == ESCORT) {
				fail(player, Component.translatable("kultivator.kota.misi.kawal_gagal"));
				return;
			}
			KillCredit.Credit credit = KillCredit.resolve(victim, source);
			if (credit != null && credit.owner() == player && misi.getIntOr("type", -1) == HUNT) {
				misi.putBoolean("done", true);
				KotaData.get(level.getServer()).touch();
				Msg.chat(player, Component.translatable("kultivator.kota.misi.buru_selesai").withStyle(ChatFormatting.GREEN));
			}
		});
	}

	/** The player's active bounty (empty when none). */
	static CompoundTag active(ServerPlayer player) {
		return KotaData.get(player.level().getServer()).player(player.getUUID()).getCompoundOrEmpty("misi");
	}

	private static void setActive(ServerPlayer player, @Nullable CompoundTag misi) {
		KotaData data = KotaData.get(player.level().getServer());
		if (misi == null) {
			data.player(player.getUUID()).remove("misi");
		} else {
			data.player(player.getUUID()).put("misi", misi);
		}
		data.touch();
	}

	/** Today's three bounties of the town (kind and reward band). */
	static int[] board(MinecraftServer server, long town) {
		long day = Kota.day(server);
		RandomSource random = RandomSource.create(town * 61L + day * 1_000_033L);
		int n = Kota.config().bounties ? 3 : 0;
		int[] kinds = new int[n];
		for (int i = 0; i < n; i++) {
			kinds[i] = random.nextInt(IDS.length);
		}
		return kinds;
	}

	/** The stone reward for a band (NB). */
	static long reward(int band, RandomSource random) {
		long low;
		long high;
		if (band <= 1) {
			low = 6L;
			high = 12L;
		} else if (band == 2) {
			low = 15L;
			high = 30L;
		} else {
			low = 40L;
			high = 80L;
			for (int b = 4; b <= band; b++) {
				low *= 3L;
				high *= 3L;
			}
		}
		return low + (long) (random.nextDouble() * (high - low + 1L));
	}

	// ================================================================== actions

	static boolean action(ServerPlayer player, KultivatorNpc npc, String verb, String[] parts) {
		if (npc.role() != NpcRole.PENJAGA || !Kota.config().bounties) {
			return false;
		}
		switch (verb) {
			case "ambil" -> accept(player, npc, Kota.arg(parts, 1, -1));
			case "serah" -> turnIn(player, npc);
			case "batal" -> cancel(player);
			default -> {
				return false;
			}
		}
		return true;
	}

	private static void accept(ServerPlayer player, KultivatorNpc npc, int index) {
		MinecraftServer server = player.level().getServer();
		KotaData data = KotaData.get(server);
		long day = Kota.day(server);
		if (!active(player).isEmpty()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.misi.sudah_ada").withStyle(ChatFormatting.RED));
			return;
		}
		if (data.dailyCount(player.getUUID(), day, COUNT) >= Kota.config().bountiesPerDay) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.misi.batas", Kota.config().bountiesPerDay).withStyle(ChatFormatting.RED));
			return;
		}
		int[] board = board(server, npc.town());
		if (index < 0 || index >= board.length) {
			return;
		}
		CompoundTag town = data.town(npc.town());
		if (town.getCompoundOrEmpty("misi_taken").getBooleanOr(player.getUUID() + "." + day + "." + index, false)) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.misi.sudah_diambil").withStyle(ChatFormatting.RED));
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		CompoundTag misi = new CompoundTag();
		misi.putInt("type", board[index]);
		misi.putLong("town", npc.town());
		misi.putLong("day", day);
		misi.putInt("index", index);
		misi.putInt("band", Mth.clamp(Cultivators.get(player).realm().ordinal(), 1, 8));
		BlockPos centre = npc.blockPosition();
		switch (board[index]) {
			case HUNT -> {
				Mob mob = spawnBeast(level, player, centre, misi.getIntOr("band", 1));
				if (mob == null) {
					Msg.actionBar(player, Component.translatable("kultivator.kota.misi.tidak_bisa").withStyle(ChatFormatting.RED));
					return;
				}
				misi.putString("mob", mob.getUUID().toString());
				Msg.chat(player, Component.translatable("kultivator.kota.misi.buru_mulai", KedaiTeh.direction(player.blockPosition(), mob.blockPosition()),
						(int) Math.sqrt(mob.distanceToSqr(player))).withStyle(ChatFormatting.GOLD));
			}
			case CAMP -> {
				BlockPos at = campSpot(level, centre);
				int id = Camps.open(level, at, player.getUUID(), cleared -> {
					if (cleared) {
						CompoundTag current = active(player);
						if (current.getIntOr("type", -1) == CAMP) {
							current.putBoolean("done", true);
							KotaData.get(level.getServer()).touch();
							Msg.chat(player, Component.translatable("kultivator.kota.misi.kemah_selesai").withStyle(ChatFormatting.GREEN));
						}
					}
				});
				misi.putInt("camp", id);
				Msg.chat(player, Component.translatable("kultivator.kota.misi.kemah_mulai", KedaiTeh.direction(player.blockPosition(), at), at.getX(), at.getZ())
						.withStyle(ChatFormatting.GOLD));
			}
			case HERB -> Msg.chat(player, Component.translatable("kultivator.kota.misi.herba_mulai", HERB_AGE).withStyle(ChatFormatting.GOLD));
			case ESCORT -> {
				BlockPos dest = escortTarget(level, npc);
				if (dest == null) {
					Msg.actionBar(player, Component.translatable("kultivator.kota.misi.kawal_tujuan_tidak_ada").withStyle(ChatFormatting.RED));
					return;
				}
				KultivatorNpc merchant = spawnMerchant(level, player, npc);
				if (merchant == null) {
					Msg.actionBar(player, Component.translatable("kultivator.kota.misi.tidak_bisa").withStyle(ChatFormatting.RED));
					return;
				}
				misi.putLong("dest", dest.asLong());
				misi.putString("mob", merchant.getUUID().toString());
				Msg.chat(player, Component.translatable("kultivator.kota.misi.kawal_mulai", KedaiTeh.direction(player.blockPosition(), dest),
						(int) Math.sqrt(dest.distSqr(player.blockPosition()))).withStyle(ChatFormatting.GOLD));
			}
			default -> {
				return;
			}
		}
		CompoundTag taken = town.getCompoundOrEmpty("misi_taken");
		taken.putBoolean(player.getUUID() + "." + day + "." + index, true);
		town.put("misi_taken", taken);
		data.addDaily(player.getUUID(), day, COUNT, 1);
		setActive(player, misi);
		Voice.say(npc, npc.voiceKey("protect"), null);
		player.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.8F, 1.0F);
	}

	private static void turnIn(ServerPlayer player, KultivatorNpc npc) {
		CompoundTag misi = active(player);
		if (misi.isEmpty()) {
			return;
		}
		int type = misi.getIntOr("type", -1);
		boolean done = misi.getBooleanOr("done", false);
		if (type == HERB && !done) {
			done = takeHerb(player);
			if (!done) {
				Msg.actionBar(player, Component.translatable("kultivator.kota.misi.herba_kurang", HERB_AGE).withStyle(ChatFormatting.RED));
				return;
			}
		}
		if (!done) {
			Msg.actionBar(player, Component.translatable("kultivator.kota.misi.belum").withStyle(ChatFormatting.YELLOW));
			return;
		}
		pay(player, npc.town(), misi);
		setActive(player, null);
		Voice.say(npc, npc.voiceKey("thanks"), null);
	}

	private static void cancel(ServerPlayer player) {
		CompoundTag misi = active(player);
		if (misi.isEmpty()) {
			return;
		}
		cleanup(player, misi);
		setActive(player, null);
		Msg.actionBar(player, Component.translatable("kultivator.kota.misi.dibatalkan").withStyle(ChatFormatting.GRAY));
	}

	static void fail(ServerPlayer player, Component why) {
		CompoundTag misi = active(player);
		if (misi.isEmpty()) {
			return;
		}
		cleanup(player, misi);
		setActive(player, null);
		Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
	}

	private static void cleanup(ServerPlayer player, CompoundTag misi) {
		if (misi.contains("camp")) {
			Camps.close(misi.getIntOr("camp", -1));
		}
		if (misi.contains("mob")) {
			try {
				Entity mob = ((ServerLevel) player.level()).getEntity(UUID.fromString(misi.getStringOr("mob", "")));
				if (mob instanceof KultivatorNpc npc) {
					npc.vanish();
				}
			} catch (IllegalArgumentException ignored) {
				// gone
			}
		}
	}

	/** The reward: stones, Nama Baik and sometimes a jade slip. */
	private static void pay(ServerPlayer player, long town, CompoundTag misi) {
		RandomSource random = player.getRandom();
		long stones = reward(misi.getIntOr("band", 1), random);
		NilaiBatu.give(player, stones);
		int rep = KotaData.get(player.level().getServer()).addReputation(town, player.getUUID(), REPUTATION);
		Msg.chat(player, Component.translatable("kultivator.kota.misi.hadiah", NilaiBatu.text(stones), REPUTATION, rep).withStyle(ChatFormatting.GREEN));
		if (random.nextFloat() < 0.2F) {
			ItemStack slip = Harga.item("jade_slip");
			if (!slip.isEmpty() && !player.getInventory().add(slip)) {
				Kota.drop(player, slip);
			}
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
	}

	private static boolean takeHerb(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (SpiritHerbs.isHerb(stack) && SpiritHerbs.age(stack) >= HERB_AGE) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}

	/** Every 20 ticks per escort: the merchant reached the destination (done) or wandered off (the bounty fails). */
	static void tickEscort(ServerLevel level, KultivatorNpc merchant) {
		UUID owner = merchant.companionOf();
		if (owner == null) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (player == null) {
			return;
		}
		CompoundTag misi = active(player);
		if (misi.getIntOr("type", -1) != ESCORT || misi.getBooleanOr("done", false)) {
			return;
		}
		BlockPos dest = BlockPos.of(misi.getLongOr("dest", 0L));
		double dx = dest.getX() - merchant.getX();
		double dz = dest.getZ() - merchant.getZ();
		if (dx * dx + dz * dz <= 32.0D * 32.0D) {
			misi.putBoolean("done", true);
			KotaData.get(level.getServer()).touch();
			Voice.say(merchant, merchant.voiceKey("thanks"), null);
			Msg.chat(player, Component.translatable("kultivator.kota.misi.kawal_selesai").withStyle(ChatFormatting.GREEN));
			pay(player, misi.getLongOr("town", 0L), misi);
			setActive(player, null);
			merchant.setCompanion(null, 0L);
			merchant.vanish();
		}
	}

	// ================================================================== spawns

	private static @Nullable Mob spawnBeast(ServerLevel level, ServerPlayer player, BlockPos centre, int band) {
		RandomSource random = level.getRandom();
		EntityType<? extends Mob> type = switch (random.nextInt(4)) {
			case 0 -> EntityTypes.SPIDER;
			case 1 -> EntityTypes.WOLF;
			case 2 -> EntityTypes.HUSK;
			default -> EntityTypes.ZOMBIE;
		};
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = 24.0D + random.nextDouble() * 16.0D;
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			BlockPos column = new BlockPos(x, 0, z);
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			Mob mob = type.create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				return null;
			}
			Vec3 spot = Targeting.safeSpot(level, mob, new Vec3(x + 0.5D, y, z + 0.5D));
			if (spot == null) {
				continue;
			}
			mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
			mob.addTag("kultivator.misi." + player.getUUID());
			mob.setPersistenceRequired();
			if (!level.addFreshEntity(mob)) {
				return null;
			}
			EliteMonsters.makeElite(mob, Mth.clamp(1 + band / 3, 1, 3), null, band);
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1200, 0));
			return mob;
		}
		return null;
	}

	private static BlockPos campSpot(ServerLevel level, BlockPos centre) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double distance = 120.0D + random.nextDouble() * 80.0D;
		int x = Mth.floor(centre.getX() + Math.cos(angle) * distance);
		int z = Mth.floor(centre.getZ() + Math.sin(angle) * distance);
		return new BlockPos(x, centre.getY(), z);
	}

	/** The nearest other known town, else the nearest village, within 2 000 blocks (one locate). */
	private static @Nullable BlockPos escortTarget(ServerLevel level, KultivatorNpc npc) {
		BlockPos from = npc.blockPosition();
		BlockPos best = null;
		double bestSq = (double) ESCORT_RANGE * ESCORT_RANGE;
		for (long id : Towns.known(level.getServer())) {
			if (id == npc.town() || !level.dimension().equals(Towns.dimension(level.getServer(), id))) {
				continue;
			}
			BlockPos centre = Towns.centre(level.getServer(), id);
			if (centre != null && centre.distSqr(from) <= bestSq && centre.distSqr(from) > 200.0D * 200.0D) {
				bestSq = centre.distSqr(from);
				best = centre;
			}
		}
		if (best != null) {
			return best;
		}
		BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, from, ESCORT_RANGE / 16, false);
		if (village != null && village.distSqr(from) <= (double) ESCORT_RANGE * ESCORT_RANGE && village.distSqr(from) > 150.0D * 150.0D) {
			return village;
		}
		return null;
	}

	private static @Nullable KultivatorNpc spawnMerchant(ServerLevel level, ServerPlayer player, KultivatorNpc board) {
		if (!DuniaBudget.mayAdd(level, DuniaBudget.NPC, player.position())) {
			return null;
		}
		KultivatorNpc merchant = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (merchant == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, merchant, player.position().add(1.5D, 0.0D, 1.5D));
		if (spot == null) {
			spot = player.position();
		}
		int look = level.getRandom().nextInt();
		merchant.snapTo(spot.x, spot.y, spot.z, player.getYRot(), 0.0F);
		merchant.setup(NpcRole.WARGA, 1, look, 1, 0, Dao.values()[Math.floorMod(look, Dao.COUNT)], Math.floorMod(look, 6), 0x8A6A3A,
				Component.translatable("kultivator.kota.misi.pedagang", KotaNames.person(look)));
		merchant.setTown(board.town());
		merchant.setLonely(96.0D, 2400);
		merchant.setCompanion(player.getUUID(), level.getGameTime() + 24000L);
		merchant.setFightsMonsters(false);
		merchant.memory().putBoolean("escort", true);
		merchant.addTag("kultivator.misi." + player.getUUID());
		if (!level.addFreshEntity(merchant)) {
			return null;
		}
		DuniaBudget.track(DuniaBudget.NPC, merchant);
		return merchant;
	}

	// ================================================================== view

	static CompoundTag view(ServerPlayer player, KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		MinecraftServer server = player.level().getServer();
		long day = Kota.day(server);
		KotaData data = KotaData.get(server);
		int[] board = board(server, npc.town());
		ListTag list = new ListTag();
		CompoundTag taken = data.town(npc.town()).getCompoundOrEmpty("misi_taken");
		int band = Mth.clamp(Cultivators.get(player).realm().ordinal(), 1, 8);
		for (int i = 0; i < board.length; i++) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("type", board[i]);
			entry.putBoolean("taken", taken.getBooleanOr(player.getUUID() + "." + day + "." + i, false));
			RandomSource preview = RandomSource.create(npc.town() ^ day ^ i);
			entry.putLong("reward", reward(band, preview));
			list.add(entry);
		}
		tag.put("board", list);
		tag.putBoolean("enabled", Kota.config().bounties);
		tag.putInt("left", Math.max(0, Kota.config().bountiesPerDay - data.dailyCount(player.getUUID(), day, COUNT)));
		CompoundTag misi = active(player);
		if (!misi.isEmpty()) {
			CompoundTag mine = new CompoundTag();
			mine.putInt("type", misi.getIntOr("type", -1));
			mine.putBoolean("done", misi.getBooleanOr("done", false));
			mine.putBoolean("here", misi.getLongOr("town", 0L) == npc.town());
			tag.put("active", mine);
		}
		tag.putInt("rep", data.reputation(npc.town(), player.getUUID()));
		return tag;
	}

	private PapanMisi() {
	}
}
