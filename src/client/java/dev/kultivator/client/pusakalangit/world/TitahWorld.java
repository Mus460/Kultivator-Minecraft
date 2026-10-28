package dev.kultivator.client.pusakalangit.world;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.dampak.world.DampakWorld;
import dev.kultivator.client.dampak.world.DampakWorldBudget;
import dev.kultivator.client.dampak.world.SigilRenderer;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.fx.spec.CameraShake;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.pusakalangit.TitahItems;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.SigilPattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 10b Titah Langit, the client world (DESIGN 29.5, 29.6, 29.8; module M3, contract C10b.6): the TechFx hub of the
 * four decrees' world pieces and the registration of the monument renderer. Everything here is client-only and visual:
 * falling weapons ({@link RelicFall}), sky marks and light pillars ({@link SkyMarks}), the cleaving fissure
 * ({@link CleaveLine}), the Seven Stars ({@link StarFall}) and the light grid, sky-carried slabs and stepped pit
 * ({@link LiftCarry}); rings, sigils, debris and decals go through the Phase 10 systems ({@code DampakWorld.titah*}).
 * Real blocks change only on the server (Mode Kehancuran, {@code WorldBreaker}); nothing here reads or sends anything.
 * <p>
 * Under the server's LOW_LOAD flag only rings, sigils and the light grid draw (29.8). Event decoding (29.3.1): {@code radius} = R_vis, {@code length} = line length (Pedang) or square half-side (Gunung),
 * {@code duration} = ticks to the climax on CAST and ticks to land on a pre-impact TRAVEL, {@code aux} bits 0-9 the
 * speed × 100, bit 10 a monument planned, bit 11 real Kehancuran, bits 12-13 the decree. Every public method never throws
 * and runs on the client thread; idle cost is one boolean per frame.
 * <p>
 * Tingkat Titah (DESIGN 29.16.10, contract C10b.7): bits 14-17 of {@code aux} carry the tier − 1. The tier's world scale
 * {@code s = 1.6^(t − 1)} ({@link #tierScale}) and {@code q = sqrt(s)} size everything here: the server's radii and
 * lengths already carry it, the weapons grow by {@code s} (≤ 1100), heights and widths by {@code q}. Tier 1 draws exactly
 * as before. From tier 2 the giant weapons are {@link FarRelic}s, rings get a far horizon arc, the cleaving line a far
 * streak and the grid its true size; whatever lies beyond render distance draws with fewer layers or not at all.
 */
public final class TitahWorld {

	public static final int PEDANG = 0;
	public static final int TIANG = 1;
	public static final int BINTANG = 2;
	public static final int GUNUNG = 3;

	static final int GOLD = 0xFFD54A;
	static final int WHITE_GOLD = 0xFFF4D0;
	static final int EMBER = 0xFF7A1A;
	static final int STAR_WHITE = 0xE8F0FF;
	static final int VIOLET = 0xB57CFF;
	static final int EMBER_VIOLET = 0xD27AF0;
	static final int JADE_GOLD = 0xFFE070;
	static final int SEAL_VIOLET = 0x6A4AFF;
	static final int DUST = 0xE6D8BC;

	private static final int MAX_SCHEDULED = 96;
	private static final int CAST_MEMORY = 1200;

	/** What the client remembers of one decree cast (caster × spec). */
	static final class Cast {
		final long key;
		final int skill;
		final TechniqueSpec spec;
		final @Nullable Dampak dampak;
		final boolean own;
		final int caster;
		Vec3 castPos;
		Vec3 aim;
		/** R_vis as sent and as this client draws it. */
		float rawRadius;
		float radius;
		float length;
		int count;
		float speed = 1.0F;
		boolean monument;
		boolean kehancuran;
		boolean lowLoad;
		float others;
		int seed;
		/** Tingkat Titah: the tier (1-9), its world scale {@code s} and {@code q = sqrt(s)}. */
		int tier = 1;
		float s = 1.0F;
		float q = 1.0F;
		double born;
		double climaxEta;
		boolean climaxed;
		boolean fell;
		boolean ascended;
		int ignited;
		double lastSeen;

		Cast(long key, int skill, TechniqueSpec spec, boolean own, int caster) {
			this.key = key;
			this.skill = skill;
			this.spec = spec;
			this.dampak = spec.dampak();
			this.own = own;
			this.caster = caster;
		}

		/** Authored ticks (at Kecepatan 1) as presentation ticks at this cast's speed. */
		int t(int ticks) {
			return Math.max(1, Math.round(ticks / Math.max(0.3F, speed)));
		}

		/** Kekuatan as seen from the radius (R_vis / (the decree's base radius at ×1 × the tier's scale)). */
		float kekuatan() {
			float base = switch (skill) {
				case PEDANG -> 48.0F;
				case TIANG -> 64.0F;
				case BINTANG -> 48.0F;
				default -> 32.0F;
			};
			return Math.max(0.1F, rawRadius / (base * Math.max(1.0F, s)));
		}

		/** A weapon's model scale at this tier: the tier-1 scale × s, at most 1100 (29.16.1 mScale). */
		float giant(float scale1) {
			return Math.min(TitahMonumentRenderer.MAX_SCALE, scale1 * s);
		}

		/** The horizontal unit direction caster → aim (or +z). */
		Vec3 dir() {
			Vec3 d = new Vec3(aim.x - castPos.x, 0.0D, aim.z - castPos.z);
			return d.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : d.normalize();
		}

		/** The ground point of the aim on this client. */
		Vec3 ground(ClientLevel level) {
			return new Vec3(aim.x, DampakWorld.titahGround(level, aim.x, aim.z, aim.y), aim.z);
		}
	}

	private record Scheduled(double at, long cast, Runnable action) {
	}

	private static final Map<Long, Cast> CASTS = new HashMap<>();
	private static final List<Scheduled> SCHEDULED = new ArrayList<>();
	private static final Map<String, Integer> WARNINGS = new HashMap<>();

	private static @Nullable Payloads.TechFx lastPayload;
	private static @Nullable ClientLevel lastLevel;
	private static boolean registered;
	private static boolean active;
	/** The tier of the last decree seen (stats). */
	private static int lastTier = 1;

	// ================================================================== C10b.6

	/** Registers the monument renderer, the client tick and the disconnect cleanup (once). */
	public static void init() {
		if (registered) {
			return;
		}
		registered = true;
		EntityRenderers.register(TitahItems.MONUMENT, TitahMonumentRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			try {
				tick(minecraft);
			} catch (RuntimeException exception) {
				warn("tick", exception);
			}
		});
		ClientDisconnect.register((handler, client) -> client.execute(() -> clear()));
	}

	/** True for the four Titah decree specs. */
	public static boolean isTitah(@Nullable TechniqueSpec spec) {
		return spec != null && spec.id() != null && spec.id().startsWith("titah_");
	}

	/**
	 * One technique event of a Titah spec. {@code DampakClient} calls this after the cinema and {@code DampakWorld}
	 * forwards Titah events here too; the second copy of the same event object is dropped.
	 */
	public static void onTechFx(Payloads.TechFx p, TechniqueSpec spec, Phase phase, boolean own, float othersScale) {
		if (p == null || p == lastPayload) {
			return;
		}
		lastPayload = p;
		try {
			handle(p, spec, phase, own, othersScale);
		} catch (RuntimeException exception) {
			warn("techfx", exception);
		}
	}

	/** Live pieces and the last frame's state (for {@code /sinematik pusaka info}). */
	public static String stats() {
		try {
			return String.format(Locale.ROOT,
					"Titah dunia: titah %d, jatuh %d, langit %d, belahan %d, bintang %d, kisi/lubang %d, slab %d, monumen %d, antre %d, tier %d; tingkat terakhir %d (x%.2f); %s",
					CASTS.size(), RelicFall.count(), SkyMarks.count(), CleaveLine.count(), StarFall.count(), LiftCarry.count(), DampakWorld.titahCells(),
					MonumentNotes.count(), SCHEDULED.size(), DampakWorldBudget.tier(), lastTier, tierScale(lastTier), FarRelic.stats());
		} catch (RuntimeException exception) {
			return "Titah dunia: -";
		}
	}

	/** Clears every Titah world visual (stop, disconnect, dimension change). Only this package's own state. */
	public static void clear() {
		try {
			RelicFall.clear();
			SkyMarks.clear();
			CleaveLine.clear();
			StarFall.clear();
			LiftCarry.clear();
			MonumentNotes.clear();
			FarRelic.clear();
			CASTS.clear();
			SCHEDULED.clear();
			lastPayload = null;
			active = false;
		} catch (RuntimeException exception) {
			warn("clear", exception);
		}
	}

	/** True while any Titah world quad may draw (the renderer's one boolean). */
	public static boolean active() {
		return active;
	}

	/** Draws the Titah world quads into the Phase 10 frame (called by {@code SigilRenderer}, never in the shadow pass). */
	public static void render(SigilRenderer.Frame frame) {
		if (!active || frame == null) {
			return;
		}
		try {
			LiftCarry.render(frame);
			CleaveLine.render(frame);
			StarFall.render(frame);
			RelicFall.render(frame);
			SkyMarks.render(frame);
			FarRelic.render(frame);
		} catch (RuntimeException exception) {
			warn("render", exception);
		}
	}

	// ================================================================== Tingkat Titah (29.16.1)

	/** The tier's world scale {@code s(t) = 1.6^(t − 1)} (t clamped to 1-9): 1, 1.6, 2.56 … 42.95. The one shared helper. */
	public static float tierScale(int tier) {
		return (float) Math.pow(1.6D, Math.max(1, Math.min(9, tier)) - 1);
	}

	/** The tier carried in bits 14-17 of a Titah event's {@code aux} (1-9; 1 for an older server). */
	public static int tierOf(int aux) {
		return Math.max(1, Math.min(9, 1 + (aux >> 14 & 0xF)));
	}

	/** Far relics: {@link FarRelic#describe} for {@code /sinematik pusaka jauh}. */
	public static List<String> farRelics() {
		try {
			return FarRelic.describe();
		} catch (RuntimeException exception) {
			return List.of();
		}
	}

	/**
	 * The impostor projection (29.16.10): {@code (x, y, z)} as a camera-relative point, pulled in along its view ray to
	 * {@code Dm} = render distance − 24 b when farther; returns the factor {@code k ≤ 1} that sizes scale with.
	 */
	static float project(SigilRenderer.Frame frame, double x, double y, double z, float[] out) {
		double rx = frame.rx(x);
		double ry = frame.ry(y);
		double rz = frame.rz(z);
		double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
		double dm = Math.max(32.0D, viewRange() - 24.0D);
		double k = d > dm ? dm / d : 1.0D;
		out[0] = (float) (rx * k);
		out[1] = (float) (ry * k);
		out[2] = (float) (rz * k);
		return (float) k;
	}

	// ================================================================== package helpers

	static double clock() {
		return DampakWorld.titahClock(0.0F);
	}

	static void touch() {
		active = true;
	}

	static void schedule(double delay, long cast, Runnable action) {
		if (SCHEDULED.size() < MAX_SCHEDULED) {
			SCHEDULED.add(new Scheduled(clock() + Math.max(0.0D, delay), cast, action));
		}
	}

	static void warn(String where, Throwable exception) {
		int count = WARNINGS.merge(where, 1, Integer::sum);
		if (count <= 3) {
			Kultivator.LOGGER.warn("Kultivator: Titah world effect error in {}", where, exception);
		}
	}

	/** Quad budget helper: true when {@code n} quads may still draw this frame. */
	static boolean quads(int n) {
		return EpicBudget.takeQuads(n);
	}

	static boolean finite(Vec3 v) {
		return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
	}

	static int argb(float alpha, int rgb) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
		return a << 24 | rgb & 0xFFFFFF;
	}

	static int mix(int a, int b, float t) {
		float k = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * k);
		int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * k);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
		return r << 16 | g << 8 | bl;
	}

	/**
	 * A ring's radius on this client: the tier's cap at Normal and up; at Minimal the true radius (within the render
	 * distance), which ShockRing draws as a dotted particle ring (29.8).
	 */
	static float ringRadius(float raw) {
		if (DampakWorldBudget.tier() <= 0) {
			return (float) Math.max(4.0D, Math.min(raw, viewRange() - 16.0D));
		}
		return DampakWorldBudget.titahRadius(raw);
	}

	/** The far-view distance of world marks (render distance in blocks). */
	static double viewRange() {
		try {
			return Math.max(64.0D, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0D);
		} catch (RuntimeException exception) {
			return 128.0D;
		}
	}

	// ================================================================== tick

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			clear();
			return;
		}
		if (level == null || minecraft.isPaused()) {
			return;
		}
		double now = clock();
		if (!SCHEDULED.isEmpty()) {
			List<Scheduled> due = new ArrayList<>();
			for (Iterator<Scheduled> it = SCHEDULED.iterator(); it.hasNext(); ) {
				Scheduled scheduled = it.next();
				if (now >= scheduled.at()) {
					due.add(scheduled);
					it.remove();
				}
			}
			for (Scheduled scheduled : due) {
				try {
					scheduled.action().run();
				} catch (RuntimeException exception) {
					warn("scheduled", exception);
				}
			}
		}
		RelicFall.tick(level, now);
		SkyMarks.tick(level, now);
		CleaveLine.tick(level, now);
		StarFall.tick(level, now);
		LiftCarry.tick(level, now);
		MonumentNotes.tick(level, now);
		FarRelic.tick(level, now);
		if (level.getGameTime() % 40L == 0L) {
			CASTS.values().removeIf(cast -> now - cast.lastSeen > CAST_MEMORY);
		}
		active = RelicFall.count() > 0 || SkyMarks.count() > 0 || CleaveLine.count() > 0 || StarFall.count() > 0 || LiftCarry.count() > 0
				|| FarRelic.count() > 0;
	}

	// ================================================================== events

	private static int skillOf(TechniqueSpec spec, int aux) {
		String id = spec.id();
		if (id != null) {
			switch (id) {
				case "titah_pedang" -> {
					return PEDANG;
				}
				case "titah_tiang" -> {
					return TIANG;
				}
				case "titah_bintang" -> {
					return BINTANG;
				}
				case "titah_gunung" -> {
					return GUNUNG;
				}
				default -> {
				}
			}
		}
		return aux >> 12 & 3;
	}

	private static void handle(Payloads.TechFx p, TechniqueSpec spec, Phase phase, boolean own, float othersScale) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || spec == null || !isTitah(spec)) {
			return;
		}
		long key = DampakWorld.titahCastKey(p.caster(), spec.netId());
		if (phase == Phase.ABORT) {
			CASTS.remove(key);
			SCHEDULED.removeIf(scheduled -> scheduled.cast() == key);
			RelicFall.abort(key);
			SkyMarks.abort(key);
			CleaveLine.abort(key);
			StarFall.abort(key);
			LiftCarry.abort(key);
			DampakWorld.titahAbort(key);
			return;
		}
		if (!own && othersScale <= 0.0F || phase == Phase.HIT || phase == Phase.MASS_HIT || phase == Phase.PULSE) {
			// hits are the 6c layers' business; nothing of the land answers them
			return;
		}
		Vec3 pos = new Vec3(p.x(), p.y(), p.z());
		Vec3 to = new Vec3(p.tx(), p.ty(), p.tz());
		if (!finite(pos) || !finite(to)) {
			return;
		}
		boolean hasTo = p.tx() != 0.0D || p.ty() != 0.0D || p.tz() != 0.0D;
		Cast cast = CASTS.get(key);
		if (phase == Phase.CAST || phase == Phase.CHARGE || cast == null) {
			if (cast == null || phase == Phase.CAST && cast.climaxed) {
				cast = new Cast(key, skillOf(spec, p.aux()), spec, own, p.caster());
				cast.born = clock();
				CASTS.put(key, cast);
			}
			if (phase == Phase.CAST || phase == Phase.CHARGE || cast.castPos == null) {
				read(cast, p, pos, hasTo ? to : pos, phase);
			}
		}
		cast.lastSeen = clock();
		cast.lowLoad = p.has(Payloads.TechFx.FLAG_LOW_LOAD);
		cast.others = own ? 1.0F : othersScale;
		if (p.has(Payloads.TechFx.FLAG_KEHANCURAN)) {
			cast.kehancuran = true;
		}
		// each decree has exactly one IMPACT, its climax (TitahSkill), whether or not the event carries FLAG_CLIMAX
		boolean climax = p.has(Payloads.TechFx.FLAG_CLIMAX) || phase == Phase.IMPACT;
		boolean preImpact = phase == Phase.TRAVEL && p.has(Payloads.TechFx.FLAG_PRE_IMPACT);
		switch (cast.skill) {
			case PEDANG -> pedang(level, cast, p, phase, climax, preImpact, pos, to, hasTo);
			case TIANG -> tiang(level, cast, p, phase, climax, preImpact, pos, to, hasTo);
			case BINTANG -> bintang(level, cast, p, phase, climax, preImpact, pos, to, hasTo);
			default -> gunung(level, cast, p, phase, climax, preImpact, pos, to, hasTo);
		}
		if (phase == Phase.END) {
			if (!cast.climaxed) {
				DampakWorld.titahSigilEnd(key);
				SkyMarks.end(key);
			}
			CASTS.remove(key);
		}
	}

	/** Reads the cast-wide fields of a CAST (or of the first event seen of a cast whose start was missed). */
	private static void read(Cast cast, Payloads.TechFx p, Vec3 pos, Vec3 aim, Phase phase) {
		cast.castPos = pos;
		cast.aim = aim;
		cast.seed = p.seed();
		if (p.radius() > 0.5F) {
			cast.rawRadius = p.radius();
		} else if (cast.rawRadius <= 0.0F) {
			cast.rawRadius = 48.0F;
		}
		cast.radius = DampakWorldBudget.titahRadius(cast.rawRadius);
		if (p.length() > 0.5F) {
			cast.length = p.length();
		}
		if (p.count() > 0) {
			cast.count = p.count();
		}
		int aux = p.aux();
		cast.tier = tierOf(aux);
		cast.s = tierScale(cast.tier);
		cast.q = (float) Math.sqrt(cast.s);
		lastTier = cast.tier;
		int speed = aux & 0x3FF;
		cast.speed = speed <= 0 ? 1.0F : Math.max(0.3F, Math.min(3.0F, speed / 100.0F));
		cast.monument = (aux & 1 << 10) != 0;
		cast.kehancuran = (aux & 1 << 11) != 0 || p.has(Payloads.TechFx.FLAG_KEHANCURAN);
		if (phase == Phase.CAST && p.duration() > 0) {
			cast.climaxEta = clock() + p.duration();
		} else if (cast.climaxEta <= 0.0D) {
			cast.climaxEta = clock() + 100.0D;
		}
	}

	/** The drop of a falling event: the height between its two points, or {@code fallback}. */
	private static float drop(Vec3 pos, Vec3 to, boolean hasTo, float fallback) {
		double h = hasTo ? Math.abs(pos.y - to.y) : 0.0D;
		return h > 4.0D ? (float) h : fallback;
	}

	/** The palette name of the spec's Dampak row (NONE without one). */
	private static String palette(Cast cast, String fallback) {
		return cast.dampak == null || cast.dampak.palette() == null ? fallback : cast.dampak.palette().name();
	}

	private static String ringName(Cast cast, String fallback) {
		return cast.dampak == null || cast.dampak.ring() == null ? fallback : cast.dampak.ring().name();
	}

	private static @Nullable SigilPattern sigil(Cast cast) {
		return cast.dampak == null ? null : cast.dampak.sigil();
	}

	/** The climax answer shared by the four decrees: ring, flash pillar, shake, the visual ground (Kehancuran off). */
	private static void strike(ClientLevel level, Cast cast, Vec3 impact, String ring, int ringRgb, int flashRgb, String palette, int decalRgb, float decal,
							   int debris, float shake) {
		DampakWorld.titahRing(impact, ringRadius(cast.rawRadius), ring, ringRgb, cast.own, 0, true);
		farRing(cast, impact, cast.rawRadius, ring, ringRgb, 0);
		SkyMarks.flash(cast.key, impact, flashRgb, 16, 180, 1.0F);
		if (!cast.kehancuran) {
			int index = DampakWorldBudget.index();
			int n = DampakWorldBudget.scaled(DampakWorldBudget.FAKE_DEBRIS[index] * debris, cast.own, DampakWorldBudget.FAKE_DEBRIS[index] * debris);
			DampakWorld.titahDebris(impact, Math.max(4.0F, Math.min(14.0F, cast.radius * 0.2F)), n, false, null, cast.key, cast.own, cast.lowLoad);
		}
		DampakWorld.titahDecal(impact, decal, palette, decalRgb, true);
		if (cast.own) {
			CameraShake.add(Math.min(0.8F, shake * DampakWorldBudget.power()), 16, impact, cast.radius * 2.0F, true);
		}
	}

	/**
	 * Tingkat Titah: a ring of true radius {@code raw} wider than this client's near ring also sweeps on as a far horizon
	 * arc (tier 2-9 only; tier 1 draws as before).
	 */
	private static void farRing(Cast cast, Vec3 at, float raw, String variant, int rgb, int delay) {
		if (cast.tier < 2) {
			return;
		}
		float near = ringRadius(raw);
		if (raw > near + 8.0F) {
			DampakWorld.titahFarRing(at, raw, near, variant, rgb, cast.own, delay);
		}
	}

	// ================================================================== Pedang Pemisah Langit (29.5.1)

	private static void pedang(ClientLevel level, Cast cast, Payloads.TechFx p, Phase phase, boolean climax, boolean preImpact, Vec3 pos, Vec3 to,
							   boolean hasTo) {
		float half = DampakWorldBudget.titahRadius(cast.length > 0.5F ? cast.length * 0.5F : cast.rawRadius);
		if (phase == Phase.CAST) {
			// the CLEAVE_LINE sigil along the aim, faint until the land splits
			DampakWorld.titahSigil(level, cast.key, sigil(cast), cast.castPos, cast.aim, half, cast.seed, GOLD, cast.own, palette(cast, "SWORD"));
			return;
		}
		if (phase == Phase.EXTRA1 && !cast.lowLoad) {
			// the gate opens above the impact: a gold ring tears the clouds (what bystanders see of the Celestial Court)
			float r = Math.max(12.0F, Math.min(48.0F * cast.q, cast.radius * 0.35F * cast.q));
			SkyMarks.gate(cast.key, cast.ground(level).add(0.0D, 96.0D * cast.q, 0.0D), r, GOLD, WHITE_GOLD, cast.climaxEta + 30.0D);
			return;
		}
		if ((phase == Phase.EXTRA2 || preImpact) && !cast.fell) {
			// EXTRA2 (sky → impact) and the pre-impact TRAVEL arrive together: the first one drops the sword
			cast.fell = true;
			int duration = p.duration() > 0 ? p.duration() : cast.t(40);
			float height = drop(pos, to, hasTo, 120.0F * cast.q);
			float scale = cast.giant(Math.max(6.0F, Math.min(24.0F, 12.0F * (float) Math.sqrt(cast.kekuatan()))));
			Vec3 ground = cast.ground(level);
			float yaw = (float) Math.toDegrees(Math.atan2(-(cast.castPos.x - ground.x), cast.castPos.z - ground.z));
			if (!cast.lowLoad) {
				RelicFall.fall(cast.key, "heaven_splitting_sword", RelicFall.SWORD, ground, height, duration, scale, yaw, WHITE_GOLD, GOLD, cast.own, false, null,
						cast.tier);
			}
			return;
		}
		if (climax) {
			cast.climaxed = true;
			Vec3 impact = cast.ground(level);
			RelicFall.land(cast.key);
			SkyMarks.end(cast.key);
			strike(level, cast, impact, ringName(cast, "GOLD"), GOLD, WHITE_GOLD, palette(cast, "SWORD"), WHITE_GOLD, Math.min(14.0F, cast.radius * 0.2F), 2, 0.6F);
			return;
		}
		if (phase == Phase.EXTRA3) {
			// Bumi terbelah: the cleaving front runs both ways along the aim (pos → to names it); the sigil lights
			Vec3 impact = cast.ground(level);
			Vec3 along = hasTo ? new Vec3(to.x - pos.x, 0.0D, to.z - pos.z) : Vec3.ZERO;
			Vec3 dir = along.lengthSqr() > 1.0E-4D ? along.normalize() : cast.dir();
			if (!cast.lowLoad) {
				if (cast.tier >= 2) {
					// the front runs at the server speed to the true half-length; beyond the drawn fissure, a far streak
					float farHalf = cast.length > 0.5F ? cast.length * 0.5F : cast.rawRadius;
					float speed = Math.max(8.0F, farHalf / 40.0F) * cast.speed;
					CleaveLine.start(level, cast.key, impact, dir, half, farHalf, speed, WHITE_GOLD, 0xFF2A1A, cast.own, cast.kehancuran, cast.lowLoad);
				} else {
					CleaveLine.start(level, cast.key, impact, dir, half, 8.0F * cast.speed, WHITE_GOLD, 0xFF2A1A, cast.own, cast.kehancuran, cast.lowLoad);
				}
			}
			DampakWorld.titahSigilClimax(cast.key, 0, 0);
			return;
		}
		if (phase == Phase.EXTRA4) {
			Vec3 impact = cast.ground(level);
			float rawSecond = p.radius() > 0.5F ? p.radius() : cast.rawRadius * 1.4F;
			float second = ringRadius(rawSecond);
			DampakWorld.titahRing(impact, second, "DUST", mix(GOLD, DUST, 0.5F), cast.own, 0, false);
			farRing(cast, impact, rawSecond, "DUST", mix(GOLD, DUST, 0.5F), 0);
			SkyMarks.wave(cast.key, impact, cast.tier >= 2 ? Math.min(rawSecond, 40.0F * cast.s) : Math.min(cast.radius, 40.0F), DUST, false);
		}
	}

	// ================================================================== Tiang Penenang Samudra (29.5.2)

	private static void tiang(ClientLevel level, Cast cast, Payloads.TechFx p, Phase phase, boolean climax, boolean preImpact, Vec3 pos, Vec3 to,
							  boolean hasTo) {
		if (phase == Phase.EXTRA0 && !cast.ascended && !cast.lowLoad) {
			// JAVELIN_THROW releases (EXTRA0, hand → sky): the pillar streaks straight up into the sky with an ember trail
			cast.ascended = true;
			float up = Math.min(120.0F * cast.q, drop(pos, to, hasTo, 90.0F * cast.q));
			int ticks = p.duration() > 0 ? Math.min(40, p.duration()) : cast.t(20);
			RelicFall.ascend(cast.key, "dinghai_pillar", pos.subtract(0.0D, 0.3D, 0.0D), up, ticks, 1.6F * cast.q, EMBER, cast.own);
			return;
		}
		if (phase == Phase.EXTRA1 && !cast.lowLoad) {
			// bystanders see a red star overhead (the event's position is the sky above the aim)
			Vec3 star = pos.y > cast.aim.y + 40.0D ? pos.subtract(0.0D, 10.0D, 0.0D) : cast.ground(level).add(0.0D, 150.0D * cast.q, 0.0D);
			SkyMarks.star(cast.key, star, 0xFF3A1A, 7.0F * cast.q, cast.climaxEta + 4.0D, cast.tier >= 2);
			return;
		}
		if ((phase == Phase.EXTRA2 || preImpact) && !cast.fell) {
			cast.fell = true;
			int duration = p.duration() > 0 ? p.duration() : cast.t(30);
			float height = drop(pos, to, hasTo, 160.0F * cast.q);
			float scale = cast.giant(Math.max(5.0F, Math.min(20.0F, 10.0F * (float) Math.sqrt(cast.kekuatan()))));
			if (!cast.lowLoad) {
				RelicFall.fall(cast.key, "dinghai_pillar", RelicFall.PILLAR, cast.ground(level), height, duration, scale, 0.0F, 0xFFFFFF, EMBER, cast.own, false,
						null, cast.tier);
			}
			if (cast.own || Minecraft.getInstance().player != null && Minecraft.getInstance().player.position().distanceToSqr(cast.aim) < 128.0D * 128.0D) {
				// a sky flash where the streak starts (client presentation, not weather)
				level.setSkyFlashTime(2);
			}
			return;
		}
		if (climax) {
			cast.climaxed = true;
			Vec3 impact = cast.ground(level);
			RelicFall.land(cast.key);
			SkyMarks.end(cast.key);
			strike(level, cast, impact, ringName(cast, "EMBER"), EMBER, 0xFFFFFF, palette(cast, "FIRE"), EMBER, Math.max(6.0F, Math.min(24.0F, cast.radius * 0.3F)), 3,
					0.7F);
			return;
		}
		if (phase == Phase.EXTRA3) {
			// the flattening wave out to the damage radius (the event's radius): grass and leaves blown outward
			Vec3 impact = cast.ground(level);
			float rawWave = p.radius() > 0.5F ? p.radius() : 40.0F * cast.s;
			float r = Math.min(cast.radius, rawWave);
			DampakWorld.titahRing(impact, r, "DUST", mix(EMBER, DUST, 0.6F), cast.own, 0, false);
			farRing(cast, impact, rawWave, "DUST", mix(EMBER, DUST, 0.6F), 0);
			SkyMarks.wave(cast.key, impact, cast.tier >= 2 ? rawWave : r, DUST, true);
		}
	}

	// ================================================================== Tujuh Bintang Biduk Utara (29.5.3)

	private static void bintang(ClientLevel level, Cast cast, Payloads.TechFx p, Phase phase, boolean climax, boolean preImpact, Vec3 pos, Vec3 to,
								boolean hasTo) {
		if (phase == Phase.CAST) {
			// at the true radius (≤ 256): its nodes are where the server's stars fall (the lines are sampled by R / 64)
			DampakWorld.titahSigil(level, cast.key, sigil(cast), cast.castPos, cast.aim, cast.rawRadius, cast.seed, VIOLET, cast.own, palette(cast, "LIGHTNING"));
			if (!cast.lowLoad) {
				SkyMarks.dipper(cast.key, cast.ground(level), DampakWorld.titahSigilNodes(cast.key), cast.dir(), STAR_WHITE, cast.climaxEta + 60.0D, cast.q);
			}
			return;
		}
		if (phase == Phase.EXTRA1) {
			// node i (the event's position) lights with its line from star i − 1, and its star flares in the sky
			DampakWorld.titahSigilIgnite(level, cast.key, pos);
			SkyMarks.lightStar(cast.key, p.of() > 1 ? p.index() : cast.ignited);
			cast.ignited++;
			return;
		}
		if (preImpact || phase == Phase.EXTRA0) {
			// a star of the Dipper falls (the pre-impact TRAVEL, sky → star), then lands (EXTRA0 at the star, 6 ticks apart)
			Vec3 at = preImpact ? to : pos;
			Vec3 ground = new Vec3(at.x, DampakWorld.titahGround(level, at.x, at.z, at.y), at.z);
			int index = Math.max(0, p.index());
			if (preImpact) {
				StarFall.fall(level, cast, ground, Math.max(4, p.duration()), drop(pos, to, hasTo, 80.0F * cast.q), index);
			} else {
				StarFall.land(level, cast, ground, index, 0);
			}
			return;
		}
		if (climax) {
			cast.climaxed = true;
			// the lines ignite as burning trenches (the DOT window, the event's duration), Yaoguang (its target) shoots a last
			// sky pillar
			int burn = p.duration() > 0 ? Math.min(400, p.duration()) : 100;
			DampakWorld.titahSigilClimax(cast.key, burn, EMBER_VIOLET);
			List<Vec3> nodes = DampakWorld.titahSigilNodes(cast.key);
			Vec3 last = hasTo ? new Vec3(to.x, DampakWorld.titahGround(level, to.x, to.z, to.y), to.z)
					: nodes.isEmpty() ? cast.ground(level) : nodes.get(nodes.size() - 1);
			SkyMarks.flash(cast.key, last, 0xD8B8FF, 30, 220, 1.4F);
			SkyMarks.end(cast.key);
			if (cast.own) {
				CameraShake.add(Math.min(0.5F, 0.35F * DampakWorldBudget.power()), 12, last, cast.radius * 2.0F, true);
			}
		}
	}

	// ================================================================== Titah Pemindah Gunung (29.5.4)

	private static void gunung(ClientLevel level, Cast cast, Payloads.TechFx p, Phase phase, boolean climax, boolean preImpact, Vec3 pos, Vec3 to,
							   boolean hasTo) {
		float rawHalf = cast.length > 0.5F ? cast.length : cast.rawRadius;
		float half = DampakWorldBudget.titahRadius(rawHalf);
		// the cell size from the server (about 8 per side at every tier; at most 16 × s)
		float cellCap = 16.0F * Math.max(1.0F, cast.s);
		float cell = cast.count > 0 ? Math.max(2.0F, Math.min(cellCap, 2.0F * rawHalf / cast.count)) : Math.max(2.0F, Math.min(cellCap, 2.0F * rawHalf / 8.0F));
		// from tier 2 the grid and the torn cells keep the true size of the decree (only what is in view draws)
		float landHalf = cast.tier >= 2 ? rawHalf : half;
		if (phase == Phase.CAST) {
			if (!cast.ascended && !cast.lowLoad) {
				cast.ascended = true;
				Cast c = cast;
				// the seal leaves the hand and rises
				schedule(cast.t(6), cast.key, () -> RelicFall.ascend(c.key, "yishan_seal", c.castPos.add(0.0D, 1.8D, 0.0D), 40.0F, 24, 1.2F, JADE_GOLD, c.own));
			}
			return;
		}
		if (phase == Phase.EXTRA1) {
			// the light grid cuts the square into cells
			LiftCarry.grid(cast.key, cast.ground(level), landHalf, cell, JADE_GOLD, cast.climaxEta + cast.t(70), cast.own);
			return;
		}
		if ((phase == Phase.EXTRA2 || preImpact) && !cast.fell) {
			cast.fell = true;
			int duration = p.duration() > 0 ? p.duration() : cast.t(30);
			float height = drop(pos, to, hasTo, 96.0F * cast.q);
			// the seal covers most of the square (its jade face is 0.875 b wide at scale 1)
			float scale = cast.tier >= 2 ? Math.max(4.0F, Math.min(TitahMonumentRenderer.MAX_SCALE, 2.0F * rawHalf * 0.8F / 0.875F))
					: Math.max(4.0F, Math.min(48.0F, 2.0F * half * 0.8F / 0.875F));
			Vec3 ground = cast.ground(level);
			float yaw = (float) Math.toDegrees(Math.atan2(-(cast.castPos.x - ground.x), cast.castPos.z - ground.z));
			if (!cast.lowLoad) {
				RelicFall.fall(cast.key, "yishan_seal", RelicFall.SEAL, ground, height, duration, scale, yaw, 0xFFE8A0, SEAL_VIOLET, cast.own, true, null, cast.tier);
			}
			return;
		}
		if (climax) {
			cast.climaxed = true;
			Vec3 impact = cast.ground(level);
			RelicFall.land(cast.key);
			strike(level, cast, impact, ringName(cast, "DUST"), mix(0xFFE8A0, DUST, 0.5F), 0xFFE8A0, palette(cast, "EARTH"), 0xFFE8A0,
					Math.max(4.0F, Math.min(16.0F, half * 0.5F)), 2, 0.6F);
			// the stepped glowing pit: the real one's size under Mode Kehancuran, the whole square otherwise (visual only)
			float pit = cast.kehancuran ? Math.max(4.0F, Math.min(half, Math.min(24.0F * cast.s, cast.rawRadius * 0.25F))) : half;
			int steps = pit >= 24.0F ? 5 : pit >= 12.0F ? 4 : 3;
			// Tingkat Titah: a deeper pit has more terraces (+2 from tier 4, +2 more from tier 7; at most 9)
			steps = Math.min(9, steps + (cast.tier >= 4 ? 2 : 0) + (cast.tier >= 7 ? 2 : 0));
			LiftCarry.pit(cast.key, impact, pit, steps, 0xC8A0FF, 0xFFD878, cast.own);
			return;
		}
		if (phase == Phase.EXTRA3) {
			// cells torn out: three-layer slabs rise turning and are carried off into the sky
			int spread = p.duration() > 0 ? p.duration() : cast.t(40);
			LiftCarry.tear(level, cast.key, cast.ground(level), landHalf, cell, cast.s, cast.dir(), spread, JADE_GOLD, cast.own, cast.lowLoad, cast.kehancuran,
					cast.others);
			return;
		}
		if (phase == Phase.EXTRA4) {
			// the levitated foes slam down; the seal lifts away and fades
			Vec3 impact = cast.ground(level);
			DampakWorld.titahRing(impact, Math.min(24.0F * cast.s, cast.radius), "DUST", DUST, cast.own, 0, false);
			RelicFall.liftAway(cast.key, 60.0F * cast.q, cast.t(30));
		}
	}

	private TitahWorld() {
	}
}
