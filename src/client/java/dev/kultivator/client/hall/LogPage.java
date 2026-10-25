package dev.kultivator.client.hall;

import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Kronik;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.Saring;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sekte › Ikhtisar › Catatan (DESIGN 20.3.1, 43.11 G37): the one chronicle of the sect, newest first under day headings
 * ({@code Kronik}, 50 entries): the hall's own log plus the chronicles other modules add with
 * {@link HallClient#sumberCatatan} (kabar murid, langit, diplomasi, formasi). A {@code Saring} row filters by text and by
 * kind (one chip per source, only when there is more than one). The hall's lines are kind {@code aula}.
 */
public class LogPage extends Page {

	static final String JENIS_AULA = "aula";

	private final Saring saring = new Saring(() -> kronik().mulaiUlang(net.minecraft.util.Util.getMillis()));
	private @Nullable Kronik kronik;
	private int jenisTerpasang = -1;

	private Kronik kronik() {
		if (kronik == null) {
			kronik = new Kronik(saring);
		}
		return kronik;
	}

	@Override
	public Component title() {
		return Component.translatable("kultivator.spine6.page.log");
	}

	@Override
	public Component shortName() {
		return Component.translatable("kultivator.spine6.page.log.short");
	}

	@Override
	public int glyph() {
		return Glyphs.HELP;
	}

	@Override
	public String helpTopic() {
		return "aula";
	}

	@Override
	protected void build() {
	}

	@Override
	public void onShow(@Nullable CompoundTag fokus) {
		HallClient.minta();
		// the disciples' news source reads the independent-disciple board
		dev.kultivator.client.sekte.mandiri.MandiriBoardScreen.minta();
		super.onShow(fokus);
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	@Override
	protected void lukis(Lukis l) {
		List<HallClient.SumberCatatan> sumber = HallClient.sumberCatatan();
		boolean aula = HallClient.adaAula();
		int jenis = sumber.size() + (aula ? 1 : 0);
		if (jenis != jenisTerpasang) {
			jenisTerpasang = jenis;
			List<Saring.Pilihan> chips = new ArrayList<>();
			if (jenis > 1) {
				if (aula) {
					chips.add(new Saring.Pilihan(JENIS_AULA, Component.translatable("kultivator.gui43.m05.catatan.aula")));
				}
				for (HallClient.SumberCatatan s : sumber) {
					chips.add(new Saring.Pilihan(s.jenis(), s.nama()));
				}
			}
			saring.jenis(chips);
		}
		CompoundTag info = HallClient.info();
		List<Kronik.Entri> entri = new ArrayList<>();
		if (aula) {
			ListTag log = info.getListOrEmpty("log");
			for (int i = 0; i < log.size(); i++) {
				CompoundTag e = log.getCompoundOrEmpty(i);
				entri.add(new Kronik.Entri(e.read("text", ComponentSerialization.CODEC).orElse(Component.empty()), e.getLongOr("day", 0L), JENIS_AULA,
						Palette.SEAL));
			}
		}
		for (HallClient.SumberCatatan s : sumber) {
			try {
				entri.addAll(s.entri().get());
			} catch (RuntimeException ignored) {
				// a broken source must not blank the whole chronicle
			}
		}
		// oldest first by day (stable: within a day each source keeps its own order)
		entri.sort(Comparator.comparingLong(Kronik.Entri::hari));
		long today = info.getLongOr("day", net.minecraft.client.Minecraft.getInstance().level == null ? 0L
				: Math.floorDiv(net.minecraft.client.Minecraft.getInstance().level.getOverworldClockTime(), 24000L));
		kronik().gambar(l, "catatan", entri, today, x, y, x + w, y + h);
	}
}
