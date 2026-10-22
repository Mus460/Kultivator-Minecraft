package dev.kultivator.client.dunia.dimensi;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.KeyHints;
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
 * <p>
 * The detail pass adds the old fisherman's news of the sea, Bai and Hei Wuchang's guidance on Teras Wangxiang, the
 * Dragon King's armoury (a second page of his scroll, turned here without a packet), and the Mirror's reading of the
 * soul's deeds in the wide world and its Mode Kehancuran. More than five strips are laid out in two columns.
 */
public class DimensiScreen extends Screen {

	private static final int MAX_W = 340;
	private static final int MAX_H = 250;
	private static final long HOLD_MILLIS = 2000L;

	private record Strip(Component label, String action, boolean enabled, boolean hold, @Nullable ItemStack icon) {
	}

	/** A strip whose action starts with this is handled here (a page turn, closing), never sent. */
	private static final String LOCAL = "#";

	private final int screen;
	private final int entity;
	private final CompoundTag data;
	private final boolean sea;
	/** The "?" seal over the top-right corner seal: the sea's or the underworld's topic (audit M5, S11). */
	private final KeyHints.SealCorner help;
	private Component heading;
	private final List<Component> lines = new ArrayList<>();
	private final List<Strip> strips = new ArrayList<>();
	private @Nullable Component source;
	/** The page of a two-page scroll (the Dragon King: 0 audience, 1 armoury). */
	private int page;
	private int columns = 1;

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
				|| screen == DimensiNet.SCREEN_HERMIT || screen == DimensiNet.SCREEN_GUARD || screen == DimensiNet.SCREEN_FISHER;
		this.heading = build();
		this.help = new KeyHints.SealCorner(sea ? "laut_tanpa_batas" : "dunia_bawah");
	}

	/** Turns to {@code page} (the same data, built again). */
	private void turn(int page) {
		this.page = page;
		lines.clear();
		strips.clear();
		source = null;
		heading = build();
		init();
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
						// the deeds of the wide world and the ruin the soul has wrought (Mode Kehancuran)
						ListTag deeds = karma.getListOrEmpty("deeds");
						if (deeds.isEmpty()) {
							lines.add(t("court.karma.no_deeds"));
						} else {
							List<String> names = new ArrayList<>();
							for (int i = 0; i < deeds.size(); i++) {
								String deed = deeds.getStringOr(i, "");
								if (!deed.isEmpty()) {
									names.add(Component.translatable("kultivator.dimensi.ui.court.deed." + deed.substring(deed.indexOf('/') + 1))
											.getString());
								}
							}
							lines.add(t("court.karma.deeds", deeds.size(), karma.getIntOr("deeds_max", deeds.size()), String.join(", ", names)));
						}
						lines.add(karma.getBooleanOr("hancur", false)
								? t("court.karma.hancur_on", karma.getIntOr("hancur_casts", 0), karma.getIntOr("hancur_blocks", 0))
								: t("court.karma.hancur_off", karma.getIntOr("hancur_casts", 0)));
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
				long nb = data.getLongOr("nb", 0L);
				ListTag armory = data.getListOrEmpty("armory");
				if (page == 1) {
					// the armoury of the Crystal Palace: sea-forged artifacts of the water element
					lines.add(t("king.armory.intro"));
					lines.add(t("king.nb", nb));
					lines.add(t(q >= 5 ? "king.armory.honoured" : "king.armory.guest"));
					for (int i = 0; i < armory.size(); i++) {
						CompoundTag arm = armory.getCompoundOrEmpty(i);
						boolean open = arm.getBooleanOr("open", false);
						int cost = arm.getIntOr("price", 0);
						Component name = t("king.armory.item", Component.translatable("kultivator.tempa.grade." + arm.getIntOr("grade", 3)),
								Component.translatable("kultivator.tempa.form." + arm.getStringOr("form", "pedang")), cost);
						strips.add(new Strip(open ? name : t("king.armory.locked", name), "raja:senjata:" + i, open && nb >= cost, false,
								stack("kultivator:forged_artifact", 1)));
					}
					strips.add(new Strip(t("king.btn.back"), LOCAL + "page:0", true, false, null));
					source = Component.translatable("kultivator.dimensi.raja_naga.source");
					return t("king.armory.title");
				}
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
				if (!armory.isEmpty()) {
					strips.add(new Strip(t("king.btn.armory"), LOCAL + "page:1", true, false, stack("kultivator:forged_artifact", 1)));
				}
				source = Component.translatable("kultivator.dimensi.raja_naga.source");
				return t("king.title");
			}
			case DimensiNet.SCREEN_FISHER -> {
				long wait = data.getLongOr("wait", 0L);
				int seen = data.getIntOr("seen", 0);
				int sky = data.getIntOr("sky", 0);
				lines.add(t("fisher.intro"));
				lines.add(t("fisher.seen", Integer.bitCount(seen & 15), 4));
				if (sky > 0) {
					lines.add(t("fisher.sky." + sky));
				}
				lines.add(wait > 0L ? t("fisher.wait", wait / 20L + 1L) : t("fisher.ready"));
				String[] places = {"herba", "pertapa", "pasar", "karang", "istana", "penglai"};
				for (int i = 0; i < places.length; i++) {
					boolean known = i < 4 && (seen & (1 << i)) != 0;
					Component label = Component.translatable("kultivator.dimensi.nelayan.tempat." + places[i]);
					strips.add(new Strip(known ? t("fisher.btn.seen", label) : label, "nelayan:" + places[i], wait <= 0L, false, null));
				}
				if (data.getBooleanOr("turtle", true)) {
					strips.add(new Strip(Component.translatable("kultivator.dimensi.nelayan.tempat.penyu"), "nelayan:penyu", true, false, null));
				}
				source = Component.translatable("kultivator.dimensi.laut.source");
				return t("fisher.title");
			}
			case DimensiNet.SCREEN_WUCHANG -> {
				boolean hei = data.getBooleanOr("hitam", false);
				String who = hei ? "hitam" : "putih";
				int tier = Math.max(0, Math.min(2, data.getIntOr("tier", 0)));
				lines.add(t("wuchang." + who + ".greet." + tier));
				if (data.getBooleanOr("soul", false)) {
					lines.add(t("wuchang.soul", data.getLongOr("left", 0L) / 1200L + 1L));
				} else {
					lines.add(t("wuchang.not_soul"));
				}
				long luka = data.getLongOr("luka", 0L);
				if (luka > 0L) {
					lines.add(t("wuchang.luka", luka / 1200L + 1L));
				}
				int passed = data.getIntOr("passed", 0);
				int required = data.getIntOr("required", 10);
				lines.add(t("wuchang.progress", t(data.getBooleanOr("mengpo", false) ? "wuchang.mengpo_done" : "wuchang.mengpo_not"), passed, required));
				String next = data.getStringOr("next", "mengpo");
				Component place = "court".equals(next)
						? t("wuchang.next.court", data.getIntOr("court", passed + 1), Component.translatable("kultivator.dimensi.raja." + data.getIntOr("court", passed + 1)))
						: t("wuchang.next." + next);
				String dir = data.getStringOr("dir", "");
				lines.add(dir.isEmpty() ? t("wuchang.way_unknown", place) : t("wuchang.way", place, Component.translatable(dir), data.getIntOr("dist", 0)));
				if (data.getBooleanOr("eclipse", false)) {
					lines.add(t("wuchang.eclipse"));
				}
				lines.add(t("wuchang." + who + ".parting"));
				strips.add(new Strip(t("wuchang.btn.go"), LOCAL + "close", true, false, null));
				source = Component.translatable("kultivator.dimensi.wuchang.source");
				return t("wuchang." + who + ".title");
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
		help.place(x1 - 10.5F, y0 + 10.5F);
		boxes.clear();
		int stripH = 16;
		int gap = 4;
		// more than five strips: two columns, filled row by row (the fisherman's places, the armoury)
		columns = strips.size() > 5 ? 2 : 1;
		int rows = (strips.size() + columns - 1) / columns;
		int left = x0 + 22;
		int right = x1 - 22;
		int colW = (right - left - (columns - 1) * gap) / columns;
		int[][] placed = new int[strips.size()][];
		int bottom = y1 - 16;
		for (int r = rows - 1; r >= 0; r--) {
			int top = bottom - stripH;
			for (int c = 0; c < columns; c++) {
				int i = r * columns + c;
				if (i < strips.size()) {
					int bx = left + c * (colW + gap);
					placed[i] = new int[]{bx, top, columns == 1 ? right : bx + colW, bottom};
				}
			}
			bottom = top - gap;
		}
		for (int[] box : placed) {
			boxes.add(box);
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
		help.render(g, font, mouseX, mouseY, width, height);
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
			if (help.click(mx, my)) {
				return true;
			}
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
					if (strip.action().startsWith(LOCAL)) {
						String local = strip.action().substring(LOCAL.length());
						if (local.startsWith("page:")) {
							turn("page:1".equals(local) ? 1 : 0);
						} else {
							onClose();
						}
						return true;
					}
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
		if (help.release(event.x(), event.y())) {
			return true;
		}
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
