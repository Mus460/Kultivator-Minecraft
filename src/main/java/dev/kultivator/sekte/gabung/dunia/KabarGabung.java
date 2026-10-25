package dev.kultivator.sekte.gabung.dunia;

import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.gabung.api.Pangkat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * World news of Gabung Sekte (DESIGN 41.6.13, owner 41W) through the 38.S rumour engine: templates
 * {@code data/kultivator/tempat/kabar/gabung_sekte.json} (text {@code kultivator.gabung.kabar.<id>} in fragment
 * {@code sekte_gabung_cerita}). The sect is stored as {@code sekte:<id>} so every listener reads its name in their own
 * language; a person is stored as their plain name. Spread world-wide by the bus events of 41: a player sworn in, a
 * promotion to Inti and above, an expulsion or desertion, a sect changing class, a war and its end, a new master, a fallen
 * sect rising again; 41B (Hari Ujian) and 41C (tournament) call {@link #hariUjian} and {@link #turnamen}. Five background
 * lines ({@code pemicu "umum"}) keep the town talking about the sects' gates when nothing happened.
 */
public final class KabarGabung {

	public static final String DITERIMA = "gabung_diterima";
	public static final String NAIK = "gabung_naik_";
	public static final String DIUSIR = "gabung_diusir";
	public static final String PEMBELOT = "gabung_pembelot";
	public static final String PAMIT = "gabung_pamit";
	public static final String KELAS_NAIK = "gabung_kelas_naik_";
	public static final String KELAS_TURUN = "gabung_kelas_turun_";
	public static final String PERANG = "gabung_perang";
	public static final String PERANG_MENANG = "gabung_perang_menang";
	public static final String KETUA_BARU = "gabung_ketua_baru";
	public static final String BANGKIT = "gabung_bangkit";
	public static final String TURNAMEN = "gabung_turnamen";
	public static final String HARI_UJIAN = "gabung_hari_ujian";

	private static boolean initialised;

	/** From {@code DuniaGabung.init} (module on). */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		GabungBus.DITERIMA.register((player, sekte, pangkat) -> sebar(player, DITERIMA, player.getName().getString(), "sekte:" + sekte));
		GabungBus.PANGKAT_NAIK.register((player, sekte, lama, baru) -> {
			if (baru.tingkat() > lama.tingkat() && baru.minimal(Pangkat.INTI)) {
				sebar(player, NAIK + baru.id(), player.getName().getString(), "sekte:" + sekte);
			}
		});
		GabungBus.KELUAR.register((player, sekte, status) -> {
			String templat = switch (status) {
				case DIUSIR -> DIUSIR;
				case PEMBELOT -> PEMBELOT;
				case PAMIT -> PAMIT;
				default -> null;
			};
			if (templat != null) {
				sebar(player, templat, player.getName().getString(), "sekte:" + sekte);
			}
		});
		GabungBus.KELAS_BERUBAH.register((server, sekte, lama, baru) -> {
			if (baru == lama) {
				return;
			}
			String id = KelasSekte.byNomor(baru).id();
			sebar(server, (baru > lama ? KELAS_NAIK : KELAS_TURUN) + id, "sekte:" + sekte);
		});
		GabungBus.PERANG_MULAI.register((server, sekte, lawan) -> sebar(server, PERANG, "sekte:" + sekte, "sekte:" + lawan));
		GabungBus.PERANG_AKHIR.register((server, sekte, lawan, menang) -> {
			if (menang) {
				sebar(server, PERANG_MENANG, "sekte:" + sekte, "sekte:" + lawan);
			}
		});
		GabungBus.KETUA_BARU.register(KabarGabung::ketuaBaru);
	}

	private static void ketuaBaru(MinecraftServer server, String sekte, @Nullable UUID pemain, String tokoh) {
		String nama = null;
		if (pemain != null) {
			ServerPlayer p = server.getPlayerList().getPlayer(pemain);
			nama = p != null ? p.getName().getString() : null;
		}
		if (nama == null && tokoh != null && TokohSekte.idSah(tokoh)) {
			TokohSekte.Tokoh t = TokohSekte.tokoh(server, tokoh);
			nama = t == null ? null : t.nama();
		}
		if (nama != null) {
			sebar(server, KETUA_BARU, "sekte:" + sekte, nama);
		}
	}

	/** 41B: a sect's Test Day is near (the directory learns of it through talk). */
	public static void hariUjian(MinecraftServer server, String sekte) {
		sebar(server, HARI_UJIAN, "sekte:" + sekte);
	}

	/** 41C: {@code juara} (a player's or a roster member's plain name) won the Disciple Tournament of {@code sekte}. */
	public static void turnamen(MinecraftServer server, String sekte, String juara) {
		sebar(server, TURNAMEN, juara, "sekte:" + sekte);
	}

	private static void sebar(ServerPlayer player, String templat, String... isian) {
		if (player.level() instanceof ServerLevel level) {
			sebar(level.getServer(), templat, isian);
		}
	}

	private static void sebar(MinecraftServer server, String templat, String... isian) {
		if (server != null && GabungApi.aktif()) {
			CeritaApi.kabar(server.overworld(), null, templat, (Object[]) isian);
		}
	}

	private KabarGabung() {
	}
}
