package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.DuniaTickets;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Menara Tambat Langit of the Gudang Dagang district (DESIGN 38.T.3.4, 38.T.11 row "Perjalanan Awan, rute kapal"):
 * a timber-and-stone tower with two landing decks 28 blocks up for flying ships.
 * <ul>
 * <li><b>Route</b>: every harbour a player has found becomes a destination of the player ships
 * ({@code DuniaApi.routes().registerDestination("kota_tema_pelabuhan_<id>", name, allowed, arrive)}): allowed when the
 * captain knows the harbour's site; the captain and the riders arrive on landing deck 0. The deck position is kept in
 * the theme data ({@code bagian.anjungan}) when the tower first loads, so a harbour stays a destination after a
 * restart (registered again at server start).</li>
 * <li><b>Fuel desk</b> (deck 0): a deckhand sells spirit stones at their own value ({@link TokoPelabuhan.Jenis#BAHAN_BAKAR},
 * never cheaper than {@code NilaiBatu.VALUE}).</li>
 * </ul>
 */
public final class MenaraTambat {

	private static final Set<String> RUTE = new HashSet<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RUTE.clear();
			CompoundTag decks = PelabuhanKota.bagian(server).getCompoundOrEmpty("anjungan");
			for (String key : decks.keySet()) {
				try {
					daftarRute(server, Long.parseLong(key), BlockPos.of(decks.getLongOr(key, 0L)));
				} catch (NumberFormatException ignored) {
					// a malformed key is skipped
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUTE.clear());
	}

	/** From the {@code pelabuhan/tambat_langit} anchors. */
	static void anjunganDekat(ServerLevel level, DuniaAnchorEntity anchor, PelabuhanKota.Hidup kota, List<ServerPlayer> within,
			PelabuhanAnchors handler) {
		int deck = PelabuhanAnchors.angka(anchor, "anjungan", 0);
		if (deck != 0) {
			return;
		}
		MinecraftServer server = level.getServer();
		CompoundTag decks = PelabuhanKota.anak(PelabuhanKota.bagian(server), "anjungan");
		String key = Long.toString(kota.situs);
		if (!decks.contains(key)) {
			decks.putLong(key, anchor.blockPosition().asLong());
			PelabuhanKota.ubah(server);
		}
		daftarRute(server, kota.situs, anchor.blockPosition());
		if (handler.buatan(anchor) == null && PelabuhanAnchors.dekat(anchor, within, 40.0D)) {
			KultivatorNpc npc = HidupApi.tubuhStaf(level, kota.situs, NpcRole.PELAUT, anchor.blockPosition(), anchor.getYRot());
			if (npc != null) {
				npc.memory().putBoolean("hold", true);
				npc.memory().putString(TokoPelabuhan.LAYANAN_NPC, "bahan_bakar");
				npc.memory().putLong(TokoPelabuhan.SITUS_NPC, kota.situs);
				handler.simpan(anchor, npc);
			}
		}
	}

	/** Registers one harbour as a ship destination (once per session). */
	static void daftarRute(MinecraftServer server, long situs, BlockPos deck) {
		String id = "kota_tema_pelabuhan_" + Long.toUnsignedString(situs, 36);
		if (!RUTE.add(id)) {
			return;
		}
		PelabuhanKota.Hidup kota = PelabuhanKota.cari(situs);
		Component name = kota != null ? kota.nama(server) : Component.translatable("kultivator.kota_tema.pelabuhan.rute");
		try {
			DuniaApi.routes().registerDestination(id, name,
					(captain, level) -> level.dimension() == Level.OVERWORLD && DuniaSites.knows(captain, situs),
					(captain, riders) -> tiba(captain, riders, situs, deck));
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Harbour ship route {} could not be registered", id, exception);
		}
	}

	private static void tiba(ServerPlayer captain, List<Entity> riders, long situs, BlockPos deck) {
		ServerLevel level = captain.level().getServer().overworld();
		DuniaTickets.touch(level, ChunkPos.containing(deck), 1);
		captain.teleportTo(level, deck.getX() + 0.5D, deck.getY(), deck.getZ() + 0.5D, Set.of(), captain.getYRot(), captain.getXRot(), true);
		int i = 0;
		for (Entity e : riders) {
			double ox = (i % 3 - 1) * 1.5D;
			double oz = 1.5D + (i / 3) * 1.5D;
			if (e instanceof ServerPlayer rp) {
				rp.teleportTo(level, deck.getX() + 0.5D + ox, deck.getY(), deck.getZ() + 0.5D + oz, Set.of(), rp.getYRot(), rp.getXRot(), true);
			} else if (e.level() == level) {
				e.teleportTo(deck.getX() + 0.5D + ox, deck.getY(), deck.getZ() + 0.5D + oz);
			}
			i++;
		}
		CeritaApi.peristiwa(captain, "pergi", "kota_tema/pelabuhan");
	}

	private MenaraTambat() {
	}
}
