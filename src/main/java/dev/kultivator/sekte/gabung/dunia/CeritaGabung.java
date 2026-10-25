package dev.kultivator.sekte.gabung.dunia;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.Kultivator;
import dev.kultivator.alam.Alam;
import dev.kultivator.duel.ai.DuelStyle;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.cerita.Syarat;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kompleks.Kartu;
import dev.kultivator.dunia.tempat.kompleks.TandingTempat;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.diplomasi.GeneratorNama;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungCommands;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import dev.kultivator.sekte.gabung.masuk.GabungItems;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import dev.kultivator.sosok.RupaKunci;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The story chains of a member (DESIGN 41.6.13, owner 41W) on the 38.S story engine: the quest files
 * {@code data/kultivator/tempat/quest/gabung_*.json}, the conditions, effects and goal kinds they use, what starts each
 * chain, and the scenes (story cards with the speaker's real name) and choices of its stages.
 *
 * <h2>Chains ({@link #RANTAI})</h2>
 * Air dan Kayu Bakar (joined as Pelayan/Tercatat/Luar), Kebun Seratus Obat (became Luar, style not DARAH), Tanah Terlarang
 * Berdarah (first realm quota), Permintaan Guru (bond 30+: three variants by the Shifu's path and office), Perang Gerbang
 * (the first war call), Dua Topeng (righteous Dalam+, a demonic enemy exists, the Outer Elder asks), Gunung yang Bangkit
 * (the sect fell), Jubah dan Mangkuk (Heir or Vice Master), Tebus Dosa (expelled 30+ days, back at the gate).
 *
 * <h2>Conditions ({@code Syarat})</h2>
 * {@code gabung.pangkat <id>} (at least), {@code gabung.sekte [id]}, {@code gabung.kelas <1-5>}, {@code gabung.corak
 * <id[,id]>}, {@code gabung.aliran <lurus|netral|iblis>}, {@code gabung.ikatan <n>}, {@code gabung.status <id>},
 * {@code gabung.merosot}, {@code gabung.musuh}, {@code gabung.samaran}, {@code gabung.di_alam}.
 *
 * <h2>Effects ({@code Akibat})</h2>
 * {@code gabung.pk <n>} (x class factor), {@code gabung.pelanggaran <n>}, {@code gabung.sikap <rel> <n>},
 * {@code gabung.ikatan <n>}, {@code gabung.beri_token}, {@code gabung.naik_kursi <pangkat>}, {@code gabung.cerita <rantai>
 * <tahap> [rel] [corak]}, {@code gabung.pilihan <rantai> <tahap> <opsi>...}, {@code gabung.adv <name>},
 * {@code gabung.infami <n>}, {@code gabung.standing <n>}, {@code gabung.standing_musuh <n>}, {@code gabung.samaran
 * <mulai|bocor|ketahuan|berpaling|selesai>}, {@code gabung.bangkit}, {@code gabung.tanding <rel>}, {@code gabung.tebus_setor},
 * {@code gabung.tebus_selesai}, {@code gabung.sumber <n>}, {@code gabung.lukai <rel> <hari>}, {@code gabung.kabar
 * <templat>}. A relation {@code rel} is {@code shifu}, {@code kakak}, {@code saingan}, {@code ketua}, an office id
 * ({@code agung hukum luar ajar obat pusaka penerimaan}) or {@code penghina} (a stand-in of the enemy sect).
 *
 * <h2>Goal kinds ({@code QuestTracker})</h2>
 * {@code gabung_titik <titik>} (the member stands within 4 blocks of that titik of their own sect; {@code samaran:<titik>}
 * for the sect they infiltrate), {@code gabung <tanda>}: {@code temu:<rel>} (near or talking to that character's body),
 * {@code duel:<rel>}, {@code tanding:<rel>} (a story bout ended; flag {@code gabung.tanding.<rel>.menang}),
 * {@code pilih:<rantai>} (a choice was made; flag {@code gabung.<rantai>.<opsi>}), {@code perang:<bela|pos|duel|akhir>},
 * {@code tebus:setor}, {@code kuota:masuk}. Other modules fire their own through {@link #peristiwa}.
 *
 * <h2>Story cards</h2>
 * Every scene line {@code kultivator.gabung.cerita.<rantai>.<tahap>[.<varian>]} gets nine fixed arguments:
 * {@code %1$s} sect, {@code %2$s} Shifu, {@code %3$s} rival, {@code %4$s} eldest senior, {@code %5$s} Discipline Elder,
 * {@code %6$s} enemy sect, {@code %7$s} player, {@code %8$s} master, {@code %9$s} Outer Elder. The variant is the corak
 * ({@code pedang alkimia darah buddha}) or else the path ({@code lurus netral iblis}).
 */
public final class CeritaGabung {

	/** The free compound of this module in {@code Keanggotaan.bagian}. */
	public static final String BAGIAN = "dunia";
	/** Prefix of every chain quest id. */
	public static final String AWALAN = "gabung_";

	public static final String AIR_KAYU = "gabung_air_kayu";
	public static final String KEBUN = "gabung_kebun";
	public static final String TANAH_TERLARANG = "gabung_tanah_terlarang";
	public static final String GURU_HERBA = "gabung_guru_herba";
	public static final String GURU_SURAT = "gabung_guru_surat";
	public static final String GURU_DENDAM = "gabung_guru_dendam";
	public static final String PERANG_GERBANG = "gabung_perang_gerbang";
	public static final String DUA_TOPENG = "gabung_dua_topeng";
	public static final String GUNUNG_BANGKIT = "gabung_gunung_bangkit";
	public static final String JUBAH_MANGKUK = "gabung_jubah_mangkuk";
	public static final String TEBUS_DOSA = "gabung_tebus_dosa";
	public static final List<String> RANTAI = List.of(AIR_KAYU, KEBUN, TANAH_TERLARANG, GURU_HERBA, GURU_SURAT, GURU_DENDAM, PERANG_GERBANG,
			DUA_TOPENG, GUNUNG_BANGKIT, JUBAH_MANGKUK, TEBUS_DOSA);
	/** Days a Dua Topeng infiltration lasts (41.6.13). */
	public static final int SAMARAN_HARI = 10;
	/** The Tebus Dosa wait after an expulsion (41.7.5). */
	public static final int TEBUS_HARI = 30;
	/** Relations a story line may name. */
	public static final List<String> REL = List.of("shifu", "kakak", "saingan", "ketua", "agung", "hukum", "luar", "ajar", "obat", "pusaka",
			TokohSekte.PENERIMAAN, "penghina");
	private static final String K = "kultivator.gabung.cerita.";

	private static boolean initialised;

	/** From {@code DuniaGabung.init} (module on). */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		daftarSyarat();
		daftarAkibat();
		QuestTracker.daftarTujuan("gabung_titik", (a, j, id) -> j.equals("gabung_titik") && !a.isEmpty() && id.equals(a.get(0)));
		QuestTracker.daftarTujuan("gabung", (a, j, id) -> j.equals("gabung") && !a.isEmpty() && id.equals(a.get(0)));
		GabungBus.DITERIMA.register(CeritaGabung::diterima);
		GabungBus.PANGKAT_NAIK.register((player, sekte, lama, baru) -> pangkatNaik(player, sekte, baru));
		GabungBus.PERANG_MULAI.register(CeritaGabung::perangMulai);
		GabungBus.PERANG_AKHIR.register(CeritaGabung::perangAkhir);
		GabungBus.KELAS_BERUBAH.register(CeritaGabung::kelasBerubah);
		Keanggotaan.addPenyentuh(CeritaGabung::sentuh);
		DuelEndListeners.add((session, winner, loser) -> {
			if (winner instanceof ServerPlayer player && loser instanceof KultivatorNpc npc) {
				String rel = relasiTubuh(player, npc);
				if (rel != null) {
					peristiwa(player, "duel:" + rel);
				}
			}
		});
		GabungCommands.daftar("cerita", () -> Commands.literal("cerita")
				.executes(ctx -> daftarCerita(ctx.getSource()))
				.then(Commands.literal("pilih")
						.then(Commands.argument("rantai", StringArgumentType.word())
								.then(Commands.argument("opsi", StringArgumentType.word())
										.executes(ctx -> pilih(ctx, StringArgumentType.getString(ctx, "rantai"), StringArgumentType.getString(ctx, "opsi"))))))
				.then(Commands.literal("setor").executes(CeritaGabung::setorTebus))
				.then(Commands.literal("tanding").executes(CeritaGabung::tandingLagi)));
	}

	// ================================================================== public hooks for other modules

	/** A story event of another module (41C quota taken: {@code kuota:masuk}; 41D war missions: {@code perang:bela}...). */
	public static void peristiwa(ServerPlayer player, String tanda) {
		if (player != null && tanda != null) {
			CeritaApi.peristiwa(player, "gabung", tanda);
		}
	}

	/** 41C: the member received a realm quota of their sect (starts Tanah Terlarang Berdarah once). */
	public static void kuotaPertama(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		if (k.status.anggota()) {
			mulai(player, TANAH_TERLARANG);
		}
	}

	/** True when a Dua Topeng member leaked {@code lawan}'s plans to {@code sekte} in the last 30 days (41D: next war won). */
	public static boolean rahasiaBocor(MinecraftServer server, String sekte, String lawan) {
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(sekte);
		if (d == null) {
			return false;
		}
		CompoundTag b = d.bagian(BAGIAN);
		return lawan.equals(b.getStringOr("bocor", "")) && Kota.day(server) - b.getLongOr("bocorHari", -999L) <= 30L;
	}

	/** True when {@code player} paid for and won Tebus Dosa with {@code sekte} (41A {@code SyaratMasuk}: may apply again). */
	public static boolean sudahTebus(ServerPlayer player, String sekte) {
		return CeritaApi.bendera(player, "gabung.tebus." + sekte);
	}

	/** Starts chain {@code rantai} (a {@link #RANTAI} id) for {@code player}; false when unknown, running or done. */
	public static boolean mulai(ServerPlayer player, String rantai) {
		if (!GabungApi.aktif() || player == null || !RANTAI.contains(rantai)) {
			return false;
		}
		long situs = 0L;
		if (player.level() instanceof ServerLevel level) {
			TempatInfo info = TempatApi.tempatDi(level, player.blockPosition());
			situs = info == null ? 0L : info.situsId();
		}
		return CeritaApi.mulaiQuest(player, rantai, situs);
	}

	// ================================================================== triggers

	private static void diterima(ServerPlayer player, String sekte, Pangkat pangkat) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungData.get(server).anggota(player.getUUID());
		pilihRoster(server, player.getUUID(), sekte, k);
		GabungData.get(server).setDirty();
		if (!pangkat.minimal(Pangkat.DALAM) && pangkat != Pangkat.TAMU) {
			mulai(player, AIR_KAYU);
		}
	}

	private static void pangkatNaik(ServerPlayer player, String sekte, Pangkat baru) {
		if (baru == Pangkat.LUAR && GabungApi.corak(sekte) != Corak.DARAH) {
			mulai(player, KEBUN);
		}
		if (baru == Pangkat.PEWARIS || baru == Pangkat.WAKIL) {
			mulai(player, JUBAH_MANGKUK);
		}
		if (baru.minimal(Pangkat.DALAM)) {
			cobaDuaTopeng(player, GabungApi.anggota(player), true);
		}
	}

	private static void perangMulai(MinecraftServer server, String sekte, String lawan) {
		for (UUID id : GabungData.get(server).anggotaSekte(sekte)) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				mulai(p, PERANG_GERBANG);
			}
		}
	}

	private static void perangAkhir(MinecraftServer server, String sekte, String lawan, boolean menang) {
		for (UUID id : GabungData.get(server).anggotaSekte(sekte)) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				CeritaApi.setBendera(p, "gabung.perang_gerbang.menang", menang);
				CeritaApi.setBendera(p, "gabung.perang_gerbang.selesai", true);
				peristiwa(p, "perang:akhir");
			}
		}
	}

	private static void kelasBerubah(MinecraftServer server, String sekte, int lama, int baru) {
		if (baru >= lama) {
			return;
		}
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(sekte);
		if (d == null || !d.merosot) {
			return;
		}
		for (UUID id : GabungData.get(server).anggotaSekte(sekte)) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				mulai(p, GUNUNG_BANGKIT);
			}
		}
	}

	/** Daily lazy check (login, compound, screens): bond chains, the fallen sect, quotas, heir, infiltration end. */
	private static void sentuh(ServerPlayer player, Keanggotaan k, long hari) {
		if (!GabungApi.aktif() || !k.status.anggota()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (k.kakak.isEmpty() || k.saingan.isEmpty()) {
			pilihRoster(server, player.getUUID(), k.sekte, k);
		}
		if (k.status == StatusAnggota.MENYAMAR && k.samaranSampai > 0L && hari > k.samaranSampai) {
			akhiriSamaran(player, k);
			Msg.chat(player, Component.translatable(K + "samaran.habis", namaSekte(k.samaranSekte)).withStyle(ChatFormatting.GRAY));
		}
		if (!k.shifu.isEmpty() && k.ikatanShifu >= 30) {
			mulai(player, rantaiGuru(server, k));
		}
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(k.sekte);
		if (d != null && d.merosot) {
			mulai(player, GUNUNG_BANGKIT);
		}
		if (k.pangkat == Pangkat.PEWARIS || k.pangkat == Pangkat.WAKIL) {
			mulai(player, JUBAH_MANGKUK);
		}
		if (punyaKuota(player, k.sekte)) {
			mulai(player, TANAH_TERLARANG);
		}
		cobaDuaTopeng(player, k, false);
	}

	/** Permintaan Guru variant: demonic Shifu -&gt; an old grudge; medicine/pavilion office -&gt; a rare herb; else a letter. */
	static String rantaiGuru(MinecraftServer server, Keanggotaan k) {
		if (SekteApi.diplomasi().aliran(k.sekte) == 2) {
			return GURU_DENDAM;
		}
		TokohSekte.Tokoh shifu = TokohSekte.idSah(k.shifu) ? TokohSekte.tokoh(server, k.shifu) : null;
		if (shifu != null && ("obat".equals(shifu.kantor()) || "ajar".equals(shifu.kantor()) || shifu.punya(Sifat.RAJIN))) {
			return GURU_HERBA;
		}
		return shifu != null && shifu.punya(Sifat.PENDENDAM) ? GURU_DENDAM : GURU_SURAT;
	}

	/**
	 * Dua Topeng (41.6.13): a righteous member of rank Dalam+ whose sect has a demonic enemy; a BAYANGAN sect asks at once,
	 * any other when the Outer Elder picks them (one day in four, by player and day).
	 */
	private static void cobaDuaTopeng(ServerPlayer player, Keanggotaan k, boolean naik) {
		if (!k.status.anggota() || k.status == StatusAnggota.MENYAMAR || !k.pangkat.minimal(Pangkat.DALAM) || k.pangkat == Pangkat.TAMU) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (SekteApi.diplomasi().aliran(k.sekte) != 0 || musuh(server, k.sekte) == null || CeritaApi.tahap(player, DUA_TOPENG) != 0) {
			return;
		}
		long hari = Kota.day(server);
		boolean diminta = GabungApi.corak(k.sekte) == Corak.BAYANGAN
				|| Math.floorMod(TokohSekte.campur(player.getUUID().getMostSignificantBits(), hari), 4L) == 0L && !naik;
		if (diminta) {
			mulai(player, DUA_TOPENG);
		}
	}

	private static boolean punyaKuota(ServerPlayer player, String sekte) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && sekte.equals(stack.get(GabungItems.KUOTA_SEKTE))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Chooses the member's eldest senior (the roster's {@code kakak}) and main rival (an outer or inner disciple, the
	 * proudest and most ambitious; ties broken per player) once (41.6.12), into {@code Keanggotaan.kakak/saingan}.
	 */
	static void pilihRoster(MinecraftServer server, UUID player, String sekte, Keanggotaan k) {
		if (sekte == null || sekte.isEmpty()) {
			return;
		}
		List<Roster.Tokoh> roster = Roster.daftar(server, sekte);
		if (k.kakak.isEmpty()) {
			Roster.Tokoh best = null;
			for (Roster.Tokoh t : roster) {
				if (t.aktif() && (best == null || "kakak".equals(t.peran()) && !"kakak".equals(best.peran())
						|| t.peran().equals(best.peran()) && t.peranNomor() > best.peranNomor())) {
					best = t;
				}
			}
			if (best != null) {
				k.kakak = best.id();
			}
		}
		if (k.saingan.isEmpty()) {
			Roster.Tokoh best = null;
			long bestScore = Long.MIN_VALUE;
			for (Roster.Tokoh t : roster) {
				if (!t.aktif() || t.id().equals(k.kakak) || !("luar".equals(t.peran()) || "dalam".equals(t.peran()))) {
					continue;
				}
				long score = (t.punya(Sifat.SOMBONG) ? 300L : 0L) + (t.punya(Sifat.AMBISIUS) ? 300L : 0L) + (t.punya(Sifat.PENDENDAM) ? 100L : 0L)
						+ (t.punya(Sifat.LICIK) ? 100L : 0L) + Math.floorMod(TokohSekte.campur(player.getLeastSignificantBits(), t.slot()), 97L);
				if (score > bestScore) {
					bestScore = score;
					best = t;
				}
			}
			if (best != null) {
				k.saingan = best.id();
			}
		}
	}

	// ================================================================== names and relations

	/** The nearest sect of the opposite path to {@code sekte}'s gate (righteous/neutral -&gt; demonic, demonic -&gt; righteous). */
	public static @Nullable String musuh(MinecraftServer server, String sekte) {
		DiplomasiService d = SekteApi.diplomasi();
		if (sekte == null || d.tier(sekte) <= 0) {
			return null;
		}
		BlockPos at = d.gate(sekte) != null ? d.gate(sekte) : d.plannedGate(sekte);
		if (at == null) {
			return null;
		}
		String other = d.nearest(at, d.aliran(sekte) == 2 ? 0 : 2);
		return other == null || other.equals(sekte) ? null : other;
	}

	/** The character id behind relation {@code rel} for member {@code k} of {@code sekte} (null when nobody fits). */
	static @Nullable String idRelasi(MinecraftServer server, Keanggotaan k, String sekte, String rel) {
		return switch (rel) {
			case "shifu" -> k.shifu.isEmpty() ? null : k.shifu;
			case "kakak" -> k.kakak.isEmpty() ? null : k.kakak;
			case "saingan" -> k.saingan.isEmpty() ? null : k.saingan;
			case "penghina" -> null;
			default -> {
				TokohSekte.Tokoh t = TokohSekte.kantor(server, sekte, rel);
				yield t == null ? null : t.id();
			}
		};
	}

	/** The shown name of a character id (elder/master or roster member), or the generic word of relation {@code rel}. */
	static MutableComponent namaId(MinecraftServer server, @Nullable String id, String rel) {
		if (id != null) {
			if (TokohSekte.idSah(id)) {
				MutableComponent n = TokohSekte.nama(server, id);
				if (n != null) {
					return n;
				}
			} else {
				Roster.Tokoh t = Roster.tokoh(server, id);
				if (t != null) {
					return t.nama();
				}
			}
		}
		return Component.translatable(K + "anon." + rel);
	}

	/** The relation of {@code player} to an elder or master body ({@code shifu} first, then the office), or null. */
	static @Nullable String relasiTubuh(ServerPlayer player, KultivatorNpc npc) {
		String wakil = npc.memory().getStringOr("gabung_wakil", "");
		if (!wakil.isEmpty()) {
			return wakil;
		}
		String id = TokohAnchors.tokohDari(npc);
		if (id == null) {
			return null;
		}
		Keanggotaan k = GabungData.get(player.level().getServer()).peek(player.getUUID());
		if (k != null && id.equals(k.shifu)) {
			return "shifu";
		}
		return TokohAnchors.kantorDari(npc);
	}

	private static Component namaSekte(@Nullable String sekte) {
		Component n = sekte == null ? null : SekteApi.diplomasi().name(sekte);
		return n != null ? n : Component.translatable("kultivator.cerita.isian.sekte_gunung");
	}

	/** The sect a chain is about: the infiltrated one for samaran lines, the Tebus Dosa sect, else the member's sect. */
	static @Nullable String sekteCerita(ServerPlayer player, Keanggotaan k, String rantai) {
		if (TEBUS_DOSA.equals(rantai) || "tebus_dosa".equals(rantai)) {
			String s = k.bagian(BAGIAN).getStringOr("tebus", "");
			return s.isEmpty() ? null : s;
		}
		return k.sekte.isEmpty() ? null : k.sekte;
	}

	/** The nine fixed arguments of a story line (see the class comment). */
	static Object[] isian(ServerPlayer player, Keanggotaan k, @Nullable String sekte) {
		MinecraftServer server = player.level().getServer();
		String s = sekte == null ? "" : sekte;
		String m = musuh(server, s);
		return new Object[]{namaSekte(sekte), namaId(server, idRelasi(server, k, s, "shifu"), "shifu"),
				namaId(server, idRelasi(server, k, s, "saingan"), "saingan"), namaId(server, idRelasi(server, k, s, "kakak"), "kakak"),
				namaId(server, idRelasi(server, k, s, "hukum"), "hukum"), namaSekte(m), player.getName().copy(),
				namaId(server, idRelasi(server, k, s, "ketua"), "ketua"), namaId(server, idRelasi(server, k, s, "luar"), "luar")};
	}

	/** The scene variant of a sect: its corak for PEDANG/ALKIMIA/DARAH/BUDDHA, else its path. */
	static String varian(String sekte) {
		Corak c = GabungApi.corak(sekte);
		if (c == Corak.PEDANG || c == Corak.ALKIMIA || c == Corak.DARAH || c == Corak.BUDDHA) {
			return c.id();
		}
		return switch (SekteApi.diplomasi().aliran(sekte)) {
			case 0 -> "lurus";
			case 2 -> "iblis";
			default -> "netral";
		};
	}

	private static String pendek(String rantai) {
		return rantai.startsWith(AWALAN) ? rantai.substring(AWALAN.length()) : rantai;
	}

	// ================================================================== conditions

	private static void daftarSyarat() {
		Syarat.daftar("gabung.pangkat", (p, a) -> {
			Keanggotaan k = peek(p);
			Pangkat want = a.isEmpty() ? null : Pangkat.byId(a.get(0));
			return k != null && k.status.anggota() && want != null && k.pangkat.minimal(want);
		});
		Syarat.daftar("gabung.sekte", (p, a) -> {
			Keanggotaan k = peek(p);
			return k != null && k.status.anggota() && (a.isEmpty() || a.get(0).equals(k.sekte));
		});
		Syarat.daftar("gabung.kelas", (p, a) -> {
			Keanggotaan k = peek(p);
			return k != null && k.status.anggota() && GabungApi.kelas(k.sekte) >= num(a, 0, 1);
		});
		Syarat.daftar("gabung.corak", (p, a) -> {
			Keanggotaan k = peek(p);
			if (k == null || !k.status.anggota() || a.isEmpty()) {
				return false;
			}
			String mine = GabungApi.corak(k.sekte).id();
			for (String c : a.get(0).split(",")) {
				if (c.trim().equals(mine)) {
					return true;
				}
			}
			return false;
		});
		Syarat.daftar("gabung.aliran", (p, a) -> {
			Keanggotaan k = peek(p);
			if (k == null || !k.status.anggota() || a.isEmpty()) {
				return false;
			}
			int want = switch (a.get(0)) {
				case "lurus" -> 0;
				case "iblis" -> 2;
				default -> 1;
			};
			return SekteApi.diplomasi().aliran(k.sekte) == want;
		});
		Syarat.daftar("gabung.ikatan", (p, a) -> {
			Keanggotaan k = peek(p);
			return k != null && k.status.anggota() && k.ikatanShifu >= num(a, 0, 0);
		});
		Syarat.daftar("gabung.status", (p, a) -> {
			Keanggotaan k = peek(p);
			return k != null && !a.isEmpty() && k.status.id().equals(a.get(0));
		});
		Syarat.daftar("gabung.merosot", (p, a) -> {
			Keanggotaan k = peek(p);
			GabungData.DataSekteGabung d = k == null || k.sekte.isEmpty() ? null : GabungData.get(p.level().getServer()).peekSekte(k.sekte);
			return d != null && d.merosot;
		});
		Syarat.daftar("gabung.musuh", (p, a) -> {
			Keanggotaan k = peek(p);
			return k != null && k.status.anggota() && musuh(p.level().getServer(), k.sekte) != null;
		});
		Syarat.daftar("gabung.samaran", (p, a) -> {
			Keanggotaan k = peek(p);
			return k != null && k.status == StatusAnggota.MENYAMAR;
		});
		Syarat.daftar("gabung.di_alam", (p, a) -> p.level().dimension() == Alam.REALM_LEVEL);
	}

	// ================================================================== effects

	private static void daftarAkibat() {
		Akibat.daftar("gabung.pk", (p, a) -> {
			Keanggotaan k = peek(p);
			if (k != null && k.status.anggota()) {
				GabungApi.tambahPk(p, (int) Math.round(num(a, 0, 0) * GabungApi.kelasSekte(k.sekte).kf()), a.size() > 1 ? a.get(1) : "gabung.cerita");
			}
		});
		Akibat.daftar("gabung.pelanggaran", (p, a) -> {
			Keanggotaan k = peek(p);
			if (k != null && k.status.anggota()) {
				k.pelanggaran = Math.max(0, Math.min(100, k.pelanggaran + (int) num(a, 0, 0)));
				GabungData.get(p.level().getServer()).setDirty();
			}
		});
		Akibat.daftar("gabung.sikap", (p, a) -> {
			Keanggotaan k = peek(p);
			if (k == null || a.size() < 2) {
				return;
			}
			MinecraftServer server = p.level().getServer();
			String sekte = k.sekte.isEmpty() ? k.bagian(BAGIAN).getStringOr("tebus", "") : k.sekte;
			String id = idRelasi(server, k, sekte, a.get(0));
			int n = (int) num(a, 1, 0);
			if (id == null || n == 0) {
				return;
			}
			if (TokohSekte.idSah(id)) {
				TokohSekte.ubahSikap(server, id, p.getUUID(), n);
			} else {
				Roster.ubahSikap(server, id, p.getUUID(), n);
			}
			Msg.actionBar(p, Component.translatable(n > 0 ? K + "sikap_naik" : K + "sikap_turun", namaId(server, id, a.get(0)), Math.abs(n))
					.withStyle(n > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
		});
		Akibat.daftar("gabung.ikatan", (p, a) -> {
			Keanggotaan k = peek(p);
			if (k != null && k.status.anggota() && !k.shifu.isEmpty()) {
				k.ikatanShifu = Math.max(0, Math.min(100, k.ikatanShifu + (int) num(a, 0, 0)));
				GabungData.get(p.level().getServer()).setDirty();
			}
		});
		Akibat.daftar("gabung.beri_token", (p, a) -> {
			Keanggotaan k = peek(p);
			String sekte = k == null || k.sekte.isEmpty() ? SekteApi.diplomasi().nearest(p.blockPosition(), -1) : k.sekte;
			if (sekte == null) {
				return;
			}
			ItemStack token = new ItemStack(GabungItems.TOKEN_NAIK);
			token.set(GabungItems.SEKTE_TOKEN, new GabungItems.SekteToken(sekte, namaSekte(sekte)));
			if (!p.getInventory().add(token) && p.level() instanceof ServerLevel level) {
				p.spawnAtLocation(level, token);
			}
		});
		Akibat.daftar("gabung.naik_kursi", (p, a) -> {
			Pangkat pangkat = a.isEmpty() ? null : Pangkat.byId(a.get(0));
			if (pangkat != null) {
				GabungApi.setPangkat(p, pangkat, "gabung.cerita");
			}
		});
		Akibat.daftar("gabung.cerita", (p, a) -> {
			if (a.size() >= 2) {
				adegan(p, a.get(0), a.get(1), a.size() > 2 ? a.get(2) : "", a.contains("corak"));
			}
		});
		Akibat.daftar("gabung.pilihan", (p, a) -> {
			if (a.size() >= 4) {
				tawarkan(p, a.get(0), a.get(1), a.subList(2, a.size()));
			}
		});
		Akibat.daftar("gabung.adv", (p, a) -> {
			if (!a.isEmpty()) {
				Advancements.grant(p, "sekte/gabung_" + a.get(0));
			}
		});
		Akibat.daftar("gabung.infami", (p, a) -> {
			int n = (int) num(a, 0, 0);
			if (n != 0) {
				TemaApi.tambahInfami(p, n, "gabung.cerita");
			}
		});
		Akibat.daftar("gabung.standing", (p, a) -> {
			Keanggotaan k = peek(p);
			int n = (int) num(a, 0, 0);
			if (k != null && !k.sekte.isEmpty() && n != 0) {
				SekteApi.diplomasi().adjust(p.getUUID(), k.sekte, n, "gabung.cerita");
			}
		});
		Akibat.daftar("gabung.standing_musuh", (p, a) -> {
			Keanggotaan k = peek(p);
			String m = k == null || k.sekte.isEmpty() ? null : musuh(p.level().getServer(), k.sekte);
			int n = (int) num(a, 0, 0);
			if (m != null && n != 0) {
				SekteApi.diplomasi().adjust(p.getUUID(), m, n, "gabung.cerita");
			}
		});
		Akibat.daftar("gabung.samaran", (p, a) -> {
			if (!a.isEmpty()) {
				samaran(p, a.get(0));
			}
		});
		Akibat.daftar("gabung.bangkit", (p, a) -> bangkit(p));
		Akibat.daftar("gabung.tanding", (p, a) -> {
			if (!a.isEmpty()) {
				tanding(p, a.get(0));
			}
		});
		Akibat.daftar("gabung.tebus_setor", (p, a) -> tawarTebus(p));
		Akibat.daftar("gabung.tebus_selesai", (p, a) -> {
			Keanggotaan k = peek(p);
			String s = k == null ? "" : k.bagian(BAGIAN).getStringOr("tebus", "");
			if (!s.isEmpty()) {
				CeritaApi.setBendera(p, "gabung.tebus." + s, true);
				SekteApi.diplomasi().adjust(p.getUUID(), s, 10, "gabung.tebus");
			}
		});
		Akibat.daftar("gabung.sumber", (p, a) -> {
			Keanggotaan k = peek(p);
			int n = (int) num(a, 0, 0);
			if (k != null && !k.sekte.isEmpty() && n != 0) {
				DiplomasiGabung.ubahSumber(p.level().getServer(), k.sekte, n, "gabung.cerita");
			}
		});
		Akibat.daftar("gabung.lukai", (p, a) -> {
			Keanggotaan k = peek(p);
			if (k == null || a.isEmpty()) {
				return;
			}
			MinecraftServer server = p.level().getServer();
			String id = idRelasi(server, k, k.sekte, a.get(0));
			int hari = (int) num(a, 1, 3);
			if (id == null) {
				return;
			}
			if (TokohSekte.idSah(id)) {
				TokohSekte.lukai(server, id, TokohSekte.sampai(server, hari));
			} else {
				Roster.lukai(server, id, hari);
			}
		});
		Akibat.daftar("gabung.kabar", (p, a) -> {
			Keanggotaan k = peek(p);
			if (!a.isEmpty() && k != null && p.level() instanceof ServerLevel level) {
				String s = k.sekte.isEmpty() ? k.bagian(BAGIAN).getStringOr("tebus", "") : k.sekte;
				CeritaApi.kabar(level, null, a.get(0), p.getName().getString(), "sekte:" + s);
			}
		});
	}

	// ================================================================== scenes and choices

	/**
	 * A story card: title (the chain's name and the sect), the scene line of stage {@code tahap} (spoken by {@code rel}
	 * when given: "Name: line"), with the corak/path variant when {@code pakaiVarian}.
	 */
	static void adegan(ServerPlayer player, String rantai, String tahap, String rel, boolean pakaiVarian) {
		Keanggotaan k = GabungApi.anggota(player);
		String quest = rantai.startsWith(AWALAN) ? rantai : AWALAN + rantai;
		String sekte = sekteCerita(player, k, quest);
		MinecraftServer server = player.level().getServer();
		String key = K + pendek(quest) + "." + tahap + (pakaiVarian && sekte != null ? "." + varian(sekte) : "");
		Object[] isian = isian(player, k, sekte);
		Component line = Component.translatable(key, isian);
		List<Component> lines = new ArrayList<>();
		if (!rel.isEmpty() && REL.contains(rel) && !"corak".equals(rel)) {
			lines.add(Kartu.ucapan(namaId(server, sekte == null ? null : idRelasi(server, k, sekte, rel), rel), line));
		} else {
			lines.add(line);
		}
		Kartu.kirim(player, Component.translatable(K + "judul", Component.translatable("kultivator.cerita.quest." + quest + ".nama"), namaSekte(sekte)),
				lines, List.of());
	}

	/** A choice card: the question of the stage and one chip per option ({@code /kultivasi gabung cerita pilih ...}). */
	static void tawarkan(ServerPlayer player, String rantai, String tahap, List<String> opsi) {
		Keanggotaan k = GabungApi.anggota(player);
		String quest = rantai.startsWith(AWALAN) ? rantai : AWALAN + rantai;
		String r = pendek(quest);
		Object[] isian = isian(player, k, sekteCerita(player, k, quest));
		List<Component> chips = new ArrayList<>();
		for (String o : opsi) {
			chips.add(Kartu.chip(Component.translatable(K + r + ".pilih." + o, isian), "/kultivasi gabung cerita pilih " + r + " " + o,
					Component.translatable(K + r + ".pilih." + o + ".hover", isian), ChatFormatting.AQUA));
			CeritaApi.setBendera(player, "gabung." + r + "." + o, false);
		}
		CompoundTag pilihan = k.bagian(BAGIAN).getCompoundOrEmpty("pilihan");
		pilihan.putString(r, tahap + "|" + String.join(",", opsi));
		k.bagian(BAGIAN).put("pilihan", pilihan);
		GabungData.get(player.level().getServer()).setDirty();
		Kartu.kirim(player, Component.translatable(K + "judul_pilih", Component.translatable("kultivator.cerita.quest." + quest + ".nama")),
				List.of(Component.translatable(K + r + "." + tahap + ".tanya", isian)), chips);
	}

	private static int pilih(CommandContext<CommandSourceStack> ctx, String r, String opsi) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		Keanggotaan k = GabungApi.anggota(player);
		CompoundTag pilihan = k.bagian(BAGIAN).getCompoundOrEmpty("pilihan");
		String stored = pilihan.getStringOr(r, "");
		String offered = stored.substring(stored.indexOf('|') + 1);
		if (offered.isEmpty() || !List.of(offered.split(",")).contains(opsi) || !QuestTracker.aktif(player, AWALAN + r)) {
			ctx.getSource().sendFailure(Component.translatable(K + "pilih.tidak"));
			return 0;
		}
		pilihan.remove(r);
		k.bagian(BAGIAN).put("pilihan", pilihan);
		GabungData.get(player.level().getServer()).setDirty();
		CeritaApi.setBendera(player, "gabung." + r + "." + opsi, true);
		Msg.chat(player, Component.translatable(K + "pilih.diambil", Component.translatable(K + r + ".pilih." + opsi,
				isian(player, k, sekteCerita(player, k, AWALAN + r)))).withStyle(ChatFormatting.GOLD));
		peristiwa(player, "pilih:" + r);
		return 1;
	}

	private static int daftarCerita(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		int n = 0;
		source.sendSuccess(() -> Component.translatable(K + "daftar.judul").withStyle(ChatFormatting.GOLD), false);
		for (String q : RANTAI) {
			int tahap = CeritaApi.tahap(player, q);
			if (tahap == 0) {
				continue;
			}
			n++;
			Component baris = QuestTracker.baris(player, q);
			Component status = tahap > 0 && tahap < 999 ? (baris != null ? baris : Component.empty())
					: Component.translatable(tahap == 999 ? K + "daftar.selesai" : K + "daftar.gagal");
			source.sendSuccess(() -> Component.translatable(K + "daftar.baris", Component.translatable("kultivator.cerita.quest." + q + ".nama"), status)
					.withStyle(ChatFormatting.GRAY), false);
		}
		if (n == 0) {
			source.sendSuccess(() -> Component.translatable(K + "daftar.kosong").withStyle(ChatFormatting.GRAY), false);
		}
		// a choice card lost in the chat is shown again
		Keanggotaan k = GabungApi.anggota(player);
		CompoundTag pilihan = k.bagian(BAGIAN).getCompoundOrEmpty("pilihan");
		for (String r : List.copyOf(pilihan.keySet())) {
			String stored = pilihan.getStringOr(r, "");
			int bar = stored.indexOf('|');
			if (bar > 0 && QuestTracker.aktif(player, AWALAN + r)) {
				tawarkan(player, r, stored.substring(0, bar), List.of(stored.substring(bar + 1).split(",")));
			}
		}
		String rel = k.bagian(BAGIAN).getStringOr("tanding", "");
		if (!rel.isEmpty()) {
			Kartu.kirim(player, Component.translatable(K + "tanding.judul"), List.of(Component.translatable(K + "tanding.ulang.tanya",
					namaId(player.level().getServer(), null, rel))), List.of(Kartu.chip(Component.translatable(K + "tanding.ulang"),
					"/kultivasi gabung cerita tanding", Component.translatable(K + "tanding.ulang.hover"), ChatFormatting.GOLD)));
		}
		return 1;
	}

	/** {@code /kultivasi gabung cerita tanding}: the story bout that could not start (busy, no space) is tried again. */
	private static int tandingLagi(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		String rel = GabungApi.anggota(player).bagian(BAGIAN).getStringOr("tanding", "");
		if (rel.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable(K + "pilih.tidak"));
			return 0;
		}
		tanding(player, rel);
		return 1;
	}

	// ================================================================== Dua Topeng

	private static void samaran(ServerPlayer player, String langkah) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || !k.status.anggota()) {
			return;
		}
		long hari = Kota.day(server);
		DiplomasiService d = SekteApi.diplomasi();
		switch (langkah) {
			case "mulai" -> {
				String m = musuh(server, k.sekte);
				if (m == null || k.status == StatusAnggota.MENYAMAR) {
					return;
				}
				k.status = StatusAnggota.MENYAMAR;
				k.samaranSekte = m;
				k.samaranSampai = hari + SAMARAN_HARI;
				String alias = GeneratorNama.member(server.overworld().getSeed() ^ player.getUUID().getMostSignificantBits(), 7);
				k.bagian(BAGIAN).putString("samaran_nama", alias);
				k.catat(hari, "kultivator.gabung.riwayat.dunia_samaran", m, alias);
				data.setDirty();
				GabungApi.kenal(player, m);
				Msg.chat(player, Component.translatable(K + "samaran.mulai", namaSekte(m), Component.literal(alias).withStyle(ChatFormatting.LIGHT_PURPLE))
						.withStyle(ChatFormatting.DARK_PURPLE));
			}
			case "bocor" -> {
				String m = k.samaranSekte;
				akhiriSamaran(player, k);
				if (!m.isEmpty()) {
					CompoundTag b = data.sekte(k.sekte).bagian(BAGIAN);
					b.putString("bocor", m);
					b.putLong("bocorHari", hari);
					d.adjust(player.getUUID(), k.sekte, 15, "gabung.dua_topeng");
					d.adjust(player.getUUID(), m, -20, "gabung.dua_topeng");
					DiplomasiGabung.ubahSumber(server, k.sekte, 20, "gabung.dua_topeng");
				}
				Advancements.grant(player, "sekte/gabung_dua_topeng");
				data.setDirty();
			}
			case "ketahuan" -> {
				String m = k.samaranSekte;
				akhiriSamaran(player, k);
				if (!m.isEmpty()) {
					// the demonic sect treats the spy as its own deserter for ten days (41D's hunters read the record)
					k.riwayatSekte.put(m, new Keanggotaan.Riwayat(StatusKeluar.PEMBELOT, hari, 0));
					k.pemburuSampai = Math.max(k.pemburuSampai, hari + SAMARAN_HARI);
					d.adjust(player.getUUID(), m, -20, "gabung.dua_topeng");
				}
				data.setDirty();
			}
			case "berpaling" -> {
				String m = k.samaranSekte;
				akhiriSamaran(player, k);
				if (!m.isEmpty()) {
					GabungApi.keluar(player, StatusKeluar.PEMBELOT);
					GabungApi.terima(player, m, Pangkat.LUAR, Pintu.UNDANGAN);
				}
			}
			case "selesai" -> akhiriSamaran(player, k);
			default -> Kultivator.LOGGER.warn("Unknown Dua Topeng step {}", langkah);
		}
	}

	private static void akhiriSamaran(ServerPlayer player, Keanggotaan k) {
		if (k.status == StatusAnggota.MENYAMAR) {
			k.status = StatusAnggota.ANGGOTA;
		}
		k.samaranSampai = 0L;
		k.samaranSekte = "";
		k.bagian(BAGIAN).remove("samaran_nama");
		GabungData.get(player.level().getServer()).setDirty();
	}

	/** The false name a member uses while infiltrating, or null. */
	public static @Nullable String namaSamaran(Keanggotaan k) {
		if (k == null || k.status != StatusAnggota.MENYAMAR) {
			return null;
		}
		String n = k.bagian(BAGIAN).getStringOr("samaran_nama", "");
		return n.isEmpty() ? null : n;
	}

	// ================================================================== Gunung yang Bangkit, Tebus Dosa

	private static void bangkit(ServerPlayer player) {
		Keanggotaan k = peek(player);
		if (k == null || k.sekte.isEmpty()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.sekte(k.sekte);
		if (d.merosot) {
			// shortcut: the story ends MEROSOT directly; if 41D adds its own Bangkit door, call it here instead
			d.merosot = false;
			d.bangkitHari = Kota.day(server);
			data.setDirty();
		}
		DiplomasiGabung.ubahSumber(server, k.sekte, 40, "gabung.bangkit");
		Advancements.grant(player, "sekte/gabung_bangkit");
		if (player.level() instanceof ServerLevel level) {
			CeritaApi.kabar(level, null, KabarGabung.BANGKIT, player.getName().getString(), "sekte:" + k.sekte);
		}
	}

	/** The Tebus Dosa offer card: pay {@code 200 x class} NB (the value of {@code 100 x class} PK of goods) to the treasury. */
	private static void tawarTebus(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String s = k.bagian(BAGIAN).getStringOr("tebus", "");
		if (s.isEmpty()) {
			return;
		}
		long nb = biayaTebus(s);
		Kartu.kirim(player, Component.translatable(K + "judul_pilih", Component.translatable("kultivator.cerita.quest." + TEBUS_DOSA + ".nama")),
				List.of(Component.translatable(K + "tebus_dosa.setor.tanya", namaSekte(s), nb)),
				List.of(Kartu.chip(Component.translatable(K + "tebus_dosa.pilih.setor", nb), "/kultivasi gabung cerita setor",
						Component.translatable(K + "tebus_dosa.pilih.setor.hover"), ChatFormatting.GOLD)));
	}

	static long biayaTebus(String sekte) {
		return 200L * GabungApi.kelas(sekte);
	}

	private static int setorTebus(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) {
			return 0;
		}
		Keanggotaan k = GabungApi.anggota(player);
		String s = k.bagian(BAGIAN).getStringOr("tebus", "");
		if (s.isEmpty() || !QuestTracker.aktif(player, TEBUS_DOSA) || !"setor".equals(QuestTracker.simpul(player, TEBUS_DOSA))) {
			ctx.getSource().sendFailure(Component.translatable(K + "pilih.tidak"));
			return 0;
		}
		long nb = biayaTebus(s);
		if (!EkonomiApi.bayar(player, nb, "gabung.tebus")) {
			ctx.getSource().sendFailure(Component.translatable(K + "tebus_dosa.kurang", nb));
			return 0;
		}
		DiplomasiGabung.ubahSumber(player.level().getServer(), s, (int) Math.min(200L, nb / 10L), "gabung.tebus");
		peristiwa(player, "tebus:setor");
		return 1;
	}

	/** A player expelled from a sect 30+ days ago stands at its gate: Tebus Dosa starts (once). */
	static void cobaTebus(ServerPlayer player, Keanggotaan k, String sekte) {
		if (k.status.anggota() || CeritaApi.tahap(player, TEBUS_DOSA) != 0) {
			return;
		}
		Keanggotaan.Riwayat r = k.riwayatSekte.get(sekte);
		long hari = Kota.day(player.level().getServer());
		if (r == null || r.status() != StatusKeluar.DIUSIR || hari - r.hari() < TEBUS_HARI) {
			return;
		}
		k.bagian(BAGIAN).putString("tebus", sekte);
		GabungData.get(player.level().getServer()).setDirty();
		mulai(player, TEBUS_DOSA);
	}

	// ================================================================== story bouts

	/**
	 * A friendly story bout (Duel Pedang rules, stop at 10 %) against relation {@code rel}: the character's own body when it
	 * stands within 32 blocks, else a stand-in body with its name 4 blocks in front of the player (gone after the bout).
	 * The result sets flag {@code gabung.tanding.<rel>.menang} and fires {@code gabung tanding:<rel>}.
	 */
	static void tanding(ServerPlayer player, String rel) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekte.isEmpty() ? k.bagian(BAGIAN).getStringOr("tebus", "") : k.sekte;
		String id = sekte.isEmpty() ? null : idRelasi(server, k, sekte, rel);
		KultivatorNpc lawan = null;
		boolean wakil = false;
		if (id != null && TokohSekte.idSah(id)) {
			KultivatorNpc body = TokohAnchors.tubuh(level, sekte, id);
			if (body != null && body.distanceToSqr(player) <= 32.0D * 32.0D) {
				lawan = body;
			}
		}
		if (lawan == null) {
			lawan = wakil(level, player, sekte, rel, id);
			wakil = true;
		}
		if (lawan == null) {
			k.bagian(BAGIAN).putString("tanding", rel);
			GabungData.get(server).setDirty();
			Msg.chat(player, Component.translatable(K + "tanding.gagal").withStyle(ChatFormatting.RED));
			return;
		}
		DuelStyle gaya = switch (rel) {
			case "saingan" -> DuelStyle.AGRESIF;
			case "ketua" -> DuelStyle.SANTO_PEDANG;
			case "penghina" -> DuelStyle.PENIPU;
			default -> DuelStyle.BERTAHAN;
		};
		boolean standIn = wakil;
		KultivatorNpc lawanAkhir = lawan;
		k.bagian(BAGIAN).putString("tanding", rel);
		GabungData.get(server).setDirty();
		Component why = TandingTempat.mulai(player, lawan, gaya, GabungApi.corak(sekte).warna(), Items.IRON_SWORD, true, (pl, npc, menang, kalah) -> {
			CeritaApi.setBendera(pl, "gabung.tanding." + rel + ".menang", menang);
			GabungApi.anggota(pl).bagian(BAGIAN).remove("tanding");
			GabungData.get(pl.level().getServer()).setDirty();
			npc.say(Component.translatable(K + "tanding." + (menang ? "kalah" : "menang") + "." + rel), 100);
			if (standIn) {
				npc.memory().putBoolean("hold", true);
				npc.vanish();
			}
			peristiwa(pl, "tanding:" + rel);
		});
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			if (standIn) {
				lawanAkhir.discard();
			}
		}
	}

	/** A stand-in body for a story bout (the rival, the senior, an elder far away, the enemy sect's mocker). */
	private static @Nullable KultivatorNpc wakil(ServerLevel level, ServerPlayer player, String sekte, String rel, @Nullable String id) {
		MinecraftServer server = level.getServer();
		double a = Math.toRadians(player.getYRot());
		BlockPos at = BlockPos.containing(player.getX() - Math.sin(a) * 4.0D, player.getY(), player.getZ() + Math.cos(a) * 4.0D);
		BlockPos ground = TempatApi.groundSpot(level, at.getX(), at.getZ(), player.getBlockY());
		BlockPos pos = ground == null ? at : ground;
		boolean tetua = id != null && TokohSekte.idSah(id);
		int look = id == null ? (int) TokohSekte.campur(player.getUUID().getMostSignificantBits(), rel.hashCode())
				: tetua ? TokohSekte.look(server, id) : id.hashCode();
		TempatInfo info = TempatApi.tempatDi(level, pos);
		KultivatorNpc npc = HidupApi.tubuhStaf(level, info == null ? 0L : info.situsId(), tetua ? NpcRole.TETUA_SEKTE : NpcRole.MURID_SEKTE_DALAM,
				pos, player.getYRot() + 180.0F, look);
		if (npc == null) {
			return null;
		}
		String rupaSekte = "penghina".equals(rel) ? musuh(server, sekte) : sekte;
		if (rupaSekte != null && !rupaSekte.isEmpty()) {
			SosokApi.setRupa(npc, tetua ? new RupaKunci(TokohAnchors.RUPA_TETUA, "npc:" + rupaSekte, RupaKunci.TIDAK_ADA, RupaKunci.PANGKAT_MAKS).format()
					: RupaKunci.muridSekteNpc(3, rupaSekte).format());
		}
		Component name = "penghina".equals(rel) ? Component.translatable(K + "anon.penghina_dari", namaSekte(rupaSekte)) : namaId(server, id, rel);
		npc.setPlate(name);
		npc.memory().putString("gabung_wakil", rel);
		if (id != null && tetua) {
			npc.memory().putString("tokoh", id);
		}
		HidupApi.tatap(npc, player);
		return npc;
	}

	// ================================================================== helpers

	private static @Nullable Keanggotaan peek(ServerPlayer player) {
		return GabungData.get(player.level().getServer()).peek(player.getUUID());
	}

	private static long num(List<String> a, int i, long fallback) {
		if (i >= a.size()) {
			return fallback;
		}
		try {
			return Long.parseLong(a.get(i).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Lower-case id helper for test commands. */
	static String id(String s) {
		return s == null ? "" : s.toLowerCase(Locale.ROOT);
	}

	private CeritaGabung() {
	}
}
