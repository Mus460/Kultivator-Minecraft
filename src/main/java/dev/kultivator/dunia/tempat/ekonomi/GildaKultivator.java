package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.duel.api.DuelEndListeners;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Gilda Kultivator's counter (DESIGN 38.M.3, screen 38.U.3.4): the graded board of {@link MisiGilda}, the running
 * missions, the Young Dragon list and the badge exams. An exam (star 3, 6, 9) costs {@code 5 × n²} NB, needs the points of
 * that star and the realm of its rank (3: Qi Refining, 6: Golden Core, 9: Deity Transformation), once a day, and is a
 * Sword Duel under Realm Suppression ("seranah") against the examiner ({@link NpcRole#PENGUJI}, called to the counter when
 * the clerk is not one). Clerks: {@link NpcRole#PETUGAS_GILDA}, {@link NpcRole#KETUA_GILDA}, {@link NpcRole#PENGUJI}.
 */
public final class GildaKultivator {

	static final Set<NpcRole> PERAN = Set.of(NpcRole.PETUGAS_GILDA, NpcRole.KETUA_GILDA, NpcRole.PENGUJI);
	private static final int[] UJIAN_RANAH = {0, 0, 0, Realm.QI_REFINING.ordinal(), 0, 0, Realm.GOLDEN_CORE.ordinal(), 0, 0,
			Realm.DEITY_TRANSFORMATION.ordinal()};
	/** Running exams: player → {star, examiner uuid}. */
	private static final Map<UUID, Object[]> UJIAN = new HashMap<>();

	static void init() {
		TempatLayar.daftar(LembarKunci.GildaMisi.LAYAR, GildaKultivator::aksi, GildaKultivator::bangun);
		DuelEndListeners.add(GildaKultivator::onDuel);
	}

	static void lupakan(UUID id) {
		UJIAN.remove(id);
	}

	static long biayaUjian(int star) {
		return 5L * star * star;
	}

	private static long situs(KultivatorNpc npc) {
		return npc.anchorSite() != 0L ? npc.anchorSite() : npc.town();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sheet

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		ServerLevel level = (ServerLevel) player.level();
		MinecraftServer server = level.getServer();
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.gilda.judul", LembarKunci.GAYA_BATU, LembarKunci.DISTRIK_GILDA, npc);
		if (!EkonomiConfig.get().gilda) {
			Lembar.kunci(tag, Component.translatable("kultivator.tempat_ekonomi.tutup"), null);
			return tag;
		}
		int kelas = MisiGilda.kelas(server, npc);
		LembarKunci.teks(tag, LembarKunci.SUBJUDUL, Component.translatable("kultivator.tempat_ekonomi.gilda.kelas." + kelas));
		tag.putInt(LembarKunci.GildaMisi.LENCANA, Lencana.bintang(player));
		tag.putInt(LembarKunci.GildaMisi.POIN, Lencana.poin(player));
		tag.putInt(LembarKunci.GildaMisi.POIN_BERIKUT, Lencana.poinBerikut(player));
		EkonomiData data = EkonomiData.get(server);
		tag.putInt(LembarKunci.GildaMisi.MISI_HARI_INI, data.harian(player.getUUID(), Barang.hari(server), "misi"));
		tag.putInt(LembarKunci.GildaMisi.MISI_MAKS, Lencana.misiPerHari(player));
		ListTag papan = new ListTag();
		for (MisiGilda.Tawaran tawaran : MisiGilda.papan(level, situs(npc), npc.blockPosition(), kelas)) {
			papan.add(tawaranTag(player, tawaran));
		}
		tag.put(LembarKunci.GildaMisi.PAPAN, papan);
		ListTag aktif = new ListTag();
		for (net.minecraft.nbt.Tag each : MisiGilda.aktif(player)) {
			if (each instanceof CompoundTag misi) {
				aktif.add(aktifTag(player, misi));
			}
		}
		tag.put(LembarKunci.GildaMisi.TAB_AKTIF, aktif);
		tag.put(LembarKunci.GildaMisi.NAGA, DaftarNagaMuda.lembar(player));
		tag.putInt(LembarKunci.GildaMisi.NAGA_ANDA, DaftarNagaMuda.peringkat(player));
		tag.put(LembarKunci.UJIAN, ujianTag(player, npc));
		if (papan.isEmpty()) {
			Lembar.kosong(tag, "kultivator.tempat_ekonomi.gilda.papan_kosong");
		}
		return tag;
	}

	private static CompoundTag tawaranTag(ServerPlayer player, MisiGilda.Tawaran tawaran) {
		CompoundTag row = new CompoundTag();
		String id = tawaran.jenis().id();
		row.putString(LembarKunci.ID, Long.toString(tawaran.id()));
		String rank = LembarKunci.GildaMisi.SEMUA_PANGKAT.get(tawaran.pangkat());
		row.putString(LembarKunci.GildaMisi.PANGKAT, rank);
		row.putString(LembarKunci.JENIS, id);
		LembarKunci.teks(row, LembarKunci.JUDUL, Component.translatable("kultivator.misi." + id + ".judul"));
		LembarKunci.teks(row, LembarKunci.GildaMisi.CERITA, Component.translatable("kultivator.misi." + id + ".cerita"
				+ ("38EK".equals(tawaran.jenis().pemilik()) ? "." + tawaran.varian() : "")));
		Realm realm = Realm.values()[MisiGilda.PITA_MIN[tawaran.pangkat()]];
		LembarKunci.teks(row, LembarKunci.SYARAT, Component.translatable("kultivator.tempat_ekonomi.gilda.syarat", rank,
				Component.translatable("kultivator.realm." + realm.id())));
		long preview = MisiGilda.imbalanTawaran(player, tawaran);
		row.putLong(LembarKunci.GildaMisi.IMBALAN_NB, preview);
		row.putInt(LembarKunci.GildaMisi.IMBALAN_NAMA_BAIK, MisiGilda.NAMA_BAIK[tawaran.pangkat()]);
		row.putInt(LembarKunci.GildaMisi.IMBALAN_POIN, MisiGilda.POIN[tawaran.pangkat()]);
		row.putFloat(LembarKunci.GildaMisi.PELUANG_BARANG, (float) MisiGilda.PELUANG_BARANG);
		row.putInt(LembarKunci.GildaMisi.WAKTU_HARI, MisiGilda.HARI_BATAS[tawaran.pangkat()]);
		CompoundTag fake = new CompoundTag();
		fake.putInt("x", tawaran.x());
		fake.putInt("z", tawaran.z());
		LembarKunci.teks(row, LembarKunci.GildaMisi.ARAH, MisiGilda.arah(player, fake));
		row.putInt(LembarKunci.X, tawaran.x());
		row.putInt(LembarKunci.Z, tawaran.z());
		Component why = MisiGilda.alasan(player, tawaran);
		if (why != null) {
			row.putBoolean(LembarKunci.TERKUNCI, true);
			LembarKunci.teks(row, LembarKunci.ALASAN, why);
		}
		return row;
	}

	private static CompoundTag aktifTag(ServerPlayer player, CompoundTag misi) {
		CompoundTag row = new CompoundTag();
		row.putString(LembarKunci.ID, Long.toString(misi.getLongOr("id", 0L)));
		row.putString(LembarKunci.GildaMisi.PANGKAT, LembarKunci.GildaMisi.SEMUA_PANGKAT.get(Math.max(0, Math.min(5, misi.getIntOr("pangkat", 0)))));
		LembarKunci.teks(row, LembarKunci.JUDUL, MisiGilda.judul(misi));
		boolean done = misi.getBooleanOr("selesai", false);
		String jenis = misi.getStringOr("jenis", "");
		LembarKunci.teks(row, LembarKunci.TEKS, done ? Component.translatable("kultivator.tempat_ekonomi.misi.tahap.serah")
				: MisiGilda.milikSendiri(jenis) ? Component.translatable("kultivator.misi." + jenis + ".tahap." + Math.min(1, misi.getIntOr("tahap", 0)),
						MisiGilda.arah(player, misi)) : Component.translatable("kultivator.tempat_ekonomi.misi.tahap.umum", MisiGilda.arah(player, misi)));
		row.putInt(LembarKunci.TAHAP, misi.getIntOr("tahap", 0));
		row.putInt(LembarKunci.TAHAP_TOTAL, misi.getIntOr("total", 1));
		row.putLong(LembarKunci.SISA_TICK, Math.max(0L, misi.getLongOr("batas", 0L) - Barang.waktu(player.level().getServer())));
		row.putInt(LembarKunci.X, misi.getIntOr("x", 0));
		row.putInt(LembarKunci.Z, misi.getIntOr("z", 0));
		row.putBoolean(LembarKunci.GildaMisi.BISA_SERAH, done || "herba".equals(jenis));
		return row;
	}

	private static CompoundTag ujianTag(ServerPlayer player, KultivatorNpc npc) {
		CompoundTag tag = new CompoundTag();
		int star = Lencana.ujianBerikut(player);
		if (star <= 0) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.gilda.ujian.tamat"));
			tag.putBoolean(LembarKunci.BISA, false);
			return tag;
		}
		tag.putInt(LembarKunci.TARGET, star);
		ListTag syarat = new ListTag();
		boolean poin = Lencana.poin(player) >= Lencana.POIN[star - 1];
		syarat.add(syaratTag(Component.translatable("kultivator.tempat_ekonomi.gilda.ujian.poin", Lencana.POIN[star - 1]), poin));
		Realm realm = Realm.values()[UJIAN_RANAH[star]];
		boolean ranah = Barang.pita(player) >= UJIAN_RANAH[star];
		syarat.add(syaratTag(Component.translatable("kultivator.tempat_ekonomi.gilda.ujian.ranah", Component.translatable("kultivator.realm." + realm.id())),
				ranah));
		MinecraftServer server = player.level().getServer();
		boolean hariIni = EkonomiData.get(server).harian(player.getUUID(), Barang.hari(server), "ujian") == 0;
		syarat.add(syaratTag(Component.translatable("kultivator.tempat_ekonomi.gilda.ujian.sekali"), hariIni));
		tag.put(LembarKunci.SYARAT, syarat);
		long biaya = biayaUjian(star);
		tag.putLong(LembarKunci.BIAYA, biaya);
		LembarKunci.teks(tag, LembarKunci.GildaMisi.PENGUJI, npc.role() == NpcRole.PENGUJI ? npc.getDisplayName()
				: Component.translatable("kultivator.tempat_ekonomi.gilda.penguji"));
		boolean bisa = poin && ranah && hariIni && NilaiBatu.total(player) >= biaya && !UJIAN.containsKey(player.getUUID());
		tag.putBoolean(LembarKunci.BISA, bisa);
		if (!bisa) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, Component.translatable(UJIAN.containsKey(player.getUUID())
					? "kultivator.tempat_ekonomi.gilda.ujian.berjalan" : "kultivator.tempat_ekonomi.gilda.ujian.belum"));
		}
		return tag;
	}

	private static CompoundTag syaratTag(Component text, boolean ok) {
		CompoundTag row = new CompoundTag();
		LembarKunci.teks(row, LembarKunci.TEKS, text);
		row.putBoolean(LembarKunci.OK, ok);
		return row;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Actions

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null || !EkonomiConfig.get().gilda) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		ServerLevel level = (ServerLevel) player.level();
		switch (parts[0]) {
			case LembarKunci.GildaMisi.OP_TERIMA -> {
				long id = LembarKunci.argLong(parts, 1, -1L);
				for (MisiGilda.Tawaran tawaran : MisiGilda.papan(level, situs(npc), npc.blockPosition(), MisiGilda.kelas(level.getServer(), npc))) {
					if (tawaran.id() == id) {
						MisiGilda.terima(player, npc, tawaran, situs(npc), npc.town());
						SosokApi.gerak(npc, GerakId.MENULIS, 0, 40);
						return;
					}
				}
			}
			case LembarKunci.GildaMisi.OP_SERAH -> MisiGilda.serah(player, LembarKunci.argLong(parts, 1, -1L));
			case LembarKunci.GildaMisi.OP_BATAL -> MisiGilda.batal(player, LembarKunci.argLong(parts, 1, -1L));
			case LembarKunci.GildaMisi.OP_UJIAN -> ujian(player, npc);
			case LembarKunci.OP_TANDAI -> Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.tandai", LembarKunci.argInt(parts, 1, 0),
					LembarKunci.argInt(parts, 2, 0)).withStyle(ChatFormatting.AQUA));
			default -> {
			}
		}
	}

	private static void ujian(ServerPlayer player, KultivatorNpc clerk) {
		int star = Lencana.ujianBerikut(player);
		if (star <= 0 || UJIAN.containsKey(player.getUUID())) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		long hari = Barang.hari(server);
		if (Lencana.poin(player) < Lencana.POIN[star - 1] || Barang.pita(player) < UJIAN_RANAH[star] || data.harian(player.getUUID(), hari, "ujian") > 0) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.gilda.ujian.belum"), false);
			return;
		}
		long biaya = biayaUjian(star);
		if (!NilaiBatu.pay(player, biaya)) {
			Lembar.kurangUang(player, biaya);
			return;
		}
		KasKota.tambah(server, situs(clerk), biaya);
		if (dev.kultivator.sect.hall.Halls.hallOf(player.getUUID()) != null) {
			BukuKas.catat(server, player.getUUID(), LembarKunci.BukuKas.SUMBER_UJIAN, 0, -biaya, "kultivator.tempat_ekonomi.kas.catatan.ujian", star);
		}
		data.tambahHarian(player.getUUID(), hari, "ujian", 1);
		KultivatorNpc examiner = clerk;
		if (clerk.role() != NpcRole.PENGUJI) {
			examiner = HidupApi.tubuh((ServerLevel) player.level(), situs(clerk), NpcRole.PENGUJI, clerk.blockPosition().offset(2, 0, 2),
					clerk.getYRot());
		}
		if (examiner == null) {
			NilaiBatu.give(player, biaya);
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.gilda.ujian.tak_ada_penguji"), false);
			return;
		}
		Component refused = DuelChallenges.challenge(player, examiner, null, DuelRuleSet.defaults().withRealm(DuelRuleSet.RealmRule.TEKAN_RANAH));
		if (refused != null) {
			NilaiBatu.give(player, biaya);
			Lembar.pesan(player, refused, false);
			return;
		}
		UJIAN.put(player.getUUID(), new Object[] {star, examiner.getUUID()});
		SosokApi.gerak(examiner, GerakId.HORMAT, 0, 30);
		TempatLayar.tutup(player);
		Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.gilda.ujian.mulai", star, examiner.getDisplayName())
				.withStyle(ChatFormatting.GOLD));
	}

	private static void onDuel(DuelSession session, @Nullable LivingEntity winner, @Nullable LivingEntity loser) {
		for (LivingEntity side : List.of(session.a(), session.b())) {
			if (!(side instanceof ServerPlayer player)) {
				continue;
			}
			Object[] exam = UJIAN.get(player.getUUID());
			if (exam == null) {
				continue;
			}
			LivingEntity other = side == session.a() ? session.b() : session.a();
			if (other == null || !other.getUUID().equals(exam[1])) {
				continue;
			}
			UJIAN.remove(player.getUUID());
			int star = (int) exam[0];
			if (winner == player) {
				Lencana.lulusUjian(player, star);
			} else {
				Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.gilda.ujian.gagal", star).withStyle(ChatFormatting.RED));
			}
		}
	}

	private GildaKultivator() {
	}
}
