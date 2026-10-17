package dev.kultivator.client.disciple;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Player-like render state of a disciple: its default skin, synced rank, Dao, root colour, flags and name. */
public class DiscipleRenderState extends AvatarRenderState {
	public Identifier texture = DefaultPlayerSkin.getDefaultTexture();
	public boolean slim;
	public int realm;
	public int layer;
	public int stage;
	/** Dao ordinal, -1 for none. */
	public int dao = -1;
	public int rootColor;
	/** {@link DiscipleLook#MEDITATING} etc. */
	public int flags;
	/** RGB of the robe (Dao colour, else realm colour). */
	public int robeColor;
	/** 0..1 how far into the seated meditation pose it is. */
	public float seated;
	public Component name = Component.empty();
}
