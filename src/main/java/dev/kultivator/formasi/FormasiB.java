package dev.kultivator.formasi;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.formasi.catalog.CatalogB;
import dev.kultivator.formasi.effect.b.AlarmEffect;
import dev.kultivator.formasi.effect.b.BHooks;
import dev.kultivator.formasi.effect.b.ConcealEffect;
import dev.kultivator.formasi.effect.b.GrandArrayEffect;
import dev.kultivator.formasi.effect.b.KillingEffect;
import dev.kultivator.formasi.effect.b.MazeEffect;
import dev.kultivator.formasi.effect.b.RedSandEffect;
import dev.kultivator.formasi.effect.b.TargetGateImpl;
import dev.kultivator.formasi.effect.b.TransportNetwork;
import dev.kultivator.formasi.spec.EffectSink;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.SpecSink;
import dev.kultivator.util.TargetGates;
import net.minecraft.commands.CommandSourceStack;

import java.util.UUID;

/**
 * The entry of P7c-katalog-ui on the common side (DESIGN 24.14.2): catalogue B (15 specs), its handlers, the
 * concealment and maze target gates, the {@code conceal} sync list, the {@code b} part of the hall info (nodes and grand
 * arrays), the {@code travel} and {@code node} actions, the node map on transport eyes and the A1/A2 power bonuses.
 * Called by {@code Formasi.init} on both physical sides.
 */
public final class FormasiB {

	private static boolean initialised;

	/** Registers catalogue B and every B handler, gate and part (once, from {@code Formasi.init}). */
	public static void init(SpecSink specs, EffectSink effects) {
		if (initialised) {
			return;
		}
		initialised = true;
		CatalogB.register(specs);
		effects.effect(CatalogB.ALARM, new AlarmEffect());
		effects.effect(CatalogB.CONCEAL, new ConcealEffect(false));
		effects.effect(CatalogB.CONCEAL_DETECT, new ConcealEffect(true));
		effects.effect(CatalogB.MAZE, new MazeEffect());
		effects.effect(CatalogB.RED_SAND, new RedSandEffect());
		effects.effect(CatalogB.KILLING, new KillingEffect());
		effects.effect(CatalogB.TRANSPORT, new TransportNetwork.Effect());
		effects.effect(CatalogB.GRAND_A1, new GrandArrayEffect(true));
		effects.effect(CatalogB.GRAND_A2, new GrandArrayEffect(false));
		TargetGates.addGate(new TargetGateImpl());
		Formasi.addPowerBonus(GrandArrayEffect::powerBonus);
		Formasi.addSyncPart((viewer, out) -> out.putIntArray("conceal", ConcealEffect.concealedFrom(viewer)));
		Formasi.addInfoPart(BHooks::writeInfo);
		Formasi.addActionHandler("travel", BHooks::onTravel);
		Formasi.addActionHandler("node", BHooks::onNode);
		Formasi.addUseHandler(Family.PERPINDAHAN, BHooks::openNodeMap);
		BHooks.init();
	}

	/** {@code formasi simpul ...} and {@code formasi pindah ...} on module A's {@code formasi} node. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> formasiNode) {
		BHooks.addCommands(formasiNode);
	}

	/** Module B's {@code uji formasi ...} commands on module A's node. */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> ujiFormasiNode) {
		BHooks.addTestCommands(ujiFormasiNode);
	}

	/** True when {@code owner}'s A1 Ten Thousand Immortals Formation is complete (L1 Lapis Inti, stack cap 2.8). */
	public static boolean grandComplete(UUID owner) {
		return owner != null && GrandArrayEffect.complete(owner);
	}

	private FormasiB() {
	}
}
