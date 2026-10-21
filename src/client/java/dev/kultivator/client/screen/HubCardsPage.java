package dev.kultivator.client.screen;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.Keys;
import dev.kultivator.client.hall.HallClient;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Realm;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.pusakalangit.TitahItems;
import dev.kultivator.sect.hall.Halls;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The launcher cards of the hub sections Murid, Sekte, Dunia, Keabadian and Panduan (DESIGN 33.3). Each card names a
 * screen or tablet, says in one line what it is, and opens it; a card the player cannot use yet is drawn at 45 % ink with
 * a small lock and its lock line ("terbuka di ..."), and a click on it plays the thock and lights that line for 2 s
 * (never a silent no-op). Locks only come from synced client data; anything else opens and the page explains itself.
 */
final class HubCardsPage extends Page {

	/** {@code Halls} page ids without a named constant (the hall screen's page list, DESIGN 20 / 21). */
	private static final int HALL_TUGAS = 5;
	private static final int HALL_SEGEL_MERAH = 9;
	private static final long DENIED_MILLIS = 2000L;
	private static final int HEAD = 14;
	private static final int GAP = 5;

	private final int kind;
	private final List<Card> cards = new ArrayList<>();
	private long hallAsked = -1L;
	private boolean builtNoHall;

	HubCardsPage(CultivationScreen screen, int kind) {
		super(screen);
		this.kind = kind;
	}

	private String key() {
		return switch (kind) {
			case HubNav.MURID -> "murid";
			case HubNav.SEKTE -> "sekte";
			case HubNav.DUNIA -> "dunia";
			case HubNav.KEABADIAN -> "keabadian";
			default -> "panduan";
		};
	}

	@Override
	Component title() {
		return Component.translatable(kind == HubNav.PANDUAN ? "kultivator.hub.sub.kitab" : "kultivator.hub.section." + key());
	}

	@Override
	Component shortName() {
		return Component.translatable(kind == HubNav.PANDUAN ? "kultivator.hub.sub.kitab" : "kultivator.hub.section." + key());
	}

	@Override
	String helpTopic() {
		return switch (kind) {
			case HubNav.MURID -> "calon";
			case HubNav.SEKTE -> "aula";
			case HubNav.DUNIA -> "jalan_dunia";
			case HubNav.KEABADIAN -> "jalan_abadi";
			default -> "jalan";
		};
	}

	/** The hub switched to this page: the Sekte cards ask for the player's own hall (one refresh, no other traffic). */
	void shown() {
		if (kind == HubNav.SEKTE && Keys.connected()) {
			hallAsked = HallClient.version();
			HallClient.send(Halls.OP_REFRESH, "");
		}
	}

	// ================================================================== locks (synced client data only)

	/** True when a fresh hall answer says the player has no hall of their own. */
	private boolean noHall() {
		if (hallAsked < 0L || HallClient.version() <= hallAsked) {
			return false;
		}
		return !HallClient.info().getBooleanOr("has", false);
	}

	private @Nullable Component hallLock() {
		return noHall() ? Component.translatable("kultivator.hub.lock.hall") : null;
	}

	private @Nullable Component redSealLock() {
		Component hall = hallLock();
		if (hall != null) {
			return hall;
		}
		boolean fresh = hallAsked >= 0L && HallClient.version() > hallAsked;
		CompoundTag info = HallClient.info();
		if (fresh && info.getBooleanOr("has", false) && !info.getCompoundOrEmpty("cfg").getBooleanOr("kehancuran", true)) {
			return Component.translatable("kultivator.hub.lock.red_off");
		}
		return null;
	}

	private static @Nullable Component realmLock(Realm needed) {
		if (!ClientCultivation.received()) {
			return null;
		}
		CultivationData data = ClientCultivation.data();
		return data.realm().atLeast(needed) ? null : Component.translatable("kultivator.hub.lock.realm", needed.displayName());
	}

	/** The phase-9 ascent to Alam Abadi ({@code extra.abadi.ascended}, synced; not the phase-5 {@code CultivationData.ascended()}). */
	private static boolean ascendedToImmortalRealm() {
		CompoundTag abadi = ClientCultivation.data().extra().getCompoundOrEmpty("abadi");
		if (abadi.getBooleanOr("ascended", false)) {
			return true;
		}
		return dev.kultivator.client.abadi.AbadiClient.data().getCompoundOrEmpty("alam").getBooleanOr("ascended", false);
	}

	private static @Nullable Component ascentLock() {
		if (!ClientCultivation.received() || ascendedToImmortalRealm()) {
			return null;
		}
		return Component.translatable("kultivator.hub.lock.ascend");
	}

	private static @Nullable Component titahLock() {
		Component realm = realmLock(Realm.TRUE_IMMORTAL);
		if (realm != null) {
			return realm;
		}
		return carriesSeal() ? null : Component.translatable("kultivator.hub.lock.titah_seal");
	}

	/** A Segel Titah Langit of the player's (or unbound) in the inventory, as the server's {@code carries} checks. */
	private static boolean carriesSeal() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		UUID self = player.getUUID();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(TitahItems.SEAL)) {
				UUID owner = stack.get(TitahItems.OWNER);
				if (owner == null || owner.equals(self)) {
					return true;
				}
			}
		}
		return false;
	}

	// ================================================================== cards

	@Override
	protected void build() {
		cards.clear();
		switch (kind) {
			case HubNav.MURID -> card("murid", Glyphs.TALENT, null, () -> Keys.send(new Payloads.Action(Actions.DISCIPLE_OPEN, -1, "")));
			case HubNav.SEKTE -> {
				builtNoHall = noHall();
				if (builtNoHall) {
					card("dirikan", Glyphs.HALL, null, () -> HubNav.help("aula"));
				}
				card("aula", Glyphs.HALL, this::hallLock, () -> HubNav.openHall(Halls.PAGE_AULA));
				card("bangun", Glyphs.BUILD, this::hallLock, () -> HubNav.openHall(Halls.PAGE_BANGUN));
				card("formasi", Glyphs.ROOT_DAO, this::hallLock, () -> HubNav.openHall(Halls.PAGE_FORMASI));
				card("anggota", Glyphs.TALENT, this::hallLock, () -> HubNav.openHall(Halls.PAGE_CALON));
				card("perbendaharaan", Glyphs.TREASURE, this::hallLock, () -> HubNav.openHall(Halls.PAGE_TREASURY));
				card("kegiatan", Glyphs.JALAN, this::hallLock, () -> HubNav.openHall(HALL_TUGAS));
				card("wilayah", Glyphs.PATH, this::hallLock, () -> HubNav.openHall(Halls.PAGE_PERTAHANAN));
				card("segel_merah", Glyphs.SEAL, this::redSealLock, () -> HubNav.openHall(HALL_SEGEL_MERAH));
			}
			case HubNav.DUNIA -> {
				card("alam_rahasia", Glyphs.PATH, () -> realmLock(Realm.QI_REFINING),
						() -> dev.kultivator.client.dunia.DuniaClient.open(Payloads.OpenScreen.DUNIA_ALAM));
				card("pusaka", Glyphs.TREASURE, null, () -> dev.kultivator.client.dunia.DuniaClient.open(Payloads.OpenScreen.DUNIA_PUSAKA));
				card("musuh", Glyphs.TECHNIQUES, null, () -> dev.kultivator.client.dunia.DuniaClient.open(Payloads.OpenScreen.DUNIA_MUSUH));
				card("cincin", Glyphs.RING, null, () -> Keys.send(new Payloads.Action(Actions.TEMPA, 0, "ring:open")));
			}
			case HubNav.KEABADIAN -> {
				card("alam_abadi", Glyphs.SEAL, null, () -> abadi(Payloads.OpenScreen.ABADI_ALAM));
				card("purba", Glyphs.TALENT, HubCardsPage::ascentLock, () -> abadi(Payloads.OpenScreen.ABADI_PURBA));
				card("bencana", Glyphs.ALKIMIA, HubCardsPage::ascentLock, () -> abadi(Payloads.OpenScreen.ABADI_BENCANA));
				card("batin", Glyphs.STATUS, HubCardsPage::ascentLock, () -> abadi(Payloads.OpenScreen.ABADI_BATIN));
				card("ciptaan", Glyphs.CODEX, HubCardsPage::ascentLock, () -> abadi(Payloads.OpenScreen.ABADI_CIPTAAN));
				card("lelang", Glyphs.TREASURE, HubCardsPage::ascentLock, () -> abadi(Payloads.OpenScreen.ABADI_LELANG));
				card("titah", Glyphs.SEAL, HubCardsPage::titahLock, HubNav::openTitah);
			}
			default -> {
				card("kitab_panduan", Glyphs.JALAN, null, () -> dev.kultivator.client.guide.GuideClient.open(screen));
				card("ensiklopedia", Glyphs.HELP, null, () -> screen.showSection(HubNav.PANDUAN, HubNav.SUB_ENSIKLOPEDIA));
			}
		}
		// Phase 34 (DESIGN 34.6 T): the cards other modules registered for this section.
		for (dev.kultivator.client.hub.HubCards.Extra extra : dev.kultivator.client.hub.HubCards.of(kind)) {
			card(extra.id(), extra.glyph(), extra.lock(), extra.action());
		}
		layoutCards();
	}

	private static void abadi(int tab) {
		dev.kultivator.client.dunia.DuniaClient.openAbadi(tab);
	}

	private void card(String id, int glyph, @Nullable Supplier<Component> lock, Runnable action) {
		Card card = new Card(id, glyph, lock, action);
		cards.add(add(card));
	}

	/** A grid of 1-3 columns that always fits the content box (no scrolling at 320 × 240). */
	private void layoutCards() {
		int n = cards.size();
		if (n == 0) {
			return;
		}
		int cols = w >= 330 ? 3 : w >= 170 ? 2 : 1;
		cols = Math.max(1, Math.min(cols, n));
		int rows = (n + cols - 1) / cols;
		int footer = kind == HubNav.MURID || kind == HubNav.SEKTE ? 12 : 0;
		int room = h - HEAD - footer - GAP * (rows - 1);
		int cardH = Math.max(20, Math.min(kind == HubNav.MURID || kind == HubNav.PANDUAN ? 52 : 40, room / rows));
		int cardW = (w - 4 - GAP * (cols - 1)) / cols;
		for (int i = 0; i < n; i++) {
			Card card = cards.get(i);
			int col = i % cols;
			int row = i / cols;
			card.x = x + 2 + col * (cardW + GAP);
			card.y = y + HEAD + row * (cardH + GAP);
			card.w = cardW;
			card.h = cardH;
		}
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		if (kind == HubNav.SEKTE && noHall() != builtNoHall) {
			// The hall answer arrived: the "Dirikan sekte" card comes or goes (no structure change mid-frame after this).
			layout(x, y, w, h);
		}
		heading(g, title(), x + 2.0F, y, w - 4.0F, 0.85F);
		if (cards.isEmpty()) {
			return;
		}
		Card last = cards.get(cards.size() - 1);
		float footY = last.y + last.h + 4.0F;
		if (kind == HubNav.MURID) {
			Component hint = Component.translatable("kultivator.hub.murid.hint", Keys.MURID.getTranslatedKeyMessage());
			Brush.wrap(g, font, hint, x + 4.0F, footY, w - 8.0F, Palette.INK_SOFT, 0.65F, false);
		} else if (kind == HubNav.SEKTE && hallAsked >= 0L && HallClient.version() <= hallAsked && Keys.connected()) {
			Brush.fit(g, font, Component.translatable("kultivator.hub.sekte.loading"), x + 4.0F, footY, w - 8.0F, Palette.INK_FAINT, 0.65F, 0.6F,
					false);
		}
	}

	/** One launcher card. */
	private final class Card extends Widget {

		private final String id;
		private final int glyph;
		private final @Nullable Supplier<Component> lock;
		private final Runnable run;
		private long deniedAt = Long.MIN_VALUE / 2;

		Card(String id, int glyph, @Nullable Supplier<Component> lock, Runnable run) {
			super(0, 0, 0, 0);
			this.id = id;
			this.glyph = glyph;
			this.lock = lock;
			this.run = run;
			tooltip(this::lines);
		}

		private @Nullable Component lockLine() {
			return lock == null ? null : lock.get();
		}

		private Component name() {
			return Component.translatable("kultivator.hub.card." + id);
		}

		private Component tip() {
			return Component.translatable("kultivator.hub.card." + id + ".tip");
		}

		private List<Component> lines() {
			List<Component> lines = new ArrayList<>();
			lines.add(Brush.bold(name()));
			lines.add(tip().copy().withStyle(ChatFormatting.GRAY));
			Component locked = lockLine();
			if (locked != null) {
				lines.add(locked.copy().withStyle(ChatFormatting.RED));
			} else {
				lines.add(Component.translatable("kultivator.hub.card.open").withStyle(ChatFormatting.DARK_GREEN));
			}
			return lines;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			Component locked = lockLine();
			float ink = locked == null ? 1.0F : 0.45F;
			float glow = hover.get();
			int off = pressOffset(over);
			int x0 = x;
			int y0 = y + off;
			int x1 = x + w;
			int y1 = y + h + off;
			g.fill(x0 + 1, y0 + 1, x1 + 1, y1 + 1, 0x30000000);
			int paper = locked == null ? Palette.mix(Palette.PAPER_TOP, 0xFFF6F1DC, glow * 0.6F) : Palette.LOCKED_PAPER;
			g.fill(x0, y0, x1, y1, Palette.fade(paper, locked == null ? 1.0F : 0.85F));
			Brush.frame(g, x0, y0, x1, y1, locked == null ? Palette.mix(Palette.GOLD_DARK, Palette.GOLD, glow) : Palette.argb(0xA0, Palette.LOCKED));
			float iconSize = Math.max(8.0F, Math.min(18.0F, h * 0.45F));
			float icx = x0 + 4.0F + iconSize / 2.0F;
			float icy = y0 + h / 2.0F;
			Glyphs.tab(g, glyph, icx, icy, iconSize, Palette.fade(locked == null ? Palette.JADE_DARK : Palette.INK_FAINT, ink));
			float tx = x0 + iconSize + 8.0F;
			float room = x1 - tx - (locked != null ? 9.0F : 3.0F);
			boolean twoLines = h >= 27;
			float nameY = twoLines ? y0 + h / 2.0F - 8.0F : y0 + h / 2.0F - 3.5F;
			Brush.fit(g, font, Brush.bold(name()), tx, nameY, room, Palette.fade(Palette.SEAL_DARK, ink), 0.8F, 0.6F, false);
			if (locked != null) {
				Glyphs.lock(g, x1 - 6.0F, y0 + 6.0F, 6.0F, Palette.fade(Palette.SEAL, 0.8F));
			}
			if (twoLines) {
				boolean flash = Util.getMillis() - deniedAt < DENIED_MILLIS;
				Component line = locked != null ? locked : tip();
				int color = locked != null ? (flash ? Palette.SEAL : Palette.fade(Palette.SEAL_DARK, 0.75F)) : Palette.INK_SOFT;
				Brush.fit(g, font, flash ? Brush.bold(line) : line, tx, nameY + 9.0F, x1 - tx - 3.0F, color, 0.65F, 0.6F, false);
			}
		}

		@Override
		protected void activate() {
			if (lockLine() != null) {
				UiSound.denied();
				deniedAt = Util.getMillis();
				return;
			}
			UiSound.press();
			run.run();
		}
	}
}
