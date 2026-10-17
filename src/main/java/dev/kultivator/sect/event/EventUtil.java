package dev.kultivator.sect.event;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared helpers of module B: the server and its clocks (DESIGN 20.1 "Time": day index from the overworld clock), the
 * module's {@code HallData} section ({@code activity}, DESIGN 20.4.6) and the disciple's {@code extra.activity}, item
 * hand-outs, effects and lookups. Read-only towards blocks.
 */
final class EventUtil {

	/** Ticks of one in-game day. */
	static final long DAY = 24000L;
	/** HallData section of this module. */
	static final String SECTION = "activity";

	private static @Nullable MinecraftServer server;
	private static int revision;

	static void setServer(@Nullable MinecraftServer value) {
		server = value;
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	/** Game time of the overworld (0 without a server). */
	static long gameTime() {
		return server == null ? 0L : server.overworld().getGameTime();
	}

	/** The overworld clock: day time (0 without a server). */
	static long clock() {
		return server == null ? 0L : server.overworld().getOverworldClockTime();
	}

	/** Day index {@code d = floor(overworld day time / 24000)} (DESIGN 20.1). */
	static long day() {
		return Math.floorDiv(clock(), DAY);
	}

	/** True when {@code mark} (ticks into a day: 0 sunrise, 6000 noon) was passed going from {@code before} to {@code now}. */
	static boolean crossed(long before, long now, long mark) {
		if (now <= before) {
			return false;
		}
		long next = Math.floorDiv(before - mark, DAY) * DAY + mark + DAY;
		return next <= now;
	}

	/** A growing number for every info tag (the client keeps the newest). */
	static int nextRevision() {
		return ++revision;
	}

	// ================================================================== data

	/** The owner's live {@code activity} section of the hall data. */
	static CompoundTag section(UUID owner) {
		return Halls.section(owner, SECTION);
	}

	static void dirty() {
		Halls.setDirty();
	}

	/** The live child compound {@code key} of {@code parent}, created when missing. */
	static CompoundTag sub(CompoundTag parent, String key) {
		CompoundTag child = parent.getCompound(key).orElse(null);
		if (child == null) {
			child = new CompoundTag();
			parent.put(key, child);
		}
		return child;
	}

	/** The live list {@code key} of {@code parent}, created when missing. */
	static ListTag list(CompoundTag parent, String key) {
		ListTag child = parent.getList(key).orElse(null);
		if (child == null) {
			child = new ListTag();
			parent.put(key, child);
		}
		return child;
	}

	/** The disciple's own {@code extra.activity} (live, created when missing). */
	static CompoundTag extra(DiscipleData data) {
		return data.extraSection(Duties.EXTRA);
	}

	/** Registry-aware NBT ops (item stacks need them); null without a server. */
	static @Nullable RegistryOps<Tag> ops() {
		HolderLookup.Provider registries = server == null ? null : server.registryAccess();
		return registries == null ? null : registries.createSerializationContext(NbtOps.INSTANCE);
	}

	static @Nullable UUID uuid(String text) {
		try {
			return text == null || text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	// ================================================================== hall and disciples

	/** The level of the owner's hall when it is loaded on this server, else null. */
	static @Nullable ServerLevel hallLevel(Halls.HallView hall) {
		return server == null || hall == null || hall.dim() == null ? null : server.getLevel(hall.dim());
	}

	/** The stele's foot: its block position, bottom centre. */
	static Vec3 center(Halls.HallView hall) {
		return Vec3.atBottomCenterOf(hall.pos());
	}

	/** True when the hall stands, its chunk is loaded and its level exists. */
	static boolean hallLoaded(Halls.HallView hall) {
		ServerLevel level = hallLevel(hall);
		return hall.active() && level != null && level.isLoaded(hall.pos());
	}

	/** The master's loaded disciple with this soul, or null. */
	static @Nullable DiscipleEntity bySoul(UUID owner, UUID soul) {
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.soulId().equals(soul)) {
				return disciple;
			}
		}
		return null;
	}

	/** True when the disciple stands in the hall's level within {@code range} blocks of the stele. */
	static boolean nearHall(DiscipleEntity disciple, Halls.HallView hall, double range) {
		return hall.dim() != null && hall.dim().equals(disciple.level().dimension())
				&& disciple.distanceToSqr(center(hall)) <= range * range;
	}

	/** A disciple free for an errand: alive, a real disciple, not breaking through, not duelling, not bowing out. */
	static boolean free(DiscipleEntity disciple) {
		return disciple.isAlive() && !disciple.isRemoved() && disciple.role() == DiscipleEntity.Role.DISCIPLE && !disciple.isReleasing()
				&& !disciple.data().breakingThrough() && !dev.kultivator.disciple.DuelRules.inDuel(disciple);
	}

	// ================================================================== items, effects, text

	/** Gives the stack to the player; what does not fit lands at their feet (an item entity, no block touched). */
	static void give(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5D, player.getZ(), stack);
			item.addTag(NoGrief.TAG);
			item.setPortalCooldown();
			player.level().addFreshEntity(item);
		}
	}

	static void fx(ServerLevel level, Vec3 pos, int kind, int b, float f) {
		Fx.send(level, pos, FxType.SECT_EVENT, kind, b, f);
	}

	static void fx(ServerLevel level, Vec3 pos, Vec3 target, int kind, int b, float f) {
		Fx.send(level, pos, target, FxType.SECT_EVENT, kind, b, f);
	}

	static void chat(@Nullable ServerPlayer player, Component message) {
		if (player != null) {
			Msg.chat(player, message);
		}
	}

	/** The owner when online, else null. */
	static @Nullable ServerPlayer player(UUID owner) {
		return server == null ? null : server.getPlayerList().getPlayer(owner);
	}

	/** A block position stored as a long, or null for the missing marker. */
	static @Nullable BlockPos pos(CompoundTag tag, String key) {
		return tag.contains(key) ? BlockPos.of(tag.getLongOr(key, 0L)) : null;
	}

	private EventUtil() {
	}
}
