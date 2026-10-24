package dev.kultivator.desa;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.Halls;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Plakat Pelindung Sekte and the pledge (DESIGN 25.6.2): the placement checks, the waiting record, the Malam Penjagaan
 * trust night (run by {@link VillageRaids}) and the oath itself. Source: PW ch. 3 and 27 (a village and its guardian);
 * the plaque, the trust night and every number are game design.
 */
public final class Pledge {

	/** Other pledged centres must lie at least this far from a new bell. */
	public static final double OTHER_VILLAGE_RANGE = 96.0D;
	/** The owner must stand this close to the plaque for the trust night. */
	public static final double TRUST_RANGE = 48.0D;
	public static final int MIN_HALL_LEVEL = 2;
	public static final int OATH_LOYALTY = 40;
	/** The village names (proper nouns, the same in every language). */
	static final String[] NAMES = {"Qingshan", "Baiyun", "Liuye", "Shitou", "Hongfeng", "Jinsha", "Yunxi", "Taohua", "Zhulin", "Longquan",
			"Fengming", "Heishui", "Xueling", "Danxia", "Yueya", "Huangtu", "Lanting", "Songhe", "Wuyi", "Qinghe", "Baima", "Cuiping", "Mingyue",
			"Jiuqu"};

	/** Vassals allowed by the hall level: {@code 1 + floor(L / 3)} (1-4). */
	public static int maxVassals(int hallLevel) {
		return Math.max(1, Math.min(4, 1 + hallLevel / 3));
	}

	/** The plaque was placed by {@code player}: validates and opens a waiting record, or explains the refusal. */
	static void onPlaced(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Component why = refusal(player, level, pos);
		BlockPos bell = why == null ? VillageScan.findBell(level, pos) : null;
		if (why == null && bell == null) {
			why = Component.translatable("kultivator.desa.refuse.no_bell", VillageScan.BELL_RANGE);
		}
		if (why == null && Vassals.anyBellWithin(bell, OTHER_VILLAGE_RANGE)) {
			why = Component.translatable("kultivator.desa.refuse.taken", (int) OTHER_VILLAGE_RANGE);
		}
		if (why == null) {
			why = Tribute.vetoIkrar(player, level, bell);
		}
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
			Msg.chat(player, Component.translatable("kultivator.desa.refuse.inert").withStyle(ChatFormatting.GRAY));
			return;
		}
		UUID owner = player.getUUID();
		Vassals.Owner entry = Vassals.owner(owner);
		if (entry == null) {
			return;
		}
		Village village = new Village();
		village.id = entry.nextId++;
		village.plaque = pos;
		village.bell = bell;
		village.name = pickName(entry, level.getRandom());
		village.villagers = VillageScan.villagersOf(level, bell).size();
		entry.villages.add(village);
		Vassals.changed();
		Desa.dirty(owner);
		level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.7F, 0.8F);
		Fx.send(level, Vec3.atBottomCenterOf(pos), FxType.DESA, DesaFxIds.PLAQUE, 0, 0.0F);
		Msg.chat(player, Component.translatable("kultivator.desa.placed", village.name).withStyle(ChatFormatting.GOLD));
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			// No rogues on Peaceful: the village swears at once (DESIGN 25.1.2).
			complete(level.getServer(), owner, village);
		} else {
			Msg.chat(player, Component.translatable("kultivator.desa.trust.waiting", (int) TRUST_RANGE).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Why the plaque cannot start a pledge here, or null (the bell and the other villages are checked by the caller). */
	private static @Nullable Component refusal(ServerPlayer player, ServerLevel level, BlockPos pos) {
		KultivatorConfig config = KultivatorConfig.get();
		if (!config.vassalVillages) {
			return Component.translatable("kultivator.desa.refuse.off");
		}
		if (!level.dimension().equals(Level.OVERWORLD)) {
			return Component.translatable("kultivator.desa.refuse.overworld");
		}
		UUID owner = player.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.active() || hall.level() < MIN_HALL_LEVEL) {
			return Component.translatable("kultivator.desa.refuse.hall", MIN_HALL_LEVEL);
		}
		if (!hall.dim().equals(Level.OVERWORLD) || hall.pos().distSqr(pos) > (double) config.vassalRange * config.vassalRange) {
			return Component.translatable("kultivator.desa.refuse.range", config.vassalRange);
		}
		Vassals.Owner entry = Vassals.peek(owner);
		int records = 0;
		if (entry != null) {
			for (Village village : entry.villages) {
				if (!village.lost) {
					records++;
				}
			}
		}
		int max = maxVassals(hall.level());
		if (records >= max) {
			return Component.translatable("kultivator.desa.refuse.count", max);
		}
		return null;
	}

	private static String pickName(Vassals.Owner entry, RandomSource random) {
		Set<String> taken = new HashSet<>();
		for (Village village : entry.villages) {
			taken.add(village.name);
		}
		for (int attempt = 0; attempt < 48; attempt++) {
			String name = NAMES[random.nextInt(NAMES.length)];
			if (!taken.contains(name)) {
				return name;
			}
		}
		return NAMES[random.nextInt(NAMES.length)] + " " + (entry.nextId);
	}

	/** The trust night was survived (or Peaceful, or a test): the village swears allegiance. */
	static void complete(MinecraftServer server, UUID owner, Village village) {
		if (village.pledged) {
			return;
		}
		long day = Vassals.day();
		RandomSource random = server.overworld().getRandom();
		village.pledged = true;
		village.lost = false;
		village.loyalty = OATH_LOYALTY;
		village.nextTribute = day + Tribute.CYCLE_DAYS;
		village.nextRequest = day + 2 + random.nextInt(3);
		village.nextRaid = day + 7 + random.nextInt(6);
		village.lastDaily = day;
		Vassals.changed();
		Desa.dirty(owner);
		ServerLevel level = server.overworld();
		Vec3 at = Vec3.atBottomCenterOf(village.plaque);
		Fx.send(level, at, FxType.DESA, DesaFxIds.OATH, 0, 0.0F);
		level.playSound(null, village.plaque, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		Halls.HallView hall = Halls.hallOf(owner);
		String sect = hall == null || hall.name().isBlank() ? (player == null ? "?" : player.getName().getString()) : hall.name();
		Component line = Component.translatable("kultivator.desa.oath", village.name, sect);
		Halls.log(owner, line);
		if (player != null) {
			Msg.chat(player, line.copy().withStyle(ChatFormatting.GOLD));
			EpicHooks.extra(level, EpicIds.EXTRA_VILLAGE_OATH, player, null, at, village.id, 0);
			Advancements.grant(player, "village_pledge");
			if (Vassals.count(owner) >= 3) {
				Advancements.grant(player, "three_vassals");
			}
		}
	}

	/** The plaque at {@code pos} is gone: the record ends (its guard comes home, a running wave event stops). */
	static void onRemoved(ServerLevel level, BlockPos pos) {
		Map.Entry<UUID, Village> found = Vassals.byPlaque(pos);
		if (found == null) {
			return;
		}
		UUID owner = found.getKey();
		Village village = found.getValue();
		forget(level.getServer(), owner, village, "kultivator.desa.removed");
	}

	/** Ends a record for good: stops its wave event and request, sends the guard home and tells the owner. */
	static void forget(MinecraftServer server, UUID owner, Village village, String reasonKey) {
		VillageRaids.stop(owner, village.id);
		Requests.clear(server, owner, village);
		VillageGuards.onVillageGone(server, owner, village);
		Vassals.Owner entry = Vassals.peek(owner);
		if (entry != null) {
			entry.villages.remove(village);
		}
		Vassals.changed();
		Desa.dirty(owner);
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		Component line = Component.translatable(reasonKey, village.name);
		if (village.pledged) {
			Halls.log(owner, line);
		}
		if (player != null) {
			Msg.chat(player, line.copy().withStyle(ChatFormatting.YELLOW));
		}
	}

	private Pledge() {
	}
}
