package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.tempat.tema.TemaNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;

/**
 * The Pelita Penunjuk Angin of the Mercusuar Pelita Angin (DESIGN 38.T.3.2, 38.T.3.5, 38.T.9.3): the lamp entity
 * {@link ApiSuarEntity} burns from 12500 to 23500 and all day while it thunders. Its look follows the town's chain state
 * {@code kota("pelabuhan", situs).pelita}: {@link #RETAK} (cracked lens, a dim yellow light: the "Pelita yang Padam" of
 * the chain), {@link #UTUH} (the new sea-salt glass lens of bab 3) and {@link #FAJAR} (the Mutiara Fajar of bab 7, a
 * blue-gold beam). The two beam quads are 38TV-F's ({@code SinarSuarFx}); this class sends them {@code TemaNet.CAHAYA}
 * {@code apa = suar} when the lamp lights or goes out and again every 30 s to the players near (for newcomers).
 * Nothing in the world changes: the "light" is drawn, never a block.
 */
public final class Suar {

	public static final int RETAK = ApiSuarEntity.VARIAN_RETAK;
	public static final int UTUH = ApiSuarEntity.VARIAN_UTUH;
	public static final int FAJAR = ApiSuarEntity.VARIAN_FAJAR;

	public static final long NYALA_DARI = 12500L;
	public static final long NYALA_SAMPAI = 23500L;

	/** Last time the beam packet went out per lamp (entity id -> game time). */
	private static final Map<Integer, Long> KIRIM = new HashMap<>();

	/** The lamp state of a town ({@link #RETAK} until bab 3 of the chain). */
	public static int pelita(MinecraftServer server, long situs) {
		return PelabuhanKota.kota(server, situs).getIntOr("pelita", RETAK);
	}

	/** Sets the lamp state of a town (quest bab 3 / bab 7, tests). */
	public static void setPelita(MinecraftServer server, long situs, int keadaan) {
		PelabuhanKota.kota(server, situs).putInt("pelita", Math.max(RETAK, Math.min(FAJAR, keadaan)));
		PelabuhanKota.ubah(server);
	}

	/** True while the lamp burns at this time of day / weather. */
	public static boolean menyala(ServerLevel level) {
		long t = PelabuhanKota.jam(level);
		return PelabuhanKota.badai(level) || t >= NYALA_DARI && t < NYALA_SAMPAI;
	}

	/** From the {@code pelabuhan/suar} anchor every 20 ticks. */
	static void atur(ServerLevel level, PelabuhanKota.Hidup kota, ApiSuarEntity suar) {
		int look = pelita(level.getServer(), kota.situs);
		suar.setVarian(look);
		boolean lit = menyala(level) || PelabuhanCerita.upacaraBerjalan(kota.situs, level.getGameTime());
		PelabuhanCerita.dekatLampu(level, suar);
		int was = suar.keadaan();
		suar.setKeadaan(lit ? ApiSuarEntity.KEADAAN_MENYALA : ApiSuarEntity.KEADAAN_PADAM);
		suar.setNilai(!lit ? 0.0F : switch (look) {
			case FAJAR -> 1.0F;
			case UTUH -> 0.85F;
			default -> 0.32F;
		});
		long now = level.getGameTime();
		Long last = KIRIM.get(suar.getId());
		if (was != suar.keadaan() || last == null || now - last >= 600L) {
			KIRIM.put(suar.getId(), now);
			CompoundTag data = new CompoundTag();
			data.putString("apa", "suar");
			data.putInt("x", suar.blockPosition().getX());
			data.putInt("y", suar.blockPosition().getY());
			data.putInt("z", suar.blockPosition().getZ());
			data.putLong("tick", now);
			data.putBoolean("nyala", lit);
			data.putInt("varian", look);
			TemaNet.kirimSekitar(level, suar.position(), 192.0D, TemaNet.CAHAYA, kota.situs, data);
			if (was != suar.keadaan() && lit) {
				level.playSound(null, suar.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 0.6F);
			}
		}
		if (KIRIM.size() > 64) {
			KIRIM.clear();
		}
	}

	private Suar() {
	}
}
