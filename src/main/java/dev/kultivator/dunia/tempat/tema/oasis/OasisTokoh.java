package dev.kultivator.dunia.tempat.tema.oasis;

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
 * The seven recurring characters of the Kota Oasis (DESIGN 38.T.4.7). The first oasis town keeps their fixed names
 * ({@code kultivator.kota_tema.oasis.tokoh.<id>}); the dialogs and quests address them as {@code tokoh:<id>} (data
 * dialog {@code pembicara}, quest {@code pemberi}, goal {@code bicara tokoh:<id>}), which only needs the runtime tag
 * {@code tokoh} in {@link KultivatorNpc#memory()} that {@link #tandai} writes, so they work without an entry in 38ST's
 * capital table. Bodies are {@code noSave}; the anchor of each character's service lot re-makes and re-tags them
 * ({@link OasisAnchors}).
 * <ul>
 * <li>{@code sha_wuyan} TUAN_BENTENG, Istana Pasir (anchor {@code tema_oasis/istana}), voice m2, watak bijak.</li>
 * <li>{@code bulat_khan} KAFILAH_BASHI, Karavanserai Agung ({@code tema_oasis/karavanserai}), m1, ramah.</li>
 * <li>{@code shui_lian} PENDETA_AIR, Kuil Air ({@code tema_oasis/kuil_air}), f3, jujur.</li>
 * <li>{@code mo_hua} PELUKIS_GUA, Tebing Seribu Lukisan ({@code tema_oasis/mural}), m3, penakut (pelupa).</li>
 * <li>{@code ajun} ANAK, Lapangan Kafilah ({@code tema_oasis/lapangan}), f1, jujur.</li>
 * <li>{@code xie_duo} TETUA_IBLIS (gurun), called by the chain's stage 5 ({@code oasis_panggil_xie}), m2, curiga.</li>
 * <li>{@code gulnar} PENENUN_SUTRA, the old carpet weaver of Bazar Barat ({@code tema_oasis/tenun}; side quest
 * {@code tema_oasis_permadani_ratu}), f2, jujur.</li>
 * </ul>
 */
public final class OasisTokoh {

	/**
	 * One character.
	 *
	 * @param suara     voice index ({@code KultivatorNpc.VOICES}: 0 m1, 1 m2, 2 m3, 3 f1, 4 f2, 5 f3)
	 * @param usia      age band of {@link Sosok} ({@code ANAK}, {@code MUDA}, {@code DEWASA}, {@code TUA})
	 * @param watak     temperament index of {@code Tokoh.WATAK}
	 * @param jangkar   the anchor {@code what} that brings the body ("" = called by the story only)
	 */
	public record Data(String id, NpcRole peran, int suara, boolean perempuan, int usia, int watak, String jangkar) {

		public String kunciNama() {
			return "kultivator.kota_tema.oasis.tokoh." + id;
		}

		public String kunciGelar() {
			return "kultivator.kota_tema.oasis.tokoh." + id + ".gelar";
		}
	}

	public static final String SHA_WUYAN = "sha_wuyan";
	public static final String BULAT_KHAN = "bulat_khan";
	public static final String SHUI_LIAN = "shui_lian";
	public static final String MO_HUA = "mo_hua";
	public static final String AJUN = "ajun";
	public static final String XIE_DUO = "xie_duo";
	public static final String GULNAR = "gulnar";

	private static final Map<String, Data> SEMUA = new LinkedHashMap<>();

	static {
		add(SHA_WUYAN, NpcRole.TUAN_BENTENG, 1, false, Sosok.DEWASA, 3, OasisAnchors.ISTANA);
		add(BULAT_KHAN, NpcRole.KAFILAH_BASHI, 0, false, Sosok.TUA, 0, OasisAnchors.KARAVANSERAI);
		add(SHUI_LIAN, NpcRole.PENDETA_AIR, 5, true, Sosok.DEWASA, 6, OasisAnchors.KUIL_AIR);
		add(MO_HUA, NpcRole.PELUKIS_GUA, 2, false, Sosok.MUDA, 4, OasisAnchors.MURAL);
		add(AJUN, NpcRole.ANAK, 3, true, Sosok.ANAK, 6, OasisAnchors.LAPANGAN);
		add(XIE_DUO, NpcRole.TETUA_IBLIS, 1, false, Sosok.TUA, 1, "");
		add(GULNAR, NpcRole.PENENUN_SUTRA, 4, true, Sosok.TUA, 6, OasisAnchors.TENUN);
	}

	private static void add(String id, NpcRole peran, int suara, boolean perempuan, int usia, int watak, String jangkar) {
		SEMUA.put(id, new Data(id, peran, suara, perempuan, usia, watak, jangkar));
	}

	public static @Nullable Data byId(@Nullable String id) {
		return id == null ? null : SEMUA.get(id);
	}

	/** Every character in table order. */
	public static List<Data> semua() {
		return List.copyOf(SEMUA.values());
	}

	/** The character whose body the anchor {@code what} brings, or null. */
	public static @Nullable Data diJangkar(String what) {
		for (Data d : SEMUA.values()) {
			if (d.jangkar().equals(what)) {
				return d;
			}
		}
		return null;
	}

	/** The character id of a body ("" when it is nobody of this theme). */
	public static String dari(KultivatorNpc npc) {
		String id = npc.memory().getStringOr("tokoh", "");
		return SEMUA.containsKey(id) ? id : "";
	}

	/** True when {@code npc} is the character {@code id}. */
	public static boolean adalah(@Nullable KultivatorNpc npc, String id) {
		return npc != null && id.equals(npc.memory().getStringOr("tokoh", ""));
	}

	/**
	 * Makes {@code npc} the character {@code id}: the runtime tag the dialogs and quests read, the temperament and the
	 * name plate "Name · Title".
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
		npc.setPlate(Component.translatable("kultivator.kota.papan_nama", Component.translatable(data.kunciNama()),
				Component.translatable(data.kunciGelar())));
	}

	/** Spawns the character's body at {@code pos} for the place {@code situs} and tags it; null when the NPC cap is full. */
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

	private OasisTokoh() {
	}
}
