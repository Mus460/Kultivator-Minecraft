package dev.kultivator.client.dunia.tempat.jalan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.tempat.ui.TeksDunia;
import dev.kultivator.client.tempat.ui.kit.TempatWarna;
import dev.kultivator.dunia.tempat.jalan.JalanNet;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The carved board over a milestone (DESIGN 38.C.6.3, 38.U.4), from {@link JalanNet.Papan}: the road's name in the
 * accent of the stone style, then the places both ways and the nearest rest post, each with its distance in li and an
 * arrow turned to where the viewer stands (the road's heading at the stela projected on the camera: "←", "→", "↑" ahead,
 * "↓" behind), and, close up, the stela's carved couplet in faint ink. Drawn with {@link TeksDunia} on the {@code batu}
 * panel (vanilla render types, polygon-offset text; no post chain, skipped in the Iris shadow pass), within its distance
 * and slot budget (12 boards, 24 blocks; 6 and 16 on Minimal) like every other world text. Boards are kept for at most
 * {@link #MAKS} stelae and forgotten {@link #LUPA} blocks away. Client code only.
 */
public final class JalanPapan {

	public static final int MAKS = 16;
	public static final double LUPA = 96.0D;
	/** Board line height in blocks (a little larger than shop boards: the stone is read from the road). */
	private static final float TINGGI_BARIS = 0.2F;

	private static final Map<BlockPos, JalanNet.Papan> PAPAN = new HashMap<>();
	private static final Vector3f KANAN = new Vector3f();
	private static final Vector3f DEPAN = new Vector3f();
	private static boolean siap;

	static void init() {
		if (siap) {
			return;
		}
		siap = true;
		LevelRenderEvents.COLLECT_SUBMITS.register(JalanPapan::render);
	}

	static void terima(JalanNet.Papan papan) {
		PAPAN.put(papan.at(), papan);
		if (PAPAN.size() > MAKS) {
			// drop the farthest board from the player
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				return;
			}
			Vec3 p = mc.player.position();
			BlockPos far = null;
			double farD = -1.0D;
			for (BlockPos at : PAPAN.keySet()) {
				double d = at.distToCenterSqr(p);
				if (d > farD) {
					farD = d;
					far = at;
				}
			}
			PAPAN.remove(far);
		}
	}

	static void lupakan() {
		PAPAN.clear();
	}

	private static void render(LevelRenderContext context) {
		if (PAPAN.isEmpty() || ShaderCompat.shadowPass()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		long start = System.nanoTime();
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cam = camera.pos;
		camera.orientation.transform(1.0F, 0.0F, 0.0F, KANAN);
		camera.orientation.transform(0.0F, 0.0F, -1.0F, DEPAN);
		PoseStack pose = context.poseStack();
		double lupa = LUPA * LUPA;
		Iterator<Map.Entry<BlockPos, JalanNet.Papan>> it = PAPAN.entrySet().iterator();
		while (it.hasNext()) {
			JalanNet.Papan papan = it.next().getValue();
			double x = papan.at().getX() + 0.5D;
			double y = papan.at().getY() + papan.tinggi();
			double z = papan.at().getZ() + 0.5D;
			double d2 = cam.distanceToSqr(x, y, z);
			if (d2 > lupa) {
				it.remove();
				continue;
			}
			if (!TeksDunia.izin(papan.at(), d2)) {
				continue;
			}
			List<Component> lines = baris(papan, TeksDunia.rinci(d2));
			BlockPos lightAt = papan.at().above(Math.max(1, (int) papan.tinggi()));
			int light = LightCoordsUtil.getLightCoords(level, lightAt);
			// carved letters keep a faint glow of their own so the stone is still read at dusk
			light = LightCoordsUtil.max(light, LightCoordsUtil.pack(5, 0));
			TeksDunia.papan(pose, context.submitNodeCollector(), camera, x - cam.x, y - cam.y, z - cam.z, lines, TempatWarna.Gaya.BATU,
					TINGGI_BARIS, light);
		}
		EpicBudget.addCpu(System.nanoTime() - start);
	}

	/** The board's lines for the current camera: title, one line per direction, the couplet when {@code rinci}. */
	private static List<Component> baris(JalanNet.Papan papan, boolean rinci) {
		List<Component> out = new ArrayList<>(papan.arah().size() + 2);
		out.add(papan.judul());
		if (papan.arah().isEmpty()) {
			out.add(Component.translatable("kultivator.jalan.papan.terkikis"));
		}
		for (JalanNet.Arah a : papan.arah()) {
			String li = String.format(Locale.ROOT, "%.1f", a.li());
			float dx = (float) Math.cos(a.sudut());
			float dz = (float) Math.sin(a.sudut());
			float kanan = dx * KANAN.x() + dz * KANAN.z();
			float depan = dx * DEPAN.x() + dz * DEPAN.z();
			String arah;
			if (Math.abs(kanan) >= Math.abs(depan) * 0.6F) {
				arah = kanan >= 0.0F ? "kanan" : "kiri";
			} else {
				arah = depan >= 0.0F ? "depan" : "belakang";
			}
			Component line = Component.translatable("kultivator.jalan.papan." + arah, a.nama(), li);
			out.add(a.pos() ? line.copy().withStyle(ChatFormatting.ITALIC) : line);
		}
		if (rinci) {
			out.add(papan.ukiran().copy().withStyle(ChatFormatting.ITALIC));
		}
		return out;
	}

	private JalanPapan() {
	}
}
