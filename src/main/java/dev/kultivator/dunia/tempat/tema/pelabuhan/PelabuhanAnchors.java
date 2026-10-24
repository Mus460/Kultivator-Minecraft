package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.PintuAirEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The anchors of {@code tools/gen_tempat_tema_pelabuhan.py} (module {@code "pelabuhan"} of {@code DuniaAnchors}; the
 * shared kinds {@code kota/*}, {@code kotabesar/*} and {@code kota_tema/*} of the same templates are served by their
 * owners). DuniaAnchors keeps one handler per module, so the harbour owns its prefix instead of sharing 38TK inti's
 * {@code kota_tema}. Data values are template strings read with defaults ({@link #angka}, {@link #teks}).
 * <pre>
 * pelabuhan/tambatan      a mooring: nomor I (0-2 merchant junks, 3 the Kapal Lintas Laut), kelas S (jung|jung_laut)
 * pelabuhan/derek         a treadwheel crane of the Dermaga Agung: nomor I (0 serves moorings 0-1, 1 serves 1-2)
 * pelabuhan/suar          the lamp room of the Mercusuar Pelita Angin (entity api_suar)
 * pelabuhan/galangan      a slipway: landasan I (0, 1) where an ordered hull grows (entity kapal_galangan)
 * pelabuhan/lintas_laut   the Pos Kapal Lintas Laut quay: Kapten Gao Fan (tokoh gao_fan)
 * pelabuhan/tokoh         a named character's spot: tokoh S, peran S
 * pelabuhan/peran         a plain resident with a post: peran S, layanan S (bea|toko_seberang|kontrak|rumah_zhao|"")
 * pelabuhan/perahu_sewa   the boat-hire stand of the Tambatan Nelayan (boats perahu_nelayan)
 * pelabuhan/pintu_dok     the sea gate of the dry dock (entity pintu_air, variant dok)
 * pelabuhan/kuil          the altar of the Kuil Dewi Laut: the priestess (tokoh pendeta_laut); start of the procession
 * pelabuhan/tambat_langit a flying-ship landing deck of the Menara Tambat Langit: anjungan I (0 with the fuel desk, 1)
 * pelabuhan/gudang        the Gudang Bersama: a porter foreman (KULI)
 * </pre>
 * Bodies and mechanisms are {@code noSave}; they appear when a player comes within {@link #radius()} blocks (the
 * moorings use the 160-block range of {@link JadwalKapal}) and go when the anchor unloads.
 */
public final class PelabuhanAnchors implements AnchorHandler {

	public static final String MODULE = "pelabuhan";

	public static final String TAMBATAN = "tambatan";
	public static final String DEREK = "derek";
	public static final String SUAR = "suar";
	public static final String GALANGAN = "galangan";
	public static final String LINTAS_LAUT = "lintas_laut";
	public static final String TOKOH = "tokoh";
	public static final String PERAN = "peran";
	public static final String PERAHU_SEWA = "perahu_sewa";
	public static final String PINTU_DOK = "pintu_dok";
	public static final String KUIL = "kuil";
	public static final String TAMBAT_LANGIT = "tambat_langit";
	public static final String GUDANG = "gudang";

	static final PelabuhanAnchors HANDLER = new PelabuhanAnchors();

	/** Body or mechanism made per anchor (anchor uuid -> entity). */
	private final Map<UUID, Entity> buatan = new HashMap<>();

	static void init() {
		DuniaAnchors.register(MODULE, HANDLER);
	}

	static void reset() {
		HANDLER.buatan.clear();
	}

	@Override
	public int radius() {
		return 128;
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		PelabuhanKota.daftar(level, anchor);
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!PelabuhanKota.aktif()) {
			return;
		}
		PelabuhanKota.Hidup kota = PelabuhanKota.dari(anchor);
		if (kota == null) {
			kota = PelabuhanKota.daftar(level, anchor);
		}
		String what = anchor.what();
		try {
			switch (what) {
				case TAMBATAN -> JadwalKapal.tambatanDekat(level, anchor, kota);
				case DEREK -> {
					if (dekat(anchor, within, 64.0D)) {
						DerekDermagaEntity derek = mekanisme(level, anchor, kota, TemaEntities.DEREK_DERMAGA);
						if (derek != null) {
							int n = angka(anchor, "nomor", 0);
							derek.setKeadaan(JadwalKapal.adaTambat(level.getServer(), kota.situs, n, n + 1)
									? DerekDermagaEntity.KEADAAN_BONGKAR : DerekDermagaEntity.KEADAAN_DIAM);
						}
					} else {
						lepas(anchor);
					}
				}
				case SUAR -> {
					ApiSuarEntity suar = mekanisme(level, anchor, kota, TemaEntities.API_SUAR);
					if (suar != null) {
						Suar.atur(level, kota, suar);
					}
				}
				case GALANGAN -> Galangan.landasanDekat(level, anchor, kota, this);
				case LINTAS_LAUT -> {
					if (dekat(anchor, within, 48.0D)) {
						tokoh(level, anchor, kota, PelabuhanTokoh.GAO_FAN);
					}
					LintasLaut.dermagaDekat(level, anchor, kota, within);
				}
				case TOKOH -> {
					if (dekat(anchor, within, 48.0D)) {
						tokoh(level, anchor, kota, teks(anchor, "tokoh"));
					}
				}
				case KUIL -> {
					if (dekat(anchor, within, 48.0D)) {
						tokoh(level, anchor, kota, PelabuhanTokoh.PENDETA_LAUT);
					}
				}
				case PERAN -> {
					if (dekat(anchor, within, 48.0D)) {
						peran(level, anchor, kota);
					}
				}
				case GUDANG -> {
					if (dekat(anchor, within, 48.0D)) {
						peran(level, anchor, kota);
					}
				}
				case PERAHU_SEWA -> {
					if (dekat(anchor, within, 48.0D)) {
						peran(level, anchor, kota);
					}
					PerahuSewa.standDekat(level, anchor, kota, within);
				}
				case PINTU_DOK -> {
					PintuAirEntity gate = mekanisme(level, anchor, kota, TemaEntities.PINTU_AIR);
					if (gate != null && gate.varian() != PintuAirEntity.VARIAN_DOK) {
						gate.setVarian(PintuAirEntity.VARIAN_DOK);
						gate.setOtomatis(true);
					}
				}
				case TAMBAT_LANGIT -> MenaraTambat.anjunganDekat(level, anchor, kota, within, this);
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Harbour anchor {} failed", what, exception);
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		if (TAMBATAN.equals(anchor.what())) {
			JadwalKapal.tambatanLepas(anchor);
		}
		if (GALANGAN.equals(anchor.what())) {
			Galangan.landasanLepas(anchor);
		}
		lepas(anchor);
		PelabuhanKota.lupakan(anchor);
	}

	// ================================================================== bodies

	/** The body this anchor made, or null. */
	@Nullable Entity buatan(DuniaAnchorEntity anchor) {
		Entity e = buatan.get(anchor.getUUID());
		return e == null || e.isRemoved() ? null : e;
	}

	/** Removes what the anchor made. */
	void lepas(DuniaAnchorEntity anchor) {
		Entity made = buatan.remove(anchor.getUUID());
		if (made != null && !made.isRemoved()) {
			if (made instanceof KultivatorNpc npc) {
				npc.vanish();
			} else {
				TemaMekanisme.lepas(made);
			}
		}
	}

	/** Keeps {@code e} as the anchor's body (replacing nothing). */
	void simpan(DuniaAnchorEntity anchor, Entity e) {
		buatan.put(anchor.getUUID(), e);
	}

	/** A named character at the anchor, once. */
	private void tokoh(ServerLevel level, DuniaAnchorEntity anchor, PelabuhanKota.Hidup kota, String id) {
		if (buatan(anchor) != null || PelabuhanTokoh.byId(id) == null) {
			return;
		}
		if (PelabuhanCerita.tokohPergi(level.getServer(), kota.situs, id)) {
			return;
		}
		KultivatorNpc npc = PelabuhanTokoh.panggil(level, kota.situs, id, anchor.blockPosition(), anchor.getYRot());
		if (npc != null) {
			npc.memory().putBoolean("hold", true);
			npc.memory().putLong(TokoPelabuhan.SITUS_NPC, kota.situs);
			String layanan = teks(anchor, "layanan");
			if (!layanan.isEmpty()) {
				npc.memory().putString(TokoPelabuhan.LAYANAN_NPC, layanan);
			}
			buatan.put(anchor.getUUID(), npc);
		}
	}

	/** A plain resident at its post (customs officer, foreign trader, porter foreman...), tagged with its service. */
	private void peran(ServerLevel level, DuniaAnchorEntity anchor, PelabuhanKota.Hidup kota) {
		if (buatan(anchor) != null) {
			return;
		}
		NpcRole role = role(teks(anchor, "peran"), GUDANG.equals(anchor.what()) ? NpcRole.KULI : NpcRole.PELAUT);
		KultivatorNpc npc = HidupApi.tubuhStaf(level, kota.situs, role, anchor.blockPosition(), anchor.getYRot());
		if (npc == null) {
			return;
		}
		npc.memory().putLong(TokoPelabuhan.SITUS_NPC, kota.situs);
		String layanan = teks(anchor, "layanan");
		if (GUDANG.equals(anchor.what())) {
			layanan = "gudang";
		} else if (PERAHU_SEWA.equals(anchor.what())) {
			layanan = "perahu_sewa";
		} else if (layanan.isEmpty() && role == NpcRole.PENJUAL_IKAN) {
			layanan = "ikan";
		}
		if (!layanan.isEmpty()) {
			npc.memory().putString(TokoPelabuhan.LAYANAN_NPC, layanan);
			npc.memory().putBoolean("hold", true);
		}
		buatan.put(anchor.getUUID(), npc);
	}

	/** A themed mechanism at the anchor (through {@code TemaMekanisme}, town and world caps), once. */
	@SuppressWarnings("unchecked")
	<T extends Entity> @Nullable T mekanisme(ServerLevel level, DuniaAnchorEntity anchor, PelabuhanKota.Hidup kota,
			net.minecraft.world.entity.EntityType<T> type) {
		Entity have = buatan(anchor);
		if (have != null) {
			return have.getType() == type ? (T) have : null;
		}
		T e = TemaMekanisme.muncul(level, type, anchor.position(), anchor.getYRot(), kota.kota, anchor.blockPosition());
		if (e != null) {
			buatan.put(anchor.getUUID(), e);
		}
		return e;
	}

	// ================================================================== helpers

	/** True when one of the players is within {@code r} blocks of the anchor. */
	static boolean dekat(DuniaAnchorEntity anchor, List<ServerPlayer> within, double r) {
		double r2 = r * r;
		for (ServerPlayer p : within) {
			if (p.distanceToSqr(anchor) <= r2) {
				return true;
			}
		}
		return false;
	}

	/** An int value of the anchor data (template strings or ints). */
	static int angka(DuniaAnchorEntity anchor, String key, int fallback) {
		String raw = anchor.data().getStringOr(key, "");
		if (raw.isEmpty()) {
			return anchor.data().getIntOr(key, fallback);
		}
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** A string value of the anchor data ("" when absent). */
	static String teks(DuniaAnchorEntity anchor, String key) {
		return anchor.data().getStringOr(key, "");
	}

	private static NpcRole role(String name, NpcRole fallback) {
		if (name == null || name.isEmpty()) {
			return fallback;
		}
		try {
			return NpcRole.valueOf(name);
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}

	/** The body of character {@code id} within {@code r} blocks of the player, or null. */
	static @Nullable KultivatorNpc tokohDekat(ServerPlayer player, String id, double r) {
		AABB box = player.getBoundingBox().inflate(r);
		List<KultivatorNpc> list = player.level().getEntitiesOfClass(KultivatorNpc.class, box, npc -> PelabuhanTokoh.adalah(npc, id));
		KultivatorNpc best = null;
		double bestD = Double.MAX_VALUE;
		for (KultivatorNpc npc : list) {
			double d = npc.distanceToSqr(player);
			if (d < bestD) {
				bestD = d;
				best = npc;
			}
		}
		return best;
	}

	/** The town position of an anchor's spot one block above it. */
	static Vec3 atas(DuniaAnchorEntity anchor) {
		return anchor.position().add(0.0D, 1.0D, 0.0D);
	}

	private PelabuhanAnchors() {
	}
}
