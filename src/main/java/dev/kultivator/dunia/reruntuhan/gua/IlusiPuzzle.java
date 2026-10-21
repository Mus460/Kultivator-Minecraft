package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.alam.Alam;
import dev.kultivator.dunia.reruntuhan.ColliderEntity;
import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * P4 Labirin Ilusi (DESIGN 34.5.5): the room is a stone maze (template walls) whose true path is closed by phantom
 * walls, entities that look like the maze's stone and stop a player like stone. Pil Mata Terang ({@code Alam
 * .hasClearSight}) reveals them for everyone in the room, and so does the hint after {@code hintSeconds}; a revealed
 * phantom wall dissolves when clicked. Dead ends hold formation pulses (telegraphed, a backlash share each). Reaching
 * the exit cell solves it.
 */
final class IlusiPuzzle extends Puzzle {

	private static final int PULSE_EVERY = 70;
	private static final int PULSE_WARN = 24;
	private final List<ColliderEntity> phantoms = new ArrayList<>();
	private final List<int[]> dead = new ArrayList<>();
	private final BlockState look;
	private int[] exit = {0, -6};
	private boolean revealed;
	private long nextPulse = -1L;
	private boolean warned;

	IlusiPuzzle(PuzzleHost host) {
		super(host);
		this.look = RuinLooks.wall(host.tier());
	}

	@Override
	public Kind kind() {
		return Kind.ILUSI;
	}

	@Override
	public void build() {
		Frame frame = host.frame();
		int n = Frame.pairs(host.layout(), "walls");
		for (int i = 0; i < n; i++) {
			int[] cell = Frame.pair(host.layout(), "walls", i);
			Vec3 at = frame.bottom(cell[0], 0, cell[1]);
			ColliderEntity wall = host.spawn(REntities.TEKA_DINDING, at, 0.0F);
			if (wall == null) {
				continue;
			}
			wall.setup(0.5F, 3.0F, 0.5F, look, ColliderEntity.FLAG_PHANTOM, 0);
			wall.setOwned(true);
			wall.setClickListener((player, part, attack) -> dispel(player, wall));
			phantoms.add(wall);
		}
		int deadCount = Frame.pairs(host.layout(), "dead");
		for (int i = 0; i < deadCount; i++) {
			dead.add(Frame.pair(host.layout(), "dead", i));
		}
		int[] e = Frame.pair(host.layout(), "exit", 0);
		if (e != null) {
			exit = e;
		}
	}

	private void dispel(ServerPlayer player, ColliderEntity wall) {
		if (!wall.has(ColliderEntity.FLAG_REVEALED) || wall.isOpen()) {
			return;
		}
		wall.open();
		fx(wall.position().add(0.0D, 1.5D, 0.0D), RFx.DOOR_OPEN, 1, 0, 0.6F);
		host.level().playSound(null, wall.getX(), wall.getY(), wall.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.BLOCKS, 0.9F, 1.2F);
	}

	@Override
	public void tick(long now) {
		if (done) {
			return;
		}
		List<ServerPlayer> inside = host.challengers();
		boolean sight = host.hinted();
		for (ServerPlayer player : inside) {
			if (Alam.hasClearSight(player)) {
				sight = true;
			}
		}
		if (sight && !revealed) {
			revealed = true;
			for (ColliderEntity wall : phantoms) {
				wall.setFlag(ColliderEntity.FLAG_REVEALED, true);
				wall.setFlag(ColliderEntity.FLAG_CLICK, true);
			}
			for (ServerPlayer player : inside) {
				Msg.actionBar(player, Component.translatable("kultivator.reruntuhan.teka.ilusi.revealed").withStyle(ChatFormatting.AQUA));
			}
		}
		Vec3 goal = host.frame().bottom(exit[0], 0, exit[1]);
		for (ServerPlayer player : inside) {
			if (player.position().distanceToSqr(goal) < 1.4D * 1.4D) {
				host.level().playSound(null, goal.x, goal.y, goal.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.9F, 1.3F);
				solve();
				return;
			}
		}
		// formation pulses in the dead ends
		if (dead.isEmpty() || inside.isEmpty()) {
			return;
		}
		if (nextPulse < 0L) {
			nextPulse = now + PULSE_EVERY;
		}
		if (!warned && now >= nextPulse - PULSE_WARN) {
			warned = true;
			for (int[] cell : dead) {
				fx(host.frame().bottom(cell[0], 0, cell[1]), RFx.PULSE_WARN, PULSE_WARN, 0, 1.4F);
			}
		}
		if (now >= nextPulse) {
			warned = false;
			nextPulse = now + PULSE_EVERY;
			for (int[] cell : dead) {
				Vec3 at = host.frame().bottom(cell[0], 0, cell[1]);
				fx(at, RFx.PULSE_HIT, 0, 0, 1.4F);
				for (ServerPlayer player : inside) {
					if (player.position().distanceToSqr(at) < 1.6D * 1.6D) {
						host.backlash(player, this);
					}
				}
			}
		}
	}

	@Override
	public void started() {
		nextPulse = -1L;
	}

	@Override
	public void reset() {
		revealed = false;
		warned = false;
		nextPulse = -1L;
		for (ColliderEntity wall : phantoms) {
			wall.close();
			wall.setFlag(ColliderEntity.FLAG_REVEALED, false);
			wall.setFlag(ColliderEntity.FLAG_CLICK, false);
		}
	}

	@Override
	public void use(ServerPlayer player, TekaPartEntity part, boolean attack) {
	}

	@Override
	public float progress() {
		int open = 0;
		for (ColliderEntity wall : phantoms) {
			if (wall.isOpen()) {
				open++;
			}
		}
		return phantoms.isEmpty() ? 0.0F : open / (float) phantoms.size();
	}
}
