package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The recurring characters of the Kota Gunung (DESIGN 38.T.6.5, chain {@code tema_gunung_tali}). Dialogs and quests name
 * them {@code tokoh:<id>} through the runtime tag {@code tokoh} in {@link KultivatorNpc#memory()} written by
 * {@link #tandai}; names {@code kultivator.kota_tema.gunung.tokoh.<id>} (+ {@code .gelar}) live in
 * {@code lang-src/tempat_tema_gunung.*}. Bodies are {@code noSave}; the anchor of each character's place re-makes and
 * re-tags them ({@link GunungAnchors}); the story calls the others ({@link #panggil}).
 * <ul>
 * <li>{@code jue_yuan} KEPALA_BIARA, Aula Dharma of the Biara Awan (anchor {@code tema_gunung/biara}), bijak; a Dao
 * Union cultivator who never fought.</li>
 * <li>{@code tie_suo} PENJAGA_LIFT, the lower station ({@code tema_gunung/lift stasiun=bawah}), sombong (stubborn and
 * proud of his cable car, in debt to the merchants).</li>
 * <li>{@code shan_ling} PEMETIK_HERBA, the cliff district ({@code tema_gunung/tokoh}), jujur; knows every niche, friend
 * of the monkeys.</li>
 * <li>{@code lao_dan} KULI_GUNUNG, the Pos Kuli ({@code tema_gunung/kuli jenis=pos}), bijak; old porter, iron legs.</li>
 * <li>{@code pei_wenzhi} PEZIARAH, a young scholar climbing for his late mother (side quest {@code tema_ziarah_ibu}),
 * sombong then repentant; waits at the Kuil Kaki while his quest is open.</li>
 * <li>{@code jing_yue} BIKSU_GUNUNG, keeper of the Aula Emas ({@code tema_gunung/aula_emas}), jujur.</li>
 * <li>{@code gou_tie} "Cakar Besi", leader of the wild herb hunters (MAKELAR_GELAP body, tier 3), serakah; called by
 * stages 3-5.</li>
 * <li>{@code yao_po} TABIB, Nenek Yao of the Aula Herba ({@code tema_gunung/aula_herba}), ramah; the appraiser.</li>
 * </ul>
 */
public final class GunungTokoh {

	/**
	 * One character.
	 *
	 * @param usia    age band of {@link Sosok}
	 * @param watak   temperament index of {@code Tokoh.WATAK} (0 ramah 1 curiga 2 serakah 3 bijak 4 penakut 5 sombong 6 jujur 7 licik)
	 * @param jangkar the anchor {@code what} that brings the body ("" = called by the story only)
	 */
	public record Data(String id, NpcRole peran, boolean perempuan, int usia, int watak, String jangkar) {

		public String kunciNama() {
			return "kultivator.kota_tema.gunung.tokoh." + id;
		}

		public String kunciGelar() {
			return "kultivator.kota_tema.gunung.tokoh." + id + ".gelar";
		}
	}

	public static final String JUE_YUAN = "jue_yuan";
	public static final String TIE_SUO = "tie_suo";
	public static final String SHAN_LING = "shan_ling";
	public static final String LAO_DAN = "lao_dan";
	public static final String PEI_WENZHI = "pei_wenzhi";
	public static final String JING_YUE = "jing_yue";
	public static final String GOU_TIE = "gou_tie";
	public static final String YAO_PO = "yao_po";

	private static final Map<String, Data> SEMUA = new LinkedHashMap<>();

	static {
		add(JUE_YUAN, NpcRole.KEPALA_BIARA, false, Sosok.TUA, 3, GunungAnchors.BIARA);
		add(TIE_SUO, NpcRole.PENJAGA_LIFT, false, Sosok.TUA, 5, GunungAnchors.LIFT);
		add(SHAN_LING, NpcRole.PEMETIK_HERBA, true, Sosok.MUDA, 6, GunungAnchors.TOKOH);
		add(LAO_DAN, NpcRole.KULI_GUNUNG, false, Sosok.TUA, 3, GunungAnchors.KULI);
		add(PEI_WENZHI, NpcRole.PEZIARAH, false, Sosok.MUDA, 5, "");
		add(JING_YUE, NpcRole.BIKSU_GUNUNG, true, Sosok.DEWASA, 6, GunungAnchors.AULA_EMAS);
		add(GOU_TIE, NpcRole.MAKELAR_GELAP, false, Sosok.DEWASA, 2, "");
		add(YAO_PO, NpcRole.TABIB, true, Sosok.TUA, 0, GunungAnchors.AULA_HERBA);
	}

	private static void add(String id, NpcRole peran, boolean perempuan, int usia, int watak, String jangkar) {
		SEMUA.put(id, new Data(id, peran, perempuan, usia, watak, jangkar));
	}

	public static @Nullable Data byId(@Nullable String id) {
		return id == null ? null : SEMUA.get(id);
	}

	public static List<Data> semua() {
		return List.copyOf(SEMUA.values());
	}

	/** The character id of a body ("" for anybody else). */
	public static String dari(@Nullable KultivatorNpc npc) {
		if (npc == null) {
			return "";
		}
		String id = npc.memory().getStringOr("tokoh", "");
		return SEMUA.containsKey(id) ? id : "";
	}

	public static boolean adalah(@Nullable KultivatorNpc npc, String id) {
		return npc != null && id.equals(npc.memory().getStringOr("tokoh", ""));
	}

	/** Makes {@code npc} the character {@code id}: story tag, temperament, sex and age, the name plate "Name · Title". */
	public static void tandai(KultivatorNpc npc, String id) {
		Data data = byId(id);
		if (data == null) {
			return;
		}
		npc.memory().putString("tokoh", id);
		npc.memory().putString("tema", GunungData.TEMA);
		npc.memory().putInt("watak", data.watak());
		npc.setTemperament(data.watak());
		Sosok sosok = SosokApi.sosok(npc);
		Sosok want = sosok.withKelamin(data.perempuan() ? Sosok.PEREMPUAN : Sosok.LAKI).withUsia(data.usia());
		if (!want.equals(sosok)) {
			SosokApi.setSosok(npc, want);
		}
		npc.setPlate(Component.translatable("kultivator.kota.papan_nama", Component.translatable(data.kunciNama()),
				Component.translatable(data.kunciGelar())));
	}

	/** Spawns the character at {@code pos} for the town (reputation id {@code kotaId}) and tags it; null when capped. */
	public static @Nullable KultivatorNpc panggil(ServerLevel level, long kotaId, String id, BlockPos pos, float yaw) {
		Data data = byId(id);
		if (data == null) {
			return null;
		}
		int look = id.hashCode() * 0x2F6B ^ 0x51ED;
		KultivatorNpc npc = HidupApi.tubuhStaf(level, kotaId, data.peran(), pos, yaw, look);
		if (npc != null) {
			tandai(npc, id);
		}
		return npc;
	}

	private GunungTokoh() {
	}
}
