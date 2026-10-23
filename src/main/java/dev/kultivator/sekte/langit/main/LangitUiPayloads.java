package dev.kultivator.sekte.langit.main;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The payloads of the "Sekte Langit" hub card and screen (DESIGN 37.7.9, module 37G), registered once on both sides
 * whatever the config says (K1), never in {@code network/Payloads}.
 * <ul>
 * <li>{@link Minta} (client → server): {@code action} {@link Minta#ASK} the data, {@link Minta#FERI} call the ferry,
 * {@link Minta#GERBANG} open/close the gate, {@link Minta#UNDANG} invite {@code arg}, {@link Minta#RANCANG} open the build
 * page of the hall tablet, {@link Minta#SETOR} lay the stones of the Heaven Court order.</li>
 * <li>{@link Papan} (server → client): the screen's data (a compound, see {@code LangitUi.data}).</li>
 * </ul>
 */
public final class LangitUiPayloads {

	public record Minta(int action, String arg) implements CustomPacketPayload {

		public static final int ASK = 0;
		public static final int FERI = 1;
		public static final int GERBANG = 2;
		public static final int UNDANG = 3;
		public static final int RANCANG = 4;
		public static final int SETOR = 5;

		public static final Type<Minta> TYPE = new Type<>(Kultivator.id("sekte_langit_ui_minta"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Minta> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.action);
					buf.writeUtf(p.arg == null ? "" : p.arg, 32);
				},
				buf -> new Minta(buf.readVarInt(), buf.readUtf(32)));

		@Override
		public Type<Minta> type() {
			return TYPE;
		}
	}

	public record Papan(CompoundTag data) implements CustomPacketPayload {

		public static final Type<Papan> TYPE = new Type<>(Kultivator.id("sekte_langit_ui_papan"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Papan> CODEC = StreamCodec.of(
				(buf, p) -> ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, p.data),
				buf -> new Papan(ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Papan> type() {
			return TYPE;
		}
	}

	private static boolean done;

	static void register() {
		if (done) {
			return;
		}
		done = true;
		PayloadTypeRegistry.clientboundPlay().register(Papan.TYPE, Papan.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Minta.TYPE, Minta.CODEC);
	}

	private LangitUiPayloads() {
	}
}
