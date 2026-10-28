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
import dev.kultivator.technique.spec.TechniquePower;
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
import net.minecraft.nbt.IntTag;
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
import net.minecraft.world.level.block.Blocks;
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
 * click and stops that master's casts at once. Since phase 6c the player's own techniques may carve too, through the
 * one-click switch of DESIGN 22.11 ({@link #setSelf}, {@link #submitSelf}; no oath screen).
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

	/** Read-only: the master has a destructive order in its aim, confirm or countdown stage (DESIGN 19.3.6). */
	public static boolean ordering(UUID master) {
		return ORDERS.containsKey(master);
	}

	public static void init() {
		WorldBreaker.init();
		ServerTickEvents.END_SERVER_TICK.register(Kehancuran::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ORDERS.clear();
			DEFERRED.clear();
			NOTED.clear();
			ProtectedZones.clearClaimCache();
		});
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

	// ================================================================== the player's own techniques (DESIGN 22.11)

	/** Results of {@link #submitSelf} (22.11.5). */
	public enum SelfResult { QUEUED, OFF, QUEUE_FULL, ALL_PROTECTED, NOTHING, DEFERRED }

	/** {@code extra.path.breaker}: the player's own switch ("Saklar gampang"), written only here. */
	public static final String SELF_KEY = "breaker";

	/**
	 * Why the player's own switch may not be on, or null when it may (22.11.1): {@code kehancuranPlayerTechniques} must be
	 * true, and the player must own the integrated server (singleplayer, the LAN host) or the server permission
	 * {@code modeKehancuran} of 20.3.21 must be on.
	 */
	public static @Nullable Component selfAllowed(ServerPlayer player) {
		KultivatorConfig config = KultivatorConfig.get();
		if (!config.kehancuranPlayerTechniques) {
			return Component.translatable("kultivator.kehancuran_diri.why.config");
		}
		if (config.modeKehancuran || player.level().getServer().isSingleplayerOwner(player.nameAndId())) {
			return null;
		}
		return Component.translatable("kultivator.kehancuran_diri.why.permission");
	}

	/** The stored switch is on and still allowed (a guest whose permission was taken reads as off). */
	public static boolean selfOn(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(DisciplePaths.PLAYER_EXTRA).getBooleanOr(SELF_KEY, false)
				&& selfAllowed(player) == null;
	}

	/** The player's Kawah Lebur switch (DESIGN 28.8.1; default on): their self jobs run the molten surface pass. */
	public static boolean kawahOn(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(DisciplePaths.PLAYER_EXTRA).getBooleanOr(KAWAH_KEY, true);
	}

	/** {@code /kultivasi kehancuran kawah nyala|mati}. */
	public static int setKawah(ServerPlayer player, boolean on) {
		section(player).putBoolean(KAWAH_KEY, on);
		Cultivators.dirty(player);
		Msg.chat(player, Component.translatable(on ? "kultivator.dampak_server.kawah.on" : "kultivator.dampak_server.kawah.off",
				KultivatorConfig.get().kehancuranSurfacePass ? Component.empty()
						: Component.translatable("kultivator.dampak_server.kawah.server_off")).withColor(on ? 0xFF8A5A : 0x9FF3E6));
		return 1;
	}

	// ================================================================== Phase 30: Hancur Total (DESIGN 30.1.2, contract C30.5)

	/** {@code extra.path.kh_total}: the player's Hancur Total switch (default ON), stored with the self switch. */
	public static final String TOTAL_KEY = "kh_total";
	private static final String TOTAL = "kultivator.kehancuran_total.";

	/** The player's Hancur Total switch is on (default true) and the server allows it ({@code kehancuranTotalAllowed}). */
	public static boolean totalOn(ServerPlayer player) {
		return KultivatorConfig.get().kehancuranTotalAllowed
				&& Cultivators.get(player).extra().getCompoundOrEmpty(DisciplePaths.PLAYER_EXTRA).getBooleanOr(TOTAL_KEY, true);
	}

	/** The rules of a new job of {@code player} (own casts, Titah carves; a disciple's Destroyer Art uses its master's). */
	public static ProtectedZones.Rules rulesFor(@Nullable ServerPlayer player) {
		return player != null && totalOn(player) ? ProtectedZones.Rules.TOTAL : ProtectedZones.Rules.CLASSIC;
	}

	/** The rules of a new job of {@code owner}; an owner who is not online gets {@code CLASSIC} (the safe side). */
	public static ProtectedZones.Rules rulesFor(UUID owner, MinecraftServer server) {
		return owner == null || server == null ? ProtectedZones.Rules.CLASSIC : rulesFor(server.getPlayerList().getPlayer(owner));
	}

	/**
	 * {@code /kultivasi kehancuran total nyala|mati}: sets the switch; it works at once for new casts and for this player's
	 * queued jobs (a running batch finishes its tick first). Refused with "dimatikan server" when the server forbids it.
	 */
	public static int setTotal(ServerPlayer player, boolean on) {
		if (on && !KultivatorConfig.get().kehancuranTotalAllowed) {
			Msg.chat(player, Component.translatable(TOTAL + "server_off").withColor(MUTED));
			return 0;
		}
		section(player).putBoolean(TOTAL_KEY, on);
		Cultivators.dirty(player);
		WorldBreaker.applyRules(player.getUUID(), rulesFor(player));
		Msg.chat(player, Component.translatable(on ? TOTAL + "on" : TOTAL + "off").withColor(on ? RED : 0x9FF3E6));
		return 1;
	}

	/** The state word of the switch: NYALA, MATI or "dimatikan server". */
	private static Component totalWord(ServerPlayer player) {
		if (!KultivatorConfig.get().kehancuranTotalAllowed) {
			return Component.translatable(TOTAL + "word.server_off").withStyle(ChatFormatting.GRAY);
		}
		return totalOn(player) ? Component.translatable(TOTAL + "word.on").withStyle(ChatFormatting.RED)
				: Component.translatable(TOTAL + "word.off").withStyle(ChatFormatting.GREEN);
	}

	/** {@code status}: "Hancur Total: %s · containers restored with contents: %s". */
	public static Component totalStatus(ServerPlayer player) {
		KultivatorConfig config = KultivatorConfig.get();
		boolean contents = config.kehancuranUndoMinutes > 0 && config.kehancuranDropsPct <= 0 && config.kehancuranUndoBlockEntities > 0;
		return Component.translatable(TOTAL + "status", totalWord(player),
				Component.translatable(contents ? TOTAL + "yes" : TOTAL + "no")).withColor(MUTED);
	}

	/** {@code on_suffix}: " Hancur Total: %s [ubah]", one click flips the switch. */
	private static Component totalSuffix(ServerPlayer player) {
		boolean on = totalOn(player);
		String command = on ? "/kultivasi kehancuran total mati" : "/kultivasi kehancuran total nyala";
		return Component.translatable(TOTAL + "on_suffix", totalWord(player)).withStyle(style -> style.withColor(0xFFB0A0)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(on ? TOTAL + "on" : TOTAL + "off"))));
	}

	/**
	 * The one-click switch (no oath screen, 22.11.1). On: refused with the reason in the action bar and chat unless
	 * {@link #selfAllowed} is null; otherwise one red chat line with [Matikan] and [Pulihkan] and one low heartbeat. Off:
	 * works at once, drops this player's queued self jobs and stops a running one (its blocks stay restorable).
	 */
	public static boolean setSelf(ServerPlayer player, boolean on) {
		if (on) {
			Component why = selfAllowed(player);
			if (why != null) {
				Component line = Component.translatable(SELF + "refused", why).withColor(RED);
				Msg.actionBar(player, line);
				Msg.chat(player, line);
				return false;
			}
		}
		section(player).putBoolean(SELF_KEY, on);
		Cultivators.dirty(player);
		if (on) {
			int minutes = KultivatorConfig.get().kehancuranUndoMinutes;
			MutableComponent off = Component.translatable(SELF + "button.off").withStyle(style -> style.withColor(ChatFormatting.AQUA).withBold(true)
					.withClickEvent(new ClickEvent.RunCommand("/kultivasi kehancuran mati"))
					.withHoverEvent(new HoverEvent.ShowText(Component.translatable(SELF + "button.off_hover"))));
			MutableComponent restore = Component.translatable(SELF + "button.restore").withStyle(style -> style.withColor(0x9FF3E6).withBold(true)
					.withClickEvent(new ClickEvent.RunCommand("/kultivasi kehancuran pulihkan"))
					.withHoverEvent(new HoverEvent.ShowText(Component.translatable(SELF + "button.restore_hover"))));
			MutableComponent line = Component.translatable(SELF + "on_line", Component.translatable(SELF + "on_title").withStyle(ChatFormatting.BOLD),
					Component.literal("/kultivasi kehancuran pulihkan").withStyle(ChatFormatting.BOLD), minutes).withColor(RED);
			line.append(Component.literal(" ")).append(off).append(Component.literal(" ")).append(restore);
			// Phase 30 (30.1.2): the Hancur Total state with its one-click toggle
			line.append(Component.literal(" ")).append(totalSuffix(player));
			Msg.chat(player, line);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 0.5F);
		} else {
			WorldBreaker.cancelSelf(player.level().getServer(), player.getUUID());
			DEFERRED.removeIf(pending -> pending.owner().equals(player.getUUID()));
			Msg.chat(player, Component.translatable(SELF + "off_line").withColor(0x9FF3E6));
		}
		return true;
	}

	// ================================================================== one command for everything (kehancuran semua)

	/** {@code extra.path.kh_all_permit}: {@code kehancuran semua nyala} of this player turned the server permission on. */
	static final String ALL_PERMIT_KEY = "kh_all_permit";

	/**
	 * {@code kehancuran semua nyala|mati} ({@link KehancuranSemua}): the player's own switch (on only while
	 * {@link #selfAllowed} is null), Hancur Total (on only while the server allows it) and Kawah Lebur, without their own
	 * chat lines (the caller prints one summary). Off drops this player's queued self jobs exactly like {@link #setSelf}.
	 */
	static void quietSwitches(ServerPlayer player, boolean on) {
		CompoundTag path = section(player);
		if (!on || selfAllowed(player) == null) {
			path.putBoolean(SELF_KEY, on);
		}
		if (!on || KultivatorConfig.get().kehancuranTotalAllowed) {
			path.putBoolean(TOTAL_KEY, on);
		}
		path.putBoolean(KAWAH_KEY, on);
		Cultivators.dirty(player);
		WorldBreaker.applyRules(player.getUUID(), rulesFor(player));
		if (!on) {
			WorldBreaker.cancelSelf(player.level().getServer(), player.getUUID());
			DEFERRED.removeIf(pending -> pending.owner().equals(player.getUUID()));
		} else if (selfOn(player)) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 0.5F);
		}
	}

	/** The flag of {@link #ALL_PERMIT_KEY}, read and written by {@link KehancuranSemua}. */
	static boolean allPermit(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(DisciplePaths.PLAYER_EXTRA).getBooleanOr(ALL_PERMIT_KEY, false);
	}

	static void setAllPermit(ServerPlayer player, boolean on) {
		section(player).putBoolean(ALL_PERMIT_KEY, on);
		Cultivators.dirty(player);
	}

	/** The stored own switch, whether or not it is allowed now. */
	static boolean selfStored(ServerPlayer player) {
		return Cultivators.get(player).extra().getCompoundOrEmpty(DisciplePaths.PLAYER_EXTRA).getBooleanOr(SELF_KEY, false);
	}

	/**
	 * The server permission ({@code modeKehancuran}) as {@code kehancuran izin on|off} sets it, without its broadcast;
	 * off cancels every order and job like {@code izin off}. Returns false when the config could not be written.
	 */
	static boolean setPermissionQuiet(MinecraftServer server, boolean on) {
		KultivatorConfig.get().modeKehancuran = on;
		boolean saved = KultivatorConfig.save();
		if (!on) {
			for (Order order : List.copyOf(ORDERS.values())) {
				cancel(server, order, "cancel.lock");
			}
			WorldBreaker.cancelAll(server);
		}
		return saved;
	}

	/** {@code kultivator.kehancuran_diri.}: the player's own switch (22.11). */
	private static final String SELF = "kultivator.kehancuran_diri.";
	/** Submits built per server tick at most, the waiting queue and how long a submit may wait (22.11.5). */
	private static final int BUILDS_PER_TICK = 2;
	private static final int DEFERRED_MAX = 8;
	private static final int DEFERRED_TICKS = 20;
	/** At most one QUEUE_FULL / ALL_PROTECTED line per player per 5 s. */
	private static final int NOTE_TICKS = 100;
	/**
	 * Placed shapes one submit builds at most: a cast's ≤ 5 KH shapes (22.11.3) resolved in the same tick go together,
	 * SCATTER and ×n among them with up to 12 anchors.
	 */
	private static final int MAX_SHAPES = TechniquePower.SCATTER_POINTS + TechniquePower.MAX_DESTRUCTION_SHAPES;
	/** Phase 10 (DESIGN 28.9.3): the sigil trench lines one cast may add on top (they share the cast's block cap). */
	public static final int MAX_SIGIL_TRENCHES = 24;
	/** {@code extra.path.kawah}: the player's Kawah Lebur switch (28.8.1, default on), stored with the self switch. */
	public static final String KAWAH_KEY = "kawah";

	/** A submit waiting for a build slot (it is dropped when the caster left that dimension meanwhile). */
	private record PendingSelf(UUID owner, String specId, List<Shapes.Placed> shapes, int blockCap, long created,
			net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
			dev.kultivator.technique.spec.dsl.Dampak.@Nullable Palette palette, @Nullable TitahPlan titah) {
	}

	/**
	 * Phase 10b (DESIGN 29.7.1, contract C10b.3): the plan of one Titah Langit carve. {@code blockCap} the cast's own
	 * block cap ({@code titahBlocksPerCast}), {@code carveRadius} the real carve radius (for the readout and the log),
	 * {@code palette} the Kawah Lebur palette (NONE = no surface pass), {@code debrisFlags} the {@code DampakDebris} motion
	 * flags, {@code recordId} the {@code TitahMonuments} record (-1 = none), {@code monumentPositions} the planned block
	 * monument cells as {@code BlockPos.asLong(x, layer, z)} (layer 0 = the first air on the crater floor of that column;
	 * the planted point's own column is never in it) and {@code monumentKinds} 0 Batu Pusaka / 1 Urat Pusaka per cell
	 * (both may be empty: no block monument).
	 */
	public record TitahPlan(int blockCap, int carveRadius, dev.kultivator.technique.spec.dsl.Dampak.Palette palette, int debrisFlags,
			int recordId, long[] monumentPositions, byte[] monumentKinds) {
	}

	private static final List<PendingSelf> DEFERRED = new ArrayList<>();
	private static final Map<UUID, Long> NOTED = new HashMap<>();
	private static long buildTick = Long.MIN_VALUE;
	private static int builds;

	/**
	 * The only entry for destruction by the player's own techniques (22.11.5). Never throws, spends, refunds or delays
	 * anything: the damage has already happened. Re-checks the switch and the realm (OFF), builds at most 2 submits per
	 * server tick (others wait up to 20 ticks in a queue of 8: DEFERRED), merges and cuts the shapes to {@code blockCap}
	 * in proportion, drops protected and never-touched positions and queues one {@link WorldBreaker} job.
	 */
	public static SelfResult submitSelf(ServerPlayer caster, String specId, List<Shapes.Placed> shapes, int blockCap) {
		return submitSelf(caster, specId, shapes, blockCap, null);
	}

	/**
	 * As {@link #submitSelf(ServerPlayer, String, List, int)} with the Kawah Lebur palette of the job (Phase 10, DESIGN
	 * 28.8; null = the spec's own {@code Dampak.palette()}). The spec's net id, debris flags and palette go into the job;
	 * the surface pass runs only when the server allows it and the player's {@code kawah} switch is on.
	 */
	public static SelfResult submitSelf(ServerPlayer caster, String specId, List<Shapes.Placed> shapes, int blockCap,
			dev.kultivator.technique.spec.dsl.Dampak.@Nullable Palette palette) {
		try {
			if (!selfOn(caster) || blockCap <= 0 || shapes.isEmpty()) {
				return SelfResult.OFF;
			}
			if (TechniquePower.kScale(Cultivators.get(caster)) <= 0.0D) {
				return SelfResult.OFF;
			}
			long now = caster.level().getServer().getTickCount();
			if (buildTick != now) {
				buildTick = now;
				builds = 0;
			}
			int maxShapes = MAX_SHAPES + MAX_SIGIL_TRENCHES;
			List<Shapes.Placed> list = shapes.size() > maxShapes ? List.copyOf(shapes.subList(0, maxShapes)) : List.copyOf(shapes);
			if (builds >= BUILDS_PER_TICK) {
				if (DEFERRED.size() >= DEFERRED_MAX) {
					note(caster, "queue_full");
					return SelfResult.QUEUE_FULL;
				}
				DEFERRED.add(new PendingSelf(caster.getUUID(), specId, list, blockCap, now, caster.level().dimension(), palette, null));
				return SelfResult.DEFERRED;
			}
			builds++;
			return build(caster, specId, list, blockCap, palette, null);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Kultivator: Mode Kehancuran self cast of {} failed", specId, exception);
			return SelfResult.NOTHING;
		}
	}

	/**
	 * Phase 10b (DESIGN 29.7.1, contract C10b.3): the only entry for a Titah Langit carve. Never throws, spends or refunds:
	 * the decree has already struck. Re-checks the caster's own switch ({@link #selfOn}), the realm (K above 0) and
	 * {@code titahEnabled}; one Titah job per owner and the 2-per-owner rule hold (QUEUE_FULL: "Bumi masih runtuh —
	 * kehancuran dilewati", the strike stays visual); then builds like {@link #submitSelf} (at most 2 builds per tick, a
	 * DEFERRED queue of 8) with the plan's own cap, 0 % drops, no merge and the monument phase.
	 */
	public static SelfResult submitTitah(ServerPlayer caster, String specId, List<Shapes.Placed> shapes, TitahPlan plan) {
		try {
			if (plan == null || shapes == null || shapes.isEmpty() || plan.blockCap() <= 0 || !KultivatorConfig.get().titahEnabled
					|| !selfOn(caster)) {
				return SelfResult.OFF;
			}
			if (TechniquePower.kScale(Cultivators.get(caster)) <= 0.0D) {
				return SelfResult.OFF;
			}
			if (WorldBreaker.hasTitah(caster.getUUID()) || WorldBreaker.ownerJobs(caster.getUUID()) >= WorldBreaker.MAX_PER_OWNER
					|| !WorldBreaker.hasRoom()) {
				titahNote(caster);
				return SelfResult.QUEUE_FULL;
			}
			for (PendingSelf pending : DEFERRED) {
				if (pending.titah() != null && pending.owner().equals(caster.getUUID())) {
					titahNote(caster);
					return SelfResult.QUEUE_FULL;
				}
			}
			long now = caster.level().getServer().getTickCount();
			if (buildTick != now) {
				buildTick = now;
				builds = 0;
			}
			int maxShapes = MAX_SHAPES + MAX_SIGIL_TRENCHES;
			List<Shapes.Placed> list = shapes.size() > maxShapes ? List.copyOf(shapes.subList(0, maxShapes)) : List.copyOf(shapes);
			if (builds >= BUILDS_PER_TICK) {
				if (DEFERRED.size() >= DEFERRED_MAX) {
					titahNote(caster);
					return SelfResult.QUEUE_FULL;
				}
				DEFERRED.add(new PendingSelf(caster.getUUID(), specId, list, plan.blockCap(), now, caster.level().dimension(), plan.palette(), plan));
				return SelfResult.DEFERRED;
			}
			builds++;
			return build(caster, specId, list, plan.blockCap(), plan.palette(), plan);
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Kultivator: Titah carve of {} failed", specId, exception);
			return SelfResult.NOTHING;
		}
	}

	/**
	 * Phase 10b (DESIGN 29.16.6, contract of S): the plan of one fast bulk carve. {@code tier} the Tingkat (2-9; the
	 * {@code pahat_cepat} test passes 2 or 6), {@code permanent} Tingkat 6-9 (no undo, logged), {@code confirmed} the
	 * chat confirmation was clicked (required for a permanent carve), {@code palette} the Kawah Lebur palette (applied
	 * only with the player's {@code kawah} switch and {@code kehancuranSurfacePass}), {@code debrisFlags} the
	 * {@code DampakDebris} motion flags, {@code recordId} the planted weapon's record (-1 = none) and {@code castPos}
	 * where the caster stood.
	 */
	public record TitahBulk(int tier, boolean permanent, boolean confirmed, dev.kultivator.technique.spec.dsl.Dampak.Palette palette, int debrisFlags,
			int recordId, Vec3 castPos) {
	}

	/**
	 * Phase 10b (DESIGN 29.16.6): the only entry for a fast bulk carve. Never throws, spends or refunds (the decree has
	 * struck). Re-checks {@code titahEnabled}, the caster's own switch, the realm (K above 0) and "permanent needs the
	 * confirmation"; an undoable carve with {@code titahUndoMaxBlocks = 0} carves nothing (never silently permanent). One
	 * Titah job per owner and 1 running plus 1 queued bulk job server-wide, else QUEUE_FULL with "Bumi masih runtuh —
	 * kehancuran dilewati". The job is planned and carved by {@link WorldBreaker} (time-budgeted, chunk by chunk).
	 */
	public static SelfResult submitTitahBulk(ServerPlayer caster, String specId, BulkShape shape, TitahBulk plan) {
		try {
			KultivatorConfig config = KultivatorConfig.get();
			if (plan == null || shape == null || shape.isEmpty() || !config.titahEnabled || !selfOn(caster)) {
				return SelfResult.OFF;
			}
			if (TechniquePower.kScale(Cultivators.get(caster)) <= 0.0D) {
				return SelfResult.OFF;
			}
			if (plan.permanent() && !plan.confirmed()) {
				return SelfResult.OFF;
			}
			if (!plan.permanent() && (config.titahUndoMaxBlocks <= 0 || config.kehancuranUndoMinutes <= 0)) {
				// 29.16.12: with no undo room Tingkat 2-5 carve nothing; they never turn permanent on their own
				Msg.actionBar(caster, Component.translatable("kultivator.kehancuran.bulk.no_undo").withColor(0xFFB0A0));
				return SelfResult.OFF;
			}
			if (WorldBreaker.hasTitah(caster.getUUID()) || !WorldBreaker.bulkHasRoom()) {
				titahNote(caster);
				return SelfResult.QUEUE_FULL;
			}
			for (PendingSelf pending : DEFERRED) {
				if (pending.titah() != null && pending.owner().equals(caster.getUUID())) {
					titahNote(caster);
					return SelfResult.QUEUE_FULL;
				}
			}
			WorldBreaker.BulkJob job = WorldBreaker.BulkJob.carve(caster, specId, shape, Math.max(2, Math.min(9, plan.tier())), plan.permanent(),
					plan.castPos() == null ? caster.position() : plan.castPos());
			dev.kultivator.data.TechniqueSpec spec = dev.kultivator.data.TechniqueRegistry.byId(specId);
			job.specNet = spec == null ? -1 : spec.netId();
			job.debrisFlags = plan.debrisFlags();
			job.recordId = plan.recordId();
			job.palette = plan.palette() != null && kawahOn(caster) && config.kehancuranSurfacePass ? plan.palette()
					: dev.kultivator.technique.spec.dsl.Dampak.Palette.NONE;
			job.rules = rulesFor(caster);
			if (!WorldBreaker.submitBulk(job)) {
				titahNote(caster);
				return SelfResult.QUEUE_FULL;
			}
			if (plan.permanent() && job.rules.total()) {
				// Phase 30 (30.1.2): containers in a permanent carve are lost with their contents
				Msg.chat(caster, Component.translatable(TOTAL + "permanent_be").withColor(0xFF8A7A));
			}
			BlockPos centre = shape.impact();
			Fx.send(caster.level(), Vec3.atCenterOf(centre), FxType.KEHANCURAN, KehancuranFxIds.IMPACT, -1, (float) Math.min(64, Math.max(2, shape.outer())));
			caster.level().playSound(null, centre.getX(), centre.getY(), centre.getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.6F, 0.5F);
			Advancements.grant(caster, "world_breaker_self");
			return SelfResult.QUEUED;
		} catch (RuntimeException exception) {
			dev.kultivator.Kultivator.LOGGER.error("Kultivator: Titah bulk carve of {} failed", specId, exception);
			return SelfResult.NOTHING;
		}
	}

	/** The running bulk carve for the test command's {@code pahat_cepat info} (29.16.15), or null. */
	public static WorldBreaker.@Nullable BulkInfo bulkInfo() {
		return WorldBreaker.bulkInfo();
	}

	/** {@code /kultivasi kehancuran batal} without a disciple order: stops the caller's own bulk carve. Returns how many. */
	public static int cancelBulk(ServerPlayer player) {
		int stopped = WorldBreaker.cancelBulk(player.level().getServer(), player.getUUID());
		Msg.chat(player, Component.translatable(stopped > 0 ? "kultivator.kehancuran.bulk.cancelled" : "kultivator.kehancuran.bulk.none")
				.withColor(stopped > 0 ? 0x9FF3E6 : MUTED));
		return stopped;
	}

	/** "Bumi masih runtuh — kehancuran dilewati" (the decree stays visual), at most once per 5 s per player. */
	private static void titahNote(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = NOTED.get(player.getUUID());
		if (last != null && now >= last && now - last < NOTE_TICKS) {
			return;
		}
		NOTED.put(player.getUUID(), now);
		Msg.actionBar(player, Component.translatable("kultivator.pusaka_langit.carve.busy").withColor(0xFFB0A0));
	}

	/** Builds the deferred submits that fit this tick (oldest first); older than 20 ticks are dropped. */
	private static void tickDeferred(MinecraftServer server) {
		if (DEFERRED.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		if (buildTick != now) {
			buildTick = now;
			builds = 0;
		}
		while (!DEFERRED.isEmpty() && builds < BUILDS_PER_TICK) {
			PendingSelf pending = DEFERRED.remove(0);
			if (now - pending.created() > DEFERRED_TICKS) {
				continue;
			}
			ServerPlayer caster = server.getPlayerList().getPlayer(pending.owner());
			if (caster == null || !selfOn(caster) || !caster.isAlive() || !caster.level().dimension().equals(pending.dimension())) {
				// the anchors belong to the cast's dimension: never carve the same coordinates somewhere else
				continue;
			}
			builds++;
			try {
				build(caster, pending.specId(), pending.shapes(), pending.blockCap(), pending.palette(), pending.titah());
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: Mode Kehancuran self cast of {} failed", pending.specId(), exception);
			}
		}
	}

	/**
	 * One build: every shape's ordered positions (read-only geometry), duplicates removed, each shape cut to its share of
	 * the cap (in proportion to its uncut size), protected and never-touched positions dropped; at most 4 x cap reads.
	 */
	private static SelfResult build(ServerPlayer caster, String specId, List<Shapes.Placed> shapes, int blockCap,
			dev.kultivator.technique.spec.dsl.Dampak.@Nullable Palette palette, @Nullable TitahPlan titah) {
		ServerLevel level = caster.level();
		// Phase 30 (30.1.5): the owner's Hancur Total switch now; the job carries the same rules
		ProtectedZones.Rules rules = rulesFor(caster);
		boolean hancur = rules.total();
		List<List<Shapes.Change>> raws = new ArrayList<>(shapes.size());
		long total = 0L;
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		// 22.15: at most 4 × cap block reads per build. The shape geometry (surface reads, the scans of RING, STRIP, WITHER
		// and the impact search) may use up to 2 × cap of them; the merge below gets what is left plus one cap, and the
		// open-top column check at most one read per planned column (≤ cap).
		int geometryBudget = 2 * blockCap;
		for (Shapes.Placed placed : shapes) {
			List<Shapes.Change> raw = Shapes.build(level, placed.spec(), placed.anchor(), placed.direction(), geometryBudget, rules);
			geometryBudget = Math.max(0, geometryBudget - Shapes.lastReads());
			raws.add(raw);
			total += raw.size();
			for (Shapes.Change change : raw) {
				BlockPos pos = change.pos();
				minX = Math.min(minX, pos.getX());
				minY = Math.min(minY, pos.getY());
				minZ = Math.min(minZ, pos.getZ());
				maxX = Math.max(maxX, pos.getX());
				maxY = Math.max(maxY, pos.getY());
				maxZ = Math.max(maxZ, pos.getZ());
			}
		}
		if (total == 0L) {
			return SelfResult.NOTHING;
		}
		AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
		ProtectedZones.Context zones = new ProtectedZones.Context(level, box, caster.getUUID(), List.of(caster.position()));
		List<Long> positions = new ArrayList<>();
		List<Byte> modes = new ArrayList<>();
		List<Boolean> checks = new ArrayList<>();
		List<Boolean> fluidChecks = new ArrayList<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		// Per column: the highest solid block that has to stay; nothing under it is planned (no sealed hollows).
		java.util.Map<Long, Integer> kept = new java.util.HashMap<>();
		int protectedCount = 0;
		int candidates = 0;
		int readsLeft = blockCap + geometryBudget;
		for (int s = 0; s < shapes.size() && readsLeft > 0; s++) {
			List<Shapes.Change> raw = raws.get(s);
			Shapes.Spec spec = shapes.get(s).spec();
			int quota = (int) Math.max(1L, Math.round((double) blockCap * raw.size() / total));
			byte mode = spec.kind() == Shapes.Kind.WITHER ? WorldBreaker.MODE_WITHER : spec.kind() == Shapes.Kind.STRIP ? WorldBreaker.MODE_STRIP
					: spec.naturalOnly() ? WorldBreaker.MODE_NATURAL : WorldBreaker.MODE_REMOVE;
			int taken = 0;
			for (Shapes.Change change : raw) {
				if (taken >= quota || readsLeft <= 0 || positions.size() >= blockCap) {
					break;
				}
				BlockPos pos = change.pos();
				if (!seen.add(pos.asLong())) {
					continue;
				}
				readsLeft--;
				ProtectedZones.Zone zone = zones.zoneOf(pos);
				if (zone == ProtectedZones.Zone.UNLOADED) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if (state.isAir()) {
					continue;
				}
				candidates++;
				boolean carves = mode == WorldBreaker.MODE_REMOVE || mode == WorldBreaker.MODE_NATURAL;
				long column = Shapes.columnKey(pos);
				if (zone != null) {
					protectedCount++;
					if (carves && (Shapes.roofs(state) || hancur)) {
						kept.merge(column, pos.getY(), Math::max);
					}
					continue;
				}
				boolean never = ProtectedZones.neverTouch(level, pos, state, rules);
				if (never || mode == WorldBreaker.MODE_NATURAL && !Shapes.natural(state)
						|| mode == WorldBreaker.MODE_STRIP && !state.is(Shapes.STRIPPABLE)
						|| mode == WorldBreaker.MODE_WITHER && Shapes.witherInto(state) == null
						|| carves && kept.getOrDefault(column, Integer.MIN_VALUE) > pos.getY()) {
					// Hancur Total: a keep-list block keeps its column below whatever its shape (30.1.1)
					if (carves && (Shapes.roofs(state) || hancur && never)) {
						kept.merge(column, pos.getY(), Math::max);
					}
					continue;
				}
				positions.add(pos.asLong());
				modes.add(mode);
				checks.add(carves && !Shapes.pointBlast(spec.kind()));
				// Hancur Total point blasts: never leave a fluid hanging over the hole (30.1.3 item 3)
				fluidChecks.add(hancur && carves && Shapes.pointBlast(spec.kind()));
				taken++;
			}
		}
		if (positions.isEmpty()) {
			if (candidates > 0 && protectedCount >= candidates) {
				note(caster, "all_protected");
				return SelfResult.ALL_PROTECTED;
			}
			return SelfResult.NOTHING;
		}
		// A column whose top planned block sits under a solid, unplanned block would become a sealed hollow: drop it.
		List<BlockPos> planned = new ArrayList<>(positions.size());
		boolean[] check = new boolean[positions.size()];
		boolean[] fluidCheck = new boolean[positions.size()];
		for (int i = 0; i < positions.size(); i++) {
			planned.add(BlockPos.of(positions.get(i)));
			check[i] = checks.get(i);
			fluidCheck[i] = fluidChecks.get(i);
		}
		List<Integer> open = Shapes.openColumns(level, planned, check, hancur ? fluidCheck : null);
		if (open.isEmpty()) {
			return SelfResult.NOTHING;
		}
		long[] packed = new long[open.size()];
		byte[] modeArray = new byte[open.size()];
		for (int i = 0; i < packed.length; i++) {
			packed[i] = positions.get(open.get(i));
			modeArray[i] = modes.get(open.get(i));
		}
		// Phase 30 (30.1.3 item 2): overlapping shapes still work each column top-down
		Shapes.topDown(packed, modeArray);
		Shapes.Placed first = shapes.get(0);
		BlockPos centre = first.anchor();
		WorldBreaker.Job job = titah != null ? WorldBreaker.Job.titah(caster, specId, first.spec().kind().id(), centre, packed, modeArray, blockCap)
				: WorldBreaker.Job.self(caster, specId, first.spec().kind().id(), centre, packed, modeArray);
		job.rules = rules;
		// Phase 10 (DESIGN 28.6.1, 28.8.1): debris flags, the spec's net id and the Kawah Lebur palette ride in the job
		dev.kultivator.data.TechniqueSpec spec = dev.kultivator.data.TechniqueRegistry.byId(specId);
		dev.kultivator.technique.spec.dsl.Dampak dampak = spec == null ? null : spec.dampak();
		job.specNet = spec == null ? -1 : spec.netId();
		job.debrisFlags = dampak == null ? 0 : dampak.debrisFlags();
		dev.kultivator.technique.spec.dsl.Dampak.Palette chosen = palette != null ? palette
				: dampak == null ? dev.kultivator.technique.spec.dsl.Dampak.Palette.NONE : dampak.palette();
		if (palette == null && dampak != null && dampak.vista() == dev.kultivator.technique.spec.dsl.Dampak.Vista.BY_DAO) {
			// own arts (28.12): the palette of the creation's first Dao
			dev.kultivator.batin.Creation.Art art = dev.kultivator.batin.Creation.art(caster, dev.kultivator.batin.CiptaanArts.slotOf(specId));
			if (art != null) {
				chosen = Surface.forDao(art.a());
			}
		}
		job.palette = kawahOn(caster) && KultivatorConfig.get().kehancuranSurfacePass ? chosen : dev.kultivator.technique.spec.dsl.Dampak.Palette.NONE;
		if (titah != null) {
			// Phase 10b (29.7.1): the plan's debris flags, record and block monument (built only by this job, 29.6.3)
			job.debrisFlags = titah.debrisFlags();
			job.recordId = titah.recordId();
			if (titah.monumentPositions() != null && titah.monumentKinds() != null && titah.monumentPositions().length > 0) {
				job.monumentPlan = titah.monumentPositions().clone();
				job.monumentKinds = titah.monumentKinds().clone();
			}
		}
		if (!WorldBreaker.submit(job)) {
			if (titah != null) {
				titahNote(caster);
			} else {
				note(caster, "queue_full");
			}
			return SelfResult.QUEUE_FULL;
		}
		float radius = Math.max(2.0F, first.spec().radius() + 1.0F);
		Fx.send(level, Vec3.atCenterOf(centre), FxType.KEHANCURAN, KehancuranFxIds.IMPACT, -1, radius);
		level.playSound(null, centre.getX(), centre.getY(), centre.getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.6F, 0.5F);
		Advancements.grant(caster, "world_breaker_self");
		return SelfResult.QUEUED;
	}

	/** The QUEUE_FULL / ALL_PROTECTED action-bar line, at most once per 5 s per player. */
	private static void note(ServerPlayer player, String key) {
		long now = player.level().getGameTime();
		Long last = NOTED.get(player.getUUID());
		if (last != null && now >= last && now - last < NOTE_TICKS) {
			return;
		}
		NOTED.put(player.getUUID(), now);
		Msg.actionBar(player, Component.translatable(SELF + key).withColor(0xFFB0A0));
	}

	/**
	 * The Kehancuran page's status ({@code sessionExtra.teknik.kh}, 22.12.6): {@code allowed}, {@code why} (a lang key),
	 * {@code on}, {@code realm} (K > 0), {@code k}, {@code cap}, {@code r}, {@code queued}, {@code running}, {@code undo}
	 * [[blocks, seconds]], {@code safe}, {@code home} and the last self casts in {@code log} [{spec, blocks, x, y, z}].
	 */
	public static CompoundTag selfStatus(ServerPlayer player) {
		KultivatorConfig config = KultivatorConfig.get();
		CompoundTag tag = new CompoundTag();
		String why = !config.kehancuranPlayerTechniques ? SELF + "why.config"
				: config.modeKehancuran || player.level().getServer().isSingleplayerOwner(player.nameAndId()) ? "" : SELF + "why.permission";
		tag.putBoolean("allowed", why.isEmpty());
		if (!why.isEmpty()) {
			tag.putString("why", why);
		}
		dev.kultivator.data.CultivationData data = Cultivators.get(player);
		boolean stored = data.extra().getCompoundOrEmpty(DisciplePaths.PLAYER_EXTRA).getBooleanOr(SELF_KEY, false);
		tag.putBoolean("on", stored && why.isEmpty());
		double k = TechniquePower.kScale(data);
		tag.putBoolean("realm", k > 0.0D);
		tag.putDouble("k", Math.round(k * 100.0D) / 100.0D);
		tag.putInt("cap", TechniquePower.blockCap(data));
		tag.putInt("r", config.kehancuranMaxRadius);
		int[] state = WorldBreaker.selfState(player.getUUID());
		tag.putInt("queued", state[0]);
		tag.putBoolean("running", state[1] > 0);
		ListTag undo = new ListTag();
		for (int[] entry : WorldBreaker.undoable(player.level().getServer(), player.getUUID())) {
			ListTag pair = new ListTag();
			pair.add(IntTag.valueOf(entry[0]));
			pair.add(IntTag.valueOf(entry[1] / 10 * 10));
			undo.add(pair);
		}
		tag.put("undo", undo);
		tag.putInt("safe", config.kehancuranCasterSafeRadius);
		tag.putInt("home", config.kehancuranHomeSafeRadius);
		// Phase 30 (30.1.2): the Hancur Total switch (server read-only data; no client reads it yet)
		tag.putBoolean("total", totalOn(player));
		tag.putBoolean("total_allowed", config.kehancuranTotalAllowed);
		ListTag log = new ListTag();
		for (CompoundTag line : KehancuranLog.recent(player.level().getServer(), player.getUUID(), 12)) {
			if (!line.getBooleanOr("self", false) || log.size() >= 6) {
				continue;
			}
			CompoundTag entry = new CompoundTag();
			entry.putString("spec", line.getStringOr("spec", ""));
			entry.putInt("blocks", line.getIntOr("blocks", 0));
			entry.putInt("x", line.getIntOr("x", 0));
			entry.putInt("y", line.getIntOr("y", 0));
			entry.putInt("z", line.getIntOr("z", 0));
			log.add(entry);
		}
		tag.put("log", log);
		return tag;
	}

	/** {running, queued, self jobs} of the WorldBreaker (for {@code debug teknik beban}). */
	public static int[] jobCounts() {
		return WorldBreaker.counts();
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
			// Phase 6c (DESIGN 22.11.1, 22.12.6): the player's own one-click switch and its undo.
			case "self_on" -> setSelf(player, true);
			case "self_off" -> setSelf(player, false);
			case "self_undo" -> restore(player);
			// Phase 30 (DESIGN 30.1.2): the Hancur Total switch (for a page button; the commands do the same)
			case "total_on" -> setTotal(player, true);
			case "total_off" -> setTotal(player, false);
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
			// Phase 10b (29.16.9): Tingkat 6-9 carves are permanent and say so
			int permanent = WorldBreaker.permanentInWindow(player.level().getServer(), player.getUUID());
			if (permanent > 0) {
				Msg.chat(player, Component.translatable("kultivator.kehancuran.bulk.restore_permanent", permanent).withColor(0xFF8A7A));
				return;
			}
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
		if (dev.kultivator.alam.Alam.blocked(master) || dev.kultivator.alam.Alam.isRealmLevel(entity.level())) {
			// Phase 8 (DESIGN 25.2 item 12): no destructive order in or from a secret realm.
			Msg.chat(master, dev.kultivator.alam.Alam.refusal().withColor(RED));
			return;
		}
		if (ORDERS.containsKey(master.getUUID())) {
			Msg.chat(master, Component.translatable(PREFIX + "why.order_running").withColor(RED));
			return;
		}
		if (!WorldBreaker.hasRoom(master.getUUID())) {
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
		tickDeferred(server);
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
		// Phase 30 (30.1.5): validated with the master's Hancur Total rules, as the job will run
		ProtectedZones.Rules rules = rulesFor(master);
		List<Shapes.Change> raw = Shapes.build(level, spec, aimed, direction, Integer.MAX_VALUE, rules);
		if (raw.isEmpty()) {
			Msg.chat(master, Component.translatable(PREFIX + "why.empty").withColor(RED));
			return;
		}
		AABB box = bounds(raw);
		ProtectedZones.Context zones = new ProtectedZones.Context(level, box);
		List<Shapes.Change> plan = new ArrayList<>();
		java.util.Map<Long, Integer> kept = new java.util.HashMap<>();
		boolean carves = spec.kind() != Shapes.Kind.WITHER && spec.kind() != Shapes.Kind.STRIP;
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
			long column = Shapes.columnKey(change.pos());
			boolean never = ProtectedZones.neverTouch(level, change.pos(), state, rules);
			if (never || spec.naturalOnly() && !Shapes.natural(state)
					|| carves && kept.getOrDefault(column, Integer.MIN_VALUE) > change.pos().getY()) {
				if (carves && (Shapes.roofs(state) || rules.total() && never && !state.isAir())) {
					kept.merge(column, change.pos().getY(), Math::max);
				}
				continue;
			}
			if (state.isAir()) {
				continue;
			}
			plan.add(change);
			if (plan.size() >= config.kehancuranMaxBlocksPerCast) {
				break;
			}
		}
		if (carves && !Shapes.pointBlast(spec.kind()) && !plan.isEmpty()) {
			List<BlockPos> planned = new ArrayList<>(plan.size());
			boolean[] check = new boolean[plan.size()];
			for (int i = 0; i < plan.size(); i++) {
				planned.add(plan.get(i).pos());
				check[i] = plan.get(i).to() == null;
			}
			List<Shapes.Change> open = new ArrayList<>(plan.size());
			for (int i : Shapes.openColumns(level, planned, check)) {
				open.add(plan.get(i));
			}
			plan = open;
		} else if (carves && rules.total() && !plan.isEmpty()) {
			// Hancur Total point blast: no fluid left hanging over the hole (30.1.3 item 3)
			List<BlockPos> planned = new ArrayList<>(plan.size());
			boolean[] check = new boolean[plan.size()];
			boolean[] fluidCheck = new boolean[plan.size()];
			for (int i = 0; i < plan.size(); i++) {
				planned.add(plan.get(i).pos());
				fluidCheck[i] = plan.get(i).to() == null;
			}
			List<Shapes.Change> open = new ArrayList<>(plan.size());
			for (int i : Shapes.openColumns(level, planned, check, fluidCheck)) {
				open.add(plan.get(i));
			}
			plan = open;
		}
		plan = Shapes.topDownChanges(plan);
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
				CONFIRM_TICKS / 20).withColor(RED).append(Component.literal(" · ")).append(Component.translatable(rules.total() ? TOTAL + "is_on" : TOTAL + "is_off")));
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
		dev.kultivator.epic.EpicHooks.kehancuran(level, master, order.entity, Vec3.atCenterOf(c), order.art.ordinal(), (float) DestroyerArts.spec(order.art, DisciplePaths.stars(order.entity.data())).radius(), dev.kultivator.epic.EpicIds.KEH_WARNING, seconds);
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
		// Phase 10 (DESIGN 28.13.1): the Destroyer Art's debris fly from the disciple's crater too (BURST, no palette)
		job.casterEntityId = entity.getId();
		// Phase 30 (30.1.5): a disciple's Destroyer Art follows its master's Hancur Total switch
		job.rules = rulesFor(master);
		if (!WorldBreaker.submit(job)) {
			Msg.chat(master, Component.translatable(PREFIX + "why.queue_full").withColor(RED));
			return;
		}
		Fx.send(level, Vec3.atCenterOf(centre), FxType.KEHANCURAN, KehancuranFxIds.IMPACT, order.art.ordinal(), spec.radius() + 2.0F);
		dev.kultivator.epic.EpicHooks.kehancuran(level, master, entity, Vec3.atCenterOf(centre), order.art.ordinal(), (float) spec.radius(), dev.kultivator.epic.EpicIds.KEH_IMPACT, positions.length);
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
				dev.kultivator.epic.EpicHooks.kehancuran(level, master, order.entity, order.centre == null ? order.entity.position() : Vec3.atCenterOf(order.centre), order.art.ordinal(), 0.0F, dev.kultivator.epic.EpicIds.KEH_ABORT, 0);
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
				// One command for everything: the summary with [Nyalakan semua] / [Matikan semua] (KehancuranSemua).
				.executes(ctx -> KehancuranSemua.summary(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("semua")
						.executes(ctx -> KehancuranSemua.summary(ctx.getSource().getPlayerOrException()))
						.then(Commands.literal("nyala").executes(ctx -> KehancuranSemua.set(ctx.getSource().getPlayerOrException(), true)))
						.then(Commands.literal("mati").executes(ctx -> KehancuranSemua.set(ctx.getSource().getPlayerOrException(), false))))
				.then(Commands.literal("sumpah").executes(ctx -> {
					openOath(ctx.getSource().getPlayerOrException());
					return 1;
				}))
				// Phase 6c (DESIGN 22.11.1): the player's own one-click switch, no oath.
				.then(Commands.literal("nyala").executes(ctx -> setSelf(ctx.getSource().getPlayerOrException(), true) ? 1 : 0))
				.then(Commands.literal("mati").executes(ctx -> setSelf(ctx.getSource().getPlayerOrException(), false) ? 1 : 0))
				// Phase 10 (DESIGN 28.8.1): the molten crater of the player's own casts
				.then(Commands.literal("kawah")
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							Msg.chat(player, Component.translatable(kawahOn(player) ? "kultivator.dampak_server.kawah.is_on" : "kultivator.dampak_server.kawah.is_off")
									.withColor(MUTED));
							return 1;
						})
						.then(Commands.literal("nyala").executes(ctx -> setKawah(ctx.getSource().getPlayerOrException(), true)))
						.then(Commands.literal("mati").executes(ctx -> setKawah(ctx.getSource().getPlayerOrException(), false))))
				// Phase 30 (DESIGN 30.1.2): Hancur Total, the player's own switch (no op)
				.then(Commands.literal("total")
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							Msg.chat(player, totalStatus(player));
							return 1;
						})
						.then(Commands.literal("nyala").executes(ctx -> setTotal(ctx.getSource().getPlayerOrException(), true)))
						.then(Commands.literal("mati").executes(ctx -> setTotal(ctx.getSource().getPlayerOrException(), false))))
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
						// Phase 10b (29.16.7 "Cancel"): no disciple order, so the caller's own bulk carve stops
						return cancelBulk(player) > 0 ? 1 : 0;
					}
					cancel(player.level().getServer(), order, "cancel.master");
					return 1;
				}))
				.then(Commands.literal("izin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("on").executes(ctx -> permit(ctx.getSource(), true)))
						.then(Commands.literal("off").executes(ctx -> permit(ctx.getSource(), false)))));
		// The short alias of "kehancuran semua nyala|mati".
		root.then(Commands.literal("hancur")
				.executes(ctx -> KehancuranSemua.summary(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("nyala").executes(ctx -> KehancuranSemua.set(ctx.getSource().getPlayerOrException(), true)))
				.then(Commands.literal("mati").executes(ctx -> KehancuranSemua.set(ctx.getSource().getPlayerOrException(), false))));
		// Phase 30 (DESIGN 30.6): uji kehancuran total ... (op). Brigadier merges this "uji" literal with the test
		// registry's own "uji" node, whichever is registered first; both require the gamemaster level.
		root.then(Commands.literal("uji").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("kehancuran")
						.then(Commands.literal("total")
								.then(Commands.literal("info").executes(ctx -> testInfo(ctx.getSource().getPlayerOrException())))
								.then(Commands.literal("uji")
										.then(Commands.literal("bedrock").executes(ctx -> testSite(ctx.getSource().getPlayerOrException(), "bedrock")))
										.then(Commands.literal("cairan").executes(ctx -> testSite(ctx.getSource().getPlayerOrException(), "cairan")))
										.then(Commands.literal("peti").executes(ctx -> testSite(ctx.getSource().getPlayerOrException(), "peti")))
										.then(Commands.literal("portal").executes(ctx -> testSite(ctx.getSource().getPlayerOrException(), "portal")))))));
	}

	// ================================================================== Phase 30 test commands (DESIGN 30.6)

	private static final String TEST = "kultivator.kehancuran_total.test.";

	/** {@code uji kehancuran total info}: the rules of the next own cast and the saved container tags of the undo window. */
	private static int testInfo(ServerPlayer player) {
		KultivatorConfig config = KultivatorConfig.get();
		ProtectedZones.Rules rules = rulesFor(player);
		Msg.chat(player, Component.translatable(TEST + "info.rules", Component.translatable(rules.total() ? TOTAL + "word.on" : TOTAL + "word.off"),
				Component.translatable(selfOn(player) ? TOTAL + "yes" : TOTAL + "no"),
				Component.translatable(config.kehancuranTotalAllowed ? TOTAL + "yes" : TOTAL + "no")).withColor(0xFFB0A0));
		long[] stats = WorldBreaker.undoNbtStats(player.level().getServer(), player.getUUID());
		Msg.chat(player, Component.translatable(TEST + "info.undo", stats[0], String.format(java.util.Locale.ROOT, "%.1f", stats[1] / 1024.0D),
				config.kehancuranUndoBlockEntities, config.kehancuranUndoNbtKiB, config.kehancuranFluidSettle).withColor(MUTED));
		player.sendSystemMessage(totalStatus(player));
		return 1;
	}

	/** Test site half width (the site is (2h+1)² columns; the test bowl has radius 3). */
	private static final int SITE_HALF = 4;

	/**
	 * {@code uji kehancuran total uji <bedrock|cairan|peti|portal>}: builds a small test site 8 b ahead through the
	 * WorldBreaker's restore path (recorded, so Pulihkan clears it; only natural ground and air are replaced, never inside a
	 * protected zone), then carves an r 3 bowl into it with the player's own rules. Refused unless Mode Kehancuran is on.
	 */
	private static int testSite(ServerPlayer player, String kind) {
		if (!selfOn(player)) {
			Msg.chat(player, Component.translatable(TEST + "needs").withColor(RED));
			return 0;
		}
		if (KultivatorConfig.get().kehancuranUndoMinutes <= 0) {
			Msg.chat(player, Component.translatable(TEST + "no_undo").withColor(RED));
			return 0;
		}
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		int cx = net.minecraft.util.Mth.floor(player.getX() + flat.x * 8.0D);
		int cz = net.minecraft.util.Mth.floor(player.getZ() + flat.z * 8.0D);
		BlockPos probe = new BlockPos(cx, player.getBlockY(), cz);
		if (!level.isLoaded(probe)) {
			Msg.chat(player, Component.translatable(TEST + "refused").withColor(RED));
			return 0;
		}
		int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
		boolean portalAxisX = Math.abs(flat.z) >= Math.abs(flat.x);
		List<Long> positions = new ArrayList<>();
		List<BlockState> targets = new ArrayList<>();
		List<BlockState> current = new ArrayList<>();
		List<BlockPos> fill = new ArrayList<>();
		ProtectedZones.Context zones = new ProtectedZones.Context(level, new AABB(cx - SITE_HALF, top - 6, cz - SITE_HALF, cx + SITE_HALF + 1, top + 6,
				cz + SITE_HALF + 1), player.getUUID(), List.of(player.position()));
		for (int dx = -SITE_HALF; dx <= SITE_HALF; dx++) {
			for (int dz = -SITE_HALF; dz <= SITE_HALF; dz++) {
				for (int dy = -4; dy <= 0; dy++) {
					BlockState to = siteBlock(kind, dx, dy, dz, portalAxisX);
					if (to == null) {
						continue;
					}
					BlockPos pos = new BlockPos(cx + dx, top + dy, cz + dz);
					ProtectedZones.Zone zone = zones.zoneOf(pos);
					if (zone == ProtectedZones.Zone.CASTER) {
						// the caster's own safe column: the site simply leaves it out
						continue;
					}
					if (zone != null) {
						Msg.chat(player, Component.translatable(TEST + "zone").withColor(RED));
						return 0;
					}
					BlockState now = level.getBlockState(pos);
					if (!now.isAir() && !Shapes.natural(now) && !now.canBeReplaced()) {
						// only natural ground, air or loose plants are replaced by the test site
						continue;
					}
					positions.add(pos.asLong());
					targets.add(to);
					current.add(now);
					if (to.hasBlockEntity()) {
						fill.add(pos);
					}
				}
			}
		}
		if (positions.isEmpty()) {
			Msg.chat(player, Component.translatable(TEST + "refused").withColor(RED));
			return 0;
		}
		BlockPos anchor = new BlockPos(cx, top, cz);
		Vec3 direction = flat;
		java.util.UUID owner = player.getUUID();
		MinecraftServer server = level.getServer();
		boolean queued = WorldBreaker.submitSite(player, positions.stream().mapToLong(Long::longValue).toArray(), targets.toArray(new BlockState[0]),
				current.toArray(new BlockState[0]), () -> {
					ServerPlayer caster = server.getPlayerList().getPlayer(owner);
					if (caster == null || caster.level() != level) {
						return;
					}
					fillContainers(level, fill);
					SelfResult result = build(caster, "uji_kehancuran_total", List.of(new Shapes.Placed(Shapes.Spec.bowl(3), anchor, direction)),
							Math.max(256, KultivatorConfig.get().kehancuranMaxBlocksPerCast), dev.kultivator.technique.spec.dsl.Dampak.Palette.NONE, null);
					Msg.chat(caster, Component.translatable(TEST + "carve", result.name(), Component.translatable(rulesFor(caster).total()
							? TOTAL + "word.on" : TOTAL + "word.off")).withColor(0xFFB0A0));
				});
		if (!queued) {
			Msg.chat(player, Component.translatable(PREFIX + "why.queue_full").withColor(RED));
			return 0;
		}
		Msg.chat(player, Component.translatable(TEST + "site", Component.translatable(TEST + "kind." + kind), positions.size(), cx, top, cz)
				.withColor(0xFFB0A0));
		return 1;
	}

	/** The block of the test site at offset (dx, dy, dz) from the surface centre (dy 0 = the surface), or null = untouched. */
	private static @Nullable BlockState siteBlock(String kind, int dx, int dy, int dz, boolean axisX) {
		int c = Math.max(Math.abs(dx), Math.abs(dz));
		switch (kind) {
			case "bedrock" -> {
				// a bedrock slab two layers deep under the surface, with obsidian and reinforced deepslate in it
				if (c > 3 || dy < -2 || dy > -1) {
					return null;
				}
				if (dx == 0 && dz == 0) {
					return Blocks.REINFORCED_DEEPSLATE.defaultBlockState();
				}
				return (dx + dz & 1) == 0 ? Blocks.BEDROCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
			}
			case "cairan" -> {
				// a pool two deep and wider than the bowl: its rim stays as a wall of water
				if (c > SITE_HALF || dy < -1) {
					return null;
				}
				return Blocks.WATER.defaultBlockState();
			}
			case "peti" -> {
				// three chests, a barrel and a furnace with contents, in the ground
				if (dy != -1) {
					return null;
				}
				if (dx == 0 && dz == 0) {
					return Blocks.FURNACE.defaultBlockState();
				}
				if (dx == 1 && dz == 0 || dx == -1 && dz == 0 || dx == 0 && dz == 1) {
					return Blocks.CHEST.defaultBlockState();
				}
				return dx == 0 && dz == -1 ? Blocks.BARREL.defaultBlockState() : null;
			}
			case "portal" -> {
				// an upright Nether portal sunk into the ground (frame 4 wide, 5 tall), crying obsidian, a respawn anchor,
				// a lodestone and an end portal frame beside it
				int along = axisX ? dx : dz;
				int across = axisX ? dz : dx;
				if (across == 0 && along >= -1 && along <= 2 && dy >= -4 && dy <= 0) {
					boolean frame = along == -1 || along == 2 || dy == -4 || dy == 0;
					if (frame) {
						return Blocks.OBSIDIAN.defaultBlockState();
					}
					return Blocks.NETHER_PORTAL.defaultBlockState().setValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS,
							axisX ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z);
				}
				if (dy == -1 && across == 2) {
					return switch (along) {
						case -1 -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
						case 0 -> Blocks.RESPAWN_ANCHOR.defaultBlockState();
						case 1 -> Blocks.LODESTONE.defaultBlockState();
						case 2 -> Blocks.END_PORTAL_FRAME.defaultBlockState();
						default -> null;
					};
				}
				return null;
			}
			default -> {
				return null;
			}
		}
	}

	/** Puts some ordinary items into the test site's containers (so Pulihkan can be checked one by one). */
	private static void fillContainers(ServerLevel level, List<BlockPos> positions) {
		int n = 0;
		for (BlockPos pos : positions) {
			if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container && container.getContainerSize() > 0) {
				container.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 16 + n));
				if (container.getContainerSize() > 2) {
					container.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 3 + n));
				}
				container.setChanged();
				n++;
			}
		}
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
		selfStatusLines(player);
		WorldBreaker.Job running = WorldBreaker.running();
		player.sendSystemMessage(Component.translatable(PREFIX + "status.jobs", running == null ? 0 : 1, WorldBreaker.queued()).withColor(MUTED));
		for (int[] undo : WorldBreaker.undoable(player.level().getServer(), player.getUUID())) {
			player.sendSystemMessage(Component.translatable(PREFIX + "status.undo", undo[0], undo[1] / 60, undo[1] % 60).withColor(0x9FF3E6));
		}
		// Phase 10b (29.16.7, 29.16.9): the running bulk carve and the saved permanent carves
		WorldBreaker.BulkInfo bulk = WorldBreaker.bulkInfo();
		if (bulk != null) {
			player.sendSystemMessage(Component.translatable("kultivator.kehancuran.bulk.status.running", bulk.ownerName(), bulk.tier(), bulk.chunksDone(),
					bulk.chunksTotal(), bulk.changed() + bulk.surface()).withColor(0xFFB0A0));
		}
		for (CompoundTag line : KehancuranLog.permanent(player.level().getServer(), player.getUUID(), 5)) {
			dev.kultivator.data.TechniqueSpec spec = dev.kultivator.data.TechniqueRegistry.byId(line.getStringOr("spec", ""));
			Component name = spec == null ? Component.literal(line.getStringOr("spec", "?")) : spec.displayName();
			player.sendSystemMessage(Component.translatable("kultivator.kehancuran.bulk.status.permanent", name, line.getIntOr("tier", 6),
					line.getIntOr("blocks", 0), line.getIntOr("x", 0), line.getIntOr("y", 0), line.getIntOr("z", 0), line.getStringOr("dim", "?"))
					.withColor(0xFF6A5A));
		}
		return 1;
	}

	/** The player's own switch in {@code /kultivasi kehancuran status}: state, size, zones and the last self casts. */
	private static void selfStatusLines(ServerPlayer player) {
		// Phase 30 (30.1.2): the Hancur Total line
		player.sendSystemMessage(totalStatus(player));
		CompoundTag kh = selfStatus(player);
		Component state = Component.translatable(kh.getBooleanOr("on", false) ? SELF + "status.on" : SELF + "status.off")
				.withStyle(kh.getBooleanOr("on", false) ? ChatFormatting.RED : ChatFormatting.GREEN);
		player.sendSystemMessage(Component.translatable(SELF + "status.line", state, String.format(java.util.Locale.ROOT, "%.2f", kh.getDoubleOr("k", 0.0D)),
				kh.getIntOr("cap", 0), kh.getIntOr("safe", 4), kh.getIntOr("home", 24)).withColor(MUTED));
		if (!kh.getBooleanOr("allowed", false)) {
			player.sendSystemMessage(Component.translatable(SELF + "status.why", Component.translatable(kh.getStringOr("why", SELF + "why.permission")))
					.withColor(MUTED));
		} else if (!kh.getBooleanOr("realm", false)) {
			player.sendSystemMessage(Component.translatable(SELF + "status.realm").withColor(MUTED));
		}
		for (net.minecraft.nbt.Tag entry : kh.getListOrEmpty("log")) {
			if (entry instanceof CompoundTag line) {
				dev.kultivator.data.TechniqueSpec spec = dev.kultivator.data.TechniqueRegistry.byId(line.getStringOr("spec", ""));
				Component name = spec == null ? Component.literal(line.getStringOr("spec", "?")) : spec.displayName();
				player.sendSystemMessage(Component.translatable(SELF + "status.cast", name, line.getIntOr("blocks", 0), line.getIntOr("x", 0),
						line.getIntOr("y", 0), line.getIntOr("z", 0)).withColor(0xFFB0A0));
			}
		}
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
