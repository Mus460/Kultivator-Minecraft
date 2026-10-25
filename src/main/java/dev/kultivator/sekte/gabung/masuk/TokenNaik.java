package dev.kultivator.sekte.gabung.masuk;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.RootGrade;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.Gabung;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.KelasSekte;
import dev.kultivator.sekte.mandiri.seteru.Roster;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Tokens and letters of the doors (DESIGN 41.5.2), and the candidate token.
 * <ul>
 * <li><b>Token Naik Sekte</b> ({@code kultivator:token_naik}, Han Li's 升仙令): opens door TOKEN; with
 * {@code kultivator:sekte_token} only for that sect, without it for any sect of class &le; 3; used up at the start of the
 * test. Sources: 2 % in the inheritance and chamber chests of the ruins ({@code chests/dunia/reruntuhan/*}, loot pool added
 * here), one lot line of the Lelang Agung table ({@code tempat/lelang/agung.json}, class "tinggi"), story effect
 * {@code gabung.beri_token [sekte]} (city quest chains of 38.S), and a friendly roster disciple (39F, attitude &ge; 80) who
 * sends the token of their own sect once.</li>
 * <li><b>Surat Undangan</b> ({@code kultivator:surat_undangan} with {@code sekte_token}): opens door UNDANGAN of the
 * sect that sent it. An envoy brings one to a talented player (root HIGH or better) from a sect with standing &ge; 40, at
 * most once per 20 days ({@link #utusan}); story effect {@code gabung.undangan <sekte>} for the tournaments.</li>
 * <li><b>Tanda Calon</b> ({@code kultivator:tanda_calon}, component {@code anggota} with rank CALON): given with the
 * application, taken back when the candidacy ends.</li>
 * </ul>
 */
public final class TokenNaik {

	/** Ruin chest chance (41.5.2: 2 %). */
	public static final float PELUANG_RERUNTUHAN = 0.02F;
	/** Days between two envoy letters, and the standing and root an envoy asks for. */
	public static final int UTUSAN_HARI = 20;
	public static final int UTUSAN_STANDING = 40;
	/** A roster disciple this friendly sends their sect's token (once). */
	public static final int SAHABAT_SIKAP = 80;

	private static final Set<String> RERUNTUHAN = Set.of("agung_kamar", "agung_warisan", "kecil_warisan", "kuno_kamar", "kuno_warisan");
	private static final String UTUSAN = "utusan_hari", TOKEN_DARI = "token_dari";

	static void init() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			if (!source.isBuiltin() || !key.identifier().getNamespace().equals(Kultivator.MOD_ID)) {
				return;
			}
			String path = key.identifier().getPath();
			if (path.startsWith("chests/dunia/reruntuhan/") && RERUNTUHAN.contains(path.substring("chests/dunia/reruntuhan/".length()))) {
				builder.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(PELUANG_RERUNTUHAN))
						.add(LootItem.lootTableItem(GabungItems.TOKEN_NAIK)));
			}
		});
		Keanggotaan.addPenyentuh((player, k, hari) -> {
			utusan(player, k, hari);
			sahabat(player, k);
		});
	}

	// ================================================================== Token Naik Sekte

	/** A token in the bag valid for {@code sekte} (its own first, then a general one for class &le; 3), or EMPTY. */
	public static ItemStack cari(ServerPlayer player, String sekte) {
		Inventory inv = player.getInventory();
		ItemStack umum = ItemStack.EMPTY;
		boolean kecil = GabungApi.kelas(sekte) <= KelasSekte.BESAR.nomor();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.is(GabungItems.TOKEN_NAIK)) {
				continue;
			}
			GabungItems.SekteToken tok = s.get(GabungItems.SEKTE_TOKEN);
			if (tok != null && sekte.equals(tok.sekte())) {
				return s;
			}
			if (tok == null && kecil && umum.isEmpty()) {
				umum = s;
			}
		}
		return umum;
	}

	/** Uses up one valid token; false when there is none. */
	static boolean pakai(ServerPlayer player, String sekte) {
		ItemStack s = cari(player, sekte);
		if (s.isEmpty()) {
			return false;
		}
		s.shrink(1);
		Msg.chat(player, Masuk.t("token.dipakai", Masuk.nama(sekte)).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** A token of {@code sekte} (null = a general token). */
	public static ItemStack token(@Nullable String sekte) {
		ItemStack stack = new ItemStack(GabungItems.TOKEN_NAIK);
		if (sekte != null && !sekte.isEmpty() && SekteApi.diplomasi().tier(sekte) > 0) {
			stack.set(GabungItems.SEKTE_TOKEN, new GabungItems.SekteToken(sekte, Masuk.nama(sekte)));
		}
		return stack;
	}

	/** Story effect {@code gabung.beri_token [sekte|umum|terdekat]}: gives a token. */
	public static void beriToken(ServerPlayer player, List<String> args) {
		String which = args.isEmpty() ? "umum" : args.getFirst();
		String sekte = switch (which) {
			case "umum" -> null;
			case "terdekat" -> SekteApi.diplomasi().nearest(player.blockPosition(), -1);
			default -> which;
		};
		Masuk.beri(player, token(sekte));
		Msg.chat(player, sekte == null ? Masuk.t("token.dapat_umum").withStyle(ChatFormatting.GOLD)
				: Masuk.t("token.dapat", Masuk.nama(sekte)).withStyle(ChatFormatting.GOLD));
	}

	// ================================================================== Surat Undangan

	/** True when the bag holds a letter of invitation from {@code sekte}. */
	public static boolean punyaUndangan(ServerPlayer player, String sekte) {
		return !undangan(player, sekte).isEmpty();
	}

	private static ItemStack undangan(ServerPlayer player, String sekte) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			GabungItems.SekteToken tok = s.is(GabungItems.SURAT_UNDANGAN) ? s.get(GabungItems.SEKTE_TOKEN) : null;
			if (tok != null && sekte.equals(tok.sekte())) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Uses the letter up at the start of the Undangan test. */
	static void pakaiUndangan(ServerPlayer player, String sekte) {
		ItemStack s = undangan(player, sekte);
		if (!s.isEmpty()) {
			s.shrink(1);
		}
	}

	/** A letter of invitation from {@code sekte}. */
	public static ItemStack suratUndangan(String sekte) {
		ItemStack stack = new ItemStack(GabungItems.SURAT_UNDANGAN);
		stack.set(GabungItems.SEKTE_TOKEN, new GabungItems.SekteToken(sekte, Masuk.nama(sekte)));
		return stack;
	}

	/** Story effect {@code gabung.undangan <sekte|terdekat>} (tournament top four, champions): a letter. */
	public static void beriUndangan(ServerPlayer player, List<String> args) {
		String sekte = args.isEmpty() || "terdekat".equals(args.getFirst()) ? SekteApi.diplomasi().nearest(player.blockPosition(), -1)
				: args.getFirst();
		if (sekte == null || SekteApi.diplomasi().tier(sekte) <= 0) {
			return;
		}
		Masuk.beri(player, suratUndangan(sekte));
		GabungApi.kenal(player, sekte);
		Msg.chat(player, Masuk.t("undangan.dapat", Masuk.nama(sekte)).withStyle(ChatFormatting.GOLD));
	}

	/**
	 * The envoy (41.5.1 "Utusan sekte"): once per 20 days a sect with standing &ge; 40 sends a letter to a player with a HIGH
	 * root or better who belongs to no sect. Lazy, from the daily upkeep.
	 */
	static void utusan(ServerPlayer player, Keanggotaan k, long hari) {
		if (!Gabung.aktif() || k.status.anggota() || k.status == dev.kultivator.sekte.gabung.api.StatusAnggota.CALON) {
			return;
		}
		if (Cultivators.get(player).rootGrade().ordinal() < RootGrade.HIGH.ordinal()) {
			return;
		}
		CompoundTag bag = k.bagian(Putusan.B);
		long last = bag.getLongOr(UTUSAN, -UTUSAN_HARI);
		if (hari - last < UTUSAN_HARI) {
			return;
		}
		String best = null;
		int bestStanding = UTUSAN_STANDING - 1;
		for (String sekte : SekteApi.diplomasi().sects()) {
			int st = SekteApi.diplomasi().standing(player.getUUID(), sekte);
			if (st > bestStanding && !punyaUndangan(player, sekte)) {
				best = sekte;
				bestStanding = st;
			}
		}
		if (best == null) {
			return;
		}
		bag.putLong(UTUSAN, hari);
		Masuk.beri(player, suratUndangan(best));
		GabungApi.kenal(player, best);
		Msg.chat(player, Masuk.t("undangan.utusan", Masuk.nama(best)).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** A roster disciple of a known sect with attitude &ge; 80 sends that sect's token, once per disciple. */
	static void sahabat(ServerPlayer player, Keanggotaan k) {
		if (!Gabung.aktif() || k.status.anggota()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag bag = k.bagian(Putusan.B);
		ListTag given = bag.getListOrEmpty(TOKEN_DARI);
		for (String sekte : SekteApi.diplomasi().sects()) {
			var e = GabungApi.entry(server, sekte);
			if (e == null || !k.kenal(e.index())) {
				continue;
			}
			for (Roster.Tokoh t : Roster.daftar(server, sekte)) {
				if (!t.aktif() || contains(given, t.id()) || Roster.sikap(server, t.id(), player.getUUID()) < SAHABAT_SIKAP) {
					continue;
				}
				given.add(StringTag.valueOf(t.id()));
				bag.put(TOKEN_DARI, given);
				Masuk.beri(player, token(sekte));
				Msg.chat(player, Masuk.t("token.sahabat", t.nama(), Masuk.nama(sekte)).withStyle(ChatFormatting.GOLD));
				GabungData.get(server).setDirty();
				return;
			}
		}
	}

	private static boolean contains(ListTag list, String id) {
		for (int i = 0; i < list.size(); i++) {
			if (id.equals(list.getStringOr(i, ""))) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== Tanda Calon

	/** Gives the Tanda Calon of {@code sekte} (replacing an older one). */
	static void beriTandaCalon(ServerPlayer player, String sekte) {
		hapusTandaCalon(player);
		ItemStack stack = new ItemStack(GabungItems.TANDA_CALON);
		stack.set(GabungItems.ANGGOTA, new GabungItems.Anggota(sekte, player.getUUID(), GabungItems.Anggota.CALON, Masuk.nama(sekte),
				SekteApi.diplomasi().color(sekte)));
		Masuk.beri(player, stack);
	}

	/** Takes every Tanda Calon out of the bag (the status ended). */
	static void hapusTandaCalon(ServerPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(GabungItems.TANDA_CALON)) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	private TokenNaik() {
	}
}
