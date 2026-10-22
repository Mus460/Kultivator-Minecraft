package dev.kultivator.client.sekte.seragam;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.sekte.seragam.SeragamPayloads;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The emblem on the formations (DESIGN 36.2.8): a flat decal of the hall's emblem 0.03 blocks above the floor at the
 * Bagua disc's lodestone and at each Titik Formasi plot of the Formasi Sekte, inside a slowly turning gold ring that
 * breathes in the emblem's border colour (the ring is emissive and pulses faintly at night, the emblem takes the world's
 * light). Drawn within 64 blocks, at most {@code SeragamPayloads.MAX_DECALS} per hall; Minimal leaves the ring out.
 * Called from {@link BenderaRenderer#collect} so both share one pass and its guards.
 */
final class LambangFormasiRenderer {

	private static final Identifier RING = Kultivator.id("textures/sekte/seragam/decal_cincin.png");
	private static final double RANGE = 64.0D;
	private static final float LIFT = 0.03F;

	private static final Map<Identifier, SeragamQuads> EMBLEMS = new HashMap<>();
	private static final SeragamQuads RINGS = new SeragamQuads(RenderTypes.entityTranslucentEmissive(RING));

	static void collect(ClientLevel level, Vec3 cam, double time) {
		for (SeragamQuads quads : EMBLEMS.values()) {
			quads.reset();
		}
		RINGS.reset();
		String dim = level.dimension().identifier().toString();
		boolean ring = EffectQuality.level() != EffectQuality.Level.MINIMAL;
		for (LambangCache.Entry entry : LambangCache.live()) {
			if (entry.decals.isEmpty() || !dim.equals(entry.dim)) {
				continue;
			}
			Identifier texture = LambangAtlas.cloth(entry);
			if (texture == null) {
				continue;
			}
			SeragamQuads emblems = EMBLEMS.computeIfAbsent(texture, id -> new SeragamQuads(RenderTypes.entityTranslucent(id)));
			int border = entry.data.emblem().borderRgb();
			for (SeragamPayloads.Decal decal : entry.decals) {
				BlockPos pos = decal.pos();
				double x = pos.getX() + 0.5D;
				double y = pos.getY() + LIFT;
				double z = pos.getZ() + 0.5D;
				double dx = x - cam.x;
				double dy = y - cam.y;
				double dz = z - cam.z;
				if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) {
					continue;
				}
				float half = Math.max(0.5F, decal.size() * 0.5F - 0.05F);
				int light = BenderaRenderer.light(level, pos);
				float cx = (float) dx;
				float cy = (float) dy;
				float cz = (float) dz;
				// the emblem (top-right quarter of the slot texture), lying flat, its top toward north
				emblems.quad(new float[]{cx - half, cy, cz - half}, new float[]{cx - half, cy, cz + half}, new float[]{cx + half, cy, cz + half},
						new float[]{cx + half, cy, cz - half}, 0.5F, 0.0F, 1.0F, 0.5F, 0xE6FFFFFF, light, 0.0F, 1.0F, 0.0F);
				if (ring) {
					float r = half * 1.32F;
					double turn = time * 0.15D + pos.getX() * 0.1D;
					float c = (float) Math.cos(turn);
					float s = (float) Math.sin(turn);
					float pulse = 0.55F + 0.25F * (float) Math.sin(time * 1.3D + pos.getZ());
					int argb = Math.round(pulse * 255.0F) << 24 | border;
					float[][] k = {{-r, -r}, {-r, r}, {r, r}, {r, -r}};
					float[][] p = new float[4][];
					for (int i = 0; i < 4; i++) {
						p[i] = new float[]{cx + k[i][0] * c - k[i][1] * s, cy + 0.005F, cz + k[i][0] * s + k[i][1] * c};
					}
					RINGS.quad(p[0], p[1], p[2], p[3], 0.0F, 0.0F, 1.0F, 1.0F, argb, LightCoordsUtil.FULL_BRIGHT, 0.0F, 1.0F, 0.0F);
				}
			}
		}
	}

	static void submit(SubmitNodeCollector collector, PoseStack pose) {
		for (SeragamQuads quads : EMBLEMS.values()) {
			quads.submit(collector, pose);
		}
		RINGS.submit(collector, pose);
	}

	static void clear() {
		EMBLEMS.clear();
	}

	private LambangFormasiRenderer() {
	}
}
