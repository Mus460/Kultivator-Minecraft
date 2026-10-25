package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.RootGrade;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Stage 1, the Test Stair (DESIGN 41.5.4 "Tangga Ujian"; Han Li's climb at the Seven Mysteries Sect): the candidate climbs
 * the compound's mountain stair from {@code kompleks/ujian_bawah} to {@code kompleks/ujian_atas} (without a compound the
 * gate stair {@code sektenpc/ujian_bawah|ujian_atas}) within {@code gabungSekte.waktuTangga} seconds (300).
 * <p>
 * <b>Mountain Pressure</b> 0-100 (the jade-red HUD bar): +0.6/s standing on the stair, +0.25/s climbing, +8 for each of
 * the three illusions (at 25/50/75 % of the height: a heart-demon face and a gust, drawn by the client from the HUD
 * byte), -1.5/s while crouching still for 2 s or more (meditating on the step). It gives Slowness I at 40 and II at 70;
 * 100 = the candidate falls to the knees (clip {@code lelah_berlutut}) and the stage fails (S1 0; the day goes on).
 * <p>
 * {@code S1 = clamp(1 - time/limit) x 0.5 + (1 - peakPressure/100) x 0.5}, times {@code 0.85 + 0.05 x root} (TRASH 0 ..
 * HEAVENLY 4), capped at 1. Two to four child candidates of the nearest villages walk the stair with the player
 * ({@code ANAK}/{@code PETANI} bodies, the town's names), each stopping and kneeling at a step of its own.
 */
public final class TanggaUjian {

	/** Pressure rates per second and per illusion (41.5.4). */
	public static final double DIAM_PER_DETIK = 0.6D;
	public static final double NAIK_PER_DETIK = 0.25D;
	public static final double ILUSI = 8.0D;
	public static final double MEDITASI_PER_DETIK = 1.5D;
	/** Crouching still this long counts as meditating. */
	public static final int MEDITASI_TICK = 40;
	/** Slowness I and II thresholds; collapse. */
	public static final double LAMBAT_1 = 40.0D;
	public static final double LAMBAT_2 = 70.0D;
	public static final double ROBOH = 100.0D;
	/** Standing within this many blocks of the top gate (and not below it) ends the climb. */
	public static final double JARI_PUNCAK = 3.0D;
	/** Child candidates (41.5.3: 2-6 in data, at most 4 bodies). */
	public static final int CALON_MAKS = 4;

	/** A stair needs at least this rise or this run to be a climb. */
	private static final double NAIK_MIN = 4.0D;
	private static final double JALAN_MIN = 10.0D;

	// scratch keys in Sesi.data
	private static final String BX = "bx", BY = "by", BZ = "bz", AX = "ax", AY = "ay", AZ = "az";
	private static final String TEKANAN = "tekanan", ILUSI_N = "ilusi", JONGKOK = "jongkok", LAMBAT = "lambat";
	private static final String Y_LALU = "y_lalu", X_LALU = "x_lalu", Z_LALU = "z_lalu", ROBOH_SAMPAI = "roboh_sampai";

	static void init() {
	}

	/** Starts stage 1: finds the stair, places the candidate at its foot, brings the child candidates. */
	static @Nullable Component mulai(ServerPlayer player, HariUjian.Sesi s) {
		BlockPos[] stair = tangga(s.sekte);
		if (stair == null) {
			return Masuk.t("tangga.tidak_ada");
		}
		BlockPos bawah = stair[0];
		BlockPos atas = stair[1];
		if (!player.level().isLoaded(bawah) || !player.level().isLoaded(atas)) {
			return Masuk.t("tangga.belum_dimuat");
		}
		s.data.putInt(BX, bawah.getX());
		s.data.putInt(BY, bawah.getY());
		s.data.putInt(BZ, bawah.getZ());
		s.data.putInt(AX, atas.getX());
		s.data.putInt(AY, atas.getY());
		s.data.putInt(AZ, atas.getZ());
		s.data.putDouble(TEKANAN, 0.0D);
		Vec3 foot = Vec3.atBottomCenterOf(bawah);
		player.teleportTo(foot.x, foot.y, foot.z);
		s.terakhir = player.position();
		s.data.putDouble(Y_LALU, player.getY());
		s.data.putDouble(X_LALU, player.getX());
		s.data.putDouble(Z_LALU, player.getZ());
		int detik = GabungConfig.get().waktuTangga;
		Msg.chat(player, Masuk.t("tangga.mulai", detik).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Masuk.t("tangga.petunjuk").withStyle(ChatFormatting.GRAY));
		calon(player.level(), s, bawah, atas);
		return null;
	}

	/** Foot and top of the sect's test stair, or null when there is no sane stair. */
	static BlockPos @Nullable [] tangga(String sekte) {
		BlockPos bawah = TitikGabung.kompleks(sekte, TitikGabung.UJIAN_BAWAH);
		BlockPos atas = TitikGabung.kompleks(sekte, TitikGabung.UJIAN_ATAS);
		if (layak(bawah, atas)) {
			return new BlockPos[]{bawah, atas};
		}
		bawah = GabungApi.titik(sekte, TitikGabung.GERBANG_UJIAN_BAWAH);
		atas = GabungApi.titik(sekte, TitikGabung.GERBANG_UJIAN_ATAS);
		return layak(bawah, atas) ? new BlockPos[]{bawah, atas} : null;
	}

	private static boolean layak(@Nullable BlockPos bawah, @Nullable BlockPos atas) {
		if (bawah == null || atas == null || bawah.getY() <= 0 || atas.getY() <= 0) {
			return false;
		}
		double rise = atas.getY() - bawah.getY();
		double run = Math.sqrt(Math.pow(atas.getX() - bawah.getX(), 2) + Math.pow(atas.getZ() - bawah.getZ(), 2));
		return run < 220.0D && (rise >= NAIK_MIN || run >= JALAN_MIN);
	}

	static void tick(ServerPlayer p, HariUjian.Sesi s, long now) {
		long until = s.data.getLongOr(ROBOH_SAMPAI, 0L);
		if (until > 0L) {
			if (now >= until) {
				GabungPayloads.kirimKlip(p, 0, 0);
				s.s1 = 0.0D;
				HariUjian.selesaiTahap(p, s, 0.0D);
			}
			return;
		}
		BlockPos bawah = new BlockPos(s.data.getIntOr(BX, 0), s.data.getIntOr(BY, 0), s.data.getIntOr(BZ, 0));
		BlockPos atas = new BlockPos(s.data.getIntOr(AX, 0), s.data.getIntOr(AY, 0), s.data.getIntOr(AZ, 0));
		long age = s.umurTahap(now);
		int limit = Math.max(1, GabungConfig.get().waktuTangga) * 20;
		double tekanan = s.data.getDoubleOr(TEKANAN, 0.0D);
		// movement over the last tick
		double dy = p.getY() - s.data.getDoubleOr(Y_LALU, p.getY());
		double dx = p.getX() - s.data.getDoubleOr(X_LALU, p.getX());
		double dz = p.getZ() - s.data.getDoubleOr(Z_LALU, p.getZ());
		s.data.putDouble(Y_LALU, p.getY());
		s.data.putDouble(X_LALU, p.getX());
		s.data.putDouble(Z_LALU, p.getZ());
		boolean diam = dx * dx + dz * dz < 0.0004D && Math.abs(dy) < 0.02D;
		int jongkok = p.isShiftKeyDown() && diam ? s.data.getIntOr(JONGKOK, 0) + 1 : 0;
		s.data.putInt(JONGKOK, jongkok);
		if (jongkok >= MEDITASI_TICK) {
			tekanan -= MEDITASI_PER_DETIK / 20.0D;
		} else if (diam) {
			tekanan += DIAM_PER_DETIK / 20.0D;
		} else {
			tekanan += NAIK_PER_DETIK / 20.0D;
		}
		// illusions at a quarter, half and three quarters of the way
		double maju = maju(p.position(), bawah, atas);
		int ilusi = s.data.getIntOr(ILUSI_N, 0);
		if (ilusi < 3 && maju >= 0.25D * (ilusi + 1)) {
			ilusi++;
			s.data.putInt(ILUSI_N, ilusi);
			tekanan += ILUSI;
			Msg.actionBar(p, Masuk.t("tangga.ilusi." + ilusi).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			if (ilusi == 1) {
				KultivatorNpc penjaga = HariUjian.penjaga(p.level(), s.sekte, false);
				if (penjaga != null) {
					Masuk.ucap(penjaga, "gabung.tangga_tekanan", "elder.welcome", Masuk.t("ucap.tangga_tekanan"));
				} else {
					Msg.chat(p, Masuk.t("ucap.tangga_tekanan").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				}
			}
		}
		tekanan = Math.max(0.0D, Math.min(ROBOH, tekanan));
		s.data.putDouble(TEKANAN, tekanan);
		s.tekananPuncak = Math.max(s.tekananPuncak, tekanan);
		// slowness bands
		int lambat = tekanan >= LAMBAT_2 ? 2 : tekanan >= LAMBAT_1 ? 1 : 0;
		s.data.putInt(LAMBAT, lambat);
		if (lambat > 0 && (now % 20L == 0L || p.getEffect(MobEffects.SLOWNESS) == null)) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, lambat - 1, false, false, true));
		}
		if (tekanan >= ROBOH) {
			roboh(p, s, now);
			return;
		}
		if (sampai(p.position(), atas)) {
			double waktu = Math.min(1.0D, age / (double) limit);
			RootGrade akar = Cultivators.get(p).rootGrade();
			double nilai = (Math.max(0.0D, 1.0D - waktu) * 0.5D + (1.0D - s.tekananPuncak / 100.0D) * 0.5D) * (0.85D + 0.05D * akar.ordinal());
			s.s1 = HariUjian.clamp(nilai);
			Msg.chat(p, Masuk.t("tangga.sampai", age / 20L, (int) Math.round(s.tekananPuncak)).withStyle(ChatFormatting.GREEN));
			p.removeEffect(MobEffects.SLOWNESS);
			bersihkan(p, s);
			HariUjian.selesaiTahap(p, s, s.s1);
			return;
		}
		if (age >= limit) {
			Msg.chat(p, Masuk.t("tangga.waktu_habis").withStyle(ChatFormatting.RED));
			s.s1 = 0.0D;
			p.removeEffect(MobEffects.SLOWNESS);
			bersihkan(p, s);
			HariUjian.selesaiTahap(p, s, 0.0D);
			return;
		}
		if (now % 40L == 0L) {
			jalankanCalon(p.level(), s, bawah, atas, now);
		}
	}

	private static void roboh(ServerPlayer p, HariUjian.Sesi s, long now) {
		s.data.putLong(ROBOH_SAMPAI, now + 60L);
		GabungPayloads.kirimKlip(p, GabungGerakId.LELAH_BERLUTUT, 0);
		p.removeEffect(MobEffects.SLOWNESS);
		Msg.chat(p, Masuk.t("tangga.roboh").withStyle(ChatFormatting.RED));
		bersihkan(p, s);
	}

	/** Stage 1 HUD: pressure, ticks left, live S1, {@code [0xFF, illusions, slowness]}. */
	static void hud(ServerPlayer p, HariUjian.Sesi s, long now) {
		int limit = Math.max(1, GabungConfig.get().waktuTangga) * 20;
		long age = s.umurTahap(now);
		double tekanan = s.data.getDoubleOr(TEKANAN, 0.0D);
		double waktu = Math.min(1.0D, age / (double) limit);
		float live = (float) HariUjian.clamp(Math.max(0.0D, 1.0D - waktu) * 0.5D + (1.0D - s.tekananPuncak / 100.0D) * 0.5D);
		byte[] data = {(byte) 0xFF, (byte) s.data.getIntOr(ILUSI_N, 0), (byte) s.data.getIntOr(LAMBAT, 0)};
		GabungPayloads.kirimUjian(p, HariUjian.TAHAP_TANGGA, (float) tekanan, (int) Math.max(0L, limit - age), live, data);
	}

	/** Progress 0-1 along the stair (by height when it rises enough, else along the ground). */
	static double maju(Vec3 at, BlockPos bawah, BlockPos atas) {
		double rise = atas.getY() - bawah.getY();
		if (rise >= NAIK_MIN) {
			return HariUjian.clamp((at.y - bawah.getY()) / rise);
		}
		double total = Math.sqrt(bawah.distSqr(atas));
		double done = Math.sqrt(Math.pow(at.x - bawah.getX() - 0.5D, 2) + Math.pow(at.z - bawah.getZ() - 0.5D, 2));
		return total <= 0.0D ? 1.0D : HariUjian.clamp(done / total);
	}

	private static boolean sampai(Vec3 at, BlockPos atas) {
		return Masuk.datar(at, atas) <= JARI_PUNCAK && at.y >= atas.getY() - 1.5D;
	}

	// ================================================================== child candidates (41.5.3)

	private static void calon(ServerLevel level, HariUjian.Sesi s, BlockPos bawah, BlockPos atas) {
		long seed = s.mulai ^ s.sekte.hashCode();
		int n = 2 + (int) Math.floorMod(seed, 3L);
		n = Math.min(CALON_MAKS, n);
		long situs = Masuk.situs(level, bawah);
		for (int i = 0; i < n; i++) {
			NpcRole role = i % 3 == 2 ? NpcRole.PETANI : NpcRole.ANAK;
			BlockPos at = bawah.offset((i % 2 == 0 ? 1 : -1) * (1 + i / 2), 0, i % 2);
			int look = (int) (seed * 31L + i * 7919L);
			KultivatorNpc npc = HidupApi.tubuh(level, situs, role, at, 0.0F, look);
			if (npc == null) {
				continue;
			}
			npc.memory().putBoolean(Masuk.MEM_CALON, true);
			npc.memory().putString(Masuk.MEM_SEKTE, s.sekte);
			// each child gives up at its own step (25-90 % of the way)
			npc.memory().putDouble("gabung_henti", 0.25D + Math.floorMod(look >>> 4, 66) / 100.0D);
			npc.setLonely(96.0D, 1200);
			s.ikat(npc);
		}
	}

	private static void jalankanCalon(ServerLevel level, HariUjian.Sesi s, BlockPos bawah, BlockPos atas, long now) {
		for (java.util.UUID id : s.tubuh) {
			Entity e = level.getEntity(id);
			if (!(e instanceof KultivatorNpc npc) || !npc.isAlive() || !npc.memory().getBooleanOr(Masuk.MEM_CALON, false)) {
				continue;
			}
			if (npc.memory().getBooleanOr("gabung_berlutut", false)) {
				continue;
			}
			double stop = npc.memory().getDoubleOr("gabung_henti", 0.5D);
			if (maju(npc.position(), bawah, atas) >= stop) {
				npc.getNavigation().stop();
				npc.memory().putBoolean("gabung_berlutut", true);
				HidupApi.klip(npc, GabungGerakId.LELAH_BERLUTUT, 0, 0);
				continue;
			}
			npc.getNavigation().moveTo(atas.getX() + 0.5D, atas.getY(), atas.getZ() + 0.5D, 0.55D);
		}
	}

	/** Ends stage 1 effects (the children stay kneeling until the day ends). */
	static void bersihkan(ServerPlayer p, HariUjian.Sesi s) {
		if (s.tahap == HariUjian.TAHAP_TANGGA) {
			p.removeEffect(MobEffects.SLOWNESS);
		}
	}

	private TanggaUjian() {
	}
}
