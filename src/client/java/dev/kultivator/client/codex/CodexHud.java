package dev.kultivator.client.codex;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.QiHud;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.hud.Kabar;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The seal parts of the HUD (DESIGN 22.12.8, 43.9; the orb, Perisai Qi, lasting arcs and vital lines are {@code QiHud}):
 * the Roda Segel, the four quick seal tiles, the selected seal tile (glyph, grade ribbon, cooldown sweep, mastery beads,
 * qi cost, charge pips, the red Kehancuran frame; G32: the only seal tile), the charge bar under the crosshair ("Tekan
 * lagi = lepas" from 50%), the emptying channel ring (flashing when it is about to break), stage pips with the window
 * timer, the ultimate lockout ("Pamungkas 12s") and the 1.2 s carousel of the previous and next seals. Seal notices
 * ("Jurus baru", "Pencerahan Jurus", mastery level-ups with a chime, "Gerbang Dewa terbuka") go to {@link Kabar}.
 * <p>
 * {@link #cincin} is the one painter of a progress ring around the crosshair (G30): the channel ring here, the formation
 * trip ({@code AlarmHud}), the capital's teleport ({@code KotaBesarHud}), the dragon contest ({@code NagaHud}) and the
 * sky-treasure claim ({@code LangitHud}) all call it. F1 hides all of it.
 */
public final class CodexHud implements HudElement {

	private static final long TOAST_MILLIS = 3500L;
	private static final long CAROUSEL_MILLIS = 1200L;
	private static final long HINT_MILLIS = 1600L;
	private static final long KILAT_MILLIS = 700L;
	/** Inner radius of the crosshair ring; the band is {@link #CINCIN_TEBAL} thick. */
	public static final float CINCIN_R = 11.0F;
	public static final float CINCIN_TEBAL = 2.5F;

	private static @Nullable TechniqueSpec carouselPrevious;
	private static @Nullable TechniqueSpec carouselCurrent;
	private static @Nullable TechniqueSpec carouselNext;
	private static long carouselAt;
	private static @Nullable Component hint;
	private static long hintAt;
	private static long kilatSampai;

	private static long seenVersion = -1L;
	private static @Nullable Set<String> known;
	private static final Map<String, Integer> LEVELS = new HashMap<>();
	private static boolean gateOpen;

	// ================================================================== events

	/** A seal notice with a title line and the art's glyph. */
	public static void toast(Component text, @Nullable TechniqueSpec spec) {
		toast(Component.translatable("kultivator.codex.toast.title"), text, spec);
	}

	public static void toast(Component title, Component text, @Nullable TechniqueSpec spec) {
		Kabar.kirim(Kabar.kartu(title, text).ikon(spec != null ? specIkon(spec) : Kabar.glyph(Glyphs.CODEX)).lama(TOAST_MILLIS));
	}

	private static Kabar.Ikon specIkon(TechniqueSpec spec) {
		return (g, cx, cy) -> {
			Brush.rect(g, cx - 8.0F, cy - 8.0F, cx + 8.0F, cy + 8.0F, Palette.SEAL);
			Brush.frame(g, Math.round(cx - 7.0F), Math.round(cy - 7.0F), Math.round(cx + 7.0F), Math.round(cy + 7.0F), Palette.argb(0xB0, Palette.TALISMAN));
			Glyphs.spec(g, spec, cx, cy, 11.0F, Palette.TALISMAN);
		};
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

	/** The cooldown ping (DESIGN 19.3.15): a short gold flash over the selected seal tile ({@code QiHud} rings it). */
	public static void kilatUbin() {
		kilatSampai = Util.getMillis() + KILAT_MILLIS;
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
		known = null;
		LEVELS.clear();
		seenVersion = -1L;
		carouselCurrent = null;
		hint = null;
		kilatSampai = 0L;
	}

	// ================================================================== the crosshair ring

	/** The crosshair ring at the standard radius: a dark track, {@code isi} (0..1) filled clockwise in {@code rgb}, an optional label. */
	public static void cincin(GuiGraphicsExtractor g, float cx, float cy, float isi, int rgb, @Nullable Component label) {
		cincin(g, cx, cy, CINCIN_R, isi, rgb, label);
	}

	/** The same ring at radius {@code r} (the dragon contest draws its arcs on a wide one). */
	public static void cincin(GuiGraphicsExtractor g, float cx, float cy, float r, float isi, int rgb, @Nullable Component label) {
		int color = (rgb >>> 24) == 0 ? Palette.opaque(rgb) : rgb;
		Brush.arc(g, cx, cy, r, r + CINCIN_TEBAL, 0.0F, 1.0F, Palette.argb(0x60, 0x101418));
		float share = Math.max(0.0F, Math.min(1.0F, isi));
		if (share > 0.0F) {
			Brush.arc(g, cx, cy, r, r + CINCIN_TEBAL, 0.0F, share, color);
		}
		if (label != null) {
			Brush.centered(g, Minecraft.getInstance().font, label, cx, cy + r + CINCIN_TEBAL + 3.0F, color, 1.0F, true);
		}
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
		if (!chat && !EpicDirector.recordHide()) {
			sealTile(g, font, minecraft, ClientCultivation.data(), width, height, partial);
		}
		if (!SealWheel.isOpen()) {
			flows(g, font, width / 2.0F, height / 2.0F);
		}
		long lockout = TeknikSession.lockoutLeft();
		if (lockout > 0L && !chat) {
			Component text = Component.translatable("kultivator.codex.hud.lockout", (lockout + 19L) / 20L);
			Brush.centered(g, font, text, width / 2.0F, height - 62.0F, 0xFFFF9A6A, 1.0F, true);
		}
		if (carouselCurrent != null && now - carouselAt < CAROUSEL_MILLIS && !chat) {
			carousel(g, font, width / 2.0F, height - 84.0F, now);
		}
		if (hint != null && now - hintAt < HINT_MILLIS) {
			float alpha = Math.min(1.0F, (HINT_MILLIS - (now - hintAt)) / 300.0F);
			Brush.centered(g, font, hint, width / 2.0F, height - 72.0F, Palette.fade(0xFFF7DC7E, alpha), 1.0F, true);
		}
	}

	/** The selected seal tile at x 40, under the quick seals, where {@code QiHud} keeps room for it. */
	private static void sealTile(GuiGraphicsExtractor g, Font font, Minecraft minecraft, CultivationData data, int width, int height, float partial) {
		int limit = QiHud.limit(minecraft, width);
		if (!QiHud.sealTile(data, limit)) {
			return;
		}
		int x = 40;
		int y = height - 33;
		TechniqueSpec spec = data.selectedId().isEmpty() ? null : TechniqueRegistry.byId(data.selectedId());
		if (spec != null) {
			drawSeal(g, font, data, spec, x, y, partial);
		} else {
			// Mode Kehancuran on with no seal chosen: the red seal alone
			kehancuranFrame(g, x, y, 18);
			Brush.rect(g, x, y, x + 18, y + 18, Palette.SEAL_DARK);
			Brush.centered(g, font, Component.literal("毁"), x + 9.5F, y + 5.0F, 0xFFFFE0D0, 1.0F, false);
		}
	}

	/**
	 * The selected spec as a seal tile (DESIGN 22.12.8): glyph, grade ribbon, cooldown sweep and seconds, four mastery
	 * beads, the qi cost after mastery and the realm gap under it, charge pips above it, and a pulsing red frame while Mode
	 * Kehancuran is on.
	 */
	private static void drawSeal(GuiGraphicsExtractor g, Font font, CultivationData data, TechniqueSpec spec, int x, int y, float partialTick) {
		int size = 18;
		int qi = SpecInfo.qi(data, spec);
		boolean affordable = data.qi() + 1.0E-6D >= qi;
		if (TeknikSession.breakerOn()) {
			kehancuranFrame(g, x, y, size);
		}
		g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xA0101824);
		int paper = affordable ? Palette.TALISMAN : Palette.grey(Palette.TALISMAN, 0.6F);
		g.fill(x, y, x + size, y + size, paper);
		long left = SpecInfo.cooldownLeft(spec);
		int total = SpecInfo.cooldown(data, spec);
		if (left > 0L && total > 0) {
			float fraction = Math.min(1.0F, Math.max(0.0F, (left - partialTick) / total));
			g.enableScissor(x, y, x + size, y + size);
			Brush.pie(g, x + size / 2.0F, y + size / 2.0F, size * 0.75F, 1.0F - fraction, 1.0F, Palette.mix(paper, 0xFF1A2230, 0.6F));
			g.disableScissor();
		}
		int seal = affordable ? Palette.SEAL : 0xFF6A6A6A;
		Brush.frame(g, x, y, x + size, y + size, seal);
		Brush.frame(g, x + 1, y + 1, x + size - 1, y + size - 1, Palette.argb(0x70, seal));
		int ribbon = SpecInfo.ribbon(spec.grade());
		for (int i = 0; i < 5; i++) {
			g.fill(x + size - 5 + i, y + i, x + size, y + i + 1, ribbon);
		}
		int ink = SpecInfo.ink(spec);
		Glyphs.spec(g, spec, x + size / 2.0F, y + size / 2.0F - 1.0F, 10.0F, left > 0L ? Palette.fade(ink, 0.55F) : ink);
		int mastery = data.knows(spec.id()) ? SpecInfo.mastery(data, spec) : 0;
		for (int i = 0; i < 4; i++) {
			Brush.dot(g, x + size / 2.0F + (i - 1.5F) * 2.6F, y + size - 2.2F, 0.7F, i < mastery ? Palette.GOLD : Palette.argb(0x60, 0x3A2D12));
		}
		if (left > 0L) {
			Component secs = Component.literal(String.valueOf((left + 19L) / 20L));
			Brush.centered(g, font, secs, x + size / 2.0F, y + size / 2.0F - 4.0F, 0xFFFFFFFF, 1.0F, true);
		}
		long flash = kilatSampai - Util.getMillis();
		if (flash > 0L) {
			int alpha = Math.round(160 * flash / (float) KILAT_MILLIS);
			g.fill(x - 1, y - 1, x + size + 1, y + size + 1, Palette.argb(alpha, 0xFFE07A));
		}
		Component cost = Component.literal(SpecInfo.compact(qi));
		Brush.centered(g, font, cost, x + size / 2.0F, y + size + 2.0F, affordable ? Palette.QI_LIGHT : 0xFFFF7A6A, 1.0F, true);
		CompoundTag charges = TeknikSession.charges(spec.id());
		if (charges != null) {
			int max = Math.max(1, Math.min(8, charges.getIntOr("max", 1)));
			int have = Math.max(0, Math.min(max, charges.getIntOr("n", max)));
			for (int i = 0; i < max; i++) {
				Brush.dot(g, x + size / 2.0F + (i - (max - 1) / 2.0F) * 3.0F, y - 2.5F, 0.9F, i < have ? Palette.QI_LIGHT : Palette.argb(0x70, 0x3A4A5A));
			}
		}
	}

	/** Mode Kehancuran on and allowed: a pulsing red double frame around the tile. */
	private static void kehancuranFrame(GuiGraphicsExtractor g, int x, int y, int size) {
		float pulse = 0.6F + 0.4F * (float) Math.sin(Util.getMillis() / 250.0D);
		int red = Palette.mix(0xFFB3281D, 0xFFFF5B3A, pulse);
		Brush.frame(g, x - 3, y - 3, x + size + 3, y + size + 3, red);
		Brush.frame(g, x - 2, y - 2, x + size + 2, y + size + 2, Palette.argb(Math.round(0x90 * pulse), 0xFF5B3A));
	}

	/** Charge bar, channel ring and stage pips around the crosshair. */
	private static void flows(GuiGraphicsExtractor g, Font font, float cx, float cy) {
		long time = ClientCultivation.serverGameTime();
		TeknikSession.Flow channel = TeknikSession.channel();
		if (channel != null) {
			float left = Math.max(0.0F, Math.min(1.0F, (channel.end() - time) / (float) Math.max(1L, channel.end() - channel.start())));
			boolean flash = channel.warn() && Util.getMillis() / 150L % 2L == 0L;
			cincin(g, cx, cy, left, flash ? 0xFFFF4A3A : Palette.QI_LIGHT, null);
		}
		// under the ring: the charge bar, then the stage pips
		float below = cy + CINCIN_R + CINCIN_TEBAL + 3.0F;
		TeknikSession.Flow charge = TeknikSession.charge();
		if (charge != null) {
			float share = Math.max(0.0F, Math.min(1.0F, (time - charge.start()) / (float) Math.max(1L, charge.end() - charge.start())));
			float bw = 40.0F;
			Brush.rect(g, cx - bw / 2.0F - 1.0F, below, cx + bw / 2.0F + 1.0F, below + 4.0F, 0xA0101824);
			Brush.rect(g, cx - bw / 2.0F, below + 1.0F, cx - bw / 2.0F + bw * share, below + 3.0F, share >= 0.5F ? Palette.GOLD_LIGHT : Palette.GOLD);
			if (share >= 0.5F) {
				Brush.centered(g, font, Component.translatable("kultivator.codex.hud.release"), cx, below + 6.0F, Palette.GOLD_LIGHT, 1.0F, true);
			}
			below += 16.0F;
		}
		TeknikSession.Flow stages = TeknikSession.stages();
		if (stages != null && stages.count() > 1) {
			float left = Math.max(0.0F, Math.min(1.0F, (stages.end() - time) / (float) Math.max(1L, stages.end() - Math.max(stages.start(), time - 600L))));
			float total = stages.count() * 6.0F;
			for (int i = 0; i < stages.count(); i++) {
				float px = cx - total / 2.0F + i * 6.0F + 3.0F;
				Brush.dot(g, px, below + 2.0F, 1.6F, i < stages.stage() ? Palette.GOLD_LIGHT : Palette.argb(0x80, Palette.GOLD_DARK));
			}
			Brush.rect(g, cx - total / 2.0F, below + 5.5F, cx - total / 2.0F + total * left, below + 6.5F, Palette.GOLD);
		}
	}

	private static void carousel(GuiGraphicsExtractor g, Font font, float cx, float y, long now) {
		float alpha = Math.min(1.0F, (CAROUSEL_MILLIS - (now - carouselAt)) / 250.0F);
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
			Brush.centered(g, font, CodexNames.name(carouselCurrent), cx, y + 10.0F, Palette.fade(0xFFF7DC7E, alpha), 1.0F, true);
		}
	}
}
