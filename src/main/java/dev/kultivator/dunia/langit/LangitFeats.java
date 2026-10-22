package dev.kultivator.dunia.langit;

import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The long achievements of module L (detail pass), kept in the player's own {@code langit} compound so they survive
 * restarts: every sky event kind witnessed under the open sky ({@code dunia/sky_all}) and the eclipse-touched monsters
 * slain ({@code dunia/eclipse_hunter} at {@link #ECLIPSE_HUNTER}). The other new ones are granted where they happen
 * (Meteor Agung core, Fajar Ungu facing east, the day's last Embun Roh, a Harta Langit or Abadi, a guardian slain).
 */
final class LangitFeats {

	/** Eclipse-touched monsters a player must slay for {@code dunia/eclipse_hunter}. */
	static final int ECLIPSE_HUNTER = 5;

	/** The player saw {@code kind} under the open sky: its bit, and {@code dunia/sky_all} once all three are seen. */
	static void witnessed(ServerPlayer player, SkyKind kind) {
		CompoundTag own = Langit.extra(Cultivators.get(player));
		int seen = own.getIntOr("seen", 0);
		int now = seen | (1 << kind.ordinal());
		if (now == seen) {
			return;
		}
		own.putInt("seen", now);
		Cultivators.dirty(player);
		int all = (1 << SkyKind.values().length) - 1;
		if ((now & all) == all) {
			Advancements.grant(player, "dunia/sky_all");
		}
	}

	/** The kinds {@code player} has seen (bits by {@code SkyKind} ordinal). */
	static int seen(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(Langit.EXTRA_KEY).getIntOr("seen", 0);
	}

	/** An eclipse-touched monster fell to {@code player}: the count, a line at the milestone, the advancement. */
	static void eclipseSlain(ServerPlayer player) {
		CompoundTag own = Langit.extra(Cultivators.get(player));
		int slain = own.getIntOr("gerhana_slain", 0) + 1;
		own.putInt("gerhana_slain", slain);
		Cultivators.dirty(player);
		if (slain == ECLIPSE_HUNTER) {
			Msg.actionBar(player, Component.translatable("kultivator.langit.gerhana.hunter", ECLIPSE_HUNTER).withColor(0xFF6A58));
		}
		if (slain >= ECLIPSE_HUNTER) {
			Advancements.grant(player, "dunia/eclipse_hunter");
		}
	}

	static int eclipseSlainCount(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(Langit.EXTRA_KEY).getIntOr("gerhana_slain", 0);
	}

	private LangitFeats() {
	}
}
