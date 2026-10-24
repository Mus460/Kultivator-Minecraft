package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Catatan Perjalanan (DESIGN 38.S.1, screen {@code jurnal} of 38.U.3.9, sheet keys {@link LembarKunci.Jurnal}): running
 * quests with their stage line and needle target, finished quests with the branch taken, and the capital chapters seen so
 * far. A hub screen (read only, {@link TempatLayar#daftarHub}); the player command {@code /kultivasi jurnal} opens it.
 */
public final class JurnalCerita {

	static void init() {
		TempatLayar.daftarHub(LembarKunci.Jurnal.LAYAR, JurnalCerita::aksi, (player, npcId) -> lembar(player));
		DuniaCommands.player("jurnal", () -> Commands.literal("jurnal").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			if (!CeritaConfig.jalan()) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.cerita.mati"));
				return 0;
			}
			TempatLayar.buka(player, LembarKunci.Jurnal.LAYAR, -1, lembar(player));
			return 1;
		}));
	}

	static CompoundTag lembar(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CeritaData data = CeritaData.get(server);
		CompoundTag tag = new CompoundTag();
		tag.putString(LembarKunci.GAYA, LembarKunci.GAYA_KERTAS);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable("kultivator.cerita.jurnal.judul"));
		ListTag active = new ListTag();
		ListTag done = new ListTag();
		List<String> ids = new ArrayList<>(data.questIds(player.getUUID()));
		ids.sort((x, y) -> Long.compare(data.quest(player.getUUID(), y).getLongOr("hari", 0L), data.quest(player.getUUID(), x).getLongOr("hari", 0L)));
		for (String id : ids) {
			Quest quest = CeritaReload.quest(id);
			if (quest == null) {
				continue;
			}
			CompoundTag record = data.quest(player.getUUID(), id);
			int stage = record.getIntOr("tahap", 0);
			if (stage > 0 && stage < TempatEvents.QUEST_SELESAI && active.size() < LembarKunci.DAFTAR_MAKS) {
				CompoundTag q = new CompoundTag();
				q.putString(LembarKunci.ID, id);
				LembarKunci.teks(q, LembarKunci.NAMA, Component.translatable(quest.kunciNama()));
				LembarKunci.teks(q, LembarKunci.TEMPAT, Component.translatable(quest.kunciTempat()));
				LembarKunci.teks(q, LembarKunci.TEKS, Component.translatable(quest.kunciTahap(record.getStringOr("simpul", ""))));
				q.putInt(LembarKunci.TAHAP, stage);
				q.putInt(LembarKunci.TAHAP_TOTAL, quest.tahap().size());
				DuniaSites.Site target = QuestTracker.arah(player, id);
				if (target != null && target.dim() == player.level().dimension()) {
					q.putInt(LembarKunci.X, target.pos().getX());
					q.putInt(LembarKunci.Z, target.pos().getZ());
					q.putBoolean(LembarKunci.Jurnal.ADA_ARAH, true);
				}
				Quest.Tahap t = quest.tahap(record.getStringOr("simpul", ""));
				if (t != null && t.batasHari() > 0) {
					long left = record.getLongOr("hari", 0L) + t.batasHari() - Kota.day(server);
					q.putLong(LembarKunci.SISA_TICK, Math.max(0L, left) * 24000L);
				}
				active.add(q);
			} else if ((stage == TempatEvents.QUEST_SELESAI || stage == TempatEvents.QUEST_GAGAL) && done.size() < LembarKunci.DAFTAR_MAKS) {
				CompoundTag q = new CompoundTag();
				q.putString(LembarKunci.ID, id);
				LembarKunci.teks(q, LembarKunci.NAMA, Component.translatable(quest.kunciNama()));
				LembarKunci.teks(q, LembarKunci.TEMPAT, Component.translatable(quest.kunciTempat()));
				q.putInt(LembarKunci.HARI, (int) record.getLongOr("selesai", 0L));
				LembarKunci.teks(q, LembarKunci.Jurnal.HASIL, Component.translatable(quest.kunciHasil(record.getStringOr("hasil", ""))));
				done.add(q);
			}
		}
		tag.put(LembarKunci.AKTIF, active);
		tag.put(LembarKunci.Jurnal.SELESAI, done);
		ListTag arc = new ListTag();
		long site = AlurTakhta.terdekat(player);
		int bab = AlurTakhta.bab(server, site);
		for (int b = 1; b <= AlurTakhta.BAB_TERAKHIR; b++) {
			CompoundTag c = new CompoundTag();
			c.putInt(LembarKunci.Jurnal.BAB, b);
			LembarKunci.teks(c, LembarKunci.JUDUL, Component.translatable("kultivator.cerita.takhta.bab." + b));
			boolean open = site != 0L && b <= Math.min(bab, AlurTakhta.BAB_TERAKHIR);
			c.putBoolean(LembarKunci.Jurnal.TERBUKA, open);
			if (open) {
				String result = data.arc(site).getCompoundOrEmpty("hasil").getStringOr(Integer.toString(b), "");
				LembarKunci.teks(c, LembarKunci.Jurnal.RINGKASAN, result.isEmpty()
						? Component.translatable("kultivator.cerita.takhta.bab." + b + ".pembuka")
						: Component.translatable("kultivator.cerita.takhta.hasil." + b + "." + result));
			}
			arc.add(c);
		}
		tag.put(LembarKunci.Jurnal.ALUR, arc);
		String tracked = data.tracked(player.getUUID());
		if (!tracked.isEmpty()) {
			tag.putString(LembarKunci.Jurnal.LACAK, tracked);
		}
		if (active.isEmpty() && done.isEmpty()) {
			CompoundTag empty = new CompoundTag();
			LembarKunci.teks(empty, LembarKunci.TEKS, Component.translatable("kultivator.cerita.jurnal.kosong"));
			tag.put(LembarKunci.KOSONG, empty);
		}
		return tag;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		if (LembarKunci.OP_LACAK.equals(parts[0])) {
			String quest = LembarKunci.arg(parts, 1);
			if (QuestTracker.aktif(player, quest)) {
				CeritaData.get(player.level().getServer()).setTracked(player.getUUID(), quest);
				petunjuk(player, quest);
			}
		}
	}

	/** One action-bar hint toward the tracked quest's target ("Gilda Alkemis · 240 blok ke barat laut"). */
	static void petunjuk(ServerPlayer player, String quest) {
		DuniaSites.Site target = QuestTracker.arah(player, quest);
		if (target == null || target.dim() != player.level().dimension()) {
			Msg.actionBar(player, Component.translatable("kultivator.cerita.jurnal.dilacak", nama(quest)).withStyle(ChatFormatting.GOLD));
			return;
		}
		double dx = target.pos().getX() + 0.5D - player.getX();
		double dz = target.pos().getZ() + 0.5D - player.getZ();
		int distance = (int) Math.sqrt(dx * dx + dz * dz);
		Msg.actionBar(player, Component.translatable("kultivator.cerita.jurnal.arah", nama(quest), distance,
				Component.translatable("kultivator.cerita.arah." + arah(dx, dz))).withStyle(ChatFormatting.GOLD));
	}

	private static Component nama(String quest) {
		Quest q = CeritaReload.quest(quest);
		return q == null ? Component.literal(quest) : Component.translatable(q.kunciNama());
	}

	/** One of eight compass words ({@code utara}, {@code timur_laut}, ...) for a horizontal offset (north = -z). */
	static String arah(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = (int) Math.floorMod(Math.round(angle / 45.0D), 8L);
		return switch (sector) {
			case 0 -> "utara";
			case 1 -> "timur_laut";
			case 2 -> "timur";
			case 3 -> "tenggara";
			case 4 -> "selatan";
			case 5 -> "barat_daya";
			case 6 -> "barat";
			default -> "barat_laut";
		};
	}

	static @Nullable String tracked(ServerPlayer player) {
		String t = CeritaData.get(player.level().getServer()).tracked(player.getUUID());
		return t.isEmpty() ? null : t;
	}

	private JurnalCerita() {
	}
}
