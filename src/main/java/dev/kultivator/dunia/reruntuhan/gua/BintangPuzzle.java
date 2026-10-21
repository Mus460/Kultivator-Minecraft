package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * P3 Peta Bintang (DESIGN 34.5.5): three concentric rings of constellation stones (radius 2, 4, 6; the client draws
 * the stones as Displays and turns them in 4-tick {@code DisplayKey} frames). Each ring has one clickable handle stone on
 * its south point; a click turns that ring 45 degrees, and the gears of the formation turn the next ring with it
 * (inner turns middle, middle turns outer, outer turns inner). The Beidou's handle star (the marker stone of each ring)
 * must point to the star shining on the ceiling. Start positions are reached from the solution by random turns, so
 * every room can be solved.
 * <p>
 * Part data: {@code arg} = the ring's turn (0-7), {@code arg2} = (ring index << 8) | target direction; the part's
 * centre is the puzzle centre.
 */
final class BintangPuzzle extends Puzzle {

	private final int[] radius = {2, 4, 6};
	private final int[] turn = new int[3];
	private final int[] startTurn = new int[3];
	private final TekaPartEntity[] handles = new TekaPartEntity[3];
	private final int target;
	private int moves;

	BintangPuzzle(PuzzleHost host) {
		super(host);
		this.target = random.nextInt(8);
		for (int k = 0; k < 3; k++) {
			startTurn[k] = target;
		}
		// scramble with 4-7 backward moves (each move is undone by seven forward ones)
		int scramble = 4 + random.nextInt(4);
		for (int i = 0; i < scramble; i++) {
			int ring = random.nextInt(3);
			startTurn[ring] = Math.floorMod(startTurn[ring] - 1, 8);
			startTurn[(ring + 1) % 3] = Math.floorMod(startTurn[(ring + 1) % 3] - 1, 8);
		}
		if (startTurn[0] == target && startTurn[1] == target && startTurn[2] == target) {
			startTurn[0] = Math.floorMod(target - 1, 8);
			startTurn[1] = Math.floorMod(target - 1, 8);
		}
		System.arraycopy(startTurn, 0, turn, 0, 3);
	}

	@Override
	public Kind kind() {
		return Kind.BINTANG;
	}

	@Override
	public void build() {
		Frame frame = host.frame();
		int[] rings = host.layout().getIntArray("rings").orElse(new int[0]);
		for (int k = 0; k < 3 && k < rings.length; k++) {
			radius[k] = Math.max(1, rings[k]);
		}
		for (int k = 0; k < 3; k++) {
			// the handle sits on the ring's south point, floating a little above the floor
			Vec3 at = frame.bottom(0, 0, radius[k]).add(0.0D, 0.15D, 0.0D);
			TekaPartEntity handle = part(REntities.TEKA_CINCIN, TekaPartEntity.RING, at, 0.0F);
			if (handle != null) {
				handles[k] = handle;
			}
		}
		sync();
	}

	private void sync() {
		for (int k = 0; k < 3; k++) {
			TekaPartEntity handle = handles[k];
			if (handle == null) {
				continue;
			}
			handle.setArg(turn[k]);
			handle.setArg2((k << 8) | target | (radius[k] << 16) | (host.frame().quarter() << 24));
			handle.setState(turn[k] == target ? TekaPartEntity.LIT : (host.hinted() && suggested() == k ? TekaPartEntity.HINT : TekaPartEntity.IDLE));
		}
	}

	/** The ring the hint suggests: the first one whose turn moves the most rings toward the target. */
	private int suggested() {
		int best = 0;
		int bestScore = Integer.MIN_VALUE;
		for (int k = 0; k < 3; k++) {
			int[] t = turn.clone();
			t[k] = (t[k] + 1) % 8;
			t[(k + 1) % 3] = (t[(k + 1) % 3] + 1) % 8;
			int score = 0;
			for (int r = 0; r < 3; r++) {
				score -= Math.floorMod(target - t[r], 8);
			}
			if (score > bestScore) {
				bestScore = score;
				best = k;
			}
		}
		return best;
	}

	@Override
	public void use(ServerPlayer player, TekaPartEntity part, boolean attack) {
		if (done) {
			return;
		}
		for (int k = 0; k < 3; k++) {
			if (handles[k] == part) {
				turn[k] = (turn[k] + 1) % 8;
				turn[(k + 1) % 3] = (turn[(k + 1) % 3] + 1) % 8;
				moves++;
				fx(host.frame().centre(), RFx.RING_TURN, k, radius[k], 1.0F);
				host.level().playSound(null, part.getX(), part.getY(), part.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8F, 0.6F + 0.2F * k);
				// too many turns stirs the formation: a backlash every 24 turns (the gears overheat)
				if (moves % 24 == 0) {
					host.backlash(player, this);
				}
				sync();
				if (turn[0] == target && turn[1] == target && turn[2] == target) {
					host.level().playSound(null, part.getX(), part.getY(), part.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
					solve();
				}
				return;
			}
		}
	}

	@Override
	public void tick(long now) {
		if (!done && host.hinted() && now % 20L == 0L) {
			sync();
		}
	}

	@Override
	public void reset() {
		System.arraycopy(startTurn, 0, turn, 0, 3);
		moves = 0;
		sync();
	}

	@Override
	public float progress() {
		int aligned = 0;
		for (int k = 0; k < 3; k++) {
			if (turn[k] == target) {
				aligned++;
			}
		}
		return aligned / 3.0F;
	}

	@Override
	public Component objective() {
		return Component.translatable("kultivator.reruntuhan.teka.bintang.objective", (int) (progress() * 3), 3);
	}
}
