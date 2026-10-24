package dev.kultivator.dunia.tempat.tema.bersama;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.tema.Tema;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Buku Dagang screen {@code tema_dagang} (DESIGN 38.T.13.3; sheet keys {@link TemaLayar.Dagang}), its market mode,
 * the player command {@code /kultivasi dagang} and the sealed-crate use. Registered as a hub screen: from the hub card or
 * the command it is read only (prices last seen, profit this week against the cap, running cargo contracts, Infami and
 * Gentar); opened by a theme's market NPC or counter through {@link #bukaPasar}/{@link #bukaPasarDi} it also carries the
 * {@code pasar} block (buy rows of the town's own goods, sell rows of the goods the player carries, crates to seal) and
 * accepts {@code beli:ID:N}, {@code jual:ID:N}, {@code segel:ID}. While the client has no market section the same
 * market is offered as a clickable list in chat ({@code /kultivasi dagang beli|jual|segel}, valid at that market only).
 */
public final class DagangLayar {

	/** Most town columns in the book. */
	public static final int KOLOM_MAKS = 12;
	/** A market session stays valid for this many ticks and within this many blocks of where it opened. */
	public static final long SESI_TICK = 12000L;
	public static final double SESI_JARAK = 24.0D;

	/** Player -> the market he opened last. */
	private record Pasar(long situsId, Vec3 di, long sampai, @Nullable Dagang.Opsi opsi) {
	}

	private static final Map<UUID, Pasar> PASAR = new HashMap<>();
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TemaLayar.daftarHub(TemaLayar.Dagang.LAYAR, DagangLayar::bangunHub, DagangLayar::aksi);
		TempatDialog.daftarJasa(DagangLayar::jasa);
		DuniaCommands.player("dagang", DagangLayar::perintah);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(DagangItems.PETI)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				Dagang.bukaPeti(sp, stack);
			}
			return InteractionResult.SUCCESS;
		});
	}

	// ================================================================================================ opening

	/** Opens the book (read only) on {@code tab} ({@code null} = prices). */
	public static void buka(ServerPlayer player, int npcId, @Nullable String tab) {
		CompoundTag sheet = bangun(player, npcId);
		if (sheet == null) {
			return;
		}
		if (tab != null) {
			sheet.putString(LembarKunci.TAB, tab);
		}
		TempatLayar.buka(player, TemaLayar.Dagang.LAYAR, npcId, sheet);
	}

	/**
	 * A theme's market NPC {@code npcId} opens the book in market mode for town {@code situsId} ({@code opsi}: a fence
	 * ({@code penadah}) or a sales duty; null = ordinary). Also sends the chat market list.
	 */
	public static void bukaPasar(ServerPlayer player, long situsId, int npcId, @Nullable Dagang.Opsi opsi) {
		PASAR.put(player.getUUID(), new Pasar(situsId, player.position(), player.level().getGameTime() + SESI_TICK, opsi));
		Dagang.lihatHarga(player, situsId);
		CompoundTag sheet = bangun(player, npcId);
		if (sheet != null) {
			TempatLayar.buka(player, TemaLayar.Dagang.LAYAR, npcId, sheet);
		}
		pasarChat(player, situsId);
	}

	/**
	 * Market roles (F2 wiring): in a themed town or a capital, these NPCs list "Pasar" in their dialog's Jasa tab, and
	 * opening it starts market mode for the NPC's own town. {@link #PENADAH} are fences (contraband accepted).
	 */
	private static final EnumSet<NpcRole> PASAR_PERAN = EnumSet.of(NpcRole.PEDAGANG_SEBERANG, NpcRole.PENJUAL_IKAN,
			NpcRole.PEDAGANG_BAZAR, NpcRole.KAFILAH_BASHI, NpcRole.NYONYA_SUTRA, NpcRole.AHLI_TEH, NpcRole.PEMETIK_HERBA,
			NpcRole.PEDAGANG_KELILING, NpcRole.MAKELAR_GELAP);
	private static final EnumSet<NpcRole> PENADAH = EnumSet.of(NpcRole.MAKELAR_GELAP);
	/** Cargo-contract issuers (38.T.10.3): Balai Dewan Saudagar, Karavanserai Agung, Rumah Sutra, Pos Kuli. */
	private static final EnumSet<NpcRole> PAPAN_PERAN = EnumSet.of(NpcRole.SYAHBANDAR, NpcRole.KAFILAH_BASHI,
			NpcRole.NYONYA_SUTRA, NpcRole.KULI_GUNUNG);
	/** Kantor Urusan Laut: 2 % duty on sales at the Pasar Seberang (38.T.10.1). */
	private static final double BEA_SEBERANG = 0.02D;

	private static boolean pasarNpc(MinecraftServer server, KultivatorNpc npc) {
		return PASAR_PERAN.contains(npc.role()) && Dagang.temaKolom(server, npc.town()) != null;
	}

	private static @Nullable TemaKota.Kota papanNpc(MinecraftServer server, KultivatorNpc npc) {
		return PAPAN_PERAN.contains(npc.role()) ? TemaKota.dari(server, npc.town()) : null;
	}

	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer player, KultivatorNpc npc) {
		MinecraftServer server = player.level().getServer();
		if (pasarNpc(server, npc)) {
			return List.of(new TempatDialog.JasaEntri(TemaLayar.Dagang.LAYAR,
					Component.translatable("kultivator.kota_tema.dagang.pasar", Dagang.namaSitus(server, npc.town())),
					LembarKunci.Ikon.TIMBANGAN, null, false, null));
		}
		TemaKota.Kota kota = papanNpc(server, npc);
		if (kota != null) {
			return List.of(new TempatDialog.JasaEntri(TemaLayar.Dagang.LAYAR,
					Component.translatable("kultivator.kota_tema.kontrak.papan", kota.nama(server.overworld().getSeed())),
					LembarKunci.Ikon.TIMBANGAN, null, false, null));
		}
		return List.of();
	}

	/** Hub builder: opened from a market NPC (dialog Jasa tab) it starts market mode for that NPC's town first. */
	private static @Nullable CompoundTag bangunHub(ServerPlayer player, int npcId) {
		if (npcId >= 0 && player.level().getEntity(npcId) instanceof KultivatorNpc npc && pasarNpc(player.level().getServer(), npc)) {
			long situs = npc.town();
			Pasar lama = pasar(player);
			if (lama == null || lama.situsId() != situs) {
				Dagang.Opsi opsi = PENADAH.contains(npc.role()) ? new Dagang.Opsi(true, 0.0D)
						: npc.role() == NpcRole.PEDAGANG_SEBERANG ? new Dagang.Opsi(false, BEA_SEBERANG) : null;
				PASAR.put(player.getUUID(), new Pasar(situs, player.position(), player.level().getGameTime() + SESI_TICK, opsi));
				Dagang.lihatHarga(player, situs);
				pasarChat(player, situs);
			}
		}
		if (npcId >= 0 && player.level().getEntity(npcId) instanceof KultivatorNpc issuer) {
			TemaKota.Kota kota = papanNpc(player.level().getServer(), issuer);
			if (kota != null && KontrakMuatan.papanChat(player, kota)) {
				CompoundTag sheet = bangun(player, npcId);
				if (sheet != null && !pasarNpc(player.level().getServer(), issuer)) {
					sheet.putString(LembarKunci.TAB, TemaLayar.Dagang.TAB_KONTRAK);
				}
				return sheet;
			}
		}
		return bangun(player, npcId);
	}

	/** As {@link #bukaPasar} at a counter anchor block {@code jangkar} (no NPC body). */
	public static void bukaPasarDi(ServerPlayer player, long situsId, BlockPos jangkar, @Nullable Dagang.Opsi opsi) {
		PASAR.put(player.getUUID(), new Pasar(situsId, player.position(), player.level().getGameTime() + SESI_TICK, opsi));
		Dagang.lihatHarga(player, situsId);
		CompoundTag sheet = bangun(player, -1);
		if (sheet != null) {
			TempatLayar.bukaDi(player, TemaLayar.Dagang.LAYAR, jangkar, sheet);
		}
		pasarChat(player, situsId);
	}

	private static @Nullable Pasar pasar(ServerPlayer player) {
		Pasar p = PASAR.get(player.getUUID());
		if (p == null) {
			return null;
		}
		if (player.level().getGameTime() > p.sampai() || player.position().distanceToSqr(p.di()) > SESI_JARAK * SESI_JARAK
				|| Dagang.pasarDi(player) != p.situsId()) {
			PASAR.remove(player.getUUID());
			return null;
		}
		return p;
	}

	private static Dagang.Opsi opsi(Pasar p) {
		return p.opsi() == null ? Dagang.Opsi.BIASA : p.opsi();
	}

	// ================================================================================================ the sheet

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = new CompoundTag();
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable("kultivator.kota_tema.dagang.judul"));
		tag.putString(LembarKunci.GAYA, LembarKunci.GAYA_KERTAS);
		tag.putLong(LembarKunci.UANG, NilaiBatu.total(player));
		tag.putIntArray(LembarKunci.BATU, Barang.batu(player));
		tag.putInt(LembarKunci.PITA, Barang.pita(player));
		long today = Dagang.hari(server);
		// --- town columns: towns with book prices first, then known themed towns and capitals (nearest first)
		List<Long> towns = new ArrayList<>();
		for (long id : Dagang.bukuSitus(player)) {
			if (Dagang.temaKolom(server, id) != null && !towns.contains(id)) {
				towns.add(id);
			}
		}
		for (DuniaSites.Site site : DuniaSites.known(player)) {
			boolean themed = site.kind().startsWith("kota_tema/") && Tema.dari(site.kind()) != null;
			if ((themed || "kota/kota_besar".equals(site.kind())) && !towns.contains(site.id())) {
				towns.add(site.id());
			}
		}
		towns.sort(Comparator.comparingDouble(id -> jarak(server, player, id)));
		if (towns.size() > KOLOM_MAKS) {
			towns = new ArrayList<>(towns.subList(0, KOLOM_MAKS));
		}
		ListTag kota = new ListTag();
		for (long id : towns) {
			CompoundTag k = new CompoundTag();
			k.putString(LembarKunci.ID, Long.toString(id));
			LembarKunci.teks(k, LembarKunci.NAMA, Dagang.namaSitus(server, id));
			String kolom = Dagang.temaKolom(server, id);
			k.putString(TemaLayar.Dagang.TEMA, kolom == null ? "ibu_kota" : kolom);
			k.putInt(LembarKunci.JARAK, (int) jarak(server, player, id));
			kota.add(k);
		}
		tag.put(TemaLayar.Dagang.KOTA, kota);
		if (towns.isEmpty()) {
			CompoundTag kosong = new CompoundTag();
			LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable("kultivator.kota_tema.dagang.kosong"));
			tag.put(LembarKunci.KOSONG, kosong);
		}
		// --- price rows
		ListTag harga = new ListTag();
		for (DagangItems.Barang b : DagangItems.barang()) {
			Item item = DagangItems.item(b.id());
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, "dagang/" + b.id());
			LembarKunci.teks(row, LembarKunci.NAMA, Dagang.nama(b));
			if (item != null) {
				LembarKunci.barang(row, new ItemStack(item), player.registryAccess());
			}
			row.putString(TemaLayar.Dagang.ASAL, b.asal().id());
			ListTag cells = new ListTag();
			CompoundTag best = null;
			double bestNb = 0.0D;
			for (long id : towns) {
				CompoundTag seen = Dagang.buku(player, id).getCompoundOrEmpty(b.id());
				if (seen.isEmpty()) {
					continue;
				}
				CompoundTag cell = new CompoundTag();
				cell.putString(TemaLayar.Dagang.KOTA, Long.toString(id));
				boolean asal = seen.getBooleanOr("asal", false);
				double nb = asal ? b.dasar() : seen.getDoubleOr("nb", 0.0D);
				int umur = (int) Math.max(0L, today - seen.getLongOr("hari", today));
				cell.putFloat(LembarKunci.NB, (float) nb);
				cell.putInt(TemaLayar.Dagang.UMUR_HARI, umur);
				cell.putFloat(TemaLayar.Dagang.JENUH, (float) seen.getDoubleOr("jenuh", 1.0D));
				cell.putBoolean(TemaLayar.Dagang.ASAL, asal);
				if (!asal && umur <= 3 && nb > bestNb) {
					bestNb = nb;
					best = cell;
				}
				cells.add(cell);
			}
			if (best != null) {
				best.putBoolean(TemaLayar.Dagang.TERBAIK, true);
			}
			row.put(TemaLayar.Dagang.SEL, cells);
			harga.add(row);
		}
		tag.put(TemaLayar.Dagang.HARGA, harga);
		// --- weekly profit
		CompoundTag laba = new CompoundTag();
		laba.putLong(TemaLayar.Dagang.MINGGU, Dagang.labaMinggu(player));
		laba.putLong(TemaLayar.Dagang.BATAS, Dagang.batas(player));
		laba.putIntArray(TemaLayar.Dagang.HARI_LABA, Dagang.labaHarian(player));
		laba.putBoolean(TemaLayar.Dagang.LEWAT, Dagang.lewatBatas(player));
		laba.putLong(TemaLayar.Dagang.TOTAL, Dagang.labaTotal(player));
		tag.put(TemaLayar.Dagang.LABA, laba);
		// --- cargo contracts
		ListTag kontrak = new ListTag();
		long now = server.overworld().getGameTime();
		for (Tag each : KontrakMuatan.aktif(player)) {
			if (!(each instanceof CompoundTag c)) {
				continue;
			}
			DagangItems.Barang b = DagangItems.barang(c.getStringOr("barang", ""));
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, c.getStringOr("id", ""));
			LembarKunci.teks(row, LembarKunci.NAMA, b == null ? Component.literal("?") : Component.translatable("kultivator.kota_tema.kontrak.nama", Dagang.nama(b)));
			LembarKunci.teks(row, LembarKunci.TUJUAN, Dagang.namaSitus(server, c.getLongOr("tujuan", 0L)));
			row.putInt(TemaLayar.Dagang.PETI, c.getIntOr("peti", 1));
			row.putInt(LembarKunci.SISA_HARI, (int) Math.max(0L, (c.getLongOr("batas", now) - now) / 24000L));
			row.putLong(TemaLayar.Dagang.DEPOSIT, c.getLongOr("deposit", 0L));
			row.putLong(TemaLayar.Dagang.BAYAR, c.getLongOr("bayar", 0L));
			row.putBoolean(TemaLayar.Dagang.KAPAL, false);
			kontrak.add(row);
		}
		tag.put(TemaLayar.Dagang.KONTRAK, kontrak);
		// --- meters
		tag.putInt(TemaLayar.Dagang.INFAMI, TemaApi.infami(player));
		ListTag gentar = new ListTag();
		for (DuniaSites.Site site : DuniaSites.known(player)) {
			if (Tema.IBLIS.jenisSitus().equals(site.kind())) {
				CompoundTag g = new CompoundTag();
				LembarKunci.teks(g, TemaLayar.Dagang.KOTA, Dagang.namaSitus(server, site.id()));
				g.putInt(LembarKunci.NILAI, TemaApi.gentar(player, site.id()));
				gentar.add(g);
			}
		}
		tag.put(TemaLayar.Dagang.GENTAR, gentar);
		// --- market mode
		Pasar p = npcId >= 0 || PASAR.containsKey(player.getUUID()) ? pasar(player) : null;
		if (p != null) {
			tag.put(TemaLayar.Dagang.PASAR, pasarTag(player, p));
		}
		return tag;
	}

	private static CompoundTag pasarTag(ServerPlayer player, Pasar p) {
		MinecraftServer server = player.level().getServer();
		CompoundTag pasar = new CompoundTag();
		pasar.putString(LembarKunci.ID, Long.toString(p.situsId()));
		LembarKunci.teks(pasar, LembarKunci.NAMA, Dagang.namaSitus(server, p.situsId()));
		ListTag beli = new ListTag();
		ListTag jual = new ListTag();
		ListTag segel = new ListTag();
		for (DagangItems.Barang b : DagangItems.barang()) {
			Item item = DagangItems.item(b.id());
			if (item == null) {
				continue;
			}
			long buy = Dagang.hargaBeli(player, p.situsId(), b);
			if (buy > 0L) {
				CompoundTag row = new CompoundTag();
				row.putString(LembarKunci.ID, b.id());
				LembarKunci.teks(row, LembarKunci.NAMA, Dagang.nama(b));
				LembarKunci.barang(row, new ItemStack(item), player.registryAccess());
				row.putLong(LembarKunci.NB, buy);
				row.putInt(TemaLayar.Dagang.STOK, Dagang.sisaStok(player, b.id()));
				beli.add(row);
			}
			int have = Dagang.hitung(player, item);
			if (have > 0) {
				CompoundTag row = new CompoundTag();
				row.putString(LembarKunci.ID, b.id());
				LembarKunci.teks(row, LembarKunci.NAMA, Dagang.nama(b));
				LembarKunci.barang(row, new ItemStack(item), player.registryAccess());
				row.putInt(LembarKunci.JUMLAH, have);
				double sell = Dagang.hargaJual(player, p.situsId(), b, opsi(p));
				row.putFloat(LembarKunci.NB, (float) sell);
				row.putFloat(TemaLayar.Dagang.JENUH, (float) Dagang.jenuh(server, p.situsId(), b.id()));
				if (sell <= 0.0D) {
					row.putBoolean(LembarKunci.TERKUNCI, true);
					LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable(b.selundupan()
							? "kultivator.kota_tema.dagang.selundupan" : "kultivator.kota_tema.dagang.tak_dibeli"));
				}
				jual.add(row);
				if (have >= PetiDagangItem.ISI_PETI) {
					CompoundTag s = new CompoundTag();
					s.putString(LembarKunci.ID, b.id());
					LembarKunci.teks(s, LembarKunci.NAMA, Dagang.nama(b));
					s.putLong(LembarKunci.NB, Dagang.SEGEL);
					segel.add(s);
				}
			}
		}
		pasar.put(TemaLayar.Dagang.BELI, beli);
		pasar.put(TemaLayar.Dagang.JUAL, jual);
		pasar.put(TemaLayar.Dagang.SEGEL, segel);
		pasar.putBoolean(TemaLayar.Dagang.LEWAT, Dagang.lewatBatas(player));
		return pasar;
	}

	private static double jarak(MinecraftServer server, ServerPlayer player, long id) {
		BlockPos pos = Dagang.posSitus(server, id);
		return pos == null ? 1.0E9D : Math.hypot(pos.getX() - player.getX(), pos.getZ() - player.getZ());
	}

	// ================================================================================================ actions

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		String verb = parts[0];
		Pasar p = pasar(player);
		if (p == null) {
			return;
		}
		DagangItems.Barang b = DagangItems.barang(LembarKunci.arg(parts, 1));
		if (b == null) {
			return;
		}
		int n = Math.max(1, Math.min(64, LembarKunci.argInt(parts, 2, 1)));
		Dagang.Hasil hasil = switch (verb) {
			case TemaLayar.Dagang.OP_BELI -> Dagang.beli(player, p.situsId(), b, n);
			case TemaLayar.Dagang.OP_JUAL -> Dagang.jual(player, p.situsId(), b, n, opsi(p));
			case TemaLayar.Dagang.OP_SEGEL -> Dagang.segel(player, p.situsId(), b, "");
			default -> null;
		};
		if (hasil != null) {
			pesan(player, hasil);
		}
	}

	private static void pesan(ServerPlayer player, Dagang.Hasil hasil) {
		player.sendSystemMessage(hasil.pesan().copy().withStyle(hasil.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	// ================================================================================================ chat market

	private static void pasarChat(ServerPlayer player, long situsId) {
		MinecraftServer server = player.level().getServer();
		Pasar p = PASAR.get(player.getUUID());
		Dagang.Opsi o = p == null ? Dagang.Opsi.BIASA : opsi(p);
		player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.pasar", Dagang.namaSitus(server, situsId))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (DagangItems.Barang b : DagangItems.barang()) {
			Item item = DagangItems.item(b.id());
			if (item == null) {
				continue;
			}
			long buy = Dagang.hargaBeli(player, situsId, b);
			if (buy > 0L) {
				MutableComponent line = Component.translatable("kultivator.kota_tema.dagang.baris_beli", Dagang.nama(b), NilaiBatu.text(buy),
						Dagang.sisaStok(player, b.id())).withStyle(ChatFormatting.GRAY);
				line.append(tombol("kultivator.kota_tema.dagang.tombol_beli1", "/kultivasi dagang beli " + b.id() + " 1"));
				line.append(tombol("kultivator.kota_tema.dagang.tombol_beli8", "/kultivasi dagang beli " + b.id() + " 8"));
				player.sendSystemMessage(line);
			}
			int have = Dagang.hitung(player, item);
			if (have > 0) {
				double sell = Dagang.hargaJual(player, situsId, b, o);
				MutableComponent line = sell > 0.0D
						? Component.translatable("kultivator.kota_tema.dagang.baris_jual", Dagang.nama(b), have,
								Component.literal(String.format(java.util.Locale.ROOT, "%.1f", sell))).withStyle(ChatFormatting.GRAY)
						: Component.translatable("kultivator.kota_tema.dagang.baris_tolak", Dagang.nama(b), have).withStyle(ChatFormatting.DARK_GRAY);
				if (sell > 0.0D) {
					line.append(tombol("kultivator.kota_tema.dagang.tombol_jual", "/kultivasi dagang jual " + b.id() + " " + have));
				}
				if (have >= PetiDagangItem.ISI_PETI) {
					line.append(tombol("kultivator.kota_tema.dagang.tombol_segel", "/kultivasi dagang segel " + b.id()));
				}
				player.sendSystemMessage(line);
			}
		}
		if (Dagang.lewatBatas(player)) {
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.lewat_batas", NilaiBatu.text(Dagang.batas(player)))
					.withStyle(ChatFormatting.YELLOW));
		}
	}

	private static Component tombol(String key, String cmd) {
		return Component.literal(" ").append(Component.translatable(key).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand(cmd)).withHoverEvent(new HoverEvent.ShowText(Component.literal(cmd)))));
	}

	// ================================================================================================ /kultivasi dagang

	private static LiteralArgumentBuilder<CommandSourceStack> perintah() {
		return Commands.literal("dagang")
				.executes(ctx -> {
					buka(ctx.getSource().getPlayerOrException(), -1, null);
					return 1;
				})
				.then(Commands.literal("beli").then(Commands.argument("barang", StringArgumentType.word()).suggests(DagangLayar::saranBarang)
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 64)).executes(ctx -> pasarPerintah(ctx.getSource().getPlayerOrException(),
								TemaLayar.Dagang.OP_BELI, StringArgumentType.getString(ctx, "barang"), IntegerArgumentType.getInteger(ctx, "n"))))))
				.then(Commands.literal("jual").then(Commands.argument("barang", StringArgumentType.word()).suggests(DagangLayar::saranBarang)
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 4096)).executes(ctx -> pasarPerintah(ctx.getSource().getPlayerOrException(),
								TemaLayar.Dagang.OP_JUAL, StringArgumentType.getString(ctx, "barang"), IntegerArgumentType.getInteger(ctx, "n"))))))
				.then(Commands.literal("segel").then(Commands.argument("barang", StringArgumentType.word()).suggests(DagangLayar::saranBarang)
						.executes(ctx -> pasarPerintah(ctx.getSource().getPlayerOrException(), TemaLayar.Dagang.OP_SEGEL,
								StringArgumentType.getString(ctx, "barang"), 1))))
				.then(Commands.literal("kontrak")
						.executes(ctx -> {
							buka(ctx.getSource().getPlayerOrException(), -1, TemaLayar.Dagang.TAB_KONTRAK);
							return 1;
						})
						.then(Commands.literal("ambil").then(Commands.argument("i", IntegerArgumentType.integer(0, KontrakMuatan.TAWARAN - 1))
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayerOrException();
									player.sendSystemMessage(KontrakMuatan.ambil(player, IntegerArgumentType.getInteger(ctx, "i")).copy().withStyle(ChatFormatting.GOLD));
									return 1;
								})))
						.then(Commands.literal("serah").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							for (Component line : KontrakMuatan.serahSemua(player)) {
								player.sendSystemMessage(line.copy().withStyle(ChatFormatting.GOLD));
							}
							return 1;
						})));
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> saranBarang(
			com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		List<String> ids = new ArrayList<>();
		for (DagangItems.Barang b : DagangItems.barang()) {
			ids.add(b.id());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	}

	private static int pasarPerintah(ServerPlayer player, String verb, String barang, int n) {
		Pasar p = pasar(player);
		if (p == null) {
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.bukan_pasar").withStyle(ChatFormatting.RED));
			return 0;
		}
		DagangItems.Barang b = DagangItems.barang(barang);
		if (b == null) {
			player.sendSystemMessage(Component.translatable("kultivator.kota_tema.dagang.tak_dikenal", barang).withStyle(ChatFormatting.RED));
			return 0;
		}
		Dagang.Hasil hasil = switch (verb) {
			case TemaLayar.Dagang.OP_BELI -> Dagang.beli(player, p.situsId(), b, n);
			case TemaLayar.Dagang.OP_JUAL -> Dagang.jual(player, p.situsId(), b, n, opsi(p));
			default -> Dagang.segel(player, p.situsId(), b, "");
		};
		pesan(player, hasil);
		return hasil.ok() ? 1 : 0;
	}

	/** Forgets market sessions (server stop). */
	static void lupakan() {
		PASAR.clear();
	}

	/** The town id of the market the player has open (0 = none). */
	public static long pasarTerbuka(ServerPlayer player) {
		Pasar p = pasar(player);
		return p == null ? 0L : p.situsId();
	}

	/** For theme code: the town of a themed-town market at {@code pos}, or 0 (helper for anchors). */
	public static long kotaDi(ServerPlayer player) {
		TemaKota.Kota kota = TemaKota.di(player.level(), player.blockPosition());
		return kota == null ? 0L : kota.situsId();
	}

	private DagangLayar() {
	}
}
