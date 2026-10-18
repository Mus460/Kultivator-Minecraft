package dev.kultivator.client.goals;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.goals.Goals;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * {@code uji hud} (DESIGN 19.4): while {@code sessionExtra.hud_demo} lies in the future every phase-7 HUD element shows
 * sample values (the flying-sword gauge, enlightenment timer, cloud pips, toxicity, vein line, stars, daily dots, the
 * guide tracker, the tribulation HUD and the danger alert), so they can be checked without setting each one up.
 */
public final class HudDemo {

	/** True while the demo runs. */
	public static boolean active() {
		long end = ClientCultivation.data().sessionExtra().getLongOr(Goals.HUD_DEMO, 0L);
		return end > ClientCultivation.serverGameTime();
	}

	/** 0..1 how far the 10 s demo has run (sample values move with it). */
	public static float progress() {
		long end = ClientCultivation.data().sessionExtra().getLongOr(Goals.HUD_DEMO, 0L);
		long left = Math.max(0L, end - ClientCultivation.serverGameTime());
		return 1.0F - Math.min(1.0F, left / (float) Goals.HUD_DEMO_TICKS);
	}

	/** A sample {@code sessionExtra.trib}: strike 4 of 9, a ring at the feet, two guardians, the next strike dangerous. */
	public static CompoundTag sampleTribulation() {
		CompoundTag trib = new CompoundTag();
		Player player = Minecraft.getInstance().player;
		long now = ClientCultivation.serverGameTime();
		double x = player == null ? 0.0D : player.getX();
		double z = player == null ? 0.0D : player.getZ();
		trib.putInt("phase", 2);
		trib.putInt("done", 4);
		trib.putInt("total", 9);
		trib.putLong("next", now + 70L - (now % 70L));
		trib.putDouble("pct", progress() > 0.5F ? 1.2D : 0.36D);
		trib.putDouble("cx", x - 2.5D);
		trib.putDouble("cz", z + 1.5D);
		trib.putDouble("rx", x);
		trib.putDouble("rz", z);
		trib.putLong("rend", now + 30L - (now % 30L));
		trib.putDouble("tx", x + 3.0D);
		trib.putDouble("tz", z);
		trib.putInt("guards", 2);
		return trib;
	}

	private HudDemo() {
	}
}
