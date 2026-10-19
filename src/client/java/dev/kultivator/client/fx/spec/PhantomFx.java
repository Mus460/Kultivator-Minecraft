package dev.kultivator.client.fx.spec;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.quad.CrescentQuad;
import dev.kultivator.client.fx.quad.GlowQuad;
import dev.kultivator.client.fx.quad.QuadFx;
import dev.kultivator.client.fx.quad.RingQuad;
import dev.kultivator.client.fx.quad.StreakQuad;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.run.PhantomKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Summoned phantoms made visible (polish S21): the server keeps phantoms virtual and syncs them with
 * {@code Payloads.PhantomSync} (spawn, every 10 ticks, empty at the end). This client keeps the latest entries per caster,
 * lerps each phantom between two syncs (or rides the entity it clings to) and draws it by its {@link PhantomKind}:
 * beasts and carts as the animation module's 3D models (flame cart, ice phoenix, ice scorpion), humanoid phantoms (clones,
 * thunder deities, emperors, the cosmic avatar, fierce corpses) as tinted translucent copies of the caster's own model,
 * with a particle aura for every kind and a particle body at Minimal. A {@code PHANTOM} event (pos = phantom, to = victim)
 * makes the phantom lunge and draws its strike: a slash arc, a flame jet, a frost lance or a bolt.
 */
public final class PhantomFx {

	private static final int SYNC_TICKS = 10;

	/** One synced phantom. */
	private static final class Phantom {
		final int slot;
		PhantomKind kind;
		Vec3 from;
		Vec3 to;
		float yawFrom;
		float yawTo;
		int cling;
		Vec3 clingOffset = Vec3.ZERO;
		int age;
		int ticksLeft;
		@Nullable ModelHandle model;
		boolean spawned;
		int lunge = -1;
		@Nullable Vec3 lungeTo;

		Phantom(int slot) {
			this.slot = slot;
		}

		Vec3 now(float partial) {
			if (cling >= 0) {
				Entity entity = QuadEntity.get(cling);
				if (entity != null) {
					return entity.position().add(clingOffset);
				}
			}
			float t = Math.min(1.0F, (age + partial) / SYNC_TICKS);
			Vec3 base = from.lerp(to, t);
			if (lunge >= 0 && lungeTo != null) {
				// 4 ticks out toward the victim, then back
				float l = lunge < 4 ? lunge / 4.0F : Math.max(0.0F, 1.0F - (lunge - 4) / 4.0F);
				Vec3 d = lungeTo.subtract(base);
				double reach = Math.min(2.0D, d.length());
				if (reach > 0.01D) {
					base = base.add(d.normalize().scale(reach * l));
				}
			}
			return base;
		}

		float yaw() {
			float t = Math.min(1.0F, age / (float) SYNC_TICKS);
			return yawFrom + Mth.wrapDegrees(yawTo - yawFrom) * t;
		}
	}

	/** The phantoms of one cast. */
	private static final class Cast {
		final int caster;
		final int spec;
		final Map<Integer, Phantom> phantoms = new HashMap<>();

		Cast(int caster, int spec) {
			this.caster = caster;
			this.spec = spec;
		}
	}

	private static final Map<Long, Cast> CASTS = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();

	/** A {@code PhantomSync} from the server (client thread). */
	public static void onSync(Payloads.PhantomSync sync) {
		long key = key(sync.caster(), sync.spec());
		if (sync.entries().isEmpty()) {
			Cast cast = CASTS.remove(key);
			if (cast != null) {
				for (Phantom phantom : cast.phantoms.values()) {
					vanish(cast, phantom);
				}
				AvatarFx.copies(cast.caster, List.of());
			}
			return;
		}
		Cast cast = CASTS.computeIfAbsent(key, k -> new Cast(sync.caster(), sync.spec()));
		List<Integer> seen = new ArrayList<>();
		for (Payloads.PhantomSync.Entry entry : sync.entries()) {
			if (!Float.isFinite(entry.x()) || !Float.isFinite(entry.y()) || !Float.isFinite(entry.z())) {
				continue;
			}
			int slot = entry.slot();
			seen.add(slot);
			Vec3 at = new Vec3(entry.x(), entry.y(), entry.z());
			Phantom phantom = cast.phantoms.get(slot);
			boolean fresh = phantom == null;
			if (fresh) {
				phantom = new Phantom(slot);
				phantom.from = at;
				phantom.yawFrom = entry.yaw();
				cast.phantoms.put(slot, phantom);
			} else {
				phantom.from = phantom.now(0.0F);
				phantom.yawFrom = phantom.yaw();
			}
			PhantomKind[] kinds = PhantomKind.values();
			phantom.kind = kinds[Math.max(0, Math.min(kinds.length - 1, entry.kind()))];
			phantom.to = at;
			phantom.yawTo = entry.yaw();
			phantom.age = 0;
			phantom.ticksLeft = Math.max(1, entry.ticksLeft());
			phantom.cling = entry.cling();
			if (phantom.cling >= 0) {
				Entity entity = QuadEntity.get(phantom.cling);
				phantom.clingOffset = entity == null ? Vec3.ZERO : at.subtract(entity.position());
			}
			if (fresh) {
				spawn(cast, phantom);
			}
		}
		for (Iterator<Map.Entry<Integer, Phantom>> it = cast.phantoms.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Phantom> e = it.next();
			if (!seen.contains(e.getKey())) {
				vanish(cast, e.getValue());
				it.remove();
			}
		}
	}

	/** A PHANTOM event (pos = the phantom, to = the victim, aux = the kind): the phantom lunges and strikes. */
	static void onPhantom(FxEvent ev) {
		PhantomKind[] kinds = PhantomKind.values();
		PhantomKind kind = kinds[Math.max(0, Math.min(kinds.length - 1, ev.aux))];
		Vec3 victim = ev.targetEntity() != null ? ev.targetEntity().position().add(0.0D, ev.targetEntity().getBbHeight() * 0.5D, 0.0D) : ev.target;
		Cast cast = CASTS.get(key(ev.casterId, ev.spec.netId()));
		Phantom nearest = null;
		if (cast != null) {
			double best = 9.0D;
			for (Phantom phantom : cast.phantoms.values()) {
				double d = phantom.now(0.0F).distanceToSqr(ev.pos);
				if (d < best) {
					best = d;
					nearest = phantom;
				}
			}
		}
		if (ev.has(Payloads.TechFx.FLAG_PRE_IMPACT)) {
			// a heavy phantom (the cosmic avatar's stomp) warns where it will strike: a ring closing on the victim, no strike
			warn(ev, kind, nearest, victim);
			return;
		}
		if (nearest != null) {
			nearest.lunge = 0;
			nearest.lungeTo = victim;
			if (nearest.model != null) {
				AnimBridge.play(nearest.model, AnimBridge.step(Anim.LUNGE, 0, 8, 2.0F, 0.0F, victim));
			}
		}
		int color = colorOf(kind);
		Vec3 from = ev.pos.add(0.0D, 1.0D, 0.0D);
		boolean quads = !EffectQuality.minimal() && ev.viewScale > 0.3F;
		switch (kind) {
			case FLAME_CART -> {
				if (quads) {
					QuadFx.add(new StreakQuad(from, victim, 0.6F, color, 0xFFF0B0, 3.0F, 3.0F, 0.0F, ev.seed, 9).caster(ev.casterId));
				}
				burst(victim, Qi.flame(color, 0.6F, 14), 10, 0.25D);
			}
			case ICE_PHOENIX, ICE_SCORPION, ICE_EMPEROR, SNOW_EMPRESS -> {
				if (quads) {
					QuadFx.add(new StreakQuad(from, victim, 0.4F, color, 0xFFFFFF, 2.0F, 2.0F, 0.0F, ev.seed, 8).caster(ev.casterId));
				}
				burst(victim, Qi.shard(color, 0.3F, 24), 12, 0.3D);
			}
			case THUNDER_DEITY -> {
				if (quads) {
					QuadFx.add(new dev.kultivator.client.fx.quad.BoltQuad(from.add(0.0D, 1.5D, 0.0D), victim, 0.25F, color, 0xF0E0FF, ev.seed, 2, 6)
							.caster(ev.casterId));
				}
				burst(victim, Qi.spark(color, 0.4F, 6), 10, 0.4D);
			}
			default -> {
				if (quads) {
					Vec3 dir = victim.subtract(from);
					QuadFx.add(new CrescentQuad(from.add(dir.scale(0.5D)), dir, false, 1.4F, 1.0F, 0.3F, 0.8F, color, 0xFFFFFF, 6).sweep(3.0F)
							.caster(ev.casterId));
				}
				burst(victim, Qi.mote(color, 0.3F, 10), 10, 0.3D);
			}
		}
		if (quads) {
			QuadFx.add(new GlowQuad(victim, 0.4F, 1.2F, color, 0.8F, 0.0F, 5).caster(ev.casterId));
		}
	}

	/** The warning of a PRE_IMPACT phantom event: the phantom rears up and a ring closes on the spot it will strike. */
	private static void warn(FxEvent ev, PhantomKind kind, Phantom nearest, Vec3 victim) {
		int color = colorOf(kind);
		int ticks = Math.max(4, ev.duration);
		if (nearest != null && nearest.model != null) {
			AnimBridge.play(nearest.model, AnimBridge.step(Anim.RISE, 0, ticks, 0.6F, 0.0F, null));
		}
		Vec3 ground = ev.targetEntity() != null ? ev.targetEntity().position().add(0.0D, 0.05D, 0.0D) : victim.add(0.0D, -0.9D, 0.0D);
		if (!EffectQuality.minimal() && ev.viewScale > 0.3F) {
			QuadFx.add(new RingQuad(ground, 3.2F, 0.8F, color, 0.55F, ticks).caster(ev.casterId));
			QuadFx.add(new RingQuad(ground, 1.2F, 1.0F, 0xFFFFFF, 0.35F, ticks).caster(ev.casterId));
		} else {
			burst(ground.add(0.0D, 0.2D, 0.0D), Qi.mote(color, 0.25F, ticks), 6, 0.6D);
		}
	}

	/** Once per client tick: lerp, draw, expire. */
	static void tick() {
		if (CASTS.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		for (Iterator<Cast> it = CASTS.values().iterator(); it.hasNext(); ) {
			Cast cast = it.next();
			List<AvatarFx.Copy> copies = new ArrayList<>();
			for (Iterator<Phantom> pit = cast.phantoms.values().iterator(); pit.hasNext(); ) {
				Phantom phantom = pit.next();
				phantom.age++;
				phantom.ticksLeft--;
				if (phantom.lunge >= 0 && ++phantom.lunge > 8) {
					phantom.lunge = -1;
				}
				if (phantom.ticksLeft < -SYNC_TICKS * 3) {
					// the server stopped syncing it
					vanish(cast, phantom);
					pit.remove();
					continue;
				}
				Vec3 at = phantom.now(0.0F);
				if (phantom.model != null) {
					AnimBridge.moveTo(phantom.model, at, 1);
					AnimBridge.rotateTo(phantom.model, phantom.yaw(), 0.0F, 0.0F, 1);
				} else if (humanoid(phantom.kind) && !EffectQuality.minimal()) {
					copies.add(new AvatarFx.Copy(at, phantom.yaw(), scaleOf(phantom.kind), colorOf(phantom.kind), alphaOf(phantom.kind)));
				}
				aura(phantom, at);
			}
			AvatarFx.copies(cast.caster, copies);
			if (cast.phantoms.isEmpty()) {
				it.remove();
			}
		}
	}

	private static void spawn(Cast cast, Phantom phantom) {
		Vec3 at = phantom.now(0.0F);
		int color = colorOf(phantom.kind);
		String model = modelOf(phantom.kind);
		if (model != null && AnimBridge.modelKnown(model) && !EffectQuality.minimal()) {
			phantom.model = AnimBridge.model(model, at, scaleOf(phantom.kind), phantom.yawTo, 0.0F, 0.0F, Math.max(20, phantom.ticksLeft + 40),
					List.of(AnimBridge.step(Anim.GROW, 0, 8, 0.2F, 0.0F, null)), -1, Vec3.ZERO, false, true, cast.caster,
					dev.kultivator.technique.spec.dsl.FxLayer.Priority.CORE);
		}
		// materialise: a flash, a ring and a puff in the phantom's colour (gold hair-puff for the hair clones)
		burst(at.add(0.0D, 1.0D, 0.0D), phantom.kind == PhantomKind.HAIR_CLONE ? Qi.glint(0xFFD54A, 0.4F, 14) : Qi.mote(color, 0.35F, 16), 16, 0.2D);
		add(Qi.ring(color, 0.4F, 2.4F, 10), at.add(0.0D, 1.0D, 0.0D), Vec3.ZERO);
		if (!EffectQuality.minimal()) {
			QuadFx.add(new GlowQuad(at.add(0.0D, 1.0D, 0.0D), 0.6F, 1.6F, color, 0.7F, 0.0F, 8).caster(cast.caster));
		}
		phantom.spawned = true;
	}

	private static void vanish(Cast cast, Phantom phantom) {
		Vec3 at = phantom.now(0.0F);
		if (phantom.model != null) {
			AnimBridge.play(phantom.model, AnimBridge.step(Anim.DISSOLVE, 0, 6, 0.0F, 0.0F, null));
			AnimBridge.end(phantom.model, 6);
			phantom.model = null;
		}
		burst(at.add(0.0D, 1.0D, 0.0D), Qi.smoke(Qi.lerp(colorOf(phantom.kind), 0x202020, 0.4F), 0.6F, 24), 10, 0.08D);
	}

	/** The kind's particle aura (and its whole body at Minimal / without a model). */
	private static void aura(Phantom phantom, Vec3 at) {
		int color = colorOf(phantom.kind);
		float k = EffectQuality.level().factor();
		boolean body = phantom.model == null && (EffectQuality.minimal() || !humanoid(phantom.kind));
		int n = Math.max(1, Math.round((body ? 6 : 2) * k));
		double height = 1.8D * scaleOf(phantom.kind);
		ParticleOptions particle = switch (phantom.kind) {
			case FLAME_CART -> Qi.flame(color, 0.4F, 12);
			case FIERCE_CORPSE -> Qi.smoke(0x2A3A20, 0.5F, 20);
			case ICE_PHOENIX, ICE_EMPEROR, SNOW_EMPRESS, ICE_SCORPION -> Qi.snow(color, 0.2F, 30);
			case THUNDER_DEITY -> Qi.spark(color, 0.3F, 5);
			case BLOOD_CLONE -> Qi.mote(0xC0182A, 0.18F, 18);
			default -> Qi.mote(color, 0.18F, 14);
		};
		for (int i = 0; i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
			double r = (body ? 0.35D : 0.5D) * scaleOf(phantom.kind);
			Vec3 p = at.add(Math.cos(angle) * r, RANDOM.nextDouble() * height, Math.sin(angle) * r);
			add(particle, p, new Vec3(0.0D, phantom.kind == PhantomKind.BLOOD_CLONE ? -0.04D : 0.02D, 0.0D));
		}
		if (phantom.kind == PhantomKind.FIERCE_CORPSE && RANDOM.nextFloat() < 0.15F * k) {
			add(ParticleTypes.SOUL_FIRE_FLAME, at.add(0.0D, 1.7D, 0.0D), Vec3.ZERO);
		}
	}

	private static boolean humanoid(PhantomKind kind) {
		return switch (kind) {
			case FLAME_CART, ICE_PHOENIX, ICE_SCORPION -> false;
			default -> true;
		};
	}

	private static @Nullable String modelOf(PhantomKind kind) {
		return switch (kind) {
			case FLAME_CART -> "flame_cart";
			case ICE_PHOENIX -> "ice_phoenix";
			case ICE_SCORPION -> "ice_scorpion";
			default -> null;
		};
	}

	static int colorOf(PhantomKind kind) {
		return switch (kind) {
			case FLAME_CART -> 0xFF7A2A;
			case FIERCE_CORPSE -> 0x7A9A6A;
			case ICE_PHOENIX -> 0x9FF3FF;
			case THUNDER_DEITY -> 0xB57CFF;
			case COSMIC_AVATAR -> 0xFFD54A;
			case ICE_EMPEROR -> 0xBFE8FF;
			case SNOW_EMPRESS -> 0xEAF6FF;
			case ICE_SCORPION -> 0x7FD8FF;
			case BLOOD_CLONE -> 0xD02838;
			case HAIR_CLONE -> 0xFFD54A;
			case GENERIC_CLONE -> 0xD8E6FF;
		};
	}

	private static float scaleOf(PhantomKind kind) {
		return switch (kind) {
			case HAIR_CLONE -> 0.4F;
			case THUNDER_DEITY -> 2.2F;
			case COSMIC_AVATAR -> 3.0F;
			case ICE_EMPEROR -> 1.6F;
			case SNOW_EMPRESS -> 1.2F;
			case ICE_PHOENIX -> 6.0F;
			case ICE_SCORPION -> 3.0F;
			case FLAME_CART -> 1.5F;
			default -> 1.0F;
		};
	}

	private static int alphaOf(PhantomKind kind) {
		return switch (kind) {
			case FIERCE_CORPSE -> 190;
			case BLOOD_CLONE -> 170;
			case THUNDER_DEITY, COSMIC_AVATAR -> 120;
			default -> 140;
		};
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

	/** Ends a caster's phantoms (ABORT). */
	static void endCaster(int casterId) {
		for (Iterator<Cast> it = CASTS.values().iterator(); it.hasNext(); ) {
			Cast cast = it.next();
			if (cast.caster == casterId) {
				for (Phantom phantom : cast.phantoms.values()) {
					vanish(cast, phantom);
				}
				AvatarFx.copies(casterId, List.of());
				it.remove();
			}
		}
	}

	static void clear() {
		CASTS.clear();
	}

	private static long key(int caster, int spec) {
		return ((long) caster << 20) ^ spec;
	}

	/** Entity lookups of the client level. */
	private static final class QuadEntity {
		static @Nullable Entity get(int id) {
			ClientLevel level = Minecraft.getInstance().level;
			return level == null ? null : level.getEntity(id);
		}
	}

	private PhantomFx() {
	}
}
