package dev.kultivator.dunia.tempat.tema.air;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.TemaSibuk;
import dev.kultivator.dunia.tempat.tema.bersama.TemaIrama;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lomba Perahu Naga (DESIGN 38.T.5.4): calendar event {@code air/perahu_naga} every 14th day ({@code d % 14 == 6}),
 * 8000-10000. Four {@link PerahuNagaEntity} (red, green, blue, black) line up on the Kanal Utama, race up into the lantern
 * lake, two laps around the Panggung Air (under the causeway hump) and back to the finish by the Pintu Air
 * ({@link JalurKanal#LINTAS_LOMBA}). For {@link #SIAP} ticks before the gun a player may sign on as the drummer of one
 * boat (right-click it): he sits at the drum and plays the {@code tema_irama} drum pattern (60 s); his accuracy adds
 * 0-{@link #BONUS_MAKS} to his boat's speed for the whole race (the lead it would have gained is added when the score
 * comes in). The winner's drummer: {@link #HADIAH} NB (the crew's share per member), Nama Baik +2, the Pita Naga ribbon,
 * quest event {@code air_naga_menang}; every other drummer at the finish gets {@link #HADIAH_IKUT} NB. The two idle
 * dragon boats of the boat house are only scenery between races.
 */
public final class LombaPerahuNaga {

	public static final String ACARA = "perahu_naga";
	public static final int DARI = 8000;
	public static final int SAMPAI = 10000;
	public static final int SIAP = 300;
	public static final double LAJU = 0.30D;
	public static final double BONUS_MAKS = 0.12D;
	public static final long HADIAH = 20L;
	public static final long HADIAH_IKUT = 3L;
	public static final double[] LAJUR = {-2.2D, -0.75D, 0.75D, 2.2D};

	private static final class Perahu {
		final PerahuNagaEntity body;
		final double lajur;
		final double dasar;
		final double fase;
		double s;
		double bonus;
		@Nullable UUID penabuh;
		boolean finis;

		Perahu(PerahuNagaEntity body, double lajur, double dasar, double fase) {
			this.body = body;
			this.lajur = lajur;
			this.dasar = dasar;
			this.fase = fase;
		}
	}

	private static final class Lomba {
		final long situs;
		final long mulai;
		final List<Perahu> perahu = new ArrayList<>();
		final List<Perahu> urutan = new ArrayList<>();
		boolean jalan;

		Lomba(long situs, long mulai) {
			this.situs = situs;
			this.mulai = mulai;
		}
	}

	private static final Map<Long, Lomba> LOMBA = new HashMap<>();
	/** Idle boats of the boat house per anchor. */
	private static final Map<UUID, List<PerahuNagaEntity>> DIAM = new HashMap<>();

	static void init() {
		TemaKalender.daftar(AirKota.TEMA, ACARA, DARI, SAMPAI, TemaKalender.setiap(14, 6), new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				LombaPerahuNaga.mulai(level, situsId);
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				LombaPerahuNaga.akhir(level, situsId);
			}
		});
	}

	static void reset() {
		LOMBA.clear();
		DIAM.clear();
	}

	private static boolean boleh() {
		return AirKota.aktif() && AirKota.config().perahuNaga && TemaConfig.inti().acara;
	}

	// ================================================================== idle boats

	static void rumahDekat(ServerLevel level, AirKota.Hidup kota, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		List<PerahuNagaEntity> idle = DIAM.computeIfAbsent(anchor.getUUID(), k -> new ArrayList<>());
		idle.removeIf(Entity::isRemoved);
		if (LOMBA.containsKey(kota.situs) || within.isEmpty() || within.getFirst().distanceToSqr(anchor) > 48.0D * 48.0D) {
			for (PerahuNagaEntity e : idle) {
				TemaMekanisme.lepas(e);
			}
			idle.clear();
			return;
		}
		if (!idle.isEmpty()) {
			return;
		}
		int n = 0;
		for (Titik t : AirAnchors.titikDekat(level, kota, "tambatan", anchor.blockPosition(), 24.0D)) {
			if (!"naga".equals(t.data().getOrDefault("kelas", "")) || n >= 2) {
				continue;
			}
			Vec3 at = new Vec3(t.pos().getX() + 0.5D, kota.permukaan(), t.pos().getZ() + 0.5D);
			PerahuNagaEntity boat = TemaMekanisme.muncul(level, TemaEntities.PERAHU_NAGA, at, t.hadap().toYRot(), kota.kota, t.pos());
			if (boat != null) {
				boat.setVarian(n == 0 ? PerahuNagaEntity.VARIAN_MERAH : PerahuNagaEntity.VARIAN_HIJAU);
				boat.setKeadaan(PerahuNagaEntity.KEADAAN_DIAM);
				idle.add(boat);
				n++;
			}
		}
	}

	// ================================================================== the race

	private static void mulai(ServerLevel level, long situs) {
		if (!boleh()) {
			return;
		}
		AirKota.Hidup kota = AirKota.cari(situs);
		if (kota == null) {
			return;
		}
		Lomba race = new Lomba(situs, level.getGameTime());
		LOMBA.put(situs, race);
		CeritaApi.kabar(level, situs, "tema_air_perahu_naga");
		for (ServerPlayer p : level.players()) {
			if (kota.jarak(p.position()) < AirKota.JANGKAU_KOTA) {
				Msg.chat(p, Component.translatable("kultivator.kota_tema.air.naga.umum", SIAP / 20).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static void akhir(ServerLevel level, long situs) {
		Lomba race = LOMBA.remove(situs);
		if (race == null) {
			return;
		}
		for (Perahu boat : race.perahu) {
			ServerPlayer p = boat.penabuh == null ? null : level.getServer().getPlayerList().getPlayer(boat.penabuh);
			if (p != null) {
				if (p.getVehicle() == boat.body) {
					p.stopRiding();
				}
				TemaSibuk.lepas(p, TemaSibuk.PERAHU);
			}
			TemaMekanisme.lepas(boat.body);
		}
	}

	/** Every server tick (from {@link Air}). */
	static void tick(ServerLevel level) {
		if (LOMBA.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Lomba race : List.copyOf(LOMBA.values())) {
			AirKota.Hidup kota = AirKota.cari(race.situs);
			if (kota == null) {
				continue;
			}
			long age = now - race.mulai;
			if (race.perahu.size() < LAJUR.length && now % 20L == 0L) {
				siapkan(level, kota, race);
			}
			if (!race.jalan) {
				for (Perahu boat : race.perahu) {
					pindah(kota, boat, 0.0D);
					boat.body.setKeadaan(PerahuNagaEntity.KEADAAN_DIAM);
				}
				if (age >= SIAP && !race.perahu.isEmpty()) {
					start(level, kota, race);
				}
				continue;
			}
			double len = JalurKanal.LINTAS_LOMBA.panjang();
			for (Perahu boat : race.perahu) {
				if (boat.finis || boat.body.isRemoved()) {
					continue;
				}
				double wave = 1.0D + 0.05D * Math.sin((now - race.mulai) / 70.0D + boat.fase);
				boat.s += boat.dasar * wave * (1.0D + boat.bonus);
				boat.body.setNilai((float) Math.min(1.0D, 0.6D + boat.bonus * 3.0D));
				if (boat.s >= len) {
					boat.s = len;
					boat.finis = true;
					race.urutan.add(boat);
					finis(level, kota, race, boat);
				}
				pindah(kota, boat, boat.s);
				if (boat.penabuh != null) {
					ServerPlayer p = level.getServer().getPlayerList().getPlayer(boat.penabuh);
					if (p != null) {
						TemaSibuk.tahan(p, TemaSibuk.PERAHU, 60);
					}
				}
			}
			if (now % 10L == 0L) {
				Perahu lead = race.perahu.stream().max((a, b) -> Double.compare(a.s, b.s)).orElse(null);
				if (lead != null) {
					level.playSound(null, lead.body.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.RECORDS, 1.2F, 0.7F);
				}
			}
		}
	}

	private static void siapkan(ServerLevel level, AirKota.Hidup kota, Lomba race) {
		int i = race.perahu.size();
		Vec3 at = JalurKanal.dunia(kota, JalurKanal.LINTAS_LOMBA, 0.0D, LAJUR[i]);
		PerahuNagaEntity boat = TemaMekanisme.muncul(level, TemaEntities.PERAHU_NAGA, at, JalurKanal.yaw(kota, JalurKanal.LINTAS_LOMBA, 0.0D),
				kota.kota, null);
		if (boat == null) {
			return;
		}
		boat.setVarian(i);
		boat.setKeadaan(PerahuNagaEntity.KEADAAN_DIAM);
		double base = LAJU * (0.96D + 0.08D * AirKota.acak(race.situs, race.mulai + i, 380461L));
		race.perahu.add(new Perahu(boat, LAJUR[i], base, i * 1.7D));
	}

	private static void start(ServerLevel level, AirKota.Hidup kota, Lomba race) {
		race.jalan = true;
		for (Perahu boat : race.perahu) {
			boat.body.setKeadaan(PerahuNagaEntity.KEADAAN_LOMBA);
			if (boat.penabuh == null) {
				continue;
			}
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(boat.penabuh);
			if (p == null || p.getVehicle() != boat.body) {
				boat.penabuh = null;
				continue;
			}
			Perahu mine = boat;
			long started = level.getGameTime();
			TemaIrama.mulai(p, -1, boat.body.blockPosition(), TemaLayar.Irama.POLA_GENDERANG, 1, 1, (pl, skor, lulus, babak) -> {
				mine.bonus = BONUS_MAKS * Math.max(0, Math.min(100, skor)) / 100.0D;
				long elapsed = pl.level().getGameTime() - started;
				if (!mine.finis) {
					mine.s += mine.dasar * mine.bonus * elapsed;
				}
				Msg.chat(pl, Component.translatable("kultivator.kota_tema.air.naga.irama", skor, Math.round(mine.bonus * 100.0D))
						.withStyle(ChatFormatting.GOLD));
			});
		}
		Vec3 start = JalurKanal.dunia(kota, JalurKanal.LINTAS_LOMBA, 0.0D, 0.0D);
		level.playSound(null, net.minecraft.core.BlockPos.containing(start), SoundEvents.BELL_BLOCK, SoundSource.RECORDS, 2.0F, 1.0F);
		level.sendParticles(ParticleTypes.FIREWORK, start.x, start.y + 3.0D, start.z, 30, 1.5D, 1.0D, 1.5D, 0.05D);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(start) < 160.0D * 160.0D) {
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.air.naga.mulai").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static void pindah(AirKota.Hidup kota, Perahu boat, double s) {
		boat.body.pindah(JalurKanal.dunia(kota, JalurKanal.LINTAS_LOMBA, s, boat.lajur), JalurKanal.yaw(kota, JalurKanal.LINTAS_LOMBA, s));
	}

	private static void finis(ServerLevel level, AirKota.Hidup kota, Lomba race, Perahu boat) {
		int place = race.urutan.size();
		boat.body.setKeadaan(PerahuNagaEntity.KEADAAN_DIAM);
		Component name = Component.translatable("kultivator.kota_tema.air.naga.perahu." + boat.body.varian());
		for (ServerPlayer p : level.players()) {
			if (kota.jarak(p.position()) < AirKota.JANGKAU_KOTA) {
				Msg.chat(p, Component.translatable("kultivator.kota_tema.air.naga.finis", name, place).withStyle(place == 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
			}
		}
		if (place == 1) {
			level.sendParticles(ParticleTypes.FIREWORK, boat.body.getX(), boat.body.getY() + 3.0D, boat.body.getZ(), 40, 2.0D, 1.0D, 2.0D, 0.06D);
			level.playSound(null, boat.body.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.RECORDS, 1.5F, 1.0F);
		}
		if (boat.penabuh == null) {
			return;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(boat.penabuh);
		if (p == null) {
			return;
		}
		if (place == 1) {
			NilaiBatu.give(p, HADIAH);
			AirKota.tambahNamaBaik(p, kota, 2);
			Barang.beri(p, new ItemStack(TemaItems.PITA_NAGA));
			Msg.chat(p, Component.translatable("kultivator.kota_tema.air.naga.menang", NilaiBatu.text(HADIAH)).withStyle(ChatFormatting.GOLD));
			Advancements.grant(p, "tempat/tema_air_perahu_naga");
			AirCerita.peristiwa(p, "air_naga_menang", "juara");
			CeritaApi.kabar(level, kota.situs, "tema_air_juara_naga", p.getName().getString());
		} else {
			NilaiBatu.give(p, HADIAH_IKUT);
			Msg.chat(p, Component.translatable("kultivator.kota_tema.air.naga.ikut", place, NilaiBatu.text(HADIAH_IKUT)).withStyle(ChatFormatting.AQUA));
			AirCerita.peristiwa(p, "air_naga_ikut", Integer.toString(place));
		}
	}

	/** A right-click on a dragon boat: sign on as drummer before the gun. */
	static boolean klik(PerahuNagaEntity body, ServerPlayer player) {
		for (Lomba race : LOMBA.values()) {
			for (Perahu boat : race.perahu) {
				if (boat.body != body) {
					continue;
				}
				if (race.jalan) {
					Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.naga.sudah_mulai").withStyle(ChatFormatting.YELLOW));
					return true;
				}
				for (Perahu other : race.perahu) {
					if (player.getUUID().equals(other.penabuh)) {
						Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.naga.sudah_daftar").withStyle(ChatFormatting.YELLOW));
						return true;
					}
				}
				if (boat.penabuh != null) {
					Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.naga.ada_penabuh").withStyle(ChatFormatting.YELLOW));
					return true;
				}
				String why = dev.kultivator.util.Sibuk.why(player);
				if (why != null) {
					Msg.actionBar(player, dev.kultivator.util.Sibuk.text(why).withStyle(ChatFormatting.RED));
					return true;
				}
				if (player.startRiding(body)) {
					boat.penabuh = player.getUUID();
					TemaSibuk.tahan(player, TemaSibuk.PERAHU, SAMPAI - DARI);
					Msg.chat(player, Component.translatable("kultivator.kota_tema.air.naga.daftar",
							Component.translatable("kultivator.kota_tema.air.naga.perahu." + body.varian())).withStyle(ChatFormatting.GOLD));
				}
				return true;
			}
		}
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.air.naga.bukan_hari").withStyle(ChatFormatting.GRAY));
		return true;
	}

	/** True while the race of the town runs (or lines up). */
	public static boolean berjalan(long situs) {
		return LOMBA.containsKey(situs);
	}

	private LombaPerahuNaga() {
	}
}
