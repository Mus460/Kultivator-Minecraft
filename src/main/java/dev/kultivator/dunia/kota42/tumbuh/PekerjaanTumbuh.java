package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.ekonomi.Investasi;
import dev.kultivator.dunia.kota42.ekonomi.Jabatan;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ekonomi.RumahKota;
import dev.kultivator.dunia.kota42.ekonomi.UsahaWarga;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.kota42.warga.Pekerjaan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import dev.kultivator.dunia.tempat.perbaikan.KlaimPemain;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The growth engine of DESIGN 42.9.4 (server, every second from {@link KotaTumbuh}):
 * <ol>
 *   <li><b>Finding plots</b>: every 5 s the {@code kavling} titik of each active overworld place (loaded pieces) become
 *       {@link Kavling} records.</li>
 *   <li><b>Trigger</b>: prosperity {@code >= kotaTumbuh.ambang} and a need (crowded: more living residents than beds;
 *       three idle adults; eight children and no school; a raid this week) and Kas Kota able to pay the first stage's
 *       missing material and no rebuild project running ({@link BangunKembali#aktif}); {@code kotaTumbuh.serentak} plots at
 *       once (the capital one more). The plan: the deputy's choice ({@code Jabatan.pilihanRencana}), else the plot's own
 *       plan when it answers the need, else a plan of the plot's style and class that does (crowded -> houses, idle ->
 *       business, children -> school, raids -> watchtower).</li>
 *   <li><b>A stage</b>: material from {@link BahanKota} (Kas Kota buys what is missing, else the gap is a city order and
 *       the plot waits), then {@link #periksa} (42.12.1A: every corner and the centre inside our own plot piece; no
 *       structure of another namespace touching the box + 2; ground within 2 blocks and dry before stage 1; no player
 *       claim), then one crew job ({@code PerbaikanApi.kerjakan}) with every template cell that differs from the
 *       blueprint record (expected = the record, so no foreign block is ever replaced). A failed (1)/(2)/ground check
 *       makes the plot {@code TIDAK_LAYAK} for good; a claim or a player's block makes it {@code terhalang} until cleared
 *       (the foreman asks nearby players).</li>
 *   <li><b>Done</b>: the stage is checked against the world; stage 5 lets the blueprint take the plot
 *       ({@code PerbaikanApi.terimaKotak}), the plan's titik join the place ({@code TempatHuni.addSumberTitik}), a house is
 *       moved into (crowded town) or offered ({@code RumahKota.tawarkan}, {@code RS_KAVLING}), a business opens
 *       ({@code UsahaWarga.buka}, a player who paid half holds 60 %), a gossip {@code KAVLING_JADI} is born,
 *       {@code KotaBus.KAVLING} fires (the opening ceremony listens), donors get {@code kota42_kavling}.</li>
 * </ol>
 */
public final class PekerjaanTumbuh {

	/** Ticks between plot searches and payload refreshes. */
	static final int CARI_TICK = 100;
	/** Seconds between two tries of a waiting plot. */
	static final int COBA_DETIK = 10;
	/** Retries of a stage whose blocks did not all go in (bodies in the way) before it counts as blocked. */
	static final int MAKS_ULANG = 6;
	/** Radius of the plot payload and the foreman's words. */
	static final double JARAK_KIRIM = 96.0D;

	/** Place -> stage-5 titik of its finished plots (memory, rebuilt on demand). */
	private static final Map<Long, List<Titik>> TITIK = new HashMap<>();
	/** Plot key -> last second it was tried. */
	private static final Map<Long, Long> COBA = new HashMap<>();
	/** Plot key -> game time the foreman last spoke about a blocked plot. */
	private static final Map<Long, Long> TEGUR = new HashMap<>();
	private static long detik;

	static void lupakan() {
		TITIK.clear();
		COBA.clear();
		TEGUR.clear();
	}

	static boolean aktif() {
		return KotaConfig.tumbuh().aktif && KotaConfig.warga().aktif;
	}

	// ================================================================== tick

	/** Once a second. */
	static void tick(MinecraftServer server) {
		if (!aktif()) {
			return;
		}
		detik++;
		ServerLevel level = server.overworld();
		if (server.getTickCount() % CARI_TICK < 20) {
			cari(server, level);
		}
		for (long situs : Kavling.tempat(server)) {
			try {
				langkah(server, level, situs);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kultivator: town growth of place {} failed this second", situs, exception);
			}
		}
		if (server.getTickCount() % CARI_TICK < 20) {
			kirim(server, level);
		}
	}

	/** Records the plots of every active place. */
	private static void cari(MinecraftServer server, ServerLevel level) {
		for (TempatHuni huni : Pemanggil.semua()) {
			if (!Level.OVERWORLD.equals(huni.dimensi)) {
				continue;
			}
			for (Titik t : TempatApi.titik(level, huni.situsId, "kavling")) {
				Kavling.temukan(server, huni.situsId, t);
			}
		}
	}

	private static void langkah(MinecraftServer server, ServerLevel level, long situs) {
		List<CompoundTag> plots = Kavling.semua(server, situs);
		int building = 0;
		for (CompoundTag c : plots) {
			int t = c.getIntOr("t", 0);
			if (t >= 0 && t < Kavling.SELESAI && !KotaLembar.Kavling.KV_KOSONG.equals(c.getStringOr("s", ""))) {
				building++;
				lanjut(server, level, situs, c);
			}
		}
		if (building >= serentak(server, situs) || detik % COBA_DETIK != Math.floorMod(situs, COBA_DETIK)) {
			return;
		}
		List<String> needs = kebutuhan(server, level, situs);
		if (needs.isEmpty() || Kemakmuran.nilai(server, situs) < KotaConfig.tumbuh().ambang || BangunKembali.aktif(server, situs)
				|| Blokade.aktif(server, situs)) {
			return;
		}
		pilihBaru(server, situs, plots, needs);
	}

	public static int serentak(MinecraftServer server, long situs) {
		int n = KotaConfig.tumbuh().serentak;
		return PajakKota.JT_IBU_KOTA.equals(PajakKota.jenisTempat(server, situs)) ? n + 1 : n;
	}

	/** Picks the next empty plot and its plan for the first need that one answers; marks it waiting. */
	private static void pilihBaru(MinecraftServer server, long situs, List<CompoundTag> plots, List<String> needs) {
		String chosen = Jabatan.aktif() ? Jabatan.pilihanRencana(server, situs) : "";
		for (CompoundTag c : plots) {
			if (c.getIntOr("t", 0) != 0 || !KotaLembar.Kavling.KV_KOSONG.equals(c.getStringOr("s", ""))) {
				continue;
			}
			RencanaTumbuh.Rencana plan = null;
			if (!chosen.isEmpty()) {
				RencanaTumbuh.Rencana r = RencanaTumbuh.get(server, chosen);
				if (r != null && r.kelas().equals(c.getStringOr("k", "")) && r.gaya().equals(c.getStringOr("g", ""))) {
					plan = r;
				}
			}
			for (int i = 0; plan == null && i < needs.size(); i++) {
				plan = rencanaUntuk(server, c, needs.get(i));
			}
			if (plan == null) {
				continue;
			}
			long cost = biayaTahap(server, situs, plan, 1);
			if (KasKota.saldo(server, situs) < cost) {
				return;
			}
			c.putString("r", plan.id());
			c.putLong("biaya", biaya(server, situs, plan));
			c.putString("kb", needs.get(0));
			c.putLong("m0", KotaHidup.hari(server));
			Kavling.keadaan(c, KotaLembar.Kavling.KV_MENUNGGU, "");
			KotaTumbuhData.get(server).ubah();
			return;
		}
	}

	/** The plot's own plan when it answers {@code need}, else the first plan of its style and class that does. */
	private static @Nullable RencanaTumbuh.Rencana rencanaUntuk(MinecraftServer server, CompoundTag c, String need) {
		RencanaTumbuh.Rencana own = RencanaTumbuh.get(server, c.getStringOr("r", ""));
		if (own != null && own.butuh(need)) {
			return own;
		}
		for (RencanaTumbuh.Rencana r : RencanaTumbuh.untuk(server, c.getStringOr("g", ""), c.getStringOr("k", ""))) {
			if (r.butuh(need)) {
				return r;
			}
		}
		return null;
	}

	/** What the place lacks, most pressing first: {@code rumah}, {@code usaha}, {@code sekolah}, {@code jaga}. */
	public static List<String> kebutuhan(MinecraftServer server, ServerLevel level, long situs) {
		List<String> out = new ArrayList<>();
		WargaData.Situs s = WargaData.get(server).peek(situs);
		if (s == null) {
			return out;
		}
		int living = 0;
		int idle = 0;
		int children = 0;
		for (Warga w : s.warga) {
			if (w.status != Warga.Status.HIDUP) {
				continue;
			}
			living++;
			int age = WargaApi.umur(w);
			if (age >= 6 && age < 16) {
				children++;
			} else if (age >= 16 && age < 60 && !Pekerjaan.bekerja(w)) {
				idle++;
			}
		}
		TempatHuni huni = Pemanggil.huni(situs);
		int beds = huni != null ? huni.titik("kasur").size() : s.stat.getIntOr("a2kasur", living);
		if (living > beds) {
			out.add("rumah");
		}
		if (idle >= 3) {
			out.add("usaha");
		}
		if (children >= 8 && (huni == null || huni.titik("sekolah").isEmpty())) {
			out.add("sekolah");
		}
		if (Kemakmuran.serbuanMinggu(server, situs) > 0) {
			out.add("jaga");
		}
		return out;
	}

	// ================================================================== a plot's stages

	private static long kunci(long situs, CompoundTag c) {
		return situs * 131L + c.getIntOr("i", 0);
	}

	private static void lanjut(MinecraftServer server, ServerLevel level, long situs, CompoundTag c) {
		long job = c.getLongOr("job", 0L);
		int next = c.getIntOr("t", 0) + 1;
		RencanaTumbuh.Rencana plan = RencanaTumbuh.get(server, c.getStringOr("r", ""));
		if (plan == null) {
			Kavling.keadaan(c, KotaLembar.Kavling.KV_TERHALANG, "rencana");
			return;
		}
		if (job != 0L) {
			if (PerbaikanApi.berjalan(job)) {
				if (!KotaLembar.Kavling.KV_DIBANGUN.equals(c.getStringOr("s", ""))) {
					Kavling.keadaan(c, KotaLembar.Kavling.KV_DIBANGUN, "");
					KotaTumbuhData.get(server).ubah();
				}
				return;
			}
			c.putLong("job", 0L);
			periksaHasil(server, level, situs, c, plan, next);
			return;
		}
		long key = kunci(situs, c);
		Long last = COBA.get(key);
		if (last != null && detik - last < COBA_DETIK) {
			return;
		}
		COBA.put(key, detik);
		mulaiTahap(server, level, situs, c, plan, next);
	}

	/** Starts stage {@code n}: material, checks, the crew job. */
	private static void mulaiTahap(MinecraftServer server, ServerLevel level, long situs, CompoundTag c, RencanaTumbuh.Rencana plan, int n) {
		RencanaTumbuh.Tahap stage = plan.tahap(n);
		if (stage == null) {
			return;
		}
		BoundingBox box = Kavling.kotak(c);
		if (!dimuat(level, box)) {
			// shortcut: planning reads the world, so plots only advance while visited; plan from the Cetak record if towns must grow unseen
			if (!KotaLembar.Kavling.KV_TERHALANG.equals(c.getStringOr("s", ""))) {
				Kavling.keadaan(c, KotaLembar.Kavling.KV_MENUNGGU, "jauh");
			}
			return;
		}
		String fail = periksa(level, c, n == 1);
		if (fail != null) {
			if (fail.startsWith("!")) {
				Kavling.matikan(server, situs, c, fail.substring(1));
			} else {
				Kavling.keadaan(c, KotaLembar.Kavling.KV_TERHALANG, fail);
				tegur(server, level, situs, c, fail);
			}
			return;
		}
		if (c.getIntOr("bn", 0) < n) {
			int[] need = stage.bahan();
			if (!BahanKota.ambil(server, situs, need) && !(BahanKota.beliKas(server, situs, need) && BahanKota.ambil(server, situs, need))) {
				Kavling.keadaan(c, KotaLembar.Kavling.KV_MENUNGGU, "bahan");
				return;
			}
			int[] used = c.getIntArray("u").orElse(new int[BahanKota.JENIS.size()]);
			for (int i = 0; i < used.length && i < need.length; i++) {
				used[i] += need[i];
			}
			c.putIntArray("u", used);
			c.putInt("bn", n);
			BahanKota.ulangPesanan(server, situs);
		}
		Rencana cells = sel(server, level, c, plan, n);
		if (cells.posisi.length == 0) {
			selesaiTahap(server, level, situs, c, plan, n);
			return;
		}
		long job = PerbaikanApi.kerjakan(new PerbaikanApi.Pekerjaan(level, situs, cells.posisi, cells.target, cells.expected));
		if (job == 0L) {
			Kavling.keadaan(c, KotaLembar.Kavling.KV_TERHALANG, "tanpa_regu");
			return;
		}
		c.putLong("job", job);
		c.putInt("tot", cells.posisi.length);
		Kavling.keadaan(c, KotaLembar.Kavling.KV_DIBANGUN, "");
		KotaTumbuhData.get(server).ubah();
	}

	/** A stage job ended: every cell in? Else retry (bodies in the way) or report the foreign blocks. */
	private static void periksaHasil(MinecraftServer server, ServerLevel level, long situs, CompoundTag c, RencanaTumbuh.Rencana plan, int n) {
		BoundingBox box = Kavling.kotak(c);
		if (!dimuat(level, box)) {
			Kavling.keadaan(c, KotaLembar.Kavling.KV_MENUNGGU, "jauh");
			return;
		}
		Rencana left = sel(server, level, c, plan, n);
		int foreign = 0;
		for (int i = 0; i < left.posisi.length; i++) {
			BlockState now = level.getBlockState(BlockPos.of(left.posisi[i]));
			if (!now.isAir() && now != left.expected[i]) {
				foreign++;
			}
		}
		if (left.posisi.length == 0) {
			selesaiTahap(server, level, situs, c, plan, n);
		} else if (foreign > 0) {
			c.putInt("an", foreign);
			Kavling.keadaan(c, KotaLembar.Kavling.KV_TERHALANG, "blok");
			tegur(server, level, situs, c, "blok");
		} else if (c.getIntOr("co", 0) < MAKS_ULANG) {
			c.putInt("co", c.getIntOr("co", 0) + 1);
			Kavling.keadaan(c, KotaLembar.Kavling.KV_MENUNGGU, "");
		} else {
			c.putInt("an", left.posisi.length);
			Kavling.keadaan(c, KotaLembar.Kavling.KV_TERHALANG, "gagal");
		}
		KotaTumbuhData.get(server).ubah();
	}

	private static void selesaiTahap(MinecraftServer server, ServerLevel level, long situs, CompoundTag c, RencanaTumbuh.Rencana plan, int n) {
		c.putInt("t", n);
		c.putLong("job", 0L);
		c.putInt("co", 0);
		c.putInt("an", 0);
		COBA.remove(kunci(situs, c));
		if (n >= Kavling.SELESAI) {
			selesai(server, level, situs, c, plan);
		} else {
			Kavling.keadaan(c, KotaLembar.Kavling.KV_MENUNGGU, "");
		}
		KotaTumbuhData.get(server).ubah();
		KotaBus.KAVLING.invoker().onKavling(server, situs, c.getIntOr("i", 0), n - 1);
	}

	/** Stage 5 done: the building joins the town. */
	private static void selesai(MinecraftServer server, ServerLevel level, long situs, CompoundTag c, RencanaTumbuh.Rencana plan) {
		Kavling.keadaan(c, KotaLembar.Kavling.KV_SELESAI, "");
		c.putLong("m1", KotaHidup.hari(server));
		BoundingBox box = Kavling.kotak(c);
		PerbaikanApi.terimaKotak(level, box);
		TITIK.remove(situs);
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni != null) {
			huni.ubahTitik();
		}
		List<Titik> points = RencanaTumbuh.titik(server, plan, Kavling.asal(c, plan), Kavling.rotasi(c));
		Titik house = null;
		BlockPos door = null;
		for (Titik t : points) {
			if ("bangunan".equals(t.jenis()) && house == null) {
				house = t;
			} else if ("pintu".equals(t.jenis()) && door == null) {
				door = t.pos();
			}
		}
		UUID top = penyumbangUtama(c);
		long given = top == null ? 0L : c.getCompoundOrEmpty("dn").getLongOr(top.toString(), 0L);
		boolean half = given * 2L >= Math.max(1L, c.getLongOr("biaya", 1L));
		if (plan.butuh("rumah") && house != null && door != null && !"rumah".equals(c.getStringOr("kb", ""))) {
			// nobody waits for a bed: the house is for sale (42.6.1 c); a crowded town moves in through the new beds
			try {
				RumahKota.tawarkan(server, situs, house, door, KotaLembar.Rumah.RS_KAVLING);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.debug("Kultivator: the plot house could not be offered", exception);
			}
		}
		String usaha = usahaUntuk(plan.jenis());
		if (usaha != null && plan.butuh("usaha")) {
			Warga owner = penganggur(server, situs);
			try {
				int id = UsahaWarga.buka(server, situs, usaha, owner == null ? 0 : owner.id, owner == null ? c.getIntOr("i", 0) : owner.slot);
				c.putInt("usaha", id);
				if (half && top != null) {
					Investasi.sahamKavling(server, situs, id, top, 0.6D, given);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.debug("Kultivator: the plot business could not open", exception);
			}
		}
		GosipWarga.lahir(level, situs, "KAVLING_JADI", Fakta.Rujuk.tempat(situs), Fakta.Rujuk.kata(plan.jenis()));
		Component name = namaRencana(plan);
		Vec3 at = Vec3.atCenterOf(box.getCenter());
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(at) <= JARAK_KIRIM * JARAK_KIRIM) {
				Msg.chat(player, Component.translatable("kultivator.kota42.tumbuh.selesai", c.getIntOr("i", 0), name).withStyle(ChatFormatting.GOLD));
				if (player.position().distanceToSqr(at) <= 32.0D * 32.0D) {
					Advancements.grant(player, "tempat/kota42_kavling");
				}
			}
		}
		for (String key : c.getCompoundOrEmpty("dn").keySet()) {
			try {
				ServerPlayer donor = server.getPlayerList().getPlayer(UUID.fromString(key));
				if (donor != null) {
					Advancements.grant(donor, "tempat/kota42_kavling");
				}
			} catch (IllegalArgumentException ignored) {
				// not a player key
			}
		}
	}

	/** Business kind a finished plan opens (42C {@code UsahaWarga.JENIS}), or null. */
	static @Nullable String usahaUntuk(String jenis) {
		return switch (jenis) {
			case "kedai_teh" -> "kedai_teh";
			case "kedai_arak" -> "kedai_arak";
			case "bengkel" -> "bengkel_pandai";
			case "rumah_toko" -> "toko_kain";
			default -> null;
		};
	}

	private static @Nullable Warga penganggur(MinecraftServer server, long situs) {
		WargaData.Situs s = WargaData.get(server).peek(situs);
		if (s == null) {
			return null;
		}
		Warga best = null;
		for (Warga w : s.warga) {
			int age = WargaApi.umur(w);
			if (w.status == Warga.Status.HIDUP && age >= 18 && age < 60 && !Pekerjaan.bekerja(w) && (best == null || w.ahli > best.ahli)) {
				best = w;
			}
		}
		return best;
	}

	private static @Nullable UUID penyumbangUtama(CompoundTag c) {
		CompoundTag dn = c.getCompoundOrEmpty("dn");
		String best = null;
		long nb = 0L;
		for (String key : dn.keySet()) {
			if (dn.getLongOr(key, 0L) > nb) {
				nb = dn.getLongOr(key, 0L);
				best = key;
			}
		}
		try {
			return best == null ? null : UUID.fromString(best);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ================================================================== cells and checks

	/** The cells of stage {@code n} still to place: world positions, rotated targets, expected (record) states. */
	private record Rencana(long[] posisi, BlockState[] target, BlockState[] expected) {
	}

	private static Rencana sel(MinecraftServer server, ServerLevel level, CompoundTag c, RencanaTumbuh.Rencana plan, int n) {
		RencanaTumbuh.Tahap stage = plan.tahap(n);
		if (stage == null) {
			return new Rencana(new long[0], new BlockState[0], new BlockState[0]);
		}
		Rotation rot = Kavling.rotasi(c);
		BlockPos origin = Kavling.asal(c, plan);
		List<RencanaTumbuh.Sel> cells = RencanaTumbuh.sel(server, stage.template());
		List<Long> pos = new ArrayList<>(cells.size());
		List<BlockState> target = new ArrayList<>(cells.size());
		List<BlockState> expected = new ArrayList<>(cells.size());
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (RencanaTumbuh.Sel cell : cells) {
			BlockPos at = RencanaTumbuh.dunia(origin, rot, cell.x(), cell.y(), cell.z());
			BlockState want = cell.state().rotate(rot);
			BlockState now = level.getBlockState(at);
			if (now == want) {
				continue;
			}
			seen.add(at.asLong());
			pos.add(at.asLong());
			target.add(want);
			expected.add(expected(level, at, now));
		}
		if (n == 1) {
			// the surveyor's string and corner stakes inside the footprint go before the foundation
			BlockPos min = BlockPos.of(c.getLongOr("p", 0L));
			for (int dx = 0; dx <= c.getIntOr("dx", 6); dx++) {
				for (int dz = 0; dz <= c.getIntOr("dz", 6); dz++) {
					BlockPos at = min.offset(dx, 1, dz);
					BlockState now = level.getBlockState(at);
					boolean corner = (dx == 0 || dx == c.getIntOr("dx", 6)) && (dz == 0 || dz == c.getIntOr("dz", 6));
					if (!seen.contains(at.asLong()) && (now.is(Blocks.TRIPWIRE) || corner && !now.isAir())) {
						BlockState exp = expected(level, at, now);
						if (exp == now) {
							pos.add(at.asLong());
							target.add(Blocks.AIR.defaultBlockState());
							expected.add(exp);
						}
					}
				}
			}
		}
		long[] p = new long[pos.size()];
		for (int i = 0; i < p.length; i++) {
			p[i] = pos.get(i);
		}
		return new Rencana(p, target.toArray(new BlockState[0]), expected.toArray(new BlockState[0]));
	}

	/**
	 * What a cell must hold for a growth block to go in: the blueprint record; where nothing is recorded, the world's
	 * block when it is air or a plant that is replaced anyway (grass, snow), else air (so the job fails there).
	 */
	private static BlockState expected(ServerLevel level, BlockPos at, BlockState now) {
		BlockState rec = PerbaikanApi.cetakDi(level, at);
		if (rec != null) {
			return rec;
		}
		return now.isAir() || now.canBeReplaced() && now.getFluidState().isEmpty() ? now : Blocks.AIR.defaultBlockState();
	}

	private static boolean dimuat(ServerLevel level, BoundingBox box) {
		return level.isLoaded(new BlockPos(box.minX(), box.minY(), box.minZ())) && level.isLoaded(new BlockPos(box.maxX(), box.minY(), box.maxZ()))
				&& level.isLoaded(new BlockPos(box.minX(), box.minY(), box.maxZ())) && level.isLoaded(new BlockPos(box.maxX(), box.minY(), box.minZ()));
	}

	/**
	 * The 42.12.1A checks before a stage. Null = fine; {@code "!<reason>"} = never buildable ({@code bukan_kavling}: a
	 * corner or the centre outside our own plot piece; {@code struktur_asing}: a structure of another namespace touches
	 * the box + 2; {@code medan}: ground more than 2 blocks off or wet, before stage 1 only); {@code "klaim"} = a player
	 * claim covers part of it (waits).
	 */
	static @Nullable String periksa(ServerLevel level, CompoundTag c, boolean tanah) {
		if (!Level.OVERWORLD.equals(level.dimension())) {
			return "!dimensi";
		}
		BoundingBox box = Kavling.kotak(c);
		int y = box.minY() + 1;
		BlockPos[] own = {new BlockPos(box.minX(), y, box.minZ()), new BlockPos(box.maxX(), y, box.minZ()), new BlockPos(box.minX(), y, box.maxZ()),
				new BlockPos(box.maxX(), y, box.maxZ()), new BlockPos(box.getCenter().getX(), y, box.getCenter().getZ())};
		for (BlockPos p : own) {
			StructureStart start = level.structureManager().getStructureWithPieceAt(p,
					h -> h.unwrapKey().map(k -> Kultivator.MOD_ID.equals(k.identifier().getNamespace())).orElse(false));
			if (!start.isValid()) {
				return "!bukan_kavling";
			}
		}
		String foreign = asing(level, box);
		if (foreign != null) {
			c.putString("asing", foreign);
			return "!struktur_asing";
		}
		if (tanah && !tanahLayak(level, box)) {
			return "!medan";
		}
		for (BlockPos p : sampel(box.inflatedBy(0))) {
			if (KlaimPemain.diklaim(level, p.atY(y))) {
				return "klaim";
			}
		}
		return null;
	}

	/** The id of a structure of another namespace whose box touches {@code box} + 2, or null (9 sample points). */
	static @Nullable String asing(ServerLevel level, BoundingBox box) {
		BoundingBox wide = box.inflatedBy(2);
		var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (BlockPos p : sampel(wide)) {
			BlockPos at = p.atY(box.minY() + 1);
			if (!level.isLoaded(at)) {
				continue;
			}
			for (Map.Entry<Structure, LongSet> e : level.structureManager().getAllStructuresAt(at).entrySet()) {
				Identifier id = registry.getKey(e.getKey());
				if (id == null || Kultivator.MOD_ID.equals(id.getNamespace())) {
					continue;
				}
				boolean[] hit = {false};
				level.structureManager().fillStartsForStructure(e.getKey(), e.getValue(), start -> {
					if (start.isValid() && start.getBoundingBox().intersects(wide)) {
						hit[0] = true;
					}
				});
				if (hit[0]) {
					return id.toString();
				}
			}
		}
		return null;
	}

	/** Corners, edge middles and centre of a box (at its min y). */
	private static List<BlockPos> sampel(BoundingBox b) {
		int cx = (b.minX() + b.maxX()) / 2;
		int cz = (b.minZ() + b.maxZ()) / 2;
		List<BlockPos> out = new ArrayList<>(9);
		for (int x : new int[]{b.minX(), cx, b.maxX()}) {
			for (int z : new int[]{b.minZ(), cz, b.maxZ()}) {
				out.add(new BlockPos(x, b.minY(), z));
			}
		}
		return out;
	}

	/** Tectonic rule: the ground row of at least 90 % of the columns is solid within 2 blocks, and no fluid on it. */
	private static boolean tanahLayak(ServerLevel level, BoundingBox box) {
		int bad = 0;
		int total = 0;
		int y = box.minY();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				total++;
				boolean ground = false;
				for (int dy = -2; dy <= 0 && !ground; dy++) {
					BlockState s = level.getBlockState(new BlockPos(x, y + dy, z));
					ground = !s.isAir() && s.getFluidState().isEmpty();
				}
				boolean wet = !level.getBlockState(new BlockPos(x, y + 1, z)).getFluidState().isEmpty()
						|| !level.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty();
				if (!ground || wet) {
					bad++;
				}
			}
		}
		return bad * 10 <= total;
	}

	/** The foreman asks the players near a blocked plot to clear it (every two minutes at most). */
	private static void tegur(MinecraftServer server, ServerLevel level, long situs, CompoundTag c, String alasan) {
		long key = kunci(situs, c);
		long now = level.getGameTime();
		Long last = TEGUR.get(key);
		if (last != null && now - last < 2400L) {
			return;
		}
		TEGUR.put(key, now);
		Vec3 at = Vec3.atCenterOf(Kavling.kotak(c).getCenter());
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(at) <= 32.0D * 32.0D) {
				Msg.chat(player, Component.translatable("kultivator.kota42.tumbuh.tegur." + alasan, c.getIntOr("i", 0)).withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	// ================================================================== costs, needs, titik, payload

	/** NB cost of a whole plan at a place (every stage's units at today's unit price). */
	public static long biaya(MinecraftServer server, long situs, RencanaTumbuh.Rencana plan) {
		long n = 0L;
		for (int s = 1; s <= Kavling.SELESAI; s++) {
			n += biayaTahap(server, situs, plan, s);
		}
		return n;
	}

	public static long biayaTahap(MinecraftServer server, long situs, RencanaTumbuh.Rencana plan, int n) {
		RencanaTumbuh.Tahap stage = plan.tahap(n);
		if (stage == null) {
			return 0L;
		}
		long cost = 0L;
		int[] stock = BahanKota.stok(server, situs);
		for (int i = 0; i < stage.bahan().length; i++) {
			cost += Math.max(0, stage.bahan()[i] - stock[i]) * BahanKota.harga(server, situs, i);
		}
		return cost;
	}

	/** Units the next stage of the running plot still lacks (city orders), by kind. */
	static int[] kurang(MinecraftServer server, long situs) {
		int[] out = new int[BahanKota.JENIS.size()];
		CompoundTag c = Kavling.berjalan(server, situs);
		if (c == null || c.getIntOr("bn", 0) > c.getIntOr("t", 0)) {
			return out;
		}
		RencanaTumbuh.Rencana plan = RencanaTumbuh.get(server, c.getStringOr("r", ""));
		RencanaTumbuh.Tahap stage = plan == null ? null : plan.tahap(c.getIntOr("t", 0) + 1);
		if (stage == null) {
			return out;
		}
		int[] stock = BahanKota.stok(server, situs);
		for (int i = 0; i < out.length; i++) {
			out[i] = Math.max(0, stage.bahan()[i] - stock[i]);
		}
		return out;
	}

	/** What the stocks should hold for the running stage (Hari Warga production aims at it). */
	static int[] sasaran(MinecraftServer server, long situs) {
		int[] out = kurang(server, situs);
		int[] stock = BahanKota.stok(server, situs);
		for (int i = 0; i < out.length; i++) {
			out[i] += stock[i];
		}
		if (out[0] + out[1] == 0) {
			// nothing running: keep a small yard stock of wood and stone for the next plot
			out[0] = 24;
			out[1] = 24;
		}
		return out;
	}

	/** The stage-5 titik of every finished plot of a place ({@code TempatHuni.addSumberTitik}). */
	static List<Titik> titikSelesai(MinecraftServer server, long situs) {
		List<Titik> cached = TITIK.get(situs);
		if (cached != null) {
			return cached;
		}
		List<Titik> out = new ArrayList<>();
		for (CompoundTag c : Kavling.semua(server, situs)) {
			if (c.getIntOr("t", 0) >= Kavling.SELESAI) {
				RencanaTumbuh.Rencana plan = RencanaTumbuh.get(server, c.getStringOr("r", ""));
				if (plan != null) {
					out.addAll(RencanaTumbuh.titik(server, plan, Kavling.asal(c, plan), Kavling.rotasi(c)));
				}
			}
		}
		List<Titik> done = List.copyOf(out);
		TITIK.put(situs, done);
		return done;
	}

	/** Forgets the cached titik of a place (reset, test). */
	static void segarTitik(long situs) {
		TITIK.remove(situs);
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni != null) {
			huni.ubahTitik();
		}
	}

	/** Sends {@code kota_kavling} for the plots within {@link #JARAK_KIRIM} of each overworld player (the plan sign). */
	private static void kirim(MinecraftServer server, ServerLevel level) {
		if (level.players().isEmpty()) {
			return;
		}
		for (long situs : Kavling.tempat(server)) {
			for (CompoundTag c : Kavling.semua(server, situs)) {
				BoundingBox box = Kavling.kotak(c);
				Vec3 at = Vec3.atCenterOf(box.getCenter());
				int t = c.getIntOr("t", 0);
				int tahap = t < 0 ? 255 : t;
				short[] rel = {0, 0, 0, (short) (box.maxX() - box.minX()), (short) 17, (short) (box.maxZ() - box.minZ())};
				KotaPayloads.siar(level, at, JARAK_KIRIM, new KotaPayloads.Kavling(situs, c.getIntOr("i", 0),
						new BlockPos(box.minX(), box.minY(), box.minZ()).asLong(), rel, tahap, c.getStringOr("r", "")));
			}
		}
	}

	/** Lang name of a plan: "Rumah Toko (gaya ibu kota pasar)". */
	public static Component namaRencana(RencanaTumbuh.Rencana plan) {
		return Component.translatable("kultivator.kota42.tumbuh.rencana_nama", Component.translatable("kultivator.kota42.tumbuh.jenis." + plan.jenis()),
				Component.translatable("kultivator.kota42.tumbuh.gaya." + plan.gaya()));
	}

	/** Nama Baik of a player at a place (38 reputation; never in the demon city). */
	static void namaBaik(ServerPlayer player, long situs, int delta) {
		if (delta != 0 && !PajakKota.kotaIblis(player.level().getServer(), situs)) {
			KotaData.get(player.level().getServer()).addReputation(situs, player.getUUID(), delta);
		}
	}

	// ================================================================== tests (uji kota_hidup tumbuh)

	/** Starts plot {@code c} now (no trigger, no cost check): waiting for its next stage. */
	static void paksaMulai(MinecraftServer server, CompoundTag c) {
		if (c.getIntOr("t", 0) < 0 || c.getIntOr("t", 0) >= Kavling.SELESAI) {
			return;
		}
		RencanaTumbuh.Rencana plan = RencanaTumbuh.get(server, c.getStringOr("r", ""));
		if (plan != null) {
			c.putLong("biaya", biaya(server, 0L, plan));
		}
		if (c.getLongOr("m0", -1L) < 0L) {
			c.putLong("m0", KotaHidup.hari(server));
		}
		Kavling.keadaan(c, KotaLembar.Kavling.KV_MENUNGGU, "");
		COBA.clear();
		KotaTumbuhData.get(server).ubah();
	}

	/** Re-runs the checks of plot {@code c} for the report ({@code kompat periksa}): null fine, else the reason. */
	static @Nullable String periksaUji(ServerLevel level, CompoundTag c) {
		return dimuat(level, Kavling.kotak(c)) ? periksa(level, c, c.getIntOr("t", 0) == 0) : "jauh";
	}

	private PekerjaanTumbuh() {
	}
}
