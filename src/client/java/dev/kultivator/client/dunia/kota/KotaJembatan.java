package dev.kultivator.client.dunia.kota;

import dev.kultivator.client.hub.DiSini;
import dev.kultivator.client.hub.HubNav;
import dev.kultivator.client.hub.Konteks;
import dev.kultivator.client.hub.Tujuan;
import dev.kultivator.client.screen.Page;
import dev.kultivator.client.screen.ui.Brush;
import dev.kultivator.client.screen.ui.Glyphs;
import dev.kultivator.client.screen.ui.Lukis;
import dev.kultivator.client.screen.ui.SegelKonfirmasi;
import dev.kultivator.dunia.kota.KotaNet;
import dev.kultivator.dunia.kota.NpcRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client bridge of module K (DESIGN 43.8.4, module M15 of 43.16.2): {@link KotaNet.Screen} (screen kind, tab, data)
 * becomes one page under the Di Sini tablet of the Gulungan Utama instead of the three old screens ({@code KotaScreens}):
 * the town (subs Toko · Lelang · Teh · Gelap · Misi), the Immortal City (Pasar · Kuliah · Persik · Perintah) and the
 * wanderer meeting. Every button sends the same {@link KotaNet.Action} ops as before; the server is unchanged. A live
 * refresh updates the page in place, an open on the same NPC switches its sub, and the context ends when the NPC is gone
 * or the player walked away ({@link #JAUH} blocks; the auction streams to watchers up to 20 blocks from the stage).
 * The subs are drawn with the single kit ({@code screen/ui}) at text scale 1.0.
 */
public final class KotaJembatan {

	/** The Di Sini context id of module K (not a sheet of 38). */
	static final String KONTEKS = "kota_k";
	static final String M = "kultivator.gui43.m15.";
	/** Beyond this distance from the NPC the context ends (the old screen never closed; 20 = the auction stage). */
	private static final double JAUH = 24.0D;
	private static final Map<String, ItemStack> IKON = new HashMap<>();

	/** One sub of the page: its id, its coin name and its drawing into the content box. */
	interface Isi {
		String id();

		Component nama();

		void lukis(Halaman p, Lukis l, int x0, int y0, int x1, int y1);

		default boolean roda(double mx, double my, double dy) {
			return false;
		}

		default boolean tombol(KeyEvent event) {
			return false;
		}
	}

	/** A {@link KotaNet.Screen} other than the rival list: refresh, retab or open the Di Sini page. */
	static void terima(KotaNet.Screen payload) {
		if (KotaLelang.terima(payload)) {
			// a Juru Lelang: the one auction sheet (G20)
			return;
		}
		int jenis = jenis(payload.screen());
		Halaman open = DiSini.halaman() instanceof Halaman h && h.npcId == payload.npcId() && h.jenis == jenis ? h : null;
		if (!payload.open() && open == null) {
			// a live refresh for a page that is not shown (the auction streams to everyone near the stage)
			return;
		}
		KotaClient.simpan(payload);
		if (open != null) {
			// the same NPC opened again: bring Di Sini to the front, on the sub the server asked for
			if (payload.open()) {
				HubNav.buka(Tujuan.bagian(HubNav.DI_SINI).sub(open.subDari(payload.tab())));
			}
			return;
		}
		Halaman page = new Halaman(jenis, payload.npcId());
		DiSini.buka(new Konteks(KONTEKS, payload.npcId(), null, page.judulKonteks(), page.glyph()), page, null, page.subDari(payload.tab()));
	}

	private static int jenis(int screen) {
		return screen == KotaNet.SCREEN_ABADI || screen == KotaNet.SCREEN_PERTEMUAN ? screen : KotaNet.SCREEN_KOTA;
	}

	// ================================================================== shared helpers of the subs

	static CompoundTag semua() {
		return KotaClient.data();
	}

	static CompoundTag tab(String key) {
		return KotaClient.tab(key);
	}

	static NpcRole peran() {
		return NpcRole.byOrdinal(semua().getIntOr("role", 0));
	}

	static void kirim(String op) {
		KotaClient.send(op);
	}

	static Component teks(CompoundTag tag, String key) {
		return tag.read(key, ComponentSerialization.CODEC).orElse(Component.empty());
	}

	/** An item icon by id ("kultivator:foundation_pill"), made once per id (never in a static initialiser). */
	static ItemStack ikon(String id) {
		return IKON.computeIfAbsent(id, key -> {
			Identifier parsed = Identifier.tryParse(key);
			Item item = parsed == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(parsed);
			return item == Items.AIR ? new ItemStack(Items.PAPER) : new ItemStack(item);
		});
	}

	/** Ticks as {@code m:ss}. */
	static String jam(long ticks) {
		long seconds = (Math.max(0L, ticks) + 19L) / 20L;
		return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	/** The town line under a sub's title: Nama Baik and the discount / raid marks. Returns the height used. */
	static int barisKota(Lukis l, int x0, int y, int x1) {
		CompoundTag data = semua();
		Component line = Component.translatable(M + "nama_baik", data.getIntOr("rep", 0));
		if (data.getBooleanOr("discount", false)) {
			line = Component.translatable("kultivator.kota.layar.sep", line, Component.translatable("kultivator.kota.layar.diskon"));
		}
		if (data.getBooleanOr("raided", false)) {
			line = Component.translatable("kultivator.kota.layar.sep", line, Component.translatable("kultivator.kota.layar.razia")
					.withStyle(style -> style.withColor(0xC02020)));
		}
		l.samar(line, x0, y, x1 - x0);
		return 11;
	}

	/** The Immortal City's purse line (Batu Asal Abadi, "debu fana" for mortals). Returns the height used. */
	static int barisAbadi(Lukis l, int x0, int y, int x1) {
		Component line = Component.translatable("kultivator.kota.abadi.dompet", semua().getIntOr("origin", 0));
		if (!semua().getBooleanOr("immortal", false)) {
			line = Component.translatable("kultivator.kota.layar.sep", line, Component.translatable("kultivator.kota.abadi.fana"));
		}
		l.samar(line, x0, y, x1 - x0);
		return 11;
	}

	/** A sub's title in bold accent. Returns the height used. */
	static int judul(Lukis l, Component t, int x0, int y, int x1) {
		l.teks(Brush.bold(t), x0, y, x1 - x0, l.gaya.aksen);
		return 11;
	}

	// ================================================================== the page

	/** The Di Sini page of one K NPC: the subs of its screen kind (none for the meeting). */
	static final class Halaman extends Page {
		final int jenis;
		final int npcId;
		private final List<Isi> isi;
		private Isi tampil;

		Halaman(int jenis, int npcId) {
			this.jenis = jenis;
			this.npcId = npcId;
			this.isi = switch (jenis) {
				case KotaNet.SCREEN_ABADI -> List.of(new AbadiPages.Pasar(), new AbadiPages.Kuliah(), new AbadiPages.Persik(), new AbadiPages.Titah());
				case KotaNet.SCREEN_PERTEMUAN -> List.of(new MeetingPage());
				default -> List.of(new TownPages.Toko(), new TownPages.Lelang(), new TownPages.Teh(), new TownPages.Gelap(), new TownPages.Misi());
			};
			this.tampil = isi.get(0);
		}

		/** The sub id of server tab {@code tab} ("" = keep the current one: a refresh sends -1). */
		String subDari(int tab) {
			int index = jenis == KotaNet.SCREEN_ABADI ? tab - KotaNet.TAB_PASAR_ABADI : jenis == KotaNet.SCREEN_KOTA ? tab : -1;
			return index >= 0 && index < isi.size() && isi.size() > 1 ? isi.get(index).id() : "";
		}

		Component judulKonteks() {
			Entity entity = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(npcId);
			return entity == null ? title() : Component.translatable("kultivator.gui43.konteks.judul", title(), entity.getName());
		}

		/** A medium confirm seal (hold 1.5 s) over the page. */
		void yakin(Component teks, Runnable ya) {
			konfirmasi(SegelKonfirmasi.Tingkat.SEDANG, teks, ya);
		}

		@Override
		public Component title() {
			return Component.translatable(switch (jenis) {
				case KotaNet.SCREEN_ABADI -> "kultivator.kota.layar.judul_abadi";
				case KotaNet.SCREEN_PERTEMUAN -> "kultivator.kota.layar.judul_pertemuan";
				default -> "kultivator.kota.layar.judul";
			});
		}

		@Override
		public int glyph() {
			return jenis == KotaNet.SCREEN_ABADI ? Glyphs.TREASURE : jenis == KotaNet.SCREEN_PERTEMUAN ? Glyphs.PATH : Glyphs.HALL;
		}

		@Override
		public String helpTopic() {
			return jenis == KotaNet.SCREEN_ABADI ? "kota_abadi" : jenis == KotaNet.SCREEN_PERTEMUAN ? "pengembara" : "kota";
		}

		@Override
		public List<Sub> subs() {
			if (isi.size() < 2) {
				return List.of();
			}
			return isi.stream().map(s -> new Sub(s.id(), s.nama())).toList();
		}

		@Override
		public void showSub(String id) {
			for (Isi s : isi) {
				if (s.id().equals(id)) {
					tampil = s;
				}
			}
		}

		@Override
		public long uang() {
			return jenis == KotaNet.SCREEN_KOTA ? semua().getLongOr("nb", 0L) : -1L;
		}

		@Override
		protected void build() {
		}

		@Override
		public void onShow(@Nullable CompoundTag fokus) {
			// shown again after another section: ask for a fresh view ("lihat" answers with a refresh); only within the
			// action reach, else the server would print "too far" to an auction watcher
			Minecraft minecraft = Minecraft.getInstance();
			Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(npcId);
			if (entity != null && minecraft.player != null && minecraft.player.distanceToSqr(entity) <= KotaNet.REACH * KotaNet.REACH) {
				kirim("lihat");
			}
			super.onShow(fokus);
		}

		@Override
		public void onHide() {
			// the Gulungan closed or another context replaced this one; K has no session to close on the server
			DiSini.lepas(this);
		}

		@Override
		public void tick() {
			Minecraft minecraft = Minecraft.getInstance();
			Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(npcId);
			if (DiSini.halaman() == this && (entity == null || !entity.isAlive() || minecraft.player == null
					|| minecraft.player.distanceToSqr(entity) > JAUH * JAUH)) {
				DiSini.selesai(Component.translatable("kultivator.gui43.konteks.berakhir"));
			}
		}

		@Override
		protected void drawBehind(GuiGraphicsExtractor g, int mouseX, int mouseY, float seconds) {
		}

		@Override
		protected void lukis(Lukis l) {
			tampil.lukis(this, l, x + 4, y + 3, x + w - 4, y + h - 3);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || tampil.roda(mouseX, mouseY, scrollY);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			return super.keyPressed(event) || !mauTombol() && tampil.tombol(event);
		}
	}

	private KotaJembatan() {
	}
}
