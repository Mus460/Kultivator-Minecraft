package dev.kultivator.client.duel.ui;

import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The pop-up of a duel challenge (DESIGN 35.2.2): a lacquered slip that slides in on the right of the screen with a
 * vermilion 決 seal, who challenges (name, realm and title), the pair, the rules in one line, the stake (with the
 * challenger's jade slip by name), a burning incense line for the time left, and where to answer: the [Terima] [Tolak]
 * chips of the chat line that came with it, or Hub › Dunia › Duel Pedang. A soft bell rings once. One offer at a time
 * (the newest); it slides out when answered, withdrawn or lapsed. Drawn by {@link DuelHud} (hidden with F1).
 */
public final class ChallengeOfferToast {

	private static final int W = 176;
	private static final int H = 62;
	private static final int SLIDE = 8;

	private static @Nullable DuelPayloads.DuelChallengeOffer offer;
	private static long shownAt;
	private static long goneAt = -1L;
	private static long ticks;

	/** A new challenge: shown at once with a bell. */
	public static void show(DuelPayloads.DuelChallengeOffer p) {
		offer = p;
		shownAt = ticks;
		goneAt = -1L;
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 1.5F, 0.35F));
		UiSound.ask();
	}

	/** The challenge was answered or withdrawn: slide out. */
	public static void dismiss(int challengeId) {
		DuelPayloads.DuelChallengeOffer current = offer;
		if (current != null && current.challengeId() == challengeId && goneAt < 0L) {
			goneAt = ticks;
		}
	}

	public static void tick() {
		ticks++;
		DuelPayloads.DuelChallengeOffer current = offer;
		if (current == null) {
			return;
		}
		if (goneAt < 0L && left(current) <= 0) {
			goneAt = ticks;
		}
		// The offer is gone from the client's list (answered from the chat or the duel screen): slide out.
		if (goneAt < 0L && ticks - shownAt > 2L && DuelClient.offers().stream().noneMatch(o -> o.offer().challengeId() == current.challengeId())) {
			goneAt = ticks;
		}
		if (goneAt >= 0L && ticks - goneAt > SLIDE) {
			offer = null;
		}
	}

	public static void clear() {
		offer = null;
		goneAt = -1L;
	}

	private static int left(DuelPayloads.DuelChallengeOffer p) {
		for (DuelClient.Offer o : DuelClient.offers()) {
			if (o.offer().challengeId() == p.challengeId()) {
				return o.ticksLeft();
			}
		}
		return (int) Math.max(0L, p.expiresTicks() - (ticks - shownAt));
	}

	/** Drawn by the HUD. */
	static void draw(GuiGraphicsExtractor g, Font font, int width, int height, float partial) {
		DuelPayloads.DuelChallengeOffer p = offer;
		if (p == null) {
			return;
		}
		float in = Mth.clamp((ticks - shownAt + partial) / SLIDE, 0.0F, 1.0F);
		float out = goneAt < 0L ? 0.0F : Mth.clamp((ticks - goneAt + partial) / SLIDE, 0.0F, 1.0F);
		float slide = easeOut(in) * (1.0F - easeOut(out));
		if (slide <= 0.01F) {
			return;
		}
		int x0 = Math.round(width - (W + 6) * slide);
		int y0 = Math.max(30, Math.round(height * 0.26F));
		int x1 = x0 + W;
		int y1 = y0 + H;
		// lacquer slip with a gold rim and a paper inlay
		Brush.chamferGradient(g, x0, y0, x1, y1, 4, Palette.argb(235, 0x2A1610), Palette.argb(235, 0x140A06));
		g.fill(x0 + 3, y0 + 1, x1 - 3, y0 + 2, Palette.argb(200, 0xD4A637));
		Brush.frame(g, x0 + 2, y0 + 2, x1 - 2, y1 - 2, Palette.argb(140, 0xD4A637));
		// the 決 seal
		DuelHud.seal(g, x0 + 18.0F, y0 + 20.0F, 0.75F, true, "決", partial);
		float tx = x0 + 34.0F;
		float tw = W - 40.0F;
		Brush.fit(g, font, Component.translatable("kultivator.duel.ui.offer.title", Component.translatable("kultivator.duel.ui.pair." + p.pair())),
				tx, y0 + 5.0F, tw, Palette.argb(230, 0xF1D57E), 0.75F, 0.5F, true);
		Brush.fit(g, font, p.name(), tx, y0 + 14.0F, tw, Palette.argb(255, 0xFFE7B0), 0.9F, 0.55F, true);
		Brush.fit(g, font, p.rankLine(), tx, y0 + 24.0F, tw, Palette.argb(200, 0xD8CCB0), 0.6F, 0.45F, true);
		Brush.fit(g, font, rulesLine(p.rules()), x0 + 6.0F, y0 + 34.0F, W - 12.0F, Palette.argb(220, 0xE8DCC0), 0.6F, 0.42F, true);
		Brush.fit(g, font, stakeLine(p), x0 + 6.0F, y0 + 42.0F, W - 12.0F, Palette.argb(230, 0xFFC870), 0.6F, 0.42F, true);
		// the incense line: the time left
		float share = Mth.clamp(left(p) / (float) Math.max(1, p.expiresTicks()), 0.0F, 1.0F);
		int barW = W - 12;
		int bx = x0 + 6;
		int by = y1 - 11;
		g.fill(bx, by, bx + barW, by + 1, Palette.argb(120, 0x5A3A20));
		int lit = Math.round(barW * share);
		g.fill(bx, by, bx + lit, by + 1, Palette.argb(220, 0xC8A060));
		g.fill(bx + lit - 1, by - 1, bx + lit + 1, by + 2, Palette.argb(Math.round(200 + 55 * Mth.sin(ticks * 0.6F)), 0xFF7A20));
		Brush.fit(g, font, Component.translatable("kultivator.duel.ui.offer.hint", left(p) / 20), x0 + 6.0F, by + 3.0F, W - 12.0F,
				Palette.argb(170, 0xD8CCB0), 0.5F, 0.4F, true);
	}

	/** "Bebas · Pedang saja · Tebasan Penentu · Gelanggang 9" (shared with the duel screen). */
	static Component rulesLine(DuelPayloads.RuleBytes rules) {
		MutableComponent line = Component.translatable("kultivator.duel.ui.rule.realm." + Mth.clamp(rules.realmRule(), 0, 2)).copy();
		line.append(" · ").append(Component.translatable("kultivator.duel.ui.rule.jurus." + Mth.clamp(rules.jurusRule(), 0, 1)));
		line.append(" · ").append(Component.translatable("kultivator.duel.ui.rule.win." + Mth.clamp(rules.winRule(), 0, 1)));
		line.append(" · ").append(Component.translatable("kultivator.duel.ui.rule.radius", rules.radius()));
		if ((rules.flags() & DuelIds.RF_INTRO_FILM) != 0) {
			line.append(" · ").append(Component.translatable("kultivator.duel.ui.rule.film"));
		}
		return line;
	}

	/** The stake in words. */
	static Component stakeLine(DuelPayloads.DuelChallengeOffer p) {
		return switch (p.stakeKind()) {
			case DuelIds.STAKE_BATU_ROH -> Component.translatable("kultivator.duel.ui.stake.line.stones", p.stakeCount(),
					Component.translatable("kultivator.duel.ui.grade." + Mth.clamp(p.stakeGrade(), 0, 3)));
			case DuelIds.STAKE_GULUNGAN -> Component.translatable("kultivator.duel.ui.stake.line.slip", p.slipName());
			case DuelIds.STAKE_KEHORMATAN -> Component.translatable("kultivator.duel.ui.stake.line.honour");
			default -> Component.translatable("kultivator.duel.ui.stake.line.none");
		};
	}

	private static float easeOut(float t) {
		float u = 1.0F - t;
		return 1.0F - u * u * u;
	}

	private ChallengeOfferToast() {
	}
}
