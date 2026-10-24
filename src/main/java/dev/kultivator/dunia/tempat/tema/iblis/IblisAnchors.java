package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaAnchors;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.sosok.GerakId;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every anchor kind of the Kota Iblis lots and of the hunters' camp (DESIGN 38.T.2.4, {@code kota_tema/<what>} through
 * {@link TemaAnchors#daftar}; tools/gen_tempat_tema_iblis.py and inti's satellite generator write them):
 * <pre>
 * gerbang_iblis      Gerbang (gate body, toll enforcers)            tuas_gerbang   PenyerbuanLurus (the lever body)
 * terowongan         PenyerbuanLurus (rubble stones, sisi luar|dalam) iblis_tokoh   a named character's body (data tokoh)
 * arena              ArenaDarah (cage doors, ladder fights)          taruhan        Taruhan (bookie, live matches)
 * kandang_binatang   ArenaDarah (the beast cages)                    tawanan        KandangTawanan (cells, captives, jailer)
 * kitab_terlarang    MenaraTerlarang (the tower's floors)            segel_menara   MenaraTerlarang (the floor-9 seal body)
 * pasar_gelap        a black-market broker (data kios)               makelar        the exchange clerk of Bai She's house
 * lelang_gelap       LelangGelap (the auctioneer)                   kontrak        the contract clerk of Sarang Kontrak
 * gudang_minyak      SarangKontrak (the oil barrels of chapter 3)    perbendaharaan the treasury guard (quest target)
 * kolam_semedi       the meditation pool (site of inti's TemaSitus)  pemburu        PemburuLurus (camp hunters, data ketua)
 * papan_buronan      BuronanIblis (the bounty board of the camp)
 * </pre>
 * Bodies and mechanisms spawned for an anchor are remembered per anchor ({@link #simpan}) and removed with it. Service
 * NPCs carry {@code memory iblis_layanan = <what>}; the "Jasa" tab of their talk lists the screens ({@link #jasa}).
 */
public final class IblisAnchors implements AnchorHandler {

	static final IblisAnchors HANDLER = new IblisAnchors();

	/** Kinds served by this handler (gerbang_iblis has its own, {@link Gerbang}). */
	static final List<String> KINDS = List.of("iblis_tokoh", "tuas_gerbang", "terowongan", "arena", "taruhan", "kandang_binatang", "tawanan",
			"kitab_terlarang", "segel_menara", "pasar_gelap", "makelar", "lelang_gelap", "kontrak", "gudang_minyak", "perbendaharaan",
			"kolam_semedi", "pemburu", "papan_buronan");

	/** Service anchors that bring one NPC of a role. */
	private static final Map<String, NpcRole> LAYANAN = new HashMap<>();

	static {
		LAYANAN.put("pasar_gelap", NpcRole.MAKELAR_GELAP);
		LAYANAN.put("makelar", NpcRole.MAKELAR_GELAP);
		LAYANAN.put("kontrak", NpcRole.PEMBUNUH_BAYARAN);
		LAYANAN.put("tawanan", NpcRole.PENEGAK_IBLIS);
		LAYANAN.put("taruhan", NpcRole.WARGA_IBLIS);
		LAYANAN.put("perbendaharaan", NpcRole.PENEGAK_IBLIS);
		LAYANAN.put("lelang_gelap", NpcRole.JURU_LELANG);
	}

	/** Respawn wait of a slain anchor body (5 minutes). */
	public static final long TUNGGU_LAHIR = 6000L;

	private static final Map<UUID, List<Entity>> MILIK = new HashMap<>();
	private static final Map<UUID, Long> MATI = new HashMap<>();

	private IblisAnchors() {
	}

	static void init() {
		for (String what : KINDS) {
			TemaAnchors.daftar(what, HANDLER);
		}
		TemaAnchors.daftar("gerbang_iblis", Gerbang.HANDLER);
		CeritaApi.daftarJasa(IblisAnchors::jasa);
	}

	static void clear() {
		MILIK.clear();
		MATI.clear();
	}

	@Override
	public int radius() {
		return 56;
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!TemaConfig.aktif(Tema.IBLIS)) {
			return;
		}
		String what = anchor.what();
		if ("pemburu".equals(what)) {
			PemburuLurus.nearbyPos(level, anchor, within);
			return;
		}
		if ("papan_buronan".equals(what)) {
			BuronanIblis.nearbyPapan(level, anchor, within);
			return;
		}
		TemaKota.Kota kota = TemaAnchors.kota(anchor);
		if (kota == null || kota.tema() != Tema.IBLIS) {
			return;
		}
		switch (what) {
			case "iblis_tokoh" -> tokoh(level, anchor, kota, within);
			case "tuas_gerbang" -> PenyerbuanLurus.nearbyTuas(level, anchor, kota, within);
			case "terowongan" -> PenyerbuanLurus.nearbyTerowongan(level, anchor, kota, within);
			case "arena" -> ArenaDarah.nearby(level, anchor, kota, within);
			case "kandang_binatang" -> ArenaDarah.ingatKandang(kota, anchor.position());
			case "taruhan" -> {
				layanan(level, anchor, kota, NpcRole.WARGA_IBLIS);
				Taruhan.nearby(level, anchor, kota, within);
			}
			case "tawanan" -> {
				layanan(level, anchor, kota, NpcRole.PENEGAK_IBLIS);
				KandangTawanan.nearby(level, anchor, kota, within);
			}
			case "kitab_terlarang" -> MenaraTerlarang.nearbyKitab(level, anchor, kota, within);
			case "segel_menara" -> MenaraTerlarang.nearbySegel(level, anchor, kota, within);
			case "gudang_minyak" -> SarangKontrak.nearbyGudang(level, anchor, kota, within);
			case "lelang_gelap" -> {
				// the one auction engine needs a JURU_LELANG (its "lelang" screen is 38EK's, offered by Ekonomi's Jasa tab)
				KultivatorNpc juru = layanan(level, anchor, kota, NpcRole.JURU_LELANG);
				LelangGelap.nearby(level, anchor, kota, juru, within);
			}
			case "kolam_semedi" -> {
				// the site and its factor are inti's TemaSitus; the pool only needs its anchor loaded
			}
			default -> {
				NpcRole role = LAYANAN.get(what);
				if (role != null) {
					layanan(level, anchor, kota, role);
				}
			}
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		lepasSemua(anchor);
		if ("kandang_binatang".equals(anchor.what())) {
			TemaKota.Kota kota = TemaAnchors.kota(anchor);
			if (kota != null) {
				ArenaDarah.lupakanKandang(kota.situsId());
			}
		}
	}

	// ================================================================== bodies of an anchor

	/** Remembers a body (NPC or mechanism) spawned for {@code anchor}; removed with the anchor. */
	public static <T extends Entity> T simpan(DuniaAnchorEntity anchor, T entity) {
		MILIK.computeIfAbsent(anchor.getUUID(), k -> new ArrayList<>()).add(entity);
		return entity;
	}

	/** The live bodies of {@code anchor} (dead ones are dropped; a slain NPC starts the respawn wait). */
	public static List<Entity> milik(DuniaAnchorEntity anchor) {
		List<Entity> list = MILIK.get(anchor.getUUID());
		if (list == null) {
			return List.of();
		}
		boolean slain = list.removeIf(e -> e.isRemoved() || e instanceof KultivatorNpc n && !n.isAlive());
		if (slain && list.isEmpty()) {
			MATI.put(anchor.getUUID(), anchor.level().getGameTime());
		}
		return list;
	}

	/** The first live body of {@code anchor} of class {@code type}, or null. */
	public static <T extends Entity> @Nullable T milik(DuniaAnchorEntity anchor, Class<T> type) {
		for (Entity e : milik(anchor)) {
			if (type.isInstance(e)) {
				return type.cast(e);
			}
		}
		return null;
	}

	/** True while a slain body of this anchor waits for its respawn. */
	public static boolean menunggu(DuniaAnchorEntity anchor) {
		Long t = MATI.get(anchor.getUUID());
		return t != null && anchor.level().getGameTime() - t < TUNGGU_LAHIR;
	}

	public static void lepasSemua(DuniaAnchorEntity anchor) {
		List<Entity> list = MILIK.remove(anchor.getUUID());
		if (list == null) {
			return;
		}
		for (Entity e : list) {
			if (e instanceof KultivatorNpc npc) {
				npc.vanish();
			} else if (e instanceof TemaMekanismeEntity) {
				TemaMekanisme.lepas(e);
			} else if (!e.isRemoved()) {
				e.discard();
			}
		}
	}

	/** The one service NPC of an anchor (spawned on demand), or null. */
	static @Nullable KultivatorNpc layanan(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, NpcRole role) {
		KultivatorNpc npc = milik(anchor, KultivatorNpc.class);
		if (npc != null || menunggu(anchor)) {
			return npc;
		}
		npc = HidupApi.tubuh(level, kota.situsId(), role, anchor.blockPosition(), anchor.getYRot(), (int) (anchor.blockPosition().asLong() * 0x9E37L));
		if (npc == null) {
			return null;
		}
		npc.memory().putString("iblis_layanan", anchor.what());
		npc.memory().putBoolean("hold", true);
		npc.setDiamDiTitik(true);
		if (role == NpcRole.MAKELAR_GELAP) {
			HidupApi.klip(npc, GerakId.HITUNG_KOIN_GELAP, 0, 0);
		} else if (role == NpcRole.PENEGAK_IBLIS) {
			HidupApi.klip(npc, GerakId.BERDIRI_JAGA, 0, 0);
		}
		return simpan(anchor, npc);
	}

	/** A named character at its lot (some only while the story keeps them there). */
	private static void tokoh(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, List<ServerPlayer> within) {
		String id = anchor.data().getStringOr("tokoh", "");
		if (!hadir(level, kota, id)) {
			lepasSemua(anchor);
			return;
		}
		KultivatorNpc npc = milik(anchor, KultivatorNpc.class);
		if (npc != null || menunggu(anchor)) {
			if (npc != null && TokohIblis.YE_WUHEN.equals(id) && !within.isEmpty() && npc.getTarget() == null) {
				HidupApi.tatap(npc, within.getFirst());
			}
			return;
		}
		BlockPos pos = anchor.blockPosition();
		npc = TokohIblis.panggil(level, kota.situsId(), id, pos, anchor.getYRot());
		if (npc == null) {
			return;
		}
		npc.memory().putBoolean("hold", true);
		npc.setDiamDiTitik(true);
		switch (id) {
			case TokohIblis.A_FU -> HidupApi.klip(npc, GerakId.BERLUTUT_RANTAI, 0, 0);
			case TokohIblis.MO_WEN -> HidupApi.klip(npc, GerakId.MEMBACA, 0, 0);
			case TokohIblis.TU_GANG -> HidupApi.klip(npc, GerakId.SIKAP_BERKACAK, 0, 0);
			case TokohIblis.BAI_SHE -> HidupApi.klip(npc, GerakId.KIPAS, 0, 0);
			case TokohIblis.LAO_KUANG -> HidupApi.klip(npc, GerakId.ASAH_PEDANG, 0, 0);
			case TokohIblis.XUE_YAO -> HidupApi.klip(npc, GerakId.SIKAP_ANGKUH, 0, 0);
			case TokohIblis.HONG_PAO -> HidupApi.klip(npc, GerakId.SIKAP_LIPAT_TANGAN, 0, 0);
			default -> HidupApi.klip(npc, GerakId.SIKAP_TANGAN_BELAKANG, 0, 0);
		}
		simpan(anchor, npc);
	}

	/** True while the story keeps character {@code id} at its lot in this town. */
	static boolean hadir(ServerLevel level, TemaKota.Kota kota, String id) {
		var town = IblisData.kota(level.getServer(), kota.situsId());
		return switch (id) {
			case TokohIblis.XUE_YAO -> IblisData.PENGUASA_XUE.equals(town.getStringOr("penguasa", IblisData.PENGUASA_XUE))
					&& !town.getBooleanOr("xue_yao_kalah", false);
			// A-Fu's body is the pens' (KandangTawanan: cell 0 while she is held); her own anchor stays empty
			case TokohIblis.A_FU -> false;
			case TokohIblis.LAO_KUANG -> !town.getBooleanOr("lao_kuang_pergi", false);
			case TokohIblis.HONG_PAO -> !town.getBooleanOr("hong_pao_pergi", false);
			case TokohIblis.YE_WUHEN -> !town.getBooleanOr("ye_wuhen_pergi", false);
			case "" -> false;
			default -> TokohIblis.byId(id) != null;
		};
	}

	// ================================================================== the "Jasa" tab

	/** Services of Kota Iblis NPCs: the five iblis screens by character or service anchor. */
	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer player, KultivatorNpc npc) {
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		String tokoh = TokohIblis.dari(npc);
		String layanan = npc.memory().getStringOr("iblis_layanan", "");
		if (tokoh.isEmpty() && layanan.isEmpty() && !npc.memory().getBooleanOr("iblis_pemburu", false)) {
			return out;
		}
		switch (tokoh) {
			case TokohIblis.TU_GANG -> out.add(entri(TemaLayar.Taruhan.LAYAR, "tangga", 3));
			case TokohIblis.MO_WEN -> out.add(entri(TemaLayar.KitabTerlarang.LAYAR, "kitab", 7));
			case TokohIblis.BAI_SHE -> {
				out.add(entri(TemaLayar.PasarGelap.LAYAR, "pasar", 5));
				out.add(entri(TemaLayar.Kontrak.LAYAR, "kontrak", 6));
			}
			case TokohIblis.HAN_QINGSHUANG -> out.add(entri(TemaLayar.Kontrak.LAYAR, "buronan", 6));
			default -> {
			}
		}
		if (npc.memory().getBooleanOr("iblis_pemburu", false) && out.isEmpty()) {
			out.add(entri(TemaLayar.Kontrak.LAYAR, "buronan", 6));
		}
		switch (layanan) {
			case "pasar_gelap", "makelar" -> out.add(entri(TemaLayar.PasarGelap.LAYAR, "pasar", 5));
			case "kontrak" -> out.add(entri(TemaLayar.Kontrak.LAYAR, "kontrak", 6));
			case "tawanan" -> out.add(entri(TemaLayar.Tawanan.LAYAR, "tawanan", 2));
			case "taruhan" -> out.add(entri(TemaLayar.Taruhan.LAYAR, "taruhan", 3));
			default -> {
			}
		}
		return out;
	}

	private static TempatDialog.JasaEntri entri(String layar, String nama, int ikon) {
		return new TempatDialog.JasaEntri(layar, Component.translatable("kultivator.kota_tema.iblis.jasa." + nama), ikon,
				Component.translatable("kultivator.kota_tema.iblis.jasa." + nama + ".catatan"), false, null);
	}

	/** The NPC of {@code npcId} near the player, or null. */
	static @Nullable KultivatorNpc npc(ServerPlayer player, int npcId) {
		if (npcId < 0) {
			return null;
		}
		Entity e = player.level().getEntity(npcId);
		return e instanceof KultivatorNpc n && n.isAlive() ? n : null;
	}
}
