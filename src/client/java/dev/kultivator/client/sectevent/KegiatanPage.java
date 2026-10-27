package dev.kultivator.client.sectevent;

import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.PageKomposit;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.TabBar;
import dev.kultivator.client.sekte.gabung.GabungClient;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.gabung.GabungPayloads;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Papan;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sekte › Kegiatan (DESIGN 43.5, 43.12.2-43.12.4): one page whose subs are the sect's activities. {@code tugas} is the
 * hall's duties ({@link DutyPage}) for the hall owner and the NPC sect's daily chores (tab Tugas of {@code gabung_papan})
 * for a member, with the Sumber chip (Aulaku / Perguruanku) when the player is both; {@code misi} the hall's missions
 * ({@link MissionPage}: daily board, teams, talent scout, forbidden ground, ark, story missions, their news and reports);
 * {@code papan} the NPC sect's mission board ({@code gabung_papan} tabs Papan/Aktif/Perang/Kelompok as kind chips); and
 * {@code arena} the hall's tournaments, Void realm, tower, war and season board ({@link ArenaPage}), or for a member the
 * sect's Turnamen Murid (read-only tab Turnamen of {@code gabung_papan}; Sumber chip when both). A sub that does not
 * apply is hidden. The hall details are asked for while shown (every 4 s, as the old hall scroll did).
 */
public final class KegiatanPage extends PageKomposit {

	public static final String TUGAS = "tugas";
	public static final String MISI = "misi";
	public static final String PAPAN = "papan";
	public static final String ARENA = "arena";
	private static final String K = "kultivator.gui43.m09.";
	private static final long SEGAR_MS = 4000L;
	private static final int CHIP_H = 14;
	private static final String[] JENIS_PAPAN = {Papan.TAB_PAPAN, Papan.TAB_AKTIF, Papan.TAB_PERANG, Papan.TAB_KELOMPOK};

	private final DutyPage tugas = new DutyPage();
	private final MissionPage misi = new MissionPage();
	private final ArenaPage arena = new ArenaPage();
	private final TabBar jenis = new TabBar();
	private @Nullable LembarHub.Halaman papan;
	private @Nullable Page aktif;
	/** True while the keyboard focus is inside the sub's page (Tab ring of the child), false on the board-kind chips. */
	private boolean diAnak;
	private String sub = TUGAS;
	private String jenisPapan = Papan.TAB_PAPAN;
	/** Sumber of the Tugas sub: 0 Aulaku (the hall), 1 Perguruanku (the NPC sect). */
	private static int sumber;
	private long segar = Long.MIN_VALUE / 2;

	/** Registers the page (from {@code SectEventClient.init}). */
	static void daftar() {
		HubPages.daftar(HubNav.SEKTE, Urutan.ID_KEGIATAN, Urutan.KEGIATAN, KegiatanPage::new);
		// the NPC sect board from the world (NPC, board block) lands on Sekte › Kegiatan › Papan, Perguruanku source
		HubPages.daftarRumah(Papan.LAYAR, Set.of(), HubNav.SEKTE, Urutan.ID_KEGIATAN, PAPAN, KegiatanPage::sumberPerguruan);
	}

	/** A world opener of the NPC sect board: show the Perguruanku source. */
	static void sumberPerguruan() {
		sumber = 1;
	}

	// ================================================================== roles

	/** True once the server answered with hall details at least once (until then the page stays visible). */
	private static boolean tahu() {
		return HallClient.version() > 0L;
	}

	private static boolean aula() {
		return SectEventClient.data().getBooleanOr("hall", false);
	}

	/** A member of an NPC sect (41): the player's own badge names a sect. */
	private static boolean anggota() {
		GabungPayloads.EntriLencana lencana = GabungClient.lencanaSendiri();
		return lencana != null && !lencana.sekte().isEmpty();
	}

	/** Tugas and Arena show the NPC sect's side (the board sheet) for a member without a hall or on source Perguruanku. */
	private boolean papanUntukTugas() {
		return anggota() && (!aula() || sumber == 1);
	}

	/** The sheet page of the NPC sect board, built on first use (the read-only hub view it asks for while shown). */
	private LembarHub.Halaman papan() {
		if (papan == null) {
			papan = LembarHub.dari(new HubPages.Entri(HubNav.SEKTE, Urutan.ID_KEGIATAN, Urutan.KEGIATAN, () -> null, Papan.LAYAR, Set.of()));
		}
		return papan;
	}

	@Override
	protected @Nullable Page anak() {
		return aktif;
	}

	/** The page under the shown sub. */
	private @Nullable Page anakSub() {
		return switch (subTampil()) {
			case TUGAS -> papanUntukTugas() ? papan() : aula() ? tugas : null;
			case MISI -> misi;
			case PAPAN -> papan();
			case ARENA -> papanUntukTugas() ? papan() : arena;
			default -> null;
		};
	}

	/** The chosen sub when it applies, else the first that does. */
	private String subTampil() {
		List<Sub> subs = subs();
		for (Sub s : subs) {
			if (s.id().equals(sub)) {
				return sub;
			}
		}
		return subs.isEmpty() ? "" : subs.get(0).id();
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.sekte.kegiatan");
	}

	@Override
	public int glyph() {
		return Glyphs.JALAN;
	}

	@Override
	public String helpTopic() {
		Page a = aktif;
		return a != null ? a.helpTopic() : "aula";
	}

	@Override
	public boolean tampil() {
		return !tahu() || aula() || anggota();
	}

	@Override
	public List<Sub> subs() {
		boolean aula = aula();
		boolean anggota = anggota();
		List<Sub> subs = new ArrayList<>(4);
		if (aula || anggota) {
			subs.add(new Sub(TUGAS, Component.translatable("kultivator.gui43.sub.kegiatan.tugas")));
		}
		if (aula) {
			subs.add(new Sub(MISI, Component.translatable("kultivator.gui43.sub.kegiatan.misi")));
		}
		if (anggota) {
			subs.add(new Sub(PAPAN, Component.translatable("kultivator.gui43.sub.kegiatan.papan")));
		}
		if (aula || anggota) {
			subs.add(new Sub(ARENA, Component.translatable("kultivator.gui43.sub.kegiatan.arena")));
		}
		return subs;
	}

	@Override
	public void showSub(String id) {
		if (id != null && !id.isEmpty()) {
			sub = id;
		}
		pasangAnak();
	}

	@Override
	public @Nullable Sumber sumber() {
		String s = subTampil();
		if (!TUGAS.equals(s) && !ARENA.equals(s) || !aula() || !anggota()) {
			return null;
		}
		return new Sumber(List.of(Component.translatable(K + "sumber.aula"), Component.translatable(K + "sumber.perguruan")), () -> sumber, i -> {
			sumber = i;
			pasangAnak();
		});
	}

	// ================================================================== life

	/** Shows the page of the current sub (and Sumber); the old one is told it left. */
	private void pasangAnak() {
		Page next = anakSub();
		if (next == papan && papan != null) {
			String s = subTampil();
			papan.showSub(PAPAN.equals(s) ? jenisPapan : ARENA.equals(s) ? Papan.TAB_TURNAMEN : Papan.TAB_TUGAS);
		}
		if (next == aktif) {
			return;
		}
		Page old = aktif;
		aktif = next;
		diAnak = false;
		fokusRing.set(null);
		if (old != null) {
			old.onHide();
		}
		if (next != null) {
			if (screen != null) {
				next.pasang(screen);
			}
			tataAnak();
			next.onShow(null);
		}
	}

	private void tataAnak() {
		Page a = aktif;
		if (a == null || w <= 0 || h <= 0) {
			return;
		}
		int top = a == papan && PAPAN.equals(subTampil()) ? CHIP_H + 3 : 0;
		a.layout(x, y + top, w, Math.max(20, h - top));
	}

	@Override
	protected void build() {
		if (aktif != null && screen != null) {
			aktif.pasang(screen);
		}
		tataAnak();
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		segarkan(true);
		if (aktif == null) {
			pasangAnak();
		} else {
			aktif.onShow(null);
		}
		if (fokus != null) {
			fokus(fokus);
		}
	}

	@Override
	public void onHide() {
		if (aktif != null) {
			aktif.onHide();
		}
	}

	@Override
	public void tick() {
		segarkan(false);
		// roles may change while shown (the hall details arrive, a badge syncs): follow the sub that applies now
		if (anakSub() != aktif) {
			pasangAnak();
		}
		if (aktif != null) {
			aktif.tick();
		}
	}

	/** Asks for the hall details now and every 4 s (43.0.12: only while shown). */
	private void segarkan(boolean sekarang) {
		long now = Util.getMillis();
		if (sekarang || now - segar >= SEGAR_MS) {
			segar = now;
			HallClient.send(Halls.OP_REFRESH, "");
		}
	}

	// ================================================================== drawing

	@Override
	protected void lukis(Lukis l) {
		if (aktif == null) {
			return;
		}
		if (aktif == papan && PAPAN.equals(subTampil())) {
			List<TabBar.Tab> tabs = new ArrayList<>(JENIS_PAPAN.length);
			for (String id : JENIS_PAPAN) {
				tabs.add(TabBar.Tab.of(id, Component.translatable("kultivator.gabung.ui2.papan.tab." + id)));
			}
			jenis.setTabs(tabs);
			jenis.gambar(l, "jenis", x, y, x + w, y + CHIP_H, false, TabBar.Rupa.SUB, true, jenisPapan, id -> {
				jenisPapan = id;
				papan().showSub(id);
			});
		}
	}

	// ================================================================== input (own chips first, then the sub's page)

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (fokusRing.klik(event.x(), event.y(), event.button())) {
			return true;
		}
		return aktif != null && aktif.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean a = aktif != null && aktif.mouseReleased(event);
		return fokusRing.lepas(event.x(), event.y()) || a;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return aktif != null && aktif.mouseDragged(event, dx, dy) || fokusRing.seret(event.x(), event.y(), dx);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// the board-kind chips have the focus: ← → / Enter go to them first (43.4.2)
		if (!diAnak && fokusRing.kunci() != null && fokusRing.tombol(event)) {
			return true;
		}
		return aktif != null && aktif.keyPressed(event);
	}

	/** Tab walks the board-kind chips (Papan only), then the sub page's ring; Shift+Tab from inside it goes back to the chips. */
	@Override
	public boolean fokusTab(boolean back) {
		if (aktif == null) {
			return false;
		}
		if (!(aktif == papan && PAPAN.equals(subTampil()))) {
			return aktif.fokusTab(back);
		}
		if (diAnak) {
			if (back) {
				diAnak = false;
				fokusRing.set("jenis");
				return true;
			}
			return aktif.fokusTab(false);
		}
		if (fokusRing.kunci() == null) {
			fokusRing.set("jenis");
			return true;
		}
		fokusRing.set(null);
		diAnak = true;
		return aktif.fokusTab(back);
	}

}
