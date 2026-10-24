package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.Distrik;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.Putusan;
import dev.kultivator.dunia.tempat.RencanaKotaBesar;
import dev.kultivator.dunia.tempat.TempatZones;
import dev.kultivator.dunia.tempat.worldgen.TataLetakStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Plan geometry of the capitals for the civil services (DESIGN 38.B.2): which capital a position belongs to, its town id
 * (the {@code kota/kota_besar} site of the central district, {@link Towns#situsRencana}), its canonical frame (north = -z,
 * the Great South Gate on +z, before the whole-city rotation), the walled core, the wall band and the district at a
 * position. Pure plan data plus the shared terrain verdict; no chunk is read here.
 */
public final class KotaBesarKota {

	/** Half size of the nine inner cells (3 x 144 / 2). */
	public static final int DALAM = 216;
	/** Outer face of the wall band (8 blocks). */
	public static final int TEMBOK_LUAR = 224;
	/** Distance from the centre where a player counts as "at the capital" (events, curfew drum, festival sky). */
	public static final int HIDUP = 256;

	/** The capital whose centre lies within {@value Towns#SAFE_RADIUS_KOTA_BESAR} + 100 blocks of {@code pos} and that is born. */
	public static @Nullable Kandidat di(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		Kandidat best = null;
		double bestSq = Math.pow(Towns.SAFE_RADIUS_KOTA_BESAR + 100, 2);
		for (Kandidat k : RencanaKotaBesar.of(level.getSeed()).kandidat()) {
			double dx = k.pusatX() - pos.getX();
			double dz = k.pusatZ() - pos.getZ();
			double d = dx * dx + dz * dz;
			if (d <= bestSq && lahir(level, k)) {
				bestSq = d;
				best = k;
			}
		}
		return best;
	}

	/** True when capital {@code k} passes its final verdict (born in new chunks). */
	public static boolean lahir(ServerLevel level, Kandidat k) {
		return putusan(level, k).layak();
	}

	public static Putusan putusan(ServerLevel level, Kandidat k) {
		return RencanaKotaBesar.akhir(TempatZones.sumber(level), k);
	}

	/** The town id (site id) of capital {@code k}. */
	public static long situs(Kandidat k) {
		return Towns.situsRencana(Level.OVERWORLD, k, Towns.JENIS_KOTA_BESAR);
	}

	/** The born capital whose town id is {@code situs}, or null. */
	public static @Nullable Kandidat dariSitus(ServerLevel overworld, long situs) {
		for (Kandidat k : RencanaKotaBesar.of(overworld.getSeed()).kandidat()) {
			if (situs(k) == situs) {
				return lahir(overworld, k) ? k : null;
			}
		}
		return null;
	}

	/** The capital's name. */
	public static Component nama(MinecraftServer server, Kandidat k) {
		return KotaBesarNames.nama(server.overworld().getSeed(), k.index());
	}

	/** The street floor height of the capital. */
	public static int dasarY(ServerLevel level, Kandidat k) {
		Putusan p = putusan(level, k);
		return p.layak() ? p.dasarY() : level.getSeaLevel() + 4;
	}

	/** Canonical offset (before rotation) of world column {@code (x, z)} from the centre: {dx, dz}. */
	public static int[] lokal(Kandidat k, int x, int z) {
		Rotation balik = Rotation.values()[Math.floorMod(4 - k.putar(), 4)];
		return TataLetakStructure.rotate(balik, x - k.pusatX(), z - k.pusatZ());
	}

	/** World column of canonical offset {@code (dx, dz)}: {x, z}. */
	public static int[] dunia(Kandidat k, int dx, int dz) {
		int[] o = TataLetakStructure.rotate(Rotation.values()[k.putar()], dx, dz);
		return new int[]{k.pusatX() + o[0], k.pusatZ() + o[1]};
	}

	/** World block at canonical offset {@code (dx, dz)} on the street floor (+ {@code dy}). */
	public static BlockPos titik(ServerLevel level, Kandidat k, int dx, int dz, int dy) {
		int[] w = dunia(k, dx, dz);
		return new BlockPos(w[0], dasarY(level, k) + dy, w[1]);
	}

	/** True inside the walled core (the nine cells, without the wall band). */
	public static boolean dalamTembok(Kandidat k, BlockPos pos) {
		int[] l = lokal(k, pos.getX(), pos.getZ());
		return Math.abs(l[0]) < DALAM && Math.abs(l[1]) < DALAM;
	}

	/** True on the wall band (between the inner cells and the outer wall face, gates included). */
	public static boolean diTembok(Kandidat k, BlockPos pos) {
		int[] l = lokal(k, pos.getX(), pos.getZ());
		int m = Math.max(Math.abs(l[0]), Math.abs(l[1]));
		return m >= DALAM && m < TEMBOK_LUAR;
	}

	/** The box of the walled core including the wall band. */
	public static BoundingBox kotakTembok(Kandidat k) {
		int r = TEMBOK_LUAR;
		return new BoundingBox(k.pusatX() - r, -64, k.pusatZ() - r, k.pusatX() + r, 320, k.pusatZ() + r);
	}

	/** The district id at {@code pos} ({@code pusat}, {@code pasar}, ..., {@code kumuh}, {@code luar}), or null outside. */
	public static @Nullable String distrik(Kandidat k, BlockPos pos) {
		for (Distrik d : RencanaKotaBesar.distrikOf(k)) {
			if (Math.abs(d.pusatX() - pos.getX()) <= 72 && Math.abs(d.pusatZ() - pos.getZ()) <= 72) {
				return d.id();
			}
		}
		return null;
	}

	/** The world centre column of district {@code id}, or the city centre. */
	public static BlockPos pusatDistrik(ServerLevel level, Kandidat k, String id) {
		for (Distrik d : RencanaKotaBesar.distrikOf(k)) {
			if (d.id().equals(id)) {
				return new BlockPos(d.pusatX(), dasarY(level, k), d.pusatZ());
			}
		}
		return new BlockPos(k.pusatX(), dasarY(level, k), k.pusatZ());
	}

	/** Horizontal distance from the capital's centre. */
	public static double jarak(Kandidat k, BlockPos pos) {
		return Math.hypot(pos.getX() - k.pusatX(), pos.getZ() - k.pusatZ());
	}

	private KotaBesarKota() {
	}
}
