package dev.kultivator.client.screen;

import dev.kultivator.client.Keys;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.HubPages;
import dev.kultivator.client.hub.LembarHub;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.data.CultivationData;
import dev.kultivator.dunia.tempat.ui.LembarKunci.BukuKas;
import dev.kultivator.network.Actions;
import dev.kultivator.sekte.gabung.ui.GabungLembar.Kediaman;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Diri › Harta (DESIGN 43.5, G66, G74): what the player owns, one page. Its subs are flat (three levels, 43.0.4):
 * <ul>
 * <li>{@code titik} "Gua Pribadi": the Cave Abode point (DESIGN 19.3.5, moved here from the Status coin): where it is,
 * whether it works, what it gives, "Tetapkan di sini" (Action KULTIVASI {@code abode:set}) and "Hapus" ({@code abode:clear}
 * behind the light confirm seal);</li>
 * <li>{@code gua} / {@code pilih} / {@code kolam}: the sect dwelling sheet {@code gabung_kediaman} (41.6.6) mounted with its
 * three tabs as these subs (read only from the hub, as the server allows);</li>
 * <li>{@code lapak}: the business side of {@code buku_kas} (38.U.3.6), its tab Lapak only (the ledger tabs live in
 * Sekte › Kas & Toko).</li>
 * </ul>
 * The sheet subs are {@link LembarHub.Halaman} mounts shared with the sheet router, so a read-only answer lands here; the
 * sheet's own tab change (e.g. "Pindah" jumps to Pilih) moves the sub bar too. 42 adds its private holdings (rumah,
 * milikku, toko, lapak sewa, investasi, jabatan) as further subs when MEGA resumes (43.16.6).
 */
public final class HartaPage extends Page {

	private static final String K = "kultivator.gui43.m01.harta.";
	private static final String ABODE = "kultivator.goals.status.";
	public static final String SUB_TITIK = "titik";
	private static final List<String> SUBS = List.of(SUB_TITIK, Kediaman.TAB_GUA, Kediaman.TAB_PILIH, Kediaman.TAB_KOLAM, BukuKas.TAB_LAPAK);

	private final LembarHub.Halaman kediaman = mount(Kediaman.LAYAR, Set.of());
	private final LembarHub.Halaman usaha = mount(BukuKas.LAYAR, Set.of(BukuKas.TAB_LAPAK));
	private String sub = SUB_TITIK;
	private boolean shown;

	public HartaPage() {
	}

	/** The mount of sheet {@code layar} with tabs {@code tab} (the same page object the sheet router fills). */
	private static LembarHub.Halaman mount(String layar, Set<String> tab) {
		return LembarHub.dari(new HubPages.Entri(HubNav.DIRI, Urutan.ID_HARTA, Urutan.HARTA, () -> null, layar, tab));
	}

	/** The sheet page of the sub shown, or null on the abode sub. */
	private @Nullable Page sheet() {
		if (SUB_TITIK.equals(sub)) {
			return null;
		}
		return BukuKas.TAB_LAPAK.equals(sub) ? usaha : kediaman;
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.diri.harta");
	}

	@Override
	public int glyph() {
		return Glyphs.TREASURE;
	}

	@Override
	public String helpTopic() {
		Page s = sheet();
		return s == null ? "meditation" : s.helpTopic();
	}

	@Override
	public List<Sub> subs() {
		return SUBS.stream().map(id -> new Sub(id, Component.translatable("kultivator.gui43.sub.harta." + id))).toList();
	}

	@Override
	public void showSub(String id) {
		String next = SUBS.contains(id) ? id : SUB_TITIK;
		Page before = sheet();
		sub = next;
		Page after = sheet();
		if (before != after && shown) {
			if (before != null) {
				before.onHide();
			}
			if (after != null) {
				after.onShow(null);
			}
		}
		if (after != null) {
			after.showSub(next);
		}
		if (w > 0 && h > 0) {
			layout(x, y, w, h);
		}
	}

	@Override
	public boolean lebar() {
		Page s = sheet();
		return s != null && s.lebar();
	}

	@Override
	public long uang() {
		Page s = sheet();
		return s != null ? s.uang() : -1L;
	}

	@Override
	public boolean rinci() {
		Page s = sheet();
		return s != null && s.rinci();
	}

	@Override
	public Palette.Gaya gaya() {
		Page s = sheet();
		return s != null ? s.gaya() : Palette.Gaya.KERTAS;
	}

	@Override
	public boolean angka(int n) {
		Page s = sheet();
		return s != null ? s.angka(n) : super.angka(n);
	}

	// ================================================================== life

	@Override
	protected void build() {
		Page s = sheet();
		if (s != null) {
			if (screen != null) {
				s.pasang(screen);
			}
			s.layout(x, y, w, h);
			return;
		}
		buildTitik();
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		shown = true;
		Page s = sheet();
		if (s != null) {
			s.onShow(fokus);
		}
	}

	@Override
	public void onHide() {
		shown = false;
		Page s = sheet();
		if (s != null) {
			s.onHide();
		}
	}

	@Override
	public void tick() {
		Page s = sheet();
		if (s == null) {
			return;
		}
		s.tick();
		// The sheet changed its own tab (a button inside it): follow with the sub bar.
		if (s == kediaman && kediaman.isi() != null && screen != null) {
			String tab = kediaman.isi().tabSekarang();
			if (!tab.equals(sub) && Kediaman.TABS.contains(tab)) {
				screen.tuju(Tujuan.of(HubNav.DIRI, Urutan.ID_HARTA).sub(tab));
			}
		}
	}

	@Override
	public void onData() {
		Page s = sheet();
		if (s != null) {
			s.onData();
		}
	}

	@Override
	public boolean needsRebuild() {
		Page s = sheet();
		return s != null ? s.needsRebuild() : builtAbode != (abode(data()) != null);
	}

	// ================================================================== the abode sub (Gua Pribadi)

	private boolean builtAbode;

	private void buildTitik() {
		builtAbode = abode(data()) != null;
		int bh = 16;
		int by = y + h - bh;
		int bw = Math.min(150, (w - 6) / 2);
		TalismanButton set = new TalismanButton(x + w / 2 - bw - 3, by, bw, bh, () -> Component.translatable(K + (builtAbode ? "pindah" : "tetapkan")));
		set.enabledWhen(Keys::connected);
		set.alasanWhen(() -> Component.translatable("kultivator.gui43.m02.luring"));
		set.tooltip(() -> List.of(Component.translatable(K + (builtAbode ? "pindah" : "tetapkan")), Component.translatable(K + "tetapkan.tip")));
		set.onPress(() -> send(Actions.KULTIVASI, 0, "abode:set"));
		add(set);
		TalismanButton clear = new TalismanButton(x + w / 2 + 3, by, bw, bh, () -> Component.translatable(K + "hapus"));
		clear.enabledWhen(() -> Keys.connected() && abode(data()) != null);
		clear.alasanWhen(() -> !Keys.connected() ? Component.translatable("kultivator.gui43.m02.luring")
				: abode(data()) == null ? Component.translatable(ABODE + "abode.none") : null);
		clear.tooltip(() -> List.of(Component.translatable(K + "hapus"), Component.translatable(K + "hapus.tip")));
		clear.onPress(() -> konfirmasi(SegelKonfirmasi.Tingkat.RINGAN, Component.translatable(K + "hapus.yakin"),
				() -> send(Actions.KULTIVASI, 0, "abode:clear")));
		add(clear);
	}

	/** The Cave Abode saved in {@code extra.kultivasi.abode {dim, x, y, z}} (DESIGN 19.9), or null when none is set. */
	static @Nullable CompoundTag abode(CultivationData data) {
		CompoundTag tag = data.extra().getCompoundOrEmpty("kultivasi").getCompoundOrEmpty("abode");
		return tag.getStringOr("dim", "").isEmpty() ? null : tag;
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		Page s = sheet();
		if (s != null) {
			s.render(g, mouseX, mouseY, seconds);
		}
	}

	/** The abode card: a seal, where it is, whether it works, what it gives, how to set it. */
	@Override
	protected void lukis(Lukis l) {
		if (sheet() != null) {
			return;
		}
		CultivationData data = data();
		CompoundTag abode = abode(data);
		int x0 = x + 2;
		int x1 = x + w - 2;
		int y1 = y + h - 20;
		l.sumur(x0, y, x1, y1);
		int ty = y + 4;
		int sealX = x0 + 6;
		Brush.disc(l.g, sealX + 8, ty + 8, 9.0F, abode == null ? Palette.PAPER_DEEP : Palette.GOLD_DARK);
		Glyphs.tab(l.g, Glyphs.TREASURE, sealX + 8, ty + 8, 12.0F, abode == null ? Palette.LOCKED : Palette.JADE_PALE);
		int tx = sealX + 22;
		int tw = x1 - tx - 6;
		l.teks(Brush.bold(Component.translatable(ABODE + "abode")), tx, ty, tw, Palette.SEAL_DARK);
		ty += 11;
		if (abode == null) {
			l.teks(Component.translatable(ABODE + "abode.none"), tx, ty, tw, Palette.INK_SOFT);
			ty += 14;
			l.bungkus(Component.translatable(K + "cara"), x0 + 6, ty, x1 - x0 - 12, Palette.INK_FAINT, y1 - ty - 2);
			return;
		}
		String dim = abode.getStringOr("dim", "");
		Component where = switch (dim) {
			case "minecraft:overworld" -> Component.translatable(ABODE + "dim.overworld");
			case "minecraft:the_nether" -> Component.translatable(ABODE + "dim.nether");
			case "minecraft:the_end" -> Component.translatable(ABODE + "dim.end");
			default -> Component.literal(dim);
		};
		l.teks(Component.translatable(ABODE + "abode.at", abode.getIntOr("x", 0), abode.getIntOr("y", 0), abode.getIntOr("z", 0), where), tx, ty, tw,
				Palette.JADE_DARK);
		ty += 14;
		int state = data.sessionExtra().getIntOr("abode", 0);
		if (state != 0) {
			ty += Math.round(l.bungkus(Component.translatable(state > 0 ? ABODE + "abode.active" : ABODE + "abode.unprotected"), x0 + 6, ty,
					x1 - x0 - 12, state > 0 ? Palette.JADE_DARK : Palette.WARN, y1 - ty - 2)) + 3;
		}
		l.bungkus(Component.translatable(ABODE + "abode.bonus"), x0 + 6, ty, x1 - x0 - 12, Palette.INK_SOFT, y1 - ty - 2);
	}

	// ================================================================== input (the sheet subs get it all)

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Page s = sheet();
		return s != null ? s.mouseClicked(event, doubleClick) : super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		Page s = sheet();
		return s != null ? s.mouseReleased(event) : super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		Page s = sheet();
		return s != null ? s.mouseDragged(event, dx, dy) : super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Page s = sheet();
		return s != null ? s.mouseScrolled(mouseX, mouseY, scrollX, scrollY) : super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		Page s = sheet();
		return s != null ? s.keyPressed(event) : super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		Page s = sheet();
		return s != null ? s.keyReleased(event) : super.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		Page s = sheet();
		return s != null ? s.charTyped(event) : super.charTyped(event);
	}

	@Override
	public boolean mauTombol() {
		Page s = sheet();
		return s != null ? s.mauTombol() : super.mauTombol();
	}

	@Override
	public boolean modal() {
		Page s = sheet();
		return s != null ? s.modal() : super.modal();
	}

	@Override
	public boolean mundur() {
		Page s = sheet();
		return s != null ? s.mundur() : super.mundur();
	}

	@Override
	public boolean fokusTab(boolean back) {
		Page s = sheet();
		return s != null ? s.fokusTab(back) : super.fokusTab(back);
	}
}
