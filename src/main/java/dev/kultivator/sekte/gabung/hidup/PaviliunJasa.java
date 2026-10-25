package dev.kultivator.sekte.gabung.hidup;

import dev.kultivator.alchemy.AlchemyComponents;
import dev.kultivator.alchemy.PillItem;
import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.gabung.GabungData;
import dev.kultivator.sekte.gabung.Keanggotaan;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.Pangkat;
import dev.kultivator.sekte.gabung.dunia.TokohSekte;
import dev.kultivator.sekte.gabung.masuk.GabungItems;
import dev.kultivator.sekte.gabung.ui.GabungLayar;
import dev.kultivator.sekte.gabung.ui.GabungLembar;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Paviliun Jasa of DESIGN 41.6.5 (contract C41.C1, owner 41C): the sect's exchange hall at titik {@code kompleks/jasa}
 * (lot {@code paviliun_jasa}; without the lot an NPC in {@code aula_utama}), kept by the Pavilion Elder. Prices are the
 * prices that already exist ({@code TechniqueGrade.pavilionPrice}, {@code Contribution.Ware}, NB / 2 for weapons and
 * materials: one source, 41.0.4) in PK, never NB, times the rank factor {@link #PENGALI_PANGKAT}, times
 * {@link #DISKON_CORAK} for goods of the sect's corak Dao, times {@link #DISKON_SEKUTU} when the player's hall is allied.
 * Tabs: {@code GabungLembar.Paviliun}. Bought goods get {@code kultivator:asal_sekte} and the 38 shop mark (so no 38 shop
 * buys them back for 3 days, 38.M.5); a Teknik Warisan slip gets {@code kultivator:warisan_sekte} and is noted in
 * {@code Keanggotaan.warisan} (41.7.4).
 * <p>
 * Orders ("Pesanan"): {@link #PESANAN_PER_HARI} requests a day (pills of a quality, aged herbs, forged artifacts) paid at
 * the Mission Office value x {@link #PENGALI_PESANAN}, no class factor; with donations at most {@link #SUMBANG_HARIAN} PK a
 * day (the shared counter {@code bagian("hidup").sumbang_*}). Sermon ("Ceramah"): {@link #HARGA_CERAMAH} PK for +5
 * comprehension of a player disciple registered as a ward (41.8.7), once a day each.
 * <p>
 * Actions are registered by {@link HidupAnggota} on the 41U screen ({@code GabungLayar.daftarAksi}: {@code tukar:ID:N},
 * {@code serahkan:ID}, {@code ceramah:SOUL}); 41U-2's builder of {@code gabung_paviliun} calls {@link #lembar}.
 */
public final class PaviliunJasa {

	/**
	 * Price factor by {@code api/Pangkat} ordinal: Pelayan, Tercatat, Luar 1.0; Dalam 0.95; Inti 0.9; Pewaris, Tetua 0.85;
	 * Wakil 0.8; Ketua 0.7; Tetua Tamu (index 9) 0.85.
	 */
	public static final double[] PENGALI_PANGKAT = {1.0D, 1.0D, 1.0D, 0.95D, 0.9D, 0.85D, 0.85D, 0.8D, 0.7D, 0.85D};
	/** Goods with the Dao of the sect's corak. */
	public static final double DISKON_CORAK = 0.8D;
	/** The player's hall is SEKUTU with the sect (does not stack with a rank factor below 0.85). */
	public static final double DISKON_SEKUTU = 0.9D;
	/** Technique slips in stock per week: {@code 6 + kelas}. */
	public static final int STOK_TEKNIK_DASAR = 6;
	/** A Teknik Warisan slip costs this many times the pavilion price of its grade. */
	public static final int KALI_WARISAN = 3;
	/** Orders of the pavilion per day ("Pesanan" tab) and their PK factor on the Mission Office value. */
	public static final int PESANAN_PER_HARI = 3;
	public static final double PENGALI_PESANAN = 1.1D;
	/** Orders and donations together per member and day (the hall's {@code Contribution.OFFER_DAILY_CAP}). */
	public static final int SUMBANG_HARIAN = 300;
	/** Ceramah Kitab for a ward: price and comprehension ({@code Contribution.SERMON_COMPREHENSION}). */
	public static final int HARGA_CERAMAH = 150;
	public static final double CERAMAH_PEMAHAMAN = 5.0D;
	/** Quantity cap of one exchange of a stackable ware. */
	public static final int TUKAR_MAKS = 16;
	/** Pill quality factor of the Mission Office (Low, Mid, High, Perfect; {@code Contribution.PILL_QUALITY_FACTOR}). */
	static final double[] FAKTOR_MUTU = {0.6D, 1.0D, 1.5D, 2.5D};
	/** {@code Keanggotaan.bagian("hidup")} keys: donation counter shared with 41D, done orders, sermons. */
	public static final String K_SUMBANG_HARI = "sumbang_hari";
	public static final String K_SUMBANG_PK = "sumbang_pk";
	static final String K_PESANAN_HARI = "pesanan_hari";
	static final String K_PESANAN = "pesanan_selesai";
	static final String K_CERAMAH = "ceramah";

	/** The rank factor of {@code pangkat} (an {@code api/Pangkat} ordinal), 1.0 when out of range. */
	public static double pengali(int pangkat) {
		return pangkat >= 0 && pangkat < PENGALI_PANGKAT.length ? PENGALI_PANGKAT[pangkat] : 1.0D;
	}

	/** The PK {@code player} pays for one {@code barang} at {@code sekte} (41.6.5 factors). */
	public static int harga(ServerPlayer player, Keanggotaan k, String sekte, StokPaviliun.Barang barang) {
		double rank = pengali(k.pangkat.ordinal());
		double f = rank;
		if (barang.kuota() < 0 && !barang.warisan() && barang.dao() != null && GabungApi.corak(sekte).punyaDao(barang.dao())) {
			f *= DISKON_CORAK;
		}
		if (barang.kuota() < 0 && rank >= 0.85D && SekteApi.diplomasi().stance(player.getUUID(), sekte) == Sikap.SEKUTU) {
			f *= DISKON_SEKUTU;
		}
		return Math.max(1, (int) Math.round(barang.harga() * f));
	}

	/** Why {@code k} may not take {@code barang} (rank, class, grade right), or null. Stock and PK are checked apart. */
	public static @Nullable Component kunci(MinecraftServer server, Keanggotaan k, String sekte, StokPaviliun.Barang barang) {
		if (k.pangkat == Pangkat.PELAYAN) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.pelayan");
		}
		if (GabungApi.kelas(sekte) < barang.kelasMin()) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.kelas");
		}
		if (!k.pangkat.minimal(barang.pangkatMin())) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.pangkat", barang.pangkatMin().nama());
		}
		if (GabungLembar.Paviliun.TAB_TEKNIK.equals(barang.tab())) {
			TechniqueGrade hak = StokPaviliun.hakTeknik(k.pangkat);
			if (hak == null || barang.mutu() > hak.tier()) {
				return Component.translatable("kultivator.gabung.paviliun.alasan.mutu");
			}
		}
		if (barang.warisan() && !k.warisan.isEmpty()) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.warisan_punya");
		}
		return null;
	}

	/**
	 * The sheet of screen {@code gabung_paviliun} ({@code GabungLembar.Paviliun}) for {@code player} at the pavilion of
	 * {@code sekte}: stock, prices in PK, locks with reasons, the player's PK. Empty (no keys) when the player may not use it.
	 */
	public static CompoundTag lembar(ServerPlayer player, String sekte) {
		if (sekte == null || GabungApi.tier(sekte) <= 0) {
			return new CompoundTag();
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag lembar = GabungLayar.kepala(player, sekte, Component.translatable("kultivator.gabung.paviliun.judul"));
		Keanggotaan k = GabungApi.anggota(player);
		if (!k.anggotaDi(sekte)) {
			CompoundTag lock = new CompoundTag();
			LembarKunci.teks(lock, LembarKunci.ALASAN, Component.translatable("kultivator.gabung.hidup.bukan_anggota"));
			LembarKunci.teks(lock, LembarKunci.SYARAT, Component.translatable("kultivator.gabung.paviliun.syarat_anggota"));
			lembar.put(LembarKunci.KUNCI_LAYAR, lock);
			return lembar;
		}
		HolderLookup.Provider reg = server.registryAccess();
		lembar.putFloat(GabungLembar.Paviliun.PENGALI, (float) pengali(k.pangkat.ordinal()));
		TokohSekte.Tokoh penjaga = TokohSekte.jabatan(server, sekte, Jabatan.TETUA_AJAR);
		if (penjaga != null) {
			CompoundTag npc = new CompoundTag();
			LembarKunci.teks(npc, LembarKunci.NAMA, Component.literal(penjaga.nama()));
			LembarKunci.teks(npc, LembarKunci.GELAR, Component.translatable("kultivator.gabung.paviliun.penjaga"));
			npc.putString(LembarKunci.TOKOH_ID, penjaga.id());
			npc.putInt(LembarKunci.POTRET, -1);
			lembar.put(LembarKunci.NPC, npc);
		}
		ListTag daftar = new ListTag();
		List<String> tabs = new ArrayList<>();
		for (StokPaviliun.Barang b : StokPaviliun.semua(server, sekte)) {
			if (daftar.size() >= LembarKunci.DAFTAR_MAKS) {
				break;
			}
			ItemStack sample = b.buat().get();
			if (sample.isEmpty()) {
				continue;
			}
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, b.id());
			e.putString(GabungLembar.Paviliun.TAB_ENTRI, b.tab());
			LembarKunci.teks(e, LembarKunci.NAMA, b.warisan() ? namaWarisan(sekte, sample) : sample.getHoverName());
			LembarKunci.barang(e, sample, reg);
			e.putInt(LembarKunci.HARGA, harga(player, k, sekte, b));
			e.putInt(GabungLembar.Paviliun.HARGA_DASAR, b.harga());
			int sisa = StokPaviliun.sisa(server, sekte, b);
			e.putInt(LembarKunci.STOK, sisa);
			e.putInt(LembarKunci.MUTU, b.mutu());
			boolean corak = b.dao() != null && !b.warisan() && GabungApi.corak(sekte).punyaDao(b.dao());
			e.putBoolean(GabungLembar.Paviliun.CORAK_DISKON, corak);
			if (b.dao() != null) {
				e.putString(GabungLembar.DAO, b.dao().id());
			}
			e.putBoolean(GabungLembar.Paviliun.WARISAN, b.warisan());
			Component why = kunci(server, k, sekte, b);
			if (why != null) {
				e.putString(LembarKunci.KEADAAN, LembarKunci.K_TERKUNCI);
				e.putBoolean(LembarKunci.TERKUNCI, true);
				LembarKunci.teks(e, LembarKunci.ALASAN, why);
			} else if (sisa == 0) {
				e.putString(LembarKunci.KEADAAN, LembarKunci.K_HABIS);
				LembarKunci.teks(e, LembarKunci.ALASAN, Component.translatable("kultivator.gabung.paviliun.alasan.habis"));
			} else {
				e.putString(LembarKunci.KEADAAN, LembarKunci.K_OK);
			}
			daftar.add(e);
			if (!tabs.contains(b.tab())) {
				tabs.add(b.tab());
			}
		}
		lembar.put(LembarKunci.DAFTAR, daftar);
		lembar.put(GabungLembar.Paviliun.PESANAN, pesananLembar(player, k, sekte, reg));
		ListTag ceramah = ceramahLembar(player, k);
		lembar.put(GabungLembar.Paviliun.CERAMAH, ceramah);
		ListTag hidden = new ListTag();
		for (String tab : List.of(GabungLembar.Paviliun.TAB_WARISAN, GabungLembar.Paviliun.TAB_KUOTA, GabungLembar.Paviliun.TAB_PUSAKA,
				GabungLembar.Paviliun.TAB_BAHAN)) {
			if (!tabs.contains(tab)) {
				hidden.add(StringTag.valueOf(tab));
			}
		}
		if (ceramah.isEmpty()) {
			hidden.add(StringTag.valueOf(GabungLembar.Paviliun.TAB_CERAMAH));
		}
		lembar.put(LembarKunci.TAB_SEMBUNYI, hidden);
		if (k.pangkat == Pangkat.PELAYAN) {
			CompoundTag tabKunci = new CompoundTag();
			Component why = Component.translatable("kultivator.gabung.paviliun.alasan.pelayan");
			for (String tab : GabungLembar.Paviliun.TABS) {
				if (!GabungLembar.Paviliun.TAB_PESANAN.equals(tab)) {
					LembarKunci.teks(tabKunci, tab, why);
				}
			}
			lembar.put(LembarKunci.TAB_KUNCI, tabKunci);
			lembar.putString(LembarKunci.TAB, GabungLembar.Paviliun.TAB_PESANAN);
		}
		return lembar;
	}

	/** "Warisan Sekte Pedang Awan: Tebasan Bulan Sabit". */
	static Component namaWarisan(String sekte, ItemStack slip) {
		return Component.translatable("kultivator.gabung.paviliun.warisan_nama", GabungLayar.namaSekte(sekte), slip.getHoverName());
	}

	// ================================================================== exchange

	/** {@code tukar:ID:N}: buys {@code n} of ware {@code id} with personal PK. Null on success, else the reason. */
	public static @Nullable Component tukar(ServerPlayer player, String sekte, String id, int n) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		if (!GabungApi.aktif() || sekte == null || !k.anggotaDi(sekte)) {
			return Component.translatable("kultivator.gabung.hidup.bukan_anggota");
		}
		StokPaviliun.Barang barang = null;
		for (StokPaviliun.Barang b : StokPaviliun.semua(server, sekte)) {
			if (b.id().equals(id)) {
				barang = b;
				break;
			}
		}
		if (barang == null) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.tidak_ada");
		}
		Component why = kunci(server, k, sekte, barang);
		if (why != null) {
			return why;
		}
		int sisa = StokPaviliun.sisa(server, sekte, barang);
		int count = Mth.clamp(n, 1, TUKAR_MAKS);
		ItemStack sample = barang.buat().get();
		if (sample.isEmpty()) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.tidak_ada");
		}
		if (sample.getMaxStackSize() <= 1 || barang.warisan() || barang.kuota() >= 0) {
			count = 1;
		}
		if (sisa >= 0) {
			if (sisa == 0) {
				return Component.translatable("kultivator.gabung.paviliun.alasan.habis");
			}
			count = Math.min(count, sisa);
		}
		int unit = harga(player, k, sekte, barang);
		int total = unit * count;
		if (k.pk < total) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.pk", total, k.pk);
		}
		if (!GabungApi.bayarPk(player, total, "gabung.paviliun")) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.pk", total, k.pk);
		}
		long hari = Kota.day(server);
		for (int i = 0; i < count; i++) {
			ItemStack stack = i == 0 ? sample : barang.buat().get();
			if (stack.isEmpty()) {
				continue;
			}
			stack.set(GabungItems.ASAL_SEKTE, new GabungItems.AsalSekte(sekte, hari));
			Barang.tandaiAsal(stack, hari);
			if (barang.warisan()) {
				stack.set(GabungItems.WARISAN_SEKTE, sekte);
				String spec = barang.id().substring(2);
				k.warisan = spec;
			}
			if (barang.kuota() >= 0) {
				KuotaAlam.catatJual(server, sekte, barang.kuota());
				KuotaAlam.catatTerima(k, hari);
			}
			Barang.beri(player, stack);
		}
		StokPaviliun.catatJual(server, sekte, barang, count);
		k.catat(hari, "kultivator.gabung.riwayat.beli", barang.id(), Integer.toString(count), Integer.toString(total));
		GabungData.get(server).setDirty();
		if (GabungLembar.Paviliun.TAB_TEKNIK.equals(barang.tab()) || barang.warisan()) {
			Advancements.grant(player, "sekte/gabung_paviliun");
		}
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2D, player.getZ(), 12, 0.4D, 0.5D, 0.4D, 0.4D);
		GabungLayar.pesan(player, Component.translatable("kultivator.gabung.paviliun.dibeli", count, sample.getHoverName(), total),
				LembarKunci.PESAN_OK);
		return null;
	}

	// ================================================================== orders

	/** Today's orders of {@code sekte} (pure from the seed). Each: id, jenis (pil/herba/pusaka), item, mutu, jumlah, pk. */
	public static List<CompoundTag> pesanan(MinecraftServer server, String sekte) {
		long hari = Kota.day(server);
		RandomSource random = RandomSource.create(server.overworld().getSeed() ^ sekte.hashCode() * 0x2545F4914F6CDD1DL ^ hari * 0x27BB2EE687B0B0FDL);
		int kelas = GabungApi.kelas(sekte);
		boolean tubuh = JenisMisiGabung.pesananPusaka(sekte);
		List<CompoundTag> out = new ArrayList<>();
		for (int i = 0; i < PESANAN_PER_HARI; i++) {
			CompoundTag o = new CompoundTag();
			o.putString("id", "o." + hari + "." + i);
			int roll = random.nextInt(10);
			String jenis = tubuh && roll < 4 ? "pusaka" : roll < 6 ? "pil" : roll < 9 ? "herba" : "pusaka";
			o.putString("jenis", jenis);
			switch (jenis) {
				case "pil" -> {
					String pill = StokPaviliun.pilKelas(kelas, random.nextInt(3));
					int q = Math.min(PillQuality.HIGH.ordinal(), random.nextInt(Math.min(3, kelas + 1)));
					int n = 2 + random.nextInt(3);
					o.putString("item", pill);
					o.putInt("mutu", q);
					o.putInt("jumlah", n);
					double unit = Math.max(1.0D, Harga.base(pill) / Harga.NB_PER_PK * FAKTOR_MUTU[q]);
					o.putInt("pk", (int) Math.round(Math.min(SUMBANG_HARIAN, unit * n * PENGALI_PESANAN)));
				}
				case "herba" -> {
					int age = 50 + 50 * random.nextInt(Math.min(6, kelas + 2));
					int n = 2 + random.nextInt(4);
					o.putInt("mutu", age);
					o.putInt("jumlah", n);
					o.putInt("pk", (int) Math.round(Math.min(SUMBANG_HARIAN, SpiritHerbs.missionValue(age) * n * PENGALI_PESANAN)));
				}
				default -> {
					int grade = random.nextInt(Math.min(3, kelas));
					o.putInt("mutu", grade);
					o.putInt("jumlah", 1);
					o.putInt("pk", (int) Math.round(Math.min(SUMBANG_HARIAN, Harga.ARTIFACT[grade] / Harga.NB_PER_PK * PENGALI_PESANAN)));
				}
			}
			out.add(o);
		}
		return out;
	}

	/** True when {@code stack} fulfils order {@code o}. */
	static boolean cocok(CompoundTag o, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		int need = o.getIntOr("mutu", 0);
		return switch (o.getStringOr("jenis", "")) {
			case "pil" -> stack.getItem() instanceof PillItem pill && pill.kind().id().equals(o.getStringOr("item", ""))
					&& AlchemyComponents.quality(stack).ordinal() >= need;
			case "herba" -> SpiritHerbs.isHerb(stack) && SpiritHerbs.age(stack) >= need;
			default -> ArtifactItem.is(stack) && ArtifactItem.grade(ArtifactItem.data(stack)) >= need;
		};
	}

	/** A sample stack of order {@code o} for the sheet. */
	static ItemStack contoh(MinecraftServer server, CompoundTag o) {
		return switch (o.getStringOr("jenis", "")) {
			case "pil" -> {
				ItemStack s = Harga.item(o.getStringOr("item", ""));
				yield s.isEmpty() ? s : AlchemyComponents.withQuality(s, PillQuality.values()[Mth.clamp(o.getIntOr("mutu", 0), 0, 3)]);
			}
			case "herba" -> SpiritHerbs.randomFor(null, RandomSource.create(o.getStringOr("id", "").hashCode()), o.getIntOr("mutu", 50),
					o.getIntOr("mutu", 50));
			default -> ArtifactItem.create(ArtifactItem.PEDANG, o.getIntOr("mutu", 0), 0, 0);
		};
	}

	static Component namaPesanan(MinecraftServer server, CompoundTag o) {
		String jenis = o.getStringOr("jenis", "");
		return switch (jenis) {
			case "pil" -> Component.translatable("kultivator.gabung.paviliun.pesanan.pil", contoh(server, o).getHoverName(),
					Component.translatable("kultivator.gabung.paviliun.mutu." + Mth.clamp(o.getIntOr("mutu", 0), 0, 3)));
			case "herba" -> Component.translatable("kultivator.gabung.paviliun.pesanan.herba", o.getIntOr("mutu", 50));
			default -> Component.translatable("kultivator.gabung.paviliun.pesanan.pusaka", o.getIntOr("mutu", 0));
		};
	}

	private static int punya(ServerPlayer player, CompoundTag o) {
		Inventory inv = player.getInventory();
		int have = 0;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack s = inv.getItem(slot);
			if (cocok(o, s)) {
				have += s.getCount();
			}
		}
		return have;
	}

	private static boolean sudahPesanan(Keanggotaan k, String id, long hari) {
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		if (b.getLongOr(K_PESANAN_HARI, -1L) != hari) {
			return false;
		}
		ListTag list = b.getListOrEmpty(K_PESANAN);
		for (int i = 0; i < list.size(); i++) {
			if (id.equals(list.getStringOr(i, ""))) {
				return true;
			}
		}
		return false;
	}

	/** PK already earned today through orders and donations (shared with 41D's donations). */
	public static int sumbangHariIni(Keanggotaan k, long hari) {
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		return b.getLongOr(K_SUMBANG_HARI, -1L) == hari ? b.getIntOr(K_SUMBANG_PK, 0) : 0;
	}

	/** Counts {@code pk} against today's order/donation cap (41D's donations call this too). */
	public static void catatSumbang(Keanggotaan k, long hari, int pk) {
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		if (b.getLongOr(K_SUMBANG_HARI, -1L) != hari) {
			b.putLong(K_SUMBANG_HARI, hari);
			b.putInt(K_SUMBANG_PK, 0);
		}
		b.putInt(K_SUMBANG_PK, b.getIntOr(K_SUMBANG_PK, 0) + Math.max(0, pk));
	}

	private static ListTag pesananLembar(ServerPlayer player, Keanggotaan k, String sekte, HolderLookup.Provider reg) {
		MinecraftServer server = player.level().getServer();
		long hari = Kota.day(server);
		ListTag list = new ListTag();
		for (CompoundTag o : pesanan(server, sekte)) {
			if (sudahPesanan(k, o.getStringOr("id", ""), hari)) {
				continue;
			}
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, o.getStringOr("id", ""));
			LembarKunci.teks(e, LembarKunci.NAMA, namaPesanan(server, o));
			ItemStack sample = contoh(server, o);
			if (!sample.isEmpty()) {
				LembarKunci.barang(e, sample, reg);
			}
			e.putInt(LembarKunci.JUMLAH, o.getIntOr("jumlah", 1));
			e.putInt("punya", punya(player, o));
			e.putInt(LembarKunci.PK, Math.min(o.getIntOr("pk", 1), Math.max(0, SUMBANG_HARIAN - sumbangHariIni(k, hari))));
			list.add(e);
		}
		return list;
	}

	/** {@code serahkan:ID}: hands in today's order {@code id} from the bag. Null on success, else the reason. */
	public static @Nullable Component serahkan(ServerPlayer player, String sekte, String id) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		if (!GabungApi.aktif() || sekte == null || !k.anggotaDi(sekte)) {
			return Component.translatable("kultivator.gabung.hidup.bukan_anggota");
		}
		long hari = Kota.day(server);
		CompoundTag order = null;
		for (CompoundTag o : pesanan(server, sekte)) {
			if (o.getStringOr("id", "").equals(id)) {
				order = o;
			}
		}
		if (order == null || sudahPesanan(k, id, hari)) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.pesanan_hilang");
		}
		int room = SUMBANG_HARIAN - sumbangHariIni(k, hari);
		if (room <= 0) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.sumbang_penuh", SUMBANG_HARIAN);
		}
		int need = order.getIntOr("jumlah", 1);
		if (punya(player, order) < need) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.pesanan_kurang", need);
		}
		Inventory inv = player.getInventory();
		int left = need;
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE && left > 0; slot++) {
			ItemStack s = inv.getItem(slot);
			if (cocok(order, s)) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
		inv.setChanged();
		int pk = Math.max(1, Math.min(order.getIntOr("pk", 1), room));
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		if (b.getLongOr(K_PESANAN_HARI, -1L) != hari) {
			b.putLong(K_PESANAN_HARI, hari);
			b.put(K_PESANAN, new ListTag());
		}
		ListTag done = b.getListOrEmpty(K_PESANAN);
		done.add(StringTag.valueOf(id));
		b.put(K_PESANAN, done);
		catatSumbang(k, hari, pk);
		k.catat(hari, "kultivator.gabung.riwayat.pesanan", order.getStringOr("jenis", ""), Integer.toString(pk));
		GabungData.get(server).setDirty();
		GabungApi.tambahPk(player, pk, "gabung.pesanan");
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.6F, 1.1F);
		GabungLayar.pesan(player, Component.translatable("kultivator.gabung.paviliun.pesanan.selesai", namaPesanan(server, order), pk),
				LembarKunci.PESAN_OK);
		return null;
	}

	// ================================================================== donations of goods (41.6.2 "Donasi barang")

	/**
	 * The Mission Office value of one item of {@code stack} in PK (the hall's {@code Contribution.unitValue}, package-private
	 * there, mirrored): aged herbs and beast cores by {@code Harta.missionValue}, pills at town base / 2 x quality, forged
	 * artifacts at NB / 2, a few precious vanilla goods. Spirit stones are never worth personal PK (41.0.4).
	 */
	public static double nilaiKantor(ItemStack stack) {
		if (stack.isEmpty() || dev.kultivator.harta.SpiritStones.isStone(stack)) {
			return 0.0D;
		}
		if (stack.is(net.minecraft.world.item.Items.GLOW_BERRIES)) {
			return 0.5D;
		}
		if (stack.is(net.minecraft.world.item.Items.AMETHYST_SHARD) || stack.is(net.minecraft.world.item.Items.GOLDEN_CARROT)
				|| stack.is(net.minecraft.world.item.Items.GOLD_INGOT)) {
			return 1.0D;
		}
		if (stack.is(net.minecraft.world.item.Items.GLISTERING_MELON_SLICE)) {
			return 2.0D;
		}
		if (stack.is(net.minecraft.world.item.Items.EMERALD)) {
			return 6.0D;
		}
		if (stack.is(net.minecraft.world.item.Items.DIAMOND)) {
			return 8.0D;
		}
		if (stack.is(net.minecraft.world.item.Items.ECHO_SHARD)) {
			return 15.0D;
		}
		double harta = dev.kultivator.harta.Harta.missionValue(stack);
		if (harta > 0.0D) {
			return harta;
		}
		String pillId = stack.getItem() instanceof PillItem pill ? pill.kind().id()
				: stack.is(dev.kultivator.item.RootItems.MARROW_CLEANSING_PILL) ? "marrow_cleansing_pill" : null;
		if (pillId != null) {
			int q = Mth.clamp(AlchemyComponents.quality(stack).ordinal(), 0, FAKTOR_MUTU.length - 1);
			long base = Harga.base(pillId);
			if (base > 0L) {
				return Math.min(SUMBANG_HARIAN, Math.max(1.0D, base / Harga.NB_PER_PK * FAKTOR_MUTU[q]));
			}
			return new double[]{5.0D, 10.0D, 20.0D, 40.0D}[q];
		}
		if (ArtifactItem.is(stack)) {
			int g = Mth.clamp(ArtifactItem.grade(ArtifactItem.data(stack)), 0, Harga.ARTIFACT.length - 1);
			return Harga.ARTIFACT[g] / Harga.NB_PER_PK;
		}
		return 0.0D;
	}

	/**
	 * {@code /kultivasi gabung sumbang}: donates the main-hand stack at the Paviliun Jasa (own compound or near the
	 * pavilion titik) for personal PK at its Mission Office value (no class factor), within the day's cap shared with the
	 * orders; the sect's resources grow +1 per 50 PK of value (41.4.7, through 41D). Null on success, else the reason.
	 */
	public static @Nullable Component sumbang(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null || !GabungApi.aktif()) {
			return Component.translatable("kultivator.gabung.hidup.bukan_anggota");
		}
		if (!Jatah.diTempat(player, sekte)) {
			return Component.translatable("kultivator.gabung.paviliun.sumbang.tempat");
		}
		ItemStack hand = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
		double unit = nilaiKantor(hand);
		if (unit <= 0.0D) {
			return Component.translatable("kultivator.gabung.paviliun.sumbang.tolak");
		}
		long hari = Kota.day(server);
		int room = SUMBANG_HARIAN - sumbangHariIni(k, hari);
		if (room <= 0) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.sumbang_penuh", SUMBANG_HARIAN);
		}
		int count = Math.min(hand.getCount(), (int) Math.floor(room / unit + 1.0E-6D));
		int pk = (int) Math.floor(count * unit + 1.0E-6D);
		if (count <= 0 || pk <= 0) {
			return Component.translatable("kultivator.gabung.paviliun.sumbang.sedikit");
		}
		Component name = hand.getHoverName();
		hand.shrink(count);
		player.getInventory().setChanged();
		catatSumbang(k, hari, pk);
		k.catat(hari, "kultivator.gabung.riwayat.sumbang_barang", Integer.toString(count), Integer.toString(pk));
		GabungData.get(server).setDirty();
		GabungApi.tambahPk(player, pk, "gabung.sumbang");
		if (pk >= 50) {
			dev.kultivator.sekte.diplomasi.DiplomasiGabung.ubahSumber(server, sekte, pk / 50, "gabung.sumbang");
		}
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
		String grade = unit >= 15.0D ? "istimewa" : unit >= 5.0D ? "tinggi" : unit >= 1.0D ? "baik" : "biasa";
		dev.kultivator.util.Msg.chat(player, Component.translatable("kultivator.gabung.paviliun.sumbang.nilai." + grade).withStyle(ChatFormatting.DARK_AQUA));
		GabungLayar.pesan(player, Component.translatable("kultivator.gabung.paviliun.sumbang.selesai", count, name, pk), LembarKunci.PESAN_OK);
		return null;
	}

	// ================================================================== sermon for a ward

	private static ListTag ceramahLembar(ServerPlayer player, Keanggotaan k) {
		ListTag list = new ListTag();
		if (k.muridTitipan.isEmpty()) {
			return list;
		}
		long hari = Kota.day(player.level().getServer());
		CompoundTag done = k.bagian(PapanMisiSekte.BAGIAN).getCompoundOrEmpty(K_CERAMAH);
		int harga = Math.max(1, (int) Math.round(HARGA_CERAMAH * pengali(k.pangkat.ordinal())));
		List<DiscipleEntity> loaded = Disciples.loaded(player.getUUID());
		for (UUID soul : k.muridTitipan) {
			CompoundTag e = new CompoundTag();
			e.putString(LembarKunci.ID, soul.toString());
			DiscipleEntity d = murid(loaded, soul);
			LembarKunci.teks(e, LembarKunci.NAMA, d != null ? d.getDisplayName() : Component.translatable("kultivator.gabung.paviliun.ceramah.jauh"));
			e.putInt(LembarKunci.HARGA, harga);
			Component why = d == null ? Component.translatable("kultivator.gabung.paviliun.ceramah.jauh")
					: done.getLongOr(soul.toString(), -1L) == hari ? Component.translatable("kultivator.gabung.paviliun.ceramah.sudah") : null;
			e.putBoolean(LembarKunci.BISA, why == null);
			if (why != null) {
				LembarKunci.teks(e, LembarKunci.ALASAN, why);
			}
			list.add(e);
		}
		return list;
	}

	private static @Nullable DiscipleEntity murid(List<DiscipleEntity> loaded, UUID soul) {
		for (DiscipleEntity d : loaded) {
			if (d.soulId().equals(soul)) {
				return d;
			}
		}
		return null;
	}

	/** {@code ceramah:SOUL}: a sermon for registered ward {@code soul}. Null on success, else the reason. */
	public static @Nullable Component ceramah(ServerPlayer player, String sekte, String soulId) {
		MinecraftServer server = player.level().getServer();
		Keanggotaan k = GabungApi.anggota(player);
		if (!GabungApi.aktif() || sekte == null || !k.anggotaDi(sekte)) {
			return Component.translatable("kultivator.gabung.hidup.bukan_anggota");
		}
		UUID soul;
		try {
			soul = UUID.fromString(soulId);
		} catch (IllegalArgumentException e) {
			return Component.translatable("kultivator.gabung.paviliun.ceramah.jauh");
		}
		if (!k.muridTitipan.contains(soul)) {
			return Component.translatable("kultivator.gabung.paviliun.ceramah.bukan_titipan");
		}
		DiscipleEntity d = murid(Disciples.loaded(player.getUUID()), soul);
		if (d == null) {
			return Component.translatable("kultivator.gabung.paviliun.ceramah.jauh");
		}
		long hari = Kota.day(server);
		CompoundTag b = k.bagian(PapanMisiSekte.BAGIAN);
		CompoundTag done = b.getCompoundOrEmpty(K_CERAMAH);
		if (done.getLongOr(soulId, -1L) == hari) {
			return Component.translatable("kultivator.gabung.paviliun.ceramah.sudah");
		}
		int harga = Math.max(1, (int) Math.round(HARGA_CERAMAH * pengali(k.pangkat.ordinal())));
		if (!GabungApi.bayarPk(player, harga, "gabung.ceramah")) {
			return Component.translatable("kultivator.gabung.paviliun.alasan.pk", harga, k.pk);
		}
		d.data().addComprehension(CERAMAH_PEMAHAMAN);
		done.putLong(soulId, hari);
		b.put(K_CERAMAH, done);
		GabungData.get(server).setDirty();
		ServerLevel level = (ServerLevel) d.level();
		level.sendParticles(ParticleTypes.ENCHANT, d.getX(), d.getY() + 1.4D, d.getZ(), 30, 0.5D, 0.6D, 0.5D, 0.6D);
		level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.8F, 0.9F);
		GabungLayar.pesan(player, Component.translatable("kultivator.gabung.paviliun.ceramah.selesai", d.getDisplayName(), (int) CERAMAH_PEMAHAMAN),
				LembarKunci.PESAN_OK);
		return null;
	}

	// ================================================================== chat fallback

	/** {@code /kultivasi gabung paviliun [tab]}: the wares of one tab in chat with exchange buttons (no screen needed). */
	public static void tulisChat(ServerPlayer player, @Nullable String tab) {
		Keanggotaan k = GabungApi.anggota(player);
		String sekte = k.sekteAktif();
		if (sekte == null) {
			dev.kultivator.util.Msg.chat(player, Component.translatable("kultivator.gabung.hidup.bukan_anggota").withStyle(ChatFormatting.GRAY));
			return;
		}
		MinecraftServer server = player.level().getServer();
		String t = tab == null || tab.isEmpty() ? GabungLembar.Paviliun.TAB_PIL : tab;
		dev.kultivator.util.Msg.chat(player, Component.translatable("kultivator.gabung.paviliun.chat.judul", GabungLayar.namaSekte(sekte), k.pk,
				Component.translatable("kultivator.gabung.paviliun.tab." + t)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		MutableComponent tabs = Component.empty();
		for (String each : List.of(GabungLembar.Paviliun.TAB_TEKNIK, GabungLembar.Paviliun.TAB_WARISAN, GabungLembar.Paviliun.TAB_PIL,
				GabungLembar.Paviliun.TAB_PUSAKA, GabungLembar.Paviliun.TAB_BAHAN, GabungLembar.Paviliun.TAB_KUOTA, GabungLembar.Paviliun.TAB_PESANAN)) {
			tabs.append(Component.literal("[").append(Component.translatable("kultivator.gabung.paviliun.tab." + each)).append("] ")
					.withStyle(st -> st.withColor(each.equals(t) ? ChatFormatting.YELLOW : ChatFormatting.GRAY)
							.withClickEvent(new ClickEvent.RunCommand("/kultivasi gabung paviliun " + each))));
		}
		dev.kultivator.util.Msg.chat(player, tabs);
		if (GabungLembar.Paviliun.TAB_PESANAN.equals(t)) {
			long hari = Kota.day(server);
			for (CompoundTag o : pesanan(server, sekte)) {
				String id = o.getStringOr("id", "");
				boolean done = sudahPesanan(k, id, hari);
				MutableComponent line = namaPesanan(server, o).copy().withStyle(done ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE)
						.append(Component.literal(" ×" + o.getIntOr("jumlah", 1) + " · " + o.getIntOr("pk", 1) + " PK ").withStyle(ChatFormatting.GOLD));
				if (!done) {
					line.append(Component.translatable("kultivator.gabung.paviliun.chat.serahkan").withStyle(st -> st.withColor(ChatFormatting.GREEN)
							.withClickEvent(new ClickEvent.RunCommand("/kultivasi gabung paviliun serahkan " + id))));
				}
				dev.kultivator.util.Msg.chat(player, line);
			}
			return;
		}
		for (StokPaviliun.Barang b : StokPaviliun.semua(server, sekte)) {
			if (!b.tab().equals(t)) {
				continue;
			}
			ItemStack sample = b.buat().get();
			if (sample.isEmpty()) {
				continue;
			}
			int sisa = StokPaviliun.sisa(server, sekte, b);
			Component why = kunci(server, k, sekte, b);
			MutableComponent line = (b.warisan() ? namaWarisan(sekte, sample) : sample.getHoverName()).copy().withStyle(ChatFormatting.WHITE)
					.append(Component.literal(" · " + harga(player, k, sekte, b) + " PK" + (sisa >= 0 ? " (" + sisa + ")" : "") + " ")
							.withStyle(ChatFormatting.GOLD));
			if (why != null) {
				line.append(why.copy().withStyle(ChatFormatting.DARK_GRAY));
			} else if (sisa != 0) {
				line.append(Component.translatable("kultivator.gabung.paviliun.chat.tukar").withStyle(st -> st.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/kultivasi gabung paviliun tukar " + b.id() + " 1"))));
			}
			dev.kultivator.util.Msg.chat(player, line);
		}
	}

	private PaviliunJasa() {
	}
}
