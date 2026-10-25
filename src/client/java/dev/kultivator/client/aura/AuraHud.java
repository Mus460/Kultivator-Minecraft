package dev.kultivator.client.aura;

import dev.kultivator.client.epic.film.FilmDirector;
import dev.kultivator.client.fx.spec.ScreenFlash;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Your own Aura Kultivasi seen through your own eyes (DESIGN 31.2.7): no world geometry, only a soft glow along the
 * screen edges, a vignette of four gradient strips at most 0.16 × the fade strong, in the realm's body colour mixed 35 %
 * with the Dao tint, breathing by alpha over 80 ticks; a full bottleneck pulses the edges amber (31.3). Hidden with F1,
 * while a film plays, in spectator and when the aura is off. Unaffected by shader packs (a HUD fill). Drawn by the one
 * edge painter {@link ScreenFlash#tepi} (DESIGN 43.9): a stronger edge (danger, red sky) wins over it.
 */
final class AuraHud implements HudElement {

	private static final float MAX_ALPHA = 0.16F;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || minecraft.player.isSpectator()
				|| AuraSettings.mode() == AuraSettings.Mode.MATI || FilmDirector.playing()) {
			return;
		}
		AuraRig rig = AuraEngine.selfView();
		if (rig == null || rig.subject == null) {
			return;
		}
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		float fade = rig.fade(partial);
		if (fade <= 0.01F) {
			return;
		}
		AuraSubject s = rig.subject;
		AuraColors.Palette palette = s.colors();
		float t = (float) (Math.floorMod(rig.clock(), AuraRig.CYCLE)) + partial;
		float breath = 0.7F + 0.3F * (float) (0.5D - 0.5D * Math.cos(Math.PI * 2.0D * (t % 80.0F) / 80.0D));
		float alpha = MAX_ALPHA * fade * breath;
		int rgb = palette.tinted(0.35F);
		if (s.bottleneck()) {
			float pulse = (float) ((1.0D - Math.cos(Math.PI * 2.0D * (t % 30.0F) / 30.0D)) / 2.0D);
			rgb = AuraColors.mix(rgb, palette.warn(pulse), 0.55F + 0.35F * pulse);
			alpha = Math.min(MAX_ALPHA, alpha * (0.85F + 0.3F * pulse));
		}
		ScreenFlash.tepi(rgb, alpha);
	}
}
