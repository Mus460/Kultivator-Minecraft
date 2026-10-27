package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.alchemy.Alchemy;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaGen;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.KabarTempat;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The six travelling troupes (DESIGN 42.7.1, characters of 42.10). They are global: every troupe has a route of its own over
 * the known towns (a pure function of the world day, so nothing is stored and a troupe is never in two towns on one day):
 * it stays {@code periode} days in a town (arriving about 07:30 on the first day, leaving at dusk on the last), then spends
 * the days of the route that have no town on the road. The day before leaving and two days before arriving a rumour tells
 * where it goes ({@code KabarTempat} trigger {@code acara_rombongan_pergi} / {@code _tiba}, which also lights a gossip).
 * <ul>
 * <li><b>Rombongan Bulan Sabit</b> (Nyonya Hong Ling, Yan Feiyan, Yan Feihu, two more acrobats, two musicians): a stage
 * ({@link PanggungKelilingEntity}) on the plaza and three three-minute shows at 10:00, 15:00 and 19:00: spinning plates, the
 * hoop, the three-high human pyramid (three bodies on one spot, the clips lift them), stilts, the lion dance
 * ({@link BarongsaiEntity} with the two dancers inside) and a bow. Watching to the end gives the buff <i>Terhibur</i>.</li>
 * <li><b>Lao Shuo dan Balok Kayu</b>: sessions at 10:00, 15:00 and 20:00, one of the twelve tales a day in six lines with the
 * wooden block between them; the residents sit around ({@code HidupApi.kerumunan}); listening 60 s or more teaches one lore
 * page and a rumour of a hidden place ({@code DuniaSites.discover}).</li>
 * <li><b>Ah Qin si Erhu</b>: on the bridge (or the gate) 09:00-12:00 and 16:00-19:00, three erhu songs of his own; a coin
 * (shift-click) asks for the next song.</li>
 * <li><b>Tabib Keliling Hu Lu</b>: a tent, a silat demonstration breaking a board at 11:00 and 16:00; sells a Tiger Plaster
 * (heals a little) and the Thousand Virtues Pill (60 % heals, 30 % nothing, 10 % nausea); an alchemist of level 2 can expose
 * him (Nama Baik +3) or become his partner (a little profit each visit).</li>
 * <li><b>Kafilah Saudagar Ma</b>: a tent and two porters; the trade goods of other lands (price x1.1) and one rare good.</li>
 * <li><b>Pemusik Pengantin Suona</b>: four musicians for hire on the square, two short sets a day; the ceremonies of 42D-P
 * may ask {@link #pemusikSewaan}.</li>
 * </ul>
 * Everything that looks like a body, a stage or a tent exists only while a player is within {@code jarakTampil} blocks;
 * otherwise a troupe is data only. Player verbs: shift-click a member (tip, coin and song, plaster, goods) and the story
 * effects {@code rombongan <verb>} for dialogs of 42B-C data.
 */
public final class Rombongan {

	public static final String BULAN_SABIT = "bulan_sabit";
	public static final String LAO_SHUO = "lao_shuo";
	public static final String AH_QIN = "ah_qin";
	public static final String HU_LU = "hu_lu";
	public static final String MA_FU = "ma_fu";
	public static final String SUONA = "suona";
	/** The ids; the index is the bit of {@code Berkah.tontonRombongan}. */
	public static final List<String> ID = List.of(BULAN_SABIT, LAO_SHUO, AH_QIN, HU_LU, MA_FU, SUONA);

	static final String L = "kultivator.kota42.acara.rombongan.";
	/** Players farther than this from a stage do not count as watching. */
	static final double JARAK_TONTON = 16.0D;
	/** Tip amounts (NB). */
	public static final long TIP_BIASA = 2L;
	public static final long TIP_BESAR = 10L;
	public static final long HARGA_PLESTER = 3L;
	public static final long HARGA_PIL = 12L;
	public static final long HARGA_KOIN_LAGU = 1L;

	/** One performer of a troupe: character id ("" = unnamed), role, sex, age group, spot on the stage, idle clip and props. */
	record Anggota(String tokoh, NpcRole peran, boolean perempuan, int usia, int spot, int klip, int prop, int propKiri) {
	}

	/** A troupe. {@code jam} are the show start hours, {@code lama} the show length in ticks. */
	record Grup(String id, int bit, int periode, int geser, int panggung, int tenda, double buka, double tutup, int[] jam, int lama,
			List<Anggota> anggota) {
	}

	static final List<Grup> GRUP = List.of(
			new Grup(BULAN_SABIT, 0, 2, 0, PanggungKelilingEntity.VARIAN_AKROBAT, -1, 8.0D, 21.0D, new int[] {10, 15, 19}, 3600, List.of(
					new Anggota("hong_ling", NpcRole.PENARI, true, Sosok.DEWASA, 1, GerakId.SIKAP_TANGAN_DEPAN, PropId.KIPAS_LIPAT, 0),
					new Anggota("yan_feiyan", NpcRole.AKROBAT, true, Sosok.MUDA, 2, GerakId.SIKAP_BERKACAK, 0, 0),
					new Anggota("yan_feihu", NpcRole.AKROBAT, false, Sosok.MUDA, 0, GerakId.SIKAP_ANGKUH, 0, 0),
					new Anggota("", NpcRole.AKROBAT, false, Sosok.MUDA, 3, GerakId.SIKAP_TANGAN_BELAKANG, 0, 0),
					new Anggota("", NpcRole.AKROBAT, true, Sosok.MUDA, 5, GerakId.SIKAP_SANTAI_P, 0, 0),
					new Anggota("", NpcRole.PEMUSIK, false, Sosok.DEWASA, 4, KotaGerakId.TABUH_GENDERANG_KECIL, KotaGerakId.PROP_GENDERANG_KECIL, 0),
					new Anggota("", NpcRole.PEMUSIK, false, Sosok.TUA, 5, KotaGerakId.PUKUL_GONG, KotaGerakId.PROP_GONG_TANGAN, 0))),
			new Grup(LAO_SHUO, 1, 2, 1, PanggungKelilingEntity.VARIAN_PENDONGENG, -1, 8.5D, 21.5D, new int[] {10, 15, 20}, 3600, List.of(
					new Anggota("lao_shuo", NpcRole.PENDONGENG, false, Sosok.TUA, 0, KotaGerakId.DONGENG, KotaGerakId.PROP_BALOK_KAYU, PropId.KIPAS_LIPAT))),
			new Grup(AH_QIN, 2, 3, 2, -1, -1, 8.5D, 19.5D, new int[] {9, 16}, 3000, List.of(
					new Anggota("ah_qin", NpcRole.PENGAMEN, true, Sosok.DEWASA, 0, KotaGerakId.MAIN_ERHU, KotaGerakId.PROP_ERHU, 0))),
			new Grup(HU_LU, 3, 2, 3, -1, TendaPasarEntity.VARIAN_PERMAINAN, 8.5D, 18.5D, new int[] {11, 16}, 1800, List.of(
					new Anggota("hu_lu", NpcRole.PENJUAL_OBAT, false, Sosok.DEWASA, 0, GerakId.SERU_DAGANG, KotaGerakId.PROP_KOTAK_OBAT_KELILING, 0),
					new Anggota("", NpcRole.PENJUAL_OBAT, false, Sosok.MUDA, 1, GerakId.SIKAP_TANGAN_DEPAN, PropId.PAPAN_KAYU, 0))),
			new Grup(MA_FU, 4, 3, 4, -1, TendaPasarEntity.VARIAN_MAINAN, 8.0D, 18.0D, new int[] {}, 0, List.of(
					new Anggota("ma_fu", NpcRole.PEDAGANG_KELILING, false, Sosok.DEWASA, 0, GerakId.TAWAR, PropId.SEMPOA, 0),
					new Anggota("", NpcRole.KULI, false, Sosok.DEWASA, 1, GerakId.SIKAP_LIPAT_TANGAN, 0, 0),
					new Anggota("", NpcRole.KULI, false, Sosok.MUDA, 2, GerakId.DUDUK_LANTAI, 0, 0))),
			new Grup(SUONA, 5, 2, 5, -1, -1, 8.0D, 20.0D, new int[] {12, 17}, 1200, List.of(
					new Anggota("", NpcRole.PEMUSIK, false, Sosok.DEWASA, 0, KotaGerakId.TIUP_SUONA, KotaGerakId.PROP_SUONA, 0),
					new Anggota("", NpcRole.PEMUSIK, false, Sosok.MUDA, 1, KotaGerakId.TIUP_SUONA, KotaGerakId.PROP_SUONA, 0),
					new Anggota("", NpcRole.PEMUSIK, true, Sosok.DEWASA, 2, KotaGerakId.TABUH_GENDERANG_KECIL, KotaGerakId.PROP_GENDERANG_KECIL, 0),
					new Anggota("", NpcRole.PEMUSIK, false, Sosok.TUA, 3, KotaGerakId.PUKUL_GONG, KotaGerakId.PROP_GONG_TANGAN, 0))));

	// ================================================================== state

	/** A troupe standing in a place now (bodies and set exist only while a player is near). */
	private static final class Hadir {
		final long situs;
		final Grup g;
		final KultivatorNpc[] badan;
		@Nullable PanggungKelilingEntity panggung;
		@Nullable BarongsaiEntity singa;
		Vec3 pusat = Vec3.ZERO;
		float yaw;
		/** Show key (day * 8 + show index) the last cycle ran, or -1. */
		long kunci = -1L;
		int babak = -1;
		/** Lao Shuo: the tale of the running show (an id of {@link #KISAH}), told to the sheet of 42U-2 once heard. */
		String kisahIni = "";
		/** Ah Qin: requested song and the tick it was asked. */
		int lagu = -1;
		long laguMulai;
		/** Forced show (test): its start game tick, or 0. */
		long paksaMulai;
		boolean tiba;

		Hadir(long situs, Grup g) {
			this.situs = situs;
			this.g = g;
			this.badan = new KultivatorNpc[g.anggota().size()];
		}
	}

	private static final Map<Long, Map<String, Hadir>> HADIR = new HashMap<>();
	/** Presence forced by tests: "situs:grup" -> game tick it lasts until. */
	private static final Map<String, Long> PAKSA = new HashMap<>();
	/** Places whose Suona troupe is out on a hire (a wedding or a funeral cortege) until the game tick. */
	private static final Map<Long, Long> SEWA = new HashMap<>();
	/** Watching: player -> {situs, grup bit, show key, samples, near}. */
	private static final Map<UUID, long[]> TONTON = new HashMap<>();
	/** Tips and purchases of the day: "uuid:grup:jenis" -> day. */
	private static final Map<String, Long> HARIAN = new HashMap<>();
	/** The ware cursor of Ma Fu per place and day: "situs:day" -> units sold. */
	private static final Map<String, Integer> TERJUAL = new HashMap<>();
	private static final TendaPasarEntity.Pengelola TENDA = new TendaPasarEntity.Pengelola();
	private static List<Long> kotaCache = List.of();
	private static long kotaMenit = -1L;
	private static long hariTerakhir = Long.MIN_VALUE;
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(Rombongan::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> lupakan());
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !player.isShiftKeyDown() || !(player instanceof ServerPlayer sp)
					|| !(entity instanceof KultivatorNpc npc) || !(level instanceof ServerLevel sl)) {
				return InteractionResult.PASS;
			}
			return klik(sl, sp, npc) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		KalenderKota.daftarSumber(Rombongan::kalender);
		HargaKota.daftarAcara(Rombongan::harga);
		Akibat.daftar("rombongan", (player, args) -> efek(player, args));
		dev.kultivator.dunia.kota42.KotaUji.chips("kota_hidup acara rombongan datang <id>", "kota_hidup acara rombongan pentas <id>",
				"kota_hidup acara rombongan pergi", "kota_hidup acara rombongan rute", "kota_hidup acara wayang perdana",
				"kota_hidup acara wayang curi", "kota_hidup acara wayang kembali", "kota_hidup acara wayang padam", "kota_hidup acara guanpu lempar <n>",
				"kota_hidup acara guanpu sisa");
	}

	private static void lupakan() {
		for (Map<String, Hadir> per : HADIR.values()) {
			for (Hadir h : per.values()) {
				bubar(h);
			}
		}
		HADIR.clear();
		PAKSA.clear();
		SEWA.clear();
		TONTON.clear();
		HARIAN.clear();
		TERJUAL.clear();
		TENDA.hapusSemua();
		kotaCache = List.of();
		kotaMenit = -1L;
		hariTerakhir = Long.MIN_VALUE;
	}

	static Grup grup(String id) {
		for (Grup g : GRUP) {
			if (g.id().equals(id)) {
				return g;
			}
		}
		return GRUP.getFirst();
	}

	// ================================================================== the route

	/** The known towns a troupe may visit (towns, themed cities and the capital of the overworld), sorted by id. */
	static List<Long> kota(MinecraftServer server) {
		long menit = server.getTickCount() / 1200L;
		if (menit != kotaMenit) {
			kotaMenit = menit;
			List<Long> out = new ArrayList<>();
			for (long s : Towns.known(server)) {
				if (PajakKota.kelas(server, s) >= 2 && Level.OVERWORLD.equals(Towns.dimension(server, s))) {
					out.add(s);
				}
			}
			Collections.sort(out);
			kotaCache = out;
		}
		return kotaCache;
	}

	/** The town troupe {@code g} stays in on world day {@code hari}, or 0 while it is on the road. */
	public static long lokasi(MinecraftServer server, String grupId, long hari) {
		return lokasi(server, grup(grupId), hari);
	}

	static long lokasi(MinecraftServer server, Grup g, long hari) {
		List<Long> kota = kota(server);
		int n = kota.size();
		if (n == 0) {
			return 0L;
		}
		int m = Math.max(n + 1, 4);
		long seg = Math.floorDiv(hari, (long) g.periode());
		int idx = (int) Math.floorMod(seg + g.geser(), (long) m);
		return idx < n ? kota.get(idx) : 0L;
	}

	private static boolean paksa(long situs, Grup g, long now) {
		Long until = PAKSA.get(situs + ":" + g.id());
		return until != null && until > now;
	}

	/** True when {@code g} is in the town at clock hour {@code jam} of day {@code hari} (route and arrival/leaving hours). */
	private static boolean dalamJendela(Grup g, long hari, double jam) {
		int k = (int) Math.floorMod(hari, (long) g.periode());
		double dari = k == 0 ? g.buka() : 6.0D;
		double sampai = k == g.periode() - 1 ? g.tutup() : 22.0D;
		return jam >= dari && jam < sampai;
	}

	private static boolean hadirDi(MinecraftServer server, Grup g, long situs, long hari, double jam, long now) {
		if (paksa(situs, g, now)) {
			return true;
		}
		return KotaConfig.acara().rombongan && lokasi(server, g, hari) == situs && dalamJendela(g, hari, jam);
	}

	/** Index of the show of {@code g} running at clock hour {@code jam}, or -1; {@code t} receives ticks since it started. */
	private static int pentasKe(Grup g, double jam, long[] t) {
		for (int i = 0; i < g.jam().length; i++) {
			long dt = (long) ((jam - g.jam()[i]) * 1000.0D);
			if (dt >= 0L && dt < g.lama()) {
				t[0] = dt;
				return i;
			}
		}
		return -1;
	}

	/** The troupe staying in place {@code situs} now (the first of them), or null. */
	static @Nullable String diKota(MinecraftServer server, long situs) {
		if (!KotaConfig.acara().rombongan && PAKSA.isEmpty()) {
			return null;
		}
		long now = server.overworld().getGameTime();
		long hari = KalenderKota.hari(server);
		double jam = KalenderKota.jamDesimal(server);
		for (Grup g : GRUP) {
			if (hadirDi(server, g, situs, hari, jam, now)) {
				return g.id();
			}
		}
		return null;
	}

	/** Every troupe staying in place {@code situs} now. */
	public static List<String> semuaDiKota(MinecraftServer server, long situs) {
		List<String> out = new ArrayList<>();
		long now = server.overworld().getGameTime();
		long hari = KalenderKota.hari(server);
		double jam = KalenderKota.jamDesimal(server);
		for (Grup g : GRUP) {
			if (hadirDi(server, g, situs, hari, jam, now)) {
				out.add(g.id());
			}
		}
		return out;
	}

	/** True while a show of a troupe runs in place {@code situs} (by the clock; a forced test show counts too). */
	static boolean sedangPentas(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null) {
			return false;
		}
		long now = server.overworld().getGameTime();
		long hari = KalenderKota.hari(server);
		double jam = KalenderKota.jamDesimal(server);
		long[] t = new long[1];
		for (Grup g : GRUP) {
			if (g.lama() > 0 && hadirDi(server, g, situs, hari, jam, now)) {
				if (pentasKe(g, jam, t) >= 0) {
					return true;
				}
				Map<String, Hadir> per = HADIR.get(situs);
				Hadir h = per == null ? null : per.get(g.id());
				if (h != null && h.paksaMulai > 0L && now - h.paksaMulai < g.lama()) {
					return true;
				}
			}
		}
		return false;
	}

	/** True when the Suona musicians are in town (42D-P weddings and funerals may hire them for the music). */
	public static boolean pemusikSewaan(MinecraftServer server, long situs) {
		return semuaDiKota(server, situs).contains(SUONA);
	}

	/**
	 * Hires the Suona musicians of place {@code situs} for a cortege until game tick {@code sampai}: true when they are in town,
	 * and then they leave their square (the cortege's musicians are theirs) until the hire ends.
	 */
	static boolean sewa(MinecraftServer server, long situs, long sampai) {
		if (server == null || !pemusikSewaan(server, situs)) {
			return false;
		}
		SEWA.merge(situs, sampai, Math::max);
		return true;
	}

	// ================================================================== calendar, prices, rumours

	private static void kalender(MinecraftServer server, long situs, long dari, long sampai, List<KalenderKota.Entri> out) {
		if (!KotaConfig.acara().rombongan) {
			return;
		}
		for (long d = dari; d <= sampai; d++) {
			for (Grup g : GRUP) {
				if (lokasi(server, g, d) != situs) {
					continue;
				}
				int k = (int) Math.floorMod(d, (long) g.periode());
				Component nama = Component.translatable(L + "nama." + g.id());
				int jamTiba = (int) (k == 0 ? g.buka() : 6.0D);
				int menitTiba = (int) ((k == 0 ? g.buka() : 6.0D) % 1.0D * 60.0D);
				if (g.jam().length == 0) {
					out.add(new KalenderKota.Entri("rb:" + g.id() + ":" + d, KotaLembar.Buku.AC_ROMBONGAN, nama, d, jamTiba, menitTiba,
							(int) Math.ceil(g.tutup() - g.buka()), null));
					continue;
				}
				for (int i = 0; i < g.jam().length; i++) {
					if (k == 0 && g.jam()[i] < g.buka() || k == g.periode() - 1 && g.jam()[i] + g.lama() / 1000.0D > g.tutup()) {
						continue;
					}
					out.add(new KalenderKota.Entri("rb:" + g.id() + ":" + d + ":" + i, KotaLembar.Buku.AC_ROMBONGAN,
							Component.translatable(L + "pertunjukan", nama), d, g.jam()[i], 0, Math.max(1, (int) Math.ceil(g.lama() / 1000.0D)), null));
				}
			}
		}
	}

	/** Kafilah Saudagar Ma in town: trade goods x1.1 (the goods of far lands). */
	private static @Nullable HargaKota.Pengali harga(MinecraftServer server, long situs, dev.kultivator.dunia.kota42.api.Kategori k) {
		if (k != dev.kultivator.dunia.kota42.api.Kategori.DAGANG || !KotaConfig.acara().rombongan) {
			return null;
		}
		long now = server.overworld().getGameTime();
		return hadirDi(server, grup(MA_FU), situs, KalenderKota.hari(server), KalenderKota.jamDesimal(server), now)
				? new HargaKota.Pengali(1.1D, Component.translatable(L + "harga_kafilah")) : null;
	}

	/** Once a world day: the rumours of the routes (leaving tomorrow, arriving in two days). */
	private static void kabar(MinecraftServer server, long hari) {
		ServerLevel level = server.overworld();
		for (Grup g : GRUP) {
			long ini = lokasi(server, g, hari);
			long besok = lokasi(server, g, hari + 1L);
			long lusa = lokasi(server, g, hari + 2L);
			Component nama = Component.translatable(L + "nama." + g.id());
			if (ini != 0L && besok != ini) {
				// leaving tonight or tomorrow: "the troupe goes to ... "
				long tujuan = lusa != 0L && lusa != ini ? lusa : besok;
				if (tujuan != 0L) {
					KabarTempat.pemicu(level, ini, "acara_rombongan_pergi", nama.getString(), PajakKota.nama(server, tujuan).getString());
				}
			}
			if (lusa != 0L && lusa != besok && lusa != ini) {
				KabarTempat.pemicu(level, lusa, "acara_rombongan_tiba", nama.getString(), "lusa");
			}
		}
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		long tc = server.getTickCount();
		if (tc % 5L != 1L) {
			return;
		}
		KotaConfig.Acara c = KotaConfig.acara();
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (!c.aktif || !c.rombongan && PAKSA.isEmpty()) {
			if (!HADIR.isEmpty()) {
				lupakan();
			}
			return;
		}
		long hari = KalenderKota.hari(server);
		if (tc % 100L == 1L && hari != hariTerakhir) {
			hariTerakhir = hari;
			kabar(server, hari);
			TERJUAL.clear();
		}
		double jam = KalenderKota.jamDesimal(server);
		boolean pelan = tc % 20L == 1L;
		java.util.Set<Long> hidup = new java.util.HashSet<>();
		for (TempatHuni huni : KalenderKota.aktif(level)) {
			if (!KalenderKota.kota(huni)) {
				continue;
			}
			Map<String, Hadir> per = HADIR.computeIfAbsent(huni.situsId, k -> new HashMap<>());
			for (Grup g : GRUP) {
				Hadir h = per.get(g.id());
				boolean ada = hadirDi(server, g, huni.situsId, hari, jam, now)
						&& !(g.id().equals(SUONA) && SEWA.getOrDefault(huni.situsId, 0L) > now);
				BlockPos jangkar = ada ? jangkar(level, huni, g) : null;
				boolean dekat = ada && jangkar != null && KalenderKota.adaPemain(level, jangkar, c.jarakTampil);
				if (!dekat) {
					if (h != null) {
						bubar(h);
						per.remove(g.id());
					}
					continue;
				}
				if (h == null) {
					h = new Hadir(huni.situsId, g);
					per.put(g.id(), h);
				}
				if (!siapkan(level, huni, h, jangkar, now, hari, jam)) {
					continue;
				}
				hidup.add(huni.situsId);
				jalankan(level, huni, h, now, jam, hari, pelan);
			}
			if (per.isEmpty()) {
				HADIR.remove(huni.situsId);
			}
		}
		// places that are no longer active
		Iterator<Map.Entry<Long, Map<String, Hadir>>> it = HADIR.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Map<String, Hadir>> e = it.next();
			if (!hidup.contains(e.getKey()) && Pemanggil.huni(e.getKey()) == null) {
				for (Hadir h : e.getValue().values()) {
					bubar(h);
				}
				it.remove();
			}
		}
		TENDA.rapikan();
		PAKSA.values().removeIf(until -> until <= now);
		SEWA.values().removeIf(until -> until <= now);
		if (pelan) {
			HARIAN.values().removeIf(d -> d < hari - 1L);
		}
	}

	// ================================================================== set and bodies

	/** Where a troupe sets up in a place: its stage or square spot, the bridge or gate, a stall spot. */
	private static @Nullable BlockPos jangkar(ServerLevel level, TempatHuni huni, Grup g) {
		BlockPos alun = KalenderKota.alun(huni);
		String[] jenis = switch (g.id()) {
			case AH_QIN -> new String[] {"jembatan", "gerbang"};
			case HU_LU, MA_FU -> new String[] {"lapak"};
			case LAO_SHUO -> new String[] {"kedai_teh", "kedai", "panggung"};
			default -> new String[] {"panggung"};
		};
		for (String type : jenis) {
			List<Titik> list = new ArrayList<>(huni.titik(type));
			list.removeIf(t -> t.data().containsKey("acara"));
			if (!list.isEmpty()) {
				// two troupes never take the same titik: spread by the troupe's bit
				return MusimKota.tanah(level, list.get((int) Math.floorMod((long) g.bit() + huni.situsId, (long) list.size())).pos());
			}
		}
		double a = Math.PI * 2.0D * g.bit() / 6.0D + 0.4D;
		double r = g.id().equals(BULAN_SABIT) ? 0.0D : 9.0D + g.bit();
		return MusimKota.tanah(level, alun.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
	}

	/** Raises the set and the bodies (once), keeps them; false when the place cannot hold them now. */
	private static boolean siapkan(ServerLevel level, TempatHuni huni, Hadir h, BlockPos jangkar, long now, long hari, double jam) {
		Grup g = h.g;
		BlockPos alun = KalenderKota.alun(huni);
		Vec3 centre = Vec3.atBottomCenterOf(jangkar);
		h.pusat = centre;
		h.yaw = (float) (Math.toDegrees(Math.atan2(alun.getZ() + 0.5D - centre.z, alun.getX() + 0.5D - centre.x))) - 90.0F;
		if (jangkar.distSqr(alun) < 4.0D) {
			h.yaw = 0.0F;
		}
		if (g.panggung() >= 0) {
			if (h.panggung == null || h.panggung.isRemoved()) {
				if (DuniaBudget.count(KotaAcaraEntities.ANGGARAN) + 8 > DuniaBudget.cap(KotaAcaraEntities.ANGGARAN)) {
					return false;
				}
				PanggungKelilingEntity p = KotaAcaraEntities.PANGGUNG_KELILING.create(level, EntitySpawnReason.EVENT);
				if (p == null) {
					return false;
				}
				p.ikat(huni.situsId, jangkar.asLong());
				p.pindah(centre, h.yaw);
				p.setVarian(g.panggung());
				p.setPanji(g.bit() + 1);
				p.rakit();
				if (!level.addFreshEntity(p)) {
					return false;
				}
				DuniaBudget.track(KotaAcaraEntities.ANGGARAN, p);
				h.panggung = p;
			}
		}
		if (g.tenda() >= 0) {
			TENDA.pastikan(level, huni.situsId, huni.situsId + ":rb:" + g.id(), jangkar, h.yaw, g.tenda(), g.id().equals(HU_LU) ? 0x5C7C3A : 0xB08A3A);
		}
		for (int i = 0; i < g.anggota().size(); i++) {
			KultivatorNpc body = h.badan[i];
			if (body != null && !body.isRemoved()) {
				continue;
			}
			Anggota a = g.anggota().get(i);
			if (!a.tokoh().isEmpty() && adaWargaTokoh(huni.situsId, a.tokoh())) {
				// the person lives here as a resident: he performs by his own round (KalenderKota.JadwalTokoh)
				continue;
			}
			Vec3 spot = titik(h, a.spot());
			int look = look(a, g, huni.situsId, i);
			KultivatorNpc npc = HidupApi.tubuhStaf(level, huni.situsId, a.peran(), BlockPos.containing(spot), h.yaw, look);
			if (npc == null) {
				continue;
			}
			npc.memory().putBoolean("hold", true);
			npc.teleportTo(spot.x, spot.y, spot.z);
			SosokApi.setUsia(npc, a.usia());
			if (!a.tokoh().isEmpty()) {
				CeritaApi.tandaiTokoh(npc, a.tokoh());
				npc.setPlate(Component.translatable("kultivator.kota.papan_nama", Component.translatable("kultivator.kota42.acara.tokoh." + a.tokoh()),
						Component.translatable("kultivator.kota42.acara.gelar." + a.tokoh())));
			}
			h.badan[i] = npc;
		}
		if (!h.tiba && jam < g.buka() + 1.5D && Math.floorMod(hari, (long) g.periode()) == 0L) {
			for (ServerPlayer p : KalenderKota.penonton(level, jangkar)) {
				Msg.actionBar(p, Component.translatable(L + "tiba", Component.translatable(L + "nama." + g.id())).withStyle(ChatFormatting.GOLD));
			}
		}
		h.tiba = true;
		return true;
	}

	private static boolean adaWargaTokoh(long situs, String id) {
		for (Warga w : WargaApi.wargaTempat(situs)) {
			if (id.equals(w.tokoh) && w.hidup()) {
				return true;
			}
		}
		return false;
	}

	/** The look seed: a named character keeps one face in the whole world; the others vary by place. */
	private static int look(Anggota a, Grup g, long situs, int i) {
		long seed = a.tokoh().isEmpty() ? situs * 31L + g.bit() * 7L + i : ("kota42:" + a.tokoh()).hashCode();
		return WargaGen.lookUntuk(a.peran(), a.perempuan() ? Sosok.PEREMPUAN : Sosok.LAKI, RandomSource.create(seed));
	}

	/** A performer's spot: a stage spot when there is a stage, else a row in front of the anchor. */
	private static Vec3 titik(Hadir h, int spot) {
		if (h.panggung != null && !h.panggung.isRemoved()) {
			return h.panggung.titik(spot);
		}
		double r = Math.toRadians(h.yaw);
		Vec3 maju = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
		Vec3 sisi = new Vec3(Math.cos(r), 0.0D, Math.sin(r));
		double s = (spot % 3 - 1) * 1.4D;
		double f = spot < 3 ? 0.4D : -1.0D;
		return h.pusat.add(maju.scale(f)).add(sisi.scale(s));
	}

	private static void bubar(Hadir h) {
		for (int i = 0; i < h.badan.length; i++) {
			if (h.badan[i] != null && !h.badan[i].isRemoved()) {
				h.badan[i].vanish();
			}
			h.badan[i] = null;
		}
		if (h.panggung != null && !h.panggung.isRemoved()) {
			h.panggung.bongkar();
		}
		h.panggung = null;
		if (h.singa != null && !h.singa.isRemoved()) {
			h.singa.discard();
		}
		h.singa = null;
	}

	// ================================================================== the shows

	private static void jalankan(ServerLevel level, TempatHuni huni, Hadir h, long now, double jam, long hari, boolean pelan) {
		Grup g = h.g;
		long[] tt = new long[1];
		int ke = pentasKe(g, jam, tt);
		long t = tt[0];
		if (ke < 0 && h.paksaMulai > 0L && now - h.paksaMulai < g.lama()) {
			ke = 0;
			t = now - h.paksaMulai;
		}
		long kunci = ke < 0 ? -1L : hari * 8L + ke;
		if (kunci != h.kunci) {
			if (h.kunci >= 0L) {
				selesai(level, h);
			}
			h.kunci = kunci;
			h.babak = -1;
			if (kunci >= 0L) {
				mulaiPentas(level, huni, h, t);
			}
		}
		if (ke >= 0) {
			switch (g.id()) {
				case BULAN_SABIT -> bulanSabit(level, huni, h, t);
				case LAO_SHUO -> laoShuo(level, h, t, hari, ke);
				case AH_QIN -> ahQin(level, h, t, now);
				case HU_LU -> huLu(level, h, t);
				case SUONA -> suona(level, h, t);
				default -> diam(h);
			}
			if (pelan) {
				catatTonton(level, h);
			}
		} else {
			diam(h);
			if (g.id().equals(AH_QIN) || g.id().equals(MA_FU) || g.id().equals(HU_LU)) {
				senggang(level, h, now);
			}
		}
		if (h.singa != null && g.id().equals(BULAN_SABIT) && h.babak != 5) {
			h.singa.discard();
			h.singa = null;
		}
	}

	/** True once per {@code periode} ticks for a clock that advances in steps of 5 (a plain modulo could be stepped over). */
	private static boolean tiap(long t, long periode) {
		return Math.floorDiv(t, periode) != Math.floorDiv(t - 5L, periode);
	}

	private static void gerak(@Nullable KultivatorNpc npc, int klip, int prop, int propKiri) {
		if (npc == null || npc.isRemoved()) {
			return;
		}
		if (SosokApi.gerakSekarang(npc) != klip) {
			SosokApi.gerak(npc, klip, 0, 0, prop, propKiri, 0);
		}
	}

	private static void taruh(Hadir h, int i, Vec3 at) {
		KultivatorNpc npc = h.badan[i];
		if (npc == null || npc.isRemoved()) {
			return;
		}
		if (npc.position().distanceToSqr(at) > 0.04D) {
			npc.teleportTo(at.x, at.y, at.z);
		}
		npc.setYRot(h.yaw);
		npc.setYHeadRot(h.yaw);
	}

	/** Every member back on his spot with his idle clip. */
	private static void diam(Hadir h) {
		for (int i = 0; i < h.badan.length; i++) {
			Anggota a = h.g.anggota().get(i);
			KultivatorNpc npc = h.badan[i];
			if (npc == null || npc.isRemoved()) {
				continue;
			}
			taruh(h, i, titik(h, a.spot()));
			gerak(npc, a.klip(), a.prop(), a.propKiri());
		}
	}

	/** Off-show idling of the street performers (Ah Qin practising, the hawkers calling now and then). */
	private static void senggang(ServerLevel level, Hadir h, long now) {
		if (tiap(now, 400L)) {
			KultivatorNpc npc = h.badan[0];
			if (npc != null && !npc.isRemoved()) {
				String kunci = h.g.id() + ".seru." + (int) (Math.abs(now / 400L) % 3L);
				npc.say(Component.translatable(L + kunci), 80);
			}
		}
	}

	private static void mulaiPentas(ServerLevel level, TempatHuni huni, Hadir h, long t) {
		Grup g = h.g;
		Vec3 depan = h.panggung != null ? h.panggung.depan(4.0D) : h.pusat.add(0.0D, 0.0D, 0.0D);
		if (g.lama() >= 3000 && !g.id().equals(AH_QIN)) {
			HidupApi.kerumunan(level, BlockPos.containing(depan), (int) Math.max(30L, (g.lama() - t) / 20L));
		}
		for (ServerPlayer p : KalenderKota.penonton(level, BlockPos.containing(h.pusat))) {
			Msg.actionBar(p, Component.translatable(L + "mulai." + g.id()).withStyle(ChatFormatting.GOLD));
		}
		level.playSound(null, BlockPos.containing(h.pusat), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.AMBIENT, 3.0F, 0.6F);
	}

	private static void sebut(@Nullable KultivatorNpc npc, String kunci, @Nullable String suara, Object... isian) {
		if (npc == null || npc.isRemoved()) {
			return;
		}
		npc.say(Component.translatable(L + kunci, isian), 140);
		if (suara != null && VoiceEvents.exists(suara)) {
			Voice.say(npc, suara, "");
		}
	}

	// ---- Rombongan Bulan Sabit

	private static final int[] BABAK_BS = {0, 150, 825, 1500, 2250, 2850, 3450};

	private static void bulanSabit(ServerLevel level, TempatHuni huni, Hadir h, long t) {
		int babak = 0;
		for (int i = BABAK_BS.length - 1; i >= 0; i--) {
			if (t >= BABAK_BS[i]) {
				babak = i;
				break;
			}
		}
		boolean baru = babak != h.babak;
		h.babak = babak;
		KultivatorNpc[] b = h.badan;
		BlockPos at = BlockPos.containing(h.pusat);
		if (baru) {
			sebut(b[0], "bs.babak." + babak, babak == 0 ? "kota42.akrobat_buka" : null);
			if (babak == 5 && h.singa == null) {
				bukaSinga(level, huni, h);
			}
			if (babak == 3) {
				level.playSound(null, at, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.AMBIENT, 2.5F, 0.6F);
			}
		}
		// the musicians always
		gerak(b[5], KotaGerakId.TABUH_GENDERANG_KECIL, KotaGerakId.PROP_GENDERANG_KECIL, 0);
		gerak(b[6], KotaGerakId.PUKUL_GONG, KotaGerakId.PROP_GONG_TANGAN, 0);
		taruh(h, 5, titik(h, 4));
		taruh(h, 6, titik(h, 5));
		taruh(h, 0, titik(h, 1));
		if (babak == 5) {
			Lagu.mainkan(level, at, Lagu.SINGA, t - 5L, t, 40.0D);
		} else if (babak < 6) {
			Lagu.mainkan(level, at, Lagu.PENTAS, t - 5L, t, 40.0D);
		}
		switch (babak) {
			case 0 -> {
				gerak(b[0], KotaGerakId.UCAP_SELAMAT, PropId.KIPAS_LIPAT, 0);
				pasangan(h, 1, 2, 0, 3, 5, GerakId.HORMAT, 0);
			}
			case 1 -> {
				// spinning plates: Feiyan and the first acrobat at the wings, Feihu in the middle
				gerak(b[0], GerakId.TEPUK_TANGAN, 0, 0);
				for (int i : new int[] {1, 2, 3}) {
					taruh(h, i, titik(h, i == 1 ? 1 : i == 2 ? 0 : 2));
					gerak(b[i], KotaGerakId.AKROBAT_PIRING, KotaGerakId.PROP_PIRING_AKROBAT, KotaGerakId.PROP_PIRING_AKROBAT);
				}
				taruh(h, 4, titik(h, 3));
				gerak(b[4], GerakId.TEPUK_TANGAN, 0, 0);
				if (tiap(t, 40L)) {
					level.playSound(null, at, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.AMBIENT, 1.5F, 1.4F + (t % 3L) * 0.2F);
				}
			}
			case 2 -> {
				// the hoop: Feihu and the second girl jump through, the others clap
				gerak(b[0], GerakId.TEPUK_TANGAN, 0, 0);
				for (int i : new int[] {2, 4}) {
					taruh(h, i, titik(h, i == 2 ? 0 : 2));
					gerak(b[i], KotaGerakId.AKROBAT_SIMPAI, KotaGerakId.PROP_SIMPAI, 0);
				}
				for (int i : new int[] {1, 3}) {
					taruh(h, i, titik(h, i == 1 ? 1 : 3));
					gerak(b[i], GerakId.TEPUK_TANGAN, 0, 0);
				}
			}
			case 3 -> {
				// the pyramid: three bodies on one spot (the clips lift the middle and the top)
				gerak(b[0], GerakId.TEPUK_TANGAN, 0, 0);
				Vec3 tengah = titik(h, 0);
				long t0 = level.getGameTime() - (t - BABAK_BS[3]);
				int[] urut = {3, 2, 1};
				int[] klip = {KotaGerakId.PIRAMID_BAWAH, KotaGerakId.PIRAMID_TENGAH, KotaGerakId.PIRAMID_ATAS};
				for (int k = 0; k < urut.length; k++) {
					taruh(h, urut[k], tengah);
					if (b[urut[k]] != null && !b[urut[k]].isRemoved() && SosokApi.gerakSekarang(b[urut[k]]) != klip[k]) {
						SosokApi.gerakSinkron(b[urut[k]], klip[k], t0);
					}
				}
				taruh(h, 4, titik(h, 2));
				gerak(b[4], GerakId.TEPUK_TANGAN, 0, 0);
				if (t - BABAK_BS[3] > 600L && tiap(t, 10L)) {
					level.playSound(null, at, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.AMBIENT, 2.0F, 0.5F);
				}
			}
			case 4 -> {
				// the stilts: three walk a lazy figure of eight over the boards
				gerak(b[0], GerakId.TEPUK_TANGAN, 0, 0);
				double a = t * 0.012D;
				Vec3 c = titik(h, 3).add(titik(h, 0)).scale(0.5D);
				double r = Math.toRadians(h.yaw);
				Vec3 maju = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
				Vec3 sisi = new Vec3(Math.cos(r), 0.0D, Math.sin(r));
				int k = 0;
				for (int i : new int[] {1, 2, 3}) {
					double ph = a + k * 2.094D;
					Vec3 at2 = c.add(sisi.scale(Math.sin(ph) * 1.9D)).add(maju.scale(Math.sin(ph * 2.0D) * 0.6D));
					KultivatorNpc npc = b[i];
					if (npc != null && !npc.isRemoved()) {
						npc.teleportTo(at2.x, at2.y, at2.z);
						npc.setYRot((float) Math.toDegrees(Math.atan2(-Math.cos(ph) * 1.9D, 1.0D)) + h.yaw);
						npc.setYHeadRot(npc.getYRot());
					}
					gerak(npc, KotaGerakId.EGRANG_JALAN, 0, 0);
					k++;
				}
				taruh(h, 4, titik(h, 5));
				gerak(b[4], GerakId.TEPUK_TANGAN, 0, 0);
			}
			case 5 -> {
				// the lion dance: Feiyan the head, Feihu the tail
				gerak(b[0], GerakId.TEPUK_TANGAN, 0, 0);
				if (h.singa != null && !h.singa.isRemoved()) {
					h.singa.bawaPenari(b[1], b[2]);
				}
				taruh(h, 3, titik(h, 3));
				taruh(h, 4, titik(h, 2));
				gerak(b[3], GerakId.TEPUK_TANGAN, 0, 0);
				gerak(b[4], GerakId.TEPUK_TANGAN, 0, 0);
			}
			default -> {
				// the bow
				pasangan(h, 0, 1, 2, 3, 4, GerakId.HORMAT, KotaGerakId.UCAP_SELAMAT);
			}
		}
	}

	/** Puts {@code a, b, c, d, e} on their own spots with one clip (everybody bows, claps...). */
	private static void pasangan(Hadir h, int a, int b, int c, int d, int e, int klip, int klipKetua) {
		int[] semua = {a, b, c, d, e};
		for (int i : semua) {
			if (i >= h.badan.length) {
				continue;
			}
			taruh(h, i, titik(h, h.g.anggota().get(i).spot()));
			gerak(h.badan[i], i == 0 && klipKetua != 0 ? klipKetua : klip, 0, 0);
		}
	}

	private static void bukaSinga(ServerLevel level, TempatHuni huni, Hadir h) {
		if (DuniaBudget.count(KotaAcaraEntities.ANGGARAN) + 3 > DuniaBudget.cap(KotaAcaraEntities.ANGGARAN)) {
			return;
		}
		BarongsaiEntity singa = KotaAcaraEntities.BARONGSAI.create(level, EntitySpawnReason.EVENT);
		if (singa == null) {
			return;
		}
		Vec3 at = titik(h, 0);
		singa.ikat(huni.situsId, BlockPos.containing(at).asLong());
		singa.pindah(at, h.yaw);
		singa.setVarian((int) Math.floorMod(huni.situsId, (long) BarongsaiEntity.VARIAN_JUMLAH));
		if (!level.addFreshEntity(singa)) {
			return;
		}
		DuniaBudget.track(KotaAcaraEntities.ANGGARAN, singa);
		singa.pentas(at, h.yaw);
		h.singa = singa;
		level.playSound(null, BlockPos.containing(at), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.AMBIENT, 2.0F, 1.0F);
	}

	// ---- Lao Shuo

	/** The twelve tales ({@code kisah.<id>.1..6} and {@code .akhir}). */
	public static final List<String> KISAH = List.of("kera_batu", "ular_putih", "penggembala_penenun", "chang_e", "mulan", "hakim_bao",
			"delapan_dewa", "yu_gong", "sarjana_rubah", "kupu_kupu", "penjara_iblis", "sekte_awan");

	/** The tale of Lao Shuo on world day {@code hari} for session {@code ke}. */
	public static String kisahHari(long hari, int ke) {
		return KISAH.get((int) Math.floorMod(hari * 3L + ke, (long) KISAH.size()));
	}

	private static void laoShuo(ServerLevel level, Hadir h, long t, long hari, int ke) {
		KultivatorNpc npc = h.badan[0];
		String kisah = kisahHari(hari, ke);
		h.kisahIni = kisah;
		int baris = t < 200L ? 0 : (int) Math.min(7L, 1L + (t - 200L) / 480L);
		boolean baru = baris != h.babak;
		h.babak = baris;
		BlockPos at = BlockPos.containing(h.pusat);
		taruh(h, 0, titik(h, 0));
		if (npc == null || npc.isRemoved()) {
			return;
		}
		if (baru) {
			level.playSound(null, at, SoundEvents.BAMBOO_WOOD_HIT, SoundSource.AMBIENT, 2.0F, 0.8F);
			if (baris == 0) {
				npc.say(Component.translatable(L + "ls.buka"), 140);
			} else if (baris <= 6) {
				npc.say(Component.translatable("kultivator.kota42.acara.kisah." + kisah + "." + baris), 400);
			} else {
				npc.say(Component.translatable("kultivator.kota42.acara.kisah." + kisah + ".akhir"), 400);
				if (VoiceEvents.exists("kota42.lao_shuo_tutup")) {
					Voice.say(npc, "kota42.lao_shuo_tutup", "");
				}
			}
		}
		// the fan and the block: the block thwacks at the high points
		int klip = baris % 2 == 0 ? KotaGerakId.DONGENG : GerakId.BICARA_TANGAN;
		gerak(npc, klip, KotaGerakId.PROP_BALOK_KAYU, PropId.KIPAS_LIPAT);
		if (tiap(t, 120L) && baris > 0 && baris < 7) {
			level.playSound(null, at, SoundEvents.BAMBOO_WOOD_HIT, SoundSource.AMBIENT, 1.2F, 1.5F);
		}
	}

	// ---- Ah Qin

	private static void ahQin(ServerLevel level, Hadir h, long t, long now) {
		taruh(h, 0, titik(h, 0));
		gerak(h.badan[0], KotaGerakId.MAIN_ERHU, KotaGerakId.PROP_ERHU, 0);
		BlockPos at = BlockPos.containing(h.pusat);
		Lagu.Melodi lagu;
		long tt;
		if (h.lagu >= 0 && now - h.laguMulai < 1500L) {
			lagu = Lagu.LAGU_AH_QIN[h.lagu % Lagu.LAGU_AH_QIN.length];
			tt = now - h.laguMulai;
		} else {
			int no = (int) ((t / 1500L) % Lagu.LAGU_AH_QIN.length);
			lagu = Lagu.LAGU_AH_QIN[no];
			tt = t % 1500L;
		}
		if (tt < 10L && h.babak != lagu.id().hashCode()) {
			h.babak = lagu.id().hashCode();
			sebut(h.badan[0], "aq.lagu." + lagu.id(), "kota42.ah_qin");
		}
		Lagu.mainkan(level, at, lagu, tt - 5L, tt, 32.0D);
	}

	// ---- Hu Lu

	private static void huLu(ServerLevel level, Hadir h, long t) {
		KultivatorNpc hu = h.badan[0];
		KultivatorNpc bantu = h.badan[1];
		BlockPos at = BlockPos.containing(h.pusat);
		int babak = t < 500L ? 0 : t < 900L ? 1 : t < 1000L ? 2 : t < 1500L ? 3 : 4;
		boolean baru = babak != h.babak;
		h.babak = babak;
		taruh(h, 0, titik(h, 0));
		taruh(h, 1, titik(h, 1));
		switch (babak) {
			case 0 -> {
				gerak(hu, GerakId.SERU_DAGANG, KotaGerakId.PROP_KOTAK_OBAT_KELILING, 0);
				gerak(bantu, GerakId.SIKAP_TANGAN_DEPAN, PropId.PAPAN_KAYU, 0);
				if (baru) {
					sebut(hu, "hl.seru.buka", "kota42.penjual_obat");
				}
			}
			case 1 -> {
				// the demonstration: stance and punches
				gerak(hu, GerakId.LATIHAN_TINJU, 0, 0);
				gerak(bantu, GerakId.SIKAP_TANGAN_DEPAN, PropId.PAPAN_KAYU, 0);
				if (baru) {
					sebut(hu, "hl.seru.jurus", null);
				}
				if (tiap(t, 40L)) {
					level.playSound(null, at, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.AMBIENT, 1.0F, 1.2F);
				}
			}
			case 2 -> {
				// the board breaks
				gerak(hu, GerakId.TEBAS_UDARA, 0, 0);
				if (baru) {
					Vec3 p = titik(h, 1);
					level.playSound(null, at, SoundEvents.BAMBOO_WOOD_HIT, SoundSource.AMBIENT, 2.5F, 0.5F);
					level.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.AMBIENT, 0.6F, 1.6F);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), p.x, p.y + 1.1D, p.z,
							24, 0.3D, 0.3D, 0.3D, 0.1D);
					sebut(hu, "hl.seru.papan", null);
				}
				gerak(bantu, GerakId.KAGET, 0, 0);
			}
			case 3 -> {
				gerak(hu, GerakId.SERU_DAGANG, KotaGerakId.PROP_PLESTER_HARIMAU, 0);
				gerak(bantu, GerakId.TEPUK_TANGAN, 0, 0);
				if (baru) {
					sebut(hu, "hl.seru.jual", null);
				}
			}
			default -> {
				gerak(hu, GerakId.SERU_DAGANG, KotaGerakId.PROP_KOTAK_OBAT_KELILING, 0);
				gerak(bantu, GerakId.SIKAP_LIPAT_TANGAN, 0, 0);
			}
		}
	}

	// ---- Suona

	private static void suona(ServerLevel level, Hadir h, long t) {
		int[] klip = {KotaGerakId.TIUP_SUONA, KotaGerakId.TIUP_SUONA, KotaGerakId.TABUH_GENDERANG_KECIL, KotaGerakId.PUKUL_GONG};
		int[] prop = {KotaGerakId.PROP_SUONA, KotaGerakId.PROP_SUONA, KotaGerakId.PROP_GENDERANG_KECIL, KotaGerakId.PROP_GONG_TANGAN};
		// the four stand in a small arc facing the square
		for (int i = 0; i < h.badan.length; i++) {
			taruh(h, i, titik(h, i));
			gerak(h.badan[i], klip[i], prop[i], 0);
		}
		if (t == 0L || h.babak < 0) {
			h.babak = 0;
			sebut(h.badan[0], "su.buka", null);
		}
		Lagu.mainkan(level, BlockPos.containing(h.pusat), Lagu.SUONA_MELODI, t - 5L, t, 44.0D);
	}

	// ================================================================== watching and rewards

	private static void catatTonton(ServerLevel level, Hadir h) {
		Grup g = h.g;
		if (h.kunci < 0L || g.lama() <= 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			double d2 = p.distanceToSqr(h.pusat.x, h.pusat.y, h.pusat.z);
			if (d2 > 48.0D * 48.0D) {
				continue;
			}
			long[] st = TONTON.get(p.getUUID());
			if (st == null || st[0] != h.situs || st[1] != g.bit() || st[2] != h.kunci) {
				st = new long[] {h.situs, g.bit(), h.kunci, 0L, 0L};
				TONTON.put(p.getUUID(), st);
			}
			st[3]++;
			if (d2 <= JARAK_TONTON * JARAK_TONTON) {
				st[4]++;
			}
		}
	}

	/** A show ended (or the troupe left): everybody who stayed to the end is rewarded. */
	private static void selesai(ServerLevel level, Hadir h) {
		Grup g = h.g;
		Iterator<Map.Entry<UUID, long[]>> it = TONTON.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, long[]> e = it.next();
			long[] st = e.getValue();
			if (st[0] != h.situs || st[1] != g.bit() || st[2] != h.kunci) {
				continue;
			}
			it.remove();
			if (!(level.getServer().getPlayerList().getPlayer(e.getKey()) instanceof ServerPlayer p) || p.isRemoved()) {
				continue;
			}
			long total = Math.max(1L, g.lama() / 20L);
			boolean penuh = st[4] >= total * 8L / 10L;
			switch (g.id()) {
				case BULAN_SABIT, HU_LU -> {
					if (penuh && g.id().equals(BULAN_SABIT)) {
						KalenderKota.Berkah.terhibur(p);
						Msg.chat(p, Component.translatable(L + "tonton.selesai", Component.translatable(L + "nama." + g.id())).withStyle(ChatFormatting.GOLD));
						Msg.actionBar(p, Component.translatable(L + "tip.petunjuk").withStyle(ChatFormatting.GRAY));
						int mask = KalenderKota.Berkah.tontonRombongan(p, g.bit());
						if (mask == (1 << ID.size()) - 1) {
							Advancements.grant(p, "tempat/kota42_rombongan");
						}
					}
				}
				case LAO_SHUO -> {
					if (st[4] >= 60L) {
						ganjaranDongeng(level, p, h);
						if (penuh) {
							KalenderKota.Berkah.terhibur(p);
						}
						int mask = KalenderKota.Berkah.tontonRombongan(p, g.bit());
						if (mask == (1 << ID.size()) - 1) {
							Advancements.grant(p, "tempat/kota42_rombongan");
						}
					}
				}
				default -> {
				}
			}
		}
	}

	private static final List<String> LORE = List.of("gua.pertapa", "gua.cermin_hati", "situs.mata_air_roh", "situs.panggung_bintang",
			"situs.puncak_petir", "jalan.tonggak", "jalan.kemah_begal", "gua.makam", "gua.urat_roh", "jalan.singgah", "jalan.dagang",
			"gua.sarang_binatang", "jalan.kuil", "jalan.penyeberangan");

	/** Teaches the next lore page of the rotation (the story effect {@code lore}: nothing when it is already known). */
	static void halamanLore(ServerPlayer p) {
		int n = KalenderKota.Berkah.urutan(p, "lore");
		Akibat.jalankan(p, "lore " + LORE.get(Math.floorMod(n, LORE.size())));
	}

	/** Lao Shuo's reward: one lore page and the rumour of one hidden place (once a day). */
	private static void ganjaranDongeng(ServerLevel level, ServerPlayer p, Hadir h) {
		dev.kultivator.dunia.kota42.ui.LembarUpacara.kisahDidengar(p, h.kisahIni);
		if (!KalenderKota.Berkah.sekaliSehari(p, "dongeng")) {
			Msg.actionBar(p, Component.translatable(L + "ls.sudah").withStyle(ChatFormatting.GRAY));
			return;
		}
		halamanLore(p);
		Msg.chat(p, Component.translatable(L + "ls.halaman").withStyle(ChatFormatting.GOLD));
		DuniaSites.Site pilih = null;
		for (DuniaSites.Site s : DuniaSites.near(level, p.blockPosition(), 1800, "")) {
			String k = s.kind();
			if (k.startsWith("kota") || k.startsWith("kampung") || k.startsWith("pos") || DuniaSites.knows(p, s.id())) {
				continue;
			}
			pilih = s;
			break;
		}
		if (pilih != null && DuniaSites.discover(p, pilih.id())) {
			Msg.chat(p, Component.translatable(L + "ls.petunjuk", pilih.pos().getX(), pilih.pos().getZ()).withStyle(ChatFormatting.AQUA));
		} else {
			Msg.chat(p, Component.translatable(L + "ls.petunjuk_kosong").withStyle(ChatFormatting.GRAY));
		}
	}

	// ================================================================== player verbs

	private static @Nullable Hadir cari(KultivatorNpc npc, int[] indeks) {
		for (Map<String, Hadir> per : HADIR.values()) {
			for (Hadir h : per.values()) {
				for (int i = 0; i < h.badan.length; i++) {
					if (h.badan[i] == npc) {
						indeks[0] = i;
						return h;
					}
				}
			}
		}
		return null;
	}

	/** Shift-click on a troupe member. */
	private static boolean klik(ServerLevel level, ServerPlayer player, KultivatorNpc npc) {
		int[] idx = new int[1];
		Hadir h = cari(npc, idx);
		if (h == null) {
			return false;
		}
		ItemStack tangan = player.getMainHandItem();
		switch (h.g.id()) {
			case AH_QIN -> lagu(player, h);
			case HU_LU -> {
				if (idx[0] == 0 && tangan.is(Items.GOLD_NUGGET)) {
					pil(player, h, npc);
				} else if (idx[0] == 0) {
					plester(player, h, npc);
				} else {
					tip(player, h, npc, TIP_BIASA);
				}
			}
			case MA_FU -> {
				if (idx[0] == 0) {
					barang(player, h, npc, tangan.is(Items.GOLD_NUGGET));
				} else {
					tip(player, h, npc, TIP_BIASA);
				}
			}
			default -> tip(player, h, npc, TIP_BIASA);
		}
		return true;
	}

	/** Tips the troupe: spirit stones for the street, a little good name once a day for a big one. */
	private static void tip(ServerPlayer player, Hadir h, KultivatorNpc npc, long nb) {
		if (!EkonomiApi.bayar(player, nb, "rombongan_tip")) {
			Msg.actionBar(player, Component.translatable(L + "tip.kurang", nb).withStyle(ChatFormatting.RED));
			return;
		}
		Msg.actionBar(player, Component.translatable(L + "tip.ok", nb).withStyle(ChatFormatting.GOLD));
		npc.say(Component.translatable(L + "tip.terima." + (int) Math.floorMod(player.level().getGameTime() / 20L, 3L)), 60);
		HidupApi.klip(npc, GerakId.HORMAT, 0, 30);
		if (nb >= TIP_BESAR && sekali(player, h.g.id() + ":tip", h.situs)) {
			PeristiwaJalan.namaBaik(player, h.situs, 1);
		}
	}

	private static boolean sekali(ServerPlayer player, String jenis, long situs) {
		MinecraftServer server = player.level().getServer();
		long hari = KalenderKota.hari(server);
		String key = player.getUUID() + ":" + jenis;
		Long d = HARIAN.get(key);
		if (d != null && d == hari) {
			return false;
		}
		HARIAN.put(key, hari);
		return true;
	}

	private static void lagu(ServerPlayer player, Hadir h) {
		if (!EkonomiApi.bayar(player, HARGA_KOIN_LAGU, "rombongan_koin")) {
			Msg.actionBar(player, Component.translatable(L + "tip.kurang", HARGA_KOIN_LAGU).withStyle(ChatFormatting.RED));
			return;
		}
		long now = player.level().getGameTime();
		h.lagu = h.lagu < 0 ? (int) Math.floorMod(now / 400L, 3L) : (h.lagu + 1) % Lagu.LAGU_AH_QIN.length;
		h.laguMulai = now;
		KultivatorNpc npc = h.badan[0];
		if (npc != null) {
			sebut(npc, "aq.minta." + h.lagu, null);
			HidupApi.tatap(npc, player);
		}
		Msg.actionBar(player, Component.translatable(L + "aq.koin", Component.translatable(L + "aq.lagu." + Lagu.LAGU_AH_QIN[h.lagu].id())).withStyle(ChatFormatting.GOLD));
		dev.kultivator.dunia.kota42.ui.LembarUpacara.laguDidengar(player, Lagu.LAGU_AH_QIN[h.lagu].id());
		CeritaApi.peristiwa(player, "rombongan_lagu", Lagu.LAGU_AH_QIN[h.lagu].id());
	}

	private static void plester(ServerPlayer player, Hadir h, KultivatorNpc npc) {
		if (player.getHealth() >= player.getMaxHealth()) {
			Msg.actionBar(player, Component.translatable(L + "hl.sehat").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!EkonomiApi.bayar(player, HARGA_PLESTER, "plester_harimau")) {
			Msg.actionBar(player, Component.translatable(L + "tip.kurang", HARGA_PLESTER).withStyle(ChatFormatting.RED));
			return;
		}
		player.heal(6.0F);
		HidupApi.klip(npc, GerakId.SERU_DAGANG, KotaGerakId.PROP_PLESTER_HARIMAU, 40);
		sebut(npc, "hl.plester", null);
		Msg.actionBar(player, Component.translatable(L + "hl.plester_ok", HARGA_PLESTER).withStyle(ChatFormatting.GREEN));
	}

	private static void pil(ServerPlayer player, Hadir h, KultivatorNpc npc) {
		if (!EkonomiApi.bayar(player, HARGA_PIL, "pil_seribu_khasiat")) {
			Msg.actionBar(player, Component.translatable(L + "tip.kurang", HARGA_PIL).withStyle(ChatFormatting.RED));
			return;
		}
		int roll = player.getRandom().nextInt(10);
		HidupApi.klip(npc, GerakId.SERU_DAGANG, 0, 40);
		if (roll < 6) {
			player.heal(20.0F);
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
			Msg.chat(player, Component.translatable(L + "hl.pil.sembuh").withStyle(ChatFormatting.GREEN));
		} else if (roll < 9) {
			Msg.chat(player, Component.translatable(L + "hl.pil.kosong").withStyle(ChatFormatting.GRAY));
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 240, 0));
			Msg.chat(player, Component.translatable(L + "hl.pil.mual").withStyle(ChatFormatting.DARK_GREEN));
		}
		sebut(npc, "hl.pil", null);
	}

	/** Ma Fu's wares today: three trade goods of other lands, at base price x1.1, and one rare good at x3 (once a visit). */
	private static List<DagangItems.Barang> dagangan(MinecraftServer server, long situs) {
		TemaKota.Kota lokal = TemaKota.dari(server, situs);
		List<DagangItems.Barang> asing = new ArrayList<>();
		for (DagangItems.Barang b : DagangItems.barang()) {
			if (!b.selundupan() && (lokal == null || b.asal() != lokal.tema())) {
				asing.add(b);
			}
		}
		asing.sort((a, b) -> Integer.compare(a.dasar(), b.dasar()));
		List<DagangItems.Barang> out = new ArrayList<>();
		if (asing.isEmpty()) {
			return out;
		}
		long seed = KalenderKota.hari(server) / 3L * 31L + situs;
		RandomSource r = RandomSource.create(seed);
		List<DagangItems.Barang> murah = new ArrayList<>(asing.subList(0, Math.max(1, asing.size() * 2 / 3)));
		while (out.size() < 3 && !murah.isEmpty()) {
			out.add(murah.remove(r.nextInt(murah.size())));
		}
		out.add(asing.get(asing.size() - 1 - r.nextInt(Math.min(3, asing.size()))));
		return out;
	}

	private static void barang(ServerPlayer player, Hadir h, KultivatorNpc npc, boolean langka) {
		MinecraftServer server = player.level().getServer();
		List<DagangItems.Barang> ware = dagangan(server, h.situs);
		if (ware.isEmpty()) {
			Msg.actionBar(player, Component.translatable(L + "mf.kosong").withStyle(ChatFormatting.GRAY));
			return;
		}
		DagangItems.Barang b = langka ? ware.getLast() : ware.get((int) Math.floorMod(KalenderKota.hari(server) + player.getUUID().hashCode(), 3L) % Math.max(1, ware.size() - 1));
		Item item = DagangItems.item(b.id());
		if (item == null) {
			return;
		}
		String kunci = h.situs + ":" + KalenderKota.hari(server) / 3L;
		int terjual = TERJUAL.getOrDefault(kunci + (langka ? ":l" : ""), 0);
		int jumlah = langka ? 1 : 4;
		int batas = langka ? 1 : 24;
		if (terjual >= batas) {
			sebut(npc, "mf.habis", null);
			return;
		}
		long harga = (long) Math.ceil(b.dasar() * jumlah * (langka ? 3.0D : 1.1D));
		if (!EkonomiApi.bayar(player, harga, "kafilah_ma")) {
			Msg.actionBar(player, Component.translatable(L + "tip.kurang", harga).withStyle(ChatFormatting.RED));
			return;
		}
		TERJUAL.put(kunci + (langka ? ":l" : ""), terjual + jumlah);
		PeristiwaJalan.beri(player, new ItemStack(item, jumlah));
		HidupApi.klip(npc, GerakId.TAWAR, PropId.SEMPOA, 40);
		sebut(npc, langka ? "mf.langka" : "mf.jual", null);
		Msg.actionBar(player, Component.translatable(L + "mf.beli", jumlah, new ItemStack(item).getHoverName(), harga).withStyle(ChatFormatting.GOLD));
	}

	/** The story effects {@code rombongan <verb>} of dialog data (42B-C): tip, lagu, plester, pil, bongkar, mitra, langka. */
	private static void efek(ServerPlayer player, List<String> args) {
		if (args.isEmpty() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		long situs = TempatDialog.situs(player);
		Map<String, Hadir> per = HADIR.get(situs);
		if (per == null) {
			return;
		}
		String verb = args.getFirst().toLowerCase(Locale.ROOT);
		Hadir h = switch (verb) {
			case "lagu" -> per.get(AH_QIN);
			case "plester", "pil", "bongkar", "mitra" -> per.get(HU_LU);
			case "langka", "barang" -> per.get(MA_FU);
			default -> per.values().stream().findFirst().orElse(null);
		};
		if (h == null || h.badan[0] == null || h.badan[0].isRemoved()) {
			return;
		}
		KultivatorNpc npc = h.badan[0];
		switch (verb) {
			case "tip" -> tip(player, h, npc, args.size() > 1 ? Math.max(1L, Math.min(100L, parse(args.get(1), TIP_BIASA))) : TIP_BIASA);
			case "lagu" -> lagu(player, h);
			case "plester" -> plester(player, h, npc);
			case "pil" -> pil(player, h, npc);
			case "langka" -> barang(player, h, npc, true);
			case "barang" -> barang(player, h, npc, false);
			case "bongkar" -> bongkar(player, h, npc);
			case "mitra" -> mitra(player, h, npc);
			default -> {
			}
		}
	}

	private static long parse(String s, long dflt) {
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			return dflt;
		}
	}

	/** An alchemist of level 2 sees through the pill: Nama Baik +3 here, Hu Lu leaves in disgrace (data only: gone for a week). */
	private static void bongkar(ServerPlayer player, Hadir h, KultivatorNpc npc) {
		if (Alchemy.level(Cultivators.get(player)) < 2) {
			Msg.chat(player, Component.translatable(L + "hl.bongkar.belum").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!sekali(player, "hl:bongkar", h.situs)) {
			return;
		}
		PeristiwaJalan.namaBaik(player, h.situs, 3);
		sebut(npc, "hl.bongkar.malu", null);
		Msg.chat(player, Component.translatable(L + "hl.bongkar.ok").withStyle(ChatFormatting.GOLD));
		CeritaApi.peristiwa(player, "rombongan_bongkar", HU_LU);
	}

	/** An alchemist of level 2 becomes a partner: a little profit each visit (NB scaled with his alchemy level). */
	private static void mitra(ServerPlayer player, Hadir h, KultivatorNpc npc) {
		int level = Alchemy.level(Cultivators.get(player));
		if (level < 2) {
			Msg.chat(player, Component.translatable(L + "hl.mitra.belum").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!sekali(player, "hl:mitra", h.situs)) {
			Msg.chat(player, Component.translatable(L + "hl.mitra.sudah").withStyle(ChatFormatting.GRAY));
			return;
		}
		long laba = 4L + level * 2L;
		EkonomiApi.beri(player, laba, "mitra_hu_lu");
		sebut(npc, "hl.mitra.laba", null, laba);
		Msg.chat(player, Component.translatable(L + "hl.mitra.ok", laba).withStyle(ChatFormatting.GOLD));
		CeritaApi.peristiwa(player, "rombongan_mitra", HU_LU);
	}

	// ================================================================== test commands (uji kota_hidup acara rombongan ...)

	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("rombongan").executes(Rombongan::ujiRingkas)
				.then(Commands.literal("datang").then(Commands.argument("id", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(ID, b)).executes(ctx -> ujiPaksa(ctx, false))))
				.then(Commands.literal("pentas").then(Commands.argument("id", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(ID, b)).executes(ctx -> ujiPaksa(ctx, true))))
				.then(Commands.literal("pergi").executes(ctx -> {
					PAKSA.clear();
					for (Map<String, Hadir> per : HADIR.values()) {
						for (Hadir h : per.values()) {
							bubar(h);
						}
					}
					HADIR.clear();
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.rombongan_pergi"), false);
					return 1;
				}))
				.then(Commands.literal("rute").executes(Rombongan::ujiRute));
	}

	private static int ujiRingkas(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		TempatInfo info = KalenderKota.tempatUji(ctx);
		if (info == null) {
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		List<String> ada = semuaDiKota(server, info.situsId());
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.rombongan", ada.size(),
				ada.isEmpty() ? "-" : String.join(", ", ada), sedangPentas(info.situsId()) ? "ya" : "tidak"), false);
		return ada.size();
	}

	private static int ujiPaksa(CommandContext<CommandSourceStack> ctx, boolean pentas) throws CommandSyntaxException {
		TempatInfo info = KalenderKota.tempatUji(ctx);
		if (info == null) {
			return 0;
		}
		String id = StringArgumentType.getString(ctx, "id");
		if (!ID.contains(id)) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.acara.uji.rombongan_tak_dikenal", id));
			return 0;
		}
		Grup g = grup(id);
		ServerLevel level = ctx.getSource().getServer().overworld();
		long now = level.getGameTime();
		PAKSA.put(info.situsId() + ":" + id, now + 6000L + g.lama());
		if (pentas && g.lama() > 0) {
			Map<String, Hadir> per = HADIR.computeIfAbsent(info.situsId(), k -> new HashMap<>());
			Hadir h = per.computeIfAbsent(id, k -> new Hadir(info.situsId(), g));
			h.paksaMulai = now;
			h.kunci = -1L;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.rombongan_datang", Component.translatable(L + "nama." + id)), false);
		return 1;
	}

	private static int ujiRute(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		long hari = KalenderKota.hari(server);
		List<Long> kota = kota(server);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.rombongan_rute", kota.size(), hari), false);
		for (Grup g : GRUP) {
			StringBuilder sb = new StringBuilder();
			for (int d = 0; d < 10; d++) {
				long s = lokasi(server, g, hari + d);
				sb.append(s == 0L ? "-" : Long.toHexString(s & 0xFFFFL)).append(' ');
			}
			String teks = sb.toString().trim();
			ctx.getSource().sendSuccess(() -> Component.literal("  " + g.id() + ": " + teks), false);
		}
		return kota.size();
	}

	private Rombongan() {
	}
}
