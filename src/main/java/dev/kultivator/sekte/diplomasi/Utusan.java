package dev.kultivator.sekte.diplomasi;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.data.Realm;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.mandiri.jalan.Jelmaan;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envoys both ways (DESIGN 36.2.9). An NPC sect's offer (greetings, a pact, an alliance, a tournament challenge, peace, a
 * tribute demand, the sect master's duel letter) waits one world day in the hall's list and, while the master is near
 * the hall, an envoy body with the sect's banner stands by the stele for {@code utusanMenitTunggu} minutes; talking to it
 * (or the Diplomasi screen) answers. An offer left to expire counts as refused where refusing costs standing. The
 * player's own envoy is a disciple sent away for one world day ({@code Halls.storeAway}, reason
 * {@value #REASON}): an officer of external affairs or any Golden Core+ disciple.
 */
final class Utusan {

	static final int SALAM = 0;
	static final int PAKTA = 1;
	static final int SEKUTU = 2;
	static final int TANTANG = 3;
	static final int DAMAI = 4;
	static final int TUNTUT = 5;
	static final int KETUA = 6;
	static final String[] KINDS = DiplomasiPayloads.OFFER_KINDS;
	static final String REASON = "sekte_diplomasi_utusan";
	static final String OFFERS = "tawaran";
	/** Offers open at once per hall. */
	static final int MAX_OFFERS = 6;
	/** Days between two envoys of the hall to one sect. */
	static final int UTUSAN_JEDA = 3;

	private record Envoy(UUID owner, int offerId, String sectId, long leaveAt) {
	}

	private static final Map<UUID, Envoy> BODIES = new HashMap<>();
	private static final Map<UUID, DiscipleEntity> ENTITIES = new HashMap<>();
	/** Envoy bodies that already bowed to their master when it came near. */
	private static final java.util.Set<UUID> BOWED = new java.util.HashSet<>();
	/** Returned envoys whose memory is written when their body is back in the world: soul → sect id. */
	private static final Map<UUID, String> RETURNED = new HashMap<>();

	static void init() {
		Halls.addReturnHandler(REASON, (level, owner, stored, info) -> {
			onReturn(level.getServer(), owner, info);
			return null;
		});
	}

	static void clear() {
		for (DiscipleEntity body : ENTITIES.values()) {
			body.discard();
		}
		BODIES.clear();
		ENTITIES.clear();
		BOWED.clear();
		RETURNED.clear();
	}

	// ================================================================== offers from the NPC sects

	static void offer(MinecraftServer server, UUID owner, SekteNpc sect, int kind) {
		DiplomasiData data = DiplomasiData.get(server);
		CompoundTag hall = data.hall(owner);
		ListTag list = hall.getListOrEmpty(OFFERS);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag o = list.getCompoundOrEmpty(i);
			if (o.getStringOr("sect", "").equals(sect.id) && o.getIntOr("kind", -1) == kind) {
				return;
			}
		}
		if (list.size() >= MAX_OFFERS) {
			list.remove(0);
		}
		int id = hall.getIntOr("tawaran_id", 0) + 1;
		hall.putInt("tawaran_id", id);
		long now = server.overworld().getGameTime();
		CompoundTag o = new CompoundTag();
		o.putInt("id", id);
		o.putString("sect", sect.id);
		o.putInt("kind", kind);
		o.putLong("until", now + (kind == KETUA ? 48000L : 24000L));
		list.add(o);
		hall.put(OFFERS, list);
		long today = Diplomasi.today(server);
		data.relasi(owner, sect).note(today, Component.translatable(GeneratorNama.KEY + "kronik.tawaran." + KINDS[kind]));
		data.setDirty();
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player == null) {
			return;
		}
		MutableComponent line = Component.translatable(GeneratorNama.KEY + "pesan.tawaran." + KINDS[kind], sect.name());
		line.append(" ").append(Component.translatable(GeneratorNama.KEY + "pesan.buka").withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand("/kultivasi perguruan diplomasi"))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(GeneratorNama.KEY + "pesan.buka.tip")))));
		player.sendSystemMessage(line.withStyle(kind == TUNTUT || kind == KETUA ? ChatFormatting.RED : ChatFormatting.YELLOW));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 0.5F, 1.4F);
		if (kind != KETUA && Diplomasi.config().utusanBerwujud) {
			spawnEnvoy(server, owner, player, sect, id);
		}
	}

	/** The envoy body by the stele, while the master is within 160 blocks of the hall in its dimension. */
	private static void spawnEnvoy(MinecraftServer server, UUID owner, ServerPlayer player, SekteNpc sect, int offerId) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !player.level().dimension().equals(hall.dim())) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos stele = hall.pos();
		if (player.distanceToSqr(Vec3.atCenterOf(stele)) > 160.0D * 160.0D || !level.isLoaded(stele)) {
			return;
		}
		for (Envoy envoy : BODIES.values()) {
			if (envoy.owner.equals(owner)) {
				// One envoy at a time stands at a hall; the next offer waits in the list.
				return;
			}
		}
		int dx = level.getRandom().nextInt(7) - 3;
		BlockPos spot = Tubuh.ground(level, stele.offset(dx, 0, 7));
		DiscipleEntity body = Tubuh.spawn(level, Vec3.atBottomCenterOf(spot), sect, sect.memberRankStep(9), 9,
				Component.translatable(GeneratorNama.KEY + "utusan.nama", sect.name()), spot, true);
		if (body == null) {
			return;
		}
		long leaveAt = level.getGameTime() + Diplomasi.config().utusanMenitTunggu * 1200L;
		BODIES.put(body.getUUID(), new Envoy(owner, offerId, sect.id, leaveAt));
		ENTITIES.put(body.getUUID(), body);
		body.getLookControl().setLookAt(player);
		// M2-ANIM: it arrives standing at its post (flag in hand), turned to the master, and bows
		Tubuh.kerja(body, 0, true);
		Tubuh.hadap(body, Tubuh.menuju(body.position(), player.position()));
		SosokApi.tatap(body, player);
		SosokApi.gerak(body, GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 60);
		SekteSuara.say(body, "elder.envoy", "disciple." + VoiceDisciples.voiceOf(body) + ".greet");
	}

	static List<CompoundTag> offers(DiplomasiData data, UUID owner) {
		ListTag list = data.hall(owner).getListOrEmpty(OFFERS);
		List<CompoundTag> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			out.add(list.getCompoundOrEmpty(i));
		}
		return out;
	}

	/** The master answers offer {@code id}. */
	static void answer(ServerPlayer player, int id, boolean accept) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		DiplomasiData data = DiplomasiData.get(server);
		CompoundTag offer = take(data, owner, id);
		if (offer == null) {
			// Already answered or expired: the screen only needs the fresh list.
			DiplomasiPayloads.sendSheet(player, false, null);
			return;
		}
		SekteNpc sect = data.sect(offer.getStringOr("sect", ""));
		if (sect == null) {
			return;
		}
		int kind = offer.getIntOr("kind", SALAM);
		Relasi r = data.relasi(owner, sect);
		long today = Diplomasi.today(server);
		Diplomasi.meet(server, owner, sect);
		if (accept) {
			switch (kind) {
				case SALAM -> Standing.adjust(server, owner, sect, 5, "utusan_diterima");
				case PAKTA -> Pakta.damai(server, owner, sect);
				case SEKUTU -> Pakta.sekutu(server, owner, sect);
				case TANTANG -> {
					if (SekteApi.turnamen().hostGuests(player, sect.id)) {
						r.tantangDay = today;
						r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.tantang_terima"));
						Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.tantang_terima", sect.name()), ChatFormatting.GOLD);
					} else {
						Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.tantang_gagal"), ChatFormatting.GRAY);
					}
				}
				case DAMAI -> Perang.damai(server, owner, sect);
				case TUNTUT -> {
					int pk = sect.tier * 25;
					if (Halls.spendPool(owner, pk)) {
						Standing.adjust(server, owner, sect, 5, "upeti_dibayar");
						sect.sumber += pk / 5;
					} else {
						Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.pk_kurang", pk), ChatFormatting.RED);
						refuse(server, owner, sect, r, kind, today);
					}
				}
				case KETUA -> KetuaSekte.summonToHall(server, owner, sect);
				default -> {
				}
			}
		} else {
			refuse(server, owner, sect, r, kind, today);
		}
		dismissFor(owner, id, accept);
		data.setDirty();
		DiplomasiPayloads.sendSheet(player, false, sect.id);
	}

	private static void refuse(MinecraftServer server, UUID owner, SekteNpc sect, Relasi r, int kind, long today) {
		switch (kind) {
			case SALAM -> Standing.adjust(server, owner, sect, -2, "utusan_ditolak");
			case PAKTA -> Standing.adjust(server, owner, sect, -3, "pakta_ditolak");
			case SEKUTU -> Standing.adjust(server, owner, sect, -5, "pakta_ditolak");
			case TANTANG -> Standing.adjust(server, owner, sect, -5, "tantangan_ditolak");
			case DAMAI -> Standing.adjust(server, owner, sect, -5, "damai_ditolak");
			case TUNTUT -> {
				Standing.adjust(server, owner, sect, -15, "upeti_ditolak");
				if (Diplomasi.config().perangAktif && sect.agresi > 70 && r.standing < Standing.PERANG_MAX && !r.perang) {
					Perang.nyatakan(server, owner, sect, false);
				}
			}
			default -> r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.ketua_tolak"));
		}
	}

	private static @Nullable CompoundTag take(DiplomasiData data, UUID owner, int id) {
		ListTag list = data.hall(owner).getListOrEmpty(OFFERS);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag o = list.getCompoundOrEmpty(i);
			if (o.getIntOr("id", -1) == id) {
				list.remove(i);
				data.hall(owner).put(OFFERS, list);
				data.setDirty();
				return o;
			}
		}
		return null;
	}

	/** Offers past their day: refused where refusing costs (greetings −2, a challenge −5, a demand −15). */
	static void expire(MinecraftServer server, UUID owner) {
		DiplomasiData data = DiplomasiData.get(server);
		long now = server.overworld().getGameTime();
		long today = Diplomasi.today(server);
		for (CompoundTag o : offers(data, owner)) {
			if (o.getLongOr("until", 0L) > now) {
				continue;
			}
			take(data, owner, o.getIntOr("id", -1));
			SekteNpc sect = data.sect(o.getStringOr("sect", ""));
			if (sect == null) {
				continue;
			}
			int kind = o.getIntOr("kind", SALAM);
			if (kind == SALAM || kind == TANTANG || kind == TUNTUT) {
				refuse(server, owner, sect, data.relasi(owner, sect), kind, today);
			} else {
				data.relasi(owner, sect).note(today, Component.translatable(GeneratorNama.KEY + "kronik.tawaran_lewat"));
			}
			dismissFor(owner, o.getIntOr("id", -1), false);
		}
	}

	// ================================================================== envoy bodies

	/** Once a second: envoys whose time is up (or whose master left) walk out in a puff of cloud. */
	static void second(MinecraftServer server) {
		if (!BODIES.isEmpty()) {
			long now = server.overworld().getGameTime();
			for (Map.Entry<UUID, Envoy> entry : List.copyOf(BODIES.entrySet())) {
				DiscipleEntity body = ENTITIES.get(entry.getKey());
				Envoy envoy = entry.getValue();
				ServerPlayer master = server.getPlayerList().getPlayer(envoy.owner);
				if (body == null || body.isRemoved() || master == null || now >= envoy.leaveAt
						|| master.level() != body.level() || master.distanceToSqr(body) > 200.0D * 200.0D) {
					remove(entry.getKey(), true);
				} else if (master.distanceToSqr(body) < 12.0D * 12.0D) {
					body.getLookControl().setLookAt(master);
					if (BOWED.add(entry.getKey())) {
						// M2-ANIM: the master comes up to it: the bow of a messenger, eyes on the master
						SosokApi.tatap(body, master);
						SosokApi.gerak(body, GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 60);
					}
				}
			}
		}
		tampilkan(server);
		if (!RETURNED.isEmpty()) {
			for (Map.Entry<UUID, String> entry : List.copyOf(RETURNED.entrySet())) {
				remembered(server, entry.getKey(), entry.getValue());
			}
		}
	}

	/**
	 * M2-ANIM: the hall's own envoy (away, pure data) stands bowing at the gate of the sect it was sent to, turned to the gate, as a
	 * {@code Jelmaan} stage body while a player is within 128 blocks of that gate. (With module X the same envoy travels as the
	 * story mission {@code utusan}, shown by {@code TimMisi}.)
	 */
	private static void tampilkan(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID owner = player.getUUID();
			for (Halls.AwayView away : Halls.away(owner)) {
				BlockPos gate = REASON.equals(away.reason()) ? SekteApi.diplomasi().gate(away.info().getStringOr("sect", "")) : null;
				if (gate != null) {
					ServerLevel level = server.overworld();
					Jelmaan.tunjukkan(level, "utusan:" + owner + ":" + away.soul(), owner, away.soul(), null, Jelmaan.sekitar(level, gate, 0), gate,
							Jelmaan.Adegan.diam(GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 0).berulang(160), 100);
				}
			}
		}
	}

	private static void dismissFor(UUID owner, int offerId, boolean pleased) {
		for (Map.Entry<UUID, Envoy> entry : List.copyOf(BODIES.entrySet())) {
			if (entry.getValue().owner.equals(owner) && entry.getValue().offerId == offerId) {
				DiscipleEntity body = ENTITIES.get(entry.getKey());
				if (body != null) {
					Tubuh.say(body, pleased ? "thanks" : "farewell");
				}
				remove(entry.getKey(), true);
			}
		}
	}

	private static void remove(UUID id, boolean fx) {
		BODIES.remove(id);
		BOWED.remove(id);
		DiscipleEntity body = ENTITIES.remove(id);
		if (body != null) {
			if (fx) {
				Tubuh.vanish(body);
			} else {
				body.discard();
			}
		}
	}

	/** Talking to an envoy opens its offer (only its hall's master answers). */
	static boolean interact(ServerPlayer player, DiscipleEntity body) {
		Envoy envoy = BODIES.get(body.getUUID());
		if (envoy == null) {
			return false;
		}
		if (!envoy.owner.equals(player.getUUID())) {
			player.sendSystemMessage(Component.translatable(GeneratorNama.KEY + "pesan.utusan_bukan").withStyle(ChatFormatting.GRAY));
			return true;
		}
		Tubuh.say(body, "greet_alt");
		DiplomasiPayloads.open(player, envoy.sectId, envoy.offerId);
		return true;
	}

	// ================================================================== the hall's own envoy

	/** Why the hall cannot send an envoy to {@code sect} now (null: it can); {@code out[0]} gets the chosen disciple. */
	static @Nullable Component canSend(MinecraftServer server, UUID owner, SekteNpc sect, Relasi r, @Nullable DiscipleEntity[] out) {
		if (Halls.hallOf(owner) == null) {
			return Component.translatable(GeneratorNama.KEY + "alasan_tidak.aula");
		}
		if (r.perang) {
			return Component.translatable(GeneratorNama.KEY + "alasan_tidak.perang");
		}
		long today = Diplomasi.today(server);
		if (today < r.utusanDay + UTUSAN_JEDA) {
			return Component.translatable(GeneratorNama.KEY + "alasan_tidak.jeda", r.utusanDay + UTUSAN_JEDA - today);
		}
		DiscipleEntity pick = envoyOf(owner);
		if (pick == null) {
			return Component.translatable(GeneratorNama.KEY + "alasan_tidak.utusan");
		}
		if (out != null) {
			out[0] = pick;
		}
		return null;
	}

	/** The officer of external affairs (module E) if in the world and free, else the strongest free Golden Core+ disciple. */
	private static @Nullable DiscipleEntity envoyOf(UUID owner) {
		UUID officer = SekteApi.tetua().holder(owner, Jabatan.TETUA_LUAR);
		DiscipleEntity best = null;
		for (DiscipleEntity d : Disciples.loaded(owner)) {
			if (d.role() != DiscipleEntity.Role.DISCIPLE || d.activity() != 0 || SekteApi.turnamen().busy(owner, d.soulId())) {
				continue;
			}
			if (officer != null && officer.equals(d.soulId())) {
				return d;
			}
			if (d.data().realm().ordinal() >= Realm.GOLDEN_CORE.ordinal()
					&& (best == null || d.data().rank().step() > best.data().rank().step())) {
				best = d;
			}
		}
		return best;
	}

	static void send(ServerPlayer player, SekteNpc sect) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		DiscipleEntity[] pick = new DiscipleEntity[1];
		Component why = canSend(server, owner, sect, r, pick);
		if (why != null || pick[0] == null) {
			player.sendSystemMessage(why == null ? Component.translatable(GeneratorNama.KEY + "alasan_tidak.utusan") : why.copy().withStyle(ChatFormatting.RED));
			return;
		}
		DiscipleEntity disciple = pick[0];
		long today = Diplomasi.today(server);
		boolean officer = disciple.soulId().equals(SekteApi.tetua().holder(owner, Jabatan.TETUA_LUAR));
		if (SekteApi.misi().carriesEnvoys()) {
			// T13: one envoy path. Module X carries the envoy as a story mission (template utusan: stage news, a written
			// report, one standing formula); D keeps its pause between two envoys to one sect.
			if (SekteApi.misi().sendEnvoy(player, sect.id, disciple.soulId(), officer)) {
				r.utusanDay = today;
				r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.utusan_kirim", disciple.getDisplayName()));
				Diplomasi.log(server, owner, Component.translatable(GeneratorNama.KEY + "log.utusan_kirim", disciple.getDisplayName(), sect.name()));
				data.setDirty();
			}
			return;
		}
		r.utusanDay = today;
		CompoundTag info = new CompoundTag();
		info.putString("sect", sect.id);
		info.putString("name", disciple.getDisplayName().getString());
		info.putString("soul", disciple.soulId().toString());
		info.putBoolean("luar", officer);
		r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.utusan_kirim", disciple.getDisplayName()));
		Diplomasi.log(server, owner, Component.translatable(GeneratorNama.KEY + "log.utusan_kirim", disciple.getDisplayName(), sect.name()));
		SekteSuara.line(disciple, "depart", "careful", 200);
		Halls.storeAway(owner, disciple, server.overworld().getGameTime() + 24000L, REASON, info);
		data.setDirty();
	}

	private static void onReturn(MinecraftServer server, UUID owner, CompoundTag info) {
		DiplomasiData data = DiplomasiData.get(server);
		SekteNpc sect = data.sect(info.getStringOr("sect", ""));
		if (sect == null) {
			return;
		}
		boolean officer = info.getBooleanOr("luar", false);
		int gain = 5 + (officer ? 3 : 0);
		if (sect.iblis()) {
			gain = Math.max(2, gain / 2);
		}
		Diplomasi.meet(server, owner, sect);
		Standing.adjust(server, owner, sect, gain, "utusan_kita");
		Relasi r = data.relasi(owner, sect);
		long today = Diplomasi.today(server);
		r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.utusan_pulang", info.getStringOr("name", "?"), sect.blockX, sect.blockZ));
		Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.utusan_pulang", info.getStringOr("name", "?"), sect.name(),
				sect.blockX, sect.blockZ), ChatFormatting.AQUA);
		try {
			// The memory and the honour are written once the body is back in the world (the next seconds).
			RETURNED.put(UUID.fromString(info.getStringOr("soul", "")), sect.id);
		} catch (IllegalArgumentException ignored) {
			// an envoy record without a soul (older data): no memory
		}
		data.setDirty();
	}

	private static void remembered(MinecraftServer server, UUID soul, String sectId) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (DiscipleEntity d : Disciples.loaded(player.getUUID())) {
				if (d.soulId().equals(soul)) {
					RETURNED.remove(soul);
					SekteNpc sect = DiplomasiData.get(server).sect(sectId);
					DiscipleData data = d.data();
					Ingatan.remember(server, data, SekteIds.D + ".utusan", null, 3, GeneratorNama.KEY + "ingatan.utusan",
							sect == null ? "" : sect.name().getString());
					SekteBus.honored(server, player.getUUID(), soul, SekteIds.D + ".utusan", 3);
					SekteSuara.line(d, "envoy_return", "greet", 200);
					return;
				}
			}
		}
	}

	private Utusan() {
	}
}
