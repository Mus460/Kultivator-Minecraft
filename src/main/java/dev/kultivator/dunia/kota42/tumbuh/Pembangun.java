package dev.kultivator.dunia.kota42.tumbuh;

import dev.kultivator.dunia.kota42.sekte.TugasKotaMurid;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the extra hands work (DESIGN 42.9.4-42.9.5): the one question both the migrant builders of the next town
 * ({@link TukangPendatang}) and the player's disciples on {@code KERJA_KOTA} ({@code TugasKotaMurid}, post
 * {@code bangun}) ask. A place has work for visitors while a rebuild project runs (the boxes of its destroyed houses) or a
 * plot is being built (the plot's footprint); otherwise none. The disciples also speed the crews up: every builder
 * disciple is one third of a crew ({@link #faktorMurid}, at most x2), the "laju +1 regu" of 42.9.5.
 */
public final class Pembangun {

	/** Disciples that count (a crew is three craftsmen; one more crew at most). */
	public static final int MURID_MAKS = 3;

	/** True when place {@code situs} has building work right now. */
	public static boolean adaKerja(MinecraftServer server, long situs) {
		return !lokasi(server, situs).isEmpty();
	}

	/**
	 * The work boxes of {@code situs}: the destroyed houses of the running project (the middle of the place when none is
	 * recorded), else the footprint of the plot being built or waiting for material. Empty when there is none.
	 */
	public static List<BoundingBox> lokasi(MinecraftServer server, long situs) {
		List<BoundingBox> out = new ArrayList<>();
		if (BangunKembali.aktif(server, situs)) {
			List<BoundingBox> houses = BangunKembali.rumahProyek(server, situs);
			if (!houses.isEmpty()) {
				return houses;
			}
			TempatHuni huni = Pemanggil.huni(situs);
			if (huni != null) {
				BlockPos c = huni.pusat();
				out.add(new BoundingBox(c.getX() - 6, c.getY(), c.getZ() - 6, c.getX() + 6, c.getY() + 6, c.getZ() + 6));
			}
			return out;
		}
		CompoundTag plot = Kavling.berjalan(server, situs);
		if (plot != null) {
			out.add(Kavling.kotak(plot));
		}
		return out;
	}

	/** Crew speed factor of the builder disciples of {@code situs}: 1 + n/3 (n up to {@link #MURID_MAKS}). */
	public static double faktorMurid(MinecraftServer server, long situs) {
		int n = Math.min(MURID_MAKS, TugasKotaMurid.pembangun(server, situs));
		return n == 0 ? 1.0D : 1.0D + n / 3.0D;
	}

	/**
	 * A free standing block beside the {@code urut}-th work spot of {@code situs}: the four sides of the first box in turn,
	 * then the next box, a step further along the side each round; null when there is no work.
	 */
	public static @Nullable BlockPos titikKerja(ServerLevel level, long situs, int urut) {
		List<BoundingBox> boxes = lokasi(level.getServer(), situs);
		if (boxes.isEmpty()) {
			return null;
		}
		int i = Math.floorMod(urut, boxes.size() * 4);
		BoundingBox b = boxes.get(i / 4);
		int geser = (urut / (boxes.size() * 4)) % 5 - 2;
		BlockPos c = b.getCenter();
		int x = c.getX();
		int z = c.getZ();
		switch (i % 4) {
			case 0 -> {
				z = b.minZ() - 1;
				x += geser;
			}
			case 1 -> {
				x = b.maxX() + 1;
				z += geser;
			}
			case 2 -> {
				z = b.maxZ() + 1;
				x -= geser;
			}
			default -> {
				x = b.minX() - 1;
				z -= geser;
			}
		}
		BlockPos spot = TempatApi.groundSpot(level, x, z, b.minY());
		return spot != null ? spot : new BlockPos(x, b.minY(), z);
	}

	private Pembangun() {
	}
}
