package dev.kultivator.client.sekte.paviliun;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the client knows of its own sect's pavilion and cemetery (from {@code PaviliunPayloads.Places}): the built
 * tombstones and their names, the altar table and its tablets, the censer and its smoke, the running rites, and the
 * lanterns found around the cemetery (a small read-only block scan every five seconds while the cemetery is within 48
 * blocks; never a block change). Times are kept in the client level's game time, re-based on each packet.
 */
final class PaviliunWorld {

	record Grave(BlockPos pos, Component name, int step, boolean enshrined) {
	}

	record Rite(int kind, BlockPos pos, long start, long ends) {
	}

	static final List<Grave> GRAVES = new ArrayList<>();
	static final List<Component> TABLETS = new ArrayList<>();
	static final List<Rite> RITES = new ArrayList<>();
	static final List<BlockPos> LANTERNS = new ArrayList<>();
	static @Nullable BlockPos table;
	static @Nullable BlockPos kneel;
	static boolean tableIsAltar;
	static int tabletCount;
	static @Nullable BlockPos smoke;
	static long smokeUntil;
	static @Nullable BlockPos centre;
	private static long lastScan;

	static boolean any() {
		return !GRAVES.isEmpty() || table != null || smoke != null || !RITES.isEmpty() || centre != null;
	}

	static void set(CompoundTag tag) {
		clear();
		if (tag.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		long offset = level == null ? 0L : level.getGameTime() - tag.getLongOr("now", 0L);
		for (Tag t : tag.getListOrEmpty("graves")) {
			if (t instanceof CompoundTag e) {
				BlockPos pos = pos(e, "pos");
				if (pos != null) {
					GRAVES.add(new Grave(pos, e.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?")), e.getIntOr("step", 0),
							e.getBooleanOr("enshrined", false)));
				}
			}
		}
		table = pos(tag, "table");
		kneel = pos(tag, "kneel");
		tableIsAltar = tag.getBooleanOr("table_altar", false);
		for (Tag t : tag.getListOrEmpty("tablets")) {
			if (t instanceof CompoundTag n) {
				TABLETS.add(n.read("name", ComponentSerialization.CODEC).orElse(Component.literal("?")));
			}
		}
		tabletCount = tag.getIntOr("tablet_count", TABLETS.size());
		smoke = pos(tag, "smoke");
		smokeUntil = tag.getLongOr("smoke_until", 0L) + offset;
		centre = pos(tag, "centre");
		for (Tag t : tag.getListOrEmpty("rites")) {
			if (t instanceof CompoundTag r) {
				BlockPos pos = pos(r, "pos");
				long start = r.getLongOr("start", -1L);
				if (pos != null) {
					RITES.add(new Rite(r.getIntOr("kind", 0), pos, start < 0L ? -1L : start + offset, r.getLongOr("ends", 0L) + offset));
				}
			}
		}
		lastScan = 0L;
	}

	static void clear() {
		GRAVES.clear();
		TABLETS.clear();
		RITES.clear();
		LANTERNS.clear();
		table = null;
		kneel = null;
		smoke = null;
		centre = null;
		tabletCount = 0;
		smokeUntil = 0L;
	}

	private static @Nullable BlockPos pos(CompoundTag tag, String key) {
		int[] p = tag.getIntArray(key).orElse(null);
		return p == null || p.length != 3 ? null : new BlockPos(p[0], p[1], p[2]);
	}

	/** Every client tick: drops finished rites and smoke, rescans the lanterns every 5 s near the cemetery. */
	static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			return;
		}
		long now = level.getGameTime();
		RITES.removeIf(r -> r.ends() > 0L && now > r.ends() + 40L);
		if (smoke != null && now > smokeUntil) {
			smoke = null;
		}
		BlockPos c = centre;
		if (c == null || now - lastScan < 100L) {
			return;
		}
		lastScan = now;
		LANTERNS.clear();
		if (client.player.blockPosition().distSqr(c) > 48 * 48) {
			return;
		}
		int r = 11;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r && LANTERNS.size() < 12; dx++) {
			for (int dz = -r; dz <= r && LANTERNS.size() < 12; dz++) {
				for (int dy = -2; dy <= 4; dy++) {
					m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (!level.isLoaded(m)) {
						continue;
					}
					BlockState state = level.getBlockState(m);
					if (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
						LANTERNS.add(m.immutable());
					}
				}
			}
		}
	}

	/** The running Qingming whose kneeling rows reach {@code (x, z)}, or null. */
	static @Nullable Rite qingmingAt(double x, double z) {
		for (Rite r : RITES) {
			if (r.kind() == 1 && r.start() >= 0L) {
				double dx = x - (r.pos().getX() + 0.5D);
				double dz = z - (r.pos().getZ() + 0.5D);
				if (dx * dx + dz * dz <= 20.0D * 20.0D) {
					return r;
				}
			}
		}
		return null;
	}

	/**
	 * 0..1 how deep a kneeler at {@code (x, z)} is in a Qingming kowtow at client game time {@code now} (three kowtows at
	 * 10, 25 and 40 s, each 3 s: down, touch, up), 0 outside them.
	 */
	static float kowtow(double x, double z, float now) {
		Rite r = qingmingAt(x, z);
		if (r == null) {
			return 0.0F;
		}
		float t = now - r.start();
		for (int bow : new int[]{200, 500, 800}) {
			float k = (t - bow) / 60.0F;
			if (k >= 0.0F && k <= 1.0F) {
				// down quickly, hold, rise slowly
				return k < 0.3F ? k / 0.3F : k < 0.6F ? 1.0F : 1.0F - (k - 0.6F) / 0.4F;
			}
		}
		return 0.0F;
	}

	private PaviliunWorld() {
	}
}
