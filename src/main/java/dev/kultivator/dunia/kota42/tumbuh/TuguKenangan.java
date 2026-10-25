package dev.kultivator.dunia.kota42.tumbuh;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Memorials of finished rebuild projects (DESIGN 42.9.5 "Tugu Kenangan"): a client-drawn inscription on the square (no
 * block) with the world day, the blocks rebuilt and the five biggest donors. {@link BangunKembali} writes one record per
 * finished project (at most 8 per place, oldest first out); clients and screens read them here.
 */
public final class TuguKenangan {

	/** One donor line of a memorial. */
	public record Penyumbang(String nama, long nb) {
	}

	/** One memorial: world day the project ended, blocks rebuilt, up to five donors (biggest first). */
	public record Tugu(long hari, int blok, List<Penyumbang> penyumbang) {
	}

	/** The memorials of place {@code situs}, oldest first. */
	public static List<Tugu> daftar(MinecraftServer server, long situs) {
		List<Tugu> out = new ArrayList<>();
		for (Tag t : BangunKembali.data(server, situs).getListOrEmpty("tugu")) {
			if (t instanceof CompoundTag c) {
				List<Penyumbang> donors = new ArrayList<>();
				for (Tag d : c.getListOrEmpty("dn")) {
					if (d instanceof CompoundTag dc) {
						donors.add(new Penyumbang(dc.getStringOr("n", "?"), dc.getLongOr("nb", 0L)));
					}
				}
				out.add(new Tugu(c.getLongOr("hari", 0L), c.getIntOr("blok", 0), List.copyOf(donors)));
			}
		}
		return out;
	}

	private TuguKenangan() {
	}
}
