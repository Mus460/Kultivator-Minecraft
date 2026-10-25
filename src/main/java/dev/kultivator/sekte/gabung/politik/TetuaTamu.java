package dev.kultivator.sekte.gabung.politik;

import dev.kultivator.duel.DuelIds;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungBus;
import dev.kultivator.sekte.gabung.GabungConfig;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.api.Pintu;
import dev.kultivator.sekte.gabung.api.StatusKeluar;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.masuk.GabungItems;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Guest elders of DESIGN 41.8.3 ("Tetua Tamu", the <i>keqing</i> of Qin and the memorial of Li Si). The side road of a
 * hall owner (R5): his hall stays independent, he gains an elder's prices at the Merit Pavilion, the Private Peak (rent
 * x1.5), bought realm quotas, a 100 PK allowance and the sect's formations; no office, no vote, never master (that needs
 * the full oath). At most {@code Pangkat.TAMU_MAKS} (2) per sect.
 * <ul>
 *   <li><b>Ujian Tamu</b> ({@link #mulaiUjian}): a friendly Sword Duel against the Grand Elder (else the master), limited to
 *   {@link #UJIAN_DETIK} seconds; lasting the minute, winning, or bringing the elder to half health passes. Or
 *   {@link #persembahan}: a work worth at least {@link #KARYA_PK} PK (a High pill, a grade-3 treasure, a formation core),
 *   which goes to the treasury.</li>
 *   <li><b>Tugas Tamu</b>: one board mission B or higher, or one war mission, per {@link #TUGAS_HARI} days; failing twice
 *   ends the status ("Lepas Tamu", standing -10). Declaring war on the sect with his hall: "Lepas Tamu", standing -20
 *   (R6, from {@code DiplomasiGabung.veto}).</li>
 * </ul>
 * State in the guest's {@code Keanggotaan.bagian("politik")}: {@code tamuTugas} (day the current period began),
 * {@code tamuSelesai} (a duty done in this period), {@code tamuLalai}, and {@code lepasTamu} (a release decided while
 * he was offline, carried out at login).
 */
public final class TetuaTamu {

	/** Seconds of the guest duel. */
	public static final int UJIAN_DETIK = 60;
	/** PK value of an accepted offering. */
	public static final int KARYA_PK = 300;
	/** Days of one duty period and the missed periods that end the status. */
	public static final int TUGAS_HARI = 30;
	public static final int LALAI_MAKS = 2;
	/** Least standing to be received as a guest. */
	public static final int STANDING_MIN = 20;

	private static final String KEY = "kultivator.gabung.politik.tamu.";
	private static boolean initialised;

	/** Why {@code player} may not become a guest elder of {@code sekte} now, or null. */
	public static @Nullable Component syarat(ServerPlayer player, String sekte) {
		MinecraftServer server = player.level().getServer();
		if (!Gabung.aktif() || !GabungConfig.get().bolehTamu) {
			return Component.translatable(KEY + "mati");
		}
		if (sekte == null || GabungApi.tier(sekte) <= 0) {
			return Component.translatable(KEY + "sekte");
		}
		if (Halls.hallOf(player.getUUID()) == null) {
			return Component.translatable(KEY + "tanpa_aula");
		}
		if (GabungApi.sekte(player.getUUID()) != null) {
			return Component.translatable(KEY + "sudah_anggota");
		}
		if (!Kursi.bebas(server, sekte, Pangkat.TAMU)) {
			return Component.translatable(KEY + "penuh", Pangkat.TAMU_MAKS);
		}
		if (SekteApi.diplomasi().atWar(player.getUUID(), sekte)) {
			return Component.translatable(KEY + "perang");
		}
		int standing = DiplomasiGabung.standing(server, player.getUUID(), sekte);
		if (standing < STANDING_MIN) {
			return Component.translatable(KEY + "standing", STANDING_MIN, standing);
		}
		return null;
	}

	/** The guest duel; null when it started, else why not. */
	public static @Nullable Component mulaiUjian(ServerPlayer player, String sekte) {
		Component why = syarat(player, sekte);
		if (why != null) {
			return why;
		}
		String busy = Sibuk.why(player);
		if (busy != null) {
			return Sibuk.text(busy);
		}
		MinecraftServer server = player.level().getServer();
		TokohSekte.Tokoh agung = TokohSekte.jabatan(server, sekte, Jabatan.TETUA_AGUNG);
		String id = agung != null ? agung.id() : TokohSekte.idKetua(sekte);
		Component nama = agung != null ? Component.literal(agung.nama()) : SekteApi.diplomasi().master(sekte);
		int step = agung != null ? Kursi.langkahTokoh(server, sekte, id) : DiplomasiGabung.langkahKetua(server, sekte);
		why = Kursi.tandingTokoh(player, sekte, id, nama == null ? Component.literal("?") : nama, step, 20 * UJIAN_DETIK,
				(p, menang, alasan, lawan) -> hasilUjian(p, sekte, menang, alasan, lawan));
		if (why == null) {
			player.sendSystemMessage(Component.translatable(KEY + "ujian", UJIAN_DETIK).withStyle(ChatFormatting.GOLD));
		}
		return why;
	}

	private static void hasilUjian(ServerPlayer player, String sekte, boolean menang, int alasan, @Nullable LivingEntity lawan) {
		boolean separuh = lawan != null && lawan.getHealth() <= lawan.getMaxHealth() * 0.5F;
		boolean lulus = menang || alasan == DuelIds.RES_WAKTU || separuh;
		if (!lulus) {
			player.sendSystemMessage(Component.translatable(KEY + "gagal").withStyle(ChatFormatting.RED));
			return;
		}
		terima(player, sekte, "ujian");
	}

	/** The offering instead of the duel: the stack in hand worth at least {@link #KARYA_PK} PK; null when accepted. */
	public static @Nullable Component persembahan(ServerPlayer player, String sekte) {
		Component why = syarat(player, sekte);
		if (why != null) {
			return why;
		}
		ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (stack.isEmpty() || stack.has(GabungItems.ASAL_SEKTE)) {
			return Component.translatable(KEY + "karya_kosong", KARYA_PK);
		}
		int pk = KasSekte.nilaiPk(stack);
		if (pk < KARYA_PK) {
			return Component.translatable(KEY + "karya_kurang", stack.getHoverName(), pk, KARYA_PK);
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		KasSekte.catat(player.level().getServer(), sekte, player.getUUID(), pk, DiplomasiGabung.SEBAB_UPETI);
		terima(player, sekte, "karya");
		return null;
	}

	/** Receives the player as guest elder ({@code GabungApi.terima} with door TAMU; the advancement comes with the rank). */
	public static void terima(ServerPlayer player, String sekte, String cara) {
		MinecraftServer server = player.level().getServer();
		GabungApi.terima(player, sekte, Pangkat.TAMU, Pintu.TAMU);
		CompoundTag t = GabungApi.anggota(player).bagian("politik");
		t.putLong("tamuTugas", GabungApi.hari(server));
		t.putBoolean("tamuSelesai", false);
		t.putInt("tamuLalai", 0);
		GabungData.get(server).setDirty();
		player.sendSystemMessage(Component.translatable(KEY + "diterima." + cara, DiplomasiGabung.nama(server, sekte), TUGAS_HARI)
				.withStyle(ChatFormatting.GOLD));
	}

	/** A guest's duty of this period is done (a board mission B+ or a war mission). */
	public static void tugasSelesai(ServerPlayer player) {
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.status.anggota() || k.pangkat != Pangkat.TAMU) {
			return;
		}
		CompoundTag t = k.bagian("politik");
		if (!t.getBooleanOr("tamuSelesai", false)) {
			t.putBoolean("tamuSelesai", true);
			GabungData.get(player.level().getServer()).setDirty();
			player.sendSystemMessage(Component.translatable(KEY + "tugas_selesai").withStyle(ChatFormatting.GREEN));
		}
	}

	/**
	 * "Lepas Tamu": the guest status of {@code owner} ends (an honourable leave of the data door, then standing
	 * {@code delta} from what it was). An offline guest is released at his next login.
	 */
	public static void lepas(MinecraftServer server, UUID owner, int delta, String sebab) {
		Keanggotaan k = server == null ? null : GabungData.get(server).peek(owner);
		if (k == null || !k.status.anggota() || k.pangkat != Pangkat.TAMU) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player == null) {
			k.bagian("politik").putInt("lepasTamu", delta);
			k.bagian("politik").putString("lepasSebab", sebab);
			GabungData.get(server).setDirty();
			return;
		}
		String sekte = k.sekte;
		int before = DiplomasiGabung.standing(server, owner, sekte);
		GabungApi.keluar(player, StatusKeluar.PAMIT);
		DiplomasiGabung.setStanding(server, owner, sekte, before + delta);
		player.sendSystemMessage(Component.translatable(KEY + "lepas." + sebab, DiplomasiGabung.nama(server, sekte), delta)
				.withStyle(ChatFormatting.RED));
	}

	// ================================================================== daily (from Politik)

	/** Duty periods of the sect's guests: a period without a duty counts as missed; two missed end the status. */
	static void harian(MinecraftServer server, String sekte, long hari) {
		GabungData data = GabungData.get(server);
		for (UUID id : data.anggotaSekte(sekte)) {
			Keanggotaan k = data.peek(id);
			if (k == null || k.pangkat != Pangkat.TAMU) {
				continue;
			}
			CompoundTag t = k.bagian("politik");
			long mulai = t.getLongOr("tamuTugas", hari);
			if (hari < mulai + TUGAS_HARI) {
				continue;
			}
			int lalai = t.getIntOr("tamuLalai", 0) + (t.getBooleanOr("tamuSelesai", false) ? 0 : 1);
			t.putLong("tamuTugas", hari);
			t.putBoolean("tamuSelesai", false);
			t.putInt("tamuLalai", lalai);
			data.setDirty();
			if (lalai >= LALAI_MAKS) {
				lepas(server, id, -10, "lalai");
			} else if (lalai > 0) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null) {
					p.sendSystemMessage(Component.translatable(KEY + "peringatan", TUGAS_HARI).withStyle(ChatFormatting.YELLOW));
				}
			}
		}
	}

	/** Registers the listeners once (from {@code Politik.init}). */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		GabungBus.PK_BERUBAH.register((player, sekte, lama, baru, sebab) -> {
			if (baru > lama && sebab != null && (sebab.startsWith("gabung.misi.b") || sebab.startsWith("gabung.misi.a")
					|| sebab.startsWith("gabung.misi.s") || sebab.startsWith("gabung.perang"))) {
				tugasSelesai(player);
			}
		});
		ServerPlayerEvents.JOIN.register(player -> {
			MinecraftServer server = player.level().getServer();
			Keanggotaan k = server == null || !Gabung.aktif() ? null : GabungData.get(server).peek(player.getUUID());
			if (k != null && k.bagian("politik").contains("lepasTamu")) {
				int delta = k.bagian("politik").getIntOr("lepasTamu", -10);
				String sebab = k.bagian("politik").getStringOr("lepasSebab", "lalai");
				k.bagian("politik").remove("lepasTamu");
				k.bagian("politik").remove("lepasSebab");
				lepas(server, player.getUUID(), delta, sebab);
			}
		});
	}

	private TetuaTamu() {
	}
}
