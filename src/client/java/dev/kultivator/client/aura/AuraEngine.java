package dev.kultivator.client.aura;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.disciple.DiscipleLook;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.render.MeditationPose;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.VisualState;
import dev.kultivator.disciple.DiscipleEntity;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Aura Kultivasi engine (DESIGN 31.2): finds the cultivators who cultivate (players seated in meditation, disciples
 * seated, or anyone with a live {@code /kultivasi uji aura} preview) within 64 blocks, keeps one {@link AuraRig} per
 * entity, runs the lifecycle (fade in 30 ticks, out 20, held at half while breaking through, ended by a Qi deviation or
 * a tribulation), the distance LOD (full ≤ 24 b, reduced ≤ 64 b), the budgets per Effect Quality, the hums and the realm
 * drivers ({@link #register}). Presentation only: nothing here touches a block, an entity of the server, the camera,
 * the weather or the time.
 * <p>
 * Idle cost with nobody cultivating: one pass over {@code level.players()} per tick and one disciple box query every 10
 * ticks.
 */
public final class AuraEngine {

	/** Full detail up to this distance from the camera (blocks). */
	public static final double FULL_RANGE = 24.0D;
	/** Reduced detail up to this distance; nothing beyond. */
	public static final double REDUCED_RANGE = 64.0D;
	/** Fade in and out (ticks). */
	public static final int FADE_IN = 30;
	public static final int FADE_OUT = 20;
	/** Props shrink away over this many ticks when the aura ends. */
	public static final int PROP_FADE = 12;

	// ---- budgets by quality: Minimal, Normal, Maximal (31.2.5)
	static final int[] FULL_AURAS = {1, 4, 8};
	static final int[] REDUCED_AURAS = {2, 8, 16};
	static final int[] FRAME_QUADS = {120, 600, 1500};
	static final int[] AURA_QUADS = {60, 200, 400};
	static final int[] PROPS_PER_AURA = {0, 3, 6};
	static final int[] PROPS_ALL = {0, 9, 24};
	static final int[] PARTICLES = {1, 4, 10};
	static final int[] RIBBONS = {0, 2, 4};
	static final int[] HUMS = {1, 2, 2};
	/** Ticks a rig survives without its entity. */
	private static final int GONE_TICKS = 40;
	private static final int BELL_EVERY = 120;

	private static final AuraRealmDriver[] DRIVERS = new AuraRealmDriver[Realm.values().length];
	private static final Map<Integer, AuraRig> RIGS = new HashMap<>();
	private static final List<AuraRig> SORTED = new ArrayList<>();
	private static final List<Integer> DISCIPLES = new ArrayList<>();
	private static final Set<Integer> SEEN = new HashSet<>();
	private static final List<AuraRig> HUM_PICK = new ArrayList<>();
	private static final AuraDraw DRAW = new AuraDraw();
	private static int ticks;
	private static int quality = 1;
	private static int propsAlive;
	private static int propCap;
	private static int warnings;

	// ================================================================== API (C31.1)

	/** Registers the driver of a realm (one per realm: E registers 0-4, {@code AuraHigh.init()} 5-8). */
	public static void register(Realm realm, AuraRealmDriver driver) {
		if (realm != null && driver != null) {
			DRIVERS[realm.ordinal()] = driver;
		}
	}

	/** True while the new aura draws this entity (its fade is above 0): the old aura and meditation motes step aside. */
	public static boolean covers(int entityId) {
		AuraRig rig = RIGS.get(entityId);
		return rig != null && rig.raw > 0.0F;
	}

	/** The registered driver of a realm, or null. */
	public static @Nullable AuraRealmDriver driver(Realm realm) {
		return DRIVERS[realm.ordinal()];
	}

	/** The quality in effect for the aura: {@code min(Effect Quality, EpicBudget.tier())}; −1 = particles only. */
	public static int quality() {
		return quality;
	}

	// ================================================================== budgets used by rigs and particles

	static boolean takeProp() {
		if (propsAlive >= propCap) {
			return false;
		}
		propsAlive++;
		return true;
	}

	static void giveBackProp() {
		propsAlive = Math.max(0, propsAlive - 1);
	}

	static boolean takeParticle(int entityId) {
		AuraRig rig = RIGS.get(entityId);
		if (rig == null || rig.particleTokens < 1.0F) {
			return false;
		}
		rig.particleTokens -= 1.0F;
		return true;
	}

	static void refundParticle(int entityId) {
		AuraRig rig = RIGS.get(entityId);
		if (rig != null) {
			rig.particleTokens += 1.0F;
		}
	}

	/** Your own rig while you see your own aura through your own eyes (for the HUD), else null. */
	static @Nullable AuraRig selfView() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return null;
		}
		AuraRig rig = RIGS.get(minecraft.player.getId());
		return rig != null && rig.firstPersonSelf && rig.raw > 0.0F && rig.subject != null ? rig : null;
	}

	// ================================================================== tick

	/** Once per unpaused client tick (from {@code AuraClient.tick}). */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		ticks++;
		quality = Math.min(EffectQuality.level().ordinal(), EpicBudget.tier());
		int q = Math.max(0, quality);
		AuraSettings.Mode mode = AuraSettings.mode();
		Vec3 camera = minecraft.gameRenderer.mainCamera().position();
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		Entity cameraEntity = minecraft.getCameraEntity();
		long gameTime = level.getGameTime();
		boolean simple = mode == AuraSettings.Mode.SEDERHANA;

		// 1. who cultivates (or is previewed)
		SEEN.clear();
		if (mode != AuraSettings.Mode.MATI) {
			for (AbstractClientPlayer player : level.players()) {
				considerPlayer(minecraft, player, camera, firstPerson && cameraEntity == player);
			}
			if (ticks % 10 == 0) {
				refreshDisciples(level, camera);
			}
			for (int i = 0; i < DISCIPLES.size(); i++) {
				if (level.getEntity(DISCIPLES.get(i)) instanceof DiscipleEntity disciple && !SEEN.contains(disciple.getId())) {
					considerDisciple(minecraft, disciple, camera);
				}
			}
			if (AuraPreviewClient.any()) {
				for (Integer id : new ArrayList<>(AuraPreviewClient.ids())) {
					if (SEEN.contains(id)) {
						continue;
					}
					Entity entity = level.getEntity(id);
					if (entity instanceof DiscipleEntity disciple) {
						considerDisciple(minecraft, disciple, camera);
					}
				}
			}
		}

		// 2. rank the wanted auras (yours first, then the nearest) and give out the LOD slots
		SORTED.clear();
		SORTED.addAll(RIGS.values());
		SORTED.sort((a, b) -> {
			if (a.ownPlayer != b.ownPlayer) {
				return a.ownPlayer ? -1 : 1;
			}
			return Double.compare(a.distance, b.distance);
		});
		int full = 0;
		int reduced = 0;
		for (AuraRig rig : SORTED) {
			if (!SEEN.contains(rig.entityId)) {
				rig.want = false;
				rig.goneTicks++;
			} else {
				rig.goneTicks = 0;
			}
			float lod = 0.0F;
			if (rig.want) {
				if (rig.distance <= FULL_RANGE && full < FULL_AURAS[q]) {
					lod = 1.0F;
					full++;
				} else if (rig.distance <= REDUCED_RANGE && reduced < REDUCED_AURAS[q]) {
					lod = 0.5F;
					reduced++;
				} else {
					rig.want = false;
				}
			}
			if (lod > 0.0F) {
				rig.lodNow = lod;
			}
		}

		// 3. props alive now (recounted every tick, so the budget never drifts)
		propsAlive = 0;
		for (AuraRig rig : SORTED) {
			propsAlive += rig.liveProps();
		}
		propCap = Math.min(PROPS_ALL[q], EffectQuality.displaysAlive() / 3);
		if (quality < 0) {
			propCap = 0;
		}

		// 4. lifecycle, drivers and particles
		for (Iterator<AuraRig> it = SORTED.iterator(); it.hasNext(); ) {
			AuraRig rig = it.next();
			rig.gameTime = gameTime;
			rig.age++;
			rig.partial = 0.0F;
			rig.rawPrev = rig.raw;
			rig.holdPrev = rig.hold;
			boolean wasWanted = rig.wantedBefore;
			rig.wantedBefore = rig.want;
			rig.raw = rig.want ? Math.min(1.0F, rig.raw + 1.0F / FADE_IN) : Math.max(0.0F, rig.raw - 1.0F / FADE_OUT);
			float holdTarget = rig.breaking ? 0.5F : 1.0F;
			rig.hold += Mth.clamp(holdTarget - rig.hold, -0.05F, 0.05F);
			if (rig.base == null) {
				continue;
			}
			float fade = rig.fadeNow();
			int qProps = simple ? 0 : q;
			AuraSubject s = rig.base.withState(fade, rig.lodNow, rig.share, rig.firstPersonSelf, simple);
			rig.subject = s;
			if (wasWanted && !rig.want) {
				// cultivation stopped: the props shrink away once while the quads fade out
				rig.endProps(PROP_FADE);
			}
			// a new realm: the old driver stops, the new one starts fresh (with the roll-out ring of a change)
			boolean newRealm = rig.started && rig.realm != s.realm();
			if (newRealm) {
				stopDriver(rig, s, 10);
			}
			if (fade > 0.0F && !rig.started) {
				startDriver(rig, s, newRealm);
			}
			int key = s.stage() * 16 + s.layer();
			if (rig.started && key != rig.stageKey) {
				if (rig.stageKey != Integer.MIN_VALUE) {
					rig.stageChangedAt = rig.age;
				}
				rig.stageKey = key;
			}
			int allowance = rig.want && rig.lodNow >= 1.0F && rig.share >= 1.0F && !rig.firstPersonSelf && quality >= 0 ? PROPS_PER_AURA[qProps] : 0;
			rig.propAllowance = allowance;
			if (allowance == 0 && rig.liveProps() > 0) {
				rig.endProps(rig.want ? 10 : PROP_FADE);
			}
			int qParticles = simple ? Math.min(q, 1) : q;
			float perTick = PARTICLES[qParticles] * rig.share * (rig.lodNow < 1.0F ? 0.25F : 1.0F) * fade;
			rig.particleTokens = Math.min(Math.max(1.0F, perTick * 2.0F), rig.particleTokens + perTick);
			if (fade > 0.0F) {
				if (rig.firstPersonSelf) {
					firstPersonMotes(minecraft, rig, s);
				} else if (rig.driver != null) {
					try {
						rig.driver.tick(s, rig);
					} catch (RuntimeException exception) {
						warn("tick " + s.realm().id(), exception);
					}
				}
			}
			if (rig.raw <= 0.0F && !rig.want || rig.goneTicks > GONE_TICKS) {
				stopDriver(rig, s, PROP_FADE);
				rig.endProps(PROP_FADE);
				if (rig.hum != null) {
					rig.hum.release();
					rig.hum = null;
				}
				RIGS.remove(rig.entityId);
				it.remove();
			}
		}

		// 5. sound
		sound(minecraft, mode, q);
	}

	private static void startDriver(AuraRig rig, AuraSubject s, boolean change) {
		rig.reset();
		rig.realm = s.realm();
		rig.driver = DRIVERS[s.realm().ordinal()];
		rig.started = true;
		rig.stageKey = s.stage() * 16 + s.layer();
		rig.stageChangedAt = change ? rig.age : Integer.MIN_VALUE;
		rig.nextChime = 0;
		if (rig.driver != null) {
			try {
				rig.driver.start(s, rig);
			} catch (RuntimeException exception) {
				warn("start " + s.realm().id(), exception);
			}
		}
	}

	private static void stopDriver(AuraRig rig, AuraSubject s, int fadeTicks) {
		if (rig.started && rig.driver != null) {
			try {
				rig.driver.stop(s, rig);
			} catch (RuntimeException exception) {
				warn("stop " + s.realm().id(), exception);
			}
		}
		rig.endProps(fadeTicks);
		rig.started = false;
		rig.driver = null;
		rig.realm = null;
		if (rig.hum != null) {
			rig.hum.release();
			rig.hum = null;
		}
	}

	// ================================================================== subjects

	private static void considerPlayer(Minecraft minecraft, AbstractClientPlayer player, Vec3 camera, boolean firstPersonSelf) {
		int id = player.getId();
		SEEN.add(id);
		AuraPreviewClient.Preview preview = AuraPreviewClient.get(id);
		VisualState visual = ClientCultivation.visual(player);
		boolean seated = MeditationPose.posed(visual) && MeditationPose.canSit(player);
		boolean active = seated && visual.has(VisualState.SHOW_AURA) || preview != null;
		if (player.isRemoved() || !player.isAlive() || player.isSpectator() || player.isInvisible()
				|| visual.has(VisualState.DEVIATION) || visual.has(VisualState.TRIBULATION)) {
			active = false;
		}
		boolean own = player == minecraft.player;
		float share = shareOf(own);
		if (share <= 0.0F) {
			active = false;
		}
		double distance = player.position().distanceTo(camera);
		if (distance > REDUCED_RANGE) {
			active = false;
		}
		AuraRig rig = RIGS.get(id);
		if (rig == null) {
			if (!active) {
				return;
			}
			rig = new AuraRig(id);
			RIGS.put(id, rig);
		}
		// while the aura fades out after standing up, it keeps the seated shape (no jump upward)
		boolean shape = active || rig.base == null ? seated : rig.base.seated();
		Realm realm = preview != null ? preview.realm() : visual.realmEnum();
		int layer = preview != null ? preview.layer() : visual.layer();
		int stage = preview != null ? preview.stage() : visual.stage();
		if (!active && rig.base != null) {
			// fading out (a preview ended, cultivation stopped): keep the realm shown, no driver swap mid-fade
			realm = rig.base.realm();
			layer = rig.base.layer();
			stage = rig.base.stage();
		}
		int accent = preview != null && preview.has(AuraPreviewClient.FORCED_ACCENT) || visual.has(VisualState.HEAVENLY_ROOT)
				? AuraColors.HEAVENLY : -1;
		boolean bottleneck = preview != null ? preview.has(AuraPreviewClient.BOTTLENECK) : visual.has(VisualState.BOTTLENECK_READY);
		bottleneck &= !isMax(realm, stage);
		rig.base = new AuraSubject(id, player, false, own, player.position(), player.getScale(),
				player.getBbHeight() * (shape ? 0.69D : 1.0D), player.yBodyRot, shape, player.onGround(), realm, clampLayer(realm, layer),
				clampStage(realm, stage), visual.dao(), visual.rootColor() & 0xFFFFFF, visual.rootElements(), accent, bottleneck,
				visual.has(VisualState.ASCENDED), visual.has(VisualState.DHARMA), 0.0F, 1.0F, share, firstPersonSelf, false, preview != null);
		rig.want = active;
		rig.breaking = visual.has(VisualState.BREAKING_THROUGH);
		rig.share = share;
		rig.ownPlayer = own;
		rig.firstPersonSelf = firstPersonSelf;
		rig.distance = distance;
		rig.lastFeet = player.position();
	}

	private static void considerDisciple(Minecraft minecraft, DiscipleEntity disciple, Vec3 camera) {
		int id = disciple.getId();
		SEEN.add(id);
		AuraPreviewClient.Preview preview = AuraPreviewClient.get(id);
		boolean seated = DiscipleLook.seated(disciple);
		int flags = DiscipleLook.flags(disciple);
		boolean active = seated || preview != null;
		if (disciple.isRemoved() || !disciple.isAlive() || disciple.isInvisible() || (flags & DiscipleLook.DEVIATING) != 0) {
			active = false;
		}
		boolean own = disciple.isOwnedBy(minecraft.player);
		float share = shareOf(own);
		if (share <= 0.0F) {
			active = false;
		}
		double distance = disciple.position().distanceTo(camera);
		if (distance > REDUCED_RANGE) {
			active = false;
		}
		AuraRig rig = RIGS.get(id);
		if (rig == null) {
			if (!active) {
				return;
			}
			rig = new AuraRig(id);
			RIGS.put(id, rig);
		}
		boolean shape = active || rig.base == null ? seated : rig.base.seated();
		Rank rank = DiscipleLook.rank(disciple);
		Realm realm = preview != null ? preview.realm() : rank.realm();
		int layer = preview != null ? preview.layer() : rank.layer();
		int stage = preview != null ? preview.stage() : rank.stage();
		if (!active && rig.base != null) {
			realm = rig.base.realm();
			layer = rig.base.layer();
			stage = rig.base.stage();
		}
		int aura = disciple.syncedAura();
		int accent;
		if (preview != null && preview.has(AuraPreviewClient.FORCED_ACCENT) || (aura & DiscipleEntity.AURA_HEAVENLY_ROOT) != 0) {
			accent = AuraColors.HEAVENLY;
		} else {
			accent = AuraColors.physique(DiscipleEntity.talentPhysique(disciple.syncedTalent()));
		}
		boolean bottleneck = preview != null ? preview.has(AuraPreviewClient.BOTTLENECK) : (aura & DiscipleEntity.AURA_BOTTLENECK_READY) != 0;
		bottleneck &= !isMax(realm, stage);
		rig.base = new AuraSubject(id, disciple, true, own, disciple.position(), disciple.getScale(),
				disciple.getBbHeight() * (shape ? 0.69D : 1.0D), disciple.yBodyRot, shape, disciple.onGround(), realm, clampLayer(realm, layer),
				clampStage(realm, stage), DiscipleLook.dao(disciple), DiscipleLook.rootColor(disciple), DiscipleEntity.auraElements(aura), accent,
				bottleneck, false, false, 0.0F, 1.0F, share, false, false, preview != null);
		rig.want = active;
		rig.breaking = (flags & DiscipleLook.BREAKING_THROUGH) != 0;
		rig.share = share;
		rig.ownPlayer = false;
		rig.firstPersonSelf = false;
		rig.distance = distance;
		rig.lastFeet = disciple.position();
	}

	private static void refreshDisciples(ClientLevel level, Vec3 camera) {
		DISCIPLES.clear();
		AABB box = AABB.ofSize(camera, REDUCED_RANGE * 2.0D, REDUCED_RANGE * 2.0D, REDUCED_RANGE * 2.0D);
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, box,
				d -> DiscipleLook.seated(d) || AuraPreviewClient.get(d.getId()) != null || RIGS.containsKey(d.getId()))) {
			DISCIPLES.add(disciple.getId());
		}
	}

	/** 1 for yourself and your own disciples; the "Efek pemain lain" factor for others, 0 when "Aura orang lain" is off. */
	private static float shareOf(boolean own) {
		if (own) {
			return 1.0F;
		}
		return AuraSettings.others() ? EffectQuality.othersScale() : 0.0F;
	}

	private static boolean isMax(Realm realm, int stage) {
		return realm.isFinal() && stage >= realm.stages() - 1;
	}

	private static int clampLayer(Realm realm, int layer) {
		return realm.layers() > 1 ? Mth.clamp(layer, 0, realm.layers() - 1) : 0;
	}

	private static int clampStage(Realm realm, int stage) {
		return Mth.clamp(stage, 0, Math.max(0, realm.stages() - 1));
	}

	// ================================================================== first person (31.2.7)

	/** At most one mote every 4 ticks drifting across the lower half of the view, below the eye line. */
	private static void firstPersonMotes(Minecraft minecraft, AuraRig rig, AuraSubject s) {
		if (rig.age % 4 != 0 || minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		Entity self = minecraft.player;
		Vec3 eye = self.getEyePosition();
		Vec3 look = self.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		if (flat.lengthSqr() < 1.0E-4D) {
			return;
		}
		flat = flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
		long t = rig.clock();
		float a = AuraRng.of(rig.entityId, t, 11);
		float b = AuraRng.signed(rig.entityId, t, 12);
		float c = AuraRng.of(rig.entityId, t, 13);
		double distance = 1.2D + 0.8D * a;
		Vec3 at = eye.add(flat.scale(distance)).add(side.scale(b * 0.9D)).add(0.0D, -(0.45D + 0.4D * c), 0.0D);
		Vec3 velocity = side.scale(-b * 0.006D).add(0.0D, 0.006D, 0.0D);
		AuraColors.Palette palette = s.colors();
		int rgb = (rig.age / 4) % 3 == 0 ? palette.inflow() : palette.glow();
		AuraParticles.mote(s, at, velocity, rgb, 0.06F, 30);
	}

	// ================================================================== sound (31.2.9)

	private static void sound(Minecraft minecraft, AuraSettings.Mode mode, int q) {
		HUM_PICK.clear();
		boolean on = AuraSettings.hum() && mode != AuraSettings.Mode.MATI;
		if (on) {
			for (AuraRig rig : SORTED) {
				if (rig.subject != null && rig.driver != null && rig.raw > 0.0F && rig.lodNow >= 1.0F && rig.distance <= AuraHum.RANGE) {
					HUM_PICK.add(rig);
				}
			}
			HUM_PICK.sort((a, b) -> Double.compare(a.distance, b.distance));
			while (HUM_PICK.size() > HUMS[q]) {
				HUM_PICK.remove(HUM_PICK.size() - 1);
			}
		}
		for (AuraRig rig : SORTED) {
			boolean picked = HUM_PICK.contains(rig);
			AuraSubject s = rig.subject;
			if (!picked || s == null || rig.driver == null) {
				if (rig.hum != null) {
					rig.hum.release();
					rig.hum = null;
				}
				continue;
			}
			AuraHum.Spec spec;
			try {
				spec = rig.driver.hum(s);
			} catch (RuntimeException exception) {
				warn("hum", exception);
				spec = AuraHum.Spec.NONE;
			}
			Vec3 at = s.chest();
			float fade = rig.fadeNow();
			if (spec.loop() != null) {
				if (rig.hum != null && (rig.hum.released() || rig.hum.event() != spec.loop())) {
					rig.hum.release();
					rig.hum = null;
				}
				if (rig.hum == null) {
					rig.hum = AuraHum.start(spec.loop(), spec.pitch(), at);
				}
				if (rig.hum != null) {
					rig.hum.aim(spec.volume() * fade, at);
				}
			} else if (rig.hum != null) {
				rig.hum.release();
				rig.hum = null;
			}
			if (spec.chime() != null && spec.chimeEvery() > 0 && fade > 0.5F) {
				int every = Math.max(AuraHum.MIN_CHIME_EVERY, spec.chimeEvery());
				if (rig.nextChime == 0) {
					rig.nextChime = rig.age + every / 2;
				} else if (rig.age >= rig.nextChime) {
					rig.nextChime = rig.age + every;
					AuraHum.chime(spec.chime(), AuraHum.CHIME_VOLUME * fade, spec.chimePitch(), at);
				}
			}
			if (s.bottleneck() && fade > 0.5F) {
				if (rig.nextBell == 0 || rig.age >= rig.nextBell) {
					if (rig.nextBell != 0) {
						AuraHum.bottleneckBell(at);
					}
					rig.nextBell = rig.age + BELL_EVERY;
				}
			} else {
				rig.nextBell = 0;
			}
		}
	}

	// ================================================================== render

	/** Every frame from Fabric {@code LevelRenderEvents.COLLECT_SUBMITS} (registered after {@code QuadFx}). */
	static void render(LevelRenderContext context) {
		if (SORTED.isEmpty() || ShaderCompat.shadowPass() || quality < 0) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		try {
			CameraRenderState camera = context.levelState().cameraRenderState;
			float partial = context.levelState().worldPartialTicks;
			boolean pack = ShaderCompat.packOn();
			int q = Math.max(0, quality);
			int frameCap = Math.round(FRAME_QUADS[q] * (pack ? EpicBudget.PACK_QUADS : 1.0F));
			DRAW.setup(camera, context.poseStack(), context.submitNodeCollector(), frameCap, pack);
			for (int i = 0; i < SORTED.size(); i++) {
				AuraRig rig = SORTED.get(i);
				AuraSubject base = rig.subject;
				if (base == null || rig.driver == null || rig.firstPersonSelf || rig.lodNow <= 0.0F) {
					continue;
				}
				float fade = rig.fade(partial);
				if (fade <= 0.003F) {
					continue;
				}
				Entity entity = base.entity();
				Vec3 feet;
				float yaw;
				if (entity != null && !entity.isRemoved()) {
					feet = entity.getPosition(partial);
					yaw = entity instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : entity.getYRot();
				} else {
					feet = rig.lastFeet;
					yaw = base.bodyYaw();
				}
				AuraSubject s = base.atFrame(feet, yaw, fade);
				int auraCap = s.simple() ? AURA_QUADS[0] : AURA_QUADS[q];
				if (s.share() < 1.0F) {
					auraCap /= 2;
				}
				int ribbonCap = s.simple() || s.reduced() ? 0 : RIBBONS[q];
				rig.partial = partial;
				DRAW.begin(s, auraCap, ribbonCap);
				try {
					rig.driver.render(s, rig, DRAW, partial);
					AuraOverlays.draw(s, rig, DRAW, rig.driver.reach(s));
				} catch (RuntimeException exception) {
					warn("render " + s.realm().id(), exception);
				}
				DRAW.end();
				rig.partial = 0.0F;
			}
			DRAW.flush();
		} catch (RuntimeException exception) {
			warn("render", exception);
			DRAW.clear();
		}
	}

	// ================================================================== housekeeping

	/** Drops every aura (world change, disconnect). */
	static void clear() {
		for (AuraRig rig : RIGS.values()) {
			rig.endProps(1);
			if (rig.hum != null) {
				rig.hum.release();
				rig.hum = null;
			}
		}
		RIGS.clear();
		SORTED.clear();
		DISCIPLES.clear();
		SEEN.clear();
		HUM_PICK.clear();
		DRAW.clear();
		propsAlive = 0;
	}

	static int live() {
		return RIGS.size();
	}

	static void warn(String where, Throwable exception) {
		if (warnings++ < 8) {
			Kultivator.LOGGER.warn("Kultivator: Aura Kultivasi error in {}", where, exception);
		}
	}

	private AuraEngine() {
	}
}
