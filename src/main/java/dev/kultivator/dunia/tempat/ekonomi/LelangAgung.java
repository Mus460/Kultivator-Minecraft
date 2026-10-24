package dev.kultivator.dunia.tempat.ekonomi;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.kultivator.data.Element;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.BalaiLelang;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.Tokoh;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.tempa.Forging;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lelang Agung, the capital's Grand Auction (DESIGN 38.M.2.3, screen 38.U.3.3), and the live {@code lelang} screen of the one
 * auction engine {@link BalaiLelang} (town sessions show in it too). Every seventh day ({@code day % 7 == 5}) between
 * 12 000 and 15 000 (18.00-21.00) the Balai Lelang Agung opens with {@link ProfilLelang#AGUNG}: 8 house lots from the
 * table {@code data/kultivator/tempat/lelang/agung.json} (3 low, 3 middle, 2 high bands relative to the strongest player
 * present), then the players' consignments as real lots. Consignments are handed in any day before the session (4 per
 * player and session, the whole stack in hand, a base the seller chooses between 0.5 and 1.5 × its value) and kept in
 * {@link EkonomiData} from the moment they are handed in; a sold piece pays the price less the 10 % commission (a sink into
 * the town treasury), an unsold one comes back. Results and stones owed wait under "Hasil" until taken.
 * <p>
 * Lot table entries ({@link EkonomiReload#lelang}): {@code jenis} one of {@code senjata} ({@code bentuk}, {@code mutu}),
 * {@code pusaka} ({@code bentuk}, {@code mutu_min}, {@code mutu_maks}), {@code pil}, {@code harta}, {@code gelap} (the band
 * pools of {@code Harga}), {@code item} ({@code id}, {@code jumlah}), {@code herba} ({@code umur_min}, {@code umur_maks}),
 * {@code inti} ({@code tingkat_min}, {@code tingkat_maks}), {@code sesaji} ({@code jumlah}), {@code misteri} (the closed box,
 * contents from the Batu Gunung table); every entry has {@code kelas} ({@code rendah|menengah|tinggi}), {@code bobot}, and
 * optional {@code pita_min}/{@code pita_maks} and {@code faktor} (base price factor).
 */
public final class LelangAgung {

	/** One lot handed to the engine: a house lot ({@code penjual} null) or a consigned piece. */
	public record LotLelang(ItemStack barang, long dasar, @Nullable UUID penjual, Component namaPenjual, long titipId, boolean misteri) {
	}

	/** One NPC bidder of a profile source. */
	public record Penawar(Component nama, String kelompok) {
	}

	public static final int SIKLUS = 7;
	public static final int HARI_KE = 5;
	public static final long MULAI = 12000L;
	public static final long AKHIR = 15000L;
	public static final double DASAR_MIN = 0.5D;
	public static final double DASAR_MAKS = 1.5D;
	/**
	 * NPC bidders never pay more than this times the town value (× price scale) for a player's consignment (38.M.5: an
	 * auction must not turn shop goods into profit; with bidders at up to 1.5 × an asking price of up to 1.5 × value a
	 * consigned piece fetched about 1.7 × its value). The seller still gets the price less the 10 % commission.
	 */
	public static final double TITIP_PLAFON = 1.0D;
	public static final int HASIL_MAKS = 12;
	static final String[] KELAS = {"rendah", "menengah", "tinggi"};
	private static final String TITIP = "lelang_titip";
	private static final String HASIL = "lelang_hasil";
	private static final String UTANG = "lelang_utang";
	private static final String KEMBALI = "lelang_kembali";
	private static final String HARI_AGUNG = "lelang_agung_hari";
	private static final Set<NpcRole> PERAN = Set.of(NpcRole.JURU_LELANG);

	/** The session key each viewer of the {@code lelang} screen looks at (live refresh). */
	private static final Map<UUID, Long> MELIHAT = new HashMap<>();

	static void init() {
		TempatLayar.daftar(LembarKunci.Lelang.LAYAR, LelangAgung::aksi, LelangAgung::bangun);
	}

	static void lupakan(UUID id) {
		MELIHAT.remove(id);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Schedule

	/** True on a Grand Auction day inside its opening hours. */
	public static boolean jamAgung(MinecraftServer server) {
		long time = Barang.jamDunia(server);
		return Math.floorMod(Barang.hari(server), SIKLUS) == HARI_KE && time >= MULAI && time < AKHIR;
	}

	/** True when site {@code situs} is a capital (its auctioneer runs the Grand Auction). */
	static boolean ibuKota(MinecraftServer server, long situs) {
		DuniaSites.Site site = DuniaSites.byId(server, situs);
		return site != null && (site.kind().equals("kota/kota_besar") || site.kind().startsWith("kotabesar"));
	}

	/** Ticks until the next Grand Auction opens (0 while it may open). */
	static long sampaiAgung(MinecraftServer server) {
		long day = Barang.hari(server);
		long time = Barang.jamDunia(server);
		long dayOffset = Math.floorMod(HARI_KE - Math.floorMod(day, SIKLUS), SIKLUS);
		if (dayOffset == 0L && time >= AKHIR) {
			dayOffset = SIKLUS;
		}
		if (dayOffset == 0L && time >= MULAI) {
			return 0L;
		}
		return dayOffset * 24000L + MULAI - time;
	}

	/**
	 * {@code kotabesar/panggung acara=lelang} or {@code kotabesar/layanan layanan=lelang_agung} near players (38KS forwards its
	 * {@code nearby}): opens the day's Grand Auction once inside its hours when a player stands within 24 blocks.
	 */
	public static void jangkarDekat(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		MinecraftServer server = level.getServer();
		if (!Kota.config().auction || !jamAgung(server) || BalaiLelang.berjalan(anchor.site()) || within.isEmpty()) {
			return;
		}
		CompoundTag site = EkonomiData.get(server).situs(anchor.site());
		long day = Barang.hari(server);
		if (site.getLongOr(HARI_AGUNG, Long.MIN_VALUE) == day) {
			return;
		}
		boolean near = false;
		for (ServerPlayer player : within) {
			near |= player.distanceToSqr(anchor) <= 24.0D * 24.0D;
		}
		if (near && BalaiLelang.mulai(level, anchor, ProfilLelang.AGUNG)) {
			site.putLong(HARI_AGUNG, day);
			EkonomiData.get(server).touch();
		}
	}

	/**
	 * Every 100 ticks inside the Grand Auction hours: the capital's auctioneer already standing near a player opens the
	 * session even when no anchor forwards it (a capital whose stage anchor is not loaded).
	 */
	static void tick(MinecraftServer server, long tick) {
		if (tick % 100L != 0L || !Kota.config().auction || !jamAgung(server)) {
			return;
		}
		long day = Barang.hari(server);
		EkonomiData data = EkonomiData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = (ServerLevel) player.level();
			for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new AABB(player.blockPosition()).inflate(24.0D, 8.0D, 24.0D),
					npc -> npc.isAlive() && npc.role() == NpcRole.JURU_LELANG)) {
				long key = BalaiLelang.kunci(npc);
				if (key == 0L || BalaiLelang.berjalan(key) || !ibuKota(server, key) || data.situs(key).getLongOr(HARI_AGUNG, Long.MIN_VALUE) == day) {
					continue;
				}
				if (BalaiLelang.mulai(level, npc, ProfilLelang.AGUNG)) {
					data.situs(key).putLong(HARI_AGUNG, day);
					data.touch();
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Lots of a table

	/**
	 * A house lot of {@code profil}'s table for lot class {@code kelas} (0 low, 1 middle, 2 high), the band being the
	 * strongest present player's band shifted by the class (−1 / 0 / +1). Null only when nothing at all can be made.
	 */
	public static @Nullable LotLelang buatLot(ServerLevel level, ProfilLelang profil, int kelas, int band, RandomSource random) {
		int pita = Mth.clamp(band + kelas - 1, 0, Harga.BANDS - 1);
		List<JsonObject> table = EkonomiReload.lelang(profil.tabelLot());
		List<JsonObject> fit = new ArrayList<>();
		int total = 0;
		for (JsonObject entry : table) {
			String k = str(entry, "kelas", "");
			if ((!k.isEmpty() && !k.equals(KELAS[Mth.clamp(kelas, 0, 2)])) || pita < num(entry, "pita_min", 0) || pita > num(entry, "pita_maks", 8)) {
				continue;
			}
			fit.add(entry);
			total += Math.max(1, num(entry, "bobot", 1));
		}
		for (int attempt = 0; attempt < 3 && !fit.isEmpty(); attempt++) {
			int roll = random.nextInt(total);
			JsonObject chosen = fit.getLast();
			for (JsonObject entry : fit) {
				roll -= Math.max(1, num(entry, "bobot", 1));
				if (roll < 0) {
					chosen = entry;
					break;
				}
			}
			LotLelang lot = lot(level, chosen, pita, random);
			if (lot != null) {
				return lot;
			}
		}
		// no table (a data pack removed it): a town weapon of the band
		ItemStack weapon = SenjataKota.stack(SenjataKota.Bentuk.values()[random.nextInt(SenjataKota.Bentuk.values().length)], mutuPita(pita));
		return weapon.isEmpty() ? null : new LotLelang(weapon, Math.max(1L, Barang.harga(EkonomiApi.harga(weapon), 1.0D)), null, Component.empty(), 0L,
				false);
	}

	private static @Nullable LotLelang lot(ServerLevel level, JsonObject entry, int pita, RandomSource random) {
		String jenis = str(entry, "jenis", "item");
		double faktor = entry.has("faktor") ? Mth.clamp(entry.get("faktor").getAsDouble(), 0.5D, 2.0D) : 1.0D;
		if ("misteri".equals(jenis)) {
			ItemStack box = new ItemStack(EkonomiItems.LOT_MISTERI);
			CompoundTag data = new CompoundTag();
			long nilai = JudiBatu.Tingkat.GUNUNG.harga;
			data.putLong("nilai", nilai);
			box.set(Barang.DATA, data);
			return new LotLelang(box, Math.max(1L, Barang.harga(nilai, faktor)), null, Component.empty(), 0L, true);
		}
		ItemStack stack = barang(level, jenis, entry, pita, random);
		if (stack.isEmpty()) {
			return null;
		}
		long value = EkonomiApi.harga(stack) * stack.getCount();
		if (value <= 0L) {
			return null;
		}
		return new LotLelang(stack, Math.max(1L, Barang.harga(value, faktor)), null, Component.empty(), 0L, false);
	}

	private static ItemStack barang(ServerLevel level, String jenis, JsonObject entry, int pita, RandomSource random) {
		switch (jenis) {
			case "senjata" -> {
				SenjataKota.Bentuk bentuk = SenjataKota.Bentuk.byId(str(entry, "bentuk", ""));
				if (bentuk == null) {
					bentuk = SenjataKota.Bentuk.values()[random.nextInt(SenjataKota.Bentuk.values().length)];
				}
				SenjataKota.Mutu mutu = SenjataKota.Mutu.byId(str(entry, "mutu", ""));
				return SenjataKota.stack(bentuk, mutu == null ? mutuPita(pita) : mutu);
			}
			case "pusaka" -> {
				int min = Mth.clamp(num(entry, "mutu_min", pita / 2), 0, 5);
				int max = Mth.clamp(num(entry, "mutu_maks", pita / 2 + 1), min, 5);
				String form = str(entry, "bentuk", ArtifactItem.FORMS.get(random.nextInt(ArtifactItem.FORMS.size())));
				if (!ArtifactItem.FORMS.contains(form)) {
					form = ArtifactItem.PEDANG;
				}
				return Forging.buatStok(level, form, min + random.nextInt(max - min + 1), random);
			}
			case "pil" -> {
				return dariKolam(Harga.PILLS, pita, random);
			}
			case "harta" -> {
				return dariKolam(Harga.TREASURES, pita, random);
			}
			case "gelap" -> {
				return dariKolam(Harga.SHADOW, pita, random);
			}
			case "herba" -> {
				int min = Math.max(1, num(entry, "umur_min", 100));
				int max = Math.max(min, num(entry, "umur_maks", 300));
				return SpiritHerbs.randomFor(null, random, min, max);
			}
			case "inti" -> {
				int min = Mth.clamp(num(entry, "tingkat_min", 1), 1, 4);
				int max = Mth.clamp(num(entry, "tingkat_maks", 4), min, 4);
				Element[] all = Element.values();
				return BeastCores.create(all[random.nextInt(all.length)], min + random.nextInt(max - min + 1));
			}
			case "sesaji" -> {
				ItemStack pill = Harga.item("spirit_beast_pill");
				pill.setCount(Mth.clamp(num(entry, "jumlah", 1 + random.nextInt(3)), 1, pill.getMaxStackSize()));
				return pill;
			}
			default -> {
				ItemStack item = Harga.item(str(entry, "id", "jade_slip"));
				if (!item.isEmpty()) {
					item.setCount(Mth.clamp(num(entry, "jumlah", 1), 1, item.getMaxStackSize()));
				}
				return item;
			}
		}
	}

	private static ItemStack dariKolam(List<Harga.Ware> pool, int pita, RandomSource random) {
		List<Harga.Ware> fit = new ArrayList<>();
		for (Harga.Ware ware : pool) {
			if (pita >= ware.minBand() && pita <= ware.maxBand()) {
				fit.add(ware);
			}
		}
		if (fit.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack made = fit.get(random.nextInt(fit.size())).make().make(random, pita);
		return made == null ? ItemStack.EMPTY : made;
	}

	static SenjataKota.Mutu mutuPita(int pita) {
		return pita <= 1 ? SenjataKota.Mutu.FANA : pita == 2 ? SenjataKota.Mutu.ROH_RENDAH : pita == 3 ? SenjataKota.Mutu.ROH_MENENGAH
				: SenjataKota.Mutu.ROH_TINGGI;
	}

	private static String str(JsonObject o, String key, String fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
	}

	private static int num(JsonObject o, String key, int fallback) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : fallback;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Bidders

	/**
	 * {@code n} NPC bidders of the profile's sources other than {@code saingan} (the engine adds the rival groups itself):
	 * NPC sect masters ({@code sekte}; {@code iblis} only the demonic ones), the capital's recurring characters
	 * ({@code tokoh}) and the demon city's brokers ({@code iblis_tokoh}).
	 */
	public static List<Penawar> penawar(MinecraftServer server, long situs, ProfilLelang profil, RandomSource random, int n) {
		List<Penawar> pool = new ArrayList<>();
		DiplomasiService diplomasi = SekteApi.diplomasi();
		for (String source : profil.sumberPenawar()) {
			switch (source) {
				case "sekte", "iblis" -> {
					for (String sect : diplomasi.sects()) {
						if ("iblis".equals(source) && diplomasi.aliran(sect) != 2) {
							continue;
						}
						Component master = diplomasi.master(sect);
						Component name = diplomasi.name(sect);
						if (master != null && name != null) {
							pool.add(new Penawar(Component.translatable("kultivator.tempat_ekonomi.lelang.penawar.sekte", master, name), "sekte:" + sect));
						} else if (name != null) {
							pool.add(new Penawar(Component.translatable("kultivator.tempat_ekonomi.lelang.penawar.utusan", name), "sekte:" + sect));
						}
					}
				}
				case "tokoh" -> {
					pool.add(new Penawar(Tokoh.nama(server, "gu_lianshan", situs), "tokoh"));
					pool.add(new Penawar(Tokoh.nama(server, "qian_duoduo", situs), "tokoh"));
				}
				case "iblis_tokoh" -> {
					for (int i = 0; i < 3; i++) {
						pool.add(new Penawar(Component.translatable("kultivator.tempat_ekonomi.lelang.penawar.bayangan." + i), "iblis"));
					}
				}
				default -> {
				}
			}
		}
		List<Penawar> out = new ArrayList<>();
		while (out.size() < n && !pool.isEmpty()) {
			out.add(pool.remove(random.nextInt(pool.size())));
		}
		return out;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Consignments (SavedData from the moment they are handed in)

	private static ListTag titipan(MinecraftServer server, long situs) {
		CompoundTag site = EkonomiData.get(server).situs(situs);
		ListTag list = site.getList(TITIP).orElse(null);
		if (list == null) {
			list = new ListTag();
			site.put(TITIP, list);
		}
		return list;
	}

	/** The highest NPC bid for consigned {@code stack} (see {@link #TITIP_PLAFON}). */
	public static long plafonTitip(ItemStack stack) {
		return Math.max(1L, Math.round(EkonomiApi.harga(stack) * stack.getCount() * TITIP_PLAFON * Barang.skala()));
	}

	/** The queued consignments of site {@code situs}, now put up as lots (status {@code dilelang}). */
	public static List<LotLelang> lotTitipan(MinecraftServer server, long situs) {
		List<LotLelang> out = new ArrayList<>();
		for (Tag each : titipan(server, situs)) {
			if (!(each instanceof CompoundTag entry) || !LembarKunci.Lelang.T_ANTRE.equals(entry.getStringOr("status", ""))) {
				continue;
			}
			ItemStack stack = Barang.muat(entry.get("barang"), server.registryAccess());
			UUID owner = uuid(entry.getStringOr("pemilik", ""));
			if (stack.isEmpty() || owner == null) {
				entry.putString("status", LembarKunci.Lelang.T_KEMBALI);
				continue;
			}
			entry.putString("status", LembarKunci.Lelang.T_DILELANG);
			out.add(new LotLelang(stack, Math.max(1L, entry.getLongOr("dasar", 1L)), owner, Component.literal(entry.getStringOr("nama_penjual", "?")),
					entry.getLongOr("id", 0L), false));
		}
		EkonomiData.get(server).touch();
		return out;
	}

	private static @Nullable CompoundTag cariTitip(MinecraftServer server, long situs, long id) {
		for (Tag each : titipan(server, situs)) {
			if (each instanceof CompoundTag entry && entry.getLongOr("id", -1L) == id) {
				return entry;
			}
		}
		return null;
	}

	/** A consigned lot sold for {@code harga}; the seller is owed the price less {@code komisi}. */
	public static void terjual(MinecraftServer server, long situs, long id, long harga, long komisi) {
		CompoundTag entry = cariTitip(server, situs, id);
		if (entry == null) {
			return;
		}
		long net = Math.max(0L, harga - komisi);
		entry.putString("status", LembarKunci.Lelang.T_TERJUAL);
		entry.putLong("hasil", net);
		entry.putLong("hari_akhir", Barang.hari(server));
		UUID owner = uuid(entry.getStringOr("pemilik", ""));
		if (owner == null) {
			return;
		}
		EkonomiData data = EkonomiData.get(server);
		CompoundTag player = data.pemain(owner);
		player.putLong(UTANG, player.getLongOr(UTANG, 0L) + net);
		Component name = Barang.muat(entry.get("barang"), server.registryAccess()).getHoverName();
		hasil(server, owner, name, net, LembarKunci.Lelang.H_TITIP);
		data.touch();
		ServerPlayer online = server.getPlayerList().getPlayer(owner);
		if (online != null) {
			Msg.chat(online, Component.translatable("kultivator.tempat_ekonomi.lelang.titipan_laku", name, NilaiBatu.text(harga), NilaiBatu.text(komisi),
					NilaiBatu.text(net)).withStyle(ChatFormatting.GOLD));
		}
	}

	/** A consigned lot nobody bought: the piece waits under "Hasil" for its owner. */
	public static void kembali(MinecraftServer server, long situs, long id) {
		CompoundTag entry = cariTitip(server, situs, id);
		if (entry == null) {
			return;
		}
		entry.putString("status", LembarKunci.Lelang.T_KEMBALI);
		entry.putLong("hari_akhir", Barang.hari(server));
		UUID owner = uuid(entry.getStringOr("pemilik", ""));
		if (owner == null) {
			return;
		}
		EkonomiData data = EkonomiData.get(server);
		data.daftar(owner, KEMBALI).add(entry.get("barang") == null ? new CompoundTag() : entry.get("barang").copy());
		entry.remove("barang");
		data.touch();
		ServerPlayer online = server.getPlayerList().getPlayer(owner);
		if (online != null) {
			Msg.chat(online, Component.translatable("kultivator.tempat_ekonomi.lelang.titipan_kembali").withStyle(ChatFormatting.YELLOW));
		}
	}

	/** The session of {@code situs} ended: consigned lots it never reached come back to their owners. */
	public static void selesai(MinecraftServer server, long situs) {
		for (Tag each : List.copyOf(titipan(server, situs))) {
			if (each instanceof CompoundTag entry && LembarKunci.Lelang.T_DILELANG.equals(entry.getStringOr("status", ""))) {
				kembali(server, situs, entry.getLongOr("id", 0L));
			}
		}
		// finished entries older than three days leave the list
		long hari = Barang.hari(server);
		titipan(server, situs).removeIf(each -> each instanceof CompoundTag entry && entry.contains("hari_akhir")
				&& hari - entry.getLongOr("hari_akhir", hari) > 3L);
		EkonomiData.get(server).touch();
		segarkan(server, situs);
	}

	/** A player won a lot of a profile session (shown under "Hasil"). */
	public static void menang(ServerPlayer player, long situs, Component nama, long harga) {
		hasil(player.level().getServer(), player.getUUID(), nama, harga, LembarKunci.Lelang.H_MENANG);
		EkonomiData.get(player.level().getServer()).touch();
	}

	private static void hasil(MinecraftServer server, UUID owner, Component nama, long nb, String jenis) {
		ListTag list = EkonomiData.get(server).daftar(owner, HASIL);
		CompoundTag row = new CompoundTag();
		row.store("nama", ComponentSerialization.CODEC, nama);
		row.putLong("nb", nb);
		row.putString("jenis", jenis);
		row.putLong("hari", Barang.hari(server));
		list.add(row);
		while (list.size() > HASIL_MAKS) {
			list.remove(0);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Live refresh

	/** Rebuilds the {@code lelang} screen of every player watching session {@code kunci}. */
	public static void segarkan(MinecraftServer server, long kunci) {
		if (MELIHAT.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Long> entry : List.copyOf(MELIHAT.entrySet())) {
			if (entry.getValue() != kunci) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !TempatLayar.sedangMelihat(player, LembarKunci.Lelang.LAYAR)) {
				MELIHAT.remove(entry.getKey());
				continue;
			}
			int npcId = TempatLayar.npcTerbuka(player);
			CompoundTag lembar = bangun(player, npcId);
			if (lembar != null) {
				TempatLayar.segarkan(player, LembarKunci.Lelang.LAYAR, npcId, lembar);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Screen lelang

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		long key = BalaiLelang.kunci(npc);
		MELIHAT.put(player.getUUID(), key);
		ProfilLelang running = BalaiLelang.profil(key);
		ProfilLelang profil = running != null ? running : ibuKota(server, key) ? ProfilLelang.AGUNG : ProfilLelang.KOTA;
		boolean agung = !profil.kota();
		CompoundTag tag = Lembar.kepala(player, agung ? "kultivator.tempat_ekonomi.lelang.judul.agung" : "kultivator.tempat_ekonomi.lelang.judul.kota",
				LembarKunci.GAYA_KAIN, LembarKunci.DISTRIK_PASAR, npc);
		if (!Kota.config().auction) {
			Lembar.kunci(tag, Component.translatable("kultivator.tempat_ekonomi.tutup"), null);
			return tag;
		}
		if (running != null) {
			BalaiLelang.lembarSesi(player, key, tag);
		} else {
			tag.putString(LembarKunci.Lelang.PROFIL, agung ? LembarKunci.Lelang.PROFIL_AGUNG : LembarKunci.Lelang.PROFIL_KOTA);
			tag.putString(LembarKunci.STATUS, LembarKunci.Lelang.S_BELUM);
			tag.putFloat(LembarKunci.Lelang.KOMISI, (float) profil.komisiBerlaku());
			tag.putIntArray(LembarKunci.Lelang.LANGKAH, new int[] {5, 10, 25});
			long until;
			if (agung) {
				until = sampaiAgung(server);
				long day = Barang.hari(server) + (Barang.jamDunia(server) + until) / 24000L;
				LembarKunci.teks(tag, LembarKunci.Lelang.JADWAL, Component.translatable("kultivator.tempat_ekonomi.lelang.jadwal.agung", day,
						jam(MULAI), jam(AKHIR)));
			} else {
				until = Math.floorMod(12000L - Barang.jamDunia(server), 24000L);
				LembarKunci.teks(tag, LembarKunci.Lelang.JADWAL, Component.translatable("kultivator.tempat_ekonomi.lelang.jadwal.kota", jam(12000L)));
			}
			tag.putLong(LembarKunci.SISA_TICK, until);
		}
		tag.put(LembarKunci.Lelang.TITIP, titipLembar(player, key, profil));
		ListTag rows = new ListTag();
		ListTag results = EkonomiData.get(server).pemain(player.getUUID()).getListOrEmpty(HASIL);
		for (int i = results.size() - 1; i >= 0; i--) {
			CompoundTag each = results.getCompoundOrEmpty(i);
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, each.read("nama", ComponentSerialization.CODEC).orElse(Component.empty()));
			row.putLong(LembarKunci.NB, each.getLongOr("nb", 0L));
			row.putString(LembarKunci.JENIS, each.getStringOr("jenis", LembarKunci.Lelang.H_MENANG));
			row.putInt(LembarKunci.HARI, (int) each.getLongOr("hari", 0L));
			rows.add(row);
		}
		int back = EkonomiData.get(server).pemain(player.getUUID()).getListOrEmpty(KEMBALI).size();
		if (back > 0) {
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.lelang.barang_kembali", back));
			row.putLong(LembarKunci.NB, 0L);
			row.putString(LembarKunci.JENIS, LembarKunci.Lelang.H_UTANG);
			row.putInt(LembarKunci.HARI, (int) Barang.hari(server));
			rows.add(0, row);
		}
		tag.put(LembarKunci.Lelang.HASIL, rows);
		tag.putLong(LembarKunci.Lelang.UTANG, utang(player));
		return tag;
	}

	private static Component jam(long time) {
		long hour = (time / 1000L + 6L) % 24L;
		return Component.literal(String.format(java.util.Locale.ROOT, "%02d.%02d", hour, time % 1000L * 60L / 1000L));
	}

	private static long utang(ServerPlayer player) {
		return EkonomiData.get(player.level().getServer()).pemain(player.getUUID()).getLongOr(UTANG, 0L) + BalaiLelang.utangKota(player);
	}

	private static CompoundTag titipLembar(ServerPlayer player, long key, ProfilLelang profil) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = new CompoundTag();
		tag.putInt(LembarKunci.MAKS, profil.titipMaks());
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		if (!hand.isEmpty()) {
			CompoundTag tangan = new CompoundTag();
			LembarKunci.barang(tangan, hand, player.registryAccess());
			tangan.putLong(LembarKunci.Lelang.PERKIRAAN, profil.kota() ? Math.max(1L, EkonomiApi.harga(hand)) : Math.max(1L, EkonomiApi.harga(hand) * hand.getCount()));
			tag.put(LembarKunci.TANGAN, tangan);
		}
		Component why;
		if (profil.kota()) {
			tag.put(LembarKunci.DAFTAR, BalaiLelang.titipanKota(player, key));
			why = !BalaiLelang.berjalan(key) ? Component.translatable("kultivator.tempat_ekonomi.lelang.titip.kota_belum")
					: BalaiLelang.titipSisaKota(player, key) <= 0 ? Component.translatable("kultivator.kota.lelang.titip_penuh", profil.titipMaks())
					: hand.isEmpty() ? Component.translatable("kultivator.tempat_ekonomi.lelang.titip.pegang") : null;
		} else {
			ListTag rows = new ListTag();
			int queued = 0;
			for (Tag each : titipan(server, key)) {
				if (!(each instanceof CompoundTag entry) || !player.getUUID().toString().equals(entry.getStringOr("pemilik", ""))) {
					continue;
				}
				String status = entry.getStringOr("status", LembarKunci.Lelang.T_ANTRE);
				if (LembarKunci.Lelang.T_ANTRE.equals(status)) {
					queued++;
				}
				CompoundTag row = new CompoundTag();
				row.putString(LembarKunci.ID, Long.toString(entry.getLongOr("id", 0L)));
				if (entry.contains("barang")) {
					LembarKunci.barang(row, Barang.muat(entry.get("barang"), player.registryAccess()), player.registryAccess());
				} else {
					LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.lelang.titip.dikembalikan"));
				}
				row.putLong(LembarKunci.Lelang.DASAR, entry.getLongOr("dasar", 1L));
				row.putString(LembarKunci.STATUS, status);
				row.putLong(LembarKunci.Lelang.HASIL, entry.getLongOr("hasil", 0L));
				rows.add(row);
			}
			tag.put(LembarKunci.DAFTAR, rows);
			why = queued >= profil.titipMaks() ? Component.translatable("kultivator.kota.lelang.titip_penuh", profil.titipMaks())
					: hand.isEmpty() ? Component.translatable("kultivator.tempat_ekonomi.lelang.titip.pegang")
					: !Barang.bolehDijual(hand) ? Component.translatable(Barang.berisi(hand) ? "kultivator.ekonomi.cincin_berisi"
					: "kultivator.tempat_ekonomi.lelang.titip.tolak")
					: Barang.baruDibeli(hand, Barang.hari(server)) ? Component.translatable("kultivator.tempat_ekonomi.asal_toko", Barang.ASAL_HARI) : null;
		}
		tag.putBoolean(LembarKunci.BISA, why == null);
		if (why != null) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, why);
		}
		return tag;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null || !Kota.config().auction) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		long key = BalaiLelang.kunci(npc);
		String[] parts = LembarKunci.urai(op);
		switch (parts[0]) {
			case LembarKunci.Lelang.OP_TAWAR -> {
				int percent = LembarKunci.argInt(parts, 1, 5);
				Component why = BalaiLelang.tawarLembar(player, key, percent);
				if (why != null) {
					Lembar.pesan(player, why, false);
				} else {
					Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lelang.ditawar"), true);
				}
			}
			case LembarKunci.Lelang.OP_TITIP -> {
				ProfilLelang running = BalaiLelang.profil(key);
				ProfilLelang profil = running != null ? running : ibuKota(server, key) ? ProfilLelang.AGUNG : ProfilLelang.KOTA;
				if (profil.kota()) {
					if (!BalaiLelang.titipKota(player, key)) {
						Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lelang.titip.kota_belum"), false);
					}
				} else {
					titip(player, key, profil, LembarKunci.argLong(parts, 1, 0L));
				}
			}
			case LembarKunci.Lelang.OP_TARIK -> tarik(player, key, LembarKunci.argLong(parts, 1, -1L));
			case LembarKunci.Lelang.OP_AMBIL_HASIL -> ambilHasil(player);
			default -> {
			}
		}
	}

	/** Hands the stack in the main hand in as a consignment for the next Grand Auction of {@code key}. */
	private static void titip(ServerPlayer player, long key, ProfilLelang profil, long dasar) {
		MinecraftServer server = player.level().getServer();
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		CompoundTag check = titipLembar(player, key, profil);
		if (!check.getBooleanOr(LembarKunci.BISA, false)) {
			Lembar.pesan(player, LembarKunci.teks(check, LembarKunci.ALASAN), false);
			return;
		}
		long value = Math.max(1L, EkonomiApi.harga(hand) * hand.getCount());
		long base = Mth.clamp(dasar <= 0L ? value : dasar, Math.max(1L, (long) Math.ceil(value * DASAR_MIN)), (long) Math.floor(value * DASAR_MAKS));
		EkonomiData data = EkonomiData.get(server);
		CompoundTag entry = new CompoundTag();
		entry.putLong("id", data.serial("lelang_titip"));
		entry.putString("pemilik", player.getUUID().toString());
		entry.putString("nama_penjual", player.getGameProfile().name());
		entry.put("barang", Barang.simpan(hand.copy(), player.registryAccess()));
		entry.putLong("dasar", base);
		entry.putString("status", LembarKunci.Lelang.T_ANTRE);
		entry.putLong("hari", Barang.hari(server));
		titipan(server, key).add(entry);
		Component name = hand.getHoverName();
		hand.setCount(0);
		data.touch();
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lelang.titip.diterima", name, NilaiBatu.text(base)), true);
		long ceiling = plafonTitip(Barang.muat(entry.get("barang"), player.registryAccess()));
		if (base > ceiling) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lelang.titip.plafon", NilaiBatu.text(ceiling)), false);
		}
	}

	/** Takes back a consignment still waiting for its session. */
	private static void tarik(ServerPlayer player, long key, long id) {
		MinecraftServer server = player.level().getServer();
		CompoundTag entry = cariTitip(server, key, id);
		if (entry == null || !player.getUUID().toString().equals(entry.getStringOr("pemilik", ""))
				|| !LembarKunci.Lelang.T_ANTRE.equals(entry.getStringOr("status", ""))) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lelang.tarik.tidak_bisa"), false);
			return;
		}
		ItemStack stack = Barang.muat(entry.get("barang"), player.registryAccess());
		titipan(server, key).remove(entry);
		EkonomiData.get(server).touch();
		Barang.beri(player, stack);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lelang.tarik.ok", stack.getHoverName()), true);
	}

	/** Pays the stones owed (Grand Auction and town consignments) and hands back unsold pieces. */
	private static void ambilHasil(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		CompoundTag tag = data.pemain(player.getUUID());
		long owed = tag.getLongOr(UTANG, 0L);
		boolean any = false;
		if (owed > 0L) {
			tag.remove(UTANG);
			NilaiBatu.give(player, owed);
			any = true;
		}
		ListTag back = tag.getListOrEmpty(KEMBALI);
		for (Tag each : back) {
			ItemStack stack = Barang.muat(each, player.registryAccess());
			if (!stack.isEmpty()) {
				Barang.beri(player, stack);
				any = true;
			}
		}
		tag.remove(KEMBALI);
		long town = BalaiLelang.utangKota(player);
		if (town > 0L) {
			BalaiLelang.bayarUtangKota(player);
			any = true;
		}
		data.touch();
		if (any) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.lelang.hasil.diambil", NilaiBatu.text(owed + town)), true);
			if (owed + town >= 1000L) {
				Advancements.grant(player, "tempat/lelang_agung");
			}
		} else {
			Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.lelang.hasil.kosong"));
		}
	}

	private static @Nullable UUID uuid(String text) {
		try {
			return text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/** {@code uji ekonomi lelang}: the Grand Auction at the auctioneer the player looks at (or the nearest). */
	static boolean uji(ServerPlayer player, boolean agung) {
		KultivatorNpc best = null;
		double bestSq = 16.0D * 16.0D;
		for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(16.0D), e -> e instanceof KultivatorNpc npc
				&& npc.role() == NpcRole.JURU_LELANG)) {
			double d = entity.distanceToSqr(player);
			if (d < bestSq) {
				bestSq = d;
				best = (KultivatorNpc) entity;
			}
		}
		if (best == null) {
			return false;
		}
		BalaiLelang.mulaiUji(player, best, agung ? ProfilLelang.AGUNG : ProfilLelang.KOTA);
		return true;
	}

	private LelangAgung() {
	}
}
