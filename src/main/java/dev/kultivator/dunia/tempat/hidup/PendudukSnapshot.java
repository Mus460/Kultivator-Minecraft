package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client (DESIGN 38.N.6): the far crowd of one place for one player. Residents whose schedule puts them 48-128
 * blocks from the player become light impostors on the client ({@code client/dunia/tempat/hidup/ImpostorRenderer}); the
 * stands of an arena or a festival square during an event become a seated, cheering crowd ({@link Tribun}). Sent every
 * 40 ticks only when changed, at most {@link #MAKS} entries and {@link #MAKS_TRIBUN} seats (Minimal 0 / Normal 32 /
 * Maximal 48 drawn, the client decides). An empty payload clears the place.
 *
 * @param situs  the place's site id
 * @param ax     anchor block x (positions are relative to it)
 * @param ay     anchor block y
 * @param az     anchor block z
 * @param orang  far residents
 * @param tribun occupied stand seats
 */
public record PendudukSnapshot(long situs, int ax, int ay, int az, List<Entri> orang, List<Tribun> tribun) implements CustomPacketPayload {

	public static final int MAKS = 48;
	public static final int MAKS_TRIBUN = 160;

	/** Pose families the client animates procedurally. */
	public static final int KLIP_DIAM = 0;
	public static final int KLIP_JALAN = 1;
	public static final int KLIP_DUDUK = 2;
	public static final int KLIP_KERJA = 3;
	public static final int KLIP_SORAK = 4;

	/**
	 * One far resident.
	 *
	 * @param dx    x relative to the anchor, in half blocks
	 * @param dy    y relative to the anchor, in half blocks
	 * @param dz    z relative to the anchor, in half blocks
	 * @param yaw   facing in 256ths of a turn
	 * @param look  look seed (with {@code peran}: the same Sosok as the near body, {@code SosokGen.untukNpc})
	 * @param peran role ordinal
	 * @param klip  {@link #KLIP_DIAM}..{@link #KLIP_SORAK}
	 */
	public record Entri(short dx, short dy, short dz, byte yaw, int look, short peran, byte klip) {
	}

	/** One occupied stand seat (block offset from the anchor, yaw in 256ths); the look comes from the seat's hash. */
	public record Tribun(short dx, short dy, short dz, byte yaw) {
	}

	public static final Type<PendudukSnapshot> TYPE = new Type<>(Kultivator.id("penduduk_jauh"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PendudukSnapshot> CODEC = StreamCodec.of(PendudukSnapshot::tulis, PendudukSnapshot::baca);

	private static void tulis(RegistryFriendlyByteBuf buf, PendudukSnapshot p) {
		buf.writeLong(p.situs);
		buf.writeInt(p.ax);
		buf.writeInt(p.ay);
		buf.writeInt(p.az);
		int n = Math.min(MAKS, p.orang.size());
		buf.writeVarInt(n);
		for (int i = 0; i < n; i++) {
			Entri e = p.orang.get(i);
			buf.writeShort(e.dx);
			buf.writeShort(e.dy);
			buf.writeShort(e.dz);
			buf.writeByte(e.yaw);
			buf.writeInt(e.look);
			buf.writeShort(e.peran);
			buf.writeByte(e.klip);
		}
		int m = Math.min(MAKS_TRIBUN, p.tribun.size());
		buf.writeVarInt(m);
		for (int i = 0; i < m; i++) {
			Tribun t = p.tribun.get(i);
			buf.writeShort(t.dx);
			buf.writeShort(t.dy);
			buf.writeShort(t.dz);
			buf.writeByte(t.yaw);
		}
	}

	private static PendudukSnapshot baca(RegistryFriendlyByteBuf buf) {
		long situs = buf.readLong();
		int ax = buf.readInt();
		int ay = buf.readInt();
		int az = buf.readInt();
		int n = Math.min(MAKS, buf.readVarInt());
		List<Entri> orang = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			orang.add(new Entri(buf.readShort(), buf.readShort(), buf.readShort(), buf.readByte(), buf.readInt(), buf.readShort(), buf.readByte()));
		}
		int m = Math.min(MAKS_TRIBUN, buf.readVarInt());
		List<Tribun> tribun = new ArrayList<>(m);
		for (int i = 0; i < m; i++) {
			tribun.add(new Tribun(buf.readShort(), buf.readShort(), buf.readShort(), buf.readByte()));
		}
		return new PendudukSnapshot(situs, ax, ay, az, List.copyOf(orang), List.copyOf(tribun));
	}

	@Override
	public Type<PendudukSnapshot> type() {
		return TYPE;
	}

	/** A content hash (send only when changed). */
	public int hashIsi() {
		return 31 * orang.hashCode() + tribun.hashCode();
	}

	private static boolean registered;

	/** From {@link Hidup#init()} (both sides: the type must exist on the client too). */
	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}
}
