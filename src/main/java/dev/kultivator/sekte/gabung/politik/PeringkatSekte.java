package dev.kultivator.sekte.gabung.politik;

import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.DiplomasiGabung;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The sect ranking of DESIGN 41.4.7 ("Papan Peringkat Sekte"): every NPC sect by {@code strength() = tier x 100 + sumber},
 * worked out lazily once per world day (the first sect of {@code Politik}'s daily pass, or the first reader of a new day).
 * Shown in the Direktori (41.10.1), the Politik screen, the capital's rumour board ({@code gabung.peringkat} when the
 * first place changes hands) and the opening line of the 30-day Sect Meeting ({@link #pembuka}).
 * <p>
 * Each sect remembers its last place in its {@code politik} compound ({@code peringkat}), so the change of the first place
 * survives a restart. Server thread only.
 */
public final class PeringkatSekte {

	private static final String KEY = "kultivator.gabung.politik.peringkat.";

	/** One row: sect id, strength, tier, class number 1-5, MEROSOT. */
	public record Baris(String sekte, int kekuatan, int tier, int kelas, boolean merosot) {
	}

	private static List<Baris> cache = List.of();
	private static long cacheHari = Long.MIN_VALUE;
	private static MinecraftServer cacheServer;

	/** The ranking of today, strongest first (recomputed at most once per world day). */
	public static List<Baris> daftar(MinecraftServer server) {
		if (server == null) {
			return List.of();
		}
		long hari = dev.kultivator.sekte.gabung.api.GabungApi.hari(server);
		if (cacheServer != server || cacheHari != hari) {
			hitung(server, hari);
		}
		return cache;
	}

	/** The 1-based place of {@code sekte} today (0 for an unknown sect). */
	public static int peringkat(MinecraftServer server, String sekte) {
		List<Baris> list = daftar(server);
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).sekte().equals(sekte)) {
				return i + 1;
			}
		}
		return 0;
	}

	/** Recomputes the ranking (also {@code uji gabung peringkat}); a new first place spreads the rumour. */
	static void hitung(MinecraftServer server, long hari) {
		List<Baris> rows = new ArrayList<>();
		for (String id : DiplomasiGabung.sekte(server)) {
			int tier = SekteApi.diplomasi().tier(id);
			rows.add(new Baris(id, DiplomasiGabung.kekuatan(server, id), tier, KelasSekte.of(Math.max(1, tier)),
					DiplomasiGabung.merosot(server, id)));
		}
		rows.sort(Comparator.comparingInt(Baris::kekuatan).reversed().thenComparing(Baris::sekte));
		cache = List.copyOf(rows);
		cacheHari = hari;
		cacheServer = server;
		GabungData data = GabungData.get(server);
		for (int i = 0; i < rows.size(); i++) {
			CompoundTag b = DiplomasiGabung.bagian(server, rows.get(i).sekte());
			int lama = b.getIntOr("peringkat", 0);
			if (lama != i + 1) {
				b.putInt("peringkat", i + 1);
				data.setDirty();
				if (i == 0 && lama > 1) {
					Politik.kabar(server, "gabung.peringkat", DiplomasiGabung.nama(server, rows.get(0).sekte()), lama);
					Politik.kabarAnggota(server, rows.get(0).sekte(), Component.translatable(KEY + "juara",
							DiplomasiGabung.nama(server, rows.get(0).sekte())), ChatFormatting.GOLD);
				}
			}
		}
	}

	/** The opening line of the Sect Meeting: the first three places ("1. ... 2. ... 3. ..."). */
	public static Component pembuka(MinecraftServer server) {
		List<Baris> list = daftar(server);
		MutableComponent line = Component.translatable(KEY + "pembuka");
		for (int i = 0; i < Math.min(3, list.size()); i++) {
			Baris b = list.get(i);
			line.append(Component.literal(" " + (i + 1) + ". "))
					.append(DiplomasiGabung.nama(server, b.sekte()).copy().withStyle(ChatFormatting.GOLD))
					.append(Component.literal(" ("))
					.append(KelasSekte.byNomor(b.kelas()).nama())
					.append(Component.literal(")"));
		}
		return line;
	}

	/** Forgets the cache (server stop). */
	static void clear() {
		cache = List.of();
		cacheHari = Long.MIN_VALUE;
		cacheServer = null;
	}

	private PeringkatSekte() {
	}
}
