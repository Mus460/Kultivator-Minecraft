package dev.kultivator.cultivation;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.data.RootGrade;
import dev.kultivator.util.Msg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Spiritual roots (DESIGN section 6). Phase 0 has the roll and an instant test; P2-roots replaces
 * {@link #startCeremony} with the full 8 s ceremony and adds the Marrow Cleansing Pill.
 */
public final class SpiritRoots {

	public static void init() {
	}

	public static void tick(ServerPlayer player, CultivationData data) {
	}

	/** Begins the root test (Mortal, not tested yet). */
	public static void startCeremony(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (data.rootTested()) {
			Msg.chat(player, Component.translatable("kultivator.core.root_already", describe(data.rootElements(), data.rootGrade())));
			return;
		}
		Roll roll = roll(player.getRandom());
		data.setRoot(roll.elements(), roll.grade());
		Cultivators.syncNow(player);
		Msg.chat(player, Component.translatable("kultivator.core.root_result", describe(roll.elements(), roll.grade())));
	}

	/** A rolled root: element bitmask and grade. */
	public record Roll(int elements, RootGrade grade) {
	}

	/** The root roll from DESIGN section 6. Never called twice for the same player (the result is saved). */
	public static Roll roll(RandomSource random) {
		int r = random.nextInt(100);
		int count = r < 10 ? 5 : r < 35 ? 4 : r < 65 ? 3 : r < 90 ? 2 : 1;
		List<Element> classic = new ArrayList<>(List.of(Element.METAL, Element.WOOD, Element.WATER, Element.FIRE, Element.EARTH));
		List<Element> mutant = new ArrayList<>(List.of(Element.LIGHTNING, Element.ICE, Element.WIND, Element.SPACE));
		int mask = 0;
		boolean mutated = random.nextInt(100) < 6;
		for (int i = 0; i < count; i++) {
			Element element;
			if (mutated && i == 0) {
				element = mutant.remove(random.nextInt(mutant.size()));
			} else {
				element = classic.remove(random.nextInt(classic.size()));
			}
			mask |= element.bit();
		}
		int g = random.nextInt(100);
		int grade = g < 15 ? 0 : g < 45 ? 1 : g < 75 ? 2 : g < 93 ? 3 : 4;
		// Fewer elements are purer: shift up by (3 - count) * 0.5 steps, the half step decided by chance.
		double shift = (3 - count) * 0.5D;
		int whole = (int) Math.floor(shift);
		double fraction = shift - whole;
		grade += whole + (random.nextDouble() < fraction ? 1 : 0);
		return new Roll(mask, RootGrade.byOrdinal(Math.max(0, Math.min(4, grade))));
	}

	/** "Fire + Wood root, High grade". */
	public static MutableComponent describe(int elements, RootGrade grade) {
		MutableComponent names = Component.empty();
		List<Element> list = Element.fromMask(elements);
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				names.append(Component.literal(" + "));
			}
			names.append(list.get(i).displayName().copy().withColor(list.get(i).color()));
		}
		return Component.translatable("kultivator.core.root_description", names, grade.displayName().copy().withColor(grade.color()));
	}

	/** Adds {@code root test} to /cultivate. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
	}

	private SpiritRoots() {
	}
}
