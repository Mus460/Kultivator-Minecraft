package dev.kultivator.sekte.langit.main;

import dev.kultivator.musuh.SectRaids;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.sekte.langit.gerak.FeriPanggil;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The server side of the "Sekte Langit" screen (DESIGN 37.7.9): answers {@link LangitUiPayloads.Minta} with a
 * {@link LangitUiPayloads.Papan} holding everything the screen draws (the hall's island, its events and chronicle, the
 * Heaven Court order, the NPC sky sects) and runs the screen's buttons through the existing commands (the gate and the
 * invitation are the build module's {@code /kultivasi aula langit gerbang|undang}, the ferry is 37M's
 * {@code FeriPanggil.callNear}). The viewer's own hall only (a sect mate sees its master's hall).
 */
public final class LangitUi {

	static void register() {
		ServerPlayNetworking.registerGlobalReceiver(LangitUiPayloads.Minta.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	private static void handle(ServerPlayer player, LangitUiPayloads.Minta minta) {
		MinecraftServer server = player.level().getServer();
		UUID owner = ownerFor(player);
		switch (minta.action()) {
			case LangitUiPayloads.Minta.FERI -> {
				Component refused = FeriPanggil.callNear(player);
				if (refused != null) {
					Msg.actionBar(player, refused.copy().withStyle(ChatFormatting.YELLOW));
				}
			}
			case LangitUiPayloads.Minta.GERBANG -> {
				SkyHallView view = LangitApi.get().skyHall(player.getUUID());
				if (view != null) {
					run(server, player, "kultivasi aula langit gerbang " + (view.gateClosed() ? "buka" : "tutup"));
				}
			}
			case LangitUiPayloads.Minta.UNDANG -> {
				String name = minta.arg() == null ? "" : minta.arg().trim();
				if (name.matches("[A-Za-z0-9_]{1,16}")) {
					run(server, player, "kultivasi aula langit undang " + name);
				}
			}
			case LangitUiPayloads.Minta.RANCANG -> {
				if (Halls.hallOf(player.getUUID()) != null) {
					Halls.openScreen(player, player.getUUID(), Halls.PAGE_BANGUN);
					return;
				}
			}
			case LangitUiPayloads.Minta.SETOR -> LangitUtusan.answer(player, "setor");
			default -> {
			}
		}
		sendData(player, data(server, player, owner));
	}

	private static void sendData(ServerPlayer player, CompoundTag tag) {
		if (ServerPlayNetworking.canSend(player, LangitUiPayloads.Papan.TYPE)) {
			ServerPlayNetworking.send(player, new LangitUiPayloads.Papan(tag));
		}
	}

	private static void run(MinecraftServer server, ServerPlayer player, String command) {
		server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
	}

	/** Everything the screen shows for {@code owner}'s hall, seen by {@code viewer}. */
	static CompoundTag data(MinecraftServer server, ServerPlayer viewer, UUID owner) {
		CompoundTag tag = new CompoundTag();
		LangitConfig config = LangitConfig.get();
		tag.putBoolean("aktif", config.aktif);
		Halls.HallView hall = Halls.hallOf(owner);
		tag.putBoolean("aula", hall != null);
		tag.putBoolean("pemilik", owner.equals(viewer.getUUID()));
		long today = LangitPeristiwa.today(server);
		tag.putLong("hari", today);
		if (hall != null) {
			tag.putString("nama", hall.name());
			tag.putInt("level", hall.level());
		}
		SkyHallView view = LangitApi.get().skyHall(owner);
		tag.putBoolean("langit", view != null);
		if (view != null) {
			CompoundTag sky = new CompoundTag();
			sky.putInt("templat", view.template());
			sky.putInt("tingkat", view.tier());
			sky.putInt("yP", view.yP());
			sky.putInt("dasar", view.skyFloorY());
			sky.putBoolean("tertambat", view.tethered());
			sky.putInt("naik", view.ascent());
			sky.putBoolean("tertutup", view.gateClosed());
			sky.putBoolean("formasi", view.grandFormation());
			sky.putBoolean("jaring", LangitSyncServer.netRunning(owner));
			sky.putDouble("faktor", LangitSites.factor(Math.max(1, view.tier()), view.grandFormation()));
			sky.putInt("lebar", view.islandBox().getXSpan());
			sky.putInt("panjang", view.islandBox().getZSpan());
			sky.putInt("tinggi_atas", view.islandBox().maxY() - view.yP());
			BlockPos o = view.origin();
			sky.putIntArray("asal", new int[]{o.getX(), o.getY(), o.getZ()});
			sky.putBoolean("tangga", LangitTangga.stairBuilt(owner));
			sky.putInt("jangkar", LangitApi.get().anchors(owner).size());
			tag.put("pulau", sky);
		}
		CompoundTag events = new CompoundTag();
		CompoundTag own = LangitData.get(server).peekHall(owner);
		if (own != null) {
			putDays(events, "bajak", own, "bajak_berikut", today);
			putDays(events, "utusan", own, "utusan_berikut", today);
			putDays(events, "tamu", own, "tamu_berikut", today);
			putDays(events, "pesta", own, "pesta_berikut", today);
			events.putString("tunda", own.getStringOr("tunda", ""));
			events.putInt("tolak", own.getIntOr("tolak", 0));
			events.putLong("diakui", Math.max(-1L, own.getLongOr("diakui_sampai", Long.MIN_VALUE / 2) - today));
		}
		events.putBoolean("pesta_kini", LangitPeristiwa.festival(server, owner));
		events.putBoolean("bajak_kini", LangitBajak.active(owner));
		events.putBoolean("serbu_kini", SectRaids.active(owner));
		events.putBoolean("utusan_layak", view != null && LangitUtusan.eligible(view));
		tag.put("peristiwa", events);
		CompoundTag order = LangitUtusan.order(server, owner);
		if (!order.isEmpty()) {
			order.putLong("sisa", order.getLongOr("until", today) - today);
			order.putInt("perlu", LangitUtusan.ORDER_KILLS);
		}
		tag.put("perintah", order);
		ListTag chronicle = new ListTag();
		for (CompoundTag line : LangitData.get(server).chronicle(owner)) {
			chronicle.add(line.copy());
		}
		tag.put("kronik", chronicle);
		ListTag sects = new ListTag();
		DiplomasiService d = SekteApi.diplomasi();
		for (LangitNpcPlan.Istana istana : LangitNpcPlan.of(server.overworld().getSeed())) {
			CompoundTag s = new CompoundTag();
			String id = LangitNpcPlan.sectId(istana);
			Component name = id == null ? null : d.name(id);
			s.store("nama", ComponentSerialization.CODEC, name == null ? Component.translatable("kultivator.sekte_langit.npc.tanpa_nama") : name);
			s.putInt("tier", istana.gate().tier());
			boolean met = id != null && d.met(owner, id);
			s.putBoolean("kenal", met);
			s.putString("sikap", id == null ? "netral" : d.stance(owner, id).id());
			s.putInt("standing", id == null ? 0 : d.standing(owner, id));
			if (met) {
				s.putIntArray("pos", new int[]{istana.column().getX(), istana.column().getZ()});
			}
			sects.add(s);
		}
		tag.put("sekte", sects);
		return tag;
	}

	private static void putDays(CompoundTag into, String key, CompoundTag hall, String source, long today) {
		if (hall.contains(source)) {
			into.putLong(key, hall.getLongOr(source, today) - today);
		}
	}

	/** Sends the screen data to the viewer (the {@code /kultivasi langit} command opens the screen client side). */
	static void send(ServerPlayer viewer) {
		CompoundTag tag = data(viewer.level().getServer(), viewer, ownerFor(viewer));
		tag.putBoolean("buka", true);
		sendData(viewer, tag);
	}

	/** The viewer's own hall, else the sky hall the viewer belongs to (a sect mate), else the viewer. */
	static UUID ownerFor(ServerPlayer viewer) {
		if (Halls.hallOf(viewer.getUUID()) != null) {
			return viewer.getUUID();
		}
		for (SkyHallView view : LangitApi.get().all(viewer.level().getServer().overworld())) {
			if (LangitApi.get().member(view.owner(), viewer)) {
				return view.owner();
			}
		}
		return viewer.getUUID();
	}

	private LangitUi() {
	}
}
