package dev.kultivator.dunia.tempat.perbaikan;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code kultivator:tempat_rusak} in the overworld (DESIGN 38.R.7): the cared-for places, damage clusters with
 * their open operations and progress, bills, the fear meter of every place, seized goods, help points, refused-bill price
 * raises and donations. New keys only; the blueprints themselves live in their own region files ({@link CetakStore}).
 * Clusters are decoded lazily on the first {@link #get} (block states need the server's registries).
 */
public final class PerbaikanData extends SavedData {

	public static final Codec<PerbaikanData> CODEC = CompoundTag.CODEC.xmap(PerbaikanData::fromTag, PerbaikanData::toTag);
	public static final SavedDataType<PerbaikanData> TYPE = new SavedDataType<>(Kultivator.id("tempat_rusak"), PerbaikanData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	// ---------------------------------------------------------------------------------------------------------------
	// records

	/** Bill states. */
	public static final int T_MENUNGGU = 0;
	public static final int T_TERBUKA = 1;
	public static final int T_LUNAS = 2;
	public static final int T_DITOLAK = 3;
	public static final int T_KEDALUWARSA = 4;
	/** A fear visit (no bill asked): kept so "bayar walau tak diminta" knows the amount. */
	public static final int T_TAKUT = 5;

	/** A bill in the making or open (mutable side of the public {@link Tagihan} snapshot). */
	public static final class TagihanData {
		public final long id;
		public final long situsId;
		public final long kunci;
		public final UUID pembayar;
		public String nama = "";
		/** Sum of block worth per bill line group (NB, before factor and wage). */
		public final Map<String, Double> rincian = new LinkedHashMap<>();
		public final Map<String, Integer> jumlahBlok = new LinkedHashMap<>();
		public double faktor = 1.0D;
		public long jumlah;
		public final String gaya;
		public int status = T_MENUNGGU;
		public final long hariDibuat;
		/** Last world day the collector still comes (3 days, 38.R.4). */
		public long hariJatuhTempo;
		/** Game time the sheet was first shown (0 = not yet). */
		public long ditunjukkan;
		public boolean sudahTawar;
		/** Offer step of the haggle: -20 % or +10 % (in percent, 0 = none). */
		public int tawarPersen;
		public final List<Long> gugus = new ArrayList<>();

		TagihanData(long id, long situsId, long kunci, UUID pembayar, String gaya, long hariDibuat) {
			this.id = id;
			this.situsId = situsId;
			this.kunci = kunci;
			this.pembayar = pembayar;
			this.gaya = gaya;
			this.hariDibuat = hariDibuat;
			this.hariJatuhTempo = hariDibuat + 3;
		}

		/** Sum of block worth (NB, no factor). */
		public double nilaiBlok() {
			double sum = 0.0D;
			for (double v : rincian.values()) {
				sum += v;
			}
			return sum;
		}

		/** The public snapshot (contract C38.PR1). */
		public Tagihan snapshot() {
			return new Tagihan(id, situsId, pembayar, jumlah, hariDibuat, hariJatuhTempo, gaya);
		}

		CompoundTag simpan() {
			CompoundTag tag = new CompoundTag();
			tag.putLong("id", id);
			tag.putLong("situs", situsId);
			tag.putLong("k", kunci);
			tag.store("u", UUIDUtil.CODEC, pembayar);
			tag.putString("nama", nama);
			CompoundTag r = new CompoundTag();
			rincian.forEach(r::putDouble);
			tag.put("rincian", r);
			CompoundTag n = new CompoundTag();
			jumlahBlok.forEach(n::putInt);
			tag.put("blok", n);
			tag.putDouble("faktor", faktor);
			tag.putLong("jumlah", jumlah);
			tag.putString("gaya", gaya);
			tag.putInt("st", status);
			tag.putLong("hari", hariDibuat);
			tag.putLong("tempo", hariJatuhTempo);
			tag.putLong("lihat", ditunjukkan);
			tag.putBoolean("tawar", sudahTawar);
			tag.putInt("tawar_p", tawarPersen);
			tag.putLongArray("gugus", gugus.stream().mapToLong(Long::longValue).toArray());
			return tag;
		}

		static @Nullable TagihanData muat(CompoundTag tag) {
			UUID who = tag.read("u", UUIDUtil.CODEC).orElse(null);
			if (who == null) {
				return null;
			}
			TagihanData t = new TagihanData(tag.getLongOr("id", 0L), tag.getLongOr("situs", 0L), tag.getLongOr("k", 0L), who,
					tag.getStringOr("gaya", JenisTempat.GAYA_BIASA), tag.getLongOr("hari", 0L));
			t.nama = tag.getStringOr("nama", "");
			CompoundTag r = tag.getCompoundOrEmpty("rincian");
			for (String key : r.keySet()) {
				t.rincian.put(key, r.getDoubleOr(key, 0.0D));
			}
			CompoundTag n = tag.getCompoundOrEmpty("blok");
			for (String key : n.keySet()) {
				t.jumlahBlok.put(key, n.getIntOr(key, 0));
			}
			t.faktor = tag.getDoubleOr("faktor", 1.0D);
			t.jumlah = tag.getLongOr("jumlah", 0L);
			t.status = tag.getIntOr("st", T_MENUNGGU);
			t.hariJatuhTempo = tag.getLongOr("tempo", t.hariDibuat + 3);
			t.ditunjukkan = tag.getLongOr("lihat", 0L);
			t.sudahTawar = tag.getBooleanOr("tawar", false);
			t.tawarPersen = tag.getIntOr("tawar_p", 0);
			for (long g : tag.getLongArray("gugus").orElse(new long[0])) {
				t.gugus.add(g);
			}
			return t;
		}
	}

	/** The fear meter of one place (38.R.5). */
	public static final class TakutData {
		public int nilai;
		/** World day the meter last decayed. */
		public long hari;
		/** The last fear visit's would-be bill (for "bayar walau tak diminta"). */
		public long tagihanTerakhir;
		public @Nullable UUID pemicu;
	}

	/** One seized item (38.R.3 rule 4): a block a player placed in the place, kept 14 days for its owner. */
	public record SitaanItem(String item, int jumlah, @Nullable UUID pemilik, long hari) {
	}

	/** A player's standing with one place's repairs. */
	public static final class PemainTempat {
		public int poinBantu;
		public long hariBantu;
		public int bantuHariIni;
		public long hariDonasi;
		public int namaBaikDonasiHariIni;
		public long hargaNaikSampai;
		public int sisaAntar;
		public boolean sahabat;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// state

	final Map<Long, TempatRawat> tempat = new LinkedHashMap<>();
	final Map<Long, Gugus> gugus = new LinkedHashMap<>();
	final Map<Long, TagihanData> tagihan = new LinkedHashMap<>();
	final Map<Long, TakutData> takut = new HashMap<>();
	final Map<Long, List<SitaanItem>> sitaan = new HashMap<>();
	final Map<UUID, Map<Long, PemainTempat>> pemain = new HashMap<>();
	/** Place id -> game time until which donations double the repair rate. */
	final Map<Long, Long> donasiSampai = new HashMap<>();
	long nextId = 1L;
	private @Nullable ListTag gugusMentah;

	public PerbaikanData() {
	}

	/** The server's data, clusters decoded on first use. */
	public static PerbaikanData get(MinecraftServer server) {
		PerbaikanData data = server.overworld().getDataStorage().computeIfAbsent(TYPE);
		if (data.gugusMentah != null) {
			HolderGetter<Block> blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
			for (Tag t : data.gugusMentah) {
				if (t instanceof CompoundTag c) {
					Gugus g = Gugus.muat(c, blocks);
					if (g != null) {
						data.gugus.put(g.id, g);
					}
				}
			}
			data.gugusMentah = null;
		}
		return data;
	}

	long id() {
		setDirty();
		return nextId++;
	}

	public void kotor() {
		setDirty();
	}

	TakutData takut(long situsId) {
		return takut.computeIfAbsent(situsId, k -> new TakutData());
	}

	PemainTempat pemain(UUID who, long situsId) {
		return pemain.computeIfAbsent(who, k -> new HashMap<>()).computeIfAbsent(situsId, k -> new PemainTempat());
	}

	@Nullable PemainTempat pemainAda(UUID who, long situsId) {
		Map<Long, PemainTempat> map = pemain.get(who);
		return map == null ? null : map.get(situsId);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// saving

	private static PerbaikanData fromTag(CompoundTag tag) {
		PerbaikanData data = new PerbaikanData();
		data.nextId = Math.max(1L, tag.getLongOr("next", 1L));
		for (Tag t : tag.getListOrEmpty("tempat")) {
			if (t instanceof CompoundTag c) {
				TempatRawat r = TempatRawat.muat(c);
				if (r != null) {
					data.tempat.put(r.kunci, r);
				}
			}
		}
		data.gugusMentah = tag.getListOrEmpty("gugus");
		for (Tag t : tag.getListOrEmpty("tagihan")) {
			if (t instanceof CompoundTag c) {
				TagihanData b = TagihanData.muat(c);
				if (b != null) {
					data.tagihan.put(b.id, b);
				}
			}
		}
		for (Tag t : tag.getListOrEmpty("takut")) {
			if (t instanceof CompoundTag c) {
				TakutData f = new TakutData();
				f.nilai = c.getIntOr("n", 0);
				f.hari = c.getLongOr("hari", 0L);
				f.tagihanTerakhir = c.getLongOr("akhir", 0L);
				f.pemicu = c.read("u", UUIDUtil.CODEC).orElse(null);
				data.takut.put(c.getLongOr("situs", 0L), f);
			}
		}
		for (Tag t : tag.getListOrEmpty("sitaan")) {
			if (t instanceof CompoundTag c) {
				List<SitaanItem> list = new ArrayList<>();
				for (Tag it : c.getListOrEmpty("isi")) {
					if (it instanceof CompoundTag e) {
						list.add(new SitaanItem(e.getStringOr("i", "minecraft:air"), e.getIntOr("n", 1), e.read("u", UUIDUtil.CODEC).orElse(null),
								e.getLongOr("hari", 0L)));
					}
				}
				data.sitaan.put(c.getLongOr("situs", 0L), list);
			}
		}
		for (Tag t : tag.getListOrEmpty("pemain")) {
			if (!(t instanceof CompoundTag c)) {
				continue;
			}
			UUID who = c.read("u", UUIDUtil.CODEC).orElse(null);
			if (who == null) {
				continue;
			}
			Map<Long, PemainTempat> map = new HashMap<>();
			for (Tag e : c.getListOrEmpty("tempat")) {
				if (e instanceof CompoundTag p) {
					PemainTempat pt = new PemainTempat();
					pt.poinBantu = p.getIntOr("bantu", 0);
					pt.hariBantu = p.getLongOr("hari_bantu", 0L);
					pt.bantuHariIni = p.getIntOr("bantu_hari", 0);
					pt.hariDonasi = p.getLongOr("hari_donasi", 0L);
					pt.namaBaikDonasiHariIni = p.getIntOr("nb_donasi", 0);
					pt.hargaNaikSampai = p.getLongOr("harga", 0L);
					pt.sisaAntar = p.getIntOr("antar", 0);
					pt.sahabat = p.getBooleanOr("sahabat", false);
					map.put(p.getLongOr("situs", 0L), pt);
				}
			}
			data.pemain.put(who, map);
		}
		CompoundTag don = tag.getCompoundOrEmpty("donasi");
		for (String key : don.keySet()) {
			try {
				data.donasiSampai.put(Long.parseLong(key), don.getLongOr(key, 0L));
			} catch (NumberFormatException ignored) {
				// a damaged key is dropped
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putLong("next", nextId);
		ListTag places = new ListTag();
		for (TempatRawat r : tempat.values()) {
			places.add(r.simpan());
		}
		tag.put("tempat", places);
		if (gugusMentah != null) {
			tag.put("gugus", gugusMentah);
		} else {
			ListTag clusters = new ListTag();
			for (Gugus g : gugus.values()) {
				if (!g.tuntas()) {
					clusters.add(g.simpan());
				}
			}
			tag.put("gugus", clusters);
		}
		ListTag bills = new ListTag();
		for (TagihanData b : tagihan.values()) {
			bills.add(b.simpan());
		}
		tag.put("tagihan", bills);
		ListTag fear = new ListTag();
		takut.forEach((situs, f) -> {
			CompoundTag c = new CompoundTag();
			c.putLong("situs", situs);
			c.putInt("n", f.nilai);
			c.putLong("hari", f.hari);
			c.putLong("akhir", f.tagihanTerakhir);
			if (f.pemicu != null) {
				c.store("u", UUIDUtil.CODEC, f.pemicu);
			}
			fear.add(c);
		});
		tag.put("takut", fear);
		ListTag seized = new ListTag();
		sitaan.forEach((situs, list) -> {
			CompoundTag c = new CompoundTag();
			c.putLong("situs", situs);
			ListTag items = new ListTag();
			for (SitaanItem item : list) {
				CompoundTag e = new CompoundTag();
				e.putString("i", item.item());
				e.putInt("n", item.jumlah());
				if (item.pemilik() != null) {
					e.store("u", UUIDUtil.CODEC, item.pemilik());
				}
				e.putLong("hari", item.hari());
				items.add(e);
			}
			c.put("isi", items);
			seized.add(c);
		});
		tag.put("sitaan", seized);
		ListTag players = new ListTag();
		pemain.forEach((who, map) -> {
			CompoundTag c = new CompoundTag();
			c.store("u", UUIDUtil.CODEC, who);
			ListTag list = new ListTag();
			map.forEach((situs, pt) -> {
				CompoundTag p = new CompoundTag();
				p.putLong("situs", situs);
				p.putInt("bantu", pt.poinBantu);
				p.putLong("hari_bantu", pt.hariBantu);
				p.putInt("bantu_hari", pt.bantuHariIni);
				p.putLong("hari_donasi", pt.hariDonasi);
				p.putInt("nb_donasi", pt.namaBaikDonasiHariIni);
				p.putLong("harga", pt.hargaNaikSampai);
				p.putInt("antar", pt.sisaAntar);
				p.putBoolean("sahabat", pt.sahabat);
				list.add(p);
			});
			c.put("tempat", list);
			players.add(c);
		});
		tag.put("pemain", players);
		CompoundTag don = new CompoundTag();
		donasiSampai.forEach((situs, until) -> don.putLong(Long.toString(situs), until));
		tag.put("donasi", don);
		return tag;
	}
}
