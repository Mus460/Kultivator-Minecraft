package dev.kultivator.sekte.mandiri.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Contract C39.C3 (DESIGN 39.F.2, 39.F.9): an encounter source asked by 39C's stage engine each time a journey stage
 * falls due (never on a tick of its own). 39F's {@code Pertemuan} registers here for meetings with disciples of other
 * sects (tea-house insults, treasure quarrels, patrols...). Providers are asked in registration order; the first non-null
 * result is applied to the stage (wound, purse, memories, stage length) and the others are not asked for that stage.
 * Resolution must be deterministic from {@link Pertemuan#benih} so a body shown later sees the same outcome. Server side
 * only.
 */
@FunctionalInterface
public interface PertemuanProvider {

	/**
	 * The stage that fell due.
	 *
	 * @param server     the server
	 * @param owner      hall owner
	 * @param soul       the leading disciple
	 * @param rombongan  every travelling soul (leader first)
	 * @param jenis      journey kind
	 * @param tahap      stage kind: {@code jalan}, {@code kedai_teh}, {@code pasar}, {@code pasar_malam}, {@code gilda},
	 *                   {@code arena}, {@code berburu}, {@code reruntuhan}, {@code alam}, {@code desa}, {@code gerbang_sekte}
	 * @param situs      site id of the place, or null on an open road
	 * @param pos        where the stage happens (estimated on the route)
	 * @param hari       world day
	 * @param benih      the stage seed ({@code soul ^ departure ^ stage index})
	 * @param terlihat   a player is within 128 blocks (or a town body of ours is shown): a body may be spawned
	 */
	record Pertemuan(MinecraftServer server, UUID owner, UUID soul, List<UUID> rombongan, JenisJalan jenis, String tahap,
					 @Nullable String situs, BlockPos pos, long hari, long benih, boolean terlihat) {
		public Pertemuan {
			rombongan = rombongan == null ? List.of() : List.copyOf(rombongan);
			tahap = tahap == null ? "" : tahap;
			pos = pos == null ? BlockPos.ZERO : pos.immutable();
		}
	}

	/**
	 * What the encounter did to the stage.
	 *
	 * @param kunciKisah     the travel-scroll line for this stage (full key)
	 * @param arg            its arguments (names, sect, town)
	 * @param luka           wound of the leading disciple
	 * @param deltaDompet    purse change in NB (applied through {@code MandiriApi.ubahDompet}, cause {@code seteru})
	 * @param deltaTahapTick added (or removed) stage length in ticks
	 * @param kenangan       memory kinds for the party ({@code mandiri.seteru.*})
	 * @param entri          a diary entry for the leader, or null
	 */
	record HasilPertemuan(String kunciKisah, List<String> arg, JenisLuka luka, long deltaDompet, int deltaTahapTick,
						  List<String> kenangan, @Nullable EntriHarian entri) {
		public HasilPertemuan {
			kunciKisah = kunciKisah == null ? "" : kunciKisah;
			arg = arg == null ? List.of() : List.copyOf(arg);
			luka = luka == null ? JenisLuka.TIDAK : luka;
			kenangan = kenangan == null ? List.of() : List.copyOf(kenangan);
		}
	}

	/** The encounter's result, or null when nothing happens at this stage. */
	@Nullable HasilPertemuan coba(Pertemuan pertemuan);
}
