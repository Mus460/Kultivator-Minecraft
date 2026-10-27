package dev.kultivator.client.duel.ui;

import dev.kultivator.Kultivator;
import dev.kultivator.client.duel.DuelBus;
import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.duel.DuelClock;
import dev.kultivator.client.duel.DuelInput;
import dev.kultivator.client.duel.DuelKeys;
import dev.kultivator.client.epic.EpicDirector;
import dev.kultivator.client.epic.screen.Letterbox;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The duel HUD (DESIGN 35.9 module U, contract C35.U1), one Fabric HUD element attached before the chat:
 * <ul>
 * <li><b>Posture bars</b> in the manner of Sekiro: the player's own bar centred above the hearts (moved up past extra
 * heart rows), the opponent's bar under the top of the screen with name, realm and title, a thin health line with the
 * 10% mark of {@code DuelRules}. A bar grows from its centre notch outward, amber while calm and crimson past 70%, the
 * newly added posture glints white for a moment, and a broken posture flashes and stamps the 決 seal beside it. With
 * "Bar postur di atas kepala" the other fighters' bars float over their heads instead (projected from the camera).
 * Spectators see both duellists' bars side by side.</li>
 * <li>The <b>Blade Lock</b> meter: a vermilion-indigo track where the taiji disc slides with the pressure (the player is
 * always the left side), the ticks left, and a ring that pulses while the opponent pushes: the moment for the sudden
 * release.</li>
 * <li>The <b>exchange chain</b> 一 … 十, the <b>countdown</b> 三 二 一 and 始, the <b>ring-out</b> warning with its 3-2-1, the
 * HUD forms of 危 (a perilous attack winding up, with what answers it) and 決 (deathblow ready), the heavy-charge arc,
 * the surrender ring, the spam-deflect hint, the Opening flash, short outcome words ("Tangkisan Sempurna"), the time
 * left with the rules, the walk to the start mark, the offer toast ({@link ChallengeOfferToast}) and the result card
 * ({@link DuelResultCard}).</li>
 * </ul>
 * Hidden with F1, while a film's letterbox is in and in Mode Rekam; fills and three small textures only (no shader).
 */
public final class DuelHud implements HudElement, DuelBus.Listener {

	static final Identifier FRAME = Kultivator.id("textures/gui/duel/posture_frame.png");
	static final Identifier FILL = Kultivator.id("textures/gui/duel/posture_fill.png");
	static final Identifier BIND = Kultivator.id("textures/gui/duel/bind_meter.png");
	static final Identifier SEALS = Kultivator.id("textures/gui/duel/seal_marks.png");

	private static final int BAR_W = 128;
	private static final int BAR_H = 12;
	/** Half the drawable width of the fill (the caps take the rest). */
	private static final int FILL_HALF = 58;
	private static final String[] NUMERALS = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
	private static final int AMBER = 0xFFD27A;
	private static final int CRIMSON = 0xE0302A;
	private static final int DANGER = 0xE01818;
	private static final int GOLD = 0xD4A637;
	private static final int INK_RED = 0xC0302A;

	private static final DuelHud LISTENER = new DuelHud();

	/** The animated state of one fighter's bar. */
	private static final class Bar {
		float target;
		float shown;
		float before;
		/** The posture just added: drawn white from {@code freshFrom} to the target while {@code fresh} > 0. */
		float freshFrom;
		int fresh;
		int flash;
		int state;
		long seenAt;
	}

	/** A short outcome word ("Tangkis", "Tangkisan Sempurna", ...). */
	private record Popup(Component text, int rgb, long born, int life, float scale, int swingId, int attackerId) {
	}

	private static final Map<Integer, Bar> BARS = new HashMap<>();
	private static final List<Popup> POPUPS = new ArrayList<>();
	private static long ticks;
	private static int phaseShown = -1;
	private static long fightStartedAt = Long.MIN_VALUE / 2;
	private static long outSince = -1L;
	private static int chainShown;
	private static long chainAt = Long.MIN_VALUE / 2;
	private static long celahAt = Long.MIN_VALUE / 2;
	private static boolean celahBefore;
	private static int recentOpponent = -1;
	private static long recentOpponentAt = Long.MIN_VALUE / 2;
	private static boolean monsterNear;

	/** Registers the bus listener (the posture glints, corrections, results). */
	public static void init() {
		DuelBus.register(LISTENER);
	}

	public static void clear() {
		BARS.clear();
		POPUPS.clear();
		phaseShown = -1;
		outSince = -1L;
		chainShown = 0;
		recentOpponent = -1;
		monsterNear = false;
	}

	// ================================================================== events

	@Override
	public void fighter(DuelPayloads.DuelFighterSync p) {
		Bar bar = BARS.computeIfAbsent(p.entityId(), id -> new Bar());
		float max = Math.max(1.0F, p.postureMaxX10() / 10.0F);
		float value = Mth.clamp(p.postureX10() / 10.0F / max, 0.0F, 1.0F);
		if (value > bar.target + 0.005F) {
			bar.freshFrom = bar.fresh > 0 ? Math.min(bar.freshFrom, bar.target) : bar.target;
			bar.fresh = 10;
		}
		if (p.state() == DuelIds.ST_STAGGER && bar.state != DuelIds.ST_STAGGER) {
			bar.flash = 18;
		}
		bar.state = p.state();
		bar.target = value;
		bar.seenAt = ticks;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && p.entityId() == player.getId()) {
			boolean celah = (p.flags() & DuelIds.FF_CELAH) != 0;
			if (celah && !celahBefore) {
				celahAt = ticks;
			}
			celahBefore = celah;
		}
	}

	@Override
	public void correction(int swingId, int outcome) {
		// The defender's guess was wrong: its word fades out at once (the true result brings its own).
		POPUPS.removeIf(popup -> popup.swingId() == swingId && popup.swingId() != Integer.MIN_VALUE);
	}

	/**
	 * Called by {@code DuelClient} for every result after the bus; {@code predicted} for the defender's own guess of
	 * 35.8.2 (its word is withdrawn by a {@link #correction}).
	 */
	public static void onClash(DuelPayloads.DuelClash p, boolean predicted) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int self = player.getId();
		boolean mine = p.aId() == self || p.bId() == self;
		if (mine) {
			recentOpponent = p.aId() == self ? p.bId() : p.aId();
			recentOpponentAt = ticks;
		}
		boolean watched = !mine && watchedHas(p.aId(), p.bId());
		if (!mine && !watched) {
			return;
		}
		if ((p.flags() & DuelIds.CF_PREDICTED_MATCH) != 0) {
			// already shown by the prediction
			return;
		}
		String key;
		int rgb;
		float scale;
		switch (p.outcome()) {
			case DuelIds.OUT_PERFECT -> {
				key = "perfect";
				rgb = 0xFFD86A;
				scale = 1.35F;
			}
			case DuelIds.OUT_DEFLECT -> {
				key = "deflect";
				rgb = 0xE8F0FF;
				scale = 0.95F;
			}
			case DuelIds.OUT_CLASH, DuelIds.OUT_CLASH_MENANG -> {
				key = "clash";
				rgb = 0xFFE7B0;
				scale = 1.0F;
			}
			case DuelIds.OUT_BIND -> {
				key = "bind";
				rgb = 0xFF9A50;
				scale = 1.15F;
			}
			case DuelIds.OUT_BIND_MENANG_A, DuelIds.OUT_BIND_MENANG_B -> {
				boolean won = p.outcome() == DuelIds.OUT_BIND_MENANG_A ? p.aId() == self : p.bId() == self;
				key = mine ? (won ? "bind_won" : "bind_lost") : "bind_won_other";
				rgb = mine && !won ? CRIMSON : 0xFFD86A;
				scale = 1.1F;
			}
			case DuelIds.OUT_BIND_LEPAS -> {
				key = "bind_break";
				rgb = 0xE8F0FF;
				scale = 0.95F;
			}
			case DuelIds.OUT_BIND_RELEASE_TRICK -> {
				key = "release_trick";
				rgb = 0xB4E2FF;
				scale = 1.1F;
			}
			case DuelIds.OUT_GUARD_BREAK -> {
				key = "guard_break";
				rgb = CRIMSON;
				scale = 1.3F;
			}
			case DuelIds.OUT_POSTURE_BREAK -> {
				key = "posture_break";
				rgb = CRIMSON;
				scale = 1.3F;
			}
			case DuelIds.OUT_DEATHBLOW -> {
				key = "deathblow";
				rgb = INK_RED;
				scale = 1.6F;
			}
			case DuelIds.OUT_MIKIRI -> {
				key = "mikiri";
				rgb = 0xFFD86A;
				scale = 1.3F;
			}
			case DuelIds.OUT_QI_REFLECT -> {
				key = "qi_reflect";
				rgb = p.rgbB() == 0 ? 0xB4E2FF : p.rgbB() & 0xFFFFFF;
				scale = 1.2F;
			}
			case DuelIds.OUT_QI_DEFLECT -> {
				key = "qi_deflect";
				rgb = 0xB4E2FF;
				scale = 1.0F;
			}
			case DuelIds.OUT_COMPANION_DEFLECT -> {
				key = "companion";
				rgb = 0xB4E2FF;
				scale = 1.0F;
			}
			case DuelIds.OUT_EVADE -> {
				key = "evade";
				rgb = 0xC8D2DC;
				scale = 0.9F;
			}
			case DuelIds.OUT_BLOCK -> {
				key = "block";
				rgb = 0xC8D2DC;
				scale = 0.85F;
			}
			default -> {
				return;
			}
		}
		if (watched) {
			scale *= 0.8F;
		}
		POPUPS.add(new Popup(Component.translatable("kultivator.duel.hud.out." + key), rgb, ticks, p.outcome() == DuelIds.OUT_DEATHBLOW ? 40 : 26,
				scale, predicted ? p.swingA() : Integer.MIN_VALUE, p.aId()));
		while (POPUPS.size() > 3) {
			POPUPS.remove(0);
		}
	}

	/** A local session changed phase. */
	public static void phaseChanged(DuelPayloads.DuelSessionSync p, int before) {
		phaseShown = p.phase();
		if (p.phase() == DuelIds.PHASE_FIGHT && before != DuelIds.PHASE_FIGHT) {
			fightStartedAt = ticks;
		}
		if (p.phase() == DuelIds.PHASE_ENDED) {
			outSince = -1L;
		}
	}

	/** Once per client tick. */
	public static void tick(Minecraft minecraft) {
		ticks++;
		for (Iterator<Map.Entry<Integer, Bar>> it = BARS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Bar> entry = it.next();
			Bar bar = entry.getValue();
			bar.before = bar.shown;
			bar.shown += (bar.target - bar.shown) * 0.35F;
			if (Math.abs(bar.target - bar.shown) < 0.002F) {
				bar.shown = bar.target;
			}
			if (bar.fresh > 0) {
				bar.fresh--;
			}
			if (bar.flash > 0) {
				bar.flash--;
			}
			if (ticks - bar.seenAt > 600L && DuelClient.fighter(entry.getKey()) == null) {
				it.remove();
			}
		}
		POPUPS.removeIf(popup -> ticks - popup.born() > popup.life());
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		DuelPayloads.DuelSessionSync s = DuelClient.localSession();
		boolean out = s != null && s.phase() == DuelIds.PHASE_FIGHT
				&& (s.aId() == player.getId() ? (s.flags() & DuelIds.SF_OUT_A) != 0 : (s.flags() & DuelIds.SF_OUT_B) != 0);
		if (out && outSince < 0L) {
			outSince = ticks;
		} else if (!out) {
			outSince = -1L;
		}
		DuelPayloads.DuelFighterSync self = DuelClient.fighter(player.getId());
		int chain = self == null ? 0 : self.chain();
		if (chain != chainShown) {
			if (chain > chainShown) {
				chainAt = ticks;
			}
			chainShown = chain;
		}
		if (ticks % 10L == 0L) {
			monsterNear = s != null && s.phase() == DuelIds.PHASE_FIGHT && hostileNear(minecraft, player, s);
		}
	}

	private static boolean hostileNear(Minecraft minecraft, LocalPlayer player, DuelPayloads.DuelSessionSync s) {
		if (minecraft.level == null) {
			return false;
		}
		for (Entity entity : minecraft.level.getEntities(player, player.getBoundingBox().inflate(8.0D))) {
			if (entity instanceof Enemy && entity.isAlive() && entity.getId() != s.aId() && entity.getId() != s.bId()) {
				return true;
			}
		}
		return false;
	}

	private static boolean watchedHas(int a, int b) {
		DuelPayloads.DuelSessionSync w = DuelClient.watchedSession();
		return w != null && (w.aId() == a || w.aId() == b || w.bId() == a || w.bId() == b);
	}

	// ================================================================== drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.hud.isHidden() || EpicDirector.recordHide()) {
			return;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		int width = g.guiWidth();
		int height = g.guiHeight();
		Font font = minecraft.font;
		boolean film = Letterbox.amount() > 0.5F;
		if (!film) {
			try {
				drawDuel(g, minecraft, player, font, width, height, partial);
			} catch (RuntimeException exception) {
				warnOnce(exception);
			}
		}
		if (!film) {
			DuelResultCard.draw(g, font, width, height, partial);
		}
	}

	private static boolean warned;

	private static void warnOnce(RuntimeException exception) {
		if (!warned) {
			warned = true;
			Kultivator.LOGGER.warn("Kultivator duel: the HUD failed once and skips the frame", exception);
		}
	}

	private static void drawDuel(GuiGraphicsExtractor g, Minecraft minecraft, LocalPlayer player, Font font, int width, int height, float partial) {
		int self = player.getId();
		DuelPayloads.DuelSessionSync s = DuelClient.localSession();
		DuelPayloads.DuelSessionSync watched = s == null ? DuelClient.watchedSession() : null;
		int opponent = DuelClient.partnerOf(self);
		if (opponent < 0 && ticks - recentOpponentAt < 200L && DuelClient.fighter(recentOpponent) != null) {
			opponent = recentOpponent;
		}
		if (opponent < 0 && minecraft.crosshairPickEntity != null && DuelClient.fighter(minecraft.crosshairPickEntity.getId()) != null
				&& minecraft.crosshairPickEntity.distanceTo(player) <= 16.0F) {
			opponent = minecraft.crosshairPickEntity.getId();
		}
		boolean world = DuelSettings.hudWorld();

		// ---- the bars
		if (watched != null) {
			drawWatched(g, minecraft, font, watched, width, partial, world);
		} else if (opponent >= 0 && !world) {
			drawOpponent(g, minecraft, font, s, opponent, width, partial);
		}
		if (world) {
			drawWorldBars(g, minecraft, player, width, height, partial);
		}
		DuelPayloads.DuelFighterSync own = DuelClient.fighter(self);
		if (own != null && (s != null || own.postureX10() > 0 || opponent >= 0)) {
			drawOwn(g, minecraft, player, font, own, width, height, partial);
		}

		// ---- session overlays
		if (s != null) {
			switch (s.phase()) {
				case DuelIds.PHASE_GATHER -> drawGather(g, player, font, s, width, height, partial);
				case DuelIds.PHASE_COUNTDOWN -> drawCountdown(g, font, s, width, height, partial);
				case DuelIds.PHASE_FIGHT -> drawFight(g, font, s, width, partial);
				default -> {
				}
			}
			if (s.phase() == DuelIds.PHASE_FIGHT && ticks - fightStartedAt < 26L) {
				drawBegin(g, font, width, height, partial);
			}
			if (outSince >= 0L) {
				drawRingOut(g, font, width, height, partial);
			}
		} else if (watched != null && watched.phase() == DuelIds.PHASE_COUNTDOWN) {
			drawCountdown(g, font, watched, width, height, partial);
		}

		// ---- the lock, the marks, the player's own state
		DuelPayloads.DuelBindSync bind = DuelClient.bind(self);
		if (bind != null) {
			drawBind(g, font, bind, self, width, height, partial);
		}
		if (opponent >= 0) {
			drawPerilous(g, font, opponent, width, height, partial);
			drawDeathblowReady(g, font, opponent, width, height, partial);
		}
		drawCrosshairRings(g, font, own, width, height, partial);
		drawChain(g, font, width, height, partial);
		drawPopups(g, font, width, height, partial);
	}

	// ---------------------------------------------------------------- posture bars

	/** One bar centred on {@code cx} at {@code y}, {@code scale} times the size. */
	private static void bar(GuiGraphicsExtractor g, int entityId, float cx, float y, float scale, float partial, @Nullable LivingEntity health,
							boolean tenPercent) {
		Bar bar = BARS.get(entityId);
		DuelPayloads.DuelFighterSync sync = DuelClient.fighter(entityId);
		float value;
		float fresh = 0.0F;
		float freshFrom = 0.0F;
		int flash = 0;
		boolean stagger;
		if (bar != null) {
			value = Mth.lerp(partial, bar.before, bar.shown);
			fresh = bar.fresh / 10.0F;
			freshFrom = bar.freshFrom;
			flash = bar.flash;
			stagger = bar.state == DuelIds.ST_STAGGER;
		} else if (sync != null) {
			value = Mth.clamp(sync.postureX10() / (float) Math.max(1, sync.postureMaxX10()), 0.0F, 1.0F);
			stagger = sync.state() == DuelIds.ST_STAGGER;
		} else {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale, scale);
		int x0 = -BAR_W / 2;
		// the lacquered frame
		g.blit(RenderPipelines.GUI_TEXTURED, FRAME, x0, 0, 0.0F, 0.0F, BAR_W, BAR_H, BAR_W, BAR_H, 0xFFFFFFFF);
		// the fill, from the centre outward
		int half = Math.round(Mth.clamp(value, 0.0F, 1.0F) * FILL_HALF);
		if (half > 0) {
			float hot = Mth.clamp((value - 0.7F) / 0.2F, 0.0F, 1.0F);
			float pulse = value >= 0.85F ? 0.75F + 0.25F * Mth.sin((ticks + partial) * 0.6F) : 1.0F;
			int calm = Palette.argb(Math.round(255 * pulse), 0xFFFFFF);
			g.blit(RenderPipelines.GUI_TEXTURED, FILL, -half, 2, 64 - half, 0.0F, half * 2, 8, half * 2, 8, 128, 16, calm);
			if (hot > 0.0F) {
				g.blit(RenderPipelines.GUI_TEXTURED, FILL, -half, 2, 64 - half, 8.0F, half * 2, 8, half * 2, 8, 128, 16,
						Palette.argb(Math.round(255 * hot * pulse), 0xFFFFFF));
			}
			// the posture just taken glints white at both growing edges
			if (fresh > 0.0F && value > freshFrom) {
				int from = Math.round(Mth.clamp(freshFrom, 0.0F, 1.0F) * FILL_HALF);
				int a = Math.round(200 * fresh);
				g.fill(-half, 3, -from, 9, Palette.argb(a, 0xFFF8E0));
				g.fill(from, 3, half, 9, Palette.argb(a, 0xFFF8E0));
			}
			// the bright leading edges
			g.fill(-half, 2, -half + 1, 10, Palette.argb(220, 0xFFF4D0));
			g.fill(half - 1, 2, half, 10, Palette.argb(220, 0xFFF4D0));
		}
		// the centre notch over the fill
		g.blit(RenderPipelines.GUI_TEXTURED, FRAME, -4, 2, 60.0F, 2.0F, 9, 9, 9, 9, BAR_W, BAR_H, 0xFFFFFFFF);
		if (flash > 0 || stagger) {
			float f = flash > 0 ? flash / 18.0F : 0.35F + 0.25F * Mth.sin((ticks + partial) * 0.8F);
			g.fill(x0 + 3, 1, -x0 - 3, BAR_H - 1, Palette.argb(Math.round(180 * f), flash > 8 ? 0xFFFFFF : CRIMSON));
		}
		// the health line with the 10% mark of DuelRules
		if (health != null) {
			float share = Mth.clamp(health.getHealth() / Math.max(1.0F, health.getMaxHealth()), 0.0F, 1.0F);
			int w = BAR_W - 16;
			int hx = -w / 2;
			g.fill(hx - 1, BAR_H + 1, hx + w + 1, BAR_H + 4, Palette.argb(170, 0x0A0604));
			g.fill(hx, BAR_H + 2, hx + Math.round(w * share), BAR_H + 3, Palette.argb(230, share <= 0.25F ? 0xFF4A3A : 0xC83A2E));
			if (tenPercent) {
				int mark = hx + Math.round(w * 0.10F);
				g.fill(mark, BAR_H + 1, mark + 1, BAR_H + 4, Palette.argb(240, GOLD));
			}
		}
		g.pose().popMatrix();
		if (stagger) {
			seal(g, cx + (BAR_W / 2.0F + 9.0F) * scale, y + BAR_H * scale / 2.0F, 0.55F * scale, true, "決", partial);
		}
	}

	/** The player's own bar above the hearts (moved up for each extra heart row and armour). */
	private static void drawOwn(GuiGraphicsExtractor g, Minecraft minecraft, LocalPlayer player, Font font, DuelPayloads.DuelFighterSync own,
								int width, int height, float partial) {
		float maxHealth = player.getMaxHealth() + player.getAbsorptionAmount();
		int rows = Mth.ceil(maxHealth / 2.0F / 10.0F);
		int rowHeight = Math.max(10 - (rows - 2), 3);
		int lift = (rows - 1) * rowHeight + (player.getArmorValue() > 0 ? 10 : 0);
		float y = height - 52 - lift - BAR_H;
		if (minecraft.gameMode != null && !minecraft.gameMode.canHurtPlayer()) {
			y = height - 40 - BAR_H;
		}
		bar(g, player.getId(), width / 2.0F, y, 0.9F, partial, null, false);
		// a tiny label under the bar while it is not empty
		if (own.postureX10() > 0) {
			Brush.centered(g, font, Component.translatable("kultivator.duel.hud.posture"), width / 2.0F, y - 7.0F, Palette.argb(150, 0xE8DCC0), 0.55F,
					true);
		}
	}

	private static void drawOpponent(GuiGraphicsExtractor g, Minecraft minecraft, Font font, @Nullable DuelPayloads.DuelSessionSync s, int opponent,
									 int width, float partial) {
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(opponent);
		float y = s != null ? 14.0F : 44.0F;
		Component name = null;
		Component rank = null;
		int rgb = 0xE8DCC0;
		if (s != null) {
			boolean isA = s.aId() == opponent;
			name = isA ? s.aName() : s.bName();
			rank = isA ? s.aRank() : s.bRank();
			int c = isA ? s.aRgb() : s.bRgb();
			if (c != 0) {
				rgb = c & 0xFFFFFF;
			}
		} else if (entity != null) {
			name = entity.getDisplayName();
		}
		if (name != null) {
			Brush.centered(g, font, name, width / 2.0F, y - 11.0F, Palette.argb(240, rgb), 0.85F, true);
		}
		if (rank != null && !rank.getString().isEmpty()) {
			Brush.centered(g, font, rank, width / 2.0F + 0.0F, y + BAR_H + 6.0F, Palette.argb(170, 0xD8CCB0), 0.55F, true);
		}
		LivingEntity living = entity instanceof LivingEntity l ? l : null;
		bar(g, opponent, width / 2.0F, y, 1.0F, partial, living, s != null);
	}

	/** Spectators: both duellists, A on the left and B on the right. */
	private static void drawWatched(GuiGraphicsExtractor g, Minecraft minecraft, Font font, DuelPayloads.DuelSessionSync w, int width, float partial,
									boolean world) {
		if (world || w.phase() < DuelIds.PHASE_COUNTDOWN || w.phase() > DuelIds.PHASE_FINISHER) {
			return;
		}
		float y = 14.0F;
		float gap = Math.min(150.0F, width * 0.24F);
		for (int side = 0; side < 2; side++) {
			int id = side == 0 ? w.aId() : w.bId();
			float cx = width / 2.0F + (side == 0 ? -gap : gap);
			Component name = side == 0 ? w.aName() : w.bName();
			int rgb = (side == 0 ? w.aRgb() : w.bRgb()) & 0xFFFFFF;
			Brush.centered(g, font, name, cx, y - 10.0F, Palette.argb(240, rgb == 0 ? 0xE8DCC0 : rgb), 0.75F, true);
			Entity e = minecraft.level == null ? null : minecraft.level.getEntity(id);
			bar(g, id, cx, y, 0.75F, partial, e instanceof LivingEntity l ? l : null, true);
		}
		Brush.centered(g, font, Component.literal("VS"), width / 2.0F, y + 1.0F, Palette.argb(200, GOLD), 0.8F, true);
		if (w.phase() == DuelIds.PHASE_FIGHT) {
			int left = DuelClient.fightTicksLeft(w);
			Brush.centered(g, font, Component.literal(clock(left)), width / 2.0F, y + 12.0F, Palette.argb(200, 0xE8DCC0), 0.6F, true);
		}
		Brush.centered(g, font, Component.translatable("kultivator.duel.hud.watching", DuelKeys.CAMERA.isUnbound() ? Component.literal("-") : DuelKeys.CAMERA.getTranslatedKeyMessage()),
				width / 2.0F, y + 22.0F, Palette.argb(120, 0xD8CCB0), 0.5F, true);
	}

	/** "Bar postur di atas kepala": every fighter within 24 blocks but the player gets a small bar over the head. */
	private static void drawWorldBars(GuiGraphicsExtractor g, Minecraft minecraft, LocalPlayer player, int width, int height, float partial) {
		Camera camera = minecraft.gameRenderer.mainCamera();
		if (camera == null || minecraft.level == null) {
			return;
		}
		float[] out = new float[3];
		for (Map.Entry<Integer, Bar> entry : BARS.entrySet()) {
			int id = entry.getKey();
			if (id == player.getId()) {
				continue;
			}
			Entity entity = minecraft.level.getEntity(id);
			if (!(entity instanceof LivingEntity living) || entity.distanceTo(player) > 24.0F || DuelClient.fighter(id) == null) {
				continue;
			}
			Vec3 at = entity.getPosition(partial).add(0.0D, entity.getBbHeight() + 0.55D, 0.0D);
			if (!project(minecraft, camera, at, width, height, out)) {
				continue;
			}
			float scale = Mth.clamp(4.0F / Math.max(1.0F, out[2]), 0.3F, 0.6F);
			bar(g, id, out[0], out[1] - BAR_H * scale, scale, partial, living, DuelClient.session(id) != null);
		}
	}

	/**
	 * World to GUI pixels by the camera's position, yaw, pitch and the FOV setting (the dynamic FOV of sprinting is
	 * ignored: a bar may sit a pixel off while running). {@code out} = x, y, depth. False behind the camera or off screen.
	 */
	static boolean project(Minecraft minecraft, Camera camera, Vec3 at, int width, int height, float[] out) {
		Vec3 rel = at.subtract(camera.position());
		float yaw = camera.yRot() * Mth.DEG_TO_RAD;
		float pitch = camera.xRot() * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw) * Mth.cos(pitch);
		double fy = -Mth.sin(pitch);
		double fz = Mth.cos(yaw) * Mth.cos(pitch);
		double rx = -Mth.cos(yaw);
		double rz = -Mth.sin(yaw);
		// up = right × forward
		double ux = 0.0D * fz - rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy - 0.0D * fx;
		double z = rel.x * fx + rel.y * fy + rel.z * fz;
		if (z < 0.2D) {
			return false;
		}
		double x = rel.x * rx + rel.z * rz;
		double y = rel.x * ux + rel.y * uy + rel.z * uz;
		double tan = Math.tan(Math.toRadians(minecraft.options.fov().get()) / 2.0D);
		double aspect = width / (double) Math.max(1, height);
		float sx = (float) (width / 2.0D + x / z / (tan * aspect) * width / 2.0D);
		float sy = (float) (height / 2.0D - y / z / tan * height / 2.0D);
		if (sx < -40.0F || sx > width + 40.0F || sy < -20.0F || sy > height + 20.0F) {
			return false;
		}
		out[0] = sx;
		out[1] = sy;
		out[2] = (float) z;
		return true;
	}

	// ---------------------------------------------------------------- phases

	/** GATHER: an arrow at the bottom of the screen toward the player's start mark, with the distance. */
	private static void drawGather(GuiGraphicsExtractor g, LocalPlayer player, Font font, DuelPayloads.DuelSessionSync s, int width, int height,
								   float partial) {
		boolean isA = s.aId() == player.getId();
		double mx = isA ? s.markAx() : s.markBx();
		double mz = isA ? s.markAz() : s.markBz();
		Vec3 pos = player.getPosition(partial);
		double dx = mx - pos.x;
		double dz = mz - pos.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		float cx = width / 2.0F;
		float cy = height * 0.7F;
		if (distance > 1.5D) {
			float worldYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
			float rel = Mth.wrapDegrees(worldYaw - player.getViewYRot(partial)) * Mth.DEG_TO_RAD;
			float ax = Mth.sin(rel);
			float ay = -Mth.cos(rel);
			float tipX = cx + ax * 16.0F;
			float tipY = cy + ay * 16.0F;
			int ink = Palette.argb(220, INK_RED);
			Brush.line(g, cx - ax * 6.0F, cy - ay * 6.0F, tipX, tipY, 2.0F, ink);
			float px = -ay;
			float py = ax;
			Brush.line(g, tipX, tipY, tipX - ax * 6.0F + px * 5.0F, tipY - ay * 6.0F + py * 5.0F, 2.0F, ink);
			Brush.line(g, tipX, tipY, tipX - ax * 6.0F - px * 5.0F, tipY - ay * 6.0F - py * 5.0F, 2.0F, ink);
		} else {
			Brush.ring(g, cx, cy, 6.0F, 8.0F, Palette.argb(200, GOLD));
		}
		Component line = distance > 1.5D
				? Component.translatable("kultivator.duel.hud.gather", String.format(java.util.Locale.ROOT, "%.1f", distance))
				: Component.translatable("kultivator.duel.hud.gather_ready");
		Brush.centered(g, font, line, cx, cy + 22.0F, Palette.argb(230, 0xF1D57E), 0.8F, true);
	}

	/** 三 二 一: a big brush numeral that pops in each second. */
	private static void drawCountdown(GuiGraphicsExtractor g, Font font, DuelPayloads.DuelSessionSync s, int width, int height, float partial) {
		int left = DuelClient.phaseTicksLeft(s);
		int index = Mth.clamp((left - 1) / 20, 0, 2);
		String numeral = NUMERALS[index];
		float within = 1.0F - ((left - 1) % 20 + 1 - partial) / 20.0F;
		float pop = within < 0.18F ? 1.0F + (0.18F - within) * 3.0F : 1.0F;
		float alpha = within > 0.8F ? (1.0F - within) / 0.2F : 1.0F;
		drawBigGlyph(g, font, numeral, width / 2.0F, height * 0.32F, 4.0F * pop, alpha, INK_RED);
		Brush.centered(g, font, Component.translatable("kultivator.duel.hud.countdown"), width / 2.0F, height * 0.32F + 26.0F,
				Palette.argb(Math.round(200 * alpha), 0xF1D57E), 0.7F, true);
	}

	/** 始: the fight begins. */
	private static void drawBegin(GuiGraphicsExtractor g, Font font, int width, int height, float partial) {
		float t = (ticks - fightStartedAt + partial) / 26.0F;
		float pop = t < 0.15F ? 1.0F + (0.15F - t) * 4.0F : 1.0F;
		float alpha = t > 0.6F ? Math.max(0.0F, (1.0F - t) / 0.4F) : 1.0F;
		drawBigGlyph(g, font, "始", width / 2.0F, height * 0.32F, 4.6F * pop, alpha, 0xE8B030);
	}

	/** A glyph in a dark ink halo. */
	private static void drawBigGlyph(GuiGraphicsExtractor g, Font font, String glyph, float cx, float cy, float scale, float alpha, int rgb) {
		Brush.disc(g, cx, cy, 9.0F * scale, Palette.argb(Math.round(90 * alpha), 0x0A0604));
		Brush.ring(g, cx, cy, 8.0F * scale, 8.6F * scale, Palette.argb(Math.round(140 * alpha), GOLD));
		Component text = Component.literal(glyph);
		float w = font.width(text) * scale;
		Brush.text(g, font, text, cx - w / 2.0F + scale * 0.5F, cy - 4.0F * scale + scale * 0.5F, Palette.argb(Math.round(180 * alpha), 0x200806),
				scale, false);
		Brush.text(g, font, text, cx - w / 2.0F, cy - 4.0F * scale, Palette.argb(Math.round(255 * alpha), rgb), scale, false);
	}

	/** FIGHT: the time left with the rules, the surrender hint, the "pause" hint when a monster comes near. */
	private static void drawFight(GuiGraphicsExtractor g, Font font, DuelPayloads.DuelSessionSync s, int width, float partial) {
		int left = DuelClient.fightTicksLeft(s);
		int rgb = left <= 200 ? (ticks / 10L % 2L == 0L ? 0xFF6A4A : 0xF1D57E) : 0xE8DCC0;
		float y = 34.0F;
		Brush.centered(g, font, Component.literal(clock(left)), width / 2.0F, y, Palette.argb(220, rgb), 0.7F, true);
		Component rules = Component.translatable("kultivator.duel.hud.rules", Component.translatable("kultivator.duel.ui.rule.realm." + s.rules().realmRule()),
				Component.translatable("kultivator.duel.ui.rule.jurus." + s.rules().jurusRule()));
		Brush.centered(g, font, rules, width / 2.0F, y + 8.0F, Palette.argb(130, 0xD8CCB0), 0.5F, true);
		if (monsterNear) {
			Brush.centered(g, font, Component.translatable("kultivator.duel.hud.monster"), width / 2.0F, y + 16.0F, Palette.argb(220, 0xFF8A5A), 0.6F,
					true);
		}
	}

	/** Out of the ring: red edges and the 3-2-1 of {@code DuelConfig.ringOutTicks} (60). */
	private static void drawRingOut(GuiGraphicsExtractor g, Font font, int width, int height, float partial) {
		float elapsed = ticks - outSince + partial;
		int seconds = Mth.clamp(3 - (int) (elapsed / 20.0F), 1, 3);
		float pulse = 0.5F + 0.5F * Mth.sin(elapsed * 0.5F);
		int a = Math.round(60 + 60 * pulse);
		g.fillGradient(0, 0, width, 24, Palette.argb(a, DANGER), Palette.argb(0, DANGER));
		g.fillGradient(0, height - 24, width, height, Palette.argb(0, DANGER), Palette.argb(a, DANGER));
		Brush.centered(g, font, Component.translatable("kultivator.duel.hud.ring_out", seconds), width / 2.0F, height * 0.4F,
				Palette.argb(240, 0xFF4A3A), 1.1F, true);
	}

	// ---------------------------------------------------------------- blade lock

	private static void drawBind(GuiGraphicsExtractor g, Font font, DuelPayloads.DuelBindSync bind, int self, int width, int height, float partial) {
		boolean selfIsA = bind.aId() == self;
		// the player always on the left: positive = me ahead
		float pressure = Mth.clamp((selfIsA ? bind.pressure() : -bind.pressure()) / 100.0F, -1.0F, 1.0F);
		boolean opponentPushing = selfIsA ? (bind.flags() & DuelIds.BF_B_PUSHING) != 0 : (bind.flags() & DuelIds.BF_A_PUSHING) != 0;
		float cx = width / 2.0F;
		float y = height * 0.62F;
		float shake = Math.abs(pressure) * 1.2F * Mth.sin((ticks + partial) * 2.3F);
		g.pose().pushMatrix();
		g.pose().translate(cx - 64.0F + shake, y);
		g.blit(RenderPipelines.GUI_TEXTURED, BIND, 0, 0, 0.0F, 0.0F, 128, 10, 128, 10, 128, 32, 0xFFFFFFFF);
		// the disc slides toward the losing side: when I push ahead it moves right (into the opponent's indigo)
		float discX = 64.0F + pressure * 54.0F;
		if (opponentPushing) {
			float glow = 0.6F + 0.4F * Mth.sin((ticks + partial) * 1.4F);
			g.blit(RenderPipelines.GUI_TEXTURED, BIND, Math.round(discX - 14.0F), -9, 24.0F, 12.0F, 28, 28, 20, 20, 128, 32,
					Palette.argb(Math.round(220 * glow), 0x7FD4FF));
		}
		g.pose().pushMatrix();
		g.pose().translate(discX, 5.0F);
		g.pose().rotate((ticks + partial) * 0.12F * (pressure >= 0.0F ? 1.0F : -1.0F));
		g.blit(RenderPipelines.GUI_TEXTURED, BIND, -8, -8, 0.0F, 12.0F, 16, 16, 20, 20, 128, 32, 0xFFFFFFFF);
		g.pose().popMatrix();
		// the ticks left until it breaks by itself (40)
		float left = Mth.clamp(bind.ticksLeft() / 40.0F, 0.0F, 1.0F);
		g.fill(0, 12, Math.round(128 * left), 13, Palette.argb(170, GOLD));
		g.pose().popMatrix();
		Brush.centered(g, font, Component.translatable("kultivator.duel.hud.bind"), cx, y - 12.0F, Palette.argb(230, 0xFF9A50), 0.8F, true);
		Component hint = opponentPushing
				? Component.translatable("kultivator.duel.hud.bind_release", parryKey())
				: Component.translatable("kultivator.duel.hud.bind_push");
		Brush.centered(g, font, hint, cx, y + 17.0F, Palette.argb(opponentPushing ? 240 : 170, opponentPushing ? 0x7FD4FF : 0xE8DCC0), 0.6F, true);
	}

	// ---------------------------------------------------------------- 危 and 決

	/** 危 while the opponent winds up a perilous attack, with the answer to it. */
	private static void drawPerilous(GuiGraphicsExtractor g, Font font, int opponent, int width, int height, float partial) {
		DuelPayloads.DuelSwing swing = DuelClient.swing(opponent);
		if (swing == null || (swing.flags() & DuelIds.SW_PERILOUS) == 0 || (swing.flags() & (DuelIds.SW_FEINT_CANCEL | DuelIds.SW_REJECTED)) != 0) {
			return;
		}
		double now = DuelClock.serverTickNow(partial);
		double impact = swing.windup() == DuelIds.WINDUP_CHARGING ? now + 8.0D : (double) swing.startTick() + swing.windup();
		if (now > impact + 3.0D || now < swing.startTick() - 2.0D) {
			return;
		}
		String kind = (swing.flags() & DuelIds.SW_PERILOUS_MAUT) != 0 ? "maut" : (swing.flags() & DuelIds.SW_PERILOUS_SAPU) != 0 ? "sapu" : "gunung";
		float since = (float) (now - swing.startTick());
		float scale = since < 3.0F ? 1.0F + (3.0F - since) * 0.25F : 1.0F;
		float cx = width / 2.0F;
		float cy = height / 2.0F - 30.0F;
		seal(g, cx, cy, 0.8F * scale, false, "危", partial);
		Brush.centered(g, font, Component.translatable("kultivator.duel.hud.peril." + kind), cx, cy + 15.0F, Palette.argb(240, 0xFF4A3A), 0.75F, true);
		Brush.centered(g, font, Component.translatable("kultivator.duel.hud.peril." + kind + ".answer", parryKey()), cx, cy + 23.0F,
				Palette.argb(200, 0xF1D57E), 0.55F, true);
	}

	/** 決 while the opponent is staggered: the deathblow is open. */
	private static void drawDeathblowReady(GuiGraphicsExtractor g, Font font, int opponent, int width, int height, float partial) {
		DuelPayloads.DuelFighterSync f = DuelClient.fighter(opponent);
		if (f == null || (f.flags() & DuelIds.FF_DEATHBLOW_MARK) == 0) {
			return;
		}
		float cx = width / 2.0F;
		float cy = height / 2.0F + 34.0F;
		seal(g, cx, cy, 0.7F, true, "決", partial);
		Brush.centered(g, font, Component.translatable("kultivator.duel.hud.deathblow_ready"), cx, cy + 13.0F,
				Palette.argb(Math.round(200 + 55 * Mth.sin((ticks + partial) * 0.5F)), 0xFF5040), 0.7F, true);
	}

	/** A vermilion seal stamp (square for 危, round for 決/勝) with the glyph written on it in white. */
	static void seal(GuiGraphicsExtractor g, float cx, float cy, float scale, boolean round, String glyph, float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		g.pose().rotate(round ? 0.0F : -0.08F);
		g.blit(RenderPipelines.GUI_TEXTURED, SEALS, -16, -16, round ? 32.0F : 0.0F, 0.0F, 32, 32, 32, 32, 64, 32, 0xFFFFFFFF);
		Component text = Component.literal(glyph);
		float s = 2.0F;
		float w = minecraft.font.width(text) * s;
		g.pose().pushMatrix();
		g.pose().translate(-w / 2.0F, -4.0F * s);
		g.pose().scale(s, s);
		g.text(minecraft.font, text, 0, 0, 0xFFFFF4E0, false);
		g.pose().popMatrix();
		g.pose().popMatrix();
	}

	// ---------------------------------------------------------------- around the crosshair

	/** The heavy charge, the surrender ring, the spam hint and the Opening flash. */
	private static void drawCrosshairRings(GuiGraphicsExtractor g, Font font, @Nullable DuelPayloads.DuelFighterSync own, int width, int height,
										   float partial) {
		float cx = width / 2.0F;
		float cy = height / 2.0F;
		float charge = DuelInput.chargeProgress();
		if (charge > 0.0F) {
			int rgb = charge >= 1.0F ? 0xFFD86A : 0xE8DCC0;
			float pulse = charge >= 1.0F ? 0.7F + 0.3F * Mth.sin((ticks + partial) * 0.9F) : 1.0F;
			Brush.arc(g, cx, cy, 10.0F, 12.0F, 0.0F, charge, Palette.argb(Math.round(220 * pulse), rgb));
		}
		float surrender = DuelInput.surrenderProgress();
		if (surrender > 0.0F) {
			Brush.arc(g, cx, cy, 14.0F, 16.0F, 0.0F, surrender, Palette.argb(230, 0xF0F0F0));
			Brush.centered(g, font, Component.translatable("kultivator.duel.hud.surrendering"), cx, cy + 20.0F, Palette.argb(230, 0xF0F0F0), 0.6F, true);
		}
		if (own == null) {
			return;
		}
		if ((own.flags() & DuelIds.FF_SPAM) != 0) {
			Brush.centered(g, font, Component.translatable("kultivator.duel.hud.spam"), cx, cy + 30.0F, Palette.argb(200, 0xFF8A5A), 0.55F, true);
		}
		if ((own.flags() & DuelIds.FF_CELAH) != 0) {
			float t = (ticks - celahAt + partial) / 10.0F;
			float a = t < 1.0F ? 1.0F : 0.7F;
			Brush.centered(g, font, Component.translatable("kultivator.duel.hud.celah"), cx, cy - 22.0F, Palette.argb(Math.round(240 * a), 0xFFD86A),
					0.75F + Math.max(0.0F, 0.3F - t * 0.3F), true);
		}
		DuelPayloads.DuelSessionSync s = DuelClient.localSession();
		LocalPlayer player = Minecraft.getInstance().player;
		if (s != null && s.phase() == DuelIds.PHASE_FIGHT && player != null && surrender <= 0.0F
				&& player.getHealth() / Math.max(1.0F, player.getMaxHealth()) < 0.35F) {
			Brush.centered(g, font, Component.translatable("kultivator.duel.hud.surrender_hint", parryKey()), cx, height - 70.0F,
					Palette.argb(110, 0xE8DCC0), 0.5F, true);
		}
	}

	// ---------------------------------------------------------------- the chain and the words

	/** 一 … 十 on the right, with the count under it; it pops on each new link and fades after the chain gap. */
	private static void drawChain(GuiGraphicsExtractor g, Font font, int width, int height, float partial) {
		int chain = chainShown;
		DuelPayloads.DuelSessionSync w = DuelClient.localSession() == null ? DuelClient.watchedSession() : null;
		if (w != null) {
			DuelPayloads.DuelFighterSync a = DuelClient.fighter(w.aId());
			DuelPayloads.DuelFighterSync b = DuelClient.fighter(w.bId());
			chain = Math.max(a == null ? 0 : a.chain(), b == null ? 0 : b.chain());
		}
		if (chain < 2) {
			return;
		}
		float since = ticks - chainAt + partial;
		float alpha = since > 60.0F ? Math.max(0.35F, 1.0F - (since - 60.0F) / 40.0F) : 1.0F;
		float pop = since < 4.0F ? 1.0F + (4.0F - since) * 0.08F : 1.0F;
		String glyph = chain <= 10 ? NUMERALS[chain - 1] : "十";
		float x = width - 34.0F;
		float y = height * 0.42F;
		int rgb = chain >= 10 ? 0xFFD86A : chain >= 6 ? 0xFFB050 : chain >= 4 ? 0xF1D57E : 0xE8DCC0;
		drawBigGlyph(g, font, glyph, x, y, 2.4F * pop, alpha, rgb);
		Component label = chain > 10 ? Component.translatable("kultivator.duel.hud.chain_more", chain) : Component.translatable("kultivator.duel.hud.chain");
		Brush.centered(g, font, label, x, y + 16.0F, Palette.argb(Math.round(200 * alpha), 0xE8DCC0), 0.55F, true);
		if (chain >= 4) {
			Brush.centered(g, font, Component.translatable("kultivator.duel.hud.sword_dance"), x, y + 23.0F, Palette.argb(Math.round(170 * alpha), 0xFFB050),
					0.5F, true);
		}
	}

	private static void drawPopups(GuiGraphicsExtractor g, Font font, int width, int height, float partial) {
		float y = height * 0.5F - 50.0F;
		for (int i = POPUPS.size() - 1; i >= 0; i--) {
			Popup popup = POPUPS.get(i);
			float age = ticks - popup.born() + partial;
			float t = age / popup.life();
			float alpha = t < 0.1F ? t / 0.1F : t > 0.65F ? Math.max(0.0F, (1.0F - t) / 0.35F) : 1.0F;
			float scale = popup.scale() * (age < 3.0F ? 1.0F + (3.0F - age) * 0.12F : 1.0F);
			float rise = age * 0.35F;
			Brush.centered(g, font, popup.text(), width / 2.0F, y - rise, Palette.argb(Math.round(255 * alpha), popup.rgb()), scale, true);
			y -= 12.0F * popup.scale();
		}
	}

	// ---------------------------------------------------------------- helpers

	static String clock(int ticks) {
		int seconds = Math.max(0, (ticks + 19) / 20);
		return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
	}

	/** The parry key's name, or "klik kanan" when it is unbound. */
	static Component parryKey() {
		return DuelKeys.PARRY.isUnbound() ? Component.translatable("kultivator.duel.hud.right_click") : DuelKeys.PARRY.getTranslatedKeyMessage();
	}

	/** The listener instance (tests). */
	public static DuelBus.Listener listener() {
		return LISTENER;
	}

	public DuelHud() {
	}
}
