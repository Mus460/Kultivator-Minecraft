package dev.kultivator.dunia.tempat.ui;

import dev.kultivator.Kultivator;
import dev.kultivator.network.Net;
import dev.kultivator.util.Msg;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The server half of the 38 screens (DESIGN 38.U.1, contract C38.UI1): gameplay modules (KS, EK, DS, GU, PR, ST, W, TK)
 * build a sheet ({@code lembar}, keys in {@link LembarKunci}) and open it with {@link #buka}; the client kit of 38UI only
 * draws it and answers with {@link TempatAksiPayload}, which reaches the module's {@link Penangan} after the checks below.
 *
 * <h2>Session</h2>
 * Every {@link #buka}/{@link #bukaDi} remembers per player which screen is open, for which entity or anchor block, in which
 * dimension. A screen closed on the client sends op {@link LembarKunci#OP_TUTUP}, which ends the session; replacing the
 * screen replaces the session. {@link #segarkan} only reaches players whose session still shows that screen, so live
 * screens (auction, score board, countdowns) stream to viewers only.
 *
 * <h2>Checks before a {@link Penangan} runs</h2>
 * <ol>
 * <li>the player is alive, not a spectator, and not over {@link #AKSI_PER_DETIK} actions in the current second;</li>
 * <li>the screen id is registered ({@link #daftar});</li>
 * <li>{@code npcId >= 0}: the entity exists in the player's level, is alive and within {@link #JANGKAU} blocks
 * (KotaNet.REACH, 8), and the open session is that screen for that entity, except op {@link LembarKunci#OP_LIHAT} on a
 * screen with a {@link Pembangun} (opening from an NPC; the builder decides whether that NPC serves the screen by
 * returning {@code null});</li>
 * <li>{@code npcId == -1} with an anchor session ({@link #bukaDi}): same dimension and within {@link #JANGKAU} blocks of
 * the anchor's centre;</li>
 * <li>{@code npcId == -1} without anchor (hub screen): only read ops ({@link LembarKunci#opBaca}) pass; op
 * {@link LembarKunci#OP_LIHAT} with no session opens the screen only when it was registered with {@link #daftarHub}.</li>
 * </ol>
 * Out of reach: action bar {@code kultivator.kota.aksi.jauh} (the town's existing line) and the session ends.
 *
 * <h2>Refresh</h2>
 * A screen registered with a {@link Pembangun} is rebuilt and refreshed by this class after every action that leaves
 * the same session open; its {@link Penangan} must then not send its own refresh. A {@link Penangan} may open another
 * screen ({@link #buka}), which replaces the session and suppresses the automatic refresh.
 *
 * <p>Server only; never references client classes. Payloads are registered in {@link #init()} (called once from
 * {@code Tempat.init()}).
 */
public final class TempatLayar {

	/** Action reach in blocks from the NPC or the service anchor (same as {@code KotaNet.REACH}). */
	public static final double JANGKAU = 8.0D;
	/** Longest screen id (also the payload's string cap). */
	public static final int LAYAR_MAKS = 64;
	/** Sheet budget of 38.D.7 (estimated with {@link CompoundTag#sizeInBytes()}); bigger sheets are logged once. */
	public static final int LEMBAR_ANGGARAN = 32 * 1024;
	/** Hard cap: a sheet this big is a bug and is not sent. */
	public static final int LEMBAR_KERAS = 512 * 1024;
	/** Actions per player per second (one second = 20 game ticks); the rest are dropped silently. */
	public static final int AKSI_PER_DETIK = 10;

	/** A screen's action handler (C38.UI1). {@code op} has passed every check of the class javadoc. */
	@FunctionalInterface
	public interface Penangan {
		void aksi(ServerPlayer p, int npcId, String op);
	}

	/**
	 * Builds the whole sheet of a screen for one player and entity ({@code npcId -1}: hub or anchor screen). Returns
	 * {@code null} when this entity does not serve the screen for this player (the open is then refused silently).
	 */
	@FunctionalInterface
	public interface Pembangun {
		@Nullable
		CompoundTag bangun(ServerPlayer p, int npcId);
	}

	private record Entri(Penangan penangan, @Nullable Pembangun pembangun, boolean hub) {
	}

	private record Sesi(String layar, int npcId, @Nullable BlockPos jangkar, ResourceKey<Level> dimensi) {
	}

	private static final Map<String, Entri> LAYAR = new HashMap<>();
	private static final Map<UUID, Sesi> SESI = new HashMap<>();
	private static final Map<UUID, long[]> LAJU = new HashMap<>();
	private static final Set<String> BESAR_DILAPOR = new HashSet<>();
	private static boolean siap;

	/** Registers the two payloads, the action receiver and session cleanup. Idempotent. */
	public static void init() {
		if (siap) {
			return;
		}
		siap = true;
		PayloadTypeRegistry.clientboundPlay().register(TempatLayarPayload.TYPE, TempatLayarPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TempatAksiPayload.TYPE, TempatAksiPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TempatAksiPayload.TYPE, (payload, context) -> onAksi(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lupakan(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SESI.clear();
			LAJU.clear();
		});
		PapanHidup.init();
		UjiUi.init();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Registry

	/** C38.UI1: the handler of screen {@code layar}; the module sends its own sheets with {@link #buka}. */
	public static void daftar(String layar, Penangan p) {
		daftar(layar, p, null, false);
	}

	/** As {@link #daftar(String, Penangan)} plus a builder: op {@code lihat} opens, every action refreshes. */
	public static void daftar(String layar, Penangan p, @Nullable Pembangun b) {
		daftar(layar, p, b, false);
	}

	/**
	 * A screen that can also be opened from a hub card without NPC ({@code npcId -1}, read only), for example
	 * {@link LembarKunci.BukuKas#LAYAR}, {@link LembarKunci.PetaKota#LAYAR}, {@link LembarKunci.Jurnal#LAYAR}.
	 */
	public static void daftarHub(String layar, Penangan p, Pembangun b) {
		daftar(layar, p, b, true);
	}

	private static void daftar(String layar, Penangan p, @Nullable Pembangun b, boolean hub) {
		if (!idSah(layar) || p == null) {
			throw new IllegalArgumentException("bad tempat screen id or handler: " + layar);
		}
		if (LAYAR.put(layar, new Entri(p, b, hub)) != null) {
			Kultivator.LOGGER.warn("Tempat screen {} registered twice; the last handler wins", layar);
		}
	}

	public static boolean terdaftar(String layar) {
		return LAYAR.containsKey(layar);
	}

	/** Screen ids are {@code [a-z0-9_]}, 1 to {@link #LAYAR_MAKS} chars. */
	public static boolean idSah(@Nullable String layar) {
		if (layar == null || layar.isEmpty() || layar.length() > LAYAR_MAKS) {
			return false;
		}
		for (int i = 0; i < layar.length(); i++) {
			char c = layar.charAt(i);
			if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_')) {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Opening, refreshing, closing

	/**
	 * C38.UI1: opens (or replaces) screen {@code layar} for entity {@code npcId} ({@code -1}: hub screen, read only) with
	 * {@code lembar}. Starts the session that later actions are checked against.
	 */
	public static void buka(ServerPlayer player, String layar, int npcId, CompoundTag lembar) {
		kirim(player, layar, Math.max(-1, npcId), null, true, lembar);
	}

	/**
	 * Opens screen {@code layar} at a service anchor block without NPC body (a station disc, a stall counter, the record
	 * office desk): {@code npcId -1}, actions allowed within {@link #JANGKAU} of {@code jangkar}.
	 */
	public static void bukaDi(ServerPlayer player, String layar, BlockPos jangkar, CompoundTag lembar) {
		kirim(player, layar, -1, jangkar.immutable(), true, lembar);
	}

	/** Refreshes {@code layar} only for a player whose open session is that screen for {@code npcId}. */
	public static void segarkan(ServerPlayer player, String layar, int npcId, CompoundTag lembar) {
		Sesi sesi = SESI.get(player.getUUID());
		if (sesi == null || sesi.npcId != npcId || !sesi.layar.equals(layar)) {
			return;
		}
		kirim(player, layar, npcId, sesi.jangkar, false, lembar);
	}

	/** Builds and opens a screen registered with a {@link Pembangun}; false if none or the builder refused. */
	public static boolean bukaDari(ServerPlayer player, String layar, int npcId) {
		Entri entri = LAYAR.get(layar);
		if (entri == null || entri.pembangun == null) {
			return false;
		}
		CompoundTag lembar = bangun(entri, player, npcId);
		if (lembar == null) {
			return false;
		}
		buka(player, layar, npcId, lembar);
		return true;
	}

	/** Closes the player's open tempat screen (jail, a scene, the NPC left) and ends the session. */
	public static void tutup(ServerPlayer player) {
		if (SESI.remove(player.getUUID()) != null) {
			Net.send(player, TempatLayarPayload.tutup());
		}
	}

	/** True while the player's session shows {@code layar} (any entity). */
	public static boolean sedangMelihat(ServerPlayer player, String layar) {
		Sesi sesi = SESI.get(player.getUUID());
		return sesi != null && sesi.layar.equals(layar);
	}

	/** The open screen id of the player's session, or null. */
	public static @Nullable String layarTerbuka(ServerPlayer player) {
		Sesi sesi = SESI.get(player.getUUID());
		return sesi == null ? null : sesi.layar;
	}

	/** The entity id of the player's session ({@code -1} for hub/anchor screens or no session). */
	public static int npcTerbuka(ServerPlayer player) {
		Sesi sesi = SESI.get(player.getUUID());
		return sesi == null ? -1 : sesi.npcId;
	}

	private static void kirim(ServerPlayer player, String layar, int npcId, @Nullable BlockPos jangkar, boolean buka, CompoundTag lembar) {
		if (!idSah(layar)) {
			Kultivator.LOGGER.warn("Tempat screen with a bad id not sent: {}", layar);
			return;
		}
		CompoundTag isi = lembar == null ? new CompoundTag() : lembar;
		int ukuran = isi.sizeInBytes();
		if (ukuran > LEMBAR_KERAS) {
			Kultivator.LOGGER.error("Tempat sheet {} is {} bytes (cap {}); not sent", layar, ukuran, LEMBAR_KERAS);
			return;
		}
		if (ukuran > LEMBAR_ANGGARAN && BESAR_DILAPOR.add(layar)) {
			Kultivator.LOGGER.warn("Tempat sheet {} is {} bytes, over the {} byte budget (38.D.7)", layar, ukuran, LEMBAR_ANGGARAN);
		}
		if (buka) {
			SESI.put(player.getUUID(), new Sesi(layar, npcId, jangkar, player.level().dimension()));
		}
		Net.send(player, new TempatLayarPayload(layar, npcId, buka, isi));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Actions

	static void onAksi(ServerPlayer player, TempatAksiPayload aksi) {
		if (player.isRemoved() || !player.isAlive() || player.isSpectator()) {
			return;
		}
		String layar = aksi.layar();
		String op = aksi.op();
		if (!idSah(layar) || op.isEmpty() || !lajuBoleh(player)) {
			return;
		}
		String verb = LembarKunci.verb(op);
		UUID id = player.getUUID();
		Sesi sesi = SESI.get(id);
		boolean sesiIni = sesi != null && sesi.layar.equals(layar) && sesi.npcId == aksi.npcId();
		if (LembarKunci.OP_TUTUP.equals(verb)) {
			if (sesiIni) {
				SESI.remove(id);
			}
			return;
		}
		Entri entri = LAYAR.get(layar);
		if (entri == null) {
			return;
		}
		boolean lihat = LembarKunci.OP_LIHAT.equals(verb) && entri.pembangun != null;
		if (aksi.npcId() >= 0) {
			Entity entity = player.level().getEntity(aksi.npcId());
			if (entity == null || entity.isRemoved() || !entity.isAlive() || entity.distanceToSqr(player) > JANGKAU * JANGKAU) {
				jauh(player, sesiIni);
				return;
			}
			if (!sesiIni && !lihat) {
				return;
			}
		} else if (sesiIni && sesi.jangkar != null) {
			if (player.level().dimension() != sesi.dimensi || player.distanceToSqr(Vec3.atCenterOf(sesi.jangkar)) > JANGKAU * JANGKAU) {
				jauh(player, true);
				return;
			}
		} else if (sesiIni) {
			if (!LembarKunci.opBaca(verb)) {
				return;
			}
		} else if (!(lihat && entri.hub)) {
			return;
		}
		if (lihat) {
			CompoundTag lembar = bangun(entri, player, aksi.npcId());
			if (lembar != null) {
				if (sesiIni && sesi.jangkar != null) {
					kirim(player, layar, -1, sesi.jangkar, true, lembar);
				} else {
					buka(player, layar, aksi.npcId(), lembar);
				}
			}
			return;
		}
		try {
			entri.penangan.aksi(player, aksi.npcId(), op);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Tempat action {} on {} failed", op, layar, exception);
			return;
		}
		Sesi sesudah = SESI.get(id);
		if (entri.pembangun != null && sesudah != null && sesudah.layar.equals(layar) && sesudah.npcId == aksi.npcId()) {
			CompoundTag lembar = bangun(entri, player, aksi.npcId());
			if (lembar != null) {
				kirim(player, layar, aksi.npcId(), sesudah.jangkar, false, lembar);
			}
		}
	}

	private static @Nullable CompoundTag bangun(Entri entri, ServerPlayer player, int npcId) {
		try {
			return entri.pembangun.bangun(player, npcId);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Tempat sheet builder failed", exception);
			return null;
		}
	}

	private static void jauh(ServerPlayer player, boolean akhiriSesi) {
		Msg.actionBar(player, Component.translatable("kultivator.kota.aksi.jauh").withStyle(ChatFormatting.RED));
		if (akhiriSesi) {
			tutup(player);
		}
	}

	private static boolean lajuBoleh(ServerPlayer player) {
		long detik = player.level().getGameTime() / 20L;
		long[] laju = LAJU.computeIfAbsent(player.getUUID(), k -> new long[] {detik, 0L});
		if (laju[0] != detik) {
			laju[0] = detik;
			laju[1] = 0L;
		}
		return ++laju[1] <= AKSI_PER_DETIK;
	}

	private static void lupakan(UUID id) {
		SESI.remove(id);
		LAJU.remove(id);
	}

	private TempatLayar() {
	}
}
