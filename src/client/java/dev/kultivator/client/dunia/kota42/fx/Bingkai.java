package dev.kultivator.client.dunia.kota42.fx;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.EpicBudget;
import dev.kultivator.client.epic.ShaderCompat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * What every town fx of 42V-F shares for one tick and one frame: the level and player, the clock (hour with fraction, world
 * day, the overworld clock tick 0-23999 where 0 = 06:00), the quality tier ({@link EpicBudget#tierOrMinimal}, 0 Minimal, 1
 * Normal, 2 Maximal) and its quad factor under a shader pack, the camera (position, billboard axes) and the light lookup.
 * {@link #tik} runs at the end of every client tick, {@link #bingkai} at the start of a level frame's collect. Client thread only.
 */
final class Bingkai {

	/** Tier 0 / 1 / 2 (never -1: town fx always show at least their Minimal form). */
	static int tier;
	static float paket = 1.0F;
	static @Nullable ClientLevel level;
	static @Nullable LocalPlayer pemain;
	static long waktu;
	static long jamTik;
	static double jam;
	static long hari;
	static float sebagian;
	static float waktuF;
	static Vec3 kamera = Vec3.ZERO;
	static final Vector3f KANAN = new Vector3f(1.0F, 0.0F, 0.0F);
	static final Vector3f ATAS = new Vector3f(0.0F, 1.0F, 0.0F);

	/** End of a client tick: clock and tier. */
	static boolean tik(Minecraft minecraft) {
		level = minecraft.level;
		pemain = minecraft.player;
		if (level == null || pemain == null) {
			return false;
		}
		waktu++;
		long clock = level.getOverworldClockTime();
		jamTik = Math.floorMod(clock, 24000L);
		hari = Math.floorDiv(clock, 24000L);
		jam = (jamTik / 1000L + 6L) % 24L + (jamTik % 1000L) / 1000.0D;
		tier = EpicBudget.tierOrMinimal();
		paket = ShaderCompat.packOn() ? EpicBudget.PACK_QUADS : 1.0F;
		return true;
	}

	/** Start of a level frame: camera, billboard axes, partial ticks. */
	static void bingkai(LevelRenderContext context) {
		CameraRenderState camera = context.levelState().cameraRenderState;
		kamera = camera.pos;
		camera.orientation.transform(1.0F, 0.0F, 0.0F, KANAN);
		camera.orientation.transform(0.0F, 1.0F, 0.0F, ATAS);
		sebagian = context.levelState().worldPartialTicks;
		waktuF = waktu + sebagian;
	}

	/** True when the clock is in {@code [dari, sampai)} hours, wrapping over midnight when {@code sampai < dari}. */
	static boolean antara(double dari, double sampai) {
		return dari <= sampai ? jam >= dari && jam < sampai : jam >= dari || jam < sampai;
	}

	/** Hours since {@code dari} o'clock (0-24). */
	static double sejak(double dari) {
		return Mth.positiveModulo(jam - dari, 24.0D);
	}

	/** The packed world light at a point (never below a dim glow so faraway quads never go black), or full bright without a level. */
	static int cahaya(double x, double y, double z) {
		ClientLevel l = level;
		if (l == null) {
			return LightCoordsUtil.FULL_BRIGHT;
		}
		return LightCoordsUtil.getLightCoords(l, BlockPos.containing(x, y, z));
	}

	/** The scale of a count of the Minimal / Normal / Maximal table by the tier. */
	static int hitung(int minimal, int normal, int maksimal) {
		return Math.round((tier == 0 ? minimal : tier == 1 ? normal : maksimal) * (tier == 0 ? 1.0F : paket));
	}

	/** Particle count factor of 42.12.6 (25 % / 100 % / 200 %). */
	static float partikel() {
		return tier == 0 ? 0.25F : tier == 1 ? 1.0F : 2.0F;
	}

	static boolean minimal() {
		return tier == 0 || EffectQuality.minimal();
	}

	private Bingkai() {
	}
}
