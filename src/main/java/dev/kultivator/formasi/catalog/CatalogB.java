package dev.kultivator.formasi.catalog;

import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.Grade;
import dev.kultivator.formasi.spec.Pattern;
import dev.kultivator.formasi.spec.Rule;
import dev.kultivator.formasi.spec.Rules;
import dev.kultivator.formasi.spec.Scope;
import dev.kultivator.formasi.spec.SpecSink;
import dev.kultivator.formasi.spec.Specs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Catalogue B (DESIGN 24.4.7-24.4.11, netIds 400-799): alarm and concealment (K1, K4, M2), illusion, maze and control
 * (M1, L2, B4), killing (B1, B2, B3, L3, L4), transport (M3, L5) and the grand combined arrays (A1, A2). Every pattern is
 * written as offsets from the Mata Formasi (north = −z) exactly as the cards draw them; the numbers the handlers read are
 * the card numbers at P = 1. Text keys: {@code kultivator.formasi.spec.<id>.name|desc|hint|source} (lang-src/formasib).
 */
public final class CatalogB {

	// ---- effect handler ids (registered by FormasiB)
	public static final String ALARM = "alarm";
	public static final String CONCEAL = "conceal";
	public static final String CONCEAL_DETECT = "conceal_detect";
	public static final String MAZE = "maze";
	public static final String RED_SAND = "red_sand";
	public static final String KILLING = "killing";
	public static final String TRANSPORT = "transport";
	public static final String GRAND_A1 = "grand_a1";
	public static final String GRAND_A2 = "grand_a2";

	// ---- killing kinds (param "kind")
	public static final int KILL_SWORD = 1;
	public static final int KILL_HEAVEN = 2;
	public static final int KILL_EARTH = 3;
	public static final int KILL_BLAZE = 4;
	public static final int KILL_WATER = 5;

	/** Registers the 15 catalogue-B specs. */
	public static void register(SpecSink r) {
		Rule fireBody = Rules.labelled(Rules.anyOf(Blocks.POLISHED_CINNABAR, Blocks.CINNABAR_BRICKS, Blocks.RED_NETHER_BRICKS),
				"kultivator.formasib.rule.fire_body");
		Rule k4Stone = Rules.labelled(Rules.anyOf(Blocks.DARK_PRISMARINE, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE),
				"kultivator.formasib.rule.shadow_stone");
		Rule azalea = Rules.labelled(Rules.anyOf(Blocks.FLOWERING_AZALEA_LEAVES, Blocks.AZALEA_LEAVES), "kultivator.formasib.rule.azalea");
		Rule copperLantern = Rules.copper(Blocks.COPPER_LANTERN);
		Rule endLight = Rules.labelled(Rules.anyOf(Blocks.END_ROD, Blocks.SOUL_LANTERN, Blocks.LANTERN), "kultivator.formasib.rule.end_light");
		Rule endStone = Rules.labelled(Rules.anyOf(Blocks.END_STONE_BRICKS, Blocks.SMOOTH_QUARTZ, Blocks.CALCITE), "kultivator.formasib.rule.end_stone");
		Rule pillar = Rules.labelled(Rules.anyOf(Blocks.PURPUR_PILLAR, Blocks.QUARTZ_PILLAR), "kultivator.formasib.rule.pillar");
		Rule cutCopper = Rules.copper(Blocks.CUT_COPPER);
		Rule chiseledCopper = Rules.copper(Blocks.CHISELED_COPPER);

		// ================================================================ K1 Larangan Peringatan / Alarm Restriction
		r.add(Specs.spec("alarm_restriction", 400, Family.PERINGATAN).grade(Grade.KUNING)
				.radius(24, 2, 32).pulseTicks(20)
				.pattern(p -> new Grid(3, 3)
						.four(3, 0, 0, 'S', 'c')
						.apply(p)
						.key('S', Rules.block(Blocks.CHISELED_STONE_BRICKS))
						.key('c', copperLantern)
						.heart(1, Rules.block(Blocks.BELL)))
				.effect(ALARM).param("ring", 8.0D).param("grade", 1.0D)
				.flag(Flag.PERSONAL_OK)
				.build());

		// ================================================================ K4 Larangan Penyembunyi / Concealment Restriction
		r.add(Specs.spec("concealment_restriction", 410, Family.PENYEMBUNYI).grade(Grade.KUNING)
				.radius(12, 1, 20).pulseTicks(40)
				.pattern(p -> new Grid(3, 3)
						.four(3, 0, 0, 'm', 'f')
						.corners(3, 0, 'D')
						.apply(p)
						.key('m', Rules.block(Blocks.MOSS_BLOCK))
						.key('f', azalea)
						.key('D', k4Stone)
						.heart(-1, Rules.block(Blocks.TINTED_GLASS)))
				.effect(CONCEAL).param("grade", 1.0D).param("outer", 16.0D)
				.flag(Flag.PERSONAL_OK)
				.build());

		// ================================================================ M2 Formasi Penyembunyi dan Pengintai
		r.add(Specs.spec("concealment_detection", 401, Family.PENYEMBUNYI).grade(Grade.MISTERIUS)
				.scope(Scope.TERRITORY).radius(16).pulseTicks(40)
				.pattern(p -> new Grid(5, 5)
						.four(5, 0, 0, 'd', 'g', 'c')
						.corners(4, 0, 'm', 'f')
						.at(1, 0, 0, 'o')
						.apply(p)
						.key('d', k4Stone)
						.key('g', Rules.block(Blocks.TINTED_GLASS))
						.key('c', copperLantern)
						.key('m', Rules.block(Blocks.MOSS_BLOCK))
						.key('f', azalea)
						.key('o', Rules.block(Blocks.SOUL_LANTERN))
						.heart(-1, Rules.block(Blocks.TINTED_GLASS)))
				.effect(CONCEAL_DETECT).param("grade", 2.0D).param("approach", 16.0D)
				.build());

		// ================================================================ M1 Formasi Lima Unsur Terbalik (FIXED)
		r.add(Specs.spec("five_elements_reversal", 420, Family.ILUSI).grade(Grade.MISTERIUS)
				.radius(16, 1, 22).pulseTicks(20)
				.pattern(p -> new Grid(5, 5)
						.column(5, 0, 0, 'B', 'B', 'V')
						.column(0, 5, 0, 'R', 'R', 'V')
						.column(-5, 0, 0, 'I', 'I', 'V')
						.column(0, -5, 0, 'K', 'K', 'V')
						.corners(3, 0, 't')
						.apply(p)
						.key('B', Rules.block(Blocks.BAMBOO_BLOCK))
						.key('R', fireBody)
						.key('I', Rules.block(Blocks.IRON_BLOCK))
						.key('K', Rules.block(Blocks.POLISHED_BLACKSTONE))
						.key('V', Rules.light())
						.key('t', Rules.block(Blocks.CHISELED_TUFF_BRICKS))
						.heart(-1, Rules.block(Blocks.RAW_GOLD_BLOCK)))
				.effect(MAZE).param("ring", 2.0D).param("alt", 0.0D).param("expel", 400.0D).param("slow", 0.0D)
				.param("shift", 1200.0D).param("steer", 40.0D).param("eliteSteer", 80.0D).param("river", 0.0D)
				.flag(Flag.FIXED_ORIENTATION)
				.build());

		// ================================================================ L2 Formasi Sungai Kuning Sembilan Lekuk
		r.add(Specs.spec("nine_bend_yellow_river", 421, Family.ILUSI).grade(Grade.LANGIT)
				.radius(20, 1, 22).pulseTicks(20)
				.pattern(p -> new Grid(9, 9)
						.four(9, 0, 0, 'w', 'w', 'F')
						.corners(6, 0, 'w', 'w', 'F')
						.at(1, 0, 0, 'U')
						.apply(p)
						.key('w', Rules.block(Blocks.CALCITE))
						.key('F', Rules.banner())
						.key('U', Rules.labelled(Rules.anyOf(Blocks.CAULDRON, Blocks.WATER_CAULDRON, Blocks.LAVA_CAULDRON,
								Blocks.POWDER_SNOW_CAULDRON), "kultivator.formasib.rule.cauldron"))
						.heart(-1, Rules.block(Blocks.GOLD_BLOCK)))
				.effect(MAZE).param("ring", 3.0D).param("alt", 8.0D).param("expel", 600.0D).param("slow", 1.0D)
				.param("shift", 0.0D).param("steer", 40.0D).param("eliteSteer", 40.0D).param("river", 1.0D)
				.build());

		// ================================================================ B4 Formasi Pasir Merah / Red Sand
		r.add(Specs.spec("red_sand", 430, Family.KENDALI).grade(Grade.BUMI)
				.radius(14, 1, 18).pulseTicks(20)
				.pattern(p -> new Grid(6, 6)
						.four(2, 0, 0, 'c')
						.four(4, 0, 0, 'c')
						.corners(6, 0, 's', 's', 'k')
						.apply(p)
						.key('c', Rules.block(Blocks.CUT_RED_SANDSTONE))
						.key('s', Rules.block(Blocks.RED_SANDSTONE))
						.key('k', Rules.block(Blocks.CHISELED_RED_SANDSTONE))
						.heart(-1, Rules.block(Blocks.CHISELED_RED_SANDSTONE)))
				.effect(RED_SAND).param("root", 80.0D).param("rootElite", 40.0D).param("rootBoss", 20.0D).param("sand", 1.5D)
				.param("blind", 40.0D)
				.build());

		// ================================================================ B1 Formasi Pedang Geng Agung Sekte
		r.add(Specs.spec("great_geng_sword", 440, Family.PEMBUNUH).grade(Grade.BUMI)
				.radius(16, 1, 20).pulseTicks(20)
				.pattern(p -> new Grid(7, 7)
						.four(7, 0, 0, 'b', 'b', 'b', 'r')
						.corners(5, 0, 'b', 'b', 'b', 'r')
						.at(1, 0, 0, 'r')
						.apply(p)
						.key('b', Rules.block(Blocks.BAMBOO_BLOCK))
						.key('r', Rules.block(Blocks.END_ROD))
						.heart(-1, Rules.block(Blocks.IRON_BLOCK)))
				.effect(KILLING).param("kind", KILL_SWORD).param("dmg", 4.0D).param("targets", 6.0D).param("sweep", 8.0D)
				.param("sweepEvery", 100.0D).param("sweepTargets", 8.0D).param("sweepRadius", 4.0D)
				.build());

		// ================================================================ B2 Formasi Mutlak Langit / Heaven Absolute (FIXED)
		r.add(Specs.spec("heaven_absolute", 441, Family.PEMBUNUH).grade(Grade.BUMI)
				.radius(18, 1, 22).pulseTicks(30)
				.pattern(p -> new Grid(-6, 6, -6, 3)
						.column(0, -6, 0, 'u', 'u', 'u', 'u', 'F')
						.column(-5, 3, 0, 'u', 'u', 'u', 'u', 'F')
						.column(5, 3, 0, 'u', 'u', 'u', 'u', 'F')
						.at(0, 0, 3, 'c')
						.at(0, -3, -2, 'c')
						.at(0, 3, -2, 'c')
						.apply(p)
						.key('u', cutCopper)
						.key('F', Rules.banner())
						.key('c', chiseledCopper)
						.heart(-1, chiseledCopper))
				.effect(KILLING).param("kind", KILL_HEAVEN).param("dmg", 6.0D).param("targets", 3.0D).param("slowTicks", 20.0D)
				.flag(Flag.FIXED_ORIENTATION)
				.build());

		// ================================================================ B3 Formasi Bumi Membara / Earth Fierce
		r.add(Specs.spec("earth_fierce", 442, Family.PEMBUNUH).grade(Grade.BUMI)
				.radius(14, 1, 18).pulseTicks(20)
				.pattern(p -> new Grid(6, 6)
						.four(6, 0, 0, 'v')
						.corners(4, 0, 'v')
						.corners(6, 0, 'u', 'u', 'u', 'r')
						.at(1, 0, 0, 'F')
						.apply(p)
						.key('v', Rules.block(Blocks.MAGMA_BLOCK))
						.key('u', cutCopper)
						.key('r', Rules.block(Blocks.END_ROD))
						.key('F', Rules.banner(DyeColor.RED))
						.heart(-1, Rules.block(Blocks.RED_NETHER_BRICKS)))
				.effect(KILLING).param("kind", KILL_EARTH).param("dmg", 2.0D).param("targets", 10.0D).param("fire", 3.0D)
				.param("thunder", 5.0D).param("thunderEvery", 60.0D)
				.build());

		// ================================================================ L3 Formasi Api Berkobar / Blazing Flame
		r.add(Specs.spec("blazing_flame", 443, Family.PEMBUNUH).grade(Grade.LANGIT)
				.radius(20, 1, 22).pulseTicks(20)
				.pattern(p -> new Grid(-7, 7, -8, 5)
						.column(0, -8, 0, 'R', 'R', 'R', 'R', 'F')
						.column(-7, 4, 0, 'R', 'R', 'R', 'R', 'F')
						.column(7, 4, 0, 'R', 'R', 'R', 'R', 'F')
						.column(-4, -3, 0, 'P', 's')
						.column(4, -3, 0, 'W', 'W', 'L')
						.at(0, 0, 5, 'm')
						.apply(p)
						.key('R', fireBody)
						.key('F', Rules.banner(DyeColor.RED))
						.key('P', Rules.block(Blocks.POLISHED_BLACKSTONE))
						.key('s', Rules.state(Blocks.SOUL_CAMPFIRE, CampfireBlock.LIT, true))
						.key('W', Rules.labelled(Rules.anyOf(Blocks.POLISHED_CINNABAR_WALL, Blocks.CINNABAR_BRICK_WALL),
								"kultivator.formasib.rule.cinnabar_wall"))
						.key('L', Rules.block(Blocks.SHROOMLIGHT))
						.key('m', Rules.block(Blocks.MAGMA_BLOCK))
						.heart(-1, fireBody))
				.effect(KILLING).param("kind", KILL_BLAZE).param("targets", 14.0D).param("air", 2.5D).param("samadhi", 6.0D)
				.param("immune", 2.0D).param("stone", 4.0D)
				.build());

		// ================================================================ L4 Formasi Air Merah / Red Water
		r.add(Specs.spec("red_water", 444, Family.PEMBUNUH).grade(Grade.LANGIT)
				.radius(18, 1, 22).pulseTicks(20)
				.pattern(p -> new Grid(7, 7)
						.ring(1, 0, 'q')
						.at(1, 0, -1, 'u')
						.at(1, 1, 1, 'u')
						.at(1, -1, 1, 'u')
						.four(4, 0, 0, 'w')
						.corners(3, 0, 'w')
						.corners(7, 0, 'd', 'd', 'R', 'L')
						.apply(p)
						.key('q', Rules.block(Blocks.SMOOTH_QUARTZ))
						.key('u', Rules.block(Blocks.WATER_CAULDRON))
						.key('w', Rules.waterSource())
						.key('d', Rules.labelled(Rules.anyOf(Blocks.PRISMARINE_BRICKS, Blocks.DEEPSLATE_TILES), "kultivator.formasib.rule.deep_stone"))
						.key('R', fireBody)
						.key('L', Rules.block(Blocks.SEA_LANTERN))
						.heart(-1, Rules.block(Blocks.SEA_LANTERN)))
				.effect(KILLING).param("kind", KILL_WATER).param("dmg", 3.0D).param("targets", 12.0D).param("share", 0.01D)
				.param("slowTicks", 40.0D)
				.build());

		// ================================================================ M3 Formasi Perpindahan Sekte / Sect Transfer
		r.add(Specs.spec("sect_transfer", 450, Family.PERPINDAHAN).grade(Grade.MISTERIUS)
				.scope(Scope.TERRITORY).radius(2).pulseTicks(20)
				.pattern(p -> new Grid(2, 2)
						.ring(1, 0, 'e')
						.corners(2, 0, 'p', 'p', 'r')
						.apply(p)
						.key('e', endStone)
						.key('p', pillar)
						.key('r', endLight)
						.heart(-1, Rules.block(Blocks.CRYING_OBSIDIAN)))
				.effect(TRANSPORT).param("tier", 0.0D)
				.build());

		// ================================================================ L5 Formasi Perpindahan Kuno / Ancient Transportation
		r.add(Specs.spec("ancient_transfer", 451, Family.PERPINDAHAN).grade(Grade.LANGIT)
				.scope(Scope.TERRITORY).radius(4).pulseTicks(20)
				.pattern(p -> new Grid(4, 4)
						.ring(1, 0, 'e')
						.corners(2, 0, 'p', 'p', 'r')
						.four(4, 0, 0, 'C', 'Q', 'Q', 'E')
						.apply(p)
						.key('e', endStone)
						.key('p', pillar)
						.key('r', endLight)
						.key('C', Rules.block(Blocks.CHISELED_QUARTZ_BLOCK))
						.key('Q', Rules.block(Blocks.QUARTZ_PILLAR))
						.key('E', Rules.block(Blocks.END_ROD))
						.heart(-1, Rules.block(Blocks.CRYING_OBSIDIAN)))
				.effect(TRANSPORT).param("tier", 1.0D)
				.build());

		// ================================================================ A1 Formasi Sepuluh Ribu Abadi (FIXED)
		r.add(Specs.spec("ten_thousand_immortals", 460, Family.GABUNGAN).grade(Grade.ABADI)
				.scope(Scope.TERRITORY).radius(10).pulseTicks(40)
				.pattern(p -> new Grid(10, 10)
						.ring(1, 0, 'q')
						// Trigram pillars, King Wen order in true compass orientation; lines bottom to top, Y = yang, N = yin.
						.column(-7, -7, 0, 'C', 'Y', 'Y', 'Y', 'E')   // ☰ Qian NW
						.column(0, -10, 0, 'C', 'N', 'Y', 'N', 'E')   // ☵ Kan N
						.column(7, -7, 0, 'C', 'N', 'N', 'Y', 'E')    // ☶ Gen NE
						.column(10, 0, 0, 'C', 'Y', 'N', 'N', 'E')    // ☳ Zhen E
						.column(7, 7, 0, 'C', 'N', 'Y', 'Y', 'E')     // ☴ Xun SE
						.column(0, 10, 0, 'C', 'Y', 'N', 'Y', 'E')    // ☲ Li S
						.column(-7, 7, 0, 'C', 'N', 'N', 'N', 'E')    // ☷ Kun SW
						.column(-10, 0, 0, 'C', 'Y', 'Y', 'N', 'E')   // ☱ Dui W
						.apply(p)
						.key('q', Rules.block(Blocks.SMOOTH_QUARTZ))
						.key('C', Rules.block(Blocks.CHISELED_QUARTZ_BLOCK))
						.key('Y', Rules.labelled(Rules.block(Blocks.POLISHED_BLACKSTONE), "kultivator.formasib.rule.yang"))
						.key('N', Rules.labelled(Rules.block(Blocks.SMOOTH_QUARTZ), "kultivator.formasib.rule.yin"))
						.key('E', Rules.block(Blocks.END_ROD))
						.heart(1, Rules.exact(Blocks.BEACON)))
				.effect(GRAND_A1)
				.flag(Flag.FIXED_ORIENTATION)
				.build());

		// ================================================================ A2 Sepuluh Formasi Mutlak / Ten Absolute Formations
		r.add(Specs.spec("ten_absolute", 461, Family.GABUNGAN).grade(Grade.ABADI)
				.scope(Scope.TERRITORY).radius(10).pulseTicks(40)
				.pattern(p -> {
					Grid grid = new Grid(-3, 3, 0, 10);
					for (int dz = 2; dz <= 10; dz += 2) {
						grid.column(-3, dz, 0, 'f', 'f', 'F');
						grid.column(3, dz, 0, 'f', 'f', 'F');
					}
					grid.apply(p)
							.key('f', Rules.block(Blocks.MANGROVE_FENCE))
							.key('F', Rules.banner())
							.heart(-1, Rules.exact(Blocks.NETHERITE_BLOCK));
				})
				.effect(GRAND_A2)
				.build());
	}

	/**
	 * A layered character grid for {@code Pattern.Builder}: every layer has the same size, so the builder's centring
	 * keeps each column on its dx. Offsets are relative to the eye (dx east, dz south); {@link #apply} puts the {@code @}.
	 */
	static final class Grid {
		private final int x0;
		private final int x1;
		private final int z0;
		private final int z1;
		private final Map<Integer, char[][]> layers = new TreeMap<>();

		/** A square grid reaching {@code halfX} and {@code halfZ} around the eye. */
		Grid(int halfX, int halfZ) {
			this(-halfX, halfX, -halfZ, halfZ);
		}

		Grid(int x0, int x1, int z0, int z1) {
			this.x0 = x0;
			this.x1 = x1;
			this.z0 = z0;
			this.z1 = z1;
		}

		private char[][] layer(int dy) {
			return layers.computeIfAbsent(dy, key -> {
				char[][] rows = new char[z1 - z0 + 1][x1 - x0 + 1];
				for (char[] row : rows) {
					Arrays.fill(row, '.');
				}
				return rows;
			});
		}

		/** One slot. */
		Grid at(int dy, int dx, int dz, char key) {
			layer(dy)[dz - z0][dx - x0] = key;
			return this;
		}

		/** A column at (dx, dz) from {@code fromDy} upward, one key per block. */
		Grid column(int dx, int dz, int fromDy, char... keys) {
			for (int i = 0; i < keys.length; i++) {
				at(fromDy + i, dx, dz, keys[i]);
			}
			return this;
		}

		/** The same column at (d, o), (−o, d), (−d, −o) and (o, −d): the four cardinal turns of (d, o). */
		Grid four(int d, int o, int fromDy, char... keys) {
			column(d, o, fromDy, keys);
			column(-o, d, fromDy, keys);
			column(-d, -o, fromDy, keys);
			column(o, -d, fromDy, keys);
			return this;
		}

		/** The same column at the four diagonal corners (±d, ±d). */
		Grid corners(int d, int fromDy, char... keys) {
			column(d, d, fromDy, keys);
			column(-d, d, fromDy, keys);
			column(d, -d, fromDy, keys);
			column(-d, -d, fromDy, keys);
			return this;
		}

		/** The eight cells around the eye at distance {@code d} (a 3 × 3 ring for d = 1) on layer {@code dy}. */
		Grid ring(int d, int dy, char key) {
			for (int dx = -d; dx <= d; dx++) {
				for (int dz = -d; dz <= d; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == d) {
						at(dy, dx, dz, key);
					}
				}
			}
			return this;
		}

		/** Writes every layer (and the eye on layer 0) into {@code p}; returns {@code p} for the legend. */
		Pattern.Builder apply(Pattern.Builder p) {
			layer(0)[-z0][-x0] = '@';
			for (Map.Entry<Integer, char[][]> entry : layers.entrySet()) {
				char[][] rows = entry.getValue();
				String[] lines = new String[rows.length];
				for (int i = 0; i < rows.length; i++) {
					lines[i] = new String(rows[i]);
				}
				p.layer(entry.getKey(), lines);
			}
			return p;
		}
	}

	private CatalogB() {
	}
}
