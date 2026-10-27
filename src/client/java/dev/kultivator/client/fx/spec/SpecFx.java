package dev.kultivator.client.fx.spec;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.quad.QuadFx;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.FxLayer;
import dev.kultivator.technique.spec.dsl.Phase;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client side of {@code Payloads.TechFx} (DESIGN 22.4, polish pass 27): looks the spec up in the registry by net id and
 * plays the FX layers of the event's phase with the viewer's Effect Quality and the {@link FxBudget}: particle templates
 * ({@link ParticleRun}), client-only phantom displays and 3D props ({@link DisplayRun}), the custom renderers
 * ({@link RendererRun}, {@link AvatarLayer}), the quad geometry ({@link QuadFx}) and the post effects
 * ({@link CameraShake}, {@link ScreenFlash}). Sounds are played by the server.
 * <p>
 * Every event first feeds the cast-pose library (the body moves before anything else) and the 7b director. Polish rules:
 * an event with non-finite points is skipped (S7); ABORT ends the caster's runs of the spec, its props, copies and
 * geometry within 6 ticks (S8); a layer above the caster's visual tier draws smaller and thinner instead of not at all,
 * and three tiers short it draws its particle stand-in (S6); lasting and travelling layers live the event's duration
 * (S10, S13); disciples' and others' casts never flash, vignette or tint the sky and shake at most 0.3 (S19); one shake
 * per cast per 10 ticks unless it is the climax (S13). IMPACT, HIT and PULSE events of a running cast accent its props
 * (a swinging sword swings again, a giant avatar strikes). PHANTOM events and FLAG_BLINK movement have their own
 * drawers ({@link PhantomFx}, {@link MovementFx}), and some arts add signature extras ({@link Signatures}).
 */
public final class SpecFx {

	private static final int GOLD = 0xFFD54A;
	private static final FxLayer RIM = FxLayer.of(FxLayer.Template.SHOCK_RING, GOLD).color2(0xFFF1B8).radius(1.6F).count(28).ticks(8).detail();
	private static final FxLayer AUTO_CRACKS = FxLayer.of(FxLayer.Template.CRACKS, 0x3A2A1A).count(60).ticks(8);
	private static final double VIGNETTE_RANGE = 48.0D;
	/** Shake cap of others' and disciples' casts (S19). */
	private static final float FOREIGN_SHAKE = 0.3F;

	private static final List<LayerRun> RUNS = new ArrayList<>();
	/** Layers started while the list is being ticked (SKY), added after the pass. */
	private static final List<LayerRun> PENDING = new ArrayList<>();
	/** Last shake tick per cast (caster × 31 + spec). */
	private static final Map<Long, Long> SHAKES = new HashMap<>();
	private static @Nullable ClientLevel lastLevel;
	private static int pruneTicks;
	private static long ticks;
	private static long displaysVersion = -1L;
	private static boolean displaysCached = true;

	/** Registers the client tick and render hooks (called once from {@code KultivatorClient}). */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SpecFx::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("technique_flash"), new ScreenFlash.Hud());
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new AvatarLayer<>(avatar));
			}
		});
		QuadFx.init();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> clearAll()));
	}

	/** Draws one technique event (called on the client thread by the payload receiver). */
	public static void play(Payloads.TechFx payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || !FxEvent.valid(payload)) {
			return;
		}
		TechniqueSpec spec = TechniqueRegistry.byNet(payload.spec());
		if (spec == null) {
			return;
		}
		Entity caster = minecraft.level.getEntity(payload.caster());
		// the body moves first (polish 2): the pose library sees every event, END and ABORT included
		AnimBridge.onTechFx(payload, spec, caster);
		// Phase 7b (DESIGN 23.6): ultimates and one's own Kehancuran casts start their epic layers (never throws).
		dev.kultivator.client.epic.EpicDirector.onTechFx(payload);
		// Phase 10 (DESIGN 28.14): the silent boom, marks, rides, impact frames and world FX of Dampak rows (never throws),
		// before the others gate so "Efek pemain lain: Mati" still hears the boom.
		dev.kultivator.client.dampak.DampakClient.onTechFx(payload);
		Phase phase = Phase.byOrdinal(payload.phase());
		if (phase == Phase.ABORT) {
			abort(payload.caster(), spec.netId());
			return;
		}
		boolean own = caster == minecraft.player;
		boolean others = !own && (caster instanceof Player || payload.has(Payloads.TechFx.FLAG_DISCIPLE));
		float othersFactor = others ? EffectQuality.othersScale() : 1.0F;
		if (othersFactor <= 0.0F) {
			return;
		}
		boolean displays = PhantomDisplays.enabled() && !(others && EffectQuality.others() != EffectQuality.Others.FULL);
		FxEvent ev = new FxEvent(spec, payload, EffectQuality.level().factor(), othersFactor, displays,
				EffectQuality.displayAllowance(spec.grade().tier()), FxBudget.perEvent());
		try {
			followUntil(ev);
			accents(ev);
			if (phase == Phase.PHANTOM) {
				PhantomFx.onPhantom(ev);
			}
			boolean legacyBlink = spec.legacy() != null && phase == Phase.CAST && ev.hasTarget && LegacyLayers.blink(spec.legacy());
			if (ev.has(Payloads.TechFx.FLAG_BLINK) || legacyBlink) {
				MovementFx.onBlink(ev);
			}
			Signatures.play(ev);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: technique extra effect of {} failed", spec.id(), exception);
		}
		// a throw here would reach the packet handler and disconnect the player
		try {
			List<FxLayer> layers = layersOf(spec, ev);
			for (int i = 0; i < layers.size(); i++) {
				start(ev, layers.get(i), i);
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: technique layers of {} failed", spec.id(), exception);
		}
	}

	/** ABORT (S8): every run, prop, copy and quad of this caster's cast fades out at once. */
	private static void abort(int casterId, int specNet) {
		for (LayerRun run : RUNS) {
			if (run.casterId == casterId && run.specNet == specNet) {
				run.finish(6);
			}
		}
		for (LayerRun run : PENDING) {
			if (run.casterId == casterId && run.specNet == specNet) {
				run.finish(6);
			}
		}
		AvatarFx.end(casterId);
		QuadFx.endCaster(casterId);
		AnimBridge.endModels(casterId);
		PhantomFx.endCaster(casterId);
	}

	/** Runs waiting for this phase of the same cast (followUntil), or for its END, fade out. */
	private static void followUntil(FxEvent ev) {
		for (LayerRun run : RUNS) {
			if (run.until == null || run.casterId != ev.casterId || run.specNet != ev.spec.netId()) {
				continue;
			}
			if (run.until == ev.phase || ev.phase == Phase.END) {
				run.finish(4);
			}
		}
	}

	/** IMPACT, HIT, MASS_HIT and PULSE accent the running props of the same cast (S15 re-swing, S21 giant strikes). */
	private static void accents(FxEvent ev) {
		boolean hit = ev.phase == Phase.HIT || ev.phase == Phase.MASS_HIT;
		if (!(hit || ev.phase == Phase.IMPACT || ev.phase == Phase.PULSE || ev.phase.extraIndex() >= 0)) {
			return;
		}
		for (LayerRun run : RUNS) {
			if (run.casterId != ev.casterId || run.specNet != ev.spec.netId()) {
				continue;
			}
			if (run instanceof DisplayRun display && !hit) {
				display.accent();
			} else if (run instanceof RendererRun renderer) {
				renderer.accent(ev);
			}
		}
	}

	/** The layers of this event: the spec's phase, legacy tiers, mastery marks, Kehancuran cracks, default hits. */
	private static List<FxLayer> layersOf(TechniqueSpec spec, FxEvent ev) {
		List<FxLayer> list = new ArrayList<>(spec.fx(ev.phase));
		if (ev.has(Payloads.TechFx.FLAG_BLINK)) {
			// a movement event draws the movement (MovementFx), not the spec's takeoff layers; path-shaped looks stay
			list.removeIf(layer -> switch (layer.template()) {
				case CLONE, AFTERIMAGE, TRAIL -> false;
				case BOLT, CHAINS -> layer.attach() != FxLayer.Attach.LINE;
				default -> true;
			});
		}
		if (ev.phase == Phase.PHANTOM && ev.has(Payloads.TechFx.FLAG_PRE_IMPACT)) {
			// a phantom's warning (PhantomFx.warn) is not a strike: the spec's PHANTOM hit layers wait for the real one
			list.clear();
		}
		if (ev.phase == Phase.MASS_HIT && list.isEmpty()) {
			list.addAll(spec.fx(Phase.HIT));
		}
		if (ev.phase == Phase.LAND && list.isEmpty()) {
			list.addAll(spec.fx(Phase.IMPACT));
		}
		if (spec.legacy() != null) {
			list.addAll(LegacyLayers.layers(spec.legacy(), ev));
		}
		int ink = spec.dao() == null ? 0xE8DDB5 : spec.dao().color();
		boolean hit = ev.phase == Phase.HIT || ev.phase == Phase.MASS_HIT;
		if (hit && list.isEmpty()) {
			// the default hit spark grows with the spec's grade (S13)
			float grade = 1.0F + 0.25F * spec.grade().tier();
			float radius = Math.max(ev.radius, 0.6F) * grade;
			int count = Math.round((ev.phase == Phase.MASS_HIT ? 10 + 3 * Math.min(8, ev.count) : 10) * grade);
			list.add(FxLayer.of(FxLayer.Template.IMPACT, ink).color2(0xFFFFFF).radius(ev.phase == Phase.MASS_HIT ? Math.max(1.0F, radius) : radius)
					.count(count).ticks(3));
		}
		if (ev.has(Payloads.TechFx.FLAG_DOT) && hit) {
			// a damage-over-time tick: only the light layers (no props, no post effects)
			list.removeIf(layer -> layer.template().group() == FxLayer.Group.DISPLAY || layer.template().group() == FxLayer.Group.POST);
		}
		if (ev.phase == Phase.CAST) {
			if (ev.mastery >= 3) {
				list.add(RIM);
			}
			if (ev.mastery >= 4) {
				list.add(FxLayer.of(FxLayer.Template.AFTERIMAGE, ink).color2(GOLD).count(50).ticks(16).extra());
			}
		}
		boolean impact = ev.phase == Phase.IMPACT || ev.phase == Phase.CAST && spec.fx(Phase.IMPACT).isEmpty() && spec.hasDestruction();
		if (ev.has(Payloads.TechFx.FLAG_KEHANCURAN) && impact) {
			list.add(ev.phase == Phase.CAST && ev.hasTarget ? AUTO_CRACKS.onTarget() : AUTO_CRACKS);
		}
		return list;
	}

	private static void start(FxEvent ev, FxLayer layer, int index) {
		if (layer.onlyKehancuran() && !ev.has(Payloads.TechFx.FLAG_KEHANCURAN)) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (layer.variant() == FxLayer.Variant.NIGHT && (level == null || !level.isDarkOutside())) {
			// night-only looks (fireflies) are skipped by day
			return;
		}
		// S6: tier gating became scaling; identity layers never disappear
		int gap = ev.tier - layer.minTier();
		float tierScale = gap >= 0 ? 1.0F : gap == -1 ? 0.6F : 0.45F;
		if (gap < 0 && layer.priority() == FxLayer.Priority.EXTRA) {
			return;
		}
		layer = promote(layer, ev);
		FxLayer.Template template = layer.template();
		if (gap <= -3 && template.group() == FxLayer.Group.DISPLAY) {
			// three tiers short: the template's particle stand-in at the reduced scale
			layer = standIn(layer);
			template = layer.template();
		}
		int life = lifetime(layer, ev);
		LayerRun run = switch (template.group()) {
			case PARTICLE -> {
				if (template == FxLayer.Template.SKY_TEAR) {
					boolean taken = PhantomDisplays.takeLarge();
					if (!taken && !ev.own) {
						yield null;
					}
					yield new LargeRun(ev, layer, life, index, taken, tierScale);
				}
				yield new ParticleRun(ev, layer, life, index, tierScale);
			}
			case DISPLAY -> new DisplayRun(ev, layer, life, index, tierScale);
			case RENDERER -> new RendererRun(ev, layer, life, index, tierScale);
			case POST -> new PostRun(ev, layer, life, index);
		};
		if (run != null) {
			if (RUNS.size() >= MAX_RUNS && !makeRoom(run.priority)) {
				// full: a DETAIL or EXTRA layer is refused; a CORE layer pushed out the oldest non-CORE one
				run.end();
				return;
			}
			RUNS.add(run);
		}
	}

	/**
	 * Looks the data asks for that geometry now draws better: a lasting dragon BEAST (dragon_soul_domain's three dragons,
	 * no target to charge at) becomes coiling RIBBON dragons circling the area or the caster.
	 */
	private static FxLayer promote(FxLayer layer, FxEvent ev) {
		if (layer.template() == FxLayer.Template.BEAST && layer.beast() == FxLayer.BeastKind.DRAGON && !ev.hasTarget
				&& (layer.ticks() >= 60 || ev.duration >= 60) && !EffectQuality.minimal()) {
			float radius = layer.radius() > 0.0F ? layer.radius() : ev.radius > 0.0F ? ev.radius * 0.7F : 4.0F;
			FxLayer.Attach attach = ev.follow() || layer.attach() == FxLayer.Attach.CASTER ? FxLayer.Attach.CASTER : layer.attach();
			return FxLayer.of(FxLayer.Template.RIBBON, layer.color()).color2(layer.color2()).n(Math.max(1, layer.n())).radius(radius)
					.ticks(layer.ticks()).count(Math.max(10, layer.count() / 3)).beast(FxLayer.BeastKind.DRAGON).attach(attach).priority(layer.priority())
					.delay(layer.delay()).minTier(layer.minTier());
		}
		return layer;
	}

	/** The particle version of a display layer (S6 three tiers short, S20). */
	private static FxLayer standIn(FxLayer layer) {
		FxLayer.Template stand = layer.fallback() != null ? layer.fallback() : switch (layer.template()) {
			case LASER -> FxLayer.Template.BEAM_CORE;
			case PETAL_RING -> FxLayer.Template.PETALS;
			case GLYPH -> FxLayer.Template.RUNE_CIRCLE;
			case GIANT_ITEM, SWORD_RING -> FxLayer.Template.SWORD_SHAPE;
			default -> FxLayer.Template.SHELL;
		};
		FxLayer base = FxLayer.of(stand, layer.color()).color2(layer.color2()).count(Math.max(12, layer.count())).ticks(layer.ticks())
				.attach(layer.attach()).above(layer.offsetY()).priority(layer.priority()).delay(layer.delay());
		if (layer.radius() > 0.0F) {
			base = base.radius(layer.radius());
		}
		return base;
	}

	/** Layers alive at most (twice the per-tick updates; the round-robin still ages every one of them). */
	private static final int MAX_RUNS = FxBudget.MAX_UPDATES * 2;
	/** Where the next tick's round-robin pass starts. */
	private static int cursor;

	/** Ends the oldest non-CORE layer for a CORE one; false when {@code priority} is not CORE or none could go. */
	private static boolean makeRoom(FxBudget.Priority priority) {
		if (priority != FxBudget.Priority.CORE) {
			return false;
		}
		for (Iterator<LayerRun> it = RUNS.iterator(); it.hasNext(); ) {
			LayerRun old = it.next();
			if (old.priority != FxBudget.Priority.CORE) {
				old.end();
				it.remove();
				return true;
			}
		}
		return false;
	}

	/**
	 * Ticks a layer plays: its own, stretched to the event's duration for lasting looks; travelling and falling templates
	 * take the event's duration (S10); displays honour an explicit {@code ticks()} up to 600 / 900 (S13, finding 155).
	 */
	private static int lifetime(FxLayer layer, FxEvent ev) {
		int ticks = Math.max(1, layer.ticks());
		boolean lasting = switch (layer.template()) {
			case DOME, MIST, CLOUD_DISC, RUNE_CIRCLE, STARFIELD, WINGS, SHIELD_SHELL, VORTEX, SWARM, AVATAR, CLONE, SKY_TEAR, SWORD_RING,
				 PETAL_RING, GLYPH, HEAL_RISE, GIANT_BLOCK, RIBBON -> true;
			default -> false;
		};
		boolean travelling = switch (layer.template()) {
			case CRESCENT_FLY, METEOR, SKY_SILHOUETTE -> true;
			case SWORD_SHAPE -> layer.variant() == FxLayer.Variant.FLYING || layer.variant() == FxLayer.Variant.FALLING;
			case PALM, FIST -> layer.attach() == FxLayer.Attach.LINE || layer.variant() == FxLayer.Variant.FALLING;
			case BEAST -> ev.hasTarget;
			case GIANT_ITEM, GIANT_BLOCK, MODEL -> layer.motion() == FxLayer.Motion.SLIDE || layer.motion() == FxLayer.Motion.FALL;
			default -> false;
		};
		if (lasting && ev.duration > ticks) {
			ticks = ev.duration;
		}
		if (travelling && ev.duration > 0) {
			// a falling or travelling look lands with the server (a pre-impact fall plus a short hold)
			ticks = ev.duration + (ev.has(Payloads.TechFx.FLAG_PRE_IMPACT) ? Math.min(12, Math.max(4, layer.ticks() - ev.duration)) : 0);
		}
		if (layer.followUntil() != null) {
			// alive until that phase (or END / ABORT) ends it
			ticks = Math.max(ticks, ev.spec.dewa() ? 900 : 600);
		}
		int cap;
		if (layer.template().group() == FxLayer.Group.DISPLAY) {
			boolean longLived = ev.spec.lasting() || ev.duration > 0 || ev.spec.type() == dev.kultivator.data.TechniqueType.UBAH
					|| layer.ticks() > 100;
			cap = longLived ? ev.spec.dewa() ? 900 : 600 : 100;
			if (!longLived && layer.ticks() > 0) {
				cap = Math.min(900, Math.max(cap, layer.ticks()));
			}
		} else {
			cap = ev.spec.dewa() ? 900 : 600;
		}
		return Math.min(ticks, cap);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		EffectQuality.tickAuto(minecraft);
		PhantomDisplays.tick(level);
		if (level != lastLevel) {
			endAll();
			lastLevel = level;
			AvatarFx.prune(true);
			QuadFx.clear();
			PhantomFx.clear();
			MovementFx.clear();
			Signatures.clear();
		}
		if (level == null || minecraft.isPaused()) {
			return;
		}
		ticks++;
		FxBudget.tick();
		QuadFx.tick();
		PhantomFx.tick();
		BindFx.tick();
		MovementFx.tick();
		Signatures.tick();
		if (RUNS.size() > FxBudget.MAX_UPDATES) {
			// over the update cap: EXTRA layers end first
			for (Iterator<LayerRun> it = RUNS.iterator(); it.hasNext() && RUNS.size() > FxBudget.MAX_UPDATES; ) {
				LayerRun run = it.next();
				if (run.extra()) {
					run.end();
					it.remove();
				}
			}
		}
		// every run ages every tick (S13); past MAX_UPDATES the ones outside this tick's window only age (no emission)
		int total = RUNS.size();
		int start = total <= FxBudget.MAX_UPDATES ? 0 : Math.floorMod(cursor, total);
		List<LayerRun> batch = new ArrayList<>(RUNS);
		for (int i = 0; i < total; i++) {
			int offset = Math.floorMod(i - start, total);
			if (offset >= FxBudget.MAX_UPDATES) {
				batch.get(i).starved = true;
			}
		}
		cursor = total <= FxBudget.MAX_UPDATES ? 0 : start + FxBudget.MAX_UPDATES;
		for (LayerRun run : batch) {
			boolean more;
			try {
				more = run.tick();
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.warn("Kultivator: technique effect layer {} failed", run.layer.template(), exception);
				more = false;
			}
			if (!more) {
				run.end();
				RUNS.remove(run);
			}
		}
		if (!PENDING.isEmpty()) {
			RUNS.addAll(PENDING);
			PENDING.clear();
		}
		if (++pruneTicks >= 20) {
			pruneTicks = 0;
			AvatarFx.prune(false);
			SHAKES.values().removeIf(at -> ticks - at > 200L);
		}
	}

	private static void endAll() {
		for (LayerRun run : RUNS) {
			run.end();
		}
		for (LayerRun run : PENDING) {
			run.end();
		}
		RUNS.clear();
		PENDING.clear();
		PhantomDisplays.clear();
	}

	private static void clearAll() {
		endAll();
		AvatarFx.prune(true);
		QuadFx.clear();
		PhantomFx.clear();
		MovementFx.clear();
		Signatures.clear();
		CameraShake.clear();
		ScreenFlash.clear();
		SHAKES.clear();
		lastLevel = null;
	}

	/** Layers playing right now (debug line). */
	public static int activeLayers() {
		return RUNS.size();
	}

	/** True when this cast may shake now: once per 10 ticks unless it is the climax (S13). */
	static boolean shakeAllowed(FxEvent ev) {
		long key = ev.casterId * 31L + ev.spec.netId();
		Long last = SHAKES.get(key);
		if (!ev.climax() && last != null && ticks - last < 10L) {
			return false;
		}
		SHAKES.put(key, ticks);
		return true;
	}

	/**
	 * The server's {@code techniqueDisplays} switch: {@code sessionExtra.teknik.displays} when the server sent it, the
	 * integrated server's config in singleplayer, otherwise on.
	 */
	static boolean serverDisplays() {
		long version = ClientCultivation.version();
		if (version != displaysVersion) {
			displaysVersion = version;
			CompoundTag teknik = ClientCultivation.data().sessionExtra().getCompoundOrEmpty("teknik");
			if (teknik.get("displays") != null) {
				displaysCached = teknik.getBooleanOr("displays", true);
			} else if (Minecraft.getInstance().hasSingleplayerServer()) {
				displaysCached = KultivatorConfig.get().techniqueDisplays;
			} else {
				displaysCached = true;
			}
		}
		return displaysCached;
	}

	// ================================================================== small run kinds

	/** A sky tear holding one of the two large translucent layer slots while it plays (domes no longer take one, S13). */
	private static final class LargeRun extends LayerRun {
		private final ParticleRun inner;
		private boolean released;

		LargeRun(FxEvent ev, FxLayer layer, int life, int index, boolean taken, float tierScale) {
			super(ev, layer, life, index, tierScale);
			this.inner = new ParticleRun(ev, layer, life, index, tierScale);
			this.released = !taken;
		}

		@Override
		boolean update(int age) {
			return inner.drive(age);
		}

		@Override
		void end() {
			if (!released) {
				released = true;
				PhantomDisplays.releaseLarge();
			}
		}
	}

	/** SHAKE, FLASH, VIGNETTE and SKY: fired once after the layer's delay. */
	private static final class PostRun extends LayerRun {
		PostRun(FxEvent ev, FxLayer layer, int life, int index) {
			super(ev, layer, Math.max(1, life), index);
		}

		@Override
		boolean update(int age) {
			if (age > 0) {
				return false;
			}
			Minecraft minecraft = Minecraft.getInstance();
			Vec3 camera = minecraft.gameRenderer.mainCamera().position();
			boolean near = ev.own || camera.distanceToSqr(ev.pos) <= VIGNETTE_RANGE * VIGNETTE_RANGE;
			switch (layer.template()) {
				case SHAKE -> {
					if (!shakeAllowed(ev)) {
						return false;
					}
					float intensity = layer.intensity() > 0.0F ? layer.intensity() : ev.spec.grade().defaultShake();
					if (ev.foreign) {
						intensity = Math.min(FOREIGN_SHAKE, intensity);
					}
					CameraShake.add(intensity, Math.max(4, layer.ticks()), ev.pos, Math.max(ev.radius, layer.radius()), ev.climax());
				}
				case FLASH -> {
					// only one's own casts flash the screen (S19)
					if (ev.own) {
						ScreenFlash.add(layer.color(), layer.alpha() > 0.0F ? layer.alpha() : 0.25F, layer.ticks());
					}
				}
				case VIGNETTE -> {
					if (ev.own) {
						ScreenFlash.vignette(layer.color(), layer.ticks());
					}
				}
				case SKY -> {
					int ticks = Math.max(40, layer.ticks());
					PENDING.add(new ParticleRun(ev, FxLayer.of(FxLayer.Template.CLOUD_DISC, darken(layer.color2())).color2(layer.color())
							.radius(Math.max(16.0F, layer.radius())).count(260).ticks(ticks).inSky().above(24.0F).extra(), ticks, 90));
					boolean taken = PhantomDisplays.takeLarge();
					if (taken || ev.own) {
						PENDING.add(new LargeRun(ev, FxLayer.of(FxLayer.Template.SKY_TEAR, layer.color()).color2(0xFFFFFF).count(240).ticks(ticks)
								.inSky().above(45.0F), ticks, 91, taken, 1.0F));
					}
					if (ev.own && near) {
						ScreenFlash.vignette(layer.color(), ticks);
					}
				}
				default -> {
				}
			}
			return false;
		}

		private static int darken(int rgb) {
			return ((rgb >> 17) & 0x7F) << 16 | ((rgb >> 9) & 0x7F) << 8 | ((rgb >> 1) & 0x7F);
		}
	}

	private SpecFx() {
	}
}
