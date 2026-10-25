package dev.kultivator.dunia.kota42.cakap;

import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.api.WargaInfo;
import dev.kultivator.dunia.kota42.warga.Warga;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bridge between the engine's {@link Giliran.Permintaan} and 42K-T's grammar ({@link Realisasi#ujar}): builds a
 * {@link Realisasi.Masukan} from the speaker's {@link Topik.Situasi} (register, dialect, emotion, listener as a
 * {@link TipeSlot.Orang}, the fact's {@code fakta.*} values, the speaker's tics) and hands the utterance back. Registered
 * with {@link Giliran#daftar} from {@code Cakap.init}; no rule fitting returns null, so the fallback keys answer.
 * <p>
 * shortcut: tics and the last tic live in memory (redrawn deterministically after a restart, only the no-repeat-in-a-row
 * memory resets) and the anti-repeat rings are the session rings of {@link AntiUlang}; persist them in
 * {@code kultivator:kota_cakap} if repeated lines across restarts become noticeable.
 */
final class PerangkaiTata implements Giliran.Perangkai {

	private static final Map<Integer, List<String>> TIK = new ConcurrentHashMap<>();
	private static final Map<Integer, String> TIK_AKHIR = new ConcurrentHashMap<>();

	static void daftar() {
		TataBahasa.init();
		Giliran.daftar(new PerangkaiTata());
	}

	static void lupakan() {
		TIK.clear();
		TIK_AKHIR.clear();
		AntiUlang.lupakan();
	}

	@Override
	public @Nullable Ujaran rangkai(Giliran.Permintaan p) {
		Topik.Situasi s = p.situasi();
		Warga w = s.pembicara();
		TataBahasa.Tabel t = TataBahasa.tabel();
		if (t.aturan().isEmpty()) {
			return null;
		}
		MinecraftServer server = KotaHidup.server();
		long benihDunia = server == null ? 0L : server.overworld().getSeed();
		List<String> tik = TIK.computeIfAbsent(w.id, id -> Tik.milik(t.tik(), Cakap.benih(benihDunia, id, 0, 0L, 0, "tik"), x -> Topik.cocok(x, s)));
		Map<String, Object> nilai = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : p.isian().entrySet()) {
			nilai.put(e.getKey(), nilai(e.getValue()));
		}
		Fakta f = p.fakta();
		if (f != null) {
			taruh(nilai, "fakta.subjek", f.subjek(), p);
			taruh(nilai, "fakta.objek", f.objek(), p);
			nilai.putIfAbsent("fakta.nilai", (long) f.nilai());
			nilai.putIfAbsent("fakta.hariKejadian", f.hariKejadian());
		}
		TipeSlot.Orang pendengar = null;
		AntiUlang.Cincin dengar = null;
		ServerPlayer pl = p.pendengar();
		Warga lawan = p.lawan();
		if (pl != null) {
			pendengar = new TipeSlot.Orang(s.tahuNama() ? Ujaran.Simpul.teks(pl.getName()) : null, "l", "dewasa", true);
			dengar = AntiUlang.pemain(pl.getUUID());
		} else if (lawan != null) {
			pendengar = orang(lawan);
			dengar = AntiUlang.warga(lawan.id);
		}
		Realisasi.Masukan m = new Realisasi.Masukan(p.niat(), p.topik(), s.register(), s.dialek(), s.kelamin(), s.tahap(), s.peran(), w.id,
				s.label(), "", s.a(), pendengar, s.kesan(), s.ranahSelisih(), s.tahuNama(), nilai, s.hari(), s.cuaca(), s.musim(), tik,
				TIK_AKHIR.get(w.id), KotaConfig.bicara().tikPeluang, null, x -> Topik.cocok(x, s), dengar, s.now());
		Realisasi.Keluaran k = Realisasi.ujar(t, m, p.acak());
		if (k.kosong()) {
			return null;
		}
		if (k.tik() != null) {
			TIK_AKHIR.put(w.id, k.tik());
		}
		return k.ujaran();
	}

	private static TipeSlot.Orang orang(Warga w) {
		return new TipeSlot.Orang(Ujaran.Simpul.teks(WargaApi.nama(w)), w.pria() ? "l" : "p",
				Topik.tahapId(WargaInfo.Tahap.dari(WargaApi.umur(w))), false);
	}

	/** A fact reference as a slot value: a resident as a person, everything else as its name node. */
	private static void taruh(Map<String, Object> nilai, String kunci, Fakta.Rujuk r, Giliran.Permintaan p) {
		if (r == null || r.jenis() == Fakta.Jenis.TIADA || nilai.containsKey(kunci)) {
			return;
		}
		Warga w = r.jenis() == Fakta.Jenis.WARGA ? WargaApi.byId((int) r.id()) : null;
		Object v = w != null ? orang(w) : Giliran.rujuk(r, p);
		if (v != null) {
			nilai.put(kunci, v);
		}
	}

	/** Engine values in the realiser's types: numbers as Long, components as nodes, residents as persons. */
	private static Object nilai(Object v) {
		if (v instanceof Integer i) {
			return (long) i;
		}
		if (v instanceof Component c) {
			return Ujaran.Simpul.teks(c);
		}
		if (v instanceof Warga w) {
			return orang(w);
		}
		return v;
	}
}
