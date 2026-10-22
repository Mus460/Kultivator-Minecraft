package dev.kultivator.dunia.kota;

import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One rule set for consignments in both auctions (audit T14, R28): the Balai Lelang of a town and Lelang Abadi (26.8)
 * take <b>one piece</b> per consignment, at most {@link #PER_SESSION} per player and session, only of a whitelist, and
 * pay 0.6-1.0 × ({@link #PAY_MIN} plus up to {@link #PAY_SPREAD}) the consignment's capped value at the session's end. Town
 * consignments are kept in {@code KotaData.player(uuid).titipan} (one entry per piece: town, day, name, value) from the
 * moment they are handed over, so a stopped server or a crash never loses them: an entry whose session no longer runs is
 * paid on the player's next town screen ({@link #payStale}). Lelang Abadi keeps its own list in the player's extra and
 * only uses {@link #paid}.
 */
public final class Titipan {

	/** Consignments per player and session (both auctions). */
	public static final int PER_SESSION = 2;
	/** The pay is PAY_MIN..PAY_MIN + PAY_SPREAD × the value (never above it: no buy-and-consign loop). */
	public static final double PAY_MIN = 0.6D;
	public static final double PAY_SPREAD = 0.4D;
	static final String KEY = "titipan";

	/** The pay for one consignment of {@code value}: 0.6-1.0 × value, at least 1. */
	public static long paid(long value, RandomSource random) {
		return Math.max(1L, Math.round(Math.max(0L, value) * (PAY_MIN + random.nextDouble() * PAY_SPREAD)));
	}

	/** The town whitelist: goods one of the town's pavilions trades (never a stone, an unsafe ring or a phase-9 ware). */
	static boolean townAccepts(ItemStack stack) {
		return Toko.tradable(stack, true) || Toko.tradable(stack, false);
	}

	private static ListTag list(KotaData data, UUID player) {
		CompoundTag tag = data.player(player);
		ListTag list = tag.getList(KEY).orElse(null);
		if (list == null) {
			list = new ListTag();
			tag.put(KEY, list);
		}
		return list;
	}

	/** The player's consignments of the session (town, day). */
	static int count(KotaData data, UUID player, long town, long day) {
		int n = 0;
		for (Tag each : data.player(player).getListOrEmpty(KEY)) {
			if (each instanceof CompoundTag entry && entry.getLongOr("town", 0L) == town && entry.getLongOr("day", -1L) == day) {
				n++;
			}
		}
		return n;
	}

	/** Stores one consigned piece (saved with the world at once). */
	static void add(KotaData data, UUID player, long town, long day, Component name, long value) {
		CompoundTag entry = new CompoundTag();
		entry.putLong("town", town);
		entry.putLong("day", day);
		entry.putLong("value", Math.max(1L, value));
		entry.store("name", ComponentSerialization.CODEC, name);
		list(data, player).add(entry);
		data.touch();
	}

	/** The names and values of the player's consignments of the session (for the Lelang tab). */
	static ListTag view(KotaData data, UUID player, long town, long day) {
		ListTag out = new ListTag();
		for (Tag each : data.player(player).getListOrEmpty(KEY)) {
			if (each instanceof CompoundTag entry && entry.getLongOr("town", 0L) == town && entry.getLongOr("day", -1L) == day) {
				CompoundTag row = new CompoundTag();
				row.store("name", ComponentSerialization.CODEC, entry.read("name", ComponentSerialization.CODEC).orElse(Component.empty()));
				row.putLong("value", entry.getLongOr("value", 1L));
				out.add(row);
			}
		}
		return out;
	}

	/**
	 * The session (town, day) ended: every player's piece of it is paid, online at once, offline into {@code owed} (paid on
	 * the next town screen by {@code BalaiLelang.payOwed}); the entries are removed.
	 */
	static void settle(MinecraftServer server, long town, long day, RandomSource random) {
		KotaData data = KotaData.get(server);
		for (UUID id : data.playerIds()) {
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			settle(data, id, online, random, (t, d) -> t == town && d == day);
		}
	}

	/** Pays the player's pieces whose session no longer runs (a stop or crash before the session's end). */
	static void payStale(ServerPlayer player, Match running) {
		settle(KotaData.get(player.level().getServer()), player.getUUID(), player, player.getRandom(), (t, d) -> !running.test(t, d));
	}

	/** A test on a session (town, day). */
	@FunctionalInterface
	interface Match {
		boolean test(long town, long day);
	}

	private static void settle(KotaData data, UUID id, @Nullable ServerPlayer online, RandomSource random, Match match) {
		ListTag list = data.player(id).getList(KEY).orElse(null);
		if (list == null || list.isEmpty()) {
			return;
		}
		long owed = 0L;
		for (int i = list.size() - 1; i >= 0; i--) {
			if (!(list.get(i) instanceof CompoundTag entry)) {
				list.remove(i);
				continue;
			}
			if (!match.test(entry.getLongOr("town", 0L), entry.getLongOr("day", -1L))) {
				continue;
			}
			long pay = paid(entry.getLongOr("value", 1L), random);
			Component name = entry.read("name", ComponentSerialization.CODEC).orElse(Component.empty());
			list.remove(i);
			if (online != null && !online.isRemoved()) {
				NilaiBatu.give(online, pay);
				Msg.chat(online, Component.translatable("kultivator.kota.lelang.titipan_laku", name, NilaiBatu.text(pay)).withStyle(ChatFormatting.GOLD));
			} else {
				owed += pay;
			}
		}
		if (list.isEmpty()) {
			data.player(id).remove(KEY);
		}
		if (owed > 0L) {
			CompoundTag tag = data.player(id);
			tag.putLong("owed", tag.getLongOr("owed", 0L) + owed);
		}
		data.touch();
	}

	private Titipan() {
	}
}
