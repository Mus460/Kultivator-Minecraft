package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The recurring characters of DESIGN 38.S.2 and the temperament table of generated village characters.
 *
 * <p>A body becomes a character through {@link #tandai} (the runtime tag {@code tokoh} in {@link KultivatorNpc#memory()}:
 * bodies are {@code noSave} and re-tagged whenever their owner spawns them) or {@link #pasang} (the owner names the
 * service lot, e.g. {@code penginapan}, and the character serving it is tagged). The first capital whose arc starts keeps
 * the fixed names of the table ({@code kultivator.cerita.tokoh.<id>}); every other capital gives the same characters a
 * generated name of the same sex ({@link KotaNames#person(int, boolean)}) with the same temperament and lines.
 *
 * <p>Generated characters (village heads, clan patriarchs, young masters, hermits, remnant souls, sect elders) carry one
 * of eight temperaments ({@link #WATAK}); dialogs branch on it with the condition {@code watak <name>}.
 */
public final class Tokoh {

	/** The eight temperaments of 38.S.2 in index order. */
	public static final List<String> WATAK = List.of("ramah", "curiga", "serakah", "bijak", "penakut", "sombong", "jujur", "licik");

	/**
	 * One recurring character.
	 *
	 * @param layanan  the service lot whose body is this character ({@link #pasang})
	 * @param suara    body voice index 0-5 ({@code KultivatorNpc.VOICES}: m1 m2 m3 f1 f2 f3)
	 * @param watak    temperament index of {@link #WATAK}
	 */
	public record Data(String id, NpcRole peran, String layanan, int suara, boolean perempuan, int watak) {

		public String kunciNama() {
			return "kultivator.cerita.tokoh." + id;
		}

		public String kunciGelar() {
			return "kultivator.cerita.tokoh." + id + ".gelar";
		}
	}

	/** Where a character's body was last seen (for {@code uji cerita tokoh}). */
	public record Jejak(ResourceKey<Level> dimensi, BlockPos pos, long tick, long situs) {
	}

	private static final Map<String, Data> SEMUA = new LinkedHashMap<>();
	private static final Map<String, Data> PER_LAYANAN = new LinkedHashMap<>();
	private static final Map<String, Jejak> JEJAK = new LinkedHashMap<>();

	static {
		add("yan_wushuang", NpcRole.PANGERAN_KOTA, "istana", 0, false, 3);
		add("yan_ruyi", NpcRole.PANGERAN_KOTA, "istana_pewaris", 4, true, 6);
		add("gu_lianshan", NpcRole.MENTERI, "istana_menteri", 1, false, 0);
		add("mo_ying", NpcRole.MENTERI, "istana_ajudan", 2, false, 7);
		add("bao_qing", NpcRole.HAKIM, "yamen", 0, false, 6);
		add("tie_hu", NpcRole.KAPTEN_PENJAGA, "gerbang", 0, false, 6);
		add("qian_duoduo", NpcRole.BANKIR, "rumah_uang", 5, true, 2);
		add("lao_shi", NpcRole.BANDAR_BATU, "rumah_judi", 2, false, 7);
		add("ou_ye", NpcRole.EMPU, "gilda_tempa", 0, false, 6);
		add("dan_chen", NpcRole.KETUA_GILDA, "gilda_alkemis", 0, false, 5);
		add("lin_fei", NpcRole.KETUA_GILDA, "gilda_kultivator", 4, true, 6);
		add("xiao_mei", NpcRole.PETUGAS_GILDA, "gilda_kultivator_meja", 3, true, 0);
		add("feng_ao", NpcRole.PENGUJI, "gilda_kultivator_uji", 2, false, 5);
		add("tuan_wen", NpcRole.PUSTAKAWAN, "perpustakaan", 0, false, 3);
		add("jing_xin", NpcRole.RAHIB, "kuil", 0, false, 3);
		add("bibi_hong", NpcRole.PEMILIK_PENGINAPAN, "penginapan", 3, true, 0);
		add("gendut_zhu", NpcRole.KOKI, "rumah_makan", 1, false, 4);
		add("ma_laoliu", NpcRole.PEDAGANG_BINATANG, "pasar_binatang", 2, false, 0);
		add("yun_fei", NpcRole.NAKHODA, "dermaga", 1, false, 0);
		add("kakek_lumpur", NpcRole.PENGEMIS, "kumuh", 0, false, 3);
		add("dou", NpcRole.ANAK, "kumuh_anak", 3, true, 0);
		add("lao_qiao", NpcRole.PEDAGANG_KELILING, "jalan", 1, false, 6);
		add("ah_lun", NpcRole.MURID_SEKTE_LUAR, "kompleks_ah_lun", 1, false, 6);
	}

	private static void add(String id, NpcRole peran, String layanan, int suara, boolean perempuan, int watak) {
		Data data = new Data(id, peran, layanan, suara, perempuan, watak);
		SEMUA.put(id, data);
		PER_LAYANAN.put(layanan, data);
	}

	public static @Nullable Data byId(String id) {
		return id == null ? null : SEMUA.get(id);
	}

	/** Every character id in table order. */
	public static List<String> ids() {
		return List.copyOf(SEMUA.keySet());
	}

	/** The character of service lot {@code layanan}, or null. */
	public static @Nullable Data diLayanan(String layanan) {
		return layanan == null ? null : PER_LAYANAN.get(layanan);
	}

	/** The character id of a body ("" when it is nobody special). */
	public static String dari(KultivatorNpc npc) {
		return npc.memory().getStringOr("tokoh", "");
	}

	/**
	 * Makes {@code npc} the character {@code id}: tags it, gives it the character's name plate for its place and records
	 * where it is. Called by the module that spawned the body (38KS service lots, 38DS compounds, 38W road posts, tests).
	 */
	public static void tandai(KultivatorNpc npc, String id) {
		Data data = byId(id);
		if (data == null) {
			return;
		}
		npc.memory().putString("tokoh", id);
		npc.memory().putInt("watak", data.watak);
		MinecraftServer server = npc.level().getServer();
		npc.setPlate(Component.translatable("kultivator.kota.papan_nama", nama(server, id, npc.town()), gelar(id)));
		lihat(npc);
	}

	/** {@link #tandai} for the character serving {@code layanan} at place {@code situs}; returns its id or null. */
	public static @Nullable String pasang(KultivatorNpc npc, long situs, String layanan) {
		Data data = diLayanan(layanan);
		if (data == null) {
			return null;
		}
		if (npc.town() == 0L) {
			npc.setTown(situs);
		}
		tandai(npc, data.id);
		return data.id;
	}

	/** The character's name at place {@code situs}: fixed in the first capital (or anywhere before one exists). */
	public static MutableComponent nama(@Nullable MinecraftServer server, String id, long situs) {
		Data data = byId(id);
		if (data == null) {
			return Component.literal(id);
		}
		long first = server == null ? 0L : CeritaData.get(server).firstCapital();
		if (first == 0L || situs == 0L || situs == first || fixedOutside(data)) {
			return Component.translatable(data.kunciNama());
		}
		int seed = (int) (situs ^ (situs >>> 32)) ^ id.hashCode() * 0x45D9F3B;
		return KotaNames.person(seed, data.perempuan);
	}

	/** Characters who travel the whole world keep their name everywhere (the peddler, Ah Lun, the hidden beggar master). */
	private static boolean fixedOutside(Data data) {
		return "lao_qiao".equals(data.id) || "ah_lun".equals(data.id) || "feng_ao".equals(data.id);
	}

	public static MutableComponent gelar(String id) {
		Data data = byId(id);
		return data == null ? Component.empty() : Component.translatable(data.kunciGelar());
	}

	/** Temperament index of a body: its character's, the one its owner set ({@code memory watak}) or one from its look. */
	public static int watak(KultivatorNpc npc) {
		if (npc.memory().contains("watak")) {
			return Math.floorMod(npc.memory().getIntOr("watak", 0), WATAK.size());
		}
		return Math.floorMod(npc.look() * 0x2545F491 >>> 7, WATAK.size());
	}

	/** Records where a character's body is (called on tag and on every talk). */
	public static void lihat(KultivatorNpc npc) {
		String id = dari(npc);
		if (!id.isEmpty()) {
			JEJAK.put(id, new Jejak(npc.level().dimension(), npc.blockPosition(), npc.level().getGameTime(), npc.town()));
		}
	}

	public static @Nullable Jejak jejak(String id) {
		return JEJAK.get(id);
	}

	static void clear() {
		JEJAK.clear();
	}

	private Tokoh() {
	}
}
