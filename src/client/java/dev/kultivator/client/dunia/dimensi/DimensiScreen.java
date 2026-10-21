package dev.kultivator.client.dunia.dimensi;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.dimensi.DimensiNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The scrolls of module D (DESIGN 34.5.13-14): one screen, two papers. The underworld's is ink-violet with old gold
 * writing and cinnabar seals (Meng Po, the courts, the Book, the ghosts); the sea's is deep teal with pearl writing
 * (the Dragon King, the island merchant, the Penglai immortal). Each shows its lore line, what will happen, and talisman
 * strips to choose with; every choice is sent to the server ({@link DimensiNet.Action}), which checks it again (the
 * client never decides). The Book's "Coret Namamu" must be held for 2 s. Esc closes; the game never pauses.
 */
public class DimensiScreen extends Screen {

	private static final int MAX_W = 340;
	private static final int MAX_H = 250;
	private static final long HOLD_MILLIS = 2000L;

	private record Strip(Component label, String action, boolean enabled, boolean hold, @Nullable ItemStack icon) {
	}

	private final int screen;
	private final int entity;
	private final CompoundTag data;
	private final boolean sea;
	private final Component heading;
	private final List<Component> lines = new ArrayList<>();
	private final List<Strip> strips = new ArrayList<>();
	private @Nullable Component source;

	private int x0;
	private int y0;
	private int x1;
	private int y1;
	private final List<int[]> boxes = new ArrayList<>();
	private int holding = -1;
	private long holdStart;
	private boolean sent;

	public DimensiScreen(int screen, int entity, CompoundTag data) {
		super(Component.translatable("kultivator.dimensi.ui.title"));
		this.screen = screen;
		this.entity = entity;
		this.data = data;
		this.sea = screen == DimensiNet.SCREEN_KING || screen == DimensiNet.SCREEN_MERCHANT || screen == DimensiNet.SCREEN_PENGLAI
				|| screen == DimensiNet.SCREEN_HERMIT || screen == DimensiNet.SCREEN_GUARD;
		this.heading = build();
	}

	static void open(DimensiNet.Open payload) {
		Minecraft.getInstance().gui.setScreen(new DimensiScreen(payload.screen(), payload.entity(), payload.data()));
		UiSound.open();
	}

	// ================================================================== content

	private static Component t(String key, Object... args) {
		return Component.translatable("kultivator.dimensi.ui." + key, args);
	}

	private Component build() {
		switch (screen) {
			case DimensiNet.SCREEN_MENG_PO -> {
				long wait = data.getLongOr("wait", 0L);
				String daoId = data.getStringOr("dao", "");
				Dao dao = daoId.isEmpty() ? null : Dao.byId(daoId);
				lines.add(t("mengpo.intro"));
				lines.add(wait > 0L ? t("mengpo.wait", wait) : t("mengpo.ready"));
				lines.add(dao == null ? t("mengpo.no_dao") : t("mengpo.dao_now", dao.displayName(),
						String.format(java.util.Locale.ROOT, "%.1f", data.getDoubleOr("comp", 0.0D))));
				lines.add(t("mengpo.hati_now", Math.round(data.getDoubleOr("pressure", 0.0D)), data.getIntOr("deviation", 0) / 20));
				String tech = data.getStringOr("tech", "");
				lines.add(tech.isEmpty() ? t("mengpo.no_tech") : t("mengpo.tech_now", Component.translatable("kultivator.technique." + tech)));
				boolean ready = wait <= 0L;
				strips.add(new Strip(t("mengpo.btn.dao"), "soup:dao", ready && dao != null, false, null));
				strips.add(new Strip(t("mengpo.btn.hati"), "soup:hati", ready, false, null));
				strips.add(new Strip(t("mengpo.btn.teknik"), "soup:teknik", ready && !tech.isEmpty(), false, null));
				strips.add(new Strip(t("mengpo.btn.tolak"), "soup:tolak", true, false, null));
				source = Component.translatable("kultivator.dimensi.bawah.source");
				return t("mengpo.title");
			}
			case DimensiNet.SCREEN_COURT -> {
				int n = Math.max(1, Math.min(10, data.getIntOr("court", 1)));
				int trial = data.getIntOr("trial", 0);
				lines.add(t("court.trial." + trial));
				switch (trial) {
					case 0 -> {
						CompoundTag karma = data.getCompoundOrEmpty("karma");
						lines.add(t("court.karma.kills", karma.getIntOr("mobs", 0), karma.getIntOr("players", 0)));
						lines.add(t("court.karma.deaths", karma.getIntOr("deaths", 0), karma.getIntOr("days", 0)));
						lines.add(t("court.karma.realm", Realm.byOrdinal(karma.getIntOr("realm", 0)).displayName(), karma.getIntOr("tribulations", 0),
								karma.getIntOr("failed", 0)));
						lines.add(t("court.karma.heart", karma.getIntOr("demons", 0), karma.getIntOr("enlightenments", 0), karma.getIntOr("pills", 0)));
						String sect = karma.getStringOr("sect", "");
						lines.add(sect.isEmpty() ? t("court.karma.no_sect", karma.getIntOr("disciples", 0))
								: t("court.karma.sect", sect, karma.getIntOr("disciples", 0)));
						lines.add(t("court.karma.bred", karma.getIntOr("bred", 0)));
						strips.add(new Strip(t("court.btn.accept"), "court:accept", true, false, null));
					}
					case 1 -> strips.add(new Strip(t("court.btn.duel"), "court:duel", true, false, null));
					case 2 -> {
						int riddle = data.getIntOr("riddle", 0);
						long wait = data.getLongOr("wait", 0L);
						lines.add(Component.translatable("kultivator.dimensi.teka." + riddle + ".q"));
						if (wait > 0L) {
							lines.add(t("court.wait", wait / 20L + 1L));
						}
						for (int i = 0; i < 3; i++) {
							strips.add(new Strip(Component.translatable("kultivator.dimensi.teka." + riddle + ".a" + i), "court:answer:" + i,
									wait <= 0L, false, null));
						}
					}
					default -> strips.add(new Strip(t(data.getBooleanOr("active", false) ? "court.btn.escort_again" : "court.btn.escort"),
							"court:escort", true, false, null));
				}
				lines.add(t("court.progress", n - 1, data.getIntOr("required", 10)));
				source = Component.translatable("kultivator.dimensi.court.source");
				return t("court.title", n, Component.translatable("kultivator.dimensi.raja." + n));
			}
			case DimensiNet.SCREEN_BOOK -> {
				boolean immortal = data.getBooleanOr("immortal", false);
				lines.add(t("book.entry", data.getStringOr("name", "?")));
				lines.add(t("book.realm", Realm.byOrdinal(data.getIntOr("realm", 0)).displayName()));
				lines.add(t("book.days", data.getLongOr("days", 0L), data.getIntOr("passed", 0)));
				lines.add(t(immortal ? "book.immortal" : "book.mortal"));
				if (!immortal) {
					boolean rewarded = data.getBooleanOr("rewarded", false);
					strips.add(new Strip(t(rewarded ? "book.btn.done" : "book.btn.hadiah"), "book:hadiah", !rewarded, false, null));
				} else if (data.getBooleanOr("perk", true)) {
					boolean struck = data.getBooleanOr("struck", false);
					strips.add(new Strip(t(struck ? "book.btn.struck" : "book.btn.coret"), "book:coret", !struck, true, null));
				}
				source = Component.translatable("kultivator.dimensi.buku.source");
				return t("book.title");
			}
			case DimensiNet.SCREEN_GHOST -> {
				String role = data.getStringOr("peran", "pesan");
				lines.add(t("ghost." + role + ".plea"));
				lines.add(t("ghost." + role + ".how"));
				if ("tersesat".equals(role)) {
					strips.add(new Strip(t("ghost.btn.antar"), "ghost:antar", true, false, null));
				} else {
					boolean letter = data.getBooleanOr("letter", false);
					strips.add(new Strip(t(letter ? "ghost.btn.sudah" : "ghost.btn.pesan"), "ghost:pesan", !letter, false, null));
				}
				source = Component.translatable("kultivator.dimensi.arwah.source");
				return t("ghost.title");
			}
			case DimensiNet.SCREEN_KING -> {
				int q = data.getIntOr("q", 0);
				int price = data.getIntOr("price", 80);
				lines.add(t("king.intro"));
				lines.add(t("king.q" + Math.max(0, Math.min(5, q))));
				lines.add(t("king.nb", data.getLongOr("nb", 0L)));
				strips.add(new Strip(t("king.btn.beli", price), "raja:beli", data.getLongOr("nb", 0L) >= price, false,
						stack("kultivator:mutiara_naga", 1)));
				if (q == 0) {
					strips.add(new Strip(t("king.btn.q1"), "raja:q1", true, false, null));
				} else if (q == 2) {
					strips.add(new Strip(t("king.btn.lapor"), "raja:lapor", true, false, null));
					strips.add(new Strip(t("king.btn.q2"), "raja:q2", true, false, null));
				} else if (q == 3) {
					strips.add(new Strip(t("king.btn.q2_again"), "raja:q2", true, false, null));
				} else if (q == 4) {
					strips.add(new Strip(t("king.btn.lapor"), "raja:lapor", true, false, null));
				}
				source = Component.translatable("kultivator.dimensi.raja_naga.source");
				return t("king.title");
			}
			case DimensiNet.SCREEN_MERCHANT -> {
				long nb = data.getLongOr("nb", 0L);
				lines.add(t("merchant.intro"));
				lines.add(t("king.nb", nb));
				ListTag offers = data.getListOrEmpty("offers");
				for (int i = 0; i < offers.size(); i++) {
					Tag tag = offers.get(i);
					if (!(tag instanceof CompoundTag offer)) {
						continue;
					}
					ItemStack icon = stack(offer.getStringOr("id", ""), offer.getIntOr("count", 1));
					int price = offer.getIntOr("price", 1);
					Component name = icon == null ? Component.literal("?") : icon.getHoverName();
					strips.add(new Strip(t("merchant.offer", offer.getIntOr("count", 1), name, price), "beli:" + i, nb >= price, false, icon));
				}
				source = Component.translatable("kultivator.dimensi.laut.source");
				return t("merchant.title");
			}
			case DimensiNet.SCREEN_PENGLAI -> {
				boolean worthy = data.getBooleanOr("worthy", false);
				boolean won = data.getBooleanOr("won", false);
				lines.add(t("penglai.intro"));
				Realm min = Realm.byId(data.getStringOr("min", "dao_union"));
				if (!worthy) {
					lines.add(t("penglai.unworthy", min == null ? Component.literal("?") : min.displayName()));
				} else if (won) {
					lines.add(t(data.getBooleanOr("drunk", false) ? "penglai.drunk" : "penglai.won"));
				} else {
					lines.add(t("penglai.trial"));
				}
				strips.add(new Strip(t("penglai.btn.uji"), "penglai:uji", worthy && !won, false, null));
				source = Component.translatable("kultivator.dimensi.penglai.source");
				return t("penglai.title");
			}
			default -> {
				return t("title");
			}
		}
	}

	private static @Nullable ItemStack stack(String id, int count) {
		Identifier key = Identifier.tryParse(id);
		if (key == null) {
			return null;
		}
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(key);
		return item.isEmpty() || item.get() == Items.AIR ? null : new ItemStack(item.get(), Math.max(1, count));
	}

	// ================================================================== layout and drawing

	@Override
	protected void init() {
		int w = Math.min(MAX_W, width - 16);
		int h = Math.min(MAX_H, height - 12);
		x0 = (width - w) / 2;
		x1 = x0 + w;
		y0 = (height - h) / 2;
		y1 = y0 + h;
		boxes.clear();
		int stripH = 16;
		int gap = 4;
		int rows = strips.size();
		int bottom = y1 - 16;
		for (int i = rows - 1; i >= 0; i--) {
			int top = bottom - stripH;
			boxes.add(0, new int[]{x0 + 22, top, x1 - 22, bottom});
			bottom = top - gap;
		}
	}

	private int paperTop() {
		return sea ? 0xFF124650 : 0xFF221A36;
	}

	private int paperBottom() {
		return sea ? 0xFF072630 : 0xFF0E0A18;
	}

	private int ink() {
		return sea ? 0xFFE0F8FF : 0xFFE8D9A8;
	}

	private int inkSoft() {
		return sea ? 0xFFA8D8E0 : 0xFFB8AC90;
	}

	private int seal() {
		return sea ? 0xFF2FA8B8 : 0xFFB3281D;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, sea ? 0xB0021418 : 0xB00A0612, sea ? 0xD0010A0E : 0xD0050309);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		long now = Util.getMillis();
		// paper with a shadow, a double frame, rods top and bottom, seals in the corners
		g.fill(x0 + 3, y0 + 4, x1 + 3, y1 + 4, 0x70000000);
		g.fillGradient(x0, y0, x1, y1, paperTop(), paperBottom());
		Brush.frame(g, x0, y0, x1, y1, Palette.argb(0xFF, sea ? 0x0A1A20 : 0x050308));
		Brush.frame(g, x0 + 3, y0 + 3, x1 - 3, y1 - 3, Palette.argb(0x90, ink() & 0xFFFFFF));
		g.fill(x0 - 4, y0 - 3, x1 + 4, y0 + 1, sea ? 0xFF1E5A60 : Palette.ROD);
		g.fill(x0 - 4, y1 - 1, x1 + 4, y1 + 3, sea ? 0xFF1E5A60 : Palette.ROD);
		for (int i = 0; i < 4; i++) {
			int sx = i % 2 == 0 ? x0 + 6 : x1 - 15;
			int sy = i < 2 ? y0 + 6 : y1 - 15;
			g.fill(sx, sy, sx + 9, sy + 9, seal());
			Brush.frame(g, sx + 1, sy + 1, sx + 8, sy + 8, Palette.argb(0xA0, 0xF3D9C8));
		}
		// a slow drift of motes over the paper (souls or sea foam)
		for (int i = 0; i < 6; i++) {
			float phase = (now / 40L + i * 97L) % 400L / 400.0F;
			float px = x0 + 20 + ((i * 53) % Math.max(1, x1 - x0 - 40));
			float py = y1 - 20 - phase * (y1 - y0 - 40);
			Brush.dot(g, px + (float) Math.sin(phase * 6.28F + i) * 4.0F, py, 1.2F, Palette.argb(Math.round(90 * (1.0F - phase)), ink() & 0xFFFFFF));
		}
		float cx = (x0 + x1) / 2.0F;
		float room = x1 - x0 - 44.0F;
		Brush.fitCentered(g, font, Brush.bold(heading), cx, y0 + 9.0F, room, ink(), 1.1F, 0.6F, false);
		Brush.rect(g, x0 + 22.0F, y0 + 21.0F, x1 - 22.0F, y0 + 22.0F, Palette.argb(0xB0, ink() & 0xFFFFFF));
		float textTop = y0 + 27.0F;
		float textBottom = (boxes.isEmpty() ? y1 - 20 : boxes.get(0)[1]) - 14.0F;
		float scale = 0.74F;
		while (scale > 0.5F && linesHeight(room, scale) > textBottom - textTop) {
			scale -= 0.04F;
		}
		g.enableScissor(x0 + 4, Math.round(textTop), x1 - 4, Math.round(textBottom + 12.0F));
		float ty = textTop;
		for (int i = 0; i < lines.size(); i++) {
			Brush.rect(g, x0 + 16.0F, ty + 2.5F * scale, x0 + 19.0F, ty + 5.5F * scale, seal());
			ty += Brush.wrap(g, font, lines.get(i), x0 + 22.0F, ty, room, i == 0 ? ink() : inkSoft(), scale, false) + 2.0F;
		}
		if (source != null) {
			Brush.wrap(g, font, source.copy().withStyle(net.minecraft.ChatFormatting.ITALIC), x0 + 22.0F, Math.max(ty + 2.0F, textBottom - 8.0F),
					room, Palette.argb(0xA0, inkSoft() & 0xFFFFFF), 0.5F, false);
		}
		g.disableScissor();
		// talisman strips
		for (int i = 0; i < strips.size() && i < boxes.size(); i++) {
			Strip strip = strips.get(i);
			int[] b = boxes.get(i);
			boolean over = mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3];
			int top = strip.enabled() ? (over ? 0xFFF7DFA0 : Palette.TALISMAN) : Palette.LOCKED_PAPER;
			int bottom = strip.enabled() ? Palette.TALISMAN_LOW : Palette.LOCKED;
			if (sea && strip.enabled()) {
				top = over ? 0xFFD8FAFF : 0xFFB8ECF4;
				bottom = 0xFF84CCD8;
			}
			g.fillGradient(b[0], b[1], b[2], b[3], top, bottom);
			Brush.frame(g, b[0], b[1], b[2], b[3], strip.enabled() ? seal() : Palette.INK_FAINT);
			int textX = b[0] + 6;
			if (strip.icon() != null) {
				g.item(strip.icon(), b[0] + 2, b[1]);
				textX = b[0] + 20;
			}
			if (strip.hold() && holding == i) {
				float progress = Math.min(1.0F, (now - holdStart) / (float) HOLD_MILLIS);
				g.fill(b[0] + 1, b[3] - 3, b[0] + 1 + Math.round((b[2] - b[0] - 2) * progress), b[3] - 1, seal());
				if (progress >= 1.0F && !sent) {
					sent = true;
					send(strip.action());
					onClose();
					return;
				}
			}
			Brush.fit(g, font, strip.label(), textX, b[1] + 4.0F, b[2] - textX - 4.0F, strip.enabled() ? Palette.INK : Palette.INK_FAINT,
					0.8F, 0.5F, false);
		}
		Brush.fit(g, font, t("esc"), x0 + 8.0F, y1 - 10.0F, 80.0F, Palette.argb(0xA0, inkSoft() & 0xFFFFFF), 0.6F, 0.45F, false);
	}

	private float linesHeight(float room, float scale) {
		float total = 0.0F;
		for (Component line : lines) {
			total += Brush.wrapHeight(font, line, room, scale) + 2.0F;
		}
		return total;
	}

	private void send(String action) {
		if (ClientPlayNetworking.canSend(DimensiNet.Action.TYPE)) {
			ClientPlayNetworking.send(new DimensiNet.Action(entity, action));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			double mx = event.x();
			double my = event.y();
			for (int i = 0; i < strips.size() && i < boxes.size(); i++) {
				int[] b = boxes.get(i);
				if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
					Strip strip = strips.get(i);
					if (!strip.enabled()) {
						UiSound.denied();
						return true;
					}
					if (strip.hold()) {
						holding = i;
						holdStart = Util.getMillis();
						UiSound.ask();
						return true;
					}
					UiSound.press();
					send(strip.action());
					onClose();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (holding >= 0) {
			holding = -1;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		UiSound.close();
		minecraft.gui.setScreen(null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
