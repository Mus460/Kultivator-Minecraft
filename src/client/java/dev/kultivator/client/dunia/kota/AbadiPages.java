package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.dunia.DuniaScreen;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.dunia.kota.NpcRole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * The four tablets of the Immortal City screen (DESIGN 34.5.4): Pasar Abadi (wares in Batu Asal Abadi), Kuliah Dao (the
 * noon lecture), Kebun Persik (the peach every few days) and Titah Utusan (the weekly edict). Each NPC serves its own
 * tablet; the others show where to go.
 */
final class AbadiPages {

	static NpcRole role() {
		return NpcRole.byOrdinal(KotaClient.data().getIntOr("role", 0));
	}

	// ================================================================== Pasar Abadi

	static final class Pasar extends KotaPage {
		private long built = -1L;

		Pasar(DuniaScreen screen) {
			super(screen);
		}

		@Override
		public Component title() {
			return Component.translatable("kultivator.kota.tab.pasar_abadi");
		}

		@Override
		public Component shortName() {
			return Component.translatable("kultivator.kota.tab.pasar_abadi.short");
		}

		@Override
		public int icon() {
			return Glyphs.TREASURE;
		}

		@Override
		public void onData() {
			if (built != KotaClient.version()) {
				layout(x, y, w, h);
			}
		}

		@Override
		protected void build() {
			built = KotaClient.version();
			if (role() != NpcRole.PEDAGANG_ABADI) {
				return;
			}
			ListTag market = all().getListOrEmpty("market");
			int columns = w >= 240 ? 2 : 1;
			int cw = (w - 4 - (columns - 1) * 4) / columns;
			for (int i = 0; i < market.size(); i++) {
				if (!(market.get(i) instanceof CompoundTag offer)) {
					continue;
				}
				int cx = x + 2 + (i % columns) * (cw + 4);
				int cy = y + 24 + (i / columns) * 24;
				if (cy + 21 > y + h) {
					break;
				}
				int index = offer.getIntOr("i", -1);
				int price = offer.getIntOr("price", 0);
				boolean rare = offer.getBooleanOr("rare", false);
				Card card = add(new Card(cx, cy, cw, 21, () -> icon(offer.getStringOr("item", "")), () -> read(offer, "name"),
						() -> Component.translatable("kultivator.kota.abadi.harga", price), () -> rare));
				card.enabledWhen(() -> all().getIntOr("origin", 0) >= price);
				card.onPress(() -> act("abeli:" + index + ":" + price));
				card.tooltip(() -> lines(read(offer, "name"), Component.translatable(rare ? "kultivator.kota.abadi.langka" : "kultivator.kota.abadi.biasa", price)));
			}
		}

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
			abadiHeader(g, Component.translatable("kultivator.kota.abadi.pasar_judul", read(all(), "town")));
			if (role() != NpcRole.PEDAGANG_ABADI) {
				elsewhere(g, Component.translatable("kultivator.kota.abadi.ke_pasar"), y + 24);
			}
		}
	}

	// ================================================================== Kuliah Dao

	static final class Kuliah extends KotaPage {
		Kuliah(DuniaScreen screen) {
			super(screen);
		}

		@Override
		public Component title() {
			return Component.translatable("kultivator.kota.tab.kuliah");
		}

		@Override
		public Component shortName() {
			return Component.translatable("kultivator.kota.tab.kuliah.short");
		}

		@Override
		public int icon() {
			return Glyphs.ROOT_DAO;
		}

		@Override
		protected void build() {
		}

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
			CompoundTag data = all();
			abadiHeader(g, title());
			int top = y + 26;
			panel(g, x + 2, top, x + w - 2, top + 64);
			Component state;
			if (data.getBooleanOr("lecture_done", false)) {
				state = Component.translatable("kultivator.kota.kuliah.sudah");
			} else if (data.getBooleanOr("lecture_now", false)) {
				state = Component.translatable("kultivator.kota.kuliah.sekarang");
			} else {
				state = Component.translatable("kultivator.kota.kuliah.nanti", clock(data.getLongOr("lecture_in", 0L)));
			}
			Brush.fit(g, font, Brush.bold(state), x + 8.0F, top + 5.0F, w - 16.0F, Palette.SEAL_DARK, 0.75F, 0.45F, false);
			Brush.wrap(g, font, Component.translatable("kultivator.kota.kuliah.cara"), x + 8.0F, top + 16.0F, w - 16.0F, Palette.INK_SOFT, 0.62F, false);
			int listened = data.getIntOr("listened", 0);
			bar(g, x + 8, top + 50, x + w - 8, top + 56, listened / 60.0F, Palette.JADE);
			Brush.fit(g, font, Component.translatable("kultivator.kota.kuliah.detik", listened, 60), x + 8.0F, top + 41.0F, w - 16.0F, Palette.JADE_DARK, 0.6F,
					0.4F, false);
			// the lecturer's seal: a slowly turning Dao circle
			float cx = x + w / 2.0F;
			float cy = top + 92.0F;
			if (cy + 16.0F < y + h) {
				long t = net.minecraft.util.Util.getMillis();
				for (int i = 0; i < 8; i++) {
					double a = t / 2400.0D + i * Math.PI / 4.0D;
					Glyphs.trigram(g, cx + (float) Math.cos(a) * 18.0F, cy + (float) Math.sin(a) * 12.0F, 7.0F, 1.0F, 2.0F, i, Palette.argb(0xB0, Palette.GOLD_DARK));
				}
				Brush.halo(g, cx, cy, 5.0F, 6.0F, 0xFFE8A8, 0.4F);
			}
		}
	}

	// ================================================================== Kebun Persik

	static final class Persik extends KotaPage {
		Persik(DuniaScreen screen) {
			super(screen);
		}

		@Override
		public Component title() {
			return Component.translatable("kultivator.kota.tab.persik");
		}

		@Override
		public Component shortName() {
			return Component.translatable("kultivator.kota.tab.persik.short");
		}

		@Override
		public int icon() {
			return Glyphs.TALENT;
		}

		@Override
		protected void build() {
			int bw = Math.min(140, w - 8);
			TalismanButton pick = add(new TalismanButton(x + (w - bw) / 2, y + h - 22, bw, 16, () -> Component.translatable("kultivator.kota.persik.tombol")));
			pick.enabledWhen(() -> role() == NpcRole.PENJAGA_KEBUN && all().getLongOr("peach_in", 1L) <= 0L);
			pick.urgentWhen(() -> role() == NpcRole.PENJAGA_KEBUN && all().getLongOr("peach_in", 1L) <= 0L);
			pick.onPress(() -> act("persik"));
		}

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
			CompoundTag data = all();
			abadiHeader(g, title());
			Brush.wrap(g, font, Component.translatable("kultivator.kota.persik.isi", data.getIntOr("peach_days", 3)), x + 6.0F, y + 26.0F, w - 12.0F,
					Palette.INK_SOFT, 0.66F, false);
			long wait = data.getLongOr("peach_in", 0L);
			Component state = wait <= 0L ? Component.translatable("kultivator.kota.persik.siap") : Component.translatable("kultivator.kota.persik.tunggu", wait);
			Brush.fitCentered(g, font, Brush.bold(state), x + w / 2.0F, y + h - 34.0F, w - 8.0F, wait <= 0L ? Palette.JADE_DARK : Palette.SEAL_DARK, 0.75F,
					0.45F, false);
			// a peach branch: blossoms on a dark twig
			float bx = x + w / 2.0F;
			float by = y + 62.0F;
			if (by + 20.0F < y + h - 36.0F) {
				Brush.line(g, bx - 40.0F, by + 14.0F, bx + 40.0F, by - 6.0F, 2.0F, 0xFF4A2A2A);
				for (int i = 0; i < 6; i++) {
					float px = bx - 32.0F + i * 13.0F;
					float py = by + 11.0F - i * 3.4F + (i % 2 == 0 ? -4.0F : 4.0F);
					Brush.disc(g, px, py, 3.2F, 0xFFF4A8C0);
					Brush.disc(g, px, py, 1.2F, 0xFFFFE070);
				}
				Brush.disc(g, bx + 10.0F, by + 10.0F, 6.0F, 0xFFF08A8A);
				Brush.halo(g, bx + 10.0F, by + 10.0F, 6.0F, 4.0F, 0xFFC0C0, 0.4F);
			}
			if (role() != NpcRole.PENJAGA_KEBUN) {
				Brush.fit(g, font, Component.translatable("kultivator.kota.persik.ke_kebun"), x + 4.0F, y + h - 44.0F, w - 8.0F, Palette.INK_FAINT, 0.6F, 0.4F, false);
			}
		}
	}

	// ================================================================== Titah Utusan

	static final class Titah extends KotaPage {
		static final String[] EDICTS = {"batu", "mutiara", "purba", "musuh"};

		Titah(DuniaScreen screen) {
			super(screen);
		}

		@Override
		public Component title() {
			return Component.translatable("kultivator.kota.tab.titah");
		}

		@Override
		public Component shortName() {
			return Component.translatable("kultivator.kota.tab.titah.short");
		}

		@Override
		public int icon() {
			return Glyphs.SEAL;
		}

		@Override
		protected void build() {
			int bw = Math.min(110, (w - 12) / 2);
			TalismanButton take = add(new TalismanButton(x + 4, y + h - 20, bw, 15, () -> Component.translatable("kultivator.kota.titah.ambil")));
			take.enabledWhen(() -> role() == NpcRole.UTUSAN && all().getBooleanOr("edict_open", true));
			take.onPress(() -> act("titah:ambil"));
			TalismanButton give = add(new TalismanButton(x + w - bw - 4, y + h - 20, bw, 15, () -> Component.translatable("kultivator.kota.titah.serah")));
			give.enabledWhen(() -> role() == NpcRole.UTUSAN && all().contains("edict") && !all().getBooleanOr("edict_paid", false));
			give.urgentWhen(() -> all().getBooleanOr("edict_done", false) && !all().getBooleanOr("edict_paid", false));
			give.onPress(() -> act("titah:serah"));
		}

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
			CompoundTag data = all();
			abadiHeader(g, title());
			int top = y + 26;
			// the edict: a yellow imperial scroll with a red seal
			g.fillGradient(x + 6, top, x + w - 6, top + 70, 0xF0F0D890, 0xF0D8B860);
			Brush.frame(g, x + 6, top, x + w - 6, top + 70, Palette.GOLD_DARK);
			g.fill(x + w - 24, top + 52, x + w - 12, top + 64, Palette.SEAL);
			if (!data.contains("edict")) {
				Brush.wrap(g, font, Component.translatable("kultivator.kota.titah.kosong"), x + 12.0F, top + 8.0F, w - 24.0F, Palette.INK_SOFT, 0.66F, false);
				return;
			}
			int kind = Math.max(0, Math.min(3, data.getIntOr("edict", 0)));
			Brush.fit(g, font, Brush.bold(Component.translatable("kultivator.kota.titah.jenis." + EDICTS[kind])), x + 12.0F, top + 6.0F, w - 24.0F,
					Palette.SEAL_DARK, 0.75F, 0.45F, false);
			Brush.wrap(g, font, Component.translatable("kultivator.kota.titah.isi." + EDICTS[kind], data.getIntOr("edict_need", 1)), x + 12.0F, top + 17.0F,
					w - 40.0F, Palette.INK, 0.6F, false);
			int n = data.getIntOr("edict_n", 0);
			int need = Math.max(1, data.getIntOr("edict_need", 1));
			if (kind >= 2) {
				bar(g, x + 12, top + 46, x + w - 32, top + 50, data.getBooleanOr("edict_done", false) ? 1.0F : n / (float) need, 0xFFC8902A);
			}
			Component state = data.getBooleanOr("edict_paid", false) ? Component.translatable("kultivator.kota.titah.dibayar")
					: data.getBooleanOr("edict_done", false) ? Component.translatable("kultivator.kota.titah.siap", data.getIntOr("edict_pay", 0))
					: Component.translatable("kultivator.kota.titah.upah", data.getIntOr("edict_pay", 0));
			Brush.fit(g, font, state, x + 12.0F, top + 56.0F, w - 44.0F, Palette.GOLD_DARK, 0.62F, 0.4F, false);
		}
	}

	private AbadiPages() {
	}
}
