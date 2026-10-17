package dev.kultivator.client.kehancuran;

import dev.kultivator.Kultivator;
import dev.kultivator.client.disciple.DiscipleScreen;
import dev.kultivator.client.hall.HallScreen;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of Mode Kehancuran (DESIGN 20.3.21, module D): the server's caps for the blood-red warning screen
 * ({@link KehancuranConfirmScreen}), the red sky of a dimension-wide countdown (a HUD tint, {@link RedSkyHud}, plus ash
 * from {@link KehancuranFx}), the shape preview kept for the confirm window, and the details of the disciple whose red
 * seal is being released (stashed by the Jalan tab). {@link #init()} is called once from {@code KultivatorClient};
 * {@link #openConfirm(int)} when the server sends {@code OpenScreen.KEHANCURAN_CONFIRM}.
 */
public final class KehancuranClient {

	/** How long a shape preview stays drawn (the server's confirm window is 15 s). */
	static final long PREVIEW_MILLIS = 15_000L;

	// ---- the server's caps (KehancuranFxIds.CONFIG; defaults until the first packet)
	static int maxRadius = 12;
	static int maxBlocks = 4096;
	static int dropsPct;
	static int hallSafe = 64;
	static int playerSafe = 24;
	static int undoMinutes = 10;
	static int warningSeconds = 30;
	static int perTick = 48;

	// ---- the red sky
	private static long skyUntil;
	private static long skyStarted;
	private static Vec3 skyCentre = Vec3.ZERO;

	// ---- the shape preview
	static long previewUntil;
	static int previewKind;
	static float previewSize;
	static Vec3 previewPos = Vec3.ZERO;
	static Vec3 previewTarget = Vec3.ZERO;

	// ---- the disciple being released (from the Jalan tab)
	private static int stashedId = -1;
	private static CompoundTag stashedPath = new CompoundTag();
	private static Component stashedName = Component.empty();

	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(KehancuranFx::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Kultivator.id("kehancuran_sky"), new RedSkyHud());
	}

	/** Opens the warning: {@code arg} 0 = the player's own oath, otherwise the disciple's entity id. */
	public static void openConfirm(int arg) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		Screen current = minecraft.gui.screen();
		if (current instanceof KehancuranConfirmScreen) {
			return;
		}
		CompoundTag path = new CompoundTag();
		Component name = Component.empty();
		if (arg != 0) {
			if (current instanceof DiscipleScreen disciple && disciple.entityId() == arg) {
				path = disciple.info().getCompoundOrEmpty("path");
				name = disciple.entity() != null ? disciple.entity().getName() : Component.literal(disciple.info().getStringOr("name", "?"));
			} else if (stashedId == arg) {
				path = stashedPath;
				name = stashedName;
			} else if (minecraft.level != null && minecraft.level.getEntity(arg) != null) {
				name = minecraft.level.getEntity(arg).getName();
			}
		}
		Screen back = current instanceof DiscipleScreen || current instanceof HallScreen ? current : null;
		minecraft.gui.setScreen(new KehancuranConfirmScreen(arg, name, path, back));
	}

	/** The Jalan tab remembers the disciple before asking to release its seal (the warning lists its art). */
	public static void remember(int entityId, CompoundTag path, Component name) {
		stashedId = entityId;
		stashedPath = path.copy();
		stashedName = name;
	}

	/** The caps packet: pos = (radius, blocks, drops), target = (hall, player, undo), b = warning s, f = per tick. */
	static void onConfig(Payloads.Fx fx) {
		maxRadius = (int) Math.round(fx.x());
		maxBlocks = (int) Math.round(fx.y());
		dropsPct = (int) Math.round(fx.z());
		hallSafe = (int) Math.round(fx.tx());
		playerSafe = (int) Math.round(fx.ty());
		undoMinutes = (int) Math.round(fx.tz());
		warningSeconds = fx.b();
		perTick = Math.round(fx.f());
	}

	/** The red sky starts ({@code seconds} > 0) or stops (0). */
	static void sky(Vec3 centre, int seconds) {
		if (seconds <= 0) {
			skyUntil = 0L;
			return;
		}
		long now = Util.getMillis();
		skyStarted = now;
		skyUntil = now + seconds * 1000L;
		skyCentre = centre;
	}

	/** True while a countdown's red sky is up. */
	static boolean skyActive() {
		return Util.getMillis() < skyUntil;
	}

	/** Seconds left of the countdown (0 when none). */
	static int skySecondsLeft() {
		long left = skyUntil - Util.getMillis();
		return left <= 0L ? 0 : (int) ((left + 999L) / 1000L);
	}

	/** 0..1: how far the countdown is (the tint deepens towards zero). */
	static float skyProgress() {
		long total = Math.max(1L, skyUntil - skyStarted);
		return Math.max(0.0F, Math.min(1.0F, (Util.getMillis() - skyStarted) / (float) total));
	}

	static Vec3 skyCentre() {
		return skyCentre;
	}

	static void preview(Vec3 pos, Vec3 target, int kind, float size) {
		previewPos = pos;
		previewTarget = target;
		previewKind = kind;
		previewSize = size;
		previewUntil = Util.getMillis() + PREVIEW_MILLIS;
	}

	static @Nullable Component stashedName(int entityId) {
		return stashedId == entityId ? stashedName : null;
	}

	private static void clear() {
		skyUntil = 0L;
		previewUntil = 0L;
		stashedId = -1;
		stashedPath = new CompoundTag();
		stashedName = Component.empty();
	}

	private KehancuranClient() {
	}
}
