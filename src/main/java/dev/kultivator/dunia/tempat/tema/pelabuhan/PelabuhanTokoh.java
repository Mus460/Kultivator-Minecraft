package dev.kultivator.dunia.tempat.tema.pelabuhan;

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
 * The recurring characters of the Kota Pelabuhan (DESIGN 38.T.3.5). Dialogs and quests address them as
 * {@code tokoh:<id>} (data dialog {@code pembicara}, quest {@code pemberi}, goal {@code bicara tokoh:<id>}); that only
 * needs the runtime tag {@code tokoh} in {@link KultivatorNpc#memory()} which {@link #tandai} writes, so they work
 * without an entry in 38ST's capital table. Names are fixed ({@code kultivator.kota_tema.pelabuhan.tokoh.<id>}) in every
 * harbour: the harbour families are the same clans up and down the coast. Bodies are {@code noSave}; the anchor of each
 * character's spot re-makes and re-tags them ({@link PelabuhanAnchors}, kind {@code pelabuhan/tokoh}, data
 * {@code tokoh}).
 * <ul>
 * <li>{@code lin_haichao} SYAHBANDAR, Kantor Urusan Laut balcony; voice f2; a stern former captain who trusts no
 * sect.</li>
 * <li>{@code zhao_wanli} PEDAGANG_SEBERANG, head of the Merchants' Council; m2; friendly, slippery, owes everyone.</li>
 * <li>{@code deng_ming} PENJAGA_SUAR, the lighthouse; m3 old; half deaf, tells of the Boundless Sea.</li>
 * <li>{@code a_lan} PELAUT, Kedai Jangkar Patah; f1 young; brave, wants to be a captain.</li>
 * <li>{@code gong_shumu} TUKANG_KAPAL "Mbah Lunas", the shipyard; f3 old; rough and brilliant.</li>
 * <li>{@code ibrahim} PEDAGANG_SEBERANG from the Western Lands, the foreign quarter; m1; polite, careful, a
 * star-watcher.</li>
 * <li>{@code gao_fan} NAKHODA_LAUT, the Kapal Lintas Laut quay; m2; a coward everywhere but at sea.</li>
 * <li>{@code bibi_yu} PENJUAL_IKAN, the Morning Fish Market; f3; loud, generous, cooks the festival soup.</li>
 * <li>{@code pendeta_laut} PENDETA_LAUT "Pendeta Mei", keeper of the Sea Goddess temple; f2.</li>
 * <li>{@code hei_sha} "Kapten Hiu Hitam", pirate captain (NAKHODA_LAUT, tier 4); m3; called by the chain only.</li>
 * </ul>
 */
public final class PelabuhanTokoh {

	/**
	 * One character.
	 *
	 * @param suara voice index of {@code KultivatorNpc.VOICES} (0 m1, 1 m2, 2 m3, 3 f1, 4 f2, 5 f3)
	 * @param usia  age band of {@link Sosok}
	 * @param watak temperament index of {@code Tokoh.WATAK} (0 ramah, 1 curiga, 2 serakah, 3 bijak, 4 penakut, 5 sombong,
	 *              6 jujur, 7 licik)
	 */
	public record Data(String id, NpcRole peran, int suara, boolean perempuan, int usia, int watak) {

		public String kunciNama() {
			return "kultivator.kota_tema.pelabuhan.tokoh." + id;
		}

		public String kunciGelar() {
			return "kultivator.kota_tema.pelabuhan.tokoh." + id + ".gelar";
		}

		public Component nama() {
			return Component.translatable(kunciNama());
		}
	}

	public static final String LIN_HAICHAO = "lin_haichao";
	public static final String ZHAO_WANLI = "zhao_wanli";
	public static final String DENG_MING = "deng_ming";
	public static final String A_LAN = "a_lan";
	public static final String GONG_SHUMU = "gong_shumu";
	public static final String IBRAHIM = "ibrahim";
	public static final String GAO_FAN = "gao_fan";
	public static final String BIBI_YU = "bibi_yu";
	public static final String PENDETA_LAUT = "pendeta_laut";
	public static final String HEI_SHA = "hei_sha";

	private static final Map<String, Data> SEMUA = new LinkedHashMap<>();

	static {
		add(LIN_HAICHAO, NpcRole.SYAHBANDAR, 4, true, Sosok.DEWASA, 1);
		add(ZHAO_WANLI, NpcRole.PEDAGANG_SEBERANG, 1, false, Sosok.DEWASA, 7);
		add(DENG_MING, NpcRole.PENJAGA_SUAR, 2, false, Sosok.TUA, 3);
		add(A_LAN, NpcRole.PELAUT, 3, true, Sosok.MUDA, 6);
		add(GONG_SHUMU, NpcRole.TUKANG_KAPAL, 5, true, Sosok.TUA, 5);
		add(IBRAHIM, NpcRole.PEDAGANG_SEBERANG, 0, false, Sosok.DEWASA, 6);
		add(GAO_FAN, NpcRole.NAKHODA_LAUT, 1, false, Sosok.DEWASA, 4);
		add(BIBI_YU, NpcRole.PENJUAL_IKAN, 5, true, Sosok.DEWASA, 0);
		add(PENDETA_LAUT, NpcRole.PENDETA_LAUT, 4, true, Sosok.DEWASA, 3);
		add(HEI_SHA, NpcRole.NAKHODA_LAUT, 2, false, Sosok.DEWASA, 5);
	}

	private static void add(String id, NpcRole peran, int suara, boolean perempuan, int usia, int watak) {
		SEMUA.put(id, new Data(id, peran, suara, perempuan, usia, watak));
	}

	public static @Nullable Data byId(@Nullable String id) {
		return id == null ? null : SEMUA.get(id);
	}

	/** Every character in table order. */
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

	/** True when {@code npc} is the character {@code id}. */
	public static boolean adalah(@Nullable KultivatorNpc npc, String id) {
		return npc != null && id.equals(npc.memory().getStringOr("tokoh", ""));
	}

	/**
	 * Makes {@code npc} the character {@code id}: the runtime tag the dialogs and quests read, the temperament, sex and
	 * age, the voice and the name plate "Name · Title".
	 */
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
		Component plate = Component.translatable("kultivator.kota.papan_nama", data.nama(), Component.translatable(data.kunciGelar()));
		int tier = HEI_SHA.equals(id) ? 4 : data.peran().tier;
		npc.setup(data.peran(), tier, npc.look(), npc.realm(), npc.stage(), npc.dao(), data.suara(), npc.robe(), plate);
		npc.setPlate(plate);
	}

	/** Spawns the character's body at {@code pos} for place {@code situs} and tags it; null when the NPC cap is full. */
	public static @Nullable KultivatorNpc panggil(ServerLevel level, long situs, String id, BlockPos pos, float yaw) {
		Data data = byId(id);
		if (data == null) {
			return null;
		}
		KultivatorNpc npc = HidupApi.tubuhStaf(level, situs, data.peran(), pos, yaw);
		if (npc != null) {
			tandai(npc, id);
		}
		return npc;
	}

	private PelabuhanTokoh() {
	}
}
