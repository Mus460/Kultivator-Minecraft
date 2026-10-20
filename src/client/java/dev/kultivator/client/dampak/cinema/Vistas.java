package dev.kultivator.client.dampak.cinema;

import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.fx.EpicDisplays;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.epic.fx.PointClouds;
import dev.kultivator.client.epic.render.AuroraRenderer;
import dev.kultivator.client.epic.render.GateRenderer;
import dev.kultivator.client.epic.render.RaysRenderer;
import dev.kultivator.client.epic.render.RingRenderer;
import dev.kultivator.client.epic.render.TrailRenderer;
import dev.kultivator.client.epic.render.VortexRenderer;
import dev.kultivator.client.fx.spec.AnimBridge;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.epic.EpicColors;
import dev.kultivator.technique.spec.dsl.Dampak;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The sky vistas of Perjalanan Langit's R2 "Puncak" (Phase 10, DESIGN 28.3.3): what the camera sees at the top of the
 * ride, built from the 7b renderers (sky-projected when they are beyond the fog) and one model prop of the technique's
 * own object charging in it. Own client only, life = R2 + 10 ticks; one or two large layers (rank 2-5). Minimal shows
 * only the sky grade and particles. Lore: the Southern Heavenly Gate and the Heavenly River are Journey to the West, the
 * Kun is Zhuangzi's "Free and Easy Wandering" [lore verify], the accretion disc and the bodhi canopy are the arts' own
 * sources; the rest is design.
 */
final class Vistas {

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
	static void spawn(Scene s, Dampak.Vista vista, TechniqueSpec spec, Vec3 centre, Vec3 eye, int life, int rgb, @Nullable String object,
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
		if (!AnimBridge.modelKnown(model)) {
			return false;
		}
		s.at(0, () -> AnimBridge.model(model, at, scale, 0.0F, life, Anim.GROW, 0.3F, 0.0F, null, casterId));
		return true;
	}

	private static void glyph(Scene s, Vec3 at, String text, int rgb, float size, int life) {
		s.at(0, () -> {
			if (EpicDisplays.canCreate()) {
				EpicDisplays.glyph(at, text, rgb, size, life);
			}
		});
	}
}
