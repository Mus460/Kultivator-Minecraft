package dev.kultivator.sect.hall;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The Sect Hall (Aula Sekte, DESIGN 20.3.1-20.3.8; module A, P6-aula): founding, moving and the dormant hall, the hall
 * tick (once a second: arrivals, candidate views, away returns, the spread building scan every 30 s), renown and the
 * hall level, the screen's actions and its {@code HallInfo}, the commands, and the public API of DESIGN 20.4.5. Phase 6b
 * (Pembangunan Sekte) plugs in through the provider hooks of DESIGN 21.24 item 5; with none registered nothing changes.
 * <p>
 * Sources: <i>Battle Through the Heavens</i> (the Xiao clan's testing stone monument), <i>A Record of a Mortal's
 * Journey</i> ch. 153 (Yue Lu Hall), <i>I Shall Seal the Heavens</i> ch. 36-38 (the Magic Pavilion), <i>Return of the
 * Mount Hua Sect</i> (renown brings applicants back), <i>Perfect World</i> ch. 551/558 (rebuilding a ruined pavilion).
 */
public final class Halls {

	/** A hall as other modules see it. {@code active} is false while the stele is missing (dormant). */
	public record HallView(BlockPos pos, ResourceKey<Level> dim, int level, long renown, int radius, boolean active, String name) {
	}

	/** A disciple away from the world (expedition, envoy, soul flight...): what the lists show. */
	public record AwayView(UUID soul, Component name, String reason, long returnAt, CompoundTag info) {
	}

	/**
	 * A candidate another module sends to the hall (envoys, the Hundred Dynasties War): its rolled data, Tekad (-1 =
	 * roll), the chance of a talent and of a physique (negative = the hall's normal chance), whether it is already tested,
	 * and a source id ("envoy", "war", ...).
	 */
	/**
	 * A candidate sent by another module. {@code originKey} (phase 8, DESIGN 25.6.5) is shown on the candidate card as
	 * "dari ‹origin›": a translation key when it starts with {@code kultivator.}, otherwise a plain name (null for none).
	 * {@code resolveBonus} is added to the Tekad (rolled or given; the total stays 0-100).
	 */
	public record CandidateSpec(DiscipleData data, int tekad, double talentChance, double physiqueChance, boolean tested, String source,
			@Nullable String originKey, int resolveBonus) {
		/** The phase-6 form: no origin, no resolve bonus. */
		public CandidateSpec(DiscipleData data, int tekad, double talentChance, double physiqueChance, boolean tested, String source) {
			this(data, tekad, talentChance, physiqueChance, tested, source, null, 0);
		}
	}

	/**
	 * Called when an away disciple of {@code reason} is due: may change the stored data ({@code stored} is the tag of
	 * {@code Disciples.depart}) and returns where it arrives in {@code level} (null: beside the stele or the master).
	 */
	@FunctionalInterface
	public interface ReturnHandler {
		@Nullable Vec3 onReturn(ServerLevel level, UUID owner, CompoundTag stored, CompoundTag info);
	}

	/**
	 * Where a candidate of the owner's hall should arrive (phase 6b: the plan's {@code arrival} anchor and the kept ring
	 * cells, DESIGN 21.17), or null for the 20.3.3 ring 8-14 blocks from the stele. The spot is checked with
	 * {@code Targeting.safeSpot} before use.
	 */
	@FunctionalInterface
	public interface ArrivalSpotProvider {
		@Nullable BlockPos spot(UUID owner, ServerLevel level, BlockPos stele, RandomSource random);
	}

	/** A line replacing the Aula tablet's building checklist (phase 6b: {@code SectBuild.statusLine}), or null for none. */
	@FunctionalInterface
	public interface StatusLineProvider {
		@Nullable Component statusLine(UUID owner);
	}

	/**
	 * Hears the stele move (phase 6b, DESIGN 21.17 / 21.18). {@link #beforeMove} may take over a wake-up or a move (for
	 * example to open the construction move dialog while a template exists): the hall then stays where it is and the
	 * listener later calls {@link Halls#relocateHall}.
	 */
	public interface SteleMoveListener {
		/**
		 * The hall is about to wake at ({@code wake}: a stele placed while dormant) or move to ({@code to}, a second stele
		 * sneak-used) from {@code from} (null when unknown). True = handled here, module A does nothing more.
		 */
		default boolean beforeMove(ServerPlayer player, UUID owner, ServerLevel level, @Nullable BlockPos from, BlockPos to, boolean wake) {
			return false;
		}

		/** The hall woke at or moved to {@code to}. */
		default void afterMove(UUID owner, ServerLevel level, @Nullable BlockPos from, BlockPos to, boolean wake) {
		}

		/** The stele at {@code pos} is gone: the hall is dormant now. */
		default void onRemoved(UUID owner, @Nullable ServerLevel level, @Nullable BlockPos pos) {
		}
	}

	// ---- screen pages (OpenScreen.HALL arg; the Bangun tablet of phase 6b is second, DESIGN 21.24) and hall action sub-ops
	public static final int PAGE_AULA = 0;
	public static final int PAGE_BANGUN = 1;
	public static final int PAGE_CALON = 2;
	public static final int PAGE_JENJANG = 3;
	public static final int PAGE_TREASURY = 4;
	/**
	 * The Formasi tablet of phase 7c (DESIGN 24.13 item 6): appended after every existing page (index 10 of the screen's
	 * page list) but drawn third, after Bangun. The indices above stay unchanged.
	 */
	public static final int PAGE_FORMASI = 10;
	/** Phase 8 (DESIGN 25.2 item 3): the Pertahanan tablet (raid schedule and log, module B), appended after Formasi. */
	public static final int PAGE_PERTAHANAN = 11;
	/** Phase 8 (DESIGN 25.2 item 3): the Desa tablet (vassal villages, module D). */
	public static final int PAGE_DESA = 12;
	public static final int OP_REFRESH = 0;
	public static final int OP_RENAME = 1;
	public static final int OP_TEST = 2;
	public static final int OP_ACCEPT = 3;
	public static final int OP_DISMISS = 4;
	public static final int OP_MONSTER = 5;
	public static final int OP_PROMOTE = 6;
	public static final int OP_DEMOTE = 7;
	public static final int OP_PROTECTOR = 8;
	public static final int OP_OFFER = 9;
	public static final int OP_BUY = 10;
	public static final int OP_GRANT = 11;
	public static final int OP_RESTORE = 12;
	public static final int OP_CLOSE = 13;

	/** A stele within this many blocks of another player's hall stays inert. */
	public static final double OTHER_HALL_RANGE = 128.0D;
	/** A move must be confirmed by a second sneak-use within this many ticks. */
	public static final int MOVE_CONFIRM_TICKS = 200;
	/** Seconds between two building scans (and stele checks). */
	public static final int SCAN_SECONDS = 30;
	public static final int LOG_SIZE = 50;
	/** Territory height: |dy| at most this. */
	public static final double TERRITORY_HEIGHT = 32.0D;
	/** Renown for a boss killed with a disciple dealing at least 20% of its health (once per boss type per day). */
	public static final int BOSS_RENOWN = 100;
	public static final double BOSS_SHARE = 0.2D;
	/** Renown per realm ordinal of a disciple's breakthrough. */
	public static final int BREAKTHROUGH_RENOWN = 10;
	private static final int HALL_COLOR = 0x8FE3C8;

	private static final List<InfoProvider> INFO_PROVIDERS = new ArrayList<>();
	// Phase 6b providers (DESIGN 21.24 item 5). With none registered every phase-6 rule is unchanged.
	private static final List<ArrivalSpotProvider> ARRIVAL_SPOT_PROVIDERS = new ArrayList<>();
	private static final List<Predicate<UUID>> RECRUIT_PAUSE_PROVIDERS = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static final List<StatusLineProvider> STATUS_LINE_PROVIDERS = new ArrayList<>();
	private static final List<SteleMoveListener> STELE_MOVE_LISTENERS = new ArrayList<>();
	private static final Map<UUID, HallView> VIEW_CACHE = new HashMap<>();
	/** Players with the hall scroll open -> whose hall. */
	private static final Map<UUID, UUID> VIEWERS = new HashMap<>();
	private static final Map<UUID, PendingMove> MOVES = new HashMap<>();
	/** Boss UUID -> master UUID -> damage their disciples dealt. */
	private static final Map<UUID, Map<UUID, Float>> BOSS_DAMAGE = new HashMap<>();
	private static @Nullable MinecraftServer server;

	private record PendingMove(BlockPos pos, ResourceKey<Level> dim, int deadline) {
	}

	public static void init() {
		HallItems.init();
		SectRanks.init();
		HallBlock.initSneakUse();
		ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			server = null;
			VIEW_CACHE.clear();
			VIEWERS.clear();
			MOVES.clear();
			BOSS_DAMAGE.clear();
			Candidates.clearAll();
			HallLevel.clearScans();
			GrandFormation.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Halls::tickServer);
		ServerPlayerEvents.LEAVE.register(player -> {
			VIEWERS.remove(player.getUUID());
			MOVES.remove(player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Halls::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(Halls::afterDeath);
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onEnd(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
				UUID owner = entity.ownerId();
				if (success && owner != null) {
					addRenown(owner, BREAKTHROUGH_RENOWN * entity.data().realm().ordinal(), "kultivator.hall.renown.breakthrough");
				}
			}
		});
	}

	// ================================================================== internals shared by the module

	static @Nullable MinecraftServer server() {
		return server;
	}

	private static @Nullable HallData data() {
		return server == null ? null : HallData.get(server);
	}

	static @Nullable CompoundTag coreOf(UUID owner) {
		HallData data = data();
		return data == null || owner == null ? null : data.core(owner);
	}

	static @Nullable BlockPos posOf(CompoundTag core) {
		int[] pos = core.getIntArray("pos").orElse(null);
		return pos == null || pos.length != 3 ? null : new BlockPos(pos[0], pos[1], pos[2]);
	}

	static @Nullable ResourceKey<Level> dimOf(CompoundTag core) {
		Identifier id = Identifier.tryParse(core.getStringOr("dim", ""));
		return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
	}

	static @Nullable ServerLevel levelOf(CompoundTag core) {
		ResourceKey<Level> dim = dimOf(core);
		return server == null || dim == null ? null : server.getLevel(dim);
	}

	static boolean dormant(CompoundTag core) {
		return core.getBooleanOr("dormant", false);
	}

	private static int levelOf(CompoundTag core, boolean ignored) {
		return HallLevel.level(core.getLongOr("renown", 0L), core.getIntOr("bcap", HallLevel.CAP_STELE));
	}

	/** The overworld clock (day time, counting up across days). */
	static long clockTime() {
		return server == null ? 0L : server.overworld().getOverworldClockTime();
	}

	/** Day index {@code floor(overworld day time / 24000)} (DESIGN 20.1). */
	static long dayIndex() {
		return Math.floorDiv(clockTime(), 24000L);
	}

	static long gameTime() {
		return server == null ? 0L : server.overworld().getGameTime();
	}

	/** Ticks as real time "h:mm:ss". */
	static String clock(long ticks) {
		long seconds = Math.max(0L, ticks) / 20L;
		return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600L, seconds / 60L % 60L, seconds % 60L);
	}

	private static void changed() {
		HallData data = data();
		if (data != null) {
			data.setDirty();
		}
		VIEW_CACHE.clear();
	}

	/** Remembers a disciple's rank, protector post and power in the hall (counts include unloaded disciples). */
	static void noteRank(UUID owner, UUID soul, int rank, boolean protector, int power) {
		CompoundTag core = coreOf(owner);
		if (core == null) {
			return;
		}
		CompoundTag ranks = HallData.section(core, "ranks");
		CompoundTag powers = HallData.section(core, "pw");
		String key = soul.toString();
		int value = rank | (protector ? 16 : 0);
		if (ranks.getIntOr(key, -1) != value || powers.getIntOr(key, -1) != power) {
			ranks.putInt(key, value);
			powers.putInt(key, power);
			changed();
		}
	}

	/** Souls of the master's living disciples (from the master's list). */
	private static Set<String> livingSouls(ServerPlayer master) {
		Set<String> souls = new HashSet<>();
		ListTag list = Cultivators.get(master).extra().getCompoundOrEmpty(Disciples.EXTRA).getListOrEmpty("list");
		for (Tag tag : list) {
			if (tag instanceof CompoundTag entry && !entry.getBooleanOr("dead", false)) {
				souls.add(entry.getStringOr("id", ""));
			}
		}
		return souls;
	}

	/** Living disciples of {@code rank} other than {@code except}. */
	static int countRank(ServerPlayer master, int rank, @Nullable UUID except) {
		CompoundTag core = coreOf(master.getUUID());
		if (core == null) {
			return 0;
		}
		Set<String> living = livingSouls(master);
		CompoundTag ranks = core.getCompoundOrEmpty("ranks");
		int count = 0;
		for (String soul : ranks.keySet()) {
			if ((ranks.getIntOr(soul, 0) & 15) == rank && living.contains(soul) && (except == null || !soul.equals(except.toString()))) {
				count++;
			}
		}
		return count;
	}

	static int countProtectors(ServerPlayer master, @Nullable UUID except) {
		CompoundTag core = coreOf(master.getUUID());
		if (core == null) {
			return 0;
		}
		Set<String> living = livingSouls(master);
		CompoundTag ranks = core.getCompoundOrEmpty("ranks");
		int count = 0;
		for (String soul : ranks.keySet()) {
			if ((ranks.getIntOr(soul, 0) & 16) != 0 && living.contains(soul) && (except == null || !soul.equals(except.toString()))) {
				count++;
			}
		}
		return count;
	}

	/** The highest {@code DisciplePaths.power} among the master's other living disciples (loaded, away or remembered). */
	static int strongestPower(ServerPlayer master, @Nullable UUID except) {
		int best = 0;
		CompoundTag core = coreOf(master.getUUID());
		Set<String> living = livingSouls(master);
		if (core != null) {
			CompoundTag powers = core.getCompoundOrEmpty("pw");
			for (String soul : powers.keySet()) {
				if (living.contains(soul) && (except == null || !soul.equals(except.toString()))) {
					best = Math.max(best, powers.getIntOr(soul, 0));
				}
			}
			for (DiscipleData data : AwayStore.data(core)) {
				best = Math.max(best, DisciplePaths.power(data));
			}
		}
		for (DiscipleEntity disciple : Disciples.loaded(master.getUUID())) {
			if (except == null || !disciple.soulId().equals(except)) {
				best = Math.max(best, DisciplePaths.power(disciple.data()));
			}
		}
		return best;
	}

	/** True when the disciple stands within {@code distance} of its master's active stele. */
	static boolean nearStele(UUID owner, DiscipleEntity entity, double distance) {
		HallView hall = hallOf(owner);
		return hall != null && hall.active() && entity.level().dimension().equals(hall.dim())
				&& entity.distanceToSqr(Vec3.atBottomCenterOf(hall.pos())) <= distance * distance;
	}

	/** The master's loaded disciple with this soul id, or null. */
	static @Nullable DiscipleEntity findDisciple(UUID owner, String soul) {
		UUID id;
		try {
			id = UUID.fromString(soul);
		} catch (IllegalArgumentException exception) {
			return null;
		}
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.soulId().equals(id) && disciple.role() == DiscipleEntity.Role.DISCIPLE) {
				return disciple;
			}
		}
		return null;
	}

	// ================================================================== the stele (HallBlock)

	/** The owner of the active hall whose stele stands at {@code pos} in {@code level}, or null. */
	private static @Nullable UUID ownerAt(ServerLevel level, BlockPos pos) {
		HallData data = data();
		if (data == null) {
			return null;
		}
		for (UUID owner : data.owners()) {
			CompoundTag core = data.core(owner);
			if (core != null && !dormant(core) && pos.equals(posOf(core)) && level.dimension().equals(dimOf(core))) {
				return owner;
			}
		}
		return null;
	}

	/** The owner of another player's hall (active or dormant) within 128 blocks horizontally, or null. */
	private static @Nullable UUID otherHallNear(UUID self, ServerLevel level, BlockPos pos) {
		HallData data = data();
		if (data == null) {
			return null;
		}
		for (UUID owner : data.owners()) {
			if (owner.equals(self)) {
				continue;
			}
			CompoundTag core = data.core(owner);
			BlockPos other = core == null ? null : posOf(core);
			if (other != null && level.dimension().equals(dimOf(core))) {
				double dx = other.getX() - pos.getX();
				double dz = other.getZ() - pos.getZ();
				if (dx * dx + dz * dz <= OTHER_HALL_RANGE * OTHER_HALL_RANGE) {
					return owner;
				}
			}
		}
		return null;
	}

	/** A player placed a stele: it founds or wakes their hall, or stays inert with a message. */
	static void onStelePlaced(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (server == null) {
			server = level.getServer();
		}
		if (!KultivatorConfig.get().sectHall) {
			Msg.chat(player, Component.translatable("kultivator.hall.disabled").withStyle(ChatFormatting.RED));
			return;
		}
		if (dev.kultivator.abadi.Abadi.isImmortalLevel(level)) {
			// Phase 9 (DESIGN 26.14 item 13): no Sect Hall in Alam Abadi.
			Msg.chat(player, Component.translatable("kultivator.spine9.refuse.hall").withStyle(ChatFormatting.RED));
			return;
		}
		UUID owner = player.getUUID();
		UUID other = otherHallNear(owner, level, pos);
		if (other != null) {
			CompoundTag core = coreOf(other);
			Msg.chat(player, Component.translatable("kultivator.hall.too_close", core == null ? "?" : hallName(core),
					(int) OTHER_HALL_RANGE).withStyle(ChatFormatting.YELLOW));
			return;
		}
		CompoundTag core = coreOf(owner);
		if (core == null) {
			found(player, level, pos);
			return;
		}
		if (dormant(core)) {
			if (beforeMove(player, owner, level, posOf(core), pos, true)) {
				return;
			}
			relocate(player, owner, core, level, pos, true);
			return;
		}
		BlockPos current = posOf(core);
		Msg.chat(player, Component.translatable("kultivator.hall.already", current == null ? "?" : current.getX() + " " + current.getY() + " "
				+ current.getZ()).withStyle(ChatFormatting.YELLOW));
		Msg.chat(player, Component.translatable("kultivator.hall.move_hint").withStyle(ChatFormatting.GRAY));
	}

	private static void found(ServerPlayer player, ServerLevel level, BlockPos pos) {
		HallData data = data();
		if (data == null) {
			return;
		}
		UUID owner = player.getUUID();
		CompoundTag core = HallData.section(data.create(owner), HallData.CORE);
		long day = dayIndex();
		core.putIntArray("pos", new int[]{pos.getX(), pos.getY(), pos.getZ()});
		core.putString("dim", level.dimension().identifier().toString());
		// Empty: shown as "Sect of <owner>" / "Sekte <owner>" in the viewer's language (hallName).
		core.putString("name", "");
		core.putString("owner_name", player.getName().getString());
		core.putLong("founded_day", day);
		core.putLong("last_arrival", day);
		core.putLong("renown", 0L);
		core.putInt("pool", 0);
		core.putInt("bcap", HallLevel.CAP_STELE);
		core.putInt("bflags", 0);
		core.putInt("level_seen", 1);
		core.putInt("next_id", 1);
		core.putBoolean("dormant", false);
		changed();
		Vec3 top = Vec3.atBottomCenterOf(pos).add(0.0D, 1.0D, 0.0D);
		Fx.send(level, top, FxType.HALL, HallFxIds.FOUNDED, HALL_COLOR, 6.0F);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5F, 0.8F);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.2F);
		log(owner, Component.translatable("kultivator.hall.log.founded"));
		Msg.chat(player, Component.translatable("kultivator.hall.founded", hallName(core)).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable("kultivator.hall.founded_hint", KultivatorConfig.get().recruitmentIntervalDays)
				.withStyle(ChatFormatting.GRAY));
		Advancements.grant(player, "sect_gate_open");
		scan(owner, level, pos);
	}

	/** The hall moves to (or wakes at) a new stele: renown, pool, storehouse, candidates and away teams come along. */
	private static void relocate(ServerPlayer player, UUID owner, CompoundTag core, ServerLevel level, BlockPos pos, boolean wake) {
		BlockPos from = posOf(core);
		if (wake) {
			Candidates.clearHall(owner, core);
		} else {
			Candidates.moveHall(owner, core);
			CompoundTag extra = Cultivators.get(player).extra();
			CompoundTag hall = extra.getCompound("hall").orElse(null);
			if (hall == null) {
				hall = new CompoundTag();
				extra.put("hall", hall);
			}
			hall.putLong("moved_day", dayIndex());
			Cultivators.dirty(player);
		}
		core.putIntArray("pos", new int[]{pos.getX(), pos.getY(), pos.getZ()});
		core.putString("dim", level.dimension().identifier().toString());
		core.putBoolean("dormant", false);
		changed();
		Vec3 top = Vec3.atBottomCenterOf(pos).add(0.0D, 1.0D, 0.0D);
		Fx.send(level, top, FxType.HALL, HallFxIds.FOUNDED, HALL_COLOR, 4.0F);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.2F, 0.9F);
		log(owner, Component.translatable(wake ? "kultivator.hall.log.woke" : "kultivator.hall.log.moved"));
		Msg.chat(player, Component.translatable(wake ? "kultivator.hall.woke" : "kultivator.hall.moved", hallName(core))
				.withStyle(ChatFormatting.GOLD));
		scan(owner, level, pos);
		for (SteleMoveListener listener : List.copyOf(STELE_MOVE_LISTENERS)) {
			try {
				listener.afterMove(owner, level, from, pos, wake);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Sect Hall stele-move listener failed", exception);
			}
		}
	}

	/** Asks the stele-move listeners whether one takes over this wake-up or move (phase 6b); false = module A goes on. */
	private static boolean beforeMove(ServerPlayer player, UUID owner, ServerLevel level, @Nullable BlockPos from, BlockPos to, boolean wake) {
		for (SteleMoveListener listener : List.copyOf(STELE_MOVE_LISTENERS)) {
			try {
				if (listener.beforeMove(player, owner, level, from, to, wake)) {
					return true;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Sect Hall stele-move listener failed", exception);
			}
		}
		return false;
	}

	/** Someone used a stele (empty hand, or an item that is not a token). */
	static void onSteleUsed(ServerPlayer player, ServerLevel level, BlockPos pos, boolean sneaking) {
		if (server == null) {
			server = level.getServer();
		}
		UUID owner = ownerAt(level, pos);
		UUID self = player.getUUID();
		if (owner == null) {
			CompoundTag mine = coreOf(self);
			if (mine == null || dormant(mine)) {
				onStelePlaced(player, level, pos);
			} else if (sneaking && player.getMainHandItem().isEmpty()) {
				offerMove(player, mine, level, pos);
			} else {
				Msg.chat(player, Component.translatable("kultivator.hall.inert").withStyle(ChatFormatting.GRAY));
				Msg.chat(player, Component.translatable("kultivator.hall.move_hint").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (owner.equals(self)) {
			openScreen(player, owner, PAGE_AULA);
			return;
		}
		if (sectMate(player, owner)) {
			openScreen(player, owner, PAGE_AULA);
			return;
		}
		CompoundTag core = coreOf(owner);
		Msg.actionBar(player, Component.translatable("kultivator.hall.belongs", core == null ? "?" : hallName(core),
				core == null ? "?" : core.getStringOr("owner_name", "?")).withStyle(ChatFormatting.GRAY));
	}

	private static boolean sectMate(ServerPlayer player, UUID owner) {
		if (server == null) {
			return false;
		}
		Sect sect = SectData.get(server).sectOf(owner);
		return sect != null && sect.isMember(player.getUUID());
	}

	/** "Pindahkan Aula ke sini?": a second sneak-use within 10 s moves the hall (once per in-game day). */
	private static void offerMove(ServerPlayer player, CompoundTag core, ServerLevel level, BlockPos pos) {
		long day = dayIndex();
		long moved = Cultivators.get(player).extra().getCompoundOrEmpty("hall").getLongOr("moved_day", Long.MIN_VALUE / 2L);
		if (day - moved < 1L) {
			Msg.chat(player, Component.translatable("kultivator.hall.move_cooldown").withStyle(ChatFormatting.YELLOW));
			return;
		}
		UUID other = otherHallNear(player.getUUID(), level, pos);
		if (other != null) {
			Msg.chat(player, Component.translatable("kultivator.hall.too_close", coreOf(other) == null ? "?" : hallName(coreOf(other)),
					(int) OTHER_HALL_RANGE).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (beforeMove(player, player.getUUID(), level, posOf(core), pos, false)) {
			MOVES.remove(player.getUUID());
			return;
		}
		int now = level.getServer().getTickCount();
		PendingMove pending = MOVES.get(player.getUUID());
		if (pending != null && pending.pos().equals(pos) && pending.dim().equals(level.dimension()) && now <= pending.deadline()) {
			MOVES.remove(player.getUUID());
			relocate(player, player.getUUID(), core, level, pos, false);
			return;
		}
		MOVES.put(player.getUUID(), new PendingMove(pos.immutable(), level.dimension(), now + MOVE_CONFIRM_TICKS));
		Msg.chat(player, Component.translatable("kultivator.hall.move_ask").withStyle(ChatFormatting.GOLD));
	}

	/** The stele block was removed: the hall falls dormant (nothing is deleted). */
	static void onSteleRemoved(ServerLevel level, BlockPos pos) {
		UUID owner = ownerAt(level, pos);
		if (owner == null || level.getBlockState(pos).is(HallItems.SECT_HALL_STELE)) {
			return;
		}
		CompoundTag core = coreOf(owner);
		if (core != null) {
			makeDormant(owner, core, level, pos);
		}
	}

	private static void makeDormant(UUID owner, CompoundTag core, @Nullable ServerLevel level, @Nullable BlockPos pos) {
		core.putBoolean("dormant", true);
		Candidates.clearHall(owner, core);
		changed();
		log(owner, Component.translatable("kultivator.hall.log.dormant"));
		if (level != null && pos != null) {
			Fx.send(level, Vec3.atBottomCenterOf(pos).add(0.0D, 0.5D, 0.0D), FxType.HALL, HallFxIds.DORMANT, 0x8C9C94, 2.0F);
			level.playSound(null, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
		}
		ServerPlayer master = server == null ? null : server.getPlayerList().getPlayer(owner);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.hall.dormant", hallName(core)).withStyle(ChatFormatting.YELLOW));
		}
		for (SteleMoveListener listener : List.copyOf(STELE_MOVE_LISTENERS)) {
			try {
				listener.onRemoved(owner, level, pos);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Sect Hall stele-move listener failed", exception);
			}
		}
	}

	// ================================================================== ticking

	private static void tickServer(MinecraftServer current) {
		server = current;
		Candidates.tickServer(current);
		HallLevel.tickScans();
		int tick = current.getTickCount();
		if (tick % 20 != 0) {
			return;
		}
		HallData data = HallData.get(current);
		if (data.isEmpty()) {
			return;
		}
		long clock = clockTime();
		long day = Math.floorDiv(clock, 24000L);
		int timeOfDay = (int) Math.floorMod(clock, 24000L);
		for (UUID owner : data.owners()) {
			CompoundTag core = data.core(owner);
			if (core == null) {
				continue;
			}
			try {
				tickHall(current, owner, core, day, timeOfDay, tick);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Sect Hall of {} failed to tick", owner, exception);
			}
		}
	}

	private static void tickHall(MinecraftServer current, UUID owner, CompoundTag core, long day, int timeOfDay, int tick) {
		if (dormant(core)) {
			if (AwayStore.tick(current, owner, core, null, null)) {
				changed();
			}
			return;
		}
		ServerLevel level = levelOf(core);
		BlockPos pos = posOf(core);
		if (level == null || pos == null) {
			return;
		}
		boolean loaded = level.isLoaded(pos);
		int second = tick / 20;
		if (loaded && Math.floorMod(second + owner.hashCode(), SCAN_SECONDS) == 0) {
			if (!level.getBlockState(pos).is(HallItems.SECT_HALL_STELE)) {
				makeDormant(owner, core, level, pos);
				return;
			}
			scan(owner, level, pos);
		}
		if (Candidates.tick(current, owner, core, level, pos, day, timeOfDay)) {
			changed();
		}
		if (AwayStore.tick(current, owner, core, level, pos)) {
			changed();
		}
		checkLevel(owner, core);
	}

	/**
	 * Re-reads the hall's buildings: with a phase-6b building cap (a provider value of 0 or more, DESIGN 21.17) the cap and
	 * bits are applied at once and the building scan is skipped; otherwise the spread scan of DESIGN 20.3.1 starts.
	 */
	private static void scan(UUID owner, ServerLevel level, BlockPos pos) {
		int provided = HallLevel.providedCap(owner);
		if (provided < 0) {
			HallLevel.startScan(owner, level, pos);
			return;
		}
		applyProvided(owner, level, pos, provided);
	}

	/**
	 * A provided building cap: {@code bcap} = that cap, {@code bflags} = the provided bits with {@code B_FORMATION} still
	 * from the real active-formation check (DESIGN 21.17); the scan counters are left as they were.
	 */
	private static void applyProvided(UUID owner, ServerLevel level, BlockPos pos, int provided) {
		CompoundTag core = coreOf(owner);
		if (core == null) {
			return;
		}
		int flags = (HallLevel.providedFlags(owner) & ~HallLevel.B_FORMATION)
				| (HallLevel.hasOwnFormation(level, pos, owner) ? HallLevel.B_FORMATION : 0);
		int cap = Math.max(HallLevel.CAP_STELE, Math.min(HallLevel.MAX_LEVEL, provided));
		if (core.getIntOr("bflags", -1) != flags || core.getIntOr("bcap", -1) != cap) {
			core.putInt("bflags", flags);
			core.putInt("bcap", cap);
			changed();
		}
		checkLevel(owner, core);
	}

	/** The building scan finished: a new building cap (and maybe a new level). */
	static void onScanFinished(UUID owner, ServerLevel level, BlockPos centre, int flags, CompoundTag counts) {
		CompoundTag core = coreOf(owner);
		if (core == null || !centre.equals(posOf(core)) || !level.dimension().equals(dimOf(core))) {
			return;
		}
		int provided = HallLevel.providedCap(owner);
		if (provided >= 0) {
			// A template was confirmed while this scan ran: the provided cap wins (DESIGN 21.17).
			applyProvided(owner, level, centre, provided);
			return;
		}
		int cap = HallLevel.capFromFlags(flags);
		if (core.getIntOr("bflags", -1) != flags || core.getIntOr("bcap", -1) != cap || core.getIntOr("scan_bcap", -1) != cap
				|| !core.getCompoundOrEmpty("bcount").equals(counts)) {
			core.putInt("bflags", flags);
			core.putInt("bcap", cap);
			core.putInt("scan_bcap", cap);
			core.put("bcount", counts);
			changed();
		}
		checkLevel(owner, core);
	}

	/** Celebrates a new hall level once the owner is online: the pillar, the advancements and, at level 5, the server line. */
	private static void checkLevel(UUID owner, CompoundTag core) {
		int level = levelOf(core, true);
		int seen = core.getIntOr("level_seen", 1);
		if (level <= seen || server == null) {
			return;
		}
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master == null) {
			return;
		}
		core.putInt("level_seen", level);
		changed();
		Component name = hallName(core);
		log(owner, Component.translatable("kultivator.hall.log.level", level));
		Msg.chat(master, Component.translatable("kultivator.hall.level_up", name, level).withStyle(ChatFormatting.GOLD));
		ServerLevel hallLevel = levelOf(core);
		BlockPos pos = posOf(core);
		if (hallLevel != null && pos != null && !dormant(core)) {
			Vec3 top = Vec3.atBottomCenterOf(pos).add(0.0D, 1.0D, 0.0D);
			Fx.send(hallLevel, top, FxType.HALL, HallFxIds.LEVEL_UP, level, 10.0F);
			dev.kultivator.epic.EpicHooks.hallLevel(server, owner, level);
			hallLevel.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.7F);
			hallLevel.playSound(null, top.x, top.y, top.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.6F, 1.0F);
		}
		if (level >= HallLevel.ANNOUNCE_LEVEL) {
			Advancements.grant(master, "hall_level_5");
			if (!core.getBooleanOr("announced", false)) {
				core.putBoolean("announced", true);
				Msg.broadcast(server, Component.translatable("kultivator.hall.returned", name).withStyle(ChatFormatting.GOLD));
			}
		}
		if (level >= HallLevel.MAX_LEVEL) {
			Advancements.grant(master, "hall_level_9");
		}
	}

	// ================================================================== bosses (renown)

	private static boolean isBoss(LivingEntity entity) {
		EntityType<?> type = entity.getType();
		return type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN || type == EntityTypes.WITHER;
	}

	private static void afterDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float base, float taken, boolean blocked) {
		if (taken <= 0.0F || !isBoss(entity) || !(source.getEntity() instanceof DiscipleEntity disciple)
				|| disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.ownerId() == null) {
			return;
		}
		if (BOSS_DAMAGE.size() > 64) {
			BOSS_DAMAGE.clear();
		}
		BOSS_DAMAGE.computeIfAbsent(entity.getUUID(), key -> new HashMap<>()).merge(disciple.ownerId(), taken, Float::sum);
	}

	private static void afterDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		Map<UUID, Float> shares = BOSS_DAMAGE.remove(entity.getUUID());
		if (shares == null) {
			return;
		}
		String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		long day = dayIndex();
		for (Map.Entry<UUID, Float> share : shares.entrySet()) {
			CompoundTag core = coreOf(share.getKey());
			if (core == null || share.getValue() < entity.getMaxHealth() * BOSS_SHARE) {
				continue;
			}
			CompoundTag bosses = HallData.section(core, "boss_day");
			if (bosses.getLongOr(type, Long.MIN_VALUE) == day) {
				continue;
			}
			bosses.putLong(type, day);
			addRenown(share.getKey(), BOSS_RENOWN, "kultivator.hall.renown.boss");
		}
	}

	// ================================================================== the scroll (actions and HallInfo)

	/** Sends the hall's details and opens the scroll on {@code page} ({@code PAGE_*}; public for phase 6b, DESIGN 21.24). */
	public static void openScreen(ServerPlayer viewer, UUID owner, int page) {
		VIEWERS.put(viewer.getUUID(), owner);
		// The building checklist is re-read whenever the scroll opens (the result reaches the scroll on its next refresh).
		CompoundTag core = coreOf(owner);
		ServerLevel level = core == null ? null : levelOf(core);
		BlockPos pos = core == null ? null : posOf(core);
		if (level != null && pos != null && !dormant(core) && level.isLoaded(pos)) {
			scan(owner, level, pos);
		}
		sendInfo(viewer, owner);
		Net.send(viewer, new Payloads.OpenScreen(Payloads.OpenScreen.HALL, page));
	}

	/** Re-sends the details to a player whose scroll is open. */
	static void refreshViewer(ServerPlayer viewer) {
		UUID owner = VIEWERS.get(viewer.getUUID());
		if (owner != null) {
			sendInfo(viewer, owner);
		}
	}

	public static void sendInfo(ServerPlayer viewer, UUID owner) {
		Net.send(viewer, new Payloads.HallInfo(compose(viewer, owner)));
	}

	/** {@code Actions.HALL}: {@code arg} = the sub-op, {@code text} = "op[:param]". */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		int op = action.arg();
		String text = action.text() == null ? "" : action.text();
		UUID self = player.getUUID();
		if (op == OP_CLOSE) {
			VIEWERS.remove(self);
			return;
		}
		UUID owner = VIEWERS.getOrDefault(self, self);
		boolean own = owner.equals(self);
		CompoundTag core = coreOf(owner);
		if (core == null) {
			sendInfo(player, owner);
			return;
		}
		if (!own && op != OP_REFRESH && op != OP_OFFER) {
			return;
		}
		if (op == OP_REFRESH) {
			sendInfo(player, owner);
			return;
		}
		try {
			switch (op) {
				case OP_RENAME -> rename(player, owner, core, text);
				case OP_TEST -> Candidates.startTest(player, owner, parseInt(text), false);
				case OP_ACCEPT -> Candidates.accept(player, owner, parseInt(text));
				case OP_DISMISS -> Candidates.dismiss(player, owner, parseInt(text));
				case OP_MONSTER -> {
					core.putBoolean("monster", !core.getBooleanOr("monster", false));
					changed();
				}
				case OP_PROMOTE, OP_DEMOTE, OP_PROTECTOR, OP_GRANT -> discipleOp(player, owner, op, text);
				case OP_OFFER -> Contribution.offer(player, owner, core, parseInt(text));
				case OP_BUY -> {
					int split = text.indexOf(':');
					Contribution.buy(player, owner, core, split < 0 ? text : text.substring(0, split), split < 0 ? "" : text.substring(split + 1));
				}
				case OP_RESTORE -> LostArts.restore(player, owner, core, parseInt(text));
				default -> {
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sect Hall action {} failed", op, exception);
		}
		changed();
		sendInfo(player, owner);
	}

	private static int parseInt(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException exception) {
			return -1;
		}
	}

	/** Promote, demote, protector, grant: {@code text} = soul id (grant: "soul:amount"). */
	private static void discipleOp(ServerPlayer player, UUID owner, int op, String text) {
		String soul = text;
		int amount = 0;
		int split = text.indexOf(':');
		if (split > 0) {
			soul = text.substring(0, split);
			amount = parseInt(text.substring(split + 1));
		}
		DiscipleEntity disciple = findDisciple(owner, soul);
		if (disciple == null) {
			Msg.chat(player, Component.translatable("kultivator.hall.rank.not_loaded").withStyle(ChatFormatting.YELLOW));
			return;
		}
		switch (op) {
			case OP_PROMOTE -> SectRanks.promote(player, disciple, false);
			case OP_DEMOTE -> SectRanks.demote(player, disciple);
			case OP_PROTECTOR -> SectRanks.toggleProtector(player, disciple);
			case OP_GRANT -> SectRanks.grant(player, disciple, amount);
			default -> {
			}
		}
	}

	private static void rename(ServerPlayer player, UUID owner, CompoundTag core, String raw) {
		if (!Sect.validName(raw)) {
			Msg.chat(player, Component.translatable("kultivator.hall.rename_invalid", Sect.MIN_NAME, Sect.MAX_NAME).withStyle(ChatFormatting.YELLOW));
			return;
		}
		String name = Sect.normalizeName(raw);
		core.putString("name", name);
		changed();
		log(owner, Component.translatable("kultivator.hall.log.renamed", name));
		Msg.chat(player, Component.translatable("kultivator.hall.renamed", name).withStyle(ChatFormatting.GOLD));
	}

	/** Everything the scroll shows (DESIGN 20.3.1-20.3.8) plus the other modules' parts. */
	private static CompoundTag compose(ServerPlayer viewer, UUID owner) {
		CompoundTag info = new CompoundTag();
		CompoundTag core = coreOf(owner);
		info.putBoolean("has", core != null);
		CompoundTag cfg = new CompoundTag();
		cfg.putBoolean("kehancuran", Kehancuran.serverAllows());
		info.put("cfg", cfg);
		if (core == null) {
			for (InfoProvider provider : INFO_PROVIDERS) {
				provider.provide(viewer, owner, info);
			}
			return info;
		}
		boolean own = viewer.getUUID().equals(owner);
		info.putBoolean("readonly", !own);
		info.putString("owner", owner.toString());
		info.putString("owner_name", core.getStringOr("owner_name", ""));
		info.putString("name", core.getStringOr("name", ""));
		info.store("name_shown", ComponentSerialization.CODEC, hallName(core));
		Sect sect = server == null ? null : SectData.get(server).sectOf(owner);
		info.putString("sect", sect == null ? "" : sect.name());
		boolean dormant = dormant(core);
		info.putBoolean("dormant", dormant);
		BlockPos pos = posOf(core);
		if (pos != null) {
			info.putIntArray("pos", new int[]{pos.getX(), pos.getY(), pos.getZ()});
		}
		info.putString("dim", core.getStringOr("dim", ""));
		long clock = clockTime();
		long day = Math.floorDiv(clock, 24000L);
		info.putLong("day", day);
		info.putInt("time", (int) Math.floorMod(clock, 24000L));
		int level = levelOf(core, true);
		long renown = core.getLongOr("renown", 0L);
		info.putInt("level", level);
		info.putLong("renown", renown);
		info.putLong("renown_next", HallLevel.nextRenown(renown));
		info.putLong("renown_base", HallLevel.RENOWN[HallLevel.renownLevel(renown) - 1]);
		info.putInt("renown_level", HallLevel.renownLevel(renown));
		info.putInt("bcap", core.getIntOr("bcap", HallLevel.CAP_STELE));
		info.putInt("bflags", core.getIntOr("bflags", 0));
		info.put("bcount", core.getCompoundOrEmpty("bcount").copy());
		info.putInt("radius", radiusOf(owner, level));
		info.putInt("cap", discipleCap(owner, level));
		info.putInt("alive", own ? Disciples.aliveCount(Cultivators.get(viewer)) : -1);
		info.putDouble("price", HallLevel.price(level));
		info.putInt("posts", HallLevel.posts(level));
		info.putInt("missions", HallLevel.missions(level));
		info.putInt("per_arrival", Math.min(HallLevel.MAX_CANDIDATES, HallLevel.candidates(level) + (renown >= HallLevel.FAME_RENOWN ? 1 : 0)
				+ HallLevel.candidateBonus(owner)));
		ServerLevel hallLevel = levelOf(core);
		if (hallLevel != null && pos != null && !dormant) {
			GrandFormation.State state = GrandFormation.forHall(hallLevel, pos, owner);
			if (state != null) {
				CompoundTag formation = new CompoundTag();
				formation.putBoolean("grand", state.grand());
				formation.putInt("layers", state.layers());
				formation.putInt("radius", (int) Math.round(state.radius()));
				formation.putString("guard", state.guardName());
				formation.putDouble("damage", state.damageFactor());
				info.put("formation", formation);
			}
		}
		info.putLong("next_arrival", Candidates.nextArrivalDay(core, day));
		info.putBoolean("war_next", Candidates.nextIsWar(owner, core, day));
		info.putBoolean("war_today", Candidates.isWarDay(owner, day));
		info.putInt("interval", KultivatorConfig.get().recruitmentIntervalDays);
		info.put("candidates", Candidates.infoTags(owner, core, clock));
		info.putBoolean("monster", core.getBooleanOr("monster", false));
		ListTag away = new ListTag();
		long now = gameTime();
		for (AwayView view : AwayStore.views(core)) {
			CompoundTag entry = new CompoundTag();
			entry.store("name", ComponentSerialization.CODEC, view.name());
			entry.putString("reason", view.reason());
			entry.putLong("left", Math.max(0L, view.returnAt() - now));
			away.add(entry);
		}
		// Module B's teams (expeditions, envoys, the forbidden ground, the Ark) keep their own records (DESIGN 20.3.10).
		for (Tag tag : section(owner, "activity").getListOrEmpty("teams")) {
			if (!(tag instanceof CompoundTag team) || team.getBooleanOr("resolved", false)) {
				continue;
			}
			long left = Math.max(0L, team.getLongOr("return_at", 0L) - now);
			for (Tag each : team.getListOrEmpty("members")) {
				if (each instanceof CompoundTag member) {
					CompoundTag entry = new CompoundTag();
					entry.store("name", ComponentSerialization.CODEC, member.read("name", ComponentSerialization.CODEC)
							.orElse(Component.translatable("entity.kultivator.disciple")));
					entry.putString("reason", team.getStringOr("kind", "other"));
					entry.putLong("left", left);
					away.add(entry);
				}
			}
		}
		info.put("away", away);
		if (own) {
			info.put("disciples", discipleTags(viewer, owner, core));
		}
		info.putInt("elders_max", SectRanks.maxElders(owner));
		info.putInt("protectors_max", SectRanks.maxProtectors(owner));
		info.putIntArray("grants", SectRanks.GRANT_AMOUNTS);
		info.putInt("pool", core.getIntOr("pool", 0));
		info.putInt("offered_today", Contribution.offeredToday(core));
		info.putInt("offer_cap", Contribution.OFFER_DAILY_CAP);
		info.put("offers", Contribution.offers(viewer));
		info.put("shop", Contribution.shop(owner, core));
		// Slip Giok Teknik (DESIGN 22.8.4): every spec the pavilion sells, in grade order
		ListTag books = new ListTag();
		for (dev.kultivator.data.TechniqueSpec spec : Contribution.slips()) {
			books.add(net.minecraft.nbt.StringTag.valueOf(spec.id()));
		}
		info.put("books", books);
		info.putInt("arts", core.getIntOr("arts", 0));
		int[] pages = new int[LostArts.SETS];
		for (int set = 1; set <= LostArts.SETS; set++) {
			pages[set - 1] = LostArts.piecesCarried(viewer, set);
		}
		info.putIntArray("pages", pages);
		info.putBoolean("pavilion", (core.getIntOr("bflags", 0) & HallLevel.B_PAVILION) != 0);
		// Phase 6b: with a template the Aula tablet shows this line instead of the building checklist (DESIGN 21.17).
		Component buildStatus = statusLine(owner);
		if (buildStatus != null) {
			info.store("build_status", ComponentSerialization.CODEC, buildStatus);
		}
		info.put("log", core.getListOrEmpty("log").copy());
		for (InfoProvider provider : INFO_PROVIDERS) {
			try {
				provider.provide(viewer, owner, info);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Sect Hall info provider failed", exception);
			}
		}
		return info;
	}

	/** The master's disciples for the Jenjang tablet: loaded ones with their checklist, away and unloaded ones greyed. */
	private static ListTag discipleTags(ServerPlayer master, UUID owner, CompoundTag core) {
		ListTag list = new ListTag();
		Set<UUID> shown = new HashSet<>();
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.isReleasing()) {
				continue;
			}
			DiscipleData data = disciple.data();
			SectRanks.ensure(data);
			shown.add(disciple.soulId());
			CompoundTag entry = new CompoundTag();
			entry.putString("soul", disciple.soulId().toString());
			entry.putInt("eid", disciple.level() == master.level() ? disciple.getId() : -1);
			entry.store("name", ComponentSerialization.CODEC, disciple.getName());
			Rank rank = data.rank();
			entry.putInt("realm", rank.realm().ordinal());
			entry.putInt("stage", rank.stage());
			entry.putInt("layer", rank.layer());
			int sectRank = SectRanks.rank(data);
			entry.putInt("rank", sectRank);
			entry.putInt("pk", SectRanks.pk(data));
			entry.putBoolean("protector", SectRanks.isProtector(data));
			entry.putBoolean("heir", DisciplePaths.isHeir(data));
			entry.putInt("power", DisciplePaths.power(data));
			entry.putInt("stars", DisciplePaths.stars(data));
			entry.putInt("skin", disciple.skinIndex());
			entry.putBoolean("slim", disciple.slimSkin());
			entry.putInt("slots", SectRanks.slots(data));
			entry.putInt("learned", data.learnedIds().size());
			entry.put("reqs", SectRanks.requirementTags(master, data, disciple.soulId()));
			entry.putInt("cost", sectRank < SectRanks.MAX_RANK ? SectRanks.PK_COST[sectRank + 1] : 0);
			Component why = SectRanks.isProtector(data) ? null : SectRanks.protectorBlocker(master, data, disciple.soulId());
			entry.putBoolean("can_protect", why == null);
			if (why != null) {
				entry.store("protect_why", ComponentSerialization.CODEC, why);
			}
			list.add(entry);
		}
		for (AwayView view : AwayStore.views(core)) {
			if (shown.add(view.soul())) {
				CompoundTag entry = new CompoundTag();
				entry.putString("soul", view.soul().toString());
				entry.store("name", ComponentSerialization.CODEC, view.name());
				entry.putBoolean("away", true);
				entry.putString("reason", view.reason());
				int value = core.getCompoundOrEmpty("ranks").getIntOr(view.soul().toString(), -1);
				entry.putInt("rank", value < 0 ? SectRanks.LUAR : value & 15);
				list.add(entry);
			}
		}
		CompoundTag ranks = core.getCompoundOrEmpty("ranks");
		for (Tag tag : Cultivators.get(master).extra().getCompoundOrEmpty(Disciples.EXTRA).getListOrEmpty("list")) {
			if (!(tag instanceof CompoundTag listed) || listed.getBooleanOr("dead", false)) {
				continue;
			}
			UUID soul;
			try {
				soul = UUID.fromString(listed.getStringOr("id", ""));
			} catch (IllegalArgumentException exception) {
				continue;
			}
			if (!shown.add(soul)) {
				continue;
			}
			CompoundTag entry = new CompoundTag();
			entry.putString("soul", soul.toString());
			listed.read("name", ComponentSerialization.CODEC).ifPresent(name -> entry.store("name", ComponentSerialization.CODEC, name));
			if (listed.getBooleanOr("away", false)) {
				// On one of module B's errands (expedition, envoy, forbidden ground, the Ark): shown as away, with why.
				entry.putBoolean("away", true);
				entry.putString("reason", listed.getStringOr("away_reason", "other"));
			} else {
				entry.putBoolean("unloaded", true);
			}
			int value = ranks.getIntOr(soul.toString(), -1);
			entry.putInt("rank", value < 0 ? SectRanks.LUAR : value & 15);
			Rank rank = Rank.ofStep(listed.getIntOr("step", 0));
			entry.putInt("realm", rank.realm().ordinal());
			entry.putInt("stage", rank.stage());
			entry.putInt("layer", rank.layer());
			list.add(entry);
		}
		return list;
	}

	// ================================================================== commands

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		LiteralArgumentBuilder<CommandSourceStack> aula = Commands.literal("aula");
		aula.then(Commands.literal("info").executes(ctx -> infoCommand(ctx.getSource().getPlayerOrException())));
		aula.then(Commands.literal("nama").then(Commands.argument("nama", StringArgumentType.greedyString()).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			CompoundTag core = coreOf(player.getUUID());
			if (core == null) {
				Msg.chat(player, Component.translatable("kultivator.hall.none").withStyle(ChatFormatting.GRAY));
				return 0;
			}
			rename(player, player.getUUID(), core, StringArgumentType.getString(ctx, "nama"));
			return 1;
		})));
		root.then(aula);

		LiteralArgumentBuilder<CommandSourceStack> aulaDebug = Commands.literal("aula");
		aulaDebug.then(Commands.literal("renown").then(Commands.argument("n", IntegerArgumentType.integer(0, 1000000)).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			if (coreOf(player.getUUID()) == null) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.hall.none"));
				return 0;
			}
			addRenown(player.getUUID(), IntegerArgumentType.getInteger(ctx, "n"), "kultivator.hall.renown.debug");
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.hall.debug.done")), false);
			return 1;
		})));
		aulaDebug.then(Commands.literal("pk").then(Commands.argument("n", IntegerArgumentType.integer(-1000000, 1000000)).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			CompoundTag core = coreOf(player.getUUID());
			if (core == null) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.hall.none"));
				return 0;
			}
			core.putInt("pool", Math.max(0, core.getIntOr("pool", 0) + IntegerArgumentType.getInteger(ctx, "n")));
			changed();
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.hall.debug.pool", core.getIntOr("pool", 0))), false);
			return 1;
		})));
		aulaDebug.then(Commands.literal("calon").then(Commands.literal("datang")
				.executes(ctx -> debugArrive(ctx, -1))
				.then(Commands.argument("n", IntegerArgumentType.integer(1, HallLevel.MAX_CANDIDATES))
						.executes(ctx -> debugArrive(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
				.then(Commands.literal("perang").executes(ctx -> debugArrive(ctx, 0))));
		aulaDebug.then(Commands.literal("level").executes(ctx -> debugLevel(ctx.getSource().getPlayerOrException())));
		// Quick disciple tests (DESIGN 20.0): set the nearest own disciple's sect rank, or give it PK.
		aulaDebug.then(Commands.literal("jenjang").then(Commands.argument("rank", IntegerArgumentType.integer(0, SectRanks.MAX_RANK)).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			DiscipleEntity entity = nearestOwnDisciple(player);
			if (entity == null) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.hall.debug.no_disciple"));
				return 0;
			}
			int rank = IntegerArgumentType.getInteger(ctx, "rank");
			DiscipleData data = entity.data();
			SectRanks.setRank(data, rank);
			entity.setSyncedSectRank(rank);
			entity.refreshSynced();
			noteRank(player.getUUID(), entity.soulId(), rank, SectRanks.isProtector(data), DisciplePaths.power(data));
			changed();
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.hall.debug.done")), false);
			return 1;
		})));
		aulaDebug.then(Commands.literal("pkmurid").then(Commands.argument("n", IntegerArgumentType.integer(-1000000, 1000000)).executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			DiscipleEntity entity = nearestOwnDisciple(player);
			if (entity == null) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.hall.debug.no_disciple"));
				return 0;
			}
			SectRanks.addPk(entity.data(), IntegerArgumentType.getInteger(ctx, "n"), "kultivator.hall.renown.debug");
			changed();
			ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.hall.debug.done")), false);
			return 1;
		})));
		debug.then(aulaDebug);
	}

	/** The player's own loaded disciple nearest to it within 16 blocks in its level, or null. */
	private static @Nullable DiscipleEntity nearestOwnDisciple(ServerPlayer player) {
		DiscipleEntity best = null;
		double bestDistance = 16.0D * 16.0D;
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (disciple.level() != player.level()) {
				continue;
			}
			double distance = disciple.distanceToSqr(player);
			if (distance <= bestDistance) {
				best = disciple;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static int infoCommand(ServerPlayer player) {
		CompoundTag core = coreOf(player.getUUID());
		if (core == null) {
			Msg.chat(player, Component.translatable("kultivator.hall.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		int level = levelOf(core, true);
		BlockPos pos = posOf(core);
		player.sendSystemMessage(Component.translatable("kultivator.hall.info.header", hallName(core), level)
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		long renown = core.getLongOr("renown", 0L);
		long next = HallLevel.nextRenown(renown);
		player.sendSystemMessage(Component.translatable("kultivator.hall.info.renown", renown, next < 0 ? "-" : String.valueOf(next),
				core.getIntOr("bcap", HallLevel.CAP_STELE)).withStyle(ChatFormatting.GRAY));
		UUID owner = player.getUUID();
		player.sendSystemMessage(Component.translatable("kultivator.hall.info.numbers", discipleCap(owner, level), radiusOf(owner, level),
				core.getIntOr("pool", 0)).withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.translatable(dormant(core) ? "kultivator.hall.info.dormant" : "kultivator.hall.info.where",
				pos == null ? "?" : pos.getX() + " " + pos.getY() + " " + pos.getZ(), core.getStringOr("dim", "")).withStyle(ChatFormatting.GRAY));
		long day = dayIndex();
		player.sendSystemMessage(Component.translatable("kultivator.hall.info.schedule", day, Candidates.nextArrivalDay(core, day),
				Candidates.list(core).size()).withStyle(ChatFormatting.GRAY));
		Component buildStatus = statusLine(owner);
		if (buildStatus != null) {
			player.sendSystemMessage(buildStatus.copy().withStyle(style -> style.getColor() == null ? style.withColor(ChatFormatting.GRAY) : style));
		}
		return 1;
	}

	private static int debugArrive(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		UUID owner = player.getUUID();
		CompoundTag core = coreOf(owner);
		ServerLevel level = core == null ? null : levelOf(core);
		BlockPos pos = core == null ? null : posOf(core);
		if (core == null || level == null || pos == null || dormant(core) || !level.isLoaded(pos)) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.hall.debug.no_active"));
			return 0;
		}
		if (count == 0) {
			// "calon perang": the eight youths of a Hundred Dynasties War day arrive now.
			int war = Candidates.arriveWar(ctx.getSource().getServer(), owner, core, level, pos, player);
			changed();
			return war;
		}
		int n = count > 0 ? count : Math.min(HallLevel.MAX_CANDIDATES, HallLevel.candidates(levelOf(core, true)) + HallLevel.candidateBonus(owner));
		Candidates.arrive(ctx.getSource().getServer(), owner, core, level, pos, player, n);
		changed();
		return n;
	}

	private static int debugLevel(ServerPlayer player) {
		CompoundTag core = coreOf(player.getUUID());
		if (core == null) {
			Msg.chat(player, Component.translatable("kultivator.hall.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		CompoundTag counts = core.getCompoundOrEmpty("bcount");
		long renown = core.getLongOr("renown", 0L);
		Msg.chat(player, Component.translatable("kultivator.hall.debug.level", levelOf(core, true), HallLevel.renownLevel(renown),
				core.getIntOr("bcap", HallLevel.CAP_STELE), counts.getIntOr("books", 0), counts.getIntOr("lecterns", 0),
				counts.getIntOr("targets", 0), counts.getIntOr("rooms", 0),
				Component.translatable(counts.getBooleanOr("formation", false) ? "gui.yes" : "gui.no")));
		ServerLevel level = levelOf(core);
		BlockPos pos = posOf(core);
		if (level != null && pos != null && !dormant(core)) {
			scan(player.getUUID(), level, pos);
		}
		return 1;
	}

	// ================================================================== API (DESIGN 20.4.5)

	/** The owner's hall, or null when the owner never founded one (a cheap cached lookup). */
	public static @Nullable HallView hallOf(UUID owner) {
		if (owner == null || server == null) {
			return null;
		}
		HallView cached = VIEW_CACHE.get(owner);
		if (cached != null) {
			return cached;
		}
		CompoundTag core = coreOf(owner);
		BlockPos pos = core == null ? null : posOf(core);
		ResourceKey<Level> dim = core == null ? null : dimOf(core);
		if (core == null || pos == null || dim == null) {
			return null;
		}
		int level = levelOf(core, true);
		HallView view = new HallView(pos, dim, level, core.getLongOr("renown", 0L), radiusOf(owner, level), !dormant(core),
				core.getStringOr("name", ""));
		VIEW_CACHE.put(owner, view);
		return view;
	}

	/** The hall's name: the one its master chose, else "Sect of <owner>" in the reader's language. */
	public static Component hallName(CompoundTag core) {
		String name = core == null ? "" : core.getStringOr("name", "");
		return name.isBlank() ? Component.translatable("kultivator.hall.default_name", core == null ? "?" : core.getStringOr("owner_name", "?"))
				: Component.literal(name);
	}

	/** Hall level 1-9 (0 without a hall). */
	public static int level(UUID owner) {
		HallView hall = hallOf(owner);
		return hall == null ? 0 : hall.level();
	}

	/** The disciple limit the hall allows ({@link Disciples#MAX_DISCIPLES} without a hall). */
	public static int cap(UUID owner) {
		HallView hall = hallOf(owner);
		return hall == null ? Disciples.MAX_DISCIPLES : discipleCap(owner, hall.level());
	}

	/** True when {@code pos} in {@code level} is inside the owner's active hall territory. */
	public static boolean inTerritory(UUID owner, Level level, Vec3 pos) {
		HallView hall = hallOf(owner);
		if (hall == null || !hall.active() || level == null || !level.dimension().equals(hall.dim())) {
			return false;
		}
		double dx = pos.x - (hall.pos().getX() + 0.5D);
		double dz = pos.z - (hall.pos().getZ() + 0.5D);
		return dx * dx + dz * dz <= (double) hall.radius() * hall.radius() && Math.abs(pos.y - hall.pos().getY()) <= TERRITORY_HEIGHT;
	}

	/** Renown never decreases; {@code reasonKey} is a translation key for the log. */
	public static void addRenown(UUID owner, int amount, String reasonKey) {
		CompoundTag core = coreOf(owner);
		if (core == null || amount <= 0) {
			return;
		}
		core.putLong("renown", Math.min(Long.MAX_VALUE / 4L, core.getLongOr("renown", 0L) + amount));
		changed();
		log(owner, Component.translatable("kultivator.hall.log.renown", amount,
				Component.translatable(reasonKey == null || reasonKey.isEmpty() ? "kultivator.hall.renown.other" : reasonKey)));
		checkLevel(owner, core);
	}

	/** Adds Poin Kontribusi to the hall pool. */
	public static void addPool(UUID owner, int amount) {
		CompoundTag core = coreOf(owner);
		if (core == null || amount == 0) {
			return;
		}
		core.putInt("pool", (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) core.getIntOr("pool", 0) + amount)));
		changed();
	}

	/** Takes PK from the hall pool; false (nothing taken) when there is not enough. */
	public static boolean spendPool(UUID owner, int amount) {
		CompoundTag core = coreOf(owner);
		if (core == null || amount < 0 || core.getIntOr("pool", 0) < amount) {
			return false;
		}
		core.putInt("pool", core.getIntOr("pool", 0) - amount);
		changed();
		return true;
	}

	/** A line in the hall's Catatan (log) tablet, stamped with the day index; the last 50 are kept. */
	public static void log(UUID owner, Component line) {
		CompoundTag core = coreOf(owner);
		if (core == null || line == null) {
			return;
		}
		ListTag log = core.getList("log").orElse(null);
		if (log == null) {
			log = new ListTag();
			core.put("log", log);
		}
		CompoundTag entry = new CompoundTag();
		entry.putLong("day", dayIndex());
		entry.store("text", ComponentSerialization.CODEC, line);
		log.add(entry);
		while (log.size() > LOG_SIZE) {
			log.remove(0);
		}
		changed();
	}

	/** A module's own section of the owner's HallData ({@code activity} for B, {@code kehancuran} for D). */
	public static CompoundTag section(UUID owner, String key) {
		HallData data = data();
		CompoundTag entry = data == null || owner == null ? null : data.entry(owner);
		return entry == null ? new CompoundTag() : HallData.section(entry, key);
	}

	/** Marks the hall data to be saved. */
	public static void setDirty() {
		changed();
	}

	/** A module's part of a {@code HallInfo}: {@code owner}'s hall as {@code viewer} sees it (a sect mate reads it only). */
	@FunctionalInterface
	public interface InfoProvider {
		void provide(ServerPlayer viewer, UUID owner, CompoundTag info);
	}

	/** Adds a module's part to every {@code HallInfo} sent to a player. */
	public static void addInfoProvider(InfoProvider provider) {
		INFO_PROVIDERS.add(provider);
	}

	/** Whose hall scroll the player has open right now (their own when none or their own). */
	public static UUID viewedOwner(ServerPlayer player) {
		return VIEWERS.getOrDefault(player.getUUID(), player.getUUID());
	}

	/**
	 * Sends a disciple away until {@code returnAt} (game time): takes it out with {@code Disciples.depart(entity, reason)}
	 * (which hands back the same tag when the caller already departed it) and keeps that tag with {@code info}. Without a
	 * hall nothing happens (the disciple stays).
	 */
	public static void storeAway(UUID owner, DiscipleEntity entity, long returnAt, String reason, CompoundTag info) {
		CompoundTag core = coreOf(owner);
		if (core == null || entity == null) {
			return;
		}
		CompoundTag stored = Disciples.depart(entity, reason);
		if (stored.isEmpty()) {
			return;
		}
		AwayStore.store(core, stored, returnAt, reason, info);
		Disciples.setAwayUntil(server, owner, entity.soulId(), returnAt);
		changed();
	}

	/** The handler for away records of {@code reason} (one per reason). */
	public static void addReturnHandler(String reason, ReturnHandler handler) {
		AwayStore.HANDLERS.put(reason, handler);
	}

	/** The owner's disciples away from the world right now. */
	public static List<AwayView> away(UUID owner) {
		CompoundTag core = coreOf(owner);
		return core == null ? List.of() : AwayStore.views(core);
	}

	/** Candidates sent by other modules arrive at the hall (they stay until the next sunrise). */
	public static void addCandidates(UUID owner, List<CandidateSpec> candidates) {
		CompoundTag core = coreOf(owner);
		if (core == null || dormant(core) || candidates == null || candidates.isEmpty()) {
			return;
		}
		Candidates.addFromSpecs(owner, core, candidates);
	}

	/** Restored lost arts: bit 1 Plum Blossom, 2 Purple Mist, 4 Northern Heaven. */
	public static int arts(UUID owner) {
		CompoundTag core = coreOf(owner);
		return core == null ? 0 : core.getIntOr("arts", 0);
	}

	// ================================================================== phase 6b hooks (DESIGN 21.17, 21.24 item 5)

	/**
	 * A building cap from outside the scan (Pembangunan Sekte: {@code max(frozenScanCap, tier)}): the highest value of 0 or
	 * more replaces the scanned cap and the building scan is skipped; -1 from every provider keeps the scan.
	 */
	public static void addBuildingCapProvider(ToIntFunction<UUID> provider) {
		HallLevel.addCapProvider(provider);
	}

	/**
	 * The building bits while a provided cap applies (the tier's {@code B_PAVILION}, {@code B_DRILL}, {@code B_TOWER});
	 * the bits of every provider are combined, negative values ignored. {@code B_FORMATION} stays the real check.
	 */
	public static void addBuildingFlagsProvider(ToIntFunction<UUID> provider) {
		HallLevel.addFlagsProvider(provider);
	}

	/** A minimum territory radius (the template's R_t); the radius is {@code max(20 + 4L, every provider)}. */
	public static void addRadiusProvider(ToIntFunction<UUID> provider) {
		HallLevel.addRadiusProvider(provider);
	}

	/** Extra disciple cap added to {@code 5 + floor(L/2)} (summed, negative values ignored). */
	public static void addCapBonusProvider(ToIntFunction<UUID> provider) {
		HallLevel.addCapBonusProvider(provider);
	}

	/** Extra candidates per arrival (summed, negative values ignored); the total stays at most {@code MAX_CANDIDATES}. */
	public static void addCandidateBonusProvider(ToIntFunction<UUID> provider) {
		HallLevel.addCandidateBonusProvider(provider);
	}

	/**
	 * Phase 8 (DESIGN 25.2 item 13): while any provider returns true for the owner (a live sect raid), no candidates arrive
	 * (the arrival waits for the next check) and their bodies step aside; the records stay and the bodies come back after.
	 */
	public static void addRecruitPauseProvider(Predicate<UUID> provider) {
		if (provider != null) {
			RECRUIT_PAUSE_PROVIDERS.add(provider);
		}
	}

	/** True while a recruit-pause provider holds the owner's candidates back. */
	public static boolean recruitPaused(UUID owner) {
		for (Predicate<UUID> provider : RECRUIT_PAUSE_PROVIDERS) {
			try {
				if (provider.test(owner)) {
					return true;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Sect Hall recruit-pause provider failed", exception);
			}
		}
		return false;
	}

	/** Where candidates arrive (the first provider with a usable spot wins; none keeps the 8-14 ring). */
	public static void addArrivalSpotProvider(ArrivalSpotProvider provider) {
		if (provider != null) {
			ARRIVAL_SPOT_PROVIDERS.add(provider);
		}
	}

	/** The Aula tablet's construction line replacing the building checklist (the first non-null line wins). */
	public static void addStatusLineProvider(StatusLineProvider provider) {
		if (provider != null) {
			STATUS_LINE_PROVIDERS.add(provider);
		}
	}

	/** Hears the stele being moved, woken or removed (see {@link SteleMoveListener}). */
	public static void addSteleMoveListener(SteleMoveListener listener) {
		if (listener != null) {
			STELE_MOVE_LISTENERS.add(listener);
		}
	}

	/**
	 * The hall's current building cap ({@code core.bcap}): the last building scan, or the provided cap while one applies;
	 * phase 6b freezes it at confirmation ({@code frozenScanCap}, DESIGN 21.17). 0 without a hall.
	 */
	public static int buildingCap(UUID owner) {
		CompoundTag core = coreOf(owner);
		return core == null ? 0 : core.getIntOr("bcap", HallLevel.CAP_STELE);
	}

	/**
	 * The cap of the last real building scan ({@code core.scan_bcap}), never a provided cap such as phase 6b's move
	 * guarantee; a hall scanned before this value existed falls back to {@code bcap} only while no cap is provided.
	 */
	public static int scanCap(UUID owner) {
		CompoundTag core = coreOf(owner);
		if (core == null) {
			return 0;
		}
		int scanned = core.getIntOr("scan_bcap", -1);
		if (scanned >= 0) {
			return scanned;
		}
		return HallLevel.providedCap(owner) < 0 ? core.getIntOr("bcap", HallLevel.CAP_STELE) : HallLevel.CAP_STELE;
	}

	/**
	 * Re-applies the building cap and every provided value now (phase 6b calls it when a tier completes or the template
	 * changes; otherwise they are read again within 30 s): clears the cached hall view and rescans or applies the cap.
	 */
	public static void refreshBuildings(UUID owner) {
		CompoundTag core = coreOf(owner);
		if (core == null) {
			return;
		}
		changed();
		ServerLevel level = levelOf(core);
		BlockPos pos = posOf(core);
		if (level != null && pos != null && !dormant(core) && level.isLoaded(pos)) {
			scan(owner, level, pos);
		} else {
			checkLevel(owner, core);
		}
	}

	/**
	 * Moves the player's hall to the stele at {@code pos} (or wakes it there when dormant) with module A's rules (a stele
	 * stands there, no other hall within 128 blocks, the one-day move cooldown) without asking the stele-move listeners:
	 * phase 6b calls it from its move dialog. False, with a chat line, when refused.
	 */
	public static boolean relocateHall(ServerPlayer player, ServerLevel level, BlockPos pos) {
		UUID owner = player.getUUID();
		CompoundTag core = coreOf(owner);
		if (core == null || !level.getBlockState(pos).is(HallItems.SECT_HALL_STELE)) {
			return false;
		}
		boolean wake = dormant(core);
		if (!wake && pos.equals(posOf(core)) && level.dimension().equals(dimOf(core))) {
			return true;
		}
		UUID other = otherHallNear(owner, level, pos);
		if (other != null) {
			CompoundTag otherCore = coreOf(other);
			Msg.chat(player, Component.translatable("kultivator.hall.too_close", otherCore == null ? "?" : hallName(otherCore),
					(int) OTHER_HALL_RANGE).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!wake) {
			long moved = Cultivators.get(player).extra().getCompoundOrEmpty("hall").getLongOr("moved_day", Long.MIN_VALUE / 2L);
			if (dayIndex() - moved < 1L) {
				Msg.chat(player, Component.translatable("kultivator.hall.move_cooldown").withStyle(ChatFormatting.YELLOW));
				return false;
			}
		}
		MOVES.remove(owner);
		relocate(player, owner, core, level, pos.immutable(), wake);
		return true;
	}

	/** Territory radius of a hall of {@code level}: {@code max(20 + 4L, provided R_t)} (DESIGN 21.17). */
	static int radiusOf(UUID owner, int level) {
		return Math.max(HallLevel.radius(level), HallLevel.providedRadius(owner));
	}

	/** Disciple cap of a hall of {@code level}: {@code 5 + floor(L/2)} plus the provided bonus. */
	static int discipleCap(UUID owner, int level) {
		return HallLevel.cap(level) + HallLevel.capBonus(owner);
	}

	/** The provided construction status line, or null. */
	static @Nullable Component statusLine(UUID owner) {
		for (StatusLineProvider provider : STATUS_LINE_PROVIDERS) {
			try {
				Component line = provider.statusLine(owner);
				if (line != null) {
					return line;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Sect Hall status-line provider failed", exception);
			}
		}
		return null;
	}

	/** A provided arrival spot for a candidate, or null. */
	static @Nullable BlockPos arrivalSpot(UUID owner, ServerLevel level, BlockPos stele, RandomSource random) {
		for (ArrivalSpotProvider provider : ARRIVAL_SPOT_PROVIDERS) {
			try {
				BlockPos spot = provider.spot(owner, level, stele, random);
				if (spot != null) {
					return spot;
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("A Sect Hall arrival-spot provider failed", exception);
			}
		}
		return null;
	}

	private Halls() {
	}
}
