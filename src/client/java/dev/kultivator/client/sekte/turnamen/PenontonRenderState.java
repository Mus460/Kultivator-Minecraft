package dev.kultivator.client.sekte.turnamen;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/** What a visiting spectator ({@code sekte.turnamen.Penonton}) looks like this frame. */
public class PenontonRenderState extends AvatarRenderState {

	/** {@code Penonton.KIND_ENVOY} or {@code KIND_VILLAGER}. */
	public int kind;
	public boolean slim;
	public boolean hat;
	public int robe;
	public int trim;
	public Identifier texture;
	/** Ticks since the cheer began (0..CHEER_TICKS), or -1 while quiet. */
	public float cheer = -1.0F;
	/** False beyond 32 blocks: the body stands still (DESIGN 36.3.9 LOD). */
	public boolean animate = true;
}
