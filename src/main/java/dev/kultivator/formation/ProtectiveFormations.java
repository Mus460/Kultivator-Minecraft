package dev.kultivator.formation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Protective formations (Formasi Pelindung, DESIGN sections 10 and 16), switched off by default.
 * <p>
 * A Lodestone with Crying Obsidian at (+-4,0,0) and (0,0,+-4) on the same level is a formation pattern (read-only
 * detection, {@link FormationPattern}). It does nothing until its owner sneak-uses the lodestone with an Amethyst Shard:
 * that registers it (owner = that player), adds 10 minutes of power (at most 2 hours) and switches it on. Sneak-use with
 * an empty main hand switches it on or off (the stored power is kept); anyone else is told the owner and the power left.
 * While on, powered and loaded, monsters (and mobs hunting a player inside) within {@link #RADIUS} blocks of the
 * lodestone are pushed outwards and take 1 magic damage per second. Never touched: pets and spirit beasts, villagers,
 * the Wither and the Ender Dragon (they break blocks when hurt or moved), a heart-bound creaking (its heart grows resin);
 * silverfish are pushed but never hurt (a hurt silverfish wakes infested blocks), ravagers are hurt but never pushed (a
 * pushed ravager tears through leaves), and every pushed mob gets a portal cooldown (portal travel could build an exit
 * portal). Data in {@link FormationData} per dimension.
 * <p>
 * Effects ({@code FxType.PROTECTIVE_FORMATION}, drawn by the client's FormationFx): pos = lodestone centre, f = radius;
 * a = 1 dome refresh every 2 s (b = 1 just switched on, b = 2 charged with a shard), a = 0 switched off, a = 2 missing
 * pillar marker / a = 3 present pillar marker (pos = the pillar block), a = 4 a monster driven back (pos = the monster,
 * target = the lodestone centre). Nothing here ever places, breaks or changes a block.
 */
public final class ProtectiveFormations {

	/** Radius of the protected sphere around the lodestone centre. */
	public static final double RADIUS = 16.0D;
	/** Power one Amethyst Shard adds: 10 minutes. */
	public static final int TICKS_PER_SHARD = 20 * 60 * 10;
	/** Most power a formation can hold: 2 hours. */
	public static final int MAX_POWER = 20 * 60 * 120;

	private static final int PULSE = 10;
	private static final int CHECK_INTERVAL = 100;
	private static final int FX_INTERVAL = 40;
	private static final double FX_RANGE = 64.0D;
	private static final int REPELS_FOR_ADVANCEMENT = 20;
	/** A monster pushed again within this many ticks is the same repel. */
	private static final long REPEL_MEMORY = 200L;
	private static final int MAX_RIPPLES_PER_PULSE = 3;
	private static final double LOOK_RANGE = 10.0D;
	/** Someone's heart demon trial is a duel the formation stays out of. */
	private static final Identifier HEART_DEMON = Kultivator.id("heart_demon");

	public static void init() {
		UseBlockCallback.EVENT.register(ProtectiveFormations::onUseBlock);
		ServerTickEvents.END_SERVER_TICK.register(ProtectiveFormations::tickServer);
	}

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("formation")
				.then(Commands.literal("list").executes(ProtectiveFormations::listCommand)));
		debug.then(Commands.literal("formation")
				.then(Commands.literal("power")
						.then(Commands.argument("minutes", IntegerArgumentType.integer(0, MAX_POWER / 1200))
								.executes(ProtectiveFormations::powerCommand)))
				.then(Commands.literal("clear").executes(ProtectiveFormations::clearCommand)));
	}

	/** True when {@code pos} lies inside a switched-on, powered formation of {@code level} (for other modules). */
	public static boolean insideActiveFormation(ServerLevel level, Vec3 pos) {
		FormationData data = FormationData.get(level);
		if (data.isEmpty()) {
			return false;
		}
		for (Formation formation : data.all()) {
			if (formation.active() && formation.power() > 0 && Vec3.atCenterOf(formation.pos()).distanceToSqr(pos) <= RADIUS * RADIUS) {
				return true;
			}
		}
		return false;
	}

	// ================================================================ using the lodestone

	/**
	 * Sneak + use on a Lodestone with an Amethyst Shard in that hand, or with an empty main hand (main hand pass only,
	 * and only when at least one pillar stands; the off hand may hold anything, a shield or a torch). Both sides answer SUCCESS for exactly these cases, so the use packet is
	 * sent once, the arm swings and vanilla's own use never runs; every other use (a compass, not sneaking, another
	 * block) passes untouched.
	 */
	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (player.isSpectator() || !player.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!FormationPattern.isLodestone(level, pos)) {
			return InteractionResult.PASS;
		}
		boolean shard = player.getItemInHand(hand).is(Items.AMETHYST_SHARD);
		// An empty hand only counts next to at least one pillar, so a plain lodestone keeps its plain behaviour.
		boolean emptyHands = hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
				&& FormationPattern.missing(level, pos).size() < FormationPattern.pillars(pos).size();
		if (!shard && !emptyHands) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		try {
			return shard ? useShard(serverPlayer, serverLevel, pos.immutable(), hand) : useEmptyHand(serverPlayer, serverLevel, pos.immutable());
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Protective formation interaction failed at {}", pos, exception);
			return InteractionResult.SUCCESS;
		}
	}

	private static InteractionResult useShard(ServerPlayer player, ServerLevel level, BlockPos pos, InteractionHand hand) {
		if (!KultivatorConfig.get().protectiveFormations) {
			Msg.chat(player, Component.translatable("kultivator.formations.disabled").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		FormationData data = FormationData.get(level);
		Formation formation = data.get(pos);
		if (formation != null && !formation.isOwner(player)) {
			Msg.chat(player, Component.translatable("kultivator.formations.owned_by", ownerName(formation)).withStyle(ChatFormatting.YELLOW));
			return InteractionResult.SUCCESS;
		}
		List<BlockPos> missing = FormationPattern.missing(level, pos);
		if (!missing.isEmpty()) {
			showIncomplete(player, pos, missing);
			return InteractionResult.SUCCESS;
		}
		boolean created = formation == null;
		if (created) {
			formation = new Formation(pos, player.getUUID(), player.getPlainTextName(), false, 0, 0);
			data.put(formation);
		}
		formation.setOwnerName(player.getPlainTextName());
		boolean wasActive = formation.active();
		// A shard is only used when at least half of it fits below the 2 hour cap.
		boolean charged = MAX_POWER - formation.power() >= TICKS_PER_SHARD / 2;
		if (charged) {
			ItemStack stack = player.getItemInHand(hand);
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
			formation.setPower(formation.power() + TICKS_PER_SHARD);
		}
		formation.setActive(true);
		data.setDirty();

		Vec3 centre = Vec3.atCenterOf(pos);
		if (!wasActive) {
			sound(level, centre, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
			sound(level, centre, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.9F);
			sendDome(level, formation, 1, 1);
			Msg.chat(player, Component.translatable("kultivator.formations.awakened", duration(formation.power())).withStyle(ChatFormatting.GREEN));
		} else if (charged) {
			sound(level, centre, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F);
			sound(level, centre, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 1.4F);
			sendDome(level, formation, 1, 2);
			Msg.chat(player, Component.translatable("kultivator.formations.charged", duration(formation.power())).withStyle(ChatFormatting.GREEN));
		} else {
			Msg.chat(player, Component.translatable("kultivator.formations.full", duration(formation.power())).withStyle(ChatFormatting.YELLOW));
		}
		if (created) {
			Advancements.grant(player, "formation_built");
		}
		if (formation.power() >= MAX_POWER) {
			Advancements.grant(player, "formation_powered");
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult useEmptyHand(ServerPlayer player, ServerLevel level, BlockPos pos) {
		FormationData data = FormationData.get(level);
		Formation formation = data.get(pos);
		if (formation == null) {
			List<BlockPos> missing = FormationPattern.missing(level, pos);
			if (missing.size() == FormationPattern.pillars(pos).size()) {
				// Just a lodestone: nothing of ours to talk about.
				return InteractionResult.PASS;
			}
			if (missing.isEmpty()) {
				Msg.chat(player, Component.translatable("kultivator.formations.not_awakened").withStyle(ChatFormatting.AQUA));
				markPillars(player, pos, missing);
			} else {
				showIncomplete(player, pos, missing);
			}
			return InteractionResult.SUCCESS;
		}
		if (!formation.isOwner(player)) {
			Msg.chat(player, Component.translatable("kultivator.formations.status", ownerName(formation), state(formation.active()),
					duration(formation.power())).withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		formation.setOwnerName(player.getPlainTextName());
		data.setDirty();
		if (formation.active()) {
			switchOff(level, data, formation, null);
			Msg.chat(player, Component.translatable("kultivator.formations.off", duration(formation.power())).withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		if (!KultivatorConfig.get().protectiveFormations) {
			Msg.chat(player, Component.translatable("kultivator.formations.disabled").withStyle(ChatFormatting.RED));
			return InteractionResult.SUCCESS;
		}
		List<BlockPos> missing = FormationPattern.missing(level, pos);
		if (!missing.isEmpty()) {
			showIncomplete(player, pos, missing);
			return InteractionResult.SUCCESS;
		}
		if (formation.power() <= 0) {
			Msg.chat(player, Component.translatable("kultivator.formations.no_power").withStyle(ChatFormatting.YELLOW));
			return InteractionResult.SUCCESS;
		}
		formation.setActive(true);
		data.setDirty();
		Vec3 centre = Vec3.atCenterOf(pos);
		sound(level, centre, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
		sendDome(level, formation, 1, 1);
		Msg.chat(player, Component.translatable("kultivator.formations.on", duration(formation.power())).withStyle(ChatFormatting.GREEN));
		return InteractionResult.SUCCESS;
	}

	/** Tells the player which pillars are missing and marks all four spots (red = missing, jade = in place). */
	private static void showIncomplete(ServerPlayer player, BlockPos lodestone, List<BlockPos> missing) {
		StringBuilder spots = new StringBuilder();
		for (BlockPos spot : missing) {
			if (!spots.isEmpty()) {
				spots.append(", ");
			}
			spots.append(spot.getX()).append(' ').append(spot.getY()).append(' ').append(spot.getZ());
		}
		Msg.chat(player, Component.translatable("kultivator.formations.incomplete", missing.size(),
				Component.literal(spots.toString()).withStyle(ChatFormatting.RED)).withStyle(ChatFormatting.YELLOW));
		markPillars(player, lodestone, missing);
	}

	private static void markPillars(ServerPlayer player, BlockPos lodestone, List<BlockPos> missing) {
		Vec3 centre = Vec3.atCenterOf(lodestone);
		for (BlockPos pillar : FormationPattern.pillars(lodestone)) {
			Fx.sendTo(player, Vec3.atCenterOf(pillar), centre, FxType.PROTECTIVE_FORMATION, missing.contains(pillar) ? 2 : 3, 0, 1.0F);
		}
	}

	// ================================================================ ticking

	private static void tickServer(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % PULSE != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			FormationData data = FormationData.get(level);
			if (data.isEmpty()) {
				continue;
			}
			for (Formation formation : List.copyOf(data.all())) {
				try {
					tickFormation(level, data, formation, tick);
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Protective formation at {} failed to tick", formation.pos(), exception);
				}
			}
		}
	}

	private static void tickFormation(ServerLevel level, FormationData data, Formation formation, int tick) {
		BlockPos pos = formation.pos();
		if (!level.isLoaded(pos) || !level.isPositionEntityTicking(pos)) {
			// Unloaded formations sleep: no power is used, nothing is checked.
			return;
		}
		if (tick % CHECK_INTERVAL == 0) {
			if (!FormationPattern.isLodestone(level, pos)) {
				data.remove(pos);
				if (formation.active()) {
					sendDome(level, formation, 0, 0);
				}
				notifyOwner(level, formation, "kultivator.formations.destroyed");
				return;
			}
			formation.forgetOldPushes(level.getGameTime(), REPEL_MEMORY);
			if (formation.repels() >= REPELS_FOR_ADVANCEMENT) {
				grantOwner(level, formation, "formation_repelled");
			}
			if (formation.active() && FormationPattern.broken(level, pos)) {
				switchOff(level, data, formation, "kultivator.formations.broken");
				return;
			}
		}
		if (!formation.active()) {
			return;
		}
		if (!KultivatorConfig.get().protectiveFormations) {
			switchOff(level, data, formation, "kultivator.formations.server_disabled");
			return;
		}
		if (formation.power() <= 0) {
			switchOff(level, data, formation, "kultivator.formations.power_out");
			return;
		}
		repel(level, data, formation, tick % 20 == 0);
		formation.setPower(formation.power() - PULSE);
		data.setDirty();
		if (formation.power() <= 0) {
			switchOff(level, data, formation, "kultivator.formations.power_out");
			return;
		}
		if (tick % FX_INTERVAL == 0) {
			sendDome(level, formation, 1, 0);
		}
	}

	/** Pushes every intruder outwards (stronger near the centre); every second also 1 magic damage. */
	private static void repel(ServerLevel level, FormationData data, Formation formation, boolean hurt) {
		Vec3 centre = Vec3.atCenterOf(formation.pos());
		double radiusSq = RADIUS * RADIUS;
		List<Mob> intruders = level.getEntitiesOfClass(Mob.class, new AABB(centre, centre).inflate(RADIUS),
				mob -> isIntruder(mob, centre, radiusSq));
		if (intruders.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		int counted = 0;
		int ripples = 0;
		for (Mob mob : intruders) {
			push(mob, centre);
			if (formation.notePush(mob.getId(), now, REPEL_MEMORY)) {
				counted++;
				sound(level, mob.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.6F);
			}
			if (ripples < MAX_RIPPLES_PER_PULSE) {
				ripples++;
				Vec3 at = mob.getBoundingBox().getCenter();
				for (ServerPlayer viewer : PlayerLookup.around(level, centre, FX_RANGE)) {
					Fx.sendTo(viewer, at, centre, FxType.PROTECTIVE_FORMATION, 4, 0, (float) RADIUS);
				}
			}
			// Never a silverfish: magic damage makes it call its friends out of infested blocks (broken or replaced).
			if (hurt && mob.isAlive() && !(mob instanceof Silverfish)) {
				mob.hurtServer(level, level.damageSources().magic(), 1.0F);
			}
		}
		if (counted > 0) {
			formation.addRepels(counted);
			data.setDirty();
			if (formation.repels() >= REPELS_FOR_ADVANCEMENT) {
				grantOwner(level, formation, "formation_repelled");
			}
		}
	}

	/**
	 * Monsters ({@link Enemy}) and mobs hunting a player inside the sphere. Never players, pets or spirit beasts,
	 * villagers or traders, statues without AI, invulnerable mobs, a heart demon (a personal trial), the Wither (hurt, it
	 * breaks the blocks around it), the Ender Dragon (pushed, it tears through terrain) or a creaking bound to a heart
	 * (hurt, the heart grows resin).
	 */
	private static boolean isIntruder(Mob mob, Vec3 centre, double radiusSq) {
		if (!mob.isAlive() || mob.isRemoved() || mob.isNoAi() || mob.isInvulnerable() || mob instanceof Npc) {
			return false;
		}
		if (mob instanceof OwnableEntity ownable && ownable.getOwnerReference() != null || SpiritBeasts.isSpiritBeast(mob)) {
			return false;
		}
		if (mob instanceof WitherBoss || mob instanceof EnderDragon || mob instanceof Creaking creaking && creaking.isHeartBound()) {
			return false;
		}
		if (HEART_DEMON.equals(EntityType.getKey(mob.getType())) || mob.distanceToSqr(centre) > radiusSq) {
			return false;
		}
		if (mob instanceof Enemy) {
			return true;
		}
		LivingEntity target = mob.getTarget();
		return target instanceof Player player && !player.isSpectator() && player.distanceToSqr(centre) <= radiusSq;
	}

	/**
	 * Velocity away from the centre (with a small hop on the ground), synced to the clients. Never a ravager (it breaks
	 * the leaves it runs into). The mob gets a portal cooldown, so a push into a portal near the lodestone never sends it
	 * through (that could build an exit portal or the End platform).
	 */
	private static void push(Mob mob, Vec3 centre) {
		if (mob.isPassenger() || mob instanceof Ravager) {
			return;
		}
		mob.setPortalCooldown();
		double dx = mob.getX() - centre.x;
		double dz = mob.getZ() - centre.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal < 1.0E-3D) {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2.0D;
			dx = Math.cos(angle);
			dz = Math.sin(angle);
			horizontal = 1.0D;
		}
		double closeness = 1.0D - Math.min(1.0D, Math.sqrt(mob.distanceToSqr(centre)) / RADIUS);
		double resistance = Mth.clamp(mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
		double power = (0.45D + 0.85D * closeness) * (1.0D - 0.6D * resistance);
		Vec3 motion = mob.getDeltaMovement();
		double up = mob.onGround() ? Math.max(motion.y, 0.2D + 0.15D * closeness) : motion.y;
		mob.setDeltaMovement(motion.x * 0.3D + dx / horizontal * power, up, motion.z * 0.3D + dz / horizontal * power);
		mob.needsSync = true;
		// A pushed monster lands somewhere it did not choose: it must not trample farmland or crush turtle eggs there.
		mob.addTag(NoGrief.TAG);
	}

	/** Switches the formation off (power kept), tells nearby clients and, with a message key, the owner. */
	private static void switchOff(ServerLevel level, FormationData data, Formation formation, String ownerMessage) {
		formation.setActive(false);
		data.setDirty();
		sendDome(level, formation, 0, 0);
		sound(level, Vec3.atCenterOf(formation.pos()), SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.2F);
		if (ownerMessage != null) {
			notifyOwner(level, formation, ownerMessage);
		}
	}

	private static void notifyOwner(ServerLevel level, Formation formation, String key) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(formation.owner());
		if (owner != null) {
			Msg.chat(owner, Component.translatable(key, position(formation.pos())).withStyle(ChatFormatting.YELLOW));
		}
	}

	private static void grantOwner(ServerLevel level, Formation formation, String advancement) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(formation.owner());
		if (owner != null) {
			Advancements.grant(owner, advancement);
		}
	}

	/** The dome effect for players within {@link #FX_RANGE}: state 1 on / 0 off, flourish 1 switched on / 2 charged. */
	private static void sendDome(ServerLevel level, Formation formation, int state, int flourish) {
		Vec3 centre = Vec3.atCenterOf(formation.pos());
		for (ServerPlayer viewer : PlayerLookup.around(level, centre, FX_RANGE)) {
			Fx.sendTo(viewer, centre, centre, FxType.PROTECTIVE_FORMATION, state, flourish, (float) RADIUS);
		}
	}

	private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch);
	}

	// ================================================================ commands

	/** {@code formation list}: the caller's formations in every dimension. */
	private static int listCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		List<Component> lines = new ArrayList<>();
		for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
			for (Formation formation : FormationData.get(level).all()) {
				if (formation.isOwner(player)) {
					lines.add(Component.translatable("kultivator.formations.list.entry",
							dimensionName(level).withStyle(ChatFormatting.GRAY),
							position(formation.pos()), state(formation.active()), duration(formation.power())));
				}
			}
		}
		if (lines.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.formations.list.empty")), false);
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.formations.list.header")
				.withStyle(ChatFormatting.GREEN)), false);
		for (Component line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(" • ").withStyle(ChatFormatting.DARK_GREEN).append(line), false);
		}
		return lines.size();
	}

	/**
	 * Debug {@code formation power <minutes>}: sets the power of the formation whose lodestone the caller looks at. A
	 * complete but unregistered pattern is registered first (owner = the caller, switched off).
	 */
	private static int powerCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
		BlockHitResult hit = Targeting.blockHit(player, LOOK_RANGE);
		if (hit.getType() != HitResult.Type.BLOCK || !FormationPattern.isLodestone(level, hit.getBlockPos())) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.formations.debug.no_lodestone"));
			return 0;
		}
		BlockPos pos = hit.getBlockPos().immutable();
		FormationData data = FormationData.get(level);
		Formation formation = data.get(pos);
		if (formation == null) {
			List<BlockPos> missing = FormationPattern.missing(level, pos);
			if (!missing.isEmpty()) {
				showIncomplete(player, pos, missing);
				return 0;
			}
			formation = new Formation(pos, player.getUUID(), player.getPlainTextName(), false, 0, 0);
			data.put(formation);
		}
		formation.setPower(minutes * 1200);
		data.setDirty();
		Formation changed = formation;
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.formations.debug.power",
				position(pos), duration(changed.power()))), false);
		return 1;
	}

	/** Debug {@code formation clear}: forgets every registered formation of this dimension (blocks untouched). */
	private static int clearCommand(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		FormationData data = FormationData.get(level);
		int count = data.size();
		for (Formation formation : List.copyOf(data.all())) {
			if (formation.active()) {
				sendDome(level, formation, 0, 0);
			}
		}
		data.clear();
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.formations.debug.cleared", count)), true);
		return count;
	}

	// ================================================================ text

	private static Component ownerName(Formation formation) {
		String name = formation.ownerName().isEmpty() ? formation.owner().toString() : formation.ownerName();
		return Component.literal(name).withStyle(ChatFormatting.GOLD);
	}

	/** The translated name of the three vanilla dimensions; the id for any other. */
	private static MutableComponent dimensionName(ServerLevel level) {
		ResourceKey<Level> key = level.dimension();
		String id = key.equals(Level.OVERWORLD) ? "overworld" : key.equals(Level.NETHER) ? "nether" : key.equals(Level.END) ? "end" : null;
		return id != null ? Component.translatable("kultivator.formations.dimension." + id) : Component.literal(key.identifier().toString());
	}

	private static MutableComponent position(BlockPos pos) {
		return Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.AQUA);
	}

	private static MutableComponent state(boolean on) {
		return on ? Component.translatable("kultivator.formations.state.on").withStyle(ChatFormatting.GREEN)
				: Component.translatable("kultivator.formations.state.off").withStyle(ChatFormatting.GRAY);
	}

	/** "1h 20m" or "9m 30s" (translated) for a number of ticks. */
	public static MutableComponent duration(int ticks) {
		int seconds = Math.max(0, ticks) / 20;
		int hours = seconds / 3600;
		int minutes = seconds % 3600 / 60;
		MutableComponent text = hours > 0
				? Component.translatable("kultivator.formations.duration.hours", hours, minutes)
				: Component.translatable("kultivator.formations.duration.minutes", minutes, seconds % 60);
		return text.withStyle(ChatFormatting.LIGHT_PURPLE);
	}

	private ProtectiveFormations() {
	}
}
