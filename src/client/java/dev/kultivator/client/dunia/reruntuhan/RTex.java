package dev.kultivator.client.dunia.reruntuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.client.epic.render.EpicRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The sprites and render types of module R's client (written by {@code tools/gen_dunia_reruntuhan_textures.py} into
 * {@code textures/dunia/reruntuhan/}, plus the shared 7b glow strip and soft ring): vanilla entity render types only
 * (translucent emissive for light, cutout for the talisman paper), so Sodium and Iris draw them like any entity quad.
 */
final class RTex {

	static final Identifier TELEGRAPH_TEX = Kultivator.id("textures/dunia/reruntuhan/cincin_telegraf.png");
	static final Identifier CRACK_TEX = Kultivator.id("textures/dunia/reruntuhan/retak.png");
	static final Identifier SIGIL_TEX = Kultivator.id("textures/dunia/reruntuhan/segel_lingkar.png");
	static final Identifier GLOW_TEX = Kultivator.id("textures/dunia/reruntuhan/cahaya.png");
	static final Identifier CLOUD_TEX = Kultivator.id("textures/dunia/reruntuhan/awan.png");
	static final Identifier CURTAIN_TEX = Kultivator.id("textures/dunia/reruntuhan/tirai_gerbang.png");
	static final Identifier PAPER_TEX = Kultivator.id("textures/dunia/reruntuhan/jimat.png");

	static final RenderType TELEGRAPH = RenderTypes.entityTranslucentEmissive(TELEGRAPH_TEX);
	static final RenderType CRACK = RenderTypes.entityTranslucentEmissive(CRACK_TEX);
	static final RenderType SIGIL = RenderTypes.entityTranslucentEmissive(SIGIL_TEX);
	static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(GLOW_TEX);
	static final RenderType CLOUD = RenderTypes.entityTranslucent(CLOUD_TEX);
	static final RenderType CURTAIN = RenderTypes.entityTranslucentEmissive(CURTAIN_TEX);
	static final RenderType PAPER = RenderTypes.entityCutout(PAPER_TEX);
	/** The 7b soft ring (shock waves) and glow strip (beams, pillars). */
	static final RenderType RING = EpicRenderTypes.RING;
	static final RenderType STRIP = EpicRenderTypes.GLOW;

	private RTex() {
	}
}
