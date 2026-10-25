package dev.kultivator.client.hall;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.hall.HallFxIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Effects of {@code FxType.HALL} (DESIGN 20.3.22, module A), all particles made here and scaled by Effect Quality: the
 * founding pillar, the level-up pillar, the travellers' mist of candidates, the three test tiers (Fanren's bamboo forest,
 * crumbling slope and cliff) and the stele's Duan pillar in the soul colour, the recruit ring, the promotion robe flash,
 * the lost-art glyphs, the dormant hall and the token. Timed effects run as small animations ticked every client tick.
 */
public final class HallFx {

	private static final int JADE = 0x8FE3C8;
	private static final int GOLD = 0xFFD54A;
	private static final int ASH = 0x8C9C94;
	private static final int MIST = 0xE4ECEE;
	/** The three tiers of the climb: bamboo green, stone grey, cliff gold. */
	private static final int[] TIER = {0x6BCB4A, 0xA0A4A8, 0xE8C25A};
	private static final int MAX_ANIMATIONS = 48;
	/** The candidates' arrival toast (DESIGN 20.3.3), shown for 6 s. */
	private static final List<Animation> ANIMATIONS = new ArrayList<>();
	private static ClientLevel lastLevel;

	private static final class Animation {
		final int op;
		final Vec3 pos;
		final Vec3 target;
		final int a;
		final float f;
		final int life;
		int age;

		Animation(int op, Vec3 pos, Vec3 target, int a, float f, int life) {
			this.op = op;
			this.pos = pos;
			this.target = target;
			this.a = a;
			this.f = f;
			this.life = life;
		}
	}

	/** One {@code FxType.HALL} packet (routed by {@code FxDispatcher}): a = the effect id, b and f per {@link HallFxIds}. */
	public static void play(FxType type, Payloads.Fx fx) {
		if (type != FxType.HALL || CommonFx.level() == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		int b = fx.b();
		float f = fx.f();
		switch (fx.a()) {
			case HallFxIds.FOUNDED -> {
				CommonFx.pillar(pos, b, Math.max(3.0F, f));
				CommonFx.ring(pos.add(0.0D, -0.9D, 0.0D), b, 2.5F, 48);
				CommonFx.ring(pos.add(0.0D, -0.9D, 0.0D), GOLD, 4.0F, 64);
				CommonFx.burst(pos, b, 40, 1.6F);
				animate(new Animation(HallFxIds.FOUNDED, pos, pos, b, f, 30));
			}
			case HallFxIds.LEVEL_UP -> {
				CommonFx.pillar(pos, JADE, Math.max(6.0F, f));
				CommonFx.ring(pos.add(0.0D, -0.9D, 0.0D), GOLD, 5.0F, 80);
				CommonFx.burst(pos.add(0.0D, 2.0D, 0.0D), GOLD, 50, 2.0F);
				animate(new Animation(HallFxIds.LEVEL_UP, pos, pos, b, f, 40));
			}
			case HallFxIds.MIST -> mist(pos, f > 0.5F);
			case HallFxIds.TEST_TIERS -> animate(new Animation(HallFxIds.TEST_TIERS, pos, pos, b, f, 60));
			case HallFxIds.DUAN -> animate(new Animation(HallFxIds.DUAN, pos, target, b, Math.max(1.0F, Math.min(9.0F, f)), 62));
			case HallFxIds.RECRUIT -> {
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), b, 30, 1.2F);
				animate(new Animation(HallFxIds.RECRUIT, pos, pos, b, Math.max(1.0F, f), 20));
			}
			case HallFxIds.PROMOTION -> animate(new Animation(HallFxIds.PROMOTION, pos, pos, b, f, 60));
			case HallFxIds.RESTORE -> animate(new Animation(HallFxIds.RESTORE, pos, pos, b, f, 60));
			case HallFxIds.DORMANT -> dormant(pos);
			case HallFxIds.TOKEN -> animate(new Animation(HallFxIds.TOKEN, pos, pos, b, f, 30));
			// the single notice slot (DESIGN 43.9); a candidate in wartime is important
			case HallFxIds.ARRIVAL_TOAST -> Kabar.kirim(Component.translatable(f > 0.5F ? "kultivator.hall.cand.war_title" : "kultivator.hall.cand.title"),
					Component.translatable("kultivator.hall.cand.subtitle", b), Kabar.glyph(Glyphs.HALL), f > 0.5F);
			default -> {
			}
		}
	}

	private static void animate(Animation animation) {
		if (ANIMATIONS.size() >= MAX_ANIMATIONS) {
			ANIMATIONS.remove(0);
		}
		ANIMATIONS.add(animation);
	}

	/** Every client tick (registered by {@link HallClient}). */
	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			ANIMATIONS.clear();
		}
		if (ANIMATIONS.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Iterator<Animation> it = ANIMATIONS.iterator(); it.hasNext(); ) {
			Animation animation = it.next();
			draw(animation, random);
			if (++animation.age > animation.life) {
				it.remove();
			}
		}
	}

	private static void draw(Animation animation, RandomSource random) {
		int age = animation.age;
		Vec3 pos = animation.pos;
		switch (animation.op) {
			case HallFxIds.FOUNDED, HallFxIds.LEVEL_UP -> {
				// Motes rising up the pillar and a gold ring sinking back to the ground.
				int n = EffectQuality.scale(3);
				DustParticleOptions dust = CommonFx.dust(animation.op == HallFxIds.LEVEL_UP ? GOLD : animation.a, 1.0F);
				for (int i = 0; i < n; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					CommonFx.add(dust, pos.x + Math.cos(angle) * 0.6D, pos.y + random.nextDouble() * 2.0D, pos.z + Math.sin(angle) * 0.6D, 0.0D, 0.12D, 0.0D);
				}
				if (age % 5 == 0) {
					float radius = 1.0F + age * 0.12F;
					CommonFx.ring(pos.add(0.0D, -0.9D, 0.0D), JADE, radius, 24);
				}
			}
			case HallFxIds.TEST_TIERS -> drawTiers(animation, random);
			case HallFxIds.DUAN -> drawDuan(animation, random);
			case HallFxIds.RECRUIT -> {
				float radius = 0.5F + animation.f * age / animation.life;
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), JADE, radius, 32);
				if (age % 4 == 0) {
					CommonFx.ring(pos.add(0.0D, 0.15D, 0.0D), GOLD, radius * 0.6F, 20);
				}
			}
			case HallFxIds.PROMOTION -> {
				// A robe flash: a spiral of the new trim colour climbing the body, brightest at the start.
				int n = EffectQuality.scale(age < 20 ? 6 : 3);
				DustParticleOptions dust = CommonFx.dust(animation.a, 0.9F);
				for (int i = 0; i < n; i++) {
					double angle = age * 0.45D + Math.PI * 2.0D * i / n;
					double height = (age % 20) / 20.0D * 1.9D;
					CommonFx.add(dust, pos.x + Math.cos(angle) * 0.45D, pos.y + height, pos.z + Math.sin(angle) * 0.45D, 0.0D, 0.01D, 0.0D);
				}
				if (age == 0) {
					CommonFx.burst(pos.add(0.0D, 1.1D, 0.0D), animation.a, 24, 1.0F);
				}
				if (age == animation.life - 1 && EffectQuality.maximal()) {
					for (int i = 0; i < 12; i++) {
						CommonFx.add(ParticleTypes.END_ROD, pos.add(0.0D, 1.0D, 0.0D),
								new Vec3(random.nextGaussian() * 0.06D, 0.08D + random.nextDouble() * 0.06D, random.nextGaussian() * 0.06D), false);
					}
				}
			}
			case HallFxIds.RESTORE -> {
				// Glyphs of the art rise around the stele in a slow ring.
				int colour = animation.a == 1 ? 0xFF9EC8 : animation.a == 2 ? 0xB88CFF : 0xBFE4FF;
				int n = EffectQuality.scale(4);
				for (int i = 0; i < n; i++) {
					double angle = age * 0.12D + Math.PI * 2.0D * i / n;
					double radius = 1.6D + Math.sin(age * 0.1D) * 0.3D;
					Vec3 at = pos.add(Math.cos(angle) * radius, (age / (double) animation.life) * 2.5D, Math.sin(angle) * radius);
					CommonFx.add(ParticleTypes.ENCHANT, at, new Vec3(0.0D, 0.02D, 0.0D), false);
					CommonFx.add(CommonFx.dust(colour, 0.8F), at, Vec3.ZERO, false);
				}
				if (animation.a == 1 && EffectQuality.chance(random, 0.5F)) {
					CommonFx.add(ParticleTypes.CHERRY_LEAVES, pos.add(random.nextGaussian(), 2.5D, random.nextGaussian()), Vec3.ZERO, false);
				}
			}
			case HallFxIds.TOKEN -> CommonFx.spiralIn(pos, GOLD, 2.0F, 6, age);
			default -> {
			}
		}
	}

	/** Three tiers climbing beside the candidate for 3 s; a failed tier stops halfway and crumbles. */
	private static void drawTiers(Animation animation, RandomSource random) {
		int tier = Math.min(2, animation.age / 20);
		float progress = (animation.age % 20) / 20.0F;
		boolean climbed = tier < animation.a;
		float height = climbed ? progress : Math.min(progress, 0.5F);
		double base = animation.pos.y + tier * 0.7D;
		double top = base + height * 0.7D;
		int n = EffectQuality.scale(4);
		DustParticleOptions dust = CommonFx.dust(TIER[tier], 1.0F);
		for (int i = 0; i < n; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double radius = 0.7D + random.nextDouble() * 0.2D;
			CommonFx.add(dust, animation.pos.x + Math.cos(angle) * radius, base + random.nextDouble() * (top - base + 0.05D),
					animation.pos.z + Math.sin(angle) * radius, 0.0D, 0.02D, 0.0D);
		}
		if (!climbed && progress > 0.5F && EffectQuality.chance(random, 0.6F)) {
			CommonFx.add(ParticleTypes.SMOKE, animation.pos.x + random.nextGaussian() * 0.4D, top, animation.pos.z + random.nextGaussian() * 0.4D,
					0.0D, -0.02D, 0.0D);
		}
		if (climbed && animation.age % 20 == 19) {
			CommonFx.ring(new Vec3(animation.pos.x, base + 0.7D, animation.pos.z), TIER[tier], 0.9F, 18);
		}
	}

	/** The stele raises Duan segments one by one in the soul colour, then a burst at the top. */
	private static void drawDuan(Animation animation, RandomSource random) {
		int duan = Math.round(animation.f);
		int shown = Math.min(duan, (int) Math.floor(animation.age * duan / 55.0F) + 1);
		Vec3 pos = animation.pos;
		DustParticleOptions dust = CommonFx.dust(animation.a, 1.2F);
		int n = EffectQuality.scale(2);
		for (int segment = 1; segment <= shown; segment++) {
			double y = pos.y + segment * 0.5D;
			for (int i = 0; i < n; i++) {
				CommonFx.add(dust, pos.x + random.nextGaussian() * 0.08D, y + random.nextDouble() * 0.4D, pos.z + random.nextGaussian() * 0.08D,
						0.0D, 0.0D, 0.0D);
			}
			if (animation.age == Math.round(segment * 55.0F / duan)) {
				CommonFx.ring(new Vec3(pos.x, y, pos.z), animation.a, 0.45F, 12);
			}
		}
		if (animation.age % 6 == 0 && pos.distanceToSqr(animation.target) > 0.5D) {
			CommonFx.beam(pos.add(0.0D, shown * 0.5D, 0.0D), animation.target.add(0.0D, 1.0D, 0.0D), animation.a, 0.05F);
		}
		if (animation.age == animation.life - 1) {
			CommonFx.burst(pos.add(0.0D, duan * 0.5D + 0.4D, 0.0D), animation.a, 30, 1.0F);
		}
	}

	/** The travellers' mist: soft clouds and pale motes, rising for an arrival, sinking for a farewell. */
	private static void mist(Vec3 pos, boolean arriving) {
		RandomSource random = CommonFx.random();
		int n = EffectQuality.scale(14);
		for (int i = 0; i < n; i++) {
			Vec3 at = pos.add(random.nextGaussian() * 0.5D, random.nextDouble() * 1.8D, random.nextGaussian() * 0.5D);
			CommonFx.add(ParticleTypes.CLOUD, at, new Vec3(random.nextGaussian() * 0.02D, arriving ? 0.03D : -0.01D, random.nextGaussian() * 0.02D), false);
		}
		CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), MIST, 16, 1.0F);
		if (EffectQuality.maximal()) {
			for (int i = 0; i < 6; i++) {
				CommonFx.add(ParticleTypes.POOF, pos.add(random.nextGaussian() * 0.4D, 0.2D, random.nextGaussian() * 0.4D), Vec3.ZERO, false);
			}
		}
	}

	/** The hall falls asleep: grey motes fall and smoke rises from the stele. */
	private static void dormant(Vec3 pos) {
		RandomSource random = CommonFx.random();
		CommonFx.burst(pos.add(0.0D, 0.8D, 0.0D), ASH, 20, 1.2F);
		for (int i = 0; i < EffectQuality.scale(8); i++) {
			CommonFx.add(ParticleTypes.SMOKE, pos.add(random.nextGaussian() * 0.3D, 0.8D, random.nextGaussian() * 0.3D),
					new Vec3(0.0D, 0.04D, 0.0D), false);
		}
	}

	private HallFx() {
	}
}
