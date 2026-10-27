package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.Kultivator;
import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarData;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import dev.kultivator.sekte.gabung.api.SyaratMasuk;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.dunia.TokohAnchors;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Stage 3, the heart interview (DESIGN 41.5.4 "Ujian Hati"), the servant door's one question and the guest trial of 41.8.3.
 * <p>
 * <b>Ujian Hati</b> in the main hall ({@code kompleks/aula}): the admissions elder ({@code TokohSekte.penerimaan}; his body
 * through {@code TokohAnchors.tubuh}, else a staff {@code TETUA_SEKTE} body for the day) asks three questions drawn from
 * the twelve of the sect's alignment (one of the three is swapped for one of the two about the sect's own art,
 * {@code gabung_wawancara_c_<corak>_1..2}), on the 38 {@code dialog} screen: the opening dialog
 * {@code gabung_wawancara_<aliran>} and the question dialogs {@code gabung_wawancara_<aliran>_01..12}. Every answer
 * runs effect {@code gabung.jawab <kesan> [sifat]} (impression -10..+15, a candidate trait {@code teguh}, {@code licik},
 * {@code welas}, {@code haus_kuasa} ...); the condition {@code gabung.soal <id>} keeps each question to its turn, so
 * right-clicking the elder again resumes it. Impression also from standing /10, the best capital Nama Baik /20, Infami by
 * alignment (Lurus -Infami/5, Iblis +Infami/5), Buronan Berat at a demonic sect +5, the Undangan door +15 and a deserter
 * of a sect of another alignment +10 (R12, shortcut: alignment stands in for enmity until D exposes NPC-NPC relations).
 * {@code S3 = clamp((kesan + 20) / 60, 0, 1)}.
 * <p>
 * <b>Pintu Pelayan</b>: one question of {@code gabung_wawancara_pelayan_1..4}; the servant door takes every honest
 * worker, the answer only sets the first impression. <b>Ujian Tamu</b> (dialog {@code gabung_ujian_tamu}): a friendly
 * Duel Pedang with the Grand Elder or master (lasting 60 s or taking half his health passes; a win passes) or an offering
 * of craft (a pill of High quality or better, an artifact of grade 3 or better, given up).
 */
public final class UjianHati {

	/** Questions per alignment, asked per interview, servant questions (41.5.4). */
	public static final int KOLAM = 12;
	public static final int DITANYA = 3;
	public static final int KOLAM_PELAYAN = 4;
	/** Questions per corak ({@code gabung_wawancara_c_<corak>_<1..2>}); one replaces a question of the alignment's pool. */
	public static final int KOLAM_CORAK = 2;
	/** An unanswered interview runs out after this long (each missing answer counts -5). */
	public static final int BATAS_TICK = 240 * 20;
	/** The guest duel passes after lasting this long. */
	public static final int TAMU_BERTAHAN_TICK = 60 * 20;

	static final String SOAL = "soal", KE = "ke", SOAL_ID = "soal_id", TETUA = "tetua", PELAYAN = "pelayan", TAMU = "tamu";
	static final String DUEL_MULAI = "duel_mulai", DUEL = "duel";

	private static final String[] ALIRAN = {"lurus", "netral", "iblis"};

	static void init() {
		DuelEndListeners.add(UjianHati::duelSelesai);
	}

	// ================================================================== start

	/** Starts the interview (or with {@code pelayan} the servant's one question). */
	static @Nullable Component mulai(ServerPlayer player, HariUjian.Sesi s, boolean pelayan) {
		KultivatorNpc tetua = tetua(player, s, false);
		if (tetua == null) {
			return Masuk.t("hati.tanpa_tetua");
		}
		s.data.putString(TETUA, tetua.getUUID().toString());
		s.data.putBoolean(PELAYAN, pelayan);
		Random rnd = new Random(s.pemain.getMostSignificantBits() ^ s.sekte.hashCode() * 31L ^ s.mulai);
		if (pelayan) {
			s.data.putIntArray(SOAL, new int[]{1 + rnd.nextInt(KOLAM_PELAYAN)});
		} else {
			int[] pick = new int[DITANYA];
			List<Integer> pool = new ArrayList<>();
			for (int i = 1; i <= KOLAM; i++) {
				pool.add(i);
			}
			for (int i = 0; i < DITANYA; i++) {
				pick[i] = pool.remove(rnd.nextInt(pool.size()));
			}
			// one of the three is about the sect's own art (41.5.4 interview, corak flavour): numbers above KOLAM
			pick[rnd.nextInt(DITANYA)] = KOLAM + 1 + rnd.nextInt(KOLAM_CORAK);
			s.data.putIntArray(SOAL, pick);
		}
		s.data.putInt(KE, 0);
		dekatkan(player, tetua);
		HidupApi.tatap(tetua, player);
		HidupApi.klip(tetua, GabungGerakId.TETUA_MENILAI, 0, 60);
		String dialog = pelayan ? idSoal(s, 0) : "gabung_wawancara_" + aliran(s);
		s.data.putString(SOAL_ID, pelayan ? dialog : "");
		Msg.chat(player, Masuk.t(pelayan ? "hati.mulai_pelayan" : "hati.mulai", namaTetua(player, s)).withStyle(ChatFormatting.GOLD));
		if (!TempatDialog.buka(player, tetua, dialog)) {
			Kultivator.LOGGER.warn("Gabung: interview dialog {} missing", dialog);
			return Masuk.t("hati.tanpa_dialog");
		}
		return null;
	}

	/** Starts the guest trial: the Grand Elder (else the master, else the admissions elder) offers duel or offering. */
	static @Nullable Component mulaiTamu(ServerPlayer player, HariUjian.Sesi s) {
		KultivatorNpc tetua = tetua(player, s, true);
		if (tetua == null) {
			return Masuk.t("hati.tanpa_tetua");
		}
		s.data.putString(TETUA, tetua.getUUID().toString());
		s.data.putBoolean(TAMU, true);
		dekatkan(player, tetua);
		HidupApi.tatap(tetua, player);
		Msg.chat(player, Masuk.t("tamu.mulai", namaTetua(player, s)).withStyle(ChatFormatting.GOLD));
		if (!TempatDialog.buka(player, tetua, "gabung_ujian_tamu")) {
			return Masuk.t("hati.tanpa_dialog");
		}
		return null;
	}

	private static String aliran(HariUjian.Sesi s) {
		return ALIRAN[Math.max(0, Math.min(2, s.aliran))];
	}

	/** The dialog id of question {@code n} (0-based) of the session. */
	static String idSoal(HariUjian.Sesi s, int n) {
		int[] soal = s.data.getIntArray(SOAL).orElse(new int[0]);
		int q = n >= 0 && n < soal.length ? soal[n] : 1;
		if (s.data.getBooleanOr(PELAYAN, false)) {
			return "gabung_wawancara_pelayan_" + q;
		}
		if (q > KOLAM) {
			return "gabung_wawancara_c_" + s.corak.id() + "_" + Math.min(KOLAM_CORAK, q - KOLAM);
		}
		return "gabung_wawancara_" + aliran(s) + "_" + (q < 10 ? "0" : "") + q;
	}

	/** Question number 1-3 being asked (0 before the first). */
	static int soalKe(HariUjian.Sesi s) {
		String id = s.data.getStringOr(SOAL_ID, "");
		return id.isEmpty() ? 0 : s.data.getIntOr(KE, 0) + 1;
	}

	static int sisaTick(HariUjian.Sesi s, long now) {
		return (int) Math.max(0L, BATAS_TICK - s.umurTahap(now));
	}

	// ================================================================== story hooks (MasukCerita)

	/** Condition {@code gabung.soal <id>}: true while that question is the player's turn. */
	static boolean soalIni(ServerPlayer player, String id) {
		HariUjian.Sesi s = HariUjian.sesi(player);
		return s != null && !s.selesai && id.equals(s.data.getStringOr(SOAL_ID, ""));
	}

	/** Condition {@code gabung.wawancara}: true while an interview runs and no question was asked yet. */
	static boolean pembuka(ServerPlayer player) {
		HariUjian.Sesi s = HariUjian.sesi(player);
		return s != null && !s.selesai && s.tahap == HariUjian.TAHAP_HATI && s.data.getStringOr(SOAL_ID, "").isEmpty();
	}

	/** Effect {@code gabung.wawancara}: the opening ended, the first question is asked. */
	static void lanjutkan(ServerPlayer player) {
		HariUjian.Sesi s = HariUjian.sesi(player);
		if (s == null || s.selesai || s.tahap != HariUjian.TAHAP_HATI) {
			return;
		}
		tanya(player, s, 0);
	}

	/** Effect {@code gabung.jawab <kesan> [sifat]}: records the answer and asks the next question or ends the stage. */
	static void jawab(ServerPlayer player, List<String> args) {
		HariUjian.Sesi s = HariUjian.sesi(player);
		if (s == null || s.selesai || s.data.getStringOr(SOAL_ID, "").isEmpty()) {
			return;
		}
		int kesan = 0;
		try {
			kesan = args.isEmpty() ? 0 : Integer.parseInt(args.getFirst());
		} catch (NumberFormatException ignored) {
			// a malformed number counts as a neutral answer
		}
		s.kesan += Math.max(-10, Math.min(15, kesan));
		if (args.size() > 1 && !s.sifat.contains(args.get(1))) {
			s.sifat.add(args.get(1));
		}
		int ke = s.data.getIntOr(KE, 0) + 1;
		s.data.putInt(KE, ke);
		int jumlah = s.data.getIntArray(SOAL).map(a -> a.length).orElse(0);
		if (ke < jumlah) {
			tanya(player, s, ke);
		} else {
			selesai(player, s);
		}
	}

	private static void tanya(ServerPlayer player, HariUjian.Sesi s, int n) {
		String id = idSoal(s, n);
		s.data.putString(SOAL_ID, id);
		KultivatorNpc tetua = tubuhTetua(player, s);
		if (tetua == null || !TempatDialog.buka(player, tetua, id)) {
			Msg.chat(player, Masuk.t("hati.tetua_hilang").withStyle(ChatFormatting.GRAY));
		}
	}

	/** Effect {@code gabung.tamu duel|karya}: the guest trial's choice. */
	static void tamu(ServerPlayer player, List<String> args) {
		HariUjian.Sesi s = HariUjian.sesi(player);
		if (s == null || s.selesai || s.tahap != HariUjian.TAHAP_TAMU) {
			return;
		}
		KultivatorNpc tetua = tubuhTetua(player, s);
		String pilih = args.isEmpty() ? "" : args.getFirst();
		if ("karya".equals(pilih)) {
			ItemStack hand = player.getMainHandItem();
			boolean pil = hand.has(AlchemyComponents.PILL_QUALITY) && AlchemyComponents.quality(hand).ordinal() >= PillQuality.HIGH.ordinal();
			boolean pusaka = hand.getItem() instanceof ArtifactItem && ArtifactItem.grade(ArtifactItem.data(hand)) >= 3;
			if (!pil && !pusaka) {
				Msg.chat(player, Masuk.t("tamu.karya_kurang").withStyle(ChatFormatting.RED));
				return;
			}
			Component name = hand.getHoverName();
			hand.shrink(1);
			player.setItemInHand(InteractionHand.MAIN_HAND, hand);
			Msg.chat(player, Masuk.t("tamu.karya_diterima", name).withStyle(ChatFormatting.GREEN));
			TempatDialog.akhiri(player);
			s.s3 = 1.0D;
			s.kesan += 15;
			HariUjian.selesaiTahap(player, s, 1.0D);
		} else if ("duel".equals(pilih)) {
			if (tetua == null) {
				Msg.chat(player, Masuk.t("hati.tetua_hilang").withStyle(ChatFormatting.GRAY));
				return;
			}
			TempatDialog.akhiri(player);
			Component why = DuelChallenges.startDirect(player, player, tetua, DuelIds.PAIR_NPC, DuelRuleSet.defaults());
			if (why != null) {
				Msg.chat(player, Masuk.t("tamu.duel_gagal", why).withStyle(ChatFormatting.RED));
				return;
			}
			s.data.putLong(DUEL_MULAI, player.level().getGameTime());
			s.data.putBoolean(DUEL, true);
			Msg.chat(player, Masuk.t("tamu.duel_mulai").withStyle(ChatFormatting.GOLD));
		}
	}

	private static void duelSelesai(dev.kultivator.duel.session.DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		ServerPlayer player = winner instanceof ServerPlayer w ? w : loser instanceof ServerPlayer l ? l : null;
		if (player == null) {
			return;
		}
		HariUjian.Sesi s = HariUjian.sesi(player);
		if (s == null || s.selesai || s.tahap != HariUjian.TAHAP_TAMU || !s.data.getBooleanOr(DUEL, false)) {
			return;
		}
		s.data.putBoolean(DUEL, false);
		long lama = player.level().getGameTime() - s.data.getLongOr(DUEL_MULAI, player.level().getGameTime());
		LivingEntity lawan = winner == player ? loser : winner;
		boolean separuh = lawan != null && lawan.getHealth() <= lawan.getMaxHealth() * 0.5F;
		boolean lulus = winner == player || lama >= TAMU_BERTAHAN_TICK || separuh;
		// queue the verdict for the next tick: a duel listener must not start new work (DuelEndListeners contract)
		s.data.putBoolean("tamu_hasil", true);
		s.data.putBoolean("tamu_lulus", lulus);
	}

	/** The interview's end: impression terms, S3, the elder's look, then the verdict. */
	private static void selesai(ServerPlayer player, HariUjian.Sesi s) {
		TempatDialog.akhiri(player);
		boolean pelayan = s.data.getBooleanOr(PELAYAN, false);
		int total = s.kesan + tambahan(player, s);
		s.kesan = total;
		s.s3 = pelayan ? Math.max(0.5D, HariUjian.clamp((total + 20) / 60.0D)) : HariUjian.clamp((total + 20) / 60.0D);
		s.data.putString(SOAL_ID, "");
		KultivatorNpc tetua = tubuhTetua(player, s);
		if (tetua != null) {
			HidupApi.klip(tetua, GabungGerakId.TETUA_MENILAI, 0, 60);
			tetua.say(Masuk.t(s.s3 >= 0.6D ? "hati.tetua_puas" : s.s3 >= 0.35D ? "hati.tetua_ragu" : "hati.tetua_kecewa"), 100);
		}
		HariUjian.selesaiTahap(player, s, s.s3);
	}

	/** The impression terms besides the answers (41.5.4). */
	static int tambahan(ServerPlayer player, HariUjian.Sesi s) {
		MinecraftServer server = player.level().getServer();
		int t = 0;
		int standing = SekteApi.diplomasi().standing(player.getUUID(), s.sekte);
		t += Math.round(standing / 10.0F);
		t += Math.round(namaBaik(server, player.getUUID()) / 20.0F);
		int infami = TemaApi.infami(player);
		if (s.aliran == 0) {
			t -= Math.round(infami / 5.0F);
		} else if (s.aliran == 2) {
			t += Math.round(infami / 5.0F);
			if (SyaratMasuk.kejahatanTerburuk(server, player) >= 3) {
				t += 5;
			}
		}
		if (s.pintu == Pintu.UNDANGAN) {
			t += 15;
		}
		Keanggotaan k = GabungApi.anggota(player);
		for (var e : k.riwayatSekte.entrySet()) {
			if (e.getValue().status() == StatusKeluar.PEMBELOT && SekteApi.diplomasi().aliran(e.getKey()) != s.aliran) {
				t += 10;
				break;
			}
		}
		return t;
	}

	/** The best Nama Baik the player holds in a known capital (0 when none). */
	static int namaBaik(@Nullable MinecraftServer server, UUID player) {
		if (server == null) {
			return 0;
		}
		int best = Integer.MIN_VALUE;
		KotaData kota = KotaData.get(server);
		for (long town : KotaBesarData.get(server).kotaIds()) {
			best = Math.max(best, kota.reputation(town, player));
		}
		return best == Integer.MIN_VALUE ? 0 : best;
	}

	/**
	 * Ujian Darah (41.4.4): passing a RACUN or BAYANGAN style test raises Infami by 10 (DARAH's Kolam Darah adds its own
	 * +10 inside the 41B-C test, so it is not counted twice).
	 */
	static void ujianDarah(ServerPlayer player, HariUjian.Sesi s) {
		if (s.s2 >= 0.5D && s.corak != dev.kultivator.sekte.gabung.api.Corak.DARAH) {
			TemaApi.tambahInfami(player, SyaratMasuk.INFAMI_UJIAN_DARAH, "gabung.ujian_darah");
			Msg.chat(player, Masuk.t("hati.ujian_darah", SyaratMasuk.INFAMI_UJIAN_DARAH).withStyle(ChatFormatting.DARK_RED));
		}
	}

	// ================================================================== tick

	static void tick(ServerPlayer p, HariUjian.Sesi s, long now) {
		if (s.tahap == HariUjian.TAHAP_TAMU && s.data.getBooleanOr("tamu_hasil", false)) {
			s.data.putBoolean("tamu_hasil", false);
			boolean lulus = s.data.getBooleanOr("tamu_lulus", false);
			s.s3 = lulus ? 1.0D : 0.0D;
			Msg.chat(p, Masuk.t(lulus ? "tamu.duel_lulus" : "tamu.duel_kalah").withStyle(lulus ? ChatFormatting.GREEN : ChatFormatting.RED));
			HariUjian.selesaiTahap(p, s, s.s3);
			return;
		}
		if (s.data.getBooleanOr(DUEL, false)) {
			return;
		}
		long age = s.umurTahap(now);
		if (age >= BATAS_TICK) {
			if (s.tahap == HariUjian.TAHAP_TAMU) {
				TempatDialog.akhiri(p);
				s.s3 = 0.0D;
				Msg.chat(p, Masuk.t("tamu.waktu_habis").withStyle(ChatFormatting.RED));
				HariUjian.selesaiTahap(p, s, 0.0D);
				return;
			}
			int jumlah = s.data.getIntArray(SOAL).map(a -> a.length).orElse(0);
			int sisa = Math.max(0, jumlah - s.data.getIntOr(KE, 0));
			s.kesan -= 5 * sisa;
			Msg.chat(p, Masuk.t("hati.waktu_habis").withStyle(ChatFormatting.RED));
			s.data.putInt(KE, jumlah);
			selesai(p, s);
			return;
		}
		// the elder keeps his eyes on the candidate; a reminder when the screen was closed
		if (age % 200L == 100L) {
			KultivatorNpc tetua = tubuhTetua(p, s);
			if (tetua != null) {
				HidupApi.tatap(tetua, p);
				if (!dev.kultivator.dunia.tempat.ui.LembarKunci.Dialog.LAYAR.equals(dev.kultivator.dunia.tempat.ui.TempatLayar.layarTerbuka(p))) {
					Msg.actionBar(p, Masuk.t("hati.ingat", namaTetua(p, s)).withStyle(ChatFormatting.YELLOW));
				}
			}
		}
	}

	/** Ends the stage's leftovers (closes the talk; the day's elder body goes home with the other bodies). */
	static void bersihkan(ServerPlayer p, HariUjian.Sesi s) {
		if (s.tahap == HariUjian.TAHAP_HATI || s.tahap == HariUjian.TAHAP_WAWANCARA || s.tahap == HariUjian.TAHAP_TAMU) {
			if (!s.data.getStringOr(TETUA, "").isEmpty()) {
				TempatDialog.akhiri(p);
			}
		}
	}

	// ================================================================== the elder

	/** The elder who examines: his named body, else a staff elder for the day at the main hall. */
	static @Nullable KultivatorNpc tetua(ServerPlayer player, HariUjian.Sesi s, boolean tamu) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level();
		TokohSekte.Tokoh tokoh = tamu ? tamuPenguji(server, s.sekte) : TokohSekte.penerimaan(server, s.sekte);
		if (tokoh != null) {
			KultivatorNpc body = TokohAnchors.tubuh(level, s.sekte, tokoh.id());
			if (body != null && body.distanceTo(player) < HariUjian.JARI_WILAYAH) {
				s.data.putString("tokoh", tokoh.id());
				return body;
			}
		}
		BlockPos aula = GabungApi.titik(s.sekte, TitikGabung.AULA);
		if (aula == null || !level.isLoaded(aula) || Masuk.datar(player.position(), aula) > HariUjian.JARI_WILAYAH || aula.getY() <= 0) {
			aula = player.blockPosition();
		}
		int look = tokoh != null ? TokohSekte.look(server, tokoh.id()) : s.sekte.hashCode() * 977 + (tamu ? 13 : 7);
		KultivatorNpc npc = HidupApi.tubuhStaf(level, Masuk.situs(level, aula), NpcRole.TETUA_SEKTE, aula, player.getYRot() + 180.0F, look);
		if (npc == null) {
			return null;
		}
		npc.memory().putBoolean(Masuk.MEM_CALON, true);
		npc.memory().putString(Masuk.MEM_SEKTE, s.sekte);
		npc.memory().putBoolean("hold", true);
		npc.setPlate(tokoh != null ? namaTokoh(server, tokoh) : Masuk.t(tamu ? "hati.plat_tamu" : "hati.plat_penerimaan"));
		Masuk.rupa(npc, TokohAnchors.RUPA_TETUA, s.sekte);
		HidupApi.klip(npc, GerakId.SIKAP_TANGAN_BELAKANG, 0, 0);
		if (tokoh != null) {
			s.data.putString("tokoh", tokoh.id());
		}
		s.ikat(npc);
		return npc;
	}

	private static @Nullable TokohSekte.Tokoh tamuPenguji(MinecraftServer server, String sekte) {
		TokohSekte.Tokoh t = TokohSekte.jabatan(server, sekte, Jabatan.TETUA_AGUNG);
		if (t == null) {
			t = TokohSekte.ketua(server, sekte);
		}
		return t != null ? t : TokohSekte.penerimaan(server, sekte);
	}

	private static Component namaTokoh(MinecraftServer server, TokohSekte.Tokoh t) {
		Component n = TokohSekte.nama(server, t.id());
		return n != null ? n : Component.literal(t.nama());
	}

	static Component namaTetua(ServerPlayer player, HariUjian.Sesi s) {
		String id = s.data.getStringOr("tokoh", "");
		Component n = id.isEmpty() ? null : TokohSekte.nama(player.level().getServer(), id);
		return n != null ? n : Masuk.t("hati.plat_penerimaan");
	}

	static @Nullable KultivatorNpc tubuhTetua(ServerPlayer player, HariUjian.Sesi s) {
		String id = s.data.getStringOr(TETUA, "");
		if (id.isEmpty()) {
			return null;
		}
		try {
			Entity e = player.level().getEntity(UUID.fromString(id));
			return e instanceof KultivatorNpc npc && npc.isAlive() ? npc : null;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/** Brings the candidate before the elder (3 blocks in front) when farther than 10 blocks. */
	private static void dekatkan(ServerPlayer player, KultivatorNpc tetua) {
		if (player.distanceTo(tetua) <= 10.0F) {
			return;
		}
		Vec3 look = Vec3.directionFromRotation(0.0F, tetua.getYRot()).scale(3.0D);
		Vec3 at = tetua.position().add(look.x, 0.0D, look.z);
		player.teleportTo(at.x, tetua.getY(), at.z);
	}

	private UjianHati() {
	}
}
