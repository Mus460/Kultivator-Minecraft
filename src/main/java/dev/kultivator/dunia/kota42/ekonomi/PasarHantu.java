package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.PasarGelap;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.KotaTitik;
import dev.kultivator.dunia.kota42.acara.KotaAcaraEntities;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Ghost Market (DESIGN 42.6.7, contract C42.C1): a night market at one of the {@code pasar_hantu_<n>} titik of a town,
 * themed city (not Kota Iblis, it has its own black market), the capital or a trading post (never in villages, K12),
 * chosen per night by {@code hash(place, day)} so gossip can "know" it. Open in the fifth watch, 01:00-05:00 (time of day
 * 19000-23000); at dawn the stalls dissolve one by one and the lanterns go out.
 * <p>
 * <b>Finding it</b>: the tea house token now tells the <i>sandi malam</i>, a riddle of tonight's spot ({@link #sandiUntuk});
 * a beggar child points for 5 NB ({@link #tunjuk}, 42B's dialog); the green lanterns are seen from 48 blocks (42V).
 * <b>Wares</b>: three to six {@code lapak_hantu} stalls with masked sellers ({@code SosokApi.setRupa(npc, "topeng_kertas")})
 * lay out the broker's list of the night ({@code PasarGelap.dagangan}, one of each), stolen goods of players (from
 * pickpockets 42.7.4 and caravan bandits; redeemed at 1.2 x value), and a fence buys stolen and smuggled goods at
 * 0.6 x value (Infami +1; the broker's daily limit). <b>The mask</b> {@code topeng_pasar_hantu} worn on the head gives
 * 10 % off and keeps the Nama Baik safe in a raid. <b>Razia</b> {@code kotaEkonomi.peluangRazia} (10 %) of the nights:
 * lantern guards come from two directions, the stalls vanish; an unmasked player within 12 blocks is caught (Nama Baik
 * -5 and the broker's Razia with its bribe, {@code PasarGelap.raid}). The day office stays at the broker of
 * {@code kota/gelap}; there is no second black market.
 * <p>
 * State in {@code KotaEkonomiData.bagian("pasar_hantu")}: {@code curian} list ({@code p} owner, {@code i} stack,
 * {@code h} day, {@code s} place it surfaces), per place {@code situs(...)["jl"]} number of spots seen. Stalls, sellers,
 * bought wares and passwords told live in memory (re-made each night).
 */
public final class PasarHantu {

	public static final long BUKA = 19000L;
	public static final long TUTUP = 23000L;
	public static final double DISKON_TOPENG = 0.10D;
	public static final double TEBUS = 1.2D;
	public static final int CURIAN_HARI = 28;
	public static final int CURIAN_MAKS = 96;
	public static final int NAMA_BAIK_RAZIA = -5;
	public static final long PENUNJUK = 5L;
	public static final double JARAK_AKTIF = 96.0D;
	public static final double JARAK_TEMU = 12.0D;
	public static final double JARAK_BELI = 8.0D;
	public static final int LOKASI_BAWAAN = 3;
	/** Spot kinds by number; a titik may name its own in its data ({@code "lokasi": "jembatan"}). */
	public static final List<String> JENIS_LOKASI = List.of("jembatan", "gudang", "gang", "makam", "dermaga");
	private static final List<String> CADANGAN = List.of("gelap", "kumuh", "obrol");
	/** The screen of 42U ({@code KotaLembar.PasarHantu.LAYAR}). */
	public static final String LAYAR = "pasar_hantu";

	/** One open market: place, night, spot, stalls, sellers, raid time. */
	private static final class Pasar {
		final long situs;
		final long hari;
		final BlockPos pusat;
		final String lokasi;
		final List<LapakHantuEntity> lapak = new ArrayList<>();
		final List<KultivatorNpc> penjual = new ArrayList<>();
		final List<KultivatorNpc> penjaga = new ArrayList<>();
		final long razia;
		boolean diserbu;
		boolean bubar;

		Pasar(long situs, long hari, BlockPos pusat, String lokasi, long razia) {
			this.situs = situs;
			this.hari = hari;
			this.pusat = pusat;
			this.lokasi = lokasi;
			this.razia = razia;
		}
	}

	private static final Map<Long, Pasar> BUKA_SEKARANG = new HashMap<>();
	/** Wares bought tonight: "situs:hari:index" by player. */
	private static final Map<UUID, Set<String>> DIBELI = new HashMap<>();
	/** Passwords told: "situs:hari" by player. */
	private static final Map<UUID, Set<String>> SANDI = new HashMap<>();
	/** Places where no spot was loaded: game time of the next try (titik lookups are not free). */
	private static final Map<Long, Long> COBA_LAGI = new HashMap<>();
	/** Markets found: "situs:hari" by player. */
	private static final Map<UUID, Set<String>> TEMU = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
	}

	static void lupakan() {
		for (Pasar p : BUKA_SEKARANG.values()) {
			hapus(p);
		}
		BUKA_SEKARANG.clear();
		DIBELI.clear();
		SANDI.clear();
		TEMU.clear();
		COBA_LAGI.clear();
	}

	public static boolean aktif() {
		return KotaConfig.ekonomi().aktif && KotaConfig.ekonomi().pasarHantu;
	}

	/** True for places that hold a Ghost Market (towns, themed cities but Kota Iblis, the capital, trading posts). */
	public static boolean adaPasar(MinecraftServer server, long situs) {
		String kind = PajakKota.jenisTempat(server, situs);
		return !PajakKota.JT_KAMPUNG.equals(kind) && !PajakKota.JT_LAIN.equals(kind) && !PajakKota.kotaIblis(server, situs);
	}

	/** True between 01:00 and 05:00. */
	public static boolean malam(MinecraftServer server) {
		long t = Kota.timeOfDay(server);
		return t >= BUKA && t < TUTUP;
	}

	/**
	 * The number {@code n} of the {@code pasar_hantu_<n>} titik where the market of place {@code situs} opens on world day
	 * {@code hari}, or -1 when there is none that night.
	 */
	public static int lokasi(long situs, long hari) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || !aktif() || !adaPasar(server, situs)) {
			return -1;
		}
		int spots = Math.max(1, KotaEkonomiData.get(server).situs(KotaEkonomiData.PASAR_HANTU, situs).getIntOr("jl", LOKASI_BAWAAN));
		long h = situs * 0x9E3779B97F4A7C15L ^ hari * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		return 1 + (int) Math.floorMod(h, (long) spots);
	}

	/** The spot kind ("jembatan", "gudang", ...) of spot {@code n}. */
	public static String jenisLokasi(int n) {
		return JENIS_LOKASI.get(Math.floorMod(n - 1, JENIS_LOKASI.size()));
	}

	/** "Bawah Jembatan Batu". */
	public static Component namaLokasi(String jenis) {
		return Component.translatable("kultivator.kota42.pasar_hantu.lokasi." + jenis);
	}

	/** True when a raid comes to place {@code situs} on night {@code hari}. */
	public static boolean razia(long situs, long hari) {
		return RandomSource.create(situs * 7477L + hari * 104723L).nextDouble() < KotaConfig.ekonomi().peluangRazia;
	}

	/** Tonight's riddle for a token holder at the tea house of {@code town}, or null when no market opens there tonight. */
	public static @Nullable Component sandiUntuk(ServerPlayer player, long town) {
		MinecraftServer server = player.level().getServer();
		long hari = malamIni(server);
		int n = lokasi(town, hari);
		if (n < 0) {
			return null;
		}
		SANDI.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(town + ":" + hari);
		String kind = jenisSpot(server, town, n);
		int variant = (int) Math.floorMod(town ^ hari, 2L);
		return Component.translatable("kultivator.kota42.pasar_hantu.teka." + kind + "." + variant);
	}

	/** True when the player was told tonight's password of {@code situs}. */
	public static boolean punyaSandi(ServerPlayer player, long situs) {
		Set<String> told = SANDI.get(player.getUUID());
		return told != null && told.contains(situs + ":" + malamIni(player.level().getServer()));
	}

	/**
	 * The beggar child's pointing (5 NB): tonight's spot name and its rough direction; null on success, or the refusal.
	 */
	public static @Nullable Component tunjuk(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		int n = lokasi(situs, malamIni(server));
		if (n < 0) {
			return Component.translatable("kultivator.kota42.pasar_hantu.sandi_tiada");
		}
		if (!EkonomiApi.bayar(player, PENUNJUK, "kota42.penunjuk")) {
			return Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(PENUNJUK));
		}
		SANDI.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(situs + ":" + malamIni(server));
		Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.tunjuk", namaLokasi(jenisSpot(server, situs, n)))
				.withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
		return null;
	}

	/** The world day of tonight's market (01:00-05:00 lies inside one world day, which starts at 06:00). */
	static long malamIni(MinecraftServer server) {
		return KotaHidup.hari(server);
	}

	private static String jenisSpot(MinecraftServer server, long situs, int n) {
		String named = KotaEkonomiData.get(server).situs(KotaEkonomiData.PASAR_HANTU, situs).getStringOr("j" + n, "");
		return named.isEmpty() ? jenisLokasi(n) : named;
	}

	// ================================================================== stolen goods

	/** One stolen stack: index in the list, owner, the stack, day stolen, place where it surfaces. */
	public record Curian(int indeks, UUID pemilik, ItemStack barang, long hari, long situs) {
	}

	private static ListTag curianList(MinecraftServer server) {
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.PASAR_HANTU);
		ListTag list = part.getListOrEmpty("curian");
		if (!part.contains("curian")) {
			part.put("curian", list);
		}
		return list;
	}

	/** A stack stolen from {@code owner} (pickpockets 42D, caravan bandits) turns up at the Ghost Market of {@code situs}. */
	public static void tambahCurian(MinecraftServer server, UUID owner, ItemStack stack, long situs) {
		if (stack.isEmpty()) {
			return;
		}
		ListTag list = curianList(server);
		CompoundTag c = new CompoundTag();
		c.putString("p", owner.toString());
		c.put("i", Barang.simpan(stack, server.registryAccess()));
		c.putLong("h", KotaHidup.hari(server));
		c.putLong("s", situs);
		list.add(c);
		while (list.size() > CURIAN_MAKS) {
			list.remove(0);
		}
		KotaEkonomiData.get(server).ubah();
	}

	/** The stolen goods on sale at {@code situs} (every place's within reach: the fences pass them on). */
	public static List<Curian> curian(MinecraftServer server, long situs) {
		List<Curian> out = new ArrayList<>();
		ListTag list = curianList(server);
		long today = KotaHidup.hari(server);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag c = list.getCompoundOrEmpty(i);
			UUID owner = Investasi.uuid(c.getStringOr("p", ""));
			if (owner == null || today - c.getLongOr("h", today) > CURIAN_HARI) {
				continue;
			}
			out.add(new Curian(i, owner, Barang.muat(c.get("i"), server.registryAccess()), c.getLongOr("h", 0L), c.getLongOr("s", 0L)));
		}
		return out;
	}

	/** Redeem price of a stolen stack: 1.2 x its value (at least 1 NB). */
	public static long hargaTebus(ItemStack stack) {
		return Math.max(1L, Math.round(EkonomiApi.harga(stack) * stack.getCount() * TEBUS));
	}

	// ================================================================== buying and selling (all need an open market near)

	/** The market open at {@code situs} right now with the player within reach of a stall, or null. */
	private static @Nullable Pasar pasarDekat(ServerPlayer player, long situs) {
		Pasar p = BUKA_SEKARANG.get(situs);
		if (p == null || p.diserbu || p.bubar) {
			return null;
		}
		for (LapakHantuEntity stall : p.lapak) {
			if (!stall.isRemoved() && stall.distanceToSqr(player) < JARAK_BELI * JARAK_BELI) {
				return p;
			}
		}
		return null;
	}

	/** True while the player wears the Ghost Market mask. */
	public static boolean bertopeng(ServerPlayer player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).is(KotaEkonomiItems.TOPENG_PASAR_HANTU);
	}

	/** Tonight's wares at {@code situs} with the mask discount applied for {@code player}. */
	public static List<PasarGelap.Offer> dagangan(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		List<PasarGelap.Offer> out = new ArrayList<>();
		double off = bertopeng(player) ? 1.0D - DISKON_TOPENG : 1.0D;
		for (PasarGelap.Offer o : PasarGelap.dagangan(server, situs, malamIni(server))) {
			out.add(new PasarGelap.Offer(o.index(), o.ware(), o.stack(), Math.max(1L, Math.round(o.price() * off))));
		}
		return out;
	}

	/** True when the player already bought ware {@code index} tonight (one of each). */
	public static boolean terjual(ServerPlayer player, long situs, int index) {
		Set<String> bought = DIBELI.get(player.getUUID());
		return bought != null && bought.contains(situs + ":" + malamIni(player.level().getServer()) + ":" + index);
	}

	/** Buys ware {@code index} of tonight's list; null on success or the refusal. */
	public static @Nullable Component beli(ServerPlayer player, long situs, int index) {
		if (pasarDekat(player, situs) == null) {
			return Component.translatable("kultivator.kota42.pasar_hantu.jauh");
		}
		if (terjual(player, situs, index)) {
			return Component.translatable("kultivator.kota42.pasar_hantu.habis");
		}
		for (PasarGelap.Offer o : dagangan(player, situs)) {
			if (o.index() != index) {
				continue;
			}
			if (!EkonomiApi.bayar(player, o.price(), "kota42.pasar_hantu")) {
				return Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(o.price()));
			}
			DIBELI.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(situs + ":" + malamIni(player.level().getServer()) + ":" + index);
			ItemStack stack = o.stack().copy();
			Component name = stack.getHoverName();
			Barang.beri(player, stack);
			PasarGelap.addShadowReputation(player, 1);
			Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.beli_ok", name, NilaiBatu.text(o.price())).withStyle(ChatFormatting.DARK_GREEN));
			return null;
		}
		return Component.translatable("kultivator.kota42.pasar_hantu.habis");
	}

	/** Redeems (or buys) stolen stack {@code indeks}; null on success or the refusal. */
	public static @Nullable Component tebus(ServerPlayer player, long situs, int indeks) {
		MinecraftServer server = player.level().getServer();
		if (pasarDekat(player, situs) == null) {
			return Component.translatable("kultivator.kota42.pasar_hantu.jauh");
		}
		ListTag list = curianList(server);
		if (indeks < 0 || indeks >= list.size()) {
			return Component.translatable("kultivator.kota42.pasar_hantu.habis");
		}
		ItemStack stack = Barang.muat(list.getCompoundOrEmpty(indeks).get("i"), server.registryAccess());
		long price = hargaTebus(stack);
		if (!EkonomiApi.bayar(player, price, "kota42.tebus")) {
			return Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(price));
		}
		list.remove(indeks);
		KotaEkonomiData.get(server).ubah();
		Component name = stack.getHoverName();
		Barang.beri(player, stack);
		Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.tebus_ok", name, NilaiBatu.text(price)).withStyle(ChatFormatting.DARK_GREEN));
		return null;
	}

	/** What the fence pays for {@code stack} (0 when he does not buy it). */
	public static long hargaPenadah(MinecraftServer server, long situs, ItemStack stack) {
		return PasarGelap.buys(stack) ? PasarGelap.buyback(server, situs, stack) * stack.getCount() : 0L;
	}

	/** Sells inventory slot {@code slot} to the fence; null on success or the refusal. */
	public static @Nullable Component jual(ServerPlayer player, long situs, int slot) {
		MinecraftServer server = player.level().getServer();
		if (pasarDekat(player, situs) == null) {
			return Component.translatable("kultivator.kota42.pasar_hantu.jauh");
		}
		if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
			return Component.translatable("kultivator.kota42.pasar_hantu.penadah_tolak");
		}
		ItemStack stack = player.getInventory().getItem(slot);
		long pay = hargaPenadah(server, situs, stack);
		if (pay <= 0L) {
			return Component.translatable("kultivator.kota42.pasar_hantu.penadah_tolak");
		}
		if (PasarGelap.sisaJual(player) <= 0) {
			return Component.translatable("kultivator.kota42.pasar_hantu.penadah_cukup");
		}
		PasarGelap.catatJual(player);
		Component name = stack.getHoverName();
		int count = stack.getCount();
		player.getInventory().setItem(slot, ItemStack.EMPTY);
		EkonomiApi.beri(player, pay, "kota42.penadah");
		TemaApi.tambahInfami(player, 1, "penadah");
		Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.jual_ok", count, name, NilaiBatu.text(pay)).withStyle(ChatFormatting.DARK_GREEN));
		return null;
	}

	// ================================================================== stall click

	/** Right-click on a stall: the {@code pasar_hantu} screen when 42U registered it, else the list in chat. */
	static boolean klik(LapakHantuEntity stall, ServerPlayer player) {
		if (stall.keadaan() != LapakHantuEntity.KEADAAN_BUKA) {
			return false;
		}
		if (TempatLayar.bukaDari(player, LAYAR, stall.getId())) {
			return true;
		}
		tampil(player, stall.situsId());
		return true;
	}

	private static void tampil(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		Pasar p = BUKA_SEKARANG.get(situs);
		Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.judul", p == null ? Component.literal("?") : namaLokasi(p.lokasi),
				(TUTUP - Kota.timeOfDay(server)) / 20L).withStyle(ChatFormatting.DARK_GREEN));
		Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.bisik." + Math.floorMod(situs ^ malamIni(server), 4L))
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		for (PasarGelap.Offer o : dagangan(player, situs)) {
			boolean sold = terjual(player, situs, o.index());
			MutableComponent line = Component.translatable("kultivator.kota42.pasar_hantu.ware", o.stack().getCount(), o.stack().getHoverName(),
					NilaiBatu.text(o.price()));
			if (sold) {
				line.append(Component.translatable("kultivator.kota42.pasar_hantu.terjual").withStyle(ChatFormatting.DARK_GRAY));
			} else {
				line.append(Component.translatable("kultivator.kota42.pasar_hantu.tombol_beli").withStyle(style -> style.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku pasar_hantu beli " + o.index()))));
			}
			Msg.chat(player, line);
		}
		for (Curian c : curian(server, situs)) {
			boolean mine = c.pemilik().equals(player.getUUID());
			MutableComponent line = Component.translatable(mine ? "kultivator.kota42.pasar_hantu.curian_milikmu" : "kultivator.kota42.pasar_hantu.curian",
					c.barang().getCount(), c.barang().getHoverName(), c.hari(), NilaiBatu.text(hargaTebus(c.barang())))
					.append(Component.translatable("kultivator.kota42.pasar_hantu.tombol_tebus").withStyle(style -> style.withColor(ChatFormatting.GREEN)
							.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku pasar_hantu tebus " + c.indeks()))));
			Msg.chat(player, line.withStyle(mine ? ChatFormatting.GOLD : ChatFormatting.WHITE));
		}
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			long pay = hargaPenadah(server, situs, stack);
			if (pay > 0L) {
				int s = slot;
				Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.penadah", stack.getCount(), stack.getHoverName(), NilaiBatu.text(pay))
						.append(Component.translatable("kultivator.kota42.pasar_hantu.tombol_jual").withStyle(style -> style.withColor(ChatFormatting.YELLOW)
								.withClickEvent(new ClickEvent.RunCommand("/kultivasi kotaku pasar_hantu jual " + s)))));
			}
		}
		Msg.chat(player, Component.translatable(bertopeng(player) ? "kultivator.kota42.pasar_hantu.topeng_ya" : "kultivator.kota42.pasar_hantu.topeng_tidak")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	// ================================================================== the night

	/** Every second: opens markets near players at night, fades them at dawn, runs raids. */
	static void tick(MinecraftServer server, int tick) {
		ServerLevel level = server.overworld();
		boolean night = aktif() && malam(server);
		long hari = malamIni(server);
		long time = Kota.timeOfDay(server);
		for (Pasar p : List.copyOf(BUKA_SEKARANG.values())) {
			if (!night || p.hari != hari) {
				p.bubar = true;
			}
			if (!p.diserbu && !p.bubar && p.razia > 0L && time >= p.razia) {
				serbu(level, p);
			}
			if (memudar(p)) {
				hapus(p);
				BUKA_SEKARANG.remove(p.situs);
				continue;
			}
			temukan(level, p);
		}
		if (!night || tick % 100 != 0) {
			return;
		}
		Set<Long> seen = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			TempatInfo here = TempatApi.tempatDi(level, player.blockPosition());
			if (here == null || !seen.add(here.situsId()) || BUKA_SEKARANG.containsKey(here.situsId()) || !adaPasar(server, here.situsId())
					|| COBA_LAGI.getOrDefault(here.situsId(), 0L) > level.getGameTime()) {
				continue;
			}
			buka(level, here.situsId(), hari, player);
			if (!BUKA_SEKARANG.containsKey(here.situsId())) {
				COBA_LAGI.put(here.situsId(), level.getGameTime() + 1200L);
			}
		}
	}

	private static void buka(ServerLevel level, long situs, long hari, ServerPlayer near) {
		MinecraftServer server = level.getServer();
		List<Titik> spots = KotaTitik.bernomor(level, situs, KotaTitik.PASAR_HANTU);
		CompoundTag site = KotaEkonomiData.get(server).situs(KotaEkonomiData.PASAR_HANTU, situs);
		Titik spot = null;
		if (!spots.isEmpty()) {
			if (site.getIntOr("jl", -1) != spots.size()) {
				site.putInt("jl", spots.size());
				for (Titik t : spots) {
					String named = t.data().getOrDefault("lokasi", "");
					if (!named.isEmpty()) {
						site.putString("j" + KotaTitik.nomor(t.jenis()), named);
					}
				}
				KotaEkonomiData.get(server).ubah();
			}
			int n = lokasi(situs, hari);
			for (Titik t : spots) {
				if (KotaTitik.nomor(t.jenis()) == n) {
					spot = t;
				}
			}
		} else {
			for (String kind : CADANGAN) {
				List<Titik> list = TempatApi.titik(level, situs, kind);
				if (!list.isEmpty()) {
					spot = list.get(Math.floorMod((int) (situs ^ hari), list.size()));
					break;
				}
			}
		}
		if (spot == null || spot.pos().distSqr(near.blockPosition()) > JARAK_AKTIF * JARAK_AKTIF || !level.isPositionEntityTicking(spot.pos())) {
			return;
		}
		int n = spots.isEmpty() ? 1 : KotaTitik.nomor(spot.jenis());
		RandomSource random = RandomSource.create(situs * 31L + hari * 17L);
		long raidAt = razia(situs, hari) ? 20000L + random.nextInt(2500) : 0L;
		Pasar p = new Pasar(situs, hari, spot.pos(), jenisSpot(server, situs, n), raidAt);
		int stalls = 3 + random.nextInt(4);
		for (int i = 0; i < stalls; i++) {
			if (!DuniaBudget.mayAdd(level, KotaAcaraEntities.ANGGARAN)) {
				break;
			}
			double angle = Math.PI * 2.0D * i / stalls + random.nextDouble() * 0.3D;
			double r = 3.5D + random.nextDouble() * 1.5D;
			int x = spot.pos().getX() + (int) Math.round(Math.cos(angle) * r);
			int z = spot.pos().getZ() + (int) Math.round(Math.sin(angle) * r);
			int y = groundY(level, x, z, spot.pos().getY());
			LapakHantuEntity stall = KotaEkonomiEntities.LAPAK_HANTU.create(level, EntitySpawnReason.EVENT);
			if (stall == null) {
				break;
			}
			float yaw = (float) Math.toDegrees(Math.atan2(spot.pos().getZ() - z, spot.pos().getX() - x)) - 90.0F;
			stall.snapTo(x + 0.5D, y, z + 0.5D, yaw, 0.0F);
			stall.ikat(situs, spot.pos().asLong());
			stall.setVarian(random.nextInt(LapakHantuEntity.VARIAN_JUMLAH));
			stall.setKeadaan(LapakHantuEntity.KEADAAN_BUKA);
			stall.setNilai(1.0F);
			if (!level.addFreshEntity(stall)) {
				continue;
			}
			p.lapak.add(stall);
			Vec3 behind = new Vec3(x + 0.5D - Math.cos(angle) * 1.2D, y, z + 0.5D - Math.sin(angle) * 1.2D);
			KultivatorNpc seller = HidupApi.tubuh(level, situs, NpcRole.MAKELAR_GELAP, BlockPos.containing(behind), yaw + 180.0F);
			if (seller != null) {
				SosokApi.setRupa(seller, "topeng_kertas");
				seller.setLonely(JARAK_AKTIF, 200);
				seller.memory().putBoolean("pasar_hantu", true);
				p.penjual.add(seller);
			}
		}
		if (!p.lapak.isEmpty()) {
			BUKA_SEKARANG.put(situs, p);
		}
	}

	private static int groundY(ServerLevel level, int x, int z, int nearY) {
		BlockPos ground = TempatApi.groundSpot(level, x, z, nearY);
		return ground != null ? ground.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/** Players coming within 12 blocks of an open market find it (advancement, a whisper). */
	private static void temukan(ServerLevel level, Pasar p) {
		if (p.bubar || p.diserbu) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(p.pusat) < JARAK_TEMU * JARAK_TEMU && TEMU.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(p.situs + ":" + p.hari)) {
				Advancements.grant(player, "tempat/kota42_pasar_hantu");
				Msg.actionBar(player, Component.translatable("kultivator.kota42.pasar_hantu.temu", namaLokasi(p.lokasi)).withStyle(ChatFormatting.DARK_GREEN));
			}
		}
	}

	/** Fades the stalls one by one when the market ends; true once all are gone. */
	private static boolean memudar(Pasar p) {
		if (!p.bubar && !p.diserbu) {
			return false;
		}
		boolean all = true;
		for (int i = 0; i < p.lapak.size(); i++) {
			LapakHantuEntity stall = p.lapak.get(i);
			if (stall.isRemoved()) {
				continue;
			}
			all = false;
			if (stall.keadaan() != LapakHantuEntity.KEADAAN_MEMUDAR) {
				// one by one: the next stall starts fading when the previous is half gone
				if (i == 0 || p.lapak.get(i - 1).isRemoved() || p.lapak.get(i - 1).nilai() < 0.5F || p.diserbu) {
					stall.setKeadaan(LapakHantuEntity.KEADAAN_MEMUDAR);
				}
			}
		}
		return all;
	}

	/** Razia: lantern guards from two directions, stalls vanish, unmasked players near are caught. */
	private static void serbu(ServerLevel level, Pasar p) {
		p.diserbu = true;
		for (int side = 0; side < 2; side++) {
			Direction dir = side == 0 ? Direction.NORTH : Direction.SOUTH;
			BlockPos from = p.pusat.relative(dir, 20);
			for (int i = 0; i < 2; i++) {
				KultivatorNpc guard = HidupApi.tubuh(level, p.situs, NpcRole.PENJAGA_MALAM, from.offset(i * 2 - 1, 0, 0), dir.getOpposite().toYRot());
				if (guard != null) {
					guard.setLonely(JARAK_AKTIF, 600);
					guard.memory().putBoolean("razia", true);
					guard.getNavigation().moveTo(p.pusat.getX() + 0.5D, p.pusat.getY(), p.pusat.getZ() + 0.5D, 1.2D);
					p.penjaga.add(guard);
				}
			}
		}
		MinecraftServer server = level.getServer();
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().distSqr(p.pusat) > JARAK_TEMU * JARAK_TEMU) {
				continue;
			}
			if (bertopeng(player)) {
				Msg.actionBar(player, Component.translatable("kultivator.kota42.pasar_hantu.razia_topeng").withStyle(ChatFormatting.GRAY));
				continue;
			}
			KotaData.get(server).addReputation(p.situs, player.getUUID(), NAMA_BAIK_RAZIA);
			Msg.chat(player, Component.translatable("kultivator.kota42.pasar_hantu.razia", NAMA_BAIK_RAZIA).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
			PasarGelap.raid(player, null);
		}
	}

	private static void hapus(Pasar p) {
		for (LapakHantuEntity stall : p.lapak) {
			if (!stall.isRemoved()) {
				stall.discard();
			}
		}
		for (KultivatorNpc npc : p.penjual) {
			if (!npc.isRemoved()) {
				npc.vanish();
			}
		}
		for (KultivatorNpc npc : p.penjaga) {
			if (!npc.isRemoved()) {
				npc.vanish();
			}
		}
	}

	// ================================================================== commands

	/** {@code /kultivasi kotaku pasar_hantu [beli <i> | tebus <i> | jual <slot>]}: tonight's market (hint, or the list near it). */
	static LiteralArgumentBuilder<CommandSourceStack> perintah() {
		return Commands.literal("pasar_hantu").executes(ctx -> info(ctx.getSource()))
				.then(Commands.literal("beli").then(Commands.argument("i", IntegerArgumentType.integer(0))
						.executes(ctx -> aksi(ctx, (p, s) -> beli(p, s, IntegerArgumentType.getInteger(ctx, "i"))))))
				.then(Commands.literal("tebus").then(Commands.argument("i", IntegerArgumentType.integer(0))
						.executes(ctx -> aksi(ctx, (p, s) -> tebus(p, s, IntegerArgumentType.getInteger(ctx, "i"))))))
				.then(Commands.literal("jual").then(Commands.argument("slot", IntegerArgumentType.integer(0, 40))
						.executes(ctx -> aksi(ctx, (p, s) -> jual(p, s, IntegerArgumentType.getInteger(ctx, "slot"))))));
	}

	private interface Aksi {
		@Nullable Component jalan(ServerPlayer player, long situs);
	}

	private static int aksi(CommandContext<CommandSourceStack> ctx, Aksi aksi) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		TempatInfo here = PajakKota.tempat(player);
		Component why = here == null ? Component.translatable("kultivator.kota42.cmd.tanpa_tempat") : aksi.jalan(player, here.situsId());
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	private static int info(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		TempatInfo here = PajakKota.tempat(player);
		if (here == null || !adaPasar(server, here.situsId())) {
			source.sendFailure(Component.translatable("kultivator.kota42.pasar_hantu.tiada_di_sini"));
			return 0;
		}
		if (pasarDekat(player, here.situsId()) != null) {
			tampil(player, here.situsId());
			return 1;
		}
		Pasar open = BUKA_SEKARANG.get(here.situsId());
		if (punyaSandi(player, here.situsId())) {
			int n = lokasi(here.situsId(), malamIni(server));
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.pasar_hantu.ingat", namaLokasi(jenisSpot(server, here.situsId(), n)),
					malam(server) ? Component.translatable("kultivator.kota42.pasar_hantu.buka_kini") : Component.translatable("kultivator.kota42.pasar_hantu.buka_nanti"))
					.withStyle(ChatFormatting.DARK_GREEN), false);
		} else {
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.pasar_hantu.petunjuk").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
		}
		return open == null ? 0 : 1;
	}

	/** {@code uji kota_hidup ekonomi pasar_hantu [buka | tp | razia]}. */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("pasar_hantu").executes(ctx -> info(ctx.getSource()))
				.then(Commands.literal("buka").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TempatInfo here = PajakKota.tempat(player);
					if (here == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
						return 0;
					}
					Pasar old = BUKA_SEKARANG.remove(here.situsId());
					if (old != null) {
						hapus(old);
					}
					buka(player.level(), here.situsId(), malamIni(ctx.getSource().getServer()), player);
					Pasar p = BUKA_SEKARANG.get(here.situsId());
					if (p == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.uji.pasar_gagal"));
						return 0;
					}
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.uji.pasar_buka", namaLokasi(p.lokasi), p.pusat.getX(), p.pusat.getY(),
							p.pusat.getZ(), p.lapak.size()), true);
					return 1;
				}))
				.then(Commands.literal("tp").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TempatInfo here = PajakKota.tempat(player);
					Pasar p = here == null ? null : BUKA_SEKARANG.get(here.situsId());
					if (p == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.uji.pasar_gagal"));
						return 0;
					}
					player.teleportTo(p.pusat.getX() + 0.5D, p.pusat.getY() + 0.1D, p.pusat.getZ() + 0.5D);
					return 1;
				}))
				.then(Commands.literal("razia").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TempatInfo here = PajakKota.tempat(player);
					Pasar p = here == null ? null : BUKA_SEKARANG.get(here.situsId());
					if (p == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.uji.pasar_gagal"));
						return 0;
					}
					serbu(player.level(), p);
					return 1;
				}));
	}

	private PasarHantu() {
	}
}
