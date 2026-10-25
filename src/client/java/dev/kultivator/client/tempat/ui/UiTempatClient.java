package dev.kultivator.client.tempat.ui;

import dev.kultivator.Kultivator;
import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.Konteks;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.tempat.ui.kit.LayarTempatDasar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatAksiPayload;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.dunia.tempat.ui.TempatLayarPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The client half of the sheets of 38/41/42 (DESIGN 38.U.1), routed into the Gulungan Utama (DESIGN 43.8, 43.12.4): a
 * registry from screen id to page factory, the receiver of {@link TempatLayarPayload}, and the action sender. The kit
 * sheets ({@code client/tempat/ui/layar/**}), the theme sheets ({@code client/tempat/ui/tema/**}) and the 41/42 sheets
 * register here with {@link #daftar}.
 *
 * <h2>Receiving</h2>
 * <ul>
 * <li>empty screen id: the server ended the session; the Di Sini context ends ("Percakapan berakhir");</li>
 * <li>a page already holding that screen id and entity (the Di Sini page, or a home page's sheet): {@link LayarTempat#perbarui}
 * (live refresh; with {@code buka} true it may also jump to the sheet's {@link LembarKunci#TAB});</li>
 * <li>otherwise, {@code buka} true: the registered {@link Pembuat} builds the page, then
 * <ul>
 * <li>the answer to a home page's read-only request ({@code OP_LIHAT}, {@code npcId -1}) goes to that page;</li>
 * <li>a world context (an NPC, a counter, a prompt) opens on its home page ({@link #rumah}, 43.12.4) with the Di Sini
 * tablet leading there, or under the Di Sini tablet when the function lives only in the world;</li>
 * <li>a read-only answer nobody waits for (an old hub card) opens its home page, or Di Sini read-only while that page is
 * not registered yet (transition);</li>
 * </ul></li>
 * <li>a refresh for a sheet nobody holds is dropped.</li>
 * </ul>
 * A screen id with no factory is logged once and ignored (the server keeps working). No screen is opened here: everything
 * goes through {@link HubNav} and {@link DiSini} (43.0.3).
 *
 * <h2>Sending</h2>
 * Pages send ops with {@link #kirim} ({@link LembarKunci#op} builds them) and call {@link #ditutup} when they leave so the
 * server ends the session.
 *
 * <p>Wired by one line {@code UiTempatClient.init()} in {@code TempatClient.init()} (38W).
 */
public final class UiTempatClient {

	/** How long a read-only request waits for its answer (ms). */
	private static final long LIHAT_TUNGGU_MS = 3000L;

	/** A sheet page: the receiver matches it by id and entity, and refreshes it in place. */
	public interface LayarTempat {
		/** Screen id ({@code LembarKunci.*.LAYAR}). */
		String layar();

		/** Entity the sheet talks to; -1 for hub/anchor sheets. */
		int npcId();

		/**
		 * A new sheet for the shown page. {@code buka} is true when the server (re)opened it (jump to
		 * {@link LembarKunci#TAB} if present), false for a live refresh (keep tab, scroll and focus).
		 */
		void perbarui(CompoundTag lembar, boolean buka);
	}

	/** Builds the page for a sheet; null refuses (the sheet is unusable). */
	@FunctionalInterface
	public interface Pembuat {
		@Nullable
		Page buat(int npcId, CompoundTag lembar);
	}

	private static final Map<String, Pembuat> PEMBUAT = new HashMap<>();
	private static final Set<String> TANPA_PEMBUAT = new HashSet<>();
	/** Read-only requests in flight: screen id → deadline. */
	private static final Map<String, Long> LIHAT = new HashMap<>();
	/** The server's session as last opened (screen id and entity), or null. */
	private static @Nullable String sesiLayar;
	private static int sesiNpc;
	private static boolean siap;

	/** Registers the receiver and the disconnect cleanup. Idempotent. */
	public static void init() {
		if (siap) {
			return;
		}
		siap = true;
		ClientPlayNetworking.registerGlobalReceiver(TempatLayarPayload.TYPE, (payload, context) -> terima(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LIHAT.clear();
			TANPA_PEMBUAT.clear();
			sesiLayar = null;
			DiSini.reset();
			LembarHub.reset();
		});
		LembarHub.init();
		dev.kultivator.client.tempat.ui.layar.SemuaLayar.daftar();
		UiHub.init();
		TeksDunia.init();
	}

	/** Registers the factory of screen {@code layar} (38UI kit sheets, 38TV-U theme sheets, 41/42 sheets). */
	public static void daftar(String layar, Pembuat pembuat) {
		if (!TempatLayar.idSah(layar) || pembuat == null) {
			throw new IllegalArgumentException("bad tempat screen id or factory: " + layar);
		}
		if (PEMBUAT.put(layar, pembuat) != null) {
			Kultivator.LOGGER.warn("Tempat screen factory {} registered twice; the last one wins", layar);
		}
	}

	public static boolean terdaftar(String layar) {
		return PEMBUAT.containsKey(layar);
	}

	/**
	 * Sends op {@code op} for screen {@code layar} / entity {@code npcId}; false when not connected to a mod server. An
	 * {@code OP_LIHAT} with {@code npcId -1} is a read-only request: its answer goes to the asking home page.
	 */
	public static boolean kirim(String layar, int npcId, String op) {
		// Keabadian › Lelang: the sheet is fed by the Abadi bridge, its ops go to the Abadi server (DESIGN 43.8.4)
		if (dev.kultivator.client.abadi.LelangJembatan.kirim(layar, npcId, op)) {
			return true;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.getConnection() == null || !TempatLayar.idSah(layar) || op == null || op.isEmpty()
				|| !ClientPlayNetworking.canSend(TempatAksiPayload.TYPE)) {
			return false;
		}
		if (npcId < 0 && LembarKunci.OP_LIHAT.equals(LembarKunci.verb(op))) {
			LIHAT.put(layar, System.currentTimeMillis() + LIHAT_TUNGGU_MS);
		}
		ClientPlayNetworking.send(new TempatAksiPayload(layar, npcId, op));
		return true;
	}

	/**
	 * Transition (old hub cards): asks for the read-only view of {@code layar}; the answer opens its home page (or Di Sini
	 * read-only until the page is registered). {@code bagian} is no longer needed (the home page knows its section).
	 */
	public static void lihatDariHub(String layar, int bagian) {
		kirim(layar, -1, LembarKunci.OP_LIHAT);
	}

	/** Called when a sheet page leaves: ends the server session of this sheet. */
	public static void ditutup(LayarTempat layar) {
		if (sesiAktif(layar.layar(), layar.npcId())) {
			sesiLayar = null;
		}
		kirim(layar.layar(), layar.npcId(), LembarKunci.OP_TUTUP);
	}

	/** True when the server's session (as last opened) is sheet {@code layar} with entity {@code npcId}. */
	public static boolean sesiAktif(String layar, int npcId) {
		return layar.equals(sesiLayar) && sesiNpc == npcId;
	}

	/**
	 * The home page of sheet {@code layar} opened on tab {@code tab} (43.12.4): the registered page mounting that sheet and
	 * tab ({@code HubPages.daftarLembar}), as a place; null = the function lives in the world only (Di Sini). New 42 sheets
	 * are not mapped in 43 (43.16.6): they go to Di Sini until their pages are registered.
	 */
	public static @Nullable Tujuan rumah(String layar, String tab) {
		HubPages.Entri e = LembarHub.rumah(layar, tab == null ? "" : tab);
		if (e == null) {
			HubPages.Rumah r = HubPages.rumah(layar, tab);
			return r == null ? null : tujuan(r, tab);
		}
		Tujuan t = Tujuan.of(e.bagian(), e.id());
		return tab == null || tab.isEmpty() ? t : t.sub(tab);
	}

	/** The place of home {@code r} for a sheet on tab {@code tab}: its sub, or the sheet's tab when the home names none. */
	private static Tujuan tujuan(HubPages.Rumah r, @Nullable String tab) {
		Tujuan t = Tujuan.of(r.bagian(), r.halaman());
		String sub = r.sub().isEmpty() ? tab : r.sub();
		return sub == null || sub.isEmpty() ? t : t.sub(sub);
	}

	private static boolean menungguLihat(String layar) {
		Long sampai = LIHAT.get(layar);
		return sampai != null && System.currentTimeMillis() <= sampai;
	}

	private static void terima(TempatLayarPayload payload) {
		if (payload.tutupLayar()) {
			sesiLayar = null;
			DiSini.selesai(Component.translatable("kultivator.gui43.konteks.berakhir"));
			return;
		}
		String layar = payload.layar();
		int npcId = payload.npcId();
		boolean dariHub = npcId < 0 && menungguLihat(layar);
		// a read-only answer belongs to the home page that asked (several pages may mount one sheet: buku_kas)
		LembarHub.Halaman peminta = dariHub ? LembarHub.menunggu(layar) : null;
		LayarTempat open = peminta != null ? peminta.isi() instanceof LayarTempat t && t.layar().equals(layar) && t.npcId() == npcId ? t : null
				: terbuka(layar, npcId, payload.buka(), dariHub);
		if (payload.buka()) {
			sesiLayar = layar;
			sesiNpc = npcId;
		}
		if (open != null) {
			if (dariHub) {
				LIHAT.remove(layar);
			}
			open.perbarui(payload.lembar(), payload.buka());
			return;
		}
		if (!payload.buka()) {
			return;
		}
		Pembuat pembuat = PEMBUAT.get(layar);
		if (pembuat == null) {
			if (TANPA_PEMBUAT.add(layar)) {
				Kultivator.LOGGER.warn("No client page registered for tempat screen {}", layar);
			}
			return;
		}
		Page page;
		try {
			page = pembuat.buat(npcId, payload.lembar());
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Tempat page {} failed to build", layar, exception);
			return;
		}
		if (page == null) {
			return;
		}
		String tab = "";
		if (page instanceof LayarTempatDasar l) {
			l.lihatSaja(dariHub);
			tab = l.tabSekarang();
		}
		if (dariHub) {
			LIHAT.remove(layar);
			if (peminta != null) {
				peminta.isiHub(page);
				return;
			}
			HubPages.Entri e = LembarHub.rumah(layar, tab);
			if (e != null) {
				LembarHub.dari(e).isiHub(page);
				HubNav.buka(Tujuan.of(e.bagian(), e.id()));
				return;
			}
			HubPages.Rumah r = HubPages.rumah(layar, tab);
			if (r != null) {
				LembarHub.dari(r).isiHub(page);
				if (r.sebelum() != null) {
					r.sebelum().run();
				}
				HubNav.buka(tujuan(r, tab));
				return;
			}
			// transition: no home page registered yet; read only under Di Sini
			DiSini.buka(konteks(layar, npcId, page), page, null, tab);
			return;
		}
		Konteks k = konteks(layar, npcId, page);
		HubPages.Entri e = LembarHub.rumah(layar, tab);
		if (e != null) {
			LembarHub.Halaman rumah = LembarHub.dari(e);
			rumah.isiKonteks(page, nama(npcId, k.judul()));
			Tujuan t = Tujuan.of(e.bagian(), e.id());
			DiSini.buka(k, rumah, t, tab);
			return;
		}
		HubPages.Rumah r = HubPages.rumah(layar, tab);
		if (r != null) {
			LembarHub.Halaman rumah = LembarHub.dari(r);
			rumah.isiKonteks(page, nama(npcId, k.judul()));
			if (r.sebelum() != null) {
				r.sebelum().run();
			}
			String sub = r.sub().isEmpty() ? tab : r.sub();
			DiSini.buka(k, rumah, Tujuan.of(r.bagian(), r.halaman()), sub);
			return;
		}
		DiSini.buka(k, page, null, tab);
	}

	/**
	 * The page holding sheet {@code layar} / {@code npcId}: the Di Sini page or a home page's sheet. An open ({@code buka})
	 * only lands on a page of the same kind (a read-only view is never turned into a live one and back).
	 */
	private static @Nullable LayarTempat terbuka(String layar, int npcId, boolean buka, boolean dariHub) {
		LayarTempat found = null;
		if (DiSini.halaman() instanceof LayarTempat t && t.layar().equals(layar) && t.npcId() == npcId) {
			found = t;
		} else {
			found = LembarHub.cari(layar, npcId);
		}
		if (found != null && buka && found instanceof LayarTempatDasar l && l.lihatSaja() != dariHub) {
			return null;
		}
		return found;
	}

	/** The context of a new sheet: title "place · speaker" (43.8: "Toko Senjata · Paman Li"). */
	private static Konteks konteks(String layar, int npcId, Page page) {
		Component tempat = page.title();
		Component judul = tempat;
		Entity entity = npcId >= 0 && Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getEntity(npcId) : null;
		if (entity != null && !entity.getName().getString().equals(tempat.getString())) {
			judul = Component.translatable("kultivator.gui43.konteks.judul", tempat, entity.getName());
		}
		int glyph = page.glyph();
		return new Konteks(layar, npcId, null, judul, glyph < 0 ? Glyphs.HALL : glyph);
	}

	/** The speaker of a context for the chip "bersama …": the entity's name, else the context title. */
	private static Component nama(int npcId, Component cadangan) {
		Entity entity = npcId >= 0 && Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getEntity(npcId) : null;
		return entity != null ? entity.getName() : cadangan;
	}

	private UiTempatClient() {
	}
}
