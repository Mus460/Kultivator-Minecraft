package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.gua.GuaEntities;
import dev.kultivator.dunia.tempat.gua.PintuTebingEntity;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Penyerbuan Lurus (DESIGN 38.T.7.10): every 20 &plusmn; 5 world days (a hash of the town and the cycle), or on chapter 5 of
 * {@code tema_iblis_rantai}, a coalition of the nearest righteous sects besieges the Skull Gate in three waves (calendar
 * event {@code penyerbuan}, 6000-12000, daylight). No block ever changes: the gate is an entity and stays shut
 * ({@link Gerbang#tick}), the attackers are PEMBURU_LURUS bodies, the defenders PENEGAK_IBLIS bodies.
 * <ul>
 * <li>Live while a player is within 128 blocks of the gate: 8-14 hunters in three waves (3/4/5, +2 on chapter 5) outside
 * the jaws against 5 enforcers inside; a wave advances when its hunters fall or after two minutes. Otherwise the result
 * is data (the city holds 3 times in 5).</li>
 * <li>The player picks a side by talking (Han Qingshuang outside, Tu Gang inside; effect {@code iblis_pihak lurus|kota}):
 * <b>help the righteous</b> (Infami -10, standing +5; captives freed at the end if they win) or <b>defend the city</b>
 * (Infami +10, standing -5, Gentar +10). The enforcers hunt a player of the righteous side for the rest of the siege.</li>
 * <li>Results (data): righteous victory - the pens stay empty 5 days, the market shut 2 days; city victory - the gate toll
 * x1.5 for 3 days.</li>
 * <li>Chapter 5 by route: <b>Lurus</b> the lever of the toll house (six guards; pulling it holds the gate open, the hunters
 * storm in: righteous victory); <b>Penebusan</b> the Old Tunnel (its rubble stones slide aside, 6-10 captives follow the
 * player out while enforcers give chase); <b>Darah</b> the player holds the gate and beats Han Qingshuang in a Sword Duel
 * (she withdraws wounded).</li>
 * </ul>
 * Busy entry {@code tempat.pertahanan} for the players inside the fight; the siege never starts on a player in danger
 * (it is a town event, he chooses to join).
 */
public final class PenyerbuanLurus {

	public static final String ACARA = "penyerbuan";
	public static final int DARI = 6000;
	public static final int SAMPAI = 12000;
	public static final int SIKLUS = 20;
	public static final int GOYANG = 5;
	public static final int GELOMBANG = 3;
	public static final long GELOMBANG_TICK = 2L * 60L * 20L;
	public static final double JARAK_HIDUP = 128.0D;
	public static final int PENEGAK = 5;
	public static final int PENJAGA_TUAS = 6;
	/** Canvas spots of the district gerbang_tengkorak: the hunters' line outside, the defenders' line inside. */
	static final double[] LUAR = {48.0D, 118.0D};
	static final double[] DALAM = {48.0D, 86.0D};
	/** The lever room of the toll house (tools/gen_tempat_tema_iblis.py tuas_gerbang at 24.5, 80.5). */
	static final double[] TUAS = {24.5D, 80.5D};

	/** One running siege. */
	static final class Serbu {
		final long situs;
		final boolean khusus;
		final long mulai;
		int gelombang;
		long gelombangMulai;
		final List<KultivatorNpc> pemburu = new ArrayList<>();
		final List<KultivatorNpc> penegak = new ArrayList<>();
		final List<KultivatorNpc> penjagaTuas = new ArrayList<>();
		final Map<UUID, String> pihak = new HashMap<>();
		int pemburuTumbang;
		int penegakTumbang;
		@Nullable String hasil;
		boolean hidup;

		Serbu(long situs, boolean khusus, long mulai) {
			this.situs = situs;
			this.khusus = khusus;
			this.mulai = mulai;
		}
	}

	private static final Map<Long, Serbu> SERBU = new HashMap<>();
	/** Town id -> the lever body, the tunnel stones (outer anchor) and the inner tunnel anchor position. */
	private static final Map<Long, TuasGerbangEntity> TUAS_HIDUP = new HashMap<>();
	private static final Map<Long, List<PintuTebingEntity>> BATU = new HashMap<>();
	private static final Map<Long, Vec3> TEROWONGAN_DALAM = new HashMap<>();
	/** Players escorting captives through the tunnel: player -> captives. */
	private static final Map<UUID, List<KultivatorNpc>> PELARIAN = new HashMap<>();
	/** Towns whose next siege is the player's chapter 5 (route of the player who called it). */
	private static final Map<Long, UUID> BAB5 = new HashMap<>();

	private PenyerbuanLurus() {
	}

	static void init() {
		TemaKalender.daftar(IblisData.TEMA, ACARA, DARI, SAMPAI, PenyerbuanLurus::hariSerbu, new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				mulaiSerbu(level, situsId, dipaksa);
			}

			@Override
			public void tick(ServerLevel level, long situsId, int tickKe) {
				tickSerbu(level, situsId);
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				akhiri(level, situsId);
			}
		});
		Sibuk.register("tempat.pertahanan", p -> {
			for (Serbu s : SERBU.values()) {
				if (s.pihak.containsKey(p.getUUID())) {
					return true;
				}
			}
			return PELARIAN.containsKey(p.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(PenyerbuanLurus::mati);
	}

	static void clear() {
		SERBU.clear();
		TUAS_HIDUP.clear();
		BATU.clear();
		TEROWONGAN_DALAM.clear();
		PELARIAN.clear();
		BAB5.clear();
	}

	// ================================================================== the calendar

	/** Siege k of a town falls on day {@code phase + 20k + wobble(k)}, the wobble in -5..5 (a pure function). */
	static boolean hariSerbu(ServerLevel level, long hari, long situs, BlockPos pusat) {
		long phase = Math.floorMod(situs * 0x9E3779B9L, (long) SIKLUS) + 8L;
		long k = Math.round((hari - phase) / (double) SIKLUS);
		for (long kk = k - 1; kk <= k + 1; kk++) {
			if (kk >= 0 && phase + kk * SIKLUS + goyang(situs, kk) == hari) {
				return true;
			}
		}
		return false;
	}

	private static long goyang(long situs, long k) {
		long h = situs * 0xBF58476D1CE4E5B9L ^ k * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return Math.floorMod(h, 2L * GOYANG + 1L) - GOYANG;
	}

	/** Days until the next natural siege of a town (0 = today), for rumours and the hub. */
	static int hariLagi(long situs, long hari) {
		for (int d = 0; d < 40; d++) {
			if (hariSerbu(null, hari + d, situs, BlockPos.ZERO)) {
				return d;
			}
		}
		return -1;
	}

	public static boolean berlangsung(long situsId) {
		return SERBU.containsKey(situsId);
	}

	/** True while the player stands with the righteous in this town's siege (the enforcers hunt him). */
	public static boolean melawanKota(ServerPlayer p, long situsId) {
		Serbu s = SERBU.get(situsId);
		return s != null && "lurus".equals(s.pihak.get(p.getUUID()));
	}

	/** The quest effect {@code iblis_serbu}: the chapter-5 siege of this player's route starts now in his town. */
	static boolean panggilBab5(ServerPlayer p) {
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			kota = IblisKota.terdekat(p.level(), p.blockPosition(), 600);
		}
		if (kota == null || SERBU.containsKey(kota.situsId())) {
			return false;
		}
		BAB5.put(kota.situsId(), p.getUUID());
		return TemaKalender.paksa(p.level().getServer().overworld(), kota.situsId(), IblisData.TEMA, ACARA, true);
	}

	// ================================================================== a siege

	private static void mulaiSerbu(ServerLevel level, long situs, boolean dipaksa) {
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), situs);
		if (kota == null) {
			return;
		}
		Serbu s = new Serbu(situs, BAB5.containsKey(situs), level.getGameTime());
		SERBU.put(situs, s);
		IblisKota.timpa(level, situs, "tema." + ACARA, SAMPAI - DARI);
		Vec3 gate = IblisKota.lokal(kota, Gerbang.DISTRIK, Gerbang.GX, IblisKota.LY + 1, Gerbang.GZ);
		s.hidup = gate != null && TemaMekanisme.pemainDekat(level, gate, JARAK_HIDUP);
		for (ServerPlayer p : level.players()) {
			if (gate != null && p.position().distanceToSqr(gate) < 400.0D * 400.0D) {
				Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.serbu.mulai").withStyle(ChatFormatting.GOLD),
						Component.translatable("kultivator.kota_tema.iblis.serbu.mulai.sub").withStyle(ChatFormatting.YELLOW), 10, 70, 20);
			}
		}
		CeritaApi.kabar(level, situs, "tema_iblis_serbu_mulai");
		UUID caller = BAB5.get(situs);
		ServerPlayer bab5 = caller == null ? null : level.getServer().getPlayerList().getPlayer(caller);
		if (bab5 != null) {
			String rute = IblisData.rute(bab5);
			s.pihak.put(bab5.getUUID(), "darah".equals(rute) ? "kota" : "lurus");
			if ("lurus".equals(rute)) {
				penjagaTuas(level, kota, s);
			} else if ("penebusan".equals(rute)) {
				bukaTerowongan(level, kota, bab5);
			}
		}
		if (s.hidup) {
			gelombang(level, kota, s);
		}
	}

	private static void gelombang(ServerLevel level, TemaKota.Kota kota, Serbu s) {
		s.gelombang++;
		s.gelombangMulai = level.getGameTime();
		int n = 2 + s.gelombang + (s.khusus ? 1 : 0);
		int tier = Mth.clamp(2 + s.gelombang, 2, 6);
		int realm = Mth.clamp(Realm.FOUNDATION.ordinal() + s.gelombang, 0, Realm.values().length - 1);
		for (int i = 0; i < n && DuniaBudget.mayAdd(level, DuniaBudget.NPC, Vec3.atCenterOf(kota.pusat())); i++) {
			BlockPos at = IblisKota.blok(kota, Gerbang.DISTRIK, (int) LUAR[0] - n + i * 2, IblisKota.LY + 1, (int) LUAR[1] + (i % 2) * 2);
			if (at == null) {
				break;
			}
			boolean han = s.gelombang == GELOMBANG && i == 0 && s.khusus;
			KultivatorNpc h = han ? TokohIblis.panggil(level, kota.situsId(), TokohIblis.HAN_QINGSHUANG, at, IblisKota.yaw(kota, 180.0F))
					: PemburuLurus.prajurit(level, at, IblisKota.yaw(kota, 180.0F), (int) (s.situs * 131L + s.gelombang * 977L + i * 31L), tier, realm);
			if (h == null) {
				continue;
			}
			if (han) {
				h.memory().putBoolean("iblis_pemburu", true);
				h.memory().putBoolean("iblis_serbu", true);
				h.memory().putBoolean("hold", false);
				h.arm(true);
				TechniquePower.markRealmFoe(h);
			}
			s.pemburu.add(h);
		}
		while (s.penegak.size() < PENEGAK) {
			int i = s.penegak.size();
			BlockPos at = IblisKota.blok(kota, Gerbang.DISTRIK, (int) DALAM[0] - 4 + i * 2, IblisKota.LY + 1, (int) DALAM[1]);
			if (at == null) {
				break;
			}
			KultivatorNpc g = HidupApi.tubuh(level, kota.situsId(), NpcRole.PENEGAK_IBLIS, at, IblisKota.yaw(kota, 0.0F), (int) (s.situs * 17L + i * 7919L));
			if (g == null) {
				break;
			}
			g.memory().putBoolean("iblis_serbu", true);
			g.memory().putBoolean("hold", false);
			g.arm(true);
			s.penegak.add(g);
		}
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(Vec3.atCenterOf(kota.pusat())) < 200.0D * 200.0D) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.serbu.gelombang", s.gelombang, GELOMBANG).withStyle(ChatFormatting.GOLD));
			}
		}
		KultivatorNpc voice = s.penegak.isEmpty() ? null : s.penegak.getFirst();
		if (voice != null) {
			Voice.say(voice, "tema.iblis.raid", null);
		}
		level.playSound(null, kota.pusat(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 0.9F);
	}

	private static void penjagaTuas(ServerLevel level, TemaKota.Kota kota, Serbu s) {
		for (int i = 0; i < PENJAGA_TUAS; i++) {
			BlockPos at = IblisKota.blok(kota, Gerbang.DISTRIK, (int) TUAS[0] - 2 + (i % 3) * 2, IblisKota.LY + 1, (int) TUAS[1] - 3 + (i / 3) * 2);
			if (at == null) {
				return;
			}
			KultivatorNpc g = HidupApi.tubuh(level, kota.situsId(), NpcRole.PENEGAK_IBLIS, at, IblisKota.yaw(kota, 0.0F), (int) (s.situs * 23L + i * 104729L));
			if (g != null) {
				g.memory().putBoolean("iblis_serbu", true);
				g.memory().putBoolean("iblis_tuas", true);
				g.memory().putBoolean("hold", true);
				g.arm(true);
				HidupApi.klip(g, GerakId.BERDIRI_JAGA, 0, 0);
				s.penjagaTuas.add(g);
			}
		}
	}

	private static void tickSerbu(ServerLevel level, long situs) {
		Serbu s = SERBU.get(situs);
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), situs);
		if (s == null || kota == null || s.hasil != null) {
			return;
		}
		Vec3 gate = IblisKota.lokal(kota, Gerbang.DISTRIK, Gerbang.GX, IblisKota.LY + 1, Gerbang.GZ);
		boolean near = gate != null && TemaMekanisme.pemainDekat(level, gate, JARAK_HIDUP);
		if (near && !s.hidup) {
			s.hidup = true;
			gelombang(level, kota, s);
		}
		s.pemburu.removeIf(h -> h.isRemoved() || !h.isAlive());
		s.penegak.removeIf(h -> h.isRemoved() || !h.isAlive());
		s.penjagaTuas.removeIf(h -> h.isRemoved() || !h.isAlive());
		if (!s.hidup) {
			return;
		}
		// the two lines find each other; the player's side decides who turns on him
		for (KultivatorNpc h : s.pemburu) {
			if (h.getTarget() == null || !h.getTarget().isAlive()) {
				KultivatorNpc foe = terdekat(h, s.penegak);
				if (foe != null) {
					h.setTarget(foe);
				} else if (gate != null) {
					h.getNavigation().moveTo(gate.x, gate.y, gate.z, 1.0D);
				}
			}
		}
		for (KultivatorNpc g : s.penegak) {
			if (g.getTarget() == null || !g.getTarget().isAlive()) {
				KultivatorNpc foe = terdekat(g, s.pemburu);
				if (foe != null && foe.distanceToSqr(g) < 40.0D * 40.0D) {
					g.setTarget(foe);
				}
			}
		}
		for (Map.Entry<UUID, String> e : s.pihak.entrySet()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				continue;
			}
			List<KultivatorNpc> foes = "lurus".equals(e.getValue()) ? s.penegak : s.pemburu;
			for (KultivatorNpc f : foes) {
				if (!f.isHostileTo(p.getUUID()) && f.distanceToSqr(p) < 24.0D * 24.0D) {
					f.markHostile(p);
				}
			}
		}
		long now = level.getGameTime();
		if (s.pemburu.isEmpty() || now - s.gelombangMulai > GELOMBANG_TICK) {
			if (s.gelombang >= GELOMBANG) {
				selesai(level, kota, s, s.pemburu.isEmpty() || s.pemburuTumbang >= s.penegakTumbang ? "kota" : "lurus");
			} else {
				gelombang(level, kota, s);
			}
		} else if (s.penegak.isEmpty() && s.gelombang >= 2) {
			selesai(level, kota, s, "lurus");
		}
	}

	private static @Nullable KultivatorNpc terdekat(KultivatorNpc from, List<KultivatorNpc> list) {
		KultivatorNpc best = null;
		double bestSq = Double.MAX_VALUE;
		for (KultivatorNpc n : list) {
			double d = n.distanceToSqr(from);
			if (d < bestSq) {
				bestSq = d;
				best = n;
			}
		}
		return best;
	}

	private static void mati(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof KultivatorNpc npc) || !npc.memory().getBooleanOr("iblis_serbu", false)) {
			return;
		}
		for (Serbu s : SERBU.values()) {
			if (s.pemburu.remove(npc)) {
				s.pemburuTumbang++;
				return;
			}
			if (s.penegak.remove(npc)) {
				s.penegakTumbang++;
				return;
			}
			s.penjagaTuas.remove(npc);
		}
	}

	/** The calendar ends the siege (time over): an undecided siege is decided as data. */
	private static void akhiri(ServerLevel level, long situs) {
		Serbu s = SERBU.get(situs);
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), situs);
		if (s == null) {
			return;
		}
		if (s.hasil == null && kota != null) {
			String hasil;
			if (s.hidup) {
				hasil = s.penegakTumbang > s.pemburuTumbang + 2 ? "lurus" : "kota";
			} else {
				long h = situs * 0x2545F4914F6CDD1DL ^ IblisKota.hari(level) * 0x9E3779B97F4A7C15L;
				hasil = Math.floorMod(h >>> 13, 5L) < 3L ? "kota" : "lurus";
			}
			selesai(level, kota, s, hasil);
		}
		SERBU.remove(situs);
		BAB5.remove(situs);
	}

	/** The outcome of a siege: the data of 38.T.7.10, the sides' rewards, rumours, the bodies leave. */
	static void selesai(ServerLevel level, TemaKota.Kota kota, Serbu s, String hasil) {
		if (s.hasil != null) {
			return;
		}
		s.hasil = hasil;
		IblisKota.lepasTimpa(level, kota.situsId(), "tema." + ACARA);
		MinecraftServer server = level.getServer();
		CompoundTag town = IblisData.kota(server, kota.situsId());
		long hari = IblisKota.hari(level);
		if ("lurus".equals(hasil)) {
			town.putLong("kosong_sampai", hari + 5L);
			town.putLong("pasar_tutup_sampai", hari + 2L);
			IblisData.daftar(town, "tawanan").clear();
			CeritaApi.kabar(level, kota.situsId(), "tema_iblis_serbu_lurus");
		} else {
			town.putLong("upeti_naik_sampai", hari + 3L);
			CeritaApi.kabar(level, kota.situsId(), "tema_iblis_serbu_kota");
		}
		IblisData.ubah(server);
		for (Map.Entry<UUID, String> e : s.pihak.entrySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				continue;
			}
			boolean lurus = "lurus".equals(e.getValue());
			String sekte = SekteApi.diplomasi().nearest(kota.pusat(), 0);
			IblisMeter.tambahInfami(p, lurus ? -10 : 10, lurus ? "bantu_lurus" : "bela_kota");
			if (sekte != null) {
				SekteApi.diplomasi().adjust(p.getUUID(), sekte, lurus ? 5 : -5, "38tk.serbu");
			}
			if (!lurus) {
				IblisMeter.tambahGentar(p, kota.situsId(), 10, "bela_kota");
			}
			boolean menang = lurus == "lurus".equals(hasil);
			Msg.title(p, Component.translatable(menang ? "kultivator.kota_tema.iblis.serbu.menang" : "kultivator.kota_tema.iblis.serbu.kalah")
					.withStyle(menang ? ChatFormatting.GOLD : ChatFormatting.GRAY),
					Component.translatable("kultivator.kota_tema.iblis.serbu.hasil." + hasil).withStyle(ChatFormatting.YELLOW), 10, 70, 20);
			CeritaApi.peristiwa(p, IblisCerita.SERBU, (lurus ? "lurus_" : "kota_") + (menang ? "menang" : "kalah"));
			for (KultivatorNpc n : level.getEntitiesOfClass(KultivatorNpc.class, p.getBoundingBox().inflate(64.0D),
					n -> n.isHostileTo(p.getUUID()) && n.role() == NpcRole.PENEGAK_IBLIS)) {
				n.forgive(p.getUUID());
			}
		}
		for (KultivatorNpc n : s.pemburu) {
			n.vanish();
		}
		for (KultivatorNpc n : s.penegak) {
			n.vanish();
		}
		for (KultivatorNpc n : s.penjagaTuas) {
			n.vanish();
		}
		s.pemburu.clear();
		s.penegak.clear();
		s.penjagaTuas.clear();
		List<PintuTebingEntity> stones = BATU.get(kota.situsId());
		if (stones != null) {
			for (PintuTebingEntity stone : stones) {
				stone.tutup();
			}
		}
		TemaKalender.paksa(server.overworld(), kota.situsId(), IblisData.TEMA, ACARA, false);
	}

	/** Dialog effect {@code iblis_pihak lurus|kota}: the player joins a side of the running siege. */
	static void pihak(ServerPlayer p, String side) {
		TemaKota.Kota kota = IblisKota.terdekat(p.level(), p.blockPosition(), 400);
		Serbu s = kota == null ? null : SERBU.get(kota.situsId());
		if (s == null) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.serbu.tidak").withStyle(ChatFormatting.GRAY));
			return;
		}
		s.pihak.put(p.getUUID(), "lurus".equals(side) ? "lurus" : "kota");
		Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.serbu.pihak." + s.pihak.get(p.getUUID())).withStyle(ChatFormatting.GOLD));
	}

	/** Han Qingshuang fell to 10 % in a siege (not a squad): she withdraws; on the Darah route this is the chapter-5 deed. */
	static void hanKalah(ServerPlayer p, KultivatorNpc han) {
		HidupApi.klip(han, GerakId.PINCANG, 0, 80);
		Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.pemburu.han_mundur").withStyle(ChatFormatting.GOLD));
		CeritaApi.peristiwa(p, IblisCerita.SERBU, "han_mundur");
		for (Serbu s : SERBU.values()) {
			s.pemburu.remove(han);
		}
		han.forgive(p.getUUID());
		han.vanish();
	}

	// ================================================================== the lever (route Lurus)

	/** Anchor {@code kota_tema/tuas_gerbang}: the lever body of the toll house. */
	static void nearbyTuas(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, List<ServerPlayer> within) {
		TuasGerbangEntity tuas = TUAS_HIDUP.get(kota.situsId());
		if (tuas != null && !tuas.isRemoved()) {
			return;
		}
		tuas = TemaMekanisme.muncul(level, TemaEntities.TUAS_GERBANG, anchor.position(), anchor.getYRot(), kota, anchor.blockPosition());
		if (tuas != null) {
			tuas.setVarian(TuasGerbangEntity.VARIAN_BIASA);
			tuas.setKeadaan(TuasGerbangEntity.KEADAAN_ATAS);
			TUAS_HIDUP.put(kota.situsId(), IblisAnchors.simpan(anchor, tuas));
		}
	}

	/** A pull of the lever: only route Lurus in the chapter-5 siege, with its guards down. */
	static void tarik(ServerPlayer p, TuasGerbangEntity tuas) {
		Serbu s = SERBU.get(tuas.situsId());
		if (s == null || !s.khusus || !"lurus".equals(IblisData.rute(p)) || !QuestTracker.aktif(p, IblisCerita.QUEST)) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.tuas.terkunci").withStyle(ChatFormatting.GRAY));
			return;
		}
		s.penjagaTuas.removeIf(g -> g.isRemoved() || !g.isAlive());
		if (!s.penjagaTuas.isEmpty()) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.tuas.dijaga", s.penjagaTuas.size()).withStyle(ChatFormatting.RED));
			for (KultivatorNpc g : s.penjagaTuas) {
				g.markHostile(p);
			}
			return;
		}
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		tuas.setKeadaan(TuasGerbangEntity.KEADAAN_BAWAH);
		tuas.setNilai(1.0F);
		level.playSound(null, tuas.getX(), tuas.getY(), tuas.getZ(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.2F, 0.5F);
		level.playSound(null, tuas.getX(), tuas.getY(), tuas.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
		Gerbang.tahanTerbuka(tuas.situsId(), 6000L, level.getGameTime());
		CeritaApi.peristiwa(p, IblisCerita.SERBU, "tuas");
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), tuas.situsId());
		if (kota != null) {
			selesai(level, kota, s, "lurus");
		}
	}

	// ================================================================== the Old Tunnel (route Penebusan)

	/** Anchors {@code kota_tema/terowongan}: outer side - the rubble stones; inner side - where the captives gather. */
	static void nearbyTerowongan(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, List<ServerPlayer> within) {
		String sisi = anchor.data().getStringOr("sisi", "luar");
		if ("dalam".equals(sisi)) {
			TEROWONGAN_DALAM.put(kota.situsId(), anchor.position());
			return;
		}
		List<PintuTebingEntity> stones = BATU.get(kota.situsId());
		if (stones != null) {
			stones.removeIf(st -> st.isRemoved());
			if (!stones.isEmpty()) {
				return;
			}
		}
		stones = new ArrayList<>();
		double yaw = Math.toRadians(anchor.getYRot());
		double ax = Math.cos(yaw);
		double az = Math.sin(yaw);
		for (int dy = 0; dy < 4; dy += 2) {
			for (int d = -1; d <= 1; d++) {
				Vec3 at = anchor.position().add(ax * d, dy, az * d);
				PintuTebingEntity stone = GuaEntities.PINTU_TEBING.create(level, EntitySpawnReason.EVENT);
				if (stone == null) {
					continue;
				}
				stone.snapTo(at.x, at.y, at.z, anchor.getYRot(), 0.0F);
				stone.ikat((player, door) -> Msg.actionBar(player, Component.translatable("kultivator.kota_tema.iblis.terowongan.runtuhan")
						.withStyle(ChatFormatting.GRAY)));
				if (level.addFreshEntity(stone)) {
					stones.add(IblisAnchors.simpan(anchor, stone));
				}
			}
		}
		BATU.put(kota.situsId(), stones);
	}

	/** Route Penebusan, chapter 5: the stones slide aside and 6-10 captives gather at the inner end to follow the player. */
	private static void bukaTerowongan(ServerLevel level, TemaKota.Kota kota, ServerPlayer p) {
		List<PintuTebingEntity> stones = BATU.get(kota.situsId());
		if (stones != null) {
			for (PintuTebingEntity stone : stones) {
				stone.buka();
			}
		}
		Vec3 inner = TEROWONGAN_DALAM.get(kota.situsId());
		if (inner == null) {
			inner = IblisKota.lokal(kota, KandangTawanan.DISTRIK, 61.5D, IblisKota.LY + 1, 79.5D);
		}
		if (inner == null) {
			return;
		}
		List<KultivatorNpc> group = new ArrayList<>();
		int n = 6 + Math.floorMod((int) kota.situsId(), 5);
		for (int i = 0; i < n; i++) {
			KultivatorNpc c = HidupApi.tubuh(level, kota.situsId(), NpcRole.TAWANAN, BlockPos.containing(inner.add(i % 3 - 1, 0.0D, i / 3)), 0.0F,
					(int) (kota.situsId() * 7L + i * 6151L));
			if (c == null) {
				continue;
			}
			c.memory().putBoolean("hold", false);
			c.memory().putBoolean("iblis_pelarian", true);
			c.setCompanion(p.getUUID(), level.getGameTime() + 24000L);
			HidupApi.klip(c, GerakId.LARI_TAKUT, 0, 60);
			group.add(c);
		}
		PELARIAN.put(p.getUUID(), group);
		// the pursuit: three enforcers come down the tunnel after them
		for (int i = 0; i < 3; i++) {
			KultivatorNpc g = HidupApi.tubuh(level, kota.situsId(), NpcRole.PENEGAK_IBLIS, BlockPos.containing(inner.add(-3.0D, 0.0D, i)), 0.0F,
					(int) (kota.situsId() * 3L + i * 15485863L));
			if (g != null) {
				g.memory().putBoolean("hold", false);
				g.memory().putBoolean("iblis_serbu", true);
				g.markHostile(p);
				g.arm(true);
			}
		}
		Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.terowongan.buka").withStyle(ChatFormatting.AQUA),
				Component.translatable("kultivator.kota_tema.iblis.terowongan.buka.sub", group.size()).withStyle(ChatFormatting.GRAY), 10, 60, 20);
	}

	/** Every 40 ticks: escaped captives that are 40 blocks past the town's box count as free. */
	static void tickPelarian(MinecraftServer server) {
		if (PELARIAN.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, List<KultivatorNpc>> e : new ArrayList<>(PELARIAN.entrySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			List<KultivatorNpc> group = e.getValue();
			if (p == null) {
				for (KultivatorNpc c : group) {
					c.vanish();
				}
				PELARIAN.remove(e.getKey());
				continue;
			}
			int free = 0;
			int alive = 0;
			for (KultivatorNpc c : group) {
				if (c.isRemoved() || !c.isAlive()) {
					continue;
				}
				alive++;
				TemaKota.Kota kota = IblisKota.terdekat(p.level(), c.blockPosition(), 300);
				if (kota == null || !kota.kotak().inflatedBy(40).isInside(c.blockPosition())) {
					free++;
				}
			}
			if (alive == 0 || free >= Math.max(1, alive) && free > 0) {
				PELARIAN.remove(e.getKey());
				int saved = free;
				for (KultivatorNpc c : group) {
					if (!c.isRemoved()) {
						HidupApi.klip(c, GerakId.HORMAT_DALAM, 0, 60);
						c.setCompanion(null, 0L);
						c.setLonely(16.0D, 400);
					}
				}
				if (saved > 0) {
					IblisMeter.tambahInfami(p, -saved, "tebus");
					Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.terowongan.selamat", saved).withStyle(ChatFormatting.GOLD),
							Component.empty(), 10, 60, 20);
					CeritaApi.peristiwa(p, IblisCerita.SERBU, "terowongan");
					Voice.say(p, "tema.iblis.dawn", null);
				}
			}
		}
	}

	/** Lines for {@code uji tema iblis serbu}: the running siege or the next day of the nearest Kota Iblis. */
	static Component status(ServerLevel level, TemaKota.Kota kota) {
		Serbu s = SERBU.get(kota.situsId());
		if (s == null) {
			return Component.translatable("kultivator.kota_tema.iblis.cmd.serbu_lagi", hariLagi(kota.situsId(), IblisKota.hari(level)));
		}
		return Component.translatable("kultivator.kota_tema.iblis.cmd.serbu", s.gelombang, GELOMBANG, s.pemburu.size(), s.penegak.size(), s.pemburuTumbang,
				s.penegakTumbang, s.hidup ? "hidup" : "data");
	}

	static int realmPemain(ServerPlayer p) {
		return Cultivators.get(p).realm().ordinal();
	}
}
