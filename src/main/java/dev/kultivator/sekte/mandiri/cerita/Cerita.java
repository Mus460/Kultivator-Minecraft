package dev.kultivator.sekte.mandiri.cerita;

import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.sekte.mandiri.Irama;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;

/**
 * Module 39E, Cerita, Layar &amp; Suara (DESIGN 39.7.3-39.7.5, 39.8): the entry of package {@code sekte.mandiri.cerita},
 * called by {@code sekte.mandiri.Mandiri}.
 * <ul>
 * <li>{@link #registerContent} (always, K1): the five payload types ({@link MandiriPayloads}).</li>
 * <li>{@link #init} (while {@code umum.mandiri} and {@code mandiri.aktif} are on): the diary writer ({@link BukuHarian},
 * C39.E1), the notices ({@link Pemberitahuan}, C39.E2), the details part {@code sekte_mandiri} ({@link InfoMandiri}),
 * the button receiver ({@link AksiMandiri}), the observer of loaded bodies ({@link Pengamat}: bubbles, small voice
 * lines, the board mirror), the named characters and their arcs ({@link DaftarTokoh}, {@link BusurTokoh},
 * {@link BusurSaingan}, {@link BusurAsmara}), the travel scrolls, the guide advancements ({@link Panduan}) and the
 * commands ({@link CeritaCommands}).</li>
 * </ul>
 * The client half is {@code client/sekte/mandiri/**} (the Jalan Hidup tab, the board, the diary composer, toasts, bubbles,
 * props and effect hooks). Own hall-record part: {@code MandiriData.<owner>.cerita} ({@link #bagian}).
 */
public final class Cerita {

	/** The key of this module's part of the hall record. */
	static final String BAGIAN = "cerita";

	private static boolean content;
	private static boolean initialised;
	private static volatile @Nullable MinecraftServer server;

	/** Registry content of 39E (always, both physical sides). */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
		MandiriPayloads.init();
	}

	/** Runtime behaviour of 39E (server listeners, ticks, commands); only while the module is on. */
	public static void init() {
		registerContent();
		if (initialised) {
			return;
		}
		initialised = true;
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			Pemberitahuan.bersih();
			Pengamat.bersih();
		});
		MandiriApi.setPencatat(BukuHarian::catat);
		MandiriApi.setPemberita(Pemberitahuan::kirim);
		MandiriPayloads.initServer();
		DiscipleHooks.addInfo(InfoMandiri::info);
		DiscipleHooks.addTick20(Pengamat::tick);
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (s.getTickCount() % 20 == 7) {
				Pemberitahuan.detik(s);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> Pemberitahuan.masuk(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> Pemberitahuan.keluar(s, handler.getPlayer().getUUID()));
		MandiriBus.BELANJA.register(BusurTokoh::belanja);
		MandiriBus.KOTA_PERISTIWA.register((s, owner, soul, town, jenis, delta) -> {
			if (jenis == null || owner == null || soul == null) {
				return;
			}
			if (!BusurSaingan.kota(s, owner, soul, town, jenis) && !BusurAsmara.kota(s, owner, soul, town, jenis)) {
				BusurTokoh.kota(s, owner, soul, town, jenis);
			}
		});
		MandiriBus.JALAN_TIBA.register((s, owner, soul, hasil) -> {
			if (owner == null || soul == null || hasil == null) {
				return;
			}
			BukuHarian.simpanGulungan(s, owner, soul, hasil.jenis().id(), hasil.tujuan(), hari(s), hasil.tahap(), hasil.sukses());
			BusurTokoh.tiba(s, owner, soul, hasil);
		});
		Panduan.init();
		CeritaCommands.init();
	}

	/** The running server (null between worlds). */
	static @Nullable MinecraftServer server() {
		return server;
	}

	/** The live sub-compound {@code key} of this module's part of {@code owner}'s hall record (created when missing). */
	static CompoundTag bagian(MinecraftServer server, UUID owner, String key) {
		CompoundTag root = MandiriApi.aula(server, owner, BAGIAN);
		CompoundTag child = root.getCompound(key).orElse(null);
		if (child == null) {
			child = new CompoundTag();
			root.put(key, child);
		}
		return child;
	}

	/** Today (world day of the overworld). */
	static long hari(MinecraftServer server) {
		return Irama.hari(server.overworld());
	}

	/** Writes one diary entry of {@code soul} now (core key short, {@code harian.<kunci>}). */
	static void tulis(MinecraftServer server, UUID owner, UUID soul, String jenis, String kunci, EntriHarian.Suasana suasana, boolean penting,
			String... arg) {
		MandiriApi.catat(owner, soul, new EntriHarian(hari(server), Irama.jam(server.overworld()), jenis, kunci,
				arg == null ? java.util.List.of() : Arrays.asList(arg), suasana, penting));
	}

	private Cerita() {
	}
}
