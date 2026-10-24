package dev.kultivator.client.dunia.tempat.hidup;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import dev.kultivator.client.sosok.SosokRender;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.hidup.PendudukSnapshot;
import dev.kultivator.sosok.Sosok;
import dev.kultivator.sosok.SosokGen;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The far crowd of DESIGN 38.N.6 (L2, 48-128 blocks): the residents the server lists in {@link PendudukSnapshot} drawn as
 * light dolls ({@link BonekaImpostor}) wearing the same Sosok fallback atlas their body would wear (40.7.4), walking
 * smoothly from one snapshot to the next, standing, sitting, working or cheering by pose family; plus the seated,
 * cheering stands of an arena or a festival square ({@link #tribun}, the design's "penonton"; named apart from the sect
 * tournament's {@code client/sekte/turnamen/PenontonRenderer}).
 *
 * <p>Budget (weak laptops): Minimal draws nothing; Normal 32 dolls (8 boxes) and 64 seats; Maximal 48 dolls (12 boxes)
 * and 160 seats; behind the camera nothing is written, the last 8 blocks fade out (120-128), everything is batched into
 * one submit per atlas, the CPU time is charged to {@link EpicBudget}, nothing draws in a shader pack's shadow pass and no
 * post chain is used (Sodium and Iris safe). Snapshots older than 10 s are dropped.
 */
final class ImpostorRenderer {

	private static final int BASI = 200;
	private static final double DEKAT = 44.0D;
	private static final double JAUH = 128.0D;
	private static final double PUDAR = 120.0D;

	/** One far resident on the client. */
	private static final class Orang {
		final int look;
		final NpcRole peran;
		final Sosok sosok;
		final @Nullable Identifier atlas;
		final float fase;
		double x;
		double y;
		double z;
		double tx;
		double ty;
		double tz;
		float yaw;
		float tyaw;
		int pose;
		boolean jalan;

		Orang(int look, NpcRole peran) {
			this.look = look;
			this.peran = peran;
			this.sosok = SosokGen.untukNpc(peran, look);
			this.atlas = SosokRender.cadangan(sosok.kelamin(), sosok.usia(), SosokRender.kelasCadangan(peran.id()));
			this.fase = (look & 0xFF) * 0.37F;
		}
	}

	/** One stand seat. */
	private record Kursi(double x, double y, double z, float yaw, Sosok sosok, @Nullable Identifier atlas, float fase) {
	}

	private static final class Kerumunan {
		final List<Orang> orang = new ArrayList<>();
		final List<Kursi> tribun = new ArrayList<>();
		long diperbarui;
	}

	private static final Map<Long, Kerumunan> TEMPAT = new HashMap<>();
	private static final Map<Identifier, BonekaImpostor.Sink> SINKS = new HashMap<>();
	private static long ticks;
	private static @Nullable ClientLevel lastLevel;
	private static boolean warned;

	private ImpostorRenderer() {
	}

	// ================================================================== network

	/** A new snapshot of one place (an empty one clears it). */
	static void terima(PendudukSnapshot snap) {
		if (snap.orang().isEmpty() && snap.tribun().isEmpty()) {
			TEMPAT.remove(snap.situs());
			return;
		}
		Kerumunan k = TEMPAT.computeIfAbsent(snap.situs(), id -> new Kerumunan());
		k.diperbarui = ticks;
		// keep the dolls already shown (matched by look and role) so they walk on instead of popping
		Map<Long, Orang> lama = new HashMap<>();
		for (Orang o : k.orang) {
			lama.put((long) o.look << 16 | o.peran.ordinal(), o);
		}
		List<Orang> baru = new ArrayList<>(snap.orang().size());
		for (PendudukSnapshot.Entri e : snap.orang()) {
			NpcRole role = NpcRole.byOrdinal(e.peran());
			Orang o = lama.remove((long) e.look() << 16 | role.ordinal());
			double x = snap.ax() + e.dx() / 2.0D + 0.5D;
			double y = snap.ay() + e.dy() / 2.0D;
			double z = snap.az() + e.dz() / 2.0D + 0.5D;
			float yaw = (e.yaw() & 0xFF) * 360.0F / 256.0F;
			if (o == null) {
				o = new Orang(e.look(), role);
				o.x = x;
				o.y = y;
				o.z = z;
				o.yaw = yaw;
			}
			o.tx = x;
			o.ty = y;
			o.tz = z;
			o.tyaw = yaw;
			o.pose = e.klip();
			o.jalan = e.klip() == PendudukSnapshot.KLIP_JALAN;
			baru.add(o);
		}
		k.orang.clear();
		k.orang.addAll(baru);
		k.tribun.clear();
		for (PendudukSnapshot.Tribun t : snap.tribun()) {
			int seed = (int) PendudukHash.campur(snap.situs() ^ ((long) t.dx() << 32) ^ ((long) t.dy() << 16) ^ t.dz());
			Sosok sosok = SosokGen.untukNpc((seed & 3) == 0 ? NpcRole.ANAK : (seed & 7) == 1 ? NpcRole.TETUA_DESA : NpcRole.WARGA, seed);
			Identifier atlas = SosokRender.cadangan(sosok.kelamin(), sosok.usia(), (seed >>> 5 & 3) == 0 ? "kultivator" : "sipil");
			k.tribun.add(new Kursi(snap.ax() + t.dx() + 0.5D, snap.ay() + t.dy(), snap.az() + t.dz() + 0.5D, (t.yaw() & 0xFF) * 360.0F / 256.0F,
					sosok, atlas, (seed & 0xFF) * 0.41F));
		}
	}

	static void bersihkan() {
		TEMPAT.clear();
	}

	// ================================================================== tick

	/** End of every client tick: dolls walk toward their last known spot, stale places drop. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level != lastLevel) {
			lastLevel = level;
			TEMPAT.clear();
		}
		if (level == null || minecraft.isPaused() || TEMPAT.isEmpty()) {
			return;
		}
		ticks++;
		for (Iterator<Kerumunan> it = TEMPAT.values().iterator(); it.hasNext(); ) {
			Kerumunan k = it.next();
			if (ticks - k.diperbarui > BASI) {
				it.remove();
				continue;
			}
			for (Orang o : k.orang) {
				double dx = o.tx - o.x;
				double dy = o.ty - o.y;
				double dz = o.tz - o.z;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > 24.0D) {
					o.x = o.tx;
					o.y = o.ty;
					o.z = o.tz;
					continue;
				}
				double step = 0.11D;
				if (d > step) {
					o.x += dx / d * step;
					o.z += dz / d * step;
					o.y += Mth.clamp(dy, -0.2D, 0.2D);
					o.yaw = Mth.rotLerp(0.25F, o.yaw, (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
				} else {
					o.x = o.tx;
					o.y = o.ty;
					o.z = o.tz;
					o.yaw = Mth.rotLerp(0.2F, o.yaw, o.tyaw);
				}
			}
		}
	}

	private static boolean berjalan(Orang o) {
		double dx = o.tx - o.x;
		double dz = o.tz - o.z;
		return o.jalan || dx * dx + dz * dz > 0.02D;
	}

	// ================================================================== frame

	/** Fabric {@code LevelRenderEvents.COLLECT_SUBMITS}. */
	static void collect(LevelRenderContext context) {
		if (TEMPAT.isEmpty() || ShaderCompat.shadowPass() || EffectQuality.minimal()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		long start = System.nanoTime();
		try {
			CameraRenderState camera = context.levelState().cameraRenderState;
			float partial = context.levelState().worldPartialTicks;
			Vec3 cam = camera.pos;
			Vector3fc fwd = minecraft.gameRenderer.mainCamera().forwardVector();
			boolean penuh = EffectQuality.maximal();
			int maksOrang = penuh ? PendudukSnapshot.MAKS : 32;
			int maksTribun = penuh ? PendudukSnapshot.MAKS_TRIBUN : 64;
			int rinci = penuh ? BonekaImpostor.RINCI_PENUH : BonekaImpostor.RINCI_SEDANG;
			if (!EpicBudget.softGeometry()) {
				maksOrang /= 2;
				maksTribun /= 2;
				rinci = BonekaImpostor.RINCI_DASAR;
			}
			for (BonekaImpostor.Sink sink : SINKS.values()) {
				sink.reset();
			}
			float t = ticks + partial;
			int drawn = 0;
			int seats = 0;
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (Kerumunan k : TEMPAT.values()) {
				for (Orang o : k.orang) {
					if (drawn >= maksOrang) {
						break;
					}
					if (o.atlas == null) {
						continue;
					}
					double dx = o.x - cam.x;
					double dy = o.y - cam.y;
					double dz = o.z - cam.z;
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (d < DEKAT || d > JAUH || !depan(fwd, dx, dy, dz, d)) {
						continue;
					}
					float alpha = d > PUDAR ? (float) ((JAUH - d) / (JAUH - PUDAR)) : 1.0F;
					int pose = berjalan(o) ? BonekaImpostor.JALAN : pose(o.pose);
					int light = cahaya(level, at.set(o.x, o.y + 1.0D, o.z));
					BonekaImpostor.gambar(sink(o.atlas), (float) dx, (float) dy, (float) dz, o.yaw, o.sosok.skala(), o.sosok.lenganRamping(), pose,
							t + o.fase, light, alpha, rinci);
					drawn++;
				}
				for (Kursi kursi : k.tribun) {
					if (seats >= maksTribun) {
						break;
					}
					if (kursi.atlas() == null) {
						continue;
					}
					double dx = kursi.x() - cam.x;
					double dy = kursi.y() - cam.y;
					double dz = kursi.z() - cam.z;
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (d > JAUH || !depan(fwd, dx, dy, dz, d)) {
						continue;
					}
					float alpha = d > PUDAR ? (float) ((JAUH - d) / (JAUH - PUDAR)) : 1.0F;
					// seated, and every few seconds a wave of cheering runs along the stand
					float wave = Mth.sin(t * 0.05F + kursi.fase());
					int pose = wave > 0.55F ? BonekaImpostor.SORAK : BonekaImpostor.DUDUK;
					int light = cahaya(level, at.set(kursi.x(), kursi.y() + 1.0D, kursi.z()));
					BonekaImpostor.gambar(sink(kursi.atlas()), (float) dx, (float) dy, (float) dz, kursi.yaw(), kursi.sosok().skala(),
							kursi.sosok().lenganRamping(), pose, t + kursi.fase(), light, alpha,
							d < 64.0D ? rinci : BonekaImpostor.RINCI_DASAR);
					seats++;
				}
			}
			PoseStack poseStack = context.poseStack();
			SubmitNodeCollector collector = context.submitNodeCollector();
			for (BonekaImpostor.Sink sink : SINKS.values()) {
				sink.submit(collector, poseStack);
			}
		} catch (RuntimeException exception) {
			if (!warned) {
				warned = true;
				Kultivator.LOGGER.warn("Far crowd skipped a frame", exception);
			}
		}
		EpicBudget.addCpu(System.nanoTime() - start);
	}

	private static int pose(int klip) {
		return switch (klip) {
			case PendudukSnapshot.KLIP_DUDUK -> BonekaImpostor.DUDUK;
			case PendudukSnapshot.KLIP_KERJA -> BonekaImpostor.KERJA;
			case PendudukSnapshot.KLIP_SORAK -> BonekaImpostor.SORAK;
			case PendudukSnapshot.KLIP_JALAN -> BonekaImpostor.JALAN;
			default -> BonekaImpostor.DIAM;
		};
	}

	/** Not behind the camera (a generous cone: wide FOV and the doll's own size). */
	private static boolean depan(Vector3fc fwd, double dx, double dy, double dz, double d) {
		double dot = (dx * fwd.x() + dy * fwd.y() + dz * fwd.z()) / Math.max(1.0E-4D, d);
		return dot > -0.15D;
	}

	private static int cahaya(ClientLevel level, BlockPos pos) {
		if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
			return LightCoordsUtil.pack(0, 15);
		}
		return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
	}

	private static BonekaImpostor.Sink sink(Identifier atlas) {
		return SINKS.computeIfAbsent(atlas, id -> new BonekaImpostor.Sink(RenderTypes.entityTranslucent(id)));
	}

	/**
	 * The far crowd as {@link RiuhKota} hears it: every doll within {@code r} blocks (horizontal) goes to {@code dengar}
	 * with its role and distance; returns the occupied stand seats within {@code r} (a cheering crowd).
	 */
	static int dengar(double x, double z, double r, java.util.function.ObjDoubleConsumer<NpcRole> dengar) {
		double r2 = r * r;
		int kursi = 0;
		for (Kerumunan k : TEMPAT.values()) {
			for (Orang o : k.orang) {
				double d2 = (o.x - x) * (o.x - x) + (o.z - z) * (o.z - z);
				if (d2 < r2) {
					dengar.accept(o.peran, Math.sqrt(d2));
				}
			}
			for (Kursi s : k.tribun) {
				if ((s.x() - x) * (s.x() - x) + (s.z() - z) * (s.z() - z) < r2) {
					kursi++;
				}
			}
		}
		return kursi;
	}

	/** Counts for {@code /sosok}-style debugging: places and dolls known. */
	static int[] hitung() {
		int orang = 0;
		int kursi = 0;
		for (Kerumunan k : TEMPAT.values()) {
			orang += k.orang.size();
			kursi += k.tribun.size();
		}
		return new int[]{TEMPAT.size(), orang, kursi};
	}

	/** A stable seat hash (no {@code Random}). */
	static final class PendudukHash {
		static long campur(long z) {
			z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
			z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
			return z ^ z >>> 31;
		}

		private PendudukHash() {
		}
	}
}
