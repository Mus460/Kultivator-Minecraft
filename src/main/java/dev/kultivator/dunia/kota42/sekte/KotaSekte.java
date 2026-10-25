package dev.kultivator.dunia.kota42.sekte;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaHidupCommands;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.warga.Suasana;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Runtime init of 42E Sekte &amp; Kota (DESIGN 42.8), called once from {@code KotaHidup.init} on both sides (the registries
 * are 42R0's: {@code KotaSekteEntities}, {@code KotaEkonomiBlocks}). It wires the four parts and their shared clock:
 * <ul>
 * <li>{@link KantorRekrut} recruitment offices (42.8.1): rented booths, applicants drawn from rooted residents, the
 * {@code batu_uji} stone, the open test day and the farewell scene;</li>
 * <li>{@link PaktaLindung} protection pacts (42.8.2) with tribute and the defence calls of {@link SerbuanKota};</li>
 * <li>{@link TugasKotaMurid} disciples on town duty (42.8.3) through the 39 away store and a town body;</li>
 * <li>{@link CabangKota} sect branches (42.8.4) on a {@code papan_cabang} in an owned building.</li>
 * </ul>
 * Every part checks {@code kotaSekte} ({@link KotaConfig#sekte()}) at run time, so a reload switches it off and on; the
 * blocks, items and entity types stay registered. Overworld only. While 41 runs, a member of an NPC sect without a hall of
 * his own is refused a pact and an office ({@link #anggotaTanpaAula}).
 * <p>
 * Also here: the shared {@link Hasil} of every action, the sect name, the place helpers and the disciple bridge for 42A
 * ({@link #tandai}: a resident who became a candidate is recorded with {@code WargaData.catatMurid} once the hall accepts
 * it and the disciple body first loads).
 */
public final class KotaSekte {

	/** {@code DiscipleData.extraSection} of 42E (resident id of a disciple who came from a town). */
	public static final String EXTRA = "kota42";
	private static final String EXTRA_WARGA = "warga";
	private static final String EXTRA_DICATAT = "dicatat";

	private static boolean initialised;
	private static long hariTerakhir = Long.MIN_VALUE;

	/** The outcome of an action: ok and the message for the player (the action already sent it). */
	public record Hasil(boolean ok, Component pesan) {

		public static Hasil gagal(String key, Object... args) {
			return new Hasil(false, Component.translatable("kultivator.kota42.sekte.gagal." + key, args));
		}

		public static Hasil berhasil(String key, Object... args) {
			return new Hasil(true, Component.translatable("kultivator.kota42.sekte." + key, args));
		}

		/** Sends the message to {@code player} (gold when ok, yellow when refused) and returns this. */
		public Hasil kirim(@Nullable ServerPlayer player) {
			if (player != null) {
				Msg.chat(player, pesan.copy().withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
			}
			return this;
		}
	}

	/** From {@code KotaHidup.init}. Safe to call twice. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(KotaSekte::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> lupakan());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof DiscipleEntity disciple && disciple.role() == DiscipleEntity.Role.DISCIPLE && disciple.ownerId() != null) {
				catatMurid(level.getServer(), disciple);
			}
		});
		KotaBus.RUMAH_BERUBAH.register(CabangKota::rumahBerubah);
		Suasana.addPengaruhTempat(PaktaLindung::pengaruhSuasana);
		Halls.addReturnHandler(TugasKotaMurid.ALASAN, TugasKotaMurid::kembali);
		Halls.addReturnHandler(SerbuanKota.ALASAN_BELA, SerbuanKota::kembali);
		SerbuanKota.init();
		KotaSekteUji.init();
	}

	private static void lupakan() {
		hariTerakhir = Long.MIN_VALUE;
		KantorRekrut.lupakan();
		TugasKotaMurid.lupakan();
		SerbuanKota.lupakan();
		CabangKota.lupakan();
	}

	/** True while 42E runs ({@code kotaSekte.aktif} and a server). */
	public static boolean aktif() {
		return KotaHidup.server() != null && KotaConfig.sekte().aktif;
	}

	// ================================================================== clock

	private static void tick(MinecraftServer server) {
		if (!KotaConfig.sekte().aktif) {
			return;
		}
		int t = server.getTickCount();
		if (t % 20 == 3) {
			long hari = KotaHidup.hari(server);
			if (hari != hariTerakhir) {
				boolean pertama = hariTerakhir == Long.MIN_VALUE;
				hariTerakhir = hari;
				// every daily step is stamped with its own day, so a restart on the same day never runs it twice
				KantorRekrut.harian(server, hari, pertama);
				PaktaLindung.harian(server, hari);
				CabangKota.harian(server, hari);
				TugasKotaMurid.harian(server, hari);
			}
			KantorRekrut.tick(server);
			SerbuanKota.tick(server);
			TugasKotaMurid.tick(server);
		}
	}

	// ================================================================== shared readers

	/** The world day. */
	public static long hari(MinecraftServer server) {
		return KotaHidup.hari(server);
	}

	/** The shown name of the sect of hall owner {@code owner}: the name its master chose, else "Sect of <owner>". */
	public static Component namaSekte(@Nullable UUID owner) {
		if (owner == null) {
			return Component.literal("?");
		}
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall != null && hall.name() != null && !hall.name().isBlank()) {
			return Component.literal(hall.name());
		}
		return Halls.hallName(Halls.section(owner, "core"));
	}

	/** The place's name ("Kota Teratai Hijau"). */
	public static Component namaTempat(MinecraftServer server, long situs) {
		try {
			return PajakKota.nama(server, situs);
		} catch (RuntimeException exception) {
			return KotaNames.town(situs);
		}
	}

	/** {@code PajakKota.JT_*} of a place. */
	public static String jenisTempat(MinecraftServer server, long situs) {
		return PajakKota.jenisTempat(server, situs);
	}

	/** Town class 1 kampung, 2 kota, 3 kota tema, 4 capital. */
	public static int kelas(MinecraftServer server, long situs) {
		return PajakKota.kelas(server, situs);
	}

	/** The place the player stands in (or nearest within 160 blocks, overworld), or null. */
	public static @Nullable TempatInfo tempat(ServerPlayer player) {
		return KotaHidupCommands.tempatPemain(player);
	}

	/** Town reputation (Nama Baik) of {@code player} at {@code situs}. */
	public static int namaBaik(MinecraftServer server, long situs, UUID player) {
		return dev.kultivator.dunia.kota.KotaData.get(server).reputation(situs, player);
	}

	/** Adds town reputation; returns the new value. */
	public static int tambahNamaBaik(MinecraftServer server, long situs, UUID player, int delta) {
		return dev.kultivator.dunia.kota.KotaData.get(server).addReputation(situs, player, delta);
	}

	/**
	 * True when 41 runs and {@code player} belongs to an NPC sect but has no hall of his own: the town will not deal with
	 * a sect through a junior disciple (42.15.3).
	 */
	public static boolean anggotaTanpaAula(UUID player) {
		try {
			return GabungApi.aktif() && GabungApi.sekte(player) != null && Halls.hallOf(player) == null;
		} catch (RuntimeException | LinkageError exception) {
			return false;
		}
	}

	/** The NPC sect {@code player} belongs to while 41 runs (for the refusal line), else null. */
	public static @Nullable String sekteNpc(UUID player) {
		try {
			return GabungApi.aktif() ? GabungApi.sekte(player) : null;
		} catch (RuntimeException | LinkageError exception) {
			return null;
		}
	}

	/** The common refusal before any 42E action: module off, no hall, a junior of an NPC sect. Null = may go on. */
	static @Nullable Hasil syaratUmum(ServerPlayer player, boolean bagianAktif) {
		if (!aktif() || !bagianAktif) {
			return Hasil.gagal("mati");
		}
		if (!KotaHidup.duniaKota(player.level())) {
			return Hasil.gagal("dunia");
		}
		if (anggotaTanpaAula(player.getUUID())) {
			String sekte = sekteNpc(player.getUUID());
			Component nama = sekte == null ? Component.literal("?") : dev.kultivator.sekte.api.SekteApi.diplomasi().name(sekte);
			return Hasil.gagal("anggota_npc", nama == null ? Component.literal(sekte) : nama);
		}
		if (Halls.hallOf(player.getUUID()) == null) {
			return Hasil.gagal("tanpa_aula");
		}
		return null;
	}

	// ================================================================== the disciple bridge (42A)

	/**
	 * Marks a hall candidate made from resident {@code w} so that the disciple it becomes is recorded as that resident's
	 * ({@code WargaData.catatMurid}: family visits of 39, {@code WargaApi.keluargaMurid}). Returns {@code spec}.
	 */
	public static @Nullable Halls.CandidateSpec tandai(@Nullable Halls.CandidateSpec spec, Warga w) {
		if (spec != null && spec.data() != null && w != null) {
			CompoundTag tag = spec.data().extraSection(EXTRA);
			tag.putInt(EXTRA_WARGA, w.id);
			tag.remove(EXTRA_DICATAT);
		}
		return spec;
	}

	/** An owned disciple loaded: record it once when it came from a town. */
	private static void catatMurid(MinecraftServer server, DiscipleEntity disciple) {
		DiscipleData data = disciple.data();
		CompoundTag extra = data == null ? null : data.extra().getCompoundOrEmpty(EXTRA);
		if (extra == null || !extra.contains(EXTRA_WARGA) || extra.getBooleanOr(EXTRA_DICATAT, false)) {
			return;
		}
		int warga = extra.getIntOr(EXTRA_WARGA, 0);
		if (warga == 0) {
			return;
		}
		WargaData.get(server).catatMurid(disciple.soulId(), warga);
		data.extraSection(EXTRA).putBoolean(EXTRA_DICATAT, true);
	}

	private KotaSekte() {
	}
}
