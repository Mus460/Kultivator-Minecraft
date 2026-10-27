package dev.kultivator.dunia.kota42.ekonomi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.RumahUang;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Player stakes in resident businesses (DESIGN 42.6.4, 42.6.0 faucet caps) and the one money drawer ("laci") of 42C-2.
 * <p>
 * <b>Planting</b>: 50 NB up to {@code kotaEkonomi.investasiMaks} (200) x (band + 1) per business, at most 3 businesses per
 * place and 10 in all. A stake is valued in NB; its share of the business is {@code value / (capital + all stakes)}. Every
 * world week the business's profit times the share is the holder's: a gain is paid into the drawer up to
 * {@code 25 x (b+1)} NB per business and {@code 60 x (b+1)} in all per week (the rest stays in the business), a loss
 * lowers the stake's value. <b>Withdrawing</b> gives seven days' notice, then pays the value then less 2 %. A closing
 * business pays 70-100 % of the value. A disciple sent as manager (42E) gives +10 % turnover.
 * <p>
 * <b>The drawer</b> collects every 42C-2 payout while the player may be away (dividends, office wages, contract pay,
 * refunds); {@code /kultivasi kotaku laci} or the screens pay it out as spirit stones. Weekly faucet totals per player
 * ({@link #catatKeran}) feed the {@code kota_milikku} caps; 42C (shop), 42E (tribute) and 42A (loans) may book theirs.
 * <p>
 * State in {@code KotaEkonomiData.bagian("usaha")["pemain"][uuid]}: {@code b} last known band, {@code laci} NB,
 * {@code km} faucet week, {@code k} {source: NB} this week, {@code kb} news list (Component JSON, 8), {@code tn} lifetime
 * planted. Stakes live in each business ({@code s[uuid]}: {@code v} value, {@code n} paid in, {@code t} withdrawal day,
 * {@code l} last week's result, {@code g} value long[7] newest first).
 */
public final class Investasi {

	public static final long MIN = 50L;
	public static final int MAKS_PER_TEMPAT = 3;
	public static final int MAKS_TOTAL = 10;
	public static final int TARIK_HARI = 7;
	public static final double BIAYA_TARIK = 0.02D;
	/** Weekly faucet caps of 42.6.0, NB x (band + 1). */
	public static final long BATAS_PER_USAHA = 25L;
	public static final long BATAS_TOTAL = 60L;
	public static final int KABAR_MAKS = 8;

	/** Faucet sources of the {@code kota_milikku} weekly block. */
	public static final String K_TOKO = "toko";
	public static final String K_INVESTASI = "investasi";
	public static final String K_KONTRAK = "kontrak";
	public static final String K_GAJI = "gaji";
	public static final String K_UPETI = "upeti";
	public static final String K_BUNGA = "bunga";

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
	}

	public static boolean aktif() {
		return KotaConfig.ekonomi().aktif && KotaConfig.ekonomi().investasi;
	}

	// ================================================================== player ledger

	private static CompoundTag buku(MinecraftServer server, UUID player) {
		CompoundTag all = KotaEkonomiData.get(server).bagian(KotaEkonomiData.USAHA).getCompoundOrEmpty("pemain");
		CompoundTag mine = all.getCompoundOrEmpty(player.toString());
		all.put(player.toString(), mine);
		KotaEkonomiData.get(server).bagian(KotaEkonomiData.USAHA).put("pemain", all);
		return mine;
	}

	/** Remembers the player's band for caps computed while he is away. */
	static int pita(ServerPlayer player) {
		int band = Barang.pita(player);
		buku(player.level().getServer(), player.getUUID()).putInt("b", band);
		return band;
	}

	private static int pita(MinecraftServer server, UUID player) {
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		return online != null ? pita(online) : buku(server, player).getIntOr("b", 0);
	}

	/** The band remembered for an absent player (0 before he was ever seen). */
	static int pitaTersimpan(MinecraftServer server, UUID player) {
		return buku(server, player).getIntOr("b", 0);
	}

	/** NB waiting in the player's drawer. */
	public static long laci(MinecraftServer server, UUID player) {
		return buku(server, player).getLongOr("laci", 0L);
	}

	/** Puts {@code nb} into the drawer; {@code sumber} one of the {@code K_*} faucets (booked for the weekly caps) or "". */
	public static void keLaci(MinecraftServer server, UUID player, long nb, String sumber) {
		if (nb <= 0L) {
			return;
		}
		CompoundTag b = buku(server, player);
		b.putLong("laci", b.getLongOr("laci", 0L) + nb);
		if (sumber != null && !sumber.isEmpty()) {
			catatKeran(server, player, sumber, nb);
		}
		KotaEkonomiData.get(server).ubah();
	}

	/** Takes up to {@code nb} out of the drawer (fines); returns what was taken. */
	public static long dariLaci(MinecraftServer server, UUID player, long nb) {
		CompoundTag b = buku(server, player);
		long take = Math.max(0L, Math.min(nb, b.getLongOr("laci", 0L)));
		b.putLong("laci", b.getLongOr("laci", 0L) - take);
		KotaEkonomiData.get(server).ubah();
		return take;
	}

	/**
	 * Pays a standing bill (property tax, wages) of a player who is away: the drawer first, then the Rumah Uang deposit.
	 * All or nothing; {@code kunci} is the deposit history line ({@code kultivator.kota42.bank.riwayat.*}, one %s).
	 */
	public static boolean bayarLuring(MinecraftServer server, UUID player, long nb, String kunci, Object arg) {
		if (nb <= 0L) {
			return true;
		}
		if (laci(server, player) >= nb) {
			dariLaci(server, player, nb);
			return true;
		}
		return RumahUang.potong(server, player, nb, kunci, arg);
	}

	/** Pays the drawer out as spirit stones; returns what was paid. */
	public static long ambilLaci(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag b = buku(server, player.getUUID());
		long nb = b.getLongOr("laci", 0L);
		if (nb <= 0L) {
			return 0L;
		}
		b.putLong("laci", 0L);
		KotaEkonomiData.get(server).ubah();
		EkonomiApi.beri(player, nb, "kota42.laci");
		return nb;
	}

	/** Books a faucet amount of this week (42.6.0 caps). */
	public static void catatKeran(MinecraftServer server, UUID player, String sumber, long nb) {
		CompoundTag b = buku(server, player);
		long week = Math.floorDiv(KotaHidup.hari(server), 7L);
		if (b.getLongOr("km", -1L) != week) {
			b.putLong("km", week);
			b.put("k", new CompoundTag());
		}
		CompoundTag k = b.getCompoundOrEmpty("k");
		k.putLong(sumber, k.getLongOr(sumber, 0L) + nb);
		b.put("k", k);
		KotaEkonomiData.get(server).ubah();
	}

	/** The faucet {@code sumber} booked for the player this week. */
	public static long keranMinggu(MinecraftServer server, UUID player, String sumber) {
		CompoundTag b = buku(server, player);
		long week = Math.floorDiv(KotaHidup.hari(server), 7L);
		return b.getLongOr("km", -1L) == week ? b.getCompoundOrEmpty("k").getLongOr(sumber, 0L) : 0L;
	}

	/** The latest news of the player's stakes (newest first). */
	public static List<Component> kabar(MinecraftServer server, UUID player) {
		List<Component> out = new ArrayList<>();
		ListTag list = buku(server, player).getListOrEmpty("kb");
		for (int i = list.size() - 1; i >= 0; i--) {
			ComponentSerialization.CODEC.parse(server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),
					list.get(i)).result().ifPresent(out::add);
		}
		return out;
	}

	/** Sends {@code teks} to every holder of business {@code c} (chat when online, kept as news). */
	static void kabar(MinecraftServer server, CompoundTag c, Component teks) {
		for (String key : c.getCompoundOrEmpty("s").keySet()) {
			UUID id = uuid(key);
			if (id != null) {
				kabarPemain(server, id, teks);
			}
		}
	}

	static void kabarPemain(MinecraftServer server, UUID player, Component teks) {
		CompoundTag b = buku(server, player);
		ListTag list = b.getListOrEmpty("kb");
		ComponentSerialization.CODEC.encodeStart(server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), teks)
				.result().ifPresent(list::add);
		while (list.size() > KABAR_MAKS) {
			list.remove(0);
		}
		b.put("kb", list);
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) {
			Msg.chat(online, teks.copy().withStyle(ChatFormatting.GOLD));
		}
	}

	// ================================================================== stakes

	/** One stake for the screens. */
	public record Saham(long situs, int usaha, long setor, long nilai, double bagian, long labaMinggu, long tarikHari, long[] grafik,
			String pengelola) {
	}

	/** Every stake of the player, all places. */
	public static List<Saham> milik(MinecraftServer server, UUID player) {
		List<Saham> out = new ArrayList<>();
		String key = player.toString();
		for (long situs : situsBerusaha(server)) {
			for (Tag tag : UsahaWarga.daftar(server, situs)) {
				if (tag instanceof CompoundTag c && c.getCompoundOrEmpty("s").contains(key)) {
					CompoundTag s = c.getCompoundOrEmpty("s").getCompoundOrEmpty(key);
					long total = c.getIntOr("m", 0) + totalSaham(c);
					out.add(new Saham(situs, c.getIntOr("i", 0), s.getLongOr("n", 0L), s.getLongOr("v", 0L),
							total <= 0L ? 0.0D : s.getLongOr("v", 0L) / (double) total, s.getLongOr("l", 0L), s.getLongOr("t", 0L),
							s.getLongArray("g").orElse(new long[0]), c.getStringOr("pgp", "").equals(key) ? c.getStringOr("pg", "") : ""));
				}
			}
		}
		return out;
	}

	private static List<Long> situsBerusaha(MinecraftServer server) {
		List<Long> out = new ArrayList<>();
		CompoundTag part = KotaEkonomiData.get(server).bagian(KotaEkonomiData.USAHA);
		for (String key : part.keySet()) {
			if (key.startsWith("kota_")) {
				try {
					out.add(Long.parseLong(key.substring(5)));
				} catch (NumberFormatException ignored) {
					// not a place section
				}
			}
		}
		return out;
	}

	/** The summed value of all stakes in business {@code c}. */
	static long totalSaham(CompoundTag c) {
		long n = 0L;
		CompoundTag s = c.getCompoundOrEmpty("s");
		for (String key : s.keySet()) {
			n += Math.max(0L, s.getCompoundOrEmpty(key).getLongOr("v", 0L));
		}
		return n;
	}

	/** The most one stake may hold for this player. */
	public static long maks(ServerPlayer player) {
		return (long) KotaConfig.ekonomi().investasiMaks * (pita(player) + 1L);
	}

	/** Why the player cannot plant {@code nb} more in business {@code id} of {@code situs}, or null when he can. */
	public static @Nullable Component alasan(ServerPlayer player, long situs, int id, long nb) {
		MinecraftServer server = player.level().getServer();
		if (!aktif()) {
			return Component.translatable("kultivator.kota42.investasi.mati");
		}
		CompoundTag c = UsahaWarga.mentah(server, situs, id);
		if (c == null) {
			return Component.translatable("kultivator.kota42.investasi.tiada");
		}
		String key = player.getUUID().toString();
		CompoundTag mine = c.getCompoundOrEmpty("s").getCompoundOrEmpty(key);
		if (mine.getLongOr("t", 0L) > 0L) {
			return Component.translatable("kultivator.kota42.investasi.sedang_tarik");
		}
		if (nb < MIN) {
			return Component.translatable("kultivator.kota42.investasi.min", MIN);
		}
		if (mine.getLongOr("v", 0L) + nb > maks(player)) {
			return Component.translatable("kultivator.kota42.investasi.maks", maks(player));
		}
		if (!c.getCompoundOrEmpty("s").contains(key)) {
			int here = 0;
			int all = 0;
			for (Saham s : milik(server, player.getUUID())) {
				all++;
				if (s.situs() == situs) {
					here++;
				}
			}
			if (here >= MAKS_PER_TEMPAT) {
				return Component.translatable("kultivator.kota42.investasi.maks_tempat", MAKS_PER_TEMPAT);
			}
			if (all >= MAKS_TOTAL) {
				return Component.translatable("kultivator.kota42.investasi.maks_total", MAKS_TOTAL);
			}
		}
		if (NilaiBatu.total(player) < nb) {
			return Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(nb));
		}
		return null;
	}

	/** Plants {@code nb} in business {@code id}; returns null on success or the refusal. */
	public static @Nullable Component tanam(ServerPlayer player, long situs, int id, long nb) {
		Component why = alasan(player, situs, id, nb);
		if (why != null) {
			return why;
		}
		MinecraftServer server = player.level().getServer();
		if (!EkonomiApi.bayar(player, nb, "kota42.investasi")) {
			return Component.translatable("kultivator.kota.kurang_batu", NilaiBatu.text(nb));
		}
		CompoundTag c = UsahaWarga.mentah(server, situs, id);
		CompoundTag shares = c.getCompoundOrEmpty("s");
		String key = player.getUUID().toString();
		CompoundTag mine = shares.getCompoundOrEmpty(key);
		mine.putLong("v", mine.getLongOr("v", 0L) + nb);
		mine.putLong("n", mine.getLongOr("n", 0L) + nb);
		shares.put(key, mine);
		c.put("s", shares);
		CompoundTag b = buku(server, player.getUUID());
		b.putLong("tn", b.getLongOr("tn", 0L) + nb);
		KotaEkonomiData.get(server).ubah();
		UsahaWarga.Usaha u = UsahaWarga.usaha(situs, id);
		Warga owner = u == null ? null : WargaApi.byId(u.pemilikWarga());
		if (owner != null) {
			WargaApi.ubahKesan(owner, player, 6, "investasi", null);
		}
		Msg.chat(player, Component.translatable("kultivator.kota42.investasi.tanam_ok", NilaiBatu.text(nb), u == null ? "?" : UsahaWarga.nama(u))
				.withStyle(ChatFormatting.GREEN));
		return null;
	}

	/**
	 * 42F: a player financed a new business on a plot ({@link UsahaWarga#buka}); he holds {@code bagian} (0.6) of it from
	 * the start. Bypasses the per-business cap (the plot cost was the payment).
	 */
	public static void sahamKavling(MinecraftServer server, long situs, int id, UUID player, double bagian) {
		CompoundTag c = UsahaWarga.mentah(server, situs, id);
		if (c == null || bagian <= 0.0D || bagian >= 1.0D) {
			return;
		}
		long value = Math.round(c.getIntOr("m", 0) * bagian / (1.0D - bagian));
		CompoundTag shares = c.getCompoundOrEmpty("s");
		CompoundTag mine = shares.getCompoundOrEmpty(player.toString());
		mine.putLong("v", value);
		mine.putLong("n", value);
		shares.put(player.toString(), mine);
		c.put("s", shares);
		KotaEkonomiData.get(server).ubah();
	}

	/** Gives notice of withdrawal (7 days); returns null on success or the refusal. */
	public static @Nullable Component tarik(ServerPlayer player, long situs, int id) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = UsahaWarga.mentah(server, situs, id);
		CompoundTag mine = c == null ? null : c.getCompoundOrEmpty("s").getCompoundOrEmpty(player.getUUID().toString());
		if (mine == null || !c.getCompoundOrEmpty("s").contains(player.getUUID().toString())) {
			return Component.translatable("kultivator.kota42.investasi.tanpa_saham");
		}
		if (mine.getLongOr("t", 0L) > 0L) {
			return Component.translatable("kultivator.kota42.investasi.sedang_tarik");
		}
		long day = KotaHidup.hari(server) + TARIK_HARI;
		mine.putLong("t", day);
		KotaEkonomiData.get(server).ubah();
		Msg.chat(player, Component.translatable("kultivator.kota42.investasi.tarik_ok", day).withStyle(ChatFormatting.YELLOW));
		return null;
	}

	/** Cancels a running withdrawal notice. */
	public static @Nullable Component batalTarik(ServerPlayer player, long situs, int id) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = UsahaWarga.mentah(server, situs, id);
		String key = player.getUUID().toString();
		if (c == null || !c.getCompoundOrEmpty("s").contains(key)) {
			return Component.translatable("kultivator.kota42.investasi.tanpa_saham");
		}
		c.getCompoundOrEmpty("s").getCompoundOrEmpty(key).putLong("t", 0L);
		KotaEkonomiData.get(server).ubah();
		return null;
	}

	/** 42E: sends disciple {@code soul} as manager (+10 % turnover); only a holder may. */
	public static @Nullable Component pengelola(ServerPlayer player, long situs, int id, @Nullable UUID soul) {
		MinecraftServer server = player.level().getServer();
		CompoundTag c = UsahaWarga.mentah(server, situs, id);
		String key = player.getUUID().toString();
		if (c == null || !c.getCompoundOrEmpty("s").contains(key)) {
			return Component.translatable("kultivator.kota42.investasi.tanpa_saham");
		}
		if (soul == null) {
			c.remove("pg");
			c.remove("pgp");
		} else {
			if (!c.getStringOr("pgp", key).equals(key)) {
				return Component.translatable("kultivator.kota42.investasi.pengelola_ada");
			}
			c.putString("pg", soul.toString());
			c.putString("pgp", key);
		}
		KotaEkonomiData.get(server).ubah();
		return null;
	}

	/** Pays out matured withdrawals (daily). */
	static void harian(MinecraftServer server, long hari) {
		for (long situs : situsBerusaha(server)) {
			for (Tag tag : UsahaWarga.daftar(server, situs)) {
				if (!(tag instanceof CompoundTag c)) {
					continue;
				}
				CompoundTag shares = c.getCompoundOrEmpty("s");
				for (String key : List.copyOf(shares.keySet())) {
					CompoundTag s = shares.getCompoundOrEmpty(key);
					long due = s.getLongOr("t", 0L);
					UUID id = uuid(key);
					if (due <= 0L || due > hari || id == null) {
						continue;
					}
					long pay = Math.max(0L, Math.round(s.getLongOr("v", 0L) * (1.0D - BIAYA_TARIK)));
					shares.remove(key);
					keLaci(server, id, pay, "");
					UsahaWarga.Usaha u = UsahaWarga.usaha(situs, c.getIntOr("i", 0));
					kabarPemain(server, id, Component.translatable("kultivator.kota42.investasi.tarik_cair", u == null ? "?" : UsahaWarga.nama(u),
							NilaiBatu.text(pay)));
					if (key.equals(c.getStringOr("pgp", ""))) {
						c.remove("pg");
						c.remove("pgp");
					}
				}
			}
		}
		KotaEkonomiData.get(server).ubah();
	}

	/** Weekly upkeep of the ledgers (old faucet weeks are reset lazily). */
	static void mingguan(MinecraftServer server, long minggu) {
	}

	/** Shares a business's week out to its holders (called by {@link UsahaWarga#mingguan}). */
	static void bagiLaba(MinecraftServer server, long situs, CompoundTag c, long week, long modal, long minggu) {
		CompoundTag shares = c.getCompoundOrEmpty("s");
		if (shares.isEmpty() || modal <= 0L) {
			return;
		}
		UsahaWarga.Usaha u = UsahaWarga.usaha(situs, c.getIntOr("i", 0));
		Component name = u == null ? Component.literal("?") : UsahaWarga.nama(u);
		for (String key : List.copyOf(shares.keySet())) {
			CompoundTag s = shares.getCompoundOrEmpty(key);
			UUID id = uuid(key);
			long value = s.getLongOr("v", 0L);
			if (id == null || value <= 0L) {
				continue;
			}
			long part = Math.round(week * (value / (double) modal));
			long paid;
			if (part > 0L) {
				int band = pita(server, id);
				long room = Math.max(0L, BATAS_TOTAL * (band + 1L) - keranMinggu(server, id, K_INVESTASI));
				paid = Math.min(part, Math.min(BATAS_PER_USAHA * (band + 1L), room));
				keLaci(server, id, paid, K_INVESTASI);
			} else {
				paid = part;
				s.putLong("v", Math.max(0L, value + part));
			}
			s.putLong("l", paid);
			long[] old = s.getLongArray("g").orElse(new long[0]);
			long[] next = new long[7];
			next[0] = s.getLongOr("v", 0L);
			System.arraycopy(old, 0, next, 1, Math.min(old.length, 6));
			s.putLongArray("g", next);
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			if (online != null && paid > 0L) {
				Advancements.grant(online, "tempat/kota42_investor");
			}
			if (online != null && paid != 0L) {
				Msg.chat(online, Component.translatable(paid > 0L ? "kultivator.kota42.investasi.laba" : "kultivator.kota42.investasi.rugi", name,
						NilaiBatu.text(Math.abs(paid))).withStyle(paid > 0L ? ChatFormatting.GREEN : ChatFormatting.RED));
			}
		}
		KotaEkonomiData.get(server).ubah();
	}

	/** A closing business pays {@code bagian} (0.7-1.0) of every stake into the drawers. */
	static void tutup(MinecraftServer server, long situs, CompoundTag c, double bagian, Component teks) {
		CompoundTag shares = c.getCompoundOrEmpty("s");
		for (String key : shares.keySet()) {
			UUID id = uuid(key);
			if (id == null) {
				continue;
			}
			long pay = Math.round(shares.getCompoundOrEmpty(key).getLongOr("v", 0L) * bagian);
			keLaci(server, id, pay, "");
			kabarPemain(server, id, teks.copy().append(" ").append(Component.translatable("kultivator.kota42.investasi.tutup_bayar", NilaiBatu.text(pay))));
		}
	}

	static @Nullable UUID uuid(String text) {
		try {
			return text == null || text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	// ================================================================== commands

	/** {@code /kultivasi kotaku investasi [peluang | tanam <id> <nb> | tarik <id> | batal <id>]}. */
	static LiteralArgumentBuilder<CommandSourceStack> perintah() {
		return Commands.literal("investasi").executes(ctx -> portofolio(ctx.getSource()))
				.then(Commands.literal("peluang").executes(ctx -> peluang(ctx.getSource())))
				.then(Commands.literal("tanam").then(Commands.argument("id", IntegerArgumentType.integer(1))
						.then(Commands.argument("nb", IntegerArgumentType.integer(1)).executes(ctx -> aksi(ctx, "tanam")))))
				.then(Commands.literal("tarik").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> aksi(ctx, "tarik"))))
				.then(Commands.literal("batal").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> aksi(ctx, "batal"))));
	}

	/** {@code /kultivasi kotaku laci}: pays the drawer out. */
	static LiteralArgumentBuilder<CommandSourceStack> perintahLaci() {
		return Commands.literal("laci").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			long nb = ambilLaci(player);
			if (nb <= 0L) {
				ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.laci.kosong"));
				return 0;
			}
			ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.laci.ambil", NilaiBatu.text(nb)).withStyle(ChatFormatting.GOLD),
					false);
			return 1;
		});
	}

	private static int aksi(CommandContext<CommandSourceStack> ctx, String op) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int id = IntegerArgumentType.getInteger(ctx, "id");
		long situs = situsUsaha(player, id);
		if (situs == 0L) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.investasi.tiada"));
			return 0;
		}
		Component why = switch (op) {
			case "tanam" -> tanam(player, situs, id, IntegerArgumentType.getInteger(ctx, "nb"));
			case "tarik" -> tarik(player, situs, id);
			default -> batalTarik(player, situs, id);
		};
		if (why != null) {
			ctx.getSource().sendFailure(why);
			return 0;
		}
		return 1;
	}

	/** The place of business {@code id}: the player's own stake first, then the place he stands in. */
	private static long situsUsaha(ServerPlayer player, int id) {
		for (Saham s : milik(player.level().getServer(), player.getUUID())) {
			if (s.usaha() == id) {
				return s.situs();
			}
		}
		TempatInfo here = PajakKota.tempat(player);
		return here != null && UsahaWarga.mentah(player.level().getServer(), here.situsId(), id) != null ? here.situsId() : 0L;
	}

	private static int portofolio(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MinecraftServer server = source.getServer();
		List<Saham> all = milik(server, player.getUUID());
		long planted = 0L;
		long value = 0L;
		long week = 0L;
		for (Saham s : all) {
			planted += s.setor();
			value += s.nilai();
			week += s.labaMinggu();
		}
		int band = pita(player);
		long p = planted;
		long v = value;
		long w = week;
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.investasi.ringkas", p, v, w, keranMinggu(server, player.getUUID(), K_INVESTASI),
				BATAS_TOTAL * (band + 1L), laci(server, player.getUUID())).withStyle(ChatFormatting.GOLD), false);
		for (Saham s : all) {
			UsahaWarga.Usaha u = UsahaWarga.usaha(s.situs(), s.usaha());
			if (u == null) {
				continue;
			}
			Component warn = UsahaWarga.peringatan(server, s.situs(), s.usaha());
			MutableComponent line = Component.translatable("kultivator.kota42.investasi.baris", s.usaha(), UsahaWarga.nama(u), PajakKota.nama(server, s.situs()),
					Math.round(s.bagian() * 100.0D), s.setor(), s.nilai(), s.labaMinggu());
			if (s.tarikHari() > 0L) {
				line.append(Component.translatable("kultivator.kota42.investasi.baris_tarik", s.tarikHari()).withStyle(ChatFormatting.YELLOW));
			}
			if (warn != null) {
				line.append(Component.literal(" ⚠ ").append(warn).withStyle(ChatFormatting.RED));
			}
			source.sendSuccess(() -> line, false);
		}
		if (all.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.investasi.kosong").withStyle(ChatFormatting.GRAY), false);
		}
		return all.size();
	}

	private static int peluang(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TempatInfo tempat = PajakKota.tempat(player);
		if (tempat == null) {
			source.sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		MinecraftServer server = source.getServer();
		long situs = tempat.situsId();
		UsahaWarga.pastikan(server, situs, KotaHidup.hari(server));
		List<UsahaWarga.Usaha> all = UsahaWarga.usaha(situs);
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.investasi.peluang_judul", PajakKota.nama(server, situs), maks(player))
				.withStyle(ChatFormatting.GOLD), false);
		for (UsahaWarga.Usaha u : all) {
			Warga owner = WargaApi.byId(u.pemilikWarga());
			long[] h = UsahaWarga.laba(server, situs, u.id());
			Component why = alasan(player, situs, u.id(), MIN);
			MutableComponent line = Component.translatable("kultivator.kota42.investasi.peluang_baris", u.id(), UsahaWarga.nama(u),
					owner == null ? "?" : WargaApi.nama(owner), u.modal(), UsahaWarga.omzet(server, situs, u.id()), UsahaWarga.marginPersen(u),
					h.length > 0 ? h[0] : 0L);
			if (why == null) {
				line.append(Component.translatable("kultivator.kota42.investasi.tombol_tanam").withStyle(style -> style.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.SuggestCommand("/kultivasi kotaku investasi tanam " + u.id() + " " + MIN))));
			} else {
				line.append(Component.literal(" (").append(why).append(")").withStyle(ChatFormatting.DARK_GRAY));
			}
			source.sendSuccess(() -> line, false);
		}
		return all.size();
	}

	// ================================================================== uji

	/** {@code uji kota_hidup ekonomi investasi [minggu <n>]}: list, or run n simulated weeks of the place. */
	static LiteralArgumentBuilder<CommandSourceStack> uji() {
		return Commands.literal("investasi").executes(ctx -> portofolio(ctx.getSource()))
				.then(Commands.literal("minggu").then(Commands.argument("n", IntegerArgumentType.integer(1, 52)).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TempatInfo tempat = PajakKota.tempat(player);
					if (tempat == null) {
						ctx.getSource().sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
						return 0;
					}
					MinecraftServer server = ctx.getSource().getServer();
					int n = IntegerArgumentType.getInteger(ctx, "n");
					long base = KotaHidup.hari(server);
					for (int week = 0; week < n; week++) {
						for (int day = 0; day < 7; day++) {
							UsahaWarga.harian(server, tempat.situsId(), base + week * 7L + day);
						}
						UsahaWarga.mingguan(server, tempat.situsId(), Math.floorDiv(base, 7L) + week);
					}
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.kota42.uji.investasi_minggu", n), true);
					return portofolio(ctx.getSource());
				})));
	}

	private Investasi() {
	}
}
