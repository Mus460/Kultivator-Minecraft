package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.reruntuhan.Frame;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Where a {@link Puzzle} lives: a room of a Gua Leluhur (its anchor's frame and data) or the fourth floor of a band of
 * the Menara Seratus Lantai (a code-built layout on the floor's slot). The host owns the spawned entities, the timer,
 * the door and the rewards; the puzzle only plays its rules.
 */
public interface PuzzleHost {

	ServerLevel level();

	/** The puzzle's frame (the room centre at standing level, turned like its piece). */
	Frame frame();

	/** The layout data (template offsets in the frame: lamps, pans, walls, exit, ...). */
	CompoundTag layout();

	/** 1 Kecil .. 3 Kuno (the tower maps its bands onto 1-3). */
	int tier();

	/** A seed fixed for the room (the same arrangement every time it resets). */
	long seed();

	/** The players taking part now (inside the room). */
	List<ServerPlayer> challengers();

	/** Makes, adds and owns an entity at {@code at}; null when it could not be added. */
	<T extends Entity> @Nullable T spawn(EntityType<T> type, Vec3 at, float yaw);

	/** The puzzle is solved (the host opens the way and pays). */
	void solved(Puzzle puzzle);

	/** A wrong move of {@code player}: the formation backlash of DESIGN 34.5.5 (never below 1 HP). */
	void backlash(ServerPlayer player, Puzzle puzzle);

	/** True once the hint time has passed for the current attempt. */
	boolean hinted();

	/** True on a tower floor (no door, a timer bar of the floor). */
	boolean inTower();

	/** Sets the glyph of the host's exit door (the starting phase of the Lima Unsur). */
	default void doorGlyph(int glyph) {
	}
}
