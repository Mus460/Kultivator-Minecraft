package dev.kultivator.client.anim.model;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.ClientBinds;
import dev.kultivator.data.Dao;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 3D props this module places itself (the M3 rows of the polish plan's section 6): the jade hand that guards the
 * chest, the somersault cloud under the feet, the crescent moon behind the back, Ruyi's staff growing in the hand and
 * slamming down the line, the three life-saving hairs circling the head, the six-path wheel, the void mirror, the time
 * clock with its hands running backwards, the falling seals, cauldrons, suns, swords, palms and feet on their
 * pre-impact events, the trees, stupas, pagodas, lotuses and vases that grow behind the caster, the sliding blades and
 * beasts, and the grass, ice and rope on bound bodies. A spec that authors a MODEL layer of the same id itself is left
 * to that layer (no double props). Everything goes through {@link ModelFx} and its budget; Minimal shows only the
 * identity props.
 */
public final class ArtModels {

	private static int warnings;
	/** Per caster: the ids of props placed for the running cast (to find and change them on later events). */
	private static final Map<Integer, List<ModelHandle>> CAST_PROPS = new HashMap<>();

	public static void onTechFx(Payloads.TechFx p, TechniqueSpec spec, Phase phase, @Nullable Entity caster) {
		try {
			if (caster != null && caster != Minecraft.getInstance().player && caster instanceof Player && EffectQuality.othersScale() < 0.5F) {
				return;
			}
			String name = phase.name();
			if ("ABORT".equals(name)) {
				if (caster != null) {
					endCast(caster.getId());
				}
				return;
			}
			place(new Ev(p, spec, phase, caster));
		} catch (RuntimeException exception) {
			if (warnings++ < 5) {
				Kultivator.LOGGER.warn("Kultivator: technique prop error for {}", spec.id(), exception);
			}
		}
	}

	/** A new bind: grass, ice and rope props on the bound body. */
	public static void onBind(ClientBinds.Bind bind) {
		try {
			ClientLevel level = Minecraft.getInstance().level;
			Entity entity = level == null ? null : level.getEntity(bind.entity());
			if (entity == null) {
				return;
			}
			int ticks = Math.max(10, bind.ticks());
			float w = entity.getBbWidth();
			float h = entity.getBbHeight();
			switch (bind.kind()) {
				case ROOT_VINE -> {
					int n = EffectQuality.minimal() ? 1 : 3;
					for (int i = 0; i < n; i++) {
						double a = Math.PI * 2.0D * i / n;
						Vec3 off = new Vec3(Math.cos(a) * w * 0.55D, 0.0D, Math.sin(a) * w * 0.55D);
						ModelHandle grass = ModelFx.show(new ModelRequest("blue_silver_grass", entity.position().add(off), 1.2F + h * 0.35F,
								(float) Math.toDegrees(a), 0.0F, 0.0F, ticks, List.of(AnimStep.of(Anim.RISE, 0, 6, 2.0F, 0.0F),
								AnimStep.of(Anim.SWING, 6, ticks, 6.0F, 30.0F)), entity.getId(), off, false, true, bind.caster(), FxLayer.Priority.CORE));
						if (grass == null) {
							break;
						}
					}
				}
				case FREEZE -> ModelFx.show(new ModelRequest("ice_block", entity.position(), Math.max(0.8F, Math.max(w, h * 0.6F) * 0.9F), 0.0F,
						0.0F, 0.0F, ticks + 2, List.of(AnimStep.of(Anim.GROW, 0, 5, 0.3F, 0.0F), AnimStep.of(Anim.SHATTER, ticks - 3, 3, 0.0F, 0.0F)),
						entity.getId(), Vec3.ZERO, false, true, bind.caster(), FxLayer.Priority.CORE));
				case ROPE -> {
					int n = EffectQuality.maximal() ? 6 : 4;
					for (int i = 0; i < n; i++) {
						ModelHandle link = ModelFx.show(new ModelRequest("chain_link_gold", entity.position(), 0.45F, i * 360.0F / n, 0.0F, 0.0F, ticks,
								List.of(AnimStep.of(Anim.ORBIT, 0, ticks, w * 0.6F + 0.15F, 120.0F)), entity.getId(), new Vec3(0.0D, h * 0.5D, 0.0D),
								false, true, bind.caster(), FxLayer.Priority.DETAIL));
						if (link == null) {
							break;
						}
					}
				}
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			if (warnings++ < 5) {
				Kultivator.LOGGER.warn("Kultivator: bind prop error", exception);
			}
		}
	}

	// ================================================================== the event view

	/** One TechFx event with the helpers the placements need. */
	private record Ev(Payloads.TechFx p, TechniqueSpec spec, Phase phase, @Nullable Entity caster) {

		Vec3 pos() {
			return new Vec3(p.x(), p.y(), p.z());
		}

		Vec3 to() {
			return new Vec3(p.tx(), p.ty(), p.tz());
		}

		boolean hasTo() {
			return to().distanceToSqr(pos()) > 0.04D;
		}

		int duration() {
			return Math.max(0, p.duration());
		}

		int casterId() {
			return p.caster();
		}

		boolean is(Phase other) {
			return phase == other;
		}

		boolean extra() {
			return phase.ordinal() >= Phase.EXTRA0.ordinal() && phase.ordinal() <= Phase.EXTRA7.ordinal();
		}

		int extraIndex() {
			return phase.ordinal() - Phase.EXTRA0.ordinal();
		}

		boolean preImpact() {
			return p.has(Payloads.TechFx.FLAG_PRE_IMPACT);
		}

		/** The ticks until a pre-impact visual lands (at least 4). */
		int fall() {
			return Math.max(4, p.duration() > 0 ? p.duration() : 12);
		}

		float yaw() {
			if (caster != null) {
				return caster.getYRot();
			}
			return hasTo() ? ModelFx.yawTo(pos(), to()) : 0.0F;
		}

		Vec3 forward() {
			float yaw = yaw() * Mth.DEG_TO_RAD;
			return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
		}

		Vec3 casterPos() {
			return caster != null ? caster.position() : pos();
		}

		/** Roughly where the caster's right hand is. */
		Vec3 hand() {
			if (caster == null) {
				return pos();
			}
			Vec3 eye = caster.getEyePosition();
			Vec3 look = caster.getLookAngle();
			float yaw = caster.getYRot() * Mth.DEG_TO_RAD;
			Vec3 right = new Vec3(-Mth.cos(yaw), 0.0D, -Mth.sin(yaw));
			return eye.add(0.0D, -0.35D, 0.0D).add(right.scale(0.35D)).add(look.scale(0.5D));
		}

		float scaleBy(float base) {
			float tier = 1.0F + 0.12F * Math.max(0, p.gradeTier() - 2);
			return base * tier * (EffectQuality.minimal() ? 0.8F : 1.0F);
		}
	}

	/** True when the spec draws model {@code id} with a layer of its own (then that layer places it). */
	private static boolean authored(TechniqueSpec spec, String id) {
		for (List<FxLayer> layers : spec.fx().values()) {
			for (FxLayer layer : layers) {
				if (layer.hasModel() && (layer.model().equals(id) || layer.model().startsWith(id + "_") || id.startsWith(layer.model() + "_"))) {
					return true;
				}
			}
		}
		return false;
	}

	// ================================================================== placement helpers

	private static @Nullable ModelHandle show(Ev ev, String id, Vec3 at, float scale, float yaw, float pitch, int ticks, AnimStep... anims) {
		if (authored(ev.spec(), id)) {
			return null;
		}
		ModelHandle handle = ModelFx.show(new ModelRequest(id, at, scale, yaw, pitch, 0.0F, ticks, List.of(anims), -1, Vec3.ZERO, false,
				FxModels.info(id).glow(), ev.casterId(), FxLayer.Priority.CORE));
		remember(ev, handle);
		return handle;
	}

	/** A prop riding the caster at {@code offset} (x left, y up, z forward, turned with the body). */
	private static @Nullable ModelHandle onCaster(Ev ev, String id, Vec3 offset, float scale, float yawOffset, float pitch, int ticks,
			AnimStep... anims) {
		if (ev.caster() == null || authored(ev.spec(), id)) {
			return null;
		}
		ModelHandle handle = ModelFx.show(new ModelRequest(id, ev.casterPos(), scale, yawOffset, pitch, 0.0F, ticks, List.of(anims),
				ev.caster().getId(), offset, true, FxModels.info(id).glow(), ev.casterId(), FxLayer.Priority.CORE));
		remember(ev, handle);
		return handle;
	}

	private static void remember(Ev ev, @Nullable ModelHandle handle) {
		if (handle == null) {
			return;
		}
		List<ModelHandle> list = CAST_PROPS.computeIfAbsent(ev.casterId(), id -> new ArrayList<>(4));
		list.removeIf(h -> !h.alive());
		list.add(handle);
	}

	private static List<ModelHandle> live(int casterId) {
		List<ModelHandle> list = CAST_PROPS.get(casterId);
		if (list == null) {
			return List.of();
		}
		list.removeIf(h -> !h.alive());
		return list;
	}

	private static @Nullable ModelFx.Prop prop(Ev ev, String id) {
		return ModelFx.find(ev.casterId(), id);
	}

	private static void endCast(int casterId) {
		for (ModelHandle handle : live(casterId)) {
			handle.end(6);
		}
		CAST_PROPS.remove(casterId);
		FireUltimates.abort(casterId);
	}

	private static AnimStep step(Anim kind, int start, int duration, float a, float b) {
		return AnimStep.of(kind, start, duration, a, b);
	}

	private static AnimStep slide(int start, int duration, Vec3 to) {
		return new AnimStep(Anim.SLIDE, start, duration, 0.0F, 1.0F, to);
	}

	private static AnimStep slideLinear(int start, int duration, Vec3 to) {
		return new AnimStep(Anim.SLIDE, start, duration, 0.0F, 0.0F, to);
	}

	/** Height to lift a model of {@code id} so its lowest point rests on the ground at {@code scale}. */
	private static double ground(String id, float scale) {
		return -FxModels.info(id).baseY() * scale;
	}

	/** Raises a blade-down sword so its tip touches the ground (the tip is about 1.45 blocks from the grip at scale 1). */
	private static Vec3 tipDown(Vec3 ground, float scale) {
		return ground.add(0.0D, 1.45D * scale, 0.0D);
	}

	private static int ticksOr(Ev ev, int fallback, int max) {
		int d = ev.duration();
		return Mth.clamp(d > 0 ? d : fallback, 10, max);
	}

	// ================================================================== the placements (section 6 rows)

	private static void place(Ev ev) {
		Phase phase = ev.phase();
		boolean cast = phase == Phase.CAST;
		boolean charge = phase == Phase.CHARGE;
		boolean travel = phase == Phase.TRAVEL;
		boolean impact = phase == Phase.IMPACT;
		boolean end = phase == Phase.END;
		String id = ev.spec().id();
		switch (id) {
			// ------------------------------------------------------------ universal
			case "mysterious_jade_hand" -> {
				if (cast) {
					onCaster(ev, "jade_palm", new Vec3(0.0D, 1.25D, 0.85D), ev.scaleBy(1.3F), 0.0F, 0.0F, ticksOr(ev, 100, 600),
							step(Anim.GROW, 0, 8, 0.2F, 0.0F), step(Anim.BOB, 0, 600, 0.05F, 40.0F));
				} else if (ev.extra()) {
					ModelFx.Prop palm = prop(ev, "jade_palm");
					if (palm != null) {
						Vec3 caught = ev.pos();
						palm.rotateTo(ModelFx.yawTo(ev.casterPos(), caught) - (ev.caster() instanceof LivingEntity l ? l.yBodyRot : 0.0F),
								ModelFx.pitchTo(ev.casterPos().add(0.0D, 1.25D, 0.0D), caught), 0.0F, 3);
						palm.play(step(Anim.PULSE, 0, 8, 0.25F, 0.0F));
					}
				}
			}
			case "sleep_insects" -> {
				if (cast && !EffectQuality.minimal()) {
					int n = EffectQuality.maximal() ? 6 : 4;
					Vec3 target = ev.hasTo() ? ev.to() : ev.pos().add(ev.forward().scale(6.0D));
					for (int i = 0; i < n; i++) {
						double a = Math.PI * 2.0D * i / n;
						Vec3 jitter = new Vec3(Math.cos(a) * 0.8D, 0.6D + 0.3D * Math.sin(a * 2.0D), Math.sin(a) * 0.8D);
						show(ev, "sleep_bug", ev.hand(), 0.45F, ev.yaw(), 0.0F, 40, slide(i * 2, 22, target.add(jitter)),
								step(Anim.BOB, 0, 40, 0.15F, 8.0F + i), step(Anim.PULSE, 0, 40, 0.3F, 0.0F), step(Anim.DISSOLVE, 30, 10, 0.0F, 0.0F));
					}
				}
			}
			case "somersault_cloud", "cloud_travel" -> {
				if (cast) {
					if (prop(ev, "nimbus_cloud") == null) {
						onCaster(ev, "nimbus_cloud", new Vec3(0.0D, -0.35D, 0.0D), ev.scaleBy(1.5F), 0.0F, 0.0F, ticksOr(ev, 200, 1200),
								step(Anim.GROW, 0, 8, 0.2F, 0.0F), step(Anim.BOB, 0, 1200, 0.06F, 30.0F));
					}
				} else if (end) {
					ModelFx.Prop cloud = prop(ev, "nimbus_cloud");
					if (cloud != null) {
						cloud.play(step(Anim.DISSOLVE, 0, 10, 0.0F, 0.0F));
					}
				}
			}
			case "qilin_sacred_palace" -> {
				if (cast) {
					Vec3 at = ev.pos();
					int life = ticksOr(ev, 200, 1200);
					show(ev, "qilin_palace", at, ev.scaleBy(Mth.clamp(ev.p().radius() * 0.45F, 3.0F, 8.0F)), ev.yaw(), 0.0F, life,
							step(Anim.RISE, 0, 16, 4.0F, 0.0F), step(Anim.SHRINK, life - 10, 10, 0.0F, 0.0F));
					Vec3 behind = at.subtract(ev.forward().scale(2.5D)).add(0.0D, 2.0D, 0.0D);
					show(ev, "qilin", behind, ev.scaleBy(4.0F), ev.yaw(), 0.0F, 60, step(Anim.GROW, 0, 12, 0.2F, 0.0F),
							step(Anim.PULSE, 12, 16, 0.12F, 0.0F), step(Anim.DISSOLVE, 50, 10, 0.0F, 0.0F));
				} else if (ev.extra()) {
					ModelFx.Prop palace = prop(ev, "qilin_palace");
					if (palace != null) {
						palace.play(step(Anim.PULSE, 0, 6, 0.05F, 0.0F));
					}
				}
			}
			case "moon_star_restoration" -> {
				if (cast) {
					onCaster(ev, "crescent_moon", new Vec3(0.0D, 1.5D, -0.7D), ev.scaleBy(2.2F), 180.0F, 0.0F, ticksOr(ev, 80, 400),
							step(Anim.GROW, 0, 10, 0.2F, 0.0F), step(Anim.PULSE, 10, 400, 0.05F, 0.0F));
				} else if (ev.extra() || phase == Phase.TRAVEL) {
					ModelFx.Prop moon = prop(ev, "crescent_moon");
					if (moon != null) {
						moon.play(step(Anim.SHATTER, 0, 3, 0.0F, 0.0F));
					}
				}
			}
			case "profound_mystery_fusion" -> {
				if (cast || charge) {
					if (prop(ev, "fusion_orb") == null) {
						onCaster(ev, "fusion_orb", new Vec3(0.0D, 2.5D, 0.3D), ev.scaleBy(1.4F), 0.0F, 0.0F, ticksOr(ev, 40, 200),
								step(Anim.GROW, 0, 12, 0.1F, 0.0F), step(Anim.SPIN, 0, 400, 9.0F, 0.0F));
					}
				} else if (travel) {
					ModelFx.Prop orb = prop(ev, "fusion_orb");
					if (orb != null) {
						orb.end(2);
					}
					int fly = Math.max(6, Math.min(20, ev.duration()));
					show(ev, "fusion_orb", ev.casterPos().add(0.0D, 2.5D, 0.0D), ev.scaleBy(1.4F), ev.yaw(), 0.0F, fly + 3,
							slide(0, fly, ev.to()), step(Anim.SPIN, 0, fly, 14.0F, 0.0F), step(Anim.SHATTER, fly, 3, 0.0F, 0.0F));
				}
			}
			case "buddha_wrath_tang_lotus" -> {
				if (cast || charge) {
					if (prop(ev, "tang_lotus") == null) {
						onCaster(ev, "tang_lotus", new Vec3(0.0D, 2.6D, 0.2D), ev.scaleBy(1.4F), 0.0F, 0.0F, ticksOr(ev, 40, 200),
								step(Anim.GROW, 0, 14, 0.1F, 0.0F), step(Anim.SPIN, 0, 400, 3.0F, 0.0F));
					}
				} else if (travel) {
					ModelFx.Prop held = prop(ev, "tang_lotus");
					if (held != null) {
						held.end(2);
					}
					int fly = Math.max(8, Math.min(24, ev.duration()));
					show(ev, "tang_lotus", ev.casterPos().add(0.0D, 2.6D, 0.0D), ev.scaleBy(1.6F), ev.yaw(), 0.0F, fly + 8,
							slide(0, fly, ev.hasTo() ? ev.to() : ev.pos()), step(Anim.SPIN, 0, fly + 8, 8.0F, 0.0F),
							step(Anim.OPEN, fly - 4, 6, 0.0F, 0.0F), step(Anim.SHATTER, fly + 4, 3, 0.0F, 0.0F));
				}
			}
			case "life_death_hex" -> {
				if (cast) {
					int life = ticksOr(ev, 60, 400);
					show(ev, "hex_disc", ev.pos().add(0.0D, 0.15D, 0.0D), ev.scaleBy(Mth.clamp(ev.p().radius() * 0.5F, 2.0F, 8.0F)), 0.0F, 0.0F, life,
							step(Anim.GROW, 0, 10, 0.1F, 0.0F), step(Anim.SPIN, 0, life, 4.0F, 0.0F), step(Anim.SHRINK, life - 8, 8, 0.0F, 0.0F));
				}
			}
			case "ruyi_staff" -> {
				if (cast || charge) {
					if (prop(ev, "ruyi_staff") == null) {
						int hold = ticksOr(ev, 30, 200);
						onCaster(ev, "ruyi_staff", new Vec3(-0.35D, 2.0D, 0.25D), ev.scaleBy(1.2F), 0.0F, 0.0F, hold + 20,
								step(Anim.GROW, 0, hold, 0.15F, 0.0F));
					}
				} else if (travel || impact) {
					ModelFx.Prop held = prop(ev, "ruyi_staff");
					if (held != null) {
						held.end(2);
					}
					Vec3 from = ev.casterPos();
					Vec3 target = ev.hasTo() ? ev.to() : ev.pos().add(ev.forward().scale(16.0D));
					double length = Math.max(4.0D, Math.min(40.0D, horizontal(from, target)));
					float scale = (float) Mth.clamp(length / 2.5D, 1.5D, 16.0D);
					Vec3 mid = from.add(target.subtract(from).scale(0.5D)).add(0.0D, 0.25D * scale, 0.0D);
					float yaw = ModelFx.yawTo(from, target);
					show(ev, "ruyi_staff", mid, scale, yaw, 90.0F, 30, step(Anim.FALL, 0, 6, 8.0F, 0.0F), step(Anim.STAMP, 6, 2, 0.0F, 0.0F),
							step(Anim.DISSOLVE, 20, 10, 0.0F, 0.0F));
				}
			}
			case "three_life_saving_hairs" -> {
				if (cast) {
					int life = ticksOr(ev, 600, 12000);
					for (int i = 0; i < 3; i++) {
						onCaster(ev, "golden_hair", new Vec3(0.0D, 2.05D, 0.0D), 0.45F, i * 120.0F, 0.0F, life,
								step(Anim.ORBIT, 0, life, 0.55F, 70.0F), step(Anim.SPIN, 0, life, 3.0F, 0.0F));
					}
				} else if (ev.extra()) {
					ModelFx.Prop hair = prop(ev, "golden_hair");
					if (hair != null) {
						hair.play(step(Anim.SHATTER, 0, 3, 0.0F, 0.0F));
					}
				}
			}
			case "myriad_cauldron" -> {
				if (travel && ev.preImpact() || cast && ev.duration() > 0 && ev.hasTo() && ev.preImpact()) {
					int fall = ev.fall();
					Vec3 land = ev.to();
					float scale = ev.scaleBy(Mth.clamp(ev.p().radius() * 0.6F, 3.0F, 8.0F));
					show(ev, "mother_qi_cauldron", land.add(0.0D, ground("mother_qi_cauldron", scale), 0.0D), scale, ev.yaw(), 0.0F, fall + 40,
							step(Anim.FALL, 0, fall, 14.0F, 0.0F), step(Anim.SPIN, 0, fall, 2.0F, 0.0F), step(Anim.DISSOLVE, fall + 30, 10, 0.0F, 0.0F));
				}
			}
			case "six_dao_reincarnation" -> {
				if (cast) {
					onCaster(ev, "six_path_wheel", new Vec3(0.0D, 2.3D, -1.2D), ev.scaleBy(3.0F), 180.0F, 0.0F, ticksOr(ev, 100, 600),
							step(Anim.GROW, 0, 12, 0.1F, 0.0F), step(Anim.SPIN, 0, 1200, 3.0F, 2.0F));
				} else if (ev.extra() || impact) {
					ModelFx.Prop wheel = prop(ev, "six_path_wheel");
					if (wheel != null) {
						wheel.play(step(Anim.PULSE, 0, 8, 0.12F, 0.0F));
					}
				}
			}
			case "heavenly_fate_finger" -> {
				if ((ev.extra() || travel) && ev.hasTo()) {
					int fall = ev.fall();
					float scale = ev.scaleBy(6.0F);
					show(ev, "fate_finger", ev.to().add(0.0D, 1.15D * scale, 0.0D), scale, ev.yaw(), 0.0F, fall + 30,
							step(Anim.FALL, 0, fall, 40.0F, 0.0F), step(Anim.DISSOLVE, fall + 20, 10, 0.0F, 0.0F));
				}
			}
			// ------------------------------------------------------------ sword
			case "heavenly_wolf_slash" -> {
				if (travel || cast && ev.p().has(Payloads.TechFx.FLAG_BLINK)) {
					int run = Math.max(6, Math.min(16, ev.duration()));
					Vec3 from = ev.pos();
					Vec3 target = ev.hasTo() ? ev.to() : from.add(ev.forward().scale(10.0D));
					show(ev, "sky_wolf", from.add(0.0D, 0.9D, 0.0D), ev.scaleBy(2.6F), ModelFx.yawTo(from, target), 0.0F, run + 10,
							slideLinear(0, run, target.add(0.0D, 0.9D, 0.0D)), step(Anim.DISSOLVE, run, 10, 0.0F, 0.0F));
				}
			}
			case "giant_sword_light" -> {
				if (charge || cast && ev.spec().charge() != null) {
					if (prop(ev, "giant_sword") == null) {
						onCaster(ev, "giant_sword", new Vec3(0.0D, 1.2D, -0.6D), ev.scaleBy(6.0F), 0.0F, 0.0F, ticksOr(ev, 40, 300),
								step(Anim.GROW, 0, 14, 0.1F, 0.0F), step(Anim.BOB, 14, 300, 0.08F, 30.0F));
					}
				} else if (travel) {
					swingSword(ev, "giant_sword", 6.0F);
				}
			}
			case "heavenbreaker_stance" -> {
				if (charge || cast && ev.spec().charge() != null) {
					if (prop(ev, "giant_sword") == null) {
						onCaster(ev, "giant_sword", new Vec3(0.0D, 2.4D, 0.0D), ev.scaleBy(5.0F), 0.0F, 0.0F, ticksOr(ev, 40, 300),
								step(Anim.GROW, 0, 14, 0.1F, 0.0F));
					}
				} else if (travel) {
					swingSword(ev, "giant_sword", 7.0F);
				}
			}
			case "bloodmoon_immortal_slaying_sword" -> {
				if (travel && ev.preImpact() || impact && !EffectQuality.minimal() && ev.duration() == 0) {
					plungeSword(ev, "bloodmoon_sword", ev.scaleBy(4.5F), ev.preImpact() ? ev.fall() : 6, travel ? ev.to() : ev.pos(), 14.0F);
				}
			}
			case "overlord_fury" -> {
				if (phase.name().equals("LAND") || end || impact) {
					if (prop(ev, "giant_sword") == null) {
						plungeSword(ev, "giant_sword", ev.scaleBy(5.0F), 4, impact ? ev.pos() : ev.casterPos().add(ev.forward().scale(1.5D)), 8.0F);
					}
				}
			}
			case "lu_ya_gourd" -> {
				if (cast) {
					onCaster(ev, "lu_ya_gourd", new Vec3(-0.45D, 2.3D, 0.15D), ev.scaleBy(0.9F), 0.0F, -20.0F, 40,
							step(Anim.GROW, 0, 6, 0.2F, 0.0F), step(Anim.PULSE, 8, 30, 0.06F, 0.0F));
				}
			}
			case "astral_sword_intent" -> {
				if (travel && ev.hasTo()) {
					plungeSword(ev, "star_sword", ev.scaleBy(1.8F), ev.fall(), ev.to(), 12.0F);
				}
			}
			case "zhuxian_sword_formation" -> {
				if ((impact || cast) && ev.p().of() > 1 && ev.p().index() >= 0) {
					String[] swords = {"zhuxian_sword", "luxian_sword", "xianxian_sword", "juexian_sword"};
					String sword = swords[Math.floorMod(ev.p().index(), 4)];
					float scale = ev.scaleBy(6.0F);
					show(ev, sword, ev.pos().add(0.0D, 1.0D * scale, 0.0D), scale, ModelFx.yawTo(ev.pos(), ev.casterPos()), 180.0F, ticksOr(ev, 240, 600),
							step(Anim.FALL, 0, 10, 16.0F, 0.0F), step(Anim.BOB, 10, 600, 0.25F, 50.0F));
				} else if (ev.extra()) {
					for (ModelHandle handle : live(ev.casterId())) {
						handle.play(slide(0, 8, ev.pos()));
						handle.play(step(Anim.SHATTER, 8, 3, 0.0F, 0.0F));
					}
				}
			}
			case "heaven_punishing_sword" -> {
				if (cast) {
					Vec3 target = ev.hasTo() ? ev.to() : ev.pos();
					float scale = ev.scaleBy(EffectQuality.maximal() ? 20.0F : 14.0F);
					int fall = Math.max(40, Math.min(80, ev.duration() > 0 ? ev.duration() : 60));
					plungeSword(ev, "heaven_punishing_sword", scale, fall, target, 40.0F);
				}
			}
			case "grass_symbol_sword" -> {
				if (travel && ev.hasTo()) {
					int fly = Math.max(6, Math.min(16, ev.duration()));
					show(ev, "grass_blade_sword", ev.pos(), ev.scaleBy(1.5F), ev.yaw(), 0.0F, fly + 4, slideLinear(0, fly, ev.to()),
							step(Anim.SHATTER, fly, 3, 0.0F, 0.0F));
				}
			}
			// ------------------------------------------------------------ fire
			case "flame_splitting_tsunami" -> {
				if (travel) {
					int run = Math.max(8, Math.min(30, ev.duration()));
					Vec3 target = ev.hasTo() ? ev.to() : ev.pos().add(ev.forward().scale(16.0D));
					show(ev, "heavy_ruler", ev.pos().add(0.0D, 2.0D, 0.0D), ev.scaleBy(3.5F), ModelFx.yawTo(ev.pos(), target), 0.0F, run + 8,
							slideLinear(0, run, target.add(0.0D, 2.0D, 0.0D)), step(Anim.DISSOLVE, run, 8, 0.0F, 0.0F));
				}
			}
			case "ten_thousand_crow_vase" -> {
				if (cast) {
					onCaster(ev, "crow_vase", new Vec3(-0.4D, 2.2D, 0.3D), ev.scaleBy(1.2F), 0.0F, 35.0F, 40, step(Anim.GROW, 0, 6, 0.2F, 0.0F),
							step(Anim.PULSE, 6, 30, 0.08F, 0.0F));
				}
			}
			case "phoenix_sky_roaring_strike", "phoenix_flame_sears_heavens" -> {
				if (cast && "phoenix_sky_roaring_strike".equals(id) || travel && "phoenix_flame_sears_heavens".equals(id)) {
					Vec3 from = ev.casterPos().add(0.0D, 3.5D, 0.0D).subtract(ev.forward().scale(2.0D));
					Vec3 target = ev.hasTo() ? ev.to() : ev.casterPos().add(ev.forward().scale(14.0D));
					int fly = Math.max(10, Math.min(30, ev.duration() > 0 ? ev.duration() : 16));
					show(ev, "fire_phoenix", from, ev.scaleBy(EffectQuality.maximal() ? 7.0F : 5.0F), ModelFx.yawTo(from, target), 0.0F, fly + 10,
							step(Anim.GROW, 0, 6, 0.3F, 0.0F), slide(4, fly, target.add(0.0D, 1.0D, 0.0D)), step(Anim.DISSOLVE, fly + 2, 8, 0.0F, 0.0F));
				}
			}
			case "eight_trigrams_furnace" -> {
				if (cast) {
					int life = ticksOr(ev, 100, 600);
					float scale = ev.scaleBy(Mth.clamp(ev.p().radius() * 0.7F, 4.0F, 10.0F));
					show(ev, "bagua_furnace", ev.pos(), scale, ev.yaw(), 0.0F, life, step(Anim.RISE, 0, 14, 5.0F, 0.0F),
							step(Anim.SPIN, 14, life, 0.8F, 0.0F), step(Anim.SHRINK, life - 10, 10, 0.0F, 0.0F));
				}
			}
			case "nine_suns_heavens_fury" -> {
				// FireUltimates: nine suns kindle in the sky during the charge, each streaks down onto its dense point,
				// flattens into a flash ring and leaves a crater glow; EXTRA0 is the great strike
				float sun = ev.scaleBy(5.0F);
				if (charge) {
					Vec3 aim = ev.hasTo() ? ev.to() : ev.casterPos().add(ev.forward().scale(16.0D));
					FireUltimates.sunsCharge(ev.casterId(), aim, ev.duration() > 0 ? ev.duration() : 60, sun);
				} else if (travel && ev.preImpact() && ev.hasTo()) {
					FireUltimates.sunsFall(ev.casterId(), ev.to(), ev.fall(), sun);
				} else if (impact) {
					FireUltimates.sunsImpact(ev.casterId(), ev.pos(), ev.p().radius(), sun);
				} else if (phase == Phase.EXTRA0) {
					FireUltimates.sunsGrand(ev.casterId(), ev.pos(), ev.p().radius(), sun);
				}
			}
			case "emperor_flame" -> {
				// FireUltimates: the glass lotus (bud, bloom, flame field, fold) at the field's centre; EXTRA0 is the nova
				if (cast) {
					FireUltimates.lotus(ev.casterId(), ev.pos(), ev.p().radius(), ev.duration());
				} else if (phase == Phase.PULSE) {
					FireUltimates.lotusPulse(ev.casterId());
				} else if (phase == Phase.EXTRA0) {
					FireUltimates.lotusNova(ev.casterId(), ev.pos());
				}
			}
			// ------------------------------------------------------------ ice
			case "cold_ice_formation" -> {
				if (travel && ev.preImpact() && ev.hasTo()) {
					int fall = ev.fall();
					float scale = ev.scaleBy(Mth.clamp(ev.p().radius() * 0.8F, 5.0F, 12.0F));
					show(ev, "ice_mountain", ev.to(), scale, ev.yaw(), 0.0F, fall + 40, step(Anim.FALL, 0, fall, 18.0F, 0.0F),
							step(Anim.SHATTER, fall + 30, 3, 0.0F, 0.0F));
				}
			}
			case "unparalleled_chill" -> {
				if (travel && ev.hasTo()) {
					int fly = Math.max(4, Math.min(16, ev.duration()));
					show(ev, "crystal_blade", ev.hand(), ev.scaleBy(2.4F), ev.yaw(), 0.0F, fly + 4, slideLinear(0, fly, ev.to()),
							step(Anim.SHATTER, fly, 3, 0.0F, 0.0F));
				}
			}
			case "ice_phoenix_frost_seal" -> {
				if ((cast || travel) && prop(ev, "ice_phoenix") == null) {
					Vec3 land = ev.hasTo() && travel ? ev.to() : ev.pos();
					int fly = Math.max(10, Math.min(30, ev.duration() > 0 ? ev.duration() : 18));
					Vec3 from = land.add(0.0D, 16.0D, 0.0D).subtract(ev.forward().scale(6.0D));
					show(ev, "ice_phoenix", from, ev.scaleBy(6.0F), ModelFx.yawTo(from, land), 0.0F, fly + 4, slide(0, fly, land.add(0.0D, 1.0D, 0.0D)),
							step(Anim.SHATTER, fly, 3, 0.0F, 0.0F));
				}
			}
			// ------------------------------------------------------------ lightning
			case "five_thunder_method" -> {
				if (cast) {
					onCaster(ev, "talisman_ling", new Vec3(-0.3D, 1.9D, 0.6D), ev.scaleBy(1.2F), 0.0F, 0.0F, 24,
							step(Anim.GROW, 0, 4, 0.2F, 0.0F), step(Anim.SPIN, 0, 24, 18.0F, 0.0F), step(Anim.DISSOLVE, 14, 10, 0.0F, 0.0F));
				}
			}
			case "lightning_emperor_art", "thunder_palm" -> {
				if (travel || cast && "thunder_palm".equals(id)) {
					Vec3 target = ev.hasTo() ? ev.to() : ev.pos();
					float scale = ev.scaleBy("thunder_palm".equals(id) ? 3.0F : 7.0F);
					show(ev, "thunder_palm", target.add(0.0D, 0.12D * scale, 0.0D), scale, ev.yaw(), 90.0F, 24,
							step(Anim.FALL, 0, 8, 10.0F, 0.0F), step(Anim.STAMP, 8, 2, 0.0F, 0.0F), step(Anim.DISSOLVE, 14, 10, 0.0F, 0.0F));
				}
			}
			case "golden_light_formation" -> {
				if ((cast || impact) && (ev.p().of() > 1 || cast)) {
					show(ev, "golden_mirror", ev.pos(), ev.scaleBy(1.8F), ModelFx.yawTo(ev.pos(), ev.casterPos()), 0.0F, ticksOr(ev, 200, 600),
							step(Anim.RISE, 0, 10, 1.5F, 0.0F), step(Anim.PULSE, 10, 600, 0.04F, 0.0F));
				}
			}
			case "divine_sword_imperial_thunder" -> {
				if (travel) {
					int fly = Math.max(6, Math.min(20, ev.duration()));
					Vec3 target = ev.hasTo() ? ev.to() : ev.pos().add(ev.forward().scale(20.0D));
					show(ev, "thunder_sword", ev.hand(), ev.scaleBy(3.0F), ev.yaw(), 0.0F, fly + 4, slideLinear(0, fly, target),
							step(Anim.SHATTER, fly, 3, 0.0F, 0.0F));
				}
			}
			case "tribulation_thunder_sword" -> {
				if ((ev.extra() || travel && ev.preImpact()) && ev.hasTo()) {
					plungeSword(ev, "thunder_sword", ev.scaleBy(5.0F), ev.preImpact() ? ev.fall() : 8, ev.to(), 20.0F);
				}
			}
			case "thunderbolt_ancestral_symbol" -> {
				if (cast) {
					onCaster(ev, "thunder_ancestral_symbol", new Vec3(0.0D, 6.5D, 0.0D), ev.scaleBy(4.0F), 180.0F, 0.0F, ticksOr(ev, 200, 1200),
							step(Anim.GROW, 0, 12, 0.1F, 0.0F), step(Anim.PULSE, 12, 1200, 0.06F, 0.0F));
				} else if (ev.extra()) {
					ModelFx.Prop symbol = prop(ev, "thunder_ancestral_symbol");
					if (symbol != null) {
						symbol.play(step(Anim.PULSE, 0, 6, 0.2F, 0.0F));
					}
				}
			}
			// ------------------------------------------------------------ body
			case "octane_blast" -> {
				if (phase == Phase.EXTRA2 || ev.extra() && ev.p().index() >= 7) {
					show(ev, "golden_fist", ev.hand(), ev.scaleBy(2.2F), ev.yaw(), ev.caster() == null ? 0.0F : ev.caster().getXRot(), 14,
							step(Anim.THRUST, 0, 12, 4.0F, 0.0F), step(Anim.SHATTER, 11, 3, 0.0F, 0.0F));
				}
			}
			case "undying_emperor_fist" -> {
				if (travel || cast) {
					show(ev, "emperor_fist", ev.hand(), ev.scaleBy(3.0F), ev.yaw(), ev.caster() == null ? 0.0F : ev.caster().getXRot(), 16,
							step(Anim.THRUST, 0, 14, 6.0F, 0.0F), step(Anim.SHATTER, 13, 3, 0.0F, 0.0F));
				}
			}
			case "six_paths_fist" -> {
				if (ev.extra() && ev.hasTo()) {
					String[] paths = {"deva", "human", "asura", "animal", "ghost", "hell"};
					String fist = "phantom_fist_" + paths[Math.floorMod(ev.extraIndex(), 6)];
					show(ev, fist, ev.hand(), ev.scaleBy(1.8F), ev.yaw(), 0.0F, 9, slideLinear(0, 6, ev.to()), step(Anim.SHATTER, 6, 3, 0.0F, 0.0F));
				}
			}
			case "great_way_of_buddha" -> {
				if (cast && prop(ev, "stupa") == null) {
					int life = ticksOr(ev, 200, 1200);
					show(ev, "stupa", ev.casterPos().subtract(ev.forward().scale(3.0D)), ev.scaleBy(3.5F), ev.yaw(), 0.0F, life,
							step(Anim.GROW, 0, 24, 0.1F, 0.0F), step(Anim.SHRINK, life - 12, 12, 0.0F, 0.0F));
				}
			}
			case "ancient_god_trample", "heaven_trampling" -> {
				if (ev.extra() || impact || phase.name().equals("LAND")) {
					Vec3 at = ev.hasTo() && ev.extra() ? ev.to() : ev.pos();
					float scale = ev.scaleBy("heaven_trampling".equals(id) ? 8.0F : 6.0F);
					show(ev, "god_foot", at, scale, ev.yaw(), 0.0F, 28, step(Anim.FALL, 0, 4, 12.0F, 0.0F), step(Anim.STAMP, 4, 2, 0.0F, 0.0F),
							step(Anim.DISSOLVE, 18, 10, 0.0F, 0.0F));
				}
			}
			// ------------------------------------------------------------ life
			case "seven_treasure_pagoda" -> {
				if (cast) {
					stackPagoda(ev, 1);
				} else if (ev.extra()) {
					stackPagoda(ev, Math.min(7, ev.extraIndex() + 2));
				}
			}
			case "gold_devouring_beetles" -> {
				if (phase == Phase.HIT && !EffectQuality.minimal()) {
					int n = EffectQuality.maximal() ? 4 : 2;
					for (int i = 0; i < n; i++) {
						show(ev, "gold_beetle", ev.pos(), 0.45F, i * 360.0F / n, 0.0F, 24, step(Anim.ORBIT, 0, 24, 0.5F, 240.0F),
								step(Anim.DISSOLVE, 16, 8, 0.0F, 0.0F));
					}
				}
			}
			case "willow_god_branches" -> {
				if (cast && prop(ev, "willow_god") == null) {
					int life = ticksOr(ev, 100, 600);
					show(ev, "willow_god", ev.casterPos().subtract(ev.forward().scale(3.0D)), ev.scaleBy(6.0F), ev.yaw(), 0.0F, life,
							step(Anim.GROW, 0, 15, 0.1F, 0.0F), step(Anim.SWING, 15, life, 4.0F, 60.0F), step(Anim.SHRINK, life - 10, 10, 0.0F, 0.0F));
				}
			}
			case "nezha_lotus_body" -> {
				if (cast && prop(ev, "lotus_pedestal") == null) {
					int life = ticksOr(ev, 800, 2400);
					onCaster(ev, "lotus_pedestal", new Vec3(0.0D, 0.02D, 0.0D), ev.scaleBy(1.6F), 0.0F, 0.0F, life,
							step(Anim.OPEN, 0, 14, 0.0F, 0.0F), step(Anim.SPIN, 0, life, 0.6F, 0.0F));
				} else if (ev.extra()) {
					ModelFx.Prop lotus = prop(ev, "lotus_pedestal");
					if (lotus != null) {
						lotus.play(step(Anim.PULSE, 0, 12, 0.25F, 0.0F));
					}
				}
			}
			case "guanyin_sweet_dew" -> {
				if (cast && prop(ev, "guanyin_vase") == null) {
					int life = ticksOr(ev, 160, 800);
					Vec3 at = ev.casterPos().add(0.0D, 8.0D, 0.0D).add(ev.forward().scale(4.0D));
					show(ev, "guanyin_vase", at, ev.scaleBy(3.0F), ev.yaw(), 0.0F, life, step(Anim.GROW, 0, 14, 0.1F, 0.0F),
							step(Anim.SWING, 14, life, 70.0F, 80.0F), step(Anim.SHRINK, life - 10, 10, 0.0F, 0.0F));
				}
			}
			case "bodhisattva_tree" -> {
				if (cast && prop(ev, "bodhi_tree") == null) {
					int life = ticksOr(ev, 300, 1200);
					show(ev, "bodhi_tree", ev.casterPos().subtract(ev.forward().scale(4.0D)), ev.scaleBy(EffectQuality.maximal() ? 14.0F : 9.0F), ev.yaw(),
							0.0F, life, step(Anim.GROW, 0, 40, 0.1F, 0.0F), step(Anim.SHRINK, life - 16, 16, 0.0F, 0.0F));
				} else if (ev.extra()) {
					ModelFx.Prop tree = prop(ev, "bodhi_tree");
					if (tree != null) {
						tree.play(step(Anim.PULSE, 0, 10, 0.04F, 0.0F));
					}
				}
			}
			// ------------------------------------------------------------ shadow and wind
			case "wraith_butterflies" -> {
				if (cast && !EffectQuality.minimal()) {
					int n = EffectQuality.maximal() ? 12 : 6;
					int life = ticksOr(ev, 200, 1200);
					for (int i = 0; i < n; i++) {
						ModelHandle b = onCaster(ev, "silver_butterfly", new Vec3(0.0D, 1.0D + (i % 3) * 0.35D, 0.0D), 0.5F, i * 360.0F / n, 0.0F, life,
								step(Anim.ORBIT, 0, life, 1.1F + (i % 2) * 0.6F, 80.0F + i * 6.0F), step(Anim.PULSE, 0, life, 0.35F, 0.0F),
								step(Anim.BOB, 0, life, 0.2F, 20.0F + i));
						if (b == null) {
							break;
						}
					}
				}
			}
			case "banana_leaf_fan" -> {
				if (cast || ev.extra()) {
					boolean back = ev.extra();
					onCaster(ev, "banana_fan", new Vec3(-0.45D, 1.35D, 0.45D), ev.scaleBy(1.8F), back ? 40.0F : -40.0F, 0.0F, 16,
							step(Anim.GROW, 0, 3, 0.4F, 0.0F), new AnimStep(Anim.SWING, 3, 7, back ? -140.0F : 140.0F, 0.0F, null),
							step(Anim.DISSOLVE, 10, 6, 0.0F, 0.0F));
				}
			}
			case "soul_falling_formation" -> {
				if (cast && prop(ev, "straw_figure") == null) {
					int life = ticksOr(ev, 200, 1200);
					show(ev, "straw_figure", ev.pos(), ev.scaleBy(1.6F), ModelFx.yawTo(ev.pos(), ev.casterPos()), 0.0F, life,
							step(Anim.RISE, 0, 10, 1.5F, 0.0F), step(Anim.SWING, 10, life, 6.0F, 50.0F));
				}
			}
			case "billion_soul_flag" -> {
				if (cast && prop(ev, "soul_flag") == null) {
					int life = ticksOr(ev, 200, 1200);
					show(ev, "soul_flag", ev.casterPos().add(ev.forward().scale(1.6D)), ev.scaleBy(3.0F), ev.yaw() + 90.0F, 0.0F, life,
							step(Anim.RISE, 0, 12, 3.0F, 0.0F), step(Anim.SWING, 12, life, 6.0F, 46.0F));
				}
			}
			case "stygian_tiger_seal" -> {
				if (cast) {
					onCaster(ev, "yin_tiger_seal", new Vec3(-0.3D, 1.9D, 0.55D), ev.scaleBy(0.9F), 0.0F, 0.0F, 40,
							step(Anim.GROW, 0, 6, 0.2F, 0.0F), step(Anim.SPIN, 0, 40, 3.0F, 0.0F), step(Anim.DISSOLVE, 30, 10, 0.0F, 0.0F));
				}
			}
			case "kunpeng_art" -> {
				if ((charge || cast && ev.spec().charge() != null) && prop(ev, "kunpeng") == null) {
					int life = ticksOr(ev, 40, 200) + 20;
					Vec3 at = ev.casterPos().add(0.0D, 11.0D, 0.0D).subtract(ev.forward().scale(4.0D));
					show(ev, "kunpeng", at, ev.scaleBy(EffectQuality.maximal() ? 18.0F : 10.0F), ev.yaw(), 0.0F, life,
							step(Anim.GROW, 0, 20, 0.2F, 0.0F), slideLinear(0, life, at.add(ev.forward().scale(10.0D))),
							step(Anim.DISSOLVE, life - 10, 10, 0.0F, 0.0F));
				}
			}
			// ------------------------------------------------------------ space
			case "qiankun_ring" -> {
				if ((travel || end) && ev.hasTo()) {
					int fly = Math.max(4, Math.min(14, ev.duration()));
					show(ev, "qiankun_ring", ev.pos(), ev.scaleBy(1.4F), ev.yaw(), 0.0F, fly + 2, slideLinear(0, fly, ev.to()),
							step(Anim.SPIN, 0, fly + 2, 24.0F, 0.0F));
				}
			}
			case "purple_gold_gourd" -> {
				if (cast && prop(ev, "zijin_gourd") == null) {
					onCaster(ev, "zijin_gourd", new Vec3(0.0D, 2.7D, 0.3D), ev.scaleBy(1.4F), 0.0F, 25.0F, ticksOr(ev, 60, 300),
							step(Anim.GROW, 0, 8, 0.2F, 0.0F), step(Anim.BOB, 8, 300, 0.08F, 30.0F));
				} else if (ev.extra()) {
					ModelFx.Prop gourd = prop(ev, "zijin_gourd");
					if (gourd != null) {
						gourd.play(step(Anim.PULSE, 0, 16, 0.18F, 0.0F));
						gourd.extend(30);
					}
				}
			}
			case "heaven_turning_seal" -> {
				if (travel && ev.preImpact() && ev.hasTo()) {
					int fall = ev.fall();
					float scale = ev.scaleBy(Mth.clamp(ev.p().radius() * 0.6F, 4.0F, 9.0F));
					show(ev, "fantian_seal", ev.to(), scale, ev.yaw(), 0.0F, fall + 30, step(Anim.FALL, 0, fall, 30.0F, 0.0F),
							step(Anim.STAMP, fall, 2, 0.0F, 0.0F), step(Anim.DISSOLVE, fall + 20, 10, 0.0F, 0.0F));
				}
			}
			case "spatial_ancestral_symbol", "space_fold" -> {
				if (cast && prop(ev, "space_symbol") == null) {
					int life = ticksOr(ev, 100, 600);
					Vec3 at = ev.pos().add(0.0D, 6.0D, 0.0D);
					show(ev, "space_symbol", at, ev.scaleBy(4.0F), ModelFx.yawTo(at, ev.casterPos()), 0.0F, life,
							step(Anim.GROW, 0, 12, 0.1F, 0.0F), step(Anim.SPIN, 0, life, 1.2F, 2.0F), step(Anim.SHRINK, life - 10, 10, 0.0F, 0.0F));
				}
			}
			case "yellow_brow_bag" -> {
				if (cast && prop(ev, "seed_bag") == null) {
					onCaster(ev, "seed_bag", new Vec3(-0.3D, 2.7D, 0.5D), ev.scaleBy(1.6F), 0.0F, 30.0F, ticksOr(ev, 160, 600),
							step(Anim.GROW, 0, 10, 0.2F, 0.0F), step(Anim.OPEN, 10, 10, 0.0F, 0.0F), step(Anim.PULSE, 20, 600, 0.06F, 0.0F));
				} else if (ev.extra() && !EffectQuality.minimal()) {
					Vec3 boss = ev.pos();
					show(ev, "cymbal", boss.add(0.0D, 3.0D, 0.0D), ev.scaleBy(2.6F), 0.0F, 0.0F, 24, step(Anim.FALL, 0, 6, -2.0F, 0.0F),
							step(Anim.DISSOLVE, 14, 10, 0.0F, 0.0F));
					show(ev, "cymbal", boss.add(0.0D, -0.2D, 0.0D), ev.scaleBy(2.6F), 0.0F, 180.0F, 24, step(Anim.RISE, 0, 6, 1.5F, 0.0F),
							step(Anim.DISSOLVE, 14, 10, 0.0F, 0.0F));
				}
			}
			case "void_mirror" -> {
				if (cast && prop(ev, "void_mirror") == null) {
					onCaster(ev, "void_mirror", new Vec3(0.0D, 1.4D, 2.0D), ev.scaleBy(2.6F), 0.0F, 0.0F, ticksOr(ev, 200, 1200),
							step(Anim.GROW, 0, 10, 0.1F, 0.0F));
				} else if (ev.extra()) {
					ModelFx.Prop mirror = prop(ev, "void_mirror");
					if (mirror != null) {
						mirror.play(step(Anim.PULSE, 0, 10, 0.12F, 0.0F));
					}
				}
			}
			case "time_spirit_domain", "time_bank" -> {
				if (cast && prop(ev, "time_clock") == null) {
					onCaster(ev, "time_clock", new Vec3(0.0D, 4.6D, 0.0D), ev.scaleBy(3.0F), 180.0F, 0.0F, ticksOr(ev, 200, 1200),
							step(Anim.GROW, 0, 14, 0.1F, 0.0F), step(Anim.BOB, 14, 1200, 0.12F, 60.0F));
				} else if (ev.extra()) {
					ModelFx.Prop clock = prop(ev, "time_clock");
					if (clock != null) {
						clock.play(step(Anim.PULSE, 0, 8, 0.1F, 0.0F));
					}
				}
			}
			case "stellar_black_hole" -> {
				if ((cast || charge) && prop(ev, "black_hole_core") == null) {
					Vec3 at = ev.hasTo() ? ev.to() : ev.pos();
					int life = ticksOr(ev, 100, 600);
					float scale = ev.scaleBy(Mth.clamp(ev.p().radius() * 0.25F, 2.5F, 6.0F));
					show(ev, "black_hole_core", at, scale, 0.0F, 0.0F, life, step(Anim.GROW, 0, 16, 0.05F, 0.0F), step(Anim.SPIN, 0, life, 2.0F, 0.0F));
					show(ev, "accretion_disk", at, scale * 1.1F, 0.0F, 12.0F, life, step(Anim.GROW, 0, 16, 0.05F, 0.0F),
							step(Anim.SPIN, 0, life, 6.0F, 0.0F));
				} else if (ev.extra()) {
					for (ModelHandle handle : live(ev.casterId())) {
						handle.play(step(Anim.SHRINK, 0, 8, 0.0F, 0.0F));
					}
				}
			}
			// ------------------------------------------------------------ immortal
			case "five_elements_mountain" -> {
				if (travel && ev.preImpact() && ev.hasTo()) {
					int fall = Math.max(20, ev.fall());
					float scale = ev.scaleBy(EffectQuality.maximal() ? 20.0F : 12.0F);
					show(ev, "buddha_palm", ev.to().add(0.0D, 0.12D * scale, 0.0D), scale, ev.yaw(), 90.0F, fall + 30,
							step(Anim.FALL, 0, fall, 48.0F, 0.0F), step(Anim.STAMP, fall, 2, 0.0F, 0.0F), step(Anim.DISSOLVE, fall + 20, 10, 0.0F, 0.0F));
				} else if (impact && ev.p().of() > 1 && ev.p().index() >= 0) {
					String[] peaks = {"mountain_peak_metal", "mountain_peak_wood", "mountain_peak_water", "mountain_peak_fire", "mountain_peak_earth"};
					String peak = peaks[Math.floorMod(ev.p().index(), 5)];
					show(ev, peak, ev.pos(), ev.scaleBy(5.0F), ev.yaw() + ev.p().index() * 30.0F, 0.0F, 200, step(Anim.RISE, 0, 12, 4.0F, 0.0F),
							step(Anim.SHRINK, 188, 12, 0.0F, 0.0F));
				}
			}
			case "tai_chi_diagram" -> {
				if (travel && ev.hasTo()) {
					Vec3 mid = ev.pos().add(ev.to().subtract(ev.pos()).scale(0.5D));
					float scale = (float) Mth.clamp(ev.pos().distanceTo(ev.to()) * 0.7D, 4.0D, 16.0D);
					show(ev, "taiji_disc", mid.add(0.0D, 0.2D, 0.0D), ev.scaleBy(scale), ModelFx.yawTo(ev.pos(), ev.to()), 0.0F, 40,
							step(Anim.OPEN, 0, 10, 0.0F, 0.0F), step(Anim.FALL, 0, 12, 20.0F, 0.0F), step(Anim.SPIN, 0, 40, 4.0F, 0.0F),
							step(Anim.SHRINK, 30, 10, 0.0F, 0.0F));
				}
			}
			// ------------------------------------------------------------ legacy techniques
			case "buddha_palm" -> {
				if (cast || impact) {
					Vec3 at = ev.hasTo() ? ev.to() : ev.pos();
					float scale = ev.scaleBy(6.0F);
					show(ev, "buddha_palm", at.add(0.0D, 0.12D * scale, 0.0D), scale, ev.yaw(), 90.0F, 30, step(Anim.FALL, 0, 12, 18.0F, 0.0F),
							step(Anim.STAMP, 12, 2, 0.0F, 0.0F), step(Anim.DISSOLVE, 20, 10, 0.0F, 0.0F));
				}
			}
			case "thousand_swords" -> {
				if (impact || phase == Phase.HIT && EffectQuality.maximal()) {
					plungeSword(ev, "light_sword", ev.scaleBy(2.2F), 6, ev.pos(), 10.0F);
				}
			}
			case "cloud_step" -> {
				if (cast) {
					show(ev, "cloud_puff", ev.casterPos().add(0.0D, -0.1D, 0.0D), 0.9F, ev.yaw(), 0.0F, 16, step(Anim.GROW, 0, 4, 0.3F, 0.0F),
							step(Anim.DISSOLVE, 6, 10, 0.0F, 0.0F));
				}
			}
			case "qi_palm", "flame_palm" -> {
				if (cast) {
					Dao dao = ev.spec().dao();
					String palm = "flame_palm".equals(id) ? "qi_palm_fire" : dao == null ? "qi_palm" : "qi_palm_" + dao.id();
					show(ev, palm, ev.hand(), ev.scaleBy(1.4F), ev.yaw(), ev.caster() == null ? 0.0F : ev.caster().getXRot(), 14,
							step(Anim.THRUST, 0, 12, 3.0F, 0.0F), step(Anim.SHATTER, 11, 3, 0.0F, 0.0F));
				}
			}
			case "frost_lotus" -> {
				if (cast) {
					Vec3 at = ev.hasTo() ? ev.to() : ev.pos();
					show(ev, "ice_lotus", at, ev.scaleBy(1.6F), ev.yaw(), 0.0F, 34, step(Anim.OPEN, 0, 10, 0.0F, 0.0F),
							step(Anim.SPIN, 0, 34, 2.0F, 0.0F), step(Anim.SHATTER, 30, 3, 0.0F, 0.0F));
				}
			}
			default -> {
			}
		}
	}

	/** A giant sword swung down along the caster's front (raised behind, then cleaving through 120 degrees). */
	private static void swingSword(Ev ev, String sword, float scale) {
		ModelFx.Prop held = prop(ev, sword);
		if (held != null) {
			held.end(2);
		}
		Vec3 at = ev.casterPos().add(ev.forward().scale(1.2D)).add(0.0D, 1.2D, 0.0D);
		show(ev, sword, at, ev.scaleBy(scale), ev.yaw(), 0.0F, 22, new AnimStep(Anim.SWING, 0, 6, 150.0F, 0.0F, null),
				step(Anim.DISSOLVE, 12, 10, 0.0F, 0.0F));
	}

	/** A sword falling tip first into {@code ground} over {@code fall} ticks from {@code height} blocks, then fading. */
	private static void plungeSword(Ev ev, String sword, float scale, int fall, Vec3 ground, float height) {
		show(ev, sword, tipDown(ground, scale), scale, ev.yaw(), 180.0F, fall + 24, step(Anim.FALL, 0, fall, height, 0.0F),
				step(Anim.STAMP, fall, 2, 0.0F, 0.0F), step(Anim.DISSOLVE, fall + 14, 10, 0.0F, 0.0F));
	}

	/** The seven-treasure pagoda: tier {@code tier} grows onto the stack above the raised hand. */
	private static void stackPagoda(Ev ev, int tier) {
		float scale = ev.scaleBy(1.1F);
		double y = 2.4D + (tier - 1) * 0.72D * scale;
		onCaster(ev, "pagoda_tier_" + tier, new Vec3(-0.25D, y, 0.25D), scale * (1.0F - 0.06F * (tier - 1)), 0.0F, 0.0F,
				ticksOr(ev, 120, 600), step(Anim.GROW, 0, 8, 0.2F, 0.0F), step(Anim.SPIN, 0, 600, 1.0F, 0.0F));
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = b.x - a.x, dz = b.z - a.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private ArtModels() {
	}
}
