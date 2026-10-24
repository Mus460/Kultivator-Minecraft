package dev.kultivator.client.sekte.mandiri;

import dev.kultivator.sekte.mandiri.api.EntriHarian;
import dev.kultivator.sekte.mandiri.api.JalanHidup;
import dev.kultivator.sekte.mandiri.api.Kebijakan;
import dev.kultivator.sekte.mandiri.api.ModeMandiri;
import dev.kultivator.sekte.mandiri.cerita.MandiriPayloads;
import dev.kultivator.sekte.mandiri.cerita.PilihVarian;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One diary page ({@code mandiri_harian}, built by {@code BukuHarian.halaman}) read once into plain fields, with every
 * entry already composed by {@link HarianTeks} (newest first) and every travel scroll ("Gulungan Perjalanan", 39.8.2)
 * turned into its lines. The tab and the book share the parse (cached per payload object). Client only.
 */
final class Halaman {

	/** A goal card. */
	record Cita(String jenis, int prog, int prio, String asal, long hari, Component target) {
	}

	/** A shopping list row. */
	record Belanja(String item, int n, int prio, String alasan, long maksHarga, String ke) {
	}

	/** One of the three strongest ties. */
	record Ikatan(String jenis, int afinitas, Component nama) {
	}

	/** A named character this disciple met. */
	record Tokoh(String nama, String peran, int sikap, int jumpa, String watak) {
	}

	/** A travel scroll: kind, destination, day, success and its story lines. */
	record Gulungan(String jenis, String tujuan, long hari, boolean sukses, List<Component> baris) {
	}

	final String soul;
	final boolean own;
	final boolean muat;
	final int eid;
	final long hari;
	final int jam;
	final Component nama;
	final @Nullable JalanHidup jalan;
	final List<String> alasan = new ArrayList<>();
	final int iblis;
	final List<String> sifat = new ArrayList<>();
	final List<Cita> cita = new ArrayList<>();
	final String citaUtama;
	final int[] riwayat;
	final int[] kemarin;
	final long dompet;
	final long dompetMaks;
	final int sakuPersen;
	final long sakuHarian;
	final List<Belanja> belanja = new ArrayList<>();
	final List<Ikatan> ikatan = new ArrayList<>();
	final List<Tokoh> tokoh = new ArrayList<>();
	final @Nullable Kebijakan kebijakan;
	final boolean punyaTumpang;
	final int rank;
	final List<ItemStack> tas;
	final List<HarianTeks.Tersusun> entri = new ArrayList<>();
	final List<Gulungan> gulungan = new ArrayList<>();

	private static final Map<MandiriPayloads.Harian, Halaman> CACHE = new IdentityHashMap<>();

	static @Nullable Halaman dari(@Nullable MandiriPayloads.Harian page) {
		if (page == null) {
			return null;
		}
		Halaman known = CACHE.get(page);
		if (known != null) {
			return known;
		}
		if (CACHE.size() > 8) {
			CACHE.clear();
		}
		Halaman made = new Halaman(page);
		CACHE.put(page, made);
		return made;
	}

	private Halaman(MandiriPayloads.Harian page) {
		CompoundTag d = page.data();
		soul = d.getStringOr("soul", "");
		own = d.getBooleanOr("own", false);
		muat = d.getBooleanOr("muat", false);
		eid = d.getIntOr("eid", -1);
		hari = d.getLongOr("hari", 0L);
		jam = d.getIntOr("jam", -1);
		nama = d.read("nama", ComponentSerialization.CODEC).orElse(Component.literal("?"));
		jalan = JalanHidup.byId(d.getStringOr("jl", ""));
		ListTag al = d.getListOrEmpty("al");
		for (int i = 0; i < al.size(); i++) {
			alasan.add(al.getStringOr(i, ""));
		}
		iblis = d.getIntOr("ib", 0);
		ListTag sf = d.getListOrEmpty("sf");
		for (int i = 0; i < sf.size(); i++) {
			sifat.add(sf.getStringOr(i, ""));
		}
		ListTag ct = d.getListOrEmpty("cita");
		for (int i = 0; i < ct.size(); i++) {
			CompoundTag c = ct.getCompoundOrEmpty(i);
			cita.add(new Cita(c.getStringOr("j", ""), c.getIntOr("p", 0), c.getIntOr("r", 3), c.getStringOr("a", ""), c.getLongOr("h", 0L),
					c.read("t", ComponentSerialization.CODEC).orElse(Component.empty())));
		}
		cita.sort((a, b) -> Integer.compare(a.prio(), b.prio()));
		citaUtama = d.getStringOr("ct", "");
		riwayat = strip(d.getIntArray("rw").orElse(null));
		kemarin = strip(d.getIntArray("km").orElse(null));
		dompet = d.getLongOr("dp", 0L);
		dompetMaks = d.getLongOr("dm", 0L);
		sakuPersen = d.getIntOr("us", 100);
		sakuHarian = d.getLongOr("sk", 0L);
		ListTag bl = d.getListOrEmpty("bl");
		for (int i = 0; i < bl.size(); i++) {
			CompoundTag b = bl.getCompoundOrEmpty(i);
			belanja.add(new Belanja(b.getStringOr("item", ""), b.getIntOr("n", 1), b.getIntOr("prio", 3), b.getStringOr("alasan", ""),
					b.getLongOr("maksHarga", 0L), b.getStringOr("ke", "")));
		}
		ListTag ik = d.getListOrEmpty("ik");
		for (int i = 0; i < ik.size(); i++) {
			CompoundTag t = ik.getCompoundOrEmpty(i);
			ikatan.add(new Ikatan(t.getStringOr("k", ""), t.getIntOr("a", 0), t.read("n", ComponentSerialization.CODEC).orElse(Component.literal("?"))));
		}
		ListTag tk = d.getListOrEmpty("tk");
		for (int i = 0; i < tk.size(); i++) {
			CompoundTag t = tk.getCompoundOrEmpty(i);
			tokoh.add(new Tokoh(t.getStringOr("n", "?"), t.getStringOr("p", ""), t.getIntOr("s", 0), t.getIntOr("j", 0), t.getStringOr("w", "")));
		}
		CompoundTag kb = d.getCompoundOrEmpty("kb");
		kebijakan = kb.isEmpty() ? null : Kebijakan.fromTag(kb, Kebijakan.bawaan(ModeMandiri.SEIMBANG, 2, 3));
		punyaTumpang = !d.getCompoundOrEmpty("tm").isEmpty();
		rank = d.getIntOr("rank", 0);
		tas = new ArrayList<>(page.tas());
		tas.removeIf(ItemStack::isEmpty);

		UUID id = null;
		try {
			id = UUID.fromString(soul);
		} catch (IllegalArgumentException ignored) {
			// a page without a soul composes with seed 0
		}
		HarianTeks.Penulis penulis = new HarianTeks.Penulis(id, sifat, citaUtama);
		ListTag e = d.getListOrEmpty("e");
		List<EntriHarian> raw = new ArrayList<>();
		for (int i = 0; i < e.size(); i++) {
			raw.add(EntriHarian.fromTag(e.getCompoundOrEmpty(i)));
		}
		raw.sort((a, b) -> a.hari() != b.hari() ? Long.compare(b.hari(), a.hari()) : Integer.compare(b.jam(), a.jam()));
		for (EntriHarian one : raw) {
			try {
				entri.add(HarianTeks.susun(one, penulis));
			} catch (RuntimeException ignored) {
				// one broken entry never hides the page
			}
		}
		ListTag g = d.getListOrEmpty("g");
		for (int i = g.size() - 1; i >= 0; i--) {
			CompoundTag s = g.getCompoundOrEmpty(i);
			List<Component> lines = new ArrayList<>();
			ListTag ls = s.getListOrEmpty("s");
			long hari = s.getLongOr("h", 0L);
			for (int j = 0; j < ls.size(); j++) {
				lines.add(baris(ls.getStringOr(j, ""), penulis.soul(), hari, j));
			}
			gulungan.add(new Gulungan(s.getStringOr("j", ""), s.getStringOr("t", ""), s.getLongOr("h", 0L), s.getBooleanOr("ok", true), lines));
		}
	}

	private static int[] strip(int[] a) {
		int[] out = new int[24];
		if (a != null) {
			System.arraycopy(a, 0, out, 0, Math.min(24, a.length));
		}
		return out;
	}

	/**
	 * One stored story line ({@code key|arg|arg}, a bare key, or literal text) as a component. A key with variants
	 * ({@code key.0 .. key.n}) reads as one of them, seeded by (soul, day, stage) so a scroll always reads the same.
	 */
	static Component baris(String line, @Nullable UUID soul, long hari, int tahap) {
		if (line == null || line.isEmpty()) {
			return Component.empty();
		}
		int bar = line.indexOf('|');
		String key = bar < 0 ? line : line.substring(0, bar);
		if (key.startsWith("kultivator.") && key.indexOf(' ') < 0 && HarianTeks.varian(key) > 0) {
			long benih = PilihVarian.benih(soul, hari, key, tahap);
			line = key + "." + PilihVarian.indeks(benih, PilihVarian.INTI, HarianTeks.varian(key)) + (bar < 0 ? "" : line.substring(bar));
		}
		Object made = HarianTeks.arg(line);
		return made instanceof Component c ? c : Component.literal(String.valueOf(made));
	}
}
