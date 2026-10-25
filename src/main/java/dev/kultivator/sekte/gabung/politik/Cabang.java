package dev.kultivator.sekte.gabung.politik;

import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Branch halls of DESIGN 41.8.2 ("Sekte Cabang"; the mother temple and its <i>xiayuan</i> branch temples). A Core member
 * (or higher) whose hall stands may make it a branch of his sect after the two-chapter chain "Restu Gunung":
 * <ol>
 *   <li>an offering of {@code 200 x class} PK to the treasury ({@link #persembahan});</li>
 *   <li>leading one board mission of rank B or higher ({@link #misiSelesai}, called by 41C's board, or a PK gain whose
 *   reason is {@code gabung.misi.b|a|s}).</li>
 * </ol>
 * Then {@link #ajukan}: the hall is listed in {@code DataSekteGabung.cabang}, allied with the mother sect
 * ({@code DiplomasiGabung.paktaCabang}: SEKUTU, standing at least 60) and shows "Cabang &lt;sect&gt;" in its status lines.
 * <p>
 * Rights: one candidate from the mother every {@link #CALON_HARI} days ({@code Halls.addCandidates}, source
 * {@code gabung_cabang}); the hall's disciples may read at the mother's Merit Pavilion (41C reads {@link #induk}). Duty: a
 * daily tribute of 10 % of the hall's average daily PK income (at most {@code 50 x class}) through {@code Halls.spendPool}
 * into the treasury ({@code KasSekte.terimaUpeti}, ledger line {@code upeti}); answering the mother's War Call (41D-P).
 * Leaving: the owner leaving the sect ends the branch (standing -10); declaring independence costs -40 (never desertion
 * without a running War Call). A branch made by "Lebur" (41.8.6) pays no tribute and is permanent while its owner is master.
 * Server thread only; state in the owner's {@code Keanggotaan.bagian("politik")} ({@code restu}) and the sect's
 * {@code politik} compound ({@code cabangInfo.<owner>}).
 */
public final class Cabang {

	/** PK of the offering per class number. */
	public static final int RESTU_PK = 200;
	/** Tribute: percent of the average daily income, and the cap per class number. */
	public static final int UPETI_PERSEN = 10;
	public static final int UPETI_MAKS = 50;
	/** Days between two candidates from the mother sect. */
	public static final int CALON_HARI = 10;
	/** Standing change of a branch that leaves with its owner / declares independence. */
	public static final int LEPAS_PAMIT = -10;
	public static final int LEPAS_MERDEKA = -40;

	public static final int RESTU_BELUM = 0;
	public static final int RESTU_PERSEMBAHAN = 1;
	public static final int RESTU_SELESAI = 2;

	private static final String KEY = "kultivator.gabung.politik.cabang.";
	private static boolean initialised;

	// ================================================================== Restu Gunung

	/** Why {@code player} may not walk "Restu Gunung" / found a branch now, or null. */
	public static @Nullable Component syarat(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !Gabung.aktif()) {
			return Component.translatable(KEY + "bukan_anggota");
		}
		if (!GabungConfig.get().bolehCabang) {
			return Component.translatable(KEY + "mati");
		}
		if (!k.pangkat.minimal(Pangkat.INTI) || k.pangkat == Pangkat.TAMU) {
			return Component.translatable(KEY + "pangkat", Pangkat.INTI.nama());
		}
		if (induk(player.level().getServer(), player.getUUID()) != null) {
			return Component.translatable(KEY + "sudah");
		}
		return null;
	}

	/** The player's progress in "Restu Gunung" ({@code RESTU_*}). */
	public static int restu(ServerPlayer player) {
		return GabungApi.anggota(player).bagian("politik").getIntOr("restu", RESTU_BELUM);
	}

	/** Chapter 1: the offering of {@code 200 x class} PK to the treasury; null when made, else why not. */
	public static @Nullable Component persembahan(ServerPlayer player) {
		Component why = syarat(player);
		if (why != null) {
			return why;
		}
		if (restu(player) >= RESTU_PERSEMBAHAN) {
			return Component.translatable(KEY + "persembahan_sudah");
		}
		String sekte = GabungApi.sekte(player.getUUID());
		int pk = RESTU_PK * GabungApi.kelas(sekte);
		if (!GabungApi.bayarPk(player, pk, "gabung.restu_gunung")) {
			return Component.translatable(KEY + "pk", pk);
		}
		MinecraftServer server = player.level().getServer();
		KasSekte.catat(server, sekte, player.getUUID(), pk, DiplomasiGabung.SEBAB_UPETI);
		GabungApi.anggota(player).bagian("politik").putInt("restu", RESTU_PERSEMBAHAN);
		GabungData.get(server).setDirty();
		player.sendSystemMessage(Component.translatable(KEY + "bab1_selesai", pk).withStyle(ChatFormatting.GOLD));
		return null;
	}

	/** Chapter 2: the player led a board mission of rank {@code pangkatMisi} ({@code E..S}); B or higher completes it. */
	public static void misiSelesai(ServerPlayer player, String pangkatMisi) {
		if (restu(player) != RESTU_PERSEMBAHAN || pangkatMisi == null) {
			return;
		}
		String p = pangkatMisi.toUpperCase(java.util.Locale.ROOT);
		if (!(p.equals("B") || p.equals("A") || p.equals("S"))) {
			return;
		}
		GabungApi.anggota(player).bagian("politik").putInt("restu", RESTU_SELESAI);
		GabungData.get(player.level().getServer()).setDirty();
		player.sendSystemMessage(Component.translatable(KEY + "bab2_selesai").withStyle(ChatFormatting.GOLD));
	}

	/** Founds the branch with the player's hall (after Restu Gunung); null when done, else why not. */
	public static @Nullable Component ajukan(ServerPlayer player) {
		Component why = syarat(player);
		if (why != null) {
			return why;
		}
		if (restu(player) < RESTU_SELESAI) {
			return Component.translatable(KEY + "restu", restu(player) + 1);
		}
		if (Halls.hallOf(player.getUUID()) == null) {
			return Component.translatable(KEY + "tanpa_aula");
		}
		jadikan(player.level().getServer(), player.getUUID(), GabungApi.sekte(player.getUUID()), false);
		return null;
	}

	/**
	 * Makes the hall of {@code owner} a branch of {@code sekte} ({@code lebur} = the permanent branch of 41.8.6, no tribute):
	 * the list, the pact, the hall log, the advancement {@code sekte/gabung_cabang}, the members' news.
	 */
	public static void jadikan(MinecraftServer server, UUID owner, String sekte, boolean lebur) {
		if (server == null || owner == null || sekte == null) {
			return;
		}
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.sekte(sekte);
		if (!d.cabang.contains(owner)) {
			d.cabang.add(owner);
		}
		CompoundTag info = info(server, sekte, owner);
		info.putLong("hari", GabungApi.hari(server));
		info.putLong("calonHari", GabungApi.hari(server));
		info.putBoolean("lebur", lebur);
		data.setDirty();
		DiplomasiGabung.paktaCabang(owner, sekte);
		Component nama = DiplomasiGabung.nama(server, sekte);
		Halls.log(owner, Component.translatable(KEY + "log", nama).withStyle(ChatFormatting.GOLD));
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Advancements.grant(player, "sekte/gabung_cabang");
			player.sendSystemMessage(Component.translatable(KEY + "jadi", nama).withStyle(ChatFormatting.GOLD));
		}
		Politik.kabarAnggota(server, sekte, Component.translatable(KEY + "kabar", Kursi.namaPemegang(server, owner.toString()), nama),
				ChatFormatting.YELLOW);
	}

	/** The sect whose branch the hall of {@code owner} is, or null. */
	public static @Nullable String induk(MinecraftServer server, UUID owner) {
		if (server == null || owner == null) {
			return null;
		}
		for (String s : DiplomasiGabung.sekte(server)) {
			GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(s);
			if (d != null && d.cabang.contains(owner)) {
				return s;
			}
		}
		return null;
	}

	/** The branch halls of {@code sekte} (owner UUIDs). */
	public static List<UUID> daftar(MinecraftServer server, String sekte) {
		GabungData.DataSekteGabung d = server == null ? null : GabungData.get(server).peekSekte(sekte);
		return d == null ? List.of() : List.copyOf(d.cabang);
	}

	/** Yesterday's tribute of the branch of {@code owner} to {@code sekte} (PK; 0 for a Lebur branch). */
	public static int upetiTerakhir(MinecraftServer server, String sekte, UUID owner) {
		return info(server, sekte, owner).getIntOr("upeti", 0);
	}

	/** The status line of a branch hall: "Cabang &lt;sect&gt;" (null for an independent hall). */
	public static @Nullable Component statusAula(UUID owner) {
		MinecraftServer server = Gabung.server();
		String sekte = server == null || !Gabung.aktif() ? null : induk(server, owner);
		return sekte == null ? null : Component.translatable(KEY + "status", DiplomasiGabung.nama(server, sekte)).withStyle(ChatFormatting.GOLD);
	}

	/**
	 * Ends the branch of {@code owner}: leaving with its owner ({@link #LEPAS_PAMIT}) or declaring independence
	 * ({@link #LEPAS_MERDEKA}); the pact goes back to none.
	 */
	public static void lepas(MinecraftServer server, UUID owner, String sekte, int delta) {
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung d = data.peekSekte(sekte);
		if (d == null || !d.cabang.remove(owner)) {
			return;
		}
		DiplomasiGabung.bagian(server, sekte).getCompoundOrEmpty("cabangInfo").remove(owner.toString());
		data.setDirty();
		DiplomasiGabung.lepasPakta(server, owner, sekte, delta);
		Component nama = DiplomasiGabung.nama(server, sekte);
		Halls.log(owner, Component.translatable(KEY + "log_lepas", nama).withStyle(ChatFormatting.YELLOW));
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			player.sendSystemMessage(Component.translatable(KEY + "lepas", nama, delta).withStyle(ChatFormatting.YELLOW));
		}
	}

	/** {@code /kultivasi gabung cabang merdeka}: the branch declares independence; null when done. */
	public static @Nullable Component merdeka(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		String sekte = induk(server, player.getUUID());
		if (sekte == null) {
			return Component.translatable(KEY + "bukan_cabang");
		}
		if (info(server, sekte, player.getUUID()).getBooleanOr("lebur", false)) {
			return Component.translatable(KEY + "lebur_tetap");
		}
		lepas(server, player.getUUID(), sekte, LEPAS_MERDEKA);
		return null;
	}

	// ================================================================== daily (from Politik)

	/** Tribute and candidates of every branch of {@code sekte}. */
	static void harian(MinecraftServer server, String sekte, long hari) {
		GabungData.DataSekteGabung d = GabungData.get(server).peekSekte(sekte);
		if (d == null || d.cabang.isEmpty()) {
			return;
		}
		int kelas = GabungApi.kelas(sekte);
		for (UUID owner : new ArrayList<>(d.cabang)) {
			Halls.HallView hall = Halls.hallOf(owner);
			if (hall == null) {
				lepas(server, owner, sekte, 0);
				continue;
			}
			CompoundTag info = info(server, sekte, owner);
			if (info.getLongOr("upetiHari", -1L) < hari && !info.getBooleanOr("lebur", false)) {
				info.putLong("upetiHari", hari);
				int rata = Halls.poolWeek(owner)[0] / 7;
				int upeti = Math.min(Math.min(rata * UPETI_PERSEN / 100, UPETI_MAKS * kelas), Halls.pool(owner));
				if (upeti > 0 && Halls.spendPool(owner, upeti)) {
					KasSekte.terimaUpeti(server, sekte, owner, upeti);
					EkonomiApi.kas(owner, LembarKunci.BukuKas.SUMBER_UPETI, true, -upeti, KEY + "buku_kas");
					info.putInt("upeti", upeti);
				} else {
					info.putInt("upeti", 0);
				}
			}
			if (hari >= info.getLongOr("calonHari", hari) + CALON_HARI) {
				info.putLong("calonHari", hari);
				var calon = DiplomasiGabung.calon(server, sekte, "gabung_cabang");
				if (calon != null) {
					Halls.addCandidates(owner, List.of(calon));
					Halls.log(owner, Component.translatable(KEY + "calon", DiplomasiGabung.nama(server, sekte)));
				}
			}
		}
		GabungData.get(server).setDirty();
	}

	private static CompoundTag info(MinecraftServer server, String sekte, UUID owner) {
		CompoundTag b = DiplomasiGabung.bagian(server, sekte);
		CompoundTag all = b.getCompoundOrEmpty("cabangInfo");
		if (!b.contains("cabangInfo")) {
			b.put("cabangInfo", all);
		}
		CompoundTag one = all.getCompoundOrEmpty(owner.toString());
		if (!all.contains(owner.toString())) {
			all.put(owner.toString(), one);
		}
		return one;
	}

	/** Registers the listeners once (from {@code Politik.init}). */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Halls.addStatusLineProvider(Cabang::statusAula);
		GabungBus.KELUAR.register((player, sekte, status) -> {
			MinecraftServer server = player.level().getServer();
			if (server != null && daftar(server, sekte).contains(player.getUUID())) {
				lepas(server, player.getUUID(), sekte, status == StatusKeluar.PAMIT ? LEPAS_PAMIT : 0);
			}
		});
		GabungBus.PK_BERUBAH.register((player, sekte, lama, baru, sebab) -> {
			if (baru > lama && sebab != null && sebab.startsWith("gabung.misi.")) {
				misiSelesai(player, sebab.substring("gabung.misi.".length()));
			}
		});
	}

	/** Every branch with its owner and tribute, for the Politik screen. */
	public static Map<UUID, Integer> upeti(MinecraftServer server, String sekte) {
		Map<UUID, Integer> out = new java.util.LinkedHashMap<>();
		for (UUID owner : daftar(server, sekte)) {
			out.put(owner, upetiTerakhir(server, sekte, owner));
		}
		return out;
	}

	private Cabang() {
	}
}
