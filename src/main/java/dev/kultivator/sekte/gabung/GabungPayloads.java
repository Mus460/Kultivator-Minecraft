package dev.kultivator.sekte.gabung;

import dev.kultivator.Kultivator;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The payloads of Gabung Sekte (DESIGN 41.12.2). All server to client; the screens go through the 38 UI kit
 * ({@code TempatLayar}), so 41 has no client-to-server payload of its own.
 * <ul>
 * <li>{@link Lencana} {@code kultivator:gabung_lencana}: player UUID -> sect, rank, ink colour, packed emblem, robe
 * shown; sent to the player and everyone tracking them when it changes, and to a viewer who starts tracking a member
 * (about the 64-block rule). At most 64 entries.</li>
 * <li>{@link Kelas} {@code kultivator:gabung_kelas}: per known sect class, tier, alignment, style, colour, MEROSOT; at
 * login, when the directory grows and on {@code GabungBus.KELAS_BERUBAH}. At most 12 entries.</li>
 * <li>{@link Ujian} {@code kultivator:gabung_ujian}: the running entrance test's HUD (stage, Mountain Pressure, time
 * left, score, a few bytes of mini-game state); every 5 ticks during a test (41B), stage 0 = hide.</li>
 * <li>{@link Klip} {@code kultivator:gabung_klip}: a player posed with a 41 clip (oath, leaving, reflection,
 * succession): player UUID, clip 300-349 (0 = stop), prop 150-169 (0 = none), start game tick; for 41V-G's
 * {@code PlayerModelMixin} hook.</li>
 * </ul>
 * Types are registered always (K1, both sides); the senders check {@code canSend}, so a client without 41 is never sent
 * anything it cannot read.
 */
public final class GabungPayloads {

	/** Most lencana entries in one payload. */
	public static final int LENCANA_MAKS = 64;
	/** Most sects in one class payload. */
	public static final int KELAS_MAKS = 12;
	/** Most mini-game bytes in one test payload. */
	public static final int UJIAN_DATA_MAKS = 40;

	/** One member's badge as other players see it ({@code sekte} "" = no longer a member). */
	public record EntriLencana(UUID pemain, String sekte, int pangkat, int warna, int lambang, boolean jubah) {
	}

	public record Lencana(List<EntriLencana> entri) implements CustomPacketPayload {

		public static final Type<Lencana> TYPE = new Type<>(Kultivator.id("gabung_lencana"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Lencana> CODEC = StreamCodec.of(
				(buf, p) -> {
					int n = Math.min(LENCANA_MAKS, p.entri.size());
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						EntriLencana e = p.entri.get(i);
						buf.writeUUID(e.pemain());
						buf.writeUtf(e.sekte(), 8);
						buf.writeByte(e.pangkat());
						buf.writeInt(e.warna());
						buf.writeInt(e.lambang());
						buf.writeBoolean(e.jubah());
					}
				},
				buf -> {
					int n = Math.min(LENCANA_MAKS, buf.readVarInt());
					List<EntriLencana> list = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						list.add(new EntriLencana(buf.readUUID(), buf.readUtf(8), buf.readByte(), buf.readInt(), buf.readInt(), buf.readBoolean()));
					}
					return new Lencana(List.copyOf(list));
				});

		@Override
		public Type<Lencana> type() {
			return TYPE;
		}
	}

	/** One known sect's class line. */
	public record EntriKelas(String sekte, int kelas, int tier, int aliran, int corak, int warna, boolean merosot) {
	}

	public record Kelas(List<EntriKelas> entri) implements CustomPacketPayload {

		public static final Type<Kelas> TYPE = new Type<>(Kultivator.id("gabung_kelas"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Kelas> CODEC = StreamCodec.of(
				(buf, p) -> {
					int n = Math.min(KELAS_MAKS, p.entri.size());
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						EntriKelas e = p.entri.get(i);
						buf.writeUtf(e.sekte(), 8);
						buf.writeByte(e.kelas());
						buf.writeByte(e.tier());
						buf.writeByte(e.aliran());
						buf.writeByte(e.corak());
						buf.writeInt(e.warna());
						buf.writeBoolean(e.merosot());
					}
				},
				buf -> {
					int n = Math.min(KELAS_MAKS, buf.readVarInt());
					List<EntriKelas> list = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						list.add(new EntriKelas(buf.readUtf(8), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readInt(),
								buf.readBoolean()));
					}
					return new Kelas(List.copyOf(list));
				});

		@Override
		public Type<Kelas> type() {
			return TYPE;
		}
	}

	/**
	 * The entrance test HUD: {@code tahap} 0 (hide) or 1-3, {@code tekanan} Mountain Pressure 0-100, {@code waktu} ticks
	 * left, {@code skor} 0-1 of the stage, {@code data} up to {@link #UJIAN_DATA_MAKS} bytes of the style test (pillar
	 * needle, maze eyes, cups...) whose layout the 41B style test and the 41U HUD share.
	 */
	public record Ujian(int tahap, float tekanan, int waktu, float skor, byte[] data) implements CustomPacketPayload {

		public static final Type<Ujian> TYPE = new Type<>(Kultivator.id("gabung_ujian"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ujian> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeByte(p.tahap);
					buf.writeFloat(p.tekanan);
					buf.writeVarInt(Math.max(0, p.waktu));
					buf.writeFloat(p.skor);
					byte[] d = p.data == null ? new byte[0] : p.data;
					buf.writeByteArray(d.length > UJIAN_DATA_MAKS ? java.util.Arrays.copyOf(d, UJIAN_DATA_MAKS) : d);
				},
				buf -> new Ujian(buf.readByte(), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readByteArray(UJIAN_DATA_MAKS)));

		@Override
		public Type<Ujian> type() {
			return TYPE;
		}
	}

	/** A player's 41 clip: {@code klip} 300-349 or 0 (stop), {@code prop} 150-169 or 0, {@code mulai} the start game tick. */
	public record Klip(UUID pemain, int klip, int prop, long mulai) implements CustomPacketPayload {

		public static final Type<Klip> TYPE = new Type<>(Kultivator.id("gabung_klip"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Klip> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeUUID(p.pemain);
					buf.writeVarInt(p.klip);
					buf.writeVarInt(p.prop);
					buf.writeLong(p.mulai);
				},
				buf -> new Klip(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readLong()));

		@Override
		public Type<Klip> type() {
			return TYPE;
		}
	}

	private static boolean jenis;
	private static boolean penerima;

	/** K1: the four types, always, on both sides (from {@code Gabung.init}). */
	static void daftarJenis() {
		if (jenis) {
			return;
		}
		jenis = true;
		PayloadTypeRegistry.clientboundPlay().register(Lencana.TYPE, Lencana.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Kelas.TYPE, Kelas.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Ujian.TYPE, Ujian.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Klip.TYPE, Klip.CODEC);
	}

	/** The sync hooks (module on): a viewer starting to track a member gets the member's badge; class changes resend. */
	static void daftarPenerima() {
		if (penerima) {
			return;
		}
		penerima = true;
		EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
			if (entity instanceof ServerPlayer member && Gabung.aktif()) {
				EntriLencana e = entri(member);
				if (e != null && !e.sekte().isEmpty() && (e.jubah() || viewer == member)) {
					kirim(viewer, new Lencana(List.of(e)));
				}
			}
		});
		GabungBus.KELAS_BERUBAH.register((server, sekte, lama, baru) -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				kirimKelas(p);
			}
		});
	}

	// ================================================================== senders

	/** The badge of {@code player} to the player and to everyone tracking them (only to the player when robes are not synced). */
	public static void siarkanLencana(ServerPlayer player) {
		EntriLencana e = entri(player);
		if (e == null) {
			e = new EntriLencana(player.getUUID(), "", 0, 0, 0, false);
		}
		Lencana payload = new Lencana(List.of(e));
		kirim(player, payload);
		if (GabungConfig.get().jubahPemain) {
			for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
				if (viewer != player) {
					kirim(viewer, payload);
				}
			}
		}
	}

	/** At login: the player's own badge and the badges of the members the player already tracks. */
	static void kirimLencanaAwal(ServerPlayer player) {
		List<EntriLencana> list = new ArrayList<>();
		EntriLencana own = entri(player);
		if (own != null) {
			list.add(own);
		}
		if (GabungConfig.get().jubahPemain) {
			for (ServerPlayer other : PlayerLookup.around(player.level(), player.position(), 64.0D)) {
				if (other != player && list.size() < LENCANA_MAKS) {
					EntriLencana e = entri(other);
					if (e != null && !e.sekte().isEmpty()) {
						list.add(e);
					}
				}
			}
		}
		if (!list.isEmpty()) {
			kirim(player, new Lencana(List.copyOf(list)));
		}
	}

	/** The class lines of every sect in the player's directory (plus the own sect). */
	public static void kirimKelas(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		Keanggotaan k = GabungData.get(server).peek(player.getUUID());
		if (k == null) {
			return;
		}
		DiplomasiService d = SekteApi.diplomasi();
		GerbangPlan.Plan plan = GerbangPlan.of(server.overworld().getSeed());
		List<EntriKelas> list = new ArrayList<>();
		for (GerbangPlan.Entry e : plan.entries()) {
			String id = e.id();
			if (list.size() >= KELAS_MAKS || !(k.kenal(e.index()) || id.equals(k.sekte))) {
				continue;
			}
			int tier = Math.max(1, d.tier(id));
			GabungData.DataSekteGabung ds = GabungData.get(server).peekSekte(id);
			list.add(new EntriKelas(id, KelasSekte.of(tier), tier, Math.max(0, d.aliran(id)), Corak.of(e.kataA()).ordinal(), d.color(id),
					ds != null && ds.merosot));
		}
		if (!list.isEmpty()) {
			kirim(player, new Kelas(List.copyOf(list)));
		}
	}

	/** The test HUD to {@code player} (41B, every 5 ticks during a test; {@code tahap} 0 hides it). */
	public static void kirimUjian(ServerPlayer player, int tahap, float tekanan, int waktu, float skor, byte[] data) {
		kirim(player, new Ujian(tahap, tekanan, waktu, skor, data == null ? new byte[0] : data));
	}

	/**
	 * Poses {@code subjek} with clip {@code klip} (one of {@code GabungGerakId.KLIP_PEMAIN}, or 0 to stop) holding prop
	 * {@code prop} (0 = none) for the subject and everyone tracking them.
	 */
	public static void kirimKlip(ServerPlayer subjek, int klip, int prop) {
		if (klip != 0 && !GabungGerakId.klipPemain(klip)) {
			return;
		}
		Klip payload = new Klip(subjek.getUUID(), klip, prop, subjek.level().getGameTime());
		kirim(subjek, payload);
		for (ServerPlayer viewer : PlayerLookup.tracking(subjek)) {
			if (viewer != subjek) {
				kirim(viewer, payload);
			}
		}
	}

	private static EntriLencana entri(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = server == null ? null : GabungData.get(server).peek(player.getUUID());
		if (k == null) {
			return null;
		}
		String sekte = k.sekteAktif();
		if (sekte == null) {
			return new EntriLencana(player.getUUID(), "", 0, 0, 0, false);
		}
		DiplomasiService d = SekteApi.diplomasi();
		return new EntriLencana(player.getUUID(), sekte, k.pangkat.ordinal(), d.color(sekte), d.emblem(sekte).pack(),
				GabungConfig.get().jubahPemain);
	}

	private static void kirim(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private GabungPayloads() {
	}
}
