package dev.kultivator.dunia.tempat.tema.bersama;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.tempat.tema.TemaSibuk;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The rhythm mini-game {@code tema_irama} (DESIGN 38.T.13.3; keys {@link TemaLayar.Irama}) shared by the whirling dance
 * of the oasis (Malam Tari Putar, 30 s), the loom of the Kota Air (20 s; the Kain Xiu Niang takes three rounds) and the
 * dragon-boat drum (60 s). A theme system calls {@link #mulai} with a callback; the server writes the beat track (beat
 * times and direction keys, harder each round), the client plays it locally and sends {@code hasil:SKOR} once. The
 * server accepts a result only from the open session, not before the track can have ended and not after a generous
 * late window, clamps it to 0-100 and hands it to the callback. While playing, the player is held in {@code tema.tari}
 * (other scheduled events wait, disciples do not follow a teleport).
 */
public final class TemaIrama {

	/** What a theme does with the result. */
	@FunctionalInterface
	public interface Hasil {
		void selesai(ServerPlayer player, int skor, boolean lulus, int babak);
	}

	/** Pass score. */
	public static final int LULUS = 70;
	/** Ticks after the track's end a late result is still accepted. */
	public static final int TELAT = 1200;
	/** Ticks before the track's end an early result is still accepted (client rounding). */
	public static final int AWAL = 40;

	private record Sesi(String pola, int babak, int babakTotal, long mulai, int durasi, int ketukan, CompoundTag lembar, Hasil hasil) {
	}

	private static final Map<UUID, Sesi> SESI = new HashMap<>();
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TemaLayar.daftar(TemaLayar.Irama.LAYAR, TemaIrama::bangun, TemaIrama::aksi);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESI.remove(handler.getPlayer().getUUID()));
	}

	/**
	 * Starts one round of pattern {@code pola} ({@link TemaLayar.Irama#POLA_TARI_PUTAR}, {@code POLA_TENUN},
	 * {@code POLA_GENDERANG}) for the player at NPC {@code npcId} (or at anchor block {@code jangkar} when npcId is -1).
	 * False when the pattern is unknown.
	 */
	public static boolean mulai(ServerPlayer player, int npcId, @Nullable BlockPos jangkar, String pola, int babak, int babakTotal, Hasil hasil) {
		int durasi = switch (pola) {
			case TemaLayar.Irama.POLA_TARI_PUTAR -> 600;
			case TemaLayar.Irama.POLA_TENUN -> 400;
			case TemaLayar.Irama.POLA_GENDERANG -> 1200;
			default -> -1;
		};
		if (durasi < 0 || hasil == null) {
			return false;
		}
		long now = player.level().getGameTime();
		RandomSource random = RandomSource.create(player.getUUID().getLeastSignificantBits() ^ now * 0x9E3779B97F4A7C15L ^ babak);
		int[][] track = trek(pola, Math.max(1, babak), durasi, random);
		CompoundTag sheet = new CompoundTag();
		LembarKunci.teks(sheet, LembarKunci.JUDUL, Component.translatable("kultivator.kota_tema.irama." + pola));
		sheet.putString(LembarKunci.GAYA, LembarKunci.GAYA_KAIN);
		sheet.putString(TemaLayar.Irama.POLA, pola);
		sheet.putIntArray(TemaLayar.Irama.KETUKAN, track[0]);
		sheet.putIntArray(TemaLayar.Irama.ARAH, track[1]);
		sheet.putInt(TemaLayar.Irama.DURASI, durasi);
		sheet.putInt(TemaLayar.Irama.LULUS, LULUS);
		sheet.putInt(TemaLayar.Irama.BABAK, Math.max(1, babak));
		sheet.putInt(TemaLayar.Irama.BABAK_TOTAL, Math.max(babak, babakTotal));
		SESI.put(player.getUUID(), new Sesi(pola, babak, babakTotal, now, durasi, track[0].length, sheet, hasil));
		TemaSibuk.tahan(player, TemaSibuk.TARI, durasi + TELAT);
		if (npcId < 0 && jangkar != null) {
			TempatLayar.bukaDi(player, TemaLayar.Irama.LAYAR, jangkar, sheet.copy());
		} else {
			TempatLayar.buka(player, TemaLayar.Irama.LAYAR, npcId, sheet.copy());
		}
		return true;
	}

	/**
	 * The beat track: {@code [times, directions]}. Tari putar: a whirl of turns (right, down, left, up) with stamps
	 * between, quicker each round; tenun: the shuttle left and right with a treadle (down) every fourth beat; genderang: a
	 * steady stroke every 8-10 ticks with an accent (up) on every fourth and drum rolls late in the race.
	 */
	static int[][] trek(String pola, int babak, int durasi, RandomSource random) {
		int[] times = new int[160];
		int[] dirs = new int[160];
		int n = 0;
		int t = 40;
		int end = durasi - 30;
		switch (pola) {
			case TemaLayar.Irama.POLA_TARI_PUTAR -> {
				int gap = Math.max(8, 16 - babak * 2);
				int turn = 0;
				while (t < end && n < times.length) {
					times[n] = t;
					dirs[n] = random.nextInt(5) == 0 ? 0 : 1 + (turn++ % 3);
					n++;
					t += gap + (random.nextBoolean() ? 0 : gap / 2);
				}
			}
			case TemaLayar.Irama.POLA_TENUN -> {
				int gap = Math.max(9, 15 - babak * 2);
				boolean left = false;
				while (t < end && n < times.length) {
					times[n] = t;
					dirs[n] = n % 4 == 3 ? 2 : left ? 3 : 1;
					if (n % 4 != 3) {
						left = !left;
					}
					n++;
					t += gap;
				}
			}
			default -> {
				int gap = Math.max(7, 10 - babak);
				while (t < end && n < times.length) {
					boolean roll = t > end * 3 / 4 && random.nextInt(3) == 0;
					times[n] = t;
					dirs[n] = n % 4 == 0 ? 0 : roll ? 2 : 1 + random.nextInt(2) * 2;
					n++;
					t += roll ? Math.max(4, gap / 2) : gap;
				}
			}
		}
		return new int[][] {java.util.Arrays.copyOf(times, n), java.util.Arrays.copyOf(dirs, n)};
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		Sesi sesi = SESI.get(player.getUUID());
		return sesi == null ? null : sesi.lembar().copy();
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		if (!TemaLayar.Irama.OP_HASIL.equals(parts[0])) {
			return;
		}
		Sesi sesi = SESI.remove(player.getUUID());
		if (sesi == null) {
			return;
		}
		TemaSibuk.lepas(player, TemaSibuk.TARI);
		long age = player.level().getGameTime() - sesi.mulai();
		if (age < sesi.durasi() - AWAL || age > sesi.durasi() + TELAT) {
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.irama.tidak_sah"));
			return;
		}
		int skor = Math.max(0, Math.min(100, LembarKunci.argInt(parts, 1, 0)));
		try {
			sesi.hasil().selesai(player, skor, skor >= LULUS, sesi.babak());
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Rhythm game {} result handler failed", sesi.pola(), exception);
		}
	}

	/** True while the player plays a round. */
	public static boolean bermain(ServerPlayer player) {
		return SESI.containsKey(player.getUUID());
	}

	private TemaIrama() {
	}
}
