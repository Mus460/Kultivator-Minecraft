package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.data.Dao;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.KedaiTeh;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KotaEntities;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.musuh.Camps;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.util.KillCredit;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The graded guild missions (DESIGN 38.M.3): one board system. A board of the day is seeded by (site, day): the Gilda
 * Kultivator of the capital offers ranks E-S (8 missions), a town guild clerk E-C (8), a road post's copy E-D (5). Kinds
 * come from the {@link JenisMisi} registry ({@link EkonomiApi#daftarJenisMisi}; the twelve of 38EK below, the themed ones
 * of 38TK). Every mission points at a real place or a real foe: elite beasts, the existing bandit camps ({@code Camps}),
 * rogue bands ({@code RogueCultivators}), caves and towns from the site registry, a real road caravan ({@link
 * KafilahJalan}), a Sword Duel with a rival. 20 % of missions carry a moral twist decided in chat
 * ({@code /kultivasi misi pilih}).
 * <p>
 * Reward: {@code dasar(band)} of the town board (×2 per band, band capped at 6) × rank factor (E 0.6 … S 2.0), Nama Baik +2
 * / +3 / +4, guild points E 1 … S 16, 20 % a ware. Limits: missions a day by badge ({@link Lencana#misiPerHari}), time
 * E/D 1 day, C/B 2, A/S 3; at most 2 active. Ranks need the realm of their band (E Mortal … S Deity Transformation).
 */
public final class MisiGilda {

	public static final double[] PENGALI = {0.6D, 0.8D, 1.0D, 1.3D, 1.6D, 2.0D};
	public static final int[] POIN = {1, 2, 4, 7, 11, 16};
	public static final int[] NAMA_BAIK = {2, 2, 3, 3, 4, 4};
	public static final int[] HARI_BATAS = {1, 1, 2, 2, 3, 3};
	/** Lowest realm band per rank (E Mortal, D Qi Refining, C Foundation, B Golden Core, A Nascent Soul, S Deity). */
	public static final int[] PITA_MIN = {0, 1, 2, 3, 4, 5};
	public static final double PELUANG_BARANG = 0.20D;
	public static final double PELUANG_TIKUNGAN = 0.20D;
	public static final int AKTIF_MAKS = 2;
	public static final int[] HERBA_UMUR = {50, 100, 150, 200, 300, 300};
	/** Story variants of each 38EK kind ({@code kultivator.misi.<id>.cerita.0..4}). */
	public static final int CERITA_VARIAN = 5;
	static final String AKTIF = "misi_gilda";
	static final String TAG_PREFIX = "kultivator.gilda.";

	/** Board classes: a town clerk, the capital's guild hall, a road post's copy. */
	public static final int KELAS_KOTA = 0;
	public static final int KELAS_GILDA = 1;
	public static final int KELAS_POS = 2;

	// ---------------------------------------------------------------------------------------------------------------
	// The kinds of 38EK (38.M.3 table)

	static void daftarJenis() {
		EkonomiApi.daftarJenisMisi(new JenisMisi("buru", JenisMisi.E, JenisMisi.C, null, 14, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("kemah", JenisMisi.D, JenisMisi.B, null, 10, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("herba", JenisMisi.E, JenisMisi.B, null, 10, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("kawal", JenisMisi.E, JenisMisi.C, null, 6, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("binatang", JenisMisi.C, JenisMisi.S, "gua/sarang_binatang", 10, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("sarang", JenisMisi.B, JenisMisi.S, "gua/sarang_iblis", 8, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("begal", JenisMisi.D, JenisMisi.A, "pos/kemah_begal", 12, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("kafilah", JenisMisi.D, JenisMisi.B, null, 8, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("gua", JenisMisi.C, JenisMisi.A, "gua/", 8, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("kurir", JenisMisi.E, JenisMisi.C, "kampung/", 10, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("tawanan", JenisMisi.C, JenisMisi.A, null, 6, "38EK"));
		EkonomiApi.daftarJenisMisi(new JenisMisi("naga_muda", JenisMisi.B, JenisMisi.S, null, 5, "38EK"));
	}

	/** The kinds whose objective 38EK runs itself (others finish through {@link EkonomiApi#misiSelesai}). */
	private static final java.util.Set<String> SENDIRI = java.util.Set.of("buru", "kemah", "herba", "kawal", "binatang", "sarang", "begal", "kafilah",
			"gua", "kurir", "tawanan", "naga_muda");
	/** True for the twelve kinds whose objective and texts are 38EK's. */
	static boolean milikSendiri(String jenis) {
		return SENDIRI.contains(jenis);
	}

	/** Kinds with a moral twist (38.M.3 table). */
	private static final java.util.Set<String> BERTIKUNGAN = java.util.Set.of("binatang", "sarang", "begal", "kafilah", "kurir");

	static void init() {
		daftarJenis();
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(victim.level() instanceof ServerLevel level)) {
				return;
			}
			String tag = victim.entityTags().stream().filter(t -> t.startsWith(TAG_PREFIX)).findFirst().orElse(null);
			if (tag == null) {
				return;
			}
			String[] parts = tag.substring(TAG_PREFIX.length()).split("\\.");
			if (parts.length < 2) {
				return;
			}
			UUID owner;
			long id;
			try {
				owner = UUID.fromString(parts[0]);
				id = Long.parseLong(parts[1]);
			} catch (IllegalArgumentException exception) {
				return;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
			if (player == null) {
				return;
			}
			CompoundTag misi = cari(player, id);
			if (misi == null) {
				return;
			}
			if (victim instanceof KultivatorNpc && ("tawanan".equals(misi.getStringOr("jenis", "")) || "kawal".equals(misi.getStringOr("jenis", "")))) {
				gagal(player, misi, Component.translatable("kultivator.tempat_ekonomi.misi.gagal.dikawal"));
				return;
			}
			KillCredit.Credit credit = KillCredit.resolve(victim, source);
			if (credit != null && credit.owner() == player) {
				tahapSelesai(player, misi);
			}
		});
		DuelEndListeners.add(MisiGilda::onDuel);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Board

	/** One offer of a board. */
	public record Tawaran(long id, JenisMisi jenis, int pangkat, int x, int z, int varian) {
	}

	/** The board class of a guild clerk by the site its body belongs to. */
	static int kelas(MinecraftServer server, @Nullable KultivatorNpc npc) {
		if (npc == null) {
			return KELAS_KOTA;
		}
		DuniaSites.Site site = DuniaSites.byId(server, npc.anchorSite());
		if (site == null) {
			return KELAS_KOTA;
		}
		if (site.kind().startsWith("kotabesar") || site.kind().equals("kota/kota_besar")) {
			return KELAS_GILDA;
		}
		return site.kind().startsWith("pos/") ? KELAS_POS : KELAS_KOTA;
	}

	static int pangkatMaks(int kelas) {
		return kelas == KELAS_GILDA ? JenisMisi.S : kelas == KELAS_POS ? JenisMisi.D : JenisMisi.C;
	}

	/** Today's board of site {@code situs} with its centre {@code pusat}. */
	public static List<Tawaran> papan(ServerLevel level, long situs, BlockPos pusat, int kelas) {
		MinecraftServer server = level.getServer();
		long hari = Barang.hari(server);
		RandomSource random = RandomSource.create(situs * 0x5851F42D4C957F2DL + hari * 1_000_033L + kelas);
		List<JenisMisi> kinds = jenisPapan(kelas);
		int maks = pangkatMaks(kelas);
		List<Tawaran> list = new ArrayList<>();
		int n = ukuranPapan(kelas);
		int total = bobot(kinds);
		for (int slot = 0; slot < n && total > 0; slot++) {
			JenisMisi chosen = pilihJenis(kinds, total, random);
			int pangkat = pangkatTawaran(chosen, maks, random);
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double distance = 140.0D + pangkat * 60.0D + random.nextDouble() * 160.0D;
			int x = Mth.floor(pusat.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(pusat.getZ() + Math.sin(angle) * distance);
			BlockPos real = situsNyata(level, chosen, new BlockPos(x, pusat.getY(), z));
			if (real != null) {
				x = real.getX();
				z = real.getZ();
			}
			long id = Math.abs(situs * 31L + hari * 1_000_003L + slot * 7919L + chosen.id().hashCode()) % 1_000_000_000L + 1L;
			list.add(new Tawaran(id, chosen, pangkat, x, z, random.nextInt(CERITA_VARIAN)));
		}
		return list;
	}

	/** Offers on a board of class {@code kelas}: 5 at a road post's copy, 8 elsewhere. */
	static int ukuranPapan(int kelas) {
		return kelas == KELAS_POS ? 5 : 8;
	}

	/** The registered kinds a board of class {@code kelas} can offer (lowest rank within the class's top rank). */
	static List<JenisMisi> jenisPapan(int kelas) {
		int maks = pangkatMaks(kelas);
		List<JenisMisi> kinds = new ArrayList<>();
		for (JenisMisi jenis : EkonomiApi.jenisMisi()) {
			if (jenis.pangkatMin() <= maks) {
				kinds.add(jenis);
			}
		}
		return kinds;
	}

	static int bobot(List<JenisMisi> kinds) {
		int total = 0;
		for (JenisMisi jenis : kinds) {
			total += jenis.bobot();
		}
		return total;
	}

	/** A weighted pick of one kind ({@code total} = {@link #bobot}, above 0). */
	static JenisMisi pilihJenis(List<JenisMisi> kinds, int total, RandomSource random) {
		int pick = random.nextInt(total);
		for (JenisMisi jenis : kinds) {
			pick -= jenis.bobot();
			if (pick < 0) {
				return jenis;
			}
		}
		return kinds.getFirst();
	}

	/** The rank of an offer of {@code chosen}: even over the kind's ranks, capped by the board's top rank. */
	static int pangkatTawaran(JenisMisi chosen, int maks, RandomSource random) {
		int high = Math.min(chosen.pangkatMaks(), maks);
		return chosen.pangkatMin() + random.nextInt(Math.max(1, high - chosen.pangkatMin() + 1));
	}

	/** A real place of the kind's site within 1600 blocks of {@code near}, or null. */
	private static @Nullable BlockPos situsNyata(ServerLevel level, JenisMisi jenis, BlockPos near) {
		if (jenis.jenisSitus() == null) {
			return null;
		}
		DuniaSites.Site best = null;
		double bestSq = Double.MAX_VALUE;
		for (DuniaSites.Site site : DuniaSites.near(level, near, 1600, jenis.jenisSitus())) {
			double sq = site.pos().distSqr(near);
			if (sq < bestSq) {
				bestSq = sq;
				best = site;
			}
		}
		return best == null ? null : best.pos();
	}

	/**
	 * The stones offer {@code tawaran} pays this player: the same roll on the board preview and when taken (seeded by the
	 * offer and the player; before the detail pass the board showed one roll and paid another).
	 */
	static long imbalanTawaran(ServerPlayer player, Tawaran tawaran) {
		RandomSource random = RandomSource.create((tawaran.id() * 0x2545F4914F6CDD1DL) ^ player.getUUID().getLeastSignificantBits());
		return imbalan(Barang.pita(player), tawaran.pangkat(), random);
	}

	/** Stone reward of rank {@code pangkat} for the player's band (the town board's base, ×2 per band, cap 6). */
	static long imbalan(int band, int pangkat, RandomSource random) {
		long low;
		long high;
		if (band <= 1) {
			low = 6L;
			high = 12L;
		} else if (band == 2) {
			low = 15L;
			high = 30L;
		} else {
			low = 40L;
			high = 80L;
			for (int b = 4; b <= Math.min(band, 6); b++) {
				low *= 2L;
				high *= 2L;
			}
		}
		long base = low + (long) (random.nextDouble() * (high - low + 1L));
		return Math.max(1L, Math.round(base * PENGALI[Math.max(0, Math.min(5, pangkat))]));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Active missions

	static ListTag aktif(ServerPlayer player) {
		return EkonomiData.get(player.level().getServer()).daftar(player.getUUID(), AKTIF);
	}

	static @Nullable CompoundTag cari(ServerPlayer player, long id) {
		for (net.minecraft.nbt.Tag each : aktif(player)) {
			if (each instanceof CompoundTag misi && misi.getLongOr("id", -1L) == id) {
				return misi;
			}
		}
		return null;
	}

	/** Why the player cannot take {@code tawaran} now (null when he can). */
	static @Nullable Component alasan(ServerPlayer player, Tawaran tawaran) {
		MinecraftServer server = player.level().getServer();
		if (!EkonomiConfig.get().gilda) {
			return Component.translatable("kultivator.tempat_ekonomi.tutup");
		}
		if (Barang.pita(player) < PITA_MIN[tawaran.pangkat()]) {
			return Component.translatable("kultivator.tempat_ekonomi.butuh_ranah",
					Component.translatable("kultivator.realm." + dev.kultivator.data.Realm.values()[PITA_MIN[tawaran.pangkat()]].id()));
		}
		if (tawaran.pangkat() == JenisMisi.S && Lencana.bintang(player) < Lencana.BINTANG_S) {
			return Component.translatable("kultivator.tempat_ekonomi.misi.butuh_bintang", Lencana.BINTANG_S);
		}
		if (cari(player, tawaran.id()) != null) {
			return Component.translatable("kultivator.tempat_ekonomi.misi.sudah_diambil");
		}
		EkonomiData data = EkonomiData.get(server);
		if (data.pemain(player.getUUID()).getCompoundOrEmpty("misi_diambil").getBooleanOr(Long.toString(tawaran.id()), false)) {
			return Component.translatable("kultivator.tempat_ekonomi.misi.sudah_diambil");
		}
		if (aktif(player).size() >= AKTIF_MAKS) {
			return Component.translatable("kultivator.tempat_ekonomi.misi.aktif_penuh", AKTIF_MAKS);
		}
		if (data.harian(player.getUUID(), Barang.hari(server), "misi") >= Lencana.misiPerHari(player)) {
			return Component.translatable("kultivator.tempat_ekonomi.misi.batas", Lencana.misiPerHari(player));
		}
		return null;
	}

	/** Takes an offer of the board (the clerk's site {@code situs}). */
	static void terima(ServerPlayer player, @Nullable KultivatorNpc clerk, Tawaran tawaran, long situs, long town) {
		Component why = alasan(player, tawaran);
		if (why != null) {
			Lembar.pesan(player, why, false);
			return;
		}
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		long hari = Barang.hari(server);
		RandomSource random = RandomSource.create(tawaran.id() ^ player.getUUID().getLeastSignificantBits());
		CompoundTag misi = new CompoundTag();
		misi.putLong("id", tawaran.id());
		misi.putString("jenis", tawaran.jenis().id());
		misi.putInt("pangkat", tawaran.pangkat());
		misi.putInt("varian", tawaran.varian());
		misi.putLong("situs", situs);
		misi.putLong("town", town);
		misi.putLong("hari", hari);
		misi.putLong("batas", Barang.waktu(server) + HARI_BATAS[tawaran.pangkat()] * 24000L);
		misi.putInt("x", tawaran.x());
		misi.putInt("z", tawaran.z());
		misi.putString("dim", player.level().dimension().identifier().toString());
		misi.putInt("tahap", 0);
		misi.putInt("total", tahapTotal(tawaran.jenis().id()));
		misi.putLong("imbalan", imbalanTawaran(player, tawaran));
		misi.putBoolean("tikungan", BERTIKUNGAN.contains(tawaran.jenis().id()) && random.nextDouble() < PELUANG_TIKUNGAN);
		misi.putInt("pilihan", -1);
		if (!mulai(player, clerk, misi)) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.misi.tak_bisa"), false);
			return;
		}
		aktif(player).add(misi);
		CompoundTag taken = data.bagian(player.getUUID(), "misi_diambil");
		if (taken.getLongOr("hari", Long.MIN_VALUE) != hari) {
			for (String key : List.copyOf(taken.keySet())) {
				taken.remove(key);
			}
			taken.putLong("hari", hari);
		}
		taken.putBoolean(Long.toString(tawaran.id()), true);
		data.tambahHarian(player.getUUID(), hari, "misi", 1);
		data.touch();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
		Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.misi.diterima", judul(misi), arah(player, misi)).withStyle(
				ChatFormatting.GOLD));
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.misi.diterima.singkat", judul(misi)), true);
	}

	private static int tahapTotal(String jenis) {
		return switch (jenis) {
			case "binatang", "sarang", "kurir", "kafilah" -> 2;
			default -> 1;
		};
	}

	/** Prepares the objective in the world (camps, escorts, rivals); false when it cannot start. */
	private static boolean mulai(ServerPlayer player, @Nullable KultivatorNpc clerk, CompoundTag misi) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos target = new BlockPos(misi.getIntOr("x", 0), player.getBlockY(), misi.getIntOr("z", 0));
		switch (misi.getStringOr("jenis", "")) {
			case "kemah", "begal" -> {
				if (!misi.getBooleanOr("tikungan", false)) {
					bukaKemah(player, misi, target);
				}
			}
			case "kawal", "tawanan" -> {
				BlockPos dest = tujuanKota(level, player.blockPosition(), misi.getLongOr("town", 0L));
				if (dest == null) {
					return false;
				}
				misi.putInt("x", dest.getX());
				misi.putInt("z", dest.getZ());
				KultivatorNpc companion = pendamping(level, player, "tawanan".equals(misi.getStringOr("jenis", "")) ? NpcRole.TAWANAN : NpcRole.PEDAGANG_KELILING,
						misi);
				if (companion == null) {
					return false;
				}
				misi.putString("mob", companion.getUUID().toString());
			}
			case "kurir" -> {
				BlockPos dest = tujuanKota(level, player.blockPosition(), misi.getLongOr("town", 0L));
				if (dest != null && situsNyata(level, jenisById("kurir"), target) == null) {
					misi.putInt("x", dest.getX());
					misi.putInt("z", dest.getZ());
				}
			}
			case "kafilah" -> {
				long caravan = KafilahJalan.kawal(player, misi.getLongOr("town", 0L), misi.getLongOr("id", 0L));
				if (caravan == 0L) {
					return false;
				}
				misi.putLong("kafilah", caravan);
			}
			case "naga_muda" -> {
				BlockPos at = clerk != null ? clerk.blockPosition().offset(3, 0, 3) : player.blockPosition().offset(4, 0, 4);
				KultivatorNpc rival = dev.kultivator.dunia.tempat.hidup.HidupApi.tubuh(level, misi.getLongOr("situs", 0L), NpcRole.TUAN_MUDA, at,
						player.getYRot() + 180.0F);
				if (rival == null) {
					return false;
				}
				rival.addTag(TAG_PREFIX + player.getUUID() + "." + misi.getLongOr("id", 0L));
				misi.putString("mob", rival.getUUID().toString());
				misi.putInt("x", at.getX());
				misi.putInt("z", at.getZ());
				Component refused = DuelChallenges.challenge(player, rival, null, DuelRuleSet.suggested(0).withRealm(DuelRuleSet.RealmRule.TEKAN_RANAH));
				if (refused != null) {
					Msg.chat(player, refused.copy().withStyle(ChatFormatting.GRAY));
				}
			}
			default -> {
			}
		}
		return true;
	}

	private static JenisMisi jenisById(String id) {
		for (JenisMisi jenis : EkonomiApi.jenisMisi()) {
			if (jenis.id().equals(id)) {
				return jenis;
			}
		}
		return new JenisMisi(id, 0, 5, null, 1, "38EK");
	}

	private static void bukaKemah(ServerPlayer player, CompoundTag misi, BlockPos target) {
		ServerLevel level = (ServerLevel) player.level();
		long id = misi.getLongOr("id", 0L);
		UUID owner = player.getUUID();
		int camp = Camps.open(level, target, owner, cleared -> {
			ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner);
			if (cleared && online != null) {
				CompoundTag current = cari(online, id);
				if (current != null) {
					tahapSelesai(online, current);
				}
			}
		});
		misi.putInt("camp", camp);
	}

	/** The nearest other town (else a vanilla village) 200-2000 blocks away. */
	private static @Nullable BlockPos tujuanKota(ServerLevel level, BlockPos from, long town) {
		BlockPos best = null;
		double bestSq = 2000.0D * 2000.0D;
		for (long id : Towns.known(level.getServer())) {
			if (id == town || !level.dimension().equals(Towns.dimension(level.getServer(), id))) {
				continue;
			}
			BlockPos centre = Towns.centre(level.getServer(), id);
			if (centre != null && centre.distSqr(from) <= bestSq && centre.distSqr(from) > 200.0D * 200.0D) {
				bestSq = centre.distSqr(from);
				best = centre;
			}
		}
		if (best != null) {
			return best;
		}
		BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, from, 2000 / 16, false);
		return village != null && village.distSqr(from) > 150.0D * 150.0D ? village : null;
	}

	private static @Nullable KultivatorNpc pendamping(ServerLevel level, ServerPlayer player, NpcRole role, CompoundTag misi) {
		if (!DuniaBudget.mayAdd(level, DuniaBudget.NPC, player.position())) {
			return null;
		}
		KultivatorNpc npc = KotaEntities.NPC.create(level, EntitySpawnReason.EVENT);
		if (npc == null) {
			return null;
		}
		Vec3 spot = Targeting.safeSpot(level, npc, player.position().add(1.5D, 0.0D, 1.5D));
		if (spot == null) {
			spot = player.position();
		}
		int look = level.getRandom().nextInt();
		npc.snapTo(spot.x, spot.y, spot.z, player.getYRot(), 0.0F);
		npc.setup(role, 1, look, 1, 0, Dao.values()[Math.floorMod(look, Dao.COUNT)], Math.floorMod(look, 6), 0x8A6A3A,
				Component.translatable("kultivator.tempat_ekonomi.misi.pendamping." + role.name().toLowerCase(java.util.Locale.ROOT), KotaNames.person(look)));
		npc.setTown(misi.getLongOr("town", 0L));
		npc.setLonely(96.0D, 2400);
		npc.setCompanion(player.getUUID(), level.getGameTime() + HARI_BATAS[misi.getIntOr("pangkat", 0)] * 24000L);
		npc.setFightsMonsters(false);
		npc.addTag(TAG_PREFIX + player.getUUID() + "." + misi.getLongOr("id", 0L));
		if (!level.addFreshEntity(npc)) {
			return null;
		}
		DuniaBudget.track(DuniaBudget.NPC, npc);
		return npc;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Progress

	/** Every 20 ticks: arrival checks, spawns at the target, escorts, deadlines (online players with missions only). */
	static void tick(MinecraftServer server) {
		long now = Barang.waktu(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ListTag list = EkonomiData.get(server).pemain(player.getUUID()).getListOrEmpty(AKTIF);
			if (list.isEmpty()) {
				continue;
			}
			for (int i = list.size() - 1; i >= 0; i--) {
				CompoundTag misi = list.getCompoundOrEmpty(i);
				if (misi.getBooleanOr("selesai", false)) {
					continue;
				}
				if (now > misi.getLongOr("batas", Long.MAX_VALUE)) {
					gagal(player, misi, Component.translatable("kultivator.tempat_ekonomi.misi.gagal.waktu"));
					continue;
				}
				tickMisi(player, misi);
			}
		}
	}

	private static void tickMisi(ServerPlayer player, CompoundTag misi) {
		ServerLevel level = (ServerLevel) player.level();
		if (!level.dimension().identifier().toString().equals(misi.getStringOr("dim", ""))) {
			return;
		}
		double dx = misi.getIntOr("x", 0) + 0.5D - player.getX();
		double dz = misi.getIntOr("z", 0) + 0.5D - player.getZ();
		double distSq = dx * dx + dz * dz;
		String jenis = misi.getStringOr("jenis", "");
		boolean twist = misi.getBooleanOr("tikungan", false);
		int pilihan = misi.getIntOr("pilihan", -1);
		switch (jenis) {
			case "buru", "binatang" -> {
				if (distSq < 48.0D * 48.0D && !misi.contains("mob")) {
					if (twist && pilihan < 0 && "binatang".equals(jenis)) {
						tanyaTikungan(player, misi);
						return;
					}
					Mob mob = binatang(level, player, misi);
					if (mob != null) {
						misi.putString("mob", mob.getUUID().toString());
						EkonomiData.get(level.getServer()).touch();
						Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.misi." + jenis + ".muncul").withStyle(ChatFormatting.RED));
					}
				}
			}
			case "kemah", "begal" -> {
				if (twist && pilihan < 0 && distSq < 80.0D * 80.0D) {
					tanyaTikungan(player, misi);
				} else if (misi.contains("camp") && !Camps.exists(misi.getIntOr("camp", -1))) {
					// the camp lives in memory only: after a restart it is opened again at the same spot
					bukaKemah(player, misi, new BlockPos(misi.getIntOr("x", 0), player.getBlockY(), misi.getIntOr("z", 0)));
				}
			}
			case "sarang" -> {
				if (distSq < 40.0D * 40.0D && !misi.getBooleanOr("band", false) && misi.getIntOr("tahap", 0) == 0 && Sibuk.enemyRefusal(player) == null) {
					int pangkat = misi.getIntOr("pangkat", 3);
					int[] tiers = pangkat >= JenisMisi.S ? new int[] {3, 3, 2, 2} : pangkat >= JenisMisi.A ? new int[] {3, 2, 2} : new int[] {2, 2, 1};
					UUID owner = player.getUUID();
					long id = misi.getLongOr("id", 0L);
					BlockPos home = new BlockPos(misi.getIntOr("x", 0), player.getBlockY(), misi.getIntOr("z", 0));
					int n = RogueCultivators.spawnBand(level, Vec3.atCenterOf(home), tiers, Math.max(2, Barang.pita(player)), RogueCultivators.ROLE_QUEST, home,
							cleared -> {
								ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner);
								CompoundTag current = online == null ? null : cari(online, id);
								if (current != null) {
									if (cleared) {
										tahapSelesai(online, current);
									} else {
										current.putBoolean("band", false);
									}
								}
							});
					if (n > 0) {
						misi.putBoolean("band", true);
						Sibuk.enemyTaken(player, "gilda.sarang");
						Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.misi.sarang.muncul").withStyle(ChatFormatting.RED));
					}
				}
			}
			case "gua" -> {
				if (distSq < 20.0D * 20.0D) {
					tahapSelesai(player, misi);
				}
			}
			case "kurir" -> {
				if (distSq < 24.0D * 24.0D && misi.getIntOr("tahap", 0) == 0) {
					tahapSelesai(player, misi);
				}
			}
			case "kawal", "tawanan" -> {
				Entity companion = entitas(level, misi);
				if (companion != null) {
					double cx = misi.getIntOr("x", 0) + 0.5D - companion.getX();
					double cz = misi.getIntOr("z", 0) + 0.5D - companion.getZ();
					if (cx * cx + cz * cz < 32.0D * 32.0D) {
						if (companion instanceof KultivatorNpc npc) {
							npc.setCompanion(null, 0L);
							npc.vanish();
						}
						tahapSelesai(player, misi);
					}
				}
			}
			case "kafilah" -> {
				if (KafilahJalan.tiba(misi.getLongOr("kafilah", 0L))) {
					tahapSelesai(player, misi);
				} else if (twist && pilihan < 0 && KafilahJalan.kemajuan(misi.getLongOr("kafilah", 0L)) > 0.5D) {
					tanyaTikungan(player, misi);
				}
			}
			default -> {
				// themed kinds (38TK): reaching the site counts only when the registering module set no own objective
				if (!SENDIRI.contains(jenis) && misi.getBooleanOr("cukup_tiba", false) && distSq < 24.0D * 24.0D) {
					tahapSelesai(player, misi);
				}
			}
		}
	}

	private static @Nullable Entity entitas(ServerLevel level, CompoundTag misi) {
		try {
			return level.getEntity(UUID.fromString(misi.getStringOr("mob", "")));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static @Nullable Mob binatang(ServerLevel level, ServerPlayer player, CompoundTag misi) {
		int pangkat = misi.getIntOr("pangkat", 0);
		boolean induk = "binatang".equals(misi.getStringOr("jenis", ""));
		RandomSource random = level.getRandom();
		EntityType<? extends Mob> type = induk ? (pangkat >= JenisMisi.A ? EntityTypes.RAVAGER : EntityTypes.POLAR_BEAR)
				: switch (random.nextInt(4)) {
					case 0 -> EntityTypes.SPIDER;
					case 1 -> EntityTypes.WOLF;
					case 2 -> EntityTypes.HUSK;
					default -> EntityTypes.ZOMBIE;
				};
		for (int attempt = 0; attempt < 6; attempt++) {
			int x = misi.getIntOr("x", 0) + random.nextInt(9) - 4;
			int z = misi.getIntOr("z", 0) + random.nextInt(9) - 4;
			BlockPos column = new BlockPos(x, 0, z);
			if (!level.isPositionEntityTicking(column) || DuniaApi.safeGround(level, new BlockPos(x, player.getBlockY(), z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			Mob mob = type.create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				return null;
			}
			Vec3 spot = Targeting.safeSpot(level, mob, new Vec3(x + 0.5D, y, z + 0.5D));
			if (spot == null) {
				continue;
			}
			mob.snapTo(spot.x, spot.y, spot.z, random.nextFloat() * 360.0F, 0.0F);
			mob.addTag(TAG_PREFIX + player.getUUID() + "." + misi.getLongOr("id", 0L));
			mob.setPersistenceRequired();
			if (!level.addFreshEntity(mob)) {
				return null;
			}
			int band = Math.max(1, Barang.pita(player));
			EliteMonsters.makeElite(mob, Mth.clamp(1 + pangkat / 2, 1, 3), null, band);
			dev.kultivator.technique.spec.TechniquePower.markRealmFoe(mob);
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 2400, 0));
			return mob;
		}
		return null;
	}

	private static void onDuel(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		ServerPlayer player = winner instanceof ServerPlayer p ? p : loser instanceof ServerPlayer p2 ? p2 : null;
		LivingEntity other = winner == player ? loser : winner;
		if (player == null || other == null) {
			return;
		}
		String tag = other.entityTags().stream().filter(t -> t.startsWith(TAG_PREFIX + player.getUUID())).findFirst().orElse(null);
		if (tag == null) {
			return;
		}
		long id;
		try {
			id = Long.parseLong(tag.substring(tag.lastIndexOf('.') + 1));
		} catch (NumberFormatException exception) {
			return;
		}
		CompoundTag misi = cari(player, id);
		if (misi == null) {
			return;
		}
		if (winner == player) {
			tahapSelesai(player, misi);
		} else {
			gagal(player, misi, Component.translatable("kultivator.tempat_ekonomi.misi.gagal.duel"));
		}
		if (other instanceof KultivatorNpc npc) {
			npc.setLonely(16.0D, 200);
		}
	}

	/** One objective stage done; the last one marks the mission ready to hand in. */
	static void tahapSelesai(ServerPlayer player, CompoundTag misi) {
		int tahap = misi.getIntOr("tahap", 0) + 1;
		int total = misi.getIntOr("total", 1);
		String jenis = misi.getStringOr("jenis", "");
		// two-stage kinds: the second stage is coming back (or, for the courier, the reply)
		if (tahap >= total || "binatang".equals(jenis) || "sarang".equals(jenis)) {
			tahap = total;
		}
		misi.putInt("tahap", tahap);
		if (tahap >= total) {
			misi.putBoolean("selesai", true);
			if (BERTIKUNGAN.contains(jenis) && misi.getBooleanOr("tikungan", false) && misi.getIntOr("pilihan", -1) < 0) {
				tanyaTikungan(player, misi);
			}
			Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.misi.siap_serah", judul(misi)).withStyle(ChatFormatting.GREEN));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7F, 1.4F);
		}
		EkonomiData.get(player.level().getServer()).touch();
	}

	/** Gives up mission {@code misi} (deadline, death of the escorted, lost duel): Nama Baik −1 at its town. */
	static void gagal(ServerPlayer player, CompoundTag misi, Component why) {
		bersihkan(player, misi);
		ListTag list = aktif(player);
		list.removeIf(each -> each instanceof CompoundTag c && c.getLongOr("id", -1L) == misi.getLongOr("id", -2L));
		KotaData.get(player.level().getServer()).addReputation(misi.getLongOr("town", 0L), player.getUUID(), -1);
		EkonomiData.get(player.level().getServer()).touch();
		Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.misi.gagal", judul(misi), why).withStyle(ChatFormatting.RED));
	}

	private static void bersihkan(ServerPlayer player, CompoundTag misi) {
		if (misi.contains("camp")) {
			Camps.close(misi.getIntOr("camp", -1));
		}
		Entity entity = entitas((ServerLevel) player.level(), misi);
		if (entity instanceof KultivatorNpc npc) {
			npc.setCompanion(null, 0L);
			npc.vanish();
		}
		if (misi.contains("kafilah")) {
			KafilahJalan.lepas(misi.getLongOr("kafilah", 0L));
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Twists (chat choice)

	private static void tanyaTikungan(ServerPlayer player, CompoundTag misi) {
		if (misi.getBooleanOr("ditanya", false)) {
			return;
		}
		misi.putBoolean("ditanya", true);
		EkonomiData.get(player.level().getServer()).touch();
		String jenis = misi.getStringOr("jenis", "");
		long id = misi.getLongOr("id", 0L);
		Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.misi.tikungan." + jenis).withStyle(ChatFormatting.LIGHT_PURPLE));
		MutableComponent a = Component.translatable("kultivator.tempat_ekonomi.misi.tikungan." + jenis + ".a").withStyle(style -> style
				.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.RunCommand("/kultivasi misi pilih " + id + " 0")));
		MutableComponent b = Component.translatable("kultivator.tempat_ekonomi.misi.tikungan." + jenis + ".b").withStyle(style -> style
				.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent.RunCommand("/kultivasi misi pilih " + id + " 1")));
		Msg.chat(player, Component.literal("  [").append(a).append("]   [").append(b).append("]"));
	}

	/** {@code /kultivasi misi pilih <id> <0|1>}: the player's choice at a twist. */
	public static void pilih(ServerPlayer player, long id, int pilihan) {
		CompoundTag misi = cari(player, id);
		if (misi == null || !misi.getBooleanOr("tikungan", false) || misi.getIntOr("pilihan", -1) >= 0 || pilihan < 0 || pilihan > 1) {
			return;
		}
		misi.putInt("pilihan", pilihan);
		String jenis = misi.getStringOr("jenis", "");
		BlockPos target = new BlockPos(misi.getIntOr("x", 0), player.getBlockY(), misi.getIntOr("z", 0));
		switch (jenis) {
			case "binatang" -> {
				if (pilihan == 0) {
					// let the mother be: half the reward, +3 Nama Baik, done
					misi.putLong("imbalan", Math.max(1L, misi.getLongOr("imbalan", 0L) / 2L));
					misi.putInt("bonus_nb", 3);
					tahapSelesai(player, misi);
				}
			}
			case "begal" -> {
				if (pilihan == 0) {
					// pardon the starving farmers: hand them 20 NB, the camp is left in peace
					if (!NilaiBatu.pay(player, 20L)) {
						misi.putInt("pilihan", -1);
						misi.putBoolean("ditanya", false);
						Lembar.kurangUang(player, 20L);
						return;
					}
					misi.putInt("bonus_nb", 3);
					tahapSelesai(player, misi);
				} else {
					bukaKemah(player, misi, target);
				}
			}
			case "kemah" -> bukaKemah(player, misi, target);
			case "sarang" -> {
				// 0 free everyone (the spy slips away: Nama Baik −2), 1 question them first (+2, one more guild point)
				misi.putInt("bonus_nb", pilihan == 0 ? -2 : 2);
				misi.putInt("bonus_poin", pilihan == 0 ? 0 : 1);
			}
			case "kafilah" -> {
				// 0 report the smuggling (+2 Nama Baik), 1 keep quiet for a cut (+30 % stones, −2 Nama Baik)
				misi.putInt("bonus_nb", pilihan == 0 ? 2 : -2);
				if (pilihan == 1) {
					misi.putLong("imbalan", Math.round(misi.getLongOr("imbalan", 0L) * 1.3D));
				}
			}
			case "kurir" -> {
				// the letter is a duel challenge: 0 deliver it as it is, 1 warn the receiver (+1 Nama Baik, a duel follows)
				misi.putInt("bonus_nb", pilihan == 0 ? 0 : 1);
			}
			default -> {
			}
		}
		EkonomiData.get(player.level().getServer()).touch();
		Msg.actionBar(player, Component.translatable("kultivator.tempat_ekonomi.misi.dipilih").withStyle(ChatFormatting.GRAY));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Hand-in

	/** Hands in mission {@code id} at a guild clerk; herb missions take the herb now. */
	static void serah(ServerPlayer player, long id) {
		CompoundTag misi = cari(player, id);
		if (misi == null) {
			return;
		}
		String jenis = misi.getStringOr("jenis", "");
		if ("herba".equals(jenis) && !misi.getBooleanOr("selesai", false)) {
			int age = HERBA_UMUR[Math.max(0, Math.min(5, misi.getIntOr("pangkat", 0)))];
			if (!ambilHerba(player, age)) {
				Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.misi.herba.kurang", age), false);
				return;
			}
			misi.putBoolean("selesai", true);
		}
		if (!misi.getBooleanOr("selesai", false)) {
			Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.misi.belum"));
			return;
		}
		if (misi.getBooleanOr("tikungan", false) && misi.getIntOr("pilihan", -1) < 0 && BERTIKUNGAN.contains(jenis)) {
			tanyaTikungan(player, misi);
			Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.misi.pilih_dulu"));
			return;
		}
		bayar(player, misi);
		aktif(player).removeIf(each -> each instanceof CompoundTag c && c.getLongOr("id", -1L) == id);
		EkonomiData.get(player.level().getServer()).touch();
	}

	static void batal(ServerPlayer player, long id) {
		CompoundTag misi = cari(player, id);
		if (misi == null) {
			return;
		}
		bersihkan(player, misi);
		aktif(player).removeIf(each -> each instanceof CompoundTag c && c.getLongOr("id", -1L) == id);
		EkonomiData.get(player.level().getServer()).touch();
		Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.misi.dibatalkan", judul(misi)));
	}

	private static boolean ambilHerba(ServerPlayer player, int age) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (SpiritHerbs.isHerb(stack) && SpiritHerbs.age(stack) >= age) {
				stack.shrink(1);
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}

	private static void bayar(ServerPlayer player, CompoundTag misi) {
		MinecraftServer server = player.level().getServer();
		int pangkat = Math.max(0, Math.min(5, misi.getIntOr("pangkat", 0)));
		long stones = misi.getLongOr("imbalan", 1L);
		NilaiBatu.give(player, stones);
		int rep = NAMA_BAIK[pangkat] + misi.getIntOr("bonus_nb", 0);
		long town = misi.getLongOr("town", 0L);
		int now = KotaData.get(server).addReputation(town, player.getUUID(), rep);
		int points = POIN[pangkat] + misi.getIntOr("bonus_poin", 0);
		Lencana.tambah(player, points);
		Component ware = Component.empty();
		RandomSource random = RandomSource.create(misi.getLongOr("id", 0L) * 13L ^ player.getUUID().getMostSignificantBits());
		if (random.nextDouble() < PELUANG_BARANG + (Jasa.restuKuil(player) ? Jasa.RESTU_KUIL_BONUS : 0.0D)) {
			ItemStack item = switch (random.nextInt(3)) {
				case 0 -> Harga.item("jade_slip");
				case 1 -> Harga.item("catatan_pengembara");
				default -> pangkat >= JenisMisi.B ? Harga.item("heavenvoid_fragment") : Harga.item("jade_slip");
			};
			if (!item.isEmpty()) {
				ware = Component.translatable("kultivator.tempat_ekonomi.misi.barang", item.getHoverName());
				Barang.beri(player, item);
			}
		}
		CeritaApi.peristiwa(player, "misi_gilda", misi.getStringOr("jenis", ""));
		if (dev.kultivator.sect.hall.Halls.hallOf(player.getUUID()) != null) {
			BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_MISI, 0, stones, "kultivator.tempat_ekonomi.kas.catatan.misi",
					LembarKunci.GildaMisi.SEMUA_PANGKAT.get(pangkat), misi.getStringOr("jenis", ""));
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
		Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.misi.hadiah", judul(misi), NilaiBatu.text(stones), rep, now, points, ware)
				.withStyle(ChatFormatting.GREEN));
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.misi.diserahkan", NilaiBatu.text(stones), points), true);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Text

	static Component judul(CompoundTag misi) {
		return Component.translatable("kultivator.misi." + misi.getStringOr("jenis", "") + ".judul");
	}

	static Component arah(ServerPlayer player, CompoundTag misi) {
		BlockPos to = new BlockPos(misi.getIntOr("x", 0), player.getBlockY(), misi.getIntOr("z", 0));
		int distance = (int) Math.sqrt(to.distSqr(player.blockPosition()));
		return Component.translatable("kultivator.tempat_ekonomi.misi.arah", distance, KedaiTeh.direction(player.blockPosition(), to));
	}

	/** {@code EkonomiApi.misiSelesai}: a module finished the objective of its own kind for the player. */
	static boolean selesaiDariLuar(ServerPlayer player, String jenis) {
		for (net.minecraft.nbt.Tag each : aktif(player)) {
			if (each instanceof CompoundTag misi && jenis.equals(misi.getStringOr("jenis", "")) && !misi.getBooleanOr("selesai", false)) {
				misi.putInt("tahap", misi.getIntOr("total", 1) - 1);
				tahapSelesai(player, misi);
				return true;
			}
		}
		return false;
	}

	private MisiGilda() {
	}
}
