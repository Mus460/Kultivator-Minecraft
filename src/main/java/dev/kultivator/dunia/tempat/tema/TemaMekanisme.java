package dev.kultivator.dunia.tempat.tema;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bodies of the themed-town mechanisms, boats and creatures (DESIGN 38.T.0 butir 1, 38.T.2.7): every one is a
 * {@code noSave} entity made from an anchor or an NBT point when a player comes near and removed when the last player
 * leaves or the chunk unloads. Budgets: at most {@code kotaTema.mekanismeMaks} (12) live bodies per town and
 * {@value #GLOBAL} in the world ({@code DuniaBudget} kind {@value #JENIS}); a body only appears while a player is within
 * {@value #JARAK} blocks of its spot. Every 40 ticks the keeper removes bodies whose town has no player within
 * {@value #JARAK_LEPAS} blocks (tick-spread; no world scan: it walks its own list).
 */
public final class TemaMekanisme {

	public static final String JENIS = "mekanisme_tema";
	public static final int GLOBAL = 24;
	public static final int JARAK = 96;
	public static final int JARAK_LEPAS = 128;

	private record Hidup(Entity entity, long situsId) {
	}

	private static final Map<Long, List<Hidup>> PER_KOTA = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		DuniaBudget.setCap(JENIS, GLOBAL);
		ServerTickEvents.END_SERVER_TICK.register(TemaMekanisme::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PER_KOTA.clear());
	}

	/** True when one more body may appear for town {@code situsId} at {@code at} (town and world caps, a player near). */
	public static boolean boleh(ServerLevel level, long situsId, Vec3 at) {
		if (!TemaConfig.inti().aktif || level == null || at == null) {
			return false;
		}
		if (hidup(situsId).size() >= TemaConfig.inti().mekanismeMaks || !DuniaBudget.mayAdd(level, JENIS)) {
			return false;
		}
		return pemainDekat(level, at, JARAK);
	}

	/**
	 * Makes a body of {@code type} at {@code pos} facing {@code yaw} for town {@code kota} (null: a test or satellite
	 * body, site 0) bound to {@code jangkar} (the anchor or point block); null when a cap says no or the type did not
	 * build. {@link TemaMekanismeEntity} bodies are bound with {@code ikat}; other entities (the three creatures) are only
	 * tracked. The caller keeps the reference and calls {@link #lepas} when done.
	 */
	public static <T extends Entity> @Nullable T muncul(ServerLevel level, EntityType<T> type, Vec3 pos, float yaw, @Nullable TemaKota.Kota kota,
			@Nullable BlockPos jangkar) {
		long situsId = kota == null ? 0L : kota.situsId();
		if (!boleh(level, situsId, pos)) {
			return null;
		}
		T entity;
		try {
			entity = type.create(level, EntitySpawnReason.EVENT);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Themed body {} could not be made", type, exception);
			return null;
		}
		if (entity == null) {
			return null;
		}
		entity.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		if (entity instanceof TemaMekanismeEntity m) {
			m.ikat(situsId, jangkar == null ? 0L : jangkar.asLong());
		}
		if (!level.addFreshEntity(entity)) {
			return null;
		}
		DuniaBudget.track(JENIS, entity);
		PER_KOTA.computeIfAbsent(situsId, k -> new ArrayList<>()).add(new Hidup(entity, situsId));
		return entity;
	}

	/** Removes a body made by {@link #muncul} (safe on null and on a body already gone). */
	public static void lepas(@Nullable Entity entity) {
		if (entity == null) {
			return;
		}
		for (List<Hidup> list : PER_KOTA.values()) {
			list.removeIf(h -> h.entity() == entity);
		}
		if (!entity.isRemoved()) {
			entity.discard();
		}
	}

	/** The live bodies of town {@code situsId}. */
	public static List<Entity> hidup(long situsId) {
		List<Hidup> list = PER_KOTA.get(situsId);
		if (list == null) {
			return List.of();
		}
		list.removeIf(h -> h.entity().isRemoved());
		List<Entity> out = new ArrayList<>(list.size());
		for (Hidup h : list) {
			out.add(h.entity());
		}
		return out;
	}

	/** Every live body (all towns). */
	public static int jumlah() {
		return DuniaBudget.count(JENIS);
	}

	/** True when a survival/adventure or creative player stands within {@code r} blocks of {@code at}. */
	public static boolean pemainDekat(ServerLevel level, Vec3 at, double r) {
		double r2 = r * r;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.position().distanceToSqr(at) <= r2) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 7 || PER_KOTA.isEmpty()) {
			return;
		}
		for (List<Hidup> list : PER_KOTA.values()) {
			list.removeIf(h -> {
				Entity e = h.entity();
				if (e.isRemoved()) {
					return true;
				}
				if (e.level() instanceof ServerLevel level && !pemainDekat(level, e.position(), JARAK_LEPAS)) {
					e.discard();
					return true;
				}
				return false;
			});
		}
		PER_KOTA.values().removeIf(List::isEmpty);
	}

	private TemaMekanisme() {
	}
}
