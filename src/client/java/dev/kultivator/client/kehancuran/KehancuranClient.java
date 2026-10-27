package dev.kultivator.client.kehancuran;

import dev.kultivator.Kultivator;
import dev.kultivator.client.Keys;
import dev.kultivator.client.codex.Codex;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of Mode Kehancuran (DESIGN 20.3.21, module D; 43.4.4): the server's caps for the heavy confirm (a blood-red
 * {@code SegelKonfirmasi} on Teknik › Kehancuran, was {@code KehancuranConfirmScreen}), the red sky of a dimension-wide
 * countdown (a HUD tint, {@link RedSkyHud}, plus ash from {@link KehancuranFx}), the shape preview kept for the confirm window, and the details of the disciple whose red
 * seal is being released (stashed by the Jalan tab). {@link #init()} is called once from {@code KultivatorClient} and also
 * registers the Teknik pages ({@link Codex#daftarHalaman}: {@code CodexClient} belongs to the HUD wave);
 * {@link #openConfirm(int)} runs when the server sends {@code OpenScreen.KEHANCURAN_CONFIRM}.
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

	/** A heavy confirm waiting for the Kehancuran page ({@link #ambilKonfirmasi}). */
	public record Konfirmasi(SegelKonfirmasi.Tingkat tingkat, Component teks, Runnable ya) {
	}

	private static @Nullable Konfirmasi pending;
	private static long pendingSince;

	// ---- the disciple being released (from the Jalan tab)
	private static int stashedId = -1;
	private static CompoundTag stashedPath = new CompoundTag();
	private static Component stashedName = Component.empty();

	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(KehancuranFx::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Kultivator.id("kehancuran_sky"), new RedSkyHud());
		Codex.daftarHalaman();
	}

	/**
	 * The heavy confirm (43.4.4): {@code arg} 0 = the player's own oath (5 s hold), otherwise the disciple's entity id (3 s).
	 * The Gulungan moves to Teknik › Kehancuran, whose page shows a blood-red {@code SegelKonfirmasi} listing exactly what
	 * will happen; "Ya" sends the confirming action. A release asked from another page returns there afterwards.
	 */
	public static void openConfirm(int arg) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		CompoundTag path = new CompoundTag();
		Component name = Component.empty();
		if (arg != 0) {
			if (stashedId == arg) {
				path = stashedPath;
				name = stashedName;
			} else if (minecraft.level != null && minecraft.level.getEntity(arg) != null) {
				name = minecraft.level.getEntity(arg).getName();
			}
		}
		Tujuan back = arg != 0 && minecraft.gui.screen() instanceof CultivationScreen hub ? hub.tempat() : null;
		long hold = (arg == 0 ? Kehancuran.OATH_HOLD_TICKS : Kehancuran.RELEASE_HOLD_TICKS) * 50L;
		// BERAT_LAMA holds 5 s, BERAT 3 s: the same holds as the server's oath / release (Kehancuran.*_HOLD_TICKS).
		SegelKonfirmasi.Tingkat tingkat = hold >= 5000L ? SegelKonfirmasi.Tingkat.BERAT_LAMA : SegelKonfirmasi.Tingkat.BERAT;
		int target = arg;
		pending = new Konfirmasi(tingkat, confirmText(arg, name, path), () -> {
			Keys.send(new Payloads.Action(Actions.KEHANCURAN, target, target == 0 ? "oath_confirm" : "release_confirm"));
			if (back != null && !Urutan.ID_KEHANCURAN.equals(back.halaman())) {
				HubNav.buka(back);
			}
		});
		pendingSince = Util.getMillis();
		HubNav.buka(Tujuan.of(HubNav.TEKNIK, Urutan.ID_KEHANCURAN).sub(arg == 0 ? "diri" : "murid"));
	}

	/** The waiting confirm, once (null when none or older than the server's 15 s window). */
	public static @Nullable Konfirmasi ambilKonfirmasi() {
		Konfirmasi k = pending;
		pending = null;
		return k != null && Util.getMillis() - pendingSince <= PREVIEW_MILLIS ? k : null;
	}

	/** What will happen, most important first (the card cuts the tail on small screens). */
	private static Component confirmText(int target, Component name, CompoundTag path) {
		String p = "kultivator.kehancuran.ui.";
		java.util.List<Component> lines = new java.util.ArrayList<>();
		if (target == 0) {
			lines.add(Component.translatable(p + "oath_sub"));
			lines.add(Component.translatable(p + "shape_any", maxRadius));
		} else {
			lines.add(Component.translatable(p + "release_sub", name));
			String kind = path.getStringOr("art_kind", "");
			String art = path.getStringOr("art", "");
			Component artName = art.isEmpty() ? Component.literal("?") : Component.translatable("kultivator.dpath.art." + art);
			if ("trench".equals(kind)) {
				lines.add(Component.translatable(p + "shape_trench", artName, path.getIntOr("art_length", 0), 3, path.getIntOr("art_depth", 0)));
			} else if (!kind.isEmpty()) {
				lines.add(Component.translatable(p + "shape", artName, Component.translatable("kultivator.kehancuran.shape." + kind),
						path.getIntOr("art_radius", 0)));
			} else {
				lines.add(Component.translatable(p + "shape_any", maxRadius));
			}
		}
		lines.add(Component.translatable(p + "blocks", maxBlocks, perTick));
		lines.add(dropsPct <= 0 ? Component.translatable(p + "no_drops") : Component.translatable(p + "drops", dropsPct));
		lines.add(dropsPct <= 0 && undoMinutes > 0 ? Component.translatable(p + "restore", undoMinutes) : Component.translatable(p + "no_restore"));
		lines.add(Component.translatable(p + "zones", hallSafe, playerSafe));
		lines.add(Component.translatable(p + "never"));
		lines.add(Component.translatable(p + "order", warningSeconds));
		lines.add(Component.translatable(p + "close"));
		net.minecraft.network.chat.MutableComponent all = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				all.append("\n");
			}
			all.append(lines.get(i));
		}
		return all;
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
		pending = null;
	}

	private KehancuranClient() {
	}
}
