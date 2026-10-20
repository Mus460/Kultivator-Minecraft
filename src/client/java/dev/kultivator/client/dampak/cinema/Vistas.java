package dev.kultivator.client.dampak.cinema;

import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.fx.EpicDisplays;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.epic.fx.PointClouds;
import dev.kultivator.client.epic.render.AuroraRenderer;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import dev.kultivator.client.epic.render.EpicRenderer;
import dev.kultivator.client.epic.render.GateRenderer;
import dev.kultivator.client.epic.render.GiantFigureRenderer;
import dev.kultivator.client.epic.render.RaysRenderer;
import dev.kultivator.client.epic.render.RingRenderer;
import dev.kultivator.client.epic.render.TrailRenderer;
import dev.kultivator.client.epic.render.VortexRenderer;
import dev.kultivator.client.fx.spec.AnimBridge;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.epic.EpicColors;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Pose;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The sky vistas of Perjalanan Langit's R2 "Puncak" (Phase 10, DESIGN 28.3.3): what the camera sees at the top of the
 * ride, built from the 7b renderers (sky-projected when they are beyond the fog) and one model prop of the technique's
 * own object charging in it. Own client only, life = R2 + 10 ticks; one or two large layers (rank 2-5). Minimal shows
 * only the sky grade and particles. Lore: the Southern Heavenly Gate and the Heavenly River are Journey to the West, the
 * Kun is Zhuangzi's "Free and Easy Wandering" [lore verify], the accretion disc and the bodhi canopy are the arts' own
 * sources; the rest is design.
 * <p>
 * Phase 10b (DESIGN 29.4.1) adds the four Titah vistas of Film Pusaka's F3 "Istana": ISTANA_LANGIT (the Lingxiao Hall
 * of the Celestial Court as setting, Journey to the West; the Heaven-Splitting Sword is lowered out of its gate),
 * LAUT_TIMUR (Journey to the West ch. 3: the East Sea Dragon King's treasury, the sea-fixing iron rising and growing),
 * BIDUK_UTARA (the seven stars of the Northern Dipper, Tianshu to Yaoguang, joined one by one) and KUA_E (Liezi, Tang
 * Wen: heaven sends the two sons of the Kua'e clan to carry the mountains away; drawing them as giants is design).
 */
public final class Vistas {

	private static final int GOLD = 0xFFD54A;
	private static final int WHITE_GOLD = 0xFFF6D8;

	private Vistas() {
	}

	/** The vista of a BY_DAO row: the vista of the first Dao. */
	static Dampak.Vista byDao(@Nullable Dao dao) {
		if (dao == null) {
			return Dampak.Vista.GERBANG_LANGIT;
		}
		return switch (dao) {
			case SWORD -> Dampak.Vista.GERBANG_LANGIT;
			case FIRE -> Dampak.Vista.MATAHARI_KAISAR;
			case ICE -> Dampak.Vista.GUNUNG_ES;
			case LIGHTNING -> Dampak.Vista.PUSARAN;
			case SPACE -> Dampak.Vista.CAKRAM_AKRESI;
			case LIFE -> Dampak.Vista.POHON_CAHAYA;
			case SHADOW -> Dampak.Vista.LAUT_UTARA;
			case BODY -> Dampak.Vista.SEMBILAN_LANGIT;
		};
	}

	/**
	 * Builds {@code vista} around {@code centre} (high in the sky beyond the aim) seen from {@code eye} (the R2 camera),
	 * living {@code life} ticks, in the row colour {@code rgb}. {@code object} is the technique's own model (the sword
	 * drawn, the sun core kindling), shown between the camera and the vista.
	 */
	public static void spawn(Scene s, Dampak.Vista vista, TechniqueSpec spec, Vec3 centre, Vec3 eye, int life, int rgb, @Nullable String object,
			float objectScale, int casterId) {
		Dampak.Vista v = vista == Dampak.Vista.BY_DAO ? byDao(spec.dao()) : vista;
		if (v == Dampak.Vista.NONE) {
			return;
		}
		int tier = EpicBudget.tierOrMinimal();
		int seed = s.seed;
		if (tier == 0) {
			// Minimal: the sky grade (set by the ride) and a drift of motes only
			s.add(dev.kultivator.client.epic.Element.every(0, life, (tick, self) -> {
				if (tick % 3 == 0) {
					EpicParticles.rain(eye.add(0.0D, 6.0D, 0.0D), 10.0F, 6.0F, 2, rgb);
				}
			}));
			return;
		}
		switch (v) {
			case GERBANG_LANGIT -> {
				Vec3 bottom = centre.add(0.0D, -40.0D, 0.0D);
				s.add(new GateRenderer(0, life, bottom, eye, 64.0F, 96.0F, GOLD, 0.85F).openAt(0.15F).open(1.0F, Math.max(10, life - 6)));
				s.add(new RaysRenderer(0, life, centre.add(0.0D, 20.0D, 0.0D), 90.0F, 8, 25.0F, 3.0F, WHITE_GOLD, 0.25F, false));
				glyph(s, centre.add(0.0D, 46.0D, 0.0D), "天門", GOLD, 10.0F, life);
			}
			case PUSARAN -> {
				VortexRenderer vortex = new VortexRenderer(0, life, centre.x, centre.y, centre.z, 56.0F, 8.0F, EpicColors.TRIB_CLOUD,
						EpicColors.BOLT_OVERWORLD, seed);
				vortex.grow(0.7F, 1.0F, life).spin(10.0F);
				s.add(vortex);
			}
			case SEMBILAN_LANGIT -> {
				int rims = tier >= 2 ? 5 : 3;
				for (int k = 0; k < rims; k++) {
					VortexRenderer rim = new VortexRenderer(k, life - k, centre.x, centre.y + k * 10.0D, centre.z, 26.0F + k * 9.0F, 5.0F,
							EpicColors.TRIB_CLOUD, EpicColors.BOLT_OVERWORLD, seed + k);
					rim.spin(k % 2 == 0 ? 6.0F : -6.0F).arcs(k == 0);
					s.add(rim);
				}
				s.add(dev.kultivator.client.epic.Element.every(0, life, (tick, self) -> {
					if (tick % 4 != 0) {
						return;
					}
					for (int k = 0; k < 4; k++) {
						double angle = Math.PI * 0.5D * k + tick * 0.01D;
						Vec3 base = centre.add(Math.cos(angle) * 34.0D, -6.0D, Math.sin(angle) * 34.0D);
						EpicParticles.outline(PointClouds.deity(), base, new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle)), 14.0D, 3,
								EpicParticles.dust(0xD8D0FF, 2.5F), true);
					}
				}));
			}
			case SUNGAI_BINTANG -> {
				Vec3 dir = centre.subtract(eye);
				Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
				side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
				s.add(new TrailRenderer(0, life, centre.add(side.scale(-90.0D)), centre.add(side.scale(90.0D)), 14.0F, 8.0F, 0xF0F4FF, 0.55F, true, seed)
						.travel(Math.max(4, life / 3)));
				s.add(dev.kultivator.client.epic.Element.every(0, life, (tick, self) -> {
					if (tick % 2 == 0) {
						EpicParticles.shell(centre, 40.0F, 6, EpicParticles.dust(0xFFFFFF, 1.6F));
					}
				}));
			}
			case MATAHARI_KAISAR -> {
				s.add(new RaysRenderer(0, life, centre, 60.0F, 10, 40.0F, 4.0F, 0xE8D0FF, 0.3F, false).turn(0.6F));
				s.add(new RaysRenderer(0, life, centre, 40.0F, 8, 10.0F, 3.0F, 0xFFF0FF, 0.25F, true).turn(-0.4F));
				prop(s, "emperor_lotus_core", centre, 14.0F, life, casterId);
			}
			case GUNUNG_ES -> {
				s.add(new AuroraRenderer(0, life, centre.add(0.0D, -30.0D, 0.0D), 40.0F, 50.0F));
				prop(s, "ice_mountain", centre.add(0.0D, -20.0D, 0.0D), 18.0F, life, casterId);
			}
			case CAKRAM_AKRESI -> {
				VortexRenderer disc = new VortexRenderer(0, life, centre.x, centre.y, centre.z, 48.0F, 7.0F, 0x1A0A20, 0xFF8A3A, seed);
				disc.grow(0.8F, 1.0F, life).spin(-24.0F).arcs(false);
				s.add(disc);
				s.add(new RingRenderer(0, life, centre.add(0.0D, 0.5D, 0.0D), 14.0F, 16.0F, 3.0F, 6.0F, 0xB070FF, 0.6F));
			}
			case POHON_CAHAYA -> {
				s.add(new RaysRenderer(0, life, centre.add(0.0D, 20.0D, 0.0D), 90.0F, 10, 30.0F, 3.0F, 0xFFF0A0, 0.28F, false));
				s.add(dev.kultivator.client.epic.Element.every(0, life, (tick, self) -> {
					if (tick % 2 == 0) {
						EpicParticles.rain(eye.add(0.0D, 4.0D, 0.0D), 14.0F, 10.0F, 3, 0xFFB7D5);
					}
				}));
			}
			case LAUT_UTARA -> {
				// a dark sea far below the cloud sea, the Kun turning in it
				s.add(new RingRenderer(0, life, eye.add(0.0D, -18.0D, 0.0D), 0.0F, 60.0F, 0.0F, 6.0F, 0x0A1A2A, 0.6F));
				if (!prop(s, "kunpeng", centre.add(0.0D, -24.0D, 0.0D), 20.0F, life, casterId)) {
					glyph(s, centre, "鲲", 0xD8ECFF, 18.0F, life);
				}
			}
			// ================================================================ Phase 10b Titah vistas (29.4.1)
			case ISTANA_LANGIT -> {
				// the Celestial Court's gate opens in a gold sky; the decree sword is lowered point-first out of it
				Vec3 bottom = centre.add(0.0D, -40.0D, 0.0D);
				s.add(new GateRenderer(0, life, bottom, eye, 72.0F, 104.0F, GOLD, 0.85F).openAt(0.1F).open(1.0F, Math.max(10, life / 2)));
				s.add(new RaysRenderer(0, life, centre.add(0.0D, 24.0D, 0.0D), 100.0F, tier >= 2 ? 12 : 8, 25.0F, 3.5F, WHITE_GOLD, 0.3F, false)
						.turn(0.3F));
				glyph(s, centre.add(0.0D, 54.0D, 0.0D), "凌霄", GOLD, 10.0F, life);
				Vec3 from = bottom.add(0.0D, 34.0D, 0.0D);
				Vec3 to = from.lerp(eye, 0.45D).add(0.0D, -6.0D, 0.0D);
				if (!moving(s, "heaven_splitting_sword", from, to, 14.0F, life, casterId, 6)) {
					glyph(s, from, "劍", WHITE_GOLD, 14.0F, life);
				}
				s.add(Element.every(0, life, (tick, self) -> {
					if (tick % 3 == 0) {
						EpicParticles.rain(bottom.add(0.0D, 20.0D, 0.0D), 24.0F, 30.0F, 2, 0xFFE8A0);
					}
				}));
			}
			case LAUT_TIMUR -> {
				// a dark sea under the cloud sea, the glint of the Dragon King's palace, the iron pillar rising and growing
				Vec3 sea = new Vec3(centre.x, eye.y - 16.0D, centre.z);
				s.add(new RingRenderer(0, life, sea, 0.0F, 80.0F, 0.0F, 6.0F, 0x061626, 0.7F));
				s.add(new RingRenderer(4, life - 4, sea.add(0.0D, 0.2D, 0.0D), 4.0F, 60.0F, 1.5F, Math.max(10.0F, life * 0.6F), 0x3A7AAA, 0.35F));
				Vec3 palace = sea.add(eye.subtract(sea).multiply(1.0D, 0.0D, 1.0D).normalize().scale(-26.0D)).add(0.0D, 0.4D, 0.0D);
				s.add(new RaysRenderer(0, life, palace, 18.0F, 6, 60.0F, 1.5F, 0xBFE8FF, 0.25F, true).turn(1.0F));
				glyph(s, palace.add(0.0D, 3.0D, 0.0D), "龍宮", 0xBFE8FF, 4.0F, life);
				if (prop(s, "dinghai_pillar", sea, 10.0F, life, casterId, Anim.RISE, 30.0F)) {
					// the gold hoops flare as it comes up
					for (int k = 0; k < 2; k++) {
						s.add(new RingRenderer(10 + k * 6, life - 10 - k * 6, sea.add(0.0D, 6.0D + k * 16.0D, 0.0D), 1.0F, 5.0F, 0.8F, 8.0F, GOLD, 0.7F)
								.rise(4.0F));
					}
				} else {
					glyph(s, sea.add(0.0D, 12.0D, 0.0D), "定", GOLD, 12.0F, life);
				}
				s.add(Element.every(0, life, (tick, self) -> {
					if (tick % 2 == 0) {
						EpicParticles.ring(sea.add(0.0D, 0.5D, 0.0D), 6.0F + (tick % 20), 8, EpicParticles.dust(0xBFE8FF, 1.4F), 0.05F);
					}
				}));
			}
			case BIDUK_UTARA -> {
				// a starfield; the seven stars of the Dipper light Tianshu → Yaoguang and are joined in order
				s.add(new StarChart(life, centre, eye, rgb, tier));
				s.add(Element.every(0, life, (tick, self) -> {
					if (tick % 2 == 0) {
						EpicParticles.shell(centre, 46.0F, 6, EpicParticles.dust(0xFFFFFF, 1.6F));
					}
				}));
			}
			case KUA_E -> {
				// two vast figures stoop beneath the clouds to carry the mountains off; the square jade seal hangs between them
				Vec3 flat = new Vec3(eye.x - centre.x, 0.0D, eye.z - centre.z);
				flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
				Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
				float yaw = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));
				Vec3 base = centre.add(0.0D, -36.0D, 0.0D);
				for (int k = 0; k < 2; k++) {
					Vec3 feet = base.add(side.scale(k == 0 ? -24.0D : 24.0D));
					if (k == 0 || tier >= 2) {
						GiantFigureRenderer giant = new GiantFigureRenderer(k * 4, life - k * 4, feet, yaw + (k == 0 ? 20.0F : -20.0F), 40.0F,
								EpicRenderTypes.IMMORTAL_AVATAR, 0xD8E8C8, 0.32F).pose(Pose.SUMMON_RAISE, life);
						giant.bow();
						s.add(giant);
					} else {
						// Normal keeps one figure (the 7b figure cap): the second is a dust outline
						s.add(Element.every(k * 4, life - k * 4, (tick, self) -> {
							if (tick % 3 == 0) {
								EpicParticles.outline(PointClouds.humanoid(), feet, side, 40.0D, 14, EpicParticles.dust(0xD8E8C8, 4.0F), true);
							}
						}));
					}
				}
				Vec3 seal = base.add(0.0D, 30.0D, 0.0D);
				if (!prop(s, "yishan_seal", seal, 12.0F, life, casterId, Anim.GROW, 0.3F)) {
					glyph(s, seal, "山", 0xB6E3CB, 16.0F, life);
				}
				s.add(new RingRenderer(0, life, seal.add(0.0D, -2.0D, 0.0D), 2.0F, 14.0F, 1.2F, 16.0F, 0xFFE8A0, 0.4F).rune(1.5F));
			}
			default -> {
			}
		}
		if (object != null && !object.isEmpty()) {
			// the technique's own object charging in the vista (the sword drawn, the sun core kindling)
			Vec3 at = eye.lerp(centre, 0.35D);
			prop(s, object, at, Math.max(2.0F, Math.min(10.0F, objectScale * 0.5F)), life, casterId);
		}
	}

	/** A glowing model prop that grows in; false when the model is missing or the budget refuses. */
	private static boolean prop(Scene s, String model, Vec3 at, float scale, int life, int casterId) {
		return prop(s, model, at, scale, life, casterId, Anim.GROW, 0.3F);
	}

	/** A glowing model prop with one animation of the whole life ({@code a} its parameter); false when the model is missing. */
	private static boolean prop(Scene s, String model, Vec3 at, float scale, int life, int casterId, Anim anim, float a) {
		if (!AnimBridge.modelKnown(model)) {
			return false;
		}
		s.at(0, () -> AnimBridge.model(model, at, scale, 0.0F, life, anim, a, 0.0F, null, casterId));
		return true;
	}

	/**
	 * Phase 10b: a glowing model prop that grows in at {@code from} and is lowered to {@code to} after {@code delay} ticks
	 * (an accelerating drop that keeps the prop's own turn); false when the model is missing.
	 */
	private static boolean moving(Scene s, String model, Vec3 from, Vec3 to, float scale, int life, int casterId, int delay) {
		if (!AnimBridge.modelKnown(model)) {
			return false;
		}
		int travel = Math.max(4, life - delay - 6);
		s.at(0, () -> AnimBridge.model(model, from, scale, 0.0F, 0.0F, 0.0F, life,
				List.of(AnimBridge.step(Anim.GROW, 0, 10, 0.2F, 0.0F, null), AnimBridge.step(Anim.SLIDE, delay, travel, 0.0F, 2.0F, to)),
				-1, Vec3.ZERO, false, true, casterId, FxLayer.Priority.CORE));
		return true;
	}

	/** Phase 10b: one vista alone ({@code /sinematik pusaka vista}), sky-projected ahead of {@code eye}; false for NONE. */
	public static boolean test(Scene s, Dampak.Vista vista, TechniqueSpec spec, Vec3 eye, Vec3 look, int life, int casterId) {
		if (vista == Dampak.Vista.NONE) {
			return false;
		}
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		boolean low = lowVista(vista);
		double pitch = Math.toRadians(low ? -8.0D : 47.0D);
		Vec3 centre = eye.add(flat.scale(Math.cos(pitch) * 110.0D)).add(0.0D, Math.sin(pitch) * 110.0D, 0.0D);
		spawn(s, vista, spec, centre, eye, life, TargetMark.colour(spec, spec.dampak() != null ? spec.dampak() : Dampak.builder().build(), null),
				null, 1.0F, casterId);
		return true;
	}

	/** Phase 10b: vistas seen across or down onto the cloud sea (the camera looks level) rather than up into the sky. */
	public static boolean lowVista(Dampak.Vista vista) {
		return vista == Dampak.Vista.LAUT_TIMUR || vista == Dampak.Vista.KUA_E || vista == Dampak.Vista.LAUT_UTARA;
	}

	/**
	 * Phase 10b BIDUK_UTARA: the seven stars of the Northern Dipper on a plane facing the camera, lit one every 6 ticks
	 * (Tianshu, Tianxuan, Tianji, Tianquan, Yuheng, Kaiyang, Yaoguang) with each incoming line drawn star to star. A star
	 * is a soft halo plus a four-point glint of two glow strips (a cheap bloom, no post shader); Maximal adds a wide faint
	 * strip under each line.
	 */
	static final class StarChart extends Element {
		/** The Dipper in its sky shape (x right, y up; Dubhe first), units of 14 b. */
		private static final double[][] DIPPER = {{0.0D, 1.0D}, {0.1D, 0.0D}, {1.3D, -0.2D}, {1.45D, 0.6D}, {2.35D, 0.75D}, {3.15D, 0.85D},
				{4.05D, 0.4D}};
		private final Vec3[] stars = new Vec3[7];
		private final int rgb;
		private final int tier;

		StarChart(int life, Vec3 centre, Vec3 eye, int rgb, int tier) {
			super(0, life);
			this.rgb = rgb & 0xFFFFFF;
			this.tier = tier;
			this.largeRank = 4;
			Vec3 dir = centre.subtract(eye);
			dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : dir.normalize();
			Vec3 right = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
			right = right.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
			Vec3 up = right.cross(dir).normalize();
			for (int i = 0; i < 7; i++) {
				double x = (DIPPER[i][0] - 2.0D) * 14.0D;
				double y = (DIPPER[i][1] - 0.4D) * 14.0D;
				stars[i] = centre.add(right.scale(x)).add(up.scale(y));
			}
		}

		@Override
		public boolean geometry() {
			return true;
		}

		@Override
		public boolean soft() {
			return false;
		}

		@Override
		protected void fallback() {
			if ((int) age % 3 == 0) {
				int lit = Math.min(7, 1 + (int) (age / 6.0F));
				for (int i = 0; i < lit; i++) {
					EpicParticles.add(EpicParticles.dust(0xFFFFFF, 3.0F), stars[i].x, stars[i].y, stars[i].z, 0.0D, 0.0D, 0.0D, true);
				}
			}
		}

		@Override
		public void render(EpicRenderer.Frame frame, float at) {
			float f = fade(at, 4.0F, 10.0F);
			if (f <= 0.01F || !EpicBudget.takeQuads(tier >= 2 ? 48 : 30)) {
				return;
			}
			for (int i = 0; i < 7; i++) {
				float since = at - i * 6.0F;
				if (since <= 0.0F) {
					break;
				}
				float on = Math.min(1.0F, since / 4.0F) * f;
				float x = frame.rx(stars[i].x);
				float y = frame.ry(stars[i].y);
				float z = frame.rz(stars[i].z);
				float flare = since < 6.0F ? 1.0F + (6.0F - since) / 3.0F : 1.0F + 0.08F * (float) Math.sin((at + i * 7.0F) * 0.3F);
				frame.billboard(frame.ring, x, y, z, 3.2F * flare, ARGB.color(Math.round(0.7F * on * 255.0F), rgb));
				int core = ARGB.color(Math.round(0.95F * on * 255.0F), 0xFFFFFF);
				int tip = ARGB.color(0, 0xFFFFFF);
				float arm = 5.0F * flare;
				frame.segment(frame.glow, x - frame.right.x * arm, y - frame.right.y * arm, z - frame.right.z * arm, x, y, z, 0.2F, 1.4F, tip, core,
						0.0F, 1.0F);
				frame.segment(frame.glow, x, y, z, x + frame.right.x * arm, y + frame.right.y * arm, z + frame.right.z * arm, 1.4F, 0.2F, core, tip,
						0.0F, 1.0F);
				frame.segment(frame.glow, x - frame.up.x * arm, y - frame.up.y * arm, z - frame.up.z * arm, x, y, z, 0.2F, 1.4F, tip, core,
						0.0F, 1.0F);
				frame.segment(frame.glow, x, y, z, x + frame.up.x * arm, y + frame.up.y * arm, z + frame.up.z * arm, 1.4F, 0.2F, core, tip,
						0.0F, 1.0F);
				if (i > 0) {
					// the incoming line draws from the previous star over 6 ticks
					float draw = Math.min(1.0F, since / 6.0F);
					Vec3 from = stars[i - 1];
					Vec3 to = from.lerp(stars[i], draw);
					int line = ARGB.color(Math.round(0.8F * f * 255.0F), rgb);
					frame.segment(frame.glow, frame.rx(from.x), frame.ry(from.y), frame.rz(from.z), frame.rx(to.x), frame.ry(to.y), frame.rz(to.z),
							0.8F, 0.8F, line, line, 0.0F, 1.0F);
					if (tier >= 2) {
						int bloom = ARGB.color(Math.round(0.2F * f * 255.0F), rgb);
						frame.segment(frame.glow, frame.rx(from.x), frame.ry(from.y), frame.rz(from.z), frame.rx(to.x), frame.ry(to.y),
								frame.rz(to.z), 3.0F, 3.0F, bloom, bloom, 0.0F, 1.0F);
					}
				}
			}
		}
	}

	private static void glyph(Scene s, Vec3 at, String text, int rgb, float size, int life) {
		s.at(0, () -> {
			if (EpicDisplays.canCreate()) {
				EpicDisplays.glyph(at, text, rgb, size, life);
			}
		});
	}
}
