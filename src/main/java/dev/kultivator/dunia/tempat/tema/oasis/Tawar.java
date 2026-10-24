package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.bersama.DagangItems;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.voice.Voice;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.MoonPhase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Bazaar haggling of the Kota Oasis (DESIGN 38.T.4.5, screen {@code tema_tawar}) and the Pasar Bulan Sabit guest stalls
 * (38.T.4.3).
 * <ul>
 * <li>Every bazaar trader ({@code PEDAGANG_BAZAR}, the water shop and the carpet weaver) offers one good of its stall
 * per world day ({@link #barangHari}); the opening price is 1.30 x base, the player haggles at most three rounds.</li>
 * <li>The trader has a mood 0-100 per player and day: temperament + Nama Baik + the hour (morning friendly, the heat
 * break curt). An offer at or above the hidden floor (0.90..1.10 x base, hashed per trader and day) is accepted; an
 * offer more than 15 % of base below the floor costs 25 mood (head shake, angry point); below 20 mood the trader will
 * not sell that good today. The final price is never under 0.90 x base; resale stays at most 0.5 (38.M.5, the shops'
 * own rule).</li>
 * <li>Kafilah Agung: for two days after the great caravan arrived the trade goods it brought are 10 % cheaper
 * ({@link KafilahUnta#diskonKafilah}).</li>
 * <li>Pasar Bulan Sabit: on crescent-moon nights (waxing and waning crescent) from 17:00 every {@code tema_oasis/bazar}
 * anchor brings up to three guest stalls ({@code kios = tamu}) with rare goods, stock one each, gone at midnight.</li>
 * <li>Heat break 11:00-15:00: the bazaar is closed (the screen is locked with the trader's line).</li>
 * </ul>
 */
public final class Tawar {

	/** Memory key of a trader's stall kind ({@code rempah}, {@code permadani}, {@code kaca}, {@code giok}, ...). */
	public static final String KIOS = "oasis_kios";
	public static final double BUKA = 1.30D;
	public static final double LANTAI_MIN = 0.90D;
	public static final double LANTAI_RENTANG = 0.20D;
	public static final double HINA = 0.15D;
	public static final int PUTARAN_MAKS = 3;
	public static final int SUASANA_TOLAK = 20;
	private static final int[] LANGKAH = {1, 5, 10};
	private static final int TAMU_PER_LORONG = 3;

	/** One stall good: the stack (made when sold) and its base price in NB for the whole stack. */
	record Dagangan(String id, Supplier<ItemStack> stack, long dasar, boolean dagang) {
	}

	private static final Map<String, List<Dagangan>> KIOS_BARANG = new HashMap<>();

	static {
		kios("rempah",
				new Dagangan("kurma", () -> new ItemStack(DagangItems.KURMA_EMAS, 8), 24L, true),
				new Dagangan("rempah_seberang", () -> new ItemStack(DagangItems.REMPAH_SEBERANG, 4), 48L, true),
				new Dagangan("kakao", () -> new ItemStack(Items.COCOA_BEANS, 16), 8L, false),
				new Dagangan("madu", () -> new ItemStack(Items.HONEY_BOTTLE, 2), 6L, false),
				new Dagangan("gula", () -> new ItemStack(Items.SUGAR, 16), 5L, false));
		kios("permadani",
				new Dagangan("permadani", () -> new ItemStack(DagangItems.PERMADANI_GURUN, 1), 30L, true),
				new Dagangan("karpet_merah", () -> new ItemStack(Items.CARPET.red(), 8), 6L, false),
				new Dagangan("karpet_jingga", () -> new ItemStack(Items.CARPET.orange(), 8), 6L, false),
				new Dagangan("wol_kuning", () -> new ItemStack(Items.WOOL.yellow(), 8), 8L, false),
				new Dagangan("kerudung", () -> new ItemStack(TemaItems.KERUDUNG_PASIR, 1), 15L, false));
		kios("kaca",
				new Dagangan("kaca_warna", () -> new ItemStack(DagangItems.KACA_WARNA, 2), 32L, true),
				new Dagangan("kaca_jingga", () -> new ItemStack(Items.STAINED_GLASS.orange(), 8), 8L, false),
				new Dagangan("kaca_biru", () -> new ItemStack(Items.STAINED_GLASS.lightBlue(), 8), 8L, false),
				new Dagangan("lentera", () -> new ItemStack(Items.LANTERN, 2), 10L, false));
		kios("giok",
				new Dagangan("giok_barat", () -> new ItemStack(DagangItems.GIOK_BARAT, 1), 60L, true),
				new Dagangan("ametis", () -> new ItemStack(Items.AMETHYST_SHARD, 4), 12L, false),
				new Dagangan("prismarin", () -> new ItemStack(Items.PRISMARINE_SHARD, 4), 10L, false));
		kios("logam",
				new Dagangan("tembaga", () -> new ItemStack(Items.COPPER_INGOT, 8), 8L, false),
				new Dagangan("besi", () -> new ItemStack(Items.IRON_INGOT, 4), 14L, false),
				new Dagangan("lonceng", () -> new ItemStack(Items.BELL, 1), 20L, false),
				new Dagangan("lentera_kuningan", () -> new ItemStack(Items.LANTERN, 2), 10L, false));
		kios("air",
				new Dagangan("air_minum", () -> {
					ItemStack s = PotionContents.createItemStack(Items.POTION, Potions.WATER);
					s.setCount(1);
					return s;
				}, 2L, false),
				new Dagangan("es_biru", () -> new ItemStack(Items.BLUE_ICE, 2), 12L, false),
				new Dagangan("es_padat", () -> new ItemStack(Items.PACKED_ICE, 4), 6L, false),
				new Dagangan("semangka", () -> new ItemStack(Items.MELON_SLICE, 8), 4L, false));
		kios("penenun",
				new Dagangan("permadani", () -> new ItemStack(DagangItems.PERMADANI_GURUN, 1), 30L, true),
				new Dagangan("wol_merah", () -> new ItemStack(Items.WOOL.red(), 8), 8L, false),
				new Dagangan("karpet_coklat", () -> new ItemStack(Items.CARPET.brown(), 8), 6L, false));
		kios("tamu",
				new Dagangan("giok_barat_dua", () -> new ItemStack(DagangItems.GIOK_BARAT, 2), 110L, true),
				new Dagangan("peta_reruntuhan", () -> new ItemStack(TemaItems.LEMPENG_PETA, 1), 45L, false),
				new Dagangan("sesaji_gurun", () -> new ItemStack(TemaItems.EKOR_RACUN_KALAJENGKING, 1), 40L, false),
				new Dagangan("koin_kuno", () -> new ItemStack(TemaItems.KOIN_KUNO, 6), 18L, false));
	}

	private static void kios(String id, Dagangan... list) {
		KIOS_BARANG.put(id, List.of(list));
	}

	/** One haggle in progress (memory; a reload starts over). */
	private static final class Sesi {
		final int npcId;
		final long hari;
		final String kios;
		final Dagangan barang;
		final long dasar;
		final long lantai;
		long minta;
		long tawaran;
		int putaran = 1;
		int suasana;
		Component ucapan;

		Sesi(int npcId, long hari, String kios, Dagangan barang, long dasar, long lantai, int suasana) {
			this.npcId = npcId;
			this.hari = hari;
			this.kios = kios;
			this.barang = barang;
			this.dasar = dasar;
			this.lantai = lantai;
			this.minta = Math.max(1L, Math.round(dasar * BUKA));
			this.tawaran = Math.max(1L, Math.round(dasar * 0.8D));
			this.suasana = suasana;
		}
	}

	private static final Map<UUID, Sesi> SESI = new HashMap<>();
	/** Guest stall bodies per bazaar anchor. */
	private static final Map<UUID, List<KultivatorNpc>> TAMU = new HashMap<>();

	static void init() {
		TemaLayar.daftar(TemaLayar.Tawar.LAYAR, Tawar::bangun, Tawar::aksi);
	}

	static void reset() {
		SESI.clear();
		TAMU.clear();
	}

	static void lupakan(UUID id) {
		SESI.remove(id);
	}

	// ================================================================== stalls

	/** True when {@code npc} serves a bazaar stall (trader, water seller, weaver or crescent guest). */
	static boolean pedagang(@Nullable KultivatorNpc npc) {
		if (npc == null) {
			return false;
		}
		return npc.role() == NpcRole.PEDAGANG_BAZAR || !npc.memory().getStringOr(KIOS, "").isEmpty();
	}

	/** The stall kind of a trader: its tag, else from the district (west: spices/carpets, east: glass/jade/brass). */
	static String kiosDari(KultivatorNpc npc) {
		String tag = npc.memory().getStringOr(KIOS, "");
		if (!tag.isEmpty() && KIOS_BARANG.containsKey(tag)) {
			return tag;
		}
		String distrik = npc.level() instanceof ServerLevel level ? TemaKota.distrikDi(level, npc.blockPosition()) : null;
		int n = Math.floorMod(npc.getUUID().hashCode(), 6);
		if ("bazar_timur".equals(distrik)) {
			return n % 3 == 0 ? "kaca" : n % 3 == 1 ? "giok" : "logam";
		}
		return n % 2 == 0 ? "rempah" : "permadani";
	}

	/** The good this stall offers today (one per stall per world day). */
	static Dagangan barangHari(KultivatorNpc npc, String kios, long hari) {
		List<Dagangan> list = KIOS_BARANG.getOrDefault(kios, KIOS_BARANG.get("rempah"));
		long h = OasisKota.hash(npc.getUUID().getLeastSignificantBits(), hari, 380430L);
		return list.get((int) Math.floorMod(h, (long) list.size()));
	}

	/** The hidden floor: 0.90..1.10 x base, hashed per trader and day. */
	static long lantai(KultivatorNpc npc, long hari, long dasar) {
		double f = LANTAI_MIN + LANTAI_RENTANG * OasisKota.acak(npc.getUUID().getMostSignificantBits(), hari, 380431L);
		return Math.max(1L, Math.round(dasar * f));
	}

	/** The starting mood of a trader toward a player: temperament, Nama Baik and the hour. */
	static int suasanaAwal(ServerPlayer player, KultivatorNpc npc, int jam) {
		int mood = switch (npc.temperament()) {
			case 0 -> 70;   // ramah
			case 1 -> 45;   // curiga
			case 2 -> 40;   // serakah
			case 3 -> 60;   // bijak
			case 4 -> 55;   // penakut
			case 5 -> 35;   // sombong
			case 6 -> 65;   // jujur
			default -> 50;  // licik
		};
		if (npc.town() != 0L) {
			int rep = KotaData.get(player.level().getServer()).reputation(npc.town(), player.getUUID());
			mood += Math.max(-20, Math.min(20, rep / 2));
		}
		if (jam >= 6 && jam < 11) {
			mood += 10;
		} else if (jam >= 11 && jam < 15) {
			mood -= 20;
		} else if (jam >= 17) {
			mood += 5;
		}
		return Math.max(0, Math.min(100, mood));
	}

	private static boolean jedaPanas(int jam) {
		return jam >= 11 && jam < 15;
	}

	// ================================================================== screen

	private static @Nullable KultivatorNpc npcDari(ServerPlayer player, int npcId) {
		if (npcId < 0) {
			return null;
		}
		Entity e = player.level().getEntity(npcId);
		return e instanceof KultivatorNpc k && pedagang(k) ? k : null;
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = npcDari(player, npcId);
		if (npc == null || !(player.level() instanceof ServerLevel level)) {
			return null;
		}
		MinecraftServer server = level.getServer();
		long hari = OasisKota.hari(level);
		int jam = OasisKota.jamHari(level);
		CompoundTag tag = OasisLayar.kepala(player, "kultivator.kota_tema.oasis.tawar.judul", LembarKunci.GAYA_KAIN, npc);
		String kios = kiosDari(npc);
		Sesi sesi = SESI.get(player.getUUID());
		if (sesi == null || sesi.npcId != npc.getId() || sesi.hari != hari) {
			Dagangan barang = barangHari(npc, kios, hari);
			long dasar = hargaDasar(server, npc, barang, hari);
			sesi = new Sesi(npc.getId(), hari, kios, barang, dasar, lantai(npc, hari, dasar), suasanaAwal(player, npc, jam));
			sesi.ucapan = Component.translatable("kultivator.kota_tema.oasis.tawar.buka." + kios, NilaiBatu.text(sesi.minta));
			SESI.put(player.getUUID(), sesi);
		}
		CompoundTag barang = new CompoundTag();
		ItemStack contoh = sesi.barang.stack().get();
		LembarKunci.teks(barang, LembarKunci.NAMA, contoh.getHoverName());
		LembarKunci.barang(barang, contoh, level.registryAccess());
		barang.putInt(LembarKunci.JUMLAH, contoh.getCount());
		tag.put(TemaLayar.Tawar.BARANG, barang);
		tag.putLong(TemaLayar.Tawar.BUKA, Math.round(sesi.dasar * BUKA));
		LembarKunci.teks(tag, TemaLayar.Tawar.DASAR_KIRA, Component.translatable("kultivator.kota_tema.oasis.tawar.kira",
				NilaiBatu.text(Math.max(1L, (sesi.dasar + 4L) / 5L * 5L))));
		tag.putLong(TemaLayar.Tawar.TAWARAN, sesi.tawaran);
		tag.putIntArray(TemaLayar.Tawar.LANGKAH, LANGKAH);
		tag.putInt(TemaLayar.Tawar.PUTARAN, Math.min(sesi.putaran, PUTARAN_MAKS));
		tag.putInt(TemaLayar.Tawar.PUTARAN_MAKS, PUTARAN_MAKS);
		tag.putInt(TemaLayar.Tawar.SUASANA, sesi.suasana);
		tag.putLong(TemaLayar.Tawar.TERIMA, sesi.minta);
		boolean tolak = sesi.suasana < SUASANA_TOLAK || ditolak(server, player.getUUID(), npc, hari);
		boolean sudah = sudahBeli(server, player.getUUID(), npc, hari);
		if (jedaPanas(jam) && !"tamu".equals(kios)) {
			LembarKunci.teks(tag, TemaLayar.Tawar.UCAPAN, Component.translatable("kultivator.kota_tema.oasis.tawar.panas"));
			OasisLayar.kunci(tag, Component.translatable("kultivator.kota_tema.oasis.tawar.panas"),
					Component.translatable("kultivator.kota_tema.oasis.tawar.buka_lagi"));
		} else if (sudah) {
			LembarKunci.teks(tag, TemaLayar.Tawar.UCAPAN, Component.translatable("kultivator.kota_tema.oasis.tawar.sudah"));
			OasisLayar.kunci(tag, Component.translatable("kultivator.kota_tema.oasis.tawar.sudah"), null);
		} else if (tolak) {
			tag.putBoolean(TemaLayar.Tawar.MENOLAK, true);
			LembarKunci.teks(tag, TemaLayar.Tawar.UCAPAN, Component.translatable("kultivator.kota_tema.oasis.tawar.tolak"));
			OasisLayar.kunci(tag, Component.translatable("kultivator.kota_tema.oasis.tawar.tolak"), null);
		} else {
			LembarKunci.teks(tag, TemaLayar.Tawar.UCAPAN, sesi.ucapan);
		}
		return tag;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = npcDari(player, npcId);
		Sesi sesi = SESI.get(player.getUUID());
		if (npc == null || sesi == null || sesi.npcId != npc.getId() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		long hari = OasisKota.hari(level);
		if (sesi.hari != hari || jedaPanas(OasisKota.jamHari(level)) && !"tamu".equals(sesi.kios)
				|| sudahBeli(server, player.getUUID(), npc, hari) || ditolak(server, player.getUUID(), npc, hari)) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		String verb = parts[0];
		if (TemaLayar.Tawar.OP_PERGI.equals(verb)) {
			SESI.remove(player.getUUID());
			npc.say(Component.translatable("kultivator.kota_tema.oasis.tawar.pergi"), 60);
			SosokApi.gerak(npc, GerakId.LAMBAI, 0, 30);
			dev.kultivator.dunia.tempat.ui.TempatLayar.tutup(player);
			return;
		}
		if (TemaLayar.Tawar.OP_TERIMA.equals(verb)) {
			beli(player, npc, sesi, sesi.minta);
			return;
		}
		if (!TemaLayar.Tawar.OP_TAWAR.equals(verb) || sesi.putaran > PUTARAN_MAKS) {
			return;
		}
		long tawar = Math.max(1L, Math.min(sesi.minta, LembarKunci.argLong(parts, 1, sesi.tawaran)));
		sesi.tawaran = tawar;
		if (tawar >= sesi.lantai) {
			beli(player, npc, sesi, Math.max(tawar, Math.round(sesi.dasar * LANTAI_MIN)));
			return;
		}
		sesi.putaran++;
		if (tawar < sesi.lantai - Math.round(sesi.dasar * HINA)) {
			sesi.suasana = Math.max(0, sesi.suasana - 25);
			SosokApi.gerak(npc, sesi.suasana < 40 ? GerakId.MARAH_TUNJUK : GerakId.BERDEBAT, 0, 40);
			sesi.ucapan = Component.translatable("kultivator.kota_tema.oasis.tawar.hina." + level.getRandom().nextInt(3));
			if (sesi.suasana < SUASANA_TOLAK) {
				tandaiTolak(server, player.getUUID(), npc, hari);
				sesi.ucapan = Component.translatable("kultivator.kota_tema.oasis.tawar.tolak");
				OasisLayar.peringatan(player, Component.translatable("kultivator.kota_tema.oasis.tawar.tolak"));
			}
			return;
		}
		// meet in the middle, never under the floor
		long tengah = (sesi.minta + tawar + 1L) / 2L;
		sesi.minta = Math.max(sesi.lantai, tengah);
		sesi.suasana = Math.max(0, sesi.suasana - 5);
		SosokApi.gerak(npc, GerakId.TAWAR_BAZAR, 0, 40, PropId.KANTONG_UANG, 0, 0);
		if (sesi.putaran > PUTARAN_MAKS) {
			sesi.ucapan = Component.translatable("kultivator.kota_tema.oasis.tawar.akhir", NilaiBatu.text(sesi.minta));
		} else {
			sesi.ucapan = Component.translatable("kultivator.kota_tema.oasis.tawar.teman." + level.getRandom().nextInt(3), NilaiBatu.text(sesi.minta));
		}
		level.playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.4F, 1.1F);
	}

	private static void beli(ServerPlayer player, KultivatorNpc npc, Sesi sesi, long harga) {
		MinecraftServer server = player.level().getServer();
		long akhir = Math.max(harga, Math.round(sesi.dasar * LANTAI_MIN));
		if (!EkonomiApi.bayar(player, akhir, "tema.oasis.tawar")) {
			OasisLayar.pesan(player, Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(akhir)), false);
			return;
		}
		ItemStack stack = sesi.barang.stack().get();
		Component nama = stack.getHoverName();
		int jumlah = stack.getCount();
		OasisKota.beri(player, stack);
		long hari = sesi.hari;
		tandaiBeli(server, player.getUUID(), npc, hari);
		SESI.remove(player.getUUID());
		SosokApi.gerak(npc, GerakId.TERTAWA, 0, 30);
		Voice.say(npc, "tema.oasis.bazaar", null);
		npc.say(Component.translatable("kultivator.kota_tema.oasis.tawar.jadi." + player.getRandom().nextInt(3)), 70);
		player.level().playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.5F, 1.0F);
		OasisLayar.pesan(player, Component.translatable("kultivator.kota_tema.oasis.tawar.dibeli", jumlah, nama, NilaiBatu.text(akhir)), true);
		boolean murah = akhir <= Math.round(sesi.dasar * 1.0D);
		QuestTracker.peristiwa(player, "oasis_tawar", murah ? "murah" : "biasa");
		if ("tamu".equals(sesi.kios)) {
			npc.memory().putBoolean("habis", true);
		}
	}

	/** The base price of a good today (the great caravan's 10 % off on its trade goods for two days). */
	private static long hargaDasar(MinecraftServer server, KultivatorNpc npc, Dagangan barang, long hari) {
		long dasar = barang.dasar();
		if (barang.dagang()) {
			OasisKota.Hidup kota = npc.level() instanceof ServerLevel level ? OasisKota.terdekat(level, npc.position(), OasisKota.JANGKAU_KOTA) : null;
			if (kota != null && KafilahUnta.diskonKafilah(server, kota.situs, hari)) {
				dasar = Math.max(1L, Math.round(dasar * 0.9D));
			}
		}
		return dasar;
	}

	// ================================================================== per-day marks (player theme data)

	private static String kunci(KultivatorNpc npc, long hari) {
		return npc.getUUID() + ":" + hari;
	}

	private static boolean dalamDaftar(MinecraftServer server, UUID player, String list, String key) {
		ListTag tags = OasisKota.pemain(server, player).getListOrEmpty(list);
		for (int i = 0; i < tags.size(); i++) {
			if (key.equals(tags.getStringOr(i, ""))) {
				return true;
			}
		}
		return false;
	}

	private static void tambahDaftar(MinecraftServer server, UUID player, String list, String key, long hari) {
		CompoundTag me = OasisKota.pemain(server, player);
		ListTag tags = me.getListOrEmpty(list);
		// keep today's and yesterday's marks only
		for (int i = tags.size() - 1; i >= 0; i--) {
			String s = tags.getStringOr(i, "");
			int colon = s.lastIndexOf(':');
			long day = colon < 0 ? 0L : parse(s.substring(colon + 1));
			if (day < hari - 1L) {
				tags.remove(i);
			}
		}
		tags.add(StringTag.valueOf(key));
		me.put(list, tags);
		OasisKota.ubah(server);
	}

	private static long parse(String s) {
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static boolean sudahBeli(MinecraftServer server, UUID player, KultivatorNpc npc, long hari) {
		return npc.memory().getBooleanOr("habis", false) || dalamDaftar(server, player, "tawar_beli", kunci(npc, hari));
	}

	private static void tandaiBeli(MinecraftServer server, UUID player, KultivatorNpc npc, long hari) {
		tambahDaftar(server, player, "tawar_beli", kunci(npc, hari), hari);
	}

	private static boolean ditolak(MinecraftServer server, UUID player, KultivatorNpc npc, long hari) {
		return dalamDaftar(server, player, "tawar_tolak", kunci(npc, hari));
	}

	private static void tandaiTolak(MinecraftServer server, UUID player, KultivatorNpc npc, long hari) {
		tambahDaftar(server, player, "tawar_tolak", kunci(npc, hari), hari);
	}

	// ================================================================== Pasar Bulan Sabit

	/** True on a crescent-moon night (waxing or waning crescent) at {@code pos}, from 17:00 to midnight. */
	static boolean malamBulanSabit(ServerLevel level, long situs, BlockPos pos) {
		int jam = OasisKota.jamHari(level);
		if (jam < 17) {
			return false;
		}
		long hari = OasisKota.hari(level);
		return TemaKalender.bulan(MoonPhase.WAXING_CRESCENT).cocok(level, hari, situs, pos)
				|| TemaKalender.bulan(MoonPhase.WANING_CRESCENT).cocok(level, hari, situs, pos);
	}

	/** Anchor {@code tema_oasis/bazar}: the crescent-night guest stalls along the lane (data {@code sisi}). */
	static void bazarDekat(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		List<KultivatorNpc> tamu = TAMU.computeIfAbsent(anchor.getUUID(), k -> new ArrayList<>());
		tamu.removeIf(n -> n.isRemoved() || !n.isAlive());
		long situs = OasisAnchors.situsOf(level, anchor);
		boolean buka = OasisKota.aktif() && malamBulanSabit(level, situs, anchor.blockPosition()) && !BadaiPasir.aktif(situs);
		if (!buka) {
			for (KultivatorNpc n : tamu) {
				n.vanish();
			}
			tamu.clear();
			return;
		}
		if (tamu.size() >= TAMU_PER_LORONG) {
			return;
		}
		double rad = Math.toRadians(anchor.getYRot());
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		// guests stand left and right of the lane, facing it
		for (int i = tamu.size(); i < TAMU_PER_LORONG; i++) {
			double along = (i - 1) * 5.0D;
			double side = (i % 2 == 0 ? 2.5D : -2.5D);
			BlockPos at = BlockPos.containing(anchor.getX() + fx * along - fz * side, anchor.getY(), anchor.getZ() + fz * along + fx * side);
			float yaw = anchor.getYRot() + (i % 2 == 0 ? 90.0F : -90.0F);
			KultivatorNpc npc = HidupApi.tubuh(level, situs, NpcRole.PEDAGANG_BAZAR, at, yaw);
			if (npc == null) {
				break;
			}
			npc.memory().putString(KIOS, "tamu");
			npc.memory().putBoolean("hold", true);
			npc.setPlate(Component.translatable("kultivator.kota_tema.oasis.tamu.papan"));
			SosokApi.gerak(npc, GerakId.SERU_DAGANG, 0, 0, PropId.LENTERA_TANGAN, 0, 0);
			tamu.add(npc);
		}
		if (!tamu.isEmpty() && level.getRandom().nextInt(8) == 0) {
			KultivatorNpc speaker = tamu.get(level.getRandom().nextInt(tamu.size()));
			speaker.say(Component.translatable("kultivator.kota_tema.oasis.tamu.seru." + level.getRandom().nextInt(4)), 70);
		}
	}

	/** Anchor unload: the guests go. */
	static void bazarLepas(DuniaAnchorEntity anchor) {
		List<KultivatorNpc> tamu = TAMU.remove(anchor.getUUID());
		if (tamu != null) {
			for (KultivatorNpc n : tamu) {
				if (!n.isRemoved()) {
					n.vanish();
				}
			}
		}
	}

	/** Every stall kind id (commands). */
	static List<String> jenisKios() {
		return List.copyOf(KIOS_BARANG.keySet());
	}

	/** Item of a good id of a stall kind, for tests ("" when none). */
	static @Nullable Item contoh(String kios) {
		List<Dagangan> list = KIOS_BARANG.get(kios);
		return list == null || list.isEmpty() ? null : list.getFirst().stack().get().getItem();
	}

	private Tawar() {
	}
}
