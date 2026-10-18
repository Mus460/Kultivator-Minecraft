package dev.kultivator.client.codex;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.technique.spec.dsl.Route;
import dev.kultivator.technique.spec.run.Mastery;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The codex parts of the HUD (DESIGN 22.12.8; the seal tile, Perisai Qi, lasting arcs and the KEHANCURAN tag are drawn by
 * {@code QiHud}): the Roda Segel, the four quick seal tiles, the charge bar under the crosshair ("Tekan lagi = lepas"
 * from 50%), the emptying channel ring (flashing when it is about to break), stage pips with the window timer, the
 * ultimate lockout ("Pamungkas 12s"), the 1.2 s carousel of the previous and next seals, and jade seal toasts ("Jurus
 * baru", "Pencerahan Jurus", mastery level-ups with a chime, "Gerbang Dewa terbuka"). F1 hides all of it.
 */
public final class CodexHud implements HudElement {

	private static final long TOAST_MILLIS = 3500L;
	private static final long CAROUSEL_MILLIS = 1200L;
	private static final long HINT_MILLIS = 1600L;

	private record Toast(Component title, Component text, @Nullable TechniqueSpec spec, long[] start) {
	}

	private static final Deque<Toast> TOASTS = new ArrayDeque<>();
	private static @Nullable TechniqueSpec carouselPrevious;
	private static @Nullable TechniqueSpec carouselCurrent;
	private static @Nullable TechniqueSpec carouselNext;
	private static long carouselAt;
	private static @Nullable Component hint;
	private static long hintAt;

	private static long seenVersion = -1L;
	private static @Nullable Set<String> known;
	private static final Map<String, Integer> LEVELS = new HashMap<>();
	private static boolean gateOpen;

	// ================================================================== events

	/** A jade toast with a title line and the art's glyph. */
	public static void toast(Component text, @Nullable TechniqueSpec spec) {
		toast(Component.translatable("kultivator.codex.toast.title"), text, spec);
	}

	public static void toast(Component title, Component text, @Nullable TechniqueSpec spec) {
		if (TOASTS.size() >= 5) {
			TOASTS.pollFirst();
		}
		TOASTS.addLast(new Toast(title, text, spec, new long[]{0L}));
	}

	/** A short line above the hotbar (an empty quick seal...). */
	public static void hint(Component text) {
		hint = text;
		hintAt = Util.getMillis();
	}

	/** Shows the previous, current and next seal for 1.2 s after cycling. */
	public static void carousel(@Nullable TechniqueSpec previous, TechniqueSpec current, @Nullable TechniqueSpec next) {
		carouselPrevious = previous;
		carouselCurrent = current;
		carouselNext = next;
		carouselAt = Util.getMillis();
	}

	/** Compares the new snapshot with the last one: newly learned arts, mastery level-ups, the Dewa gate. */
	static void tick() {
		if (!ClientCultivation.received()) {
			return;
		}
		long version = ClientCultivation.version();
		if (version == seenVersion) {
			return;
		}
		seenVersion = version;
		CultivationData data = ClientCultivation.data();
		Set<String> now = new HashSet<>(data.learnedIds());
		boolean gate = dewaGateOpen(data);
		if (known == null) {
			known = now;
			LEVELS.clear();
			for (TechniqueSpec spec : data.learnedSpecs()) {
				LEVELS.put(spec.id(), Mastery.level(data, spec.id()));
			}
			gateOpen = gate;
			return;
		}
		for (String id : now) {
			if (known.contains(id)) {
				continue;
			}
			TechniqueSpec spec = TechniqueRegistry.byId(id);
			if (spec == null || !spec.playerVisible()) {
				continue;
			}
			boolean automatic = false;
			for (Route route : spec.routes()) {
				automatic |= route.automatic();
			}
			toast(Component.translatable(automatic ? "kultivator.codex.toast.enlightened" : "kultivator.codex.toast.new"), spec.displayName(), spec);
		}
		for (TechniqueSpec spec : data.learnedSpecs()) {
			int level = Mastery.level(data, spec.id());
			Integer before = LEVELS.put(spec.id(), level);
			if (before != null && level > before) {
				toast(Component.translatable("kultivator.codex.toast.mastery"), Component.translatable("kultivator.codex.toast.mastery_text",
						spec.displayName(), SpecInfo.masteryName(level)), spec);
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3F, 0.7F));
			}
		}
		if (gate && !gateOpen) {
			toast(Component.translatable("kultivator.codex.toast.gate"), Component.translatable("kultivator.codex.toast.gate_text"), null);
			UiSound.open();
		}
		gateOpen = gate;
		known = now;
	}

	private static boolean dewaGateOpen(CultivationData data) {
		for (TechniqueSpec spec : TechniqueRegistry.all()) {
			if (spec.gate() == dev.kultivator.data.TechniqueSpec.Gate.DEWA_I && spec.playerVisible()) {
				return SpecInfo.gateOk(data, spec);
			}
		}
		return false;
	}

	/** Forgets everything (disconnect). */
	static void reset() {
		TOASTS.clear();
		known = null;
		LEVELS.clear();
		seenVersion = -1L;
		carouselCurrent = null;
		hint = null;
	}

	// ================================================================== drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		Font font = minecraft.font;
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		SealWheel.render(g, font, partial);
		if (minecraft.getDebugOverlay().showDebugScreen() || !EffectQuality.hudVisible() || !ClientCultivation.received()) {
			return;
		}
		int width = g.guiWidth();
		int height = g.guiHeight();
		long now = Util.getMillis();
		boolean chat = minecraft.gui.screen() instanceof ChatScreen;
		if (!chat && QuickSeals.shown()) {
			QuickSeals.draw(g, font, 40, height - 47);
		}
		if (!SealWheel.isOpen()) {
			flows(g, font, width / 2.0F, height / 2.0F);
		}
		long lockout = TeknikSession.lockoutLeft();
		if (lockout > 0L && !chat) {
			Component text = Component.translatable("kultivator.codex.hud.lockout", (lockout + 19L) / 20L);
			Brush.centered(g, font, text, width / 2.0F, height - 62.0F, 0xFFFF9A6A, 0.7F, true);
		}
		if (carouselCurrent != null && now - carouselAt < CAROUSEL_MILLIS && !chat) {
			carousel(g, font, width / 2.0F, height - 78.0F, now);
		}
		if (hint != null && now - hintAt < HINT_MILLIS) {
			float alpha = Math.min(1.0F, (HINT_MILLIS - (now - hintAt)) / 300.0F);
			Brush.centered(g, font, hint, width / 2.0F, height - 70.0F, Palette.fade(0xFFF7DC7E, alpha), 0.7F, true);
		}
		toasts(g, font, width, now);
	}

	/** Charge bar, channel ring and stage pips around the crosshair. */
	private static void flows(GuiGraphicsExtractor g, Font font, float cx, float cy) {
		long time = ClientCultivation.serverGameTime();
		TeknikSession.Flow charge = TeknikSession.charge();
		if (charge != null) {
			float share = Math.max(0.0F, Math.min(1.0F, (time - charge.start()) / (float) Math.max(1L, charge.end() - charge.start())));
			float bw = 40.0F;
			Brush.rect(g, cx - bw / 2.0F - 1.0F, cy + 9.0F, cx + bw / 2.0F + 1.0F, cy + 13.0F, 0xA0101824);
			Brush.rect(g, cx - bw / 2.0F, cy + 10.0F, cx - bw / 2.0F + bw * share, cy + 12.0F, share >= 0.5F ? Palette.GOLD_LIGHT : Palette.GOLD);
			if (share >= 0.5F) {
				Brush.centered(g, font, Component.translatable("kultivator.codex.hud.release"), cx, cy + 15.0F, Palette.GOLD_LIGHT, 0.55F, true);
			}
		}
		TeknikSession.Flow channel = TeknikSession.channel();
		if (channel != null) {
			float left = Math.max(0.0F, Math.min(1.0F, (channel.end() - time) / (float) Math.max(1L, channel.end() - channel.start())));
			boolean flash = channel.warn() && Util.getMillis() / 150L % 2L == 0L;
			Brush.arc(g, cx, cy, 8.0F, 9.5F, 0.0F, left, flash ? 0xFFFF4A3A : Palette.QI_LIGHT);
		}
		TeknikSession.Flow stages = TeknikSession.stages();
		if (stages != null && stages.count() > 1) {
			float left = Math.max(0.0F, Math.min(1.0F, (stages.end() - time) / (float) Math.max(1L, stages.end() - Math.max(stages.start(), time - 600L))));
			float total = stages.count() * 6.0F;
			for (int i = 0; i < stages.count(); i++) {
				float px = cx - total / 2.0F + i * 6.0F + 3.0F;
				Brush.dot(g, px, cy + 20.0F, 1.6F, i < stages.stage() ? Palette.GOLD_LIGHT : Palette.argb(0x80, Palette.GOLD_DARK));
			}
			Brush.rect(g, cx - total / 2.0F, cy + 23.5F, cx - total / 2.0F + total * left, cy + 24.5F, Palette.GOLD);
		}
	}

	private static void carousel(GuiGraphicsExtractor g, Font font, float cx, float y, long now) {
		float alpha = Math.min(1.0F, (CAROUSEL_MILLIS - (now - carouselAt)) / 250.0F);
		CultivationData data = ClientCultivation.data();
		TechniqueSpec[] specs = {carouselPrevious, carouselCurrent, carouselNext};
		for (int i = 0; i < 3; i++) {
			TechniqueSpec spec = specs[i];
			if (spec == null) {
				continue;
			}
			boolean main = i == 1;
			float size = main ? 16.0F : 12.0F;
			float x = cx + (i - 1) * 22.0F;
			float fade = alpha * (main ? 1.0F : 0.6F);
			int x0 = Math.round(x - size / 2.0F);
			int y0 = Math.round(y - size / 2.0F);
			g.fill(x0 - 1, y0 - 1, x0 + Math.round(size) + 1, y0 + Math.round(size) + 1, Palette.argb(Math.round(0xA0 * fade), 0x101824));
			g.fill(x0, y0, x0 + Math.round(size), y0 + Math.round(size), Palette.fade(Palette.TALISMAN, fade));
			Glyphs.spec(g, spec, x, y, size * 0.6F, Palette.fade(SpecInfo.ink(spec), fade));
		}
		if (carouselCurrent != null) {
			Brush.centered(g, font, carouselCurrent.displayName(), cx, y + 10.0F, Palette.fade(0xFFF7DC7E, alpha), 0.6F, true);
		}
	}

	private static void toasts(GuiGraphicsExtractor g, Font font, int width, long now) {
		if (TOASTS.isEmpty()) {
			return;
		}
		List<Toast> shown = new ArrayList<>();
		int index = 0;
		for (Toast toast : TOASTS) {
			if (index++ >= 3) {
				break;
			}
			if (toast.start()[0] == 0L) {
				toast.start()[0] = now;
			}
			shown.add(toast);
		}
		float y = 6.0F;
		for (Toast toast : shown) {
			long age = now - toast.start()[0];
			float slide = Math.min(1.0F, age / 200.0F) * Math.min(1.0F, (TOAST_MILLIS - age) / 250.0F);
			float w = 150.0F;
			float x = width - (w + 6.0F) * Math.max(0.0F, slide);
			int x0 = Math.round(x);
			int y0 = Math.round(y);
			g.fill(x0 + 2, y0 + 2, x0 + Math.round(w) + 2, y0 + 26, 0x50000000);
			g.fill(x0, y0, x0 + Math.round(w), y0 + 24, 0xF2112A23);
			Brush.frame(g, x0, y0, x0 + Math.round(w), y0 + 24, Palette.GOLD);
			g.fill(x0 + 3, y0 + 3, x0 + 21, y0 + 21, Palette.SEAL);
			Brush.frame(g, x0 + 4, y0 + 4, x0 + 20, y0 + 20, Palette.argb(0xB0, Palette.TALISMAN));
			if (toast.spec() != null) {
				Glyphs.spec(g, toast.spec(), x0 + 12.0F, y0 + 12.0F, 11.0F, Palette.TALISMAN);
			} else {
				Glyphs.tab(g, Glyphs.CODEX, x0 + 12.0F, y0 + 12.0F, 11.0F, Palette.TALISMAN);
			}
			Brush.fit(g, font, toast.title(), x0 + 25.0F, y0 + 4.0F, w - 29.0F, Palette.GOLD_LIGHT, 0.6F, 0.45F, false);
			Brush.fit(g, font, toast.text(), x0 + 25.0F, y0 + 13.0F, w - 29.0F, Palette.JADE_PALE, 0.65F, 0.45F, false);
			y += 27.0F;
		}
		while (!TOASTS.isEmpty() && TOASTS.peekFirst().start()[0] != 0L && now - TOASTS.peekFirst().start()[0] >= TOAST_MILLIS) {
			TOASTS.pollFirst();
		}
	}
}
