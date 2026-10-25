package dev.kultivator.dunia.kota42.acara;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.KotaGerakId;
import dev.kultivator.dunia.kota42.api.PenimpaJadwalKota;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.api.WargaInfo;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Jadwal;
import dev.kultivator.dunia.tempat.hidup.Kegiatan;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.Penduduk;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.cerita.CeritaData;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seasons and festivals of the towns (DESIGN 42.7.6; season {@code (day / 24) % 4}, 38.N.3). The nine festivals of the
 * year fall on fixed days of their season ({@link #hariRaya}); on that day:
 * <ul>
 * <li>the residents live the festival ({@link Penimpa}, order 110: couplets and red envelopes, the lion dance crowd and the
 * family feast at New Year; willow twigs, sweeping the graves and kites at Qingming; the river crowd of the Dragon Boats;
 * needlework and lovers under the stars at Qixi; the harvest market and dances; mooncakes, moon gazing and rabbit lanterns
 * at Mid-Autumn; the elders climbing at Chongyang; the temple's free Laba porridge; the winter dumplings);</li>
 * <li>festival tents go up in the square ({@link TendaPasarEntity}), the bodies wear their festive clothes, players in town
 * are greeted with the festival's food once ({@code kue_keranjang}, {@code air_plum}, {@code tanghulu}, {@code kue_bulan},
 * {@code bubur_laba});</li>
 * <li>New Year: the lion dance parades the streets twice ({@link BarongsaiEntity}, two dancers, two drummers, firecrackers);</li>
 * <li>prices follow the festival ({@link HargaKota#daftarAcara}) and the calendar lists it.</li>
 * </ul>
 * Winter: Nenek Tang's soup stall is open (a staff body when the character does not live in the place; a free bowl of
 * {@code sup_hangat} a day, buff {@code Hangat}). The visuals (firecrackers, couplets, petals, falling leaves, snow only in
 * snowy biomes) are 42V's, driven by {@code kota_suasana}. {@code uji kota_hidup acara musim} previews a season and festival
 * for one place without touching time or weather.
 */
public final class MusimKota {

	public static final int HR_TIDAK = 0;
	public static final int HR_TAHUN_BARU = 1;
	public static final int HR_QINGMING = 2;
	public static final int HR_PERAHU_NAGA = 3;
	public static final int HR_QIXI = 4;
	public static final int HR_PANEN = 5;
	public static final int HR_PERTENGAHAN_GUGUR = 6;
	public static final int HR_CHONGYANG = 7;
	public static final int HR_LABA = 8;
	public static final int HR_TITIK_BALIK = 9;
	/** Ids in {@code HR_*} order (lang {@code kultivator.kota42.acara.hari_raya.<id>}). */
	public static final String[] ID = {"", "tahun_baru", "qingming", "perahu_naga", "qixi", "panen", "pertengahan_gugur", "chongyang",
			"laba", "titik_balik"};
	/** Season of each festival. */
	private static final int[] MUSIM = {-1, Jadwal.SEMI, Jadwal.SEMI, Jadwal.PANAS, Jadwal.PANAS, Jadwal.GUGUR, Jadwal.GUGUR, Jadwal.GUGUR,
			Jadwal.DINGIN, Jadwal.DINGIN};
	/** Day inside the season (42.7.6 table). */
	private static final int[] HARI = {-1, 0, 4, 5, 15, 6, 14, 18, 8, 21};
	/** Main hours for the calendar: start, length. */
	private static final int[][] JAM = {{0, 0}, {8, 14}, {7, 4}, {9, 4}, {18, 4}, {17, 5}, {18, 5}, {8, 4}, {7, 4}, {16, 3}};
	/** Tent stripe colours: New Year red, Qixi blue, harvest ochre, Mid-Autumn gold. */
	private static final int[] WARNA = {0, 0xC0262D, 0x6E7B5A, 0x2F7A6A, 0x3A5BA8, 0xC78A1E, 0xE0B040, 0x8A5A9A, 0x9A6A3A, 0xB0413E};

	/** Festival previews of {@code uji ... musim}: place -> {season, festival, until game time}. */
	private static final Map<Long, long[]> PRATINJAU = new HashMap<>();
	/** New Year lion dances running: place -> the parade. */
	private static final Map<Long, Arak> ARAK = new HashMap<>();
	/** Soup stall bodies (winter): place -> body. */
	private static final Map<Long, KultivatorNpc> SUP = new HashMap<>();
	private static final TendaPasarEntity.Pengelola TENDA = new TendaPasarEntity.Pengelola();
	/** Parade routes per place, built once a day. */
	private static final Map<Long, List<Vec3>> RUTE = new HashMap<>();
	private static final Map<Long, Long> RUTE_HARI = new HashMap<>();
	/** Gravestone spots per place (Qingming), rebuilt with the routes. */
	private static final Map<Long, List<BlockPos>> NISAN = new HashMap<>();

	private static final class Arak {
		final long situs;
		final BarongsaiEntity singa;
		@Nullable KultivatorNpc kepala;
		@Nullable KultivatorNpc ekor;
		final List<KultivatorNpc> pemusik = new ArrayList<>();
		long sampai;

		Arak(long situs, BarongsaiEntity singa) {
			this.situs = situs;
			this.singa = singa;
		}
	}

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		WargaApi.daftarPenimpa(new Penimpa());
		HargaKota.daftarAcara(MusimKota::harga);
		KalenderKota.daftarSumber(MusimKota::kalender);
		ServerTickEvents.END_SERVER_TICK.register(MusimKota::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> lupakan());
	}

	private static void lupakan() {
		for (Arak a : ARAK.values()) {
			bubar(a);
		}
		ARAK.clear();
		for (KultivatorNpc npc : SUP.values()) {
			npc.vanish();
		}
		SUP.clear();
		TENDA.hapusSemua();
		PRATINJAU.clear();
		RUTE.clear();
		RUTE_HARI.clear();
		NISAN.clear();
	}

	// ================================================================== the year

	/** The festival of world day {@code hari} ({@code HR_*}), or {@link #HR_TIDAK}. */
	public static int hariRaya(long hari) {
		int musim = KalenderKota.musim(hari);
		int d = KalenderKota.hariMusim(hari);
		for (int i = 1; i < ID.length; i++) {
			if (MUSIM[i] == musim && HARI[i] == d) {
				return i == HR_TAHUN_BARU && !KotaConfig.acara().tahunBaru ? HR_TIDAK : i;
			}
		}
		return HR_TIDAK;
	}

	/** The festival as place {@code situs} shows it (a test preview wins), or {@link #HR_TIDAK} with the switch off. */
	public static int hariRaya(long situs, long hari) {
		long[] p = pratinjau(situs);
		if (p != null) {
			return (int) p[1];
		}
		return KotaConfig.acara().musim ? hariRaya(hari) : HR_TIDAK;
	}

	/** The season as place {@code situs} shows it (a test preview wins). */
	public static int musim(long situs, long hari) {
		long[] p = pratinjau(situs);
		return p != null ? (int) p[0] : KalenderKota.musim(hari);
	}

	private static long @Nullable [] pratinjau(long situs) {
		long[] p = PRATINJAU.get(situs);
		MinecraftServer server = KotaHidup.server();
		if (p == null || server == null) {
			return null;
		}
		if (server.overworld().getGameTime() >= p[2]) {
			PRATINJAU.remove(situs);
			return null;
		}
		return p;
	}

	/** The festival's name. */
	public static Component nama(int hr) {
		return Component.translatable("kultivator.kota42.acara.hari_raya." + ID[Math.max(0, Math.min(ID.length - 1, hr))]);
	}

	/** The next world day (from {@code dari}, inclusive) of festival {@code hr}. */
	public static long berikut(int hr, long dari) {
		if (hr <= 0 || hr >= ID.length) {
			return -1L;
		}
		long year = Jadwal.HARI_MUSIM * 4L;
		long start = Math.floorDiv(dari, year) * year + MUSIM[hr] * (long) Jadwal.HARI_MUSIM + HARI[hr];
		return start >= dari ? start : start + year;
	}

	private static void kalender(MinecraftServer server, long situs, long dari, long sampai, List<KalenderKota.Entri> out) {
		if (!KotaConfig.acara().musim) {
			return;
		}
		for (long d = dari; d <= sampai; d++) {
			int hr = hariRaya(d);
			if (hr != HR_TIDAK) {
				out.add(new KalenderKota.Entri("hr:" + ID[hr], KotaLembar.Buku.AC_HARI_RAYA, nama(hr), d, JAM[hr][0], 0, JAM[hr][1], null));
			}
		}
	}

	/** Festival price multipliers (42.6.3 "acara"): busy feast days raise food, luxuries and cloth. */
	private static HargaKota.@Nullable Pengali harga(MinecraftServer server, long situs, Kategori k) {
		if (!KotaConfig.acara().aktif) {
			return null;
		}
		int hr = hariRaya(situs, KalenderKota.hari(server));
		double kali = switch (hr) {
			case HR_TAHUN_BARU -> k == Kategori.PANGAN ? 1.15D : k == Kategori.MEWAH ? 1.25D : k == Kategori.KAIN ? 1.10D : 1.0D;
			case HR_QINGMING -> k == Kategori.KAIN ? 1.05D : 1.0D;
			case HR_PERAHU_NAGA, HR_TITIK_BALIK -> k == Kategori.PANGAN ? 1.10D : 1.0D;
			case HR_QIXI -> k == Kategori.MEWAH ? 1.15D : k == Kategori.KAIN ? 1.05D : 1.0D;
			case HR_PANEN -> k == Kategori.PANGAN ? 0.90D : 1.0D;
			case HR_PERTENGAHAN_GUGUR -> k == Kategori.PANGAN ? 1.15D : k == Kategori.MEWAH ? 1.10D : 1.0D;
			case HR_LABA -> k == Kategori.PANGAN ? 1.05D : 1.0D;
			default -> 1.0D;
		};
		return kali == 1.0D ? null : new HargaKota.Pengali(kali, Component.translatable("kultivator.kota42.acara.harga.hari_raya", nama(hr)));
	}

	// ================================================================== the residents' festival

	/** Order 110: after ceremonies (a wedding guest stays at the wedding), before named characters and habits. */
	static final class Penimpa implements PenimpaJadwalKota {

		@Override
		public int urutan() {
			return 110;
		}

		@Override
		public @Nullable Kegiatan timpa(TempatHuni huni, Warga w, Penduduk p, long waktu, int jam, long now) {
			if (!KotaConfig.acara().aktif || !KotaConfig.acara().musim || !KalenderKota.kota(huni)) {
				return null;
			}
			Jadwal.Saat s = Jadwal.saat(p, huni.jenis, waktu);
			int hr = hariRaya(huni.situsId, s.hari());
			if (hr == HR_TIDAK) {
				return null;
			}
			NpcRole.Kelompok g = p.peran().kelompok;
			if (g == NpcRole.Kelompok.PENJAGA || g == NpcRole.Kelompok.KHUSUS || g == NpcRole.Kelompok.KULTIVATOR) {
				return null;
			}
			WargaInfo.Tahap tahap = KalenderKota.tahap(w);
			boolean anak = tahap == WargaInfo.Tahap.ANAK || tahap == WargaInfo.Tahap.REMAJA;
			boolean bayi = tahap == WargaInfo.Tahap.BAYI;
			boolean tua = tahap == WargaInfo.Tahap.TUA || tahap == WargaInfo.Tahap.PENSIUN;
			boolean dagang = g == NpcRole.Kelompok.PEDAGANG;
			boolean hujan = huni.cuaca().hujan();
			double h = s.jam() + s.menit() / 60.0D;
			if (bayi) {
				return null;
			}
			int pick = Math.floorMod(w.id * 31 + s.menit() / 10, 6);
			switch (hr) {
				case HR_TAHUN_BARU -> {
					if (KalenderKota.antara(h, 6.0D, 8.0D) && !anak && !dagang) {
						return Kegiatan.di("hr_kuplet", Kegiatan.RUMAH, KotaGerakId.TEMPEL_KUPLET, KotaGerakId.PROP_KUPLET_MERAH);
					}
					if (KalenderKota.antara(h, 8.0D, 10.0D)) {
						if (anak) {
							return pick < 3 ? Kegiatan.di("hr_angpao", Kegiatan.RUMAH, KotaGerakId.TERIMA_ANGPAO, KotaGerakId.PROP_ANGPAO)
									: Kegiatan.di("hr_main", "obrol", GerakId.MAIN_KEJAR, 0);
						}
						if (tua) {
							return Kegiatan.di("hr_terima_tamu", Kegiatan.RUMAH, GerakId.DUDUK_KURSI, 0);
						}
						if (!dagang) {
							return Kegiatan.di("hr_bertamu", Kegiatan.JALAN + (w.id % 7), KotaGerakId.BERI_ANGPAO, KotaGerakId.PROP_ANGPAO)
									.berjalan(GerakId.JALAN_SANTAI);
						}
					}
					if (KalenderKota.antara(h, 10.0D, 12.0D) || KalenderKota.antara(h, 15.0D, 16.5D)) {
						Vec3 at = posisiArak(huni.situsId);
						if (at != null && !dagang && !hujan) {
							double a = (w.id * 0.618D % 1.0D) * Math.PI * 2.0D;
							double r = anak ? 3.0D : 5.5D + (w.id % 3);
							String xz = Kegiatan.XZ + (int) Math.floor(at.x + Math.cos(a) * r) + "," + (int) Math.floor(at.z + Math.sin(a) * r);
							int klip = anak ? GerakId.MAIN_KEJAR : pick % 2 == 0 ? GerakId.TEPUK_TANGAN : GerakId.SORAK_ARENA;
							return new Kegiatan("hr_barongsai", xz, klip, 0, 0, 0, 0, GerakId.JALAN_TERGESA, false);
						}
					}
					if (KalenderKota.antara(h, 12.0D, 14.0D)) {
						return new Kegiatan("hr_makan_besar", "meja_makan", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0, false);
					}
					if (KalenderKota.antara(h, 17.0D, 22.0D) && !hujan) {
						if (anak) {
							return Kegiatan.di("hr_lentera", Kegiatan.JALAN + (w.id % 9), GerakId.MAIN_LENTERA, PropId.LENTERA_TANGAN);
						}
						if (dagang) {
							return new Kegiatan("hr_dagang", Kegiatan.KERJA, GerakId.SERU_DAGANG, 0, PropId.LENTERA_TANGAN, 0, 0, 0, false);
						}
						return Kegiatan.di("hr_obrol", "obrol", pick < 2 ? GerakId.TERTAWA : KotaGerakId.UCAP_SELAMAT, 0);
					}
					return null;
				}
				case HR_QINGMING -> {
					if (KalenderKota.antara(h, 6.0D, 7.0D) && !anak) {
						return Kegiatan.di("hr_dedalu", Kegiatan.RUMAH, KotaGerakId.TANCAP_DEDALU, KotaGerakId.PROP_RANTING_DEDALU);
					}
					if (KalenderKota.antara(h, 7.0D, 11.0D) && !hujan) {
						BlockPos grave = nisanUntuk(huni, w);
						if (grave != null) {
							String xz = Kegiatan.XZ + (grave.getX() + (w.id % 3) - 1) + "," + (grave.getZ() + 1 + (w.id % 2));
							if (anak) {
								return new Kegiatan("hr_makam", xz, GerakId.HORMAT, 0, 0, 0, 0, 0, false);
							}
							if (tua) {
								return new Kegiatan("hr_makam", xz, GerakId.SUJUD_DUPA, 0, PropId.DUPA, 0, 0, GerakId.JALAN_TUA, false);
							}
							boolean sapu = (s.menit() / 20 + w.id) % 2 == 0;
							return new Kegiatan("hr_makam", xz, sapu ? KotaGerakId.SAPU_MAKAM : KotaGerakId.BAKAR_UANG_KERTAS, 0,
									sapu ? KotaGerakId.PROP_SAPU_LIDI : KotaGerakId.PROP_UANG_KERTAS, 0, 0, 0, false);
						}
					}
					if (KalenderKota.antara(h, 14.0D, 17.0D) && !hujan && (anak || tahap == WargaInfo.Tahap.MUDA)) {
						return Kegiatan.di("hr_layang", Kegiatan.JALAN + (w.id % 5), KotaGerakId.LAYANG_NAGA,
								w.id % 2 == 0 ? KotaGerakId.PROP_LAYANG_NAGA : KotaGerakId.PROP_LAYANG_KUPU);
					}
					return null;
				}
				case HR_PERAHU_NAGA -> {
					if (KalenderKota.antara(h, 9.0D, 13.0D) && !dagang && !hujan) {
						String to = huni.titik("pancing").isEmpty() ? "obrol" : "pancing";
						return Kegiatan.di("hr_perahu_naga", to, anak ? GerakId.MAIN_KEJAR : pick < 3 ? GerakId.SORAK_ARENA : GerakId.TUNJUK, 0);
					}
					if (KalenderKota.antara(h, 17.0D, 19.0D)) {
						return new Kegiatan("hr_makan_zongzi", "meja_makan", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0, false);
					}
					return null;
				}
				case HR_QIXI -> {
					if (KalenderKota.antara(h, 18.0D, 20.0D) && !w.pria() && (tahap == WargaInfo.Tahap.MUDA || tahap == WargaInfo.Tahap.REMAJA)) {
						return Kegiatan.di("hr_sulam", Kegiatan.RUMAH, KotaGerakId.MENYULAM, KotaGerakId.PROP_BINGKAI_SULAM);
					}
					if (KalenderKota.antara(h, 20.0D, 23.0D) && !hujan && w.pasangan != Warga.TANPA && !anak) {
						// couples walk out together and look up at the Weaver and the Herdsman
						int pair = Math.min(w.id, w.pasangan);
						return Kegiatan.di("hr_bintang", Kegiatan.JALAN + (pair % 6), GerakId.TATAP_LANGIT, 0);
					}
					return null;
				}
				case HR_PANEN -> {
					if (KalenderKota.antara(h, 9.0D, 12.0D)) {
						if (dagang) {
							return new Kegiatan("hr_pasar_panen", Kegiatan.KERJA, GerakId.SERU_DAGANG, 0, PropId.KERANJANG, 0, 0, 0, false);
						}
						if (!anak && pick < 3) {
							return Kegiatan.di("hr_belanja", Kegiatan.DEPAN_LAPAK + (w.id % 11), GerakId.BICARA_TANGAN, PropId.KERANJANG);
						}
					}
					if (KalenderKota.antara(h, 17.0D, 22.0D) && !hujan) {
						BlockPos alun = KalenderKota.alun(huni);
						double a = (w.id * 0.618D % 1.0D) * Math.PI * 2.0D;
						String xz = Kegiatan.XZ + (int) (alun.getX() + Math.cos(a) * 6.0D) + "," + (int) (alun.getZ() + Math.sin(a) * 6.0D);
						return new Kegiatan("hr_tari_panen", xz, anak ? GerakId.MAIN_KEJAR : GerakId.MENARI_PANEN, 0, 0, 0, 0, 0, false);
					}
					return null;
				}
				case HR_PERTENGAHAN_GUGUR -> {
					if (KalenderKota.antara(h, 18.0D, 23.0D) && !hujan) {
						if (anak) {
							return Kegiatan.di("hr_lentera_kelinci", Kegiatan.JALAN + (w.id % 9), KotaGerakId.ANGKAT_LENTERA_KELINCI,
									KotaGerakId.PROP_LENTERA_KELINCI);
						}
						BlockPos alun = KalenderKota.alun(huni);
						double a = (w.rumahTangga * 0.618D % 1.0D) * Math.PI * 2.0D;
						String xz = Kegiatan.XZ + (int) (alun.getX() + Math.cos(a) * 8.0D) + "," + (int) (alun.getZ() + Math.sin(a) * 8.0D);
						return new Kegiatan("hr_tatap_bulan", xz, KotaGerakId.TATAP_BULAN, 0, KotaGerakId.PROP_KUE_BULAN, 0, 0, 0, false);
					}
					return null;
				}
				case HR_CHONGYANG -> {
					if (KalenderKota.antara(h, 8.0D, 12.0D) && tua && !hujan) {
						String to = !huni.titik("dupa").isEmpty() ? "dupa" : "obrol";
						return new Kegiatan("hr_naik_bukit", to, GerakId.TATAP_LANGIT, 0, 0, 0, 0, GerakId.JALAN_TUA, false);
					}
					return null;
				}
				case HR_LABA -> {
					if (KalenderKota.antara(h, 7.0D, 11.0D)) {
						if (g == NpcRole.Kelompok.RAHIB) {
							return Kegiatan.di("hr_bagi_bubur", huni.titik("dupa").isEmpty() ? Kegiatan.KERJA : "dupa", KotaGerakId.BAGI_BUBUR,
									KotaGerakId.PROP_MANGKUK_BUBUR);
						}
						if (!dagang) {
							String to = huni.titik("dupa").isEmpty() ? "obrol" : "dupa";
							return KalenderKota.antara(h, 7.0D, 9.0D)
									? Kegiatan.di("hr_antre_bubur", to, KotaGerakId.ANTRE, 0)
									: new Kegiatan("hr_makan_bubur", "obrol", GerakId.MAKAN, 0, KotaGerakId.PROP_MANGKUK_BUBUR, 0, 0, 0, false);
						}
					}
					return null;
				}
				case HR_TITIK_BALIK -> {
					return KalenderKota.antara(h, 16.0D, 19.0D)
							? new Kegiatan("hr_pangsit", "meja_makan", GerakId.MAKAN, 0, PropId.SUMPIT, PropId.MANGKUK, 0, 0, false)
							: null;
				}
				default -> {
					return null;
				}
			}
		}
	}

	/** The gravestone a household visits at Qingming (its own when 42A gave one, else by household). */
	private static @Nullable BlockPos nisanUntuk(TempatHuni huni, Warga w) {
		List<BlockPos> list = NISAN.get(huni.situsId);
		if (list == null || list.isEmpty()) {
			return null;
		}
		return list.get(Math.floorMod(w.rumahTangga * 7 + 3, list.size()));
	}

	/** Where the New Year lion is now in place {@code situs}, or null. */
	public static @Nullable Vec3 posisiArak(long situs) {
		Arak a = ARAK.get(situs);
		return a == null || a.singa.isRemoved() ? null : a.singa.position();
	}

	// ================================================================== tick

	private static void tick(MinecraftServer server) {
		for (Arak a : ARAK.values()) {
			if (!a.singa.isRemoved()) {
				a.singa.bawaPenari(a.kepala, a.ekor);
				pemusik(a);
			}
		}
		if (server.getTickCount() % 20L != 11L) {
			return;
		}
		ServerLevel level = server.overworld();
		KotaConfig.Acara c = KotaConfig.acara();
		long day = KalenderKota.hari(server);
		double jam = KalenderKota.jamDesimal(server);
		Set<Long> live = new HashSet<>();
		if (c.aktif && c.musim) {
			for (TempatHuni huni : KalenderKota.aktif(level)) {
				if (!KalenderKota.kota(huni)) {
					continue;
				}
				live.add(huni.situsId);
				siapkanRute(level, huni, day);
				int hr = hariRaya(huni.situsId, day);
				if (hr != HR_TIDAK) {
					hariRayaTick(level, huni, hr, jam);
				}
				supTick(level, huni, day, jam);
			}
		}
		TENDA.rapikan();
		Iterator<Map.Entry<Long, Arak>> it = ARAK.entrySet().iterator();
		while (it.hasNext()) {
			Arak a = it.next().getValue();
			boolean keep = live.contains(a.situs) && level.getGameTime() < a.sampai && !a.singa.isRemoved()
					&& KalenderKota.adaPemain(level, a.singa.blockPosition(), c.jarakTampil);
			if (!keep) {
				bubar(a);
				it.remove();
			}
		}
		SUP.entrySet().removeIf(e -> {
			boolean gone = e.getValue().isRemoved() || !live.contains(e.getKey());
			if (gone && !e.getValue().isRemoved()) {
				e.getValue().vanish();
			}
			return gone;
		});
	}

	private static void hariRayaTick(ServerLevel level, TempatHuni huni, int hr, double jam) {
		BlockPos alun = KalenderKota.alun(huni);
		List<ServerPlayer> near = KalenderKota.penonton(level, alun);
		if (near.isEmpty()) {
			return;
		}
		// greetings and the festival's food, once a day per player
		for (ServerPlayer p : near) {
			if (KalenderKota.Berkah.sekaliSehari(p, "hr_" + ID[hr])) {
				Msg.title(p, nama(hr).copy().withColor(0xF0C060), Component.translatable("kultivator.kota42.acara.hari_raya." + ID[hr] + ".sub")
						.withColor(0xF8E8C8), 10, 60, 20);
				Item food = makanan(hr);
				if (food != null) {
					Barang.beri(p, new ItemStack(food, hr == HR_TAHUN_BARU || hr == HR_PERTENGAHAN_GUGUR ? 2 : 1));
					Msg.chat(p, Component.translatable("kultivator.kota42.acara.hari_raya.hadiah", nama(hr), Component.translatable(food.getDescriptionId()))
							.withStyle(ChatFormatting.GOLD));
				}
			}
		}
		// festive clothes on the bodies of the day
		boolean pesta = jam >= 8.0D && jam < 23.0D;
		for (Penduduk p : huni.penduduk()) {
			KultivatorNpc body = huni.tubuhDari(p.id());
			if (body == null || p.peran().kelompok == NpcRole.Kelompok.PENJAGA) {
				continue;
			}
			int now = SosokApi.gaya(body).busanaKhusus();
			if (pesta && now == Gaya.BUSANA_OTOMATIS) {
				SosokApi.setBusanaKhusus(body, Gaya.BUSANA_PESTA);
			} else if (!pesta && now == Gaya.BUSANA_PESTA) {
				SosokApi.setBusanaKhusus(body, Gaya.BUSANA_OTOMATIS);
			}
		}
		// tents in the square during the festival's busy hours
		boolean tendaJam = switch (hr) {
			case HR_TAHUN_BARU -> jam >= 9.0D && jam < 22.0D;
			case HR_PANEN -> jam >= 9.0D && jam < 22.0D;
			case HR_PERTENGAHAN_GUGUR, HR_QIXI -> jam >= 17.0D && jam < 23.0D;
			case HR_PERAHU_NAGA -> jam >= 9.0D && jam < 14.0D;
			default -> false;
		};
		if (tendaJam) {
			int[] varian = switch (hr) {
				case HR_TAHUN_BARU -> new int[] {TendaPasarEntity.VARIAN_KERTAS_MERAH, TendaPasarEntity.VARIAN_JAJANAN, TendaPasarEntity.VARIAN_LENTERA,
						TendaPasarEntity.VARIAN_MAINAN, TendaPasarEntity.VARIAN_PERMAINAN};
				case HR_PERTENGAHAN_GUGUR -> new int[] {TendaPasarEntity.VARIAN_LENTERA, TendaPasarEntity.VARIAN_JAJANAN, TendaPasarEntity.VARIAN_LENTERA,
						TendaPasarEntity.VARIAN_PERMAINAN};
				case HR_QIXI -> new int[] {TendaPasarEntity.VARIAN_KERTAS_MERAH, TendaPasarEntity.VARIAN_JAJANAN, TendaPasarEntity.VARIAN_MAINAN};
				default -> new int[] {TendaPasarEntity.VARIAN_JAJANAN, TendaPasarEntity.VARIAN_MAINAN, TendaPasarEntity.VARIAN_PERMAINAN,
						TendaPasarEntity.VARIAN_JAJANAN};
			};
			for (int i = 0; i < varian.length; i++) {
				double a = Math.PI * 2.0D * i / varian.length + (huni.situsId & 7) * 0.3D;
				BlockPos at = tanah(level, alun.offset((int) Math.round(Math.cos(a) * 9.0D), 0, (int) Math.round(Math.sin(a) * 9.0D)));
				float yaw = (float) Math.toDegrees(Math.atan2(alun.getZ() - at.getZ(), alun.getX() - at.getX())) - 90.0F;
				TENDA.pastikan(level, huni.situsId, huni.situsId + ":hr:" + i, at, yaw, varian[i], WARNA[hr]);
			}
		}
		// the New Year lion dance: 10:00 and 15:00
		if (hr == HR_TAHUN_BARU && KotaConfig.acara().tahunBaru && !ARAK.containsKey(huni.situsId)
				&& (jam >= 10.0D && jam < 11.5D || jam >= 15.0D && jam < 16.0D)) {
			mulaiArak(level, huni, jam < 12.0D ? 12.0D : 16.5D);
		}
	}

	private static @Nullable Item makanan(int hr) {
		return switch (hr) {
			case HR_TAHUN_BARU -> KotaAcaraItems.KUE_KERANJANG;
			case HR_PERAHU_NAGA -> KotaAcaraItems.AIR_PLUM;
			case HR_QIXI -> KotaAcaraItems.TANGHULU;
			case HR_PERTENGAHAN_GUGUR -> KotaAcaraItems.KUE_BULAN;
			case HR_LABA -> KotaAcaraItems.BUBUR_LABA;
			case HR_TITIK_BALIK, HR_CHONGYANG -> KotaAcaraItems.PERMEN_GULA;
			default -> null;
		};
	}

	/** The parade loop of a place: up to 10 street nodes around the square in angle order, else a ring of 12 blocks. */
	private static void siapkanRute(ServerLevel level, TempatHuni huni, long day) {
		Long built = RUTE_HARI.get(huni.situsId);
		if (built != null && built == day && RUTE.containsKey(huni.situsId)) {
			return;
		}
		RUTE_HARI.put(huni.situsId, day);
		BlockPos alun = KalenderKota.alun(huni);
		List<Titik> nodes = new ArrayList<>(huni.titik("jalan"));
		nodes.removeIf(t -> t.pos().distSqr(alun) > 40.0D * 40.0D || t.pos().distSqr(alun) < 6.0D * 6.0D);
		nodes.sort(Comparator.comparingDouble(t -> t.pos().distSqr(alun)));
		if (nodes.size() > 10) {
			nodes = new ArrayList<>(nodes.subList(0, 10));
		}
		List<Vec3> path = new ArrayList<>();
		if (nodes.size() >= 4) {
			nodes.sort(Comparator.comparingDouble(t -> Math.atan2(t.pos().getZ() - alun.getZ(), t.pos().getX() - alun.getX())));
			for (Titik t : nodes) {
				path.add(Vec3.atBottomCenterOf(t.pos()));
			}
		} else {
			for (int i = 0; i < 8; i++) {
				double a = Math.PI * 2.0D * i / 8.0D;
				path.add(Vec3.atBottomCenterOf(tanah(level, alun.offset((int) Math.round(Math.cos(a) * 12.0D), 0, (int) Math.round(Math.sin(a) * 12.0D)))));
			}
		}
		RUTE.put(huni.situsId, path);
		// gravestones for Qingming (numbered nisan titik of 42W's cemeteries)
		List<BlockPos> graves = new ArrayList<>();
		for (String type : huni.ringkasTitik().keySet()) {
			if (type.startsWith(dev.kultivator.dunia.kota42.KotaTitik.NISAN)) {
				for (Titik t : huni.titik(type)) {
					graves.add(t.pos());
				}
			}
		}
		NISAN.put(huni.situsId, graves);
	}

	private static void mulaiArak(ServerLevel level, TempatHuni huni, double sampaiJam) {
		List<Vec3> path = RUTE.get(huni.situsId);
		if (path == null || path.size() < 2 || DuniaBudget.count(KotaAcaraEntities.ANGGARAN) + 5 > DuniaBudget.cap(KotaAcaraEntities.ANGGARAN)) {
			return;
		}
		BarongsaiEntity singa = KotaAcaraEntities.BARONGSAI.create(level, EntitySpawnReason.EVENT);
		if (singa == null) {
			return;
		}
		Vec3 first = path.getFirst();
		singa.ikat(huni.situsId, BlockPos.containing(first).asLong());
		singa.pindah(first, 0.0F);
		singa.setVarian((int) Math.floorMod(huni.situsId, (long) BarongsaiEntity.VARIAN_JUMLAH));
		if (!level.addFreshEntity(singa)) {
			return;
		}
		DuniaBudget.track(KotaAcaraEntities.ANGGARAN, singa);
		singa.arak(path, true);
		Arak a = new Arak(huni.situsId, singa);
		a.sampai = level.getGameTime() + KalenderKota.tickSampai(level.getServer(), sampaiJam);
		BlockPos base = BlockPos.containing(first);
		a.kepala = penari(level, huni.situsId, base, 1);
		a.ekor = penari(level, huni.situsId, base, 2);
		for (int i = 0; i < 2; i++) {
			KultivatorNpc m = HidupApi.tubuhStaf(level, huni.situsId, NpcRole.PEMUSIK, base.offset(i * 2 - 1, 0, -3), 0.0F,
					(int) (huni.situsId * 31L + 77L + i));
			if (m != null) {
				m.memory().putBoolean("hold", true);
				a.pemusik.add(m);
			}
		}
		ARAK.put(huni.situsId, a);
		level.playSound(null, base, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.AMBIENT, 2.0F, 1.0F);
		level.playSound(null, base, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.AMBIENT, 1.5F, 1.2F);
		for (ServerPlayer p : KalenderKota.penonton(level, base)) {
			Msg.actionBar(p, Component.translatable("kultivator.kota42.acara.barongsai.mulai").withStyle(ChatFormatting.RED));
		}
	}

	private static @Nullable KultivatorNpc penari(ServerLevel level, long situs, BlockPos at, int i) {
		KultivatorNpc npc = HidupApi.tubuhStaf(level, situs, NpcRole.PENARI, at, 0.0F, (int) (situs * 17L + i));
		if (npc != null) {
			npc.memory().putBoolean("hold", true);
		}
		return npc;
	}

	/** The drummers walk three blocks behind the lion, beating in step; firecrackers now and then. */
	private static void pemusik(Arak a) {
		double r = Math.toRadians(a.singa.getYRot());
		Vec3 maju = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
		Vec3 sisi = new Vec3(Math.cos(r), 0.0D, Math.sin(r));
		for (int i = 0; i < a.pemusik.size(); i++) {
			KultivatorNpc m = a.pemusik.get(i);
			if (m.isRemoved()) {
				continue;
			}
			Vec3 at = a.singa.position().add(maju.scale(-3.2D)).add(sisi.scale(i == 0 ? 0.9D : -0.9D));
			if (m.distanceToSqr(at) > 0.04D) {
				m.teleportTo(at.x, at.y, at.z);
			}
			m.setYRot(a.singa.getYRot());
			m.setYHeadRot(a.singa.getYRot());
			int klip = i == 0 ? KotaGerakId.TABUH_GENDERANG_KECIL : GerakId.GENDERANG;
			if (SosokApi.gerakSekarang(m) != klip) {
				SosokApi.gerakSinkron(m, klip, a.singa.mulai());
			}
		}
		if (a.singa.level() instanceof ServerLevel level && level.getGameTime() % 300L == 0L) {
			level.playSound(null, a.singa.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.AMBIENT, 1.6F, 1.1F);
		}
	}

	private static void bubar(Arak a) {
		if (!a.singa.isRemoved()) {
			a.singa.discard();
		}
		if (a.kepala != null) {
			a.kepala.vanish();
		}
		if (a.ekor != null) {
			a.ekor.vanish();
		}
		for (KultivatorNpc m : a.pemusik) {
			m.vanish();
		}
	}

	// ================================================================== Nenek Tang's winter soup

	private static void supTick(ServerLevel level, TempatHuni huni, long day, double jam) {
		boolean buka = musim(huni.situsId, day) == Jadwal.DINGIN && jam >= 10.0D && jam < 20.0D;
		KultivatorNpc body = SUP.get(huni.situsId);
		if (!buka) {
			if (body != null) {
				body.vanish();
				SUP.remove(huni.situsId);
			}
			return;
		}
		KultivatorNpc nenek = tokohTubuh(huni, "nenek_tang");
		if (nenek == null) {
			if (body == null || body.isRemoved()) {
				BlockPos spot = tempatSup(huni);
				if (spot == null || !KalenderKota.adaPemain(level, spot, KotaConfig.acara().jarakTampil)) {
					return;
				}
				body = HidupApi.tubuhStaf(level, huni.situsId, NpcRole.KOKI, spot, 0.0F, (int) (huni.situsId * 13L + 81L));
				if (body == null) {
					return;
				}
				body.memory().putBoolean("hold", true);
				body.memory().putString("kota42_sup", "1");
				SosokApi.setUsia(body, dev.kultivator.sosok.Sosok.TUA);
				long first = CeritaData.get(level.getServer()).firstCapital();
				Component nama = first == 0L || first == huni.situsId
						? Component.translatable("kultivator.kota42.acara.tokoh.nenek_tang")
						: Component.translatable("kultivator.kota42.acara.tokoh.penjual_sup");
				body.setPlate(Component.translatable("kultivator.kota.papan_nama", nama,
						Component.translatable("kultivator.kota42.acara.gelar.warung_sup")));
				SUP.put(huni.situsId, body);
			}
			nenek = body;
		}
		if (level.getGameTime() % 120L < 20L) {
			HidupApi.klip(nenek, KotaGerakId.SAJIKAN_TEH_BESAR, KotaGerakId.PROP_KENDI_SUP, 80);
		}
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, nenek.getBoundingBox().inflate(3.0D))) {
			if (!p.isSpectator() && KalenderKota.Berkah.sekaliSehari(p, "sup_hangat")) {
				Barang.beri(p, new ItemStack(KotaAcaraItems.SUP_HANGAT));
				nenek.say(Component.translatable("kultivator.kota42.acara.sup.beri"), 80);
				HidupApi.tatap(nenek, p);
				if (VoiceEvents.exists("kota42.sup_nenek")) {
					Voice.say(nenek, "kota42.sup_nenek", null);
				}
			}
		}
	}

	private static @Nullable BlockPos tempatSup(TempatHuni huni) {
		for (String type : new String[] {"lapak", "kerja:kios", "obrol"}) {
			List<Titik> list = huni.titik(type);
			if (!list.isEmpty()) {
				return list.get((int) Math.floorMod(huni.situsId * 7L, (long) list.size())).pos();
			}
		}
		return null;
	}

	/** The live body of the resident who is character {@code tokoh} in this place, or null. */
	static @Nullable KultivatorNpc tokohTubuh(TempatHuni huni, String tokoh) {
		for (Warga w : WargaApi.wargaTempat(huni.situsId)) {
			if (tokoh.equals(w.tokoh) && w.hidup() && w.slot >= 0) {
				return huni.tubuhDari(w.slot);
			}
		}
		return null;
	}

	/** The ground at {@code pos}'s column (top of motion blocking, near its own y). */
	static BlockPos tanah(ServerLevel level, BlockPos pos) {
		BlockPos at = dev.kultivator.dunia.tempat.TempatApi.groundSpot(level, pos.getX(), pos.getZ(), pos.getY());
		return at != null ? at : pos;
	}

	// ================================================================== test

	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("musim").executes(MusimKota::ujiTahun)
				.then(Commands.argument("musim", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("semi", "panas", "gugur", "dingin"), b))
						.executes(ctx -> ujiPratinjau(ctx, ""))
						.then(Commands.argument("hari_raya", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of(ID).subList(1, ID.length), b))
								.executes(ctx -> ujiPratinjau(ctx, StringArgumentType.getString(ctx, "hari_raya")))));
	}

	private static int ujiTahun(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		long today = KalenderKota.hari(server);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.tahun", today,
				Component.translatable("kultivator.kota42.acara.musim." + KalenderKota.musim(today)), KalenderKota.hariMusim(today))
				.withStyle(ChatFormatting.GOLD), false);
		for (int i = 1; i < ID.length; i++) {
			int hr = i;
			long next = berikut(hr, today);
			ctx.getSource().sendSuccess(() -> Component.literal("  " + next + " ").append(nama(hr)), false);
		}
		return 1;
	}

	private static int ujiPratinjau(CommandContext<CommandSourceStack> ctx, String raya) throws CommandSyntaxException {
		TempatInfo info = KalenderKota.tempatUji(ctx);
		if (info == null) {
			return 0;
		}
		String m = StringArgumentType.getString(ctx, "musim");
		int musim = switch (m) {
			case "semi" -> Jadwal.SEMI;
			case "panas" -> Jadwal.PANAS;
			case "gugur" -> Jadwal.GUGUR;
			default -> Jadwal.DINGIN;
		};
		int hr = HR_TIDAK;
		for (int i = 1; i < ID.length; i++) {
			if (ID[i].equals(raya)) {
				hr = i;
			}
		}
		long until = ctx.getSource().getServer().overworld().getGameTime() + 6000L;
		PRATINJAU.put(info.situsId(), new long[] {musim, hr, until});
		RUTE_HARI.remove(info.situsId());
		int shown = hr;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.acara.uji.pratinjau",
				Component.translatable("kultivator.kota42.acara.musim." + musim), shown == HR_TIDAK ? Component.literal("-") : nama(shown)), false);
		return 1;
	}

	/** Active place of {@code situs} (test helpers). */
	static @Nullable TempatHuni huni(long situs) {
		return Pemanggil.huni(situs);
	}

	private MusimKota() {
	}
}
