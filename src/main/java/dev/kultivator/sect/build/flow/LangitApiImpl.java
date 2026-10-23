package dev.kultivator.sect.build.flow;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.engine.BuildEngine;
import dev.kultivator.sect.build.engine.SkySiteInfo;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitEvents;
import dev.kultivator.sekte.langit.api.SkyHallView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sky API (DESIGN 37.9.1) over the construction engine: a picture of every sky site rebuilt once a second (and at once
 * after a founding, a tier, the Ascension or a removal), so the spawn check, the fall net and the warded air read a plain
 * list. Volumes follow DESIGN 37.4.10: the island column + 8 horizontally from 8 under its lowest block to 8 over its
 * envelope, plus the stair foot. Anchors keep their template-local ids except the direction suffix of
 * {@code dermaga_*} and {@code airterjun_*}, turned to the world compass here.
 */
public final class LangitApiImpl implements LangitApi {

	/** Horizontal and vertical margin of the warded volume. */
	public static final int VOLUME_MARGIN = 8;

	private record Entry(SkyHallView view, Map<String, BlockPos> anchors, boolean test, boolean showcase) {
	}

	private volatile List<Entry> entries = List.of();
	private volatile boolean dirty = true;
	private @Nullable MinecraftServer server;

	public LangitApiImpl() {
		LangitEvents.add(new LangitEvents.Listener() {
			@Override
			public void founded(UUID owner, ServerLevel level) {
				dirty = true;
			}

			@Override
			public void tier(UUID owner, ServerLevel level, int tier) {
				dirty = true;
			}

			@Override
			public void ascended(UUID owner, ServerLevel level) {
				dirty = true;
			}

			@Override
			public void removed(UUID owner) {
				dirty = true;
			}

			@Override
			public void changed(UUID owner) {
				dirty = true;
			}
		});
	}

	/** Server tick (from {@code LangitBangun}): refresh once a second or when told. */
	void tick(MinecraftServer current, long ticks) {
		this.server = current;
		if (dirty || ticks % 20L == 0L) {
			refresh();
		}
	}

	void stop() {
		entries = List.of();
		server = null;
		dirty = true;
	}

	/** Rebuilds the picture now (server thread). */
	void refresh() {
		dirty = false;
		List<Entry> list = new ArrayList<>();
		if (!dev.kultivator.sekte.langit.api.LangitConfig.on()) {
			// Switched off (K1): the sites stay as blocks, but no module sees a sky hall (no ward, no net, no events).
			entries = List.of();
			return;
		}
		for (SkySiteInfo info : BuildEngine.INSTANCE.skyInfos()) {
			BoundingBox island = info.island();
			int top = info.yP() - 1 + info.above() + VOLUME_MARGIN;
			BoundingBox volume = new BoundingBox(island.minX() - VOLUME_MARGIN, info.skyFloorY() - VOLUME_MARGIN, island.minZ() - VOLUME_MARGIN,
					island.maxX() + VOLUME_MARGIN, top, island.maxZ() + VOLUME_MARGIN);
			boolean grand = !info.showcase() && LangitBangun.grandActive(server, info.owner());
			boolean closed = !info.showcase() && LangitBangun.gateClosed(info.owner());
			SkyHallView view = new SkyHallView(info.owner(), info.dim(), info.throne(), info.yP(), info.skyFloorY(), info.template(), info.tier(),
					info.tethered(), info.ascent(), island, volume, info.kaki(), grand, closed);
			list.add(new Entry(view, worldAnchors(info), info.test(), info.showcase()));
		}
		entries = List.copyOf(list);
	}

	/** Anchors with a direction suffix turned from template-local to the world compass by the site's rotation. */
	private static Map<String, BlockPos> worldAnchors(SkySiteInfo info) {
		Map<String, BlockPos> map = new LinkedHashMap<>();
		for (Map.Entry<String, BlockPos> anchor : info.anchors().entrySet()) {
			map.put(worldId(anchor.getKey(), info.rotation()), anchor.getValue());
		}
		return map;
	}

	/** {@code dermaga_0_p_n} / {@code airterjun_3_w}: the last letter rotated; any other id unchanged. */
	public static String worldId(String id, int rotation) {
		if (!(id.startsWith("dermaga_") || id.startsWith("airterjun_")) || id.length() < 2 || id.charAt(id.length() - 2) != '_') {
			return id;
		}
		Direction local = switch (id.charAt(id.length() - 1)) {
			case 'n' -> Direction.NORTH;
			case 'e' -> Direction.EAST;
			case 'w' -> Direction.WEST;
			case 's' -> Direction.SOUTH;
			default -> null;
		};
		if (local == null) {
			return id;
		}
		Direction world = SectTemplates.rotation(rotation).rotate(local);
		char letter = switch (world) {
			case NORTH -> 'n';
			case EAST -> 'e';
			case WEST -> 'w';
			default -> 's';
		};
		return id.substring(0, id.length() - 1) + letter;
	}

	private @Nullable Entry entryOf(UUID owner) {
		Entry fallback = null;
		for (Entry entry : entries) {
			if (entry.view().owner().equals(owner)) {
				if (!entry.test() && !entry.showcase()) {
					return entry;
				}
				if (fallback == null && !entry.showcase()) {
					fallback = entry;
				}
			}
		}
		return fallback;
	}

	// ================================================================== LangitApi

	@Override
	public boolean isSkyHall(UUID owner) {
		Entry entry = entryOf(owner);
		return entry != null && !entry.view().tethered();
	}

	@Override
	public @Nullable SkyHallView skyHall(UUID owner) {
		Entry entry = entryOf(owner);
		return entry == null ? null : entry.view();
	}

	@Override
	public @Nullable SkyHallView at(Level level, BlockPos pos) {
		for (Entry entry : entries) {
			SkyHallView view = entry.view();
			if (view.dim().equals(level.dimension()) && view.inVolume(pos)) {
				return view;
			}
		}
		return null;
	}

	@Override
	public List<SkyHallView> all(ServerLevel level) {
		List<SkyHallView> list = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.view().dim().equals(level.dimension())) {
				list.add(entry.view());
			}
		}
		return list;
	}

	@Override
	public boolean underSkyIsland(Level level, BlockPos pos) {
		for (Entry entry : entries) {
			SkyHallView view = entry.view();
			BoundingBox island = view.islandBox();
			if (view.dim().equals(level.dimension()) && pos.getX() >= island.minX() && pos.getX() <= island.maxX() && pos.getZ() >= island.minZ()
					&& pos.getZ() <= island.maxZ() && pos.getY() < view.skyFloorY()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public @Nullable BlockPos anchor(UUID owner, String id) {
		Entry entry = entryOf(owner);
		return entry == null ? null : entry.anchors().get(id);
	}

	@Override
	public Map<String, BlockPos> anchors(UUID owner) {
		Entry entry = entryOf(owner);
		return entry == null ? Map.of() : entry.anchors();
	}

	@Override
	public boolean member(UUID owner, Entity entity) {
		if (entity == null) {
			return false;
		}
		if (entity instanceof ServerPlayer player) {
			return isMember(owner, player);
		}
		if (entity instanceof DiscipleEntity disciple) {
			return disciple.role() == DiscipleEntity.Role.DISCIPLE && owner.equals(disciple.ownerId());
		}
		if (entity instanceof OwnableEntity ownable) {
			EntityReference<LivingEntity> reference = ownable.getOwnerReference();
			if (reference == null) {
				return false;
			}
			UUID tamer = reference.getUUID();
			if (owner.equals(tamer)) {
				return true;
			}
			MinecraftServer current = server;
			ServerPlayer player = current == null ? null : current.getPlayerList().getPlayer(tamer);
			return player != null && isMember(owner, player);
		}
		return false;
	}

	/** The owner or a member of the owner's sect. */
	public static boolean isMember(UUID owner, ServerPlayer player) {
		if (player.getUUID().equals(owner)) {
			return true;
		}
		Sect sect = Sects.sectOf(player);
		return sect != null && sect.isMember(owner);
	}

	@Override
	public boolean invited(UUID owner, UUID player) {
		return LangitBangun.invitedUntil(owner, player) >= FlowData.day();
	}

	/** The flow's sky sub-tag of an owner (gate, invitations). */
	static CompoundTag data(UUID owner) {
		return FlowData.sub(FlowData.of(owner), "langit");
	}
}
