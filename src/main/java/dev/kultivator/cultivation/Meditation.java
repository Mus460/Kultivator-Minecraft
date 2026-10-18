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
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Meditation (DESIGN section 4): the player sits cross-legged and gathers qi. Every second it gives XP scaled by the
 * spiritual site, the qi gathering formation and the spirit vein density, drains the vein a little, and every minute
 * may bring an enlightenment. Qi regeneration itself is boosted by {@code Qi.tick} (it reads {@code meditating()}).
 * Moving, getting hurt, attacking, using an item or block, changing dimension, dying or starting a breakthrough ends it.
 */
public final class Meditation {

	/** How far the player may drift from where they sat down before the meditation breaks. */
	private static final double MAX_HORIZONTAL = 0.5D;
	private static final double MAX_VERTICAL = 1.0D;
	/** Below this density the player is told the vein is thin. */
	private static final double THIN_VEIN = 0.2D;
	/** Comprehension per second for each site that matches the chosen Dao. */
	private static final double SITE_COMPREHENSION = 0.004D;

	private static final int JADE = 0x8FE3C8;
	private static final int GOLD = 0xFFD54A;
	private static final int MUTED = 0xA8B5B0;

	/** Server-side state of one meditation, from sitting down to standing up. */
	private static final class Session {
		final Vec3 start;
		final ResourceKey<Level> dimension;
		int ticks;
		int seconds;
		SpiritSites.Result site = SpiritSites.Result.NONE;
		int tier;
		boolean warnedThin;

		Session(Vec3 start, ResourceKey<Level> dimension) {
			this.start = start;
			this.dimension = dimension;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
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
			}
			return;
		}
		if (session == null) {
			// Someone else switched the flag on (or a reload): start a session where the player is.
			session = new Session(player.position(), player.level().dimension());
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

	/** Re-checks spiritual sites and the formation (on start and every 5 s), grants their advancements and shows the lines. */
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

	/** One second of meditation: XP, vein drain, Dao insight from the site, the thin-vein warning, enlightenment rolls. */
	private static void second(ServerPlayer player, CultivationData data, Session session) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		Realm realm = data.realm();
		double site = session.site.multiplier();
		int tier = session.tier;
		double density = SpiritVeins.density(level, pos);
		double xp = (0.6D + 0.25D * realm.ordinal()) * site * QiFormations.multiplier(tier) * Math.max(0.15D, density);
		Progression.addXp(player, xp * dev.kultivator.sect.Sects.meditationBonus(player), XpSource.MEDITATION);
		SpiritVeins.drain(level, pos, 0.0015D * (1.0D + realm.ordinal() * 0.25D));
		density = SpiritVeins.density(level, pos);

		data.setSiteMultiplier(site);
		data.setFormationTier(tier);
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
		Cultivators.dirty(player);
		session.seconds++;

		if (density < THIN_VEIN && !session.warnedThin) {
			session.warnedThin = true;
			Msg.chat(player, Component.translatable("kultivator.meditation.vein_thin", percent(density)).withColor(0xFF9A7A));
			Advancements.grant(player, "depleted_vein");
		}
		if (session.seconds % 60 == 0) {
			double chance = Enlightenment.chance(data, site);
			if (!Enlightenment.isActive(player) && player.getRandom().nextDouble() < chance) {
				Enlightenment.trigger(player);
			}
		}
	}

	/** The line above the hotbar: site, qi density and formation, or the bottleneck hint when the bar is full. */
	private static void actionBar(ServerPlayer player, CultivationData data, Session session) {
		Rank rank = data.rank();
		if (data.barFull() && rank.nextIsBreakthrough()) {
			if (rank.realm().isMortal() && !data.rootTested()) {
				Msg.actionBar(player, Component.translatable("kultivator.progression.bottleneck_mortal_bar").withStyle(ChatFormatting.LIGHT_PURPLE));
				return;
			}
			if (!data.deviating()) {
				Msg.actionBar(player, Component.translatable("kultivator.meditation.bar.bottleneck",
						Component.keybind("key.kultivator.meditate").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
						Component.keybind("key.kultivator.info").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)).withColor(GOLD));
				return;
			}
			// During a deviation no breakthrough is possible: show the normal line instead.
		}
		double site = session.site.multiplier();
		double density = data.veinDensity();
		int densityColor = density >= 0.6D ? 0x7FD8FF : density >= THIN_VEIN ? GOLD : 0xFF6A5A;
		MutableComponent line = Component.literal("☯ ").withColor(JADE)
				.append(Component.translatable("kultivator.meditation.bar.site", multiplier(site)).withColor(site > 1.0001D ? GOLD : MUTED))
				.append(Component.literal("  ·  ").withColor(MUTED))
				.append(Component.translatable("kultivator.meditation.bar.density", percent(density)).withColor(densityColor))
				.append(Component.literal("  ·  ").withColor(MUTED))
				.append(session.tier > 0
						? QiFormations.name(session.tier).copy().withColor(QiFormations.color(session.tier))
						: Component.translatable("kultivator.meditation.bar.no_formation").withColor(MUTED));
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
		Session session = new Session(player.position(), player.level().dimension());
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
		Enlightenment.forget(player);
		SpiritSites.forget(player);
		CultivationData data = Cultivators.get(player);
		data.setMeditating(false);
		resetSessionValues(data);
	}

	/** Ends the meditation; {@code reasonKey} is the chat line telling why, null for none. */
	private static void end(ServerPlayer player, @Nullable String reasonKey) {
		SESSIONS.remove(player.getUUID());
		CultivationData data = Cultivators.get(player);
		boolean was = data.meditating();
		data.setMeditating(false);
		resetSessionValues(data);
		Cultivators.syncNow(player);
		if (was && reasonKey != null) {
			Msg.chat(player, Component.translatable(reasonKey).withColor(MUTED));
		}
	}

	private static void resetSessionValues(CultivationData data) {
		data.setSiteMultiplier(1.0D);
		data.setFormationTier(0);
		data.setVeinDensity(1.0D);
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

	// ================================================================ commands

	/** Adds {@code meditate} to /cultivate, and {@code enlighten}, {@code site}, {@code vein [percent]} to debug. */
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
				.then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0D, 100.0D))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							double value = DoubleArgumentType.getDouble(ctx, "percent") / 100.0D;
							SpiritVeins.set(player.level(), player.blockPosition(), value);
							if (isMeditating(player)) {
								Cultivators.get(player).setVeinDensity(SpiritVeins.density(player.level(), player.blockPosition()));
								Cultivators.dirty(player);
							}
							ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.meditation.debug.vein",
									percent(value))), false);
							return 1;
						})));
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

	/** Density 0..1 as a whole percentage. */
	public static String percent(double fraction) {
		return String.valueOf(Math.round(Math.max(0.0D, Math.min(1.0D, fraction)) * 100.0D));
	}

	private Meditation() {
	}
}
