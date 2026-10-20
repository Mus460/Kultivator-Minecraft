package dev.kultivator.client.pusakalangit.world;

import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.anim.model.AnimStep;
import dev.kultivator.client.anim.model.ModelFx;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.anim.model.ModelRequest;
import dev.kultivator.client.dampak.world.DampakWorldBudget;
import dev.kultivator.client.dampak.world.SigilRenderer;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.technique.spec.dsl.FxLayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The decree weapons in flight (DESIGN 29.5, 29.8 "falling weapon"): the Heaven-Splitting Sword falling point-first down
 * the pillar of light, the Sea-Stabilising Pillar thrown up and streaking back down white-hot, the Mountain-Moving seal
 * rising, falling to stamp the land and lifting away, the Dipper stars (for {@link StarFall}). Each is a 3D prop
 * ({@link ModelFx}, created at its landing point so the 64 b display range holds, its height carried by the FALL step) with
 * two bloom layers drawn here: a camera-facing light trail and a soft halo; Maximal adds a halo pillar; Minimal draws a
 * particle column instead of the model. The planted monument (a real entity) takes over at the climax; the prop ends then.
 * <p>
 * Tingkat Titah (DESIGN 29.16.10): a tier 2-9 weapon (up to scale 1100, falling from up to 1024 b) is too large and too
 * high for a phantom prop: {@link FarRelic} draws it (a true-size display within render distance, an impostor or a
 * silhouette beyond it) and this class keeps only its light trail, whose widths grow with the square root of the scale.
 */
final class RelicFall {

	static final int SWORD = 0;
	static final int PILLAR = 1;
	static final int SEAL = 2;
	static final int STAR = 3;

	private static final int MAX = 24;

	private static final class Fall {
		final long cast;
		final int kind;
		/** The prop's anchor at rest (landing) and the ground point under it. */
		final Vec3 anchor;
		final Vec3 ground;
		final float height;
		final int duration;
		final float scale;
		final int rgb;
		final int trailRgb;
		final boolean own;
		final boolean hold;
		final boolean up;
		final double start;
		final @Nullable Runnable onLand;
		@Nullable ModelHandle handle;
		boolean landed;
		double endAt = Double.NaN;
		/** Lift-away: start, height and ticks (NaN = not lifting). */
		double liftStart = Double.NaN;
		float liftHeight;
		int liftTicks;

		Fall(long cast, int kind, Vec3 anchor, Vec3 ground, float height, int duration, float scale, int rgb, int trailRgb, boolean own, boolean hold,
			 boolean up, double start, @Nullable Runnable onLand) {
			this.cast = cast;
			this.kind = kind;
			this.anchor = anchor;
			this.ground = ground;
			this.height = height;
			this.duration = Math.max(1, duration);
			this.scale = scale;
			this.rgb = rgb;
			this.trailRgb = trailRgb;
			this.own = own;
			this.hold = hold;
			this.up = up;
			this.start = start;
			this.onLand = onLand;
		}

		/** 0..1 of the flight at time {@code now}. */
		float progress(double now) {
			return (float) Math.max(0.0D, Math.min(1.0D, (now - start) / duration));
		}

		/** The anchor's world position at {@code now} (the FALL step's easeInQuad drop, or the ascent's p²). */
		Vec3 at(double now) {
			float p = progress(now);
			if (up) {
				return anchor.add(0.0D, height * p * p, 0.0D);
			}
			Vec3 base = anchor.add(0.0D, height * (1.0F - p * p), 0.0D);
			if (!Double.isNaN(liftStart)) {
				float q = (float) Math.max(0.0D, Math.min(1.0D, (now - liftStart) / Math.max(1, liftTicks)));
				base = base.add(0.0D, liftHeight * q * q, 0.0D);
			}
			return base;
		}

		/** The lowest point of the shown model (where the trail meets it) below the anchor. */
		double tipBelow() {
			return switch (kind) {
				case SWORD -> 1.47D * scale;
				case PILLAR -> 1.5D * scale;
				case STAR -> 0.58D * scale;
				default -> 0.0D;
			};
		}

		/** The height of the model above its anchor (the trail starts at its top when it flies up). */
		double topAbove() {
			return switch (kind) {
				case SWORD -> 0.52D * scale;
				case PILLAR -> 1.5D * scale;
				case STAR -> 0.58D * scale;
				default -> 1.1D * scale;
			};
		}
	}

	private static final List<Fall> FALLS = new ArrayList<>();

	// ================================================================== API

	/**
	 * A weapon falls onto {@code ground} from {@code height} b over {@code duration} ticks. {@code hold} keeps the prop on
	 * the ground after landing (the seal until {@link #liftAway}); otherwise it ends 4 ticks after landing (the monument
	 * entity stands there from the climax). {@code onLand} runs once when it lands on this client.
	 */
	static void fall(long cast, String model, int kind, Vec3 ground, float height, int duration, float scale, float yaw, int rgb, int trailRgb, boolean own,
					 boolean hold, @Nullable Runnable onLand) {
		fall(cast, model, kind, ground, height, duration, scale, yaw, rgb, trailRgb, own, hold, onLand, 1);
	}

	/** {@link #fall} of a decree of Tingkat {@code tier}: from tier 2 the weapon itself is a {@link FarRelic}. */
	static void fall(long cast, String model, int kind, Vec3 ground, float height, int duration, float scale, float yaw, int rgb, int trailRgb, boolean own,
					 boolean hold, @Nullable Runnable onLand, int tier) {
		if (!TitahWorld.finite(ground) || FALLS.size() >= MAX) {
			return;
		}
		Vec3 anchor = switch (kind) {
			// planted point-first, a quarter of the blade in the ground
			case SWORD -> ground.add(0.0D, 1.47D * scale * 0.75D, 0.0D);
			// upright, sunk 25 % (the anchor is its middle)
			case PILLAR -> ground.add(0.0D, 0.75D * scale, 0.0D);
			case STAR -> ground.add(0.0D, 0.58D * scale, 0.0D);
			default -> ground.add(0.0D, 0.02D, 0.0D);
		};
		Fall f = new Fall(cast, kind, anchor, ground, Math.max(4.0F, height), duration, scale, rgb, trailRgb, own, hold, false, TitahWorld.clock(), onLand);
		boolean far = tier >= 2 && kind != STAR && FarRelic.falling(cast, model, kind, ground, f.height, f.duration, scale, yaw, hold, tier);
		if (!far && DampakWorldBudget.tier() >= 1 && DampakWorldBudget.worldFx()) {
			List<AnimStep> anims = new ArrayList<>(2);
			anims.add(AnimStep.of(Anim.FALL, 0, f.duration, f.height, 0.0F));
			if (kind == SEAL || kind == STAR) {
				anims.add(AnimStep.of(Anim.STAMP, f.duration, 2, 0.0F, 0.0F));
			}
			if (kind == STAR) {
				anims.add(AnimStep.of(Anim.SPIN, 0, f.duration + 4, 9.0F, 0.0F));
			}
			int life = hold ? f.duration + 400 : f.duration + 8;
			float roll = kind == SWORD ? 180.0F : 0.0F;
			f.handle = ModelFx.show(new ModelRequest(model, anchor, scale, yaw, 0.0F, roll, life, anims, -1, Vec3.ZERO, false, true, -1,
					FxLayer.Priority.CORE));
		}
		FALLS.add(f);
		TitahWorld.touch();
	}

	/**
	 * A weapon thrown or raised straight up from {@code from} by {@code up} b over {@code ticks} (accelerating), with its
	 * trail; it ends at the top (the decree's weapon has gone to heaven).
	 */
	static void ascend(long cast, String model, Vec3 from, float up, int ticks, float scale, int trailRgb, boolean own) {
		ascend(cast, model, from, up, ticks, scale, 0.0F, 0.0F, trailRgb, own);
	}

	/** {@link #ascend} turned by {@code yaw} and {@code roll} (a planted sword pulled out rises point-down). */
	static void ascend(long cast, String model, Vec3 from, float up, int ticks, float scale, float yaw, float roll, int trailRgb, boolean own) {
		if (!TitahWorld.finite(from) || FALLS.size() >= MAX) {
			return;
		}
		Fall f = new Fall(cast, PILLAR, from, from, up, ticks, scale, 0xFFFFFF, trailRgb, own, false, true, TitahWorld.clock(), null);
		if (DampakWorldBudget.tier() >= 1 && DampakWorldBudget.worldFx()) {
			// SLIDE b = 2: an accelerating move that keeps the prop's own turn (the same p² as the trail)
			f.handle = ModelFx.show(new ModelRequest(model, from, scale, yaw, 0.0F, roll, f.duration + 2,
					List.of(new AnimStep(Anim.SLIDE, 0, f.duration, 0.0F, 2.0F, from.add(0.0D, up, 0.0D))), -1, Vec3.ZERO, false, true, -1,
					FxLayer.Priority.CORE));
		}
		FALLS.add(f);
		TitahWorld.touch();
		burst(from, ParticleTypes.CLOUD, 8, 0.15F);
	}

	/** The cast's climax: its falling weapon has landed (the monument entity takes over; a held seal stays). */
	static void land(long cast) {
		FarRelic.land(cast);
		double now = TitahWorld.clock();
		for (Fall f : FALLS) {
			if (f.cast == cast && !f.up && !f.landed) {
				landNow(f, now);
			}
		}
	}

	/** A held prop (the seal) rises {@code up} b over {@code ticks} and fades. */
	static void liftAway(long cast, float up, int ticks) {
		FarRelic.liftAway(cast, up, ticks);
		double now = TitahWorld.clock();
		for (Fall f : FALLS) {
			if (f.cast == cast && f.hold && Double.isNaN(f.liftStart)) {
				f.liftStart = now;
				f.liftHeight = up;
				f.liftTicks = Math.max(4, ticks);
				f.endAt = now + f.liftTicks;
				if (f.handle != null) {
					f.handle.moveTo(f.anchor.add(0.0D, up, 0.0D), f.liftTicks);
					f.handle.end(f.liftTicks);
				}
				burst(f.ground.add(0.0D, 0.5D, 0.0D), ParticleTypes.CLOUD, 10, 0.2F);
			}
		}
	}

	static void abort(long cast) {
		FarRelic.abort(cast);
		for (Fall f : FALLS) {
			if (f.cast == cast) {
				if (f.handle != null) {
					f.handle.end(6);
				}
				f.endAt = TitahWorld.clock();
			}
		}
	}

	static int count() {
		return FALLS.size();
	}

	static void clear() {
		for (Fall f : FALLS) {
			if (f.handle != null) {
				f.handle.end(2);
			}
		}
		FALLS.clear();
	}

	// ================================================================== tick

	private static void landNow(Fall f, double now) {
		f.landed = true;
		if (f.onLand != null) {
			try {
				f.onLand.run();
			} catch (RuntimeException exception) {
				TitahWorld.warn("land", exception);
			}
		}
		if (!f.hold) {
			f.endAt = now + 4.0D;
			if (f.handle != null) {
				f.handle.end(6);
			}
		}
		burst(f.ground.add(0.0D, 0.6D, 0.0D), f.kind == STAR ? ParticleTypes.END_ROD : ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | f.rgb),
				f.kind == STAR ? 10 : 1, 0.25F);
	}

	static void tick(ClientLevel level, double now) {
		if (FALLS.isEmpty()) {
			return;
		}
		boolean minimal = DampakWorldBudget.tier() <= 0;
		RandomSource random = EpicParticles.random();
		for (Iterator<Fall> it = FALLS.iterator(); it.hasNext(); ) {
			Fall f = it.next();
			if (!Double.isNaN(f.endAt) && now >= f.endAt) {
				it.remove();
				continue;
			}
			if (f.hold && f.landed && Double.isNaN(f.liftStart) && now - f.start > f.duration + 400) {
				it.remove();
				continue;
			}
			float p = f.progress(now);
			if (!f.landed && p >= 1.0F) {
				if (f.up) {
					// gone into the sky
					f.landed = true;
					f.endAt = now + 2.0D;
					if (f.handle != null) {
						f.handle.end(2);
					}
					burst(f.at(now), ParticleTypes.END_ROD, 6, 0.2F);
				} else {
					landNow(f, now);
				}
				continue;
			}
			if (f.landed) {
				continue;
			}
			Vec3 at = f.at(now);
			// the trail's particles: white fire round a star, embers off the pillar, gold motes off the sword and seal
			int n = EpicParticles.grant(minimal || f.handle == null ? 6 : 2, false);
			ParticleOptions particle = switch (f.kind) {
				case STAR -> random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.FIREWORK;
				case PILLAR -> random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME;
				default -> random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.GLOW;
			};
			double tip = f.up ? -0.2D : -f.tipBelow();
			for (int i = 0; i < n; i++) {
				double y = at.y + tip + (f.up ? -random.nextDouble() * 6.0D : random.nextDouble() * Math.min(12.0D, f.height * 0.2D));
				EpicParticles.add(particle, at.x + random.nextGaussian() * 0.3D * Math.max(1.0F, f.scale * 0.1F), y,
						at.z + random.nextGaussian() * 0.3D * Math.max(1.0F, f.scale * 0.1F), 0.0D, f.up ? -0.05D : 0.05D, 0.0D, true);
			}
		}
	}

	private static void burst(Vec3 at, ParticleOptions particle, int wanted, float speed) {
		int n = EpicParticles.grant(wanted, false);
		RandomSource random = EpicParticles.random();
		for (int i = 0; i < n; i++) {
			EpicParticles.add(particle, at.x + random.nextGaussian() * 0.4D, at.y + random.nextDouble() * 0.6D, at.z + random.nextGaussian() * 0.4D,
					random.nextGaussian() * speed, Math.abs(random.nextGaussian()) * speed, random.nextGaussian() * speed);
		}
	}

	// ================================================================== render: the light trail, the halo, the halo pillar

	static void render(SigilRenderer.Frame frame) {
		if (FALLS.isEmpty() || DampakWorldBudget.tier() < 1) {
			return;
		}
		double range = TitahWorld.viewRange() + 64.0D;
		double far = TitahWorld.viewRange() - 24.0D;
		boolean maximal = DampakWorldBudget.tier() >= 2;
		for (int i = 0; i < FALLS.size(); i++) {
			Fall f = FALLS.get(i);
			if (f.landed && !(f.hold && !Double.isNaN(f.liftStart))) {
				continue;
			}
			Vec3 at = f.at(frame.clock);
			if (frame.dist2(at.x, at.y, at.z) > range * range) {
				// a giant weapon falling far away: its trail as the impostor's, projected inside render distance
				if (f.scale > 24.0F && frame.dist2(at.x, at.y, at.z) <= 16.0D * range * range) {
					farTrail(frame, f, at, far / Math.sqrt(frame.dist2(at.x, at.y, at.z)));
				}
				continue;
			}
			if (!TitahWorld.quads(4)) {
				return;
			}
			float p = f.progress(frame.clock);
			float fadeIn = Math.min(1.0F, p * 6.0F + 0.15F);
			// the trail runs from the weapon's leading end back along its path (up while it falls, down while it rises)
			double lead = f.up ? f.topAbove() : -f.tipBelow();
			// a giant weapon (Tingkat Titah) has a longer, wider trail: the caps grow with sqrt(scale / 12)
			float giant = (float) Math.max(1.0D, Math.sqrt(f.scale / 12.0D));
			double tailLength = Math.min((f.up ? 30.0D : 48.0D) * giant, Math.max(6.0D, f.height * (f.up ? 0.35D : 0.5D) * (0.3D + p)));
			float ax = frame.rx(at.x);
			float az = frame.rz(at.z);
			float ay = frame.ry(at.y + lead * 0.6D);
			float by = frame.ry(at.y + (f.up ? -tailLength : tailLength));
			float width = Math.max(0.8F, Math.min(6.0F * giant, f.scale * (f.kind == SEAL ? 0.5F : 0.22F)));
			int head = TitahWorld.argb(0.85F * fadeIn, f.trailRgb);
			int tail = TitahWorld.argb(0.0F, f.trailRgb);
			// the heat-haze / light trail (glow strip) and its white core
			frame.beam(frame.glow, ax, ay, az, ax, by, az, width * 2.2F, head, tail);
			frame.beam(frame.lightning, ax, ay, az, ax, frame.ry(at.y + (f.up ? -tailLength : tailLength) * 0.7D), az, Math.max(0.15F, width * 0.18F),
					TitahWorld.argb(0.9F * fadeIn, f.rgb), TitahWorld.argb(0.0F, f.rgb));
			// the halo round the leading end
			float halo = Math.max(1.5F, Math.min(10.0F * giant, f.scale * (f.kind == STAR ? 1.2F : 0.45F)));
			frame.billboard(frame.ground, ax, frame.ry(at.y + lead), az, halo, TitahWorld.argb(0.7F * fadeIn, f.rgb));
			if (maximal && !f.up && f.kind != SEAL) {
				// the halo pillar over the falling weapon
				frame.pillar(at.x, at.y + f.topAbove(), at.z, (int) Math.min(64.0D, tailLength), f.trailRgb, Math.max(0.3F, f.scale * 0.06F));
			}
		}
	}

	/** The trail of a giant fall beyond render distance, scaled by {@code k} about the camera (one glow strip). */
	private static void farTrail(SigilRenderer.Frame frame, Fall f, Vec3 at, double k) {
		if (f.up || !(k > 0.0D) || !TitahWorld.quads(1)) {
			return;
		}
		float p = f.progress(frame.clock);
		double giant = Math.sqrt(f.scale / 12.0D);
		double tail = Math.min(48.0D * giant, Math.max(6.0D, f.height * 0.5D * (0.3D + p)));
		float ax = (float) (frame.rx(at.x) * k);
		float ay = (float) (frame.ry(at.y - f.tipBelow()) * k);
		float az = (float) (frame.rz(at.z) * k);
		float by = (float) (frame.ry(at.y + tail) * k);
		float width = (float) Math.max(0.3D, Math.min(6.0D * giant, f.scale * 0.22D) * 2.2D * k);
		frame.beam(frame.glow, ax, ay, az, ax, by, az, width, TitahWorld.argb(0.8F, f.trailRgb), TitahWorld.argb(0.0F, f.trailRgb));
	}

	private RelicFall() {
	}
}
