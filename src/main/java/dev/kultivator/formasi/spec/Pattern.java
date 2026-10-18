package dev.kultivator.formasi.spec;

import dev.kultivator.formasi.FormasiItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A formation pattern (DESIGN 24.2.2): layers of character grids around the Mata Formasi {@code '@'} (layer 0, exactly
 * once), a legend of {@link Rule}s and the heart block under ({@code dy −1}) or on ({@code dy +1}) the eye. North (−z) is
 * the top row; rows map to dz and columns to dx, both centred on {@code '@'}; spaces are optional; {@code '.'} means
 * "don't care". Layers of another size are centred on the layer-0 grid. Rotations are clockwise quarter turns,
 * {@code (dx, dz) → (−dz, dx)}, never mirrored. Slots are stored nearest-first so a mismatch fails early. Immutable; a
 * pattern that breaks the bounds keeps its {@link #error()} and the registry refuses its spec.
 */
public final class Pattern {

	/** Limits of 24.2.2. */
	public static final int MAX_DXZ = 16;
	public static final int MIN_DY = -3;
	public static final int MAX_DY = 5;
	public static final int MAX_SLOTS = 160;
	/** The legend key of heart slots. */
	public static final char HEART = '^';

	private final Map<Character, Rule> legend;
	private final List<List<Slot>> rotated;
	private final int distinctRotations;
	private final int radius;
	private final int minDy;
	private final int maxDy;
	private final @Nullable String error;

	private Pattern(Map<Character, Rule> legend, List<Slot> base, @Nullable String error) {
		this.legend = Collections.unmodifiableMap(new LinkedHashMap<>(legend));
		this.error = error;
		List<List<Slot>> rotations = new ArrayList<>(4);
		for (int rot = 0; rot < 4; rot++) {
			List<Slot> list = new ArrayList<>(base.size());
			for (Slot slot : base) {
				int dx = slot.dx();
				int dz = slot.dz();
				for (int i = 0; i < rot; i++) {
					int turned = -dz;
					dz = dx;
					dx = turned;
				}
				list.add(new Slot(dx, slot.dy(), dz, slot.key(), slot.rule()));
			}
			list.sort(NEAREST);
			rotations.add(Collections.unmodifiableList(list));
		}
		this.rotated = Collections.unmodifiableList(rotations);
		int r = 0;
		int lo = 0;
		int hi = 0;
		boolean directional = false;
		for (Slot slot : base) {
			r = Math.max(r, Math.max(Math.abs(slot.dx()), Math.abs(slot.dz())));
			lo = Math.min(lo, slot.dy());
			hi = Math.max(hi, slot.dy());
			directional |= slot.rule().directional();
		}
		this.radius = r;
		this.minDy = lo;
		this.maxDy = hi;
		if (directional || base.isEmpty()) {
			this.distinctRotations = base.isEmpty() ? 1 : 4;
		} else if (same(rotations.get(0), rotations.get(1))) {
			this.distinctRotations = 1;
		} else if (same(rotations.get(0), rotations.get(2))) {
			this.distinctRotations = 2;
		} else {
			this.distinctRotations = 4;
		}
	}

	private static final Comparator<Slot> NEAREST = Comparator.comparingInt((Slot s) -> s.dx() * s.dx() + s.dz() * s.dz() + s.dy() * s.dy())
			.thenComparingInt(Slot::dy).thenComparingInt(Slot::dz).thenComparingInt(Slot::dx);

	private static boolean same(List<Slot> a, List<Slot> b) {
		if (a.size() != b.size()) {
			return false;
		}
		Set<Long> keys = new HashSet<>();
		for (Slot slot : a) {
			keys.add(key(slot));
		}
		for (Slot slot : b) {
			if (!keys.contains(key(slot))) {
				return false;
			}
		}
		return true;
	}

	private static long key(Slot slot) {
		return ((long) (slot.dx() + 64) << 40) | ((long) (slot.dy() + 64) << 32) | ((long) (slot.dz() + 64) << 24) | slot.key();
	}

	/** The cared slots in rotation {@code rot} (0-3), nearest first; the eye itself is not a slot. */
	public List<Slot> slots(int rot) {
		return rotated.get(Math.floorMod(rot, 4));
	}

	/** 1, 2 or 4: rotations that give different slot sets (directional patterns always 4). */
	public int distinctRotations() {
		return distinctRotations;
	}

	/** The largest horizontal offset of any slot (Chebyshev). */
	public int radius() {
		return radius;
	}

	public int minDy() {
		return minDy;
	}

	public int maxDy() {
		return maxDy;
	}

	/** The legend, heart included under {@link #HEART}. */
	public Map<Character, Rule> legend() {
		return legend;
	}

	/** Null for a valid pattern, else why the registry refuses it. */
	public @Nullable String error() {
		return error;
	}

	/** Pure emission (bill, hologram, 6b): every slot's canonical state plus the eye ({@code core = true}). */
	public List<PatternBlock> emit(int rot) {
		List<Slot> slots = slots(rot);
		List<PatternBlock> out = new ArrayList<>(slots.size() + 1);
		out.add(new PatternBlock(0, 0, 0, FormasiItems.FORMATION_EYE.defaultBlockState(), FormasiItems.FORMATION_EYE_ITEM, true));
		for (Slot slot : slots) {
			out.add(new PatternBlock(slot.dx(), slot.dy(), slot.dz(), slot.rule().canonical(rot), slot.rule().exactItem(), false));
		}
		return out;
	}

	/** How many slots each rule has (the eye is not counted), in legend order. */
	public Map<Rule, Integer> bill() {
		Map<Rule, Integer> counts = new LinkedHashMap<>();
		for (Rule rule : legend.values()) {
			counts.put(rule, 0);
		}
		for (Slot slot : slots(0)) {
			counts.merge(slot.rule(), 1, Integer::sum);
		}
		counts.values().removeIf(count -> count == 0);
		return counts;
	}

	/** The total number of cared slots. */
	public int size() {
		return rotated.get(0).size();
	}

	/** An empty pattern (legacy mirrors without a grid). */
	public static Pattern empty() {
		return new Pattern(Map.of(), List.of(), null);
	}

	/** Starts a pattern builder (normally through {@code Builder.pattern(p -> ...)}). */
	public static Builder builder() {
		return new Builder();
	}

	/** The pattern DSL of 24.2.2. */
	public static final class Builder {
		private final TreeMap<Integer, String[]> layers = new TreeMap<>();
		private final Map<Character, Rule> legend = new LinkedHashMap<>();
		private final Map<Integer, Rule> hearts = new HashMap<>();
		private @Nullable String error;

		private Builder() {
		}

		/** Layer {@code dy} (−3 … +5) as rows from north to south. */
		public Builder layer(int dy, String... rows) {
			if (layers.containsKey(dy)) {
				fail("layer " + dy + " twice");
			}
			String[] clean = new String[rows.length];
			for (int i = 0; i < rows.length; i++) {
				clean[i] = rows[i].replace(" ", "");
			}
			layers.put(dy, clean);
			return this;
		}

		/** What character {@code c} means. */
		public Builder key(char c, Rule rule) {
			if (c == '.' || c == '@' || c == ' ' || c == HEART) {
				fail("reserved key " + c);
			} else {
				legend.put(c, rule);
			}
			return this;
		}

		/** The heart under ({@code −1}) or on ({@code +1}) the eye. */
		public Builder heart(int dy, Rule rule) {
			if (dy != -1 && dy != 1) {
				fail("heart dy " + dy);
			} else {
				hearts.put(dy, rule);
			}
			return this;
		}

		private void fail(String why) {
			if (error == null) {
				error = why;
			}
		}

		/** Builds the pattern; problems end up in {@link Pattern#error()} instead of throwing. */
		public Pattern build() {
			List<Slot> slots = new ArrayList<>();
			String[] zero = layers.get(0);
			int eyeRow = -1;
			int eyeCol = -1;
			int eyes = 0;
			if (zero == null) {
				fail("no layer 0");
			} else {
				for (int row = 0; row < zero.length; row++) {
					for (int col = 0; col < zero[row].length(); col++) {
						if (zero[row].charAt(col) == '@') {
							eyes++;
							eyeRow = row;
							eyeCol = col;
						}
					}
				}
			}
			if (eyes != 1) {
				fail("needs exactly one @, found " + eyes);
			}
			if (error == null) {
				int height0 = zero.length;
				int width0 = 0;
				for (String row : zero) {
					width0 = Math.max(width0, row.length());
				}
				Set<Long> used = new HashSet<>();
				for (Map.Entry<Integer, String[]> layer : layers.entrySet()) {
					int dy = layer.getKey();
					String[] rows = layer.getValue();
					int width = 0;
					for (String row : rows) {
						width = Math.max(width, row.length());
					}
					int shiftCol = (width0 - width) / 2;
					int shiftRow = (height0 - rows.length) / 2;
					for (int row = 0; row < rows.length; row++) {
						for (int col = 0; col < rows[row].length(); col++) {
							char c = rows[row].charAt(col);
							if (c == '.' || c == '@') {
								if (c == '@' && dy != 0) {
									fail("@ outside layer 0");
								}
								continue;
							}
							Rule rule = legend.get(c);
							if (rule == null) {
								fail("unknown key " + c);
								continue;
							}
							int dx = col + shiftCol - eyeCol;
							int dz = row + shiftRow - eyeRow;
							add(slots, used, dx, dy, dz, c, rule);
						}
					}
				}
				for (Map.Entry<Integer, Rule> heart : hearts.entrySet()) {
					add(slots, used, 0, heart.getKey(), 0, HEART, heart.getValue());
				}
				if (slots.size() > MAX_SLOTS) {
					fail("too many slots " + slots.size());
				}
			}
			Map<Character, Rule> all = new LinkedHashMap<>(legend);
			for (Rule heart : hearts.values()) {
				all.putIfAbsent(HEART, heart);
			}
			return error == null ? new Pattern(all, slots, null) : new Pattern(all, List.of(), error);
		}

		private void add(List<Slot> slots, Set<Long> used, int dx, int dy, int dz, char c, Rule rule) {
			if (Math.abs(dx) > MAX_DXZ || Math.abs(dz) > MAX_DXZ || dy < MIN_DY || dy > MAX_DY) {
				fail("out of bounds " + dx + "," + dy + "," + dz);
				return;
			}
			if (dx == 0 && dy == 0 && dz == 0) {
				fail("slot on the eye");
				return;
			}
			long packed = ((long) (dx + 64) << 32) | ((long) (dy + 64) << 16) | (dz + 64);
			if (!used.add(packed)) {
				fail("two slots at " + dx + "," + dy + "," + dz);
				return;
			}
			slots.add(new Slot(dx, dy, dz, c, rule));
		}
	}
}
