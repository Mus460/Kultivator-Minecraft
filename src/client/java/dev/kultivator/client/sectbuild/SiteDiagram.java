package dev.kultivator.client.sectbuild;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.sect.build.api.ComponentView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The ink site diagrams of the template screen and the Bangun tablet (DESIGN 21.12 step 2, 21.21): a top-down sketch drawn
 * at runtime, gate at the bottom, in template-local coordinates (+z towards the gate, the stele at 0,0). With a real plan
 * (the preview's components, or the site's components with their states) every component box is drawn by its kind
 * (halls with roof hatching, water, gardens, paving, walls, the disc, peaks, hanging islands, gates); before the server
 * sent a plan a procedural sketch of the template's archetype (DESIGN 21.1 Table B, 21.3, 21.4) stands in. Later tiers than
 * the scrubber fade; on the tablet done parts are full ink, the open one pulses gold, queued ones are faint outlines,
 * blocked ones carry a red seal. Game design and art direction, no canon claimed.
 */
public final class SiteDiagram {

	// ---- shapes
	public static final int BUILDING = 0;
	public static final int WATER = 1;
	public static final int GARDEN = 2;
	public static final int PAVING = 3;
	public static final int WALL = 4;
	public static final int DISC = 5;
	public static final int PEAK = 6;
	public static final int ISLAND = 7;
	public static final int GATE = 8;

	/** DESIGN 21.1 Table A local bounds: x half-width, z min (back), z max (gate). */
	public static final int[] X_HALF = {12, 16, 22, 28, 36, 44, 54, 66, 80};
	public static final int[] Z_MIN = {-16, -24, -32, -38, -50, -60, -72, -88, -100};
	public static final int[] Z_MAX = {12, 14, 18, 24, 30, 36, 44, 52, 60};
	/** Table B: lodestone distance d and ring radius r of the formation disc. */
	public static final int[] DISC_Z = {2, 6, 6, 6, 6, 6, 6, 6, 6};
	public static final int[] DISC_R = {4, 4, 5, 5, 5, 7, 8, 8, 9};
	/** Templates with a perimeter wall (6, 7 and 9 have boundary stones instead). */
	private static final boolean[] WALLED = {true, true, true, true, true, false, false, true, false};

	private static final int INK = 0xFF2A332E;
	private static final int[] TIER_INK = {0xFF3E7F64, 0xFF5E8F3A, 0xFF3D6F9A, 0xFF9A7A2E, 0xFF6E4E9A, 0xFFA45A2E, 0xFF9A3E52, 0xFF2E8A8A, 0xFFB08A1E};

	/** One part of a diagram in template-local blocks. */
	public record Part(double x0, double z0, double x1, double z1, int tier, int shape, int state, @Nullable Component name, int progress) {
		Part(double x0, double z0, double x1, double z1, int tier, int shape) {
			this(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), tier, shape, -1, null, 0);
		}
	}

	/** The local bounds of a diagram (x0, z0, x1, z1). */
	public record Bounds(double x0, double z0, double x1, double z1) {
		public static Bounds of(int template) {
			int t = Math.max(1, Math.min(9, template)) - 1;
			return new Bounds(-X_HALF[t] - 0.5D, Z_MIN[t] - 0.5D, X_HALF[t] + 0.5D, Z_MAX[t] + 0.5D);
		}
	}

	private static final List<List<Part>> SKETCHES = new ArrayList<>();

	// ================================================================== from real data

	/** The shape of a component kind ("hall", "pond", "tree", ...). */
	public static int shapeOf(String kind) {
		String k = kind.toLowerCase(java.util.Locale.ROOT);
		if (k.contains("pond") || k.contains("lake") || k.contains("stream") || k.contains("water") || k.contains("moat") || k.contains("basin")) {
			return WATER;
		}
		if (k.contains("island")) {
			return ISLAND;
		}
		if (k.contains("tree") || k.contains("bamboo") || k.contains("herb") || k.contains("garden") || k.contains("grove") || k.contains("ornament")) {
			return GARDEN;
		}
		if (k.contains("paving") || k.contains("path") || k.contains("plaza") || k.contains("terrace") || k.contains("stair") || k.contains("drill")
				|| k.contains("archery") || k.contains("arena") || k.contains("terrain")) {
			return PAVING;
		}
		if (k.contains("wall") || k.contains("boundary")) {
			return WALL;
		}
		if (k.contains("disc") || k.contains("formation")) {
			return DISC;
		}
		if (k.contains("peak") || k.contains("hill") || k.contains("rock")) {
			return PEAK;
		}
		if (k.contains("gate") || k.contains("paifang")) {
			return GATE;
		}
		return BUILDING;
	}

	/** World xz to template-local (undoing {@code rot} clockwise quarter turns about the stele). */
	static double[] toLocal(double x, double z, int[] stele, int rot) {
		double lx = x - stele[0];
		double lz = z - stele[2];
		for (int i = 0; i < Math.floorMod(rot, 4); i++) {
			double nx = lz;
			double nz = -lx;
			lx = nx;
			lz = nz;
		}
		return new double[]{lx, lz};
	}

	/** Parts from a component list ({@code comps} of a preview or the Bangun tablet's info), in local coordinates. */
	public static List<Part> fromComponents(ListTag comps, int[] stele, int rot) {
		List<Part> parts = new ArrayList<>();
		for (Tag each : comps) {
			if (!(each instanceof CompoundTag comp)) {
				continue;
			}
			int[] box = comp.getIntArray("box").orElse(null);
			if (box == null || box.length != 6) {
				continue;
			}
			double[] a = toLocal(box[0], box[2], stele, rot);
			double[] b = toLocal(box[3] + 1.0D, box[5] + 1.0D, stele, rot);
			Component name = comp.read("name", ComponentSerialization.CODEC).orElse(null);
			parts.add(new Part(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), comp.getIntOr("tier", 1),
					shapeOf(comp.getStringOr("kind", "")), comp.getIntOr("st", -1), name, comp.getIntOr("pg", 0)));
		}
		return parts;
	}

	/** Bounds from a world footprint box. */
	public static Bounds bounds(int[] fp, int[] stele, int rot) {
		double[] a = toLocal(fp[0], fp[2], stele, rot);
		double[] b = toLocal(fp[3] + 1.0D, fp[5] + 1.0D, stele, rot);
		return new Bounds(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[0], b[0]), Math.max(a[1], b[1]));
	}

	// ================================================================== drawing

	/**
	 * Draws a diagram in the square {@code x, y, size}; returns the index of the part under the mouse (-1 none).
	 * {@code current} is the tier being built on the tablet (0 on the template screen: no states).
	 */
	public static int draw(GuiGraphicsExtractor g, float x, float y, float size, List<Part> parts, Bounds bounds, int scrub, int current, int mouseX,
			int mouseY, boolean showStele) {
		double width = bounds.x1() - bounds.x0();
		double depth = bounds.z1() - bounds.z0();
		float scale = (float) (size / Math.max(width, depth));
		float ox = x + (float) ((size - width * scale) / 2.0D);
		float oy = y + (float) ((size - depth * scale) / 2.0D);
		// Paper, a faint wash and the footprint's dashed ink edge.
		g.fill(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size), Palette.argb(0x40, Palette.PAPER_DEEP));
		float fx0 = ox;
		float fy0 = oy;
		float fx1 = ox + (float) (width * scale);
		float fy1 = oy + (float) (depth * scale);
		dashed(g, fx0, fy0, fx1, fy0, Palette.argb(0xA0, INK));
		dashed(g, fx1, fy0, fx1, fy1, Palette.argb(0xA0, INK));
		dashed(g, fx1, fy1, fx0, fy1, Palette.argb(0xA0, INK));
		dashed(g, fx0, fy1, fx0, fy0, Palette.argb(0xA0, INK));
		// The axis.
		float axisX = ox + (float) ((0.5D - bounds.x0()) * scale);
		Brush.rect(g, axisX - 0.3F, fy0 + 2.0F, axisX + 0.3F, fy1 - 1.0F, Palette.argb(0x60, Palette.GOLD));

		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < parts.size(); i++) {
			order.add(i);
		}
		order.sort(Comparator.comparingInt(i -> layer(parts.get(i).shape())));
		int hover = -1;
		long millis = Util.getMillis();
		float pulse = 0.5F + 0.5F * (float) Math.sin(millis / 260.0D);
		for (int index : order) {
			Part part = parts.get(index);
			float px0 = ox + (float) ((part.x0() - bounds.x0()) * scale);
			float py0 = oy + (float) ((part.z0() - bounds.z0()) * scale);
			float px1 = ox + (float) ((part.x1() - bounds.x0()) * scale);
			float py1 = oy + (float) ((part.z1() - bounds.z0()) * scale);
			if (px1 - px0 < 1.0F) {
				px1 = px0 + 1.0F;
			}
			if (py1 - py0 < 1.0F) {
				py1 = py0 + 1.0F;
			}
			boolean future = part.tier() > scrub;
			int state = part.state();
			int ink = TIER_INK[Math.max(0, Math.min(8, part.tier() - 1))];
			float alpha = future ? 0.22F : current > 0 && state != ComponentView.STATE_DONE ? 0.45F : 1.0F;
			drawPart(g, part, px0, py0, px1, py1, ink, alpha, future || current > 0 && (state == ComponentView.STATE_QUEUED
					|| state == ComponentView.STATE_FUTURE), scale);
			if (state == ComponentView.STATE_OPEN) {
				Brush.frame(g, Math.round(px0) - 1, Math.round(py0) - 1, Math.round(px1) + 1, Math.round(py1) + 1, Palette.argb(Math.round(140 + 115 * pulse),
						Palette.GOLD_LIGHT));
				float fill = (px1 - px0) * part.progress() / 100.0F;
				Brush.rect(g, px0, py1 + 0.5F, px0 + fill, py1 + 1.5F, Palette.GOLD);
			} else if (state == ComponentView.STATE_BLOCKED) {
				float cx = (px0 + px1) / 2.0F;
				float cy = (py0 + py1) / 2.0F;
				Brush.rect(g, cx - 2.0F, cy - 2.0F, cx + 2.0F, cy + 2.0F, Palette.SEAL);
				Brush.rect(g, cx - 1.0F, cy - 0.3F, cx + 1.0F, cy + 0.3F, Palette.TALISMAN);
			}
			if (mouseX >= px0 && mouseX < px1 && mouseY >= py0 && mouseY < py1 && part.shape() != WALL) {
				hover = index;
			}
		}
		if (hover >= 0) {
			Part part = parts.get(hover);
			float px0 = ox + (float) ((part.x0() - bounds.x0()) * scale);
			float py0 = oy + (float) ((part.z0() - bounds.z0()) * scale);
			float px1 = ox + (float) ((part.x1() - bounds.x0()) * scale);
			float py1 = oy + (float) ((part.z1() - bounds.z0()) * scale);
			Brush.frame(g, Math.round(px0) - 1, Math.round(py0) - 1, Math.round(px1) + 1, Math.round(py1) + 1, Palette.SEAL);
		}
		if (showStele) {
			float sx = ox + (float) ((0.5D - bounds.x0()) * scale);
			float sy = oy + (float) ((0.5D - bounds.z0()) * scale);
			Brush.disc(g, sx, sy, Math.max(1.2F, scale * 0.9F), Palette.SEAL);
			Brush.disc(g, sx, sy, Math.max(0.5F, scale * 0.35F), Palette.TALISMAN);
		}
		return hover;
	}

	private static int layer(int shape) {
		return switch (shape) {
			case PAVING -> 0;
			case PEAK -> 1;
			case WATER -> 2;
			case GARDEN -> 3;
			case WALL -> 4;
			case DISC -> 5;
			case BUILDING -> 6;
			case GATE -> 7;
			default -> 8;
		};
	}

	private static void drawPart(GuiGraphicsExtractor g, Part part, float x0, float y0, float x1, float y1, int ink, float alpha, boolean faint, float scale) {
		int a = Math.round(255 * alpha);
		float w = x1 - x0;
		float h = y1 - y0;
		float cx = (x0 + x1) / 2.0F;
		float cy = (y0 + y1) / 2.0F;
		switch (part.shape()) {
			case PAVING -> {
				Brush.rect(g, x0, y0, x1, y1, Palette.argb(Math.round(70 * alpha), 0xD8D0B8));
				if (!faint) {
					frameF(g, x0, y0, x1, y1, Palette.argb(Math.round(90 * alpha), 0x8C8470));
				}
			}
			case WATER -> {
				ellipse(g, cx, cy, w / 2.0F, h / 2.0F, Palette.argb(Math.round(170 * alpha), 0x5FA8C8));
				ellipse(g, cx - w * 0.08F, cy - h * 0.1F, w * 0.25F, h * 0.18F, Palette.argb(Math.round(90 * alpha), 0xCDEBF5));
			}
			case GARDEN -> {
				int dots = Math.max(1, Math.min(9, Math.round(w * h / 18.0F)));
				for (int i = 0; i < dots; i++) {
					float dx = x0 + w * ((i * 37 % 10) / 10.0F + 0.05F);
					float dy = y0 + h * ((i * 53 % 10) / 10.0F + 0.05F);
					Brush.dot(g, dx, dy, Math.max(0.8F, Math.min(2.2F, scale * 1.4F)), Palette.argb(Math.round(200 * alpha), 0x4E8A3A));
				}
			}
			case WALL -> frameF(g, x0, y0, x1, y1, Palette.argb(Math.round(220 * alpha), 0x9A2E24));
			case DISC -> {
				float r = Math.min(w, h) / 2.0F;
				Brush.ring(g, cx, cy, Math.max(0.5F, r - 0.8F), r, Palette.argb(Math.round(220 * alpha), 0x7A4EB0));
				Brush.dot(g, cx, cy, Math.max(0.8F, r * 0.18F), Palette.argb(a, 0x4A4A4A));
			}
			case PEAK -> {
				float r = Math.max(w, h) / 2.0F;
				for (int ring = 0; ring < 3; ring++) {
					float rr = r * (1.0F - ring * 0.3F);
					ringE(g, cx, cy, w / 2.0F * (1.0F - ring * 0.3F), h / 2.0F * (1.0F - ring * 0.3F), Palette.argb(Math.round((110 + ring * 40) * alpha), 0x6A6A5A));
					if (rr < 1.0F) {
						break;
					}
				}
			}
			case ISLAND -> {
				ellipse(g, cx, cy, w / 2.0F, h / 2.0F, Palette.argb(Math.round(60 * alpha), 0x88B07A));
				ringE(g, cx, cy, w / 2.0F, h / 2.0F, Palette.argb(Math.round(200 * alpha), 0x2E8A8A));
			}
			case GATE -> {
				Brush.rect(g, x0, y0, x1, y1, Palette.argb(a, 0xB3281D));
				Brush.rect(g, x0, y0, x1, y0 + Math.max(0.6F, h * 0.3F), Palette.argb(a, 0x2E3438));
			}
			default -> {
				if (faint) {
					frameF(g, x0, y0, x1, y1, Palette.argb(Math.round(170 * alpha), ink));
				} else {
					Brush.rect(g, x0, y0, x1, y1, Palette.argb(a, ink));
					// The roof: a darker ridge along the long side and two hatched slopes.
					boolean wide = w >= h;
					int roof = Palette.argb(a, Palette.darken(ink, 0.35F));
					if (wide) {
						Brush.rect(g, x0, cy - 0.35F, x1, cy + 0.35F, roof);
					} else {
						Brush.rect(g, cx - 0.35F, y0, cx + 0.35F, y1, roof);
					}
					frameF(g, x0, y0, x1, y1, Palette.argb(Math.round(220 * alpha), INK));
				}
			}
		}
	}

	private static void frameF(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
		Brush.rect(g, x0, y0, x1, y0 + 0.5F, color);
		Brush.rect(g, x0, y1 - 0.5F, x1, y1, color);
		Brush.rect(g, x0, y0, x0 + 0.5F, y1, color);
		Brush.rect(g, x1 - 0.5F, y0, x1, y1, color);
	}

	private static void dashed(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
		float length = (float) Math.hypot(x1 - x0, y1 - y0);
		int dashes = Math.max(1, Math.round(length / 4.0F));
		for (int i = 0; i < dashes; i += 2) {
			float a = i / (float) dashes;
			float b = Math.min(1.0F, (i + 1) / (float) dashes);
			Brush.line(g, x0 + (x1 - x0) * a, y0 + (y1 - y0) * a, x0 + (x1 - x0) * b, y0 + (y1 - y0) * b, 0.7F, color);
		}
	}

	/** A filled ellipse from horizontal spans. */
	private static void ellipse(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, int color) {
		if (rx <= 0.0F || ry <= 0.0F) {
			return;
		}
		int rows = Math.max(1, Math.round(ry * 2.0F));
		for (int i = 0; i < rows; i++) {
			float t = (i + 0.5F) / rows * 2.0F - 1.0F;
			float half = rx * (float) Math.sqrt(Math.max(0.0F, 1.0F - t * t));
			float yy = cy - ry + i * (2.0F * ry / rows);
			Brush.rect(g, cx - half, yy, cx + half, yy + 2.0F * ry / rows, color);
		}
	}

	private static void ringE(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, int color) {
		int steps = Math.max(12, Math.round((rx + ry) * 1.5F));
		for (int i = 0; i < steps; i++) {
			double a0 = Math.PI * 2.0D * i / steps;
			double a1 = Math.PI * 2.0D * (i + 1) / steps;
			Brush.line(g, cx + (float) Math.cos(a0) * rx, cy + (float) Math.sin(a0) * ry, cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry,
					0.6F, color);
		}
	}

	/** The skyline strip along the axis: each part's silhouette by its tier and kind, the main hall tallest. */
	public static void skyline(GuiGraphicsExtractor g, float x, float y, float w, float h, List<Part> parts, Bounds bounds, int scrub) {
		g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), Palette.argb(0x30, Palette.PAPER_DEEP));
		Brush.rect(g, x, y + h - 0.6F, x + w, y + h, Palette.argb(0xA0, INK));
		double depth = bounds.z1() - bounds.z0();
		for (Part part : parts) {
			if (part.tier() > scrub || part.shape() != BUILDING && part.shape() != GATE && part.shape() != PEAK) {
				continue;
			}
			float sx0 = x + (float) ((part.z0() - bounds.z0()) / depth * w);
			float sx1 = x + (float) ((part.z1() - bounds.z0()) / depth * w);
			double size = Math.min(part.x1() - part.x0(), part.z1() - part.z0());
			float height = (float) Math.min(h - 2.0F, part.shape() == PEAK ? h - 2.0F : 2.0D + size * 0.35D);
			int ink = Palette.argb(0xC0, TIER_INK[Math.max(0, Math.min(8, part.tier() - 1))]);
			if (part.shape() == PEAK) {
				for (int i = 0; i < 6; i++) {
					float t = i / 6.0F;
					float bh = height * (1.0F - Math.abs(t - 0.5F) * 2.0F);
					Brush.rect(g, sx0 + (sx1 - sx0) * t, y + h - bh, sx0 + (sx1 - sx0) * (t + 1.0F / 6.0F), y + h, Palette.argb(0x70, 0x6A6A5A));
				}
				continue;
			}
			Brush.rect(g, sx0, y + h - height * 0.6F, sx1, y + h, ink);
			// A curved roof: wider than the body, with upturned ends.
			Brush.rect(g, sx0 - 0.8F, y + h - height * 0.8F, sx1 + 0.8F, y + h - height * 0.6F, Palette.argb(0xE0, 0x2E3438));
			Brush.rect(g, sx0 - 1.2F, y + h - height * 0.9F, sx0 - 0.4F, y + h - height * 0.75F, Palette.argb(0xE0, 0x2E3438));
			Brush.rect(g, sx1 + 0.4F, y + h - height * 0.9F, sx1 + 1.2F, y + h - height * 0.75F, Palette.argb(0xE0, 0x2E3438));
		}
	}

	// ================================================================== procedural sketches (before a plan arrives)

	/** The procedural sketch of a template (cached). */
	public static List<Part> sketch(int template) {
		if (SKETCHES.isEmpty()) {
			for (int t = 1; t <= 9; t++) {
				SKETCHES.add(t == 1 ? unranked() : generic(t));
			}
		}
		return SKETCHES.get(Math.max(1, Math.min(9, template)) - 1);
	}

	/** Template 1 after the site plan of DESIGN 21.4. */
	private static List<Part> unranked() {
		List<Part> p = new ArrayList<>();
		p.add(new Part(-2, -5, 3, 13, 1, PAVING));
		p.add(new Part(-5, -5, 6, 5, 1, PAVING));
		p.add(new Part(-8, -16, 9, -5, 1, BUILDING));
		p.add(new Part(-3, 12, 4, 13, 1, GATE));
		p.add(new Part(-4, -5, -3, -4, 1, WATER));
		p.add(new Part(4, -5, 5, -4, 1, WATER));
		p.add(new Part(10, 7, 12, 9, 1, GARDEN));
		p.add(new Part(-12, -3, -7, 7, 2, BUILDING));
		p.add(new Part(6, 8, 13, 12, 2, BUILDING));
		p.add(new Part(-12, 7, -7, 8, 2, GARDEN));
		p.add(new Part(6, 7, 9, 8, 2, GARDEN));
		p.add(new Part(-10, -6, -8, -4, 2, GARDEN));
		p.add(new Part(8, -3, 13, 7, 3, BUILDING));
		p.add(new Part(-12, 8, -5, 12, 4, BUILDING));
		p.add(new Part(-12, -16, 13, 13, 4, WALL));
		p.add(new Part(-6, -6, -4, -4, 4, WATER));
		p.add(new Part(5, -6, 7, -4, 4, WATER));
		p.add(new Part(-4, -2, 5, 7, 5, DISC));
		p.add(new Part(-1, 2, 2, 4, 6, GATE));
		p.add(new Part(-10, -10, -8, -8, 7, GARDEN));
		p.add(new Part(9, -10, 11, -8, 7, GARDEN));
		p.add(new Part(-12, -16, -9, -11, 8, GARDEN));
		p.add(new Part(10, -16, 13, -11, 8, GARDEN));
		p.add(new Part(-6, -15, -3, -12, 9, BUILDING));
		return p;
	}

	/** Templates 2-9: the shared growth order (21.2) laid out on the template's footprint and archetype (Table B). */
	private static List<Part> generic(int t) {
		List<Part> p = new ArrayList<>();
		double half = X_HALF[t - 1];
		double zMin = Z_MIN[t - 1];
		double zMax = Z_MAX[t - 1];
		double depth = zMax - zMin;
		double back = zMin + depth * 0.04D;
		// T1: axis, forecourt, Aula Pendiri, Outer dorm, inner gate, well, hero tree.
		p.add(new Part(-1.5, zMin + depth * 0.28D, 1.5, zMax, 1, PAVING));
		p.add(new Part(-half * 0.28D, -depth * 0.08D, half * 0.28D, depth * 0.1D, 1, PAVING));
		p.add(new Part(-half * 0.22D, -depth * 0.2D, half * 0.22D, -depth * 0.11D, 1, BUILDING));
		p.add(new Part(half * 0.36D, -depth * 0.1D, half * 0.62D, depth * 0.06D, 1, BUILDING));
		p.add(new Part(-half * 0.1D, depth * 0.15D, half * 0.1D, depth * 0.17D, 1, GATE));
		p.add(new Part(half * 0.16D, depth * 0.03D, half * 0.22D, depth * 0.07D, 1, WATER));
		p.add(new Part(half * 0.45D, depth * 0.12D, half * 0.55D, depth * 0.17D, 1, GARDEN));
		if (t >= 8) {
			p.add(new Part(-half * 0.62D, -depth * 0.1D, -half * 0.36D, depth * 0.06D, 1, BUILDING));
		}
		// T2: kitchen court, herb garden, trees.
		p.add(new Part(half * 0.5D, depth * 0.2D, half * 0.85D, depth * 0.32D, 2, BUILDING));
		p.add(new Part(-half * 0.85D, depth * 0.2D, -half * 0.5D, depth * 0.32D, 2, GARDEN));
		p.add(new Part(half * 0.2D, depth * 0.22D, half * 0.4D, depth * 0.3D, 2, GARDEN));
		// T3: the Scripture Pavilion and the galleries round the plaza.
		p.add(new Part(-half * 0.62D, -depth * 0.12D, -half * 0.36D, depth * 0.06D, 3, BUILDING));
		if (t >= 3) {
			p.add(new Part(-half * 0.3D, -depth * 0.1D, -half * 0.27D, depth * 0.1D, 3, BUILDING));
			p.add(new Part(half * 0.27D, -depth * 0.1D, half * 0.3D, depth * 0.1D, 3, BUILDING));
		}
		// T4: alchemy hall, water, the boundary and the gate.
		p.add(new Part(half * 0.42D, zMin + depth * 0.26D, half * 0.72D, zMin + depth * 0.38D, 4, BUILDING));
		if (t == 7 || t == 9) {
			p.add(new Part(-half * 0.92D, zMin + depth * 0.22D, -half * 0.1D, zMin + depth * 0.5D, 4, WATER));
			p.add(new Part(-half * 0.55D, zMin + depth * 0.31D, -half * 0.42D, zMin + depth * 0.38D, 4, BUILDING));
		} else {
			p.add(new Part(-half * 0.75D, zMin + depth * 0.3D, -half * 0.38D, zMin + depth * 0.46D, 4, WATER));
		}
		if (WALLED[t - 1]) {
			p.add(new Part(-half, zMin, half + 1, zMax + 1, 4, WALL));
			if (t == 8) {
				p.add(new Part(-half + 2, zMax - 3, half - 1, zMax - 1, 4, WATER));
			}
		} else {
			for (int i = 0; i < 4; i++) {
				double sx = i % 2 == 0 ? -half : half - 1;
				double sz = i < 2 ? zMin : zMax - 1;
				p.add(new Part(sx, sz, sx + 2, sz + 2, 4, GATE));
			}
		}
		p.add(new Part(-half * 0.16D, zMax - 3, half * 0.16D, zMax + 1, 4, GATE));
		// T5: the disc and the corner towers.
		double r = DISC_R[t - 1];
		p.add(new Part(-r, DISC_Z[t - 1] - r, r + 1, DISC_Z[t - 1] + r + 1, 5, DISC));
		p.add(new Part(-half + 1, zMax - depth * 0.12D, -half + 1 + Math.max(3, half * 0.12D), zMax - depth * 0.12D + Math.max(3, half * 0.12D), 5, BUILDING));
		p.add(new Part(half - Math.max(3, half * 0.12D), zMax - depth * 0.12D, half, zMax - depth * 0.12D + Math.max(3, half * 0.12D), 5, BUILDING));
		// T6: the main hall on its terrace and the paifang.
		p.add(new Part(-half * 0.48D, back, half * 0.48D, zMin + depth * 0.27D, 6, PAVING));
		p.add(new Part(-half * 0.34D, back + depth * 0.03D, half * 0.34D, zMin + depth * 0.2D, 6, BUILDING));
		p.add(new Part(-half * 0.13D, zMax - depth * 0.1D, half * 0.13D, zMax - depth * 0.08D, 6, GATE));
		if (t == 6 || t == 9) {
			p.add(new Part(-half * 0.6D, zMin, half * 0.6D, back + depth * 0.06D, 6, PEAK));
		}
		// T7: drill ground with targets, bell and drum towers.
		p.add(new Part(-half * 0.85D, depth * 0.36D, -half * 0.45D, depth * 0.48D, 7, PAVING));
		if (t >= 3) {
			p.add(new Part(-half * 0.3D, -depth * 0.2D, -half * 0.22D, -depth * 0.14D, 7, BUILDING));
			p.add(new Part(half * 0.22D, -depth * 0.2D, half * 0.3D, -depth * 0.14D, 7, BUILDING));
		}
		// T8: pagoda, ancestral hall, meditation pavilion, bamboo, the extra peaks.
		double pagoda = Math.max(3, half * 0.1D);
		p.add(new Part(-half * 0.8D, back + depth * 0.05D, -half * 0.8D + pagoda, back + depth * 0.05D + pagoda, 8, BUILDING));
		p.add(new Part(half * 0.55D, back + depth * 0.04D, half * 0.85D, back + depth * 0.14D, 8, BUILDING));
		p.add(new Part(-half * 0.95D, back, -half * 0.85D, zMin + depth * 0.2D, 8, GARDEN));
		if (t >= 6) {
			p.add(new Part(half * 0.55D, zMin, half * 0.98D, back + depth * 0.1D, 8, PEAK));
			p.add(new Part(-half * 0.98D, zMin, -half * 0.55D, back + depth * 0.08D, 8, PEAK));
		}
		// T9: the tower entrance and the finale; sky elements for templates 7-9.
		p.add(new Part(half * 0.08D, back + depth * 0.2D, half * 0.16D, back + depth * 0.24D, 9, BUILDING));
		if (t == 7) {
			p.add(new Part(half * 0.4D, zMin + depth * 0.08D, half * 0.9D, zMin + depth * 0.22D, 9, PEAK));
		}
		if (t >= 7) {
			p.add(new Part(-half * 0.55D, zMin + depth * 0.28D, -half * 0.35D, zMin + depth * 0.36D, 9, ISLAND));
			p.add(new Part(-half * 0.25D, zMin + depth * 0.4D, -half * 0.08D, zMin + depth * 0.47D, 9, ISLAND));
			p.add(new Part(half * 0.1D, zMin + depth * 0.3D, half * 0.3D, zMin + depth * 0.38D, 9, ISLAND));
		}
		return p;
	}

	private SiteDiagram() {
	}
}
