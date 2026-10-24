package dev.kultivator.sekte.mandiri.kerja;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The voice of module 39D towards the diary, the hall news and the master (DESIGN 39.7.5, 39.8.1).
 * <p>
 * <b>Diary.</b> Entries carry a FULL key of this fragment, {@code kultivator.sekte.mandiri.kerja.harian.<inti>.<n>}: the
 * variant {@code n} is chosen here with the seed {@code (soul, day, inti)} (so an entry always reads the same) out of the
 * count in {@link #VARIAN}, and 39E's composer shows the sentence as the core of the entry with the real arguments (item
 * names as lang keys, partner names, numbers).
 * <p>
 * <b>News and notices.</b> {@link #kabar} adds a line to the hall feed ({@code MandiriApi.kabar}); {@link #beritahu} also
 * tells the master when the policy wants that kind ({@code MandiriApi.beritahu}: 39E's notices, else chat while not busy).
 */
final class KabarKerja {

	/** Variants of each diary core (keys {@code harian.<inti>.1..n} in {@code lang-src/sekte_mandiri_kerja.*}). */
	static final Map<String, Integer> VARIAN = new HashMap<>();

	static {
		VARIAN.put("latih.selesai", 4);
		VARIAN.put("latih.naik", 4);
		VARIAN.put("latih.kejar", 4);
		VARIAN.put("tanding.menang", 4);
		VARIAN.put("tanding.kalah", 4);
		VARIAN.put("tanding.seri", 4);
		VARIAN.put("meramu.berhasil", 4);
		VARIAN.put("meramu.sempurna", 4);
		VARIAN.put("meramu.gagal", 4);
		VARIAN.put("meramu.terganggu", 4);
		VARIAN.put("jimat.berhasil", 4);
		VARIAN.put("jimat.gagal", 4);
		VARIAN.put("jimat.naik", 4);
		VARIAN.put("kebun.selesai", 4);
		VARIAN.put("tambang.selesai", 4);
		VARIAN.put("pos.water", 4);
		VARIAN.put("pos.garden", 4);
		VARIAN.put("pos.kitchen", 4);
		VARIAN.put("pos.farm", 4);
		VARIAN.put("formasi.jaga", 4);
		VARIAN.put("formasi.perkuat", 4);
		VARIAN.put("teh.selesai", 4);
		VARIAN.put("teh.kejar", 4);
		VARIAN.put("natal.tempa", 4);
		VARIAN.put("natal.lipat", 4);
		VARIAN.put("natal.gagal", 4);
		VARIAN.put("natal.hilang", 4);
		VARIAN.put("natal.perak", 3);
		VARIAN.put("natal.emas", 3);
		VARIAN.put("natal.abadi", 3);
	}

	/** The full diary key of {@code inti} for this soul and day. */
	static String kunci(UUID soul, long hari, String inti) {
		int n = Math.max(1, VARIAN.getOrDefault(inti, 1));
		int pick = Math.floorMod((soul.hashCode() * 31L + hari) * 31L + inti.hashCode(), n) + 1;
		return DataKerja.P + "harian." + inti + "." + pick;
	}

	/** Writes a diary entry of {@code e} with the core {@code inti} (args: names, lang keys of items, numbers). */
	static void catat(DiscipleEntity e, String jenis, String inti, EntriHarian.Suasana suasana, boolean penting, String... args) {
		UUID owner = e.ownerId();
		if (owner == null) {
			return;
		}
		MinecraftServer server = e.level().getServer();
		long hari = DataKerja.hari(server);
		catatPada(owner, e.soulId(), hari, DataKerja.jam(server), jenis, inti, suasana, penting, args);
	}

	/** A diary entry dated {@code hari} (the off-screen catch-up writes past days). */
	static void catatPada(UUID owner, UUID soul, long hari, int jam, String jenis, String inti, EntriHarian.Suasana suasana,
			boolean penting, String... args) {
		List<String> list = args == null ? List.of() : Arrays.asList(args);
		MandiriApi.catat(owner, soul, new EntriHarian(hari, jam, jenis, kunci(soul, hari, inti), list, suasana, penting));
	}

	/** A hall news line {@code kultivator.sekte.mandiri.kerja.kabar.<key>}. */
	static void kabar(DiscipleEntity e, String key, boolean penting, String... args) {
		UUID owner = e.ownerId();
		MinecraftServer server = e.level().getServer();
		if (owner != null && server != null) {
			MandiriApi.kabar(server, owner, e.soulId(), DataKerja.P + "kabar." + key, penting, args);
		}
	}

	/** Tells the master (when the policy wants {@code jenis}) and the elder's morning report. */
	static void beritahu(DiscipleEntity e, Kebijakan.Pemberitahuan jenis, Component pesan, boolean penting) {
		UUID owner = e.ownerId();
		MinecraftServer server = e.level().getServer();
		if (owner != null && server != null) {
			MandiriApi.beritahu(server, owner, e.soulId(), jenis, pesan, penting);
		}
	}

	/** A line of 39D in a colour. */
	static MutableComponent baris(String key, int color, Object... args) {
		return Component.translatable(DataKerja.P + key, args).withColor(color);
	}

	/** The disciple's name in the warm parchment colour of the old chat lines. */
	static Component nama(DiscipleEntity e) {
		return e.getName().copy().withColor(0xF2E6C8);
	}

	/** One of the disciple's recorded voice lines (it keeps its own cooldown and the master's gap). */
	static void suara(DiscipleEntity e, String line, int cooldown) {
		VoiceDisciples.line(e, line, cooldown);
	}

	private KabarKerja() {
	}
}
