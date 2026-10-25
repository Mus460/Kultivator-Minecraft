package dev.kultivator.sekte.mandiri.seteru;

import dev.kultivator.Kultivator;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.diplomasi.DiplomasiMandiri;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The payloads of 39F (names reserved in {@code MandiriIds}): {@link Layar} (S2C, the Perseteruan screen's sheet),
 * {@link Aksi} (C2S, open/refresh/the master's actions/a sect's roster) and {@link Judul} (S2C, the 2 s title card of an
 * honour duel, sent to the players within 64 blocks once per duel). Types are registered always (K1, both sides);
 * the receiver only while the module runs. The sheet is about 4-9 KB: every feud of the hall with its people, ladder,
 * history and the state of each action (allowed, or the reason it is closed), the hall's friends and lovers, and one
 * sect's roster on request.
 */
public final class SeteruPayloads {

	private static final int JEDA_AKSI = 6;
	private static final Map<UUID, Integer> AKSI_TERAKHIR = new HashMap<>();

	/** To the screen: {@code buka} opens it, else an open screen only refreshes. */
	public record Layar(boolean buka, CompoundTag sheet) implements CustomPacketPayload {

		public static final Type<Layar> TYPE = new Type<>(Kultivator.id(MandiriIds.P_SETERU_LAYAR));
		public static final StreamCodec<RegistryFriendlyByteBuf, Layar> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeBoolean(p.buka);
					buf.writeNbt(p.sheet);
				},
				buf -> {
					boolean open = buf.readBoolean();
					CompoundTag tag = buf.readNbt();
					return new Layar(open, tag == null ? new CompoundTag() : tag);
				});

		@Override
		public Type<Layar> type() {
			return TYPE;
		}
	}

	/** From the screen: {@code op} ({@code SeteruIds.OP_*}), a feud id, an action id and a sect id (roster). */
	public record Aksi(int op, String seteru, int aksi, String sekte) implements CustomPacketPayload {

		public static final Type<Aksi> TYPE = new Type<>(Kultivator.id(MandiriIds.P_SETERU_AKSI));
		public static final StreamCodec<RegistryFriendlyByteBuf, Aksi> CODEC = StreamCodec.of(
				(buf, p) -> {
					buf.writeVarInt(p.op);
					buf.writeUtf(p.seteru == null ? "" : p.seteru, 16);
					buf.writeVarInt(p.aksi);
					buf.writeUtf(p.sekte == null ? "" : p.sekte, 16);
				},
				buf -> new Aksi(buf.readVarInt(), buf.readUtf(16), buf.readVarInt(), buf.readUtf(16)));

		@Override
		public Type<Aksi> type() {
			return TYPE;
		}
	}

	/**
	 * The honour-duel title card: "PERSETERUAN — ours (our hall) vs theirs (their sect) — cause", with both sides'
	 * colours and their emblem.
	 */
	public record Judul(Component kita, Component aulaKita, Component mereka, Component sekteMereka, String sebab, int warnaKita,
						int warnaMereka, int lambang, int tingkat) implements CustomPacketPayload {

		public static final Type<Judul> TYPE = new Type<>(Kultivator.id(MandiriIds.P_SETERU_JUDUL));
		public static final StreamCodec<RegistryFriendlyByteBuf, Judul> CODEC = StreamCodec.of(
				(buf, p) -> {
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.kita);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.aulaKita);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.mereka);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.sekteMereka);
					buf.writeUtf(p.sebab, 32);
					buf.writeInt(p.warnaKita);
					buf.writeInt(p.warnaMereka);
					buf.writeInt(p.lambang);
					buf.writeVarInt(p.tingkat);
				},
				buf -> new Judul(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
						ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
						buf.readUtf(32), buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt()));

		@Override
		public Type<Judul> type() {
			return TYPE;
		}
	}

	/** K1: the types, always, on both sides. */
	static void daftarJenis() {
		PayloadTypeRegistry.clientboundPlay().register(Layar.TYPE, Layar.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Aksi.TYPE, Aksi.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Judul.TYPE, Judul.CODEC);
	}

	/** The receiver (module on). */
	static void daftarPenerima() {
		ServerPlayNetworking.registerGlobalReceiver(Aksi.TYPE, (payload, context) -> terima(context.player(), payload));
	}

	private static void terima(ServerPlayer player, Aksi a) {
		MinecraftServer server = player.level().getServer();
		if (server == null || !Seteru.aktif()) {
			return;
		}
		switch (a.op()) {
			case SeteruIds.OP_BUKA -> kirim(player, true, a.sekte());
			case SeteruIds.OP_SEGAR, SeteruIds.OP_ROSTER -> kirim(player, false, a.sekte());
			case SeteruIds.OP_AKSI -> {
				if (terlaluCepat(player)) {
					return;
				}
				Perseteruan f = SeteruData.get(server).aula(player.getUUID()).byId(a.seteru());
				if (f == null) {
					return;
				}
				Component why = CampurTangan.jalankan(server, player, f, a.aksi());
				if (why != null) {
					Msg.chat(player, Teks.t("alasan.gagal", why).withColor(0xE0A060));
				}
				kirim(player, false, f.sekte);
			}
			default -> {
			}
		}
	}

	private static boolean terlaluCepat(ServerPlayer player) {
		int now = player.level().getServer().getTickCount();
		Integer last = AKSI_TERAKHIR.get(player.getUUID());
		if (last != null && now - last < JEDA_AKSI && now >= last) {
			return true;
		}
		AKSI_TERAKHIR.put(player.getUUID(), now);
		return false;
	}

	static void kirim(ServerPlayer player, boolean buka, @Nullable String sekte) {
		CustomPacketPayload payload = new Layar(buka, lembar(player, sekte));
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** The title card to everyone within 64 blocks of the duel. */
	static void judul(ServerLevel level, BlockPos pos, Perseteruan f, Component kita, Component mereka) {
		DiplomasiService d = SekteApi.diplomasi();
		var hall = dev.kultivator.sect.hall.Halls.hallOf(f.owner);
		Component aula = hall == null ? Teks.t("aula_pemain") : Component.literal(hall.name());
		Judul card = new Judul(kita.copy(), aula, mereka.copy(), Teks.sekte(f.sekte).copy(), f.sebab, 0x7FD8C0, d.color(f.sekte),
				d.emblem(f.sekte).pack(), Math.max(0, f.tingkat));
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(pos) <= 64.0D * 64.0D && ServerPlayNetworking.canSend(player, Judul.TYPE)) {
				ServerPlayNetworking.send(player, card);
			}
		}
	}

	// ================================================================== the sheet

	private static void komp(CompoundTag tag, String key, Component value) {
		tag.store(key, ComponentSerialization.CODEC, value == null ? Component.empty() : value);
	}

	static CompoundTag lembar(ServerPlayer player, @Nullable String fokusSekte) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		long today = Ingatan.today(server);
		DiplomasiService d = SekteApi.diplomasi();
		SeteruConfig config = SeteruConfig.get();
		SeteruData data = SeteruData.get(server);
		SeteruData.Aula aula = data.aula(owner);
		CompoundTag sheet = new CompoundTag();
		sheet.putBoolean("aktif", Seteru.aktif());
		sheet.putString("intensitas", config.tingkatIntensitas().name());
		sheet.putBoolean("bolehPerang", config.perangBoleh());
		sheet.putInt("maks", config.seteruMaksAula);
		sheet.putLong("hari", today);
		Kebijakan policy = MandiriApi.kebijakanAula(owner);
		sheet.putString("kebijakan", policy.seteru().name());
		ListTag feuds = new ListTag();
		for (Perseteruan f : aula.seteru) {
			feuds.add(seteru(server, data, f, today));
		}
		sheet.put("seteru", feuds);
		// the sects of the world (roster tab)
		ListTag sects = new ListTag();
		for (String id : d.sects()) {
			CompoundTag s = new CompoundTag();
			s.putString("id", id);
			komp(s, "nama", Teks.sekte(id));
			s.putInt("lambang", d.emblem(id).pack());
			s.putInt("warna", d.color(id));
			s.putInt("aliran", d.aliran(id));
			s.putInt("tier", d.tier(id));
			s.putString("sikap", d.stance(owner, id).id());
			s.putInt("standing", d.standing(owner, id));
			s.putBoolean("pakta", DiplomasiMandiri.berpakta(server, owner, id));
			sects.add(s);
		}
		sheet.put("sekte", sects);
		// a client-sent id that names no known sect falls back too: Roster.anggota would create and save an entry for it
		String focus = fokusSekte == null || !d.sects().contains(fokusSekte) ? (d.sects().isEmpty() ? "" : d.sects().get(0)) : fokusSekte;
		sheet.putString("fokus", focus);
		ListTag roster = new ListTag();
		if (!focus.isEmpty()) {
			for (TokohSekte t : Roster.anggota(server, focus)) {
				roster.add(tokoh(t, today, d.tier(focus), null, owner, aula));
			}
		}
		sheet.put("roster", roster);
		ListTag friends = new ListTag();
		for (CompoundTag fr : aula.sahabat) {
			TokohSekte t = data.tokoh(fr.getStringOr("tokoh", ""));
			if (t == null) {
				continue;
			}
			CompoundTag one = new CompoundTag();
			komp(one, "tokoh", t.namaTampil());
			komp(one, "sekte", Teks.sekte(t.sekte));
			komp(one, "murid", MuridInfo.dari(owner, UUID.fromString(fr.getStringOr("soul", owner.toString()))).nama());
			one.putInt("sikap", t.sikap(UUID.fromString(fr.getStringOr("soul", owner.toString()))));
			friends.add(one);
		}
		sheet.put("sahabat", friends);
		CompoundTag patrol = aula.patroli;
		if (!patrol.isEmpty()) {
			CompoundTag p = new CompoundTag();
			p.putString("seteru", patrol.getStringOr("seteru", ""));
			komp(p, "sekte", Teks.sekte(patrol.getStringOr("sekte", "")));
			p.putLong("a", patrol.getLongOr("a", 0L));
			p.putLong("b", patrol.getLongOr("b", 0L));
			p.putLong("usir", patrol.getLongOr("usir", 0L));
			komp(p, "kota", Teks.kota(patrol.getLongOr("town", 0L)));
			sheet.put("patroli", p);
		}
		sheet.putInt("tubuh", TubuhSeteru.jumlah());
		return sheet;
	}

	private static CompoundTag seteru(MinecraftServer server, SeteruData data, Perseteruan f, long today) {
		DiplomasiService d = SekteApi.diplomasi();
		CompoundTag t = new CompoundTag();
		t.putString("id", f.id);
		t.putString("sekte", f.sekte);
		komp(t, "namaSekte", Teks.sekte(f.sekte));
		t.putInt("lambang", d.emblem(f.sekte).pack());
		t.putInt("warna", d.color(f.sekte));
		t.putInt("aliran", d.aliran(f.sekte));
		t.putInt("tier", d.tier(f.sekte));
		t.putString("sikap", d.stance(f.owner, f.sekte).id());
		t.putInt("standing", d.standing(f.owner, f.sekte));
		t.putString("sebab", f.sebab);
		t.putString("salah", f.salah);
		t.putInt("panas", f.panas);
		t.putInt("tingkat", f.tingkat);
		t.putInt("batas", Tangga.batas(server, f));
		t.putString("status", f.status);
		t.putLong("mulai", f.hariMulai);
		t.putLong("akhir", f.hariAkhir);
		t.putLong("sumpah", Math.max(0L, f.sumpahSampai - today));
		t.putBoolean("beku", f.beku);
		t.putBoolean("tetua", f.tetua != null);
		t.putBoolean("sergap", f.sergapTertunda);
		t.putString("adegan", f.adegan.getStringOr("jenis", ""));
		t.putBoolean("dukung", f.adegan.getBooleanOr("dukung", false));
		long left = f.adegan.getLongOr("sampai", 0L) - server.overworld().getGameTime();
		t.putLong("adeganSisa", Math.max(0L, left));
		ListTag souls = new ListTag();
		for (Component name : f.namaJiwa) {
			CompoundTag s = new CompoundTag();
			komp(s, "nama", name);
			souls.add(s);
		}
		t.put("jiwa", souls);
		ListTag them = new ListTag();
		SeteruData.Aula aula = data.aula(f.owner);
		for (String id : f.tokoh) {
			TokohSekte m = data.tokoh(id);
			if (m != null) {
				them.add(tokoh(m, today, d.tier(f.sekte), f.jiwaUtama(), f.owner, aula));
			}
		}
		t.put("tokoh", them);
		ListTag history = new ListTag();
		for (CompoundTag line : f.riwayat) {
			history.add(line.copy());
		}
		t.put("riwayat", history);
		// the state of every action: "" allowed, else a reason key
		ListTag acts = new ListTag();
		boolean lover = SeteruIds.TERLARANG.equals(f.status);
		for (int i = 0; i <= SeteruIds.AKSI_PISAHKAN; i++) {
			acts.add(StringTag.valueOf(alasan(server, f, i, today, lover)));
		}
		t.put("aksi", acts);
		return t;
	}

	/** Why action {@code aksi} is closed now ("" = allowed); the screen greys it out with this reason. */
	private static String alasan(MinecraftServer server, Perseteruan f, int aksi, long today, boolean lover) {
		if (!f.aktif()) {
			return SeteruIds.k("alasan.selesai");
		}
		if (lover != (aksi >= SeteruIds.AKSI_RESTUI)) {
			return SeteruIds.k(lover ? "alasan.kekasih" : "alasan.bukan_kekasih");
		}
		if (!f.bolehAksi(aksi, today)) {
			return SeteruIds.k("alasan.sudah_hari_ini");
		}
		return switch (aksi) {
			case SeteruIds.AKSI_UTUS_TETUA -> f.tetua != null ? SeteruIds.k("alasan.tetua_pergi") : "";
			case SeteruIds.AKSI_TUNTUT -> f.korban() ? "" : SeteruIds.k("alasan.bukan_korban");
			case SeteruIds.AKSI_DUKUNG -> DuelSeteru.JENIS.equals(f.adegan.getStringOr("jenis", "")) ? "" : SeteruIds.k("alasan.tanpa_duel");
			case SeteruIds.AKSI_TANTANG_TETUA -> f.tingkat >= SeteruIds.T_CAMPUR ? "" : SeteruIds.k("alasan.belum_t5");
			default -> "";
		};
	}

	private static CompoundTag tokoh(TokohSekte m, long today, int tier, @Nullable UUID soul, UUID owner, SeteruData.Aula aula) {
		CompoundTag t = new CompoundTag();
		t.putString("id", m.id);
		komp(t, "nama", m.namaTampil());
		t.putString("peran", m.kunciPeran());
		t.putInt("langkah", m.langkah(today, tier));
		ListTag traits = new ListTag();
		for (Sifat s : m.sifat) {
			traits.add(StringTag.valueOf(s.id()));
		}
		t.put("sifat", traits);
		t.putString("dao", m.dao);
		t.putInt("senjata", m.senjata);
		t.putBoolean("perempuan", m.perempuan());
		t.putString("status", m.status);
		t.putBoolean("luka", m.luka(today));
		t.putInt("jumpa", m.jumpa);
		if (soul != null) {
			t.putInt("sikap", m.sikap(soul));
			t.putBoolean("dendam", m.benci(soul));
		} else {
			int best = 0;
			for (Integer v : m.sikap.values()) {
				if (Math.abs(v) > Math.abs(best)) {
					best = v;
				}
			}
			t.putInt("sikap", best);
			t.putBoolean("dendam", !m.dendam.isEmpty());
		}
		boolean feud = false;
		for (Perseteruan f : aula.seteru) {
			if (f.aktif() && f.tokoh.contains(m.id)) {
				feud = true;
			}
		}
		t.putBoolean("seteru", feud);
		return t;
	}

	private SeteruPayloads() {
	}
}
