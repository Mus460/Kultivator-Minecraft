package dev.kultivator.dunia.kota42.cakap;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Outcome of one player choice ({@link Cakap#pilih}, DESIGN 42.5A.7). Effects (impression, emotion, price factor, quest,
 * gossip, law, Infami) are already applied by the engine when this is returned; this record is what the dialog sheet
 * shows next. Frozen shape (C42.K1, 42-G0).
 *
 * @param berhasil   whether the tone check succeeded (always true for checks-free intents)
 * @param balasan    the NPC's answer turn (1-3 utterances, may be empty)
 * @param kesan      impression change applied to the player (for the floating "+3" hint)
 * @param tahap      state after the turn
 * @param opsi       options for the next choice (empty when {@link #selesai})
 * @param isyarat    mood hint instead of a success percent ("Dia tampak tidak sabar"), null for none
 * @param peluang    success chance 0-1 of the check, shown only when {@code tampilPeluang} is on; -1 when no check ran
 * @param selesai    the conversation ended (closing line is the last of {@link #balasan})
 */
public record Hasil(boolean berhasil, List<Ujaran> balasan, int kesan, SesiCakap.Tahap tahap, List<SesiCakap.Opsi> opsi,
		@Nullable Component isyarat, float peluang, boolean selesai) {

	public Hasil {
		balasan = List.copyOf(balasan);
		opsi = List.copyOf(opsi);
	}

	/** An ended conversation with no further text (engine off, stub, invalid session). */
	public static Hasil tutup() {
		return new Hasil(true, List.of(), 0, SesiCakap.Tahap.PENUTUP, List.of(), null, -1.0F, true);
	}
}
