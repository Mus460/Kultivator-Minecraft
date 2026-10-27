package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Konteks;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.tempat.ui.layar.LayarLelang;
import dev.kultivator.dunia.kota.KotaNet;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Lelang;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The K auctioneer on the one auction GUI (DESIGN 43.11 G20, test 43.18 no. 6; P6): clicking a module K Juru Lelang shows
 * {@link LayarLelang}, the same sheet as the town Balai Lelang of 38 and Keabadian › Lelang, under the Di Sini tablet,
 * instead of the Lelang sub of the town page. The server is unchanged (43.0.2):
 * <ul>
 * <li>{@link KotaNet.Screen} of a Juru Lelang → sheet: {@link #lembar()} turns the town view's {@code lelang} part into the
 * {@code lelang} sheet contract (the lot on the stage, every lot of the session as the board rows, the bid steps, the
 * player's Nilai Batu, the consignment tab with the item in the main hand and this session's consignments; the Hasil tab is
 * hidden: K pays at the session end);</li>
 * <li>sheet ops → {@code KotaNet.Action}: the ops come back through {@link #kirim} (called first by the bridge hook
 * {@code LelangJembatan.kirim}, itself the one-line hook in {@code UiTempatClient.kirim}) and become the old K ops
 * {@code tawar:lot:price:step}, {@code titip} and {@code lihat}.</li>
 * </ul>
 * The sheet talks to the auctioneer's real entity id (so the sheet base asks for a fresh view when shown instead of ending
 * the context); no 38 session ever exists for a K entity, and the hook only claims ops while this bridged page lives. The
 * other town subs of a Juru Lelang only ever said where to find that service, so nothing is lost. The context ends when
 * the auctioneer is gone or farther than {@link #JAUH} blocks (as the town page).
 */
public final class KotaLelang {

	private static final double JAUH = 24.0D;
	private static final int[] LANGKAH = {5, 10, 25};

	private static @Nullable LayarLelang halaman;
	private static int npc = -1;

	/** Called from {@code KotaClient.init}: the reach watch (a sheet page cannot override {@code tick}). */
	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick(minecraft));
	}

	static void reset() {
		halaman = null;
		npc = -1;
	}

	/**
	 * A town view arrived: true when it belongs to a Juru Lelang and was handled here (opened, refreshed, or a stream for a
	 * page that is not shown, dropped); false for every other NPC (the town page of {@link KotaJembatan}).
	 */
	static boolean terima(KotaNet.Screen payload) {
		if (payload.screen() != KotaNet.SCREEN_KOTA) {
			return false;
		}
		CompoundTag data = payload.data() == null ? new CompoundTag() : payload.data();
		if (NpcRole.byOrdinal(data.getIntOr("role", 0)) != NpcRole.JURU_LELANG) {
			return false;
		}
		LayarLelang shown = halaman != null && DiSini.halaman() == halaman && npc == payload.npcId() ? halaman : null;
		if (!payload.open() && shown == null) {
			// the auction streams to everyone near the stage; only the shown page follows it
			return true;
		}
		KotaClient.simpan(payload);
		if (shown != null) {
			shown.perbarui(lembar(), payload.open());
			if (payload.open()) {
				HubNav.buka(Tujuan.bagian(HubNav.DI_SINI));
			}
			return true;
		}
		npc = payload.npcId();
		LayarLelang page = new LayarLelang(npc, lembar());
		halaman = page;
		DiSini.buka(new Konteks(KotaJembatan.KONTEKS, npc, judul(page), Glyphs.HALL), page, null, page.tabSekarang());
		return true;
	}

	private static Component judul(LayarLelang page) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(npc);
		return entity == null ? page.title() : Component.translatable("kultivator.gui43.konteks.judul", page.title(), entity.getName());
	}

	private static void tick(Minecraft minecraft) {
		LayarLelang page = halaman;
		if (page == null) {
			return;
		}
		if (DiSini.halaman() != page) {
			// the Gulungan closed or another context replaced this one
			reset();
			return;
		}
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(npc);
		if (entity == null || !entity.isAlive() || minecraft.player == null || minecraft.player.distanceToSqr(entity) > JAUH * JAUH) {
			DiSini.selesai(Component.translatable("kultivator.gui43.konteks.berakhir"));
			reset();
		}
	}

	/**
	 * The bridge hook: true when the op belongs to the bridged K auction sheet (and is handled here), false for every other
	 * sheet. Never sends a {@code TempatAksiPayload}.
	 */
	public static boolean kirim(String layar, int npcId, String op) {
		if (halaman == null || npcId != npc || !Lelang.LAYAR.equals(layar) || op == null) {
			return false;
		}
		String[] parts = LembarKunci.urai(op);
		switch (parts[0]) {
			case Lelang.OP_TAWAR -> {
				CompoundTag lelang = KotaClient.tab("lelang");
				int current = lelang.getIntOr("current", -1);
				int step = LembarKunci.argInt(parts, 1, LANGKAH[0]);
				KotaClient.send("tawar:" + current + ":" + lot(lelang, current).getLongOr("price", 0L) + ":" + step);
			}
			case Lelang.OP_TITIP -> KotaClient.send("titip");
			case LembarKunci.OP_LIHAT -> {
				// only within the action reach, else the server prints "too far" to an auction watcher (as the town page)
				Minecraft minecraft = Minecraft.getInstance();
				Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(npc);
				if (entity != null && minecraft.player != null && minecraft.player.distanceToSqr(entity) <= KotaNet.REACH * KotaNet.REACH) {
					KotaClient.send("lihat");
				}
			}
			default -> {
				// tutup, tab: K keeps no session; tarik / ambil_hasil are never offered (no such K ops)
			}
		}
		return true;
	}

	private static CompoundTag lot(CompoundTag lelang, int index) {
		ListTag lots = lelang.getListOrEmpty("lots");
		return index >= 0 && index < lots.size() && lots.get(index) instanceof CompoundTag lot ? lot : new CompoundTag();
	}

	// ================================================================== town view -> sheet

	/** The {@code lelang} sheet of the last town view (the times are relative ticks, fresh: built when the view arrives). */
	static CompoundTag lembar() {
		CompoundTag semua = KotaClient.data();
		CompoundTag lelang = KotaClient.tab("lelang");
		CompoundTag t = new CompoundTag();
		LembarKunci.teks(t, LembarKunci.JUDUL, Component.translatable("kultivator.kota.lelang.judul", KotaJembatan.teks(semua, "town")));
		ListTag sembunyi = new ListTag();
		sembunyi.add(StringTag.valueOf(Lelang.TAB_HASIL));
		t.put(LembarKunci.TAB_SEMBUNYI, sembunyi);
		t.putString(Lelang.PROFIL, Lelang.PROFIL_KOTA);
		t.putIntArray(Lelang.LANGKAH, LANGKAH.clone());
		t.putLong(LembarKunci.UANG, semua.getLongOr("nb", 0L));
		LembarKunci.teks(t, LembarKunci.SUBJUDUL, Component.translatable("kultivator.kota.lelang.di_panggung"));
		boolean active = lelang.getBooleanOr("active", false);
		ListTag lots = lelang.getListOrEmpty("lots");
		int current = lelang.getIntOr("current", -1);
		CompoundTag onStage = lot(lelang, current);
		if (!lelang.getBooleanOr("enabled", true)) {
			t.putString(LembarKunci.STATUS, Lelang.S_SELESAI);
			LembarKunci.teks(t, Lelang.JADWAL, Component.translatable("kultivator.kota.lelang.mati"));
		} else if (!active || onStage.isEmpty()) {
			long next = Math.max(0L, lelang.getLongOr("next", 0L));
			boolean done = lelang.getBooleanOr("done_today", false);
			t.putString(LembarKunci.STATUS, done ? Lelang.S_SELESAI : Lelang.S_BELUM);
			if (!done) {
				t.putLong(LembarKunci.SISA_TICK, next);
			}
			LembarKunci.teks(t, Lelang.JADWAL, done ? Component.translatable("kultivator.kota.lelang.sudah_hari_ini")
					: Component.translatable("kultivator.kota.lelang.berikut", KotaJembatan.jam(next)));
		} else {
			int state = onStage.getIntOr("state", 0);
			long present = onStage.getLongOr("present", 0L);
			long sell = onStage.getLongOr("sell", -1L);
			boolean open = state == 1 && present <= 0L;
			t.putString(LembarKunci.STATUS, open ? Lelang.S_BERJALAN : Lelang.S_JEDA);
			// the lot's ring: until it opens, then "going, going" after the last raise (no end is known before a bid)
			long left = !open ? present : Math.max(0L, sell);
			t.putLong(LembarKunci.SISA_TICK, left);
			CompoundTag card = new CompoundTag();
			card.putInt(LembarKunci.NO, current + 1);
			card.putInt(Lelang.TOTAL, lots.size());
			LembarKunci.teks(card, LembarKunci.NAMA, KotaJembatan.teks(onStage, "name"));
			Minecraft minecraft = Minecraft.getInstance();
			ItemStack stack = KotaJembatan.ikon(onStage.getStringOr("item", ""));
			if (minecraft.level != null && !stack.isEmpty()) {
				LembarKunci.barang(card, stack.copy(), minecraft.level.registryAccess());
			}
			card.putLong(Lelang.DASAR, onStage.getLongOr("base", 0L));
			card.putLong(Lelang.KINI, onStage.getLongOr("price", 0L));
			Component leader = KotaJembatan.teks(onStage, "leader");
			if (!leader.getString().isEmpty()) {
				LembarKunci.teks(card, Lelang.PEMIMPIN, leader);
			}
			card.putBoolean(LembarKunci.ANDA, onStage.getBooleanOr("mine", false));
			card.putBoolean(Lelang.TERJUAL, state == 2);
			card.putLong(LembarKunci.SISA_TICK, left);
			t.put(Lelang.LOT, card);
			// the lowest valid next bid (the first bid is the base price whatever the step)
			t.putLong(Lelang.TAWAR_MIN, onStage.getLongOr("offer" + LANGKAH[0], onStage.getLongOr("base", 0L)));
		}
		t.put(Lelang.TAWARAN, papan(lots, current));
		t.put(Lelang.TITIP, titip(lelang, active));
		return t;
	}

	/** Every lot of the session as the board rows: number, name, state or leader, price; the lot on the stage flashes. */
	private static ListTag papan(ListTag lots, int current) {
		ListTag rows = new ListTag();
		for (int i = 0; i < lots.size() && i < LembarKunci.DAFTAR_MAKS; i++) {
			if (!(lots.get(i) instanceof CompoundTag entry)) {
				continue;
			}
			Component leader = KotaJembatan.teks(entry, "leader");
			int state = entry.getIntOr("state", 0);
			Component line = switch (state) {
				case 1 -> leader.getString().isEmpty() ? Component.translatable("kultivator.kota.lelang.dibuka") : leader;
				case 2 -> Component.translatable("kultivator.kota.lelang.terjual", leader);
				case 3 -> Component.translatable("kultivator.kota.lelang.tak_laku");
				default -> Component.translatable("kultivator.kota.lelang.menunggu");
			};
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.gui43.m16.lelang.baris", i + 1, KotaJembatan.teks(entry, "name"), line));
			row.putLong(LembarKunci.NB, entry.getLongOr("price", 0L));
			row.putBoolean(LembarKunci.ANDA, entry.getBooleanOr("mine", false));
			row.putBoolean(LembarKunci.BARU, i == current && state == 1);
			rows.add(row);
		}
		return rows;
	}

	/** The Titip tab: the item in the main hand and this session's consignments (one fixed value per item, server side). */
	private static CompoundTag titip(CompoundTag lelang, boolean active) {
		CompoundTag titip = new CompoundTag();
		titip.putInt(LembarKunci.MAKS, lelang.getIntOr("consign_max", 2));
		titip.putBoolean(LembarKunci.BISA, active);
		titip.putBoolean(LayarLelang.TETAP, true);
		if (!active) {
			LembarKunci.teks(titip, LembarKunci.ALASAN, Component.translatable("kultivator.kota.lelang.tidak_ada"));
		}
		Minecraft minecraft = Minecraft.getInstance();
		ItemStack hand = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
		if (!hand.isEmpty() && minecraft.level != null) {
			// the server checks what it takes (whitelist, rings with contents) and says why in the action bar
			CompoundTag tangan = new CompoundTag();
			LembarKunci.barang(tangan, hand.copyWithCount(1), minecraft.level.registryAccess());
			titip.put(LembarKunci.TANGAN, tangan);
		} else if (active) {
			LembarKunci.teks(titip, LembarKunci.ALASAN, Component.translatable("kultivator.kota.lelang.tip_titip", lelang.getIntOr("consign_max", 2)));
		}
		ListTag daftar = new ListTag();
		List<CompoundTag> consign = TownPages.daftar(lelang.getListOrEmpty("consign"));
		for (int i = 0; i < consign.size(); i++) {
			CompoundTag entry = consign.get(i);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, String.valueOf(i));
			LembarKunci.teks(row, LembarKunci.NAMA, KotaJembatan.teks(entry, "name"));
			row.putLong(Lelang.DASAR, entry.getLongOr("value", 1L));
			row.putString(LembarKunci.STATUS, Lelang.T_DILELANG);
			daftar.add(row);
		}
		titip.put(LembarKunci.DAFTAR, daftar);
		return titip;
	}

	private KotaLelang() {
	}
}
