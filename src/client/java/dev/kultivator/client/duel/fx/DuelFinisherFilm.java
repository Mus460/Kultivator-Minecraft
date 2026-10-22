package dev.kultivator.client.duel.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.Shots;
import dev.kultivator.client.epic.screen.Letterbox;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.fx.spec.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The closing film of a formal duel (DESIGN 35.4.9), started by the decisive clash (a deathblow, the cut that would take
 * a duellist under 10 %, a reflected qi wave; leaving the ring never) or by the FINISHER phase of a surrender. Real ticks
 * from the decisive blow:
 * <ul>
 * <li>0: the hold. A deathblow's own burst already froze the frame (hit-stop 5, the impact frame, gold sparks hanging in
 * the air, the gold trail); any other decisive cut gets the same beat here. The silent beat before the boom is module
 * U's.</li>
 * <li>3-27: bullet time (σ 0.06 for 24 ticks) while the camera circles the pair at 4.5 b (70°, a world-anchored kill-cam
 * around their midpoint at chest height); a wide stroke of black-red ink is brushed across the body of the fallen in
 * five ticks and dries away; petals and leaves of the biome whirl round the two.</li>
 * <li>24-40: σ ramps back; the camera pushes in on the victor (the kneel and the sheathing are module A's poses).</li>
 * <li>40-60: the card "Pemenang" with the victor's name and the way it was won, seal 勝 in the victor's colour; leaves
 * fall slowly.</li>
 * </ul>
 * Without the camera (Kamera sinematik Mati, unsafe, the hard gates of {@code ShotGuard}) there are no shots; hit-stop,
 * sparks, ink, petals and the card stay. A surrender has no impact frame and no bullet time: the camera goes straight to
 * a two-shot of the pair and the card reads "Mengaku kalah" once the result names the victor. Every shot is a result
 * shot (it may run while the fallen local player sits under the health floor, with RESULT_SAFE), skippable by
 * Backspace or a movement key. No sound here (module U plays the boom, the whoosh, the click and the music).
 */
final class DuelFinisherFilm {

	private static final int GOLD = SparkBurst.GOLD;
	private static final int INK_BLACK = 0x0A0606;
	private static final int INK_RED = 0xB01818;

	private static final class Run {
		final DuelFx.SessionView session;
		final long start;
		final boolean surrender;
		final RandomSource random;
		int winner = -1;
		int loser = -1;
		int outcome = -1;
		int reason = -1;
		Vec3 mid;
		@Nullable Scene scene;
		int step;
		boolean cardShown;
		boolean bullet;

		Run(DuelFx.SessionView session, boolean surrender) {
			this.session = session;
			this.start = DuelFx.ticks();
			this.surrender = surrender;
			this.random = RandomSource.create(session.id * 977L + 13L);
			this.mid = SparkBurst.midpoint(session);
		}
	}

	/** The ink stroke across the fallen, in real ticks (it draws on while the frame is frozen). */
	private record Ink(Vec3 from, Vec3 to, long start) {
	}

	private static @Nullable Run run;
	private static @Nullable Ink ink;

	// ================================================================ starts (from DuelFx)

	/** The decisive clash {@code e} (or only the FINISHER phase with the DECISIVE flag when {@code e} is null). */
	static void decisive(DuelFx.SessionView s, DuelFx.@Nullable ClashEvent e) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !inRange(s)) {
			return;
		}
		Run r = new Run(s, false);
		if (e != null) {
			r.outcome = e.outcome();
			boolean reflected = e.outcome() == DuelCodes.QI_REFLECT;
			r.winner = reflected ? e.bId() : e.aId();
			r.loser = reflected ? e.aId() : e.bId();
		}
		r.scene = EpicDirector.duelScene(s.aId, s.bId, r.mid, s.id * 7919, 160);
		run = r;
		ink = null;
		boolean involved = DuelFx.involved(s.aId, s.bId);
		if (e == null || e.outcome() != DuelCodes.DEATHBLOW) {
			// the deathblow's burst framed itself; a decisive 10 % cut or a reflected wave gets the same held beat
			Vec3 at = e != null ? e.anchor() : r.mid.add(0.0D, 1.2D, 0.0D);
			int rgb = r.winner >= 0 ? s.rgbOf(r.winner) : GOLD;
			DuelTime.hitStop(5, s.aId, s.bId, at);
			DuelShake.impactFrame(true, rgb, involved, e != null ? e.seed() : s.id);
			MetalFlash.add(at, 1.2F, GOLD, 5);
			ShockRing.vertical(at, line(s), GOLD, 0.3F, 2.4F, 7);
		}
		if (near(s)) {
			Letterbox.flick(60);
		}
	}

	/** A surrender: no impact frame, the camera goes straight to the pair; the card waits for the result. */
	static void surrender(DuelFx.SessionView s, int winnerId, int loserId) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || !inRange(s)) {
			return;
		}
		Run r = new Run(s, true);
		r.winner = winnerId;
		r.loser = loserId;
		r.scene = EpicDirector.duelScene(s.aId, s.bId, r.mid, s.id * 7919, 160);
		run = r;
		ink = null;
		if (near(s)) {
			Letterbox.flick(60);
		}
	}

	/** {@code DuelResult}: names the victor and the reason; the card shows now if its beat has passed. */
	static void result(DuelFx.SessionView s, int winnerId, int loserId, int reason, Component title) {
		Run r = run;
		if (r == null || r.session != s) {
			return;
		}
		if (winnerId >= 0) {
			r.winner = winnerId;
		}
		if (loserId >= 0) {
			r.loser = loserId;
		}
		r.reason = reason;
		if (reason == DuelCodes.REASON_SERI || reason == DuelCodes.REASON_BATAL) {
			// a draw or a cancel has no victor to crown
			r.cardShown = true;
			return;
		}
		if (r.step >= 3 || r.surrender) {
			card(r);
		}
	}

	// ================================================================ tick

	static void tick(Minecraft minecraft) {
		if (ink != null && DuelFx.ticks() - ink.start > 40L) {
			ink = null;
		}
		Run r = run;
		if (r == null || minecraft.level == null || minecraft.player == null) {
			return;
		}
		long elapsed = DuelFx.ticks() - r.start;
		DuelFx.SessionView s = r.session;
		if (elapsed > 110L || elapsed > 70L && r.cardShown) {
			run = null;
			return;
		}
		r.mid = SparkBurst.midpoint(s);
		if (r.surrender) {
			if (r.step == 0) {
				r.step = 2;
				Entity a = DuelFx.entity(s.aId);
				Entity b = DuelFx.entity(s.bId);
				if (a != null && b != null) {
					shoot(r, Shots.twoShot(b.position(), 6.0F, 4.5F, 1.2F, 2.0F).asResult());
				}
			}
			if (r.step == 2 && elapsed >= 40L) {
				r.step = 3;
				card(r);
			}
			if (r.step >= 3) {
				fallingLeaves(minecraft.level, r, 1);
			}
			return;
		}
		if (r.step == 0 && elapsed >= 3L) {
			r.step = 1;
			r.bullet = DuelTime.bullet(0.06F, 24, s.aId, s.bId, r.mid);
			// the kill-cam: 4.5 b round the pair's midpoint, camera at chest height + 1.4, looking at the chests
			Vec3 chest = r.mid.add(0.0D, 1.1D, 0.0D);
			shoot(r, Shots.killcam(r.mid.subtract(0.0D, 1.1D, 0.0D), 4.5F, 70.0F, 2.0F).lookAt(chest).asResult());
			brushInk(r);
			whirl(minecraft.level, r);
		}
		if (r.step == 1 && elapsed >= 24L) {
			r.step = 2;
			pushToVictor(r);
		}
		if (r.step == 2 && elapsed >= 40L) {
			r.step = 3;
			card(r);
		}
		if (r.step >= 3) {
			fallingLeaves(minecraft.level, r, elapsed < 60L ? 1 : 0);
		}
	}

	private static void pushToVictor(Run r) {
		DuelFx.SessionView s = r.session;
		Entity winner = r.winner >= 0 ? DuelFx.entity(r.winner) : null;
		Scene scene = r.scene;
		if (winner == null || scene == null) {
			return;
		}
		if (r.winner == s.aId) {
			// the scene's subject: the plain push in front of the body
			shoot(r, Shots.push(5.0F, 3.2F, 1.3F, 0.8F).asResult());
			return;
		}
		float yaw = winner instanceof LivingEntity living ? living.yBodyRot : winner.getYRot();
		double rad = Math.toRadians(yaw);
		Vec3 front = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
		Vec3 eye = winner.position().add(0.0D, winner.getEyeHeight() - 0.1D, 0.0D);
		shoot(r, DuelIntroFilm.sideShot(scene, winner.position(), front, 3.6F, 1.3F, 0.8F).lookAt(eye).asResult());
	}

	private static void shoot(Run r, Shots.Shot shot) {
		Scene scene = r.scene;
		if (scene == null || !scene.own) {
			return;
		}
		DuelIntroFilm.shoot(scene, r.session, shot);
	}

	// ================================================================ the card

	private static void card(Run r) {
		if (r.cardShown || r.winner < 0 || !near(r.session)) {
			return;
		}
		r.cardShown = true;
		DuelFx.SessionView s = r.session;
		Component name = s.nameOf(r.winner);
		Component how = Component.translatable("kultivator.duel.fx.finisher.how." + how(r));
		Component title = Component.translatable(r.surrender ? "kultivator.duel.fx.finisher.yield" : "kultivator.duel.fx.finisher.title");
		Component sub = Component.translatable("kultivator.duel.fx.finisher.sub", name, how);
		TitleCard.show(TitleCard.full(title, sub, "勝", s.rgbOf(r.winner), 70).safeBeat(true));
	}

	/** The lang suffix of the way the duel was won. */
	private static String how(Run r) {
		if (r.surrender || r.reason == DuelCodes.REASON_MENYERAH) {
			return "menyerah";
		}
		if (r.reason == DuelCodes.REASON_PENENTU || r.outcome == DuelCodes.DEATHBLOW) {
			return "penentu";
		}
		if (r.outcome == DuelCodes.QI_REFLECT) {
			return "pantulan";
		}
		if (r.reason == DuelCodes.REASON_KELUAR) {
			return "keluar";
		}
		if (r.reason == DuelCodes.REASON_WAKTU) {
			return "waktu";
		}
		return "sepuluh";
	}

	// ================================================================ the world

	/** The black-red stroke across the fallen: from above one shoulder, down through the chest, past the other hip. */
	private static void brushInk(Run r) {
		Entity loser = r.loser >= 0 ? DuelFx.entity(r.loser) : null;
		Entity winner = r.winner >= 0 ? DuelFx.entity(r.winner) : null;
		if (loser == null) {
			return;
		}
		Vec3 chest = loser.position().add(0.0D, loser.getBbHeight() * 0.62D, 0.0D);
		Vec3 line = winner != null ? loser.position().subtract(winner.position()) : new Vec3(1.0D, 0.0D, 0.0D);
		line = new Vec3(line.x, 0.0D, line.z);
		line = line.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : line.normalize();
		Vec3 side = new Vec3(-line.z, 0.0D, line.x);
		Vec3 from = chest.add(side.scale(1.5D)).add(0.0D, 0.85D, 0.0D).subtract(line.scale(0.25D));
		Vec3 to = chest.subtract(side.scale(1.5D)).add(0.0D, -0.75D, 0.0D).subtract(line.scale(0.25D));
		ink = new Ink(from, to, DuelFx.ticks());
	}

	/** Petals in both colours and leaves of the biome whirling round the pair. */
	private static void whirl(ClientLevel level, Run r) {
		DuelFx.SessionView s = r.session;
		int n = EffectQuality.scale(DuelFxSettings.pick(8, 18, 30));
		ParticleOptions leaf = DuelIntroFilm.leafOf(level, BlockPos.containing(r.mid));
		for (int i = 0; i < n && FxBudget.take(2, FxBudget.Priority.EXTRA) > 0; i++) {
			double angle = Math.PI * 2.0D * i / n + r.random.nextDouble() * 0.3D;
			double radius = 2.0D + r.random.nextDouble() * 1.2D;
			Vec3 p = r.mid.add(Math.cos(angle) * radius, 0.3D + r.random.nextDouble() * 2.0D, Math.sin(angle) * radius);
			double speed = 0.16D + r.random.nextDouble() * 0.08D;
			Vec3 v = new Vec3(-Math.sin(angle) * speed, 0.03D, Math.cos(angle) * speed);
			ParticleOptions options = switch (i % 3) {
				case 0 -> Qi.petal(s.rgbOf(r.winner >= 0 ? r.winner : s.aId), 0.2F, 50);
				case 1 -> Qi.petal(0xF8F0F0, 0.18F, 50);
				default -> leaf;
			};
			level.addParticle(options, p.x, p.y, p.z, v.x, v.y, v.z);
		}
	}

	private static void fallingLeaves(ClientLevel level, Run r, int count) {
		if (count <= 0 || DuelFx.ticks() % 2L != 0L) {
			return;
		}
		ParticleOptions leaf = DuelIntroFilm.leafOf(level, BlockPos.containing(r.mid));
		for (int i = 0; i < count && FxBudget.take(1, FxBudget.Priority.EXTRA) > 0; i++) {
			double angle = r.random.nextDouble() * Math.PI * 2.0D;
			double radius = r.random.nextDouble() * 4.0D;
			level.addParticle(leaf, r.mid.x + Math.cos(angle) * radius, r.mid.y + 3.5D + r.random.nextDouble() * 1.5D, r.mid.z + Math.sin(angle) * radius,
					0.02D, -0.015D, 0.01D);
		}
	}

	// ================================================================ render

	static void render(DuelGeo frame) {
		Ink stroke = ink;
		if (stroke == null || frame.dist2(stroke.from) > 96.0D * 96.0D) {
			return;
		}
		float t = (DuelFx.ticks() - stroke.start) + frame.partial;
		float head = Mth.clamp(t / 5.0F, 0.0F, 1.0F);
		float fade = t < 18.0F ? 1.0F : Math.max(0.0F, 1.0F - (t - 18.0F) / 14.0F);
		if (head <= 0.0F || fade <= 0.0F) {
			return;
		}
		int pieces = 16;
		if (!frame.take(pieces * 2)) {
			return;
		}
		Vec3 prev = stroke.from;
		float prevWidth = 0.0F;
		for (int i = 1; i <= pieces; i++) {
			float s = head * i / pieces;
			Vec3 next = stroke.from.lerp(stroke.to, s);
			// a brush: thin at the touch-down, full in the middle, a dry flick at the end
			float width = 0.62F * (float) Math.pow(Math.max(0.0F, Mth.sin((float) Math.PI * s)), 0.45F);
			float dry = 0.75F + 0.25F * Mth.sin(i * 2.7F + stroke.start);
			int red = DuelGeo.argb(0.55F * fade * dry, INK_RED);
			int black = DuelGeo.argb(0.92F * fade * dry, INK_BLACK);
			frame.segment(frame.strip, prev, next, prevWidth * 1.3F + 0.04F, width * 1.3F + 0.04F, red, red, 0.0F, 1.0F);
			frame.segment(frame.strip, prev, next, prevWidth, width, black, black, 0.0F, 1.0F);
			prev = next;
			prevWidth = width;
		}
	}

	// ================================================================ helpers

	/** The film reaches the duellists and anyone within 64 b. */
	private static boolean inRange(DuelFx.SessionView s) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null
				&& (s.involves(minecraft.player.getId()) || minecraft.player.position().distanceToSqr(s.center) <= 64.0D * 64.0D);
	}

	/** Cards and letterbox: the duellists and onlookers within 32 b (like the intro). */
	private static boolean near(DuelFx.SessionView s) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null
				&& (s.involves(minecraft.player.getId()) || minecraft.player.position().distanceToSqr(s.center) <= 32.0D * 32.0D);
	}

	private static Vec3 line(DuelFx.SessionView s) {
		Entity a = DuelFx.entity(s.aId);
		Entity b = DuelFx.entity(s.bId);
		Vec3 line = a != null && b != null ? b.position().subtract(a.position()) : s.markB().subtract(s.markA());
		line = new Vec3(line.x, 0.0D, line.z);
		return line.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : line.normalize();
	}

	static boolean idle() {
		return ink == null;
	}

	static void clear() {
		run = null;
		ink = null;
	}

	private DuelFinisherFilm() {
	}
}
