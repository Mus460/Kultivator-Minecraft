package dev.kultivator.dunia.tempat.tema.gunung;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The anchors of {@code tools/gen_tempat_tema_gunung.py} (module {@value #MODULE} of {@code DuniaAnchors}; the shared kinds
 * {@code kota/*}, {@code kotabesar/*} and {@code kota_tema/*} of the same templates are served by their owners). Data
 * values are template strings, read with defaults.
 * <pre>
 * tema_gunung/peran        a service body: peran S (NpcRole name)
 * tema_gunung/tokoh        a named character: tokoh S, peran S (Shan Ling in the cliff district)
 * tema_gunung/lift         a cable-car station: stasiun S bawah|atas (Paman Tie Suo at the lower one), peran S
 * tema_gunung/roda         a guide wheel (entity roda_katrol): besar I 1 = the 9-block drive wheel, katrol S (pulley)
 * tema_gunung/tiang        the foot of the Tiang Tengah (a lift keeper; the night watch of chain stage 2)
 * tema_gunung/katrol       a pulley basket (entity katrol): nama S teras|tebing, naik I blocks, keluar S exit side
 * tema_gunung/bordes       pilgrim landing no I 1-9 (incense of the Seribu Anak Tangga)
 * tema_gunung/ziarah       the pilgrims' desk of the Kuil Kaki (tema_ziarah; Pei Wenzhi waits here)
 * tema_gunung/kuli         porters: jenis S kaki (gate) | pos (the Pos Kuli: Lao Dan, porter service)
 * tema_gunung/aula_herba   the herb hall counter: Nenek Yao (tema_herba)
 * tema_gunung/aula_emas    the Aula Emas: Biksuni Jing Yue
 * tema_gunung/lonceng_awan the bell tower of the Lonceng Awan (bell monk)
 * tema_gunung/biara        the Aula Dharma: Kepala Biara Jue Yuan
 * tema_gunung/titipan      a delivery room of the porter service: tempat S biara|aula_emas
 * tema_gunung/ceruk        a meditation niche of the plank path: no I 1-6 (spirit snares of chain stage 3)
 * tema_gunung/herba        a cliff herb: no I 1-12 (entity herba_tebing)
 * tema_gunung/kera         a monkey haunt of the top row: jumlah I
 * tema_gunung/sarang       the crag niche of the Kera Putih Tua (chain stage 4)
 * tema_gunung/palang       the foot of the plank path: the chain barrier closed in rain
 * </pre>
 * Bodies and mechanisms are made when a player comes within {@link #radius()} blocks and removed when the anchor
 * unloads; mechanisms through {@link TemaMekanisme#muncul} (town and world caps, {@code mekanisme_tema}).
 */
public final class GunungAnchors implements AnchorHandler {

	public static final String MODULE = "tema_gunung";

	public static final String PERAN = "peran";
	public static final String TOKOH = "tokoh";
	public static final String LIFT = "lift";
	public static final String RODA = "roda";
	public static final String TIANG = "tiang";
	public static final String KATROL = "katrol";
	public static final String BORDES = "bordes";
	public static final String ZIARAH = "ziarah";
	public static final String KULI = "kuli";
	public static final String AULA_HERBA = "aula_herba";
	public static final String AULA_EMAS = "aula_emas";
	public static final String LONCENG = "lonceng_awan";
	public static final String BIARA = "biara";
	public static final String TITIPAN = "titipan";
	public static final String CERUK = "ceruk";
	public static final String HERBA = "herba";
	public static final String KERA = "kera";
	public static final String SARANG = "sarang";
	public static final String PALANG = "palang";

	static final GunungAnchors HANDLER = new GunungAnchors();

	/** Bodies made per anchor (anchor uuid -> entities). */
	private final Map<UUID, List<Entity>> buatan = new HashMap<>();
	/** Loaded anchors by kind (for the systems: stations, landings, niches, herbs, haunts). */
	private final Map<String, List<DuniaAnchorEntity>> dimuat = new HashMap<>();

	static void init() {
		DuniaAnchors.register(MODULE, HANDLER);
	}

	static void reset() {
		HANDLER.buatan.clear();
		HANDLER.dimuat.clear();
	}

	@Override
	public int radius() {
		return 64;
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		List<DuniaAnchorEntity> list = dimuat.computeIfAbsent(anchor.what(), k -> new ArrayList<>());
		if (!list.contains(anchor)) {
			list.add(anchor);
		}
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!TemaConfig.inti().aktif || !TemaConfig.aktif(dev.kultivator.dunia.tempat.tema.Tema.GUNUNG)) {
			return;
		}
		String what = anchor.what();
		try {
			switch (what) {
				case PERAN -> peran(level, anchor, peranDari(anchor, NpcRole.WARGA), 32);
				case TOKOH -> tokoh(level, anchor, anchor.data().getStringOr("tokoh", ""));
				case LIFT -> {
					if ("bawah".equals(anchor.data().getStringOr("stasiun", ""))) {
						tokoh(level, anchor, GunungTokoh.TIE_SUO);
					} else {
						peran(level, anchor, NpcRole.PENJAGA_LIFT, 40);
					}
					KeretaGantung.dekatStasiun(level, anchor, within);
					GunungCerita.dekatStasiun(level, anchor, within);
				}
				case RODA -> mekanisme(level, anchor, TemaEntities.RODA_KATROL, e -> ((RodaKatrolEntity) e)
						.setVarian(angka(anchor, "besar", 0) == 1 ? RodaKatrolEntity.VARIAN_BESAR : RodaKatrolEntity.VARIAN_KECIL));
				case TIANG -> {
					peran(level, anchor, NpcRole.PENJAGA_LIFT, 40);
					GunungCerita.dekatTiang(level, anchor, within);
				}
				case KATROL -> {
					mekanisme(level, anchor, TemaEntities.KATROL, e -> ((KatrolEntity) e).siapkan(anchor));
					peranDiAtas(level, anchor);
				}
				case BORDES -> Ziarah.dekatBordes(level, anchor, within);
				case ZIARAH -> {
					peran(level, anchor, NpcRole.BIKSU_GUNUNG, 32);
					GunungCerita.dekatZiarah(level, anchor, within, this);
				}
				case KULI -> {
					if ("pos".equals(anchor.data().getStringOr("jenis", ""))) {
						tokoh(level, anchor, GunungTokoh.LAO_DAN);
						TemaKota.Kota kota = kota(anchor);
						if (kota != null && GunungData.kota(level.getServer(), kota.situsId()).getIntOr("gt", 0) == 1) {
							// chain stage 5 (a): Gou Tie works off his debt as a porter beside Lao Dan
							tamu(level, anchor, GunungTokoh.GOU_TIE, anchor.blockPosition().offset(2, 0, 1));
						}
					} else {
						peran(level, anchor, NpcRole.KULI_GUNUNG, 32);
					}
				}
				case AULA_HERBA -> tokoh(level, anchor, GunungTokoh.YAO_PO);
				case AULA_EMAS -> {
					tokoh(level, anchor, GunungTokoh.JING_YUE);
					Ziarah.dekatAulaEmas(level, anchor, within);
				}
				case LONCENG -> peran(level, anchor, NpcRole.BIKSU_GUNUNG, 40);
				case BIARA -> tokoh(level, anchor, GunungTokoh.JUE_YUAN);
				case TITIPAN -> {
					peran(level, anchor, anchor.data().getStringOr("tempat", "").equals("biara") ? NpcRole.BIKSU_GUNUNG : NpcRole.PELAYAN, 32);
					JasaKuli.dekatTitipan(level, anchor, within);
				}
				case CERUK -> GunungCerita.dekatCeruk(level, anchor, within);
				case HERBA -> mekanisme(level, anchor, TemaEntities.HERBA_TEBING, e -> ((HerbaTebingEntity) e).siapkan(anchor));
				case KERA -> KeraGunungEntity.dekatSarang(level, anchor, within, daftar(anchor));
				case SARANG -> GunungCerita.dekatSarang(level, anchor, within);
				case PALANG -> GunungAcara.dekatPalang(level, anchor, within, daftar(anchor));
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Kota Gunung anchor {} failed", what, exception);
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		List<DuniaAnchorEntity> list = dimuat.get(anchor.what());
		if (list != null) {
			list.remove(anchor);
		}
		List<Entity> made = buatan.remove(anchor.getUUID());
		if (made != null) {
			for (Entity e : made) {
				lepas(e);
			}
		}
		if (LIFT.equals(anchor.what())) {
			KeretaGantung.stasiunLepas(level, anchor);
		}
		GunungCerita.jangkarLepas(level, anchor);
	}

	// ================================================================== queries for the systems

	/** Loaded anchors of kind {@code what} (live list copy). */
	public static List<DuniaAnchorEntity> dimuat(String what) {
		List<DuniaAnchorEntity> list = HANDLER.dimuat.get(what);
		if (list == null) {
			return List.of();
		}
		list.removeIf(Entity::isRemoved);
		return List.copyOf(list);
	}

	/** The bodies an anchor made (live, may be edited by the caller). */
	List<Entity> daftar(DuniaAnchorEntity anchor) {
		List<Entity> list = buatan.computeIfAbsent(anchor.getUUID(), k -> new ArrayList<>());
		list.removeIf(Entity::isRemoved);
		return list;
	}

	/** The first live body of an anchor (its keeper or mechanism), or null. */
	public static @Nullable Entity tubuh(DuniaAnchorEntity anchor) {
		List<Entity> list = HANDLER.buatan.get(anchor.getUUID());
		if (list == null) {
			return null;
		}
		for (Entity e : list) {
			if (!e.isRemoved()) {
				return e;
			}
		}
		return null;
	}

	/** The town an anchor stands in (plan lookup), or null. */
	public static @Nullable TemaKota.Kota kota(DuniaAnchorEntity anchor) {
		return anchor.level() instanceof ServerLevel level ? GunungTata.kotaDi(level, anchor.blockPosition()) : null;
	}

	/** The Nama Baik / NPC town id of an anchor (38KS town), else the themed town's site id. */
	public static long kotaId(ServerLevel level, DuniaAnchorEntity anchor) {
		long town = Towns.townOf(anchor);
		if (town != 0L) {
			return town;
		}
		TemaKota.Kota kota = kota(anchor);
		return kota == null ? anchor.site() : kota.situsId();
	}

	/** The town id for the NPC of a position (38KS town if known). */
	public static long kotaId(ServerLevel level, BlockPos pos) {
		long town = Towns.within(level, pos);
		if (town != 0L) {
			return town;
		}
		TemaKota.Kota kota = GunungTata.kotaDi(level, pos);
		return kota == null ? 0L : kota.situsId();
	}

	// ================================================================== bodies

	private void peran(ServerLevel level, DuniaAnchorEntity anchor, NpcRole role, int jarak) {
		List<Entity> list = daftar(anchor);
		if (!list.isEmpty() || !TemaMekanisme.pemainDekat(level, anchor.position(), jarak)) {
			return;
		}
		KultivatorNpc npc = HidupApi.tubuh(level, kotaId(level, anchor), role, anchor.blockPosition(), anchor.getYRot());
		if (npc != null) {
			npc.memory().putBoolean("hold", true);
			npc.memory().putString("tema", GunungData.TEMA);
			npc.memory().putString("gunung_jangkar", anchor.what());
			list.add(npc);
		}
	}

	private void tokoh(ServerLevel level, DuniaAnchorEntity anchor, String id) {
		GunungTokoh.Data data = GunungTokoh.byId(id);
		if (data == null) {
			peran(level, anchor, peranDari(anchor, NpcRole.WARGA), 32);
			return;
		}
		List<Entity> list = daftar(anchor);
		if (!list.isEmpty()) {
			return;
		}
		if (GunungTokoh.SHAN_LING.equals(id)) {
			TemaKota.Kota kota = kota(anchor);
			if (kota != null && GunungData.shanLingPergi(level.getServer(), kota.situsId())) {
				return;            // she left the town after the bribe of chain stage 5 (c)
			}
		}
		KultivatorNpc npc = GunungTokoh.panggil(level, kotaId(level, anchor), id, anchor.blockPosition(), anchor.getYRot());
		if (npc != null) {
			npc.memory().putBoolean("hold", !GunungTokoh.SHAN_LING.equals(id));
			npc.memory().putString("gunung_jangkar", anchor.what());
			list.add(npc);
		}
	}

	/** A body that is not the anchor's first one (a guest such as Pei Wenzhi); kept in the anchor's list. */
	@Nullable KultivatorNpc tamu(ServerLevel level, DuniaAnchorEntity anchor, String tokohId, BlockPos pos) {
		List<Entity> list = daftar(anchor);
		for (Entity e : list) {
			if (e instanceof KultivatorNpc npc && GunungTokoh.adalah(npc, tokohId)) {
				return npc;
			}
		}
		KultivatorNpc npc = GunungTokoh.panggil(level, kotaId(level, anchor), tokohId, pos, anchor.getYRot());
		if (npc != null) {
			npc.memory().putBoolean("hold", true);
			list.add(npc);
		}
		return npc;
	}

	/** The pulley keeper stands at the windlass on the upper landing. */
	private void peranDiAtas(ServerLevel level, DuniaAnchorEntity anchor) {
		List<Entity> list = daftar(anchor);
		for (Entity e : list) {
			if (e instanceof KultivatorNpc) {
				return;
			}
		}
		if (!TemaMekanisme.pemainDekat(level, anchor.position(), 40)) {
			return;
		}
		int naik = angka(anchor, "naik", 12);
		BlockPos top = anchor.blockPosition().above(naik);
		Vec3 out = KatrolEntity.keluarArah(anchor);
		BlockPos at = BlockPos.containing(top.getX() + 0.5D + out.x * 2.0D + out.z, top.getY(), top.getZ() + 0.5D + out.z * 2.0D + out.x);
		KultivatorNpc npc = HidupApi.tubuh(level, kotaId(level, anchor), NpcRole.PENJAGA_LIFT, at, anchor.getYRot());
		if (npc != null) {
			npc.memory().putBoolean("hold", true);
			npc.memory().putString("tema", GunungData.TEMA);
			npc.memory().putString("gunung_jangkar", KATROL);
			list.add(npc);
		}
	}

	/** A themed mechanism at the anchor, once, within the caps (TemaMekanisme). */
	private <T extends Entity> void mekanisme(ServerLevel level, DuniaAnchorEntity anchor, EntityType<T> type, java.util.function.Consumer<Entity> setup) {
		List<Entity> list = daftar(anchor);
		for (Entity e : list) {
			if (e.getType() == type) {
				return;
			}
		}
		TemaKota.Kota kota = kota(anchor);
		Vec3 at = anchor.position();
		T e = TemaMekanisme.muncul(level, type, at, anchor.getYRot(), kota, anchor.blockPosition());
		if (e != null) {
			setup.accept(e);
			list.add(e);
		}
	}

	static void lepas(@Nullable Entity e) {
		if (e == null || e.isRemoved()) {
			return;
		}
		if (e instanceof KultivatorNpc npc) {
			npc.vanish();
		} else if (e instanceof TemaMekanismeEntity m) {
			TemaMekanisme.lepas(m);
		} else {
			e.discard();
		}
	}

	// ================================================================== data helpers

	static NpcRole peranDari(DuniaAnchorEntity anchor, NpcRole fallback) {
		String name = anchor.data().getStringOr("peran", "");
		if (!name.isEmpty()) {
			try {
				return NpcRole.valueOf(name);
			} catch (IllegalArgumentException ignored) {
				// unknown role in data: fall back
			}
		}
		return fallback;
	}

	/** An int value of the anchor data (template strings or ints). */
	public static int angka(DuniaAnchorEntity anchor, String key, int fallback) {
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

	private GunungAnchors() {
	}
}
