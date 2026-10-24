package dev.kultivator.dunia.tempat.cerita;

import dev.kultivator.dunia.kota.KultivatorNpc;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stories public face (contract C38.ST1, owner 38ST): flags and quest stages per player ({@link CeritaData}), rumours
 * ({@link KabarTempat}), data dialogs ({@link TempatDialog}) and quest events from other modules ({@link QuestTracker}).
 * Additions beyond C38.ST1 (all additive): {@link #mulaiQuest}, {@link #kalimat}, {@link #kabarUntuk},
 * {@link #daftarJasa}, {@link #tandaiTokoh}, {@link #pasangTokoh}, {@link #penguasa}.
 *
 * <h2>Events other modules fire (goal kinds of {@link QuestTracker})</h2>
 * <pre>
 * peristiwa(player, "racik", pillId)            38EK alchemy finish (AlchemyFurnace.addFinishListener)
 * peristiwa(player, "tempa", shape + ":" + q)    38EK forging finish / forge order pickup
 * peristiwa(player, "bebaskan", "tawanan")       38GU demon lair captive freed, 38TK ransom
 * peristiwa(player, "kawal", what + "@" + kind)  38EK caravans ("kafilah@kota/kota"), escorts reaching a place
 * peristiwa(player, "pergi", siteKind)           any module that knows the player arrived (sites fire it already)
 * peristiwa(player, "ujian", id)                 38GU trials: "pertapa:meditasi" (5 unbroken minutes in the hermit's
 *                                                room), "makam:&lt;dao&gt;:&lt;tier&gt;" (all three tomb trials passed),
 *                                                "cermin_hati" (heart-demon trial at the pool passed)
 * peristiwa(player, "misi_gilda", jenis)         38EK guild mission finished (already fired by MisiGilda)
 * </pre>
 * 38ST itself fires {@code racik} (every successful furnace refining, item id of the result), {@code kalahkan} (entity
 * type id, {@code peran:<role>}, {@code tokoh:<id>}, {@code elit}, {@code monster}), {@code duel}, {@code bicara},
 * {@code antar}, {@code kawal} and {@code pergi}.
 *
 * <h2>Lantern riddles (38.B.6.1, read by 38KS)</h2>
 * {@link #TEKA_TEKI} riddles: {@code kultivator.cerita.teka.<n>.soal} (the riddle), {@code .benar} (right answer),
 * {@code .salah1}, {@code .salah2} (wrong answers), {@code n = 0 .. TEKA_TEKI - 1}; the screen shuffles the three.
 */
public final class CeritaApi {

	/** Number of lantern riddles in fragment {@code tempat_cerita}. */
	public static final int TEKA_TEKI = 40;

	/** True when story flag {@code id} is set for {@code player}. */
	public static boolean bendera(ServerPlayer player, String id) {
		return CeritaData.get(player.level().getServer()).flag(player.getUUID(), id);
	}

	/** Sets or clears story flag {@code id} for {@code player}. */
	public static void setBendera(ServerPlayer player, String id, boolean on) {
		CeritaData.get(player.level().getServer()).setFlag(player.getUUID(), id, on);
	}

	/** Current stage of quest {@code quest} for {@code player} (0 not started, 999 finished, -1 failed). */
	public static int tahap(ServerPlayer player, String quest) {
		return QuestTracker.tahap(player, quest);
	}

	/** Spreads a rumour from template {@code templat} (place-bound when {@code situsId} is given). */
	public static void kabar(ServerLevel level, @Nullable Long situsId, String templat, Object... isian) {
		KabarTempat.kabar(level, situsId, templat, isian);
	}

	/** Opens dialog {@code dialogId} of {@code npc} for {@code player}; false when no such dialog (old behaviour runs). */
	public static boolean dialog(ServerPlayer player, KultivatorNpc npc, String dialogId) {
		return TempatDialog.buka(player, npc, dialogId);
	}

	/** A quest-relevant event from another module ({@code jenis} = goal kind, {@code id} = what). */
	public static void peristiwa(ServerPlayer player, String jenis, String id) {
		QuestTracker.peristiwa(player, jenis, id);
	}

	/** Starts quest {@code quest} given at place {@code situsId}; false when unknown, running or already done. */
	public static boolean mulaiQuest(ServerPlayer player, String quest, long situsId) {
		return QuestTracker.mulai(player, quest, situsId);
	}

	/** A speech-bubble line for {@code npc} meeting {@code player} (38NL chatter, 38.S.6). */
	public static Component kalimat(ServerPlayer player, KultivatorNpc npc) {
		return Kalimat.sapa(player, npc);
	}

	/** A rumour {@code npc} would tell {@code player} now, or null (38NL chatter, Papan Kabar, Kedai Teh). */
	public static @Nullable Component kabarUntuk(ServerPlayer player, KultivatorNpc npc) {
		return KabarTempat.pilih(player, npc.town(), npc.role());
	}

	/**
	 * The newest live rumours of place {@code situsId} (then world-wide), newest first, at most {@code maks}, as
	 * {@code viewer} would read them: for a town's Papan Kabar ({@code PapanHidupEntity.JENIS_KABAR} sheets). Does not mark
	 * them heard.
	 */
	public static List<Component> kabarTerbaru(ServerPlayer viewer, long situsId, int maks) {
		List<Component> all = KabarTempat.semua(viewer, situsId);
		List<Component> out = new ArrayList<>();
		for (int i = all.size() - 1; i >= 0 && out.size() < maks; i--) {
			out.add(all.get(i));
		}
		return out;
	}

	/** Adds services to the "Jasa" tab of the dialog screen. */
	public static void daftarJasa(TempatDialog.PenyediaJasa penyedia) {
		TempatDialog.daftarJasa(penyedia);
	}

	/** Makes a spawned body the recurring character {@code tokohId} (38.S.2). */
	public static void tandaiTokoh(KultivatorNpc npc, String tokohId) {
		Tokoh.tandai(npc, tokohId);
	}

	/** Tags the body serving service lot {@code layanan} of place {@code situsId} as its character; returns the id or null. */
	public static @Nullable String pasangTokoh(KultivatorNpc npc, long situsId, String layanan) {
		return Tokoh.pasang(npc, situsId, layanan);
	}

	/** The crowned ruler of capital {@code situsId} after the arc ({@code "ruyi"}, {@code "gu"}, "" before). */
	public static String penguasa(ServerLevel level, long situsId) {
		return AlurTakhta.penguasa(level.getServer(), situsId);
	}

	private CeritaApi() {
	}
}
