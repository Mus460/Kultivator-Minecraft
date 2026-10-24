package dev.kultivator.client.sosok.gerak;

import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Client-side reactions (DESIGN 40.5.4 layer 7): a lightning strike within 24 blocks makes everyone nearby jump
 * ({@code kaget} 18); a player who appears within 16 blocks is glanced at for 2 seconds; a hostile mob appearing within
 * 10 blocks startles the timid; other modules may ask for a reaction with {@link #picu} or report an explosion with
 * {@link #ledakan}. Reactions never play while a server clip (layers 4-6), cast or duel holds the body. Only reads
 * what the client already has (entity load events); the mod never creates lightning or weather.
 */
public final class GerakReaksi {

	private static final double PETIR_JARAK_SQ = 24.0D * 24.0D;
	private static final double TIBA_JARAK_SQ = 16.0D * 16.0D;
	private static final double MUSUH_JARAK_SQ = 10.0D * 10.0D;
	private static final int UKURAN = 6;

	/** Recent events: kind, position, game time (ring buffers). */
	private static final int[] JENIS = new int[UKURAN];
	private static final double[] X = new double[UKURAN];
	private static final double[] Y = new double[UKURAN];
	private static final double[] Z = new double[UKURAN];
	private static final long[] WAKTU = new long[UKURAN];
	private static final int[] SUMBER = new int[UKURAN];
	private static int kepala;

	private static final int PETIR = 1, TIBA = 2, MUSUH = 3, LEDAKAN = 4;

	static void init() {
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> dimuat(entity));
	}

	private static void dimuat(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		if (entity instanceof LightningBolt) {
			catat(PETIR, entity.getX(), entity.getY(), entity.getZ(), entity.getId());
		} else if (entity instanceof Player && entity != mc.player) {
			catat(TIBA, entity.getX(), entity.getY(), entity.getZ(), entity.getId());
		} else if (entity instanceof Enemy) {
			catat(MUSUH, entity.getX(), entity.getY(), entity.getZ(), entity.getId());
		}
	}

	private static void catat(int jenis, double x, double y, double z, int sumber) {
		Minecraft mc = Minecraft.getInstance();
		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		int i = kepala;
		kepala = (kepala + 1) % UKURAN;
		JENIS[i] = jenis;
		X[i] = x;
		Y[i] = y;
		Z[i] = z;
		WAKTU[i] = now;
		SUMBER[i] = sumber;
	}

	/** Another module reports a nearby explosion (bodies within 16 blocks jump). */
	public static void ledakan(double x, double y, double z) {
		catat(LEDAKAN, x, y, z, -1);
	}

	/** Pending explicit requests: entity id, clip, until (game time). */
	private static final int[] MINTA_ID = new int[16];
	private static final int[] MINTA_KLIP = new int[16];
	private static final long[] MINTA_SAMPAI = new long[16];
	private static int mintaKepala;

	/** Another module asks entity {@code entityId} to play reaction clip {@code klip} on the client for {@code durasi} ticks. */
	public static void picu(int entityId, int klip, int durasi) {
		Minecraft mc = Minecraft.getInstance();
		long now = mc.level != null ? mc.level.getGameTime() : 0L;
		int i = mintaKepala;
		mintaKepala = (mintaKepala + 1) % MINTA_ID.length;
		MINTA_ID[i] = entityId;
		MINTA_KLIP[i] = klip;
		MINTA_SAMPAI[i] = now + Math.max(1, durasi);
	}

	/**
	 * Checks the recent events for this body: starts a reaction ({@code k.reaksiKlip} until {@code k.reaksiSampai}) and
	 * a glance at an arriving player. {@code age} is the body's age in ticks, {@code now} the game time.
	 */
	static void cek(LivingEntity e, GerakKeadaan k, float age, long now, Gaya gaya) {
		for (int i = 0; i < MINTA_ID.length; i++) {
			if (MINTA_ID[i] == e.getId() && MINTA_SAMPAI[i] > now) {
				k.reaksiKlip = MINTA_KLIP[i];
				k.reaksiSampai = age + (MINTA_SAMPAI[i] - now);
				MINTA_ID[i] = Integer.MIN_VALUE;
			}
		}
		for (int i = 0; i < UKURAN; i++) {
			int jenis = JENIS[i];
			if (jenis == 0 || now - WAKTU[i] > 3L || WAKTU[i] <= k.petirTerakhir) {
				continue;
			}
			double dsq = e.distanceToSqr(X[i], Y[i], Z[i]);
			switch (jenis) {
				case PETIR, LEDAKAN -> {
					if (dsq <= (jenis == PETIR ? PETIR_JARAK_SQ : TIBA_JARAK_SQ)) {
						k.petirTerakhir = WAKTU[i];
						k.reaksiKlip = GerakId.KAGET;
						k.reaksiSampai = age + 10.0F + Math.floorMod(k.benih, 4);
					}
				}
				case TIBA -> {
					if (dsq <= TIBA_JARAK_SQ) {
						k.petirTerakhir = WAKTU[i];
						k.pemainLirik = SUMBER[i];
						k.pemainLirikSampai = age + 40.0F;
					}
				}
				case MUSUH -> {
					if (dsq <= MUSUH_JARAK_SQ && gaya.punya(Sifat.PENAKUT)) {
						k.petirTerakhir = WAKTU[i];
						k.reaksiKlip = GerakId.KAGET;
						k.reaksiSampai = age + 10.0F;
					}
				}
				default -> {
				}
			}
		}
	}

	private GerakReaksi() {
	}
}
