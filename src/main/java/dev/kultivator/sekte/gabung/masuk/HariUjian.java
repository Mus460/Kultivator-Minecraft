package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.Corak;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.technique.FlightLease;
import dev.kultivator.technique.FlyingSword;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The test day of DESIGN 41.5.2-41.5.5 (contract C41.B1, owner 41B). On a sect's Hari Ujian (06:00-12:00 of every
 * {@code gabungSekte.hariUjian}-th day shifted by the plan index; class Kecil any daytime) the gate guard
 * ({@code PENJAGA_GERBANG} at {@code kompleks/gerbang}, else the mountain gate) calls the candidates within 32 blocks
 * ({@code gabung.gerbang_panggil}); talking to him offers "Mulai ujian" (data dialog {@code gabung_gerbang}, effect
 * {@code gabung.mulai_ujian}), which reaches {@link #mulai}.
 * <p>
 * {@link #mulai} runs the stages the door asks for, one {@link Sesi} per player and one per sect at a time (another
 * candidate waits 60 s): GERBANG 1-2-3, TOKEN 2-3 (the token is used up), UNDANGAN 3 (the letter is used up), PELAYAN the
 * one-question talk, TAMU the guest trial of 41.8.3. Stage 1 is {@link TanggaUjian}, stage 2 the corak test of 41B-C
 * (registered through {@link #daftarTahap2}), stage 3 {@link UjianHati}; then {@link Putusan}. During every stage the busy
 * state {@code gabung.ujian} holds, the player's disciples may not follow, flying is refused (the flight lease is taken
 * back) and a jump of more than 12 blocks in one tick (pearl, chorus, warp) is put back. Death, leaving the sect's
 * ground (160 blocks) or logging out ends the test: refused, the 3-day pause, nothing else lost (41.5.4).
 * <p>
 * The HUD goes through {@code GabungPayloads.kirimUjian} every 5 ticks (layout: see {@link #hud}); 41B-C sends its own
 * during stage 2.
 */
public final class HariUjian {

	/** {@code gabungSekte.hariUjian} default: a sect tests every 10 world days. */
	public static final int HARI_UJIAN_BAWAAN = 10;
	/** Time of day the test window opens and closes (06:00 + 6000 ticks .. 12000, 41.5.2). */
	public static final int BUKA_TICK = 6000;
	public static final int TUTUP_TICK = 12000;
	/** A second candidate of the same sect waits this long after a test ended (41.5.4: 60 s). */
	public static final int ANTRE_TICK = 1200;
	/** Leaving this radius around the sect's ground ends the test. */
	public static final double JARI_WILAYAH = 160.0D;
	/** The guard calls a candidate within this distance of the gate. */
	public static final double JARI_PANGGIL = 32.0D;
	/** A candidate must stand this close to the gate to start (tests skip it). */
	public static final double JARI_MULAI = 48.0D;
	/** Busy state refresh: held for this long, renewed every second while the test runs. */
	private static final int SIBUK_TICK = 400;
	/** A move longer than this in one tick is a teleport and is undone. */
	private static final double LOMPAT_MAKS = 12.0D;
	/** Score a stage skipped by the door counts (41.5.5). */
	public static final double SKOR_DILEWATI = 0.8D;

	/** Stage ids of {@link Sesi#tahap}: 1-3 of 41.5.4, the servant's talk, the guest trial, the verdict. */
	public static final int TAHAP_TANGGA = 1;
	public static final int TAHAP_CORAK = 2;
	public static final int TAHAP_HATI = 3;
	public static final int TAHAP_WAWANCARA = 4;
	public static final int TAHAP_TAMU = 5;
	public static final int TAHAP_PUTUSAN = 6;

	/**
	 * The corak test of stage 2 (41B-C {@code UjianCorak} registers one). {@link #mulai} builds the field around
	 * {@code kompleks/ujian_corak} (or its own titik) and returns null, or returns why it cannot run (the stage then counts as
	 * skipped, 0.8). The test ends by calling {@link HariUjian#tahap2Selesai}. {@link #batal} removes everything when the
	 * test day ends early (death in another stage, log out, a test command).
	 */
	public interface Tahap2 {
		@Nullable Component mulai(ServerPlayer player, Sesi sesi);

		void batal(ServerPlayer player);
	}

	/** One running entrance test (server thread only). Fields named here are read by 41B-C and the verdict. */
	public static final class Sesi {
		public final UUID pemain;
		public final String sekte;
		public final Pintu pintu;
		/** The style of the stage-2 test (the sect's own; a test command may pick another). */
		public Corak corak;
		/** Class 1-5 and tier 1-9 of the sect, alignment 0-2. */
		public final int kelas;
		public final int tier;
		public final int aliran;
		/** Started by {@code uji gabung ujian}: no candidacy needed, no day rule; with {@link #hanyaTahap} one stage only. */
		public final boolean uji;
		public final int hanyaTahap;
		/** Stage scores 0-1; -1 = not run; {@link #dilewati} marks a stage the door skips (0.8). */
		public double s1 = -1.0D;
		public double s2 = -1.0D;
		public double s3 = -1.0D;
		public final boolean[] dilewati = new boolean[4];
		/** Ujian Hati impression (41.5.4) and the candidate traits it revealed ({@code teguh}, {@code licik} ...). */
		public int kesan;
		public final List<String> sifat = new ArrayList<>();
		/** Peak Mountain Pressure of stage 1 (for the verdict card). */
		public double tekananPuncak;
		/** The stage running now ({@code TAHAP_*}) and the game tick it began. */
		public int tahap;
		public long mulaiTahap;
		public final long mulai;
		/** Free scratch of the running stage (cleared at every stage change). */
		public final CompoundTag data = new CompoundTag();
		/** Centre of the sect's ground (gate or compound) and its dimension. */
		public final BlockPos pusat;
		public final ResourceKey<Level> dimensi;
		/** Bodies spawned for the day (guard, child candidates, the interviewing elder) to send home at the end. */
		final List<UUID> tubuh = new ArrayList<>();
		final int[] urutan;
		int langkah = -1;
		@Nullable Vec3 terakhir;
		boolean selesai;

		Sesi(UUID pemain, String sekte, Pintu pintu, Corak corak, int tier, int aliran, boolean uji, int hanyaTahap, BlockPos pusat,
				ResourceKey<Level> dimensi, long mulai) {
			this.pemain = pemain;
			this.sekte = sekte;
			this.pintu = pintu;
			this.corak = corak;
			this.tier = tier;
			this.kelas = KelasSekte.of(Math.max(1, tier));
			this.aliran = aliran;
			this.uji = uji;
			this.hanyaTahap = hanyaTahap;
			this.pusat = pusat;
			this.dimensi = dimensi;
			this.mulai = mulai;
			this.urutan = urutan(pintu, hanyaTahap);
		}

		/** Ticks the current stage has run. */
		public long umurTahap(long now) {
			return Math.max(0L, now - mulaiTahap);
		}

		/** The stage score of {@code tahap} 1-3 as the verdict counts it (skipped 0.8, not run 0). */
		public double skor(int tahap) {
			if (tahap >= 1 && tahap <= 3 && dilewati[tahap]) {
				return SKOR_DILEWATI;
			}
			double s = switch (tahap) {
				case 1 -> s1;
				case 2 -> s2;
				case 3 -> s3;
				default -> -1.0D;
			};
			return Math.max(0.0D, s);
		}

		/** Remembers a body to send home when the test day ends. */
		public void ikat(Entity e) {
			if (e != null && !tubuh.contains(e.getUUID())) {
				tubuh.add(e.getUUID());
			}
		}

		private static int[] urutan(Pintu pintu, int hanya) {
			if (hanya >= 1 && hanya <= 3) {
				return new int[]{hanya};
			}
			return switch (pintu) {
				case GERBANG -> new int[]{TAHAP_TANGGA, TAHAP_CORAK, TAHAP_HATI};
				case TOKEN -> new int[]{TAHAP_CORAK, TAHAP_HATI};
				case UNDANGAN -> new int[]{TAHAP_HATI};
				case PELAYAN -> new int[]{TAHAP_WAWANCARA};
				case TAMU -> new int[]{TAHAP_TAMU};
			};
		}
	}

	private static final Map<UUID, Sesi> SESI = new HashMap<>();
	/** sect -> game tick its last test ended (the 60 s wait). */
	private static final Map<String, Long> SELESAI_SEKTE = new HashMap<>();
	/** player -> world day the guard last called them. */
	private static final Map<UUID, Long> DIPANGGIL = new HashMap<>();
	private static @Nullable Tahap2 tahap2;
	private static boolean initialised;

	/** Registers the corak test of stage 2 (41B-C). The last registration wins. */
	public static void daftarTahap2(Tahap2 t) {
		if (t != null) {
			if (tahap2 != null && tahap2 != t) {
				Kultivator.LOGGER.warn("Gabung: stage-2 test registered twice; the later one wins");
			}
			tahap2 = t;
		}
	}

	/** True when world day {@code hari} is a test day of the sect with plan index {@code indeks} (41.5.2). */
	public static boolean hariUjian(int indeks, long hari, int hariUjian) {
		int period = Math.max(1, hariUjian);
		return Math.floorMod(hari + indeks * 3L, period) == 0L;
	}

	/** The first test day of the sect at or after {@code hari}. */
	public static long berikut(int indeks, long hari, int hariUjian) {
		int period = Math.max(1, hariUjian);
		long wait = Math.floorMod(-(hari + indeks * 3L), period);
		return hari + wait;
	}

	/** The next test day of {@code sekte} at or after today (class Kecil: today), or -1 for an unknown sect. */
	public static long berikutUntuk(MinecraftServer server, String sekte) {
		var e = GabungApi.entry(server, sekte);
		if (e == null) {
			return -1L;
		}
		long hari = Kota.day(server);
		if (GabungApi.kelas(sekte) == KelasSekte.KECIL.nomor()) {
			return Kota.timeOfDay(server) < TUTUP_TICK ? hari : hari + 1L;
		}
		long next = berikut(e.index(), hari, GabungConfig.get().hariUjian);
		if (next == hari && Kota.timeOfDay(server) >= TUTUP_TICK) {
			next = berikut(e.index(), hari + 1L, GabungConfig.get().hariUjian);
		}
		return next;
	}

	/** True right now when {@code sekte} receives candidates (its test day, inside the window; Kecil: daytime). */
	public static boolean bukaSekarang(MinecraftServer server, String sekte) {
		var e = GabungApi.entry(server, sekte);
		if (e == null) {
			return false;
		}
		long tod = Kota.timeOfDay(server);
		if (GabungApi.kelas(sekte) == KelasSekte.KECIL.nomor()) {
			return tod < TUTUP_TICK;
		}
		return tod >= BUKA_TICK && tod < TUTUP_TICK && hariUjian(e.index(), Kota.day(server), GabungConfig.get().hariUjian);
	}

	/** The running test of {@code player}, or null. */
	public static @Nullable Sesi sesi(ServerPlayer player) {
		return player == null ? null : SESI.get(player.getUUID());
	}

	public static @Nullable Sesi sesi(UUID player) {
		return SESI.get(player);
	}

	/** True while {@code player} sits a test. */
	public static boolean sedangUjian(ServerPlayer player) {
		Sesi s = sesi(player);
		return s != null && !s.selesai;
	}

	// ================================================================== start

	/**
	 * Starts the entrance test of {@code sekte} for {@code player} through door {@code pintu}. Returns null when it
	 * started, else the reason shown to the player (not a candidate, wrong day, busy, another test running, too far).
	 */
	public static @Nullable Component mulai(ServerPlayer player, String sekte, Pintu pintu) {
		return mulai(player, sekte, pintu, false, 0, null);
	}

	/**
	 * The full form: {@code uji} skips candidacy, the day rule and the distance; {@code hanyaTahap} 1-3 runs only that stage
	 * (a test command); {@code corak} replaces the sect's style for stage 2 (null = the sect's).
	 */
	public static @Nullable Component mulai(ServerPlayer player, String sekte, Pintu pintu, boolean uji, int hanyaTahap, @Nullable Corak corak) {
		if (!Gabung.aktif()) {
			return Component.translatable("kultivator.gabung.cmd.mati");
		}
		MinecraftServer server = player.level().getServer();
		DiplomasiService d = SekteApi.diplomasi();
		int tier = d.tier(sekte);
		if (server == null || tier <= 0) {
			return Masuk.t("ujian.tak_dikenal", sekte);
		}
		if (SESI.containsKey(player.getUUID())) {
			return Masuk.t("ujian.sudah_jalan");
		}
		Keanggotaan k = GabungApi.anggota(player);
		if (!uji) {
			if (k.status != StatusAnggota.CALON || !sekte.equals(k.sekte)) {
				return Masuk.t("ujian.bukan_calon", Masuk.nama(sekte));
			}
			if (pintu != Pintu.PELAYAN && pintu != Pintu.TAMU && !bukaSekarang(server, sekte)) {
				long next = berikutUntuk(server, sekte);
				return Masuk.t("ujian.bukan_hari", Masuk.nama(sekte), Math.max(0L, next - Kota.day(server)));
			}
		}
		String sibuk = Sibuk.why(player);
		if (sibuk != null) {
			return Sibuk.text(sibuk);
		}
		for (Sesi other : SESI.values()) {
			if (other.sekte.equals(sekte) && !other.selesai) {
				return Masuk.t("ujian.antre", Masuk.nama(sekte));
			}
		}
		long now = player.level().getGameTime();
		Long ended = SELESAI_SEKTE.get(sekte);
		if (!uji && ended != null && now - ended < ANTRE_TICK) {
			return Masuk.t("ujian.antre_detik", (ANTRE_TICK - (now - ended) + 19) / 20);
		}
		BlockPos gerbang = gerbang(sekte);
		if (gerbang == null) {
			return Masuk.t("ujian.tanpa_tempat", Masuk.nama(sekte));
		}
		if (!uji && Masuk.datar(player.position(), gerbang) > JARI_MULAI && Masuk.datar(player.position(), pusat(sekte, gerbang)) > JARI_WILAYAH) {
			return Masuk.t("ujian.terlalu_jauh", Masuk.nama(sekte));
		}
		if (!uji && pintu == Pintu.TOKEN && !TokenNaik.pakai(player, sekte)) {
			return Masuk.t("ujian.tanpa_token");
		}
		if (!uji && pintu == Pintu.UNDANGAN) {
			TokenNaik.pakaiUndangan(player, sekte);
		}
		Corak c = corak != null ? corak : GabungApi.corak(sekte);
		Sesi s = new Sesi(player.getUUID(), sekte, pintu, c, tier, Math.max(0, d.aliran(sekte)), uji, hanyaTahap, pusat(sekte, gerbang),
				player.level().dimension(), now);
		for (int t = 1; t <= 3; t++) {
			s.dilewati[t] = !pintu.jalani(t) && hanyaTahap == 0;
		}
		SESI.put(player.getUUID(), s);
		GabungApi.sibuk(player, Gabung.SIBUK_UJIAN, SIBUK_TICK);
		s.terakhir = player.position();
		Msg.chat(player, Masuk.t("ujian.mulai", Masuk.nama(sekte), pintu.nama(), daftarTahap(s)).withStyle(ChatFormatting.GOLD));
		KultivatorNpc penjaga = penjaga(player.level(), sekte, false);
		if (penjaga != null) {
			Masuk.ucap(penjaga, "gabung.ujian_mulai", "elder.welcome", Masuk.t("ucap.ujian_mulai"));
		}
		lanjut(player, s);
		return null;
	}

	private static Component daftarTahap(Sesi s) {
		List<Component> names = new ArrayList<>();
		for (int t : s.urutan) {
			names.add(namaTahap(t, s.corak));
		}
		return Component.literal(String.join(" · ", names.stream().map(Component::getString).toList()));
	}

	/** The stage's display name (corak test name for stage 2). */
	public static Component namaTahap(int tahap, Corak corak) {
		return switch (tahap) {
			case TAHAP_TANGGA -> Masuk.t("tahap.tangga");
			case TAHAP_CORAK -> corak.namaUjian();
			case TAHAP_HATI -> Masuk.t("tahap.hati");
			case TAHAP_WAWANCARA -> Masuk.t("tahap.wawancara");
			case TAHAP_TAMU -> Masuk.t("tahap.tamu");
			default -> Masuk.t("tahap.putusan");
		};
	}

	// ================================================================== stage flow

	/** Moves to the next stage of the door's list, or to the verdict. */
	static void lanjut(ServerPlayer player, Sesi s) {
		if (s.selesai) {
			return;
		}
		s.langkah++;
		s.data.keySet().clear();
		s.mulaiTahap = player.level().getGameTime();
		GabungPayloads.kirimUjian(player, 0, 0.0F, 0, 0.0F, null);
		if (s.langkah >= s.urutan.length) {
			s.tahap = TAHAP_PUTUSAN;
			akhir(player, s);
			return;
		}
		s.tahap = s.urutan[s.langkah];
		Component why = switch (s.tahap) {
			case TAHAP_TANGGA -> TanggaUjian.mulai(player, s);
			case TAHAP_CORAK -> mulaiCorak(player, s);
			case TAHAP_HATI -> UjianHati.mulai(player, s, false);
			case TAHAP_WAWANCARA -> UjianHati.mulai(player, s, true);
			case TAHAP_TAMU -> UjianHati.mulaiTamu(player, s);
			default -> null;
		};
		if (why != null) {
			// the stage cannot run here (no stair, no elder body, budget full): it counts as skipped, the day goes on
			Msg.chat(player, Masuk.t("ujian.tahap_lewat", namaTahap(s.tahap, s.corak), why).withStyle(ChatFormatting.GRAY));
			if (s.tahap <= TAHAP_HATI) {
				s.dilewati[s.tahap] = true;
			} else if (s.tahap == TAHAP_WAWANCARA || s.tahap == TAHAP_TAMU) {
				s.s3 = 0.5D;
			}
			lanjut(player, s);
		}
	}

	private static @Nullable Component mulaiCorak(ServerPlayer player, Sesi s) {
		Tahap2 t = tahap2;
		if (t == null) {
			Kultivator.LOGGER.warn("Gabung: no stage-2 corak test registered; stage 2 counts as skipped");
			return Masuk.t("ujian.corak_tidak_ada");
		}
		if (s.corak == Corak.TUBUH) {
			// Pikul Gunung carries the stone from the foot of the stair (the stone is handed over where the candidate stands)
			BlockPos[] stair = TanggaUjian.tangga(s.sekte);
			if (stair != null && player.level().isLoaded(stair[0])) {
				Vec3 foot = Vec3.atBottomCenterOf(stair[0]);
				player.teleportTo(foot.x, foot.y, foot.z);
				s.terakhir = player.position();
			}
		}
		try {
			return t.mulai(player, s);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Gabung: corak test {} failed to start", s.corak.id(), exception);
			return Masuk.t("ujian.corak_tidak_ada");
		}
	}

	/**
	 * Stage 2 ended (41B-C {@code UjianCorak.Selesai}): {@code skor} 0-1. A failure because the candidate left the field
	 * ({@code wilayah}), teleported, fell ({@code tumbang}) or logged out ({@code keluar}) ends the whole test (41.5.4); an
	 * internal error ({@code galat}) counts the stage as skipped; a cancel ({@code batal}) from outside ends the day; any
	 * other end (broken vow, seen, time) keeps the score and the day goes on.
	 */
	public static void tahap2Selesai(ServerPlayer player, double skor, boolean gagal, String sebab) {
		Sesi s = sesi(player);
		if (s == null || s.selesai || s.tahap != TAHAP_CORAK || s.data.getBooleanOr("paksa", false)) {
			return;
		}
		String why = sebab == null ? "" : sebab;
		if (gagal) {
			switch (why) {
				case "wilayah" -> {
					gugur(player, s, "ujian.gugur.keluar", false);
					return;
				}
				case "teleport", "tumbang" -> {
					gugur(player, s, "ujian.gugur." + why, false);
					return;
				}
				case "keluar" -> {
					gugur(player, s, "ujian.gugur.pergi", true);
					return;
				}
				case "batal" -> {
					gugur(player, s, "ujian.gugur.lain", false);
					return;
				}
				case "galat" -> {
					s.dilewati[TAHAP_CORAK] = true;
					selesaiTahap(player, s, SKOR_DILEWATI);
					return;
				}
				default -> {
				}
			}
		}
		s.s2 = clamp(skor);
		if (s.corak.ujianDarah() && !s.uji) {
			UjianHati.ujianDarah(player, s);
		}
		selesaiTahap(player, s, s.s2);
	}

	/** A stage of 41B ended with score {@code skor}: chat line, the elder's nod, then the next stage after 2 s. */
	static void selesaiTahap(ServerPlayer player, Sesi s, double skor) {
		if (s.selesai) {
			return;
		}
		int persen = (int) Math.round(clamp(skor) * 100.0D);
		Msg.chat(player, Masuk.t("ujian.tahap_skor", namaTahap(s.tahap, s.corak), persen).withStyle(persen >= 50 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		KultivatorNpc penjaga = penjaga(player.level(), s.sekte, false);
		if (penjaga != null && persen >= 60) {
			Masuk.ucap(penjaga, "gabung.ujian_lulus", "elder.welcome", Masuk.t("ucap.ujian_lulus"));
		}
		if (s.hanyaTahap != 0) {
			// a test command asked for one stage: report and end without a verdict
			Msg.chat(player, Masuk.t("ujian.uji_selesai", namaTahap(s.tahap, s.corak), persen).withStyle(ChatFormatting.AQUA));
			tutup(player, s, false);
			return;
		}
		s.data.putLong("jeda_sampai", player.level().getGameTime() + 40L);
		s.data.putBoolean("jeda", true);
	}

	/**
	 * Ends the test early as failed (death, leaving the ground, logging out, a command): refused, the pause of
	 * {@code jedaLamarHari} days (none for a test command), the candidacy ends.
	 */
	public static void gugur(ServerPlayer player, Sesi s, String alasanKunci, boolean diam) {
		if (s.selesai) {
			return;
		}
		if (!diam) {
			Msg.chat(player, Masuk.t("ujian.gugur", Masuk.t(alasanKunci)).withStyle(ChatFormatting.RED));
			KultivatorNpc penjaga = penjaga(player.level(), s.sekte, false);
			if (penjaga != null) {
				Masuk.ucap(penjaga, "gabung.ujian_gagal", "elder.welcome", Masuk.t("ucap.ujian_gagal"));
			}
		}
		if (!s.uji) {
			Lamaran.tolak(player, s.sekte);
		}
		tutup(player, s, true);
	}

	/** Test command {@code uji gabung ujian lulus|gagal}: ends the running stage with full or no marks. */
	public static boolean paksa(ServerPlayer player, boolean lulus) {
		Sesi s = sesi(player);
		if (s == null || s.selesai) {
			return false;
		}
		double skor = lulus ? 1.0D : 0.0D;
		switch (s.tahap) {
			case TAHAP_TANGGA -> {
				TanggaUjian.bersihkan(player, s);
				s.s1 = skor;
				selesaiTahap(player, s, skor);
			}
			case TAHAP_CORAK -> {
				Tahap2 t = tahap2;
				if (t != null) {
					s.data.putBoolean("paksa", true);
					t.batal(player);
					s.data.remove("paksa");
				}
				s.s2 = skor;
				selesaiTahap(player, s, skor);
			}
			case TAHAP_HATI, TAHAP_WAWANCARA, TAHAP_TAMU -> {
				UjianHati.bersihkan(player, s);
				s.s3 = skor;
				s.kesan = lulus ? 40 : -20;
				selesaiTahap(player, s, skor);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private static void akhir(ServerPlayer player, Sesi s) {
		Putusan.Hasil hasil = Putusan.hitung(player, s);
		tutup(player, s, false);
		Putusan.umumkan(player, s, hasil);
	}

	/** Cleans the day up: bodies home, HUD off, busy state off, the sect's 60 s wait starts. */
	private static void tutup(ServerPlayer player, Sesi s, boolean batalCorak) {
		s.selesai = true;
		SESI.remove(s.pemain);
		SELESAI_SEKTE.put(s.sekte, player.level().getGameTime());
		if (batalCorak && s.tahap == TAHAP_CORAK && tahap2 != null) {
			try {
				tahap2.batal(player);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Gabung: corak test cancel failed", exception);
			}
		}
		TanggaUjian.bersihkan(player, s);
		UjianHati.bersihkan(player, s);
		pulangkan(player.level().getServer(), s);
		GabungPayloads.kirimUjian(player, 0, 0.0F, 0, 0.0F, null);
		GabungApi.lepasSibuk(player, Gabung.SIBUK_UJIAN);
	}

	private static void pulangkan(@Nullable MinecraftServer server, Sesi s) {
		if (server == null) {
			return;
		}
		ServerLevel level = server.getLevel(s.dimensi);
		if (level == null) {
			return;
		}
		for (UUID id : s.tubuh) {
			Entity e = level.getEntity(id);
			if (e instanceof KultivatorNpc npc && npc.memory().getBooleanOr(Masuk.MEM_CALON, false)) {
				npc.discard();
			}
		}
		s.tubuh.clear();
	}

	// ================================================================== ticking

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(HariUjian::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				Sesi s = sesi(player);
				if (s != null && !s.selesai && !(s.tahap == TAHAP_CORAK && tahap2 != null)) {
					gugur(player, s, "ujian.gugur.mati", false);
				}
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			Sesi s = sesi(player);
			if (s != null && !s.selesai) {
				gugur(player, s, "ujian.gugur.pergi", true);
			}
			DIPANGGIL.remove(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SESI.clear();
			SELESAI_SEKTE.clear();
			DIPANGGIL.clear();
		});
		dev.kultivator.disciple.Disciples.addFollowVeto((player, origin, destination) ->
				sedangUjian(player) ? "kultivator.gabung.masuk.ujian.murid_dilarang" : null);
	}

	private static void tick(MinecraftServer server) {
		if (!Gabung.aktif()) {
			if (!SESI.isEmpty()) {
				for (Sesi s : List.copyOf(SESI.values())) {
					ServerPlayer p = server.getPlayerList().getPlayer(s.pemain);
					if (p != null) {
						gugur(p, s, "ujian.gugur.lain", true);
					} else {
						SESI.remove(s.pemain);
					}
				}
			}
			return;
		}
		long tickCount = server.getTickCount();
		if (!SESI.isEmpty()) {
			Iterator<Sesi> it = List.copyOf(SESI.values()).iterator();
			while (it.hasNext()) {
				Sesi s = it.next();
				ServerPlayer p = server.getPlayerList().getPlayer(s.pemain);
				if (p == null) {
					SESI.remove(s.pemain);
					continue;
				}
				try {
					tickSesi(p, s, tickCount);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Gabung: test tick failed for {}", p.getName().getString(), exception);
					gugur(p, s, "ujian.gugur.lain", true);
				}
			}
		}
		if (tickCount % 40L == 7L) {
			panggil(server);
		}
	}

	private static void tickSesi(ServerPlayer p, Sesi s, long tickCount) {
		if (s.selesai) {
			return;
		}
		long now = p.level().getGameTime();
		if (tickCount % 20L == 0L) {
			GabungApi.sibuk(p, Gabung.SIBUK_UJIAN, SIBUK_TICK);
		}
		if (!p.level().dimension().equals(s.dimensi) || Masuk.datar(p.position(), s.pusat) > JARI_WILAYAH) {
			gugur(p, s, "ujian.gugur.keluar", false);
			return;
		}
		tolakTerbang(p);
		if (s.tahap != TAHAP_CORAK) {
			Vec3 last = s.terakhir;
			if (last != null && p.position().distanceTo(last) > LOMPAT_MAKS) {
				p.teleportTo(last.x, last.y, last.z);
				Msg.actionBar(p, Masuk.t("ujian.teleport_ditolak").withStyle(ChatFormatting.RED));
			} else {
				s.terakhir = p.position();
			}
		} else {
			s.terakhir = p.position();
		}
		if (s.data.getBooleanOr("jeda", false)) {
			if (now >= s.data.getLongOr("jeda_sampai", now)) {
				lanjut(p, s);
			}
			return;
		}
		switch (s.tahap) {
			case TAHAP_TANGGA -> TanggaUjian.tick(p, s, now);
			case TAHAP_HATI, TAHAP_WAWANCARA, TAHAP_TAMU -> UjianHati.tick(p, s, now);
			default -> {
			}
		}
		if (!s.selesai && tickCount % 5L == 0L && s.tahap != TAHAP_CORAK) {
			hud(p, s, now);
		}
	}

	/**
	 * The HUD payload of the stages 41B runs (41.12.2, at most 64 bytes): stage 1 {@code tekanan} 0-100, ticks left, live
	 * S1, data {@code [0xFF, illusions 0-3, slowness 0-2]}; Ujian Hati / servant talk / guest trial: stage 3, data
	 * {@code [0xFE, question 1-3, kesan + 64]}.
	 */
	static void hud(ServerPlayer p, Sesi s, long now) {
		switch (s.tahap) {
			case TAHAP_TANGGA -> TanggaUjian.hud(p, s, now);
			case TAHAP_HATI, TAHAP_WAWANCARA, TAHAP_TAMU -> {
				byte[] data = {(byte) 0xFE, (byte) UjianHati.soalKe(s), (byte) Math.max(0, Math.min(255, s.kesan + 64))};
				int sisa = UjianHati.sisaTick(s, now);
				GabungPayloads.kirimUjian(p, TAHAP_HATI, 0.0F, sisa, (float) Math.max(0.0D, s.s3), data);
			}
			default -> {
			}
		}
	}

	private static void tolakTerbang(ServerPlayer p) {
		if (p.isCreative() || p.isSpectator()) {
			return;
		}
		boolean flying = p.getAbilities().flying || p.isFallFlying();
		if (!flying) {
			return;
		}
		FlightLease lease = FlightLease.holder(p);
		if (lease != null) {
			if (FlyingSword.LEASE_SOURCE.equals(lease.source())) {
				FlyingSword.dismount(p, Cultivators.get(p), FlyingSword.Reason.LOST_FLIGHT);
			} else {
				lease.release();
			}
		}
		if (p.getAbilities().flying) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		if (p.isFallFlying()) {
			p.stopFallFlying();
		}
		Msg.actionBar(p, Masuk.t("ujian.terbang_ditolak").withStyle(ChatFormatting.RED));
	}

	// ================================================================== the guard's call (41.5.3)

	private static void panggil(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long hari = Kota.day(server);
		GabungData data = GabungData.get(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level() != level || SESI.containsKey(p.getUUID())) {
				continue;
			}
			Keanggotaan k = data.peek(p.getUUID());
			if (k == null || k.status != StatusAnggota.CALON || k.sekte.isEmpty()) {
				continue;
			}
			if (k.calonSampai >= 0L && hari > k.calonSampai) {
				Lamaran.kedaluwarsa(p, k);
				continue;
			}
			if (k.pintu == Pintu.PELAYAN || k.pintu == Pintu.TAMU || !bukaSekarang(server, k.sekte)) {
				continue;
			}
			BlockPos gerbang = gerbang(k.sekte);
			if (gerbang == null || Masuk.datar(p.position(), gerbang) > JARI_PANGGIL) {
				continue;
			}
			KultivatorNpc penjaga = penjaga(level, k.sekte, true);
			Long called = DIPANGGIL.get(p.getUUID());
			if (penjaga != null && (called == null || called != hari)) {
				DIPANGGIL.put(p.getUUID(), hari);
				HidupApi.tatap(penjaga, p);
				HidupApi.klip(penjaga, GabungGerakId.PENJAGA_MEMANGGIL, 0, 60);
				Masuk.ucap(penjaga, "gabung.gerbang_panggil", "kotabesar.recruit_call", Masuk.t("ucap.gerbang_panggil"));
				Msg.chat(p, Masuk.t("ujian.dipanggil", Masuk.nama(k.sekte)).withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** The sect's entrance: {@code kompleks/gerbang}, else the mountain gate, else null. */
	public static @Nullable BlockPos gerbang(String sekte) {
		return GabungApi.titik(sekte, TitikGabung.GERBANG_KOMPLEKS);
	}

	private static BlockPos pusat(String sekte, BlockPos gerbang) {
		BlockPos aula = GabungApi.titik(sekte, TitikGabung.AULA);
		if (aula == null || aula.distSqr(gerbang) > JARI_WILAYAH * JARI_WILAYAH) {
			return gerbang;
		}
		return new BlockPos((aula.getX() + gerbang.getX()) / 2, (aula.getY() + gerbang.getY()) / 2, (aula.getZ() + gerbang.getZ()) / 2);
	}

	/**
	 * The gate guard of {@code sekte}: a live {@code PENJAGA_GERBANG} within 12 blocks of the gate, or (with
	 * {@code buat}) a staff body placed there. Null when the gate is not loaded.
	 */
	public static @Nullable KultivatorNpc penjaga(ServerLevel level, String sekte, boolean buat) {
		BlockPos gerbang = gerbang(sekte);
		if (gerbang == null || !level.isLoaded(gerbang)) {
			return null;
		}
		List<KultivatorNpc> near = level.getEntitiesOfClass(KultivatorNpc.class, new AABB(gerbang).inflate(12.0D),
				n -> n.isAlive() && n.role() == NpcRole.PENJAGA_GERBANG);
		if (!near.isEmpty()) {
			return near.getFirst();
		}
		if (!buat) {
			return null;
		}
		KultivatorNpc npc = HidupApi.tubuhStaf(level, Masuk.situs(level, gerbang), NpcRole.PENJAGA_GERBANG, gerbang, 0.0F);
		if (npc != null) {
			npc.memory().putString(Masuk.MEM_SEKTE, sekte);
			Masuk.rupa(npc, "murid.dalam", sekte);
		}
		return npc;
	}

	static double clamp(double v) {
		return v < 0.0D ? 0.0D : v > 1.0D ? 1.0D : v;
	}

	private HariUjian() {
	}
}
