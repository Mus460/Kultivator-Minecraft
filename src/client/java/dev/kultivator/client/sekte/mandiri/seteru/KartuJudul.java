package dev.kultivator.client.sekte.mandiri.seteru;

import dev.kultivator.client.epic.screen.TitleCard;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sekte.api.Lambang;
import dev.kultivator.sekte.mandiri.seteru.SeteruIds;
import dev.kultivator.sekte.mandiri.seteru.SeteruPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The title card of an honour duel (DESIGN 39.F.5), shown through the one title card painter {@link TitleCard} (DESIGN
 * 43.9, G28): "<ours> lawan <theirs>" as the big line with the red 鬥 seal and their emblem's symbol as the second seal,
 * and "Perseteruan · <our hall> lawan <their sect> · <cause> · <rung>" under it. It runs while the duel's own intro of
 * DESIGN 35 runs (that intro obeys the camera setting Penuh/Singkat/Mati; this card never moves the camera), follows the
 * setting "Kartu judul" (inside {@link TitleCard#show}), waits its turn behind another full card, and hides with the HUD.
 * The HUD element {@code mandiri_seteru_judul} stays registered and draws nothing of its own.
 */
public final class KartuJudul implements HudElement {

	/** The 32 emblem symbols as seal glyphs (the order of {@code api.Lambang} symbols, as D's screen). */
	private static final String SIMBOL = "剑莲鹤龙凤虎龟山月日云雷火水木金土风星海玉竹梅松霜雪花鼎钟塔天道";

	/** A new card. */
	static void tampil(SeteruPayloads.Judul j) {
		Component sebab = Component.translatable(SeteruIds.k("judul.sebab"), Component.translatable(SeteruIds.k("sebab." + j.sebab())),
				Component.translatable(SeteruIds.k("tingkat." + Math.max(0, Math.min(7, j.tingkat())))));
		Component title = Component.translatable("kultivator.gui43.m22.seteru_judul", j.kita(), j.mereka());
		Component sub = Component.translatable("kultivator.gui43.m22.seteru_sub", j.aulaKita(), j.sekteMereka(), sebab);
		String simbol = String.valueOf(SIMBOL.charAt(Math.floorMod(Lambang.unpack(j.lambang()).symbol(), SIMBOL.length())));
		TitleCard.show(TitleCard.full(title, sub, "鬥", Palette.SEAL & 0xFFFFFF, SeteruIds.JUDUL_DETIK * 20).second(simbol));
	}

	/** Disconnect: {@link TitleCard} clears its own queue. */
	static void hapus() {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		// drawn by TitleCard (DESIGN 43.9)
	}
}
