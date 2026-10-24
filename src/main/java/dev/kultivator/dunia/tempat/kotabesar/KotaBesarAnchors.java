package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.LapakSekte;
import dev.kultivator.dunia.tempat.ekonomi.LelangAgung;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The anchor handler of module {@code kotabesar} (DESIGN 38.B.2.6; one handler per module in {@code DuniaAnchors}). It
 * keeps a live picture of every loaded capital ({@link KotaHidup}: drum, bell, stages, cells, discs, docks, service spots)
 * that the civil services read, makes the gate leaves ({@code gerbang}) the moment their chunk loads, and brings the
 * service bodies ({@code layanan}, role from {@code data.peran}) through {@link HidupApi#tubuh} when a player comes near
 * (at most {@code kotaBesar.tubuhMaks} per capital; a body that died returns after five minutes).
 * <p>
 * Other modules serve their own kinds through {@link #daftar} (one forwarder per "what"): 38EK {@code lapak_sekte} and the
 * Grand Auction stage, 38PR {@code tukang}, 38MD / 38EK {@code rak_senjata}. Never call
 * {@code DuniaAnchors.register("kotabesar", ...)} elsewhere: it would replace this handler.
 */
public final class KotaBesarAnchors implements AnchorHandler {

	static final KotaBesarAnchors HANDLER = new KotaBesarAnchors();
	/** A slain service body returns after this many ticks (5 minutes). */
	static final int KEMBALI_TICK = 6000;
	/** A service body farther than this from every player may make room for a nearer one when the cap is full. */
	static final double LEPAS_JARAK = 160.0D;
	/** Service bodies come when a player is within this ({@link #radius}); everything else keeps {@link #DEKAT}. */
	static final int JARAK_TUBUH = 128;
	/** The range of the gates, stalls, stages and forwarders (the handler's radius before the bodies reached 128). */
	static final double DEKAT = 56.0D;

	private static final Map<String, AnchorHandler> TAMBAHAN = new HashMap<>();
	/** Live capitals by town id. */
	private static final Map<Long, KotaHidup> KOTA = new HashMap<>();

	private final Map<UUID, GerbangKotaEntity> gerbang = new HashMap<>();
	private final Map<UUID, KultivatorNpc> tubuh = new HashMap<>();
	private final Map<UUID, Long> kembaliPada = new HashMap<>();

	/**
	 * The loaded parts of one capital. Lists hold anchors of the loaded chunks only; positions are world blocks.
	 */
	public static final class KotaHidup {
		public final long town;
		public final ResourceKey<Level> dimensi;
		public @Nullable Kandidat kandidat;
		public @Nullable BlockPos genderang;
		public @Nullable BlockPos lonceng;
		/** The great incense burner of the Temple of Heaven ({@code kotabesar/kuil}). */
		public @Nullable BlockPos kuil;
		/** The armillary deck on the Sky Observatory's top ({@code kotabesar/pengamat}). */
		public @Nullable BlockPos pengamat;
		public final Map<String, List<DuniaAnchorEntity>> panggung = new HashMap<>();
		public final List<DuniaAnchorEntity> sel = new ArrayList<>();
		public final List<DuniaAnchorEntity> cakram = new ArrayList<>();
		public final List<DuniaAnchorEntity> dermaga = new ArrayList<>();
		public final List<DuniaAnchorEntity> layanan = new ArrayList<>();
		public final List<GerbangKotaEntity> gerbang = new ArrayList<>();

		KotaHidup(long town, ResourceKey<Level> dimensi) {
			this.town = town;
			this.dimensi = dimensi;
		}

		/** The first loaded stage anchor of event {@code acara}, or null. */
		public @Nullable DuniaAnchorEntity panggung(String acara) {
			List<DuniaAnchorEntity> list = panggung.get(acara);
			if (list == null) {
				return null;
			}
			for (DuniaAnchorEntity a : list) {
				if (!a.isRemoved()) {
					return a;
				}
			}
			return null;
		}

		/** The first loaded service anchor of {@code jenis}, or null. */
		public @Nullable DuniaAnchorEntity layanan(String jenis) {
			for (DuniaAnchorEntity a : layanan) {
				if (!a.isRemoved() && jenis.equals(a.data().getStringOr("layanan", ""))) {
					return a;
				}
			}
			return null;
		}

		boolean kosong() {
			return genderang == null && lonceng == null && kuil == null && pengamat == null && panggung.isEmpty() && sel.isEmpty() && cakram.isEmpty()
					&& dermaga.isEmpty() && layanan.isEmpty() && gerbang.isEmpty();
		}
	}

	/** Registers the forwarder of anchors {@code kotabesar/<what>} for another module (replaces an earlier one). */
	public static void daftar(String what, AnchorHandler handler) {
		if (what != null && handler != null) {
			TAMBAHAN.put(what, handler);
		}
	}

	/** The live capital of town {@code town}, or null when none of its chunks is loaded. */
	public static @Nullable KotaHidup kota(long town) {
		return KOTA.get(town);
	}

	/** Every loaded capital. */
	public static List<KotaHidup> semua() {
		return new ArrayList<>(KOTA.values());
	}

	/** The loaded gates (leaf entities) of every capital. */
	public static List<GerbangKotaEntity> gerbang() {
		List<GerbangKotaEntity> out = new ArrayList<>();
		for (GerbangKotaEntity g : HANDLER.gerbang.values()) {
			if (!g.isRemoved()) {
				out.add(g);
			}
		}
		return out;
	}

	/** The live service body an anchor brought, or null. */
	public static @Nullable KultivatorNpc tubuhDari(DuniaAnchorEntity anchor) {
		KultivatorNpc npc = HANDLER.tubuh.get(anchor.getUUID());
		return npc != null && npc.isAlive() && !npc.isRemoved() ? npc : null;
	}

	/**
	 * {@code uji hidup audit}: why the service anchor has no body now (suffix of {@code kultivator.hidup.uji.audit.alasan.*}),
	 * or null when its body stands.
	 */
	public static @Nullable String alasanKosong(ServerLevel level, DuniaAnchorEntity anchor) {
		if (tubuhDari(anchor) != null) {
			return null;
		}
		if (!DuniaSpine.enabled()) {
			return "dunia_mati";
		}
		if (!KotaBesarConfig.hidup()) {
			return "saklar";
		}
		if (peran(anchor.data().getStringOr("peran", "")) == null) {
			return "peran";
		}
		if (level.getGameTime() < HANDLER.kembaliPada.getOrDefault(anchor.getUUID(), 0L)) {
			return "mati";
		}
		if (DuniaAnchors.playersNear(level, anchor.position(), JARAK_TUBUH).isEmpty()) {
			return "jauh";
		}
		if (tubuhHidup(Towns.townOf(anchor)) >= KotaBesarConfig.get().tubuhMaks) {
			return "topi_kota";
		}
		return "menunggu";
	}

	/** How many service bodies of town {@code town} are alive now. */
	static int tubuhHidup(long town) {
		int n = 0;
		for (KultivatorNpc npc : HANDLER.tubuh.values()) {
			if (npc.isAlive() && !npc.isRemoved() && npc.town() == town) {
				n++;
			}
		}
		return n;
	}

	/** Server stopped: forget everything. */
	static void lupakan() {
		KOTA.clear();
		HANDLER.gerbang.clear();
		HANDLER.tubuh.clear();
		HANDLER.kembaliPada.clear();
	}

	// ================================================================== AnchorHandler

	@Override
	public int radius() {
		return JARAK_TUBUH;
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		String what = anchor.what();
		AnchorHandler extra = TAMBAHAN.get(what);
		if (extra != null) {
			try {
				extra.loaded(level, anchor);
			} catch (RuntimeException e) {
				Kultivator.LOGGER.error("Kota Besar anchor forwarder {} failed on load", what, e);
			}
		}
		long town = Towns.townOf(anchor);
		if (town == 0L) {
			return;
		}
		KotaHidup kota = KOTA.computeIfAbsent(town, t -> new KotaHidup(t, level.dimension()));
		if (kota.kandidat == null) {
			kota.kandidat = KotaBesarKota.di(level, anchor.blockPosition());
			if (kota.kandidat != null) {
				Teleport.daftarRute(level.getServer(), kota.kandidat);
			}
		}
		switch (what) {
			case "genderang" -> kota.genderang = anchor.blockPosition();
			case "lonceng" -> kota.lonceng = anchor.blockPosition();
			case "kuil" -> kota.kuil = anchor.blockPosition();
			case "pengamat" -> kota.pengamat = anchor.blockPosition();
			case "panggung" -> kota.panggung.computeIfAbsent(anchor.data().getStringOr("acara", ""), k -> new ArrayList<>()).add(anchor);
			case "sel" -> kota.sel.add(anchor);
			case "cakram" -> kota.cakram.add(anchor);
			case "dermaga" -> kota.dermaga.add(anchor);
			case "layanan" -> kota.layanan.add(anchor);
			case "gerbang" -> buatGerbang(level, anchor, kota);
			default -> {
			}
		}
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> semua) {
		String what = anchor.what();
		if ("layanan".equals(what) && KotaBesarConfig.hidup()) {
			bawaTubuh(level, anchor, semua);
		}
		List<ServerPlayer> within = DuniaAnchors.dalam(semua, anchor, DEKAT);
		if (within.isEmpty()) {
			return;
		}
		AnchorHandler extra = TAMBAHAN.get(what);
		if (extra != null) {
			try {
				extra.nearby(level, anchor, within);
			} catch (RuntimeException e) {
				Kultivator.LOGGER.error("Kota Besar anchor forwarder {} failed", what, e);
			}
		}
		if (!KotaBesarConfig.hidup()) {
			return;
		}
		switch (what) {
			case "lapak_sekte" -> {
				if (extra == null) {
					LapakSekte.jangkarDekat(level, anchor, within);
				}
			}
			case "panggung" -> {
				if ("lelang".equals(anchor.data().getStringOr("acara", ""))) {
					LelangAgung.jangkarDekat(level, anchor, within);
				}
			}
			case "layanan" -> {
				if ("lelang_agung".equals(anchor.data().getStringOr("layanan", ""))) {
					LelangAgung.jangkarDekat(level, anchor, within);
				}
			}
			case "gerbang" -> {
				GerbangKotaEntity g = gerbang.get(anchor.getUUID());
				if (g == null || g.isRemoved()) {
					KotaHidup kota = KOTA.get(Towns.townOf(anchor));
					if (kota != null) {
						buatGerbang(level, anchor, kota);
					}
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		String what = anchor.what();
		AnchorHandler extra = TAMBAHAN.get(what);
		if (extra != null) {
			try {
				extra.unloaded(level, anchor);
			} catch (RuntimeException e) {
				Kultivator.LOGGER.error("Kota Besar anchor forwarder {} failed on unload", what, e);
			}
		}
		UUID id = anchor.getUUID();
		KultivatorNpc npc = tubuh.remove(id);
		if (npc != null && !npc.isRemoved()) {
			npc.discard();
		}
		kembaliPada.remove(id);
		GerbangKotaEntity g = gerbang.remove(id);
		if (g != null && !g.isRemoved()) {
			g.discard();
		}
		long town = Towns.townOf(anchor);
		KotaHidup kota = KOTA.get(town);
		if (kota == null) {
			return;
		}
		kota.gerbang.remove(g);
		kota.sel.remove(anchor);
		kota.cakram.remove(anchor);
		kota.dermaga.remove(anchor);
		kota.layanan.remove(anchor);
		for (List<DuniaAnchorEntity> list : kota.panggung.values()) {
			list.remove(anchor);
		}
		kota.panggung.values().removeIf(List::isEmpty);
		if ("genderang".equals(what) && anchor.blockPosition().equals(kota.genderang)) {
			kota.genderang = null;
		}
		if ("lonceng".equals(what) && anchor.blockPosition().equals(kota.lonceng)) {
			kota.lonceng = null;
		}
		if ("kuil".equals(what) && anchor.blockPosition().equals(kota.kuil)) {
			kota.kuil = null;
		}
		if ("pengamat".equals(what) && anchor.blockPosition().equals(kota.pengamat)) {
			kota.pengamat = null;
		}
		if (kota.kosong()) {
			KOTA.remove(town);
		}
	}

	// ================================================================== gates and bodies

	private void buatGerbang(ServerLevel level, DuniaAnchorEntity anchor, KotaHidup kota) {
		GerbangKotaEntity old = gerbang.get(anchor.getUUID());
		if (old != null && !old.isRemoved()) {
			return;
		}
		GerbangKotaEntity g = KotaBesarEntities.GERBANG_KOTA.create(level, EntitySpawnReason.EVENT);
		if (g == null) {
			return;
		}
		g.snapTo(anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getYRot(), 0.0F);
		boolean buka = JamMalam.harusBuka(level, kota.town, g);
		g.pasang(kota.town, anchor.data().getStringOr("nama", ""), anchor.data().getIntOr("lebar", 4),
				anchor.data().getIntOr("tinggi", 6), buka);
		if (level.addFreshEntity(g)) {
			gerbang.put(anchor.getUUID(), g);
			kota.gerbang.removeIf(lama -> lama.isRemoved());
			kota.gerbang.add(g);
		}
	}

	/** Brings the service body of a {@code kotabesar/layanan} anchor (role {@code data.peran}). */
	private void bawaTubuh(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		UUID id = anchor.getUUID();
		KultivatorNpc npc = tubuh.get(id);
		long now = level.getGameTime();
		if (npc != null) {
			if (npc.isAlive() && !npc.isRemoved()) {
				return;
			}
			tubuh.remove(id);
			if (npc.isDeadOrDying() || npc.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED) {
				kembaliPada.put(id, now + KEMBALI_TICK);
			}
		}
		if (now < kembaliPada.getOrDefault(id, 0L) || within.isEmpty()) {
			return;
		}
		NpcRole peran = peran(anchor.data().getStringOr("peran", ""));
		if (peran == null) {
			return;
		}
		long town = Towns.townOf(anchor);
		if (tubuhHidup(town) >= KotaBesarConfig.get().tubuhMaks && !lepasJauh(level, town)) {
			return;
		}
		KultivatorNpc made = HidupApi.tubuhStaf(level, town, peran, anchor.blockPosition(), anchor.getYRot());
		if (made != null) {
			made.memory().putString("kotabesar_layanan", anchor.data().getStringOr("layanan", ""));
			if (peran == NpcRole.PANGERAN_KOTA) {
				Kandidat k = KotaBesarKota.di(level, anchor.blockPosition());
				if (k != null) {
					made.setPlate(KotaBesarNames.pangeran(level.getSeed(), k.index()));
				}
			} else if (peran == NpcRole.HAKIM) {
				Kandidat k = KotaBesarKota.di(level, anchor.blockPosition());
				if (k != null) {
					made.setPlate(KotaBesarNames.hakim(level.getSeed(), k.index()));
				}
			}
			// the recurring character of the lot (38.S.2: Bibi Hong, Qian Duoduo...), one body per capital
			String tokoh = anchor.data().getStringOr("tokoh", "");
			if (!tokoh.isEmpty() && !adaTokoh(town, tokoh)) {
				CeritaApi.tandaiTokoh(made, tokoh);
			}
			tubuh.put(id, made);
		}
	}

	/** True when a live service body of {@code town} already stands for character {@code tokoh}. */
	private boolean adaTokoh(long town, String tokoh) {
		for (KultivatorNpc npc : tubuh.values()) {
			if (npc.isAlive() && !npc.isRemoved() && npc.town() == town && tokoh.equals(npc.memory().getStringOr("tokoh", ""))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Frees one place under {@code tubuhMaks}: bodies stay while their chunks are loaded, so without this a walk across
	 * the capital would fill the cap in the first districts and leave the later service halls empty. Removes one body of
	 * {@code town} with no player within {@link #LEPAS_JARAK} blocks (it returns when a player comes near again).
	 */
	private boolean lepasJauh(ServerLevel level, long town) {
		for (Map.Entry<UUID, KultivatorNpc> e : tubuh.entrySet()) {
			KultivatorNpc npc = e.getValue();
			if (npc.isAlive() && !npc.isRemoved() && npc.town() == town && npc.level() == level
					&& !level.hasNearbyAlivePlayer(npc.getX(), npc.getY(), npc.getZ(), LEPAS_JARAK)) {
				tubuh.remove(e.getKey());
				npc.discard();
				return true;
			}
		}
		return false;
	}

	static @Nullable NpcRole peran(String nama) {
		if (nama == null || nama.isEmpty()) {
			return null;
		}
		try {
			return NpcRole.valueOf(nama);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The cell points of a {@code kotabesar/sel} anchor: list of [in x,y,z, door x,y,z] offsets from the anchor block. */
	static List<int[]> titikSel(DuniaAnchorEntity anchor) {
		List<int[]> out = new ArrayList<>();
		ListTag pts = anchor.data().getListOrEmpty("pts");
		List<Integer> flat = new ArrayList<>();
		for (Tag t : pts) {
			flat.add(t.asInt().orElse(0));
		}
		for (int i = 0; i + 5 < flat.size(); i += 6) {
			out.add(new int[]{flat.get(i), flat.get(i + 1), flat.get(i + 2), flat.get(i + 3), flat.get(i + 4), flat.get(i + 5)});
		}
		return out;
	}

	/**
	 * World block of a template offset {@code (dx, dy, dz)} written next to {@code anchor} in the canonical frame (before
	 * the whole-city rotation {@code putar}); {@code dy} counts from the street floor (1 = standing on it).
	 */
	static BlockPos dari(DuniaAnchorEntity anchor, int putar, int dx, int dy, int dz) {
		int[] o = dev.kultivator.dunia.tempat.worldgen.TataLetakStructure.rotate(net.minecraft.world.level.block.Rotation.values()[putar & 3], dx, dz);
		BlockPos a = anchor.blockPosition();
		return new BlockPos(a.getX() + o[0], a.getY() + dy - 1, a.getZ() + o[1]);
	}

	/**
	 * The stage points of a {@code kotabesar/panggung} anchor (seats, stations, stalls, corners): {@code pts} triples
	 * {@code [dx, dy, dz]} in the canonical frame, all on the anchor's own level (the templates write {@code dy} equal to the
	 * anchor's height above its floor), rotated by the whole-city rotation.
	 */
	static List<BlockPos> titikPanggung(ServerLevel level, DuniaAnchorEntity anchor) {
		List<BlockPos> out = new ArrayList<>();
		int putar = putarDi(level, anchor);
		ListTag pts = anchor.data().getListOrEmpty("pts");
		List<Integer> flat = new ArrayList<>();
		for (Tag t : pts) {
			flat.add(t.asInt().orElse(0));
		}
		BlockPos a = anchor.blockPosition();
		for (int i = 0; i + 2 < flat.size(); i += 3) {
			int[] o = dev.kultivator.dunia.tempat.worldgen.TataLetakStructure.rotate(net.minecraft.world.level.block.Rotation.values()[putar & 3],
					flat.get(i), flat.get(i + 2));
			out.add(new BlockPos(a.getX() + o[0], a.getY(), a.getZ() + o[1]));
		}
		return out;
	}

	/** The whole-city rotation of the capital an anchor stands in (0 when unknown). */
	static int putarDi(ServerLevel level, DuniaAnchorEntity anchor) {
		Kandidat k = KotaBesarKota.di(level, anchor.blockPosition());
		return k == null ? 0 : k.putar();
	}

	private KotaBesarAnchors() {
	}
}
