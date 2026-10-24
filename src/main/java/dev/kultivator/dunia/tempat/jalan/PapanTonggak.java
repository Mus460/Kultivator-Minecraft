package dev.kultivator.dunia.tempat.jalan;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.tempat.TempatNames;
import dev.kultivator.dunia.tempat.kampung.KampungNames;
import dev.kultivator.dunia.tempat.kotabesar.KotaBesarNames;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The carved text over a milestone (DESIGN 38.C.6.3 "← place 1.2 li / 600 b →", 38.U.4 world text). When a player walks
 * within {@link #JARAK} blocks of a {@code pos/tonggak} anchor the server sends him the stela's board once
 * ({@link JalanNet.Papan}, again after {@link #ULANG} ticks): the road's name, the place at each end with its distance in
 * li and the road's heading toward it at the stela, the next rest post or caravanserai ahead, and the stela's carved
 * couplet ({@code kultivator.jalan.tonggak.ukir.<0-23>}, by hash of the site). The client turns the arrows toward the
 * viewer and draws it on a stone panel. Pure reads of the seed road plan and the published sites; the board of a stela
 * is computed once and cached ({@link #CACHE} entries). Touching the stone ({@link Tonggak#sentuh}) is unchanged.
 */
public final class PapanTonggak {

	/** Distance at which the board is sent (blocks; the client draws it within 24, see {@code TeksDunia}). */
	public static final double JARAK = 28.0D;
	/** Resend interval per player and stela (ticks; the client forgets boards it has walked away from). */
	public static final long ULANG = 2400L;
	public static final int UKIRAN = 24;
	private static final int CACHE = 96;
	private static final int MAKS_INGAT = 128;
	/** Height of the board's centre above the anchor for the 38W stelae (the {@code papan} anchor data overrides it). */
	private static final float TINGGI = 6.9F;

	private static final Map<Long, JalanNet.Papan> PAPAN = new LinkedHashMap<>(CACHE, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, JalanNet.Papan> eldest) {
			return size() > CACHE;
		}
	};
	private static final Map<UUID, Map<Long, Long>> TERKIRIM = new HashMap<>();

	/** The anchor's nearby tick: send the board to players who came close and have not had it lately. */
	static void dekat(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		long now = level.getGameTime();
		long site = anchor.siteOf("pos/tonggak");
		JalanNet.Papan board = null;
		for (ServerPlayer player : within) {
			if (player.distanceToSqr(anchor) > JARAK * JARAK) {
				continue;
			}
			Map<Long, Long> sent = TERKIRIM.computeIfAbsent(player.getUUID(), u -> new LinkedHashMap<>(16, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
					return size() > MAKS_INGAT;
				}
			});
			Long last = sent.get(site);
			if (last != null && now - last < ULANG && now >= last) {
				continue;
			}
			if (board == null) {
				board = papan(level, anchor, site);
			}
			sent.put(site, now);
			JalanNet.send(player, board);
		}
	}

	/** Sends the board of the milestone anchor at once (test command {@code uji jalan pos tonggak}). */
	static void kirimSekarang(ServerLevel level, DuniaAnchorEntity anchor, ServerPlayer player) {
		long site = anchor.siteOf("pos/tonggak");
		JalanNet.send(player, papan(level, anchor, site));
		TERKIRIM.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(site, level.getGameTime());
	}

	static void lupa(UUID player) {
		TERKIRIM.remove(player);
	}

	static void reset() {
		PAPAN.clear();
		TERKIRIM.clear();
	}

	private static JalanNet.Papan papan(ServerLevel level, DuniaAnchorEntity anchor, long site) {
		JalanNet.Papan cached = PAPAN.get(site);
		if (cached != null) {
			return cached;
		}
		BlockPos at = anchor.blockPosition();
		float tinggi = TINGGI;
		try {
			tinggi = Float.parseFloat(anchor.data().getStringOr("papan", Float.toString(TINGGI)));
		} catch (NumberFormatException ignored) {
			// keep the default height
		}
		Component ukiran = Component.translatable("kultivator.jalan.tonggak.ukir." + Math.floorMod(mix(site), UKIRAN));
		ChunkGeneratorStructureState state = RencanaJalan.of(level);
		RencanaJalan.Sisi e = RencanaJalan.terdekat(state, at.getX(), at.getZ(), 64);
		JalanNet.Papan board;
		if (e == null) {
			board = new JalanNet.Papan(at, tinggi, SimpulJalan.DESA, Component.translatable("kultivator.jalan.papan.tanpa_jalan"),
					List.of(), ukiran);
		} else {
			double[] hit = letak(e, at.getX(), at.getZ());
			double along = hit[0];
			float maju = (float) Math.atan2(hit[2], hit[1]);
			float mundur = (float) Math.atan2(-hit[2], -hit[1]);
			double total = e.panjang();
			List<JalanNet.Arah> arah = new ArrayList<>(3);
			arah.add(new JalanNet.Arah(namaUjung(level, e.dari()), (float) (along / Tonggak.LI), mundur, false));
			arah.add(new JalanNet.Arah(namaUjung(level, e.ke()), (float) ((total - along) / Tonggak.LI), maju, false));
			RencanaJalan.Pos next = null;
			RencanaJalan.Pos prev = null;
			int ownX = Math.floorDiv((e.xs()[0] + e.xs()[e.xs().length - 1]) / 2, RencanaJalan.SEL);
			int ownZ = Math.floorDiv((e.zs()[0] + e.zs()[e.zs().length - 1]) / 2, RencanaJalan.SEL);
			for (RencanaJalan.Pos p : RencanaJalan.posSel(state, ownX, ownZ)) {
				if (p.sisiId() != e.id() || !(p.jenis().startsWith("singgah") || p.jenis().equals("dagang"))) {
					continue;
				}
				if (p.jarak() > along && (next == null || p.jarak() < next.jarak())) {
					next = p;
				} else if (p.jarak() <= along && (prev == null || p.jarak() > prev.jarak())) {
					prev = p;
				}
			}
			// the nearer rest post of the two directions is carved under the places
			RencanaJalan.Pos rest = next;
			boolean ahead = true;
			if (prev != null && (next == null || along - prev.jarak() < next.jarak() - along)) {
				rest = prev;
				ahead = false;
			}
			if (rest != null) {
				long postSite = DuniaSites.idOf(level.dimension(), new BlockPos(rest.x(), 0, rest.z()), "pos/" + rest.jenis());
				float li = (float) (Math.abs(rest.jarak() - along) / Tonggak.LI);
				arah.add(new JalanNet.Arah(TempatNames.pos(rest.jenis(), postSite), li, ahead ? maju : mundur, true));
			}
			board = new JalanNet.Papan(at, tinggi, e.kelas(), TempatNames.jalan(e.id(), e.kelas()), List.copyOf(arah), ukiran);
		}
		PAPAN.put(site, board);
		return board;
	}

	/**
	 * Where {@code (x, z)} lies on the road: {distance along the polyline, unit heading x, unit heading z} of the
	 * segment nearest to the point (heading from {@code dari} toward {@code ke}).
	 */
	static double[] letak(RencanaJalan.Sisi e, int x, int z) {
		int[] xs = e.xs();
		int[] zs = e.zs();
		double walked = 0.0D;
		double bestD = Double.MAX_VALUE;
		double[] best = {0.0D, 1.0D, 0.0D};
		for (int i = 1; i < xs.length; i++) {
			double vx = xs[i] - xs[i - 1];
			double vz = zs[i] - zs[i - 1];
			double len = Math.hypot(vx, vz);
			double d = RencanaJalan.segDist2(x, z, xs[i - 1], zs[i - 1], xs[i], zs[i]);
			if (d < bestD && len > 1.0E-6D) {
				bestD = d;
				double t = Math.clamp(((x - xs[i - 1]) * vx + (z - zs[i - 1]) * vz) / (len * len), 0.0D, 1.0D);
				best = new double[] {walked + t * len, vx / len, vz / len};
			}
			walked += len;
		}
		return best;
	}

	/**
	 * The name of a road end as a traveller would carve it: the sect's name at a sect gate, the capital's name, the
	 * themed town's name, else the name of the nearest published place of that kind around the road end (a town, a
	 * village, a clan village); a place nobody has seen yet keeps its kind ("Kota Kultivator", "kampung").
	 */
	static Component namaUjung(ServerLevel level, SimpulJalan end) {
		long seed = level.getServer().overworld().getSeed();
		BlockPos p = new BlockPos(end.x(), level.getSeaLevel(), end.z());
		switch (end.jenis()) {
			case "sekte" -> {
				return Tonggak.namaUjung(level, end);
			}
			case "kota_besar" -> {
				return KotaBesarNames.nama(seed, (int) end.kunci());
			}
			case "kota_tema" -> {
				TemaKota.Kota kota = TemaKota.terdekat(level, p, null, 320);
				if (kota != null) {
					return kota.nama(seed);
				}
			}
			case "kota" -> {
				for (DuniaSites.Site s : DuniaSites.near(level, p, 160, "kota/kota")) {
					if (s.kind().equals("kota/kota")) {
						return KotaNames.town(s.id());
					}
				}
			}
			case "kampung", "klan" -> {
				for (DuniaSites.Site s : DuniaSites.near(level, p, 160, "kampung/")) {
					return KampungNames.nama(s.kind().substring("kampung/".length()), s.id());
				}
			}
			default -> {
			}
		}
		return Component.translatable("kultivator.jalan.ujung." + end.jenis());
	}

	private static long mix(long z) {
		z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
		z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
		return z ^ (z >>> 33);
	}

	private PapanTonggak() {
	}
}
