package dev.kultivator.dunia.tempat.tema.air;

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
 * The seven recurring characters of the Kota Air (DESIGN 38.T.5.5), addressed by the dialogs and quests as
 * {@code tokoh:<id>} through the runtime tag {@code tokoh} of {@link KultivatorNpc#memory()} ({@link #tandai}), like the
 * oasis characters (no entry in 38ST's capital table needed). Bodies are {@code noSave}; the anchor of each character's
 * place re-makes and re-tags them ({@link AirAnchors}); Xiu Niang and Lao Shu are called by the story and the Tikus Air
 * events only. Names: {@code kultivator.kota_tema.air.tokoh.<id>} and {@code .gelar}.
 * <ul>
 * <li>{@code su_jin} Nyonya Su Jin, NYONYA_SUTRA, Rumah Sutra Su (anchor {@code sutra}), voice f3, bijak.</li>
 * <li>{@code su_jiner} Su Jin'er, PENENUN_SUTRA, her daughter, the weaving hall (anchor {@code sutra}, second body), f1, jujur.</li>
 * <li>{@code lu_yuqing} Lu Yuqing, AHLI_TEH, Paviliun Teh Awan ({@code teh_awan}), m1, bijak.</li>
 * <li>{@code bibi_chuan} Bibi Chuan, PENDAYUNG, boat hire ({@code sewa_perahu}), the tour guide, f1, ramah.</li>
 * <li>{@code wen_shi} Sastrawan Wen Shi, SASTRAWAN, Taman Sang Sastrawan ({@code taman}), m2, jujur.</li>
 * <li>{@code xiu_niang} Xiu Niang, SISA_JIWA, the drowned weaver; called on lantern nights of chain stage 4-6, f2.</li>
 * <li>{@code lao_shu} Lao Shu "Tikus Tua", MAKELAR_GELAP, leader of the Tikus Air; chain stage 2-3, m3, licik.</li>
 * </ul>
 */
public final class AirTokoh {

	/**
	 * One character.
	 *
	 * @param suara   voice index ({@code KultivatorNpc.VOICES}: 0 m1, 1 m2, 2 m3, 3 f1, 4 f2, 5 f3)
	 * @param usia    age band of {@link Sosok}
	 * @param watak   temperament index of {@code Tokoh.WATAK}
	 * @param jangkar the anchor what that brings the body ("" = called by the story only)
	 */
	public record Data(String id, NpcRole peran, int suara, boolean perempuan, int usia, int watak, String jangkar) {

		public String kunciNama() {
			return "kultivator.kota_tema.air.tokoh." + id;
		}

		public String kunciGelar() {
			return "kultivator.kota_tema.air.tokoh." + id + ".gelar";
		}
	}

	public static final String SU_JIN = "su_jin";
	public static final String SU_JINER = "su_jiner";
	public static final String LU_YUQING = "lu_yuqing";
	public static final String BIBI_CHUAN = "bibi_chuan";
	public static final String WEN_SHI = "wen_shi";
	public static final String XIU_NIANG = "xiu_niang";
	public static final String LAO_SHU = "lao_shu";

	private static final Map<String, Data> SEMUA = new LinkedHashMap<>();

	static {
		add(SU_JIN, NpcRole.NYONYA_SUTRA, 5, true, Sosok.DEWASA, 3, AirAnchors.SUTRA);
		add(SU_JINER, NpcRole.PENENUN_SUTRA, 3, true, Sosok.MUDA, 6, AirAnchors.SUTRA);
		add(LU_YUQING, NpcRole.AHLI_TEH, 0, false, Sosok.DEWASA, 3, AirAnchors.TEH_AWAN);
		add(BIBI_CHUAN, NpcRole.PENDAYUNG, 3, true, Sosok.TUA, 0, AirAnchors.SEWA_PERAHU);
		add(WEN_SHI, NpcRole.SASTRAWAN, 1, false, Sosok.TUA, 6, AirAnchors.TAMAN);
		add(XIU_NIANG, NpcRole.SISA_JIWA, 4, true, Sosok.MUDA, 6, "");
		add(LAO_SHU, NpcRole.MAKELAR_GELAP, 2, false, Sosok.TUA, 7, "");
	}

	private static void add(String id, NpcRole peran, int suara, boolean perempuan, int usia, int watak, String jangkar) {
		SEMUA.put(id, new Data(id, peran, suara, perempuan, usia, watak, jangkar));
	}

	public static @Nullable Data byId(@Nullable String id) {
		return id == null ? null : SEMUA.get(id);
	}

	public static List<Data> semua() {
		return List.copyOf(SEMUA.values());
	}

	/** The character id of a body ("" when it is nobody of this theme). */
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

	/** Makes {@code npc} the character {@code id}: tag, temperament, sex and age of the look, name plate. */
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

	/** Spawns the character's body at {@code pos} for town {@code situs} and tags it; null when a cap says no. */
	public static @Nullable KultivatorNpc panggil(ServerLevel level, long situs, String id, BlockPos pos, float yaw) {
		Data data = byId(id);
		if (data == null) {
			return null;
		}
		KultivatorNpc npc = HidupApi.tubuh(level, situs, data.peran(), pos, yaw);
		if (npc != null) {
			tandai(npc, id);
		}
		return npc;
	}

	/** The display name of a character (for messages). */
	public static Component nama(String id) {
		Data data = byId(id);
		return data == null ? Component.literal(id) : Component.translatable(data.kunciNama());
	}

	private AirTokoh() {
	}
}
