package dev.kultivator.dunia.tempat.tema.bersama;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.tema.KotaTemaData;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaInti;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kontrak Muatan, the cargo contracts of DESIGN 38.T.10.3: issued at the Balai Dewan Saudagar (pelabuhan), the
 * Karavanserai Agung (oasis), the Rumah Sutra (air) and the Pos Kuli (gunung) - the theme agents call
 * {@link #bukaPapan} from their issuer NPC or anchor. "Carry N sealed crates (provided) to the known themed town or
 * capital Y within D world days." The player pays a deposit of 20 % of the cargo value (back on arrival) and gets the
 * crates; pay is {@code ceil(distance / 100 x (2 + band))} NB, +25 % when it arrives within half the time, x1.3 when it
 * arrives by sky ship (riding or next to a Kapal Roh, or handed over at a sky dock by the dock's code). At most
 * {@value #AKTIF_MAKS} running, {@value #MINGGU_MAKS} accepted per world week.
 * <p>
 * Failure (time out, a crate opened on the way): the deposit is lost and Nama Baik -2 in the issuing town. Road risks
 * are the existing ones (bandits of 38.C.6.3, sky pirates of module T); nothing extra is added here. A delivered contract
 * counts as guild mission {@code muatan} ({@code EkonomiApi.misiSelesai}) or, without one, gives guild points.
 * State: {@code KotaTemaData.inti().kontrak.<uuid>}. The board is offered as a clickable list in chat and the running
 * contracts show in the Buku Dagang (tab {@code kontrak}).
 */
public final class KontrakMuatan {

	public static final int AKTIF_MAKS = 2;
	public static final int MINGGU_MAKS = 3;
	public static final double DEPOSIT = 0.20D;
	public static final double BONUS_CEPAT = 0.25D;
	public static final double KALI_LANGIT = 1.3D;
	public static final int NAMA_BAIK_GAGAL = -2;
	public static final int TAWARAN = 3;
	/** Blocks around the destination centre that count as "arrived" (themed towns and capitals are bigger). */
	public static final int JARAK_TIBA = 160;
	/** Guild mission kind of a contract. */
	public static final String MISI = "muatan";

	/** One offer on a board. */
	public record Tawaran(int indeks, long asal, long tujuan, DagangItems.Barang barang, int peti, int hari, long deposit, long bayar, int jarak) {
	}

	private static final Set<java.util.UUID> PUNYA = new HashSet<>();
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		ServerTickEvents.END_SERVER_TICK.register(KontrakMuatan::tick);
	}

	// ================================================================================================ data

	private static CompoundTag milik(MinecraftServer server, ServerPlayer player) {
		CompoundTag all = KotaTemaData.get(server).inti().getCompoundOrEmpty("kontrak");
		KotaTemaData.get(server).inti().put("kontrak", all);
		String key = player.getUUID().toString();
		CompoundTag me = all.getCompoundOrEmpty(key);
		all.put(key, me);
		return me;
	}

	/** The running contracts of the player (live list). */
	public static ListTag aktif(ServerPlayer player) {
		CompoundTag me = milik(player.level().getServer(), player);
		ListTag list = me.getListOrEmpty("aktif");
		me.put("aktif", list);
		return list;
	}

	private static void ubah(MinecraftServer server) {
		KotaTemaData.get(server).ubah();
	}

	// ================================================================================================ the board

	/** Destinations a player can be sent to: known themed towns (not iblis) and capitals, except the issuer. */
	private static List<Long> tujuan(ServerPlayer player, long asal) {
		List<Long> out = new ArrayList<>();
		for (DuniaSites.Site site : DuniaSites.known(player)) {
			if (site.id() == asal) {
				continue;
			}
			Tema tema = Tema.dari(site.kind());
			boolean themed = site.kind().startsWith("kota_tema/") && tema != null && tema != Tema.IBLIS;
			if (themed || Towns.JENIS_KOTA_BESAR.equals(site.kind())) {
				out.add(site.id());
			}
		}
		out.sort(Long::compare);
		return out;
	}

	/** Today's offers at town {@code kota} for the player (deterministic per town, day and player). */
	public static List<Tawaran> tawaran(ServerPlayer player, TemaKota.Kota kota) {
		MinecraftServer server = player.level().getServer();
		List<Tawaran> out = new ArrayList<>();
		if (kota.tema() == Tema.IBLIS) {
			return out;
		}
		List<Long> dest = tujuan(player, kota.situsId());
		List<DagangItems.Barang> goods = new ArrayList<>();
		for (DagangItems.Barang b : DagangItems.barang()) {
			if (b.asal() == kota.tema() && !b.selundupan()) {
				goods.add(b);
			}
		}
		if (dest.isEmpty() || goods.isEmpty()) {
			return out;
		}
		long day = Dagang.hari(server);
		int band = Barang.pita(player);
		for (int i = 0; i < TAWARAN; i++) {
			long h = mix(kota.situsId() * 31L + day * 1_000_003L + i * 7919L);
			long to = dest.get((int) Math.floorMod(h, (long) dest.size()));
			DagangItems.Barang good = goods.get((int) Math.floorMod(h >>> 17, (long) goods.size()));
			BlockPos a = kota.pusat();
			BlockPos b = Dagang.posSitus(server, to);
			if (b == null) {
				continue;
			}
			int jarak = (int) Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
			int peti = 1 + (int) Math.floorMod(h >>> 33, 4L);
			int hari = Math.max(2, (int) Math.ceil(jarak / 1500.0D) + 2);
			long nilai = (long) peti * PetiDagangItem.ISI_PETI * good.dasar();
			long deposit = Math.max(1L, Math.round(nilai * DEPOSIT));
			long bayar = (long) Math.ceil(jarak / 100.0D * (2 + band));
			out.add(new Tawaran(i, kota.situsId(), to, good, peti, hari, deposit, bayar, jarak));
		}
		return out;
	}

	/**
	 * Shows the contract board of town {@code kota} to the player (issuer NPC or anchor of the theme; chat list with
	 * clickable entries) and opens the Buku Dagang on its contract tab.
	 */
	public static void bukaPapan(ServerPlayer player, TemaKota.Kota kota) {
		if (papanChat(player, kota)) {
			DagangLayar.buka(player, -1, TemaLayar.Dagang.TAB_KONTRAK);
		}
	}

	/** The board's chat list of {@code kota} (clickable offers); false when cargo contracts are switched off. */
	static boolean papanChat(ServerPlayer player, TemaKota.Kota kota) {
		if (!TemaConfig.inti().aktif || !TemaConfig.inti().kontrakMuatan) {
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.kontrak.mati").withStyle(ChatFormatting.GRAY));
			return false;
		}
		MinecraftServer server = player.level().getServer();
		player.sendSystemMessage(Component.translatable("kultivator.kota_tema.kontrak.papan", kota.nama(server.overworld().getSeed()))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		List<Tawaran> list = tawaran(player, kota);
		if (list.isEmpty()) {
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.kontrak.kosong").withStyle(ChatFormatting.GRAY));
		}
		for (Tawaran t : list) {
			String cmd = "/kultivasi dagang kontrak ambil " + t.indeks();
			MutableComponent line = Component.translatable("kultivator.kota_tema.kontrak.baris", t.peti(), Dagang.nama(t.barang()),
					Dagang.namaSitus(server, t.tujuan()), t.hari(), NilaiBatu.text(t.bayar()), NilaiBatu.text(t.deposit()));
			line.append(Component.literal(" "));
			line.append(Component.translatable("kultivator.kota_tema.kontrak.ambil").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
					.withClickEvent(new ClickEvent.RunCommand(cmd)).withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd)))));
			player.sendSystemMessage(line);
		}
		PAPAN.put(player.getUUID(), kota.situsId());
		return true;
	}

	private static final java.util.Map<java.util.UUID, Long> PAPAN = new java.util.HashMap<>();

	/** Accepts offer {@code indeks} of the board the player opened last (he must still stand in that town). */
	public static Component ambil(ServerPlayer player, int indeks) {
		MinecraftServer server = player.level().getServer();
		Long boardTown = PAPAN.get(player.getUUID());
		TemaKota.Kota kota = TemaKota.di(player.level(), player.blockPosition());
		if (boardTown == null || kota == null || kota.situsId() != boardTown) {
			return Component.translatable("kultivator.kota_tema.kontrak.jauh");
		}
		if (!TemaConfig.inti().kontrakMuatan) {
			return Component.translatable("kultivator.kota_tema.kontrak.mati");
		}
		ListTag running = aktif(player);
		if (running.size() >= AKTIF_MAKS) {
			return Component.translatable("kultivator.kota_tema.kontrak.penuh", AKTIF_MAKS);
		}
		CompoundTag me = milik(server, player);
		long week = Dagang.minggu(server);
		if (me.getLongOr("minggu", -1L) != week) {
			me.putLong("minggu", week);
			me.putInt("diambil", 0);
		}
		if (me.getIntOr("diambil", 0) >= MINGGU_MAKS) {
			return Component.translatable("kultivator.kota_tema.kontrak.minggu", MINGGU_MAKS);
		}
		Tawaran t = null;
		for (Tawaran each : tawaran(player, kota)) {
			if (each.indeks() == indeks) {
				t = each;
			}
		}
		if (t == null) {
			return Component.translatable("kultivator.kota_tema.kontrak.hilang");
		}
		String taken = "diambil_" + Dagang.hari(server) + "_" + t.asal() + "_" + t.indeks();
		if (me.getBooleanOr(taken, false)) {
			return Component.translatable("kultivator.kota_tema.kontrak.sudah");
		}
		if (!NilaiBatu.pay(player, t.deposit())) {
			return Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(t.deposit()));
		}
		int serial = me.getIntOr("serial", 0) + 1;
		me.putInt("serial", serial);
		me.putInt("diambil", me.getIntOr("diambil", 0) + 1);
		me.putBoolean(taken, true);
		String id = "km" + Long.toHexString(player.getUUID().getMostSignificantBits() & 0xFFFFL) + "_" + serial;
		long now = server.overworld().getGameTime();
		CompoundTag c = new CompoundTag();
		c.putString("id", id);
		c.putLong("asal", t.asal());
		c.putLong("tujuan", t.tujuan());
		c.putString("barang", t.barang().id());
		c.putInt("peti", t.peti());
		c.putLong("deposit", t.deposit());
		c.putLong("bayar", t.bayar());
		c.putInt("jarak", t.jarak());
		c.putLong("mulai", now);
		c.putLong("batas", now + t.hari() * 24000L);
		c.putLong("kota", Towns.within(player.level(), player.blockPosition()));
		running.add(c);
		for (int i = 0; i < t.peti(); i++) {
			Barang.beri(player, Dagang.peti(t.barang(), t.asal(), id));
		}
		PUNYA.add(player.getUUID());
		ubah(server);
		return Component.translatable("kultivator.kota_tema.kontrak.diterima", t.peti(), Dagang.nama(t.barang()), Dagang.namaSitus(server, t.tujuan()),
				t.hari());
	}

	// ================================================================================================ delivery

	/** The running contract with id {@code id}, or null. */
	public static @Nullable CompoundTag cari(ServerPlayer player, String id) {
		for (Tag each : aktif(player)) {
			if (each instanceof CompoundTag c && id.equals(c.getStringOr("id", ""))) {
				return c;
			}
		}
		return null;
	}

	/** True when the player stands at the destination of contract {@code c}. */
	public static boolean diTujuan(ServerPlayer player, CompoundTag c) {
		MinecraftServer server = player.level().getServer();
		long tujuan = c.getLongOr("tujuan", 0L);
		TemaKota.Kota kota = TemaKota.di(player.level(), player.blockPosition());
		if (kota != null && kota.situsId() == tujuan) {
			return true;
		}
		if (Towns.within(player.level(), player.blockPosition()) == tujuan) {
			return true;
		}
		BlockPos pos = Dagang.posSitus(server, tujuan);
		return pos != null && player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
				&& Math.hypot(pos.getX() - player.getX(), pos.getZ() - player.getZ()) <= JARAK_TIBA;
	}

	/** True when the player arrives by sky ship (riding one or right next to a landed one). */
	public static boolean lewatLangit(ServerPlayer player) {
		if (player.getRootVehicle() instanceof KapalRohEntity) {
			return true;
		}
		ServerLevel level = player.level();
		return !level.getEntitiesOfClass(KapalRohEntity.class, new AABB(player.blockPosition()).inflate(12.0D)).isEmpty();
	}

	/** Hands over every running contract whose destination the player stands at; returns the lines to show. */
	public static List<Component> serahSemua(ServerPlayer player) {
		List<Component> out = new ArrayList<>();
		for (Tag each : List.copyOf(aktif(player))) {
			if (each instanceof CompoundTag c && diTujuan(player, c)) {
				out.add(serah(player, c.getStringOr("id", ""), lewatLangit(player)));
			}
		}
		if (out.isEmpty()) {
			out.add(Component.translatable("kultivator.kota_tema.kontrak.bukan_tujuan"));
		}
		return out;
	}

	/**
	 * Hands over contract {@code id} at its destination ({@code langit}: delivered by sky ship, x1.3; sky docks call this
	 * with true). The sealed crates of the contract must all be in the inventory.
	 */
	public static Component serah(ServerPlayer player, String id, boolean langit) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = cari(player, id);
		if (c == null) {
			return Component.translatable("kultivator.kota_tema.kontrak.hilang");
		}
		if (!diTujuan(player, c)) {
			return Component.translatable("kultivator.kota_tema.kontrak.bukan_tujuan");
		}
		int need = c.getIntOr("peti", 1);
		Inventory inv = player.getInventory();
		int have = 0;
		for (int slot = 0; slot < inv.getContainerSize(); slot++) {
			PetiDagangItem.Isi isi = inv.getItem(slot).get(PetiDagangItem.ISI);
			if (isi != null && id.equals(isi.kontrak())) {
				have += inv.getItem(slot).getCount();
			}
		}
		if (have < need) {
			return Component.translatable("kultivator.kota_tema.kontrak.kurang_peti", have, need);
		}
		int left = need;
		for (int slot = 0; slot < inv.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inv.getItem(slot);
			PetiDagangItem.Isi isi = stack.get(PetiDagangItem.ISI);
			if (isi != null && id.equals(isi.kontrak())) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
		inv.setChanged();
		long now = server.overworld().getGameTime();
		long start = c.getLongOr("mulai", now);
		long end = c.getLongOr("batas", now);
		double pay = c.getLongOr("bayar", 0L);
		boolean fast = now - start <= (end - start) / 2;
		if (fast) {
			pay *= 1.0D + BONUS_CEPAT;
		}
		if (langit) {
			pay *= KALI_LANGIT;
		}
		long total = (long) Math.ceil(pay) + c.getLongOr("deposit", 0L);
		NilaiBatu.give(player, total);
		hapus(player, id);
		if (!EkonomiApi.misiSelesai(player, MISI)) {
			EkonomiApi.poinGilda(player, 1 + need);
		}
		DuniaSites.Site dest = DuniaSites.byId(server, c.getLongOr("tujuan", 0L));
		CeritaApi.peristiwa(player, TemaCerita.KONTRAK_MUATAN, dest == null ? "?" : dest.kind());
		ubah(server);
		MutableComponent line = Component.translatable("kultivator.kota_tema.kontrak.selesai", Dagang.namaSitus(server, c.getLongOr("tujuan", 0L)),
				NilaiBatu.text(total));
		if (fast) {
			line.append(Component.literal(" ")).append(Component.translatable("kultivator.kota_tema.kontrak.cepat"));
		}
		if (langit) {
			line.append(Component.literal(" ")).append(Component.translatable("kultivator.kota_tema.kontrak.langit"));
		}
		return line;
	}

	/** A crate of contract {@code id} was opened: the contract fails. */
	static void petiDibuka(ServerPlayer player, String id) {
		CompoundTag c = cari(player, id);
		if (c != null) {
			gagal(player, c, "kultivator.kota_tema.kontrak.gagal_buka");
		}
	}

	/** Fails contract {@code c}: deposit lost, Nama Baik -2 in the issuing town. */
	public static void gagal(ServerPlayer player, CompoundTag c, String alasan) {
		MinecraftServer server = player.level().getServer();
		long town = c.getLongOr("kota", 0L);
		if (town != 0L) {
			KotaData data = KotaData.get(server);
			data.addReputation(town, player.getUUID(), NAMA_BAIK_GAGAL);
			data.touch();
		}
		hapus(player, c.getStringOr("id", ""));
		ubah(server);
		player.sendSystemMessage(Component.translatable(alasan, Dagang.namaSitus(server, c.getLongOr("tujuan", 0L)), NilaiBatu.text(c.getLongOr("deposit", 0L)))
				.withStyle(ChatFormatting.RED));
	}

	private static void hapus(ServerPlayer player, String id) {
		ListTag list = aktif(player);
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i) instanceof CompoundTag c && id.equals(c.getStringOr("id", ""))) {
				list.remove(i);
			}
		}
	}

	/** Every 100 ticks for players with contracts: time-outs and a one-time arrival hint at the destination. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 100 != 13) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!PUNYA.contains(player.getUUID()) && server.getTickCount() % 2400 != 13) {
				continue;
			}
			ListTag list = aktif(player);
			if (list.isEmpty()) {
				PUNYA.remove(player.getUUID());
				continue;
			}
			PUNYA.add(player.getUUID());
			for (Tag each : List.copyOf(list)) {
				if (!(each instanceof CompoundTag c)) {
					continue;
				}
				if (now > c.getLongOr("batas", Long.MAX_VALUE)) {
					gagal(player, c, "kultivator.kota_tema.kontrak.gagal_waktu");
					continue;
				}
				if (!c.getBooleanOr("diberitahu", false) && diTujuan(player, c)) {
					c.putBoolean("diberitahu", true);
					String cmd = "/kultivasi dagang kontrak serah";
					player.sendSystemMessage(Component.translatable("kultivator.kota_tema.kontrak.tiba", Dagang.namaSitus(server, c.getLongOr("tujuan", 0L)))
							.withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent.RunCommand(cmd))
									.withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd)))));
					ubah(server);
				}
			}
		}
	}

	private static long mix(long h) {
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return h & Long.MAX_VALUE;
	}

	private KontrakMuatan() {
	}
}
