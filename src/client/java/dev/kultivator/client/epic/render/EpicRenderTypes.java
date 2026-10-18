package dev.kultivator.client.epic.render;

import dev.kultivator.Kultivator;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The render types of the 7b geometry (DESIGN 23.10.2): only vanilla types and the vanilla beacon beam.
 * <ul>
 * <li>thin glow (bolts, arcs, lead threads, thin seams): {@code lightning()} (POSITION_COLOR, additive, writes depth, so
 * only thin strips); under a pack {@link #BOLT_EMISSIVE_UNDER_PACK} switches them to the emissive glow strip;</li>
 * <li>soft layers (vortex, rays, rings, gate, aurora, trails, wide seams, domes): {@code entityTranslucentEmissive} with a
 * texture whose alpha stays ≥ 0.1 (the cutout runs before the vertex colour; fades go into vertex alpha);</li>
 * <li>giant figures: {@code entityTranslucentCull} at full bright; pillars: the beacon beam texture.</li>
 * </ul>
 * Dark layers never use the additive type.
 */
public final class EpicRenderTypes {

	/** [verify in game, 23.19 item 2] true draws bolts as the emissive glow strip while a shader pack is on. */
	public static final boolean BOLT_EMISSIVE_UNDER_PACK = false;

	public static final Identifier CLOUD_SWIRL = Kultivator.id("textures/misc/epic/cloud_swirl.png");
	public static final Identifier GLOW_STRIP = Kultivator.id("textures/misc/epic/glow_strip.png");
	public static final Identifier RAY_FAN = Kultivator.id("textures/misc/epic/ray_fan.png");
	public static final Identifier RING_SOFT = Kultivator.id("textures/misc/epic/ring_soft.png");
	public static final Identifier DAO_CIRCLE = Kultivator.id("textures/misc/dao_circle.png");
	public static final Identifier THUNDER_AVATAR = Kultivator.id("textures/misc/epic/thunder_avatar.png");
	public static final Identifier IMMORTAL_AVATAR = Kultivator.id("textures/misc/epic/immortal_avatar.png");
	public static final Identifier BEACON_BEAM = Identifier.withDefaultNamespace("textures/entity/beacon/beacon_beam.png");

	public static final RenderType LIGHTNING = RenderTypes.lightning();
	public static final RenderType SWIRL = RenderTypes.entityTranslucentEmissive(CLOUD_SWIRL);
	public static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(GLOW_STRIP);
	public static final RenderType RAYS = RenderTypes.entityTranslucentEmissive(RAY_FAN);
	public static final RenderType RING = RenderTypes.entityTranslucentEmissive(RING_SOFT);
	public static final RenderType RUNE = RenderTypes.entityTranslucentEmissive(DAO_CIRCLE);

	private EpicRenderTypes() {
	}
}
