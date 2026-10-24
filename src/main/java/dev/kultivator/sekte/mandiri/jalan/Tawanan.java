package dev.kultivator.sekte.mandiri.jalan;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.mandiri.MandiriConfig;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JenisLuka;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriBus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Captured (DESIGN 39.5.9 "Tertawan", 39.6.2): bandits, a rival sect or the guards of a black-market raid hold the party
 * at a real place (the 38 bandit camp, the road, the town gaol) for three world days. The place becomes a live map site
 * {@code mandiri/tawanan} (red camp icon), the master gets the coordinates with [Tebus] (ransom {@code 30 x (band + 1)}
 * NB from the stones in his inventory) and [Lacak]; a bound body kneels there while he is near ({@link Jelmaan}) and his
 * right-click frees the party at once. Three days without help: the disciples break out badly wounded, remember that
 * nobody came ({@code DIABAIKAN}) and walk home. A Return Talisman in the bag avoids it all. Never death.
 */
final class Tawanan {

	/** Days a captive waits. */
	static final long HARI = 3L;

	/** Captures the party at {@code pos} during stage {@code k}. */
	static void tangkap(Tahap.Konteks k, String sebab, BlockPos pos) {
		Rencana r = k.r();
		if (r.tertawan()) {
			return;
		}
		List<String> args = List.of(k.namaPemimpin());
		if (r.anggota(r.soul()).getBooleanOr("jimatKembali", false) && !r.hasil().getBooleanOr("jimatKembaliDipakai", false)) {
			r.hasil().putBoolean("jimatKembaliDipakai", true);
			for (UUID soul : r.rombongan()) {
				r.lukai(soul, JenisLuka.TERLUKA);
			}
			r.kisah(JalanIds.k("kisah.tawanan.jimat"), args);
			Tahap.catat(k, r.soul(), "tawanan", JalanIds.k("harian.tawanan.jimat"), args, EntriHarian.Suasana.LELAH, true);
			pulangDari(k.level(), r, k.index(), k.sampai(), pos);
			Kepulangan.aturPulang(k.server(), r);
			return;
		}
		long sampai = k.sampai() + HARI * 24000L;
		int band = r.anggota(r.soul()).getIntOr("band", 0);
		CompoundTag t = new CompoundTag();
		t.putLong("pos", pos.asLong());
		t.putLong("sampai", sampai);
		t.putString("sebab", sebab);
		t.putLong("tebusan", (long) MandiriConfig.get().harga.tebusanPerBand * (band + 1L));
		DuniaSites.Site site = DuniaSites.publishLive(k.level(), JalanIds.SITUS_TAWANAN, pos, Math.max(1, band), sampai);
		t.putLong("situs", site.id());
		r.tag().put(Rencana.TAWANAN, t);
		for (UUID soul : r.rombongan()) {
			r.lukai(soul, JenisLuka.TERTAWAN);
		}
		r.potong(k.index());
		CompoundTag held = Tahap.stage(Tahap.TAWANAN, k.sampai(), sampai, pos, 0);
		r.tahap().add(held);
		Tahap.tambahPulang(k.level(), r, pos, sampai);
		Kepulangan.aturPulang(k.server(), r);
		r.kisah(JalanIds.k("kisah.tawanan." + sebab), args);
		Tahap.catat(k, r.soul(), "tawanan", JalanIds.k("harian.tawanan.tangkap"), args, EntriHarian.Suasana.SEDIH, true);
		MandiriBus.tertawan(k.server(), r.owner(), r.soul(), Long.toString(site.id()));
		MandiriApi.kabar(k.server(), r.owner(), r.soul(), JalanIds.k("kabar.tawanan"), true, k.namaPemimpin(),
				String.valueOf(pos.getX()), String.valueOf(pos.getZ()));
		MandiriApi.beritahu(k.server(), r.owner(), r.soul(), Kebijakan.Pemberitahuan.TERTAWAN, pesan(r, pos), true);
	}

	/** The master's notice: where, the ransom, and the two buttons. */
	static Component pesan(Rencana r, BlockPos pos) {
		long ransom = r.tawanan().getLongOr("tebusan", 0L);
		MutableComponent line = JalanIds.t("pesan.tertawan", Component.literal(r.nama()).withColor(JalanIds.WARNA_NAMA), pos.getX(), pos.getY(),
				pos.getZ()).withColor(JalanIds.WARNA_MERAH);
		line.append(" ").append(tombol(JalanIds.k("tombol.tebus"), "/kultivasi perguruan mandiri jelmaan tebus " + r.soul(),
				JalanIds.k("tombol.tebus.hover"), JalanIds.WARNA_EMAS, ransom));
		line.append(" ").append(tombol(JalanIds.k("tombol.lacak"), "/kultivasi perguruan mandiri jelmaan lacak " + r.soul(),
				JalanIds.k("tombol.lacak.hover"), JalanIds.WARNA_GIOK));
		return line;
	}

	static MutableComponent tombol(String labelKey, String command, String hoverKey, int color, Object... args) {
		return Component.literal("[").append(Component.translatable(labelKey, args)).append("]").withStyle(style -> style.withColor(color)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(hoverKey))));
	}

	/** The captivity ran out (the {@code tawanan} stage resolved): the party breaks out badly wounded. */
	static void lolos(Tahap.Konteks k) {
		Rencana r = k.r();
		CompoundTag t = r.tawanan();
		if (t.isEmpty() || t.getBooleanOr("bebas", false)) {
			return;
		}
		t.putBoolean("bebas", true);
		t.putBoolean("lolosSendiri", true);
		DuniaSites.endLive(t.getLongOr("situs", 0L));
		for (UUID soul : r.rombongan()) {
			r.sembuhkan(soul);
			r.lukai(soul, JenisLuka.BERAT);
			SekteBus.mistreated(k.server(), r.owner(), soul, Perlakuan.DIABAIKAN, "mandiri_tertawan");
		}
		r.kenangan("mandiri.tidak_ditolong");
		List<String> args = List.of(k.namaPemimpin());
		r.kisah(JalanIds.k("kisah.tawanan.lolos"), args);
		Tahap.catat(k, r.soul(), "tawanan", JalanIds.k("harian.tawanan.lolos"), args, EntriHarian.Suasana.SEDIH, true);
	}

	/** The master frees the party in person ({@code dekat}: he stands within 24 blocks of the place). */
	static boolean bebaskan(ServerPlayer master, Rencana r, boolean dekat) {
		CompoundTag t = r.tawanan();
		if (!r.tertawan() || !dekat) {
			return false;
		}
		MinecraftServer server = master.level().getServer();
		BlockPos pos = BlockPos.of(t.getLongOr("pos", 0L));
		lepas(server, r, "diselamatkan");
		r.panggil(pos);
		long now = server.overworld().getGameTime();
		r.potong(r.berikut() - 1 < 0 ? 0 : r.berikut() - 1);
		r.setPulang(now);
		Kepulangan.aturPulang(server, r);
		return true;
	}

	/** The master pays the ransom: the party is let go and walks home from the place. */
	static boolean tebus(ServerPlayer master, Rencana r) {
		CompoundTag t = r.tawanan();
		if (!r.tertawan()) {
			return false;
		}
		long price = t.getLongOr("tebusan", 0L);
		if (!NilaiBatu.pay(master, price)) {
			return false;
		}
		MinecraftServer server = master.level().getServer();
		ServerLevel level = Tahap.level(server, r);
		lepas(server, r, "ditebus");
		long now = server.overworld().getGameTime();
		int i = Math.max(0, r.berikut() - 1);
		r.potong(i);
		if (level != null) {
			Tahap.tambahPulang(level, r, BlockPos.of(t.getLongOr("pos", 0L)), now);
		} else {
			r.setPulang(now);
		}
		Kepulangan.aturPulang(server, r);
		return true;
	}

	private static void lepas(MinecraftServer server, Rencana r, String how) {
		CompoundTag t = r.tawanan();
		t.putBoolean("bebas", true);
		t.putString("cara", how);
		DuniaSites.endLive(t.getLongOr("situs", 0L));
		for (UUID soul : r.rombongan()) {
			r.sembuhkan(soul);
			r.lukai(soul, JenisLuka.TERLUKA);
		}
		r.kenangan("mandiri." + how);
		r.kisah(JalanIds.k("kisah.tawanan." + how), List.of(r.nama()));
		long hari = dev.kultivator.dunia.kota.Kota.day(server);
		int jam = dev.kultivator.sekte.mandiri.Irama.jam(server.overworld());
		MandiriApi.catat(r.owner(), r.soul(), new EntriHarian(hari, jam, "tawanan", JalanIds.k("harian.tawanan." + how), List.of(r.nama()),
				EntriHarian.Suasana.SENANG, true));
		try {
			JalanData.get(server).setDirty();
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.debug("Mandiri: captive save failed", exception);
		}
	}

	/** Replaces the rest of the plan with the road home from {@code pos} starting at {@code t}. */
	private static void pulangDari(ServerLevel level, Rencana r, int index, long t, BlockPos pos) {
		r.potong(index);
		Tahap.tambahPulang(level, r, pos, t);
	}

	/** The captive record of a soul's journey (its leader's), or null. */
	static @Nullable Rencana dari(MinecraftServer server, UUID soul) {
		return Perjalanan.rencana(server, soul);
	}

	private Tawanan() {
	}
}
