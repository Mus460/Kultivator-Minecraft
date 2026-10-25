package dev.kultivator.client.dunia.dimensi;

import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Konteks;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.layar.LayarDialog;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.dimensi.DimensiNet;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.LembarKunci.Dialog;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The client bridge of module D's scrolls (DESIGN 34.5.13-14, 43.8.4, G55): {@code DimensiNet.Open} of the eleven people of
 * the underworld and the sea becomes the one conversation of the mod, {@link LayarDialog}, under the Di Sini tablet. The
 * server is unchanged (43.0.2):
 * <ul>
 * <li>scroll → sheet: the lore lines are the speaker's line (one paragraph each, paged at text 1.0), the talisman strips
 * the numbered choices (a strip that cannot be used now is a locked choice), the scroll's source the strip line above
 * the talk, the speaker card the entity's live portrait under the scroll's title. The two papers are two styles
 * ({@code Palette.Gaya}): the underworld's violet cloth ({@code kain}), the sea's jade ({@code giok});</li>
 * <li>choice → action: {@code pilih:N} sends strip N as {@link DimensiNet.Action} (the server checks it again) and
 * closes the Gulungan, as the scroll did; the Book's "Coret Namamu" asks the confirm seal first (medium: hold 1.5 s;
 * was the scroll's 2 s hold); a page turn (the Dragon King's armoury) and "go" are handled here.</li>
 * </ul>
 * Replaces {@code DimensiScreen} (now {@code .deactivation}). Client thread only.
 */
public final class DimensiJembatan {

	/** A strip whose action starts with this is handled here (a page turn, closing), never sent. */
	private static final String LOCAL = "#";

	private record Strip(Component label, String action, boolean enabled, boolean hold) {
	}

	/** One opened scroll: the payload and the page of a two-page scroll, built into heading, lines and strips. */
	private static final class Isi {
		final int screen;
		final int entity;
		final CompoundTag data;
		final boolean sea;
		int page;
		Component heading = Component.empty();
		final List<Component> lines = new ArrayList<>();
		final List<Strip> strips = new ArrayList<>();
		@Nullable Component source;

		Isi(int screen, int entity, CompoundTag data) {
			this.screen = screen;
			this.entity = entity;
			this.data = data;
			this.sea = screen == DimensiNet.SCREEN_KING || screen == DimensiNet.SCREEN_MERCHANT || screen == DimensiNet.SCREEN_PENGLAI
					|| screen == DimensiNet.SCREEN_HERMIT || screen == DimensiNet.SCREEN_GUARD || screen == DimensiNet.SCREEN_FISHER;
			build();
		}

		void turn(int page) {
			this.page = page;
			build();
		}

		void build() {
			lines.clear();
			strips.clear();
			source = null;
			heading = DimensiJembatan.build(this);
		}
	}

	private static @Nullable Isi aktif;
	private static @Nullable LayarDialog halaman;

	/** {@code DimensiNet.Open}: the scroll opens as the talk under Di Sini (a new scroll replaces the last one). */
	static void buka(DimensiNet.Open payload) {
		Isi isi = new Isi(payload.screen(), payload.entity(), payload.data());
		LayarDialog page = new LayarDialog(isi.entity, lembar(isi), op -> op(isi, op), isi.sea ? "laut_tanpa_batas" : "dunia_bawah");
		aktif = isi;
		halaman = page;
		DiSini.buka(new Konteks(Dialog.LAYAR, isi.entity, null, judul(isi), Glyphs.HUBUNGAN), page, null, Dialog.TAB_BICARA);
		UiSound.open();
	}

	/** Forgets the scroll (disconnect). */
	static void reset() {
		aktif = null;
		halaman = null;
	}

	/** The Di Sini title: "scroll title · speaker" (43.8: "Toko Senjata · Paman Li"). */
	private static Component judul(Isi isi) {
		Entity e = entitas(isi.entity);
		return e == null || e.getName().getString().equals(isi.heading.getString()) ? isi.heading
				: Component.translatable("kultivator.gui43.konteks.judul", isi.heading, e.getName());
	}

	private static @Nullable Entity entitas(int id) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? null : minecraft.level.getEntity(id);
	}

	// ================================================================== op -> action

	private static void op(Isi isi, String op) {
		String[] parts = LembarKunci.urai(op);
		if (!Dialog.OP_PILIH.equals(parts[0])) {
			// lihat (the page asks for its session again), tutup, tab: nothing to tell the server
			return;
		}
		int i = LembarKunci.argInt(parts, 1, 0) - 1;
		if (i < 0 || i >= isi.strips.size()) {
			return;
		}
		Strip strip = isi.strips.get(i);
		if (!strip.enabled()) {
			UiSound.denied();
			return;
		}
		if (strip.action().startsWith(LOCAL)) {
			String local = strip.action().substring(LOCAL.length());
			if (local.startsWith("page:")) {
				isi.turn("page:1".equals(local) ? 1 : 0);
				if (halaman != null && aktif == isi) {
					halaman.perbarui(lembar(isi), false);
				}
			} else {
				UiSound.close();
				HubNav.tutupSemua();
			}
			return;
		}
		if (ClientPlayNetworking.canSend(DimensiNet.Action.TYPE)) {
			ClientPlayNetworking.send(new DimensiNet.Action(isi.entity, strip.action()));
		}
		// as the scroll did: the choice is made, the world goes on (the server opens a new scroll when it has one)
		HubNav.tutupSemua();
	}

	// ================================================================== scroll -> sheet

	/** The {@code dialog} sheet of a scroll. */
	private static CompoundTag lembar(Isi isi) {
		CompoundTag t = new CompoundTag();
		t.putString(LembarKunci.GAYA, isi.sea ? LembarKunci.GAYA_GIOK : LembarKunci.GAYA_KAIN);
		LembarKunci.teks(t, LembarKunci.JUDUL, isi.heading);
		if (isi.source != null) {
			LembarKunci.teks(t, LembarKunci.SUBJUDUL, isi.source);
		}
		CompoundTag npc = new CompoundTag();
		Entity e = entitas(isi.entity);
		LembarKunci.teks(npc, LembarKunci.NAMA, e != null ? e.getName() : isi.heading);
		if (e != null) {
			LembarKunci.teks(npc, LembarKunci.GELAR, isi.heading);
		}
		npc.putInt(LembarKunci.POTRET, isi.entity);
		t.put(LembarKunci.NPC, npc);
		MutableComponent teks = Component.empty();
		for (int i = 0; i < isi.lines.size(); i++) {
			if (i > 0) {
				teks.append("\n");
			}
			teks.append(isi.lines.get(i));
		}
		LembarKunci.teks(t, LembarKunci.TEKS, teks);
		ListTag pilihan = new ListTag();
		for (int i = 0; i < isi.strips.size(); i++) {
			Strip s = isi.strips.get(i);
			CompoundTag p = new CompoundTag();
			p.putInt(LembarKunci.NO, i + 1);
			LembarKunci.teks(p, LembarKunci.TEKS, s.label());
			if (!s.enabled()) {
				p.putBoolean(LembarKunci.TERKUNCI, true);
			}
			if (s.hold()) {
				p.putBoolean(LayarDialog.YAKIN, true);
			}
			pilihan.add(p);
		}
		t.put(Dialog.PILIHAN, pilihan);
		return t;
	}

	private static Component t(String key, Object... args) {
		return Component.translatable("kultivator.dimensi.ui." + key, args);
	}

	/** The scroll's content (moved unchanged from {@code DimensiScreen.build}); returns the heading. */
	private static Component build(Isi s) {
		CompoundTag data = s.data;
		List<Component> lines = s.lines;
		List<Strip> strips = s.strips;
		switch (s.screen) {
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
				strips.add(new Strip(t("mengpo.btn.dao"), "soup:dao", ready && dao != null, false));
				strips.add(new Strip(t("mengpo.btn.hati"), "soup:hati", ready, false));
				strips.add(new Strip(t("mengpo.btn.teknik"), "soup:teknik", ready && !tech.isEmpty(), false));
				strips.add(new Strip(t("mengpo.btn.tolak"), "soup:tolak", true, false));
				s.source = Component.translatable("kultivator.dimensi.bawah.source");
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
						strips.add(new Strip(t("court.btn.accept"), "court:accept", true, false));
					}
					case 1 -> strips.add(new Strip(t("court.btn.duel"), "court:duel", true, false));
					case 2 -> {
						int riddle = data.getIntOr("riddle", 0);
						long wait = data.getLongOr("wait", 0L);
						lines.add(Component.translatable("kultivator.dimensi.teka." + riddle + ".q"));
						if (wait > 0L) {
							lines.add(t("court.wait", wait / 20L + 1L));
						}
						for (int i = 0; i < 3; i++) {
							strips.add(new Strip(Component.translatable("kultivator.dimensi.teka." + riddle + ".a" + i), "court:answer:" + i, wait <= 0L, false));
						}
					}
					default -> strips.add(new Strip(t(data.getBooleanOr("active", false) ? "court.btn.escort_again" : "court.btn.escort"),
							"court:escort", true, false));
				}
				lines.add(t("court.progress", n - 1, data.getIntOr("required", 10)));
				s.source = Component.translatable("kultivator.dimensi.court.source");
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
					strips.add(new Strip(t(rewarded ? "book.btn.done" : "book.btn.hadiah"), "book:hadiah", !rewarded, false));
				} else if (data.getBooleanOr("perk", true)) {
					boolean struck = data.getBooleanOr("struck", false);
					strips.add(new Strip(t(struck ? "book.btn.struck" : "book.btn.coret"), "book:coret", !struck, true));
				}
				s.source = Component.translatable("kultivator.dimensi.buku.source");
				return t("book.title");
			}
			case DimensiNet.SCREEN_GHOST -> {
				String role = data.getStringOr("peran", "pesan");
				lines.add(t("ghost." + role + ".plea"));
				lines.add(t("ghost." + role + ".how"));
				if ("tersesat".equals(role)) {
					strips.add(new Strip(t("ghost.btn.antar"), "ghost:antar", true, false));
				} else {
					boolean letter = data.getBooleanOr("letter", false);
					strips.add(new Strip(t(letter ? "ghost.btn.sudah" : "ghost.btn.pesan"), "ghost:pesan", !letter, false));
				}
				s.source = Component.translatable("kultivator.dimensi.arwah.source");
				return t("ghost.title");
			}
			case DimensiNet.SCREEN_KING -> {
				int q = data.getIntOr("q", 0);
				int price = data.getIntOr("price", 80);
				long nb = data.getLongOr("nb", 0L);
				ListTag armory = data.getListOrEmpty("armory");
				if (s.page == 1) {
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
						strips.add(new Strip(open ? name : t("king.armory.locked", name), "raja:senjata:" + i, open && nb >= cost, false));
					}
					strips.add(new Strip(t("king.btn.back"), LOCAL + "page:0", true, false));
					s.source = Component.translatable("kultivator.dimensi.raja_naga.source");
					return t("king.armory.title");
				}
				lines.add(t("king.intro"));
				lines.add(t("king.q" + Math.max(0, Math.min(5, q))));
				lines.add(t("king.nb", nb));
				strips.add(new Strip(t("king.btn.beli", price), "raja:beli", nb >= price, false));
				if (q == 0) {
					strips.add(new Strip(t("king.btn.q1"), "raja:q1", true, false));
				} else if (q == 2) {
					strips.add(new Strip(t("king.btn.lapor"), "raja:lapor", true, false));
					strips.add(new Strip(t("king.btn.q2"), "raja:q2", true, false));
				} else if (q == 3) {
					strips.add(new Strip(t("king.btn.q2_again"), "raja:q2", true, false));
				} else if (q == 4) {
					strips.add(new Strip(t("king.btn.lapor"), "raja:lapor", true, false));
				}
				if (!armory.isEmpty()) {
					strips.add(new Strip(t("king.btn.armory"), LOCAL + "page:1", true, false));
				}
				s.source = Component.translatable("kultivator.dimensi.raja_naga.source");
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
					strips.add(new Strip(known ? t("fisher.btn.seen", label) : label, "nelayan:" + places[i], wait <= 0L, false));
				}
				if (data.getBooleanOr("turtle", true)) {
					strips.add(new Strip(Component.translatable("kultivator.dimensi.nelayan.tempat.penyu"), "nelayan:penyu", true, false));
				}
				s.source = Component.translatable("kultivator.dimensi.laut.source");
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
				strips.add(new Strip(t("wuchang.btn.go"), LOCAL + "close", true, false));
				s.source = Component.translatable("kultivator.dimensi.wuchang.source");
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
					strips.add(new Strip(t("merchant.offer", offer.getIntOr("count", 1), name, price), "beli:" + i, nb >= price, false));
				}
				s.source = Component.translatable("kultivator.dimensi.laut.source");
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
				strips.add(new Strip(t("penglai.btn.uji"), "penglai:uji", worthy && !won, false));
				s.source = Component.translatable("kultivator.dimensi.penglai.source");
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

	private DimensiJembatan() {
	}
}
