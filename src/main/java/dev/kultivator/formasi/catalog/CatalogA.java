package dev.kultivator.formasi.catalog;

import dev.kultivator.formasi.effect.a.FlagEffect;
import dev.kultivator.formasi.effect.a.GatherEffect;
import dev.kultivator.formasi.effect.a.HealEffect;
import dev.kultivator.formasi.effect.a.RefineEffect;
import dev.kultivator.formasi.effect.a.ShieldEffect;
import dev.kultivator.formasi.effect.a.UnitingCircleEffect;
import dev.kultivator.formasi.effect.a.WardEffect;
import dev.kultivator.formasi.spec.EffectSink;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.Grade;
import dev.kultivator.formasi.spec.Rule;
import dev.kultivator.formasi.spec.Rules;
import dev.kultivator.formasi.spec.Scope;
import dev.kultivator.formasi.spec.SpecSink;
import dev.kultivator.formasi.spec.Specs;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * Catalogue A (DESIGN 24.4.1-24.4.6): protection (L1), gathering (K2, M5, B5), healing (M4), refining (K3, B7),
 * tribulation warding (B6) and support (L6), with their handlers. netIds 100-399. Patterns follow the cards: north is the
 * top row, the eye is {@code '@'}, the heart sits under the eye.
 */
public final class CatalogA {

	public static void register(SpecSink r, EffectSink e) {
		e.effect("shield", new ShieldEffect());
		e.effect("gather", new GatherEffect());
		e.effect("unite", new UnitingCircleEffect());
		e.effect("heal", new HealEffect());
		e.effect("refine", new RefineEffect());
		e.effect("ward", new WardEffect());
		e.effect("flag", new FlagEffect());

		Rule tip = Rules.labelled(Rules.anyOf(Blocks.AMETHYST_CLUSTER, Blocks.LARGE_AMETHYST_BUD, Blocks.END_ROD, Blocks.SOUL_LANTERN, Blocks.LANTERN)
				.or(Rules.tag(BlockTags.CANDLES)), "kultivator.formasi.rule.tip");
		Rule lamp = Rules.labelled(Rules.anyOf(Blocks.END_ROD, Blocks.SOUL_LANTERN, Blocks.LANTERN).or(Rules.tag(BlockTags.CANDLES)),
				"kultivator.formasi.rule.lamp");
		Rule clusterOrCandle = Rules.labelled(Rules.block(Blocks.AMETHYST_CLUSTER).or(Rules.tag(BlockTags.CANDLES)),
				"kultivator.formasi.rule.cluster_candle");

		// ---- L1 Perisai Larangan Lima Lapis (FIXED, five peaks of the Langit Five Elements column)
		r.add(Specs.spec("five_layer_shield", 100, Family.PELINDUNG).grade(Grade.LANGIT)
				.pattern(p -> {
					Object[] bodies = {'W', 0, -10, 'K', 10, -3, 'F', 6, 8, 'E', -6, 8, 'M', -10, -3};
					Object[] caps = {'w', 0, -10, 'k', 10, -3, 'f', 6, 8, 'e', -6, 8, 'm', -10, -3};
					p.layer(0, g(10, 10, true, bodies));
					p.layer(1, g(10, 10, false, bodies));
					p.layer(2, g(10, 10, false, bodies));
					p.layer(3, g(10, 10, false, bodies));
					p.layer(4, g(10, 10, false, caps));
					p.key('W', Rules.block(Blocks.POLISHED_BLACKSTONE))
							.key('K', Rules.block(Blocks.BAMBOO_BLOCK))
							.key('F', Rules.anyOf(Blocks.POLISHED_CINNABAR, Blocks.CINNABAR_BRICKS, Blocks.RED_NETHER_BRICKS))
							.key('E', Rules.block(Blocks.SMOOTH_SANDSTONE))
							.key('M', Rules.block(Blocks.IRON_BLOCK))
							.key('w', Rules.block(Blocks.SEA_LANTERN))
							.key('k', Rules.block(Blocks.VERDANT_FROGLIGHT))
							.key('f', Rules.block(Blocks.SHROOMLIGHT))
							.key('e', Rules.block(Blocks.OCHRE_FROGLIGHT))
							.key('m', Rules.block(Blocks.PEARLESCENT_FROGLIGHT))
							.heart(-1, Rules.exact(Blocks.DIAMOND_BLOCK));
				})
				.flag(Flag.FIXED_ORIENTATION).radius(48).pulseTicks(40)
				.param("territory_cap", 1).param("dy", 24)
				.effect("shield").build());

		// ---- K2 Formasi Pengumpul Roh Kecil
		r.add(Specs.spec("small_spirit_gathering", 110, Family.PENGUMPUL).grade(Grade.KUNING)
				.pattern(p -> p
						.layer(0, g(4, 4, true, 'A', -4, -4, 'A', 4, -4, 'A', -4, 4, 'A', 4, 4, 'Q', 0, -4, 'Q', 0, 4, 'Q', -4, 0, 'Q', 4, 0))
						.layer(1, g(4, 4, false, 'c', -4, -4, 'c', 4, -4, 'c', -4, 4, 'c', 4, 4, 'k', 0, 0))
						.key('A', Rules.block(Blocks.AMETHYST_BLOCK))
						.key('Q', Rules.block(Blocks.QUARTZ_PILLAR))
						.key('c', tip)
						.key('k', Rules.block(Blocks.AMETHYST_CLUSTER))
						.heart(-1, Rules.block(Blocks.AMETHYST_BLOCK)))
				.flag(Flag.PERSONAL_OK).radius(10, 1, 14).pulseTicks(40)
				.param("keeper_radius", 4).param("dy", 4).param("gather", 0.10D).param("drain", 0.9D)
				.effect("gather").build());

		// ---- M5 Formasi Lingkar Penyatu
		r.add(Specs.spec("uniting_circle", 111, Family.PENGUMPUL).grade(Grade.MISTERIUS)
				.pattern(p -> p
						.layer(0, g(5, 5, true, 's', 3, 0, 's', -3, 0, 's', 0, 3, 's', 0, -3, 's', 2, 2, 's', -2, 2, 's', 2, -2, 's', -2, -2,
								'A', -5, -5, 'A', 5, -5, 'A', -5, 5, 'A', 5, 5))
						.layer(1, g(5, 5, false, 'A', -5, -5, 'A', 5, -5, 'A', -5, 5, 'A', 5, 5, 'k', 0, 0))
						.layer(2, g(5, 5, false, 'r', -5, -5, 'r', 5, -5, 'r', -5, 5, 'r', 5, 5))
						.key('s', Rules.labelled(Rules.tag(BlockTags.WOOL_CARPETS), "kultivator.formasi.rule.cushion"))
						.key('A', Rules.block(Blocks.AMETHYST_BLOCK))
						.key('k', clusterOrCandle)
						.key('r', lamp)
						.heart(-1, Rules.block(Blocks.CHISELED_QUARTZ_BLOCK)))
				.radius(6).pulseTicks(20).param("dy", 3)
				.effect("unite").build());

		// ---- B5 Formasi Pengumpul Roh Agung Sekte (territory)
		Object[] spires = {'a', 0, -7, 'a', 0, 7, 'a', -7, 0, 'a', 7, 0, 'a', -5, -5, 'a', 5, -5, 'a', -5, 5, 'a', 5, 5};
		r.add(Specs.spec("great_spirit_gathering", 112, Family.PENGUMPUL).grade(Grade.BUMI)
				.pattern(p -> p
						.layer(0, g(7, 7, true, concat(spires, new Object[]{'j', -3, -3, 'j', 3, -3, 'j', -3, 3, 'j', 3, 3})))
						.layer(1, g(7, 7, false, concat(spires, new Object[]{'k', 0, 0})))
						.layer(2, g(7, 7, false, spires))
						.layer(3, g(7, 7, false, swap(spires, 'r')))
						.key('a', Rules.block(Blocks.AMETHYST_BLOCK))
						.key('r', Rules.block(Blocks.END_ROD))
						.key('j', Rules.labelled(Rules.anyOf(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.EMERALD_BLOCK), "kultivator.formasi.rule.jade"))
						.key('k', Rules.block(Blocks.AMETHYST_CLUSTER))
						.heart(-1, Rules.block(Blocks.GOLD_BLOCK)))
				.scope(Scope.TERRITORY).radius(32).pulseTicks(40)
				.param("gather", 0.20D).param("gather_high", 0.25D).param("drain", 0.8D).param("drain_keeper", 0.7D)
				.effect("gather").build());

		// ---- M4 Formasi Pemelihara Roh
		r.add(Specs.spec("spirit_nurturing", 120, Family.PEMULIHAN).grade(Grade.MISTERIUS)
				.pattern(p -> p
						.layer(0, g(4, 4, true, 'm', -4, -4, 'm', 4, -4, 'm', -4, 4, 'm', 4, 4, 'w', 0, -2, 'w', 0, 2, 'w', -2, 0, 'w', 2, 0))
						.layer(1, g(4, 4, false, 'f', -4, -4, 'f', 4, -4, 'f', -4, 4, 'f', 4, 4))
						.layer(2, g(4, 4, false, 'v', -4, -4, 'v', 4, -4, 'v', -4, 4, 'v', 4, 4))
						.key('m', Rules.block(Blocks.MOSS_BLOCK))
						.key('w', Rules.waterSource())
						.key('f', Rules.anyOf(Blocks.FLOWERING_AZALEA_LEAVES, Blocks.AZALEA_LEAVES))
						.key('v', Rules.light())
						.heart(-1, Rules.labelled(Rules.anyOf(Blocks.PEARLESCENT_FROGLIGHT, Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT,
								Blocks.SEA_LANTERN, Blocks.GLOWSTONE, Blocks.SHROOMLIGHT), "kultivator.formasi.rule.white_egg")))
				.flag(Flag.PAUSE_IN_TRIBULATION, Flag.PERSONAL_OK).radius(12, 1, 18).pulseTicks(100)
				.effect("heal").build());

		// ---- K3 Formasi Api Bumi
		r.add(Specs.spec("earthfire", 130, Family.PENEMPAAN).grade(Grade.KUNING)
				.pattern(p -> p
						.layer(0, g(3, 3, true, 'P', -3, -3, 'P', 3, -3, 'P', -3, 3, 'P', 3, 3, 'b', 0, -1, 'b', 0, 1, 'b', -1, 0, 'b', 1, 0))
						.layer(1, g(3, 3, false, 's', -3, -3, 's', 3, -3, 's', -3, 3, 's', 3, 3))
						.key('P', Rules.block(Blocks.CHISELED_POLISHED_BLACKSTONE))
						.key('b', Rules.block(Blocks.POLISHED_BLACKSTONE_BRICKS))
						.key('s', Rules.block(Blocks.SOUL_LANTERN))
						.heart(-1, Rules.block(Blocks.MAGMA_BLOCK)))
				.radius(6, 0, 8).pulseTicks(40)
				.param("plus_l5", 1).param("plus_l9", 1).param("keeper_radius", 2).param("dy", 3)
				.param("chance", 0.05D).param("time", 0.8D).param("inscribe", 0.03D)
				.effect("refine").build());

		// ---- B7 Formasi Api Paru Bumi (the K3 upgrade on the same eye)
		Object[] chimneys = {'c', 0, -5, 'c', 0, 5, 'c', -5, 0, 'c', 5, 0};
		r.add(Specs.spec("earth_lung_fire", 131, Family.PENEMPAAN).grade(Grade.BUMI)
				.pattern(p -> p
						.layer(0, g(5, 5, true, concat(chimneys, new Object[]{'P', -3, -3, 'P', 3, -3, 'P', -3, 3, 'P', 3, 3,
								'm', -2, -2, 'm', 2, -2, 'm', -2, 2, 'm', 2, 2, 'b', 0, -1, 'b', 0, 1, 'b', -1, 0, 'b', 1, 0})))
						.layer(1, g(5, 5, false, concat(chimneys, new Object[]{'s', -3, -3, 's', 3, -3, 's', -3, 3, 's', 3, 3})))
						.layer(2, g(5, 5, false, swap(chimneys, 'f')))
						.key('c', Rules.block(Blocks.POLISHED_BLACKSTONE_BRICKS))
						.key('P', Rules.block(Blocks.CHISELED_POLISHED_BLACKSTONE))
						.key('b', Rules.block(Blocks.POLISHED_BLACKSTONE_BRICKS))
						.key('m', Rules.block(Blocks.MAGMA_BLOCK))
						.key('s', Rules.block(Blocks.SOUL_LANTERN))
						.key('f', Rules.labelled(Rules.state(Blocks.SOUL_CAMPFIRE, CampfireBlock.LIT, true), "kultivator.formasi.rule.lit_soul_campfire"))
						.heart(-1, Rules.block(Blocks.MAGMA_BLOCK)))
				.radius(8).pulseTicks(40).param("dy", 4)
				.param("chance", 0.10D).param("steps", 1).param("time", 0.7D).param("inscribe", 0.05D)
				.effect("refine").build());

		// ---- B6 Formasi Jaring Petir Emas
		Object[] masts = {'u', -6, -6, 'u', 6, -6, 'u', -6, 6, 'u', 6, 6};
		r.add(Specs.spec("golden_lightning_net", 140, Family.PENANGKAL).grade(Grade.BUMI)
				.pattern(p -> p
						.layer(0, g(6, 6, true, concat(masts, new Object[]{'o', 0, -6, 'o', 0, 6, 'o', -6, 0, 'o', 6, 0})))
						.layer(1, g(6, 6, false, masts))
						.layer(2, g(6, 6, false, masts))
						.layer(3, g(6, 6, false, masts))
						.layer(4, g(6, 6, false, swap(masts, 'r')))
						.key('u', Rules.copper(Blocks.CUT_COPPER))
						.key('o', Rules.block(Blocks.GOLD_BLOCK))
						.key('r', Rules.block(Blocks.END_ROD))
						.heart(-1, Rules.anyOf(Blocks.GILDED_BLACKSTONE, Blocks.GOLD_BLOCK)))
				.radius(12, 1, 16).pulseTicks(40).param("dy", 12).param("factor", 0.90D).param("strong", 0.85D)
				.effect("ward").build());

		// ---- L6 Formasi Panji Larangan
		r.add(Specs.spec("restriction_flag", 150, Family.PENGUAT).grade(Grade.LANGIT)
				.pattern(p -> p
						.layer(0, g(3, 3, true, 'b', 3, 0, 'b', -3, 0, 'b', 0, 3, 'b', 0, -3, 'b', 2, 2, 'b', -2, 2, 'b', 2, -2, 'b', -2, -2))
						.layer(1, g(3, 3, false, 'F', 0, 0))
						.key('b', Rules.block(Blocks.POLISHED_BLACKSTONE_BRICKS))
						.key('F', Rules.banner())
						.heart(-1, Rules.block(Blocks.CHISELED_POLISHED_BLACKSTONE)))
				.radius(8).pulseTicks(40)
				.effect("flag").build());
	}

	/**
	 * A layer grid of {@code (2·rx+1) × (2·rz+1)} cells: {@code '.'} everywhere, {@code '@'} in the middle when
	 * {@code eye}, and each {@code (char, dx, dz)} triple of {@code cells} at its offset (north = −z = top row).
	 */
	static String[] g(int rx, int rz, boolean eye, Object... cells) {
		char[][] grid = new char[2 * rz + 1][2 * rx + 1];
		for (char[] row : grid) {
			java.util.Arrays.fill(row, '.');
		}
		if (eye) {
			grid[rz][rx] = '@';
		}
		for (int i = 0; i + 2 < cells.length; i += 3) {
			char c = (Character) cells[i];
			int dx = (Integer) cells[i + 1];
			int dz = (Integer) cells[i + 2];
			grid[dz + rz][dx + rx] = c;
		}
		String[] rows = new String[grid.length];
		for (int i = 0; i < grid.length; i++) {
			rows[i] = new String(grid[i]);
		}
		return rows;
	}

	static Object[] concat(Object[] a, Object[] b) {
		Object[] out = new Object[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	/** A copy of the triples with every key replaced by {@code key}. */
	static Object[] swap(Object[] cells, char key) {
		Object[] out = cells.clone();
		for (int i = 0; i < out.length; i += 3) {
			out[i] = key;
		}
		return out;
	}

	private CatalogA() {
	}
}
