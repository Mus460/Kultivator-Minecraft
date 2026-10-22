package dev.kultivator.client.duel.fx;

import dev.kultivator.client.fx.spec.FxBudget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The test previews of the duel visuals ({@code /kultivasi uji duel ...}, DESIGN 35.8.4; the server sends
 * {@code DuelPreview(kind, arg, f, rgb)} to the operator who typed it). Everything happens in front of the local player,
 * fixed in the world where it started, with two virtual blades drawn as light so the contact point can be judged:
 * <ul>
 * <li>{@code SPARK} ({@code percikan [hasil] [warna]}): {@code arg} = a {@code DuelClash} outcome (−1 or unknown: a cycle of
 * clash, deflect, perfect, blade lock, guard break, deathblow, 1.5 s apart), {@code rgb} = the qi / attacker colour (0
 * none), {@code f} = power 0..1 (0: 0.7). The attacking blade sweeps into the defending one and the burst of 35.4.2 fires
 * where they cross.</li>
 * <li>{@code SLOWMO} ({@code lambat [tick]}): {@code arg} real ticks (0: 20) of Gerak lambat with a perfect-deflect burst
 * and a sample trail.</li>
 * <li>{@code INTRO} / {@code FINISHER} ({@code intro} / {@code akhir}): the films with the local player as duellist A and
 * {@code arg} as B (an entity id, or −1 for a virtual dummy standing 6 b ahead).</li>
 * <li>{@code TRAIL}: a sample blade trail; {@code arg} = a swing direction code (−1: right), {@code rgb} the colour (0
 * steel), {@code f} ≥ 0.5 = a qi swing.</li>
 * <li>{@code BIND}: a blade lock of {@code arg} ticks (0: 60) with the sliding hot spot and the grinding sparks, ending in
 * a won lock.</li>
 * <li>{@code SHOCK}, {@code DECAL}: shock rings / scorched slash marks ahead ({@code rgb} colour).</li>
 * <li>{@code RING} ({@code gelanggang [radius]}): the ring at the player's feet for 20 s without a session.</li>
 * </ul>
 * {@code ALL_POSES} and the other kinds belong to modules A and U and are ignored here.
 */
final class DuelPreviews {

	/** Session ids of the preview films (never sent by the server). */
	private static final int INTRO_SESSION = Integer.MIN_VALUE + 11;
	private static final int FINISHER_SESSION = Integer.MIN_VALUE + 12;
	private static final int STEEL = 0xE8F0FF;
	private static final int[] CYCLE = {DuelCodes.CLASH, DuelCodes.DEFLECT, DuelCodes.PERFECT, DuelCodes.BIND, DuelCodes.GUARD_BREAK,
			DuelCodes.DEATHBLOW};

	/** The frame of a preview: a contact point in front of the player and its axes, fixed at the start. */
	private record Stage(Vec3 eye, Vec3 at, Vec3 look, Vec3 right, Vec3 up) {
		Vec3 point(double r, double u, double f) {
			return at.add(right.scale(r)).add(up.scale(u)).add(look.scale(f));
		}
	}

	/** One running preview. */
	private abstract static class Demo {
		final Stage stage;
		final long start;
		final int life;

		Demo(Stage stage, int life) {
			this.stage = stage;
			this.start = DuelFx.ticks();
			this.life = life;
		}

		long age() {
			return DuelFx.ticks() - start;
		}

		boolean done() {
			return age() > life;
		}

		void tick() {
		}

		void render(DuelGeo frame, float age) {
		}

		void end() {
		}
	}

	private static final List<Demo> LIVE = new ArrayList<>();

	static void play(int kind, int arg, float f, int rgb) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}
		Stage stage = stage(player, 2.0D);
		int colour = rgb & 0xFFFFFF;
		switch (kind) {
			case DuelCodes.PREVIEW_SPARK -> {
				float power = f > 0.0F ? Mth.clamp(f, 0.0F, 1.0F) : 0.7F;
				if (arg >= DuelCodes.HIT && arg <= DuelCodes.COMPANION_DEFLECT) {
					add(new Clash(stage, arg, colour, power, 0));
				} else {
					for (int i = 0; i < CYCLE.length; i++) {
						add(new Clash(stage, CYCLE[i], colour, power, i * 30));
					}
				}
			}
			case DuelCodes.PREVIEW_SLOWMO -> {
				int ticks = arg > 0 ? Math.min(30, arg) : 20;
				if (!DuelTime.slowmo(ticks, player.getId(), -1, stage.at())) {
					player.sendOverlayMessage(Component.translatable("kultivator.duel.fx.preview.slowmo_refused"));
				}
				add(new Clash(stage, DuelCodes.PERFECT, colour, 0.8F, 2));
				add(new Trail(stage(player, 1.6D), DuelCodes.DIR_KANAN, colour != 0 ? colour : STEEL, colour != 0, 0));
			}
			case DuelCodes.PREVIEW_INTRO -> add(new IntroFilm(stage(player, 3.0D), player, arg));
			case DuelCodes.PREVIEW_FINISHER -> add(new FinisherFilm(stage(player, 3.0D), player, arg, colour));
			case DuelCodes.PREVIEW_TRAIL -> {
				int dir = arg >= 0 && arg <= DuelCodes.DIR_PUTAR ? arg : DuelCodes.DIR_KANAN;
				boolean qi = f >= 0.5F;
				add(new Trail(stage(player, 1.6D), dir, colour != 0 ? colour : qi ? 0x9FD8FF : STEEL, qi, 0));
			}
			case DuelCodes.PREVIEW_BIND -> add(new Lock(stage, arg > 0 ? Math.min(200, arg) : 60, colour));
			case DuelCodes.PREVIEW_SHOCK -> {
				int c = colour != 0 ? colour : 0xFFE7B0;
				Vec3 ground = ScorchDecal.groundBelow(player.position().add(stage.look().scale(3.0D)).add(0.0D, 1.0D, 0.0D), 4.0D);
				ShockRing.ground(ground != null ? ground : player.position().add(stage.look().scale(3.0D)), c, 0.5F, 4.5F, 8);
				ShockRing.vertical(stage.at(), stage.look(), c, 0.3F, 2.2F, 6);
				MetalFlash.add(stage.at(), 1.0F, c, 4);
			}
			case DuelCodes.PREVIEW_DECAL -> {
				Vec3 flat = new Vec3(stage.look().x, 0.0D, stage.look().z);
				flat = flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
				for (int i = 0; i < 5; i++) {
					ScorchDecal.add(player.position().add(flat.scale(2.0D + i * 1.5D)), flat, 1.4F, 0.55F);
				}
				ScorchDecal.add(player.position().add(flat.scale(10.0D)), new Vec3(-flat.z, 0.0D, flat.x), 2.2F, 0.9F);
			}
			case DuelCodes.PREVIEW_RING -> DuelRingVisual.preview(player.position(), arg > 0 ? arg : 9, 400);
			default -> {
			}
		}
	}

	private static void add(Demo demo) {
		if (LIVE.size() >= 16) {
			LIVE.remove(0).end();
		}
		LIVE.add(demo);
	}

	/** The stage {@code distance} b ahead of the eye (horizontal look, so the blades stand upright). */
	private static Stage stage(LocalPlayer player, double distance) {
		Vec3 eye = player.getEyePosition();
		Vec3 view = player.getViewVector(1.0F);
		Vec3 look = new Vec3(view.x, 0.0D, view.z);
		look = look.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : look.normalize();
		Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 right = new Vec3(-look.z, 0.0D, look.x);
		return new Stage(eye, eye.add(look.scale(distance)).subtract(0.0D, 0.25D, 0.0D), look, right, up);
	}

	static void tick(Minecraft minecraft) {
		if (LIVE.isEmpty()) {
			return;
		}
		for (Iterator<Demo> it = LIVE.iterator(); it.hasNext(); ) {
			Demo demo = it.next();
			if (demo.done()) {
				demo.end();
				it.remove();
				continue;
			}
			demo.tick();
		}
	}

	static void render(DuelGeo frame) {
		for (Demo demo : LIVE) {
			demo.render(frame, demo.age() + frame.partial);
		}
	}

	static boolean idle() {
		return LIVE.isEmpty();
	}

	static void clear() {
		for (Demo demo : LIVE) {
			demo.end();
		}
		LIVE.clear();
	}

	// ================================================================ the virtual blades

	/** A blade of light: a steel body tapering to the tip, a white edge and a small cross-guard. */
	private static void blade(DuelGeo frame, Vec3 grip, Vec3 tip, float alpha, int tint) {
		if (alpha <= 0.01F || !frame.take(4)) {
			return;
		}
		int body = DuelGeo.mix(0xC8D2E4, tint, 0.25F);
		frame.segment(frame.strip, grip, tip, 0.09F, 0.03F, DuelGeo.argb(0.85F * alpha, body), DuelGeo.argb(0.85F * alpha, 0xF4F8FF), 0.0F, 1.0F);
		frame.segment(frame.add, grip, tip, 0.025F, 0.01F, DuelGeo.argb(0.9F * alpha, 0xFFFFFF), DuelGeo.argb(0.6F * alpha, 0xFFFFFF), 0.0F, 1.0F);
		Vec3 dir = tip.subtract(grip);
		Vec3 across = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (across.lengthSqr() < 1.0E-6D) {
			across = new Vec3(1.0D, 0.0D, 0.0D);
		}
		across = across.normalize().scale(0.14D);
		frame.segment(frame.strip, grip.add(across), grip.subtract(across), 0.05F, 0.05F, DuelGeo.argb(0.9F * alpha, 0x8A6A3A),
				DuelGeo.argb(0.9F * alpha, 0x8A6A3A), 0.0F, 1.0F);
		frame.segment(frame.strip, grip, grip.subtract(dir.normalize().scale(0.22D)), 0.06F, 0.06F, DuelGeo.argb(0.9F * alpha, 0x3A2418),
				DuelGeo.argb(0.9F * alpha, 0x3A2418), 0.0F, 1.0F);
	}

	private static float smooth(float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/** The axis of a swing direction code in the stage's frame (where the blade starts pointing). */
	private static Vec3 startAxis(Stage s, int dir) {
		Vec3 r = s.right();
		Vec3 u = s.up();
		return switch (dir) {
			case DuelCodes.DIR_ATAS -> u;
			case DuelCodes.DIR_ATAS_KANAN -> u.add(r).normalize();
			case DuelCodes.DIR_BAWAH_KANAN -> u.scale(-1.0D).add(r).normalize();
			case DuelCodes.DIR_BAWAH -> u.scale(-1.0D);
			case DuelCodes.DIR_BAWAH_KIRI -> u.scale(-1.0D).subtract(r).normalize();
			case DuelCodes.DIR_KIRI -> r.scale(-1.0D);
			case DuelCodes.DIR_ATAS_KIRI -> u.subtract(r).normalize();
			default -> r;
		};
	}

	// ================================================================ demos

	/** Two virtual blades meeting: the attacker's sweeps in over 4 ticks, the burst of {@code outcome} fires at the cross. */
	private static final class Clash extends Demo {
		final int outcome;
		final int rgb;
		final float power;
		final int delay;
		boolean fired;

		Clash(Stage stage, int outcome, int rgb, float power, int delay) {
			super(stage, delay + 26);
			this.outcome = outcome;
			this.rgb = rgb;
			this.power = power;
			this.delay = delay;
		}

		/** The defender's blade: held across the body, point up and to the left. */
		Vec3[] defender() {
			return new Vec3[]{stage.point(0.35D, -0.45D, 0.35D), stage.point(-0.45D, 0.55D, 0.25D)};
		}

		/** The attacker's blade at sweep progress {@code t} (0 wound up on the right, 1 at the contact, then a little past). */
		Vec3[] attacker(float t) {
			Vec3 grip = stage.point(0.15D, -0.15D, -0.75D);
			double angle = Math.toRadians(-70.0D + 70.0D * Math.min(1.15D, t));
			Vec3 dir = stage.right().scale(Math.cos(angle)).add(stage.look().scale(Math.sin(angle) + 0.9D)).add(stage.up().scale(0.25D)).normalize();
			return new Vec3[]{grip, grip.add(dir.scale(1.15D))};
		}

		@Override
		void tick() {
			if (!fired && age() >= delay + 4) {
				fired = true;
				Vec3[] a = attacker(1.0F);
				Vec3[] b = defender();
				DuelLinks.Blade ba = new DuelLinks.Blade(a[0], a[1]);
				DuelLinks.Blade bb = new DuelLinks.Blade(b[0], b[1]);
				Vec3[] pair = ContactSolver.closest(ba, bb);
				Vec3 at = pair[0].add(pair[1]).scale(0.5D);
				Vec3 normal = ba.dir().cross(bb.dir());
				normal = normal.lengthSqr() < 1.0E-6D ? stage.look() : normal.normalize();
				Vec3 velocity = stage.right().scale(-0.9D).add(stage.look().scale(0.3D));
				ContactSolver.Contact contact = new ContactSolver.Contact(at, normal, velocity, ba.dir(), bb.dir(), true);
				int self = DuelFx.localId();
				int seed = (int) (DuelFx.ticks() * 31L + outcome);
				DuelFx.ClashEvent event = new DuelFx.ClashEvent(self, -1, -1, -1, outcome, at, DuelCodes.DIR_KANAN, DuelCodes.DIR_ATAS_KIRI,
						Math.round(power * 255.0F), rgb, STEEL, 0L, seed, 0, false, 0);
				SparkBurst.play(event, contact);
				Minecraft minecraft = Minecraft.getInstance();
				if (minecraft.player != null) {
					minecraft.player.sendOverlayMessage(Component.translatable("kultivator.duel.fx.preview.outcome",
							Component.translatable("kultivator.duel.fx.outcome." + outcome)));
				}
			}
		}

		@Override
		void render(DuelGeo frame, float age) {
			float local = age - delay;
			if (local < 0.0F) {
				return;
			}
			float fade = local < 20.0F ? 1.0F : Math.max(0.0F, 1.0F - (local - 20.0F) / 6.0F);
			int tint = rgb != 0 ? rgb : STEEL;
			Vec3[] b = defender();
			// the defender's blade gives a little at the contact
			float give = local > 4.0F && local < 8.0F ? Mth.sin((local - 4.0F) / 4.0F * (float) Math.PI) * 0.06F : 0.0F;
			blade(frame, b[0].add(stage.right().scale(-give)), b[1].add(stage.right().scale(-give * 2.0F)), fade, STEEL);
			Vec3[] a = attacker(local < 4.0F ? smooth(local / 4.0F) : 1.0F + Math.min(0.15F, (local - 4.0F) * 0.02F));
			blade(frame, a[0], a[1], fade, tint);
		}
	}

	/** A sample trail: a virtual blade sweeping through the swing of {@code dir} in 4 ticks, its trail fading after. */
	private static final class Trail extends Demo {
		final int dir;
		final BladeTrail trail;
		final int delay;

		Trail(Stage stage, int dir, int rgb, boolean qi, int delay) {
			super(stage, delay + 16);
			this.dir = dir;
			this.delay = delay;
			this.trail = new BladeTrail(Integer.MIN_VALUE + 3);
			trail.qi = qi;
			trail.rgb = rgb;
			trail.core = 0xFFFFFF;
			trail.alpha = qi ? 0.8F : 0.35F;
		}

		Vec3[] bladeAt(float t) {
			Vec3 grip = stage.point(0.0D, -0.1D, -0.6D);
			if (dir == DuelCodes.DIR_TUSUK) {
				Vec3 g = grip.add(stage.look().scale(-0.4D + 0.9D * t));
				return new Vec3[]{g, g.add(stage.look().scale(1.1D))};
			}
			Vec3 start = startAxis(stage, dir);
			double angle = (dir == DuelCodes.DIR_PUTAR ? Math.PI * 2.0D : Math.PI) * t;
			// sweep from the start axis through the front to the opposite side
			Vec3 d = start.scale(Math.cos(angle)).add(stage.look().scale(Math.sin(Math.min(Math.PI, angle)) + 0.15D)).normalize();
			return new Vec3[]{grip, grip.add(d.scale(1.1D))};
		}

		@Override
		void render(DuelGeo frame, float age) {
			float local = age - delay;
			if (local < 0.0F) {
				return;
			}
			float t = smooth(local / 4.0F);
			Vec3[] b = bladeAt(t);
			if (local <= 4.5F) {
				trail.push(new DuelLinks.Blade(b[0], b[1]), frame.time);
			}
			trail.render(frame);
			blade(frame, b[0], b[1], local < 10.0F ? 1.0F : Math.max(0.0F, 1.0F - (local - 10.0F) / 6.0F), trail.rgb);
		}
	}

	/** A blade lock: two blades pressed together, the hot spot sliding with a swaying pressure, grinding sparks, a won end. */
	private static final class Lock extends Demo {
		final int rgb;
		final RandomSource random = RandomSource.create(17L);

		Lock(Stage stage, int ticks, int rgb) {
			super(stage, ticks + 20);
			this.rgb = rgb;
		}

		float pressure(float age) {
			return Mth.sin(age * 0.11F) * 70.0F + Mth.sin(age * 0.37F) * 20.0F;
		}

		Vec3[] bladeA(float age) {
			float p = pressure(age) / 100.0F;
			Vec3 grip = stage.point(0.55D, -0.35D, -0.25D + 0.08D * p);
			return new Vec3[]{grip, stage.point(-0.5D + 0.1D * p, 0.45D, 0.12D)};
		}

		Vec3[] bladeB(float age) {
			float p = pressure(age) / 100.0F;
			Vec3 grip = stage.point(-0.55D, -0.35D, 0.45D + 0.08D * p);
			return new Vec3[]{grip, stage.point(0.5D - 0.1D * p, 0.45D, 0.05D)};
		}

		Vec3 contact(float age) {
			Vec3[] a = bladeA(age);
			Vec3[] b = bladeB(age);
			DuelLinks.Blade ba = new DuelLinks.Blade(a[0], a[1]);
			Vec3[] pair = ContactSolver.closest(ba, new DuelLinks.Blade(b[0], b[1]));
			float slide = Mth.clamp(0.55F + pressure(age) / 100.0F * 0.3F + 0.08F * Mth.sin(age * 1.7F), 0.15F, 0.95F);
			return pair[0].add(pair[1]).scale(0.5D).lerp(ba.at(slide), 0.35D);
		}

		@Override
		void tick() {
			long age = age();
			int lockTicks = life - 20;
			if (age < lockTicks) {
				if (DuelTime.frozen()) {
					return;
				}
				Vec3 at = contact(age);
				int n = DuelFxSettings.pick(1, 3, 5);
				for (int i = 0; i < n; i++) {
					double sign = random.nextBoolean() ? 1.0D : -1.0D;
					Vec3 dir = SparkBurst.cone(stage.look().scale(sign * 0.6D).add(stage.up().scale(0.5D)).normalize(), 40.0F, random);
					DuelSparkParticle.spawn(at, dir.scale(0.18D + random.nextDouble() * 0.22D), 0.08F + random.nextFloat() * 0.05F,
							4.0F + random.nextFloat() * 5.0F, -1, 0.0F, 0, i == 0 ? FxBudget.Priority.CORE : FxBudget.Priority.DETAIL);
				}
				if ((age & 1L) == 0L) {
					DuelEmberParticle.spawn(at, new Vec3(0.0D, 0.03D, 0.0D), 0.06F, 28.0F, rgb != 0 ? rgb : 0xFFC070, 0);
				}
				DuelShake.bindRumble(at, DuelFx.ticks());
			} else if (age == lockTicks) {
				Vec3 at = contact(age);
				Vec3[] a = bladeA(age);
				Vec3[] b = bladeB(age);
				DuelLinks.Blade ba = new DuelLinks.Blade(a[0], a[1]);
				DuelLinks.Blade bb = new DuelLinks.Blade(b[0], b[1]);
				Vec3 normal = ba.dir().cross(bb.dir());
				normal = normal.lengthSqr() < 1.0E-6D ? stage.look() : normal.normalize();
				ContactSolver.Contact contact = new ContactSolver.Contact(at, normal, stage.look().scale(0.8D), ba.dir(), bb.dir(), true);
				DuelFx.ClashEvent event = new DuelFx.ClashEvent(DuelFx.localId(), -1, -1, -1, DuelCodes.BIND_MENANG_A, at, DuelCodes.DIR_KANAN,
						DuelCodes.DIR_KIRI, 220, rgb, STEEL, 0L, (int) DuelFx.ticks(), 0, false, 0);
				SparkBurst.play(event, contact);
			}
		}

		@Override
		void render(DuelGeo frame, float age) {
			int lockTicks = life - 20;
			float fade = age < lockTicks ? 1.0F : Math.max(0.0F, 1.0F - (age - lockTicks) / 12.0F);
			float held = Math.min(age, lockTicks);
			Vec3[] a = bladeA(held);
			Vec3[] b = bladeB(held);
			if (age > lockTicks) {
				// the loser's blade is thrown aside
				float k = Math.min(1.0F, (age - lockTicks) / 6.0F);
				b = new Vec3[]{b[0], b[1].add(stage.right().scale(0.6D * k)).add(stage.up().scale(-0.4D * k))};
			}
			blade(frame, a[0], a[1], fade, rgb != 0 ? rgb : STEEL);
			blade(frame, b[0], b[1], fade, STEEL);
			if (age < lockTicks && frame.take(3)) {
				Vec3 at = contact(age);
				float strain = Math.abs(pressure(age)) / 100.0F;
				float pulse = 0.75F + 0.25F * Mth.sin(age * 1.3F);
				float heat = 0.6F + 0.4F * strain;
				frame.billboard(frame.glow, at, 0.32F * pulse * heat + 0.1F, 0.0F, DuelGeo.argb(0.75F * heat, 0xFF8A30));
				frame.billboard(frame.glow, at, 0.12F * pulse + 0.05F, 0.0F, DuelGeo.argb(0.95F, 0xFFF0C0));
				frame.star(at, 0.22F + 0.1F * pulse * heat, age * 0.35F, 0xFFE0A0, 0.6F * heat);
			}
		}
	}

	/** The intro film with the local player as A and an entity or a virtual dummy as B. */
	private static final class IntroFilm extends Demo {
		final DuelFx.SessionView session;

		IntroFilm(Stage stage, LocalPlayer player, int partnerId) {
			super(stage, 175);
			session = fakeSession(INTRO_SESSION, player, partnerId, stage, 0);
			session.phase = DuelCodes.PHASE_INTRO;
			session.flags = DuelCodes.SESSION_INTRO_FILM;
			session.phaseTicksLeft = 100;
			DuelRingVisual.preview(session.center, session.radius, 180);
			DuelIntroFilm.start(session, 0);
		}

		@Override
		void tick() {
			long age = age();
			if (age == 100L) {
				session.phase = DuelCodes.PHASE_COUNTDOWN;
				DuelIntroFilm.countdown(session);
			} else if (age == 160L) {
				session.phase = DuelCodes.PHASE_FIGHT;
				DuelIntroFilm.stop(session);
			}
		}

		@Override
		void render(DuelGeo frame, float age) {
			if (DuelFx.entity(session.bId) == null) {
				dummy(frame, session.markB(), stage.look().scale(-1.0D), Math.min(1.0F, age / 10.0F));
			}
		}

		@Override
		void end() {
			DuelIntroFilm.stop(session);
		}
	}

	/** The finisher film: a deathblow from the local player on the entity or dummy ahead. */
	private static final class FinisherFilm extends Demo {
		final DuelFx.SessionView session;
		final int rgb;

		FinisherFilm(Stage stage, LocalPlayer player, int partnerId, int rgb) {
			super(stage, 90);
			this.rgb = rgb;
			session = fakeSession(FINISHER_SESSION, player, partnerId, stage, rgb);
			session.phase = DuelCodes.PHASE_FINISHER;
			session.flags = DuelCodes.SESSION_DECISIVE;
			session.phaseTicksLeft = 60;
			Entity partner = DuelFx.entity(session.bId);
			Vec3 at = partner != null ? partner.position().add(0.0D, partner.getBbHeight() * 0.62D, 0.0D) : stage.at();
			Vec3 normal = stage.right().cross(stage.up());
			ContactSolver.Contact contact = new ContactSolver.Contact(at, normal.lengthSqr() < 1.0E-6D ? stage.look() : normal.normalize(),
					stage.right().scale(-0.9D), stage.right(), stage.up(), true);
			DuelFx.ClashEvent event = new DuelFx.ClashEvent(player.getId(), session.bId, -1, -1, DuelCodes.DEATHBLOW, at, DuelCodes.DIR_ATAS_KANAN,
					DuelCodes.DIR_ATAS, 255, rgb != 0 ? rgb : SparkBurst.GOLD, STEEL, 0L, (int) DuelFx.ticks(), DuelCodes.CLASH_DECISIVE, false, 0);
			SparkBurst.play(event, contact);
			DuelFinisherFilm.decisive(session, event);
		}

		@Override
		void tick() {
			if (age() == 60L) {
				session.phase = DuelCodes.PHASE_ENDED;
			}
		}

		@Override
		void render(DuelGeo frame, float age) {
			if (DuelFx.entity(session.bId) == null) {
				// the dummy sinks to its knees
				float kneel = Math.min(1.0F, Math.max(0.0F, (age - 24.0F) / 10.0F));
				dummy(frame, session.markB().subtract(0.0D, 0.45D * kneel, 0.0D), stage.look().scale(-1.0D), 1.0F - Math.max(0.0F, (age - 70.0F) / 20.0F));
			}
		}
	}

	/** A session that exists only in the preview (never in {@link DuelFx#sessions}). */
	private static DuelFx.SessionView fakeSession(int id, LocalPlayer player, int partnerId, Stage stage, int rgb) {
		DuelFx.SessionView s = new DuelFx.SessionView(id);
		Entity partner = partnerId >= 0 ? DuelFx.entity(partnerId) : null;
		Vec3 a = player.position();
		Vec3 b = partner != null ? partner.position() : a.add(stage.look().scale(6.0D));
		s.aId = player.getId();
		s.bId = partner != null ? partner.getId() : -1;
		s.center = a.lerp(b, 0.5D);
		s.radius = 9;
		s.markAx = (float) a.x;
		s.markAz = (float) a.z;
		s.markBx = (float) b.x;
		s.markBz = (float) b.z;
		s.aName = player.getDisplayName();
		s.bName = partner != null ? partner.getDisplayName() : Component.translatable("kultivator.duel.fx.preview.dummy");
		s.aRank = Component.translatable("kultivator.duel.fx.preview.rank");
		s.bRank = Component.translatable("kultivator.duel.fx.preview.rank");
		s.aRgb = rgb != 0 ? rgb : 0xE8C070;
		s.bRgb = 0x9FB8D8;
		s.pair = DuelCodes.PAIR_UJI;
		s.lastSync = DuelFx.ticks();
		return s;
	}

	/** The virtual dummy: a faint ink silhouette standing at {@code feet}, facing {@code facing}, holding a blade. */
	private static void dummy(DuelGeo frame, Vec3 feet, Vec3 facing, float alpha) {
		if (alpha <= 0.01F || !frame.take(8)) {
			return;
		}
		Vec3 side = new Vec3(-facing.z, 0.0D, facing.x);
		int ink = DuelGeo.argb(0.55F * alpha, 0x1A1820);
		Vec3 hip = feet.add(0.0D, 0.75D, 0.0D);
		Vec3 neck = feet.add(0.0D, 1.45D, 0.0D);
		Vec3 head = feet.add(0.0D, 1.72D, 0.0D);
		frame.segment(frame.strip, feet.add(side.scale(0.15D)), hip, 0.16F, 0.18F, ink, ink, 0.0F, 1.0F);
		frame.segment(frame.strip, feet.subtract(side.scale(0.15D)), hip, 0.16F, 0.18F, ink, ink, 0.0F, 1.0F);
		frame.segment(frame.strip, hip, neck, 0.42F, 0.46F, ink, ink, 0.0F, 1.0F);
		frame.billboard(frame.glow, head, 0.2F, 0.0F, ink);
		Vec3 hand = neck.add(side.scale(-0.35D)).add(facing.scale(0.35D)).subtract(0.0D, 0.35D, 0.0D);
		frame.segment(frame.strip, neck.add(side.scale(-0.22D)), hand, 0.12F, 0.1F, ink, ink, 0.0F, 1.0F);
		blade(frame, hand, hand.add(facing.scale(0.6D)).add(0.0D, 0.85D, 0.0D), alpha, STEEL);
	}

	private DuelPreviews() {
	}
}
