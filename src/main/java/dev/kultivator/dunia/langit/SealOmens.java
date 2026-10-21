package dev.kultivator.dunia.langit;

import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.network.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sky omen over a weakening demon prison (DESIGN 34.4.3: L listens to {@code SEAL_CHANGED} for "the omen sky grade
 * near the prison"). When module R reports a seal at or below {@link #THRESHOLD} integrity (or broken), players within
 * {@link #RANGE} blocks of that prison get a faint ash-red sky grade and vignette on the client, stronger the weaker the
 * seal; it fades a few seconds after they walk away or the seal is mended. R's own break moment (beat 41, darkness 0.6)
 * sits at a higher priority and wins while it plays.
 * <p>
 * Server side this is a small map of prison positions (memory only: R fires the event again on every change) and one
 * {@link LangitFxPayload#KIND_SEAL_OMEN} per nearby player every 40 ticks. Nothing in the world changes.
 */
final class SealOmens {

	/** Seals at or below this integrity darken the sky around their prison. */
	static final int THRESHOLD = 30;
	static final double RANGE = 160.0D;
	private static final int MAX_OMENS = 32;

	/** {@code until}: the game time a test preview ends ({@code Long.MAX_VALUE} for a real prison). */
	private record Omen(ResourceKey<Level> dim, BlockPos pos, int strength, long until) {
	}

	/** The id of the {@code uji langit segel} preview (never a real site id: those are hashes of a position). */
	private static final long PREVIEW_ID = Long.MIN_VALUE + 34L;
	private static final int PREVIEW_TICKS = 1200;

	private static final Map<Long, Omen> OMENS = new HashMap<>();

	static void init() {
		DuniaEvents.SEAL_CHANGED.register(SealOmens::onSeal);
	}

	/** {@code DuniaEvents.SEAL_CHANGED}: remembers or forgets the prison's omen. */
	private static void onSeal(ServerLevel level, long siteId, int integrity, boolean broken) {
		int strength = strength(integrity, broken);
		if (strength <= 0) {
			OMENS.remove(siteId);
			return;
		}
		DuniaSites.Site site = DuniaSites.byId(level.getServer(), siteId);
		if (site == null) {
			return;
		}
		if (!OMENS.containsKey(siteId) && OMENS.size() >= MAX_OMENS) {
			return;
		}
		OMENS.put(siteId, new Omen(site.dim(), site.pos().immutable(), strength, Long.MAX_VALUE));
	}

	/** 0 for a sound seal; 15-100 below the threshold; 100 when broken. */
	static int strength(int integrity, boolean broken) {
		if (broken) {
			return 100;
		}
		if (integrity > THRESHOLD) {
			return 0;
		}
		int clamped = Math.max(0, integrity);
		return 15 + (THRESHOLD - clamped) * 85 / THRESHOLD;
	}

	/** Every 40 ticks from {@link Langit}: the omen to every player near a weak seal (the client fades it out on its own). */
	static void tick(MinecraftServer server) {
		if (OMENS.isEmpty() || !DuniaSpine.enabled()) {
			return;
		}
		for (Omen omen : List.copyOf(OMENS.values())) {
			ServerLevel level = server.getLevel(omen.dim());
			if (level == null || level.players().isEmpty()) {
				continue;
			}
			if (level.getGameTime() >= omen.until()) {
				OMENS.values().remove(omen);
				continue;
			}
			Vec3 at = Vec3.atCenterOf(omen.pos());
			for (ServerPlayer player : List.copyOf(level.players())) {
				double distance = TreasureBirths.horizontal(player.position(), at);
				if (distance > RANGE || player.isSpectator()) {
					continue;
				}
				// the strength the player feels: full within 48 blocks, fading to a third at the edge
				double falloff = distance <= 48.0D ? 1.0D : 1.0D - (distance - 48.0D) / (RANGE - 48.0D) * 0.67D;
				int felt = (int) Math.round(omen.strength() * falloff);
				Net.send(player, LangitFxPayload.at(LangitFxPayload.KIND_SEAL_OMEN, at.x, at.y, at.z, Math.max(1, felt)));
			}
		}
	}

	/**
	 * {@code uji langit segel [integritas]}: a one-minute preview of the omen of a seal at {@code integrity} around
	 * {@code pos} (works with module R off); false when that integrity casts no omen (the preview ends).
	 */
	static boolean preview(ServerLevel level, BlockPos pos, int integrity) {
		int strength = strength(integrity, integrity <= 0);
		if (strength <= 0) {
			OMENS.remove(PREVIEW_ID);
			return false;
		}
		OMENS.put(PREVIEW_ID, new Omen(level.dimension(), pos.immutable(), strength, level.getGameTime() + PREVIEW_TICKS));
		return true;
	}

	static void clear() {
		OMENS.clear();
	}

	private SealOmens() {
	}
}
