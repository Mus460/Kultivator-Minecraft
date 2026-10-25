package dev.kultivator.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.client.Keys;
import dev.kultivator.client.kehancuran.KehancuranClient;
import dev.kultivator.client.kehancuran.RedSealPage;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KeadaanTerkunci;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Realm;
import dev.kultivator.network.Actions;
import dev.kultivator.technique.spec.TechniquePower;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Teknik › Kehancuran (DESIGN 22.11.1, 22.12.6, 43.5, G47): one page, two subs, so the player's own switch and the
 * disciples' red seals are never mistaken for each other.
 * <ul>
 * <li><b>diri</b>, on the blood-red paper: one big red seal is the "Saklar gampang" switch (one click, no oath, hold or
 * checkbox) sending {@code Actions.KEHANCURAN self_on / self_off}; the server answers with its one red chat line. Beside
 * it (scrolls) the status from {@code sessionExtra.teknik.kh}: availability or why not, this rank's K, radius cap and
 * blocks per cast, the safe zones, "Shift saat merapal = tanpa kehancuran", the queue and the last six self casts; under
 * it the Pulihkan talisman with the undo windows (blocks, mm:ss).</li>
 * <li><b>murid</b> (hall owners; locked while the server forbids Mode Kehancuran): {@link RedSealPage}, the old Segel
 * Merah Murid tablet of the hall.</li>
 * </ul>
 * The heavy confirm of an oath or a disciple's release ({@code OpenScreen.KEHANCURAN_CONFIRM}) is shown here as a
 * {@code SegelKonfirmasi} ({@link KehancuranClient#ambilKonfirmasi}). Text at 1.0 (43.0.5). ↑↓ PgUp PgDn Home End scroll
 * the status column (diri) or the lock column (murid); a murid sub that becomes locked while shown draws the lock state.
 */
public final class KehancuranPage extends Page {

	private static final int PAPER_TOP = 0xFF7E1812;
	private static final int PAPER_BOTTOM = 0xFF4E0C08;
	private static final int TEXT = 0xFFF3D9C8;
	private static final int TEXT_SOFT = 0xFFD9A898;
	private static final int STAMP = 0xFFD8261A;
	private static final String P = "kultivator.codex.kh.";
	private static final String SUB_DIRI = "diri";
	private static final String SUB_MURID = "murid";
	private static final long REFRESH_MILLIS = 4000L;

	private final RedSealPage murid = new RedSealPage();
	private String sub = SUB_DIRI;
	private int sealX;
	private int sealY;
	private int sealR;
	private int columnX;
	private long pendingUntil;
	private float scroll;
	private float contentHeight;
	private long askedAt;
	/** When the murid sub's lock state was first drawn (its stamp lands once). */
	private long lockedSince;

	public KehancuranPage() {
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.gui43.hal.teknik.kehancuran");
	}

	@Override
	public int glyph() {
		return Glyphs.SEAL;
	}

	@Override
	public String helpTopic() {
		return SUB_MURID.equals(sub) ? "kehancuran" : "kehancuran_diri";
	}

	@Override
	public List<Sub> subs() {
		return List.of(new Sub(SUB_DIRI, Component.translatable("kultivator.gui43.sub.kehancuran.diri")),
				new Sub(SUB_MURID, Component.translatable("kultivator.gui43.sub.kehancuran.murid"), RedSealPage.kunci()));
	}

	@Override
	public void showSub(String id) {
		String next = SUB_MURID.equals(id) ? SUB_MURID : SUB_DIRI;
		if (next.equals(sub)) {
			return;
		}
		sub = next;
		scroll = 0.0F;
		if (SUB_MURID.equals(sub)) {
			ask();
		}
		if (w > 0) {
			layout(x, y, w, h);
		}
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		ask();
		konfirmasiTertunda();
		super.onShow(fokus);
	}

	@Override
	public void tick() {
		konfirmasiTertunda();
		if (SUB_MURID.equals(sub) && Util.getMillis() - askedAt >= REFRESH_MILLIS) {
			ask();
		}
	}

	/** Hall details for the murid sub and its lock (one refresh, then every 4 s while the murid sub shows). */
	private void ask() {
		askedAt = Util.getMillis();
		RedSealPage.minta();
	}

	/** A heavy confirm the server asked for while this page shows (or that brought the Gulungan here). */
	private void konfirmasiTertunda() {
		KehancuranClient.Konfirmasi k = KehancuranClient.ambilKonfirmasi();
		if (k != null) {
			konfirmasi(k.tingkat(), k.teks(), k.ya());
		}
	}

	@Override
	protected void build() {
		if (SUB_MURID.equals(sub)) {
			murid.build(new RedSealPage.Tuan() {
				@Override
				public void tambah(Widget widget) {
					add(widget);
				}

				@Override
				public void konfirmasi(SegelKonfirmasi.Tingkat tingkat, Component teks, Runnable ya) {
					KehancuranPage.this.konfirmasi(tingkat, teks, ya);
				}
			}, x, y, w, h);
			return;
		}
		int leftW = Math.max(96, Math.min(150, Math.round(w * 0.42F)));
		sealR = Math.max(16, Math.min(28, Math.min(leftW, h - 70) / 3));
		sealX = x + leftW / 2;
		sealY = y + 14 + sealR;
		columnX = x + leftW + 6;
		add(new RedSeal(sealX - sealR, sealY - sealR, sealR * 2));
		int bw = leftW - 8;
		TalismanButton restore = add(new TalismanButton(x + 4, y + h - 16, bw, 15, () -> Component.translatable(P + "restore", undoBlocks())));
		restore.enabledWhen(() -> Keys.connected() && undoBlocks() > 0);
		restore.alasanWhen(() -> Component.translatable(Keys.connected() ? "kultivator.gui43.m02.pulihkan.kosong" : "kultivator.gui43.m02.luring"));
		restore.onPress(() -> send(Actions.KEHANCURAN, 0, "self_undo"));
		restore.tooltip(this::restoreTip);
	}

	private boolean on() {
		return TeknikSession.breakerStored();
	}

	private boolean allowed() {
		CompoundTag kh = TeknikSession.kh();
		if (kh.get("allowed") != null) {
			return kh.getBooleanOr("allowed", false);
		}
		return Minecraft.getInstance().hasSingleplayerServer();
	}

	private int undoBlocks() {
		int total = 0;
		for (Tag entry : TeknikSession.kh().getListOrEmpty("undo")) {
			if (entry instanceof ListTag pair && !pair.isEmpty()) {
				total += pair.get(0).asInt().orElse(0);
			}
		}
		return total;
	}

	private List<Component> restoreTip() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(P + "restore.title"));
		for (Tag entry : TeknikSession.kh().getListOrEmpty("undo")) {
			if (entry instanceof ListTag pair && pair.size() >= 2) {
				int blocks = pair.get(0).asInt().orElse(0);
				int seconds = pair.get(1).asInt().orElse(0);
				lines.add(Component.translatable(P + "restore.entry", SpecInfo.number(blocks), clock(seconds)).withStyle(ChatFormatting.GRAY));
			}
		}
		if (lines.size() == 1) {
			lines.add(Component.translatable(P + "restore.none").withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.translatable(P + "restore.restart").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	private static String clock(int seconds) {
		return String.format(Locale.ROOT, "%d:%02d", Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
	}

	// ================================================================== drawing

	/** The murid sub while the hall answer locks it (no hall, Mode Kehancuran off on the server). */
	private @Nullable Component muridTerkunci() {
		return SUB_MURID.equals(sub) ? RedSealPage.kunci() : null;
	}

	@Override
	protected boolean visible(Widget widget) {
		return muridTerkunci() == null;
	}

	@Override
	protected void lukis(Lukis l) {
		Component lock = muridTerkunci();
		if (lock == null) {
			lockedSince = 0L;
			return;
		}
		if (lockedSince == 0L) {
			lockedSince = Util.getMillis();
		}
		KeadaanTerkunci.gambar(l, lock, Component.translatable("kultivator.gui43.p2.kehancuran.syarat"), x, y, x + w, y + h,
				Util.getMillis() - lockedSince);
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (SUB_MURID.equals(sub)) {
			if (muridTerkunci() == null) {
				murid.draw(g);
			}
			return;
		}
		g.fillGradient(x - 2, y - 2, x + w + 2, y + h + 2, PAPER_TOP, PAPER_BOTTOM);
		Brush.frame(g, x - 2, y - 2, x + w + 2, y + h + 2, Palette.argb(0xC0, Palette.GOLD_DARK));
		boolean on = on();
		Component state = Component.translatable(on ? P + "state_on" : P + "state_off");
		Brush.fitCentered(g, font, Brush.bold(state), sealX, y + 2.0F, columnX - x - 8.0F, on ? 0xFFFF8A70 : TEXT, 1.0F, 1.0F, false);
		int textTop = sealY + sealR + 6;
		int textBottom = y + h - 18;
		if (textBottom - textTop >= 9) {
			g.enableScissor(x, textTop, columnX - 4, textBottom);
			Brush.wrap(g, font, Component.translatable(P + "one_click"), x + 4.0F, textTop, columnX - x - 10.0F, TEXT_SOFT, 1.0F, false);
			g.disableScissor();
		}

		float room = x + w - columnX - 6.0F;
		float maxScroll = Math.max(0.0F, contentHeight - h);
		scroll = Math.max(0.0F, Math.min(maxScroll, scroll));
		g.enableScissor(columnX, y, x + w, y + h);
		float top = y + 1.0F - scroll;
		contentHeight = column(g, top, room) - top;
		g.disableScissor();
		if (maxScroll > 0.0F) {
			float bead = y + 3.0F + (h - 6.0F) * (scroll / maxScroll);
			g.fill(x + w - 2, y, x + w - 1, y + h, Palette.argb(0xA0, Palette.GOLD_DARK));
			Brush.disc(g, x + w - 1.5F, bead, 2.0F, 0xFFFFC8B0);
		}
	}

	/** The status column from {@code top}; returns the y after its last line. */
	private float column(GuiGraphicsExtractor g, float top, float room) {
		CultivationData data = data();
		float tx = columnX;
		float cursor = top;
		CompoundTag kh = TeknikSession.kh();
		List<Component> lines = new ArrayList<>();
		if (!allowed()) {
			Component why = kh.get("why") != null ? Component.translatable(kh.getStringOr("why", "")) : Component.translatable(P + "why_unknown");
			lines.add(Component.translatable(P + "unavailable", why).withColor(0xFFFFB0A0));
		} else if (!data.realm().atLeast(Realm.GOLDEN_CORE)) {
			lines.add(Component.translatable(P + "below_gc").withColor(0xFFFFD0A0));
		} else {
			lines.add(Component.translatable(P + "available").withColor(0xFFB0FFC0));
		}
		double k = kh.get("k") != null ? kh.getDoubleOr("k", 0.0D) : TechniquePower.kScale(data);
		int radius = kh.get("r") != null ? kh.getIntOr("r", 0) : KultivatorConfig.get().kehancuranMaxRadius;
		int cap = kh.get("cap") != null ? kh.getIntOr("cap", 0) : TechniquePower.blockCap(data);
		lines.add(Component.translatable(P + "size", SpecInfo.decimal(k, 2), radius, SpecInfo.number(cap)));
		int safe = kh.get("safe") != null ? kh.getIntOr("safe", 4) : KultivatorConfig.get().kehancuranCasterSafeRadius;
		int home = kh.get("home") != null ? kh.getIntOr("home", 24) : KultivatorConfig.get().kehancuranHomeSafeRadius;
		lines.add(Component.translatable(P + "zone.self", safe));
		lines.add(Component.translatable(P + "zone.home", home));
		lines.add(Component.translatable(P + "zone.others"));
		lines.add(Component.translatable(P + "shift").withStyle(ChatFormatting.BOLD).withColor(0xFFFFE08A));
		int queued = kh.getIntOr("queued", 0);
		boolean running = kh.getBooleanOr("running", false);
		lines.add(Component.translatable(running ? P + "queue.running" : queued > 0 ? P + "queue.waiting" : P + "queue.idle", queued));
		for (Component line : lines) {
			cursor += Brush.wrap(g, font, line, tx, cursor, room, TEXT, 1.0F, false) + 1.5F;
		}
		cursor += 2.0F;
		Brush.rect(g, tx, cursor, tx + room, cursor + 0.6F, Palette.argb(0xA0, Palette.GOLD));
		cursor += 3.0F;
		Brush.fit(g, font, Brush.bold(Component.translatable(P + "log")), tx, cursor, room, 0xFFFFC8B0, 1.0F, 1.0F, false);
		cursor += 10.0F;
		int shown = 0;
		for (Tag entry : kh.getListOrEmpty("log")) {
			if (shown >= 6) {
				break;
			}
			cursor += Brush.wrap(g, font, logLine(entry), tx + 2.0F, cursor, room - 2.0F, TEXT_SOFT, 1.0F, false) + 0.5F;
			shown++;
		}
		if (shown == 0) {
			cursor += Brush.wrap(g, font, Component.translatable(P + "log.none"), tx + 2.0F, cursor, room - 2.0F, TEXT_SOFT, 1.0F, false);
		}
		return cursor + 4.0F;
	}

	private static Component logLine(Tag entry) {
		if (entry instanceof CompoundTag tag) {
			return Component.translatable("kultivator.codex.kh.log.entry", spec(tag.getStringOr("spec", "")), SpecInfo.number(tag.getIntOr("blocks", 0)),
					tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
		}
		return Component.literal(entry.asString().orElse(""));
	}

	private static Component spec(String id) {
		String bare = id.startsWith("self:") ? id.substring(5) : id;
		dev.kultivator.data.TechniqueSpec spec = dev.kultivator.data.TechniqueRegistry.byId(bare);
		return spec == null ? Component.literal(bare) : spec.displayName();
	}

	// ================================================================== input

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		}
		int key = event.key();
		if (SUB_MURID.equals(sub)) {
			return muridTerkunci() == null && murid.gulirTombol(key);
		}
		float page = Math.max(20.0F, h - 20.0F);
		switch (key) {
			case InputConstants.KEY_UP -> scroll -= 14.0F;
			case InputConstants.KEY_DOWN -> scroll += 14.0F;
			case InputConstants.KEY_PAGEUP -> scroll -= page;
			case InputConstants.KEY_PAGEDOWN -> scroll += page;
			case InputConstants.KEY_HOME -> scroll = 0.0F;
			case InputConstants.KEY_END -> scroll = contentHeight;
			default -> {
				return false;
			}
		}
		// drawBehind() clamps to the column.
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (SUB_MURID.equals(sub)) {
			return murid.scrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		if (mouseX >= columnX && mouseX < x + w && mouseY >= y && mouseY < y + h) {
			scroll -= (float) scrollY * 14.0F;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** The big red seal: the one-click switch. */
	private final class RedSeal extends Widget {
		RedSeal(int x, int y, int size) {
			super(x, y, size, size);
			tooltip(() -> List.of(Component.translatable(on() ? P + "seal.off" : P + "seal.on"),
					Component.translatable(P + "seal.tip").withStyle(ChatFormatting.GRAY),
					Component.translatable(P + "seal.undo").withStyle(ChatFormatting.DARK_GRAY)));
		}

		@Override
		public boolean contains(double mouseX, double mouseY) {
			double dx = mouseX - (x + w / 2.0D);
			double dy = mouseY - (y + h / 2.0D);
			return dx * dx + dy * dy <= (w / 2.0D) * (w / 2.0D);
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			boolean on = on();
			float r = w / 2.0F;
			float cx = x + r;
			float cy = y + r + pressOffset(over);
			float beat = on ? 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 300.0D) : 0.0F;
			if (on || over) {
				Brush.halo(g, cx, cy, r, 5.0F, on ? 0xFF4030 : 0xFFB070, on ? 0.6F + 0.4F * beat : hover.get());
			}
			Brush.disc(g, cx + 1.0F, cy + 2.0F, r, 0x60000000);
			Brush.disc(g, cx, cy, r, on ? 0xFFFF5B3A : 0xFF8A1A12);
			Brush.disc(g, cx, cy, r - 2.0F, on ? STAMP : 0xFF5A0E0A);
			Brush.ring(g, cx, cy, r * 0.72F, r * 0.72F + 1.0F, Palette.argb(0xC0, 0xFFE0C0));
			// The carved square of the seal with a cross.
			float s = r * 0.45F;
			Brush.rect(g, cx - s, cy - s, cx + s, cy + s, Palette.argb(on ? 0xF0 : 0xA0, 0xFFE0C0));
			Brush.rect(g, cx - s + 2.0F, cy - s + 2.0F, cx + s - 2.0F, cy + s - 2.0F, on ? STAMP : 0xFF5A0E0A);
			Brush.line(g, cx - s + 3.0F, cy - s + 3.0F, cx + s - 3.0F, cy + s - 3.0F, 1.3F, Palette.argb(on ? 0xF0 : 0xA0, 0xFFE0C0));
			Brush.line(g, cx - s + 3.0F, cy + s - 3.0F, cx + s - 3.0F, cy - s + 3.0F, 1.3F, Palette.argb(on ? 0xF0 : 0xA0, 0xFFE0C0));
			if (Util.getMillis() < pendingUntil) {
				Brush.arc(g, cx, cy, r + 1.0F, r + 2.5F, (Util.getMillis() % 1000L) / 1000.0F, (Util.getMillis() % 1000L) / 1000.0F + 0.25F, 0xFFFFE08A);
			}
		}

		@Override
		protected void activate() {
			toggle();
		}

		private void toggle() {
			if (!Keys.connected()) {
				UiSound.denied();
				return;
			}
			boolean turnOn = !on();
			// The server answers with its red line and one low heartbeat (22.11.1); here only the seal's knock.
			UiSound.toggle(turnOn);
			pendingUntil = Util.getMillis() + 1500L;
			send(Actions.KEHANCURAN, 0, turnOn ? "self_on" : "self_off");
		}
	}
}
