package dev.kultivator.client.dunia.kapal;

import dev.kultivator.client.dunia.peta.PetaScreen;
import dev.kultivator.client.dunia.peta.PetaUi;
import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Konteks;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Bilah;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.InkField;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.Palette;
import dev.kultivator.client.screen.ui.TalismanButton;
import dev.kultivator.client.screen.ui.GulirIsi;
import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.kapal.KapalNet;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.dunia.kapal.KapalStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Di Sini › Kemudi, the ship's helm (DESIGN 34.5.15, 43.8.3, module M12 of 43.16.2): a page under the Di Sini tablet while
 * the player stands at the helm, opened by sneak + use on the ship or the inventory key while seated. It shows the hull,
 * fuel, riders and disciples, speed and state; the hold, repair, "Simpan" (back into the keel) and stop; the banner colour,
 * the hull livery (five, three earned) and the ship's name (owner only). The old voyage list is gone (G58): "Pilih tujuan
 * di Peta" opens Dunia › Peta in sail mode, where the sect hall and the routes are rows and every marker can be sailed to;
 * after the choice the Gulungan comes back here (R13). The spirit weather line is gone too (G57: the {@code LangitHud} chip
 * and the map's layer show it). Responsive (R8): two columns from 270 px, else one scrolled column. Every action is a
 * request the server checks.
 */
public final class HelmScreen extends Page {

	/** The Di Sini context id of the helm (not a sheet of 38). */
	public static final String KONTEKS = "kemudi";
	private static final String K = "kultivator.kapal.helm.";
	private static final String M = "kultivator.gui43.m12.";
	private static final int SW = 16;
	private static final int SH = 12;

	private final int ship;
	private CompoundTag info;
	private final InkField nama;
	private final GulirIsi gulir = new GulirIsi();

	private HelmScreen(int ship, CompoundTag info) {
		this.ship = ship;
		this.info = info;
		this.nama = new InkField(0, 0, 80, 14, KapalRohEntity.NAME_MAX, text -> simpanNama());
		nama.hint(Component.translatable(K + "name.hint"));
		nama.setText(info.getStringOr("name", ""));
	}

	/** The helm page under the Di Sini tablet now, or null (no helm context). */
	public static @Nullable HelmScreen terbuka() {
		return DiSini.halaman() instanceof HelmScreen h ? h : null;
	}

	/** {@code KapalNet.Info}: refreshes the helm page of that ship, or opens it as the Di Sini context. */
	static void onInfo(KapalNet.Info payload) {
		HelmScreen open = terbuka();
		if (open != null && open.ship == payload.ship()) {
			open.terima(payload.info());
			if (payload.open()) {
				HubNav.buka(Tujuan.bagian(HubNav.DI_SINI));
			}
			return;
		}
		if (payload.open() && Minecraft.getInstance().player != null) {
			HelmScreen page = new HelmScreen(payload.ship(), payload.info());
			DiSini.buka(new Konteks(KONTEKS, payload.ship(), page.judulKonteks(), Glyphs.PATH), page, null, "");
		}
	}

	private void terima(CompoundTag next) {
		info = next;
		if (!nama.focused()) {
			nama.setText(next.getStringOr("name", ""));
		}
	}

	private Component judulKonteks() {
		String n = info.getStringOr("name", "");
		Component kapal = n.isEmpty() ? Component.translatable("kultivator.kapal.class." + KapalClass.byOrdinal(info.getIntOr("class", 0)).model())
				: Component.literal(n);
		return Component.translatable("kultivator.gui43.konteks.judul", title(), kapal);
	}

	// ================================================================== for the map's sail mode (R13)

	/** The helm info snapshot (hall, routes, captain). */
	public CompoundTag info() {
		return info;
	}

	/** True when the player may choose a voyage (seated at the helm). */
	public boolean kapten() {
		return info.getBooleanOr("captain", false);
	}

	/** Sends helm op {@code op} for this ship (the server checks it). */
	public void perintah(int op, int arg, String text) {
		KapalClient.helm(ship, op, arg, text);
	}

	/** The name of a route row of the helm info ({@code routes}). */
	public static Component namaRute(CompoundTag route) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			try {
				return route.read("name", ComponentSerialization.CODEC, minecraft.level.registryAccess().createSerializationContext(NbtOps.INSTANCE))
						.orElse(Component.literal(route.getStringOr("id", "?")));
			} catch (RuntimeException ignored) {
				// fall through to the id
			}
		}
		return Component.literal(route.getStringOr("id", "?"));
	}

	// ================================================================== identity and life

	@Override
	public Component title() {
		return Component.translatable(K + "title");
	}

	@Override
	public int glyph() {
		return Glyphs.PATH;
	}

	@Override
	public String helpTopic() {
		return "kapal_terbang";
	}

	@Override
	protected void build() {
	}

	@Override
	public void onHide() {
		// shown as the context: the Gulungan closed or another context replaced it
		DiSini.lepas(this);
	}

	@Override
	protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
	}

	// ================================================================== drawing

	@Override
	protected void lukis(Lukis l) {
		int x0 = x + 4;
		int x1 = x + w - 9;
		int top = gulir.mulai(l, x0, y + 2, x1, y + h - 2);
		int akhir;
		if (w >= 270) {
			int mid = (x0 + x1) / 2;
			akhir = Math.max(kiri(l, x0, top, mid - 5), kanan(l, mid + 5, top, x1));
		} else {
			akhir = kanan(l, x0, kiri(l, x0, top, x1) + 4, x1);
		}
		gulir.selesai(l, akhir);
	}

	/** Name, owner, the ship's numbers and the voyage. Returns the y below. */
	private int kiri(Lukis l, int x0, int y, int x1) {
		int lebar = x1 - x0;
		KapalClass kind = KapalClass.byOrdinal(info.getIntOr("class", 0));
		l.judul(Component.translatable("kultivator.kapal.class." + kind.model()), x0, y, lebar, Palette.SEAL_DARK);
		y += 15;
		String shipName = info.getStringOr("name", "");
		if (!shipName.isEmpty()) {
			l.teks(Component.literal("«" + shipName + "»"), x0, y, lebar, Palette.GOLD_DARK);
			y += 10;
		}
		l.samar(Component.translatable(K + "owner", info.getStringOr("owner", "?")), x0, y, lebar);
		y += 12;
		y += l.kepala(Component.translatable(M + "keadaan"), x0, y, x1);
		int hull = info.getIntOr("hull", 0);
		int max = Math.max(1, info.getIntOr("hull_max", 1));
		float ratio = hull / (float) max;
		y += Bilah.baris(l, Component.translatable(M + "lambung"), hull, max, x0, y, x1, null,
				ratio > 0.5F ? 0xFF2E8963 : ratio > 0.25F ? 0xFFE5821C : 0xFFD5322A);
		l.teks(Component.translatable(K + "fuel", PetaUi.duration(info.getLongOr("fuel", 0L) / 20L)), x0, y, lebar, l.gaya.teks);
		y += 10;
		l.teks(Component.translatable(K + "riders", info.getIntOr("riders", 0), info.getIntOr("seats", 0), info.getIntOr("disciples", 0)), x0, y, lebar,
				l.gaya.teks);
		y += 10;
		l.teks(Component.translatable(K + "speed", info.getIntOr("speed_max", 0)), x0, y, lebar, l.gaya.teksLemah);
		y += 10;
		String dest = info.getStringOr("dest", "");
		Component status = dest.isEmpty() ? Component.translatable(K + "status", stateName(info))
				: Component.translatable(K + "status_dest", stateName(info), destName(dest));
		y += Math.round(l.bungkus(status, x0, y, lebar, Palette.JADE_DARK, 27.0F)) + 4;
		y += l.kepala(Component.translatable(K + "voyage"), x0, y, x1);
		boolean captain = kapten();
		y += Math.round(l.bungkus(Component.translatable(captain ? K + "voyage.sub" : K + "voyage.not_captain"), x0, y, lebar, l.gaya.teksSamar, 27.0F)) + 2;
		TalismanButton.gambar(l, "peta", x0, y, lebar, 14, Component.translatable(M + "pilih_peta"), captain,
				Component.translatable(K + "voyage.not_captain"), PetaScreen::berlayar, captain, false);
		return y + 16;
	}

	/** The helm actions and the ship's looks. Returns the y below. */
	private int kanan(Lukis l, int x0, int y, int x1) {
		boolean owner = info.getBooleanOr("is_owner", false);
		boolean docked = info.getBooleanOr("docked", false);
		int state = info.getIntOr("state", 0);
		y += l.kepala(Component.translatable(M + "aksi"), x0, y, x1);
		int bw = (x1 - x0 - 4) / 2;
		TalismanButton.gambar(l, "muatan", x0, y, bw, 14, Component.translatable(K + "hold"), true, null, () -> perintah(KapalRohEntity.OP_HOLD, 0, ""));
		int repair = info.getIntOr("repair", 0);
		if (TalismanButton.gambar(l, "perbaiki", x0 + bw + 4, y, bw, 14, Component.translatable(K + "repair"), repair > 0,
				Component.translatable("kultivator.kapal.repair.full"), () -> perintah(KapalRohEntity.OP_REPAIR, 0, "")) && repair > 0) {
			l.tip(Component.translatable(K + "repair.tip", repair * KapalRohEntity.REPAIR_PLANKS, repair));
		}
		y += 17;
		if (TalismanButton.gambar(l, "simpan", x0, y, bw, 14, Component.translatable(K + "stow"), owner,
				Component.translatable("kultivator.kapal.stow.owner_only"), () -> perintah(KapalRohEntity.OP_STOW, 0, "")) && owner) {
			l.tip(Component.translatable(docked ? K + "stow.tip" : "kultivator.kapal.stow.land_first"));
		}
		boolean sailing = !info.getStringOr("dest", "").isEmpty() || state == KapalRohEntity.STATE_AUTO || state == KapalRohEntity.STATE_LEAP;
		TalismanButton.gambar(l, "henti", x0 + bw + 4, y, bw, 14, Component.translatable(K + "stop"), sailing, Component.translatable(M + "tak_berlayar"),
				() -> perintah(KapalRohEntity.OP_STOP, 0, ""));
		y += 19;
		// banner colours
		y += l.kepala(Component.translatable(K + "colour"), x0, y, x1);
		int current = info.getIntOr("color", KapalRohEntity.DEFAULT_COLOR);
		int sx = x0;
		for (int i = 0; i < KapalRohEntity.COLORS.length; i++) {
			if (sx + SW > x1) {
				sx = x0;
				y += SH + 4;
			}
			int index = i;
			int rgb = KapalRohEntity.COLORS[i];
			swatch(l, "warna:" + i, sx, y, 0xFF000000 | rgb, (rgb & 0xFFFFFF) == (current & 0xFFFFFF), true, null,
					() -> perintah(KapalRohEntity.OP_COLOR, index, ""));
			sx += SW + 3;
		}
		y += SH + 6;
		// hull liveries (some earned)
		KapalStyle styleNow = KapalStyle.byOrdinal(info.getIntOr("style", 0));
		y += l.kepala(Component.translatable(K + "style").append(": ").append(Component.translatable("kultivator.kapal.style." + styleNow.id())), x0, y, x1);
		int open = info.getIntOr("styles_open", 1);
		sx = x0;
		for (KapalStyle style : KapalStyle.values()) {
			if (sx + SW > x1) {
				sx = x0;
				y += SH + 4;
			}
			int index = style.ordinal();
			boolean earned = (open & (1 << index)) != 0;
			Component locked = Component.translatable("kultivator.kapal.style.locked." + style.unlock().name().toLowerCase(Locale.ROOT));
			Component reason = !owner ? Component.translatable("kultivator.kapal.stow.owner_only") : earned ? null : locked;
			if (swatch(l, "gaya:" + index, sx, y, 0xFF000000 | KapalLivery.swatch(style), index == styleNow.ordinal(), owner && earned, reason,
					() -> perintah(KapalRohEntity.OP_STYLE, index, ""))) {
				List<Component> lines = new ArrayList<>();
				lines.add(Brush.bold(Component.translatable("kultivator.kapal.style." + style.id())));
				lines.add(Component.translatable("kultivator.kapal.style.desc." + style.id()).withStyle(ChatFormatting.GRAY));
				lines.add(locked.copy().withStyle(earned ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
				l.tip(lines);
			}
			sx += SW + 3;
		}
		y += SH + 6;
		// the ship's name (owner only)
		y += l.kepala(Component.translatable(K + "name"), x0, y, x1);
		int saveW = Math.min(56, TalismanButton.lebar(l.font, Component.translatable(K + "name.save"), 40, 56));
		if (owner) {
			nama.x = x0;
			nama.y = y;
			nama.w = Math.max(TalismanButton.MIN_W, x1 - x0 - saveW - 4);
			nama.h = 14;
			l.widget("nama", nama);
		} else {
			String shipName = info.getStringOr("name", "");
			l.samar(shipName.isEmpty() ? Component.literal("—") : Component.literal(shipName), x0, y + 3, x1 - x0 - saveW - 4);
		}
		if (TalismanButton.gambar(l, "namai", x1 - saveW, y, saveW, 14, Component.translatable(K + "name.save"), owner,
				Component.translatable("kultivator.kapal.stow.owner_only"), this::simpanNama) && owner) {
			l.tip(Component.translatable(K + "name.tip"));
		}
		return y + 16;
	}

	/** One colour / livery swatch (16x12, a focusable zone); returns true when hovered. */
	private boolean swatch(Lukis l, String kunci, int sx, int sy, int argb, boolean selected, boolean aktif, @Nullable Component alasan, Runnable aksi) {
		l.g.fill(sx, sy, sx + SW, sy + SH, argb);
		if (!aktif) {
			// locked: veiled, with a seal bar across
			l.g.fill(sx, sy, sx + SW, sy + SH, 0x99101010);
			l.g.fill(sx + 2, sy + SH / 2, sx + SW - 2, sy + SH / 2 + 1, Palette.argb(0xC0, Palette.SEAL));
		}
		boolean over = l.di(sx, sy, sx + SW, sy + SH);
		Brush.frame(l.g, sx - 1, sy - 1, sx + SW + 1, sy + SH + 1, selected ? Palette.GOLD : over ? Palette.GOLD_LIGHT : Palette.argb(0x80, Palette.INK));
		l.zona(kunci, sx, sy, sx + SW, sy + SH, aksi, aktif, alasan, true);
		l.fokusCincin(kunci, sx, sy, sx + SW, sy + SH);
		return over;
	}

	private void simpanNama() {
		if (!info.getBooleanOr("is_owner", false)) {
			return;
		}
		perintah(KapalRohEntity.OP_RENAME, 0, nama.text().strip());
		nama.setFocused(false);
	}

	private static Component stateName(CompoundTag info) {
		if (info.getBooleanOr("docked", false)) {
			return Component.translatable("kultivator.kapal.state.docked");
		}
		return Component.translatable("kultivator.kapal.state." + switch (info.getIntOr("state", 0)) {
			case KapalRohEntity.STATE_FLY -> "fly";
			case KapalRohEntity.STATE_AUTO -> "auto";
			case KapalRohEntity.STATE_LEAP -> "leap";
			case KapalRohEntity.STATE_BROKEN -> "broken";
			case KapalRohEntity.STATE_HALT -> "halt";
			default -> "idle";
		});
	}

	private static Component destName(String dest) {
		String[] parts = dest.split(" ", 3);
		if (parts.length > 2 && !parts[2].isBlank()) {
			return Component.literal(parts[2]);
		}
		return parts.length >= 2 ? Component.translatable("kultivator.kapal.dest.point", parts[0], parts[1]) : Component.literal(dest);
	}

	// ================================================================== input

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || gulir.roda(mouseX, mouseY, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return super.keyPressed(event) || !mauTombol() && gulir.tombol(event);
	}
}
