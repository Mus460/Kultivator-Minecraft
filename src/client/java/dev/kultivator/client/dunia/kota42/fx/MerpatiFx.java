package dev.kultivator.client.dunia.kota42.fx;

import dev.kultivator.client.screen.SettingsRows;
import dev.kultivator.dunia.kota42.hewan.KawananMerpatiEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

/**
 * The hum of whistles on the pigeons' tails (DESIGN 42.12.6 {@code merpati_peluit}): a flock that takes off or circles
 * ({@code KawananMerpatiEntity.keadaan}) within 40 blocks of the player hums every two to three seconds from where it flies, a
 * little louder the closer it is. The flock itself is drawn by 42V-H's renderer; this class only adds the sound, behind the
 * switch {@code kota42.merpati}. Client thread only.
 */
final class MerpatiFx {

	private static final Map<Integer, Integer> SAMPAI = new HashMap<>();
	private static int detak;

	static void bersihkan() {
		SAMPAI.clear();
	}

	static void tik() {
		var level = Bingkai.level;
		var pemain = Bingkai.pemain;
		if (level == null || pemain == null || ++detak % 10 != 0 || !SettingsRows.kota42("kota42.merpati")) {
			return;
		}
		RandomSource rnd = level.getRandom();
		var p = pemain.position();
		for (KawananMerpatiEntity k : level.getEntitiesOfClass(KawananMerpatiEntity.class, new AABB(p, p).inflate(40.0D, 30.0D, 40.0D), KawananMerpatiEntity::isAlive)) {
			if (k.keadaan() == KawananMerpatiEntity.KEADAAN_MENDARAT) {
				continue;
			}
			int sampai = SAMPAI.getOrDefault(k.getId(), 0);
			if (Bingkai.waktu < sampai) {
				continue;
			}
			SAMPAI.put(k.getId(), (int) Bingkai.waktu + 46 + rnd.nextInt(30));
			double d = Math.sqrt(k.distanceToSqr(pemain));
			float v = (float) Math.max(0.2D, 0.9D - d / 55.0D);
			SuaraKota42.suara(level, k.getX(), k.getY() + 0.5D, k.getZ(), SuaraKota42.acara("kota42.merpati_peluit"), v, 0.92F + rnd.nextFloat() * 0.16F);
		}
		SAMPAI.keySet().removeIf(id -> level.getEntity(id) == null);
	}

	private MerpatiFx() {
	}
}
