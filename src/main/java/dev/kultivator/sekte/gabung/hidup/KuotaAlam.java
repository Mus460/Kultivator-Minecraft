package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.alam.AlamItems;
import dev.kultivator.alam.RealmSpecs;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.diplomasi.GerbangPlan;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.dunia.TitikGabung;
import dev.kultivator.sekte.gabung.masuk.GabungItems;
import dev.kultivator.sekte.gabung.ui.GabungLayar;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The secret-realm quota of DESIGN 41.6.8 (contract C41.C1, owner 41C; Han Li and the quota each sect sends to the
 * Blood-Coloured Forbidden Land). On day {@code d % 30 == (index * 7) % 30} a sect hands out its class quota of existing
 * realm keys ({@code AlamItems.TOKEN_DARAH_API}, {@code HEAVENVOID_MAP}, {@code INHERITANCE_CRYSTAL}, {@code TOMB_SIGIL})
 * marked {@code kultivator:kuota_sekte}: (1) the Heir one, (2) the top 3 of the month by PK (players and roster compete),
 * (3) the last tournament champion, (4) the rest is sold in the pavilion's Kuota tab at {@link #HARGA}.
 * <p>
 * Keys go to members on line at once, to members away on their next login ({@code bagian("hidup").kuota_tertunda});
 * a roster disciple who wins a key takes it on the run as a rival (a story line, no body: the realms of 25 have no NPC
 * entrants). A member who clears a realm within 30 days of a sect key earns {@code sekte/gabung_kuota} and may hand a
 * realm report to the pavilion within {@link #LAPOR_HARI} days ({@link #lapor}: 10 x realm tier PK + 20 % of the loot
 * handed over, standing +2).
 */
public final class KuotaAlam {

	/** Key kinds in {@link #KUOTA} column order. */
	public static final int TOKEN = 0;
	public static final int PETA = 1;
	public static final int KRISTAL = 2;
	public static final int SEGEL = 3;

	/** Quota per 30 days by class 1-5 (row = class - 1): Token Darah Api, Peta Heavenvoid, Kristal Warisan, Segel Makam. */
	public static final int[][] KUOTA = {
			{1, 0, 0, 0},
			{2, 0, 0, 0},
			{2, 1, 0, 0},
			{2, 1, 1, 0},
			{3, 2, 1, 1}
	};
	/** Pavilion price (PK, times the rank factor) of the keys left over, same column order. */
	public static final int[] HARGA = {300, 800, 1500, 3000};
	/** Days after returning in which a realm report may be handed in. */
	public static final int LAPOR_HARI = 3;
	/** Share of the handed-over loot's value paid as PK, and the PK per realm tier. */
	public static final double LAPOR_BAGIAN = 0.20D;
	public static final int LAPOR_PER_TINGKAT = 10;
	public static final int LAPOR_STANDING = 2;
	/** Days a received sect key counts for the "used a quota" advancement. */
	public static final int SAH_HARI = 30;

	static final String BAGIAN = "kuota";
	static final String K_TERTUNDA = "kuota_tertunda";
	static final String K_TERIMA = "kuota_terima";
	static final String K_LAPOR = "lapor_alam";

	/** True when world day {@code hari} is the quota day of the sect with plan index {@code indeks}. */
	public static boolean hariBagi(int indeks, long hari) {
		return Math.floorMod(hari, 30L) == Math.floorMod(indeks * 7L, 30L);
	}

	/** The quota of class {@code kelas} (1-5, clamped), a copy. */
	public static int[] kuota(int kelas) {
		return KUOTA[Math.max(1, Math.min(5, kelas)) - 1].clone();
	}

	/** The item of key column {@code col}. */
	static Item item(int col) {
		return switch (col) {
			case PETA -> AlamItems.HEAVENVOID_MAP;
			case KRISTAL -> AlamItems.INHERITANCE_CRYSTAL;
			case SEGEL -> AlamItems.TOMB_SIGIL;
			default -> AlamItems.TOKEN_DARAH_API;
		};
	}

	/** One sect key of column {@code col}, marked {@code kultivator:kuota_sekte = sekte}. */
	public static ItemStack kunci(int col, String sekte) {
		ItemStack stack = new ItemStack(item(col));
		stack.set(GabungItems.KUOTA_SEKTE, sekte);
		return stack;
	}

	/** Keys of this month's quota still for sale in the pavilion (Kuota tab), by column. */
	public static int[] sisa(MinecraftServer server, String sekte) {
		GabungData.DataSekteGabung ds = GabungData.get(server).peekSekte(sekte);
		int[] out = new int[4];
		if (ds == null) {
			return out;
		}
		int[] saved = ds.bagian(BAGIAN).getIntArray("sisa").orElse(new int[0]);
		System.arraycopy(saved, 0, out, 0, Math.min(saved.length, 4));
		return out;
	}

	/** One leftover key of column {@code col} was sold. */
	static void catatJual(MinecraftServer server, String sekte, int col) {
		GabungData data = GabungData.get(server);
		int[] s = sisa(server, sekte);
		if (col >= 0 && col < 4 && s[col] > 0) {
			s[col]--;
			data.sekte(sekte).bagian(BAGIAN).put("sisa", new IntArrayTag(s));
			data.setDirty();
		}
	}

	/** Marks the day {@code k} received a sect key (the advancement and the realm report count from it). */
	static void catatTerima(Keanggotaan k, long hari) {
		k.bagian(PapanMisiSekte.BAGIAN).putLong(K_TERIMA, hari);
	}

	/** Hands out this month's quota of {@code sekte}; returns how many keys were given (0 when none or not today). */
	public static int bagi(MinecraftServer server, String sekte) {
		GerbangPlan.Entry e = GabungApi.entry(server, sekte);
		if (e == null || !hariBagi(e.index(), Kota.day(server))) {
			return 0;
		}
		return bagiSekarang(server, sekte);
	}

	/** Hands out the quota now whatever the day (the quota day, or {@code uji gabung kuota bagi}); once per day. */
	public static int bagiSekarang(MinecraftServer server, String sekte) {
		GabungData data = GabungData.get(server);
		GabungData.DataSekteGabung ds = data.sekte(sekte);
		long hari = Kota.day(server);
		if (ds.kuotaHari == hari || GabungApi.tier(sekte) <= 0) {
			return 0;
		}
		ds.kuotaHari = hari;
		int[] q = kuota(GabungApi.kelas(sekte));
		if (ds.merosot) {
			q = new int[]{1, 0, 0, 0};
		}
		// the keys from the most precious down
		List<Integer> keys = new ArrayList<>();
		for (int col = SEGEL; col >= TOKEN; col--) {
			for (int i = 0; i < q[col]; i++) {
				keys.add(col);
			}
		}
		List<String> penerima = new ArrayList<>();
		// (1) the heir
		if (!ds.pewaris.isEmpty()) {
			penerima.add(ds.pewaris);
		}
		// (2) the top 3 of the month by PK: members and the roster (the roster's month is a seeded figure of its rank)
		for (String id : tigaBesar(server, data, sekte, hari)) {
			if (!penerima.contains(id)) {
				penerima.add(id);
			}
		}
		// (3) the last tournament champion
		String juara = TurnamenMurid.juaraTerakhir(server, sekte);
		if (juara != null && !penerima.contains(juara)) {
			penerima.add(juara);
		}
		int given = 0;
		ListTag pesaing = new ListTag();
		for (String id : penerima) {
			if (keys.isEmpty()) {
				break;
			}
			int col = keys.removeFirst();
			UUID uuid = uuid(id);
			if (uuid != null) {
				Keanggotaan k = data.peek(uuid);
				if (k == null || !k.anggotaDi(sekte)) {
					keys.addFirst(col);
					continue;
				}
				serahkan(server, uuid, k, sekte, col, hari);
			} else {
				pesaing.add(StringTag.valueOf(id + "|" + col));
			}
			given++;
		}
		int[] left = new int[4];
		for (int col : keys) {
			left[col]++;
		}
		CompoundTag b = ds.bagian(BAGIAN);
		b.put("sisa", new IntArrayTag(left));
		b.putLong("hari", hari);
		b.put("pesaing", pesaing);
		data.setDirty();
		umumkan(server, data, sekte, given, left, pesaing);
		return given;
	}

	private static List<String> tigaBesar(MinecraftServer server, GabungData data, String sekte, long hari) {
		long bulan = Math.floorDiv(hari, 30L);
		List<Object[]> rows = new ArrayList<>();
		for (Map.Entry<UUID, Keanggotaan> en : data.semuaAnggota().entrySet()) {
			Keanggotaan k = en.getValue();
			if (k.anggotaDi(sekte) && k.pangkat.minimal(dev.kultivator.sekte.gabung.api.Pangkat.DALAM)) {
				rows.add(new Object[]{en.getKey().toString(), k.bulan == bulan ? k.pkBulan : 0});
			}
		}
		double kf = GabungApi.kelasSekte(sekte).kf();
		for (Roster.Tokoh t : Roster.daftar(server, sekte)) {
			if (t.lukaSampai() > hari || !t.aktif()) {
				continue;
			}
			RandomSource r = RandomSource.create(t.benih() * 31L + bulan);
			int pk = (int) Math.round((40 + 30 * t.peranNomor() + r.nextInt(120)) * kf * 4);
			rows.add(new Object[]{t.id(), pk});
		}
		rows.sort(Comparator.comparingInt((Object[] o) -> (int) o[1]).reversed());
		List<String> out = new ArrayList<>();
		for (int i = 0; i < Math.min(3, rows.size()); i++) {
			out.add((String) rows.get(i)[0]);
		}
		return out;
	}

	private static void serahkan(MinecraftServer server, UUID uuid, Keanggotaan k, String sekte, int col, long hari) {
		ServerPlayer online = server.getPlayerList().getPlayer(uuid);
		catatTerima(k, hari);
		k.catat(hari, "kultivator.gabung.riwayat.kuota", Integer.toString(col));
		if (online != null) {
			Barang.beri(online, kunci(col, sekte));
			Msg.chat(online, Component.translatable("kultivator.gabung.kuota.terima", new ItemStack(item(col)).getHoverName(), GabungLayar.namaSekte(sekte))
					.withStyle(ChatFormatting.GOLD));
			online.level().playSound(null, online.getX(), online.getY(), online.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.6F, 1.2F);
		} else {
			CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
			ListTag pending = b.getListOrEmpty(K_TERTUNDA);
			pending.add(StringTag.valueOf(sekte + "|" + col));
			b.put(K_TERTUNDA, pending);
		}
	}

	private static void umumkan(MinecraftServer server, GabungData data, String sekte, int given, int[] left, ListTag pesaing) {
		Component name = GabungLayar.namaSekte(sekte);
		int jual = left[0] + left[1] + left[2] + left[3];
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Keanggotaan k = data.peek(p.getUUID());
			if (k == null || !k.anggotaDi(sekte)) {
				continue;
			}
			Msg.chat(p, Component.translatable("kultivator.gabung.kuota.umum", name, given, jual).withStyle(ChatFormatting.AQUA));
			for (int i = 0; i < pesaing.size(); i++) {
				String line = pesaing.getStringOr(i, "");
				int bar = line.indexOf('|');
				String id = bar < 0 ? line : line.substring(0, bar);
				Roster.Tokoh t = Roster.tokoh(server, id);
				if (t != null) {
					Msg.chat(p, Component.translatable("kultivator.gabung.kuota.pesaing", t.nama()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				}
			}
		}
	}

	/** Login: keys of a quota day the member missed. */
	static void kirimTertunda(ServerPlayer player, Keanggotaan k) {
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		ListTag pending = b.getListOrEmpty(K_TERTUNDA);
		if (pending.isEmpty()) {
			return;
		}
		for (int i = 0; i < pending.size(); i++) {
			String line = pending.getStringOr(i, "");
			int bar = line.lastIndexOf('|');
			if (bar < 0) {
				continue;
			}
			String sekte = line.substring(0, bar);
			int col;
			try {
				col = Integer.parseInt(line.substring(bar + 1));
			} catch (NumberFormatException e) {
				continue;
			}
			Barang.beri(player, kunci(col, sekte));
			Msg.chat(player, Component.translatable("kultivator.gabung.kuota.terima", new ItemStack(item(col)).getHoverName(), GabungLayar.namaSekte(sekte))
					.withStyle(ChatFormatting.GOLD));
		}
		b.remove(K_TERTUNDA);
	}

	// ================================================================== realm clears and reports

	/** {@code Alam.addClearListener}: a member who cleared a realm within 30 days of a sect key may report it. */
	static void onClear(ServerPlayer player, String realmId, int layer, boolean first) {
		if (!GabungApi.aktif()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		GabungData data = GabungData.get(server);
		Keanggotaan k = data.peek(player.getUUID());
		if (k == null || k.sekteAktif() == null) {
			return;
		}
		long hari = Kota.day(server);
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		long terima = b.getLongOr(K_TERIMA, -1L);
		if (terima < 0L || hari - terima > SAH_HARI) {
			return;
		}
		Advancements.grant(player, "sekte/gabung_kuota");
		CompoundTag lapor = new CompoundTag();
		lapor.putString("alam", realmId);
		lapor.putInt("tingkat", tingkat(realmId));
		lapor.putLong("sampai", hari + LAPOR_HARI);
		b.put(K_LAPOR, lapor);
		data.setDirty();
		Msg.chat(player, Component.translatable("kultivator.gabung.kuota.lapor_bisa", LAPOR_HARI).withStyle(ChatFormatting.AQUA));
	}

	private static int tingkat(String realmId) {
		return switch (realmId) {
			case RealmSpecs.HEAVENVOID -> 2;
			case RealmSpecs.ANCIENT_GOD -> 3;
			case RealmSpecs.HEAVENLY_TOMB -> 4;
			default -> 1;
		};
	}

	/**
	 * {@code /kultivasi gabung lapor}: the realm report at the pavilion (within 32 blocks of {@code jasa}, 3 days after a
	 * clear): {@link #LAPOR_PER_TINGKAT} x tier PK, plus {@link #LAPOR_BAGIAN} of the value of the main-hand stack, which
	 * the sect keeps (optional: an empty hand reports without loot), standing +2. Null on success, else the reason.
	 */
	public static @Nullable Component lapor(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !GabungApi.aktif()) {
			return Component.translatable("kultivator.gabung.hidup.bukan_anggota");
		}
		long hari = Kota.day(server);
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		CompoundTag lapor = b.getCompoundOrEmpty(K_LAPOR);
		if (lapor.isEmpty() || hari > lapor.getLongOr("sampai", -1L)) {
			return Component.translatable("kultivator.gabung.kuota.lapor_tidak");
		}
		BlockPos jasa = GabungApi.titik(sekte, TitikGabung.JASA);
		if (jasa == null || player.distanceToSqr(jasa.getX() + 0.5D, player.getY(), jasa.getZ() + 0.5D) > 32.0D * 32.0D) {
			return Component.translatable("kultivator.gabung.kuota.lapor_jauh");
		}
		int pk = LAPOR_PER_TINGKAT * Math.max(1, lapor.getIntOr("tingkat", 1));
		ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
		long nilai = hand.isEmpty() ? 0L : Harga.value(hand) * hand.getCount();
		Component setor = null;
		if (nilai > 0L) {
			pk += (int) Math.round(nilai / Harga.NB_PER_PK * LAPOR_BAGIAN);
			setor = hand.getHoverName();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		b.remove(K_LAPOR);
		k.catat(hari, "kultivator.gabung.riwayat.lapor_alam", lapor.getStringOr("alam", ""), Integer.toString(pk));
		GabungData.get(server).setDirty();
		GabungApi.tambahPk(player, pk, "gabung.lapor_alam");
		SekteApi.diplomasi().adjust(player.getUUID(), sekte, LAPOR_STANDING, "gabung.lapor_alam");
		Msg.chat(player, (setor == null ? Component.translatable("kultivator.gabung.kuota.lapor_selesai", pk)
				: Component.translatable("kultivator.gabung.kuota.lapor_setor", setor, pk)).withStyle(ChatFormatting.GOLD));
		return null;
	}

	private static @Nullable UUID uuid(String s) {
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private KuotaAlam() {
	}
}
