package dev.kultivator.client.alam;

import dev.kultivator.alam.AlamFxIds;
import dev.kultivator.alam.RealmRelicEntity;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Secret realm visuals (DESIGN 25.2 item 4, sub-ids in {@code alam.AlamFxIds}): the gate of glyph rings during the omen,
 * telegraph rings that hold until they strike, lava jets, the crystal channel, phantom exits shattering, the clear burst,
 * the Divine Sense marks and the fade of the Formasi Teleportasi; plus the ambient motes of nearby relics and the
 * {@link RealmScenery} tick. Particles only, scaled by Effect Quality.
 */
public final class AlamFx {

	/** A telegraph ring redrawn until it strikes. */
	private record Ring(Vec3 at, float radius, int rgb, long until) {
	}

	/** A gate of glyph rings during an omen. */
	private record GateFx(Vec3 at, int rgb, long until) {
	}

	private static final List<Ring> RINGS = new ArrayList<>();
	private static final List<GateFx> GATES = new ArrayList<>();
	private static long ticks;

	/** One {@code FxType.ALAM} event. */
	public static void play(Payloads.Fx fx) {
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		Vec3 target = new Vec3(fx.tx(), fx.ty(), fx.tz());
		RandomSource random = CommonFx.random();
		switch (fx.a()) {
			case AlamFxIds.GATE -> {
				int rgb = RealmScenery.color(fx.b());
				GATES.removeIf(gate -> gate.at().distanceToSqr(pos) < 4.0D);
				GATES.add(new GateFx(pos, rgb, ticks + Math.max(20, Math.round(fx.f() * 20.0F))));
				CommonFx.spiralIn(pos.add(0.0D, 1.5D, 0.0D), rgb, 3.5F, 24, ticks);
			}
			case AlamFxIds.FADE -> {
				ScreenFlash.add(0x000000, ScreenFlash.MAX_ALPHA, ScreenFlash.MAX_TICKS);
				ScreenFlash.vignette(RealmScenery.color(fx.b()), 20);
			}
			case AlamFxIds.TELEGRAPH -> {
				int rgb = (int) Math.round(target.x) & 0xFFFFFF;
				RINGS.add(new Ring(pos, Math.max(0.5F, fx.f()), rgb == 0 ? 0xFF5A1E : rgb, ticks + Math.max(4, fx.b())));
				CommonFx.ring(pos, rgb, fx.f(), 32);
			}
			case AlamFxIds.JET -> {
				int n = EffectQuality.scale(fx.b() == 1 ? 30 : 18);
				for (int i = 0; i < n; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0D;
					double r = random.nextDouble() * fx.f();
					Vec3 at = pos.add(Math.cos(angle) * r, 0.1D, Math.sin(angle) * r);
					if (fx.b() == 1) {
						CommonFx.add(ParticleTypes.CLOUD, at.x, at.y, at.z, Math.cos(angle) * 0.2D, 0.05D, Math.sin(angle) * 0.2D);
					} else {
						CommonFx.add(ParticleTypes.FLAME, at.x, at.y, at.z, 0.0D, 0.25D + random.nextDouble() * 0.25D, 0.0D);
						if (i % 4 == 0) {
							CommonFx.add(ParticleTypes.LAVA, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
						}
					}
				}
				if (fx.b() == 1) {
					CommonFx.ring(pos, 0xC8B090, fx.f(), 40);
				}
			}
			case AlamFxIds.CRYSTAL -> {
				if (fx.b() >= 100) {
					CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), 0x9FF3FF, 40, 2.5F);
					for (int i = 0; i < EffectQuality.scale(20); i++) {
						CommonFx.add(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 1.0D, pos.z, random.nextGaussian() * 0.2D, random.nextDouble() * 0.2D,
								random.nextGaussian() * 0.2D);
					}
					ScreenFlash.add(0x9FF3FF, 0.2F, 6);
				} else {
					CommonFx.spiralIn(pos.add(0.0D, 1.0D, 0.0D), 0x9FF3FF, 1.5F + fx.b() / 100.0F, 8, ticks);
				}
			}
			case AlamFxIds.PHANTOM_EXIT -> {
				CommonFx.burst(pos.add(0.0D, 1.0D, 0.0D), 0xB070FF, 30, 1.5F);
				for (int i = 0; i < EffectQuality.scale(16); i++) {
					CommonFx.add(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1.0D, pos.z, random.nextGaussian() * 0.3D, random.nextDouble() * 0.3D,
							random.nextGaussian() * 0.3D);
				}
				ScreenFlash.add(0x6A2AA8, 0.25F, 6);
			}
			case AlamFxIds.CLEAR -> {
				int rgb = RealmScenery.color(fx.b());
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), rgb, fx.f(), 64);
				CommonFx.ring(pos.add(0.0D, 0.6D, 0.0D), 0xFFD54A, fx.f() * 0.6F, 48);
				for (int i = 0; i < EffectQuality.scale(24); i++) {
					CommonFx.add(ParticleTypes.END_ROD, pos.x, pos.y + 0.5D, pos.z, random.nextGaussian() * 0.15D, 0.1D + random.nextDouble() * 0.2D,
							random.nextGaussian() * 0.15D);
				}
			}
			case AlamFxIds.QI_MARK -> {
				for (int i = 0; i < EffectQuality.scale(10); i++) {
					CommonFx.add(ParticleTypes.GLOW, pos.x + random.nextGaussian() * 0.4D, pos.y + random.nextDouble() * 0.6D,
							pos.z + random.nextGaussian() * 0.4D, 0.0D, 0.02D, 0.0D);
				}
			}
			case AlamFxIds.TRUE_EXIT -> CommonFx.pillar(pos, 0xFFD54A, Math.max(3.0F, fx.f()));
			case AlamFxIds.PAD -> {
				int rgb = RealmScenery.color(fx.b());
				CommonFx.ring(pos.add(0.0D, 0.05D, 0.0D), rgb, fx.f(), 40);
				CommonFx.spiralIn(pos.add(0.0D, 0.6D, 0.0D), rgb, fx.f() + 1.0F, 18, ticks);
			}
			case AlamFxIds.GATE_OPEN -> {
				int rgb = fx.b() == 2 ? 0xFFD54A : fx.b() == 1 ? 0xD5322A : 0xA0A0A0;
				for (int i = 0; i < EffectQuality.scale(24); i++) {
					CommonFx.add(ParticleTypes.CLOUD, pos.x + random.nextGaussian() * 0.8D, pos.y + random.nextDouble() * 3.0D,
							pos.z + random.nextGaussian() * 0.4D, 0.0D, 0.02D, 0.0D);
				}
				CommonFx.burst(pos.add(0.0D, 1.5D, 0.0D), rgb, 24, 2.0F);
			}
			default -> {
			}
		}
	}

	/** Every client tick: the telegraphs and gates that last, the relics' motes and the scenery. */
	public static void tick() {
		ticks++;
		RealmScenery.tick();
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			RINGS.clear();
			GATES.clear();
			return;
		}
		if (!RINGS.isEmpty() && ticks % 4L == 0L) {
			for (Iterator<Ring> it = RINGS.iterator(); it.hasNext(); ) {
				Ring ring = it.next();
				if (ticks >= ring.until()) {
					it.remove();
					continue;
				}
				CommonFx.ring(ring.at(), ring.rgb(), ring.radius(), 24);
				CommonFx.ring(ring.at(), ring.rgb(), ring.radius() * (1.0F - (ring.until() - ticks) / 30.0F), 12);
			}
		}
		if (!GATES.isEmpty() && ticks % 3L == 0L) {
			for (Iterator<GateFx> it = GATES.iterator(); it.hasNext(); ) {
				GateFx gate = it.next();
				if (ticks >= gate.until()) {
					it.remove();
					continue;
				}
				// Three upright glyph rings turning around the gate.
				int points = EffectQuality.scale(14);
				for (int ringIndex = 0; ringIndex < 3; ringIndex++) {
					double radius = 1.2D + ringIndex * 0.7D;
					double spin = ticks * 0.05D * (ringIndex % 2 == 0 ? 1.0D : -1.0D);
					for (int i = 0; i < points; i++) {
						double angle = spin + Math.PI * 2.0D * i / points;
						CommonFx.add(CommonFx.dust(gate.rgb(), 0.9F), gate.at().x + Math.cos(angle) * radius, gate.at().y + 1.6D + Math.sin(angle) * radius,
								gate.at().z, 0.0D, 0.0D, 0.0D);
					}
				}
			}
		}
		if (ticks % 5L == 0L && RealmScenery.scene() != null) {
			RandomSource random = level.getRandom();
			for (RealmRelicEntity relic : RealmScenery.relicsNear(level, minecraft.player.position(), 32.0D)) {
				Vec3 at = relic.position();
				switch (relic.kind()) {
					case RealmRelicEntity.KIND_PAD -> CommonFx.ring(at.add(0.0D, 0.1D, 0.0D), RealmScenery.color(RealmScenery.realm()), 1.2F, 10);
					case RealmRelicEntity.KIND_EXIT -> CommonFx.add(ParticleTypes.PORTAL, at.x + random.nextGaussian() * 0.4D, at.y + random.nextDouble() * 2.0D,
							at.z, 0.0D, 0.0D, 0.0D);
					case RealmRelicEntity.KIND_CRYSTAL -> {
						if (relic.state() != 1) {
							CommonFx.add(ParticleTypes.SNOWFLAKE, at.x + random.nextGaussian() * 0.5D, at.y + random.nextDouble() * 2.0D,
									at.z + random.nextGaussian() * 0.5D, 0.0D, 0.01D, 0.0D);
						}
					}
					default -> {
					}
				}
			}
		}
	}

	/** Disconnect: forget everything. */
	public static void clear() {
		RINGS.clear();
		GATES.clear();
		RealmScenery.reset();
	}

	private AlamFx() {
	}
}
