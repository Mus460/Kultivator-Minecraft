package dev.kultivator.dunia.tempat.perbaikan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The attribution log of DESIGN 38.R.2: a five-minute ring of events per cared-for place (memory only), read when the
 * scanner finds a difference. Matching rules, newest event first:
 * <ul>
 *   <li>{@code pecah}: a player broke exactly this block;</li>
 *   <li>{@code pasang}: a player placed a block here (the difference is a foreign block);</li>
 *   <li>{@code ledakan_tnt}: TNT primed within 8 blocks in the last two minutes (its owner);</li>
 *   <li>{@code api}: flint and steel or a fire charge used within 8 blocks in the last two minutes, and the block burned
 *       (fire now, or air where a block was);</li>
 *   <li>{@code kehancuran}: a Mode Kehancuran batch of the player's own technique or his disciple changed a box that holds
 *       this block (the master);</li>
 *   <li>otherwise {@code serangan} while a raid or city defence is running at the place, else {@code monster}.</li>
 * </ul>
 */
public final class LogAtribusi {

	/** How long an event is kept (ticks: five minutes). */
	public static final long UMUR = 6000L;
	/** Events kept per place at most. */
	public static final int MAKS = 512;
	private static final long JENDELA_DEKAT = 2400L;

	/** One logged event: {@code box} is a single block for break/place, a sphere box for TNT/fire, a batch box for casts. */
	public record Peristiwa(long waktu, String sebab, @Nullable UUID pelaku, BoundingBox kotak) {
	}

	/** An attribution: the cause and its player (null for monsters and raids). */
	public record Atribusi(String sebab, @Nullable UUID pelaku) {
	}

	private static final Map<Long, ArrayDeque<Peristiwa>> LOG = new HashMap<>();
	/** Place id -> game time until which an attack on the place is running (raids, city defence, themed raids). */
	private static final Map<Long, Long> SERANGAN = new HashMap<>();

	static void catat(long kunci, long now, String sebab, @Nullable UUID pelaku, BoundingBox kotak) {
		ArrayDeque<Peristiwa> ring = LOG.computeIfAbsent(kunci, k -> new ArrayDeque<>());
		ring.addLast(new Peristiwa(now, sebab, pelaku, kotak));
		while (ring.size() > MAKS || !ring.isEmpty() && now - ring.peekFirst().waktu() > UMUR) {
			ring.pollFirst();
		}
	}

	static void catatBlok(long kunci, long now, String sebab, @Nullable UUID pelaku, BlockPos pos) {
		catat(kunci, now, sebab, pelaku, new BoundingBox(pos));
	}

	static void catatSekitar(long kunci, long now, String sebab, @Nullable UUID pelaku, BlockPos pos, int radius) {
		catat(kunci, now, sebab, pelaku, new BoundingBox(pos).inflatedBy(radius));
	}

	/** Marks an attack on place {@code situsId} running until {@code sampai} (game time). */
	static void serangan(long situsId, long sampai) {
		SERANGAN.merge(situsId, sampai, Math::max);
	}

	static void seranganSelesai(long situsId) {
		SERANGAN.remove(situsId);
	}

	static boolean diserang(long situsId, long now) {
		Long until = SERANGAN.get(situsId);
		return until != null && until > now;
	}

	/** The cause of a difference at {@code pos} found at game time {@code now}. */
	static Atribusi cari(TempatRawat t, BlockPos pos, BlockState rec, BlockState now, long time) {
		ArrayDeque<Peristiwa> ring = LOG.get(t.kunci);
		if (ring != null) {
			boolean burned = now.getBlock() instanceof BaseFireBlock || now.isAir() && !rec.isAir();
			boolean foreign = !now.isAir() && now.getBlock() != rec.getBlock() && !(now.getBlock() instanceof BaseFireBlock);
			for (Iterator<Peristiwa> it = ring.descendingIterator(); it.hasNext(); ) {
				Peristiwa e = it.next();
				long age = time - e.waktu();
				if (age > UMUR || age < -40L) {
					continue;
				}
				boolean inside = e.kotak().isInside(pos);
				switch (e.sebab()) {
					case Gugus.PECAH -> {
						if (inside) {
							return new Atribusi(Gugus.PECAH, e.pelaku());
						}
					}
					case Gugus.PASANG -> {
						if (inside && foreign) {
							return new Atribusi(Gugus.PASANG, e.pelaku());
						}
					}
					case Gugus.TNT -> {
						if (inside && age <= JENDELA_DEKAT) {
							return new Atribusi(Gugus.TNT, e.pelaku());
						}
					}
					case Gugus.API -> {
						if (inside && burned && age <= JENDELA_DEKAT) {
							return new Atribusi(Gugus.API, e.pelaku());
						}
					}
					case Gugus.KEHANCURAN -> {
						if (inside && age <= JENDELA_DEKAT) {
							return new Atribusi(Gugus.KEHANCURAN, e.pelaku());
						}
					}
					case Gugus.MONSTER, Gugus.SERANGAN -> {
						if (inside) {
							return new Atribusi(e.sebab(), null);
						}
					}
					default -> {
					}
				}
			}
		}
		return new Atribusi(diserang(t.situsId, time) ? Gugus.SERANGAN : Gugus.MONSTER, null);
	}

	static void bersihkan(long now) {
		for (Iterator<ArrayDeque<Peristiwa>> it = LOG.values().iterator(); it.hasNext(); ) {
			ArrayDeque<Peristiwa> ring = it.next();
			while (!ring.isEmpty() && now - ring.peekFirst().waktu() > UMUR) {
				ring.pollFirst();
			}
			if (ring.isEmpty()) {
				it.remove();
			}
		}
		SERANGAN.values().removeIf(until -> until <= now);
	}

	static void lupa() {
		LOG.clear();
		SERANGAN.clear();
	}

	private LogAtribusi() {
	}
}
