package dev.kultivator.duel.net;

import dev.kultivator.duel.DuelConfig;
import dev.kultivator.duel.clash.Fighters;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRecords;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
		PayloadTypeRegistry.clientboundPlay().register(DuelPayloads.DuelCatatan.TYPE, DuelPayloads.DuelCatatan.CODEC);

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
			if (payload.answer() == DuelPayloads.DuelAnswer.ANSWER_CATATAN) {
				send(context.player(), new DuelPayloads.DuelCatatan(catatan(context.player())));
			} else if (DuelConfig.get().enabled) {
				DuelChallenges.answer(context.player(), payload.challengeId(), payload.answer());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelPrefs.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			PREFS.put(player.getUUID(), payload);
			Fighters.applyPrefs(player, payload);
		});
	}

	/** The record of {@code player} as {@code /kultivasi duel catatan} prints it, for {@link DuelPayloads.DuelCatatan} (43.14 S2). */
	static CompoundTag catatan(ServerPlayer player) {
		DuelRecords records = DuelRecords.get(player.level().getServer());
		DuelRecords.Entry entry = records.entry(player.getUUID());
		int honour = records.honour(player.getUUID());
		CompoundTag tag = new CompoundTag();
		tag.putInt("ada", entry == null ? 0 : 1);
		tag.putInt("kehormatan", honour);
		tag.putString("gelar", DuelRecords.titleId(honour));
		if (entry == null) {
			return tag;
		}
		tag.putInt("menang", entry.wins);
		tag.putInt("kalah", entry.losses);
		tag.putInt("seri", entry.draws);
		tag.putInt("sempurna", entry.perfects);
		tag.putInt("penentu", entry.deathblows);
		tag.putInt("kunci", entry.bindsWon);
		tag.putInt("rantai", entry.maxChain);
		ListTag baru = new ListTag();
		long now = player.level().getGameTime();
		for (DuelRecords.Recent recent : entry.recent) {
			CompoundTag r = new CompoundTag();
			r.putString("nama", recent.name());
			r.putInt("hasil", Integer.signum(recent.result()));
			r.putInt("hari", (int) Math.max(0L, (now - recent.time()) / 24000L));
			baru.add(r);
		}
		tag.put("baru", baru);
		return tag;
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
