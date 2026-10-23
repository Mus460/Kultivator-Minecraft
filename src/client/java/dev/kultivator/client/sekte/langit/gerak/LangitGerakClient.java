package dev.kultivator.client.sekte.langit.gerak;

import dev.kultivator.sekte.langit.gerak.LangitGerakEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;

/**
 * The client side of module 37M (DESIGN 37.5): the Cloud Ferry's renderer. Called once from 37V's
 * {@code client.sekte.langit.LangitClient.init()} (its own commented line). The patrol pose of activity 25 and the
 * transient effects ({@code FX_TANGKAP}, {@code FX_PINDAH}, {@code FX_FERI}) are 37V's ({@code LangitPoses},
 * {@code LangitFxClient}). Client only.
 */
public final class LangitGerakClient {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		EntityRenderers.register(LangitGerakEntities.FERI, FeriAwanRenderer::new);
	}

	private LangitGerakClient() {
	}
}
