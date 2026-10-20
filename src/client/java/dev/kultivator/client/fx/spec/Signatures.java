package dev.kultivator.client.fx.spec;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.quad.BeamQuad;
import dev.kultivator.client.fx.quad.BoltQuad;
import dev.kultivator.client.fx.quad.CrescentQuad;
import dev.kultivator.client.fx.quad.GiantAvatar;
import dev.kultivator.client.fx.quad.GlowQuad;
import dev.kultivator.client.fx.quad.QuadFx;
import dev.kultivator.client.fx.quad.RibbonQuad;
import dev.kultivator.client.fx.quad.RingQuad;
import dev.kultivator.client.fx.quad.StreakQuad;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Signature extras of single arts (polish section 6, the M2 rows): the subject-side looks the data layers cannot express
 * on their own: eye glows, whips and flame ribbons from the hand, coiling dragons around domes and pillars, auroras,
 * sky tears, fang and moon crescents, sliding shadow clones, karma threads, black-glass space slivers, a giant golden
 * emperor punching behind the caster, the 死 glyph popping over a victim. Each one runs once per cast and phase (keyed
 * by caster, spec and seed) and steps aside when the spec already authors that look itself (a RIBBON, MODEL or AVATAR
 * layer in the phase), so later data authoring never doubles it. Everything respects the quad caps, so Minimal keeps
 * only its particles.
 */
final class Signatures {

	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<String, Long> ONCE = new HashMap<>();
	private static final List<Timed> TIMED = new ArrayList<>();
	private static long ticks;

	/** A display that pops and is removed after a while (glyphs over victims). */
	private static final class Timed {
		final Display display;
		final DisplayKey key;
		final int life;
		int age;

		Timed(Display display, int life) {
			this.display = display;
			this.key = new DisplayKey(display);
			this.life = life;
		}

		/**
		 * The pop at age {@code a} (one ramp up to 1.1 over the first fifth, held, one ramp down over the last 30%, rising
		 * 0.4 blocks); keyed one tick ahead each tick, the first frame without interpolation.
		 */
		void pose(int a, boolean first) {
			float t = Math.min(1.0F, a / (float) Math.max(1, life));
			float s = (t < 0.2F ? 0.5F + 3.0F * t : t > 0.7F ? Math.max(0.05F, 1.1F * (1.0F - t) / 0.3F) : 1.1F) * 2.0F;
			Vector3f translation = new Vector3f(0.0F, t * 0.4F, 0.0F);
			Vector3f scale = new Vector3f(s, s, s);
			if (first) {
				key.snap(translation, new Quaternionf(), scale);
			} else {
				key.key(translation, new Quaternionf(), scale, 1);
			}
		}
	}

	static void play(FxEvent ev) {
		ticks++;
		Phase phase = ev.phase;
		boolean cast = phase == Phase.CAST;
		boolean hit = phase == Phase.HIT || phase == Phase.MASS_HIT;
		if (ev.has(Payloads.TechFx.FLAG_VOLLEY) && phase == Phase.TRAVEL && ev.hasTarget) {
			volley(ev);
		}
		switch (ev.spec.id()) {
			// ------------------------------------------------------------------ universal
			case "purple_demon_eye" -> {
				if (cast && once(ev, "eyes")) {
					QuadFx.add(new GlowQuad(Vec3.ZERO, 0.12F, 0.1F, 0xB050FF, 1.0F, 0.15F, 14).follow(ev.casterId, Vec3.ZERO).eyes().caster(ev.casterId));
					Entity caster = ev.caster();
					if (ev.hasTarget && caster != null) {
						QuadFx.add(new BeamQuad(caster.getEyePosition(), target(ev), 0.12F, 0xB050FF, 0xFFFFFF, 9, false).shoot(2.0F).caster(ev.casterId));
					}
				}
			}
			case "fiery_golden_eyes" -> {
				if (cast && once(ev, "eyes")) {
					QuadFx.add(new GlowQuad(Vec3.ZERO, 0.13F, 0.12F, 0xFFC020, 1.0F, 0.2F, 60).follow(ev.casterId, Vec3.ZERO).eyes().caster(ev.casterId));
					Entity caster = ev.caster();
					if (caster != null) {
						cone(caster.getEyePosition(), caster.getLookAngle(), Qi.spark(0xFFD54A, 0.35F, 10), 24, 24.0D, 0.6D);
					}
				}
			}
			case "finger_of_death" -> {
				if ((cast || phase == Phase.TRAVEL) && ev.hasTarget && once(ev, "beam")) {
					QuadFx.add(new BeamQuad(ev.hand(), target(ev), 0.07F, 0x4A4A58, 0xB8FFF0, 10, false).shoot(3.0F).caster(ev.casterId));
					burst(target(ev), ParticleTypes.SOUL_FIRE_FLAME, 6, 0.04D);
				}
				if (hit) {
					glyph(target(ev).add(0.0D, 1.2D, 0.0D), "死", 0xD8D8E8, 24);
				}
			}
			case "profound_mystery_fusion" -> {
				if ((cast || phase == Phase.TRAVEL) && once(ev, "helix") && !authored(ev, FxLayer.Template.RIBBON)) {
					int a = ev.spec.dao() == null ? 0xFFD54A : ev.spec.dao().color();
					int b = ev.has(Payloads.TechFx.FLAG_TWO_DAO) && ev.aux != 0 ? ev.aux & 0xFFFFFF : 0x9FF3FF;
					Vec3 from = ev.hand();
					Vec3 to = ev.hasTarget ? target(ev) : from.add(ev.dir.scale(12.0D));
					QuadFx.add(new RibbonQuad(RibbonQuad.helix(from, to, 0.45D, 3.0D, 0.0D, 10.0F), 26, 0.22F, 0.06F, a, Qi.rim(a), false, 22).caster(ev.casterId));
					QuadFx.add(new RibbonQuad(RibbonQuad.helix(from, to, 0.45D, 3.0D, Math.PI, 10.0F), 26, 0.22F, 0.06F, b, Qi.rim(b), false, 22).caster(ev.casterId));
				}
			}
			case "buddha_wrath_tang_lotus" -> {
				if (phase == Phase.IMPACT && once(ev, "blades")) {
					int blades = EffectQuality.maximal() ? 24 : 12;
					for (int i = 0; i < blades; i++) {
						double angle = Math.PI * 2.0D * i / blades + RANDOM.nextDouble() * 0.2D;
						double tilt = (RANDOM.nextDouble() - 0.3D) * 0.8D;
						Vec3 dir = new Vec3(Math.cos(angle), tilt, Math.sin(angle)).normalize();
						Vec3 from = ev.pos.add(0.0D, 1.0D, 0.0D);
						QuadFx.add(new StreakQuad(from, from.add(dir.scale(6.0D + RANDOM.nextDouble() * 5.0D)), 0.18F, 0x7FFFD0, 0xFFFFFF, 4.0F, 3.0F, 0.0F,
								ev.seed + i, 10).caster(ev.casterId).delay(i % 3));
					}
				}
			}
			case "life_death_hex" -> {
				if (cast && once(ev, "ash") && !authored(ev, FxLayer.Template.RIBBON)) {
					float r = Math.max(3.0F, ev.radius * 0.7F);
					int life = Math.max(60, ev.duration);
					QuadFx.add(new RibbonQuad(RibbonQuad.orbit(ev.pos, r, 1.0D, 0.08D, 0.0D, 160.0D, 0.6D), 22, 0.35F, 0.08F, 0xE8E8E8, 0x707078, false, life)
							.caster(ev.casterId));
					QuadFx.add(new RibbonQuad(RibbonQuad.orbit(ev.pos, r * 0.8D, 2.2D, -0.08D, Math.PI, 160.0D, 0.6D), 22, 0.35F, 0.08F, 0x303038, 0x101014,
							false, life).alpha(0.8F).caster(ev.casterId));
				}
			}
			case "ji_realm" -> {
				if (cast && once(ev, "grey")) {
					if (ev.own) {
						ScreenFlash.vignette(0x707070, 30);
					}
					Entity caster = ev.caster();
					Vec3 from = ev.hand();
					Vec3 look = caster == null ? ev.dir : caster.getLookAngle();
					cone(from, look, Qi.smoke(0x9A9AA0, 0.7F, 30), 36, 30.0D, 0.35D);
				}
			}
			case "netherworld_white_tiger" -> {
				if (phase == Phase.TRAVEL && once(ev, "trail") && ev.hasTarget) {
					QuadFx.add(new StreakQuad(ev.pos.add(0.0D, 1.0D, 0.0D), target(ev), 0.7F, 0x9A6AFF, 0xE8E0FF, 8.0F, 6.0F, 0.0F, ev.seed, 20).caster(ev.casterId));
				}
			}
			case "golden_ape_roar", "phoenix_sky_roaring_strike" -> {
				if (cast && once(ev, "roar")) {
					roarRing(ev, ev.spec.id().equals("golden_ape_roar") ? 0xFFD54A : 0xFF7A2A);
				}
			}
			// ------------------------------------------------------------------ sword
			case "azure_essence_sword" -> {
				if ((phase == Phase.TRAVEL || phase == Phase.IMPACT || hit) && once(ev, "cut" + ev.index)) {
					QuadFx.add(new CrescentQuad(ev.hand(), ev.dir, false, 2.2F, 1.0F, 0.35F, 6.0F, 0x7FD8FF, 0xFFFFFF, 8).caster(ev.casterId));
				}
			}
			case "heavenly_wolf_slash" -> {
				if (phase == Phase.END && once(ev, "fangs")) {
					for (int i = 0; i < 3; i++) {
						Vec3 at = ev.pos.add(0.0D, 1.2D + i * 0.35D, 0.0D);
						QuadFx.add(new CrescentQuad(at, ev.dir, true, 2.0F + i * 0.4F, 0.9F, 0.3F, 2.0F, 0xC8D8FF, 0xFFFFFF, 8).sweep(2.0F).caster(ev.casterId)
								.delay(i * 2));
					}
				}
			}
			case "bloodmoon_immortal_slaying_sword" -> {
				if (cast && once(ev, "moon")) {
					int life = Math.max(100, ev.duration + 40);
					QuadFx.add(new GlowQuad(ev.pos.add(0.0D, 42.0D, 0.0D).subtract(ev.dir.scale(10.0D)), 9.0F, 10.0F, 0xC0182A, 0.85F, 0.03F, life)
							.caster(ev.casterId));
					if (ev.own) {
						ScreenFlash.vignette(0x8A0010, Math.min(200, life));
					}
				}
			}
			case "blood_drop_stance" -> {
				if (hit || phase == Phase.IMPACT) {
					Vec3 at = target(ev);
					QuadFx.add(new CrescentQuad(at.subtract(ev.dir.scale(0.8D)), ev.dir, RANDOM.nextBoolean(), 1.6F, 1.1F, 0.3F, 1.2F, 0xD01020, 0xFFB0B0, 7)
							.sweep(2.0F).caster(ev.casterId));
					burst(at, Qi.mote(0xA00818, 0.2F, 20), 12, 0.2D);
				}
			}
			case "zhuxian_sword_formation" -> {
				if (phase == Phase.IMPACT || phase.extraIndex() == 0) {
					int swords = EffectQuality.maximal() ? 16 : 8;
					float r = Math.max(4.0F, ev.radius);
					for (int i = 0; i < swords; i++) {
						double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
						double rr = r * Math.sqrt(RANDOM.nextDouble());
						Vec3 ground = ev.pos.add(Math.cos(angle) * rr, 0.0D, Math.sin(angle) * rr);
						int color = new int[]{0xFF4A4A, 0x5FD066, 0x5AA8FF, 0x9D4FD8}[i % 4];
						QuadFx.add(new StreakQuad(ground.add(0.0D, 16.0D, 0.0D), ground, 0.25F, color, 0xFFFFFF, 4.0F, 2.0F, 0.0F, ev.seed + i, 9)
								.caster(ev.casterId).delay(i % 5));
					}
				}
			}
			case "heaven_punishing_sword" -> {
				if (cast && once(ev, "tear")) {
					Vec3 mid = ev.pos.add(0.0D, 32.0D, 0.0D);
					Vec3 side = ev.dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
					side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
					QuadFx.add(new StreakQuad(mid.subtract(side.scale(18.0D)), mid.add(side.scale(18.0D)), 2.2F, 0xFFE7A0, 0xFFFFFF, 10.0F, 0.0F, 2.0F, ev.seed,
							Math.max(80, ev.duration)).caster(ev.casterId));
				}
			}
			case "immortal_tribulation_sword" -> {
				if (cast && ev.hasTarget && once(ev, "arcs")) {
					Vec3 a = ev.hand();
					Vec3 b = target(ev);
					for (int i = 0; i < 3; i++) {
						Vec3 p0 = a.lerp(b, i / 3.0D);
						Vec3 p1 = a.lerp(b, (i + 1) / 3.0D);
						QuadFx.add(new BoltQuad(p0, p1, 0.12F, 0xB57CFF, 0xFFFFFF, ev.seed + i * 13, 1, 10).caster(ev.casterId).delay(i * 2));
					}
				}
			}
			case "heavenbreaker_stance" -> {
				if (phase == Phase.TRAVEL && once(ev, "cleave")) {
					QuadFx.add(new CrescentQuad(ev.hand().add(0.0D, 1.0D, 0.0D), ev.dir, true, 5.0F, 1.2F, 0.9F, Math.max(8.0F, ev.length), 0xE8F2FF, 0xFFFFFF, 12)
							.sweep(3.0F).caster(ev.casterId));
				}
			}
			// ------------------------------------------------------------------ fire
			case "phoenix_fire_line" -> {
				if (cast && once(ev, "line") && !authored(ev, FxLayer.Template.RIBBON)) {
					Entity caster = ev.caster();
					Vec3 mouth = caster == null ? ev.pos : caster.getEyePosition().add(ev.dir.scale(0.4D)).add(0.0D, -0.15D, 0.0D);
					Vec3 to = ev.hasTarget ? target(ev) : mouth.add(ev.dir.scale(Math.max(8.0F, ev.length)));
					QuadFx.add(new RibbonQuad(RibbonQuad.wave(mouth, to, 0.3D, 6.0F), 26, 0.7F, 0.15F, 0xFFD54A, 0xC2280A, true, 18).caster(ev.casterId));
				}
			}
			case "flame_splitting_tsunami" -> {
				if (phase == Phase.TRAVEL && once(ev, "wave")) {
					QuadFx.add(new CrescentQuad(ev.pos.add(0.0D, 1.5D, 0.0D), ev.dir, true, 4.5F, 1.25F, 1.2F, Math.max(10.0F, ev.length), 0xD8203A, 0xFFB060,
							Math.max(12, ev.duration)).caster(ev.casterId));
				}
			}
			case "nine_dragon_fire_cover" -> {
				if (cast && once(ev, "dragons") && !authored(ev, FxLayer.Template.RIBBON)) {
					dragons(ev, ev.pos, Math.max(6.0F, ev.radius), 9, 0xFF5A1A, 0xC2280A, Math.max(120, ev.duration), false);
				}
			}
			case "tongtian_divine_fire_pillars" -> {
				if ((cast || phase == Phase.IMPACT) && once(ev, "coil" + ev.index) && !authored(ev, FxLayer.Template.RIBBON)) {
					int pillars = ev.of > 1 ? 1 : 4;
					float r = Math.max(4.0F, ev.radius);
					for (int i = 0; i < pillars; i++) {
						double angle = Math.PI * 2.0D * i / pillars;
						Vec3 base = pillars == 1 ? ev.pos : ev.pos.add(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r);
						QuadFx.add(new RibbonQuad(RibbonQuad.coil(base, 1.4D, 14.0D, 2.5D, 0.12D, angle), 22, 0.6F, 0.12F, 0xFF7A2A, 0xC2280A, true,
								Math.max(80, ev.duration)).caster(ev.casterId));
					}
				}
			}
			// ------------------------------------------------------------------ ice
			case "qianlan_ice_flame" -> {
				if (cast && once(ev, "flame") && !authored(ev, FxLayer.Template.RIBBON)) {
					Vec3 from = ev.hand();
					Vec3 to = ev.hasTarget ? target(ev) : from.add(ev.dir.scale(Math.max(8.0F, ev.length)));
					QuadFx.add(new RibbonQuad(RibbonQuad.wave(from, to, 0.25D, 6.0F), 24, 0.55F, 0.12F, 0x9FE8FF, 0x2A6AD8, true, 16).caster(ev.casterId));
				}
			}
			case "frozen_end_divine_art" -> {
				if (cast && once(ev, "aurora") && !authored(ev, FxLayer.Template.RIBBON)) {
					float r = Math.max(6.0F, ev.radius * 0.8F);
					int life = Math.max(120, ev.duration);
					for (int i = 0; i < 3; i++) {
						RibbonQuad aurora = new RibbonQuad(RibbonQuad.aurora(ev.pos, r, 8.0D + i * 2.0D, i * 2.1D), 30, 1.6F, 1.0F,
								i == 1 ? 0x9FFFD8 : 0x9FE8FF, 0x6A4AD8, false, life);
						aurora.alpha(0.55F);
						if (ev.follow()) {
							aurora.follow(ev.casterId, ev.casterPos());
						}
						QuadFx.add(aurora.caster(ev.casterId));
					}
				}
			}
			case "broken_moon_shadow" -> {
				if ((cast || phase == Phase.TRAVEL) && once(ev, "moon" + ev.index)) {
					QuadFx.add(new CrescentQuad(ev.pos.add(0.0D, 1.2D, 0.0D), ev.dir, true, 2.4F, 1.3F, 0.4F, 3.0F, 0xD8F0FF, 0xFFFFFF, 10).sweep(3.0F)
							.caster(ev.casterId));
				}
			}
			// ------------------------------------------------------------------ lightning
			case "zidian_whip" -> {
				if (cast && ev.hasTarget && once(ev, "whip") && !authored(ev, FxLayer.Template.RIBBON)) {
					QuadFx.add(new RibbonQuad(RibbonQuad.whip(ev.hand(), target(ev), 0.8D, 5.0F), 26, 0.22F, 0.05F, 0xC07CFF, 0x5B2DB0, false, 14)
							.caster(ev.casterId));
				}
				if (hit) {
					burst(target(ev), Qi.spark(0xE0C8FF, 0.4F, 5), 10, 0.4D);
				}
			}
			case "tribulation_thunder_emperor_array" -> {
				if (hit) {
					Vec3 at = target(ev);
					QuadFx.add(new BoltQuad(at.add((RANDOM.nextDouble() - 0.5D) * 3.0D, 16.0D, (RANDOM.nextDouble() - 0.5D) * 3.0D), at, 0.28F, 0xB57CFF,
							0xFFFFFF, ev.seed + ev.targetId, 2, 7).caster(ev.casterId));
				}
			}
			// ------------------------------------------------------------------ body
			case "undying_emperor_fist" -> {
				if (cast && once(ev, "emperor") && !authored(ev, FxLayer.Template.AVATAR) && !EffectQuality.minimal()) {
					GiantAvatar giant = new GiantAvatar(ev.casterId, 8.0F, 0xFFD54A, 0xFFF1B8, 0.45F, 3.0F, Pose.PUNCH, 34);
					QuadFx.add(giant);
				}
			}
			case "six_paths_fist" -> {
				if ((phase == Phase.IMPACT || phase.extraIndex() >= 0) && once(ev, "fist" + phase.ordinal() + ev.index)) {
					int[] paths = {0xFFFFFF, 0xFFD54A, 0xD02838, 0x5AA8FF, 0x5FD066, 0x9D4FD8};
					int color = paths[Math.floorMod(Math.max(0, phase.extraIndex()) + ev.index, paths.length)];
					QuadFx.add(new StreakQuad(ev.hand(), ev.hasTarget ? target(ev) : ev.pos, 0.45F, color, 0xFFFFFF, 6.0F, 4.0F, 0.0F, ev.seed, 12).caster(ev.casterId));
				}
			}
			// ------------------------------------------------------------------ life
			case "willow_god_branches" -> {
				if ((phase == Phase.TRAVEL || hit) && ev.hasTarget && once(ev, "branch" + ev.targetId + ev.index)) {
					Vec3 canopy = ev.casterPos().add(0.0D, 4.0D, 0.0D).subtract(ev.dir.scale(2.0D));
					QuadFx.add(new RibbonQuad(RibbonQuad.whip(canopy, target(ev), 0.6D, 6.0F), 22, 0.18F, 0.05F, 0x9CFF8A, 0x2E8A3A, false, 16).caster(ev.casterId));
				}
			}
			// ------------------------------------------------------------------ shadow / wind
			case "shadow_devil_fusion" -> {
				if (cast && once(ev, "devil")) {
					Entity caster = ev.caster();
					if (caster != null) {
						List<Vec3> points = new ArrayList<>(4);
						Vec3 flat = new Vec3(ev.dir.x, 0.0D, ev.dir.z);
						flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
						for (int i = 0; i < 4; i++) {
							points.add(caster.position().add(flat.scale(2.0D + i * 2.0D)));
						}
						AvatarFx.fixed(ev.casterId, points, (float) Math.toDegrees(Math.atan2(-flat.x, flat.z)), 0x201030, 150, 30);
						QuadFx.add(new GlowQuad(caster.position().add(0.0D, 0.2D, 0.0D), 0.5F, 0.9F, 0x9A6AFF, 0.8F, 0.2F, 20).caster(ev.casterId));
					}
				}
			}
			case "kunpeng_art" -> {
				if (phase == Phase.TRAVEL && once(ev, "wing" + ev.index)) {
					QuadFx.add(new CrescentQuad(ev.pos.add(0.0D, 2.0D, 0.0D), ev.dir, false, 9.0F, 1.0F, 1.0F, Math.max(16.0F, ev.length), 0xD8F0FF, 0xFFFFFF,
							Math.max(12, ev.duration)).caster(ev.casterId));
				}
			}
			case "karma_domain", "karmic_hex" -> {
				if ((phase == Phase.PULSE || hit || phase.extraIndex() == 0) && ev.targetId >= 0) {
					Entity a = ev.targetEntity();
					Entity b = ev.aux > 0 ? entity(ev.aux) : ev.caster();
					if (a != null && b != null && a != b) {
						Vec3 pa = a.position().add(0.0D, a.getBbHeight() * 0.6D, 0.0D);
						Vec3 pb = b.position().add(0.0D, b.getBbHeight() * 0.6D, 0.0D);
						QuadFx.add(new BoltQuad(pa, pb, 0.05F, 0xFF3030, 0xFFD54A, ev.seed + ev.targetId, 0, 12)
								.follow(a.getId(), (float) (a.getBbHeight() * 0.6D), b.getId(), (float) (b.getBbHeight() * 0.6D)).caster(ev.casterId));
					}
				}
			}
			// ------------------------------------------------------------------ space
			case "space_blade" -> {
				if ((cast || phase == Phase.TRAVEL) && once(ev, "sliver")) {
					Vec3 from = ev.hand();
					Vec3 to = ev.hasTarget ? target(ev) : from.add(ev.dir.scale(Math.max(8.0F, ev.length)));
					QuadFx.add(new StreakQuad(from, to, 0.3F, 0x3A1A6A, 0xE8E0FF, 6.0F, 8.0F, 0.15F, ev.seed, 16).caster(ev.casterId));
					for (int i = 0; i < Math.round(16 * EffectQuality.level().factor()); i++) {
						add(ParticleTypes.REVERSE_PORTAL, from.lerp(to, RANDOM.nextDouble()), Vec3.ZERO);
					}
				}
			}
			case "immortal_binding_rope" -> {
				if ((cast || phase == Phase.TRAVEL) && ev.hasTarget && once(ev, "rope" + ev.index)) {
					Vec3 from = ev.hand();
					QuadFx.add(new RibbonQuad(RibbonQuad.helix(from, target(ev), 0.12D, 6.0D, 0.0D, 6.0F), 24, 0.14F, 0.1F, 0xFFD54A, 0xC89020, false, 24)
							.caster(ev.casterId));
				}
			}
			case "stellar_black_hole" -> {
				if (cast && once(ev, "disk")) {
					float r = Math.max(4.0F, ev.radius * 0.6F);
					int life = Math.max(80, ev.duration);
					Vec3 center = ev.pos.add(0.0D, 3.0D, 0.0D);
					QuadFx.add(new RingQuad(center, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.15D, 1.0D), r, r, 0xFF9A3A, 0.85F, 0.1F, life).caster(ev.casterId));
					QuadFx.add(new RingQuad(center, new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.15D, 1.0D), r * 0.6F, r * 0.6F, 0xFFE7A0, 0.7F, 0.16F, life)
							.caster(ev.casterId));
				}
			}
			case "gravitational_space" -> {
				if ((cast || phase == Phase.PULSE) && !EffectQuality.minimal()) {
					float r = Math.max(4.0F, ev.radius);
					RingQuad lens = new RingQuad(new Vec3(ev.pos.x, LayerRun.groundY(ev.pos.add(0.0D, 1.0D, 0.0D)), ev.pos.z), r, r * 0.25F, 0x8A6AFF, 0.6F, 20);
					if (ev.follow()) {
						lens.follow(ev.casterId, 0.05D);
					}
					QuadFx.add(lens.caster(ev.casterId));
				}
			}
			// ------------------------------------------------------------------ immortal
			// ------------------------------------------------------------------ second batch (rows 6.1-6.9)
			case "mysterious_jade_hand" -> {
				if (phase.extraIndex() == 0) {
					// a caught projectile: a jade ripple where it stopped
					burst(ev.pos, Qi.ring(0x8FFFD0, 0.3F, 2.2F, 10), 1, 0.0D);
					burst(ev.pos, Qi.mote(0x8FFFD0, 0.2F, 12), 10, 0.15D);
				}
			}
			case "somersault_cloud", "cloud_travel" -> {
				if (cast && once(ev, "wake")) {
					crawl(ev.casterId, Qi.mote(0xFFE7A0, 0.3F, 24), 3, Math.max(40, ev.duration), 0.0D, 0.25D);
					crawl(ev.casterId, Qi.mist(0xF4F8FF, 0.9F, 30), 2, Math.max(40, ev.duration), -0.2D, 0.15D);
				}
			}
			case "seventy_two_transformations" -> {
				if (cast || phase.extraIndex() >= 0) {
					Entity caster = ev.caster();
					Vec3 at = caster == null ? ev.pos : caster.position();
					burst(at.add(0.0D, 1.0D, 0.0D), Qi.smoke(0xE8C860, 0.9F, 24), 18, 0.15D);
					glyph(at.add(0.0D, 2.6D, 0.0D), "变", 0xFFD54A, 18);
				}
			}
			case "blood_death_world" -> {
				if (cast && once(ev, "sky") && ev.own) {
					ScreenFlash.vignette(0x6A0010, Math.max(40, Math.min(200, ev.duration)));
				}
				if (phase == Phase.PULSE || cast) {
					float r = Math.max(4.0F, ev.radius);
					Vec3 c = ev.follow() ? ev.casterPos() : ev.pos;
					int n = Math.round(10 * EffectQuality.level().factor());
					for (int i = 0; i < n; i++) {
						double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
						double rr = r * Math.sqrt(RANDOM.nextDouble());
						// blood drips fall (qi with gravity)
						add(Qi.make(dev.kultivator.fx.QiParticles.QI, 0xFF8A8A, 0xC0182A, 0x4A0008, 0.18F, 0.12F, 30, dev.kultivator.fx.QiParticleOptions.GLOW),
								c.add(Math.cos(angle) * rr, 3.0D + RANDOM.nextDouble() * 3.0D, Math.sin(angle) * rr), new Vec3(0.0D, -0.25D, 0.0D));
					}
				}
			}
			case "proud_golden_body" -> {
				if (cast && once(ev, "crawl")) {
					crawl(ev.casterId, Qi.spark(0xFFD54A, 0.3F, 6), 2, Math.max(60, ev.duration), 0.0D, 0.05D);
				}
			}
			case "lightning_mark_armor" -> {
				if (cast && once(ev, "crawl")) {
					crawl(ev.casterId, Qi.spark(0xC7A2FF, 0.35F, 4), 2, Math.max(60, ev.duration), 0.0D, 0.1D);
				}
			}
			case "phoenix_bathing_fire" -> {
				if (cast && once(ev, "mantle")) {
					crawl(ev.casterId, Qi.flame(0xFF7A2A, 0.45F, 14), 5, Math.max(60, ev.duration), 0.0D, 0.04D);
				}
			}
			case "burning_heart" -> {
				if (cast && once(ev, "aura")) {
					crawl(ev.casterId, Qi.flame(0xE8402A, 0.35F, 12), 3, Math.max(60, ev.duration), 0.0D, 0.05D);
				}
				if (phase == Phase.PULSE || phase.extraIndex() >= 0) {
					Entity caster = ev.caster();
					if (caster != null) {
						burst(caster.position().add(0.0D, 1.2D, 0.0D), Qi.ring(0xE8402A, 0.3F, 2.6F, 8), 1, 0.0D);
						if (ev.own) {
							ScreenFlash.vignette(0x8A1010, 10);
						}
					}
				}
			}
			case "ruyi_staff" -> {
				if (phase == Phase.TRAVEL && once(ev, "bands") && !EffectQuality.minimal()) {
					Vec3 from = ev.pos;
					Vec3 to = ev.hasTarget ? ev.target : ev.pos.add(ev.dir.scale(Math.max(8.0F, ev.length)));
					Vec3 axis = to.subtract(from);
					double len = axis.length();
					Vec3 d = len < 1.0E-4D ? ev.dir : axis.scale(1.0D / len);
					Vec3 side = d.cross(new Vec3(0.0D, 1.0D, 0.0D));
					side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
					Vec3 upv = side.cross(d).normalize();
					int bands = (int) Math.max(2, Math.min(10, len / 3.0D));
					for (int i = 0; i < bands; i++) {
						Vec3 at = from.add(d.scale(len * (i + 0.5D) / bands)).add(0.0D, 0.6D, 0.0D);
						QuadFx.add(new RingQuad(at, side, upv, 0.6F, 1.6F, 0xFFD54A, 0.8F, 0.0F, 12).caster(ev.casterId).delay(i));
						burst(at.add(0.0D, -0.5D, 0.0D), Qi.smoke(0x9A8A70, 0.7F, 24), 4, 0.08D);
					}
				}
			}
			case "lu_ya_gourd" -> {
				if (cast && ev.hasTarget && once(ev, "light")) {
					Vec3 mouth = ev.hand().add(0.0D, 0.9D, 0.0D);
					QuadFx.add(new BeamQuad(mouth, target(ev), 0.1F, 0xFFFFF0, 0xFFFFFF, 12, false).shoot(4.0F).caster(ev.casterId));
					glyph(target(ev).add(0.0D, 1.4D, 0.0D), "仙", 0xFFF2C0, 20);
				}
			}
			case "clear_heart_melody" -> {
				if (cast || hit) {
					Vec3 at = cast ? ev.casterPos().add(0.0D, 1.4D, 0.0D) : target(ev);
					int n = Math.round(8 * EffectQuality.level().factor());
					for (int i = 0; i < n; i++) {
						double angle = Math.PI * 2.0D * i / Math.max(1, n);
						Vec3 p = at.add(Math.cos(angle) * 1.2D, RANDOM.nextDouble() * 0.6D, Math.sin(angle) * 1.2D);
						addNote(p, RANDOM.nextInt(24) / 24.0D);
					}
				}
			}
			case "guanyin_sweet_dew" -> {
				if (phase == Phase.PULSE || cast) {
					float r = Math.max(3.0F, ev.radius);
					Vec3 c = ev.follow() ? ev.casterPos() : ev.pos;
					int n = Math.round(12 * EffectQuality.level().factor());
					for (int i = 0; i < n; i++) {
						double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
						double rr = r * Math.sqrt(RANDOM.nextDouble());
						Vec3 p = c.add(Math.cos(angle) * rr, 5.0D + RANDOM.nextDouble() * 2.0D, Math.sin(angle) * rr);
						add(i % 2 == 0 ? ParticleTypes.FALLING_WATER : Qi.mote(0xE8FFF8, 0.16F, 24), p, new Vec3(0.0D, -0.2D, 0.0D));
					}
				}
			}
			case "purple_gold_gourd" -> {
				if (phase.extraIndex() == 0) {
					Vec3 from = target(ev);
					Vec3 mouth = ev.hasTarget ? ev.target : ev.casterPos().add(0.0D, 3.0D, 0.0D);
					Entity victim = ev.targetEntity();
					if (victim != null) {
						glyph(victim.position().add(0.0D, victim.getBbHeight() + 0.8D, 0.0D), "名", 0xE8C8FF, 24);
						from = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
					}
					QuadFx.add(new StreakQuad(from, mouth, 0.4F, 0xB57CFF, 0xFFE7A0, 8.0F, 4.0F, 0.0F, ev.seed, 16).caster(ev.casterId));
				}
			}
			case "heaven_trampling" -> {
				if (phase == Phase.IMPACT || phase.extraIndex() == 0) {
					Vec3 feet = ev.casterPos();
					QuadFx.add(new RingQuad(new Vec3(feet.x, LayerRun.groundY(feet.add(0.0D, 0.5D, 0.0D)) + 0.02D, feet.z), 0.4F, 2.4F, 0xFFD54A, 0.9F, 14)
							.caster(ev.casterId));
				}
			}
			default -> {
			}
		}
	}

	/** A particle crawl over a body for a while (golden body, lightning armour, flame mantle, the cloud's wake). */
	private static final class Crawl {
		final int entity;
		final ParticleOptions particle;
		final int perTick;
		final double lift;
		final double speed;
		int ticks;

		Crawl(int entity, ParticleOptions particle, int perTick, int ticks, double lift, double speed) {
			this.entity = entity;
			this.particle = particle;
			this.perTick = perTick;
			this.ticks = ticks;
			this.lift = lift;
			this.speed = speed;
		}
	}

	private static final List<Crawl> CRAWLS = new ArrayList<>();

	private static void crawl(int entity, ParticleOptions particle, int perTick, int ticks, double lift, double speed) {
		if (CRAWLS.size() < 32) {
			CRAWLS.add(new Crawl(entity, particle, perTick, Math.min(1200, ticks), lift, speed));
		}
	}

	/** One projectile of a volley (FLAG_VOLLEY, one TRAVEL per projectile): a streak in the art's colour to its target. */
	private static void volley(FxEvent ev) {
		if (EffectQuality.minimal() || ev.viewScale <= 0.3F) {
			return;
		}
		int color = ev.spec.dao() == null ? 0xE8F0FF : ev.spec.dao().color();
		Vec3 from = ev.pos;
		Vec3 to = target(ev);
		int travel = Math.max(3, Math.min(12, ev.duration > 0 ? ev.duration : (int) (from.distanceTo(to) / 2.5D)));
		QuadFx.add(new StreakQuad(from, to, 0.16F, color, 0xFFFFFF, travel, 4.0F, 0.0F, ev.seed + ev.index, travel + 6).caster(ev.casterId));
	}

	private static void addNote(Vec3 at, double color) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0) {
			// the note particle reads its colour from the x speed (0..1 around the hue circle)
			level.addParticle(ParticleTypes.NOTE, true, false, at.x, at.y, at.z, color, 0.0D, 0.0D);
		}
	}

	/** Once per client tick: ages the popped glyphs and the once-keys. */
	static void tick() {
		ClientLevel crawlLevel = Minecraft.getInstance().level;
		for (Iterator<Crawl> it = CRAWLS.iterator(); it.hasNext(); ) {
			Crawl crawl = it.next();
			Entity entity = crawlLevel == null ? null : crawlLevel.getEntity(crawl.entity);
			if (entity == null || !entity.isAlive() || crawl.ticks-- <= 0) {
				it.remove();
				continue;
			}
			int n = Math.max(1, Math.round(crawl.perTick * EffectQuality.level().factor()));
			double w = entity.getBbWidth() * 0.6D;
			double h = entity.getBbHeight();
			for (int i = 0; i < n; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
				Vec3 p = entity.position().add(Math.cos(angle) * w, crawl.lift + RANDOM.nextDouble() * h, Math.sin(angle) * w);
				add(crawl.particle, p, new Vec3(Math.cos(angle) * crawl.speed * 0.3D, crawl.speed, Math.sin(angle) * crawl.speed * 0.3D));
			}
		}
		for (Iterator<Timed> it = TIMED.iterator(); it.hasNext(); ) {
			Timed timed = it.next();
			timed.age++;
			if (timed.display.isRemoved() || timed.age >= timed.life) {
				PhantomDisplays.remove(timed.display);
				it.remove();
				continue;
			}
			timed.pose(timed.age + 1, false);
		}
		if (ONCE.size() > 256) {
			ONCE.values().removeIf(at -> ticks - at > 400L);
		}
	}

	static void clear() {
		CRAWLS.clear();
		for (Timed timed : TIMED) {
			PhantomDisplays.remove(timed.display);
		}
		TIMED.clear();
		ONCE.clear();
	}

	// ================================================================== helpers

	/** True the first time this (cast, phase, tag) is seen. */
	private static boolean once(FxEvent ev, String tag) {
		String key = ev.casterId + ":" + ev.spec.netId() + ":" + ev.seed + ":" + ev.phase.ordinal() + ":" + tag;
		if (ONCE.containsKey(key)) {
			return false;
		}
		ONCE.put(key, ticks);
		return true;
	}

	/** True when the spec authors a layer of {@code template} in this phase (the data then carries the look). */
	private static boolean authored(FxEvent ev, FxLayer.Template template) {
		for (FxLayer layer : ev.spec.fx(ev.phase)) {
			if (layer.template() == template || template == FxLayer.Template.RIBBON && layer.hasModel()) {
				return true;
			}
		}
		return false;
	}

	private static Vec3 target(FxEvent ev) {
		Entity entity = ev.targetEntity();
		if (entity != null && entity.isAlive()) {
			return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
		}
		return ev.target;
	}

	private static Entity entity(int id) {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? null : level.getEntity(id);
	}

	/** {@code count} dragons circling {@code center} (or the caster) at radius {@code r}. */
	private static void dragons(FxEvent ev, Vec3 center, float r, int count, int head, int tail, int life, boolean follow) {
		for (int i = 0; i < count; i++) {
			double phase = Math.PI * 2.0D * i / count;
			double height = 1.5D + (i % 3) * r * 0.18D;
			double speed = (i % 2 == 0 ? 1.0D : -1.0D) * (0.05D + 0.01D * (i % 3));
			RibbonQuad quad = new RibbonQuad(RibbonQuad.orbit(center, r * (0.85D + 0.1D * (i % 2)), height, speed, phase, 190.0D, 0.8D), 26, 0.6F, 0.12F, head,
					tail, true, life);
			if (follow) {
				quad.follow(ev.casterId, ev.casterPos());
			}
			if (!QuadFx.add(quad.caster(ev.casterId))) {
				break;
			}
		}
	}

	/** A vertical ring of the roar rolling forward from the caster's mouth. */
	private static void roarRing(FxEvent ev, int color) {
		Entity caster = ev.caster();
		if (caster == null || EffectQuality.minimal()) {
			return;
		}
		Vec3 look = caster.getLookAngle();
		Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
		side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
		Vec3 upv = side.cross(look).normalize();
		for (int i = 0; i < 3; i++) {
			Vec3 at = caster.getEyePosition().add(look.scale(1.5D + i * 1.6D));
			QuadFx.add(new RingQuad(at, side, upv, 0.6F + i * 0.4F, 3.0F + i * 1.5F, color, 0.8F - i * 0.2F, 0.0F, 10).caster(ev.casterId).delay(i * 2));
		}
	}

	private static void glyph(Vec3 at, String text, int color, int life) {
		if (!PhantomDisplays.enabled() || EffectQuality.minimal()) {
			return;
		}
		Display.TextDisplay display = PhantomDisplays.create(EntityTypes.TEXT_DISPLAY, at, 1.2F);
		if (display == null) {
			return;
		}
		display.setText(Component.literal(text).withColor(color & 0xFFFFFF));
		display.setBackgroundColor(0);
		display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		Timed timed = new Timed(display, life);
		// the first frame now (it used to show at the default scale 1 until the first key, then zoom)
		timed.pose(1, true);
		TIMED.add(timed);
	}

	private static void cone(Vec3 from, Vec3 dir, ParticleOptions particle, int count, double halfDeg, double speed) {
		Vec3 d = dir.normalize();
		Vec3 side = d.cross(new Vec3(0.0D, 1.0D, 0.0D));
		side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
		Vec3 upv = side.cross(d).normalize();
		double half = Math.toRadians(halfDeg);
		int n = Math.max(2, Math.round(count * EffectQuality.level().factor()));
		for (int i = 0; i < n; i++) {
			double spin = RANDOM.nextDouble() * Math.PI * 2.0D;
			double tilt = half * Math.sqrt(RANDOM.nextDouble());
			Vec3 v = d.scale(Math.cos(tilt)).add(side.scale(Math.sin(tilt) * Math.cos(spin))).add(upv.scale(Math.sin(tilt) * Math.sin(spin)));
			add(particle, from.add(v.scale(0.4D)), v.scale(speed * (0.6D + RANDOM.nextDouble() * 0.6D)));
		}
	}

	private static void burst(Vec3 at, ParticleOptions particle, int count, double speed) {
		int n = Math.max(2, Math.round(count * EffectQuality.level().factor()));
		for (int i = 0; i < n; i++) {
			Vec3 d = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
			add(particle, at.add(d.scale(0.2D)), d.scale(speed));
		}
	}

	private static void add(ParticleOptions particle, Vec3 at, Vec3 velocity) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0) {
			level.addParticle(particle, true, false, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
		}
	}

	private Signatures() {
	}
}
