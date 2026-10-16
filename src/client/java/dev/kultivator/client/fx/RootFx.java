package dev.kultivator.client.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.Element;
import dev.kultivator.data.RootGrade;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * The root testing ceremony and the marrow cleansing (particles only, scaled by {@link EffectQuality}).
 * <p>
 * ROOT_CEREMONY ({@code pos} = where the ceremony began, {@code b} = element mask, {@code f} = grade ordinal):
 * {@code a = -2} the testing stones rise in a circle of radius 3 (five classic stones, all nine when the root holds a
 * mutant element, METAL..SPACE clockwise from the north); {@code a = element ordinal} that stone is read: the
 * player's elements flare into tall columns of their colour that keep glowing (orbiting glyphs at Maximal), the
 * others flicker and fade; once every stone is read the lit columns pour streams of light into the centre;
 * {@code a = -1} the reveal: a burst in the root's colours, a ring in the grade colour and grade-specific extras.
 * <p>
 * MARROW_CLEANSE: {@code b > 0} starts {@code b} ticks of black impurities seeping out of the player at {@code pos}
 * (followed while they move) with sickly drops; {@code b = 0} is the purified white flash at the end
 * ({@code a} = the new grade ordinal).
 */
public final class RootFx {

	private static final int STEP_BEGIN = -2;
	private static final int STEP_REVEAL = -1;

	private static final double RADIUS = 3.0D;
	private static final double LIT_HEIGHT = 3.6D;
	private static final double FLARE_HEIGHT = 7.0D;
	private static final int FLARE_TICKS = 12;
	private static final int FLICKER_TICKS = 16;
	private static final int AFTERGLOW = 40;
	/** A ceremony that hears nothing for this long is dropped (the viewer walked away, the server stopped...). */
	private static final int SILENCE_LIMIT = 200;
	private static final int MAX_CEREMONIES = 8;
	private static final int MAX_CLEANSES = 16;
	private static final double FOLLOW_RANGE_SQ = 2.5D * 2.5D;
	private static final int MUTANT_MASK = Element.LIGHTNING.bit() | Element.ICE.bit() | Element.WIND.bit() | Element.SPACE.bit();

	private static final int STONE = 0xB4BDCC;
	private static final int STONE_DARK = 0x5C6270;
	private static final int[] IMPURITY = {0x0E0E0E, 0x1C1A18, 0x2B2724, 0x3B3631};
	private static final int SICK_LIGHT = 0x9AA83A;
	private static final int SICK_DARK = 0x5A6620;
	private static final int PURE = 0xFFFFFF;
	private static final int PURE_GOLD = 0xFFF2C0;

	private static final List<Ceremony> CEREMONIES = new ArrayList<>();
	private static final List<Cleanse> CLEANSES = new ArrayList<>();
	private static long lastTick = Long.MIN_VALUE;
	private static ClientLevel lastLevel;
	private static boolean hooked;

	public static void play(FxType type, Payloads.Fx fx) {
		hook();
		ClientLevel level = CommonFx.level();
		if (level == null) {
			return;
		}
		forgetOldWorld(level);
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		switch (type) {
			case ROOT_CEREMONY -> ceremony(pos, fx.a(), fx.b(), Math.round(fx.f()));
			case MARROW_CLEANSE -> {
				if (fx.b() > 0) {
					startCleanse(level, pos, fx.b());
				} else {
					purified(level, pos, fx.a());
				}
			}
			default -> {
			}
		}
	}

	/** Every client tick (only while in a world and not paused). Safe to call more than once per tick. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		forgetOldWorld(level);
		if (level == null || minecraft.player == null || minecraft.isPaused() || (CEREMONIES.isEmpty() && CLEANSES.isEmpty())) {
			return;
		}
		long now = level.getGameTime();
		if (now == lastTick) {
			return;
		}
		lastTick = now;
		RandomSource random = level.getRandom();
		Iterator<Ceremony> ceremonies = CEREMONIES.iterator();
		while (ceremonies.hasNext()) {
			Ceremony ceremony = ceremonies.next();
			tickCeremony(ceremony, random);
			boolean faded = ceremony.revealAge >= AFTERGLOW;
			boolean silent = ceremony.age - ceremony.lastHeard > SILENCE_LIMIT;
			if (faded || silent) {
				ceremonies.remove();
			}
		}
		Iterator<Cleanse> cleanses = CLEANSES.iterator();
		while (cleanses.hasNext()) {
			Cleanse cleanse = cleanses.next();
			tickCleanse(cleanse, level, random);
			cleanse.age++;
			if (cleanse.age >= cleanse.duration) {
				cleanses.remove();
			}
		}
	}

	/** The effect dispatcher has no tick hook for this module, so the animation hooks itself in on first use. */
	private static void hook() {
		if (!hooked) {
			hooked = true;
			ClientTickEvents.END_CLIENT_TICK.register(RootFx::tick);
		}
	}

	/** Running effects belong to one world: they are dropped when the client changes or leaves it. */
	private static void forgetOldWorld(ClientLevel level) {
		if (level != lastLevel) {
			lastLevel = level;
			CEREMONIES.clear();
			CLEANSES.clear();
			lastTick = Long.MIN_VALUE;
		}
	}

	// ================================================================== ceremony

	private static void ceremony(Vec3 pos, int a, int mask, int grade) {
		Ceremony ceremony = find(pos);
		if (a == STEP_BEGIN) {
			if (ceremony != null) {
				CEREMONIES.remove(ceremony);
			}
			ceremony = add(new Ceremony(pos, mask, grade));
			opening(ceremony);
			return;
		}
		if (ceremony == null) {
			// Came into range late: draw from here on.
			ceremony = add(new Ceremony(pos, mask, grade));
		}
		ceremony.mask = mask;
		ceremony.grade = grade;
		ceremony.lastHeard = ceremony.age;
		if (a == STEP_REVEAL) {
			reveal(ceremony);
		} else if (a >= 0 && a < ceremony.count) {
			ceremony.readAt[a] = ceremony.age;
			reading(ceremony, a);
		}
	}

	private static Ceremony find(Vec3 pos) {
		for (Ceremony ceremony : CEREMONIES) {
			if (ceremony.center.distanceToSqr(pos) < 0.5D) {
				return ceremony;
			}
		}
		return null;
	}

	private static Ceremony add(Ceremony ceremony) {
		while (CEREMONIES.size() >= MAX_CEREMONIES) {
			CEREMONIES.remove(0);
		}
		CEREMONIES.add(ceremony);
		return ceremony;
	}

	/** The stones rise: a faint circle, a ring and a short grey column at every stone, glyphs drawn to the centre. */
	private static void opening(Ceremony ceremony) {
		RandomSource random = CommonFx.random();
		Vec3 heart = ceremony.heart();
		CommonFx.ring(ceremony.center.add(0.0D, 0.05D, 0.0D), STONE, (float) RADIUS, 60);
		for (int i = 0; i < ceremony.count; i++) {
			Vec3 base = ceremony.base(i);
			CommonFx.ring(base, STONE, 0.45F, 14);
			CommonFx.pillar(base, STONE, 1.2F);
			int glyphs = EffectQuality.scale(3);
			for (int k = 0; k < glyphs; k++) {
				Vec3 from = base.add(0.0D, 0.6D + random.nextFloat(), 0.0D).subtract(heart);
				CommonFx.add(ParticleTypes.ENCHANT, heart.x, heart.y, heart.z, from.x + random.nextGaussian() * 0.2D,
						from.y, from.z + random.nextGaussian() * 0.2D);
			}
		}
		CommonFx.burst(heart, STONE, 10, 0.8F);
	}

	/** One stone is read: a tall flare for the player's elements, a short dull flicker and smoke for the others. */
	private static void reading(Ceremony ceremony, int index) {
		RandomSource random = CommonFx.random();
		Element element = Element.values()[index];
		Vec3 base = ceremony.base(index);
		if (ceremony.owned(index)) {
			int rgb = element.color();
			CommonFx.pillar(base, rgb, (float) FLARE_HEIGHT);
			CommonFx.ring(base, rgb, 0.8F, 18);
			CommonFx.burst(base.add(0.0D, FLARE_HEIGHT * 0.7D, 0.0D), rgb, 12, 1.4F);
			int sparks = EffectQuality.scale(4);
			for (int k = 0; k < sparks; k++) {
				CommonFx.add(ParticleTypes.END_ROD, base.x, base.y + 0.3D, base.z, random.nextGaussian() * 0.02D,
						0.15D + random.nextFloat() * 0.12D, random.nextGaussian() * 0.02D);
			}
			if (element.mutant()) {
				mutantSparkle(element, base.add(0.0D, 2.0D, 0.0D), 10);
			}
		} else {
			CommonFx.pillar(base, dull(element.color()), 1.0F);
			int puffs = EffectQuality.scale(3);
			for (int k = 0; k < puffs; k++) {
				CommonFx.add(ParticleTypes.SMOKE, base.x + random.nextGaussian() * 0.1D, base.y + 0.3D + random.nextFloat() * 0.5D,
						base.z + random.nextGaussian() * 0.1D, 0.0D, 0.02D, 0.0D);
			}
		}
	}

	private static void tickCeremony(Ceremony ceremony, RandomSource random) {
		ceremony.age++;
		boolean revealed = ceremony.revealAge >= 0;
		if (revealed) {
			ceremony.revealAge++;
		}
		float fade = revealed ? Math.max(0.0F, 1.0F - (float) ceremony.revealAge / AFTERGLOW) : 1.0F;
		if (!revealed && ceremony.age % 20 == 0) {
			CommonFx.ring(ceremony.center.add(0.0D, 0.05D, 0.0D), STONE_DARK, (float) RADIUS, 40);
		}
		int read = 0;
		for (int i = 0; i < ceremony.count; i++) {
			Vec3 base = ceremony.base(i);
			int at = ceremony.readAt[i];
			if (at < 0) {
				if (!revealed) {
					dormant(ceremony, i, base, random);
				}
				continue;
			}
			read++;
			int since = ceremony.age - at;
			Element element = Element.values()[i];
			if (ceremony.owned(i)) {
				litColumn(ceremony, element, base, since, fade, random);
			} else {
				fadingColumn(element, base, since, random);
			}
		}
		if (!revealed && read == ceremony.count) {
			converge(ceremony, random);
		}
		if (revealed && ceremony.revealAge < 16 && ceremony.revealAge % 2 == 0) {
			// The reveal ring keeps spreading outwards.
			int color = RootGrade.byOrdinal(ceremony.grade).color();
			CommonFx.ring(ceremony.center.add(0.0D, 0.1D, 0.0D), color, 1.0F + ceremony.revealAge * 0.28F, 32);
		}
	}

	/** A stone waiting to be read: a faint ring and now and then a grey mote. */
	private static void dormant(Ceremony ceremony, int index, Vec3 base, RandomSource random) {
		if ((ceremony.age + index * 3) % 8 != 0 || !EffectQuality.chance(random, 0.8F)) {
			return;
		}
		CommonFx.ring(base, STONE, 0.3F, 8);
		CommonFx.add(CommonFx.dust(STONE, 0.8F), base.x + random.nextGaussian() * 0.08D, base.y + random.nextFloat() * 0.9D,
				base.z + random.nextGaussian() * 0.08D, 0.0D, 0.0D, 0.0D);
	}

	/** A lit element column: tall right after the flare, then a steady glow; it sinks away after the reveal. */
	private static void litColumn(Ceremony ceremony, Element element, Vec3 base, int since, float fade, RandomSource random) {
		int rgb = element.color();
		double height = since < FLARE_TICKS ? FLARE_HEIGHT - (FLARE_HEIGHT - LIT_HEIGHT) * since / FLARE_TICKS : LIT_HEIGHT;
		height *= 0.25D + 0.75D * fade;
		int motes = since < FLARE_TICKS ? 5 : 2;
		DustParticleOptions dust = CommonFx.dust(rgb, 1.3F);
		for (int k = 0; k < motes; k++) {
			if (EffectQuality.chance(random, 0.9F * Math.max(0.2F, fade))) {
				CommonFx.add(dust, base.x + random.nextGaussian() * 0.12D, base.y + random.nextFloat() * height,
						base.z + random.nextGaussian() * 0.12D, 0.0D, 0.02D, 0.0D);
			}
		}
		if (since % 2 == 0 && EffectQuality.chance(random, fade)) {
			CommonFx.add(CommonFx.dust(lighten(rgb), 0.9F), base.x, base.y + height, base.z, 0.0D, 0.0D, 0.0D);
		}
		if (since % 8 == 0 && fade > 0.3F) {
			CommonFx.ring(base, rgb, 0.5F, 12);
		}
		if (EffectQuality.maximal() && EffectQuality.chance(random, 0.3F * fade)) {
			// Glyph motes orbit the column and are drawn into it.
			double angle = ceremony.age * 0.3D + element.ordinal() * 1.3D;
			double y = 0.6D + (Math.sin(ceremony.age * 0.1D + element.ordinal()) * 0.5D + 0.5D) * Math.max(0.0D, height - 0.6D);
			CommonFx.add(ParticleTypes.ENCHANT, base.x, base.y + y, base.z, Math.cos(angle) * 1.1D, 0.25D, Math.sin(angle) * 1.1D);
		}
	}

	/** An element the player lacks: it flickers on and off for a moment, then only a lone ember remains. */
	private static void fadingColumn(Element element, Vec3 base, int since, RandomSource random) {
		if (since < FLICKER_TICKS) {
			if ((since / 2) % 2 == 0) {
				float left = 1.0F - (float) since / FLICKER_TICKS;
				int n = EffectQuality.scale(3);
				DustParticleOptions dust = CommonFx.dust(dull(element.color()), 0.9F);
				for (int k = 0; k < n; k++) {
					CommonFx.add(dust, base.x + random.nextGaussian() * 0.1D, base.y + random.nextFloat() * 1.4D * left,
							base.z + random.nextGaussian() * 0.1D, 0.0D, 0.0D, 0.0D);
				}
			}
		} else if (since % 25 == 0 && EffectQuality.chance(random, 0.5F)) {
			CommonFx.add(CommonFx.dust(STONE_DARK, 0.7F), base.x, base.y + 0.2D, base.z, 0.0D, 0.0D, 0.0D);
		}
	}

	/** Every stone is read: streams of light run from the lit columns into the player's heart. */
	private static void converge(Ceremony ceremony, RandomSource random) {
		Vec3 heart = ceremony.heart();
		for (int i = 0; i < ceremony.count; i++) {
			if (!ceremony.owned(i) || ceremony.readAt[i] < 0) {
				continue;
			}
			int rgb = Element.values()[i].color();
			Vec3 top = ceremony.base(i).add(0.0D, LIT_HEIGHT * 0.85D, 0.0D);
			double head = (ceremony.age * 0.07D + i * 0.37D) % 1.0D;
			for (int k = 0; k < 3; k++) {
				double t = head - k * 0.05D;
				if (t < 0.0D || !EffectQuality.chance(random, 0.95F)) {
					continue;
				}
				Vec3 at = top.lerp(heart, t).add(0.0D, Math.sin(t * Math.PI) * 0.6D, 0.0D);
				CommonFx.add(CommonFx.dust(k == 0 ? lighten(rgb) : rgb, 1.1F - 0.2F * k), at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
			}
		}
		if (ceremony.age % 3 == 0) {
			CommonFx.spiralIn(heart, Element.mainColor(ceremony.mask), 1.0F, 4, ceremony.age);
		}
		if (EffectQuality.maximal() && ceremony.age % 2 == 0) {
			CommonFx.add(ParticleTypes.ENCHANT, heart.x, heart.y, heart.z, random.nextGaussian() * 1.5D, random.nextFloat() * 1.2D,
					random.nextGaussian() * 1.5D);
		}
	}

	/** The result: bursts in every root colour, a flash, a last flare of the lit stones and grade-specific extras. */
	private static void reveal(Ceremony ceremony) {
		RandomSource random = CommonFx.random();
		ceremony.revealAge = 0;
		Vec3 heart = ceremony.heart();
		RootGrade grade = RootGrade.byOrdinal(ceremony.grade);
		List<Element> elements = Element.fromMask(ceremony.mask);
		for (Element element : elements) {
			CommonFx.burst(heart, element.color(), 10 + 26 / Math.max(1, elements.size()), 2.4F);
			if (element.mutant()) {
				mutantSparkle(element, heart, 16);
			}
		}
		CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | grade.color()), heart, Vec3.ZERO, true);
		for (int i = 0; i < ceremony.count; i++) {
			if (ceremony.owned(i)) {
				CommonFx.pillar(ceremony.base(i), Element.values()[i].color(), (float) FLARE_HEIGHT + 1.0F);
			}
		}
		switch (grade) {
			case HEAVENLY -> {
				CommonFx.pillar(ceremony.center, grade.color(), 14.0F);
				CommonFx.pillar(ceremony.center, PURE, 10.0F);
				int totems = EffectQuality.scale(40);
				for (int k = 0; k < totems; k++) {
					CommonFx.add(ParticleTypes.TOTEM_OF_UNDYING, heart.x, heart.y, heart.z, random.nextGaussian() * 0.5D,
							0.3D + random.nextFloat() * 0.5D, random.nextGaussian() * 0.5D);
				}
				rods(heart, 16, random);
			}
			case HIGH -> {
				CommonFx.pillar(ceremony.center, grade.color(), 8.0F);
				rods(heart, 10, random);
			}
			case MID -> rods(heart, 5, random);
			case LOW -> CommonFx.ring(ceremony.center.add(0.0D, 0.1D, 0.0D), grade.color(), 1.6F, 24);
			case TRASH -> {
				CommonFx.burst(heart, 0x6B6B6B, 10, 1.0F);
				int puffs = EffectQuality.scale(8);
				for (int k = 0; k < puffs; k++) {
					CommonFx.add(ParticleTypes.SMOKE, heart.x + random.nextGaussian() * 0.4D, heart.y + random.nextGaussian() * 0.3D,
							heart.z + random.nextGaussian() * 0.4D, 0.0D, 0.03D, 0.0D);
				}
			}
		}
		if (EffectQuality.maximal()) {
			// A last circle of glyphs rushes into the heart.
			int glyphs = 30;
			for (int k = 0; k < glyphs; k++) {
				double angle = Math.PI * 2.0D * k / glyphs;
				CommonFx.add(ParticleTypes.ENCHANT, heart.x, heart.y, heart.z, Math.cos(angle) * RADIUS, -0.6D + random.nextFloat(),
						Math.sin(angle) * RADIUS);
			}
		}
	}

	private static void rods(Vec3 pos, int count, RandomSource random) {
		int n = EffectQuality.scale(count);
		for (int k = 0; k < n; k++) {
			CommonFx.add(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, random.nextGaussian() * 0.12D, 0.1D + random.nextFloat() * 0.2D,
					random.nextGaussian() * 0.12D);
		}
	}

	/** Lightning sparks, snowflakes, puffs of wind or space rifts for the rare elements. */
	private static void mutantSparkle(Element element, Vec3 pos, int count) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(count);
		for (int k = 0; k < n; k++) {
			double vx = random.nextGaussian() * 0.15D;
			double vy = random.nextGaussian() * 0.1D + 0.05D;
			double vz = random.nextGaussian() * 0.15D;
			switch (element) {
				case LIGHTNING -> CommonFx.add(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, vx * 2.0D, vy * 2.0D, vz * 2.0D);
				case ICE -> CommonFx.add(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, vx, vy, vz);
				case WIND -> {
					double angle = Math.PI * 2.0D * k / n;
					CommonFx.add(ParticleTypes.CLOUD, pos.x + Math.cos(angle) * 0.6D, pos.y + random.nextGaussian() * 0.3D,
							pos.z + Math.sin(angle) * 0.6D, -Math.sin(angle) * 0.15D, 0.02D, Math.cos(angle) * 0.15D);
				}
				case SPACE -> CommonFx.add(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, vx, vy, vz);
				default -> CommonFx.add(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, vx * 0.5D, vy * 0.5D, vz * 0.5D);
			}
		}
		if (element == Element.LIGHTNING && EffectQuality.maximal()) {
			CommonFx.bolt(pos.add(0.0D, 4.0D, 0.0D), pos, element.color(), 5);
		}
	}

	/** A running ceremony as this client sees it. */
	private static final class Ceremony {
		private final Vec3 center;
		private final int count;
		/** Age at which each stone was read, -1 while waiting. */
		private final int[] readAt = new int[9];
		private int mask;
		private int grade;
		private int age;
		private int lastHeard;
		private int revealAge = -1;

		private Ceremony(Vec3 center, int mask, int grade) {
			this.center = center;
			this.mask = mask;
			this.grade = grade;
			this.count = (mask & MUTANT_MASK) != 0 ? 9 : 5;
			Arrays.fill(readAt, -1);
		}

		/** Foot of stone {@code index}: evenly spread on the circle, the first one to the north. */
		private Vec3 base(int index) {
			double angle = -Math.PI / 2.0D + Math.PI * 2.0D * index / count;
			return center.add(Math.cos(angle) * RADIUS, 0.05D, Math.sin(angle) * RADIUS);
		}

		/** Chest height in the middle of the circle. */
		private Vec3 heart() {
			return center.add(0.0D, 1.2D, 0.0D);
		}

		private boolean owned(int index) {
			return (mask & (1 << index)) != 0;
		}
	}

	// ================================================================== marrow cleansing

	private static void startCleanse(ClientLevel level, Vec3 pos, int duration) {
		int id = followId(level, pos);
		CLEANSES.removeIf(each -> id >= 0 ? each.entityId == id : each.origin.distanceToSqr(pos) < 1.0D);
		while (CLEANSES.size() >= MAX_CLEANSES) {
			CLEANSES.remove(0);
		}
		CLEANSES.add(new Cleanse(id, pos, Math.min(duration, 400)));
		// The pill hits: a dark gasp around the body.
		Vec3 chest = pos.add(0.0D, 1.1D, 0.0D);
		CommonFx.burst(chest, IMPURITY[1], 10, 0.9F);
		CommonFx.ring(pos.add(0.0D, 0.9D, 0.0D), IMPURITY[2], 0.6F, 16);
	}

	/** One tick of the cleansing: dark impurities seep from the whole body, sickly drops fall, pain pulses. */
	private static void tickCleanse(Cleanse cleanse, ClientLevel level, RandomSource random) {
		Vec3 feet = cleanse.center(level);
		float rampIn = Math.min(1.0F, cleanse.age / 20.0F);
		float rampOut = Math.min(1.0F, (cleanse.duration - cleanse.age) / 15.0F + 0.2F);
		float strength = Math.max(0.0F, rampIn * rampOut);
		boolean beat = cleanse.age % 20 < 3;
		float pulse = beat ? Math.min(1.6F, strength * 1.6F) : strength;

		int motes = Math.round(3.0F * pulse);
		for (int k = 0; k < motes; k++) {
			if (!EffectQuality.chance(random, 0.9F)) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = 0.3D + random.nextDouble() * 0.1D;
			double y = 0.15D + random.nextDouble() * 1.65D;
			int rgb = IMPURITY[random.nextInt(IMPURITY.length)];
			CommonFx.add(CommonFx.dust(rgb, 0.8F + random.nextFloat() * 0.7F), feet.x + Math.cos(angle) * r, feet.y + y,
					feet.z + Math.sin(angle) * r, Math.cos(angle) * 0.5D, -0.2D, Math.sin(angle) * 0.5D);
		}
		if (cleanse.age % 3 == 0 && EffectQuality.chance(random, 0.7F * strength)) {
			CommonFx.add(ParticleTypes.SMOKE, feet.x + random.nextGaussian() * 0.25D, feet.y + 0.3D + random.nextDouble() * 1.4D,
					feet.z + random.nextGaussian() * 0.25D, 0.0D, 0.01D, 0.0D);
		}
		if (cleanse.age % 5 == 0 && EffectQuality.chance(random, 0.8F * strength)) {
			// Sickly drops falling from the arms and chest.
			CommonFx.add(ParticleTypes.ITEM_SLIME, feet.x + random.nextGaussian() * 0.3D, feet.y + 0.8D + random.nextDouble() * 0.7D,
					feet.z + random.nextGaussian() * 0.3D, random.nextGaussian() * 0.02D, 0.0D, random.nextGaussian() * 0.02D);
		}
		if (cleanse.age % 2 == 0 && EffectQuality.chance(random, 0.5F * strength)) {
			int rgb = random.nextBoolean() ? SICK_LIGHT : SICK_DARK;
			CommonFx.add(CommonFx.dust(rgb, 0.8F + random.nextFloat() * 0.4F), feet.x + (random.nextFloat() - 0.5F) * 0.8D,
					feet.y + 1.4D + random.nextFloat() * 0.5D, feet.z + (random.nextFloat() - 0.5F) * 0.8D, 0.0D, -0.02D, 0.0D);
		}
		if (cleanse.age % 4 == 0 && EffectQuality.chance(random, 0.6F * strength)) {
			// Filth gathers on the ground under the feet.
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double r = random.nextDouble() * 0.6D;
			CommonFx.add(CommonFx.dust(IMPURITY[0], 1.2F), feet.x + Math.cos(angle) * r, feet.y + 0.05D, feet.z + Math.sin(angle) * r,
					0.0D, 0.0D, 0.0D);
		}
		if (cleanse.age % 20 == 0 && strength > 0.3F) {
			CommonFx.ring(feet.add(0.0D, 0.9D, 0.0D), IMPURITY[2], 0.55F, 14);
		}
		if (EffectQuality.maximal() && cleanse.age % 6 == 0 && strength > 0.2F) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(ParticleTypes.SQUID_INK, feet.x + Math.cos(angle) * 0.35D, feet.y + 0.5D + random.nextDouble() * 1.0D,
					feet.z + Math.sin(angle) * 0.35D, Math.cos(angle) * 0.04D, 0.0D, Math.sin(angle) * 0.04D);
		}
	}

	/** The end of the cleansing: a white flash, a pure burst with a touch of the new grade's colour, rising light. */
	private static void purified(ClientLevel level, Vec3 pos, int gradeOrdinal) {
		RandomSource random = CommonFx.random();
		int id = followId(level, pos);
		CLEANSES.removeIf(each -> id >= 0 ? each.entityId == id : each.origin.distanceToSqr(pos) < 9.0D);
		Entity entity = id >= 0 ? level.getEntity(id) : null;
		Vec3 feet = entity == null ? pos : entity.position();
		Vec3 chest = feet.add(0.0D, 1.1D, 0.0D);
		CommonFx.add(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), chest, Vec3.ZERO, true);
		CommonFx.burst(chest, PURE, 30, 1.8F);
		CommonFx.burst(chest, RootGrade.byOrdinal(gradeOrdinal).color(), 14, 1.4F);
		CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), PURE, 0.8F, 20);
		CommonFx.ring(feet.add(0.0D, 0.05D, 0.0D), PURE_GOLD, 1.6F, 32);
		rods(chest, 10, random);
		int glints = EffectQuality.scale(16);
		DustParticleOptions shine = CommonFx.dust(PURE_GOLD, 0.7F);
		for (int k = 0; k < glints; k++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(shine, feet.x + Math.cos(angle) * 0.35D, feet.y + 0.1D + random.nextDouble() * 1.7D,
					feet.z + Math.sin(angle) * 0.35D, 0.0D, 0.0D, 0.0D);
		}
		if (EffectQuality.maximal()) {
			for (int k = 0; k < 10; k++) {
				CommonFx.add(ParticleTypes.WAX_OFF, feet.x + random.nextGaussian() * 0.4D, feet.y + 0.2D + random.nextDouble() * 1.6D,
						feet.z + random.nextGaussian() * 0.4D, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	/** The nearest player at the effect position (the one who swallowed the pill), -1 when none. */
	private static int followId(ClientLevel level, Vec3 pos) {
		int best = -1;
		double bestSq = FOLLOW_RANGE_SQ;
		for (Entity player : level.players()) {
			double distSq = player.distanceToSqr(pos);
			if (distSq < bestSq) {
				bestSq = distSq;
				best = player.getId();
			}
		}
		return best;
	}

	/** One running cleansing. */
	private static final class Cleanse {
		private final int entityId;
		private final Vec3 origin;
		private final int duration;
		private int age;

		private Cleanse(int entityId, Vec3 origin, int duration) {
			this.entityId = entityId;
			this.origin = origin;
			this.duration = duration;
		}

		/** The followed player's feet, or the original position when they are gone. */
		private Vec3 center(ClientLevel level) {
			Entity entity = entityId < 0 ? null : level.getEntity(entityId);
			return entity == null || entity.isRemoved() ? origin : entity.position();
		}
	}

	// ================================================================== colours

	/** Halfway to grey and darker: the colour of an element the player lacks. */
	private static int dull(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int grey = (r + g + b) / 3;
		r = (r + grey) / 2 * 6 / 10;
		g = (g + grey) / 2 * 6 / 10;
		b = (b + grey) / 2 * 6 / 10;
		return (r << 16) | (g << 8) | b;
	}

	/** Halfway to white: the bright core of a column. */
	private static int lighten(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return (((r + 255) / 2) << 16) | (((g + 255) / 2) << 8) | ((b + 255) / 2);
	}

	private RootFx() {
	}
}
