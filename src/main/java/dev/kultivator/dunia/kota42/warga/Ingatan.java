package dev.kultivator.dunia.kota42.warga;

import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kenangan;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What one resident remembers about one player (DESIGN 42.4.10): at most {@code kotaWarga.ingatanMaks} (6) per resident,
 * least recently met dropped first. The player is stored as an index into {@code WargaData}'s UUID table ({@link #pemain}).
 * Compound keys of 42.13.1: {@code P} player index, {@code k} times met, {@code n} knows the name, {@code s} impression,
 * {@code K} the strong part of the impression (never drifts), {@code g} impression earned by greetings (at most 20),
 * {@code d} last greeting day, {@code b} help count, {@code u} debt (NB, + resident owes the player), {@code h} last gift
 * day, {@code m} last met day, {@code e} the four remembered events (each {@code kenangan ordinal + 1 << 24 | day & 0xFFFFFF},
 * 0 = empty).
 * <p>
 * Behaviour (42A-2): {@link #peristiwa} turns what a player did into impressions, strong memories and gossip (the table of
 * 42.4.10); {@link #harian} lets the soft part drift back to 0 by 1 every 4 days; {@link #wariskan} gives each child half of
 * a dead parent's impression ("my father often told of the master who put out that fire"); {@link #faktorHarga} and
 * {@link #lapor} are the effects other modules read.
 */
public final class Ingatan {

	/** Remembered events per player. */
	public static final int PERISTIWA = 4;
	/** Most impression greetings can earn. */
	public static final int SAPA_MAKS = 20;
	/** Days between two gifts that count. */
	public static final int HADIAH_JEDA = 3;
	/** Days per drift step of the soft impression. */
	public static final int LURUH_HARI = 4;
	/** Impression from which a resident does not report a small offence it saw (42.4.10). */
	public static final int KESAN_TUTUP_MATA = 60;

	/** Index into {@code WargaData.pemain}. */
	public int pemain;
	/** Times met, 0-99 (moved here from {@code PendudukData.kenal}). */
	public int kenal;
	/** Knows the player's name (introduced, title or Nama Baik >= 60 there, or told by gossip). */
	public boolean nama;
	/** Personal impression -100..100 (strong part included). */
	public int kesan;
	/** The part of {@link #kesan} that came from strong events (it never drifts). */
	public int kesanKuat;
	/** Impression earned by greetings so far (capped at {@link #SAPA_MAKS}). */
	public int dariSapa;
	/** World day of the last greeting that counted. */
	public long sapaHari = -1L;
	/** Help count (requests done, rescues). */
	public int bantu;
	/** Debt in NB: positive = the resident owes the player, negative = the player owes the resident. */
	public int utang;
	/** World day of the last meaningful gift (one per 3 days counts). */
	public long hadiahHari = -HADIAH_JEDA;
	/** World day last met (LRU order). */
	public long temuHari;
	/** Remembered events, packed {@code kenangan << 24 | (day & 0xFFFFFF)}; 0 = empty. */
	public final int[] peristiwa = new int[PERISTIWA];

	public Ingatan(int pemain) {
		this.pemain = pemain;
	}

	public void setKesan(int value) {
		kesan = Mth.clamp(value, -100, 100);
	}

	public void tambahKenal() {
		kenal = Math.min(99, kenal + 1);
	}

	/**
	 * Changes the impression by {@code delta}; a strong {@code kenangan} (or {@code kuat}) also moves the strong part and is
	 * remembered with day {@code hari}.
	 */
	public void ubah(int delta, @Nullable Kenangan kenangan, boolean kuat, long hari) {
		setKesan(kesan + delta);
		if (kuat || kenangan != null && kenangan.kuat) {
			kesanKuat = Mth.clamp(kesanKuat + delta, -100, 100);
		}
		if (kenangan != null) {
			ingat(kenangan, hari);
		}
		temuHari = Math.max(temuHari, hari);
	}

	/** A greeting on day {@code hari}: met once more and +1 impression, once a day, at most 20 in all. True if it counted. */
	public boolean sapa(long hari) {
		temuHari = Math.max(temuHari, hari);
		if (sapaHari == hari) {
			return false;
		}
		sapaHari = hari;
		tambahKenal();
		if (dariSapa < SAPA_MAKS) {
			dariSapa++;
			setKesan(kesan + 1);
		}
		return true;
	}

	/** True when a gift on day {@code hari} counts (one per {@value #HADIAH_JEDA} days); marks the day when it does. */
	public boolean hadiahBoleh(long hari) {
		if (hari - hadiahHari < HADIAH_JEDA) {
			return false;
		}
		hadiahHari = hari;
		return true;
	}

	/** One drift step: the soft part of the impression moves 1 toward 0 (the strong part stays). */
	public void luruh() {
		int lunak = kesan - kesanKuat;
		if (lunak > 0) {
			setKesan(kesan - 1);
		} else if (lunak < 0) {
			setKesan(kesan + 1);
		}
	}

	/** Remembers {@code kenangan} on {@code hari}: fills an empty slot, else replaces the weakest (smallest |delta|, oldest). */
	public void ingat(Kenangan kenangan, long hari) {
		int packed = (kenangan.ordinal() + 1) << 24 | (int) (hari & 0xFFFFFFL);
		int slot = -1;
		long weakest = Long.MAX_VALUE;
		for (int i = 0; i < PERISTIWA; i++) {
			if (peristiwa[i] == 0) {
				slot = i;
				break;
			}
			Kenangan old = kenangan(i);
			long w = (long) (old == null ? 0 : Math.abs(old.delta)) * 0x1000000L + (peristiwa[i] & 0xFFFFFF);
			if (w < weakest) {
				weakest = w;
				slot = i;
			}
		}
		peristiwa[slot] = packed;
	}

	/** The event in slot {@code i}, or null when empty. */
	public @Nullable Kenangan kenangan(int i) {
		int v = peristiwa[i];
		return v == 0 ? null : Kenangan.byOrdinal((v >>> 24) - 1);
	}

	/** The world day (low 24 bits) of the event in slot {@code i}. */
	public int hariKenangan(int i) {
		return peristiwa[i] & 0xFFFFFF;
	}

	/** The newest remembered event, or null (dialog lines "you saved my son from the fire"). */
	public @Nullable Kenangan terbaru() {
		int best = -1;
		int day = -1;
		for (int i = 0; i < PERISTIWA; i++) {
			if (peristiwa[i] != 0 && hariKenangan(i) > day) {
				day = hariKenangan(i);
				best = i;
			}
		}
		return best < 0 ? null : kenangan(best);
	}

	/** True when {@code kenangan} is among the remembered events. */
	public boolean ingatAkan(Kenangan kenangan) {
		for (int i = 0; i < PERISTIWA; i++) {
			if (kenangan(i) == kenangan) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== codec

	public CompoundTag simpan() {
		CompoundTag t = new CompoundTag();
		t.putInt("P", pemain);
		if (kenal != 0) {
			t.putByte("k", (byte) kenal);
		}
		if (nama) {
			t.putBoolean("n", true);
		}
		if (kesan != 0) {
			t.putByte("s", (byte) kesan);
		}
		if (kesanKuat != 0) {
			t.putByte("K", (byte) kesanKuat);
		}
		if (dariSapa != 0) {
			t.putByte("g", (byte) dariSapa);
		}
		if (sapaHari >= 0L) {
			t.putLong("d", sapaHari);
		}
		if (bantu != 0) {
			t.putShort("b", (short) Math.min(Short.MAX_VALUE, bantu));
		}
		if (utang != 0) {
			t.putInt("u", utang);
		}
		t.putLong("h", hadiahHari);
		t.putLong("m", temuHari);
		t.putIntArray("e", peristiwa.clone());
		return t;
	}

	public static Ingatan muat(CompoundTag t) {
		Ingatan i = new Ingatan(t.getIntOr("P", 0));
		i.kenal = Mth.clamp(t.getByteOr("k", (byte) 0), 0, 99);
		i.nama = t.getBooleanOr("n", false);
		i.kesan = Mth.clamp(t.getByteOr("s", (byte) 0), -100, 100);
		i.kesanKuat = Mth.clamp(t.getByteOr("K", (byte) 0), -100, 100);
		i.dariSapa = Mth.clamp(t.getByteOr("g", (byte) 0), 0, SAPA_MAKS);
		i.sapaHari = t.getLongOr("d", -1L);
		i.bantu = Math.max(0, t.getShortOr("b", (short) 0));
		i.utang = t.getIntOr("u", 0);
		i.hadiahHari = t.getLongOr("h", -HADIAH_JEDA);
		i.temuHari = t.getLongOr("m", 0L);
		int[] e = t.getIntArray("e").orElse(new int[0]);
		System.arraycopy(e, 0, i.peristiwa, 0, Math.min(PERISTIWA, e.length));
		return i;
	}

	// ================================================================== the table of 42.4.10

	/**
	 * One kind of player deed: the impression change, the strong memory (null = none) and the gossip predicate born at the
	 * place (null = none). {@code keluarga}: the whole household of the resident feels it (weddings, funerals, recruiting a
	 * child, a damaged house).
	 */
	public enum Jenis {
		SAPA(1, null, null, false),
		KENALKAN(1, null, null, false),
		HADIAH_SUKA(8, Kenangan.DIBERI_HADIAH, null, false),
		HADIAH_BIASA(3, Kenangan.DIBERI_HADIAH, null, false),
		HADIAH_BENCI(-5, Kenangan.DIBERI_HADIAH, null, false),
		BANTU(15, Kenangan.DIBANTU, "MENOLONG", false),
		SELAMAT(30, Kenangan.DISELAMATKAN, "MENOLONG", false),
		NIKAH(10, Kenangan.HADIR_NIKAH, "DERMAWAN", true),
		MAKAM(12, Kenangan.HADIR_MAKAM, "DERMAWAN", true),
		REKRUT_BANGGA(20, Kenangan.ANAK_DIREKRUT, "DIREKRUT", true),
		REKRUT_TOLAK(-25, Kenangan.ANAK_DIREKRUT, "DIREKRUT", true),
		PUKUL(-25, Kenangan.DIPUKUL, "BERKELAHI", false),
		CURI(-40, Kenangan.DICURI, "MENCURI", false),
		RUSAK_RUMAH(-15, Kenangan.RUMAH_RUSAK, null, true),
		UTANG_LUNAS(5, Kenangan.UTANG_LUNAS, null, false),
		UTANG_MANGKIR(-20, Kenangan.UTANG_MANGKIR, "INGKAR", false),
		GOSIP_BAIK(3, null, null, false),
		GOSIP_BURUK(-3, null, null, false);

		public final int delta;
		public final @Nullable Kenangan kenangan;
		public final @Nullable String predikat;
		public final boolean keluarga;

		Jenis(int delta, @Nullable Kenangan kenangan, @Nullable String predikat, boolean keluarga) {
			this.delta = delta;
			this.kenangan = kenangan;
			this.predikat = predikat;
			this.keluarga = keluarga;
		}

		/** {@code "bantu"}, {@code "hadiah_suka"}... */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static @Nullable Jenis byId(@Nullable String id) {
			if (id == null) {
				return null;
			}
			try {
				return valueOf(id.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
	}

	/** The memory {@code w} has of {@code player}, created on first use (the least recently met dropped beyond the cap). */
	public static Ingatan untuk(WargaData data, Warga w, UUID player) {
		int index = data.indeksPemain(player);
		Ingatan found = w.ingatanDari(index);
		if (found != null) {
			return found;
		}
		Ingatan baru = new Ingatan(index);
		int cap = KotaConfig.warga().ingatanMaks;
		while (w.ingatan.size() >= cap) {
			Ingatan oldest = w.ingatan.get(0);
			for (Ingatan i : w.ingatan) {
				if (i.temuHari < oldest.temuHari) {
					oldest = i;
				}
			}
			w.ingatan.remove(oldest);
		}
		w.ingatan.add(baru);
		data.ubah();
		return baru;
	}

	/**
	 * A player deed toward resident {@code w} of place {@code situs} ({@code jenis} of {@link Jenis}; unknown kinds are
	 * ignored). Applies the impression (to the whole household when the kind says so), the strong memory, help and gift
	 * rules, and births the gossip of a strong deed. Returns the impression change {@code w} actually got.
	 */
	public static int peristiwa(ServerPlayer player, long situs, Warga w, String jenis) {
		Jenis j = Jenis.byId(jenis);
		MinecraftServer server = KotaHidup.server();
		if (j == null || w == null || player == null || server == null) {
			return 0;
		}
		WargaData data = WargaData.get(server);
		long hari = KotaHidup.hari(server);
		int got = beri(data, w, player.getUUID(), j, hari);
		if (j.keluarga) {
			for (Warga other : data.situs(situs).warga) {
				if (other != w && other.hidup() && w.rumahTangga != 0 && other.rumahTangga == w.rumahTangga) {
					beri(data, other, player.getUUID(), j, hari);
				}
			}
		}
		if (j.predikat != null && player.level() instanceof ServerLevel level && KotaHidup.duniaKota(level)) {
			int idx = data.indeksPemain(player.getUUID());
			GosipWarga.lahir(level, situs, j.predikat, Fakta.Rujuk.pemain(idx), Fakta.Rujuk.warga(w.id));
		}
		data.ubah();
		return got;
	}

	private static int beri(WargaData data, Warga w, UUID player, Jenis j, long hari) {
		Ingatan i = untuk(data, w, player);
		switch (j) {
			case SAPA, KENALKAN -> {
				i.nama |= j == Jenis.KENALKAN;
				int before = i.kesan;
				i.sapa(hari);
				return i.kesan - before;
			}
			case HADIAH_SUKA, HADIAH_BIASA, HADIAH_BENCI -> {
				if (!i.hadiahBoleh(hari)) {
					i.temuHari = Math.max(i.temuHari, hari);
					return 0;
				}
			}
			case BANTU, SELAMAT -> i.bantu++;
			case UTANG_LUNAS -> i.utang = 0;
			default -> {
			}
		}
		i.ubah(j.delta, j.kenangan, false, hari);
		return j.delta;
	}

	/** A gossip about the player reached {@code w} (good or bad, ±3, never a memory); it may also tell the player's name. */
	public static void dengarGosip(WargaData data, Warga w, UUID player, boolean baik, boolean menyebutNama) {
		Ingatan i = untuk(data, w, player);
		i.setKesan(i.kesan + (baik ? 3 : -3));
		if (menyebutNama) {
			i.nama = true;
		}
		data.ubah();
	}

	/** Changes a debt between {@code w} and {@code player} by {@code nb} (+ the resident owes more). */
	public static void ubahUtang(WargaData data, Warga w, UUID player, int nb) {
		Ingatan i = untuk(data, w, player);
		i.utang = Mth.clamp(i.utang + nb, -1_000_000, 1_000_000);
		data.ubah();
	}

	/**
	 * Hari Warga step of a place: every resident's soft impressions drift one step toward 0 every {@value #LURUH_HARI} days
	 * (staggered by resident id so not every memory moves on the same day).
	 */
	public static void harian(WargaData.Situs s, long hari) {
		for (Warga w : s.warga) {
			if (w.ingatan.isEmpty() || Math.floorMod(hari + w.id, LURUH_HARI) != 0) {
				continue;
			}
			for (Ingatan i : w.ingatan) {
				i.luruh();
			}
		}
	}

	/**
	 * A parent died: each living child keeps half of the parent's impression of every player it remembered, as a strong
	 * memory {@code CERITA_ORANG_TUA}, and learns the names the parent knew (42.4.10 "warisan"). Returns the number of
	 * memories passed on.
	 */
	public static int wariskan(WargaData data, Warga mati, long hari) {
		if (mati.ingatan.isEmpty()) {
			return 0;
		}
		int n = 0;
		for (int c : mati.anak) {
			Warga child = data.byId(c);
			if (child == null || !child.hidup()) {
				continue;
			}
			for (Ingatan from : new ArrayList<>(mati.ingatan)) {
				int half = from.kesan / 2;
				if (half == 0 && !from.nama) {
					continue;
				}
				Ingatan to = child.ingatanDari(from.pemain);
				if (to == null) {
					to = new Ingatan(from.pemain);
					to.temuHari = hari;
					int cap = KotaConfig.warga().ingatanMaks;
					if (child.ingatan.size() >= cap) {
						continue;     // a child's own memories come first
					}
					child.ingatan.add(to);
				}
				if (half != 0) {
					to.ubah(half, Kenangan.CERITA_ORANG_TUA, true, hari);
				}
				to.nama |= from.nama;
				n++;
			}
		}
		if (n > 0) {
			data.ubah();
		}
		return n;
	}

	// ================================================================== effects read by other modules

	/** Personal price factor of a trading resident toward a player: up to 5 % cheaper at +100, 5 % dearer at -100. */
	public static double faktorHarga(@Nullable Ingatan i) {
		return i == null ? 1.0D : 1.0D - 0.05D * i.kesan / 100.0D;
	}

	/** True when the resident reports a small offence of this player it saw (false from impression 60). */
	public static boolean lapor(@Nullable Ingatan i) {
		return i == null || i.kesan < KESAN_TUTUP_MATA;
	}

	/** Every resident of a place who remembers {@code player} (for screens and gossip), strongest impression first. */
	public static List<Warga> yangIngat(WargaData data, WargaData.Situs s, UUID player) {
		int index = data.cariPemain(player);
		List<Warga> out = new ArrayList<>();
		if (index < 0) {
			return out;
		}
		for (Warga w : s.warga) {
			if (w.ingatanDari(index) != null) {
				out.add(w);
			}
		}
		out.sort((a, b) -> Integer.compare(Math.abs(b.ingatanDari(index).kesan), Math.abs(a.ingatanDari(index).kesan)));
		return out;
	}
}
