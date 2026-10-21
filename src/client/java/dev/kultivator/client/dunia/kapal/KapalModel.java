package dev.kultivator.client.dunia.kapal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.kapal.KapalClass;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The hull display lists of the three ship classes (DESIGN 34.5.15), read once per resource reload from
 * {@code assets/kultivator/dunia/kapal/<class>.json} (written by {@code tools/gen_dunia_kapal.py}): a block-state palette,
 * runs of one state along x or z (at most 4 long; hidden blocks already dropped), sails, light points, the stern engine,
 * the banner and the wake size. Runs come sorted with the hull first ({@link Hull#core} of them) and the decoration after,
 * which is the renderer's LOD order. A missing or broken file gives a plain plank box, never a crash.
 */
public final class KapalModel {

	/** One run: a state from {@code x, y, z} (local, the run's first block's min corner) {@code n} blocks long. */
	public record Run(BlockState state, float x, float y, float z, int n, boolean alongZ, boolean detail) {
	}

	/** A sail quad across the ship at local {@code z}, from {@code y0} to {@code y1} and {@code x0} to {@code x1}. */
	public record Sail(float z, float y0, float y1, float x0, float x1) {
	}

	/** A lantern or ornament glow. */
	public record Light(float x, float y, float z, int rgb, float size) {
	}

	/** A parsed hull. */
	public static final class Hull {
		public final List<Run> runs = new ArrayList<>();
		public final List<Sail> sails = new ArrayList<>();
		public final List<Light> lights = new ArrayList<>();
		public final List<float[]> engines = new ArrayList<>();
		public float[] banner = {0.0F, 4.0F, -4.0F, 1.0F, 2.0F};
		public int core;
		public int blocks;
		public float width;
		public float height;
		public float length;
		public float deck;
	}

	private static final Map<KapalClass, Hull> CACHE = new EnumMap<>(KapalClass.class);

	/** The hull of {@code kind} (loaded on first use after a reload). */
	public static Hull of(KapalClass kind) {
		Hull hull = CACHE.get(kind);
		if (hull == null) {
			hull = load(kind);
			CACHE.put(kind, hull);
		}
		return hull;
	}

	/** Forgets every hull (resource reload, disconnect). */
	public static void clear() {
		CACHE.clear();
	}

	private static Hull load(KapalClass kind) {
		Identifier id = Kultivator.id("dunia/kapal/" + kind.model() + ".json");
		try {
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
			if (resource.isPresent()) {
				try (BufferedReader reader = resource.get().openAsReader()) {
					JsonElement root = JsonParser.parseReader(reader);
					if (root != null && root.isJsonObject()) {
						Hull hull = parse(root.getAsJsonObject(), kind);
						if (!hull.runs.isEmpty()) {
							return hull;
						}
					}
				}
			}
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: ship model {} could not be read, a plain hull is drawn", id, exception);
		}
		return fallback(kind);
	}

	private static Hull parse(JsonObject root, KapalClass kind) {
		Hull hull = new Hull();
		JsonArray size = root.getAsJsonArray("size");
		hull.width = size != null && size.size() == 3 ? size.get(0).getAsFloat() : kind.width();
		hull.height = size != null && size.size() == 3 ? size.get(1).getAsFloat() : kind.height();
		hull.length = size != null && size.size() == 3 ? size.get(2).getAsFloat() : kind.length();
		hull.deck = root.has("deck") ? root.get("deck").getAsFloat() : kind.deck();
		hull.blocks = root.has("blocks") ? root.get("blocks").getAsInt() : 0;
		List<BlockState> palette = new ArrayList<>();
		for (JsonElement element : root.getAsJsonArray("palette")) {
			palette.add(state(element.getAsJsonObject()));
		}
		for (JsonElement element : root.getAsJsonArray("runs")) {
			JsonObject run = element.getAsJsonObject();
			int b = run.get("b").getAsInt();
			if (b < 0 || b >= palette.size()) {
				continue;
			}
			BlockState state = palette.get(b);
			if (state == null || state.isAir()) {
				continue;
			}
			boolean detail = run.has("d") && run.get("d").getAsInt() != 0;
			hull.runs.add(new Run(state, run.get("x").getAsFloat(), run.get("y").getAsFloat(), run.get("z").getAsFloat(),
					Math.max(1, Math.min(8, run.get("n").getAsInt())), "z".equals(run.get("a").getAsString()), detail));
			if (!detail) {
				hull.core++;
			}
		}
		if (root.has("sails")) {
			for (JsonElement element : root.getAsJsonArray("sails")) {
				JsonObject s = element.getAsJsonObject();
				hull.sails.add(new Sail(s.get("z").getAsFloat(), s.get("y0").getAsFloat(), s.get("y1").getAsFloat(), s.get("x0").getAsFloat(),
						s.get("x1").getAsFloat()));
			}
		}
		if (root.has("lights")) {
			for (JsonElement element : root.getAsJsonArray("lights")) {
				JsonArray l = element.getAsJsonArray();
				int rgb = 0xFFC66A;
				try {
					rgb = Integer.parseInt(l.get(3).getAsString().replace("#", ""), 16);
				} catch (RuntimeException ignored) {
					// keep the lantern gold
				}
				float sizeL = l.size() > 4 ? l.get(4).getAsFloat() : 1.0F;
				hull.lights.add(new Light(l.get(0).getAsFloat(), l.get(1).getAsFloat(), l.get(2).getAsFloat(), rgb, sizeL));
			}
		}
		if (root.has("engine")) {
			for (JsonElement element : root.getAsJsonArray("engine")) {
				JsonArray e = element.getAsJsonArray();
				hull.engines.add(new float[]{e.get(0).getAsFloat(), e.get(1).getAsFloat(), e.get(2).getAsFloat()});
			}
		}
		if (root.has("banner")) {
			JsonObject b = root.getAsJsonObject("banner");
			hull.banner = new float[]{b.get("x").getAsFloat(), b.get("y").getAsFloat(), b.get("z").getAsFloat(), b.get("w").getAsFloat(),
					b.get("h").getAsFloat()};
		}
		return hull;
	}

	/** A palette entry ({@code id}, optional {@code p} properties) as a block state; unknown values keep the default. */
	private static @Nullable BlockState state(JsonObject entry) {
		Identifier id = Identifier.tryParse(entry.get("id").getAsString());
		if (id == null) {
			return null;
		}
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
		if (block.isEmpty()) {
			return null;
		}
		BlockState state = block.get().defaultBlockState();
		if (entry.has("p")) {
			JsonObject props = entry.getAsJsonObject("p");
			for (Map.Entry<String, JsonElement> prop : props.entrySet()) {
				Property<?> property = block.get().getStateDefinition().getProperty(prop.getKey());
				if (property != null) {
					state = with(state, property, prop.getValue().getAsString());
				}
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
		Optional<T> parsed = property.getValue(value);
		return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
	}

	/** A plain plank hull when the model is missing. */
	private static Hull fallback(KapalClass kind) {
		Hull hull = new Hull();
		hull.width = kind.width();
		hull.height = kind.height();
		hull.length = kind.length();
		hull.deck = kind.deck();
		float hw = kind.width() * 0.5F;
		float hl = kind.length() * 0.5F;
		BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
		for (float z = -hl + 1.0F; z < hl - 1.0F; z += 4.0F) {
			int n = (int) Math.min(4.0F, hl - 1.0F - z);
			for (float x = -hw + 1.0F; x < hw - 1.0F; x += 1.0F) {
				hull.runs.add(new Run(deck, x, kind.deck() - 1.0F, z, n, true, false));
				hull.runs.add(new Run(planks, x, 1.0F, z, n, true, false));
			}
		}
		hull.core = hull.runs.size();
		return hull;
	}

	private KapalModel() {
	}
}
