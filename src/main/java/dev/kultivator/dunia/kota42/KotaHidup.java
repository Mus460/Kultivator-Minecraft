package dev.kultivator.dunia.kota42;

import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kota yang Hidup (DESIGN 42): living towns. The one entry of the eleven modules 42A-42V, called from
 * {@code DuniaContent.init} on both physical sides after the 38 places, before {@code ModItems.init()}:
 * <ol>
 * <li>registry content, always (42.0: switches gate behaviour only): the eight payload types ({@link KotaPayloads}), the
 * blocks, items and entity types of 42R0 ({@code ekonomi}, {@code sekte}, {@code acara}, {@code hewan}, {@code tumbuh});</li>
 * <li>the seven config sections ({@link KotaConfig});</li>
 * <li>the runtime: commands ({@link KotaHidupCommands} {@code /kultivasi kotaku}, {@link KotaUji}
 * {@code /kultivasi uji kota_hidup}), the rhythm receiver, the Sibuk states of 42 ({@code kota.kejar}, {@code kota.sidang},
 * {@code kota.upacara}, {@code kota.bela}; enemy {@code kota.kepung}), the entity cap {@code kota42} and the runtime init
 * of every sub-package: {@code warga.KotaWarga} (42A), {@code ragam.KotaRagam} (42S), {@code cakap.Cakap} (42K),
 * {@code bicara.KotaBicara} (42B), {@code ekonomi.KotaEkonomi} (42C), {@code acara.KotaAcara} (42D),
 * {@code hewan.HewanKota} (42D), {@code sekte.KotaSekte} (42E), {@code tumbuh.KotaTumbuh} (42F). Each module checks its own
 * section's {@code aktif} at run time, so a reload can switch it off and on.</li>
 * </ol>
 * Packages: server {@code dev.kultivator.dunia.kota42.<sub>}, client {@code dev.kultivator.client.dunia.kota42.<sub>} (entry
 * {@code KotaHidupClient}, called from {@code KultivatorClient}); lang {@code lang-src/kota_hidup*.json} (prefix
 * {@code kultivator.kota42.}); advancements {@code data/kultivator/advancement/tempat/kota42_*.json}. 42 is overworld only;
 * runtime blocks change only through {@code WorldBreaker.submitRestore} (42F growth, real-damage rebuilds); no structure
 * type, set or placement is added.
 */
public final class KotaHidup {

	/** Busy states (reason keys {@code kultivator.sibuk.kota.*}). */
	public static final String SIBUK_KEJAR = "kota.kejar";
	public static final String SIBUK_SIDANG = "kota.sidang";
	public static final String SIBUK_UPACARA = "kota.upacara";
	public static final String SIBUK_BELA = "kota.bela";
	/** The enemy event of a town siege ({@code Sibuk.enemyTaken(player, "kota")}). */
	public static final String SIBUK_KEPUNG = "kota.kepung";
	/** {@code DuniaBudget} kind and cap of 42's entities (animals <= 48, mechanisms <= 48; 42.13.3). */
	public static final String ANGGARAN = "kota42";
	public static final int ANGGARAN_MAKS = 96;

	private static final String[] SIBUK = {SIBUK_KEJAR, SIBUK_SIDANG, SIBUK_UPACARA, SIBUK_BELA};

	/** player -> busy state -> game tick it ends. */
	private static final Map<UUID, Map<String, Long>> SIBUK_SAMPAI = new ConcurrentHashMap<>();
	/** Players inside a besieged town. */
	private static final Set<UUID> KEPUNG = ConcurrentHashMap.newKeySet();

	private static boolean content;
	private static boolean initialised;
	private static volatile @Nullable MinecraftServer server;

	/** From {@code DuniaContent.init} (both sides). Safe to call twice. */
	public static void init() {
		registerContent();
		if (initialised) {
			return;
		}
		initialised = true;
		KotaConfig.load();
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			SIBUK_SAMPAI.clear();
			KEPUNG.clear();
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			SIBUK_SAMPAI.remove(player.getUUID());
			KEPUNG.remove(player.getUUID());
			KotaPayloads.lupakan(player.getUUID());
		});
		DuniaBudget.setCap(ANGGARAN, ANGGARAN_MAKS);
		KotaHidupCommands.init();
		KotaUji.init();
		KotaPayloads.daftarPenerima();
		daftarSibuk();
		// 42A Warga (residents, life cycle, memory):
		dev.kultivator.dunia.kota42.warga.KotaWarga.init();
		// 42S Sebaran & Ragam (even spread, varied days):
		dev.kultivator.dunia.kota42.ragam.KotaRagam.init();
		// 42K Mesin Percakapan (no LLM):
		dev.kultivator.dunia.kota42.cakap.Cakap.init();
		// 42B Bicara & Cerita:
		dev.kultivator.dunia.kota42.bicara.KotaBicara.init();
		// 42C Ekonomi Kota:
		dev.kultivator.dunia.kota42.ekonomi.KotaEkonomi.init();
		// 42D Kehidupan & Acara, Hewan Kota:
		dev.kultivator.dunia.kota42.acara.KotaAcara.init();
		dev.kultivator.dunia.kota42.hewan.HewanKota.init();
		// 42E Sekte & Kota:
		dev.kultivator.dunia.kota42.sekte.KotaSekte.init();
		// 42F Tumbuh & Bangun Kembali:
		dev.kultivator.dunia.kota42.tumbuh.KotaTumbuh.init();
	}

	/** Payload types, blocks, items and entity types of 42, always registered. Safe to call twice. */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
		KotaPayloads.daftarJenis();
		// 42R0 registries (blocks before their items, entity types last):
		dev.kultivator.dunia.kota42.ekonomi.KotaEkonomiBlocks.register();
		dev.kultivator.dunia.kota42.ekonomi.KotaEkonomiItems.register();
		dev.kultivator.dunia.kota42.acara.KotaAcaraItems.register();
		dev.kultivator.dunia.kota42.ekonomi.KotaEkonomiEntities.register();
		dev.kultivator.dunia.kota42.acara.KotaAcaraEntities.register();
		dev.kultivator.dunia.kota42.hewan.HewanEntities.register();
		dev.kultivator.dunia.kota42.sekte.KotaSekteEntities.register();
		dev.kultivator.dunia.kota42.tumbuh.KotaTumbuhEntities.register();
	}

	/** The running server, or null (client side, between worlds). */
	public static @Nullable MinecraftServer server() {
		return server;
	}

	/** The world day ({@code Kota.day}: overworld clock / 24000). */
	public static long hari(MinecraftServer server) {
		return Kota.day(server);
	}

	/** True for the one dimension 42 lives in (42.12.1A: overworld only, every entry point checks it). */
	public static boolean duniaKota(Level level) {
		return level != null && level.dimension() == Level.OVERWORLD;
	}

	// ================================================================== Sibuk

	private static void daftarSibuk() {
		for (String id : SIBUK) {
			Sibuk.register(id, player -> sibukDengan(player, id));
		}
		Sibuk.registerEnemy(SIBUK_KEPUNG, player -> KEPUNG.contains(player.getUUID()));
	}

	/** True while {@code player} is held by 42 state {@code keadaan}. */
	public static boolean sibukDengan(ServerPlayer player, String keadaan) {
		Map<String, Long> map = SIBUK_SAMPAI.get(player.getUUID());
		Long until = map == null ? null : map.get(keadaan);
		if (until == null) {
			return false;
		}
		if (until > player.level().getGameTime()) {
			return true;
		}
		map.remove(keadaan);
		return false;
	}

	/** Holds {@code player} in busy state {@code keadaan} ({@link #SIBUK_KEJAR}...) for {@code ticks}. */
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

	/** Marks {@code player} as inside (or out of) a besieged town (42E raids). */
	public static void kepung(ServerPlayer player, boolean hidup) {
		if (hidup) {
			KEPUNG.add(player.getUUID());
		} else {
			KEPUNG.remove(player.getUUID());
		}
	}

	private KotaHidup() {
	}
}
