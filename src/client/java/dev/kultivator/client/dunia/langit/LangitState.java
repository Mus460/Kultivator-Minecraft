package dev.kultivator.client.dunia.langit;

import dev.kultivator.abadi.Abadi;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dunia.DuniaScenes;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.Scene;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.epic.sky.SkyDirector;
import dev.kultivator.client.fx.spec.CameraShake;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.dunia.langit.LangitCuacaPayload;
import dev.kultivator.dunia.langit.LangitFxPayload;
import dev.kultivator.dunia.langit.LangitHartaPayload;
import dev.kultivator.dunia.langit.LangitSkyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the client knows of module L (DESIGN 34.5.8-34.5.10): the sky event of its dimension, the 3 × 3 spirit
 * weather cells around the player, the live treasure births (their pillars), the one-off effects and the eclipse marks.
 * Filled by the four payloads; eased once per client tick; read by {@link LangitRenderer}, {@link LangitHud}, the
 * particle pass of {@link LangitParticles} and the sounds of {@link LangitAmbience}. Also the treasure guardians near the
 * player, the mist colour of the local biome, the Meteor Agung's path and the short bolide flash. All of it is cleared on a world change or a disconnect.
 * <p>
 * The sky grades go through the 7b {@link SkyDirector} at the lowest priority (0), refreshed every 5 s so any epic moment
 * may take the sky and L's grade comes back after it: Gerhana Darah red 0.35, the Qi mists white 0.25 / 0.5, Fajar Ungu
 * a violet Dao tint, the meteor shower a starry grade. Fog distances, the time and the weather are never touched.
 */
final class LangitState {

	static final int SKY_PRIORITY = 0;

	// ---- the sky event
	static int skyKind = -1;
	/** The last kind shown, kept while the sky fades back after the event ended. */
	private static int fadeKind = -1;
	static int skyState = LangitSkyPayload.NONE;
	static int skySeed;
	static long skyStart;
	static long skyEnd;
	/** 0..1, eased over 10 s towards 1 while the event is active. */
	static float skyWeight;
	private static float skyWeightO;

	// ---- the spirit weather
	static int cellSize;
	static int cellX;
	static int cellZ;
	static byte[] kinds = new byte[9];
	/** The mist strength at the player (0..1: thin 0.5, dense 1), eased; the kind in effect for colours. */
	static float mist;
	private static float mistO;
	static SpiritWeather weather = SpiritWeather.CLEAR;
	/** 0..1, eased: how much of the mist is the violet Fajar Ungu. */
	static float dawn;

	// ---- treasure births
	static final class BirthView {
		final long id;
		int x;
		int y;
		int z;
		int tier;
		String kind;
		long born;
		long ends;
		int phase;
		/** 0..1 fade-in; after the end, the collapse counts down {@link #ending} ticks. */
		float alpha;
		int ending = -1;
		int seed;

		BirthView(long id) {
			this.id = id;
		}

		Vec3 base() {
			return new Vec3(x + 0.5D, y, z + 0.5D);
		}

		boolean live() {
			return ending < 0;
		}
	}

	static final Map<Long, BirthView> BIRTHS = new LinkedHashMap<>();

	// ---- one-off effects (impacts, bursts, flashes)
	static final class Fx {
		final int kind;
		final double x;
		final double y;
		final double z;
		final int a;
		final int life;
		int age;
		final int seed;
		/** {@link LangitFxPayload#KIND_GREAT_INCOMING}: the start offset of the fall and its ticks. */
		float ox;
		float oy;
		float oz;
		int fall;

		Fx(int kind, double x, double y, double z, int a, int life, int seed) {
			this.kind = kind;
			this.x = x;
			this.y = y;
			this.z = z;
			this.a = a;
			this.life = life;
			this.seed = seed;
		}

		float t(float partial) {
			return Math.min(1.0F, (age + partial) / life);
		}
	}

	static final List<Fx> FX = new ArrayList<>();
	static final int MAX_FX = 24;
	/** A client-only effect kind: the ring that runs over the ground when a treasure is born near the player. */
	static final int FX_BIRTH_WAVE = 100;

	// ---- the treasure guardians near the player (their sigils), forgotten 5 s after the last refresh
	static int[] guards = new int[0];
	static int guardsTier = 1;
	private static int guardsTicks;

	// ---- the colour of the Qi mist where the player stands (by the biome), eased
	static int mistRgb = 0xD8F2E8;
	private static int mistTarget = 0xD8F2E8;
	private static int tintTimer;

	// ---- a white-blue flash over the screen (a bolide bursting, a Meteor Agung landing near), Effect Quality "Kilat" only
	static float flash;
	static int flashRgb = 0xFFFFFF;
	/** Entity ids of the eclipse-touched monsters near the player. */
	static int[] marks = new int[0];

	// ---- the omen of a weak demon prison seal (SEAL_CHANGED, refreshed by the server every 2 s while near)
	private static final int SEAL_OMEN_HOLD = 80;
	private static float sealOmenTarget;
	private static int sealOmenTicks;
	/** 0..1, eased: the ash-red omen around a weak seal. */
	static float sealOmen;
	private static float sealOmenO;
	private static float sealOmenShown;

	private static @Nullable ClientLevel lastLevel;
	private static int gradeTimer;
	private static int gradeShown = -1;
	private static int fxSeed;

	// ================================================================== payloads

	/**
	 * Clears everything when the client's level changed since the last call. Called first by every payload handler and
	 * the tick, so a sync that arrives right after a respawn or dimension change is kept (not wiped by the next tick).
	 */
	private static void ensureLevel() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != lastLevel) {
			lastLevel = level;
			clear();
		}
	}

	static void onSky(LangitSkyPayload payload) {
		ensureLevel();
		int before = skyState;
		int beforeKind = skyKind;
		skyKind = payload.kind();
		if (skyKind >= 0) {
			fadeKind = skyKind;
		}
		skyState = payload.state();
		skySeed = payload.seed();
		skyStart = payload.startClock();
		skyEnd = payload.endClock();
		gradeTimer = 0;
		SkyKind kind = kind();
		if (kind != null && skyState == LangitSkyPayload.ACTIVE && (before != LangitSkyPayload.ACTIVE || beforeKind != skyKind)) {
			begun(kind);
		}
		if (skyState != LangitSkyPayload.ACTIVE) {
			LangitSky.clearStreaks();
		}
	}

	static void onWeather(LangitCuacaPayload payload) {
		ensureLevel();
		cellSize = payload.cellSize();
		cellX = payload.cx();
		cellZ = payload.cz();
		kinds = payload.kinds().length >= 9 ? payload.kinds().clone() : new byte[9];
		gradeTimer = 0;
	}

	static void onBirth(LangitHartaPayload payload) {
		ensureLevel();
		Minecraft minecraft = Minecraft.getInstance();
		if (payload.phase() == LangitHartaPayload.TREMOR) {
			return;
		}
		BirthView view = BIRTHS.get(payload.id());
		if (payload.over()) {
			if (view != null && view.live()) {
				view.phase = payload.phase();
				view.ending = 40;
			}
			return;
		}
		boolean fresh = view == null;
		if (fresh) {
			view = new BirthView(payload.id());
			view.seed = (int) (payload.id() ^ (payload.id() >>> 32));
			BIRTHS.put(payload.id(), view);
		}
		view.x = payload.x();
		view.y = payload.y();
		view.z = payload.z();
		view.tier = payload.tier();
		view.kind = payload.kind();
		view.born = payload.born();
		view.ends = payload.ends();
		view.phase = payload.phase();
		view.ending = -1;
		if (fresh && minecraft.player != null && minecraft.level != null && minecraft.level.getGameTime() - payload.born() < 200) {
			// a birth just happened: a far chime and the banner (the chat line names the direction)
			Vec3 base = view.base();
			double distance = Math.sqrt(minecraft.player.distanceToSqr(base.x, minecraft.player.getY(), base.z));
			if (distance < 1500.0D) {
				playAt(SoundEvents.BEACON_ACTIVATE, base, 4.0F, 0.6F);
				TitleCard.show(TitleCard.banner(Component.translatable("kultivator.langit.harta.banner")));
			}
			if (distance < 256.0D) {
				// the birth's shock ring runs over the ground from the pillar's foot
				add(new Fx(FX_BIRTH_WAVE, base.x, base.y, base.z, view.tier, 70, view.seed));
				LangitParticles.birthBurst(minecraft.level, base.x, base.y, base.z, view.tier);
			}
		}
	}

	static void onFx(LangitFxPayload payload) {
		ensureLevel();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		int seed = ++fxSeed * 0x9E3779B9;
		switch (payload.kind()) {
			case LangitFxPayload.KIND_MARKS -> marks = payload.ids().clone();
			case LangitFxPayload.KIND_IMPACT -> {
				add(new Fx(payload.kind(), payload.x(), payload.y(), payload.z(), payload.a(), 400, seed));
				LangitParticles.impact(minecraft.level, payload.x(), payload.y(), payload.z());
				double distance = minecraft.player.position().distanceTo(new Vec3(payload.x(), payload.y(), payload.z()));
				if (distance < 48.0D) {
					CameraShake.add((float) (0.35D * (1.0D - distance / 48.0D)) + 0.1F, 14, minecraft.player.position(), 8.0F);
				}
			}
			case LangitFxPayload.KIND_TREMOR -> {
				float strength = (float) Mth.clamp(1.0D - payload.a() / 1000.0D, 0.15D, 1.0D);
				CameraShake.add(0.25F + 0.45F * strength, 70, minecraft.player.position(), 8.0F);
				Vec3 at = minecraft.player.position();
				playAt(SoundEvents.BELL_RESONATE, at, 0.8F * strength + 0.2F, 0.5F);
				playAt(SoundEvents.AMETHYST_BLOCK_RESONATE, at, 0.6F * strength + 0.2F, 0.5F);
				LangitParticles.tremorDust(minecraft.level, at, strength);
			}
			case LangitFxPayload.KIND_CLAIM, LangitFxPayload.KIND_CLAIM_OTHER -> {
				add(new Fx(LangitFxPayload.KIND_CLAIM, payload.x(), payload.y(), payload.z(), payload.a(), 60, seed));
				// another player's claim: its burst follows the "others" particle setting (22.15.2)
				if (payload.kind() == LangitFxPayload.KIND_CLAIM || minecraft.level.getRandom().nextFloat() < EffectQuality.othersScale()) {
					LangitParticles.claimBurst(minecraft.level, payload.x(), payload.y(), payload.z(), payload.a());
				}
				if (payload.kind() == LangitFxPayload.KIND_CLAIM) {
					claimScene(new Vec3(payload.x(), payload.y(), payload.z()), payload.a(), seed);
				}
			}
			case LangitFxPayload.KIND_DEW -> LangitParticles.dew(minecraft.level, payload.x(), payload.y(), payload.z(), payload.a());
			case LangitFxPayload.KIND_ABSORB -> LangitParticles.absorb(minecraft.level, minecraft.player, payload.a());
			case LangitFxPayload.KIND_CHANNEL_BROKEN -> LangitParticles.broken(minecraft.level, payload.x(), payload.y(), payload.z());
			case LangitFxPayload.KIND_GREAT_INCOMING -> greatIncoming(minecraft, payload, seed);
			case LangitFxPayload.KIND_GREAT_IMPACT -> {
				add(new Fx(payload.kind(), payload.x(), payload.y(), payload.z(), payload.a(), 1200, seed));
				LangitParticles.greatImpact(minecraft.level, payload.x(), payload.y(), payload.z());
				double distance = minecraft.player.position().distanceTo(new Vec3(payload.x(), payload.y(), payload.z()));
				if (distance < 128.0D) {
					CameraShake.add((float) (0.8D * (1.0D - distance / 128.0D)) + 0.15F, 26, minecraft.player.position(), 16.0F);
				}
				if (distance < 160.0D) {
					bolide(0xE8FAFF, (float) (0.55D * (1.0D - distance / 160.0D)));
				}
			}
			case LangitFxPayload.KIND_GUARDS -> {
				guards = payload.ids().clone();
				guardsTier = Math.max(1, Math.min(4, payload.a()));
				guardsTicks = guards.length == 0 ? 0 : 100;
			}
			case LangitFxPayload.KIND_GUARD_FALL -> LangitParticles.guardFall(minecraft.level, payload.x(), payload.y(), payload.z(), payload.a());
			case LangitFxPayload.KIND_SEAL_OMEN -> {
				float felt = Mth.clamp(payload.a() / 100.0F, 0.0F, 1.0F);
				boolean fresh = sealOmenTarget <= 0.0F;
				sealOmenTarget = Math.max(fresh ? 0.0F : sealOmenTarget * 0.5F, felt);
				sealOmenTicks = SEAL_OMEN_HOLD;
				if (fresh || Math.abs(felt - sealOmenShown) > 0.2F) {
					gradeTimer = 0;
				}
				if (fresh) {
					playAt(SoundEvents.BELL_RESONATE, new Vec3(payload.x(), payload.y(), payload.z()), 0.7F, 0.4F);
				}
			}
			default -> {
			}
		}
	}

	private static void add(Fx fx) {
		if (FX.size() >= MAX_FX) {
			FX.removeFirst();
		}
		FX.add(fx);
	}

	// ================================================================== moments

	/** The event began (or the player arrived while it runs): the card, a distant sound. */
	private static void begun(SkyKind kind) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		Vec3 at = minecraft.player.position();
		switch (kind) {
			case METEOR_SHOWER -> playAt(SoundEvents.AMETHYST_BLOCK_CHIME, at, 1.0F, 0.6F);
			case BLOOD_ECLIPSE -> playAt(SoundEvents.BELL_RESONATE, at, 1.0F, 0.45F);
			case TWIN_MOONS -> playAt(SoundEvents.AMETHYST_BLOCK_RESONATE, at, 1.0F, 1.4F);
		}
		Scene scene = DuniaScenes.world("langit/" + kind.id(), at, skySeed, 100, true);
		if (scene != null) {
			String seal = switch (kind) {
				case METEOR_SHOWER -> "星";
				case BLOOD_ECLIPSE -> "血";
				case TWIN_MOONS -> "月";
			};
			int rgb = switch (kind) {
				case METEOR_SHOWER -> 0x6FA8FF;
				case BLOOD_ECLIPSE -> 0xC0281C;
				case TWIN_MOONS -> 0xB8C8F0;
			};
			DuniaScenes.card(scene, 10, Component.translatable("kultivator.langit.kind." + kind.id()),
					Component.translatable("kultivator.langit.card." + kind.id()), seal, rgb);
		}
	}

	/**
	 * A Meteor Agung was sent: its path (for the sky-projected streak beyond the tracking range), a far rumble from where it
	 * enters the sky, and within 320 blocks the moment's card (no camera move: the player should look up themselves).
	 */
	private static void greatIncoming(Minecraft minecraft, LangitFxPayload payload, int seed) {
		int[] path = payload.ids();
		int fall = path.length >= 4 ? Math.max(10, path[3]) : 140;
		Fx fx = new Fx(LangitFxPayload.KIND_GREAT_INCOMING, payload.x(), payload.y(), payload.z(), payload.a(), fall + 10, seed);
		fx.ox = path.length >= 4 ? path[0] / 16.0F : 40.0F;
		fx.oy = path.length >= 4 ? path[1] / 16.0F : 160.0F;
		fx.oz = path.length >= 4 ? path[2] / 16.0F : 0.0F;
		fx.fall = fall;
		add(fx);
		Vec3 player = minecraft.player.position();
		Vec3 start = new Vec3(payload.x() + fx.ox, payload.y() + fx.oy, payload.z() + fx.oz);
		Vec3 toward = start.subtract(player).normalize();
		playAt(SoundEvents.TRIDENT_THUNDER.value(), player.add(toward.scale(16.0D)), 0.7F, 0.5F);
		playAt(SoundEvents.FIREWORK_ROCKET_LAUNCH, player.add(toward.scale(12.0D)), 0.6F, 0.4F);
		Vec3 landing = new Vec3(payload.x(), payload.y(), payload.z());
		if (player.distanceTo(landing) < 320.0D) {
			Scene scene = DuniaScenes.world("langit/meteor_agung", landing, seed, 120, true);
			DuniaScenes.card(scene, 4, Component.translatable("kultivator.langit.agung.card"),
					Component.translatable("kultivator.langit.agung.card_sub"), "隕", 0x6FD8FF);
		}
	}

	/** A short screen flash (Effect Quality "Kilat" on): a bolide bursting or a Meteor Agung landing near. */
	static void bolide(int rgb, float strength) {
		if (!EffectQuality.flash() || !(strength > flash)) {
			return;
		}
		flash = Mth.clamp(strength, 0.0F, 0.6F);
		flashRgb = rgb;
	}

	/** The local player claimed the treasure: the beat-40 scene (Penuh: a 3 s orbit and the card; Singkat: the card). */
	private static void claimScene(Vec3 at, int tier, int seed) {
		EpicSettings.Camera mode = CinematicDirector.mode();
		if (mode == EpicSettings.Camera.MATI) {
			return;
		}
		Scene scene = DuniaScenes.world("harta", at, seed, 90, true);
		if (scene == null) {
			return;
		}
		if (mode == EpicSettings.Camera.PENUH) {
			DuniaScenes.orbit(scene, 2, 6.5F, 2.8F, 150.0F, 3.0F);
		}
		DuniaScenes.card(scene, 6, Component.translatable("kultivator.langit.harta.card"),
				Component.translatable("kultivator.langit.harta.tier." + Math.max(1, Math.min(4, tier))), "宝", 0xD8A030);
	}

	// ================================================================== the client tick

	static void tick(Minecraft minecraft) {
		ensureLevel();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		// sky weight
		skyWeightO = skyWeight;
		float target = skyState == LangitSkyPayload.ACTIVE ? 1.0F : 0.0F;
		skyWeight += Mth.clamp(target - skyWeight, -1.0F / 100.0F, 1.0F / 200.0F);
		// the mist at the player: a soft 64-block blend across the cell borders
		mistO = mist;
		float wanted = mistAt(minecraft.player.getX(), minecraft.player.getZ());
		boolean outdoors = level.canSeeSky(minecraft.player.blockPosition().above());
		if (!outdoors) {
			wanted *= 0.35F;
		}
		mist += Mth.clamp(wanted - mist, -1.0F / 80.0F, 1.0F / 80.0F);
		weather = kindAt(minecraft.player.getX(), minecraft.player.getZ());
		float dawnWanted = weather == SpiritWeather.PURPLE_DAWN ? 1.0F : 0.0F;
		dawn += Mth.clamp(dawnWanted - dawn, -0.02F, 0.02F);
		// the seal omen: held while the server keeps refreshing it, then fades out over 4 s
		if (sealOmenTicks > 0 && --sealOmenTicks == 0) {
			sealOmenTarget = 0.0F;
			gradeTimer = 0;
		}
		sealOmenO = sealOmen;
		sealOmen += Mth.clamp(sealOmenTarget - sealOmen, -1.0F / 80.0F, 1.0F / 100.0F);
		// births: fade in, collapse, forget the ended ones
		long now = level.getGameTime();
		for (Iterator<BirthView> it = BIRTHS.values().iterator(); it.hasNext(); ) {
			BirthView view = it.next();
			if (view.ending >= 0) {
				if (--view.ending < 0) {
					it.remove();
				}
				continue;
			}
			view.alpha = Math.min(1.0F, view.alpha + 1.0F / 60.0F);
			if (now > view.ends + 200L) {
				it.remove();
			}
		}
		for (Iterator<Fx> it = FX.iterator(); it.hasNext(); ) {
			Fx fx = it.next();
			if (++fx.age >= fx.life) {
				it.remove();
			}
		}
		if (guardsTicks > 0 && --guardsTicks == 0) {
			guards = new int[0];
		}
		if (--tintTimer <= 0) {
			tintTimer = 20;
			mistTarget = biomeTint(level, minecraft.player.blockPosition());
		}
		mistRgb = LangitRenderer.lerpRgb(mistRgb, mistTarget, 0.08F);
		flash = Math.max(0.0F, flash - 0.04F);
		LangitSky.tick(level, minecraft);
		LangitParticles.tick(level, minecraft);
		LangitAmbience.tick(level, minecraft);
		grades(minecraft);
	}

	/** The mist strength of the cells around (x, z), blended over 64 blocks at the cell borders (0 clear .. 1 dense). */
	static float mistAt(double x, double z) {
		if (cellSize <= 0) {
			return 0.0F;
		}
		float total = 0.0F;
		for (int i = 0; i < 4; i++) {
			double ox = (i & 1) == 0 ? -32.0D : 32.0D;
			double oz = (i & 2) == 0 ? -32.0D : 32.0D;
			total += strength(kindAt(x + ox, z + oz));
		}
		return total / 4.0F;
	}

	/** The weather kind at a world column from the synced 3 × 3 view (clear outside it). */
	static SpiritWeather kindAt(double x, double z) {
		if (cellSize <= 0) {
			return SpiritWeather.CLEAR;
		}
		int cx = Math.floorDiv(Mth.floor(x), cellSize) - cellX;
		int cz = Math.floorDiv(Mth.floor(z), cellSize) - cellZ;
		if (cx < -1 || cx > 1 || cz < -1 || cz > 1) {
			return SpiritWeather.CLEAR;
		}
		int ordinal = kinds[(cz + 1) * 3 + cx + 1];
		return ordinal >= 0 && ordinal < SpiritWeather.values().length ? SpiritWeather.values()[ordinal] : SpiritWeather.CLEAR;
	}

	static float strength(SpiritWeather weather) {
		return switch (weather) {
			case THIN_MIST -> 0.5F;
			case DENSE_MIST -> 1.0F;
			case PURPLE_DAWN -> 0.8F;
			case CLEAR -> 0.0F;
		};
	}

	/**
	 * The colour of the Qi mist by the biome under the player (the server's weather is per 512-block cell, its colour is
	 * local): pink in cherry groves, reed green in swamps, blue-white in snow, sand in deserts, gold in Alam Abadi.
	 */
	static int biomeTint(ClientLevel level, BlockPos pos) {
		if (level.dimension().equals(Abadi.IMMORTAL_LEVEL)) {
			return 0xF2E4BC;
		}
		Holder<Biome> biome = level.getBiome(pos);
		if (biome.is(Biomes.CHERRY_GROVE)) {
			return 0xF4D4E4;
		}
		if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
			return 0xC6DABE;
		}
		if (biome.is(Biomes.PALE_GARDEN)) {
			return 0xDCE0E0;
		}
		if (biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.SNOWY_TAIGA) || biome.is(Biomes.SNOWY_SLOPES) || biome.is(Biomes.FROZEN_PEAKS)
				|| biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.ICE_SPIKES) || biome.is(Biomes.GROVE)) {
			return 0xE2EEFA;
		}
		if (biome.is(BiomeTags.IS_JUNGLE)) {
			return 0xC8ECCC;
		}
		if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA)) {
			return 0xEEE2C8;
		}
		if (biome.is(BiomeTags.IS_MOUNTAIN)) {
			return 0xDCE8F2;
		}
		return LangitParticles.MIST_RGB;
	}

	/**
	 * How much of the moon Tiangou has swallowed during Gerhana Darah (0..1): the bite comes over the first 22 % of the
	 * night, the totality holds, and the moon is given back over the last 22 % (a forced test night is shorter, the same
	 * shape).
	 */
	static float eclipseCover(ClientLevel level, float partial) {
		if (skyEnd <= skyStart) {
			return 1.0F;
		}
		float p = (float) ((level.getOverworldClockTime() + partial - skyStart) / (double) (skyEnd - skyStart));
		if (p < 0.22F) {
			return smooth(p / 0.22F);
		}
		if (p > 0.78F) {
			return smooth((1.0F - p) / 0.22F);
		}
		return 1.0F;
	}

	static float smooth(float t) {
		float k = Mth.clamp(t, 0.0F, 1.0F);
		return k * k * (3.0F - 2.0F * k);
	}

	static float skyWeight(float partial) {
		return Mth.lerp(partial, skyWeightO, skyWeight);
	}

	static float mist(float partial) {
		return Mth.lerp(partial, mistO, mist);
	}

	static float sealOmen(float partial) {
		return Mth.lerp(partial, sealOmenO, sealOmen);
	}

	static @Nullable SkyKind kind() {
		return skyKind >= 0 && skyKind < SkyKind.values().length ? SkyKind.values()[skyKind] : null;
	}

	/** The kind the sky shows: the active event, or the one that just ended while it fades out (weight above 0). */
	static @Nullable SkyKind shown() {
		if (skyWeight <= 0.001F) {
			return null;
		}
		int index = skyState == LangitSkyPayload.ACTIVE && skyKind >= 0 ? skyKind : fadeKind;
		return index >= 0 && index < SkyKind.values().length ? SkyKind.values()[index] : null;
	}

	/**
	 * Every 5 s (and on every change): L's sky grade at priority 0. The eclipse wins over the mists, the mists over the
	 * meteor shower; with nothing to show, L's grade is released.
	 */
	private static void grades(Minecraft minecraft) {
		if (--gradeTimer > 0) {
			return;
		}
		gradeTimer = 100;
		Vec3 at = minecraft.gameRenderer.mainCamera().position();
		// the full intensities: SkyDirector blends over the 5 s itself
		SkyKind kind = skyState == LangitSkyPayload.ACTIVE ? kind() : null;
		float mistNow = mist;
		int choice;
		sealOmenShown = sealOmenTarget;
		if (kind == SkyKind.BLOOD_ECLIPSE) {
			choice = 1;
			// a weak seal nearby deepens the eclipse a little
			SkyDirector.grade(SkyDirector.Grade.SK_RED, 0.35F + 0.1F * sealOmenTarget, 0.25F + 0.15F * sealOmenTarget, 100, SKY_PRIORITY, at,
					true, 1.0F, at.y);
		} else if (sealOmenTarget > 0.05F) {
			// the ash-red omen over a weakening prison (R's break moment, at a higher priority, wins while it plays)
			choice = 6;
			SkyDirector.grade(SkyDirector.Grade.SK_ASH_RED, 0.15F + 0.3F * sealOmenTarget, 0.1F + 0.3F * sealOmenTarget, 100, SKY_PRIORITY, at,
					true, 1.0F, at.y);
		} else if (dawn > 0.3F && mistNow > 0.05F) {
			choice = 2;
			SkyDirector.dao(0xC8A0F0, 0.3F * Math.min(1.0F, mistNow * 1.2F), 100, SKY_PRIORITY, at, true, 1.0F);
		} else if (mistNow > 0.05F && daylight() > 0.2F) {
			// a white mist tint only by day (at night it would light the sky); the night mist is the planes and the HUD
			choice = 3;
			SkyDirector.grade(SkyDirector.Grade.SK_WHITE, 0.5F * mistNow * daylight(), 0.0F, 100, SKY_PRIORITY, at, true, 1.0F, at.y);
		} else if (kind == SkyKind.METEOR_SHOWER) {
			choice = 4;
			SkyDirector.grade(SkyDirector.Grade.SK_STARS, 0.35F, 0.2F, 100, SKY_PRIORITY, at, true, 1.0F, at.y);
		} else if (kind == SkyKind.TWIN_MOONS) {
			choice = 5;
			SkyDirector.grade(SkyDirector.Grade.SK_ICE, 0.12F, 0.0F, 100, SKY_PRIORITY, at, true, 1.0F, at.y);
		} else {
			choice = -1;
		}
		if (choice < 0 && gradeShown >= 0) {
			SkyDirector.release(80, SKY_PRIORITY);
		}
		gradeShown = choice;
	}

	/** 1 in full daylight .. 0 at night (the level's sky darkening). */
	static float daylight() {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? 1.0F : Mth.clamp(1.0F - level.getSkyDarken() / 11.0F, 0.0F, 1.0F);
	}

	static void playAt(SoundEvent sound, Vec3 at, float volume, float pitch) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, SoundInstance.createUnseededRandom(),
				at.x, at.y, at.z));
	}

	/** True when Effect Quality allows the full look (Normal and above). */
	static boolean rich() {
		return !EffectQuality.minimal();
	}

	static void clear() {
		if (gradeShown >= 0) {
			SkyDirector.release(20, SKY_PRIORITY);
		}
		gradeShown = -1;
		skyKind = -1;
		fadeKind = -1;
		skyState = LangitSkyPayload.NONE;
		skyWeight = 0.0F;
		skyWeightO = 0.0F;
		cellSize = 0;
		kinds = new byte[9];
		mist = 0.0F;
		mistO = 0.0F;
		dawn = 0.0F;
		weather = SpiritWeather.CLEAR;
		BIRTHS.clear();
		FX.clear();
		marks = new int[0];
		guards = new int[0];
		guardsTicks = 0;
		mistRgb = LangitParticles.MIST_RGB;
		mistTarget = LangitParticles.MIST_RGB;
		tintTimer = 0;
		flash = 0.0F;
		sealOmenTarget = 0.0F;
		sealOmenTicks = 0;
		sealOmen = 0.0F;
		sealOmenO = 0.0F;
		sealOmenShown = 0.0F;
		LangitSky.clearStreaks();
		LangitParticles.clear();
		LangitAmbience.clear();
	}

	private LangitState() {
	}
}
