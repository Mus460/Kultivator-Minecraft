package dev.kultivator.dunia.kota42.sekte;

import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.KotaBus;
import dev.kultivator.dunia.kota42.KotaConfig;
import dev.kultivator.dunia.kota42.KotaHidup;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.ekonomi.PajakKota;
import dev.kultivator.dunia.kota42.ekonomi.StokKota;
import dev.kultivator.dunia.tempat.TempatInfo;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.kompleks.TandingTempat;
import dev.kultivator.dunia.tempat.tema.TemaApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.duel.ai.DuelStyle;
import dev.kultivator.sect.build.flow.LumbungOverflow;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.DiplomasiService;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A player sect protecting a town (DESIGN 42.8.2, contract C42.E1).
 * <ul>
 * <li><b>Where</b>: towns of 34, theme towns and kampung of 38 (no capital: its protector is the palace; no post). Kota
 * Iblis only for a demonic sect; a demonic sect (infamy &ge; 50) only Kota Iblis and kampung that fear it (Gentar &ge; 40).</li>
 * <li><b>Needs</b>: hall level &ge; 3 (town, theme town) or &ge; 2 (kampung), Nama Baik &ge; 50, at most
 * {@code kotaSekte.paktaMaks} pacts.</li>
 * <li><b>The old protector</b>: half the places are born under the nearest NPC sect of a fitting way (within 2000 blocks).
 * Taking over ({@link #rebut}): (a) talk to its envoy with standing &ge; 20 (handed over in peace), (b) beat its champion in
 * a friendly bout ({@code TandingTempat}, once a day), (c) the elders choose the player when his renown is above
 * {@code 2 x tier x 100} (standing -10 with that sect).</li>
 * <li><b>Tribute</b> every 7 days: {@code 30 x (b + 1) x class} NB from the town purse (class kampung 1, kota 2, theme 3;
 * what the purse lacks waits), plus at most 16 surplus goods of the fullest category to the Lumbung; renown +5 and Nama Baik
 * +1 a week; prices x0.97 (42C) and mood +5 (42A-2) while protected.</li>
 * <li><b>Duty</b>: the defence calls of {@link SerbuanKota}; a lost call halves that week's tribute and darkens the town's
 * mood for three days (-20); three lost in a row end the pact (Nama Baik -15).</li>
 * </ul>
 * State: {@code KotaSekteData.situs("pakta", situs)}: {@code o} owner, {@code mulai}, {@code gagal}, {@code siap} weeks
 * of tribute waiting, {@code tunda} NB the purse still owes, {@code lama} the NPC protector ("-" none), {@code sb} the raid
 * of {@link SerbuanKota}.
 */
public final class PaktaLindung {

	public static final int AULA_KOTA = 3;
	public static final int AULA_KAMPUNG = 2;
	public static final int NAMA_BAIK_MIN = 50;
	public static final int GAGAL_MAKS = 3;
	public static final int SIAP_MAKS = 4;
	public static final int BARANG_MAKS = 16;
	public static final int STANDING_SERAH = 20;
	public static final int INFAMI_IBLIS = 50;
	public static final int GENTAR_KAMPUNG = 40;
	public static final int JARAK_LAMA = 2000;
	static final String TANPA = "-";

	/** One way to take over a town from its NPC protector ({@code KotaLembar.Pakta.CR_*}). */
	public record Cara(String id, boolean bisa, Component alasan) {
	}

	/** One part of a week's surplus goods. */
	public record BarangUpeti(Kategori kategori, int jumlah) {
	}

	// ================================================================== reading (C42.E1)

	/** The hall owner protecting place {@code situs}, or null. */
	public static @Nullable UUID pelindung(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || !KotaConfig.sekte().aktif || !KotaConfig.sekte().pakta) {
			return null;
		}
		CompoundTag t = peek(server, situs);
		return t == null ? null : t.read("o", UUIDUtil.CODEC).orElse(null);
	}

	/** Every place {@code owner} protects. */
	public static List<Long> milik(MinecraftServer server, UUID owner) {
		List<Long> out = new ArrayList<>();
		for (long situs : situsSemua(server)) {
			CompoundTag t = peek(server, situs);
			if (t != null && owner.equals(t.read("o", UUIDUtil.CODEC).orElse(null))) {
				out.add(situs);
			}
		}
		return out;
	}

	/** Every protected place. */
	public static List<Long> semua(MinecraftServer server) {
		List<Long> out = new ArrayList<>();
		for (long situs : situsSemua(server)) {
			CompoundTag t = peek(server, situs);
			if (t != null && t.contains("o")) {
				out.add(situs);
			}
		}
		return out;
	}

	/** The day the pact began (-1 none). */
	public static long sejak(MinecraftServer server, long situs) {
		CompoundTag t = peek(server, situs);
		return t == null || !t.contains("o") ? -1L : t.getLongOr("mulai", -1L);
	}

	/** Defence calls lost in a row. */
	public static int gagal(MinecraftServer server, long situs) {
		CompoundTag t = peek(server, situs);
		return t == null ? 0 : t.getIntOr("gagal", 0);
	}

	/** Weeks of tribute waiting to be collected. */
	public static int upetiSiap(MinecraftServer server, long situs) {
		CompoundTag t = peek(server, situs);
		return t == null ? 0 : t.getIntOr("siap", 0);
	}

	/** One week's tribute in NB for a player of band {@code pita}: {@code 30 x (b+1) x class}. */
	public static long upetiNb(MinecraftServer server, long situs, int pita) {
		return 30L * (pita + 1L) * Math.min(3, PajakKota.kelas(server, situs));
	}

	/** This week's surplus goods: the fullest goods category above its base stock, at most 16. */
	public static List<BarangUpeti> barang(MinecraftServer server, long situs) {
		Kategori best = null;
		int lebih = 0;
		for (Kategori k : Kategori.values()) {
			if (item(k) == null) {
				continue;
			}
			int s = StokKota.stok(server, situs, k) - StokKota.AWAL;
			if (s > lebih) {
				lebih = s;
				best = k;
			}
		}
		if (best == null) {
			return List.of();
		}
		return List.of(new BarangUpeti(best, Math.min(BARANG_MAKS, Math.max(1, lebih / 4))));
	}

	/** The NPC sect protecting the place before any player (null none; decided once, 50 %, the nearest fitting sect). */
	public static @Nullable String sekteLama(MinecraftServer server, long situs, @Nullable BlockPos pusat) {
		CompoundTag t = KotaSekteData.get(server).situs(KotaSekteData.PAKTA, situs);
		if (t.contains("o")) {
			return null;
		}
		String lama = t.getStringOr("lama", "");
		if (lama.isEmpty() && pusat != null) {
			lama = TANPA;
			if (Math.floorMod(situs * 0x9E3779B97F4A7C15L >>> 29, 100L) < 50L) {
				DiplomasiService d = SekteApi.diplomasi();
				String s = d.nearest(pusat, PajakKota.kotaIblis(server, situs) ? 2 : 0);
				BlockPos gate = s == null ? null : d.gate(s) != null ? d.gate(s) : d.plannedGate(s);
				if (s != null && gate != null && gate.closerThan(pusat, JARAK_LAMA)) {
					lama = s;
				}
			}
			t.putString("lama", lama);
			KotaSekteData.get(server).ubah();
		}
		return lama.isEmpty() || TANPA.equals(lama) ? null : lama;
	}

	/** The checklist of a pact here for {@code player}. */
	public static List<KantorRekrut.Syarat> syarat(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		List<KantorRekrut.Syarat> out = new ArrayList<>();
		int perlu = aulaPerlu(server, situs);
		int level = Halls.level(player.getUUID());
		out.add(new KantorRekrut.Syarat(level >= perlu, Component.translatable("kultivator.kota42.sekte.pakta.syarat.aula", perlu),
				Component.literal(Integer.toString(level))));
		int nb = KotaSekte.namaBaik(server, situs, player.getUUID());
		out.add(new KantorRekrut.Syarat(nb >= NAMA_BAIK_MIN, Component.translatable("kultivator.kota42.sekte.pakta.syarat.nama_baik", NAMA_BAIK_MIN),
				Component.literal(Integer.toString(nb))));
		int punya = milik(server, player.getUUID()).size();
		int maks = KotaConfig.sekte().paktaMaks;
		out.add(new KantorRekrut.Syarat(punya < maks, Component.translatable("kultivator.kota42.sekte.pakta.syarat.maks", maks),
				Component.literal(punya + "/" + maks)));
		Component tempat = alasanTempat(player, situs);
		out.add(new KantorRekrut.Syarat(tempat == null, Component.translatable("kultivator.kota42.sekte.pakta.syarat.tempat"),
				tempat == null ? Component.translatable("kultivator.kota42.sekte.ya") : tempat));
		return out;
	}

	/** The three ways to take a town from its NPC protector, each with whether it can be used now and why not. */
	public static List<Cara> cara(ServerPlayer player, long situs, String sekte) {
		MinecraftServer server = player.level().getServer();
		DiplomasiService d = SekteApi.diplomasi();
		UUID owner = player.getUUID();
		List<Cara> out = new ArrayList<>();
		int standing = d.standing(owner, sekte);
		out.add(new Cara("dialog", standing >= STANDING_SERAH, Component.translatable("kultivator.kota42.sekte.pakta.cara.dialog", standing, STANDING_SERAH)));
		CompoundTag t = KotaSekteData.get(server).situs(KotaSekteData.PAKTA, situs);
		boolean hariIni = t.getLongOr("tanding", -9L) == KotaSekte.hari(server);
		out.add(new Cara("tanding", !hariIni, Component.translatable(hariIni ? "kultivator.kota42.sekte.pakta.cara.tanding_besok"
				: "kultivator.kota42.sekte.pakta.cara.tanding")));
		Halls.HallView hall = Halls.hallOf(owner);
		long renown = hall == null ? 0L : hall.renown();
		long perlu = 2L * Math.max(1, d.tier(sekte)) * 100L;
		out.add(new Cara("dipilih", renown > perlu, Component.translatable("kultivator.kota42.sekte.pakta.cara.dipilih", renown, perlu)));
		return out;
	}

	/** Hall level needed here: kampung 2, towns 3. */
	public static int aulaPerlu(MinecraftServer server, long situs) {
		return PajakKota.JT_KAMPUNG.equals(PajakKota.jenisTempat(server, situs)) ? AULA_KAMPUNG : AULA_KOTA;
	}

	/** True when {@code player}'s sect walks the demonic way (shortcut: the 38 infamy of the master; upgrade to a hall alignment if one is added). */
	public static boolean sekteIblis(ServerPlayer player) {
		return TemaApi.infami(player) >= INFAMI_IBLIS;
	}

	/** Why a pact may not be made at this place by this player (null = it may). */
	static @Nullable Component alasanTempat(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		String jenis = PajakKota.jenisTempat(server, situs);
		if (PajakKota.JT_IBU_KOTA.equals(jenis)) {
			return Component.translatable("kultivator.kota42.sekte.gagal.pakta.ibu_kota");
		}
		if (!PajakKota.JT_KOTA.equals(jenis) && !PajakKota.JT_TEMA.equals(jenis) && !PajakKota.JT_KAMPUNG.equals(jenis)) {
			return Component.translatable("kultivator.kota42.sekte.gagal.pakta.tempat");
		}
		boolean iblis = sekteIblis(player);
		boolean kotaIblis = PajakKota.kotaIblis(server, situs);
		if (kotaIblis && !iblis) {
			return Component.translatable("kultivator.kota42.sekte.gagal.pakta.kota_iblis");
		}
		if (iblis && !kotaIblis && !(PajakKota.JT_KAMPUNG.equals(jenis) && TemaApi.gentar(player, situs) >= GENTAR_KAMPUNG)) {
			return Component.translatable("kultivator.kota42.sekte.gagal.pakta.iblis", GENTAR_KAMPUNG);
		}
		return null;
	}

	// ================================================================== actions

	/** {@code buat}: makes the pact where no NPC sect protects the place (else the ways of {@link #rebut}). */
	public static KotaSekte.Hasil buat(ServerPlayer player, long situs) {
		KotaSekte.Hasil h = cek(player, situs);
		if (h != null) {
			return h.kirim(player);
		}
		MinecraftServer server = player.level().getServer();
		String lama = sekteLama(server, situs, pusat(player));
		if (lama != null) {
			Component nama = SekteApi.diplomasi().name(lama);
			return KotaSekte.Hasil.gagal("pakta.ada_pelindung", nama == null ? Component.literal(lama) : nama).kirim(player);
		}
		jadikan(server, situs, player.getUUID(), player);
		return KotaSekte.Hasil.berhasil("pakta.dibuat", KotaSekte.namaTempat(server, situs)).kirim(player);
	}

	/** {@code rebut:CR}: takes the town from its NPC protector by {@code cara} ({@code dialog}, {@code tanding}, {@code dipilih}). */
	public static KotaSekte.Hasil rebut(ServerPlayer player, long situs, String cara) {
		KotaSekte.Hasil h = cek(player, situs);
		if (h != null) {
			return h.kirim(player);
		}
		MinecraftServer server = player.level().getServer();
		String sekte = sekteLama(server, situs, pusat(player));
		if (sekte == null) {
			return buat(player, situs);
		}
		DiplomasiService d = SekteApi.diplomasi();
		Component namaSekte = d.name(sekte) == null ? Component.literal(sekte) : d.name(sekte);
		UUID owner = player.getUUID();
		for (Cara c : cara(player, situs, sekte)) {
			if (c.id().equals(cara) && !c.bisa()) {
				return new KotaSekte.Hasil(false, c.alasan()).kirim(player);
			}
		}
		switch (cara) {
			case "dialog" -> {
				jadikan(server, situs, owner, player);
				d.adjust(owner, sekte, 2, "kota.serah_pelindung");
				return KotaSekte.Hasil.berhasil("pakta.diserahkan", namaSekte, KotaSekte.namaTempat(server, situs)).kirim(player);
			}
			case "dipilih" -> {
				jadikan(server, situs, owner, player);
				d.adjust(owner, sekte, -10, "kota.rebut_pelindung");
				return KotaSekte.Hasil.berhasil("pakta.dipilih", KotaSekte.namaTempat(server, situs), namaSekte).kirim(player);
			}
			case "tanding" -> {
				return tanding(player, situs, sekte, namaSekte);
			}
			default -> {
				return KotaSekte.Hasil.gagal("pakta.cara").kirim(player);
			}
		}
	}

	private static KotaSekte.Hasil tanding(ServerPlayer player, long situs, String sekte, Component namaSekte) {
		if (!(player.level() instanceof ServerLevel level)) {
			return KotaSekte.Hasil.gagal("dunia").kirim(player);
		}
		MinecraftServer server = level.getServer();
		BlockPos at = BlockPos.containing(player.position().add(player.getLookAngle().multiply(4.0D, 0.0D, 4.0D)));
		int look = (int) (situs ^ sekte.hashCode() * 0x9E3779B1L);
		KultivatorNpc juara = HidupApi.tubuhStaf(level, situs, NpcRole.JUARA_SEKTE, at, player.getYRot() + 180.0F, look);
		if (juara == null) {
			return KotaSekte.Hasil.gagal("pakta.juara").kirim(player);
		}
		juara.setPlate(Component.translatable("kultivator.kota42.sekte.pakta.juara", namaSekte));
		KotaSekteData.get(server).situs(KotaSekteData.PAKTA, situs).putLong("tanding", KotaSekte.hari(server));
		KotaSekteData.get(server).ubah();
		int warna = SekteApi.diplomasi().color(sekte) & 0xFFFFFF;
		Component why = TandingTempat.mulai(player, juara, DuelStyle.SANTO_PEDANG, warna == 0 ? 0xE8C860 : warna, Items.IRON_SWORD, true,
				(pl, body, menang, kalah) -> {
					body.vanish();
					if (menang && pelindung(situs) == null) {
						jadikan(pl.level().getServer(), situs, pl.getUUID(), pl);
						KotaSekte.Hasil.berhasil("pakta.menang_tanding", namaSekte, KotaSekte.namaTempat(pl.level().getServer(), situs)).kirim(pl);
					} else if (kalah) {
						KotaSekte.Hasil.gagal("pakta.kalah_tanding", namaSekte).kirim(pl);
					}
				});
		if (why != null) {
			juara.vanish();
			return new KotaSekte.Hasil(false, why).kirim(player);
		}
		juara.say(Component.translatable("kultivator.kota42.sekte.pakta.juara_seru", namaSekte), 100);
		return KotaSekte.Hasil.berhasil("pakta.tanding_mulai", namaSekte).kirim(player);
	}

	/** {@code ambil_upeti}: the waiting weeks of tribute (NB from the purse, surplus goods to the Lumbung). */
	public static KotaSekte.Hasil ambilUpeti(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = peek(server, situs);
		if (t == null || !player.getUUID().equals(t.read("o", UUIDUtil.CODEC).orElse(null))) {
			return KotaSekte.Hasil.gagal("pakta.bukan_milik").kirim(player);
		}
		int minggu = t.getIntOr("siap", 0);
		long tunda = t.getLongOr("tunda", 0L);
		if (minggu <= 0 && tunda <= 0L) {
			return KotaSekte.Hasil.gagal("pakta.upeti_belum").kirim(player);
		}
		int setengah = t.getIntOr("setengah", 0);
		long ingin = tunda;
		long perMinggu = upetiNb(server, situs, Barang.pita(player));
		for (int i = 0; i < minggu; i++) {
			ingin += i < setengah ? perMinggu / 2L : perMinggu;
		}
		long dapat = KasKota.ambil(server, situs, ingin);
		t.putLong("tunda", ingin - dapat);
		t.putInt("siap", 0);
		t.putInt("setengah", 0);
		if (dapat > 0L) {
			EkonomiApi.beri(player, dapat, "kota42.upeti");
			EkonomiApi.kas(player.getUUID(), LembarKunci.BukuKas.SUMBER_UPETI, false, dapat, "kultivator.kota42.sekte.buku.upeti");
		}
		int barang = 0;
		Component namaBarang = Component.empty();
		if (minggu > 0) {
			for (BarangUpeti b : barang(server, situs)) {
				Item item = item(b.kategori());
				if (item == null) {
					continue;
				}
				StokKota.ubah(server, situs, b.kategori(), -b.jumlah());
				LumbungOverflow.store(player.getUUID(), new ItemStack(item, b.jumlah()));
				barang += b.jumlah();
				namaBarang = b.kategori().nama();
			}
		}
		KotaSekteData.get(server).ubah();
		return KotaSekte.Hasil.berhasil("pakta.upeti_diambil", Barang.nb(dapat), barang, namaBarang, Barang.nb(ingin - dapat)).kirim(player);
	}

	/** {@code putus_ya}: ends the pact (Nama Baik -5: the town feels abandoned). */
	public static KotaSekte.Hasil putus(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		CompoundTag t = peek(server, situs);
		if (t == null || !player.getUUID().equals(t.read("o", UUIDUtil.CODEC).orElse(null))) {
			return KotaSekte.Hasil.gagal("pakta.bukan_milik").kirim(player);
		}
		KotaSekte.tambahNamaBaik(server, situs, player.getUUID(), -5);
		putuskan(server, situs, "diputus");
		return KotaSekte.Hasil.berhasil("pakta.diputus", KotaSekte.namaTempat(server, situs)).kirim(player);
	}

	private static @Nullable KotaSekte.Hasil cek(ServerPlayer player, long situs) {
		KotaSekte.Hasil h = KotaSekte.syaratUmum(player, KotaConfig.sekte().pakta);
		if (h != null) {
			return h;
		}
		MinecraftServer server = player.level().getServer();
		UUID owner = player.getUUID();
		UUID ada = pelindung(situs);
		if (ada != null) {
			return ada.equals(owner) ? KotaSekte.Hasil.gagal("pakta.sudah") : KotaSekte.Hasil.gagal("pakta.dilindungi", KotaSekte.namaSekte(ada));
		}
		Component tempat = alasanTempat(player, situs);
		if (tempat != null) {
			return new KotaSekte.Hasil(false, tempat);
		}
		int perlu = aulaPerlu(server, situs);
		if (Halls.level(owner) < perlu) {
			return KotaSekte.Hasil.gagal("aula", perlu);
		}
		int nb = KotaSekte.namaBaik(server, situs, owner);
		if (nb < NAMA_BAIK_MIN) {
			return KotaSekte.Hasil.gagal("nama_baik", NAMA_BAIK_MIN, nb);
		}
		if (milik(server, owner).size() >= KotaConfig.sekte().paktaMaks) {
			return KotaSekte.Hasil.gagal("pakta.maks", KotaConfig.sekte().paktaMaks);
		}
		return null;
	}

	private static @Nullable BlockPos pusat(ServerPlayer player) {
		TempatInfo info = KotaSekte.tempat(player);
		return info == null ? player.blockPosition() : info.kotak().getCenter();
	}

	/** The pact begins. */
	static void jadikan(MinecraftServer server, long situs, UUID owner, @Nullable ServerPlayer player) {
		CompoundTag t = KotaSekteData.get(server).situs(KotaSekteData.PAKTA, situs);
		long hari = KotaSekte.hari(server);
		t.store("o", UUIDUtil.CODEC, owner);
		t.putLong("mulai", hari);
		t.putLong("minggu", hari);
		t.putInt("gagal", 0);
		t.putInt("siap", 0);
		t.putString("lama", TANPA);
		SerbuanKota.jadwalkan(server, situs, t, hari);
		KotaSekteData.get(server).ubah();
		if (player != null) {
			Advancements.grant(player, "tempat/kota42_pelindung");
			Halls.log(owner, Component.translatable("kultivator.kota42.sekte.pakta.log", KotaSekte.namaTempat(server, situs)));
		}
		KotaBus.PAKTA_BERUBAH.invoker().onPaktaBerubah(server, situs, null, owner);
	}

	/** The pact ends ({@code sebab}: diputus, gagal). */
	static void putuskan(MinecraftServer server, long situs, String sebab) {
		CompoundTag t = KotaSekteData.get(server).situs(KotaSekteData.PAKTA, situs);
		UUID lama = t.read("o", UUIDUtil.CODEC).orElse(null);
		t.remove("o");
		t.remove("sb");
		t.putInt("siap", 0);
		t.putInt("gagal", 0);
		t.putString("lama", TANPA);
		KotaSekteData.get(server).ubah();
		SerbuanKota.lupakanTempat(server, situs);
		TugasKotaMurid.sasaranHilang(server, situs, TugasKotaMurid.SASARAN_JAGA, lama);
		if (lama != null) {
			ServerPlayer p = server.getPlayerList().getPlayer(lama);
			if (p != null && !"diputus".equals(sebab)) {
				Msg.chat(p, Component.translatable("kultivator.kota42.sekte.pakta.putus." + sebab, KotaSekte.namaTempat(server, situs))
						.withStyle(ChatFormatting.RED));
			}
			Halls.log(lama, Component.translatable("kultivator.kota42.sekte.pakta.log_putus", KotaSekte.namaTempat(server, situs)));
		}
		KotaBus.PAKTA_BERUBAH.invoker().onPaktaBerubah(server, situs, lama, null);
	}

	// ================================================================== weekly and daily

	/** Day step: a week of tribute (renown +5, Nama Baik +1), then the raid schedule of {@link SerbuanKota}. */
	static void harian(MinecraftServer server, long hari) {
		if (!KotaConfig.sekte().pakta) {
			return;
		}
		for (long situs : semua(server)) {
			CompoundTag t = KotaSekteData.get(server).situs(KotaSekteData.PAKTA, situs);
			UUID owner = t.read("o", UUIDUtil.CODEC).orElse(null);
			if (owner == null) {
				continue;
			}
			if (Halls.hallOf(owner) == null) {
				// the hall is gone: no sect to protect anyone
				putuskan(server, situs, "aula");
				continue;
			}
			long minggu = t.getLongOr("minggu", hari);
			if (hari - minggu >= 7L) {
				t.putLong("minggu", minggu + 7L);
				t.putInt("siap", Math.min(SIAP_MAKS, t.getIntOr("siap", 0) + 1));
				Halls.addRenown(owner, 5, "kultivator.kota42.sekte.renown.pakta");
				KotaSekte.tambahNamaBaik(server, situs, owner, 1);
			}
			SerbuanKota.harian(server, situs, t, hari);
		}
		KotaSekteData.get(server).ubah();
	}

	/** A lost defence call: halves the coming tribute week; three in a row end the pact. */
	static void kalah(MinecraftServer server, long situs, CompoundTag t, long hari) {
		int gagal = t.getIntOr("gagal", 0) + 1;
		t.putInt("gagal", gagal);
		t.putLong("kalahHari", hari);
		t.putInt("setengah", Math.min(SIAP_MAKS, t.getIntOr("setengah", 0) + 1));
		UUID owner = t.read("o", UUIDUtil.CODEC).orElse(null);
		if (gagal >= GAGAL_MAKS && owner != null) {
			KotaSekte.tambahNamaBaik(server, situs, owner, -15);
			putuskan(server, situs, "gagal");
		}
		KotaSekteData.get(server).ubah();
	}

	/** A won defence call: the losing streak ends. */
	static void menang(MinecraftServer server, long situs, CompoundTag t) {
		t.putInt("gagal", 0);
		KotaSekteData.get(server).ubah();
	}

	/** {@code Suasana} place layer: -20 for three days after a lost defence call. */
	static int pengaruhSuasana(long situs) {
		MinecraftServer server = KotaHidup.server();
		if (server == null || !KotaConfig.sekte().aktif) {
			return 0;
		}
		CompoundTag t = peek(server, situs);
		if (t == null) {
			return 0;
		}
		long kalah = t.getLongOr("kalahHari", -99L);
		return KotaSekte.hari(server) - kalah < 3L ? -20 : 0;
	}

	/** The vanilla good that stands for a category's surplus (null: no goods, e.g. services and buildings). */
	static @Nullable Item item(Kategori k) {
		return switch (k) {
			case PANGAN -> Items.BREAD;
			case OBAT -> Items.HONEY_BOTTLE;
			case HERBA -> Items.SWEET_BERRIES;
			case SENJATA -> Items.IRON_INGOT;
			case ZIRAH -> Items.LEATHER;
			case JIMAT -> Items.PAPER;
			case BAHAN -> Items.OAK_PLANKS;
			case KAIN -> Items.STRING;
			case MEWAH -> Items.AMETHYST_SHARD;
			default -> null;
		};
	}

	// ================================================================== storage

	static @Nullable CompoundTag peek(MinecraftServer server, long situs) {
		CompoundTag part = KotaSekteData.get(server).bagian(KotaSekteData.PAKTA);
		String key = "kota_" + situs;
		return part.contains(key) ? part.getCompoundOrEmpty(key) : null;
	}

	static List<Long> situsSemua(MinecraftServer server) {
		List<Long> out = new ArrayList<>();
		for (String key : KotaSekteData.get(server).bagian(KotaSekteData.PAKTA).keySet()) {
			if (key.startsWith("kota_")) {
				try {
					out.add(Long.parseLong(key.substring(5)));
				} catch (NumberFormatException ignored) {
					// not a place key
				}
			}
		}
		return out;
	}

	private PaktaLindung() {
	}
}
