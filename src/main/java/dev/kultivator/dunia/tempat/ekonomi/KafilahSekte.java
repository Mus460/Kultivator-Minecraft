package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kafilah Dagang Sekte (DESIGN 38.M.4.2, 38.T.14.3 row EK): a hall of level 3 or more sends goods from the player's
 * inventory to a known town, capital or themed city with 0-3 following disciples as escort (away with reason
 * {@code kafilah} until the caravan is back). The trip takes {@code ceil(distance / 1500)} world days (at least 1); the
 * goods sell there at {@code Harga.value × demand}, the demand of the destination's kind read generically from every
 * {@code data/kultivator/tempat/permintaan/*.json} (38TK writes the themed ones; {@code TemaApi.permintaan} answers for a
 * themed site without a table), shifted ±0.1 every 7 days and held to 0.9-1.3 (0 = not bought there: the stack comes
 * back). Costs: 5 % of the goods' value on departure (NB) and 2 PK per escort per day from the hall pool. Bandits: 10 % a
 * day in the third ring and beyond (3000+ blocks from the world's centre) without escort, 6 % with one or with escorts
 * below Foundation, 2 % with two Foundation escorts or more; an attack loses 30-60 % of the load, and the escorts come
 * home wounded but alive (never killed, 36.0 rule 3). Net profit is held to +30 % of the goods' value and to
 * {@code 200 × (band + 1)} NB a week per sect. One caravan at a time, two from hall level 6.
 * <p>
 * The owner within 64 blocks of a caravan's place on its road sees its body (a cart, or the camel string / junk of
 * {@code TemaApi.tubuhKafilah}); nothing is stored in chunks. Ledger lines: {@code kafilah} (NB), {@code ongkos},
 * {@code gaji} (PK).
 */
public final class KafilahSekte {

	public static final int LEVEL_MIN = 3;
	public static final int LEVEL_DUA = 6;
	public static final int PENGAWAL_MAKS = 3;
	public static final int MUATAN_MAKS = 9;
	public static final double ONGKOS = 0.05D;
	public static final int GAJI_PK = 2;
	public static final double LABA_MAKS = 0.30D;
	public static final double JARAK_HARI = 1500.0D;
	public static final double CINCIN_BAHAYA = 3000.0D;
	static final String KUNCI = "kafilah_sekte";
	private static final String UTANG = "kafilah_utang";
	private static final String KEMBALI = "kafilah_kembali";
	/** Caravan ids live above this value (road caravans count from 1). */
	private static final long ID_DASAR = 1L << 40;
	private static final Map<Long, GerobakEntity> TUBUH = new HashMap<>();
	private static @Nullable MinecraftServer server;

	static void init() {
		Halls.addReturnHandler("kafilah", (level, owner, stored, info) -> null);
	}

	static void setServer(@Nullable MinecraftServer current) {
		server = current;
		if (current == null) {
			TUBUH.values().forEach(GerobakEntity::discard);
			TUBUH.clear();
		}
	}

	private static ListTag daftar(MinecraftServer current, UUID owner) {
		return EkonomiData.get(current).daftar(owner, KUNCI);
	}

	/** True while sect caravan {@code id} is on the road (its cart body asks). */
	static boolean hidup(long id) {
		MinecraftServer current = server;
		if (current == null || id < ID_DASAR) {
			return false;
		}
		EkonomiData data = EkonomiData.get(current);
		for (UUID owner : data.pemainIds()) {
			for (Tag each : data.pemain(owner).getListOrEmpty(KUNCI)) {
				if (each instanceof CompoundTag c && c.getLongOr("id", 0L) == id && !c.getBooleanOr("tiba", false)) {
					return true;
				}
			}
		}
		return false;
	}

	static int maks(UUID owner) {
		int level = Halls.level(owner);
		return level >= LEVEL_DUA ? 2 : level >= LEVEL_MIN ? 1 : 0;
	}

	static long batasMingguan(int band) {
		return 200L * (band + 1);
	}

	private static int aktif(MinecraftServer current, UUID owner) {
		int n = 0;
		for (Tag each : daftar(current, owner)) {
			if (each instanceof CompoundTag c && !c.getBooleanOr("tiba", false)) {
				n++;
			}
		}
		return n;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Demand

	/** The site kind of town {@code town} ({@code kota/kota}, {@code kota/kota_besar}, {@code kota_tema/pelabuhan}). */
	static String jenisSitus(MinecraftServer current, long town) {
		DuniaSites.Site site = DuniaSites.byId(current, town);
		return site == null ? "kota/kota" : site.kind();
	}

	/** The demand for {@code stack} at town {@code town} this week: 0 (not bought) or 0.9-1.3. */
	public static double permintaan(MinecraftServer current, long town, ItemStack stack) {
		String kind = jenisSitus(current, town);
		Map<String, Double> table = EkonomiReload.permintaan(kind);
		double base;
		if (table != null) {
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			Double factor = table.get(id);
			if (factor == null) {
				factor = table.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
			}
			base = factor != null ? factor : table.getOrDefault("*", 1.0D);
		} else if (kind.startsWith("kota_tema")) {
			base = TemaApi.permintaan(town, stack.getItem());
		} else {
			base = 1.0D;
		}
		if (base <= 0.0D) {
			return 0.0D;
		}
		long week = Math.floorDiv(Barang.hari(current), 7L);
		RandomSource random = RandomSource.create(town * 31L + week * 7919L + BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode());
		double shift = (random.nextDouble() - 0.5D) * 0.2D;
		return Mth.clamp(base + shift, 0.9D, 1.3D);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Plan (what the ledger offers) and departure

	private record Tujuan(long town, BlockPos pos, int jarak, int hari) {
	}

	private static @Nullable BlockPos asal(ServerPlayer player) {
		Halls.HallView hall = Halls.hallOf(player.getUUID());
		if (hall != null && Level.OVERWORLD.equals(hall.dim())) {
			return hall.pos();
		}
		return Level.OVERWORLD.equals(player.level().dimension()) ? player.blockPosition() : null;
	}

	/** Known overworld towns 200-12000 blocks from the hall, nearest first (at most 12). */
	private static List<Tujuan> tujuan(ServerPlayer player) {
		MinecraftServer current = player.level().getServer();
		BlockPos from = asal(player);
		List<Tujuan> out = new ArrayList<>();
		if (from == null) {
			return out;
		}
		for (long town : Towns.known(current)) {
			BlockPos centre = Towns.centre(current, town);
			if (centre == null || !Level.OVERWORLD.equals(Towns.dimension(current, town))) {
				continue;
			}
			int d = (int) Math.sqrt(centre.distSqr(new BlockPos(from.getX(), centre.getY(), from.getZ())));
			if (d >= 200 && d <= 12000) {
				out.add(new Tujuan(town, centre, d, Math.max(1, (int) Math.ceil(d / JARAK_HARI))));
			}
		}
		out.sort((a, b) -> Integer.compare(a.jarak, b.jarak));
		return out.size() > 12 ? new ArrayList<>(out.subList(0, 12)) : out;
	}

	/** Inventory slots of goods a caravan may carry (the common 38 sell filter), at most 18. */
	private static List<Integer> muatan(ServerPlayer player) {
		List<Integer> out = new ArrayList<>();
		long hari = Barang.hari(player.level().getServer());
		for (int i = 0; i < player.getInventory().getContainerSize() && out.size() < 18; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (Barang.bolehDijual(stack) && !Barang.baruDibeli(stack, hari)) {
				out.add(i);
			}
		}
		return out;
	}

	/** Following disciples near the player who may escort (at most 6 offered). */
	private static List<DiscipleEntity> murid(ServerPlayer player) {
		List<DiscipleEntity> out = new ArrayList<>();
		for (DiscipleEntity disciple : Disciples.loaded(player.getUUID())) {
			if (disciple.role() == DiscipleEntity.Role.DISCIPLE && disciple.level() == player.level()
					&& disciple.distanceToSqr(player) <= Disciples.ACTION_RANGE * Disciples.ACTION_RANGE && out.size() < 6) {
				out.add(disciple);
			}
		}
		return out;
	}

	/** {@code kafilah_kirim:TUJUAN:M1.M2:S1.S2.S3} from the ledger (indexes into the plan lists). */
	static void aksi(ServerPlayer player, String[] parts) {
		if (!EkonomiConfig.get().kafilah) {
			return;
		}
		MinecraftServer current = player.level().getServer();
		UUID owner = player.getUUID();
		if (maks(owner) <= 0) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.level", LEVEL_MIN), false);
			return;
		}
		if (aktif(current, owner) >= maks(owner)) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.penuh", maks(owner)), false);
			return;
		}
		List<Tujuan> places = tujuan(player);
		int target = LembarKunci.argInt(parts, 1, -1);
		if (target < 0 || target >= places.size()) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.pilih_tujuan"), false);
			return;
		}
		List<DiscipleEntity> disciples = murid(player);
		List<DiscipleEntity> escorts = new ArrayList<>();
		for (int index : indeks(LembarKunci.arg(parts, 2))) {
			if (index >= 0 && index < disciples.size() && !escorts.contains(disciples.get(index)) && escorts.size() < PENGAWAL_MAKS) {
				escorts.add(disciples.get(index));
			}
		}
		List<Integer> slots = muatan(player);
		List<ItemStack> load = new ArrayList<>();
		List<Integer> taken = new ArrayList<>();
		long value = 0L;
		for (int index : indeks(LembarKunci.arg(parts, 3))) {
			if (index >= 0 && index < slots.size() && !taken.contains(slots.get(index)) && taken.size() < MUATAN_MAKS) {
				ItemStack stack = player.getInventory().getItem(slots.get(index));
				if (Barang.bolehDijual(stack)) {
					taken.add(slots.get(index));
					value += EkonomiApi.harga(stack) * stack.getCount();
				}
			}
		}
		if (taken.isEmpty()) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.tanpa_muatan"), false);
			return;
		}
		long fee = Math.max(1L, Math.round(value * ONGKOS));
		if (!NilaiBatu.pay(player, fee)) {
			Lembar.kurangUang(player, fee);
			return;
		}
		for (int slot : taken) {
			ItemStack stack = player.getInventory().getItem(slot);
			load.add(stack.copy());
			stack.setCount(0);
		}
		player.getInventory().setChanged();
		Tujuan place = places.get(target);
		BlockPos from = asal(player);
		EkonomiData data = EkonomiData.get(current);
		long id = ID_DASAR + data.serial(KUNCI);
		long now = Barang.waktu(current);
		CompoundTag caravan = new CompoundTag();
		caravan.putLong("id", id);
		caravan.putLong("tujuan", place.town);
		caravan.putInt("ax", from.getX());
		caravan.putInt("az", from.getZ());
		caravan.putInt("tx", place.pos.getX());
		caravan.putInt("tz", place.pos.getZ());
		caravan.putLong("berangkat", now);
		caravan.putLong("tiba_pada", now + place.hari * 24000L);
		caravan.putInt("hari", place.hari);
		caravan.putLong("nilai", value);
		caravan.putInt("pita", Barang.pita(player));
		caravan.putString("rupa", TemaApi.rupaKafilah(0L, place.town));
		ListTag goods = new ListTag();
		for (ItemStack stack : load) {
			goods.add(Barang.simpan(stack, player.registryAccess()));
		}
		caravan.put("muatan", goods);
		ListTag guards = new ListTag();
		int fondasi = 0;
		for (DiscipleEntity escort : escorts) {
			CompoundTag guard = new CompoundTag();
			guard.putString("jiwa", escort.soulId().toString());
			guard.putString("nama", escort.getDisplayName().getString());
			guard.putInt("ranah", escort.data().realm().ordinal());
			if (escort.data().realm().ordinal() >= Realm.FOUNDATION.ordinal()) {
				fondasi++;
			}
			guards.add(guard);
			CompoundTag info = new CompoundTag();
			info.putLong("kafilah", id);
			Halls.storeAway(owner, escort, now + (place.hari + 1L) * 24000L, "kafilah", info);
		}
		caravan.put("pengawal", guards);
		caravan.putInt("fondasi", fondasi);
		daftar(current, owner).add(caravan);
		data.touch();
		BukuKas.catat(current, owner, LembarKunci.BukuKas.SUMBER_ONGKOS, 0, -fee, "kultivator.tempat_ekonomi.kas.catatan.ongkos", KotaNames.town(place.town)
				.getString(), place.hari);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.berangkat", KotaNames.town(place.town), place.hari,
				NilaiBatu.text(fee)), true);
	}

	private static List<Integer> indeks(String text) {
		List<Integer> out = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return out;
		}
		for (String part : text.split("\\.")) {
			try {
				out.add(Integer.parseInt(part));
			} catch (NumberFormatException ignored) {
				// skipped
			}
		}
		return out;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Days on the road

	/** A new world day: wages, the bandit roll of each caravan still travelling, arrivals and sales. */
	static void hariBaru(MinecraftServer current, long hari) {
		if (!EkonomiConfig.get().kafilah) {
			return;
		}
		EkonomiData data = EkonomiData.get(current);
		long now = Barang.waktu(current);
		for (UUID owner : data.pemainIds()) {
			ListTag list = data.pemain(owner).getListOrEmpty(KUNCI);
			for (int i = list.size() - 1; i >= 0; i--) {
				if (!(list.get(i) instanceof CompoundTag caravan)) {
					list.remove(i);
					continue;
				}
				if (caravan.getBooleanOr("tiba", false)) {
					if (hari - caravan.getLongOr("hari_tiba", hari) > 7L) {
						list.remove(i);
					}
					continue;
				}
				if (caravan.getLongOr("hari_dicek", Long.MIN_VALUE) >= hari) {
					continue;
				}
				caravan.putLong("hari_dicek", hari);
				gaji(current, owner, caravan);
				if (now >= caravan.getLongOr("tiba_pada", now)) {
					tiba(current, owner, caravan, hari);
				} else {
					begal(current, owner, caravan, hari);
				}
			}
		}
		data.touch();
	}

	private static void gaji(MinecraftServer current, UUID owner, CompoundTag caravan) {
		int guards = caravan.getListOrEmpty("pengawal").size();
		if (guards <= 0) {
			return;
		}
		int wage = GAJI_PK * guards;
		if (Halls.spendPool(owner, wage)) {
			BukuKas.catat(current, owner, LembarKunci.BukuKas.SUMBER_GAJI, -wage, 0L, "kultivator.tempat_ekonomi.kas.catatan.gaji", guards);
		} else {
			caravan.putInt("gaji_tunggak", caravan.getIntOr("gaji_tunggak", 0) + wage);
		}
	}

	private static Vec3 posisi(CompoundTag caravan, long now) {
		long start = caravan.getLongOr("berangkat", now);
		long end = caravan.getLongOr("tiba_pada", now);
		double t = end <= start ? 1.0D : Mth.clamp((now - start) / (double) (end - start), 0.0D, 1.0D);
		double x = caravan.getIntOr("ax", 0) + (caravan.getIntOr("tx", 0) - caravan.getIntOr("ax", 0)) * t + 0.5D;
		double z = caravan.getIntOr("az", 0) + (caravan.getIntOr("tz", 0) - caravan.getIntOr("az", 0)) * t + 0.5D;
		return new Vec3(x, 0.0D, z);
	}

	/** Daily bandit chance in percent at the caravan's place. */
	static int risiko(CompoundTag caravan, long now) {
		Vec3 pos = posisi(caravan, now);
		if (Math.hypot(pos.x, pos.z) < CINCIN_BAHAYA) {
			return 0;
		}
		int guards = caravan.getListOrEmpty("pengawal").size();
		int fondasi = caravan.getIntOr("fondasi", 0);
		return fondasi >= 2 ? 2 : guards >= 1 ? 6 : 10;
	}

	private static void begal(MinecraftServer current, UUID owner, CompoundTag caravan, long hari) {
		int chance = risiko(caravan, Barang.waktu(current));
		RandomSource random = RandomSource.create(caravan.getLongOr("id", 0L) * 977L + hari * 31L ^ current.overworld().getSeed());
		if (chance <= 0 || random.nextInt(100) >= chance) {
			return;
		}
		double loss = 0.30D + random.nextDouble() * 0.30D;
		caravan.putDouble("rugi", Math.min(0.9D, caravan.getDoubleOr("rugi", 0.0D) + loss * (1.0D - caravan.getDoubleOr("rugi", 0.0D))));
		caravan.putInt("diserang", caravan.getIntOr("diserang", 0) + 1);
		ServerPlayer online = current.getPlayerList().getPlayer(owner);
		if (online != null) {
			Msg.chat(online, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.diserang", KotaNames.town(caravan.getLongOr("tujuan", 0L)),
					Math.round(loss * 100.0D), caravan.getListOrEmpty("pengawal").isEmpty() ? Component.empty()
							: Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.pengawal_luka")).withStyle(ChatFormatting.RED));
		}
		Halls.log(owner, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.log_diserang", KotaNames.town(caravan.getLongOr("tujuan", 0L))));
	}

	/** Arrival: the goods sell at the destination's demand, the load lost to bandits stays lost, profit capped. */
	private static void tiba(MinecraftServer current, UUID owner, CompoundTag caravan, long hari) {
		long town = caravan.getLongOr("tujuan", 0L);
		double kept = 1.0D - caravan.getDoubleOr("rugi", 0.0D);
		long value = 0L;
		long gross = 0L;
		ListTag back = new ListTag();
		for (Tag each : caravan.getListOrEmpty("muatan")) {
			ItemStack stack = Barang.muat(each, current.registryAccess());
			if (stack.isEmpty()) {
				continue;
			}
			long worth = EkonomiApi.harga(stack) * stack.getCount();
			double demand = permintaan(current, town, stack);
			if (demand <= 0.0D) {
				back.add(each.copy());
				continue;
			}
			value += worth;
			gross += Math.round(worth * demand * kept);
		}
		int band = caravan.getIntOr("pita", 0);
		long capByValue = Math.round(value * (1.0D + LABA_MAKS) * kept);
		gross = Math.min(gross, capByValue);
		EkonomiData data = EkonomiData.get(current);
		long weekProfit = data.mingguan(owner, hari, "kafilah_laba");
		long room = Math.max(0L, batasMingguan(band) - weekProfit);
		long profit = gross - Math.round(value * kept);
		if (profit > room) {
			gross -= profit - room;
			profit = room;
		}
		if (profit > 0L) {
			data.tambahMingguan(owner, hari, "kafilah_laba", profit);
		}
		caravan.putBoolean("tiba", true);
		caravan.putLong("hari_tiba", hari);
		caravan.putLong("hasil", gross);
		caravan.putLong("laba", profit);
		CompoundTag player = data.pemain(owner);
		player.putLong(UTANG, player.getLongOr(UTANG, 0L) + Math.max(0L, gross));
		if (!back.isEmpty()) {
			ListTag returned = data.daftar(owner, KEMBALI);
			returned.addAll(back);
		}
		data.touch();
		BukuKas.catat(current, owner, LembarKunci.BukuKas.SUMBER_KAFILAH, 0, gross, "kultivator.tempat_ekonomi.kas.catatan.kafilah",
				KotaNames.town(town).getString(), profit);
		Halls.log(owner, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.log_tiba", KotaNames.town(town), gross));
		ServerPlayer online = current.getPlayerList().getPlayer(owner);
		if (online != null) {
			bayar(online);
			if (profit > 0L) {
				Advancements.grant(online, "tempat/kafilah_untung");
			}
		}
	}

	/** Pays what arrived caravans earned and hands back unsold goods (login, the ledger). */
	static void bayar(ServerPlayer player) {
		EkonomiData data = EkonomiData.get(player.level().getServer());
		CompoundTag tag = data.pemain(player.getUUID());
		long owed = tag.getLongOr(UTANG, 0L);
		if (owed > 0L) {
			tag.remove(UTANG);
			NilaiBatu.give(player, owed);
			Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.dibayar", NilaiBatu.text(owed)).withStyle(ChatFormatting.GOLD));
		}
		ListTag back = tag.getListOrEmpty(KEMBALI);
		if (!back.isEmpty()) {
			for (Tag each : back) {
				Barang.beri(player, Barang.muat(each, player.registryAccess()));
			}
			tag.remove(KEMBALI);
			Msg.chat(player, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.tak_laku").withStyle(ChatFormatting.YELLOW));
		}
		data.touch();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Bodies (owner near the caravan's place)

	/** Every 20 ticks: the owner within 64 blocks of a travelling caravan sees its cart rolling on. */
	static void tick(MinecraftServer current) {
		server = current;
		if (!EkonomiConfig.get().kafilah) {
			return;
		}
		long now = Barang.waktu(current);
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (ServerPlayer player : current.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || !Level.OVERWORLD.equals(level.dimension())) {
				continue;
			}
			for (Tag each : EkonomiData.get(current).pemain(player.getUUID()).getListOrEmpty(KUNCI)) {
				if (!(each instanceof CompoundTag caravan) || caravan.getBooleanOr("tiba", false)) {
					continue;
				}
				Vec3 pos = posisi(caravan, now);
				if (Math.hypot(player.getX() - pos.x, player.getZ() - pos.z) > KafilahJalan.TUBUH_JARAK
						|| !level.isPositionEntityTicking(BlockPos.containing(pos.x, player.getY(), pos.z))) {
					continue;
				}
				long id = caravan.getLongOr("id", 0L);
				seen.add(id);
				GerobakEntity cart = TUBUH.get(id);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z));
				if (cart == null || cart.isRemoved()) {
					cart = EkonomiEntities.GEROBAK.create(level, EntitySpawnReason.EVENT);
					if (cart == null) {
						continue;
					}
					float yaw = (float) (Mth.atan2(caravan.getIntOr("tz", 0) - pos.z, caravan.getIntOr("tx", 0) - pos.x) * Mth.RAD_TO_DEG) - 90.0F;
					cart.snapTo(pos.x, y, pos.z, yaw, 0.0F);
					cart.setMuatan((int) (id & 3L) % 3);
					cart.ikat(id, new Vec3(caravan.getIntOr("tx", 0) + 0.5D, y, caravan.getIntOr("tz", 0) + 0.5D));
					if (!level.addFreshEntity(cart)) {
						continue;
					}
					TUBUH.put(id, cart);
				} else if (Math.hypot(cart.getX() - pos.x, cart.getZ() - pos.z) > 8.0D) {
					cart.snapTo(pos.x, y, pos.z, cart.getYRot(), 0.0F);
				}
			}
		}
		TUBUH.entrySet().removeIf(entry -> {
			if (!seen.contains(entry.getKey())) {
				entry.getValue().discard();
				return true;
			}
			return false;
		});
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Sheet block (buku_kas.kafilah)

	static CompoundTag lembar(ServerPlayer player) {
		MinecraftServer current = player.level().getServer();
		UUID owner = player.getUUID();
		CompoundTag tag = new CompoundTag();
		int maks = maks(owner);
		tag.putInt(LembarKunci.MAKS, maks);
		int band = Barang.pita(player);
		long hari = Barang.hari(current);
		tag.putLong(LembarKunci.BukuKas.LABA_MINGGU, EkonomiData.get(current).mingguan(owner, hari, "kafilah_laba"));
		tag.putLong(LembarKunci.BukuKas.LABA_BATAS, batasMingguan(band));
		if (!EkonomiConfig.get().kafilah) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.tutup"));
			return tag;
		}
		if (maks <= 0) {
			LembarKunci.teks(tag, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.level", LEVEL_MIN));
		}
		long now = Barang.waktu(current);
		ListTag rows = new ListTag();
		for (Tag each : daftar(current, owner)) {
			if (!(each instanceof CompoundTag caravan)) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Long.toString(caravan.getLongOr("id", 0L)));
			LembarKunci.teks(row, LembarKunci.TUJUAN, KotaNames.town(caravan.getLongOr("tujuan", 0L)));
			ListTag goods = new ListTag();
			for (Tag stackTag : caravan.getListOrEmpty("muatan")) {
				ItemStack stack = Barang.muat(stackTag, player.registryAccess());
				if (!stack.isEmpty()) {
					CompoundTag g = new CompoundTag();
					LembarKunci.barang(g, stack, player.registryAccess());
					g.putInt(LembarKunci.JUMLAH, stack.getCount());
					goods.add(g);
				}
			}
			row.put(LembarKunci.BukuKas.MUATAN, goods);
			ListTag guards = new ListTag();
			for (Tag guardTag : caravan.getListOrEmpty("pengawal")) {
				if (guardTag instanceof CompoundTag guard) {
					CompoundTag g = new CompoundTag();
					LembarKunci.teks(g, LembarKunci.NAMA, Component.literal(guard.getStringOr("nama", "?")));
					guards.add(g);
				}
			}
			row.put(LembarKunci.BukuKas.PENGAWAL, guards);
			boolean arrived = caravan.getBooleanOr("tiba", false);
			row.putInt(LembarKunci.RISIKO, arrived ? 0 : risiko(caravan, now));
			long value = caravan.getLongOr("nilai", 0L);
			row.putLong(LembarKunci.BukuKas.LABA_KIRA, arrived ? caravan.getLongOr("laba", 0L)
					: Math.round(value * (1.0D - caravan.getDoubleOr("rugi", 0.0D)) * 0.15D));
			long end = caravan.getLongOr("tiba_pada", now);
			row.putInt(LembarKunci.SISA_HARI, arrived ? 0 : (int) Math.max(1L, (end - now + 23999L) / 24000L));
			long start = caravan.getLongOr("berangkat", now);
			row.putFloat(LembarKunci.KEMAJUAN, arrived ? 1.0F : (float) Mth.clamp((now - start) / (double) Math.max(1L, end - start), 0.0D, 1.0D));
			row.putInt(LembarKunci.BukuKas.ASAL_X, caravan.getIntOr("ax", 0));
			row.putInt(LembarKunci.BukuKas.ASAL_Z, caravan.getIntOr("az", 0));
			row.putInt(LembarKunci.BukuKas.TUJUAN_X, caravan.getIntOr("tx", 0));
			row.putInt(LembarKunci.BukuKas.TUJUAN_Z, caravan.getIntOr("tz", 0));
			if (arrived) {
				LembarKunci.teks(row, LembarKunci.CATATAN, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.hasil",
						NilaiBatu.text(caravan.getLongOr("hasil", 0L))));
			} else if (caravan.getIntOr("diserang", 0) > 0) {
				LembarKunci.teks(row, LembarKunci.CATATAN, Component.translatable("kultivator.tempat_ekonomi.kafilah_sekte.rugi",
						Math.round(caravan.getDoubleOr("rugi", 0.0D) * 100.0D)));
			}
			rows.add(row);
		}
		tag.put(LembarKunci.DAFTAR, rows);
		// the plan: destinations, escorts, goods
		CompoundTag plan = new CompoundTag();
		ListTag places = new ListTag();
		int i = 0;
		ItemStack probe = player.getMainHandItem();
		for (Tujuan place : tujuan(player)) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Integer.toString(i++));
			LembarKunci.teks(row, LembarKunci.NAMA, KotaNames.town(place.town));
			row.putInt(LembarKunci.JARAK, place.jarak);
			row.putInt(LembarKunci.HARI, place.hari);
			row.putFloat(LembarKunci.BukuKas.PERMINTAAN, (float) (probe.isEmpty() ? 1.0D : permintaan(current, place.town, probe)));
			places.add(row);
		}
		plan.put(LembarKunci.TUJUAN, places);
		ListTag disciples = new ListTag();
		for (DiscipleEntity disciple : murid(player)) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, disciple.soulId().toString());
			LembarKunci.teks(row, LembarKunci.NAMA, disciple.getDisplayName());
			LembarKunci.teks(row, LembarKunci.BukuKas.RANAH, Component.translatable("kultivator.realm." + disciple.data().realm().id()));
			disciples.add(row);
		}
		plan.put(LembarKunci.BukuKas.MURID, disciples);
		ListTag goods = new ListTag();
		for (int slot : muatan(player)) {
			ItemStack stack = player.getInventory().getItem(slot);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Integer.toString(slot));
			LembarKunci.barang(row, stack, player.registryAccess());
			row.putLong(LembarKunci.NILAI, EkonomiApi.harga(stack) * stack.getCount());
			goods.add(row);
		}
		plan.put(LembarKunci.BukuKas.MUATAN, goods);
		tag.put(LembarKunci.BukuKas.RENCANA, plan);
		return tag;
	}

	private KafilahSekte() {
	}
}
