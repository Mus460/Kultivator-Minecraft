package dev.kultivator.client.dampak.world;

import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.anim.model.AnimStep;
import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.anim.model.ModelRequest;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Monumen (DESIGN 28.10): a lingering giant prop at the impact, a client-only {@code ModelFx} prop of an existing
 * {@code kultivator:fx/<id>} model ({@code casterId} −1 so ABORT does not end it, glow on, CORE priority), posed by
 * {@code Dampak.pose}: PLANTED (tilted 8-12°, sunk 20 %, falling in with a stamp), FLAT (lying on the ground), UPRIGHT
 * (standing, turning 0.3°/t), HOVER (2 b up, a slow bob). It grows in from 0.6, breathes by light, cools by light over
 * its life and leaves by one 20-tick fade after {@code Dampak.monumentSeconds}. Caps 1 / 2 / 4 alive (all casters; the
 * oldest ends first); ended early when the camera is more than 128 b away or the dimension changes. No real block is ever
 * left behind.
 */
final class Monument {

	private static final int END_FADE = 20;
	private static final double FAR = 128.0D;

	private static final List<Live> LIVE = new ArrayList<>();

	private static final class Live {
		final ModelHandle handle;
		final Vec3 pos;
		final int life;
		int age;
		boolean cooling;

		Live(ModelHandle handle, Vec3 pos, int life) {
			this.handle = handle;
			this.pos = pos;
			this.life = life;
		}
	}

	/**
	 * Raises a monument of model {@code id} at {@code impact} (the ground there is read on the client), facing
	 * {@code yaw}; {@code pose} is a {@code Dampak.MonumentPose} name.
	 */
	static boolean raise(ClientLevel level, @Nullable String id, Vec3 impact, float scale, int seconds, @Nullable String pose, float yaw, int seed,
						 boolean own) {
		if (id == null || id.isEmpty() || !DampakWorldBudget.worldFx() || DampakWorldBudget.othersFactor(own) <= 0.0F) {
			return false;
		}
		if (!ModelFx.known(id)) {
			return false;
		}
		int cap = DampakWorldBudget.MONUMENTS[DampakWorldBudget.index()];
		prune();
		while (LIVE.size() >= cap && !LIVE.isEmpty()) {
			LIVE.remove(0).handle.end(END_FADE);
		}
		int life = Math.max(30, Math.min(180, seconds <= 0 ? 60 : seconds)) * 20;
		double ground = DampakDisplays.ground(level, impact.x, impact.z, impact.y);
		float size = Math.max(0.5F, Math.min(40.0F, scale <= 0.0F ? 1.0F : scale));
		String kind = pose == null ? "UPRIGHT" : pose;
		float pitch = 0.0F;
		float roll = 0.0F;
		Vec3 pos;
		List<AnimStep> anims = new ArrayList<>(4);
		switch (kind) {
			case "PLANTED" -> {
				// tilted 8-12 degrees, sunk 20 % of its height into the ground, falling in with a stamp
				roll = (8.0F + Math.floorMod(seed, 5)) * (Math.floorMod(seed, 2) == 0 ? 1.0F : -1.0F);
				pitch = (Math.floorMod(seed >> 3, 7) - 3) * 1.0F;
				pos = new Vec3(impact.x, ground - size * 0.2D, impact.z);
				anims.add(AnimStep.of(Anim.FALL, 0, 10, Math.min(24.0F, 6.0F + size), 0.0F));
				anims.add(AnimStep.of(Anim.STAMP, 10, 2, 0.0F, 0.0F));
			}
			case "FLAT" -> {
				pos = new Vec3(impact.x, ground + 0.05D, impact.z);
				anims.add(AnimStep.of(Anim.GROW, 0, 12, 0.6F, 0.0F));
				anims.add(AnimStep.of(Anim.STAMP, 12, 2, 0.0F, 0.0F));
			}
			case "HOVER" -> {
				pos = new Vec3(impact.x, ground + 2.0D, impact.z);
				anims.add(AnimStep.of(Anim.GROW, 0, 12, 0.6F, 0.0F));
				anims.add(AnimStep.of(Anim.BOB, 12, life, 0.2F, 80.0F));
			}
			default -> {
				pos = new Vec3(impact.x, ground, impact.z);
				anims.add(AnimStep.of(Anim.GROW, 0, 12, 0.6F, 0.0F));
				anims.add(AnimStep.of(Anim.SPIN, 12, life, 0.3F, 0.0F));
			}
		}
		// the prop's own end comes after ours, so the one 20-tick fade below is the exit
		ModelRequest request = new ModelRequest(id, pos, size, yaw, pitch, roll, life + END_FADE * 3, anims, -1, Vec3.ZERO, false, true, -1,
				FxLayer.Priority.CORE);
		ModelHandle handle = ModelFx.show(request);
		if (handle == null) {
			return false;
		}
		handle.lightCycle(9, 80, Math.floorMod(seed, 80));
		LIVE.add(new Live(handle, pos, life));
		DampakParticles.burst(level, ParticleTypes.END_ROD, pos.add(0.0D, 0.5D, 0.0D), 12, 0.15F);
		return true;
	}

	static void tick(ClientLevel level) {
		if (LIVE.isEmpty()) {
			return;
		}
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		for (Iterator<Live> it = LIVE.iterator(); it.hasNext(); ) {
			Live live = it.next();
			if (!live.handle.alive()) {
				it.remove();
				continue;
			}
			live.age++;
			if (camera.distanceToSqr(live.pos) > FAR * FAR || live.age >= live.life) {
				live.handle.end(END_FADE);
				it.remove();
				continue;
			}
			if (!live.cooling && live.age >= live.life / 2) {
				// the second half: the breathing glow cools down
				live.cooling = true;
				live.handle.lightTo(6, live.life - live.age);
			}
			if (live.age % 40 == 0 && live.age < 400) {
				DampakParticles.burst(level, ParticleTypes.END_ROD, live.pos.add(0.0D, 1.0D, 0.0D), 2, 0.03F);
			}
		}
	}

	private static void prune() {
		LIVE.removeIf(live -> !live.handle.alive());
	}

	static int count() {
		prune();
		return LIVE.size();
	}

	/** Ends every monument (dimension change, disconnect, stop). */
	static void clear() {
		for (Live live : LIVE) {
			live.handle.end(4);
		}
		LIVE.clear();
	}

	private Monument() {
	}
}
