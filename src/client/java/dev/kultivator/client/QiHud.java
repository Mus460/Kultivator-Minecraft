package dev.kultivator.client;

import dev.kultivator.client.codex.SpecInfo;
import dev.kultivator.client.codex.TeknikSession;
import dev.kultivator.client.goals.GoalsClient;
import dev.kultivator.client.goals.HudDemo;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.cultivation.Enlightenment;
import dev.kultivator.cultivation.ImmortalPerks;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.SpiritRoots;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Element;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Technique;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.goals.DailyChores;
import dev.kultivator.technique.CloudStep;
import dev.kultivator.technique.FlyingSword;
import dev.kultivator.util.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The cultivation HUD in the bottom-left corner, in the strip under the chat and left of the offhand slot (DESIGN 5):
 * a dantian orb whose qi rises from the bottom in the root's colour, wrapped in a gold ring that fills clockwise with
 * the cultivation bar, a white-jade Perisai Qi arc, and violet beads while Stored Qi waits; the selected technique spec
 * as a seal tile (glyph, grade ribbon, cooldown sweep, mastery beads, scaled qi cost, charge pips, the red KEHANCURAN
 * tag, DESIGN 22.12.8); countdown arcs of lasting forms, domains and fields above the orb; and the realm, qi and
 * warnings beside it. Every value glides to its new number. Hidden with F1, F3, the HUD setting, an open chat, or
 * before the server sent any data. The quick seals, wheel, charge bar and toasts are {@code codex.CodexHud}.
 * <p>
 * Phase 7 (DESIGN 19.3.15): the flying-sword gauge (amber below 15 s, red below 10 s with chimes at 10/5/3 s, "Turun!"
 * below 2 s), the enlightenment timer, Cloud Step pips while airborne, the optional cooldown ping, the toxicity meter
 * from 40, the vein line (density, grade, "Tertutup +15%", the stone and its fuel timer), the star count and the three
 * daily-chore dots. The new lines are the first to go on narrow screens.
 */
public final class QiHud implements HudElement {

	private static final float ORB = 11.0F;
	private static final int STORED_BEADS = 12;
	/** Toxicity is shown from here (DESIGN 19.3.15), and as a warning above 80. */
	private static final double TOXICITY_SHOWN = 40.0D;
	private static final double TOXICITY_WARN = 80.0D;
	/** Flying-sword gauge thresholds in seconds. */
	private static final double FLY_AMBER = 15.0D;
	private static final double FLY_RED = 10.0D;
	private static final double FLY_LAND = 2.0D;
	private static final int[] FLY_CHIMES = {10, 5, 3};
	/** A cooldown at least this long pings when it ends (setting "Pengingat cooldown"). */
	private static final int PING_COOLDOWN_TICKS = 200;
	private static final long PING_FLASH_MILLIS = 700L;
	/** Fuel stone colours by grade (Low, Mid, High, Supreme). */
	private static final int[] STONE_COLORS = {0xFF9FD8C8, 0xFF6FB8FF, 0xFFB98CFF, 0xFFFFD54A};

	private static long seenVersion = -1L;
	private static int deviationAtSync;
	private static long syncClientTime;

	// Phase 7 client counters (ticked by goals.GoalsClient).
	private static int jumpsUsed;
	private static boolean jumpWasDown;
	private static int airTicks;
	private static long enlightenedAt = -1L;
	private static double lastFlySeconds = Double.MAX_VALUE;
	private static final Map<String, Long> COOLDOWNS = new HashMap<>();
	private static long pingFlashUntil;

	private final Eased qi = new Eased(5.0F);
	private final Eased bar = new Eased(5.0F);
	private final Eased stored = new Eased(4.0F);
	private final Eased storedAmount = new Eased(4.0F);
	private long lastFrame;
	private int lastStep = -1;

	/** Width the four stage pips need after a realm name. */
	private static final float PIP_ROOM = 17.0F;
	/** Tallest text block: its top then stays at or below the vanilla chat, which starts 40 px above the bottom. */
	private static final float MAX_TEXT_HEIGHT = 37.0F;

	/** One text line beside the orb: a component (shrunk or cut to fit) or a pre-split row, optionally with stage pips. */
	private record Line(@Nullable Component text, @Nullable FormattedCharSequence row, int color, float scale, int pips) {
		static Line of(Component text, int color, float scale) {
			return new Line(text, null, color, scale, -1);
		}
	}

	/** Deviation ticks left right now: the last snapshot's value, counted down on the client between snapshots. */
	public static int deviationTicksNow() {
		Minecraft minecraft = Minecraft.getInstance();
		long now = minecraft.level == null ? syncClientTime : minecraft.level.getGameTime();
		if (ClientCultivation.version() != seenVersion) {
			seenVersion = ClientCultivation.version();
			deviationAtSync = ClientCultivation.data().deviationTicks();
			syncClientTime = now;
		}
		return (int) Math.max(0L, deviationAtSync - Math.max(0L, now - syncClientTime));
	}

	// ================================================================== phase 7 counters (DESIGN 19.3.15)

	/**
	 * Every client tick (from {@code goals.GoalsClient}): counts Cloud Step jumps (the same press the key handler sends),
	 * notes when an enlightenment began, rings the flying-sword chimes and the optional cooldown ping.
	 */
	public static void clientTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !ClientCultivation.received()) {
			return;
		}
		CultivationData data = ClientCultivation.data();
		boolean grounded = player.onGround() || player.isInWater() || player.onClimbable() || player.isPassenger() || player.getAbilities().flying;
		airTicks = grounded ? 0 : airTicks + 1;
		if (grounded) {
			jumpsUsed = 0;
		}
		boolean jumpDown = minecraft.options.keyJump.isDown();
		if (jumpDown && !jumpWasDown && !grounded && airTicks > 2 && minecraft.gui.screen() == null && data.knows(Technique.CLOUD_STEP)
				&& !player.isFallFlying()) {
			jumpsUsed = Math.min(CloudStep.maxJumps(data.realm()), jumpsUsed + 1);
		}
		jumpWasDown = jumpDown;

		if (data.enlightened()) {
			if (enlightenedAt < 0L) {
				enlightenedAt = ClientCultivation.serverGameTime();
			}
		} else {
			enlightenedAt = -1L;
		}

		double flySeconds = flySeconds(data);
		if (!Double.isNaN(flySeconds)) {
			for (int mark : FLY_CHIMES) {
				if (lastFlySeconds > mark && flySeconds <= mark) {
					minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), mark <= 3 ? 1.8F : 1.4F, 0.5F));
				}
			}
			lastFlySeconds = flySeconds;
		} else {
			lastFlySeconds = Double.MAX_VALUE;
		}

		if (EffectQuality.cooldownPing()) {
			pingCooldowns(minecraft, data);
		} else {
			COOLDOWNS.clear();
		}
	}

	/** Seconds of flight the qi still pays for, or NaN when not flying or when regeneration covers the drain. */
	private static double flySeconds(CultivationData data) {
		if (!data.flyingSword()) {
			return Double.NaN;
		}
		double net = FlyingSword.drainPerSecond(data) - Qi.regenPerSecond(data);
		if (net <= 0.01D) {
			return Double.NaN;
		}
		return data.qi() / Math.max(0.01D, net);
	}

	/** A chime (and a flash on the HUD seal) when a cooldown of 10 s or more of the selected seal or a quick seal ends. */
	private static void pingCooldowns(Minecraft minecraft, CultivationData data) {
		List<String> ids = new ArrayList<>();
		if (!data.selectedId().isEmpty()) {
			ids.add(data.selectedId());
		}
		String[] slots = TeknikSession.slots();
		for (int i = 0; i < Math.min(4, slots.length); i++) {
			if (!slots[i].isEmpty() && !ids.contains(slots[i])) {
				ids.add(slots[i]);
			}
		}
		boolean ping = false;
		for (String id : ids) {
			TechniqueSpec spec = TechniqueRegistry.byId(id);
			if (spec == null) {
				continue;
			}
			long left = SpecInfo.cooldownLeft(spec);
			Long before = COOLDOWNS.put(id, left);
			if (before != null && before > 0L && left <= 0L && SpecInfo.cooldown(data, spec) >= PING_COOLDOWN_TICKS) {
				ping = true;
				if (id.equals(data.selectedId())) {
					pingFlashUntil = Util.getMillis() + PING_FLASH_MILLIS;
				}
			}
		}
		COOLDOWNS.keySet().retainAll(ids);
		if (ping) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.6F));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		long nowMillis = Util.getMillis();
		float seconds = lastFrame == 0L ? 0.0F : Math.max(0.0F, Math.min(0.25F, (nowMillis - lastFrame) / 1000.0F));
		lastFrame = nowMillis;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || minecraft.getDebugOverlay().showDebugScreen()
				|| !EffectQuality.hudVisible() || !ClientCultivation.received() || minecraft.gui.screen() instanceof ChatScreen
				|| dev.kultivator.client.epic.EpicDirector.recordHide()) {
			return;
		}
		CultivationData data = ClientCultivation.data();
		Rank rank = data.rank();
		Font font = minecraft.font;
		int height = g.guiHeight();
		int width = g.guiWidth();
		float cx = 20.0F;
		float cy = height - 20.0F;

		int step = rank.step();
		if (lastStep >= 0 && step != lastStep) {
			bar.set(0.0F);
		}
		lastStep = step;
		double maxQi = Math.max(1.0D, Qi.max(data));
		float qiShown = qi.to((float) Math.min(data.qi(), maxQi), seconds);
		float barShown = bar.to((float) data.barFraction(), seconds);
		double required = Math.max(1.0D, rank.xpRequired());
		float storedShown = stored.to((float) Math.min(1.0D, data.storedQi() / required), seconds);
		float storedNumber = storedAmount.to((float) data.storedQi(), seconds);

		drawOrb(g, data, rank, cx, cy, (float) (qiShown / maxQi), barShown, storedShown, nowMillis);

		// Everything stays left of the offhand slot (on the left for right-handed players), and for left-handed players
		// left of the hotbar attack indicator when it is shown there.
		boolean right = minecraft.player.getMainArm() == HumanoidArm.RIGHT;
		int limit = right ? width / 2 - 91 - 29 - 4
				: minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR ? width / 2 - 91 - 22 - 4 : width / 2 - 91 - 4;
		int textX = 40;
		TechniqueSpec selected = data.selectedId().isEmpty() ? null : TechniqueRegistry.byId(data.selectedId());
		if (selected != null && 40 + 18 <= limit) {
			drawSeal(g, font, data, selected, 40, Math.round(cy) - 13, deltaTracker.getGameTimeDeltaPartialTick(false));
			textX = 62;
		} else if (TeknikSession.breakerOn() && 40 + 18 <= limit) {
			kehancuranTag(g, font, 49.0F, Math.round(cy) - 3.0F);
			textX = 62;
		}
		drawLasting(g, nowMillis, height);
		drawCloudPips(g, minecraft.player, data, height);
		int room = limit - textX;
		if (room >= 24) {
			drawText(g, font, data, rank, textX, room, cy, height, qiShown, maxQi, barShown, storedNumber, nowMillis);
		}
	}

	/** Root tested and revealed (not still inside its testing ceremony). */
	private static boolean rootKnown(CultivationData data) {
		return data.rootTested() && !SpiritRoots.revealPending(data);
	}

	// ================================================================== orb

	private static void drawOrb(GuiGraphicsExtractor g, CultivationData data, Rank rank, float cx, float cy, float qiFraction, float barFraction,
								float storedFraction, long now) {
		float r = ORB;
		// The root is saved when its testing ceremony starts: keep the orb pale until the reveal.
		boolean known = rootKnown(data);
		int root = Palette.opaque(Element.mainColor(known ? data.rootElements() : 0));
		// A Mortal whose root is not known yet is not ready: the root test (and its reveal) comes first.
		boolean ready = data.barFull() && rank.nextIsBreakthrough() && !(rank.realm().isMortal() && !known);
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 240.0D);

		Brush.disc(g, cx, cy, r + 4.2F, 0x70101824);
		if (ready) {
			Brush.halo(g, cx, cy, r + 3.4F, 3.0F, 0xFFE07A, pulse);
		}
		// Glass and the qi inside, rising from the bottom.
		Brush.disc(g, cx, cy, r, 0xE0141E2C);
		float level = Math.max(0.0F, Math.min(1.0F, qiFraction));
		float surface = r - 2.0F * r * level;
		if (level > 0.0F) {
			Brush.band(g, cx, cy, r - 0.5F, surface, r, Palette.lighten(root, 0.1F), Palette.darken(root, 0.5F));
			// The shimmering surface line.
			if (level < 0.995F) {
				int shine = Palette.lighten(root, 0.65F);
				float halfChord = (float) Math.sqrt(Math.max(0.0F, (r - 0.8F) * (r - 0.8F) - surface * surface));
				for (float sx = -halfChord; sx < halfChord; sx += 1.0F) {
					float wave = (float) Math.sin(sx * 0.8F + now / 180.0D) * 0.45F;
					int alpha = Math.round(150 + 90 * (float) Math.sin(sx * 0.5F - now / 260.0D));
					Brush.rect(g, cx + sx, cy + surface + wave - 0.5F, cx + sx + 1.0F, cy + surface + wave + 0.5F, Palette.argb(alpha, shine));
				}
			}
		}
		// A glint on the glass.
		Brush.disc(g, cx - r * 0.38F, cy - r * 0.42F, r * 0.2F, 0x50FFFFFF);
		Brush.disc(g, cx - r * 0.18F, cy - r * 0.6F, r * 0.08F, 0x40FFFFFF);

		// The gold ring: cultivation, clockwise from the top.
		int gold = ready ? Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse) : Palette.GOLD;
		Brush.arc(g, cx, cy, r + 0.8F, r + 2.8F, 0.0F, 1.0F, 0xFF3A2D12);
		Brush.arc(g, cx, cy, r + 0.8F, r + 2.8F, 0.0F, barFraction, gold);
		if (barFraction > 0.01F && barFraction < 0.999F) {
			double angle = -Math.PI / 2.0D + barFraction * Math.PI * 2.0D;
			Brush.dot(g, cx + (float) Math.cos(angle) * (r + 1.8F), cy + (float) Math.sin(angle) * (r + 1.8F), 1.3F, Palette.GOLD_LIGHT);
		}

		// Perisai Qi: a white-jade arc between the gold ring and the beads (pool / its maximum, DESIGN 22.12.8).
		double shield = TeknikSession.shield();
		if (shield > 0.01D) {
			double max = TeknikSession.shieldMax();
			if (max <= 0.0D) {
				Minecraft minecraft = Minecraft.getInstance();
				max = minecraft.player == null ? shield : Math.max(shield, minecraft.player.getMaxHealth() * 2.0D);
			}
			float share = (float) Math.max(0.0D, Math.min(1.0D, shield / Math.max(0.01D, max)));
			Brush.arc(g, cx, cy, r + 3.0F, r + 4.1F, 0.0F, share, 0xFFE4F4EC);
		}

		// Violet beads while Stored Qi waits (lit share = stored / one full bar), turning slowly.
		if (data.storedQi() > 0.5D) {
			int lit = Math.max(1, (int) Math.ceil(STORED_BEADS * storedFraction));
			double turn = (now % 12000L) / 12000.0D * Math.PI * 2.0D;
			float beadPulse = 0.6F + 0.4F * (float) Math.sin(now / 300.0D);
			for (int i = 0; i < STORED_BEADS; i++) {
				double angle = -Math.PI / 2.0D + turn + i * Math.PI * 2.0D / STORED_BEADS;
				float bx = cx + (float) Math.cos(angle) * (r + 5.2F);
				float by = cy + (float) Math.sin(angle) * (r + 5.2F);
				int color = i < lit ? Palette.argb(Math.round(255 * beadPulse), Palette.STORED_LIGHT) : Palette.argb(0x55, Palette.STORED);
				Brush.dot(g, bx, by, i < lit ? 1.1F : 0.8F, color);
			}
		}
	}

	// ================================================================== technique seal

	/**
	 * The selected spec as a seal tile (DESIGN 22.12.8): glyph, grade ribbon, cooldown sweep, four mastery beads, the qi
	 * cost after mastery and the realm gap, charge pips above it and the red KEHANCURAN tag under it while the switch is on.
	 */
	private static void drawSeal(GuiGraphicsExtractor g, Font font, CultivationData data, TechniqueSpec spec, int x, int y, float partialTick) {
		int size = 18;
		int qi = SpecInfo.qi(data, spec);
		boolean affordable = data.qi() + 1.0E-6D >= qi;
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
			Brush.centered(g, font, secs, x + size / 2.0F, y + size / 2.0F - 2.5F, 0xFFFFFFFF, 0.6F, true);
		}
		long flash = pingFlashUntil - Util.getMillis();
		if (flash > 0L) {
			// The cooldown ping (DESIGN 19.3.15): a short gold flash over the seal.
			int alpha = Math.round(160 * flash / (float) PING_FLASH_MILLIS);
			g.fill(x - 1, y - 1, x + size + 1, y + size + 1, Palette.argb(alpha, 0xFFE07A));
		}
		Component cost = Component.literal(SpecInfo.compact(qi));
		Brush.centered(g, font, cost, x + size / 2.0F, y + size + 2.0F, affordable ? Palette.QI_LIGHT : 0xFFFF7A6A, 0.6F, true);
		CompoundTag charges = TeknikSession.charges(spec.id());
		if (charges != null) {
			int max = Math.max(1, Math.min(8, charges.getIntOr("max", 1)));
			int have = Math.max(0, Math.min(max, charges.getIntOr("n", max)));
			for (int i = 0; i < max; i++) {
				Brush.dot(g, x + size / 2.0F + (i - (max - 1) / 2.0F) * 3.0F, y - 2.5F, 0.9F, i < have ? Palette.QI_LIGHT : Palette.argb(0x70, 0x3A4A5A));
			}
		}
		if (TeknikSession.breakerOn()) {
			kehancuranTag(g, font, x + size / 2.0F, y + size + 7.5F);
		}
	}

	/** The red "KEHANCURAN" tag (Mode Kehancuran on and allowed). */
	private static void kehancuranTag(GuiGraphicsExtractor g, Font font, float cx, float y) {
		float pulse = 0.6F + 0.4F * (float) Math.sin(Util.getMillis() / 250.0D);
		Component tag = Component.translatable("kultivator.codex.hud.kehancuran").withStyle(net.minecraft.ChatFormatting.BOLD);
		Brush.centered(g, font, tag, cx, y, Palette.mix(0xFFB3281D, 0xFFFF5B3A, pulse), 0.42F, true);
	}

	/** Countdown arcs for forms, domains, auras, toggles and fields above the orb, in their Dao colour. */
	private static void drawLasting(GuiGraphicsExtractor g, long now, int height) {
		List<TeknikSession.Lasting> lasting = TeknikSession.lasting();
		if (lasting.isEmpty()) {
			return;
		}
		long time = ClientCultivation.serverGameTime();
		int shown = 0;
		for (TeknikSession.Lasting entry : lasting) {
			if (shown >= 5) {
				break;
			}
			TechniqueSpec spec = TechniqueRegistry.byId(entry.id());
			int color = spec == null || spec.dao() == null ? Palette.GOLD : Palette.opaque(spec.dao().color());
			float cx = 6.0F + shown * 8.0F;
			float cy = height - 40.0F;
			Brush.disc(g, cx, cy, 3.4F, 0xA0101824);
			if (entry.end() <= 0L) {
				float pulse = 0.5F + 0.5F * (float) Math.sin(now / 300.0D);
				Brush.arc(g, cx, cy, 2.2F, 3.4F, 0.0F, 1.0F, Palette.mix(color, 0xFFFFFFFF, 0.3F * pulse));
			} else {
				float share = Math.max(0.0F, Math.min(1.0F, (entry.end() - time) / (float) Math.max(1L, entry.end() - entry.start())));
				Brush.arc(g, cx, cy, 2.2F, 3.4F, 0.0F, share, color);
			}
			Brush.dot(g, cx, cy, 0.8F, color);
			shown++;
		}
	}

	/** Cloud Step pips above the orb while airborne: one small cloud per air jump left of {@code CloudStep.maxJumps}. */
	private static void drawCloudPips(GuiGraphicsExtractor g, @Nullable LocalPlayer player, CultivationData data, int height) {
		boolean demo = HudDemo.active();
		if (player == null || !demo && (airTicks <= 2 || !data.knows(Technique.CLOUD_STEP))) {
			return;
		}
		int max = demo ? 6 : CloudStep.maxJumps(data.realm());
		int left = demo ? 6 - Math.round(HudDemo.progress() * 3.0F) : Math.max(0, max - jumpsUsed);
		for (int i = 0; i < max; i++) {
			float cx = 7.0F + i * 6.5F;
			float cy = height - 49.0F;
			int color = i < left ? 0xF0F4FAFF : 0x50A0B0C0;
			Brush.disc(g, cx - 1.3F, cy + 0.4F, 1.5F, color);
			Brush.disc(g, cx + 1.3F, cy + 0.4F, 1.5F, color);
			Brush.disc(g, cx, cy - 0.7F, 1.8F, color);
		}
	}

	// ================================================================== text

	private static void drawText(GuiGraphicsExtractor g, Font font, CultivationData data, Rank rank, int x, int room, float cy, int height,
								 float qiShown, double maxQi, float barShown, float storedNumber, long now) {
		List<Line> lines = new ArrayList<>();
		Line storedLine = null;
		Line toxicityLine = null;
		Line meditatingLine = null;
		Line veinLine = null;
		Line enlightenLine = null;
		boolean demo = HudDemo.active();
		CompoundTag session = data.sessionExtra();
		addRankLines(lines, font, rank, room);
		int rankLines = lines.size();
		boolean untested = rank.realm().isMortal() && !data.rootTested();
		boolean unknown = rank.realm().isMortal() && !rootKnown(data);
		boolean narrow = room < 100;
		float pulse = 0.5F + 0.5F * (float) Math.sin(now / 200.0D);
		if (untested) {
			Component text = Component.translatable("kultivator.hud.hint_root", Keys.INFO.getTranslatedKeyMessage());
			List<FormattedCharSequence> hint = font.split(text, Math.max(10, (int) (room / 0.75F)));
			int hintColor = Palette.mix(Palette.JADE_PALE, Palette.GOLD_LIGHT, pulse * 0.5F);
			for (int i = 0; i < Math.min(2, hint.size()); i++) {
				lines.add(new Line(null, hint.get(i), hintColor, 0.75F, -1));
			}
		} else {
			MutableComponent qiLine = Component.translatable("kultivator.hud.qi", Msg.number(qiShown), Msg.number(maxQi)).withColor(Palette.QI_LIGHT);
			if (!narrow) {
				qiLine.append(Component.literal("  " + Math.round(barShown * 100.0F) + "%").withColor(Palette.GOLD));
			}
			int stars = demo ? 9 : ImmortalPerks.stars(data);
			if (stars > 0) {
				qiLine.append(Component.literal("  ★" + stars).withColor(Palette.GOLD_LIGHT));
			}
			lines.add(Line.of(qiLine, Palette.QI_LIGHT, 0.75F));
			if (data.storedQi() > 0.5D) {
				storedLine = Line.of(Component.translatable(rank.isMax() ? "kultivator.hud.stored_immortal" : "kultivator.hud.stored",
						Msg.number(storedNumber)), Palette.STORED_LIGHT, 0.75F);
				lines.add(storedLine);
			}
		}
		if (data.breakingThrough()) {
			lines.add(Line.of(Component.translatable("kultivator.hud.breaking_through"), Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse), 0.75F));
		} else if (data.barFull() && rank.nextIsBreakthrough() && !unknown) {
			lines.add(Line.of(Component.translatable("kultivator.hud.ready"), Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse), 0.75F));
		}
		// The flying-sword gauge: seconds of flight the qi still pays for.
		double fly = demo ? 42.0D - 41.0D * HudDemo.progress() : flySeconds(data);
		if (!Double.isNaN(fly)) {
			int color = fly < FLY_RED ? Palette.mix(0xFFFF4A3A, 0xFFFF9A8A, pulse) : fly < FLY_AMBER ? 0xFFFFB347 : Palette.JADE_PALE;
			Component text = fly < FLY_LAND ? Component.translatable("kultivator.goals.hud.fly_land")
					: Component.translatable("kultivator.goals.hud.fly", Math.round(fly));
			lines.add(Line.of(text, color, 0.75F));
		}
		int deviation = deviationTicksNow();
		if (deviation > 0) {
			int seconds = deviation / 20;
			String clock = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
			lines.add(Line.of(Component.translatable("kultivator.hud.deviation", clock), Palette.mix(0xFFFF4A3A, 0xFFFF9A8A, pulse), 0.75F));
		}
		// The enlightenment timer.
		if (demo || data.enlightened() && enlightenedAt >= 0L) {
			long left = demo ? 140L : Math.max(0L, Enlightenment.DURATION_TICKS - (ClientCultivation.serverGameTime() - enlightenedAt));
			enlightenLine = Line.of(Component.translatable("kultivator.goals.hud.enlightenment", clock(left)),
					Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse), 0.75F);
			lines.add(enlightenLine);
		}
		// Toxicity from 40 (amber), a warning above 80.
		double toxicity = demo ? 55.0D : data.toxicity();
		if (toxicity >= TOXICITY_SHOWN) {
			boolean warn = toxicity > TOXICITY_WARN;
			toxicityLine = Line.of(Component.translatable(warn ? "kultivator.hud.toxicity" : "kultivator.goals.hud.toxicity", Msg.number(toxicity)),
					warn ? Palette.WARN : 0xFFE0C070, 0.75F);
			lines.add(toxicityLine);
		}
		if (data.meditating() || demo) {
			MutableComponent line = Component.translatable("kultivator.hud.meditating", multiplier(demo ? 1.5D : data.siteMultiplier()))
					.append(Component.literal(" · "))
					.append(Component.translatable("kultivator.hud.density", Math.round((demo ? 1.8D : data.veinDensity()) * 100.0D)));
			int grade = demo ? 2 : session.getIntOr("vein_grade", 0);
			if (grade != 0) {
				line.append(Component.literal(" · ")).append(Component.translatable("kultivator.goals.hud.vein." + (grade >= 2 ? "great" : grade == 1
						? "rich" : "thin")));
			}
			if (data.formationTier() > 0) {
				line.append(Component.literal(" · ")).append(Component.translatable("kultivator.hud.formation." + Math.min(3, data.formationTier())));
			}
			meditatingLine = Line.of(line, Palette.JADE_PALE, 0.75F);
			lines.add(meditatingLine);
			// Closed-door seclusion and the spirit stone burning as fuel.
			MutableComponent extra = Component.empty();
			boolean any = false;
			int seclusion = demo ? 3 : session.getIntOr("seclusion", 0);
			if (seclusion > 0) {
				extra.append(Component.translatable("kultivator.goals.hud.seclusion", seclusion * 5));
				any = true;
			}
			int fuel = demo ? 2 : session.getIntOr("fuel", -1);
			long fuelLeft = demo ? 480L : session.getLongOr("fuel_end", 0L) - ClientCultivation.serverGameTime();
			if (fuel >= 0 && fuelLeft > 0L) {
				if (any) {
					extra.append(Component.literal(" · "));
				}
				extra.append(Component.literal("◆ ").withColor(STONE_COLORS[Math.min(STONE_COLORS.length - 1, fuel)]))
						.append(Component.literal(clock(fuelLeft)));
				any = true;
			}
			// The Cave Abode (1 bonuses act, −1 near the seat but unprotected) and a running Perkokoh Fondasi ritual.
			int abode = demo ? 1 : session.getIntOr("abode", 0);
			if (abode != 0) {
				if (any) {
					extra.append(Component.literal(" · "));
				}
				extra.append(Component.translatable(abode > 0 ? "kultivator.goals.hud.abode" : "kultivator.goals.hud.abode_unprotected")
						.withColor(abode > 0 ? Palette.JADE_LIGHT : 0xFFFFB347));
				any = true;
			}
			long reforgeLeft = session.getLongOr("reforge_end", 0L) - ClientCultivation.serverGameTime();
			if (reforgeLeft > 0L) {
				if (any) {
					extra.append(Component.literal(" · "));
				}
				extra.append(Component.translatable("kultivator.goals.hud.reforge", clock(reforgeLeft)).withColor(Palette.GOLD_LIGHT));
				any = true;
			}
			if (any) {
				veinLine = Line.of(extra, Palette.JADE_PALE, 0.7F);
				lines.add(veinLine);
			}
		}

		// The vanilla chat starts 40 px above the bottom: keep the block below it by dropping the least important lines
		// (the phase-7 vein line first, then the enlightenment timer, the meditation details, Stored Qi, toxicity).
		float total = linesHeight(lines);
		for (Line droppable : new Line[]{veinLine, enlightenLine, meditatingLine, storedLine, toxicityLine}) {
			if (total <= MAX_TEXT_HEIGHT) {
				break;
			}
			if (droppable != null && lines.remove(droppable)) {
				total = linesHeight(lines);
			}
		}
		float y = total <= 34.0F ? cy - total / 2.0F : height - 3.0F - total;
		int[] dots = dailyDots(data, demo);
		for (int i = 0; i < lines.size(); i++) {
			Line line = lines.get(i);
			float drawn;
			if (line.text() != null) {
				drawn = Brush.fit(g, font, line.text(), x, y, room, line.color(), line.scale(), line.scale() * 0.65F, true);
			} else if (line.row() != null) {
				Brush.text(g, font, line.row(), x, y, line.color(), line.scale(), true);
				drawn = font.width(line.row()) * line.scale();
			} else {
				drawn = 0.0F;
			}
			float end = drawn;
			if (line.pips() >= 0 && drawn + PIP_ROOM <= room) {
				pips(g, x + drawn + 3.0F, y + 3.5F * line.scale(), line.pips(), line.color());
				end += PIP_ROOM;
			}
			// The three daily-chore dots beside the realm name (DESIGN 19.3.9 c).
			if (dots != null && i == rankLines - 1 && end + 14.0F <= room) {
				for (int d = 0; d < dots[0]; d++) {
					boolean done = (dots[1] & (1 << d)) != 0;
					Brush.dot(g, x + end + 5.0F + d * 3.6F, y + 3.5F * line.scale(), done ? 1.2F : 0.9F, done ? Palette.GOLD_LIGHT : 0x90A0B0A8);
				}
			}
			y += 9.0F * line.scale();
		}
	}

	/** {count, done bits} of today's chores, or null when there are none or the server switched them off. */
	private static @Nullable int[] dailyDots(CultivationData data, boolean demo) {
		if (demo) {
			return new int[]{3, 3};
		}
		if (!GoalsClient.cfg("daily", true)) {
			return null;
		}
		CompoundTag daily = DailyChores.view(data);
		ListTag chores = daily.getListOrEmpty("chores");
		return chores.isEmpty() ? null : new int[]{Math.min(3, chores.size()), daily.getIntOr("done", 0)};
	}

	/** Ticks as m:ss. */
	private static String clock(long ticks) {
		long seconds = Math.max(0L, ticks) / 20L;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	/**
	 * The realm and stage: the full "Golden Core, Late" when it fits, else the realm name with four stage pips, else the
	 * realm name over two small lines (narrow windows, long names).
	 */
	private static void addRankLines(List<Line> lines, Font font, Rank rank, int room) {
		int color = Palette.opaque(rank.realm().color());
		Component full = rank.displayName();
		float fullScale = Brush.fitScale(font, full, room, 1.0F, 0.5F);
		if (fullScale >= 0.7F || rank.realm().isMortal()) {
			lines.add(Line.of(full, color, Math.max(0.6F, fullScale)));
			return;
		}
		Component realm = rank.realm().layers() > 1
				? Component.translatable("kultivator.hud.realm_layer", rank.realm().displayName(), rank.layer() + 1)
				: rank.realm().displayName();
		float realmScale = Brush.fitScale(font, realm, room - PIP_ROOM, 0.85F, 0.5F);
		if (realmScale >= 0.6F) {
			lines.add(new Line(realm, null, color, realmScale, rank.stage()));
			return;
		}
		List<FormattedCharSequence> rows = font.split(realm, Math.max(10, (int) (room / 0.65F)));
		int count = Math.min(2, rows.size());
		for (int i = 0; i < count; i++) {
			lines.add(new Line(null, rows.get(i), color, 0.65F, i == count - 1 ? rank.stage() : -1));
		}
	}

	private static float linesHeight(List<Line> lines) {
		float total = 0.0F;
		for (Line line : lines) {
			total += 9.0F * line.scale();
		}
		return total;
	}

	/** Four small diamonds after the realm name: how many stages of it are reached. */
	private static void pips(GuiGraphicsExtractor g, float x, float cy, int stage, int color) {
		for (int i = 0; i < 4; i++) {
			g.pose().pushMatrix();
			g.pose().translate(x + 1.5F + i * 3.6F, cy);
			g.pose().rotate((float) (Math.PI / 4.0D));
			Brush.rect(g, -1.1F, -1.1F, 1.1F, 1.1F, i <= stage ? color : Palette.argb(0x60, color));
			g.pose().popMatrix();
		}
	}

	/** "×1.5", "×2.25". */
	private static String multiplier(double value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
			text = text.substring(0, text.length() - 1);
		}
		return "×" + text;
	}
}
