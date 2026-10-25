package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokGen;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raising residents (DESIGN 42.4.2, 42.4.13 "Bangkit pertama"): the first time a place lives, every 38 slot of
 * {@code PendudukGen} gets a stored person, deterministic from {@code (situs, world seed)}. Households follow the slot's
 * {@code kelompok}: the working adults found it (the first two of opposite sex are a married couple, 42.16 K9: always one
 * man and one woman), the {@code ANAK} slots are their children, the {@code TETUA} slots the parents of the head. One
 * family name per household (the wife keeps her own), siblings share the generation syllable (zibei), ages by role (ANAK
 * 3-12, TETUA 60-80, work 18-60) and consistent with the family (a mother is 18-42 years older than each child). A few
 * young couples get a baby that has no slot yet. Then 2-4 weighted bonds per resident ({@link Hubungan#awal}).
 * <p>
 * Also the births ({@link #bayi}) and newcomers ({@link #pendatang}) of later days. Looks are chosen so the body's sex
 * ({@link SosokGen#kelamin}) matches the stored sex; a child's look mixes the bits of both parents ("benih campuran").
 */
public final class WargaGen {

	/** Spirit roots at birth inside {@code kotaWarga.peluangAkar} (15 %): TRASH 6, LOW 5, MID 2.8, HIGH 1, HEAVENLY 0.2. */
	private static final double[] AKAR = {6.0D, 5.0D, 2.8D, 1.0D, 0.2D};
	private static final double AKAR_TOTAL = 15.0D;
	/** {@code Situs.stat} key: household id -> beds of its house (the generated slots, at least 2). */
	public static final String KAPASITAS = "kap";

	/** The seed of a place (stable for one world). */
	public static long benihTempat(long worldSeed, long situs) {
		return mix(worldSeed ^ situs * 0x9E3779B97F4A7C15L ^ 0x42A42A42L);
	}

	/** SplitMix64 finaliser (the same as 38's, never {@code java.util.Random}). */
	public static long mix(long z) {
		z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
		z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
		return z ^ z >>> 31;
	}

	// ================================================================== the first raising

	/** A household member being made (before ids are fixed into family links). */
	private static final class Calon {
		final Penduduk p;
		final Warga w;
		int umur;

		Calon(Penduduk p, Warga w) {
			this.p = p;
			this.w = w;
		}
	}

	/**
	 * Fills place {@code situs} with residents for the 38 slots {@code slot} (ids below {@code jumlahAsli}; guests too) on
	 * world day {@code hari}. Does nothing when the place was raised before. Returns the residents made.
	 */
	public static List<Warga> bangkitkan(WargaData data, long situs, List<Penduduk> slot, int jumlahAsli, long hari, long worldSeed) {
		WargaData.Situs s = data.situs(situs);
		if (s.bangkit()) {
			return List.of();
		}
		RandomSource random = RandomSource.create(benihTempat(worldSeed, situs));
		Map<Integer, List<Penduduk>> perRumah = new LinkedHashMap<>();
		int maxRumah = 0;
		for (Penduduk p : slot) {
			if (p.id() >= jumlahAsli) {
				continue;
			}
			int key = p.tamu() || p.kelompok() <= 0 ? -1 - p.id() : p.kelompok();
			perRumah.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
			maxRumah = Math.max(maxRumah, p.kelompok());
		}
		s.stat.putInt("rtNext", maxRumah + 1);
		List<Warga> semua = new ArrayList<>();
		Map<Integer, Integer> umur = new HashMap<>();
		net.minecraft.nbt.CompoundTag kap = new net.minecraft.nbt.CompoundTag();
		for (Map.Entry<Integer, List<Penduduk>> e : perRumah.entrySet()) {
			int rumahTangga = e.getKey() > 0 ? e.getKey() : s.rumahTanggaBaru();
			if (e.getKey() > 0) {
				// beds of the house = the slots generated into it (42.4.4: capacity, at least 2)
				kap.putInt(Integer.toString(rumahTangga), Math.max(2, e.getValue().size()));
			}
			for (Calon c : rumahTangga(data, e.getValue(), rumahTangga, hari, random)) {
				semua.add(c.w);
				umur.put(c.w.id, c.umur);
			}
		}
		for (Warga w : semua) {
			data.tambah(situs, w);
		}
		Hubungan.awal(data, semua, umur, hari, random);
		for (Warga w : semua) {
			Kebiasaan.undiAwal(w, umur.get(w.id), random);
		}
		s.hariTerakhir = hari;
		s.stat.put(KAPASITAS, kap);
		s.stat.putInt("awal", semua.size());
		s.stat.putLong("bangkitHari", hari);
		data.ubah();
		return semua;
	}

	/** One household: head, spouse, siblings, children, the head's parents, maybe a baby. */
	private static List<Calon> rumahTangga(WargaData data, List<Penduduk> slots, int rumahTangga, long hari, RandomSource random) {
		List<Calon> kerja = new ArrayList<>();
		List<Calon> anak = new ArrayList<>();
		List<Calon> tetua = new ArrayList<>();
		for (Penduduk p : slots) {
			Calon c = new Calon(p, new Warga(data.idBaru()));
			c.w.slot = p.id();
			c.w.peran = p.peran().id();
			c.w.rumahTangga = rumahTangga;
			c.w.rumah = p.rumah();
			NpcRole.Kelompok k = p.peran().kelompok;
			if (k == NpcRole.Kelompok.ANAK) {
				anak.add(c);
			} else if (k == NpcRole.Kelompok.TETUA && p.peran() != NpcRole.KEPALA_DESA && p.peran() != NpcRole.MAK_COMBLANG) {
				tetua.add(c);
			} else {
				kerja.add(c);
			}
		}
		KotaConfig.Warga cfg = KotaConfig.warga();
		long hpt = cfg.hariPerTahun;
		// a household of only elders is led by the eldest; one of only children is orphaned siblings
		boolean yatim = false;
		if (kerja.isEmpty() && !tetua.isEmpty()) {
			kerja.add(tetua.remove(0));
		} else if (kerja.isEmpty()) {
			kerja.add(anak.remove(0));
			yatim = true;
		}
		Calon kepala = kerja.get(0);
		kepala.w.kelamin = kelaminUntuk(kepala.p.peran(), random.nextDouble() < cfg.rasioPerempuan ? Warga.WANITA : Warga.PRIA, random);
		kepala.umur = umurPeran(kepala.p.peran(), random);
		// the spouse: the first other working adult who may be of the other sex (42.16 K9: one man and one woman)
		Calon pasangan = null;
		if (!yatim) {
			for (int i = 1; i < kerja.size(); i++) {
				Calon c = kerja.get(i);
				int wajib = kelaminWajib(c.p.peran());
				byte lawan = kepala.w.pria() ? Warga.WANITA : Warga.PRIA;
				if (wajib < 0 || wajib == lawan) {
					pasangan = c;
					c.w.kelamin = lawan;
					break;
				}
			}
		}
		Calon suami = kepala.w.pria() ? kepala : pasangan;
		Calon istri = kepala.w.pria() ? pasangan : kepala;
		if (pasangan != null) {
			int beda = pasangan == istri ? -random.nextInt(7) : random.nextInt(7);
			pasangan.umur = Mth.clamp(kepala.umur + beda + random.nextInt(3) - 1, 18, 75);
		}
		// ruzhui (a son-in-law who married in, 10 %): the household and the children carry the wife's family name
		boolean ruzhui = suami != null && istri != null && random.nextInt(100) < 10;
		Calon puncak = ruzhui ? istri : suami != null ? suami : kepala;
		int margaRumah = NamaWarga.margaAcak(random);
		int genKepala = random.nextInt(8);
		boolean zibeiKeluarga = random.nextInt(100) < 80;
		puncak.w.marga = margaRumah;
		puncak.w.generasi = genKepala;
		Calon ayah = yatim ? null : suami;
		Calon ibu = yatim ? null : istri;

		// the other working adults: grown children when the head is old enough, else the line's younger siblings
		List<Calon> saudara = new ArrayList<>();
		List<Calon> dewasaAnak = new ArrayList<>();
		for (Calon c : kerja) {
			if (c == kepala || c == pasangan) {
				continue;
			}
			c.w.kelamin = kelaminUntuk(c.p.peran(), random.nextDouble() < cfg.rasioPerempuan ? Warga.WANITA : Warga.PRIA, random);
			if (!yatim && kepala.umur >= 40 && random.nextBoolean()) {
				c.umur = Mth.clamp(kepala.umur - 20 - random.nextInt(8), 16, 40);
				dewasaAnak.add(c);
			} else {
				c.umur = Mth.clamp(puncak.umur - 1 - random.nextInt(8), 16, 60);
				saudara.add(c);
			}
		}
		kerja.removeAll(saudara);
		kerja.removeAll(dewasaAnak);
		if (yatim) {
			// orphans: the other children are the head's siblings
			saudara.addAll(anak);
			anak.clear();
		}
		for (Calon c : anak) {
			c.umur = 3 + random.nextInt(10);
			c.w.kelamin = kelaminUntuk(c.p.peran(), random.nextDouble() < cfg.rasioPerempuan ? Warga.WANITA : Warga.PRIA, random);
		}
		anak.addAll(dewasaAnak);
		for (Calon c : saudara) {
			if (c.umur == 0) {
				c.umur = yatim ? 3 + random.nextInt(10) : Mth.clamp(puncak.umur - 1 - random.nextInt(8), 3, 60);
				c.w.kelamin = kelaminUntuk(c.p.peran(), random.nextDouble() < cfg.rasioPerempuan ? Warga.WANITA : Warga.PRIA, random);
			}
		}
		int umurMaksAnak = 0;
		for (Calon c : anak) {
			umurMaksAnak = Math.max(umurMaksAnak, c.umur);
		}
		// a mother is 18-42 years older than each of her children; the father keeps near her age
		if (umurMaksAnak > 0) {
			Calon mother = ibu != null ? ibu : kepala;
			if (mother.umur < umurMaksAnak + 18) {
				mother.umur = umurMaksAnak + 18 + random.nextInt(4);
			}
			for (Calon c : anak) {
				c.umur = Math.max(c.umur, mother.umur - 42);
			}
			if (ayah != null && ibu != null) {
				ayah.umur = Math.max(ayah.umur, ibu.umur - 3);
			}
		}
		// the parents of the line's head (the husband's, or the wife's when he married in)
		Calon kakek = null;
		Calon nenek = null;
		for (Calon c : tetua) {
			boolean mauPria = kakek == null && (nenek != null || random.nextBoolean());
			c.w.kelamin = kelaminUntuk(c.p.peran(), mauPria ? Warga.PRIA : Warga.WANITA, random);
			c.umur = Math.min(60 + random.nextInt(21), cfg.umurMaks - 2);
			if (c.w.pria() && kakek == null) {
				kakek = c;
			} else if (!c.w.pria() && nenek == null) {
				nenek = c;
			}
		}
		// final consistency, top-down: the line under its parents, the spouse near the line's head, children under the mother
		List<Calon> lini = new ArrayList<>(saudara);
		lini.add(0, puncak);
		Calon induk = nenek != null ? nenek : kakek;
		if (induk != null) {
			for (Calon c : lini) {
				c.umur = Mth.clamp(c.umur, induk.umur - (induk == nenek ? 40 : 45), induk.umur - 18);
			}
		}
		Calon sisi = pasangan == null ? null : puncak == kepala ? pasangan : kepala;
		if (sisi != null) {
			sisi.umur = Math.max(18, Mth.clamp(sisi.umur, puncak.umur - 8, puncak.umur + 8));
		}
		Calon mother = ibu != null ? ibu : kepala;
		if (!yatim) {
			mother.umur = Math.max(mother.umur, 21);
			for (Calon c : anak) {
				c.umur = Mth.clamp(c.umur, Math.max(3, mother.umur - 42), Math.max(3, mother.umur - 18));
			}
		}
		List<Calon> semua = all(tetua, kerja, saudara, anak);
		for (Calon c : semua) {
			c.w.lahir = hari * 10L - (c.umur * 10L * hpt + random.nextInt((int) (10L * hpt)));
		}

		// ------------------------------------------------------------ family names and links
		for (Calon c : saudara) {
			c.w.marga = puncak.w.marga;
			c.w.generasi = puncak.w.generasi;
		}
		Calon luar = pasangan == null ? null : puncak == suami ? istri : suami;
		if (luar != null) {
			luar.w.marga = margaLain(margaRumah, random);
			luar.w.generasi = random.nextInt(8);
		}
		for (Calon c : anak) {
			c.w.marga = margaRumah;
			c.w.generasi = genKepala + 1;
			if (ayah != null || ibu != null) {
				c.w.ayah = ayah == null ? Warga.TANPA : ayah.w.id;
				c.w.ibu = ibu == null ? Warga.TANPA : ibu.w.id;
			} else {
				c.w.ayah = kepala.w.pria() ? kepala.w.id : Warga.TANPA;
				c.w.ibu = kepala.w.pria() ? Warga.TANPA : kepala.w.id;
			}
		}
		if (kakek != null) {
			kakek.w.marga = puncak.w.marga;
			kakek.w.generasi = puncak.w.generasi - 1;
		}
		if (nenek != null) {
			nenek.w.marga = margaLain(puncak.w.marga, random);
			nenek.w.generasi = random.nextInt(8);
		}
		for (Calon c : lini) {
			c.w.ayah = kakek == null ? Warga.TANPA : kakek.w.id;
			c.w.ibu = nenek == null ? Warga.TANPA : nenek.w.id;
		}
		// given names: one set of siblings never repeats a second syllable and shares the family's zibei syllable
		List<Warga> sekandung = new ArrayList<>();
		for (Calon c : lini) {
			sekandung.add(c.w);
		}
		beriNama(sekandung, zibeiKeluarga, random);
		List<Warga> keturunan = new ArrayList<>();
		for (Calon c : anak) {
			keturunan.add(c.w);
		}
		beriNama(keturunan, zibeiKeluarga, random);
		for (Calon c : semua) {
			if (!sekandung.contains(c.w) && !keturunan.contains(c.w)) {
				c.w.nama = NamaWarga.acak(c.w.pria(), c == kakek && zibeiKeluarga, random, x -> false);
			}
		}
		if (suami != null && istri != null) {
			nikahkan(suami.w, istri.w, hari, Math.max(1, Math.min(suami.umur, istri.umur) - 20 - random.nextInt(4)), hpt);
		}
		if (kakek != null && nenek != null) {
			nikahkan(kakek.w, nenek.w, hari, Math.max(1, Math.min(kakek.umur, nenek.umur) - 22), hpt);
		}
		for (Calon parent : semua) {
			for (Calon child : semua) {
				if (child != parent && (child.w.ayah == parent.w.id || child.w.ibu == parent.w.id)) {
					parent.w.tambahAnak(child.w.id);
				}
			}
		}
		// looks, voices, temperaments, roots: elders first, then the line, the spouse, the children (they inherit)
		for (Calon c : tetua) {
			isi(c, null, null, random);
		}
		for (Calon c : lini) {
			isi(c, kakek == null ? null : kakek.w, nenek == null ? null : nenek.w, random);
		}
		for (Calon c : kerja) {
			if (!lini.contains(c)) {
				isi(c, null, null, random);
			}
		}
		for (Calon c : anak) {
			isi(c, find(semua, c.w.ayah), find(semua, c.w.ibu), random);
		}
		List<Calon> out = new ArrayList<>(semua);
		// a baby without a slot for a young couple now and then (carried by its mother, DESIGN 42.4.3)
		if (suami != null && istri != null && istri.umur >= 19 && istri.umur <= 34 && random.nextInt(100) < 18) {
			Warga bayi = bayi(data, suami.w, istri.w, hari - random.nextInt((int) (2 * hpt) + 1), random);
			out.add(new Calon(istri.p, bayi));
		}
		return out;
	}

	private static @Nullable Warga find(List<Calon> list, int id) {
		if (id == Warga.TANPA) {
			return null;
		}
		for (Calon c : list) {
			if (c.w.id == id) {
				return c.w;
			}
		}
		return null;
	}

	@SafeVarargs
	private static List<Calon> all(List<Calon>... lists) {
		List<Calon> out = new ArrayList<>();
		for (List<Calon> l : lists) {
			out.addAll(l);
		}
		return out;
	}

	/** Married {@code tahun} Fana Years ago: spouses, the PASANGAN bond, the wedding day. */
	private static void nikahkan(Warga pria, Warga wanita, long hari, int tahun, long hpt) {
		pria.pasangan = wanita.id;
		wanita.pasangan = pria.id;
		long sejak = Math.max(0L, hari - tahun * hpt);
		pria.menikahHari = sejak;
		wanita.menikahHari = sejak;
		Hubungan.ikat(pria, wanita, JenisHubungan.PASANGAN, 60 + Math.floorMod(pria.id * 31 + wanita.id, 35), sejak);
	}

	/** Gives a set of siblings given names: all with the family's zibei syllable, or free ones, never one second syllable twice. */
	private static void beriNama(List<Warga> sekandung, boolean zibei, RandomSource random) {
		List<Integer> dipakai = new ArrayList<>();
		for (Warga w : sekandung) {
			w.nama = NamaWarga.acak(w.pria(), zibei, random, dipakai::contains);
			dipakai.add(w.nama & 0x7F);
		}
	}

	/** The look, voice, temperament, root, realm, skill and health of a member (parents may be null). */
	private static void isi(Calon c, @Nullable Warga ayah, @Nullable Warga ibu, RandomSource random) {
		Warga w = c.w;
		NpcRole role = c.p.peran();
		w.look = ayah != null && ibu != null ? lookAnak(ayah.look, ibu.look, role, w.kelamin, random) : lookUntuk(role, w.kelamin, random);
		w.suara = (w.pria() ? 0 : 3) + random.nextInt(3);
		Warga parent = random.nextBoolean() ? ayah : ibu;
		w.watak = parent != null && random.nextBoolean() ? parent.watak : random.nextInt(8);
		boolean akarOrtu = ayah != null && ayah.akar >= 2 || ibu != null && ibu.akar >= 2;
		w.akar = akarLahir(random, akarOrtu);
		int realm = HidupApi.realmFor(role, w.look);
		w.ranah = role.tier >= 2 ? realm : w.akar > 0 && c.umur >= 12 ? Math.min(1, realm + 1) : 0;
		w.ahli = role.kelompok == NpcRole.Kelompok.ANAK ? 0 : Mth.clamp((c.umur - 16) * 3 + 10 + random.nextInt(20), 10, 95);
		int sehat = c.umur >= 60 ? 55 + random.nextInt(36) : 72 + random.nextInt(29);
		w.kesehatan = Mth.clamp(sehat, 0, 100);
	}

	/** Age by role for the first raising (DESIGN 42.4.13). */
	static int umurPeran(NpcRole role, RandomSource random) {
		return switch (role.kelompok) {
			case ANAK -> 3 + random.nextInt(10);
			case TETUA -> role == NpcRole.KEPALA_DESA || role == NpcRole.MAK_COMBLANG ? 45 + random.nextInt(26) : 60 + random.nextInt(21);
			default -> 18 + random.nextInt(43);
		};
	}

	/** Another family name than {@code bukan} (a spouse from another family). */
	private static int margaLain(int bukan, RandomSource random) {
		for (int i = 0; i < 8; i++) {
			int m = NamaWarga.margaAcak(random);
			if (m != bukan) {
				return m;
			}
		}
		return Math.floorMod(bukan + 1 + random.nextInt(NamaWarga.MARGA - 1), NamaWarga.MARGA);
	}

	// ================================================================== births and newcomers

	/**
	 * A baby of {@code ibu} (and {@code ayah}, may be null) born on world day {@code hari}: the father's family name and
	 * next generation (the mother's when there is no father), a name that repeats no sibling's second syllable, a look that
	 * mixes both parents, the temperament of a parent half the time, the root lottery (twice the chance when a parent is
	 * rooted). No slot, the mother's household. The caller adds it to the data and fires {@code KotaBus.LAHIR}.
	 */
	public static Warga bayi(WargaData data, @Nullable Warga ayah, Warga ibu, long hari, RandomSource random) {
		Warga b = new Warga(data.idBaru());
		KotaConfig.Warga cfg = KotaConfig.warga();
		b.kelamin = random.nextDouble() < cfg.rasioPerempuan ? Warga.WANITA : Warga.PRIA;
		Warga keluarga = ayah != null ? ayah : ibu;
		b.marga = keluarga.marga;
		b.generasi = keluarga.generasi + 1;
		b.ayah = ayah == null ? Warga.TANPA : ayah.id;
		b.ibu = ibu.id;
		b.lahir = hari * 10L;
		b.rumahTangga = ibu.rumahTangga;
		b.rumah = ibu.rumah;
		b.peran = NpcRole.ANAK.id();
		List<Integer> dipakai = new ArrayList<>();
		boolean zibei = false;
		for (int c : ibu.anak) {
			Warga sib = data.byId(c);
			if (sib != null) {
				dipakai.add(sib.nama & 0x7F);
				zibei |= (sib.nama & NamaWarga.ZIBEI) != 0;
			}
		}
		if (ibu.anak.length == 0) {
			zibei = random.nextInt(100) < 75;
		}
		b.nama = NamaWarga.acak(b.pria(), zibei, random, dipakai::contains);
		b.look = ayah != null ? lookAnak(ayah.look, ibu.look, NpcRole.ANAK, b.kelamin, random) : lookUntuk(NpcRole.ANAK, b.kelamin, random);
		b.suara = (b.pria() ? 0 : 3) + random.nextInt(3);
		Warga parent = ayah != null && random.nextBoolean() ? ayah : ibu;
		b.watak = random.nextBoolean() ? parent.watak : random.nextInt(8);
		b.akar = akarLahir(random, ibu.akar >= 2 || ayah != null && ayah.akar >= 2);
		b.kesehatan = 80 + random.nextInt(16);
		ibu.tambahAnak(b.id);
		if (ayah != null) {
			ayah.tambahAnak(b.id);
		}
		return b;
	}

	/**
	 * A newcomer adult (DESIGN 42.4.6/42.4.8: arrivals when a place shrinks, movers from another town) of {@code peran},
	 * aged {@code umurMin}-{@code umurMaks}, arriving on {@code hari}. No slot and no household yet: the caller (42A-2
	 * {@code Migrasi}/{@code Pekerjaan}) gives it a slot and adds it to the data.
	 */
	public static Warga pendatang(WargaData data, NpcRole peran, int umurMin, int umurMaks, long hari, RandomSource random) {
		Warga w = new Warga(data.idBaru());
		KotaConfig.Warga cfg = KotaConfig.warga();
		w.kelamin = kelaminUntuk(peran, random.nextDouble() < cfg.rasioPerempuan ? Warga.WANITA : Warga.PRIA, random);
		int umur = umurMin + random.nextInt(Math.max(1, umurMaks - umurMin + 1));
		w.lahir = hari * 10L - (umur * 10L * cfg.hariPerTahun + random.nextInt(10 * cfg.hariPerTahun));
		w.marga = NamaWarga.margaAcak(random);
		w.generasi = random.nextInt(8);
		w.nama = NamaWarga.acak(w.pria(), random.nextInt(100) < 60, random, x -> false);
		w.peran = peran.id();
		w.look = lookUntuk(peran, w.kelamin, random);
		w.suara = (w.pria() ? 0 : 3) + random.nextInt(3);
		w.watak = random.nextInt(8);
		w.akar = akarLahir(random, false);
		w.ranah = peran.tier >= 2 ? HidupApi.realmFor(peran, w.look) : 0;
		w.ahli = Mth.clamp((umur - 16) * 3 + random.nextInt(20), 10, 90);
		w.kesehatan = 70 + random.nextInt(25);
		w.datang = hari;
		return w;
	}

	// ================================================================== small rules

	/** The sex a role forces on its body ({@code Sosok.LAKI/PEREMPUAN} = {@link Warga#PRIA}/{@link Warga#WANITA}), or -1. */
	public static int kelaminWajib(NpcRole peran) {
		return SosokGen.kebijakan(peran).kelaminWajib();
	}

	/** {@code mau} unless the role forces the other sex. */
	public static byte kelaminUntuk(NpcRole peran, byte mau, RandomSource random) {
		int wajib = kelaminWajib(peran);
		return wajib < 0 ? mau : wajib == Sosok.PEREMPUAN ? Warga.WANITA : Warga.PRIA;
	}

	/** A look seed whose body ({@link SosokGen#kelamin}) has sex {@code kelamin} for this role (when the role allows it). */
	public static int lookUntuk(NpcRole peran, int kelamin, RandomSource random) {
		int look = random.nextInt();
		for (int i = 0; i < 64 && SosokGen.kelamin(peran, look) != kelamin; i++) {
			look = random.nextInt();
		}
		return look;
	}

	/** A child's look: every bit from the father or the mother, then the sex bits (24-30) re-rolled to fit {@code kelamin}. */
	public static int lookAnak(int ayah, int ibu, NpcRole peran, int kelamin, RandomSource random) {
		int mask = random.nextInt();
		int look = ayah & mask | ibu & ~mask;
		for (int i = 0; i < 64 && SosokGen.kelamin(peran, look) != kelamin; i++) {
			look = look & ~(0x7F << 24) | random.nextInt(128) << 24;
		}
		return look;
	}

	/** The spirit root lottery at birth: 0 (mortal) or 1-5 ({@code RootGrade} ordinal + 1). */
	public static int akarLahir(RandomSource random, boolean orangTuaBerakar) {
		double peluang = KotaConfig.warga().peluangAkar * (orangTuaBerakar ? 2.0D : 1.0D);
		double roll = random.nextDouble();
		if (roll >= peluang) {
			return 0;
		}
		double pick = roll / peluang * AKAR_TOTAL;
		for (int i = 0; i < AKAR.length; i++) {
			pick -= AKAR[i];
			if (pick < 0.0D) {
				return i + 1;
			}
		}
		return AKAR.length;
	}

	private WargaGen() {
	}
}
