package dev.kultivator.client.duel.ui;

import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.hud.Kabar;
import dev.kultivator.client.screen.ui.KeyHints;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The notice of a duel challenge (DESIGN 35.2.2), a card of the single notice slot {@link Kabar} (DESIGN 43.9, G27/G50):
 * the vermilion 決 seal, who challenges (name, realm and title), the pair, the rules in one line, the stake (with the
 * challenger's jade slip by name), a thin line for the time left, and where to answer: the [Terima] [Tolak] chips of the
 * chat line that came with it, or V › Dunia › Duel. A soft bell rings once. One offer at a time (the newest); it leaves
 * when answered, withdrawn or lapsed.
 */
public final class ChallengeOfferToast {

	private static final String KUNCI = "duel_tawaran";

	private static @Nullable DuelPayloads.DuelChallengeOffer offer;
	private static long shownAt;
	private static boolean gone;
	private static long ticks;

	/** A new challenge: shown at once with a bell. */
	public static void show(DuelPayloads.DuelChallengeOffer p) {
		offer = p;
		shownAt = ticks;
		gone = false;
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 1.5F, 0.35F));
		UiSound.ask();
		Component title = Component.translatable("kultivator.duel.ui.offer.title", Component.translatable("kultivator.duel.ui.pair." + p.pair()));
		Kabar.kirim(Kabar.kartu(title, p.name()).ikon(Kabar.segel("決", Palette.SEAL)).penting(true)
				.baris(List.of(p.rankLine(), rulesLine(p.rules()), stakeLine(p), Component.translatable("kultivator.gui43.m22.duel_jawab", KeyHints.hubKey())))
				.lama(Math.max(1000L, p.expiresTicks() * 50L)).kunci(KUNCI)
				.hidup(() -> offer == p && !gone)
				.sisa(() -> Mth.clamp(left(p) / (float) Math.max(1, p.expiresTicks()), 0.0F, 1.0F)));
	}

	/** The challenge was answered or withdrawn: the card leaves. */
	public static void dismiss(int challengeId) {
		DuelPayloads.DuelChallengeOffer current = offer;
		if (current != null && current.challengeId() == challengeId) {
			gone = true;
		}
	}

	public static void tick() {
		ticks++;
		DuelPayloads.DuelChallengeOffer current = offer;
		if (current == null || gone) {
			return;
		}
		// lapsed, or gone from the client's list (answered from the chat or the duel page)
		if (left(current) <= 0 || ticks - shownAt > 2L && DuelClient.offers().stream().noneMatch(o -> o.offer().challengeId() == current.challengeId())) {
			gone = true;
		}
	}

	public static void clear() {
		offer = null;
		gone = false;
		Kabar.tutup(KUNCI);
	}

	private static int left(DuelPayloads.DuelChallengeOffer p) {
		for (DuelClient.Offer o : DuelClient.offers()) {
			if (o.offer().challengeId() == p.challengeId()) {
				return o.ticksLeft();
			}
		}
		return (int) Math.max(0L, p.expiresTicks() - (ticks - shownAt));
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

	private ChallengeOfferToast() {
	}
}
