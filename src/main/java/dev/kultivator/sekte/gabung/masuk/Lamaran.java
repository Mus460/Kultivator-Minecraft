package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import dev.kultivator.sekte.gabung.api.SyaratMasuk;
import dev.kultivator.sekte.gabung.ui.GabungLayar;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.sekte.gabung.ui.LembarLamaran;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The application of DESIGN 41.5.3 (contract C41.B1, owner 41B). The Lamaran screen ({@code gabung_lamaran}, 41.10.2; sheet
 * built by 41U-1's {@code LembarLamaran}) shows every door ({@code api/Pintu}) with the ticks and crosses of
 * {@code SyaratMasuk.periksa}; "Ajukan" ({@link #ajukan}) makes the player a CALON of that sect for 3 world days: one
 * application at a time, the next test day noted, a <b>Tanda Calon</b> in the bag (removed when the status ends). The
 * servant door and the guest door need no test day: their short talk starts at once when the player stands at the gate,
 * else at the next visit. A failed application or test waits {@code gabungSekte.jedaLamarHari} days per sect
 * ({@code Keanggotaan.jedaLamar}); a lapsed candidacy simply ends.
 * <p>
 * With 41U-1's screen ({@code LembarLamaran}): {@link #buka} opens it, a sheet filler remembers the sect of every built
 * sheet ({@link #dilihat}) and this class implements its ops {@code ajukan:PINTU} and {@code batal}
 * ({@code GabungLayar.daftarAksi}), so the Tanda Calon, the door checks and the servant/guest start are one code path for
 * the button, the command and the recruiters. Without the screen {@link #buka} prints the requirements in chat.
 */
public final class Lamaran {

	/** Days a candidacy lasts (41.5.3, {@code calonSampai = hari + 3}). */
	public static final int CALON_HARI = 3;
	/** Days before the same sect may be asked again after a failure (41.5.2; {@code gabungSekte.jedaLamarHari}). */
	public static final int JEDA_HARI = 3;

	/** player -> the sect whose application screen they last opened or a recruiter talked about. */
	private static final Map<UUID, String> DILIHAT = new HashMap<>();

	static void init() {
		ServerPlayerEvents.LEAVE.register(player -> DILIHAT.remove(player.getUUID()));
		Keanggotaan.addPenyentuh((player, k, hari) -> {
			if (k.status == StatusAnggota.CALON && k.calonSampai >= 0L && hari > k.calonSampai) {
				kedaluwarsa(player, k);
			}
		});
		// the 41U-1 screen (LembarLamaran): learn the sect of every built sheet, and run its two ops here
		GabungLayar.addPengisi(GabungLembar.Lamaran.LAYAR, (player, npcId, lembar) -> lihat(player, lembar.getStringOr(GabungLembar.SEKTE_ID, "")));
		GabungLayar.daftarAksi(GabungLembar.Lamaran.LAYAR, GabungLembar.Lamaran.OP_AJUKAN, (player, npcId, op) -> {
			String sekte = dilihat(player);
			Pintu pintu = Pintu.byId(op.length > 1 ? op[1] : "", null);
			if (sekte == null || pintu == null) {
				GabungLayar.pesan(player, Component.translatable("kultivator.gabung.belum_tersedia"), LembarKunci.PESAN_BAHAYA);
				return;
			}
			Component why = ajukan(player, sekte, pintu);
			if (why != null) {
				GabungLayar.pesan(player, why, LembarKunci.PESAN_BAHAYA);
			} else {
				GabungLayar.pesan(player, Masuk.t("lamar.diajukan_singkat", Masuk.nama(sekte), pintu.nama()), LembarKunci.PESAN_OK);
			}
		});
		GabungLayar.daftarAksi(GabungLembar.Lamaran.LAYAR, GabungLembar.Lamaran.OP_BATAL, (player, npcId, op) -> {
			if (!batal(player)) {
				GabungLayar.pesan(player, Masuk.t("cmd.tak_ada_lamaran"), LembarKunci.PESAN_PERINGATAN);
			}
		});
	}

	/** The sect whose application screen {@code player} looks at (or last looked at), or null. */
	public static @Nullable String dilihat(ServerPlayer player) {
		return DILIHAT.get(player.getUUID());
	}

	/** Remembers the sect a recruiter or the directory talks about (before a {@code TempatLayar.bukaDari}). */
	public static void lihat(ServerPlayer player, String sekte) {
		if (sekte != null && !sekte.isEmpty()) {
			DILIHAT.put(player.getUUID(), sekte);
		}
	}

	/**
	 * Opens the Lamaran screen of {@code sekte} ({@code "s<i>"}) for {@code player}, from the Direktori, a recruiter's "Lamar"
	 * button or the hub. Does nothing for an unknown sect.
	 */
	public static void buka(ServerPlayer player, String sekte) {
		buka(player, sekte, -1);
	}

	/** {@link #buka(ServerPlayer, String)} for a recruiter's body {@code npcId} (actions then check reach to that body). */
	public static void buka(ServerPlayer player, String sekte, int npcId) {
		if (!Gabung.aktif() || sekte == null || SekteApi.diplomasi().tier(sekte) <= 0) {
			return;
		}
		lihat(player, sekte);
		GabungApi.kenal(player, sekte);
		if (GabungLayar.terdaftar().contains(GabungLembar.Lamaran.LAYAR)) {
			LembarLamaran.buka(player, sekte, npcId);
		} else {
			ringkas(player, sekte);
		}
	}

	/** Chat form of the screen: every door with its requirement lines and a clickable "Ajukan". */
	public static void ringkas(ServerPlayer player, String sekte) {
		Msg.chat(player, Masuk.t("lamar.judul", Masuk.nama(sekte), GabungApi.kelasSekte(sekte).nama(), GabungApi.corak(sekte).nama())
				.withStyle(ChatFormatting.GOLD));
		MinecraftServer server = player.level().getServer();
		long next = HariUjian.berikutUntuk(server, sekte);
		if (next >= 0L) {
			Msg.chat(player, Masuk.t("lamar.hari_ujian", next, Math.max(0L, next - GabungApi.hari(server))).withStyle(ChatFormatting.GRAY));
		}
		for (Pintu p : Pintu.values()) {
			Component why = alasanPintu(player, sekte, p);
			player.sendSystemMessage(Component.literal(" ").append(p.nama().copy().withStyle(why == null ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
					.append(Component.literal("  "))
					.append(why == null ? Putusan.tombol(Masuk.t("lamar.ajukan"), "/kultivasi gabung lamar " + sekte + " " + p.id(), Masuk.t("lamar.ajukan_hover"))
							: why.copy().withStyle(ChatFormatting.GRAY)));
			for (SyaratMasuk.Baris baris : SyaratMasuk.periksa(player, sekte, p)) {
				if (!baris.ok()) {
					player.sendSystemMessage(Component.literal("    ✗ ").withStyle(ChatFormatting.RED).append(baris.teks()));
				}
			}
		}
	}

	/** Why {@code pintu} cannot be taken for {@code sekte} now (null = it can): requirements, token, letter, switches. */
	public static @Nullable Component alasanPintu(ServerPlayer player, String sekte, Pintu pintu) {
		List<SyaratMasuk.Baris> lines = SyaratMasuk.periksa(player, sekte, pintu);
		for (SyaratMasuk.Baris b : lines) {
			if (!b.ok()) {
				return b.teks();
			}
		}
		switch (pintu) {
			case TOKEN -> {
				if (TokenNaik.cari(player, sekte).isEmpty()) {
					return Masuk.t("lamar.butuh_token");
				}
			}
			case UNDANGAN -> {
				if (!TokenNaik.punyaUndangan(player, sekte)) {
					return Masuk.t("lamar.butuh_undangan");
				}
			}
			default -> {
			}
		}
		return null;
	}

	/** True when the token in the bag opens door TOKEN of {@code sekte} (its own or a general one at class &le; 3). */
	public static boolean tokenSah(ServerPlayer player, String sekte) {
		return !TokenNaik.cari(player, sekte).isEmpty();
	}

	/** The sect's next test day (world day), -1 unknown. */
	public static long hariUjianBerikut(ServerPlayer player, String sekte) {
		return HariUjian.berikutUntuk(player.level().getServer(), sekte);
	}

	/**
	 * "Ajukan": makes the player a CALON of {@code sekte} through {@code pintu}. Null on success, else the reason. The servant
	 * and guest doors start their talk at once when the player stands at the gate.
	 */
	public static @Nullable Component ajukan(ServerPlayer player, String sekte, Pintu pintu) {
		if (!Gabung.aktif()) {
			return Component.translatable("kultivator.gabung.cmd.mati");
		}
		Component why = alasanPintu(player, sekte, pintu);
		if (why != null) {
			return why;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		long hari = GabungApi.hari(server);
		k.sekte = sekte;
		k.status = StatusAnggota.CALON;
		k.pintu = pintu;
		k.hariUjian = pintu == Pintu.PELAYAN || pintu == Pintu.TAMU ? hari : HariUjian.berikutUntuk(server, sekte);
		k.calonSampai = Math.max(hari + CALON_HARI, k.hariUjian);
		k.catat(hari, "kultivator.gabung.masuk.riwayat.lamar", sekte, pintu.id());
		data.setDirty();
		GabungApi.kenal(player, sekte);
		TokenNaik.beriTandaCalon(player, sekte);
		Advancements.grant(player, "sekte/gabung_lamar");
		Msg.chat(player, Masuk.t("lamar.diajukan", Masuk.nama(sekte), pintu.nama(), Math.max(0L, k.hariUjian - hari), k.calonSampai - hari)
				.withStyle(ChatFormatting.GREEN));
		if (pintu == Pintu.PELAYAN || pintu == Pintu.TAMU) {
			// no test day for these doors: the talk starts now when the gate is near, else on the next visit
			Component start = HariUjian.mulai(player, sekte, pintu);
			if (start != null) {
				Msg.chat(player, Masuk.t("lamar.datang_ke_gerbang", Masuk.nama(sekte)).withStyle(ChatFormatting.GRAY));
			}
		}
		return null;
	}

	/** Withdraws the running candidacy (no penalty); false when there is none. */
	public static boolean batal(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || k.status != StatusAnggota.CALON) {
			return false;
		}
		HariUjian.Sesi s = HariUjian.sesi(player);
		if (s != null && !s.selesai) {
			return false;
		}
		String sekte = k.sekte;
		akhiriCalon(k);
		k.catat(GabungApi.hari(server), "kultivator.gabung.masuk.riwayat.batal", sekte);
		data.setDirty();
		TokenNaik.hapusTandaCalon(player);
		Msg.chat(player, Masuk.t("lamar.dibatalkan", Masuk.nama(sekte)).withStyle(ChatFormatting.YELLOW));
		return true;
	}

	/** A failed application or test: the candidacy ends and the sect's pause starts. */
	static void tolak(ServerPlayer player, String sekte) {
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.anggota(player.getUUID());
		long hari = GabungApi.hari(server);
		k.jedaLamar.put(sekte, hari + Math.max(0, GabungConfig.get().jedaLamarHari));
		if (k.status == StatusAnggota.CALON && sekte.equals(k.sekte)) {
			akhiriCalon(k);
		}
		k.catat(hari, "kultivator.gabung.masuk.riwayat.tolak", sekte);
		data.setDirty();
		TokenNaik.hapusTandaCalon(player);
	}

	/** The candidacy ran out (3 days, no test or no oath): it ends quietly with a line. */
	static void kedaluwarsa(ServerPlayer player, Keanggotaan k) {
		String sekte = k.sekte;
		akhiriCalon(k);
		GabungData.get(player.level().getServer()).setDirty();
		TokenNaik.hapusTandaCalon(player);
		Msg.chat(player, Masuk.t("lamar.kedaluwarsa", Masuk.nama(sekte)).withStyle(ChatFormatting.GRAY));
	}

	private static void akhiriCalon(Keanggotaan k) {
		k.sekte = "";
		k.status = StatusAnggota.BEBAS;
		k.pintu = Pintu.GERBANG;
		k.hariUjian = -1L;
		k.calonSampai = -1L;
		Putusan.lupakan(k);
	}

	private Lamaran() {
	}
}
