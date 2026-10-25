package dev.kultivator.client.sectevent;

import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.DaftarGulir;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.PemilihOrang;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.sect.event.Duties;
import dev.kultivator.sect.event.Storehouse;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Kegiatan › Tugas of the hall owner (DESIGN 20.3.9, 43.5): left, the disciples with their duties (one picked) and the
 * duty talismans (Pikul Air, Kebun Seratus Obat, Dapur Api, Peternakan Ayam Ekor Roh, release); right, the 27 slots of
 * the Lumbung Sekte (click a slot to take it), the herb refining talisman and what the chosen duty yields.
 */
public class DutyPage extends SectEventPage {

	private final PemilihOrang pemilih = new PemilihOrang(0);
	private final DaftarGulir info = new DaftarGulir();

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.duty");
	}

	@Override
	public int glyph() {
		return Glyphs.TALENT;
	}

	@Override
	public String helpTopic() {
		return "aula";
	}

	@Override
	protected void isi(Lukis l) {
		CompoundTag data = sekte();
		int colW = Math.max(120, Math.round(w * 0.47F));
		int left1 = x + colW - 2;
		int bottom = y + h;
		// ---- left: the disciples and the duty talismans
		int used = data.getCompoundOrEmpty("post_list").size();
		int top = kepala(l, Component.translatable("kultivator.sectevent.ui.posts", used, data.getIntOr("posts", 1)), x, y, left1);
		List<PemilihOrang.Orang> semua = orang(e -> true, e -> e.getBooleanOr("near", false) ? null
				: Component.translatable("kultivator.sectevent.ui.far"), e -> {
					// the short duty name always shows (it is what this list is for); "idle" only on a wide page
					String duty = e.getStringOr("duty", "");
					return !duty.isEmpty() ? Component.translatable("kultivator.sectevent.duty." + duty + ".short")
							: w >= 400 ? Component.translatable("kultivator.sectevent.ui.idle") : null;
				}, true);
		int rowsTop = bottom - 3 * (BTN_H + 2);
		pemilih.gambar(l, "murid", semua, x, top, left1, rowsTop - 3);
		CompoundTag chosen = satu(pemilih);
		int half = (left1 - x - 2) / 2;
		String[] duties = Duties.DUTIES;
		for (int i = 0; i < duties.length; i++) {
			String duty = duties[i];
			int bx = x + (i % 2) * (half + 2);
			int by = rowsTop + (i / 2) * (BTN_H + 2);
			Component full = Component.translatable("kultivator.sectevent.duty." + duty);
			Component label = font.width(full) + 24 <= half ? full : Component.translatable("kultivator.sectevent.duty." + duty + ".short");
			boolean now = chosen != null && duty.equals(chosen.getStringOr("duty", ""));
			Component why = chosen == null ? Component.translatable("kultivator.sectevent.ui.duty_hint") : alasanBaca();
			boolean over = TalismanButton.gambar(l, "tugas." + duty, bx, by, half, BTN_H, label, why == null, why, () -> {
				CompoundTag c = satu(pemilih);
				if (c != null) {
					act(c.getIntOr("id", 0), "duty:" + duty);
				}
			}, false, now);
			if (over && why == null) {
				l.tip(List.of(full.copy().withStyle(ChatFormatting.GOLD), Component.translatable("kultivator.sectevent.duty." + duty + ".desc"),
						Component.translatable("kultivator.sectevent.duty." + duty + ".source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			}
		}
		boolean bebas = chosen != null && !chosen.getStringOr("duty", "").isEmpty();
		TalismanButton.gambar(l, "lepas", x, rowsTop + 2 * (BTN_H + 2), left1 - x, BTN_H, Component.translatable("kultivator.sectevent.ui.release_duty"),
				bebas && !readonly(), bebas ? alasanBaca() : Component.translatable("kultivator.gui43.m09.tanpa_tugas"), () -> {
					CompoundTag c = satu(pemilih);
					if (c != null) {
						act(c.getIntOr("id", 0), "duty_off:" + c.getStringOr("soul", ""));
					}
				});
		// ---- right: the storehouse, refining and the duty's yield
		int rx = x + colW + 2;
		int rx1 = x + w;
		int ry = kepala(l, Component.translatable("kultivator.sectevent.ui.store"), rx, y, rx1);
		int slot = Math.max(16, Math.min(18, (rx1 - rx) / 9));
		int gx = rx + (rx1 - rx - slot * 9) / 2;
		lumbung(l, gx, ry, slot);
		int refineY = ry + slot * 3 + 4;
		int herbs = data.getIntOr("herbs", 0);
		boolean bisa = herbs >= Duties.HERBS_PER_PILL;
		Component kurang = Component.translatable("kultivator.gui43.m09.herba_kurang", herbs, Duties.HERBS_PER_PILL);
		boolean over = TalismanButton.gambar(l, "murnikan", rx, refineY, rx1 - rx, BTN_H,
				Component.translatable("kultivator.sectevent.ui.refine", herbs, Duties.HERBS_PER_PILL), bisa && !readonly(),
				bisa ? alasanBaca() : kurang, () -> act(0, "refine"));
		if (over && bisa) {
			l.tip(List.of(Component.translatable("kultivator.sectevent.ui.refine.tip"),
					Component.translatable("kultivator.sectevent.ui.refine.odds").withStyle(ChatFormatting.GRAY),
					Component.translatable("kultivator.sectevent.item.foundation_herb.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
		}
		int infoY = refineY + BTN_H + 4;
		l.sumur(rx, infoY, rx1, bottom);
		gulungan(l, "hasil", info, hasil(chosen), rx + 3, infoY + 2, rx1 - 2, bottom - 2);
	}

	/** The 27 slots of the Lumbung Sekte; a click takes the stack. */
	private void lumbung(Lukis l, int gx, int gy, int slot) {
		ItemStack[] store = SectEventClient.store();
		for (int i = 0; i < Storehouse.SLOTS; i++) {
			int sx = gx + i % 9 * slot;
			int sy = gy + i / 9 * slot;
			boolean over = l.di(sx, sy, sx + slot, sy + slot);
			l.g.fill(sx, sy, sx + slot - 1, sy + slot - 1, over ? Palette.argb(0x70, Palette.JADE_PALE) : Palette.argb(0x48, Palette.PAPER_DEEP));
			Brush.frame(l.g, sx, sy, sx + slot - 1, sy + slot - 1, Palette.argb(0x90, Palette.GOLD_DARK));
			ItemStack stack = store[i];
			int index = i;
			l.zona("lumbung#" + i, sx, sy, sx + slot - 1, sy + slot - 1, () -> {
				act(0, "take:" + index);
				UiSound.press();
			}, !stack.isEmpty() && !readonly(), alasanBaca(), false);
			if (over && !stack.isEmpty()) {
				l.tip(List.of(stack.getHoverName(), Component.translatable("kultivator.sectevent.ui.store.count", stack.getCount()).withStyle(ChatFormatting.GRAY),
						Component.translatable("kultivator.sectevent.ui.store.take").withStyle(ChatFormatting.DARK_AQUA)));
			}
		}
		// items on their own layer so they sit on top of the slots; always full size (the count stays at text 1.0)
		l.g.nextStratum();
		int inset = Math.max(0, (slot - 17) / 2);
		for (int i = 0; i < Storehouse.SLOTS; i++) {
			ItemStack stack = store[i];
			if (stack.isEmpty()) {
				continue;
			}
			int sx = gx + i % 9 * slot + inset;
			int sy = gy + i / 9 * slot + inset;
			l.g.item(stack, sx, sy);
			l.g.itemDecorations(font, stack, sx, sy);
		}
	}

	/** What the chosen disciple's duty yields, the chickens for the farm, the Pikul Air body and the meal. */
	private List<Garis> hasil(CompoundTag chosen) {
		CompoundTag data = sekte();
		List<Garis> list = new ArrayList<>();
		if (chosen == null) {
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.duty_hint"), Palette.INK_SOFT));
		} else {
			String duty = chosen.getStringOr("duty", "");
			if (duty.isEmpty()) {
				list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.duty_pick", SectEventClient.name(chosen, "name")), Palette.INK_SOFT));
			} else {
				list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.duty_now", SectEventClient.name(chosen, "name"),
						Component.translatable("kultivator.sectevent.duty." + duty)), Palette.JADE_DARK));
				list.add(Garis.teks(Component.translatable("kultivator.sectevent.duty." + duty + ".desc"), Palette.INK_SOFT));
			}
			list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.trained", chosen.getIntOr("trained_hp", 0), Duties.WATER_HP_MAX),
					Palette.INK_SOFT));
			long meal = chosen.getLongOr("meal", 0L) - now();
			if (meal > 0L) {
				list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.meal", clock(meal)), Palette.onPaper(0xE5821C)));
			}
		}
		int chickens = data.getIntOr("chickens", 0);
		list.add(Garis.teks(Component.translatable("kultivator.sectevent.ui.chickens", chickens, Duties.FARM_CHICKENS),
				chickens >= Duties.FARM_CHICKENS ? Palette.JADE_DARK : Palette.DANGER));
		return list;
	}
}
