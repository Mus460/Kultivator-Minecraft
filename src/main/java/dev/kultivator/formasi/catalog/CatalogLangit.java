package dev.kultivator.formasi.catalog;

import dev.kultivator.formasi.effect.b.JaringAwanEffect;
import dev.kultivator.formasi.effect.b.TransportNetwork;
import dev.kultivator.formasi.spec.EffectSink;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.Grade;
import dev.kultivator.formasi.spec.Rules;
import dev.kultivator.formasi.spec.Scope;
import dev.kultivator.formasi.spec.SpecSink;
import dev.kultivator.formasi.spec.Specs;
import net.minecraft.world.level.block.Blocks;

/**
 * The two formations of Sekte Langit (DESIGN 37.5.1, 37.5.6 b; module 37M), netIds 470-471, registered from
 * {@code FormasiB.init} on both physical sides. Text keys {@code kultivator.formasi.spec.<id>.name|desc|hint|source} live
 * in {@code lang-src/sekte_langit_gerak}.
 * <ul>
 * <li>{@code awan_transfer} (470) Formasi Pindah Awan: a transport node like M3 ({@code CatalogB.TRANSPORT}, the same
 * channel, refusals, Daya and qi costs) whose pair crosses between the ground and a sky island:
 * {@code TransportNetwork.linked} ties it to the other {@code awan_transfer} node of the owner when one end stands in the
 * sky hall's territory and the other within {@link TransportNetwork#AWAN_RANGE} blocks (horizontal, any height) of the
 * stele. The sky node is built (inactive) by the sky template's T1 component {@code aw_t1_formasi_awan}; the ground node
 * is laid by hand by the player. At most {@code Limits.AWAN_NODES} per owner.</li>
 * <li>{@code jaring_awan} (471) Formasi Jaring Awan: a territory guard (PELINDUNG) whose eye stands on the island's
 * formation disc with the four rim poles of {@code aw_t5_jaring} standing ({@code jaring_0..3}); while it runs the base
 * Cloud Net also catches guests and members may take the Cloud Dive ({@link JaringAwanEffect}).</li>
 * </ul>
 * Patterns are offsets from the Mata Formasi (north = −z), drawn with {@link CatalogB.Grid}.
 */
public final class CatalogLangit {

	public static final String AWAN_TRANSFER = TransportNetwork.AWAN;
	public static final String JARING_AWAN = "jaring_awan";
	/** Effect handler id of the Cloud Net formation. */
	public static final String JARING = "jaring_awan";
	public static final int AWAN_COLOR = 0xCFE8FF;
	public static final int JARING_COLOR = 0xE6F2FF;

	private static boolean registered;

	/** Registers both specs and the Cloud Net handler (the transfer node reuses {@code CatalogB.TRANSPORT}). */
	public static void register(SpecSink specs, EffectSink effects) {
		if (registered) {
			return;
		}
		registered = true;

		// ================================================================ Formasi Pindah Awan / Cloud Transfer
		// A 5 × 5 node: a 3 × 3 ring of smooth quartz around the eye and an amethyst block on each diagonal corner.
		specs.add(Specs.spec(AWAN_TRANSFER, 470, Family.PERPINDAHAN).grade(Grade.MISTERIUS)
				.scope(Scope.CORE).radius(2).pulseTicks(20)
				.pattern(p -> new CatalogB.Grid(2, 2)
						.ring(1, 0, 'q')
						.corners(2, 0, 'a')
						.apply(p)
						.key('q', Rules.block(Blocks.SMOOTH_QUARTZ))
						.key('a', Rules.block(Blocks.AMETHYST_BLOCK)))
				.effect(CatalogB.TRANSPORT).param("tier", 0.0D)
				.color(AWAN_COLOR)
				.build());

		// ================================================================ Formasi Jaring Awan / Cloud Net
		// A 7 × 7 net: smooth quartz around the eye, four quartz pillars ×2 crowned with amethyst at 3 blocks (the rim
		// poles in small), an end rod on each diagonal at 2 blocks, an amethyst heart under the eye.
		specs.add(Specs.spec(JARING_AWAN, 471, Family.PELINDUNG).grade(Grade.MISTERIUS)
				.scope(Scope.TERRITORY).radius(8).pulseTicks(40)
				.pattern(p -> new CatalogB.Grid(3, 3)
						.ring(1, 0, 'q')
						.four(3, 0, 0, 'Q', 'Q', 'A')
						.corners(2, 0, 'e')
						.apply(p)
						.key('q', Rules.block(Blocks.SMOOTH_QUARTZ))
						.key('Q', Rules.block(Blocks.QUARTZ_PILLAR))
						.key('A', Rules.block(Blocks.AMETHYST_BLOCK))
						.key('e', Rules.block(Blocks.END_ROD))
						.heart(-1, Rules.block(Blocks.AMETHYST_BLOCK)))
				.effect(JARING).param("guests", 1.0D)
				.color(JARING_COLOR)
				.build());

		effects.effect(JARING, new JaringAwanEffect());
	}

	private CatalogLangit() {
	}
}
