package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.dunia.kota.Harga;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.harta.HartaText;
import dev.kultivator.harta.SpiritHerbs;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import dev.kultivator.tempa.ArtifactItem;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Judi Batu, the Rumah Batu Sumber (DESIGN 38.M.2.4): raw stones Kerikil / Bongkah / Batu Gunung / Batu Purba at 5 / 40 /
 * 300 / 2400 NB. What hides inside is fixed when the stone is cut, from the world seed and the stone's id (no client can
 * read it): empty 50 %, small 30 % (0.6-1.2 P), medium 15 % (1.6-2.8 P), large 4 % (4-6 P), jackpot 1 % (6-10 P) → the
 * expected value 0.27 + 0.33 + 0.20 + 0.08 = 0.88 P ({@code ekonomi38.judiEv} rescales it within 0.80-0.92). The prize is
 * spirit stones ({@code NilaiBatu.give}, never Agung) or a ware of equal value (an aged herb, a beast core, a jade slip; a
 * Batu Purba jackpot may hold a grade-3 artifact), the rest in stones. Mata Batin (band 4+, 20 % of the qi bar, 3 a day):
 * a colour hint grey / blue / gold that is right 70 %, and the house raises that stone's price ×1.6. Limits: 10 stones a
 * day per player, one Batu Purba. The best strategy (only buy gold hints) stays under 1.02 (test {@code uji ekonomi judi}).
 */
public final class JudiBatu {

	/** Stone tiers and prices (NB, before the town price scale). */
	public enum Tingkat {
		KERIKIL("kerikil", 5L),
		BONGKAH("bongkah", 40L),
		GUNUNG("gunung", 300L),
		PURBA("purba", 2400L);

		public final String id;
		public final long harga;

		Tingkat(String id, long harga) {
			this.id = id;
			this.harga = harga;
		}

		public static @Nullable Tingkat byId(String id) {
			for (Tingkat tingkat : values()) {
				if (tingkat.id.equals(id)) {
					return tingkat;
				}
			}
			return null;
		}
	}

	/** Outcome classes of a cut stone. */
	public enum Hasil {
		KOSONG(LembarKunci.JudiBatu.HASIL_KOSONG, 0.50D, 0.0D, 0.0D),
		KECIL(LembarKunci.JudiBatu.HASIL_KECIL, 0.30D, 0.6D, 1.2D),
		SEDANG(LembarKunci.JudiBatu.HASIL_SEDANG, 0.15D, 1.6D, 2.8D),
		BESAR(LembarKunci.JudiBatu.HASIL_BESAR, 0.04D, 4.0D, 6.0D),
		JACKPOT(LembarKunci.JudiBatu.HASIL_JACKPOT, 0.01D, 6.0D, 10.0D);

		public final String id;
		public final double peluang;
		public final double min;
		public final double maks;

		Hasil(String id, double peluang, double min, double maks) {
			this.id = id;
			this.peluang = peluang;
			this.min = min;
			this.maks = maks;
		}

		/** The colour Mata Batin sees for this outcome when it sees true. */
		public String warna() {
			return switch (this) {
				case KOSONG -> LembarKunci.JudiBatu.WARNA_ABU;
				case KECIL, SEDANG -> LembarKunci.JudiBatu.WARNA_BIRU;
				default -> LembarKunci.JudiBatu.WARNA_EMAS;
			};
		}
	}

	/** The design's expected value of the table above. */
	public static final double EV_TABEL = 0.88D;
	public static final int BATAS_HARIAN = 10;
	public static final int PURBA_HARIAN = 1;
	public static final int MATA_BATIN_HARIAN = 3;
	public static final int MATA_BATIN_PITA = 4;
	public static final double MATA_BATIN_QI = 0.20D;
	public static final double MATA_BATIN_BENAR = 0.70D;
	public static final double HARGA_DIPERIKSA = 1.6D;
	/** The rack of one day: three pebbles, two lumps, two mountain stones, one ancient stone. */
	private static final Tingkat[] RAK = {Tingkat.KERIKIL, Tingkat.KERIKIL, Tingkat.KERIKIL, Tingkat.BONGKAH, Tingkat.BONGKAH, Tingkat.GUNUNG,
			Tingkat.GUNUNG, Tingkat.PURBA};
	private static final Set<NpcRole> PERAN = Set.of(NpcRole.BANDAR_BATU, NpcRole.PEMOTONG_BATU);

	static void init() {
		TempatLayar.daftar(LembarKunci.JudiBatu.LAYAR, JudiBatu::aksi, JudiBatu::bangun);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// The hidden content

	/** The outcome and its value factor of stone {@code id}, fixed by the world seed (rolled the same every time). */
	static Object[] isi(MinecraftServer server, long id) {
		RandomSource random = RandomSource.create(server.overworld().getSeed() * 0x5DEECE66DL ^ id * 0x9E3779B97F4A7C15L);
		double roll = random.nextDouble();
		double sum = 0.0D;
		Hasil hasil = Hasil.KOSONG;
		for (Hasil each : Hasil.values()) {
			sum += each.peluang;
			if (roll < sum) {
				hasil = each;
				break;
			}
		}
		double faktor = hasil.min + random.nextDouble() * (hasil.maks - hasil.min);
		faktor *= EkonomiConfig.get().judiEv / EV_TABEL;
		return new Object[] {hasil, faktor, random.nextLong()};
	}

	/** The Mata Batin hint of stone {@code id} seen by {@code player} (70 % the true colour). */
	static String petunjuk(MinecraftServer server, long id, java.util.UUID player) {
		Hasil hasil = (Hasil) isi(server, id)[0];
		RandomSource random = RandomSource.create(id * 31L ^ player.getMostSignificantBits() ^ server.overworld().getSeed());
		if (random.nextDouble() < MATA_BATIN_BENAR) {
			return hasil.warna();
		}
		String[] colours = {LembarKunci.JudiBatu.WARNA_ABU, LembarKunci.JudiBatu.WARNA_BIRU, LembarKunci.JudiBatu.WARNA_EMAS};
		String wrong;
		do {
			wrong = colours[random.nextInt(3)];
		} while (wrong.equals(hasil.warna()));
		return wrong;
	}

	/** Stone id of rack slot {@code slot} at site {@code situs} on {@code hari}. */
	static long idRak(long situs, long hari, int slot) {
		long h = situs * 0x9E3779B97F4A7C15L + hari * 1_000_003L + slot * 7919L + 0x1F2E3D4C5B6AL;
		h ^= h >>> 29;
		return h == 0L ? 1L : h;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Prize

	/** Gives the content of a cut stone worth {@code nilai} NB; returns the item given (empty for stones only). */
	static ItemStack beriIsi(ServerPlayer player, long nilai, boolean purbaJackpot, RandomSource random) {
		if (nilai <= 0L) {
			return ItemStack.EMPTY;
		}
		ItemStack ware = ItemStack.EMPTY;
		if (purbaJackpot && nilai >= Harga.ARTIFACT[3]) {
			ware = ArtifactItem.create(ArtifactItem.FORMS.get(random.nextInt(ArtifactItem.FORMS.size())), 3, random.nextInt(3), 0, null,
					"Rumah Batu Sumber");
		} else if (random.nextFloat() < 0.4F) {
			ware = barangSetara(nilai, random);
		}
		long sisa = nilai;
		if (!ware.isEmpty()) {
			sisa = Math.max(0L, nilai - Math.max(1L, EkonomiApi.harga(ware)));
			Barang.beri(player, ware.copy());
		}
		if (sisa > 0L) {
			NilaiBatu.give(player, sisa);
		}
		return ware;
	}

	/** A ware worth at most {@code nilai}: an aged herb (≤ 300 years), a beast core, a jade slip or a forge metal. */
	private static ItemStack barangSetara(long nilai, RandomSource random) {
		switch (random.nextInt(3)) {
			case 0 -> {
				// herb curve 2 × (1 + (age/100)^1.6): invert, capped at the town limit of 300 years
				double base = Math.max(0.0D, nilai / Harga.HERB_FACTOR - 1.0D);
				int age = (int) Math.min(Harga.SHOP_HERB_MAX_AGE, Math.max(10.0D, 100.0D * Math.pow(base, 1.0D / Harga.HERB_POWER)));
				return SpiritHerbs.randomFor(null, random, age, age);
			}
			case 1 -> {
				int grade = nilai >= 480L ? 4 : nilai >= 120L ? 3 : nilai >= 40L ? 2 : 1;
				ItemStack core = BeastCores.create(Element.values()[random.nextInt(Element.values().length)], grade);
				return Harga.value(core) <= nilai ? core : ItemStack.EMPTY;
			}
			default -> {
				return nilai >= Harga.base("jade_slip") ? Harga.item("jade_slip") : ItemStack.EMPTY;
			}
		}
	}

	/** Opens a Lot Misteri worth {@code nilai} (one Batu Gunung roll scaled to its price). */
	static void bukaMisteri(ServerPlayer player, long nilai) {
		MinecraftServer server = player.level().getServer();
		long id = EkonomiData.get(server).serial("misteri") * 104729L + player.getRandom().nextLong();
		Object[] isi = isi(server, id);
		Hasil hasil = (Hasil) isi[0];
		// the box is never empty: an empty roll still holds a small find
		double faktor = hasil == Hasil.KOSONG ? 0.6D : (double) isi[1];
		long prize = Math.max(1L, Math.round(nilai * faktor));
		ItemStack ware = beriIsi(player, prize, false, RandomSource.create((long) isi[2]));
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.misteri.buka", ware.isEmpty() ? NilaiBatu.text(prize)
				: ware.getHoverName()), true);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Screen

	private static long situs(ServerPlayer player, @Nullable KultivatorNpc npc) {
		return npc == null ? 0L : npc.anchorSite() != 0L ? npc.anchorSite() : npc.town();
	}

	/** Shared (per site and day) sold mask of the rack. */
	private static CompoundTag rakHariIni(MinecraftServer server, long situs) {
		EkonomiData data = EkonomiData.get(server);
		CompoundTag site = data.situs(situs);
		CompoundTag rak = site.getCompoundOrEmpty("judi_rak");
		long hari = Barang.hari(server);
		if (rak.getLongOr("hari", Long.MIN_VALUE) != hari) {
			rak = new CompoundTag();
			rak.putLong("hari", hari);
			site.put("judi_rak", rak);
			data.touch();
		}
		return rak;
	}

	/** The player's gambling record of today (counts, inspected stones, history). */
	private static CompoundTag hariIni(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		CompoundTag tag = data.bagian(player.getUUID(), "judi");
		long hari = Barang.hari(server);
		if (tag.getLongOr("hari", Long.MIN_VALUE) != hari) {
			for (String key : java.util.List.copyOf(tag.keySet())) {
				tag.remove(key);
			}
			tag.putLong("hari", hari);
			data.touch();
		}
		return tag;
	}

	static long hargaBatu(Tingkat tingkat, boolean diperiksa) {
		return Barang.harga(tingkat.harga, diperiksa ? HARGA_DIPERIKSA : 1.0D);
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.judi.judul", LembarKunci.GAYA_BATU, LembarKunci.DISTRIK_PASAR, npc);
		if (!EkonomiConfig.get().judi) {
			Lembar.kunci(tag, Component.translatable("kultivator.tempat_ekonomi.tutup"), null);
			return tag;
		}
		long situs = situs(player, npc);
		long hari = Barang.hari(server);
		CompoundTag rak = rakHariIni(server, situs);
		CompoundTag mine = hariIni(player);
		int bought = mine.getIntOr("n", 0);
		int purba = mine.getIntOr("purba", 0);
		CompoundTag periksa = mine.getCompoundOrEmpty("periksa");
		ListTag list = new ListTag();
		for (int slot = 0; slot < RAK.length; slot++) {
			Tingkat tingkat = RAK[slot];
			long id = idRak(situs, hari, slot);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Integer.toString(slot));
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("item.kultivator.batu_mentah." + tingkat.id));
			row.putString(LembarKunci.JudiBatu.TINGKAT, tingkat.id);
			LembarKunci.barang(row, new ItemStack(EkonomiItems.batu(tingkat)), player.registryAccess());
			String warna = periksa.getStringOr(Long.toString(id), "");
			boolean diperiksa = !warna.isEmpty();
			long harga = hargaBatu(tingkat, diperiksa);
			row.putLong(LembarKunci.HARGA, harga);
			row.putBoolean(LembarKunci.JudiBatu.DIPERIKSA, diperiksa);
			if (diperiksa) {
				row.putString(LembarKunci.JudiBatu.WARNA, warna);
			}
			String owner = rak.getStringOr("s" + slot, "");
			if (!owner.isEmpty()) {
				boolean anda = owner.equals(player.getUUID().toString());
				row.putBoolean(LembarKunci.ANDA, anda && punya(player, id));
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_HABIS);
			} else if (bought >= BATAS_HARIAN || tingkat == Tingkat.PURBA && purba >= PURBA_HARIAN) {
				row.putString(LembarKunci.KEADAAN, LembarKunci.K_BATAS);
				LembarKunci.teks(row, LembarKunci.ALASAN, Component.translatable("kultivator.tempat_ekonomi.judi.batas",
						tingkat == Tingkat.PURBA ? PURBA_HARIAN : BATAS_HARIAN));
			} else {
				row.putString(LembarKunci.KEADAAN, Lembar.keadaan(player, harga, false));
			}
			list.add(row);
		}
		tag.put(LembarKunci.JudiBatu.RAK, list);
		tag.putInt(LembarKunci.JudiBatu.SISA, Math.max(0, BATAS_HARIAN - bought));
		tag.putInt(LembarKunci.JudiBatu.PURBA_SISA, Math.max(0, PURBA_HARIAN - purba));
		tag.putInt(LembarKunci.JudiBatu.MATA_BATIN_SISA, Math.max(0, MATA_BATIN_HARIAN - mine.getIntOr("mata", 0)));
		tag.putBoolean(LembarKunci.JudiBatu.MATA_BATIN_BOLEH, Barang.pita(player) >= MATA_BATIN_PITA);
		tag.put(LembarKunci.RIWAYAT, mine.getListOrEmpty("riwayat").copy());
		CompoundTag potong = mine.getCompoundOrEmpty("potong");
		if (!potong.isEmpty()) {
			tag.put(LembarKunci.JudiBatu.POTONG, potong.copy());
		}
		return tag;
	}

	private static boolean punya(ServerPlayer player, long id) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() instanceof BatuItem && stack.getOrDefault(Barang.DATA, new CompoundTag()).getLongOr("id", 0L) == id) {
				return true;
			}
		}
		return false;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		if (!EkonomiConfig.get().judi) {
			return;
		}
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return;
		}
		String[] parts = LembarKunci.urai(op);
		switch (parts[0]) {
			case LembarKunci.JudiBatu.OP_BELI -> beli(player, npc, LembarKunci.argInt(parts, 1, -1));
			case LembarKunci.JudiBatu.OP_PERIKSA -> periksa(player, npc, LembarKunci.argInt(parts, 1, -1));
			case LembarKunci.JudiBatu.OP_POTONG -> potong(player, npc, LembarKunci.arg(parts, 1));
			default -> {
			}
		}
	}

	private static void beli(ServerPlayer player, KultivatorNpc npc, int slot) {
		if (slot < 0 || slot >= RAK.length) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		long situs = situs(player, npc);
		long hari = Barang.hari(server);
		CompoundTag rak = rakHariIni(server, situs);
		CompoundTag mine = hariIni(player);
		Tingkat tingkat = RAK[slot];
		if (!rak.getStringOr("s" + slot, "").isEmpty()) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.judi.terjual"), false);
			return;
		}
		if (mine.getIntOr("n", 0) >= BATAS_HARIAN || tingkat == Tingkat.PURBA && mine.getIntOr("purba", 0) >= PURBA_HARIAN) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.judi.batas", tingkat == Tingkat.PURBA ? PURBA_HARIAN : BATAS_HARIAN),
					false);
			return;
		}
		long id = idRak(situs, hari, slot);
		boolean diperiksa = !mine.getCompoundOrEmpty("periksa").getStringOr(Long.toString(id), "").isEmpty();
		long harga = hargaBatu(tingkat, diperiksa);
		if (!NilaiBatu.pay(player, harga)) {
			Lembar.kurangUang(player, harga);
			return;
		}
		rak.putString("s" + slot, player.getUUID().toString());
		mine.putInt("n", mine.getIntOr("n", 0) + 1);
		if (tingkat == Tingkat.PURBA) {
			mine.putInt("purba", mine.getIntOr("purba", 0) + 1);
		}
		EkonomiData.get(server).touch();
		ItemStack stone = new ItemStack(EkonomiItems.batu(tingkat));
		CompoundTag data = new CompoundTag();
		data.putLong("id", id);
		data.putString("tingkat", tingkat.id);
		data.putLong("harga", harga);
		String warna = mine.getCompoundOrEmpty("periksa").getStringOr(Long.toString(id), "");
		if (!warna.isEmpty()) {
			data.putString("warna", warna);
		}
		stone.set(Barang.DATA, data);
		Barang.tandaiAsal(stone, hari);
		Barang.beri(player, stone);
		SosokApi.gerak(npc, GerakId.HITUNG_SEMPOA, 0, 40, PropId.SEMPOA, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.dibeli", stone.getHoverName(), NilaiBatu.text(harga)), true);
	}

	private static void periksa(ServerPlayer player, KultivatorNpc npc, int slot) {
		if (slot < 0 || slot >= RAK.length) {
			return;
		}
		if (Barang.pita(player) < MATA_BATIN_PITA) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.judi.mata_batin.pita"), false);
			return;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag mine = hariIni(player);
		if (mine.getIntOr("mata", 0) >= MATA_BATIN_HARIAN) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.judi.mata_batin.batas", MATA_BATIN_HARIAN), false);
			return;
		}
		long id = idRak(situs(player, npc), Barang.hari(server), slot);
		CompoundTag periksa = mine.getCompoundOrEmpty("periksa");
		if (!periksa.getStringOr(Long.toString(id), "").isEmpty()) {
			return;
		}
		double cost = Qi.max(Cultivators.get(player)) * MATA_BATIN_QI;
		if (!Qi.trySpend(player, cost)) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.judi.mata_batin.qi"), false);
			return;
		}
		String warna = petunjuk(server, id, player.getUUID());
		periksa.putString(Long.toString(id), warna);
		mine.put("periksa", periksa);
		mine.putInt("mata", mine.getIntOr("mata", 0) + 1);
		EkonomiData.get(server).touch();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 0.6F);
		Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.judi.mata_batin.lihat",
				Component.translatable("kultivator.tempat_ekonomi.judi.warna." + warna)));
	}

	/** Cuts a stone the player carries: {@code id} of a rack slot bought today, or {@code tangan} for the main hand. */
	private static void potong(ServerPlayer player, KultivatorNpc npc, String which) {
		MinecraftServer server = player.level().getServer();
		ItemStack stone = ItemStack.EMPTY;
		if ("tangan".equals(which)) {
			ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
			if (hand.getItem() instanceof BatuItem) {
				stone = hand;
			}
		} else {
			int slot;
			try {
				slot = Integer.parseInt(which);
			} catch (NumberFormatException exception) {
				return;
			}
			long id = idRak(situs(player, npc), Barang.hari(server), slot);
			for (int i = 0; i < player.getInventory().getContainerSize() && stone.isEmpty(); i++) {
				ItemStack each = player.getInventory().getItem(i);
				if (each.getItem() instanceof BatuItem && each.getOrDefault(Barang.DATA, new CompoundTag()).getLongOr("id", 0L) == id) {
					stone = each;
				}
			}
		}
		if (stone.isEmpty()) {
			Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.judi.tak_ada_batu"), false);
			return;
		}
		cut(player, npc, stone);
	}

	/** Cuts {@code stone} (consumed) and hands out what it held. */
	static void cut(ServerPlayer player, @Nullable KultivatorNpc npc, ItemStack stone) {
		MinecraftServer server = player.level().getServer();
		BatuItem item = (BatuItem) stone.getItem();
		CompoundTag data = stone.getOrDefault(Barang.DATA, new CompoundTag());
		long id = data.getLongOr("id", 0L);
		if (id == 0L) {
			// a stone without a record (creative): a fresh roll
			id = EkonomiData.get(server).serial("batu") * 15485863L;
		}
		Tingkat tingkat = item.tingkat;
		long harga = Math.max(1L, data.getLongOr("harga", Barang.harga(tingkat.harga, 1.0D)));
		Object[] isi = isi(server, id);
		Hasil hasil = (Hasil) isi[0];
		long nilai = Math.round(harga * (double) isi[1]);
		Component nama = stone.getHoverName();
		stone.shrink(1);
		ItemStack ware = beriIsi(player, nilai, hasil == Hasil.JACKPOT && tingkat == Tingkat.PURBA, RandomSource.create((long) isi[2]));
		CompoundTag mine = hariIni(player);
		CompoundTag cut = new CompoundTag();
		cut.putString(LembarKunci.ID, Long.toString(id));
		cut.putString(LembarKunci.JudiBatu.HASIL, hasil.id);
		LembarKunci.teks(cut, LembarKunci.NAMA, ware.isEmpty() ? (nilai > 0L ? NilaiBatu.text(nilai) : Component.translatable(
				"kultivator.tempat_ekonomi.judi.hasil.kosong")) : ware.getHoverName());
		if (!ware.isEmpty()) {
			LembarKunci.barang(cut, ware, player.registryAccess());
		}
		cut.putLong(LembarKunci.NILAI, nilai);
		mine.put("potong", cut);
		ListTag history = mine.getListOrEmpty("riwayat");
		CompoundTag row = new CompoundTag();
		LembarKunci.teks(row, LembarKunci.NAMA, nama);
		row.putString(LembarKunci.JudiBatu.HASIL, hasil.id);
		row.putLong(LembarKunci.NILAI, nilai);
		history.add(0, row);
		while (history.size() > 12) {
			history.remove(history.size() - 1);
		}
		mine.put("riwayat", history);
		EkonomiData.get(server).touch();
		if (npc != null) {
			SosokApi.gerak(npc, GerakId.PALU_LANDASAN, 0, 16, PropId.PAHAT, PropId.BATU_MENTAH, PropId.TIDAK_ADA);
		}
		float pitch = switch (hasil) {
			case KOSONG -> 0.6F;
			case KECIL -> 1.0F;
			case SEDANG -> 1.2F;
			default -> 1.5F;
		};
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
		if (hasil != Hasil.KOSONG) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F,
					pitch);
		}
		if (hasil == Hasil.JACKPOT) {
			Advancements.grant(player, "tempat/judi_jackpot");
		}
		Component line = Component.translatable("kultivator.tempat_ekonomi.judi.hasil." + hasil.id, nama,
				ware.isEmpty() ? NilaiBatu.text(nilai) : ware.getHoverName());
		if (hasil == Hasil.KOSONG) {
			Lembar.peringatan(player, line);
		} else {
			Lembar.pesan(player, line, true);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Test: expected value per strategy

	/**
	 * The {@code uji ekonomi judi <n>} check (38.M.2.4): n stones of the hidden table. Returns the return per NB paid of
	 * {blind buying, buying only stones Mata Batin shows gold, only blue, only grey}; an inspected stone costs
	 * {@link #HARGA_DIPERIKSA} times as much. The best strategy must stay at or under 1.02.
	 */
	public static double[] simulasi(MinecraftServer server, int n) {
		double paidBlind = 0.0D;
		double gotBlind = 0.0D;
		String[] colours = {LembarKunci.JudiBatu.WARNA_EMAS, LembarKunci.JudiBatu.WARNA_BIRU, LembarKunci.JudiBatu.WARNA_ABU};
		double[] paid = new double[colours.length];
		double[] got = new double[colours.length];
		java.util.UUID tester = new java.util.UUID(0x38E0L, 0x1234L);
		for (int i = 0; i < n; i++) {
			long id = idRak(-7L, i, i & 7) ^ ((long) i << 20);
			Object[] isi = isi(server, id);
			double factor = (double) isi[1];
			paidBlind += 1.0D;
			gotBlind += factor;
			String hint = petunjuk(server, id, tester);
			for (int c = 0; c < colours.length; c++) {
				if (colours[c].equals(hint)) {
					paid[c] += HARGA_DIPERIKSA;
					got[c] += factor;
				}
			}
		}
		double[] out = new double[1 + colours.length];
		out[0] = gotBlind / Math.max(1.0D, paidBlind);
		for (int c = 0; c < colours.length; c++) {
			out[1 + c] = paid[c] <= 0.0D ? 0.0D : got[c] / paid[c];
		}
		return out;
	}

	/** A raw stone: cut it at a Rumah Batu Sumber. */
	public static final class BatuItem extends Item {

		public final Tingkat tingkat;

		public BatuItem(Item.Properties properties, Tingkat tingkat) {
			super(properties);
			this.tingkat = tingkat;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable("kultivator.tempat_ekonomi.judi.batu.tooltip").withColor(HartaText.USE_COLOR));
			CompoundTag data = stack.getOrDefault(Barang.DATA, new CompoundTag());
			String warna = data.getStringOr("warna", "");
			if (!warna.isEmpty()) {
				builder.accept(Component.translatable("kultivator.tempat_ekonomi.judi.warna." + warna).withStyle(ChatFormatting.ITALIC));
			}
			HartaText.sources("kultivator.tempat_ekonomi.judi", builder);
		}
	}

	private JudiBatu() {
	}
}
