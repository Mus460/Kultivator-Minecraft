package dev.kultivator.client.dunia.reruntuhan;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.ShaderCompat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What the pagoda of the Menara Seratus Lantai shows from outside (DESIGN 34.5.7), drawn from Fabric
 * {@code LevelRenderEvents.COLLECT_SUBMITS} with the module's glow and cloud sprites (rule V2):
 * <ul>
 * <li>the cloud ring that hides the top of the nine-tier pagoda ({@code op: "luar"}, every five seconds within 192
 * blocks): 32 / 96 / 160 puffs turning slowly round the crown in three layers, seen up to 256 blocks, thinner with
 * distance, and a soft gold halo on the spire;</li>
 * <li>the ranking steles ({@code op: "stele"}): the week's five best climbers, the best of all time and the viewer's own
 * best floor as floating text boards within 24 blocks.</li>
 * </ul>
 */
final class TowerSights {

	private static final int[] PUFFS = {32, 96, 160};
	private static final long KEEP = 160L;

	private record Pagoda(Vec3 base, int height, long until) {
	}

	private record Stele(Vec3 at, List<String> weekly, String best, int bestFloor, int mine, long until) {
	}

	private static final Map<Long, Pagoda> PAGODAS = new HashMap<>();
	private static final Map<Long, Stele> STELES = new HashMap<>();
	private static final RSink CLOUD = new RSink(RTex.CLOUD, 1024);
	private static final RSink HALO = new RSink(RTex.GLOW, 16);
	private static long ticks;

	static void onPagoda(CompoundTag tag) {
		Vec3 base = new Vec3(tag.getDoubleOr("x", 0.0D), tag.getDoubleOr("y", 0.0D), tag.getDoubleOr("z", 0.0D));
		long key = Mth.floor(base.x) * 31L + Mth.floor(base.z);
		if (PAGODAS.size() > 8 && !PAGODAS.containsKey(key)) {
			return;
		}
		PAGODAS.put(key, new Pagoda(base, Math.max(16, tag.getIntOr("h", 112)), ticks + KEEP));
	}

	static void onStele(CompoundTag tag) {
		Vec3 at = new Vec3(tag.getDoubleOr("x", 0.0D), tag.getDoubleOr("y", 0.0D), tag.getDoubleOr("z", 0.0D));
		List<String> weekly = new ArrayList<>();
		ListTag list = tag.getListOrEmpty("weekly");
		for (Tag t : list) {
			if (t instanceof StringTag s && weekly.size() < 5) {
				weekly.add(s.value());
			}
		}
		long key = tag.getLongOr("id", 0L);
		if (STELES.size() > 8 && !STELES.containsKey(key)) {
			return;
		}
		STELES.put(key, new Stele(at, weekly, tag.getStringOr("best", ""), tag.getIntOr("best_floor", 0), tag.getIntOr("mine", 0), ticks + KEEP));
	}

	static void tick() {
		ticks++;
		for (Iterator<Pagoda> it = PAGODAS.values().iterator(); it.hasNext(); ) {
			if (it.next().until < ticks) {
				it.remove();
			}
		}
		for (Iterator<Stele> it = STELES.values().iterator(); it.hasNext(); ) {
			if (it.next().until < ticks) {
				it.remove();
			}
		}
	}

	static void clear() {
		PAGODAS.clear();
		STELES.clear();
	}

	static void render(LevelRenderContext context) {
		if ((PAGODAS.isEmpty() && STELES.isEmpty()) || ShaderCompat.shadowPass()) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cam = camera.pos;
		float time = ticks + context.levelState().worldPartialTicks;
		PoseStack pose = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		if (!PAGODAS.isEmpty()) {
			Vector3f right = new Vector3f();
			Vector3f up = new Vector3f();
			camera.orientation.transform(1.0F, 0.0F, 0.0F, right);
			camera.orientation.transform(0.0F, 1.0F, 0.0F, up);
			CLOUD.reset();
			HALO.reset();
			for (Pagoda pagoda : PAGODAS.values()) {
				clouds(pagoda, cam, right, up, time);
			}
			CLOUD.submit(collector, pose);
			HALO.submit(collector, pose);
		}
		for (Stele stele : STELES.values()) {
			if (stele.at().distanceToSqr(cam) < 24.0D * 24.0D) {
				board(stele, cam, camera.orientation, pose, collector);
			}
		}
	}

	private static void clouds(Pagoda pagoda, Vec3 cam, Vector3f right, Vector3f up, float time) {
		Vec3 crown = pagoda.base().add(0.0D, pagoda.height() - 10.0D, 0.0D);
		double d = Math.sqrt(crown.distanceToSqr(cam));
		if (d > 256.0D) {
			return;
		}
		float pack = ShaderCompat.packOn() ? 0.6F : 1.0F;
		int puffs = Math.round(PUFFS[EffectQuality.level().ordinal()] * pack * (d > 128.0D ? 0.5F : 1.0F));
		float fade = d < 160.0D ? 1.0F : (float) (1.0D - (d - 160.0D) / 96.0D);
		float x = (float) (crown.x - cam.x);
		float y = (float) (crown.y - cam.y);
		float z = (float) (crown.z - cam.z);
		for (int i = 0; i < puffs; i++) {
			int layer = i % 3;
			float radius = 9.0F + layer * 5.0F + (i * 37 % 7) * 0.6F;
			float speed = 0.0016F + layer * 0.0007F;
			double a = i * 2.39996D + time * speed * (layer == 1 ? -1.0F : 1.0F);
			float px = x + (float) Math.cos(a) * radius;
			float pz = z + (float) Math.sin(a) * radius;
			float py = y + layer * 3.0F - 2.0F + 0.6F * Mth.sin(time * 0.01F + i);
			float size = 3.2F + layer * 0.8F + (i * 13 % 5) * 0.3F;
			int shade = layer == 0 ? 0xFFF4F0 : layer == 1 ? 0xF0ECF8 : 0xE8E4F4;
			CLOUD.billboard(px, py, pz, right, up, size, RSink.argb(0.82F * fade, shade));
		}
		// the spire's halo above the clouds
		float hy = (float) (pagoda.base().y + pagoda.height() + 2.0D - cam.y);
		HALO.billboard(x, hy, z, right, up, 5.0F + Mth.sin(time * 0.05F), RSink.argb(0.35F * fade, 0xFFE6A0));
		HALO.billboard(x, hy, z, right, up, 1.6F, RSink.argb(0.7F * fade, 0xFFFFFF));
	}

	private static void board(Stele stele, Vec3 cam, Quaternionf face, PoseStack pose, SubmitNodeCollector collector) {
		float x = (float) (stele.at().x - cam.x);
		float y = (float) (stele.at().y - cam.y);
		float z = (float) (stele.at().z - cam.z);
		float line = 0.3F;
		float top = y + 2.0F;
		RDraw.board(pose, collector, Component.translatable("kultivator.reruntuhan.menara.stele.title").withStyle(ChatFormatting.BOLD), x, top, z, face,
				0.38F, 0xFFFFD54A, 0x88000000);
		float yy = top - 0.42F;
		if (stele.weekly().isEmpty()) {
			RDraw.board(pose, collector, Component.translatable("kultivator.reruntuhan.menara.stele.empty"), x, yy, z, face, line, 0xFFB0B0B0, 0x66000000);
			yy -= 0.36F;
		}
		for (int i = 0; i < stele.weekly().size(); i++) {
			String entry = stele.weekly().get(i);
			int bar = entry.lastIndexOf('|');
			String name = bar < 0 ? entry : entry.substring(0, bar);
			String floor = bar < 0 ? "?" : entry.substring(bar + 1);
			int color = i == 0 ? 0xFFFFE6A0 : i == 1 ? 0xFFE0E6EE : i == 2 ? 0xFFE0A070 : 0xFFD0D0D0;
			RDraw.board(pose, collector, Component.translatable("kultivator.reruntuhan.menara.stele.row", i + 1, name, floor), x, yy, z, face, line, color,
					0x66000000);
			yy -= 0.36F;
		}
		if (!stele.best().isEmpty()) {
			RDraw.board(pose, collector, Component.translatable("kultivator.reruntuhan.menara.stele.best", stele.best(), stele.bestFloor()), x, yy - 0.06F, z,
					face, line, 0xFFC8A0FF, 0x66000000);
			yy -= 0.42F;
		}
		RDraw.board(pose, collector, Component.translatable("kultivator.reruntuhan.menara.stele.mine", stele.mine()), x, yy, z, face, line, 0xFF9FE8FF,
				0x66000000);
	}

	private TowerSights() {
	}
}
