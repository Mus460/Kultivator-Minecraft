package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.PillItem;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.JenisMisi;
import dev.kultivator.dunia.tempat.ekonomi.MisiGilda;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.util.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The mission kinds of the sect board (DESIGN 41.6.3). The eleven guild kinds of 38 ({@code buru}, {@code kemah},
 * {@code herba}, {@code kawal}, {@code binatang}, {@code sarang}, {@code begal}, {@code kafilah}, {@code gua},
 * {@code kurir}, {@code tawanan}) are offered with PK pay (never NB) and the sect's flavour, plus the eight kinds of 41
 * ({@code gabung_*}). Every kind points at a real place or a real foe: the existing rogue camps ({@code Camps}), elite
 * beasts, the site registry ({@code DuniaSites}: caves, villages, towns, bandit camps), the gates of other sects, the
 * herb garden titik of the own compound, a sky palace. Nothing changes blocks.
 * <p>
 * The kinds share the record {@link JenisMisi} of 38 (id, ranks, site kind, weight, owner {@code 41C}); they are kept in
 * this module's own table instead of {@code EkonomiApi.daftarJenisMisi}, because the guild boards of 38 offer every
 * registered kind to everyone (a herb-garden chore of a sect on a town board would be wrong).
 * <p>
 * A mission is a compound (one per board slot, copied to the player when taken): {@link #ID}, {@link #JENIS},
 * {@link #MUTU} (rank 0-5 = E-S), the target {@link #X}/{@link #Y}/{@link #Z}, the goal {@link #JUMLAH} and progress
 * {@link #KEMAJUAN}, the stage {@link #TAHAP} ({@link #TAHAP_JALAN} -> {@link #TAHAP_LAPOR} back at the board), and
 * kind extras ({@link #SEKTE_TUJUAN}, {@link #MUTU_BARANG}, {@link #TERLIHAT}, {@link #TERAKHIR}).
 */
public final class JenisMisiGabung {

	/** How a kind is fulfilled (one objective engine for all kinds). */
	public enum Tujuan {
		/** Kill {@code jumlah} hostile monsters within {@link #RADIUS_BURU} blocks of the target. */
		BUNUH_MUSUH,
		/** Clear a rogue camp opened at the target ({@code Camps.open}: the band appears when the player comes near). */
		BUNUH_KEMAH,
		/** Kill the {@code jumlah} marked elite beasts that appear at the target when the player comes within 48 blocks. */
		BUNUH_BINATANG,
		/** Reach the target (12 blocks). */
		TIBA,
		/** Reach the target and stay {@code jumlah} seconds within 16 blocks. */
		DIAM,
		/** Stay {@code jumlah} seconds within 48 blocks of the target unseen (sneaking or invisible); 5 seen seconds fail. */
		SENYAP,
		/** Bring {@code jumlah} items of the kind to the board (herbs by age, pills or artifacts by grade). */
		SERAH,
		/** Harvest the compound's herb plots ({@code kerja:cangkul}) {@code jumlah} times, a quarter day apart. */
		KEBUN
	}

	/**
	 * One kind: the 38 record, the objective, an alignment filter ({@code -1} any, {@code 0} Lurus only, {@code 2} Iblis
	 * only), the lowest sect class, the style that pays {@link #PENGALI_CORAK} more (or null, also {@code corak2}), a
	 * shared group mission, and the goal count at rank E / per rank above.
	 */
	public record Jenis(JenisMisi misi, Tujuan tujuan, int aliran, int kelasMin, @Nullable Corak corak, @Nullable Corak corak2,
			boolean kelompok, int jumlahDasar, int jumlahPerPangkat) {

		public String id() {
			return misi.id();
		}

		/** True when a sect of {@code aliranSekte} and class {@code kelas} may offer this kind. */
		public boolean bolehDi(int aliranSekte, int kelas) {
			return (aliran < 0 || aliran == aliranSekte) && kelas >= kelasMin;
		}

		public boolean corakKhas(Corak c) {
			return c != null && (c == corak || c == corak2);
		}

		/** The goal count at rank {@code mutu}. */
		public int jumlah(int mutu) {
			return Math.max(1, jumlahDasar + jumlahPerPangkat * Math.max(0, mutu - misi.pangkatMin()));
		}

		/** "Ternak Binatang Roh" / "Kill the Spirit Beast" (the kind's board title). */
		public MutableComponent judul() {
			return Component.translatable(KUNCI + id() + ".judul");
		}
	}

	public static final String PEMILIK = "41C";
	/** Lang prefix of every kind ({@code kultivator.gabung.misi.<id>.judul|teks.<aliran>|tujuan}). */
	public static final String KUNCI = "kultivator.gabung.misi.";

	// mission compound keys
	public static final String ID = "id";
	public static final String JENIS = "jenis";
	public static final String MUTU = "mutu";
	public static final String X = "x";
	public static final String Y = "y";
	public static final String Z = "z";
	public static final String JUMLAH = "jumlah";
	public static final String KEMAJUAN = "kemajuan";
	public static final String TAHAP = "tahap";
	public static final String VARIAN = "varian";
	public static final String SEKTE = "sekte";
	public static final String SEKTE_TUJUAN = "sekte_tujuan";
	public static final String MUTU_BARANG = "mutu_barang";
	public static final String TERLIHAT = "terlihat";
	public static final String TERAKHIR = "terakhir";
	public static final String BATAS = "batas";
	public static final String PK = "pk";
	public static final String PESERTA = "peserta";
	public static final String DIAMBIL = "diambil";
	public static final String TAHAP_JALAN = "jalan";
	public static final String TAHAP_LAPOR = "lapor";

	/** Reward multiplier of a kind of the sect's own style (41.6.3: ALKIMIA/TUBUH orders, BAYANGAN spying x1.3). */
	public static final double PENGALI_CORAK = 1.3D;
	/** Group missions: the reward is shared evenly plus this bonus (41.6.3 "bagi rata + 10 %"). */
	public static final double BONUS_KELOMPOK = 1.10D;
	public static final int KELOMPOK_MAKS = 4;
	public static final int KELOMPOK_MIN = 2;
	public static final double RADIUS_BURU = 64.0D;
	public static final double RADIUS_TIBA = 12.0D;
	public static final double RADIUS_DIAM = 16.0D;
	public static final double RADIUS_SENYAP = 48.0D;
	public static final double RADIUS_MUNCUL = 48.0D;
	public static final int SENYAP_GAGAL = 5;
	/** Ticks between two harvests of {@link Tujuan#KEBUN} (a quarter of a day). */
	public static final int KEBUN_JEDA = 6000;
	/** Seconds of crouching at a plot for one harvest. */
	public static final int KEBUN_DETIK = 5;
	/** Herb age per rank of {@code herba} (same as the guild, {@code MisiGilda.HERBA_UMUR}). */
	public static final int[] HERBA_UMUR = MisiGilda.HERBA_UMUR;

	private static final List<Jenis> SEMUA = new ArrayList<>();

	static {
		// the 38 kinds (38.M.3 ranks), fulfilled by this engine with PK pay
		tambah("buru", JenisMisi.E, JenisMisi.C, null, 14, Tujuan.BUNUH_MUSUH, -1, 1, Corak.PEDANG, Corak.TUBUH, false, 5, 2);
		tambah("kemah", JenisMisi.D, JenisMisi.B, null, 10, Tujuan.BUNUH_KEMAH, -1, 1, null, null, false, 1, 0);
		tambah("herba", JenisMisi.E, JenisMisi.B, null, 10, Tujuan.SERAH, -1, 1, Corak.ALKIMIA, Corak.RACUN, false, 3, 1);
		tambah("kawal", JenisMisi.E, JenisMisi.C, "kota", 6, Tujuan.TIBA, -1, 1, null, null, false, 1, 0);
		tambah("binatang", JenisMisi.C, JenisMisi.S, "gua/sarang_binatang", 10, Tujuan.BUNUH_BINATANG, -1, 1, Corak.BINATANG, null,
				false, 1, 0);
		tambah("sarang", JenisMisi.B, JenisMisi.S, "gua/sarang_iblis", 8, Tujuan.BUNUH_BINATANG, -1, 1, null, null, false, 3, 1);
		tambah("begal", JenisMisi.D, JenisMisi.A, "pos/kemah_begal", 12, Tujuan.BUNUH_KEMAH, -1, 1, null, null, false, 1, 0);
		tambah("kafilah", JenisMisi.D, JenisMisi.B, null, 8, Tujuan.DIAM, -1, 1, null, null, false, 60, 15);
		tambah("gua", JenisMisi.C, JenisMisi.A, "gua/", 8, Tujuan.DIAM, -1, 1, null, null, false, 30, 10);
		tambah("kurir", JenisMisi.E, JenisMisi.C, "kampung/", 10, Tujuan.TIBA, -1, 1, null, null, false, 1, 0);
		tambah("tawanan", JenisMisi.C, JenisMisi.A, null, 6, Tujuan.BUNUH_KEMAH, -1, 1, null, null, false, 1, 0);
		// the kinds of 41 (41.6.3 table)
		tambah("gabung_kebun", JenisMisi.E, JenisMisi.E, null, 12, Tujuan.KEBUN, -1, 1, Corak.ALKIMIA, null, false, 3, 0);
		tambah("gabung_antar", JenisMisi.E, JenisMisi.D, null, 12, Tujuan.TIBA, -1, 1, null, null, false, 1, 0);
		tambah("gabung_pesanan", JenisMisi.D, JenisMisi.B, null, 10, Tujuan.SERAH, -1, 1, Corak.ALKIMIA, Corak.TUBUH, false, 2, 1);
		tambah("gabung_buru_iblis", JenisMisi.C, JenisMisi.A, null, 9, Tujuan.BUNUH_KEMAH, 0, 1, null, null, false, 1, 0);
		tambah("gabung_culik", JenisMisi.C, JenisMisi.A, null, 9, Tujuan.BUNUH_KEMAH, 2, 1, null, null, false, 1, 0);
		tambah("gabung_mata_mata", JenisMisi.B, JenisMisi.A, null, 6, Tujuan.SENYAP, -1, 1, Corak.BAYANGAN, null, false, 20, 10);
		tambah("gabung_kelompok", JenisMisi.B, JenisMisi.S, "gua/sarang_binatang", 6, Tujuan.BUNUH_BINATANG, -1, 1, null, null, true, 1,
				0);
		tambah("gabung_langit", JenisMisi.A, JenisMisi.S, null, 5, Tujuan.DIAM, -1, KelasSekte.AGUNG.nomor(), null, null, false, 120, 0);
	}

	private static void tambah(String id, int min, int maks, @Nullable String situs, int bobot, Tujuan tujuan, int aliran, int kelasMin,
			@Nullable Corak corak, @Nullable Corak corak2, boolean kelompok, int jumlah, int perPangkat) {
		SEMUA.add(new Jenis(new JenisMisi(id, min, maks, situs, bobot, PEMILIK), tujuan, aliran, kelasMin, corak, corak2, kelompok, jumlah,
				perPangkat));
	}

	/** Every kind, in table order. */
	public static List<Jenis> semua() {
		return List.copyOf(SEMUA);
	}

	public static @Nullable Jenis byId(@Nullable String id) {
		for (Jenis j : SEMUA) {
			if (j.id().equals(id)) {
				return j;
			}
		}
		return null;
	}

	// ================================================================== rewards (41.6.2)

	/**
	 * {@code dasarPK(pita)}: half the guild's NB base of 38.M.3 (band 0-1: 3-6, 2: 8-15, 3: 20-40, 4: 40-80, 5: 80-160,
	 * 6+: 160-320), rolled with {@code random}.
	 */
	public static int dasarPk(int pita, RandomSource random) {
		int low;
		int high;
		if (pita <= 1) {
			low = 3;
			high = 6;
		} else if (pita == 2) {
			low = 8;
			high = 15;
		} else {
			low = 20;
			high = 40;
			for (int b = 4; b <= Math.min(pita, 6); b++) {
				low *= 2;
				high *= 2;
			}
		}
		return low + random.nextInt(high - low + 1);
	}

	/**
	 * The PK one mission pays {@code player} at sect {@code sekte}: {@link #dasarPk} x the rank factor of 38
	 * ({@code MisiGilda.PENGALI}) x the class factor {@code kf} x {@link #PENGALI_CORAK} for a kind of the sect's style.
	 * Seeded by the mission and the player, so the board preview and the payout agree.
	 */
	public static int imbalan(ServerPlayer player, String sekte, CompoundTag misi) {
		Jenis jenis = byId(misi.getStringOr(JENIS, ""));
		int mutu = Mth.clamp(misi.getIntOr(MUTU, 0), 0, 5);
		RandomSource random = RandomSource.create(misi.getStringOr(ID, "").hashCode() * 0x2545F4914F6CDD1DL
				^ player.getUUID().getLeastSignificantBits());
		double pk = dasarPk(Barang.pita(player), random) * MisiGilda.PENGALI[mutu] * GabungApi.kelasSekte(sekte).kf();
		if (jenis != null && jenis.corakKhas(GabungApi.corak(sekte))) {
			pk *= PENGALI_CORAK;
		}
		return Math.max(1, (int) Math.round(pk));
	}

	// ================================================================== drawing a board slot

	/** The highest mission rank a member of {@code pangkat} may take (41.6.1 rights): Pelayan/Tercatat E, Luar D, Dalam B, Inti A. */
	public static int pangkatMaks(Pangkat pangkat) {
		return switch (pangkat) {
			case PELAYAN, TERCATAT -> JenisMisi.E;
			case LUAR -> JenisMisi.D;
			case DALAM -> JenisMisi.B;
			case INTI -> JenisMisi.A;
			default -> JenisMisi.S;
		};
	}

	/** The kinds sect {@code sekte} offers with top rank {@code maks} (alignment, class, sky palace). */
	public static List<Jenis> untukSekte(MinecraftServer server, String sekte, int maks) {
		int aliran = SekteApi.diplomasi().aliran(sekte);
		int kelas = GabungApi.kelas(sekte);
		boolean langit = istana(server, sekte) != null;
		List<Jenis> out = new ArrayList<>();
		for (Jenis j : SEMUA) {
			if (j.misi().pangkatMin() <= maks && j.bolehDi(aliran, kelas) && (!"gabung_langit".equals(j.id()) || langit)) {
				out.add(j);
			}
		}
		return out;
	}

	/**
	 * Draws mission {@code slot} of the board of {@code sekte} for {@code hari}: kind (weighted), rank within the kind and
	 * {@code maks}, the target place. Null when nothing fits. {@code wajibE} forces an E kind (the first slot, so a servant
	 * always finds work).
	 */
	public static @Nullable CompoundTag buat(MinecraftServer server, String sekte, long hari, int slot, int maks, boolean wajibE,
			RandomSource random) {
		List<Jenis> kinds = untukSekte(server, sekte, maks);
		if (wajibE) {
			kinds.removeIf(j -> j.misi().pangkatMin() != JenisMisi.E);
		}
		int total = 0;
		for (Jenis j : kinds) {
			total += j.misi().bobot();
		}
		if (total <= 0) {
			return null;
		}
		int pick = random.nextInt(total);
		Jenis chosen = kinds.getFirst();
		for (Jenis j : kinds) {
			pick -= j.misi().bobot();
			if (pick < 0) {
				chosen = j;
				break;
			}
		}
		int high = wajibE ? JenisMisi.E : Math.min(chosen.misi().pangkatMaks(), maks);
		int mutu = chosen.misi().pangkatMin() + random.nextInt(Math.max(1, high - chosen.misi().pangkatMin() + 1));
		CompoundTag m = new CompoundTag();
		m.putString(ID, sekte + "." + hari + "." + slot);
		m.putString(JENIS, chosen.id());
		m.putString(SEKTE, sekte);
		m.putInt(MUTU, mutu);
		m.putInt(JUMLAH, chosen.jumlah(mutu));
		m.putInt(KEMAJUAN, 0);
		m.putString(TAHAP, TAHAP_JALAN);
		m.putInt(VARIAN, random.nextInt(2));
		if (!sasaran(server, sekte, chosen, mutu, m, random)) {
			return null;
		}
		return m;
	}

	/** Picks the real target of the mission into {@code m}; false when the kind has no target in this world. */
	private static boolean sasaran(MinecraftServer server, String sekte, Jenis jenis, int mutu, CompoundTag m, RandomSource random) {
		ServerLevel level = server.overworld();
		BlockPos pusat = pusat(sekte);
		if (pusat == null) {
			return false;
		}
		BlockPos target;
		switch (jenis.id()) {
			case "gabung_kebun", "herba", "gabung_pesanan" -> {
				BlockPos papan = GabungApi.titik(sekte, dev.kultivator.sekte.gabung.dunia.TitikGabung.PAPAN_MISI);
				target = papan != null ? papan : pusat;
				if (!"gabung_kebun".equals(jenis.id())) {
					m.putInt(MUTU_BARANG, mutuBarang(jenis.id(), mutu, GabungApi.corak(sekte)));
				}
			}
			case "gabung_antar" -> {
				String sekutu = sekteLain(server, sekte, pusat, true);
				BlockPos gate = sekutu == null ? null : gerbang(sekutu);
				if (gate != null) {
					m.putString(SEKTE_TUJUAN, sekutu);
					target = gate;
				} else {
					target = situs(level, pusat, "kota", 2400);
				}
			}
			case "gabung_mata_mata" -> {
				String musuh = sekteLain(server, sekte, pusat, false);
				target = musuh == null ? null : gerbang(musuh);
				if (musuh != null) {
					m.putString(SEKTE_TUJUAN, musuh);
				}
			}
			case "gabung_langit" -> {
				LangitNpcPlan.Istana istana = istana(server, sekte);
				if (istana == null) {
					return false;
				}
				BlockPos col = istana.column();
				target = new BlockPos(col.getX(), (LangitNpcPlan.Y_MIN + LangitNpcPlan.Y_MAX) / 2, col.getZ());
			}
			default -> {
				BlockPos acak = titikAcak(pusat, mutu, random);
				String kind = jenis.misi().jenisSitus();
				BlockPos nyata = kind == null ? null : situs(level, acak, kind, 1600);
				target = nyata != null ? nyata : acak;
			}
		}
		if (target == null) {
			return false;
		}
		m.putInt(X, target.getX());
		m.putInt(Y, target.getY());
		m.putInt(Z, target.getZ());
		return true;
	}

	/** The sect's gate (built) or its planned column; the centre missions are measured from. */
	public static @Nullable BlockPos pusat(String sekte) {
		return gerbang(sekte);
	}

	private static @Nullable BlockPos gerbang(String sekte) {
		BlockPos gate = SekteApi.diplomasi().gate(sekte);
		return gate != null ? gate : SekteApi.diplomasi().plannedGate(sekte);
	}

	/** A point in the wild 150-560 blocks from the gate (further for higher ranks). */
	private static BlockPos titikAcak(BlockPos pusat, int mutu, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double distance = 150.0D + mutu * 70.0D + random.nextDouble() * 160.0D;
		return new BlockPos(Mth.floor(pusat.getX() + Math.cos(angle) * distance), 64, Mth.floor(pusat.getZ() + Math.sin(angle) * distance));
	}

	private static @Nullable BlockPos situs(ServerLevel level, BlockPos near, String kind, int radius) {
		DuniaSites.Site best = null;
		double bestSq = Double.MAX_VALUE;
		for (DuniaSites.Site site : DuniaSites.near(level, near, radius, kind)) {
			double sq = site.pos().distSqr(near);
			if (sq < bestSq) {
				bestSq = sq;
				best = site;
			}
		}
		return best == null ? null : best.pos();
	}

	/**
	 * The nearest other sect: {@code sekutu} = one of the same alignment (an errand between friends), otherwise one of a
	 * hostile alignment (Lurus against Iblis; a neutral sect spies on the nearest demonic one, a demonic sect on the nearest
	 * righteous one). Null when there is none.
	 */
	public static @Nullable String sekteLain(MinecraftServer server, String sekte, BlockPos pusat, boolean sekutu) {
		int own = SekteApi.diplomasi().aliran(sekte);
		int want = sekutu ? own : own == 2 ? 0 : 2;
		String best = null;
		double bestSq = Double.MAX_VALUE;
		for (String other : SekteApi.diplomasi().sects()) {
			if (other.equals(sekte) || SekteApi.diplomasi().aliran(other) != want) {
				continue;
			}
			BlockPos g = gerbang(other);
			if (g == null) {
				continue;
			}
			double sq = g.distSqr(pusat);
			if (sq < bestSq) {
				bestSq = sq;
				best = other;
			}
		}
		return best;
	}

	/** The sky palace of {@code sekte} in this world's plan (37, read only), or null. */
	public static LangitNpcPlan.@Nullable Istana istana(MinecraftServer server, String sekte) {
		for (LangitNpcPlan.Istana istana : LangitNpcPlan.of(server.overworld().getSeed())) {
			if (sekte.equals(LangitNpcPlan.sectId(istana))) {
				return istana;
			}
		}
		return null;
	}

	/**
	 * The grade an item must have for a hand-in mission: {@code herba} the herb age of the rank; {@code gabung_pesanan} a
	 * pill quality ordinal (D Low, C Mid, B High) or, in a TUBUH sect, an artifact grade (D 0, C 1, B 2).
	 */
	private static int mutuBarang(String jenis, int mutu, Corak corak) {
		if ("herba".equals(jenis)) {
			return HERBA_UMUR[Mth.clamp(mutu, 0, HERBA_UMUR.length - 1)];
		}
		return Math.max(0, mutu - JenisMisi.D);
	}

	/** True when the order of a TUBUH sect asks for forged artifacts instead of pills. */
	public static boolean pesananPusaka(String sekte) {
		return GabungApi.corak(sekte) == Corak.TUBUH;
	}

	/** True when {@code stack} counts for the hand-in mission {@code misi}. */
	public static boolean cocokSerah(CompoundTag misi, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		int need = misi.getIntOr(MUTU_BARANG, 0);
		return switch (misi.getStringOr(JENIS, "")) {
			case "herba" -> SpiritHerbs.isHerb(stack) && SpiritHerbs.age(stack) >= need;
			case "gabung_pesanan" -> pesananPusaka(misi.getStringOr(SEKTE, ""))
					? ArtifactItem.is(stack) && ArtifactItem.grade(ArtifactItem.data(stack)) >= need
					: stack.getItem() instanceof PillItem && AlchemyComponents.quality(stack).ordinal() >= need;
			default -> false;
		};
	}

	// ================================================================== elite beasts of BUNUH_BINATANG

	/** The entity tag of the beasts of mission {@code id} (the death event counts tagged kills). */
	public static String tagBinatang(String id) {
		return "kultivator.gabung.misi." + Integer.toHexString(id.hashCode());
	}

	/**
	 * Spawns the beasts still missing for mission {@code misi} around its target (beasts already alive with the tag are
	 * reused, so a restart never doubles them). Within the 41 entity budget ({@code DuniaBudget} kind {@code gabung}).
	 * Returns how many beasts with the tag are alive afterwards.
	 */
	public static int munculkanBinatang(ServerLevel level, ServerPlayer player, CompoundTag misi) {
		String tag = tagBinatang(misi.getStringOr(ID, ""));
		int x = misi.getIntOr(X, 0);
		int z = misi.getIntOr(Z, 0);
		AABB box = new AABB(x - 48, level.getMinY(), z - 48, x + 48, level.getMaxY(), z + 48);
		int alive = level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && mob.entityTags().contains(tag)).size();
		int need = misi.getIntOr(JUMLAH, 1) - misi.getIntOr(KEMAJUAN, 0) - alive;
		int mutu = misi.getIntOr(MUTU, 0);
		boolean induk = !"sarang".equals(misi.getStringOr(JENIS, ""));
		RandomSource random = level.getRandom();
		for (int i = 0; i < need; i++) {
			if (!DuniaBudget.mayAdd(level, "gabung")) {
				break;
			}
			EntityType<? extends Mob> type = induk
					? (mutu >= JenisMisi.A ? EntityTypes.RAVAGER : mutu >= JenisMisi.B ? EntityTypes.HOGLIN : EntityTypes.POLAR_BEAR)
					: switch (random.nextInt(4)) {
						case 0 -> EntityTypes.CAVE_SPIDER;
						case 1 -> EntityTypes.SPIDER;
						case 2 -> EntityTypes.BOGGED;
						default -> EntityTypes.HUSK;
					};
			Mob mob = spawn(level, player, type, x, z, random);
			if (mob == null) {
				break;
			}
			mob.addTag(tag);
			DuniaBudget.track("gabung", mob);
			int tier = Mth.clamp(1 + mutu / 2 + ("gabung_kelompok".equals(misi.getStringOr(JENIS, "")) ? 1 : 0), 1, 3);
			EliteMonsters.makeElite(mob, tier, null, Math.max(1, Barang.pita(player)));
			dev.kultivator.technique.spec.TechniquePower.markRealmFoe(mob);
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 2400, 0));
			alive++;
		}
		return alive;
	}

	private static @Nullable Mob spawn(ServerLevel level, ServerPlayer player, EntityType<? extends Mob> type, int cx, int cz,
			RandomSource random) {
		for (int attempt = 0; attempt < 6; attempt++) {
			int x = cx + random.nextInt(11) - 5;
			int z = cz + random.nextInt(11) - 5;
			if (!level.isPositionEntityTicking(new BlockPos(x, 0, z)) || DuniaApi.safeGround(level, new BlockPos(x, player.getBlockY(), z))) {
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
			if (level.addFreshEntity(mob)) {
				return mob;
			}
			return null;
		}
		return null;
	}

	// ================================================================== text

	/** The board title of a mission ("[C] Kemah Kultivator Liar"). */
	public static MutableComponent nama(CompoundTag misi) {
		Jenis jenis = byId(misi.getStringOr(JENIS, ""));
		MutableComponent title = jenis == null ? Component.literal(misi.getStringOr(JENIS, "?")) : jenis.judul();
		return title;
	}

	/**
	 * The two or three lines of flavour in the sect's alignment voice, with the target sect's name when the kind has one
	 * ({@code kultivator.gabung.misi.<id>.teks.<lurus|netral|iblis>.<varian>}).
	 */
	public static MutableComponent teks(CompoundTag misi) {
		String sekte = misi.getStringOr(SEKTE, "");
		int aliran = Math.max(0, SekteApi.diplomasi().aliran(sekte));
		String suffix = switch (aliran) {
			case 2 -> "iblis";
			case 1 -> "netral";
			default -> "lurus";
		};
		String tujuan = misi.getStringOr(SEKTE_TUJUAN, "");
		Component target = tujuan.isEmpty() ? Component.translatable("kultivator.gabung.misi.tempat_jauh") : namaSekte(tujuan);
		return Component.translatable(KUNCI + misi.getStringOr(JENIS, "") + ".teks." + suffix + "." + Mth.clamp(misi.getIntOr(VARIAN, 0), 0, 1),
				target, misi.getIntOr(JUMLAH, 1), misi.getIntOr(MUTU_BARANG, 0));
	}

	/** The goal line with progress ("Kalahkan 3/7 siluman di sekitar tanda"). */
	public static MutableComponent tujuanBaris(CompoundTag misi) {
		String jenis = misi.getStringOr(JENIS, "");
		if (TAHAP_LAPOR.equals(misi.getStringOr(TAHAP, ""))) {
			return Component.translatable(KUNCI + "lapor");
		}
		Jenis j = byId(jenis);
		String key = j == null ? KUNCI + "tujuan.tiba" : KUNCI + "tujuan." + j.tujuan().name().toLowerCase(Locale.ROOT);
		return Component.translatable(key, misi.getIntOr(KEMAJUAN, 0), misi.getIntOr(JUMLAH, 1), misi.getIntOr(MUTU_BARANG, 0));
	}

	/** The rank letter E..S. */
	public static String huruf(int mutu) {
		return String.valueOf(JenisMisi.HURUF.charAt(Mth.clamp(mutu, 0, 5)));
	}

	static Component namaSekte(String sekte) {
		Component name = SekteApi.diplomasi().name(sekte);
		return name == null ? Component.literal(sekte) : name;
	}

	/** Direction and distance from {@code player} to the target ("barat laut, 340 blok"). */
	public static MutableComponent arah(ServerPlayer player, CompoundTag misi) {
		double dx = misi.getIntOr(X, 0) + 0.5D - player.getX();
		double dz = misi.getIntOr(Z, 0) + 0.5D - player.getZ();
		int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		// 0 = north, clockwise in 8 sectors (Minecraft north = -Z)
		double deg = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = Math.floorMod((int) Math.round(deg / 45.0D), 8);
		return Component.translatable("kultivator.gabung.misi.arah", Component.translatable("kultivator.gabung.arah." + sector), dist);
	}

	/** The planned entry of {@code sekte} (index, words), for seeds. */
	static @Nullable GerbangPlan.Entry entry(MinecraftServer server, String sekte) {
		return GabungApi.entry(server, sekte);
	}

	private JenisMisiGabung() {
	}
}
