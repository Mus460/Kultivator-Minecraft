package dev.kultivator.cultivation;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.talisman.TalismanFxIds;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * Meditation (DESIGN 4, reworked in 19.3.5): the player sits cross-legged and gathers qi. Every second it gives
 * {@link #gainPerSecond} XP (realm base rate × site × formation × spirit-stone fuel, capped at ×3.5, × sect formation G capped at ×1.5, × vein density × sect
 * bonus × closed-door seclusion), drains the spirit vein, and every minute may bring an enlightenment. Qi regeneration
 * itself is boosted by {@code Qi.tick} (it reads {@code meditating()}). Moving, getting hurt, attacking, using an item or
 * block, changing dimension, dying or starting a breakthrough ends it; a session of a minute or more ends with a
 * summary line.
 * <p>
 * Kultivasi Tertutup (closed-door seclusion): unbroken meditation earns a stack every 5 minutes (+5% each, up to 5) and
 * after 30 minutes raises the enlightenment chance ×1.5. Within 6 blocks of a protected cave abode a break of 10 s or
 * less keeps the stacks. Sumber: RMJ ch. 520 and <i>RMJ: Immortal Realm</i> ch. 1142 (years of seclusion); the numbers
 * are game design.
 */
public final class Meditation {

	/** How far the player may drift from where they sat down before the meditation breaks. */
	private static final double MAX_HORIZONTAL = 0.5D;
	private static final double MAX_VERTICAL = 1.0D;
	/** Below this density the player is told the vein is thin. */
	private static final double THIN_VEIN = 0.2D;
	/** Comprehension per second for each site that matches the chosen Dao. */
	private static final double SITE_COMPREHENSION = 0.004D;

	/** The base rate: {@code max(0.6 + 0.25 × realm, Rank(realm, layer, 0).xpRequired() / BASE_DIVISOR)} XP per second. */
	public static final double BASE_FLAT = 0.6D;
	public static final double BASE_PER_REALM = 0.25D;
	public static final double BASE_DIVISOR = 3000.0D;
	/** Site × formation × fuel never multiplies the base by more than this. */
	public static final double MULTIPLIER_CAP = 3.5D;
	/** Density never lowers the gain below this share. */
	public static final double DENSITY_FLOOR = 0.15D;
	/** Density never raises the gain above this share (the vein itself still reads 0-200%). */
	public static final double DENSITY_XP_CAP = 1.4D;
	/** Vein drain per second: {@code DRAIN_BASE × (1 + DRAIN_PER_REALM × realm)} × the factors below. */
	public static final double DRAIN_BASE = 0.0015D;
	public static final double DRAIN_PER_REALM = 0.10D;
	public static final double DRAIN_FUEL = 0.5D;
	public static final double DRAIN_GREAT_FORMATION = 0.6D;
	public static final double DRAIN_HEAVENLY_FORMATION = 0.35D;
	public static final double DRAIN_ABODE = 0.5D;
	/** Balance change (flow audit): below Deity Transformation the vein drains at this share (a casual seat lasts twice as long). */
	public static final double DRAIN_LOW_REALM = 0.5D;
	/** Balance change (flow audit): the site factor in the enlightenment chance never counts above this. */
	public static final double ENLIGHTENMENT_SITE_CAP = 2.0D;
	/** A Great (Heavenly) qi formation keeps the density at or above this while it is active at the seat. */
	public static final double FLOOR_GREAT_FORMATION = 0.30D;
	public static final double FLOOR_HEAVENLY_FORMATION = 0.45D;
	/** Spirit-stone fuel by grade (Low, Mid, High, Supreme): seconds one stone lasts and its XP factor. */
	public static final int[] FUEL_SECONDS = {30, 150, 300, 600};
	public static final double[] FUEL_FACTOR = {1.5D, 2.0D, 2.5D, 3.0D};
	/** Closed-door seclusion: one stack per period of unbroken meditation, up to the maximum, +5% XP each. */
	public static final int SECLUSION_PERIOD = 300;
	public static final int SECLUSION_MAX = 5;
	public static final double SECLUSION_BONUS = 0.05D;
	/** After this many unbroken seconds the enlightenment chance is multiplied. */
	public static final int SECLUSION_DEEP = 1800;
	public static final double SECLUSION_DEEP_FACTOR = 1.5D;
	/** Unbroken seconds of the advancement {@code closed_door_hour}. */
	public static final int CLOSED_DOOR_HOUR = 3600;
	/** Seconds in a Great vein for the advancement {@code great_vein}. */
	public static final int GREAT_VEIN_SECONDS = 600;
	/** A meditation this long ends with a summary line. */
	public static final int SUMMARY_SECONDS = 60;
	/** A break this short near a protected abode keeps the seclusion. */
	public static final int ABODE_BREAK_TICKS = 200;
	/** Clear Mind boon (L8): extra enlightenment chance per minute and healing per second while meditating. */
	public static final double CLEAR_MIND_CHANCE = 0.02D;
	public static final float CLEAR_MIND_HEAL = 0.5F;
	/** A protected cave abode adds this to the enlightenment chance per minute. */
	public static final double ABODE_CHANCE = 0.01D;

	private static final int JADE = 0x8FE3C8;
	private static final int GOLD = 0xFFD54A;
	private static final int MUTED = 0xA8B5B0;
	private static final int VIOLET = 0xC69CFF;

	/** Server-side state of one meditation, from sitting down to standing up. */
	private static final class Session {
		final Vec3 start;
		final ResourceKey<Level> dimension;
		final long startTime;
		final int startEnlightenments;
		final double startDensity;
		int ticks;
		int seconds;
		/** Unbroken seconds of closed-door seclusion (restored after a short break at the abode). */
		int unbroken;
		int stacks;
		int greatSeconds;
		SpiritSites.Result site = SpiritSites.Result.NONE;
		int tier;
		int grade;
		/** 1 abode bonuses active, −1 at the abode but unprotected, 0 not at an abode. */
		int abode;
		boolean warnedThin;
		boolean warnedAbode;
		boolean fuelTold;
		boolean reserveTold;
		boolean deepTold;
		/** The absorption limit message (audit M4 T6) was shown once this session. */
		boolean capTold;
		double xpGained;
		double storedGained;

		Session(Vec3 start, ResourceKey<Level> dimension, long startTime, int startEnlightenments, double startDensity) {
			this.start = start;
			this.dimension = dimension;
			this.startTime = startTime;
			this.startEnlightenments = startEnlightenments;
			this.startDensity = startDensity;
		}
	}

	/** A consumed spirit stone: its grade and the game time its period ends. */
	private record Fuel(int grade, long end) {
	}

	/** Where a seclusion was broken off at the abode: when, and how many unbroken seconds it had. */
	private record Break(long time, int unbroken) {
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final Map<UUID, Fuel> FUEL = new HashMap<>();
	private static final Map<UUID, Break> BREAKS = new HashMap<>();
	/** True while the meditate key is asking for a breakthrough: the stop it causes needs no message of its own. */
	private static boolean quietStop;

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && (damageTaken > 0.0F || baseDamage > 0.0F) && isMeditating(player)) {
				end(player, "kultivator.meditation.stop.hurt");
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			interrupt(player, level, "kultivator.meditation.stop.attack");
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			interrupt(player, level, "kultivator.meditation.stop.use");
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			interrupt(player, level, "kultivator.meditation.stop.use");
			return InteractionResult.PASS;
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (isMeditating(player)) {
				end(player, "kultivator.meditation.stop.dimension");
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				Enlightenment.forget(player);
				BREAKS.remove(player.getUUID());
				if (isMeditating(player) || SESSIONS.containsKey(player.getUUID())) {
					end(player, null);
				}
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			SESSIONS.remove(newPlayer.getUUID());
			Enlightenment.forget(newPlayer);
			SpiritSites.forget(newPlayer);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SESSIONS.clear();
			FUEL.clear();
			BREAKS.clear();
		});
	}

	private static void interrupt(Player player, Level level, String reasonKey) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && isMeditating(serverPlayer)) {
			end(serverPlayer, reasonKey);
		}
	}

	// ================================================================ ticking

	public static void tick(ServerPlayer player, CultivationData data) {
		Enlightenment.tick(player, data);
		UUID id = player.getUUID();
		Session session = SESSIONS.get(id);
		if (!data.meditating()) {
			if (session != null) {
				SESSIONS.remove(id);
				clearHud(player, data);
			}
			return;
		}
		if (session == null) {
			// Someone else switched the flag on (or a reload): start a session where the player is.
			session = newSession(player, data);
			SESSIONS.put(id, session);
			evaluate(player, data, session, true);
		}
		if (!player.isAlive() || data.breakingThrough()) {
			// A breakthrough keeps the seated pose through its own flag; it shows its own messages.
			end(player, null);
			return;
		}
		if (player.level().dimension() != session.dimension) {
			end(player, "kultivator.meditation.stop.dimension");
			return;
		}
		Vec3 now = player.position();
		double dx = now.x - session.start.x;
		double dz = now.z - session.start.z;
		if (dx * dx + dz * dz > MAX_HORIZONTAL * MAX_HORIZONTAL || Math.abs(now.y - session.start.y) > MAX_VERTICAL) {
			end(player, "kultivator.meditation.stop.moved");
			return;
		}
		if (cannotSit(player)) {
			end(player, "kultivator.meditation.stop.state");
			return;
		}

		session.ticks++;
		if (session.ticks % 100 == 0) {
			evaluate(player, data, session, false);
		}
		if (session.ticks % 20 == 0) {
			second(player, data, session);
		}
		if (session.ticks % 40 == 0 && data.meditating()) {
			actionBar(player, data, session);
		}
	}

	private static Session newSession(ServerPlayer player, CultivationData data) {
		ServerLevel level = player.level();
		Session session = new Session(player.position(), level.dimension(), level.getGameTime(), data.enlightenments(),
				SpiritVeins.density(level, player.blockPosition()));
		Break pause = BREAKS.remove(player.getUUID());
		if (pause != null && level.getGameTime() - pause.time() <= ABODE_BREAK_TICKS && CaveAbode.bonusActive(player)) {
			// A short break at the protected abode: the seclusion goes on (DESIGN 19.3.5).
			session.unbroken = pause.unbroken();
			session.stacks = Math.min(SECLUSION_MAX, session.unbroken / SECLUSION_PERIOD);
			session.deepTold = session.unbroken >= SECLUSION_DEEP;
			if (session.stacks > 0) {
				Msg.chat(player, Component.translatable("kultivator.kultivasi.seclusion.kept", percentBonus(session.stacks)).withColor(VIOLET));
			}
		}
		return session;
	}

	/** Re-checks spiritual sites, the formation, the vein grade and the abode (on start and every 5 s). */
	private static void evaluate(ServerPlayer player, CultivationData data, Session session, boolean starting) {
		ServerLevel level = player.level();
		BlockPos seat = player.blockPosition().below();
		SpiritSites.Result site = SpiritSites.evaluate(player);
		int tier = QiFormations.tier(level, seat);
		boolean tierChanged = tier != session.tier;
		if (!starting && tierChanged) {
			if (tier > 0) {
				announceFormation(player, tier);
			} else {
				Msg.chat(player, Component.translatable("kultivator.meditation.formation_lost").withColor(MUTED));
			}
		}
		session.site = site;
		session.tier = tier;
		data.setSiteMultiplier(site.multiplier());
		data.setFormationTier(tier);
		int grade = SpiritVeins.grade(level, player.blockPosition());
		if (starting && grade != SpiritVeins.NORMAL) {
			Msg.chat(player, Component.translatable("kultivator.kultivasi.vein.here", SpiritVeins.gradeName(grade),
					percentText(SpiritVeins.cap(level, player.blockPosition()))).withColor(SpiritVeins.gradeColor(grade)));
		}
		session.grade = grade;
		putInt(player, data, "vein_grade", grade);
		int abode = CaveAbode.state(player);
		session.abode = abode;
		putInt(player, data, "abode", abode);
		if (abode < 0 && !session.warnedAbode) {
			session.warnedAbode = true;
			Msg.chat(player, Component.translatable("kultivator.kultivasi.abode.unprotected").withColor(0xFF9A7A));
		}
		putInt(player, data, "seclusion", session.stacks);
		Cultivators.dirty(player);
		if (site.any()) {
			Advancements.grant(player, "spiritual_site");
		}
		if (tier >= 1) {
			Advancements.grant(player, "qi_formation_small");
		}
		if (tier >= 2) {
			Advancements.grant(player, "qi_formation_great");
		}
		if (tier >= 3) {
			Advancements.grant(player, "qi_formation_heavenly");
		}
		if (tier > 0 && (starting || tierChanged)) {
			// The activation flash for everyone nearby; the continuous lines are drawn by the client from synced state.
			Vec3 top = new Vec3(seat.getX() + 0.5D, seat.getY() + 1.0D, seat.getZ() + 0.5D);
			Fx.send(level, top, FxType.QI_FORMATION, tier, 0, 0.0F);
		}
	}

	private static void announceFormation(ServerPlayer player, int tier) {
		Msg.chat(player, Component.translatable("kultivator.meditation.formation_awake", QiFormations.name(tier))
				.withColor(QiFormations.color(tier)));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
				0.9F, 0.8F + tier * 0.2F);
	}

	// ================================================================ the formula

	/** The realm base rate of {@link #gainPerSecond}: unchanged up to Nascent Soul, then a share of the realm's bar. */
	public static double baseRate(Rank rank) {
		Realm realm = rank.realm();
		double flat = BASE_FLAT + BASE_PER_REALM * realm.ordinal();
		return Math.max(flat, new Rank(realm, rank.layer(), 0).xpRequired() / BASE_DIVISOR);
	}

	/**
	 * Meditation XP per second before {@code Progression}'s multipliers (DESIGN 19.3.5), shared with
	 * {@code DiscipleCultivation.masterMeditationGain}: {@code base × min(3.5, site × formation × fuel) × min(1.5, sect G)
	 * × clamp(density, 0.15, 1.4)
	 * × Sects.meditationBonus × (1 + 0.05 × seclusion stacks)}. Site and formation are the synced values of the session.
	 */
	public static double gainPerSecond(ServerPlayer player, CultivationData data) {
		double density = SpiritVeins.density(player.level(), player.blockPosition());
		return baseRate(data.rank()) * Math.min(MULTIPLIER_CAP, innerMultiplier(player, data)) * sectFactor(player) * gainFactor(player)
				* Math.max(DENSITY_FLOOR, Math.min(DENSITY_XP_CAP, density))
				* dev.kultivator.sect.Sects.meditationBonus(player) * (1.0D + SECLUSION_BONUS * seclusionStacks(player));
	}

	/**
	 * Balance change (audit M4 T6, approved): the sect formation factor G sits outside {@link #MULTIPLIER_CAP} and is capped
	 * at this on its own, so a sect field still adds up to +50% on top of a full site × formation × fuel seat.
	 */
	public static final double GATHER_SECT_CAP = 1.5D;

	/** Site × personal Qi formation × spirit-stone fuel: the part capped at {@link #MULTIPLIER_CAP}. */
	public static double innerMultiplier(ServerPlayer player, CultivationData data) {
		double site = data.siteMultiplier() > 0.0D ? data.siteMultiplier() : 1.0D;
		return site * QiFormations.multiplier(data.formationTier()) * fuelFactor(player);
	}

	/**
	 * The sect gathering factor G at the player (every gathering provider: sect formation field, soul lantern), clamped to
	 * 1..{@link #GATHER_SECT_CAP}; 1 without a field. {@link #formationTerm} and its stack cap stay for the display only.
	 */
	public static double sectFactor(ServerPlayer player) {
		if (GATHERING.isEmpty()) {
			return 1.0D;
		}
		return Math.max(1.0D, Math.min(GATHER_SECT_CAP, sectGathering(player)));
	}

	// ================================================================ phase 7c sect formation hooks (DESIGN 24.13 item 8)

	private static final List<ToDoubleFunction<ServerPlayer>> GATHERING = new ArrayList<>();
	private static final List<ToDoubleFunction<ServerPlayer>> STACK_CAP = new ArrayList<>();
	private static final List<ToDoubleFunction<ServerPlayer>> DRAIN_FACTOR = new ArrayList<>();
	/** A sect formation never lowers the vein drain below this share. */
	public static final double SECT_DRAIN_FLOOR = 0.7D;

	private static final List<ToDoubleFunction<ServerPlayer>> GAIN_FACTOR = new ArrayList<>();

	/** A flat multiplier on the meditation gain (product of all providers, 1 = none; 41.6.11 Gua Renungan x0.8). */
	public static void addGainFactor(ToDoubleFunction<ServerPlayer> provider) {
		GAIN_FACTOR.add(provider);
	}

	/** The product of the {@link #addGainFactor} providers. */
	public static double gainFactor(ServerPlayer player) {
		double product = 1.0D;
		for (ToDoubleFunction<ServerPlayer> provider : GAIN_FACTOR) {
			product *= provider.applyAsDouble(player);
		}
		return product;
	}

	/** A sect gathering factor at the player's seat (product of all providers, 1 = none). */
	public static void addGatheringProvider(ToDoubleFunction<ServerPlayer> provider) {
		GATHERING.add(provider);
	}

	/** The formation stack cap at the player's seat (the lowest provider value wins; no provider = no cap). */
	public static void addStackCapProvider(ToDoubleFunction<ServerPlayer> provider) {
		STACK_CAP.add(provider);
	}

	/** A vein drain factor at the player's seat (the lowest provider value wins, never below {@link #SECT_DRAIN_FLOOR}). */
	public static void addDrainFactorProvider(ToDoubleFunction<ServerPlayer> provider) {
		DRAIN_FACTOR.add(provider);
	}

	/** The sect gathering term G at the player (1 without providers or outside every field). */
	public static double sectGathering(ServerPlayer player) {
		double product = 1.0D;
		for (ToDoubleFunction<ServerPlayer> provider : GATHERING) {
			double value = provider.applyAsDouble(player);
			if (Double.isFinite(value) && value > 0.0D) {
				product *= value;
			}
		}
		return product;
	}

	/**
	 * The formation term {@code min(stackCap, seat × G)} (DESIGN 24.5); never below the seat's own multiplier, so the
	 * personal Qi formations stay exactly as they were without a sect field.
	 */
	public static double formationTerm(ServerPlayer player, double seat) {
		if (GATHERING.isEmpty()) {
			return seat;
		}
		double gathering = sectGathering(player);
		if (!(gathering > 1.0D)) {
			return seat;
		}
		double cap = Double.MAX_VALUE;
		for (ToDoubleFunction<ServerPlayer> provider : STACK_CAP) {
			double value = provider.applyAsDouble(player);
			if (Double.isFinite(value) && value > 0.0D) {
				cap = Math.min(cap, value);
			}
		}
		return Math.max(seat, Math.min(cap, seat * gathering));
	}

	private static double sectDrainFactor(ServerPlayer player) {
		double best = 1.0D;
		for (ToDoubleFunction<ServerPlayer> provider : DRAIN_FACTOR) {
			double value = provider.applyAsDouble(player);
			if (Double.isFinite(value)) {
				best = Math.min(best, value);
			}
		}
		return Math.max(SECT_DRAIN_FLOOR, best);
	}

	/** Vein drain per second of the player's meditation now (fuel, formation tier, abode and sect formation factors). */
	public static double drainPerSecond(ServerPlayer player, CultivationData data, int tier, boolean abode) {
		double drain = DRAIN_BASE * (1.0D + DRAIN_PER_REALM * data.realm().ordinal());
		if (!data.realm().atLeast(Realm.DEITY_TRANSFORMATION)) {
			drain *= DRAIN_LOW_REALM;
		}
		if (!DRAIN_FACTOR.isEmpty()) {
			drain *= sectDrainFactor(player);
		}
		if (fuelActive(player)) {
			drain *= DRAIN_FUEL;
		}
		if (tier >= 3) {
			drain *= DRAIN_HEAVENLY_FORMATION;
		} else if (tier == 2) {
			drain *= DRAIN_GREAT_FORMATION;
		}
		if (abode) {
			drain *= DRAIN_ABODE;
		}
		return drain;
	}

	/**
	 * The enlightenment chance for one minute: {@code (0.04 × min(site, 2) + 0.02 with a Dao) × 1.5 after 30 min of seclusion ×
	 * the pill boost + 0.02 (Clear Mind) + 0.01 (abode)}, at most {@link Enlightenment#MAX_CHANCE}.
	 */
	public static double enlightenmentChance(ServerPlayer player, CultivationData data, double site, int unbroken, boolean abode) {
		double chance = Enlightenment.chance(data, Math.min(ENLIGHTENMENT_SITE_CAP, site));
		if (unbroken >= SECLUSION_DEEP) {
			chance *= SECLUSION_DEEP_FACTOR;
		}
		chance *= Enlightenment.boostFactor(player);
		if (ImmortalPerks.boonActive(data, 8)) {
			chance += CLEAR_MIND_CHANCE;
		}
		if (abode) {
			chance += ABODE_CHANCE;
		}
		return Math.min(Enlightenment.MAX_CHANCE, chance);
	}

	/** Seclusion stacks of the running meditation (0 when not meditating). */
	public static int seclusionStacks(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		return session == null ? 0 : Math.min(SECLUSION_MAX, session.unbroken / SECLUSION_PERIOD);
	}

	/** Unbroken seconds of the running meditation (0 when not meditating). */
	public static int unbrokenSeconds(ServerPlayer player) {
		Session session = SESSIONS.get(player.getUUID());
		return session == null ? 0 : session.unbroken;
	}

	/** The spirit-stone XP factor while a consumed stone's period runs (1 otherwise). */
	public static double fuelFactor(ServerPlayer player) {
		Fuel fuel = FUEL.get(player.getUUID());
		if (fuel == null || fuel.end() <= player.level().getGameTime()) {
			return 1.0D;
		}
		return FUEL_FACTOR[Math.max(0, Math.min(FUEL_FACTOR.length - 1, fuel.grade()))];
	}

	/** True while a consumed spirit stone's period runs. */
	public static boolean fuelActive(ServerPlayer player) {
		Fuel fuel = FUEL.get(player.getUUID());
		return fuel != null && fuel.end() > player.level().getGameTime();
	}

	/**
	 * Sets the unbroken seconds of the running meditation ({@code uji tertutup}); false when the player is not meditating.
	 */
	public static boolean setUnbroken(ServerPlayer player, int seconds) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null) {
			return false;
		}
		session.unbroken = Math.max(0, seconds);
		session.stacks = Math.min(SECLUSION_MAX, session.unbroken / SECLUSION_PERIOD);
		putInt(player, Cultivators.get(player), "seclusion", session.stacks);
		return true;
	}

	// ================================================================ one second

	/** One second of meditation: fuel, XP, vein drain, Dao insight, seclusion, the warnings, enlightenment rolls. */
	private static void second(ServerPlayer player, CultivationData data, Session session) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		double site = session.site.multiplier();
		int tier = session.tier;
		data.setSiteMultiplier(site);
		data.setFormationTier(tier);
		session.unbroken++;
		refuel(player, data, session);

		double gain = gainPerSecond(player, data);
		if (!session.capTold) {
			double inner = innerMultiplier(player, data);
			if (inner > MULTIPLIER_CAP + 1.0E-6D) {
				// audit M4 T6: make the ×3.5 absorption limit visible once (site × personal formation × fuel above it is wasted)
				session.capTold = true;
				Msg.chat(player, Component.translatable("kultivator.kekuatan.meditation.cap", Msg.number(inner), Msg.number(MULTIPLIER_CAP),
						Msg.number(GATHER_SECT_CAP)).withColor(MUTED));
			}
		}
		double storedBefore = data.storedQi();
		double added = Progression.grantXp(player, gain, XpSource.MEDITATION);
		double stored = Math.max(0.0D, data.storedQi() - storedBefore);
		session.xpGained += Math.max(0.0D, added - stored);
		session.storedGained += stored;

		boolean abode = session.abode > 0;
		double density = SpiritVeins.density(level, pos);
		double floor = tier >= 3 ? FLOOR_HEAVENLY_FORMATION : tier == 2 ? FLOOR_GREAT_FORMATION : 0.0D;
		if (density > floor) {
			SpiritVeins.drain(level, pos, Math.min(drainPerSecond(player, data, tier, abode), density - floor));
		}
		density = SpiritVeins.density(level, pos);
		data.setVeinDensity(density);
		data.addMeditationTicks(20);
		// MEDITATE_AT feats (DESIGN 22.8.9).
		dev.kultivator.technique.spec.run.FeatTracker.onMeditate(player, 20);
		Dao dao = data.dao();
		if (dao != null) {
			for (SpiritSites.Site each : session.site.sites()) {
				if (each.dao() == dao) {
					Comprehension.add(player, dao, SITE_COMPREHENSION);
				}
			}
		}
		if (ImmortalPerks.boonActive(data, 8) && player.getHealth() < player.getMaxHealth()) {
			// Pikiran Jernih (L8): the calm mind mends the body.
			player.heal(CLEAR_MIND_HEAL);
		}
		Cultivators.dirty(player);
		session.seconds++;

		seclusion(player, data, session);
		if (session.grade == SpiritVeins.GREAT) {
			session.greatSeconds++;
			if (session.greatSeconds >= GREAT_VEIN_SECONDS) {
				Advancements.grant(player, "great_vein");
			}
		}
		if (density < THIN_VEIN && !session.warnedThin) {
			session.warnedThin = true;
			Msg.chat(player, Component.translatable("kultivator.meditation.vein_thin", percent(density)).withColor(0xFF9A7A));
			Advancements.grant(player, "depleted_vein");
		}
		if (session.seconds % 60 == 0) {
			double chance = enlightenmentChance(player, data, site, session.unbroken, abode);
			if (!Enlightenment.isActive(player) && player.getRandom().nextDouble() < chance) {
				Enlightenment.trigger(player);
			}
		}
	}

	/** The seclusion stacks grow every 5 minutes; the deep mark at 30 minutes; the hour advancement. */
	private static void seclusion(ServerPlayer player, CultivationData data, Session session) {
		int stacks = Math.min(SECLUSION_MAX, session.unbroken / SECLUSION_PERIOD);
		if (stacks > session.stacks) {
			session.stacks = stacks;
			putInt(player, data, "seclusion", stacks);
			Msg.chat(player, Component.translatable("kultivator.kultivasi.seclusion.stack", percentBonus(stacks), stacks, SECLUSION_MAX)
					.withColor(VIOLET));
			player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
					SoundSource.PLAYERS, 0.7F, 0.6F + 0.15F * stacks);
		}
		if (session.unbroken >= SECLUSION_DEEP && !session.deepTold) {
			session.deepTold = true;
			Msg.chat(player, Component.translatable("kultivator.kultivasi.seclusion.deep",
					Msg.number(SECLUSION_DEEP_FACTOR)).withColor(GOLD));
		}
		if (session.unbroken >= CLOSED_DOOR_HOUR) {
			Advancements.grant(player, "closed_door_hour");
		}
	}

	/** Consumes one spirit stone from the offhand when no consumed stone's period runs (DESIGN 19.3.2, 19.3.5). */
	private static void refuel(ServerPlayer player, CultivationData data, Session session) {
		UUID id = player.getUUID();
		long now = player.level().getGameTime();
		Fuel fuel = FUEL.get(id);
		if (fuel != null && fuel.end() > now) {
			putInt(player, data, "fuel", fuel.grade());
			putLong(player, data, "fuel_end", fuel.end());
			return;
		}
		ItemStack offhand = player.getOffhandItem();
		int grade = SpiritStones.isStone(offhand) ? SpiritStones.grade(offhand) : -1;
		if (grade < 0 || grade >= FUEL_SECONDS.length) {
			if (fuel != null) {
				FUEL.remove(id);
				removeKey(player, data, "fuel");
				removeKey(player, data, "fuel_end");
				Msg.actionBar(player, Component.translatable("kultivator.kultivasi.fuel.out").withColor(MUTED));
			}
			return;
		}
		if (grade >= SpiritStones.HIGH && data.barFull() && data.rank().nextIsBreakthrough() && !data.deviating()) {
			// High and Supreme stones at a bottleneck are kept for Batu Roh di Hambatan (StoneBottleneck), never burned as fuel.
			if (fuel != null) {
				FUEL.remove(id);
				removeKey(player, data, "fuel");
				removeKey(player, data, "fuel_end");
			}
			if (!session.reserveTold) {
				session.reserveTold = true;
				Msg.chat(player, Component.translatable("kultivator.kultivasi.fuel.reserved", offhand.getHoverName().copy()).withColor(JADE));
			}
			return;
		}
		Component stoneName = offhand.getHoverName().copy();
		if (!player.hasInfiniteMaterials()) {
			offhand.shrink(1);
		}
		long end = now + FUEL_SECONDS[grade] * 20L;
		FUEL.put(id, new Fuel(grade, end));
		putInt(player, data, "fuel", grade);
		putLong(player, data, "fuel_end", end);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY() + 0.5D, player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS,
				0.6F, 1.2F + 0.1F * grade);
		Fx.send(level, player.position().add(0.0D, 0.6D, 0.0D), FxType.TALISMAN, TalismanFxIds.FUEL, grade, 0.0F);
		if (!session.fuelTold) {
			session.fuelTold = true;
			Msg.chat(player, Component.translatable("kultivator.kultivasi.fuel.start", stoneName, Msg.number(FUEL_FACTOR[grade]),
					FUEL_SECONDS[grade]).withColor(JADE));
		}
	}

	/** The line above the hotbar: site, qi density, vein grade, formation and seclusion, or the bottleneck hint. */
	private static void actionBar(ServerPlayer player, CultivationData data, Session session) {
		Rank rank = data.rank();
		if (data.barFull() && rank.nextIsBreakthrough()) {
			if (rank.realm().isMortal() && !data.rootTested()) {
				Msg.actionBar(player, Component.translatable("kultivator.progression.bottleneck_mortal_bar").withStyle(ChatFormatting.LIGHT_PURPLE));
				return;
			}
			if (!data.deviating() && !fuelActive(player)) {
				Msg.actionBar(player, Component.translatable("kultivator.meditation.bar.bottleneck",
						Component.keybind("key.kultivator.meditate").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
						Component.keybind("key.kultivator.info").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)).withColor(GOLD));
				return;
			}
			// During a deviation no breakthrough is possible, and fuel now flows into Stored Qi: show the normal line.
		}
		double site = session.site.multiplier();
		double density = data.veinDensity();
		if (density < THIN_VEIN && (session.ticks / 40) % 2 == 0) {
			// Balance change (flow audit): every other line, a plain hint while the vein under the player is nearly exhausted.
			Msg.actionBar(player, Component.translatable("kultivator.meditation.bar.vein_exhausted", percent(density)).withColor(0xFF9A7A));
			return;
		}
		int densityColor = density >= 0.6D ? 0x7FD8FF : density >= THIN_VEIN ? GOLD : 0xFF6A5A;
		MutableComponent line = Component.literal("☯ ").withColor(JADE)
				.append(Component.translatable("kultivator.meditation.bar.site", multiplier(site)).withColor(site > 1.0001D ? GOLD : MUTED))
				.append(Component.literal("  ·  ").withColor(MUTED))
				.append(Component.translatable("kultivator.meditation.bar.density", percent(density)).withColor(densityColor));
		if (session.grade != SpiritVeins.NORMAL) {
			line.append(Component.literal(" · ").withColor(MUTED))
					.append(SpiritVeins.gradeName(session.grade).copy().withColor(SpiritVeins.gradeColor(session.grade)));
		}
		line.append(Component.literal("  ·  ").withColor(MUTED))
				.append(session.tier > 0
						? QiFormations.name(session.tier).copy().withColor(QiFormations.color(session.tier))
						: Component.translatable("kultivator.meditation.bar.no_formation").withColor(MUTED));
		if (!GATHERING.isEmpty()) {
			// Phase 7c (DESIGN 24.7.4): the sect formation term, when a field lifts it.
			double sect = sectGathering(player);
			if (sect > 1.0001D) {
				line.append(Component.literal(" · ").withColor(MUTED))
						.append(Component.translatable("kultivator.spine7c.meditation.sect", multiplier(sect)).withColor(VIOLET));
			}
		}
		if (session.stacks > 0) {
			line.append(Component.literal("  ·  ").withColor(MUTED))
					.append(Component.translatable("kultivator.kultivasi.seclusion.bar", percentBonus(session.stacks)).withColor(VIOLET));
		}
		Msg.actionBar(player, line);
	}

	// ================================================================ start / stop

	public static void toggle(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!data.meditating()) {
			start(player);
			return;
		}
		// At a full bottleneck the meditate key starts the breakthrough instead (DESIGN section 3). The breakthrough
		// reads the site while we still sit, then stops the meditation itself; it tells its own story, so stay quiet.
		// An untested Mortal or a deviating cultivator would only be refused: then the key simply stops meditating.
		if (data.barFull() && data.rank().nextIsBreakthrough() && !data.breakingThrough() && !data.deviating()
				&& !(data.realm().isMortal() && !data.rootTested())) {
			boolean started;
			quietStop = true;
			try {
				started = Breakthrough.attempt(player);
			} finally {
				quietStop = false;
			}
			if (started) {
				if (isMeditating(player)) {
					end(player, null);
				}
				return;
			}
		}
		stop(player);
	}

	/** True when meditation started; tells the player why not otherwise. */
	public static boolean start(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (data.meditating()) {
			return true;
		}
		String reason = reasonNotToStart(player, data);
		if (reason != null) {
			Msg.chat(player, Component.translatable(reason).withColor(0xFF9A7A));
			return false;
		}
		Session session = newSession(player, data);
		SESSIONS.put(player.getUUID(), session);
		data.setMeditating(true);
		evaluate(player, data, session, true);
		data.setVeinDensity(SpiritVeins.density(player.level(), player.blockPosition()));
		Cultivators.syncNow(player);

		Msg.chat(player, Component.translatable("kultivator.meditation.start").withColor(JADE));
		if (session.site.any()) {
			Msg.chat(player, Component.translatable("kultivator.meditation.start_site",
					ComponentUtils.formatList(session.site.names(), Component.literal(", ")),
					multiplier(session.site.multiplier())).withColor(GOLD));
		}
		if (session.tier > 0) {
			announceFormation(player, session.tier);
		}
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.7F);
		actionBar(player, data, session);
		Advancements.grant(player, "first_meditation");
		return true;
	}

	/** Ends the meditation; quiet when a breakthrough is starting (it sets breakingThrough first and tells its own story). */
	public static void stop(ServerPlayer player) {
		if (isMeditating(player)) {
			boolean quiet = quietStop || Cultivators.get(player).breakingThrough();
			end(player, quiet ? null : "kultivator.meditation.stop");
		}
	}

	public static boolean isMeditating(ServerPlayer player) {
		return Cultivators.get(player).meditating();
	}

	public static void onLeave(ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
		FUEL.remove(player.getUUID());
		BREAKS.remove(player.getUUID());
		Enlightenment.forgetAll(player);
		SpiritSites.forget(player);
		CultivationData data = Cultivators.get(player);
		data.setMeditating(false);
		resetSessionValues(data);
	}

	/** Ends the meditation; {@code reasonKey} is the chat line telling why, null for none. */
	private static void end(ServerPlayer player, @Nullable String reasonKey) {
		Session session = SESSIONS.remove(player.getUUID());
		CultivationData data = Cultivators.get(player);
		boolean was = data.meditating();
		data.setMeditating(false);
		resetSessionValues(data);
		clearHud(player, data);
		Cultivators.syncNow(player);
		if (was && reasonKey != null) {
			Msg.chat(player, Component.translatable(reasonKey).withColor(MUTED));
		}
		if (session != null && player.isAlive()) {
			if (session.abode > 0 && session.unbroken > 0) {
				BREAKS.put(player.getUUID(), new Break(player.level().getGameTime(), session.unbroken));
			}
			if (session.seconds >= SUMMARY_SECONDS) {
				summary(player, data, session);
			}
		}
	}

	/** "Meditasi 32 menit: +4.210 kultivasi · 2 pencerahan · kepadatan 180% → 95%". */
	private static void summary(ServerPlayer player, CultivationData data, Session session) {
		List<Component> parts = new ArrayList<>();
		if (session.xpGained >= 0.5D) {
			parts.add(Component.translatable("kultivator.kultivasi.summary.xp", Msg.number(session.xpGained)).withColor(JADE));
		}
		if (session.storedGained >= 0.5D) {
			parts.add(Component.translatable("kultivator.kultivasi.summary.stored", Msg.number(session.storedGained)).withColor(VIOLET));
		}
		int enlightenments = Math.max(0, data.enlightenments() - session.startEnlightenments);
		if (enlightenments > 0) {
			parts.add(Component.translatable("kultivator.kultivasi.summary.enlightenments", enlightenments).withColor(GOLD));
		}
		double end = SpiritVeins.density(player.level(), player.blockPosition());
		parts.add(Component.translatable("kultivator.kultivasi.summary.density", percent(session.startDensity), percent(end)).withColor(0x7FD8FF));
		Component duration = session.seconds >= 3600
				? Component.translatable("kultivator.kultivasi.summary.hours", session.seconds / 3600, (session.seconds % 3600) / 60)
				: Component.translatable("kultivator.kultivasi.summary.minutes", Math.max(1, session.seconds / 60));
		Msg.chat(player, Component.translatable("kultivator.kultivasi.summary", duration,
				ComponentUtils.formatList(parts, Component.literal(" · ").withColor(MUTED))).withColor(MUTED));
	}

	private static void resetSessionValues(CultivationData data) {
		data.setSiteMultiplier(1.0D);
		data.setFormationTier(0);
		data.setVeinDensity(1.0D);
	}

	/** Removes the meditation HUD keys of {@code sessionExtra} (DESIGN 19.9). */
	private static void clearHud(ServerPlayer player, CultivationData data) {
		for (String key : new String[]{"vein_grade", "seclusion", "fuel", "fuel_end", "abode"}) {
			removeKey(player, data, key);
		}
	}

	/** The translation key of why the player cannot sit down now, or null when they can. */
	private static @Nullable String reasonNotToStart(ServerPlayer player, CultivationData data) {
		if (!player.isAlive() || player.isSpectator()) {
			return "kultivator.meditation.cannot.now";
		}
		if (data.breakingThrough()) {
			return "kultivator.meditation.cannot.breakthrough";
		}
		if (player.isSleeping()) {
			return "kultivator.meditation.cannot.sleeping";
		}
		if (player.isPassenger()) {
			return "kultivator.meditation.cannot.riding";
		}
		if (player.isFallFlying() || player.getAbilities().flying) {
			return "kultivator.meditation.cannot.flying";
		}
		if (player.isSwimming() || player.isInWater()) {
			return "kultivator.meditation.cannot.water";
		}
		if (!player.onGround()) {
			return "kultivator.meditation.cannot.ground";
		}
		return null;
	}

	/** Something that makes sitting impossible happened after sitting down. */
	private static boolean cannotSit(ServerPlayer player) {
		return player.isPassenger() || player.isFallFlying() || player.getAbilities().flying || player.isSwimming()
				|| player.isInWater() || player.isSleeping() || player.isSpectator();
	}

	// ================================================================ session keys (HUD, DESIGN 19.9)

	private static void putInt(ServerPlayer player, CultivationData data, String key, int value) {
		CompoundTag tag = data.sessionExtra();
		if (!tag.contains(key) || tag.getIntOr(key, Integer.MIN_VALUE) != value) {
			tag.putInt(key, value);
			Cultivators.dirty(player);
		}
	}

	private static void putLong(ServerPlayer player, CultivationData data, String key, long value) {
		CompoundTag tag = data.sessionExtra();
		if (!tag.contains(key) || tag.getLongOr(key, Long.MIN_VALUE) != value) {
			tag.putLong(key, value);
			Cultivators.dirty(player);
		}
	}

	private static void removeKey(ServerPlayer player, CultivationData data, String key) {
		if (data.sessionExtra().contains(key)) {
			data.sessionExtra().remove(key);
			Cultivators.dirty(player);
		}
	}

	// ================================================================ commands

	/**
	 * Adds {@code meditate} to /cultivate, and {@code enlighten}, {@code site}, {@code vein [percent 0-200]} and
	 * {@code vein grade} to debug.
	 */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("meditate").executes(ctx -> {
			toggle(ctx.getSource().getPlayerOrException());
			return 1;
		}));
		debug.then(Commands.literal("enlighten").executes(ctx -> {
			Enlightenment.trigger(ctx.getSource().getPlayerOrException());
			return 1;
		}));
		debug.then(Commands.literal("site").executes(Meditation::showSite));
		debug.then(Commands.literal("vein")
				.executes(Meditation::showSite)
				.then(Commands.literal("grade").executes(Meditation::showGrade))
				.then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0D, 200.0D))
						.executes(ctx -> setDensity(ctx.getSource(), ctx.getSource().getPlayerOrException(),
								DoubleArgumentType.getDouble(ctx, "percent")))));
	}

	/**
	 * {@code uji urat tingkat <agung|kaya|biasa|tipis|reset>}: overrides the grade of the 4x4-chunk region the player stands
	 * in (saved with the world; meditation, compass, {@code uji urat cari}, HUD and formations all read it), or restores the
	 * natural grade with null. The region starts full at its new cap.
	 */
	public static int setVeinGrade(CommandSourceStack source, ServerPlayer player, @org.jetbrains.annotations.Nullable Integer grade) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		SpiritVeins.setOverride(level, pos, grade);
		int now = SpiritVeins.grade(level, pos);
		double density = SpiritVeins.density(level, pos);
		if (isMeditating(player)) {
			Cultivators.get(player).setVeinDensity(density);
			Cultivators.dirty(player);
		}
		Component name = SpiritVeins.gradeName(now).copy().withColor(SpiritVeins.gradeColor(now));
		String cap = percentText(SpiritVeins.cap(level, pos));
		source.sendSuccess(() -> Msg.prefixed(grade == null
				? Component.translatable("kultivator.kultivasi.test.vein_grade_reset", name, cap)
				: Component.translatable("kultivator.kultivasi.test.vein_grade_set", name, cap, SpiritVeins.regionX(pos), SpiritVeins.regionZ(pos))), false);
		return 1;
	}

	/** Sets the density of the region the player stands in (debug vein, uji urat); clamped to the region's cap. */
	public static int setDensity(CommandSourceStack source, ServerPlayer player, double percent) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		SpiritVeins.set(level, pos, percent / 100.0D);
		double now = SpiritVeins.density(level, pos);
		if (isMeditating(player)) {
			Cultivators.get(player).setVeinDensity(now);
			Cultivators.dirty(player);
		}
		source.sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kultivasi.debug.vein_set", percent(now),
				SpiritVeins.gradeName(SpiritVeins.grade(level, pos)), percentText(SpiritVeins.cap(level, pos)))), false);
		return 1;
	}

	private static int showGrade(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		int grade = SpiritVeins.grade(level, pos);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.kultivasi.debug.vein_grade",
				SpiritVeins.gradeName(grade).copy().withColor(SpiritVeins.gradeColor(grade)), percentText(SpiritVeins.cap(level, pos)),
				percent(SpiritVeins.density(level, pos)), SpiritVeins.regionX(pos), SpiritVeins.regionZ(pos))), false);
		return 1;
	}

	private static int showSite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		SpiritSites.Result site = SpiritSites.evaluate(player);
		int tier = QiFormations.tier(player.level(), player.blockPosition().below());
		double density = SpiritVeins.density(player.level(), player.blockPosition());
		List<Component> names = site.names();
		Component siteNames = names.isEmpty()
				? Component.translatable("kultivator.meditation.debug.no_site")
				: ComponentUtils.formatList(names, Component.literal(", "));
		Component formation = tier > 0 ? QiFormations.name(tier) : Component.translatable("kultivator.meditation.bar.no_formation");
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.meditation.debug.site",
				multiplier(site.multiplier()), siteNames, formation, percent(density))), false);
		return 1;
	}

	// ================================================================ formatting

	/** "1.5", "1.95", "4": a multiplier without trailing zeros. */
	public static String multiplier(double value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}

	/** Density 0..2 as a whole percentage (values above 100% are shown, DESIGN 19.3.5). */
	public static String percent(double fraction) {
		return String.valueOf(Math.round(Math.max(0.0D, Math.min(SpiritVeins.MAX_CAP, fraction)) * 100.0D));
	}

	/** "180%". */
	private static String percentText(double fraction) {
		return percent(fraction) + "%";
	}

	/** "+15%" for 3 seclusion stacks. */
	private static String percentBonus(int stacks) {
		return "+" + Math.round(stacks * SECLUSION_BONUS * 100.0D) + "%";
	}

	private Meditation() {
	}
}
