package dev.kultivator.dunia.reruntuhan.gua;

import dev.kultivator.dunia.reruntuhan.Frame;
import dev.kultivator.dunia.reruntuhan.Glyphs;
import dev.kultivator.dunia.reruntuhan.REntities;
import dev.kultivator.dunia.reruntuhan.RFx;
import dev.kultivator.dunia.reruntuhan.TekaPartEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * P1 Delapan Trigram (DESIGN 34.5.5): eight floor plates on an octagon (radius 4), each carved with a trigram placed by
 * the room's seed. The stele's riddle gives the Early Heaven order (Heaven, Lake, Fire, Thunder, Wind, Water, Mountain,
 * Earth); stepping on the plates in that order lights each glyph; a wrong plate is a backlash and every plate goes
 * dark. Plates are stepped on, never clicked, so the check reads each challenger's feet every tick (eight plates).
 */
final class BaguaPuzzle extends Puzzle {

	private static final int[][] PADS = {{4, 0}, {3, 3}, {0, 4}, {-3, 3}, {-4, 0}, {-3, -3}, {0, -4}, {3, -3}};
	private final TekaPartEntity[] plates = new TekaPartEntity[8];
	/** trigram (0-7) of plate i. */
	private final int[] glyph = new int[8];
	private int next;
	private final Map<UUID, Integer> standing = new HashMap<>();

	BaguaPuzzle(PuzzleHost host) {
		super(host);
		for (int i = 0; i < 8; i++) {
			glyph[i] = i;
		}
		for (int i = 7; i > 0; i--) {
			int k = random.nextInt(i + 1);
			int t = glyph[i];
			glyph[i] = glyph[k];
			glyph[k] = t;
		}
	}

	@Override
	public Kind kind() {
		return Kind.BAGUA;
	}

	@Override
	public void build() {
		Frame frame = host.frame();
		int[] pads = host.layout().getIntArray("pads").orElse(new int[0]);
		for (int i = 0; i < 8; i++) {
			int dx = pads.length >= (i + 1) * 3 ? pads[i * 3] : PADS[i][0];
			int dz = pads.length >= (i + 1) * 3 ? pads[i * 3 + 2] : PADS[i][1];
			// the pad block is the floor (anchor y - 1): the plate lies on top of it
			Vec3 at = frame.bottom(dx, 0, dz).add(0.0D, 0.01D, 0.0D);
			TekaPartEntity plate = part(REntities.TEKA_PLAT, TekaPartEntity.PLATE, at, 0.0F);
			if (plate != null) {
				plate.setArg(glyph[i]);
				plate.setColor(Glyphs.TRIGRAM_RGB[glyph[i]]);
				plates[i] = plate;
			}
		}
	}

	@Override
	public void tick(long now) {
		if (done) {
			return;
		}
		for (ServerPlayer player : host.challengers()) {
			int on = plateUnder(player);
			Integer was = standing.get(player.getUUID());
			if (on < 0) {
				standing.remove(player.getUUID());
				continue;
			}
			if (was != null && was == on) {
				continue;
			}
			standing.put(player.getUUID(), on);
			step(player, on);
			if (done) {
				return;
			}
		}
		if (host.hinted() && next < 8) {
			for (int i = 0; i < 8; i++) {
				TekaPartEntity plate = plates[i];
				if (plate != null && plate.state() != TekaPartEntity.LIT) {
					plate.setState(glyph[i] == next ? TekaPartEntity.HINT : TekaPartEntity.IDLE);
				}
			}
		}
	}

	private int plateUnder(ServerPlayer player) {
		if (!player.onGround()) {
			return -1;
		}
		for (int i = 0; i < 8; i++) {
			TekaPartEntity plate = plates[i];
			if (plate == null || plate.isRemoved()) {
				continue;
			}
			double dx = player.getX() - plate.getX();
			double dz = player.getZ() - plate.getZ();
			double dy = player.getY() - plate.getY();
			if (dx * dx + dz * dz <= 0.62D * 0.62D && dy > -0.3D && dy < 1.0D) {
				return i;
			}
		}
		return -1;
	}

	private void step(ServerPlayer player, int i) {
		TekaPartEntity plate = plates[i];
		if (plate == null || plate.state() == TekaPartEntity.LIT) {
			return;
		}
		if (glyph[i] == next) {
			plate.setState(TekaPartEntity.LIT);
			next++;
			fx(plate.position(), RFx.PLATE_LIT, glyph[i], next, 1.0F);
			host.level().playSound(null, plate.getX(), plate.getY(), plate.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
					1.0F, 0.6F + next * 0.1F);
			if (next >= 8) {
				solve();
			}
		} else {
			plate.setState(TekaPartEntity.WRONG);
			host.level().playSound(null, plate.getX(), plate.getY(), plate.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.8F, 0.5F);
			wrong(player);
		}
	}

	@Override
	public void reset() {
		next = 0;
		for (TekaPartEntity plate : plates) {
			if (plate != null) {
				plate.setState(TekaPartEntity.IDLE);
			}
		}
	}

	@Override
	public float progress() {
		return next / 8.0F;
	}
}
