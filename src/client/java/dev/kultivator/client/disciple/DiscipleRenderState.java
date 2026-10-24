package dev.kultivator.client.disciple;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
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
	// Phase 6 (DESIGN 20.3.22, module C): synced role, activity, sect rank, stars, talent and power bits.
	/** {@code DiscipleEntity.Role} ordinal. */
	public int role;
	public int activity;
	public int sectRank;
	public int stars;
	public int talent;
	public int power;
	/** The natal treasure's item (empty when it has none). */
	public final ItemStackRenderState natalItem = new ItemStackRenderState();
	// Polish pass (DESIGN 27.2): cast pose, held qi sword, the open Eternal Parasol and the clone fade.
	/** True while a cast pose drives the model (the duty pose stays off). */
	public boolean castActive;
	/** True when the right hand shows the natal or qi sword for a sword pose. */
	public boolean heldSword;
	/** The opened natal parasol held above the head (empty unless the Eternal Parasol is out). */
	public final ItemStackRenderState parasolItem = new ItemStackRenderState();
	/** 0..1 how solid a Divine Clone is (fading in after it appears and out before it is removed). */
	public float cloneFade = 1.0F;
	// Penampilan dan Animasi (DESIGN 40.7.2, module 40R): this frame's packed Sosok and Gaya (0 = Sosok did not extract
	// this body); the full per-body data rides SosokRender.DATA.
	/** {@code Sosok.pack()} of the body (0 = none). */
	public int sosok;
	/** {@code Gaya.pack()} of the body. */
	public int gaya;
}
