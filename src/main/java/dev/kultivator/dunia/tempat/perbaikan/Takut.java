package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kampung.KampungApi;
import dev.kultivator.dunia.tempat.kompleks.KompleksApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.util.Advancements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fear of an overwhelming visitor (DESIGN 38.R.5). A place's strength is a realm ordinal (hamlet mortal, village Qi
 * Refining, the capital Nascent Soul, an NPC sect compound its tier minus one, ...); when a culprit stands at least
 * {@code perbaikan.selisihTakut} realms above it, nobody dares to bill him: the collector kneels, the repair runs at
 * x{@code pengaliTakut}, and the place's fear meter (0-100) rises by {@code min(40, bill / 2)}, falling 2 a world day.
 * <ul>
 *   <li>fear 20+: children within 32 blocks of such a visitor run home and hide; dialogue stutters, merchants give a
 *       10 % "offering", quest givers decline and the tea house keeps its rumours (read through
 *       {@link PerbaikanApi#takut} by the owners of those screens);</li>
 *   <li>fear 60+: townsfolk bow deeply as he passes, guards look away (crimes are still recorded); the culprit earns the
 *       hidden advancement {@code tempat/ditakuti}.</li>
 * </ul>
 * Atonement: paying the last bill anyway ({@code bayar_rela}: fear -30, Nama Baik +5), helping the craftsmen, donating.
 */
public final class Takut {

	public static final int AMBANG_ANAK = 20;
	public static final int AMBANG_HORMAT = 60;
	/** Ticks between two bows of the same townsman toward the same visitor. */
	private static final long JEDA_HORMAT = 600L;

	private static final Map<Integer, Long> HORMAT = new HashMap<>();

	/** The fear meter of place {@code situsId} (after the daily decay). */
	public static int nilai(MinecraftServer server, long situsId) {
		PerbaikanData data = PerbaikanData.get(server);
		PerbaikanData.TakutData f = data.takut.get(situsId);
		if (f == null) {
			return 0;
		}
		long day = Kota.day(server);
		if (f.hari < day && f.nilai > 0) {
			f.nilai = Math.max(0, f.nilai - 2 * (int) Math.min(50L, day - f.hari));
			f.hari = day;
			data.kotor();
		} else if (f.hari < day) {
			f.hari = day;
		}
		return f.nilai;
	}

	/** Raises the fear of place {@code situsId} after a fear visit by {@code pemicu} for a would-be bill. */
	static void naik(ServerLevel level, long situsId, long tagihan, @Nullable ServerPlayer pemicu) {
		MinecraftServer server = level.getServer();
		int before = nilai(server, situsId);
		PerbaikanData data = PerbaikanData.get(server);
		PerbaikanData.TakutData f = data.takut(situsId);
		f.nilai = Math.min(100, before + (int) Math.min(40L, Math.max(1L, tagihan / 2L)));
		f.hari = Kota.day(server);
		f.tagihanTerakhir = Math.max(5L, tagihan);
		f.pemicu = pemicu == null ? null : pemicu.getUUID();
		data.kotor();
		if (pemicu != null && f.nilai >= AMBANG_HORMAT) {
			Advancements.grant(pemicu, "tempat/ditakuti");
		}
	}

	/** Lowers the fear (atonement); never below 0. */
	static void turun(MinecraftServer server, long situsId, int amount) {
		PerbaikanData data = PerbaikanData.get(server);
		PerbaikanData.TakutData f = data.takut.get(situsId);
		if (f != null) {
			nilai(server, situsId);
			f.nilai = Math.max(0, f.nilai - amount);
			data.kotor();
		}
	}

	/** Sets the meter ({@code uji tempat takut}). */
	static void set(MinecraftServer server, long situsId, int value) {
		PerbaikanData data = PerbaikanData.get(server);
		PerbaikanData.TakutData f = data.takut(situsId);
		f.nilai = Math.max(0, Math.min(100, value));
		f.hari = Kota.day(server);
		data.kotor();
	}

	/** The strength of place {@code t} as a realm ordinal: the place index, the compound or village module, else its kind. */
	static int kekuatan(ServerLevel level, TempatRawat t) {
		TempatInfo info = TempatApi.tempatDi(level, t.pusat());
		if (info != null && info.kekuatan() > 0) {
			return info.kekuatan();
		}
		if (t.jenis.startsWith("sekte/kompleks")) {
			int k = KompleksApi.kekuatan(t.situsId);
			if (k > 0) {
				return k;
			}
		}
		if (t.jenis.startsWith("kampung")) {
			int k = KampungApi.kekuatan(t.situsId);
			if (k > 0) {
				return k;
			}
		}
		return t.profil().kekuatan();
	}

	/** True when {@code player} is overwhelming for place {@code t} (38.R.5) and fear is switched on. */
	static boolean berlaku(ServerPlayer player, TempatRawat t) {
		if (!PerbaikanConfig.get().takutAktif || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		int realm = Cultivators.get(player).realm().ordinal();
		return realm - kekuatan(level, t) >= PerbaikanConfig.get().selisihTakut;
	}

	/** Every 40 ticks: the visible fear of the scanned places (children hide, townsfolk bow). */
	static void tick(MinecraftServer server) {
		List<TempatRawat> places = PindaiTempat.aktif();
		if (places.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		HORMAT.values().removeIf(t -> now - t > JEDA_HORMAT * 4);
		for (TempatRawat t : places) {
			int fear = nilai(server, t.situsId);
			if (fear < AMBANG_ANAK) {
				continue;
			}
			ServerLevel level = server.getLevel(t.dimensi);
			if (level == null) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (t.jarakKotakSqr(player.blockPosition()) > 32.0D * 32.0D || !berlaku(player, t)) {
					continue;
				}
				AABB around = player.getBoundingBox().inflate(32.0D, 8.0D, 32.0D);
				for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, around, n -> n.isAlive() && n.getTarget() == null)) {
					if (npc.role() == NpcRole.ANAK) {
						BlockPos home = npc.home();
						HidupApi.klip(npc, GerakId.TAKUT_SEMBUNYI, 0, 60);
						if (home != null && npc.getNavigation().isDone()) {
							npc.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 0.9D);
						}
						continue;
					}
					if (fear < AMBANG_HORMAT || !npc.role().townsfolk() || npc.distanceToSqr(player) > 6.0D * 6.0D) {
						continue;
					}
					Long last = HORMAT.get(npc.getId());
					if (last != null && now - last < JEDA_HORMAT) {
						continue;
					}
					HORMAT.put(npc.getId(), now);
					HidupApi.tatap(npc, player);
					HidupApi.klip(npc, GerakId.HORMAT_DALAM, 0, 40);
				}
			}
		}
	}

	static void lupa() {
		HORMAT.clear();
	}

	/** The players who caused the place's fear and are present (for the fear sheet's line), or null. */
	static @Nullable UUID pemicu(MinecraftServer server, long situsId) {
		PerbaikanData.TakutData f = PerbaikanData.get(server).takut.get(situsId);
		return f == null ? null : f.pemicu;
	}

	private Takut() {
	}
}
