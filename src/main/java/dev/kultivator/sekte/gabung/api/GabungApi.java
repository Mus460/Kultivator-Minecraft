package dev.kultivator.sekte.gabung.api;

import com.mojang.brigadier.builder.ArgumentBuilder;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.Aturan;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.GabungUji;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.util.Advancements;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The static server API of Gabung Sekte (DESIGN 41.14.3, contract C41.A1). Every 41 module and every other system
 * (stores refusing sect goods, ambushes, quests) asks here: who belongs where, at what rank, with how much PK; the
 * sect's class and style; its named spots. Server thread only; every method is safe while the module is off (it then
 * answers "not a member" and changes nothing).
 * <p>
 * The flow methods ({@link #terima}, {@link #keluar}, {@link #langgar}) change the data and fire {@link GabungBus};
 * ceremonies, dialogs and punishments are written by the modules that listen (41A {@code Keluar}/{@code BalaiHukum},
 * 41B the oath, 41D hunters), so the data door stays one. PK always passes {@link #tambahPk}/{@link #bayarPk}: one PK
 * unit, never exchanged for NB or stones (K8).
 */
public final class GabungApi {

	/** A source of named spots tried before the compound and the gate (41W {@code TitikGabung}: new spots, town office). */
	@FunctionalInterface
	public interface PenyediaTitik {
		@Nullable BlockPos titik(MinecraftServer server, String sekte, String titik);
	}

	/** A listener of recorded violations (41A {@code BalaiHukum}: reprimand, fine, confinement, trial). */
	@FunctionalInterface
	public interface Penegak {
		void langgar(ServerPlayer player, Keanggotaan k, Aturan aturan, BlockPos at, int sebelum, int sesudah);
	}

	private static final List<PenyediaTitik> TITIK = new CopyOnWriteArrayList<>();
	private static final List<Penegak> PENEGAK = new CopyOnWriteArrayList<>();

	// ================================================================== who belongs where

	/** The player's record (created on first use). */
	public static Keanggotaan anggota(ServerPlayer player) {
		return GabungData.get(player.level().getServer()).anggota(player.getUUID());
	}

	/** The record of {@code player} (online or not); a fresh unsaved record when the server is not running. */
	public static Keanggotaan anggota(UUID player) {
		MinecraftServer server = Gabung.server();
		return server == null ? new Keanggotaan() : GabungData.get(server).anggota(player);
	}

	/** The sect {@code player} belongs to right now, or null. */
	public static @Nullable String sekte(UUID player) {
		Keanggotaan k = peek(player);
		return k == null ? null : k.sekteAktif();
	}

	/** The player's rank (Pelayan when not a member; check {@link #sekte} first). */
	public static Pangkat pangkat(UUID player) {
		Keanggotaan k = peek(player);
		return k == null || !k.status.anggota() ? Pangkat.PELAYAN : k.pangkat;
	}

	/** True when {@code player} belongs to {@code sekte} right now. */
	public static boolean anggota(UUID player, String sekte) {
		Keanggotaan k = peek(player);
		return k != null && k.anggotaDi(sekte);
	}

	private static @Nullable Keanggotaan peek(UUID player) {
		MinecraftServer server = Gabung.server();
		return server == null || player == null ? null : GabungData.get(server).peek(player);
	}

	// ================================================================== PK (one door, K8)

	/**
	 * Adds {@code n} personal PK (already multiplied by the class factor where 41.6.2 says so) for {@code sebab}
	 * ({@code "gabung.<what>"}); {@code gabungSekte.skalaPk} applies here. Ignored for non-members and while off.
	 */
	public static void tambahPk(ServerPlayer player, int n, String sebab) {
		if (n <= 0 || !Gabung.aktif()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || !k.status.anggota()) {
			return;
		}
		int add = Math.max(1, (int) Math.round(n * GabungConfig.get().skalaPk));
		long hari = Kota.day(server);
		int lama = k.pk;
		k.pk = (int) Math.min(Integer.MAX_VALUE, (long) k.pk + add);
		k.pkTotal += add;
		k.gulirBulan(hari);
		k.pkBulan += add;
		k.catat(hari, "kultivator.gabung.riwayat.pk_tambah", Integer.toString(add), sebab);
		data.setDirty();
		GabungBus.PK_BERUBAH.invoker().onPkBerubah(player, k.sekte, lama, k.pk, sebab);
	}

	/** Spends {@code n} PK for {@code sebab}; false (nothing spent) when the member has fewer or is not a member. */
	public static boolean bayarPk(ServerPlayer player, int n, String sebab) {
		if (n < 0 || !Gabung.aktif()) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || !k.status.anggota() || k.pk < n) {
			return false;
		}
		if (n == 0) {
			return true;
		}
		int lama = k.pk;
		k.pk -= n;
		k.catat(Kota.day(server), "kultivator.gabung.riwayat.pk_bayar", Integer.toString(n), sebab);
		data.setDirty();
		GabungBus.PK_BERUBAH.invoker().onPkBerubah(player, k.sekte, lama, k.pk, sebab);
		return true;
	}

	/**
	 * {@link #tambahPk} under a daily cap shared by group {@code kelompok} (41.6.2: e.g. {@code "persembahan"} for orders
	 * and donations together at {@code Contribution.OFFER_DAILY_CAP} 300, {@code "tugas"} 3 chores): adds at most what is
	 * left of {@code batasHarian} today (counted before {@code skalaPk}) and returns the amount granted (0 when capped).
	 */
	public static int tambahPkBatas(ServerPlayer player, int n, String sebab, String kelompok, int batasHarian) {
		if (n <= 0 || !Gabung.aktif() || kelompok == null) {
			return 0;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || !k.status.anggota()) {
			return 0;
		}
		net.minecraft.nbt.CompoundTag batas = k.bagian("pk_batas");
		long hari = Kota.day(server);
		if (batas.getLongOr("hari", -1L) != hari) {
			for (String key : java.util.List.copyOf(batas.keySet())) {
				batas.remove(key);
			}
			batas.putLong("hari", hari);
		}
		int sudah = batas.getIntOr(kelompok, 0);
		int beri = Math.min(n, Math.max(0, batasHarian - sudah));
		if (beri <= 0) {
			return 0;
		}
		batas.putInt(kelompok, sudah + beri);
		data.setDirty();
		tambahPk(player, beri, sebab);
		return beri;
	}

	/** PK of group {@code kelompok} already granted today under {@link #tambahPkBatas}. */
	public static int pkHariIni(ServerPlayer player, String kelompok) {
		Keanggotaan k = peek(player.getUUID());
		if (k == null) {
			return 0;
		}
		net.minecraft.nbt.CompoundTag batas = k.bagian("pk_batas");
		return batas.getLongOr("hari", -1L) == Kota.day(player.level().getServer()) ? batas.getIntOr(kelompok, 0) : 0;
	}

	/** A promotion cost in PK: base x class factor x {@code skalaBiaya} (41.6.1). */
	public static int biaya(String sekte, int dasar) {
		return (int) Math.round(dasar * kelasSekte(sekte).kf() * GabungConfig.get().skalaBiaya);
	}

	// ================================================================== flow (data door; ceremonies listen to the bus)

	/**
	 * Records a violation of {@code aturan} at {@code at}. With {@code perluSaksi} it counts only when witnessed
	 * ({@code HidupApi.saksi}). Demonic sects count A3 only when witnessed and A1/A7 only per their own wording (the
	 * caller decides whether a challenge came first). The registered {@link Penegak}s then act on the new record.
	 */
	public static void langgar(ServerPlayer player, Aturan aturan, BlockPos at, boolean perluSaksi) {
		if (!Gabung.aktif() || aturan == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || !k.status.anggota()) {
			return;
		}
		boolean iblis = SekteApi.diplomasi().aliran(k.sekte) == 2;
		boolean saksiPerlu = perluSaksi || iblis && aturan == Aturan.A3;
		if (saksiPerlu && !HidupApi.saksi(player.level(), at, player)) {
			return;
		}
		int sebelum = k.pelanggaran;
		// the week of the member who read the rules at the gate (PahamAturan): half the points
		int poin = dev.kultivator.sekte.gabung.PahamAturan.poin(k, Kota.day(server), aturan == Aturan.A2, aturan.poin());
		k.pelanggaran = Math.min(Aturan.MAKS, k.pelanggaran + poin);
		k.catat(Kota.day(server), "kultivator.gabung.riwayat.langgar", aturan.id(), Integer.toString(poin));
		data.setDirty();
		for (Penegak p : PENEGAK) {
			try {
				p.langgar(player, k, aturan, at, sebelum, k.pelanggaran);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Gabung enforcer failed", exception);
			}
		}
	}

	/** Registers an enforcer (41A {@code BalaiHukum}). */
	public static void addPenegak(Penegak penegak) {
		if (penegak != null) {
			PENEGAK.add(penegak);
		}
	}

	/**
	 * Makes the player a member of {@code sekte} at {@code pangkat} through {@code pintu} (after the verdict and the oath,
	 * or a test command): status ANGGOTA, membership day, Tamu path for the guest door, standing +10
	 * ({@code gabung.sumpah}), the advancements, the lencana sync, then {@link GabungBus#DITERIMA}.
	 */
	public static void terima(ServerPlayer player, String sekte, Pangkat pangkat, Pintu pintu) {
		if (!Gabung.aktif() || sekte == null || SekteApi.diplomasi().tier(sekte) <= 0) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		long hari = Kota.day(server);
		boolean kembali = k.riwayatSekte.containsKey(sekte);
		k.sekte = sekte;
		k.status = StatusAnggota.ANGGOTA;
		k.pangkat = pangkat;
		k.jalur = pintu == Pintu.TAMU || pangkat == Pangkat.TAMU ? Keanggotaan.JALUR_TAMU : Keanggotaan.JALUR_MURID;
		k.pintu = pintu;
		k.hariMasuk = hari;
		k.hariPangkat = hari;
		k.calonSampai = -1L;
		k.hariUjian = -1L;
		if (k.ikatanShifu <= 0) {
			k.ikatanShifu = 20;
		}
		Keanggotaan.Riwayat lalu = k.riwayatSekte.get(sekte);
		if (lalu != null && lalu.status() == StatusKeluar.PAMIT && lalu.pkBeku() > 0) {
			k.pk += lalu.pkBeku();
		}
		k.riwayatSekte.remove(sekte);
		GerbangPlan.Entry e = entry(server, sekte);
		if (e != null) {
			k.tandaiKenal(e.index());
		}
		k.catat(hari, "kultivator.gabung.riwayat.terima", sekte, pangkat.id(), pintu.id());
		data.setDirty();
		SekteApi.diplomasi().adjust(player.getUUID(), sekte, 10, "gabung.sumpah");
		Advancements.grant(player, "sekte/gabung_kenal");
		Advancements.grant(player, "sekte/gabung_diterima");
		if (kembali) {
			Advancements.grant(player, "sekte/gabung_kembali");
		}
		advPangkat(player, pangkat);
		dev.kultivator.sekte.gabung.PahamAturan.saatMasuk(player, k);
		GabungPayloads.siarkanLencana(player);
		GabungBus.DITERIMA.invoker().onDiterima(player, sekte, pangkat);
	}

	/**
	 * Sets the member's rank (promotion, seat won or lost, demotion) with its advancement and the lencana sync, then
	 * {@link GabungBus#PANGKAT_NAIK}. Costs and requirements are the caller's (41A rank tree, 41D seats).
	 */
	public static void setPangkat(ServerPlayer player, Pangkat baru, String sebab) {
		if (!Gabung.aktif() || baru == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || !k.status.anggota() || k.pangkat == baru) {
			return;
		}
		Pangkat lama = k.pangkat;
		long hari = Kota.day(server);
		k.pangkat = baru;
		k.hariPangkat = hari;
		k.catat(hari, "kultivator.gabung.riwayat.pangkat", lama.id(), baru.id(), sebab);
		data.setDirty();
		advPangkat(player, baru);
		GabungPayloads.siarkanLencana(player);
		GabungBus.PANGKAT_NAIK.invoker().onPangkatNaik(player, k.sekte, lama, baru);
	}

	/**
	 * Ends the membership as {@code status} (the data part of 41.7; 41A's {@code Keluar} runs the ceremony first):
	 * PAMIT freezes half the PK and gives standing +5; PAMIT_PAKSA and DIUSIR lower standing by the most
	 * {@code DiplomasiService.adjust} allows (-20; the design's -30 and the deserter's -60 are set exactly by 41D through
	 * {@code DiplomasiGabung.setStanding}, which listens to {@link GabungBus#KELUAR}); PEMBELOT starts the hunt window.
	 * Then the lencana sync and {@link GabungBus#KELUAR}.
	 */
	public static void keluar(ServerPlayer player, StatusKeluar status) {
		if (status == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || !k.status.anggota() || k.sekte.isEmpty()) {
			return;
		}
		String sekte = k.sekte;
		long hari = Kota.day(server);
		int beku = status == StatusKeluar.PAMIT ? k.pk / 2 : 0;
		k.riwayatSekte.put(sekte, new Keanggotaan.Riwayat(status, hari, beku));
		k.catat(hari, "kultivator.gabung.riwayat.keluar", sekte, status.id());
		if (status == StatusKeluar.PEMBELOT && GabungConfig.get().pemburu) {
			k.pemburuSampai = hari + GabungConfig.get().pemburuHari;
			k.pemburuMenang = 0;
		}
		k.lepas();
		data.setDirty();
		DiplomasiService d = SekteApi.diplomasi();
		switch (status) {
			case PAMIT -> d.adjust(player.getUUID(), sekte, 5, "gabung.pamit");
			case PAMIT_PAKSA, DIUSIR -> d.adjust(player.getUUID(), sekte, -20, "gabung." + status.id());
			case PEMBELOT -> {
				// the -60 of 41.7.3 is applied by 41D through DiplomasiGabung.setStanding (adjust is clamped to +-20)
			}
		}
		switch (status) {
			case DIUSIR -> Advancements.grant(player, "sekte/gabung_diusir");
			case PEMBELOT -> Advancements.grant(player, "sekte/gabung_pembelot");
			default -> {
			}
		}
		GabungPayloads.siarkanLencana(player);
		GabungBus.KELUAR.invoker().onKeluar(player, sekte, status);
	}

	/** Adds sect {@code sekte} to the player's directory (visit, rumour, recruiter, map); true when it was new. */
	public static boolean kenal(ServerPlayer player, String sekte) {
		MinecraftServer server = player.level().getServer();
		GerbangPlan.Entry e = entry(server, sekte);
		if (e == null) {
			return false;
		}
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		if (!k.tandaiKenal(e.index())) {
			return false;
		}
		data.setDirty();
		Advancements.grant(player, "sekte/gabung_kenal");
		GabungPayloads.kirimKelas(player);
		return true;
	}

	private static void advPangkat(ServerPlayer player, Pangkat pangkat) {
		String adv = switch (pangkat) {
			case LUAR -> "pangkat_luar";
			case DALAM -> "pangkat_dalam";
			case INTI -> "pangkat_inti";
			case PEWARIS -> "pewaris";
			case TETUA -> "tetua";
			case KETUA -> "ketua";
			case TAMU -> "tamu";
			default -> null;
		};
		if (adv != null) {
			Advancements.grant(player, "sekte/gabung_" + adv);
		}
	}

	// ================================================================== the sects

	/** The sect's tier 1-9 (0 for an unknown sect). */
	public static int tier(String sekte) {
		return sekte == null ? 0 : SekteApi.diplomasi().tier(sekte);
	}

	/** The sect's class number 1-5 (1 for an unknown sect). */
	public static int kelas(String sekte) {
		return KelasSekte.of(Math.max(1, tier(sekte)));
	}

	public static KelasSekte kelasSekte(String sekte) {
		return KelasSekte.byNomor(kelas(sekte));
	}

	/** The sect's style from word A of its name ({@link Corak#PEDANG} for an unknown sect). */
	public static Corak corak(String sekte) {
		MinecraftServer server = Gabung.server();
		GerbangPlan.Entry e = server == null ? null : entry(server, sekte);
		return e == null ? Corak.PEDANG : Corak.of(e.kataA());
	}

	/** The planned entry of {@code sekte} in this world's plan, or null. */
	public static @Nullable GerbangPlan.Entry entry(MinecraftServer server, String sekte) {
		return server == null || sekte == null ? null : GerbangPlan.of(server.overworld().getSeed()).entry(sekte);
	}

	/**
	 * A named spot of the sect: the registered providers (41W new spots, the town office), then the compound
	 * ({@code KompleksApi.spot}), then the gate ({@code DiplomasiService.gate}, else its planned column); null when none.
	 */
	public static @Nullable BlockPos titik(String sekte, String titik) {
		MinecraftServer server = Gabung.server();
		if (server == null || sekte == null || titik == null) {
			return null;
		}
		for (PenyediaTitik p : TITIK) {
			BlockPos pos = p.titik(server, sekte, titik);
			if (pos != null) {
				return pos;
			}
		}
		BlockPos spot = KompleksApi.spot(sekte, titik);
		if (spot != null) {
			return spot;
		}
		DiplomasiService d = SekteApi.diplomasi();
		BlockPos gate = d.gate(sekte);
		return gate != null ? gate : d.plannedGate(sekte);
	}

	/** Registers a spot provider (41W). */
	public static void addPenyediaTitik(PenyediaTitik penyedia) {
		if (penyedia != null) {
			TITIK.add(penyedia);
		}
	}

	// ================================================================== test commands, busy states, misc

	/** Hangs {@code uji gabung <sub>} (op 2) under the root of {@code GabungUji}; call from a module's init. */
	public static void daftarUji(String sub, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>> node) {
		GabungUji.daftar(sub, node);
	}

	/**
	 * Marks the player busy with {@code keadaan} ({@code gabung.ujian}, {@code gabung.sumpah}, {@code gabung.kurungan},
	 * {@code gabung.perang}, {@code gabung.suksesi}; 41.0 item 6) for {@code ticks} game ticks (the Sibuk door asks).
	 */
	public static void sibuk(ServerPlayer player, String keadaan, int ticks) {
		Gabung.tandaiSibuk(player, keadaan, ticks);
	}

	/** Ends a busy state early. */
	public static void lepasSibuk(ServerPlayer player, String keadaan) {
		Gabung.lepasSibuk(player, keadaan);
	}

	/** Marks a live deserter-hunter squad on the player (the shared enemy pressure, {@code gabung.pemburu}). */
	public static void pemburuHidup(ServerPlayer player, boolean hidup) {
		Gabung.pemburuHidup(player, hidup);
	}

	/** The world day (the town clock, overworld time / 24000). */
	public static long hari(MinecraftServer server) {
		return Kota.day(server);
	}

	/** True while Gabung Sekte runs (phase switch and {@code gabungSekte.aktif}). */
	public static boolean aktif() {
		return Gabung.aktif();
	}

	private GabungApi() {
	}
}
