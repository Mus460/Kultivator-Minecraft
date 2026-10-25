package dev.kultivator.client.alam;

import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.screen.CultivationScreen;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.KartuHasil;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.network.Actions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Gulungan Harta, the treasure scroll of a cleared realm (DESIGN 25.3.4 step 6, 25.10), as the Di Sini prompt "Hadiah"
 * (DESIGN 43.8.2; was its own screen that cut into whatever was open, R11): the kit's result card names what the run gave
 * (already in the inventory; what did not fit waits at the return point or in the Lumbung) and, where the realm says so,
 * three seal cards below to choose one from (click, or Tab + Enter). A choice not made within a minute takes the first card
 * on the server; the page then says so. "Tutup" (or Esc) closes; closing before choosing keeps the scroll waiting on the Di
 * Sini tablet until the minute is up. While another screen is open the scroll only waits on the tablet (no interruption).
 */
public class TreasureScreen extends Page {

	private static final String M = "kultivator.gui43.m13.hadiah.";
	private static final long CHOICE_MILLIS = 60_000L;
	private static final int CARD = 32;

	/** The live treasure page (null when none). */
	private static @Nullable TreasureScreen aktif;

	private final String realm;
	private final int layer;
	private final List<ItemStack> items = new ArrayList<>();
	private final List<ItemStack> choices = new ArrayList<>();
	private final long receivedAt = Util.getMillis();
	private final KartuHasil kartu = new KartuHasil();
	private int chosen = -1;
	private boolean selesai;

	private TreasureScreen(CompoundTag treasure) {
		this.realm = treasure.getStringOr("realm", "");
		this.layer = treasure.getIntOr("layer", 0);
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			RegistryOps<Tag> ops = minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
			for (Tag tag : treasure.getListOrEmpty("items")) {
				if (tag instanceof CompoundTag entry) {
					entry.read("item", ItemStack.CODEC, ops).ifPresent(items::add);
				}
			}
			for (Tag tag : treasure.getListOrEmpty("choices")) {
				if (tag instanceof CompoundTag entry) {
					entry.read("item", ItemStack.CODEC, ops).ifPresent(choices::add);
				}
			}
		}
	}

	/** A cleared realm's treasure arrived ({@code AlamClient}, sync key {@code treasure}): it shows under Di Sini. */
	static void tampil(CompoundTag treasure) {
		TreasureScreen page = new TreasureScreen(treasure);
		aktif = page;
		DiSini.prompt("hadiah_alam", page, judul(), Glyphs.TREASURE);
	}

	/** Every client tick: a scroll left waiting past its minute leaves the tablet unless it is on screen. */
	static void tick(Minecraft minecraft) {
		TreasureScreen page = aktif;
		if (page != null && Util.getMillis() - page.receivedAt > CHOICE_MILLIS && !(minecraft.gui.screen() instanceof CultivationScreen hub && hub.section() == HubNav.DI_SINI
				&& DiSini.halaman() == page)) {
			aktif = null;
			page.selesai = true;
			DiSini.tutupPrompt(page);
		}
	}

	/** Forgets the scroll (disconnect). */
	static void reset() {
		aktif = null;
	}

	private static Component judul() {
		return Component.translatable("kultivator.alam.treasure.title");
	}

	/** True when nothing waits for the player any more (no choice, chosen, or the minute is up). */
	private boolean lewat() {
		return choices.isEmpty() || chosen >= 0 || Util.getMillis() - receivedAt > CHOICE_MILLIS;
	}

	@Override
	public Component title() {
		return judul();
	}

	@Override
	public int glyph() {
		return Glyphs.TREASURE;
	}

	@Override
	public String helpTopic() {
		return "alam_rahasia";
	}

	@Override
	public void onHide() {
		if (!selesai && aktif == this && !lewat() && !CultivationScreen.adaKonteks()) {
			// closed before choosing: the scroll waits on the tablet until its minute is up (43.8.1.5)
			CultivationScreen.konteks(this, judul(), Glyphs.TREASURE);
		} else {
			selesai = true;
			if (aktif == this) {
				aktif = null;
			}
			DiSini.lepas(this);
		}
	}

	@Override
	protected void build() {
		// Immediate page: everything is drawn and hit-tested in lukis().
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		Component name = Component.translatable("kultivator.alam.realm." + realm).withColor(Palette.opaque(RealmScenery.color(realmOrdinal())) & 0xFFFFFF);
		Component header = layer > 0 ? Component.translatable("kultivator.alam.treasure.header_layer", name, layer)
				: Component.translatable("kultivator.alam.treasure.header", name);
		List<KartuHasil.Bagian> isi = new ArrayList<>();
		for (ItemStack stack : items) {
			isi.add(new KartuHasil.Bagian(null, Component.translatable(M + "barang", stack.getCount(), stack.getHoverName()), KartuHasil.BERHASIL, 0));
		}
		if (items.isEmpty()) {
			isi.add(KartuHasil.Bagian.of(Component.translatable("kultivator.alam.treasure.none")));
		}
		int footH = 18;
		int choiceH = choices.isEmpty() ? 0 : CARD + 14;
		int cardBottom = y + h - footH - choiceH;
		kartu.gambar(l, "hadiah", Glyphs.Atlas.PIALA, header, Component.translatable("kultivator.alam.treasure.given"), null, null, isi,
				x, y, x + w, cardBottom);
		if (!choices.isEmpty()) {
			drawChoices(l, cardBottom + 2);
		}
		Component close = Component.translatable("kultivator.alam.treasure.close");
		int bw = TalismanButton.lebar(l.font, close, 72, 140);
		TalismanButton.gambar(l, "hadiah.tutup", x + (w - bw) / 2, y + h - 15, bw, 14, close, true, null, this::tutup);
	}

	/** The caption and the three seal cards (the chosen one stamped, the others greyed). */
	private void drawChoices(Lukis l, int top) {
		boolean auto = chosen < 0 && Util.getMillis() - receivedAt > CHOICE_MILLIS;
		int shown = auto ? 0 : chosen;
		Component caption = auto ? Component.translatable(M + "otomatis")
				: Component.translatable(chosen < 0 ? "kultivator.alam.treasure.choose" : "kultivator.alam.treasure.chosen");
		l.teks(caption, x + 2, top, w - 4, chosen < 0 && !auto ? l.gaya.aksen : l.gaya.teksLemah);
		int gap = 8;
		int totalW = choices.size() * CARD + (choices.size() - 1) * gap;
		int startX = x + (w - totalW) / 2;
		int cy = top + 11;
		for (int i = 0; i < choices.size(); i++) {
			int cx = startX + i * (CARD + gap);
			int index = i;
			boolean open = chosen < 0 && !auto;
			l.zona("hadiah.pilih." + i, cx, cy, cx + CARD, cy + CARD, () -> pilih(index), open, Component.translatable("kultivator.alam.treasure.chosen"), true);
			boolean over = l.di(cx, cy, cx + CARD, cy + CARD);
			boolean picked = shown == i;
			int frame = picked ? Palette.GOLD : over && open ? Palette.GOLD_LIGHT : Palette.GOLD_DARK;
			l.g.fill(cx, cy, cx + CARD, cy + CARD, Palette.argb(picked ? 0x90 : 0x50, Palette.JADE_PALE));
			Brush.frame(l.g, cx, cy, cx + CARD, cy + CARD, frame);
			Brush.frame(l.g, cx + 2, cy + 2, cx + CARD - 2, cy + CARD - 2, Palette.argb(0x80, frame));
			l.g.item(choices.get(i), cx + (CARD - 16) / 2, cy + (CARD - 16) / 2);
			l.g.itemDecorations(l.font, choices.get(i), cx + (CARD - 16) / 2, cy + (CARD - 16) / 2);
			if (picked) {
				Brush.ring(l.g, cx + CARD - 6.0F, cy + 6.0F, 3.0F, 4.0F, Palette.argb(Math.round(0x90 + 0x40 * l.denyut()), Palette.SEAL));
			} else if (shown >= 0) {
				l.g.fill(cx + 1, cy + 1, cx + CARD - 1, cy + CARD - 1, 0x60A0A0A0);
			}
			l.fokusCincin("hadiah.pilih." + i, cx, cy, cx + CARD, cy + CARD);
			if (over) {
				l.tip(Screen.getTooltipFromItem(minecraft, choices.get(i)));
			}
		}
	}

	private void pilih(int index) {
		if (chosen >= 0 || index < 0 || index >= choices.size()) {
			return;
		}
		chosen = index;
		UiSound.press();
		send(Actions.ALAM, 0, "pick:" + index);
	}

	private void tutup() {
		boolean wait = !lewat();
		if (wait) {
			// leaving before choosing: like Esc, the scroll keeps waiting on the tablet
			HubNav.tutupSemua();
			return;
		}
		selesai = true;
		aktif = null;
		boolean diSini = Minecraft.getInstance().gui.screen() instanceof CultivationScreen hub && hub.section() == HubNav.DI_SINI
				&& DiSini.halaman() == this;
		DiSini.tutupPrompt(this);
		if (diSini) {
			HubNav.tutupSemua();
		}
	}

	private int realmOrdinal() {
		return switch (realm) {
			case "blood_fire_trial" -> 1;
			case "heavenvoid_hall" -> 2;
			case "ancient_god_land" -> 3;
			default -> 4;
		};
	}
}
