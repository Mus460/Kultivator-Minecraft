package dev.kultivator.dunia;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Mth;

/**
 * The spine of Dunia Kultivasi yang Luas (DESIGN 34.4, owned by module K, landed first): anchors, sites, events and
 * services, config, commands, world generation packs, chunk tickets, budgets and markers. Called once from
 * {@link DuniaContent#init()} before every module, on both physical sides. Nothing here changes a block.
 */
public final class DuniaSpine {

	/** Section {@code umum} of {@code config/kultivator-dunia.json}. */
	public static final class Umum {
		/** Master switch: false = every module's runtime off (structures still generate while their packs are on). */
		public boolean enabled = true;
		/** Humans of this phase alive per server (12-192). */
		public int npcCap = 120;
		/** Humans within 96 blocks of one player (4-96). */
		public int npcPerPlayer = 48;
		/** MSPT guard in milliseconds (30-200). */
		public int msptGuard = 90;
		/** Logs every anchor load and unload. */
		public boolean debugAnchors = false;

		Umum clamp() {
			npcCap = Mth.clamp(npcCap, 12, 192);
			npcPerPlayer = Mth.clamp(npcPerPlayer, 4, 96);
			msptGuard = Mth.clamp(msptGuard, 30, 200);
			return this;
		}
	}

	private static Umum umum = new Umum();
	private static boolean initialised;

	/** From {@code DuniaContent.init()}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		umum = DuniaConfig.section("umum", Umum.class, Umum::new, Umum::clamp);
		PayloadTypeRegistry.clientboundPlay().register(DuniaGhostPayload.TYPE, DuniaGhostPayload.CODEC);
		JauhDariPusatPlacement.register();
		DuniaAnchors.init();
		DuniaSites.init();
		DuniaBudget.init();
		DuniaTickets.init();
		DuniaPacks.init();
		DuniaCommands.init();
	}

	/** The {@code umum} section (never null). */
	public static Umum umum() {
		return umum;
	}

	/** The master switch {@code umum.enabled}. */
	public static boolean enabled() {
		return umum.enabled;
	}

	private DuniaSpine() {
	}
}
