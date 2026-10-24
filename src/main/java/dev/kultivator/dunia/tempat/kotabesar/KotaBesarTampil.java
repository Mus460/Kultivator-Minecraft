package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.PapanHidup;
import dev.kultivator.dunia.tempat.ui.PapanHidupEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared pieces of the capital's sheets and pictures: the sheet header every 38KS screen starts with (title, style,
 * district colour, the player's money, stones, band, Nama Baik and rank), the speaker card, and the client pictures that
 * concern a whole capital (the wanted and hero boards, 38UI {@code papan_hidup}). Players walking up to a capital get
 * the pictures of its running events once ({@link #masuk}).
 */
public final class KotaBesarTampil {

	/** The capital each player was last near (to send the pictures once on arrival). */
	private static final Map<UUID, Long> DEKAT = new HashMap<>();

	static void lupakan() {
		DEKAT.clear();
	}

	static void keluar(UUID id) {
		DEKAT.remove(id);
	}

	/** A new sheet with the 38KS header for {@code player} at capital {@code town}. */
	static CompoundTag kepala(ServerPlayer player, long town, String gaya, @Nullable String distrik) {
		CompoundTag tag = new CompoundTag();
		tag.putString(LembarKunci.GAYA, gaya);
		if (distrik != null) {
			tag.putString(LembarKunci.DISTRIK, distrik);
		}
		tag.putLong(LembarKunci.SITUS, town);
		MinecraftServer server = player.level().getServer();
		Kandidat k = KotaBesarKota.dariSitus(server.overworld(), town);
		if (k != null) {
			LembarKunci.teks(tag, LembarKunci.TEMPAT, KotaBesarKota.nama(server, k));
		}
		tag.putLong(LembarKunci.UANG, NilaiBatu.total(player));
		tag.putIntArray(LembarKunci.BATU, Barang.batu(player));
		tag.putInt(LembarKunci.PITA, Barang.pita(player));
		tag.putInt(LembarKunci.NAMA_BAIK, KotaData.get(server).reputation(town, player.getUUID()));
		tag.putInt(LembarKunci.PANGKAT_NB, KotaBesarApi.pangkatNamaBaik(player, town));
		return tag;
	}

	/** The speaker card of {@code npc} (live portrait, role title). */
	static CompoundTag kartu(KultivatorNpc npc) {
		CompoundTag card = new CompoundTag();
		LembarKunci.teks(card, LembarKunci.NAMA, npc.getDisplayName());
		LembarKunci.teks(card, LembarKunci.GELAR, Component.translatable(npc.role().titleKey()));
		card.putString(LembarKunci.PERAN, npc.role().name());
		card.putInt(LembarKunci.POTRET, npc.getId());
		return card;
	}

	/** Puts the result line of an action on a sheet. */
	static void pesan(CompoundTag tag, Component teks, String jenis) {
		LembarKunci.teks(tag, LembarKunci.PESAN, teks);
		tag.putString(LembarKunci.PESAN_JENIS, jenis);
	}

	/** Locks a whole sheet with a reason. */
	static void kunci(CompoundTag tag, Component alasan, @Nullable Component syarat) {
		CompoundTag kunci = new CompoundTag();
		LembarKunci.teks(kunci, LembarKunci.ALASAN, alasan);
		if (syarat != null) {
			LembarKunci.teks(kunci, LembarKunci.SYARAT, syarat);
		}
		tag.put(LembarKunci.KUNCI_LAYAR, kunci);
	}

	// ================================================================== wanted posters

	/** The wanted-poster picture of capital {@code town}: every player with 25+ points, the boards to pin them on. */
	static CompoundTag posterData(MinecraftServer server, long town) {
		CompoundTag tag = new CompoundTag();
		Kandidat k = KotaBesarKota.dariSitus(server.overworld(), town);
		if (k == null) {
			return tag;
		}
		tag.putDouble("x", k.pusatX());
		tag.putDouble("z", k.pusatZ());
		tag.putLong("town", town);
		KotaBesarData data = KotaBesarData.get(server);
		ListTag daftar = new ListTag();
		CompoundTag nama = data.bagian(town, "nama");
		for (String id : data.pelaku(town)) {
			try {
				int poin = data.kejahatan(town, UUID.fromString(id));
				int tingkat = Hukum.tingkat(poin);
				if (tingkat < 2) {
					continue;
				}
				CompoundTag row = new CompoundTag();
				ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(id));
				row.putString("nama", online != null ? online.getGameProfile().name() : nama.getStringOr(id, "?"));
				row.putString("uuid", id);
				row.putInt("tingkat", tingkat);
				row.putLong("denda", (long) poin * 3L);
				daftar.add(row);
			} catch (IllegalArgumentException ignored) {
				// skip
			}
			if (daftar.size() >= 8) {
				break;
			}
		}
		tag.put("daftar", daftar);
		ListTag pahlawan = new ListTag();
		CompoundTag rep = KotaData.get(server).town(town).getCompoundOrEmpty("rep");
		List<String> ids = new ArrayList<>(rep.keySet());
		ids.sort((a, b) -> Integer.compare(rep.getIntOr(b, 0), rep.getIntOr(a, 0)));
		for (String id : ids) {
			int nb = rep.getIntOr(id, 0);
			if (nb < KotaBesarApi.NB_PAHLAWAN || pahlawan.size() >= 6) {
				break;
			}
			CompoundTag row = new CompoundTag();
			ServerPlayer online = null;
			try {
				online = server.getPlayerList().getPlayer(UUID.fromString(id));
			} catch (IllegalArgumentException ignored) {
				continue;
			}
			row.putString("nama", online != null ? online.getGameProfile().name() : nama.getStringOr(id, "?"));
			row.putInt("nb", nb);
			pahlawan.add(row);
		}
		tag.put("pahlawan", pahlawan);
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		List<Integer> papan = new ArrayList<>();
		if (kota != null) {
			for (DuniaAnchorEntity a : kota.layanan) {
				String l = a.data().getStringOr("layanan", "");
				if (l.equals("gilda_misi") || l.equals("yamen") || l.equals("denda")) {
					BlockPos p = a.blockPosition();
					papan.add(p.getX());
					papan.add(p.getY());
					papan.add(p.getZ());
				}
			}
		}
		tag.putIntArray("papan", papan.stream().mapToInt(Integer::intValue).toArray());
		return tag;
	}

	/**
	 * Refreshes the capital's two living boards (38UI {@code papan_hidup}, model 38MD): the wanted board on the north wall
	 * of the Yamen hall beside the Guard Captain's desk (the most wanted face and bounty, up to six sheets) and the Hero
	 * board at the north end of the Great Square's causeway (Heroes of the City by Nama Baik). Only boards of loaded
	 * anchors; nothing changes a block.
	 */
	static void poster(MinecraftServer server, long town) {
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		ServerLevel level = kota == null ? null : server.getLevel(kota.dimensi);
		if (level == null) {
			return;
		}
		CompoundTag data = posterData(server, town);
		DuniaAnchorEntity desk = kota.layanan("denda");
		if (desk != null) {
			int putar = KotaBesarAnchors.putarDi(level, desk);
			// against the hall's north wall beside the desk, facing into the hall (not in the walkway to the desk)
			BlockPos stand = KotaBesarAnchors.dari(desk, putar, 0, 1, -9);
			PapanHidupEntity board = PapanHidup.pasang(level, stand.below(), 90.0F * putar, PapanHidupEntity.JENIS_BURONAN);
			if (board != null) {
				List<Component> titles = new ArrayList<>();
				List<Component> lines = new ArrayList<>();
				ListTag wanted = data.getListOrEmpty("daftar");
				for (int i = 0; i < wanted.size() && titles.size() < PapanHidupEntity.MAKS_LEMBAR; i++) {
					CompoundTag w = wanted.getCompoundOrEmpty(i);
					titles.add(Component.translatable("kultivator.kota_besar.papan.buronan." + Math.max(2, w.getIntOr("tingkat", 2)), w.getStringOr("nama", "?")));
					lines.add(Component.translatable("kultivator.kota_besar.papan.hadiah", w.getLongOr("denda", 0L)));
				}
				if (titles.isEmpty()) {
					titles.add(Component.translatable("kultivator.kota_besar.papan.buronan.kosong"));
					lines.add(Component.translatable("kultivator.kota_besar.papan.buronan.kosong.baris"));
					board.setBuronan("", 0L);
				} else {
					CompoundTag first = wanted.getCompoundOrEmpty(0);
					board.setBuronan(first.getStringOr("nama", ""), first.getLongOr("denda", 0L));
				}
				board.setLembar(titles, lines);
			}
		}
		DuniaAnchorEntity stage = kota.panggung("festival");
		if (stage != null) {
			int putar = KotaBesarAnchors.putarDi(level, stage);
			// at the square's north end of the causeway, facing the stage (not on the causeway itself)
			BlockPos stand = KotaBesarAnchors.dari(stage, putar, 0, 1, -9);
			PapanHidupEntity board = PapanHidup.pasang(level, stand.below(), 90.0F * putar, PapanHidupEntity.JENIS_KABAR);
			if (board != null) {
				List<Component> titles = new ArrayList<>();
				List<Component> lines = new ArrayList<>();
				ListTag heroes = data.getListOrEmpty("pahlawan");
				titles.add(Component.translatable("kultivator.kota_besar.papan.pahlawan"));
				lines.add(Component.translatable("kultivator.kota_besar.papan.pahlawan.baris", heroes.size()));
				for (int i = 0; i < heroes.size() && titles.size() < PapanHidupEntity.MAKS_LEMBAR; i++) {
					CompoundTag h = heroes.getCompoundOrEmpty(i);
					titles.add(Component.literal(h.getStringOr("nama", "?")));
					lines.add(Component.translatable("kultivator.kota_besar.papan.nb", h.getIntOr("nb", 0)));
				}
				board.setLembar(titles, lines);
			}
		}
	}

	/** Remembers the display name of a player with a record (posters while he is offline). */
	static void ingatNama(ServerPlayer player, long town) {
		KotaBesarData data = KotaBesarData.get(player.level().getServer());
		CompoundTag nama = data.bagian(town, "nama");
		nama.putString(player.getUUID().toString(), player.getGameProfile().name());
		data.putBagian(town, "nama", nama);
	}

	/** Every 20 ticks per overworld player: entering the 320-block sphere of a capital sends its pictures once. */
	static void masuk(ServerLevel level, ServerPlayer player) {
		Kandidat k = KotaBesarKota.di(level, player.blockPosition());
		long town = k == null || KotaBesarKota.jarak(k, player.blockPosition()) > KotaBesarPayload.JANGKAU - 16 ? 0L : KotaBesarKota.situs(k);
		Long before = DEKAT.put(player.getUUID(), town);
		if (town == 0L || before != null && before == town) {
			return;
		}
		ingatNama(player, town);
		KotaBesarKalender.kirimKeadaan(level, player, k, town);
	}

	private KotaBesarTampil() {
	}
}
