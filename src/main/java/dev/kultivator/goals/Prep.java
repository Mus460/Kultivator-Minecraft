package dev.kultivator.goals;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.QiFormations;
import dev.kultivator.cultivation.SpiritSites;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.elite.HuFaTribulation;
import dev.kultivator.harta.StoneBottleneck;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Persiapan (DESIGN 19.3.10): the server's part of the breakthrough forecast. Every 100 ticks while the bar is full at a
 * bottleneck it writes {@code sessionExtra.prep {q, f, scale, allies, stones, trials, guards, guards_q, site, root, pill, stored}}: the phase-7 quality
 * bonus of the next breakthrough ({@code Breakthrough.qualityBonus}), the share of the first strike that lands
 * ({@code Tribulation.strikeFactor}) and the server's tribulation damage scale, the allies within 16 blocks, the parts of
 * the bonus this module can name and (Foundation) the place bonus. It only reads; the client screens do the rest. Marked
 * dirty only when a value changed.
 * <p>
 * Sumber: game design (UX).
 */
public final class Prep {

	public static final String KEY = "prep";
	public static final double ALLY_RANGE = 16.0D;
	/** The tips of the Foundation chance (DESIGN 3; the owner's constants). */
	public static final double SITE_TIP = Breakthrough.SITE_BONUS;
	public static final double FORMATION_TIP = Breakthrough.FORMATION_BONUS;

	/** The tribulation strength of a target (targets below Golden Core are raised to it, as {@code Tribulation.start} does). */
	public static Rank strength(Rank target) {
		return target.realm().atLeast(Realm.GOLDEN_CORE) ? target : new Rank(Realm.GOLDEN_CORE, 0, 0);
	}

	/** Every 100 ticks. True when {@code sessionExtra.prep} changed. */
	static boolean update(ServerPlayer player, CultivationData data) {
		CompoundTag session = data.sessionExtra();
		Rank rank = data.rank();
		if (!data.barFull() || !rank.nextIsBreakthrough() || data.breakingThrough()) {
			if (session.contains(KEY)) {
				session.remove(KEY);
				return true;
			}
			return false;
		}
		Rank target = rank.next();
		CompoundTag prep = new CompoundTag();
		if (!rank.realm().isMortal()) {
			prep.putDouble("q", round(Breakthrough.qualityBonus(player, data, target)));
			prep.putDouble("stones", round(StoneBottleneck.bonus(player)));
			prep.putDouble("trials", round(RealmTrials.bonus(data, target)));
		}
		if (target.realm() == Realm.FOUNDATION) {
			prep.putDouble("site", round(siteBonus(player, data)));
			double[] parts = Breakthrough.chanceParts(data);
			prep.putDouble("root", round(parts[1]));
			prep.putDouble("pill", round(parts[2]));
			prep.putDouble("stored", round(parts[3]));
		}
		if (target.realm().atLeast(Realm.GOLDEN_CORE)) {
			prep.putDouble("f", round(Tribulation.strikeFactor(player, strength(target), 1, Tribulation.strikeCount(strength(target)) == 1)));
			prep.putDouble("scale", round(KultivatorConfig.get().tribulationDamageScale));
			int guards = HuFaTribulation.guards(player);
			prep.putInt("guards", guards);
			prep.putDouble("guards_q", round(HuFaTribulation.qualityFor(guards)));
		}
		prep.putInt("allies", allies(player));
		if (!prep.equals(session.getCompoundOrEmpty(KEY))) {
			session.put(KEY, prep);
			return true;
		}
		return false;
	}

	/**
	 * The place bonus the Foundation breakthrough would read if it began now: the site and qi formation of the running
	 * meditation, else where the player stands (read the same way {@code Breakthrough} reads them; block reads only).
	 */
	static double siteBonus(ServerPlayer player, CultivationData data) {
		double site = data.meditating() ? data.siteMultiplier() : SpiritSites.multiplier(player);
		int formation = data.meditating() ? data.formationTier() : QiFormations.tier(player.level(), player.blockPosition().below());
		return (site > 1.01D ? SITE_TIP : 0.0D) + FORMATION_TIP * formation;
	}

	/** Other players, the player's loaded disciples and spirit beasts within 16 blocks. */
	static int allies(ServerPlayer player) {
		double range = ALLY_RANGE * ALLY_RANGE;
		int count = 0;
		for (ServerPlayer other : player.level().players()) {
			if (other != player && !other.isSpectator() && other.isAlive() && other.distanceToSqr(player) <= range) {
				count++;
			}
		}
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (disciple.level() == player.level() && disciple.distanceToSqr(player) <= range) {
				count++;
			}
		}
		for (Mob beast : SpiritBeasts.loadedBeasts(player.getUUID())) {
			if (beast.isAlive() && beast.level() == player.level() && beast.distanceToSqr(player) <= range) {
				count++;
			}
		}
		return count;
	}

	private static double round(double value) {
		return Math.round(value * 1000.0D) / 1000.0D;
	}

	private Prep() {
	}
}
