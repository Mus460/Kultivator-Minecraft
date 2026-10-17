package dev.kultivator.cultivation;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.technique.Techniques;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * All cultivation XP goes through {@link #addXp}: multipliers, stage-ups (minor breakthroughs), the bottleneck at Peak
 * and Mortal, and Stored Qi (docs/DESIGN.md section 1). Rank changes from breakthroughs and commands also come here.
 */
public final class Progression {

	/** Sub-compound of {@link CultivationData#extra()} owned by this module. */
	private static final String EXTRA_KEY = "progression";
	/** Rank step at which the player was last told about the bottleneck (-1: never). */
	private static final String NOTIFIED_STEP = "notified_step";
	/** Whether the root was tested when that notice was given (a Mortal is told again once it is tested). */
	private static final String NOTIFIED_TESTED = "notified_tested";
	/** Extra XP factors from other modules (DESIGN 20.2.10), multiplied into {@link #xpMultiplier(ServerPlayer, XpSource)}. */
	private static final List<ToDoubleFunction<ServerPlayer>> MULTIPLIERS = new ArrayList<>();

	public static void init() {
		// Nothing to register: XP is pushed in by the sources (XpSources, meditation, pills, ...).
	}

	// ================================================================== XP

	/**
	 * Adds XP from {@code source} with all multipliers ({@link XpSource#EXACT}: none). Amounts that are not positive
	 * numbers are ignored. Fills the bar, advances stages, stops at bottlenecks and keeps the rest as Stored Qi.
	 * <p>
	 * MEDITATION and PILL callers must already include their own site / vein / formation / toxicity factors in
	 * {@code amount}; {@link #xpMultiplier} does not add them again.
	 */
	public static void addXp(ServerPlayer player, double amount, XpSource source) {
		grantXp(player, amount, source);
	}

	/** Same as {@link #addXp}, but returns the XP actually added after multipliers (0 when nothing was added). */
	public static double grantXp(ServerPlayer player, double amount, XpSource source) {
		if (!(amount > 0.0D) || !Double.isFinite(amount)) {
			return 0.0D;
		}
		CultivationData data = Cultivators.get(player);
		double gain = amount * xpMultiplier(player, source);
		if (!(gain > 0.0D) || !Double.isFinite(gain)) {
			return 0.0D;
		}
		data.addTotalXp(gain);
		int stageUps = distribute(data, gain);
		if (stageUps > 0) {
			onStageUp(player, data, stageUps, true);
		}
		checkBottleneck(player, data);
		Cultivators.dirty(player);
		return gain;
	}

	/**
	 * The multiplier {@link #addXp} uses for {@code source}: root grade (once tested) x breakthrough quality factor x
	 * server config x 0.5 during a Qi Deviation x every registered multiplier provider (DESIGN 20.2.10). EXACT is always
	 * 1. Site, vein, formation and toxicity factors are NOT part of it: the meditation and pill modules apply those
	 * themselves.
	 */
	public static double xpMultiplier(ServerPlayer player, XpSource source) {
		double base = xpMultiplier(Cultivators.get(player), source);
		if (source == XpSource.EXACT || MULTIPLIERS.isEmpty()) {
			return base;
		}
		for (ToDoubleFunction<ServerPlayer> provider : MULTIPLIERS) {
			double factor = provider.applyAsDouble(player);
			if (Double.isFinite(factor) && factor >= 0.0D) {
				base *= factor;
			}
		}
		return base;
	}

	/**
	 * Adds a factor to every player's XP multiplier (not for EXACT), e.g. Kebanggaan Guru (DESIGN 20.3.20). Register it
	 * once from a module's {@code init()}; a provider returns 1 when it has nothing to add.
	 */
	public static void addMultiplierProvider(ToDoubleFunction<ServerPlayer> provider) {
		MULTIPLIERS.add(provider);
	}

	/** Pure version of {@link #xpMultiplier(ServerPlayer, XpSource)} (the config part is the local one on a client). */
	public static double xpMultiplier(CultivationData data, XpSource source) {
		if (source == XpSource.EXACT) {
			return 1.0D;
		}
		double root = data.rootTested() ? data.rootGrade().multiplier() : 1.0D;
		double config = Math.max(0.0D, KultivatorConfig.get().xpMultiplier);
		double deviation = data.deviating() ? 0.5D : 1.0D;
		return root * data.qualityFactor() * config * deviation;
	}

	/**
	 * Puts {@code amount} into the bar following the rules of section 1 (no multipliers, no total XP): stage-ups below
	 * Peak with carry-over, a full bar and Stored Qi at a bottleneck, only Stored Qi at the very end. Returns the number
	 * of stage-ups.
	 */
	private static int distribute(CultivationData data, double amount) {
		double left = amount;
		int stageUps = 0;
		// Each pass either ends the loop or climbs one step, so MAX_STEP + 2 passes always suffice.
		for (int guard = 0; left > 0.0D && guard <= Rank.MAX_STEP + 2; guard++) {
			Rank rank = data.rank();
			if (rank.isMax()) {
				data.setStoredQi(data.storedQi() + left);
				return stageUps;
			}
			double room = Math.max(0.0D, rank.xpRequired() - data.xp());
			if (rank.isBottleneck()) {
				double into = Math.min(room, left);
				data.setXp(data.xp() + into);
				left -= into;
				if (left > 0.0D) {
					data.setStoredQi(data.storedQi() + left);
				}
				return stageUps;
			}
			if (left < room) {
				data.setXp(data.xp() + left);
				return stageUps;
			}
			left -= room;
			data.setRank(rank.next());
			data.setXp(0.0D);
			stageUps++;
		}
		if (left > 0.0D) {
			data.setStoredQi(data.storedQi() + left);
		}
		return stageUps;
	}

	/** Stats, unlocks, advancement and the chat line for {@code count} stage-ups; with {@code effects} also FX + sound. */
	private static void onStageUp(ServerPlayer player, CultivationData data, int count, boolean effects) {
		Rank rank = data.rank();
		Stats.apply(player);
		Techniques.refreshUnlocks(player);
		Advancements.grant(player, "stage_up");
		MutableComponent name = rank.displayName().withColor(rank.realm().color());
		if (count == 1) {
			Msg.chat(player, Component.translatable("kultivator.progression.stage_up", name).withStyle(ChatFormatting.AQUA));
		} else {
			Msg.chat(player, Component.translatable("kultivator.progression.stage_up_multi", count, name).withStyle(ChatFormatting.AQUA));
		}
		if (!effects) {
			return;
		}
		ServerLevel level = player.level();
		Fx.send(level, player.position(), FxType.STAGE_UP, rank.realm().ordinal(), rank.stage(), count);
		float pitch = 0.9F + 0.12F * rank.stage();
		level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.PLAYERS, 1.4F, pitch);
		level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.PLAYER_LEVELUP,
				SoundSource.PLAYERS, 0.3F, 1.5F);
	}

	/** Tells the player once per bottleneck (and once at the end of the path) what to do next. */
	private static void checkBottleneck(ServerPlayer player, CultivationData data) {
		Rank rank = data.rank();
		int step = rank.step();
		CompoundTag tag = data.extra().getCompoundOrEmpty(EXTRA_KEY);
		int notified = tag.getIntOr(NOTIFIED_STEP, -1);
		if (rank.isMax()) {
			if (notified == step) {
				return;
			}
			remember(data, tag, step, data.rootTested());
			Msg.chat(player, Component.translatable("kultivator.progression.peak_of_path",
					rank.displayName().withColor(rank.realm().color())).withStyle(ChatFormatting.GOLD));
			Msg.actionBar(player, Component.translatable("kultivator.progression.peak_of_path_bar").withStyle(ChatFormatting.GOLD));
			XpSources.holdDisplay(player, 60);
			return;
		}
		if (!rank.isBottleneck() || !data.barFull()) {
			return;
		}
		boolean mortal = rank.realm().isMortal();
		boolean tested = data.rootTested();
		if (notified == step && (!mortal || tag.getBooleanOr(NOTIFIED_TESTED, tested) == tested)) {
			return;
		}
		remember(data, tag, step, tested);

		Component screenKey = Component.keybind("key.kultivator.info").withStyle(ChatFormatting.YELLOW);
		if (mortal && !tested) {
			Msg.chat(player, Component.translatable("kultivator.progression.bottleneck_mortal", screenKey,
					command("/cultivate root test")).withStyle(ChatFormatting.LIGHT_PURPLE));
			Msg.actionBar(player, Component.translatable("kultivator.progression.bottleneck_mortal_bar").withStyle(ChatFormatting.LIGHT_PURPLE));
		} else if (mortal) {
			Msg.chat(player, Component.translatable("kultivator.progression.bottleneck_awakening", screenKey,
					command("/cultivate breakthrough")).withStyle(ChatFormatting.LIGHT_PURPLE));
			Msg.actionBar(player, Component.translatable("kultivator.progression.bottleneck_bar").withStyle(ChatFormatting.LIGHT_PURPLE));
		} else {
			Msg.chat(player, Component.translatable("kultivator.progression.bottleneck",
					rank.displayName().withColor(rank.realm().color()), screenKey,
					command("/cultivate breakthrough")).withStyle(ChatFormatting.LIGHT_PURPLE));
			Msg.actionBar(player, Component.translatable("kultivator.progression.bottleneck_bar").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		Msg.chat(player, Component.translatable("kultivator.progression.bottleneck_stored").withStyle(ChatFormatting.GRAY));
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.PLAYERS, 1.0F, 0.6F);
		XpSources.holdDisplay(player, 60);
	}

	private static void remember(CultivationData data, CompoundTag tag, int step, boolean tested) {
		tag.putInt(NOTIFIED_STEP, step);
		tag.putBoolean(NOTIFIED_TESTED, tested);
		data.extra().put(EXTRA_KEY, tag);
	}

	/** A yellow, underlined command the player can click to put it into the chat box. */
	private static MutableComponent command(String text) {
		return Component.literal(text).withStyle(style -> style.withColor(ChatFormatting.YELLOW).withUnderlined(true)
				.withClickEvent(new ClickEvent.SuggestCommand(text)));
	}

	// ================================================================== rank changes

	/** Puts the player at {@code rank} (commands, breakthroughs): clamps XP, re-applies stats, unlocks techniques. */
	public static void setRank(ServerPlayer player, Rank rank) {
		CultivationData data = Cultivators.get(player);
		data.setRank(rank);
		data.setXp(Math.min(data.xp(), data.rank().xpRequired()));
		Stats.apply(player);
		Techniques.refreshUnlocks(player);
		Qi.clamp(player);
		Cultivators.dirty(player);
	}

	/**
	 * After a successful breakthrough: moves to the next rank with an empty bar, lets Stored Qi flow in
	 * ({@link #absorbStoredQi}), re-applies stats, unlocks techniques and grants the realm advancements (also those of
	 * every lower realm, in case of debug jumps). Recording the breakthrough quality is the caller's job; call
	 * {@link Stats#apply} again if it is recorded afterwards.
	 */
	public static void completeBreakthrough(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		Rank current = data.rank();
		if (!current.isMax()) {
			data.setRank(current.next());
			data.setXp(0.0D);
			absorbStoredQi(player);
		}
		Stats.apply(player);
		Techniques.refreshUnlocks(player);
		grantRealmAdvancements(player, data.realm());
		Cultivators.dirty(player);
	}

	/**
	 * Lets Stored Qi flow into the bar with the normal rules (stage-ups, stopping at the next bottleneck) but without
	 * multipliers and without counting it as new total XP. What does not fit stays stored. Returns the amount that
	 * flowed into the bar.
	 */
	public static double absorbStoredQi(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		double stored = data.storedQi();
		if (!(stored > 0.0D) || data.rank().isMax()) {
			return 0.0D;
		}
		data.setStoredQi(0.0D);
		int stageUps = distribute(data, stored);
		double absorbed = Math.max(0.0D, stored - data.storedQi());
		if (absorbed >= 1.0D) {
			Msg.chat(player, Component.translatable("kultivator.progression.stored_flow", Msg.number(absorbed))
					.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		if (stageUps > 0) {
			onStageUp(player, data, stageUps, false);
		}
		checkBottleneck(player, data);
		Cultivators.dirty(player);
		return absorbed;
	}

	/**
	 * Hardcore failure: drops one realm to the Peak of the previous realm (inside Immortal Ascension above layer 1: the
	 * previous layer's Peak) with an empty bar. Stored Qi is kept; the quality of a realm that is lost completely is
	 * forgotten. Mortal stays Mortal.
	 */
	public static void dropRealm(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		Rank rank = data.rank();
		Realm realm = rank.realm();
		if (realm.isMortal()) {
			return;
		}
		Rank target;
		if (realm.layers() > 1 && rank.layer() > 0) {
			target = new Rank(realm, rank.layer() - 1, realm.stages() - 1);
		} else {
			Realm previous = realm.previous();
			target = new Rank(previous, previous.layers() - 1, previous.stages() - 1);
			data.setQuality(realm, null);
		}
		data.setRank(target);
		data.setXp(0.0D);
		Stats.apply(player);
		Techniques.refreshUnlocks(player);
		Qi.clamp(player);
		Msg.chat(player, Component.translatable("kultivator.progression.realm_lost",
				target.displayName().withColor(target.realm().color())).withStyle(ChatFormatting.RED));
		Cultivators.dirty(player);
	}

	/** Grants {@code realm_<id>} for every realm from Qi Refining up to {@code reached}. */
	public static void grantRealmAdvancements(ServerPlayer player, Realm reached) {
		for (Realm each : Realm.values()) {
			if (!each.isMortal() && each.ordinal() <= reached.ordinal()) {
				Advancements.grant(player, "realm_" + each.id());
			}
		}
	}

	private Progression() {
	}
}
