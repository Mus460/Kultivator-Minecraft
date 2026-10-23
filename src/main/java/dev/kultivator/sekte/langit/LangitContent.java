package dev.kultivator.sekte.langit;

import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitPayloads;

/**
 * Sekte Langit (DESIGN 37): the one entry of the four sky-sect modules, called once from {@code Kultivator.onInitialize}
 * on both physical sides, right after {@code SekteContent.init()} and before {@code ModItems.init()}.
 * <p>
 * The core (config section {@code sekteLangit}, payload types, the {@link LangitApi} implementation) is the build
 * module's (37B). Each other module owns exactly ONE line below and replaces its own commented line with the real call.
 * K1 rule: a module registers its registry content (items, entity types, payload types, template components) always and
 * only gates its runtime logic with {@link LangitConfig#on()}.
 */
public final class LangitContent {

	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LangitConfig.register();
		LangitPayloads.register();
		LangitApi.set(new dev.kultivator.sect.build.flow.LangitApiImpl());
		// 37B runtime: warded air, lightning, the island's shadow, the commands under /kultivasi aula langit.
		dev.kultivator.sect.build.flow.LangitBangun.init();
		dev.kultivator.sekte.langit.gerak.LangitGerak.init();               // 37M: net, dive, ferry, docks, patrols, stair
		// 37G gameplay, events, NPC sky sects, UI, guide and tests (its registry content always, its runtime behind aktif).
		dev.kultivator.sekte.langit.main.LangitMain.init();
	}

	private LangitContent() {
	}
}
