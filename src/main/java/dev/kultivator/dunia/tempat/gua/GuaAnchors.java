package dev.kultivator.dunia.tempat.gua;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The anchor contract of {@code tools/gen_tempat_gua.py} (module {@code "gua"} of {@code DuniaAnchors}): kinds, their
 * template data and the derived numbers every run shares (site kinds, distance rings, suggested realms, a tomb's Dao).
 * <pre>
 * gua/situs              situs S (gua/pertapa, gua/makam, ..., situs/puncak_petir), tier I   (published by the spine)
 * gua/masuk              nama S (gua_pertapa|makam_warisan|sarang_binatang|sarang_iblis|cermin_hati), ranah S, tier I
 * gua/pertapa            ruang I (radius of the meditation chamber)
 * gua/makam_penghalang   tier I, px pz I (seal centre offset)
 * gua/makam_hati         tier I, px pz pyaw (exit door)
 * gua/makam_kekuatan     tier I, kursi S "x,z;..." (puppet seats)
 * gua/makam_pemahaman    tier I, lempeng S "x,z;..." (five plate sockets)
 * gua/makam_aula         tier I
 * gua/sarang_bos         sarang I (radius of the nest)
 * gua/iblis_pintu        px pz I (door centre offset)      gua/iblis_band, gua/iblis_bos
 * gua/iblis_sel          sel S "x,y,z;..." (cage doors)
 * gua/urat_inti          jaga S "x,z;..." (guardian seats), harta S "x,z" (treasure point)
 * gua/cermin_kolam       cx cz I (the stone mirror)
 * gua/petir_altar        r I (terrace radius), bx bz I (Batu Catatan)
 * gua/bintang_panggung   r I            gua/mata_air  r I
 * </pre>
 */
public final class GuaAnchors {

	public static final String MODULE = "gua";

	public static final String SITUS = "situs";
	public static final String MASUK = "masuk";
	public static final String PERTAPA = "pertapa";
	public static final String MAKAM_PENGHALANG = "makam_penghalang";
	public static final String MAKAM_HATI = "makam_hati";
	public static final String MAKAM_KEKUATAN = "makam_kekuatan";
	public static final String MAKAM_PEMAHAMAN = "makam_pemahaman";
	public static final String MAKAM_AULA = "makam_aula";
	public static final String SARANG_BOS = "sarang_bos";
	public static final String IBLIS_PINTU = "iblis_pintu";
	public static final String IBLIS_BAND = "iblis_band";
	public static final String IBLIS_SEL = "iblis_sel";
	public static final String IBLIS_BOS = "iblis_bos";
	public static final String URAT_INTI = "urat_inti";
	public static final String CERMIN_KOLAM = "cermin_kolam";
	public static final String PETIR_ALTAR = "petir_altar";
	public static final String BINTANG_PANGGUNG = "bintang_panggung";
	public static final String MATA_AIR = "mata_air";

	/** Site kinds published by the {@code gua/situs} anchors. */
	public static final String K_PERTAPA = "gua/pertapa";
	public static final String K_MAKAM = "gua/makam";
	public static final String K_SARANG_BINATANG = "gua/sarang_binatang";
	public static final String K_SARANG_IBLIS = "gua/sarang_iblis";
	public static final String K_URAT_ROH = "gua/urat_roh";
	public static final String K_CERMIN = "gua/cermin_hati";
	public static final String K_PUNCAK = "situs/puncak_petir";
	public static final String K_PANGGUNG = "situs/panggung_bintang";
	public static final String K_MATA_AIR = "situs/mata_air_roh";

	/** The eight Dao in their code order (a tomb's Dao is one of them by site hash; 38ST has 24 ancestors = 8 x 3). */
	private static final Dao[] DAO = Dao.values();

	/** The site kind of the structure an anchor belongs to. */
	public static String situsOf(DuniaAnchorEntity anchor) {
		String what = anchor.what();
		if (SITUS.equals(what)) {
			return anchor.data().getStringOr("situs", K_PERTAPA);
		}
		if (MASUK.equals(what)) {
			return situsOfNama(anchor.data().getStringOr("nama", ""));
		}
		if (what.startsWith("makam_")) {
			return K_MAKAM;
		}
		if (what.startsWith("iblis_")) {
			return K_SARANG_IBLIS;
		}
		return switch (what) {
			case PERTAPA -> K_PERTAPA;
			case SARANG_BOS -> K_SARANG_BINATANG;
			case URAT_INTI -> K_URAT_ROH;
			case CERMIN_KOLAM -> K_CERMIN;
			case PETIR_ALTAR -> K_PUNCAK;
			case BINTANG_PANGGUNG -> K_PANGGUNG;
			case MATA_AIR -> K_MATA_AIR;
			default -> MODULE + "/" + what;
		};
	}

	/** The site kind of an entry card name ({@code gua/masuk} data {@code nama}). */
	public static String situsOfNama(String nama) {
		return switch (nama) {
			case "makam_warisan" -> K_MAKAM;
			case "sarang_binatang" -> K_SARANG_BINATANG;
			case "sarang_iblis" -> K_SARANG_IBLIS;
			case "cermin_hati" -> K_CERMIN;
			default -> K_PERTAPA;
		};
	}

	/** The world day of {@code level}'s overworld clock. */
	public static long today(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), 24000L);
	}

	/** The distance ring of DESIGN 38.C.9 (1: < 1000 blocks from the world centre, 2: < 3000, 3: < 8000, 4: beyond). */
	public static int ring(BlockPos pos) {
		double d = Math.sqrt((double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ());
		if (d < 1000.0D) {
			return 1;
		}
		if (d < 3000.0D) {
			return 2;
		}
		return d < 8000.0D ? 3 : 4;
	}

	/** Elite tier (1-3) of a beast den's boss: grows with the ring. */
	public static int sarangTier(BlockPos pos) {
		return Math.max(1, Math.min(3, ring(pos)));
	}

	/** The suggested realm of a beast den (T1 Qi Refining, T2 Foundation, T3 Golden Core). */
	public static Realm ranahSarang(BlockPos pos) {
		return switch (sarangTier(pos)) {
			case 3 -> Realm.GOLDEN_CORE;
			case 2 -> Realm.FOUNDATION;
			default -> Realm.QI_REFINING;
		};
	}

	/** The suggested realm of a demonic lair: Foundation (rings 1-2), Golden Core (3), Nascent Soul (4). */
	public static Realm ranahIblis(BlockPos pos) {
		return switch (ring(pos)) {
			case 4 -> Realm.NASCENT_SOUL;
			case 3 -> Realm.GOLDEN_CORE;
			default -> Realm.FOUNDATION;
		};
	}

	/** The suggested realm of a tomb tier (1 Foundation, 2 Nascent Soul, 3 Dao Union). */
	public static Realm ranahMakam(int tier) {
		return switch (Math.max(1, Math.min(3, tier))) {
			case 3 -> Realm.DAO_UNION;
			case 2 -> Realm.NASCENT_SOUL;
			default -> Realm.FOUNDATION;
		};
	}

	/** The suggested realm of an entry card ({@code ranah} "" none, a realm id, or "auto" from the place). */
	public static @Nullable Realm ranahMasuk(DuniaAnchorEntity anchor) {
		String ranah = anchor.data().getStringOr("ranah", "");
		if (ranah.isEmpty()) {
			return null;
		}
		if ("auto".equals(ranah)) {
			String nama = anchor.data().getStringOr("nama", "");
			BlockPos at = anchor.origin() != null ? anchor.origin() : anchor.blockPosition();
			return "sarang_iblis".equals(nama) ? ranahIblis(at) : ranahSarang(at);
		}
		return Realm.byId(ranah);
	}

	/** The Dao of a tomb, fixed by its site id. */
	public static Dao daoMakam(long situs) {
		return DAO[Math.floorMod((int) (situs ^ (situs >>> 32)) * 31 + 7, DAO.length)];
	}

	/** Parses {@code "a,b;c,d"} int pairs (missing or bad entries are skipped). */
	public static List<int[]> pairs(CompoundTag data, String key) {
		return ints(data.getStringOr(key, ""), 2);
	}

	/** Parses {@code "a,b,c;d,e,f"} int triples. */
	public static List<int[]> triples(CompoundTag data, String key) {
		return ints(data.getStringOr(key, ""), 3);
	}

	private static List<int[]> ints(String text, int n) {
		List<int[]> out = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return out;
		}
		for (String part : text.split(";")) {
			String[] bits = part.trim().split(",");
			if (bits.length != n) {
				continue;
			}
			int[] v = new int[n];
			try {
				for (int i = 0; i < n; i++) {
					v[i] = Integer.parseInt(bits[i].trim());
				}
			} catch (NumberFormatException e) {
				continue;
			}
			out.add(v);
		}
		return out;
	}

	private GuaAnchors() {
	}
}
