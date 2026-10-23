package dev.kultivator.client.sekte.langit;

import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The one-off effects of the sky sect ({@link LangitPayloads.Fx}, ops {@link LangitIds} {@code FX_*}; DESIGN 37.6.5,
 * 37.7.3). Each is a few layered quads for a few seconds plus a short burst of vanilla or qi particles from the particle
 * budget; none is an entity, a LightningBolt or a block:
 * <ul>
 * <li>{@code FX_PILAR} 0: the tether pillar while a sky site is tethered, a column of rising light from the ground stele
 * to the Cloud Throne (kept while the synced hall is tethered, else 30 s after the last packet), motes climbing it;</li>
 * <li>{@code FX_ARRIVE} 1: a disciple steps out of a billow of cloud;</li>
 * <li>{@code FX_TANGKAP} 2: the Cloud Net flashes open under the faller and a soft ring blooms where it sets them down;</li>
 * <li>{@code FX_PINDAH} 3: a blue-white pillar and a ring of turning clouds at both ends of a Cloud Transfer;</li>
 * <li>{@code FX_FERI} 4: the cloud ferry's puffs;</li>
 * <li>{@code FX_NAIK} 5: the Ascension: nine rings in the colours of the Nine Heavens (gold, orange, red, pink, lilac,
 * violet, blue, jade, white) rise 40 blocks from the throne one after another over 8 s round a white-gold pillar;</li>
 * <li>{@code FX_BAJAK} 6: a pirate hull tears up through the cloud sea: a dark ring of billows thrown outwards;</li>
 * <li>{@code FX_SENGSARA_BAWAH} 7: a tribulation far below, seen from the island: a dark vortex turning in the cloud sea
 * over it (20 s after the last strike), and with each strike a bolt of light bursting up through the clouds and a
 * muffled thunder.</li>
 * </ul>
 * Test ops of 37G's {@code /kultivasi uji langit_sekte}: {@link #OP_TEST_HALO} shows the halo for 60 s,
 * {@link #OP_TEST_FESTIVAL} the festival lanterns for 60 s.
 */
public final class LangitFxClient {

	/** Test: the Buddha's Light halo for 60 s (sent by 37G {@code uji langit_sekte halo}). */
	public static final int OP_TEST_HALO = 100;
	/** Test: festival lanterns for 60 s (sent by 37G {@code uji langit_sekte pesta}). */
	public static final int OP_TEST_FESTIVAL = 101;

	private static final int MAX_ACTIVE = 24;
	private static final double VIEW = 256.0D;
	private static final int[] NINE = {0xFFD54A, 0xFF9A3C, 0xF0403A, 0xFF8FB8, 0xD8B8FF, 0x9A6CFF, 0x5A8CFF, 0x4FE0A8, 0xFFFFFF};

	private static final AwanSink RING = new AwanSink(AwanSink.RING, false);
	private static final AwanSink PUFF = new AwanSink(AwanSink.CLOUD_PUFF, false);
	private static final AwanSink PILLAR = new AwanSink(AwanSink.PILAR, false);
	private static final AwanSink NET = new AwanSink(AwanSink.JARING, false);
	private static final AwanSink VORTEX = new AwanSink(AwanSink.PUSARAN, false);
	private static final AwanSink GLOW = new AwanSink(AwanSink.GATE_GLOW, false);
	private static final AwanSink BOLT = new AwanSink(AwanSink.KILAT, true);
	private static final AwanSink FLASH = new AwanSink(AwanSink.GATE_GLOW, true);
	static final AwanSink[] SINKS = {VORTEX, RING, PUFF, NET, PILLAR, GLOW};
	static final AwanSink[] ADDITIVE = {BOLT, FLASH};

	private static final class Active {
		final int op;
		final BlockPos a;
		final BlockPos b;
		float start;
		float duration;
		/** The last strike (tribulation) in seconds. */
		float strike;

		Active(int op, BlockPos a, BlockPos b, float start, float duration) {
			this.op = op;
			this.a = a;
			this.b = b;
			this.start = start;
			this.duration = duration;
			this.strike = start;
		}
	}

	private static final List<Active> ACTIVE = new ArrayList<>();

	private static float now() {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? 0.0F : (float) (level.getGameTime() % 240000L) / 20.0F;
	}

	/** The {@link LangitPayloads.Fx} receiver (client thread). */
	static void onFx(LangitPayloads.Fx fx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		int op = fx.op();
		if (op == OP_TEST_HALO) {
			HaloPelangi.force(1200);
			return;
		}
		if (op == OP_TEST_FESTIVAL) {
			LenteraLangit.force(1200);
			return;
		}
		float t = now();
		// a repeated pillar or vortex refreshes the one already there
		for (Active active : ACTIVE) {
			if (active.op == op && (op == LangitIds.FX_PILAR && active.b.equals(fx.b())
					|| op == LangitIds.FX_SENGSARA_BAWAH && active.a.distManhattan(fx.a()) <= 24)) {
				active.start = op == LangitIds.FX_PILAR ? active.start : t;
				active.duration = op == LangitIds.FX_PILAR ? t - active.start + 30.0F : 20.0F;
				active.strike = t;
				strikeSound(op, fx.a());
				return;
			}
		}
		float duration = switch (op) {
			case LangitIds.FX_PILAR -> 30.0F;
			case LangitIds.FX_ARRIVE -> 1.6F;
			case LangitIds.FX_TANGKAP -> 1.4F;
			case LangitIds.FX_PINDAH -> 2.2F;
			case LangitIds.FX_FERI -> 1.2F;
			case LangitIds.FX_NAIK -> 9.0F;
			case LangitIds.FX_BAJAK -> 3.5F;
			case LangitIds.FX_SENGSARA_BAWAH -> 20.0F;
			default -> 0.0F;
		};
		if (duration <= 0.0F) {
			return;
		}
		if (ACTIVE.size() >= MAX_ACTIVE) {
			ACTIVE.remove(0);
		}
		ACTIVE.add(new Active(op, fx.a(), fx.b(), t, duration));
		burst(level, op, fx.a(), fx.b());
		LangitSuara.fx(level, op, fx.a(), fx.b());
	}

	private static void strikeSound(int op, BlockPos a) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && op == LangitIds.FX_SENGSARA_BAWAH) {
			LangitSuara.fx(level, op, a, a);
		}
	}

	/** The particle burst of a new effect (vanilla and qi particles, the particle budget, DETAIL priority). */
	private static void burst(ClientLevel level, int op, BlockPos a, BlockPos b) {
		int tier = LangitBudget.particleTier(Minecraft.getInstance(), Math.max(0, LangitBudget.tier()));
		RandomSource random = level.getRandom();
		switch (op) {
			case LangitIds.FX_ARRIVE, LangitIds.FX_FERI -> cloudRing(level, random, a, 1.0D, LangitBudget.particles(op == LangitIds.FX_ARRIVE ? 14 : 8, tier));
			case LangitIds.FX_TANGKAP -> {
				cloudRing(level, random, a, 1.5D, LangitBudget.particles(10, tier));
				cloudRing(level, random, b, 1.0D, LangitBudget.particles(8, tier));
			}
			case LangitIds.FX_PINDAH -> {
				qiRing(level, random, a, 0xCFE8FF, LangitBudget.particles(12, tier));
				qiRing(level, random, b, 0xCFE8FF, LangitBudget.particles(12, tier));
			}
			case LangitIds.FX_NAIK -> {
				int n = LangitBudget.particles(40, tier);
				for (int i = 0; i < n && Qi.on(); i++) {
					double ang = random.nextDouble() * Math.PI * 2.0D;
					double r = 1.0D + random.nextDouble() * 6.0D;
					level.addParticle(Qi.glint(NINE[i % NINE.length], 0.35F, 60), a.getX() + 0.5D + Math.cos(ang) * r, a.getY() + 1.0D,
							a.getZ() + 0.5D + Math.sin(ang) * r, 0.0D, 0.12D + random.nextDouble() * 0.1D, 0.0D);
				}
			}
			case LangitIds.FX_BAJAK -> cloudRing(level, random, a, 4.0D, LangitBudget.particles(24, tier));
			default -> {
			}
		}
	}

	private static void cloudRing(ClientLevel level, RandomSource random, BlockPos at, double radius, int n) {
		for (int i = 0; i < n; i++) {
			double ang = Math.PI * 2.0D * i / Math.max(1, n);
			double x = at.getX() + 0.5D + Math.cos(ang) * radius;
			double z = at.getZ() + 0.5D + Math.sin(ang) * radius;
			level.addParticle(ParticleTypes.CLOUD, x, at.getY() + 0.6D + random.nextDouble() * 0.6D, z, Math.cos(ang) * 0.08D, 0.02D,
					Math.sin(ang) * 0.08D);
		}
	}

	private static void qiRing(ClientLevel level, RandomSource random, BlockPos at, int rgb, int n) {
		if (!Qi.on()) {
			return;
		}
		for (int i = 0; i < n; i++) {
			double ang = Math.PI * 2.0D * i / Math.max(1, n);
			level.addParticle(Qi.mote(rgb, 0.3F, 30), at.getX() + 0.5D + Math.cos(ang) * 1.8D, at.getY() + 0.3D, at.getZ() + 0.5D + Math.sin(ang) * 1.8D,
					0.0D, 0.08D + random.nextDouble() * 0.05D, 0.0D);
		}
	}

	/** Motes climbing the tether pillars near the camera (every 5 ticks). */
	static void tick(ClientLevel level, double camX, double camY, double camZ) {
		float t = now();
		Iterator<Active> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Active active = it.next();
			if (active.op == LangitIds.FX_PILAR) {
				LangitSkyState.Hall hall = LangitSkyState.at(active.b);
				if (hall != null && hall.tethered()) {
					active.duration = Math.max(active.duration, t - active.start + 2.0F);
				}
			}
			float age = t - active.start;
			if (age > active.duration || age < -1.0F) {
				it.remove();
			}
		}
		if (level.getGameTime() % 5L != 0L || !Qi.on()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Active active : ACTIVE) {
			if (active.op != LangitIds.FX_PILAR || active.a.distToCenterSqr(camX, active.a.getY(), camZ) > 96.0D * 96.0D) {
				continue;
			}
			if (LangitBudget.particles(1, 1) == 0) {
				return;
			}
			double y = camY + (random.nextDouble() - 0.5D) * 24.0D;
			double k = Mth.clamp((y - active.a.getY()) / Math.max(1.0D, active.b.getY() - active.a.getY()), 0.0D, 1.0D);
			double x = Mth.lerp(k, active.a.getX(), active.b.getX()) + 0.5D + (random.nextDouble() - 0.5D);
			double z = Mth.lerp(k, active.a.getZ(), active.b.getZ()) + 0.5D + (random.nextDouble() - 0.5D);
			level.addParticle(Qi.mote(0xDDF2FF, 0.25F, 40), x, Mth.lerp(k, active.a.getY(), active.b.getY()), z, 0.0D, 0.15D, 0.0D);
		}
	}

	static void draw(LangitFrame f) {
		if (ACTIVE.isEmpty() || f.tier < 0) {
			return;
		}
		float t = f.seconds;
		for (Active active : ACTIVE) {
			if (active.a.distToCenterSqr(f.camX, active.a.getY(), f.camZ) > VIEW * VIEW
					&& active.b.distToCenterSqr(f.camX, active.b.getY(), f.camZ) > VIEW * VIEW) {
				continue;
			}
			float age = t - active.start;
			if (age < 0.0F) {
				age += 12000.0F;
			}
			float k = Mth.clamp(age / active.duration, 0.0F, 1.0F);
			switch (active.op) {
				case LangitIds.FX_PILAR -> tether(f, active, age);
				case LangitIds.FX_ARRIVE, LangitIds.FX_FERI -> billow(f, active.a, k, active.op == LangitIds.FX_ARRIVE ? 3 : 2, 1.2F);
				case LangitIds.FX_TANGKAP -> caught(f, active, k);
				case LangitIds.FX_PINDAH -> {
					transfer(f, active.a, k, age);
					transfer(f, active.b, k, age);
				}
				case LangitIds.FX_NAIK -> ascend(f, active.a, age, active.duration);
				case LangitIds.FX_BAJAK -> breach(f, active.a, k);
				case LangitIds.FX_SENGSARA_BAWAH -> storm(f, active, age, t - active.strike);
				default -> {
				}
			}
		}
	}

	// ---------------------------------------------------------------- the effects

	private static void tether(LangitFrame f, Active active, float age) {
		if (!LangitBudget.take(5)) {
			return;
		}
		float in = Mth.clamp(age / 1.5F, 0.0F, 1.0F);
		float ax = f.rx(active.a.getX() + 0.5D);
		float ay = f.ry(active.a.getY() + 1.0D);
		float az = f.rz(active.a.getZ() + 0.5D);
		float bx = f.rx(active.b.getX() + 0.5D);
		float by = f.ry(active.b.getY());
		float bz = f.rz(active.b.getZ() + 0.5D);
		float len = Math.max(1.0F, by - ay);
		float scroll = -f.seconds * 0.6F;
		float pulse = 0.8F + 0.2F * Mth.sin(f.seconds * 1.7F);
		PILLAR.beam(ax, ay, az, bx, by, bz, 0.35F, scroll, scroll + len / 6.0F, AwanSink.argb(0.9F * in, 0xFFFFFF), AwanSink.argb(0.7F * in, 0xE8F6FF));
		PILLAR.beam(ax, ay, az, bx, by, bz, 1.6F, scroll * 0.5F, scroll * 0.5F + len / 12.0F, AwanSink.argb(0.3F * in * pulse, 0xBFE4FF),
				AwanSink.argb(0.2F * in * pulse, 0xBFE4FF));
		GLOW.billboard(ax, ay, az, 2.2F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(0.5F * in * pulse, 0xDDF2FF));
		GLOW.billboard(bx, by, bz, 2.8F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(0.55F * in * pulse, 0xFFF2C8));
		RING.flat(ax, ay - 0.9F, az, 2.5F + 0.5F * pulse, f.seconds * 0.4F, AwanSink.argb(0.35F * in, 0xDDF2FF));
	}

	private static void billow(LangitFrame f, BlockPos at, float k, int puffs, float size) {
		if (!LangitBudget.take(puffs)) {
			return;
		}
		float a = 0.75F * (1.0F - LautAwanRenderer.smooth(k));
		for (int i = 0; i < puffs; i++) {
			float ang = i * Mth.TWO_PI / puffs + k;
			float r = 0.4F + k * 1.4F;
			float x = f.rx(at.getX() + 0.5D) + Mth.cos(ang) * r;
			float y = f.ry(at.getY() + 0.8D) + k * 0.8F;
			float z = f.rz(at.getZ() + 0.5D) + Mth.sin(ang) * r;
			PUFF.billboard(x, y, z, size * (0.8F + 1.2F * k), k * 1.5F + i, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(a, 0xF4F8FF));
		}
	}

	private static void caught(LangitFrame f, Active active, float k) {
		if (!LangitBudget.take(3)) {
			return;
		}
		float a = 1.0F - LautAwanRenderer.smooth(k);
		float x = f.rx(active.a.getX() + 0.5D);
		float y = f.ry(active.a.getY() - 0.5D);
		float z = f.rz(active.a.getZ() + 0.5D);
		NET.flat(x, y, z, 1.5F + 3.5F * LautAwanRenderer.smooth(k * 1.4F), k * 0.6F, AwanSink.argb(0.85F * a, 0xE0F4FF));
		float bx = f.rx(active.b.getX() + 0.5D);
		float by = f.ry(active.b.getY() + 0.05D);
		float bz = f.rz(active.b.getZ() + 0.5D);
		RING.flat(bx, by, bz, 0.8F + 2.5F * k, 0.0F, AwanSink.argb(0.6F * a, 0xDDF2FF));
		GLOW.billboard(bx, by + 1.0F, bz, 1.6F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(0.4F * a, 0xE8F6FF));
	}

	private static void transfer(LangitFrame f, BlockPos at, float k, float age) {
		int puffs = f.tier >= 1 ? 6 : 3;
		if (!LangitBudget.take(2 + puffs)) {
			return;
		}
		float a = Mth.sin(Mth.clamp(k, 0.0F, 1.0F) * Mth.PI);
		float x = f.rx(at.getX() + 0.5D);
		float y = f.ry(at.getY());
		float z = f.rz(at.getZ() + 0.5D);
		PILLAR.beam(x, y, z, x, y + 22.0F, z, 1.2F, -age * 2.0F, -age * 2.0F + 3.0F, AwanSink.argb(0.8F * a, 0xCFE8FF), AwanSink.argb(0.0F, 0xCFE8FF));
		GLOW.billboard(x, y + 1.0F, z, 3.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(0.5F * a, 0xCFE8FF));
		for (int i = 0; i < puffs; i++) {
			float ang = i * Mth.TWO_PI / puffs + age * 2.2F;
			float r = 2.6F - k;
			PUFF.billboard(x + Mth.cos(ang) * r, y + 0.6F + i * 0.15F, z + Mth.sin(ang) * r, 1.0F, age + i, 0.0F, 1.0F, 0.0F, 1.0F,
					AwanSink.argb(0.6F * a, 0xF4F8FF));
		}
	}

	private static void ascend(LangitFrame f, BlockPos at, float age, float duration) {
		int segments = f.tier >= 1 ? 32 : 16;
		float x = f.rx(at.getX() + 0.5D);
		float y = f.ry(at.getY());
		float z = f.rz(at.getZ() + 0.5D);
		float fade = 1.0F - LautAwanRenderer.smooth((age - (duration - 1.5F)) / 1.5F);
		if (LangitBudget.take(3)) {
			float grow = Mth.clamp(age / 1.2F, 0.0F, 1.0F);
			PILLAR.beam(x, y, z, x, y + 60.0F * grow, z, 0.8F, -age, -age + 8.0F, AwanSink.argb(0.9F * fade, 0xFFF6DC), AwanSink.argb(0.2F * fade, 0xFFF6DC));
			PILLAR.beam(x, y, z, x, y + 60.0F * grow, z, 3.0F, -age * 0.5F, -age * 0.5F + 4.0F, AwanSink.argb(0.25F * fade, 0xFFE08A),
					AwanSink.argb(0.0F, 0xFFE08A));
			GLOW.billboard(x, y + 1.5F, z, 5.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(0.45F * fade, 0xFFF0C0));
		}
		for (int k = 0; k < NINE.length; k++) {
			float start = k * 0.6F;
			float local = (age - start) / 5.0F;
			if (local <= 0.0F || local >= 1.0F) {
				continue;
			}
			if (!LangitBudget.take(segments)) {
				return;
			}
			float rise = LautAwanRenderer.smooth(local) * 40.0F;
			float radius = 3.0F + 6.0F * local;
			float a = Mth.sin(local * Mth.PI) * 0.85F * fade;
			annulus(f, x, y + 1.0F + rise, z, radius, 0.9F, segments, AwanSink.argb(a, NINE[k]), local * 2.0F);
		}
	}

	private static void breach(LangitFrame f, BlockPos at, float k) {
		int puffs = f.tier >= 1 ? 10 : 5;
		if (!LangitBudget.take(puffs + 1)) {
			return;
		}
		LangitSkyState.Hall hall = LangitSkyState.nearest(at.getX(), at.getZ());
		double seaY = hall != null ? hall.cloudY() : at.getY();
		float x = f.rx(at.getX() + 0.5D);
		float y = f.ry(seaY + 1.0D);
		float z = f.rz(at.getZ() + 0.5D);
		float a = 1.0F - LautAwanRenderer.smooth(k);
		RING.flat(x, y, z, 3.0F + 14.0F * LautAwanRenderer.smooth(k), 0.0F, AwanSink.argb(0.55F * a, 0x5A5E6E));
		for (int i = 0; i < puffs; i++) {
			float ang = i * Mth.TWO_PI / puffs;
			float r = 2.0F + 12.0F * LautAwanRenderer.smooth(k);
			float up = 4.0F * Mth.sin(k * Mth.PI) * (0.5F + 0.5F * ((i * 7) % 3) / 2.0F);
			PUFF.billboard(x + Mth.cos(ang) * r, y + up, z + Mth.sin(ang) * r, 2.5F + 2.0F * k, i + k, 0.0F, 1.0F, 0.0F, 1.0F,
					AwanSink.argb(0.7F * a, i % 2 == 0 ? 0xC8CCD8 : 0x8A8E9E));
		}
	}

	private static void storm(LangitFrame f, Active active, float age, float sinceStrike) {
		LangitSkyState.Hall hall = LangitSkyState.nearest(f.camX, f.camZ);
		double seaY = hall != null ? hall.cloudY() : active.a.getY() + 30.0D;
		float x = f.rx(active.a.getX() + 0.5D);
		float y = f.ry(seaY + 0.6D);
		float z = f.rz(active.a.getZ() + 0.5D);
		float in = Mth.clamp(age / 3.0F, 0.0F, 1.0F) * (1.0F - LautAwanRenderer.smooth((age - (active.duration - 4.0F)) / 4.0F));
		if (LangitBudget.take(2)) {
			VORTEX.flat(x, y, z, 26.0F, -f.seconds * 0.35F, AwanSink.argb(0.85F * in, 0xFFFFFF));
			VORTEX.flat(x, y + 0.4F, z, 16.0F, -f.seconds * 0.6F + 1.0F, AwanSink.argb(0.5F * in, 0xD8C8FF));
		}
		if (sinceStrike >= 0.0F && sinceStrike < 0.6F && LangitBudget.take(3)) {
			float flash = 1.0F - sinceStrike / 0.6F;
			float bottom = f.ry(active.a.getY() + 22.0D);
			BOLT.beam(x, bottom, z, x + 1.5F, y + 10.0F, z - 1.0F, 1.6F, 1.0F, 0.0F, AwanSink.argb(0.9F * flash, 0xE0D0FF),
					AwanSink.argb(0.7F * flash, 0xFFFFFF));
			FLASH.flat(x, y + 0.8F, z, 18.0F, 0.0F, AwanSink.argb(0.6F * flash, 0xB8A0FF));
			FLASH.billboard(x + 1.5F, y + 10.0F, z - 1.0F, 4.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, AwanSink.argb(0.8F * flash, 0xFFFFFF));
		}
	}

	/** A flat horizontal ring (ring_soft: u round the ring, v across) of {@code segments} quads. */
	private static void annulus(LangitFrame f, float x, float y, float z, float radius, float width, int segments, int color, float turn) {
		float inner = radius - width;
		float outer = radius + width;
		for (int i = 0; i < segments; i++) {
			float t0 = (float) i / segments * Mth.TWO_PI + turn;
			float t1 = (float) (i + 1) / segments * Mth.TWO_PI + turn;
			float c0 = Mth.cos(t0);
			float s0 = Mth.sin(t0);
			float c1 = Mth.cos(t1);
			float s1 = Mth.sin(t1);
			RING.quad(x + c0 * inner, y, z + s0 * inner, i * 0.25F, 0.0F, color,
					x + c1 * inner, y, z + s1 * inner, (i + 1) * 0.25F, 0.0F, color,
					x + c1 * outer, y, z + s1 * outer, (i + 1) * 0.25F, 1.0F, color,
					x + c0 * outer, y, z + s0 * outer, i * 0.25F, 1.0F, color);
		}
	}

	static boolean any() {
		return !ACTIVE.isEmpty();
	}

	static void clear() {
		ACTIVE.clear();
	}

	/** The nearest active tribulation vortex position, or null (the client sound fades thunder with distance). */
	static @Nullable BlockPos storm() {
		for (Active active : ACTIVE) {
			if (active.op == LangitIds.FX_SENGSARA_BAWAH) {
				return active.a;
			}
		}
		return null;
	}

	private LangitFxClient() {
	}
}
