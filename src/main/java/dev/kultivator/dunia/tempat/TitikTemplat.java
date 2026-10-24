package dev.kultivator.dunia.tempat;

import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Rotation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the life points a generator wrote with {@code Template.titik} (tools/nbt.py) into the extra root key
 * {@code kultivator_titik} of a structure NBT (DESIGN 38.B.2.5). Vanilla {@code StructureTemplate.load} reads only
 * size, palette(s), blocks and entities (26.3), so the key costs nothing at generation; the server reads it here from
 * the data pack resource {@code <ns>:structure/<path>.nbt} (gzip, {@code NbtIo.readCompressed}), caches it per
 * template id (cleared on data pack reload) and turns template-local points into world points for a placed piece
 * ({@link #dunia}). Points also exist on old towns' templates (38.C.7), so towns born before their NPCs learn to sit
 * still get beds and chairs.
 */
public final class TitikTemplat {

	/** NBT root key written by tools/nbt.py {@code Template.titik}. */
	public static final String KUNCI = "kultivator_titik";
	/** NBT root key written by tools/nbt.py {@code Template.sisi} (themed lots, 38.T.2.4). */
	public static final String KUNCI_SISI = "kultivator_sisi";
	/** Bytes read per template at most (a lot is ~200 KB uncompressed). */
	private static final long QUOTA = 64L * 1024L * 1024L;

	/** A template-local point (before the piece transform). */
	public record Lokal(String jenis, int x, int y, int z, Direction hadap, Map<String, String> data) {
	}

	private static final Map<Identifier, List<Lokal>> CACHE = new ConcurrentHashMap<>();
	private static boolean hooked;

	/** From {@code TempatSpine.init()}: clear the cache when data packs reload. */
	public static void init() {
		if (hooked) {
			return;
		}
		hooked = true;
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> CACHE.clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CACHE.clear());
	}

	/** The local points of template {@code template} ({@code kultivator:kota_besar/pasar/kios_a}); empty when none. */
	public static List<Lokal> of(MinecraftServer server, Identifier template) {
		List<Lokal> cached = CACHE.get(template);
		if (cached != null) {
			return cached;
		}
		List<Lokal> read = read(server, template);
		CACHE.put(template, read);
		return read;
	}

	/** The world points of template {@code template} placed at {@code origin} with {@code rotation} (no mirror). */
	public static List<Titik> dunia(MinecraftServer server, Identifier template, BlockPos origin, Rotation rotation) {
		List<Lokal> local = of(server, template);
		List<Titik> out = new ArrayList<>(local.size());
		for (Lokal p : local) {
			out.add(transform(p, origin, rotation));
		}
		return out;
	}

	/** One local point placed with its piece: position and facing rotated about the piece origin, box sizes swapped. */
	public static Titik transform(Lokal p, BlockPos origin, Rotation rotation) {
		Map<String, String> data = p.data();
		int x = p.x();
		int z = p.z();
		if (data.containsKey("dx") || data.containsKey("dz")) {
			int dx = parse(data.get("dx"));
			int dz = parse(data.get("dz"));
			int[] a = rotate(rotation, x, z);
			int[] b = rotate(rotation, x + dx, z + dz);
			x = Math.min(a[0], b[0]);
			z = Math.min(a[1], b[1]);
			Map<String, String> copy = new HashMap<>(data);
			copy.put("dx", Integer.toString(Math.abs(b[0] - a[0])));
			copy.put("dz", Integer.toString(Math.abs(b[1] - a[1])));
			data = copy;
			return new Titik(p.jenis(), origin.offset(x, p.y(), z), rotation.rotate(p.hadap()), data);
		}
		int[] r = rotate(rotation, x, z);
		return new Titik(p.jenis(), origin.offset(r[0], p.y(), r[1]), rotation.rotate(p.hadap()), data);
	}

	private static int[] rotate(Rotation r, int x, int z) {
		return switch (r) {
			case CLOCKWISE_90 -> new int[]{-z, x};
			case CLOCKWISE_180 -> new int[]{-x, -z};
			case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
			default -> new int[]{x, z};
		};
	}

	private static int parse(String s) {
		if (s == null) {
			return 0;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** The raw side codes of a themed lot ({@code n/e/s/w} -> code), empty when the template has none. */
	public static Map<String, String> sisi(MinecraftServer server, Identifier template) {
		CompoundTag root = load(server, template);
		if (root == null) {
			return Map.of();
		}
		CompoundTag tag = root.getCompoundOrEmpty(KUNCI_SISI);
		Map<String, String> out = new HashMap<>();
		for (String k : tag.keySet()) {
			out.put(k, tag.getStringOr(k, ""));
		}
		return Map.copyOf(out);
	}

	private static List<Lokal> read(MinecraftServer server, Identifier template) {
		CompoundTag root = load(server, template);
		if (root == null) {
			return List.of();
		}
		ListTag list = root.getListOrEmpty(KUNCI);
		List<Lokal> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompoundOrEmpty(i);
			ListTag pos = t.getListOrEmpty("pos");
			Direction facing = Direction.byName(t.getStringOr("hadap", "south"));
			if (facing == null || facing.getAxis().isVertical()) {
				facing = Direction.SOUTH;
			}
			CompoundTag d = t.getCompoundOrEmpty("data");
			Map<String, String> data = new HashMap<>();
			for (String k : d.keySet()) {
				data.put(k, d.getStringOr(k, ""));
			}
			out.add(new Lokal(t.getStringOr("jenis", ""), pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0), facing, Map.copyOf(data)));
		}
		return List.copyOf(out);
	}

	private static CompoundTag load(MinecraftServer server, Identifier template) {
		Identifier file = template.withPath(p -> "structure/" + p + ".nbt");
		Optional<Resource> resource = server.getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream in = resource.get().open()) {
			return NbtIo.readCompressed(in, NbtAccounter.create(QUOTA));
		} catch (IOException | RuntimeException e) {
			Kultivator.LOGGER.warn("Could not read life points of {}", template, e);
			return null;
		}
	}

	private TitikTemplat() {
	}
}
