package dev.kultivator.client.sosok.gerak;

import dev.kultivator.client.disciple.DiscipleRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The head's gaze (DESIGN 40.5.9): targets by priority - the server's {@code DATA_LOOK_AT}, the master (a disciple
 * standing idle within 8 blocks of its master looks up now and then; loyal ones twice as often, sly ones only glance
 * with the eyes, the demonic path looks away), a player who comes within 6 blocks (2 seconds, once per 30 seconds per
 * player) or just arrived ({@link GerakReaksi}), else the walking direction. The turn is added on top of the server's
 * head rotation and clamped so the sum stays within yaw +-55 and pitch +-30 degrees; past 40 degrees the body twists
 * with it; the head moves at most 8 degrees a tick; what the head cannot reach the eyes do ({@code lirik} strips).
 */
final class Tatap {

	private static final float BATAS_YAW = 55.0F;
	private static final float BATAS_PITCH = 30.0F;
	private static final float LAJU = 8.0F;

	/** Fills {@code k.tatapYaw/Pitch/Twist} (degrees) and the glance in {@code d}. */
	static void hitung(LivingEntity e, DiscipleRenderState st, GerakKeadaan k, GerakData d, float age, float dt, int lookAt,
			@Nullable UUID guru, int sifatBit, boolean jalanIblis, boolean aktif) {
		if (!aktif) {
			ease(k, 0.0F, 0.0F, dt);
			d.lirikX = 0.0F;
			d.lirikY = 0.0F;
			k.tatapTwist = 0.0F;
			return;
		}
		Entity sasaran = null;
		boolean hanyaMata = false;
		boolean membalik = false;
		if (lookAt >= 0) {
			sasaran = e.level().getEntity(lookAt);
		}
		boolean diam = st.walkAnimationSpeed <= 0.15F;
		if (sasaran == null && guru != null && diam) {
			Player p = e.level().getPlayerByUUID(guru);
			if (p != null && p.distanceToSqr(e) <= 64.0D) {
				// every 200 ticks the disciple decides whether to look at the master for a while
				int slot = (int) (age / 200.0F);
				int peluang = (sifatBit & 1 << dev.kultivator.sekte.api.Sifat.SETIA.ordinal()) != 0 ? 60 : 30;
				long h = (slot * 0x9E3779B97F4A7C15L) ^ k.benih;
				h ^= h >>> 29;
				boolean kini = Math.floorMod(h, 100L) < peluang && (age % 200.0F) < 60.0F + Math.floorMod(h >>> 8, 60L);
				if (kini) {
					sasaran = p;
					hanyaMata = (sifatBit & 1 << dev.kultivator.sekte.api.Sifat.LICIK.ordinal()) != 0;
					membalik = jalanIblis;
				}
			}
		}
		if (sasaran == null && k.pemainLirik >= 0 && age < k.pemainLirikSampai) {
			sasaran = e.level().getEntity(k.pemainLirik);
		}
		if (sasaran == null && diam) {
			sasaran = pemainDekat(e, k, age);
		}
		if (sasaran == null) {
			ease(k, 0.0F, 0.0F, dt);
			lirikCepat(k, d, age, sifatBit);
			k.tatapTwist = 0.0F;
			return;
		}
		double dx = sasaran.getX() - e.getX();
		double dz = sasaran.getZ() - e.getZ();
		double dy = sasaran.getEyeY() - e.getEyeY();
		float yawDunia = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float relatif = Mth.wrapDegrees(yawDunia - st.bodyRot);
		if (membalik) {
			relatif = Mth.wrapDegrees(relatif + 150.0F);
		}
		float pitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		float yawMau = Mth.clamp(relatif, -BATAS_YAW, BATAS_YAW) - st.yRot;
		float pitchMau = Mth.clamp(pitch, -BATAS_PITCH, BATAS_PITCH) - st.xRot;
		if (hanyaMata) {
			yawMau = 0.0F;
			pitchMau = 0.0F;
		}
		ease(k, yawMau, pitchMau, dt);
		float total = st.yRot + k.tatapYaw;
		k.tatapTwist = Math.abs(total) > 40.0F ? (Math.abs(total) - 40.0F) * 0.5F * Math.signum(total) : 0.0F;
		float sisaYaw = relatif - total;
		float sisaPitch = pitch - (st.xRot + k.tatapPitch);
		d.lirikX = Math.abs(sisaYaw) > 10.0F || hanyaMata ? Wajah.lirik(sisaYaw) : 0.0F;
		d.lirikY = Math.abs(sisaPitch) > 10.0F ? -Wajah.lirik(sisaPitch) : 0.0F;
	}

	private static void ease(GerakKeadaan k, float yaw, float pitch, float dt) {
		float max = LAJU * Math.max(0.0F, Math.min(dt, 4.0F));
		k.tatapYaw += Mth.clamp((yaw - k.tatapYaw) * 0.35F, -max, max);
		k.tatapPitch += Mth.clamp((pitch - k.tatapPitch) * 0.35F, -max, max);
	}

	/** A player within 6 blocks not looked at in the last 30 seconds: looked at for 2 seconds. */
	private static @Nullable Entity pemainDekat(LivingEntity e, GerakKeadaan k, float age) {
		Player p = e.level().getNearestPlayer(e, 6.0D);
		if (p == null) {
			return null;
		}
		int pid = p.getId();
		for (int i = 0; i < k.pemainTerakhir.length; i++) {
			if (k.pemainTerakhir[i] == pid) {
				if (age - k.pemainTerakhirWaktu[i] < 600.0F && age - k.pemainTerakhirWaktu[i] > 40.0F) {
					return null;
				}
				if (age - k.pemainTerakhirWaktu[i] <= 40.0F) {
					return p;
				}
				k.pemainTerakhirWaktu[i] = age;
				return p;
			}
		}
		int slot = Math.floorMod((int) age, k.pemainTerakhir.length);
		k.pemainTerakhir[slot] = pid;
		k.pemainTerakhirWaktu[slot] = age;
		return p;
	}

	/** Sly and timid bodies glance sideways every 60-120 ticks for half a second. */
	private static void lirikCepat(GerakKeadaan k, GerakData d, float age, int sifatBit) {
		boolean licik = (sifatBit & 1 << dev.kultivator.sekte.api.Sifat.LICIK.ordinal()) != 0;
		boolean penakut = (sifatBit & 1 << dev.kultivator.sekte.api.Sifat.PENAKUT.ordinal()) != 0;
		d.lirikX = 0.0F;
		d.lirikY = 0.0F;
		if (!licik && !penakut) {
			return;
		}
		if (age >= k.lirikCepatBerikut) {
			k.lirikCepatSampai = age + 10.0F;
			k.lirikCepatArah = ((int) age + k.benih & 1) == 0 ? 1.0F : -1.0F;
			k.lirikCepatBerikut = age + 60.0F + Math.floorMod((int) age * 31 + k.benih, 60);
		}
		if (age < k.lirikCepatSampai) {
			d.lirikX = k.lirikCepatArah;
		}
	}

	private Tatap() {
	}
}
