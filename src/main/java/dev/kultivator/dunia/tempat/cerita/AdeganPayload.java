package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.Kultivator;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.network.Net;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server to client {@code kultivator:adegan_cerita}: a short story scene (DESIGN 38.S.3: the Yamen trial, Ruyi
 * collapsing, Mo Ying's mask breaking, the coronation, a chapter card). The client ({@code AdeganCerita}) plays it under
 * the 7b rules: one camera shot of at most 4 s through {@code CinematicDirector} (Kamera sinematik Penuh / Singkat / Mati,
 * Backspace or any key skips, ShotGuard refuses in real danger), a title card and the scene's sound; with
 * {@link #KARTU_SAJA} the client shows only the card. The server already sends {@link #KARTU_SAJA} when the viewer is not
 * safe ({@code EpicMoments.safe}) or {@code cerita.adegan} is off, so a camera never moves during real danger.
 *
 * @param adegan scene id ({@code bab}, {@code ruyi_pingsan}, {@code sidang}, {@code topeng}, {@code penobatan},
 *               {@code kakek_lumpur}, {@code pelita}, ...; unknown ids show a card from {@code kultivator.cerita.adegan.<id>})
 * @param subjek entity id the shot centres on (-1: {@link #pos})
 * @param a      scene number (chapter number for {@code bab})
 * @param flags  {@link #KARTU_SAJA}
 */
public record AdeganPayload(String adegan, int subjek, Vec3 pos, int a, int flags) implements CustomPacketPayload {

	public static final int KARTU_SAJA = 1;
	public static final Type<AdeganPayload> TYPE = new Type<>(Kultivator.id("adegan_cerita"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AdeganPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeUtf(p.adegan, 48);
				buf.writeVarInt(p.subjek);
				buf.writeDouble(p.pos.x);
				buf.writeDouble(p.pos.y);
				buf.writeDouble(p.pos.z);
				buf.writeVarInt(p.a);
				buf.writeVarInt(p.flags);
			},
			buf -> new AdeganPayload(buf.readUtf(48), buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
					buf.readVarInt(), buf.readVarInt()));

	private static boolean registered;

	public AdeganPayload {
		adegan = adegan == null ? "" : adegan.length() > 48 ? adegan.substring(0, 48) : adegan;
		pos = pos == null ? Vec3.ZERO : pos;
	}

	/** Registers the payload type (both sides; from {@code Cerita.init}). */
	static void register() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** Plays scene {@code adegan} for {@code viewer} centred on {@code subject} (or {@code pos}). */
	public static void kirim(ServerPlayer viewer, String adegan, @Nullable Entity subject, Vec3 pos, int a) {
		if (viewer == null || viewer.isRemoved()) {
			return;
		}
		boolean camera = CeritaConfig.get().adegan && EpicMoments.safe(viewer, null);
		Vec3 at = subject != null ? subject.position() : pos;
		Net.send(viewer, new AdeganPayload(adegan, subject == null ? -1 : subject.getId(), at, a, camera ? 0 : KARTU_SAJA));
	}

	@Override
	public Type<AdeganPayload> type() {
		return TYPE;
	}
}
