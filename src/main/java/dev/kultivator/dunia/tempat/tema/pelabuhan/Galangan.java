package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kapal.KapalClass;
import dev.kultivator.dunia.kapal.KapalItems;
import dev.kultivator.dunia.kapal.KapalRohEntity;
import dev.kultivator.dunia.kapal.KapalStyle;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Galangan Kapal of the harbour (DESIGN 38.T.3.4): Mbah Lunas (Gong Shumu) takes keel orders, and the player sees the
 * hull grow on a slipway.
 * <ul>
 * <li><b>Orders</b> ({@code tema_galangan}): a Perahu Awan keel ({@link KapalClass#PERAHU}, band 3+, 900 NB, ready in 2
 * world days) or a Kapal Sekte keel ({@link KapalClass#SEKTE}, band 4+, 4000 NB, 4 days). One order per player per town;
 * orders are persistent in {@code KotaTemaData} (town list {@code pesanan}).</li>
 * <li><b>Slipways</b>: two {@code pelabuhan/galangan} anchors ({@code landasan} 0, 1). Each new order takes the slipway
 * with fewer orders; the slipway shows its oldest unfinished order as a {@link KapalGalanganEntity}: stage
 * {@code floor(4 x days / days needed)} 0..4 (keel, ribs, half planking, full hull and mast stumps, masts and furled sails),
 * each new stage growing in over 10 ticks ({@code KEADAAN_TAHAP_BARU}).</li>
 * <li><b>Pick-up</b> ({@code ambil}): the finished keel item ({@code KapalItems.keel}) with the name the player and Mbah
 * Lunas gave it ({@code tema_lunas_pertama}) in its {@code kultivator:kapal_state}; event {@code lunas <kelas>}.</li>
 * <li><b>Livery</b> ({@code layar:ID}, 60 NB): re-paints the hull style ({@link KapalStyle}, only the always-open ones;
 * the earned ones stay earned at the helm) of the keel the player holds.</li>
 * </ul>
 */
public final class Galangan {

	public static final long HARGA_PERAHU = 900L;
	public static final long HARGA_SEKTE = 4000L;
	public static final long HARGA_LAYAR = 60L;
	public static final int PITA_PERAHU = 3;
	public static final int PITA_SEKTE = 4;
	public static final int TAHAP = 4;

	/** One keel order. */
	public record Pesanan(UUID pemain, String kelas, long mulai, int hari, int landasan, String nama) {

		static Pesanan dari(CompoundTag tag) {
			UUID id;
			try {
				id = UUID.fromString(tag.getStringOr("pemain", ""));
			} catch (IllegalArgumentException e) {
				id = new UUID(0L, 0L);
			}
			return new Pesanan(id, tag.getStringOr("kelas", "perahu"), tag.getLongOr("mulai", 0L), tag.getIntOr("hari", 2),
					tag.getIntOr("landasan", 0), tag.getStringOr("nama", ""));
		}

		CompoundTag tag() {
			CompoundTag tag = new CompoundTag();
			tag.putString("pemain", pemain.toString());
			tag.putString("kelas", kelas);
			tag.putLong("mulai", mulai);
			tag.putInt("hari", hari);
			tag.putInt("landasan", landasan);
			tag.putString("nama", nama);
			return tag;
		}

		/** Build stage 0..4 on world day {@code hari}. */
		public int tahap(long today) {
			long done = Math.max(0L, today - mulai);
			return (int) Math.min(TAHAP, done * TAHAP / Math.max(1, hari));
		}

		public boolean siap(long today) {
			return today - mulai >= hari;
		}

		public int sisaHari(long today) {
			return (int) Math.max(0L, hari - (today - mulai));
		}

		public KapalClass kapal() {
			return "sekte".equals(kelas) ? KapalClass.SEKTE : KapalClass.PERAHU;
		}
	}

	// ================================================================== orders

	private static ListTag daftar(MinecraftServer server, long situs) {
		CompoundTag kota = PelabuhanKota.kota(server, situs);
		ListTag list = kota.getListOrEmpty("pesanan");
		if (!kota.contains("pesanan")) {
			kota.put("pesanan", list);
		}
		return list;
	}

	/** The player's order in this town, or null. */
	public static @Nullable Pesanan pesanan(MinecraftServer server, long situs, UUID player) {
		for (Tag t : daftar(server, situs)) {
			if (t instanceof CompoundTag c && player.toString().equals(c.getStringOr("pemain", ""))) {
				return Pesanan.dari(c);
			}
		}
		return null;
	}

	/** Why the player cannot order class {@code kelas} now, or null. */
	public static @Nullable Component alasan(ServerPlayer player, long situs, String kelas) {
		if (!PelabuhanKota.cfg().galangan) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.galangan.tutup");
		}
		boolean sekte = "sekte".equals(kelas);
		int pita = Barang.pita(player);
		if (pita < (sekte ? PITA_SEKTE : PITA_PERAHU)) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.galangan.pita", sekte ? PITA_SEKTE : PITA_PERAHU);
		}
		if (pesanan(player.level().getServer(), situs, player.getUUID()) != null) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.galangan.sudah");
		}
		return null;
	}

	public static long harga(String kelas) {
		return "sekte".equals(kelas) ? HARGA_SEKTE : HARGA_PERAHU;
	}

	public static int hari(String kelas) {
		return "sekte".equals(kelas) ? 4 : 2;
	}

	/** Places an order (paid); the result line for the screen. */
	public static Component pesan(ServerPlayer player, long situs, String kelas) {
		Component why = alasan(player, situs, kelas);
		if (why != null) {
			return why;
		}
		if (!EkonomiApi.bayar(player, harga(kelas), "galangan." + kelas)) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.kurang_uang");
		}
		MinecraftServer server = player.level().getServer();
		ListTag list = daftar(server, situs);
		int[] per = new int[2];
		for (Tag t : list) {
			if (t instanceof CompoundTag c) {
				per[Math.floorMod(c.getIntOr("landasan", 0), 2)]++;
			}
		}
		int landasan = per[0] <= per[1] ? 0 : 1;
		long today = PelabuhanKota.hari(player.level());
		list.add(new Pesanan(player.getUUID(), kelas, today, hari(kelas), landasan, "").tag());
		PelabuhanKota.ubah(server);
		EkonomiApi.kasKota(situs, harga(kelas) / 10L);
		CeritaApi.peristiwa(player, "pesan_lunas", kelas);
		return Component.translatable("kultivator.kota_tema.pelabuhan.galangan.dipesan", hari(kelas));
	}

	/** Names the hull of the player's order (quest {@code tema_lunas_pertama}; up to 24 characters). */
	public static boolean namai(ServerPlayer player, long situs, String nama) {
		MinecraftServer server = player.level().getServer();
		ListTag list = daftar(server, situs);
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof CompoundTag c && player.getUUID().toString().equals(c.getStringOr("pemain", ""))) {
				String clean = nama == null ? "" : nama.strip();
				c.putString("nama", clean.length() > 24 ? clean.substring(0, 24) : clean);
				PelabuhanKota.ubah(server);
				return true;
			}
		}
		return false;
	}

	/** Hands the finished keel over; the result line. */
	public static Component ambil(ServerPlayer player, long situs) {
		MinecraftServer server = player.level().getServer();
		ListTag list = daftar(server, situs);
		long today = PelabuhanKota.hari(player.level());
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof CompoundTag c) || !player.getUUID().toString().equals(c.getStringOr("pemain", ""))) {
				continue;
			}
			Pesanan p = Pesanan.dari(c);
			if (!p.siap(today)) {
				return Component.translatable("kultivator.kota_tema.pelabuhan.galangan.belum", p.sisaHari(today));
			}
			ItemStack keel = new ItemStack(KapalItems.keel(p.kapal()));
			String name = p.nama().isEmpty() ? "" : p.nama();
			keel.set(KapalItems.STATE, new KapalItems.State(p.kapal().hull(), 0L, KapalRohEntity.DEFAULT_COLOR, false, name, 0));
			if (!name.isEmpty()) {
				keel.set(DataComponents.CUSTOM_NAME, Component.literal(name));
			}
			PelabuhanKota.beri(player, keel);
			list.remove(i);
			PelabuhanKota.ubah(server);
			player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.2F);
			CeritaApi.setBendera(player, "pelabuhan_lunas_diambil", true);
			CeritaApi.peristiwa(player, "lunas", p.kelas());
			dev.kultivator.util.Advancements.grant(player, "tempat/tema_pelabuhan_lunas");
			return Component.translatable("kultivator.kota_tema.pelabuhan.galangan.diambil");
		}
		return Component.translatable("kultivator.kota_tema.pelabuhan.galangan.tidak_ada");
	}

	/** Re-paints the held keel's hull livery; the result line. */
	public static Component layar(ServerPlayer player, String styleId) {
		KapalStyle style = null;
		for (KapalStyle s : KapalStyle.values()) {
			if (s.id().equals(styleId)) {
				style = s;
			}
		}
		if (style == null || style.unlock() != KapalStyle.Unlock.NONE) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.galangan.gaya_terkunci");
		}
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		KapalClass kind = null;
		for (KapalClass k : KapalClass.values()) {
			if (held.is(KapalItems.keel(k))) {
				kind = k;
			}
		}
		if (kind == null) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.galangan.pegang_lunas");
		}
		if (!EkonomiApi.bayar(player, HARGA_LAYAR, "galangan.layar")) {
			return Component.translatable("kultivator.kota_tema.pelabuhan.kurang_uang");
		}
		KapalItems.State old = held.get(KapalItems.STATE);
		KapalItems.State now = old == null ? new KapalItems.State(kind.hull(), 0L, KapalRohEntity.DEFAULT_COLOR, false, "", style.ordinal())
				: new KapalItems.State(old.hull(), old.fuel(), old.color(), old.broken(), old.name(), style.ordinal());
		held.set(KapalItems.STATE, now);
		player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_LEATHERWORKER, SoundSource.PLAYERS, 0.8F, 1.0F);
		return Component.translatable("kultivator.kota_tema.pelabuhan.galangan.gaya_baru", Component.translatable("kultivator.kapal.style." + style.id()));
	}

	// ================================================================== slipways

	/** From the {@code pelabuhan/galangan} anchor: the hull of the slipway's oldest unfinished order. */
	static void landasanDekat(ServerLevel level, DuniaAnchorEntity anchor, PelabuhanKota.Hidup kota, PelabuhanAnchors handler) {
		int landasan = PelabuhanAnchors.angka(anchor, "landasan", 0);
		long today = PelabuhanKota.hari(level);
		Pesanan show = null;
		for (Tag t : daftar(level.getServer(), kota.situs)) {
			if (t instanceof CompoundTag c && c.getIntOr("landasan", 0) == landasan) {
				Pesanan p = Pesanan.dari(c);
				if (show == null || p.mulai() < show.mulai()) {
					show = p;
				}
			}
		}
		if (show == null) {
			handler.lepas(anchor);
			return;
		}
		KapalGalanganEntity hull = handler.mekanisme(level, anchor, kota, TemaEntities.KAPAL_GALANGAN);
		if (hull == null) {
			return;
		}
		hull.setVarian("sekte".equals(show.kelas()) ? KapalGalanganEntity.VARIAN_KAPAL_SEKTE : KapalGalanganEntity.VARIAN_PERAHU_AWAN);
		int stage = show.tahap(today);
		if (Math.round(hull.nilai()) != stage || hull.mulai() == 0) {
			hull.setNilai(stage);
			hull.setKeadaan(KapalGalanganEntity.KEADAAN_TAHAP_BARU);
			level.playSound(null, hull.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		} else if (hull.keadaan() == KapalGalanganEntity.KEADAAN_TAHAP_BARU && hull.umurKeadaan(level.getGameTime()) > 40) {
			hull.setKeadaan(KapalGalanganEntity.KEADAAN_DIAM);
		}
	}

	static void landasanLepas(DuniaAnchorEntity anchor) {
		// the body is removed by the handler's lepas
	}

	private Galangan() {
	}
}
