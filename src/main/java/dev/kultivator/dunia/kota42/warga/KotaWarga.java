package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaUjiWarga;
import dev.kultivator.dunia.kota42.api.PenimpaJadwalKota;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.tempat.hidup.HidupCommands;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.PendudukData;
import dev.kultivator.dunia.tempat.hidup.TubuhPenduduk;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Runtime init of 42A Inti Warga (DESIGN 42.4, 42.13.1), called once from {@code KotaHidup.init} on both sides (registries
 * are not here). Wires the stored people into 38NL through the seams 42H0 left in {@code dunia/tempat/hidup}:
 * <ul>
 * <li>{@link PendudukData#setPenerus}: the slot as the person makes it ({@code WargaApi.terapkan}), empty slots and
 * travellers away, the 42 life states ({@code SAKIT}, {@code MABUK}, {@code PENGANTIN}, {@code BERKABUNG}), meetings with
 * players into {@link Ingatan} (greeted by name from the fifth day: {@code kota42_kenal}), a killed body's person dies
 * ({@link SiklusHidup#wafat}, cause "serangan") instead of a newcomer in three days. Everything declines while
 * {@code kotaWarga.aktif} is off or the place was not raised, so 38 runs exactly as built.</li>
 * <li>{@link Pemanggil#setPenghias}: a new body gets the person's name plate, sex, visible age and face.</li>
 * <li>{@link TubuhPenduduk#setMitraJalan}: lovers, dusk couples and grandparent with grandchild walk side by side;
 * {@link TubuhPenduduk#setMusuhJalan}: enemies glare at each other when they pass.</li>
 * <li>{@link Jadwal#addPenimpa}: one bridge that asks every {@link PenimpaJadwalKota} of {@code WargaApi.daftarPenimpa}
 * with the person resolved (42A-2 habits, 42D ceremonies, 42S plans).</li>
 * <li>{@link HariWarga}: the daily pass (tick): birthdays and weddings, meetings, the 42A-2 step
 * ({@code ArsipWarga.harian}: habits, moods, work, moves, gifted children, memory drift), statistics; other modules add
 * theirs with {@link HariWarga#addLangkah}.</li>
 * <li>{@code uji hidup audit warga} lines and the {@code uji kota_hidup warga ...} / {@code kotaku warga} commands
 * ({@link KotaUjiWarga}).</li>
 * </ul>
 * Behaviour checks {@code kotaWarga} at run time ({@code KotaConfig}), so a reload can switch it off and on.
 */
public final class KotaWarga {

	private static boolean initialised;

	/** From {@code KotaHidup.init}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// 42A-2 (moods, habits, its schedule overrider, extra slots, moves, travellers): one init, one daily step
		ArsipWarga.init();
		HariWarga.addLangkah(HariWarga.URUT_SIKLUS, "siklus", HariWarga.SIKLUS);
		HariWarga.addLangkah(HariWarga.URUT_TEMU, "temu", HariWarga.TEMU);
		HariWarga.addLangkah(HariWarga.URUT_SUASANA, "warga2", (server, data, s, hari, random) -> ArsipWarga.harian(server, s, hari));
		HariWarga.addLangkah(HariWarga.URUT_STAT, "stat", HariWarga.STAT);
		ServerTickEvents.END_SERVER_TICK.register(HariWarga::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HariWarga.lupakan());
		PendudukData.setPenerus(new Penerus());
		Pemanggil.setPenghias(KotaWarga::hias);
		TubuhPenduduk.setMitraJalan(WargaApi::mitraJalan);
		TubuhPenduduk.setMusuhJalan(KotaWarga::musuh);
		Jadwal.addPenimpa(Jadwal.URUT_UPACARA, KotaWarga::timpa);
		HidupCommands.setPenjelasWarga(KotaUjiWarga::barisAudit);
		KotaUjiWarga.init();
		// 42S-R caches plans and groups per place: drop them when the residents change mid-day
		KotaBus.LAHIR.register((server, situs, bayi, ibu) -> lupakanRencana(situs));
		KotaBus.WAFAT.register((server, situs, warga, sebab) -> lupakanRencana(situs));
		KotaBus.KERJA_BERUBAH.register((server, situs, warga, lama, baru) -> lupakanRencana(situs));
		KotaBus.PINDAH.register((server, warga, asal, tujuan) -> {
			lupakanRencana(asal);
			lupakanRencana(tujuan);
		});
		Migrasi.setPembuatPendatang((server, situs, peran, hari) -> pendatang(server, situs, peran, hari));
	}

	private static void lupakanRencana(long situs) {
		dev.kultivator.dunia.kota42.ragam.RencanaHarian.lupakan(situs);
		dev.kultivator.dunia.kota42.ragam.KegiatanKelompok.lupakan(situs);
	}

	/** True while 42 owns the people of {@code situs} (switch on, place raised). */
	static boolean milik(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || !WargaApi.aktif()) {
			return false;
		}
		WargaData.Situs s = WargaData.get(server).peek(situs);
		return s != null && s.bangkit();
	}

	// ================================================================== the seam of PendudukData

	private static final class Penerus implements PendudukData.Penerus {
		@Override
		public @Nullable Penduduk terapkan(long situs, Penduduk p) {
			return milik(situs) ? WargaApi.terapkan(situs, p) : null;
		}

		@Override
		public int absen(long situs, int id, long now) {
			if (!milik(situs)) {
				return PendudukData.LANJUT;
			}
			Warga w = WargaApi.warga(situs, id);
			if (w == null || w.status != Warga.Status.HIDUP || WargaApi.umur(w) < 3) {
				// an empty slot (a death, a retirement, a move) waits for its successor (42A-2 Pekerjaan)
				return 1;
			}
			return PendudukData.LANJUT;
		}

		@Override
		public int keadaan(long situs, int id, int rumahTangga, long now) {
			if (!milik(situs)) {
				return PendudukData.LANJUT;
			}
			int k = WargaApi.keadaan(situs, id, now);
			return k == PendudukData.SEHAT ? PendudukData.LANJUT : k;
		}

		@Override
		public int kenal(long situs, int id, UUID player) {
			MinecraftServer server = KotaHidup.server();
			Warga w = milik(situs) ? WargaApi.warga(situs, id) : null;
			if (w == null || server == null) {
				return PendudukData.LANJUT;
			}
			WargaData data = WargaData.get(server);
			Ingatan i = Ingatan.untuk(data, w, player);
			i.sapa(KotaHidup.hari(server));
			if (i.kenal >= 5) {
				i.nama = true;
				ServerPlayer online = server.getPlayerList().getPlayer(player);
				if (online != null) {
					Advancements.grant(online, "tempat/kota42_kenal");
				}
			}
			data.ubah();
			return i.kenal;
		}

		@Override
		public int kenalan(long situs, int id, UUID player) {
			MinecraftServer server = KotaHidup.server();
			Warga w = milik(situs) ? WargaApi.warga(situs, id) : null;
			if (w == null || server == null) {
				return PendudukData.LANJUT;
			}
			Ingatan i = w.ingatanDari(WargaData.get(server).cariPemain(player));
			return i == null ? 0 : i.kenal;
		}

		@Override
		public boolean mati(long situs, int id, int rumahTangga, long now) {
			MinecraftServer server = KotaHidup.server();
			Warga w = milik(situs) ? WargaApi.warga(situs, id) : null;
			if (w == null || server == null) {
				return false;
			}
			SiklusHidup.wafat(server, WargaData.get(server), situs, w, "serangan", KotaHidup.hari(server));
			return true;
		}
	}

	/** 42A-2 Migrasi's newcomer from beyond the map: an adult of {@code peran}, habits rolled, added to the place (Pekerjaan gives the slot). */
	private static Warga pendatang(MinecraftServer server, long situs, dev.kultivator.dunia.kota.NpcRole peran, long hari) {
		WargaData data = WargaData.get(server);
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(situs * 31L + hari * 0x9E3779B97F4A7C15L + data.semuaSitus().size());
		Warga w = WargaGen.pendatang(data, peran, 18, 40, hari, random);
		Kebiasaan.undiAwal(w, SiklusHidup.umurPada(w, hari), random);
		data.tambah(situs, w);
		data.ubah();
		return w;
	}

	// ================================================================== bodies

	/** A new resident body: the person's name plate, sex, visible age and face (42.4.14). */
	private static void hias(net.minecraft.server.level.ServerLevel level, dev.kultivator.dunia.tempat.hidup.TempatHuni huni, KultivatorNpc npc,
			Penduduk p, long now) {
		if (!milik(huni.situsId)) {
			return;
		}
		Warga w = WargaApi.warga(huni.situsId, p.id());
		if (w == null) {
			return;
		}
		npc.setPlate(WargaApi.namaPapan(huni.situsId, p));
		Sosok sosok = SosokApi.sosok(npc).withKelamin(w.pria() ? Sosok.LAKI : Sosok.PEREMPUAN).withUsia(WargaApi.usiaSosok(w));
		SosokApi.setSosok(npc, sosok);
		SosokApi.setSuasana(npc, WargaApi.wajah(w, now), 0);
		if (Berbakat.aura(w) > 0.0F) {
			// shortcut: a gifted child glows the whole visit, not only while sitting; a sit hook in TubuhPenduduk would narrow it
			npc.setFlag(KultivatorNpc.FLAG_AURA, true);
		}
	}

	/** The body of an enemy ({@code MUSUH} bond) of {@code npc}'s person, if one is out in the same place. */
	private static @Nullable KultivatorNpc musuh(KultivatorNpc npc) {
		Warga w = milik(npc.situsPenduduk()) ? WargaApi.dari(npc) : null;
		dev.kultivator.dunia.tempat.hidup.TempatHuni huni = w == null ? null : Pemanggil.huni(npc.situsPenduduk());
		if (huni == null || w.hubungan.length == 0) {
			return null;
		}
		for (long bond : w.hubungan) {
			if (dev.kultivator.dunia.kota42.api.JenisHubungan.jenis(bond) == dev.kultivator.dunia.kota42.api.JenisHubungan.MUSUH) {
				Warga foe = WargaApi.byId(dev.kultivator.dunia.kota42.api.JenisHubungan.idLain(bond));
				KultivatorNpc body = foe == null || foe.slot == Warga.TANPA_SLOT ? null : huni.tubuhDari(foe.slot);
				if (body != null) {
					return body;
				}
			}
		}
		return null;
	}

	/** The one 38 overrider that asks every 42 overrider with the stored person resolved. */
	private static @Nullable Kegiatan timpa(dev.kultivator.dunia.tempat.hidup.TempatHuni huni, Penduduk p, long waktu, int jam, long now) {
		if (WargaApi.penimpa().isEmpty() || !milik(huni.situsId)) {
			return null;
		}
		Warga w = WargaApi.warga(huni.situsId, p.id());
		if (w == null) {
			return null;
		}
		for (PenimpaJadwalKota each : WargaApi.penimpa()) {
			Kegiatan k = each.timpa(huni, w, p, waktu, jam, now);
			if (k != null) {
				return k;
			}
		}
		return null;
	}




	private KotaWarga() {
	}
}
