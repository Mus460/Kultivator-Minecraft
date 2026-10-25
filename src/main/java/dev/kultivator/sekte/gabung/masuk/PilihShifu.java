package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.GabungGerakId;
import dev.kultivator.sekte.gabung.dunia.TokohAnchors;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * "Tetua Memilih Murid" of DESIGN 41.5.7 (contract C41.B1, owner 41B; Su Ming and the peaks that choose their disciples):
 * after the oath the elders who want the new member step forward. Interest of an elder
 * {@code m = 30 + 10 x akar + 20 x (Dao match) + 15 x (Calon Istimewa) + sikap/5 + rand(0..20)}; those with
 * {@code m >= 50} make an offer (at most 3; a Calon Istimewa always draws at least two), else the admissions elder takes
 * the player "out of pity". The player picks on the {@code gabung_shifu} screen (built and handled here, drawn by 41U-2):
 * portrait, name, office, realm, Dao, temper and a promise ("Aku akan mengajarimu pedang", "Aku punya resep pil"). A
 * refused elder's attitude drops by 5 (a proud one by 10, and he remembers). The bond starts at 20.
 * <p>
 * When no elder is at home (wounded, none generated yet) the choosing waits: the member is told, and it runs again from
 * {@code /kultivasi gabung shifu} or the next login day.
 */
public final class PilihShifu {

	/** Offer threshold and the most offers shown. */
	public static final int MINAT_MIN = 50;
	public static final int TAWARAN_MAKS = 3;
	/** Starting bond with the chosen teacher (41.5.7 "Ikatan Guru"). */
	public static final int IKATAN_AWAL = 20;
	/** Attitude lost by a refused elder (proud: double). */
	public static final int TOLAK_SIKAP = 5;

	private static final String TAWARAN = "shifu_tawaran";
	private static final String KASIHAN = "shifu_kasihan";
	private static final String TUNDA = "shifu_tunda";

	/** Pure interest formula of 41.5.7 ({@code akar} 0 TRASH .. 4 HEAVENLY, {@code acak} 0..20 from the caller's random). */
	public static int minat(int akar, boolean daoCocok, boolean istimewa, int sikap, int acak) {
		return 30 + 10 * akar + (daoCocok ? 20 : 0) + (istimewa ? 15 : 0) + sikap / 5 + Math.max(0, Math.min(20, acak));
	}

	static void init() {
		TempatLayar.daftar(GabungLembar.Shifu.LAYAR, PilihShifu::aksi, PilihShifu::bangun);
		Keanggotaan.addPenyentuh((player, k, hari) -> {
			if (k.status.anggota() && k.shifu.isEmpty() && k.bagian(Putusan.B).getBooleanOr(TUNDA, false)) {
				Msg.chat(player, Masuk.t("shifu.ingat").withStyle(ChatFormatting.GRAY));
			}
		});
	}

	/**
	 * Starts the choosing ceremony for {@code player}, a fresh member without a Shifu. Returns false when it cannot run now
	 * (not a member, already has a teacher, a guest elder, the elders are away: it is retried later).
	 */
	public static boolean mulai(ServerPlayer player) {
		if (!Gabung.aktif()) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !k.shifu.isEmpty() || Keanggotaan.JALUR_TAMU.equals(k.jalur)) {
			return false;
		}
		long hari = GabungApi.hari(server);
		List<TokohSekte.Tokoh> elders = new ArrayList<>();
		for (TokohSekte.Tokoh t : TokohSekte.tetua(server, sekte)) {
			if (!t.terluka(hari) && t.status() == TokohSekte.Status.AKTIF && !t.ketua()) {
				elders.add(t);
			}
		}
		CompoundTag bag = k.bagian(Putusan.B);
		if (elders.isEmpty()) {
			bag.putBoolean(TUNDA, true);
			GabungData.get(server).setDirty();
			return false;
		}
		bag.remove(TUNDA);
		CultivationData cd = Cultivators.get(player);
		Dao dao = cd.dao();
		boolean istimewa = bag.getIntOr(Putusan.L_SKOR, 0) >= Putusan.ISTIMEWA || bag.getBooleanOr("putusan_istimewa", false);
		Random rnd = new Random(player.getUUID().getLeastSignificantBits() ^ sekte.hashCode() ^ hari * 7919L);
		record Minat(TokohSekte.Tokoh t, int m) {
		}
		List<Minat> all = new ArrayList<>();
		for (TokohSekte.Tokoh t : elders) {
			int m = minat(cd.rootGrade().ordinal(), dao != null && dao == t.dao(), istimewa, TokohSekte.sikap(server, t.id(), player.getUUID()),
					rnd.nextInt(21));
			all.add(new Minat(t, m));
		}
		all.sort(Comparator.comparingInt(Minat::m).reversed());
		List<Minat> offers = new ArrayList<>();
		for (Minat mi : all) {
			if (offers.size() < TAWARAN_MAKS && (mi.m() >= MINAT_MIN || istimewa && offers.size() < 2)) {
				offers.add(mi);
			}
		}
		ListTag list = new ListTag();
		boolean kasihan = offers.isEmpty();
		if (kasihan) {
			TokohSekte.Tokoh pity = TokohSekte.penerimaan(server, sekte);
			Minat chosen = null;
			for (Minat mi : all) {
				if (pity != null && mi.t().id().equals(pity.id())) {
					chosen = mi;
				}
			}
			offers.add(chosen != null ? chosen : all.getFirst());
		}
		for (Minat mi : offers) {
			CompoundTag o = new CompoundTag();
			o.putString("id", mi.t().id());
			o.putInt("m", mi.m());
			list.add(o);
		}
		bag.put(TAWARAN, list);
		bag.putBoolean(KASIHAN, kasihan);
		GabungData.get(server).setDirty();
		// the first suitor speaks
		KultivatorNpc body = TokohAnchors.tubuh(player.level(), sekte, offers.getFirst().t().id());
		if (body != null) {
			HidupApi.tatap(body, player);
			HidupApi.klip(body, GabungGerakId.TETUA_MENILAI, 0, 60);
			Masuk.ucap(body, "gabung.shifu_pilih", "elder.welcome", Masuk.t(kasihan ? "ucap.shifu_kasihan" : "ucap.shifu_pilih"));
		}
		Msg.chat(player, Masuk.t(kasihan ? "shifu.kasihan" : istimewa ? "shifu.rebutan" : "shifu.mulai", offers.size()).withStyle(ChatFormatting.GOLD));
		CompoundTag sheet = bangun(player, -1);
		if (sheet != null) {
			TempatLayar.bukaDi(player, GabungLembar.Shifu.LAYAR, player.blockPosition(), sheet);
		}
		return true;
	}

	/** Reopens a pending offer screen ({@code /kultivasi gabung shifu}); false when there is none. */
	public static boolean bukaLagi(ServerPlayer player) {
		CompoundTag sheet = bangun(player, -1);
		if (sheet == null) {
			return false;
		}
		TempatLayar.bukaDi(player, GabungLembar.Shifu.LAYAR, player.blockPosition(), sheet);
		return true;
	}

	/** The offer sheet ({@code gabung_shifu}), or null when no offer waits. */
	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		ListTag offers = k.bagian(Putusan.B).getListOrEmpty(TAWARAN);
		if (sekte == null || offers.isEmpty() || !k.shifu.isEmpty()) {
			return null;
		}
		CompoundTag tag = new CompoundTag();
		Putusan.kepala(tag, player, sekte);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Masuk.t("shifu.judul"));
		ListTag rows = new ListTag();
		for (int i = 0; i < offers.size(); i++) {
			CompoundTag o = offers.getCompoundOrEmpty(i);
			TokohSekte.Tokoh t = TokohSekte.tokoh(server, o.getStringOr("id", ""));
			if (t == null) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, t.id());
			KultivatorNpc body = TokohAnchors.tubuh(player.level(), sekte, t.id());
			row.putInt(GabungLembar.POTRET, body == null ? -1 : body.getId());
			Component nama = TokohSekte.nama(server, t.id());
			LembarKunci.teks(row, LembarKunci.NAMA, nama != null ? nama : Component.literal(t.nama()));
			LembarKunci.teks(row, GabungLembar.JABATAN, jabatan(t));
			row.putInt(GabungLembar.RANAH, t.ranah().ordinal());
			row.putString(GabungLembar.DAO, t.dao().id());
			LembarKunci.teks(row, GabungLembar.WATAK, watak(t));
			LembarKunci.teks(row, GabungLembar.Shifu.JANJI, janji(t));
			row.putInt(GabungLembar.Shifu.MINAT, o.getIntOr("m", MINAT_MIN));
			rows.add(row);
		}
		tag.put(GabungLembar.Shifu.TAWARAN, rows);
		tag.putBoolean(GabungLembar.Shifu.KASIHAN, k.bagian(Putusan.B).getBooleanOr(KASIHAN, false));
		return tag;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		if (!GabungLembar.Shifu.OP_PILIH.equals(parts[0])) {
			return;
		}
		pilih(player, LembarKunci.arg(parts, 1));
	}

	/** Takes elder {@code tokohId} (one of the waiting offers) as teacher; false when it was not on offer. */
	public static boolean pilih(ServerPlayer player, String tokohId) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		CompoundTag bag = k.bagian(Putusan.B);
		ListTag offers = bag.getListOrEmpty(TAWARAN);
		boolean found = false;
		for (int i = 0; i < offers.size(); i++) {
			if (offers.getCompoundOrEmpty(i).getStringOr("id", "").equals(tokohId)) {
				found = true;
			}
		}
		if (!found || !k.shifu.isEmpty() || k.sekteAktif() == null) {
			return false;
		}
		k.shifu = tokohId;
		k.ikatanShifu = IKATAN_AWAL;
		for (int i = 0; i < offers.size(); i++) {
			String other = offers.getCompoundOrEmpty(i).getStringOr("id", "");
			if (!other.equals(tokohId)) {
				TokohSekte.Tokoh t = TokohSekte.tokoh(server, other);
				int drop = t != null && t.sifat().contains(Sifat.SOMBONG) ? TOLAK_SIKAP * 2 : TOLAK_SIKAP;
				TokohSekte.ubahSikap(server, other, player.getUUID(), -drop);
			}
		}
		TokohSekte.ubahSikap(server, tokohId, player.getUUID(), 10);
		bag.remove(TAWARAN);
		bag.remove(KASIHAN);
		k.catat(GabungApi.hari(server), "kultivator.gabung.masuk.riwayat.shifu", tokohId);
		GabungData.get(server).setDirty();
		Advancements.grant(player, "sekte/gabung_shifu");
		Component nama = TokohSekte.nama(server, tokohId);
		Msg.chat(player, Masuk.t("shifu.dipilih", nama != null ? nama : Component.literal(tokohId)).withStyle(ChatFormatting.GREEN));
		ServerLevel level = player.level();
		String sekte = TokohSekte.sekteDari(tokohId);
		KultivatorNpc body = sekte == null ? null : TokohAnchors.tubuh(level, sekte, tokohId);
		if (body != null) {
			HidupApi.tatap(body, player);
			body.say(Masuk.t("shifu.terima_murid"), 100);
		}
		TempatLayar.tutup(player);
		return true;
	}

	private static Component jabatan(TokohSekte.Tokoh t) {
		if (t.jabatan() != null) {
			return Component.translatable("kultivator.gabung.masuk.shifu.jabatan." + t.jabatan().id());
		}
		return Masuk.t(t.penerimaan() ? "shifu.jabatan.penerimaan" : "shifu.jabatan.tetua");
	}

	private static Component watak(TokohSekte.Tokoh t) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < t.sifat().size(); i++) {
			if (i > 0) {
				out.append(Component.literal(", "));
			}
			out.append(Component.translatable(t.sifat().get(i).key()));
		}
		return out;
	}

	/** The elder's promise by office (an office-less elder promises by Dao). */
	private static Component janji(TokohSekte.Tokoh t) {
		if (t.jabatan() != null) {
			return Masuk.t("shifu.janji." + t.jabatan().id());
		}
		if (t.penerimaan()) {
			return Masuk.t("shifu.janji.penerimaan");
		}
		return Masuk.t("shifu.janji.dao", t.dao().displayName());
	}

	private PilihShifu() {
	}
}
