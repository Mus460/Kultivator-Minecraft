package dev.kultivator.client.sosok.gerak;

import dev.kultivator.client.duel.anim.DuelPoseLibrary;
import dev.kultivator.duel.blade.DuelPoseTable;
import dev.kultivator.duel.clash.WeaponClass;

import java.util.Arrays;
import java.util.List;

/**
 * Bakes the DUEL source of a clip (DESIGN 40.5.5): a sequence of {@code DuelPose} keys of DESIGN 35 (the shared
 * {@link DuelPoseTable} the server tests blades with) sampled with {@code DuelPoseLibrary.sample} and
 * {@code modelChannels} (wrist folded into the sword arm, so the blade's edge faces the cut) at {@code Ctx.NONE}, one
 * frame per tick. Sword drills (60-62, 128, 172) and paired sparring (66/67) look exactly like the duel system's cuts but
 * never touch {@code DuelPoses}, which is reserved for real duels. Each pose eases out of where the previous one ended;
 * a looping clip's first pose eases out of its last.
 */
public final class SumberDuel {

	/** Frames {@code (durasi + 1) x 24}, NaN where {@code mask} does not reach. */
	static float[] bakar(List<Klip.SegDuel> segmen, int durasi, long mask, boolean loop) {
		int n = durasi + 1;
		float[] out = new float[n * Klip.KANAL];
		Arrays.fill(out, Float.NaN);
		int jumlah = segmen.size();
		float[][] akhir = new float[jumlah][DuelPoseTable.CHANNELS];
		float[] scratch = new float[DuelPoseTable.CHANNELS];
		// two passes: the second lets a loop's first pose start from the last pose's end
		for (int pass = 0; pass < (loop ? 2 : 1); pass++) {
			for (int i = 0; i < jumlah; i++) {
				Klip.SegDuel s = segmen.get(i);
				float[] dari = i > 0 ? akhir[i - 1] : pass > 0 ? akhir[jumlah - 1] : null;
				DuelPoseLibrary.sample(s.pose(), s.durasi(), 0, dari, DuelPoseTable.NONE, scratch);
				System.arraycopy(scratch, 0, akhir[i], 0, DuelPoseTable.CHANNELS);
			}
		}
		for (int t = 0; t < n; t++) {
			int mulai = 0;
			int i = 0;
			while (i < jumlah - 1 && t >= mulai + segmen.get(i).durasi()) {
				mulai += segmen.get(i).durasi();
				i++;
			}
			Klip.SegDuel s = segmen.get(i);
			float[] dari = i > 0 ? akhir[i - 1] : loop ? akhir[jumlah - 1] : null;
			float tt = Math.min(t - mulai, s.durasi());
			DuelPoseLibrary.sample(s.pose(), tt, 0, dari, DuelPoseTable.NONE, scratch);
			float[] model = DuelPoseLibrary.modelChannels(scratch, s.cermin(), false, WeaponClass.PEDANG, false, 0.0F);
			for (int c = 0; c < Klip.KANAL_POSE; c++) {
				if ((mask & Klip.bit(c)) != 0) {
					float v = model[c];
					out[t * Klip.KANAL + c] = Float.isFinite(v) ? v : 0.0F;
				}
			}
		}
		return out;
	}

	private SumberDuel() {
	}
}
