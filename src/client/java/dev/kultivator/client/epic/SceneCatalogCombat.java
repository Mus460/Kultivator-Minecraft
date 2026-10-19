package dev.kultivator.client.epic;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.epic.fx.PointClouds;
import dev.kultivator.client.epic.render.GiantFigureRenderer;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import dev.kultivator.client.epic.render.PillarRenderer;
import dev.kultivator.client.epic.render.RingRenderer;
import dev.kultivator.client.epic.render.TrailRenderer;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.data.Realm;
import dev.kultivator.data.VisualState;
import dev.kultivator.epic.EpicIds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static dev.kultivator.client.epic.SceneCatalog.GOLD;
import static dev.kultivator.client.epic.SceneCatalog.JADE;
import static dev.kultivator.client.epic.SceneCatalog.PALE_GOLD;
import static dev.kultivator.client.epic.SceneCatalog.SEAL_RED;
import static dev.kultivator.client.epic.SceneCatalog.edge;
import static dev.kultivator.client.epic.SceneCatalog.emit;
import static dev.kultivator.client.epic.SceneCatalog.flash;
import static dev.kultivator.client.epic.SceneCatalog.full;
import static dev.kultivator.client.epic.SceneCatalog.lite;
import static dev.kultivator.client.epic.SceneCatalog.music;
import static dev.kultivator.client.epic.SceneCatalog.n;
import static dev.kultivator.client.epic.SceneCatalog.nameOf;
import static dev.kultivator.client.epic.SceneCatalog.shot;
import static dev.kultivator.client.epic.SceneCatalog.shotPenuh;
import static dev.kultivator.client.epic.SceneCatalog.sky;
import static dev.kultivator.client.epic.SceneCatalog.skyRelease;
import static dev.kultivator.client.epic.SceneCatalog.slowmo;
import static dev.kultivator.client.epic.SceneCatalog.snap;
import static dev.kultivator.client.epic.SceneCatalog.stamp;
import static dev.kultivator.client.epic.SceneCatalog.t;
import static dev.kultivator.client.epic.SceneCatalog.upper;

/**
 * Storyboards of DESIGN 23.13 groups F (boss kill-cams, cheat-death) and G (the short extras), plus the client-derived
 * enlightenment. Cheat-death never moves the camera (real danger): only slow motion, a FOV punch, one flash, an ink edge
 * and a STAMP.
 */
final class SceneCatalogCombat {

	private static final String[] BOSS_KEYS = {"warden", "wither", "dragon", "elder_guardian", "inheritance", "demon_king", "penjaga_alam",
			"tetua_iblis", "binatang_purba", "panglima_langit"};
	/** The Heavenly Flame ids and colours of {@code PowerIds} (copied, read-only). */
	private static final String[] FLAME_IDS = {"green_lotus", "bone_chilling", "sea_heart", "fallen_heart", "pure_lotus"};
	private static final int[] FLAME_RGB = {0x4FE07A, 0xBFEFFF, 0x3D8BFF, 0xFF7A2A, 0xC24BFF};

	// ================================================================ F1 boss kills

	static void boss(Scene s, int delay) {
		int kind = Math.max(0, Math.min(BOSS_KEYS.length - 1, s.a));
		Vec3 at = s.pos;
		boolean first = s.has(EpicIds.FLAG_FIRST);
		Minecraft minecraft = Minecraft.getInstance();
		boolean near = minecraft.player != null && minecraft.player.position().distanceTo(at) <= 32.0D;
		snap(s, delay, -6.0F, 6, 0);
		if (s.own || near) {
			s.at(delay, () -> EpicClock.slowmo(24, s.priority));
		}
		flash(s, delay, 0xFFFFFF, 0.15F, 3, 0);
		s.add(new TrailRenderer(delay, 40, at.add(0.0D, 1.0D, 0.0D), at.add(0.0D, 13.0D, 0.0D), 1.5F, 0.6F, 0xE8F0FF, 0.6F, false, s.seed).travel(30).tail(10));
		boolean penuh = CinematicDirector.mode() == EpicSettings.Camera.PENUH;
		if (kind == EpicIds.BOSS_DRAGON) {
			if (first || penuh) {
				shot(s, delay + 6, () -> Shots.pullback(4.0F, 24.0F, 2.0F, 16.0F, 4.0F).lookAt(at), CinematicDirector.KIND_BOSS, true);
			}
		} else if ((first || penuh) && minecraft.player != null && minecraft.player.position().distanceTo(at) <= 24.0D) {
			float r = kind == EpicIds.BOSS_WARDEN ? 6.0F : 8.0F;
			shot(s, delay + 6, () -> Shots.killcam(at, r, 60.0F, 3.0F), CinematicDirector.KIND_BOSS, true);
		}
		// Phase 8 subjects (DESIGN 25.2 item 15) keep their names in the spine8 fragment.
		// Phase 9 subjects (DESIGN 26.14 item 4) keep theirs in the spine9 fragment.
		Component name = kind >= EpicIds.BOSS_PRIMORDIAL ? Component.translatable("kultivator.spine9.boss." + BOSS_KEYS[kind])
				: kind >= EpicIds.BOSS_REALM_GUARDIAN ? Component.translatable("kultivator.spine8.boss." + BOSS_KEYS[kind])
				: t("boss." + BOSS_KEYS[kind]);
		Component title = kind == EpicIds.BOSS_DEMON_KING ? t("boss_demon_king.title") : t("boss.title", upper(name));
		if (first) {
			music(s, delay + 6, MusicCue.Cue.M_TRIUMPH, 14);
			full(s, delay + 10, title, null, "勝", SEAL_RED, 70, null);
		} else {
			stamp(s, delay + 10, title, "勝", SEAL_RED);
		}
		int flame = s.b;
		if (first && flame != EpicIds.FLAME_NONE) {
			int rgb = flame == EpicIds.FLAME_SEA_HEART ? 0x2A6AFF : flame == EpicIds.FLAME_FALLING_HEART ? 0xC8A8FF : 0xF0F0F0;
			emit(s, delay + 40, 80, (tick, self) -> {
				EpicParticles.column(at, 6.0F, 0.5F, n(s, 4, 1), flame == EpicIds.FLAME_SEA_HEART ? ParticleTypes.SOUL_FIRE_FLAME : EpicParticles.dust(rgb, 1.4F),
						0.06F);
				if (flame == EpicIds.FLAME_DEMON_LOTUS && tick % 4 == 0) {
					EpicParticles.outline(PointClouds.lotus(), at, new Vec3(1.0D, 0.0D, 0.0D), 4.0D, n(s, 6, 2), EpicParticles.dust(tick % 8 == 0 ? 0xFFFFFF : 0x101010, 1.2F),
							false);
				}
			});
			stamp(s, delay + 50, t("flame_seed", t("flame." + flame)), "炎", SEAL_RED);
		}
		s.end(delay + 160);
	}

	// ================================================================ F2 cheat-death

	static void cheatDeath(Scene s, int delay) {
		int kind = s.a;
		Vec3 at = s.feet(0.0F);
		Component name = nameOf(s, "disciple");
		switch (kind) {
			case EpicIds.SAVE_UNBROKEN_SOUL -> {
				slowmo(s, delay, 30);
				emit(s, delay, 24, (tick, self) -> {
					if (tick < 8) {
						return;
					}
					EpicParticles.outline(PointClouds.humanoid(), s.feet(0.0F), SceneCatalog.forward(s.yaw() + 90.0F), 1.9D, n(s, 12, 4), EpicParticles.dust(GOLD, 0.8F),
							false);
				});
				s.add(new RingRenderer(delay, 30, at, 1.0F, 6.0F, 1.0F, 12.0F, GOLD, 0.7F));
				edge(s, delay, 0xE8C040, 0.5F, 40);
				flash(s, delay, GOLD, 0.2F, 4, 0);
				stamp(s, delay, t("save.unbroken"), "魂", 0xC8A030);
			}
			case EpicIds.SAVE_HAIR -> {
				emit(s, delay, 20, (tick, self) -> {
					Vec3 leap = at.add(tick * 0.3D, 1.0D + Math.sin(tick * 0.3D) * 1.5D, 0.0D);
					EpicParticles.outline(PointClouds.ape(), leap, new Vec3(1.0D, 0.0D, 0.0D), 3.0D, n(s, 6, 2), EpicParticles.dust(GOLD, 0.9F), false);
				});
				snap(s, delay, 6.0F, 4, 4);
				stamp(s, delay, s.b > 0 ? t("save.hair_left", s.b) : t("save.hair"), "毛", 0xC8A030);
			}
			case EpicIds.SAVE_LOTUS -> {
				slowmo(s, delay, 20);
				emit(s, delay, 20, (tick, self) -> EpicParticles.outline(PointClouds.lotus(), s.feet(0.0F), new Vec3(1.0D, 0.0D, 0.0D), 6.0D * (tick < 10 ? 1.0D - tick / 12.0D
						: (tick - 10) / 10.0D), n(s, 10, 3), EpicParticles.dust(0xFFB7D5, 1.4F), false));
				flash(s, delay + 10, 0xFFE8F0, 0.2F, 4, 0);
				stamp(s, delay, t("save.lotus"), "莲", 0xD86090);
			}
			case EpicIds.SAVE_TREE -> {
				emit(s, delay, 20, (tick, self) -> EpicParticles.ring(s.feet(0.0F).add(0.0D, 2.2D, 0.0D), 1.2F, n(s, 8, 2), EpicParticles.dust(0x6CC060, 1.2F), 0.0F));
				stamp(s, delay, t("save.tree"), "菩", 0x3B9A50);
			}
			case EpicIds.SAVE_UNDYING_BLOOD -> {
				emit(s, delay, 40, (tick, self) -> {
					if (tick % 10 == 0) {
						EpicParticles.ring(s.feet(0.0F).add(0.0D, 1.0D, 0.0D), 1.0F, n(s, 12, 4), EpicParticles.dust(0xC03020, 1.2F), 0.04F);
					}
				});
				stamp(s, delay, t("save.blood", name), "血", SEAL_RED);
			}
			case EpicIds.SAVE_SOUL_SHIELD -> {
				Vec3 behind = at.add(SceneCatalog.forward(s.yaw()).scale(-0.8D));
				if (!lite(s)) {
					s.add(new GiantFigureRenderer(delay, 40, behind, s.yaw(), 3.0F, EpicRenderTypes.IMMORTAL_AVATAR, 0xCFE8FF, 0.3F));
				}
				stamp(s, delay, t("save.soul_shield"), "師", 0x5A8AC8);
			}
			case EpicIds.SAVE_REPAY -> {
				Minecraft minecraft = Minecraft.getInstance();
				Entity master = minecraft.level == null ? null : minecraft.level.getEntity(s.viewerId);
				Vec3 to = master != null ? master.position().add(0.0D, 1.0D, 0.0D) : at.add(4.0D, 1.0D, 0.0D);
				s.add(new TrailRenderer(delay, 20, at.add(0.0D, 1.0D, 0.0D), to, 2.0F, 0.4F, GOLD, 0.9F, false, s.seed).travel(8).tail(4));
				s.add(new RingRenderer(delay + 8, 30, master != null ? master.position() : at, 1.0F, 12.0F, 1.0F, 10.0F, GOLD, 0.6F));
				stamp(s, delay, t("save.repay", name), "恩", 0xC8A030);
			}
			case EpicIds.SAVE_SOUL_FLIGHT -> {
				Vec3 stele = at.add(30.0D, 0.0D, 0.0D);
				s.add(new TrailRenderer(delay, 60, at.add(0.0D, 1.0D, 0.0D), stele, 24.0F, 0.3F, GOLD, 0.9F, false, s.seed).travel(40).tail(14));
				stamp(s, delay, t("save.soul_flight", name), "魂", 0xC8A030);
			}
			default -> {
				flash(s, delay, GOLD, 0.15F, 3, 0);
				edge(s, delay, 0xE8C040, 0.4F, 30);
				stamp(s, delay, t("save.generic"), "命", 0xC8A030);
			}
		}
		s.end(delay + 80);
	}

	// ================================================================ G extras

	static void extra(Scene s, int beat, int delay) {
		Vec3 at = s.pos;
		switch (beat) {
			case EpicIds.EXTRA_ROOT_TEST -> {
				shot(s, delay, () -> Shots.orbit(5.0F, 1.6F, 120.0F, 5.0F), CinematicDirector.KIND_EXTRA, true);
				// a = the RootGrade ordinal (TRASH 0 … HEAVENLY 4), b = the element mask (0 = show all).
				int[] elements = {0xD8E6FF, 0xFF6A2A, 0x6CFF8A, 0x5AB8FF, 0xD9A05B, 0xB57CFF, 0x9FF3FF, 0x5A3FD0};
				int shown = 0;
				for (int k = 0; k < elements.length; k++) {
					if (s.b != 0 ? (s.b & 1 << k) == 0 : k >= 5) {
						continue;
					}
					double angle = shown * 1.256D;
					s.add(new PillarRenderer(delay + shown * 12, 40, at.add(Math.cos(angle) * 2.0D, 0.0D, Math.sin(angle) * 2.0D), 10.0F, elements[k], 0.4F)
							.rise(8));
					shown++;
				}
				boolean heavenly = s.a >= dev.kultivator.data.RootGrade.HEAVENLY.ordinal();
				if (heavenly) {
					sky(s, delay + 60, SkyDirector.Grade.SK_WHITE, 0.6F, 0.0F, 20);
					skyRelease(s, delay + 80, 40, 40);
					music(s, delay + 60, MusicCue.Cue.M_GLORY, 16);
				}
				dev.kultivator.data.RootGrade[] grades = dev.kultivator.data.RootGrade.values();
				Component grade = grades[Math.max(0, Math.min(grades.length - 1, s.a))].displayName();
				full(s, delay + 70, t("root.title", upper(grade)), null, "根", SEAL_RED, 60, null);
			}
			case EpicIds.EXTRA_BEAST_TIER3 -> {
				shot(s, delay, () -> Shots.orbit(5.0F, 1.6F, 80.0F, 4.0F), CinematicDirector.KIND_EXTRA, true);
				// a = the beast kind (BeastData.WOLF 0: a moon disc; FOX 1: a fan of nine light tails).
				boolean wolf = s.a == 0;
				emit(s, delay, 60, (tick, self) -> {
					if (tick % 2 != 0) {
						return;
					}
					if (wolf) {
						EpicParticles.ring(s.feet(0.0F).add(0.0D, 3.5D, 0.0D), 1.6F, n(s, 10, 3), EpicParticles.dust(0xE8F0FF, 1.2F), 0.0F);
					} else {
						EpicParticles.outline(PointClouds.tails(), s.feet(0.0F).add(0.0D, 0.5D, 0.0D), SceneCatalog.forward(s.yaw() + 90.0F), 3.0D, n(s, 8, 2),
								EpicParticles.dust(0xFFB070, 1.0F), false);
					}
				});
				stamp(s, delay + 10, t(wolf ? "beast.wolf" : "beast.fox"), "獸", SEAL_RED);
			}
			case EpicIds.EXTRA_HEIR -> {
				Minecraft minecraft = Minecraft.getInstance();
				Entity master = minecraft.level == null ? null : minecraft.level.getEntity(s.viewerId);
				Vec3 masterPos = master != null ? master.position() : at.add(3.0D, 0.0D, 0.0D);
				shot(s, delay, () -> Shots.twoShot(masterPos, 6.0F, 4.5F, 1.2F, 3.0F), CinematicDirector.KIND_EXTRA, true);
				s.add(new TrailRenderer(delay, 80, masterPos.add(0.0D, 1.0D, 0.0D), at.add(0.0D, 1.0D, 0.0D), 0.5F, 0.15F, JADE, 0.8F, false, s.seed).travel(20));
				full(s, delay + 10, t("heir.title", Math.max(1, s.a)), null, "承", SEAL_RED, 60, null);
			}
			case EpicIds.EXTRA_RING_BOND -> stamp(s, delay, t("ring_bond"), "師", 0x5A8AC8);
			case EpicIds.EXTRA_MASTER_DESCENDS -> {
				emit(s, delay, 400, (tick, self) -> {
					if (tick % 4 == 0) {
						EpicParticles.outline(PointClouds.humanoid(), s.feet(0.0F), SceneCatalog.forward(s.yaw() + 90.0F), 2.2D, n(s, 6, 2),
								EpicParticles.dust(s.a != 0 ? s.a : Realm.byOrdinal(ClientCultivation.data().realm().ordinal()).color(), 0.9F), false);
					}
				});
				stamp(s, delay, t("master_descends"), "師", 0x5A8AC8);
			}
			case EpicIds.EXTRA_FLAME_ABSORBED -> {
				shot(s, delay, () -> Shots.orbit(5.0F, 1.6F, 60.0F, 3.0F), CinematicDirector.KIND_EXTRA, true);
				// a = the flame index of PowerIds.FLAME_IDS (names read-only from the seed item keys).
				int index = Math.max(0, Math.min(FLAME_IDS.length - 1, s.a));
				int rgb = FLAME_RGB[index];
				emit(s, delay, 60, (tick, self) -> EpicParticles.column(s.feet(0.0F), 8.0F, 0.6F, n(s, 5, 1), EpicParticles.dust(rgb, 1.3F), 0.08F));
				stamp(s, delay, t("flame_absorbed", Component.translatable("item.kultivator.heavenly_flame_seed." + FLAME_IDS[index])), "炎", SEAL_RED);
			}
			case EpicIds.EXTRA_VOID_BORDER -> {
				s.add(new RingRenderer(delay, 40, at, 1.0F, 8.0F, 1.0F, 16.0F, GOLD, 0.6F));
				stamp(s, delay, t("void_border"), "界", SEAL_RED);
			}
			// Phase 8 (DESIGN 25.2 item 15): stamps and rings only, never a camera shot (23.1: the player may be in danger).
			case EpicIds.EXTRA_REALM_GATE -> {
				s.add(new RingRenderer(delay, 60, at, 1.0F, 6.0F, 1.0F, 12.0F, GOLD, 0.5F));
				stamp(s, delay, Component.translatable("kultivator.spine8.momen.realm_gate"), "門", SEAL_RED);
			}
			case EpicIds.EXTRA_REALM_CLEAR -> stamp(s, delay, Component.translatable("kultivator.spine8.momen.realm_clear"), "秘", SEAL_RED);
			case EpicIds.EXTRA_RAID_OMEN -> stamp(s, delay, Component.translatable("kultivator.spine8.momen.raid_omen"), "旗", SEAL_RED);
			case EpicIds.EXTRA_SPIRIT_AWAKEN -> stamp(s, delay, Component.translatable("kultivator.spine8.momen.spirit_awaken"), "靈", SEAL_RED);
			case EpicIds.EXTRA_NATAL_BOND -> stamp(s, delay, Component.translatable("kultivator.spine8.momen.natal_bond"), "寶", SEAL_RED);
			case EpicIds.EXTRA_VILLAGE_OATH -> stamp(s, delay, Component.translatable("kultivator.spine8.momen.village_oath"), "誓", 0x5A8AC8);
			// Phase 9 (DESIGN 26.14 item 4): stamps and rings only, never a camera shot (23.1).
			case EpicIds.EXTRA_ASCEND_OMEN -> {
				s.add(new RingRenderer(delay, 80, at, 1.0F, 8.0F, 1.0F, 18.0F, GOLD, 0.5F));
				stamp(s, delay, Component.translatable("kultivator.spine9.momen.ascend_omen"), "昇", SEAL_RED);
			}
			case EpicIds.EXTRA_ASCEND_PASS -> {
				s.add(new RingRenderer(delay, 60, at, 1.0F, 6.0F, 1.0F, 14.0F, GOLD, 0.6F));
				stamp(s, delay, Component.translatable("kultivator.spine9.momen.ascend_pass"), "仙", SEAL_RED);
			}
			case EpicIds.EXTRA_PRIMORDIAL_OMEN -> stamp(s, delay, Component.translatable("kultivator.spine9.momen.primordial_omen"), "獸", SEAL_RED);
			case EpicIds.EXTRA_PRIMORDIAL_PHASE -> stamp(s, delay, Component.translatable("kultivator.spine9.momen.primordial_phase"), "變", SEAL_RED);
			case EpicIds.EXTRA_WRATH_OMEN -> stamp(s, delay, Component.translatable("kultivator.spine9.momen.wrath_omen"), "天", SEAL_RED);
			case EpicIds.EXTRA_PLAYER_TRAMPLING -> stamp(s, delay, Component.translatable("kultivator.spine9.momen.player_trampling"), "踏", SEAL_RED);
			case EpicIds.EXTRA_INNER_CALAMITY -> stamp(s, delay, Component.translatable("kultivator.spine9.momen.inner_calamity"), "災", SEAL_RED);
			case EpicIds.EXTRA_OWN_ART_BORN -> stamp(s, delay, Component.translatable("kultivator.spine9.momen.own_art_born"), "創", 0xC8A030);
			case EpicIds.EXTRA_AUCTION_WIN -> stamp(s, delay, Component.translatable("kultivator.spine9.momen.auction_win"), "拍", 0x5A8AC8);
			default -> {
			}
		}
		s.end(delay + 140);
	}

	/** Momen Pencerahan (client-derived, local player): an orbit, a golden lotus under the player, soft gold sky. */
	static void enlighten(Scene s) {
		shot(s, 0, () -> Shots.orbit(4.0F, 1.2F, 90.0F, 5.0F), CinematicDirector.KIND_DHARMA, true);
		emit(s, 0, 100, (tick, self) -> {
			if (tick % 3 == 0) {
				EpicParticles.outline(PointClouds.lotus(), s.feet(0.0F).add(0.0D, 0.1D, 0.0D), SceneCatalog.forward(s.yaw() + 90.0F), 1.5D, n(s, 6, 2),
						EpicParticles.dust(PALE_GOLD, 0.8F), false);
			}
		});
		sky(s, 0, SkyDirector.Grade.SK_GOLD, 0.25F, 0.0F, 40);
		skyRelease(s, 40, 120, 60);
		music(s, 0, MusicCue.Cue.M_GENTLE, 12);
		stamp(s, 0, t("enlighten"), "悟", 0xC8A030);
		s.end(220);
	}

	private SceneCatalogCombat() {
	}
}
