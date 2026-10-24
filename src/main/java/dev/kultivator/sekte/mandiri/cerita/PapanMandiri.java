package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.CitaCita;
import dev.kultivator.sekte.mandiri.Irama;
import dev.kultivator.sekte.mandiri.MandiriData;
import dev.kultivator.sekte.mandiri.Perencana;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The "Jalan Hidup Murid" board (DESIGN 39.7.4, hub card {@code sekte_mandiri_jalan}, payload {@code mandiri_papan}):
 * every disciple of the hall, loaded, travelling or unloaded, with its path, what it does or where it is ("Tutup Diri hari
 * 2/3", "on a shopping trip, home in 0.3 days"), realm, priority-1 goal, purse and flags (travelling, wounded, ready to
 * break through, tempted); the news feed of 30 events; the hall policy; the money book of seven days.
 * <p>
 * Bodies of unloaded chunks cannot be read, so the board keeps a small mirror per soul ({@link #ringkas}, hall record
 * {@code cerita.ringkas.<soul>}, about 150 bytes) refreshed at most once a minute by the observer for loaded bodies and
 * whenever a page is built; an unloaded row shows its last known state, marked as such.
 */
final class PapanMandiri {

	/** Row flags. */
	static final int F_PERGI = 1;
	static final int F_LUKA = 2;
	static final int F_SIAP = 4;
	static final int F_TERGODA = 8;
	static final int F_TUTUP = 16;
	static final int F_PERINTAH = 32;
	static final int F_DIMUAT = 64;
	static final int F_JAUH = 128;

	/** The mirror of {@code soul} (empty when never seen). */
	static CompoundTag ringkas(MinecraftServer server, UUID owner, UUID soul) {
		return Cerita.bagian(server, owner, "ringkas").getCompoundOrEmpty(soul.toString());
	}

	/** Refreshes the mirror of a loaded body. */
	static void perbaruiRingkas(MinecraftServer server, DiscipleEntity body) {
		UUID owner = body.ownerId();
		if (owner == null || body.role() != DiscipleEntity.Role.DISCIPLE) {
			return;
		}
		DiscipleData data = body.data();
		CompoundTag m = MandiriApi.bagian(data);
		CompoundTag t = new CompoundTag();
		t.store("n", ComponentSerialization.CODEC, body.getName());
		t.putString("j", m.getStringOr(MandiriIds.K_JALAN, ""));
		t.putInt("r", data.rank().step());
		t.putInt("sr", SectRanks.rank(data));
		Kegiatan k = MandiriApi.kegiatan(body);
		t.putString("k", k == null ? "" : k.id());
		ListTag sifat = new ListTag();
		for (Sifat s : SekteApi.hati().traits(data)) {
			sifat.add(StringTag.valueOf(s.id()));
		}
		t.put("s", sifat);
		t.putString("c", BukuHarian.citaUtama(data));
		for (CitaCita.Cita c : CitaCita.aktif(data)) {
			if (c.prio() == 1) {
				t.putInt("cp", c.prog());
			}
		}
		t.putLong("d", MandiriApi.dompet(data));
		t.putInt("i", MandiriApi.iblis(data));
		long now = body.level().getGameTime();
		t.putString("l", MandiriApi.luka(data, now).id());
		t.putLong("t", now);
		t.putInt("f", bendera(body, now));
		Cerita.bagian(server, owner, "ringkas").put(body.soulId().toString(), t);
		MandiriApi.tandai(server);
	}

	private static int bendera(DiscipleEntity body, long now) {
		DiscipleData data = body.data();
		CompoundTag m = MandiriApi.bagian(data);
		int f = 0;
		JenisLuka luka = MandiriApi.luka(data, now);
		if (luka != JenisLuka.TIDAK) {
			f |= F_LUKA;
		}
		if (m.getCompoundOrEmpty(MandiriIds.K_TUNGGU).contains("terobosan")) {
			f |= F_SIAP;
		}
		if (MandiriApi.iblis(data) >= 60) {
			f |= F_TERGODA;
		}
		if (MandiriApi.tutupDiri(data, now)) {
			f |= F_TUTUP;
		}
		if (MandiriApi.bebasSampai(data) > now) {
			f |= F_PERINTAH;
		}
		return f;
	}

	/** The board for {@code viewer} about the hall of {@code owner}. */
	static CompoundTag bangun(ServerPlayer viewer, UUID owner) {
		MinecraftServer server = viewer.level().getServer();
		CompoundTag out = new CompoundTag();
		out.putBoolean("on", MandiriApi.aktif());
		out.putBoolean("hall", Halls.hallOf(owner) != null);
		out.putBoolean("own", owner.equals(viewer.getUUID()));
		long now = server.overworld().getGameTime();
		out.putLong("hari", Irama.hari(server.overworld()));
		out.putInt("jam", Irama.jam(server.overworld()));
		ListTag rows = new ListTag();
		Set<UUID> shown = new HashSet<>();
		CompoundTag journeys = MandiriApi.aula(server, owner, MandiriIds.A_JALAN);
		for (DiscipleEntity body : Disciples.loaded(owner)) {
			if (body.role() != DiscipleEntity.Role.DISCIPLE || body.isReleasing() || !shown.add(body.soulId())) {
				continue;
			}
			perbaruiRingkas(server, body);
			CompoundTag row = ringkas(server, owner, body.soulId()).copy();
			row.putString("soul", body.soulId().toString());
			int f = row.getIntOr("f", 0) | F_DIMUAT;
			if (body.level() != viewer.level() || body.distanceToSqr(viewer) > Disciples.VIEW_RANGE * Disciples.VIEW_RANGE) {
				f |= F_JAUH;
			} else {
				row.putInt("eid", body.getId());
			}
			row.putInt("f", f);
			tutupKata(row, body, now);
			rows.add(row);
		}
		for (Halls.AwayView away : Halls.away(owner)) {
			if (!shown.add(away.soul())) {
				continue;
			}
			CompoundTag row = ringkas(server, owner, away.soul()).copy();
			row.putString("soul", away.soul().toString());
			row.store("n", ComponentSerialization.CODEC, away.name());
			row.putInt("f", row.getIntOr("f", 0) & (F_LUKA | F_TERGODA) | F_PERGI);
			row.putString("ar", away.reason());
			row.putLong("ret", Math.max(0L, away.returnAt() - now));
			CompoundTag journey = journeys.getCompoundOrEmpty(away.soul().toString());
			if (!journey.isEmpty()) {
				row.putString("jj", journey.getStringOr("jenis", ""));
			}
			rows.add(row);
		}
		if (owner.equals(viewer.getUUID())) {
			for (Tag tag : Cultivators.get(viewer).extra().getCompoundOrEmpty(Disciples.EXTRA).getListOrEmpty("list")) {
				if (!(tag instanceof CompoundTag listed) || listed.getBooleanOr("dead", false)) {
					continue;
				}
				UUID soul;
				try {
					soul = UUID.fromString(listed.getStringOr("id", ""));
				} catch (IllegalArgumentException exception) {
					continue;
				}
				if (!shown.add(soul)) {
					continue;
				}
				CompoundTag row = ringkas(server, owner, soul).copy();
				row.putString("soul", soul.toString());
				listed.read("name", ComponentSerialization.CODEC).ifPresent(name -> row.store("n", ComponentSerialization.CODEC, name));
				if (!row.contains("r")) {
					row.putInt("r", listed.getIntOr("step", 0));
				}
				boolean away = listed.getBooleanOr("away", false);
				row.putInt("f", row.getIntOr("f", 0) & (F_LUKA | F_TERGODA | F_SIAP) | (away ? F_PERGI : 0));
				if (away) {
					row.putString("ar", listed.getStringOr("away_reason", "other"));
				}
				rows.add(row);
			}
		}
		out.put("rows", rows);

		ListTag news = new ListTag();
		ListTag feed = MandiriData.get(server).kabar(owner);
		for (int i = feed.size() - 1; i >= 0; i--) {
			CompoundTag line = feed.getCompoundOrEmpty(i);
			CompoundTag n = new CompoundTag();
			n.putLong("h", line.getLongOr("hari", 0L));
			n.putInt("j", line.getIntOr("jam", 0));
			n.putBoolean("p", line.getBooleanOr("penting", false));
			n.store("t", ComponentSerialization.CODEC, MandiriApi.teksKabar(line));
			news.add(n);
		}
		out.put("kabar", news);
		out.put("kb", MandiriApi.kebijakanAula(owner).toTag());

		ListTag book = new ListTag();
		ListTag kas = MandiriData.get(server).aula(owner).getListOrEmpty(MandiriIds.A_KAS);
		for (int i = kas.size() - 1; i >= 0 && book.size() < 40; i--) {
			CompoundTag line = kas.getCompoundOrEmpty(i);
			CompoundTag b = new CompoundTag();
			b.putLong("h", line.getLongOr("hari", 0L));
			b.putLong("j", line.getLongOr("jumlah", 0L));
			b.putString("s", line.getStringOr("sebab", ""));
			String soul = line.getStringOr("soul", "");
			b.store("n", ComponentSerialization.CODEC, BukuHarian.namaJiwa(server, owner, soul));
			book.add(b);
		}
		out.put("kas", book);
		out.putLong("saku7", MandiriData.get(server).sakuMinggu(owner));
		out.putInt("pool", Halls.pool(owner));
		return out;
	}

	/** "Tutup Diri hari 2/3" for a secluded body. */
	private static void tutupKata(CompoundTag row, DiscipleEntity body, long now) {
		CompoundTag tutup = MandiriApi.bagian(body.data()).getCompoundOrEmpty(MandiriIds.K_TUTUP);
		long mulai = tutup.getLongOr("mulai", 0L);
		long sampai = tutup.getLongOr("sampai", 0L);
		if (sampai > now && sampai > mulai) {
			row.putInt("th", (int) ((now - mulai) / 24000L) + 1);
			row.putInt("tt", (int) Math.max(1L, (sampai - mulai + 23999L) / 24000L));
		}
	}

	/** Sends the board. */
	static void kirim(ServerPlayer viewer, UUID owner) {
		MandiriPayloads.kirim(viewer, new MandiriPayloads.Papan(bangun(viewer, owner)));
	}

	private PapanMandiri() {
	}
}
