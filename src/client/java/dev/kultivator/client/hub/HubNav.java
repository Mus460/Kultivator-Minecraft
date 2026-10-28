package dev.kultivator.client.hub;

import dev.kultivator.client.ClientDisconnect;
import dev.kultivator.client.Keys;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.hall.Halls;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * The navigation of the Gulungan Utama (DESIGN 43.4): {@link CultivationScreen} is the one screen of the mod, with eight
 * sections in play order and the Di Sini tablet while a world context lives. This class opens it on a place
 * ({@link #buka(Tujuan)}), routes every server request to open a menu there ({@link #rute}), maps the section keys
 * ({@link #bukaKunci}), remembers the last page per section and the last sub per page for the session (plain statics, no
 * file), and brings the Gulungan back after the few vanilla screens opened from it ({@link #kembaliSetelah}: the ring
 * container, chat): Esc there only marks the press; on the next client tick, if that screen closed (no screen left) and
 * the mark is a few ticks old at most, the Gulungan reopens where it was. V or {@code info} there closes everything in one
 * press (not in chat, where they type).
 */
public final class HubNav {

	public static final int DIRI = 0;
	public static final int TEKNIK = 1;
	public static final int MURID = 2;
	public static final int SEKTE = 3;
	public static final int DUNIA = 4;
	public static final int KEABADIAN = 5;
	public static final int PANDUAN = 6;
	public static final int PENGATURAN = 7;
	/** The number of hub sections (number keys 1-8). */
	public static final int SECTIONS = 8;
	/** The Di Sini tablet (key 9), present only while a world context lives (43.8). Not a {@link HubPages} section. */
	public static final int DI_SINI = 8;

	/** Pengaturan groups (33.5), the sub-coins of section {@link #PENGATURAN}. */
	public static final int GROUP_TAMPILAN = 0;
	public static final int GROUP_FILM = 1;
	public static final int GROUP_SUARA = 2;
	public static final int GROUP_HUD = 3;
	public static final int GROUP_KONTROL = 4;

	private static final long EXPECT_MILLIS = 3000L;
	private static final int ESC_TICKS = 2;

	private static int lastSection = DIRI;
	/** The last page id per section and the last sub id per page, for the session (43.4.3; plain statics, no file). */
	private static final String[] LAST_PAGE = {"", "", "", "", "", "", "", ""};
	private static final java.util.Map<String, String> LAST_SUB = new java.util.HashMap<>();

	private static long ticks;
	private static long escTick = Long.MIN_VALUE / 2;

	/** Registers the screen and tick hooks (called once from {@code KultivatorClient}). */
	public static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> afterInit(screen));
		ClientTickEvents.END_CLIENT_TICK.register(HubNav::tick);
		ClientDisconnect.register((handler, client) -> client.execute(HubNav::reset));
	}

	// ================================================================== Gulungan Utama (DESIGN 43.4.3)

	private static @Nullable Tujuan antrean;
	private static @Nullable Class<? extends Screen> kembaliKelas;
	private static @Nullable Screen kembaliLayar;
	private static @Nullable Tujuan kembaliKe;
	private static long kembaliAt;

	/**
	 * Opens the Gulungan Utama on {@code tujuan}, or moves the open one there. Nothing opens while a camera shot runs or
	 * without a player. The one way client code opens a mod menu after 43 (43.0.3).
	 */
	public static void buka(Tujuan tujuan) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || tujuan == null) {
			return;
		}
		if (minecraft.gui.screen() instanceof CultivationScreen hub) {
			hub.tuju(tujuan);
			return;
		}
		if (CinematicDirector.active()) {
			return;
		}
		QuickWheel.close();
		minecraft.gui.setScreen(CultivationScreen.buka(tujuan));
	}

	/** Opens {@code tujuan} now when no screen is open, else as soon as the open screen closes (replaces the old "pending" flags). */
	public static void antre(Tujuan tujuan) {
		Minecraft minecraft = Minecraft.getInstance();
		Screen current = minecraft.gui.screen();
		if (current == null || current instanceof CultivationScreen) {
			antrean = null;
			buka(tujuan);
		} else {
			antrean = tujuan;
		}
	}

	/**
	 * The next screen of type {@code layar} (a container screen, {@code ChatScreen}) is opened from the Gulungan: when it
	 * closes with Esc the Gulungan reopens where it was (43.8.5).
	 */
	public static void kembaliSetelah(Class<? extends Screen> layar) {
		Minecraft minecraft = Minecraft.getInstance();
		kembaliKelas = layar;
		kembaliLayar = null;
		kembaliKe = minecraft.gui.screen() instanceof CultivationScreen hub ? hub.tempat() : Tujuan.bagian(lastSection);
		kembaliAt = Util.getMillis();
	}

	/** True while the Gulungan gave way to a screen that brings it back ({@link #kembaliSetelah}: ring, chat). */
	public static boolean menungguKembali() {
		return kembaliKelas != null;
	}

	/** V / info: closes the Gulungan and any screen over it in one press. */
	public static void tutupSemua() {
		closeAll();
	}

	/**
	 * Shows {@code halaman} under the Di Sini tablet (43.8) with title {@code judul} and {@code glyph}, opening the Gulungan
	 * there when it is closed. One context at a time: a new one replaces the old. Called by 43S-C's {@code DiSini}.
	 */
	public static void bukaKonteks(dev.kultivator.client.screen.Page halaman, net.minecraft.network.chat.Component judul, int glyph) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || halaman == null) {
			return;
		}
		CultivationScreen.konteks(halaman, judul, glyph);
		buka(Tujuan.bagian(DI_SINI));
	}

	/** The context ended (server closed the session, out of reach): the Di Sini tablet goes, with footer {@code pesan}. */
	public static void tutupKonteks(net.minecraft.network.chat.@Nullable Component pesan) {
		CultivationScreen.tutupKonteks(pesan);
	}

	// ================================================================== the hub

	/** The section the hub shows when opened without one (the last one this session). */
	public static int lastSection() {
		return lastSection;
	}

	/** The last page id shown in {@code bagian} this session ("" = none yet). */
	public static String halamanTerakhir(int bagian) {
		return bagian >= 0 && bagian < SECTIONS ? LAST_PAGE[bagian] : "";
	}

	/** Called by the Gulungan whenever it shows page {@code halaman} of section {@code bagian}. */
	public static void ingat(int bagian, String halaman) {
		if (bagian >= 0 && bagian < SECTIONS) {
			lastSection = bagian;
			LAST_PAGE[bagian] = halaman == null ? "" : halaman;
		}
	}

	/** The last sub id of page {@code halaman} this session ("" = the page's first open sub). */
	public static String subTerakhir(String halaman) {
		return LAST_SUB.getOrDefault(halaman, "");
	}

	/** Called by the Gulungan whenever page {@code halaman} shows sub {@code sub}. */
	public static void ingatSub(String halaman, String sub) {
		if (halaman != null && !halaman.isEmpty()) {
			LAST_SUB.put(halaman, sub == null ? "" : sub);
		}
	}

	/** Opens Pengaturan on {@code group} ({@code GROUP_*}, the Pengaturan pages). */
	public static void openSettings(int group) {
		buka(Tujuan.of(PENGATURAN, dev.kultivator.client.screen.SettingsPage.idGrup(group)));
	}

	/** Opens Panduan › Ensiklopedia on {@code topic} (the "?" seal of every page). */
	public static void help(String topic) {
		buka(Tujuan.of(PANDUAN, Urutan.ID_ENSIKLOPEDIA));
		if (Minecraft.getInstance().gui.screen() instanceof CultivationScreen hub) {
			hub.showHelp(topic == null || topic.isEmpty() ? "jalan" : topic);
		}
	}

	// ================================================================== server-opened places (43.4.4)

	/**
	 * The client side of {@code Payloads.OpenScreen} (43.4.4): every server request to open a menu becomes a {@link Tujuan}.
	 * The Dao choice waits for a free screen
	 * ({@link #antre}, never covering chat or an inventory). {@code KEHANCURAN_CONFIRM} stays with its module (43.4.4: a
	 * heavy {@code SegelKonfirmasi}, M02).
	 */
	public static void rute(int layar, int arg) {
		switch (layar) {
			case Payloads.OpenScreen.INFO -> buka(CultivationScreen.tujuanDari(arg));
			case Payloads.OpenScreen.CHOOSE_DAO -> antre(Tujuan.of(DIRI, Urutan.ID_AKAR_DAO));
			case Payloads.OpenScreen.DISCIPLE -> {
				net.minecraft.nbt.CompoundTag f = new net.minecraft.nbt.CompoundTag();
				f.putInt("murid", arg);
				buka(Tujuan.of(MURID, Urutan.ID_MURID).fokus(f));
			}
			case Payloads.OpenScreen.HALL -> buka(tujuanAula(arg));
			case Payloads.OpenScreen.KEHANCURAN_CONFIRM -> dev.kultivator.client.kehancuran.KehancuranClient.openConfirm(arg);
			case Payloads.OpenScreen.SECT_TEMPLATE -> {
				net.minecraft.nbt.CompoundTag f = new net.minecraft.nbt.CompoundTag();
				f.putInt("templat", arg);
				buka(Tujuan.of(SEKTE, Urutan.ID_BANGUN).sub("templat").fokus(f));
			}
			case Payloads.OpenScreen.CODEX -> {
				Tujuan t = Tujuan.of(TEKNIK, Urutan.ID_KODEKS);
				if (arg >= 0) {
					net.minecraft.nbt.CompoundTag f = new net.minecraft.nbt.CompoundTag();
					f.putInt("spec", arg);
					t = t.fokus(f);
				}
				buka(t);
			}
			case Payloads.OpenScreen.DUNIA -> buka(tujuanDunia(arg));
			case Payloads.OpenScreen.ABADI -> buka(tujuanAbadi(arg));
			case Payloads.OpenScreen.TITAH -> dev.kultivator.client.pusakalangit.TitahClient.openScreen(arg);
			default -> {
			}
		}
	}

	/**
	 * {@code Halls.PAGE_*} as places (43.12.3). The client-only indices of the old hall page list, verified against
	 * {@code HallScreen}: 5 Tugas, 6 Misi, 7 Arena, 8 Catatan, 9 Segel Merah.
	 */
	public static Tujuan tujuanAula(int page) {
		return switch (page) {
			case Halls.PAGE_BANGUN -> Tujuan.of(SEKTE, Urutan.ID_BANGUN);
			case Halls.PAGE_CALON -> Tujuan.of(SEKTE, Urutan.ID_ANGGOTA).sub("calon");
			case Halls.PAGE_JENJANG -> {
				dev.kultivator.client.hall.AnggotaPage.sumberAula(); // the hall inscription is always the own hall
				yield Tujuan.of(SEKTE, Urutan.ID_ANGGOTA).sub("pangkat");
			}
			case Halls.PAGE_TREASURY -> Tujuan.of(SEKTE, Urutan.ID_KAS).sub("kas");
			case 5 -> Tujuan.of(SEKTE, Urutan.ID_KEGIATAN).sub("tugas");
			case 6 -> Tujuan.of(SEKTE, Urutan.ID_KEGIATAN).sub("misi");
			case 7 -> Tujuan.of(SEKTE, Urutan.ID_KEGIATAN).sub("arena");
			case 8 -> Tujuan.of(SEKTE, Urutan.ID_IKHTISAR).sub("catatan");
			case 9 -> Tujuan.of(TEKNIK, Urutan.ID_KEHANCURAN).sub("murid");
			case Halls.PAGE_FORMASI -> Tujuan.of(SEKTE, Urutan.ID_FORMASI);
			case Halls.PAGE_PERTAHANAN -> Tujuan.of(SEKTE, Urutan.ID_WILAYAH).sub("pertahanan");
			case Halls.PAGE_DESA -> Tujuan.of(SEKTE, Urutan.ID_WILAYAH).sub("desa_pakta");
			default -> Tujuan.of(SEKTE, Urutan.ID_IKHTISAR).sub("aula");
		};
	}

	/** {@code OpenScreen.DUNIA} tabs as places (43.4.4); 4 is {@code DUNIA_DUEL} (43.14 S1, added by M14). */
	public static Tujuan tujuanDunia(int tab) {
		return switch (tab) {
			case Payloads.OpenScreen.DUNIA_PUSAKA -> Tujuan.of(DIRI, Urutan.ID_PUSAKA);
			case Payloads.OpenScreen.DUNIA_MUSUH -> Tujuan.of(DUNIA, Urutan.ID_MUSUH);
			case Payloads.OpenScreen.DUNIA_DESA -> Tujuan.of(SEKTE, Urutan.ID_WILAYAH).sub("desa_pakta");
			case Payloads.OpenScreen.DUNIA_DUEL -> Tujuan.of(DUNIA, Urutan.ID_DUEL);
			default -> Tujuan.of(DUNIA, Urutan.ID_ALAM);
		};
	}

	/** {@code OpenScreen.ABADI} tabs as places (43.4.4). */
	public static Tujuan tujuanAbadi(int tab) {
		return switch (tab) {
			case Payloads.OpenScreen.ABADI_PURBA -> Tujuan.of(KEABADIAN, Urutan.ID_PURBA).sub("binatang");
			case Payloads.OpenScreen.ABADI_BENCANA -> Tujuan.of(KEABADIAN, Urutan.ID_PURBA).sub("bencana");
			case Payloads.OpenScreen.ABADI_BATIN -> Tujuan.of(KEABADIAN, Urutan.ID_BATIN);
			case Payloads.OpenScreen.ABADI_CIPTAAN -> Tujuan.of(KEABADIAN, Urutan.ID_CIPTAAN);
			case Payloads.OpenScreen.ABADI_LELANG -> Tujuan.of(KEABADIAN, Urutan.ID_LELANG);
			default -> Tujuan.of(KEABADIAN, Urutan.ID_ALAM_ABADI);
		};
	}

	// ================================================================== section keys (43.4.2, 43.10.3)

	/**
	 * The place a section key opens: K Teknik › Kodeks, M Murid › Murid, J Panduan › Kitab, N Dunia › Peta, and the unbound
	 * {@code dunia} / {@code abadi} keys their sections. Null when {@code event} is none of them.
	 */
	public static @Nullable Tujuan tombolBagian(KeyEvent event) {
		KeyMapping key = kunciBagian(event);
		return key == null ? null : tujuanKunci(key);
	}

	/** Opens what section key {@code event} names (inside or outside the Gulungan). True when it was a section key. */
	public static boolean bukaTombol(KeyEvent event) {
		KeyMapping key = kunciBagian(event);
		if (key == null) {
			return false;
		}
		bukaKunci(key);
		return true;
	}

	/** The section key {@code event} matches, or null. */
	private static @Nullable KeyMapping kunciBagian(KeyEvent event) {
		for (KeyMapping key : new KeyMapping[]{Keys.CODEX, Keys.MURID, Keys.GUIDE, dev.kultivator.client.dunia.peta.PetaClient.PETA, Keys.DUNIA,
				Keys.ABADI}) {
			if (key.matches(event)) {
				return key;
			}
		}
		return null;
	}

	/** The place section key {@code key} opens (43.10.3), or null for a key that is not a section key. */
	public static @Nullable Tujuan tujuanKunci(KeyMapping key) {
		if (key == Keys.CODEX) {
			return Tujuan.of(TEKNIK, Urutan.ID_KODEKS);
		}
		if (key == Keys.MURID) {
			return Tujuan.of(MURID, Urutan.ID_MURID);
		}
		if (key == Keys.GUIDE) {
			return Tujuan.of(PANDUAN, Urutan.ID_KITAB);
		}
		if (key == dev.kultivator.client.dunia.peta.PetaClient.PETA) {
			return Tujuan.of(DUNIA, Urutan.ID_PETA);
		}
		if (key == Keys.DUNIA) {
			return Tujuan.bagian(DUNIA);
		}
		if (key == Keys.ABADI) {
			return Tujuan.bagian(KEABADIAN);
		}
		return null;
	}

	/**
	 * Opens the place of section key {@code key} (from the world or inside the Gulungan). M also asks the server, which picks the
	 * disciple looked at (else the nearest) and answers with {@code OpenScreen.DISCIPLE} (a focus on Murid › Murid).
	 */
	public static void bukaKunci(KeyMapping key) {
		Tujuan t = tujuanKunci(key);
		if (t == null) {
			return;
		}
		// 43.10.3: M always opens Murid › Murid (R17: a far disciple is a locked sub, not silence); the server's answer
		// (OpenScreen.DISCIPLE) then only focuses the disciple looked at or the nearest.
		buka(t);
		if (key == Keys.MURID) {
			Keys.send(new Payloads.Action(Actions.DISCIPLE_OPEN, -1, ""));
		}
	}

	// ================================================================== vanilla screens opened from the Gulungan (43.8.5)

	private static void afterInit(Screen screen) {
		if (kembaliKelas != null && (screen == kembaliLayar || kembaliLayar == null && kembaliKelas.isInstance(screen))) {
			kembaliLayar = screen;
			// Fabric recreates a screen's events on every init, so this runs after each one.
			ScreenKeyboardEvents.allowKeyPress(screen).register(HubNav::kembaliKey);
		}
	}

	/** Esc in a screen opened by {@link #kembaliSetelah} marks the press; V/info closes it for good (not in chat: it types). */
	private static boolean kembaliKey(Screen screen, KeyEvent event) {
		if (screen != kembaliLayar) {
			return true;
		}
		if (event.isEscape()) {
			escTick = ticks;
		} else if (!(screen instanceof net.minecraft.client.gui.screens.ChatScreen) && (Keys.QUICK.matches(event) || Keys.INFO.matches(event))) {
			closeAll();
			return false;
		}
		return true;
	}

	/** Closes the current screen and everything under it (a container screen also tells the server). */
	public static void closeAll() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen current = minecraft.gui.screen();
		kembaliKelas = null;
		kembaliLayar = null;
		QuickWheel.suppress();
		if (current instanceof AbstractContainerScreen<?> && minecraft.player != null) {
			minecraft.player.closeContainer();
		} else {
			minecraft.gui.setScreen(null);
		}
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		Screen shown = minecraft.gui.screen();
		if (kembaliKelas != null) {
			if (kembaliLayar != null && shown != kembaliLayar) {
				Tujuan back = kembaliKe;
				boolean esc = ticks - escTick <= ESC_TICKS + 1;
				kembaliKelas = null;
				kembaliLayar = null;
				if (shown == null && back != null && esc && minecraft.player != null) {
					buka(back);
				}
			} else if (kembaliLayar == null && Util.getMillis() - kembaliAt > EXPECT_MILLIS) {
				kembaliKelas = null;
			}
		}
		if (antrean != null && minecraft.player != null && (shown == null || shown instanceof CultivationScreen) && !CinematicDirector.active()) {
			Tujuan t = antrean;
			antrean = null;
			buka(t);
		}
	}

	private static void reset() {
		antrean = null;
		kembaliKelas = null;
		kembaliLayar = null;
		CultivationScreen.tutupKonteks(null);
	}

	private HubNav() {
	}
}
