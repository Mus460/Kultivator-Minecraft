package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Sect Council in the Hall of the Sects (DESIGN 38.B.4.2): every thirtieth day ({@code d % 30 == 15}), 6000-9000,
 * the envoys of four to eight NPC sects (the strongest by tier, the same single sect list as the map and the tournament)
 * sit at the round table of the {@code istana/balai_pertemuan} lot: a {@link NpcRole#JUARA_SEKTE} body per seat, unarmed,
 * named for its sect. The player may bring one proposal to one sect per council, on top of the diplomacy actions that
 * already exist: a pact of friendship (standing +5) or a gift of {@code 20 x (band + 1)} NB (standing +5 and Nama Baik +1
 * with the city), through {@code DiplomasiService.adjust(owner, sect, +5, "kotabesar.pertemuan")}; the stance bands of
 * module D still bound the standing. Small event: it runs beside the day's great event.
 */
final class PertemuanSekte implements KotaBesarKalender.Acara {

	static final String ID = LembarKunci.Acara.ACARA_PERTEMUAN_SEKTE;
	static final int KURSI_MAKS = 8;
	static final int BONUS = 5;

	private final Map<Long, List<UUID>> utusan = new HashMap<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int prioritas() {
		return 0;
	}

	@Override
	public boolean hariAsli(MinecraftServer server, long town, long day) {
		return Math.floorMod(day, 30L) == 15L;
	}

	@Override
	public long mulaiJam() {
		return 6000L;
	}

	@Override
	public long akhirJam() {
		return 9000L;
	}

	/** The seated sects of this council: up to eight, the strongest first (ties by id). */
	static List<String> kursi(long town, long day) {
		DiplomasiService d = SekteApi.diplomasi();
		List<String> all = new ArrayList<>(d.sects());
		all.sort((a, b) -> d.tier(b) != d.tier(a) ? Integer.compare(d.tier(b), d.tier(a)) : a.compareTo(b));
		int seats = Math.min(all.size(), 4 + (int) Math.floorMod((town ^ day) >>> 17, 5L));
		return all.subList(0, Math.min(KURSI_MAKS, seats));
	}

	@Override
	public void mulai(ServerLevel level, Kandidat k, long town, boolean hidup) {
		MinecraftServer server = level.getServer();
		List<String> seats = kursi(town, Kota.day(server));
		if (!hidup || seats.isEmpty()) {
			if (!seats.isEmpty()) {
				CeritaApi.kabar(level, town, "kota_besar.pertemuan_sekte", KotaBesarKota.nama(server, k), seats.size());
			}
			return;
		}
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		DuniaAnchorEntity table = kota == null ? null : kota.panggung(ID);
		if (table == null) {
			return;
		}
		List<BlockPos> chairs = KotaBesarAnchors.titikPanggung(level, table);
		List<UUID> bodies = new ArrayList<>();
		DiplomasiService d = SekteApi.diplomasi();
		for (int i = 0; i < seats.size() && i < chairs.size(); i++) {
			BlockPos chair = chairs.get(i);
			BlockPos c = table.blockPosition();
			float yaw = (float) Math.toDegrees(Math.atan2(chair.getX() - c.getX(), c.getZ() - chair.getZ()));
			KultivatorNpc npc = HidupApi.tubuh(level, town, NpcRole.JUARA_SEKTE, chair, yaw);
			if (npc == null) {
				continue;
			}
			String sect = seats.get(i);
			Component name = d.name(sect);
			npc.setPlate(Component.translatable("kultivator.kota_besar.pertemuan.utusan", name == null ? Component.literal(sect) : name));
			npc.memory().putString("kb_acara", ID);
			npc.memory().putString("kb_sekte", sect);
			npc.memory().putBoolean("hold", true);
			npc.arm(false);
			npc.setLonely(96.0D, 2400);
			HidupApi.klip(npc, GerakId.DUDUK_KURSI, 0, 0);
			bodies.add(npc.getUUID());
		}
		utusan.put(town, bodies);
	}

	@Override
	public void tick(ServerLevel level, Kandidat k, long town, long now) {
		if (now % 200L >= 20L) {
			return;
		}
		List<UUID> bodies = utusan.get(town);
		if (bodies == null || bodies.size() < 2) {
			return;
		}
		// the council talks: two envoys take turns arguing, the rest listen seated
		int a = (int) Math.floorMod(now / 200L, bodies.size());
		int b = (a + 1) % bodies.size();
		for (int i = 0; i < bodies.size(); i++) {
			if (!(level.getEntity(bodies.get(i)) instanceof KultivatorNpc npc)) {
				continue;
			}
			if (i == a) {
				HidupApi.klip(npc, GerakId.BERDEBAT, 0, 160);
				npc.say(Component.translatable("kultivator.kota_besar.pertemuan.bicara." + Math.floorMod(now / 200L + i, 8L)), 150);
			} else if (i == b) {
				HidupApi.klip(npc, GerakId.BICARA_TANGAN, 0, 160);
			} else {
				HidupApi.klip(npc, GerakId.DUDUK_KURSI, 0, 0);
			}
		}
	}

	@Override
	public void akhir(ServerLevel level, Kandidat k, long town, boolean hidup) {
		List<UUID> bodies = utusan.remove(town);
		if (bodies != null) {
			for (UUID id : bodies) {
				Entity e = level.getEntity(id);
				if (e instanceof KultivatorNpc npc && npc.isAlive()) {
					npc.vanish();
				}
			}
		}
	}

	/** The council sheet: the seated sects as rows, and the proposal question for the next sect not yet passed. */
	@Override
	public void lembar(ServerPlayer player, long town, @Nullable KultivatorNpc npc, CompoundTag tag) {
		MinecraftServer server = player.level().getServer();
		long day = Kota.day(server);
		DiplomasiService d = SekteApi.diplomasi();
		List<String> seats = kursi(town, day);
		ListTag rows = new ListTag();
		for (String sect : seats) {
			CompoundTag row = new CompoundTag();
			Component name = d.name(sect);
			LembarKunci.teks(row, LembarKunci.NAMA, name == null ? Component.literal(sect) : name);
			row.putInt(LembarKunci.SKOR, d.standing(player.getUUID(), sect));
			row.putString(LembarKunci.ID, sect);
			rows.add(row);
		}
		tag.put(LembarKunci.SKOR, rows);
		LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.pertemuan.catatan", BONUS));
		if (!KotaBesarKalender.berjalan(server, town, ID)) {
			return;
		}
		CompoundTag me = KotaBesarData.get(server).pemain(player.getUUID()).getCompoundOrEmpty("pertemuan");
		CompoundTag c = new CompoundTag();
		if (me.getLongOr("hari", Long.MIN_VALUE) == day && me.getBooleanOr("selesai", false)) {
			c.putInt(LembarKunci.Acara.SISA, 0);
			LembarKunci.teks(c, LembarKunci.Acara.SOAL, Component.translatable("kultivator.kota_besar.pertemuan.sudah"));
			tag.put(LembarKunci.Acara.TEKA, c);
			return;
		}
		int lewat = me.getLongOr("hari", Long.MIN_VALUE) == day ? me.getIntOr("lewat", 0) : 0;
		String sect = null;
		for (int i = 0; i < seats.size(); i++) {
			String s = seats.get((lewat + i) % seats.size());
			if (d.stance(player.getUUID(), s) != Sikap.PERANG) {
				sect = s;
				break;
			}
		}
		if (sect == null) {
			return;
		}
		Component name = d.name(sect);
		long gift = 20L * (Barang.pita(player) + 1);
		c.putString(LembarKunci.ID, sect);
		c.putInt(LembarKunci.Acara.SISA, 1);
		LembarKunci.teks(c, LembarKunci.Acara.SOAL, Component.translatable("kultivator.kota_besar.pertemuan.soal",
				name == null ? Component.literal(sect) : name, Component.translatable("kultivator.sekte.sikap." + d.stance(player.getUUID(), sect).id())));
		ListTag answers = new ListTag();
		for (Component answer : List.of(Component.translatable("kultivator.kota_besar.pertemuan.pakta"),
				Component.translatable("kultivator.kota_besar.pertemuan.hadiah", Barang.nb(gift)),
				Component.translatable("kultivator.kota_besar.pertemuan.lewat"))) {
			CompoundTag a = new CompoundTag();
			LembarKunci.teks(a, LembarKunci.TEKS, answer);
			answers.add(a);
		}
		c.put(LembarKunci.Acara.JAWABAN, answers);
		tag.put(LembarKunci.Acara.TEKA, c);
	}

	@Override
	public void aksi(ServerPlayer player, long town, @Nullable KultivatorNpc npc, String[] op) {
		MinecraftServer server = player.level().getServer();
		if (!LembarKunci.Acara.OP_JAWAB.equals(op[0]) || !KotaBesarKalender.berjalan(server, town, ID)) {
			return;
		}
		long day = Kota.day(server);
		String sect = LembarKunci.arg(op, 1);
		int pick = LembarKunci.argInt(op, 2, -1);
		DiplomasiService d = SekteApi.diplomasi();
		if (!kursi(town, day).contains(sect) || d.stance(player.getUUID(), sect) == Sikap.PERANG) {
			return;
		}
		KotaBesarData data = KotaBesarData.get(server);
		CompoundTag me = data.pemain(player.getUUID()).getCompoundOrEmpty("pertemuan");
		if (me.getLongOr("hari", Long.MIN_VALUE) != day) {
			me = new CompoundTag();
			me.putLong("hari", day);
		}
		if (me.getBooleanOr("selesai", false)) {
			return;
		}
		Component name = d.name(sect);
		Component nama = name == null ? Component.literal(sect) : name;
		Component pesan;
		boolean ok = true;
		switch (pick) {
			case 0 -> {
				d.adjust(player.getUUID(), sect, BONUS, "kotabesar.pertemuan");
				me.putBoolean("selesai", true);
				pesan = Component.translatable("kultivator.kota_besar.pertemuan.pakta_ok", nama, BONUS);
			}
			case 1 -> {
				long gift = 20L * (Barang.pita(player) + 1);
				if (!EkonomiApi.bayar(player, gift, "kotabesar.pertemuan")) {
					pesan = Component.translatable("kultivator.kota_besar.hukum.kurang", Barang.nb(gift));
					ok = false;
				} else {
					d.adjust(player.getUUID(), sect, BONUS, "kotabesar.pertemuan");
					dev.kultivator.dunia.kota.KotaData.get(server).addReputation(town, player.getUUID(), 1);
					me.putBoolean("selesai", true);
					pesan = Component.translatable("kultivator.kota_besar.pertemuan.hadiah_ok", nama, BONUS);
				}
			}
			default -> {
				me.putInt("lewat", me.getIntOr("lewat", 0) + 1);
				pesan = Component.translatable("kultivator.kota_besar.pertemuan.lewat_ok", nama);
			}
		}
		data.pemain(player.getUUID()).put("pertemuan", me);
		data.setDirty();
		if (ok && pick <= 1) {
			Msg.chat(player, pesan.copy().withStyle(ChatFormatting.GOLD));
			List<UUID> bodies = utusan.get(town);
			if (bodies != null) {
				for (UUID id : bodies) {
					if (player.level().getEntity(id) instanceof KultivatorNpc envoy && sect.equals(envoy.memory().getStringOr("kb_sekte", ""))) {
						HidupApi.klip(envoy, GerakId.HORMAT, 0, 40);
						HidupApi.tatap(envoy, player);
					}
				}
			}
		}
		KotaBesarKalender.segarkan(player, pesan, ok);
	}
}
