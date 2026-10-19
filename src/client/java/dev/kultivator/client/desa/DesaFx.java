package dev.kultivator.client.desa;

import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.fx.CommonFx;
import dev.kultivator.desa.DesaFxIds;
import dev.kultivator.network.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Vassal village visuals (DESIGN 25.2 item 4; sub-ids in {@code desa.DesaFxIds}): the oath at the plaque (gold rings and a
 * rising seal for 2 s), the plaque's jade ring, a bandit camp's smoke column, the finished request's green motes and the
 * request / wave toasts. Every count goes through Effect Quality; at most 8 running animations.
 */
public final class DesaFx {

	private static final int GOLD = 0xFFD54A;
	private static final int JADE = 0x8FE3C8;
	private static final int SEAL = 0xD23A2A;
	private static final int MAX_ANIMATIONS = 8;
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(6000L);
	private static final List<Oath> OATHS = new ArrayList<>();

	private static final class Oath {
		final Vec3 pos;
		int age;

		Oath(Vec3 pos) {
			this.pos = pos;
		}
	}

	/** One {@code FxType.DESA} event (routed by {@code FxDispatcher}). */
	public static void play(Payloads.Fx fx) {
		if (CommonFx.level() == null) {
			return;
		}
		Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
		switch (fx.a()) {
			case DesaFxIds.OATH -> {
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), GOLD, 2.5F, 48);
				CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), JADE, 4.5F, 64);
				CommonFx.pillar(pos, GOLD, 5.0F);
				CommonFx.burst(pos.add(0.0D, 1.2D, 0.0D), SEAL, 24, 1.4F);
				if (OATHS.size() < MAX_ANIMATIONS) {
					OATHS.add(new Oath(pos));
				}
			}
			case DesaFxIds.PLAQUE -> CommonFx.ring(pos.add(0.0D, 0.1D, 0.0D), JADE, 1.6F, 28);
			case DesaFxIds.CAMP_SMOKE -> smoke(pos);
			case DesaFxIds.REQUEST_DONE -> {
				RandomSource random = CommonFx.random();
				int n = EffectQuality.scale(14);
				for (int i = 0; i < n; i++) {
					CommonFx.add(ParticleTypes.HAPPY_VILLAGER, pos.x + random.nextGaussian() * 0.6D, pos.y + 0.6D + random.nextDouble(),
							pos.z + random.nextGaussian() * 0.6D, 0.0D, 0.03D, 0.0D);
				}
			}
			case DesaFxIds.TOAST -> toast(fx.b());
			default -> {
			}
		}
	}

	/** A thin column of cosy campfire smoke over a camp (no block: particles only, visible from far away). */
	private static void smoke(Vec3 pos) {
		RandomSource random = CommonFx.random();
		int n = Math.max(2, EffectQuality.scale(5));
		for (int i = 0; i < n; i++) {
			CommonFx.add(ParticleTypes.CAMPFIRE_COSY_SMOKE, new Vec3(pos.x + random.nextGaussian() * 0.3D, pos.y + 0.2D + random.nextDouble() * 0.5D,
					pos.z + random.nextGaussian() * 0.3D), new Vec3(0.0D, 0.06D + random.nextDouble() * 0.02D, 0.0D), true);
		}
	}

	private static void toast(int b) {
		Minecraft minecraft = Minecraft.getInstance();
		Component title;
		Component body;
		if (b >= 10) {
			title = Component.translatable("kultivator.desa.toast.siege." + (b - 10));
			body = Component.translatable("kultivator.desa.toast.siege_body");
		} else {
			String[] types = {"binatang", "sarang", "wabah", "panen", "jaga"};
			title = Component.translatable("kultivator.desa.toast.request");
			body = Component.translatable("kultivator.desa.request." + types[Math.max(0, Math.min(types.length - 1, b))]);
		}
		SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST, title, body);
	}

	/** Every client tick: the oath seals rise and fade. */
	public static void tick() {
		if (OATHS.isEmpty()) {
			return;
		}
		if (CommonFx.level() == null) {
			OATHS.clear();
			return;
		}
		Iterator<Oath> iterator = OATHS.iterator();
		while (iterator.hasNext()) {
			Oath oath = iterator.next();
			oath.age++;
			if (oath.age % 4 == 0) {
				float t = oath.age / 40.0F;
				CommonFx.ring(oath.pos.add(0.0D, 0.2D + t * 3.0D, 0.0D), oath.age % 8 == 0 ? GOLD : SEAL, 1.2F + t, 20);
			}
			if (oath.age >= 40) {
				iterator.remove();
			}
		}
	}

	private DesaFx() {
	}
}
