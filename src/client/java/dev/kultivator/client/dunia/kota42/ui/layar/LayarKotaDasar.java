package dev.kultivator.client.dunia.kota42.ui.layar;

import dev.kultivator.client.screen.Page.Tata;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.UiSound;
import dev.kultivator.client.tempat.ui.layar.LayarInti;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.dunia.kota42.ui.KotaLembar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import java.util.Set;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base of the 42U-1 sheets ({@link LayarInti} on the single kit): tabs that are server jumps to another sheet (Buku
 * Kota's Acara, Harga, Milikku, Kavling), row selection that works in a remote session ({@code tab:TAB:ARG}) as well as at
 * the counter ({@code pilih:ID}), the "datangi tempatnya" reason on writes of a remote session, the town strip, and a
 * "Tunjuk" button that sets the 5-minute marker.
 */
abstract class LayarKotaDasar extends LayarInti {

	protected LayarKotaDasar(String layar, int npcId, CompoundTag lembar, Component judul, String gayaId) {
		super(layar, npcId, lembar, judul, gayaId);
	}

	/** Tabs whose click only asks the server to open another sheet (the tab itself does not change). */
	protected Set<String> lompat() {
		return Set.of();
	}

	@Override
	protected void gantiTab(String id, boolean kirimOp) {
		if (kirimOp && lompat().contains(id)) {
			UiSound.tab();
			kirim(LembarKunci.op(LembarKunci.OP_TAB, id));
			return;
		}
		super.gantiTab(id, kirimOp);
	}

	/**
	 * The scrolled column of the current tab (null = the tab scrolls nothing itself). The wheel and the keys Up Down PgUp
	 * PgDn Home End go to it, so a tab drawn inside a {@link GulirIsi} works by keyboard at every GUI scale.
	 */
	protected @Nullable GulirIsi gulirTab() {
		return null;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		GulirIsi g = gulirTab();
		if (g != null && g.roda(mouseX, mouseY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	protected boolean tombolLain(KeyEvent event) {
		GulirIsi g = gulirTab();
		return g != null && g.tombol(event);
	}

	/** The sentence of the confirm seal for a dangerous button ("Hold to confirm: ..."). */
	protected static Component yakinTeks(Component label) {
		return Component.translatable("kultivator.gui43.konteks.yakin", label);
	}

	/** True in the narrow layout (box under 320 wide): one column, lists over details. */
	protected boolean sempit() {
		return tata == Tata.SEMPIT;
	}

	/** True when this page cannot write: a hub view, or a sheet the server flagged remote. */
	protected boolean jauh() {
		return hanyaBaca() || lembar.b(KotaLembar.JAUH);
	}

	/** A remote sheet (flagged by the server, or a hub view) cannot write; the server would drop the op anyway. */
	@Override
	protected boolean tulisBoleh() {
		return !jauh();
	}

	@Override
	protected Component alasanBaca() {
		Component dimana = lembar.teksAtau(KotaLembar.DI_MANA);
		return dimana == null ? KotaGambar.t("jauh") : KotaGambar.t("jauh.di", dimana);
	}

	/** Selects row {@code id}: {@code pilih:ID} at the counter, {@code tab:TAB:ID} in a remote session. */
	protected void pilihBaris(String tabId, String id) {
		if (jauh()) {
			kirim(LembarKunci.op(LembarKunci.OP_TAB, tabId, id));
		} else {
			kirim(LembarKunci.op(KotaLembar.OP_PILIH, id));
		}
	}

	/** The "Tunjuk" button: the server marks (x, z) for five minutes. A read verb, so it works remotely. */
	protected void tunjuk(Lukis l, String kunci, int x, int y, int w, int h, int tx, int tz) {
		tombolOp(l, kunci, x, y, w, h, KotaGambar.t("tunjuk"), tx != 0 || tz != 0, null, LembarKunci.op(LembarKunci.OP_TANDAI, tx, tz));
	}

	/** A write talisman with a custom reason; the reason of a remote session wins. */
	protected boolean tulis(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			String op) {
		return tombolOp(l, kunci, x, y, w, h, label, aktif, alasan, op);
	}

	/** A plain talisman running a client action (no op). */
	protected boolean aksi(Lukis l, String kunci, int x, int y, int w, int h, Component label, boolean aktif, @Nullable Component alasan,
			Runnable jalan) {
		return TalismanButton.gambar(l, kunci, x, y, w, h, label, aktif, alasan, jalan);
	}

	protected int lebarTombol(Lukis l, Component label, int min, int max) {
		return TalismanButton.lebar(l.font, label, min, max);
	}
}
