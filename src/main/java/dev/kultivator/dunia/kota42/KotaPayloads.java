package dev.kultivator.dunia.kota42;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The payloads of Kota yang Hidup (DESIGN 42.13.2). Types are registered always (both sides, from
 * {@code KotaHidup.registerContent}); senders check {@code canSend}, so a client without 42 is never sent anything. Ages,
 * moods and looks of bodies use the existing Sosok synced data (no per-resident payload); screens go through the 38 UI kit.
 * <ul>
 * <li>{@link Suasana} {@code kota_suasana}: a place's atmosphere (event bits, prosperity, mood, season and festival,
 * protector emblem, lantern mode, up to 16 special houses: wedding / mourning / new year), on entering within 160 blocks
 * and on change; at most 128 bytes.</li>
 * <li>{@link TitikVisual} {@code kota_titik}: visual titik of one chunk (lanterns, chimneys, banners, sleeping cats,
 * gravestones with names, street names, displays) as relative shorts, once per chunk per session within 128 blocks.</li>
 * <li>{@link Upacara} {@code kota_upacara}: a ceremony's path (up to 32 nodes) and participants (up to 24 entity ids),
 * at start and on phase change.</li>
 * <li>{@link Kavling} {@code kota_kavling}: a plot's scaffold boxes (up to 16), stage and plan within 96 blocks.</li>
 * <li>{@link Kejar} {@code kota_kejar}: the pickpocket chase HUD (thief entity id, ticks left) at 4 Hz.</li>
 * <li>{@link Irama} {@code kota_irama}: the rhythm HUD bytes ({@code KotaLembar.HudIrama} layout), both directions, at
 * most {@value #IRAMA_PER_DETIK} per second from a client.</li>
 * <li>{@link Ujaran} {@code kota_ujaran}: one spoken line of an NPC-NPC chat or a micro-break heard within 12 blocks
 * (speaker id, translatable component tree, gesture, duration, interjection key, face), at most {@value #UJARAN_PER_DETIK} per second
 * per player. The component is always assembled from lang keys (42.5A: no free machine text).</li>
 * <li>{@link PetaPanas} {@code kota_peta_panas}: the op debug heat map (16 x 16 cells, occupancy / target) at 1 Hz.</li>
 * </ul>
 */
public final class KotaPayloads {

	public static final int RUMAH_KHUSUS_MAKS = 16;
	public static final int TITIK_MAKS = 1024;
	public static final int TEKS_MAKS = 32;
	public static final int SIMPUL_MAKS = 32;
	public static final int PESERTA_MAKS = 24;
	public static final int KOTAK_MAKS = 16;
	public static final int PETA_SEL_MAKS = 2048;
	public static final int IRAMA_PER_DETIK = 10;
	/** Bytes of one {@link Irama} ({@code KotaLembar.HudIrama.DATA_MAKS}). */
	public static final int IRAMA_DATA_MAKS = 16;
	public static final int UJARAN_PER_DETIK = 8;

	/** Kinds of {@link EntriTitik#jenis}. */
	public static final byte TITIK_LENTERA = 0;
	public static final byte TITIK_CEROBONG = 1;
	public static final byte TITIK_PANJI = 2;
	public static final byte TITIK_SPANDUK = 3;
	public static final byte TITIK_KUCING_TIDUR = 4;
	/** {@code data} = index into {@link TitikVisual#teks} (the engraved name). */
	public static final byte TITIK_NISAN = 5;
	/** {@code data} = index into {@link TitikVisual#teks} (the street name). */
	public static final byte TITIK_NAMA_JALAN = 6;
	public static final byte TITIK_PAJANGAN = 7;

	/** Kinds of {@link EntriRumah#jenis}. */
	public static final byte RUMAH_NIKAH = 0;
	public static final byte RUMAH_DUKA = 1;
	public static final byte RUMAH_TAHUN_BARU = 2;

	// ================================================================== kota_suasana

	public record EntriRumah(int rumah, byte jenis) {
	}

	/**
	 * @param acara     event bits (festival, wedding, funeral, attack, war, night market, Ghost Market, rain...)
	 * @param kemakmuran 0-100
	 * @param suasana   mean mood -100..100
	 * @param musim     0-3 (semi, panas, gugur, dingin)
	 * @param hariRaya  festival id or 0
	 * @param lambang   protector emblem packed, or -1
	 * @param lentera   lantern mode (0 off, 1 evening, 2 festival, 3 mourning white)
	 */
	public record Suasana(long situs, int acara, int kemakmuran, int suasana, int musim, int hariRaya, int lambang, int lentera,
			List<EntriRumah> rumah) implements CustomPacketPayload {

		public static final Type<Suasana> TYPE = new Type<>(Kultivator.id("kota_suasana"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Suasana> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeLong(p.situs);
					buf.writeInt(p.acara);
					buf.writeByte(p.kemakmuran);
					buf.writeByte(p.suasana);
					buf.writeByte(p.musim);
					buf.writeByte(p.hariRaya);
					buf.writeInt(p.lambang);
					buf.writeByte(p.lentera);
					int n = Math.min(RUMAH_KHUSUS_MAKS, p.rumah.size());
					buf.writeByte(n);
					for (int i = 0; i < n; i++) {
						buf.writeVarInt(p.rumah.get(i).rumah());
						buf.writeByte(p.rumah.get(i).jenis());
					}
				},
				buf -> {
					long situs = buf.readLong();
					int acara = buf.readInt();
					int kemakmuran = buf.readUnsignedByte();
					int suasana = buf.readByte();
					int musim = buf.readUnsignedByte();
					int hariRaya = buf.readUnsignedByte();
					int lambang = buf.readInt();
					int lentera = buf.readUnsignedByte();
					int n = Math.min(RUMAH_KHUSUS_MAKS, buf.readUnsignedByte());
					List<EntriRumah> list = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						list.add(new EntriRumah(buf.readVarInt(), buf.readByte()));
					}
					return new Suasana(situs, acara, kemakmuran, suasana, musim, hariRaya, lambang, lentera, List.copyOf(list));
				});

		@Override
		public Type<Suasana> type() {
			return TYPE;
		}
	}

	// ================================================================== kota_titik

	/** One visual titik: offset from the chunk origin (x/z 0-15, y absolute), kind, data. */
	public record EntriTitik(short x, short y, short z, byte jenis, short data) {
	}

	/** The visual titik of chunk {@code chunk} ({@code ChunkPos.toLong}) of place {@code situs}, plus engraved texts. */
	public record TitikVisual(long situs, long chunk, List<EntriTitik> titik, List<String> teks) implements CustomPacketPayload {

		public static final Type<TitikVisual> TYPE = new Type<>(Kultivator.id("kota_titik"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TitikVisual> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeLong(p.situs);
					buf.writeLong(p.chunk);
					int n = Math.min(TITIK_MAKS, p.titik.size());
					buf.writeVarInt(n);
					for (int i = 0; i < n; i++) {
						EntriTitik e = p.titik.get(i);
						buf.writeShort(e.x());
						buf.writeShort(e.y());
						buf.writeShort(e.z());
						buf.writeByte(e.jenis());
						buf.writeShort(e.data());
					}
					int t = Math.min(TEKS_MAKS, p.teks.size());
					buf.writeByte(t);
					for (int i = 0; i < t; i++) {
						buf.writeUtf(p.teks.get(i), 32);
					}
				},
				buf -> {
					long situs = buf.readLong();
					long chunk = buf.readLong();
					int n = Math.min(TITIK_MAKS, buf.readVarInt());
					List<EntriTitik> list = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						list.add(new EntriTitik(buf.readShort(), buf.readShort(), buf.readShort(), buf.readByte(), buf.readShort()));
					}
					int t = Math.min(TEKS_MAKS, buf.readUnsignedByte());
					List<String> teks = new ArrayList<>(t);
					for (int i = 0; i < t; i++) {
						teks.add(buf.readUtf(32));
					}
					return new TitikVisual(situs, chunk, List.copyOf(list), List.copyOf(teks));
				});

		@Override
		public Type<TitikVisual> type() {
			return TYPE;
		}
	}

	// ================================================================== kota_upacara

	/**
	 * A ceremony: {@code id} (place-unique), {@code jenis} ({@code JenisUpacara} ordinal), path nodes packed as
	 * {@code BlockPos.asLong}, participant entity ids, {@code fase} (-1 = ended: the client drops it).
	 */
	public record Upacara(int id, int jenis, long[] simpul, int[] peserta, int fase) implements CustomPacketPayload {

		public static final Type<Upacara> TYPE = new Type<>(Kultivator.id("kota_upacara"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Upacara> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.id);
					buf.writeByte(p.jenis);
					int n = Math.min(SIMPUL_MAKS, p.simpul.length);
					buf.writeByte(n);
					for (int i = 0; i < n; i++) {
						buf.writeLong(p.simpul[i]);
					}
					int m = Math.min(PESERTA_MAKS, p.peserta.length);
					buf.writeByte(m);
					for (int i = 0; i < m; i++) {
						buf.writeVarInt(p.peserta[i]);
					}
					buf.writeByte(p.fase);
				},
				buf -> {
					int id = buf.readVarInt();
					int jenis = buf.readUnsignedByte();
					int n = Math.min(SIMPUL_MAKS, buf.readUnsignedByte());
					long[] simpul = new long[n];
					for (int i = 0; i < n; i++) {
						simpul[i] = buf.readLong();
					}
					int m = Math.min(PESERTA_MAKS, buf.readUnsignedByte());
					int[] peserta = new int[m];
					for (int i = 0; i < m; i++) {
						peserta[i] = buf.readVarInt();
					}
					return new Upacara(id, jenis, simpul, peserta, buf.readByte());
				});

		@Override
		public Type<Upacara> type() {
			return TYPE;
		}
	}

	// ================================================================== kota_kavling

	/** A plot: scaffold boxes as 6 shorts each (min x/y/z, max x/y/z relative to {@code asal}), stage 0-4, plan id. */
	public record Kavling(long situs, int kavling, long asal, short[] kotak, int tahap, String rencana) implements CustomPacketPayload {

		public static final Type<Kavling> TYPE = new Type<>(Kultivator.id("kota_kavling"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Kavling> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeLong(p.situs);
					buf.writeVarInt(p.kavling);
					buf.writeLong(p.asal);
					int n = Math.min(KOTAK_MAKS * 6, p.kotak.length - p.kotak.length % 6);
					buf.writeByte(n / 6);
					for (int i = 0; i < n; i++) {
						buf.writeShort(p.kotak[i]);
					}
					buf.writeByte(p.tahap);
					buf.writeUtf(p.rencana, 64);
				},
				buf -> {
					long situs = buf.readLong();
					int kavling = buf.readVarInt();
					long asal = buf.readLong();
					int n = Math.min(KOTAK_MAKS, buf.readUnsignedByte()) * 6;
					short[] kotak = new short[n];
					for (int i = 0; i < n; i++) {
						kotak[i] = buf.readShort();
					}
					return new Kavling(situs, kavling, asal, kotak, buf.readUnsignedByte(), buf.readUtf(64));
				});

		@Override
		public Type<Kavling> type() {
			return TYPE;
		}
	}

	// ================================================================== kota_kejar

	/** The chase HUD: the thief's entity id (-1 = hide) and ticks left. */
	public record Kejar(int copet, int sisa) implements CustomPacketPayload {

		public static final Type<Kejar> TYPE = new Type<>(Kultivator.id("kota_kejar"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Kejar> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeInt(p.copet);
					buf.writeVarInt(Math.max(0, p.sisa));
				},
				buf -> new Kejar(buf.readInt(), buf.readVarInt()));

		@Override
		public Type<Kejar> type() {
			return TYPE;
		}
	}

	// ================================================================== kota_irama

	/**
	 * The rhythm HUD state / one beat: at most {@value #IRAMA_DATA_MAKS} unsigned bytes laid out by
	 * {@code dunia.kota42.ui.KotaLembar.HudIrama} ({@code data[0]} = kind: night watch, teaching, shadow-theatre gong; then
	 * the pattern and strike values), both directions.
	 */
	public record Irama(byte[] data) implements CustomPacketPayload {

		public static final Type<Irama> TYPE = new Type<>(Kultivator.id("kota_irama"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Irama> CODEC = StreamCodec.of(
				(buf, p) -> {
					byte[] d = p.data == null ? new byte[0] : p.data;
					buf.writeByteArray(d.length > IRAMA_DATA_MAKS ? Arrays.copyOf(d, IRAMA_DATA_MAKS) : d);
				},
				buf -> new Irama(buf.readByteArray(IRAMA_DATA_MAKS)));

		@Override
		public Type<Irama> type() {
			return TYPE;
		}
	}

	// ================================================================== kota_ujaran

	/**
	 * A spoken line ({@code cakap.Ujaran} of 42K): speaker entity id, the line ({@code Ujaran.komponen()}, a translatable
	 * tree), gesture clip id (0 = none), duration in ticks, the short interjection / voice key {@code seru} ("" = caption
	 * only) and the face ({@code KodeWajah} ordinal, -1 = keep the current face).
	 */
	public record Ujaran(int pembicara, Component teks, int gestur, int durasi, String seru, int wajah) implements CustomPacketPayload {

		public static final Type<Ujaran> TYPE = new Type<>(Kultivator.id("kota_ujaran"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ujaran> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.pembicara);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.teks);
					buf.writeVarInt(p.gestur);
					buf.writeVarInt(Math.max(0, p.durasi));
					buf.writeUtf(p.seru == null ? "" : p.seru, 64);
					buf.writeByte(p.wajah);
				},
				buf -> new Ujaran(buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
						buf.readUtf(64), buf.readByte()));

		@Override
		public Type<Ujaran> type() {
			return TYPE;
		}
	}

	// ================================================================== kota_peta_panas

	/** The debug heat map: origin cell (block x/z of its corner), width/height in 16-block cells, occupancy and target. */
	public record PetaPanas(long situs, int asalX, int asalZ, int lebar, int tinggi, byte[] hunian, byte[] target)
			implements CustomPacketPayload {

		public static final Type<PetaPanas> TYPE = new Type<>(Kultivator.id("kota_peta_panas"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PetaPanas> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeLong(p.situs);
					buf.writeInt(p.asalX);
					buf.writeInt(p.asalZ);
					buf.writeByte(p.lebar);
					buf.writeByte(p.tinggi);
					buf.writeByteArray(potong(p.hunian));
					buf.writeByteArray(potong(p.target));
				},
				buf -> new PetaPanas(buf.readLong(), buf.readInt(), buf.readInt(), buf.readUnsignedByte(), buf.readUnsignedByte(),
						buf.readByteArray(PETA_SEL_MAKS), buf.readByteArray(PETA_SEL_MAKS)));

		@Override
		public Type<PetaPanas> type() {
			return TYPE;
		}
	}

	private static byte[] potong(byte[] b) {
		if (b == null) {
			return new byte[0];
		}
		return b.length > PETA_SEL_MAKS ? Arrays.copyOf(b, PETA_SEL_MAKS) : b;
	}

	// ================================================================== registration

	private static boolean jenis;
	private static boolean penerima;
	private static volatile @Nullable BiConsumer<ServerPlayer, Irama> penerimaIrama;
	/** player -> {second, count} for the client rhythm beats and the spoken lines. */
	private static final Map<UUID, long[]> LAJU_IRAMA = new ConcurrentHashMap<>();
	private static final Map<UUID, long[]> LAJU_UJARAN = new ConcurrentHashMap<>();

	/** All eight types, always, both sides ({@code kota_irama} both directions). */
	static void daftarJenis() {
		if (jenis) {
			return;
		}
		jenis = true;
		PayloadTypeRegistry.clientboundPlay().register(Suasana.TYPE, Suasana.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TitikVisual.TYPE, TitikVisual.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Upacara.TYPE, Upacara.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Kavling.TYPE, Kavling.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Kejar.TYPE, Kejar.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Irama.TYPE, Irama.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Irama.TYPE, Irama.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Ujaran.TYPE, Ujaran.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PetaPanas.TYPE, PetaPanas.CODEC);
	}

	/** The server receiver of the rhythm beats (module on); the beat goes to the handler of 42D ({@link #setPenerimaIrama}). */
	static void daftarPenerima() {
		if (penerima) {
			return;
		}
		penerima = true;
		ServerPlayNetworking.registerGlobalReceiver(Irama.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			BiConsumer<ServerPlayer, Irama> handler = penerimaIrama;
			if (handler != null && laju(LAJU_IRAMA, player, IRAMA_PER_DETIK)) {
				handler.accept(player, payload);
			}
		});
	}

	/** Who handles the client's rhythm beats (42D street performance / 38.T rhythm HUD); one handler. */
	public static void setPenerimaIrama(@Nullable BiConsumer<ServerPlayer, Irama> handler) {
		penerimaIrama = handler;
	}

	static void lupakan(UUID player) {
		LAJU_IRAMA.remove(player);
		LAJU_UJARAN.remove(player);
	}

	// ================================================================== senders

	public static void kirim(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** Sends {@code payload} to every player within {@code radius} blocks of {@code at}. */
	public static void siar(ServerLevel level, Vec3 at, double radius, CustomPacketPayload payload) {
		for (ServerPlayer player : PlayerLookup.around(level, at, radius)) {
			kirim(player, payload);
		}
	}

	/**
	 * A spoken line to every player within {@code kotaBicara.obrolanTampilJarak} of the speaker, at most
	 * {@value #UJARAN_PER_DETIK} per player per second (extra lines are dropped for that player).
	 */
	public static void siarUjaran(ServerLevel level, Vec3 at, Ujaran ujaran) {
		double radius = KotaConfig.bicara().obrolanTampilJarak;
		for (ServerPlayer player : PlayerLookup.around(level, at, radius)) {
			if (laju(LAJU_UJARAN, player, UJARAN_PER_DETIK)) {
				kirim(player, ujaran);
			}
		}
	}

	private static boolean laju(Map<UUID, long[]> map, ServerPlayer player, int max) {
		long second = player.level().getGameTime() / 20L;
		long[] slot = map.computeIfAbsent(player.getUUID(), k -> new long[]{second, 0L});
		if (slot[0] != second) {
			slot[0] = second;
			slot[1] = 0L;
		}
		return ++slot[1] <= max;
	}

	private KotaPayloads() {
	}
}
