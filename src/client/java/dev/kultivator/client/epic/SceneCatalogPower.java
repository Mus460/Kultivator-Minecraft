package dev.kultivator.client.epic;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.fx.EpicDisplays;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.epic.fx.PointClouds;
import dev.kultivator.client.epic.render.AuroraRenderer;
import dev.kultivator.client.epic.render.BoltRenderer;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import dev.kultivator.client.epic.render.GateRenderer;
import dev.kultivator.client.epic.render.GiantFigureRenderer;
import dev.kultivator.client.epic.render.PillarRenderer;
import dev.kultivator.client.epic.render.RaysRenderer;
import dev.kultivator.client.epic.render.RingRenderer;
import dev.kultivator.client.epic.render.TrailRenderer;
import dev.kultivator.client.epic.render.VortexRenderer;
import dev.kultivator.client.epic.screen.Letterbox;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.EpicSound;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.VisualState;
import dev.kultivator.epic.EpicColors;
import dev.kultivator.epic.EpicIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static dev.kultivator.client.epic.SceneCatalog.GLOW;
import static dev.kultivator.client.epic.SceneCatalog.GOLD;
import static dev.kultivator.client.epic.SceneCatalog.JADE;
import static dev.kultivator.client.epic.SceneCatalog.PALE_GOLD;
import static dev.kultivator.client.epic.SceneCatalog.R_V;
import static dev.kultivator.client.epic.SceneCatalog.SEAL_RED;
import static dev.kultivator.client.epic.SceneCatalog.WHITE_GOLD;
import static dev.kultivator.client.epic.SceneCatalog.emit;
import static dev.kultivator.client.epic.SceneCatalog.full;
import static dev.kultivator.client.epic.SceneCatalog.glyph;
import static dev.kultivator.client.epic.SceneCatalog.hitStop;
import static dev.kultivator.client.epic.SceneCatalog.idx;
import static dev.kultivator.client.epic.SceneCatalog.lite;
import static dev.kultivator.client.epic.SceneCatalog.music;
import static dev.kultivator.client.epic.SceneCatalog.n;
import static dev.kultivator.client.epic.SceneCatalog.shake;
import static dev.kultivator.client.epic.SceneCatalog.shot;
import static dev.kultivator.client.epic.SceneCatalog.shotPenuh;
import static dev.kultivator.client.epic.SceneCatalog.sky;
import static dev.kultivator.client.epic.SceneCatalog.skyRelease;
import static dev.kultivator.client.epic.SceneCatalog.slowmo;
import static dev.kultivator.client.epic.SceneCatalog.snap;
import static dev.kultivator.client.epic.SceneCatalog.stamp;
import static dev.kultivator.client.epic.SceneCatalog.t;
import static dev.kultivator.client.epic.SceneCatalog.tier;
import static dev.kultivator.client.epic.SceneCatalog.upper;

/**
 * Storyboards of DESIGN 23.13 group D (power: Wujud Dharma, Wilayah Dao, the 12 Jurus Abadi and 11 Jurus Dewa, Mode
 * Kehancuran) and the realm layers B1-B8 that play inside a passed tribulation. Client-derived scenes (Dharma, Domain,
 * ultimates, one's own Kehancuran casts) use local SAFE checks; ultimates use the technique's own Sumber line and add no
 * canon claim.
 */
final class SceneCatalogPower {

	// ================================================================ B1-B8 realm layers

	/** The realm layer of a passed tribulation (inside A4, from 0.5 s). */
	static void realmVisual(Scene s, int realm, int quality, int layer, int delay) {
		Vec3 feet = s.pos;
		Vec3 dantian = feet.add(0.0D, 0.9D, 0.0D);
		Realm r = Realm.byOrdinal(realm);
		boolean heavenly = quality == BreakthroughQuality.HEAVENLY.ordinal();
		Element pillarElement = s.get("pillar");
		switch (r) {
			case GOLDEN_CORE -> {
				// B2: the pillar narrows into a thread; a gold core condenses, pulses three times and seals.
				if (pillarElement instanceof PillarRenderer pillar) {
					s.at(delay + 10, () -> pillar.glowTo(0.3F, 20));
				}
				int rings = heavenly ? 9 : 3;
				emit(s, delay + 10, 60, (tick, self) -> {
					float spin = 0.15F + tick * 0.01F;
					orb(s, s.pos.add(0.0D, 0.9D, 0.0D), 0.35F, rings, tick * spin, GOLD);
				});
				for (int k = 0; k < 3; k++) {
					s.add(new RingRenderer(delay + 20 + k * 12, 10, dantian, 4.0F, 12.0F, 1.0F, 8.0F, GOLD, 0.7F));
				}
				SceneCatalog.flash(s, delay + 60, GOLD, 0.2F, 4, 0);
			}
			case NASCENT_SOUL -> {
				// B3: a seated infant rises above the head, opens its eyes, circles once and streaks back in.
				emit(s, delay + 10, 60, (tick, self) -> {
					float t = tick / 60.0F;
					double rise = t < 0.3F ? t / 0.3F * 1.4D : 1.4D;
					double angle = t > 0.3F && t < 0.8F ? (t - 0.3F) / 0.5F * Math.PI * 2.0D : 0.0D;
					Vec3 head = s.feet(0.0F).add(0.0D, 1.8D + rise * (t < 0.85F ? 1.0D : Math.max(0.0D, 1.0D - (t - 0.85F) / 0.15F)), 0.0D);
					Vec3 at = head.add(Math.cos(angle) * 0.8D, 0.0D, Math.sin(angle) * 0.8D);
					int aura = 0xB98CFF;
					EpicParticles.outline(PointClouds.infant(), at.add(0.0D, -0.3D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D), 0.6D, n(s, 10, 3),
							EpicParticles.dust(aura, 0.5F), false);
					if (t > 0.2F) {
						EpicParticles.add(EpicParticles.dust(0xFFFFFF, 0.4F), at.x - 0.06D, at.y + 0.18D, at.z, 0.0D, 0.0D, 0.0D);
						EpicParticles.add(EpicParticles.dust(0xFFFFFF, 0.4F), at.x + 0.06D, at.y + 0.18D, at.z, 0.0D, 0.0D, 0.0D);
					}
				});
			}
			case DEITY_TRANSFORMATION -> {
				// B4: a 4× gold figure rises behind; heaven and earth answer with a shell and a wide ring.
				Entity subject = s.subject();
				Vec3 behind = feet.add(SceneCatalog.forward(s.yaw()).scale(-2.0D));
				s.add(new GiantFigureRenderer(delay + 10, 60, behind, s.yaw(), 7.2F,
						subject == null ? EpicRenderTypes.IMMORTAL_AVATAR : GiantFigureRenderer.skinOf(subject, EpicRenderTypes.IMMORTAL_AVATAR), GOLD, 0.35F)
						.moveTo(behind.add(0.0D, 0.5D, 0.0D), 60));
				emit(s, delay + 10, 30, (tick, self) -> EpicParticles.converge(s.pos.add(0.0D, 1.0D, 0.0D), 32.0F, n(s, 8, 2), EpicParticles.dust(PALE_GOLD, 1.2F),
						0.9F, true));
				s.at(delay + 40, () -> EpicParticles.shell(s.pos.add(0.0D, 1.0D, 0.0D), 32.0F, n(s, 80, 16), EpicParticles.dust(GOLD, 2.0F)));
				s.add(new RingRenderer(delay + 40, 40, feet, 4.0F, 48.0F, 4.0F, 30.0F, GOLD, 0.5F));
			}
			case DAO_UNION -> {
				// B5: the Dao glyph in the cloud hole, a Dao-coloured pillar and one dome pulse.
				Entity subject = s.subject();
				VisualState visual = subject == null ? VisualState.NONE : ClientCultivation.visual(subject);
				int dao = visual.dao();
				int color = dao >= 0 && dao < Dao.values().length ? Dao.values()[dao].color() : PALE_GOLD;
				String glyphText = dao >= 0 && dao < SceneCatalog.DAO_GLYPH.length ? SceneCatalog.DAO_GLYPH[dao] : "道";
				VortexRenderer vortex = SceneCatalog.vortex(s);
				double hole = vortex != null ? vortex.baseY() : feet.y + 64.0D;
				glyph(s, delay + 10, new Vec3(feet.x, hole + 4.0D, feet.z), glyphText, color, 32.0F, 120);
				s.add(new PillarRenderer(delay + 15, 110, feet, (float) (hole - feet.y), color, GLOW[idx(s.s)] * 0.8F));
				s.add(new RingRenderer(delay + 20, 30, feet, 2.0F, 24.0F, 3.0F, 20.0F, color, 0.45F));
				s.at(delay + 10, () -> SkyDirector.dao(color, 0.3F, 40, s.priority, s.pos, s.own, 1.0F));
				skyRelease(s, delay + 50, 80, 60);
			}
			case IMMORTAL_ASCENSION -> {
				// B6: Immortal meridians trace outward over the body (8 + 12 L lines, max 60).
				int lines = Math.min(60, 8 + 12 * Math.max(1, layer));
				int count = Math.min(lines, tier() >= 2 ? lines : tier() == 1 ? 24 : 0);
				for (int k = 0; k < count; k++) {
					double angle = Math.PI * 2.0D * k / Math.max(1, count);
					double up = (k % 5) * 0.35D;
					Vec3 a = dantian.add(Math.cos(angle) * 0.1D, up * 0.3D, Math.sin(angle) * 0.1D);
					Vec3 b = feet.add(Math.cos(angle) * 0.45D, 0.2D + up, Math.sin(angle) * 0.45D);
					s.add(new TrailRenderer(delay + 10 + k % 10, 40, a, b, 0.1F, 0.05F, k % 2 == 0 ? JADE : GOLD, 0.9F, false, s.seed + k).travel(12));
				}
				if (layer >= 7) {
					Element deity = s.get("deity");
					if (deity instanceof GiantFigureRenderer figure) {
						s.at(delay, figure::bow);
					}
				}
			}
			case TRUE_IMMORTAL -> {
				// B7: the Heavenly Gate opens in the eye.
				VortexRenderer vortex = SceneCatalog.vortex(s);
				double base = vortex != null ? vortex.baseY() : feet.y + 112.0D;
				Vec3 gateAt = new Vec3(feet.x, base + 16.0D, feet.z);
				s.add("gate", new GateRenderer(delay, 220, gateAt, feet.add(0.0D, 1.6D, 0.0D), 64.0F, 96.0F, GOLD, 0.85F).openAt(0.0F).open(1.0F, 50));
				glyph(s, delay + 30, gateAt.add(0.0D, 96.0D * 0.86D, 0.0D), "天門", GOLD, 10.0F, 180);
				if (pillarElement instanceof PillarRenderer pillar) {
					s.at(delay + 10, () -> pillar.glowTo(3.5F, 20));
				}
			}
			default -> {
			}
		}
		if (heavenly) {
			// B8: Kualitas Surgawi overlay: slow motion, white sky, the aurora up the pillar.
			slowmo(s, delay + 10, 20);
			sky(s, delay + 10, SkyDirector.Grade.SK_WHITE, 0.8F, 0.0F, 20);
			sky(s, delay + 70, SkyDirector.Grade.SK_GOLD, 0.8F, 0.0F, 40);
			if (tier() >= 1) {
				s.add(new AuroraRenderer(delay + 10, 120, feet, 2.0F + GLOW[idx(s.s)], 40.0F * (1.0F + 0.1F * s.s)));
			}
		}
	}

	/** An F_ORB: a sphere of r 0.35 with {@code rings} tilted orbit rings (point cloud). */
	private static void orb(Scene s, Vec3 c, float r, int rings, double turn, int rgb) {
		int dots = EpicParticles.grant(n(s, 10 + rings * 4, 4), false);
		for (int i = 0; i < dots; i++) {
			int ring = i % (rings + 1);
			double a = turn + i * 0.7D;
			double x;
			double y;
			double z;
			if (ring == 0) {
				double u = EpicParticles.random().nextDouble() * 2.0D - 1.0D;
				double phi = EpicParticles.random().nextDouble() * Math.PI * 2.0D;
				double k = Math.sqrt(1.0D - u * u);
				x = Math.cos(phi) * k * r;
				y = u * r;
				z = Math.sin(phi) * k * r;
			} else {
				double tilt = ring * Math.PI / (rings + 1);
				double rr = r * 1.8D;
				x = Math.cos(a) * rr;
				y = Math.sin(a) * rr * Math.sin(tilt);
				z = Math.sin(a) * rr * Math.cos(tilt);
			}
			EpicParticles.add(EpicParticles.dust(rgb, 0.5F), c.x + x, c.y + y, c.z + z, 0.0D, 0.0D, 0.0D);
		}
	}

	/** B6: the Pintu Keabadian opens a further L/9 of 80° per layer (L9: fully, the clouds dissipate at once). */
	static void immortalDoor(Scene s, SceneCatalog.Trib info, int delay, Vec3 eye, Vec3 feet) {
		int layer = Math.max(1, Math.min(9, info.layer));
		float open = layer / 9.0F;
		Vec3 doorAt = eye.add(0.0D, 8.0D, 0.0D);
		GateRenderer door = new GateRenderer(delay, 220, doorAt, feet.add(0.0D, 1.6D, 0.0D), 24.0F, 36.0F, 0xD8F0C8, 0.85F)
				.openAt(Math.max(0.0F, open - 1.0F / 9.0F)).open(open, layer >= 9 ? 30 : 50);
		s.add("door", door);
		s.add(new RaysRenderer(delay + 20, 160, doorAt.add(0.0D, 4.0D, 0.0D), (float) (doorAt.y - feet.y), 8, 8.0F, 1.0F + layer * 0.6F, WHITE_GOLD,
				0.22F, false));
		shot(s, delay + 60, () -> Shots.craneUp(3.0F, 1.6F, 6.0F, 18.0F, 0.0F, -60.0F, 3.0F), CinematicDirector.KIND_TRIBULATION, true);
	}

	// ================================================================ B1 Foundation pillars (also disciples, E1)

	/** Nine (ten at Surgawi / Sempurna) small pillars rise at r 5 one every 0.2 s, graded by their cracks. */
	static void foundationPillars(Scene s, int quality, int delay, boolean disciple) {
		Vec3 feet = s.pos;
		boolean heavenly = quality == BreakthroughQuality.HEAVENLY.ordinal();
		boolean perfect = quality == BreakthroughQuality.PERFECT.ordinal();
		boolean cracked = quality == BreakthroughQuality.CRACKED.ordinal();
		int color = heavenly ? WHITE_GOLD : perfect ? GOLD : cracked ? 0xB08A5A : 0xE0D8C0;
		for (int k = 0; k < 9; k++) {
			double angle = Math.PI * 2.0D * k / 9.0D;
			Vec3 base = feet.add(Math.cos(angle) * 5.0D, 0.0D, Math.sin(angle) * 5.0D);
			int at = delay + k * 4;
			PillarRenderer pillar = new PillarRenderer(at, 40 - k * 4 + 4, base, 20.0F, color, 0.35F).rise(12);
			s.add(pillar);
			if (cracked && k % 3 == 0) {
				s.at(at + 10, () -> pillar.glowTo(0.12F, 4));
				s.at(at + 16, () -> pillar.glowTo(0.35F, 4));
			}
			float note = 0.5F + k * 0.12F;
			if (s.own) {
				s.at(at, () -> EpicSound.at(base, SoundEvents.NOTE_BLOCK_BASS.value(), 0.8F, note));
			}
			if (!perfect && !heavenly) {
				// The cracks: dark bands across the pillar (two heavy ones when cracked, one hairline when normal).
				int bands = cracked ? 2 : 1;
				emit(s, at + 8, 30, (tick, self) -> {
					if (tick % 3 == 0) {
						for (int b = 0; b < bands; b++) {
							EpicParticles.ring(base.add(0.0D, 6.0D + b * 5.0D, 0.0D), 0.4F, cracked ? 6 : 3, EpicParticles.dust(0x201810, cracked ? 1.4F : 0.8F),
									0.0F);
						}
					}
				});
			}
		}
		if (perfect) {
			// Three thin harmless visual bolts touch the pillar tops (no damage, no screen flash).
			for (int k = 0; k < 3; k++) {
				double angle = Math.PI * 2.0D * (k * 3) / 9.0D;
				Vec3 top = feet.add(Math.cos(angle) * 5.0D, 20.0D, Math.sin(angle) * 5.0D);
				s.add(new BoltRenderer(delay + 12 + k * 2, 6, top.add(0.0D, 24.0D, 0.0D), top, null, 0.1F, 0xD8C8FF, 0xFFFFFF, s.seed + k, 0, true));
			}
		}
		if (heavenly) {
			s.add(new PillarRenderer(delay + 36, 60, feet, 40.0F, WHITE_GOLD, 0.6F).rise(12));
			s.add(new AuroraRenderer(delay + 40, 80, feet, 2.6F, 40.0F));
			sky(s, delay + 36, SkyDirector.Grade.SK_WHITE, 0.7F, 0.0F, 20);
			skyRelease(s, delay + 60, 40, 60);
			slowmo(s, delay + 36, 20);
		}
		// Converge into the dantian, then a gold ring.
		s.at(delay + 40, () -> EpicParticles.converge(s.pos.add(0.0D, 0.9D, 0.0D), 5.0F, n(s, 40, 8), EpicParticles.dust(color, 1.2F), 0.4F, false));
		s.add(new RingRenderer(delay + 44, 24, feet, 1.0F, 6.0F, 1.2F, 12.0F, GOLD, 0.7F));
		sky(s, delay, SkyDirector.Grade.SK_JADE, 0.2F, 0.0F, 30);
		skyRelease(s, delay + 40, 20, 40);
		shot(s, delay, () -> Shots.craneUp(3.5F, 1.6F, 10.0F, 12.0F, 10.0F, 40.0F, 3.0F), disciple ? CinematicDirector.KIND_DISCIPLE
				: CinematicDirector.KIND_BREAKTHROUGH, true);
		if (s.own) {
			s.at(delay + 4, () -> EpicSound.at(feet, SoundEvents.BELL_RESONATE, 1.0F, 1.0F));
		}
		if (!disciple) {
			music(s, delay, heavenly ? MusicCue.Cue.M_GLORY : MusicCue.Cue.M_TRIUMPH, heavenly ? 18 : 14);
			String sub = heavenly ? "foundation.sub_heavenly" : perfect ? "foundation.sub_perfect" : cracked ? "foundation.sub_cracked" : "foundation.sub_normal";
			full(s, delay + 20, t("foundation.title"), () -> t(sub), "基", SEAL_RED, 70, card -> {
				if (heavenly) {
					card.second("天");
				}
				if (perfect) {
					card.goldRim();
				}
			});
			if (heavenly) {
				full(s, delay + 110, t("heavenly.title"), () -> t("heavenly.sub"), "天", 0xC8A030, 60, TitleCard.Card::rainbow);
			}
			s.end(delay + 220);
		}
	}

	/** B1 failure: the pillars rise to half height, crack and dissolve. */
	static void foundationCollapse(Scene s, int delay) {
		Vec3 feet = s.pos;
		for (int k = 0; k < 9; k++) {
			double angle = Math.PI * 2.0D * k / 9.0D;
			Vec3 base = feet.add(Math.cos(angle) * 5.0D, 0.0D, Math.sin(angle) * 5.0D);
			s.add(new PillarRenderer(delay + k * 2, 24, base, 10.0F, 0x8A7A6A, 0.3F).rise(10));
			s.at(delay + 24 + k, () -> EpicParticles.column(base, 8.0F, 0.6F, n(s, 10, 3), ParticleTypes.SMOKE, 0.05F));
		}
		stamp(s, delay + 10, t("foundation_fail"), "基", 0x6A5A50);
		shotPenuh(s, delay + 6, () -> Shots.still(4.0F, 3.5F, 1.2F, 1.5F).asResult(), CinematicDirector.KIND_BREAKTHROUGH);
	}

	// ================================================================ D1 Wujud Dharma

	static void dharma(Scene s, VisualState visual) {
		int realm = visual.realm();
		float scale = realm >= Realm.TRUE_IMMORTAL.ordinal() ? 4.0F : realm >= Realm.IMMORTAL_ASCENSION.ordinal() ? 3.0F
				: realm >= Realm.DAO_UNION.ordinal() ? 2.5F : 2.0F;
		Vec3 feet = s.feet(0.0F);
		snap(s, 0, 10.0F, 8, 6);
		if (s.own) {
			s.at(0, () -> {
				if (CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
					CinematicDirector.shoot(s, Shots.lowHero(4.0F + 2.0F * scale, 0.4F, 25.0F, 1.5F, 1.5F).asDanger(), CinematicDirector.KIND_DHARMA, s.priority, false);
				}
			});
		}
		s.add(new RingRenderer(0, 30, feet, 0.5F, 2.0F * scale, 0.8F, 10.0F, GOLD, 0.7F));
		s.add(new PillarRenderer(0, 12, feet, 12.0F * scale, GOLD, 0.8F * scale).rise(4));
		if (tier() >= 2) {
			emit(s, 2, 60, (tick, self) -> {
				if (tick % 2 != 0) {
					return;
				}
				Vec3 at = s.feet(0.0F);
				double height = 1.8D * scale;
				Vec3 side = SceneCatalog.forward(s.yaw() + 90.0F);
				for (int head = -1; head <= 1; head += 2) {
					Vec3 h = at.add(side.scale(head * 0.6D * scale)).add(0.0D, height * 0.92D, 0.0D);
					EpicParticles.burst(h, 2, 0.01F, EpicParticles.dust(GOLD, 1.0F), true);
				}
				for (int arm = 0; arm < 4; arm++) {
					double sign = arm < 2 ? -1.0D : 1.0D;
					Vec3 a = at.add(side.scale(sign * (0.5D + 0.2D * arm) * scale)).add(0.0D, height * (0.55D + 0.1D * (arm % 2)), 0.0D);
					EpicParticles.add(EpicParticles.dust(GOLD, 1.0F), a.x, a.y, a.z, 0.0D, 0.0D, 0.0D);
				}
			});
		}
		if (realm >= Realm.TRUE_IMMORTAL.ordinal()) {
			sky(s, 0, SkyDirector.Grade.SK_GOLD, 0.2F, 0.0F, 20);
			skyRelease(s, 20, 40, 40);
			s.add(new RingRenderer(0, 30, feet, 2.0F, 12.0F, 2.0F, 12.0F, 0xB89A70, 0.5F).crown(n(s, 30, 6)));
		}
		SceneCatalog.sound(s, 0, feet, SoundEvents.BEACON_ACTIVATE, 0.8F, 0.6F);
		SceneCatalog.sound(s, 0, feet, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.6F, 1.0F);
		stamp(s, 0, t("dharma"), "法", SEAL_RED);
		s.end(80);
	}

	// ================================================================ D2 Wilayah Dao

	static void domain(Scene s, int dao, VisualState visual) {
		Vec3 feet = s.feet(0.0F);
		Dao d = dao >= 0 && dao < Dao.values().length ? Dao.values()[dao] : null;
		int color = d == null ? PALE_GOLD : d.color();
		float radius = Math.min(24.0F, 10.0F + 2.0F * Math.max(0, visual.realm() - Realm.DAO_UNION.ordinal()) * 2.0F);
		shotPenuh(s, 0, () -> Shots.pullback(3.0F, 10.0F, 2.0F, 6.0F, 1.5F), CinematicDirector.KIND_DHARMA);
		s.add(new RingRenderer(0, 26, feet, 0.5F, radius, 2.0F, 20.0F, color, 0.6F));
		s.add(new RingRenderer(10, 80, feet, radius, radius, 0.0F, 1.0F, color, 0.55F).rune(0.6F));
		Minecraft minecraft = Minecraft.getInstance();
		boolean inside = minecraft.player != null && minecraft.player.position().distanceTo(feet) <= radius;
		s.at(0, () -> SkyDirector.dao(color, inside ? 0.35F : 0.1F, 20, s.priority, s.pos, s.own, 1.0F));
		skyRelease(s, 20, 60, 40);
		SceneCatalog.sound(s, 0, feet, SoundEvents.BEACON_ACTIVATE, 0.7F, 1.0F);
		SoundEvent cue = d == null ? SoundEvents.AMETHYST_BLOCK_CHIME : switch (d) {
			case SWORD -> SoundEvents.BELL_BLOCK;
			case FIRE -> SoundEvents.BLAZE_SHOOT;
			case ICE -> SoundEvents.GLASS_BREAK;
			case LIGHTNING -> SoundEvents.TRIDENT_THUNDER.value();
			case BODY -> SoundEvents.MACE_SMASH_GROUND;
			case LIFE -> SoundEvents.AMETHYST_BLOCK_CHIME;
			case SHADOW -> SoundEvents.WARDEN_SNIFF;
			case SPACE -> SoundEvents.PORTAL_TRIGGER;
		};
		float volume = d == Dao.ICE || d == Dao.SPACE ? 0.3F : d == Dao.LIGHTNING || d == Dao.SHADOW ? 0.4F : 0.8F;
		SceneCatalog.sound(s, 0, feet, cue, volume, 1.0F);
		if (d != null) {
			emit(s, 10, 120, (tick, self) -> {
				if (tick % 4 != 0) {
					return;
				}
				Vec3 c = s.feet(0.0F);
				switch (d) {
					case FIRE -> EpicParticles.rain(c, radius, 8.0F, n(s, 4, 1), 0xFF6A2A);
					case ICE -> EpicParticles.rain(c, radius, 10.0F, n(s, 4, 1), 0xDFF6FF);
					case LIGHTNING -> {
						if (tick % 40 == 0) {
							Vec3 g = SceneCatalog.around(s, c, 2.0F, radius);
							s.add(new BoltRenderer(0, 6, g.add(0.0D, 12.0D, 0.0D), g, null, 0.12F, d.color(), 0xFFFFFF, s.seed + tick, 0, true));
						}
					}
					case LIFE -> EpicParticles.rain(c, radius, 6.0F, n(s, 3, 1), 0xFFB7D5);
					case SPACE -> EpicParticles.converge(c.add(0.0D, 2.0D, 0.0D), radius, n(s, 3, 1), ParticleTypes.REVERSE_PORTAL, 0.0F, true);
					case SHADOW -> EpicParticles.converge(c.add(0.0D, 2.0D, 0.0D), radius, n(s, 3, 1), ParticleTypes.SMOKE, 0.1F, true);
					default -> EpicParticles.ring(c, radius, n(s, 6, 2), EpicParticles.dust(d.color(), 1.0F), 0.0F);
				}
			});
			if (d == Dao.ICE && inside) {
				SceneCatalog.edge(s, 0, 0xCFEFFF, 0.35F, 60);
			}
		}
		Component daoName = d == null ? t("dao_none") : d.displayName();
		stamp(s, 0, t("domain", daoName), d == null ? "道" : SceneCatalog.DAO_GLYPH[d.ordinal()], SEAL_RED);
		s.end(140);
	}

	// ================================================================ D3 / D4 ultimates

	/** A TECH_ULTIMATE test cue ({@code a} = the spec's net id). */
	static void ultimateTest(Scene s, int beat, int delay) {
		TechniqueSpec spec = TechniqueRegistry.byNet(s.a);
		if (spec == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 target = minecraft.player == null ? s.pos.add(0.0D, 0.0D, 8.0D) : s.pos.add(SceneCatalog.forward(minecraft.player.getYRot()).scale(8.0D));
		s.at(delay, () -> ultimate(s, spec, beat, target, true));
	}

	static void ultimate(Scene s, TechniqueSpec spec, int beat, Vec3 target, boolean test) {
		ultimate(s, spec, beat, target, test, 0);
	}

	/**
	 * One ultimate beat; {@code duration} is the event's (a lasting ultimate's field or channel ticks). Polish S12: the
	 * moment ends {@code max(duration, 60) + 40} ticks after the cast unless END comes first, a climax never shortens a
	 * lasting one, the climax plays at least 8 ticks after the cast beat with its subject-side layers first, and slow
	 * motion or a pull-back plays once per cast (never with "Kurangi gerak").
	 */
	static void ultimate(Scene s, TechniqueSpec spec, int beat, Vec3 target, boolean test, int duration) {
		boolean dewa = spec.dewa() || spec.grade() == TechniqueGrade.DEWA;
		String id = spec.id();
		String seal = sealOf(id, dewa);
		Vec3 caster = s.feet(0.0F);
		Ult u = s.data instanceof Ult x ? x : null;
		if (u == null) {
			u = new Ult();
			u.target = target;
			s.data = u;
		}
		long now = EpicDirector.ticks();
		if (beat == EpicIds.TECH_CAST) {
			u.target = target;
			u.castTick = now;
			u.duration = Math.max(0, duration);
			u.slowmoDone = false;
			boolean first = dewa ? !EpicSettings.seenThisSession(id) : !EpicSettings.seenEver(id);
			if (first) {
				full(s, 0, upper(spec.displayName()), null, seal, SEAL_RED, 60, card -> card.safeBeat(false));
				if (s.own && CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
					Letterbox.flick(20);
				}
			} else {
				stamp(s, 0, spec.displayName(), seal, SEAL_RED);
			}
			skyFor(s, id, dewa ? 0.8F : 0.5F);
			if (s.own) {
				if (dewa) {
					if (CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
						Letterbox.flick(20);
					}
				} else {
					s.at(0, () -> {
						if (CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
							CinematicDirector.shoot(s, Shots.ots(target.add(0.0D, 1.0D, 0.0D), 1.5F).asDanger(), CinematicDirector.KIND_ULTIMATE, s.priority, false);
						}
					});
				}
			}
			castLayers(s, id, caster, target);
			// Most ultimates send no END cue (the spec has no END layers): end the moment on our own when nothing follows.
			ultFallback(s, spec, u, Math.max(u.duration, 60) + 40);
		} else if (beat == EpicIds.TECH_CLIMAX) {
			Vec3 aim = u.target;
			// the climax waits at least 8 ticks after the cast beat (the build-up reads first)
			int wait = u.castTick < 0L ? 0 : (int) Math.max(0L, 8L - (now - u.castTick));
			boolean motion = !u.slowmoDone && !dev.kultivator.client.EffectQuality.reduceMotion();
			if (motion) {
				u.slowmoDone = true;
				if (dewa) {
					slowmo(s, wait, 20);
					hitStop(s, wait);
				} else {
					slowmo(s, wait, 12);
				}
			}
			final Vec3 climaxAim = aim;
			s.at(wait, () -> climaxLayers(s, id, caster, climaxAim));
			if (dewa && motion) {
				shotPenuh(s, 40 + wait, () -> Shots.pullback(4.0F, 14.0F, 2.0F, 8.0F, 2.0F), CinematicDirector.KIND_ULTIMATE);
			}
			// a lasting ultimate keeps its moment until its own end; others close 60 ticks after the climax
			long left = u.castTick < 0L ? 0L : u.castTick + Math.max(u.duration, 60) + 40L - now;
			ultFallback(s, spec, u, (int) Math.max(60L + wait, left));
		} else if (beat == EpicIds.TECH_END) {
			ultEnd(s, spec, u);
		}
	}

	/** Shared state of one ultimate scene: the aim, the fallback token and whether the end beat already ran. */
	private static final class Ult {
		Vec3 target;
		int token;
		boolean ended;
		long castTick = -1L;
		int duration;
		boolean slowmoDone;
	}

	/** Runs the end beat {@code delay} ticks from now unless a later cast or climax beat postpones it. */
	private static void ultFallback(Scene s, TechniqueSpec spec, Ult u, int delay) {
		int token = ++u.token;
		s.at(delay, () -> {
			if (u.token == token) {
				ultEnd(s, spec, u);
			}
		});
	}

	/** The end beat (TECH_END, or the fallback): releases the sky, the closing shots, ends the scene. Runs once. */
	private static void ultEnd(Scene s, TechniqueSpec spec, Ult u) {
		if (u.ended) {
			return;
		}
		u.ended = true;
		boolean dewa = spec.dewa() || spec.grade() == TechniqueGrade.DEWA;
		skyRelease(s, 0, 20, 60);
		if (dewa && s.own && noEnemy(16.0D)) {
			shot(s, 4, () -> Shots.craneUp(3.0F, 2.0F, 8.0F, 10.0F, 0.0F, 30.0F, 2.5F), CinematicDirector.KIND_ULTIMATE, true);
		}
		if (spec.id().equals("cosmic_giant_form") && s.own && noEnemy(16.0D)) {
			shot(s, 4, () -> Shots.pullback(4.0F, 24.0F, 2.0F, 14.0F, 4.0F), CinematicDirector.KIND_ULTIMATE, true);
		}
		s.end(80);
	}

	private static boolean noEnemy(double radius) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		return player.level().getEntities(player, new AABB(player.blockPosition()).inflate(radius), e -> e instanceof Enemy && e.isAlive()).isEmpty();
	}

	private static String sealOf(String id, boolean dewa) {
		return switch (id) {
			case "zhuxian_sword_formation" -> "誅";
			case "nine_suns_heavens_fury" -> "陽";
			case "ice_phoenix_descent" -> "凤";
			case "tribulation_thunder_emperor_array", "nine_heavens_thunder_venerable" -> "雷";
			case "cosmic_giant_form" -> "巨";
			case "guanyin_sweet_dew" -> "露";
			case "life_death_domain" -> "生";
			case "stygian_tiger_seal" -> "虎";
			case "void_mirror" -> "镜";
			case "ninth_hex" -> "封";
			case "five_elements_mountain" -> "佛";
			case "tai_chi_diagram" -> "太";
			case "heaven_punishing_sword" -> "罰";
			case "emperor_flame" -> "帝";
			case "freeze_mount_qi" -> "冰";
			case "ancient_god_trample" -> "古";
			case "bodhisattva_tree" -> "菩";
			case "kunpeng_art" -> "鲲";
			case "karma_domain" -> "因";
			case "time_spirit_domain" -> "时";
			case "stellar_black_hole" -> "星";
			case "heaven_trampling" -> "踏";
			default -> dewa ? "神" : "仙";
		};
	}

	private static void skyFor(Scene s, String id, float w) {
		switch (id) {
			case "zhuxian_sword_formation", "stygian_tiger_seal" -> sky(s, 0, SkyDirector.Grade.SK_RED, 0.4F, 0.3F, 20);
			case "nine_suns_heavens_fury", "five_elements_mountain" -> sky(s, 0, SkyDirector.Grade.SK_GOLD, 0.4F, 0.0F, 20);
			case "ice_phoenix_descent" -> sky(s, 0, SkyDirector.Grade.SK_ICE, 0.4F, 0.0F, 20);
			case "tribulation_thunder_emperor_array" -> sky(s, 0, SkyDirector.Grade.SK_TRIB, 0.7F, 0.5F, 20);
			case "guanyin_sweet_dew" -> sky(s, 0, SkyDirector.Grade.SK_JADE, 0.3F, 0.0F, 20);
			case "life_death_domain" -> sky(s, 0, SkyDirector.Grade.SK_ASH, 0.3F, 0.2F, 20);
			case "void_mirror" -> sky(s, 0, SkyDirector.Grade.SK_STARS, 0.4F, 0.3F, 20);
			case "ninth_hex" -> sky(s, 0, SkyDirector.Grade.SK_ASH, 0.5F, 0.3F, 20);
			case "tai_chi_diagram" -> sky(s, 0, SkyDirector.Grade.SK_ASH, 0.2F, 0.1F, 20);
			case "heaven_punishing_sword", "heaven_trampling" -> sky(s, 0, SkyDirector.Grade.SK_WHITE, w, 0.0F, 20);
			// a darkening golden-violet sky over the rising lotus bud (blended in over the bud's 30 tick rise)
			case "emperor_flame" -> s.at(0, () -> SkyDirector.dao(0xA884D8, 0.5F, 30, s.priority, s.pos, s.own, 1.0F));
			case "freeze_mount_qi" -> sky(s, 0, SkyDirector.Grade.SK_ICE, 0.4F, 0.0F, 20);
			case "nine_heavens_thunder_venerable" -> sky(s, 0, SkyDirector.Grade.SK_TRIB, 0.9F, 0.6F, 20);
			case "ancient_god_trample", "stellar_black_hole" -> sky(s, 0, SkyDirector.Grade.SK_STARS, w, 0.5F, 20);
			case "bodhisattva_tree" -> sky(s, 0, SkyDirector.Grade.SK_JADE, 0.3F, 0.0F, 20);
			case "kunpeng_art" -> sky(s, 0, SkyDirector.Grade.SK_ICE, 0.4F, 0.0F, 20);
			case "karma_domain" -> s.at(0, () -> SkyDirector.dao(0xE06030, 0.35F, 20, s.priority, s.pos, s.own, 1.0F));
			case "time_spirit_domain" -> sky(s, 0, SkyDirector.Grade.SK_ASH, 0.6F, 0.3F, 20);
			default -> {
			}
		}
	}

	/** The 7b additions that start with the cast (charge). */
	private static void castLayers(Scene s, String id, Vec3 caster, Vec3 target) {
		int i = idx(Math.max(2, s.s));
		switch (id) {
			case "nine_suns_heavens_fury", "emperor_flame" -> haze(s, caster, 120);
			case "ice_phoenix_descent" -> {
				SceneCatalog.edge(s, 0, 0xBFE8FF, 0.4F, 60);
				emit(s, 0, 80, (tick, self) -> EpicParticles.column(s.feet(0.0F), 3.0F, 2.0F, n(s, 4, 1), ParticleTypes.SNOWFLAKE, 0.02F));
			}
			case "tribulation_thunder_emperor_array" -> {
				VortexRenderer vortex = new VortexRenderer(0, 200, target.x, target.y + 40.0D, target.z, R_V[i] * 0.25F, 6.0F, EpicColors.TRIB_CLOUD,
						EpicColors.BOLT_OVERWORLD, s.seed);
				vortex.grow(0.3F, 1.0F, 40.0F).spin(4.0F);
				s.add("vortex", vortex);
			}
			case "cosmic_giant_form" -> {
				// polish S21 / finding 169: the giant is the cast's own AVATAR (a posed world-level GiantAvatar that walks to each
				// stomp and slams); no second epic colossus behind it
			}
			case "guanyin_sweet_dew" -> s.add(new RaysRenderer(0, 120, caster.add(0.0D, 2.4D, 0.0D), 10.0F, 4, 30.0F, 1.5F, 0xCFFFE8, 0.2F, false));
			case "life_death_domain" -> emit(s, 0, 120, (tick, self) -> {
				if (tick % 3 == 0) {
					EpicParticles.ring(s.feet(0.0F), 6.0F, n(s, 8, 2), ParticleTypes.SOUL, 0.0F);
				}
			});
			case "stygian_tiger_seal" -> s.add(new TrailRenderer(0, 80, caster.add(0.0D, 1.5D, 0.0D), target.add(0.0D, 1.0D, 0.0D), 3.0F, 0.12F, 0x8AFFC0, 0.8F,
					false, s.seed).travel(40));
			case "void_mirror" -> {
			}
			case "five_elements_mountain" -> {
				VortexRenderer vortex = new VortexRenderer(0, 160, target.x, target.y + 48.0D, target.z, R_V[i] * 0.2F, 8.0F, 0xC8B070, GOLD, s.seed);
				vortex.grow(0.3F, 1.0F, 30.0F).spin(3.0F).arcs(false);
				s.add("vortex", vortex);
			}
			case "nine_heavens_thunder_venerable" -> {
				VortexRenderer vortex = new VortexRenderer(0, 400, caster.x, caster.y + 48.0D, caster.z, R_V[i] * 0.3F, 8.0F, EpicColors.TRIB_CLOUD,
						EpicColors.BOLT_OVERWORLD, s.seed);
				vortex.grow(0.3F, 1.0F, 60.0F).spin(4.0F);
				s.add("vortex", vortex);
				int silhouettes = Math.min(EpicBudget.silhouetteCap(), 6);
				emit(s, 20, 360, (tick, self) -> {
					if (tick % 3 != 0 || lite(s)) {
						return;
					}
					for (int k = 0; k < silhouettes; k++) {
						double angle = Math.PI * 2.0D * k / silhouettes + tick * 0.003D;
						Vec3 base = caster.add(Math.cos(angle) * 30.0D, 34.0D, Math.sin(angle) * 30.0D);
						EpicParticles.outline(PointClouds.deity(), base, new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)), 12.0D, 3,
								EpicParticles.dust(0xD8D0FF, 2.5F), true);
					}
				});
			}
			case "kunpeng_art" -> snap(s, 0, 12.0F, 6, 20);
			case "karma_domain" -> {
				for (int k = 0; k < 6; k++) {
					Vec3 end = SceneCatalog.around(s, caster, 4.0F, 10.0F).add(0.0D, 1.0D, 0.0D);
					s.add(new TrailRenderer(k * 2, 100, caster.add(0.0D, 1.2D, 0.0D), end, 1.0F, 0.08F, 0xE0602A, 0.85F, false, s.seed + k).travel(10));
				}
			}
			case "heaven_trampling" -> {
				for (int k = 0; k < 9; k++) {
					s.add(new RingRenderer(k * 3, 120, caster.add(0.0D, 6.0D * (k + 1), 0.0D), 2.0F, 3.0F, 0.8F, 6.0F, WHITE_GOLD, 0.6F));
				}
				shotPenuh(s, 0, () -> Shots.lowHero(4.0F, 0.3F, 30.0F, 0.0F, 1.5F).asDanger(), CinematicDirector.KIND_ULTIMATE);
			}
			case "bodhisattva_tree" -> s.add(new RaysRenderer(0, 200, caster.add(0.0D, 34.0D, 0.0D), 32.0F, 6, 18.0F, 3.0F, 0xE8FFD8, 0.18F, false));
			default -> {
			}
		}
	}

	/** The 7b additions on the decisive beat. */
	private static void climaxLayers(Scene s, String id, Vec3 caster, Vec3 target) {
		switch (id) {
			case "zhuxian_sword_formation" -> {
				double r = 12.0D;
				Vec3[] corners = {target.add(-r, 0.0D, -r), target.add(r, 0.0D, -r), target.add(r, 0.0D, r), target.add(-r, 0.0D, r)};
				for (int k = 0; k < 4; k++) {
					Vec3 a = corners[k];
					Vec3 b = corners[(k + 1) % 4];
					s.add(new TrailRenderer(0, 60, a.add(0.0D, 24.0D, 0.0D), b.add(0.0D, 24.0D, 0.0D), 0.0F, 0.25F, 0xFF3030, 0.9F, true, s.seed + k));
					s.add(new TrailRenderer(0, 60, a, a.add(0.0D, 24.0D, 0.0D), 0.0F, 0.25F, 0xFF3030, 0.9F, true, s.seed + 10 + k));
				}
			}
			case "tribulation_thunder_emperor_array", "nine_heavens_thunder_venerable" -> {
				VortexRenderer vortex = SceneCatalog.vortex(s);
				Vec3 from = vortex != null ? new Vec3(vortex.centerX(), vortex.baseY(), vortex.centerZ()) : target.add(0.0D, 40.0D, 0.0D);
				for (int k = 0; k < 3; k++) {
					Vec3 ground = k == 0 ? target : SceneCatalog.around(s, target, 2.0F, 8.0F);
					s.add(new BoltRenderer(k * 6, 9, from, ground, null, 0.45F, EpicColors.BOLT_OVERWORLD, EpicColors.CORE_WHITE, s.seed + k, 1, k > 0));
				}
				SceneCatalog.flash(s, 0, EpicColors.BOLT_OVERWORLD, 0.15F, 3, 2);
			}
			case "nine_suns_heavens_fury" -> SceneCatalog.flash(s, 0, 0xFFF0C0, 0.2F, 4, 0);
			case "void_mirror" -> {
			}
			case "ninth_hex" -> slowmo(s, 0, 20);
			case "tai_chi_diagram" -> {
				s.add(new TrailRenderer(0, 80, caster.add(0.0D, 1.0D, 0.0D), target.add(0.0D, 1.0D, 0.0D), 2.0F, 0.2F, 0xF0F0F0, 0.9F, true, s.seed));
				s.add(new TrailRenderer(0, 80, caster.add(0.0D, 0.6D, 0.0D), target.add(0.0D, 0.6D, 0.0D), 1.6F, 0.2F, 0x101010, 0.9F, true, s.seed + 1));
				emit(s, 0, 60, (tick, self) -> EpicParticles.column(target, 4.0F, 3.0F, n(s, 3, 1), ParticleTypes.WHITE_ASH, 0.02F));
			}
			case "heaven_punishing_sword" -> {
				Vec3 dir = target.subtract(caster);
				dir = dir.lengthSqr() < 0.01D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(dir.x, 0.0D, dir.z).normalize();
				Vec3 mid = caster.add(dir.scale(40.0D)).add(0.0D, 30.0D, 0.0D);
				s.add(new TrailRenderer(0, 60, mid.add(dir.scale(-88.0D)), mid.add(dir.scale(88.0D)), 0.0F, 1.2F, 0xFFF8E0, 0.9F, true, s.seed));
			}
			case "emperor_flame" -> s.add(new RingRenderer(0, 60, target, 10.0F, 40.0F, 4.0F, 16.0F, 0xB070FF, 0.6F));
			case "freeze_mount_qi" -> {
				SceneCatalog.edge(s, 0, 0xE0F4FF, 0.45F, 60);
				emit(s, 0, 100, (tick, self) -> EpicParticles.rain(target, 40.0F, 20.0F, n(s, 8, 2), 0xFFFFFF));
			}
			case "ancient_god_trample" -> {
				Vec3 dir = target.subtract(caster);
				dir = dir.lengthSqr() < 0.01D ? SceneCatalog.forward(s.yaw()) : new Vec3(dir.x, 0.0D, dir.z).normalize();
				for (int k = 0; k < 9; k++) {
					Vec3 step = caster.add(dir.scale(2.0D + k * 2.0D));
					s.add(new RingRenderer(k * 4, 40, step, 0.4F, 1.2F, 0.6F, 4.0F, 0xFFD58A, 0.8F));
				}
				s.add(new TrailRenderer(36, 50, caster.add(0.0D, 20.0D, 0.0D), target.add(0.0D, 30.0D, 0.0D), 0.0F, 0.8F, 0xB08A30, 0.8F, true, s.seed));
			}
			case "bodhisattva_tree" -> SceneCatalog.flash(s, 0, 0xE8FFD8, 0.2F, 4, 0);
			case "kunpeng_art" -> s.add(new TrailRenderer(0, 60, caster.add(0.0D, 1.0D, 0.0D), target.add(0.0D, 1.0D, 0.0D), 2.0F, 0.8F, 0xE8F4FF, 0.6F, false,
					s.seed).travel(8));
			case "time_spirit_domain" -> {
				SceneCatalog.flash(s, 0, 0xCFE0FF, 0.25F, 4, 0);
				SceneCatalog.ui(s, 0, SoundEvents.WARDEN_SONIC_BOOM, 0.7F, 0.6F);
			}
			case "stellar_black_hole" -> {
				VortexRenderer vortex = new VortexRenderer(0, 120, target.x, target.y + 2.0D, target.z, 40.0F, 3.0F, 0x0A0612, 0x6A3AFF, s.seed);
				vortex.grow(0.6F, 1.0F, 20.0F).spin(-12.0F).arcs(false);
				s.add(vortex);
			}
			case "heaven_trampling" -> s.add(new TrailRenderer(0, 60, caster.add(-40.0D, 60.0D, 0.0D), caster.add(40.0D, 64.0D, 0.0D), 0.0F, 0.9F, WHITE_GOLD,
					0.85F, true, s.seed));
			default -> {
			}
		}
	}

	/** Rising white ash and faint embers (no FOV wobble), Normal+, ≤ 6 s. */
	private static void haze(Scene s, Vec3 at, int ticks) {
		if (tier() < 1) {
			return;
		}
		emit(s, 0, Math.min(120, ticks), (tick, self) -> {
			if (tick % 2 == 0) {
				EpicParticles.column(s.feet(0.0F), 3.0F, 4.0F, n(s, 3, 1), ParticleTypes.WHITE_ASH, 0.05F);
				EpicParticles.column(s.feet(0.0F), 2.0F, 3.0F, n(s, 1, 0), ParticleTypes.SMALL_FLAME, 0.02F);
			}
		});
	}

	// ================================================================ D5 Mode Kehancuran

	/** (a) The player's own carving cast (KEHANCURAN_SELF from {@code TechFx} with {@code FLAG_KEHANCURAN}). */
	static void kehancuranSelf(Scene s, TechniqueSpec spec) {
		Vec3 c = s.pos;
		float radius = Math.max(1.0F, s.f);
		EpicParticles.dustCrown(c, Math.max(2.0F, radius), n(s, tier() == 0 ? 8 : tier() == 1 ? 24 : 40, 4));
		s.add(new RingRenderer(0, 30, c, radius * 0.5F, radius * 1.5F, 1.5F, 12.0F, 0x8A7A60, 0.5F));
		if (tier() >= 2 && !lite(s) && !s.has(EpicIds.FLAG_LOW_LOAD)) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level != null) {
				var ground = minecraft.level.getBlockState(net.minecraft.core.BlockPos.containing(c.x, c.y - 0.5D, c.z));
				if (!ground.isAir()) {
					for (int k = 0; k < 6; k++) {
						double angle = Math.PI * 2.0D * k / 6.0D + s.random().nextDouble();
						double speed = 0.4D + s.random().nextDouble() * 0.5D;
						EpicDisplays.chunk(c.add(0.0D, 0.5D, 0.0D), ground, 0.6F + 0.1F * (k % 4), new Vec3(Math.cos(angle) * speed, 0.7D, Math.sin(angle) * speed),
								0.08D, 22);
					}
				}
			}
		}
		if (spec != null && spec.dewa() && spec.pamungkas()) {
			sky(s, 0, SkyDirector.Grade.SK_RED, 0.5F, 0.4F, 20);
			skyRelease(s, 20, 60, 40);
		}
		SceneCatalog.sound(s, 0, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.8F, 0.6F);
		s.end(60);
	}

	/** (b) A disciple's Jurus Pemusnah, and the CRATER of the player's own carving job. */
	static void kehancuran(Scene s, int beat, int delay) {
		Vec3 zone = s.pos;
		int art = s.a;
		float radius = Math.max(4.0F, s.f);
		switch (beat) {
			case EpicIds.KEH_WARNING -> {
				shot(s, delay, () -> Shots.pullback(3.0F, 14.0F, 2.0F, 9.0F, 3.0F).lookAt(zone), CinematicDirector.KIND_KEHANCURAN, true);
				glyph(s, delay, zone.add(0.0D, radius + 12.0D, 0.0D), "毁", 0xFF2A1A, 12.0F, 30 * 20);
				for (int sec = 10; sec >= 1; sec--) {
					int left = sec;
					glyph(s, delay + (30 - sec) * 20, zone.add(0.0D, radius + 4.0D, 0.0D), String.valueOf(left), 0xFF5040, 6.0F, 19);
				}
				sky(s, delay, SkyDirector.Grade.SK_RED, 0.15F, 0.2F, 20);
				sky(s, delay + 200, SkyDirector.Grade.SK_RED, 0.45F, 0.4F, 200);
				sky(s, delay + 400, SkyDirector.Grade.SK_RED, 0.7F, 0.6F, 200);
				music(s, delay, MusicCue.Cue.M_DREAD, 30);
				if (s.own) {
					full(s, delay + 10, t("red_seal.title"), () -> t("red_seal.sub", SceneCatalog.nameOf(s, "disciple"), artName(art)), "毁", SEAL_RED, 70,
							card -> card.safeBeat(false));
				} else if (SceneCatalog.distanceToCamera(zone) <= 256.0D) {
					s.at(delay + 10, () -> TitleCard.show(TitleCard.banner(t("red_seal.banner", SceneCatalog.nameOf(s, "disciple")))));
				}
			}
			case EpicIds.KEH_IMPACT -> {
				shot(s, delay, () -> Shots.stillAt(zone, 24.0F, 16.0F, 0.0F, 4.0F).lookAt(zone), CinematicDirector.KIND_KEHANCURAN, true);
				pemusnah(s, art, zone, radius, delay);
				skyRelease(s, delay, 0, 160);
				if (s.own) {
					s.at(delay + 20, () -> MusicCue.release(MusicCue.Cue.M_DREAD));
				}
				s.end(delay + 200);
			}
			case EpicIds.KEH_ABORT -> {
				skyRelease(s, delay, 0, 40);
				if (s.own) {
					s.at(delay, () -> MusicCue.release(MusicCue.Cue.M_DREAD));
				}
				s.end(delay + 40);
			}
			case EpicIds.KEH_RESTORE -> {
				emit(s, delay, 60, (tick, self) -> EpicParticles.rain(zone, radius, 12.0F, n(s, 6, 2), 0x9AF0FF));
				stamp(s, delay, t("restored"), "復", 0x2E8963);
				s.end(delay + 80);
			}
			case EpicIds.KEH_CRATER -> {
				int blocks = s.b;
				Minecraft minecraft = Minecraft.getInstance();
				double distance = minecraft.player == null ? 99.0D : Math.sqrt(minecraft.player.position().distanceToSqr(zone.x, minecraft.player.getY(), zone.z));
				boolean penuh = CinematicDirector.mode() == EpicSettings.Camera.PENUH;
				if (distance <= 16.0D && (blocks >= 512 || penuh && blocks >= 128)) {
					float seconds = distance >= 10.0D ? 4.0F : 3.0F;
					shot(s, delay, () -> Shots.craneLook(1.0F, 2.0F, 2.0F, 20.0F, seconds).lookAt(zone), CinematicDirector.KIND_KEHANCURAN, true);
				}
				stamp(s, delay, t("destruction", blocks), "毁", SEAL_RED);
				SceneCatalog.banner(s, delay + 30, t("destruction_hint"));
				s.end(delay + 120);
			}
			default -> {
			}
		}
	}

	/**
	 * A 3D prop of the model module for a scene beat (polish S22: an object on the subject, not only a camera move), with
	 * one animation; the old glyph when the model is missing or the scene is light.
	 */
	private static void prop(Scene s, int delay, String model, Vec3 at, float scale, float roll, int life, dev.kultivator.client.anim.model.Anim anim,
			float a, int animTicks, String glyph, int rgb, float glyphSize) {
		if (!dev.kultivator.client.fx.spec.AnimBridge.modelKnown(model) || lite(s)) {
			Vec3 glyphAt = anim == dev.kultivator.client.anim.model.Anim.FALL ? at.add(0.0D, a, 0.0D) : at;
			glyph(s, delay, glyphAt, glyph, rgb, glyphSize, life);
			return;
		}
		s.at(delay, () -> dev.kultivator.client.fx.spec.AnimBridge.model(model, at, scale, 0.0F, 0.0F, roll, life,
				java.util.List.of(dev.kultivator.client.fx.spec.AnimBridge.step(anim, 0, animTicks, a, 0.0F, null)), -1, Vec3.ZERO, false, true,
				s.subjectId, dev.kultivator.technique.spec.dsl.FxLayer.Priority.CORE));
	}

	/** The art ids of {@code DestroyerArts.Art} (the ordinal travels in {@code a}; names read-only from its lang keys). */
	private static final String[] ARTS = {"xuantian", "lotus", "quake", "desolation", "prison_hand", "sumeru", "hell_monarch", "devouring", "mountain_sea"};

	static Component artName(int art) {
		return art >= 0 && art < ARTS.length ? Component.translatable("kultivator.dpath.art." + ARTS[art]) : t("art_unknown");
	}

	/** The nine arts of 20.3.21, by {@code DestroyerArts.Art} ordinal. */
	private static void pemusnah(Scene s, int art, Vec3 zone, float radius, int delay) {
		SceneCatalog.flash(s, delay, 0xFF6040, 0.2F, 4, 0);
		shake(s, delay, 0.8F, 20);
		switch (art) {
			case 0 -> {
				// Tebasan Xuantian: the black-gold sword (a 3D prop; the 剑 glyph without the model module) descends with a light line.
				prop(s, delay, "xuantian_sword", zone, 14.0F, 180.0F, 50, dev.kultivator.client.anim.model.Anim.FALL, 30.0F, 14, "剑", 0xE8F4FF, 24.0F);
				s.add(new TrailRenderer(delay + 10, 40, zone.add(0.0D, 48.0D, 0.0D), zone, 0.0F, 1.0F, 0xE8F4FF, 0.9F, true, s.seed));
			}
			case 1 -> emit(s, delay, 60, (tick, self) -> {
				// Teratai Api Amarah Buddha Agung: a three-colour 16-b lotus.
				int[] colors = {0xFF4A2A, 0xFFD54A, 0x40D0FF};
				EpicParticles.outline(PointClouds.lotus(), zone, new Vec3(1.0D, 0.0D, 0.0D), 16.0D, n(s, 8, 2),
						EpicParticles.dust(colors[tick % 3], 2.0F), true);
			});
			case 2 -> {
				// Getaran Bumi: three expanding rings.
				for (int k = 0; k < 3; k++) {
					s.add(new RingRenderer(delay + k * 10, 40, zone, 2.0F, radius * (1.0F + k * 0.4F), 3.0F, 30.0F, 0xB08A50, 0.6F).crown(n(s, 20, 4)));
				}
			}
			case 3 -> {
				// Kitab Kehancuran Agung: a green-to-grey wave.
				s.add(new RingRenderer(delay, 50, zone, 2.0F, radius * 1.4F, 4.0F, 40.0F, 0x6AC060, 0.6F));
				s.add(new RingRenderer(delay + 20, 50, zone, 2.0F, radius * 1.4F, 4.0F, 40.0F, 0x7A7A7A, 0.6F));
			}
			case 4 -> {
				// Tangan Penjara Langit: the bronze hand falls open from 20 b and closes on the zone (the 囚 cube without models).
				if (dev.kultivator.client.fx.spec.AnimBridge.modelKnown("prison_hand_open") && !lite(s)) {
					s.at(delay, () -> {
						var hand = dev.kultivator.client.fx.spec.AnimBridge.model("prison_hand_open", zone, 9.0F, 0.0F, 60,
								dev.kultivator.client.anim.model.Anim.FALL, 20.0F, 0.0F, null, s.subjectId);
						s.at(16, () -> dev.kultivator.client.fx.spec.AnimBridge.swap(hand, "prison_hand_closed"));
					});
				} else {
					for (int x = -1; x <= 1; x++) {
						for (int z = -1; z <= 1; z++) {
							glyph(s, delay + (x + 1) * 2 + (z + 1), zone.add(x * 4.0D, 40.0D - (x + z + 2) * 2.0D, z * 4.0D), "囚", 0x9A80FF, 4.0F, 40);
						}
					}
				}
			}
			case 5 -> {
				// Palu Agung Sumeru: the hammer grows over nine blows above the zone, each a ring and a shake (the 锤 glyph without models).
				prop(s, delay, "sumeru_hammer", zone.add(0.0D, 24.0D, 0.0D), 10.0F, 0.0F, 96, dev.kultivator.client.anim.model.Anim.GROW, 0.2F, 88, "锤", 0xC8A0FF,
						8.0F);
				for (int k = 0; k < 9; k++) {
					s.add(new RingRenderer(delay + k * 10, 20, zone, 1.0F, radius * 0.6F + k, 2.0F, 10.0F, 0xB57CFF, 0.6F));
					shake(s, delay + k * 10, 0.4F, 6);
				}
			}
			case 6 -> {
				// Murka Raja Neraka: a crimson vortex with the red sky at full (light floor 0.5).
				VortexRenderer vortex = new VortexRenderer(delay, 160, zone.x, zone.y + 40.0D, zone.z, 1024.0F * 0.15F, 8.0F, 0x5A0A0A, 0xFF3020, s.seed);
				vortex.grow(0.3F, 1.0F, 40.0F).spin(6.0F);
				s.add(vortex);
				sky(s, delay, SkyDirector.Grade.SK_RED, 1.0F, 0.6F, 20);
			}
			case 7 -> {
				// Pusaran Melahap: an inverted vortex into the ground.
				VortexRenderer vortex = new VortexRenderer(delay, 120, zone.x, zone.y + 0.5D, zone.z, radius * 2.0F, 2.0F, 0x0A0A12, 0x8A60FF, s.seed);
				vortex.grow(1.0F, 0.2F, 100.0F).spin(-10.0F).arcs(false);
				s.add(vortex);
			}
			case 8 -> {
				// Segel Gunung-Laut: nine 8-b seal glyphs stamping down in a 3×3 grid.
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						prop(s, delay + ((x + 1) * 3 + (z + 1)) * 3, "mountain_sea_seal", zone.add(x * 6.0D, 0.0D, z * 6.0D), 6.0F, 0.0F, 40,
								dev.kultivator.client.anim.model.Anim.FALL, 12.0F, 8, "封", 0xFFD54A, 8.0F);
					}
				}
			}
			default -> s.add(new RingRenderer(delay, 40, zone, 2.0F, radius, 3.0F, 20.0F, 0xFF6040, 0.6F));
		}
		SceneCatalog.sound(s, delay, zone, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.5F);
	}

	private SceneCatalogPower() {
	}
}
