package dev.kultivator.client.dampak.cinema;

import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.Element;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.fx.EpicDisplays;
import dev.kultivator.client.epic.fx.EpicParticles;
import dev.kultivator.client.epic.render.EpicRenderer;
import dev.kultivator.client.epic.render.RingRenderer;
import dev.kultivator.client.fx.qi.Qi;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tanda Langit (Phase 10, DESIGN 28.2): before an ultimate lands, heaven marks the spot. A thin pillar of light in the
 * row's colour comes down from the clouds onto the aim, a glyph ring turns on the ground and tightens 2× → 1× (the
 * lock-on), qi motes rise, and for the caster a seal bracket closes around the crosshair. Bystanders see the pillar and
 * the ring (a fair warning in PvP; "Efek pemain lain: Ringan" ring only, "Mati" nothing). Everything is a 7b element of
 * the cast's scene ({@link EpicDirector#dampakScene}), so it shares the 7b budget, the frame guard and the particle
 * fallback; no new packet: the marks are read from the TechFx stream. Client thread only.
 */
public final class TargetMark {

	private static final Identifier BRACKET = Kultivator.id("textures/gui/dampak/seal_bracket.png");
	private static final int GOLD = 0xFFD54A;
	private static final float PILLAR_HEIGHT = 72.0F;

	/** Live marks by cast (caster id, spec net id). */
	private static final Map<Long, Cast> CASTS = new HashMap<>();

	// The caster's crosshair seal (own marks only).
	private static long hudStart;
	private static long hudEnd;
	private static long hudSnap;
	private static int hudRgb = GOLD;

	private TargetMark() {
	}

	/** One cast's marks: the main mark (it may follow the own crosshair while charging) and the sub-marks of MULTI / FOOT. */
	private static final class Cast {
		@Nullable MarkElement main;
		final List<MarkElement> subs = new ArrayList<>();
		int subCount;
		boolean follow;
		double range = 32.0D;
		long born;
	}

	// ================================================================ the TechFx stream

	/** One event of a Dampak spec (the hub calls this after the sound, before the rides and frames). */
	public static void onTechFx(Payloads.TechFx p, TechniqueSpec spec, Dampak d, Phase phase, boolean own, float othersScale) {
		long key = key(p.caster(), spec.netId());
		if (phase == Phase.ABORT || phase == Phase.END) {
			end(key, phase == Phase.ABORT ? 4 : 10);
			if (own) {
				hudEnd = Math.min(hudEnd, Util.getMillis() + 200L);
			}
			return;
		}
		if (d.mark() == Dampak.Mark.NONE || (!own && othersScale <= 0.0F) || p.has(Payloads.TechFx.FLAG_ECHO)) {
			return;
		}
		boolean charged = spec.charge() != null;
		boolean climax = p.has(Payloads.TechFx.FLAG_CLIMAX) && phase != Phase.PULSE && phase != Phase.MASS_HIT;
		Cast cast = CASTS.get(key);
		if (climax) {
			climax(key, cast, own);
			return;
		}
		Vec3 pos = new Vec3(p.x(), p.y(), p.z());
		Vec3 aim = aim(p, d.mark());
		int rgb = colour(spec, d, p);
		boolean opening = phase == Phase.CHARGE || phase == Phase.CAST;
		if (opening) {
			if (phase == Phase.CAST && charged && cast != null && cast.main != null) {
				// the release: the mark eases from the crosshair to the server's point in 4 ticks
				cast.follow = false;
				cast.main.easeTo(aim, 4);
				return;
			}
			if (phase == Phase.CAST && cast != null && cast.main != null && !cast.main.finished()) {
				cast.main.easeTo(aim, 4);
				return;
			}
			int life = markLife(spec, d);
			Scene scene = EpicDirector.dampakScene(p.caster(), own, pos, p.seed(), life + 40);
			if (scene == null) {
				return;
			}
			Cast fresh = new Cast();
			fresh.born = Util.getMillis();
			fresh.follow = own && phase == Phase.CHARGE;
			fresh.range = Math.max(12.0D, Math.min(64.0D, pos.distanceTo(aim)));
			boolean light = lightOnly(own, p);
			Dampak.Mark kind = d.mark() == Dampak.Mark.MULTI ? Dampak.Mark.RING_ONLY : d.mark();
			fresh.main = new MarkElement(life, ground(aim), pos, kind, ringRadius(d.mark(), p), rgb, light, EpicDirector.seal(spec), own);
			scene.add(fresh.main);
			CASTS.put(key, fresh);
			if (own) {
				hud(rgb, life);
			}
			return;
		}
		boolean pre = p.has(Payloads.TechFx.FLAG_PRE_IMPACT);
		boolean sub = pre && (phase == Phase.TRAVEL && d.mark() == Dampak.Mark.MULTI || phase == Phase.PHANTOM
				&& (d.mark() == Dampak.Mark.MULTI || d.mark() == Dampak.Mark.FOOT));
		boolean foot = d.mark() == Dampak.Mark.FOOT && phase == Phase.EXTRA0;
		if (!sub && !foot) {
			return;
		}
		Cast owner = cast;
		if (owner == null) {
			owner = new Cast();
			owner.born = Util.getMillis();
			CASTS.put(key, owner);
		}
		int cap = EpicBudget.tierOrMinimal() == 0 ? 3 : 9;
		if (owner.subCount >= cap) {
			return;
		}
		Scene scene = EpicDirector.dampakScene(p.caster(), own, pos, p.seed(), 200);
		if (scene == null) {
			return;
		}
		owner.subCount++;
		Vec3 at = ground(p.tx() == 0.0D && p.ty() == 0.0D && p.tz() == 0.0D ? pos : new Vec3(p.tx(), p.ty(), p.tz()));
		int life = Math.max(10, Math.min(100, foot ? 14 : p.duration()));
		Dampak.Mark kind = foot || d.mark() == Dampak.Mark.FOOT ? Dampak.Mark.FOOT : Dampak.Mark.PILLAR;
		MarkElement mark = new MarkElement(life, at, pos, kind, 2.5F, rgb, lightOnly(own, p) || EpicBudget.tierOrMinimal() < 2 && owner.subCount > 3,
				kind == Dampak.Mark.FOOT ? "足" : "", own);
		mark.small = true;
		scene.add(mark);
		owner.subs.add(mark);
	}

	/** The climax: the main mark becomes the impact (the pillar narrows to a thread and goes out in 4 ticks). */
	private static void climax(long key, @Nullable Cast cast, boolean own) {
		if (cast != null && cast.main != null) {
			cast.main.snap();
			cast.follow = false;
		}
		CASTS.remove(key);
		if (own) {
			long now = Util.getMillis();
			hudSnap = now;
			hudEnd = Math.min(hudEnd, now + 250L);
		}
	}

	private static void end(long key, int ticks) {
		Cast cast = CASTS.remove(key);
		if (cast == null) {
			return;
		}
		if (cast.main != null) {
			cast.main.endIn(ticks);
		}
		for (MarkElement sub : cast.subs) {
			sub.endIn(ticks);
		}
	}

	// ================================================================ per tick

	/** Once per client tick: the own charge mark follows the crosshair; old entries are pruned. */
	public static void tick(Minecraft minecraft) {
		if (CASTS.isEmpty()) {
			return;
		}
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		long now = Util.getMillis();
		for (Iterator<Map.Entry<Long, Cast>> it = CASTS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Long, Cast> entry = it.next();
			Cast cast = entry.getValue();
			boolean dead = (cast.main == null || cast.main.finished()) && cast.subs.stream().allMatch(Element::finished);
			if (dead || now - cast.born > 30_000L) {
				it.remove();
				continue;
			}
			if (cast.follow && cast.main != null && player != null && level != null && (int) (entry.getKey() >> 32) == player.getId()) {
				// one clip per tick along the look, blocks only, at most the spec's reach (purely visual: the server aims)
				Vec3 eye = player.getEyePosition();
				Vec3 end = eye.add(player.getViewVector(1.0F).scale(cast.range));
				BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
				Vec3 at = hit.getType() == HitResult.Type.MISS ? ground(end) : hit.getLocation();
				cast.main.easeTo(at, 2);
			}
		}
	}

	/** Ends every mark (dimension change, disconnect, stop). */
	public static void clear() {
		for (Cast cast : CASTS.values()) {
			if (cast.main != null) {
				cast.main.endIn(1);
			}
			for (MarkElement sub : cast.subs) {
				sub.endIn(1);
			}
		}
		CASTS.clear();
		hudEnd = 0L;
	}

	/** The pillar the own sky ride climbs (28.3.2 R1): a fresh mark pillar at the aim for the ride's length. */
	static void ridePillar(Scene scene, Vec3 aim, int rgb, int life) {
		MarkElement pillar = new MarkElement(life, ground(aim), aim, Dampak.Mark.PILLAR, 4.0F, rgb, false, "", true);
		pillar.noGlyph = true;
		scene.add(pillar);
	}

	/** {@code /sinematik dampak tanda <id>}: the mark of a spec ahead of the player for {@code life} ticks, then its climax. */
	public static boolean test(TechniqueSpec spec, Dampak d, Vec3 aim, int life) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return false;
		}
		Scene scene = EpicDirector.dampakScene(minecraft.player.getId(), true, minecraft.player.position(), minecraft.player.getId() * 31, life + 40);
		if (scene == null) {
			return false;
		}
		Dampak.Mark kind = d.mark() == Dampak.Mark.NONE ? Dampak.Mark.PILLAR : d.mark() == Dampak.Mark.MULTI ? Dampak.Mark.RING_ONLY : d.mark();
		int rgb = colour(spec, d, null);
		MarkElement mark = new MarkElement(life, ground(aim), minecraft.player.position(), kind, 5.0F, rgb, false, EpicDirector.seal(spec), true);
		scene.add(mark);
		scene.at(Math.max(1, life - 4), mark::snap);
		hud(rgb, life);
		return true;
	}

	// ================================================================ helpers

	private static long key(int caster, int spec) {
		return ((long) caster << 32) | (spec & 0xFFFFFFFFL);
	}

	/** Others' marks with "Efek pemain lain: Ringan" and a server under load: the ring only. */
	private static boolean lightOnly(boolean own, Payloads.TechFx p) {
		return p.has(Payloads.TechFx.FLAG_LOW_LOAD) || !own && EffectQuality.others() == EffectQuality.Others.LIGHT;
	}

	/** Mark life: the row's markTicks, else until the climax (≤ 200), a lasting field 20 ticks past its start. */
	private static int markLife(TechniqueSpec spec, Dampak d) {
		if (d.markTicks() > 0) {
			return Math.min(Dampak.MAX_MARK_TICKS, d.markTicks());
		}
		if (spec.lasting() && d.climaxAt() > 60) {
			return 40;
		}
		int until = d.climaxAt() > 0 ? d.climaxAt() + 4 : 60;
		return Math.max(20, Math.min(Dampak.MAX_MARK_TICKS, until));
	}

	/** The aim of the event: the resolved {@code to} (a field centre, a beam's far end), or the event point. */
	private static Vec3 aim(Payloads.TechFx p, Dampak.Mark mark) {
		boolean hasTo = p.tx() != 0.0D || p.ty() != 0.0D || p.tz() != 0.0D;
		if (!hasTo || !Double.isFinite(p.tx()) || !Double.isFinite(p.ty()) || !Double.isFinite(p.tz())) {
			return new Vec3(p.x(), p.y(), p.z());
		}
		return new Vec3(p.tx(), p.ty(), p.tz());
	}

	private static float ringRadius(Dampak.Mark mark, Payloads.TechFx p) {
		float r = p.radius();
		if (mark == Dampak.Mark.RING_ONLY) {
			return r > 0.0F ? Math.max(4.0F, Math.min(40.0F, r)) : 8.0F;
		}
		if (mark == Dampak.Mark.SHADOW) {
			return r > 0.0F ? Math.max(3.0F, Math.min(16.0F, r)) : 6.0F;
		}
		return r > 0.0F ? Math.max(4.0F, Math.min(6.0F, r * 0.5F)) : 5.0F;
	}

	/**
	 * The mark's colour: the row's second frame colour when it is a real colour, else the first, else the Dao's colour
	 * (own arts: the first stored Dao), else gold.
	 */
	static int colour(TechniqueSpec spec, Dampak d, @Nullable Payloads.TechFx p) {
		int dao = spec.dao() != null ? spec.dao().color() : GOLD;
		for (int frame : new int[]{d.frame2(), d.frame1(), d.frame3()}) {
			if (frame == Dampak.DAO) {
				return dao & 0xFFFFFF;
			}
			if (frame >= 0 && Qi.luma(frame) > 0.18F) {
				return frame & 0xFFFFFF;
			}
		}
		return dao & 0xFFFFFF;
	}

	/** Snaps a point onto the ground below it (≤ 12 b; the point itself when nothing is below). */
	public static Vec3 ground(Vec3 at) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return at;
		}
		BlockPos.MutableBlockPos pos = BlockPos.containing(at.x, at.y + 0.2D, at.z).mutable();
		for (int i = 0; i < 12; i++) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && state.getFluidState().isEmpty()) {
				return new Vec3(at.x, pos.getY() + 1.0D, at.z);
			}
			pos.move(0, -1, 0);
		}
		return at;
	}

	// ================================================================ the crosshair seal (own marks)

	private static void hud(int rgb, int lifeTicks) {
		long now = Util.getMillis();
		hudStart = now;
		hudEnd = now + Math.max(10, lifeTicks) * 50L;
		hudSnap = 0L;
		hudRgb = rgb;
	}

	/** The caster's seal bracket closing around the crosshair (2 quads per corner, under the impact frames). */
	public static final class Hud implements HudElement {
		@Override
		public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
			long now = Util.getMillis();
			if (hudEnd == 0L || now >= hudEnd) {
				return;
			}
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player == null || minecraft.gui.hud.isHidden() || EpicDirector.cinematicActive()) {
				return;
			}
			float life = Math.max(1.0F, hudEnd - hudStart);
			float t = Math.min(1.0F, (now - hudStart) / Math.min(life, 2000.0F));
			float eased = 1.0F - (1.0F - t) * (1.0F - t);
			float in = Math.min(1.0F, (now - hudStart) / 150.0F);
			float out = Math.min(1.0F, (hudEnd - now) / 200.0F);
			float a = Math.min(in, out) * 0.85F;
			float distance = 30.0F - 20.0F * eased;
			if (hudSnap > 0L) {
				// the lock: the bracket snaps shut and flares
				float s = Math.min(1.0F, (now - hudSnap) / 120.0F);
				distance = 10.0F - 6.0F * s;
				a = Math.min(1.0F, a + 0.3F);
			}
			int size = 12;
			int color = Palette.argb(Math.round(a * 255.0F), hudRgb);
			float cx = g.guiWidth() / 2.0F;
			float cy = g.guiHeight() / 2.0F;
			for (int k = 0; k < 4; k++) {
				g.pose().pushMatrix();
				g.pose().translate(cx, cy);
				g.pose().rotate((float) (Math.PI / 2.0D * k));
				int d = Math.round(distance);
				g.blit(RenderPipelines.GUI_TEXTURED, BRACKET, -d - size, -d - size, 0.0F, 0.0F, size, size, 64, 64, 64, 64, color);
				g.pose().popMatrix();
			}
		}
	}

	// ================================================================ the world mark

	/**
	 * One mark on the ground (a 7b element): the descending pillar, the turning glyph ring that tightens 2× → 1×, the
	 * Maximal halo and dotted outer ring, the SHADOW disc, the PATH line, the FOOT glyph, and rising qi motes. The
	 * centre can move (the own charge follows the crosshair). Falls back to particles without geometry.
	 */
	static final class MarkElement extends Element {
		private final Dampak.Mark kind;
		private final Vec3 from;
		private final float radius;
		private final int rgb;
		private final boolean light;
		private final String glyph;
		private final boolean own;
		private Vec3 centre;
		private Vec3 easeFrom;
		private Vec3 easeTo;
		private int easeTicks;
		private int easeLeft;
		private float snapAt = -1.0F;
		private float motes;
		boolean small;
		boolean noGlyph;

		MarkElement(int life, Vec3 centre, Vec3 from, Dampak.Mark kind, float radius, int rgb, boolean light, String glyph, boolean own) {
			super(0, life);
			this.centre = centre;
			this.easeFrom = centre;
			this.easeTo = centre;
			this.from = from;
			this.kind = kind;
			this.radius = radius;
			this.rgb = rgb & 0xFFFFFF;
			this.light = light;
			this.glyph = glyph == null ? "" : glyph;
			this.own = own;
			this.largeRank = radius > 16.0F ? 6 : 0;
		}

		/** Eases the centre to {@code target} over {@code ticks}. */
		void easeTo(Vec3 target, int ticks) {
			easeFrom = centre;
			easeTo = target;
			easeTicks = Math.max(1, ticks);
			easeLeft = easeTicks;
		}

		/** The climax: the pillar narrows to a thread and everything goes out within 4 ticks. */
		void snap() {
			if (snapAt < 0.0F) {
				snapAt = age;
				life = Math.min(life, age + 4.0F);
			}
		}

		/** Ends within {@code ticks} (ABORT, END, clear). */
		void endIn(int ticks) {
			life = Math.min(life, age + Math.max(1, ticks));
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
		protected void start() {
			if (!glyph.isEmpty() && !noGlyph && !light && EpicBudget.tierOrMinimal() >= 1 && EpicDisplays.canCreate()) {
				boolean footGlyph = kind == Dampak.Mark.FOOT;
				Vec3 at = footGlyph ? centre.add(0.0D, 0.3D, 0.0D) : centre.add(0.0D, small ? 2.0D : 3.2D, 0.0D);
				EpicDisplays.glyph(at, glyph, footGlyph ? 0xFFD54A : rgb, footGlyph ? 1.6F : small ? 1.2F : 2.2F, Math.max(6, Math.round(life)));
			}
		}

		@Override
		protected void tick() {
			if (easeLeft > 0) {
				easeLeft--;
				float t = 1.0F - easeLeft / (float) easeTicks;
				float e = t * t * (3.0F - 2.0F * t);
				centre = easeFrom.lerp(easeTo, e);
			}
			if (light || snapAt >= 0.0F) {
				return;
			}
			// qi motes rising from the ring: 4 / 12 / 24 per second
			int tier = EpicBudget.tierOrMinimal();
			motes += (tier == 0 ? 4.0F : tier == 1 ? 12.0F : 24.0F) / 20.0F * (small ? 0.4F : 1.0F);
			int n = EpicParticles.grant((int) motes, false);
			motes -= (int) motes;
			ParticleOptions particle = Qi.on() ? Qi.mote(rgb, 0.22F, 30) : EpicParticles.dust(rgb, 0.9F);
			float r = currentRadius(age);
			for (int i = 0; i < n; i++) {
				double angle = EpicParticles.random().nextDouble() * Math.PI * 2.0D;
				double rr = r * (0.3D + 0.7D * EpicParticles.random().nextDouble());
				EpicParticles.add(particle, centre.x + Math.cos(angle) * rr, centre.y + 0.1D, centre.z + Math.sin(angle) * rr, 0.0D, 0.06D + 0.04D
						* EpicParticles.random().nextDouble(), 0.0D);
			}
		}

		@Override
		protected void fallback() {
			// Minimal or no geometry: a particle ring every 4 ticks and a thin column every 2 ticks
			int a = (int) age;
			if (a % 4 == 0) {
				EpicParticles.ring(centre.add(0.0D, 0.1D, 0.0D), Math.min(radius, 3.0F + (radius - 3.0F) * 0.3F), small ? 8 : 16,
						EpicParticles.dust(rgb, 1.0F), 0.0F);
			}
			if (!light && a % 2 == 0 && kind != Dampak.Mark.RING_ONLY && kind != Dampak.Mark.SHADOW && kind != Dampak.Mark.FOOT) {
				EpicParticles.column(centre, small ? 6.0F : 14.0F, 0.15F, 1, EpicParticles.dust(rgb, 1.2F), 0.05F);
			}
		}

		private float currentRadius(float at) {
			if (kind == Dampak.Mark.SHADOW) {
				float grow = Math.min(1.0F, at / Math.max(1.0F, life * 0.8F));
				return radius * (0.3F + 0.7F * grow);
			}
			float contract = Math.min(1.0F, at / Math.max(1.0F, Math.min(life, 40.0F)));
			float eased = 1.0F - (1.0F - contract) * (1.0F - contract);
			return radius * (2.0F - eased);
		}

		@Override
		public void render(EpicRenderer.Frame frame, float at) {
			float f = fade(at, 4.0F, snapAt >= 0.0F ? 3.0F : 8.0F);
			if (f <= 0.01F) {
				return;
			}
			int tier = EpicBudget.tier();
			float r = currentRadius(at);
			float x = frame.rx(centre.x);
			float y = frame.ry(centre.y + 0.05D);
			float z = frame.rz(centre.z);
			switch (kind) {
				case SHADOW -> {
					// a dark soft disc the size of the falling object, growing under it (never additive)
					if (EpicBudget.takeQuads(24)) {
						RingRenderer.annulus(frame, frame.ring, x, y, z, 0.0F, r, 24, ARGB.color(Math.round(0.55F * f * 255.0F), 0x0C0A0A),
								ARGB.color(Math.round(0.15F * f * 255.0F), 0x0C0A0A), 0.0F);
					}
					return;
				}
				case FOOT -> {
					if (EpicBudget.takeQuads(1)) {
						frame.flat(frame.rune, x, y, z, 1.6F, (float) Math.toRadians(at * 3.0F), ARGB.color(Math.round(0.8F * f * 255.0F), 0xFFD54A));
					}
					return;
				}
				default -> {
				}
			}
			// the turning glyph ring (the lock-on)
			if (EpicBudget.takeQuads(1)) {
				frame.flat(frame.rune, x, y, z, small ? r * 0.6F : r, (float) Math.toRadians(at * (small ? 4.0F : 2.0F)),
						ARGB.color(Math.round(0.85F * f * 255.0F), rgb));
			}
			if (tier >= 2 && !small && !light && EpicBudget.takeQuads(32)) {
				// Maximal: an outer dotted ring (a thin band with the ring texture's dotted grain)
				float outer = r * 1.3F;
				RingRenderer.annulus(frame, frame.ring, x, y + 0.01F, z, outer - 0.25F, outer, 32, ARGB.color(Math.round(0.35F * f * 255.0F), rgb),
						ARGB.color(Math.round(0.6F * f * 255.0F), rgb), (float) (frame.clock * 0.02D));
			}
			if (kind == Dampak.Mark.PATH && !light && EpicBudget.takeQuads(2)) {
				// a glowing line along the beam or front, with the ring at its end
				float ax = frame.rx(from.x);
				float ay = frame.ry(from.y + 0.15D);
				float az = frame.rz(from.z);
				int c0 = ARGB.color(Math.round(0.2F * f * 255.0F), rgb);
				int c1 = ARGB.color(Math.round(0.85F * f * 255.0F), rgb);
				frame.segment(frame.glow, ax, ay, az, x, y + 0.1F, z, 0.4F, 0.7F, c0, c1, 0.0F, 1.0F);
			}
			if (light || kind == Dampak.Mark.RING_ONLY || tier < 1) {
				return;
			}
			// the pillar of heaven's light coming down from the clouds (28.2: glow 0.6, a thread over the last 4 ticks)
			float descend = Math.min(1.0F, at / 8.0F);
			float height = small ? 28.0F : PILLAR_HEIGHT;
			float glow = small ? 0.3F : 0.6F;
			if (snapAt >= 0.0F) {
				glow = Math.max(0.12F, glow - (glow - 0.15F) * Math.min(1.0F, (at - snapAt) / 2.0F));
			}
			glow *= Math.max(0.2F, f);
			double bottom = centre.y + height * (1.0F - descend);
			int blocks = Math.max(1, Math.round(height * descend));
			boolean big = glow > 0.5F || height > 40.0F;
			if ((!big || EpicBudget.takePillar()) && EpicBudget.takeQuads(big ? 8 : 16)) {
				frame.pillar(centre.x, bottom, centre.z, blocks, rgb, glow, (float) frame.clock);
				if (tier >= 2 && !small && own && EpicBudget.takeQuads(8)) {
					// Maximal: a second, fainter halo pillar (glow 1.4, the colour at a third)
					frame.pillar(centre.x, bottom, centre.z, blocks, Qi.darken(rgb, 0.65F), 1.4F * Math.max(0.2F, f), (float) frame.clock);
				}
			}
		}
	}
}
