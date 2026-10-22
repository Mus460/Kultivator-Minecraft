package dev.kultivator.sekte.diplomasi;

import dev.kultivator.Kultivator;
import dev.kultivator.sect.hall.Halls;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The payloads of module D (DESIGN 36.2.9), registered from {@link Diplomasi#init} with {@code PayloadTypeRegistry}
 * (never {@code network/Payloads} or {@code Net}). The screen sends one {@link Request} (open, refresh, an action toward a
 * sect, an answer to an offer); the server answers with one {@link Sheet}: every sect as the hall sees it (name, emblem,
 * tier, strength, standing and stance, pact, war tally, gate if known, the chronicle, and for each action either nothing
 * (allowed) or the reason it is closed), the open offers and the world news. About 6-10 KB for eight sects.
 */
public final class DiplomasiPayloads {

	public static final int OP_OPEN = 0;
	public static final int OP_REFRESH = 1;
	public static final int OP_ACTION = 2;
	public static final int OP_ANSWER = 3;

	/** The action ids of {@code OP_ACTION}, in order (the screen's buttons; lang {@code ...diplomasi.aksi.<id>}). */
	public static final String[] ACTIONS = {"utusan", "hadiah_kecil", "hadiah_sedang", "hadiah_besar", "pakta", "sekutu", "putus", "tuntut",
			"tantang", "perang", "damai"};
	/** The offer kinds of the sheet's {@code offers} (lang {@code ...diplomasi.tawaran.<kind>}). */
	public static final String[] OFFER_KINDS = {"salam", "pakta", "sekutu", "tantang", "damai", "tuntut", "ketua"};
	/** The gifts' PK by size (small, medium, large). */
	public static final int[] GIFT_PK = {60, 150, 300};

	/** Ticks between two actions of one player (a double click never pays twice). */
	private static final int ACTION_GAP = 6;
	private static final Map<UUID, Integer> LAST_ACTION = new HashMap<>();

	/** From the screen: {@code op}, the sect id, and two numbers (the action id; or the offer id and 1 accept / 0 refuse). */
	public record Request(int op, String sect, int a, int b) implements CustomPacketPayload {

		public static final Type<Request> TYPE = new Type<>(Kultivator.id("sekte_diplomasi_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.op);
					buf.writeUtf(p.sect == null ? "" : p.sect, 16);
					buf.writeVarInt(p.a);
					buf.writeVarInt(p.b);
				},
				buf -> new Request(buf.readVarInt(), buf.readUtf(16), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/** To the screen: {@code open} opens it, else it only refreshes an open one. */
	public record Sheet(boolean open, CompoundTag sheet) implements CustomPacketPayload {

		public static final Type<Sheet> TYPE = new Type<>(Kultivator.id("sekte_diplomasi_sheet"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sheet> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeBoolean(p.open);
					buf.writeNbt(p.sheet);
				},
				buf -> {
					boolean open = buf.readBoolean();
					CompoundTag tag = buf.readNbt();
					return new Sheet(open, tag == null ? new CompoundTag() : tag);
				});

		@Override
		public Type<Sheet> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Sheet.TYPE, Sheet.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	private static void handle(ServerPlayer player, Request request) {
		MinecraftServer server = player.level().getServer();
		if (server == null || Diplomasi.server() == null) {
			return;
		}
		DiplomasiData data = DiplomasiData.get(server);
		switch (request.op) {
			case OP_OPEN -> sendSheet(player, true, request.sect);
			case OP_REFRESH -> sendSheet(player, false, request.sect);
			case OP_ACTION -> {
				SekteNpc sect = data.sect(request.sect);
				if (sect == null || request.a < 0 || request.a >= Aksi.COUNT || tooSoon(player)) {
					return;
				}
				Aksi.run(player, sect, request.a);
				sendSheet(player, false, sect.id);
			}
			case OP_ANSWER -> {
				if (tooSoon(player)) {
					return;
				}
				Utusan.answer(player, request.a, request.b == 1);
			}
			default -> {
			}
		}
	}

	private static boolean tooSoon(ServerPlayer player) {
		int now = player.level().getServer().getTickCount();
		Integer last = LAST_ACTION.get(player.getUUID());
		if (last != null && now - last < ACTION_GAP && now >= last) {
			return true;
		}
		LAST_ACTION.put(player.getUUID(), now);
		return false;
	}

	/** Opens the screen focused on {@code sectId} (and offer {@code offerId}, 0 none). */
	static void open(ServerPlayer player, @Nullable String sectId, int offerId) {
		CompoundTag sheet = build(player, sectId);
		sheet.putInt("offer", offerId);
		send(player, new Sheet(true, sheet));
	}

	static void sendSheet(ServerPlayer player, boolean open, @Nullable String focus) {
		send(player, new Sheet(open, build(player, focus)));
	}

	private static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** The sheet of the player's hall. */
	static CompoundTag build(ServerPlayer player, @Nullable String focus) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		DiplomasiData data = DiplomasiData.get(server);
		long today = Diplomasi.today(server);
		CompoundTag root = new CompoundTag();
		root.putString("focus", focus == null ? "" : focus);
		root.putLong("today", today);
		Halls.HallView hall = Halls.hallOf(owner);
		root.putBoolean("hall", hall != null);
		root.putInt("hall_level", hall == null ? 0 : hall.level());
		root.putInt("pool", Simulasi.poolOf(server, owner));
		root.putInt("kekuatan", Diplomasi.kekuatanAula(server, owner));
		root.putBoolean("perang_aktif", Diplomasi.config().perangAktif);
		root.putString("penyerbu", Perang.raiderId(data, owner));
		ListTag sects = new ListTag();
		for (SekteNpc sect : data.sects()) {
			Relasi r = data.relasi(owner, sect);
			CompoundTag s = new CompoundTag();
			s.putString("id", sect.id);
			s.putInt("i", sect.index);
			s.store("name", ComponentSerialization.CODEC, sect.name());
			s.store("master", ComponentSerialization.CODEC, sect.master());
			s.putInt("aliran", sect.aliran);
			s.putInt("tier", sect.tier);
			s.putInt("sumber", Math.max(0, sect.sumber));
			s.putInt("next", sect.nextTierCost());
			s.putInt("strength", sect.strength());
			s.putInt("lambang", sect.lambang);
			s.putInt("band", sect.band);
			s.putInt("standing", r.standing);
			s.putString("sikap", r.sikap().id());
			s.putInt("pakta", r.pakta);
			s.putBoolean("perang", r.perang);
			s.putLong("perang_hari", r.perang ? today - r.perangDay : 0L);
			s.putInt("kalah", r.perangKalah);
			s.putInt("menang", r.perangMenang);
			s.putBoolean("ditemui", r.ditemui);
			s.putBoolean("bawahan", r.bawahan);
			s.putInt("upeti_paksa", r.upetiPaksa);
			s.putBoolean("gerbang", sect.gateBorn());
			if (r.ditemui) {
				s.putInt("gx", sect.blockX);
				s.putInt("gz", sect.blockZ);
			}
			s.putInt("pembelot", sect.pembelot.size());
			SekteNpc enemy = data.sect(sect.musuhNpc);
			if (enemy != null) {
				s.store("musuh", ComponentSerialization.CODEC, enemy.name());
			}
			if (!sect.rivalLink.isEmpty()) {
				Component group = dev.kultivator.dunia.api.DuniaApi.rivals().name(sect.rivalLink);
				if (group != null) {
					s.store("klan", ComponentSerialization.CODEC, group);
				}
			}
			s.putInt("peluang_pakta", (int) Math.round(Pakta.chance(sect, r, Relasi.PAKTA_DAMAI) * 100.0D));
			s.putInt("peluang_sekutu", (int) Math.round(Pakta.chance(sect, r, Relasi.PAKTA_SEKUTU) * 100.0D));
			ListTag why = new ListTag();
			for (int a = 0; a < Aksi.COUNT; a++) {
				Component reason = Aksi.why(server, owner, sect, r, a);
				CompoundTag w = new CompoundTag();
				if (reason != null) {
					w.store("t", ComponentSerialization.CODEC, reason);
				}
				why.add(w);
			}
			s.put("why", why);
			ListTag kronik = new ListTag();
			kronik.addAll(r.kronik);
			s.put("kronik", kronik);
			sects.add(s);
		}
		root.put("sects", sects);
		ListTag offers = new ListTag();
		long now = server.overworld().getGameTime();
		for (CompoundTag o : Utusan.offers(data, owner)) {
			CompoundTag c = o.copy();
			c.putLong("left", Math.max(0L, o.getLongOr("until", now) - now));
			offers.add(c);
		}
		root.put("offers", offers);
		ListTag news = new ListTag();
		news.addAll(data.news);
		root.put("news", news);
		return root;
	}

	private DiplomasiPayloads() {
	}
}
