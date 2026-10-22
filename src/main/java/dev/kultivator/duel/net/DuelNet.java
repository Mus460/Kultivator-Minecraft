package dev.kultivator.duel.net;

import dev.kultivator.duel.DuelConfig;
import dev.kultivator.duel.clash.Fighters;
import dev.kultivator.duel.session.DuelChallenges;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registers every duel payload (DESIGN 35.8.1, contract C35.S1/S3) with {@code PayloadTypeRegistry} and the server
 * receivers of the four client-to-server records. Receivers run on the server thread; the clash layer itself
 * ({@code Fighters}) checks sequence, rate and phase, so a bad client can only lose its own inputs.
 */
public final class DuelNet {

	/** The last {@code DuelPrefs} of each player (kept over a duel so a fighter created later still knows them). */
	private static final Map<UUID, DuelPayloads.DuelPrefs> PREFS = new HashMap<>();
	private static boolean done;

	/** Once, from {@code Duel.init} (both physical sides: the payload types must exist on the client too). */
	public static void init() {
		if (done) {
			return;
		}
		done = true;
		PayloadTypeRegistry.serverboundPlay().register(DuelPayloads.DuelInput.TYPE, DuelPayloads.DuelInput.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DuelPayloads.DuelChallenge.TYPE, DuelPayloads.DuelChallenge.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DuelPayloads.DuelAnswer.TYPE, DuelPayloads.DuelAnswer.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DuelPayloads.DuelPrefs.TYPE, DuelPayloads.DuelPrefs.CODEC);

		PayloadTypeRegistry.clientboundPlay().register(DuelPayloads.DuelChallengeOffer.TYPE, DuelPayloads.DuelChallengeOffer.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DuelPayloads.DuelSessionSync.TYPE, DuelPayloads.DuelSessionSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DuelPayloads.DuelFighterSync.TYPE, DuelPayloads.DuelFighterSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DuelPayloads.DuelSwing.TYPE, DuelPayloads.DuelSwing.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DuelPayloads.DuelClash.TYPE, DuelPayloads.DuelClash.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DuelPayloads.DuelBindSync.TYPE, DuelPayloads.DuelBindSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DuelPayloads.DuelResult.TYPE, DuelPayloads.DuelResult.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DuelPayloads.DuelPreview.TYPE, DuelPayloads.DuelPreview.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelInput.TYPE, (payload, context) -> {
			if (DuelConfig.get().enabled) {
				Fighters.onNetworkInput(context.player(), payload);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelChallenge.TYPE, (payload, context) -> {
			if (DuelConfig.get().enabled) {
				DuelChallenges.fromPayload(context.player(), payload);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelAnswer.TYPE, (payload, context) -> {
			if (DuelConfig.get().enabled) {
				DuelChallenges.answer(context.player(), payload.challengeId(), payload.answer());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelPrefs.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			PREFS.put(player.getUUID(), payload);
			Fighters.applyPrefs(player, payload);
		});
	}

	/** The duel settings {@code player}'s client reported, or null (no client module, or not yet). */
	public static @Nullable DuelPayloads.DuelPrefs prefs(UUID player) {
		return PREFS.get(player);
	}

	/** True when the player's client has the duel module (it sent its settings). */
	public static boolean hasModule(ServerPlayer player) {
		return PREFS.containsKey(player.getUUID());
	}

	/** Sends {@code payload} when the client can receive it (a client without the mod is skipped silently). */
	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** A player left: forget the settings (the client sends them again on join). */
	public static void forget(UUID player) {
		PREFS.remove(player);
	}

	public static void clear() {
		PREFS.clear();
	}

	private DuelNet() {
	}
}
