package dev.kultivator.dunia.tempat.perbaikan;

import dev.kultivator.dunia.kota.Kota;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Repair and compensation public face (contract C38.PR1, owner 38PR, DESIGN 38.R). Server thread only; every answer is
 * neutral (0 / false / null / 1.0) while {@code perbaikan.aktif} is off or before the server started.
 * <ul>
 *   <li>{@link #takut}: the fear meter of a place (38.R.5). Owners of dialogue, shops, quests and the tea house read it:
 *       20+ stuttering lines, a 10 % "offering" discount ({@link #diskonPersembahan}), quests politely declined, no
 *       rumours shared; 60+ deep bows and guards that look away (crimes still recorded).</li>
 *   <li>{@link #adaKerusakan}, {@link #tagihanTerbuka}: damage waiting for repair, the player's open bill.</li>
 *   <li>{@link #faktorHarga}: 1.10 for 7 days after a refused bill at that place (38.R.4), for the shop owners.</li>
 *   <li>{@link #ditakuti}: true when the place is too afraid of this player to bill him (strength gap, 38.R.5).</li>
 *   <li>42F (DESIGN 42.9.4-42.9.5): {@link #kerjakan} hands a growth job to the same crews (blocks only through
 *       {@code WorldBreaker.submitRestore}, reason {@code "tumbuh"}, never over a foreign block), {@link #berjalan} /
 *       {@link #sisa} / {@link #regu} follow it, {@link #terimaKotak} lets the blueprint take a finished plot,
 *       {@link #cetakDi} reads the record, {@link #setPengali} speeds a place's crews (rebuild projects),
 *       {@link #rusakTerbuka} counts real damage still waiting.</li>
 * </ul>
 */
public final class PerbaikanApi {

	private static @Nullable MinecraftServer server() {
		return Perbaikan.server();
	}

	/** Fear meter 0..100 of place {@code situsId} toward overwhelmingly strong visitors (38.R.5). */
	public static int takut(long situsId) {
		MinecraftServer s = server();
		if (s == null || !PerbaikanConfig.get().aktif || !PerbaikanConfig.get().takutAktif) {
			return 0;
		}
		return Takut.nilai(s, situsId);
	}

	/** True while place {@code situsId} has unrepaired recorded damage. */
	public static boolean adaKerusakan(long situsId) {
		MinecraftServer s = server();
		if (s == null || !PerbaikanConfig.get().aktif) {
			return false;
		}
		for (Gugus g : PerbaikanData.get(s).gugus.values()) {
			if (g.situsId == situsId && !g.tuntas() && !PekerjaanPerbaikan.tumbuh(g)) {
				return true;
			}
		}
		return false;
	}

	/** The open bill of {@code player} (waiting for the collector or shown and not answered), or null. */
	public static @Nullable Tagihan tagihanTerbuka(ServerPlayer player) {
		if (player == null || !PerbaikanConfig.get().aktif) {
			return null;
		}
		PerbaikanData.TagihanData bill = Penagihan.terbuka(player.level().getServer(), player.getUUID());
		return bill == null ? null : bill.snapshot();
	}

	/** Price factor for {@code player} at place {@code situsId}: 1.10 for 7 world days after he refused a bill there. */
	public static double faktorHarga(ServerPlayer player, long situsId) {
		if (player == null || !PerbaikanConfig.get().aktif) {
			return 1.0D;
		}
		MinecraftServer s = player.level().getServer();
		PerbaikanData.PemainTempat pt = PerbaikanData.get(s).pemainAda(player.getUUID(), situsId);
		return pt != null && pt.hargaNaikSampai >= Kota.day(s) ? 1.10D : 1.0D;
	}

	/** True when place {@code situsId} is too afraid of {@code player} to bill him (38.R.5). */
	public static boolean ditakuti(ServerPlayer player, long situsId) {
		if (player == null || !PerbaikanConfig.get().aktif) {
			return false;
		}
		TempatRawat t = Rekam.bySitus(situsId);
		return t != null && Takut.berlaku(player, t);
	}

	/** The "offering" discount a frightened place gives {@code player} (0.10 at fear 20+ when he is feared, else 0). */
	public static double diskonPersembahan(ServerPlayer player, long situsId) {
		return takut(situsId) >= Takut.AMBANG_ANAK && ditakuti(player, situsId) ? 0.10D : 0.0D;
	}

	/** Help points of {@code player} at place {@code situsId} (38.R.6; 100 = "Sahabat"). */
	public static int poinBantu(ServerPlayer player, long situsId) {
		if (player == null) {
			return 0;
		}
		PerbaikanData.PemainTempat pt = PerbaikanData.get(player.level().getServer()).pemainAda(player.getUUID(), situsId);
		return pt == null ? 0 : pt.poinBantu;
	}

	// ================================================================== growth and rebuild hooks (42F, DESIGN 42.9.4-42.9.5)

	/**
	 * A block job for the repair crews that is not a repair (a growth stage of a plot): world positions (packed), the
	 * blocks to put there and the state each position must still hold for the block to go in (the blueprint record, or
	 * air where nothing is recorded). Positions whose expected state already equals the target are skipped.
	 */
	public record Pekerjaan(ServerLevel level, long situsId, long[] posisi, BlockState[] target, BlockState[] expected) {
	}

	/** Rate multiplier of a place's crews ({@code tumbuh}: a growth job, else a repair); 1.0 = as built. */
	@FunctionalInterface
	public interface Pengali {
		double kali(long situsId, boolean tumbuh);
	}

	private static @Nullable Pengali pengali;

	/** 42F: the place multiplier (rebuild projects, migrant crews, growth speed); null removes it. */
	public static void setPengali(@Nullable Pengali value) {
		pengali = value;
	}

	static double pengali(long situsId, boolean tumbuh) {
		Pengali p = pengali;
		if (p == null) {
			return 1.0D;
		}
		try {
			double v = p.kali(situsId, tumbuh);
			return Double.isFinite(v) ? Math.max(0.1D, Math.min(v, 4.0D)) : 1.0D;
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.debug("Kultivator: repair multiplier failed", exception);
			return 1.0D;
		}
	}

	/**
	 * Hands job {@code p} to the crews of the cared-for place whose piece holds one of its positions (bodies, props and
	 * clips of 38.R; one block per craftsman per {@code detikPerBlok} seconds, the place cap shared with repairs). Returns
	 * the job id, or 0 when repairs are off, the place is not cared for, or nothing differs.
	 */
	public static long kerjakan(Pekerjaan p) {
		if (p == null || p.level() == null || p.posisi().length == 0 || p.target().length != p.posisi().length
				|| p.expected().length != p.posisi().length || !PerbaikanConfig.get().aktif || server() == null) {
			return 0L;
		}
		TempatRawat t = null;
		for (int i = 0; i < p.posisi().length && t == null; i += 7) {
			t = Rekam.tempatDi(p.level(), BlockPos.of(p.posisi()[i]));
		}
		if (t == null) {
			t = Rekam.bySitus(p.situsId());
		}
		return t == null ? 0L : PekerjaanPerbaikan.tumbuhBaru(p.level(), t, p.posisi(), p.target(), p.expected());
	}

	/** True while job {@code id} of {@link #kerjakan} still has open operations. */
	public static boolean berjalan(long id) {
		MinecraftServer s = server();
		if (s == null || id == 0L) {
			return false;
		}
		Gugus g = PerbaikanData.get(s).gugus.get(id);
		return g != null && !g.tuntas();
	}

	/** {open, done, given up} operations of job {@code id} (all 0 once the job is gone). */
	public static int[] sisa(long id) {
		MinecraftServer s = server();
		Gugus g = s == null ? null : PerbaikanData.get(s).gugus.get(id);
		int[] out = new int[3];
		if (g != null) {
			for (Gugus.Op op : g.ops) {
				out[op.terbuka() ? 0 : op.status == Gugus.SELESAI ? 1 : 2]++;
			}
		}
		return out;
	}

	/** Craftsmen working on job {@code id} now (bodies at their spot; 0 while nobody is near). */
	public static int regu(long id) {
		MinecraftServer s = server();
		Gugus g = s == null ? null : PerbaikanData.get(s).gugus.get(id);
		return g == null ? 0 : Tukang.bekerja(g);
	}

	/** The blueprint state at {@code pos} (what repairs would put back), or null when nothing is recorded there. */
	public static @Nullable BlockState cetakDi(ServerLevel level, BlockPos pos) {
		TempatRawat t = Rekam.tempatDi(level, pos);
		Cetak c = t == null ? null : Rekam.cetak(t, net.minecraft.world.level.ChunkPos.pack(pos));
		return c == null ? null : c.di(pos);
	}

	/**
	 * The blueprint takes the world's blocks inside {@code kotak} (a finished plot building: from now on 38.R repairs it).
	 * Only loaded chunks; returns the voxels that changed in the record.
	 */
	public static int terimaKotak(ServerLevel level, BoundingBox kotak) {
		if (level == null || kotak == null) {
			return 0;
		}
		int n = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = kotak.minX(); x <= kotak.maxX(); x++) {
			for (int z = kotak.minZ(); z <= kotak.maxZ(); z++) {
				for (int y = kotak.minY(); y <= kotak.maxY(); y++) {
					at.set(x, y, z);
					if (!level.isLoaded(at)) {
						continue;
					}
					TempatRawat t = Rekam.tempatDi(level, at);
					Cetak c = t == null ? null : Rekam.cetak(t, net.minecraft.world.level.ChunkPos.pack(at));
					BlockState rec = c == null ? null : c.di(at);
					BlockState now = level.getBlockState(at);
					if (rec != null && rec != now && PekerjaanPerbaikan.terimaCetak(t, at.immutable(), now)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	/** Real damage blocks still waiting for repair at place {@code situsId} (growth jobs left out). */
	public static int rusakTerbuka(long situsId) {
		MinecraftServer s = server();
		if (s == null || !PerbaikanConfig.get().aktif) {
			return 0;
		}
		int n = 0;
		for (Gugus g : PerbaikanData.get(s).gugus.values()) {
			if (g.situsId == situsId && !PekerjaanPerbaikan.tumbuh(g)) {
				n += g.sisa();
			}
		}
		return n;
	}

	// ================================================================== damage attribution hooks (mixins, 38.R.2)

	/** A destroying explosion is about to break {@code blok}; {@code sumber} is its indirect source (may be null). */
	public static void ledakan(net.minecraft.server.level.ServerLevel level, java.util.List<net.minecraft.core.BlockPos> blok,
			net.minecraft.world.entity.@Nullable Entity sumber) {
		try {
			Perbaikan.onLedakan(level, blok, sumber);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.debug("Kultivator: explosion attribution failed", exception);
		}
	}

	/** A non-player entity destroyed, placed or changed the block at {@code pos}. */
	public static void ubahBlok(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity pelaku) {
		try {
			Perbaikan.onUbahBlok(level, pos, pelaku);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.debug("Kultivator: block change attribution failed", exception);
		}
	}

	/** Fire is eating the block at {@code pos}. */
	public static void terbakar(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		try {
			Perbaikan.onTerbakar(level, pos);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.debug("Kultivator: fire attribution failed", exception);
		}
	}

	private PerbaikanApi() {
	}
}
