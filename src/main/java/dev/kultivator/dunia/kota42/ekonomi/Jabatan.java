package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.CeritaReload;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Town offices of players (DESIGN 42.6.6, contract C42.C1), per place: 34 towns, themed cities and the capital; villages
 * and posts only ranks 1-2 ("Tetua Kehormatan"). Rank 5 is Wali Kota, in the capital Menteri Kehormatan by Palace Order
 * (K5; never a prince). The item {@code lencana_pejabat} carries the component {@code kultivator:jabatan} (place, rank).
 * <p>
 * <b>Requirements</b> ({@link #syarat}): Nama Baik of the place 40/55/70/80/90; rank 2 three town tasks there
 * ({@link #catatTugas}: city orders, contract deliveries, resident requests of 42B, inspections); rank 3 Foundation; rank 4
 * Golden Core and the quest {@value #QUEST_BANJIR}; rank 5 Nascent Soul and the elders' blessing (asked with
 * {@code ajukan}); rank 1 the quest {@value #QUEST_CATATAN}. A quest that no data pack defines yet does not block.
 * One Wali Kota per place; at most two places where a player holds rank 3 or more.
 * <p>
 * <b>Duties</b>: rank 2 checks the scales once a week (one market trader of the place cheats each week, chosen by seed;
 * {@link #periksa} the right one: fine 20 NB to the Kas Kota and a task; a wrong accusation costs his goodwill); rank 3
 * judges two cases a week ({@link Perkara}); rank 4 changes the tax by +-2 and picks the next plot plan
 * ({@link #pilihRencana}, read by 42F); rank 5 sets the tax 5-20 %, the festival budget and adds guards.
 * <b>Wages</b> from the Kas Kota each week: 1/2/3/5 % of the week's town income for ranks 2-5, capped at
 * {@code 20 x (b+1) x rank x kotaEkonomi.gajiSkala} NB, paid into the drawer ({@link Investasi#keLaci}). <b>Absence</b>:
 * not in the place for 14 days gives a warning, 28 days drops one rank.
 * <p>
 * State: {@code KotaEkonomiData.situs("jabatan", situs)["p"][uuid]}: {@code r} rank, {@code h} last day present,
 * {@code w} warned, {@code tm} task week, {@code tp}/{@code tk} inspections/cases this week, {@code gm} last wage,
 * {@code n} town tasks; place keys {@code rc} the plot plan picked by the deputy, {@code pj} extra guards hired.
 * Per player {@code bagian("jabatan")["pemain"][uuid]}: {@code d} cases judged, {@code s} bribes taken, {@code a} audits,
 * {@code j} judged in a row without a bribe.
 */
public final class Jabatan {

	public static final int TANPA = 0;
	/** Juru Tulis Kehormatan: Nama Baik >= 40, quest "Catatan Kota"; 3 % discount. */
	public static final int JURU_TULIS = 1;
	/** Pengawas Pasar: >= 55, 3 town tasks; checks scales weekly; 1 % of the weekly tax. */
	public static final int PENGAWAS_PASAR = 2;
	/** Hakim Pembantu: >= 70, Foundation+; judges 2 cases a week; 2 %. */
	public static final int HAKIM_PEMBANTU = 3;
	/** Wakil Wali Kota: >= 80, Golden Core+, quest "Banjir Musim Semi"; picks the next plot plan, tax +-2; 3 %. */
	public static final int WAKIL = 4;
	/** Wali Kota (capital: Menteri Kehormatan): >= 90, Nascent Soul+; sets tax 5-20 %, growth plans; 5 %. */
	public static final int WALI_KOTA = 5;

	public static final String QUEST_CATATAN = "kota42_catatan_kota";
	public static final String QUEST_BANJIR = "kota42_banjir_musim_semi";

	private static final int[] NAMA_BAIK = {0, 40, 55, 70, 80, 90};
	private static final Realm[] RANAH = {Realm.MORTAL, Realm.MORTAL, Realm.MORTAL, Realm.FOUNDATION, Realm.GOLDEN_CORE, Realm.NASCENT_SOUL};
	private static final int[] GAJI_PERSEN = {0, 0, 1, 2, 3, 5};
	/** Town tasks needed for rank 2. */
	public static final int TUGAS_PANGKAT_2 = 3;
	public static final int TEMPAT_TINGGI_MAKS = 2;
	public static final int PERIKSA_MINGGU = 1;
	public static final int PERIKSA_MAKS = 3;
	public static final int PERKARA_MINGGU = 2;
	public static final int PERINGATAN_HARI = 14;
	public static final int TURUN_HARI = 28;
	public static final long DENDA_CURANG = 20L;
	public static final long GAJI_DASAR = 20L;
	/** Extra guards a Wali Kota may hire (paid 3 NB a week each by the Kas Kota). */
	public static final int PENJAGA_MAKS = 6;

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
	}

	public static boolean aktif() {
		return KotaConfig.ekonomi().aktif && KotaConfig.ekonomi().jabatan;
	}

	// ================================================================== reading

	private static CompoundTag pemegang(MinecraftServer server, long situs) {
		CompoundTag site = KotaEkonomiData.get(server).situs(KotaEkonomiData.JABATAN, situs);
		CompoundTag p = site.getCompoundOrEmpty("p");
		site.put("p", p);
		return p;
	}

	private static CompoundTag entri(MinecraftServer server, long situs, UUID player) {
		CompoundTag p = pemegang(server, situs);
		CompoundTag e = p.getCompoundOrEmpty(player.toString());
		p.put(player.toString(), e);
		return e;
	}

	private static CompoundTag catatan(MinecraftServer server, UUID player) {
		CompoundTag all = KotaEkonomiData.get(server).bagian(KotaEkonomiData.JABATAN).getCompoundOrEmpty("pemain");
		CompoundTag mine = all.getCompoundOrEmpty(player.toString());
		all.put(player.toString(), mine);
		KotaEkonomiData.get(server).bagian(KotaEkonomiData.JABATAN).put("pemain", all);
		return mine;
	}

	/** The office rank of {@code pemain} in place {@code situs} ({@link #TANPA} when none). */
	public static int pangkat(UUID pemain, long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || pemain == null) {
			return TANPA;
		}
		CompoundTag p = KotaEkonomiData.get(server).situs(KotaEkonomiData.JABATAN, situs).getCompoundOrEmpty("p");
		return Mth.clamp(p.getCompoundOrEmpty(pemain.toString()).getIntOr("r", TANPA), TANPA, WALI_KOTA);
	}

	/** The highest rank a place allows: villages and posts 2, towns 5. */
	public static int pangkatMaks(MinecraftServer server, long situs) {
		String kind = PajakKota.jenisTempat(server, situs);
		return PajakKota.JT_KAMPUNG.equals(kind) || PajakKota.JT_POS.equals(kind) || PajakKota.JT_LAIN.equals(kind) ? PENGAWAS_PASAR : WALI_KOTA;
	}

	/** The title of rank {@code pangkat} at place {@code situs} (village "Tetua Kehormatan", capital "Menteri Kehormatan"). */
	public static Component gelar(MinecraftServer server, long situs, int pangkat) {
		if (pangkat <= TANPA) {
			return Component.translatable("kultivator.kota42.jabatan.gelar.0");
		}
		String kind = PajakKota.jenisTempat(server, situs);
		if (pangkatMaks(server, situs) == PENGAWAS_PASAR) {
			return Component.translatable("kultivator.kota42.jabatan.gelar.tetua." + pangkat);
		}
		if (pangkat == WALI_KOTA && PajakKota.JT_IBU_KOTA.equals(kind)) {
			return Component.translatable("kultivator.kota42.jabatan.gelar.menteri");
		}
		return Component.translatable("kultivator.kota42.jabatan.gelar." + pangkat);
	}

	/** The discount an office gives at the place's shops (rank 1: 3 %, rank 2+: 5 %), for 42C's sellers. */
	public static double diskon(ServerPlayer player, long situs) {
		int rank = situs != 0L && aktif() ? pangkat(player.getUUID(), situs) : TANPA;
		return rank >= PENGAWAS_PASAR ? 0.05D : rank == JURU_TULIS ? 0.03D : 0.0D;
	}

	/** Places where the player holds rank 3 or more. */
	public static int tempatTinggi(MinecraftServer server, UUID player) {
		int n = 0;
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.JABATAN);
		for (String key : part.keySet()) {
			if (key.startsWith("kota_") && part.getCompoundOrEmpty(key).getCompoundOrEmpty("p").getCompoundOrEmpty(player.toString())
					.getIntOr("r", 0) >= HAKIM_PEMBANTU) {
				n++;
			}
		}
		return n;
	}

	/** Every office of the player: place id -> rank. */
	public static java.util.Map<Long, Integer> semua(MinecraftServer server, UUID player) {
		java.util.Map<Long, Integer> out = new java.util.LinkedHashMap<>();
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.JABATAN);
		for (String key : part.keySet()) {
			if (!key.startsWith("kota_")) {
				continue;
			}
			int rank = part.getCompoundOrEmpty(key).getCompoundOrEmpty("p").getCompoundOrEmpty(player.toString()).getIntOr("r", 0);
			if (rank > 0) {
				try {
					out.put(Long.parseLong(key.substring(5)), rank);
				} catch (NumberFormatException ignored) {
					// not a place section
				}
			}
		}
		return out;
	}

	/** The name of the place's Wali Kota (for the gate board and the town book), or null. */
	public static @Nullable String waliKota(MinecraftServer server, long situs) {
		CompoundTag p = pemegang(server, situs);
		for (String key : p.keySet()) {
			if (p.getCompoundOrEmpty(key).getIntOr("r", 0) >= WALI_KOTA) {
				String name = p.getCompoundOrEmpty(key).getStringOr("nm", "");
				return name.isEmpty() ? key : name;
			}
		}
		return null;
	}

	/** The plot plan the deputy picked for the next plot (42F reads it), "" for none. */
	public static String pilihanRencana(MinecraftServer server, long situs) {
		return KotaEkonomiData.get(server).situs(KotaEkonomiData.JABATAN, situs).getStringOr("rc", "");
	}

	/** Rank 4+: picks the next plot plan {@code rencana} (42F validates the id when it builds). */
	public static @Nullable Component pilihRencana(ServerPlayer player, long situs, String rencana) {
		if (pangkat(player.getUUID(), situs) < WAKIL) {
			return Component.translatable("kultivator.kota42.pajak.tak_berhak");
		}
		KotaEkonomiData.get(player.level().getServer()).situs(KotaEkonomiData.JABATAN, situs).putString("rc", rencana == null ? "" : rencana);
		KotaEkonomiData.get(player.level().getServer()).ubah();
		return null;
	}

	/** Rank 5: hires one more guard (Kas Kota pays 3 NB a week, at most 6). */
	public static @Nullable Component rekrutPenjaga(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		if (pangkat(player.getUUID(), situs) < WALI_KOTA) {
			return Component.translatable("kultivator.kota42.pajak.tak_berhak");
		}
		CompoundTag site = KotaEkonomiData.get(server).situs(KotaEkonomiData.JABATAN, situs);
		int n = site.getIntOr("pj", 0);
		if (n >= PENJAGA_MAKS) {
			return Component.translatable("kultivator.kota42.jabatan.penjaga_penuh", PENJAGA_MAKS);
		}
		site.putInt("pj", n + 1);
		KotaEkonomiData.get(server).ubah();
		return null;
	}

	/** Extra guards hired by the Wali Kota (42D's night watch adds them to the round). */
	public static int penjagaTambahan(MinecraftServer server, long situs) {
		return KotaEkonomiData.get(server).situs(KotaEkonomiData.JABATAN, situs).getIntOr("pj", 0);
	}

	// ================================================================== requirements

	/** One requirement line: met, text, what the player has. */
	public record Syarat(boolean ok, Component teks, Component punya) {
	}

	/** The requirements of rank {@code pangkat} at place {@code situs} for {@code player}. */
	public static List<Syarat> syarat(ServerPlayer player, long situs, int pangkat) {
		MinecraftServer server = player.level().getServer();
		List<Syarat> out = new ArrayList<>();
		int rank = Mth.clamp(pangkat, JURU_TULIS, WALI_KOTA);
		int rep = KotaData.get(server).reputation(situs, player.getUUID());
		out.add(new Syarat(rep >= NAMA_BAIK[rank], Component.translatable("kultivator.kota42.jabatan.syarat.nama_baik", NAMA_BAIK[rank]),
				Component.literal(Integer.toString(rep))));
		Realm realm = Cultivators.get(player).realm();
		if (RANAH[rank] != Realm.MORTAL) {
			out.add(new Syarat(realm.ordinal() >= RANAH[rank].ordinal(), Component.translatable("kultivator.kota42.jabatan.syarat.ranah",
					RANAH[rank].displayName()), realm.displayName()));
		}
		if (rank == PENGAWAS_PASAR) {
			int tasks = entri(server, situs, player.getUUID()).getIntOr("n", 0);
			out.add(new Syarat(tasks >= TUGAS_PANGKAT_2, Component.translatable("kultivator.kota42.jabatan.syarat.tugas", TUGAS_PANGKAT_2),
					Component.literal(Integer.toString(tasks))));
		}
		if (rank == JURU_TULIS || rank == WAKIL) {
			String quest = rank == JURU_TULIS ? QUEST_CATATAN : QUEST_BANJIR;
			boolean done = questSelesai(player, quest);
			out.add(new Syarat(done, Component.translatable("kultivator.kota42.jabatan.syarat.quest." + quest),
					Component.translatable(done ? "kultivator.kota42.jabatan.syarat.ya" : "kultivator.kota42.jabatan.syarat.belum")));
		}
		if (rank == WALI_KOTA) {
			String wali = waliKota(server, situs);
			boolean free = wali == null || pangkat(player.getUUID(), situs) >= WALI_KOTA;
			out.add(new Syarat(free, Component.translatable("kultivator.kota42.jabatan.syarat.kursi_kosong"),
					free ? Component.translatable("kultivator.kota42.jabatan.syarat.ya") : Component.literal(wali)));
			out.add(new Syarat(rep >= NAMA_BAIK[WALI_KOTA], Component.translatable("kultivator.kota42.jabatan.syarat.restu"),
					Component.translatable(rep >= NAMA_BAIK[WALI_KOTA] ? "kultivator.kota42.jabatan.syarat.ya" : "kultivator.kota42.jabatan.syarat.belum")));
		}
		if (rank >= HAKIM_PEMBANTU && pangkat(player.getUUID(), situs) < HAKIM_PEMBANTU) {
			int high = tempatTinggi(server, player.getUUID());
			out.add(new Syarat(high < TEMPAT_TINGGI_MAKS, Component.translatable("kultivator.kota42.jabatan.syarat.tempat_tinggi", TEMPAT_TINGGI_MAKS),
					Component.literal(Integer.toString(high))));
		}
		return out;
	}

	/** Finished, or not defined by any data pack yet (then it does not block). */
	private static boolean questSelesai(ServerPlayer player, String quest) {
		return CeritaReload.quest(quest) == null || CeritaApi.tahap(player, quest) >= 999;
	}

	/** Why the player cannot rise to the next rank here, or null when he can. */
	public static @Nullable Component alasanNaik(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		if (!aktif()) {
			return Component.translatable("kultivator.kota42.jabatan.mati");
		}
		int next = pangkat(player.getUUID(), situs) + 1;
		if (next > pangkatMaks(server, situs)) {
			return Component.translatable("kultivator.kota42.jabatan.puncak");
		}
		if (dev.kultivator.dunia.tempat.kotabesar.KotaBesarApi.buronan(player, situs)) {
			return Component.translatable("kultivator.kota42.jabatan.buronan");
		}
		for (Syarat s : syarat(player, situs, next)) {
			if (!s.ok()) {
				return Component.translatable("kultivator.kota42.jabatan.kurang", s.teks());
			}
		}
		return null;
	}

	/**
	 * Applies for the next rank: starts the rank's quest when it is defined and not done yet, else rises if every
	 * requirement holds. Returns null on promotion, or what is missing.
	 */
	public static @Nullable Component ajukan(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		int next = pangkat(player.getUUID(), situs) + 1;
		if (aktif() && (next == JURU_TULIS || next == WAKIL)) {
			String quest = next == JURU_TULIS ? QUEST_CATATAN : QUEST_BANJIR;
			if (!questSelesai(player, quest) && CeritaApi.tahap(player, quest) == 0 && CeritaApi.mulaiQuest(player, quest, situs)) {
				return Component.translatable("kultivator.kota42.jabatan.quest_mulai");
			}
		}
		Component why = alasanNaik(player, situs);
		if (why != null) {
			return why;
		}
		setPangkat(server, situs, player.getUUID(), next, player.getScoreboardName());
		Component title = gelar(server, situs, next);
		Msg.title(player, title.copy().withStyle(ChatFormatting.GOLD), PajakKota.nama(server, situs).copy(), 10, 60, 20);
		if (next == WALI_KOTA) {
			Msg.chat(player, Component.translatable("kultivator.kota42.jabatan.restu_tetua").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
		}
		beriLencana(player, situs, next);
		if (next >= PENGAWAS_PASAR) {
			Advancements.grant(player, "tempat/kota42_pejabat");
		}
		if (next >= WALI_KOTA) {
			Advancements.grant(player, "tempat/kota42_wali_kota");
		}
		return null;
	}

	/** Sets an office directly (tests, demotion); 0 removes it. */
	public static void setPangkat(MinecraftServer server, long situs, UUID player, int pangkat, @Nullable String nama) {
		CompoundTag p = pemegang(server, situs);
		int rank = Mth.clamp(pangkat, TANPA, WALI_KOTA);
		if (rank == TANPA) {
			p.remove(player.toString());
		} else {
			CompoundTag e = entri(server, situs, player);
			e.putInt("r", rank);
			e.putLong("h", KotaHidup.hari(server));
			e.putBoolean("w", false);
			if (nama != null) {
				e.putString("nm", nama);
			}
		}
		KotaEkonomiData.get(server).ubah();
	}

	private static void beriLencana(ServerPlayer player, long situs, int rank) {
		ItemStack badge = new ItemStack(KotaEkonomiItems.LENCANA_PEJABAT);
		badge.set(KotaEkonomiItems.JABATAN, new KotaEkonomiItems.LencanaJabatan(situs, rank, PajakKota.nama(player.level().getServer(), situs)));
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack old = player.getInventory().getItem(slot);
			KotaEkonomiItems.LencanaJabatan was = old.get(KotaEkonomiItems.JABATAN);
			if (old.is(KotaEkonomiItems.LENCANA_PEJABAT) && was != null && was.situs() == situs) {
				player.getInventory().setItem(slot, badge);
				return;
			}
		}
		Barang.beri(player, badge);
	}

	// ================================================================== tasks and duties

	/** Counts one town task of {@code player} at {@code situs} (city orders, deliveries, requests of 42B, inspections). */
	public static void catatTugas(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag e = entri(server, situs, player.getUUID());
		e.putInt("n", e.getIntOr("n", 0) + 1);
		KotaEkonomiData.get(server).ubah();
	}

	/** Duty counter of this week ({@code "periksa"} or {@code "perkara"}). */
	public static int tugasMinggu(MinecraftServer server, long situs, UUID player, String jenis) {
		CompoundTag e = entri(server, situs, player);
		long week = Math.floorDiv(KotaHidup.hari(server), 7L);
		return e.getLongOr("tm", -1L) == week ? e.getIntOr("perkara".equals(jenis) ? "tk" : "tp", 0) : 0;
	}

	static void catatTugasMinggu(MinecraftServer server, long situs, UUID player, String jenis) {
		CompoundTag e = entri(server, situs, player);
		long week = Math.floorDiv(KotaHidup.hari(server), 7L);
		if (e.getLongOr("tm", -1L) != week) {
			e.putLong("tm", week);
			e.putInt("tp", 0);
			e.putInt("tk", 0);
		}
		String key = "perkara".equals(jenis) ? "tk" : "tp";
		e.putInt(key, e.getIntOr(key, 0) + 1);
		KotaEkonomiData.get(server).ubah();
	}

	/** This week's cheating trader of a place (seeded by place and week), or null. */
	public static @Nullable Warga pedagangCurang(MinecraftServer server, long situs) {
		List<Warga> traders = new ArrayList<>();
		for (Warga w : WargaApi.wargaTempat(situs)) {
			NpcRole role = w.peranNpc();
			if (w.hidup() && (role.kelompok == NpcRole.Kelompok.PEDAGANG || role == NpcRole.KOKI || role == NpcRole.PENJUAL_IKAN)) {
				traders.add(w);
			}
		}
		if (traders.isEmpty()) {
			return null;
		}
		long week = Math.floorDiv(KotaHidup.hari(server), 7L);
		return traders.get(RandomSource.create(situs * 8191L + week * 131071L).nextInt(traders.size()));
	}

	/**
	 * Rank 2+: inspects the scales of the trader nearest to the player (within 5 blocks). Right: fine 20 NB to the Kas
	 * Kota, a task and the week's duty; wrong: the trader's goodwill -5. At most three tries a week.
	 */
	public static @Nullable Component periksa(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		TempatInfo tempat = PajakKota.tempat(player);
		if (tempat == null) {
			return Component.translatable("kultivator.kota42.cmd.tanpa_tempat");
		}
		long situs = tempat.situsId();
		if (pangkat(player.getUUID(), situs) < PENGAWAS_PASAR) {
			return Component.translatable("kultivator.kota42.pajak.tak_berhak");
		}
		CompoundTag e = entri(server, situs, player.getUUID());
		long week = Math.floorDiv(KotaHidup.hari(server), 7L);
		if (e.getLongOr("tw", -1L) != week) {
			e.putLong("tw", week);
			e.putInt("tc", 0);
			e.putBoolean("tx", false);
		}
		if (e.getBooleanOr("tx", false)) {
			return Component.translatable("kultivator.kota42.jabatan.periksa_sudah");
		}
		if (e.getIntOr("tc", 0) >= PERIKSA_MAKS) {
			return Component.translatable("kultivator.kota42.jabatan.periksa_habis");
		}
		KultivatorNpc nearest = null;
		double best = 25.0D;
		for (KultivatorNpc npc : player.level().getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(5.0D))) {
			double d = npc.distanceToSqr(player);
			if (d < best && WargaApi.dari(npc) != null) {
				best = d;
				nearest = npc;
			}
		}
		Warga w = nearest == null ? null : WargaApi.dari(nearest);
		if (w == null) {
			return Component.translatable("kultivator.kota42.jabatan.periksa_tanpa");
		}
		e.putInt("tc", e.getIntOr("tc", 0) + 1);
		Warga cheat = pedagangCurang(server, situs);
		if (cheat != null && cheat.id == w.id) {
			e.putBoolean("tx", true);
			KasKota.pajak(server, situs, DENDA_CURANG, "denda");
			catatTugas(player, situs);
			catatTugasMinggu(server, situs, player.getUUID(), "periksa");
			WargaApi.ubahKesan(w, player, -10, "periksa", null);
			KotaData.get(server).addReputation(situs, player.getUUID(), 1);
			Msg.chat(player, Component.translatable("kultivator.kota42.jabatan.periksa_benar", WargaApi.nama(w), NilaiBatu.text(DENDA_CURANG))
					.withStyle(ChatFormatting.GREEN));
		} else {
			WargaApi.ubahKesan(w, player, -5, "tuduh", null);
			Msg.chat(player, Component.translatable("kultivator.kota42.jabatan.periksa_salah", WargaApi.nama(w)).withStyle(ChatFormatting.YELLOW));
		}
		KotaEkonomiData.get(server).ubah();
		return null;
	}

	// ================================================================== court record (Perkara)

	static void catatPutusan(MinecraftServer server, UUID player, boolean suap) {
		CompoundTag c = catatan(server, player);
		c.putInt("d", c.getIntOr("d", 0) + 1);
		if (suap) {
			c.putInt("s", c.getIntOr("s", 0) + 1);
			c.putInt("j", 0);
		} else {
			c.putInt("j", c.getIntOr("j", 0) + 1);
		}
		KotaEkonomiData.get(server).ubah();
	}

	static void catatAudit(MinecraftServer server, UUID player) {
		CompoundTag c = catatan(server, player);
		c.putInt("a", c.getIntOr("a", 0) + 1);
		KotaEkonomiData.get(server).ubah();
	}

	/** The court record {@code [judged, bribes, audits, judged in a row without a bribe]}. */
	public static int[] catatanSidang(MinecraftServer server, UUID player) {
		CompoundTag c = catatan(server, player);
		return new int[] {c.getIntOr("d", 0), c.getIntOr("s", 0), c.getIntOr("a", 0), c.getIntOr("j", 0)};
	}

	// ================================================================== wages, attendance

	/** The share of the place's weekly intake (%) a holder of rank {@code pangkat} (0-5) is paid. */
	public static int persenGaji(int pangkat) {
		return GAJI_PERSEN[Math.max(0, Math.min(GAJI_PERSEN.length - 1, pangkat))];
	}

	/** The most rank {@code pangkat} may earn here per week for this band. */
	public static long gajiMaks(int pangkat, int band) {
		return Math.round(GAJI_DASAR * (band + 1L) * pangkat * KotaConfig.ekonomi().gajiSkala);
	}

	/** Weekly wages of the place's offices from its income of the last week. */
	static void gajiMingguan(MinecraftServer server, long situs, long masuk) {
		if (!aktif()) {
			return;
		}
		CompoundTag p = pemegang(server, situs);
		for (String key : p.keySet()) {
			CompoundTag e = p.getCompoundOrEmpty(key);
			int rank = e.getIntOr("r", 0);
			UUID id = Investasi.uuid(key);
			if (id == null || rank < PENGAWAS_PASAR) {
				continue;
			}
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			int band = online != null ? Barang.pita(online) : e.getIntOr("b", 0);
			if (online != null) {
				e.putInt("b", band);
			}
			long wage = Math.min(gajiMaks(rank, band), Math.round(masuk * GAJI_PERSEN[rank] / 100.0D * KotaConfig.ekonomi().gajiSkala));
			if (wage > 0L && KasKota.bayar(server, situs, wage, "gaji")) {
				Investasi.keLaci(server, id, wage, Investasi.K_GAJI);
				e.putLong("gm", wage);
				Investasi.kabarPemain(server, id, Component.translatable("kultivator.kota42.jabatan.gaji", PajakKota.nama(server, situs), NilaiBatu.text(wage)));
			} else {
				e.putLong("gm", 0L);
			}
		}
		KotaEkonomiData.get(server).ubah();
	}

	/** The last weekly wage of the player's office here. */
	public static long gajiTerakhir(MinecraftServer server, long situs, UUID player) {
		return entri(server, situs, player).getLongOr("gm", 0L);
	}

	/** Every 10 s: an office holder standing in his place is present today. */
	static void tickHadir(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long today = KotaHidup.hari(server);
		for (ServerPlayer player : level.players()) {
			if (player.level().dimension() != Level.OVERWORLD) {
				continue;
			}
			TempatInfo here = TempatApi.tempatDi(level, player.blockPosition());
			if (here == null || pangkat(player.getUUID(), here.situsId()) == TANPA) {
				continue;
			}
			CompoundTag e = entri(server, here.situsId(), player.getUUID());
			if (e.getLongOr("h", -1L) != today) {
				e.putLong("h", today);
				e.putBoolean("w", false);
				KotaEkonomiData.get(server).ubah();
			}
		}
	}

	/** The last day the player was present in his office's place. */
	public static long hadir(MinecraftServer server, long situs, UUID player) {
		return entri(server, situs, player).getLongOr("h", -1L);
	}

	/** Weekly: absence warnings (14 days) and demotions (28 days). */
	static void mingguan(MinecraftServer server, long minggu) {
		long today = KotaHidup.hari(server);
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.JABATAN);
		for (String siteKey : List.copyOf(part.keySet())) {
			if (!siteKey.startsWith("kota_")) {
				continue;
			}
			long situs;
			try {
				situs = Long.parseLong(siteKey.substring(5));
			} catch (NumberFormatException exception) {
				continue;
			}
			CompoundTag p = part.getCompoundOrEmpty(siteKey).getCompoundOrEmpty("p");
			for (String key : List.copyOf(p.keySet())) {
				CompoundTag e = p.getCompoundOrEmpty(key);
				UUID id = Investasi.uuid(key);
				long away = today - e.getLongOr("h", today);
				if (id == null) {
					continue;
				}
				if (away >= TURUN_HARI) {
					int rank = e.getIntOr("r", 0) - 1;
					setPangkat(server, situs, id, rank, null);
					Investasi.kabarPemain(server, id, Component.translatable("kultivator.kota42.jabatan.turun", PajakKota.nama(server, situs),
							gelar(server, situs, Math.max(0, rank))));
				} else if (away >= PERINGATAN_HARI && !e.getBooleanOr("w", false)) {
					e.putBoolean("w", true);
					Investasi.kabarPemain(server, id, Component.translatable("kultivator.kota42.jabatan.peringatan", PajakKota.nama(server, situs),
							TURUN_HARI - away));
				}
			}
		}
		KotaEkonomiData.get(server).ubah();
	}

	/** The absence warning text for the screen, or null. */
	public static @Nullable Component peringatan(MinecraftServer server, long situs, UUID player) {
		long away = KotaHidup.hari(server) - hadir(server, situs, player);
		return pangkat(player, situs) > 0 && away >= PERINGATAN_HARI
				? Component.translatable("kultivator.kota42.jabatan.peringatan_layar", away, TURUN_HARI) : null;
	}

	// ================================================================== commands

	/** {@code /kultivasi kotaku jabatan [ajukan | periksa | pajak <tarif> | festival <nb> | penjaga | rencana <id>]}. */
	static LiteralArgumentBuilder<CommandSourceStack> perintah() {
		return Commands.literal("jabatan").executes(ctx -> lihat(ctx.getSource()))
				.then(Commands.literal("ajukan").executes(ctx -> hasil(ctx, ajukanDiSini(ctx))))
				.then(Commands.literal("periksa").executes(ctx -> hasil(ctx, periksa(ctx.getSource().getPlayerOrException()))))
				.then(Commands.literal("penjaga").executes(ctx -> hasil(ctx, diSini(ctx, Jabatan::rekrutPenjaga))))
				.then(Commands.literal("pajak").then(Commands.argument("tarif", IntegerArgumentType.integer(PajakKota.TARIF_MIN, PajakKota.TARIF_MAKS))
						.executes(ctx -> hasil(ctx, diSini(ctx, (p, s) -> PajakKota.ubahTarif(p, s, IntegerArgumentType.getInteger(ctx, "tarif")))))))
				.then(Commands.literal("festival").then(Commands.argument("nb", IntegerArgumentType.integer(0, (int) PajakKota.FESTIVAL_MAKS))
						.executes(ctx -> hasil(ctx, diSini(ctx, (p, s) -> PajakKota.setAnggaranFestival(p, s, IntegerArgumentType.getInteger(ctx, "nb")))))))
				.then(Commands.literal("rencana").then(Commands.argument("id", StringArgumentType.word())
						.executes(ctx -> hasil(ctx, diSini(ctx, (p, s) -> pilihRencana(p, s, StringArgumentType.getString(ctx, "id")))))));
	}

	private interface AksiTempat {
		@Nullable Component jalan(ServerPlayer player, long situs);
	}

	private static @Nullable Component diSini(CommandContext<CommandSourceStack> ctx, AksiTempat aksi) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo tempat = PajakKota.tempat(player);
		return tempat == null ? Component.translatable("kultivator.kota42.cmd.tanpa_tempat") : aksi.jalan(player, tempat.situsId());
	}

	private static @Nullable Component ajukanDiSini(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return diSini(ctx, Jabatan::ajukan);
	}

	private static int hasil(CommandContext<CommandSourceStack> ctx, @Nullable Component why) throws CommandSyntaxException {
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return lihat(ctx.getSource());
	}

	private static int lihat(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		TempatInfo tempat = PajakKota.tempat(player);
		if (tempat == null) {
			source.sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		long situs = tempat.situsId();
		int rank = pangkat(player.getUUID(), situs);
		int band = Barang.pita(player);
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.jabatan.judul", gelar(server, situs, rank), PajakKota.nama(server, situs),
				gajiTerakhir(server, situs, player.getUUID()), gajiMaks(Math.max(rank, 1), band)).withStyle(ChatFormatting.GOLD), false);
		if (rank >= PENGAWAS_PASAR) {
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.jabatan.tugas_periksa",
					tugasMinggu(server, situs, player.getUUID(), "periksa"), PERIKSA_MINGGU), false);
		}
		if (rank >= HAKIM_PEMBANTU) {
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.jabatan.tugas_perkara",
					tugasMinggu(server, situs, player.getUUID(), "perkara"), PERKARA_MINGGU), false);
		}
		Component warn = peringatan(server, situs, player.getUUID());
		if (warn != null) {
			source.sendSuccess(() -> warn.copy().withStyle(ChatFormatting.RED), false);
		}
		if (rank < pangkatMaks(server, situs)) {
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.jabatan.berikut", gelar(server, situs, rank + 1)), false);
			for (Syarat s : syarat(player, situs, rank + 1)) {
				MutableComponent line = Component.literal(s.ok() ? " ✓ " : " ✗ ").withStyle(s.ok() ? ChatFormatting.GREEN : ChatFormatting.RED)
						.append(s.teks().copy().withStyle(ChatFormatting.WHITE)).append(Component.literal(" (").append(s.punya()).append(")")
								.withStyle(ChatFormatting.GRAY));
				source.sendSuccess(() -> line, false);
			}
		}
		return 1;
	}

	// ================================================================== uji

	/** {@code uji kota_hidup ekonomi jabatan <pangkat>}: sets the tester's office here. */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("jabatan").executes(ctx -> lihat(ctx.getSource()))
				.then(Commands.argument("pangkat", IntegerArgumentType.integer(0, WALI_KOTA)).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TempatInfo tempat = PajakKota.tempat(player);
					if (tempat == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
						return 0;
					}
					int rank = IntegerArgumentType.getInteger(ctx, "pangkat");
					setPangkat(ctx.getSource().getServer(), tempat.situsId(), player.getUUID(), rank, player.getScoreboardName());
					if (rank > 0) {
						beriLencana(player, tempat.situsId(), rank);
					}
					return lihat(ctx.getSource());
				}));
	}

	private Jabatan() {
	}
}
