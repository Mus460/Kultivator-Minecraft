package dev.kultivator.dunia.tempat.ekonomi;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.SekteBus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Buku Kas Sekte (DESIGN 38.M.4, screen 38.U.3.6): SavedData {@code kultivator:tempat_kas} with one line per income or
 * expense of a hall owner, kept {@link #SIMPAN_HARI} world days. PK and Batu (NB) are always two separate columns (38.M.0:
 * never mixed, never converted). Writers: the 38 modules through {@link #catat} / {@code EkonomiApi.kas}, and the public
 * sect bus ({@code WORK_DONE} mine and garden; tournament prizes arrive through {@code EkonomiApi.kas} from the module
 * that pays them); 38 never edits a 36 file.
 * <p>
 * Screen {@code buku_kas}: hub SEKTE card (read only), the Record Office clerk of the capital ({@link NpcRole#MENTERI}) and
 * the stall counter anchor ({@link #bukaDiJangkar}). Without a hall the whole screen is locked with its reason.
 */
public final class BukuKas extends SavedData {

	public static final int SIMPAN_HARI = 30;
	public static final int BARIS_MAKS = 600;

	public static final Codec<BukuKas> CODEC = CompoundTag.CODEC.xmap(BukuKas::fromTag, BukuKas::toTag);
	public static final SavedDataType<BukuKas> TYPE = new SavedDataType<>(Kultivator.id("tempat_kas"), BukuKas::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	/** One ledger line: day, source id, PK, NB, note key and plain arguments. */
	public record Baris(long hari, String sumber, int pk, long nb, String kunci, List<String> isian) {
		Component catatan() {
			if (kunci == null || kunci.isEmpty()) {
				return Component.empty();
			}
			return Component.translatable(kunci, isian.toArray());
		}
	}

	private final Map<UUID, List<Baris>> buku = new HashMap<>();

	public BukuKas() {
	}

	public static BukuKas get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Bus listeners and the screen (from {@link Ekonomi#init()}). */
	static void init() {
		SekteBus.WORK_DONE.register(BukuKas::onWork);
		TempatLayar.daftarHub(LembarKunci.BukuKas.LAYAR, BukuKas::aksi, BukuKas::bangun);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Writing

	/**
	 * Writes a ledger line for hall owner {@code owner}. {@code sumber} is a {@code LembarKunci.BukuKas.SUMBER_*} id,
	 * {@code kunci} a lang key of the note (may be empty) with plain arguments.
	 */
	public static void catat(MinecraftServer server, UUID owner, String sumber, int pk, long nb, String kunci, Object... isian) {
		if (server == null || owner == null || pk == 0 && nb == 0L) {
			return;
		}
		List<String> args = new ArrayList<>();
		for (Object each : isian) {
			args.add(String.valueOf(each));
		}
		BukuKas kas = get(server);
		List<Baris> lines = kas.buku.computeIfAbsent(owner, key -> new ArrayList<>());
		long hari = Barang.hari(server);
		lines.add(new Baris(hari, sumber == null ? LembarKunci.BukuKas.SUMBER_MISI : sumber, pk, nb, kunci == null ? "" : kunci, args));
		lines.removeIf(line -> line.hari < hari - SIMPAN_HARI);
		while (lines.size() > BARIS_MAKS) {
			lines.removeFirst();
		}
		kas.setDirty();
	}

	/** The owner's lines (oldest first, a copy). */
	public static List<Baris> baris(MinecraftServer server, UUID owner) {
		return new ArrayList<>(get(server).buku.getOrDefault(owner, List.of()));
	}

	/** Net PK and NB of the owner on day {@code hari}. */
	public static long[] bersih(MinecraftServer server, UUID owner, long hari) {
		long pk = 0L;
		long nb = 0L;
		for (Baris line : get(server).buku.getOrDefault(owner, List.of())) {
			if (line.hari == hari) {
				pk += line.pk;
				nb += line.nb;
			}
		}
		return new long[] {pk, nb};
	}

	private static void onWork(MinecraftServer server, UUID owner, UUID soul, String kind, int amount) {
		if (amount <= 0) {
			return;
		}
		boolean tambang = "tambang".equals(kind);
		catat(server, owner, tambang ? LembarKunci.BukuKas.SUMBER_TAMBANG : LembarKunci.BukuKas.SUMBER_KEBUN, amount, 0L,
				"kultivator.tempat_ekonomi.kas.catatan." + (tambang ? "tambang" : "kebun"), amount);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Screen

	/** Opens the ledger at the stall counter or the Record Office desk anchor (actions allowed within reach). */
	public static void bukaDiJangkar(ServerPlayer player, BlockPos jangkar) {
		CompoundTag lembar = bangun(player, -1);
		if (lembar != null) {
			TempatLayar.bukaDi(player, LembarKunci.BukuKas.LAYAR, jangkar, lembar);
		}
	}

	/** {@code /kultivasi kas}: the hub view (read only). */
	public static void bukaHub(ServerPlayer player) {
		CompoundTag lembar = bangun(player, -1);
		if (lembar != null) {
			TempatLayar.buka(player, LembarKunci.BukuKas.LAYAR, -1, lembar);
		}
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		if (npcId >= 0) {
			Entity entity = player.level().getEntity(npcId);
			if (!(entity instanceof KultivatorNpc npc)) {
				return null;
			}
			boolean guard = player.getUUID().equals(LapakSekte.pemilikPenjaga(npc));
			if (npc.role() != NpcRole.MENTERI && !guard) {
				return null;
			}
			LapakSekte.ingatSitus(player, npc.anchorSite() != 0L ? npc.anchorSite() : npc.town());
		}
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		CompoundTag tag = new CompoundTag();
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable("kultivator.tempat_ekonomi.kas.judul"));
		tag.putString(LembarKunci.GAYA, LembarKunci.GAYA_KERTAS);
		tag.putLong(LembarKunci.UANG, dev.kultivator.dunia.kota.NilaiBatu.total(player));
		if (Halls.hallOf(owner) == null) {
			CompoundTag kunci = new CompoundTag();
			LembarKunci.teks(kunci, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.kas.tanpa_aula"));
			LembarKunci.teks(kunci, LembarKunci.SYARAT, Component.translatable("kultivator.tempat_ekonomi.kas.tanpa_aula.syarat"));
			tag.put(LembarKunci.KUNCI_LAYAR, kunci);
			return tag;
		}
		LembarKunci.teks(tag, LembarKunci.SUBJUDUL, Halls.hallOf(owner).name().isBlank()
				? Component.translatable("kultivator.tempat_ekonomi.kas.aula_tanpa_nama") : Component.literal(Halls.hallOf(owner).name()));
		long hari = Barang.hari(server);
		tag.putLong(LembarKunci.HARI, hari);
		List<Baris> lines = baris(server, owner);
		// today
		ListTag today = new ListTag();
		for (int i = lines.size() - 1; i >= 0 && today.size() < LembarKunci.DAFTAR_MAKS; i--) {
			Baris line = lines.get(i);
			if (line.hari != hari) {
				continue;
			}
			today.add(barisTag(line));
		}
		tag.put(LembarKunci.BukuKas.BARIS, today);
		long[] net = bersih(server, owner, hari);
		tag.putInt(LembarKunci.BukuKas.BERSIH_PK, (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, net[0])));
		tag.putLong(LembarKunci.BukuKas.BERSIH_NB, net[1]);
		// 7 days, oldest first
		int[] grafikPk = new int[7];
		long[] grafikNb = new long[7];
		ListTag minggu = new ListTag();
		for (int d = 0; d < 7; d++) {
			long day = hari - 6 + d;
			long[] dayNet = bersih(server, owner, day);
			grafikPk[d] = (int) dayNet[0];
			grafikNb[d] = dayNet[1];
			CompoundTag row = new CompoundTag();
			row.putLong(LembarKunci.HARI, day);
			row.putInt(LembarKunci.PK, (int) dayNet[0]);
			row.putLong(LembarKunci.NB, dayNet[1]);
			minggu.add(row);
		}
		tag.putIntArray(LembarKunci.BukuKas.GRAFIK_PK, grafikPk);
		tag.putLongArray(LembarKunci.BukuKas.GRAFIK_NB, grafikNb);
		tag.put(LembarKunci.BukuKas.MINGGU, minggu);
		// per source over 7 days
		Map<String, long[]> sums = new LinkedHashMap<>();
		for (Baris line : lines) {
			if (line.hari > hari - 7) {
				long[] sum = sums.computeIfAbsent(line.sumber, key -> new long[2]);
				sum[0] += line.pk;
				sum[1] += line.nb;
			}
		}
		ListTag sumber = new ListTag();
		for (Map.Entry<String, long[]> entry : sums.entrySet()) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.BukuKas.SUMBER, entry.getKey());
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.kas." + entry.getKey()));
			row.putInt(LembarKunci.PK, (int) entry.getValue()[0]);
			row.putLong(LembarKunci.NB, entry.getValue()[1]);
			Component cap = batasSumber(player, entry.getKey());
			if (cap != null) {
				LembarKunci.teks(row, LembarKunci.CATATAN, cap);
			}
			sumber.add(row);
		}
		tag.put(LembarKunci.BukuKas.SUMBER_DAFTAR, sumber);
		tag.put(LembarKunci.BukuKas.LAPAK, LapakSekte.lembar(player));
		tag.put(LembarKunci.BukuKas.KAFILAH, KafilahSekte.lembar(player));
		if (today.isEmpty()) {
			CompoundTag kosong = new CompoundTag();
			LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable("kultivator.tempat_ekonomi.kas.kosong"));
			tag.put(LembarKunci.KOSONG, kosong);
		}
		return tag;
	}

	private static @Nullable Component batasSumber(ServerPlayer player, String sumber) {
		int band = Barang.pita(player);
		int level = Math.max(1, Halls.level(player.getUUID()));
		return switch (sumber) {
			case LembarKunci.BukuKas.SUMBER_LAPAK -> Component.translatable("kultivator.tempat_ekonomi.kas.batas.lapak", 40L * (band + 1) * level);
			case LembarKunci.BukuKas.SUMBER_KAFILAH -> Component.translatable("kultivator.tempat_ekonomi.kas.batas.kafilah", 200L * (band + 1));
			case LembarKunci.BukuKas.SUMBER_LUMBUNG -> Component.translatable("kultivator.tempat_ekonomi.kas.batas.lumbung", 30L * (band + 1) * level);
			default -> null;
		};
	}

	private static CompoundTag barisTag(Baris line) {
		CompoundTag row = new CompoundTag();
		row.putLong(LembarKunci.HARI, line.hari);
		row.putString(LembarKunci.BukuKas.SUMBER, line.sumber);
		LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.kas." + line.sumber));
		row.putInt(LembarKunci.PK, line.pk);
		row.putLong(LembarKunci.NB, line.nb);
		if (!line.kunci.isEmpty()) {
			LembarKunci.teks(row, LembarKunci.CATATAN, line.catatan());
		}
		return row;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String verb = LembarKunci.verb(op);
		String[] parts = LembarKunci.urai(op);
		if (verb.startsWith("lapak_")) {
			LapakSekte.aksi(player, verb, parts);
		} else if (LembarKunci.BukuKas.OP_KAFILAH_KIRIM.equals(verb)) {
			KafilahSekte.aksi(player, parts);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Codec

	private static BukuKas fromTag(CompoundTag tag) {
		BukuKas kas = new BukuKas();
		CompoundTag owners = tag.getCompoundOrEmpty("buku");
		for (String key : owners.keySet()) {
			UUID owner;
			try {
				owner = UUID.fromString(key);
			} catch (IllegalArgumentException exception) {
				continue;
			}
			List<Baris> lines = new ArrayList<>();
			for (Tag each : owners.getListOrEmpty(key)) {
				if (!(each instanceof CompoundTag row)) {
					continue;
				}
				List<String> args = new ArrayList<>();
				ListTag list = row.getListOrEmpty("a");
				for (int i = 0; i < list.size(); i++) {
					args.add(list.getStringOr(i, ""));
				}
				lines.add(new Baris(row.getLongOr("h", 0L), row.getStringOr("s", LembarKunci.BukuKas.SUMBER_MISI), row.getIntOr("pk", 0),
						row.getLongOr("nb", 0L), row.getStringOr("k", ""), args));
			}
			kas.buku.put(owner, lines);
		}
		return kas;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag owners = new CompoundTag();
		for (Map.Entry<UUID, List<Baris>> entry : buku.entrySet()) {
			ListTag list = new ListTag();
			for (Baris line : entry.getValue()) {
				CompoundTag row = new CompoundTag();
				row.putLong("h", line.hari);
				row.putString("s", line.sumber);
				row.putInt("pk", line.pk);
				row.putLong("nb", line.nb);
				row.putString("k", line.kunci);
				ListTag args = new ListTag();
				for (String arg : line.isian) {
					args.add(StringTag.valueOf(arg));
				}
				row.put("a", args);
				list.add(row);
			}
			owners.put(entry.getKey().toString(), list);
		}
		tag.put("buku", owners);
		return tag;
	}
}
