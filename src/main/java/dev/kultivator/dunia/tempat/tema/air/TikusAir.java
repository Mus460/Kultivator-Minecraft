package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bahaya Tikus Air (DESIGN 38.T.5.4): the canal thieves. Calendar event {@code air/tikus_air} at night (14000-22000)
 * every 4-7 days per town ({@link #pilihHari}). They rob a stall boat of the Pasar Air Pagi (moored for the night while
 * the event runs) or the Rumah Sutra, never a player: when a player is within {@link #JARAK_SAKSI} blocks of the prey
 * (and not busy, {@code Sibuk}), a thief appears on the bank, a resident points and shouts "Pencuri!", and the thief
 * flees in the black skiff ({@link PerahuKanalEntity#VARIAN_HITAM}, {@code KEADAAN_KEJAR}) along the Perahu Pos line at
 * {@link #LAJU} blocks a tick (a sprinting player, a hired boat or a flyer can catch him). A hit on the skiff or on the
 * thief stops them: the goods are returned, Nama Baik +3 and 15 x (band + 1) NB. After {@link #LAMA_KABUR} ticks or the
 * end of the line he is gone. The thieves never attack; they become realm foes only when they fight back (never here).
 * <p>
 * Chain {@code tema_air_benang} stage {@code kejar}: the thief is Lao Shu himself; caught, he stays on the bank for the
 * chain's dialog (hand him to the guards or let him go as an informer, flag {@code air_lao_shu_mata}).
 */
public final class TikusAir {

	public static final String ACARA = "tikus_air";
	public static final int DARI = 14000;
	public static final int SAMPAI = 22000;
	public static final double JARAK_SAKSI = 40.0D;
	public static final double LAJU = 0.22D;
	public static final int LAMA_KABUR = 1500;
	public static final int LAMA_RAMPOK = 60;
	public static final long HADIAH_DASAR = 15L;
	public static final String QUEST = "tema_air_benang";
	/** Guild mission kind of inti's {@code TemaMisi} this event fulfils. */
	public static final String MISI = "pencuri_kanal";

	private enum Fase { TUNGGU, RAMPOK, KABUR, SELESAI }

	private static final class Kejadian {
		final long situs;
		Fase fase = Fase.TUNGGU;
		long sejak;
		@Nullable Entity mangsa;
		@Nullable KultivatorNpc pencuri;
		@Nullable PerahuKanalEntity sampan;
		double s;
		boolean laoShu;
		@Nullable ServerPlayer saksi;

		Kejadian(long situs) {
			this.situs = situs;
		}
	}

	private static final Map<Long, Kejadian> JALAN = new HashMap<>();

	static void init() {
		TemaKalender.daftar(AirKota.TEMA, ACARA, DARI, SAMPAI, TikusAir::pilihHari, new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				TikusAir.mulai(level, situsId);
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				TikusAir.akhir(level, situsId);
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl && entity instanceof KultivatorNpc npc) {
				for (Kejadian k : JALAN.values()) {
					if (k.pencuri == npc && (k.fase == Fase.KABUR || k.fase == Fase.RAMPOK)) {
						tangkap(sl, k, sp);
						return InteractionResult.SUCCESS;
					}
				}
			}
			return InteractionResult.PASS;
		});
	}

	static void reset() {
		JALAN.clear();
	}

	/** Every 4-7 days per town: at least 4 days after the last raid, surely after 7, a third of the days between. */
	private static boolean pilihHari(ServerLevel level, long hari, long situsId, BlockPos pusat) {
		long last = AirKota.kota(level.getServer(), situsId).getLongOr("tikus_hari", -100L);
		long gap = hari - last;
		return gap >= 7L || gap >= 4L && AirKota.acak(situsId, hari, 380462L) < 0.34D;
	}

	private static boolean boleh() {
		return AirKota.aktif() && AirKota.config().tikusAir && TemaConfig.inti().bahaya;
	}

	/** True while the thieves are about in the town (the stall boats stay moored for the night). */
	public static boolean berjalan(long situs) {
		Kejadian k = JALAN.get(situs);
		return k != null && k.fase != Fase.SELESAI;
	}

	/** True when this stall boat is the thieves' prey right now (it must not go home). */
	static boolean sasaran(Entity boat) {
		for (Kejadian k : JALAN.values()) {
			if (k.mangsa == boat) {
				return true;
			}
		}
		return false;
	}

	private static void mulai(ServerLevel level, long situs) {
		if (!boleh()) {
			return;
		}
		CompoundTag town = AirKota.kota(level.getServer(), situs);
		town.putLong("tikus_hari", AirKota.hari(level));
		AirKota.ubah(level.getServer());
		JALAN.put(situs, new Kejadian(situs));
	}

	private static void akhir(ServerLevel level, long situs) {
		Kejadian k = JALAN.remove(situs);
		if (k != null) {
			bersihkan(k, !k.laoShu || k.fase != Fase.SELESAI);
		}
	}

	private static void bersihkan(Kejadian k, boolean pencuriJuga) {
		if (pencuriJuga && k.pencuri != null && !k.pencuri.isRemoved()) {
			k.pencuri.vanish();
		}
		if (k.sampan != null) {
			if (k.pencuri != null && k.pencuri.getVehicle() == k.sampan) {
				k.pencuri.stopRiding();
			}
			TemaMekanisme.lepas(k.sampan);
			k.sampan = null;
		}
	}

	/**
	 * Every 20 ticks: a night (14:00-22:00 world time window of the event) with a player in a town who stands at chain
	 * stage {@code kejar} or holds the guild mission {@code pencuri_kanal} brings the thieves out that very night (the
	 * forced run of {@code TemaKalender.paksa}), at most once a world day per town.
	 */
	private static void panggil(ServerLevel level) {
		long t = AirKota.jam(level);
		if (t < DARI || t >= SAMPAI - 2000L || !boleh()) {
			return;
		}
		long hari = AirKota.hari(level);
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || !perluPencuri(p)) {
				continue;
			}
			AirKota.Hidup kota = AirKota.di(p);
			if (kota == null || JALAN.containsKey(kota.situs) || TemaKalender.berjalan(kota.situs, AirKota.TEMA, ACARA)) {
				continue;
			}
			CompoundTag town = AirKota.kota(level.getServer(), kota.situs);
			if (town.getLongOr("tikus_paksa", -1L) == hari) {
				continue;
			}
			town.putLong("tikus_paksa", hari);
			AirKota.ubah(level.getServer());
			TemaKalender.paksa(level, kota.situs, AirKota.TEMA, ACARA, true);
		}
	}

	private static boolean perluPencuri(ServerPlayer p) {
		return QuestTracker.aktif(p, QUEST) && "kejar".equals(QuestTracker.simpul(p, QUEST)) || EkonomiApi.misiAktif(p, MISI);
	}

	/** Every 20 ticks (from {@link Air}); the skiff moves every tick ({@link #gerak}). */
	static void tick(ServerLevel level) {
		panggil(level);
		for (Kejadian k : List.copyOf(JALAN.values())) {
			AirKota.Hidup kota = AirKota.cari(k.situs);
			if (kota == null || k.fase == Fase.SELESAI) {
				continue;
			}
			long now = level.getGameTime();
			switch (k.fase) {
				case TUNGGU -> tunggu(level, kota, k, now);
				case RAMPOK -> {
					if (now - k.sejak >= LAMA_RAMPOK) {
						kabur(level, kota, k, now);
					}
				}
				case KABUR -> {
					if (now - k.sejak >= LAMA_KABUR || k.s >= JalurKanal.LINTAS_POS.panjang() - 1.0D) {
						lolos(level, kota, k);
					}
				}
				default -> {
				}
			}
		}
	}

	private static void tunggu(ServerLevel level, AirKota.Hidup kota, Kejadian k, long now) {
		Entity prey = null;
		ServerPlayer witness = null;
		List<PerahuKanalEntity> stalls = Perahu.lapakHidup(kota.situs);
		DuniaAnchorEntity silk = kota.satu(AirAnchors.SUTRA);
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || Sibuk.why(p) != null || kota.jarak(p.position()) > AirKota.JANGKAU_KOTA) {
				continue;
			}
			for (PerahuKanalEntity boat : stalls) {
				if (boat.distanceToSqr(p) <= JARAK_SAKSI * JARAK_SAKSI) {
					prey = boat;
					witness = p;
					break;
				}
			}
			if (prey == null && silk != null && silk.distanceToSqr(p) <= JARAK_SAKSI * JARAK_SAKSI) {
				prey = silk;
				witness = p;
			}
			if (prey != null) {
				break;
			}
		}
		if (prey == null) {
			return;
		}
		k.laoShu = QuestTracker.aktif(witness, QUEST) && "kejar".equals(QuestTracker.simpul(witness, QUEST));
		BlockPos bank = BlockPos.containing(prey.getX(), kota.lantai(), prey.getZ());
		KultivatorNpc thief = k.laoShu ? AirTokoh.panggil(level, kota.situs, AirTokoh.LAO_SHU, bank, 0.0F)
				: HidupApi.tubuh(level, kota.situs, NpcRole.WARGA, bank, 0.0F);
		if (thief == null) {
			return;
		}
		thief.memory().putBoolean("hold", true);
		thief.memory().putString("air_tikus", "pencuri");
		if (!k.laoShu) {
			thief.setPlate(Component.translatable("kultivator.kota_tema.air.tikus.nama"));
		}
		k.pencuri = thief;
		k.mangsa = prey;
		k.saksi = witness;
		k.fase = Fase.RAMPOK;
		k.sejak = now;
		// a resident sees it and shouts
		KultivatorNpc teller = null;
		double best = 24.0D * 24.0D;
		for (Entity e : level.getEntities(thief, thief.getBoundingBox().inflate(24.0D), e -> e instanceof KultivatorNpc)) {
			double d = e.distanceToSqr(thief);
			if (d < best && e != thief) {
				best = d;
				teller = (KultivatorNpc) e;
			}
		}
		if (teller != null) {
			HidupApi.tatap(teller, thief);
			HidupApi.klip(teller, GerakId.id("tunjuk"), 0, 60);
			teller.say(Component.translatable("kultivator.kota_tema.air.tikus.teriak"), 80);
		}
		level.playSound(null, bank, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 1.4F);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(thief) <= 64.0D * 64.0D) {
				Msg.chat(p, Component.translatable(k.laoShu ? "kultivator.kota_tema.air.tikus.lao_shu" : "kultivator.kota_tema.air.tikus.rampok",
						Component.translatable(prey instanceof PerahuKanalEntity ? "kultivator.kota_tema.air.tikus.mangsa.lapak"
								: "kultivator.kota_tema.air.tikus.mangsa.sutra")).withStyle(ChatFormatting.RED));
			}
		}
		CeritaApi.kabar(level, kota.situs, "tema_air_tikus");
	}

	private static void kabur(ServerLevel level, AirKota.Hidup kota, Kejadian k, long now) {
		KultivatorNpc thief = k.pencuri;
		if (thief == null || thief.isRemoved()) {
			k.fase = Fase.SELESAI;
			return;
		}
		double[] local = kota.lokal(thief.position());
		k.s = JalurKanal.LINTAS_POS.terdekat(local[0], local[1]);
		Vec3 at = JalurKanal.dunia(kota, JalurKanal.LINTAS_POS, k.s, 0.0D);
		PerahuKanalEntity skiff = TemaMekanisme.muncul(level, TemaEntities.PERAHU_KANAL, at, JalurKanal.yaw(kota, JalurKanal.LINTAS_POS, k.s),
				kota.kota, null);
		if (skiff == null) {
			// no room for a body: he runs off into the alleys instead
			lolos(level, kota, k);
			return;
		}
		skiff.setVarian(PerahuKanalEntity.VARIAN_HITAM);
		skiff.setKeadaan(PerahuKanalEntity.KEADAAN_KEJAR);
		skiff.setNilai(1.0F);
		thief.teleportTo(at.x, at.y + 0.5D, at.z);
		thief.startRiding(skiff);
		k.sampan = skiff;
		k.fase = Fase.KABUR;
		k.sejak = now;
		level.playSound(null, skiff.blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL, 1.0F, 1.2F);
	}

	/** Every server tick: the fleeing skiff. */
	static void gerak(ServerLevel level) {
		for (Kejadian k : JALAN.values()) {
			if (k.fase != Fase.KABUR || k.sampan == null || k.sampan.isRemoved()) {
				continue;
			}
			AirKota.Hidup kota = AirKota.cari(k.situs);
			if (kota == null) {
				continue;
			}
			k.s = Math.min(JalurKanal.LINTAS_POS.panjang(), k.s + LAJU);
			k.sampan.pindah(JalurKanal.dunia(kota, JalurKanal.LINTAS_POS, k.s, 0.0D), JalurKanal.yaw(kota, JalurKanal.LINTAS_POS, k.s));
			if (level.getGameTime() % 8L == 0L) {
				level.sendParticles(ParticleTypes.SPLASH, k.sampan.getX(), k.sampan.getY() + 0.3D, k.sampan.getZ(), 4, 0.6D, 0.1D, 0.6D, 0.05D);
			}
		}
	}

	/** A hit on the black skiff. */
	static void dipukul(ServerLevel level, PerahuKanalEntity skiff, ServerPlayer player) {
		for (Kejadian k : JALAN.values()) {
			if (k.sampan == skiff && k.fase == Fase.KABUR) {
				tangkap(level, k, player);
				return;
			}
		}
	}

	private static void tangkap(ServerLevel level, Kejadian k, ServerPlayer player) {
		AirKota.Hidup kota = AirKota.cari(k.situs);
		if (kota == null) {
			return;
		}
		k.fase = Fase.SELESAI;
		KultivatorNpc thief = k.pencuri;
		Vec3 at = k.sampan != null ? k.sampan.position() : thief != null ? thief.position() : player.position();
		bersihkan(k, false);
		if (thief != null && !thief.isRemoved()) {
			// to the nearest bank: next to the player
			thief.teleportTo(player.getX(), player.getY(), player.getZ());
			HidupApi.klip(thief, GerakId.id("sujud_guru"), 0, 80);
			thief.say(Component.translatable(k.laoShu ? "kultivator.kota_tema.air.tikus.lao_shu_menyerah" : "kultivator.kota_tema.air.tikus.menyerah"), 100);
		}
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.3D, at.z, 20, 1.0D, 0.2D, 1.0D, 0.1D);
		AirKota.tambahNamaBaik(player, kota, 3);
		long reward = HADIAH_DASAR * (Barang.pita(player) + 1L);
		if (k.laoShu) {
			reward = 30L * (Barang.pita(player) + 1L);
		}
		NilaiBatu.give(player, reward);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.air.tikus.tangkap", NilaiBatu.text(reward)).withStyle(ChatFormatting.GOLD));
		Advancements.grant(player, "tempat/tema_air_tikus");
		if (EkonomiApi.misiAktif(player, MISI)) {
			EkonomiApi.misiSelesai(player, MISI);
		}
		AirCerita.peristiwa(player, "air_tikus", k.laoShu ? "lao_shu" : "pencuri");
		CeritaApi.kabar(level, kota.situs, "tema_air_tikus_tertangkap", player.getName().getString());
		if (thief != null && !thief.isRemoved()) {
			if (k.laoShu) {
				// Lao Shu waits for the chain's dialog for a minute, then slinks off
				Air.nanti(level.getServer(), 1200, () -> {
					if (!thief.isRemoved()) {
						thief.vanish();
					}
				});
			} else {
				Air.nanti(level.getServer(), 100, () -> {
					if (!thief.isRemoved()) {
						thief.vanish();
					}
				});
			}
		}
	}

	private static void lolos(ServerLevel level, AirKota.Hidup kota, Kejadian k) {
		k.fase = Fase.SELESAI;
		Vec3 at = k.sampan != null ? k.sampan.position() : null;
		bersihkan(k, true);
		if (at != null) {
			for (ServerPlayer p : level.players()) {
				if (p.position().distanceToSqr(at) < 96.0D * 96.0D) {
					Msg.chat(p, Component.translatable("kultivator.kota_tema.air.tikus.lolos").withStyle(ChatFormatting.GRAY));
				}
			}
		}
	}

	private TikusAir() {
	}
}
