package dev.kultivator.client.pusakalangit.world;

import dev.kultivator.client.dampak.world.DampakWorld;
import dev.kultivator.client.dampak.world.DampakWorldBudget;
import dev.kultivator.client.epic.fx.EpicParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The client side of the planted monuments' life (DESIGN 29.6, 29.8 "monument entity extras"): the renderer notes each
 * drawn monument here once per frame; per tick this adds what an entity renderer must not (particles): the heat motes
 * (Normal and up, one per 4 ticks while hot), the dust and debris of a pull (STATE 2) and, when a pulled weapon's entity
 * is removed at the end of its pull, the white-gold column carrying it up and away (a prop continuing the rise and a light
 * pillar). Monuments not drawn for 5 s are forgotten. Client-only and visual.
 */
final class MonumentNotes {

	static final int STATE_PULLING = 2;

	private static final class Note {
		final int id;
		Vec3 anchor = Vec3.ZERO;
		Vec3 ground = Vec3.ZERO;
		String model = "";
		float scale;
		float yaw;
		boolean sword;
		int state;
		float stateAge;
		float heat;
		double seen;

		Note(int id) {
			this.id = id;
		}
	}

	private static final Map<Integer, Note> NOTES = new HashMap<>();

	/** The renderer drew monument {@code id} this frame (positions in world space). */
	static void note(int id, Vec3 anchor, Vec3 ground, String model, float scale, float yaw, boolean sword, int state, float stateAge, float heat) {
		if (NOTES.size() >= 64 && !NOTES.containsKey(id)) {
			return;
		}
		Note note = NOTES.computeIfAbsent(id, Note::new);
		note.anchor = anchor;
		note.ground = ground;
		note.model = model;
		note.scale = scale;
		note.yaw = yaw;
		note.sword = sword;
		note.state = state;
		note.stateAge = stateAge;
		note.heat = heat;
		note.seen = TitahWorld.clock();
	}

	static int count() {
		return NOTES.size();
	}

	static void clear() {
		NOTES.clear();
	}

	static void tick(ClientLevel level, double now) {
		if (NOTES.isEmpty()) {
			return;
		}
		boolean extras = DampakWorldBudget.tier() >= 1 && DampakWorldBudget.worldFx();
		RandomSource random = EpicParticles.random();
		int tick = (int) now;
		for (Iterator<Note> it = NOTES.values().iterator(); it.hasNext(); ) {
			Note note = it.next();
			if (level.getEntity(note.id) == null) {
				// removed: a finished pull goes on rising into the sky (the entity was discarded at the end of it)
				if (note.state == STATE_PULLING && note.stateAge >= 26.0F && now - note.seen < 10.0D) {
					carryAway(note);
				}
				it.remove();
				continue;
			}
			if (now - note.seen > 100.0D) {
				it.remove();
				continue;
			}
			if (now - note.seen > 2.0D) {
				// not drawn right now (behind the camera): no particles
				continue;
			}
			if (note.state == STATE_PULLING) {
				if ((tick & 1) == 0) {
					pullDust(level, note, random);
				}
				continue;
			}
			if (extras && note.heat > 0.05F && (tick & 3) == 0 && EpicParticles.grant(1, false) > 0) {
				// one heat mote per 4 ticks rising off the hot metal
				double h = note.sword ? note.scale * 0.6D * random.nextDouble() : note.scale * (random.nextDouble() * 1.5D - 0.2D);
				EpicParticles.add(note.heat > 0.5F ? ParticleTypes.LAVA : ParticleTypes.END_ROD, note.ground.x + random.nextGaussian() * 0.3D * Math.max(1.0F,
						note.scale * 0.08F), note.ground.y + Math.max(0.3D, h), note.ground.z + random.nextGaussian() * 0.3D * Math.max(1.0F, note.scale * 0.08F),
						0.0D, 0.04D, 0.0D);
			}
		}
	}

	/** The ground cracks and spits dust and stones while the weapon is worked loose. */
	private static void pullDust(ClientLevel level, Note note, RandomSource random) {
		BlockState ground = level.getBlockState(BlockPos.containing(note.ground.x, note.ground.y - 0.5D, note.ground.z));
		int n = EpicParticles.grant(DampakWorldBudget.scaled(8, true, 12), false);
		double r = Math.max(1.0D, note.scale * 0.15D);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double x = note.ground.x + Math.cos(angle) * r * random.nextDouble();
			double z = note.ground.z + Math.sin(angle) * r * random.nextDouble();
			boolean dust = i % 3 != 0 && !ground.isAir();
			EpicParticles.add(dust ? new BlockParticleOption(i % 2 == 0 ? ParticleTypes.DUST_PILLAR : ParticleTypes.BLOCK, ground) : ParticleTypes.END_ROD, x,
					note.ground.y + 0.1D, z, Math.cos(angle) * 0.12D, 0.15D + random.nextDouble() * 0.2D, Math.sin(angle) * 0.12D);
		}
		if (note.stateAge > 20.0F && (int) note.stateAge % 6 == 0) {
			DampakWorld.titahDebris(note.ground, (float) r, 2, false, null, -note.id, true, false);
		}
	}

	/** The end of a pull: the weapon continues up a white-gold column and is gone to heaven. */
	private static void carryAway(Note note) {
		long key = -1L - note.id;
		RelicFall.ascend(key, note.model, note.anchor, 90.0F, 18, note.scale, note.yaw, note.sword ? 180.0F : 0.0F, TitahWorld.WHITE_GOLD, true);
		SkyMarks.flash(key, note.ground, TitahWorld.WHITE_GOLD, 26, 220, 1.3F);
	}

	private MonumentNotes() {
	}
}
