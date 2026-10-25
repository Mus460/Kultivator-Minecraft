package dev.kultivator.client.dunia.kota42.ragam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.dunia.kota42.KotaPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client of 42S (DESIGN 42.4A.8), called once from {@code KotaHidupClient.init}: the op debug layer of
 * {@code uji kota_hidup sebaran peta}. Every 16 x 16 cell of {@link KotaHidupClient#petaPanas()} within 96 blocks is a flat
 * translucent tile on the ground (heightmap, kept within 10 blocks of the player's feet) with a darker rim, coloured by
 * occupancy / target: blue below half, green 0.5-1.25, yellow up to the cap (1.5), red above it, purple where people stand
 * without a target. The action bar names the cell the player stands in. The server sends the map at 1 Hz; a map older than
 * three seconds (the command was switched off, the place went quiet) is not drawn. Never in the shadow pass; costs nothing
 * while off.
 */
public final class PetaPanasDebug {

	private static final double JARAK = 96.0D;
	private static final long BASI_MS = 3000L;

	private static boolean initialised;
	private static KotaPayloads.@Nullable PetaPanas dilihat;
	private static long dilihatMs;
	private static int pesanTick;

	/** One tile to draw (camera-relative corner, size, colours). */
	private record Ubin(float x, float y, float z, float sisi, int isi, int tepi) {
	}

	private static final List<Ubin> UBIN = new ArrayList<>();

	/** Idempotent; client thread. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(PetaPanasDebug::collect);
		ClientTickEvents.END_CLIENT_TICK.register(PetaPanasDebug::tick);
	}

	/** The live map, or null when off or stale. */
	private static KotaPayloads.@Nullable PetaPanas peta() {
		KotaPayloads.PetaPanas map = KotaHidupClient.petaPanas();
		if (map == null || map.lebar() == 0 || map.tinggi() == 0) {
			dilihat = null;
			return null;
		}
		long nowMs = System.currentTimeMillis();
		if (map != dilihat) {
			dilihat = map;
			dilihatMs = nowMs;
		}
		return nowMs - dilihatMs > BASI_MS ? null : map;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		KotaPayloads.PetaPanas map = peta();
		if (player == null || map == null || ++pesanTick < 20) {
			return;
		}
		pesanTick = 0;
		int cx = (int) Math.floor((player.getX() - map.asalX()) / 16.0D);
		int cz = (int) Math.floor((player.getZ() - map.asalZ()) / 16.0D);
		int occ = 0;
		int target = 0;
		if (cx >= 0 && cz >= 0 && cx < map.lebar() && cz < map.tinggi()) {
			int i = cx + cz * map.lebar();
			occ = i < map.hunian().length ? map.hunian()[i] & 0xFF : 0;
			target = i < map.target().length ? map.target()[i] & 0xFF : 0;
		}
		player.sendOverlayMessage(Component.translatable("kultivator.kota42.ragam.debug.sumbu", map.lebar(), map.tinggi(), String.format("%.2f", occ / 4.0D),
				String.format("%.2f", target / 4.0D)));
	}

	/** The tile colour (ARGB fill) of one cell. */
	static int warna(int occ, int target) {
		if (target <= 0) {
			return 0x60B040E0;
		}
		double r = occ / (double) target;
		if (r < 0.5D) {
			return 0x603070FF;
		}
		if (r <= 1.25D) {
			return 0x6040D060;
		}
		if (r <= 1.5D) {
			return 0x60F0D030;
		}
		return 0x60F03030;
	}

	private static void collect(LevelRenderContext context) {
		KotaPayloads.PetaPanas map = peta();
		if (map == null || ShaderCompat.shadowPass()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			return;
		}
		Vec3 cam = context.levelState().cameraRenderState.pos;
		UBIN.clear();
		int feet = player.blockPosition().getY();
		for (int cz = 0; cz < map.tinggi(); cz++) {
			for (int cx = 0; cx < map.lebar(); cx++) {
				int i = cx + cz * map.lebar();
				int occ = i < map.hunian().length ? map.hunian()[i] & 0xFF : 0;
				int target = i < map.target().length ? map.target()[i] & 0xFF : 0;
				if (occ == 0 && target == 0) {
					continue;
				}
				int x0 = map.asalX() + cx * 16;
				int z0 = map.asalZ() + cz * 16;
				double mx = x0 + 8.0D - player.getX();
				double mz = z0 + 8.0D - player.getZ();
				if (mx * mx + mz * mz > JARAK * JARAK) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0 + 8, z0 + 8);
				if (Math.abs(y - feet) > 10) {
					y = feet;
				}
				int fill = warna(occ, target);
				int rim = fill & 0x00FFFFFF | 0xD0000000;
				UBIN.add(new Ubin((float) (x0 + 0.5D - cam.x), (float) (y + 0.06D - cam.y), (float) (z0 + 0.5D - cam.z), 15.0F, fill, rim));
			}
		}
		if (UBIN.isEmpty()) {
			return;
		}
		PoseStack poseStack = context.poseStack();
		List<Ubin> tiles = List.copyOf(UBIN);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
			for (Ubin u : tiles) {
				quad(pose, buffer, u.x(), u.y(), u.z(), u.sisi(), u.sisi(), u.isi());
				float w = 0.25F;
				quad(pose, buffer, u.x(), u.y() + 0.01F, u.z(), u.sisi(), w, u.tepi());
				quad(pose, buffer, u.x(), u.y() + 0.01F, u.z() + u.sisi() - w, u.sisi(), w, u.tepi());
				quad(pose, buffer, u.x(), u.y() + 0.01F, u.z(), w, u.sisi(), u.tepi());
				quad(pose, buffer, u.x() + u.sisi() - w, u.y() + 0.01F, u.z(), w, u.sisi(), u.tepi());
			}
		});
	}

	/** A horizontal quad with corner (x, y, z) and size dx by dz. */
	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float dx, float dz, int argb) {
		buffer.addVertex(pose, x, y, z).setColor(argb);
		buffer.addVertex(pose, x, y, z + dz).setColor(argb);
		buffer.addVertex(pose, x + dx, y, z + dz).setColor(argb);
		buffer.addVertex(pose, x + dx, y, z).setColor(argb);
	}

	private PetaPanasDebug() {
	}
}
