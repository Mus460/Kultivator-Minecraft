package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.worldgen.TataLetakStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Geometry and small lookups of the Kota Iblis (DESIGN 38.T.7.2): which town a player stands in, district-local
 * template coordinates to world positions, the realm band used by every price formula, the world day, the Zona
 * Perjanjian test and the town's strength (38.R.5: the ruler's realm).
 * <p>
 * District-local coordinates are those of the generator canvas (tools/gen_tempat_tema_iblis.py): 0..95 on x and z,
 * the street floor at template y 4. TataLetak places canvas column {@code (cx, cz)} of a district at
 * {@code centre + rotate(place, cx - 48, cz - 48)} and template y {@code ty} at {@code dasarY - 1 - 4 + ty}.
 */
public final class IblisKota {

	/** Template street floor (tools/tempat_lib.py LANTAI_Y). */
	public static final int LY = TataLetakStructure.LANTAI_Y;

	private IblisKota() {
	}

	/** The Kota Iblis whose box holds {@code pos}, or null (overworld only). */
	public static @Nullable TemaKota.Kota di(ServerLevel level, BlockPos pos) {
		if (level == null || level.dimension() != Level.OVERWORLD) {
			return null;
		}
		TemaKota.Kota kota = TemaKota.di(level, pos);
		return kota != null && kota.tema() == Tema.IBLIS ? kota : null;
	}

	/** The Kota Iblis the player stands in, or null. */
	public static @Nullable TemaKota.Kota di(ServerPlayer player) {
		return di(player.level(), player.blockPosition());
	}

	/** The Kota Iblis of id {@code situsId}, or null. */
	public static @Nullable TemaKota.Kota dari(MinecraftServer server, long situsId) {
		TemaKota.Kota kota = TemaKota.dari(server, situsId);
		return kota != null && kota.tema() == Tema.IBLIS ? kota : null;
	}

	/** The nearest Kota Iblis within {@code radius} blocks, or null. */
	public static @Nullable TemaKota.Kota terdekat(ServerLevel level, BlockPos pos, int radius) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		return TemaKota.terdekat(level, pos, Tema.IBLIS, radius);
	}

	/** The world position of canvas point {@code (cx, ty, cz)} of district {@code distrik}; null for an unknown id. */
	public static @Nullable Vec3 lokal(TemaKota.Kota kota, String distrik, double cx, double ty, double cz) {
		BlockPos centre = kota.distrik(distrik);
		if (centre == null) {
			return null;
		}
		Rotation r = Rotation.values()[kota.kandidat().putar() & 3];
		double dx = cx - 48.0D;
		double dz = cz - 48.0D;
		double wx;
		double wz;
		switch (r) {
			case CLOCKWISE_90 -> {
				wx = -dz;
				wz = dx;
			}
			case CLOCKWISE_180 -> {
				wx = -dx;
				wz = -dz;
			}
			case COUNTERCLOCKWISE_90 -> {
				wx = dz;
				wz = -dx;
			}
			default -> {
				wx = dx;
				wz = dz;
			}
		}
		double y = kota.dasarY() - 1 - LY + ty;
		return new Vec3(centre.getX() + wx, y, centre.getZ() + wz);
	}

	/**
	 * The inverse of {@link #lokal}: the canvas point {@code (cx, ty, cz)} of district {@code distrik} under world position
	 * {@code pos} (null for an unknown district). Used to ask "is the player inside the tower / the pit / a cell row".
	 */
	public static @Nullable Vec3 kanvas(TemaKota.Kota kota, String distrik, Vec3 pos) {
		BlockPos centre = kota.distrik(distrik);
		if (centre == null) {
			return null;
		}
		double wx = pos.x - centre.getX();
		double wz = pos.z - centre.getZ();
		double dx;
		double dz;
		switch (Rotation.values()[kota.kandidat().putar() & 3]) {
			case CLOCKWISE_90 -> {
				dx = wz;
				dz = -wx;
			}
			case CLOCKWISE_180 -> {
				dx = -wx;
				dz = -wz;
			}
			case COUNTERCLOCKWISE_90 -> {
				dx = -wz;
				dz = wx;
			}
			default -> {
				dx = wx;
				dz = wz;
			}
		}
		return new Vec3(dx + 48.0D, pos.y - (kota.dasarY() - 1 - LY), dz + 48.0D);
	}

	/** True when {@code pos} lies inside the canvas box {@code [x0..x1] x [z0..z1]} (template y {@code [y0..y1]}) of a district. */
	public static boolean dalam(TemaKota.Kota kota, String distrik, Vec3 pos, double x0, double y0, double z0, double x1, double y1, double z1) {
		Vec3 c = kanvas(kota, distrik, pos);
		return c != null && c.x >= x0 && c.x < x1 + 1.0D && c.z >= z0 && c.z < z1 + 1.0D && c.y >= y0 && c.y < y1 + 1.0D;
	}

	/** {@link #lokal} as a block position (the block whose min corner is the point). */
	public static @Nullable BlockPos blok(TemaKota.Kota kota, String distrik, int cx, int ty, int cz) {
		Vec3 v = lokal(kota, distrik, cx, ty, cz);
		return v == null ? null : BlockPos.containing(v.x, v.y, v.z);
	}

	/** World yaw of a canvas yaw (degrees, 0 = facing south = +z) after the town's rotation. */
	public static float yaw(TemaKota.Kota kota, float canvasYaw) {
		return canvasYaw + 90.0F * (kota.kandidat().putar() & 3);
	}

	/** The realm band of every 38 price ({@code Barang.pita}, 0-8). */
	public static int band(ServerPlayer player) {
		return Math.max(0, Math.min(8, Barang.pita(player)));
	}

	/** The overworld clock day of the themed calendar. */
	public static long hari(ServerLevel level) {
		return TemaKalender.hari(level.getServer().overworld());
	}

	/** Time of day 0-23999 of the overworld clock. */
	public static long jam(ServerLevel level) {
		return Math.floorMod(TemaKalender.jam(level.getServer().overworld()), 24000L);
	}

	/**
	 * True inside a Zona Perjanjian (the only safe ground, 38.T.7.8 butir 1). After the Penebusan ending (Ye Wuhen rules)
	 * the Pact covers the whole town by day (06:00-18:00).
	 */
	public static boolean zp(ServerLevel level, BlockPos pos) {
		if (TemaApi.zonaPerjanjian(level, pos)) {
			return true;
		}
		if (jam(level) >= 12000L) {
			return false;
		}
		TemaKota.Kota kota = di(level, pos);
		return kota != null && IblisData.PENGUASA_YE.equals(IblisData.penguasa(level.getServer(), kota.situsId()));
	}

	/** The town's strength for ransoms (38.R.5: the ruler's realm): 4 inside 4500 blocks of the world centre, else 5. */
	public static int kekuatan(TemaKota.Kota kota) {
		double d = Math.hypot(kota.pusat().getX(), kota.pusat().getZ());
		return d < 4500.0D ? 4 : 5;
	}

	/** Ticks since a mechanism body entered its state ({@code mulai} is the game time masked to 31 bits). */
	public static long sejak(dev.kultivator.dunia.tempat.tema.bersama.TemaMekanismeEntity body, long now) {
		long d = (now & 0x7fffffffL) - (body.mulai() & 0x7fffffffL);
		return d < 0L ? d + 0x80000000L : d;
	}

	/**
	 * Puts the residents of town {@code situs} on event schedule {@code acara} ({@code tema.<id>} of
	 * {@code tempat/jadwal/kota_tema_iblis.json}) for {@code ticks}. One override per town: a later event replaces it.
	 */
	public static void timpa(ServerLevel level, long situs, String acara, long ticks) {
		dev.kultivator.dunia.tempat.hidup.HidupApi.timpaJadwal(situs, acara, level.getServer().overworld().getGameTime() + ticks);
	}

	/** Ends the schedule override {@code acara} of town {@code situs}, only if it is still the one in force. */
	public static void lepasTimpa(ServerLevel level, long situs, String acara) {
		if (acara.equals(dev.kultivator.dunia.tempat.hidup.HidupApi.timpaan(situs, level.getServer().overworld().getGameTime()))) {
			dev.kultivator.dunia.tempat.hidup.HidupApi.timpaJadwal(situs, "", 0L);
		}
	}

	/** True at night for the demonic schedule (18:00-06:00, time of day 12000-24000). */
	public static boolean malam(ServerLevel level) {
		return jam(level) >= 12000L;
	}
}
