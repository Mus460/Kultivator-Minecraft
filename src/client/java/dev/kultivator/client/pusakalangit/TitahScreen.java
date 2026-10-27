package dev.kultivator.client.pusakalangit;

import dev.kultivator.Kultivator;
import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.epic.EpicSettings;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Eased;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.JadeCoin;
import dev.kultivator.client.screen.ui.JadeSlider;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.SealCard;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.Widget;
import dev.kultivator.data.Realm;
import dev.kultivator.pusakalangit.TitahItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Keabadian › Titah (DESIGN 29.2.2, 43.5; was the Titah Langit screen of the Segel Titah Langit, a page of the Gulungan Utama
 * since 43 M16, so Esc goes back like any page: R3). Left: the four decree cards (Pedang, Tiang, Bintang, Gunung, 2 × 2)
 * with lock state, cooldown sweep and the decree glyph (劍 柱 斗 山); clicking one selects it, and the seal's right-click
 * then casts it; under them the talismans Pilih (select and back to the world to cast), Bawaan and Peringatan cahaya (a
 * link to Pengaturan › Tampilan, 43.10.1). Right, for the selected decree: its name and state, the Tingkat slider with the
 * PERMANEN stamp, the Kekuatan, Kerusakan and Kecepatan sliders ({@link JadeSlider}; values above the realm's limit are
 * sealed red), the "Hancurkan tanah" coin (only while Mode Kehancuran is on), "Tinggalkan pusaka" (Pedang, Tiang), the
 * "Film: Penuh ▸" chip (a link to Pengaturan › Kamera & Film, where the Film Titah rows live), the server's readout line
 * and the hint line. The page is locked below True Immortal and without a seal of the player's in the bag (the rule of the
 * old menu and hub card). Every value is sanitised by the server, which answers with a {@code TitahSync}. Text at 1.0.
 */
public final class TitahScreen extends Page {

	private static final String K = "kultivator.titah_client.screen.";
	private static final String KT = "kultivator.titah_tingkat_client.screen.";
	/** The room right of the Tingkat slider for the PERMANEN stamp. */
	private static final int STAMP_W = 48;
	private static final Identifier ICONS = Kultivator.id("textures/gui/titah/decree_icons.png");
	private static final Identifier WASH = Kultivator.id("textures/gui/titah/seal_wash.png");
	private static final int CARD_W = 42;
	private static final int CARD_H = 36;
	private static final int CARD_GAP = 5;
	private static final int LEFT_W = CARD_W * 2 + CARD_GAP;
	private static final int ROW = 18;
	private static final int BUTTON_H = 14;
	/** Dewa Abadi stage of each decree (Awal, Tengah, Akhir, Puncak). */
	private static final String[] STAGES = {"awal", "tengah", "akhir", "puncak"};

	private int skill = TitahClient.selected();
	private @Nullable JadeSlider tSlider;
	private @Nullable JadeSlider kSlider;
	private @Nullable JadeSlider dSlider;
	private @Nullable JadeSlider vSlider;
	private final Eased glow = new Eased(6.0F);
	private long lastFrame = Util.getMillis();
	private int seenVersion = -1;
	private String layoutKey = "";
	private int filmY;

	public TitahScreen() {
	}

	@Override
	public Component title() {
		return Component.translatable(K + "title");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.gui43.hal.keabadian.titah");
	}

	@Override
	public int glyph() {
		return Glyphs.SEAL;
	}

	@Override
	public String helpTopic() {
		return "titah";
	}

	/** True Immortal first, then a Segel Titah Langit of the player's (or unbound) in the bag, as the server's {@code carries}. */
	@Override
	public @Nullable Component kunci() {
		if (ClientCultivation.received() && !ClientCultivation.data().realm().atLeast(Realm.TRUE_IMMORTAL)) {
			return Component.translatable("kultivator.hub.lock.realm", Realm.TRUE_IMMORTAL.displayName());
		}
		return carriesSeal() ? null : Component.translatable("kultivator.hub.lock.titah_seal");
	}

	private boolean carriesSeal() {
		LocalPlayer player = minecraft.player;
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

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		super.onShow(fokus);
		TitahClient.requestSync();
	}

	/** {@code {"titah": n}}: shows decree {@code n} ({@code OpenScreen.TITAH} from the seal or {@code /kultivasi titah}). */
	@Override
	public void fokus(CompoundTag fokus) {
		int index = fokus.getIntOr("titah", -1);
		if (index >= 0 && index <= 3) {
			choose(index);
		}
	}

	// ================================================================ layout

	@Override
	protected void build() {
		// the four decree cards, 2 x 2
		for (int i = 0; i < 4; i++) {
			int index = i;
			DecreeCard card = new DecreeCard(x + (i % 2) * (CARD_W + CARD_GAP), y + (i / 2) * (CARD_H + CARD_GAP), index);
			card.onPress(() -> choose(index));
			card.tooltip(() -> cardTooltip(index));
			add(card);
		}
		int current = skill;
		// the talismans under the cards
		int by = y + 2 * CARD_H + CARD_GAP + 4;
		TalismanButton pick = new TalismanButton(x, by, LEFT_W, BUTTON_H, () -> Component.translatable(K + "pick"));
		pick.urgentWhen(() -> TitahClient.selected() != current && TitahClient.decree(current).unlocked);
		pick.enabledWhen(() -> TitahClient.decree(current).unlocked);
		pick.onPress(() -> {
			TitahClient.select(current);
			HubNav.tutupSemua();
		});
		pick.tooltip(() -> List.of(Component.translatable(K + "pick.tip").withStyle(ChatFormatting.GRAY)));
		add(pick);
		by += BUTTON_H + 2;
		TalismanButton reset = new TalismanButton(x, by, LEFT_W, BUTTON_H, () -> Component.translatable(K + "reset"));
		reset.onPress(() -> {
			TitahClient.reset(current);
		});
		reset.tooltip(() -> List.of(Component.translatable(K + "reset.tip").withStyle(ChatFormatting.GRAY)));
		add(reset);
		by += BUTTON_H + 2;
		TalismanButton warning = new TalismanButton(x, by, LEFT_W, BUTTON_H, () -> Component.translatable(K + "warning"));
		// the photosensitivity text again, over Kamera & Film where the impact-frame row lives
		warning.onPress(dev.kultivator.client.screen.SettingsPage::peringatanDampak);
		warning.tooltip(() -> List.of(Component.translatable(K + "warning"), Component.translatable(K + "warning.tip").withStyle(ChatFormatting.GRAY)));
		add(warning);

		// the selected decree's controls
		int rx = x + LEFT_W + 8;
		int rw = x + w - rx;
		int ry = y + 12;
		TitahClient.Decree decree = TitahClient.decree(skill);
		boolean open = decree.unlocked;
		// Tingkat (29.16.10): above Kekuatan, the stamp's room on its right
		tSlider = add(new JadeSlider(rx, ry, Math.max(40, rw - STAMP_W), 16, TitahClient.T_STEPS, TitahClient.nearest(TitahClient.T_STEPS, decree.t),
				value -> Component.translatable(KT + "t", Math.round(value), scale(Math.round(value))),
				index -> TitahClient.set(current, TitahClient.FIELD_T, TitahClient.T_STEPS[index]))
				.limit(() -> TitahClient.tMaxIndex(current)));
		tSlider.enabledWhen(() -> open);
		tSlider.tooltip(() -> tierTooltip(current));
		ry += ROW;
		kSlider = add(new JadeSlider(rx, ry, rw, 16, TitahClient.K_STEPS, TitahClient.nearest(TitahClient.K_STEPS, decree.k),
				value -> Component.translatable(K + "k", times(value)),
				index -> TitahClient.set(current, TitahClient.FIELD_K, TitahClient.K_STEPS[index]))
				.limit(() -> TitahClient.kMaxIndex(current)));
		kSlider.enabledWhen(() -> open);
		kSlider.tooltip(() -> List.of(kSlider.message(), Component.translatable(K + "k.tip").withStyle(ChatFormatting.GRAY),
				Component.translatable(K + "k.limit", times(TitahClient.K_STEPS[TitahClient.kMaxIndex(current)])).withStyle(ChatFormatting.GRAY)));
		ry += ROW;
		dSlider = add(new JadeSlider(rx, ry, rw, 16, TitahClient.D_STEPS, TitahClient.nearest(TitahClient.D_STEPS, decree.d),
				value -> Component.translatable(K + "d", Math.round(value * 100.0F)),
				index -> TitahClient.set(current, TitahClient.FIELD_D, TitahClient.D_STEPS[index])));
		dSlider.enabledWhen(() -> open);
		dSlider.tooltip(() -> List.of(dSlider.message(), Component.translatable(K + "d.tip").withStyle(ChatFormatting.GRAY)));
		ry += ROW;
		vSlider = add(new JadeSlider(rx, ry, rw, 16, TitahClient.V_STEPS, TitahClient.nearest(TitahClient.V_STEPS, decree.v),
				value -> Component.translatable(K + "v", times(value)),
				index -> TitahClient.set(current, TitahClient.FIELD_V, TitahClient.V_STEPS[index])));
		vSlider.enabledWhen(() -> open);
		vSlider.tooltip(() -> List.of(vSlider.message(), Component.translatable(K + "v.tip").withStyle(ChatFormatting.GRAY)));
		ry += ROW + 1;
		if (TitahClient.kehancuranOn()) {
			JadeCoin coin = new JadeCoin(rx, ry, rw, 12, () -> Component.translatable(K + "h", Component.translatable(
					TitahClient.decree(current).h ? K + "h.on" : K + "h.off")), () -> TitahClient.decree(current).h);
			coin.onPress(() -> TitahClient.set(current, TitahClient.FIELD_H, !TitahClient.decree(current).h));
			coin.enabledWhen(() -> TitahClient.decree(current).unlocked);
			coin.tooltip(() -> List.of(Component.translatable(K + "h.tip").withStyle(ChatFormatting.GRAY)));
			add(coin);
			ry += 14;
		}
		if (skill <= 1) {
			JadeCoin coin = new JadeCoin(rx, ry, rw, 12, () -> Component.translatable(K + "m", Component.translatable(
					TitahClient.decree(current).m ? K + "m.on" : K + "m.off")), () -> TitahClient.decree(current).m);
			coin.onPress(() -> TitahClient.set(current, TitahClient.FIELD_M, !TitahClient.decree(current).m));
			coin.enabledWhen(() -> TitahClient.decree(current).unlocked);
			coin.tooltip(() -> List.of(Component.translatable(K + "m.tip").withStyle(ChatFormatting.GRAY)));
			add(coin);
			ry += 14;
		}
		// the Film choice (client side): one chip naming it; the choice itself lives in Pengaturan › Kamera & Film (33.6 R10)
		TalismanButton film = new TalismanButton(rx, ry, rw, 13, () -> Component.translatable("kultivator.uxpages.titah.film",
				Component.translatable("kultivator.titah_client.film." + EpicSettings.titahFilm(current).id())));
		film.onPress(() -> HubNav.openSettings(HubNav.GROUP_FILM));
		film.tooltip(() -> List.of(Component.translatable("kultivator.uxpages.titah.film.title"),
				Component.translatable("kultivator.titah_client.film." + EpicSettings.titahFilm(current).id() + ".tip").withStyle(ChatFormatting.GRAY),
				Component.translatable("kultivator.uxpages.titah.film.tip").withStyle(ChatFormatting.DARK_GRAY)));
		add(film);
		filmY = ry;

		layoutKey = layoutKey();
		seenVersion = TitahClient.version();
	}

	/** What decides which controls exist: the decree, Mode Kehancuran and the lock (a change rebuilds the controls). */
	private String layoutKey() {
		return skill + ":" + TitahClient.kehancuranOn() + ":" + TitahClient.decree(skill).unlocked;
	}

	private void choose(int index) {
		if (index == skill) {
			return;
		}
		skill = index;
		if (TitahClient.decree(index).unlocked) {
			TitahClient.select(index);
		}
		if (w > 0) {
			layout(x, y, w, h);
		}
	}

	@Override
	public void tick() {
		if (w <= 0) {
			return;
		}
		if (!layoutKey.equals(layoutKey())) {
			layout(x, y, w, h);
			return;
		}
		if (seenVersion != TitahClient.version()) {
			// a sync arrived: follow the server's sanitised values (never under the player's hand)
			seenVersion = TitahClient.version();
			TitahClient.Decree decree = TitahClient.decree(skill);
			if (tSlider != null) {
				tSlider.sync(TitahClient.nearest(TitahClient.T_STEPS, decree.t));
			}
			if (kSlider != null) {
				kSlider.sync(TitahClient.nearest(TitahClient.K_STEPS, decree.k));
			}
			if (dSlider != null) {
				dSlider.sync(TitahClient.nearest(TitahClient.D_STEPS, decree.d));
			}
			if (vSlider != null) {
				vSlider.sync(TitahClient.nearest(TitahClient.V_STEPS, decree.v));
			}
		}
	}

	// ================================================================ drawing

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		int rx = x + LEFT_W + 8;
		int rw = x + w - rx;
		// the seal's glyph 敕 ("by decree") as a faint ink wash behind the controls
		int washSize = Math.min(96, Math.min(rw, h));
		g.blit(RenderPipelines.GUI_TEXTURED, WASH, rx + rw - washSize, y + h - washSize, 0.0F, 0.0F, washSize, washSize, 128, 128, 128, 128,
				Palette.argb(0x22, Palette.JADE_DARK));
	}

	@Override
	protected void lukis(Lukis l) {
		GuiGraphicsExtractor g = l.g;
		long now = Util.getMillis();
		float seconds = Math.max(0.0F, Math.min(0.1F, (now - lastFrame) / 1000.0F));
		lastFrame = now;
		int rx = x + LEFT_W + 8;
		int rw = x + w - rx;

		// the selected decree: its name, then its stage or cooldown on the right
		TitahClient.Decree decree = TitahClient.decree(skill);
		Component state;
		int stateColor;
		if (!TitahClient.synced()) {
			state = Component.translatable(TitahClient.tooBig() ? "kultivator.sect_fix_feat.too_big" : K + "loading");
			stateColor = TitahClient.tooBig() ? Palette.SEAL : Palette.INK_FAINT;
		} else if (!decree.unlocked) {
			state = lockedLine(skill);
			stateColor = Palette.SEAL;
		} else if (TitahClient.cooldownLeft(skill) > 0) {
			state = Component.translatable(K + "cooldown", TitahClient.minutes((TitahClient.cooldownLeft(skill) + 19) / 20));
			stateColor = Palette.WARN;
		} else {
			state = Component.translatable(TitahClient.selected() == skill ? K + "ready_selected" : K + "ready");
			stateColor = Palette.JADE_DARK;
		}
		float nameW = Brush.fit(g, font, Brush.bold(TitahClient.decreeName(skill)), rx, y, rw * 0.5F, decree.unlocked ? Palette.INK : Palette.INK_SOFT,
				1.0F, 1.0F, false);
		float stateRoom = rw - nameW - 6.0F;
		float stateW = Math.min(stateRoom, font.width(state));
		Brush.fit(g, font, state, rx + rw - stateW, y, stateRoom, stateColor, 1.0F, 1.0F, false);

		// the PERMANEN stamp right of the Tingkat slider (tier 6-9 and this cast would carve for real)
		int stampX = rx + Math.max(40, rw - STAMP_W) + 4;
		int stampY = y + 12;
		if (decree.unlocked && TitahClient.permanent(skill) && rx + rw - stampX >= 24) {
			drawStamp(g, font, stampX, stampY, rx + rw - stampX);
			if (l.di(stampX, stampY, rx + rw, stampY + 16)) {
				l.tip(List.of(Component.translatable(KT + "perm").withStyle(ChatFormatting.RED),
						Component.translatable(KT + "perm.tip").withStyle(ChatFormatting.GRAY)));
			}
		}

		// the readout and the hint under the Film chip
		float ry = filmY + 16.0F;
		float pulse = glow.to(1.0F, seconds);
		if (ry + 9.0F <= y + h) {
			Brush.fit(g, font, TitahClient.readout(skill), rx, ry, rw, Palette.mix(Palette.INK_SOFT, Palette.JADE_DARK, 0.5F * pulse), 1.0F, 1.0F, false);
		}
		ry += 10.0F;
		if (ry + 9.0F <= y + h) {
			Component hint = Component.translatable(TitahClient.tooBig() ? "kultivator.sect_fix_feat.too_big" : K + "hint");
			Brush.fit(g, font, hint, rx, ry, rw, TitahClient.tooBig() ? Palette.SEAL : Palette.INK_FAINT, 1.0F, 1.0F, false);
		}
	}

	/** The world scale of a tier with one decimal (×1,0 … ×43,0). */
	private static String scale(int tier) {
		return "×" + String.format(Locale.ROOT, "%.1f", Math.pow(1.6D, Math.max(1, Math.min(9, tier)) - 1)).replace('.', ',');
	}

	/** The Tingkat slider's tooltip: what it does, the limit now and why higher tiers are sealed. */
	private static List<Component> tierTooltip(int index) {
		TitahClient.Decree decree = TitahClient.decree(index);
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(KT + "t.tip").withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable(KT + "t.limit", decree.tMax).withStyle(ChatFormatting.GRAY));
		if (decree.tMax < 9 && !decree.tWhy.isEmpty()) {
			lines.add(Component.translatable(decree.tWhy).withStyle(ChatFormatting.RED));
		}
		if (decree.t >= TitahClient.PERMANENT_TIER) {
			lines.add(Component.translatable(TitahClient.permanent(index) ? KT + "perm.tip" : KT + "t.visual").withStyle(
					TitahClient.permanent(index) ? ChatFormatting.RED : ChatFormatting.GRAY));
		}
		return lines;
	}

	/** A red seal stamp reading PERMANEN (its frame breathes so it is never missed). */
	private static void drawStamp(GuiGraphicsExtractor g, Font font, int x, int y, int w) {
		float pulse = 0.75F + 0.25F * (float) Math.sin(Util.getMillis() / 260.0D);
		int x1 = x + w;
		int y1 = y + 15;
		g.fill(x + 1, y + 2, x1 + 1, y1 + 1, 0x30000000);
		Brush.frame(g, x, y, x1, y1, Palette.argb(Math.round(200 * pulse), Palette.SEAL));
		Brush.frame(g, x + 1, y + 1, x1 - 1, y1 - 1, Palette.argb(0x90, Palette.SEAL));
		g.fill(x + 2, y + 2, x1 - 2, y1 - 2, Palette.argb(0x30, Palette.SEAL));
		Brush.fitCentered(g, font, Brush.bold(Component.translatable(KT + "perm")), (x + x1) / 2.0F, y + 4.0F, w - 4.0F, Palette.SEAL, 1.0F, 1.0F, false);
	}

	private static String times(float value) {
		String text = value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%s", value);
		return "×" + text.replace('.', ',');
	}

	/** Why a decree is locked: the reason the server sent (a lang key) when there is one, else the Dewa Abadi stage it needs. */
	private static Component lockedLine(int index) {
		String why = TitahClient.decree(index).why;
		if (!why.isEmpty()) {
			return Component.translatable(why);
		}
		return Component.translatable(K + "locked", Component.translatable(K + "stage." + STAGES[index]));
	}

	private List<Component> cardTooltip(int index) {
		TitahClient.Decree decree = TitahClient.decree(index);
		List<Component> lines = new ArrayList<>();
		lines.add(Brush.bold(TitahClient.decreeName(index)));
		lines.add(Component.translatable(K + "desc." + index).withStyle(ChatFormatting.GRAY));
		if (!decree.unlocked) {
			lines.add(lockedLine(index).copy().withStyle(ChatFormatting.RED));
		} else if (TitahClient.cooldownLeft(index) > 0) {
			lines.add(Component.translatable(K + "cooldown", TitahClient.minutes((TitahClient.cooldownLeft(index) + 19) / 20))
					.withStyle(ChatFormatting.GOLD));
		}
		return lines;
	}

	// ================================================================ the decree card

	/**
	 * One decree as a square seal card (the 5b seal-card look, {@link SealCard}'s palette): the decree glyph over a faint
	 * silhouette of its weapon, the cooldown sweep, a pulsing gold frame when shown, a red stamp on the decree the seal
	 * casts, a padlock while the realm stage is not reached.
	 */
	private final class DecreeCard extends Widget {
		private final int index;

		DecreeCard(int x, int y, int index) {
			super(x, y, CARD_W, CARD_H);
			this.index = index;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, Font font, boolean over, float seconds) {
			TitahClient.Decree decree = TitahClient.decree(index);
			boolean bright = decree.unlocked;
			boolean shown = index == skill;
			int xa = x;
			int ya = y + pressOffset(over);
			int xb = xa + w;
			int yb = ya + h;
			long now = Util.getMillis();
			int tone = DECREE_TONE[index];
			if (shown) {
				float pulse = 0.5F + 0.5F * (float) Math.sin(now / 220.0D);
				Brush.frame(g, xa - 2, ya - 2, xb + 2, yb + 2, Palette.mix(Palette.GOLD, Palette.GOLD_LIGHT, pulse));
				Brush.frame(g, xa - 1, ya - 1, xb + 1, yb + 1, Palette.GOLD_DARK);
			} else if (hover.get() > 0.02F) {
				Brush.frame(g, xa - 1, ya - 1, xb + 1, yb + 1, Palette.argb(Math.round(110 * hover.get()), bright ? tone : Palette.LOCKED));
			}
			g.fill(xa + 1, ya + 2, xb + 1, yb + 1, 0x40000000);
			g.fill(xa, ya, xb, yb, bright ? Palette.mix(tone, Palette.INK, 0.15F) : Palette.LOCKED);
			int paper = bright ? Palette.lighten(0xFFF4E6B8, 0.15F * hover.get()) : Palette.LOCKED_PAPER;
			g.fill(xa + 2, ya + 2, xb - 2, yb - 2, paper);
			// the weapon's silhouette, faint, behind the glyph
			int icon = Math.min(w, h) - 6;
			g.blit(RenderPipelines.GUI_TEXTURED, ICONS, (xa + xb - icon) / 2, ya + 3, index * 32.0F, 0.0F, icon, icon, 32, 32, 128, 32,
					Palette.argb(bright ? 0x58 : 0x30, bright ? tone : Palette.INK_FAINT));
			// the cooldown sweep
			int left = TitahClient.cooldownLeft(index);
			if (bright && left > 0) {
				float fraction = Math.min(1.0F, left / (float) Math.max(1, decree.cdTotal));
				g.enableScissor(xa + 2, ya + 2, xb - 2, yb - 2);
				Brush.pie(g, (xa + xb) / 2.0F, (ya + yb) / 2.0F, Math.max(w, h) * 0.75F, 1.0F - fraction, 1.0F,
						Palette.mix(paper, Palette.NIGHT_MIST, 0.4F));
				g.disableScissor();
			}
			Brush.frame(g, xa + 3, ya + 3, xb - 3, yb - 3, Palette.argb(bright ? 0x70 : 0x40, bright ? tone : Palette.INK_FAINT));
			// the decree glyph (title size 1.5, 43.0.5); its name is the tooltip
			Component glyph = Component.literal(TitahClient.GLYPHS[index]);
			Brush.centered(g, font, glyph, (xa + xb) / 2.0F, ya + 6.0F, bright ? Palette.onPaper(tone) : Palette.INK_FAINT, 1.5F, false);
			Brush.fitCentered(g, font, Component.translatable(K + "short." + index), (xa + xb) / 2.0F, yb - 11.0F, w - 6.0F,
					bright ? Palette.INK : Palette.INK_SOFT, 1.0F, 1.0F, false);
			// the decree the seal casts carries a small red stamp
			if (bright && TitahClient.selected() == index) {
				g.fill(xb - 8, ya + 3, xb - 3, ya + 8, Palette.SEAL);
				g.fill(xb - 7, ya + 4, xb - 4, ya + 7, Palette.argb(0xB0, Palette.TALISMAN));
				g.fill(xb - 6, ya + 5, xb - 5, ya + 6, Palette.SEAL);
			}
			if (!bright) {
				g.fill(xa + 2, ya + 2, xb - 2, yb - 2, 0x30FFFFFF);
				Glyphs.lock(g, (xa + xb) / 2.0F, (ya + yb) / 2.0F, 10.0F, Palette.INK_SOFT);
			}
		}
	}

	/** Card tones: white-gold sword, ember pillar, violet dipper, gold-jade mountain seal. */
	private static final int[] DECREE_TONE = {0xFFC8A030, 0xFFD2622A, 0xFF8A5AD8, 0xFF2E8963};
}
