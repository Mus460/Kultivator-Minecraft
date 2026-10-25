package dev.kultivator.sekte.diplomasi;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import dev.kultivator.sekte.gabung.politik.Politik;
import dev.kultivator.sekte.gabung.politik.TetuaTamu;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The bridge of DESIGN 41 (Gabung Sekte, contract C41.D1, owner 41D) into module D, in D's package so it may use D's
 * package-private state (the pattern of {@link DiplomasiMandiri}, 39F). It is the only 41 file in {@code sekte.diplomasi};
 * the old D files get one line each from 41D: {@code SekteNpc.founded} ({@code s.tier = tingkatAwal(e)}), {@code Aksi.run}
 * ({@link #vetoKirim}) and {@code Simulasi.grow} ({@link #bolehTumbuh} in the tier-up test).
 * <ul>
 *   <li>{@link #tingkatAwal}: the starting tier by the {@code gabungSekte.sebaran} preset (41.4.2). The default
 *   {@code seimbang} gives t8/t7 to exactly the two sects of {@code LangitNpcPlan.of(seed)}: both orders come from
 *   {@code KelasSekte.urutan}, the palace comparator.</li>
 *   <li>{@link #bolehTumbuh}: growth stops at {@code gabungSekte.tingkatMaks} and while MEROSOT.</li>
 *   <li>{@link #ubahSumber}: 41's bounded factors of 41.4.7 (treasury donations at most 20 a day, board missions A/S at
 *   most 10 a day, wars +-40, raids, tournaments); a negative {@code sumber} costs a tier ({@code sumber = 75 x tier}).</li>
 *   <li>The NPC war of 41.6.10 ({@link #perangMulai}, {@link #perangSkor}, {@link #perangAkhir}): D's feud lasts ten days
 *   or until a score of +-100; the War Call and the missions (41D-P) listen to {@code GabungBus.PERANG_MULAI}. Every war
 *   end moves 40 resources from loser to winner; a tier-1 sect that loses twice in 30 days goes MEROSOT (K4) until
 *   {@link #bangkit}.</li>
 *   <li>{@code GabungBus.KELAS_BERUBAH}: fired whenever a tier change crosses a class boundary, whoever changed the tier
 *   (D's growth and feuds included: {@link #cekKelas} runs in the daily pass of {@code Politik}).</li>
 *   <li>{@link #veto}, {@link #paktaCabang}, {@link #setLebur}: membership diplomacy, branch pact, absorb (41.8.2-41.8.6).</li>
 *   <li>{@link #setStanding}: the exact standing writes of 41.7 (the {@code KELUAR} listener: a forced leave or an
 *   expulsion leaves at most -30, a defector at most -60).</li>
 * </ul>
 * Server thread only. Every write is a no-op while the module is off, except {@link #tingkatAwal} (then {@code alami}).
 */
public final class DiplomasiGabung {

	/** Module key of every 41 compound in {@code DataSekteGabung.bagian}. */
	public static final String BAGIAN = "politik";

	/** Days an NPC war lasts (41.6.10) and the score that ends it early. */
	public static final int PERANG_HARI = 10;
	public static final int PERANG_SKOR = 100;
	/** Resources won by the winner of an NPC war and lost by the loser (41.4.7). */
	public static final int SUMBER_PERANG = 40;
	/** Daily caps per sect: donations to the treasury and board missions A/S (41.4.7). */
	public static final int CAP_KAS = 20;
	public static final int CAP_MISI = 10;
	/** MEROSOT: two lost wars within 30 days at tier 1; Bangkit gives +150 at most once per 60 days. */
	public static final int MEROSOT_KALAH = 2;
	public static final int MEROSOT_JENDELA = 30;
	public static final int BANGKIT_SUMBER = 150;
	public static final int BANGKIT_JEDA = 60;
	/** A player master may lead the sect into a war once per 30 days and act toward other sects once a day (41.8.5). */
	public static final int JEDA_PERANG_KETUA = 30;

	/** Reasons of {@link #ubahSumber} with a daily cap or a special rule. */
	public static final String SEBAB_KAS = "kas";
	public static final String SEBAB_MISI = "misi";
	public static final String SEBAB_PERANG = "perang";
	public static final String SEBAB_SERBUAN = "serbuan";
	public static final String SEBAB_TURNAMEN = "turnamen";
	public static final String SEBAB_UPETI = "upeti";
	public static final String SEBAB_GOYAH = "goyah";
	public static final String SEBAB_BANGKIT = "bangkit";

	private static final String KEY = "kultivator.gabung.politik.";
	private static boolean listening;

	/** A running NPC war seen from one side: the enemy, the score (positive = winning), start and end day. */
	public record Perang(String lawan, int skor, long mulai, long sampai) {

		/** World days left on day {@code hari}. */
		public long sisa(long hari) {
			return Math.max(0L, sampai - hari);
		}
	}

	// ================================================================== founding and growth (41.4.2)

	/**
	 * The starting tier of a sect founded from plan entry {@code e} (1-9): the {@code gabungSekte.sebaran} preset over the
	 * world plan (the plan the live server's seed gives, which is the plan {@code Simulasi.ensureFounded} founds from), capped
	 * at {@code tingkatMaks}. The plan tier ({@code alami}) while the module is off or no server runs.
	 */
	public static int tingkatAwal(GerbangPlan.Entry e) {
		MinecraftServer server = Diplomasi.server();
		if (server == null || !Gabung.aktif()) {
			return clamp(e.tier());
		}
		GabungConfig c = GabungConfig.get();
		GerbangPlan.Plan plan = GerbangPlan.of(server.overworld().getSeed());
		return Math.min(clamp(c.tingkatMaks), tingkatPreset(plan, e, c.sebaran, c.tingkatDatar));
	}

	/**
	 * The tier preset {@code preset} gives entry {@code e} of {@code plan} (pure; {@code uji gabung sebaran} prints it):
	 * <ul>
	 *   <li>{@code seimbang}: rank 0 -> 8, rank 1 -> 7, {@code q < 0.40} -> 5-6, {@code q < 0.70} -> 3-4, the rest 1-2, where
	 *   {@code q = rank / n}. Inside one band the stronger half takes the upper tier; an odd middle one is rolled with
	 *   {@code new Random(seed ^ 0x6AB041 ^ index)}, so eight sects read 8, 7, 6, 5, 4, 3, 2, 1.</li>
	 *   <li>{@code alami}: the plan tier (1-5); {@code agung}: {@code seimbang} + 1; {@code kecil}: {@code alami} - 1, rank 0
	 *   at most 4; {@code datar}: {@code tingkatDatar} for all.</li>
	 * </ul>
	 */
	public static int tingkatPreset(GerbangPlan.Plan plan, GerbangPlan.Entry e, String preset, int datar) {
		return switch (preset == null ? "" : preset) {
			case "alami" -> clamp(e.tier());
			case "agung" -> clamp(seimbang(plan, e) + 1);
			case "kecil" -> {
				int t = Math.max(1, e.tier() - 1);
				yield peringkatRencana(plan, e) == 0 ? Math.min(4, t) : t;
			}
			case "datar" -> clamp(datar);
			default -> seimbang(plan, e);
		};
	}

	/** The strength rank (0 = strongest) of entry {@code e} in its plan, by the palace comparator. */
	public static int peringkatRencana(GerbangPlan.Plan plan, GerbangPlan.Entry e) {
		List<GerbangPlan.Entry> order = KelasSekte.urutan(plan);
		for (int r = 0; r < order.size(); r++) {
			if (order.get(r).index() == e.index()) {
				return r;
			}
		}
		return order.size();
	}

	private static int seimbang(GerbangPlan.Plan plan, GerbangPlan.Entry e) {
		List<GerbangPlan.Entry> order = KelasSekte.urutan(plan);
		int n = Math.max(1, order.size());
		int r = peringkatRencana(plan, e);
		if (r == 0) {
			return 8;
		}
		if (r == 1) {
			return 7;
		}
		int band = band(r, n);
		// position of r inside its band and the band's size
		int first = -1;
		int size = 0;
		for (int i = 2; i < n; i++) {
			if (band(i, n) == band) {
				if (first < 0) {
					first = i;
				}
				size++;
			}
		}
		int j = r - first;
		int hi = switch (band) {
			case 0 -> 6;
			case 1 -> 4;
			default -> 2;
		};
		boolean upper;
		if (size % 2 == 1 && j == size / 2) {
			upper = new Random(plan.seed() ^ 0x6A_B0_41L ^ e.index()).nextBoolean();
		} else {
			upper = j < size / 2;
		}
		return upper ? hi : hi - 1;
	}

	private static int band(int r, int n) {
		double q = (double) r / n;
		return q < 0.40D ? 0 : q < 0.70D ? 1 : 2;
	}

	/** False when {@code sect} may not climb another tier now ({@code tingkatMaks}, MEROSOT). */
	public static boolean bolehTumbuh(SekteNpc sect) {
		if (sect == null || !Gabung.aktif()) {
			return true;
		}
		if (sect.tier >= clamp(GabungConfig.get().tingkatMaks)) {
			return false;
		}
		MinecraftServer server = Diplomasi.server();
		GabungData.DataSekteGabung d = server == null ? null : GabungData.get(server).peekSekte(sect.id);
		return d == null || !d.merosot;
	}

	// ================================================================== resources and tier (41.4.7)

	/**
	 * Adds {@code n} (may be negative) to the sect's resource {@code sumber} for {@code sebab} ({@link #SEBAB_KAS} and
	 * {@link #SEBAB_MISI} have daily caps, the rest none). A negative total costs a tier ({@code sumber = 75 x tier}, never
	 * below tier 1) and a class change fires {@code KELAS_BERUBAH}. Returns nothing; use {@link #sumber} to read.
	 */
	public static void ubahSumber(MinecraftServer server, String sekte, int n, String sebab) {
		tambahSumber(server, sekte, n, sebab);
	}

	/** {@link #ubahSumber} returning the amount actually applied after the daily caps. */
	public static int tambahSumber(MinecraftServer server, String sekte, int n, String sebab) {
		DiplomasiData data = server == null || !Gabung.aktif() ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null || n == 0) {
			return 0;
		}
		int applied = n;
		if (n > 0 && (SEBAB_KAS.equals(sebab) || SEBAB_MISI.equals(sebab))) {
			CompoundTag b = bagian(server, sekte);
			long hari = Diplomasi.today(server);
			if (b.getLongOr("capHari", -1L) != hari) {
				b.putLong("capHari", hari);
				b.putInt("capKas", 0);
				b.putInt("capMisi", 0);
			}
			String key = SEBAB_KAS.equals(sebab) ? "capKas" : "capMisi";
			int cap = SEBAB_KAS.equals(sebab) ? CAP_KAS : CAP_MISI;
			int used = b.getIntOr(key, 0);
			applied = Math.max(0, Math.min(n, cap - used));
			b.putInt(key, used + applied);
			GabungData.get(server).setDirty();
		}
		if (applied == 0) {
			return 0;
		}
		cekKelas(server, sekte);
		sect.sumber += applied;
		jatuhTier(server, data, sect);
		data.setDirty();
		cekKelas(server, sekte);
		return applied;
	}

	/** {@code sumber < 0}: one tier lower with {@code sumber = 75 x tier}; at tier 1 the resource rests at 0. */
	private static void jatuhTier(MinecraftServer server, DiplomasiData data, SekteNpc sect) {
		if (sect.sumber >= 0) {
			return;
		}
		if (sect.tier > 1) {
			sect.tier--;
			sect.sumber = 75 * sect.tier;
			data.addNews(Diplomasi.today(server), Component.translatable(GeneratorNama.KEY + "berita.turun", sect.name(), sect.tier));
		} else {
			sect.sumber = 0;
		}
	}

	/** Sets the sect's tier (clamped 1-9); a class change fires {@code KELAS_BERUBAH}. */
	public static void setTier(MinecraftServer server, String sekte, int tier) {
		DiplomasiData data = server == null ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null) {
			return;
		}
		int next = clamp(tier);
		cekKelas(server, sekte);
		if (sect.tier != next) {
			sect.tier = next;
			sect.sumber = Math.max(0, sect.sumber);
			data.setDirty();
		}
		cekKelas(server, sekte);
	}

	/**
	 * Compares the sect's class with the one last seen (saved in its {@code politik} compound) and, when it changed, fires
	 * {@code GabungBus.KELAS_BERUBAH}, writes D's news and a world rumour ({@code gabung.naik_kelas}/{@code gabung.turun_kelas}),
	 * tells the members and resends the class sync to every online player. The first look only records the class.
	 */
	public static void cekKelas(MinecraftServer server, String sekte) {
		DiplomasiData data = server == null ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null || !Gabung.aktif()) {
			return;
		}
		CompoundTag b = bagian(server, sekte);
		int baru = KelasSekte.of(sect.tier);
		int lama = b.getIntOr("kelas", 0);
		if (lama == baru) {
			return;
		}
		b.putInt("kelas", baru);
		GabungData.get(server).setDirty();
		if (lama == 0) {
			return;
		}
		boolean naik = baru > lama;
		long hari = Diplomasi.today(server);
		KelasSekte kelas = KelasSekte.byNomor(baru);
		data.addNews(hari, Component.translatable(KEY + (naik ? "berita.naik_kelas" : "berita.turun_kelas"), sect.name(), kelas.nama()));
		Politik.kabar(server, naik ? "gabung.naik_kelas" : "gabung.turun_kelas", sect.name(), kelas.nama());
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + (naik ? "pesan.naik_kelas" : "pesan.turun_kelas"), sect.name(),
				kelas.nama(), kelas.gelar()), naik ? ChatFormatting.GOLD : ChatFormatting.RED);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			GabungPayloads.kirimKelas(player);
		}
		GabungBus.KELAS_BERUBAH.invoker().onKelasBerubah(server, sekte, lama, baru);
	}

	// ================================================================== reads

	/** The sect's resource {@code sumber} (0 for an unknown sect). */
	public static int sumber(MinecraftServer server, String sekte) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		return sect == null ? 0 : sect.sumber;
	}

	/** {@code tier x 100 + sumber} (0 for an unknown sect). */
	public static int kekuatan(MinecraftServer server, String sekte) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		return sect == null ? 0 : sect.strength();
	}

	/** Resources the sect needs for its next tier ({@code 150 x tier}). */
	public static int sumberNaik(MinecraftServer server, String sekte) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		return sect == null ? 0 : sect.nextTierCost();
	}

	/** Every sect id of the world, in index order. */
	public static List<String> sekte(MinecraftServer server) {
		List<String> out = new ArrayList<>();
		if (server != null) {
			for (SekteNpc s : DiplomasiData.get(server).sects) {
				out.add(s.id);
			}
		}
		return out;
	}

	/** The sect's name ({@code "?"} literal for an unknown id). */
	public static Component nama(MinecraftServer server, String sekte) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		return sect == null ? Component.literal(sekte == null ? "?" : sekte) : sect.name();
	}

	/** The standing of {@code owner} (a player UUID) with the sect (alignment default when never met). */
	public static int standing(MinecraftServer server, UUID owner, String sekte) {
		DiplomasiData data = server == null || owner == null ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null) {
			return 0;
		}
		Relasi r = data.peek(owner, sect.id);
		return r == null ? Standing.awal(sect) : r.standing;
	}

	// ================================================================== raw writes

	/**
	 * Sets the standing of {@code owner} (a player UUID, hall or not) with the sect to {@code nilai} (clamped -100..100),
	 * bypassing the daily +-20 clamp of {@code DiplomasiService.adjust} (41.7.3: a defector's -60 at once). The chronicle
	 * gets the line {@code kultivator.gabung.politik.kronik.standing}.
	 */
	public static void setStanding(MinecraftServer server, UUID owner, String sekte, int nilai) {
		DiplomasiData data = server == null || owner == null ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null) {
			return;
		}
		Relasi r = data.relasi(owner, sect);
		int target = Math.max(-100, Math.min(100, nilai));
		if (r.standing() != target) {
			int delta = target - r.standing();
			r.add(delta);
			r.note(Diplomasi.today(server), Component.translatable(KEY + "kronik.standing", (delta > 0 ? "+" : "") + delta, r.standing));
			if (r.pakta == Relasi.PAKTA_SEKUTU && r.standing < Standing.SEKUTU_PUTUS) {
				r.pakta = Relasi.PAKTA_DAMAI;
			}
			data.setDirty();
		}
	}

	// ================================================================== a player master acts for the sect (41.8.5)

	/** {@link #apply(MinecraftServer, String, Peristiwa, String)} without a chosen target (war: the most hostile path). */
	public static boolean apply(MinecraftServer server, String sekte, Peristiwa peristiwa) {
		return apply(server, sekte, peristiwa, null);
	}

	/**
	 * A player sect master acts on behalf of {@code sekte} toward NPC sect {@code sasaran} with D's event {@code peristiwa}
	 * (at most one act a day): {@code UTUSAN} (greetings; during a war with {@code sasaran} it is a peace offer the other side
	 * takes when losing), {@code HADIAH} (15 of our resources, +10 to theirs), {@code PAKTA} (the two sects will not feud:
	 * a feud between them is called off), {@code PERANG_NPC} (an NPC war, once per 30 days, needs {@code perangAktif}).
	 * Returns true when it was carried out.
	 */
	public static boolean apply(MinecraftServer server, String sekte, Peristiwa peristiwa, @Nullable String sasaran) {
		DiplomasiData data = server == null || !Gabung.aktif() ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null || peristiwa == null) {
			return false;
		}
		long hari = Diplomasi.today(server);
		CompoundTag b = bagian(server, sekte);
		if (b.getLongOr("aksiHari", -1L) >= hari) {
			return false;
		}
		SekteNpc target = sasaran == null ? null : data.sect(sasaran);
		if (target == sect) {
			return false;
		}
		boolean done = switch (peristiwa) {
			case UTUSAN -> {
				if (target == null) {
					yield false;
				}
				Perang p = perang(server, sekte);
				if (p != null && p.lawan().equals(target.id) && p.skor() > 0) {
					data.addNews(hari, Component.translatable(KEY + "berita.damai", sect.name(), target.name()));
					selesai(server, sekte, null, true);
				} else {
					data.addNews(hari, Component.translatable(KEY + "berita.utusan", sect.name(), target.name()));
				}
				yield true;
			}
			case HADIAH -> {
				if (target == null || sect.sumber < 15) {
					yield false;
				}
				sect.sumber -= 15;
				target.sumber += 10;
				data.addNews(hari, Component.translatable(KEY + "berita.hadiah", sect.name(), target.name()));
				yield true;
			}
			case PAKTA -> {
				if (target == null) {
					yield false;
				}
				tambahPakta(server, sekte, target.id);
				tambahPakta(server, target.id, sekte);
				data.addNews(hari, Component.translatable(KEY + "berita.pakta", sect.name(), target.name()));
				yield true;
			}
			case PERANG_NPC -> {
				if (!Diplomasi.config().perangAktif || hari < b.getLongOr("perangKetuaHari", -1000L) + JEDA_PERANG_KETUA) {
					yield false;
				}
				SekteNpc lawan = target != null ? target : musuhTerburuk(data, sect);
				if (lawan == null || !perangMulai(server, sekte, lawan.id)) {
					yield false;
				}
				b.putLong("perangKetuaHari", hari);
				yield true;
			}
			default -> false;
		};
		if (done) {
			b.putLong("aksiHari", hari);
			data.setDirty();
			GabungData.get(server).setDirty();
		}
		return done;
	}

	private static @Nullable SekteNpc musuhTerburuk(DiplomasiData data, SekteNpc sect) {
		SekteNpc best = null;
		int score = Integer.MIN_VALUE;
		for (SekteNpc other : data.sects) {
			if (other == sect) {
				continue;
			}
			int s = (other.aliran != sect.aliran ? 200 : 0) + Math.abs(other.aliran - sect.aliran) * 50 - Math.abs(other.strength() - sect.strength()) / 10;
			if (s > score) {
				score = s;
				best = other;
			}
		}
		return best;
	}

	private static void tambahPakta(MinecraftServer server, String sekte, String lain) {
		CompoundTag b = bagian(server, sekte);
		String list = b.getStringOr("pakta", "");
		if (!(("," + list + ",").contains("," + lain + ","))) {
			b.putString("pakta", list.isEmpty() ? lain : list + "," + lain);
			GabungData.get(server).setDirty();
		}
	}

	/** True when a player master made the two sects sign a pact (41.8.5 "Arah"): they will not feud. */
	public static boolean pakta(MinecraftServer server, String a, String b) {
		return ("," + bagian(server, a).getStringOr("pakta", "") + ",").contains("," + b + ",");
	}

	// ================================================================== membership diplomacy (41.8.4)

	/**
	 * Asked by {@code Aksi.run} before hall {@code owner} acts against {@code sekte} with {@code aksi} (a D action id of
	 * {@code DiplomasiPayloads.ACTIONS}): non-null refuses with that reason (41.8.4). A member, a branch hall or the player
	 * master may not declare war on the sect, nor demand tribute from it; a guest elder is not refused, but once the war is
	 * declared he is "Lepas Tamu" (standing -20, R6; D's {@code PERANG} bus action, registered in {@link #init}).
	 */
	public static @Nullable Component veto(UUID owner, String sekte, String aksi) {
		MinecraftServer server = Diplomasi.server();
		if (server == null || owner == null || sekte == null || !Gabung.aktif()) {
			return null;
		}
		boolean perang = "perang".equals(aksi);
		boolean tuntut = "tuntut".equals(aksi);
		if (!perang && !tuntut) {
			return null;
		}
		GabungData g = GabungData.get(server);
		var k = g.peek(owner);
		boolean anggota = k != null && k.anggotaDi(sekte);
		boolean tamu = anggota && k.pangkat == dev.kultivator.sekte.gabung.api.Pangkat.TAMU;
		GabungData.DataSekteGabung d = g.peekSekte(sekte);
		boolean cabang = d != null && d.cabang.contains(owner);
		boolean ketua = d != null && owner.equals(d.ketuaPemain);
		if (tamu && !cabang && !ketua) {
			// R6: not refused; the declared war itself ends the guest status (the PERANG bus action, see init)
			return null;
		}
		if (anggota || cabang || ketua) {
			return Component.translatable(KEY + (perang ? "veto.perang" : "veto.tuntut"), nama(server, sekte));
		}
		return null;
	}

	/** One-line hook of {@code Aksi.run}: tells the player and returns true when {@link #veto} refuses. */
	public static boolean vetoKirim(ServerPlayer player, String sekte, String aksi) {
		Component why = veto(player.getUUID(), sekte, aksi);
		if (why != null) {
			player.sendSystemMessage(why.copy().withStyle(ChatFormatting.RED));
			return true;
		}
		return false;
	}

	/**
	 * Makes the hall of {@code owner} allied with its mother sect (41.8.2): pact SEKUTU through D's relation, standing at
	 * least 60, no war; works for demonic sects too (a branch is family, not an alliance offer). False without a hall.
	 */
	public static boolean paktaCabang(UUID owner, String sekte) {
		MinecraftServer server = Diplomasi.server();
		DiplomasiData data = server == null || owner == null ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null || Halls.hallOf(owner) == null) {
			return false;
		}
		Relasi r = data.relasi(owner, sect);
		if (r.perang) {
			r.perang = false;
			r.perangKalah = 0;
			r.perangMenang = 0;
		}
		long hari = Diplomasi.today(server);
		r.pakta = Relasi.PAKTA_SEKUTU;
		r.standing = Math.max(r.standing, Standing.PAKTA_SEKUTU_MIN);
		r.sekutuHadiahDay = hari;
		r.ditemui = true;
		r.note(hari, Component.translatable(KEY + "kronik.cabang"));
		Diplomasi.log(server, owner, Component.translatable(KEY + "log.cabang", sect.name()).withStyle(ChatFormatting.GOLD));
		Diplomasi.fire(server, owner, sect, AksiSekte.SEKUTU);
		data.setDirty();
		return true;
	}

	/** Ends the branch pact of {@code owner} with {@code sekte} (back to no pact) and changes the standing by {@code delta}. */
	public static void lepasPakta(MinecraftServer server, UUID owner, String sekte, int delta) {
		DiplomasiData data = server == null || owner == null ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null) {
			return;
		}
		Relasi r = data.relasi(owner, sect);
		r.pakta = Relasi.PAKTA_TIDAK;
		r.note(Diplomasi.today(server), Component.translatable(KEY + "kronik.cabang_lepas"));
		data.setDirty();
		if (delta != 0) {
			setStanding(server, owner, sekte, r.standing + delta);
		}
	}

	/** True while {@code sekte} is absorbed into its player master's hall (41.8.6 "Serap"). */
	public static boolean lebur(MinecraftServer server, String sekte) {
		GabungData.DataSekteGabung d = server == null ? null : GabungData.get(server).peekSekte(sekte);
		return d != null && d.lebur;
	}

	/**
	 * Absorbs {@code sekte} into hall {@code owner} ({@code tuan = owner}, the one vassal mechanism of D) or releases it
	 * ({@code owner} null: {@code tuan = null}, standing stays at least SEKUTU 60 for the former master).
	 */
	public static void setLebur(MinecraftServer server, String sekte, @Nullable UUID owner) {
		DiplomasiData data = server == null ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null) {
			return;
		}
		GabungData g = GabungData.get(server);
		GabungData.DataSekteGabung d = g.sekte(sekte);
		UUID lama = sect.tuan;
		sect.tuan = owner;
		d.lebur = owner != null;
		if (owner != null) {
			Relasi r = data.relasi(owner, sect);
			r.bawahan = true;
			r.pakta = Relasi.PAKTA_SEKUTU;
			r.standing = Math.max(r.standing, Standing.PAKTA_SEKUTU_MIN);
			r.note(Diplomasi.today(server), Component.translatable(KEY + "kronik.serap"));
		} else if (lama != null) {
			Relasi r = data.relasi(lama, sect);
			r.bawahan = false;
			r.standing = Math.max(r.standing, Standing.PAKTA_SEKUTU_MIN);
			r.note(Diplomasi.today(server), Component.translatable(KEY + "kronik.pisah"));
		}
		data.setDirty();
		g.setDirty();
	}

	// ================================================================== the NPC war (41.6.10)

	/** The war {@code sekte} is fighting now, or null. */
	public static @Nullable Perang perang(MinecraftServer server, String sekte) {
		GabungData.DataSekteGabung d = server == null ? null : GabungData.get(server).peekSekte(sekte);
		if (d == null || d.perang.getStringOr("lawan", "").isEmpty()) {
			return null;
		}
		CompoundTag t = d.perang;
		return new Perang(t.getStringOr("lawan", ""), t.getIntOr("skor", 0), t.getLongOr("mulai", 0L), t.getLongOr("sampai", 0L));
	}

	/**
	 * Starts an NPC war of {@code sekte} against {@code lawan} now (a player master's order, {@code uji gabung perang}): D's
	 * feud for {@link #PERANG_HARI} days, the war record on both sides, D's news and bus action, then
	 * {@code GabungBus.PERANG_MULAI} for both sides. False when either side already fights or they share a pact.
	 */
	public static boolean perangMulai(MinecraftServer server, String sekte, String lawan) {
		DiplomasiData data = server == null || !Gabung.aktif() ? null : DiplomasiData.get(server);
		SekteNpc a = data == null ? null : data.sect(sekte);
		SekteNpc b = data == null ? null : data.sect(lawan);
		if (a == null || b == null || a == b || perang(server, a.id) != null || perang(server, b.id) != null || pakta(server, a.id, b.id)) {
			return false;
		}
		long hari = Diplomasi.today(server);
		a.musuhNpc = b.index;
		a.musuhSampai = hari + PERANG_HARI;
		catatPerang(server, a, b, hari);
		data.addNews(hari, Component.translatable(GeneratorNama.KEY + "berita.perang_npc", a.name(), b.name()));
		data.setDirty();
		Diplomasi.fire(server, null, a, AksiSekte.PERANG_NPC);
		umumkanMulai(server, a, b);
		return true;
	}

	private static void catatPerang(MinecraftServer server, SekteNpc a, SekteNpc b, long hari) {
		GabungData g = GabungData.get(server);
		for (SekteNpc[] pair : new SekteNpc[][]{{a, b}, {b, a}}) {
			CompoundTag t = new CompoundTag();
			t.putString("lawan", pair[1].id);
			t.putInt("skor", 0);
			t.putLong("mulai", hari);
			t.putLong("sampai", hari + PERANG_HARI);
			t.putLong("hariSkor", hari);
			g.sekte(pair[0].id).perang = t;
		}
		g.setDirty();
	}

	private static void umumkanMulai(MinecraftServer server, SekteNpc a, SekteNpc b) {
		Politik.kabar(server, "gabung.perang_mulai", a.name(), b.name());
		GabungBus.PERANG_MULAI.invoker().onPerangMulai(server, a.id, b.id);
		GabungBus.PERANG_MULAI.invoker().onPerangMulai(server, b.id, a.id);
	}

	/**
	 * Adds {@code delta} to {@code sekte}'s war score (and the opposite to the enemy's) for {@code sebab} (a mission, a
	 * defended gate, a duel); +-{@link #PERANG_SKOR} ends the war at once.
	 */
	public static void perangSkor(MinecraftServer server, String sekte, int delta, String sebab) {
		Perang p = perang(server, sekte);
		if (p == null || delta == 0) {
			return;
		}
		GabungData g = GabungData.get(server);
		int skor = Math.max(-PERANG_SKOR, Math.min(PERANG_SKOR, p.skor() + delta));
		g.sekte(sekte).perang.putInt("skor", skor);
		GabungData.DataSekteGabung other = g.peekSekte(p.lawan());
		if (other != null && sekte.equals(other.perang.getStringOr("lawan", ""))) {
			other.perang.putInt("skor", -skor);
		}
		g.setDirty();
		if (Math.abs(skor) >= PERANG_SKOR) {
			perangAkhir(server, sekte, skor > 0);
		}
	}

	/**
	 * Ends {@code sekte}'s war. {@code menang} null decides by the score (a tie by strength); a peace by envoy
	 * ({@link #apply} UTUSAN) is a draw for both (no resources move). The winner gains
	 * {@link #SUMBER_PERANG}, the loser loses as much (a tier when it falls below zero) and may go MEROSOT; D's feud ends;
	 * then {@code GabungBus.PERANG_AKHIR} for both sides.
	 */
	public static void perangAkhir(MinecraftServer server, String sekte, @Nullable Boolean menang) {
		selesai(server, sekte, menang, false);
	}

	private static void selesai(MinecraftServer server, String sekte, @Nullable Boolean menang, boolean damai) {
		Perang p = perang(server, sekte);
		DiplomasiData data = server == null ? null : DiplomasiData.get(server);
		if (p == null || data == null) {
			return;
		}
		SekteNpc a = data.sect(sekte);
		SekteNpc b = data.sect(p.lawan());
		GabungData g = GabungData.get(server);
		g.sekte(sekte).perang = new CompoundTag();
		if (b != null && sekte.equals(g.sekte(b.id).perang.getStringOr("lawan", ""))) {
			g.sekte(b.id).perang = new CompoundTag();
		}
		g.setDirty();
		if (a == null || b == null) {
			return;
		}
		if (a.musuhNpc == b.index) {
			a.musuhNpc = -1;
		}
		if (b.musuhNpc == a.index) {
			b.musuhNpc = -1;
		}
		long hari = Diplomasi.today(server);
		boolean seri = damai || menang == null && p.skor() == 0 && a.strength() == b.strength();
		boolean aMenang = menang != null ? menang : p.skor() != 0 ? p.skor() > 0 : a.strength() > b.strength();
		cekKelas(server, a.id);
		cekKelas(server, b.id);
		if (!seri) {
			SekteNpc winner = aMenang ? a : b;
			SekteNpc loser = aMenang ? b : a;
			winner.sumber += SUMBER_PERANG;
			loser.sumber -= SUMBER_PERANG;
			jatuhTier(server, data, loser);
			data.addNews(hari, Component.translatable(GeneratorNama.KEY + "berita.perang_npc_usai", winner.name(), loser.name()));
			Politik.kabar(server, "gabung.perang_akhir", winner.name(), loser.name());
			catatKalah(server, loser, hari);
		} else {
			data.addNews(hari, Component.translatable(KEY + "berita.perang_seri", a.name(), b.name()));
		}
		data.setDirty();
		cekKelas(server, a.id);
		cekKelas(server, b.id);
		GabungBus.PERANG_AKHIR.invoker().onPerangAkhir(server, a.id, b.id, !seri && aMenang);
		GabungBus.PERANG_AKHIR.invoker().onPerangAkhir(server, b.id, a.id, !seri && !aMenang);
	}

	/** Records a lost war; a tier-1 sect with two losses inside 30 days goes MEROSOT (K4, {@code bolehMerosot}). */
	private static void catatKalah(MinecraftServer server, SekteNpc loser, long hari) {
		CompoundTag b = bagian(server, loser.id);
		long[] lama = b.getLongArray("kalah").orElse(new long[0]);
		List<Long> keep = new ArrayList<>();
		for (long d : lama) {
			if (hari - d < MEROSOT_JENDELA) {
				keep.add(d);
			}
		}
		keep.add(hari);
		long[] baru = new long[keep.size()];
		for (int i = 0; i < baru.length; i++) {
			baru[i] = keep.get(i);
		}
		b.putLongArray("kalah", baru);
		GabungData g = GabungData.get(server);
		GabungData.DataSekteGabung d = g.sekte(loser.id);
		if (!d.merosot && GabungConfig.get().bolehMerosot && loser.tier <= 1 && keep.size() >= MEROSOT_KALAH) {
			setMerosot(server, loser.id, true);
		}
		g.setDirty();
	}

	/** Puts the sect into MEROSOT or lifts it (test, {@link #bangkit}). */
	public static void setMerosot(MinecraftServer server, String sekte, boolean merosot) {
		DiplomasiData data = server == null ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null) {
			return;
		}
		GabungData g = GabungData.get(server);
		GabungData.DataSekteGabung d = g.sekte(sekte);
		if (d.merosot == merosot) {
			return;
		}
		long hari = Diplomasi.today(server);
		d.merosot = merosot;
		if (merosot) {
			d.merosotHari = hari;
		}
		g.setDirty();
		data.addNews(hari, Component.translatable(KEY + (merosot ? "berita.merosot" : "berita.bangkit"), sect.name()));
		Politik.kabar(server, merosot ? "gabung.merosot" : "gabung.bangkit", sect.name());
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + (merosot ? "pesan.merosot" : "pesan.bangkit"), sect.name()),
				merosot ? ChatFormatting.DARK_RED : ChatFormatting.GOLD);
	}

	/** True while the sect is MEROSOT (half the people, Paviliun only HUANG, missions E-D; 41C/41W read it). */
	public static boolean merosot(MinecraftServer server, String sekte) {
		GabungData.DataSekteGabung d = server == null ? null : GabungData.get(server).peekSekte(sekte);
		return d != null && d.merosot;
	}

	/**
	 * "Gunung yang Bangkit" (41.6.13, the end of 41W's chain): MEROSOT is lifted and the sect gains {@link #BANGKIT_SUMBER}
	 * resources, at most once per {@link #BANGKIT_JEDA} days; every online member gets {@code sekte/gabung_bangkit} and
	 * {@code pemain} (who led it, may be null) too. False when the sect is not MEROSOT or Bangkit is still on cooldown.
	 */
	public static boolean bangkit(MinecraftServer server, String sekte, @Nullable ServerPlayer pemain) {
		DiplomasiData data = server == null || !Gabung.aktif() ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null) {
			return false;
		}
		GabungData g = GabungData.get(server);
		GabungData.DataSekteGabung d = g.sekte(sekte);
		long hari = Diplomasi.today(server);
		if (!d.merosot || d.bangkitHari >= 0L && hari < d.bangkitHari + BANGKIT_JEDA) {
			return false;
		}
		d.bangkitHari = hari;
		setMerosot(server, sekte, false);
		sect.sumber += BANGKIT_SUMBER;
		data.setDirty();
		g.setDirty();
		for (UUID id : g.anggotaSekte(sekte)) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				Advancements.grant(p, "sekte/gabung_bangkit");
			}
		}
		if (pemain != null) {
			Advancements.grant(pemain, "sekte/gabung_bangkit");
		}
		cekKelas(server, sekte);
		return true;
	}

	// ================================================================== daily pass (called by Politik, one sect a turn)

	/**
	 * One sect's daily 41 politics on world day {@code hari}: the class check, the tier fall of a negative resource, the war
	 * record against D's feud (data score of the day, the end at day 10 or +-100, a feud D ended on its own). Cheap: O(1).
	 */
	public static void harian(MinecraftServer server, String sekte, long hari) {
		DiplomasiData data = server == null ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		if (sect == null) {
			return;
		}
		cekKelas(server, sekte);
		if (sect.sumber < 0) {
			jatuhTier(server, data, sect);
			data.setDirty();
		}
		Perang p = perang(server, sekte);
		if (p != null) {
			SekteNpc lawan = data.sect(p.lawan());
			GabungData g = GabungData.get(server);
			CompoundTag t = g.sekte(sekte).perang;
			if (lawan == null) {
				g.sekte(sekte).perang = new CompoundTag();
				g.setDirty();
			} else if (hari >= p.sampai() || Math.abs(p.skor()) >= PERANG_SKOR || sect.musuhNpc != lawan.index && lawan.musuhNpc != sect.index) {
				perangAkhir(server, sekte, null);
			} else if (t.getLongOr("hariSkor", hari) < hari) {
				// the data part of the war: the day's skirmishes, like D's feud day (members' missions add on top)
				RandomSource random = server.overworld().getRandom();
				long days = Math.min(PERANG_HARI, hari - t.getLongOr("hariSkor", hari));
				int delta = 0;
				for (int i = 0; i < days; i++) {
					int mine = sect.strength() + random.nextInt(120);
					int theirs = lawan.strength() + random.nextInt(120);
					delta += (mine >= theirs ? 1 : -1) * (4 + random.nextInt(6));
				}
				t.putLong("hariSkor", hari);
				GabungData.DataSekteGabung other = g.peekSekte(lawan.id);
				if (other != null) {
					other.perang.putLong("hariSkor", hari);
				}
				g.setDirty();
				perangSkor(server, sekte, delta, "simulasi");
			}
		}
		cekKelas(server, sekte);
	}

	// ================================================================== bodies and candidates (D's own helpers)

	/**
	 * A temporary sect body of {@code sekte} (D's RIVAL disciple body: never saved, untouchable except by a duel partner) at
	 * {@code pos} with rank step {@code step}: the seat duels, the succession duel, the guest test. Null when it could not
	 * spawn. Remove it with {@link #lenyap}.
	 */
	public static @Nullable DiscipleEntity tubuh(ServerLevel level, Vec3 pos, String sekte, int step, int slot, Component nama, boolean panji) {
		SekteNpc sect = DiplomasiData.get(level.getServer()).sect(sekte);
		if (sect == null) {
			return null;
		}
		return Tubuh.spawn(level, pos, sect, step, slot, nama, BlockPos.containing(pos), panji);
	}

	/** The master's rank step of the sect ({@code 4 + 2 x tier}), for its duel body. */
	public static int langkahKetua(MinecraftServer server, String sekte) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		return sect == null ? 6 : sect.masterRankStep();
	}

	/** A member's rank step of the sect for {@code slot}. */
	public static int langkahAnggota(MinecraftServer server, String sekte, int slot) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		return sect == null ? 3 : sect.memberRankStep(slot);
	}

	/** Leaves in a puff of cloud (never a death, never loot). */
	public static void lenyap(@Nullable DiscipleEntity body) {
		Tubuh.vanish(body);
	}

	/** The ground top at {@code pos}'s column. */
	public static BlockPos tanah(ServerLevel level, BlockPos pos) {
		return Tubuh.ground(level, pos);
	}

	/**
	 * A disciple candidate sent by the sect to a player hall (41.8.2 branch, every 10 days; 41.8.6 absorbed, every 5 days):
	 * a young cultivator three steps under the sect's members, source {@code sumber} ({@code gabung_cabang},
	 * {@code gabung_serap}).
	 */
	public static @Nullable Halls.CandidateSpec calon(MinecraftServer server, String sekte, String sumber) {
		SekteNpc sect = server == null ? null : DiplomasiData.get(server).sect(sekte);
		if (sect == null) {
			return null;
		}
		RandomSource random = server.overworld().getRandom();
		int step = Math.max(1, sect.memberRankStep(random.nextInt(8)) - 3);
		return new Halls.CandidateSpec(Tubuh.body(random, step), 45 + random.nextInt(30), 0.15D, 0.05D, false, sumber,
				KEY + "calon.asal", 10);
	}

	// ================================================================== listeners (from Politik.init)

	/** Registers the bus listeners once: standing after leaving (41.7), D's feuds becoming 41 wars, R6 for guests. */
	public static void init() {
		if (listening) {
			return;
		}
		listening = true;
		GabungBus.KELUAR.register(DiplomasiGabung::keluar);
		SekteBus.RIVAL_SECT_ACTION.register((server, owner, sectId, action, magnitude) -> {
			if (server == null || !Gabung.aktif()) {
				return;
			}
			if (action == AksiSekte.PERANG_NPC && owner == null) {
				feudMulai(server, sectId);
			} else if (action == AksiSekte.PERANG && owner != null) {
				var k = GabungData.get(server).peek(owner);
				if (k != null && k.anggotaDi(sectId) && k.pangkat == dev.kultivator.sekte.gabung.api.Pangkat.TAMU) {
					TetuaTamu.lepas(server, owner, -20, "perang");
				}
			}
		});
	}

	/**
	 * 41.7: a forced leave or an expulsion leaves the standing at most -30, a defector at most -60 ({@code GabungApi.keluar}
	 * already applied D's clamped -20 where it could; this sets the exact floor and never raises a worse standing).
	 */
	private static void keluar(ServerPlayer player, String sekte, StatusKeluar status) {
		MinecraftServer server = player.level().getServer();
		int batas = switch (status) {
			case PAMIT_PAKSA, DIUSIR -> -30;
			case PEMBELOT -> -60;
			default -> Integer.MAX_VALUE;
		};
		if (batas == Integer.MAX_VALUE || server == null) {
			return;
		}
		int now = standing(server, player.getUUID(), sekte);
		if (now > batas) {
			setStanding(server, player.getUUID(), sekte, batas);
		}
	}

	/** D started a feud on its own (Simulasi): it becomes a 41 war, unless a pact or a peaceful master stops it. */
	private static void feudMulai(MinecraftServer server, String sectId) {
		DiplomasiData data = DiplomasiData.get(server);
		SekteNpc a = data.sect(sectId);
		SekteNpc b = a == null ? null : data.sect(a.musuhNpc);
		if (a == null || b == null || perang(server, a.id) != null && perang(server, a.id).lawan().equals(b.id)) {
			return;
		}
		long hari = Diplomasi.today(server);
		boolean damai = pakta(server, a.id, b.id) || Politik.ARAH_DAMAI.equals(Politik.arah(server, a.id))
				|| perang(server, a.id) != null || perang(server, b.id) != null;
		if (damai) {
			a.musuhNpc = -1;
			data.addNews(hari, Component.translatable(KEY + "berita.perang_batal", a.name(), b.name()));
			data.setDirty();
			return;
		}
		a.musuhSampai = Math.max(a.musuhSampai, hari + PERANG_HARI);
		catatPerang(server, a, b, hari);
		data.setDirty();
		umumkanMulai(server, a, b);
	}

	// ================================================================== helpers

	/** The 41D compound of the sect ({@code DataSekteGabung.bagian("politik")}). */
	public static CompoundTag bagian(MinecraftServer server, String sekte) {
		return GabungData.get(server).sekte(sekte).bagian(BAGIAN);
	}

	private static int clamp(int tier) {
		return Math.max(1, Math.min(SekteNpc.MAX_TIER, tier));
	}

	/** Shown standing helper for the screens: the stance key of {@code owner} toward the sect. */
	public static String sikapKunci(MinecraftServer server, UUID owner, String sekte) {
		DiplomasiData data = server == null || owner == null ? null : DiplomasiData.get(server);
		SekteNpc sect = data == null ? null : data.sect(sekte);
		Relasi r = sect == null ? null : data.peek(owner, sect.id);
		return r == null ? (sect == null ? dev.kultivator.sekte.api.Sikap.NETRAL : dev.kultivator.sekte.api.Sikap.of(Standing.awal(sect), false)).key()
				: r.sikap().key();
	}

	private DiplomasiGabung() {
	}
}
