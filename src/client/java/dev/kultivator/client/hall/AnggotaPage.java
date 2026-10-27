package dev.kultivator.client.hall;

import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.PageKomposit;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.sekte.gabung.GabungClient;
import dev.kultivator.client.sekte.tetua.BalaiTetuaScreen;
import dev.kultivator.client.sekte.tetua.TetuaClient;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Sekte › Anggota (DESIGN 43.5, 43.16.2 M06): one page for the people of the sect, seen from the player's own hall
 * (Aulaku) or from the NPC sect the player joined (Perguruanku, DESIGN 41); the Sumber chip switches when both exist.
 * <ul>
 * <li>{@code calon} (Aulaku only): the candidates at the gate ({@link CalonPage}).</li>
 * <li>{@code pangkat}: the rank pagoda of the hall ({@link JenjangPage}) / the rank tree of the sect ({@code gabung_pangkat}).</li>
 * <li>{@code tetua}: the offices and the morning report of the Balai Tetua / the council (Majelis tab of {@code gabung_politik}).</li>
 * <li>{@code penerus}: the heir, the ceremony and the lineage / the succession (Suksesi tab of {@code gabung_politik}).</li>
 * </ul>
 * The Perguruanku subs are the 41 sheets mounted read through {@code OP_LIHAT} (41 hub views may act, the server checks).
 * The hall details are asked for while the page is shown and every 4 s, like the old Aula scroll (43.0.12).
 */
public final class AnggotaPage extends PageKomposit {

	public static final String CALON = "calon";
	public static final String PANGKAT = "pangkat";
	public static final String TETUA = "tetua";
	public static final String PENERUS = "penerus";
	private static final String K = "kultivator.gui43.m06.";
	private static final long SEGAR_MS = 4000L;

	/** 0 Aulaku, 1 Perguruanku (session memory, like the last sub). */
	private static int sumber;

	private final CalonPage calon = new CalonPage();
	private final JenjangPage jenjang = new JenjangPage();
	private final BalaiTetuaScreen balai = new BalaiTetuaScreen(false);
	private final BalaiTetuaScreen penerus = new BalaiTetuaScreen(true);
	private @Nullable Page pangkat41;
	private @Nullable Page majelis41;
	private @Nullable Page suksesi41;
	private String sub = PANGKAT;
	private @Nullable Page isi;
	private long segarAt = Long.MIN_VALUE / 2;
	private long versiAula = -1L;
	private boolean tampilSekarang;

	// ================================================================== sources

	private static boolean aula() {
		return HallClient.info().getBooleanOr("has", false);
	}

	private static boolean aulaSendiri() {
		return aula() && !HallClient.info().getBooleanOr("readonly", false);
	}

	private static boolean perguruan() {
		return GabungClient.lencanaSendiri() != null;
	}

	/** 0 Aulaku, 1 Perguruanku: the chip when both, else the one that exists. */
	private static int mode() {
		boolean a = aula();
		boolean p = perguruan();
		return a && p ? sumber : a ? 0 : p ? 1 : 0;
	}

	/** A world opener of the own hall (the Balai Tetua): show the Aulaku source. */
	public static void sumberAula() {
		sumber = 0;
	}

	/** A world opener of an NPC sect sheet (rank tree, council, succession): show the Perguruanku source. */
	public static void sumberPerguruan() {
		sumber = 1;
	}

	private void mintaAula() {
		segarAt = Util.getMillis();
		HallClient.send(Halls.OP_REFRESH, "");
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.sekte.anggota");
	}

	@Override
	public int glyph() {
		return Glyphs.HALL;
	}

	@Override
	public String helpTopic() {
		return isi != null ? isi.helpTopic() : "jenjang";
	}

	/** Hidden without a hall and without a sect (43.5); shown while the hall details are still unknown, and asks for them. */
	@Override
	public boolean tampil() {
		if (aula() || perguruan()) {
			return true;
		}
		// only while Sekte is shown (the host asks the shown section): a hall founded since the last answer appears again
		if (Util.getMillis() - segarAt > (HallClient.version() == 0L ? 5000L : 15000L)) {
			mintaAula();
		}
		return HallClient.version() == 0L;
	}

	@Override
	public List<Sub> subs() {
		List<Sub> subs = new ArrayList<>(4);
		if (mode() == 0) {
			if (aula()) {
				subs.add(new Sub(CALON, Component.translatable("kultivator.gui43.sub.anggota.calon")));
			}
			subs.add(new Sub(PANGKAT, Component.translatable("kultivator.gui43.sub.anggota.pangkat")));
			if (aulaSendiri()) {
				Component kunci = TetuaClient.tersedia() ? null : Component.translatable(K + "tetua.mati");
				subs.add(new Sub(TETUA, Component.translatable("kultivator.gui43.sub.anggota.tetua"), kunci));
				subs.add(new Sub(PENERUS, Component.translatable("kultivator.gui43.sub.anggota.penerus"), kunci));
			}
		} else {
			subs.add(new Sub(PANGKAT, Component.translatable("kultivator.gui43.sub.anggota.pangkat")));
			subs.add(new Sub(TETUA, Component.translatable("kultivator.gui43.sub.anggota.tetua")));
			subs.add(new Sub(PENERUS, Component.translatable("kultivator.gui43.sub.anggota.penerus")));
		}
		return subs;
	}

	private boolean subAda(String id) {
		for (Sub s : subs()) {
			if (s.id().equals(id) && s.kunci() == null) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void showSub(String id) {
		sub = id == null || id.isEmpty() ? PANGKAT : id;
		if (CALON.equals(sub)) {
			sumber = 0; // the gate candidates are the own hall's only (Halls.PAGE_CALON from the inscription)
		}
		pasangIsi();
	}

	@Override
	public @Nullable Sumber sumber() {
		if (!aula() || !perguruan()) {
			return null;
		}
		return new Sumber(List.of(Component.translatable(K + "sumber.aula"), Component.translatable(K + "sumber.perguruan")), () -> sumber, i -> {
			sumber = i;
			String tetap = CALON.equals(sub) && i == 1 ? PANGKAT : sub;
			tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_ANGGOTA).sub(tetap));
			pasangIsi();
		});
	}

	// ================================================================== the inner page

	@Override
	protected @Nullable Page anak() {
		return isi;
	}

	/** The page of the current sub and source (the 41 sheets are mounted on first use). */
	private @Nullable Page halamanSub() {
		boolean aulaku = mode() == 0;
		return switch (sub) {
			case CALON -> aulaku ? calon : null;
			case TETUA -> aulaku ? balai : (majelis41 = lembar(majelis41, GabungLembar.Politik.LAYAR, Set.of(GabungLembar.Politik.TAB_MAJELIS)));
			case PENERUS -> aulaku ? penerus : (suksesi41 = lembar(suksesi41, GabungLembar.Politik.LAYAR, Set.of(GabungLembar.Politik.TAB_SUKSESI)));
			default -> aulaku ? jenjang : (pangkat41 = lembar(pangkat41, GabungLembar.PohonPangkat.LAYAR, Set.of()));
		};
	}

	/**
	 * The mounted page of a 41 sheet with only {@code tab}: the same stable page {@code LembarHub} keeps per (sheet, tabs),
	 * which receives the {@code OP_LIHAT} answers. Not registered in {@link HubPages}, so no second coin appears.
	 */
	private static Page lembar(@Nullable Page ada, String layar, Set<String> tab) {
		if (ada != null) {
			return ada;
		}
		return LembarHub.dari(new HubPages.Entri(HubNav.SEKTE, Urutan.ID_ANGGOTA, Urutan.ANGGOTA, () -> null, layar, tab));
	}

	/** Switches the inner page to the current sub (hide the old, show the new). */
	private void pasangIsi() {
		Page next = halamanSub();
		if (next == isi) {
			return;
		}
		if (isi != null && tampilSekarang) {
			isi.onHide();
		}
		isi = next;
		if (isi == null) {
			return;
		}
		if (screen != null) {
			isi.pasang(screen);
		}
		if (w > 0 && h > 0) {
			isi.layout(x, y, w, h);
		}
		if (tampilSekarang) {
			isi.onShow(null);
		}
	}

	// ================================================================== life

	@Override
	protected void build() {
		if (isi == null) {
			pasangIsi();
			return;
		}
		if (screen != null) {
			isi.pasang(screen);
		}
		isi.layout(x, y, w, h);
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		tampilSekarang = true;
		mintaAula();
		if (isi == null) {
			pasangIsi();
		} else {
			isi.onShow(fokus);
		}
	}

	@Override
	public void onHide() {
		tampilSekarang = false;
		if (isi != null) {
			isi.onHide();
		}
	}

	@Override
	public void tick() {
		if (mode() == 0 && Util.getMillis() - segarAt >= SEGAR_MS) {
			mintaAula();
		}
		if (tampilSekarang && !subAda(sub)) {
			// the sources changed while shown (hall gone, sect left): back to the rank sub, which always exists
			sub = PANGKAT;
			tuju(Tujuan.of(HubNav.SEKTE, Urutan.ID_ANGGOTA).sub(PANGKAT));
		}
		if (halamanSub() != isi) {
			pasangIsi();
		}
		long v = HallClient.version();
		if (v != versiAula) {
			versiAula = v;
			if (isi instanceof CalonPage || isi instanceof JenjangPage) {
				if (isi.needsRebuild()) {
					isi.layout(x, y, w, h);
				} else {
					isi.onData();
				}
			}
		}
		if (isi != null) {
			isi.tick();
		}
	}

	// ================================================================== drawing

	@Override
	protected void lukis(Lukis l) {
		if (isi == null) {
			l.tengah(Component.translatable(K + "kosong"), x + w / 2.0F, y + h / 2.0F - 4.0F, w - 8, l.gaya.teksSamar);
		}
	}
}
