package dev.kultivator.client.epic.kosmik;

import dev.kultivator.Kultivator;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The vanilla render types of the stages (DESIGN 32.0, 32.1.4, 32.8): no custom pipeline, no core shader, no post chain.
 * Each is created once per texture, on first use (never in a static initialiser):
 * <ul>
 * <li>{@link #solid}: {@code entitySolid} (opaque, back faces culled, depth write): the dome, the planet, the moon;</li>
 * <li>{@link #cutout}: {@code entityCutout} (alpha cutout 0.1, no cull, depth write): the cuboid stage models;</li>
 * <li>{@link #glow}: {@code entityTranslucentEmissive} (translucent, no cull, depth test without write): clouds, decks;</li>
 * <li>{@link #add}: {@code energySwirl(tex, 0, 0)} ({@code BlendFunction.ADDITIVE} = ONE, ONE, so the colour is
 * premultiplied by its alpha in {@link StageFrame#vertex}; no cull; it <b>writes depth</b>, so additive layers are drawn
 * last and far to near where they overlap). One render type per texture: animate it through vertex UVs.</li>
 * </ul>
 */
public final class KosmikTypes {

	/**
	 * [verify in game, 32.10 item 21] true draws the planet and the moon with {@code entityTranslucentEmissive} (far to
	 * near) instead of {@code entitySolid} while a shader pack is in use, if a pack shades the solid bodies badly.
	 */
	public static final boolean PACK_EMISSIVE_BODIES = false;

	/** The fallback dome (a stage without a dome texture). */
	public static final Identifier BLACK = Kultivator.id("textures/misc/kosmik/black.png");
	/** A plain white texture for vertex-coloured geometry. */
	public static final Identifier WHITE = Kultivator.id("textures/misc/kosmik/white.png");

	private static final Map<Identifier, RenderType> SOLID = new HashMap<>();
	private static final Map<Identifier, RenderType> CUTOUT = new HashMap<>();
	private static final Map<Identifier, RenderType> GLOW = new HashMap<>();
	private static final Map<Identifier, RenderType> ADD = new HashMap<>();

	public static RenderType solid(Identifier texture) {
		return SOLID.computeIfAbsent(texture, RenderTypes::entitySolid);
	}

	public static RenderType cutout(Identifier texture) {
		return CUTOUT.computeIfAbsent(texture, RenderTypes::entityCutout);
	}

	public static RenderType glow(Identifier texture) {
		return GLOW.computeIfAbsent(texture, RenderTypes::entityTranslucentEmissive);
	}

	public static RenderType add(Identifier texture) {
		return ADD.computeIfAbsent(texture, id -> RenderTypes.energySwirl(id, 0.0F, 0.0F));
	}

	/** The body type: solid, or the emissive translucent one under a pack when {@link #PACK_EMISSIVE_BODIES}. */
	public static boolean emissiveBodies(boolean pack) {
		return PACK_EMISSIVE_BODIES && pack;
	}

	private KosmikTypes() {
	}
}
