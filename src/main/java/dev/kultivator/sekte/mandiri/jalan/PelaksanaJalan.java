package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteSuara;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.mandiri.MandiriKegiatan;
import dev.kultivator.sekte.mandiri.Perencana;
import dev.kultivator.sekte.mandiri.TempatCache;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.JenisCita;
import dev.kultivator.sekte.mandiri.api.JenisJalan;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.Kehatian;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.Pelaksana;
import dev.kultivator.sekte.mandiri.api.TujuanProvider;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The executors of 39C (contract C39.C1): {@code PERJALANAN} (choosing a journey, the farewell, packing at the Lumbung
 * and leaving through the gate or on the flying sword), {@code TIBA} (carrying the goods home, unpacking, the gifts and
 * the report) and the two pose activities {@code BERKEMAS}/{@code BERANGKAT} that only ever run inside them.
 * <p>
 * The departure (DESIGN 39.6.2 stages 1-2): a bow to a master within 16 blocks ("Guru, murid pamit ke kota sebentar"),
 * ten seconds kneeling at the Lumbung over the bundle ({@code ACT_BERKEMAS} 46), then either the walk to the gate and 24
 * blocks beyond toward the destination, fading in dust, or for a Golden Core party the spiral rise of 20 blocks on the
 * flying sword ({@code ACT_TERBANG_JAUH} 52, six seconds, server-side positions each tick, at most two at once per hall)
 * and the dash; then the party goes into {@code AwayStore}. Companions invited by the leader follow its steps.
 */
final class PelaksanaJalan {

	/** One departure or arrival scene in progress. */
	static final class Adegan {
		final UUID soul;
		final boolean pemimpin;
		final @Nullable UUID pimpinan;
		@Nullable Pilihan pilihan;
		final List<UUID> anggota = new ArrayList<>();
		int fase;
		long t0;
		@Nullable BlockPos sasaran;
		boolean terbang;
		Vec3 arah = Vec3.ZERO;
		Vec3 pusat = Vec3.ZERO;
		/** Where the sword flight began (the body goes back there when the departure is refused). */
		@Nullable Vec3 asal;
		boolean berangkat;
		int hadiahKe;

		Adegan(UUID soul, boolean pemimpin, @Nullable UUID pimpinan) {
			this.soul = soul;
			this.pemimpin = pemimpin;
			this.pimpinan = pimpinan;
		}
	}

	/** What the leader chose: the kind and the place. */
	record Pilihan(JenisJalan jenis, TujuanProvider.Tujuan tujuan, long jam) {
	}

	static final int F_PAMIT = 0;
	static final int F_KE_LUMBUNG = 1;
	static final int F_BERKEMAS = 2;
	static final int F_KE_GERBANG = 3;
	static final int F_KELUAR = 4;
	static final int F_TERBANG = 5;
	static final int F_MELESAT = 6;

	static final int T_BAWA = 10;
	static final int T_BONGKAR = 11;
	static final int T_HADIAH = 12;
	static final int T_LAPOR = 13;

	private static final Map<UUID, Adegan> ADEGAN = new HashMap<>();
	private static final Map<UUID, Pilihan> PILIHAN = new HashMap<>();

	static void kosongkan() {
		ADEGAN.clear();
		PILIHAN.clear();
	}

	/** The walking target of a disciple in a 39C scene (read by {@link JelmaanJalanGoal}), or null. */
	static @Nullable BlockPos sasaran(DiscipleEntity e) {
		Adegan a = ADEGAN.get(e.soulId());
		return a == null || a.terbang ? null : a.sasaran;
	}

	static boolean terbang(UUID soul) {
		Adegan a = ADEGAN.get(soul);
		return a != null && a.terbang;
	}

	// ================================================================== registration

	static void daftar() {
		MandiriApi.register(Kegiatan.PERJALANAN, new Perjalanannya());
		MandiriApi.register(Kegiatan.TIBA, new Tibanya());
		MandiriApi.register(Kegiatan.BERKEMAS, new Pose(MandiriKegiatan.ACT_BERKEMAS));
		MandiriApi.register(Kegiatan.BERANGKAT, new Pose(MandiriKegiatan.ACT_TERBANG_JAUH));
	}

	/** BERKEMAS / BERANGKAT as stand-alone activities: never chosen (the planner scores them 0), a pose when forced. */
	private record Pose(int act) implements Pelaksana {
		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			return false;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			murid.setActivity(act, konteks.tempat());
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			return true;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			if (murid.activity() == act) {
				murid.setActivity(0, null);
			}
		}
	}

	// ================================================================== choosing a journey

	/** The journey the disciple would start this hour, or null (cached per world hour, cheap for the planner). */
	static @Nullable Pilihan pilih(DiscipleEntity murid) {
		long jam = Math.floorDiv(murid.level().getGameTime(), Tahap.JAM);
		Pilihan cached = PILIHAN.get(murid.soulId());
		if (cached != null && cached.jam() == jam) {
			return cached.tujuan() == null ? null : cached;
		}
		Pilihan p = null;
		try {
			p = hitung(murid, jam);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Mandiri journey choice failed for {}", murid.getName().getString(), exception);
		}
		PILIHAN.put(murid.soulId(), p == null ? new Pilihan(JenisJalan.BELANJA, null, jam) : p);
		return p;
	}

	private static @Nullable Pilihan hitung(DiscipleEntity murid, long jam) {
		if (!(murid.level() instanceof ServerLevel level) || murid.ownerId() == null) {
			return null;
		}
		UUID owner = murid.ownerId();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.active() || !hall.dim().equals(level.dimension())) {
			return null;
		}
		DiscipleData data = murid.data();
		long now = level.getGameTime();
		if (MandiriApi.luka(data, now) != JenisLuka.TIDAK || data.deviating() || JalanData.get(level.getServer()).adaTiba(murid.soulId())) {
			return null;
		}
		Kebijakan kebijakan = MandiriApi.kebijakan(murid);
		JalanHidup jalan = MandiriApi.jalan(data);
		int sifat = Watak.sifat(data);
		double power = Watak.kekuatan(data);
		Kehatian hati = Watak.kehatian(kebijakan.risiko(), sifat);
		int radius = Tujuan.radius(data);
		List<double[]> weights = new ArrayList<>();
		double belanja = 0.0D;
		long cheapest = Long.MAX_VALUE;
		for (DaftarBelanja.Baris b : DaftarBelanja.baca(data)) {
			belanja += 4 - b.prio();
			cheapest = Math.min(cheapest, b.maksHarga());
		}
		boolean bisaBelanja = belanja > 0.0D && MandiriApi.dompet(data) >= Math.min(cheapest, 4L);
		add(weights, JenisJalan.BELANJA, bisaBelanja ? 3.0D + belanja : 0.0D);
		add(weights, JenisJalan.JUAL, jalan == JalanHidup.PEDAGANG && !MandiriApi.dagangan(owner, murid.soulId()).isEmpty() ? 2.0D : 0.0D);
		add(weights, JenisJalan.MISI_GILDA, (jalan == JalanHidup.PEDAGANG ? 2.0D : jalan == JalanHidup.PETUALANG ? 1.0D : 0.4D)
				+ (cita(data, JenisCita.KEKAYAAN) ? 2.0D : 0.0D));
		add(weights, JenisJalan.BERBURU, (jalan == JalanHidup.PEMBURU ? 3.0D : jalan == JalanHidup.PEDANG ? 1.0D : 0.3D)
				+ (cita(data, JenisCita.BINATANG) ? 2.0D : 0.0D));
		add(weights, JenisJalan.RERUNTUHAN, (jalan == JalanHidup.PETUALANG ? 3.0D : 0.3D)
				+ (cita(data, JenisCita.RAHASIA) || cita(data, JenisCita.PUSAKA) ? 2.0D : 0.0D));
		add(weights, JenisJalan.ALAM_RAHASIA, data.realm().ordinal() >= Realm.NASCENT_SOUL.ordinal()
				? (jalan == JalanHidup.PETUALANG ? 2.0D : 0.3D) + (cita(data, JenisCita.RAHASIA) ? 2.0D : 0.0D) : 0.0D);
		add(weights, JenisJalan.GUA_PERTAPA, data.realm().ordinal() >= Realm.FOUNDATION.ordinal() ? (jalan == JalanHidup.PERTAPA ? 3.0D : 0.3D) : 0.0D);
		add(weights, JenisJalan.DESA, (jalan == JalanHidup.PENJAGA ? 2.0D : 0.3D) + (cita(data, JenisCita.MENOLONG) ? 2.0D : 0.0D)
				+ (SekteApi.hati().has(data, Sifat.PENYAYANG) ? 1.0D : 0.0D));
		add(weights, JenisJalan.SEKTE_TAMU, jalan == JalanHidup.PEDANG ? 1.0D : 0.4D);
		int iblis = MandiriApi.iblis(data);
		add(weights, JenisJalan.ZIARAH, iblis > 30 ? 2.0D + iblis / 30.0D : 0.3D);
		add(weights, JenisJalan.BERKELANA, jalan == JalanHidup.CENDEKIA ? 1.5D : jalan == JalanHidup.PETUALANG ? 1.0D : 0.5D);
		weights.sort((a, b) -> Double.compare(b[1], a[1]));
		List<Pilihan> found = new ArrayList<>(2);
		for (double[] w : weights) {
			if (w[1] <= 0.0D || found.size() >= 2) {
				continue;
			}
			JenisJalan jenis = JenisJalan.values()[(int) w[0]];
			if (!kebijakan.boleh(jenis.izin()) || kebijakan.dilarang(jenis.id())) {
				continue;
			}
			TujuanProvider.Tujuan t = tempat(level, owner, hall.pos(), murid, jenis, radius, kebijakan, power, hati);
			if (t != null) {
				found.add(new Pilihan(jenis, t, jam));
			}
		}
		if (found.isEmpty()) {
			return null;
		}
		if (found.size() == 1) {
			return found.get(0);
		}
		// the strongest wish usually wins; one time in four the second one does (habits are not machines)
		return Math.floorMod(murid.soulId().hashCode() + jam, 4L) == 0L ? found.get(1) : found.get(0);
	}

	/**
	 * Test ({@code uji perguruan mandiri kirim}): forces this hour's choice of {@code murid} to {@code jenis}, at the
	 * destination with id {@code id} or the nearest one at least 120 blocks away (a point in the wilds for hunting and
	 * wandering when the world knows no place); null when there is nowhere to go.
	 */
	static @Nullable Pilihan paksa(DiscipleEntity murid, JenisJalan jenis, @Nullable String id) {
		if (!(murid.level() instanceof ServerLevel level) || murid.ownerId() == null) {
			return null;
		}
		UUID owner = murid.ownerId();
		Halls.HallView hall = Halls.hallOf(owner);
		BlockPos aula = hall != null && hall.dim().equals(level.dimension()) ? hall.pos() : murid.blockPosition();
		Kebijakan kebijakan = MandiriApi.kebijakan(murid);
		long jam = Math.floorDiv(level.getGameTime(), Tahap.JAM);
		TujuanProvider.Tujuan found = null;
		List<TujuanProvider.Tujuan> list = Tujuan.daftar(level, owner, aula, jenis, 4000, kebijakan);
		boolean nearest = id == null || id.isBlank() || "terdekat".equals(id);
		for (TujuanProvider.Tujuan t : list) {
			if (nearest ? Tujuan.jarak(aula, t.pos()) >= 120.0D : t.id().equals(id)) {
				found = t;
				break;
			}
		}
		if (found == null && nearest && !list.isEmpty()) {
			found = list.get(0);
		}
		if (found == null && nearest) {
			found = tempat(level, owner, aula, murid, jenis, 4000, kebijakan, 99.0D, Kehatian.NEKAT);
		}
		if (found == null) {
			return null;
		}
		Pilihan p = new Pilihan(jenis, found, jam);
		PILIHAN.put(murid.soulId(), p);
		return p;
	}

	private static void add(List<double[]> out, JenisJalan jenis, double w) {
		out.add(new double[]{jenis.ordinal(), w});
	}

	private static boolean cita(DiscipleData data, JenisCita jenis) {
		return DaftarBelanja.punyaCita(data, jenis);
	}

	/** The nearest fitting place of {@code jenis} (tier within caution), or a point in the wilds for hunting and wandering. */
	private static @Nullable TujuanProvider.Tujuan tempat(ServerLevel level, UUID owner, BlockPos aula, DiscipleEntity murid, JenisJalan jenis,
			int radius, Kebijakan kebijakan, double power, Kehatian hati) {
		int maxTier = (int) Math.floor(power) + hati.tierRelatif() + 1;
		for (TujuanProvider.Tujuan t : Tujuan.daftar(level, owner, aula, jenis, radius, kebijakan)) {
			if (jenis.risikoDasar() < 0 && t.tier() > maxTier) {
				continue;
			}
			if (Tujuan.jarak(aula, t.pos()) < 120.0D) {
				continue;
			}
			return t;
		}
		if (jenis == JenisJalan.BERBURU || jenis == JenisJalan.BERKELANA) {
			long seed = murid.soulId().getMostSignificantBits() ^ Math.floorDiv(level.getGameTime(), 24000L);
			double angle = Math.floorMod(seed, 360L) * Math.PI / 180.0D;
			double d = Math.min(radius, 600.0D + Math.floorMod(seed >>> 9, 1400L));
			BlockPos p = new BlockPos((int) (aula.getX() + Math.cos(angle) * d), aula.getY(), (int) (aula.getZ() + Math.sin(angle) * d));
			int tier = Math.max(1, Math.min(maxTier, 1 + Tas.band(murid.data()) / 3));
			return new TujuanProvider.Tujuan("liar:" + Long.toHexString(seed & 0xFFFFFFL), jenis, p, tier, "situs#liar", 0L, null);
		}
		return null;
	}

	// ================================================================== PERJALANAN

	private static final class Perjalanannya implements Pelaksana {

		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			if (konteks.teman() != null) {
				Adegan lead = ADEGAN.get(konteks.teman());
				return lead != null && lead.pemimpin && lead.anggota.contains(murid.soulId()) && lead.fase <= F_BERKEMAS;
			}
			return pilih(murid) != null;
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			long now = konteks.level().getGameTime();
			if (konteks.teman() != null) {
				Adegan a = new Adegan(murid.soulId(), false, konteks.teman());
				a.fase = F_KE_LUMBUNG;
				a.t0 = now;
				Adegan lead = ADEGAN.get(konteks.teman());
				a.sasaran = lead != null && lead.sasaran != null ? lead.sasaran.offset(1 + a.anggota.size(), 0, 1) : murid.blockPosition();
				ADEGAN.put(murid.soulId(), a);
				return;
			}
			Pilihan p = pilih(murid);
			Adegan a = new Adegan(murid.soulId(), true, null);
			a.pilihan = p;
			a.t0 = now;
			ADEGAN.put(murid.soulId(), a);
			ServerPlayer master = konteks.level().getServer().getPlayerList().getPlayer(konteks.owner());
			if (master != null && master.level() == murid.level() && master.distanceToSqr(murid) <= 16.0D * 16.0D) {
				a.fase = F_PAMIT;
				murid.getLookControl().setLookAt(master, 30.0F, 30.0F);
				SosokApi.gerak(murid, GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 40);
				SekteSuara.line(murid, "pamit", "farewell", 0);
				Msg2.dekat(murid, JalanIds.t("ucap.pamit." + (p == null ? JenisJalan.BELANJA.id() : p.jenis().id()), master.getName()), 16.0D);
			} else {
				a.fase = F_KE_LUMBUNG;
			}
			a.sasaran = TempatCache.cari(murid, Kegiatan.BERKEMAS);
			if (p != null) {
				for (DiscipleEntity friend : Rombongan.ajak(murid, p.jenis())) {
					a.anggota.add(friend.soulId());
					if (!MandiriApi.mulaiKegiatan(friend, Kegiatan.PERJALANAN, -1, null, murid.soulId())) {
						a.anggota.remove(friend.soulId());
					}
				}
			}
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			Adegan a = ADEGAN.get(murid.soulId());
			if (a == null) {
				return false;
			}
			if (a.berangkat) {
				return false;
			}
			long now = konteks.level().getGameTime();
			long lama = now - a.t0;
			if (!a.pemimpin) {
				Adegan lead = a.pimpinan == null ? null : ADEGAN.get(a.pimpinan);
				if (lead == null) {
					return false;
				}
				ikut(murid, a, lead, now);
				return true;
			}
			switch (a.fase) {
				case F_PAMIT -> {
					if (lama >= 40L) {
						next(a, F_KE_LUMBUNG, now);
					}
				}
				case F_KE_LUMBUNG -> {
					if (a.sasaran == null || dekat(murid, a.sasaran, 2.5D) || lama > 300L) {
						next(a, F_BERKEMAS, now);
						murid.setActivity(MandiriKegiatan.ACT_BERKEMAS, a.sasaran);
						SosokApi.gerak(murid, GerakId.BERKEMAS, 0, 200, PropId.TIDAK_ADA, PropId.TIDAK_ADA, PropId.PETI);
					}
				}
				case F_BERKEMAS -> {
					if (lama >= 200L) {
						murid.setActivity(0, null);
						Pilihan p = a.pilihan;
						boolean pedang = p != null && semuaInti(murid, a) && !MandiriApi.kebijakan(murid).dilarang("pedang")
								&& Perjalanan.bolehTerbang(konteks.owner());
						if (pedang) {
							a.terbang = true;
							a.pusat = murid.position();
							a.asal = a.pusat;
							a.arah = arah(murid.blockPosition(), p.tujuan().pos());
							next(a, F_TERBANG, now);
							murid.setActivity(MandiriKegiatan.ACT_TERBANG_JAUH, null);
							SosokApi.gerak(murid, GerakId.BERDIRI_DI_PEDANG, 0, 200);
							murid.setNoGravity(true);
							Perjalanan.terbangMulai(murid);
						} else {
							BlockPos gate = TempatCache.cari(murid, Kegiatan.BERANGKAT);
							a.sasaran = gate != null ? gate : murid.blockPosition();
							next(a, F_KE_GERBANG, now);
							SosokApi.gerak(murid, GerakId.JALAN_BAWA, GerakId.V_JALAN_BAWA_PUNGGUNG, 0, PropId.TIDAK_ADA, PropId.TIDAK_ADA, PropId.RANGKA_PUNGGUNG);
						}
					}
				}
				case F_KE_GERBANG -> {
					if (a.sasaran == null || dekat(murid, a.sasaran, 3.0D) || lama > 600L) {
						Pilihan p = a.pilihan;
						Vec3 dir = p == null ? Vec3.ZERO : arah(murid.blockPosition(), p.tujuan().pos());
						a.sasaran = BlockPos.containing(murid.getX() + dir.x * 24.0D, murid.getY(), murid.getZ() + dir.z * 24.0D);
						next(a, F_KELUAR, now);
					}
				}
				case F_KELUAR -> {
					if (a.sasaran == null || dekat(murid, a.sasaran, 3.0D) || lama > 400L) {
						Fx.burst(konteks.level(), murid.position().add(0.0D, 0.4D, 0.0D), 0xB89A70, 20, 1.2F);
						berangkat(murid, a);
						return false;
					}
				}
				default -> {
					// the flight runs on the server tick (Perjalanan.terbangTick)
				}
			}
			return true;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			Adegan a = ADEGAN.remove(murid.soulId());
			if (a != null && a.berangkat) {
				return;
			}
			int act = murid.activity();
			if (act == MandiriKegiatan.ACT_BERKEMAS || act == MandiriKegiatan.ACT_TERBANG_JAUH) {
				murid.setActivity(0, null);
			}
			murid.setNoGravity(false);
			SosokApi.henti(murid);
			if (a != null && a.pemimpin) {
				for (UUID member : a.anggota) {
					DiscipleEntity m = Perencana.cari(konteks.owner(), member);
					if (m != null && MandiriApi.kegiatan(m) == Kegiatan.PERJALANAN) {
						MandiriApi.akhiriKegiatan(m, true);
					}
				}
			}
		}

		@Override
		public double K(DiscipleEntity murid, Konteks konteks) {
			return 0.0D;
		}
	}

	/** A companion walks a step behind its leader and packs when it packs. */
	private static void ikut(DiscipleEntity murid, Adegan a, Adegan lead, long now) {
		if (lead.fase == F_BERKEMAS && murid.activity() != MandiriKegiatan.ACT_BERKEMAS) {
			murid.setActivity(MandiriKegiatan.ACT_BERKEMAS, murid.blockPosition());
			SosokApi.gerak(murid, GerakId.BERKEMAS, 0, 200, PropId.TIDAK_ADA, PropId.TIDAK_ADA, PropId.PETI);
		} else if (lead.fase > F_BERKEMAS && murid.activity() == MandiriKegiatan.ACT_BERKEMAS) {
			murid.setActivity(0, null);
		}
		DiscipleEntity leader = Perencana.cari(murid.ownerId(), lead.soul);
		if (leader != null && lead.fase != F_BERKEMAS) {
			a.sasaran = leader.blockPosition().offset(Math.floorMod(murid.soulId().hashCode(), 3) - 1, 0, -2);
		}
	}

	private static void next(Adegan a, int fase, long now) {
		a.fase = fase;
		a.t0 = now;
	}

	static boolean dekat(DiscipleEntity e, BlockPos p, double r) {
		return e.distanceToSqr(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D) <= r * r;
	}

	static Vec3 arah(BlockPos from, BlockPos to) {
		Vec3 d = new Vec3(to.getX() - from.getX(), 0.0D, to.getZ() - from.getZ());
		return d.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : d.normalize();
	}

	private static boolean semuaInti(DiscipleEntity leader, Adegan a) {
		if (leader.data().realm().ordinal() < Realm.GOLDEN_CORE.ordinal()) {
			return false;
		}
		for (UUID member : a.anggota) {
			DiscipleEntity m = Perencana.cari(leader.ownerId(), member);
			if (m == null || m.data().realm().ordinal() < Realm.GOLDEN_CORE.ordinal()) {
				return false;
			}
		}
		return true;
	}

	/** One server tick of a sword departure: the spiral rise of 20 blocks in six seconds, then a three-second dash. */
	static boolean terbangTick(DiscipleEntity murid) {
		Adegan a = ADEGAN.get(murid.soulId());
		if (a == null || !a.terbang || !(murid.level() instanceof ServerLevel level)) {
			murid.setNoGravity(false);
			return false;
		}
		long t = level.getGameTime() - a.t0;
		murid.getNavigation().stop();
		murid.setDeltaMovement(Vec3.ZERO);
		if (a.fase == F_TERBANG) {
			double f = Math.min(1.0D, t / 120.0D);
			double angle = f * Math.PI * 4.0D;
			double r = 1.5D + f * 1.5D;
			murid.setPos(a.pusat.x + Math.cos(angle) * r, a.pusat.y + 20.0D * f, a.pusat.z + Math.sin(angle) * r);
			if (t % 4L == 0L) {
				Fx.burst(level, murid.position(), 0xBFE8FF, 3, 0.3F);
			}
			if (t >= 120L) {
				a.pusat = murid.position();
				next(a, F_MELESAT, level.getGameTime());
				level.playSound(null, murid.getX(), murid.getY(), murid.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.NEUTRAL, 0.8F, 1.3F);
			}
			return true;
		}
		if (a.fase == F_MELESAT) {
			double d = t * 1.6D;
			murid.setPos(a.pusat.x + a.arah.x * d, a.pusat.y + t * 0.15D, a.pusat.z + a.arah.z * d);
			Fx.beam(level, murid.position(), murid.position().subtract(a.arah.scale(3.0D)), 0xBFE8FF, 0.12F);
			if (t >= 60L) {
				Fx.burst(level, murid.position(), 0xE8F6FF, 16, 1.0F);
				murid.setNoGravity(false);
				a.terbang = false;
				berangkat(murid, a);
				return false;
			}
			return true;
		}
		return true;
	}

	/** The party leaves the world: the plan, the away records, the story of the gate. */
	private static void berangkat(DiscipleEntity leader, Adegan a) {
		if (a.berangkat || a.pilihan == null || !(leader.level() instanceof ServerLevel level)) {
			MandiriApi.akhiriKegiatan(leader, true);
			return;
		}
		a.berangkat = true;
		List<DiscipleEntity> party = new ArrayList<>();
		party.add(leader);
		for (UUID member : a.anggota) {
			DiscipleEntity m = Perencana.cari(leader.ownerId(), member);
			if (m != null && m.isAlive() && m.level() == level && MandiriApi.kegiatan(m) == Kegiatan.PERJALANAN) {
				Adegan ma = ADEGAN.get(member);
				if (ma != null) {
					ma.berangkat = true;
				}
				party.add(m);
			}
		}
		try {
			Perjalanan.berangkat(level, party, a.pilihan.jenis(), a.pilihan.tujuan());
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Mandiri departure of {} failed", leader.getName().getString(), exception);
		}
		if (a.asal != null && !leader.isRemoved()) {
			// The leader did not leave (storeAway refused): back to where the flight began, not into a hill 96 blocks away.
			leader.teleportTo(a.asal.x, a.asal.y, a.asal.z);
			leader.resetFallDistance();
		}
		for (DiscipleEntity d : party) {
			ADEGAN.remove(d.soulId());
		}
		PILIHAN.remove(leader.soulId());
	}

	// ================================================================== TIBA

	private static final class Tibanya implements Pelaksana {

		@Override
		public boolean bisa(DiscipleEntity murid, Konteks konteks) {
			return JalanData.get(konteks.level().getServer()).adaTiba(murid.soulId());
		}

		@Override
		public void mulai(DiscipleEntity murid, Konteks konteks) {
			Adegan a = new Adegan(murid.soulId(), true, null);
			a.fase = T_BAWA;
			a.t0 = konteks.level().getGameTime();
			a.sasaran = konteks.tempat();
			ADEGAN.put(murid.soulId(), a);
			murid.setActivity(MandiriKegiatan.ACT_BAWA, null);
			int barang = arrival(murid) == null ? 0 : arrival(murid).getIntOr("barang", 0);
			int back = barang >= 4 ? PropId.PETI : barang > 0 ? PropId.RANGKA_PUNGGUNG : PropId.TIDAK_ADA;
			SosokApi.gerak(murid, GerakId.JALAN_BAWA, GerakId.V_JALAN_BAWA_PUNGGUNG, 0, PropId.TIDAK_ADA, PropId.TIDAK_ADA, back);
		}

		@Override
		public boolean jalan(DiscipleEntity murid, Konteks konteks) {
			Adegan a = ADEGAN.get(murid.soulId());
			CompoundTag arrival = arrival(murid);
			if (a == null || arrival == null) {
				return false;
			}
			long now = konteks.level().getGameTime();
			long lama = now - a.t0;
			switch (a.fase) {
				case T_BAWA -> {
					if (a.sasaran == null || dekat(murid, a.sasaran, 2.5D) || lama > 400L) {
						next(a, T_BONGKAR, now);
						murid.setActivity(MandiriKegiatan.ACT_BERKEMAS, a.sasaran);
						SosokApi.gerak(murid, GerakId.BERKEMAS, 0, 200, PropId.TIDAK_ADA, PropId.TIDAK_ADA, PropId.PETI);
					}
				}
				case T_BONGKAR -> {
					if (lama >= 200L) {
						murid.setActivity(0, null);
						Kepulangan.antar(murid, arrival, false);
						Fx.burst(konteks.level(), murid.position().add(0.0D, 0.6D, 0.0D), 0xE8C890, 12, 0.6F);
						next(a, T_HADIAH, now);
						a.sasaran = null;
					}
				}
				case T_HADIAH -> {
					ListTag gifts = arrival.getListOrEmpty("hadiah");
					if (a.hadiahKe >= gifts.size()) {
						next(a, T_LAPOR, now);
						break;
					}
					CompoundTag gift = gifts.getCompoundOrEmpty(a.hadiahKe);
					Entity target = penerima(murid, gift);
					if (target == null || lama > 400L) {
						Hadiah.beri(murid, gift, false);
						gifts.remove(a.hadiahKe); // off the live arrival list, so an early end (antar) never gives it twice
						a.t0 = now;
					} else if (murid.distanceToSqr(target) <= 2.5D * 2.5D) {
						murid.getLookControl().setLookAt(target, 30.0F, 30.0F);
						Hadiah.beri(murid, gift, true);
						gifts.remove(a.hadiahKe);
						a.t0 = now;
					} else {
						a.sasaran = target.blockPosition();
					}
				}
				case T_LAPOR -> {
					arrival.put("hadiah", new ListTag());
					Kepulangan.selesai(murid, arrival);
					return false;
				}
				default -> {
					return false;
				}
			}
			return true;
		}

		@Override
		public void selesai(DiscipleEntity murid, Konteks konteks, boolean batal) {
			ADEGAN.remove(murid.soulId());
			int act = murid.activity();
			if (act == MandiriKegiatan.ACT_BAWA || act == MandiriKegiatan.ACT_BERKEMAS) {
				murid.setActivity(0, null);
			}
			SosokApi.henti(murid);
			CompoundTag arrival = arrival(murid);
			if (arrival != null) {
				// interrupted (an order, a raid): what is left is delivered at once
				Kepulangan.antar(murid, arrival, true);
				Kepulangan.selesai(murid, arrival);
			}
		}

		@Override
		public double K(DiscipleEntity murid, Konteks konteks) {
			return 0.0D;
		}
	}

	private static @Nullable CompoundTag arrival(DiscipleEntity murid) {
		return murid.level() instanceof ServerLevel level ? JalanData.get(level.getServer()).tiba(murid.soulId()) : null;
	}

	/** Who a gift goes to when it stands within 48 blocks: the master or a loaded friend. */
	private static @Nullable Entity penerima(DiscipleEntity murid, CompoundTag gift) {
		String ke = gift.getStringOr("ke", DaftarBelanja.KE_GURU);
		if (DaftarBelanja.KE_GURU.equals(ke)) {
			ServerPlayer master = murid.level() instanceof ServerLevel level && murid.ownerId() != null
					? level.getServer().getPlayerList().getPlayer(murid.ownerId()) : null;
			return master != null && master.level() == murid.level() && master.distanceToSqr(murid) <= 48.0D * 48.0D ? master : null;
		}
		UUID friend = Rencana.uuid(ke);
		DiscipleEntity other = friend == null ? null : Perencana.cari(murid.ownerId(), friend);
		return other != null && other.isAlive() && other.distanceToSqr(murid) <= 48.0D * 48.0D ? other : null;
	}

	/** Local speech lines (chat to players within a radius, the body's own voice is separate). */
	static final class Msg2 {
		static void dekat(Entity speaker, net.minecraft.network.chat.Component text, double radius) {
			if (!(speaker.level() instanceof ServerLevel level)) {
				return;
			}
			net.minecraft.network.chat.MutableComponent line = net.minecraft.network.chat.Component.literal("")
					.append(speaker.getName().copy().withColor(JalanIds.WARNA_NAMA)).append(": ").append(text.copy().withColor(0xE6E0D0));
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(speaker) <= radius * radius) {
					dev.kultivator.util.Msg.chat(p, line);
				}
			}
		}

		private Msg2() {
		}
	}

	/** Forgets scenes of bodies that are gone (unloaded mid-scene). */
	static void bersihkan(java.util.function.Predicate<UUID> hidup) {
		for (Iterator<Map.Entry<UUID, Adegan>> it = ADEGAN.entrySet().iterator(); it.hasNext(); ) {
			if (!hidup.test(it.next().getKey())) {
				it.remove();
			}
		}
		PILIHAN.keySet().removeIf(soul -> !hidup.test(soul));
	}

	private PelaksanaJalan() {
	}
}
