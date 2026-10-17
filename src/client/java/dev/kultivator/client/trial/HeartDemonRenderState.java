package dev.kultivator.client.trial;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;

/** Player-like render state of a heart demon: the owner's skin texture and arm model, plus the dark palm wind-up. */
public class HeartDemonRenderState extends AvatarRenderState {
	public Identifier texture = DefaultPlayerSkin.getDefaultTexture();
	public boolean slim;
	public boolean chargingPalm;
}
