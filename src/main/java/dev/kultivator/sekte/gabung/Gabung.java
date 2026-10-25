package dev.kultivator.sekte.gabung;

import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gabung Sekte (DESIGN 41): joining the NPC sects. The one entry of the seven modules 41A-41V, called from
 * {@code SekteContent.init} on both physical sides <b>whatever the switches say</b>, before {@code ModItems.init()}:
 * <ol>
 * <li>K1 registry content, always: the four payload types ({@link GabungPayloads}) and the entity types, items and data
 * components of {@code masuk.Masuk.registerContent()};</li>
 * <li>the config section {@code gabungSekte} ({@link GabungConfig});</li>
 * <li>while the phase ({@code umum.aktif}) and {@code gabungSekte.aktif} are on: the commands ({@link GabungCommands},
 * {@link GabungUji}), the sync hooks, the Sibuk states of 41.0 item 6, the lazy upkeep at login, the 41A core
 * ({@link Pemeriksa} rule watchers, {@link BalaiHukum} Law Hall, {@link Keluar} leaving and the R2 founding veto,
 * {@link GabungHarian} daily round, {@link Promosi} rank tree), and the runtime init of
 * every sub-package: {@code masuk.Masuk} (41B), {@code hidup.HidupAnggota} (41C), {@code politik.Politik} (41D),
 * {@code dunia.DuniaGabung} (41W), {@code ui.GabungLayar} (41U).</li>
 * </ol>
 * Packages: server {@code dev.kultivator.sekte.gabung.<sub>}, client {@code dev.kultivator.client.sekte.gabung.<sub>}
 * (entry {@code GabungClient}, called from {@code SekteContentClient}); lang {@code lang-src/sekte_gabung*.json} (prefix
 * {@code kultivator.gabung.}); advancements {@code data/kultivator/advancement/sekte/gabung_*.json}. Runtime blocks never
 * change through 41 (no {@code WorldBreaker}, no {@code BuildPlacer}); every building comes from world generation in new
 * chunks; mechanisms are entities.
 */
public final class Gabung {

	/** Busy states of 41.0 item 6 (reason keys {@code kultivator.sibuk.gabung.*}). */
	public static final String SIBUK_UJIAN = "gabung.ujian";
	public static final String SIBUK_SUMPAH = "gabung.sumpah";
	public static final String SIBUK_KURUNGAN = "gabung.kurungan";
	public static final String SIBUK_PERANG = "gabung.perang";
	public static final String SIBUK_SUKSESI = "gabung.suksesi";
	/** The enemy event of the deserter hunters (shared pressure, {@code Sibuk.enemyTaken(player, "gabung")}). */
	public static final String SIBUK_PEMBURU = "gabung.pemburu";

	private static final String[] SIBUK = {SIBUK_UJIAN, SIBUK_SUMPAH, SIBUK_KURUNGAN, SIBUK_PERANG, SIBUK_SUKSESI};

	/** player -> busy state -> game tick it ends. */
	private static final Map<UUID, Map<String, Long>> SIBUK_SAMPAI = new ConcurrentHashMap<>();
	/** Players with a live hunter squad. */
	private static final Set<UUID> PEMBURU = ConcurrentHashMap.newKeySet();

	private static boolean content;
	private static boolean initialised;
	private static boolean runtime;
	private static volatile @Nullable MinecraftServer server;

	/** From {@code SekteContent.init} (both sides, always). Safe to call twice. */
	public static void init() {
		registerContent();
		if (initialised) {
			return;
		}
		initialised = true;
		GabungConfig.load();
		if (!SekteContent.umum().aktif || !GabungConfig.get().aktif) {
			return;
		}
		runtime = true;
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			SIBUK_SAMPAI.clear();
			PEMBURU.clear();
		});
		GabungCommands.init();
		GabungUji.init();
		GabungPayloads.daftarPenerima();
		daftarSibuk();
		ServerPlayerEvents.JOIN.register(Gabung::masuk);
		// 41A core: rule watchers, the Law Hall, leaving and the R2 founding veto, the daily round, the rank tree
		Pemeriksa.init();
		BalaiHukum.init();
		Keluar.init();
		GabungHarian.init();
		Promosi.init();
		ServerPlayerEvents.LEAVE.register(player -> {
			SIBUK_SAMPAI.remove(player.getUUID());
			PEMBURU.remove(player.getUUID());
		});
		// 41W's compound spots (new titik and their stand-ins) come before GabungApi's own compound/gate steps.
		GabungApi.addPenyediaTitik((s, sekte, titik) -> dev.kultivator.sekte.gabung.dunia.TitikGabung.kompleks(sekte, titik));
		// 41B Masuk & Ujian:
		dev.kultivator.sekte.gabung.masuk.Masuk.init();
		// 41C Hidup Anggota:
		dev.kultivator.sekte.gabung.hidup.HidupAnggota.init();
		// 41D Politik & Perang:
		dev.kultivator.sekte.gabung.politik.Politik.init();
		// 41W Dunia & Tokoh:
		dev.kultivator.sekte.gabung.dunia.DuniaGabung.init();
		// 41U Layar:
		dev.kultivator.sekte.gabung.ui.GabungLayar.init();
	}

	/** K1: payload types, entity types, items and components of 41, always registered. Safe to call twice. */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
		GabungPayloads.daftarJenis();
		dev.kultivator.sekte.gabung.masuk.Masuk.registerContent();
	}

	/** True while 41 runs: the runtime was set up and both switches are on now (a reload can switch it off). */
	public static boolean aktif() {
		return runtime && SekteContent.umum().aktif && GabungConfig.get().aktif;
	}

	/** The running server, or null (client side, between worlds). */
	public static @Nullable MinecraftServer server() {
		return server;
	}

	// ================================================================== login

	private static void masuk(ServerPlayer player) {
		MinecraftServer s = player.level().getServer();
		if (s == null || !aktif()) {
			return;
		}
		if (server == null) {
			server = s;
		}
		GabungData data = GabungData.get(s);
		Keanggotaan k = data.peek(player.getUUID());
		if (k != null && k.sentuh(player, GabungApi.hari(s))) {
			data.setDirty();
		}
		GabungPayloads.kirimLencanaAwal(player);
		GabungPayloads.kirimKelas(player);
	}

	// ================================================================== Sibuk (41.0 item 6)

	private static void daftarSibuk() {
		for (String id : SIBUK) {
			Sibuk.register(id, player -> sibukDengan(player, id));
		}
		Sibuk.registerEnemy(SIBUK_PEMBURU, player -> PEMBURU.contains(player.getUUID()));
	}

	/** True while {@code player} is held by 41 state {@code keadaan} (confinement also from the saved record). */
	public static boolean sibukDengan(ServerPlayer player, String keadaan) {
		long now = player.level().getGameTime();
		Map<String, Long> map = SIBUK_SAMPAI.get(player.getUUID());
		Long until = map == null ? null : map.get(keadaan);
		if (until != null) {
			if (until > now) {
				return true;
			}
			map.remove(keadaan);
		}
		if (SIBUK_KURUNGAN.equals(keadaan)) {
			MinecraftServer s = player.level().getServer();
			Keanggotaan k = s == null ? null : GabungData.get(s).peek(player.getUUID());
			return k != null && k.kurunganSampai > now;
		}
		return false;
	}

	/** Holds {@code player} in busy state {@code keadaan} for {@code ticks} (use {@code GabungApi.sibuk}). */
	public static void tandaiSibuk(ServerPlayer player, String keadaan, int ticks) {
		if (player == null || keadaan == null || ticks <= 0) {
			return;
		}
		SIBUK_SAMPAI.computeIfAbsent(player.getUUID(), key -> new ConcurrentHashMap<>())
				.put(keadaan, player.level().getGameTime() + ticks);
	}

	public static void lepasSibuk(ServerPlayer player, String keadaan) {
		Map<String, Long> map = SIBUK_SAMPAI.get(player.getUUID());
		if (map != null) {
			map.remove(keadaan);
		}
	}

	public static void pemburuHidup(ServerPlayer player, boolean hidup) {
		if (hidup) {
			PEMBURU.add(player.getUUID());
		} else {
			PEMBURU.remove(player.getUUID());
		}
	}

	private Gabung() {
	}
}
