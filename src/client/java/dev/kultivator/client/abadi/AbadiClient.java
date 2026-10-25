package dev.kultivator.client.abadi;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.data.Rank;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The client side of Alam Abadi, the ascension, the crossing talisman and the Immortal auction (DESIGN 26.15, owned by
 * P9-langit): keeps the last {@code DuniaSync} of channel {@code CH_ABADI} (routed by {@code client.dunia.DuniaClient}) with
 * its two parts {@code alam} (Keabadian › Alam Abadi) and {@code lelang} (Keabadian › Lelang through {@link LelangJembatan}).
 * The module has no entity renderers: the black hole is drawn by {@link VortexRenderer} from {@link AbadiFx} packets.
 * <p>
 * DESIGN 43 (M16): registers the Keabadian section of the Gulungan Utama (alam_abadi, purba, batin, ciptaan, lelang; titah
 * is registered by {@code TitahClient}) and holds what those pages share: the locks the old hub cards had and a scroll of
 * wrapped lines drawn at 1.0 ({@link Baris}).
 */
public final class AbadiClient {

	private static CompoundTag data = new CompoundTag();

	/** Called once from {@code KultivatorClient}. */
	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			data = new CompoundTag();
			AbadiFx.clear();
			LelangJembatan.reset();
		});
		LelangJembatan.init();
		HubPages.daftar(HubNav.KEABADIAN, Urutan.ID_ALAM_ABADI, Urutan.ALAM_ABADI, AlamAbadiPage::new);
		HubPages.daftar(HubNav.KEABADIAN, Urutan.ID_PURBA, Urutan.PURBA, dev.kultivator.client.purba.PurbaPage::new);
		HubPages.daftar(HubNav.KEABADIAN, Urutan.ID_BATIN, Urutan.BATIN, dev.kultivator.client.batin.BatinPage::new);
		HubPages.daftar(HubNav.KEABADIAN, Urutan.ID_CIPTAAN, Urutan.CIPTAAN, dev.kultivator.client.batin.CiptaanPage::new);
		HubPages.daftar(HubNav.KEABADIAN, Urutan.ID_LELANG, Urutan.LELANG, LelangJembatan::halaman);
	}

	/** Called once from {@code KultivatorClient} after {@link #init()}: the module has no entity renderers (the vortex is particles). */
	public static void registerRenderers() {
	}

	/** The module's last {@code DuniaSync} data arrived. */
	public static void onSync(CompoundTag tag) {
		data = tag == null ? new CompoundTag() : tag.copy();
		receivedAt = net.minecraft.util.Util.getMillis();
		LelangJembatan.sinkron();
	}

	private static long receivedAt;

	/** Ticks since the last sync arrived (countdowns run on between syncs). */
	static long age() {
		return Math.max(0L, (net.minecraft.util.Util.getMillis() - receivedAt) / 50L);
	}

	/** The last synced data (never null; empty before the first sync). */
	public static CompoundTag data() {
		return data;
	}

	/** The Alam Abadi page's part. */
	static CompoundTag alam() {
		return data.getCompoundOrEmpty("alam");
	}

	/** The Lelang page's part. */
	static CompoundTag lelang() {
		return data.getCompoundOrEmpty("lelang");
	}

	// ================================================================== locks (were the Keabadian hub cards')

	/** The phase-9 ascent to Alam Abadi ({@code extra.abadi.ascended}, synced; not the phase-5 {@code CultivationData.ascended()}). */
	public static boolean naik() {
		if (ClientCultivation.data().extra().getCompoundOrEmpty("abadi").getBooleanOr("ascended", false)) {
			return true;
		}
		return alam().getBooleanOr("ascended", false);
	}

	/** Purba, Batin, Ciptaan, Lelang: open after the ascent; null = open. */
	public static @Nullable Component kunciNaik() {
		if (!ClientCultivation.received() || naik()) {
			return null;
		}
		return Component.translatable("kultivator.hub.lock.ascend");
	}

	/**
	 * Bencana Bintang (audit M5, T18): the calamities are what a True Immortal Peak goes through <i>before</i> the ascent
	 * ({@code StarCalamity.refusal} only asks {@code rank.isMax()}), so they open at the Peak, not after the ascent.
	 */
	public static @Nullable Component kunciPuncak() {
		if (!ClientCultivation.received() || naik() || ClientCultivation.data().rank().isMax()) {
			return null;
		}
		return Component.translatable("kultivator.hub.lock.realm", Rank.ofStep(Rank.MAX_STEP).displayName());
	}

	// ================================================================== a scroll of lines (43.0.5: text at 1.0, never shrunk)

	/**
	 * Wrapped lines in the kit's one scrolling list ({@code DaftarGulir}: wheel, rail, keyboard), every line a heading so
	 * nothing is selected. A page refills it each frame ({@link #mulai}, {@link #tambah}) and draws it into its box. A line
	 * may carry a small coloured square (checklists: met / not met).
	 */
	public static final class Baris {

		public static final int TINGGI = 10;

		private record Line(FormattedCharSequence text, int warna, int tanda, int indent) {
		}

		private final List<Line> lines = new ArrayList<>();
		private final DaftarGulir daftar = new DaftarGulir().judul(i -> true, TINGGI);
		private int lebar = 100;

		/** Starts a new fill for a column {@code lebar} wide. */
		public Baris mulai(int lebar) {
			lines.clear();
			this.lebar = Math.max(20, lebar);
			return this;
		}

		public Baris tambah(Component text, int warna) {
			return tambah(text, warna, 0);
		}

		/** Adds {@code text} wrapped to the column; {@code tanda} (ARGB, 0 = none) is a square before its first line. */
		public Baris tambah(Component text, int warna, int tanda) {
			Font font = Minecraft.getInstance().font;
			int indent = tanda != 0 ? 9 : 0;
			boolean first = true;
			for (FormattedCharSequence line : font.split(text, Math.max(10, lebar - 6 - indent))) {
				lines.add(new Line(line, warna, first ? tanda : 0, indent));
				first = false;
			}
			return this;
		}

		/** Half a blank line between groups. */
		public Baris jarak() {
			lines.add(new Line(FormattedCharSequence.EMPTY, 0, 0, 0));
			return this;
		}

		public int jumlah() {
			return lines.size();
		}

		/** Draws the lines into the box (the list scrolls when they do not fit). */
		public void gambar(Lukis l, String kunci, int x0, int y0, int x1, int y1) {
			if (y1 - y0 < TINGGI) {
				return;
			}
			daftar.gambar(l, kunci, x0, y0, x1, y1, lines.size(), TINGGI, (lk, i, rx0, ry0, rx1, ry1, sorot, pilih) -> {
				Line line = lines.get(i);
				if (line.tanda() != 0) {
					Brush.rect(lk.g, rx0 + 1.0F, ry0 + 2.0F, rx0 + 6.0F, ry0 + 7.0F, line.tanda());
				}
				Brush.text(lk.g, lk.font, line.text(), rx0 + line.indent(), ry0 + 1.0F, line.warna(), 1.0F, false);
			}, null, null);
		}
	}

	private AbadiClient() {
	}
}
