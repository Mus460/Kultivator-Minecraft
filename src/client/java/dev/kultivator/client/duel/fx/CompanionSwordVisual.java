package dev.kultivator.client.duel.fx;

import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.DisplayKey;
import dev.kultivator.client.fx.spec.FxBudget;
import dev.kultivator.data.Cultivators;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The companion sword (DESIGN 35.3.11): a fighter whose flying sword became a duel companion (fighter flag
 * {@code FF_COMPANION}) has it circling over the shoulder, point up, bobbing and leaving faint qi motes: a client-only
 * {@code ItemDisplay} of {@code Cultivators.flightSword(player)} (other entities: their main-hand weapon), keyed every tick
 * through {@link DisplayKey}. On a TERBANG swing it darts at the opponent and back (a 4-tick strike, a 6-tick return);
 * on {@code COMPANION_DEFLECT} it snaps to the contact for a moment. Everything is presentation; the server decides.
 */
final class CompanionSwordVisual {

	private static final class Companion {
		final int ownerId;
		final Display.ItemDisplay display;
		final DisplayKey key;
		final float phase;
		/** Client tick of a dart and its target (−1 none). */
		long dartAt = -1L;
		int dartTarget = -1;
		@Nullable Vec3 deflectAt;
		long deflectUntil;

		Companion(int ownerId, Display.ItemDisplay display, float phase) {
			this.ownerId = ownerId;
			this.display = display;
			this.key = new DisplayKey(display);
			this.phase = phase;
		}
	}

	private static final Map<Integer, Companion> LIVE = new HashMap<>();
	private static @Nullable ItemStack fallback;

	static void onSwing(DuelFx.SwingView swing) {
		if (swing.kind != DuelCodes.KIND_TERBANG || swing.has(DuelCodes.SWING_FEINT_CANCEL) || swing.has(DuelCodes.SWING_REJECTED)) {
			return;
		}
		Companion companion = LIVE.get(swing.entityId);
		if (companion == null) {
			return;
		}
		DuelFx.SessionView s = DuelFx.sessionOf(swing.entityId);
		companion.dartTarget = s != null ? s.partnerOf(swing.entityId) : -1;
		double lead = swing.charging() ? 0.0D : Math.max(0.0D, swing.impact() - DuelLinks.serverTick(0.0F) - 4.0D);
		companion.dartAt = DuelFx.ticks() + (long) lead;
	}

	static void onClash(DuelFx.ClashEvent e) {
		if (e.outcome() != DuelCodes.COMPANION_DEFLECT) {
			return;
		}
		// the defender's companion caught it (bId), else the attacker's
		Companion companion = LIVE.get(e.bId());
		if (companion == null) {
			companion = LIVE.get(e.aId());
		}
		if (companion != null) {
			companion.deflectAt = e.anchor();
			companion.deflectUntil = DuelFx.ticks() + 6L;
		}
	}

	static void tick(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}
		// fighters with the companion flag get one
		for (DuelFx.FighterView f : DuelFx.fighters()) {
			if (!f.has(DuelCodes.FIGHTER_COMPANION) || LIVE.containsKey(f.entityId)) {
				continue;
			}
			Entity owner = DuelFx.entity(f.entityId);
			if (owner == null || minecraft.player.distanceToSqr(owner) > 48.0D * 48.0D || !DuelDisplays.canCreate()) {
				continue;
			}
			Display.ItemDisplay display = DuelDisplays.item(shoulder(owner, 0.0F, 0.0D), sword(owner), false, 1.0F);
			if (display != null) {
				LIVE.put(f.entityId, new Companion(f.entityId, display, (f.entityId % 7) * 0.9F));
			}
		}
		long now = DuelFx.ticks();
		double time = DuelTime.now();
		for (Iterator<Companion> it = LIVE.values().iterator(); it.hasNext(); ) {
			Companion c = it.next();
			Entity owner = DuelFx.entity(c.ownerId);
			DuelFx.FighterView f = DuelFx.fighter(c.ownerId);
			if (owner == null || !owner.isAlive() || f == null || !f.has(DuelCodes.FIGHTER_COMPANION) || c.display.isRemoved()) {
				DuelDisplays.remove(c.display);
				it.remove();
				continue;
			}
			Vec3 rest = shoulder(owner, c.phase, time);
			Vec3 at = rest;
			Quaternionf rotation;
			Entity target = c.dartTarget >= 0 ? DuelFx.entity(c.dartTarget) : null;
			long dart = c.dartAt >= 0L ? now - c.dartAt : -1L;
			if (dart >= 0L && dart <= 10L && target != null) {
				// out in 4 ticks to the chest of the target, back in 6
				Vec3 chest = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
				double t = dart <= 4L ? dart / 4.0D : 1.0D - (dart - 4L) / 6.0D;
				at = rest.lerp(chest, t * t * (3.0D - 2.0D * t));
				rotation = pointing(chest.subtract(rest));
				trail(at, owner);
			} else if (c.deflectAt != null && now < c.deflectUntil) {
				at = rest.lerp(c.deflectAt, 0.8D);
				rotation = pointing(c.deflectAt.subtract(owner.position().add(0.0D, owner.getBbHeight() * 0.7D, 0.0D)));
			} else {
				if (dart > 10L) {
					c.dartAt = -1L;
				}
				// point up, turning slowly about the vertical, tilted outward
				float spin = (float) (time * 0.08D + c.phase);
				rotation = new Quaternionf().rotationY(spin).rotateZ((float) Math.toRadians(-40.0D));
				if ((now + c.ownerId) % 6L == 0L) {
					trail(at, owner);
				}
			}
			c.key.move(at);
			c.key.key(new Vector3f(0.0F, 0.0F, 0.0F), rotation, new Vector3f(0.7F, 0.7F, 0.7F), 1);
		}
	}

	/** Over the owner's right shoulder, circling slowly, bobbing. */
	private static Vec3 shoulder(Entity owner, float phase, double time) {
		float yaw = owner instanceof LivingEntity living ? living.yBodyRot : owner.getYRot();
		double rad = Math.toRadians(yaw);
		Vec3 right = new Vec3(-Math.cos(rad), 0.0D, -Math.sin(rad));
		Vec3 back = new Vec3(Math.sin(rad), 0.0D, -Math.cos(rad));
		double circle = time * 0.06D + phase;
		Vec3 base = owner.position().add(0.0D, owner.getBbHeight() + 0.25D + Math.sin(time * 0.15D + phase) * 0.08D, 0.0D);
		return base.add(right.scale(0.75D + Math.cos(circle) * 0.15D)).add(back.scale(0.35D + Math.sin(circle) * 0.15D));
	}

	/** A rotation turning the sword model's blade (its +x/+y diagonal in the FIXED transform) toward {@code dir}. */
	private static Quaternionf pointing(Vec3 dir) {
		if (dir.lengthSqr() < 1.0E-6D) {
			return new Quaternionf();
		}
		Vector3f to = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
		Vector3f from = new Vector3f(1.0F, 1.0F, 0.0F).normalize();
		return new Quaternionf().rotationTo(from, to);
	}

	private static void trail(Vec3 at, Entity owner) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || FxBudget.take(1, FxBudget.Priority.EXTRA) < 1) {
			return;
		}
		DuelFx.SessionView s = DuelFx.sessionOf(owner.getId());
		int rgb = s != null ? s.rgbOf(owner.getId()) : 0xBFE8FF;
		minecraft.level.addParticle(Qi.mote(rgb, 0.12F, 12), at.x, at.y, at.z, 0.0D, 0.01D, 0.0D);
	}

	/** The flight sword of a player (their best spare sword), else the held weapon, else an iron sword. */
	private static ItemStack sword(Entity owner) {
		if (owner instanceof Player player) {
			ItemStack stack = Cultivators.flightSword(player);
			if (!stack.isEmpty()) {
				return stack.copy();
			}
		}
		if (owner instanceof LivingEntity living && !living.getMainHandItem().isEmpty()) {
			return living.getMainHandItem().copy();
		}
		if (fallback == null) {
			fallback = new ItemStack(Items.IRON_SWORD);
		}
		return fallback;
	}

	static void clear() {
		for (Companion c : LIVE.values()) {
			DuelDisplays.remove(c.display);
		}
		LIVE.clear();
	}

	private CompanionSwordVisual() {
	}
}
