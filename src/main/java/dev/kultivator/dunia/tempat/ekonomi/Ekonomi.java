package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Init of 38EK, the money loop (DESIGN 38.M): weapon shops, bank, guilds, graded missions, sect ledger, caravans, the
 * Grand Auction. Called once from {@code Tempat.init()}. Registry entries (items, components, the cart entity) are
 * registered always; the switches of {@link EkonomiConfig} only gate behaviour (38.0 rule 17).
 * <p>
 * Ticks are spread (38.D.7): missions, road and sect caravans every 20 ticks, dishes and the Grand Auction check every
 * 100-1200 ticks, the daily stall sale and caravan days once on a new world day. Every NPC service is listed in the
 * "Jasa" tab of the 38 dialog through one {@code CeritaApi.daftarJasa} provider.
 */
public final class Ekonomi {

	private static boolean initialised;
	private static long tick;
	private static long hariTerakhir = Long.MIN_VALUE;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		EkonomiEntities.init();
		Barang.init();
		SenjataKota.init();
		EkonomiItems.init();
		EkonomiReload.register();
		TokoSenjata.init();
		RumahUang.init();
		JudiBatu.init();
		Hidangan.init();
		Penginapan.init();
		DaftarNagaMuda.init();
		MisiGilda.init();
		GildaKultivator.init();
		BukuKas.init();
		LapakSekte.init();
		KafilahJalan.init();
		KafilahSekte.init();
		LelangAgung.init();
		GildaTempa.init();
		GildaAlkemis.init();
		PasarBinatang.init();
		Jasa.init();
		EkonomiCommands.init();
		CeritaApi.daftarJasa(Ekonomi::jasa);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			EkonomiApi.setServer(server);
			KafilahJalan.setServer(server);
			KafilahSekte.setServer(server);
			tick = 0L;
			hariTerakhir = Barang.hari(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			KafilahJalan.bersihkanSemua();
			LapakSekte.bersihkan();
			KafilahSekte.setServer(null);
			KafilahJalan.setServer(null);
			EkonomiApi.setServer(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(Ekonomi::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> KafilahSekte.bayar(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> lupakan(handler.getPlayer().getUUID()));
	}

	private static void tick(MinecraftServer server) {
		tick++;
		if (tick % 20L == 0L) {
			MisiGilda.tick(server);
			KafilahJalan.tick(server, tick);
			KafilahSekte.tick(server);
		}
		LelangAgung.tick(server, tick);
		if (tick % 1200L == 0L) {
			Hidangan.tickMenit(server);
		}
		if (tick % 100L == 0L) {
			long hari = Barang.hari(server);
			if (hari != hariTerakhir) {
				hariTerakhir = hari;
				LapakSekte.hariBaru(server, hari);
				KafilahSekte.hariBaru(server, hari);
			}
		}
	}

	private static void lupakan(UUID id) {
		Lembar.lupakan(id);
		GildaKultivator.lupakan(id);
		LapakSekte.lupakan(id);
		PesananTempa.lupakan(id);
		RumahUang.lupakan(id);
		LelangAgung.lupakan(id);
	}

	/** The economy services of a 38 NPC for the dialog's "Jasa" tab (one entry per screen the role serves). */
	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer player, KultivatorNpc npc) {
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		NpcRole role = npc.role();
		if (TokoSenjata.PERAN.contains(role)) {
			out.add(entri(LembarKunci.TokoSenjata.LAYAR, "toko_senjata", LembarKunci.Ikon.TOKO_SENJATA));
		}
		if (GildaTempa.PERAN.contains(role)) {
			out.add(entri(LembarKunci.Gilda.LAYAR_TEMPA, "gilda_tempa", LembarKunci.Ikon.GILDA_TEMPA));
		}
		if (GildaAlkemis.PERAN.contains(role)) {
			out.add(entri(LembarKunci.Gilda.LAYAR_ALKEMIS, "gilda_alkemis", LembarKunci.Ikon.GILDA_ALKEMIS));
		}
		if (GildaKultivator.PERAN.contains(role)) {
			out.add(entri(LembarKunci.GildaMisi.LAYAR, "gilda_misi", LembarKunci.Ikon.GILDA_MISI));
		}
		switch (role) {
			case BANKIR -> out.add(entri(LembarKunci.Bank.LAYAR, "bank", LembarKunci.Ikon.BANK));
			case BANDAR_BATU, PEMOTONG_BATU -> out.add(entri(LembarKunci.JudiBatu.LAYAR, "judi_batu", LembarKunci.Ikon.JUDI_BATU));
			case PEDAGANG_BINATANG -> out.add(entri(LembarKunci.PasarBinatang.LAYAR, "pasar_binatang", LembarKunci.Ikon.PASAR_BINATANG));
			case JURU_LELANG -> out.add(entri(LembarKunci.Lelang.LAYAR, "lelang", LembarKunci.Ikon.LELANG));
			case MENTERI -> out.add(entri(LembarKunci.BukuKas.LAYAR, "buku_kas", LembarKunci.Ikon.LAPAK_SEKTE));
			case KAFILAH_BASHI, PEDAGANG_KELILING, KULI -> out.add(entri(LembarKunci.Kafilah.LAYAR, "kafilah", LembarKunci.Ikon.KAFILAH));
			case PEMILIK_PENGINAPAN -> {
				out.add(entri(LembarKunci.Layanan.LAYAR_PENGINAPAN, "penginapan", LembarKunci.Ikon.PENGINAPAN));
				out.add(entri(LembarKunci.Layanan.LAYAR_RUMAH_MAKAN, "rumah_makan", LembarKunci.Ikon.RUMAH_MAKAN));
			}
			case PELAYAN -> {
				out.add(entri(LembarKunci.Layanan.LAYAR_RUMAH_MAKAN, "rumah_makan", LembarKunci.Ikon.RUMAH_MAKAN));
				out.add(entri(LembarKunci.Layanan.LAYAR_PENGINAPAN, "penginapan", LembarKunci.Ikon.PENGINAPAN));
			}
			case KOKI -> out.add(entri(LembarKunci.Layanan.LAYAR_RUMAH_MAKAN, "rumah_makan", LembarKunci.Ikon.RUMAH_MAKAN));
			case TABIB -> out.add(entri(LembarKunci.Layanan.LAYAR_JASA, "jasa.tabib", LembarKunci.Ikon.TABIB));
			case RAHIB -> out.add(entri(LembarKunci.Layanan.LAYAR_JASA, "jasa.kuil", LembarKunci.Ikon.KUIL));
			case PUSTAKAWAN -> out.add(entri(LembarKunci.Layanan.LAYAR_JASA, "jasa.perpustakaan", LembarKunci.Ikon.PERPUSTAKAAN));
			default -> {
			}
		}
		if (!EkonomiConfig.get().toko && !out.isEmpty()) {
			List<TempatDialog.JasaEntri> closed = new ArrayList<>();
			for (TempatDialog.JasaEntri each : out) {
				closed.add(new TempatDialog.JasaEntri(each.layar(), each.nama(), each.ikon(), each.catatan(), true,
						Component.translatable("kultivator.tempat_ekonomi.tutup")));
			}
			return closed;
		}
		return out;
	}

	private static TempatDialog.JasaEntri entri(String layar, String nama, int ikon) {
		return new TempatDialog.JasaEntri(layar, Component.translatable("kultivator.tempat_ekonomi.layanan." + nama), ikon,
				Component.translatable("kultivator.tempat_ekonomi.layanan." + nama + ".catatan"), false, null);
	}

	private Ekonomi() {
	}
}
