package dev.kultivator.client.pusakalangit.world;

import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.anim.model.AnimStep;
import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.anim.model.ModelRequest;
import dev.kultivator.client.dampak.world.DampakWorld;
import dev.kultivator.client.dampak.world.DampakWorldBudget;
import dev.kultivator.client.dampak.world.SigilRenderer;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.fx.spec.CameraShake;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Seven Stars of the Northern Dipper falling (DESIGN 29.5.3, EXTRA0 × 7 of Tujuh Bintang Biduk Utara, 6 ticks apart,
 * Tianshu → Yaoguang): each star ({@code star_core} wrapped in white fire, {@link RelicFall}) drops onto its node; its
 * impact is a small VOID ring tinted ember-violet, a short light pillar, sparks and a star crystal growing on the spot
 * (the {@code star_core} grounded plus 1 / 3 amethyst and end-rod displays at Normal / Maximal, a soft violet glow under
 * it; Minimal: particles only). The crystals stay 120 s (60 s of motes at Minimal) and are never blocks.
 * <p>
 * A star may arrive as a pre-impact TRAVEL (it falls, its impact plays on landing) and/or as its EXTRA0 (landing): the
 * second view of the same star (within 4 b and its flight time) adds nothing.
 */
final class StarFall {

	private static final int MAX_GLOWS = 14;

	private record Pending(long cast, Vec3 at, double lands) {
	}

	private static final class Glow {
		final long cast;
		final Vec3 at;
		final double born;
		final int life;

		Glow(long cast, Vec3 at, double born, int life) {
			this.cast = cast;
			this.at = at;
			this.born = born;
			this.life = life;
		}
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static final List<Pending> RECENT = new ArrayList<>();
	private static final List<Glow> GLOWS = new ArrayList<>();

	// ================================================================== API

	/** A pre-impact TRAVEL: the star falls onto {@code ground} from {@code height} b over {@code duration} ticks. */
	static void fall(ClientLevel level, TitahWorld.Cast cast, Vec3 ground, int duration, float height, int index) {
		double now = TitahWorld.clock();
		if (near(PENDING, cast.key, ground) || near(RECENT, cast.key, ground)) {
			return;
		}
		PENDING.add(new Pending(cast.key, ground, now + duration));
		TitahWorld.Cast c = cast;
		if (cast.lowLoad) {
			// server under load: no falling prop, the impact (its ring) at the landing time
			TitahWorld.schedule(duration, cast.key, () -> impact(level, c, ground, index));
			return;
		}
		RelicFall.fall(cast.key, "star_core", RelicFall.STAR, ground, Math.min(120.0F, height), duration, 1.6F, (index * 51) % 360, TitahWorld.STAR_WHITE,
				TitahWorld.VIOLET, cast.own, false, () -> impact(level, c, ground, index));
		TitahWorld.touch();
	}

	/**
	 * An EXTRA0: star {@code index} lands at {@code ground}. Already falling or landed here: nothing; else it falls now
	 * when the event names a short flight ({@code duration} 1-30), or strikes at once.
	 */
	static void land(ClientLevel level, TitahWorld.Cast cast, Vec3 ground, int index, int duration) {
		if (near(PENDING, cast.key, ground) || near(RECENT, cast.key, ground)) {
			return;
		}
		if (duration > 0 && duration <= 30) {
			fall(level, cast, ground, duration, 60.0F, index);
			return;
		}
		impact(level, cast, ground, index);
	}

	static void abort(long cast) {
		PENDING.removeIf(p -> p.cast() == cast);
	}

	static int count() {
		return PENDING.size() + GLOWS.size();
	}

	static void clear() {
		PENDING.clear();
		RECENT.clear();
		GLOWS.clear();
	}

	private static boolean near(List<Pending> list, long cast, Vec3 at) {
		for (Pending p : list) {
			if (p.cast() == cast && p.at().distanceToSqr(at) < 16.0D) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== the impact

	private static void impact(ClientLevel level, TitahWorld.Cast cast, Vec3 ground, int index) {
		double now = TitahWorld.clock();
		PENDING.removeIf(p -> p.cast() == cast.key && p.at().distanceToSqr(ground) < 16.0D);
		if (near(RECENT, cast.key, ground)) {
			return;
		}
		RECENT.add(new Pending(cast.key, ground, now));
		DampakWorld.titahRing(ground, 8.0F, "VOID", TitahWorld.EMBER_VIOLET, cast.own, 0, false);
		SkyMarks.flash(cast.key, ground, 0xE8D8FF, 12, 70, 0.6F);
		RandomSource random = EpicParticles.random();
		int n = EpicParticles.grant(DampakWorldBudget.scaled(16, cast.own, 24), false);
		for (int i = 0; i < n; i++) {
			EpicParticles.add(i % 3 == 0 ? ParticleTypes.FIREWORK : ParticleTypes.END_ROD, ground.x + random.nextGaussian() * 0.5D, ground.y + 0.4D,
					ground.z + random.nextGaussian() * 0.5D, random.nextGaussian() * 0.25D, 0.15D + random.nextDouble() * 0.35D, random.nextGaussian() * 0.25D);
		}
		if (!cast.kehancuran) {
			DampakWorld.titahDebris(ground, 3.0F, Math.max(2, DampakWorldBudget.FAKE_DEBRIS[DampakWorldBudget.index()] / 4), false, null, cast.key, cast.own,
					cast.lowLoad);
		}
		if (cast.own) {
			CameraShake.add(Math.min(0.35F, 0.2F * DampakWorldBudget.power()), 8, ground, 24.0F, false);
		}
		crystal(level, cast, ground, index);
	}

	/** The star crystal: the grounded star_core, its amethyst / end-rod displays and a soft glow (particles at Minimal). */
	private static void crystal(ClientLevel level, TitahWorld.Cast cast, Vec3 ground, int index) {
		int tier = DampakWorldBudget.index();
		int life = DampakWorldBudget.TITAH_CRYSTAL_LIFE[tier];
		boolean displays = DampakWorldBudget.displays(cast.own, cast.lowLoad) && tier >= 1;
		if (displays) {
			float scale = 1.4F;
			List<AnimStep> anims = List.of(AnimStep.of(Anim.GROW, 0, 8, 0.1F, 0.0F), AnimStep.of(Anim.SPIN, 8, life, 0.6F, 0.0F));
			ModelHandle handle = ModelFx.show(new ModelRequest("star_core", ground.add(0.0D, 0.58D * scale - 0.15D, 0.0D), scale, (index * 51) % 360, 0.0F, 0.0F,
					life, anims, -1, Vec3.ZERO, false, true, -1, FxLayer.Priority.CORE));
			if (handle != null) {
				handle.lightCycle(10, 60, index * 9);
			}
			int blocks = DampakWorldBudget.TITAH_CRYSTAL_BLOCKS[tier];
			RandomSource random = EpicParticles.random();
			for (int k = 0; k < blocks; k++) {
				double angle = Math.PI * 2.0D * k / Math.max(1, blocks) + index;
				double r = 0.9D + random.nextDouble() * 0.5D;
				double x = ground.x + Math.cos(angle) * r;
				double z = ground.z + Math.sin(angle) * r;
				double y = DampakWorld.titahGround(level, x, z, ground.y);
				BlockState state = k % 2 == 0 ? Blocks.AMETHYST_CLUSTER.defaultBlockState() : Blocks.END_ROD.defaultBlockState();
				DampakWorld.titahCrystal(level, state, new Vec3(x, y - SigilRenderer.LIFT, z), cast.key, 2 + k * 2, life);
			}
		}
		if (GLOWS.size() >= MAX_GLOWS) {
			GLOWS.remove(0);
		}
		GLOWS.add(new Glow(cast.key, ground, TitahWorld.clock(), displays ? life : Math.min(life, 1200)));
		TitahWorld.touch();
	}

	// ================================================================== tick and render

	static void tick(ClientLevel level, double now) {
		RECENT.removeIf(p -> now - p.lands() > 60.0D);
		PENDING.removeIf(p -> now - p.lands() > 40.0D);
		if (GLOWS.isEmpty()) {
			return;
		}
		RandomSource random = EpicParticles.random();
		for (Iterator<Glow> it = GLOWS.iterator(); it.hasNext(); ) {
			Glow glow = it.next();
			double age = now - glow.born;
			if (age > glow.life) {
				it.remove();
				continue;
			}
			// motes rising from the crystal (the only crystal at Minimal)
			if (((int) age & 7) == 0 && EpicParticles.grant(1, false) > 0) {
				EpicParticles.add(random.nextBoolean() ? ParticleTypes.END_ROD : EpicParticles.dust(TitahWorld.VIOLET, 1.0F), glow.at.x + random.nextGaussian() * 0.4D,
						glow.at.y + 0.6D + random.nextDouble(), glow.at.z + random.nextGaussian() * 0.4D, 0.0D, 0.03D, 0.0D);
			}
		}
	}

	static void render(SigilRenderer.Frame frame) {
		if (GLOWS.isEmpty() || DampakWorldBudget.tier() < 1) {
			return;
		}
		for (int i = 0; i < GLOWS.size(); i++) {
			Glow glow = GLOWS.get(i);
			double age = frame.clock - glow.born;
			if (age < 0.0D || frame.dist2(glow.at.x, glow.at.y, glow.at.z) > 128.0D * 128.0D || !TitahWorld.quads(1)) {
				continue;
			}
			float fade = (float) Math.min(1.0D, Math.min(age / 6.0D, (glow.life - age) / 60.0D));
			float breathe = 0.75F + 0.25F * (float) Math.sin(frame.clock * 0.08D + i);
			float flare = age < 12.0D ? 1.0F + 1.5F * (float) (1.0D - age / 12.0D) : 1.0F;
			double y = DampakWorld.titahGround(frame.level, glow.at.x, glow.at.z, glow.at.y) + SigilRenderer.LIFT + 0.02D;
			frame.flat(frame.ground, frame.rx(glow.at.x), frame.ry(y), frame.rz(glow.at.z), 2.6F * flare, (float) (frame.clock * 0.01D),
					TitahWorld.argb(Math.max(0.0F, 0.7F * fade * breathe), TitahWorld.EMBER_VIOLET));
		}
	}

	private StarFall() {
	}
}
