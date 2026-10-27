package dev.kultivator.dunia.kota42.bicara;

import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.cakap.Fakta;
import dev.kultivator.dunia.kota42.cakap.GosipWarga;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.kota42.ekonomi.Investasi;
import dev.kultivator.dunia.kota42.ekonomi.Jabatan;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ekonomi.UsahaWarga;
import dev.kultivator.dunia.kota42.sekte.KotaSekteData;
import dev.kultivator.dunia.kota42.sekte.PaktaLindung;
import dev.kultivator.dunia.kota42.warga.ArsipWarga;
import dev.kultivator.dunia.kota42.warga.Berbakat;
import dev.kultivator.dunia.kota42.warga.Kebiasaan;
import dev.kultivator.dunia.kota42.warga.Migrasi;
import dev.kultivator.dunia.kota42.warga.SiklusHidup;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.cerita.Akibat;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The story effects of the 42.10 arcs ({@code data/kultivator/tempat/quest|dialog/kota42_*.json}, written by 42B-C):
 * {@code kota42.<id> <args>} lines run by the 38 effect engine ({@link Akibat}). A reference is {@code tokoh:<id>} (the
 * resident that is that character, {@link TokohKota#warga}), {@code peran:<role>} (a resident of that work role in the
 * player's place) or {@code pemain}/{@code *} (the resident of the open talk). An effect whose reference does not resolve
 * (the character is dead, not cast yet) does nothing.
 * <pre>
 * kota42.kesan   &lt;ref&gt; &lt;delta&gt;                 impression of the player changes
 * kota42.gosip   &lt;predicate&gt; [ref] [ref]       a true rumour is born in the place (custom ids map to a 42K predicate)
 * kota42.kebiasaan &lt;ref&gt; &lt;habit&gt; [hapus]      a habit added (or removed)
 * kota42.nikah   &lt;ref&gt; &lt;ref&gt;                   two residents marry (a man and a woman; the 42D wedding follows)
 * kota42.pindah  &lt;ref&gt; &lt;place kind&gt;            the resident moves to the nearest place of that kind ({@code kota_tema/air})
 * kota42.calon   &lt;ref&gt; pemain|gao|tinggal      a gifted child joins the player's hall, a strong NPC sect, or stays
 * kota42.calon   peran:anak kantor              a child with a root shows up for the recruitment office
 * kota42.saham   &lt;ref&gt; &lt;percent&gt;             the player holds that share of the resident's business (opened if none)
 * kota42.nama_jalan pemain                      a street of the place is named after the player
 * kota42.harga   &lt;category&gt; &lt;percent&gt; &lt;days&gt;  a price event in the player's place (famine, hoarding...)
 * kota42.resep   &lt;item&gt;                         the player learns a recipe (story flag {@code kota42.resep.<item>})
 * kota42.pensiun &lt;ref&gt;                          the character stops being eternal and dies of old age in a few days
 * kota42.warisi  &lt;heir ref&gt; [old ref]           the heir takes over the post and grows up
 * kota42.pakta                                  the player takes the protection of the place (the standard pact)
 * kota42.jabatan &lt;1-5&gt;                          the player holds that office in the place
 * kota42.restu                                 the family of the child the recruitment office refused gives its blessing
 * </pre>
 */
final class AkibatKota {

	private static final String K_HARGA = "harga_cerita";
	private static final String K_JALAN = "jalan_nama";
	private static final String K_CALON = "calon";

	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		Akibat.daftar("kota42.kesan", AkibatKota::kesan);
		Akibat.daftar("kota42.gosip", AkibatKota::gosip);
		Akibat.daftar("kota42.kebiasaan", AkibatKota::kebiasaan);
		Akibat.daftar("kota42.nikah", AkibatKota::nikah);
		Akibat.daftar("kota42.pindah", AkibatKota::pindah);
		Akibat.daftar("kota42.calon", AkibatKota::calon);
		Akibat.daftar("kota42.saham", AkibatKota::saham);
		Akibat.daftar("kota42.nama_jalan", AkibatKota::namaJalan);
		Akibat.daftar("kota42.harga", AkibatKota::harga);
		Akibat.daftar("kota42.resep", AkibatKota::resep);
		Akibat.daftar("kota42.pensiun", AkibatKota::pensiun);
		Akibat.daftar("kota42.warisi", AkibatKota::warisi);
		Akibat.daftar("kota42.pakta", AkibatKota::pakta);
		Akibat.daftar("kota42.jabatan", AkibatKota::jabatan);
		Akibat.daftar("kota42.restu", AkibatKota::restu);
		TempatEvents.QUEST.register((player, id, tahap) -> {
			if (tahap == TempatEvents.QUEST_SELESAI) {
				switch (id) {
					case "kota42_dua_keluarga" -> Advancements.grant(player, "tempat/kota42_dua_keluarga");
					case "kota42_lentera_lao_geng" -> Advancements.grant(player, "tempat/kota42_lao_geng");
					default -> {
					}
				}
			}
		});
		HargaKota.daftarAcara((server, situs, k) -> {
			if (!KotaBicara.aktif()) {
				return null;
			}
			CompoundTag part = KotaBicara.Data.get(server).bagian(K_HARGA);
			ListTag list = part.getListOrEmpty("kota_" + situs);
			long hari = KotaHidup.hari(server);
			double kali = 1.0D;
			for (Tag t : list) {
				if (t instanceof CompoundTag c && c.getLongOr("u", 0L) >= hari && k.id().equals(c.getStringOr("k", ""))) {
					kali *= c.getIntOr("p", 100) / 100.0D;
				}
			}
			return kali == 1.0D ? null : new HargaKota.Pengali(kali, Component.translatable("kultivator.kota42.bicara.cerita.harga"));
		});
	}

	// ================================================================== references

	private static @Nullable MinecraftServer server(ServerPlayer player) {
		return player.level().getServer();
	}

	/** The resident a reference names, or null. */
	private static @Nullable Warga warga(ServerPlayer player, String ref) {
		MinecraftServer server = server(player);
		if (server == null || ref == null) {
			return null;
		}
		if (ref.startsWith("tokoh:")) {
			return TokohKota.warga(server, ref.substring(6));
		}
		if (ref.startsWith("peran:")) {
			WargaData.Situs s = WargaData.get(server).peek(TempatDialog.situs(player));
			if (s != null) {
				for (Warga w : s.warga) {
					if (w.status == Warga.Status.HIDUP && w.peran.equals(ref.substring(6))) {
						return w;
					}
				}
			}
			return null;
		}
		KultivatorNpc npc = TempatDialog.npc(player);
		return npc == null ? null : WargaApi.dari(npc);
	}

	private static int angka(List<String> args, int i, int def) {
		if (i >= args.size()) {
			return def;
		}
		try {
			return Integer.parseInt(args.get(i));
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static long hari(MinecraftServer server) {
		return KotaHidup.hari(server);
	}

	private static long situsPemain(ServerPlayer player) {
		long situs = TempatDialog.situs(player);
		return situs != 0L ? situs : TokohKota.situs(server(player), "gong_shu");
	}

	/** Drops the plans and walking groups cached for a resident whose habits, job or household changed. */
	private static void segarkan(MinecraftServer server, WargaData data, Warga w) {
		data.ubah();
		KotaBus.KERJA_BERUBAH.invoker().onKerjaBerubah(server, data.situsDari(w.id), w, w.peran, w.peran);
	}

	// ================================================================== effects

	private static void kesan(ServerPlayer player, List<String> args) {
		Warga w = args.isEmpty() ? null : warga(player, args.get(0));
		if (w != null) {
			WargaApi.ubahKesan(w, player, angka(args, 1, 0), "cerita", null);
		}
	}

	/** Story rumour ids that are not 42K predicates and the predicate whose telling fits them. */
	private static final Map<String, String> GOSIP = Map.of(
			"kota42_pasangan_sumur", "SUKA",
			"kota42_batas_sumur", "BENCI",
			"kota42_seribu_jari", "MENCURI",
			"kota42_wayang_hilang", "MENCURI");

	private static void gosip(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		if (server == null || args.isEmpty()) {
			return;
		}
		String predikat = GOSIP.getOrDefault(args.get(0), args.get(0).toUpperCase(java.util.Locale.ROOT));
		Fakta.Rujuk a = Fakta.Rujuk.TIADA;
		Fakta.Rujuk b = Fakta.Rujuk.TIADA;
		long situs = 0L;
		for (int i = 1; i < args.size() && i < 3; i++) {
			Warga w = warga(player, args.get(i));
			if (w != null) {
				if (i == 1) {
					a = Fakta.Rujuk.warga(w.id);
				} else {
					b = Fakta.Rujuk.warga(w.id);
				}
				situs = situs == 0L ? WargaData.get(server).situsDari(w.id) : situs;
			}
		}
		situs = situs != 0L ? situs : situsPemain(player);
		if (situs != 0L) {
			GosipWarga.lahir(server.overworld(), situs, predikat, a, b);
		}
	}

	private static void kebiasaan(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		Warga w = args.size() < 2 ? null : warga(player, args.get(0));
		int nomor = args.size() < 2 ? 0 : Kebiasaan.nomor(args.get(1));
		if (server == null || w == null || nomor == 0) {
			return;
		}
		boolean hapus = args.size() > 2 && "hapus".equals(args.get(2));
		if (hapus) {
			w.kebiasaan1 = w.kebiasaan1 == nomor ? w.kebiasaan2 : w.kebiasaan1;
			w.kebiasaan2 = w.kebiasaan2 == nomor || w.kebiasaan1 == w.kebiasaan2 ? 0 : w.kebiasaan2;
		} else if (w.kebiasaan1 != nomor && w.kebiasaan2 != nomor) {
			if (w.kebiasaan1 == 0) {
				w.kebiasaan1 = nomor;
			} else {
				w.kebiasaan2 = nomor;
			}
		}
		segarkan(server, WargaData.get(server), w);
	}

	private static void nikah(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		Warga a = args.size() < 2 ? null : warga(player, args.get(0));
		Warga b = args.size() < 2 ? null : warga(player, args.get(1));
		if (server == null || a == null || b == null || a.kelamin == b.kelamin || a.pasangan == b.id) {
			return;
		}
		WargaData data = WargaData.get(server);
		long situs = data.situsDari(a.id);
		if (situs == 0L || situs != data.situsDari(b.id)) {
			return;
		}
		TokohKota.cerai(data, a);
		TokohKota.cerai(data, b);
		SiklusHidup.nikah(server, data, situs, a, b, hari(server), RandomSource.create(a.id * 0x9E3779B97F4A7C15L ^ b.id));
	}

	private static boolean cocokJenis(MinecraftServer server, long situs, String spec) {
		TemaKota.Kota kota = TemaKota.dari(server, situs);
		if (kota != null) {
			return kota.jenis().startsWith(spec) || PajakKota.JT_TEMA.equals(spec);
		}
		DuniaSites.Site site = DuniaSites.byId(server, situs);
		return site != null && site.kind().startsWith(spec) || PajakKota.jenisTempat(server, situs).equals(spec);
	}

	private static void pindah(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		Warga w = args.size() < 2 ? null : warga(player, args.get(0));
		if (server == null || w == null || w.status != Warga.Status.HIDUP) {
			return;
		}
		WargaData data = WargaData.get(server);
		WargaData.Situs asal = data.peek(data.situsDari(w.id));
		if (asal == null) {
			return;
		}
		long ke = tujuanPindah(data, asal, server, args.get(1));
		if (ke == 0L) {
			ke = tujuanPindah(data, asal, server, ""); // no place of that kind: the nearest one of any kind, never off the map
		}
		if (ke == 0L) {
			return; // a story character never vanishes beyond the map
		}
		// the keys of Migrasi.rencana (a planned move leaves at the next day change)
		ArsipWarga.Catatan catatan = ArsipWarga.catatan(server);
		int pemimpin = w.id;
		CompoundTag sp = w.pasangan > 0 ? catatan.peekWarga(w.pasangan) : null;
		if (sp != null && sp.contains("pdHari") && sp.getLongOr("pdKe", -1L) == ke) {
			pemimpin = sp.getIntOr("pdRombongan", w.pasangan); // the spouse is moving there too: one household on arrival
		}
		CompoundTag t = catatan.warga(w.id);
		t.putLong("pdKe", ke);
		t.putLong("pdHari", hari(server) + 1L);
		t.putString("pdSebab", "");
		t.putInt("pdRombongan", pemimpin);
		catatan.ubah();
	}

	/** The nearest living place of kind {@code spec} ("" = any) other than {@code asal}, or 0. */
	private static long tujuanPindah(WargaData data, WargaData.Situs asal, MinecraftServer server, String spec) {
		long ke = 0L;
		double terdekat = Double.MAX_VALUE;
		for (WargaData.Situs x : data.semuaSitus()) {
			double jarak = x.id == asal.id || !x.bangkit() ? Double.MAX_VALUE : Migrasi.jarak(asal, x);
			if (jarak < 1.0E8D && jarak < terdekat && (spec.isEmpty() || cocokJenis(server, x.id, spec))) {
				terdekat = jarak;
				ke = x.id;
			}
		}
		return ke;
	}

	private static void calon(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		if (server == null || args.size() < 2) {
			return;
		}
		WargaData data = WargaData.get(server);
		if (args.get(0).startsWith("peran:")) {
			anakKantor(player, server, data);
			return;
		}
		Warga w = warga(player, args.get(0));
		if (w == null || w.status != Warga.Status.HIDUP) {
			return;
		}
		long situs = data.situsDari(w.id);
		KotaBicara.Data store = KotaBicara.Data.get(server);
		TokohKota.sub(store, K_CALON).putString(Integer.toString(w.id), args.get(1));
		store.ubah();
		switch (args.get(1)) {
			case "pemain" -> {
				if (w.akar < 2) {
					w.akar = 5;
				}
				if (Berbakat.perluRestu(w)) {
					Berbakat.beriRestu(server, w);
				}
				Halls.CandidateSpec spec = WargaApi.jadikanCalon(w, player.getUUID(), "cerita");
				if (spec != null) {
					Halls.addCandidates(player.getUUID(), List.of(spec));
				}
			}
			case "gao" -> {
				long hari = hari(server);
				w.status = Warga.Status.SEKTE;
				w.hariStatus = hari;
				ArsipWarga.arsipkan(data, situs, w, ArsipWarga.SEBAB_SEKTE + "gao", hari, -1);
				data.ubah();
				CeritaApi.setBendera(player, "kota42.calon.gao", true);
			}
			default -> CeritaApi.setBendera(player, "kota42.calon.tinggal", true);
		}
	}

	/** A child with a spirit root shows up for the recruitment office of the player's place (a root is given when none has one). */
	private static void anakKantor(ServerPlayer player, MinecraftServer server, WargaData data) {
		WargaData.Situs s = data.peek(TempatDialog.situs(player));
		if (s == null) {
			return;
		}
		long hari = hari(server);
		Warga pilih = null;
		for (Warga w : s.warga) {
			int umur = SiklusHidup.umurPada(w, hari);
			if (w.status == Warga.Status.HIDUP && w.tokoh.isEmpty() && umur >= 6 && umur < 13) {
				if (w.akar >= 2) {
					return;
				}
				pilih = pilih == null ? w : pilih;
			}
		}
		if (pilih != null) {
			pilih.akar = 3;
			data.ubah();
			Msg.chat(player, Component.translatable("kultivator.kota42.bicara.cerita.calon_muncul", WargaApi.nama(pilih)).withStyle(ChatFormatting.AQUA));
		}
	}

	private static void saham(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		Warga w = args.isEmpty() ? null : warga(player, args.get(0));
		if (server == null || w == null || !UsahaWarga.aktif()) {
			return;
		}
		long situs = WargaData.get(server).situsDari(w.id);
		UsahaWarga.Usaha u = UsahaWarga.milikWarga(situs, w.id);
		int id = u == null ? 0 : u.id();
		if (u == null) {
			String jenis = "warung_sup";
			for (UsahaWarga.Jenis j : UsahaWarga.JENIS) {
				if (j.peran().contains(w.peranNpc())) {
					jenis = j.id();
					break;
				}
			}
			id = UsahaWarga.buka(server, situs, jenis, w.id, 0);
		}
		int persen = Math.max(1, Math.min(99, angka(args, 1, 40)));
		if (id > 0) {
			Investasi.sahamKavling(server, situs, id, player.getUUID(), persen / 100.0D);
		}
	}

	private static void namaJalan(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		long situs = situsPemain(player);
		if (server == null || situs == 0L) {
			return;
		}
		KotaBicara.Data store = KotaBicara.Data.get(server);
		CompoundTag c = store.situs(K_JALAN, situs);
		String nama = player.getName().getString();
		c.putString("n", nama);
		c.putString("u", player.getUUID().toString());
		store.ubah();
		CeritaApi.setBendera(player, "kota42.nama_jalan", true);
		Advancements.grant(player, "tempat/kota42_jalan_namaku");
		Msg.chat(player, Component.translatable("kultivator.kota42.bicara.cerita.nama_jalan", PajakKota.nama(server, situs), nama).withStyle(ChatFormatting.GOLD));
	}

	/** The name a player gave a street of the place ({@code kota42.nama_jalan}), or null. */
	static @Nullable String namaJalanDi(MinecraftServer server, long situs) {
		CompoundTag c = KotaBicara.Data.get(server).bagian(K_JALAN).getCompoundOrEmpty("kota_" + situs);
		String n = c.getStringOr("n", "");
		return n.isEmpty() ? null : n;
	}

	private static void harga(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		Kategori k = args.isEmpty() ? null : Kategori.byId(args.get(0));
		long situs = situsPemain(player);
		if (server == null || k == null || situs == 0L) {
			return;
		}
		KotaBicara.Data store = KotaBicara.Data.get(server);
		CompoundTag part = store.bagian(K_HARGA);
		String key = "kota_" + situs;
		ListTag list = part.getListOrEmpty(key);
		long sampai = hari(server) + Math.max(1, angka(args, 2, 7));
		ListTag next = new ListTag();
		for (Tag t : list) {
			if (t instanceof CompoundTag c && !k.id().equals(c.getStringOr("k", "")) && c.getLongOr("u", 0L) >= hari(server)) {
				next.add(c);
			}
		}
		CompoundTag c = new CompoundTag();
		c.putString("k", k.id());
		c.putInt("p", Math.max(20, Math.min(400, angka(args, 1, 100))));
		c.putLong("u", sampai);
		next.add(c);
		part.put(key, next);
		store.ubah();
	}

	private static void resep(ServerPlayer player, List<String> args) {
		if (args.isEmpty()) {
			return;
		}
		String item = args.get(0);
		String path = item.substring(item.indexOf(':') + 1);
		CeritaApi.setBendera(player, "kota42.resep." + path, true);
		Msg.chat(player, Component.translatable("kultivator.kota42.bicara.cerita.resep", Component.translatable("item.kultivator." + path))
				.withStyle(ChatFormatting.GREEN));
	}

	private static void pensiun(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		Warga w = args.isEmpty() ? null : warga(player, args.get(0));
		if (server != null && w != null) {
			TokohKota.jadwalkanWafat(server, w, hari(server));
		}
	}

	private static void warisi(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		Warga heir = args.isEmpty() ? null : warga(player, args.get(0));
		if (server == null || heir == null) {
			return;
		}
		Warga old = args.size() > 1 ? warga(player, args.get(1)) : null;
		heir.abadi = true;
		heir.ahli = Math.max(heir.ahli, 80);
		if (heir.watak == 4) {
			heir.watak = 6; // the timid apprentice grows into an honest keeper
		}
		if (old != null && heir.kebiasaan2 == 0 && old.kebiasaan1 != heir.kebiasaan1) {
			heir.kebiasaan2 = old.kebiasaan1;
		}
		segarkan(server, WargaData.get(server), heir);
	}

	private static void pakta(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		long situs = TempatDialog.situs(player);
		if (server == null || situs == 0L) {
			situs = TokohKota.situs(server, "tetua_he");
		}
		if (situs != 0L) {
			Msg.chat(player, PaktaLindung.buat(player, situs).pesan());
		}
	}

	private static void jabatan(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		long situs = situsPemain(player);
		int pangkat = angka(args, 0, 0);
		if (server != null && situs != 0L && pangkat > 0) {
			Jabatan.setPangkat(server, situs, player.getUUID(), pangkat, player.getName().getString());
			Msg.chat(player, Component.translatable("kultivator.kota42.bicara.cerita.jabatan", Jabatan.gelar(server, situs, pangkat),
					PajakKota.nama(server, situs)).withStyle(ChatFormatting.GOLD));
		}
	}

	/** The resident the recruitment office remembered for {@code player} when it started "Restu Ayah" gets the family's blessing. */
	private static void restu(ServerPlayer player, List<String> args) {
		MinecraftServer server = server(player);
		if (server == null) {
			return;
		}
		int id = KotaSekteData.get(server).bagian(KotaSekteData.RESTU).getIntOr(player.getUUID().toString(), -1);
		Warga w = id < 0 ? null : WargaData.get(server).byId(id);
		if (w != null && w.status == Warga.Status.HIDUP) {
			Berbakat.beriRestu(server, w);
		}
	}

	private AkibatKota() {
	}
}
