package dev.kultivator.dunia.naga;

import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The deeds of module B that span several moments, kept per player in {@code extra.naga_feats} (saved with the
 * cultivation data) and turned into advancements of the "Kultivasi" tab:
 * <ul>
 * <li>{@code dunia/jiao_three} Tiga Warna Langit: slain or subdued a Jiao of every kind (bit mask {@code kinds});</li>
 * <li>{@code dunia/jiao_rider} Penunggang Awan: {@link #RIDE_GOAL} blocks flown on one's own Jiao ({@code ride});</li>
 * <li>{@code dunia/body_guard} Penjaga Raga: one's beasts drove a monster from one's body ({@code guarded}).</li>
 * </ul>
 */
final class NagaFeats {

	static final String KEY = "naga_feats";
	/** Blocks flown on a Jiao for Penunggang Awan. */
	static final double RIDE_GOAL = 2000.0D;

	private static CompoundTag read(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(KEY);
	}

	private static void write(ServerPlayer player, CompoundTag tag) {
		CultivationData data = Cultivators.get(player);
		data.extra().put(KEY, tag);
		Cultivators.dirty(player);
	}

	/** {@code player} slew or subdued a Jiao of {@code kind}. */
	static void kindMet(ServerPlayer player, NagaKind kind) {
		CompoundTag tag = read(player);
		int before = tag.getIntOr("kinds", 0);
		int mask = before | (1 << kind.ordinal());
		if (mask == before) {
			return;
		}
		tag.putInt("kinds", mask);
		write(player, tag);
		if (mask == 7) {
			Advancements.grant(player, "dunia/jiao_three");
		}
	}

	/** The kinds met so far (bit 0 air, 1 awan, 2 petir). */
	static int kinds(ServerPlayer player) {
		return read(player).getIntOr("kinds", 0);
	}

	/** Adds {@code blocks} flown on one's own Jiao; the goal grants Penunggang Awan once. */
	static void rode(ServerPlayer player, double blocks) {
		if (!(blocks > 0.0D) || !Double.isFinite(blocks)) {
			return;
		}
		CompoundTag tag = read(player);
		double before = tag.getDoubleOr("ride", 0.0D);
		double after = Math.min(1.0E9D, before + blocks);
		tag.putDouble("ride", after);
		write(player, tag);
		if (before < RIDE_GOAL && after >= RIDE_GOAL) {
			Advancements.grant(player, "dunia/jiao_rider");
			dev.kultivator.util.Msg.chat(player, Component.translatable("kultivator.naga.feat.rider", (int) RIDE_GOAL).withStyle(ChatFormatting.AQUA));
		}
	}

	static double ridden(ServerPlayer player) {
		return read(player).getDoubleOr("ride", 0.0D);
	}

	/** One's beasts drove a monster from one's body (count kept for the guard report). */
	static void guarded(ServerPlayer player) {
		CompoundTag tag = read(player);
		tag.putInt("guarded", tag.getIntOr("guarded", 0) + 1);
		write(player, tag);
		Advancements.grant(player, "dunia/body_guard");
	}

	private NagaFeats() {
	}
}
