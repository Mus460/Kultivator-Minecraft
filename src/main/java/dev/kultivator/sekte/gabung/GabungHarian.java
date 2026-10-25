package dev.kultivator.sekte.gabung;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.api.GabungApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one daily server task of Gabung Sekte (DESIGN 41.12.3): every {@link #TICK_PER_SEKTE} ticks one sect, in turn, gets
 * its daily round once per world day ({@code DataSekteGabung.harianHari}); the round runs every registered
 * {@link Tugas} for that sect: 41C the board, quota and tournament, 41D war, raids and seats. O(sects), never per member.
 * The time each round takes is kept for {@code uji gabung anggaran} (target below 0.3 ms per sect).
 */
public final class GabungHarian {

	/** Ticks between two sects' rounds. */
	public static final int TICK_PER_SEKTE = 20;
	/** The target of 41.12.3 (nanoseconds per sect round). */
	public static final long TARGET_NANO = 300_000L;

	/** One module's daily work for one sect. */
	@FunctionalInterface
	public interface Tugas {
		void harian(MinecraftServer server, String sekte, GabungData.DataSekteGabung data, long hari);
	}

	private static final List<Tugas> TUGAS = new CopyOnWriteArrayList<>();
	private static int giliran;
	private static long jumlahRonde;
	private static long totalNano;
	private static long maksNano;
	private static long terakhirNano;
	private static String terakhirSekte = "";
	private static boolean registered;

	/** Registers a module's daily work (call from the module's init). */
	public static void addTugas(Tugas tugas) {
		if (tugas != null) {
			TUGAS.add(tugas);
		}
	}

	/** From {@code Gabung.init} (module on). */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(GabungHarian::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			giliran = 0;
			jumlahRonde = 0L;
			totalNano = 0L;
			maksNano = 0L;
			terakhirNano = 0L;
			terakhirSekte = "";
		});
		GabungUji.daftar("anggaran", () -> Commands.literal("anggaran").executes(ctx -> {
			for (Component line : laporan()) {
				ctx.getSource().sendSuccess(() -> line, false);
			}
			return 1;
		}));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICK_PER_SEKTE != 0 || !Gabung.aktif()) {
			return;
		}
		List<String> sects = new ArrayList<>(SekteApi.diplomasi().sects());
		if (sects.isEmpty()) {
			return;
		}
		giliran = Math.floorMod(giliran, sects.size());
		String sekte = sects.get(giliran);
		giliran = (giliran + 1) % sects.size();
		GabungData data = GabungData.get(server);
		long hari = GabungApi.hari(server);
		GabungData.DataSekteGabung d = data.sekte(sekte);
		if (d.harianHari == hari) {
			return;
		}
		d.harianHari = hari;
		data.setDirty();
		ronde(server, sekte, d, hari);
	}

	/** Runs the daily round of {@code sekte} now (tests, or a forced refresh); returns the nanoseconds it took. */
	public static long ronde(MinecraftServer server, String sekte, GabungData.DataSekteGabung d, long hari) {
		long start = System.nanoTime();
		for (Tugas t : TUGAS) {
			try {
				t.harian(server, sekte, d, hari);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Gabung daily task failed for {}", sekte, exception);
			}
		}
		long took = System.nanoTime() - start;
		jumlahRonde++;
		totalNano += took;
		maksNano = Math.max(maksNano, took);
		terakhirNano = took;
		terakhirSekte = sekte;
		GabungData.get(server).setDirty();
		return took;
	}

	/** {@code uji gabung anggaran}: round times, registered tasks, 41 entities against the cap, confined members. */
	public static List<Component> laporan() {
		List<Component> out = new ArrayList<>();
		double rata = jumlahRonde == 0L ? 0.0D : totalNano / (double) jumlahRonde / 1_000_000.0D;
		out.add(Component.translatable("kultivator.gabung.anggaran.judul").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		out.add(Component.translatable("kultivator.gabung.anggaran.ronde", jumlahRonde, String.format(java.util.Locale.ROOT, "%.3f", rata),
				String.format(java.util.Locale.ROOT, "%.3f", maksNano / 1_000_000.0D), TUGAS.size())
				.withStyle(maksNano <= TARGET_NANO ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		if (!terakhirSekte.isEmpty()) {
			out.add(Component.translatable("kultivator.gabung.anggaran.terakhir", terakhirSekte,
					String.format(java.util.Locale.ROOT, "%.3f", terakhirNano / 1_000_000.0D)).withStyle(ChatFormatting.GRAY));
		}
		out.add(Component.translatable("kultivator.gabung.anggaran.entitas", DuniaBudget.count("gabung"), DuniaBudget.cap("gabung"))
				.withStyle(ChatFormatting.AQUA));
		return out;
	}

	private GabungHarian() {
	}
}
