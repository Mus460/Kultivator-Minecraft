package dev.kultivator.client.fx;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.data.Realm;
import dev.kultivator.data.VisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The aura of every cultivator from Qi Refining up, one motif per realm, denser with every stage (and Immortal
 * Ascension layer), all in the realm's colour:
 * <ul>
 * <li>Qi Refining: a faint mist drifting around the body;</li>
 * <li>Foundation Establishment: motes rising from the feet;</li>
 * <li>Golden Core: rising motes and three golden beads orbiting the waist, leaving short trails;</li>
 * <li>Nascent Soul: the same plus a small spectral infant seated in lotus above the head, with a tiny halo;</li>
 * <li>Deity Transformation: tongues of flame-like qi streaming up around the body;</li>
 * <li>Dao Union: a yin-yang pair of light and dark comets orbiting on a tilted ring;</li>
 * <li>Immortal Ascension: pillars of rising light around the body, one more for every second layer;</li>
 * <li>True Immortal: brighter motes in the realm colour and five golden beads; once ascended: shifting rainbow motes and
 * beads, drifting petals.</li>
 * </ul>
 * Shown for players whose synced look has the aura on, within 48 blocks, never for invisible players or spectators,
 * and not for yourself in first person. Everything is scaled by Effect Quality (Minimal: almost nothing; Maximal adds
 * end-rod sparkles from Nascent Soul). Motes that must keep their shape (the infant, beads, comets, flames) are made
 * with exact speed and lifetime instead of the random drift of plain dust.
 */
public final class AuraFx {

	private static final double RANGE_SQ = 48.0D * 48.0D;
	/** Same distance limit as vanilla particles. */
	private static final double EXACT_RANGE_SQ = 32.0D * 32.0D;
	private static final int GOLD = 0xFFD54A;
	private static final int YANG = 0xF6FFF9;
	private static final int YIN = 0x16201C;
	private static final int PILLAR_LIGHT = 0xFFF4D0;
	/** How much of the standing height is left while seated in meditation (the pose lowers the model). */
	private static final float SEATED_HEIGHT = 0.69F;

	/** Where and when one player's aura is drawn this tick. */
	private record Frame(double x, double y, double z, double width, double height, long t, float yaw) {
	}

	/** Every client tick: aura particles around every visible player whose VisualState shows the aura. */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		RandomSource random = level.getRandom();
		long time = level.getGameTime();
		Vec3 eye = minecraft.player.position();
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		for (AbstractClientPlayer player : level.players()) {
			if (player.isRemoved() || player.isInvisible() || player.isSpectator()) {
				continue;
			}
			if (player == minecraft.player && firstPerson) {
				continue;
			}
			if (player.distanceToSqr(eye) > RANGE_SQ) {
				continue;
			}
			VisualState visual = ClientCultivation.visual(player);
			if (!visual.has(VisualState.SHOW_AURA) || visual.realm() < Realm.QI_REFINING.ordinal()) {
				continue;
			}
			aura(minecraft, player, visual, random, time);
		}
	}

	private static void aura(Minecraft minecraft, AbstractClientPlayer player, VisualState visual, RandomSource random, long time) {
		Realm realm = visual.realmEnum();
		int tier = realm.ordinal();
		int stage = visual.stage();
		boolean rainbow = visual.has(VisualState.ASCENDED);
		double height = player.getBbHeight() * (visual.has(VisualState.MEDITATING) ? SEATED_HEIGHT : 1.0F);
		Frame f = new Frame(player.getX(), player.getY(), player.getZ(), player.getBbWidth(), height, time + player.getId() * 37L,
				player.yBodyRot);
		switch (realm) {
			case MORTAL -> {
				return;
			}
			case QI_REFINING -> mist(f, realm.color(), stage, random);
			case FOUNDATION -> rising(minecraft, f, visual, random, false, 1.0F);
			case GOLDEN_CORE -> {
				rising(minecraft, f, visual, random, false, 1.0F);
				beads(minecraft, f, 3, GOLD, stage, false, random);
			}
			case NASCENT_SOUL -> {
				rising(minecraft, f, visual, random, false, 0.9F);
				beads(minecraft, f, 3, GOLD, stage, false, random);
				soulInfant(minecraft, f, realm.color(), random);
			}
			case DEITY_TRANSFORMATION -> {
				rising(minecraft, f, visual, random, false, 0.6F);
				flames(minecraft, f, realm.color(), stage, random);
			}
			case DAO_UNION -> {
				rising(minecraft, f, visual, random, false, 0.7F);
				yinYang(minecraft, f, stage, random);
			}
			case IMMORTAL_ASCENSION -> {
				rising(minecraft, f, visual, random, false, 0.7F);
				pillars(minecraft, f, visual.layer(), stage, random);
			}
			case TRUE_IMMORTAL -> {
				rising(minecraft, f, visual, random, rainbow, 1.0F);
				beads(minecraft, f, 5, GOLD, stage, rainbow, random);
			}
		}
		// The ascension's gift: drifting petals for those the heavens have acknowledged.
		if (rainbow) {
			petals(f, random);
		}
		// Nascent Soul and above at Maximal: end-rod sparkles drifting up.
		if (tier >= Realm.NASCENT_SOUL.ordinal() && EffectQuality.maximal()
				&& random.nextFloat() < 0.08F + 0.02F * (tier - Realm.NASCENT_SOUL.ordinal())) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			CommonFx.add(ParticleTypes.END_ROD, new Vec3(f.x + Math.cos(angle) * f.width * 0.9D, f.y + random.nextDouble() * f.height,
					f.z + Math.sin(angle) * f.width * 0.9D), new Vec3(0.0D, 0.015D + random.nextDouble() * 0.02D, 0.0D), false);
		}
	}

	// ================================================================ motifs

	/** Qi Refining: pale wisps drifting slowly around the lower body. */
	private static void mist(Frame f, int realmColor, int stage, RandomSource random) {
		int n = amount(random, 0.35F + 0.12F * stage);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = f.width * (0.5D + random.nextDouble() * 0.6D);
			double px = f.x + Math.cos(angle) * radius;
			double pz = f.z + Math.sin(angle) * radius;
			double py = f.y + 0.1D + random.nextDouble() * f.height * 0.75D;
			int rgb = lighten(realmColor, 0.1F + random.nextFloat() * 0.25F);
			CommonFx.add(CommonFx.dust(rgb, 0.5F + random.nextFloat() * 0.3F), new Vec3(px, py, pz),
					new Vec3(Math.cos(angle) * 0.02D, 0.01D, Math.sin(angle) * 0.02D), false);
		}
	}

	/** Motes rising from the feet, more and brighter with realm, stage and layer ({@code share} of the full rate). */
	private static void rising(Minecraft minecraft, Frame f, VisualState visual, RandomSource random, boolean rainbow, float share) {
		int tier = visual.realm();
		int stage = visual.stage();
		float rate = (0.25F + 0.08F * tier + 0.07F * stage + 0.03F * visual.layer()) * share;
		int n = amount(random, rate);
		float brightness = Math.min(0.45F, 0.04F * tier + 0.03F * stage);
		float size = Math.min(1.6F, 0.45F + 0.06F * tier + 0.04F * stage);
		int base = visual.realmEnum().color();
		for (int i = 0; i < n; i++) {
			int rgb = rainbow ? rainbow(f.t + i * 11L, random) : lighten(base, brightness + random.nextFloat() * 0.15F);
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = f.width * (0.55D + random.nextDouble() * 0.45D);
			double px = f.x + Math.cos(angle) * radius;
			double pz = f.z + Math.sin(angle) * radius;
			double py = f.y + random.nextDouble() * f.height * 0.6D;
			double rise = 0.025D + 0.004D * tier + random.nextDouble() * 0.015D;
			exact(minecraft, random, CommonFx.dust(rgb, size * (0.8F + random.nextFloat() * 0.4F)), px, py, pz,
					(random.nextDouble() - 0.5D) * 0.006D, rise, (random.nextDouble() - 0.5D) * 0.006D, 18 + random.nextInt(14));
		}
	}

	/** Beads circling the waist; each leaves a short trail because it stays a few ticks where it was made. */
	private static void beads(Minecraft minecraft, Frame f, int count, int rgb, int stage, boolean rainbow, RandomSource random) {
		if (!every(f.t, 1)) {
			return;
		}
		double spin = f.t * 0.07D;
		double orbit = f.width * 1.25D + 0.04D * stage;
		double waist = f.y + f.height * 0.5D;
		int lifetime = EffectQuality.maximal() ? 7 : 5;
		for (int i = 0; i < count; i++) {
			double angle = spin + i * Math.PI * 2.0D / count;
			double bob = Math.sin(spin * 1.7D + i) * 0.08D;
			int color = rainbow ? rainbow(f.t + i * 40L, random) : rgb;
			exact(minecraft, random, CommonFx.dust(color, 0.85F + 0.05F * stage), f.x + Math.cos(angle) * orbit, waist + bob,
					f.z + Math.sin(angle) * orbit, 0.0D, 0.0D, 0.0D, lifetime);
		}
	}

	/** Nascent Soul: a small spectral infant seated in lotus above the head, bobbing gently, with a tiny halo. */
	private static void soulInfant(Minecraft minecraft, Frame f, int realmColor, RandomSource random) {
		boolean minimal = EffectQuality.minimal();
		if (minimal && f.t % 3L != 0L) {
			return;
		}
		double bob = Math.sin(f.t * 0.08D) * 0.05D;
		double cx = f.x;
		double cy = f.y + f.height + 0.42D + bob;
		double cz = f.z;
		// The body's sideways axis, so the little figure sits the same way as its owner.
		double yaw = Math.toRadians(f.yaw);
		double rx = Math.cos(yaw);
		double rz = Math.sin(yaw);
		int pale = lighten(realmColor, 0.55F);
		int glow = lighten(realmColor, 0.85F);
		mote(minecraft, random, glow, 0.85F, cx, cy + 0.22D, cz, 3);
		if (minimal) {
			return;
		}
		mote(minecraft, random, pale, 0.9F, cx, cy + 0.09D, cz, 3);
		for (int side = -1; side <= 1; side += 2) {
			// Hands resting on the knees, then the crossed legs.
			mote(minecraft, random, pale, 0.55F, cx + rx * 0.09D * side, cy + 0.06D, cz + rz * 0.09D * side, 3);
			mote(minecraft, random, pale, 0.6F, cx + rx * 0.12D * side, cy - 0.03D, cz + rz * 0.12D * side, 3);
		}
		if (f.t % (EffectQuality.maximal() ? 2L : 3L) == 0L) {
			double spin = f.t * 0.15D;
			for (int i = 0; i < 6; i++) {
				double angle = spin + i * Math.PI / 3.0D;
				mote(minecraft, random, glow, 0.4F, cx + Math.cos(angle) * 0.2D, cy + 0.33D, cz + Math.sin(angle) * 0.2D, 4);
			}
		}
	}

	/** Deity Transformation: tongues of qi flame licking up around the body, from the realm colour to white-hot. */
	private static void flames(Minecraft minecraft, Frame f, int realmColor, int stage, RandomSource random) {
		int tongues = 3 + stage;
		double spin = f.t * 0.03D;
		int hot = lighten(realmColor, 0.75F);
		int n = amount(random, 0.9F + 0.25F * stage);
		for (int i = 0; i < n; i++) {
			int k = random.nextInt(tongues);
			double angle = spin + k * Math.PI * 2.0D / tongues + Math.sin(f.t * 0.2D + k * 1.7D) * 0.25D;
			double radius = f.width * (0.75D + random.nextDouble() * 0.2D);
			double px = f.x + Math.cos(angle) * radius;
			double pz = f.z + Math.sin(angle) * radius;
			double py = f.y + random.nextDouble() * 0.3D;
			// Leaning in a little as they rise, like a flame closing to its tip.
			exact(minecraft, random, new DustColorTransitionOptions(realmColor, hot, 0.9F + random.nextFloat() * 0.5F), px, py, pz,
					-Math.cos(angle) * 0.008D, 0.09D + random.nextDouble() * 0.04D, -Math.sin(angle) * 0.008D, 12 + random.nextInt(8));
		}
	}

	/** Dao Union: a light and a dark comet chasing each other round a tilted ring at the waist. */
	private static void yinYang(Minecraft minecraft, Frame f, int stage, RandomSource random) {
		if (!every(f.t, 1)) {
			return;
		}
		double spin = f.t * (0.11D + 0.01D * stage);
		double radius = f.width * 1.6D + 0.05D * stage;
		double waist = f.y + f.height * 0.5D;
		int lifetime = EffectQuality.maximal() ? 9 : 6;
		for (int fish = 0; fish < 2; fish++) {
			double angle = spin + fish * Math.PI;
			double tilt = Math.sin(angle) * 0.35D;
			exact(minecraft, random, CommonFx.dust(fish == 0 ? YANG : YIN, 1.15F), f.x + Math.cos(angle) * radius, waist + tilt,
					f.z + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D, lifetime);
		}
	}

	/** Immortal Ascension: pillars of rising light slowly circling the body, one more for every second layer. */
	private static void pillars(Minecraft minecraft, Frame f, int layer, int stage, RandomSource random) {
		int count = 1 + (Mth.clamp(layer, 0, 8) + 1) / 2;
		double spin = f.t * 0.015D;
		double radius = f.width * 1.9D;
		for (int k = 0; k < count; k++) {
			double angle = spin + k * Math.PI * 2.0D / count;
			double px = f.x + Math.cos(angle) * radius;
			double pz = f.z + Math.sin(angle) * radius;
			int n = amount(random, 0.45F + 0.08F * stage);
			for (int i = 0; i < n; i++) {
				double py = f.y + random.nextDouble() * f.height * 1.4D;
				exact(minecraft, random, CommonFx.dust(lighten(PILLAR_LIGHT, random.nextFloat() * 0.5F), 0.7F),
						px + (random.nextDouble() - 0.5D) * 0.08D, py, pz + (random.nextDouble() - 0.5D) * 0.08D,
						0.0D, 0.05D + random.nextDouble() * 0.02D, 0.0D, 14);
			}
			if (EffectQuality.chance(random, 0.08F)) {
				CommonFx.add(ParticleTypes.END_ROD, new Vec3(px, f.y + 0.1D, pz), new Vec3(0.0D, 0.07D, 0.0D), false);
			}
		}
	}

	/** Ascended True Immortal: petals drifting down around the immortal, some cherry pink, some in shifting rainbow tints. */
	private static void petals(Frame f, RandomSource random) {
		if (!EffectQuality.chance(random, 0.16F)) {
			return;
		}
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double radius = 0.4D + random.nextDouble() * 1.2D;
		Vec3 at = new Vec3(f.x + Math.cos(angle) * radius, f.y + f.height + 0.3D + random.nextDouble() * 0.8D, f.z + Math.sin(angle) * radius);
		if (random.nextInt(3) == 0) {
			CommonFx.add(ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | rainbow(f.t, random)), at, Vec3.ZERO, false);
		} else {
			CommonFx.add(ParticleTypes.CHERRY_LEAVES, at, Vec3.ZERO, false);
		}
	}

	// ================================================================ helpers

	private static void mote(Minecraft minecraft, RandomSource random, int rgb, float size, double x, double y, double z, int lifetime) {
		exact(minecraft, random, CommonFx.dust(rgb, size), x, y, z, 0.0D, 0.0D, 0.0D, lifetime);
	}

	/**
	 * A particle with exactly this speed and lifetime (plain dust gets a random drift and lifetime). Respects the
	 * vanilla particle setting and distance like {@code level.addParticle}.
	 */
	private static void exact(Minecraft minecraft, RandomSource random, ParticleOptions options, double x, double y, double z,
							  double vx, double vy, double vz, int lifetime) {
		ParticleStatus status = minecraft.options.particles().get();
		if (status == ParticleStatus.MINIMAL || status == ParticleStatus.DECREASED && random.nextInt(3) == 0) {
			return;
		}
		if (minecraft.gameRenderer.mainCamera().position().distanceToSqr(x, y, z) > EXACT_RANGE_SQ) {
			return;
		}
		Particle particle = minecraft.particleEngine.createParticle(options, x, y, z, 0.0D, 0.0D, 0.0D);
		if (particle != null) {
			particle.setParticleSpeed(vx, vy, vz);
			particle.setLifetime(lifetime);
		}
	}

	/** How many particles to make this tick for a rate given at Normal quality (fractions become chances). */
	private static int amount(RandomSource random, float normalRate) {
		float rate = normalRate * EffectQuality.level().factor();
		int whole = (int) rate;
		return whole + (random.nextFloat() < rate - whole ? 1 : 0);
	}

	/** True every {@code normalTicks} ticks at Normal and Maximal quality, three times rarer at Minimal. */
	private static boolean every(long t, int normalTicks) {
		int n = EffectQuality.minimal() ? normalTicks * 3 : normalTicks;
		return t % n == 0L;
	}

	/** A slowly shifting rainbow colour. */
	private static int rainbow(long time, RandomSource random) {
		float hue = ((time % 240L) / 240.0F + random.nextFloat() * 0.08F) % 1.0F;
		return Mth.hsvToRgb(hue, 0.7F, 1.0F);
	}

	private static int lighten(int rgb, float amount) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		float t = Math.max(0.0F, Math.min(1.0F, amount));
		r = Math.round(r + (255 - r) * t);
		g = Math.round(g + (255 - g) * t);
		b = Math.round(b + (255 - b) * t);
		return (r << 16) | (g << 8) | b;
	}

	private AuraFx() {
	}
}
