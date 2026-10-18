package dev.kultivator.formasi.catalog;

import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.Flag;
import dev.kultivator.formasi.spec.Grade;
import dev.kultivator.formasi.spec.Rules;
import dev.kultivator.formasi.spec.SpecSink;
import dev.kultivator.formasi.spec.Specs;
import net.minecraft.world.level.block.Blocks;

/**
 * Read-only descriptors of the formations that stay unchanged (DESIGN 24.2.2, 24.3): the three personal Qi formations,
 * the Formasi Pelindung and the Formasi Agung Sekte, for the Kitab and the hologram only. netIds 0-19. They have no
 * handler, are never detected on a Mata Formasi and never registered; {@code '@'} stands for the seat or the lodestone.
 */
public final class LegacyMirrors {

	public static void register(SpecSink r) {
		Object[] cardinal = {'A', 3, 0, 'A', -3, 0, 'A', 0, 3, 'A', 0, -3};
		Object[] corners = {'A', 3, 3, 'A', -3, 3, 'A', 3, -3, 'A', -3, -3};
		r.add(Specs.spec("qi_small", 0, Family.PENGUMPUL).grade(Grade.KUNING).flag(Flag.LEGACY_MIRROR, Flag.NO_6B)
				.pattern(p -> p.layer(0, CatalogA.g(3, 3, true, cardinal)).key('A', Rules.block(Blocks.AMETHYST_BLOCK)))
				.radius(1).build());
		r.add(Specs.spec("qi_great", 1, Family.PENGUMPUL).grade(Grade.MISTERIUS).flag(Flag.LEGACY_MIRROR, Flag.NO_6B)
				.pattern(p -> p.layer(0, CatalogA.g(3, 3, true, CatalogA.concat(cardinal, corners))).key('A', Rules.block(Blocks.AMETHYST_BLOCK)))
				.radius(1).build());
		r.add(Specs.spec("qi_heavenly", 2, Family.PENGUMPUL).grade(Grade.BUMI).flag(Flag.LEGACY_MIRROR, Flag.NO_6B)
				.pattern(p -> p.layer(0, CatalogA.g(3, 3, true, CatalogA.concat(cardinal, corners)))
						.layer(1, CatalogA.g(3, 3, false, CatalogA.swap(cardinal, 'r')))
						.key('A', Rules.block(Blocks.AMETHYST_BLOCK))
						.key('r', Rules.block(Blocks.END_ROD))
						.heart(-1, Rules.anyOf(Blocks.BEACON, Blocks.LODESTONE)))
				.radius(1).build());
		Object[] pillars = {'O', 4, 0, 'O', -4, 0, 'O', 0, 4, 'O', 0, -4};
		r.add(Specs.spec("protective", 3, Family.PELINDUNG).grade(Grade.KUNING).flag(Flag.LEGACY_MIRROR, Flag.NO_6B)
				.pattern(p -> p.layer(0, CatalogA.g(4, 4, true, pillars)).key('O', Rules.block(Blocks.CRYING_OBSIDIAN)))
				.radius(16).build());
		r.add(Specs.spec("formasi_agung", 4, Family.PELINDUNG).grade(Grade.MISTERIUS).flag(Flag.LEGACY_MIRROR, Flag.NO_6B)
				.pattern(p -> p.layer(0, CatalogA.g(4, 4, true, pillars)).key('O', Rules.block(Blocks.CRYING_OBSIDIAN)))
				.radius(16, 2, 32).build());
	}

	private LegacyMirrors() {
	}
}
