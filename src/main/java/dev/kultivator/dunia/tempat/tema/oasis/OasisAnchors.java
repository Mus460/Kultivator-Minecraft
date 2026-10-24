package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The anchors of {@code tools/gen_tempat_tema_oasis.py} (module {@code "tema_oasis"} of {@code DuniaAnchors}; the shared
 * kinds {@code kota/*}, {@code kotabesar/*} and {@code kota_tema/*} of the same templates are served by their owners, the
 * ruin satellite's {@code kota_tema/*} kinds by {@link Reruntuhan}). DuniaAnchors allows one handler per module, so the
 * oasis owns its own prefix instead of sharing 38TK inti's {@code kota_tema}. Data values are strings (template NBT),
 * read with defaults.
 * <pre>
 * tema_oasis/pusat         Alun Oasis, the exact grid centre of district mata_air (town centre, storm centre)
 * tema_oasis/kuil_air      the holy pool of the Kuil Air: Pendeta Shui Lian; r I (pool radius, meditation site)
 * tema_oasis/batu_ukur     the Batu Ukur Air stela (entity batu_ukur_air)
 * tema_oasis/timba         a shaduf well (entity timba_sumur); antre B (the rationing queue of chain stage 1 forms here)
 * tema_oasis/mural         Tebing Seribu Lukisan: Mo Hua (painter, tema_mural screen)
 * tema_oasis/gua12         the twelfth niche of the cliff: Xie Duo sits there as a hermit after his redemption
 * tema_oasis/istana        the throne of the Istana Pasir: Tuan Benteng Sha Wuyan
 * tema_oasis/karavanserai  the caravan office of the Karavanserai Agung: Bulat Khan (tema_kafilah screen)
 * tema_oasis/kandang       a camel pen: unta I resting camels, sewa B (a camel-hire handler, role PENUNTUN_UNTA)
 * tema_oasis/lapangan      the Lapangan Kafilah camp: Si Kecil Ajun; caravans form and arrive here
 * tema_oasis/bazar         a bazaar lane (Pasar Bulan Sabit guest stalls on crescent nights); sisi S (barat|timur)
 * tema_oasis/toko_air      the water and ice shop (stall kind "air")
 * tema_oasis/bea           the customs desk of the Gerbang Giok (3 % gate duty on sealed trade crates)
 * tema_oasis/gerbang       the middle of the Gerbang Giok arch; lebar I (7): four leaves close it during a sandstorm
 * tema_oasis/panggung      the round dance stage on the lake (Malam Tari Putar); r I (dancer ring radius)
 * tema_oasis/kebun         the date garden: the date farmer (daily harvest of quest tema_oasis_kurma_emas); r I
 * tema_oasis/tenun         the carpet weaver's house (quest tema_oasis_permadani_ratu; stall kind "penenun")
 * </pre>
 * Every body and mechanism made here is {@code noSave}; it is made when a player comes within {@link #radius()} blocks
 * and removed when the anchor unloads. Mechanisms go through 38TK inti's {@code TemaMekanisme} caps.
 */
public final class OasisAnchors implements AnchorHandler {

	public static final String MODULE = "tema_oasis";

	public static final String PUSAT = "pusat";
	public static final String KUIL_AIR = "kuil_air";
	public static final String BATU_UKUR = "batu_ukur";
	public static final String TIMBA = "timba";
	public static final String MURAL = "mural";
	public static final String GUA12 = "gua12";
	public static final String ISTANA = "istana";
	public static final String KARAVANSERAI = "karavanserai";
	public static final String KANDANG = "kandang";
	public static final String LAPANGAN = "lapangan";
	public static final String BAZAR = "bazar";
	public static final String TOKO_AIR = "toko_air";
	public static final String BEA = "bea";
	public static final String GERBANG = "gerbang";
	public static final String PANGGUNG = "panggung";
	public static final String KEBUN = "kebun";
	public static final String TENUN = "tenun";

	static final OasisAnchors HANDLER = new OasisAnchors();

	/** Bodies and mechanisms made per anchor (anchor uuid -> entity). */
	private final Map<UUID, Entity> buatan = new HashMap<>();
	/** Extra bodies per anchor (camels of a pen, Xie Duo at the cliff). */
	private final Map<UUID, List<Entity>> buatanBanyak = new HashMap<>();

	static void init() {
		DuniaAnchors.register(MODULE, HANDLER);
	}

	/** Server stop. */
	static void reset() {
		HANDLER.buatan.clear();
		HANDLER.buatanBanyak.clear();
	}

	@Override
	public int radius() {
		return 48;
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		OasisKota.daftar(level, anchor);
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!OasisKota.aktif()) {
			return;
		}
		String what = anchor.what();
		try {
			switch (what) {
				case PUSAT -> OasisKota.daftar(level, anchor);
				case KUIL_AIR, ISTANA, KARAVANSERAI, LAPANGAN -> tokoh(level, anchor);
				case MURAL -> tokoh(level, anchor);
				case GUA12 -> xieDiTebing(level, anchor);
				case BATU_UKUR -> mekanisme(level, anchor, TemaEntities.BATU_UKUR_AIR, e -> KuilAir.batuDibuat(level, anchor, (BatuUkurAirEntity) e));
				case TIMBA -> {
					mekanisme(level, anchor, TemaEntities.TIMBA_SUMUR, e -> {
					});
					Entity e = buatan(anchor);
					KuilAir.timbaDekat(level, anchor, e instanceof TimbaSumurEntity t ? t : null, within);
				}
				case KANDANG -> KafilahUnta.kandangDekat(level, anchor, within, buatanBanyak.computeIfAbsent(anchor.getUUID(), k -> new ArrayList<>()));
				case BAZAR -> Tawar.bazarDekat(level, anchor, within);
				case TOKO_AIR -> layanan(level, anchor, NpcRole.PEDAGANG_BAZAR, "air", GerakId.SERU_DAGANG, PropId.KENDI);
				case TENUN -> layanan(level, anchor, NpcRole.PENENUN_SUTRA, "penenun", GerakId.TENUN, 0);
				case KEBUN -> layanan(level, anchor, NpcRole.PETANI, "kurma", GerakId.MEMANEN, PropId.KERANJANG);
				case BEA -> Bea.dekat(level, anchor, within);
				case GERBANG -> BadaiPasir.gerbangDekat(level, anchor);
				case PANGGUNG -> TariPutar.panggungDekat(level, anchor, within);
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Oasis anchor {} failed", what, exception);
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		OasisKota.lupakan(anchor);
		Entity made = buatan.remove(anchor.getUUID());
		lepas(made);
		List<Entity> many = buatanBanyak.remove(anchor.getUUID());
		if (many != null) {
			for (Entity e : many) {
				if (!(e instanceof UntaKafilahEntity unta && unta.disewa())) {
					lepas(e);
				}
			}
		}
		switch (anchor.what()) {
			case BAZAR -> Tawar.bazarLepas(anchor);
			case BEA -> Bea.lepas(anchor);
			case GERBANG -> BadaiPasir.gerbangLepas(anchor);
			case KANDANG -> KafilahUnta.kandangLepas(anchor);
			case PANGGUNG -> TariPutar.bubar(anchor);
			default -> {
			}
		}
	}

	private static void lepas(@Nullable Entity e) {
		if (e == null || e.isRemoved()) {
			return;
		}
		if (e instanceof KultivatorNpc npc) {
			npc.vanish();
		} else {
			TemaMekanisme.lepas(e);
		}
	}

	// ================================================================== bodies

	/** The body this anchor made, or null. */
	@Nullable Entity buatan(DuniaAnchorEntity anchor) {
		Entity e = buatan.get(anchor.getUUID());
		return e == null || e.isRemoved() ? null : e;
	}

	/** The named character of a service lot (Shui Lian, Sha Wuyan, Bulat Khan, Mo Hua, Ajun). */
	private void tokoh(ServerLevel level, DuniaAnchorEntity anchor) {
		OasisTokoh.Data data = OasisTokoh.diJangkar(anchor.what());
		if (data == null || buatan(anchor) != null) {
			return;
		}
		// Bulat Khan walks with the caravan while it is in town; Ajun is not at the camp while lost in a storm
		long situs = situsOf(level, anchor);
		if (OasisTokoh.BULAT_KHAN.equals(data.id()) && KafilahUnta.berjalan(situs)) {
			return;
		}
		KultivatorNpc npc = OasisTokoh.panggil(level, situs, data.id(), anchor.blockPosition(), anchor.getYRot());
		if (npc != null) {
			npc.memory().putBoolean("hold", !OasisTokoh.AJUN.equals(data.id()));
			switch (data.id()) {
				case OasisTokoh.SHUI_LIAN -> SosokApi.gerak(npc, GerakId.SIKAP_DOA, 0, 0, PropId.KENDI, 0, 0);
				case OasisTokoh.MO_HUA -> SosokApi.gerak(npc, GerakId.LUKIS_DINDING, 0, 0, PropId.KUAS_BESAR, PropId.PALET, 0);
				case OasisTokoh.SHA_WUYAN -> SosokApi.gerak(npc, GerakId.DUDUK_KURSI, 0, 0);
				case OasisTokoh.BULAT_KHAN -> SosokApi.gerak(npc, GerakId.HITUNG_SEMPOA, 0, 0, PropId.SEMPOA, 0, 0);
				default -> {
				}
			}
			buatan.put(anchor.getUUID(), npc);
		}
	}

	/** After his redemption (town flag {@code xie_pertapa}) Xie Duo sits as a hermit at the twelfth niche. */
	private void xieDiTebing(ServerLevel level, DuniaAnchorEntity anchor) {
		long situs = situsOf(level, anchor);
		if (situs == 0L || !OasisKota.kota(level.getServer(), situs).getBooleanOr("xie_pertapa", false)) {
			return;
		}
		List<Entity> many = buatanBanyak.computeIfAbsent(anchor.getUUID(), k -> new ArrayList<>());
		many.removeIf(Entity::isRemoved);
		if (!many.isEmpty()) {
			return;
		}
		BlockPos at = anchor.blockPosition();
		KultivatorNpc xie = OasisTokoh.panggil(level, situs, OasisTokoh.XIE_DUO, at, anchor.getYRot());
		if (xie != null) {
			xie.memory().putBoolean("hold", true);
			xie.memory().putBoolean("pertapa", true);
			SosokApi.gerak(xie, GerakId.MEDITASI_NAPAS, 0, 0);
			many.add(xie);
		}
	}

	/** A plain service body tagged with its stall kind {@code oasis_kios}. */
	private void layanan(ServerLevel level, DuniaAnchorEntity anchor, NpcRole peran, String kios, int klip, int prop) {
		if (buatan(anchor) != null) {
			return;
		}
		KultivatorNpc npc = HidupApi.tubuh(level, situsOf(level, anchor), peran, anchor.blockPosition(), anchor.getYRot());
		if (npc != null) {
			OasisTokoh.Data tokoh = OasisTokoh.diJangkar(anchor.what());
			if (tokoh != null) {
				// a service lot kept by a named character (the weaver's house: Nenek Gulnar)
				OasisTokoh.tandai(npc, tokoh.id());
			}
			npc.memory().putString(Tawar.KIOS, kios);
			npc.memory().putBoolean("hold", true);
			SosokApi.gerak(npc, klip, 0, 0, prop, 0, 0);
			buatan.put(anchor.getUUID(), npc);
		}
	}

	/** A themed mechanism entity at the anchor (bound to it), once, within 38TK inti's caps. */
	private <T extends TemaMekanismeEntity> void mekanisme(ServerLevel level, DuniaAnchorEntity anchor, EntityType<T> type, Consumer<Entity> setup) {
		if (buatan(anchor) != null) {
			return;
		}
		TemaKota.Kota kota = TemaKota.dari(level.getServer(), situsOf(level, anchor));
		T e = TemaMekanisme.muncul(level, type, anchor.position(), anchor.getYRot(), kota, anchor.blockPosition());
		if (e == null) {
			return;
		}
		setup.accept(e);
		buatan.put(anchor.getUUID(), e);
	}

	/** The town id an anchor belongs to (its town, else the anchor's own published site, else 0). */
	static long situsOf(ServerLevel level, DuniaAnchorEntity anchor) {
		OasisKota.Hidup kota = OasisKota.terdekat(level, anchor.position(), OasisKota.JANGKAU_KOTA);
		if (kota != null) {
			return kota.situs;
		}
		return anchor.site();
	}

	/** An int value of the anchor data (template strings). */
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
}
