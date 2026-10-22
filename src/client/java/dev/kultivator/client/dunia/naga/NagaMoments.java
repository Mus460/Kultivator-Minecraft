package dev.kultivator.client.dunia.naga;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.render.PillarRenderer;
import dev.kultivator.client.epic.render.RingRenderer;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.dunia.naga.NagaKind;
import dev.kultivator.dunia.naga.NagaPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The world moments of module B on the client (DESIGN 34.5.11, 34.5.12): a sighting (a banner), the subdued Jiao (beat
 * 42: a light pillar falls on it, a ring spreads, a short orbit shot and the card "Naga Tunduk", only Penuh and safe; a
 * stamp otherwise), an evolution (beat 43: the pillar rises from the beast, the card "Evolusi! <title>"), a failed
 * tribulation, the pearl's summon and dismissal, the moon howl and the roar. Shots obey Kamera sinematik and ShotGuard
 * through {@link DuniaScenes}; others see the light but never get a camera move. Particles scale with EffectQuality.
 */
final class NagaMoments {

	static void play(NagaPayloads.Moment moment) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Vec3 pos = moment.pos();
		NagaKind kind = NagaKind.byOrdinal(moment.kind());
		switch (moment.what()) {
			case NagaPayloads.MOMENT_SIGHTED -> DuniaScenes.banner(Component.translatable("kultivator.naga.banner.sighted", kind.displayName()));
			case NagaPayloads.MOMENT_SUBDUED -> subdued(moment, pos, kind);
			case NagaPayloads.MOMENT_EVOLVED -> evolved(moment, pos, kind);
			case NagaPayloads.MOMENT_FAILED -> {
				CommonFx.burst(pos.add(0.0D, 0.5D, 0.0D), 0x6A5A8A, 24, 1.6F);
				if (moment.own()) {
					DuniaScenes.banner(Component.translatable("kultivator.naga.banner.failed"));
				}
			}
			case NagaPayloads.MOMENT_SUMMON -> pearlSwirl(pos, kind.glow, true);
			case NagaPayloads.MOMENT_DISMISS -> pearlSwirl(pos, kind.glow, false);
			case NagaPayloads.MOMENT_HOWL -> {
				int rgb = SpiritBeasts.tierColor(BeastData.WOLF, moment.tier());
				for (int i = 0; i < 3; i++) {
					CommonFx.ring(pos.add(0.0D, 0.3D + i * 0.4D, 0.0D), rgb, 2.0F + i * 2.5F, 14 + i * 6);
				}
				CommonFx.add(CommonFx.glow(0xFFFFFF, 2.0F, 30), pos.add(0.0D, 2.2D, 0.0D), Vec3.ZERO, false);
			}
			case NagaPayloads.MOMENT_FOXFIRE -> CommonFx.burst(pos.add(0.0D, 0.5D, 0.0D), SpiritBeasts.tierColor(BeastData.FOX, moment.tier()), 18,
					1.2F);
			case NagaPayloads.MOMENT_ROAR -> CommonFx.ring(pos.add(0.0D, 0.6D, 0.0D), kind.glow, 6.0F, 28);
			case NagaPayloads.MOMENT_ENRAGED -> enraged(pos, kind);
			case NagaPayloads.MOMENT_WHIRL -> NagaStrikes.whirl(moment);
			case NagaPayloads.MOMENT_VEIL -> NagaStrikes.veil(moment);
			case NagaPayloads.MOMENT_THUNDER_MARK -> NagaStrikes.mark(moment);
			case NagaPayloads.MOMENT_TRIB_START -> tribulationSky(moment, pos);
			case NagaPayloads.MOMENT_GUARD -> {
				for (int i = 0; i < 3; i++) {
					CommonFx.ring(pos.add(0.0D, 0.15D + i * 0.5D, 0.0D), 0xFFE08A, 1.5F + i * 0.8F, 16);
				}
				CommonFx.pillar(pos, 0xFFE08A, 3.0F);
			}
			default -> {
			}
		}
	}

	/** Sky grades of module B: a little above the ambient sky (spirit weather 0) and below the ruins' big moments. */
	private static final int SKY_PRIORITY = 4;

	/**
	 * Amukan Naga near you: a banner, a burst of its colour and, within 96 blocks, the sky darkens toward its element for
	 * about 30 s (thunder: the storm grade; water: the cold grade; cloud: ash white). "Langit berubah" gates the grade.
	 */
	private static void enraged(Vec3 pos, NagaKind kind) {
		Minecraft minecraft = Minecraft.getInstance();
		CommonFx.burst(pos, 0xFF5030, 40, 4.0F);
		for (int i = 0; i < 2; i++) {
			CommonFx.ring(pos.add(0.0D, i * 0.8D, 0.0D), kind.glow, 5.0F + i * 3.0F, 24);
		}
		if (minecraft.player == null || minecraft.player.position().distanceToSqr(pos) > 96.0D * 96.0D) {
			return;
		}
		DuniaScenes.banner(Component.translatable("kultivator.naga.banner.enraged", kind.displayName()));
		SkyDirector.Grade grade = switch (kind) {
			case AIR -> SkyDirector.Grade.SK_ICE;
			case AWAN -> SkyDirector.Grade.SK_ASH;
			case PETIR -> SkyDirector.Grade.SK_TRIB;
		};
		SkyDirector.grade(grade, kind == NagaKind.AWAN ? 0.3F : 0.45F, 0.25F, 40, SKY_PRIORITY, pos, true, 1.0F, pos.y);
		SkyDirector.holdThenRelease(600, 80, SKY_PRIORITY);
	}

	/** A beast tribulation within 48 blocks: the tribulation sky for its length, then it clears. */
	private static void tribulationSky(NagaPayloads.Moment moment, Vec3 pos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.player.position().distanceToSqr(pos) > 48.0D * 48.0D) {
			return;
		}
		SkyDirector.grade(SkyDirector.Grade.SK_TRIB, 0.55F, 0.35F, 60, SKY_PRIORITY, pos, true, 1.0F, pos.y);
		SkyDirector.holdThenRelease(Math.max(100, moment.seed()), 80, SKY_PRIORITY);
		if (moment.own()) {
			DuniaScenes.banner(Component.translatable("kultivator.naga.banner.tribulation",
					Component.translatable(BeastData.titleKey(moment.line(), Math.max(0, moment.tier() - 1))), moment.tier()));
		}
	}

	private static void subdued(NagaPayloads.Moment moment, Vec3 pos, NagaKind kind) {
		CommonFx.burst(pos, kind.glow, 50, 3.0F);
		Component title = Component.translatable("kultivator.naga.card.subdued");
		Component sub = Component.translatable("kultivator.naga.card.subdued.sub", kind.displayName());
		Scene scene = DuniaScenes.world("naga/tunduk", pos, moment.seed(), 120, moment.own());
		if (scene == null) {
			if (moment.own()) {
				DuniaScenes.banner(title);
			}
			return;
		}
		scene.add(new PillarRenderer(0, 70.0F, pos.subtract(0.0D, 1.0D, 0.0D), 64.0F, kind.glow, 1.1F).descend(48.0F, 18.0F));
		scene.add(new RingRenderer(8, 40.0F, pos.subtract(0.0D, 0.5D, 0.0D), 1.0F, 14.0F, 1.4F, 30.0F, kind.glow, 0.8F).rune(1.5F));
		DuniaScenes.orbit(scene, 6, 11.0F, 3.0F, 110.0F, 3.5F);
		DuniaScenes.card(scene, 24, title, sub, "龍", kind.glow);
	}

	private static void evolved(NagaPayloads.Moment moment, Vec3 pos, NagaKind kind) {
		int line = moment.line();
		int tier = moment.tier();
		int rgb = SpiritBeasts.tierColor(line, tier);
		CommonFx.burst(pos.add(0.0D, 0.8D, 0.0D), rgb, 40 + 10 * (tier - 3), 2.4F);
		Component name = Component.translatable(BeastData.titleKey(line, tier));
		Component title = Component.translatable("kultivator.naga.card.evolve", name);
		Component sub = Component.translatable("kultivator.naga.card.evolve.sub", tier);
		Scene scene = DuniaScenes.world("evolusi/" + BeastData.lineOf(line), pos, moment.seed(), 110, moment.own());
		if (scene == null) {
			if (moment.own()) {
				DuniaScenes.banner(title);
			}
			return;
		}
		scene.add(new PillarRenderer(0, 60.0F, pos, 48.0F, rgb, 0.9F).rise(16.0F));
		scene.add(new RingRenderer(4, 36.0F, pos.add(0.0D, 0.1D, 0.0D), 0.6F, 7.0F + tier, 1.0F, 24.0F, rgb, 0.85F).crown(EffectQuality.scale(18)));
		DuniaScenes.orbit(scene, 4, 6.0F, 2.2F, 90.0F, 3.0F);
		DuniaScenes.card(scene, 20, title, sub, "化", rgb);
	}

	/** The pearl's light coils into a Jiao (summon) or the Jiao folds back into light (dismiss). */
	private static void pearlSwirl(Vec3 pos, int rgb, boolean summon) {
		RandomSource random = CommonFx.random();
		if (summon) {
			CommonFx.spiralIn(pos, rgb, 3.0F, 24, random.nextFloat() * 10.0F);
			CommonFx.add(CommonFx.glow(0xFFFFFF, 2.5F, 20), pos, Vec3.ZERO, false);
		} else {
			CommonFx.burst(pos, rgb, 30, 2.5F);
			for (int i = 0; i < EffectQuality.scale(8); i++) {
				CommonFx.add(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, random.nextGaussian() * 0.1D, 0.05D + random.nextFloat() * 0.1D,
						random.nextGaussian() * 0.1D);
			}
		}
	}

	private NagaMoments() {
	}
}
