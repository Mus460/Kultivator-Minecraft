package dev.kultivator.dunia.kota42.bicara;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kultivator.data.Cultivators;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.acara.KotaAcaraItems;
import dev.kultivator.dunia.kota42.api.JenisHubungan;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.Kenangan;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Cakap;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.hewan.HewanEntities;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Kebiasaan;
import dev.kultivator.dunia.kota42.warga.SiklusHidup;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Personal requests of residents ("Tolong", DESIGN 42.5.6, contract C42.B1). Requests are born from the residents' real
 * needs on Hari Warga (a sick household member, a poor winter, a lover, an enemy, a loan shark, a lost child...), at most
 * one open per resident, {@code kotaBicara.permintaanPerTempat} per place and {@code permintaanPerHari} accepted per player
 * a day. Each request is a short chain of real objectives the server checks itself:
 * <ul>
 * <li>{@code SERAH} hand items over at the asker (by item id, {@code #tag} or {@code kat:<Kategori>});</li>
 * <li>{@code BAYAR} pay NB at the asker (lending, paying a debt, the healer);</li>
 * <li>{@code BICARA} talk with a named resident (anywhere, also in another town);</li>
 * <li>{@code TEMUKAN} find a lost cat or child that really stands somewhere in or at the edge of the place;</li>
 * <li>{@code TEMANI} stay 60 seconds beside a resident's body (harvest help, teaching a child);</li>
 * <li>{@code DUEL} beat thugs or a pickpocket in a friendly duel (nobody dies);</li>
 * <li>{@code PERGI} reach a place (a sect gate, the player's own hall) with a letter;</li>
 * <li>{@code HADIR} be in the place while the family's ceremony runs ({@code KotaBus.UPACARA}).</li>
 * </ul>
 * Rewards come from the town (Kas Kota pays money; no NB is minted), with impression, the {@code DIBANTU} memory, the
 * {@code MENOLONG} gossip and the promise record of 42K ({@code Cakap.janji}/{@code tepati}). A deadline missed after
 * accepting costs impression 5.
 * <p>
 * Data file {@code data/kultivator/tempat/tolong/<jenis>.json} (optional, 42B-C; code has a full default):
 * <pre>
 * { "nama": "lang key",            the request's title (default kultivator.kota42.bicara.tolong.&lt;jenis&gt;.nama)
 *   "teks": ["lang key", ...],     the asking lines, one picked per warga (args: %1$s target name, %2$s count/NB)
 *   "imbalan": "lang key",         the reward line (args: %1$s NB)
 *   "batas_hari": 3,               days to finish
 *   "peluang": 1.0 }               multiplier of the daily birth chance (0 disables the kind)
 * </pre>
 * Storage: {@code KotaBicara.Data} section {@code tolong}, per place {@code kota_<situs>}: {@code L} list of
 * {@code {i id, w warga, j jenis, d day made, b deadline, st state, p accepting player, f isian, o objectives, k progress,
 * e spawned entity uuids, tt seconds stayed}}, {@code n} next id; per player {@code p_<uuid>}: {@code {h day, n accepted}}.
 */
public final class PermintaanWarga {

	/**
	 * One open request: id, the asking resident, template kind, place, world day made and deadline, and the filled slots of
	 * the template ({@code isian}: item, count, target resident, place...).
	 */
	public record Permintaan(int id, int warga, String jenis, long situs, long dibuat, long batas, CompoundTag isian) {
	}

	/** The 16 kinds of 42.5.6 in table order. */
	public static final List<String> JENIS = List.of(KotaLembar.Dialog.TL_OBAT_ORTU, KotaLembar.Dialog.TL_KAYU_BAKAR,
			KotaLembar.Dialog.TL_KUCING_HILANG, KotaLembar.Dialog.TL_ANAK_HILANG, KotaLembar.Dialog.TL_SURAT_KEKASIH,
			KotaLembar.Dialog.TL_DAMAIKAN, KotaLembar.Dialog.TL_RENTENIR, KotaLembar.Dialog.TL_PREMAN, KotaLembar.Dialog.TL_BARANG_DICOPET,
			KotaLembar.Dialog.TL_BANTU_PANEN, KotaLembar.Dialog.TL_BAHAN_RUMAH, KotaLembar.Dialog.TL_AJARI_ANAK, KotaLembar.Dialog.TL_CARI_JODOH,
			KotaLembar.Dialog.TL_KABAR_ANAK_SEKTE, KotaLembar.Dialog.TL_PINJAM_UANG, KotaLembar.Dialog.TL_UNDANGAN);

	/** Request states. */
	static final int TERBUKA = 0;
	static final int DITERIMA = 1;
	static final int SELESAI = 2;
	static final int GAGAL = 3;

	/** Objective kinds (ordinal saved). */
	enum Tugas {
		SERAH, BAYAR, BICARA, TEMUKAN, TEMANI, DUEL, PERGI, HADIR
	}

	/** Seconds of {@link Tugas#TEMANI}. */
	static final int TEMANI_DETIK = 60;
	/** Most requests born per place a day. */
	static final int LAHIR_PER_HARI = 2;
	/** Ticks between two duel offers of one thug. */
	static final int TANTANG_JEDA = 600;

	/** One planned request before it is stored. */
	private record Rencana(CompoundTag isian, List<CompoundTag> tujuan, int batasHari) {
	}

	/** Spawned helpers (cat, child body, thugs): entity uuid -> "situs:id". */
	private static final Map<UUID, String> MILIK = new HashMap<>();
	private static final Map<UUID, Long> TANTANG = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		DuelEndListeners.add((session, winner, loser) -> {
			if (winner instanceof ServerPlayer player && loser != null) {
				String key = MILIK.get(loser.getUUID());
				if (key != null) {
					majuDuel(player, key, loser);
				}
			}
		});
		// invitations come from real family feasts: a wedding, and the one-month feast of a newborn (42.5.6 "undangan")
		KotaBus.NIKAH.register((server, situs, pria, wanita) -> undang(pria, JenisUpacara.NIKAH, 3));
		KotaBus.LAHIR.register((server, situs, bayi, ibu) -> {
			if (ibu != null) {
				undang(ibu, JenisUpacara.SEBULAN, 10);
			}
		});
		KotaBus.UPACARA.register((server, situs, jenis, id, fase) -> {
			if (fase >= 1) {
				hadir(server, situs, jenis);
			}
		});
	}

	static void lupakanSemua() {
		MILIK.clear();
		TANTANG.clear();
	}

	// ================================================================== C42.B1

	/** Makes a request of kind {@code jenis} ({@code tolong/<jenis>.json}) for {@code warga}; null when none fits now. */
	public static @Nullable Permintaan buat(Warga warga, String jenis) {
		return buat(warga, jenis, false);
	}

	/** {@link #buat} ignoring chance, season and soft needs (test command); structural needs (a lover, a child) still hold. */
	static @Nullable Permintaan paksa(Warga warga, String jenis) {
		return buat(warga, jenis, true);
	}

	private static @Nullable Permintaan buat(Warga w, String jenis, boolean paksa) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || w == null || !JENIS.contains(jenis) || !KotaBicara.aktif() || !w.hidup()) {
			return null;
		}
		long situs = WargaData.get(server).situsDari(w.id);
		if (situs == 0L) {
			return null;
		}
		ListTag list = daftar(server, situs);
		int open = 0;
		for (Tag t : list) {
			CompoundTag c = (CompoundTag) t;
			int st = c.getIntOr("st", 0);
			if (st <= DITERIMA) {
				open++;
				if (c.getIntOr("w", 0) == w.id) {
					return null;
				}
			}
		}
		if (!paksa && open >= KotaConfig.bicara().permintaanPerTempat) {
			return null;
		}
		long hari = KotaHidup.hari(server);
		RandomSource r = RandomSource.create(w.id * 0x9E3779B97F4A7C15L ^ hari * 31L ^ jenis.hashCode());
		Rencana plan = rencana(server, situs, w, jenis, hari, r, paksa);
		if (plan == null) {
			return null;
		}
		KotaBicara.Data data = KotaBicara.Data.get(server);
		CompoundTag site = data.situs(KotaBicara.Data.TOLONG, situs);
		int id = site.getIntOr("n", 1);
		site.putInt("n", id + 1);
		CompoundTag c = new CompoundTag();
		c.putInt("i", id);
		c.putInt("w", w.id);
		c.putString("j", jenis);
		c.putLong("d", hari);
		int days = data(jenis).has("batas_hari") ? Math.max(1, data(jenis).get("batas_hari").getAsInt()) : plan.batasHari();
		c.putLong("b", hari + days);
		c.putInt("st", TERBUKA);
		c.put("f", plan.isian());
		ListTag o = new ListTag();
		o.addAll(plan.tujuan());
		c.put("o", o);
		c.putIntArray("k", new int[plan.tujuan().size()]);
		list.add(c);
		data.ubah();
		return dari(situs, c);
	}

	private static Permintaan dari(long situs, CompoundTag c) {
		return new Permintaan(c.getIntOr("i", 0), c.getIntOr("w", 0), c.getStringOr("j", ""), situs, c.getLongOr("d", 0L), c.getLongOr("b", 0L),
				c.getCompoundOrEmpty("f").copy());
	}

	/** Every open or accepted request of a place (read-only copies, table order). */
	public static List<Permintaan> daftarTempat(MinecraftServer server, long situs) {
		List<Permintaan> out = new ArrayList<>();
		for (Tag t : daftar(server, situs)) {
			CompoundTag c = (CompoundTag) t;
			if (c.getIntOr("st", 0) <= DITERIMA) {
				out.add(dari(situs, c));
			}
		}
		return out;
	}

	// ================================================================== planning (the needs of 42.5.6)

	private static CompoundTag tujuan(Tugas t, String arg, int maks, int warga, long pos) {
		CompoundTag c = new CompoundTag();
		c.putInt("t", t.ordinal());
		c.putString("a", arg);
		c.putInt("n", Math.max(1, maks));
		c.putInt("w", warga);
		c.putLong("x", pos);
		return c;
	}

	private static double peluang(String jenis) {
		JsonObject o = data(jenis);
		return o.has("peluang") ? Math.max(0.0D, o.get("peluang").getAsDouble()) : 1.0D;
	}

	private static boolean undi(RandomSource r, boolean paksa, String jenis, double p) {
		return paksa || r.nextDouble() < p * peluang(jenis);
	}

	static int musim(long hari) {
		return (int) Math.floorMod(hari / 24L, 4L);
	}

	private static @Nullable Rencana rencana(MinecraftServer server, long situs, Warga w, String jenis, long hari, RandomSource r, boolean paksa) {
		WargaData data = WargaData.get(server);
		WargaData.Situs s = data.peek(situs);
		if (s == null) {
			return null;
		}
		int umur = WargaApi.umur(w);
		int b = 0;
		CompoundTag f = new CompoundTag();
		List<CompoundTag> o = new ArrayList<>();
		switch (jenis) {
			case KotaLembar.Dialog.TL_OBAT_ORTU -> {
				Warga sakit = null;
				for (Warga k : keluarga(s, w)) {
					if (k != w && k.hidup() && k.kesehatan < 30) {
						sakit = k;
						break;
					}
				}
				if (umur < 12 || sakit == null || !undi(r, paksa, jenis, 0.5D)) {
					return null;
				}
				f.putInt("sakit", sakit.id);
				o.add(tujuan(Tugas.SERAH, "kat:obat", 1, w.id, 0L));
				return new Rencana(f, o, 3);
			}
			case KotaLembar.Dialog.TL_KAYU_BAKAR -> {
				if (umur < 16 || !paksa && musim(hari) != 3 || !paksa && Suasana.kemakmuranRumahTangga(server, situs, w) >= 40 || !undi(r, paksa, jenis, 0.3D)) {
					return null;
				}
				o.add(tujuan(Tugas.SERAH, "#minecraft:logs", 16, w.id, 0L));
				return new Rencana(f, o, 3);
			}
			case KotaLembar.Dialog.TL_KUCING_HILANG -> {
				if (!Kebiasaan.punya(w, "teman_kucing") || !undi(r, paksa, jenis, 0.03D)) {
					return null;
				}
				BlockPos spot = tempatJauh(server, situs, w, r, 30, 96);
				if (spot == null) {
					return null;
				}
				f.putLong("pos", spot.asLong());
				o.add(tujuan(Tugas.TEMUKAN, "kucing", 1, w.id, spot.asLong()));
				o.add(tujuan(Tugas.BICARA, "", 1, w.id, 0L));
				return new Rencana(f, o, 2);
			}
			case KotaLembar.Dialog.TL_ANAK_HILANG -> {
				Warga anak = null;
				for (Warga k : keluarga(s, w)) {
					int u = WargaApi.umur(k);
					if (k != w && k.status == Warga.Status.HIDUP && u >= 5 && u <= 12 && (k.ayah == w.id || k.ibu == w.id)) {
						anak = k;
						break;
					}
				}
				if (anak == null || !undi(r, paksa, jenis, 0.015D)) {
					return null;
				}
				BlockPos spot = tepi(server, situs, r);
				if (spot == null) {
					return null;
				}
				f.putInt("anak", anak.id);
				f.putLong("pos", spot.asLong());
				o.add(tujuan(Tugas.TEMUKAN, "anak", 1, anak.id, spot.asLong()));
				o.add(tujuan(Tugas.BICARA, "", 1, w.id, 0L));
				return new Rencana(f, o, 1);
			}
			case KotaLembar.Dialog.TL_SURAT_KEKASIH -> {
				for (long bond : w.hubungan) {
					if (JenisHubungan.jenis(bond) == JenisHubungan.KEKASIH && JenisHubungan.kekuatan(bond) < 70) {
						Warga kekasih = WargaApi.byId(JenisHubungan.idLain(bond));
						if (kekasih != null && kekasih.hidup() && undi(r, paksa, jenis, 0.15D)) {
							f.putInt("target", kekasih.id);
							o.add(tujuan(Tugas.BICARA, "surat", 1, kekasih.id, 0L));
							return new Rencana(f, o, 4);
						}
					}
				}
				return null;
			}
			case KotaLembar.Dialog.TL_DAMAIKAN -> {
				for (long bond : w.hubungan) {
					if (JenisHubungan.jenis(bond) == JenisHubungan.MUSUH) {
						Warga musuh = WargaApi.byId(JenisHubungan.idLain(bond));
						if (musuh != null && musuh.status == Warga.Status.HIDUP && data.situsDari(musuh.id) == situs && undi(r, paksa, jenis, 0.08D)) {
							f.putInt("target", musuh.id);
							o.add(tujuan(Tugas.BICARA, "damai", 1, musuh.id, 0L));
							o.add(tujuan(Tugas.BICARA, "", 1, w.id, 0L));
							return new Rencana(f, o, 3);
						}
					}
				}
				return null;
			}
			case KotaLembar.Dialog.TL_RENTENIR -> {
				if (umur < 18 || !paksa && Suasana.kemakmuranRumahTangga(server, situs, w) >= 25) {
					return null;
				}
				Warga lintah = null;
				for (Warga k : s.warga) {
					String watak = watak(k);
					if (k != w && k.status == Warga.Status.HIDUP && WargaApi.umur(k) >= 25 && k.rumahTangga != w.rumahTangga
							&& ("licik".equals(watak) || "serakah".equals(watak)) && Suasana.kemakmuranRumahTangga(server, situs, k) >= 50) {
						lintah = k;
						break;
					}
				}
				if (lintah == null || !undi(r, paksa, jenis, 0.06D)) {
					return null;
				}
				int nb = 10 + r.nextInt(31);
				f.putInt("target", lintah.id);
				f.putInt("nb", nb);
				o.add(tujuan(Tugas.BAYAR, "rentenir", nb, w.id, 0L));
				return new Rencana(f, o, 4);
			}
			case KotaLembar.Dialog.TL_PREMAN, KotaLembar.Dialog.TL_BARANG_DICOPET -> {
				boolean preman = jenis.equals(KotaLembar.Dialog.TL_PREMAN);
				if (umur < 16 || w.peranNpc().kelompok != NpcRole.Kelompok.PEDAGANG && !kumuh(server, situs, w)
						|| !undi(r, paksa, jenis, preman ? 0.02D : 0.01D)) {
					return null;
				}
				BlockPos spot = tempatJauh(server, situs, w, r, 16, 48);
				if (spot == null) {
					return null;
				}
				f.putLong("pos", spot.asLong());
				o.add(tujuan(Tugas.DUEL, preman ? "preman" : "copet", preman ? 2 + r.nextInt(2) : 1, w.id, spot.asLong()));
				return new Rencana(f, o, 2);
			}
			case KotaLembar.Dialog.TL_BANTU_PANEN -> {
				if (w.peranNpc() != NpcRole.PETANI || !paksa && musim(hari) != 2 || !undi(r, paksa, jenis, 0.2D)) {
					return null;
				}
				o.add(tujuan(Tugas.TEMANI, "panen", TEMANI_DETIK, w.id, 0L));
				return new Rencana(f, o, 2);
			}
			case KotaLembar.Dialog.TL_BAHAN_RUMAH -> {
				if (umur < 18 || !paksa && !PerbaikanApi.adaKerusakan(situs) || !undi(r, paksa, jenis, 0.25D)) {
					return null;
				}
				o.add(tujuan(Tugas.SERAH, "#minecraft:planks", 32, w.id, 0L));
				return new Rencana(f, o, 3);
			}
			case KotaLembar.Dialog.TL_AJARI_ANAK -> {
				for (Warga k : keluarga(s, w)) {
					int u = WargaApi.umur(k);
					if (k != w && k.status == Warga.Status.HIDUP && u >= 7 && u <= 14 && (Kebiasaan.punya(k, "kutu_buku") || k.akar > 0)
							&& k.slot != Warga.TANPA_SLOT && undi(r, paksa, jenis, 0.1D)) {
						f.putInt("anak", k.id);
						o.add(tujuan(Tugas.TEMANI, "ajar", TEMANI_DETIK, k.id, 0L));
						return new Rencana(f, o, 3);
					}
				}
				return null;
			}
			case KotaLembar.Dialog.TL_CARI_JODOH -> {
				if (!w.lajang() || umur < 25 || umur > 45 || !undi(r, paksa, jenis, 0.05D)) {
					return null;
				}
				Warga comblang = null;
				Warga calon = null;
				for (Warga k : s.warga) {
					if (k.status != Warga.Status.HIDUP || k == w) {
						continue;
					}
					if (k.peranNpc() == NpcRole.MAK_COMBLANG && comblang == null) {
						comblang = k;
					}
					// 42.16 K9: a match is always one man and one woman
					int u = WargaApi.umur(k);
					if (calon == null && k.kelamin != w.kelamin && k.lajang() && u >= 20 && Math.abs(u - umur) <= 10 && w.hubunganDengan(k.id) == 0L) {
						calon = k;
					}
				}
				if (calon == null) {
					return null;
				}
				f.putInt("target", calon.id);
				if (comblang != null) {
					f.putInt("comblang", comblang.id);
					o.add(tujuan(Tugas.BICARA, "comblang", 1, comblang.id, 0L));
				}
				o.add(tujuan(Tugas.BICARA, "jodoh", 1, calon.id, 0L));
				return new Rencana(f, o, 5);
			}
			case KotaLembar.Dialog.TL_KABAR_ANAK_SEKTE -> {
				Warga murid = null;
				for (Warga k : keluarga(s, w)) {
					if (k.status == Warga.Status.SEKTE) {
						murid = k;
						break;
					}
				}
				BlockPos gerbang = murid == null ? null : gerbangSekte(server, situs);
				if (murid == null || gerbang == null || !undi(r, paksa, jenis, 0.1D)) {
					return null;
				}
				f.putInt("anak", murid.id);
				f.putLong("pos", gerbang.asLong());
				o.add(tujuan(Tugas.PERGI, "gerbang", 1, murid.id, gerbang.asLong()));
				return new Rencana(f, o, 7);
			}
			case KotaLembar.Dialog.TL_PINJAM_UANG -> {
				if (umur < 18 || !paksa && Suasana.kemakmuranRumahTangga(server, situs, w) >= 20 || !undi(r, paksa, jenis, 0.08D)) {
					return null;
				}
				int nb = 10 + 5 * r.nextInt(11);
				f.putInt("nb", nb);
				f.putInt("bunga", r.nextInt(11));
				o.add(tujuan(Tugas.BAYAR, "pinjam", nb, w.id, 0L));
				return new Rencana(f, o, 3);
			}
			case KotaLembar.Dialog.TL_UNDANGAN -> {
				// born from KotaBus.NIKAH / LAHIR (undang); the daily pass never invents a ceremony
				if (!paksa) {
					return null;
				}
				f.putString("upacara", JenisUpacara.values()[0].name());
				o.add(tujuan(Tugas.HADIR, JenisUpacara.values()[0].name(), 1, w.id, 0L));
				return new Rencana(f, o, 3);
			}
			default -> {
				return null;
			}
		}
	}

	/** An invitation to a household ceremony (from 42D's wedding/birth flow or a KotaBus listener). */
	public static @Nullable Permintaan undang(Warga tuanRumah, JenisUpacara jenis, int hariMaks) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || tuanRumah == null || jenis == null || !KotaBicara.aktif()) {
			return null;
		}
		Permintaan p = buat(tuanRumah, KotaLembar.Dialog.TL_UNDANGAN, true);
		if (p == null) {
			return null;
		}
		CompoundTag c = cari(server, p.situs(), p.id());
		if (c != null) {
			c.getCompoundOrEmpty("f").putString("upacara", jenis.name());
			ListTag o = c.getListOrEmpty("o");
			if (!o.isEmpty()) {
				o.getCompoundOrEmpty(0).putString("a", jenis.name());
			}
			c.putLong("b", c.getLongOr("d", 0L) + Math.max(1, hariMaks));
			KotaBicara.Data.get(server).ubah();
		}
		return p;
	}

	private static List<Warga> keluarga(WargaData.Situs s, Warga w) {
		List<Warga> out = new ArrayList<>();
		for (Warga k : s.warga) {
			if (k == w || w.rumahTangga != 0 && k.rumahTangga == w.rumahTangga || k.id == w.ayah || k.id == w.ibu || k.id == w.pasangan
					|| k.ayah == w.id || k.ibu == w.id) {
				out.add(k);
			}
		}
		return out;
	}

	static String watak(Warga w) {
		return Tokoh.WATAK.get(Math.floorMod(w.watak, Tokoh.WATAK.size()));
	}

	private static boolean kumuh(MinecraftServer server, long situs, Warga w) {
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni == null) {
			return false;
		}
		TempatInfo info = TempatApi.tempatDi(server.overworld(), huni.pusat());
		return info != null && LembarKunci.DISTRIK_KUMUH.equals(info.distrik()) || Kebiasaan.punya(w, "pemarah");
	}

	/** A titik of the place {@code min}-{@code max} blocks from the warga's house (cat nap spots, squats, cages first). */
	private static @Nullable BlockPos tempatJauh(MinecraftServer server, long situs, Warga w, RandomSource r, int min, int max) {
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni == null) {
			return null;
		}
		BlockPos home = huni.pusat();
		if (w.slot != Warga.TANPA_SLOT && huni.orang(w.slot) != null) {
			home = huni.rumahPos(huni.orang(w.slot));
		}
		List<Titik> pool = new ArrayList<>();
		for (String kind : List.of(KotaTitik.KUCING_TIDUR, KotaTitik.JONGKOK, KotaTitik.GANTUNG_SANGKAR, KotaTitik.SUMUR, KotaTitik.CATUR,
				KotaTitik.PANCING, KotaTitik.KANDANG)) {
			for (Titik t : huni.titik(kind)) {
				double d = Math.sqrt(t.pos().distSqr(home));
				if (d >= min && d <= max) {
					pool.add(t);
				}
			}
		}
		if (pool.isEmpty()) {
			return null;
		}
		return pool.get(r.nextInt(pool.size())).pos();
	}

	/** A walkable spot at the edge of the place's box (a child that wandered off toward the walls). */
	private static @Nullable BlockPos tepi(MinecraftServer server, long situs, RandomSource r) {
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni == null) {
			return null;
		}
		BoundingBox box = huni.kotak;
		BlockPos c = huni.pusat();
		for (int tries = 0; tries < 6; tries++) {
			int side = r.nextInt(4);
			int x = switch (side) {
				case 0 -> box.minX() + 6;
				case 1 -> box.maxX() - 6;
				default -> box.minX() + 6 + r.nextInt(Math.max(1, box.getXSpan() - 12));
			};
			int z = switch (side) {
				case 2 -> box.minZ() + 6;
				case 3 -> box.maxZ() - 6;
				default -> box.minZ() + 6 + r.nextInt(Math.max(1, box.getZSpan() - 12));
			};
			if (!server.overworld().hasChunk(x >> 4, z >> 4)) {
				// the spot is fixed now; its ground is found when a player comes near
				return new BlockPos(x, c.getY(), z);
			}
			BlockPos g = TempatApi.groundSpot(server.overworld(), x, z, c.getY());
			if (g != null) {
				return g;
			}
		}
		return null;
	}

	/** The player's own hall within 3000 blocks is not known here; the nearest NPC sect gate within 3000 blocks is. */
	private static @Nullable BlockPos gerbangSekte(MinecraftServer server, long situs) {
		BlockPos here = dev.kultivator.dunia.tempat.tema.bersama.Dagang.posSitus(server, situs);
		if (here == null) {
			return null;
		}
		BlockPos best = null;
		double bestD = 3000.0D * 3000.0D;
		for (DuniaSites.Site s : DuniaSites.all(server)) {
			if (s.dim() == Level.OVERWORLD && ("sekte/gerbang".equals(s.kind()) || "sekte/kompleks".equals(s.kind()))) {
				double d = s.pos().distSqr(here);
				if (d < bestD) {
					bestD = d;
					best = s.pos();
				}
			}
		}
		return best;
	}

	// ================================================================== daily pass (Hari Warga)

	static void harian(MinecraftServer server, WargaData.Situs s, long hari) {
		if (!KotaBicara.aktif()) {
			return;
		}
		ListTag list = daftar(server, s.id);
		KotaBicara.Data data = KotaBicara.Data.get(server);
		int open = 0;
		for (int i = list.size() - 1; i >= 0; i--) {
			CompoundTag c = list.getCompoundOrEmpty(i);
			int st = c.getIntOr("st", 0);
			long batas = c.getLongOr("b", 0L);
			if (st <= DITERIMA && hari > batas) {
				gagal(server, s.id, c);
				st = GAGAL;
			}
			if (st >= SELESAI && hari - batas > 2L) {
				list.remove(i);
				continue;
			}
			if (st <= DITERIMA) {
				open++;
			}
		}
		int budget = Math.min(LAHIR_PER_HARI, KotaConfig.bicara().permintaanPerTempat - open);
		if (budget <= 0 || s.warga.isEmpty()) {
			data.ubah();
			return;
		}
		RandomSource r = RandomSource.create(s.id * 0x2545F4914F6CDD1DL ^ hari);
		List<Warga> order = new ArrayList<>(s.warga);
		for (int i = order.size() - 1; i > 0; i--) {
			int j = r.nextInt(i + 1);
			Warga t = order.get(i);
			order.set(i, order.get(j));
			order.set(j, t);
		}
		for (Warga w : order) {
			if (budget <= 0) {
				break;
			}
			if (w.status != Warga.Status.HIDUP) {
				continue;
			}
			int start = r.nextInt(JENIS.size());
			for (int k = 0; k < JENIS.size(); k++) {
				String jenis = JENIS.get((start + k) % JENIS.size());
				if (buat(w, jenis, false) != null) {
					budget--;
					break;
				}
			}
		}
		data.ubah();
	}

	private static void gagal(MinecraftServer server, long situs, CompoundTag c) {
		c.putInt("st", GAGAL);
		lepasEntitas(server, c);
		UUID p = pemain(c);
		Warga w = WargaApi.byId(c.getIntOr("w", 0));
		if (p != null && w != null) {
			Ingatan i = WargaApi.ingatan(w, p);
			i.ubah(-5, null, false, KotaHidup.hari(server));
			WargaData.get(server).ubah();
		}
	}

	// ================================================================== sheet

	/** Tablet Tolong: this warga's open and accepted requests ({@link KotaLembar.Dialog#TOLONG} rows). */
	static ListTag lembar(ServerPlayer player, Warga w) {
		ListTag out = new ListTag();
		MinecraftServer server = player.level().getServer();
		long situs = WargaData.get(server).situsDari(w.id);
		long hari = KotaHidup.hari(server);
		boolean batasPemain = diterimaHariIni(server, player.getUUID(), hari) >= KotaConfig.bicara().permintaanPerHari;
		for (Tag t : daftar(server, situs)) {
			CompoundTag c = (CompoundTag) t;
			if (c.getIntOr("w", 0) != w.id) {
				continue;
			}
			int st = c.getIntOr("st", 0);
			UUID owner = pemain(c);
			boolean mine = player.getUUID().equals(owner);
			if (st == GAGAL || st == SELESAI && !mine || st == DITERIMA && !mine) {
				continue;
			}
			String jenis = c.getStringOr("j", "");
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, idTeks(situs, c));
			row.putString(LembarKunci.JENIS, jenis);
			LembarKunci.teks(row, LembarKunci.NAMA, nama(jenis));
			LembarKunci.teks(row, LembarKunci.TEKS, teks(server, w, c));
			ListTag goals = new ListTag();
			ListTag o = c.getListOrEmpty("o");
			int[] k = c.getIntArray("k").orElse(new int[o.size()]);
			for (int i = 0; i < o.size(); i++) {
				CompoundTag g = o.getCompoundOrEmpty(i);
				CompoundTag gr = new CompoundTag();
				int maks = g.getIntOr("n", 1);
				int prog = i < k.length ? k[i] : 0;
				if (Tugas.values()[Math.floorMod(g.getIntOr("t", 0), Tugas.values().length)] == Tugas.TEMANI) {
					prog = i < k.length && prog >= maks ? maks : (int) Math.min(maks, c.getIntOr("tt", 0));
				}
				LembarKunci.teks(gr, LembarKunci.TEKS, teksTujuan(g));
				gr.putInt(LembarKunci.KEMAJUAN, Math.min(prog, maks));
				gr.putInt(LembarKunci.MAKS, maks);
				gr.putBoolean("selesai", prog >= maks);
				goals.add(gr);
			}
			row.put(LembarKunci.TUJUAN, goals);
			long nb = imbalanNb(player, jenis, c);
			LembarKunci.teks(row, KotaLembar.Dialog.IMBALAN, imbalanTeks(jenis, nb));
			row.putLong(LembarKunci.NB, nb);
			long sisa = (c.getLongOr("b", hari) + 1L) * 24000L - player.level().getServer().overworld().getOverworldClockTime();
			row.putLong(LembarKunci.SISA_TICK, Math.max(0L, sisa));
			String keadaan = st == SELESAI ? KotaLembar.Dialog.TL_SELESAI : st == DITERIMA ? KotaLembar.Dialog.TL_DITERIMA
					: batasPemain ? LembarKunci.K_BATAS : LembarKunci.K_OK;
			row.putString(LembarKunci.KEADAAN, keadaan);
			BlockPos at = posTujuan(c);
			if (at != null) {
				row.putInt(LembarKunci.X, at.getX());
				row.putInt(LembarKunci.Z, at.getZ());
			}
			out.add(row);
		}
		return out;
	}

	private static String idTeks(long situs, CompoundTag c) {
		return "tolong:" + situs + ":" + c.getIntOr("w", 0) + ":" + c.getLongOr("d", 0L) + ":" + c.getIntOr("i", 0);
	}

	static Component nama(String jenis) {
		JsonObject o = data(jenis);
		return Component.translatable(o.has("nama") ? o.get("nama").getAsString() : "kultivator.kota42.bicara.tolong." + jenis + ".nama");
	}

	/** The asking line: data variants (one per warga), else the built-in line, with the target's name and the amount. */
	private static Component teks(MinecraftServer server, Warga w, CompoundTag c) {
		String jenis = c.getStringOr("j", "");
		CompoundTag f = c.getCompoundOrEmpty("f");
		int targetId = f.getIntOr("target", f.getIntOr("anak", f.getIntOr("sakit", 0)));
		Warga target = targetId > 0 ? WargaApi.byId(targetId) : null;
		Component nama = target == null ? Component.translatable("kultivator.kota42.bicara.tolong.seseorang") : TokohKota.nama(target);
		Object jumlah = f.contains("nb") ? (Object) f.getIntOr("nb", 0) : (Object) jumlahTujuan(c);
		JsonObject o = data(jenis);
		if (o.has("teks") && o.get("teks").isJsonArray() && !o.getAsJsonArray("teks").isEmpty()) {
			JsonArray arr = o.getAsJsonArray("teks");
			JsonElement e = arr.get(Math.floorMod(w.id, arr.size()));
			return Component.translatable(e.getAsString(), nama, jumlah);
		}
		return Component.translatable("kultivator.kota42.bicara.tolong." + jenis + ".teks." + Math.floorMod(w.id, 2), nama, jumlah);
	}

	private static int jumlahTujuan(CompoundTag c) {
		ListTag o = c.getListOrEmpty("o");
		return o.isEmpty() ? 1 : o.getCompoundOrEmpty(0).getIntOr("n", 1);
	}

	private static Component teksTujuan(CompoundTag g) {
		Tugas t = Tugas.values()[Math.floorMod(g.getIntOr("t", 0), Tugas.values().length)];
		String a = g.getStringOr("a", "");
		int n = g.getIntOr("n", 1);
		Warga target = g.getIntOr("w", 0) > 0 ? WargaApi.byId(g.getIntOr("w", 0)) : null;
		Component nama = target == null ? Component.translatable("kultivator.kota42.bicara.tolong.seseorang") : TokohKota.nama(target);
		return switch (t) {
			case SERAH -> Component.translatable("kultivator.kota42.bicara.tolong.tujuan.serah", n, barangNama(a));
			case BAYAR -> Component.translatable("kultivator.kota42.bicara.tolong.tujuan.bayar." + (a.isEmpty() ? "umum" : a), n);
			case BICARA -> Component.translatable("kultivator.kota42.bicara.tolong.tujuan.bicara." + (a.isEmpty() ? "lapor" : a), nama);
			case TEMUKAN -> Component.translatable("kultivator.kota42.bicara.tolong.tujuan.temukan." + a, nama);
			case TEMANI -> Component.translatable("kultivator.kota42.bicara.tolong.tujuan.temani." + a, nama, n);
			case DUEL -> Component.translatable("kultivator.kota42.bicara.tolong.tujuan.duel." + a, n);
			case PERGI -> Component.translatable("kultivator.kota42.bicara.tolong.tujuan.pergi." + a, nama);
			case HADIR -> Component.translatable("kultivator.kota42.bicara.tolong.tujuan.hadir", nama);
		};
	}

	private static Component barangNama(String arg) {
		if (arg.startsWith("kat:")) {
			return Component.translatable("kultivator.kota42.kategori." + arg.substring(4));
		}
		if (arg.startsWith("#")) {
			return Component.translatable("kultivator.kota42.bicara.tolong.barang." + arg.substring(arg.indexOf(':') + 1));
		}
		Identifier id = Identifier.tryParse(arg);
		Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
		return item == null || item == Items.AIR ? Component.literal(arg) : item.getDefaultInstance().getHoverName();
	}

	private static @Nullable BlockPos posTujuan(CompoundTag c) {
		int step = langkah(c);
		ListTag o = c.getListOrEmpty("o");
		if (step < 0 || step >= o.size()) {
			return null;
		}
		CompoundTag g = o.getCompoundOrEmpty(step);
		long x = g.getLongOr("x", 0L);
		if (x != 0L) {
			return BlockPos.of(x);
		}
		Warga w = WargaApi.byId(g.getIntOr("w", 0));
		MinecraftServer server = KotaHidup.server();
		if (w == null || server == null || w.slot == Warga.TANPA_SLOT) {
			return null;
		}
		TempatHuni huni = Pemanggil.huni(WargaData.get(server).situsDari(w.id));
		KultivatorNpc body = huni == null ? null : huni.tubuhDari(w.slot);
		return body == null ? null : body.blockPosition();
	}

	// ================================================================== rewards

	private static int pita(ServerPlayer player) {
		return Barang.pita(player);
	}

	private static long imbalanNb(ServerPlayer player, String jenis, CompoundTag c) {
		int b = pita(player) + 1;
		return switch (jenis) {
			case KotaLembar.Dialog.TL_OBAT_ORTU -> 4L * b;
			case KotaLembar.Dialog.TL_PREMAN -> 3L * b;
			case KotaLembar.Dialog.TL_BARANG_DICOPET -> 2L * b;
			case KotaLembar.Dialog.TL_ANAK_HILANG -> 3L * b;
			default -> 0L;
		};
	}

	private static Component imbalanTeks(String jenis, long nb) {
		JsonObject o = data(jenis);
		return Component.translatable(o.has("imbalan") ? o.get("imbalan").getAsString() : "kultivator.kota42.bicara.tolong." + jenis + ".imbalan", nb);
	}

	/** Impression, memory, help count, gossip and feeling of a done request. */
	private static void terimaKasih(ServerPlayer player, long situs, Warga w, int kesan) {
		MinecraftServer server = player.level().getServer();
		long hari = KotaHidup.hari(server);
		Ingatan i = WargaApi.ingatan(w, player.getUUID());
		i.bantu++;
		i.ubah(kesan, Kenangan.DIBANTU, false, hari);
		WargaData data = WargaData.get(server);
		data.ubah();
		int idx = data.indeksPemain(player.getUUID());
		GosipWarga.lahir(player.level(), situs, "MENOLONG", Fakta.Rujuk.pemain(idx), Fakta.Rujuk.warga(w.id));
		Cakap.peristiwa(w, "dibantu", Fakta.Rujuk.pemain(idx), 1.0F);
	}

	private static void selesai(ServerPlayer player, long situs, CompoundTag c, @Nullable KultivatorNpc body) {
		MinecraftServer server = player.level().getServer();
		Warga w = WargaApi.byId(c.getIntOr("w", 0));
		c.putInt("st", SELESAI);
		lepasEntitas(server, c);
		KotaBicara.Data.get(server).ubah();
		if (w == null) {
			return;
		}
		String jenis = c.getStringOr("j", "");
		CompoundTag f = c.getCompoundOrEmpty("f");
		long nb = imbalanNb(player, jenis, c);
		int kesan = 10;
		switch (jenis) {
			case KotaLembar.Dialog.TL_OBAT_ORTU -> {
				kesan = 15;
				Warga sakit = WargaApi.byId(f.getIntOr("sakit", 0));
				if (sakit != null) {
					SiklusHidup.sembuh(sakit);
					sakit.kesehatan = Math.max(sakit.kesehatan, 70);
				}
			}
			case KotaLembar.Dialog.TL_KAYU_BAKAR -> Barang.beri(player, new ItemStack(KotaAcaraItems.SUP_HANGAT, 2));
			case KotaLembar.Dialog.TL_KUCING_HILANG -> kesan = 12;
			case KotaLembar.Dialog.TL_ANAK_HILANG -> {
				kesan = 25;
				WargaData.Situs s = WargaData.get(server).peek(situs);
				if (s != null) {
					for (Warga k : keluarga(s, w)) {
						if (k != w && k.hidup()) {
							terimaKasih(player, situs, k, 10);
						}
					}
				}
			}
			case KotaLembar.Dialog.TL_SURAT_KEKASIH -> {
				kesan = 8;
				Warga kekasih = WargaApi.byId(f.getIntOr("target", 0));
				if (kekasih != null) {
					perkuat(w, kekasih, 15);
					perkuat(kekasih, w, 15);
				}
			}
			case KotaLembar.Dialog.TL_DAMAIKAN -> damai(player, situs, w, WargaApi.byId(f.getIntOr("target", 0)));
			case KotaLembar.Dialog.TL_RENTENIR -> {
				kesan = 20;
				Warga lintah = WargaApi.byId(f.getIntOr("target", 0));
				if (lintah != null) {
					WargaApi.ubahKesan(lintah, player, f.getBooleanOr("ditekan", false) ? -30 : 5, "tolong_rentenir", null);
				}
			}
			case KotaLembar.Dialog.TL_PREMAN -> {
				kesan = 15;
				KotaData.get(server).addReputation(situs, player.getUUID(), 2);
			}
			case KotaLembar.Dialog.TL_BARANG_DICOPET -> kesan = 15;
			case KotaLembar.Dialog.TL_BANTU_PANEN -> {
				kesan = 8;
				Barang.beri(player, new ItemStack(Items.WHEAT, 12));
				Barang.beri(player, new ItemStack(Items.CARROT, 6));
				Barang.beri(player, new ItemStack(Items.POTATO, 6));
			}
			case KotaLembar.Dialog.TL_AJARI_ANAK -> {
				Warga anak = WargaApi.byId(f.getIntOr("anak", 0));
				if (anak != null) {
					anak.ahli = Math.min(100, anak.ahli + 10);
					WargaData.Situs s = WargaData.get(server).peek(situs);
					if (s != null) {
						for (Warga k : keluarga(s, anak)) {
							if (k != w && k.hidup()) {
								WargaApi.ubahKesan(k, player, 10, "tolong_ajari", null);
							}
						}
					}
				}
			}
			case KotaLembar.Dialog.TL_CARI_JODOH -> {
				Warga calon = WargaApi.byId(f.getIntOr("target", 0));
				if (calon != null && calon.kelamin != w.kelamin) {
					long hari = KotaHidup.hari(server);
					int maks = KotaConfig.warga().hubunganMaks;
					w.setHubungan(JenisHubungan.kemas(calon.id, JenisHubungan.KENALAN, 30, hari), maks);
					calon.setHubungan(JenisHubungan.kemas(w.id, JenisHubungan.KENALAN, 30, hari), maks);
				}
			}
			case KotaLembar.Dialog.TL_KABAR_ANAK_SEKTE -> kesan = 15;
			case KotaLembar.Dialog.TL_PINJAM_UANG -> {
				kesan = 8;
				Pinjaman.catatPinjamkan(player, w, f.getIntOr("nb", 0), f.getIntOr("bunga", 0));
			}
			case KotaLembar.Dialog.TL_UNDANGAN -> Barang.beri(player, new ItemStack(KotaAcaraItems.KUE_PENGANTIN, 1));
			default -> {
			}
		}
		terimaKasih(player, situs, w, kesan);
		CompoundTag bagian = KotaBicara.Data.get(server).bagian(KotaBicara.Data.TOLONG);
		CompoundTag pc = bagian.getCompoundOrEmpty("p_" + player.getUUID());
		pc.putInt("s", pc.getIntOr("s", 0) + 1);
		bagian.put("p_" + player.getUUID(), pc);
		KotaBicara.Data.get(server).ubah();
		if (pc.getIntOr("s", 0) >= 10) {
			Advancements.grant(player, "tempat/kota42_tolong");
		}
		if (nb > 0L) {
			long paid = KasKota.ambil(server, situs, nb);
			if (paid > 0L) {
				NilaiBatu.give(player, paid);
			}
			if (paid < nb) {
				Barang.beri(player, new ItemStack(Items.BREAD, (int) Math.max(1L, (nb - paid + 1L) / 2L)));
			}
		}
		Cakap.tepati(w, player, janjiKata(jenis));
		TujuanKota.tolong(player, jenis);
		Component line = Component.translatable("kultivator.kota42.bicara.tolong.terima_kasih." + Math.floorMod(w.id + (int) c.getLongOr("d", 0L), 3));
		Msg.chat(player, Component.translatable("kultivator.kota42.bicara.tolong.selesai_chat", TokohKota.nama(w), nama(jenis)).withStyle(ChatFormatting.GREEN));
		KultivatorNpc npc = body != null ? body : tubuh(server, w);
		if (npc != null) {
			npc.say(line, 100);
			HidupApi.klip(npc, GerakId.HORMAT, 0, 40);
			if (VoiceEvents.exists("kota42.terima_kasih")) {
				Voice.say(npc, "kota42.terima_kasih", null);
			}
		}
	}

	private static String janjiKata(String jenis) {
		return switch (jenis) {
			case KotaLembar.Dialog.TL_OBAT_ORTU -> "obat";
			case KotaLembar.Dialog.TL_PINJAM_UANG, KotaLembar.Dialog.TL_RENTENIR -> "uang";
			default -> "janji";
		};
	}

	private static void perkuat(Warga a, Warga b, int delta) {
		long bond = a.hubunganDengan(b.id);
		if (bond != 0L) {
			a.setHubungan(JenisHubungan.denganKekuatan(bond, Math.min(100, JenisHubungan.kekuatan(bond) + delta)), KotaConfig.warga().hubunganMaks);
		}
	}

	/** The outcome of a reconciliation: warm, fair tellers and a respected player make peace more likely. */
	private static void damai(ServerPlayer player, long situs, Warga a, @Nullable Warga b) {
		if (b == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		RandomSource r = RandomSource.create(a.id * 31L + b.id * 17L + KotaHidup.hari(server));
		double p = 0.45D;
		for (Warga x : List.of(a, b)) {
			String watak = watak(x);
			p += switch (watak) {
				case "ramah", "bijak", "jujur" -> 0.12D;
				case "sombong", "licik" -> -0.12D;
				default -> 0.0D;
			};
			p += WargaApi.ingatan(x, player.getUUID()).kesan / 400.0D;
		}
		long hari = KotaHidup.hari(server);
		int maks = KotaConfig.warga().hubunganMaks;
		boolean ok = r.nextDouble() < p;
		int strength = ok ? 0 : Math.max(-100, JenisHubungan.kekuatan(a.hubunganDengan(b.id)) - 10);
		JenisHubungan jenis = ok ? JenisHubungan.KENALAN : JenisHubungan.MUSUH;
		a.setHubungan(JenisHubungan.kemas(b.id, jenis, strength, hari), maks);
		b.setHubungan(JenisHubungan.kemas(a.id, jenis, strength, hari), maks);
		WargaApi.ubahKesan(b, player, ok ? 5 : -5, "tolong_damai", null);
		Msg.chat(player, Component.translatable(ok ? "kultivator.kota42.bicara.tolong.damai_ok" : "kultivator.kota42.bicara.tolong.damai_gagal",
				TokohKota.nama(a), TokohKota.nama(b)).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
	}

	// ================================================================== actions

	/** [Terima] on request {@code id} of {@code w}. */
	static BicaraWarga.Balasan terima(ServerPlayer player, KultivatorNpc npc, Warga w, String id) {
		MinecraftServer server = player.level().getServer();
		long situs = WargaData.get(server).situsDari(w.id);
		CompoundTag c = cariTeks(server, situs, id);
		if (c == null || c.getIntOr("w", 0) != w.id || c.getIntOr("st", 0) != TERBUKA) {
			return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.tolong.tidak_ada"));
		}
		long hari = KotaHidup.hari(server);
		if (diterimaHariIni(server, player.getUUID(), hari) >= KotaConfig.bicara().permintaanPerHari) {
			return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.tolong.batas_harian", KotaConfig.bicara().permintaanPerHari));
		}
		c.putInt("st", DITERIMA);
		c.putString("p", player.getUUID().toString());
		CompoundTag pc = KotaBicara.Data.get(server).bagian(KotaBicara.Data.TOLONG).getCompoundOrEmpty("p_" + player.getUUID());
		if (pc.getLongOr("h", -1L) != hari) {
			pc.putLong("h", hari);
			pc.putInt("n", 0);
		}
		pc.putInt("n", pc.getIntOr("n", 0) + 1);
		KotaBicara.Data.get(server).bagian(KotaBicara.Data.TOLONG).put("p_" + player.getUUID(), pc);
		KotaBicara.Data.get(server).ubah();
		Cakap.janji(w, player, "MENOLONG", c.getLongOr("b", hari), janjiKata(c.getStringOr("j", "")));
		HidupApi.klip(npc, GerakId.HORMAT, 0, 40);
		Component line = Component.translatable("kultivator.kota42.bicara.tolong.terima." + Math.floorMod(w.id, 3));
		npc.say(line, 100);
		return new BicaraWarga.Balasan(line, Component.translatable("kultivator.kota42.bicara.tolong.diterima", nama(c.getStringOr("j", ""))), true);
	}

	/** [Serahkan] on request {@code id}: hands over items or pays NB for the current SERAH/BAYAR objective. */
	static BicaraWarga.Balasan serahkan(ServerPlayer player, KultivatorNpc npc, Warga w, String id) {
		MinecraftServer server = player.level().getServer();
		long situs = WargaData.get(server).situsDari(w.id);
		CompoundTag c = cariTeks(server, situs, id);
		if (c == null || c.getIntOr("st", 0) != DITERIMA || !player.getUUID().equals(pemain(c))) {
			return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.tolong.tidak_ada"));
		}
		int step = langkah(c);
		ListTag o = c.getListOrEmpty("o");
		if (step < 0 || step >= o.size()) {
			return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.tolong.belum"));
		}
		CompoundTag g = o.getCompoundOrEmpty(step);
		Tugas t = Tugas.values()[Math.floorMod(g.getIntOr("t", 0), Tugas.values().length)];
		int[] k = c.getIntArray("k").orElse(new int[o.size()]);
		int need = g.getIntOr("n", 1) - k[step];
		if (t == Tugas.SERAH) {
			int took = ambil(player, g.getStringOr("a", ""), need);
			if (took <= 0) {
				if ("kat:obat".equals(g.getStringOr("a", ""))) {
					// no medicine at hand: pay the healer instead (42.5.6 "atau bayar tabib")
					long fee = 6L * (pita(player) + 1);
					if (NilaiBatu.pay(player, fee)) {
						KasKota.pajak(server, situs, fee, "tabib");
						took = need;
					} else {
						return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.tolong.tanpa_obat", fee));
					}
				} else {
					return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.tolong.tanpa_barang", need, barangNama(g.getStringOr("a", ""))));
				}
			}
			k[step] += took;
		} else if (t == Tugas.BAYAR) {
			if ("pinjam".equals(g.getStringOr("a", "")) && !KotaConfig.bicara().pinjaman) {
				return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.pinjam.mati"));
			}
			if (!NilaiBatu.pay(player, need)) {
				return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.tolong.kurang_uang", need));
			}
			if ("rentenir".equals(g.getStringOr("a", ""))) {
				KasKota.pajak(server, situs, need, "utang_warga");
			}
			k[step] += need;
		} else if (t == Tugas.BICARA && g.getIntOr("w", 0) == w.id) {
			k[step] = g.getIntOr("n", 1);
		} else {
			return BicaraWarga.Balasan.gagal(Component.translatable("kultivator.kota42.bicara.tolong.belum"));
		}
		c.putIntArray("k", k);
		KotaBicara.Data.get(server).ubah();
		if (langkah(c) < 0) {
			selesai(player, situs, c, npc);
			return new BicaraWarga.Balasan(null, Component.translatable("kultivator.kota42.bicara.tolong.beres", nama(c.getStringOr("j", ""))), true);
		}
		return new BicaraWarga.Balasan(null, Component.translatable("kultivator.kota42.bicara.tolong.maju"), true);
	}

	/** Takes up to {@code n} items matching {@code arg} from the inventory; returns how many. */
	private static int ambil(ServerPlayer player, String arg, int n) {
		int took = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && took < n; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && cocok(s, arg)) {
				int take = Math.min(n - took, s.getCount());
				s.shrink(take);
				took += take;
			}
		}
		if (took > 0) {
			inv.setChanged();
		}
		return took;
	}

	static boolean cocok(ItemStack s, String arg) {
		if (arg.startsWith("kat:")) {
			Kategori k = Kategori.byId(arg.substring(4));
			return k != null && Kategori.dari(s) == k;
		}
		if (arg.startsWith("#")) {
			Identifier id = Identifier.tryParse(arg.substring(1));
			return id != null && s.is(TagKey.create(Registries.ITEM, id));
		}
		return BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(arg);
	}

	/** A talk with {@code w}: every accepted request of the player whose current step is "talk with w" advances. */
	static void bicara(ServerPlayer player, KultivatorNpc npc, Warga w) {
		MinecraftServer server = player.level().getServer();
		semua(server, (situs, c) -> {
			if (c.getIntOr("st", 0) != DITERIMA || !player.getUUID().equals(pemain(c))) {
				return;
			}
			int step = langkah(c);
			ListTag o = c.getListOrEmpty("o");
			if (step < 0 || step >= o.size()) {
				return;
			}
			CompoundTag g = o.getCompoundOrEmpty(step);
			boolean talk = g.getIntOr("t", 0) == Tugas.BICARA.ordinal() && g.getIntOr("w", 0) == w.id;
			// pressing the loan shark (Nama Baik >= 40) settles a rentenir request without paying
			boolean tekan = g.getIntOr("t", 0) == Tugas.BAYAR.ordinal() && "rentenir".equals(g.getStringOr("a", ""))
					&& c.getCompoundOrEmpty("f").getIntOr("target", 0) == w.id
					&& KotaData.get(server).reputation(situs, player.getUUID()) >= 40;
			if (!talk && !tekan) {
				return;
			}
			int[] k = c.getIntArray("k").orElse(new int[o.size()]);
			k[step] = g.getIntOr("n", 1);
			c.putIntArray("k", k);
			if (tekan) {
				c.getCompoundOrEmpty("f").putBoolean("ditekan", true);
			}
			KotaBicara.Data.get(server).ubah();
			String a = g.getStringOr("a", "");
			if (!a.isEmpty()) {
				Component line = Component.translatable("kultivator.kota42.bicara.tolong.dengar." + (tekan ? "tekan" : a));
				npc.say(line, 100);
				Msg.chat(player, Component.empty().append(TokohKota.nama(w)).append(": ").append(line));
			}
			if (langkah(c) < 0) {
				selesai(player, situs, c, null);
			}
		});
	}

	// ================================================================== tick: finding, staying, travelling, thugs

	static void tick(MinecraftServer server, long now) {
		ServerLevel level = server.overworld();
		semua(server, (situs, c) -> {
			if (c.getIntOr("st", 0) != DITERIMA) {
				return;
			}
			UUID owner = pemain(c);
			ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
			if (player == null || player.level() != level) {
				return;
			}
			int step = langkah(c);
			ListTag o = c.getListOrEmpty("o");
			if (step < 0 || step >= o.size()) {
				return;
			}
			CompoundTag g = o.getCompoundOrEmpty(step);
			Tugas t = Tugas.values()[Math.floorMod(g.getIntOr("t", 0), Tugas.values().length)];
			switch (t) {
				case TEMUKAN -> temukan(level, player, situs, c, g, step);
				case TEMANI -> temani(level, player, situs, c, g, step);
				case PERGI -> {
					BlockPos at = BlockPos.of(g.getLongOr("x", 0L));
					if (player.blockPosition().distSqr(at) <= 24.0D * 24.0D) {
						maju(player, situs, c, step, g.getIntOr("n", 1));
						Msg.actionBar(player, Component.translatable("kultivator.kota42.bicara.tolong.surat_sampai").withStyle(ChatFormatting.GREEN));
					}
				}
				case DUEL -> preman(level, player, situs, c, g, now);
				default -> {
				}
			}
		});
	}

	private static void maju(ServerPlayer player, long situs, CompoundTag c, int step, int by) {
		ListTag o = c.getListOrEmpty("o");
		int[] k = c.getIntArray("k").orElse(new int[o.size()]);
		k[step] = Math.min(o.getCompoundOrEmpty(step).getIntOr("n", 1), k[step] + by);
		c.putIntArray("k", k);
		KotaBicara.Data.get(player.level().getServer()).ubah();
		if (langkah(c) < 0) {
			selesai(player, situs, c, null);
		}
	}

	/** The lost cat or child stands at its spot while the player is within 64 blocks; within 3 blocks it is found. */
	private static void temukan(ServerLevel level, ServerPlayer player, long situs, CompoundTag c, CompoundTag g, int step) {
		BlockPos at = BlockPos.of(g.getLongOr("x", 0L));
		if (player.blockPosition().distSqr(at) > 64.0D * 64.0D || !level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
			return;
		}
		Entity e = entitas(level, c);
		if (e == null) {
			e = munculkan(level, situs, c, g, at);
			if (e == null) {
				return;
			}
		}
		if (player.distanceToSqr(e) <= 9.0D) {
			boolean cat = "kucing".equals(g.getStringOr("a", ""));
			Msg.actionBar(player, Component.translatable(cat ? "kultivator.kota42.bicara.tolong.kucing_ketemu" : "kultivator.kota42.bicara.tolong.anak_ketemu")
					.withStyle(ChatFormatting.GREEN));
			if (e instanceof KultivatorNpc child) {
				child.say(Component.translatable("kultivator.kota42.bicara.tolong.anak_lega." + Math.floorMod(child.getId(), 2)), 80);
			}
			lepasEntitas(level.getServer(), c);
			maju(player, situs, c, step, 1);
		}
	}

	private static @Nullable Entity munculkan(ServerLevel level, long situs, CompoundTag c, CompoundTag g, BlockPos at) {
		BlockPos ground = TempatApi.groundSpot(level, at.getX(), at.getZ(), at.getY());
		BlockPos pos = ground != null ? ground : at;
		Entity e;
		if ("kucing".equals(g.getStringOr("a", ""))) {
			e = HewanEntities.KUCING_KOTA.create(level, EntitySpawnReason.EVENT);
			if (e == null) {
				return null;
			}
			e.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
			if (!level.addFreshEntity(e)) {
				return null;
			}
		} else {
			Warga anak = WargaApi.byId(g.getIntOr("w", 0));
			KultivatorNpc npc = HidupApi.tubuhStaf(level, situs, NpcRole.ANAK, pos, level.getRandom().nextFloat() * 360.0F, anak == null ? 0 : anak.look);
			if (npc == null) {
				return null;
			}
			if (anak != null) {
				npc.setPlate(TokohKota.nama(anak));
			}
			npc.memory().putBoolean("hold", true);
			e = npc;
		}
		ListTag ids = new ListTag();
		ids.add(StringTag.valueOf(e.getUUID().toString()));
		c.put("e", ids);
		MILIK.put(e.getUUID(), situs + ":" + c.getIntOr("i", 0));
		return e;
	}

	private static @Nullable Entity entitas(ServerLevel level, CompoundTag c) {
		for (Tag t : c.getListOrEmpty("e")) {
			try {
				Entity e = level.getEntity(UUID.fromString(t.asString().orElse("")));
				if (e != null && e.isAlive()) {
					return e;
				}
			} catch (IllegalArgumentException ignored) {
				// not a uuid
			}
		}
		return null;
	}

	private static void lepasEntitas(MinecraftServer server, CompoundTag c) {
		for (Tag t : c.getListOrEmpty("e")) {
			try {
				UUID id = UUID.fromString(t.asString().orElse(""));
				MILIK.remove(id);
				TANTANG.remove(id);
				Entity e = server.overworld().getEntity(id);
				if (e != null) {
					e.discard();
				}
			} catch (IllegalArgumentException ignored) {
				// not a uuid
			}
		}
		c.remove("e");
	}

	/** One second beside the resident's body counts; the progress shows in the action bar. */
	private static void temani(ServerLevel level, ServerPlayer player, long situs, CompoundTag c, CompoundTag g, int step) {
		Warga target = WargaApi.byId(g.getIntOr("w", 0));
		if (target == null || target.slot == Warga.TANPA_SLOT) {
			return;
		}
		TempatHuni huni = Pemanggil.huni(WargaData.get(level.getServer()).situsDari(target.id));
		KultivatorNpc body = huni == null ? null : huni.tubuhDari(target.slot);
		if (body == null || player.distanceToSqr(body) > 36.0D) {
			return;
		}
		int tt = c.getIntOr("tt", 0) + 1;
		c.putInt("tt", tt);
		int maks = g.getIntOr("n", TEMANI_DETIK);
		HidupApi.tatap(body, player);
		if (tt % 15 == 1) {
			body.say(Component.translatable("kultivator.kota42.bicara.tolong.temani." + g.getStringOr("a", "panen") + "." + Math.floorMod(tt / 15, 3)), 80);
			HidupApi.klip(body, "ajar".equals(g.getStringOr("a", "")) ? GerakId.ANGGUK_DENGAR : GerakId.BICARA_TANGAN, 0, 40);
		}
		Msg.actionBar(player, Component.translatable("kultivator.kota42.bicara.tolong.temani_hitung", tt, maks).withStyle(ChatFormatting.YELLOW));
		if (tt >= maks) {
			c.remove("tt");
			maju(player, situs, c, step, maks);
		} else {
			KotaBicara.Data.get(level.getServer()).ubah();
		}
	}

	/** Thugs (or the pickpocket) wait at their spot; a player walking up within 5 blocks is challenged to a friendly duel. */
	private static void preman(ServerLevel level, ServerPlayer player, long situs, CompoundTag c, CompoundTag g, long now) {
		BlockPos at = BlockPos.of(g.getLongOr("x", 0L));
		if (player.blockPosition().distSqr(at) > 48.0D * 48.0D || !level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
			return;
		}
		int step = langkah(c);
		int[] k = c.getIntArray("k").orElse(new int[0]);
		int left = g.getIntOr("n", 1) - (step >= 0 && step < k.length ? k[step] : 0);
		List<KultivatorNpc> live = new ArrayList<>();
		for (Tag t : c.getListOrEmpty("e")) {
			try {
				Entity e = level.getEntity(UUID.fromString(t.asString().orElse("")));
				if (e instanceof KultivatorNpc npc && npc.isAlive()) {
					live.add(npc);
				}
			} catch (IllegalArgumentException ignored) {
				// not a uuid
			}
		}
		ListTag ids = new ListTag();
		for (KultivatorNpc npc : live) {
			ids.add(StringTag.valueOf(npc.getUUID().toString()));
		}
		boolean copet = "copet".equals(g.getStringOr("a", ""));
		BlockPos ground = TempatApi.groundSpot(level, at.getX(), at.getZ(), at.getY());
		BlockPos base = ground != null ? ground : at;
		while (live.size() < left) {
			BlockPos spot = base.offset(live.size() * 2 - 1, 0, live.size() % 2 == 0 ? 1 : -1);
			KultivatorNpc thug = HidupApi.tubuhStaf(level, situs, NpcRole.SAINGAN, spot, level.getRandom().nextFloat() * 360.0F,
					(int) (situs ^ c.getIntOr("i", 0) * 7919L) + live.size());
			if (thug == null) {
				break;
			}
			thug.setPlate(Component.translatable(copet ? "kultivator.kota42.bicara.tolong.plat_copet" : "kultivator.kota42.bicara.tolong.plat_preman"));
			thug.memory().putBoolean("hold", true);
			MILIK.put(thug.getUUID(), situs + ":" + c.getIntOr("i", 0));
			ids.add(StringTag.valueOf(thug.getUUID().toString()));
			live.add(thug);
		}
		c.put("e", ids);
		for (KultivatorNpc thug : live) {
			if (player.distanceToSqr(thug) > 25.0D || now - TANTANG.getOrDefault(thug.getUUID(), -TANTANG_JEDA * 2L) < TANTANG_JEDA) {
				continue;
			}
			TANTANG.put(thug.getUUID(), now);
			HidupApi.tatap(thug, player);
			thug.say(Component.translatable(copet ? "kultivator.kota42.bicara.tolong.copet_seru" : "kultivator.kota42.bicara.tolong.preman_seru."
					+ Math.floorMod(thug.getId(), 3)), 80);
			int gap = Cultivators.get(player).realm().ordinal() - thug.realm();
			Component why = DuelChallenges.challenge(player, thug, null, DuelRuleSet.suggested(gap));
			if (why != null) {
				Msg.actionBar(player, why.copy().withStyle(ChatFormatting.RED));
			}
			break;
		}
	}

	private static void majuDuel(ServerPlayer player, String key, Entity loser) {
		MinecraftServer server = player.level().getServer();
		int colon = key.indexOf(':');
		long situs;
		int id;
		try {
			situs = Long.parseLong(key.substring(0, colon));
			id = Integer.parseInt(key.substring(colon + 1));
		} catch (RuntimeException e) {
			return;
		}
		CompoundTag c = cari(server, situs, id);
		if (c == null || c.getIntOr("st", 0) != DITERIMA || !player.getUUID().equals(pemain(c))) {
			return;
		}
		int step = langkah(c);
		ListTag o = c.getListOrEmpty("o");
		if (step < 0 || o.getCompoundOrEmpty(step).getIntOr("t", 0) != Tugas.DUEL.ordinal()) {
			return;
		}
		MILIK.remove(loser.getUUID());
		TANTANG.remove(loser.getUUID());
		if (loser instanceof KultivatorNpc thug) {
			thug.say(Component.translatable("kultivator.kota42.bicara.tolong.preman_kabur"), 60);
		}
		loser.discard();
		maju(player, situs, c, step, 1);
	}

	/** A ceremony phase in {@code situs}: invitations of players standing inside the place are fulfilled. */
	private static void hadir(MinecraftServer server, long situs, JenisUpacara jenis) {
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni == null) {
			return;
		}
		for (Tag t : daftar(server, situs)) {
			CompoundTag c = (CompoundTag) t;
			if (c.getIntOr("st", 0) != DITERIMA) {
				continue;
			}
			int step = langkah(c);
			ListTag o = c.getListOrEmpty("o");
			if (step < 0 || step >= o.size()) {
				continue;
			}
			CompoundTag g = o.getCompoundOrEmpty(step);
			if (g.getIntOr("t", 0) != Tugas.HADIR.ordinal() || !g.getStringOr("a", "").equals(jenis.name())) {
				continue;
			}
			UUID owner = pemain(c);
			ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
			if (player != null && huni.kotak.isInside(player.blockPosition())) {
				maju(player, situs, c, step, 1);
			}
		}
	}

	// ================================================================== storage helpers

	private static ListTag daftar(MinecraftServer server, long situs) {
		CompoundTag site = KotaBicara.Data.get(server).situs(KotaBicara.Data.TOLONG, situs);
		ListTag list = site.getListOrEmpty("L");
		if (!site.contains("L")) {
			site.put("L", list);
		}
		return list;
	}

	private static @Nullable CompoundTag cari(MinecraftServer server, long situs, int id) {
		for (Tag t : daftar(server, situs)) {
			if (t instanceof CompoundTag c && c.getIntOr("i", -1) == id) {
				return c;
			}
		}
		return null;
	}

	/** By the sheet id {@code tolong:SITUS:WARGA:HARI:ID}. */
	private static @Nullable CompoundTag cariTeks(MinecraftServer server, long situs, String id) {
		String[] parts = id.split(":");
		if (parts.length < 5) {
			return null;
		}
		try {
			long s = Long.parseLong(parts[1]);
			if (s != situs) {
				return null;
			}
			return cari(server, s, Integer.parseInt(parts[4]));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private interface Kunjung {
		void lihat(long situs, CompoundTag c);
	}

	/** Visits every stored request of every place (sections are small: at most a few per place). */
	private static void semua(MinecraftServer server, Kunjung k) {
		CompoundTag part = KotaBicara.Data.get(server).bagian(KotaBicara.Data.TOLONG);
		for (String key : List.copyOf(part.keySet())) {
			if (!key.startsWith("kota_")) {
				continue;
			}
			long situs;
			try {
				situs = Long.parseLong(key.substring(5));
			} catch (NumberFormatException e) {
				continue;
			}
			ListTag list = part.getCompoundOrEmpty(key).getListOrEmpty("L");
			for (int i = 0; i < list.size(); i++) {
				k.lihat(situs, list.getCompoundOrEmpty(i));
			}
		}
	}

	/** Index of the first unfinished objective, -1 when all are done. */
	static int langkah(CompoundTag c) {
		ListTag o = c.getListOrEmpty("o");
		int[] k = c.getIntArray("k").orElse(new int[o.size()]);
		for (int i = 0; i < o.size(); i++) {
			if ((i < k.length ? k[i] : 0) < o.getCompoundOrEmpty(i).getIntOr("n", 1)) {
				return i;
			}
		}
		return -1;
	}

	private static @Nullable UUID pemain(CompoundTag c) {
		String s = c.getStringOr("p", "");
		if (s.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static int diterimaHariIni(MinecraftServer server, UUID player, long hari) {
		CompoundTag pc = KotaBicara.Data.get(server).bagian(KotaBicara.Data.TOLONG).getCompoundOrEmpty("p_" + player);
		return pc.getLongOr("h", -1L) == hari ? pc.getIntOr("n", 0) : 0;
	}

	private static JsonObject data(String jenis) {
		JsonObject o = KotaBicara.muatan().tolong().get(jenis);
		return o == null ? new JsonObject() : o;
	}

	private static @Nullable KultivatorNpc tubuh(MinecraftServer server, Warga w) {
		if (w.slot == Warga.TANPA_SLOT) {
			return null;
		}
		TempatHuni huni = Pemanggil.huni(WargaData.get(server).situsDari(w.id));
		return huni == null ? null : huni.tubuhDari(w.slot);
	}

	// ================================================================== test helpers

	static List<Component> daftarUji(ServerPlayer player) {
		List<Component> out = new ArrayList<>();
		MinecraftServer server = player.level().getServer();
		semua(server, (situs, c) -> {
			Warga w = WargaApi.byId(c.getIntOr("w", 0));
			out.add(Component.translatable("kultivator.kota42.bicara.uji.tolong_baris", c.getIntOr("i", 0), nama(c.getStringOr("j", "")),
					w == null ? Component.literal("?") : TokohKota.nama(w), c.getIntOr("st", 0), langkah(c), c.getLongOr("b", 0L), situs));
		});
		return out;
	}

	static int selesaikanUji(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		int[] n = {0};
		List<Object[]> done = new ArrayList<>();
		semua(server, (situs, c) -> {
			if (c.getIntOr("st", 0) == DITERIMA && player.getUUID().equals(pemain(c))) {
				ListTag o = c.getListOrEmpty("o");
				int[] k = new int[o.size()];
				for (int i = 0; i < o.size(); i++) {
					k[i] = o.getCompoundOrEmpty(i).getIntOr("n", 1);
				}
				c.putIntArray("k", k);
				done.add(new Object[] {situs, c});
			}
		});
		for (Object[] d : done) {
			selesai(player, (long) d[0], (CompoundTag) d[1], null);
			n[0]++;
		}
		return n[0];
	}

	private PermintaanWarga() {
	}
}
