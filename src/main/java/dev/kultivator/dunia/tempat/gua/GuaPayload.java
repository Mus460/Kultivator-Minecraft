package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.Kultivator;
import dev.kultivator.network.Net;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client {@code kultivator:tempat_gua}: the pictures of caves and natural sites (DESIGN 38.C.4, 38.C.5),
 * drawn only by {@code client/dunia/tempat/gua/GuaClient} (title cards through {@code TitleCard}, the armillary and
 * incense through {@code HiasanDunia}; layered quads under {@code EffectQuality}, no post chain).
 *
 * <pre>
 * jenis        data keys
 * kartu        nama S (place key kultivator.gua.tempat.&lt;nama&gt;), ranah S (realm id or ""), bawah B (below the suggested realm)
 * stempel      judul S (translation key), segel S (one CJK glyph), warna I (RGB)
 * hias         grup S, isi list of {j S (HiasanDunia.Jenis name), x y z D, yaw F, ukuran F}
 * hias_hapus   grup S
 * kamera       kunci S (moment key, e.g. "gua/bintang"), x y z D (the subject), judul S, sub S (subtitle key
 *              kultivator.gua.kamera.&lt;last part of kunci&gt;), segel S, warna I: a short
 *              wide pull-back of one's own moment after the danger is over (DuniaScenes: ShotGuard, Kamera sinematik
 *              Penuh / Singkat / Mati, at most 4 s, skippable), with the moment's card
 * </pre>
 */
public record GuaPayload(String jenis, CompoundTag data) implements CustomPacketPayload {

	public static final Type<GuaPayload> TYPE = new Type<>(Kultivator.id("tempat_gua"));
	public static final StreamCodec<RegistryFriendlyByteBuf, GuaPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeUtf(p.jenis, 24);
				ByteBufCodecs.COMPOUND_TAG.encode(buf, p.data);
			},
			buf -> new GuaPayload(buf.readUtf(24), ByteBufCodecs.COMPOUND_TAG.decode(buf)));

	public static final String KARTU = "kartu";
	public static final String STEMPEL = "stempel";
	public static final String HIAS = "hias";
	public static final String HIAS_HAPUS = "hias_hapus";
	public static final String KAMERA = "kamera";

	private static boolean registered;

	public GuaPayload {
		jenis = jenis == null ? "" : jenis;
		data = data == null ? new CompoundTag() : data;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	public static void kirim(ServerPlayer player, String jenis, CompoundTag data) {
		Net.send(player, new GuaPayload(jenis, data));
	}

	/** The entry card of a cave (a banner; red when the player is below the suggested realm). */
	public static void kartu(ServerPlayer player, String nama, String ranah, boolean bawah) {
		CompoundTag tag = new CompoundTag();
		tag.putString("nama", nama);
		tag.putString("ranah", ranah == null ? "" : ranah);
		tag.putBoolean("bawah", bawah);
		kirim(player, KARTU, tag);
	}

	/** A stamp card (a trial passed, a puzzle solved, a lair cleared). */
	public static void stempel(ServerPlayer player, String judul, String segel, int warna) {
		CompoundTag tag = new CompoundTag();
		tag.putString("judul", judul);
		tag.putString("segel", segel);
		tag.putInt("warna", warna);
		kirim(player, STEMPEL, tag);
	}

	/**
	 * A wide shot of the player's own moment at {@code at} (only sent when the danger is over: a passed star calamity on
	 * the stage). The client decides whether a camera may move at all.
	 */
	public static void kamera(ServerPlayer player, String kunci, Vec3 at, String judul, String segel, int warna) {
		CompoundTag tag = new CompoundTag();
		tag.putString("kunci", kunci);
		tag.putDouble("x", at.x);
		tag.putDouble("y", at.y);
		tag.putDouble("z", at.z);
		tag.putString("judul", judul);
		tag.putString("segel", segel);
		tag.putInt("warna", warna);
		tag.putString("sub", "kultivator.gua.kamera." + kunci.substring(kunci.lastIndexOf('/') + 1));
		kirim(player, KAMERA, tag);
	}

	/** One decoration entry for {@link #hias}. */
	public static CompoundTag hiasan(String jenis, Vec3 at, float yaw, float ukuran) {
		CompoundTag tag = new CompoundTag();
		tag.putString("j", jenis);
		tag.putDouble("x", at.x);
		tag.putDouble("y", at.y);
		tag.putDouble("z", at.z);
		tag.putFloat("yaw", yaw);
		tag.putFloat("ukuran", ukuran);
		return tag;
	}

	/** Places a client decoration group for {@code player}. */
	public static void hias(ServerPlayer player, String grup, ListTag isi) {
		CompoundTag tag = new CompoundTag();
		tag.putString("grup", grup);
		tag.put("isi", isi);
		kirim(player, HIAS, tag);
	}

	/** Removes a client decoration group for every player of {@code level} within 160 blocks of {@code at}. */
	public static void hapusHias(ServerLevel level, Vec3 at, String grup) {
		CompoundTag tag = new CompoundTag();
		tag.putString("grup", grup);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(at) <= 160.0D * 160.0D) {
				kirim(p, HIAS_HAPUS, tag);
			}
		}
	}
}
