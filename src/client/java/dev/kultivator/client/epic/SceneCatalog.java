package dev.kultivator.client.epic;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.ShotGuard;
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
import dev.kultivator.client.epic.screen.EpicScreen;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.epic.sound.EpicSound;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.client.fx.spec.CameraShake;
import dev.kultivator.cultivation.ImmortalPerks;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.data.VisualState;
import dev.kultivator.epic.EpicColors;
import dev.kultivator.epic.EpicIds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The storyboards of DESIGN 23.13 as data in code: every (moment, beat) schedules the elements of its tracks (camera,
 * bars, cards, sky, vortex, bolts, pillars, rays, rings, gates, aurora, trails, seams, figures, particles, displays,
 * flashes, shake, edges, slow motion, sound and music) on its {@link Scene}, timed from the beat's server landing tick.
 * This file holds the shared helpers and groups A (Tribulasi Langit), B (breakthroughs, Pengakuan Langit) and C (crises);
 * D lives in {@link SceneCatalogPower}, E in {@link SceneCatalogSect}, F and G in {@link SceneCatalogCombat}. Story beats
 * keep the Sumber lines of DESIGN 23.13; camera, music, colours, pillars, seals and shapes are Presentasi and no text
 * presents them as canon.
 */
public final class SceneCatalog {

	// ================================================================ scene scale S (23.10.1)

	static final float[] R_V = {0.0F, 0.0F, 64.0F, 112.0F, 176.0F, 256.0F, 384.0F, 512.0F, 704.0F, 1024.0F, 1024.0F};
	static final float[] CLOUD_H = {0.0F, 0.0F, 40.0F, 48.0F, 56.0F, 64.0F, 72.0F, 84.0F, 96.0F, 112.0F, 112.0F};
	static final float[] EYE = {0.0F, 0.0F, 6.0F, 8.0F, 10.0F, 12.0F, 14.0F, 16.0F, 18.0F, 24.0F, 24.0F};
	static final float[] BOLT_W = {0.2F, 0.2F, 0.25F, 0.30F, 0.35F, 0.40F, 0.50F, 0.60F, 0.70F, 0.90F, 1.0F};
	static final float[] GLOW = {0.6F, 0.8F, 1.0F, 1.2F, 1.5F, 1.8F, 2.0F, 2.4F, 2.8F, 3.5F, 4.0F};
	static final float[] DARK = {0.0F, 0.10F, 0.35F, 0.45F, 0.55F, 0.60F, 0.65F, 0.70F, 0.75F, 0.80F, 0.80F};
	static final float[] SHAKE = {0.0F, 0.0F, 0.6F, 0.7F, 0.8F, 0.9F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F};
	static final String[] SEAL = {"气", "基", "丹", "婴", "神", "道", "仙", "仙", "仙", "天", "星"};

	static final int GOLD = 0xFFD54A;
	static final int PALE_GOLD = 0xFFF0B0;
	static final int SEAL_RED = 0xB3281D;
	static final int JADE = 0x8FE3C8;
	static final int WHITE_GOLD = 0xFFF6D8;
	/** The seven soul colours (Tales of Demons and Gods ch. 13-14: no purple). */
	static final int[] SOUL = {0xFF3B3B, 0xFF8A1A, 0xFFD54A, 0x3BDB7A, 0x3BE0E0, 0x4AA0FF, 0x4B4BCF};
	static final String[] DAO_GLYPH = {"剑", "炎", "冰", "雷", "体", "生", "影", "空"};

	/** Realm info of a BREAKTHROUGH SUCCESS waiting to join its tribulation scene (either order, 23.6 merging). */
	private record PendingRealm(int realm, int quality, int layer, long tick) {
	}

	private static final Map<Integer, PendingRealm> PENDING = new HashMap<>();

	/** Tribulation data shared between its beats. */
	static final class Trib {
		int variant;
		int realm;
		int layer;
		int total;
		boolean disciple;
		float radius;
		float height;
		float eye;
		boolean hardcore;
		boolean ended;
		int realmShown = -1;
		int quality = -1;
		int strikes;
	}

	// ================================================================ dispatch

	/** Schedules one server beat of {@code moment} on its scene; {@code delay} = ticks until the beat lands. */
	static void beat(Scene s, int moment, int beat, int delay) {
		switch (moment) {
			case EpicIds.TRIBULATION -> tribulation(s, beat, delay);
			case EpicIds.BREAKTHROUGH -> breakthrough(s, beat, delay);
			case EpicIds.ASCENSION -> ascension(s, beat, delay);
			case EpicIds.HEART_DEMON -> heartDemon(s, beat, delay);
			case EpicIds.DISCIPLE_BT -> SceneCatalogSect.discipleBreakthrough(s, beat, delay);
			case EpicIds.CALAMITY -> SceneCatalogSect.calamity(s, beat, delay);
			case EpicIds.SURPASS -> SceneCatalogSect.surpass(s, delay);
			case EpicIds.RECRUIT -> SceneCatalogSect.recruit(s, beat, delay);
			case EpicIds.TOURNAMENT -> SceneCatalogSect.tournament(s, beat, delay);
			case EpicIds.SECT -> SceneCatalogSect.sect(s, beat, delay);
			case EpicIds.KEHANCURAN -> SceneCatalogPower.kehancuran(s, beat, delay);
			case EpicIds.BOSS -> SceneCatalogCombat.boss(s, delay);
			case EpicIds.CHEAT_DEATH -> SceneCatalogCombat.cheatDeath(s, delay);
			case EpicIds.EXTRA -> SceneCatalogCombat.extra(s, beat, delay);
			case EpicIds.TECH_ULTIMATE -> SceneCatalogPower.ultimateTest(s, beat, delay);
			case EpicIds.DHARMA -> s.at(delay, () -> dharma(s, testVisual(s)));
			case EpicIds.DOMAIN -> s.at(delay, () -> domain(s, s.a, testVisual(s)));
			case EpicIds.ENLIGHTEN -> s.at(delay, () -> enlighten(s));
			case EpicIds.DEVIATION -> s.at(delay, () -> deviation(s, false));
			case EpicIds.KEHANCURAN_SELF -> s.at(delay, () -> kehancuranSelf(s, null));
			default -> {
			}
		}
	}

	// Client-derived entries (from the director).
	static void dharma(Scene s, VisualState visual) {
		SceneCatalogPower.dharma(s, visual);
	}

	static void domain(Scene s, int dao, VisualState visual) {
		SceneCatalogPower.domain(s, dao, visual);
	}

	static void enlighten(Scene s) {
		SceneCatalogCombat.enlighten(s);
	}

	static void kehancuranSelf(Scene s, TechniqueSpec spec) {
		SceneCatalogPower.kehancuranSelf(s, spec);
	}

	static void ultimate(Scene s, TechniqueSpec spec, int beat, Vec3 target, boolean test) {
		SceneCatalogPower.ultimate(s, spec, beat, target, test);
	}

	private static VisualState testVisual(Scene s) {
		Entity subject = s.subject();
		VisualState visual = subject == null ? VisualState.NONE : ClientCultivation.visual(subject);
		return visual;
	}

	// ================================================================ shared helpers

	static Component t(String key, Object... args) {
		return Component.translatable("kultivator.momen." + key, args);
	}

	static Component upper(Component text) {
		return Component.literal(text.getString().toUpperCase(Locale.ROOT));
	}

	static Component realmName(int ordinal) {
		return Realm.byOrdinal(ordinal).displayName();
	}

	static Component qualityName(int ordinal) {
		BreakthroughQuality[] values = BreakthroughQuality.values();
		return values[Mth.clamp(ordinal, 0, values.length - 1)].displayName();
	}

	static Component nameOf(Scene s, String fallbackKey) {
		Entity subject = s.subject();
		return subject != null ? subject.getDisplayName() : t(fallbackKey);
	}

	static int idx(int s) {
		return Mth.clamp(s, 0, 10);
	}

	static boolean lite(Scene s) {
		return !s.own && EffectQuality.others() == EffectQuality.Others.LIGHT;
	}

	static int tier() {
		return EpicBudget.tierOrMinimal();
	}

	/** A count × the scene scale (view, VFX setting, LOW_LOAD), at least {@code min}. */
	static int n(Scene s, float count, int min) {
		return Math.max(min, Math.round(count * s.scale()));
	}

	/** A FULL card for the protagonist; {@code sub} is read when the card shows. */
	static void full(Scene s, int delay, Component title, Supplier<Component> sub, String seal, int sealRgb, int ticks, Consumer<TitleCard.Card> tweak) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> {
			TitleCard.Card card = TitleCard.full(title, sub == null ? null : sub.get(), seal, sealRgb, ticks);
			card.suppressTitle(s.has(EpicIds.FLAG_SUPPRESS_TITLE)).safeBeat(s.has(EpicIds.FLAG_SAFE));
			if (tweak != null) {
				tweak.accept(card);
			}
			TitleCard.show(card);
		});
	}

	static void stamp(Scene s, int delay, Component title, String seal, int rgb) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> TitleCard.show(TitleCard.stamp(title, seal, rgb)));
	}

	static void banner(Scene s, int delay, Component title) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> TitleCard.show(TitleCard.banner(title)));
	}

	static void music(Scene s, int delay, MusicCue.Cue cue, int seconds) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> MusicCue.play(cue, seconds, s.priority));
	}

	static void sky(Scene s, int delay, SkyDirector.Grade grade, float w, float darkness, int blend) {
		float others = s.own ? 1.0F : lite(s) ? 0.5F : 1.0F;
		s.at(delay, () -> SkyDirector.grade(grade, w, darkness, blend, s.priority, s.pos, s.own, others, s.pos.y));
	}

	static void skyRelease(Scene s, int delay, int hold, int release) {
		s.at(delay, () -> SkyDirector.holdThenRelease(hold, release, s.priority));
	}

	/** A flash and sky flash as one luminance event: full for the protagonist, half within 24 b, none beyond. */
	static void flash(Scene s, int delay, int rgb, float alpha, int ticks, int skyFlash) {
		s.at(delay, () -> {
			double distance = distanceToCamera(s.pos);
			float a = s.own ? alpha : distance <= 24.0D ? Math.min(alpha, 0.08F) : 0.0F;
			int sky = s.own || distance <= 48.0D ? skyFlash : 0;
			if (a > 0.0F || sky > 0) {
				EpicScreen.flash(rgb, a, ticks, sky);
			}
		});
	}

	static void shake(Scene s, int delay, float intensity, int ticks) {
		s.at(delay, () -> CameraShake.add(intensity * (CinematicDirector.active() ? 0.3F : 1.0F), ticks, s.pos, 8.0F));
	}

	static void shot(Scene s, int delay, Supplier<Shots.Shot> shot, int kind, boolean safeBeat) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> CinematicDirector.shoot(s, shot.get(), kind, s.priority, safeBeat));
	}

	/** A Penuh-only shot of a safe beat. */
	static void shotPenuh(Scene s, int delay, Supplier<Shots.Shot> shot, int kind) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> {
			if (CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
				CinematicDirector.shoot(s, shot.get(), kind, s.priority, true);
			}
		});
	}

	static void slowmo(Scene s, int delay, int realTicks) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> EpicClock.slowmo(realTicks, s.priority));
	}

	static void hitStop(Scene s, int delay) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> {
			EpicClock.hitStop();
			EpicSound.subBass();
		});
	}

	static void snap(Scene s, int delay, float percent, int in, int hold) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> CinematicDirector.snap(percent, in, hold));
	}

	static void edge(Scene s, int delay, int rgb, float alpha, int ticks) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> EpicScreen.inkEdge(rgb, alpha, ticks));
	}

	static void grey(Scene s, int delay, float alpha, int ticks) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> EpicScreen.grey(alpha, ticks));
	}

	static void chroma(Scene s, int delay, int ticks) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> EpicScreen.chroma(ticks));
	}

	static void ui(Scene s, int delay, SoundEvent sound, float pitch, float volume) {
		if (!s.own) {
			return;
		}
		s.at(delay, () -> EpicSound.ui(sound, pitch, volume));
	}

	static void sound(Scene s, int delay, Vec3 at, SoundEvent sound, float volume, float pitch) {
		s.at(delay, () -> EpicSound.at(at, sound, volume, pitch));
	}

	static void sting(Scene s, int delay) {
		if (!s.own) {
			return;
		}
		s.at(delay, EpicSound::sting);
	}

	static void glyph(Scene s, int delay, Vec3 at, String text, int rgb, float size, int life) {
		if (lite(s) || s.has(EpicIds.FLAG_LOW_LOAD)) {
			return;
		}
		s.at(delay, () -> {
			if (EpicDisplays.glyph(at, text, rgb, size, life) == null) {
				EpicParticles.burst(at, 20, 0.1F, EpicParticles.dust(rgb, 2.0F), true);
			}
		});
	}

	static double distanceToCamera(Vec3 pos) {
		return Math.sqrt(Minecraft.getInstance().gameRenderer.mainCamera().position().distanceToSqr(pos));
	}

	static Vec3 forward(float yaw) {
		double r = Math.toRadians(yaw);
		return new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
	}

	/** The seeded random point on a circle of radius r0..r1 around {@code c}. */
	static Vec3 around(Scene s, Vec3 c, float r0, float r1) {
		double angle = s.random().nextDouble() * Math.PI * 2.0D;
		double r = r0 + s.random().nextDouble() * (r1 - r0);
		return c.add(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r);
	}

	static int branches(int s) {
		return switch (tier()) {
			case 0 -> 0;
			case 1 -> 1;
			default -> 2 + s / 2;
		};
	}

	/** A particle emitter for {@code ticks} after {@code delay}. */
	static void emit(Scene s, int delay, int ticks, Element.Ticker ticker) {
		s.add(Element.every(delay, ticks, ticker));
	}

	/** The first solid block above {@code pos} within 32 b (Nether ceilings), or −1. */
	static double ceiling(Vec3 pos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return -1.0D;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dy = 3; dy <= 32; dy++) {
			cursor.set(pos.x, pos.y + dy, pos.z);
			if (!minecraft.level.getBlockState(cursor).isAir()) {
				return pos.y + dy;
			}
		}
		return -1.0D;
	}

	// ================================================================ A. Tribulasi Langit

	static Trib trib(Scene s) {
		if (!(s.data instanceof Trib)) {
			Trib info = new Trib();
			info.total = 3;
			s.data = info;
		}
		return (Trib) s.data;
	}

	private static int boltColor(Scene s, Trib info, int index) {
		if (info.variant == 1) {
			return EpicColors.BOLT_NETHER;
		}
		if (info.variant == 2) {
			return EpicColors.BOLT_END;
		}
		if (s.s >= 6 && s.s <= 8) {
			return EpicColors.immortalStrike(index);
		}
		return EpicColors.BOLT_OVERWORLD;
	}

	private static int coreColor(Trib info) {
		return info.variant == 1 ? EpicColors.CORE_NETHER : EpicColors.CORE_WHITE;
	}

	static void tribulation(Scene s, int beat, int delay) {
		Trib info = trib(s);
		switch (beat) {
			case EpicIds.TRIB_OMEN -> omen(s, info, delay);
			case EpicIds.TRIB_PRE -> pre(s, info, delay);
			case EpicIds.TRIB_STRIKE -> strike(s, info, delay);
			case EpicIds.TRIB_HUSH -> hush(s, info, delay);
			case EpicIds.TRIB_END_OK -> success(s, info, delay);
			case EpicIds.TRIB_END_FAIL -> failure(s, info, delay);
			case EpicIds.TRIB_ABORT -> {
				VortexRenderer vortex = vortex(s);
				if (vortex != null) {
					s.at(delay, () -> vortex.dissolve(40));
				}
				s.at(delay, () -> {
					SkyDirector.release(40, s.priority);
					if (s.own) {
						MusicCue.release();
						EpicSound.restore();
					}
					EpicDirector.closeTelegraph();
				});
				s.end(delay + 60);
			}
			default -> {
			}
		}
	}

	static VortexRenderer vortex(Scene s) {
		Element element = s.get("vortex");
		return element instanceof VortexRenderer vortex ? vortex : null;
	}

	/** Base of the cloud and the eye position of the scene's vortex (or above the subject). */
	private static Vec3 eye(Scene s, Trib info) {
		VortexRenderer vortex = vortex(s);
		if (vortex != null) {
			return new Vec3(vortex.centerX(), vortex.baseY(), vortex.centerZ());
		}
		return s.pos.add(0.0D, Math.max(24.0F, info.height), 0.0D);
	}

	/** A1: Pertanda Langit (12 s). */
	private static void omen(Scene s, Trib info, int delay) {
		info.variant = EpicIds.nibble(s.a, 0);
		info.realm = EpicIds.nibble(s.a, 1);
		info.layer = EpicIds.nibble(s.a, 2);
		info.total = Math.max(1, s.b);
		info.disciple = s.moment == EpicIds.DISCIPLE_BT;
		int i = idx(s.s);
		float scaleR = info.disciple ? 0.35F : 1.0F;
		float scaleH = info.disciple ? 0.6F : 1.0F;
		info.radius = Math.max(48.0F, R_V[i]) * scaleR * Math.max(0.5F, EpicSettings.vfx());
		info.height = Math.max(32.0F, CLOUD_H[i]) * scaleH;
		info.eye = Math.max(5.0F, EYE[i]) * (info.disciple ? 0.6F : 1.0F);
		float dark = DARK[i];
		Vec3 feet = s.pos;
		// Stillness: white ash drifting inward; protector waves add red sparks circling at r 12 (a hint).
		emit(s, delay, 240, (tick, self) -> {
			Vec3 at = s.feet(0.0F);
			EpicParticles.converge(at.add(0.0D, 1.0D, 0.0D), 24.0F, n(s, tier() == 0 ? 2 : tier() == 1 ? 6 : 10, 1), ParticleTypes.WHITE_ASH, 0.25F, false);
			if (s.has(EpicIds.FLAG_PROTECTORS) && tick % 3 == 0) {
				double angle = tick * 0.2D;
				EpicParticles.add(EpicParticles.dust(0xFF3030, 1.2F), at.x + Math.cos(angle) * 12.0D, at.y + 1.0D, at.z + Math.sin(angle) * 12.0D, 0.0D, 0.02D, 0.0D);
			}
		});
		SkyDirector.Grade grade = info.variant == 1 ? SkyDirector.Grade.SK_NETHER : info.variant == 2 ? SkyDirector.Grade.SK_VOID : SkyDirector.Grade.SK_TRIB;
		sky(s, delay, grade, 0.4F, dark, 80);
		sky(s, delay + 80, grade, 0.6F, dark, 60);
		sky(s, delay + 140, grade, 1.0F, dark, 80);
		if (s.own) {
			s.at(delay, () -> MusicCue.duck(40));
		}
		MusicCue.Cue cue = info.variant == 1 ? MusicCue.Cue.M_NETHER : info.variant == 2 ? MusicCue.Cue.M_END
				: s.s >= 6 ? MusicCue.Cue.M_STORM : MusicCue.Cue.M_OMEN;
		music(s, delay + 20, cue, 12);
		// Camera: S CRANE_UP to look at the eye, then a 1 s hold; P adds an orbit while the arms gather.
		shot(s, delay + 10, () -> Shots.craneUp(2.5F, 1.9F, 9.0F, 16.0F, 5.0F, -55.0F, 5.0F), CinematicDirector.KIND_TRIBULATION, true);
		shotPenuh(s, delay + 120, () -> Shots.orbit(6.0F, 1.4F, 60.0F, 3.0F), CinematicDirector.KIND_TRIBULATION);
		// Card.
		Component title;
		Component sub;
		String seal;
		if (info.variant == 1) {
			title = t("trib_nether.title");
			sub = t("trib.sub", realmName(info.realm), info.total);
			seal = "火";
		} else if (info.variant == 2) {
			title = t("trib_void.title");
			sub = t("trib.sub", realmName(info.realm), info.total);
			seal = "虚";
		} else if (info.realm == Realm.TRUE_IMMORTAL.ordinal()) {
			title = t("trib_true.title");
			sub = t("trib_true.sub", info.total);
			seal = "劫";
		} else if (info.realm == Realm.IMMORTAL_ASCENSION.ordinal()) {
			title = t("trib.title");
			sub = t("trib_ia.sub", Math.max(1, info.layer), info.total);
			seal = "劫";
		} else {
			title = t("trib.title");
			sub = t("trib.sub", realmName(info.realm), info.total);
			seal = "劫";
		}
		if (!info.disciple) {
			full(s, delay + 50, title, () -> sub, seal, SEAL_RED, 70, null);
		}
		if (info.variant == 1) {
			// Nether: no sky geometry; a flame ring under the ceiling and four fire tornadoes.
			double ceiling = ceiling(feet);
			double ringY = ceiling > 0.0D ? Math.min(feet.y + 14.0D, ceiling - 2.0D) : feet.y + 14.0D;
			s.add("netherRing", new RingRenderer(delay + 10, 20 * 60 * 3, new Vec3(feet.x, ringY, feet.z), 8.0F, 16.0F, 3.0F, 120.0F, 0xFF6A1A, 0.7F));
			emit(s, delay + 20, 20 * 60 * 3, (tick, self) -> {
				if (tick % 2 != 0) {
					return;
				}
				for (int k = 0; k < 4; k++) {
					double angle = Math.PI * 0.5D * k + 0.4D;
					Vec3 base = s.pos.add(Math.cos(angle) * 10.0D, 0.0D, Math.sin(angle) * 10.0D);
					EpicParticles.helix(base, 12.0F, 1.0F, n(s, 3, 1), tick * 0.3D, k % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME);
				}
			});
			return;
		}
		int cloud = info.variant == 2 ? 0x3A1F5C : info.disciple ? 0x3A3C4A : EpicColors.TRIB_CLOUD;
		int glow = info.variant == 2 ? EpicColors.BOLT_END : 0xC9B8FF;
		VortexRenderer vortex = new VortexRenderer(delay + 10, 20 * 60 * 4, feet.x, feet.y + info.height, feet.z, info.radius, info.eye, cloud, glow, s.seed);
		vortex.grow(0.15F, 0.5F, 230.0F).spin(2.0F).sevenRim(s.s >= 6 && s.s <= 8);
		s.add("vortex", vortex);
		s.at(delay + 220, () -> vortex.pulse(1.5F));
		if (info.variant == 2) {
			emit(s, delay + 10, 20 * 60 * 3, (tick, self) -> {
				if (tick % 3 == 0) {
					EpicParticles.rain(s.pos, 16.0F, 20.0F, n(s, 4, 1), 0xC08BFF);
					EpicParticles.converge(s.pos.add(0.0D, info.height * 0.5D, 0.0D), 30.0F, n(s, 3, 1), ParticleTypes.REVERSE_PORTAL, 0.0F, true);
				}
			});
		}
		if (s.s >= 5 && !lite(s)) {
			s.add("shafts", new RaysRenderer(delay + 120, 220, new Vec3(feet.x, feet.y + info.height, feet.z), info.height, 4, 18.0F, 6.0F, 0x101018, 0.15F,
					false).shafts());
		}
		if (s.s >= 6 && !lite(s)) {
			float yaw = s.yaw();
			Vec3 dir = forward(yaw);
			double distance = Math.min(info.radius * 0.3D, 90.0D);
			Vec3 at = feet.add(dir.scale(distance)).add(0.0D, info.height - 8.0D, 0.0D);
			GiantFigureRenderer deity = new GiantFigureRenderer(delay + 160, 20 * 60 * 4, at, yaw + 180.0F, 24.0F, EpicRenderTypes.THUNDER_AVATAR, 0xE8E0FF, 0.3F);
			s.add("deity", deity);
		}
		if (s.s >= 8 && !lite(s)) {
			int silhouettes = Math.min(EpicBudget.silhouetteCap(), tier() >= 2 ? 12 : 4);
			emit(s, delay + 160, 20 * 60 * 3, (tick, self) -> {
				if (tick % 2 != 0) {
					return;
				}
				double radius = Math.min(info.radius * 0.25D, 80.0D);
				for (int k = 0; k < silhouettes; k++) {
					double angle = Math.PI * 2.0D * k / silhouettes + tick * 0.002D;
					Vec3 base = s.pos.add(Math.cos(angle) * radius, info.height - 6.0D, Math.sin(angle) * radius);
					Vec3 side = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
					EpicParticles.outline(PointClouds.deity(), base, side, 12.0D, 3, EpicParticles.dust(0xD8D0FF, 2.5F), true);
				}
			});
		}
		if (s.s >= 9 && !lite(s)) {
			Vec3 gateAt = new Vec3(feet.x, feet.y + info.height + 20.0D, feet.z);
			s.add("gateHint", new GateRenderer(delay + 60, 20 * 60 * 4, gateAt, feet.add(0.0D, 1.6D, 0.0D), 48.0F, 72.0F, GOLD, 0.2F));
		}
	}

	/** A2 / A3: the telegraph 30 ticks before a strike lands. */
	private static void pre(Scene s, Trib info, int delay) {
		boolean finalStrike = s.a >= info.total;
		if (s.own) {
			EpicDirector.telegraph(delay + 32);
		}
		Vec3 eye = eye(s, info);
		VortexRenderer vortex = vortex(s);
		if (vortex != null && info.variant != 1) {
			s.add(new BoltRenderer(delay, 30, eye, s.pos, 0.05F, boltColor(s, info, s.a)));
			float spin = vortex.spinTarget();
			s.at(delay, () -> vortex.spin(spin * 1.5F));
			s.at(delay + 10, () -> vortex.pulse(1.25F));
			s.at(delay + 22, () -> vortex.pulse(1.25F));
			s.at(delay + 20, () -> {
				vortex.eye(finalStrike ? 2.0F : 0.7F);
				vortex.converge();
			});
		}
		if (s.has(EpicIds.FLAG_LETHAL_NEXT)) {
			edge(s, delay, 0xB01010, 0.45F, 30);
		}
		if (s.own) {
			s.at(delay + 15, () -> EpicSound.duck(14));
		}
		snap(s, delay + 20, finalStrike ? -8.0F : -4.0F, 10, 0);
		if (finalStrike && tier() >= 1) {
			// Eight short dim ground arcs at r 8-14.
			for (int k = 0; k < 8; k++) {
				Vec3 a = around(s, s.pos, 8.0F, 14.0F);
				Vec3 b = a.add((s.random().nextDouble() - 0.5D) * 3.0D, 0.0D, (s.random().nextDouble() - 0.5D) * 3.0D);
				s.add(new BoltRenderer(delay + 20, 6, a.add(0.0D, 0.3D, 0.0D), b.add(0.0D, 0.1D, 0.0D), null, 0.08F, boltColor(s, info, s.a), 0xFFFFFF,
						s.seed + k, 0, true));
			}
		}
	}

	/** A2 / A3: the strike. */
	private static void strike(Scene s, Trib info, int delay) {
		int index = Math.max(1, s.a);
		boolean finalStrike = s.has(EpicIds.FLAG_FINAL) || index >= info.total;
		int i = idx(s.s);
		Vec3 eye = eye(s, info);
		Vec3 feet = s.pos;
		int color = boltColor(s, info, index);
		float w = BOLT_W[i] * (info.disciple ? 0.8F : 1.0F) * (finalStrike ? 2.5F : 1.0F);
		int life = 9;
		if (s.own) {
			s.at(delay, () -> {
				ShotGuard.strikeShown();
				EpicDirector.closeTelegraph();
			});
		}
		VortexRenderer vortex = vortex(s);
		if (vortex != null) {
			float spin = vortex.spinTarget();
			s.at(delay, () -> {
				vortex.eye(1.0F);
				vortex.spin(Math.max(2.0F, spin / 1.5F) + (finalStrike ? 0.0F : 0.6F));
			});
		}
		Vec3 top = info.variant == 1 ? feet.add(0.0D, Math.max(8.0D, ceiling(feet) > 0 ? ceiling(feet) - feet.y - 1.0D : 14.0D), 0.0D) : eye;
		s.add(new BoltRenderer(delay, life, top, feet, null, w, color, coreColor(info), s.seed + index * 7, branches(s.s), false));
		float ringTo = finalStrike ? 10.0F + s.s : 4.0F + s.s / 2.0F;
		RingRenderer ring = new RingRenderer(delay, finalStrike ? 30 : 16, feet, finalStrike ? 6.0F : 1.0F, ringTo, 1.5F, 10.0F, color, 0.75F);
		if (finalStrike) {
			ring.crown(n(s, tier() == 0 ? 10 : tier() == 1 ? 40 : 60, 4));
		}
		s.add(ring);
		flash(s, delay, color, finalStrike ? 0.3F : 0.15F, finalStrike ? 5 : 3, finalStrike ? 3 : 2);
		snap(s, delay, 6.0F, 4, 0);
		float shakeIntensity = finalStrike ? SHAKE[i] : Math.min(0.55F, 0.25F + 0.03F * s.s);
		shake(s, delay, shakeIntensity, finalStrike ? 14 : 10);
		// Sympathetic visual bolts (thin, never damage, inside the bolt budget).
		int sympathetic = lite(s) ? 0 : s.s >= 9 ? 9 : s.s >= 8 ? 6 : s.s >= 6 ? 4 : s.s >= 4 ? 2 : 0;
		float far = s.s >= 6 ? 40.0F : 24.0F;
		for (int k = 0; k < sympathetic; k++) {
			Vec3 ground = around(s, feet, s.s >= 6 ? 12.0F : 10.0F, far);
			Vec3 sky = new Vec3(ground.x, eye.y, ground.z);
			s.add(new BoltRenderer(delay + 1 + k % 3, 7, sky, ground, null, w * 0.5F, color, coreColor(info), s.seed + 97 * k + index, 0, true));
		}
		// Afterglow: a scorch ring of dust under the feet and smoke.
		emit(s, delay + 6, 30, (tick, self) -> {
			if (tick % 3 == 0) {
				EpicParticles.ring(s.pos, 1.2F, n(s, 10, 3), EpicParticles.dust(0x2A2018, 1.0F), 0.0F);
				EpicParticles.column(s.pos, 1.5F, 1.0F, n(s, 3, 1), ParticleTypes.SMOKE, 0.03F);
			}
		});
		if (finalStrike) {
			// Convergence of thin bolts from the eye rim: three, the Kenaikan Abadi seven, the Dewa Abadi nine.
			int[] colors;
			if (s.s >= 9) {
				colors = EpicColors.NINE;
			} else if (s.s >= 6) {
				colors = EpicColors.IMMORTAL_STRIKE;
			} else {
				colors = new int[]{color, color, color};
			}
			int count = tier() >= 2 ? colors.length : tier() == 1 ? Math.min(4, colors.length) : 0;
			float rim = vortex != null ? vortex.eyeRadius() * 2.0F : 10.0F;
			for (int k = 0; k < count; k++) {
				double angle = Math.PI * 2.0D * k / Math.max(1, count);
				Vec3 from = eye.add(Math.cos(angle) * rim, 0.0D, Math.sin(angle) * rim);
				s.add(new BoltRenderer(delay, 10, from, feet.add(0.0D, 1.0D, 0.0D), null, BOLT_W[i] * 0.5F, colors[k], 0xFFFFFF, s.seed + 1000 + k, 0, true));
			}
			s.add(new PillarRenderer(delay, 10, feet, 64.0F, 0xFFFFFF, 1.5F * BOLT_W[i] * 2.5F));
			hitStop(s, delay);
			chroma(s, delay, 6);
			Element deity = s.get("deity");
			if (deity instanceof GiantFigureRenderer figure) {
				s.at(delay - 2 < 0 ? 0 : delay - 2, () -> figure.arm(2.4F));
			}
			// Bullet time: S+P orbit (SAFE; the damage is resolved), Gerak lambat 1.5 s.
			shot(s, delay + 3, () -> Shots.orbit(7.0F, 1.6F, 36.0F, 1.5F), CinematicDirector.KIND_TRIBULATION, true);
			slowmo(s, delay + 3, 30);
			if (vortex != null) {
				s.at(delay, () -> vortex.arcs(false));
			}
		} else if (s.own && index % 3 == 0 && !s.has(EpicIds.FLAG_LETHAL_NEXT)) {
			// Penuh: a low hero cut after every third strike, ending before the next telegraph.
			float r = Math.max(4.0F, GLOW[i] + 1.5F);
			s.at(delay + 2, () -> {
				if (CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
					CinematicDirector.shoot(s, Shots.lowHero(r, 0.4F, 25.0F, 0.8F, 0.9F).asDanger(), CinematicDirector.KIND_TRIBULATION, s.priority, false);
				}
			});
		}
	}

	/** A3: the hush before the final strike. */
	private static void hush(Scene s, Trib info, int delay) {
		VortexRenderer vortex = vortex(s);
		if (vortex != null) {
			s.at(delay, () -> vortex.arcs(false).spin(vortex.spinTarget() * 3.0F).sink(info.height * 0.2F));
		}
		banner(s, delay, t("final_strike"));
		if (s.own) {
			s.at(delay, () -> {
				MusicCue.release();
				EpicSound.duck(40);
			});
		}
	}

	/** A4: Langit Terbelah (with the realm layer B2-B7 and the B8 overlay). */
	private static void success(Scene s, Trib info, int delay) {
		info.ended = true;
		int i = idx(s.s);
		Vec3 feet = s.pos;
		Vec3 eye = eye(s, info);
		float glow = GLOW[i] * (info.disciple ? 0.6F : 1.0F);
		VortexRenderer vortex = vortex(s);
		boolean l9 = info.realm == Realm.IMMORTAL_ASCENSION.ordinal() && info.layer >= 9;
		if (vortex != null) {
			s.at(delay, () -> {
				vortex.dissolve(l9 ? 20 : 60);
				vortex.eye(Math.max(1.0F, info.radius * 0.3F / Math.max(1.0F, info.eye)));
			});
		}
		Element shafts = s.get("shafts");
		if (shafts != null) {
			s.at(delay, shafts::stop);
		}
		Element deity = s.get("deity");
		if (deity != null) {
			s.at(delay + 40, deity::stop);
		}
		Element hint = s.get("gateHint");
		if (hint != null) {
			s.at(delay + 20, hint::stop);
		}
		float height = (float) (eye.y - feet.y) + 8.0F;
		PillarRenderer pillar = new PillarRenderer(delay, 150, feet, height, GOLD, glow).descend(height, 30);
		s.add("pillar", pillar);
		s.add(new RaysRenderer(delay, 100, eye, (float) (eye.y - feet.y), 12, 22.0F, 3.0F + s.s * 0.5F, GOLD, 0.2F, false));
		sky(s, delay, SkyDirector.Grade.SK_GOLD, 0.8F, 0.0F, 40);
		skyRelease(s, delay + 40, 80, 80);
		if (!info.disciple) {
			shot(s, delay + 10, () -> Shots.lowHero(Math.max(5.0F, glow + 3.0F), 0.4F, 25.0F, 1.0F, 2.5F), CinematicDirector.KIND_TRIBULATION, true);
		}
		emit(s, delay + 10, 60, (tick, self) -> EpicParticles.rain(s.pos, 8.0F, 16.0F, n(s, tier() == 0 ? 10 : tier() == 1 ? 40 : 60, 4) / 4, GOLD));
		if (s.own) {
			s.at(delay + 10, () -> EpicSound.restore());
		}
		sting(s, delay + 10);
		boolean ia = info.realm == Realm.IMMORTAL_ASCENSION.ordinal();
		if (info.disciple) {
			return;
		}
		if (ia) {
			SceneCatalogPower.immortalDoor(s, info, delay, eye, feet);
		} else {
			shot(s, delay + 60, () -> Shots.pullback(Math.max(4.0F, glow + 1.5F), 18.0F, 0.4F, 10.0F, 3.0F), CinematicDirector.KIND_TRIBULATION, true);
		}
		// The realm layer and the card join from the BREAKTHROUGH SUCCESS (either order within 4 ticks).
		PendingRealm pending = PENDING.remove(s.subjectId);
		if (pending != null && EpicDirector.ticks() - pending.tick() <= 6) {
			realmLayer(s, info, pending.realm(), pending.quality(), pending.layer(), delay);
		} else {
			s.at(delay + 4, () -> {
				PendingRealm late = PENDING.remove(s.subjectId);
				if (late != null) {
					realmLayer(s, info, late.realm(), late.quality(), late.layer(), 0);
				} else if (info.realmShown < 0) {
					realmLayer(s, info, info.realm, -1, info.layer, 0);
				}
			});
		}
		s.end(delay + 20 * 12);
	}

	/** The realm layer of a passed tribulation: B2-B7, the card, the music and the Kualitas Surgawi overlay. */
	private static void realmLayer(Scene s, Trib info, int realm, int quality, int layer, int delay) {
		if (info.realmShown >= 0) {
			return;
		}
		info.realmShown = realm;
		info.quality = quality;
		boolean heavenly = quality == BreakthroughQuality.HEAVENLY.ordinal();
		boolean l9 = realm == Realm.IMMORTAL_ASCENSION.ordinal() && layer >= 9;
		music(s, delay + 10, heavenly || l9 ? MusicCue.Cue.M_GLORY : MusicCue.Cue.M_TRIUMPH, heavenly || l9 ? 18 : 14);
		Component title;
		Supplier<Component> sub;
		String seal = SEAL[idx(s.s)];
		Realm r = Realm.byOrdinal(realm);
		if (r == Realm.IMMORTAL_ASCENSION) {
			title = t("ia.title", Math.max(1, layer));
			sub = l9 ? () -> t("ia9.sub") : () -> t("ia.sub", ImmortalPerks.Boon.ofLayer(Math.max(1, layer)).displayName());
			seal = "仙";
		} else if (r == Realm.TRUE_IMMORTAL) {
			title = t("true_immortal.title");
			sub = () -> t("true_immortal.sub");
			seal = "天";
		} else {
			title = upper(realmName(realm));
			Component extra = r == Realm.DEITY_TRANSFORMATION ? t("dharma_unlocked") : r == Realm.DAO_UNION ? t("domain_unlocked") : null;
			sub = () -> {
				Component base = quality >= 0 ? t("trib_ok.sub", qualityName(quality)) : t("trib_ok.sub_plain");
				return extra == null ? base : Component.empty().append(base).append(" · ").append(extra);
			};
		}
		boolean perfect = quality == BreakthroughQuality.PERFECT.ordinal();
		full(s, delay + 20, title, sub, seal, SEAL_RED, 70, card -> {
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
		SceneCatalogPower.realmVisual(s, realm, quality, layer, delay);
	}

	/** A5: Langit Murka (→ C1). */
	private static void failure(Scene s, Trib info, int delay) {
		info.ended = true;
		EpicDirector.markFail(s.subjectId);
		VortexRenderer vortex = vortex(s);
		if (vortex != null) {
			s.at(delay + 4, () -> vortex.collapse(40));
		}
		Element deity = s.get("deity");
		if (deity != null) {
			s.at(delay, deity::stop);
		}
		emit(s, delay + 4, 6, (tick, self) -> EpicParticles.burst(s.pos.add(0.0D, 1.5D, 0.0D), n(s, 12, 4), 0.25F, EpicParticles.dust(0xC01818, 1.3F), false));
		grey(s, delay, 0.2F, 40);
		sky(s, delay, SkyDirector.Grade.SK_ASH_RED, 0.7F, 0.4F, 20);
		skyRelease(s, delay + 40, 60, 80);
		shot(s, delay + 6, () -> Shots.still(4.0F, 3.5F, 1.2F, 2.0F).asResult(), CinematicDirector.KIND_TRIBULATION, true);
		edge(s, delay + 6, 0xB01010, 0.5F, 80);
		music(s, delay + 6, MusicCue.Cue.M_SORROW, 10);
		if (s.own) {
			s.at(delay, () -> EpicSound.restore());
		}
		info.hardcore |= s.has(EpicIds.FLAG_HARDCORE);
		if (!info.disciple) {
			// The card is decided when it shows: a Hardcore drop shows "RANAH RUNTUH".
			failCard(s, info, delay + 20);
		}
		s.end(delay + 20 * 8);
	}

	// ================================================================ B. Breakthroughs

	static void breakthrough(Scene s, int beat, int delay) {
		int realm = EpicIds.nibble(s.a, 0);
		int quality = EpicIds.nibble(s.a, 1);
		int kind = EpicIds.nibble(s.a, 2);
		int layer = EpicIds.nibble(s.a, 3);
		switch (beat) {
			case EpicIds.BT_BEGIN -> {
				if (kind == EpicIds.KIND_AWAKENING) {
					awakeningBegin(s, delay);
				} else if (kind == EpicIds.KIND_CHANCE && realm == Realm.FOUNDATION.ordinal()) {
					foundationBegin(s, delay);
				}
			}
			case EpicIds.BT_SUCCESS -> {
				if (kind == EpicIds.KIND_TRIBULATION) {
					joinTribulation(s, realm, quality, layer);
					s.end(20);
				} else if (kind == EpicIds.KIND_AWAKENING) {
					awakeningSuccess(s, delay);
				} else if (realm == Realm.FOUNDATION.ordinal()) {
					SceneCatalogPower.foundationPillars(s, quality, delay, false);
				} else {
					music(s, delay, MusicCue.Cue.M_TRIUMPH, 14);
					full(s, delay + 10, upper(realmName(realm)), () -> t("bt_ok.sub", qualityName(quality)), SEAL[idx(s.s)], SEAL_RED, 70, null);
					s.add(new PillarRenderer(delay, 120, s.pos, 32.0F, GOLD, GLOW[idx(s.s)]).rise(20));
					s.end(delay + 200);
				}
			}
			case EpicIds.BT_FAIL -> {
				Scene trib = EpicDirector.find(EpicIds.TRIBULATION, s.subjectId);
				if (trib != null && trib.data instanceof Trib info) {
					info.hardcore |= s.f >= 1.0F || s.has(EpicIds.FLAG_HARDCORE);
					s.end(10);
					return;
				}
				if (realm == Realm.FOUNDATION.ordinal() || kind == EpicIds.KIND_CHANCE) {
					SceneCatalogPower.foundationCollapse(s, delay);
				}
				s.end(delay + 120);
			}
			default -> {
			}
		}
	}

	private static void failCard(Scene trib, Trib info, int delay) {
		if (!trib.own) {
			return;
		}
		trib.at(delay, () -> {
			TitleCard.Card card;
			if (info.hardcore) {
				Component realm = ClientCultivation.data().realm().displayName();
				card = TitleCard.full(t("realm_shattered.title"), t("realm_shattered.sub", realm), "魔", SEAL_RED, 70).shatter();
			} else {
				int minutes = Math.max(1, ClientCultivation.data().deviationTicks() / 1200);
				card = TitleCard.full(t("deviation.title"), t("trib_fail.sub", minutes), "魔", SEAL_RED, 60);
			}
			card.suppressTitle(trib.has(EpicIds.FLAG_SUPPRESS_TITLE));
			TitleCard.show(card);
		});
	}

	private static void joinTribulation(Scene bt, int realm, int quality, int layer) {
		Scene trib = EpicDirector.find(EpicIds.TRIBULATION, bt.subjectId);
		if (trib != null && trib.data instanceof Trib info && info.ended) {
			realmLayer(trib, info, realm, quality, layer, 0);
			return;
		}
		PENDING.put(bt.subjectId, new PendingRealm(realm, quality, layer, EpicDirector.ticks()));
		if (PENDING.size() > 16) {
			PENDING.values().removeIf(p -> EpicDirector.ticks() - p.tick() > 40);
		}
	}

	/** B0: Kebangkitan (the existing 6 s). */
	private static void awakeningBegin(Scene s, int delay) {
		shot(s, delay + 10, () -> Shots.orbit(4.0F, 1.4F, 120.0F, 5.0F), CinematicDirector.KIND_BREAKTHROUGH, true);
		music(s, delay, MusicCue.Cue.M_GENTLE, 12);
	}

	private static void awakeningSuccess(Scene s, int delay) {
		Entity subject = s.subject();
		int root = subject == null ? JADE : ClientCultivation.visual(subject).rootColor();
		s.add(new PillarRenderer(delay, 80, s.pos, 12.0F, root, 0.6F).rise(10));
		full(s, delay + 10, upper(realmName(Realm.QI_REFINING.ordinal())), () -> t("awakening.sub"), "气", SEAL_RED, 70, null);
		sting(s, delay + 10);
		s.end(delay + 160);
	}

	/** B1: the 5 s gathering of a Foundation. */
	private static void foundationBegin(Scene s, int delay) {
		s.add("rune", new RingRenderer(delay, 140, s.pos, 0.0F, 4.0F, 0.0F, 20.0F, JADE, 0.8F).rune(1.2F));
		sky(s, delay, SkyDirector.Grade.SK_JADE, 0.2F, 0.0F, 40);
		shot(s, delay + 40, () -> Shots.push(6.0F, 3.5F, 1.4F, 3.0F), CinematicDirector.KIND_BREAKTHROUGH, true);
	}

	// ================================================================ B7. Pengakuan Langit

	static void ascension(Scene s, int beat, int delay) {
		Vec3 feet = s.pos;
		switch (beat) {
			case EpicIds.ASC_DARKEN -> {
				shot(s, delay, () -> Shots.push(12.0F, 9.0F, 0.5F, 4.0F).asCeremony(), CinematicDirector.KIND_ASCENSION, true);
				sky(s, delay, SkyDirector.Grade.SK_STARS, 1.0F, 0.6F, 60);
				slowmo(s, delay, 20);
				if (s.own) {
					s.at(delay, () -> MusicCue.duck(80));
				}
				VortexRenderer vortex = new VortexRenderer(delay, 20 * 40, feet.x, feet.y + 112.0D, feet.z, 1024.0F, 24.0F, 0x6A5420, GOLD, s.seed);
				vortex.grow(0.05F, 0.4F, 400.0F).spin(1.5F).arcs(false);
				s.add("vortex", vortex);
			}
			case EpicIds.ASC_PILLAR -> {
				PillarRenderer pillar = new PillarRenderer(delay, 20 * 30, feet, 420.0F, GOLD, 3.5F).descend(400.0F, 60.0F);
				s.add("pillar", pillar);
				shot(s, delay + 0, () -> Shots.craneUp(6.0F, 0.5F, 6.0F, 0.5F, -70.0F, -10.0F, 3.0F), CinematicDirector.KIND_ASCENSION, true);
				s.add(new RingRenderer(delay + 60, 60, feet, 4.0F, 48.0F, 6.0F, 30.0F, GOLD, 0.6F).crown(n(s, 40, 8)));
				flash(s, delay + 60, GOLD, 0.3F, 6, 0);
				shake(s, delay + 60, 0.6F, 14);
				emit(s, delay + 60, 80, (tick, self) -> EpicParticles.column(s.pos, 20.0F, 3.0F, n(s, 6, 1), EpicParticles.dust(PALE_GOLD, 1.4F), 0.12F));
				sky(s, delay + 60, SkyDirector.Grade.SK_GOLD, 0.8F, 0.0F, 80);
			}
			case EpicIds.ASC_HEAVENS -> {
				VortexRenderer vortex = vortex(s);
				if (vortex != null) {
					s.at(delay, () -> vortex.eye(1024.0F * 0.4F / 24.0F));
				}
				Vec3 gateAt = feet.add(0.0D, 112.0D + 16.0D, 0.0D);
				s.add("gate", new GateRenderer(delay, 20 * 25, gateAt, feet.add(0.0D, 1.6D, 0.0D), 64.0F, 96.0F, GOLD, 0.8F).openAt(0.0F).open(1.0F, 60));
				s.add(new RaysRenderer(delay, 20 * 20, feet.add(0.0D, 110.0D, 0.0D), 110.0F, 12, 20.0F, 6.0F, GOLD, 0.22F, false));
				emit(s, delay, 200, (tick, self) -> {
					if (tick % 2 == 0) {
						EpicParticles.rain(s.pos, 48.0F, 30.0F, n(s, 6, 1), GOLD);
						EpicParticles.rain(s.pos, 48.0F, 30.0F, n(s, 3, 1), 0xFFB7D5);
					}
				});
				emit(s, delay, 200, (tick, self) -> {
					if (tick % 3 != 0 || lite(s)) {
						return;
					}
					for (int k = 0; k < 6; k++) {
						double angle = Math.PI * 2.0D * k / 6.0D + tick * 0.01D;
						Vec3 base = s.pos.add(Math.cos(angle) * 60.0D, 40.0D + (k % 2) * 6.0D, Math.sin(angle) * 60.0D);
						Vec3 side = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
						EpicParticles.outline(PointClouds.crane(), base, side, 6.0D, 3, EpicParticles.dust(0xFFFFFF, 2.0F), true);
					}
				});
				sky(s, delay, SkyDirector.Grade.SK_GOLD, 1.0F, 0.0F, 40);
				shot(s, delay, () -> Shots.skypan(0.0F, -60.0F, 0.0F, 2.5F).asCeremony(), CinematicDirector.KIND_ASCENSION, true);
				shot(s, delay + 50, () -> Shots.orbit(10.0F, 6.0F, 36.0F, 1.5F).lookAt(feet.add(0.0D, 60.0D, 0.0D)).asCeremony(), CinematicDirector.KIND_ASCENSION,
						true);
				music(s, delay, MusicCue.Cue.M_ASCEND, 35);
			}
			case EpicIds.ASC_VOICE -> {
				glyph(s, delay, feet.add(0.0D, 112.0D + 16.0D + 96.0D * 0.5D, 0.0D), "天", GOLD, 40.0F, 60);
				full(s, delay, t("acknowledge.title"), () -> Component.translatable("kultivator.ascension.voice.subtitle"), "天", SEAL_RED, 70, null);
				shotPenuh(s, delay + 20, () -> Shots.pullback(4.0F, 24.0F, 2.0F, 16.0F, 4.0F).asCeremony(), CinematicDirector.KIND_ASCENSION);
			}
			case EpicIds.ASC_FINALE -> {
				s.add(new RingRenderer(delay, 80, feet, 8.0F, 64.0F, 8.0F, 40.0F, 0xFFFFFF, 0.5F));
				s.add(new AuroraRenderer(delay, 80, feet, 2.0F + 3.5F, 40.0F * 1.9F));
				Element pillar = s.get("pillar");
				if (pillar != null) {
					s.at(delay + 20, pillar::stop);
				}
				Element gate = s.get("gate");
				if (gate != null) {
					s.at(delay + 60, gate::stop);
				}
				VortexRenderer vortex = vortex(s);
				if (vortex != null) {
					s.at(delay, () -> vortex.dissolve(80));
				}
				skyRelease(s, delay, 0, 120);
				full(s, delay + 10, t("true_immortal_named.title", nameOf(s, "you")), () -> t("true_immortal_named.sub"), "天", SEAL_RED, 80, null);
				if (s.own) {
					s.at(delay + 100, () -> MusicCue.release(MusicCue.Cue.M_ASCEND));
				}
				s.end(delay + 20 * 8);
			}
			case EpicIds.ASC_STOP -> {
				s.at(delay, () -> {
					SkyDirector.release(40, s.priority);
					if (s.own) {
						MusicCue.release();
					}
				});
				s.end(delay + 40);
			}
			default -> {
			}
		}
	}

	// ================================================================ C. Crises

	/** C1: Penyimpangan Qi (client-derived; chained after A5 or a heart-demon loss: no second card). */
	static void deviation(Scene s, boolean chained) {
		Vec3 feet = s.feet(0.0F);
		EpicParticles.burst(feet.add(0.0D, 1.2D, 0.0D), n(s, 30, 8), 0.3F, EpicParticles.dust(0x200808, 1.5F), false);
		EpicParticles.burst(feet.add(0.0D, 1.2D, 0.0D), n(s, 20, 6), 0.25F, EpicParticles.dust(0xC01818, 1.3F), false);
		edge(s, 0, 0xB01010, 0.5F, 60);
		sky(s, 0, SkyDirector.Grade.SK_ASH_RED, 0.6F, 0.3F, 20);
		skyRelease(s, 20, 60, 60);
		ui(s, 0, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.6F);
		shot(s, 10, () -> Shots.still(4.0F, 3.5F, 1.2F, 2.0F).asResult(), CinematicDirector.KIND_EXTRA, true);
		Entity subject = s.subject();
		for (int k = 0; k < 8; k++) {
			double angle = Math.PI * 2.0D * k / 8.0D;
			Vec3 a = feet.add(Math.cos(angle) * 0.8D, 0.3D + (k % 4) * 0.4D, Math.sin(angle) * 0.8D);
			Vec3 b = a.add(Math.cos(angle) * 1.4D, 0.6D, Math.sin(angle) * 1.4D);
			TrailRenderer seam = new TrailRenderer(10 + k, 60, a, b, 0.0F, 0.12F, 0x8A1010, 0.9F, true, s.seed + k);
			if (subject != null) {
				seam.follow(subject.getId(), (float) (a.y - feet.y), -1, 0.0F);
			}
			s.add(seam);
		}
		if (!chained) {
			int minutes = s.own ? Math.max(1, ClientCultivation.data().deviationTicks() / 1200) : 1;
			full(s, 10, t("deviation.title"), () -> t("deviation.sub", minutes), "魔", SEAL_RED, 60, null);
		}
		s.end(100);
	}

	/** C2: Ujian Iblis Hati. */
	static void heartDemon(Scene s, int beat, int delay) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity viewer = minecraft.level == null ? null : minecraft.level.getEntity(s.viewerId);
		Vec3 feet = viewer != null ? viewer.position() : s.pos;
		switch (beat) {
			case EpicIds.HD_WARNING -> {
				// The warning is private (the 6c warning Fx is sendTo): bystanders get nothing.
				if (s.own) {
					emit(s, delay, 40, (tick, self) -> EpicParticles.column(s.feet(0.0F), 2.0F, 0.6F, n(s, 4, 1), EpicParticles.dust(0x050505, 1.2F), 0.06F));
					edge(s, delay, 0x050505, 0.55F, 40);
					sky(s, delay, SkyDirector.Grade.SK_ASH, 0.3F, 0.2F, 20);
				}
			}
			case EpicIds.HD_APPEAR -> {
				Vec3 ahead = viewer != null ? feet.add(forward(viewer.getYRot()).scale(3.0D)) : s.pos;
				Vec3 dir = viewer != null ? forward(viewer.getYRot()) : new Vec3(0.0D, 0.0D, 1.0D);
				Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
				Vec3 bl = ahead.add(side.scale(-1.0D));
				Vec3 br = ahead.add(side.scale(1.0D));
				Vec3 tl = bl.add(0.0D, 3.0D, 0.0D);
				Vec3 tr = br.add(0.0D, 3.0D, 0.0D);
				s.add(new TrailRenderer(delay, 12, bl, br, 0.0F, 0.08F, 0x202028, 0.9F, false, s.seed));
				s.add(new TrailRenderer(delay, 12, br, tr, 0.0F, 0.08F, 0x202028, 0.9F, false, s.seed + 1));
				s.add(new TrailRenderer(delay, 12, tr, tl, 0.0F, 0.08F, 0x202028, 0.9F, false, s.seed + 2));
				s.add(new TrailRenderer(delay, 12, tl, bl, 0.0F, 0.08F, 0x202028, 0.9F, false, s.seed + 3));
				s.add(new TrailRenderer(delay + 4, 8, ahead.add(0.0D, 0.4D, 0.0D), ahead.add(0.0D, 2.7D, 0.0D), 0.0F, 0.06F, 0xD0D8FF, 0.9F, true, s.seed + 4));
				s.add(new TrailRenderer(delay + 6, 6, bl.add(0.0D, 1.5D, 0.0D), tr.add(0.0D, -0.5D, 0.0D), 0.0F, 0.06F, 0xD0D8FF, 0.9F, true, s.seed + 5));
				s.at(delay + 10, () -> {
					if (!lite(s) && !s.has(EpicIds.FLAG_LOW_LOAD)) {
						EpicDisplays.shards(ahead.add(0.0D, 1.5D, 0.0D), Math.min(8, EpicBudget.displayCap()), 0.18F);
					}
					EpicParticles.burst(ahead.add(0.0D, 1.5D, 0.0D), n(s, 20, 6), 0.2F, ParticleTypes.SMOKE, false);
				});
				emit(s, delay + 10, 30, (tick, self) -> {
					if (tick % 5 == 0) {
						EpicParticles.add(EpicParticles.dust(0xFF2020, 0.8F), ahead.x + side.x * 0.2D, ahead.y + 1.7D, ahead.z + side.z * 0.2D, 0.0D, 0.0D, 0.0D);
						EpicParticles.add(EpicParticles.dust(0xFF2020, 0.8F), ahead.x - side.x * 0.2D, ahead.y + 1.7D, ahead.z - side.z * 0.2D, 0.0D, 0.0D, 0.0D);
					}
				});
				sky(s, delay, SkyDirector.Grade.SK_ASH, 0.7F, 0.4F, 20);
				music(s, delay, MusicCue.Cue.M_HEART, 10);
				if (s.own) {
					s.at(delay, () -> {
						if (CinematicDirector.mode() == EpicSettings.Camera.PENUH) {
							CinematicDirector.roll(3.0F, 40);
						}
					});
				}
				full(s, delay + 10, t("heart_demon.title"), () -> t("heart_demon.sub"), "心", SEAL_RED, 70, card -> card.safeBeat(false));
			}
			case EpicIds.HD_WON -> {
				Vec3 at = s.pos.add(0.0D, 1.2D, 0.0D);
				hitStop(s, delay);
				slowmo(s, delay + 3, 20);
				shot(s, delay + 3, () -> Shots.orbit(5.0F, 1.5F, 45.0F, 2.0F), CinematicDirector.KIND_HEART_DEMON, true);
				s.at(delay, () -> {
					if (!lite(s) && !s.has(EpicIds.FLAG_LOW_LOAD)) {
						EpicDisplays.shards(at, Math.min(8, EpicBudget.displayCap()), 0.15F);
					}
					EpicParticles.burst(at, n(s, 30, 8), 0.2F, EpicParticles.dust(0x050505, 1.4F), false);
				});
				s.add(new PillarRenderer(delay + 5, 60, feet, 24.0F, GOLD, 0.3F).rise(10));
				skyRelease(s, delay, 0, 40);
				full(s, delay + 20, t("steadfast.title"), null, "心", SEAL_RED, 60, null);
				s.end(delay + 120);
			}
			case EpicIds.HD_LOST -> {
				EpicDirector.markFail(s.viewerId);
				s.add(new TrailRenderer(delay, 20, s.pos.add(0.0D, 1.2D, 0.0D), feet.add(0.0D, 1.2D, 0.0D), 0.5F, 0.35F, 0x050505, 0.9F, false, s.seed)
						.travel(10).tail(6));
				skyRelease(s, delay + 20, 0, 40);
				s.end(delay + 60);
			}
			default -> {
			}
		}
	}

	private SceneCatalog() {
	}
}
