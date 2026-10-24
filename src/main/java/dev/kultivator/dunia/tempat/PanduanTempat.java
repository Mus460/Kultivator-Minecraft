package dev.kultivator.dunia.tempat;

import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.desa.Tribute;
import dev.kultivator.dunia.tempat.cerita.AlurTakhta;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.perbaikan.PerbaikanApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.util.Advancements;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The guide milestones of the Kitab Panduan chapters 18 "Kota Besar" and 19 "Tempat-Tempat Dunia" (DESIGN 38.D.11,
 * 38.D.13; owner 38W) that no place module grants itself, granted here from what the player visibly does, read only
 * through the modules' public APIs (no state of its own except the services seen):
 * <ul>
 *   <li>{@code tempat/kampung_tiba}, {@code tempat/kompleks_tiba}: standing in a village / an NPC sect compound
 *       ({@link TempatApi#tempatDi});</li>
 *   <li>{@code tempat/kampung_ikrar}: standing in a village whose bell is pledged to the player ({@link Tribute#ikrarDi});</li>
 *   <li>{@code tempat/tukang_bantu}: help points at the place he stands in ({@link PerbaikanApi#poinBantu} &gt; 0);</li>
 *   <li>{@code tempat/tiga_layanan}, {@code tempat/papan_yamen}: three different service screens opened in a capital
 *       ({@link TempatLayar#layarTerbuka}; the list is kept in {@code CultivationData.extra()} tag
 *       {@value #TAG}), the Yamen board among them for the second;</li>
 *   <li>{@code tempat/acara_ikut}, {@code tempat/lelang_agung_ikut}: standing in a capital while one of its great events
 *       runs (the {@link TempatEvents#ACARA} bus; curfew bells do not count), the Grand Auction for the second;</li>
 *   <li>{@code tempat/lencana_1}: a first guild badge star ({@link EkonomiApi#lencana});</li>
 *   <li>{@code tempat/takhta_bab3}, {@code tempat/penjaga_takhta}: the throne story's chapter quests 3-7 / 7 finished
 *       ({@link CeritaApi#tahap}).</li>
 * </ul>
 * Budget: one player per server tick slot, each player looked at every {@link #SELANG} ticks (screens every
 * {@link #SELANG_LAYAR}); done advancements are remembered per session so a finished player costs a set lookup.
 */
public final class PanduanTempat {

	public static final String TAG = "tempat_panduan";
	public static final int SELANG = 40;
	public static final int SELANG_LAYAR = 10;
	/** Service screens that count for "three services" (the plain NPC dialog does not). */
	public static final Set<String> LAYANAN = Set.of(LembarKunci.TokoSenjata.LAYAR, LembarKunci.Lelang.LAYAR, LembarKunci.GildaMisi.LAYAR,
			LembarKunci.Bank.LAYAR, LembarKunci.PetaKota.LAYAR, LembarKunci.Teleport.LAYAR, LembarKunci.Dermaga.LAYAR,
			LembarKunci.PasarBinatang.LAYAR, LembarKunci.JudiBatu.LAYAR, LembarKunci.Yamen.LAYAR, LembarKunci.Acara.LAYAR,
			LembarKunci.BukuKas.LAYAR, LembarKunci.Tagihan.LAYAR, LembarKunci.Layanan.LAYAR_PENGINAPAN, LembarKunci.Layanan.LAYAR_RUMAH_MAKAN,
			LembarKunci.Layanan.LAYAR_JASA, LembarKunci.Gilda.LAYAR_ALKEMIS, LembarKunci.Gilda.LAYAR_TEMPA);
	/** Town events that are not "great events" (bells and curfew). */
	private static final Set<String> RUTIN = Set.of("jam_malam", "lonceng_pagi");
	/** An event the bus never closed is forgotten after one world day. */
	private static final long ACARA_MAKS = 24000L;

	private static final List<String> SEMUA = List.of("kampung_tiba", "kompleks_tiba", "kampung_ikrar", "tukang_bantu", "tiga_layanan",
			"papan_yamen", "acara_ikut", "lelang_agung_ikut", "lencana_1", "takhta_bab3", "penjaga_takhta");

	/** Live events by place site id: event id -> game time it started. */
	private static final Map<Long, Map<String, Long>> ACARA = new HashMap<>();
	private static final Map<UUID, Set<String>> SELESAI = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TempatEvents.ACARA.register((level, situsId, acara, mulai) -> {
			if (acara == null || RUTIN.contains(acara)) {
				return;
			}
			if (mulai) {
				ACARA.computeIfAbsent(situsId, k -> new HashMap<>()).put(acara, level.getGameTime());
			} else {
				Map<String, Long> live = ACARA.get(situsId);
				if (live != null) {
					live.remove(acara);
					if (live.isEmpty()) {
						ACARA.remove(situsId);
					}
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(PanduanTempat::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SELESAI.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACARA.clear();
			SELESAI.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (!TempatConfig.get().aktif) {
			return;
		}
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int slot = player.getId() + now;
			if (slot % SELANG_LAYAR != 0 || player.isSpectator()) {
				continue;
			}
			Set<String> done = SELESAI.computeIfAbsent(player.getUUID(), u -> new HashSet<>());
			if (done.size() >= SEMUA.size()) {
				continue;
			}
			try {
				lihat(server, player, done, slot % SELANG == 0);
			} catch (RuntimeException e) {
				Kultivator.LOGGER.warn("Guide place check failed for {}", player.getName().getString(), e);
			}
		}
	}

	private static void lihat(MinecraftServer server, ServerPlayer player, Set<String> done, boolean penuh) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		TempatInfo here = TempatApi.tempatDi(level, player.blockPosition());
		boolean ibuKota = here != null && "kota/kota_besar".equals(here.jenis());
		// screens: every SELANG_LAYAR ticks, only in a capital
		if (ibuKota && (!done.contains("tiga_layanan") || !done.contains("papan_yamen"))) {
			String layar = TempatLayar.layarTerbuka(player);
			if (layar != null && LAYANAN.contains(layar)) {
				int n = catatLayanan(player, layar);
				if (layar.equals(LembarKunci.Yamen.LAYAR)) {
					beri(player, done, "papan_yamen");
				}
				if (n >= 3) {
					beri(player, done, "tiga_layanan");
				}
			}
		}
		if (!penuh) {
			return;
		}
		if (here != null) {
			if (here.jenis().startsWith("kampung/")) {
				beri(player, done, "kampung_tiba");
				if (!done.contains("kampung_ikrar")) {
					Tribute.Ikrar ikrar = Tribute.ikrarDi(player.blockPosition(), 128.0D);
					if (ikrar != null && ikrar.owner().equals(player.getUUID())) {
						beri(player, done, "kampung_ikrar");
					}
				}
			} else if (here.jenis().equals("sekte/kompleks")) {
				beri(player, done, "kompleks_tiba");
			}
			if (!done.contains("tukang_bantu") && PerbaikanApi.poinBantu(player, here.situsId()) > 0) {
				beri(player, done, "tukang_bantu");
			}
			if (ibuKota) {
				Map<String, Long> live = ACARA.get(here.situsId());
				if (live != null) {
					long t = level.getGameTime();
					live.values().removeIf(start -> t - start > ACARA_MAKS || t < start);
					if (!live.isEmpty()) {
						beri(player, done, "acara_ikut");
					}
					if (live.containsKey(LembarKunci.Acara.ACARA_LELANG_AGUNG)) {
						beri(player, done, "lelang_agung_ikut");
					}
				}
			}
		}
		if (!done.contains("lencana_1") && EkonomiApi.lencana(player) >= 1) {
			beri(player, done, "lencana_1");
		}
		if (!done.contains("takhta_bab3") || !done.contains("penjaga_takhta")) {
			for (int bab = 3; bab <= AlurTakhta.BAB_TERAKHIR; bab++) {
				if (CeritaApi.tahap(player, AlurTakhta.questBab(bab)) == 999) {
					beri(player, done, "takhta_bab3");
					if (bab == AlurTakhta.BAB_TERAKHIR) {
						beri(player, done, "penjaga_takhta");
					}
				}
			}
		}
		// advancements granted elsewhere (commands, other paths) are picked up so the set fills
		for (String id : SEMUA) {
			if (!done.contains(id) && sudah(player, id)) {
				done.add(id);
			}
		}
	}

	/** Remembers service screen {@code layar}; returns how many different services the player has used. */
	private static int catatLayanan(ServerPlayer player, String layar) {
		CultivationData data = Cultivators.get(player);
		CompoundTag tag = data.extra().getCompoundOrEmpty(TAG);
		ListTag list = tag.getListOrEmpty("layanan");
		Set<String> seen = new HashSet<>();
		for (Tag t : list) {
			t.asString().ifPresent(seen::add);
		}
		if (seen.add(layar)) {
			CompoundTag next = tag.copy();
			ListTag out = new ListTag();
			for (String s : seen) {
				out.add(StringTag.valueOf(s));
			}
			next.put("layanan", out);
			data.extra().put(TAG, next);
			Cultivators.dirty(player);
		}
		return seen.size();
	}

	/** The services the player has used in capitals (for the guide hint and {@code uji tempat}). */
	public static int layananDipakai(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(TAG).getListOrEmpty("layanan").size();
	}

	private static void beri(ServerPlayer player, Set<String> done, String id) {
		if (done.add(id)) {
			Advancements.grant(player, "tempat/" + id);
		}
	}

	private static boolean sudah(ServerPlayer player, String id) {
		@Nullable AdvancementHolder holder = player.level().getServer().getAdvancements().get(Kultivator.id("tempat/" + id));
		return holder == null || player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	private PanduanTempat() {
	}
}
