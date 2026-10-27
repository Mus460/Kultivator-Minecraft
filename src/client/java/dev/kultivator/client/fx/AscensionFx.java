package dev.kultivator.client.fx;

import dev.kultivator.ascension.Ascension;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The True Immortal ascension as a cinematic of particles (DESIGN 17): the world darkens around the cultivator, a golden
 * pillar of light falls from 40 blocks up and sends out expanding rings of light, then the heavens open (a disc of golden
 * clouds parting, shafts of light, cherry petals drifting down), rainbow motes rise from a turning lotus of light under
 * the feet (the feeling of floating, the player is never moved) and the finale bursts in a rainbow nova. The server sends
 * the steps plus a pulse every second; each event follows its player and is dropped when the pulses stop. All counts go
 * through {@link EffectQuality}; the client plays no sounds (the server does).
 */
public final class AscensionFx {

	/** An event is dropped this long after its last packet. */
	private static final int EXPIRE_TICKS = 60;
	private static final int MAX_EVENTS = 6;
	/** The cloud disc is fully open this long after the heavens open. */
	private static final int OPEN_TICKS = 80;

	private static final int PILLAR_CORE = 0xFFF6D8;
	private static final int PILLAR_GOLD = 0xFFC93C;
	private static final int PILLAR_DEEP = 0xF0A020;
	private static final int[] CLOUDS = {0xFFE8A8, 0xFFD27A, 0xFFF4DC, 0xFFDF94};
	private static final int[] SHADOWS = {0x15101F, 0x221836, 0x0B0812, 0x1C1430};

	private static final class Event {
		final int entityId;
		Vec3 pos;
		int elapsed;
		int sincePacket;

		Event(int entityId, Vec3 pos) {
			this.entityId = entityId;
			this.pos = pos;
		}
	}

	private static final List<Event> EVENTS = new ArrayList<>();
	/** Events belong to one world: they are dropped when the client changes or leaves it. */
	private static ClientLevel lastLevel;

	public static void play(FxType type, Payloads.Fx fx) {
		ClientLevel level = CommonFx.level();
		if (level == null || type != FxType.ASCENSION) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			EVENTS.clear();
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		int id = fx.b();
		switch (fx.a()) {
			case Ascension.FX_DARKEN -> {
				Event event = event(id, pos);
				event.elapsed = 0;
				darkenBurst(pos);
			}
			case Ascension.FX_PILLAR -> {
				Event event = event(id, pos);
				event.elapsed = Math.max(event.elapsed, Ascension.PILLAR_AT);
				pillarHerald(event.pos);
			}
			case Ascension.FX_HEAVENS -> {
				Event event = event(id, pos);
				event.elapsed = Math.max(event.elapsed, Ascension.HEAVENS_AT);
				heavensOpen(event.pos);
			}
			case Ascension.FX_PULSE -> {
				Event event = event(id, pos);
				event.elapsed = Math.max(0, (int) fx.f());
			}
			case Ascension.FX_VOICE -> {
				Event event = event(id, pos);
				event.elapsed = Math.max(event.elapsed, Ascension.VOICE_AT);
				voice(event.pos);
			}
			case Ascension.FX_FINALE -> {
				remove(id);
				finale(pos, fx.f() >= 0.5F);
			}
			case Ascension.FX_STOP -> {
				remove(id);
				fade(pos);
			}
			default -> {
			}
		}
	}

	/** Every client tick (only while in a world and not paused): keeps drawing every running event. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			EVENTS.clear();
		}
		if (level == null || EVENTS.isEmpty()) {
			return;
		}
		Iterator<Event> iterator = EVENTS.iterator();
		while (iterator.hasNext()) {
			Event event = iterator.next();
			event.elapsed++;
			event.sincePacket++;
			if (event.sincePacket > EXPIRE_TICKS || event.elapsed > Ascension.DURATION + 20) {
				iterator.remove();
				continue;
			}
			Entity entity = level.getEntity(event.entityId);
			if (entity != null && entity.isAlive()) {
				event.pos = entity.position();
			}
			// Seen from the ascending player's own eyes the pillar is hollow around the head, so the view stays clear.
			boolean hollow = entity != null && entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
			draw(event, hollow);
		}
	}

	// ================================================================== bookkeeping

	private static Event event(int id, Vec3 pos) {
		for (Event event : EVENTS) {
			if (event.entityId == id) {
				event.sincePacket = 0;
				return event;
			}
		}
		if (EVENTS.size() >= MAX_EVENTS) {
			EVENTS.remove(0);
		}
		Event event = new Event(id, pos);
		EVENTS.add(event);
		return event;
	}

	private static void remove(int id) {
		EVENTS.removeIf(event -> event.entityId == id);
	}

	private static void draw(Event event, boolean hollow) {
		RandomSource random = CommonFx.random();
		int t = event.elapsed;
		Vec3 feet = event.pos;
		if (t < Ascension.PILLAR_AT + 20) {
			drawDarkening(feet, t, random);
		}
		if (t >= Ascension.PILLAR_AT) {
			drawPillar(feet, t - Ascension.PILLAR_AT, random, hollow);
		}
		if (t >= Ascension.HEAVENS_AT) {
			drawHeavens(feet, t - Ascension.HEAVENS_AT, random);
			drawFloating(feet, t, random);
		}
	}

	// ================================================================== step 0: the world darkens

	/** Dark motes from far around collapse towards the cultivator. */
	private static void darkenBurst(Vec3 feet) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(48);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			Vec3 at = feet.add(Math.cos(angle) * 10.0D, 0.5D + random.nextDouble() * 3.0D, Math.sin(angle) * 10.0D);
			Vec3 velocity = new Vec3(-Math.cos(angle) * 0.3D, -0.01D, -Math.sin(angle) * 0.3D);
			CommonFx.add(CommonFx.dust(SHADOWS[random.nextInt(SHADOWS.length)], 3.0F), at, velocity, true);
		}
		int ink = EffectQuality.scale(8);
		for (int i = 0; i < ink; i++) {
			CommonFx.add(ParticleTypes.SQUID_INK, feet.add(random.nextGaussian() * 3.0D, 1.0D + random.nextDouble() * 2.0D,
					random.nextGaussian() * 3.0D), Vec3.ZERO, true);
		}
	}

	/** While the heavens hold their breath: a dark, slowly tightening swirl and a dim violet ring. */
	private static void drawDarkening(Vec3 feet, int t, RandomSource random) {
		float progress = Mth.clamp(t / (float) Ascension.PILLAR_AT, 0.0F, 1.0F);
		float fade = t > Ascension.PILLAR_AT ? 1.0F - (t - Ascension.PILLAR_AT) / 20.0F : 1.0F;
		if (fade <= 0.0F) {
			return;
		}
		double radius = 9.0D - 6.0D * progress;
		int n = EffectQuality.scale(Math.max(1, Math.round(8 * fade)));
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = radius * (0.7D + random.nextDouble() * 0.3D);
			Vec3 at = feet.add(Math.cos(angle) * r, random.nextDouble() * 3.5D, Math.sin(angle) * r);
			Vec3 velocity = new Vec3(-Math.cos(angle) * 0.06D - Math.sin(angle) * 0.04D, -0.01D,
					-Math.sin(angle) * 0.06D + Math.cos(angle) * 0.04D);
			DustParticleOptions dust = CommonFx.dust(SHADOWS[random.nextInt(SHADOWS.length)], 2.5F + random.nextFloat());
			CommonFx.add(dust, at, velocity, true);
		}
		if (EffectQuality.chance(random, 0.2F * fade)) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(ParticleTypes.SQUID_INK, feet.add(Math.cos(angle) * radius, 0.5D + random.nextDouble() * 2.0D, Math.sin(angle) * radius),
					Vec3.ZERO, true);
		}
		if (t % 10 == 0) {
			CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), 0x3A2A5A, (float) (radius * 0.5D), 24);
		}
		if (EffectQuality.maximal() && random.nextFloat() < 0.3F) {
			// Far above, the heavens begin to look down: single pale stars.
			CommonFx.add(ParticleTypes.END_ROD, feet.add(random.nextGaussian() * 6.0D, 18.0D + random.nextDouble() * 6.0D,
					random.nextGaussian() * 6.0D), Vec3.ZERO, true);
		}
	}

	// ================================================================== step 1: the golden pillar

	/** A flash high in the sky where the pillar is born. */
	private static void pillarHerald(Vec3 feet) {
		RandomSource random = CommonFx.random();
		Vec3 top = feet.add(0.0D, Ascension.PILLAR_HEIGHT, 0.0D);
		CommonFx.add(flash(PILLAR_GOLD), top, Vec3.ZERO, true);
		int sparks = EffectQuality.scale(20);
		for (int i = 0; i < sparks; i++) {
			CommonFx.add(ParticleTypes.END_ROD, top, new Vec3(random.nextGaussian() * 0.2D, random.nextGaussian() * 0.2D,
					random.nextGaussian() * 0.2D), true);
		}
		expandingRing(top, PILLAR_CORE, 0.4D, 40);
	}

	/**
	 * The pillar: a white-gold core and a turning golden shell. It falls from {@link Ascension#PILLAR_HEIGHT} in
	 * {@link Ascension#DESCENT_TICKS}, strikes the ground with a flash, then pulses out rings of light every second.
	 */
	private static void drawPillar(Vec3 feet, int s, RandomSource random, boolean hollow) {
		float descent = Mth.clamp(s / (float) Ascension.DESCENT_TICKS, 0.0F, 1.0F);
		float eased = 1.0F - (1.0F - descent) * (1.0F - descent) * (1.0F - descent);
		double top = feet.y + Ascension.PILLAR_HEIGHT;
		double bottom = top - Ascension.PILLAR_HEIGHT * eased;
		double length = Math.max(0.5D, top - bottom);
		double fall = descent < 1.0F ? -0.2D : 0.02D;

		int core = EffectQuality.scale(10 + (int) Math.round(length * 0.6D));
		DustParticleOptions coreDust = CommonFx.dust(PILLAR_CORE, 1.6F);
		for (int i = 0; i < core; i++) {
			double y = bottom + random.nextDouble() * length;
			double x;
			double z;
			if (hollow && y < feet.y + 2.4D) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				double r = 0.9D + random.nextDouble() * 0.2D;
				x = feet.x + Math.cos(angle) * r;
				z = feet.z + Math.sin(angle) * r;
			} else {
				x = feet.x + random.nextGaussian() * 0.22D;
				z = feet.z + random.nextGaussian() * 0.22D;
			}
			CommonFx.add(coreDust, new Vec3(x, y, z), new Vec3(0.0D, fall, 0.0D), true);
		}
		int shell = EffectQuality.scale(6 + (int) Math.round(length * 0.3D));
		for (int i = 0; i < shell; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = 1.0D + random.nextDouble() * 0.4D;
			double y = bottom + random.nextDouble() * length;
			Vec3 at = new Vec3(feet.x + Math.cos(angle) * r, y, feet.z + Math.sin(angle) * r);
			Vec3 swirl = new Vec3(-Math.sin(angle) * 0.03D, fall * 0.25D, Math.cos(angle) * 0.03D);
			CommonFx.add(CommonFx.dust(random.nextBoolean() ? PILLAR_GOLD : PILLAR_DEEP, 1.2F), at, swirl, true);
		}

		if (descent < 1.0F) {
			// The falling head of the light.
			Vec3 head = new Vec3(feet.x, bottom, feet.z);
			if (s % 3 == 0) {
				CommonFx.add(flash(PILLAR_GOLD), head, Vec3.ZERO, true);
			}
			int sparks = EffectQuality.scale(6);
			for (int i = 0; i < sparks; i++) {
				CommonFx.add(ParticleTypes.END_ROD, head, new Vec3(random.nextGaussian() * 0.1D, 0.05D, random.nextGaussian() * 0.1D), true);
			}
			return;
		}
		int sinceImpact = s - Ascension.DESCENT_TICKS;
		if (sinceImpact == 0) {
			impact(feet, random);
		}
		if (sinceImpact % 20 == 0) {
			expandingRing(feet.add(0.0D, 0.1D, 0.0D), PILLAR_GOLD, 0.3D, 48);
		}
		if (EffectQuality.maximal() && sinceImpact % 20 == 10) {
			expandingRing(feet.add(0.0D, 1.2D, 0.0D), PILLAR_CORE, 0.2D, 40);
		}
		if (EffectQuality.chance(random, 0.6F)) {
			// Sparkles running down the pillar.
			CommonFx.add(ParticleTypes.END_ROD, new Vec3(feet.x + random.nextGaussian() * 1.2D, feet.y + 4.0D + random.nextDouble() * 30.0D,
					feet.z + random.nextGaussian() * 1.2D), new Vec3(0.0D, -0.4D, 0.0D), true);
		}
	}

	/** The pillar touches the ground: flashes, a wide ring and a burst of light. */
	private static void impact(Vec3 feet, RandomSource random) {
		CommonFx.add(flash(PILLAR_CORE), feet.add(0.0D, 1.0D, 0.0D), Vec3.ZERO, true);
		CommonFx.add(flash(PILLAR_GOLD), feet.add(0.0D, 0.2D, 0.0D), Vec3.ZERO, true);
		expandingRing(feet.add(0.0D, 0.1D, 0.0D), PILLAR_CORE, 0.5D, 72);
		expandingRing(feet.add(0.0D, 0.6D, 0.0D), PILLAR_GOLD, 0.35D, 56);
		CommonFx.burst(feet.add(0.0D, 1.0D, 0.0D), PILLAR_GOLD, 40, 3.0F);
		int sparks = EffectQuality.scale(24);
		for (int i = 0; i < sparks; i++) {
			CommonFx.add(ParticleTypes.END_ROD, feet.add(0.0D, 0.5D, 0.0D), new Vec3(random.nextGaussian() * 0.25D,
					0.1D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.25D), true);
		}
	}

	// ================================================================== step 2: the heavens open

	/** A ring of light spreads where the clouds will part. */
	private static void heavensOpen(Vec3 feet) {
		Vec3 sky = feet.add(0.0D, Ascension.HEAVENS_HEIGHT, 0.0D);
		CommonFx.add(flash(PILLAR_CORE), sky, Vec3.ZERO, true);
		expandingRing(sky, PILLAR_CORE, 0.5D, 80);
		expandingRing(sky.add(0.0D, -0.5D, 0.0D), PILLAR_GOLD, 0.35D, 60);
	}

	/** A disc of golden clouds whose centre opens, shafts of light from its rim, and cherry petals drifting down. */
	private static void drawHeavens(Vec3 feet, int s, RandomSource random) {
		double skyY = feet.y + Ascension.HEAVENS_HEIGHT;
		float open = Mth.clamp(s / (float) OPEN_TICKS, 0.0F, 1.0F);
		double hole = 1.5D + 5.5D * open;
		double outer = hole + 9.0D;
		int clouds = EffectQuality.scale(9);
		for (int i = 0; i < clouds; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = hole + (outer - hole) * Math.sqrt(random.nextDouble());
			Vec3 at = new Vec3(feet.x + Math.cos(angle) * r, skyY + random.nextGaussian() * 0.6D, feet.z + Math.sin(angle) * r);
			Vec3 swirl = new Vec3(-Math.sin(angle) * 0.025D + Math.cos(angle) * 0.01D * open, 0.0D,
					Math.cos(angle) * 0.025D + Math.sin(angle) * 0.01D * open);
			CommonFx.add(CommonFx.dust(CLOUDS[random.nextInt(CLOUDS.length)], 3.0F + random.nextFloat()), at, swirl, true);
		}
		if (s % 2 == 0) {
			// The bright rim of the opening.
			int rim = EffectQuality.scale(12);
			DustParticleOptions light = CommonFx.dust(PILLAR_CORE, 1.8F);
			for (int i = 0; i < rim; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				CommonFx.add(light, new Vec3(feet.x + Math.cos(angle) * hole, skyY - 0.3D, feet.z + Math.sin(angle) * hole), Vec3.ZERO, true);
			}
		}
		if (EffectQuality.chance(random, 0.5F)) {
			// A shaft of light from the rim.
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = hole * random.nextDouble();
			CommonFx.add(ParticleTypes.END_ROD, new Vec3(feet.x + Math.cos(angle) * r, skyY - 0.5D, feet.z + Math.sin(angle) * r),
					new Vec3(0.0D, -0.5D, 0.0D), true);
		}
		int petals = EffectQuality.scale(2);
		for (int i = 0; i < petals; i++) {
			CommonFx.add(ParticleTypes.CHERRY_LEAVES, feet.add(random.nextGaussian() * 5.0D, 8.0D + random.nextDouble() * 10.0D,
					random.nextGaussian() * 5.0D), Vec3.ZERO, true);
		}
		if (EffectQuality.maximal()) {
			if (random.nextFloat() < 0.4F) {
				double angle = random.nextDouble() * Math.PI * 2.0D;
				CommonFx.add(ParticleTypes.FIREWORK, new Vec3(feet.x + Math.cos(angle) * outer, skyY, feet.z + Math.sin(angle) * outer),
						new Vec3(0.0D, -0.02D, 0.0D), true);
			}
			if (s % 4 == 0) {
				CommonFx.ring(new Vec3(feet.x, skyY + 1.5D, feet.z), CLOUDS[0], (float) (outer + 1.0D), 40);
			}
		}
	}

	/** Rainbow motes rising from under the feet, a turning lotus of light and rings that float upwards. */
	private static void drawFloating(Vec3 feet, int t, RandomSource random) {
		float hue = (t % 120) / 120.0F;
		int rise = EffectQuality.scale(4);
		for (int i = 0; i < rise; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = 0.3D + random.nextDouble() * 0.7D;
			Vec3 at = feet.add(Math.cos(angle) * r, -0.3D + random.nextDouble() * 0.4D, Math.sin(angle) * r);
			int rgb = rainbow(hue + random.nextFloat() * 0.25F, 0.55F);
			CommonFx.add(CommonFx.dust(rgb, 0.9F), at, new Vec3(0.0D, 0.08D + random.nextDouble() * 0.06D, 0.0D), false);
		}
		if (t % 2 == 0) {
			int perArm = EffectQuality.minimal() ? 1 : EffectQuality.maximal() ? 4 : 2;
			double spin = t * 0.05D;
			DustParticleOptions lotus = CommonFx.dust(PILLAR_GOLD, 0.7F);
			for (int arm = 0; arm < 8; arm++) {
				double base = spin + arm * Math.PI / 4.0D;
				for (int k = 0; k < perArm; k++) {
					double r = 0.5D + k * 0.25D;
					double angle = base + k * 0.12D;
					CommonFx.add(lotus, new Vec3(feet.x + Math.cos(angle) * r, feet.y + 0.05D, feet.z + Math.sin(angle) * r), Vec3.ZERO, false);
				}
			}
		}
		if (t % 30 == 0) {
			int n = EffectQuality.scale(28);
			for (int i = 0; i < n; i++) {
				double angle = Math.PI * 2.0D * i / n;
				CommonFx.add(CommonFx.dust(rainbow((float) i / n, 0.5F), 1.0F),
						new Vec3(feet.x + Math.cos(angle) * 0.9D, feet.y + 0.1D, feet.z + Math.sin(angle) * 0.9D), new Vec3(0.0D, 0.12D, 0.0D), false);
			}
		}
	}

	// ================================================================== the voice and the finale

	/** "The heavens acknowledge a True Immortal": flashes, a golden and a rainbow ring, a fountain of light. */
	private static void voice(Vec3 feet) {
		RandomSource random = CommonFx.random();
		CommonFx.add(flash(PILLAR_GOLD), feet.add(0.0D, 1.6D, 0.0D), Vec3.ZERO, true);
		CommonFx.add(flash(PILLAR_CORE), feet.add(0.0D, 2.5D, 0.0D), Vec3.ZERO, true);
		expandingRing(feet.add(0.0D, 0.2D, 0.0D), PILLAR_GOLD, 0.45D, 72);
		rainbowRing(feet.add(0.0D, 1.0D, 0.0D), 0.35D, 72);
		int fountain = EffectQuality.scale(30);
		for (int i = 0; i < fountain; i++) {
			CommonFx.add(ParticleTypes.END_ROD, feet.add(0.0D, 1.0D, 0.0D), new Vec3(random.nextGaussian() * 0.12D,
					0.3D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.12D), true);
		}
		if (EffectQuality.maximal()) {
			rainbowRing(feet.add(0.0D, 2.0D, 0.0D), 0.25D, 60);
			for (int i = 0; i < 30; i++) {
				CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, feet.add(0.0D, 1.2D, 0.0D), new Vec3(random.nextGaussian() * 0.3D,
						0.2D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.3D), true);
			}
		}
	}

	/** The ascension is complete: flashes, rainbow novas, a rainbow spiral to the sky, totem light and a petal shower. */
	private static void finale(Vec3 feet, boolean real) {
		RandomSource random = CommonFx.random();
		CommonFx.add(flash(PILLAR_CORE), feet.add(0.0D, 0.5D, 0.0D), Vec3.ZERO, true);
		CommonFx.add(flash(PILLAR_GOLD), feet.add(0.0D, 1.5D, 0.0D), Vec3.ZERO, true);
		CommonFx.add(flash(0xFFFFFF), feet.add(0.0D, 3.0D, 0.0D), Vec3.ZERO, true);
		rainbowRing(feet.add(0.0D, 0.3D, 0.0D), 0.55D, 96);
		rainbowRing(feet.add(0.0D, 1.4D, 0.0D), 0.4D, 72);
		int spiral = EffectQuality.scale(80);
		for (int i = 0; i < spiral; i++) {
			float f = (float) i / spiral;
			double angle = f * Math.PI * 8.0D;
			double r = 1.2D * (1.0D - f * 0.5D);
			Vec3 at = feet.add(Math.cos(angle) * r, f * 14.0D, Math.sin(angle) * r);
			CommonFx.add(CommonFx.dust(rainbow(f, 0.6F), 1.4F), at, new Vec3(0.0D, 0.05D, 0.0D), true);
		}
		int totem = EffectQuality.scale(70);
		for (int i = 0; i < totem; i++) {
			CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, feet.add(0.0D, 1.2D, 0.0D), new Vec3(random.nextGaussian() * 0.5D,
					0.3D + random.nextDouble() * 0.4D, random.nextGaussian() * 0.5D), true);
		}
		int rods = EffectQuality.scale(40);
		for (int i = 0; i < rods; i++) {
			CommonFx.add(ParticleTypes.END_ROD, feet.add(0.0D, 1.0D, 0.0D), new Vec3(random.nextGaussian() * 0.3D,
					random.nextGaussian() * 0.3D + 0.1D, random.nextGaussian() * 0.3D), true);
		}
		int petals = EffectQuality.scale(real ? 40 : 24);
		for (int i = 0; i < petals; i++) {
			CommonFx.add(ParticleTypes.CHERRY_LEAVES, feet.add(random.nextGaussian() * 4.0D, 4.0D + random.nextDouble() * 8.0D,
					random.nextGaussian() * 4.0D), Vec3.ZERO, true);
		}
		CommonFx.beam(feet.add(0.0D, 30.0D, 0.0D), feet, PILLAR_CORE, 0.6F);
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 40; i++) {
				Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
				CommonFx.add(ParticleTypes.FIREWORK, feet.add(0.0D, 2.0D, 0.0D), dir.scale(0.3D), true);
			}
			rainbowRing(feet.add(0.0D, 2.6D, 0.0D), 0.3D, 60);
		}
	}

	/** A cancelled event: the light sinks away. */
	private static void fade(Vec3 feet) {
		RandomSource random = CommonFx.random();
		CommonFx.burst(feet.add(0.0D, 1.0D, 0.0D), 0xB08A3A, 16, 1.5F);
		int smoke = EffectQuality.scale(6);
		for (int i = 0; i < smoke; i++) {
			CommonFx.add(ParticleTypes.LARGE_SMOKE, feet.add(random.nextGaussian() * 0.5D, 0.5D + random.nextDouble(), random.nextGaussian() * 0.5D),
					new Vec3(0.0D, 0.03D, 0.0D), false);
		}
	}

	// ================================================================== shapes

	/** A horizontal ring of motes flying outwards from {@code center}: it looks like a ring of light expanding. */
	private static void expandingRing(Vec3 center, int rgb, double speed, int points) {
		int n = EffectQuality.scale(points);
		DustParticleOptions dust = CommonFx.dust(rgb, 1.3F);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			CommonFx.add(dust, center.add(cos * 0.4D, 0.0D, sin * 0.4D), new Vec3(cos * speed, 0.0D, sin * speed), true);
		}
	}

	/** Like {@link #expandingRing}, each mote coloured by its angle. */
	private static void rainbowRing(Vec3 center, double speed, int points) {
		int n = EffectQuality.scale(points);
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2.0D * i / n;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			CommonFx.add(CommonFx.dust(rainbow((float) i / n, 0.6F), 1.3F), center.add(cos * 0.4D, 0.0D, sin * 0.4D),
					new Vec3(cos * speed, 0.0D, sin * speed), true);
		}
	}

	private static int rainbow(float hue, float saturation) {
		float wrapped = hue - (float) Math.floor(hue);
		return Mth.hsvToRgb(wrapped, saturation, 1.0F) & 0xFFFFFF;
	}

	private static ColorParticleOption flash(int rgb) {
		return ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | rgb);
	}

	/** Disconnect: drops the old world (the next world's first tick would only do it after it loads). */
	static void forget() {
		lastLevel = null;
		EVENTS.clear();
	}

	private AscensionFx() {
	}
}
