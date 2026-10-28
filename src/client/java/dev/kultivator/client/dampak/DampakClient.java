package dev.kultivator.client.dampak;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.dampak.cinema.SilentImpact;
import dev.kultivator.client.dampak.cinema.SkyRide;
import dev.kultivator.client.dampak.cinema.TargetMark;
import dev.kultivator.client.dampak.world.DampakWorld;
import dev.kultivator.client.epic.EpicClock;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.ShotGuard;
import dev.kultivator.client.epic.screen.CloudWipe;
import dev.kultivator.client.epic.screen.ImpactFrame;
import dev.kultivator.client.fx.spec.ScreenFlash;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.network.Payloads;
import dev.kultivator.technique.spec.dsl.Dampak;
import dev.kultivator.technique.spec.dsl.Phase;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Client entry of Phase 10 Dampak Langit (DESIGN 28, module M2 "client cinema"): the TechFx hub, the
 * {@code DampakDebris} receiver (handed to {@link DampakWorld}, M3), the HUD layers (the crosshair seal, the cloud wipe
 * and the impact frames, before the chat), the photosensitivity warning hook of the settings page, a small tick
 * scheduler and {@code /sinematik dampak}. Order on the client (28.14): {@code SpecFx.play} → {@code EpicDirector.onTechFx}
 * → {@link #onTechFx} (the silent boom first, then marks, frames, rides and the hit-stop, then {@code DampakWorld}) → the
 * existing layer runs. Presentation never breaks the game: every entry catches its own errors. Client thread only.
 */
public final class DampakClient {

	private static final String K = "kultivator.dampak.cmd.";

	private static final List<Pending> PENDING = new ArrayList<>();
	private static boolean hudRegistered;
	private static @Nullable ClientLevel lastLevel;
	private static long ticks;
	private static int warnings;

	private record Pending(long due, Runnable action) {
	}

	private DampakClient() {
	}

	/** Called once from {@code KultivatorClient.onInitializeClient}, right after {@code EpicClient.init()}. */
	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(Payloads.DampakDebris.TYPE, (payload, context) -> {
			try {
				DampakWorld.onDebris(payload);
			} catch (RuntimeException | LinkageError exception) {
				warn("debris", exception);
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(DampakClient::tick);
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> registerHud());
		ClientDisconnect.register((handler, client) -> client.execute(() -> clearAll()));
		// the photosensitivity warning is a seal on Pengaturan › Kamera & Film (43.10.1), hooked by SettingsPage.daftar
		try {
			DampakWorld.init();
		} catch (RuntimeException | LinkageError exception) {
			warn("world init", exception);
		}
	}

	private static void registerHud() {
		if (hudRegistered) {
			return;
		}
		hudRegistered = true;
		// in this order before the chat: the seal under the wipe under the frames
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("dampak_seal"), new TargetMark.Hud());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("dampak_wipe"), new CloudWipe());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("dampak_frame"), new ImpactFrame());
	}

	// ================================================================ the TechFx hub

	/** Called by {@code SpecFx.play} for every technique event, right after the 7b director. Never throws. */
	public static void onTechFx(Payloads.TechFx payload) {
		try {
			techFx(payload);
		} catch (RuntimeException | LinkageError exception) {
			warn("techFx", exception);
		}
	}

	private static void techFx(Payloads.TechFx p) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		TechniqueSpec spec = TechniqueRegistry.byNet(p.spec());
		if (spec == null) {
			return;
		}
		Dampak d = spec.dampak();
		if (d == null) {
			return;
		}
		Phase phase = Phase.byOrdinal(p.phase());
		Entity caster = minecraft.level.getEntity(p.caster());
		boolean own = caster == minecraft.player;
		boolean disciple = p.has(Payloads.TechFx.FLAG_DISCIPLE);
		boolean echo = p.has(Payloads.TechFx.FLAG_ECHO);
		boolean climax = p.has(Payloads.TechFx.FLAG_CLIMAX) && phase != Phase.PULSE && phase != Phase.MASS_HIT;
		// 1. the delayed boom, on every client and before any VFX gate (the server skipped these cues)
		if (climax && d.silent()) {
			SilentImpact.onClimax(p, spec, d, phase, own);
		}
		float others = own ? 1.0F : EffectQuality.othersScale();
		// 2. Tanda Langit
		TargetMark.onTechFx(p, spec, d, phase, own, others);
		// 3. the impact frames (before the ride's exit, which cuts under them), the hit-stop
		if (climax && !echo) {
			frames(p, spec, d, own, disciple, others);
			if (own && d.frames()) {
				EpicClock.hitStop(Dampak.hitStop(dewa(spec)));
			}
		}
		// 4. Perjalanan Langit: the local player's own casts only, never a disciple's camera
		if (own && !disciple && !echo) {
			boolean hasTo = p.tx() != 0.0D || p.ty() != 0.0D || p.tz() != 0.0D;
			SkyRide.onTechFx(p, spec, d, phase, hasTo ? new Vec3(p.tx(), p.ty(), p.tz()) : new Vec3(p.x(), p.y(), p.z()));
		}
		// 5. the world (M3): debris, lift cells, sigils, monuments, rings
		try {
			DampakWorld.onTechFx(p, spec, phase, own, others);
		} catch (RuntimeException | LinkageError exception) {
			warn("world techFx", exception);
		}
		// 6. Phase 10b (29.10 C10b.6): a Titah decree's own world pieces (falling stars, the cleaving line, the lifted cells)
		if (spec.has(TechniqueSpec.Flag.TITAH)) {
			try {
				dev.kultivator.client.pusakalangit.world.TitahWorld.onTechFx(p, spec, phase, own, others);
			} catch (RuntimeException | LinkageError exception) {
				warn("titah world techFx", exception);
			}
		}
	}

	private static boolean dewa(TechniqueSpec spec) {
		return spec.dewa() || spec.grade() == TechniqueGrade.DEWA;
	}

	/**
	 * The impact frames of a climax (28.4.1): one's own casts always; others only within 32 b and in front of the camera
	 * (view dot > 0.3), always Lembut at half alpha; never for a disciple's cast.
	 */
	private static void frames(Payloads.TechFx p, TechniqueSpec spec, Dampak d, boolean own, boolean disciple, float others) {
		if (!d.frames()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 impact = new Vec3(p.x(), p.y(), p.z());
		if (!own) {
			if (disciple || others <= 0.0F) {
				return;
			}
			Vec3 camera = minecraft.gameRenderer.mainCamera().position();
			Vec3 to = impact.subtract(camera);
			double distance = to.length();
			if (distance > 32.0D) {
				return;
			}
			if (distance > 2.0D && minecraft.player != null && minecraft.player.getViewVector(1.0F).dot(to.scale(1.0D / distance)) <= 0.3D) {
				return;
			}
		}
		int second = p.has(Payloads.TechFx.FLAG_TWO_DAO) ? p.aux() & 0xFFFFFF : -1;
		playFrames(spec, d, own, p.seed(), second);
	}

	/** Resolves the row's frame colours and plays them under the player's "Bingkai dampak" choice. */
	private static boolean playFrames(TechniqueSpec spec, Dampak d, boolean own, int seed, int secondDao) {
		EpicSettings.ImpactFrames mode = EpicSettings.impactFrames();
		if (mode == EpicSettings.ImpactFrames.MATI || !EffectQuality.flash()) {
			return false;
		}
		boolean reduce = EffectQuality.reduceMotion();
		boolean full = own && mode == EpicSettings.ImpactFrames.PENUH && !reduce;
		int dao = spec.dao() != null ? spec.dao().color() & 0xFFFFFF : 0xFFD54A;
		int f1 = resolve(d.frame1(), dao, dao);
		int f2 = resolve(d.frame2(), dao, secondDao >= 0 ? secondDao : dao);
		int f3 = resolve(d.frame3(), dao, dao);
		if (!ImpactFrame.play(f1, f2, f3, full, own ? 1.0F : 0.5F, !reduce, seed)) {
			return false;
		}
		// one luminance event: the spec's own climax flash layer stays quiet, the vanilla sky flashes with it
		ScreenFlash.suppress(2);
		Minecraft minecraft = Minecraft.getInstance();
		if (own && minecraft.level != null) {
			minecraft.level.setSkyFlashTime(2);
		}
		return true;
	}

	private static int resolve(int frame, int dao, int daoHere) {
		return frame == Dampak.DAO ? daoHere : frame;
	}

	/** The climax of a test ride or {@code bingkai}: frames, the boom and the hit-stop, as the real climax would. */
	public static void testClimax(TechniqueSpec spec, Dampak d, Vec3 at, boolean own) {
		try {
			playFrames(spec, d, own, (int) (at.x * 31.0D + at.z), -1);
			SilentImpact.play(spec.sounds(spec.climaxPhase()), at, d.silence(), (int) at.x * 31 + (int) at.z, own);
			EpicClock.hitStop(Dampak.hitStop(dewa(spec)));
		} catch (RuntimeException | LinkageError exception) {
			warn("test climax", exception);
		}
	}

	// ================================================================ tick and scheduling

	/** Runs {@code action} {@code delay} client ticks from now (the sub-bass of a delayed boom, a screen after a command). */
	public static void later(int delay, Runnable action) {
		if (PENDING.size() < 64) {
			PENDING.add(new Pending(ticks + Math.max(0, delay), action));
		}
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		if (minecraft.level != lastLevel) {
			lastLevel = minecraft.level;
			clearCinema();
			return;
		}
		if (!PENDING.isEmpty()) {
			List<Pending> due = new ArrayList<>();
			for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
				Pending pending = it.next();
				if (pending.due() <= ticks) {
					it.remove();
					due.add(pending);
				}
			}
			for (Pending pending : due) {
				try {
					pending.action().run();
				} catch (RuntimeException exception) {
					warn("later", exception);
				}
			}
		}
		if (minecraft.level == null) {
			return;
		}
		try {
			TargetMark.tick(minecraft);
		} catch (RuntimeException exception) {
			warn("tick", exception);
		}
	}

	/** Ends the marks, rides, wipes and frames (dimension change, disconnect). */
	private static void clearCinema() {
		PENDING.clear();
		TargetMark.clear();
		SkyRide.clear();
		CloudWipe.clear();
		ImpactFrame.clear();
	}

	private static void clearAll() {
		clearCinema();
		try {
			DampakWorld.clear();
		} catch (RuntimeException | LinkageError exception) {
			warn("world clear", exception);
		}
		try {
			dev.kultivator.client.pusakalangit.world.TitahWorld.clear();
		} catch (RuntimeException | LinkageError exception) {
			warn("titah world clear", exception);
		}
	}

	/** Logs a presentation error at most 8 times per session (shared with the Titah client, Phase 10b). */
	public static void warn(String where, Throwable exception) {
		if (warnings++ < 8) {
			Kultivator.LOGGER.warn("Kultivator: Dampak Langit error in {}", where, exception);
		}
	}

	// ================================================================ /sinematik dampak (28.15)

	/** The {@code dampak} branch of {@code /sinematik} (added by {@code EpicClientCommands}). */
	public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("dampak");
		root.then(ClientCommands.literal("naik").then(ClientCommands.argument("id", StringArgumentType.word()).suggests(DampakClient::suggestIds)
				.executes(DampakClient::naik)));
		root.then(ClientCommands.literal("bingkai").then(ClientCommands.argument("id", StringArgumentType.word()).suggests(DampakClient::suggestIds)
				.executes(DampakClient::bingkai)));
		root.then(ClientCommands.literal("tanda").then(ClientCommands.argument("id", StringArgumentType.word()).suggests(DampakClient::suggestIds)
				.executes(DampakClient::tanda)));
		root.then(ClientCommands.literal("sunyi").then(ClientCommands.argument("jarak", IntegerArgumentType.integer(0, 96)).executes(context -> {
			int distance = IntegerArgumentType.getInteger(context, "jarak");
			int delay = SilentImpact.test(distance);
			return ok(context, Component.translatable(K + "silent", distance, delay));
		})));
		root.then(ClientCommands.literal("monumen").then(ClientCommands.argument("model", StringArgumentType.word())
				.executes(context -> monumen(context, 60))
				.then(ClientCommands.argument("detik", IntegerArgumentType.integer(5, 180))
						.executes(context -> monumen(context, IntegerArgumentType.getInteger(context, "detik"))))));
		root.then(ClientCommands.literal("cincin").then(ClientCommands.argument("radius", IntegerArgumentType.integer(1, 48)).executes(context -> {
			int radius = IntegerArgumentType.getInteger(context, "radius");
			DampakWorld.test("cincin", ahead(12.0D), radius, null);
			return ok(context, Component.translatable(K + "ring", radius));
		})));
		root.then(ClientCommands.literal("info").executes(DampakClient::info));
		LiteralArgumentBuilder<FabricClientCommandSource> skyride = ClientCommands.literal("skyride");
		for (EpicSettings.SkyRide mode : EpicSettings.SkyRide.values()) {
			skyride.then(ClientCommands.literal(mode.id()).executes(context -> {
				EpicSettings.setSkyRide(mode);
				return ok(context, Component.translatable("kultivator.dampak.setting.skyride",
						Component.translatable("kultivator.dampak.setting.skyride." + mode.id())));
			}));
		}
		root.then(skyride);
		LiteralArgumentBuilder<FabricClientCommandSource> frames = ClientCommands.literal("frames");
		for (EpicSettings.ImpactFrames mode : EpicSettings.ImpactFrames.values()) {
			frames.then(ClientCommands.literal(mode.id()).executes(context -> {
				if (mode == EpicSettings.ImpactFrames.PENUH && !EpicSettings.impactFramesConfirmed()) {
					// the warning first: the seal on Pengaturan › Kamera & Film, opened once the chat is closed
					dev.kultivator.client.screen.SettingsPage.peringatanDampak();
					return ok(context, Component.translatable(K + "frames_warning"));
				}
				EpicSettings.setImpactFrames(mode);
				return ok(context, Component.translatable("kultivator.dampak.setting.frames",
						Component.translatable("kultivator.dampak.setting.frames." + mode.id())));
			}));
		}
		root.then(frames);
		root.then(toggle("silent", EpicSettings.SILENT_IMPACT));
		root.then(toggle("worldfx", EpicSettings.WORLD_FX));
		LiteralArgumentBuilder<FabricClientCommandSource> power = ClientCommands.literal("power");
		for (int percent : new int[]{50, 100, 150}) {
			power.then(ClientCommands.literal(String.valueOf(percent)).executes(context -> {
				EpicSettings.setImpactPower(percent / 100.0F);
				return ok(context, Component.translatable("kultivator.dampak.setting.power", percent + "%"));
			}));
		}
		root.then(power);
		return root;
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, String key) {
		return ClientCommands.literal(name)
				.then(ClientCommands.literal("nyala").executes(context -> {
					EpicSettings.setFlag(key, true);
					return ok(context, Component.translatable(K + name + ".on"));
				}))
				.then(ClientCommands.literal("mati").executes(context -> {
					EpicSettings.setFlag(key, false);
					return ok(context, Component.translatable(K + name + ".off"));
				}));
	}

	private static CompletableFuture<Suggestions> suggestIds(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
		List<String> ids = new ArrayList<>();
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.dampak() != null) {
				ids.add(spec.id());
			}
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	}

	private static @Nullable TechniqueSpec spec(CommandContext<FabricClientCommandSource> context) {
		String id = StringArgumentType.getString(context, "id").toLowerCase(Locale.ROOT);
		TechniqueSpec spec = TechniqueRegistry.byId(id);
		if (spec == null || spec.dampak() == null) {
			context.getSource().sendError(Component.translatable(K + "unknown", id));
			return null;
		}
		return spec;
	}

	private static int naik(CommandContext<FabricClientCommandSource> context) {
		TechniqueSpec spec = spec(context);
		if (spec == null) {
			return 0;
		}
		String refused = SkyRide.test(spec, spec.dampak(), ahead(16.0D));
		if (refused != null) {
			context.getSource().sendError(Component.translatable(K + "ride_refused", refused, ShotGuard.reason().isEmpty() ? "-" : ShotGuard.reason()));
			return 0;
		}
		return ok(context, Component.translatable(K + "ride", spec.displayName()));
	}

	private static int bingkai(CommandContext<FabricClientCommandSource> context) {
		TechniqueSpec spec = spec(context);
		if (spec == null) {
			return 0;
		}
		Dampak d = spec.dampak();
		boolean shown = playFrames(spec, d, true, (int) (Math.random() * 1024.0D), -1);
		return ok(context, Component.translatable(shown ? K + "frames" : K + "frames_none", spec.displayName()));
	}

	private static int tanda(CommandContext<FabricClientCommandSource> context) {
		TechniqueSpec spec = spec(context);
		if (spec == null) {
			return 0;
		}
		TargetMark.test(spec, spec.dampak(), ahead(12.0D), 80);
		return ok(context, Component.translatable(K + "mark", spec.displayName()));
	}

	private static int monumen(CommandContext<FabricClientCommandSource> context, int seconds) {
		String model = StringArgumentType.getString(context, "model").toLowerCase(Locale.ROOT);
		DampakWorld.test("monumen", ahead(10.0D), seconds, model);
		return ok(context, Component.translatable(K + "monument", model, seconds));
	}

	private static int info(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		source.sendFeedback(Component.translatable(K + "info.title").withStyle(ChatFormatting.GOLD));
		source.sendFeedback(Component.translatable(K + "info.settings",
				Component.translatable("kultivator.dampak.setting.skyride." + EpicSettings.skyRide().id()),
				Component.translatable("kultivator.dampak.setting.frames." + EpicSettings.impactFrames().id()),
				Component.translatable(EpicSettings.silentImpact() ? K + "on" : K + "off"),
				Component.translatable(EpicSettings.worldFx() ? K + "on" : K + "off"),
				Math.round(EpicSettings.impactPower() * 100.0F) + "%").withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.translatable(K + "info.gates",
				Component.translatable("kultivator.momen.setting.camera." + CinematicDirector.mode().id()),
				ShotGuard.reason().isEmpty() ? "-" : ShotGuard.reason(),
				Component.translatable(EpicSettings.impactFramesConfirmed() ? K + "on" : K + "off")).withStyle(ChatFormatting.GRAY));
		String world;
		try {
			world = DampakWorld.stats();
		} catch (RuntimeException | LinkageError exception) {
			world = "-";
		}
		source.sendFeedback(Component.translatable(K + "info.world", world).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	/** A point on the ground about {@code distance} blocks along the player's look (a clip, else the end snapped down). */
	private static Vec3 ahead(double distance) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return Vec3.ZERO;
		}
		Vec3 eye = minecraft.player.getEyePosition();
		Vec3 end = eye.add(minecraft.player.getViewVector(1.0F).scale(distance));
		BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
		return TargetMark.ground(hit.getType() == HitResult.Type.MISS ? end : hit.getLocation());
	}

	private static int ok(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendFeedback(message.copy().withStyle(ChatFormatting.GREEN));
		return 1;
	}
}
