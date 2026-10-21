package dev.kultivator.client.guide;

import dev.kultivator.network.Payloads;

import java.util.BitSet;

/**
 * Reads the shared guide payloads for the client (the only place that knows their fields): the done and manual bitsets
 * over GuideChapters order, the current chapter, the open flag, and builds a {@code GuideAction}.
 */
final class GuidePayloads {

	static BitSet done(Payloads.GuideSync payload) {
		return bits(payload.done());
	}

	static BitSet manual(Payloads.GuideSync payload) {
		return bits(payload.manual());
	}

	static int current(Payloads.GuideSync payload) {
		return payload.current();
	}

	static boolean open(Payloads.GuideSync payload) {
		return payload.open();
	}

	static Payloads.GuideAction action(String text) {
		return new Payloads.GuideAction(text);
	}

	// One overload per bitset form the payload may carry; the compiler picks the one that matches.

	private static BitSet bits(BitSet bits) {
		return bits == null ? new BitSet() : (BitSet) bits.clone();
	}

	private static BitSet bits(long[] words) {
		return words == null ? new BitSet() : BitSet.valueOf(words);
	}

	private static BitSet bits(byte[] bytes) {
		return bytes == null ? new BitSet() : BitSet.valueOf(bytes);
	}

	private static BitSet bits(long word) {
		return BitSet.valueOf(new long[]{word});
	}

	private GuidePayloads() {
	}
}
