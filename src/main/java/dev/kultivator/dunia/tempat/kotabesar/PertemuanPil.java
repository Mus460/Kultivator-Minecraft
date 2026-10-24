package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.alchemy.AlchemyFurnace;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Pill Gathering of the Alchemist Guild (DESIGN 38.B.6.2): {@code d % 7 == 2} (or the next free day), 6000-9000, on the
 * round stage of eight cauldron stations of {@code gilda/gilda_alkemis}.
 * <ul>
 * <li><b>Entry</b> at the guild's examiner ({@code acara} screen, op {@code daftar}): {@code 20 x (band + 1)} NB. From
 * then the contestant has five real minutes to finish one pill in any furnace of the guild district; the best finished
 * pill counts ({@link AlchemyFurnace#addFinishListener}).</li>
 * <li><b>Score</b> = {@code quality x 100 + max(0, 300 - seconds) / 3 + 50} when the pill is worth at least the
 * contestant's band ({@code 20 x (band + 1)} NB at the guild's buying price), else without the 50.</li>
 * <li><b>Rivals</b>: five guild alchemists ({@link NpcRole#ALKEMIS} bodies at the other stations, fanning their
 * furnaces) with scores {@code N(150 + 40 x band, 40)}, revealed one by one as they "finish"; the live score board is the
 * {@code acara} screen (refreshed while open).</li>
 * <li><b>Prizes</b> (at 9000): first 4 x fee and one rare spirit herb (at most one a week), second 2 x fee, third the fee
 * back; Nama Baik +3/+2/+1; the title "Juara Pertemuan Pil" and {@code tempat/alkemis_juara} for the first; guild
 * points +2/+1 kept for 38EK's alchemist tiers ({@code poin_alkemis}).</li>
 * </ul>
 */
final class PertemuanPil implements KotaBesarKalender.Acara {

	static final String ID = LembarKunci.Acara.ACARA_PERTEMUAN_PIL;
	static final long WAKTU = 6000L;
	static final int SAINGAN = 5;

	private final Map<Long, List<UUID>> tubuh = new HashMap<>();
	private static PertemuanPil instance;

	PertemuanPil() {
		instance = this;
		AlchemyFurnace.addFinishListener(PertemuanPil::selesaiRacik);
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public int prioritas() {
		return 3;
	}

	@Override
	public boolean hariAsli(MinecraftServer server, long town, long day) {
		return Math.floorMod(day, 7L) == 2L;
	}

	@Override
	public long mulaiJam() {
		return 6000L;
	}

	@Override
	public long akhirJam() {
		return 9000L;
	}

	private static long biaya(ServerPlayer p) {
		return 20L * (Barang.pita(p) + 1);
	}

	/** The contest record of capital {@code town}: {@code daftar.<uuid>} {mulai L, skor I, biaya L, nama S}, {@code saingan} list. */
	private static CompoundTag lomba(MinecraftServer server, long town) {
		return KotaBesarData.get(server).bagian(town, "pil");
	}

	private static void simpan(MinecraftServer server, long town, CompoundTag tag) {
		KotaBesarData.get(server).putBagian(town, "pil", tag);
	}

	@Override
	public void mulai(ServerLevel level, Kandidat k, long town, boolean hidup) {
		MinecraftServer server = level.getServer();
		CompoundTag tag = new CompoundTag();
		tag.putLong("hari", Kota.day(server));
		RandomSource r = RandomSource.create(town ^ Kota.day(server) * 0x5DEECE66DL);
		int band = 2;
		for (ServerPlayer p : level.players()) {
			if (KotaBesarKota.jarak(k, p.blockPosition()) <= KotaBesarKota.HIDUP) {
				band = Math.max(band, Barang.pita(p));
			}
		}
		ListTag rivals = new ListTag();
		for (int i = 0; i < SAINGAN; i++) {
			CompoundTag rv = new CompoundTag();
			int look = r.nextInt();
			rv.putInt("look", look);
			rv.putInt("skor", Mth.clamp((int) Math.round(150 + 40 * band + r.nextGaussian() * 40.0D), 40, 520));
			rv.putLong("selesai", level.getGameTime() + 600L + r.nextInt(2400));
			rivals.add(rv);
		}
		tag.put("saingan", rivals);
		simpan(server, town, tag);
		if (!hidup) {
			return;
		}
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		DuniaAnchorEntity stage = kota == null ? null : kota.panggung("pil");
		List<UUID> bodies = new ArrayList<>();
		if (stage != null) {
			List<BlockPos> stations = KotaBesarAnchors.titikPanggung(level, stage);
			for (int i = 0; i < SAINGAN && i < stations.size(); i++) {
				BlockPos at = stations.get(i * 2 % stations.size());
				float yaw = (float) Math.toDegrees(Math.atan2(at.getX() - stage.getX(), stage.getZ() - at.getZ()));
				int look = rivals.getCompoundOrEmpty(i).getIntOr("look", 0);
				KultivatorNpc npc = HidupApi.tubuh(level, town, NpcRole.ALKEMIS, at, yaw + 180.0F, look);
				if (npc == null) {
					continue;
				}
				npc.setPlate(KotaNames.person(look, NpcRole.ALKEMIS));
				npc.memory().putString("kb_acara", ID);
				npc.memory().putBoolean("hold", true);
				npc.setLonely(96.0D, 2400);
				HidupApi.klip(npc, GerakId.KIPAS_TUNGKU, 0, 0);
				bodies.add(npc.getUUID());
			}
			KultivatorNpc speaker = bodies.isEmpty() ? null : level.getEntity(bodies.get(0)) instanceof KultivatorNpc n ? n : null;
			if (speaker != null) {
				Voice.say(speaker, "kotabesar.alchemy_start", null);
			}
		}
		tubuh.put(town, bodies);
		for (ServerPlayer p : level.players()) {
			if (KotaBesarKota.jarak(k, p.blockPosition()) <= KotaBesarKota.HIDUP) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_besar.pil.mulai").withStyle(ChatFormatting.AQUA));
			}
		}
	}

	@Override
	public void tick(ServerLevel level, Kandidat k, long town, long now) {
		if (now % 100L >= 20L) {
			return;
		}
		List<UUID> bodies = tubuh.get(town);
		CompoundTag tag = lomba(level.getServer(), town);
		ListTag rivals = tag.getListOrEmpty("saingan");
		boolean changed = false;
		for (int i = 0; i < rivals.size(); i++) {
			CompoundTag rv = rivals.getCompoundOrEmpty(i);
			if (!rv.getBooleanOr("tampil", false) && now >= rv.getLongOr("selesai", Long.MAX_VALUE)) {
				rv.putBoolean("tampil", true);
				changed = true;
				if (bodies != null && i < bodies.size() && level.getEntity(bodies.get(i)) instanceof KultivatorNpc npc) {
					HidupApi.klip(npc, GerakId.HORMAT, 0, 40);
					npc.say(Component.translatable("kultivator.kota_besar.pil.saingan_selesai", rv.getIntOr("skor", 0)), 80);
				}
			}
		}
		if (changed) {
			simpan(level.getServer(), town, tag);
			for (ServerPlayer p : level.players()) {
				KotaBesarKalender.segarkan(p);
			}
		}
	}

	/** A furnace finished: counts for a contestant of a running gathering in the guild district. */
	private static void selesaiRacik(ServerLevel level, BlockPos pos, @Nullable UUID owner, ItemStack result, @Nullable PillQuality quality,
			boolean success, int alchemyLevel) {
		if (owner == null || !success || quality == null || instance == null) {
			return;
		}
		TempatInfo info = TempatApi.tempatDi(level, pos);
		if (info == null || !LembarKunci.DISTRIK_GILDA.equals(info.distrik())) {
			return;
		}
		Kandidat k = KotaBesarKota.di(level, pos);
		if (k == null) {
			return;
		}
		long town = KotaBesarKota.situs(k);
		MinecraftServer server = level.getServer();
		if (!KotaBesarKalender.berjalan(server, town, ID)) {
			return;
		}
		CompoundTag tag = lomba(server, town);
		CompoundTag entry = tag.getCompoundOrEmpty("daftar").getCompoundOrEmpty(owner.toString());
		if (entry.isEmpty()) {
			return;
		}
		long secs = (level.getGameTime() - entry.getLongOr("mulai", 0L)) / 20L;
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (secs > WAKTU / 20L) {
			if (player != null) {
				Msg.actionBar(player, Component.translatable("kultivator.kota_besar.pil.terlambat").withStyle(ChatFormatting.RED));
			}
			return;
		}
		int band = player == null ? 0 : Barang.pita(player);
		boolean layak = EkonomiApi.harga(result) >= 20L * (band + 1);
		int score = quality.ordinal() * 100 + (int) Math.max(0L, 300L - secs) / 3 + (layak ? 50 : 0);
		if (score > entry.getIntOr("skor", -1)) {
			entry.putInt("skor", score);
			CompoundTag all = tag.getCompoundOrEmpty("daftar");
			all.put(owner.toString(), entry);
			tag.put("daftar", all);
			simpan(server, town, tag);
			if (player != null) {
				Msg.chat(player, Component.translatable("kultivator.kota_besar.pil.skor", score,
						Component.translatable("kultivator.alchemy.quality." + quality.id()), secs).withStyle(ChatFormatting.AQUA));
				KotaBesarKalender.segarkan(player);
			}
		}
	}

	@Override
	public void akhir(ServerLevel level, Kandidat k, long town, boolean hidup) {
		MinecraftServer server = level.getServer();
		List<UUID> bodies = tubuh.remove(town);
		CompoundTag tag = lomba(server, town);
		List<Object[]> board = papan(server, tag);
		int place = 0;
		KultivatorNpc speaker = null;
		if (bodies != null) {
			for (UUID id : bodies) {
				Entity e = level.getEntity(id);
				if (e instanceof KultivatorNpc npc && npc.isAlive()) {
					if (speaker == null) {
						speaker = npc;
					}
				}
			}
		}
		for (Object[] row : board) {
			place++;
			if (row[2] == null) {
				continue;
			}
			UUID id = (UUID) row[2];
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			long fee = tag.getCompoundOrEmpty("daftar").getCompoundOrEmpty(id.toString()).getLongOr("biaya", 0L);
			if ((Integer) row[1] < 0) {
				p.sendSystemMessage(Component.translatable("kultivator.kota_besar.pil.tanpa_pil").withStyle(ChatFormatting.GRAY));
				continue;
			}
			KotaBesarData data = KotaBesarData.get(server);
			switch (place) {
				case 1 -> {
					EkonomiApi.beri(p, fee * 4L, "kotabesar.pil");
					KotaData.get(server).addReputation(town, id, 3);
					long day = Kota.day(server);
					CompoundTag pd = data.pemain(id);
					if (day - pd.getLongOr("herba_pil", -100L) >= 7L) {
						pd.putLong("herba_pil", day);
						Barang.beri(p, SpiritHerbs.randomFor(null, p.getRandom(), 200, 300));
					}
					pd.putInt("poin_alkemis", pd.getIntOr("poin_alkemis", 0) + 2);
					data.beriGelar(id, "juara_pil");
					data.setDirty();
					Advancements.grant(p, "tempat/alkemis_juara");
					Msg.title(p, Component.translatable("kultivator.kota_besar.pil.juara").withStyle(ChatFormatting.GOLD),
							Component.translatable("kultivator.kota_besar.pil.juara.sub", row[1]), 10, 70, 20);
					if (speaker != null) {
						Voice.say(speaker, "kotabesar.alchemy_winner", null);
					}
					CeritaApi.kabar(level, town, "kota_besar.juara_pil", p.getDisplayName());
				}
				case 2 -> {
					EkonomiApi.beri(p, fee * 2L, "kotabesar.pil");
					KotaData.get(server).addReputation(town, id, 2);
					data.pemain(id).putInt("poin_alkemis", data.pemain(id).getIntOr("poin_alkemis", 0) + 1);
					data.setDirty();
				}
				case 3 -> {
					EkonomiApi.beri(p, fee, "kotabesar.pil");
					KotaData.get(server).addReputation(town, id, 1);
				}
				default -> {
				}
			}
			p.sendSystemMessage(Component.translatable("kultivator.kota_besar.pil.hasil", place, board.size(), row[1]).withStyle(ChatFormatting.AQUA));
		}
		if (bodies != null) {
			for (UUID id : bodies) {
				Entity e = level.getEntity(id);
				if (e instanceof KultivatorNpc npc && npc.isAlive()) {
					npc.vanish();
				}
			}
		}
		boolean ikut = board.stream().anyMatch(row -> row[2] != null);
		KotaBesarData.get(server).putString(town, "ringkasan." + ID, ikut ? "kultivator.kota_besar.acara.ringkasan.pil"
				: "kultivator.kota_besar.acara.ringkasan.pil.kosong");
	}

	/**
	 * The board sorted by score: rows {nama S (player name or rival key), score Integer (-1 no pill yet), player UUID or
	 * null, revealed Boolean}.
	 */
	private static List<Object[]> papan(MinecraftServer server, CompoundTag tag) {
		List<Object[]> rows = new ArrayList<>();
		CompoundTag all = tag.getCompoundOrEmpty("daftar");
		for (String id : all.keySet()) {
			CompoundTag e = all.getCompoundOrEmpty(id);
			try {
				rows.add(new Object[]{e.getStringOr("nama", "?"), e.getIntOr("skor", -1), UUID.fromString(id), Boolean.TRUE});
			} catch (IllegalArgumentException ignored) {
				// skip
			}
		}
		ListTag rivals = tag.getListOrEmpty("saingan");
		for (int i = 0; i < rivals.size(); i++) {
			CompoundTag rv = rivals.getCompoundOrEmpty(i);
			rows.add(new Object[]{"#" + rv.getIntOr("look", 0), rv.getIntOr("skor", 0), null, rv.getBooleanOr("tampil", false)});
		}
		rows.sort((a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
		return rows;
	}

	@Override
	public void lembar(ServerPlayer player, long town, @Nullable KultivatorNpc npc, CompoundTag tag) {
		MinecraftServer server = player.level().getServer();
		long fee = biaya(player);
		tag.putLong(LembarKunci.BIAYA, fee);
		LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.pil.catatan", Barang.nb(fee), WAKTU / 1200L));
		if (!KotaBesarKalender.berjalan(server, town, ID)) {
			return;
		}
		CompoundTag l = lomba(server, town);
		CompoundTag me = l.getCompoundOrEmpty("daftar").getCompoundOrEmpty(player.getUUID().toString());
		tag.putBoolean(LembarKunci.Acara.TERDAFTAR, !me.isEmpty());
		if (!me.isEmpty()) {
			long left = WAKTU - (player.level().getGameTime() - me.getLongOr("mulai", 0L));
			if (left > 0L) {
				tag.putLong(LembarKunci.SISA_TICK, left);
			}
		}
		ListTag skor = new ListTag();
		for (Object[] row : papan(server, l)) {
			CompoundTag r = new CompoundTag();
			String name = (String) row[0];
			boolean rival = row[2] == null;
			if (rival && !(Boolean) row[3]) {
				continue;
			}
			LembarKunci.teks(r, LembarKunci.NAMA, rival ? KotaNames.person(Integer.parseInt(name.substring(1)), NpcRole.ALKEMIS) : Component.literal(name));
			r.putInt(LembarKunci.SKOR, Math.max(0, (Integer) row[1]));
			if (player.getUUID().equals(row[2])) {
				r.putBoolean(LembarKunci.ANDA, true);
			}
			skor.add(r);
		}
		tag.put(LembarKunci.SKOR, skor);
	}

	@Override
	public void aksi(ServerPlayer player, long town, @Nullable KultivatorNpc npc, String[] op) {
		MinecraftServer server = player.level().getServer();
		if (!LembarKunci.Acara.OP_DAFTAR.equals(op[0])) {
			return;
		}
		if (!KotaBesarKalender.berjalan(server, town, ID)) {
			KotaBesarKalender.segarkan(player, Component.translatable("kultivator.kota_besar.acara.belum_buka"), false);
			return;
		}
		CompoundTag l = lomba(server, town);
		CompoundTag all = l.getCompoundOrEmpty("daftar");
		if (all.contains(player.getUUID().toString())) {
			return;
		}
		long fee = biaya(player);
		if (!EkonomiApi.bayar(player, fee, "kotabesar.pil")) {
			KotaBesarKalender.segarkan(player, Component.translatable("kultivator.kota_besar.hukum.kurang", Barang.nb(fee)), false);
			return;
		}
		CompoundTag e = new CompoundTag();
		e.putLong("mulai", player.level().getGameTime());
		e.putLong("biaya", fee);
		e.putInt("skor", -1);
		e.putString("nama", player.getGameProfile().name());
		all.put(player.getUUID().toString(), e);
		l.put("daftar", all);
		simpan(server, town, l);
		if (npc != null) {
			HidupApi.klip(npc, GerakId.HORMAT, 0, 30);
		}
		KotaBesarKalender.segarkan(player, Component.translatable("kultivator.kota_besar.pil.terdaftar", WAKTU / 1200L), true);
	}
}
