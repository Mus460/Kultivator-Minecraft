package dev.kultivator.sect.hall;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.fx.Fx;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Disciples away from the world (expeditions, envoys, soul flight...): pure data in {@code core.away} with zero ticking
 * (DESIGN 20.1). A record holds the {@code Disciples.depart} tag, the reason, the game time it is due and the caller's
 * info. When due (checked by the hall tick, so also right after the master joins) the reason's {@link Halls.ReturnHandler}
 * runs once, then the disciple arrives beside the stele, or beside the master while the hall is dormant.
 */
final class AwayStore {

	static final String KEY = "away";
	private static final int ARRIVE_COLOR = 0xBFE8FF;
	static final Map<String, Halls.ReturnHandler> HANDLERS = new HashMap<>();

	/** Keeps a departed disciple ({@code stored} from {@code Disciples.depart}) until {@code returnAt}. */
	static void store(CompoundTag core, CompoundTag stored, long returnAt, String reason, CompoundTag info) {
		CompoundTag rec = new CompoundTag();
		stored.read("soul", UUIDUtil.CODEC).ifPresent(soul -> rec.store("soul", UUIDUtil.CODEC, soul));
		stored.read("name", ComponentSerialization.CODEC).ifPresent(name -> rec.store("name", ComponentSerialization.CODEC, name));
		rec.putString("reason", reason == null ? "" : reason);
		rec.putLong("return_at", returnAt);
		rec.put("info", info == null ? new CompoundTag() : info.copy());
		rec.put("stored", stored.copy());
		list(core).add(rec);
	}

	static ListTag list(CompoundTag core) {
		ListTag list = core.getList(KEY).orElse(null);
		if (list == null) {
			list = new ListTag();
			core.put(KEY, list);
		}
		return list;
	}

	static List<Halls.AwayView> views(CompoundTag core) {
		List<Halls.AwayView> views = new ArrayList<>();
		for (Tag tag : list(core)) {
			if (tag instanceof CompoundTag rec) {
				UUID soul = rec.read("soul", UUIDUtil.CODEC).orElse(null);
				if (soul == null) {
					continue;
				}
				Component name = rec.read("name", ComponentSerialization.CODEC).orElse(Component.translatable("entity.kultivator.disciple"));
				views.add(new Halls.AwayView(soul, name, rec.getStringOr("reason", ""), rec.getLongOr("return_at", 0L),
						rec.getCompoundOrEmpty("info").copy()));
			}
		}
		return views;
	}

	/** The cultivation data of every away disciple (for rank counts and the strongest disciple). */
	static List<DiscipleData> data(CompoundTag core) {
		List<DiscipleData> list = new ArrayList<>();
		for (Tag tag : list(core)) {
			if (tag instanceof CompoundTag rec) {
				CompoundTag disciple = rec.getCompoundOrEmpty("stored").getCompoundOrEmpty("disciple");
				if (!disciple.isEmpty()) {
					list.add(DiscipleData.load(disciple));
				}
			}
		}
		return list;
	}

	/** True when the soul is away. */
	static boolean isAway(CompoundTag core, UUID soul) {
		for (Tag tag : list(core)) {
			if (tag instanceof CompoundTag rec && soul.equals(rec.read("soul", UUIDUtil.CODEC).orElse(null))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Brings back every due record of {@code owner}. {@code hallLevel} is the hall's level while it is active (null when
	 * dormant or unloaded). A record that cannot arrive yet (chunk not loaded, no room, master offline) stays and is tried
	 * again on the next hall tick; its handler ran only once.
	 */
	static boolean tick(MinecraftServer server, UUID owner, CompoundTag core, @Nullable ServerLevel hallLevel, @Nullable BlockPos stele) {
		ListTag list = list(core);
		if (list.isEmpty()) {
			return false;
		}
		long now = server.overworld().getGameTime();
		boolean changed = false;
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		for (int i = list.size() - 1; i >= 0; i--) {
			if (!(list.get(i) instanceof CompoundTag rec) || rec.getLongOr("return_at", 0L) > now) {
				continue;
			}
			ServerLevel level = hallLevel != null ? hallLevel : master != null ? master.level() : null;
			if (level == null) {
				continue;
			}
			CompoundTag stored = rec.getCompoundOrEmpty("stored");
			if (!rec.getBooleanOr("handled", false)) {
				Halls.ReturnHandler handler = HANDLERS.get(rec.getStringOr("reason", ""));
				Vec3 wanted = null;
				if (handler != null) {
					try {
						wanted = handler.onReturn(level, owner, stored, rec.getCompoundOrEmpty("info"));
					} catch (RuntimeException exception) {
						dev.kultivator.Kultivator.LOGGER.error("Sect Hall return handler for {} failed", rec.getStringOr("reason", ""), exception);
					}
				}
				rec.putBoolean("handled", true);
				if (wanted != null) {
					rec.putDouble("ax", wanted.x);
					rec.putDouble("ay", wanted.y);
					rec.putDouble("az", wanted.z);
					rec.putString("adim", level.dimension().identifier().toString());
				}
				changed = true;
			}
			ServerLevel arriveLevel = level;
			Vec3 at;
			if (rec.contains("ax") && rec.getStringOr("adim", "").equals(level.dimension().identifier().toString())) {
				at = new Vec3(rec.getDoubleOr("ax", 0.0D), rec.getDoubleOr("ay", 0.0D), rec.getDoubleOr("az", 0.0D));
			} else if (hallLevel != null && stele != null && hallLevel.isLoaded(stele)) {
				at = Vec3.atBottomCenterOf(stele).add(2.0D, 0.0D, 0.0D);
				arriveLevel = hallLevel;
			} else if (master != null && !master.isSpectator()) {
				at = master.position();
				arriveLevel = master.level();
			} else {
				continue;
			}
			DiscipleEntity back = Disciples.arrive(arriveLevel, at, stored, owner);
			if (back == null) {
				continue;
			}
			list.remove(i);
			changed = true;
			Vec3 pos = back.position();
			Fx.burst(arriveLevel, pos.add(0.0D, 1.0D, 0.0D), ARRIVE_COLOR, 24, 1.0F);
			Fx.ring(arriveLevel, pos.add(0.0D, 0.1D, 0.0D), ARRIVE_COLOR, 1.8F);
			arriveLevel.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.7F, 1.2F);
			if (master != null) {
				Msg.chat(master, Component.translatable("kultivator.hall.away.back", back.getDisplayName()).withStyle(ChatFormatting.AQUA));
			}
		}
		return changed;
	}

	private AwayStore() {
	}
}
