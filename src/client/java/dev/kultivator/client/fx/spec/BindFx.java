package dev.kultivator.client.fx.spec;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.model.Anim;
import dev.kultivator.client.anim.model.ModelHandle;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.quad.QuadFx;
import dev.kultivator.client.fx.quad.RibbonQuad;
import dev.kultivator.client.fx.quad.RingQuad;
import dev.kultivator.technique.spec.run.BindKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bind visuals drawn per bound entity (polish S21, BindSync read through the animation module's ClientBinds; the split
 * with that module: its ArtModels grows the vine, ice and rope props and BindVisuals freezes and tints the body, this
 * draws the chains and glyphs): an iron chain ring at the waist (CHAINS, chain_link models when present), a gold light
 * ring for ROPE, a red 封 seal ring over the head (SEAL), grey clock glints (TIME_STOP), an upward pull (SUSPEND), a
 * bobbing "Z z z" (SLEEP) and a red karma thread from the caster (KARMA_LINK), plus the particles of every bind (vines'
 * blue-silver motes, frost snow). Minimal keeps the particles only.
 */
public final class BindFx {

	/** One drawn bind. */
	private static final class Drawn {
		final int entity;
		final BindKind kind;
		final int caster;
		final List<ModelHandle> models = new ArrayList<>();
		@Nullable Display display;
		@Nullable QuadFx.Quad thread;
		@Nullable QuadFx.Quad ring;
		int age;

		Drawn(int entity, BindKind kind, int caster) {
			this.entity = entity;
			this.kind = kind;
			this.caster = caster;
		}
	}

	private static final Map<Long, Drawn> DRAWN = new HashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();

	/** Once per client tick (from {@code SpecFx.tick}). */
	static void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		List<AnimBridge.BindView> binds = AnimBridge.binds();
		if (binds.isEmpty() && DRAWN.isEmpty()) {
			return;
		}
		Set<Long> live = new HashSet<>();
		for (AnimBridge.BindView bind : binds) {
			if (bind.ticks() <= 0) {
				continue;
			}
			Entity entity = level.getEntity(bind.entity());
			if (entity == null || !entity.isAlive()) {
				continue;
			}
			long key = ((long) bind.entity() << 4) | bind.kind().ordinal();
			live.add(key);
			Drawn drawn = DRAWN.get(key);
			if (drawn == null) {
				drawn = new Drawn(bind.entity(), bind.kind(), bind.caster());
				DRAWN.put(key, drawn);
				start(drawn, entity);
			}
			drawn.age++;
			draw(drawn, entity);
		}
		for (Iterator<Map.Entry<Long, Drawn>> it = DRAWN.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Drawn> e = it.next();
			if (!live.contains(e.getKey())) {
				end(e.getValue(), level);
				it.remove();
			}
		}
	}

	private static void start(Drawn drawn, Entity entity) {
		boolean props = !EffectQuality.minimal() && PhantomDisplays.enabled();
		double h = entity.getBbHeight();
		double w = Math.max(0.4D, entity.getBbWidth());
		switch (drawn.kind) {
			case ROOT_VINE, FREEZE -> {
				// the vine and ice props are the animation module's (ArtModels); this draws their particles only
			}
			case ROPE -> {
				// the rope prop is the animation module's (ArtModels); a gold waist ring of light marks it here
				if (!EffectQuality.minimal()) {
					RingQuad ring = new RingQuad(entity.position().add(0.0D, h * 0.5D, 0.0D), (float) w * 0.8F, (float) w * 0.8F, 0xFFD54A, 0.45F, 600);
					ring.follow(drawn.entity, h * 0.5D);
					if (QuadFx.add(ring)) {
						drawn.ring = ring;
					}
				}
			}
			case CHAINS -> {
				String id = "chain_link";
				int links = EffectQuality.maximal() ? 8 : 4;
				if (props && AnimBridge.modelKnown(id)) {
					for (int i = 0; i < links; i++) {
						double angle = Math.PI * 2.0D * i / links;
						Vec3 offset = new Vec3(Math.cos(angle) * w * 0.75D, h * 0.5D, Math.sin(angle) * w * 0.75D);
						ModelHandle link = AnimBridge.model(id, entity.position().add(offset), 0.5F, (float) Math.toDegrees(-angle), 0.0F, 90.0F, 600,
								List.of(AnimBridge.step(Anim.GROW, 0, 5, 0.1F, 0.0F, null)), drawn.entity, offset, false, true, drawn.caster,
								dev.kultivator.technique.spec.dsl.FxLayer.Priority.CORE);
						if (link != null) {
							drawn.models.add(link);
						}
					}
				}
				if (!EffectQuality.minimal()) {
					int color = 0xB8B8C8;
					RingQuad ring = new RingQuad(entity.position().add(0.0D, h * 0.5D, 0.0D), (float) w * 0.8F, (float) w * 0.8F, color, 0.7F, 600);
					ring.follow(drawn.entity, h * 0.5D);
					if (QuadFx.add(ring)) {
						drawn.ring = ring;
					}
				}
			}
			case SEAL -> {
				if (props && AnimBridge.modelKnown("seal_ring_feng")) {
					ModelHandle seal = AnimBridge.model("seal_ring_feng", entity.position().add(0.0D, h + 0.8D, 0.0D), 1.5F, 0.0F, 90.0F, 0.0F, 600,
							List.of(AnimBridge.step(Anim.SPIN, 0, 600, 3.0F, 0.0F, null)), drawn.entity, new Vec3(0.0D, h + 0.8D, 0.0D), false, true,
							drawn.caster, dev.kultivator.technique.spec.dsl.FxLayer.Priority.CORE);
					if (seal != null) {
						drawn.models.add(seal);
					}
				} else if (props) {
					drawn.display = text(entity, "封", 0xFF3030, h + 0.9D);
				}
				if (!EffectQuality.minimal()) {
					RingQuad ring = new RingQuad(entity.position().add(0.0D, 0.05D, 0.0D), (float) w, (float) w * 1.6F, 0xE02828, 0.6F, 600);
					ring.follow(drawn.entity, 0.05D);
					if (QuadFx.add(ring)) {
						drawn.ring = ring;
					}
				}
			}
			case SLEEP -> {
				if (props) {
					drawn.display = text(entity, "Z z z", 0xC8F0FF, h + 0.5D);
				}
			}
			case KARMA_LINK -> {
				if (!EffectQuality.minimal() && drawn.caster >= 0) {
					int caster = drawn.caster;
					int bound = drawn.entity;
					RibbonQuad.Path path = (t, age) -> {
						Entity a = entityOf(caster);
						Entity b = entityOf(bound);
						if (a == null || b == null) {
							return Vec3.ZERO;
						}
						Vec3 from = a.position().add(0.0D, a.getBbHeight() * 0.6D, 0.0D);
						Vec3 to = b.position().add(0.0D, b.getBbHeight() * 0.6D, 0.0D);
						// a sagging thread that shivers
						Vec3 p = from.lerp(to, t);
						double sag = Math.sin(Math.PI * t) * Math.min(1.5D, from.distanceTo(to) * 0.08D);
						return p.add(0.0D, -sag + Math.sin(age * 0.4D + t * 9.0D) * 0.05D, 0.0D);
					};
					QuadFx.Quad thread = new RibbonQuad(path, 16, 0.08F, 0.08F, 0xFF4040, 0xFFD54A, false, 600).alpha(0.8F);
					if (QuadFx.add(thread)) {
						drawn.thread = thread;
					}
				}
			}
			default -> {
			}
		}
	}

	private static void draw(Drawn drawn, Entity entity) {
		double h = entity.getBbHeight();
		double w = Math.max(0.4D, entity.getBbWidth());
		Vec3 feet = entity.position();
		if (drawn.display != null && !drawn.display.isRemoved()) {
			double bob = drawn.kind == BindKind.SLEEP ? Math.sin(drawn.age * 0.15D) * 0.15D : 0.0D;
			double lift = drawn.kind == BindKind.FREEZE ? 0.0D : h + (drawn.kind == BindKind.SLEEP ? 0.5D : 0.9D) + bob;
			drawn.display.setPos(feet.x, feet.y + lift, feet.z);
		}
		if (drawn.age % 2 != 0) {
			return;
		}
		float k = EffectQuality.level().factor();
		int n = Math.max(1, Math.round(2 * k));
		for (int i = 0; i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
			switch (drawn.kind) {
				case ROOT_VINE -> {
					double y = RANDOM.nextDouble() * h * 0.6D;
					double spiral = angle + drawn.age * 0.1D;
					add(Qi.mote(0x8FD0FF, 0.16F, 16), feet.add(Math.cos(spiral) * w * 0.55D, y, Math.sin(spiral) * w * 0.55D), new Vec3(0.0D, 0.03D, 0.0D));
				}
				case ROPE, CHAINS -> add(Qi.mote(drawn.kind == BindKind.ROPE ? 0xFFD54A : 0xC8C8D8, 0.14F, 10),
						feet.add(Math.cos(angle) * w * 0.8D, h * 0.5D + (RANDOM.nextDouble() - 0.5D) * 0.15D, Math.sin(angle) * w * 0.8D), Vec3.ZERO);
				case SEAL -> add(Qi.mote(0xFF3030, 0.16F, 14), feet.add(Math.cos(angle) * w * 0.7D, h + 0.8D, Math.sin(angle) * w * 0.7D), new Vec3(0.0D, -0.02D, 0.0D));
				case FREEZE -> add(Qi.snow(0xBFF6FF, 0.18F, 30), feet.add(Math.cos(angle) * w * 0.7D, RANDOM.nextDouble() * h, Math.sin(angle) * w * 0.7D),
						new Vec3(0.0D, -0.01D, 0.0D));
				case TIME_STOP -> add(i % 2 == 0 ? Qi.glint(0xFFE7A0, 0.25F, 10) : Qi.mote(0xA8A8B8, 0.15F, 20),
						feet.add(Math.cos(angle) * w * 0.8D, RANDOM.nextDouble() * h, Math.sin(angle) * w * 0.8D), Vec3.ZERO);
				case SUSPEND -> add(Qi.mote(0xC9B8FF, 0.16F, 16), feet.add(Math.cos(angle) * w * 0.5D, -0.2D, Math.sin(angle) * w * 0.5D), new Vec3(0.0D, 0.12D, 0.0D));
				case SLEEP -> {
					if (drawn.age % 10 == 0) {
						add(Qi.mote(0x9CFF9C, 0.12F, 30), feet.add(Math.cos(angle) * 0.6D, h + 0.3D, Math.sin(angle) * 0.6D), new Vec3(0.0D, 0.02D, 0.0D));
					}
				}
				case KARMA_LINK -> {
					if (drawn.thread == null) {
						Entity caster = entityOf(drawn.caster);
						if (caster != null) {
							Vec3 from = caster.position().add(0.0D, caster.getBbHeight() * 0.6D, 0.0D);
							Vec3 to = feet.add(0.0D, h * 0.6D, 0.0D);
							add(Qi.mote(0xFF4040, 0.12F, 6), from.lerp(to, RANDOM.nextDouble()), Vec3.ZERO);
						}
					}
				}
			}
		}
	}

	private static void end(Drawn drawn, ClientLevel level) {
		for (ModelHandle handle : drawn.models) {
			AnimBridge.play(handle, AnimBridge.step(Anim.SHATTER, 0, 4, 0.0F, 0.0F, null));
			AnimBridge.end(handle, 4);
		}
		drawn.models.clear();
		if (drawn.display != null) {
			PhantomDisplays.remove(drawn.display);
		}
		if (drawn.thread != null) {
			drawn.thread.stop(4);
		}
		if (drawn.ring != null) {
			drawn.ring.stop(4);
		}
		Entity entity = level.getEntity(drawn.entity);
		if (entity != null) {
			// the bind breaks: shards in its colour
			int color = switch (drawn.kind) {
				case FREEZE -> 0xBFF6FF;
				case SEAL -> 0xFF3030;
				case ROOT_VINE -> 0x8FD0FF;
				case ROPE -> 0xFFD54A;
				default -> 0xC8C8D8;
			};
			Vec3 mid = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
			int n = Math.max(3, Math.round(10 * EffectQuality.level().factor()));
			for (int i = 0; i < n; i++) {
				Vec3 d = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
				add(Qi.shard(color, 0.2F, 20), mid.add(d.scale(0.3D)), d.scale(0.2D));
			}
		}
	}

	private static @Nullable Display text(Entity entity, String glyph, int color, double lift) {
		Display.TextDisplay text = PhantomDisplays.create(EntityTypes.TEXT_DISPLAY, entity.position().add(0.0D, lift, 0.0D), 1.0F);
		if (text == null) {
			return null;
		}
		text.setText(Component.literal(glyph).withColor(color & 0xFFFFFF));
		text.setBackgroundColor(0);
		text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		DisplayKey.place(text, new Vector3f(), new Quaternionf(), new Vector3f(1.2F, 1.2F, 1.2F));
		return text;
	}

	private static @Nullable Entity entityOf(int id) {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null || id < 0 ? null : level.getEntity(id);
	}

	private static void add(ParticleOptions particle, Vec3 at, Vec3 velocity) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0) {
			level.addParticle(particle, true, false, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
		}
	}

	private BindFx() {
	}
}
