package dev.kultivator.dunia.reruntuhan.penjara;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.AnchorHandler;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaAnchors;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SealService;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.reruntuhan.Reruntuhan;
import dev.kultivator.dunia.reruntuhan.Runs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Penjara Iblis Kuno (idea 62, DESIGN 34.5.6): the seals of every ancient demon prison, saved in the overworld
 * ({@code kultivator:penjara_segel}), and the {@link SealService} of contract C34.R1. Integrity 0-100 per prison (seeded
 * 60-90), falling {@code sealDecayPerDay} per in-game day, {@code eclipseDecayPerHour} per in-game hour while a Gerhana
 * Darah reddens the moon ({@code DuniaEvents.SKY_EVENT}) and 5 per pulled talisman; raised by Perkuat Segel. At 0 (or
 * with all five talismans pulled) the seal breaks (once per {@code rebreakDays}) and the demon king rises when someone
 * stands in the hall. Every change is told through {@code DuniaEvents.SEAL_CHANGED}.
 * <p>
 * The prison's id is the site id of its structure ({@code anchor.siteOf("penjara/penjara")}): every anchor of one prison
 * shares it.
 */
public final class Prisons extends SavedData {

	public static final String MODULE = "penjara";
	public static final String SITE_KIND = "penjara/penjara";
	public static final int TICK_EVERY = 200;
	public static final double PULL_COST = 5.0D;
	public static final double REINFORCE = 15.0D;

	/** One prison's seal. */
	public static final class Seal {
		public final long id;
		String dim = "";
		BlockPos shrine = BlockPos.ZERO;
		double integrity;
		int pulled;
		boolean broken;
		boolean pendingRise;
		long lastBreak = Long.MIN_VALUE / 2;
		long calmUntil;
		int king;
		float kingHealth = 1.0F;
		long lastDecay;
		final Map<UUID, Long> reinforced = new HashMap<>();

		Seal(long id) {
			this.id = id;
		}

		public int integrity() {
			return (int) Math.round(Math.max(0.0D, Math.min(100.0D, integrity)));
		}

		public boolean broken() {
			return broken;
		}

		public int pulled() {
			return pulled;
		}

		public int king() {
			return king;
		}

		public boolean calm(long now) {
			return now < calmUntil;
		}

		public boolean pendingRise() {
			return pendingRise;
		}

		public float kingHealth() {
			return kingHealth;
		}

		public BlockPos shrine() {
			return shrine;
		}

		public String dim() {
			return dim;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("id", id);
			tag.putString("dim", dim);
			tag.putLong("shrine", shrine.asLong());
			tag.putDouble("integrity", integrity);
			tag.putInt("pulled", pulled);
			tag.putBoolean("broken", broken);
			tag.putBoolean("pending", pendingRise);
			tag.putLong("last_break", lastBreak);
			tag.putLong("calm_until", calmUntil);
			tag.putInt("king", king);
			tag.putFloat("king_health", kingHealth);
			tag.putLong("last_decay", lastDecay);
			ListTag list = new ListTag();
			for (Map.Entry<UUID, Long> entry : reinforced.entrySet()) {
				CompoundTag e = new CompoundTag();
				e.putString("p", entry.getKey().toString());
				e.putLong("d", entry.getValue());
				list.add(e);
			}
			tag.put("reinforced", list);
			return tag;
		}

		static Seal load(CompoundTag tag) {
			Seal seal = new Seal(tag.getLongOr("id", 0L));
			seal.dim = tag.getStringOr("dim", "");
			seal.shrine = BlockPos.of(tag.getLongOr("shrine", 0L));
			seal.integrity = tag.getDoubleOr("integrity", 75.0D);
			seal.pulled = tag.getIntOr("pulled", 0);
			seal.broken = tag.getBooleanOr("broken", false);
			seal.pendingRise = tag.getBooleanOr("pending", false);
			seal.lastBreak = tag.getLongOr("last_break", Long.MIN_VALUE / 2);
			seal.calmUntil = tag.getLongOr("calm_until", 0L);
			seal.king = tag.getIntOr("king", 0);
			seal.kingHealth = tag.getFloatOr("king_health", 1.0F);
			seal.lastDecay = tag.getLongOr("last_decay", 0L);
			for (Tag t : tag.getListOrEmpty("reinforced")) {
				if (t instanceof CompoundTag e) {
					try {
						seal.reinforced.put(UUID.fromString(e.getStringOr("p", "")), e.getLongOr("d", 0L));
					} catch (IllegalArgumentException ignored) {
						// a broken entry is dropped
					}
				}
			}
			return seal;
		}
	}

	public static final Codec<Prisons> CODEC = CompoundTag.CODEC.xmap(Prisons::fromTag, Prisons::toTag);
	public static final SavedDataType<Prisons> TYPE = new SavedDataType<>(Kultivator.id("penjara_segel"), Prisons::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<Long, Seal> seals = new LinkedHashMap<>();
	private static final Set<ResourceKey<Level>> ECLIPSE = new HashSet<>();
	private static @Nullable MinecraftServer server;

	public Prisons() {
	}

	private static Prisons fromTag(CompoundTag tag) {
		Prisons data = new Prisons();
		for (Tag t : tag.getListOrEmpty("seals")) {
			if (t instanceof CompoundTag e) {
				Seal seal = Seal.load(e);
				if (seal.id != 0L) {
					data.seals.put(seal.id, seal);
				}
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Seal seal : seals.values()) {
			list.add(seal.save());
		}
		tag.put("seals", list);
		return tag;
	}

	public static Prisons get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ================================================================== init and the service

	/** From {@code Reruntuhan.init()}. */
	public static void init() {
		DuniaApi.setSeals(new SealService() {
			@Override
			public int integrity(long siteId) {
				Seal seal = known(siteId);
				return seal == null ? 100 : seal.integrity();
			}

			@Override
			public boolean broken(long siteId) {
				Seal seal = known(siteId);
				return seal != null && seal.broken;
			}
		});
		DuniaAnchors.register(MODULE, new AnchorHandler() {
			@Override
			public void loaded(ServerLevel level, DuniaAnchorEntity anchor) {
				if (Reruntuhan.config().prisons && ("kuil".equals(anchor.what()) || "segel".equals(anchor.what()))) {
					seal(level, anchor);
				}
			}

			@Override
			public void nearby(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
				if (!Reruntuhan.config().prisons) {
					return;
				}
				switch (anchor.what()) {
					case "kuil" -> Runs.touch(anchor, level, a -> new ShrineRun(a, level));
					case "segel" -> Runs.touch(anchor, level, a -> new CoreRun(a, level));
					default -> {
					}
				}
			}

			@Override
			public void unloaded(ServerLevel level, DuniaAnchorEntity anchor) {
				Runs.drop(anchor);
			}

			@Override
			public int radius() {
				return 64;
			}
		});
		DuniaEvents.SKY_EVENT.register((level, kind, started) -> {
			if (kind == SkyKind.BLOOD_ECLIPSE) {
				if (started) {
					ECLIPSE.add(level.dimension());
				} else {
					ECLIPSE.remove(level.dimension());
				}
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
			server = null;
			ECLIPSE.clear();
		});
	}

	private static @Nullable Seal known(long siteId) {
		MinecraftServer s = server;
		return s == null ? null : get(s).seals.get(siteId);
	}

	/** {@code uji penjara gerhana}: marks (or clears) a Gerhana Darah over {@code level} for the prisons only. */
	public static void testEclipse(net.minecraft.world.level.Level level, boolean on) {
		if (on) {
			ECLIPSE.add(level.dimension());
		} else {
			ECLIPSE.remove(level.dimension());
		}
	}

	/** True while a Gerhana Darah reddens the moon over {@code level} (the sealed demons grow restless). */
	public static boolean eclipse(@Nullable net.minecraft.world.level.Level level) {
		return level != null && ECLIPSE.contains(level.dimension());
	}

	/** The prison id of an anchor of a prison. */
	public static long idOf(DuniaAnchorEntity anchor) {
		return anchor.siteOf(SITE_KIND);
	}

	/** The seal of the prison {@code anchor} belongs to, made (seeded) on the first visit. */
	public static Seal seal(ServerLevel level, DuniaAnchorEntity anchor) {
		Prisons data = get(level.getServer());
		long id = idOf(anchor);
		Seal seal = data.seals.get(id);
		if (seal == null) {
			seal = new Seal(id);
			long h = id ^ (id >>> 29);
			seal.dim = level.dimension().identifier().toString();
			seal.shrine = anchor.blockPosition();
			seal.integrity = 60 + Math.floorMod(h, 31L);
			seal.king = (int) Math.floorMod(h >>> 7, 3L);
			seal.lastDecay = level.getGameTime();
			data.seals.put(id, seal);
			data.setDirty();
		}
		if ("kuil".equals(anchor.what()) && !seal.shrine.equals(anchor.blockPosition())) {
			seal.shrine = anchor.blockPosition();
			data.setDirty();
		}
		return seal;
	}

	public static Collection<Seal> all(MinecraftServer server) {
		return get(server).seals.values();
	}

	// ================================================================== the clock

	/** Every server tick (decay every {@link #TICK_EVERY} ticks). */
	public static void tick(MinecraftServer server) {
		if (!Reruntuhan.config().prisons || server.getTickCount() % TICK_EVERY != 0) {
			return;
		}
		Prisons data = get(server);
		if (data.seals.isEmpty()) {
			return;
		}
		for (Seal seal : data.seals.values()) {
			ServerLevel level = levelOf(server, seal);
			long now = level == null ? server.overworld().getGameTime() : level.getGameTime();
			long elapsed = Math.max(0L, now - seal.lastDecay);
			seal.lastDecay = now;
			if (seal.broken || seal.calm(now) || elapsed <= 0L || level == null) {
				continue;
			}
			double loss = Reruntuhan.config().sealDecayPerDay * elapsed / 24000.0D;
			if (ECLIPSE.contains(level.dimension())) {
				loss += Reruntuhan.config().eclipseDecayPerHour * elapsed / 1000.0D;
			}
			if (loss > 0.0D) {
				change(level, seal, seal.integrity - loss);
			}
		}
		data.setDirty();
	}

	static @Nullable ServerLevel levelOf(MinecraftServer server, Seal seal) {
		Identifier id = Identifier.tryParse(seal.dim);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	/** Sets the integrity, fires SEAL_CHANGED on a whole-point change and breaks the seal at 0. */
	static void change(ServerLevel level, Seal seal, double integrity) {
		int before = seal.integrity();
		seal.integrity = Math.max(0.0D, Math.min(100.0D, integrity));
		get(level.getServer()).setDirty();
		if (seal.integrity() != before) {
			DuniaEvents.fireSealChanged(level, seal.id, seal.integrity(), seal.broken);
		}
		if (seal.integrity <= 0.0D && !seal.broken) {
			breakSeal(level, seal, false);
		}
	}

	/** The seal breaks (at most once per {@code rebreakDays} unless {@code force}); the king rises at the next visit of the hall. */
	public static boolean breakSeal(ServerLevel level, Seal seal, boolean force) {
		long now = level.getGameTime();
		if (seal.broken) {
			return false;
		}
		if (!force && (seal.calm(now) || now - seal.lastBreak < Reruntuhan.config().rebreakDays * 24000L)) {
			seal.integrity = Math.max(seal.integrity, 1.0D);
			return false;
		}
		seal.broken = true;
		seal.pendingRise = true;
		seal.kingHealth = 1.0F;
		seal.lastBreak = now;
		seal.integrity = 0.0D;
		get(level.getServer()).setDirty();
		DuniaEvents.fireSealChanged(level, seal.id, 0, true);
		for (CoreRun run : Runs.all(CoreRun.class)) {
			if (run.prisonId() == seal.id) {
				run.onBreak();
			}
		}
		return true;
	}

	/** The king was slain: the prison is calm for {@code rebreakDays} days, the seal is mended to 60. */
	static void slain(ServerLevel level, Seal seal) {
		long now = level.getGameTime();
		seal.broken = false;
		seal.pendingRise = false;
		seal.pulled = 0;
		seal.kingHealth = 1.0F;
		seal.calmUntil = now + Reruntuhan.config().rebreakDays * 24000L;
		seal.integrity = 60.0D;
		seal.lastDecay = now;
		get(level.getServer()).setDirty();
		DuniaEvents.fireSealChanged(level, seal.id, seal.integrity(), false);
	}

	/** The five pillars were lit again: sealed whole. */
	static void resealed(ServerLevel level, Seal seal) {
		long now = level.getGameTime();
		seal.broken = false;
		seal.pendingRise = false;
		seal.pulled = 0;
		seal.kingHealth = 1.0F;
		seal.integrity = 100.0D;
		seal.lastDecay = now;
		get(level.getServer()).setDirty();
		DuniaEvents.fireSealChanged(level, seal.id, 100, false);
	}

	// ================================================================== the talismans and the reinforcing

	/** A talisman pull was confirmed on the warning screen ({@code n} 0-4); the shrine run checks distance and state. */
	public static void confirmPull(ServerPlayer player, int n) {
		for (ShrineRun run : Runs.all(ShrineRun.class)) {
			if (run.level == player.level() && run.centre().distanceToSqr(player.position()) < 24.0D * 24.0D) {
				run.pull(player, n);
				return;
			}
		}
	}

	/** True when {@code player} reinforced this seal today. */
	static boolean reinforcedToday(Seal seal, ServerPlayer player, long day) {
		Long last = seal.reinforced.get(player.getUUID());
		return last != null && last == day;
	}

	static void markReinforced(ServerLevel level, Seal seal, ServerPlayer player, long day) {
		seal.reinforced.put(player.getUUID(), day);
		if (seal.reinforced.size() > 64) {
			seal.reinforced.keySet().removeIf(id -> seal.reinforced.get(id) < day - 7);
		}
		get(level.getServer()).setDirty();
	}

	/** A lethal blow of a demon: the player is thrown out of the arena to the shaft's landing. */
	public static void throwOut(ServerPlayer player) {
		for (CoreRun run : Runs.all(CoreRun.class)) {
			if (run.level == player.level() && run.centre().distanceToSqr(player.position()) < 40.0D * 40.0D) {
				run.expel(player);
				return;
			}
		}
	}

	/** The nearest live seal run for tests. */
	public static @Nullable CoreRun nearestCore(ServerPlayer player, double range) {
		CoreRun best = null;
		double bestSq = range * range;
		for (CoreRun run : Runs.all(CoreRun.class)) {
			if (run.level != player.level()) {
				continue;
			}
			double d = run.centre().distanceToSqr(player.position());
			if (d < bestSq) {
				bestSq = d;
				best = run;
			}
		}
		return best;
	}

	public static @Nullable ShrineRun nearestShrine(ServerPlayer player, double range) {
		ShrineRun best = null;
		double bestSq = range * range;
		for (ShrineRun run : Runs.all(ShrineRun.class)) {
			if (run.level != player.level()) {
				continue;
			}
			double d = run.centre().distanceToSqr(player.position());
			if (d < bestSq) {
				bestSq = d;
				best = run;
			}
		}
		return best;
	}

	/** The seal of a live prison near the player (shrine or hall) for tests; null when none is near. */
	public static @Nullable Seal nearestSeal(ServerPlayer player) {
		CoreRun core = nearestCore(player, 96.0D);
		if (core != null) {
			return core.seal();
		}
		ShrineRun shrine = nearestShrine(player, 96.0D);
		return shrine == null ? null : shrine.seal();
	}

	/** {@code uji penjara hapus}: forgets the seal (it is seeded again on the next visit). */
	public static void forget(ServerLevel level, Seal seal) {
		get(level.getServer()).seals.remove(seal.id);
		get(level.getServer()).setDirty();
	}

	/** Sets the integrity directly ({@code uji penjara segel <n>}). */
	public static void testSet(ServerLevel level, Seal seal, int integrity) {
		if (integrity > 0 && seal.broken) {
			seal.broken = false;
			seal.pendingRise = false;
		}
		seal.calmUntil = 0L;
		seal.lastBreak = Long.MIN_VALUE / 2;
		change(level, seal, integrity);
	}

	/** Marks the prison's next king ({@code uji penjara lepas <raja>}) and breaks the seal at once. */
	public static void testRelease(ServerLevel level, Seal seal, int king) {
		seal.king = Math.max(0, Math.min(2, king));
		seal.calmUntil = 0L;
		seal.broken = false;
		breakSeal(level, seal, true);
	}
}
