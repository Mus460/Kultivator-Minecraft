package dev.kultivator.client.fx.spec;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
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
import java.util.Iterator;
import java.util.List;

/**
 * Client side of {@code Payloads.TechFx} (DESIGN 22.4): looks the spec up in the registry by net id and plays the FX
 * layers of the event's phase with the viewer's Effect Quality and the {@link FxBudget}: particle templates
 * ({@link ParticleRun}), client-only phantom displays ({@link DisplayRun}), the custom renderers ({@link RendererRun},
 * {@link AvatarLayer}) and the post effects ({@link CameraShake}, {@link ScreenFlash}). Sounds are played by the server.
 * <p>
 * Escalation (22.4.7): layers above the caster's visual tier are skipped, sizes and counts grow with it, mastery 3 adds a
 * gold rim and mastery 4 an afterimage. Legacy specs add {@link LegacyLayers}. Mode Kehancuran (flag 1) adds the CRACKS
 * layer and block crumbs at the impact; a cast without it never shows CRACKS. Other players' casts follow "Efek pemain
 * lain"; the low-load flag halves particles and stops displays. At most {@link FxBudget#MAX_UPDATES} layers update per
 * tick (the oldest EXTRA ones end first). Idle cost is zero.
 */
public final class SpecFx {

	private static final int GOLD = 0xFFD54A;
	private static final FxLayer RIM = FxLayer.of(FxLayer.Template.SHOCK_RING, GOLD).color2(0xFFF1B8).radius(1.6F).count(28).ticks(8).detail();
	private static final FxLayer AUTO_CRACKS = FxLayer.of(FxLayer.Template.CRACKS, 0x3A2A1A).count(60).ticks(8);
	/** Large translucent particle layers (dome, sky tear) share the display limit of 2. */
	private static final double VIGNETTE_RANGE = 48.0D;

	private static final List<LayerRun> RUNS = new ArrayList<>();
	/** Layers started while the list is being ticked (SKY), added after the pass. */
	private static final List<LayerRun> PENDING = new ArrayList<>();
	private static @Nullable ClientLevel lastLevel;
	private static int pruneTicks;
	private static long displaysVersion = -1L;
	private static boolean displaysCached = true;

	/** Registers the client tick and render hooks (called once from {@code KultivatorClient}). */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SpecFx::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("technique_flash"), new ScreenFlash.Hud());
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new AvatarLayer(avatar));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAll());
	}

	/** Draws one technique event (called on the client thread by the payload receiver). */
	public static void play(Payloads.TechFx payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		TechniqueSpec spec = TechniqueRegistry.byNet(payload.spec());
		if (spec == null) {
			return;
		}
		Entity caster = minecraft.level.getEntity(payload.caster());
		boolean others = caster instanceof Player && caster != minecraft.player;
		float othersFactor = others ? EffectQuality.othersScale() : 1.0F;
		if (othersFactor <= 0.0F) {
			return;
		}
		boolean displays = PhantomDisplays.enabled() && !(others && EffectQuality.others() != EffectQuality.Others.FULL);
		FxEvent ev = new FxEvent(spec, payload, EffectQuality.level().factor(), othersFactor, displays,
				EffectQuality.displayAllowance(spec.grade().tier()), FxBudget.perEvent());
		List<FxLayer> layers = layersOf(spec, ev);
		for (int i = 0; i < layers.size(); i++) {
			start(ev, layers.get(i), i);
		}
	}

	/** The layers of this event: the spec's phase, legacy tiers, mastery marks, Kehancuran cracks, default hits. */
	private static List<FxLayer> layersOf(TechniqueSpec spec, FxEvent ev) {
		List<FxLayer> list = new ArrayList<>(spec.fx(ev.phase));
		if (ev.phase == Phase.MASS_HIT && list.isEmpty()) {
			list.addAll(spec.fx(Phase.HIT));
		}
		if (spec.legacy() != null) {
			list.addAll(LegacyLayers.layers(spec.legacy(), ev));
		}
		int ink = spec.dao() == null ? 0xE8DDB5 : spec.dao().color();
		if ((ev.phase == Phase.HIT || ev.phase == Phase.MASS_HIT) && list.isEmpty()) {
			list.add(FxLayer.of(FxLayer.Template.IMPACT, ink).color2(0xFFFFFF).radius(ev.phase == Phase.MASS_HIT ? Math.max(1.0F, ev.radius) : 0.8F)
					.count(ev.phase == Phase.MASS_HIT ? 10 + 3 * Math.min(8, ev.count) : 10).ticks(3));
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
		if (layer.minTier() > ev.tier || layer.onlyKehancuran() && !ev.has(Payloads.TechFx.FLAG_KEHANCURAN)) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (layer.variant() == FxLayer.Variant.NIGHT && (level == null || !level.isDarkOutside())) {
			// night-only looks (fireflies) are skipped by day
			return;
		}
		FxLayer.Template template = layer.template();
		int life = lifetime(layer, ev);
		LayerRun run = switch (template.group()) {
			case PARTICLE -> {
				if (template == FxLayer.Template.DOME || template == FxLayer.Template.SKY_TEAR) {
					boolean taken = PhantomDisplays.takeLarge();
					if (!taken && !ev.own) {
						yield null;
					}
					yield new LargeRun(ev, layer, life, index, taken);
				}
				yield new ParticleRun(ev, layer, life, index);
			}
			case DISPLAY -> new DisplayRun(ev, layer, life, index);
			case RENDERER -> new RendererRun(ev, layer, life, index);
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

	/** Ticks a layer plays: its own, stretched to the event's duration for lasting looks, within the 22.15.2 caps. */
	private static int lifetime(FxLayer layer, FxEvent ev) {
		int ticks = Math.max(1, layer.ticks());
		boolean lasting = switch (layer.template()) {
			case DOME, MIST, CLOUD_DISC, RUNE_CIRCLE, STARFIELD, WINGS, SHIELD_SHELL, VORTEX, SWARM, AVATAR, CLONE, SKY_TEAR, SWORD_RING,
				 PETAL_RING, GLYPH, HEAL_RISE, GIANT_BLOCK -> true;
			default -> false;
		};
		if (lasting && ev.duration > ticks) {
			ticks = ev.duration;
		}
		int cap;
		if (layer.template().group() == FxLayer.Group.DISPLAY) {
			boolean longLived = ev.spec.lasting() || ev.duration > 0 || ev.spec.type() == dev.kultivator.data.TechniqueType.UBAH;
			cap = longLived ? ev.spec.dewa() ? 900 : 600 : 100;
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
		}
		if (level == null || minecraft.isPaused()) {
			return;
		}
		FxBudget.tick();
		if (RUNS.size() > FxBudget.MAX_UPDATES) {
			for (Iterator<LayerRun> it = RUNS.iterator(); it.hasNext() && RUNS.size() > FxBudget.MAX_UPDATES; ) {
				LayerRun run = it.next();
				if (run.extra()) {
					run.end();
					it.remove();
				}
			}
		}
		// at most MAX_UPDATES layers per tick, round-robin: past that many, each layer still ages every few ticks and ends
		int total = RUNS.size();
		int count = Math.min(total, FxBudget.MAX_UPDATES);
		int start = total <= FxBudget.MAX_UPDATES ? 0 : Math.floorMod(cursor, total);
		List<LayerRun> batch = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			batch.add(RUNS.get((start + i) % total));
		}
		cursor = total <= FxBudget.MAX_UPDATES ? 0 : start + count;
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
		CameraShake.clear();
		ScreenFlash.clear();
		lastLevel = null;
	}

	/** Layers playing right now (debug line). */
	public static int activeLayers() {
		return RUNS.size();
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

	/** A dome or sky tear holding one of the two large translucent layer slots while it plays. */
	private static final class LargeRun extends LayerRun {
		private final ParticleRun inner;
		private boolean released;

		LargeRun(FxEvent ev, FxLayer layer, int life, int index, boolean taken) {
			super(ev, layer, life, index);
			this.inner = new ParticleRun(ev, layer, life, index);
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
					float intensity = layer.intensity() > 0.0F ? layer.intensity() : ev.spec.grade().defaultShake();
					CameraShake.add(intensity, Math.max(4, layer.ticks()), ev.pos, Math.max(ev.radius, layer.radius()));
				}
				case FLASH -> {
					if (near) {
						ScreenFlash.add(layer.color(), layer.alpha() > 0.0F ? layer.alpha() : 0.25F, layer.ticks());
					}
				}
				case VIGNETTE -> {
					if (near) {
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
								.inSky().above(45.0F), ticks, 91, taken));
					}
					if (near) {
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
