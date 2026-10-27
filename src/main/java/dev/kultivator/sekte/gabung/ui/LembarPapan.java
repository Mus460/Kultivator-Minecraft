package dev.kultivator.sekte.gabung.ui;

import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.hidup.PapanMisiSekte;
import dev.kultivator.sekte.gabung.hidup.TugasHarian;
import dev.kultivator.sekte.gabung.hidup.TurnamenMurid;
import dev.kultivator.sekte.gabung.politik.MisiPerang;
import dev.kultivator.sekte.gabung.politik.PanggilanPerang;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Papan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The sect mission board ({@code gabung_papan}, DESIGN 41.6.3-41.6.4, 41.6.10 and 41.10.5, keys {@link Papan}). The sheet
 * is the sect header ({@link GabungLayar#kepala}), then the parts the gameplay modules write themselves: the board, the
 * running missions and the group rosters ({@code PapanMisiSekte.isiLembar}, 41C), the daily duties
 * ({@code TugasHarian.isiLembar}, 41C), the war call ({@code PanggilanPerang.lembarPapan}, 41D-P) and the war missions
 * ({@code MisiPerang.lembar}, appended to {@code daftar} with tab {@code perang}). The war tab is hidden without a war.
 * <p>
 * Default ops (a module may replace any with {@link GabungLayar#daftarAksi}): {@code terima:ID} (a war mission id goes to
 * {@code MisiPerang.terima}, any other to {@code PapanMisiSekte.terima}), {@code gabung:ID}, {@code batal:ID} (war:
 * {@code MisiPerang.batal}), {@code tugas:ID} ({@code TugasHarian.mulai}); {@code tandai:X:Z} is answered by
 * {@link GabungLayar}. Each answer is the module's reason in danger colours, or a short confirmation.
 */
public final class LembarPapan {

	private static final String K = "kultivator.gabung.ui2.";

	static void init() {
		GabungLayar.daftar(Papan.LAYAR, LembarPapan::bangun);
		GabungLayar.bawaan(Papan.LAYAR, Papan.OP_TERIMA, (p, npcId, op) -> terima(p, LembarKunci.arg(op, 1)));
		GabungLayar.bawaan(Papan.LAYAR, Papan.OP_GABUNG, (p, npcId, op) -> hasil(p, PapanMisiSekte.gabung(p, LembarKunci.arg(op, 1)),
				Component.translatable(K + "papan.bergabung")));
		GabungLayar.bawaan(Papan.LAYAR, Papan.OP_BATAL, (p, npcId, op) -> batal(p, LembarKunci.arg(op, 1)));
		GabungLayar.bawaan(Papan.LAYAR, Papan.OP_TUGAS, (p, npcId, op) -> hasil(p, TugasHarian.mulai(p, LembarKunci.arg(op, 1)),
				Component.translatable(K + "papan.tugas_mulai")));
	}

	static CompoundTag bangun(ServerPlayer player, int npcId) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		CompoundTag tag = GabungLayar.kepala(player, sekte, Component.translatable(K + "papan.judul"));
		if (sekte == null) {
			return bukanAnggota(tag);
		}
		PapanMisiSekte.isiLembar(player, sekte, tag);
		TugasHarian.isiLembar(player, sekte, tag);
		tag.put(Papan.TURNAMEN, TurnamenMurid.lembar(player, sekte));
		ListTag daftar = tag.getListOrEmpty(LembarKunci.DAFTAR);
		ListTag perangMisi = MisiPerang.lembar(player);
		// war missions first: a war call outranks the board (41.6.10)
		ListTag gabung = new ListTag();
		for (Tag t : perangMisi) {
			gabung.add(t);
		}
		for (Tag t : daftar) {
			if (gabung.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			gabung.add(t);
		}
		tag.put(LembarKunci.DAFTAR, gabung);
		CompoundTag perang = PanggilanPerang.lembarPapan(player);
		ListTag sembunyi = new ListTag();
		if (perang != null) {
			tag.put(Papan.PERANG, perang);
			if (perang.getBooleanOr(Papan.WAJIB, false)) {
				tag.putString(LembarKunci.TAB, Papan.TAB_PERANG);
			}
		} else {
			sembunyi.add(StringTag.valueOf(Papan.TAB_PERANG));
		}
		tag.put(LembarKunci.TAB_SEMBUNYI, sembunyi);
		CompoundTag kosong = new CompoundTag();
		LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable(K + "papan.kosong"));
		tag.put(LembarKunci.KOSONG, kosong);
		return tag;
	}

	private static void terima(ServerPlayer player, String id) {
		if (MisiPerang.Jenis.byId(id) != null) {
			hasil(player, MisiPerang.terima(player, id), Component.translatable(K + "papan.perang_diterima"));
		} else {
			hasil(player, PapanMisiSekte.terima(player, id), Component.translatable(K + "papan.diterima"));
		}
	}

	private static void batal(ServerPlayer player, String id) {
		if (MisiPerang.Jenis.byId(id) != null) {
			hasil(player, MisiPerang.batal(player), Component.translatable(K + "papan.dibatalkan"));
		} else {
			hasil(player, PapanMisiSekte.batal(player, id), Component.translatable(K + "papan.dibatalkan"));
		}
	}

	// ================================================================== shared by the 41U-2 sheets

	/** The whole-screen lock of a member screen opened by a non-member ("Belum bergabung", the way to the directory). */
	static CompoundTag bukanAnggota(CompoundTag tag) {
		CompoundTag kunci = new CompoundTag();
		LembarKunci.teks(kunci, LembarKunci.ALASAN, Component.translatable(K + "bukan_anggota"));
		LembarKunci.teks(kunci, LembarKunci.SYARAT, Component.translatable(K + "bukan_anggota.syarat"));
		tag.put(LembarKunci.KUNCI_LAYAR, kunci);
		return tag;
	}

	/** The answer of a module call: its refusal in danger colours, else {@code ok} (null: the module already spoke). */
	static void hasil(ServerPlayer player, @Nullable Component tolak, @Nullable Component ok) {
		if (tolak != null) {
			GabungLayar.pesan(player, tolak, LembarKunci.PESAN_BAHAYA);
		} else if (ok != null) {
			GabungLayar.pesan(player, ok, LembarKunci.PESAN_OK);
		}
	}

	private LembarPapan() {
	}
}
