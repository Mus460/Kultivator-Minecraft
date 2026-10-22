package dev.kultivator.client.duel.fx;

import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Sword Qi Wave as seen (DESIGN 35.3.10, 35.4.5): a qi swing flagged {@code SW_QI_WAVE} releases, at its impact, a
 * crescent of the swing's qi colour 2.4 b wide (+0.6 b with deep sword comprehension is the server's business; the look
 * keeps 2.4) that flies 1 b per presentation tick from the attacker's chest along the duel line, horizontal for a cut from
 * the side, upright for a cut from above, diagonal for the diagonal cuts. It is drawn in its own plane: a soft glowing
 * body, a white-hot leading edge (additive) and a motion ghost behind; it burns a scorch mark into the ground every 1.5 b
 * of its path, and stops at the first solid block (a read-only clip per tick) with a small burst. A perfect deflect turns
 * it back toward its sender in the defender's colour at 70 % strength; a deflect splits it into two small crescents
 * flying apart. Nothing here is an entity; the server decides every hit.
 */
final class QiWaveVisual {

	private static final class Wave {
		int ownerId;
		Vec3 pos;
		Vec3 dir;
		Vec3 widthAxis;
		int rgb;
		float strength = 1.0F;
		float width = 2.4F;
		double travelled;
		double max = 12.0D;
		double lastScorch;
		double born;
		boolean small;
		boolean dead;
		float fade = 1.0F;
	}

	private record Pending(DuelFx.SwingView swing, long serverImpact) {
	}

	private static final List<Wave> LIVE = new ArrayList<>();
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

	static void onSwing(DuelFx.SwingView swing) {
		if (!swing.has(DuelCodes.SWING_QI_WAVE) || swing.charging() || swing.has(DuelCodes.SWING_FEINT_CANCEL) || swing.has(DuelCodes.SWING_REJECTED)) {
			PENDING.removeIf(p -> p.swing == swing);
			return;
		}
		for (Pending p : PENDING) {
			if (p.swing == swing) {
				return;
			}
		}
		PENDING.add(new Pending(swing, (long) swing.impact()));
	}

	static void onClash(DuelFx.ClashEvent e) {
		if (LIVE.isEmpty()) {
			return;
		}
		Wave wave = nearest(e.aId(), e.anchor());
		if (wave == null) {
			return;
		}
		switch (e.outcome()) {
			case DuelCodes.QI_REFLECT -> {
				// sent back to its sender in the defender's colour, at 70 %
				wave.dir = wave.dir.scale(-1.0D);
				wave.rgb = e.rgbB() != 0 ? e.rgbB() : wave.rgb;
				wave.strength *= 0.7F;
				wave.ownerId = e.bId();
				wave.travelled = Math.max(0.0D, wave.max - 10.0D);
			}
			case DuelCodes.QI_DEFLECT -> {
				wave.dead = true;
				split(wave);
			}
			case DuelCodes.HIT, DuelCodes.BLOCK, DuelCodes.EVADE, DuelCodes.COMPANION_DEFLECT -> {
				if (e.outcome() != DuelCodes.EVADE) {
					wave.dead = true;
					burst(wave.pos, wave.rgb, 6);
				}
			}
			default -> {
			}
		}
	}

	private static @Nullable Wave nearest(int ownerId, Vec3 at) {
		Wave best = null;
		double bestDistance = 6.0D * 6.0D;
		for (Wave wave : LIVE) {
			if (wave.dead || wave.small) {
				continue;
			}
			double d = wave.pos.distanceToSqr(at);
			if ((wave.ownerId == ownerId || d < 2.0D * 2.0D) && d < bestDistance) {
				best = wave;
				bestDistance = d;
			}
		}
		return best;
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		if (!PENDING.isEmpty()) {
			double now = DuelLinks.serverTick(0.0F);
			for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
				Pending p = it.next();
				if (now >= p.serverImpact) {
					it.remove();
					launch(p.swing);
				} else if (p.serverImpact - now > 60.0D) {
					it.remove();
				}
			}
		}
		if (LIVE.isEmpty()) {
			return;
		}
		float s = DuelTime.frozen() ? 0.0F : DuelTime.sigma();
		for (Iterator<Wave> it = LIVE.iterator(); it.hasNext(); ) {
			Wave wave = it.next();
			if (wave.dead) {
				wave.fade -= 0.34F;
				if (wave.fade <= 0.0F) {
					it.remove();
				}
				continue;
			}
			if (s <= 0.0F) {
				continue;
			}
			double step = (wave.small ? 0.7D : 1.0D) * s;
			Vec3 next = wave.pos.add(wave.dir.scale(step));
			BlockHitResult hit = level.clip(new ClipContext(wave.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
			if (hit.getType() != HitResult.Type.MISS) {
				// a wall stops it (nothing breaks)
				wave.pos = hit.getLocation();
				wave.dead = true;
				burst(wave.pos, wave.rgb, 8);
				ScorchDecal.add(wave.pos, wave.dir, 1.6F, 0.8F);
				continue;
			}
			wave.pos = next;
			wave.travelled += step;
			if (!wave.small && wave.travelled - wave.lastScorch >= 1.5D) {
				wave.lastScorch = wave.travelled;
				ScorchDecal.add(wave.pos, wave.dir, 1.4F, 0.55F);
			}
			if (wave.travelled >= wave.max) {
				wave.dead = true;
			}
			if (!wave.small && FxBudget.take(1, FxBudget.Priority.EXTRA) > 0) {
				Vec3 tail = wave.pos.subtract(wave.dir.scale(0.6D));
				level.addParticle(Qi.mote(wave.rgb, 0.18F, 10), tail.x, tail.y, tail.z, 0.0D, 0.02D, 0.0D);
			}
		}
	}

	private static void launch(DuelFx.SwingView swing) {
		Entity owner = DuelFx.entity(swing.entityId);
		Minecraft minecraft = Minecraft.getInstance();
		if (owner == null || minecraft.player == null || minecraft.player.distanceToSqr(owner) > 64.0D * 64.0D) {
			return;
		}
		DuelFx.SessionView s = DuelFx.sessionOf(swing.entityId);
		Entity target = s != null ? DuelFx.entity(s.partnerOf(swing.entityId)) : null;
		Vec3 dir;
		if (target != null) {
			dir = target.position().subtract(owner.position());
		} else {
			float yaw = owner instanceof LivingEntity living ? living.yHeadRot : owner.getYRot();
			double rad = Math.toRadians(yaw);
			dir = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
		}
		dir = new Vec3(dir.x, 0.0D, dir.z);
		if (dir.lengthSqr() < 1.0E-6D) {
			return;
		}
		dir = dir.normalize();
		Vec3 side = dir.cross(UP).normalize();
		Vec3 widthAxis = switch (swing.dir) {
			case DuelCodes.DIR_ATAS -> UP;
			case DuelCodes.DIR_ATAS_KANAN -> UP.add(side).normalize();
			case DuelCodes.DIR_ATAS_KIRI -> UP.subtract(side).normalize();
			default -> side;
		};
		Wave wave = new Wave();
		wave.ownerId = swing.entityId;
		wave.pos = owner.position().add(0.0D, owner.getBbHeight() * 0.62D, 0.0D).add(dir.scale(0.9D));
		wave.dir = dir;
		wave.widthAxis = widthAxis;
		wave.rgb = swing.qiRgb != 0 ? swing.qiRgb : 0x9FD8FF;
		wave.born = DuelTime.now();
		int cap = DuelFxSettings.pick(2, 4, 8);
		while (LIVE.size() >= cap && !LIVE.isEmpty()) {
			LIVE.remove(0);
		}
		LIVE.add(wave);
		MetalFlash.add(wave.pos, 0.8F, wave.rgb, 3);
	}

	/** A deflect: two small crescents flying apart, half as wide, short-lived. */
	private static void split(Wave wave) {
		burst(wave.pos, wave.rgb, 10);
		for (int i = -1; i <= 1; i += 2) {
			Wave half = new Wave();
			half.ownerId = wave.ownerId;
			half.pos = wave.pos;
			half.dir = wave.dir.scale(0.4D).add(wave.widthAxis.scale(i * 0.9D)).normalize();
			half.widthAxis = wave.widthAxis;
			half.rgb = wave.rgb;
			half.width = 1.1F;
			half.strength = 0.7F;
			half.max = 4.0D;
			half.small = true;
			half.born = DuelTime.now();
			LIVE.add(half);
		}
	}

	private static void burst(Vec3 at, int rgb, int count) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		MetalFlash.add(at, 0.7F, rgb, 3);
		int n = Math.min(count, DuelFxSettings.pick(4, 10, 20));
		for (int i = 0; i < n && FxBudget.take(1, FxBudget.Priority.DETAIL) > 0; i++) {
			double a = Math.PI * 2.0D * i / n;
			level.addParticle(Qi.spark(rgb, 0.14F, 8), at.x, at.y, at.z, Math.cos(a) * 0.25D, 0.08D, Math.sin(a) * 0.25D);
		}
	}

	static void render(DuelGeo frame) {
		if (LIVE.isEmpty()) {
			return;
		}
		for (Wave wave : LIVE) {
			if (frame.dist2(wave.pos) > 96.0D * 96.0D) {
				continue;
			}
			float end = (float) Math.min(1.0D, (wave.max - wave.travelled) / 2.0D);
			float alpha = wave.strength * Math.max(0.0F, Math.min(end, wave.fade));
			if (alpha <= 0.01F || !frame.take(40)) {
				continue;
			}
			Vec3 lead = wave.dead ? wave.pos : wave.pos.add(wave.dir.scale(DuelTime.frozen() ? 0.0D : frame.partial * DuelTime.sigma()));
			crescent(frame, lead, wave, alpha, 1.0F);
			// the motion ghost behind it
			crescent(frame, lead.subtract(wave.dir.scale(0.7D)), wave, alpha * 0.35F, 0.85F);
		}
	}

	/** One crescent in the plane of its width axis and its travel direction, bulging forward. */
	private static void crescent(DuelGeo frame, Vec3 centre, Wave wave, float alpha, float scale) {
		int segments = 12;
		double r = wave.width * 0.5D * scale;
		double half = Math.toRadians(70.0D);
		Vec3 prevOuter = null;
		Vec3 prevInner = null;
		int body = DuelGeo.argb(0.55F * alpha, wave.rgb);
		int clear = DuelGeo.argb(0.0F, wave.rgb);
		int hot = DuelGeo.argb(Math.min(1.0F, 0.95F * alpha), DuelGeo.mix(wave.rgb, 0xFFFFFF, 0.7F));
		for (int i = 0; i <= segments; i++) {
			double t = -half + 2.0D * half * i / segments;
			double cos = Math.cos(t);
			double thickness = 0.42D * scale * Math.pow(Math.max(0.0D, cos), 1.5D);
			Vec3 outer = centre.add(wave.widthAxis.scale(Math.sin(t) * r)).add(wave.dir.scale((cos - 1.0D) * r * 0.45D));
			Vec3 inner = outer.subtract(wave.dir.scale(thickness));
			if (prevOuter != null) {
				frame.quad(frame.strip, prevInner, prevOuter, outer, inner, 0.0F, 0.0F, 1.0F, 1.0F, clear, body, body, clear, LightCoordsUtil.FULL_BRIGHT);
				frame.segment(frame.add, prevOuter, outer, 0.05F * scale, 0.05F * scale, hot, hot, 0.0F, 1.0F);
			}
			prevOuter = outer;
			prevInner = inner;
		}
		frame.billboard(frame.glow, centre, (float) (r * 0.7D), 0.0F, DuelGeo.argb(0.35F * alpha, wave.rgb));
	}

	static boolean idle() {
		return LIVE.isEmpty();
	}

	static void clear() {
		LIVE.clear();
		PENDING.clear();
	}

	private QiWaveVisual() {
	}
}
