package dev.kultivator.client.dunia.kota42.fx;

import dev.kultivator.client.dunia.kota42.KotaHidupClient;
import dev.kultivator.dunia.kota42.KotaPayloads;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The confetti of the running ceremonies of {@code kota_upacara} (DESIGN 42.7.7, 42.12.6), around the middle of the participants
 * that exist on this client within 60 blocks of the player: a wedding rains red paper and peach petals, a funeral scatters joss
 * paper and white scraps in front of the procession, a birth and a first-year ceremony burst into red paper and glints each time
 * the ceremony enters a new phase. Particle counts follow the tier (none beyond Minimal's small share). Client thread only.
 */
final class UpacaraFx {

	private static final Map<Integer, Integer> FASE = new HashMap<>();
	private static int detak;

	static void bersihkan() {
		FASE.clear();
	}

	static void tik() {
		var level = Bingkai.level;
		var pemain = Bingkai.pemain;
		if (level == null || pemain == null || ++detak % 4 != 0) {
			return;
		}
		RandomSource rnd = level.getRandom();
		float skala = Bingkai.partikel();
		for (KotaPayloads.Upacara u : KotaHidupClient.upacara()) {
			if (u.fase() < 0) {
				continue;
			}
			Vec3 c = TubuhFx.pusatUpacara(u);
			if (c == null || c.distanceToSqr(pemain.position()) > 60.0D * 60.0D) {
				continue;
			}
			Integer lama = FASE.put(u.id(), u.fase());
			boolean baru = lama == null || lama != u.fase();
			JenisUpacara jenis = JenisUpacara.byOrdinal(u.jenis());
			if (jenis == null) {
				continue;
			}
			switch (jenis) {
				case NIKAH -> {
					int n = Math.round(2.0F * skala);
					for (int i = 0; i < n; i++) {
						float x = (float) c.x + (rnd.nextFloat() - 0.5F) * 5.0F;
						float z = (float) c.z + (rnd.nextFloat() - 0.5F) * 5.0F;
						float y = (float) c.y + 2.4F + rnd.nextFloat() * 1.2F;
						if (rnd.nextBoolean()) {
							PartikelKota.kertasMerah(rnd, x, y, z, 0.0F, -0.005F, 0.0F);
						} else {
							PartikelKota.kelopak(rnd, x, y, z, rnd.nextInt(2), -0.006F);
						}
					}
				}
				case MAKAM -> {
					int n = Math.round(2.0F * skala);
					for (int i = 0; i < n; i++) {
						float x = (float) c.x + (rnd.nextFloat() - 0.5F) * 3.0F;
						float z = (float) c.z + (rnd.nextFloat() - 0.5F) * 3.0F;
						if (rnd.nextInt(3) == 0) {
							PartikelKota.kertasPutih(rnd, x, (float) c.y + 1.2F, z, (rnd.nextFloat() - 0.5F) * 0.05F, 0.07F + rnd.nextFloat() * 0.05F, (rnd.nextFloat() - 0.5F) * 0.05F);
						} else {
							PartikelKota.uang(rnd, x, (float) c.y + 1.2F, z, (rnd.nextFloat() - 0.5F) * 0.05F, 0.08F + rnd.nextFloat() * 0.05F, (rnd.nextFloat() - 0.5F) * 0.05F);
						}
					}
				}
				case LAHIR, SEBULAN, ZHUAZHOU -> {
					if (baru) {
						int n = Math.round(14.0F * skala);
						for (int i = 0; i < n; i++) {
							PartikelKota.kertasMerah(rnd, (float) c.x, (float) c.y + 0.8F, (float) c.z, (rnd.nextFloat() - 0.5F) * 0.22F, 0.08F + rnd.nextFloat() * 0.1F,
									(rnd.nextFloat() - 0.5F) * 0.22F);
						}
						for (int i = 0; i < Math.round(5.0F * skala); i++) {
							PartikelKota.kilau(rnd, (float) c.x + (rnd.nextFloat() - 0.5F) * 1.5F, (float) c.y + 1.0F + rnd.nextFloat(), (float) c.z + (rnd.nextFloat() - 0.5F) * 1.5F,
									0.16F, 14, 0xFFE9A8);
						}
					}
				}
			}
		}
		// forget ceremonies that ended
		FASE.keySet().removeIf(id -> KotaHidupClient.upacara().stream().noneMatch(k -> k.id() == id));
	}

	private UpacaraFx() {
	}
}
