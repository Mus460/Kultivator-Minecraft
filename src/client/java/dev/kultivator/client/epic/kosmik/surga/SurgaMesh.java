package dev.kultivator.client.epic.kosmik.surga;

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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The Heaven stage's cuboid models, read from {@code surga_models.json} in the C32.4 format that {@code KosmikMesh} also
 * reads (the same face names, corner order, UVs in atlas pixels and baked face shades), but kept here so the palace can
 * drop back faces on the CPU before asking the budget: a cuboid shows at most three faces, so the stage draws about half
 * the quads {@code StageFrame.model} would (that one draws every face through the no-cull cutout type). Built once per
 * load (prepare); nothing is allocated while drawing.
 */
final class SurgaMesh {

	/** Stage axes x east, y up, z north (32.3): the face names as KosmikMesh reads them. */
	private static final String[] FACE_NAMES = {"north", "south", "east", "west", "up", "down"};
	/** Corners per face: top-left, top-right, bottom-right, bottom-left seen from outside (corner bits x1 | y1 | z1). */
	private static final int[][] FACE_CORNERS = {
			{6, 7, 5, 4},
			{3, 2, 0, 1},
			{7, 3, 1, 5},
			{2, 6, 4, 0},
			{7, 6, 2, 3},
			{4, 5, 1, 0}
	};
	/** Baked face shades (32.3.1): up 1.0, north/south 0.8, east/west 0.6, down 0.5. */
	private static final float[] FACE_SHADE = {0.8F, 0.8F, 0.6F, 0.6F, 1.0F, 0.5F};

	/** A loaded file: the atlas and the models by id. */
	static final class Library {
		final Identifier atlas;
		final Map<String, Mesh> meshes;

		Library(Identifier atlas, Map<String, Mesh> meshes) {
			this.atlas = atlas;
			this.meshes = meshes;
		}

		@Nullable Mesh mesh(String id) {
			return meshes.get(id);
		}
	}

	/** One model: its parts. */
	static final class Mesh {
		final String id;
		final Part[] parts;
		/** Cuboids over all parts (the size of a placed instance's corner scratch / 24). */
		final int cuboids;

		Mesh(String id, Part[] parts) {
			this.id = id;
			this.parts = parts;
			int n = 0;
			for (Part p : parts) {
				n += p.cuboids.length;
			}
			this.cuboids = n;
		}

		@Nullable Part part(String name) {
			for (Part p : parts) {
				if (p.name.equals(name)) {
					return p;
				}
			}
			return null;
		}
	}

	/** One named part: a pivot (stage metres) and its cuboids. */
	static final class Part {
		final String name;
		final float px;
		final float py;
		final float pz;
		final Cuboid[] cuboids;

		Part(String name, float px, float py, float pz, Cuboid[] cuboids) {
			this.name = name;
			this.px = px;
			this.py = py;
			this.pz = pz;
			this.cuboids = cuboids;
		}
	}

	/** One cuboid: its 8 corners (its own rotation applied), faces, lod, glow and RGB tint. */
	static final class Cuboid {
		final float[] corners = new float[24];
		final Face[] faces;
		final int lod;
		final boolean glow;
		final int tint;

		Cuboid(Face[] faces, int lod, boolean glow, int tint) {
			this.faces = faces;
			this.lod = lod;
			this.glow = glow;
			this.tint = tint;
		}
	}

	/** One face: four corner indices, normalised UVs and its baked shade. */
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

		Face(int[] c, float u0, float v0, float u1, float v1, float shade) {
			this.c0 = c[0];
			this.c1 = c[1];
			this.c2 = c[2];
			this.c3 = c[3];
			this.u0 = u0;
			this.v0 = v0;
			this.u1 = u1;
			this.v1 = v1;
			this.shade = shade;
		}
	}

	/** Loads the file; a missing file or a parse error logs once and returns an empty library. */
	static Library load(Identifier json) {
		try {
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(json);
			if (resource.isEmpty()) {
				Kultivator.LOGGER.warn("Kultivator: Film kosmik 3D: {} missing", json);
				return new Library(json, Map.of());
			}
			try (Reader reader = resource.get().openAsReader()) {
				return parse(json, JsonParser.parseReader(reader).getAsJsonObject());
			}
		} catch (IOException | RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: Film kosmik 3D: {}: {}", json, exception.toString());
			return new Library(json, Map.of());
		}
	}

	private static Library parse(Identifier source, JsonObject root) {
		Identifier atlas = SurgaLayout.ATLAS;
		if (root.has("atlas")) {
			Identifier parsed = Identifier.tryParse(root.get("atlas").getAsString());
			if (parsed != null && parsed.getPath().endsWith(".png")) {
				atlas = parsed;
			}
		}
		float size = root.has("atlasSize") ? Math.max(1.0F, root.get("atlasSize").getAsFloat()) : 256.0F;
		Map<String, Mesh> meshes = new HashMap<>();
		if (root.has("models") && root.get("models").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("models").entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject model = entry.getValue().getAsJsonObject();
				List<Part> parts = new ArrayList<>();
				if (model.has("parts") && model.get("parts").isJsonObject()) {
					for (Map.Entry<String, JsonElement> pe : model.getAsJsonObject("parts").entrySet()) {
						if (!pe.getValue().isJsonObject()) {
							continue;
						}
						JsonObject part = pe.getValue().getAsJsonObject();
						float[] pivot = vec(part.get("pivot"));
						List<Cuboid> cuboids = new ArrayList<>();
						if (part.has("cuboids") && part.get("cuboids").isJsonArray()) {
							for (JsonElement element : part.getAsJsonArray("cuboids")) {
								Cuboid cuboid = element.isJsonObject() ? cuboid(element.getAsJsonObject(), size) : null;
								if (cuboid != null) {
									cuboids.add(cuboid);
								}
							}
						}
						parts.add(new Part(pe.getKey(), pivot[0], pivot[1], pivot[2], cuboids.toArray(new Cuboid[0])));
					}
				}
				meshes.put(entry.getKey(), new Mesh(entry.getKey(), parts.toArray(new Part[0])));
			}
		}
		return new Library(atlas, meshes);
	}

	private static @Nullable Cuboid cuboid(JsonObject json, float size) {
		float[] from = vec(json.get("from"));
		float[] to = vec(json.get("to"));
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
			rotate(cuboid.corners, axis, angle, vec(rot.get("origin")));
		}
		return cuboid;
	}

	/** Rotates 8 corners about {@code origin} round one axis, in the same sense as {@code KosmikMesh}. */
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

	private static float[] vec(@Nullable JsonElement element) {
		float[] out = new float[3];
		if (element != null && element.isJsonArray()) {
			JsonArray a = element.getAsJsonArray();
			for (int i = 0; i < 3 && i < a.size(); i++) {
				out[i] = a.get(i).getAsFloat();
			}
		}
		return out;
	}

	private SurgaMesh() {
	}
}
