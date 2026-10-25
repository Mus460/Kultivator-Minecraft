package dev.kultivator.dunia.kota42.ui;

import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.ekonomi.Jabatan;
import dev.kultivator.dunia.kota42.ekonomi.Perkara;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.kultivator.dunia.kota42.ui.KotaLembar.Perkara.*;

/**
 * {@code perkara} (DESIGN 42.11.9, keys {@link KotaLembar.Perkara}) — one case heard by a Hakim Pembantu, from 42C-2's
 * {@code Perkara} (templates of 42B-C): the two parties face to face with their claims, the witnesses (a testimony
 * bubble once called), the evidence cards (an item, a note, or a gossip with its spreader chain once traced), the three
 * verdicts with their estimated consequences, a bribe envelope on the table when the sly party left one, and after the
 * verdict its line. The case is the player's selection ({@code perkara:ID} of {@code jabatan}, or {@code tab:perkara:ID}),
 * else the oldest open case of the place. Opened on the client inside the Yamen screen.
 * <p>
 * Extra keys: {@link #SISA_HARI} in {@code perkara} (days before the case goes stale), {@link #TERBUKA} (D, the other open
 * cases: id S, nama T, so the judge can switch with {@code tab:perkara:ID}).
 */
public final class LembarPerkara {

	/** In {@code perkara}: I days left before the case is dropped. */
	public static final String SISA_HARI = LembarKunci.SISA_HARI;
	/** D the other open cases: id S, nama T, pilih B. */
	public static final String TERBUKA = "terbuka";

	private static final String K = LembarKotaDasar.K + "perkara.";

	static void init() {
		LembarKotaDasar.daftar(LAYAR, LembarPerkara::bangun);
		LembarKotaDasar.bawaan(LAYAR, OP_PANGGIL, (p, npc, situs, op) -> {
			Perkara.Kasus k = kasus(p, situs);
			LembarKotaDasar.hasil(p, k == null ? tiada() : Perkara.panggil(p, situs, k.id(), LembarKunci.argInt(op, 1, -1)),
					Component.translatable(K + "panggil_ok"));
		});
		LembarKotaDasar.bawaan(LAYAR, OP_TELUSURI, (p, npc, situs, op) -> {
			Perkara.Kasus k = kasus(p, situs);
			LembarKotaDasar.hasil(p, k == null ? tiada() : Perkara.telusuri(p, situs, k.id(), LembarKunci.argInt(op, 1, -1)),
					Component.translatable(K + "telusuri_ok"));
		});
		LembarKotaDasar.bawaan(LAYAR, OP_PUTUSKAN, (p, npc, situs, op) -> {
			Perkara.Kasus k = kasus(p, situs);
			LembarKotaDasar.hasil(p, k == null ? tiada() : Perkara.putuskan(p, situs, k.id(), LembarKunci.argInt(op, 1, 0)), null);
		});
		LembarKotaDasar.bawaan(LAYAR, OP_AMPLOP_TERIMA, (p, npc, situs, op) -> amplop(p, situs, true));
		LembarKotaDasar.bawaan(LAYAR, OP_AMPLOP_TOLAK, (p, npc, situs, op) -> amplop(p, situs, false));
	}

	private static Component tiada() {
		return Component.translatable("kultivator.kota42.perkara.tiada");
	}

	private static void amplop(ServerPlayer p, long situs, boolean terima) {
		Perkara.Kasus k = kasus(p, situs);
		Component why = k == null ? tiada() : Perkara.amplop(p, situs, k.id(), terima);
		if (why != null) {
			LembarKotaDasar.pesan(p, why, LembarKunci.PESAN_BAHAYA);
		} else {
			LembarKotaDasar.pesan(p, Component.translatable(K + (terima ? "amplop_diterima" : "amplop_ditolak")),
					terima ? LembarKunci.PESAN_PERINGATAN : LembarKunci.PESAN_OK);
		}
	}

	/** The selected case, else the oldest open one of the place. */
	static @Nullable Perkara.Kasus kasus(ServerPlayer p, long situs) {
		MinecraftServer server = p.level().getServer();
		String pilih = LembarKotaDasar.pilih(p, LAYAR);
		if (!pilih.isEmpty()) {
			try {
				Perkara.Kasus k = Perkara.kasus(server, situs, Integer.parseInt(pilih));
				if (k != null) {
					return k;
				}
			} catch (NumberFormatException ignored) {
				// stale selection
			}
		}
		Perkara.Kasus terbaik = null;
		for (Perkara.Kasus k : Perkara.kasus(server, situs)) {
			if (!k.selesai() && (terbaik == null || k.hari() < terbaik.hari())) {
				terbaik = k;
			}
		}
		return terbaik;
	}

	static @Nullable CompoundTag bangun(ServerPlayer player, int npcId, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = LembarKotaDasar.lembar(player, Component.translatable(K + "judul"), LembarKunci.GAYA_BATU);
		LembarKotaDasar.kepala(tag, player, situs);
		if (situs == 0L) {
			return LembarKotaDasar.kunci(tag, Component.translatable(LembarKotaDasar.K + "jabatan.tanpa_tempat"));
		}
		if (Jabatan.pangkat(player.getUUID(), situs) < Jabatan.HAKIM_PEMBANTU) {
			return LembarKotaDasar.kunci(tag, Component.translatable("kultivator.kota42.perkara.bukan_hakim"));
		}
		Perkara.Kasus k = kasus(player, situs);
		if (k == null) {
			CompoundTag kosong = new CompoundTag();
			LembarKunci.teks(kosong, LembarKunci.TEKS, Component.translatable(K + "kosong"));
			tag.put(LembarKunci.KOSONG, kosong);
			return tag;
		}
		long hari = KotaHidup.hari(server);
		CompoundTag c = new CompoundTag();
		c.putString(LembarKunci.ID, Integer.toString(k.id()));
		LembarKunci.teks(c, LembarKunci.NAMA, Perkara.nama(k));
		LembarKunci.teks(c, LembarKunci.TEKS, Perkara.ringkas(k));
		c.putString(LembarKunci.JENIS, k.templat());
		c.putLong(LembarKunci.HARI, k.hari());
		c.putInt(SISA_HARI, (int) Math.max(0L, Perkara.BUKA_HARI - (hari - k.hari())));
		tag.put(PERKARA, c);

		List<CompoundTag> pihak = new ArrayList<>();
		pihak.add(pihak(player, k, k.penggugat(), PP_PENGGUGAT));
		pihak.add(pihak(player, k, k.tergugat(), PP_TERGUGAT));
		tag.put(PIHAK, LembarKotaDasar.daftar(pihak));

		List<CompoundTag> saksi = new ArrayList<>();
		for (int i = 0; i < k.saksi().length; i++) {
			Warga w = WargaApi.byId(k.saksi()[i]);
			CompoundTag t = w == null ? new CompoundTag() : LembarKotaDasar.warga(w, player, false);
			if (w == null) {
				t.putInt(KotaLembar.WARGA, k.saksi()[i]);
				LembarKunci.teks(t, LembarKunci.NAMA, Component.literal("?"));
			}
			boolean dipanggil = (k.dipanggil() & (1 << i)) != 0;
			t.putBoolean(DIPANGGIL, dipanggil);
			t.putBoolean(HADIR, dipanggil);
			if (dipanggil) {
				LembarKunci.teks(t, KESAKSIAN, Perkara.kesaksian(server, k, i));
			}
			saksi.add(t);
		}
		tag.put(SAKSI, LembarKotaDasar.daftar(saksi));

		List<CompoundTag> bukti = new ArrayList<>();
		for (Perkara.Bukti b : Perkara.bukti(server, k)) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, Integer.toString(b.indeks()));
			t.putString(LembarKunci.JENIS, switch (b.jenis()) {
				case "barang" -> BK_BARANG;
				case "gosip" -> BK_GOSIP;
				default -> BK_CATATAN;
			});
			LembarKunci.teks(t, LembarKunci.NAMA, Component.translatable(K + "bukti." + (b.jenis().isEmpty() ? "catatan" : b.jenis()), b.indeks() + 1));
			if (!b.barang().isEmpty()) {
				LembarKunci.barang(t, b.barang(), server.registryAccess());
				LembarKunci.teks(t, LembarKunci.NAMA, b.barang().getHoverName());
			}
			LembarKunci.teks(t, LembarKunci.TEKS, b.teks());
			ListTag rantai = new ListTag();
			for (Component nama : b.rantai()) {
				CompoundTag r = new CompoundTag();
				LembarKunci.teks(r, LembarKunci.NAMA, nama);
				rantai.add(r);
			}
			t.put(RANTAI, rantai);
			t.putBoolean(DITELUSURI, b.ditelusuri());
			bukti.add(t);
		}
		tag.put(BUKTI, LembarKotaDasar.daftar(bukti));

		List<CompoundTag> putusan = new ArrayList<>();
		for (int no = 1; no <= 3; no++) {
			Component[] v = Perkara.putusanTeks(k, no);
			if (v[0].getString().isEmpty()) {
				continue;
			}
			CompoundTag t = new CompoundTag();
			t.putInt(LembarKunci.NO, no);
			LembarKunci.teks(t, LembarKunci.TEKS, v[0]);
			LembarKunci.teks(t, AKIBAT, v[1]);
			boolean called = k.dipanggil() != 0 || k.ditelusuri() != 0;
			t.putBoolean(LembarKunci.BISA, !k.selesai() && called);
			if (k.selesai()) {
				LembarKunci.teks(t, LembarKunci.ALASAN, Component.translatable("kultivator.kota42.perkara.sudah"));
			} else if (!called) {
				LembarKunci.teks(t, LembarKunci.ALASAN, Component.translatable(K + "dengar_dulu"));
			}
			putusan.add(t);
		}
		tag.put(PUTUSAN, LembarKotaDasar.daftar(putusan));

		if (k.amplop() > 0L && k.amplopKeadaan() == 0 && !k.selesai()) {
			CompoundTag a = new CompoundTag();
			LembarKunci.teks(a, KotaLembar.DARI, Component.translatable(K + "amplop_dari"));
			a.putLong(LembarKunci.HARGA, k.amplop());
			tag.put(AMPLOP, a);
		}
		if (k.selesai()) {
			CompoundTag h = new CompoundTag();
			Component[] v = Perkara.putusanTeks(k, k.putusan());
			LembarKunci.teks(h, LembarKunci.TEKS, Component.translatable(K + "hasil", v[0], v[1]));
			if (k.amplopKeadaan() == 1) {
				h.putBoolean(KotaLembar.Jabatan.SUAP, true);
			}
			tag.put(HASIL, h);
		}

		List<CompoundTag> lain = new ArrayList<>();
		for (Perkara.Kasus o : Perkara.kasus(server, situs)) {
			CompoundTag t = new CompoundTag();
			t.putString(LembarKunci.ID, Integer.toString(o.id()));
			LembarKunci.teks(t, LembarKunci.NAMA, Perkara.nama(o));
			t.putBoolean(KotaLembar.PILIH, o.id() == k.id());
			t.putBoolean(KotaLembar.Jabatan.SELESAI, o.selesai());
			lain.add(t);
		}
		tag.put(TERBUKA, LembarKotaDasar.daftar(lain));
		return tag;
	}

	private static CompoundTag pihak(ServerPlayer player, Perkara.Kasus k, int wargaId, String peran) {
		Warga w = WargaApi.byId(wargaId);
		CompoundTag t = w == null ? new CompoundTag() : LembarKotaDasar.warga(w, player, false);
		if (w == null) {
			t.putInt(KotaLembar.WARGA, wargaId);
			LembarKunci.teks(t, LembarKunci.NAMA, Component.literal("?"));
		}
		t.putString(PERAN_PIHAK, peran);
		LembarKunci.teks(t, KLAIM, Perkara.klaim(k, peran));
		// the watak stays hidden until a witness was heard (the judge learns people by listening)
		if (k.dipanggil() == 0) {
			t.remove(KotaLembar.WATAK);
		}
		return t;
	}

	private LembarPerkara() {
	}
}
