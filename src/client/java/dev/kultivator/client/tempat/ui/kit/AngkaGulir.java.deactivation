package dev.kultivator.client.tempat.ui.kit;

/**
 * A number that rolls to its new value over 300 ms when it changes (DESIGN 38.U.2 "angka uang bergulir"): money on hand,
 * auction price, balances. The first value shows at once.
 */
public final class AngkaGulir {

	private static final long DURASI_MS = 300L;

	private long dari;
	private long ke;
	private long mulai;
	private boolean ada;

	/** The value to show now for target {@code nilai}. */
	public long nilai(long nilai, long kini) {
		if (!ada) {
			ada = true;
			dari = nilai;
			ke = nilai;
			mulai = kini - DURASI_MS;
		} else if (nilai != ke) {
			dari = nilai(ke, kini, true);
			ke = nilai;
			mulai = kini;
		}
		return nilai(ke, kini, true);
	}

	private long nilai(long target, long kini, boolean ignored) {
		float t = Math.min(1.0F, (kini - mulai) / (float) DURASI_MS);
		float e = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
		return dari + Math.round((target - dari) * e);
	}

	/** True while rolling (draw it highlighted). */
	public boolean bergulir(long kini) {
		return ada && kini - mulai < DURASI_MS && dari != ke;
	}

	/** +1 rising, -1 falling, 0 still. */
	public int arah(long kini) {
		if (!bergulir(kini)) {
			return 0;
		}
		return ke > dari ? 1 : -1;
	}
}
