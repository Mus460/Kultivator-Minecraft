package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.RencanaKotaBesar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The capital map and directory ({@code peta_kota}, DESIGN 38.U.3.8): a read-only hub card "Kota Besar" and the sheet the
 * gate guards and the post office clerk open. It shows the capital the player stands in, else the nearest one he knows:
 * the nine districts on their 3 x 3 grid and the two outside the walls, every service with its hours and whether it is
 * open now, the officials (the Prince Warden, the Magistrate, the Founder on the statue) and the calendar of the next
 * fortnight. Op {@code tandai:X:Z} is handled by the screen itself (map pin).
 */
final class PetaKota {

	/** One service of the directory: screen id, district, lang id, icon, opening hours (0..24000; both 0 = always). */
	private record Layanan(String layar, String lot, String distrik, String nama, int ikon, long buka, long tutup) {
	}

	private static final List<Layanan> LAYANAN = List.of(
			new Layanan(LembarKunci.Acara.LAYAR, "audiensi", "istana", "audiensi", LembarKunci.Ikon.ISTANA, 10000L, 12000L),
			new Layanan(LembarKunci.Acara.LAYAR, "perintah_istana", "istana", "perintah", LembarKunci.Ikon.ISTANA, 0L, 12000L),
			new Layanan(LembarKunci.Acara.LAYAR, "pertemuan_sekte", "istana", "pertemuan", LembarKunci.Ikon.ISTANA, 6000L, 9000L),
			new Layanan(LembarKunci.Layanan.LAYAR_JASA, "perpustakaan", "cendekia", "perpustakaan", LembarKunci.Ikon.PERPUSTAKAAN, 0L, 12000L),
			new Layanan(LembarKunci.Layanan.LAYAR_JASA, "kuil", "cendekia", "kuil", LembarKunci.Ikon.KUIL, 23000L, 13000L),
			new Layanan(LembarKunci.Acara.LAYAR, "menara_pengamat", "cendekia", "menara", LembarKunci.Ikon.MENARA, 12000L, 23000L),
			new Layanan(LembarKunci.Teleport.LAYAR, "teleport", "awan", "teleport", LembarKunci.Ikon.TELEPORT, 0L, 0L),
			new Layanan(LembarKunci.Dermaga.LAYAR, "dermaga", "awan", "dermaga", LembarKunci.Ikon.DERMAGA, Teleport.DERMAGA_BUKA, Teleport.DERMAGA_TUTUP),
			new Layanan(LembarKunci.Gilda.LAYAR_ALKEMIS, "gilda_alkemis", "gilda", "gilda_alkemis", LembarKunci.Ikon.GILDA_ALKEMIS, 0L, 13000L),
			new Layanan(LembarKunci.Gilda.LAYAR_TEMPA, "gilda_tempa", "gilda", "gilda_tempa", LembarKunci.Ikon.GILDA_TEMPA, 0L, 13000L),
			new Layanan(LembarKunci.GildaMisi.LAYAR, "gilda_misi", "gilda", "gilda_misi", LembarKunci.Ikon.GILDA_MISI, 0L, 0L),
			new Layanan(LembarKunci.Layanan.LAYAR_JASA, "tabib", "gilda", "tabib", LembarKunci.Ikon.TABIB, 0L, 0L),
			new Layanan(LembarKunci.Yamen.LAYAR, "yamen", "pusat", "yamen", LembarKunci.Ikon.YAMEN, 0L, 12000L),
			new Layanan(LembarKunci.Lelang.LAYAR, "lelang_agung", "pasar", "lelang", LembarKunci.Ikon.LELANG, 12000L, 15000L),
			new Layanan(LembarKunci.Bank.LAYAR, "bank", "pasar", "bank", LembarKunci.Ikon.BANK, 0L, 12000L),
			new Layanan(LembarKunci.JudiBatu.LAYAR, "judi_batu", "pasar", "judi_batu", LembarKunci.Ikon.JUDI_BATU, 6000L, 18000L),
			new Layanan(LembarKunci.PasarBinatang.LAYAR, "pasar_binatang", "pasar", "pasar_binatang", LembarKunci.Ikon.PASAR_BINATANG, 0L, 12000L),
			new Layanan(LembarKunci.TokoSenjata.LAYAR, "toko_senjata", "pasar", "toko_senjata", LembarKunci.Ikon.TOKO_SENJATA, 0L, 12000L),
			new Layanan(LembarKunci.Layanan.LAYAR_PENGINAPAN, "penginapan", "gerbang", "penginapan", LembarKunci.Ikon.PENGINAPAN, 0L, 0L),
			new Layanan(LembarKunci.Layanan.LAYAR_RUMAH_MAKAN, "rumah_makan", "gerbang", "rumah_makan", LembarKunci.Ikon.RUMAH_MAKAN, 0L, 16000L),
			new Layanan(LembarKunci.Acara.LAYAR, "turnamen", "arena", "turnamen", LembarKunci.Ikon.ARENA, 0L, 12000L),
			new Layanan(LembarKunci.Kafilah.LAYAR, "kafilah", "luar", "kafilah", LembarKunci.Ikon.KAFILAH, 0L, 12000L));

	static void init() {
		TempatLayar.daftarHub(LembarKunci.PetaKota.LAYAR, (p, npcId, op) -> {
		}, PetaKota::bangun);
		dev.kultivator.dunia.tempat.cerita.CeritaApi.daftarJasa((p, npc) -> {
			boolean guide = npc.role() == dev.kultivator.dunia.kota.NpcRole.PENJAGA_GERBANG
					|| "kantor_pos".equals(npc.memory().getStringOr("kotabesar_layanan", ""));
			if (!guide || !Hukum.ibuKota(p.level().getServer(), npc.town())) {
				return List.of();
			}
			return List.of(new dev.kultivator.dunia.tempat.cerita.TempatDialog.JasaEntri(LembarKunci.PetaKota.LAYAR,
					Component.translatable("kultivator.kota_besar.jasa.peta"), LembarKunci.Ikon.DISTRIK_PERTAMA,
					Component.translatable("kultivator.kota_besar.jasa.peta.catatan"), false, null));
		});
	}

	/** The capital the player stands in, else the nearest one he knows; null when he knows none. */
	static @Nullable Kandidat kotaUntuk(ServerPlayer p) {
		ServerLevel overworld = p.level().getServer().overworld();
		if (p.level() == overworld) {
			Kandidat here = KotaBesarKota.di(overworld, p.blockPosition());
			if (here != null) {
				return here;
			}
		}
		Kandidat best = null;
		double bestSq = Double.MAX_VALUE;
		for (DuniaSites.Site site : DuniaSites.known(p)) {
			if (!"kota/kota_besar".equals(site.kind())) {
				continue;
			}
			Kandidat k = KotaBesarKota.dariSitus(overworld, site.id());
			if (k == null) {
				continue;
			}
			double d = site.pos().distSqr(p.blockPosition());
			if (d < bestSq) {
				bestSq = d;
				best = k;
			}
		}
		return best;
	}

	static String token(String distrik) {
		return "hunian_barat".equals(distrik) ? LembarKunci.DISTRIK_HUNIAN : distrik;
	}

	private static boolean buka(long t, long open, long close) {
		if (open == close) {
			return true;
		}
		return open < close ? t >= open && t < close : t >= open || t < close;
	}

	private static @Nullable CompoundTag bangun(ServerPlayer p, int npcId) {
		MinecraftServer server = p.level().getServer();
		Kandidat k = kotaUntuk(p);
		CompoundTag tag = new CompoundTag();
		tag.putString(LembarKunci.GAYA, LembarKunci.GAYA_GIOK);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable("kultivator.kota_besar.peta.judul"));
		if (k == null) {
			KotaBesarTampil.kunci(tag, Component.translatable("kultivator.kota_besar.peta.belum"), Component.translatable("kultivator.kota_besar.peta.belum.syarat"));
			return tag;
		}
		long town = KotaBesarKota.situs(k);
		tag = KotaBesarTampil.kepala(p, town, LembarKunci.GAYA_GIOK, null);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable("kultivator.kota_besar.peta.judul"));
		ServerLevel level = server.overworld();
		if (npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc npc) {
			tag.put(LembarKunci.NPC, KotaBesarTampil.kartu(npc));
		}
		long t = Kota.timeOfDay(server);
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		ListTag districts = new ListTag();
		for (RencanaKotaBesar.Petak petak : RencanaKotaBesar.PETAK) {
			CompoundTag d = new CompoundTag();
			d.putString(LembarKunci.ID, token(petak.id()));
			LembarKunci.teks(d, LembarKunci.NAMA, Component.translatable("kultivator.kota_besar.distrik." + petak.id()));
			boolean luar = petak.dx() > RencanaKotaBesar.SEL || petak.dz() > RencanaKotaBesar.SEL;
			d.putBoolean(LembarKunci.PetaKota.LUAR, luar);
			d.putInt(LembarKunci.PetaKota.GX, luar ? -1 : petak.dx() / RencanaKotaBesar.SEL + 1);
			d.putInt(LembarKunci.PetaKota.GZ, luar ? -1 : petak.dz() / RencanaKotaBesar.SEL + 1);
			ListTag inside = new ListTag();
			for (Layanan l : LAYANAN) {
				if (l.distrik().equals(petak.id())) {
					CompoundTag row = new CompoundTag();
					LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.kota_besar.peta.layanan." + l.nama()));
					row.putInt(LembarKunci.IKON, l.ikon());
					LembarKunci.teks(row, LembarKunci.CATATAN, jam(l));
					inside.add(row);
				}
			}
			d.put(LembarKunci.PetaKota.LAYANAN, inside);
			districts.add(d);
		}
		tag.put(LembarKunci.PetaKota.DISTRIK_DAFTAR, districts);
		if (p.level() == level && KotaBesarKota.dalamTembok(k, p.blockPosition())) {
			int[] l = KotaBesarKota.lokal(k, p.getBlockX(), p.getBlockZ());
			tag.putFloat(LembarKunci.PetaKota.POS_X, (l[0] + KotaBesarKota.DALAM) / (float) RencanaKotaBesar.SEL);
			tag.putFloat(LembarKunci.PetaKota.POS_Z, (l[1] + KotaBesarKota.DALAM) / (float) RencanaKotaBesar.SEL);
		}
		String here = p.level() == level ? KotaBesarKota.distrik(k, p.blockPosition()) : null;
		if (here != null) {
			tag.putString(LembarKunci.PetaKota.DISTRIK_PEMAIN, token(here));
		}
		ListTag services = new ListTag();
		for (Layanan l : LAYANAN) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, l.layar());
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.kota_besar.peta.layanan." + l.nama()));
			row.putInt(LembarKunci.IKON, l.ikon());
			row.putString(LembarKunci.DISTRIK, token(l.distrik()));
			LembarKunci.teks(row, LembarKunci.CATATAN, jam(l));
			row.putBoolean(LembarKunci.PetaKota.BUKA, buka(t, l.buka(), l.tutup()) && !GelombangBinatang.tokoTutup(server, town));
			DuniaAnchorEntity anchor = kota == null ? null : kota.layanan(l.lot());
			BlockPos at = anchor != null ? anchor.blockPosition() : KotaBesarKota.pusatDistrik(level, k, l.distrik());
			row.putInt(LembarKunci.X, at.getX());
			row.putInt(LembarKunci.Z, at.getZ());
			services.add(row);
		}
		tag.put(LembarKunci.PetaKota.LAYANAN, services);
		ListTag people = new ListTag();
		long seed = level.getSeed();
		people.add(tokoh("pangeran", KotaBesarNames.pangeran(seed, k.index()), "istana", Component.translatable("kultivator.kota_besar.peta.tokoh.pangeran")));
		people.add(tokoh("hakim", KotaBesarNames.hakim(seed, k.index()), "pusat", Component.translatable("kultivator.kota_besar.peta.tokoh.hakim")));
		people.add(tokoh("pendiri", KotaBesarNames.pendiri(seed, k.index()), "pusat", Component.translatable("kultivator.kota_besar.peta.tokoh.pendiri")));
		tag.put(LembarKunci.PetaKota.TOKOH, people);
		ListTag events = new ListTag();
		long day = Kota.day(server);
		for (int i = 0; i < 14 && events.size() < 12; i++) {
			long d = day + i;
			String id = KotaBesarKalender.jadwal(server, town, d);
			if (id == null) {
				continue;
			}
			KotaBesarKalender.Acara a = KotaBesarKalender.acara(id);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, id);
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.kota_besar.acara." + id));
			row.putInt(LembarKunci.HARI, (int) d);
			boolean running = i == 0 && KotaBesarKalender.berjalan(server, town, id);
			row.putBoolean(LembarKunci.AKTIF, running);
			long until = i * 24000L + a.mulaiJam() - t;
			if (until > 0L) {
				row.putLong(LembarKunci.SISA_TICK, until);
			}
			LembarKunci.teks(row, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.acara.jam", KotaBesarKalender.jam(a.mulaiJam()),
					KotaBesarKalender.jam(a.akhirJam())));
			events.add(row);
		}
		tag.put(LembarKunci.PetaKota.ACARA, events);
		return tag;
	}

	private static Component jam(Layanan l) {
		return l.buka() == l.tutup() ? Component.translatable("kultivator.kota_besar.peta.selalu")
				: Component.translatable("kultivator.kota_besar.acara.jam", KotaBesarKalender.jam(l.buka()), KotaBesarKalender.jam(l.tutup()));
	}

	private static CompoundTag tokoh(String id, Component nama, String distrik, Component gelar) {
		CompoundTag row = new CompoundTag();
		row.putString(LembarKunci.ID, id);
		LembarKunci.teks(row, LembarKunci.NAMA, nama);
		LembarKunci.teks(row, LembarKunci.GELAR, gelar);
		row.putString(LembarKunci.DISTRIK, token(distrik));
		LembarKunci.teks(row, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.peta.tokoh." + id + ".di"));
		row.putBoolean(LembarKunci.PetaKota.KENAL, true);
		return row;
	}

	private PetaKota() {
	}
}
