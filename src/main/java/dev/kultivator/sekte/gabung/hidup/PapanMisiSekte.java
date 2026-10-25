package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.JenisMisi;
import dev.kultivator.dunia.tempat.ekonomi.MisiGilda;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.musuh.Camps;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.KillCredit;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sect mission board of DESIGN 41.6.3: one board per sect at titik {@code papan_misi} (the {@code PapanHidupEntity}
 * paper of 38 is the visible board; without the lot the main hall). Drawn lazily once per world day from the seed
 * {@code (world seed, sect, day)}: {@code 4 + class} missions from rank E up to the class's top rank (MEROSOT: E-D), the
 * first slot always an E chore so a servant finds work. Shared by every member: a taken mission leaves the board for the
 * others, except a group mission, which stays until 4 have joined.
 * <p>
 * Limits per member: {@code 3} missions a day (+1 Dalam, +1 Inti), at most {@code Keanggotaan.MISI_MAKS} running, a rank
 * no higher than the member's right ({@link JenisMisiGabung#pangkatMaks}). Time: E/D 1 day, C/B 2, A/S 3 (the guild's
 * {@code MisiGilda.HARI_BATAS}). Done = back to the board ({@link JenisMisiGabung#TAHAP_LAPOR}); the PK goes through the
 * one door {@code GabungApi.tambahPk}. Failed or expired: no PK, violation record +2 (not when a group fails together).
 * <p>
 * Storage: the board in {@code DataSekteGabung.papan}/{@code papanHari}; a member's missions as ids in
 * {@code Keanggotaan.misi} and their compounds in {@code Keanggotaan.bagian("hidup").misi}. Progress runs in
 * {@link #detik} (once a second, only for members with a running mission) and the death event ({@link #onMati}).
 * Quest goal kind {@code misi_sekte <jenis|*>} (stories of 41W count finished sect missions).
 */
public final class PapanMisiSekte {

	/** {@code Keanggotaan.bagian} key of module 41C. */
	public static final String BAGIAN = "hidup";
	static final String K_MISI = "misi";
	static final String K_HARI = "misi_hari";
	static final String K_JUMLAH = "misi_jumlah";
	static final String K_TERTUNDA = "pk_tertunda";
	/** The quest goal kind of finished sect missions. */
	public static final String TUJUAN_QUEST = "misi_sekte";
	/** Violation points of a failed or abandoned mission. */
	public static final int LANGGAR_GAGAL = 2;
	/** Blocks around the board titik that count as "back at the board". */
	public static final double RADIUS_LAPOR = 24.0D;
	/** Blocks within which a member may join a group mission (around one of its members, or at the board). */
	public static final double RADIUS_GABUNG = 32.0D;

	/** Runtime camp ids of BUNUH_KEMAH missions: "uuid|missionId" -> camp id (camps are not saved, reopened lazily). */
	private static final Map<String, Integer> KEMAH = new HashMap<>();
	/** Last progress line per player (game tick), so the action bar is not spammed. */
	private static final Map<UUID, Long> GARIS = new HashMap<>();

	static void reset() {
		for (int id : KEMAH.values()) {
			Camps.close(id);
		}
		KEMAH.clear();
		GARIS.clear();
	}

	static void daftarTujuanQuest() {
		QuestTracker.daftarTujuan(TUJUAN_QUEST, (a, j, id) -> j.equals(TUJUAN_QUEST) && (a.isEmpty() || a.get(0).equals("*") || id.equals(a.get(0))));
	}

	// ================================================================== the board

	/** Today's board of {@code sekte} (drawn on first use of the day; carried group missions keep their members). */
	public static List<CompoundTag> papan(MinecraftServer server, String sekte) {
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung ds = data.sekte(sekte);
		long hari = Kota.day(server);
		if (ds.papanHari != hari) {
			ds.papanHari = hari;
			ds.papan.clear();
			ds.papan.addAll(undi(server, sekte, hari, ds.merosot));
			data.setDirty();
		}
		return ds.papan;
	}

	/** The missions of {@code sekte} for day {@code hari} (pure apart from the site lookups). */
	static List<CompoundTag> undi(MinecraftServer server, String sekte, long hari, boolean merosot) {
		KelasSekte kelas = GabungApi.kelasSekte(sekte);
		int maks = merosot ? JenisMisi.D : kelas.misiMaks();
		int n = Math.min(GabungData.PAPAN_MAKS, 4 + kelas.nomor());
		long seed = server.overworld().getSeed();
		RandomSource random = RandomSource.create(seed ^ sekte.hashCode() * 0x9E3779B97F4A7C15L ^ hari * 0x5DEECE66DL);
		List<CompoundTag> out = new ArrayList<>();
		for (int slot = 0; slot < n * 2 && out.size() < n; slot++) {
			CompoundTag m = JenisMisiGabung.buat(server, sekte, hari, slot, maks, out.isEmpty(), random);
			if (m != null && out.stream().noneMatch(o -> o.getStringOr(JenisMisiGabung.JENIS, "").equals(m.getStringOr(JenisMisiGabung.JENIS, ""))
					&& o.getIntOr(JenisMisiGabung.MUTU, 0) == m.getIntOr(JenisMisiGabung.MUTU, 0))) {
				out.add(m);
			}
		}
		return out;
	}

	/** Missions a day for {@code pangkat}: 3, +1 from Dalam, +1 from Inti. */
	public static int batasHarian(Pangkat pangkat) {
		return 3 + (pangkat.minimal(Pangkat.DALAM) ? 1 : 0) + (pangkat.minimal(Pangkat.INTI) ? 1 : 0);
	}

	/** Missions {@code k} took on day {@code hari}. */
	public static int diambilHariIni(Keanggotaan k, long hari) {
		CompoundTag b = k.bagian(BAGIAN);
		return b.getLongOr(K_HARI, -1L) == hari ? b.getIntOr(K_JUMLAH, 0) : 0;
	}

	private static void hitungAmbil(Keanggotaan k, long hari) {
		CompoundTag b = k.bagian(BAGIAN);
		if (b.getLongOr(K_HARI, -1L) != hari) {
			b.putLong(K_HARI, hari);
			b.putInt(K_JUMLAH, 0);
		}
		b.putInt(K_JUMLAH, b.getIntOr(K_JUMLAH, 0) + 1);
	}

	/** The running mission compounds of {@code k} (live). */
	public static CompoundTag misiAktif(Keanggotaan k) {
		CompoundTag b = k.bagian(BAGIAN);
		CompoundTag m = b.getCompoundOrEmpty(K_MISI);
		if (!b.contains(K_MISI)) {
			b.put(K_MISI, m);
		}
		return m;
	}

	/** The running missions of {@code k} in taking order. */
	public static List<CompoundTag> aktif(Keanggotaan k) {
		CompoundTag all = misiAktif(k);
		List<CompoundTag> out = new ArrayList<>();
		for (String id : k.misi) {
			CompoundTag m = all.getCompoundOrEmpty(id);
			if (!m.isEmpty()) {
				out.add(m);
			}
		}
		return out;
	}

	/** Why {@code player} may not take board mission {@code m} now, or null. */
	public static @Nullable Component alasan(ServerPlayer player, Keanggotaan k, CompoundTag m, long hari) {
		int mutu = m.getIntOr(JenisMisiGabung.MUTU, 0);
		if (mutu > JenisMisiGabung.pangkatMaks(k.pangkat)) {
			Pangkat butuh = mutu <= JenisMisi.D ? Pangkat.LUAR : mutu <= JenisMisi.B ? Pangkat.DALAM : mutu <= JenisMisi.A ? Pangkat.INTI : Pangkat.PEWARIS;
			return Component.translatable("kultivator.gabung.misi.alasan.pangkat", butuh.nama());
		}
		int pita = dev.kultivator.dunia.tempat.ekonomi.Barang.pita(player);
		if (pita < MisiGilda.PITA_MIN[Mth.clamp(mutu, 0, 5)]) {
			return Component.translatable("kultivator.gabung.misi.alasan.ranah");
		}
		if (k.misi.contains(m.getStringOr(JenisMisiGabung.ID, ""))) {
			return Component.translatable("kultivator.gabung.misi.alasan.sudah");
		}
		if (diambilHariIni(k, hari) >= batasHarian(k.pangkat)) {
			return Component.translatable("kultivator.gabung.misi.alasan.harian", batasHarian(k.pangkat));
		}
		if (k.misi.size() >= Keanggotaan.MISI_MAKS) {
			return Component.translatable("kultivator.gabung.misi.alasan.penuh", Keanggotaan.MISI_MAKS);
		}
		return null;
	}

	/** {@code terima:ID}: takes a board mission (a group mission: founds the group). Null on success, else the reason. */
	public static @Nullable Component terima(ServerPlayer player, String id) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !GabungApi.aktif()) {
			return Component.translatable("kultivator.gabung.hidup.bukan_anggota");
		}
		long hari = Kota.day(server);
		List<CompoundTag> board = papan(server, sekte);
		CompoundTag entry = cari(board, id);
		if (entry == null) {
			return Component.translatable("kultivator.gabung.misi.alasan.hilang");
		}
		JenisMisiGabung.Jenis jenis = JenisMisiGabung.byId(entry.getStringOr(JenisMisiGabung.JENIS, ""));
		if (jenis != null && jenis.kelompok() && peserta(entry).size() > 0) {
			return gabung(player, id);
		}
		Component why = alasan(player, k, entry, hari);
		if (why != null) {
			return why;
		}
		CompoundTag mine = entry.copy();
		int mutu = mine.getIntOr(JenisMisiGabung.MUTU, 0);
		mine.putLong(JenisMisiGabung.BATAS, hari + MisiGilda.HARI_BATAS[Mth.clamp(mutu, 0, 5)] - 1L);
		mine.putInt(JenisMisiGabung.PK, JenisMisiGabung.imbalan(player, sekte, mine));
		mine.putLong(JenisMisiGabung.DIAMBIL, hari);
		if (jenis != null && jenis.kelompok()) {
			ListTag list = new ListTag();
			list.add(StringTag.valueOf(pesertaId(player)));
			entry.put(JenisMisiGabung.PESERTA, list.copy());
			mine.put(JenisMisiGabung.PESERTA, list);
		} else {
			board.remove(entry);
		}
		simpanMisi(server, k, mine, hari);
		mulaiPesan(player, mine);
		return null;
	}

	/** {@code gabung:ID}: joins a running group mission within {@link #RADIUS_GABUNG} blocks of a member or of the board. */
	public static @Nullable Component gabung(ServerPlayer player, String id) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !GabungApi.aktif()) {
			return Component.translatable("kultivator.gabung.hidup.bukan_anggota");
		}
		long hari = Kota.day(server);
		CompoundTag entry = cari(papan(server, sekte), id);
		JenisMisiGabung.Jenis jenis = entry == null ? null : JenisMisiGabung.byId(entry.getStringOr(JenisMisiGabung.JENIS, ""));
		if (entry == null || jenis == null || !jenis.kelompok()) {
			return Component.translatable("kultivator.gabung.misi.alasan.hilang");
		}
		List<String> ikut = peserta(entry);
		if (ikut.isEmpty()) {
			return terima(player, id);
		}
		if (ikut.size() >= JenisMisiGabung.KELOMPOK_MAKS) {
			return Component.translatable("kultivator.gabung.misi.alasan.kelompok_penuh", JenisMisiGabung.KELOMPOK_MAKS);
		}
		Component why = alasan(player, k, entry, hari);
		if (why != null) {
			return why;
		}
		if (!dekatKelompok(player, sekte, ikut)) {
			return Component.translatable("kultivator.gabung.misi.alasan.jauh", (int) RADIUS_GABUNG);
		}
		ikut.add(pesertaId(player));
		ListTag list = new ListTag();
		for (String p : ikut) {
			list.add(StringTag.valueOf(p));
		}
		entry.put(JenisMisiGabung.PESERTA, list.copy());
		// every member's copy learns the new roster
		GabungData data = GabungData.get(server);
		CompoundTag first = null;
		for (String p : ikut) {
			Keanggotaan other = data.peek(uuidDari(p));
			CompoundTag copy = other == null ? null : misiAktif(other).getCompoundOrEmpty(id);
			if (copy != null && !copy.isEmpty()) {
				copy.put(JenisMisiGabung.PESERTA, list.copy());
				if (first == null) {
					first = copy;
				}
			}
		}
		CompoundTag mine = (first != null ? first : entry).copy();
		mine.put(JenisMisiGabung.PESERTA, list.copy());
		mine.putInt(JenisMisiGabung.PK, JenisMisiGabung.imbalan(player, sekte, mine));
		mine.putLong(JenisMisiGabung.DIAMBIL, hari);
		if (!mine.contains(JenisMisiGabung.BATAS)) {
			mine.putLong(JenisMisiGabung.BATAS, hari + MisiGilda.HARI_BATAS[Mth.clamp(mine.getIntOr(JenisMisiGabung.MUTU, 0), 0, 5)] - 1L);
		}
		simpanMisi(server, k, mine, hari);
		for (String p : ikut) {
			ServerPlayer online = server.getPlayerList().getPlayer(uuidDari(p));
			if (online != null && online != player) {
				Msg.chat(online, Component.translatable("kultivator.gabung.misi.kelompok_masuk", player.getDisplayName(), ikut.size(),
						JenisMisiGabung.KELOMPOK_MAKS).withStyle(ChatFormatting.AQUA));
			}
		}
		mulaiPesan(player, mine);
		return null;
	}

	private static boolean dekatKelompok(ServerPlayer player, String sekte, List<String> ikut) {
		BlockPos board = GabungApi.titik(sekte, TitikGabung.PAPAN_MISI);
		double r2 = RADIUS_GABUNG * RADIUS_GABUNG;
		if (board != null && player.distanceToSqr(board.getX() + 0.5D, player.getY(), board.getZ() + 0.5D) <= r2) {
			return true;
		}
		MinecraftServer server = player.level().getServer();
		for (String p : ikut) {
			ServerPlayer other = server.getPlayerList().getPlayer(uuidDari(p));
			if (other != null && other.level() == player.level() && other.distanceToSqr(player) <= r2) {
				return true;
			}
		}
		return false;
	}

	/** {@code batal:ID}: gives a running mission up (counts as failed). */
	public static @Nullable Component batal(ServerPlayer player, String id) {
		Keanggotaan k = GabungApi.anggota(player);
		CompoundTag m = misiAktif(k).getCompoundOrEmpty(id);
		if (m.isEmpty() || !k.misi.contains(id)) {
			return Component.translatable("kultivator.gabung.misi.alasan.hilang");
		}
		gagal(player.level().getServer(), player.getUUID(), k, m, "kultivator.gabung.misi.gagal.batal", true);
		return null;
	}

	private static void simpanMisi(MinecraftServer server, Keanggotaan k, CompoundTag mine, long hari) {
		String id = mine.getStringOr(JenisMisiGabung.ID, "");
		k.misi.add(id);
		misiAktif(k).put(id, mine);
		hitungAmbil(k, hari);
		k.catat(hari, "kultivator.gabung.riwayat.misi_ambil", mine.getStringOr(JenisMisiGabung.JENIS, ""),
				JenisMisiGabung.huruf(mine.getIntOr(JenisMisiGabung.MUTU, 0)));
		GabungData.get(server).setDirty();
	}

	private static void mulaiPesan(ServerPlayer player, CompoundTag m) {
		Msg.chat(player, Component.translatable("kultivator.gabung.misi.diterima", JenisMisiGabung.huruf(m.getIntOr(JenisMisiGabung.MUTU, 0)),
				JenisMisiGabung.nama(m), m.getIntOr(JenisMisiGabung.PK, 0)).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, JenisMisiGabung.teks(m).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		Msg.chat(player, JenisMisiGabung.tujuanBaris(m).append(" · ").append(JenisMisiGabung.arah(player, m)).withStyle(ChatFormatting.YELLOW));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	static @Nullable CompoundTag cari(List<CompoundTag> list, String id) {
		for (CompoundTag m : list) {
			if (id.equals(m.getStringOr(JenisMisiGabung.ID, ""))) {
				return m;
			}
		}
		return null;
	}

	static List<String> peserta(CompoundTag m) {
		ListTag list = m.getListOrEmpty(JenisMisiGabung.PESERTA);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			String s = list.getStringOr(i, "");
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}

	/** "uuid|name" of a group roster line. */
	private static String pesertaId(ServerPlayer player) {
		return player.getUUID() + "|" + player.getName().getString();
	}

	static UUID uuidDari(String peserta) {
		int bar = peserta.indexOf('|');
		try {
			return UUID.fromString(bar < 0 ? peserta : peserta.substring(0, bar));
		} catch (IllegalArgumentException e) {
			return new UUID(0L, 0L);
		}
	}

	private static String namaDari(String peserta) {
		int bar = peserta.indexOf('|');
		return bar < 0 ? "?" : peserta.substring(bar + 1);
	}

	// ================================================================== progress (once a second per active member)

	/** One second of the running missions of {@code player} (from the sweep of {@link HidupAnggota}). */
	static void detik(ServerPlayer player, Keanggotaan k, long hari, long tick) {
		if (k.misi.isEmpty()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level();
		boolean overworld = level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
		boolean dirty = false;
		for (String id : List.copyOf(k.misi)) {
			CompoundTag m = misiAktif(k).getCompoundOrEmpty(id);
			if (m.isEmpty()) {
				k.misi.remove(id);
				dirty = true;
				continue;
			}
			if (hari > m.getLongOr(JenisMisiGabung.BATAS, hari)) {
				gagal(server, player.getUUID(), k, m, "kultivator.gabung.misi.gagal.waktu", !m.contains(JenisMisiGabung.PESERTA));
				continue;
			}
			if (!overworld) {
				continue;
			}
			dirty |= langkah(player, k, m, tick);
		}
		if (dirty) {
			GabungData.get(server).setDirty();
		}
	}

	private static boolean langkah(ServerPlayer player, Keanggotaan k, CompoundTag m, long tick) {
		String sekte = m.getStringOr(JenisMisiGabung.SEKTE, k.sekte);
		if (JenisMisiGabung.TAHAP_LAPOR.equals(m.getStringOr(JenisMisiGabung.TAHAP, ""))) {
			BlockPos board = GabungApi.titik(sekte, TitikGabung.PAPAN_MISI);
			if (board != null && datar(player, board.getX(), board.getZ()) <= RADIUS_LAPOR) {
				selesai(player, k, m);
				return true;
			}
			return false;
		}
		JenisMisiGabung.Jenis jenis = JenisMisiGabung.byId(m.getStringOr(JenisMisiGabung.JENIS, ""));
		if (jenis == null) {
			return false;
		}
		int x = m.getIntOr(JenisMisiGabung.X, 0);
		int z = m.getIntOr(JenisMisiGabung.Z, 0);
		double d = datar(player, x, z);
		int jumlah = m.getIntOr(JenisMisiGabung.JUMLAH, 1);
		int maju = m.getIntOr(JenisMisiGabung.KEMAJUAN, 0);
		ServerLevel level = player.level();
		switch (jenis.tujuan()) {
			case TIBA -> {
				boolean langit = "gabung_langit".equals(jenis.id());
				if (d <= JenisMisiGabung.RADIUS_TIBA && (!langit || player.getY() > m.getIntOr(JenisMisiGabung.Y, 0) - 24)) {
					m.putInt(JenisMisiGabung.KEMAJUAN, jumlah);
					String tujuan = m.getStringOr(JenisMisiGabung.SEKTE_TUJUAN, "");
					if ("gabung_antar".equals(jenis.id()) && !tujuan.isEmpty()) {
						SekteApi.diplomasi().adjust(player.getUUID(), tujuan, 1, "gabung.antar");
						Msg.chat(player, Component.translatable("kultivator.gabung.misi.antar_tiba", JenisMisiGabung.namaSekte(tujuan))
								.withStyle(ChatFormatting.AQUA));
					}
					keLapor(player, m);
					return true;
				}
			}
			case DIAM -> {
				boolean langit = "gabung_langit".equals(jenis.id());
				double r = langit ? 32.0D : JenisMisiGabung.RADIUS_DIAM;
				if (d <= r && (!langit || player.getY() > m.getIntOr(JenisMisiGabung.Y, 0) - 24)) {
					maju++;
					m.putInt(JenisMisiGabung.KEMAJUAN, maju);
					if (maju % 5 == 0) {
						level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 3, 0.4D, 0.4D, 0.4D, 0.01D);
					}
					garis(player, m, tick, true);
					if (maju >= jumlah) {
						keLapor(player, m);
					}
					return true;
				}
			}
			case SENYAP -> {
				if (d <= JenisMisiGabung.RADIUS_SENYAP) {
					boolean tersembunyi = player.isShiftKeyDown() || player.isInvisible();
					if (tersembunyi) {
						maju++;
						m.putInt(JenisMisiGabung.KEMAJUAN, maju);
					} else {
						int seen = m.getIntOr(JenisMisiGabung.TERLIHAT, 0) + 1;
						m.putInt(JenisMisiGabung.TERLIHAT, seen);
						Msg.actionBar(player, Component.translatable("kultivator.gabung.misi.senyap_terlihat", seen, JenisMisiGabung.SENYAP_GAGAL)
								.withStyle(ChatFormatting.RED));
						if (seen >= JenisMisiGabung.SENYAP_GAGAL) {
							gagal(player.level().getServer(), player.getUUID(), k, m, "kultivator.gabung.misi.gagal.ketahuan", true);
							return true;
						}
						return true;
					}
					garis(player, m, tick, true);
					if (maju >= jumlah) {
						keLapor(player, m);
					}
					return true;
				}
			}
			case BUNUH_MUSUH -> {
				if (d <= JenisMisiGabung.RADIUS_BURU) {
					garis(player, m, tick, false);
				}
			}
			case BUNUH_KEMAH -> bukaKemah(player, m);
			case BUNUH_BINATANG -> {
				if (d <= JenisMisiGabung.RADIUS_MUNCUL) {
					List<String> ikut = peserta(m);
					if (jenis.kelompok() && ikut.size() < JenisMisiGabung.KELOMPOK_MIN) {
						Msg.actionBar(player, Component.translatable("kultivator.gabung.misi.kelompok_kurang", ikut.size(), JenisMisiGabung.KELOMPOK_MIN)
								.withStyle(ChatFormatting.YELLOW));
						return false;
					}
					if (tick % 100L < 20L) {
						JenisMisiGabung.munculkanBinatang(level, player, m);
					}
					garis(player, m, tick, false);
				}
			}
			case SERAH -> {
				BlockPos board = GabungApi.titik(sekte, TitikGabung.PAPAN_MISI);
				if (board != null && datar(player, board.getX(), board.getZ()) <= 8.0D && serahkan(player, m)) {
					selesai(player, k, m);
					return true;
				}
			}
			case KEBUN -> {
				return kebun(player, m, tick);
			}
		}
		return false;
	}

	/** One harvest of a KEBUN mission: crouch {@code KEBUN_DETIK} s within 2.5 blocks of a {@code kerja:cangkul} plot. */
	private static boolean kebun(ServerPlayer player, CompoundTag m, long tick) {
		String sekte = m.getStringOr(JenisMisiGabung.SEKTE, "");
		BlockPos plot = player.isShiftKeyDown() ? TugasHarian.titikDekat(player, sekte, "kerja:cangkul", 2.5D) : null;
		if (plot == null) {
			m.putInt("jongkok", 0);
			return false;
		}
		long last = m.getLongOr(JenisMisiGabung.TERAKHIR, -JenisMisiGabung.KEBUN_JEDA);
		if (tick - last < JenisMisiGabung.KEBUN_JEDA) {
			Msg.actionBar(player, Component.translatable("kultivator.gabung.misi.kebun_tunggu",
					(int) Math.ceil((JenisMisiGabung.KEBUN_JEDA - (tick - last)) / 1200.0D)).withStyle(ChatFormatting.GRAY));
			return false;
		}
		int s = m.getIntOr("jongkok", 0) + 1;
		m.putInt("jongkok", s);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, plot.getX() + 0.5D, plot.getY() + 0.3D, plot.getZ() + 0.5D, 3, 0.4D, 0.2D, 0.4D, 0.0D);
		if (s < JenisMisiGabung.KEBUN_DETIK) {
			return true;
		}
		m.putInt("jongkok", 0);
		m.putLong(JenisMisiGabung.TERAKHIR, tick);
		int maju = m.getIntOr(JenisMisiGabung.KEMAJUAN, 0) + 1;
		m.putInt(JenisMisiGabung.KEMAJUAN, maju);
		level.playSound(null, plot, SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
		Msg.actionBar(player, Component.translatable("kultivator.gabung.misi.kebun_panen", maju, m.getIntOr(JenisMisiGabung.JUMLAH, 3))
				.withStyle(ChatFormatting.GREEN));
		if (maju >= m.getIntOr(JenisMisiGabung.JUMLAH, 3)) {
			keLapor(player, m);
		}
		return true;
	}

	/** Takes the items of a hand-in mission from the bag; false (nothing taken) when there are not enough. */
	private static boolean serahkan(ServerPlayer player, CompoundTag m) {
		int need = m.getIntOr(JenisMisiGabung.JUMLAH, 1);
		Inventory inv = player.getInventory();
		int have = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack s = inv.getItem(slot);
			if (JenisMisiGabung.cocokSerah(m, s)) {
				have += s.getCount();
			}
		}
		if (have < need) {
			return false;
		}
		int left = need;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE && left > 0; slot++) {
			ItemStack s = inv.getItem(slot);
			if (JenisMisiGabung.cocokSerah(m, s)) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
		inv.setChanged();
		m.putInt(JenisMisiGabung.KEMAJUAN, need);
		return true;
	}

	private static void bukaKemah(ServerPlayer player, CompoundTag m) {
		String key = player.getUUID() + "|" + m.getStringOr(JenisMisiGabung.ID, "");
		Integer camp = KEMAH.get(key);
		if (camp != null && Camps.exists(camp)) {
			return;
		}
		ServerLevel level = player.level();
		UUID owner = player.getUUID();
		String id = m.getStringOr(JenisMisiGabung.ID, "");
		BlockPos at = new BlockPos(m.getIntOr(JenisMisiGabung.X, 0), m.getIntOr(JenisMisiGabung.Y, 64), m.getIntOr(JenisMisiGabung.Z, 0));
		int opened = Camps.open(level, at, owner, cleared -> {
			KEMAH.remove(key);
			MinecraftServer server = level.getServer();
			ServerPlayer online = server.getPlayerList().getPlayer(owner);
			Keanggotaan k = GabungData.get(server).peek(owner);
			if (!cleared || online == null || k == null) {
				return;
			}
			CompoundTag mine = misiAktif(k).getCompoundOrEmpty(id);
			if (!mine.isEmpty() && JenisMisiGabung.TAHAP_JALAN.equals(mine.getStringOr(JenisMisiGabung.TAHAP, ""))) {
				mine.putInt(JenisMisiGabung.KEMAJUAN, mine.getIntOr(JenisMisiGabung.JUMLAH, 1));
				keLapor(online, mine);
				GabungData.get(server).setDirty();
			}
		});
		KEMAH.put(key, opened);
	}

	private static void tutupKemah(UUID player, String id) {
		Integer camp = KEMAH.remove(player + "|" + id);
		if (camp != null) {
			Camps.close(camp);
		}
	}

	private static void keLapor(ServerPlayer player, CompoundTag m) {
		m.putString(JenisMisiGabung.TAHAP, JenisMisiGabung.TAHAP_LAPOR);
		Msg.chat(player, Component.translatable("kultivator.gabung.misi.tujuan_selesai", JenisMisiGabung.nama(m)).withStyle(ChatFormatting.GREEN));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	private static void garis(ServerPlayer player, CompoundTag m, long tick, boolean selalu) {
		Long last = GARIS.get(player.getUUID());
		if (!selalu && last != null && tick - last < 60L) {
			return;
		}
		GARIS.put(player.getUUID(), tick);
		Msg.actionBar(player, JenisMisiGabung.tujuanBaris(m).withStyle(ChatFormatting.YELLOW));
	}

	private static double datar(ServerPlayer player, int x, int z) {
		double dx = x + 0.5D - player.getX();
		double dz = z + 0.5D - player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ================================================================== kills

	/** The death event: hostile kills near a hunt target, the marked beasts of a beast or group mission. */
	static void onMati(LivingEntity victim, DamageSource source) {
		if (!(victim.level() instanceof ServerLevel level) || !GabungApi.aktif()) {
			return;
		}
		MinecraftServer server = level.getServer();
		GabungData data = GabungData.get(server);
		String tag = null;
		for (String t : victim.entityTags()) {
			if (t.startsWith("kultivator.gabung.misi.")) {
				tag = t;
				break;
			}
		}
		if (tag != null) {
			binatangMati(server, data, tag);
			return;
		}
		KillCredit.Credit credit = KillCredit.resolve(victim, source);
		if (credit == null || !(victim instanceof Enemy)) {
			return;
		}
		ServerPlayer player = credit.owner();
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || k.misi.isEmpty()) {
			return;
		}
		for (CompoundTag m : aktif(k)) {
			JenisMisiGabung.Jenis jenis = JenisMisiGabung.byId(m.getStringOr(JenisMisiGabung.JENIS, ""));
			if (jenis == null || jenis.tujuan() != JenisMisiGabung.Tujuan.BUNUH_MUSUH
					|| !JenisMisiGabung.TAHAP_JALAN.equals(m.getStringOr(JenisMisiGabung.TAHAP, ""))) {
				continue;
			}
			double dx = m.getIntOr(JenisMisiGabung.X, 0) - victim.getX();
			double dz = m.getIntOr(JenisMisiGabung.Z, 0) - victim.getZ();
			if (dx * dx + dz * dz > JenisMisiGabung.RADIUS_BURU * JenisMisiGabung.RADIUS_BURU) {
				continue;
			}
			int maju = m.getIntOr(JenisMisiGabung.KEMAJUAN, 0) + 1;
			m.putInt(JenisMisiGabung.KEMAJUAN, maju);
			Msg.actionBar(player, JenisMisiGabung.tujuanBaris(m).withStyle(ChatFormatting.YELLOW));
			if (maju >= m.getIntOr(JenisMisiGabung.JUMLAH, 1)) {
				keLapor(player, m);
			}
			data.setDirty();
			return;
		}
	}

	private static void binatangMati(MinecraftServer server, GabungData data, String tag) {
		// every member (online or not) whose running mission owns this tag counts the kill; group members all share it
		for (Map.Entry<UUID, Keanggotaan> e : data.semuaAnggota().entrySet()) {
			Keanggotaan k = e.getValue();
			if (k.misi.isEmpty()) {
				continue;
			}
			for (CompoundTag m : aktif(k)) {
				if (!tag.equals(JenisMisiGabung.tagBinatang(m.getStringOr(JenisMisiGabung.ID, "")))
						|| !JenisMisiGabung.TAHAP_JALAN.equals(m.getStringOr(JenisMisiGabung.TAHAP, ""))) {
					continue;
				}
				int maju = m.getIntOr(JenisMisiGabung.KEMAJUAN, 0) + 1;
				m.putInt(JenisMisiGabung.KEMAJUAN, maju);
				ServerPlayer online = server.getPlayerList().getPlayer(e.getKey());
				if (maju >= m.getIntOr(JenisMisiGabung.JUMLAH, 1)) {
					m.putString(JenisMisiGabung.TAHAP, JenisMisiGabung.TAHAP_LAPOR);
					if (online != null) {
						keLapor(online, m);
					}
				} else if (online != null) {
					Msg.actionBar(online, JenisMisiGabung.tujuanBaris(m).withStyle(ChatFormatting.YELLOW));
				}
				data.setDirty();
			}
		}
	}

	// ================================================================== done / failed

	/** Pays a finished mission (one PK door) and records it. */
	static void selesai(ServerPlayer player, Keanggotaan k, CompoundTag m) {
		MinecraftServer server = player.level().getServer();
		String id = m.getStringOr(JenisMisiGabung.ID, "");
		String sekte = m.getStringOr(JenisMisiGabung.SEKTE, k.sekte);
		String jenis = m.getStringOr(JenisMisiGabung.JENIS, "");
		int mutu = m.getIntOr(JenisMisiGabung.MUTU, 0);
		int pk = m.getIntOr(JenisMisiGabung.PK, 1);
		List<String> ikut = peserta(m);
		if (!ikut.isEmpty()) {
			pk = Math.max(1, (int) Math.ceil(pk * JenisMisiGabung.BONUS_KELOMPOK / ikut.size()));
		}
		k.misi.remove(id);
		misiAktif(k).remove(id);
		tutupKemah(player.getUUID(), id);
		k.misiTotal++;
		long hari = Kota.day(server);
		k.catat(hari, "kultivator.gabung.riwayat.misi_selesai", jenis, JenisMisiGabung.huruf(mutu), Integer.toString(pk));
		GabungData.get(server).setDirty();
		GabungApi.tambahPk(player, pk, "gabung.misi");
		if (k.misiTotal >= 10) {
			Advancements.grant(player, "sekte/gabung_misi_10");
		}
		if (mutu >= JenisMisi.A) {
			DiplomasiGabung.ubahSumber(server, sekte, mutu >= JenisMisi.S ? 4 : 2, "gabung.misi");
			if (k.pangkat == Pangkat.DALAM) {
				dev.kultivator.sekte.gabung.Promosi.lulus(player, Pangkat.INTI); // 41.7: one A mission opens Inti
			}
		}
		TokohSekte.Tokoh luar = TokohSekte.jabatan(server, sekte, Jabatan.TETUA_LUAR);
		if (luar != null) {
			TokohSekte.ubahSikap(server, luar.id(), player.getUUID(), 1);
		}
		CeritaApi.peristiwa(player, TUJUAN_QUEST, jenis);
		Msg.chat(player, Component.translatable("kultivator.gabung.misi.selesai", JenisMisiGabung.nama(m), pk).withStyle(ChatFormatting.GOLD));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.3F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0D, player.getZ(), 8, 0.5D, 0.6D, 0.5D, 0.0D);
	}

	/** Ends a mission without pay; {@code hukum}: violation record +2 (41.6.3). Works for offline members too. */
	static void gagal(MinecraftServer server, UUID player, Keanggotaan k, CompoundTag m, String alasanKey, boolean hukum) {
		String id = m.getStringOr(JenisMisiGabung.ID, "");
		k.misi.remove(id);
		misiAktif(k).remove(id);
		tutupKemah(player, id);
		long hari = Kota.day(server);
		if (hukum) {
			k.pelanggaran = Math.min(dev.kultivator.sekte.gabung.Aturan.MAKS, k.pelanggaran + LANGGAR_GAGAL);
		}
		k.catat(hari, "kultivator.gabung.riwayat.misi_gagal", m.getStringOr(JenisMisiGabung.JENIS, ""), hukum ? Integer.toString(LANGGAR_GAGAL) : "0");
		GabungData.get(server).setDirty();
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) {
			MutableComponent line = Component.translatable("kultivator.gabung.misi.gagal", JenisMisiGabung.nama(m), Component.translatable(alasanKey));
			if (hukum) {
				line.append(" ").append(Component.translatable("kultivator.gabung.misi.gagal_catatan", LANGGAR_GAGAL));
			}
			Msg.chat(online, line.withStyle(ChatFormatting.RED));
		}
	}

	/** Lazy daily upkeep (offline time): expired missions fail. */
	static void sentuh(ServerPlayer player, Keanggotaan k, long hari) {
		for (CompoundTag m : aktif(k)) {
			if (hari > m.getLongOr(JenisMisiGabung.BATAS, hari)) {
				gagal(player.level().getServer(), player.getUUID(), k, m, "kultivator.gabung.misi.gagal.waktu", !m.contains(JenisMisiGabung.PESERTA));
			}
		}
	}

	// ================================================================== sheet and chat

	/**
	 * Fills the mission part of sheet {@code gabung_papan} ({@code GabungLembar.Papan}): {@code misi_hari},
	 * {@code misi_maks} and {@code daftar} with the board (tab papan / kelompok), the running missions (tab aktif) and the
	 * group rosters. 41U-2's builder adds the header, the war (41D) and calls {@code TugasHarian.isiLembar} for the duties.
	 */
	public static void isiLembar(ServerPlayer player, String sekte, CompoundTag lembar) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		long hari = Kota.day(server);
		lembar.putInt(GabungLembar.Papan.MISI_HARI, diambilHariIni(k, hari));
		lembar.putInt(GabungLembar.Papan.MISI_MAKS, batasHarian(k.pangkat));
		ListTag daftar = new ListTag();
		boolean anggota = k.anggotaDi(sekte);
		for (CompoundTag m : papan(server, sekte)) {
			JenisMisiGabung.Jenis jenis = JenisMisiGabung.byId(m.getStringOr(JenisMisiGabung.JENIS, ""));
			if (jenis == null || daftar.size() >= LembarKunci.DAFTAR_MAKS) {
				continue;
			}
			CompoundTag e = entri(player, sekte, m, jenis.kelompok() ? GabungLembar.Papan.TAB_KELOMPOK : GabungLembar.Papan.TAB_PAPAN);
			Component why = !anggota ? Component.translatable("kultivator.gabung.hidup.bukan_anggota") : alasan(player, k, m, hari);
			if (k.misi.contains(m.getStringOr(JenisMisiGabung.ID, ""))) {
				e.putString(LembarKunci.KEADAAN, LembarKunci.K_OK);
			} else if (why != null) {
				e.putString(LembarKunci.KEADAAN, k.misi.size() >= Keanggotaan.MISI_MAKS || diambilHariIni(k, hari) >= batasHarian(k.pangkat)
						? LembarKunci.K_BATAS : LembarKunci.K_TERKUNCI);
				e.putBoolean(LembarKunci.TERKUNCI, true);
				LembarKunci.teks(e, LembarKunci.ALASAN, why);
			} else {
				e.putString(LembarKunci.KEADAAN, LembarKunci.K_OK);
			}
			daftar.add(e);
		}
		long now = hari * 24000L + Kota.timeOfDay(server);
		for (CompoundTag m : aktif(k)) {
			if (daftar.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			CompoundTag e = entri(player, sekte, m, GabungLembar.Papan.TAB_AKTIF);
			LembarKunci.teks(e, LembarKunci.TEKS, JenisMisiGabung.tujuanBaris(m).append("\n").append(JenisMisiGabung.teks(m)));
			e.putInt(LembarKunci.PK, m.getIntOr(JenisMisiGabung.PK, 0));
			e.putLong(LembarKunci.SISA_TICK, Math.max(0L, (m.getLongOr(JenisMisiGabung.BATAS, hari) + 1L) * 24000L - now));
			e.putString(LembarKunci.KEADAAN, LembarKunci.K_OK);
			if (JenisMisiGabung.TAHAP_LAPOR.equals(m.getStringOr(JenisMisiGabung.TAHAP, ""))) {
				BlockPos board = GabungApi.titik(sekte, TitikGabung.PAPAN_MISI);
				if (board != null) {
					e.putInt(LembarKunci.X, board.getX());
					e.putInt(LembarKunci.Z, board.getZ());
				}
			}
			daftar.add(e);
		}
		lembar.put(LembarKunci.DAFTAR, daftar);
	}

	private static CompoundTag entri(ServerPlayer player, String sekte, CompoundTag m, String tab) {
		CompoundTag e = new CompoundTag();
		int mutu = m.getIntOr(JenisMisiGabung.MUTU, 0);
		e.putString(LembarKunci.ID, m.getStringOr(JenisMisiGabung.ID, ""));
		e.putString(GabungLembar.Papan.TAB_ENTRI, tab);
		e.putString(GabungLembar.Papan.MUTU_MISI, JenisMisiGabung.huruf(mutu));
		e.putString("jenis", m.getStringOr(JenisMisiGabung.JENIS, ""));
		LembarKunci.teks(e, LembarKunci.NAMA, JenisMisiGabung.nama(m));
		LembarKunci.teks(e, LembarKunci.TEKS, JenisMisiGabung.teks(m));
		e.putInt(LembarKunci.PK, m.contains(JenisMisiGabung.PK) ? m.getIntOr(JenisMisiGabung.PK, 0) : JenisMisiGabung.imbalan(player, sekte, m));
		e.putInt(LembarKunci.X, m.getIntOr(JenisMisiGabung.X, 0));
		e.putInt(LembarKunci.Z, m.getIntOr(JenisMisiGabung.Z, 0));
		List<String> ikut = peserta(m);
		JenisMisiGabung.Jenis jenis = JenisMisiGabung.byId(m.getStringOr(JenisMisiGabung.JENIS, ""));
		if (jenis != null && jenis.kelompok()) {
			ListTag roster = new ListTag();
			for (String p : ikut) {
				CompoundTag r = new CompoundTag();
				LembarKunci.teks(r, LembarKunci.NAMA, Component.literal(namaDari(p)));
				r.putBoolean(LembarKunci.ANDA, uuidDari(p).equals(player.getUUID()));
				roster.add(r);
			}
			e.put(GabungLembar.Papan.ANGGOTA, roster);
			e.putInt(GabungLembar.Papan.ANGGOTA_MAKS, JenisMisiGabung.KELOMPOK_MAKS);
		}
		return e;
	}

	/** {@code /kultivasi gabung papan}: the board and the running missions in chat, with clickable buttons. */
	public static void tulisChat(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null) {
			Msg.chat(player, Component.translatable("kultivator.gabung.hidup.bukan_anggota").withStyle(ChatFormatting.GRAY));
			return;
		}
		MinecraftServer server = player.level().getServer();
		long hari = Kota.day(server);
		Msg.chat(player, Component.translatable("kultivator.gabung.misi.chat.judul", JenisMisiGabung.namaSekte(sekte), diambilHariIni(k, hari),
				batasHarian(k.pangkat)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (CompoundTag m : papan(server, sekte)) {
			String id = m.getStringOr(JenisMisiGabung.ID, "");
			Component why = alasan(player, k, m, hari);
			MutableComponent line = Component.literal("[" + JenisMisiGabung.huruf(m.getIntOr(JenisMisiGabung.MUTU, 0)) + "] ")
					.withStyle(ChatFormatting.AQUA).append(JenisMisiGabung.nama(m).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" · " + JenisMisiGabung.imbalan(player, sekte, m) + " PK ").withStyle(ChatFormatting.GOLD));
			if (!peserta(m).isEmpty()) {
				line.append(Component.translatable("kultivator.gabung.misi.chat.kelompok", peserta(m).size(), JenisMisiGabung.KELOMPOK_MAKS)
						.withStyle(ChatFormatting.DARK_AQUA)).append(" ");
			}
			if (why == null) {
				line.append(Component.translatable("kultivator.gabung.misi.chat.terima").withStyle(s -> s.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/kultivasi gabung papan terima " + id))));
			} else {
				line.append(why.copy().withStyle(ChatFormatting.DARK_GRAY));
			}
			Msg.chat(player, line);
		}
		for (CompoundTag m : aktif(k)) {
			String id = m.getStringOr(JenisMisiGabung.ID, "");
			MutableComponent line = Component.translatable("kultivator.gabung.misi.chat.aktif", JenisMisiGabung.nama(m), JenisMisiGabung.tujuanBaris(m),
					JenisMisiGabung.arah(player, m)).withStyle(ChatFormatting.YELLOW).append(" ")
					.append(Component.translatable("kultivator.gabung.misi.chat.batal").withStyle(s -> s.withColor(ChatFormatting.RED)
							.withClickEvent(new ClickEvent.RunCommand("/kultivasi gabung papan batal " + id))));
			Msg.chat(player, line);
		}
	}

	private PapanMisiSekte() {
	}
}
