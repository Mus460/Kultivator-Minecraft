package dev.kultivator.client.abadi;

import dev.kultivator.Kultivator;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.tempat.ui.layar.LayarLelang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Lelang;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The client bridge of the Immortal auction (DESIGN 43.8.4, G20; M16): Keabadian › Lelang is the one auction GUI of the mod,
 * {@link LayarLelang} (the same sheet as the town Balai Lelang and the K auctioneer), fed from the {@code CH_ABADI} sync
 * instead of a 38 sheet. The server is unchanged (43.0.2):
 * <ul>
 * <li>sync → sheet: {@link #lembar()} turns {@code AbadiClient.lelang()} into the {@code lelang} sheet contract
 * ({@code LembarKunci.Lelang}): the lot on the block, every lot of the session as the board rows (number, name, state or
 * leader, price), the bid steps, the player's Batu Asal as the money in hand, the consignment tab (the item in the main hand
 * and the consigned items); the Hasil tab is hidden (the Immortal auction pays at the session end);</li>
 * <li>ops → actions: the sheet's ops come back through {@link #kirim} (the one-line hook in {@code UiTempatClient.kirim})
 * and become {@code Actions.ABADI} {@code "bid:lot:price:step"} / {@code "titip"}; {@code lihat} asks for a fresh sync.</li>
 * </ul>
 * The bridged sheet talks to entity {@link #NPC}, which no server session ever has, so an op that reached the server
 * without the hook would be ignored there. While the page is shown the bridge asks for the sync when it appears and every
 * 60 s after (the server pushes auction changes to viewers of the last 2 minutes).
 */
public final class LelangJembatan {

	/** The entity id of the bridged sheet (no entity; never a server session). */
	public static final int NPC = -16;
	private static final String K = "kultivator.gui43.m16.lelang.";
	private static final int SEGAR_TICK = 1200;
	/** The items the Immortal auction takes on consignment (the server's {@code Auction.SELLABLE}, package-private there). */
	private static final TagKey<Item> SELLABLE = TagKey.create(Registries.ITEM, Kultivator.id("auction_sellable"));

	private static @Nullable LayarLelang halaman;
	private static boolean tampil;
	private static int sejak;

	/** Called from {@code AbadiClient.init}: the "shown" watch (the sheet page cannot override {@code onShow}). */
	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick(minecraft));
	}

	/** The {@code HubPages} supplier of Keabadian › Lelang: a new {@link LayarLelang} for this Gulungan. */
	static Page halaman() {
		LayarLelang page = new LayarLelang(NPC, lembar());
		halaman = page;
		return page;
	}

	/** A {@code CH_ABADI} sync arrived: the shown sheet refreshes in place (tab, scroll and focus kept). */
	static void sinkron() {
		if (halaman != null) {
			halaman.perbarui(lembar(), false);
		}
	}

	static void reset() {
		halaman = null;
		tampil = false;
	}

	private static void tick(Minecraft minecraft) {
		boolean now = halaman != null && minecraft.gui.screen() instanceof CultivationScreen hub && Urutan.ID_LELANG.equals(hub.tempat().halaman());
		if (now && (!tampil || ++sejak >= SEGAR_TICK)) {
			minta();
		}
		tampil = now;
	}

	private static void minta() {
		sejak = 0;
		Keys.send(new Payloads.Action(Actions.ABADI, 0, "sync"));
	}

	/**
	 * The hook of {@code UiTempatClient.kirim}: true when the op belongs to the bridged sheet (and is handled here), false for
	 * every other sheet. Never sends a {@code TempatAksiPayload}.
	 */
	public static boolean kirim(String layar, int npcId, String op) {
		// the K auctioneer's bridged sheet shares this hook (one line in UiTempatClient for both bridges)
		if (dev.kultivator.client.dunia.kota.KotaLelang.kirim(layar, npcId, op)) {
			return true;
		}
		if (npcId != NPC || !Lelang.LAYAR.equals(layar) || op == null) {
			return false;
		}
		String[] parts = LembarKunci.urai(op);
		switch (parts[0]) {
			case Lelang.OP_TAWAR -> {
				CompoundTag page = AbadiClient.lelang();
				int current = page.getIntOr("current", -1);
				CompoundTag lot = lot(page, current);
				int step = LembarKunci.argInt(parts, 1, 5);
				Keys.send(new Payloads.Action(Actions.ABADI, 0, "bid:" + current + ":" + lot.getIntOr("price", 0) + ":" + step));
			}
			case Lelang.OP_TITIP -> Keys.send(new Payloads.Action(Actions.ABADI, 0, "titip"));
			case LembarKunci.OP_LIHAT -> minta();
			default -> {
				// tutup, tab: nothing to tell the server; tarik / ambil_hasil are never offered (no such Immortal ops)
			}
		}
		return true;
	}

	private static CompoundTag lot(CompoundTag page, int index) {
		ListTag lots = page.getListOrEmpty("lots");
		return index >= 0 && index < lots.size() && lots.get(index) instanceof CompoundTag lot ? lot : new CompoundTag();
	}

	// ================================================================== sync -> sheet

	/** The {@code lelang} sheet of the last {@code CH_ABADI} sync. */
	static CompoundTag lembar() {
		CompoundTag page = AbadiClient.lelang();
		CompoundTag t = new CompoundTag();
		LembarKunci.teks(t, LembarKunci.JUDUL, Component.translatable("kultivator.spine9.tab.lelang"));
		ListTag sembunyi = new ListTag();
		sembunyi.add(StringTag.valueOf(Lelang.TAB_HASIL));
		t.put(LembarKunci.TAB_SEMBUNYI, sembunyi);
		t.putString(Lelang.PROFIL, Lelang.PROFIL_KOTA);
		t.putString(LayarLelang.MATA_UANG, LayarLelang.BATU_ASAL);
		if (page.isEmpty()) {
			t.putString(LembarKunci.STATUS, Lelang.S_BELUM);
			LembarKunci.teks(t, Lelang.JADWAL, Component.translatable("kultivator.abadi.page.loading"));
			return t;
		}
		long age = AbadiClient.age();
		boolean enabled = page.getBooleanOr("enabled", true);
		boolean active = page.getBooleanOr("active", false);
		boolean inRealm = page.getBooleanOr("in_realm", false);
		boolean banned = page.getBooleanOr("banned", false);
		int stones = page.getIntOr("stones", 0);
		t.putLong(LembarKunci.UANG, stones);
		// the strip under the header: the currency, the bag and why a bid would not count
		Component strip = Component.translatable(K + "batu", stones);
		if (!inRealm) {
			strip = Component.translatable("kultivator.abadi.page.sep", strip, Component.translatable("kultivator.abadi.lelang.page.only_realm"));
		}
		if (banned) {
			strip = Component.translatable("kultivator.abadi.page.sep", strip, Component.translatable("kultivator.abadi.lelang.page.banned"));
		}
		LembarKunci.teks(t, LembarKunci.SUBJUDUL, strip);

		ListTag lots = page.getListOrEmpty("lots");
		int current = page.getIntOr("current", -1);
		CompoundTag onBlock = lot(page, current);
		if (!enabled) {
			t.putString(LembarKunci.STATUS, Lelang.S_SELESAI);
			LembarKunci.teks(t, Lelang.JADWAL, Component.translatable("kultivator.abadi.lelang.page.disabled"));
		} else if (!active) {
			long next = Math.max(0L, page.getLongOr("next", 0L) - age);
			t.putString(LembarKunci.STATUS, Lelang.S_BELUM);
			t.putLong(LembarKunci.SISA_TICK, next);
			LembarKunci.teks(t, Lelang.JADWAL, Component.translatable("kultivator.abadi.lelang.page.next", Brush.waktu(next)));
		} else {
			boolean open = onBlock.getIntOr("state", 0) == 1;
			t.putString(LembarKunci.STATUS, open ? Lelang.S_BERJALAN : Lelang.S_JEDA);
			long ends = Math.max(0L, page.getLongOr("ends", 0L) - age);
			t.putLong(LembarKunci.SISA_TICK, ends);
			if (!onBlock.isEmpty()) {
				CompoundTag lot = new CompoundTag();
				lot.putInt(LembarKunci.NO, current + 1);
				lot.putInt(Lelang.TOTAL, lots.size());
				LembarKunci.teks(lot, LembarKunci.NAMA, onBlock.read("name", ComponentSerialization.CODEC).orElse(Component.empty()));
				lot.putLong(Lelang.DASAR, onBlock.getIntOr("base", 0));
				lot.putLong(Lelang.KINI, onBlock.getIntOr("price", 0));
				onBlock.read("leader", ComponentSerialization.CODEC).ifPresent(leader -> LembarKunci.teks(lot, Lelang.PEMIMPIN, leader));
				lot.putBoolean(LembarKunci.ANDA, onBlock.getBooleanOr("mine", false));
				lot.putBoolean(Lelang.TERJUAL, onBlock.getIntOr("state", 0) == 2);
				long sell = onBlock.getLongOr("sell", -1L);
				long present = onBlock.getLongOr("present", 0L);
				// the lot's ring: "going, going" after the last raise, else the presentation, else the session's end
				lot.putLong(LembarKunci.SISA_TICK, Math.max(0L, (sell >= 0L ? sell : present > 0L ? present : page.getLongOr("ends", 0L)) - age));
				t.put(Lelang.LOT, lot);
				if (sell < 0L) {
					// no bid yet: the first bid is the base price whatever the step (Auction.bid)
					t.putLong(Lelang.TAWAR_MIN, onBlock.getIntOr("base", 0));
				}
			}
		}
		// every lot of the session as the board rows: number, name, state or leader, price; the lot on the block flashes
		ListTag rows = new ListTag();
		for (int i = 0; i < lots.size() && i < LembarKunci.DAFTAR_MAKS; i++) {
			if (!(lots.get(i) instanceof CompoundTag entry)) {
				continue;
			}
			Component name = entry.read("name", ComponentSerialization.CODEC).orElse(Component.empty());
			Component leader = entry.read("leader", ComponentSerialization.CODEC).orElse(Component.empty());
			Component state = switch (entry.getIntOr("state", 0)) {
				case 1 -> leader;
				case 2 -> Component.translatable("kultivator.abadi.lelang.page.sold", leader);
				case 3 -> Component.translatable("kultivator.abadi.lelang.state.unsold");
				default -> Component.translatable("kultivator.abadi.lelang.state.waiting");
			};
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable(K + "baris", i + 1, name, state));
			row.putLong(LembarKunci.NB, entry.getIntOr("price", 0));
			row.putBoolean(LembarKunci.ANDA, entry.getBooleanOr("mine", false));
			row.putBoolean("baru", i == current && entry.getIntOr("state", 0) == 1);
			rows.add(row);
		}
		t.put(Lelang.TAWARAN, rows);
		t.put(Lelang.TITIP, titip(page, active, inRealm));
		return t;
	}

	/** The Titip tab: the item in the main hand (when the auction takes it) and the items consigned this session. */
	private static CompoundTag titip(CompoundTag page, boolean active, boolean inRealm) {
		CompoundTag titip = new CompoundTag();
		titip.putInt(LembarKunci.MAKS, page.getIntOr("consign_max", 2));
		boolean bisa = active && inRealm;
		titip.putBoolean(LembarKunci.BISA, bisa);
		if (!bisa) {
			LembarKunci.teks(titip, LembarKunci.ALASAN, Component.translatable(active ? "kultivator.abadi.lelang.page.only_realm" : K + "tutup"));
		}
		// one fixed value per item (Auction.VALUES, server side): the three reserve buttons all consign at that value
		titip.putBoolean("tetap", true);
		Minecraft minecraft = Minecraft.getInstance();
		ItemStack hand = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
		if (!hand.isEmpty() && hand.is(SELLABLE) && minecraft.level != null) {
			CompoundTag tangan = new CompoundTag();
			LembarKunci.barang(tangan, hand.copyWithCount(1), minecraft.level.registryAccess());
			titip.put(LembarKunci.TANGAN, tangan);
		} else if (bisa) {
			LembarKunci.teks(titip, LembarKunci.ALASAN, Component.translatable("kultivator.abadi.lelang.consign_tip", page.getIntOr("consign_max", 2)));
		}
		ListTag daftar = new ListTag();
		int i = 0;
		for (Tag tag : page.getListOrEmpty("consign")) {
			if (!(tag instanceof CompoundTag entry)) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, String.valueOf(i++));
			LembarKunci.teks(row, LembarKunci.NAMA, entry.read("name", ComponentSerialization.CODEC).orElse(Component.empty()));
			row.putLong(Lelang.DASAR, entry.getIntOr("value", 0));
			row.putString(LembarKunci.STATUS, Lelang.T_DILELANG);
			daftar.add(row);
		}
		titip.put(LembarKunci.DAFTAR, daftar);
		return titip;
	}

	private LelangJembatan() {
	}
}
