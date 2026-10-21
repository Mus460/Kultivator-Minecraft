package dev.kultivator.dunia.reruntuhan.menara;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.reruntuhan.AnchorRun;
import dev.kultivator.dunia.reruntuhan.RNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The ranking stele of the pagoda (anchor {@code menara/prasasti}, DESIGN 34.5.7): within 24 blocks the players see
 * the week's five best climbers and the best of all time as floating text Displays (client {@code op: "stele"}),
 * refreshed every five seconds. The lobby's stele uses the same sync ({@link #send}).
 */
public final class RankStele extends AnchorRun {

	public RankStele(DuniaAnchorEntity anchor, ServerLevel level) {
		super(anchor, level);
	}

	@Override
	public double radius() {
		return 24.0D;
	}

	@Override
	protected void spawn() {
	}

	@Override
	protected void tick(long now) {
		if (now % 100L == 5L) {
			send(level, centre().add(0.0D, 2.2D, 0.0D), playersWithin(24.0D), anchor.getId());
		}
	}

	/** Sends the ranking lines for a stele at {@code at} to {@code viewers}. */
	public static void send(ServerLevel level, Vec3 at, List<ServerPlayer> viewers, long id) {
		if (viewers.isEmpty()) {
			return;
		}
		TowerRecords records = TowerRecords.get(level.getServer());
		records.rollWeek(level.getServer(), level.getServer().overworld().getGameTime());
		CompoundTag tag = new CompoundTag();
		tag.putString("op", "stele");
		tag.putDouble("x", at.x);
		tag.putDouble("y", at.y);
		tag.putDouble("z", at.z);
		tag.putLong("id", id);
		ListTag weekly = new ListTag();
		for (TowerRecords.Entry entry : records.weekly(5)) {
			weekly.add(StringTag.valueOf(entry.name() + "|" + entry.week()));
		}
		tag.put("weekly", weekly);
		List<TowerRecords.Entry> best = records.allTime(1);
		if (!best.isEmpty()) {
			tag.putString("best", best.get(0).name());
			tag.putInt("best_floor", best.get(0).best());
		}
		for (ServerPlayer player : viewers) {
			tag.putInt("mine", Menara.best(player));
			RNet.send(player, tag.copy());
		}
	}
}
