package dev.kultivator.client.epic.kosmik;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kultivator.Kultivator;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The meshes of the stages (DESIGN 32.1.3, contract C32.4): UV spheres and the cuboid stage models. Built once per size
 * (spheres) or per load (models) into float arrays; {@link StageFrame} only reads them and writes the reused sinks.
 */
public final class KosmikMesh {

	/** Per-vertex colour of a sphere: implemented by stage objects (no per-frame lambdas). */
	public interface Shade {
		/** The ARGB colour at unit normal ({@code nx, ny, nz}) (after the spin) and texture ({@code u, v}). */
		int argb(double nx, double ny, double nz, float u, float v);
	}

	private static final Map<Long, Sphere> SPHERES = new HashMap<>();
	private static final Map<Identifier, Model> MODELS = new HashMap<>();
	private static final Set<String> WARNED = new HashSet<>();

	// ================================================================ spheres

	/**
	 * A UV sphere of {@code rings} latitude bands × {@code segments} longitude steps, cached per size. Vertex (i, j), i in
	 * 0..rings (north pole to south pole), j in 0..segments (the seam duplicated): {@code u = j / segments},
	 * {@code v = i / rings}; longitude {@code (u − 0.5)·2π}, latitude {@code (0.5 − v)·π}; unit position = normal =
	 * {@code (cos lat · sin lon, sin lat, cos lat · cos lon)}, so u = 0.5 faces +z and the north pole is +y.
	 */
	public static Sphere sphere(int rings, int segments) {
		int r = Math.max(2, Math.min(96, rings));
		int s = Math.max(3, Math.min(192, segments));
		long key = ((long) r << 32) | s;
		Sphere sphere = SPHERES.get(key);
		if (sphere == null) {
			sphere = new Sphere(r, s);
			SPHERES.put(key, sphere);
		}
		return sphere;
	}

	/** One cached UV sphere and its per-frame scratch (one body is written at a time). */
	public static final class Sphere {
		public final int rings;
		public final int segments;
		/** Unit positions (= normals), 3 floats per vertex. */
		public final float[] pos;
		/** Equirect UVs, 2 floats per vertex. */
		public final float[] uv;
		// per-frame scratch of StageFrame.sphere / dome
		final float[] drawn;
		final float[] normal;
		final int[] color;
		final int[] colorStamp;
		final boolean[] facing;

		Sphere(int rings, int segments) {
			this.rings = rings;
			this.segments = segments;
			int n = (rings + 1) * (segments + 1);
			this.pos = new float[n * 3];
			this.uv = new float[n * 2];
			this.drawn = new float[n * 3];
			this.normal = new float[n * 3];
			this.color = new int[n];
			this.colorStamp = new int[n];
			this.facing = new boolean[n];
			for (int i = 0; i <= rings; i++) {
				float v = i / (float) rings;
				double lat = (0.5D - v) * Math.PI;
				for (int j = 0; j <= segments; j++) {
					float u = j / (float) segments;
					double lon = (u - 0.5D) * Math.PI * 2.0D;
					int k = index(i, j);
					double c = Math.cos(lat);
					pos[k * 3] = (float) (c * Math.sin(lon));
					pos[k * 3 + 1] = (float) Math.sin(lat);
					pos[k * 3 + 2] = (float) (c * Math.cos(lon));
					uv[k * 2] = u;
					uv[k * 2 + 1] = v;
				}
			}
		}

		/** The vertex index of ring {@code i}, segment {@code j}. */
		public int index(int i, int j) {
			return i * (segments + 1) + j;
		}

		/** Every quad of the sphere. */
		public int quads() {
			return rings * segments;
		}

		public int vertices() {
			return (rings + 1) * (segments + 1);
		}
	}

	// ================================================================ cuboid models

	/**
	 * Loads a stage model file ({@code assets/<ns>/<path>}), cached until {@link #clearCache}. A missing file or a parse
	 * error logs once and returns an empty model. The file holds several models: {@link Model#model(String)}.
	 */
	public static Model load(Identifier json) {
		Model cached = MODELS.get(json);
		if (cached != null) {
			return cached;
		}
		Model model;
		try {
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(json);
			if (resource.isEmpty()) {
				warn(json + " missing");
				model = Model.EMPTY;
			} else {
				try (Reader reader = resource.get().openAsReader()) {
					model = parse(JsonParser.parseReader(reader).getAsJsonObject());
				}
			}
		} catch (IOException | RuntimeException exception) {
			warn(json + ": " + exception);
			model = Model.EMPTY;
		}
		MODELS.put(json, model);
		return model;
	}

	/** Forgets the loaded models (resource reload, disconnect). */
	public static void clearCache() {
		MODELS.clear();
	}

	/**
	 * A stage model, or a model file. A file's {@link #model(String)} returns one of its models; a model's parts are drawn
	 * by {@link StageFrame#model}. Coordinates in stage metres; UVs normalised to the atlas.
	 */
	public static final class Model {
		public static final Model EMPTY = new Model(KosmikTypes.WHITE, 256.0F, Map.of(), List.of());

		public final Identifier atlas;
		public final float atlasSize;
		final Map<String, Model> models;
		final List<Part> parts;
		final Map<String, Part> byName = new HashMap<>();

		Model(Identifier atlas, float atlasSize, Map<String, Model> models, List<Part> parts) {
			this.atlas = atlas;
			this.atlasSize = atlasSize;
			this.models = models;
			this.parts = parts;
			for (Part part : parts) {
				byName.put(part.name, part);
			}
		}

		/** One model of a file (an empty model when unknown). */
		public Model model(String id) {
			Model model = models.get(id);
			return model != null ? model : EMPTY;
		}

		/** The model ids of a file, in file order. */
		public Set<String> ids() {
			return Collections.unmodifiableSet(models.keySet());
		}

		/** The part names of a model, in file order. */
		public List<String> partNames() {
			List<String> names = new ArrayList<>(parts.size());
			for (Part part : parts) {
				names.add(part.name);
			}
			return names;
		}

		public boolean hasPart(String part) {
			return byName.containsKey(part);
		}

		public boolean isEmpty() {
			return parts.isEmpty() && models.isEmpty();
		}

		/** Faces drawn at {@code quality} (every part, or one part when {@code part} is non-null). */
		public int quads(@Nullable String part, int quality) {
			int n = 0;
			for (Part p : parts) {
				if (part != null && !part.equals(p.name)) {
					continue;
				}
				for (Cuboid c : p.cuboids) {
					if (c.lod <= quality) {
						n += c.faces.length;
					}
				}
			}
			return n;
		}

		/** The pivot of a part (stage metres), or null. */
		public @Nullable float[] pivot(String part) {
			Part p = byName.get(part);
			return p == null ? null : new float[]{p.px, p.py, p.pz};
		}
	}

	/** One named part: a pivot and its cuboids. */
	static final class Part {
		final String name;
		final float px;
		final float py;
		final float pz;
		final List<Cuboid> cuboids;

		Part(String name, float px, float py, float pz, List<Cuboid> cuboids) {
			this.name = name;
			this.px = px;
			this.py = py;
			this.pz = pz;
			this.cuboids = cuboids;
		}
	}

	/** One cuboid: 8 corners (its own rotation applied) and its faces. */
	static final class Cuboid {
		/** Corner c = (x1 if bit 0) | (y1 if bit 1) | (z1 if bit 2), 3 floats each. */
		final float[] corners = new float[24];
		final Face[] faces;
		final int lod;
		final boolean glow;
		/** RGB multiplier (0xFFFFFF = none). */
		final int tint;

		Cuboid(Face[] faces, int lod, boolean glow, int tint) {
			this.faces = faces;
			this.lod = lod;
			this.glow = glow;
			this.tint = tint;
		}
	}

	/** One face: four corner indices (top-left, top-right, bottom-right, bottom-left seen from outside), UVs and shade. */
	static final class Face {
		final int c0;
		final int c1;
		final int c2;
		final int c3;
		final float u0;
		final float v0;
		final float u1;
		final float v1;
		final float shade;

		Face(int[] corners, float u0, float v0, float u1, float v1, float shade) {
			this.c0 = corners[0];
			this.c1 = corners[1];
			this.c2 = corners[2];
			this.c3 = corners[3];
			this.u0 = u0;
			this.v0 = v0;
			this.u1 = u1;
			this.v1 = v1;
			this.shade = shade;
		}
	}

	// Stage axes: x east, y up, z north (32.3). Corner order per face: TL, TR, BR, BL seen from outside, image up = +y
	// (up and down faces: image up = +z).
	private static final String[] FACE_NAMES = {"north", "south", "east", "west", "up", "down"};
	private static final int[][] FACE_CORNERS = {
			{6, 7, 5, 4}, // north (+z)
			{3, 2, 0, 1}, // south (−z)
			{7, 3, 1, 5}, // east (+x)
			{2, 6, 4, 0}, // west (−x)
			{7, 6, 2, 3}, // up (+y)
			{4, 5, 1, 0}  // down (−y)
	};
	/** Baked face shades (32.3.1): up 1.0, north/south 0.8, east/west 0.6, down 0.5. */
	private static final float[] FACE_SHADE = {0.8F, 0.8F, 0.6F, 0.6F, 1.0F, 0.5F};

	private static Model parse(JsonObject root) {
		Identifier atlas = KosmikTypes.WHITE;
		if (root.has("atlas")) {
			Identifier parsed = Identifier.tryParse(root.get("atlas").getAsString());
			if (parsed != null) {
				atlas = parsed.getPath().endsWith(".png") ? parsed : parsed.withPath(p -> p.startsWith("textures/") ? p + ".png" : "textures/" + p + ".png");
			}
		}
		float size = root.has("atlasSize") ? Math.max(1.0F, root.get("atlasSize").getAsFloat()) : 256.0F;
		Map<String, Model> models = new LinkedHashMap<>();
		if (root.has("models") && root.get("models").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("models").entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				models.put(entry.getKey(), parseModel(entry.getKey(), entry.getValue().getAsJsonObject(), atlas, size));
			}
		}
		return new Model(atlas, size, models, List.of());
	}

	private static Model parseModel(String id, JsonObject json, Identifier atlas, float size) {
		List<Part> parts = new ArrayList<>();
		if (json.has("parts") && json.get("parts").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("parts").entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject part = entry.getValue().getAsJsonObject();
				float[] pivot = vec(part.get("pivot"), 0.0F);
				List<Cuboid> cuboids = new ArrayList<>();
				if (part.has("cuboids") && part.get("cuboids").isJsonArray()) {
					for (JsonElement element : part.getAsJsonArray("cuboids")) {
						try {
							Cuboid cuboid = parseCuboid(element.getAsJsonObject(), size);
							if (cuboid != null) {
								cuboids.add(cuboid);
							}
						} catch (RuntimeException exception) {
							warn("model " + id + "/" + entry.getKey() + ": " + exception);
						}
					}
				}
				parts.add(new Part(entry.getKey(), pivot[0], pivot[1], pivot[2], List.copyOf(cuboids)));
			}
		}
		return new Model(atlas, size, Map.of(), List.copyOf(parts));
	}

	private static @Nullable Cuboid parseCuboid(JsonObject json, float size) {
		float[] from = vec(json.get("from"), 0.0F);
		float[] to = vec(json.get("to"), 0.0F);
		float x0 = Math.min(from[0], to[0]);
		float y0 = Math.min(from[1], to[1]);
		float z0 = Math.min(from[2], to[2]);
		float x1 = Math.max(from[0], to[0]);
		float y1 = Math.max(from[1], to[1]);
		float z1 = Math.max(from[2], to[2]);
		List<Face> faces = new ArrayList<>(6);
		if (json.has("faces") && json.get("faces").isJsonObject()) {
			JsonObject f = json.getAsJsonObject("faces");
			for (int i = 0; i < FACE_NAMES.length; i++) {
				JsonElement uv = f.get(FACE_NAMES[i]);
				if (uv == null || !uv.isJsonArray() || uv.getAsJsonArray().size() < 4) {
					continue;
				}
				JsonArray a = uv.getAsJsonArray();
				faces.add(new Face(FACE_CORNERS[i], a.get(0).getAsFloat() / size, a.get(1).getAsFloat() / size, a.get(2).getAsFloat() / size,
						a.get(3).getAsFloat() / size, FACE_SHADE[i]));
			}
		}
		if (faces.isEmpty()) {
			return null;
		}
		int lod = json.has("lod") ? Math.max(0, Math.min(2, json.get("lod").getAsInt())) : 0;
		boolean glow = json.has("glow") && json.get("glow").getAsBoolean();
		int tint = 0xFFFFFF;
		if (json.has("tint")) {
			try {
				tint = Integer.parseInt(json.get("tint").getAsString().replace("#", ""), 16) & 0xFFFFFF;
			} catch (NumberFormatException ignored) {
				tint = 0xFFFFFF;
			}
		}
		Cuboid cuboid = new Cuboid(faces.toArray(new Face[0]), lod, glow, tint);
		for (int c = 0; c < 8; c++) {
			cuboid.corners[c * 3] = (c & 1) != 0 ? x1 : x0;
			cuboid.corners[c * 3 + 1] = (c & 2) != 0 ? y1 : y0;
			cuboid.corners[c * 3 + 2] = (c & 4) != 0 ? z1 : z0;
		}
		if (json.has("rot") && json.get("rot").isJsonObject()) {
			JsonObject rot = json.getAsJsonObject("rot");
			String axis = rot.has("axis") ? rot.get("axis").getAsString().toLowerCase(Locale.ROOT) : "y";
			double angle = Math.toRadians(rot.has("angle") ? rot.get("angle").getAsDouble() : 0.0D);
			float[] origin = vec(rot.get("origin"), 0.0F);
			rotate(cuboid.corners, axis, angle, origin);
		}
		return cuboid;
	}

	/** Rotates the 8 corners about {@code origin} around one axis (right-handed, radians). */
	private static void rotate(float[] corners, String axis, double angle, float[] origin) {
		if (angle == 0.0D) {
			return;
		}
		double c = Math.cos(angle);
		double s = Math.sin(angle);
		for (int i = 0; i < 8; i++) {
			double x = corners[i * 3] - origin[0];
			double y = corners[i * 3 + 1] - origin[1];
			double z = corners[i * 3 + 2] - origin[2];
			double nx = x;
			double ny = y;
			double nz = z;
			switch (axis) {
				case "x" -> {
					ny = y * c - z * s;
					nz = y * s + z * c;
				}
				case "z" -> {
					nx = x * c - y * s;
					ny = x * s + y * c;
				}
				default -> {
					nx = x * c + z * s;
					nz = -x * s + z * c;
				}
			}
			corners[i * 3] = (float) (nx + origin[0]);
			corners[i * 3 + 1] = (float) (ny + origin[1]);
			corners[i * 3 + 2] = (float) (nz + origin[2]);
		}
	}

	private static float[] vec(@Nullable JsonElement element, float fallback) {
		float[] out = {fallback, fallback, fallback};
		if (element != null && element.isJsonArray()) {
			JsonArray a = element.getAsJsonArray();
			for (int i = 0; i < 3 && i < a.size(); i++) {
				out[i] = a.get(i).getAsFloat();
			}
		}
		return out;
	}

	private static void warn(String message) {
		if (WARNED.add(message)) {
			Kultivator.LOGGER.warn("Kultivator: Film kosmik 3D model {}", message);
		}
	}

	private KosmikMesh() {
	}
}
