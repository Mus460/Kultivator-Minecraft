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
		PayloadTypeRegistry.clientboundPlay().register(Payloads.DiscipleInfo.TYPE, Payloads.DiscipleInfo.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.HallInfo.TYPE, Payloads.HallInfo.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.BuildPreview.TYPE, Payloads.BuildPreview.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Payloads.TechFx.TYPE, Payloads.TechFx.CODEC);
		// Phase 7b (DESIGN 23.4.1).
		PayloadTypeRegistry.clientboundPlay().register(Payloads.EpicMoment.TYPE, Payloads.EpicMoment.CODEC);
		// Phase 7c (DESIGN 24.12, 24.13 item 2).
		PayloadTypeRegistry.clientboundPlay().register(Payloads.FormasiSync.TYPE, Payloads.FormasiSync.CODEC);
		// Phase 8 (DESIGN 25.2 item 2).
		PayloadTypeRegistry.clientboundPlay().register(Payloads.DuniaSync.TYPE, Payloads.DuniaSync.CODEC);
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
			case Actions.DISCIPLE_OPEN, Actions.DISCIPLE_MODE, Actions.DISCIPLE_TEACH, Actions.DISCIPLE_FEED,
				 Actions.DISCIPLE_BREAKTHROUGH, Actions.DISCIPLE_CHOOSE_DAO, Actions.DISCIPLE_RELEASE ->
					dev.kultivator.disciple.Disciples.handleAction(player, action);
			// Phase 6 (DESIGN 20.2.1).
			case Actions.HALL -> dev.kultivator.sect.hall.Halls.handleAction(player, action);
			case Actions.SECT_EVENT -> dev.kultivator.sect.event.SectEvents.handleAction(player, action);
			case Actions.DISCIPLE_POWER -> dev.kultivator.disciple.power.DisciplePowers.handleAction(player, action);
			case Actions.DISCIPLE_PATH -> dev.kultivator.disciple.path.DisciplePaths.handleAction(player, action);
			case Actions.KEHANCURAN -> dev.kultivator.kehancuran.Kehancuran.handleAction(player, action);
			// Phase 6b (DESIGN 21.24).
			case Actions.SECT_BUILD -> dev.kultivator.sect.build.SectBuild.handleAction(player, action);
			// Phase 6c (DESIGN 22.12.10).
			case Actions.TECHNIQUE -> dev.kultivator.technique.spec.run.Codex.handleAction(player, action);
			// Phase 7 (DESIGN 19.2.1).
			case Actions.KULTIVASI -> dev.kultivator.cultivation.ImmortalPerks.handleAction(player, action.text());
			case Actions.JALAN -> dev.kultivator.goals.Goals.handleAction(player, action.text());
			// Phase 7c (DESIGN 24.13 item 1).
			case Actions.FORMASI -> dev.kultivator.formasi.Formasi.handleAction(player, action.text());
			// Phase 8 (DESIGN 25.2 item 1).
			case Actions.ALAM -> dev.kultivator.alam.Alam.handleAction(player, action.text());
			case Actions.MUSUH -> dev.kultivator.musuh.Musuh.handleAction(player, action.text());
			case Actions.TEMPA -> dev.kultivator.tempa.Tempa.handleAction(player, action.text());
			case Actions.DESA -> dev.kultivator.desa.Desa.handleAction(player, action.text());
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
