package dev.kultivator.client.sectbuild;

import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.client.Keys;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;

/**
 * Client side of Pembangunan Sekte (DESIGN 21.12, 21.16, 21.21): keeps the last footprint preview (for the hologram, the
 * HUD and the template screen), the last status push (the open component's wireframe, the work mist, the HUD's "Masih
 * kurang" line) and the template catalog, all three arriving as a {@code BuildPreview} (status and catalog carry a
 * {@code kind} key, a footprint preview has none, an empty tag clears the preview). Opens the template screen, sends the
 * {@code Actions.SECT_BUILD} actions, draws the hologram every client tick and reports a low frame rate (the FPS hint).
 */
public final class BuildClient {

	/** A preview's hologram lasts at most this long on the client (ms), even when the server never clears it. */
	public static final long PREVIEW_MILLIS = 120_000L;
	/** A status push older than this is treated as gone (ms): the player walked away from the site. */
	public static final long STATUS_MILLIS = 6_000L;
	/** The FPS hint: below this frame rate for {@link #LOW_FPS_MILLIS} the build rate halves (DESIGN 21.15). */
	public static final int LOW_FPS = 30;
	public static final long LOW_FPS_MILLIS = 5_000L;

	private static CompoundTag preview = new CompoundTag();
	private static long previewVersion;
	private static long previewAt;
	private static CompoundTag status = new CompoundTag();
	private static long statusAt;
	private static CompoundTag catalog = new CompoundTag();
	private static long catalogVersion;
	/** The diagram scrubber's tier (1-9) shared by the hologram and the template screen. */
	private static int scrubTier = 9;
	private static long lowSince = -1L;
	private static boolean lowSent;

	/** Called once from {@code KultivatorClient}. */
	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("sect_build_hud"), new BuildHud());
		ClientTickEvents.END_CLIENT_TICK.register(BuildClient::tick);
	}

	/** A {@code BuildPreview} arrived: a footprint preview, a status push, the catalog, or an empty tag (clear). */
	public static void onPreview(Payloads.BuildPreview payload) {
		CompoundTag tag = payload.data() == null ? new CompoundTag() : payload.data();
		String kind = tag.getStringOr("kind", "");
		switch (kind) {
			case "status" -> {
				status = tag;
				statusAt = Util.getMillis();
			}
			case "catalog" -> {
				catalog = tag;
				catalogVersion++;
			}
			default -> {
				preview = tag;
				previewAt = Util.getMillis();
				previewVersion++;
			}
		}
	}

	/** The server asks for the template screen; {@code arg} = the template to select first (0 = none). */
	public static void openScreen(int arg) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (minecraft.gui.screen() instanceof TemplateScreen screen) {
			screen.select(arg);
			return;
		}
		minecraft.gui.setScreen(new TemplateScreen(arg, false, minecraft.gui.screen()));
	}

	/** Opens the template screen from the Bangun tablet ({@code upgrade}: only larger templates, "Naik Templat"). */
	public static void openTemplates(int select, boolean upgrade) {
		Minecraft minecraft = Minecraft.getInstance();
		send(0, "catalog");
		minecraft.gui.setScreen(new TemplateScreen(select, upgrade, minecraft.gui.screen()));
	}

	/** The last preview tag (never null, empty when none or expired). */
	public static CompoundTag preview() {
		if (!preview.isEmpty() && Util.getMillis() - previewAt > PREVIEW_MILLIS) {
			preview = new CompoundTag();
			previewVersion++;
		}
		return preview;
	}

	/** True while a footprint preview is shown. */
	public static boolean previewActive() {
		CompoundTag tag = preview();
		return !tag.isEmpty() && tag.contains("fp");
	}

	/** Milliseconds of the preview's hologram still left. */
	public static long previewLeftMillis() {
		long serverLeft = preview.getIntOr("left", (int) (PREVIEW_MILLIS / 50L)) * 50L;
		return Math.max(0L, Math.min(PREVIEW_MILLIS, serverLeft) - (Util.getMillis() - previewAt));
	}

	/** Grows with every preview. */
	public static long previewVersion() {
		return previewVersion;
	}

	/** The last status push while it is fresh, else an empty tag. */
	public static CompoundTag status() {
		return Util.getMillis() - statusAt > STATUS_MILLIS ? new CompoundTag() : status;
	}

	public static CompoundTag catalog() {
		return catalog;
	}

	public static long catalogVersion() {
		return catalogVersion;
	}

	public static int scrubTier() {
		return scrubTier;
	}

	public static void setScrubTier(int tier) {
		scrubTier = Math.max(1, Math.min(9, tier));
	}

	/** One warning of a preview ({@code key} with its {@code args}) as text. */
	public static net.minecraft.network.chat.Component warning(CompoundTag warning) {
		String key = warning.getStringOr("key", "");
		java.util.List<Object> args = new java.util.ArrayList<>();
		net.minecraft.nbt.Tag raw = warning.get("args");
		if (raw instanceof net.minecraft.nbt.ListTag list) {
			for (net.minecraft.nbt.Tag arg : list) {
				args.add(arg.asString().orElse(arg.toString()));
			}
		} else if (raw != null) {
			args.add(raw.asString().orElse(raw.toString()));
		}
		return key.isEmpty() ? net.minecraft.network.chat.Component.literal("?") : net.minecraft.network.chat.Component.translatable(key, args.toArray());
	}

	/** Sends a construction action ({@code arg} = sub-op, {@code text} = "op[:param]"). */
	public static void send(int op, String text) {
		Keys.send(new Payloads.Action(Actions.SECT_BUILD, op, text == null ? "" : text));
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		SiteHologram.tick(minecraft);
		fpsHint(minecraft);
	}

	/** Reports once when the frame rate stayed below 30 for 5 s near the own site, and once when it recovered. */
	private static void fpsHint(Minecraft minecraft) {
		if (!KultivatorConfig.get().sectBuildFpsHint || status().isEmpty() || !status.getBooleanOr("own", false)) {
			if (lowSent) {
				lowSent = false;
				send(0, "fps:0");
			}
			lowSince = -1L;
			return;
		}
		long now = Util.getMillis();
		boolean low = minecraft.getFps() < LOW_FPS;
		if (low) {
			if (lowSince < 0L) {
				lowSince = now;
			}
			if (!lowSent && now - lowSince >= LOW_FPS_MILLIS) {
				lowSent = true;
				send(0, "fps:1");
			}
		} else {
			lowSince = -1L;
			if (lowSent) {
				lowSent = false;
				send(0, "fps:0");
			}
		}
	}

	private static void clear() {
		preview = new CompoundTag();
		previewVersion = 0L;
		status = new CompoundTag();
		statusAt = 0L;
		catalog = new CompoundTag();
		lowSince = -1L;
		lowSent = false;
	}

	private BuildClient() {
	}
}
