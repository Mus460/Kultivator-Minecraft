package dev.kultivator.dunia.tempat.hidup;

import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The visible life of one resident body (DESIGN 38.N.4, L0/L1), driven from {@code NpcLife} every 20 ticks and from
 * {@code NpcAi.HomeGoal} for walking (the entity class stays 40I's). Server state lives in the body's memory, keys
 * prefixed {@code hd_}.
 * <ol>
 *   <li><b>To a real titik</b>: the schedule's goal ({@link TempatHuni#tujuan}, one person per titik, a queue three
 *       blocks in front when taken); vanilla pathfinding within 24 blocks of a player (at most 8 searches per server tick
 *       for all bodies), straight steps between 24 and 48; on arrival it faces the titik and plays the clip with its
 *       props, now and then the role's once clip (a vendor's call, the judge's gavel, a monk's bow).</li>
 *   <li><b>Carrying</b>: the walk takes the activity's props (crate, carrying pole, tray, umbrella).</li>
 *   <li><b>Market bargaining</b> between residents (38.N.4 butir 3): a buyer goes to a stall, both bargain for three
 *       seconds, money and goods change hands, a small bow. A show only: the player's economy never changes.</li>
 *   <li><b>Children at play</b>: chasing runs between two play spots; paired sparring of disciples in step.</li>
 *   <li><b>Gestured chats</b>: four lines of the town's chatter with the hands, nods and laughter, faces turned.</li>
 *   <li><b>Players</b>: a glance at a passer-by within 6 blocks (once per 30 s), a bow two realms below, stepping aside
 *       and a deep bow four below, a wave and the name for a friend of the town (Nama Baik >= 80 or met often), an angry
 *       finger and a whisper at a disgraced one (<= -30), kneeling or hiding when the place fears the player (38.R.5),
 *       pointing and calling the guards at a wanted one (38.B.5).</li>
 *   <li><b>Crime</b>: witnesses point and shout, run to the nearest guard, the rest back off and stare.</li>
 *   <li><b>Danger</b> (monsters within 16, a fight, a beast wave, a player striking a resident): civilians run to the
 *       nearest door and go in, or hide; guards draw and fight; fighters of tier 2 and up join; after 60 s quiet the
 *       schedule returns.</li>
 * </ol>
 */
public final class TubuhPenduduk {

	private static final String TX = "hd_tx";
	private static final String TY = "hd_ty";
	private static final String TZ = "hd_tz";
	private static final String TF = "hd_tf";
	private static final String TNYATA = "hd_tnyata";
	private static final String KUNCI = "hd_key";
	private static final String TIBA = "hd_tiba";
	private static final String KLIP = "hd_klip";
	private static final String JALAN = "hd_jalan";
	private static final String SELA_T = "hd_sela_t";
	private static final String TAKUT = "hd_takut";
	private static final String DI_RUMAH = "hd_di_rumah";
	private static final String MACET_T = "hd_macet_t";
	private static final String MACET_D = "hd_macet_d";
	private static final String TATAP_T = "hd_tatap_t";
	private static final String OBROL = "hd_obrol";
	private static final String OBROL_STEP = "hd_obrol_step";
	private static final String OBROL_NEXT = "hd_obrol_next";
	private static final String OBROL_TOPIK = "hd_obrol_topik";
	private static final String OBROL_T = "hd_obrol_t";
	private static final String SIBUK = "hd_sibuk";
	private static final String BELI = "hd_beli";
	private static final String BELI_T = "hd_beli_t";
	private static final String JUAL_T = "hd_jual_t";
	private static final String KEJAR_T = "hd_kejar_t";
	private static final String TANDING = "hd_tanding";
	/** L1 street route (block positions), the next node index, the block aimed at, an L0 search already started. */
	private static final String RUTE = "hd_rute";
	private static final String RUTE_I = "hd_rute_i";
	private static final String ARAH = "hd_arah";
	private static final String DICARI = "hd_dicari";
	/** The face was set by the activity (cleared when the next goal is given). */
	private static final String MOOD = "hd_mood";

	/** Line length of a speech bubble (ticks). */
	static final int BARIS = 70;
	static final double LIHAT = 6.0D;
	static final long LIHAT_JEDA = 600L;
	static final double BAHAYA_MONSTER = 16.0D;
	/** Roles whose market calls have their own lines (else {@code seru.umum}). */
	private static final Set<NpcRole> SERU_SENDIRI = Set.of(NpcRole.BANKIR, NpcRole.BANDAR_BATU, NpcRole.PEDAGANG_BINATANG,
			NpcRole.PEDAGANG_SENJATA, NpcRole.PEMILIK_PENGINAPAN, NpcRole.PENJAGA_TEKA, NpcRole.PEDAGANG_KELILING, NpcRole.PETUGAS_BEA,
			NpcRole.PENJUAL_IKAN, NpcRole.PEDAGANG_SEBERANG, NpcRole.KAFILAH_BASHI, NpcRole.PEDAGANG_BAZAR, NpcRole.NYONYA_SUTRA,
			NpcRole.MAKELAR_GELAP, NpcRole.PEDAGANG_PIL, NpcRole.JURU_LELANG, NpcRole.PENGEMIS, NpcRole.BIKSU_GUNUNG, NpcRole.PENDETA_LAUT);
	private static final String[] TOPIK = {"harga", "gosip", "makanan", "anak", "sekte", "latihan", "pil", "pengembara", "lelang"};

	private TubuhPenduduk() {
	}

	// ================================================================== identity

	/** True for a resident body made by {@link Pemanggil} ({@link KultivatorNpc#penduduk}). */
	public static boolean adalah(KultivatorNpc npc) {
		return npc.penduduk();
	}

	/** The place of a resident body, or null. */
	static @Nullable TempatHuni huni(KultivatorNpc npc) {
		return npc.penduduk() ? Pemanggil.huni(npc.situsPenduduk()) : null;
	}

	/** The resident a body stands for, or null. */
	static @Nullable Penduduk penduduk(KultivatorNpc npc) {
		TempatHuni huni = huni(npc);
		return huni == null ? null : huni.orang(npc.pendudukId());
	}

	/** The walking clip of a role (38.N.5.1): the old lean on a staff, children skip, guards march, porters bear. */
	static int jalanPeran(NpcRole peran, Kegiatan k) {
		if (k.jalan() != 0) {
			return k.jalan();
		}
		return switch (peran.kelompok) {
			case ANAK -> GerakId.JALAN_ANAK;
			case TETUA -> GerakId.JALAN_TUA;
			case PENJAGA -> GerakId.BARIS_JAGA;
			default -> peran == NpcRole.KULI || peran == NpcRole.KULI_GUNUNG ? GerakId.JALAN_PIKUL : GerakId.JALAN_SANTAI;
		};
	}

	private static double laju(int jalan) {
		return switch (jalan) {
			case GerakId.JALAN_TUA -> 0.42D;
			case GerakId.JALAN_ANAK -> 0.62D;
			case GerakId.LARI, GerakId.LARI_TAKUT, GerakId.MAIN_KEJAR -> 0.95D;
			case GerakId.JALAN_TERGESA -> 0.8D;
			case GerakId.JALAN_PIKUL, GerakId.JALAN_BAWA, GerakId.DAKI_PIKUL, GerakId.ANGKAT_AIR, GerakId.ANGKAT_PAPAN -> 0.48D;
			default -> 0.55D;
		};
	}

	// ================================================================== start

	/** A body was just made for {@code p} doing {@code k}: aims it at {@code tujuan}. */
	static void mulai(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, Kegiatan k, TempatHuni.Tujuan tujuan, long now) {
		int jam = Jadwal.saat(p, huni.jenis, Pemanggil.waktu(huni, level)).jam();
		npc.memory().putString(KUNCI, kunci(k, jam));
		tuju(huni, npc, p, k, tujuan);
		if (npc.position().distanceToSqr(Vec3.atBottomCenterOf(tujuan.pos())) < 2.0D) {
			tiba(level, huni, npc, p, k, now);
		}
	}

	private static String kunci(Kegiatan k, int jam) {
		return k.kunci() + "|" + k.tujuan() + "|" + k.klip() + "|" + jam / 2;
	}

	private static void tuju(TempatHuni huni, KultivatorNpc npc, Penduduk p, Kegiatan k, TempatHuni.Tujuan t) {
		CompoundTag m = npc.memory();
		m.putInt(TX, t.pos().getX());
		m.putInt(TY, t.pos().getY());
		m.putInt(TZ, t.pos().getZ());
		m.putInt(TF, t.hadap().get2DDataValue());
		m.putBoolean(TNYATA, t.nyata());
		m.putBoolean(TIBA, false);
		m.remove(DI_RUMAH);
		m.remove(MACET_T);
		m.remove(MACET_D);
		m.remove(RUTE);
		m.remove(ARAH);
		m.remove(DICARI);
		npc.getNavigation().stop();
		npc.setDiamDiTitik(false);
		if (m.getBooleanOr(MOOD, false)) {
			m.remove(MOOD);
			suasana(npc, Gaya.SUASANA_NETRAL, 0);
		}
		if (t.nyata()) {
			huni.kunci(p.id(), t.pos());
		} else {
			huni.lepas(p.id());
		}
		npc.setHome(t.pos());
		int jalan = jalanPeran(p.peran(), k);
		m.putInt(JALAN, jalan);
		int[] prop = propJalan(k, jalan);
		SosokApi.gerak(npc, jalan, 0, 0, prop[0], prop[1], prop[2]);
		m.putInt(KLIP, jalan);
	}

	/**
	 * Props carried on the way: what the walk itself bears, the umbrella of the activity, and the light tools a person
	 * simply carries in the hand to where they are used (38.N.4 butir 2: the farmer's hoe to the field, the monk's incense,
	 * the child's kite, the elder's staff, the guard's lantern).
	 */
	private static int[] propJalan(Kegiatan k, int jalan) {
		boolean bawa = jalan == GerakId.JALAN_BAWA || jalan == GerakId.JALAN_PIKUL || jalan == GerakId.ANGKAT_AIR || jalan == GerakId.ANGKAT_PAPAN
				|| jalan == GerakId.DAKI_PIKUL || jalan == GerakId.BARIS_JAGA;
		int kiri = k.propKiri() == PropId.PAYUNG_KERTAS ? PropId.PAYUNG_KERTAS : bawa || DIJINJING.contains(k.propKiri()) ? k.propKiri() : 0;
		int kanan = bawa || DIJINJING.contains(k.propKanan()) ? k.propKanan() : 0;
		return new int[]{kanan, kiri, k.propPunggung()};
	}

	/** Light things carried in the hand on the way (the others appear only at the spot). */
	private static final Set<Integer> DIJINJING = Set.of(PropId.CANGKUL, PropId.JORAN, PropId.SAPU, PropId.TOMBAK, PropId.TONGKAT,
			PropId.TONGKAT_BAMBU, PropId.TONGKAT_CINCIN, PropId.KIPAS_LIPAT, PropId.KIPAS_SUTRA, PropId.LENTERA_TANGAN, PropId.DUPA,
			PropId.LAYANG_LAYANG, PropId.KITAB, PropId.GULUNGAN, PropId.SABIT_HERBA, PropId.KERANJANG, PropId.KERANJANG_TEH,
			PropId.TASBIH_TANGAN, PropId.KEBUT, PropId.PAPAN_CATATAN, PropId.SERULING, PropId.KANTONG_UANG, PropId.LENTERA_TERATAI);

	private static @Nullable BlockPos target(KultivatorNpc npc) {
		CompoundTag m = npc.memory();
		if (!m.contains(TX)) {
			return null;
		}
		return new BlockPos(m.getIntOr(TX, 0), m.getIntOr(TY, 0), m.getIntOr(TZ, 0));
	}

	// ================================================================== the pass (every 20 ticks, from NpcLife)

	/** The resident's pass. Returns quietly when the place is gone (the body is released). */
	public static void tik(ServerLevel level, KultivatorNpc npc, long now) {
		TempatHuni huni = huni(npc);
		Penduduk p = huni == null ? null : huni.orang(npc.pendudukId());
		if (huni == null || p == null) {
			npc.discard();
			return;
		}
		huni.tubuh.putIfAbsent(p.id(), npc);
		CompoundTag m = npc.memory();
		if (npc.getTarget() != null || DuelRules.inDuel(npc)) {
			npc.setDiamDiTitik(false);
			if (m.getIntOr(KLIP, -1) != 0) {
				SosokApi.henti(npc);
				m.putInt(KLIP, 0);
				m.putBoolean(TIBA, false);
			}
			return;
		}
		try {
			pass(level, huni, npc, p, now);
		} finally {
			npc.setDiamDiTitik(diam(npc));
		}
	}

	private static void pass(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, long now) {
		CompoundTag m = npc.memory();
		// back from a bad wound: the sling shows on the painted body while it recovers (PendudukData.PULIH)
		int luka = huni.keadaanDari(p.id()) == PendudukData.PULIH ? Gaya.LUKA_LENGAN : Gaya.LUKA_SEHAT;
		if (SosokApi.gaya(npc).luka() != luka) {
			SosokApi.setLuka(npc, luka);
		}
		if (now >= m.getLongOr(TATAP_T, 0L) && m.contains(TATAP_T)) {
			m.remove(TATAP_T);
			SosokApi.tatap(npc, null);
		}
		if (bahaya(level, huni, npc, p, now)) {
			return;
		}
		long waktu = Pemanggil.waktu(huni, level);
		if (now % 40L < 20L || !m.contains(KUNCI)) {
			jadwal(level, huni, npc, p, now, waktu);
		}
		if (beli(level, huni, npc, p, now)) {
			return;
		}
		BlockPos goal = target(npc);
		if (goal != null && !m.getBooleanOr(TIBA, false)) {
			cekPerjalanan(level, huni, npc, p, goal, now);
			return;
		}
		if (m.getBooleanOr(TIBA, false)) {
			diTitik(level, huni, npc, p, now);
		}
		pemain(level, huni, npc, p, now);
	}

	/** Re-reads the schedule; a new activity gives a new goal. */
	private static void jadwal(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, long now, long waktu) {
		Kegiatan k = huni.kegiatan(p, waktu, now);
		int jam = Jadwal.saat(p, huni.jenis, waktu).jam();
		String key = kunci(k, jam);
		CompoundTag m = npc.memory();
		if (key.equals(m.getStringOr(KUNCI, ""))) {
			return;
		}
		m.putString(KUNCI, key);
		akhiriObrol(level, npc);
		akhiriTanding(level, npc);
		TempatHuni.Tujuan t = huni.tujuan(level, p, k, jam, true);
		if (t == null) {
			// stay: play it where it stands
			m.remove(TX);
			m.putBoolean(TIBA, true);
			main(npc, k);
			return;
		}
		tuju(huni, npc, p, k, t);
	}

	private static Kegiatan kegiatanSekarang(ServerLevel level, TempatHuni huni, Penduduk p, long now) {
		return huni.kegiatan(p, Pemanggil.waktu(huni, level), now);
	}

	// ================================================================== walking (GolPenduduk.Jalan, 38.N.1 "Gerak")

	/**
	 * The 20-tick check of a body on its way: arrived, or stuck. Stuck means no progress for 10 s: unseen it simply
	 * arrives (snapped to the goal), seen it waits for the next try. The steps themselves are {@link #langkah}.
	 */
	private static void cekPerjalanan(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, BlockPos goal, long now) {
		CompoundTag m = npc.memory();
		Vec3 g = Vec3.atBottomCenterOf(goal);
		double dx = g.x - npc.getX();
		double dz = g.z - npc.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat < 1.3D && Math.abs(g.y - npc.getY()) < 2.0D) {
			tiba(level, huni, npc, p, kegiatanSekarang(level, huni, p, now), now);
			return;
		}
		double before = m.getDoubleOr(MACET_D, Double.MAX_VALUE);
		if (flat < before - 0.5D) {
			m.putDouble(MACET_D, flat);
			m.putLong(MACET_T, now);
		} else if (now - m.getLongOr(MACET_T, now) > 200L) {
			m.putLong(MACET_T, now);
			m.putDouble(MACET_D, Double.MAX_VALUE);
			m.remove(RUTE);
			Player near = level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(), 48.0D, each -> !each.isSpectator());
			double pd = near == null ? Double.MAX_VALUE : near.distanceTo(npc);
			if (pd > 32.0D || near != null && !near.hasLineOfSight(npc)) {
				npc.getNavigation().stop();
				npc.snapTo(g.x, g.y, g.z, npc.getYRot(), 0.0F);
				tiba(level, huni, npc, p, kegiatanSekarang(level, huni, p, now), now);
			}
		}
	}

	/** True while the body is on its way to a goal it has not reached ({@code GolPenduduk.Jalan.canUse}). */
	static boolean dalamPerjalanan(KultivatorNpc npc) {
		CompoundTag m = npc.memory();
		return npc.getTarget() == null && m.contains(TX) && !m.getBooleanOr(TIBA, false) && !m.contains(BELI);
	}

	/**
	 * One step of the walk (every 4th tick near a player, every 10th otherwise). Within 24 blocks of a player (L0) the
	 * vanilla navigation finds the way (one search per goal, inside the shared budget of {@link Pemanggil#bolehJalur});
	 * further out (L1) the body follows the place's street nodes ({@link TempatHuni#rute}, no path search) and the
	 * move control is aimed at the next node every tick by the goal ({@link #arah}). Returns quietly on arrival.
	 */
	static void langkah(KultivatorNpc npc) {
		if (!(npc.level() instanceof ServerLevel level)) {
			return;
		}
		TempatHuni huni = huni(npc);
		Penduduk p = huni == null ? null : huni.orang(npc.pendudukId());
		BlockPos goal = target(npc);
		CompoundTag m = npc.memory();
		if (huni == null || p == null || goal == null || m.getBooleanOr(TIBA, false)) {
			return;
		}
		Vec3 g = Vec3.atBottomCenterOf(goal);
		double flat = Math.sqrt(npc.distanceToSqr(g.x, npc.getY(), g.z));
		long now = level.getGameTime();
		if (flat < 1.3D && Math.abs(g.y - npc.getY()) < 2.0D) {
			tiba(level, huni, npc, p, kegiatanSekarang(level, huni, p, now), now);
			return;
		}
		double laju = laju(m.getIntOr(JALAN, GerakId.JALAN_SANTAI));
		Player near = level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(), 48.0D, each -> !each.isSpectator());
		double pd = near == null ? Double.MAX_VALUE : near.distanceTo(npc);
		if (pd <= 24.0D) {
			// L0: a real path, searched once per goal
			m.remove(RUTE);
			m.remove(ARAH);
			if (npc.getNavigation().isDone() && !m.getBooleanOr(DICARI, false) && Pemanggil.bolehJalur()) {
				m.putBoolean(DICARI, true);
				npc.getNavigation().moveTo(g.x, g.y, g.z, laju);
			} else if (npc.getNavigation().isDone() && m.getBooleanOr(DICARI, false)) {
				// the search ended short of the goal: try again on the next free slot
				m.remove(DICARI);
			}
			return;
		}
		// L1: street nodes, straight steps between them
		if (!npc.getNavigation().isDone()) {
			npc.getNavigation().stop();
		}
		long[] rute = m.getLongArray(RUTE).orElse(null);
		if (rute == null) {
			if (!Pemanggil.bolehJalur()) {
				m.putLong(ARAH, goal.asLong());
				return;
			}
			rute = huni.rute(npc.blockPosition(), goal);
			m.putLongArray(RUTE, rute);
			m.putInt(RUTE_I, 0);
		}
		int i = m.getIntOr(RUTE_I, 0);
		while (i < rute.length) {
			BlockPos node = BlockPos.of(rute[i]);
			if (npc.distanceToSqr(node.getX() + 0.5D, npc.getY(), node.getZ() + 0.5D) > 2.25D) {
				break;
			}
			i++;
		}
		m.putInt(RUTE_I, i);
		m.putLong(ARAH, i < rute.length ? rute[i] : goal.asLong());
	}

	/** The block the move control aims at this tick in L1 (null in L0 or when nothing is planned). */
	static @Nullable Vec3 arah(KultivatorNpc npc) {
		CompoundTag m = npc.memory();
		if (!m.contains(ARAH) || m.getBooleanOr(TIBA, false)) {
			return null;
		}
		return Vec3.atBottomCenterOf(BlockPos.of(m.getLongOr(ARAH, 0L)));
	}

	/** Speed modifier of the walk under way. */
	static double lajuJalan(KultivatorNpc npc) {
		return laju(npc.memory().getIntOr(JALAN, GerakId.JALAN_SANTAI));
	}

	/** The walk was interrupted (a fight, a sit-down): the plan is dropped, the next start plans again. */
	static void hentiLangkah(KultivatorNpc npc) {
		CompoundTag m = npc.memory();
		m.remove(ARAH);
		m.remove(DICARI);
	}

	// ================================================================== still, focused (GolPenduduk.Diam / Fokus)

	/** Clips during which the body stays put at its titik and is not pushed (38.N.5.1 sitting, sleeping, kneeling). */
	private static final Set<Integer> KLIP_DIAM = Set.of(GerakId.TIDUR, GerakId.TIDUR_DUDUK, GerakId.DUDUK_KURSI, GerakId.DUDUK_LANTAI,
			GerakId.CATUR, GerakId.CERITA, GerakId.NAPAS_MEDITASI, GerakId.MEDITASI_NAPAS, GerakId.BERLUTUT, GerakId.BERLUTUT_RANTAI,
			GerakId.SEMEDI_DARAH, GerakId.TENUN, GerakId.SUJUD_DUPA, GerakId.MEMANCING, GerakId.ASAH_PISAU, GerakId.TAKUT_SEMBUNYI);
	/** Clips whose eyes stay on the work in front (38.N.5.4: the gaze target is the work object). */
	private static final Set<Integer> KLIP_FOKUS = Set.of(GerakId.MENULIS, GerakId.MEMBACA, GerakId.MASAK, GerakId.PALU_LANDASAN,
			GerakId.TUMBUK_HERBA, GerakId.HITUNG_SEMPOA, GerakId.SAPU, GerakId.TENUN, GerakId.LUKIS_DINDING, GerakId.PETIK_TEH,
			GerakId.PETIK_HERBA_TEBING, GerakId.PALU_LUNAS, GerakId.IKAT_TALI, GerakId.JEMUR_IKAN, GerakId.MENCANGKUL, GerakId.MEMANEN,
			GerakId.TANAM_BIBIT, GerakId.PERBAIKI_PALU, GerakId.PERBAIKI_PLESTER, GerakId.ASAH_PISAU, GerakId.HITUNG_KOIN_GELAP,
			GerakId.RAK_SUSUN, GerakId.TIMBA_SUMUR, GerakId.PUTAR_KATROL, GerakId.CATUR, GerakId.MEMANCING, GerakId.TARIK_JALA,
			GerakId.SEDUH_TEH, GerakId.TUANG_TEH, GerakId.BASUH_PAKAIAN, GerakId.GANTI_TANAH, GerakId.TANAM_ULANG);

	/** True while the body sleeps, sits or kneels at its titik. */
	static boolean diam(KultivatorNpc npc) {
		CompoundTag m = npc.memory();
		return m.getBooleanOr(TIBA, false) && !m.contains(TAKUT) && !m.contains(BELI) && KLIP_DIAM.contains(m.getIntOr(KLIP, 0));
	}

	/** {@code GolPenduduk.Diam.tick}: faces the titik's own way (the bed's head, the chair's front). */
	static void tahanDiam(KultivatorNpc npc) {
		CompoundTag m = npc.memory();
		if (!m.getBooleanOr(TNYATA, false) || SosokApi.tatapSekarang(npc) != SosokApi.TATAP_TIDAK_ADA) {
			return;
		}
		float yaw = Direction.from2DDataValue(m.getIntOr(TF, 0)).toYRot();
		npc.setYRot(yaw);
		npc.setYBodyRot(yaw);
		npc.setYHeadRot(yaw);
	}

	/** True while the body works with its eyes on the work and nobody is being looked at. */
	static boolean fokus(KultivatorNpc npc) {
		CompoundTag m = npc.memory();
		return m.getBooleanOr(TIBA, false) && !m.contains(OBROL) && !m.contains(BELI) && !m.getBooleanOr(DI_RUMAH, false)
				&& KLIP_FOKUS.contains(m.getIntOr(KLIP, 0)) && SosokApi.tatapSekarang(npc) == SosokApi.TATAP_TIDAK_ADA;
	}

	/** {@code GolPenduduk.Fokus.tick}: looks down at the bench, desk or field just in front. */
	static void lihatKerja(KultivatorNpc npc) {
		Vec3 front = Vec3.directionFromRotation(0.0F, npc.getYRot()).scale(1.1D);
		npc.getLookControl().setLookAt(npc.getX() + front.x, npc.getY() + 0.55D, npc.getZ() + front.z, 10.0F, 40.0F);
	}

	// ================================================================== at the goal

	private static void tiba(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, Kegiatan k, long now) {
		CompoundTag m = npc.memory();
		BlockPos goal = target(npc);
		npc.getNavigation().stop();
		m.putBoolean(TIBA, true);
		if (goal != null && m.getBooleanOr(TNYATA, false)) {
			Direction face = Direction.from2DDataValue(m.getIntOr(TF, 0));
			Player close = level.getNearestPlayer(npc, 3.0D);
			if (close == null) {
				double y = goal.getY() + ("kasur".equals(jenisTitik(huni, goal)) ? 0.5625D : 0.0D);
				npc.snapTo(goal.getX() + 0.5D, y, goal.getZ() + 0.5D, face.toYRot(), 0.0F);
			}
			npc.setYRot(face.toYRot());
			npc.setYHeadRot(face.toYRot());
			npc.setYBodyRot(face.toYRot());
		}
		if (k.tujuan().startsWith(Kegiatan.XZ)) {
			// a watcher of a scene faces its centre exactly (the ring of C39.K11)
			int[] xz = Jadwal.xz(k.tujuan().substring(Kegiatan.XZ.length()));
			if (xz != null) {
				hadap(npc, new Vec3(xz[0] + 0.5D, npc.getY(), xz[1] + 0.5D));
			}
		}
		if (k.dalam()) {
			// home for the night: in bed when it has one in sight, else it goes in (released unseen by Pemanggil)
			m.putBoolean(DI_RUMAH, true);
			boolean kasur = "kasur".equals(goal == null ? "" : jenisTitik(huni, goal));
			// asleep in its own bed; sheltering ones crouch inside; the rest wait indoors until they are out of sight
			int klip = kasur && k.kunci().equals("tidur") ? GerakId.TIDUR
					: k.kunci().equals("berlindung") ? GerakId.TAKUT_SEMBUNYI
					: kasur ? GerakId.DUDUK_KURSI : GerakId.SIKAP_TANGAN_BELAKANG;
			int varian = klip == GerakId.TIDUR && (p.benih() >>> 27 & 1L) == 1L ? GerakId.V_TIDUR_MIRING : 0;
			SosokApi.gerak(npc, klip, varian, 0, 0, 0, 0);
			m.putInt(KLIP, klip);
			npc.setDiamDiTitik(diam(npc));
			return;
		}
		main(npc, k);
		if (k.kunci().equals("bangun")) {
			if (k.klip() == GerakId.SIKAP_SANTAI_L && SosokApi.perempuan(npc)) {
				// women stand at ease in their own way (40 stance pair L/P)
				SosokApi.gerak(npc, GerakId.SIKAP_SANTAI_P, 0, 0, 0, 0, 0);
				m.putInt(KLIP, GerakId.SIKAP_SANTAI_P);
			}
			// out of the door: the big stretch and yawn (officials and merchants straighten a sleeve instead, by seed)
			boolean rapi = (p.peran().kelompok == NpcRole.Kelompok.PEJABAT || p.peran().kelompok == NpcRole.Kelompok.PEDAGANG)
					&& (p.benih() & 1L) == 0L;
			SosokApi.gerak(npc, GerakId.MENGUAP_PEREGANGAN, rapi ? GerakId.V_MENGUAP_RAPIKAN : 0, 0, 0, 0, 0);
		}
		int mood = suasanaKegiatan(k.kunci());
		if (mood >= 0) {
			suasana(npc, mood, 0);
			m.putBoolean(MOOD, true);
		}
		m.putLong(SELA_T, now + 200L + npc.getRandom().nextInt(400));
		if (k.klip() == GerakId.TANDING_A) {
			pasangkanTanding(level, npc, now);
		}
		if (LONCENG.contains(k.kunci()) && (p.peran().kelompok == NpcRole.Kelompok.RAHIB)) {
			lonceng(level, huni, npc, now);
		}
		if (k.klip() == GerakId.GENDERANG) {
			genderang(level, huni, npc, now);
		}
		npc.setDiamDiTitik(diam(npc));
	}

	// ================================================================== the sounds of the town (38.N.4 butir 12)

	/** Activities at which a monk rings the temple bell. */
	private static final Set<String> LONCENG = Set.of("lonceng_subuh", "doa_senja", "lonceng");
	/** Bell, drum and bow reach this far. */
	static final double SUARA_JARAK = 48.0D;

	/**
	 * The temple bell (dawn and dusk prayers): the bell sounds at the monk (a sound only), and the people of the place
	 * within {@value #SUARA_JARAK} blocks who are not asleep, fleeing, trading or chatting turn toward the temple and bow
	 * with joined palms. At most once per 2.5 minutes per place.
	 */
	private static void lonceng(ServerLevel level, TempatHuni huni, KultivatorNpc monk, long now) {
		if (now - huni.loncengT < 3000L) {
			return;
		}
		huni.loncengT = now;
		level.playSound(null, monk.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.6F, 0.62F);
		for (KultivatorNpc other : huni.tubuh.values()) {
			if (other == monk || other.isRemoved() || other.distanceToSqr(monk) > SUARA_JARAK * SUARA_JARAK || other.getTarget() != null) {
				continue;
			}
			CompoundTag o = other.memory();
			if (o.contains(OBROL) || o.contains(BELI) || o.contains(TAKUT) || o.getBooleanOr(DI_RUMAH, false) || o.getIntOr(KLIP, 0) == GerakId.TIDUR
					|| other.getRandom().nextInt(5) < 2) {
				continue;
			}
			hadap(other, monk.position());
			SosokApi.gerak(other, GerakId.HORMAT, GerakId.V_HORMAT_WANFU, 0, 0, 0, 0);
		}
	}

	/**
	 * The night drum at 22:00: the night market packs up early ({@link TempatHuni#tutupSampai}, 40 world minutes) and the
	 * vendors within reach start closing now.
	 */
	private static void genderang(ServerLevel level, TempatHuni huni, KultivatorNpc drummer, long now) {
		if (now < huni.tutupSampai) {
			return;
		}
		huni.tutupSampai = now + 2400L;
		for (KultivatorNpc other : huni.tubuh.values()) {
			if (other != drummer && !other.isRemoved() && other.distanceToSqr(drummer) <= 64.0D * 64.0D
					&& other.role().kelompok == NpcRole.Kelompok.PEDAGANG) {
				segarkan(other);
			}
		}
	}

	/**
	 * A lightning bolt struck near inhabited places (read when the bolt entity loads; the mod never makes one): children
	 * within 48 blocks jump and some run to hide, adults flinch or look up at the sky.
	 */
	static void petir(ServerLevel level, BlockPos at) {
		for (TempatHuni huni : Pemanggil.semua()) {
			if (!huni.dimensi.equals(level.dimension()) || Pemanggil.jarak(huni.kotak, Vec3.atCenterOf(at)) > 96.0D) {
				continue;
			}
			for (KultivatorNpc npc : List.copyOf(huni.tubuh.values())) {
				if (npc.isRemoved() || npc.getTarget() != null || npc.blockPosition().distSqr(at) > 48.0D * 48.0D) {
					continue;
				}
				CompoundTag m = npc.memory();
				if (m.getIntOr(KLIP, 0) == GerakId.TIDUR || m.getBooleanOr(DI_RUMAH, false)) {
					continue;
				}
				if (npc.role().anak()) {
					suasana(npc, Gaya.SUASANA_TAKUT, 200);
					SosokApi.gerak(npc, npc.getRandom().nextInt(3) == 0 ? GerakId.TAKUT_SEMBUNYI : GerakId.KAGET, 0,
							npc.getRandom().nextInt(3) == 0 ? 60 : 0, 0, 0, 0);
					if (npc.getRandom().nextBoolean()) {
						npc.say(Component.translatable("kultivator.hidup.petir.anak." + npc.getRandom().nextInt(3)), BARIS);
					}
				} else if (npc.getRandom().nextInt(3) == 0) {
					SosokApi.gerak(npc, GerakId.KAGET, 0, 0, 0, 0, 0);
				} else if (npc.getRandom().nextBoolean()) {
					SosokApi.gerak(npc, GerakId.TATAP_LANGIT, 0, 40, 0, 0, 0);
					if (npc.getRandom().nextInt(4) == 0) {
						npc.say(Component.translatable("kultivator.hidup.petir.dewasa." + npc.getRandom().nextInt(3)), BARIS);
					}
				}
			}
		}
	}

	/**
	 * The sky reaction of a resident ({@code NpcLife.reactLater}, staggered per body): the blood eclipse startles it and
	 * sends it indoors, meteors make it point up, the twin moons make it stare and whisper, a newborn treasure's light
	 * makes it point toward the light; the end of an event brings a relieved word. The schedule is then re-read so the
	 * day follows the sky ({@link Jadwal}).
	 */
	public static void reaksi(ServerLevel level, KultivatorNpc npc, String react, @Nullable BlockPos at, long now) {
		CompoundTag m = npc.memory();
		if (m.getIntOr(KLIP, 0) == GerakId.TIDUR || m.getBooleanOr(DI_RUMAH, false) || m.contains(TAKUT)) {
			segarkan(npc);
			return;
		}
		akhiriObrol(level, npc);
		boolean penjaga = npc.role().kelompok == NpcRole.Kelompok.PENJAGA;
		switch (react) {
			case "blood_eclipse" -> {
				SosokApi.gerak(npc, penjaga ? GerakId.HUNUS : GerakId.KAGET, 0, 0, 0, 0, 0);
				suasana(npc, penjaga ? Gaya.SUASANA_MARAH : Gaya.SUASANA_TAKUT, 600);
				npc.say(Component.translatable((penjaga ? "kultivator.kota.penjaga.gerhana." : "kultivator.kota.langit.blood_eclipse.")
						+ npc.getRandom().nextInt(4)), BARIS);
			}
			case "meteor_shower" -> {
				SosokApi.gerak(npc, GerakId.TATAP_LANGIT, GerakId.V_TATAP_TUNJUK, 100, 0, 0, 0);
				suasana(npc, Gaya.SUASANA_SENANG, 300);
				npc.say(Component.translatable("kultivator.kota.langit.meteor_shower." + npc.getRandom().nextInt(4)), BARIS + 30);
			}
			case "twin_moons" -> {
				SosokApi.gerak(npc, GerakId.TATAP_LANGIT, GerakId.V_TATAP_RENUNG, 100, 0, 0, 0);
				npc.say(Component.translatable("kultivator.kota.langit.twin_moons." + npc.getRandom().nextInt(4)), BARIS + 30);
			}
			case "harta" -> {
				if (at != null) {
					hadap(npc, Vec3.atCenterOf(at));
				}
				SosokApi.gerak(npc, GerakId.TUNJUK, 0, 60, 0, 0, 0);
				suasana(npc, Gaya.SUASANA_SENANG, 200);
				npc.say(Component.translatable("kultivator.kota.langit.harta." + npc.getRandom().nextInt(4)), BARIS + 20);
			}
			default -> {
				if (react.endsWith("_end") && npc.role().townsfolk() && npc.getRandom().nextBoolean()) {
					npc.say(Component.translatable("kultivator.kota.langit.reda." + npc.getRandom().nextInt(3)), BARIS);
				}
			}
		}
		segarkan(npc);
	}

	private static String jenisTitik(TempatHuni huni, BlockPos pos) {
		for (Titik t : huni.titik("kasur")) {
			if (t.pos().equals(pos)) {
				return "kasur";
			}
		}
		return "";
	}

	/**
	 * The face an activity wears while it lasts (-1 = leave it): joy at festivals, games and shows, grief in mourning,
	 * sleepiness at dawn and in the small hours of the watch, pride in a won bout.
	 */
	static int suasanaKegiatan(String kunci) {
		return switch (kunci) {
			case "festival", "tonton", "main", "main_air", "musik", "kedai", "pasar_malam", "lihat_langit", "bercerita", "dengar_cerita" -> Gaya.SUASANA_SENANG;
			case "berkabung", "takut_petir" -> Gaya.SUASANA_SEDIH;
			case "bangun", "terkantuk", "tidur_siang", "jaga_subuh" -> Gaya.SUASANA_MENGANTUK;
			case "berlindung" -> Gaya.SUASANA_TAKUT;
			case "jaga_ganda", "waspada", "bertahan" -> Gaya.SUASANA_MARAH;
			case "pulih" -> Gaya.SUASANA_SEDIH;
			default -> -1;
		};
	}

	/** Plays the activity's clip with its props (0 = stand). */
	private static void main(KultivatorNpc npc, Kegiatan k) {
		CompoundTag m = npc.memory();
		if (k.klip() == 0) {
			SosokApi.henti(npc);
		} else {
			SosokApi.gerak(npc, k.klip(), k.varian(), 0, k.propKanan(), k.propKiri(), k.propPunggung());
		}
		m.putInt(KLIP, k.klip());
	}

	/** At the goal: the role's once clip now and then, the vendor's call, the children's chase, the chats. */
	private static void diTitik(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, long now) {
		CompoundTag m = npc.memory();
		if (m.getBooleanOr(DI_RUMAH, false)) {
			return;
		}
		Kegiatan k = kegiatanSekarang(level, huni, p, now);
		if (m.contains(OBROL)) {
			obrol(level, npc, now);
			return;
		}
		if (k.klip() == GerakId.MAIN_KEJAR && now >= m.getLongOr(SELA_T, 0L)) {
			// the chase: off to another play spot, laughing
			List<Titik> spots = huni.titik("main");
			if (spots.size() > 1) {
				Titik next = spots.get(npc.getRandom().nextInt(spots.size()));
				m.putInt(TX, next.pos().getX());
				m.putInt(TY, next.pos().getY());
				m.putInt(TZ, next.pos().getZ());
				m.putBoolean(TIBA, false);
				m.putBoolean(TNYATA, false);
				m.putInt(JALAN, GerakId.MAIN_KEJAR);
				SosokApi.gerak(npc, GerakId.MAIN_KEJAR, 0, 0, 0, 0, 0);
				m.putLong(SELA_T, now + 60L + npc.getRandom().nextInt(80));
				return;
			}
		}
		if (now >= m.getLongOr(SELA_T, 0L) && KUNCI_KERJA.contains(k.kunci())) {
			m.putLong(SELA_T, now + 300L + npc.getRandom().nextInt(500));
			KerjaPeran.Kerja kerja = KerjaPeran.untuk(p.peran(), p.benih());
			int sela = kerja.sela();
			Player near = level.getNearestPlayer(npc, 12.0D);
			if (sela == GerakId.SERU_DAGANG) {
				if (near != null) {
					seru(npc, p, near);
				}
			} else if (sela == GerakId.PALU_HAKIM && !"sidang".equals(k.kunci()) && npc.getRandom().nextInt(3) != 0) {
				// the gavel falls only in session, now and then outside it
			} else if (sela != 0) {
				SosokApi.gerak(npc, sela, 0, 0, 0, 0, 0);
			}
		}
		if (now >= m.getLongOr(SELA_T, 0L) && !KUNCI_KERJA.contains(k.kunci())) {
			m.putLong(SELA_T, now + 300L + npc.getRandom().nextInt(600));
			int sela = selaKegiatan(k.kunci());
			if (sela != 0 && npc.getRandom().nextInt(3) != 0) {
				SosokApi.gerak(npc, sela, 0, 0, k.propKanan(), k.propKiri(), k.propPunggung());
			}
		}
		if (m.contains(OBROL)) {
			return;
		}
		boolean santai = switch (k.kunci()) {
			case "obrol", "serambi", "minum_teh", "festival", "tonton", "lihat_langit", "catur", "bercerita", "kedai", "api_unggun",
					"karavanserai", "obrol_jembatan", "teh_jendela", "berdebat", "kedai_racun", "jeda_panas", "makan_dermaga" -> true;
			default -> false;
		};
		if (santai && now - m.getLongOr(OBROL_T, -100000L) >= HidupConfig.get().obrolanDetik * 20L && npc.getRandom().nextInt(3) == 0) {
			mulaiObrol(level, huni, npc, p, now);
		}
		if (HidupConfig.get().transaksiAntarNpc && p.peran().kelompok == NpcRole.Kelompok.PEDAGANG && KUNCI_KERJA.contains(k.kunci())
				&& !k.kunci().equals("sidang") && now >= m.getLongOr(JUAL_T, 0L)) {
			m.putLong(JUAL_T, now + 1200L + npc.getRandom().nextInt(1200));
			cariPembeli(level, huni, npc, now);
		}
	}

	/**
	 * The once clip an everyday activity plays now and then (0 = none): a sip of wine at the tavern, a yawn on the porch
	 * or in the small hours of the watch, a cut at the air in the drill, a hand seal in meditation, a laugh at a story.
	 */
	static int selaKegiatan(String kunci) {
		return switch (kunci) {
			case "kedai", "pesta_kedai" -> GerakId.MINUM_ARAK;
			case "terkantuk", "serambi", "jaga_subuh" -> GerakId.MENGUAP_PEREGANGAN;
			case "latihan_pagi", "latihan_senja", "latihan" -> GerakId.TEBAS_UDARA;
			case "meditasi", "meditasi_malam", "meditasi_kabut" -> GerakId.SEGEL_TANGAN;
			case "dengar_cerita", "bercerita" -> GerakId.TERTAWA;
			case "festival", "festival_panen" -> GerakId.TERTAWA;
			case "tonton", "lihat_kota" -> GerakId.TUNJUK;
			default -> 0;
		};
	}

	/**
	 * Activities that are the role's work at its own spot: the once clip of {@link KerjaPeran} plays now and then (the
	 * vendor's call, the gavel, the whisper of the black market) and vendors trade with passing residents.
	 */
	private static final Set<String> KUNCI_KERJA = Set.of("kerja", "sidang", "pasar_ikan", "bazar", "pasar_bulan", "pasar_air",
			"pasar_gelap", "pasar_malam", "hitung_malam");

	/** A vendor's call to a passer-by (38.N.4 butir 6): the gesture and a line of the day's goods. */
	private static void seru(KultivatorNpc npc, Penduduk p, Player near) {
		SosokApi.gerak(npc, GerakId.SERU_DAGANG, 0, 0, 0, 0, 0);
		hadap(npc, near.position());
		String role = SERU_SENDIRI.contains(p.peran()) ? p.peran().id() : "umum";
		npc.say(Component.translatable("kultivator.hidup.seru." + role + "." + npc.getRandom().nextInt(3)), BARIS + 20);
	}

	// ================================================================== chats (38.N.4 butir 8)

	private static void mulaiObrol(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, long now) {
		KultivatorNpc partner = null;
		for (KultivatorNpc other : level.getEntitiesOfClass(KultivatorNpc.class, npc.getBoundingBox().inflate(4.5D),
				o -> o != npc && o.isAlive() && adalah(o) && o.getTarget() == null && o.memory().getBooleanOr(TIBA, false)
						&& !o.memory().contains(OBROL) && !o.memory().getBooleanOr(SIBUK, false) && !o.memory().getBooleanOr(DI_RUMAH, false))) {
			partner = other;
			break;
		}
		if (partner == null) {
			return;
		}
		CompoundTag m = npc.memory();
		m.putInt(OBROL, partner.getId());
		m.putInt(OBROL_STEP, 0);
		m.putLong(OBROL_NEXT, now);
		m.putString(OBROL_TOPIK, topik(level, huni, npc));
		m.putLong(OBROL_T, now);
		partner.memory().putBoolean(SIBUK, true);
		partner.memory().putLong(OBROL_T, now);
		SosokApi.tatap(npc, partner);
		SosokApi.tatap(partner, npc);
		hadap(npc, partner.position());
		hadap(partner, npc.position());
		obrol(level, npc, now);
	}

	/**
	 * The topic: the sky first, the rain, the market's trade, dusk; then the neighbourhood's own news (a death in a house,
	 * a newcomer, someone back from a wound), the night, market and rest days, the season and the speaker's own trade
	 * ({@link #TOPIK_SENDIRI}, keys {@code kultivator.hidup.obrol.*}); else one of the town's old topics.
	 */
	private static String topik(ServerLevel level, TempatHuni huni, KultivatorNpc npc) {
		SkyKind sky = huni.cuaca.langit();
		if (sky != null) {
			return "langit_" + sky.id();
		}
		if (huni.cuaca.hujan() && npc.getRandom().nextBoolean()) {
			return "cuaca";
		}
		if (huni.laris > 0 && npc.getRandom().nextInt(3) == 0) {
			huni.laris = Math.max(0, huni.laris - 1);
			return "harga";
		}
		long waktu = Pemanggil.waktu(huni, level);
		long tod = Math.floorMod(waktu, 24000L);
		if (tod >= 12000L && tod < 13500L && npc.getRandom().nextBoolean()) {
			return "senja";
		}
		int r = npc.getRandom().nextInt(10);
		if (r < 3 && huni.keadaan.containsValue(PendudukData.DUKA)) {
			return "duka_tetangga";
		}
		if (r < 3 && huni.keadaan.containsValue(PendudukData.DATANG)) {
			return "pendatang";
		}
		if (r < 2 && huni.keadaan.containsValue(PendudukData.PULIH)) {
			return "pulih_tetangga";
		}
		Penduduk p = penduduk(npc);
		if (p == null) {
			return TOPIK[npc.getRandom().nextInt(TOPIK.length)];
		}
		Jadwal.Saat s = Jadwal.saat(p, huni.jenis, waktu);
		if ((s.jam() >= 20 || s.jam() < 4) && r < 4) {
			return "malam";
		}
		if (s.hariPekan() == 0 && r < 3) {
			return "hari_pasar";
		}
		if (s.hariPekan() == 6 && r < 3) {
			return "hari_libur";
		}
		if (r < 3) {
			return "musim_" + s.musim();
		}
		if (r < 6) {
			return "kerja_" + kerjaTopik(p.peran());
		}
		return TOPIK[npc.getRandom().nextInt(TOPIK.length)];
	}

	/** The trade a resident talks shop about ({@code kerja_<x>} topics). */
	private static String kerjaTopik(NpcRole peran) {
		if (peran.iblis()) {
			return "iblis";
		}
		return switch (peran.kelompok) {
			case PEDAGANG -> "pedagang";
			case PENJAGA -> "penjaga";
			case KULTIVATOR -> "kultivator";
			case CENDEKIA, RAHIB -> "cendekia";
			case TETUA -> "tetua";
			case PEJABAT -> "pejabat";
			case ANAK -> "anak";
			default -> "pekerja";
		};
	}

	/** Topics whose four lines live in this module's lang ({@code kultivator.hidup.obrol.<topic>.<0-3>}). */
	static final Set<String> TOPIK_SENDIRI = Set.of("musim_0", "musim_1", "musim_2", "musim_3", "kerja_pekerja", "kerja_pedagang",
			"kerja_penjaga", "kerja_kultivator", "kerja_cendekia", "kerja_tetua", "kerja_pejabat", "kerja_anak", "kerja_iblis",
			"hari_pasar", "hari_libur", "malam", "duka_tetangga", "pendatang", "pulih_tetangga");

	private static void obrol(ServerLevel level, KultivatorNpc npc, long now) {
		CompoundTag m = npc.memory();
		Entity found = level.getEntity(m.getIntOr(OBROL, -1));
		if (!(found instanceof KultivatorNpc partner) || !partner.isAlive() || partner.distanceToSqr(npc) > 49.0D || partner.getTarget() != null) {
			akhiriObrol(level, npc);
			return;
		}
		if (now < m.getLongOr(OBROL_NEXT, 0L)) {
			return;
		}
		int step = m.getIntOr(OBROL_STEP, 0);
		if (step >= 4) {
			String done = m.getStringOr(OBROL_TOPIK, "");
			if (!done.startsWith("langit_") && !done.equals("duka_tetangga") && !done.equals("gosip") && npc.getRandom().nextInt(3) == 0) {
				// a friendly pat on the shoulder to part
				hadap(npc, partner.position());
				SosokApi.gerak(npc, GerakId.TEPUK_BAHU, 0, 0, 0, 0, 0);
			}
			akhiriObrol(level, npc);
			return;
		}
		KultivatorNpc speaker = step % 2 == 0 ? npc : partner;
		KultivatorNpc listener = speaker == npc ? partner : npc;
		String topic = m.getStringOr(OBROL_TOPIK, "harga");
		int gesture = switch (topic) {
			case "gosip", "langit_blood_eclipse" -> step == 2 ? GerakId.BISIK : GerakId.BICARA_TANGAN;
			case "harga", "lelang" -> step == 3 ? GerakId.BERDEBAT : GerakId.BICARA_TANGAN;
			case "anak", "makanan" -> step == 3 ? GerakId.TERTAWA : GerakId.BICARA_TANGAN;
			case "langit_meteor_shower", "langit_twin_moons" -> step % 2 == 0 ? GerakId.TUNJUK : GerakId.TATAP_LANGIT;
			case "duka_tetangga" -> step % 2 == 0 ? GerakId.BISIK : GerakId.SIKAP_TUNDUK;
			case "pendatang", "kerja_iblis" -> step == 1 ? GerakId.BISIK : GerakId.BICARA_TANGAN;
			case "hari_libur", "malam", "kerja_anak" -> step == 3 ? GerakId.TERTAWA : GerakId.BICARA_TANGAN;
			case "kerja_pedagang", "hari_pasar" -> step == 2 ? GerakId.BERDEBAT : GerakId.BICARA_TANGAN;
			default -> step == 1 ? GerakId.TERTAWA : GerakId.BICARA_TANGAN;
		};
		SosokApi.gerak(speaker, gesture, 0, BARIS, 0, 0, 0);
		SosokApi.gerak(listener, GerakId.ANGGUK_DENGAR, 0, BARIS, 0, 0, 0);
		String prefix = TOPIK_SENDIRI.contains(topic) ? "kultivator.hidup.obrol." : "kultivator.kota.obrol.";
		speaker.say(Component.translatable(prefix + topic + "." + step), BARIS);
		m.putInt(OBROL_STEP, step + 1);
		m.putLong(OBROL_NEXT, now + BARIS + 10L);
	}

	private static void akhiriObrol(ServerLevel level, KultivatorNpc npc) {
		CompoundTag m = npc.memory();
		if (!m.contains(OBROL)) {
			return;
		}
		Entity found = level.getEntity(m.getIntOr(OBROL, -1));
		if (found instanceof KultivatorNpc partner) {
			partner.memory().remove(SIBUK);
			SosokApi.tatap(partner, null);
		}
		SosokApi.tatap(npc, null);
		m.remove(OBROL);
		m.remove(OBROL_STEP);
		m.remove(OBROL_NEXT);
		m.remove(OBROL_TOPIK);
	}

	// ================================================================== market bargaining (38.N.4 butir 3)

	private static void cariPembeli(ServerLevel level, TempatHuni huni, KultivatorNpc vendor, long now) {
		for (KultivatorNpc other : level.getEntitiesOfClass(KultivatorNpc.class, vendor.getBoundingBox().inflate(16.0D),
				o -> o != vendor && o.isAlive() && adalah(o) && o.getTarget() == null && !o.memory().contains(OBROL) && !o.memory().contains(BELI)
						&& !o.memory().getBooleanOr(SIBUK, false) && !o.memory().getBooleanOr(DI_RUMAH, false))) {
			Penduduk buyer = penduduk(other);
			if (buyer == null || buyer.peran().kelompok == NpcRole.Kelompok.PEDAGANG || buyer.peran().kelompok == NpcRole.Kelompok.PENJAGA
					|| buyer.peran().kelompok == NpcRole.Kelompok.ANAK) {
				continue;
			}
			CompoundTag m = other.memory();
			m.putInt(BELI, vendor.getId());
			m.putLong(BELI_T, 0L);
			vendor.memory().putBoolean(SIBUK, true);
			Vec3 front = vendor.position().add(Vec3.directionFromRotation(0.0F, vendor.getYRot()).scale(1.6D));
			m.putInt(TX, Mth.floor(front.x));
			m.putInt(TY, Mth.floor(vendor.getY()));
			m.putInt(TZ, Mth.floor(front.z));
			m.putBoolean(TIBA, false);
			m.putBoolean(TNYATA, false);
			m.putInt(JALAN, GerakId.JALAN_SANTAI);
			SosokApi.gerak(other, GerakId.JALAN_SANTAI, 0, 0, PropId.KANTONG_UANG, 0, 0);
			return;
		}
		vendor.memory().remove(SIBUK);
	}

	/** The buyer's side: walk, bargain 60 ticks, swap money for goods, bow, back to the schedule. */
	private static boolean beli(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, long now) {
		CompoundTag m = npc.memory();
		if (!m.contains(BELI)) {
			return false;
		}
		Entity found = level.getEntity(m.getIntOr(BELI, -1));
		if (!(found instanceof KultivatorNpc vendor) || !vendor.isAlive() || vendor.distanceToSqr(npc) > 24.0D * 24.0D) {
			selesaiBeli(npc, null);
			return false;
		}
		long t = m.getLongOr(BELI_T, 0L);
		if (t == 0L) {
			if (npc.distanceToSqr(vendor) > 2.6D * 2.6D) {
				if (npc.getNavigation().isDone() && Pemanggil.bolehJalur()) {
					npc.getNavigation().moveTo(vendor, 0.55D);
				}
				return true;
			}
			npc.getNavigation().stop();
			hadap(npc, vendor.position());
			hadap(vendor, npc.position());
			SosokApi.tatap(npc, vendor);
			SosokApi.tatap(vendor, npc);
			SosokApi.gerak(npc, GerakId.TUNJUK, 0, 30, 0, PropId.KANTONG_UANG, 0);
			SosokApi.gerak(vendor, GerakId.TAWAR, 0, 60, 0, 0, 0);
			npc.say(Component.translatable("kultivator.hidup.tawar.beli." + npc.getRandom().nextInt(3)), 50);
			m.putLong(BELI_T, now + 60L);
			return true;
		}
		if (now < t) {
			return true;
		}
		// the deal: goods for the buyer, the money purse for the vendor, a small bow both ways
		vendor.say(Component.translatable("kultivator.hidup.tawar.jual." + vendor.getRandom().nextInt(3)), 50);
		SosokApi.gerak(vendor, GerakId.HORMAT, 0, 0, PropId.KANTONG_UANG, 0, 0);
		int barang = npc.getRandom().nextBoolean() ? PropId.KERANJANG : PropId.PETI;
		SosokApi.gerak(npc, GerakId.HORMAT, 0, 0, barang, 0, 0);
		suasana(vendor, Gaya.SUASANA_SENANG, 120);
		suasana(npc, Gaya.SUASANA_SENANG, 80);
		huni.laris++;
		selesaiBeli(npc, vendor);
		npc.memory().putString(KUNCI, "");
		return true;
	}

	private static void selesaiBeli(KultivatorNpc npc, @Nullable KultivatorNpc vendor) {
		npc.memory().remove(BELI);
		npc.memory().remove(BELI_T);
		SosokApi.tatap(npc, null);
		if (vendor != null) {
			vendor.memory().remove(SIBUK);
			SosokApi.tatap(vendor, null);
		}
	}

	// ================================================================== paired sparring (66/67)

	private static void pasangkanTanding(ServerLevel level, KultivatorNpc npc, long now) {
		for (KultivatorNpc other : level.getEntitiesOfClass(KultivatorNpc.class, npc.getBoundingBox().inflate(5.0D),
				o -> o != npc && adalah(o) && o.memory().getBooleanOr(TIBA, false) && !o.memory().contains(TANDING)
						&& (o.memory().getIntOr(KLIP, 0) == GerakId.TANDING_A || o.memory().getIntOr(KLIP, 0) >= GerakId.LATIHAN_PEDANG_1
								&& o.memory().getIntOr(KLIP, 0) <= GerakId.LATIHAN_TOMBAK))) {
			hadap(npc, other.position());
			hadap(other, npc.position());
			SosokApi.gerakSinkron(npc, GerakId.TANDING_A, now);
			SosokApi.gerakSinkron(other, GerakId.TANDING_B, now);
			npc.memory().putInt(TANDING, other.getId());
			other.memory().putInt(TANDING, npc.getId());
			other.memory().putInt(KLIP, GerakId.TANDING_B);
			return;
		}
	}

	private static void akhiriTanding(ServerLevel level, KultivatorNpc npc) {
		CompoundTag m = npc.memory();
		if (!m.contains(TANDING)) {
			return;
		}
		Entity found = level.getEntity(m.getIntOr(TANDING, -1));
		if (found instanceof KultivatorNpc other) {
			other.memory().remove(TANDING);
			other.memory().putString(KUNCI, "");
		}
		m.remove(TANDING);
	}

	// ================================================================== players (38.N.4 butir 9)

	private static void pemain(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, long now) {
		CompoundTag m = npc.memory();
		if (m.contains(OBROL) || m.getBooleanOr(SIBUK, false) || m.getIntOr(KLIP, 0) == GerakId.TIDUR) {
			return;
		}
		Player found = level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(), LIHAT, each -> !each.isSpectator());
		if (!(found instanceof ServerPlayer player) || !npc.hasLineOfSight(player)) {
			return;
		}
		String key = "hd_l_" + Integer.toHexString(player.getUUID().hashCode());
		if (now - m.getLongOr(key, -LIHAT_JEDA) < LIHAT_JEDA) {
			return;
		}
		m.putLong(key, now);
		SosokApi.tatap(npc, player);
		m.putLong(TATAP_T, now + 40L);
		NpcRole peran = p.peran();
		long town = huni.situsId;
		if (peran.townsfolk() && buronan(player, town)) {
			SosokApi.gerak(npc, GerakId.TUNJUK, 0, 40, 0, 0, 0);
			suasana(npc, peran.anak() ? Gaya.SUASANA_TAKUT : Gaya.SUASANA_MARAH, 200);
			npc.say(Component.translatable("kultivator.hidup.buronan." + npc.getRandom().nextInt(3)), BARIS);
			panggilPenjaga(level, npc, player.position(), now);
			return;
		}
		if (peran.townsfolk() && takut(player, town)) {
			suasana(npc, Gaya.SUASANA_TAKUT, 160);
			if (peran.anak()) {
				SosokApi.gerak(npc, GerakId.TAKUT_SEMBUNYI, 0, 100, 0, 0, 0);
			} else {
				SosokApi.gerak(npc, GerakId.BERLUTUT, 0, 100, 0, 0, 0);
				npc.say(Component.translatable("kultivator.hidup.takut." + npc.getRandom().nextInt(3)), BARIS);
			}
			return;
		}
		int theirs = Cultivators.get(player).realm().ordinal();
		int rep = KotaData.get(level.getServer()).reputation(town, player.getUUID());
		int met = PendudukData.get(level.getServer()).kenal(town, p.id(), player.getUUID());
		if (theirs >= npc.realm() + 4 && !peran.iblis()) {
			// step aside and bow deep
			Vec3 side = Vec3.directionFromRotation(0.0F, npc.getYRot() + 90.0F).scale(1.5D);
			if (npc.getNavigation().isDone() && Pemanggil.bolehJalur()) {
				npc.getNavigation().moveTo(npc.getX() + side.x, npc.getY(), npc.getZ() + side.z, 0.6D);
			}
			hadap(npc, player.position());
			SosokApi.gerak(npc, GerakId.HORMAT_DALAM, 0, 0, 0, 0, 0);
			suasana(npc, Gaya.SUASANA_MALU, 120);
			return;
		}
		if (rep <= -30 && peran.townsfolk()) {
			SosokApi.gerak(npc, npc.getRandom().nextBoolean() ? GerakId.MARAH_TUNJUK : GerakId.BISIK, 0, 0, 0, 0, 0);
			suasana(npc, Gaya.SUASANA_MARAH, 160);
			npc.say(Component.translatable("kultivator.hidup.marah." + npc.getRandom().nextInt(3)), BARIS);
			return;
		}
		if (rep >= 80 || met >= 5 && rep >= 0) {
			hadap(npc, player.position());
			SosokApi.gerak(npc, GerakId.LAMBAI, 0, 0, 0, 0, 0);
			suasana(npc, Gaya.SUASANA_SENANG, 160);
			npc.say(Component.translatable("kultivator.hidup.sapa.nama." + npc.getRandom().nextInt(3), player.getName()), BARIS);
			return;
		}
		int state = huni.keadaanDari(p.id());
		if (state != PendudukData.SEHAT) {
			// the resident's own news first: the newcomer introduces itself, the hurt one and the mourner say a word
			hadap(npc, player.position());
			String which = switch (state) {
				case PendudukData.DATANG -> "datang";
				case PendudukData.PULIH -> "pulih";
				default -> "duka";
			};
			if (state == PendudukData.DATANG) {
				SosokApi.gerak(npc, GerakId.LAMBAI, 0, 0, 0, 0, 0);
			} else if (state == PendudukData.DUKA) {
				SosokApi.gerak(npc, GerakId.SIKAP_TUNDUK, 0, 60, 0, 0, 0);
			}
			npc.say(Component.translatable("kultivator.hidup.keadaan." + which + "." + npc.getRandom().nextInt(4)), BARIS + 20);
			return;
		}
		if (theirs >= npc.realm() + 2 && !peran.iblis()) {
			hadap(npc, player.position());
			SosokApi.gerak(npc, GerakId.HORMAT, 0, 0, 0, 0, 0);
			return;
		}
		if (npc.getRandom().nextInt(3) == 0) {
			sapa(level, huni, npc, p, player);
		}
	}

	/**
	 * An everyday greeting to a passer-by (one glance in three): children wave and chirp, guards nod a warning, vendors
	 * invite, elders bless, cultivators salute fist in palm, the demonic city sneers; everyone else greets by the hour.
	 */
	private static void sapa(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, ServerPlayer player) {
		NpcRole peran = p.peran();
		hadap(npc, player.position());
		String key;
		if (peran.iblis()) {
			key = "iblis";
			suasana(npc, Gaya.SUASANA_BANGGA, 100);
		} else {
			switch (peran.kelompok) {
				case ANAK -> {
					key = "anak";
					SosokApi.gerak(npc, GerakId.LAMBAI, 0, 0, 0, 0, 0);
					suasana(npc, Gaya.SUASANA_SENANG, 120);
				}
				case PENJAGA -> {
					key = "penjaga";
					SosokApi.gerak(npc, GerakId.ANGGUK_DENGAR, 0, 30, 0, 0, 0);
				}
				case PEDAGANG -> {
					key = "pedagang";
					SosokApi.gerak(npc, GerakId.LAMBAI, 0, 0, 0, 0, 0);
				}
				case TETUA -> {
					key = "tetua";
					SosokApi.gerak(npc, GerakId.ANGGUK_DENGAR, 0, 30, 0, 0, 0);
				}
				case KULTIVATOR -> {
					key = "kultivator";
					SosokApi.gerak(npc, GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 0, 0, 0, 0);
				}
				default -> {
					int jam = Jadwal.saat(p, huni.jenis, Pemanggil.waktu(huni, level)).jam();
					key = jam >= 5 && jam < 11 ? "pagi" : jam < 15 ? "siang" : jam < 19 ? "sore" : "malam";
					SosokApi.gerak(npc, GerakId.ANGGUK_DENGAR, 0, 30, 0, 0, 0);
				}
			}
		}
		npc.say(Component.translatable("kultivator.hidup.sapa." + key + "." + npc.getRandom().nextInt(3)), BARIS);
	}

	private static boolean buronan(ServerPlayer player, long town) {
		try {
			return KotaBesarApi.buronan(player, town);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static boolean takut(ServerPlayer player, long town) {
		try {
			return PerbaikanApi.ditakuti(player, town);
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Guards of the place within 24 blocks come to {@code at} and call a halt (no blow: the law module arrests). */
	private static void panggilPenjaga(ServerLevel level, KultivatorNpc caller, Vec3 at, long now) {
		for (KultivatorNpc guard : level.getEntitiesOfClass(KultivatorNpc.class, caller.getBoundingBox().inflate(24.0D),
				o -> o.isAlive() && adalah(o) && o.role().kelompok == NpcRole.Kelompok.PENJAGA && o.getTarget() == null)) {
			CompoundTag g = guard.memory();
			if (now - g.getLongOr(KEJAR_T, -1000L) < 400L) {
				continue;
			}
			g.putLong(KEJAR_T, now);
			g.putInt(TX, Mth.floor(at.x));
			g.putInt(TY, Mth.floor(at.y));
			g.putInt(TZ, Mth.floor(at.z));
			g.putBoolean(TIBA, false);
			g.putBoolean(TNYATA, false);
			g.putInt(JALAN, GerakId.LARI);
			g.putString(KUNCI, "");
			SosokApi.gerak(guard, GerakId.LARI, 0, 0, 0, 0, 0);
			guard.say(Component.translatable("kultivator.kota.penjaga.halt." + guard.getRandom().nextInt(3)), BARIS);
		}
	}

	// ================================================================== crime (38.N.4 butir 10)

	/** A crime by {@code player} in town {@code town}: witnesses point and run to a guard, the rest back off and stare. */
	static void kejahatan(ServerPlayer player, long town) {
		ServerLevel level = player.level();
		TempatHuni huni = Pemanggil.huni(town);
		if (huni == null) {
			return;
		}
		long now = level.getGameTime();
		boolean first = true;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(HidupApi.SAKSI_JARAK),
				o -> o.isAlive() && o.penduduk() && o.situsPenduduk() == town && o.hasLineOfSight(player))) {
			SosokApi.tatap(npc, player);
			npc.memory().putLong(TATAP_T, now + 100L);
			if (npc.role().kelompok == NpcRole.Kelompok.PENJAGA) {
				continue;
			}
			if (first && !npc.role().anak()) {
				first = false;
				SosokApi.gerak(npc, GerakId.TUNJUK, 0, 30, 0, 0, 0);
				suasana(npc, Gaya.SUASANA_MARAH, 200);
				npc.say(Component.translatable("kultivator.hidup.pencuri." + npc.getRandom().nextInt(3)), BARIS);
				panggilPenjaga(level, npc, player.position(), now);
				continue;
			}
			// back off eight blocks and stare, hand over the mouth
			Vec3 away = npc.position().subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
			if (away.lengthSqr() > 1.0E-4D && npc.getNavigation().isDone() && Pemanggil.bolehJalur()) {
				Vec3 to = npc.position().add(away.normalize().scale(8.0D));
				npc.getNavigation().moveTo(to.x, npc.getY(), to.z, 0.8D);
			}
			SosokApi.gerak(npc, GerakId.KAGET, 0, 0, 0, 0, 0);
			suasana(npc, Gaya.SUASANA_TAKUT, 300);
			npc.memory().putString(KUNCI, "");
		}
	}

	// ================================================================== danger (38.N.4 butir 11)

	/**
	 * Danger near the body: a marked danger zone ({@link HidupApi#bahaya}), or a monster within 16 blocks (which marks
	 * one for its neighbours). Civilians flee to a door and go in (or hide); guards draw and fight; fighters join.
	 * Returns true while it handles danger.
	 */
	private static boolean bahaya(ServerLevel level, TempatHuni huni, KultivatorNpc npc, Penduduk p, long now) {
		CompoundTag m = npc.memory();
		long takutSampai = m.getLongOr(TAKUT, 0L);
		boolean cek = now % 40L < 20L;
		HidupApi.Bahaya zone = cek ? HidupApi.bahayaDi(level, npc.blockPosition()) : null;
		if (cek && zone == null) {
			List<Monster> monsters = level.getEntitiesOfClass(Monster.class, npc.getBoundingBox().inflate(BAHAYA_MONSTER, 6.0D, BAHAYA_MONSTER),
					mob -> mob.isAlive() && !mob.isSpectator());
			if (!monsters.isEmpty()) {
				HidupApi.bahaya(level, monsters.getFirst().blockPosition(), 60);
				zone = HidupApi.bahayaDi(level, npc.blockPosition());
			}
		}
		if (zone == null) {
			if (takutSampai != 0L && now >= takutSampai) {
				m.remove(TAKUT);
				m.putString(KUNCI, "");
			}
			return takutSampai != 0L && now < takutSampai;
		}
		NpcRole peran = p.peran();
		if (peran.kelompok == NpcRole.Kelompok.PENJAGA || peran.petarung() && peran.kelompok != NpcRole.Kelompok.PEDAGANG) {
			if (!npc.fightsMonsters()) {
				npc.setFightsMonsters(true);
				SosokApi.gerak(npc, GerakId.HUNUS, 0, 0, 0, 0, 0);
				suasana(npc, Gaya.SUASANA_MARAH, 600);
			}
			return false;
		}
		if (takutSampai != 0L && now < takutSampai) {
			// already fleeing: in at the door
			BlockPos goal = target(npc);
			if (goal != null && npc.position().distanceToSqr(Vec3.atBottomCenterOf(goal)) < 2.5D && m.getBooleanOr(TNYATA, false)) {
				Pemanggil.lepas(huni, npc);
			} else if (goal != null && npc.getNavigation().isDone() && Pemanggil.bolehJalur()) {
				npc.getNavigation().moveTo(goal.getX() + 0.5D, goal.getY(), goal.getZ() + 0.5D, 1.0D);
			}
			return true;
		}
		m.putLong(TAKUT, zone.sampai() + 1200L);
		akhiriObrol(level, npc);
		suasana(npc, Gaya.SUASANA_TAKUT, (int) Math.min(2400L, Math.max(200L, zone.sampai() + 1200L - now)));
		Titik door = null;
		double best = 24.0D * 24.0D;
		for (Titik t : huni.titik("pintu")) {
			double d = t.pos().distSqr(npc.blockPosition());
			if (d < best && t.pos().distSqr(zone.pusat()) > npc.blockPosition().distSqr(zone.pusat()) * 0.5D) {
				best = d;
				door = t;
			}
		}
		npc.say(Component.translatable("kultivator.hidup.lari." + npc.getRandom().nextInt(4)), 50);
		if (door == null) {
			SosokApi.gerak(npc, GerakId.TAKUT_SEMBUNYI, 0, 0, 0, 0, 0);
			m.remove(TX);
			m.putBoolean(TIBA, true);
			return true;
		}
		m.putInt(TX, door.pos().getX());
		m.putInt(TY, door.pos().getY());
		m.putInt(TZ, door.pos().getZ());
		m.putBoolean(TNYATA, true);
		m.putBoolean(TIBA, false);
		m.putInt(JALAN, GerakId.LARI_TAKUT);
		SosokApi.gerak(npc, GerakId.LARI_TAKUT, 0, 0, 0, 0, 0);
		if (Pemanggil.bolehJalur()) {
			npc.getNavigation().moveTo(door.pos().getX() + 0.5D, door.pos().getY(), door.pos().getZ() + 0.5D, 1.0D);
		}
		return true;
	}

	// ================================================================== helpers

	/**
	 * A face for the moment (DESIGN 40 moods, {@link Gaya}{@code .SUASANA_*}): fear while fleeing, anger at a thief, a
	 * smile for a friend or a good sale; back to neutral after {@code tick} ticks (0 = until replaced).
	 */
	static void suasana(KultivatorNpc npc, int suasana, int tick) {
		SosokApi.setSuasana(npc, suasana, Math.max(0, tick));
	}

	/** Turns head and body toward {@code at}. */
	static void hadap(KultivatorNpc npc, Vec3 at) {
		double dx = at.x - npc.getX();
		double dz = at.z - npc.getZ();
		if (dx * dx + dz * dz < 1.0E-4D) {
			return;
		}
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		npc.setYRot(yaw);
		npc.setYHeadRot(yaw);
		npc.setYBodyRot(yaw);
		npc.getLookControl().setLookAt(at.x, at.y + 1.4D, at.z);
	}

	/** What the body does now, for commands: activity key and whether it has arrived. */
	static String keadaan(KultivatorNpc npc) {
		CompoundTag m = npc.memory();
		return m.getStringOr(KUNCI, "-") + (m.getBooleanOr(TIBA, false) ? " @" : " ->") + (m.contains(TAKUT) ? " !takut" : "")
				+ (m.contains(OBROL) ? " obrol" : "") + (m.contains(BELI) ? " beli" : "");
	}

	/** Forces the schedule to be re-read on the next pass (commands, events). */
	static void segarkan(KultivatorNpc npc) {
		npc.memory().putString(KUNCI, "");
	}

	/** Plays clip {@code klip} now on a resident (commands), pausing the schedule for {@code tick} ticks. */
	static void paksaKlip(KultivatorNpc npc, int klip, int prop, int tick) {
		SosokApi.gerak(npc, klip, 0, tick, prop, 0, 0);
	}
}
