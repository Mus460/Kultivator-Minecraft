package dev.kultivator.dunia.tempat.ekonomi;

import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.dunia.kota.KedaiTeh;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota42.api.Kategori;
import dev.kultivator.dunia.kota42.ekonomi.HargaKota;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.sosok.SosokApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Capital dishes and the timed effects of eating and sleeping (DESIGN 38.M.2.2): Bakpao Roh 2 NB (filling), Sup Ginseng
 * Roh 6 NB (+10 % qi regeneration 5 minutes), Mi Panjang Umur 12 NB (+2 hearts absorption 3 minutes), Arak Bunga Persik
 * 25 NB (+0.02 comprehension per minute of meditation for 10 minutes, not with Teh Awan Ungu); the inn's "Istirahat
 * Nyenyak" (meditation XP ×1.10 for 20 minutes, not stacking with Teh Embun Pagi: the larger counts). Effects are kept in
 * {@link EkonomiData} (overworld game time), so a relog keeps them. Screen {@code rumah_makan}: cooks and waiters.
 */
public final class Hidangan {

	/** The four dishes with their price and effect duration in ticks. */
	public enum Jenis {
		BAKPAO("bakpao_roh", 2L, 0),
		SUP("sup_ginseng_roh", 6L, 5 * 1200),
		MI("mi_panjang_umur", 12L, 3 * 1200),
		ARAK("arak_bunga_persik", 25L, 10 * 1200);

		public final String id;
		public final long harga;
		public final int durasi;

		Jenis(String id, long harga, int durasi) {
			this.id = id;
			this.harga = harga;
			this.durasi = durasi;
		}

		public Item item() {
			return switch (this) {
				case BAKPAO -> EkonomiItems.BAKPAO_ROH;
				case SUP -> EkonomiItems.SUP_GINSENG_ROH;
				case MI -> EkonomiItems.MI_PANJANG_UMUR;
				case ARAK -> EkonomiItems.ARAK_BUNGA_PERSIK;
			};
		}

		public static @Nullable Jenis byId(String id) {
			for (Jenis jenis : values()) {
				if (jenis.id.equals(id)) {
					return jenis;
				}
			}
			return null;
		}
	}

	/** Qi regeneration factor of the ginseng soup. */
	public static final double SUP_REGEN = 1.10D;
	/** Comprehension per minute of meditation of the peach-blossom wine. */
	public static final double ARAK_PER_MENIT = 0.02D;
	/** Meditation XP factor of a night at an inn. */
	public static final double NYENYAK = 1.10D;
	public static final int NYENYAK_TICK = 20 * 1200;

	static final String EFEK = "efek";
	static final String SUP_SAMPAI = "sup";
	static final String ARAK_SAMPAI = "arak";
	static final String NYENYAK_SAMPAI = "nyenyak";

	private static final Set<NpcRole> PERAN = Set.of(NpcRole.KOKI, NpcRole.PELAYAN, NpcRole.PEMILIK_PENGINAPAN);

	static void init() {
		Qi.addRegenFactor(player -> aktif(player, SUP_SAMPAI) ? SUP_REGEN : 1.0D);
		Progression.addMultiplierProvider(Hidangan::faktorNyenyak);
		TempatLayar.daftar(LembarKunci.Layanan.LAYAR_RUMAH_MAKAN, Hidangan::aksi, Hidangan::bangun);
	}

	/** True while effect {@code kunci} of the player runs. */
	static boolean aktif(ServerPlayer player, String kunci) {
		MinecraftServer server = player.level().getServer();
		CompoundTag efek = EkonomiData.get(server).pemain(player.getUUID()).getCompoundOrEmpty(EFEK);
		return efek.getLongOr(kunci, 0L) > Barang.waktu(server);
	}

	/** Ticks left of effect {@code kunci} (0 when not running). */
	static long sisa(ServerPlayer player, String kunci) {
		MinecraftServer server = player.level().getServer();
		CompoundTag efek = EkonomiData.get(server).pemain(player.getUUID()).getCompoundOrEmpty(EFEK);
		return Math.max(0L, efek.getLongOr(kunci, 0L) - Barang.waktu(server));
	}

	/** Starts effect {@code kunci} for {@code tick} ticks (a new one replaces the old). */
	static void mulai(ServerPlayer player, String kunci, int tick) {
		MinecraftServer server = player.level().getServer();
		EkonomiData data = EkonomiData.get(server);
		CompoundTag efek = data.bagian(player.getUUID(), EFEK);
		efek.putLong(kunci, Barang.waktu(server) + tick);
		data.touch();
	}

	/** Meditation XP factor of "Istirahat Nyenyak": the larger of it and Teh Embun Pagi counts, never both. */
	private static double faktorNyenyak(ServerPlayer player) {
		if (!aktif(player, NYENYAK_SAMPAI) || !Meditation.isMeditating(player)) {
			return 1.0D;
		}
		double teh = KedaiTeh.active(player) == KedaiTeh.EMBUN ? 1.0D + Kota.config().teaBonus : 1.0D;
		return Math.max(NYENYAK, teh) / teh;
	}

	/** Eaten (server, from the item): the dish's effect. */
	static void makan(ServerPlayer player, Jenis jenis) {
		switch (jenis) {
			case SUP -> mulai(player, SUP_SAMPAI, jenis.durasi);
			case MI -> player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, jenis.durasi, 0));
			case ARAK -> {
				if (KedaiTeh.active(player) == KedaiTeh.AWAN) {
					Lembar.peringatan(player, Component.translatable("kultivator.tempat_ekonomi.hidangan.arak_teh"));
				} else {
					mulai(player, ARAK_SAMPAI, jenis.durasi);
				}
			}
			default -> {
			}
		}
	}

	/** Every minute (from {@link Ekonomi}): the wine's comprehension while meditating. */
	static void tickMenit(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (aktif(player, ARAK_SAMPAI) && KedaiTeh.active(player) != KedaiTeh.AWAN && Meditation.isMeditating(player)) {
				Dao dao = Cultivators.get(player).dao();
				if (dao != null) {
					Comprehension.grant(player, dao, ARAK_PER_MENIT);
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Screen rumah_makan (and the dish rows of the inn)

	/** The four dish rows of a menu (shared with {@link Penginapan}). */
	static ListTag menu(ServerPlayer player) {
		ListTag menu = new ListTag();
		boolean toko = EkonomiConfig.get().toko;
		for (Jenis jenis : Jenis.values()) {
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, jenis.id);
			ItemStack stack = new ItemStack(jenis.item());
			LembarKunci.barang(row, stack, player.registryAccess());
			long harga = harga(player, jenis);
			row.putLong(LembarKunci.HARGA, harga);
			LembarKunci.teks(row, LembarKunci.Layanan.EFEK, Component.translatable("kultivator.tempat_ekonomi.hidangan.efek." + jenis.id));
			row.putString(LembarKunci.KEADAAN, toko ? Lembar.keadaan(player, harga, false) : LembarKunci.K_TUTUP);
			menu.add(row);
		}
		return menu;
	}

	/** Effects running for the {@code aktif} list. */
	static ListTag efekAktif(ServerPlayer player) {
		ListTag list = new ListTag();
		String[][] keys = {{SUP_SAMPAI, "sup"}, {ARAK_SAMPAI, "arak"}, {NYENYAK_SAMPAI, "nyenyak"}};
		for (String[] key : keys) {
			long left = sisa(player, key[0]);
			if (left > 0L) {
				CompoundTag row = new CompoundTag();
				LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.tempat_ekonomi.efek." + key[1]));
				row.putLong(LembarKunci.SISA_TICK, left);
				list.add(row);
			}
		}
		return list;
	}

	private static @Nullable CompoundTag bangun(ServerPlayer player, int npcId) {
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		if (npc == null) {
			return null;
		}
		CompoundTag tag = Lembar.kepala(player, "kultivator.tempat_ekonomi.rumah_makan.judul", LembarKunci.GAYA_KAYU, LembarKunci.DISTRIK_GERBANG, npc);
		tag.putString(LembarKunci.JENIS, LembarKunci.Layanan.JENIS_RUMAH_MAKAN);
		tag.put(LembarKunci.Layanan.MENU, menu(player));
		tag.put(LembarKunci.AKTIF, efekAktif(player));
		return tag;
	}

	private static void aksi(ServerPlayer player, int npcId, String op) {
		String[] parts = LembarKunci.urai(op);
		if (!LembarKunci.Layanan.OP_PESAN.equals(parts[0])) {
			return;
		}
		KultivatorNpc npc = Lembar.npc(player, npcId, PERAN);
		beli(player, npc, LembarKunci.arg(parts, 1));
	}

	/** A dish's price here: base x one {@code HargaKota.faktor} (wine MEWAH, the rest PANGAN; 42.6.3) of the place. */
	static long harga(ServerPlayer player, Jenis jenis) {
		Kategori kategori = jenis == Jenis.ARAK ? Kategori.MEWAH : Kategori.PANGAN;
		return Barang.harga(jenis.harga, HargaKota.faktor(player, null, kategori));
	}

	/** Buys one dish (rumah makan and inn). */
	static void beli(ServerPlayer player, @Nullable KultivatorNpc npc, String id) {
		Jenis jenis = Jenis.byId(id);
		if (jenis == null || !EkonomiConfig.get().toko) {
			return;
		}
		long harga = harga(player, jenis);
		if (!NilaiBatu.pay(player, harga)) {
			Lembar.kurangUang(player, harga);
			return;
		}
		ItemStack stack = Barang.tandaiAsal(new ItemStack(jenis.item()), Barang.hari(player.level().getServer()));
		Component name = stack.getHoverName();
		Barang.beri(player, stack);
		if (npc != null) {
			SosokApi.gerak(npc, GerakId.ANGKAT_BAWA, 0, 40, PropId.NAMPAN, PropId.TIDAK_ADA, PropId.TIDAK_ADA);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.4F, 1.3F);
		Lembar.pesan(player, Component.translatable("kultivator.tempat_ekonomi.dibeli", name, NilaiBatu.text(harga)), true);
	}

	private Hidangan() {
	}
}
