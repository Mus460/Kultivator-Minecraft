package dev.kultivator.dunia.kota42.bicara;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.api.JenisUpacara;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Ingatan;
import dev.kultivator.dunia.kota42.warga.Kebiasaan;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.cerita.Syarat;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.hidup.Pemanggil;
import dev.kultivator.dunia.tempat.hidup.TempatHuni;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The quest goal kinds of Kota yang Hidup (DESIGN 42.10 "tujuan baru 42B") for the 38 quest engine
 * ({@code data/kultivator/tempat/quest/kota42_*.json}, written by 42B-C), plus three story conditions and effects for the
 * 42 dialogs. A goal is written {@code "<kind> <token> [xN]"}; the event id is a list of {@code key:value} tokens joined by
 * {@code ;} and a goal matches when its token is one of them (or {@code *}):
 * <pre>
 * bicara_warga   warga:&lt;id&gt; | peran:&lt;role id&gt; | tokoh:&lt;id&gt; | situs:&lt;site&gt; | kebiasaan:&lt;habit&gt; | tahap:&lt;anak|remaja|dewasa|tua&gt; | *
 *                a talk opened with a resident (42.5.1)
 * gosip_telusur  templat:&lt;predicate&gt; | gosip:&lt;id&gt; | *          a rumour traced back to its first witness (42.5.4)
 * hadir_upacara  jenis:&lt;nikah|makam|lahir|sebulan|zhuazhou&gt; | situs:&lt;site&gt; | *   inside the place while a ceremony runs
 * bayar_utang    warga:&lt;id&gt; | tokoh:&lt;id&gt; | *                  a loan from a resident paid off (42.5.7)
 * perkara        templat:&lt;case&gt; | putusan:&lt;id&gt; | *            a Yamen case decided by the player (42C calls {@link #perkara})
 * kavling_tahap  tahap:&lt;0-4&gt; | situs:&lt;site&gt; | *                a growth plot of the place reached a stage (42F, KotaBus.KAVLING)
 * kejar_copet    situs:&lt;site&gt; | *                                a pickpocket caught (42D-P calls {@link #kejarCopet})
 * ronda_geng     situs:&lt;site&gt; | malam:&lt;n&gt; | *                  a night watch round walked (42D calls {@link #ronda})
 * tolong         jenis:&lt;request kind&gt; | *                         a resident's request done (42.5.6)
 * beri_warga     rasa:&lt;taste&gt; | item:&lt;item id&gt; | warga:&lt;id&gt; | tokoh:&lt;id&gt; | *   a gift handed over (42.5.7)
 * </pre>
 * Conditions: {@code kota_kesan <n>} (the talking resident's impression of the player &ge; n), {@code kota_kenal} (it
 * knows the player's name), {@code kota_tolong <jenis>} (the player ever finished such a request). Effects:
 * {@code kota_kesan <delta>} (changes that impression), {@code kota_tolong <jenis>} (the talking resident asks for it now),
 * {@code kota_kenal} (the resident learns the player's name).
 */
public final class TujuanKota {

	public static final List<String> JENIS = List.of("bicara_warga", "gosip_telusur", "hadir_upacara", "bayar_utang", "perkara",
			"kavling_tahap", "kejar_copet", "ronda_geng", "tolong", "beri_warga");

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		for (String jenis : JENIS) {
			QuestTracker.daftarTujuan(jenis, (args, j, id) -> j.equals(jenis) && cocok(args, id));
		}
		KotaBus.UPACARA.register((server, situs, jenis, id, fase) -> {
			if (fase == 1) {
				diTempat(server, situs, "hadir_upacara", "jenis:" + jenis.name().toLowerCase(Locale.ROOT) + ";situs:" + situs);
			}
		});
		KotaBus.KAVLING.register((server, situs, kavling, tahap) -> diTempat(server, situs, "kavling_tahap", "tahap:" + tahap + ";situs:" + situs));
		Syarat.daftar("kota_kesan", (player, args) -> {
			Warga w = wargaBicara(player);
			return w != null && !args.isEmpty() && WargaApi.ingatan(w, player.getUUID()).kesan >= angka(args.get(0));
		});
		Syarat.daftar("kota_kenal", (player, args) -> {
			Warga w = wargaBicara(player);
			return w != null && WargaApi.ingatan(w, player.getUUID()).nama;
		});
		Syarat.daftar("kota_tolong", (player, args) -> !args.isEmpty() && CeritaApi.bendera(player, "kota42.tolong." + args.get(0)));
		Akibat.daftar("kota_kesan", (player, args) -> {
			Warga w = wargaBicara(player);
			if (w != null && !args.isEmpty()) {
				WargaApi.ubahKesan(w, player, angka(args.get(0)), "cerita", null);
			}
		});
		Akibat.daftar("kota_tolong", (player, args) -> {
			Warga w = wargaBicara(player);
			if (w != null && !args.isEmpty()) {
				PermintaanWarga.paksa(w, args.get(0));
			}
		});
		Akibat.daftar("kota_kenal", (player, args) -> {
			Warga w = wargaBicara(player);
			MinecraftServer server = player.level().getServer();
			if (w != null) {
				Ingatan i = WargaApi.ingatan(w, player.getUUID());
				i.nama = true;
				WargaData.get(server).ubah();
			}
		});
	}

	private static boolean cocok(List<String> args, String id) {
		if (args.isEmpty() || "*".equals(args.get(0))) {
			return true;
		}
		String want = args.get(0);
		for (String token : id.split(";")) {
			if (token.equals(want)) {
				return true;
			}
		}
		return false;
	}

	private static int angka(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** The resident behind the body of the player's open talk, or null. */
	private static @Nullable Warga wargaBicara(ServerPlayer player) {
		KultivatorNpc npc = TempatDialog.npc(player);
		return npc == null ? null : WargaApi.dari(npc);
	}

	/** An event for every online player standing inside the place's box. */
	private static void diTempat(MinecraftServer server, long situs, String jenis, String id) {
		TempatHuni huni = Pemanggil.huni(situs);
		if (huni == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension() == huni.dimensi && huni.kotak.isInside(player.blockPosition())) {
				QuestTracker.peristiwa(player, jenis, id);
			}
		}
	}

	// ================================================================== events fired by 42B

	/** A talk with {@code w} opened (42.5.1). */
	static void bicara(ServerPlayer player, Warga w) {
		MinecraftServer server = player.level().getServer();
		StringBuilder id = new StringBuilder("warga:").append(w.id).append(";peran:").append(w.peranNpc().id())
				.append(";situs:").append(WargaData.get(server).situsDari(w.id))
				.append(";tahap:").append(Selera.tahapGolongan(WargaApi.umur(w)));
		if (!w.tokoh.isEmpty()) {
			id.append(";tokoh:").append(w.tokoh);
		}
		for (int k : new int[] {w.kebiasaan1, w.kebiasaan2}) {
			if (k > 0) {
				id.append(";kebiasaan:").append(Kebiasaan.id(k));
			}
		}
		QuestTracker.peristiwa(player, "bicara_warga", id.toString());
	}

	/** A rumour traced to its first witness. */
	static void gosipTelusur(ServerPlayer player, String templat, int gosip) {
		QuestTracker.peristiwa(player, "gosip_telusur", "templat:" + templat + ";gosip:" + gosip);
	}

	/** A request of kind {@code jenis} done; also remembered as the story flag {@code kota42.tolong.<jenis>}. */
	static void tolong(ServerPlayer player, String jenis) {
		CeritaApi.setBendera(player, "kota42.tolong." + jenis, true);
		QuestTracker.peristiwa(player, "tolong", "jenis:" + jenis);
	}

	/** A gift handed over. */
	static void beri(ServerPlayer player, Warga w, ItemStack stack) {
		StringBuilder id = new StringBuilder("warga:").append(w.id).append(";item:").append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
		if (!w.tokoh.isEmpty()) {
			id.append(";tokoh:").append(w.tokoh);
		}
		String rasa = Selera.nilai(w, stack) > 0 ? Selera.rasaSuka(w, stack) : null;
		if (rasa != null) {
			id.append(";rasa:").append(rasa);
		}
		QuestTracker.peristiwa(player, "beri_warga", id.toString());
	}

	/** A loan from {@code w} paid off. */
	static void bayarUtang(ServerPlayer player, Warga w) {
		QuestTracker.peristiwa(player, "bayar_utang", "warga:" + w.id + (w.tokoh.isEmpty() ? "" : ";tokoh:" + w.tokoh));
	}

	// ================================================================== events other modules fire (hand-offs)

	/** 42C ({@code Perkara}): the player decided case {@code templat} with verdict {@code putusan}. */
	public static void perkara(ServerPlayer player, String templat, String putusan) {
		if (player != null) {
			QuestTracker.peristiwa(player, "perkara", "templat:" + templat + ";putusan:" + putusan);
		}
	}

	/** 42D-P ({@code Copet}): the player caught a pickpocket in place {@code situs}. */
	public static void kejarCopet(ServerPlayer player, long situs) {
		if (player != null) {
			QuestTracker.peristiwa(player, "kejar_copet", "situs:" + situs);
		}
	}

	/** 42D ({@code Ronda}): the player walked night watch round number {@code malam} (1-based) in place {@code situs}. */
	public static void ronda(ServerPlayer player, long situs, int malam) {
		if (player != null) {
			QuestTracker.peristiwa(player, "ronda_geng", "situs:" + situs + ";malam:" + malam);
		}
	}

	/** {@code jenis} as a goal token ({@code nikah}...). */
	public static String token(JenisUpacara jenis) {
		return "jenis:" + jenis.name().toLowerCase(Locale.ROOT);
	}

	private TujuanKota() {
	}
}
