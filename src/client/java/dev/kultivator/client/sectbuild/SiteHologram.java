package dev.kultivator.client.sectbuild;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.sect.build.api.SectTemplates;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The footprint hologram (DESIGN 21.16), drawn with vanilla {@code net.minecraft.gizmos.Gizmos} from the client tick (the
 * per-tick gizmo collector is active there): jade light curtains along the footprint edge from the ground to +3, gold corner
 * pillars up to the envelope height, the gold axis line, terrace outlines at their fitted heights, the component boxes
 * coloured by tier up to the scrubber value, orange (cut) and blue (fill) ticks per 4x4 cell, a red X on each obstacle
 * and labels for the main components. At most {@link #BUDGET} gizmos a tick. While building (no preview) the open
 * component gets a faint gold wireframe and its label within 64 blocks.
 */
public final class SiteHologram {

	/** Gizmos per tick at most (DESIGN 21.15: at most 800). */
	public static final int BUDGET = 760;
	public static final double OPEN_RANGE = 64.0D;
	/** Tier colours T1-T9 (ink to gold), ARGB without alpha. */
	public static final int[] TIER_RGB = {0x7FD4B0, 0x9BD86A, 0x6FB8E8, 0xE8D06A, 0xB78CF0, 0xF09A5A, 0xE86A8A, 0x5AD8D8, 0xFFD54A};
	private static final int JADE = 0x8FE3C8;
	private static final int GOLD = 0xFFD54A;
	private static final int RED = 0xFF4A3A;
	private static final int BLUE = 0x4AA8FF;
	private static final int ORANGE = 0xFF9A3A;

	private static int used;

	static void tick(Minecraft minecraft) {
		used = 0;
		try {
			if (BuildClient.previewActive()) {
				drawPreview(minecraft, BuildClient.preview());
			} else {
				drawOpen(minecraft, BuildClient.status());
			}
		} catch (IllegalStateException exception) {
			// No gizmo collector in this phase of the tick: nothing to draw this time.
		}
	}

	// ================================================================== the preview

	private static void drawPreview(Minecraft minecraft, CompoundTag tag) {
		int[] fp = box(tag, "fp");
		if (fp == null || minecraft.player == null) {
			return;
		}
		int[] stele = tag.getIntArray("stele").orElse(null);
		double base = stele != null && stele.length == 3 ? stele[1] : Math.floor(minecraft.player.getY());
		long millis = Util.getMillis();
		float pulse = 0.65F + 0.35F * (float) Math.sin(millis / 500.0D);
		double x0 = fp[0];
		double z0 = fp[2];
		double x1 = fp[3] + 1.0D;
		double z1 = fp[5] + 1.0D;
		double top = fp[4] + 1.0D;

		// Jade curtains: four bands from the ground to +3, posts every 4 blocks.
		for (int level = 0; level < 4; level++) {
			int color = argb(Math.round((150 - level * 30) * pulse), JADE);
			double y = base + level + 0.05D;
			line(new Vec3(x0, y, z0), new Vec3(x1, y, z0), color, 2.5F);
			line(new Vec3(x1, y, z0), new Vec3(x1, y, z1), color, 2.5F);
			line(new Vec3(x1, y, z1), new Vec3(x0, y, z1), color, 2.5F);
			line(new Vec3(x0, y, z1), new Vec3(x0, y, z0), color, 2.5F);
		}
		int postColor = argb(Math.round(110 * pulse), JADE);
		int step = (x1 - x0) > 120 ? 8 : 4;
		for (double x = x0; x <= x1; x += step) {
			line(new Vec3(x, base, z0), new Vec3(x, base + 3.0D, z0), postColor, 1.5F);
			line(new Vec3(x, base, z1), new Vec3(x, base + 3.0D, z1), postColor, 1.5F);
		}
		for (double z = z0; z <= z1; z += step) {
			line(new Vec3(x0, base, z), new Vec3(x0, base + 3.0D, z), postColor, 1.5F);
			line(new Vec3(x1, base, z), new Vec3(x1, base + 3.0D, z), postColor, 1.5F);
		}
		// Gold corner pillars up to the envelope.
		int gold = argb(220, GOLD);
		line(new Vec3(x0, base, z0), new Vec3(x0, top, z0), gold, 3.0F);
		line(new Vec3(x1, base, z0), new Vec3(x1, top, z0), gold, 3.0F);
		line(new Vec3(x1, base, z1), new Vec3(x1, top, z1), gold, 3.0F);
		line(new Vec3(x0, base, z1), new Vec3(x0, top, z1), gold, 3.0F);
		// The gold axis through the stele, from the back to the gate.
		if (stele != null && stele.length == 3) {
			Direction gate = SectTemplates.gate(tag.getIntOr("rot", 0));
			double cx = stele[0] + 0.5D;
			double cz = stele[2] + 0.5D;
			double y = base + 0.3D;
			Vec3 front = switch (gate) {
				case NORTH -> new Vec3(cx, y, z0);
				case EAST -> new Vec3(x1, y, cz);
				case WEST -> new Vec3(x0, y, cz);
				default -> new Vec3(cx, y, z1);
			};
			Vec3 back = switch (gate) {
				case NORTH -> new Vec3(cx, y, z1);
				case EAST -> new Vec3(x0, y, cz);
				case WEST -> new Vec3(x1, y, cz);
				default -> new Vec3(cx, y, z0);
			};
			line(back, front, argb(230, GOLD), 3.5F);
			text(Component.translatable("kultivator.sectbuild.holo.gate").getString(), front.add(0.0D, 1.6D, 0.0D), argb(255, GOLD), 0.6F);
		}
		// Terrace outlines at their fitted heights.
		for (Tag each : tag.getListOrEmpty("zones")) {
			if (each instanceof CompoundTag zone) {
				int[] box = box(zone, "box");
				if (box != null) {
					double y = zone.getIntOr("y", (int) base) + 1.02D;
					rect(box[0], box[2], box[3] + 1.0D, box[5] + 1.0D, y, argb(140, 0xE8E2C8), 1.8F);
				}
			}
		}
		// Component boxes by tier up to the scrubber.
		int scrub = BuildClient.scrubTier();
		int labels = 0;
		for (Tag each : tag.getListOrEmpty("comps")) {
			if (!(each instanceof CompoundTag comp)) {
				continue;
			}
			int tier = Math.max(1, Math.min(9, comp.getIntOr("tier", 1)));
			if (tier > scrub) {
				continue;
			}
			int[] box = box(comp, "box");
			if (box == null) {
				continue;
			}
			int color = argb(tier == scrub ? 220 : 120, TIER_RGB[tier - 1]);
			cuboid(new AABB(box[0], box[1], box[2], box[3] + 1.0D, box[4] + 1.0D, box[5] + 1.0D), color, tier == scrub ? 2.5F : 1.5F);
			String kind = comp.getStringOr("kind", "");
			if (labels < 12 && important(kind)) {
				labels++;
				Component name = comp.read("name", net.minecraft.network.chat.ComponentSerialization.CODEC)
						.orElseGet(() -> Component.translatableWithFallback("kultivator.blueprint.comp." + kind, kind));
				text(name.getString(), new Vec3((box[0] + box[3] + 1) / 2.0D, box[4] + 1.6D, (box[2] + box[5] + 1) / 2.0D), argb(255,
						TIER_RGB[tier - 1]), 0.5F);
			}
		}
		// Cut (orange) and fill (blue) ticks per 4x4 cell.
		cutTicks(tag, fp, base);
		// A red X on each obstacle, a blue X on water to fill.
		crosses(tag.getLongArray("obst").orElse(new long[0]), argb(240, RED));
		crosses(tag.getLongArray("water").orElse(new long[0]), argb(200, BLUE));
		// "bandingkan": every footprint nested, when the engine sends them.
		for (Tag each : tag.getListOrEmpty("fps")) {
			int[] box = each instanceof CompoundTag compound ? box(compound, "box") : null;
			if (box != null) {
				rect(box[0], box[2], box[3] + 1.0D, box[5] + 1.0D, base + 0.1D, argb(200, GOLD), 2.0F);
			}
		}
	}

	private static boolean important(String kind) {
		return kind.contains("hall") || kind.contains("pagoda") || kind.contains("gate") || kind.contains("paifang") || kind.contains("tower")
				|| kind.contains("disc") || kind.contains("pavilion");
	}

	private static void cutTicks(CompoundTag tag, int[] fp, double base) {
		int width = (fp[3] - fp[0]) / 4 + 1;
		int[] cells = tag.getIntArray("cut").orElse(null);
		if (cells == null) {
			byte[] bytes = tag.getByteArray("cut").orElse(null);
			if (bytes != null) {
				cells = new int[bytes.length];
				for (int i = 0; i < bytes.length; i++) {
					cells[i] = bytes[i];
				}
			}
		}
		if (cells == null || width <= 0) {
			return;
		}
		int stride = Math.max(1, cells.length / 220);
		for (int i = 0; i < cells.length; i += stride) {
			int delta = cells[i];
			if (Math.abs(delta) < 2) {
				continue;
			}
			double x = fp[0] + (i % width) * 4 + 2.0D;
			double z = fp[2] + (i / width) * 4 + 2.0D;
			line(new Vec3(x, base, z), new Vec3(x, base + delta, z), argb(200, delta < 0 ? ORANGE : BLUE), 2.0F);
		}
	}

	private static void crosses(long[] cells, int color) {
		for (int i = 0; i < Math.min(256, cells.length); i++) {
			BlockPos pos = BlockPos.of(cells[i]);
			double y = pos.getY() + 1.02D;
			line(new Vec3(pos.getX(), y, pos.getZ()), new Vec3(pos.getX() + 1.0D, y, pos.getZ() + 1.0D), color, 3.0F);
			line(new Vec3(pos.getX() + 1.0D, y, pos.getZ()), new Vec3(pos.getX(), y, pos.getZ() + 1.0D), color, 3.0F);
		}
	}

	// ================================================================== the open component while building

	private static void drawOpen(Minecraft minecraft, CompoundTag status) {
		if (status.isEmpty() || minecraft.player == null || EffectQuality.minimal() && !status.getBooleanOr("own", false)) {
			return;
		}
		CompoundTag open = status.getCompoundOrEmpty("open");
		int[] box = box(open, "box");
		if (box == null) {
			return;
		}
		Vec3 center = new Vec3((box[0] + box[3] + 1) / 2.0D, (box[1] + box[4] + 1) / 2.0D, (box[2] + box[5] + 1) / 2.0D);
		if (minecraft.player.position().distanceToSqr(center) > OPEN_RANGE * OPEN_RANGE) {
			return;
		}
		float pulse = 0.6F + 0.4F * (float) Math.sin(Util.getMillis() / 700.0D);
		cuboid(new AABB(box[0], box[1], box[2], box[3] + 1.0D, box[4] + 1.0D, box[5] + 1.0D), argb(Math.round(90 * pulse), GOLD), 1.5F);
		Component name = open.read("name", net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(Component.empty());
		String label = Component.translatable("kultivator.sectbuild.holo.open", name, open.getIntOr("pg", 0)).getString();
		text(label, new Vec3(center.x, box[4] + 2.0D, center.z), argb(255, GOLD), 0.55F);
	}

	// ================================================================== gizmo helpers (counted against the budget)

	static @Nullable int[] box(CompoundTag tag, String key) {
		int[] box = tag.getIntArray(key).orElse(null);
		if (box == null) {
			return null;
		}
		if (box.length == 6) {
			return box;
		}
		if (box.length == 4) {
			return new int[]{box[0], 0, box[1], box[2], 0, box[3]};
		}
		return null;
	}

	private static boolean room() {
		return used < BUDGET;
	}

	private static void line(Vec3 from, Vec3 to, int argb, float width) {
		if (room()) {
			used++;
			Gizmos.line(from, to, argb, width);
		}
	}

	private static void rect(double x0, double z0, double x1, double z1, double y, int argb, float width) {
		line(new Vec3(x0, y, z0), new Vec3(x1, y, z0), argb, width);
		line(new Vec3(x1, y, z0), new Vec3(x1, y, z1), argb, width);
		line(new Vec3(x1, y, z1), new Vec3(x0, y, z1), argb, width);
		line(new Vec3(x0, y, z1), new Vec3(x0, y, z0), argb, width);
	}

	private static void cuboid(AABB box, int argb, float width) {
		if (room()) {
			used++;
			Gizmos.cuboid(box, GizmoStyle.stroke(argb, width));
		}
	}

	private static void text(String text, Vec3 pos, int argb, float scale) {
		if (room() && !text.isEmpty()) {
			used++;
			Gizmos.billboardText(text, pos, TextGizmo.Style.forColorAndCentered(argb).withScale(scale)).setAlwaysOnTop();
		}
	}

	private static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	private SiteHologram() {
	}
}
