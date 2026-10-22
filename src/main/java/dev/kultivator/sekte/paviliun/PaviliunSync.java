package dev.kultivator.sekte.paviliun;

import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What each master's client draws of A in the world (DESIGN 36.2.5, budgets 36.3.9): the floating names over the built
 * tombstones (the client shows at most eight, within eight blocks), the spirit tablets on the altar table (at most twelve
 * names, newest first, plus the count), the censer and how long its smoke rises, the running rites (kind, centre, when the
 * kneeling began, so the client can time the kowtows) and the cemetery centre (the client looks for its lanterns there).
 * One small {@link PaviliunPayloads.Places} to the master only, while they are within the territory (+24 blocks): when
 * something changed, else every 15 s; an empty one once when they leave. Checked every 2 s, players spread over it.
 */
final class PaviliunSync {

	private static final int MAX_TABLETS = 12;
	private static final long REFRESH_TICKS = 300L;

	private static final Set<UUID> DIRTY = new HashSet<>();
	private static final Map<UUID, Long> SENT_AT = new HashMap<>();
	private static final Set<UUID> SHOWN = new HashSet<>();
	private static final Map<UUID, BlockPos> SMOKE_AT = new HashMap<>();
	private static final Map<UUID, Long> SMOKE_UNTIL = new HashMap<>();

	static void clear() {
		DIRTY.clear();
		SENT_AT.clear();
		SHOWN.clear();
		SMOKE_AT.clear();
		SMOKE_UNTIL.clear();
	}

	static void dirty(UUID owner) {
		if (owner != null) {
			DIRTY.add(owner);
		}
	}

	/** Incense smoke rises at {@code pos} until game time {@code until}. */
	static void smoke(UUID owner, @Nullable BlockPos pos, long until) {
		if (pos == null) {
			return;
		}
		Long known = SMOKE_UNTIL.get(owner);
		if (known == null || until > known || !pos.equals(SMOKE_AT.get(owner))) {
			SMOKE_AT.put(owner, pos.immutable());
			SMOKE_UNTIL.put(owner, until);
		}
		dirty(owner);
	}

	/** Where the spirit tablets stand: the altar table, else the cemetery stele's face. */
	static @Nullable BlockPos tablePos(UUID owner) {
		BlockPos table = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_TABLE);
		return table != null ? table : SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_STELE);
	}

	/** Every 40 ticks (from {@code Paviliun.serverTick}). */
	static void tick(MinecraftServer server, long ticks) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID owner = player.getUUID();
			Halls.HallView hall = Halls.hallOf(owner);
			boolean near = hall != null && player.level().dimension().equals(hall.dim())
					&& horizontal(player, hall.pos()) <= (double) (hall.radius() + 24) * (hall.radius() + 24);
			if (!near) {
				if (SHOWN.remove(owner)) {
					PaviliunPayloads.places(player, new CompoundTag());
				}
				continue;
			}
			long now = player.level().getGameTime();
			Long last = SENT_AT.get(owner);
			if (!DIRTY.contains(owner) && SHOWN.contains(owner) && last != null && now - last < REFRESH_TICKS) {
				continue;
			}
			DIRTY.remove(owner);
			SENT_AT.put(owner, now);
			SHOWN.add(owner);
			PaviliunPayloads.places(player, build(owner, now));
		}
	}

	private static double horizontal(ServerPlayer player, BlockPos pos) {
		double dx = player.getX() - (pos.getX() + 0.5D);
		double dz = player.getZ() - (pos.getZ() + 0.5D);
		return dx * dx + dz * dz;
	}

	/** The world picture of {@code owner}'s sect. */
	static CompoundTag build(UUID owner, long now) {
		CompoundTag tag = new CompoundTag();
		ListTag graves = new ListTag();
		for (PaviliunData.Grave g : PaviliunData.graves(owner)) {
			if (g.slot() < 0) {
				continue;
			}
			BlockPos stone = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_GRAVE + g.slot());
			if (stone == null) {
				continue;
			}
			CompoundTag e = new CompoundTag();
			e.put("pos", pos(stone));
			e.store("name", ComponentSerialization.CODEC, g.name());
			e.putInt("step", g.step());
			e.putLong("day", g.day());
			e.putBoolean("enshrined", g.enshrined());
			graves.add(e);
		}
		tag.put("graves", graves);
		BlockPos table = tablePos(owner);
		if (table != null) {
			tag.put("table", pos(table));
			tag.putBoolean("table_altar", SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_TABLE) != null);
			// where the tablets look: the kneeling cell of the altar, else the cemetery path before the stele
			BlockPos kneel = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_ALTAR);
			if (kneel == null) {
				kneel = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_CENTER);
			}
			if (kneel != null) {
				tag.put("kneel", pos(kneel));
			}
			ListTag names = new ListTag();
			int total = 0;
			for (List<PaviliunData.Grave> list : List.of(PaviliunData.graves(owner), PaviliunData.lineage(owner))) {
				for (PaviliunData.Grave g : list) {
					if (!g.enshrined()) {
						continue;
					}
					total++;
					if (names.size() < MAX_TABLETS) {
						CompoundTag n = new CompoundTag();
						n.store("name", ComponentSerialization.CODEC, g.name());
						names.add(n);
					}
				}
			}
			tag.put("tablets", names);
			tag.putInt("tablet_count", total);
		}
		BlockPos smoke = SMOKE_AT.get(owner);
		Long until = SMOKE_UNTIL.get(owner);
		if (smoke != null && until != null && until > now) {
			tag.put("smoke", pos(smoke));
			tag.putLong("smoke_until", until);
		}
		BlockPos centre = SectBuild.anchor(owner, PaviliunKomponen.ANCHOR_CENTER);
		if (centre != null) {
			tag.put("centre", pos(centre));
		}
		ListTag rites = new ListTag();
		for (Upacara.Rite rite : Upacara.rites(owner)) {
			CompoundTag r = new CompoundTag();
			r.putInt("kind", rite.kind);
			r.put("pos", pos(rite.centre));
			r.putLong("start", rite.riteStart);
			r.putLong("ends", rite.ends);
			rites.add(r);
		}
		tag.put("rites", rites);
		tag.putLong("now", now);
		return tag;
	}

	private static IntArrayTag pos(BlockPos p) {
		return new IntArrayTag(new int[]{p.getX(), p.getY(), p.getZ()});
	}

	private PaviliunSync() {
	}
}
