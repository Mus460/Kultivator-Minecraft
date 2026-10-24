package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.PutusanTanah;
import dev.kultivator.dunia.tempat.TempatZones;
import dev.kultivator.dunia.tempat.tema.bersama.TemaCerita;
import dev.kultivator.util.Advancements;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one anchor handler of module {@code kota_tema} (DESIGN 38.T.2.4; {@code DuniaAnchors} keeps one handler per
 * module). Inti serves {@code situs} (publishes the town site {@code kota_tema/<tema>} at the plan centre, discovery and
 * the arrival line within 96 blocks), {@code tiba} (arrival point; {@code TempatApi.arrival} reads it), {@code zona}
 * (protection only, data {@code protect}), and the satellites' sites {@code reruntuhan} ({@code kota_tema/reruntuhan_gurun})
 * and {@code pos} ({@code kota_tema/pos_pemburu}). Every other "what" is forwarded to the handler a theme agent
 * registered with {@link #daftar} ({@code tambatan}, {@code lift}, {@code arena}, {@code sel}, ...), each with its own
 * {@link AnchorHandler#radius()} (the players list is filtered per forwarder).
 * <p>
 * The handler also keeps the live-town count of {@link TemaKota#hidupIds()} (towns with a loaded anchor), which drives the
 * calendar ({@link TemaKalender}) and the mechanisms ({@link TemaMekanisme}).
 */
public final class TemaAnchors implements AnchorHandler {

	public static final String MODULE = "kota_tema";
	/** Players within this many blocks of the {@code situs} anchor discover the town. */
	public static final int JARAK_TEMU = 96;

	static final TemaAnchors HANDLER = new TemaAnchors();

	private static final Map<String, AnchorHandler> TAMBAHAN = new HashMap<>();
	private static boolean initialised;

	/** Anchor -> the town id it counted for (for the unload). */
	private final Map<DuniaAnchorEntity, Long> hitung = new IdentityHashMap<>();
	/** "uuid:town" pairs greeted this session. */
	private final Set<String> disapa = new HashSet<>();

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		DuniaAnchors.register(MODULE, HANDLER);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HANDLER.hitung.clear();
			HANDLER.disapa.clear();
			HANDLER.jalur.clear();
			HANDLER.jalurTerkirim.clear();
			TemaKota.reset();
		});
		// a reconnecting client lost its lanes: send them again
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			String prefix = handler.getPlayer().getUUID() + ":";
			HANDLER.jalurTerkirim.keySet().removeIf(k -> k.startsWith(prefix));
		});
	}

	/**
	 * Registers the forwarder of anchors {@code kota_tema/<what>} for a theme agent (from its {@code <Tema>.init()}; a
	 * second registration of the same what replaces the first, logged).
	 */
	public static void daftar(String what, AnchorHandler handler) {
		if (what == null || what.isEmpty() || handler == null) {
			return;
		}
		if (TAMBAHAN.put(what, handler) != null) {
			Kultivator.LOGGER.warn("Themed anchor kind kota_tema/{} registered twice; the later one wins", what);
		}
	}

	/** The born town an anchor stands in, or null (satellite or not overworld). */
	public static @Nullable TemaKota.Kota kota(DuniaAnchorEntity anchor) {
		if (!(anchor.level() instanceof ServerLevel level)) {
			return null;
		}
		return TemaKota.di(level, anchor.blockPosition());
	}

	@Override
	public int radius() {
		return JARAK_TEMU;
	}

	@Override
	public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
		TemaKota.Kota kota = kota(anchor);
		if (kota != null && !hitung.containsKey(anchor)) {
			hitung.put(anchor, kota.situsId());
			TemaKota.hitungHidup(kota.situsId(), 1);
		}
		switch (anchor.what()) {
			case "situs" -> {
				if (kota != null) {
					terbitkan(level, kota);
				}
			}
			case "reruntuhan" -> DuniaSites.publish(level, "kota_tema/reruntuhan_gurun", asal(anchor), tingkatReruntuhan(anchor.blockPosition()));
			case "pos" -> terbitkanPos(level, anchor);
			default -> {
			}
		}
		AnchorHandler forward = TAMBAHAN.get(anchor.what());
		if (forward != null) {
			try {
				forward.loaded(level, anchor);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Themed anchor handler {} failed on load", anchor.kind(), exception);
			}
		}
	}

	@Override
	public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (!TemaConfig.inti().aktif) {
			return;
		}
		switch (anchor.what()) {
			case "situs" -> {
				TemaKota.Kota kota = kota(anchor);
				if (kota != null) {
					sapa(level, kota, within);
				}
			}
			case "reruntuhan" -> temukan(level, asal(anchor), "kota_tema/reruntuhan_gurun", within, 40);
			case "pos" -> temukan(level, asal(anchor), "kota_tema/pos_pemburu", within, 48);
			default -> {
			}
		}
		AnchorHandler forward = TAMBAHAN.get(anchor.what());
		if (forward == null) {
			return;
		}
		int r = Math.min(JARAK_TEMU, Math.max(1, forward.radius()));
		List<ServerPlayer> mine = within;
		if (r < JARAK_TEMU) {
			double r2 = (double) r * r;
			mine = new ArrayList<>();
			for (ServerPlayer p : within) {
				if (p.distanceToSqr(anchor) <= r2) {
					mine.add(p);
				}
			}
		}
		if (mine.isEmpty()) {
			return;
		}
		try {
			forward.nearby(level, anchor, mine);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Themed anchor handler {} failed", anchor.kind(), exception);
		}
	}

	@Override
	public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
		Long town = hitung.remove(anchor);
		if (town != null) {
			TemaKota.hitungHidup(town, -1);
		}
		AnchorHandler forward = TAMBAHAN.get(anchor.what());
		if (forward != null) {
			try {
				forward.unloaded(level, anchor);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Themed anchor handler {} failed on unload", anchor.kind(), exception);
			}
		}
	}

	// ================================================================================================ inti kinds

	/** Publishes the town site at the plan centre (its id is the town id of {@link TemaKota}). */
	static DuniaSites.Site terbitkan(ServerLevel level, TemaKota.Kota kota) {
		Kandidat k = kota.kandidat();
		return DuniaSites.publish(level, kota.jenis(), new BlockPos(k.pusatX(), kota.dasarY(), k.pusatZ()), tingkat(kota.tema()));
	}

	/** Site tier of a theme (map pointer strength; 38.T.1 danger column). */
	public static int tingkat(Tema tema) {
		return switch (tema) {
			case PELABUHAN, AIR -> 1;
			case OASIS, GUNUNG -> 2;
			case IBLIS -> 4;
		};
	}

	// ------------------------------------------------------------------------------------------------ lanes (SPLINE)

	/** Town -> [game time built, lane packet]; rebuilt at most every {@value #JALUR_SEGAR} ticks (pieces load late). */
	private final Map<Long, Object[]> jalur = new HashMap<>();
	/** "uuid:town" -> number of lanes last sent to that player. */
	private final Map<String, Integer> jalurTerkirim = new HashMap<>();
	private static final int JALUR_SEGAR = 200;

	/**
	 * The canal, sea, lantern and cable lanes of the town's loaded pieces as one {@code TemaNet.SPLINE} body: titik
	 * {@code jalur_perahu} / {@code jalur_kapal} / {@code lentera_air} grouped by data {@code jalur} and ordered by
	 * {@code urut} (kinds {@code perahu}, {@code kapal}, {@code lentera}), and {@code stasiun_lift} pairs (data
	 * {@code pasangan}) as two-point {@code kabel} lanes. Trimmed to fit one packet.
	 */
	private CompoundTag lajur(ServerLevel level, TemaKota.Kota kota) {
		Object[] cached = jalur.get(kota.situsId());
		long now = level.getGameTime();
		if (cached != null && now - (long) cached[0] < JALUR_SEGAR) {
			return (CompoundTag) cached[1];
		}
		Map<String, java.util.TreeMap<Integer, BlockPos>> lanes = new java.util.LinkedHashMap<>();
		Map<String, String> kinds = new HashMap<>();
		for (String jenis : new String[]{"jalur_perahu", "jalur_kapal", "lentera_air"}) {
			String kind = switch (jenis) {
				case "jalur_perahu" -> "perahu";
				case "jalur_kapal" -> "kapal";
				default -> "lentera";
			};
			for (dev.kultivator.dunia.tempat.Titik t : TemaKota.titik(level, kota, jenis)) {
				String id = kind + ":" + t.data().getOrDefault("jalur", "a");
				int urut;
				try {
					urut = Integer.parseInt(t.data().getOrDefault("urut", "0"));
				} catch (NumberFormatException e) {
					urut = 0;
				}
				java.util.TreeMap<Integer, BlockPos> lane = lanes.computeIfAbsent(id, k -> new java.util.TreeMap<>());
				while (lane.containsKey(urut)) {
					urut++;
				}
				lane.put(urut, t.pos());
				kinds.put(id, kind);
			}
		}
		Map<String, BlockPos> lift = new HashMap<>();
		for (dev.kultivator.dunia.tempat.Titik t : TemaKota.titik(level, kota, "stasiun_lift")) {
			String pair = t.data().getOrDefault("pasangan", "");
			BlockPos other = lift.remove(pair);
			if (other == null) {
				lift.put(pair, t.pos());
			} else {
				java.util.TreeMap<Integer, BlockPos> lane = new java.util.TreeMap<>();
				lane.put(0, other);
				lane.put(1, t.pos());
				lanes.put("kabel:" + pair, lane);
				kinds.put("kabel:" + pair, "kabel");
			}
		}
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		CompoundTag body = new CompoundTag();
		for (Map.Entry<String, java.util.TreeMap<Integer, BlockPos>> e : lanes.entrySet()) {
			if (e.getValue().size() < 2 && !"lentera".equals(kinds.get(e.getKey()))) {
				continue;
			}
			int[] p = new int[e.getValue().size() * 3];
			int i = 0;
			for (BlockPos pos : e.getValue().values()) {
				p[i++] = pos.getX();
				p[i++] = pos.getY();
				p[i++] = pos.getZ();
			}
			CompoundTag lane = new CompoundTag();
			lane.putString("id", e.getKey());
			lane.putString("kind", kinds.get(e.getKey()));
			lane.putIntArray("p", p);
			list.add(lane);
			body.put("jalur", list);
			if (!TemaNet.muat(body)) {
				list.removeLast();
				break;
			}
		}
		body.put("jalur", list);
		jalur.put(kota.situsId(), new Object[]{now, body});
		return body;
	}

	private void kirimJalur(ServerLevel level, TemaKota.Kota kota, ServerPlayer player) {
		CompoundTag body = lajur(level, kota);
		int n = body.getListOrEmpty("jalur").size();
		String key = player.getUUID() + ":" + kota.situsId();
		if (n == 0 || jalurTerkirim.getOrDefault(key, -1) >= n) {
			return;
		}
		if (TemaNet.kirim(player, TemaNet.SPLINE, kota.situsId(), body)) {
			jalurTerkirim.put(key, n);
		}
	}

	private void sapa(ServerLevel level, TemaKota.Kota kota, List<ServerPlayer> within) {
		DuniaSites.Site site = terbitkan(level, kota);
		for (ServerPlayer player : within) {
			if (player.distanceToSqr(kota.pusat().getX() + 0.5D, player.getY(), kota.pusat().getZ() + 0.5D) > 160.0D * 160.0D) {
				continue;
			}
			kirimJalur(level, kota, player);
			boolean baru = DuniaSites.discover(player, site.id());
			String key = player.getUUID() + ":" + kota.situsId();
			if (!disapa.add(key) && !baru) {
				continue;
			}
			Component nama = kota.nama(level.getSeed());
			Component sumber = Component.translatable("kultivator.kota_tema." + kota.tema().id() + ".sumber");
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.tiba." + kota.tema().id(), nama)
					.withStyle(style -> style.withColor(kota.tema().warna()).withHoverEvent(new HoverEvent.ShowText(sumber))));
			if (baru) {
				player.sendSystemMessage(Component.translatable("kultivator.kota_tema.tiba.baru").withStyle(ChatFormatting.GRAY));
				level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_IN, SoundSource.PLAYERS, 0.6F, 1.0F);
			}
			Advancements.grant(player, "tempat/tema_" + kota.tema().id());
			KotaTemaData data = KotaTemaData.get(level.getServer());
			if (TemaCerita.catatKunjungan(data, player, kota.tema())) {
				Advancements.grant(player, "tempat/tema_lima_kota");
			}
			dev.kultivator.dunia.tempat.cerita.CeritaApi.peristiwa(player, TemaCerita.MASUK_KOTA_TEMA, kota.tema().id());
		}
	}

	private static void temukan(ServerLevel level, BlockPos pos, String kind, List<ServerPlayer> within, int jarak) {
		DuniaSites.Site site = null;
		for (ServerPlayer player : within) {
			if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) > (double) jarak * jarak) {
				continue;
			}
			if (site == null) {
				site = DuniaSites.publish(level, kind, pos, "kota_tema/pos_pemburu".equals(kind) ? 2 : tingkatReruntuhan(pos));
			}
			if (DuniaSites.discover(player, site.id())) {
				player.sendSystemMessage(Component.translatable("kultivator.kota_tema.temu." + kind.substring("kota_tema/".length()))
						.withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static void terbitkanPos(ServerLevel level, DuniaAnchorEntity anchor) {
		DuniaSites.publish(level, "kota_tema/pos_pemburu", asal(anchor), 2);
	}

	/** The structure start (or the anchor itself) as the site position. */
	private static BlockPos asal(DuniaAnchorEntity anchor) {
		BlockPos origin = anchor.origin();
		return origin != null ? origin : anchor.blockPosition();
	}

	/** Ruin tier from the distance ring (38.T.2.6): 1 inside 3000, 2 inside 6000, else 3. */
	public static int tingkatReruntuhan(BlockPos pos) {
		double d = Math.hypot(pos.getX(), pos.getZ());
		return d < 3000.0D ? 1 : d < 6000.0D ? 2 : 3;
	}

	/** The Kota Iblis of a hunters' camp position (the camp's plan link), or null. */
	public static @Nullable TemaKota.Kota kotaIblisDari(ServerLevel level, BlockPos camp) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		PutusanTanah.Sumber sumber = TempatZones.sumber(level);
		for (Kandidat k : RencanaPosPemburu.INSTANCE.kandidat(level.getSeed())) {
			if (Math.abs(k.pusatX() - camp.getX()) <= RencanaPosPemburu.SETENGAH && Math.abs(k.pusatZ() - camp.getZ()) <= RencanaPosPemburu.SETENGAH
					&& RencanaPosPemburu.akhir(sumber, k).layak()) {
				RencanaKotaTema.Calon c = RencanaKotaTema.plan(level.getSeed()).calon(Tema.IBLIS, k.index() >> 2);
				return c == null ? null : TemaKota.dari(sumber, c);
			}
		}
		return null;
	}

	private TemaAnchors() {
	}
}
