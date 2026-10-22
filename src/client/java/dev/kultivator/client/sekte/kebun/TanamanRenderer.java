package dev.kultivator.client.sekte.kebun;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.anim.form.FormModels;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.sekte.kebun.TabelHasil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The spirit beds and the mine as the client sees them (DESIGN 36.2.10 "Visual", 36.3.9): the herbs are never blocks, the
 * server's {@code KebunPayloads.World} names each bed's anchor, seed and stage, and this draws, from
 * {@code LevelRenderEvents.COLLECT_SUBMITS} (camera-relative, never in the Iris shadow pass, no post chain):
 * <ul>
 * <li>each bed's herb as a FormModels piece per stage ({@code sekte_kebun_tunas}, {@code _muda}, then the herb's own
 * {@code _kuncup} and {@code _mekar}), turned per bed, swaying a little, growing in when its stage changes, lit by the
 * world's light at the bed; at most 12 beds a site and only within 32 blocks (LOD);</li>
 * <li>Normal and Maximal: a soft glow on the soil under a budding or ripe herb in its own colour, breathing; a ripe one adds
 * rising motes (Normal 2, Maximal 4) and, on Maximal, a faint ray of light;</li>
 * <li>the mine while miners are inside: a warm glow in the adit and gold dust drifting out of it (Normal 6, Maximal 12;
 * deeper levels shift toward Nüwa's five colours), within 48 blocks.</li>
 * </ul>
 * Minimal draws the herbs only. A site not refreshed for 45 s (the server refreshes every {@code sinkronDetik}) is dropped.
 */
final class TanamanRenderer {

	private static final Identifier GLOW_TEX = Kultivator.id("textures/sekte/kebun/glow.png");
	private static final Identifier DUST_TEX = Kultivator.id("textures/sekte/kebun/debu.png");
	private static final Identifier RAY_TEX = Kultivator.id("textures/sekte/kebun/sinar.png");

	static final double PLANT_RANGE = 32.0D;
	static final double MINE_RANGE = 48.0D;
	private static final double STALE_SECONDS = 45.0D;
	private static final int MAX_BEDS = 12;
	private static final float GROW_SECONDS = 1.4F;

	/** The pieces' short names in {@code TabelHasil.Benih} order. */
	private static final String[] HERBS = {"rumput", "ginseng", "es", "api", "jamur", "ilusi", "bambu", "buah", "fondasi"};

	/** One bed (client state). */
	static final class Bed {
		BlockPos pos;
		int herb = -1;
		int stage;
		float progress;
		double changedAt = -100.0D;
		float yaw;
		float phase;
	}

	/** The mine of a site (client state). */
	static final class Mine {
		BlockPos mouth;
		int level;
		int miners;
		BlockPos[] inside = new BlockPos[0];
	}

	/** One owner's hall: its beds and mine. */
	static final class Site {
		long hall;
		final List<Bed> beds = new ArrayList<>();
		Mine mine;
		double seenAt;
	}

	private static final Map<Long, Site> SITES = new HashMap<>();
	private static final Kuas GLOW = new Kuas(RenderTypes.entityTranslucentEmissive(GLOW_TEX));
	private static final Kuas DUST = new Kuas(RenderTypes.entityTranslucentEmissive(DUST_TEX));
	private static final Kuas RAY = new Kuas(RenderTypes.entityTranslucentEmissive(RAY_TEX));
	private static final Vector3f RIGHT = new Vector3f();
	private static final Vector3f UP = new Vector3f();
	private static boolean registered;

	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(TanamanRenderer::collect);
	}

	// ================================================================== state

	/** The server's world picture of one hall. */
	static void onWorld(CompoundTag data) {
		long hall = data.getLongOr("hall", 0L);
		double now = seconds();
		Site site = SITES.computeIfAbsent(hall, key -> {
			Site fresh = new Site();
			fresh.hall = key;
			return fresh;
		});
		site.seenAt = now;
		Map<Long, Bed> old = new HashMap<>();
		for (Bed bed : site.beds) {
			old.put(bed.pos.asLong(), bed);
		}
		site.beds.clear();
		for (Tag tag : data.getListOrEmpty("beds")) {
			if (!(tag instanceof CompoundTag b) || site.beds.size() >= MAX_BEDS) {
				continue;
			}
			long key = b.getLongOr("p", 0L);
			Bed bed = old.get(key);
			if (bed == null) {
				bed = new Bed();
				bed.pos = BlockPos.of(key);
				int hash = (int) (key ^ (key >>> 32)) * 0x9E3779B1;
				bed.yaw = Math.floorMod(hash, 360);
				bed.phase = (Math.floorMod(hash >>> 8, 628)) / 100.0F;
			}
			int herb = b.getIntOr("h", -1);
			int stage = b.getIntOr("s", 0);
			if (herb != bed.herb || stage != bed.stage) {
				bed.changedAt = old.containsKey(key) ? now : now - GROW_SECONDS;
			}
			bed.herb = herb;
			bed.stage = stage;
			bed.progress = b.getFloatOr("f", 0.0F);
			site.beds.add(bed);
		}
		if (data.get("mine") instanceof CompoundTag m) {
			Mine mine = new Mine();
			mine.mouth = BlockPos.of(m.getLongOr("p", 0L));
			mine.level = m.getIntOr("l", 1);
			mine.miners = m.getIntOr("n", 0);
			long[] inside = m.getLongArray("in").orElse(new long[0]);
			mine.inside = new BlockPos[inside.length];
			for (int i = 0; i < inside.length; i++) {
				mine.inside[i] = BlockPos.of(inside[i]);
			}
			site.mine = mine;
		} else {
			site.mine = null;
		}
	}

	static Iterable<Site> sites() {
		return SITES.values();
	}

	static void clear() {
		SITES.clear();
	}

	/** Drops sites the server stopped refreshing (the player walked away or the beds were cleared). */
	static void prune() {
		double now = seconds();
		Iterator<Site> it = SITES.values().iterator();
		while (it.hasNext()) {
			Site site = it.next();
			if (now - site.seenAt > STALE_SECONDS || now < site.seenAt - 1.0D) {
				it.remove();
			}
		}
	}

	static double seconds() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? 0.0D : (minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true)) / 20.0D;
	}

	/** The colour of seed {@code herb} (Benih order), a soft green when unknown. */
	static int colourOf(int herb) {
		TabelHasil.Benih[] all = TabelHasil.Benih.all();
		return herb >= 0 && herb < all.length ? all[herb].color() : 0x8CFFB0;
	}

	/** The piece of a stage (1 sprout, 2 young, 3 bud, 4 ripe), null for a bare bed. */
	static String piece(int herb, int stage) {
		if (stage <= 0 || herb < 0) {
			return null;
		}
		if (stage == 1) {
			return "sekte_kebun_tunas";
		}
		if (stage == 2) {
			return "sekte_kebun_muda";
		}
		String name = herb < HERBS.length ? HERBS[herb] : "fondasi";
		return "sekte_kebun_" + name + (stage >= 4 ? "_mekar" : "_kuncup");
	}

	/** The gold of the mine's dust at a depth level: pale gold near the surface, then rich gold, then the five colours. */
	static int dustColour(int level, int i) {
		if (level >= 7) {
			int[] five = {0x5AAAFF, 0xE8403A, 0xFFCE48, 0xF4F4EC, 0x4FB08E};
			return i % 3 == 0 ? five[i % five.length] : 0xFFD27A;
		}
		return level >= 4 ? 0xFFC447 : 0xFFE6A8;
	}

	// ================================================================== drawing

	private static void collect(LevelRenderContext context) {
		if (SITES.isEmpty() || ShaderCompat.shadowPass()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.gui.screen() instanceof KebunScreen) {
			return;
		}
		try {
			CameraRenderState camera = context.levelState().cameraRenderState;
			Vec3 cam = camera.pos;
			camera.orientation.transform(1.0F, 0.0F, 0.0F, RIGHT);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, UP);
			GLOW.reset();
			DUST.reset();
			RAY.reset();
			double now = seconds();
			EffectQuality.Level quality = EffectQuality.level();
			PoseStack poseStack = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			for (Site site : SITES.values()) {
				for (Bed bed : site.beds) {
					if (bed.stage > 0 && Vec3.atBottomCenterOf(bed.pos).distanceToSqr(cam) <= PLANT_RANGE * PLANT_RANGE) {
						plant(level, bed, cam, now, quality, poseStack, collector);
					}
				}
				Mine mine = site.mine;
				if (mine != null && mine.miners > 0 && quality != EffectQuality.Level.MINIMAL
						&& Vec3.atCenterOf(mine.mouth).distanceToSqr(cam) <= MINE_RANGE * MINE_RANGE) {
					mine(mine, cam, now, quality);
				}
			}
			GLOW.submit(collector, poseStack);
			RAY.submit(collector, poseStack);
			DUST.submit(collector.order(1), poseStack);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kebun herb render failed", exception);
			SITES.clear();
		}
	}

	private static void plant(ClientLevel level, Bed bed, Vec3 cam, double now, EffectQuality.Level quality, PoseStack poseStack,
			SubmitNodeCollector collector) {
		String piece = piece(bed.herb, bed.stage);
		if (piece == null) {
			return;
		}
		double bx = bed.pos.getX() + 0.5D - cam.x;
		double by = bed.pos.getY() - cam.y;
		double bz = bed.pos.getZ() + 0.5D - cam.z;
		float grow = (float) Mth.clamp((now - bed.changedAt) / GROW_SECONDS, 0.0D, 1.0D);
		float alpha = 0.55F + 0.45F * grow * grow * (3.0F - 2.0F * grow);
		float sway = Mth.sin((float) (now * 1.3D) + bed.phase) * (bed.stage >= 3 ? 2.6F : 1.6F);
		float swayX = Mth.cos((float) (now * 0.9D) + bed.phase * 1.7F) * 1.4F;
		int light = LightCoordsUtil.getLightCoords(level, bed.pos);
		poseStack.pushPose();
		poseStack.translate(bx, by, bz);
		poseStack.rotateDegrees(Axis.YP, bed.yaw);
		poseStack.rotateDegrees(Axis.ZP, sway);
		poseStack.rotateDegrees(Axis.XP, swayX);
		// the piece's centre (8, 8, 8) sits on the origin, so lift it half a block to put its y = 0 on the soil;
		// FormModels turns pieces by 180 degrees about z for model-part space, undone here for the world
		poseStack.translate(0.0F, 0.5F, 0.0F);
		poseStack.rotateDegrees(Axis.ZP, 180.0F);
		FormModels.submitPiece(piece, poseStack, collector, light, alpha);
		poseStack.popPose();
		if (quality == EffectQuality.Level.MINIMAL || bed.stage < 3) {
			return;
		}
		int rgb = colourOf(bed.herb);
		boolean ripe = bed.stage >= 4;
		float breath = 0.5F + 0.5F * Mth.sin((float) (now * (ripe ? 2.0D : 1.2D)) + bed.phase);
		float flash = 1.0F - grow;
		// the glow on the soil, two layers: a wide faint one and a tight bright one
		float wide = (ripe ? 0.3F : 0.16F) + 0.08F * breath + 0.35F * flash;
		GLOW.ground(bx, by + 0.02D, bz, ripe ? 1.05F : 0.8F, ARGB.color(alpha(wide), rgb));
		if (ripe) {
			GLOW.ground(bx, by + 0.03D, bz, 0.45F, ARGB.color(alpha(0.35F + 0.2F * breath), 0xFFFFFF));
			// a halo round the bloom itself
			GLOW.billboard(bx, by + 0.55D, bz, 0.42F + 0.05F * breath, ARGB.color(alpha(0.16F + 0.1F * breath + 0.3F * flash), rgb), RIGHT, UP);
			int motes = quality == EffectQuality.Level.MAXIMAL ? 4 : 2;
			for (int i = 0; i < motes; i++) {
				double t = (now * 0.32D + i / (double) motes + bed.phase) % 1.0D;
				double angle = bed.phase * 3.0D + i * 2.4D + t * 2.0D;
				double r = 0.18D + 0.22D * t;
				float fade = (float) Math.min(1.0D, Math.min(t * 5.0D, (1.0D - t) * 2.5D));
				DUST.billboard(bx + Math.cos(angle) * r, by + 0.25D + t * 1.4D, bz + Math.sin(angle) * r, 0.07F + 0.03F * (1.0F - (float) t),
						ARGB.color(alpha(0.9F * fade), i % 2 == 0 ? rgb : 0xFFFFFF), RIGHT, UP);
			}
			if (quality == EffectQuality.Level.MAXIMAL) {
				RAY.ray(bx, by + 0.05D, bz, 0.22F, 1.7F + 0.2F * breath, ARGB.color(alpha(0.18F + 0.08F * breath + 0.3F * flash), rgb), RIGHT);
			}
		}
	}

	private static void mine(Mine mine, Vec3 cam, double now, EffectQuality.Level quality) {
		BlockPos adit = mine.inside.length > 0 ? mine.inside[0] : mine.mouth;
		double ox = mine.mouth.getX() - adit.getX();
		double oz = mine.mouth.getZ() - adit.getZ();
		double len = Math.sqrt(ox * ox + oz * oz);
		if (len < 1.0E-3D) {
			ox = 0.0D;
			oz = 1.0D;
		} else {
			ox /= len;
			oz /= len;
		}
		double ax = adit.getX() + 0.5D - cam.x;
		double ay = adit.getY() - cam.y;
		double az = adit.getZ() + 0.5D - cam.z;
		float flicker = 0.75F + 0.15F * Mth.sin((float) (now * 7.0D)) + 0.1F * Mth.sin((float) (now * 13.0D + 1.3D));
		// the warm glow of the lamps deep in the adit, seen through the opening
		GLOW.billboard(ax + ox * 0.6D, ay + 1.0D, az + oz * 0.6D, 0.95F, ARGB.color(alpha(0.28F * flicker), 0xFFB860), RIGHT, UP);
		GLOW.billboard(ax + ox * 0.9D, ay + 0.9D, az + oz * 0.9D, 0.5F, ARGB.color(alpha(0.22F * flicker), 0xFFE2A0), RIGHT, UP);
		int count = quality == EffectQuality.Level.MAXIMAL ? 12 : 6;
		count = Math.min(count, 2 + mine.miners * 2 + (quality == EffectQuality.Level.MAXIMAL ? 2 : 0));
		double sideX = -oz;
		double sideZ = ox;
		for (int i = 0; i < count; i++) {
			double t = (now * 0.18D + i / (double) count + i * 0.137D) % 1.0D;
			double drift = t * 3.2D;
			double wobble = Math.sin(now * 1.1D + i * 1.9D) * 0.35D * t;
			double x = ax + ox * (0.2D + drift) + sideX * (wobble + ((i * 37) % 7 - 3) * 0.08D);
			double z = az + oz * (0.2D + drift) + sideZ * (wobble + ((i * 37) % 7 - 3) * 0.08D);
			double y = ay + 0.5D + ((i * 53) % 9) * 0.1D + t * 1.0D + Math.sin(now * 2.0D + i) * 0.06D;
			float fade = (float) Math.min(1.0D, Math.min(t * 6.0D, (1.0D - t) * 2.2D));
			float twinkle = 0.7F + 0.3F * Mth.sin((float) (now * 9.0D + i * 2.1D));
			DUST.billboard(x, y, z, 0.05F + 0.03F * twinkle, ARGB.color(alpha(0.85F * fade * twinkle), dustColour(mine.level, i)), RIGHT, UP);
		}
	}

	private static int alpha(float a) {
		return Mth.clamp(Math.round(a * 255.0F), 0, 255);
	}

	private TanamanRenderer() {
	}
}
