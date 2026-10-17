package dev.kultivator.kehancuran;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DestroyerArts;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mode Kehancuran, the only block-changing feature of the mod (DESIGN 20.3.21). OFF by default and behind three locks,
 * all required: the server ({@code modeKehancuran} in {@code config/kultivator.json}, changed by an operator with
 * {@code /kultivasi kehancuran izin on|off} or in the file), the player's Sumpah Kehancuran, and the disciple's released
 * red seal ("Lepaskan Segel" on the Jalan tab). Locks 2 and 3 open the blood-red warning screen; closing any lock is one
 * click and stops that master's casts at once. The player never gets destructive techniques himself.
 * <p>
 * An ordered destructive cast: the master aims within 10 s (sneak while looking at the target, a read-only ray of up to
 * 64 blocks); the shape is validated against the protected zones ({@link ProtectedZones}) and previewed; a clickable
 * chat confirm "[Ya, hancurkan]" within 15 s starts a dimension-wide warning and red-sky countdown of
 * {@code kehancuranWarningSeconds} ("[Batalkan]" works until zero); then the disciple casts and {@link WorldBreaker}
 * works the shape off within the block budget. Every cast is logged ({@link KehancuranLog}) and restorable for
 * {@code kehancuranUndoMinutes} when no drops were enabled.
 */
public final class Kehancuran {

	public static final int AIM_TICKS = 200;
	public static final int CONFIRM_TICKS = 300;
	public static final double AIM_RANGE = 64.0D;
	/** The warning seal is held this long on the client: 5 s for the oath, 3 s for a disciple. */
	public static final int OATH_HOLD_TICKS = 100;
	public static final int RELEASE_HOLD_TICKS = 60;
	private static final String PREFIX = "kultivator.kehancuran.";
	private static final int RED = 0xFF4A3A;
	private static final int MUTED = 0xA8B5B0;

	private enum Stage { AIM, CONFIRM, WARNING }

	/** One master's destructive order on its way (one at a time per master). */
	private static final class Order {
		final UUID owner;
		final DiscipleEntity entity;
		final DestroyerArts.Art art;
		Stage stage = Stage.AIM;
		long until;
		boolean wasSneaking;
		@Nullable BlockPos centre;
		Vec3 direction = Vec3.ZERO;
		@Nullable ResourceKey<Level> dimension;
		List<Shapes.Change> plan = List.of();
		int lastSecond = -1;

		Order(UUID owner, DiscipleEntity entity, DestroyerArts.Art art) {
			this.owner = owner;
			this.entity = entity;
			this.art = art;
		}
	}

	private static final Map<UUID, Order> ORDERS = new HashMap<>();

	public static void init() {
		WorldBreaker.init();
		ServerTickEvents.END_SERVER_TICK.register(Kehancuran::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ORDERS.clear());
		ServerPlayerEvents.LEAVE.register(player -> {
			Order order = ORDERS.get(player.getUUID());
			if (order != null) {
				cancel(player.level().getServer(), order, "cancel.left");
			}
			// The hall zone remembers every hall at its current stele, also for owners who stay offline.
			ProtectedZones.noteHall(Halls.hallOf(player.getUUID()), player.getUUID(), player.level().getServer());
		});
		ServerPlayerEvents.JOIN.register(player -> ProtectedZones.noteHall(Halls.hallOf(player.getUUID()), player.getUUID(),
				player.level().getServer()));
		Halls.addInfoProvider(Kehancuran::hallInfo);
	}

	// ================================================================== the three locks

	/** Lock 1: the server allows Mode Kehancuran at all ({@code config/kultivator.json}, default false). */
	public static boolean serverAllows() {
		return KultivatorConfig.get().modeKehancuran;
	}

	/** Lock 2: the player swore the Sumpah Kehancuran. */
	public static boolean playerSworn(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(DisciplePaths.PLAYER_EXTRA).getBooleanOr("sworn", false);
	}

	/** Lock 3: the master released this disciple's red seal. */
	public static boolean released(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(DisciplePaths.EXTRA).getBooleanOr("released", false);
	}

	/** All three locks are open for this disciple and it commands a Jurus Pemusnah. */
	public static boolean destructiveAllowed(ServerPlayer master, DiscipleEntity entity) {
		return serverAllows() && playerSworn(master) && released(entity.data()) && DestroyerArts.eligible(entity.data())
				&& DestroyerArts.chosen(entity.data()) != null;
	}

	// ================================================================== actions

	/** {@code Actions.KEHANCURAN}: {@code arg} = 0 or a disciple's entity id, {@code text} = "op[:param]". */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		String op = action.text() == null ? "" : action.text();
		switch (op) {
			case "oath" -> openOath(player);
			case "oath_confirm" -> swear(player);
			case "oath_revoke" -> revoke(player);
			case "release" -> {
				DiscipleEntity entity = disciple(player, action.arg());
				if (entity != null) {
					openRelease(player, entity);
				}
			}
			case "release_confirm" -> {
				DiscipleEntity entity = disciple(player, action.arg());
				if (entity != null) {
					release(player, entity);
				}
			}
			case "seal" -> {
				DiscipleEntity entity = disciple(player, action.arg());
				if (entity != null) {
					seal(player, entity);
				}
			}
			case "restore" -> restore(player);
			case "cancel" -> {
				Order order = ORDERS.get(player.getUUID());
				if (order != null) {
					cancel(player.level().getServer(), order, "cancel.master");
				}
			}
			default -> {
			}
		}
	}

	private static @Nullable DiscipleEntity disciple(ServerPlayer player, int id) {
		Entity entity = player.level().getEntity(id);
		if (entity instanceof DiscipleEntity disciple && disciple.isAlive() && disciple.isOwnedBy(player)
				&& disciple.role() == DiscipleEntity.Role.DISCIPLE && player.distanceToSqr(disciple) <= Disciples.ACTION_RANGE * Disciples.ACTION_RANGE) {
			return disciple;
		}
		Msg.actionBar(player, Component.translatable("kultivator.dpath.gone").withStyle(ChatFormatting.GRAY));
		return null;
	}

	/** Sends the server's caps (for the warning screen) to one player. */
	private static void sendConfig(ServerPlayer player) {
		KultivatorConfig c = KultivatorConfig.get();
		Fx.sendTo(player, new Vec3(c.kehancuranMaxRadius, c.kehancuranMaxBlocksPerCast, c.kehancuranDropsPct),
				new Vec3(c.kehancuranHallSafeRadius, c.kehancuranPlayerSafeRadius, c.kehancuranUndoMinutes), FxType.KEHANCURAN,
				KehancuranFxIds.CONFIG, c.kehancuranWarningSeconds, c.kehancuranBlocksPerTick);
	}

	/** Opens the oath's warning screen (Segel Merah tablet or {@code /kultivasi kehancuran sumpah}). */
	static void openOath(ServerPlayer player) {
		if (!serverAllows()) {
			Msg.chat(player, Component.translatable(PREFIX + "why.server").withColor(RED));
			return;
		}
		if (playerSworn(player)) {
			Msg.chat(player, Component.translatable(PREFIX + "already_sworn").withColor(MUTED));
			return;
		}
		sendConfig(player);
		Net.send(player, new Payloads.OpenScreen(Payloads.OpenScreen.KEHANCURAN_CONFIRM, 0));
	}

	private static void swear(ServerPlayer player) {
		if (!serverAllows()) {
			Msg.chat(player, Component.translatable(PREFIX + "why.server").withColor(RED));
			return;
		}
		CompoundTag path = section(player);
		path.putBoolean("sworn", true);
		Cultivators.dirty(player);
		Msg.chat(player, Component.translatable(PREFIX + "sworn").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		Msg.chat(player, Component.translatable(PREFIX + "sworn_hint").withColor(MUTED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.6F);
		Advancements.grant(player, "destruction_oath");
	}

	private static void revoke(ServerPlayer player) {
		section(player).putBoolean("sworn", false);
		Cultivators.dirty(player);
		MinecraftServer server = player.level().getServer();
		Order order = ORDERS.get(player.getUUID());
		if (order != null) {
			cancel(server, order, "cancel.lock");
		}
		int stopped = WorldBreaker.cancelOwner(server, player.getUUID());
		Msg.chat(player, Component.translatable(PREFIX + "revoked", stopped).withColor(0x9FF3E6));
	}

	/** Why the disciple's red seal cannot be released now (a translation key), or null. */
	static @Nullable String releaseBlocker(ServerPlayer player, DiscipleEntity entity) {
		if (!serverAllows()) {
			return PREFIX + "why.server";
		}
		if (!playerSworn(player)) {
			return PREFIX + "why.oath";
		}
		if (!DestroyerArts.eligible(entity.data())) {
			return PREFIX + "why.eligible";
		}
		if (DestroyerArts.chosen(entity.data()) == null) {
			return PREFIX + "why.no_art";
		}
		return null;
	}

	private static void openRelease(ServerPlayer player, DiscipleEntity entity) {
		String why = releaseBlocker(player, entity);
		if (why != null) {
			Msg.chat(player, Component.translatable(why, entity.getDisplayName()).withColor(RED));
			return;
		}
		sendConfig(player);
		Disciples.sendInfo(player, entity);
		Net.send(player, new Payloads.OpenScreen(Payloads.OpenScreen.KEHANCURAN_CONFIRM, entity.getId()));
	}

	private static void release(ServerPlayer player, DiscipleEntity entity) {
		String why = releaseBlocker(player, entity);
		if (why != null) {
			Msg.chat(player, Component.translatable(why, entity.getDisplayName()).withColor(RED));
			return;
		}
		entity.data().extraSection(DisciplePaths.EXTRA).putBoolean("released", true);
		DisciplePaths.syncPower(entity);
		Msg.chat(player, Component.translatable(PREFIX + "released", entity.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
		player.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.NEUTRAL, 1.0F, 0.7F);
		Disciples.sendInfo(player, entity);
	}

	/** Closes the disciple's red seal (lock 3) at once. */
	private static void seal(ServerPlayer player, DiscipleEntity entity) {
		entity.data().extraSection(DisciplePaths.EXTRA).putBoolean("released", false);
		DisciplePaths.syncPower(entity);
		MinecraftServer server = player.level().getServer();
		Order order = ORDERS.get(player.getUUID());
		if (order != null && order.entity == entity) {
			cancel(server, order, "cancel.lock");
		}
		int stopped = WorldBreaker.cancelOwner(server, player.getUUID());
		Msg.chat(player, Component.translatable(PREFIX + "sealed", entity.getDisplayName(), stopped).withColor(0x9FF3E6));
		Disciples.sendInfo(player, entity);
	}

	private static void restore(ServerPlayer player) {
		int queued = WorldBreaker.restore(player.getUUID());
		if (queued <= 0) {
			Msg.chat(player, Component.translatable(PREFIX + "why.nothing_to_restore").withColor(MUTED));
			return;
		}
		Msg.chat(player, Component.translatable(PREFIX + "restoring", queued).withColor(0x9FF3E6));
	}

	private static CompoundTag section(ServerPlayer player) {
		CompoundTag extra = Cultivators.get(player).extra();
		CompoundTag path = extra.getCompound(DisciplePaths.PLAYER_EXTRA).orElse(null);
		if (path == null) {
			path = new CompoundTag();
			extra.put(DisciplePaths.PLAYER_EXTRA, path);
		}
		return path;
	}

	// ================================================================== a destructive order

	/** Begins the 10 s aim of a destructive cast (all three locks are open; checked by the caller). */
	public static void beginAim(ServerPlayer master, DiscipleEntity entity, DestroyerArts.Art art) {
		if (ORDERS.containsKey(master.getUUID())) {
			Msg.chat(master, Component.translatable(PREFIX + "why.order_running").withColor(RED));
			return;
		}
		if (!WorldBreaker.hasRoom()) {
			Msg.chat(master, Component.translatable(PREFIX + "why.queue_full").withColor(RED));
			return;
		}
		Order order = new Order(master.getUUID(), entity, art);
		order.until = master.level().getGameTime() + AIM_TICKS;
		order.wasSneaking = master.isShiftKeyDown();
		ORDERS.put(master.getUUID(), order);
		Msg.chat(master, Component.translatable(PREFIX + "aim", entity.getDisplayName(), art.displayName(), AIM_TICKS / 20, (int) AIM_RANGE)
				.withStyle(ChatFormatting.RED));
	}

	private static void tick(MinecraftServer server) {
		if (ORDERS.isEmpty()) {
			return;
		}
		for (Order order : List.copyOf(ORDERS.values())) {
			ServerPlayer master = server.getPlayerList().getPlayer(order.owner);
			if (master == null) {
				cancel(server, order, "cancel.left");
				continue;
			}
			DiscipleEntity entity = order.entity;
			if (entity.isRemoved() || !entity.isAlive() || !destructiveAllowed(master, entity)) {
				cancel(server, order, "cancel.lock");
				continue;
			}
			long now = master.level().getGameTime();
			switch (order.stage) {
				case AIM -> tickAim(master, order, now);
				case CONFIRM -> {
					if (now >= order.until) {
						cancel(server, order, "cancel.timeout");
					}
				}
				case WARNING -> tickWarning(server, master, order, now);
			}
		}
	}

	private static void tickAim(ServerPlayer master, Order order, long now) {
		if (now >= order.until) {
			cancel(master.level().getServer(), order, "cancel.timeout");
			return;
		}
		boolean sneaking = master.isShiftKeyDown();
		boolean pressed = sneaking && !order.wasSneaking;
		order.wasSneaking = sneaking;
		if ((order.until - now) % 20 == 0) {
			Msg.actionBar(master, Component.translatable(PREFIX + "aim_bar", (order.until - now) / 20).withStyle(ChatFormatting.RED));
		}
		if (!pressed) {
			return;
		}
		BlockHitResult hit = Targeting.blockHit(master, AIM_RANGE);
		if (hit.getType() == HitResult.Type.MISS) {
			Msg.actionBar(master, Component.translatable(PREFIX + "aim_miss").withColor(MUTED));
			return;
		}
		plan(master, order, hit.getBlockPos());
	}

	/** Builds and validates the shape at the aimed block, previews it and asks for the clickable confirm. */
	private static void plan(ServerPlayer master, Order order, BlockPos aimed) {
		ServerLevel level = master.level();
		if (order.entity.level() != level) {
			cancel(level.getServer(), order, "cancel.dimension");
			return;
		}
		KultivatorConfig config = KultivatorConfig.get();
		DiscipleData data = order.entity.data();
		Shapes.Spec spec = DestroyerArts.spec(order.art, DisciplePaths.stars(data));
		Vec3 look = master.getViewVector(1.0F);
		Vec3 direction = new Vec3(look.x, 0.0D, look.z);
		List<Shapes.Change> raw = Shapes.build(level, spec, aimed, direction);
		if (raw.isEmpty()) {
			Msg.chat(master, Component.translatable(PREFIX + "why.empty").withColor(RED));
			return;
		}
		AABB box = bounds(raw);
		ProtectedZones.Context zones = new ProtectedZones.Context(level, box);
		List<Shapes.Change> plan = new ArrayList<>();
		for (Shapes.Change change : raw) {
			ProtectedZones.Zone zone = zones.zoneOf(change.pos());
			if (zone == ProtectedZones.Zone.UNLOADED) {
				continue;
			}
			if (zone != null) {
				Msg.chat(master, Component.translatable(PREFIX + "why.zone", Component.translatable(zone.key()), change.pos().getX(),
						change.pos().getY(), change.pos().getZ()).withColor(RED));
				return;
			}
			BlockState state = level.getBlockState(change.pos());
			if (ProtectedZones.neverTouch(level, change.pos(), state) || spec.naturalOnly() && !Shapes.natural(state)) {
				continue;
			}
			plan.add(change);
			if (plan.size() >= config.kehancuranMaxBlocksPerCast) {
				break;
			}
		}
		if (plan.isEmpty()) {
			Msg.chat(master, Component.translatable(PREFIX + "why.empty").withColor(RED));
			return;
		}
		order.plan = plan;
		order.centre = aimed;
		order.direction = direction;
		order.dimension = level.dimension();
		order.stage = Stage.CONFIRM;
		order.until = level.getGameTime() + CONFIRM_TICKS;
		preview(level, spec, aimed, direction);
		MutableComponent yes = Component.translatable(PREFIX + "confirm.yes").withStyle(style -> style.withColor(ChatFormatting.RED)
				.withBold(true).withClickEvent(new ClickEvent.RunCommand("/kultivasi kehancuran ya"))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(PREFIX + "confirm.yes_hover"))));
		MutableComponent no = Component.translatable(PREFIX + "confirm.no").withStyle(style -> style.withColor(ChatFormatting.GRAY)
				.withClickEvent(new ClickEvent.RunCommand("/kultivasi kehancuran batal")));
		Msg.chat(master, Component.translatable(PREFIX + "confirm", order.entity.getDisplayName(), order.art.displayName(),
				Component.translatable(PREFIX + "shape." + spec.kind().id()), plan.size(), aimed.getX(), aimed.getY(), aimed.getZ(),
				CONFIRM_TICKS / 20).withColor(RED));
		master.sendSystemMessage(Component.empty().append(yes).append(Component.literal("   ")).append(no));
	}

	private static void preview(ServerLevel level, Shapes.Spec spec, BlockPos centre, Vec3 direction) {
		Vec3 at = Vec3.atBottomCenterOf(centre).add(0.0D, 1.0D, 0.0D);
		Vec3 target = switch (spec.kind()) {
			case TRENCH -> {
				Vec3 dir = direction.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : direction.normalize();
				yield at.add(dir.scale(spec.length()));
			}
			case DISC, PIT, COLUMN -> at.add(0.0D, -spec.depth(), 0.0D);
			default -> at;
		};
		float size = spec.kind() == Shapes.Kind.TRENCH ? Math.max(1, spec.width() / 2.0F) : spec.radius();
		Fx.send(level, at, target, FxType.KEHANCURAN, KehancuranFxIds.PREVIEW, spec.kind().ordinal(), size);
	}

	/** {@code /kultivasi kehancuran ya}: the dimension-wide warning and the red-sky countdown begin. */
	static int confirm(ServerPlayer master) {
		Order order = ORDERS.get(master.getUUID());
		if (order == null || order.stage != Stage.CONFIRM) {
			Msg.chat(master, Component.translatable(PREFIX + "why.no_order").withColor(MUTED));
			return 0;
		}
		ServerLevel level = master.level();
		int seconds = KultivatorConfig.get().kehancuranWarningSeconds;
		order.stage = Stage.WARNING;
		order.until = level.getGameTime() + seconds * 20L;
		order.lastSecond = -1;
		BlockPos c = order.centre;
		if (c == null) {
			cancel(level.getServer(), order, "cancel.timeout");
			return 0;
		}
		Component line = Component.translatable(PREFIX + "warning", order.entity.getDisplayName(), order.art.displayName(), c.getX(), c.getY(), c.getZ(), seconds)
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Msg.prefixed(line));
			Fx.sendTo(player, Vec3.atCenterOf(c), Vec3.atCenterOf(c), FxType.KEHANCURAN, KehancuranFxIds.RED_SKY, seconds, 0.0F);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.5F);
		}
		MutableComponent stop = Component.translatable(PREFIX + "cancel_button").withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withBold(true).withClickEvent(new ClickEvent.RunCommand("/kultivasi kehancuran batal")));
		master.sendSystemMessage(stop);
		return 1;
	}

	private static void tickWarning(MinecraftServer server, ServerPlayer master, Order order, long now) {
		ServerLevel level = master.level();
		int left = (int) Math.max(0L, (order.until - now + 19L) / 20L);
		if (left != order.lastSecond) {
			order.lastSecond = left;
			if (left > 0 && (left % 10 == 0 || left <= 5)) {
				BlockPos c = order.centre;
				Component title = Component.translatable(PREFIX + "title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
				Component subtitle = Component.translatable(PREFIX + "subtitle", order.entity.getDisplayName(), order.art.displayName(),
						c == null ? 0 : c.getX(), c == null ? 0 : c.getY(), c == null ? 0 : c.getZ(), left).withColor(RED);
				for (ServerPlayer player : level.players()) {
					Msg.title(player, title, subtitle, 0, 30, 10);
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 0.8F, 0.5F);
				}
			}
		}
		if (now >= order.until) {
			cast(server, master, order);
		}
	}

	/** The countdown reached zero: the disciple casts, and the shape goes to the WorldBreaker. */
	private static void cast(MinecraftServer server, ServerPlayer master, Order order) {
		ORDERS.remove(order.owner);
		stopSky(server, order);
		DiscipleEntity entity = order.entity;
		BlockPos centre = order.centre;
		ResourceKey<Level> dimension = order.dimension;
		if (centre == null || dimension == null || !(entity.level() instanceof ServerLevel level) || !level.dimension().equals(dimension)) {
			Msg.chat(master, Component.translatable(PREFIX + "cancel.dimension").withColor(MUTED));
			return;
		}
		KultivatorConfig config = KultivatorConfig.get();
		DestroyerArts.castSafe(entity, order.art, Vec3.atCenterOf(centre), false);
		DestroyerArts.startCooldown(entity);
		long[] positions = new long[order.plan.size()];
		BlockState[] targets = new BlockState[order.plan.size()];
		for (int i = 0; i < positions.length; i++) {
			positions[i] = order.plan.get(i).pos().asLong();
			targets[i] = order.plan.get(i).to();
		}
		Shapes.Spec spec = DestroyerArts.spec(order.art, DisciplePaths.stars(entity.data()));
		WorldBreaker.Job job = new WorldBreaker.Job(master.getUUID(), master.getName().getString(), entity.getName().getString(),
				spec.kind().id(), dimension, centre, positions, targets, null, false, spec.naturalOnly(), spec.kind() == Shapes.Kind.WITHER,
				config.kehancuranDropsPct);
		if (!WorldBreaker.submit(job)) {
			Msg.chat(master, Component.translatable(PREFIX + "why.queue_full").withColor(RED));
			return;
		}
		Fx.send(level, Vec3.atCenterOf(centre), FxType.KEHANCURAN, KehancuranFxIds.IMPACT, order.art.ordinal(), spec.radius() + 2.0F);
		level.playSound(null, centre.getX(), centre.getY(), centre.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0F, 0.5F);
		Msg.chat(master, Component.translatable(PREFIX + "cast", entity.getDisplayName(), order.art.displayName(), positions.length)
				.withStyle(ChatFormatting.DARK_RED));
		Advancements.grant(master, "world_breaker");
	}

	private static void cancel(MinecraftServer server, Order order, String reasonKey) {
		if (ORDERS.remove(order.owner) == null) {
			return;
		}
		ServerPlayer master = server.getPlayerList().getPlayer(order.owner);
		if (master != null) {
			Msg.chat(master, Component.translatable(PREFIX + reasonKey).withColor(MUTED));
		}
		if (order.stage == Stage.WARNING) {
			stopSky(server, order);
			ServerLevel level = order.dimension == null ? null : server.getLevel(order.dimension);
			if (level != null) {
				Component line = Component.translatable(PREFIX + "warning_cancelled", order.entity.getDisplayName()).withColor(0x9FF3E6);
				for (ServerPlayer player : level.players()) {
					player.sendSystemMessage(Msg.prefixed(line));
				}
			}
		}
	}

	private static void stopSky(MinecraftServer server, Order order) {
		ServerLevel level = order.dimension == null ? null : server.getLevel(order.dimension);
		if (level == null) {
			return;
		}
		BlockPos c = order.centre == null ? BlockPos.ZERO : order.centre;
		for (ServerPlayer player : level.players()) {
			Fx.sendTo(player, Vec3.atCenterOf(c), Vec3.atCenterOf(c), FxType.KEHANCURAN, KehancuranFxIds.RED_SKY, 0, 0.0F);
		}
	}

	private static AABB bounds(List<Shapes.Change> changes) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (Shapes.Change change : changes) {
			BlockPos pos = change.pos();
			minX = Math.min(minX, pos.getX());
			minY = Math.min(minY, pos.getY());
			minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX());
			maxY = Math.max(maxY, pos.getY());
			maxZ = Math.max(maxZ, pos.getZ());
		}
		return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
	}

	// ================================================================== commands

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("kehancuran")
				.executes(ctx -> status(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("sumpah").executes(ctx -> {
					openOath(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("status").executes(ctx -> status(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("pulihkan").executes(ctx -> {
					restore(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("ya").executes(ctx -> confirm(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("batal").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Order order = ORDERS.get(player.getUUID());
					if (order == null) {
						Msg.chat(player, Component.translatable(PREFIX + "why.no_order").withColor(MUTED));
						return 0;
					}
					cancel(player.level().getServer(), order, "cancel.master");
					return 1;
				}))
				.then(Commands.literal("izin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("on").executes(ctx -> permit(ctx.getSource(), true)))
						.then(Commands.literal("off").executes(ctx -> permit(ctx.getSource(), false)))));
	}

	private static int permit(CommandSourceStack source, boolean on) {
		KultivatorConfig.get().modeKehancuran = on;
		boolean saved = KultivatorConfig.save();
		MinecraftServer server = source.getServer();
		if (!on) {
			for (Order order : List.copyOf(ORDERS.values())) {
				cancel(server, order, "cancel.lock");
			}
			WorldBreaker.cancelAll(server);
		}
		Component line = Component.translatable(on ? PREFIX + "permit_on" : PREFIX + "permit_off").withStyle(on ? ChatFormatting.DARK_RED : ChatFormatting.AQUA);
		source.sendSuccess(() -> Msg.prefixed(line), true);
		if (!saved) {
			source.sendFailure(Component.translatable(PREFIX + "permit_unsaved"));
		}
		return 1;
	}

	private static int status(ServerPlayer player) {
		KultivatorConfig c = KultivatorConfig.get();
		Msg.chat(player, Component.translatable(PREFIX + "status.title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.translatable(PREFIX + "status.locks", lock(serverAllows()), lock(playerSworn(player))).withColor(MUTED));
		List<String> released = new ArrayList<>();
		for (DiscipleEntity entity : Disciples.loaded(player.getUUID())) {
			if (released(entity.data())) {
				released.add(entity.getName().getString());
			}
		}
		player.sendSystemMessage(Component.translatable(PREFIX + "status.released", released.isEmpty() ? "-" : String.join(", ", released)).withColor(MUTED));
		player.sendSystemMessage(Component.translatable(PREFIX + "status.caps", c.kehancuranMaxRadius, c.kehancuranMaxBlocksPerCast, c.kehancuranBlocksPerTick,
				c.kehancuranDropsPct, c.kehancuranHallSafeRadius, c.kehancuranPlayerSafeRadius, c.kehancuranUndoMinutes).withColor(MUTED));
		WorldBreaker.Job running = WorldBreaker.running();
		player.sendSystemMessage(Component.translatable(PREFIX + "status.jobs", running == null ? 0 : 1, WorldBreaker.queued()).withColor(MUTED));
		for (int[] undo : WorldBreaker.undoable(player.level().getServer(), player.getUUID())) {
			player.sendSystemMessage(Component.translatable(PREFIX + "status.undo", undo[0], undo[1] / 60, undo[1] % 60).withColor(0x9FF3E6));
		}
		return 1;
	}

	private static Component lock(boolean open) {
		return Component.translatable(open ? PREFIX + "lock.open" : PREFIX + "lock.closed").withStyle(open ? ChatFormatting.RED : ChatFormatting.GREEN);
	}

	// ================================================================== screen details

	/**
	 * The {@code kehancuran} compound of {@code owner}'s Sect Hall details (the Segel Merah tablet) as {@code viewer} sees
	 * it; a sect mate's view is read-only.
	 */
	private static void hallInfo(ServerPlayer viewer, UUID owner, CompoundTag tag) {
		MinecraftServer server = viewer.level().getServer();
		boolean own = viewer.getUUID().equals(owner);
		ProtectedZones.noteHall(Halls.hallOf(owner), owner, server);
		ServerPlayer master = own ? viewer : server.getPlayerList().getPlayer(owner);
		CompoundTag info = new CompoundTag();
		info.putBoolean("readonly", !own);
		info.putBoolean("server", serverAllows());
		info.putBoolean("sworn", master != null && playerSworn(master));
		ListTag released = new ListTag();
		for (DiscipleEntity entity : Disciples.loaded(owner)) {
			if (released(entity.data())) {
				released.add(StringTag.valueOf(entity.getName().getString()));
			}
		}
		info.put("released", released);
		WorldBreaker.Job running = WorldBreaker.running();
		info.putBoolean("job_mine", running != null && running.owner.equals(owner));
		info.putInt("job_done", running == null ? 0 : running.index);
		info.putInt("job_total", running == null ? 0 : running.positions.length);
		info.putInt("queued", WorldBreaker.queued());
		ListTag undo = new ListTag();
		for (int[] entry : WorldBreaker.undoable(server, owner)) {
			CompoundTag line = new CompoundTag();
			line.putInt("blocks", entry[0]);
			line.putInt("seconds", entry[1]);
			undo.add(line);
		}
		info.put("undo", undo);
		ListTag log = new ListTag();
		for (CompoundTag line : KehancuranLog.recent(server, owner, 6)) {
			log.add(line.copy());
		}
		info.put("log", log);
		info.put("cfg", config());
		tag.put("kehancuran", info);
	}

	/** The red seal's part of the disciple's {@code path} details (the Jalan tab). */
	public static void discipleInfo(DiscipleEntity entity, ServerPlayer master, CompoundTag path) {
		path.putBoolean("k_server", serverAllows());
		path.putBoolean("k_sworn", playerSworn(master));
		path.putBoolean("k_released", released(entity.data()));
		path.putBoolean("k_destructive", destructiveAllowed(master, entity));
		String why = releaseBlocker(master, entity);
		path.putString("k_release_reason", why == null ? "" : why);
		Order order = ORDERS.get(master.getUUID());
		path.putString("k_order", order == null || order.entity != entity ? "" : order.stage.name().toLowerCase(java.util.Locale.ROOT));
		path.put("k_cfg", config());
	}

	private static CompoundTag config() {
		KultivatorConfig c = KultivatorConfig.get();
		CompoundTag cfg = new CompoundTag();
		cfg.putInt("max_radius", c.kehancuranMaxRadius);
		cfg.putInt("max_blocks", c.kehancuranMaxBlocksPerCast);
		cfg.putInt("per_tick", c.kehancuranBlocksPerTick);
		cfg.putInt("drops", c.kehancuranDropsPct);
		cfg.putInt("hall_safe", c.kehancuranHallSafeRadius);
		cfg.putInt("player_safe", c.kehancuranPlayerSafeRadius);
		cfg.putInt("warning", c.kehancuranWarningSeconds);
		cfg.putInt("undo", c.kehancuranUndoMinutes);
		cfg.putInt("mspt", c.kehancuranMsptPause);
		return cfg;
	}

	private Kehancuran() {
	}
}
