package dev.kultivator.dunia.tempat.tema.iblis;

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
 * The recurring characters of the Kota Iblis (DESIGN 38.T.7.11, chain {@code tema_iblis_rantai}). Dialogs and quests
 * name them {@code tokoh:<id>}; the runtime tag {@code tokoh} in {@link KultivatorNpc#memory()} written by
 * {@link #tandai} is all the 38ST engine needs. Names and titles: {@code kultivator.kota_tema.iblis.tokoh.<id>} and
 * {@code .gelar} (lang fragment {@code tempat_tema_iblis}). Bodies are {@code noSave}; the anchor
 * {@code kota_tema/iblis_tokoh} (data {@code tokoh}) of each character's lot re-makes them ({@link IblisAnchors}),
 * the hunters' camp brings Han Qingshuang ({@code kota_tema/pemburu} with {@code ketua}).
 * <ul>
 * <li>{@code xue_yao} PENGUASA_IBLIS, Istana Darah, cold and deadly polite (voice boss line {@code tema.iblis.lord}).</li>
 * <li>{@code tu_gang} JAGAL_ARENA "Si Jagal", the arena master: rough, fair by his own rules (m1).</li>
 * <li>{@code bai_she} MAKELAR_GELAP Nyonya Bai She, snake-sweet, owner of the Shadow Oil ring (f3).</li>
 * <li>{@code mo_wen} PENJAGA_KITAB_TERLARANG Si Buta Mo Wen, old, blind, truly neutral (elder).</li>
 * <li>{@code ye_wuhen} WARGA_IBLIS, a young demonic cultivator who wants out; taken from a village as a child (m3).</li>
 * <li>{@code a_fu} TAWANAN, a brave village girl who keeps trying to escape (f1).</li>
 * <li>{@code han_qingshuang} PEMBURU_LURUS, core disciple of the nearest righteous sect; hard and distrustful (f2).</li>
 * <li>{@code hong_pao} PEMBUNUH_BAYARAN, Bai She's courier "the man in the red robe" of the harbour and canal quests (m3).</li>
 * <li>{@code lao_kuang} GLADIATOR_IBLIS, an old gladiator who wants an honourable death or a quiet end (side quest).</li>
 * </ul>
 */
public final class TokohIblis {

	/**
	 * One character.
	 *
	 * @param usia  age band of {@link Sosok}
	 * @param watak temperament index of {@code Tokoh.WATAK} (ramah, curiga, serakah, bijak, penakut, sombong, jujur, licik)
	 */
	public record Data(String id, NpcRole peran, boolean perempuan, int usia, int watak) {

		public String kunciNama() {
			return "kultivator.kota_tema.iblis.tokoh." + id;
		}

		public String kunciGelar() {
			return "kultivator.kota_tema.iblis.tokoh." + id + ".gelar";
		}
	}

	public static final String XUE_YAO = "xue_yao";
	public static final String TU_GANG = "tu_gang";
	public static final String BAI_SHE = "bai_she";
	public static final String MO_WEN = "mo_wen";
	public static final String YE_WUHEN = "ye_wuhen";
	public static final String A_FU = "a_fu";
	public static final String HAN_QINGSHUANG = "han_qingshuang";
	public static final String HONG_PAO = "hong_pao";
	public static final String LAO_KUANG = "lao_kuang";

	private static final Map<String, Data> SEMUA = new LinkedHashMap<>();

	static {
		add(XUE_YAO, NpcRole.PENGUASA_IBLIS, true, Sosok.DEWASA, 5);
		add(TU_GANG, NpcRole.JAGAL_ARENA, false, Sosok.DEWASA, 6);
		add(BAI_SHE, NpcRole.MAKELAR_GELAP, true, Sosok.DEWASA, 7);
		add(MO_WEN, NpcRole.PENJAGA_KITAB_TERLARANG, false, Sosok.TUA, 3);
		add(YE_WUHEN, NpcRole.WARGA_IBLIS, false, Sosok.MUDA, 6);
		add(A_FU, NpcRole.TAWANAN, true, Sosok.ANAK, 0);
		add(HAN_QINGSHUANG, NpcRole.PEMBURU_LURUS, true, Sosok.MUDA, 1);
		add(HONG_PAO, NpcRole.PEMBUNUH_BAYARAN, false, Sosok.DEWASA, 7);
		add(LAO_KUANG, NpcRole.GLADIATOR_IBLIS, false, Sosok.TUA, 5);
	}

	private static void add(String id, NpcRole peran, boolean perempuan, int usia, int watak) {
		SEMUA.put(id, new Data(id, peran, perempuan, usia, watak));
	}

	private TokohIblis() {
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

	/** The fixed display name of a character. */
	public static Component nama(String id) {
		Data d = byId(id);
		return d == null ? Component.literal(id) : Component.translatable(d.kunciNama());
	}

	/** Tags {@code npc} as character {@code id}: the runtime tag, temperament, sex and age, the name plate. */
	public static void tandai(KultivatorNpc npc, String id) {
		Data data = byId(id);
		if (data == null) {
			return;
		}
		npc.memory().putString("tokoh", id);
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

	/** Spawns the character's body for town {@code situs} and tags it; null when the NPC cap is full. */
	public static @Nullable KultivatorNpc panggil(ServerLevel level, long situs, String id, BlockPos pos, float yaw) {
		Data data = byId(id);
		if (data == null) {
			return null;
		}
		int look = id.hashCode() * 0x45D9F3B ^ 0x1B1A20;
		KultivatorNpc npc = HidupApi.tubuhStaf(level, situs, data.peran(), pos, yaw, look);
		if (npc != null) {
			tandai(npc, id);
		}
		return npc;
	}
}
