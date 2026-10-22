package dev.kultivator.sekte.tetua;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Balai Tetua, the hall of elders (DESIGN 36.2.6 "UI"): what the screen shows and what its buttons do. The view is the
 * owner's whole elder state in one compound (the six offices with their holder, bonus and affairs; the disciples who
 * may serve; the last morning report; the heir, the ceremony checklist, the generation and the lineage) and is rebuilt
 * after every action. Only the hall's own master acts; every choice is checked again here.
 */
final class BalaiTetua {

	static void open(ServerPlayer player) {
		if (Halls.hallOf(player.getUUID()) == null) {
			Msg.actionBar(player, Component.translatable("kultivator.sekte.tetua.why.no_hall").withStyle(ChatFormatting.YELLOW));
			return;
		}
		TetuaPayloads.balai(player, true, view(player));
	}

	static void refresh(ServerPlayer player) {
		if (Halls.hallOf(player.getUUID()) != null) {
			TetuaPayloads.balai(player, false, view(player));
		}
	}

	/** A choice on the screen ({@code TetuaPayloads.Act}). */
	static void onAct(ServerPlayer player, String op, String arg) {
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		if (server == null || op == null) {
			return;
		}
		switch (op) {
			case TetuaPayloads.OP_OPEN -> open(player);
			case TetuaPayloads.OP_APPOINT -> {
				String[] parts = arg == null ? new String[0] : arg.split(":");
				Jabatan office = parts.length > 0 ? Jabatan.byId(parts[0]) : null;
				UUID soul = parts.length > 1 ? TetuaData.uuid(parts[1]) : null;
				if (office == null || soul == null) {
					return;
				}
				DiscipleEntity entity = JabatanTetua.loaded(owner, soul);
				Component why = entity == null ? Component.translatable("kultivator.sekte.tetua.why.not_loaded")
						: JabatanTetua.appoint(player, entity, office, false);
				if (why != null) {
					Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
				}
				refresh(player);
			}
			case TetuaPayloads.OP_DISMISS -> {
				Jabatan office = Jabatan.byId(arg);
				if (office != null) {
					JabatanTetua.dismiss(server, owner, office, player);
				}
				refresh(player);
			}
			case TetuaPayloads.OP_REPORT -> {
				CompoundTag last = TetuaData.hall(owner).getCompoundOrEmpty(TetuaData.REPORT);
				CompoundTag report = last.isEmpty() ? LaporanPagi.build(server, owner) : last.copy();
				report.putBoolean("brought", false);
				TetuaPayloads.report(player, report);
			}
			case TetuaPayloads.OP_THRONE -> {
				Component why = PenyerahanTakhta.start(server, player, false, null);
				if (why != null) {
					Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
				}
				refresh(player);
			}
			default -> {
			}
		}
	}

	/** The whole view of the owner's elders. */
	static CompoundTag view(ServerPlayer player) {
		UUID owner = player.getUUID();
		MinecraftServer server = player.level().getServer();
		CompoundTag mine = TetuaData.hall(owner);
		CompoundTag view = new CompoundTag();
		view.store("hall", ComponentSerialization.CODEC, Halls.hallName(Halls.section(owner, HallData.CORE)));
		view.putInt("level", Halls.level(owner));
		int hour = TetuaData.hour(server);
		view.putInt("hour", hour);
		view.putInt("from", Tetua.config().jamMulai);
		view.putInt("to", Tetua.config().jamSelesai);
		view.putBoolean("open", TetuaData.within(hour, Tetua.config().jamMulai, Tetua.config().jamSelesai));
		view.putLong("day", Ingatan.today(server));

		ListTag offices = new ListTag();
		for (Jabatan office : Jabatan.values()) {
			CompoundTag entry = TetuaData.office(owner, office);
			CompoundTag line = new CompoundTag();
			line.putString("id", office.id());
			String soul = entry.getStringOr("soul", "");
			line.putString("soul", soul);
			if (!soul.isEmpty()) {
				line.store("name", ComponentSerialization.CODEC, TetuaData.name(entry, "name"));
				line.putInt("realm", entry.getIntOr("realm", 0));
				line.putBoolean("wakil", entry.getBooleanOr("wakil", false));
				line.putInt("urusan", entry.getIntOr("urusan", 0));
				line.putLong("sejak", entry.getLongOr("sejak", 0L));
				UUID id = TetuaData.uuid(soul);
				DiscipleEntity holder = id == null ? null : JabatanTetua.loaded(owner, id);
				line.putBoolean("loaded", holder != null);
				line.putBoolean("desk", holder != null && holder.activity() == dev.kultivator.sekte.SekteIds.ACT_OFFICE);
			}
			line.putDouble("factor", JabatanTetua.factor(owner, office));
			offices.add(line);
		}
		view.put("offices", offices);

		ListTag candidates = new ListTag();
		UUID ketua = Generasi.master(owner);
		for (DiscipleEntity d : Disciples.loaded(owner)) {
			if (d.role() != DiscipleEntity.Role.DISCIPLE || d.isReleasing()) {
				continue;
			}
			DiscipleData data = d.data();
			int rank = SectRanks.rank(data);
			if (rank < SectRanks.INTI || d.soulId().equals(ketua)) {
				continue;
			}
			CompoundTag c = new CompoundTag();
			c.putString("soul", d.soulId().toString());
			c.store("name", ComponentSerialization.CODEC, d.getName());
			c.putInt("rank", rank);
			c.putInt("realm", data.realm().ordinal());
			Jabatan held = TetuaData.officeOf(owner, d.soulId());
			c.putString("office", held == null ? "" : held.id());
			c.putBoolean("heir", DisciplePaths.isHeir(data));
			candidates.add(c);
			if (candidates.size() >= 24) {
				break;
			}
		}
		view.put("candidates", candidates);

		CompoundTag report = mine.getCompoundOrEmpty(TetuaData.REPORT);
		if (!report.isEmpty()) {
			view.put("report", report.copy());
		}

		CompoundTag heir = new CompoundTag();
		UUID heirSoul = Generasi.heir(owner);
		if (heirSoul != null) {
			heir.putString("soul", heirSoul.toString());
			DiscipleEntity h = JabatanTetua.loaded(owner, heirSoul);
			heir.store("name", ComponentSerialization.CODEC, h != null ? h.getName() : TetuaData.name(mine, TetuaData.HEIR_NAME));
			heir.putBoolean("loaded", h != null);
			if (h != null) {
				heir.putInt("realm", h.data().realm().ordinal());
				heir.putInt("stars", DisciplePaths.stars(h.data()));
				heir.putInt("hexes", dev.kultivator.disciple.path.Heir.hexes(h.data()).size());
			}
		}
		view.put("heir", heir);
		ListTag reqs = new ListTag();
		boolean ready = true;
		for (Generasi.Requirement r : Generasi.requirements(server, owner)) {
			CompoundTag line = new CompoundTag();
			line.putBoolean("ok", r.ok());
			line.store("text", ComponentSerialization.CODEC, r.text());
			reqs.add(line);
			ready &= r.ok();
		}
		view.put("reqs", reqs);
		view.putBoolean("ready", ready);
		view.putInt("running", PenyerahanTakhta.secondsLeft(owner));
		view.putInt("gen", Generasi.generation(owner));
		if (ketua != null) {
			view.store("ketua", ComponentSerialization.CODEC, TetuaData.name(mine, TetuaData.MASTER_NAME));
		}
		view.putBoolean("founder", Generasi.founder(owner));
		view.store("owner", ComponentSerialization.CODEC, player.getName());
		ListTag lineage = new ListTag();
		for (Tag tag : mine.getListOrEmpty(TetuaData.LINEAGE)) {
			if (tag instanceof CompoundTag line) {
				lineage.add(line.copy());
			}
		}
		view.put("lineage", lineage);
		view.putInt("age", (int) Math.min(Integer.MAX_VALUE, Generasi.age(server, owner)));
		view.putBoolean("auto", Tetua.config().terimaOtomatis);
		return view;
	}

	private BalaiTetua() {
	}
}
