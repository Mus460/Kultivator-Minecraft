package dev.kultivator.client.epic.kosmik.surga;

import dev.kultivator.client.epic.kosmik.StageFrame;
import dev.kultivator.client.epic.render.EpicRenderer;
import org.jetbrains.annotations.Nullable;

/**
 * The palace of the Heaven stage (DESIGN 32.3.1): every {@link SurgaLayout#INSTANCES} model, drawn per vertex through the
 * diorama with baked face shades and the stage light. Per frame each instance is culled by the view cone, dropped when it
 * would be a few pixels, drawn with only its Minimal parts beyond {@link #FAR_LOD} (a distance LOD on top of the quality's
 * {@code lod}), and its back faces are dropped on the CPU. The faces are counted per budget group first ({@link #plan}),
 * so the stage asks {@code takeQuads} in priority order before anything is written ({@link #draw}).
 */
final class SurgaPalace {

	/** Beyond this stage distance (to the bounding sphere) an instance draws only its lod-0 cuboids. */
	static final double FAR_LOD = 160.0D;
	/** At Minimal, instances outside the gate group farther than this (to the bounding sphere) are not drawn. */
	static final double MINIMAL_FAR = 260.0D;
	/** Instances smaller than this angular radius (radians) are not drawn. */
	private static final double TINY = 0.004D;
	/** A warm white over the whole palace (dawn light of Heaven). */
	private static final int TINT = 0xFFF6EC;
	private static final String DOOR_LEFT = "pintu_kiri";
	private static final String DOOR_RIGHT = "pintu_kanan";

	private @Nullable SurgaMesh.Library library;
	private final SurgaMesh.Mesh[] meshes = new SurgaMesh.Mesh[SurgaLayout.INSTANCES.length];
	/** Per instance: its cuboids' corners placed in the stage this frame (24 per cuboid). */
	private final double[][] world = new double[SurgaLayout.INSTANCES.length][];
	/** Per instance: which faces look at the eye this frame (6 per cuboid). */
	private final boolean[][] front = new boolean[SurgaLayout.INSTANCES.length][];
	/** Per instance this frame: the quality drawn with, or −1 (skipped). */
	private final int[] drawQuality = new int[SurgaLayout.INSTANCES.length];
	private final int[] drawQuads = new int[SurgaLayout.INSTANCES.length];
	private final float[] drawn = new float[24];
	private final boolean[] mapped = new boolean[8];
	private final float[] tmp = new float[3];
	private int loadedModels;

	/** Loads the model file and resolves every instance (prepare; allocates the per-instance scratch once). */
	void load() {
		SurgaMesh.Library lib = SurgaMesh.load(SurgaLayout.MODELS);
		library = lib;
		loadedModels = 0;
		for (int i = 0; i < meshes.length; i++) {
			SurgaMesh.Mesh mesh = lib.mesh(SurgaLayout.INSTANCES[i].model());
			meshes[i] = mesh;
			if (mesh != null && mesh.cuboids > 0) {
				world[i] = new double[mesh.cuboids * 24];
				front[i] = new boolean[mesh.cuboids * 6];
				loadedModels++;
			} else {
				meshes[i] = null;
				world[i] = null;
				front[i] = null;
			}
		}
	}

	int loadedModels() {
		return loadedModels;
	}

	void release() {
		library = null;
		loadedModels = 0;
		for (int i = 0; i < meshes.length; i++) {
			meshes[i] = null;
			world[i] = null;
			front[i] = null;
			drawQuality[i] = -1;
		}
	}

	/**
	 * Decides every instance for this frame and adds its front faces to {@code groupQuads}: quality and settings, the view
	 * cone, the tiny-size cut, the distance LOD, the door angle (degrees) and back-face culling.
	 */
	void plan(StageFrame frame, SurgaDraw view, boolean ringan, boolean pack, double doors, int[] groupQuads) {
		int quality = frame.quality;
		for (int i = 0; i < SurgaLayout.INSTANCES.length; i++) {
			drawQuality[i] = -1;
			drawQuads[i] = 0;
			SurgaLayout.Inst inst = SurgaLayout.INSTANCES[i];
			if (meshes[i] == null || quality < inst.minQuality()) {
				continue;
			}
			if (ringan && (inst.flags() & SurgaLayout.HALVE) != 0 || pack && (inst.flags() & SurgaLayout.NO_PACK) != 0) {
				continue;
			}
			double r = inst.radius() * inst.scale();
			if (!view.sees(inst.cx(), inst.cy(), inst.cz(), r)) {
				continue;
			}
			double distance = view.distance(inst.cx(), inst.cy(), inst.cz());
			if ((inst.flags() & SurgaLayout.ALWAYS) == 0 && distance > r && r / distance < TINY) {
				continue;
			}
			// Minimal keeps its few quads for what is near: only the gate group is drawn from far away
			if (quality == 0 && inst.group() != SurgaLayout.G_GATE && distance - r > MINIMAL_FAR) {
				continue;
			}
			int q = distance - r > FAR_LOD ? 0 : quality;
			int quads = place(i, q, doors, frame.ex, frame.ey, frame.ez);
			drawQuality[i] = q;
			drawQuads[i] = quads;
			groupQuads[inst.group()] += quads;
		}
	}

	/** Places instance i's cuboids (lod ≤ q) in the stage and marks the faces that look at the eye; returns their count. */
	private int place(int i, int q, double doors, double ex, double ey, double ez) {
		SurgaLayout.Inst inst = SurgaLayout.INSTANCES[i];
		SurgaMesh.Mesh mesh = meshes[i];
		double[] w = world[i];
		boolean[] fr = front[i];
		double scale = inst.scale();
		int k = 0;
		int quads = 0;
		for (SurgaMesh.Part part : mesh.parts) {
			double angle;
			if ((inst.flags() & SurgaLayout.DOORS) != 0 && DOOR_LEFT.equals(part.name)) {
				// the doors swing inward (toward +z) on hinges at the inner pillars
				angle = -Math.toRadians(doors);
			} else if ((inst.flags() & SurgaLayout.DOORS) != 0 && DOOR_RIGHT.equals(part.name)) {
				angle = Math.toRadians(doors);
			} else {
				angle = Math.toRadians(inst.yaw());
			}
			double c = Math.cos(angle);
			double s = Math.sin(angle);
			for (SurgaMesh.Cuboid cub : part.cuboids) {
				int base = k * 24;
				if (cub.lod > q) {
					for (int f = 0; f < 6; f++) {
						fr[k * 6 + f] = false;
					}
					k++;
					continue;
				}
				for (int j = 0; j < 8; j++) {
					double lx = cub.corners[j * 3] - part.px;
					double ly = cub.corners[j * 3 + 1] - part.py;
					double lz = cub.corners[j * 3 + 2] - part.pz;
					// the same turning sense as JOML rotationY and KosmikMesh: x' = x cos + z sin, z' = −x sin + z cos
					double rx = lx * c + lz * s;
					double rz = -lx * s + lz * c;
					w[base + j * 3] = inst.x() + (part.px + rx) * scale;
					w[base + j * 3 + 1] = inst.y() + (part.py + ly) * scale;
					w[base + j * 3 + 2] = inst.z() + (part.pz + rz) * scale;
				}
				for (int f = 0; f < cub.faces.length; f++) {
					SurgaMesh.Face face = cub.faces[f];
					boolean visible = faces(w, base, face, ex, ey, ez);
					fr[k * 6 + f] = visible;
					if (visible) {
						quads++;
					}
				}
				k++;
			}
		}
		return quads;
	}

	/** True when the face's outward normal ((BL − TL) × (TR − TL)) looks at the eye. */
	private static boolean faces(double[] w, int base, SurgaMesh.Face face, double ex, double ey, double ez) {
		int a = base + face.c0 * 3;
		int b = base + face.c1 * 3;
		int d = base + face.c3 * 3;
		double rx = w[b] - w[a];
		double ry = w[b + 1] - w[a + 1];
		double rz = w[b + 2] - w[a + 2];
		double dx = w[d] - w[a];
		double dy = w[d + 1] - w[a + 1];
		double dz = w[d + 2] - w[a + 2];
		double nx = dy * rz - dz * ry;
		double ny = dz * rx - dx * rz;
		double nz = dx * ry - dy * rx;
		return nx * (w[a] - ex) + ny * (w[a + 1] - ey) + nz * (w[a + 2] - ez) < 0.0D;
	}

	/** Writes the planned instances of the granted groups (cutout for the body, additive for glowing cuboids). */
	void draw(StageFrame frame, boolean[] granted, float light) {
		SurgaMesh.Library lib = library;
		if (lib == null) {
			return;
		}
		EpicRenderer.Sink body = null;
		EpicRenderer.Sink shine = null;
		float lit = Math.max(0.0F, Math.min(1.5F, light));
		float tr = (TINT >> 16 & 0xFF) / 255.0F;
		float tg = (TINT >> 8 & 0xFF) / 255.0F;
		float tb = (TINT & 0xFF) / 255.0F;
		for (int i = 0; i < SurgaLayout.INSTANCES.length; i++) {
			int q = drawQuality[i];
			if (q < 0 || drawQuads[i] == 0 || !granted[SurgaLayout.INSTANCES[i].group()]) {
				continue;
			}
			double[] w = world[i];
			boolean[] fr = front[i];
			int k = 0;
			for (SurgaMesh.Part part : meshes[i].parts) {
				for (SurgaMesh.Cuboid cub : part.cuboids) {
					if (cub.lod > q) {
						k++;
						continue;
					}
					int base = k * 24;
					for (int j = 0; j < 8; j++) {
						mapped[j] = false;
					}
					float cr = (cub.tint >> 16 & 0xFF) / 255.0F * tr;
					float cg = (cub.tint >> 8 & 0xFF) / 255.0F * tg;
					float cb = (cub.tint & 0xFF) / 255.0F * tb;
					EpicRenderer.Sink s;
					if (cub.glow) {
						if (shine == null) {
							shine = frame.add(lib.atlas);
						}
						s = shine;
					} else {
						if (body == null) {
							body = frame.cutout(lib.atlas);
						}
						s = body;
					}
					for (int f = 0; f < cub.faces.length; f++) {
						if (!fr[k * 6 + f]) {
							continue;
						}
						SurgaMesh.Face face = cub.faces[f];
						float m = cub.glow ? 1.0F : face.shade * lit;
						int argb = 0xFF000000 | channel(cr * m) << 16 | channel(cg * m) << 8 | channel(cb * m);
						corner(frame, s, w, base, face.c0, face.u0, face.v0, argb);
						corner(frame, s, w, base, face.c1, face.u1, face.v0, argb);
						corner(frame, s, w, base, face.c2, face.u1, face.v1, argb);
						corner(frame, s, w, base, face.c3, face.u0, face.v1, argb);
					}
					k++;
				}
			}
		}
	}

	private void corner(StageFrame frame, EpicRenderer.Sink s, double[] w, int base, int c, float u, float v, int argb) {
		if (!mapped[c]) {
			frame.map(w[base + c * 3], w[base + c * 3 + 1], w[base + c * 3 + 2], tmp);
			drawn[c * 3] = tmp[0];
			drawn[c * 3 + 1] = tmp[1];
			drawn[c * 3 + 2] = tmp[2];
			mapped[c] = true;
		}
		frame.raw(s, drawn[c * 3], drawn[c * 3 + 1], drawn[c * 3 + 2], u, v, argb);
	}

	private static int channel(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255.0F)));
	}

	/** Instances drawn this frame (for info). */
	int drawn(boolean[] granted) {
		int n = 0;
		for (int i = 0; i < drawQuality.length; i++) {
			if (drawQuality[i] >= 0 && granted[SurgaLayout.INSTANCES[i].group()]) {
				n++;
			}
		}
		return n;
	}
}
