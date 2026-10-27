package dev.kultivator.dunia.kota42.sekte;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.tumbuh.Pembangun;
import dev.kultivator.dunia.kota42.warga.ArsipWarga;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.kotabesar.GelombangBinatang;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Disciples sent on town duty (DESIGN 42.8.3, contract C42.E1), on the 39 independent-disciple system: the disciple goes
 * into the hall's away store (reason {@link #ALASAN}) for its rotation, and while a player is near the town a town body of
 * 39 ({@code DiscipleEntity.Role.JELMAAN}, never saved) shows it at its post.
 * <ul>
 * <li>{@link #JAGA_KOTA} (a town the hall protects, 3-day rotation, at most 4): at a guard point by the town edge in the
 * daytime walking between two of them, standing watch at night; pickpockets halve, the defence strength of
 * {@link SerbuanKota} grows by the disciple's power.</li>
 * <li>{@link #KERJA_KOTA} (7-day rotation): the keeper of the hall's recruitment office ({@link KantorRekrut}: applicants
 * x1.25) or of its branch ({@link CabangKota}: the branch's work). A player's shop keeps its own disciple clerk (42C
 * {@code TokoPemain.pekerjakanMurid}). With neither, while the town is being built or rebuilt ({@link Pembangun}) the
 * disciple goes to the building site as a builder (post {@link #SASARAN_BANGUN}, at most {@link Pembangun#MURID_MAKS}
 * per town): it carries planks and hammers beside the destroyed houses or the plot, each one a third of a crew
 * ({@link Pembangun#faktorMurid}, the "laju +1 regu" of 42.9.5), and comes home when the work is over.</li>
 * <li>{@link #BELANJA} is 39's own shopping journey (39.6.6); here only the town prices apply (42C).</li>
 * </ul>
 * On return ({@link #kembali}) the disciple gets a small share of a bar per day served (39 scale, at most 0.3 bars), a
 * diary line, maybe a friend in the town ({@code sahabatMurid} on the resident's side, {@code murid:<soul>}), and a wound
 * when a defence went badly ({@link #tandaiLuka}). The master may call one home early ({@link #tarik}).
 * <p>
 * State: {@code KotaSekteData.bagian("murid")[soul]}: {@code o} owner, {@code situs}, {@code tugas}, {@code sasaran},
 * {@code mulai}, {@code sampai} (world day), {@code nama}, {@code ranah}, {@code kuat}, {@code luka}, {@code pulang}.
 */
public final class TugasKotaMurid {

	/** AwayStore reason of a disciple on town duty. */
	public static final String ALASAN = "kota42.tugas";
	public static final String JAGA_KOTA = "jaga_kota";
	public static final String KERJA_KOTA = "kerja_kota";
	public static final String BELANJA = "belanja";
	/** Posts: the town watch, the recruitment office, the branch. */
	public static final String SASARAN_JAGA = "jaga";
	public static final String SASARAN_KANTOR = "kantor";
	public static final String SASARAN_CABANG = "cabang";
	/** Post of a builder disciple (rebuild project or growing plot). */
	public static final String SASARAN_BANGUN = "bangun";
	public static final int ROTASI_JAGA = 3;
	public static final int ROTASI_KERJA = 7;
	public static final int JAGA_MAKS = 4;
	static final double JARAK_TAMPIL = 96.0D;

	/**
	 * One task: place, task id ({@link #JAGA_KOTA} / {@link #KERJA_KOTA}), until world day; then the disciple, its master, the
	 * post ({@link #SASARAN_JAGA} / {@link #SASARAN_KANTOR} / {@link #SASARAN_CABANG}), the name shown, realm ordinal and
	 * power step (when it left).
	 */
	public record Tugas(long situs, String tugas, long sampai, UUID soul, UUID pemilik, String sasaran, String nama, int ranah, int kekuatan) {
	}

	/** One town body shown. */
	private static final class Tubuh {
		final DiscipleEntity body;
		final UUID soul;
		long berikut;
		int titik;
		long hormat;
		boolean berjalan;

		Tubuh(DiscipleEntity body, UUID soul) {
			this.body = body;
			this.soul = soul;
		}
	}

	private static final Map<UUID, Tubuh> TUBUH = new HashMap<>();

	static void lupakan() {
		for (Tubuh t : TUBUH.values()) {
			if (!t.body.isRemoved()) {
				t.body.discard();
			}
		}
		TUBUH.clear();
	}

	// ================================================================== reading (C42.E1)

	/** The town task of disciple {@code soul}, or null. */
	public static @Nullable Tugas tugas(UUID soul) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || soul == null) {
			return null;
		}
		CompoundTag t = bagian(server).getCompoundOrEmpty(soul.toString());
		return t.isEmpty() || t.getBooleanOr("pulang", false) ? null : baca(soul, t);
	}

	/** Every running task of {@code owner}. */
	public static List<Tugas> milik(MinecraftServer server, UUID owner) {
		List<Tugas> out = new ArrayList<>();
		for (Tugas t : semua(server)) {
			if (t.pemilik().equals(owner)) {
				out.add(t);
			}
		}
		return out;
	}

	/** The keeper on post {@code sasaran} of {@code owner} in {@code situs}, or null. */
	public static @Nullable Tugas penjagaDi(long situs, String sasaran, UUID owner) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || owner == null) {
			return null;
		}
		for (Tugas t : semua(server)) {
			if (t.situs() == situs && t.sasaran().equals(sasaran) && t.pemilik().equals(owner)) {
				return t;
			}
		}
		return null;
	}

	/** The disciples of {@code owner} keeping the watch of {@code situs}. */
	public static List<Tugas> penjagaKota(MinecraftServer server, long situs, UUID owner) {
		List<Tugas> out = new ArrayList<>();
		for (Tugas t : semua(server)) {
			if (t.situs() == situs && SASARAN_JAGA.equals(t.sasaran()) && t.pemilik().equals(owner)) {
				out.add(t);
			}
		}
		return out;
	}

	/** Builder disciples (any hall) at work in {@code situs} now. */
	public static int pembangun(MinecraftServer server, long situs) {
		if (server == null || !KotaConfig.sekte().aktif || !KotaConfig.sekte().murid) {
			return 0;
		}
		int n = 0;
		for (Tugas t : semua(server)) {
			if (t.situs() == situs && SASARAN_BANGUN.equals(t.sasaran())) {
				n++;
			}
		}
		return n;
	}

	/** True when sect disciples keep the watch of {@code situs} (42D-P: pickpockets x0.5, shop theft 0). */
	public static boolean dijaga(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || !KotaConfig.sekte().aktif || !KotaConfig.sekte().murid) {
			return false;
		}
		for (Tugas t : semua(server)) {
			if (t.situs() == situs && SASARAN_JAGA.equals(t.sasaran())) {
				return true;
			}
		}
		return false;
	}

	/** The friend a disciple made in a town ({@code sahabatMurid} on the resident's side), or null. */
	public static @Nullable Warga sahabat(MinecraftServer server, UUID soul) {
		CompoundTag t = bagian(server).getCompoundOrEmpty(soul.toString());
		int id = t.getIntOr("sahabat", 0);
		return id == 0 ? null : WargaApi.byId(id);
	}

	static List<Tugas> semua(MinecraftServer server) {
		List<Tugas> out = new ArrayList<>();
		CompoundTag part = bagian(server);
		for (String key : part.keySet()) {
			CompoundTag t = part.getCompoundOrEmpty(key);
			if (t.getBooleanOr("pulang", false)) {
				continue;
			}
			try {
				out.add(baca(UUID.fromString(key), t));
			} catch (IllegalArgumentException ignored) {
				// not a soul key
			}
		}
		return out;
	}

	private static Tugas baca(UUID soul, CompoundTag t) {
		UUID o = t.read("o", UUIDUtil.CODEC).orElse(new UUID(0L, 0L));
		return new Tugas(t.getLongOr("situs", 0L), t.getStringOr("tugas", ""), t.getLongOr("sampai", 0L), soul, o, t.getStringOr("sasaran", ""),
				t.getStringOr("nama", "?"), t.getIntOr("ranah", 0), t.getIntOr("kuat", 1));
	}

	// ================================================================== actions

	/**
	 * {@code tugaskan:SOUL:TK}: sends a disciple (by soul among the loaded ones, else the nearest within 16 blocks) on duty
	 * {@code tugas} in {@code situs}. {@code jaga_kota} needs the hall's pact here; {@code kerja_kota} goes to the office
	 * when it has no keeper, else to the branch.
	 */
	public static KotaSekte.Hasil tugaskan(ServerPlayer player, long situs, @Nullable UUID soul, String tugas) {
		KotaSekte.Hasil h = KotaSekte.syaratUmum(player, KotaConfig.sekte().murid);
		if (h != null) {
			return h.kirim(player);
		}
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		if (BELANJA.equals(tugas)) {
			return KotaSekte.Hasil.gagal("murid.belanja").kirim(player);
		}
		String sasaran;
		int rotasi;
		if (JAGA_KOTA.equals(tugas)) {
			if (!owner.equals(PaktaLindung.pelindung(situs))) {
				return KotaSekte.Hasil.gagal("murid.tanpa_pakta").kirim(player);
			}
			if (penjagaKota(server, situs, owner).size() >= JAGA_MAKS) {
				return KotaSekte.Hasil.gagal("murid.jaga_penuh", JAGA_MAKS).kirim(player);
			}
			sasaran = SASARAN_JAGA;
			rotasi = ROTASI_JAGA;
		} else if (KERJA_KOTA.equals(tugas)) {
			boolean kantor = KantorRekrut.kantor(situs, owner) != null && penjagaDi(situs, SASARAN_KANTOR, owner) == null;
			CabangKota.Cabang cabang = CabangKota.cabang(situs);
			boolean bisaCabang = cabang != null && cabang.pemilik().equals(owner) && penjagaDi(situs, SASARAN_CABANG, owner) == null;
			boolean bangun = !kantor && !bisaCabang && Pembangun.adaKerja(server, situs);
			if (bangun && pembangun(server, situs) >= Pembangun.MURID_MAKS) {
				return KotaSekte.Hasil.gagal("murid.bangun_penuh", Pembangun.MURID_MAKS).kirim(player);
			}
			if (!kantor && !bisaCabang && !bangun) {
				return KotaSekte.Hasil.gagal("murid.tanpa_kerja").kirim(player);
			}
			sasaran = kantor ? SASARAN_KANTOR : bisaCabang ? SASARAN_CABANG : SASARAN_BANGUN;
			rotasi = ROTASI_KERJA;
		} else {
			return KotaSekte.Hasil.gagal("murid.tugas").kirim(player);
		}
		DiscipleEntity d = pilih(player, soul);
		if (d == null) {
			return KotaSekte.Hasil.gagal("murid.tanpa_murid").kirim(player);
		}
		long now = player.level().getGameTime();
		if (d.data().breakingThrough() || MandiriApi.luka(d.data(), now) != JenisLuka.TIDAK) {
			return KotaSekte.Hasil.gagal("murid.tidak_siap", d.getDisplayName()).kirim(player);
		}
		long hari = KotaSekte.hari(server);
		CompoundTag t = new CompoundTag();
		t.store("o", UUIDUtil.CODEC, owner);
		t.putLong("situs", situs);
		t.putString("tugas", tugas);
		t.putString("sasaran", sasaran);
		t.putLong("mulai", hari);
		t.putLong("sampai", hari + rotasi);
		t.putString("nama", d.getDisplayName().getString());
		t.putInt("ranah", d.data().realm().ordinal());
		t.putInt("kuat", Math.max(1, DisciplePaths.power(d.data())));
		UUID jiwa = d.soulId();
		CompoundTag info = new CompoundTag();
		info.putLong("situs", situs);
		info.putString("tugas", tugas);
		Component nama = d.getDisplayName();
		Halls.storeAway(owner, d, now + rotasi * 24000L, ALASAN, info);
		bagian(server).put(jiwa.toString(), t);
		KotaSekteData.get(server).ubah();
		Fx.burst(player.level(), player.position().add(0.0D, 1.0D, 0.0D), 0xD8C8A8, 8, 0.5F);
		return KotaSekte.Hasil.berhasil("murid.ditugaskan." + (SASARAN_BANGUN.equals(sasaran) ? "bangun" : tugas), nama,
				KotaSekte.namaTempat(server, situs), rotasi).kirim(player);
	}

	/** {@code tarik_murid:SOUL}: calls a disciple home now (it arrives at the hall within a few seconds). */
	public static KotaSekte.Hasil tarik(ServerPlayer player, UUID soul) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = bagian(server).getCompoundOrEmpty(soul.toString());
		if (t.isEmpty() || !player.getUUID().equals(t.read("o", UUIDUtil.CODEC).orElse(null)) || t.getBooleanOr("pulang", false)) {
			return KotaSekte.Hasil.gagal("murid.tidak_bertugas").kirim(player);
		}
		pulangkan(server, player.getUUID(), soul, t);
		return KotaSekte.Hasil.berhasil("murid.ditarik", t.getStringOr("nama", "?")).kirim(player);
	}

	/** A post is gone (office closed, pact ended, branch closed): its keepers come home. */
	static void sasaranHilang(MinecraftServer server, long situs, String sasaran, @Nullable UUID owner) {
		CompoundTag part = bagian(server);
		for (String key : List.copyOf(part.keySet())) {
			CompoundTag t = part.getCompoundOrEmpty(key);
			UUID o = t.read("o", UUIDUtil.CODEC).orElse(null);
			if (t.getLongOr("situs", 0L) == situs && sasaran.equals(t.getStringOr("sasaran", "")) && (owner == null || owner.equals(o))
					&& !t.getBooleanOr("pulang", false) && o != null) {
				try {
					pulangkan(server, o, UUID.fromString(key), t);
				} catch (IllegalArgumentException ignored) {
					// not a soul key
				}
			}
		}
	}

	/** A defence went badly for a guard: it comes home wounded (39's wound, never death). */
	static void tandaiLuka(MinecraftServer server, UUID soul) {
		CompoundTag t = bagian(server).getCompoundOrEmpty(soul.toString());
		if (!t.isEmpty()) {
			t.putBoolean("luka", true);
			KotaSekteData.get(server).ubah();
		}
	}

	private static void pulangkan(MinecraftServer server, UUID owner, UUID soul, CompoundTag t) {
		t.putBoolean("pulang", true);
		// the away record is the hall's: we only bring its due time forward (39 reads these records the same way)
		ListTag away = Halls.section(owner, "core").getListOrEmpty("away");
		long now = server.overworld().getGameTime();
		for (Tag e : away) {
			if (e instanceof CompoundTag rec && ALASAN.equals(rec.getStringOr("reason", ""))
					&& soul.equals(rec.read("soul", UUIDUtil.CODEC).orElse(null))) {
				rec.putLong("return_at", Math.min(rec.getLongOr("return_at", now), now));
			}
		}
		Halls.setDirty();
		Tubuh body = TUBUH.remove(soul);
		if (body != null && !body.body.isRemoved()) {
			if (body.body.level() instanceof ServerLevel level) {
				Fx.burst(level, body.body.position().add(0.0D, 1.0D, 0.0D), 0xD8C8A8, 10, 0.6F);
			}
			body.body.discard();
		}
		KotaSekteData.get(server).ubah();
	}

	private static @Nullable DiscipleEntity pilih(ServerPlayer player, @Nullable UUID soul) {
		DiscipleEntity best = null;
		double bestSq = Disciples.ACTION_RANGE * Disciples.ACTION_RANGE;
		for (DiscipleEntity d : Disciples.loaded(player.getUUID())) {
			if (d.role() != DiscipleEntity.Role.DISCIPLE || d.level() != player.level()) {
				continue;
			}
			if (soul != null) {
				if (soul.equals(d.soulId())) {
					return d;
				}
				continue;
			}
			double sq = d.distanceToSqr(player);
			if (sq < bestSq) {
				bestSq = sq;
				best = d;
			}
		}
		return best;
	}

	// ================================================================== return (AwayStore handler)

	/** A disciple's duty ended: bar share, wound, diary, friend; arrives beside the stele. */
	static @Nullable Vec3 kembali(ServerLevel level, UUID owner, CompoundTag stored, CompoundTag info) {
		MinecraftServer server = level.getServer();
		UUID soul = stored.read("soul", UUIDUtil.CODEC).orElse(null);
		CompoundTag disciple = stored.getCompoundOrEmpty("disciple");
		CompoundTag t = soul == null ? new CompoundTag() : bagian(server).getCompoundOrEmpty(soul.toString());
		if (!disciple.isEmpty()) {
			DiscipleData data = DiscipleData.load(disciple);
			long hari = KotaSekte.hari(server);
			long hariBertugas = Math.max(1L, Math.min(hari, t.getLongOr("sampai", hari)) - t.getLongOr("mulai", hari - 1L));
			DisciplePaths.grantBarShare(data, Math.min(0.3D, 0.03D * hariBertugas));
			long now = level.getGameTime();
			if (t.getBooleanOr("luka", false)) {
				MandiriApi.lukai(data, JenisLuka.TERLUKA, now, now + 2L * 24000L, "kota42.tugas");
			}
			stored.put("disciple", data.save());
			if (soul != null) {
				String tempat = KotaSekte.namaTempat(server, info.getLongOr("situs", t.getLongOr("situs", 0L))).getString();
				Warga sahabat = sahabat(server, soul);
				int jam = (int) ((Kota.timeOfDay(server) / 1000L + 6L) % 24L);
				String tugas = info.getStringOr("tugas", t.getStringOr("tugas", JAGA_KOTA));
				String kunci = SASARAN_BANGUN.equals(t.getStringOr("sasaran", "")) ? "kerja_bangun" : tugas;
				List<String> arg = sahabat == null ? List.of(tempat, Long.toString(hariBertugas))
						: List.of(tempat, Long.toString(hariBertugas), WargaApi.nama(sahabat).getString());
				MandiriApi.simpanHarian(server, owner, soul, new EntriHarian(hari, jam, "kota." + tugas,
						"kultivator.kota42.sekte.harian." + kunci + (sahabat == null ? "" : "_sahabat"), arg, EntriHarian.Suasana.TENANG,
						false));
			}
		}
		if (soul != null) {
			bagian(server).remove(soul.toString());
			Tubuh body = TUBUH.remove(soul);
			if (body != null && !body.body.isRemoved()) {
				body.body.discard();
			}
			KotaSekteData.get(server).ubah();
		}
		return null;
	}

	// ================================================================== daily

	/** Day step: a friend in the town now and then; records whose disciple came home are dropped. */
	static void harian(MinecraftServer server, long hari) {
		if (!KotaConfig.sekte().murid) {
			return;
		}
		CompoundTag part = bagian(server);
		ServerLevel level = server.overworld();
		for (String key : List.copyOf(part.keySet())) {
			CompoundTag t = part.getCompoundOrEmpty(key);
			UUID owner = t.read("o", UUIDUtil.CODEC).orElse(null);
			UUID soul;
			try {
				soul = UUID.fromString(key);
			} catch (IllegalArgumentException exception) {
				part.remove(key);
				continue;
			}
			if (owner == null || !masihPergi(owner, soul) && hari > t.getLongOr("sampai", 0L) + 1L) {
				part.remove(key);
				continue;
			}
			if (!t.getBooleanOr("pulang", false) && SASARAN_BANGUN.equals(t.getStringOr("sasaran", ""))) {
				// the site is quiet two days running: the builder goes home
				if (Pembangun.adaKerja(server, t.getLongOr("situs", 0L))) {
					t.putInt("sepi", 0);
				} else if (t.getIntOr("sepi", 0) >= 1) {
					pulangkan(server, owner, soul, t);
					continue;
				} else {
					t.putInt("sepi", 1);
				}
			}
			if (t.getBooleanOr("pulang", false) || t.getIntOr("sahabat", 0) != 0) {
				continue;
			}
			long situs = t.getLongOr("situs", 0L);
			RandomSource r = RandomSource.create(soul.getLeastSignificantBits() ^ hari * 0x9E3779B97F4A7C15L);
			if (r.nextDouble() >= 0.3D) {
				continue;
			}
			List<Warga> calon = new ArrayList<>();
			for (Warga w : WargaApi.wargaTempat(situs)) {
				int umur = WargaApi.umur(w);
				if (w.status == Warga.Status.HIDUP && umur >= 14 && umur <= 60) {
					calon.add(w);
				}
			}
			if (calon.isEmpty()) {
				continue;
			}
			Warga w = calon.get(r.nextInt(calon.size()));
			t.putInt("sahabat", w.id);
			ArsipWarga.Catatan c = ArsipWarga.catatan(server);
			c.warga(w.id).putString("sahabatMurid", "murid:" + soul);
			c.ubah();
			GosipWarga.lahir(level, situs, "MENOLONG", Fakta.Rujuk.warga(w.id), Fakta.Rujuk.kata("murid_sekte"));
		}
		KotaSekteData.get(server).ubah();
	}

	private static boolean masihPergi(UUID owner, UUID soul) {
		for (Tag e : Halls.section(owner, "core").getListOrEmpty("away")) {
			if (e instanceof CompoundTag rec && soul.equals(rec.read("soul", UUIDUtil.CODEC).orElse(null))) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== town bodies (every second)

	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		boolean boleh = KotaConfig.sekte().murid;
		for (Iterator<Map.Entry<UUID, Tubuh>> it = TUBUH.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Tubuh> e = it.next();
			Tubuh t = e.getValue();
			Tugas tugas = boleh ? tugas(e.getKey()) : null;
			if (t.body.isRemoved() || tugas == null
					|| !level.hasNearbyAlivePlayer(t.body.getX(), t.body.getY(), t.body.getZ(), JARAK_TAMPIL + 64.0D)) {
				if (!t.body.isRemoved()) {
					t.body.discard();
				}
				it.remove();
				continue;
			}
			gerakkan(level, t, tugas, now);
		}
		if (!boleh) {
			return;
		}
		for (Tugas tugas : semua(server)) {
			if (TUBUH.containsKey(tugas.soul())) {
				continue;
			}
			BlockPos pos = pos(level, tugas, 0);
			if (pos == null || !level.isLoaded(pos) || !level.hasNearbyAlivePlayer(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, JARAK_TAMPIL)
					|| !DuniaBudget.mayAdd(level, DuniaBudget.NPC, Vec3.atBottomCenterOf(pos))) {
				continue;
			}
			DiscipleEntity body = munculkan(level, tugas, pos);
			if (body != null) {
				Tubuh t = new Tubuh(body, tugas.soul());
				t.berikut = now + 40L;
				TUBUH.put(tugas.soul(), t);
				pose(t, tugas, now);
			}
		}
	}

	private static @Nullable DiscipleEntity munculkan(ServerLevel level, Tugas tugas, BlockPos at) {
		CompoundTag rec = null;
		for (Tag e : Halls.section(tugas.pemilik(), "core").getListOrEmpty("away")) {
			if (e instanceof CompoundTag c && ALASAN.equals(c.getStringOr("reason", "")) && tugas.soul().equals(c.read("soul", UUIDUtil.CODEC).orElse(null))) {
				rec = c;
			}
		}
		if (rec == null) {
			return null;
		}
		CompoundTag stored = rec.getCompoundOrEmpty("stored");
		DiscipleData data = DiscipleData.load(stored.getCompoundOrEmpty("disciple"));
		Component name = stored.read("name", ComponentSerialization.CODEC).orElse(Component.literal(tugas.nama()));
		Sosok sosok = Sosok.unpack(stored.getIntOr("sosok", Sosok.KOSONG));
		try {
			DiscipleEntity body = Disciples.spawnUnowned(level, Vec3.atBottomCenterOf(at), data, name, stored.getIntOr("skin", 0),
					stored.getBooleanOr("slim", false), sosok, stored.getIntOr("gaya", 0), DiscipleEntity.Role.JELMAAN, at);
			if (body == null) {
				return null;
			}
			if (sosok != null) {
				SosokApi.setSosok(body, sosok);
			}
			DuniaBudget.track(DuniaBudget.NPC, body);
			Fx.burst(level, body.position().add(0.0D, 1.0D, 0.0D), 0xD8C8A8, 8, 0.5F);
			return body;
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.warn("Kota hidup: town body of {} could not be shown", tugas.soul(), exception);
			return null;
		}
	}

	/** The post of a task: guard points by the town edge (the {@code i}-th), the office desk, the branch sign. */
	private static @Nullable BlockPos pos(ServerLevel level, Tugas tugas, int i) {
		MinecraftServer server = level.getServer();
		switch (tugas.sasaran()) {
			case SASARAN_KANTOR -> {
				return KantorRekrut.meja(server, tugas.situs(), tugas.pemilik());
			}
			case SASARAN_CABANG -> {
				return CabangKota.posPenjaga(server, tugas.situs());
			}
			case SASARAN_BANGUN -> {
				return Pembangun.titikKerja(level, tugas.situs(), i + 3 * pembangunUrut(server, tugas));
			}
			default -> {
				List<BlockPos> jaga = posJaga(level, tugas.situs());
				if (jaga.isEmpty()) {
					return null;
				}
				int urut = penjagaUrut(server, tugas);
				return jaga.get(Math.floorMod(urut * 2 + i, jaga.size()));
			}
		}
	}

	/** Guard points of the place ({@code jaga} titik of 38), farthest from the centre first; else the four box edges. */
	private static List<BlockPos> posJaga(ServerLevel level, long situs) {
		BoundingBox box = GelombangBinatang.kotakTempat(level, situs);
		List<BlockPos> out = new ArrayList<>();
		for (Titik t : TempatApi.titik(level, situs, "jaga")) {
			out.add(t.pos());
		}
		if (box == null) {
			return out;
		}
		BlockPos c = box.getCenter();
		if (out.isEmpty()) {
			int[][] tepi = {{box.minX() + 1, c.getZ()}, {box.maxX() - 1, c.getZ()}, {c.getX(), box.minZ() + 1}, {c.getX(), box.maxZ() - 1}};
			for (int[] xz : tepi) {
				BlockPos g = TempatApi.groundSpot(level, xz[0], xz[1], c.getY());
				if (g != null) {
					out.add(g);
				}
			}
		}
		out.sort(Comparator.comparingDouble((BlockPos p) -> -p.distSqr(c)).thenComparingLong(BlockPos::asLong));
		return out;
	}

	/** Rank of a builder among the builders of its town (soul order), so two do not stand on the same spot. */
	private static int pembangunUrut(MinecraftServer server, Tugas tugas) {
		List<Tugas> sama = new ArrayList<>();
		for (Tugas t : semua(server)) {
			if (t.situs() == tugas.situs() && SASARAN_BANGUN.equals(t.sasaran())) {
				sama.add(t);
			}
		}
		sama.sort(Comparator.comparing(x -> x.soul().toString()));
		return Math.max(0, sama.indexOf(tugas));
	}

	private static int penjagaUrut(MinecraftServer server, Tugas tugas) {
		List<Tugas> sama = penjagaKota(server, tugas.situs(), tugas.pemilik());
		sama.sort(Comparator.comparing(x -> x.soul().toString()));
		return Math.max(0, sama.indexOf(tugas));
	}

	/** Day: the guard walks between its two points; night: it stands its watch. Keepers mind their desk. */
	private static void gerakkan(ServerLevel level, Tubuh t, Tugas tugas, long now) {
		MinecraftServer server = level.getServer();
		ServerPlayer master = server.getPlayerList().getPlayer(tugas.pemilik());
		if (master != null && master.level() == level && master.distanceToSqr(t.body) < 25.0D && now >= t.hormat) {
			t.hormat = now + 2400L;
			SosokApi.gerak(t.body, GerakId.HORMAT, 0, 40);
			SosokApi.tatap(t.body, master);
			Msg.actionBar(master, Component.translatable("kultivator.kota42.sekte.murid.hormat", t.body.getDisplayName()).withStyle(ChatFormatting.GOLD));
		}
		if (now < t.berikut) {
			return;
		}
		t.berikut = now + 600L;
		if (SASARAN_BANGUN.equals(tugas.sasaran())) {
			if (t.berjalan) {
				// arrived at the next spot: hammer there for a while
				t.berjalan = false;
				t.berikut = now + 900L;
				pose(t, tugas, now);
				return;
			}
			BlockPos ke = pos(level, tugas, ++t.titik);
			if (ke != null) {
				SosokApi.gerak(t.body, GerakId.ANGKAT_PAPAN, 0, 0, PropId.PAPAN_KAYU, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
				t.body.getNavigation().moveTo(ke.getX() + 0.5D, ke.getY(), ke.getZ() + 0.5D, 0.6D);
				t.berjalan = true;
				t.berikut = now + 160L;
				return;
			}
		}
		if (SASARAN_JAGA.equals(tugas.sasaran())) {
			long jam = (Kota.timeOfDay(server) / 1000L + 6L) % 24L;
			boolean siang = jam >= 6L && jam < 18L;
			if (siang) {
				t.titik = 1 - t.titik;
				BlockPos ke = pos(level, tugas, t.titik);
				if (ke != null) {
					SosokApi.gerak(t.body, 0, 0, 0);
					t.body.getNavigation().moveTo(ke.getX() + 0.5D, ke.getY(), ke.getZ() + 0.5D, 0.6D);
					return;
				}
			}
		}
		pose(t, tugas, now);
	}

	private static void pose(Tubuh t, Tugas tugas, long now) {
		switch (tugas.sasaran()) {
			case SASARAN_KANTOR -> SosokApi.gerak(t.body, Math.floorMod(now / 600L, 3L) == 0L ? GerakId.SERU_DAGANG : GerakId.HITUNG_SEMPOA, 0, 0);
			case SASARAN_BANGUN -> {
				boolean palu = Math.floorMod(now / 900L, 2L) == 0L;
				if (t.body.level() instanceof ServerLevel level) {
					for (BoundingBox b : Pembangun.lokasi(level.getServer(), tugas.situs())) {
						t.body.getLookControl().setLookAt(b.getCenter().getX() + 0.5D, b.getCenter().getY() + 1.0D, b.getCenter().getZ() + 0.5D);
						break;
					}
				}
				SosokApi.gerak(t.body, palu ? GerakId.PERBAIKI_PALU : GerakId.PERBAIKI_PLESTER, 0, 0, palu ? PropId.PALU : PropId.SENDOK_PLESTER,
						PropId.TIDAK_ADA, PropId.TIDAK_ADA);
			}
			case SASARAN_CABANG -> {
				CabangKota.Cabang c = CabangKota.cabang(tugas.situs());
				int klip = c != null && CabangKota.BALAI_LATIHAN.equals(c.jenis()) ? GerakId.MENGAJAR : GerakId.HITUNG_SEMPOA;
				SosokApi.gerak(t.body, klip, 0, 0);
			}
			default -> SosokApi.gerak(t.body, GerakId.BERDIRI_JAGA, 0, 0);
		}
	}

	// ================================================================== storage

	private static CompoundTag bagian(MinecraftServer server) {
		return KotaSekteData.get(server).bagian(KotaSekteData.MURID);
	}

	private TugasKotaMurid() {
	}
}
