package dev.kultivator.client.abadi;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.dunia.DuniaClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.ui.GulirTeks;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.data.Rank;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;


/**
 * The client side of Alam Abadi, the ascension, the crossing talisman and the Immortal auction (DESIGN 26.15, owned by
 * P9-langit): keeps the last {@code DuniaSync} of channel {@code CH_ABADI} (routed by {@code client.dunia.DuniaClient}) with
 * its two parts {@code alam} (Keabadian › Alam Abadi) and {@code lelang} (Keabadian › Lelang through {@link LelangJembatan}).
 * The module has no entity renderers: the black hole is drawn by {@link VortexRenderer} from {@link AbadiFx} packets.
 * <p>
 * DESIGN 43 (M16): registers the Keabadian section of the Gulungan Utama (alam_abadi, purba, batin, ciptaan, lelang; titah
 * is registered by {@code TitahClient}) and holds what those pages share: the locks the old hub cards had and the start of their
 * scroll of wrapped lines ({@link #mulai}, kit {@code GulirTeks}).
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

	/**
	 * Starts a page's per-frame fill of {@code gulir} for a column {@code lebar} wide; when the server refused the last
	 * {@code channel} sync as too big the first line says so (the page shows the previous data; audit ulang B-2).
	 */
	public static GulirTeks mulai(GulirTeks gulir, int lebar, int channel) {
		gulir.mulai(lebar);
		if (DuniaClient.tooBig(channel)) {
			gulir.tambah(Component.translatable("kultivator.sect_fix_feat.too_big"), Palette.SEAL_DARK);
		}
		return gulir;
	}

	private AbadiClient() {
	}
}
