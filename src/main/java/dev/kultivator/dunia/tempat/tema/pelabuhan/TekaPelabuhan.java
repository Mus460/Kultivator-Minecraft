package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.cerita.BarangCerita;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two puzzle screens of the chain {@code tema_pelabuhan_pelita} (DESIGN 38.T.3.5, 38.T.13.3):
 * <ul>
 * <li>{@code tema_dokumen} (Syahbandar Lin Haichao, stage {@code catatan}; keys {@link TemaLayar.Dokumen}): three cargo
 * manifests of the ships lost in the fog and the customs ledger. One manifest is forged: its seal number is not in the
 * ledger and the cargo weighs more than its hold allows; the true cargo was Minyak Bayangan, the shadow oil that
 * smothers spirit lamps. The papers are made per player (names, seals, weights from a hash). Three tries; after the third
 * wrong pick Lin points out the forgery herself (the chain goes on, never a dead end).</li>
 * <li>{@code tema_racik} (Mbah Lunas, any time she is asked for lens glass; keys {@link TemaLayar.Racik}): sea-salt glass
 * from 4 sand and 1 Ikan Roh in three steps (fire, mix, polish). Two right choices of three give one Kaca Garam Laut,
 * three give two; the ingredients are used when the fire is lit.</li>
 * </ul>
 */
final class TekaPelabuhan {

	public static final int PERCOBAAN = 3;
	public static final int PASIR = 4;

	/** Per-player state of the two puzzles (memory; a reload restarts the puzzle, the quest stage stays). */
	private static final class Teka {
		int percobaan = PERCOBAAN;
		int langkah = 1;
		int mutu;
		float panas;
		boolean menyala;
	}

	private static final Map<UUID, Teka> TEKA = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TemaLayar.daftar(TemaLayar.Dokumen.LAYAR, TekaPelabuhan::bangunDokumen, TekaPelabuhan::aksiDokumen);
		TemaLayar.daftar(TemaLayar.Racik.LAYAR, TekaPelabuhan::bangunRacik, TekaPelabuhan::aksiRacik);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TEKA.remove(handler.getPlayer().getUUID()));
	}

	private static Teka teka(ServerPlayer player) {
		return TEKA.computeIfAbsent(player.getUUID(), k -> new Teka());
	}

	// ================================================================== tema_dokumen

	/** Which of the three manifests is the forged one for this player (0..2). */
	static int palsu(ServerPlayer player) {
		return (int) Math.floorMod(PelabuhanKota.hash(player.getUUID().getMostSignificantBits(), player.getUUID().getLeastSignificantBits(), 380408L), 3L);
	}

	private static @Nullable CompoundTag bangunDokumen(ServerPlayer player, int npcId) {
		KultivatorNpc npc = PelabuhanLayar.npc(player, npcId);
		if (npc == null || !PelabuhanTokoh.adalah(npc, PelabuhanTokoh.LIN_HAICHAO)) {
			return null;
		}
		if (!QuestTracker.aktif(player, PelabuhanCerita.QUEST) || !"catatan".equals(QuestTracker.simpul(player, PelabuhanCerita.QUEST))) {
			return null;
		}
		CompoundTag tag = PelabuhanLayar.kepala(player, "kultivator.kota_tema.pelabuhan.layar.dokumen", LembarKunci.GAYA_KERTAS, npc);
		long h = PelabuhanKota.hash(player.getUUID().getMostSignificantBits(), 7L, 380409L);
		int fake = palsu(player);
		ListTag surat = new ListTag();
		ListTag catatan = new ListTag();
		for (int i = 0; i < 3; i++) {
			int ship = (int) Math.floorMod(h + i * 5L, 12L);
			int seal = 1000 + (int) Math.floorMod(h >>> (i * 8), 8000L);
			int weight = 120 + (int) Math.floorMod(h >>> (i * 5 + 3), 60L) * 5;
			int day = 3 + i * 4 + (int) Math.floorMod(h >>> (i * 3), 3L);
			int cargo = (int) Math.floorMod(h + i * 3L, 6L);
			CompoundTag s = new CompoundTag();
			s.putString(LembarKunci.ID, Integer.toString(i));
			LembarKunci.teks(s, LembarKunci.JUDUL, Component.translatable("kultivator.kota_tema.pelabuhan.dokumen.judul",
					Component.translatable("kultivator.kota_tema.pelabuhan.kapal.nama." + ship)));
			ListTag baris = new ListTag();
			int shownSeal = i == fake ? seal + 37 : seal;
			int shownWeight = i == fake ? weight + 85 : weight;
			baris.add(baris("kapal", Component.translatable("kultivator.kota_tema.pelabuhan.kapal.nama." + ship)));
			baris.add(baris("muatan", Component.translatable("kultivator.kota_tema.pelabuhan.dokumen.muatan." + (i == fake ? "lampu" : Integer.toString(cargo)))));
			baris.add(baris("berat", Component.translatable("kultivator.kota_tema.pelabuhan.dokumen.dan", shownWeight)));
			baris.add(baris("segel", Component.literal("No. " + shownSeal)));
			baris.add(baris("tanggal", Component.translatable("kultivator.kota_tema.pelabuhan.dokumen.hari", day)));
			s.put(TemaLayar.Dokumen.BARIS, baris);
			surat.add(s);
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, TemaLayar.Dokumen.LABEL, Component.translatable("kultivator.kota_tema.pelabuhan.kapal.nama." + ship));
			LembarKunci.teks(row, TemaLayar.Dokumen.ISI, Component.translatable("kultivator.kota_tema.pelabuhan.dokumen.catatan_isi", seal,
					Component.translatable("kultivator.kota_tema.pelabuhan.dokumen.ruang." + i), weight + 20));
			catatan.add(row);
		}
		tag.put(TemaLayar.Dokumen.SURAT, surat);
		tag.put("catatan", catatan);
		tag.putInt(TemaLayar.Dokumen.PERCOBAAN, teka(player).percobaan);
		return tag;
	}

	private static CompoundTag baris(String label, Component isi) {
		CompoundTag b = new CompoundTag();
		LembarKunci.teks(b, TemaLayar.Dokumen.LABEL, Component.translatable("kultivator.kota_tema.pelabuhan.dokumen.label." + label));
		LembarKunci.teks(b, TemaLayar.Dokumen.ISI, isi);
		return b;
	}

	private static void aksiDokumen(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		if (!TemaLayar.Dokumen.OP_PALSU.equals(parts[0])) {
			return;
		}
		if (!QuestTracker.aktif(player, PelabuhanCerita.QUEST) || !"catatan".equals(QuestTracker.simpul(player, PelabuhanCerita.QUEST))) {
			return;
		}
		Teka t = teka(player);
		int pick = LembarKunci.argInt(parts, 1, -1);
		if (pick == palsu(player)) {
			player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
			PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.dokumen.benar"), true);
			TEKA.remove(player.getUUID());
			CeritaApi.peristiwa(player, "pelabuhan_dokumen", "benar");
			return;
		}
		t.percobaan--;
		CeritaApi.peristiwa(player, "pelabuhan_dokumen", "salah");
		if (t.percobaan <= 0) {
			PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.dokumen.ditunjukkan"), true);
			TEKA.remove(player.getUUID());
			CeritaApi.peristiwa(player, "pelabuhan_dokumen", "benar");
		} else {
			PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.dokumen.salah", t.percobaan), false);
		}
	}

	// ================================================================== tema_racik

	private static final List<List<String>> PILIHAN = List.of(
			List.of("bara_merah", "bara_putih", "bara_biru"),
			List.of("tumbuk_kasar", "tumbuk_halus", "sisik_ikan"),
			List.of("poles_pasir", "poles_sutra", "poles_minyak"));
	private static final List<String> BENAR = List.of("bara_putih", "sisik_ikan", "poles_sutra");

	private static @Nullable CompoundTag bangunRacik(ServerPlayer player, int npcId) {
		KultivatorNpc npc = PelabuhanLayar.npc(player, npcId);
		if (npc == null || !PelabuhanTokoh.adalah(npc, PelabuhanTokoh.GONG_SHUMU)) {
			return null;
		}
		Teka t = teka(player);
		CompoundTag tag = PelabuhanLayar.kepala(player, "kultivator.kota_tema.pelabuhan.layar.racik", LembarKunci.GAYA_KAYU, npc);
		tag.putInt(TemaLayar.Racik.LANGKAH, t.langkah);
		tag.putInt(LembarKunci.TAHAP_TOTAL, 3);
		ListTag bahan = new ListTag();
		bahan.add(bahan(player, "pasir", new ItemStack(Items.SAND), PASIR, "minecraft:sand"));
		bahan.add(bahan(player, "ikan_roh", new ItemStack(TemaItems.IKAN_ROH), 1, "kultivator:tema/ikan_roh"));
		tag.put(TemaLayar.Racik.BAHAN, bahan);
		ListTag pilihan = new ListTag();
		for (String id : PILIHAN.get(Math.max(0, Math.min(2, t.langkah - 1)))) {
			CompoundTag p = new CompoundTag();
			p.putString(LembarKunci.ID, id);
			LembarKunci.teks(p, LembarKunci.TEKS, Component.translatable("kultivator.kota_tema.pelabuhan.racik." + id));
			pilihan.add(p);
		}
		tag.put(TemaLayar.Racik.PILIHAN, pilihan);
		tag.putFloat(TemaLayar.Racik.PANAS, t.panas);
		tag.putInt(LembarKunci.MUTU, t.menyala ? t.mutu : -1);
		LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_tema.pelabuhan.racik.petunjuk." + t.langkah));
		return tag;
	}

	private static CompoundTag bahan(ServerPlayer player, String id, ItemStack shown, int need, String key) {
		CompoundTag b = new CompoundTag();
		b.putString(LembarKunci.ID, id);
		LembarKunci.barang(b, shown, player.level().registryAccess());
		b.putInt("punya", BarangCerita.hitung(player, key));
		b.putInt("perlu", need);
		return b;
	}

	private static void aksiRacik(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = PelabuhanLayar.npc(player, npcId);
		if (npc == null || !PelabuhanTokoh.adalah(npc, PelabuhanTokoh.GONG_SHUMU)) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		Teka t = teka(player);
		if (TemaLayar.Racik.OP_ULANG.equals(parts[0])) {
			TEKA.remove(player.getUUID());
			return;
		}
		if (!TemaLayar.Racik.OP_PILIH.equals(parts[0])) {
			return;
		}
		String pick = LembarKunci.arg(parts, 1);
		int step = Math.max(1, Math.min(3, t.langkah));
		if (!PILIHAN.get(step - 1).contains(pick)) {
			return;
		}
		if (step == 1 && !t.menyala) {
			if (BarangCerita.hitung(player, "minecraft:sand") < PASIR || BarangCerita.hitung(player, "kultivator:tema/ikan_roh") < 1) {
				PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.racik.kurang"), false);
				return;
			}
			BarangCerita.ambil(player, "minecraft:sand", PASIR);
			BarangCerita.ambil(player, "kultivator:tema/ikan_roh", 1);
			t.menyala = true;
			player.level().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.8F);
		}
		boolean right = BENAR.get(step - 1).equals(pick);
		if (right) {
			t.mutu++;
		}
		if (step == 1) {
			t.panas = switch (pick) {
				case "bara_putih" -> 0.92F;
				case "bara_biru" -> 0.7F;
				default -> 0.4F;
			};
		}
		player.level().playSound(null, player.blockPosition(), right ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 0.8F,
				right ? 1.2F : 0.7F);
		if (step < 3) {
			t.langkah = step + 1;
			PelabuhanLayar.pesan(player, Component.translatable(right ? "kultivator.kota_tema.pelabuhan.racik.baik" : "kultivator.kota_tema.pelabuhan.racik.buruk"), right);
			return;
		}
		int glass = t.mutu >= 3 ? 2 : t.mutu >= 2 ? 1 : 0;
		TEKA.remove(player.getUUID());
		if (glass == 0) {
			PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.racik.gagal"), false);
			return;
		}
		PelabuhanKota.beri(player, new ItemStack(TemaItems.KACA_GARAM_LAUT, glass));
		CeritaApi.peristiwa(player, "pelabuhan_racik", "lensa");
		player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
		PelabuhanLayar.pesan(player, Component.translatable("kultivator.kota_tema.pelabuhan.racik.jadi", glass), true);
	}

	private TekaPelabuhan() {
	}
}
