package dev.kultivator.client.epic;

import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.epic.fx.PointClouds;
import dev.kultivator.client.epic.render.BoltRenderer;
import dev.kultivator.client.epic.render.GiantFigureRenderer;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import dev.kultivator.client.epic.render.PillarRenderer;
import dev.kultivator.client.epic.render.RaysRenderer;
import dev.kultivator.client.epic.render.RingRenderer;
import dev.kultivator.client.epic.render.TrailRenderer;
import dev.kultivator.client.epic.render.VortexRenderer;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.EpicSound;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Realm;
import dev.kultivator.epic.EpicColors;
import dev.kultivator.epic.EpicIds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static dev.kultivator.client.epic.SceneCatalog.GOLD;
import static dev.kultivator.client.epic.SceneCatalog.JADE;
import static dev.kultivator.client.epic.SceneCatalog.SEAL_RED;
import static dev.kultivator.client.epic.SceneCatalog.SOUL;
import static dev.kultivator.client.epic.SceneCatalog.WHITE_GOLD;
import static dev.kultivator.client.epic.SceneCatalog.banner;
import static dev.kultivator.client.epic.SceneCatalog.emit;
import static dev.kultivator.client.epic.SceneCatalog.flash;
import static dev.kultivator.client.epic.SceneCatalog.full;
import static dev.kultivator.client.epic.SceneCatalog.glyph;
import static dev.kultivator.client.epic.SceneCatalog.lite;
import static dev.kultivator.client.epic.SceneCatalog.music;
import static dev.kultivator.client.epic.SceneCatalog.n;
import static dev.kultivator.client.epic.SceneCatalog.nameOf;
import static dev.kultivator.client.epic.SceneCatalog.qualityName;
import static dev.kultivator.client.epic.SceneCatalog.realmName;
import static dev.kultivator.client.epic.SceneCatalog.shake;
import static dev.kultivator.client.epic.SceneCatalog.shot;
import static dev.kultivator.client.epic.SceneCatalog.shotPenuh;
import static dev.kultivator.client.epic.SceneCatalog.sky;
import static dev.kultivator.client.epic.SceneCatalog.skyRelease;
import static dev.kultivator.client.epic.SceneCatalog.slowmo;
import static dev.kultivator.client.epic.SceneCatalog.stamp;
import static dev.kultivator.client.epic.SceneCatalog.sting;
import static dev.kultivator.client.epic.SceneCatalog.t;
import static dev.kultivator.client.epic.SceneCatalog.tier;
import static dev.kultivator.client.epic.SceneCatalog.upper;

/**
 * Storyboards of DESIGN 23.13 group E: disciple breakthroughs (护法), the Ancient God calamities with Penginjak Langit
 * and the Soul Lamps, surpassing the master, recruitment day, the tournament final and the sect ceremonies. The master is
 * the protagonist; the disciple (or the place) is the subject.
 */
final class SceneCatalogSect {

	// ================================================================ E1 disciple breakthroughs

	static void discipleBreakthrough(Scene s, int beat, int delay) {
		int realm = EpicIds.nibble(s.a, 0);
		int quality = EpicIds.nibble(s.a, 1);
		Component name = nameOf(s, "disciple");
		switch (beat) {
			case EpicIds.DBT_START -> {
				if (realm >= Realm.GOLDEN_CORE.ordinal()) {
					// The omen at the disciple rule (0.35 R_v, 0.6 H): the tribulation storyboard with these sizes; only the
					// FINAL flag marks the last strike.
					s.a = EpicIds.packOmen(0, realm, 0);
					s.b = 99;
					SceneCatalog.tribulation(s, EpicIds.TRIB_OMEN, delay);
				}
				banner(s, delay, t("disciple_trib", name));
			}
			case EpicIds.DBT_STRIKE -> {
				SceneCatalog.Trib info = SceneCatalog.trib(s);
				info.disciple = true;
				if (info.total < 99) {
					info.total = 99;
				}
				s.a = ++info.strikes;
				SceneCatalog.tribulation(s, EpicIds.TRIB_STRIKE, delay);
				Minecraft minecraft = Minecraft.getInstance();
				Entity master = minecraft.level == null ? null : minecraft.level.getEntity(s.viewerId);
				if (master != null && master.position().distanceTo(s.pos) <= 16.0D && s.get("hufa") == null) {
					s.add("hufa", new TrailRenderer(delay, 20 * 60, master.position().add(0.0D, 1.2D, 0.0D), s.pos.add(0.0D, 1.2D, 0.0D), 0.5F, 0.12F, GOLD, 0.8F,
							false, s.seed).follow(master.getId(), 1.2F, s.subjectId, 1.2F));
				}
			}
			case EpicIds.DBT_END_OK -> {
				boolean foundation = realm == Realm.FOUNDATION.ordinal();
				int type = s.b;
				if (foundation) {
					// Foundation types: Fana 1, Bumi 2, Langit 3, Sempurna 4 (B1 pillars on the disciple).
					int q = type >= 4 ? BreakthroughQuality.HEAVENLY.ordinal() : type == 3 ? BreakthroughQuality.PERFECT.ordinal()
							: type == 2 ? BreakthroughQuality.NORMAL.ordinal() : BreakthroughQuality.CRACKED.ordinal();
					SceneCatalogPower.foundationPillars(s, q, delay, true);
					if (type >= 4) {
						music(s, delay, MusicCue.Cue.M_GLORY, 18);
					}
				} else {
					SceneCatalog.tribulation(s, EpicIds.TRIB_END_OK, delay);
					music(s, delay + 10, MusicCue.Cue.M_TRIUMPH, 14);
				}
				boolean milestone = s.has(EpicIds.FLAG_FIRST) || foundation && type >= 3;
				if (milestone) {
					full(s, delay + 20, t("disciple_realm.title", upper(name), upper(realmName(realm))), () -> t("disciple_realm.sub", qualityName(quality)),
							SceneCatalog.SEAL[SceneCatalog.idx(s.s)], SEAL_RED, 70, null);
					shot(s, delay + 10, () -> Shots.orbit(6.0F, 1.6F, 60.0F, 3.0F), CinematicDirector.KIND_DISCIPLE, true);
				} else {
					stamp(s, delay + 20, t("disciple_realm.stamp", name, realmName(realm)), "徒", SEAL_RED);
					if (realm >= Realm.GOLDEN_CORE.ordinal()) {
						shotPenuh(s, delay + 10, () -> Shots.orbit(6.0F, 1.6F, 60.0F, 3.0F), CinematicDirector.KIND_DISCIPLE);
					}
				}
				Element hufa = s.get("hufa");
				if (hufa != null) {
					s.at(delay + 20, hufa::stop);
				}
				s.end(delay + 240);
			}
			case EpicIds.DBT_END_FAIL -> {
				emit(s, delay, 40, (tick, self) -> EpicParticles.column(s.pos, 3.0F, 1.0F, n(s, 4, 1), ParticleTypes.LARGE_SMOKE, 0.05F));
				VortexRenderer vortex = SceneCatalog.vortex(s);
				if (vortex != null) {
					s.at(delay, () -> vortex.collapse(40));
				}
				stamp(s, delay, t("disciple_fail"), "徒", 0x6A5A50);
				s.end(delay + 80);
			}
			case EpicIds.DBT_CANCEL -> {
				VortexRenderer vortex = SceneCatalog.vortex(s);
				if (vortex != null) {
					s.at(delay, () -> vortex.dissolve(40));
				}
				skyRelease(s, delay, 0, 40);
				s.end(delay + 60);
			}
			case EpicIds.DBT_TAKE_LIGHTNING -> {
				// Tahan Petir: the strike bends from the sky over the midpoint to the master.
				Minecraft minecraft = Minecraft.getInstance();
				Entity master = minecraft.level == null ? null : minecraft.level.getEntity(s.viewerId);
				Vec3 to = master != null ? master.position() : s.pos.add(2.0D, 0.0D, 0.0D);
				Vec3 kink = s.pos.lerp(to, 0.5D).add(0.0D, 12.0D, 0.0D);
				Vec3 top = s.pos.add(0.0D, 40.0D, 0.0D);
				s.add(new BoltRenderer(delay, 9, top, to, kink, 0.3F, EpicColors.BOLT_OVERWORLD, EpicColors.CORE_WHITE, s.seed + 5, 1, false));
				flash(s, delay, EpicColors.BOLT_OVERWORLD, 0.12F, 3, 2);
				if (s.own) {
					s.at(delay, () -> {
						dev.kultivator.client.epic.camera.ShotGuard.strikeShown();
						if (CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
							CinematicDirector.shoot(s, Shots.ots(s.pos.add(0.0D, 1.0D, 0.0D), 1.2F).asDanger(), CinematicDirector.KIND_DISCIPLE, s.priority, false);
						}
					});
				}
				stamp(s, delay, t("take_lightning"), "护", SEAL_RED);
			}
			case EpicIds.DBT_REVEAL -> reveal(s, s.b, delay, name);
			default -> {
			}
		}
	}

	/** E4 (d): special reveals (b: 1 Pengemis di Gerbang, 2 Nadi Dewa Sesat, 3 Garis Darah Dou Di). */
	private static void reveal(Scene s, int kind, int delay, Component name) {
		Vec3 feet = s.pos;
		switch (kind) {
			case 1 -> {
				emit(s, delay, 80, (tick, self) -> {
					EpicParticles.rain(s.pos, 12.0F, 6.0F, n(s, 4, 1), 0xFFB7D5);
					if (tick % 2 == 0) {
						EpicParticles.add(ParticleTypes.CHERRY_LEAVES, s.pos.x + (EpicParticles.random().nextDouble() - 0.5D) * 24.0D, s.pos.y + 6.0D,
								s.pos.z + (EpicParticles.random().nextDouble() - 0.5D) * 24.0D, 0.0D, -0.05D, 0.0D);
					}
				});
				s.add(new TrailRenderer(delay + 20, 40, feet.add(-10.0D, 3.0D, 0.0D), feet.add(10.0D, 3.0D, 0.0D), 6.0F, 0.5F, 0xFF9AC8, 0.8F, false, s.seed)
						.travel(12));
				shot(s, delay, () -> Shots.orbit(5.0F, 1.6F, 100.0F, 4.0F), CinematicDirector.KIND_DISCIPLE, true);
				music(s, delay, MusicCue.Cue.M_GLORY, 18);
				full(s, delay + 20, t("beggar.title"), () -> t("beggar.sub"), "梅", SEAL_RED, 70, null);
			}
			case 2 -> {
				for (int g = 0; g < 7; g++) {
					int gate = g;
					s.at(delay + g * 6, () -> {
						EpicParticles.ring(s.pos.add(0.0D, 0.2D + gate * 0.28D, 0.0D), 0.6F, n(s, 14, 4), EpicParticles.dust(0xC21F1F, 1.0F), 0.0F);
						if (s.own) {
							EpicSound.ui(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.6F + gate * 0.1F, 0.7F);
						}
					});
				}
				full(s, delay + 44, t("heretic_veins.title"), null, "邪", SEAL_RED, 70, null);
			}
			case 3 -> {
				s.at(delay, () -> {
					EpicParticles.burst(s.pos.add(0.0D, 1.0D, 0.0D), n(s, 40, 10), 0.3F, EpicParticles.dust(0x9A40FF, 1.6F), false);
					EpicParticles.burst(s.pos.add(0.0D, 1.0D, 0.0D), n(s, 30, 8), 0.25F, EpicParticles.dust(GOLD, 1.4F), false);
				});
				full(s, delay + 10, t("dou_di.title"), null, "帝", SEAL_RED, 70, null);
			}
			default -> {
			}
		}
		s.end(delay + 160);
	}

	// ================================================================ E2 calamities, Penginjak Langit, Lampu Jiwa

	static void calamity(Scene s, int beat, int delay) {
		int k = Math.max(1, s.a);
		int index = s.b & 255;
		Component name = nameOf(s, "disciple");
		switch (beat) {
			case EpicIds.CAL_OMEN -> {
				float radius = SceneCatalog.R_V[SceneCatalog.idx(Math.max(2, s.s))] * 0.35F;
				float height = SceneCatalog.CLOUD_H[SceneCatalog.idx(Math.max(2, s.s))] * 0.6F;
				VortexRenderer vortex = new VortexRenderer(delay, 20 * 120, s.pos.x, s.pos.y + height, s.pos.z, Math.max(40.0F, radius), 5.0F, 0x4A5A80,
						EpicColors.BOLT_CALAMITY, s.seed);
				vortex.grow(0.2F, 0.7F, 120.0F).spin(3.0F);
				s.add("vortex", vortex);
				s.data = null;
				emit(s, delay, 120, (tick, self) -> {
					if (tick % 3 == 0) {
						EpicParticles.converge(s.pos.add(0.0D, height * 0.5D, 0.0D), 30.0F, n(s, 3, 1), ParticleTypes.END_ROD, 0.0F, true);
					}
				});
				s.add(new TrailRenderer(delay + 20, 30, s.pos.add(20.0D, height + 20.0D, 10.0D), s.pos.add(0.0D, height, 0.0D), 0.0F, 0.4F, 0xD8E8FF, 0.9F, false,
						s.seed).travel(20).tail(8));
				sky(s, delay, SkyDirector.Grade.SK_STARS, 0.8F, 0.5F, 40);
				shot(s, delay + 10, () -> Shots.craneLook(4.0F, 1.6F, 10.0F, 8.0F, 4.0F), CinematicDirector.KIND_DISCIPLE, true);
				music(s, delay, k >= 5 ? MusicCue.Cue.M_STORM : MusicCue.Cue.M_DREAD, 12);
				banner(s, delay, t("calamity", k, name));
			}
			case EpicIds.CAL_PRE -> {
				VortexRenderer vortex = SceneCatalog.vortex(s);
				if (vortex != null) {
					s.add(new BoltRenderer(delay, 30, new Vec3(vortex.centerX(), vortex.baseY(), vortex.centerZ()), s.pos, 0.05F, EpicColors.BOLT_CALAMITY));
				}
			}
			case EpicIds.CAL_STRIKE -> {
				VortexRenderer vortex = SceneCatalog.vortex(s);
				Vec3 top = vortex != null ? new Vec3(vortex.centerX(), vortex.baseY(), vortex.centerZ()) : s.pos.add(0.0D, 40.0D, 0.0D);
				s.add(new BoltRenderer(delay, 9, top, s.pos, null, 0.4F, EpicColors.BOLT_CALAMITY, EpicColors.CORE_GOLD, s.seed + index, SceneCatalog.branches(s.s),
						false));
				flash(s, delay, EpicColors.BOLT_CALAMITY, 0.1F, 3, 2);
				shake(s, delay, 0.3F, 8);
				if (s.own && index % 3 == 0) {
					s.at(delay + 2, () -> {
						if (CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
							CinematicDirector.shoot(s, Shots.lowHero(4.0F, 0.4F, 25.0F, 0.6F, 1.0F).asDanger(), CinematicDirector.KIND_DISCIPLE, s.priority, false);
						}
					});
				}
			}
			case EpicIds.CAL_PASS -> {
				VortexRenderer vortex = SceneCatalog.vortex(s);
				Vec3 eye = vortex != null ? new Vec3(vortex.centerX(), vortex.baseY(), vortex.centerZ()) : s.pos.add(0.0D, 30.0D, 0.0D);
				s.add(new TrailRenderer(delay, 20, eye, s.pos.add(0.0D, 1.3D, 0.0D), 0.0F, 0.5F, 0xE0E8FF, 0.95F, false, s.seed).travel(10).tail(5));
				if (vortex != null) {
					s.at(delay + 10, () -> vortex.dissolve(60));
				}
				shot(s, delay + 6, () -> Shots.orbit(6.0F, 2.0F, 60.0F, 3.0F), CinematicDirector.KIND_DISCIPLE, true);
				skyRelease(s, delay, 0, 80);
				music(s, delay + 6, MusicCue.Cue.M_TRIUMPH, 14);
				if (k == 2 || k == 4 || k == 7) {
					s.add(new RingRenderer(delay + 10, 60, s.pos, 4.0F, 32.0F, 3.0F, 30.0F, 0xE0E8FF, 0.6F));
					String trial = k == 2 ? "first" : k == 4 ? "second" : "third";
					full(s, delay + 20, t("trial_" + trial + ".title"), () -> t("trial.sub"), "星", SEAL_RED, 70, null);
				}
				s.end(delay + 200);
			}
			case EpicIds.CAL_FAIL -> {
				s.at(delay, () -> EpicParticles.burst(s.pos.add(0.0D, 4.0D, 0.0D), n(s, 30, 8), 0.2F, EpicParticles.dust(0xC0C8E0, 1.2F), false));
				SceneCatalog.edge(s, delay, 0xB01010, 0.4F, 40);
				music(s, delay, MusicCue.Cue.M_SORROW, 8);
				VortexRenderer vortex = SceneCatalog.vortex(s);
				if (vortex != null) {
					s.at(delay, () -> vortex.collapse(40));
				}
				skyRelease(s, delay, 0, 60);
				s.end(delay + 100);
			}
			case EpicIds.CAL_ABORT -> {
				VortexRenderer vortex = SceneCatalog.vortex(s);
				if (vortex != null) {
					s.at(delay, () -> vortex.dissolve(30));
				}
				skyRelease(s, delay, 0, 40);
				s.end(delay + 50);
			}
			case EpicIds.CAL_TRAMPLING -> trampling(s, delay, name);
			case EpicIds.CAL_LAMP -> {
				// a = k; the lamps start at k = 8 (SoulLamps.LAMP_BASE_K), so lamp n = k − 7.
				int lamp = Math.max(1, k - 7);
				emit(s, delay, 40, (tick, self) -> EpicParticles.helix(s.pos, 2.0F, 0.8F, n(s, 3, 1), tick * 0.3D, ParticleTypes.SOUL_FIRE_FLAME));
				if (lamp % 9 == 0) {
					full(s, delay + 10, t("lamp_full.title", lamp), null, "灯", SEAL_RED, 60, null);
				} else {
					stamp(s, delay + 10, t("lamp", lamp), "灯", SEAL_RED);
				}
				s.end(delay + 80);
			}
			default -> {
			}
		}
	}

	/** Penginjak Langit: a translucent clone climbs nine light steps and stamps on the ninth. */
	private static void trampling(Scene s, int delay, Component name) {
		Vec3 feet = s.pos;
		int start = delay + 120;
		for (int step = 0; step < 9; step++) {
			s.add(new RingRenderer(start + step * 10, 140 - step * 10, feet.add(0.0D, 6.0D * (step + 1), 0.0D), 2.0F, 3.0F, 0.8F, 6.0F, WHITE_GOLD, 0.7F));
		}
		if (!lite(s)) {
			Entity subject = s.subject();
			GiantFigureRenderer clone = new GiantFigureRenderer(start, 120, feet, s.yaw(), 3.0F,
					subject == null ? EpicRenderTypes.IMMORTAL_AVATAR : GiantFigureRenderer.skinOf(subject, EpicRenderTypes.IMMORTAL_AVATAR), 0xFFFFFF, 0.35F)
					.moveTo(feet.add(0.0D, 54.0D, 0.0D), 90);
			s.add(clone);
		}
		s.add(new TrailRenderer(start + 92, 60, feet.add(-48.0D, 60.0D, 0.0D), feet.add(48.0D, 62.0D, 0.0D), 0.0F, 0.9F, WHITE_GOLD, 0.9F, true, s.seed));
		flash(s, start + 92, WHITE_GOLD, 0.3F, 5, 0);
		shake(s, start + 92, 0.8F, 14);
		shot(s, start, () -> Shots.craneUp(4.0F, 2.0F, 6.0F, 24.0F, 0.0F, -45.0F, 5.0F), CinematicDirector.KIND_DISCIPLE, true);
		music(s, start, MusicCue.Cue.M_GLORY, 18);
		full(s, start + 100, t("trampling.title"), () -> t("trampling.sub", name), "踏", SEAL_RED, 70, null);
		s.end(start + 240);
	}

	// ================================================================ E3 surpassing the master

	static void surpass(Scene s, int delay) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity master = minecraft.level == null ? null : minecraft.level.getEntity(s.viewerId);
		Vec3 masterPos = master != null ? master.position() : s.pos.add(3.0D, 0.0D, 0.0D);
		if (s.a == 1) {
			slowmo(s, delay, 20);
		}
		int masterColor = Realm.byOrdinal(dev.kultivator.client.ClientCultivation.data().realm().ordinal()).color();
		s.add(new PillarRenderer(delay, 100, masterPos, 24.0F, masterColor, 1.0F).rise(20));
		s.add(new PillarRenderer(delay + 10, 100, s.pos, 48.0F, GOLD, 1.0F).rise(40));
		sky(s, delay, SkyDirector.Grade.SK_GOLD, 0.3F, 0.0F, 30);
		skyRelease(s, delay + 30, 40, 60);
		shot(s, delay, () -> Shots.twoShot(masterPos, 6.0F, 4.5F, 1.2F, 3.0F), CinematicDirector.KIND_DISCIPLE, true);
		shot(s, delay + 60, () -> Shots.craneLook(4.5F, 1.2F, 7.0F, 8.0F, 2.0F), CinematicDirector.KIND_DISCIPLE, true);
		emit(s, delay + 60, 40, (tick, self) -> EpicParticles.rain(s.pos, 6.0F, 6.0F, n(s, 3, 1), 0xFFB7D5));
		music(s, delay, MusicCue.Cue.M_TRIUMPH, 14);
		full(s, delay + 20, t("surpass.title"), () -> nameOf(s, "disciple"), "徒", SEAL_RED, 70, null);
		s.end(delay + 160);
	}

	// ================================================================ E4 recruitment day

	static void recruit(Scene s, int beat, int delay) {
		Vec3 stele = s.pos;
		switch (beat) {
			case EpicIds.RECRUIT_ARRIVAL -> {
				sky(s, delay, SkyDirector.Grade.SK_DAWN, 0.5F, 0.0F, 40);
				skyRelease(s, delay + 40, 320, 40);
				shot(s, delay + 10, () -> Shots.craneLook(10.0F, 10.0F, 4.0F, 6.0F, 4.0F).asCeremony(), CinematicDirector.KIND_SECT, true);
				emit(s, delay + 10, 80, (tick, self) -> {
					if (tick % 2 == 0) {
						EpicParticles.ring(stele, 8.0F + (tick % 12) * 0.5F, n(s, 10, 3), ParticleTypes.CLOUD, 0.02F);
					}
					if (tick % 3 == 0 && !lite(s)) {
						for (int c = 0; c < 6; c++) {
							double angle = Math.PI * 2.0D * c / 6.0D + tick * 0.02D;
							Vec3 base = stele.add(Math.cos(angle) * 14.0D, 10.0D + (c % 2) * 2.0D, Math.sin(angle) * 14.0D);
							EpicParticles.outline(PointClouds.crane(), base, new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)), 3.0D, 2,
									EpicParticles.dust(0xFFFFFF, 1.0F), true);
						}
					}
				});
				music(s, delay + 10, MusicCue.Cue.M_GENTLE, 12);
				if (s.b == 1) {
					full(s, delay + 20, t("war_day.title"), () -> t("war_day.sub"), "戰", SEAL_RED, 70, null);
				} else {
					full(s, delay + 20, t("recruit.title"), () -> t("recruit.sub", Math.max(1, s.a)), "徒", SEAL_RED, 70, null);
				}
				s.end(delay + 420);
			}
			case EpicIds.RECRUIT_TEST -> {
				int duan = Math.max(1, Math.min(9, EpicIds.nibble(s.a, 0)));
				int colour = Math.max(0, Math.min(6, EpicIds.nibble(s.a, 1)));
				int special = s.a >> 8 & 255;
				int rgb = SOUL[colour];
				for (int d = 0; d < duan; d++) {
					float base = d * 2.5F;
					s.add(new PillarRenderer(delay + d * 8, 120 - d * 8, stele.add(0.0D, base, 0.0D), 2.5F, rgb, 0.35F));
				}
				if (duan >= 7) {
					shot(s, delay + 10, () -> Shots.push(6.0F, 4.0F, 1.0F, 3.0F).lookAt(stele.add(0.0D, duan * 2.5D, 0.0D)), CinematicDirector.KIND_SECT, true);
				}
				if (duan >= 9 || special != 0) {
					s.at(delay + duan * 8, () -> SkyDirector.dao(rgb, 0.3F, 20, s.priority, s.pos, s.own, 1.0F));
					skyRelease(s, delay + duan * 8 + 20, 40, 20);
					stamp(s, delay + duan * 8, t("rare_talent", duan, t("soul_colour." + colour)), "才", SEAL_RED);
				}
				s.end(delay + 160);
			}
			case EpicIds.RECRUIT_ACCEPT -> {
				stamp(s, delay, t("new_disciple", nameOf(s, "disciple")), "徒", JADE);
				s.end(delay + 40);
			}
			default -> {
			}
		}
	}

	// ================================================================ E5 tournament final

	static void tournament(Scene s, int beat, int delay) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity first = s.subject();
		Entity second = minecraft.level == null ? null : minecraft.level.getEntity(s.b);
		Vec3 a = first != null ? first.position() : s.pos;
		Vec3 b = second != null ? second.position() : s.pos.add(6.0D, 0.0D, 0.0D);
		switch (beat) {
			case EpicIds.TOUR_INTRO -> {
				shot(s, delay, () -> Shots.twoShot(b, 6.0F, 4.5F, 1.2F, 3.0F), CinematicDirector.KIND_SECT, true);
				s.add("ring", new RingRenderer(delay, 20 * 70, s.pos, 8.6F, 9.0F, 0.6F, 10.0F, GOLD, 0.6F));
				for (int k = 0; k < 4; k++) {
					SceneCatalog.ui(s, delay + k * 10, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.7F, 0.9F);
				}
				music(s, delay, MusicCue.Cue.M_BOUT, 60);
				Component na = first != null ? first.getDisplayName() : t("finalist");
				Component nb = second != null ? second.getDisplayName() : t("finalist");
				full(s, delay + 10, t("final_bout.title"), () -> t("final_bout.sub", na, nb), "武", SEAL_RED, 70, null);
			}
			case EpicIds.TOUR_WINNER -> {
				slowmo(s, delay, 20);
				shot(s, delay, () -> Shots.orbit(5.0F, 1.5F, 60.0F, 2.5F), CinematicDirector.KIND_SECT, true);
				for (int k = 0; k < 3; k++) {
					int burst = k;
					s.at(delay + k * 8, () -> EpicParticles.burst(a.add((burst - 1) * 3.0D, 8.0D, 0.0D), n(s, 30, 8), 0.3F, ParticleTypes.FIREWORK, false));
				}
				s.at(delay, () -> EpicParticles.burst(a.add(0.0D, 1.0D, 0.0D), n(s, 20, 6), 0.25F, EpicParticles.dust(GOLD, 1.2F), false));
				sky(s, delay, SkyDirector.Grade.SK_GOLD, 0.3F, 0.0F, 20);
				skyRelease(s, delay + 20, 60, 40);
				sting(s, delay);
				if (s.own) {
					s.at(delay, () -> MusicCue.release(MusicCue.Cue.M_BOUT));
				}
				int kind = s.a;
				music(s, delay + 40, kind == EpicIds.TOUR_INTERNAL ? MusicCue.Cue.M_TRIUMPH : MusicCue.Cue.M_GLORY, 16);
				Component name = first != null ? first.getDisplayName() : t("finalist");
				String key = kind == EpicIds.TOUR_ELITE ? "champion_elite" : kind == EpicIds.TOUR_HUNDRED_DYNASTIES ? "champion_dynasties" : "champion";
				full(s, delay + 20, t(key + ".title"), () -> t(key + ".sub", name), "魁", SEAL_RED, 70, null);
				Element ring = s.get("ring");
				if (ring != null) {
					s.at(delay + 40, ring::stop);
				}
				s.end(delay + 200);
			}
			default -> {
			}
		}
	}

	// ================================================================ E6 sect ceremonies

	static void sect(Scene s, int beat, int delay) {
		Vec3 hall = s.pos;
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 player = minecraft.player != null ? minecraft.player.position() : hall.add(20.0D, 0.0D, 0.0D);
		Vec3 toPlayer = new Vec3(player.x - hall.x, 0.0D, player.z - hall.z);
		Vec3 dir = toPlayer.lengthSqr() < 1.0D ? new Vec3(0.0D, 0.0D, 1.0D) : toPlayer.normalize();
		switch (beat) {
			case EpicIds.SECT_TIER -> {
				Vec3 gate = hall.add(dir.scale(Math.min(40.0D, Math.max(18.0D, Math.sqrt(toPlayer.lengthSqr())))));
				Vec3[] path = {gate.add(0.0D, 14.0D, 0.0D), gate.lerp(hall, 0.4D).add(0.0D, 11.0D, 0.0D), hall.add(dir.scale(10.0D)).add(0.0D, 8.0D, 0.0D)};
				shot(s, delay, () -> Shots.flyover(path, 6.5F).lookAt(hall.add(0.0D, 6.0D, 0.0D)), CinematicDirector.KIND_SECT, true);
				s.add(new PillarRenderer(delay, 200, hall, 48.0F, 0xB8F0C0, 1.2F).rise(20));
				s.add(new TrailRenderer(delay, 200, gate.add(0.0D, 0.5D, 0.0D), hall.add(0.0D, 0.5D, 0.0D), 0.0F, 0.4F, GOLD, 0.7F, false, s.seed).travel(40));
				emit(s, delay, 100, (tick, self) -> {
					if (tick % 4 == 0) {
						EpicParticles.column(hall, 12.0F, 6.0F, n(s, 3, 1), EpicParticles.dust(0xFFC870, 1.5F), 0.06F);
					}
				});
				sky(s, delay, SkyDirector.Grade.SK_JADE, 0.3F, 0.0F, 30);
				skyRelease(s, delay + 30, 140, 40);
				music(s, delay, MusicCue.Cue.M_GENTLE, 12);
				full(s, delay + 20, t("sect_tier.title", Math.max(1, s.a)), () -> t("sect_tier.sub"), "宗", SEAL_RED, 80, null);
				s.end(delay + 220);
			}
			case EpicIds.SECT_TEMPLATE -> {
				if (s.b == 9) {
					heavenlyCourt(s, delay, hall, dir);
					return;
				}
				for (int k = 0; k < 9; k++) {
					double angle = Math.PI * 2.0D * k / 9.0D;
					Vec3 at = hall.add(Math.cos(angle) * 18.0D, 0.0D, Math.sin(angle) * 18.0D);
					s.add(new PillarRenderer(delay + k * 4, 160, at, 32.0F, GOLD, 0.5F).rise(20));
					int burst = k;
					s.at(delay + 20 + k * 6, () -> EpicParticles.burst(hall.add(0.0D, 18.0D + burst, 0.0D), n(s, 24, 6), 0.3F, ParticleTypes.FIREWORK, false));
				}
				music(s, delay, MusicCue.Cue.M_GLORY, 20);
				full(s, delay + 20, t("sect_glory.title"), () -> t("sect_glory.sub"), "宗", SEAL_RED, 80, null);
				s.end(delay + 240);
			}
			case EpicIds.SECT_HALL_LEVEL -> {
				s.add(new PillarRenderer(delay, 80, hall, 16.0F, 0xB8F0C0, 0.4F).rise(10));
				if (s.a == 5) {
					stamp(s, delay + 10, t("sect_returned"), "宗", SEAL_RED);
				}
				s.end(delay + 100);
			}
			default -> {
			}
		}
	}

	/** Istana Langit T9 (20 s). */
	private static void heavenlyCourt(Scene s, int delay, Vec3 hall, Vec3 dir) {
		sky(s, delay, SkyDirector.Grade.SK_GOLD, 0.6F, 0.0F, 40);
		skyRelease(s, delay + 40, 280, 120);
		for (int k = 0; k < 9; k++) {
			// Nine bells rising from F#3 to F#4 (note-block pitch 0.5 → 1.0).
			SceneCatalog.sound(s, delay + k * 10, hall, SoundEvents.BELL_BLOCK, 0.9F, (float) (0.5D * Math.pow(2.0D, k / 8.0D)));
		}
		Vec3 gate = hall.add(dir.scale(48.0D));
		Vec3[] path = {gate.add(0.0D, 2.0D, 0.0D), gate.lerp(hall, 0.35D).add(0.0D, 8.0D, 0.0D), gate.lerp(hall, 0.7D).add(0.0D, 14.0D, 0.0D),
				hall.add(dir.scale(12.0D)).add(0.0D, 20.0D, 0.0D)};
		shot(s, delay + 10, () -> Shots.flyover(path, 8.0F).lookAt(hall.add(0.0D, 24.0D, 0.0D)), CinematicDirector.KIND_SECT, true);
		shotPenuh(s, delay + 170, () -> Shots.pullback(12.0F, 40.0F, 6.0F, 24.0F, 4.5F).asCeremony(), CinematicDirector.KIND_SECT);
		for (int k = 0; k < 5; k++) {
			double angle = Math.PI * 2.0D * k / 5.0D;
			s.add(new PillarRenderer(delay + 20 + k * 6, 360, hall.add(Math.cos(angle) * 40.0D, 10.0D, Math.sin(angle) * 40.0D), 48.0F, GOLD, 0.5F).rise(30));
		}
		s.add(new PillarRenderer(delay + 50, 340, hall.add(0.0D, 20.0D, 0.0D), 96.0F, WHITE_GOLD, 3.0F).rise(40));
		s.add(new RaysRenderer(delay + 60, 300, hall.add(0.0D, 90.0D, 0.0D), 80.0F, 12, 22.0F, 5.0F, GOLD, 0.2F, false));
		glyph(s, delay + 170, hall.add(0.0D, 70.0D, 0.0D), "天庭", GOLD, 40.0F, 120);
		emit(s, delay + 170, 200, (tick, self) -> {
			if (tick % 3 != 0 || lite(s)) {
				return;
			}
			for (int c = 0; c < 9; c++) {
				double angle = Math.PI * 2.0D * c / 9.0D + tick * 0.01D;
				Vec3 base = hall.add(Math.cos(angle) * 44.0D, 40.0D + (c % 3) * 4.0D, Math.sin(angle) * 44.0D);
				EpicParticles.outline(PointClouds.crane(), base, new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)), 6.0D, 2, EpicParticles.dust(0xFFFFFF, 2.0F),
						true);
			}
			if (tick % 30 == 0) {
				EpicParticles.burst(hall.add(0.0D, 50.0D, 0.0D), n(s, 30, 8), 0.4F, EpicParticles.dust(GOLD, 2.0F), false);
			}
		});
		emit(s, delay, 200, (tick, self) -> {
			if (tick % 3 == 0) {
				EpicParticles.ring(hall, 60.0F, n(s, 12, 3), EpicParticles.dust(0xFFE6A0, 2.0F), 0.0F);
			}
		});
		music(s, delay + 10, MusicCue.Cue.M_GLORY, 20);
		full(s, delay + 180, t("heavenly_court.title"), () -> t("heavenly_court.sub"), "庭", SEAL_RED, 80, null);
		s.end(delay + 420);
	}

	private SceneCatalogSect() {
	}
}
