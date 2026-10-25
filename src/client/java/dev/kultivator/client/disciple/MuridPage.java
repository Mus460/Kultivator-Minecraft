package dev.kultivator.client.disciple;

import dev.kultivator.client.Keys;
import dev.kultivator.client.disciple.path.PathTab;
import dev.kultivator.client.disciple.power.TalentTab;
import dev.kultivator.client.disciple.power.TreasureTab;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.hub.Urutan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanKosong;
import dev.kultivator.client.screen.ui.KeadaanTerkunci;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.sekte.hubungan.HubunganTab;
import dev.kultivator.client.sekte.kepribadian.HatiTab;
import dev.kultivator.client.sekte.mandiri.BukuHarianScreen;
import dev.kultivator.client.sekte.mandiri.JalanHidupTab;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.sekte.hubungan.HubunganPayloads;
import dev.kultivator.sekte.kepribadian.KepribadianPayloads;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Murid › Murid (DESIGN 43.5, 43.12.1; was the disciple scroll {@code DiscipleScreen}): a picker "‹ name ›" over the
 * chosen disciple and ten subs: Status (with the orders as its bottom row), Teknik, Pil &amp; Dao, Kekuatan (Bakat over
 * Pusaka), Jalan (its four parts stacked), Hati, Hubungan, Jalan Hidup (Ikhtisar over Kebijakan), Buku Harian (wide paper)
 * and Dompet. ←/→ pick the previous / next own disciple nearby (R24), Shift+←/→ the sub. A disciple that walks out of
 * reach (more than {@code Disciples.VIEW_RANGE} blocks, or no longer loaded) never closes the page: every sub but Buku
 * Harian locks with the reason until it comes back (R17). Focus tags: {@code {"murid": entityId}} and
 * {@code {"jiwa": soul id}} (the diary of a disciple that is not loaded). Live values are asked for every 5 s while shown.
 */
public final class MuridPage extends Page {

	/** Lang prefix of this page (fragment {@code gui43_m03}). */
	public static final String K = "kultivator.gui43.m03.";
	private static final String SUB = "kultivator.gui43.sub.murid.";
	/** The subs in order (ids of {@code Tujuan.sub}). */
	public static final String[] SUBS = {"status", "teknik", "pil_dao", "kekuatan", "jalan", "hati", "hubungan", "jalan_hidup", "buku_harian",
			"dompet"};
	public static final String SUB_BUKU = "buku_harian";
	private static final int REFRESH_TICKS = 100;
	private static final int PEMILIH_H = 14;
	private static final long TUNDA_MILLIS = 3000L;
	private static final double RANGE_SQ = Disciples.VIEW_RANGE * Disciples.VIEW_RANGE;

	/** The sub the next disciple focus opens on (the board's "Jalan Hidup" button), and when it was asked. */
	private static String subTunda = "";
	private static long subTundaAt;

	private int entityId = -1;
	/** The soul of a disciple shown only through its diary (not loaded nearby). */
	private String soulBuku = "";
	private CompoundTag info = new CompoundTag();
	private DiscipleView view = new DiscipleView(info);
	private long seenVersion = -1L;
	private String sub = SUBS[0];
	private final Map<String, MuridSub> anak = new HashMap<>();
	private final Map<MuridSub, String> laid = new HashMap<>();
	private int ticks;
	/** How many own disciples are within reach (picker arrows; counted every half second, not every frame). */
	private int jumlahDekat;
	private long kunciSejak;
	private @Nullable Component kunciTerakhir;

	public MuridPage() {
	}

	/** The next disciple focus within 3 s opens on sub {@code id} (one of {@link #SUBS}). */
	public static void subBerikutnya(String id) {
		subTunda = id == null ? "" : id;
		subTundaAt = Util.getMillis();
	}

	/** The place of disciple {@code entityId} (sub {@code sub}, "" = the last one). */
	public static Tujuan tujuan(int entityId, String sub) {
		CompoundTag f = new CompoundTag();
		f.putInt("murid", entityId);
		return Tujuan.of(HubNav.MURID, Urutan.ID_MURID).sub(sub).fokus(f);
	}

	/** The diary of {@code soul} (Murid › Murid › Buku Harian), also for a disciple that is not loaded. */
	public static Tujuan tujuanBuku(String soul) {
		CompoundTag f = new CompoundTag();
		f.putString("jiwa", soul == null ? "" : soul);
		return Tujuan.of(HubNav.MURID, Urutan.ID_MURID).sub(SUB_BUKU).fokus(f);
	}

	// ================================================================== identity

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.murid.murid");
	}

	@Override
	public int glyph() {
		return Glyphs.STATUS;
	}

	@Override
	public String helpTopic() {
		MuridSub c = aktif();
		return c == null || kunciSub(sub) != null ? "murid" : c.helpTopic();
	}

	@Override
	public List<Sub> subs() {
		List<Sub> list = new ArrayList<>(SUBS.length);
		for (String id : SUBS) {
			if (berlaku(id)) {
				list.add(new Sub(id, Component.translatable(SUB + id), kunciSub(id)));
			}
		}
		return list;
	}

	@Override
	public void showSub(String id) {
		if (id == null || id.isEmpty()) {
			return;
		}
		boolean ganti = !id.equals(sub);
		MuridSub old = ganti ? anak.get(sub) : null;
		if (old != null) {
			old.onHide();
		}
		sub = id;
		MuridSub c = aktif();
		if (c != null) {
			laid.remove(c);
			tataAnak();
			if (ganti || old == null) {
				c.onShow(null);
			}
		}
	}

	@Override
	public boolean lebar() {
		return SUB_BUKU.equals(sub) && !kosong();
	}

	@Override
	public boolean rinci() {
		MuridSub c = terbuka();
		return c != null && c.rinci();
	}

	/** Hidden subs (43.5: a sub that does not apply is hidden): Hati / Hubungan without their server module, the Jalan Hidup subs when it is off. */
	private boolean berlaku(String id) {
		return switch (id) {
			case "hati" -> ClientPlayNetworking.canSend(KepribadianPayloads.Aksi.TYPE);
			case "hubungan" -> ClientPlayNetworking.canSend(HubunganPayloads.Aksi.TYPE);
			case "jalan_hidup", "dompet" -> {
				CompoundTag mandiri = info.getCompoundOrEmpty(MandiriIds.INFO);
				yield mandiri.isEmpty() || mandiri.getBooleanOr("on", false);
			}
			case SUB_BUKU -> kosong() || !soul().isEmpty() || entityId >= 0;
			default -> true;
		};
	}

	/** Why sub {@code id} is locked now (R17), or null. Buku Harian works from the mirror while the disciple is far. */
	private @Nullable Component kunciSub(String id) {
		if (kosong()) {
			// no subject yet (a focus is on its way, or the empty state shows): keep the asked sub open
			return null;
		}
		if (SUB_BUKU.equals(id)) {
			return soul().isEmpty() ? Component.translatable(K + "kunci.tanpa_buku") : null;
		}
		return alasanJauh();
	}

	/** Null while the disciple is loaded, alive and within reach; else why the live subs are locked. */
	private @Nullable Component alasanJauh() {
		if (entityId < 0) {
			return Component.translatable(K + "kunci.tak_dimuat");
		}
		DiscipleEntity disciple = entity();
		if (disciple == null) {
			return Component.translatable(K + "kunci.tak_dimuat");
		}
		if (disciple.isRemoved() || !disciple.isAlive() || disciple.isReleasing()) {
			return Component.translatable(K + "kunci.pergi");
		}
		if (minecraft.player == null || disciple.distanceToSqr(minecraft.player) > RANGE_SQ) {
			return Component.translatable(K + "kunci.jauh", (int) Disciples.VIEW_RANGE);
		}
		return null;
	}

	private boolean kosong() {
		return entityId < 0 && soulBuku.isEmpty();
	}

	// ================================================================== the disciple

	public int entityId() {
		return entityId;
	}

	DiscipleView view() {
		return view;
	}

	/** The raw details of the last {@code DiscipleInfo} (never null; empty for a diary-only disciple). */
	public CompoundTag info() {
		return info;
	}

	/** The soul id of the disciple (its Jalan Hidup part), or the diary-only soul. */
	public String soul() {
		String soul = info.getCompoundOrEmpty(MandiriIds.INFO).getStringOr("soul", "");
		return soul.isEmpty() ? soulBuku : soul;
	}

	/** The disciple on this client, or null when it is not loaded. */
	public @Nullable DiscipleEntity entity() {
		if (minecraft.level == null || entityId < 0) {
			return null;
		}
		Entity entity = minecraft.level.getEntity(entityId);
		return entity instanceof DiscipleEntity disciple ? disciple : null;
	}

	/** The disciple's name: from the details, else its entity, else its diary. */
	public Component discipleName() {
		if (!view.name.isEmpty()) {
			return Component.literal(view.name);
		}
		DiscipleEntity disciple = entity();
		if (disciple != null) {
			return DiscipleLook.name(disciple);
		}
		Component fromBook = soul().isEmpty() ? null : BukuHarianScreen.nama(soul());
		return fromBook != null ? fromBook : Component.translatable("kultivator.discipleui.title");
	}

	/** Sends a disciple action for this disciple (arg = its entity id). */
	public void send(int action, String text) {
		if (entityId >= 0) {
			Keys.send(new Payloads.Action(action, entityId, text));
		}
	}

	/** Closes the Gulungan so an effect in the world can be watched (breakthrough, calamity, duel...). */
	public void tutup() {
		HubNav.tutupSemua();
	}

	/** Shows disciple {@code id}: its cached details now, fresh ones asked for (the picker, a focus tag). */
	private void pilih(int id) {
		if (id == entityId && soulBuku.isEmpty()) {
			return;
		}
		entityId = id;
		soulBuku = "";
		CompoundTag cached = DiscipleClient.info(id);
		info = cached == null ? new CompoundTag() : cached;
		view = new DiscipleView(info);
		seenVersion = DiscipleClient.version(id);
		baru();
		send(Actions.DISCIPLE_OPEN, "refresh");
	}

	/** Shows only the diary of {@code soul} (a disciple that is not loaded here). */
	private void pilihBuku(String soul) {
		DiscipleEntity loaded = cariJiwa(soul);
		if (loaded != null) {
			pilih(loaded.getId());
			return;
		}
		if (entityId < 0 && soul.equals(soulBuku)) {
			return;
		}
		entityId = -1;
		soulBuku = soul;
		info = new CompoundTag();
		view = new DiscipleView(info);
		seenVersion = -1L;
		baru();
	}

	/** A loaded own disciple whose cached details carry {@code soul}, or null. */
	private @Nullable DiscipleEntity cariJiwa(String soul) {
		for (DiscipleEntity d : dekat()) {
			CompoundTag cached = DiscipleClient.info(d.getId());
			if (cached != null && soul.equals(cached.getCompoundOrEmpty(MandiriIds.INFO).getStringOr("soul", ""))) {
				return d;
			}
		}
		return null;
	}

	/** New subject: every sub is made again for it. */
	private void baru() {
		for (MuridSub c : anak.values()) {
			c.onHide();
		}
		anak.clear();
		laid.clear();
		fokusRing.lupakan();
		tataAnak();
		MuridSub c = aktif();
		if (c != null) {
			c.onShow(null);
		}
	}

	/** The own disciples this client sees within reach, by name (the picker's order). */
	private List<DiscipleEntity> dekat() {
		List<DiscipleEntity> list = new ArrayList<>();
		if (minecraft.level == null || minecraft.player == null) {
			return list;
		}
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof DiscipleEntity d && d.isAlive() && d.isOwnedBy(minecraft.player)
					&& d.syncedRole() == DiscipleEntity.Role.DISCIPLE && d.distanceToSqr(minecraft.player) <= RANGE_SQ) {
				list.add(d);
			}
		}
		list.sort(Comparator.comparing((DiscipleEntity d) -> DiscipleLook.name(d).getString().toLowerCase(Locale.ROOT))
				.thenComparingInt(DiscipleEntity::getId));
		return list;
	}

	/** ←/→: the previous / next own disciple nearby. */
	private void ganti(int arah) {
		List<DiscipleEntity> list = dekat();
		if (list.isEmpty() || list.size() == 1 && list.get(0).getId() == entityId) {
			UiSound.denied();
			pesan(Component.translatable(K + "pemilih.sendiri"), Palette.INK_SOFT);
			return;
		}
		int at = -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).getId() == entityId) {
				at = i;
			}
		}
		int next = at < 0 ? (arah > 0 ? 0 : list.size() - 1) : Math.floorMod(at + arah, list.size());
		UiSound.tab();
		pilih(list.get(next).getId());
	}

	/** Picks up details that arrived (the server answers every action with fresh ones). */
	private void segarkan() {
		if (entityId < 0) {
			return;
		}
		long version = DiscipleClient.version(entityId);
		if (version == seenVersion) {
			return;
		}
		seenVersion = version;
		CompoundTag fresh = DiscipleClient.info(entityId);
		if (fresh == null) {
			return;
		}
		info = fresh;
		view = new DiscipleView(fresh);
		MuridSub c = aktif();
		if (c != null) {
			if (c.needsRebuild()) {
				laid.remove(c);
				tataAnak();
			} else {
				c.onData();
			}
		}
	}

	// ================================================================== subs

	/** The shown sub's page (made on first use), or null without a subject. */
	private @Nullable MuridSub aktif() {
		if (kosong() || w <= 0) {
			return null;
		}
		MuridSub c = anak.get(sub);
		if (c == null) {
			c = buat(sub);
			if (c == null) {
				return null;
			}
			if (screen != null) {
				c.pasang(screen);
			}
			anak.put(sub, c);
		}
		return c;
	}

	/** The shown sub when it is open (not locked), else null. */
	private @Nullable MuridSub terbuka() {
		return kunciSub(sub) == null ? aktif() : null;
	}

	private @Nullable MuridSub buat(String id) {
		return switch (id) {
			case "status" -> new StatusTab(this);
			case "teknik" -> tunggal(new TechniqueTab(this));
			case "pil_dao" -> tunggal(new PillTab(this));
			case "kekuatan" -> new Tumpuk(this, Component.translatable(SUB + id), Component.translatable(SUB + id), "bakat",
					List.of(new TalentTab(this), new TreasureTab(this)));
			case "jalan" -> new Tumpuk(this, Component.translatable(SUB + id), Component.translatable(SUB + id), "bintang",
					List.of(new PathTab(this, 0), new PathTab(this, 1), new PathTab(this, 2), new PathTab(this, 3)));
			case "hati" -> tunggal(new HatiTab(this));
			case "hubungan" -> tunggal(new HubunganTab(this));
			case "jalan_hidup" -> new Tumpuk(this, Component.translatable(SUB + id), Component.translatable(SUB + id), "mandiri",
					List.of(new JalanHidupTab(this, JalanHidupTab.HAL_IKHTISAR), new JalanHidupTab(this, JalanHidupTab.HAL_KEBIJAKAN)));
			case SUB_BUKU -> new BukuHarianScreen(this);
			case "dompet" -> tunggal(new JalanHidupTab(this, JalanHidupTab.HAL_TAS));
			default -> null;
		};
	}

	/** A sub taller than a small box (1366x768 at GUI scale 4) in a one-part stack: it scrolls instead of overlapping (43.18.1). */
	private MuridSub tunggal(MuridSub part) {
		return new Tumpuk(this, part.title(), part.shortName(), part.helpTopic(), List.of(part));
	}

	private int anakY() {
		return y + PEMILIH_H + 3;
	}

	/** Lays the shown sub out in the box under the picker (again only when the box changed). */
	private void tataAnak() {
		MuridSub c = aktif();
		if (c == null) {
			return;
		}
		int cy = anakY();
		int ch = y + h - cy;
		String box = x + "," + cy + "," + w + "," + ch;
		if (!box.equals(laid.get(c))) {
			laid.put(c, box);
			c.layout(x, cy, w, ch);
		}
	}

	// ================================================================== life

	@Override
	protected void build() {
		laid.clear();
		for (MuridSub c : anak.values()) {
			if (screen != null) {
				c.pasang(screen);
			}
		}
		tataAnak();
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		super.onShow(fokus);
		if (kosong()) {
			List<DiscipleEntity> list = dekat();
			if (!list.isEmpty()) {
				pilih(list.get(0).getId());
			}
		} else if (entityId >= 0) {
			send(Actions.DISCIPLE_OPEN, "refresh");
		}
		ticks = 0;
		jumlahDekat = dekat().size();
		MuridSub c = aktif();
		if (c != null) {
			tataAnak();
			c.onShow(null);
		}
	}

	@Override
	public void fokus(CompoundTag fokus) {
		int id = fokus.getIntOr("murid", -1);
		String soul = fokus.getStringOr("jiwa", fokus.getStringOr("soul", ""));
		if (id >= 0) {
			pilih(id);
			if (!soul.isEmpty() && entityId == id) {
				// a far body (the board knows its soul): Buku Harian works before its details arrive
				soulBuku = soul;
			}
		} else if (!soul.isEmpty()) {
			pilihBuku(soul);
		}
		String want = "";
		if (!subTunda.isEmpty() && Util.getMillis() - subTundaAt <= TUNDA_MILLIS) {
			want = subTunda;
			subTunda = "";
		} else if (kunciSub(sub) != null && kunciSub(SUB_BUKU) == null && berlaku(SUB_BUKU)) {
			// a far disciple: straight to its diary, the one sub that works (R17)
			want = SUB_BUKU;
		}
		if (!want.isEmpty() && !want.equals(sub)) {
			tuju(Tujuan.of(HubNav.MURID, Urutan.ID_MURID).sub(want));
		}
	}

	@Override
	public void onHide() {
		MuridSub c = anak.get(sub);
		if (c != null) {
			c.onHide();
		}
	}

	@Override
	public void tick() {
		segarkan();
		if (ticks % 10 == 0) {
			jumlahDekat = dekat().size();
		}
		ticks++;
		if (ticks % REFRESH_TICKS == 0 && alasanJauh() == null) {
			// Only fresh details: the server never opens anything for a refresh.
			send(Actions.DISCIPLE_OPEN, "refresh");
		}
		MuridSub c = terbuka();
		if (c != null) {
			c.tick();
		}
	}

	@Override
	public void onData() {
		MuridSub c = terbuka();
		if (c != null) {
			c.onData();
		}
	}

	// ================================================================== drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (kosong()) {
			return;
		}
		// The picker strip: a bamboo slat with the name and rank between the two arrows.
		int sx0 = x + 20;
		int sx1 = x + w - 20;
		Brush.rect(g, sx0, y, sx1, y + PEMILIH_H, Palette.BAMBOO);
		Brush.rect(g, sx0, y, sx1, y + 0.6F, 0x40FFFFFF);
		Brush.rect(g, sx0, y + PEMILIH_H - 1.0F, sx1, y + PEMILIH_H, Palette.BAMBOO_KNOT);
		Component name = Brush.bold(discipleName());
		Component line = entityId >= 0 && !info.isEmpty()
				? Component.empty().append(name).append(Component.literal("  ·  ").withColor(Palette.INK_FAINT))
						.append(view.rank.displayName().withColor(Palette.onPaper(view.rank.realm().color()) & 0xFFFFFF))
				: name;
		Brush.fitCentered(g, font, line, (sx0 + sx1) / 2.0F, y + 3.0F, sx1 - sx0 - 8.0F, Palette.INK, 1.0F, 1.0F, false);
		MuridSub c = terbuka();
		if (c != null) {
			c.render(g, mouseX, mouseY, seconds);
		}
	}

	@Override
	protected void lukis(Lukis l) {
		if (kosong()) {
			KeadaanKosong.gambar(l, KeadaanKosong.Gambar.LENTERA, Component.translatable(K + "kosong"), Component.translatable(K + "kosong.cari"),
					() -> Keys.send(new Payloads.Action(Actions.DISCIPLE_OPEN, -1, "")), x, y, x + w, y + h);
			return;
		}
		boolean banyak = jumlahDekat > (alasanJauh() == null ? 1 : 0);
		Component tiada = banyak ? null : Component.translatable(K + "pemilih.sendiri");
		TalismanButton.gambar(l, "murid_kiri", x, y, 18, PEMILIH_H, Component.literal("‹"), banyak, tiada, () -> ganti(-1));
		TalismanButton.gambar(l, "murid_kanan", x + w - 18, y, 18, PEMILIH_H, Component.literal("›"), banyak, tiada, () -> ganti(1));
		if (l.di(x + 20, y, x + w - 20, y + PEMILIH_H)) {
			l.tip(Component.translatable(K + "pemilih.tip"));
		}
		Component kunci = kunciSub(sub);
		if (!java.util.Objects.equals(kunci, kunciTerakhir)) {
			kunciTerakhir = kunci;
			kunciSejak = Util.getMillis();
		}
		if (kunci != null) {
			KeadaanTerkunci.gambar(l, kunci, Component.translatable(K + "kunci.syarat"), x, anakY(), x + w, y + h, Util.getMillis() - kunciSejak);
		}
	}

	// ================================================================== input

	private boolean diPemilih(double mouseY) {
		return mouseY < anakY();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		MuridSub c = terbuka();
		if (c != null && (c.modal() || !diPemilih(event.y())) && c.mouseClicked(event, doubleClick)) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean used = super.mouseReleased(event);
		MuridSub c = terbuka();
		return (c != null && c.mouseReleased(event)) || used;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		MuridSub c = terbuka();
		return c != null && c.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		MuridSub c = terbuka();
		return c != null && c.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		MuridSub c = terbuka();
		if (c != null && c.keyPressed(event)) {
			return true;
		}
		if (super.keyPressed(event)) {
			return true;
		}
		if ((event.isLeft() || event.isRight()) && !kosong()) {
			int arah = event.isLeft() ? -1 : 1;
			if (event.hasShiftDown()) {
				return gantiSub(arah);
			}
			ganti(arah);
			return true;
		}
		return false;
	}

	/** Shift+←/→: the neighbouring open sub. */
	private boolean gantiSub(int arah) {
		List<Sub> list = subs();
		int at = -1;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id().equals(sub)) {
				at = i;
			}
		}
		for (int step = 1; step < list.size(); step++) {
			Sub next = list.get(Math.floorMod(at + arah * step, list.size()));
			if (next.kunci() == null) {
				UiSound.tab();
				tuju(Tujuan.of(HubNav.MURID, Urutan.ID_MURID).sub(next.id()));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		MuridSub c = terbuka();
		return c != null && c.keyReleased(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		MuridSub c = terbuka();
		return c != null && c.charTyped(event) || super.charTyped(event);
	}

	@Override
	public boolean mauTombol() {
		MuridSub c = terbuka();
		return c != null && c.mauTombol() || super.mauTombol();
	}

	@Override
	public boolean modal() {
		MuridSub c = terbuka();
		return c != null && c.modal() || super.modal();
	}

	@Override
	public boolean mundur() {
		MuridSub c = terbuka();
		return c != null && c.mundur();
	}

	@Override
	public boolean fokusTab(boolean back) {
		MuridSub c = terbuka();
		return c != null ? c.fokusTab(back) : super.fokusTab(back);
	}
}
