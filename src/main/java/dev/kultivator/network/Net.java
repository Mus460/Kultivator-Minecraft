package dev.kultivator.network;

import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.SpiritRoots;
import dev.kultivator.dao.DaoPaths;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.technique.Techniques;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Registers the packets and routes key presses / buttons to the modules. */
public final class Net {

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Payloads.SyncSelf.TYPE, Payloads.SyncSelf.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.Fx.TYPE, Payloads.Fx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.OrePings.TYPE, Payloads.OrePings.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.OpenScreen.TYPE, Payloads.OpenScreen.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Payloads.Action.TYPE, Payloads.Action.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Payloads.Action.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	/** Sends a payload if the player's client has the mod (vanilla clients are skipped silently). */
	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static void handle(ServerPlayer player, Payloads.Action action) {
		if (player.isRemoved() || player.isSpectator()) {
			return;
		}
		switch (action.action()) {
			case Actions.MEDITATE_TOGGLE -> Meditation.toggle(player);
			case Actions.BREAKTHROUGH -> Breakthrough.attempt(player);
			case Actions.CAST, Actions.SELECT_TECHNIQUE, Actions.SELECT_NEXT, Actions.SELECT_PREVIOUS,
				 Actions.FLYING_SWORD_TOGGLE, Actions.CLOUD_STEP -> Techniques.handleAction(player, action);
			case Actions.ROOT_TEST -> SpiritRoots.startCeremony(player);
			case Actions.CHOOSE_DAO -> {
				Dao dao = Dao.byOrdinal(action.arg());
				if (dao != null) {
					DaoPaths.choose(player, dao);
				}
			}
			case Actions.SET_COSMETIC -> {
				int flag = action.arg() & CultivationData.COSMETICS_ALL;
				if (flag != 0) {
					Cultivators.get(player).setCosmetic(flag, "1".equals(action.text()));
					Cultivators.syncNow(player);
				}
			}
			case Actions.SET_HARDCORE -> setHardcore(player, action.arg() == 1);
			case Actions.REQUEST_SYNC -> Cultivators.syncNow(player);
			default -> {
			}
		}
	}

	/** Hardcore breakthroughs: a failed breakthrough drops one realm instead of a Qi Deviation. */
	public static void setHardcore(ServerPlayer player, boolean on) {
		Cultivators.get(player).setHardcore(on);
		Cultivators.syncNow(player);
		player.sendSystemMessage(Msg.prefixed(Component.translatable(on ? "kultivator.core.hardcore_on" : "kultivator.core.hardcore_off")));
	}

	private Net() {
	}
}
