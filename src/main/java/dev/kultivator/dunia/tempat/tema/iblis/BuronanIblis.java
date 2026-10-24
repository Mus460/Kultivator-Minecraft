package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.JenisMisi;
import dev.kultivator.dunia.tempat.ekonomi.MisiGilda;
import dev.kultivator.dunia.tempat.ekonomi.SenjataKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.PapanHidup;
import dev.kultivator.dunia.tempat.ui.PapanHidupEntity;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Papan Buronan Iblis (DESIGN 38.T.7.9): 3-6 named demonic fugitives per known Kota Iblis, issued in the name of the
 * nearest righteous sect ({@code DiplomasiService.nearest(pos, 0)}; none: the Cultivator Guild).
 * <ul>
 * <li>Each fugitive is data in the town compound ({@code buronan}): look (name, face, nickname), realm tier, the crime,
 * the issuing sect. On two days of every four he is out of the city (hunting or trading) on the road toward the nearest
 * demon lair or a point 200-420 blocks out; on the other days he is in the city, outside the Pact Zone.</li>
 * <li>A body (PEMBUNUH_BAYARAN or WARGA_IBLIS, a realm foe, with 2-3 guards) appears when a player who took the bounty
 * comes within 64 blocks. Killing him inside the city: the enforcers hunt the killer for a day and Gentar -10.</li>
 * <li>Handing in at the hunters' camp (Han Qingshuang, the camp hunters, the board): {@code MisiGilda} reward of rank B..S
 * by his tier, standing +3 with the issuing sect, Infami -3, guild points, 20 % his treasure (a city weapon of grade
 * roh). The kill also completes a running guild mission of kind {@code buronan_iblis} ({@code EkonomiApi.misiSelesai}).</li>
 * <li>A slain fugitive is replaced after 5 world days. The black-auction lot Kontrak Kepala names the strongest one and
 * where he roams today; Han buys such a contract for 150 NB.</li>
 * </ul>
 */
public final class BuronanIblis {

	public static final String JENIS_MISI = "buronan_iblis";
	public static final int MIN = 3;
	public static final int MAKS = 6;
	public static final int GANTI_HARI = 5;
	public static final int KEJAHATAN = 12;
	public static final double MUNCUL = 64.0D;
	public static final double LEPAS = 128.0D;
	public static final int INFAMI = -3;
	public static final int SIKAP = 3;
	public static final double PELUANG_BARANG = 0.20D;
	public static final long HARGA_KONTRAK_KEPALA = 150L;

	/** Live bodies: "situs:id" -> the fugitive and his guards. */
	private static final Map<String, List<KultivatorNpc>> HIDUP = new HashMap<>();

	private BuronanIblis() {
	}

	static void init() {
		EkonomiApi.daftarJenisMisi(new JenisMisi(JENIS_MISI, JenisMisi.B, JenisMisi.S, "kota_tema/iblis", 6, "38TK"));
		ServerLivingEntityEvents.AFTER_DEATH.register(BuronanIblis::mati);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player instanceof ServerPlayer p && stack.is(TemaItems.KONTRAK_KEPALA)) {
				return bacaKontrak(p, stack);
			}
			return InteractionResult.PASS;
		});
	}

	static void clear() {
		HIDUP.clear();
	}

	// ================================================================== the list

	/** The fugitives of a Kota Iblis (made and replaced on demand). */
	static ListTag daftar(ServerLevel level, TemaKota.Kota kota) {
		MinecraftServer server = level.getServer();
		CompoundTag town = IblisData.kota(server, kota.situsId());
		ListTag list = IblisData.daftar(town, "buronan");
		long hari = IblisKota.hari(level);
		boolean changed = false;
		if (!town.getBooleanOr("buronan_awal", false)) {
			town.putBoolean("buronan_awal", true);
			int n = MIN + (int) Math.floorMod(kota.situsId() >>> 9, (long) (MAKS - MIN + 1));
			for (int i = 0; i < n; i++) {
				list.add(baru(kota, town, i, hari));
			}
			changed = true;
		}
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i) instanceof CompoundTag c && c.getBooleanOr("tewas", false) && hari - c.getLongOr("tewas_hari", hari) >= GANTI_HARI
					&& c.getBooleanOr("diklaim", false)) {
				list.set(i, baru(kota, town, 100 + (int) hari + i, hari));
				changed = true;
			}
		}
		if (changed) {
			IblisData.ubah(server);
		}
		return list;
	}

	private static CompoundTag baru(TemaKota.Kota kota, CompoundTag town, int salt, long hari) {
		int seq = town.getIntOr("buronan_seq", 0) + 1;
		town.putInt("buronan_seq", seq);
		long h = kota.situsId() * 0x9E3779B97F4A7C15L ^ (long) salt * 0xBF58476D1CE4E5B9L ^ seq * 0x94D049BB133111EBL;
		CompoundTag c = new CompoundTag();
		c.putInt("id", seq);
		c.putInt("look", (int) (h >>> 16));
		c.putInt("tier", 3 + (int) Math.floorMod(h >>> 5, 4L));
		c.putInt("kejahatan", (int) Math.floorMod(h >>> 23, (long) KEJAHATAN));
		c.putInt("korban", 3 + (int) Math.floorMod(h >>> 35, 14L));
		c.putInt("fase", (int) Math.floorMod(h >>> 41, 4L));
		c.putInt("arah", (int) Math.floorMod(h >>> 47, 360L));
		c.putLong("sejak", hari);
		String sekte = SekteApi.diplomasi().nearest(kota.pusat(), 0);
		c.putString("sekte", sekte == null ? "" : sekte);
		return c;
	}

	static Component nama(CompoundTag c) {
		int look = c.getIntOr("look", 0);
		return Component.translatable("kultivator.kota_tema.iblis.buronan.nama", KotaNames.person(look, Math.floorMod(look, 5) == 0),
				Component.translatable("kultivator.kota_tema.iblis.julukan." + Math.floorMod(look >>> 13, 16)));
	}

	static Component kejahatan(CompoundTag c, ServerLevel level) {
		int k = c.getIntOr("kejahatan", 0);
		Component tempat = Component.translatable("kultivator.kota_tema.iblis.buronan.dusun." + Math.floorMod(c.getIntOr("look", 0) >>> 7, 10));
		return Component.translatable("kultivator.kota_tema.iblis.buronan.kejahatan." + k, tempat, c.getIntOr("korban", 5));
	}

	static int pangkat(CompoundTag c) {
		return Mth.clamp(JenisMisi.B + (c.getIntOr("tier", 3) - 3) * 2 / 3, JenisMisi.B, JenisMisi.S);
	}

	static long imbalan(ServerPlayer p, CompoundTag c) {
		return Math.round(SarangKontrak.imbalan(IblisKota.band(p), pangkat(c)) / SarangKontrak.FAKTOR);
	}

	/** True when the fugitive is outside the city on world day {@code hari} (two days of every four). */
	static boolean diLuar(CompoundTag c, long hari) {
		return Math.floorMod(hari + c.getIntOr("fase", 0), 4L) < 2L;
	}

	/** Where the fugitive roams on world day {@code hari}: on the road toward the nearest lair, or a street of the city. */
	static BlockPos posisi(ServerLevel level, TemaKota.Kota kota, CompoundTag c, long hari) {
		if (!diLuar(c, hari)) {
			String[] streets = {"kumuh_iblis", "sarang_kontrak", "arena_darah", "gerbang_tengkorak"};
			String d = streets[Math.floorMod(c.getIntOr("look", 0) + (int) hari, streets.length)];
			BlockPos at = IblisKota.blok(kota, d, 40 + Math.floorMod(c.getIntOr("id", 0) * 7, 16), IblisKota.LY + 1, 44 + Math.floorMod((int) hari * 5, 10));
			return at == null ? kota.pusat() : at;
		}
		double angle = Math.toRadians(c.getIntOr("arah", 0) + (hari % 3) * 25.0D);
		int dist = 200 + (int) Math.floorMod(hari * 37L + c.getIntOr("id", 0) * 53L, 220L);
		return kota.pusat().offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist));
	}

	/** The Kota Iblis a board or a hunter serves: the camp's own town, else the nearest within 3000 blocks. */
	static @Nullable TemaKota.Kota kotaUntuk(ServerLevel level, BlockPos at) {
		TemaKota.Kota kota = PemburuLurus.kotaKemah(level, at);
		if (kota == null) {
			kota = IblisKota.di(level, at);
		}
		if (kota == null) {
			kota = IblisKota.terdekat(level, at, 3000);
		}
		return kota;
	}

	// ================================================================== the board anchor

	/** Anchor {@code kota_tema/papan_buronan}: a wanted-poster board with the town's fugitives and their bounties. */
	static void nearbyPapan(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		if (within.isEmpty() || (level.getGameTime() + anchor.getId()) % 200L != 0L) {
			return;
		}
		TemaKota.Kota kota = kotaUntuk(level, anchor.blockPosition());
		PapanHidupEntity board = PapanHidup.pasang(level, anchor.blockPosition().below(), anchor.getYRot(), PapanHidupEntity.JENIS_BURONAN);
		if (board == null) {
			return;
		}
		IblisAnchors.simpan(anchor, board);
		if (kota == null) {
			board.setLembar(List.of(Component.translatable("kultivator.kota_tema.iblis.buronan.papan.kosong")), List.of());
			return;
		}
		List<Component> titles = new ArrayList<>();
		List<Component> lines = new ArrayList<>();
		long best = 0L;
		String bestName = "";
		ServerPlayer viewer = within.getFirst();
		for (Tag t : daftar(level, kota)) {
			if (t instanceof CompoundTag c && !c.getBooleanOr("tewas", false) && titles.size() < 6) {
				titles.add(nama(c));
				long reward = imbalan(viewer, c);
				lines.add(Component.translatable("kultivator.kota_tema.iblis.buronan.papan.baris", kejahatan(c, level), NilaiBatu.text(reward)));
				if (reward > best) {
					best = reward;
					bestName = nama(c).getString();
				}
			}
		}
		board.setLembar(titles, lines);
		board.setBuronan(bestName, best);
	}

	// ================================================================== the screen (tema_kontrak in bounty mode)

	static @Nullable CompoundTag bangun(ServerPlayer p, @Nullable KultivatorNpc npc) {
		ServerLevel level = p.level();
		TemaKota.Kota kota = kotaUntuk(level, npc != null ? npc.blockPosition() : p.blockPosition());
		CompoundTag tag = IblisLayar.kepala(p, "kultivator.kota_tema.iblis.layar.buronan", LembarKunci.GAYA_KERTAS, npc);
		if (kota == null) {
			IblisLayar.kosong(tag, Component.translatable("kultivator.kota_tema.iblis.buronan.papan.kosong"));
			tag.put(TemaLayar.Kontrak.KONTRAK, new ListTag());
			tag.put(TemaLayar.Kontrak.AKTIF_KONTRAK, new ListTag());
			return tag;
		}
		long hari = IblisKota.hari(level);
		ListTag mine = ambilan(p);
		ListTag rows = new ListTag();
		ListTag aktif = new ListTag();
		for (Tag t : daftar(level, kota)) {
			if (!(t instanceof CompoundTag c)) {
				continue;
			}
			String ref = ref(kota.situsId(), c);
			boolean dead = c.getBooleanOr("tewas", false);
			boolean claimed = c.getBooleanOr("diklaim", false);
			boolean taken = cari(mine, ref) != null;
			boolean killedByMe = dead && p.getUUID().toString().equals(c.getStringOr("pembunuh", ""));
			BlockPos at = posisi(level, kota, c, hari);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, ref);
			LembarKunci.teks(row, LembarKunci.NAMA, nama(c));
			LembarKunci.teks(row, TemaLayar.Kontrak.SASARAN, kejahatan(c, level));
			row.putString("jenis", "buronan");
			row.putString(TemaLayar.Kontrak.PANGKAT, String.valueOf(JenisMisi.HURUF.charAt(pangkat(c))));
			row.putLong(TemaLayar.Kontrak.IMBALAN, imbalan(p, c));
			row.putInt(TemaLayar.Kontrak.INFAMI, INFAMI);
			row.putInt(TemaLayar.Kontrak.GENTAR, 0);
			row.putInt("tier", c.getIntOr("tier", 3));
			row.putInt("jarak", taken ? (int) Math.sqrt(at.distSqr(p.blockPosition())) : -1);
			row.putInt("x", taken ? at.getX() : 0);
			row.putInt("z", taken ? at.getZ() : 0);
			Component issuer = SekteApi.diplomasi().name(c.getStringOr("sekte", ""));
			LembarKunci.teks(row, "penerbit", issuer != null ? issuer : Component.translatable("kultivator.kota_tema.iblis.pemburu.gilda"));
			row.putString(LembarKunci.KEADAAN, claimed || dead && !killedByMe ? LembarKunci.K_HABIS : taken ? LembarKunci.K_BATAS : LembarKunci.K_OK);
			rows.add(row);
			if (taken || killedByMe && !claimed) {
				CompoundTag a = new CompoundTag();
				a.putString(LembarKunci.ID, ref);
				LembarKunci.teks(a, LembarKunci.NAMA, nama(c));
				a.putInt("sisa_hari", -1);
				a.putFloat("kemajuan", killedByMe ? 1.0F : 0.0F);
				a.putInt("x", at.getX());
				a.putInt("z", at.getZ());
				LembarKunci.teks(a, "tempat", Component.translatable(diLuar(c, hari) ? "kultivator.kota_tema.iblis.buronan.di_luar" : "kultivator.kota_tema.iblis.buronan.di_kota"));
				aktif.add(a);
			}
		}
		tag.put(TemaLayar.Kontrak.KONTRAK, rows);
		tag.put(TemaLayar.Kontrak.AKTIF_KONTRAK, aktif);
		return tag;
	}

	static void aksi(ServerPlayer p, @Nullable KultivatorNpc npc, String[] parts) {
		if (parts.length < 2) {
			return;
		}
		ServerLevel level = p.level();
		TemaKota.Kota kota = kotaUntuk(level, npc != null ? npc.blockPosition() : p.blockPosition());
		if (kota == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		ListTag list = daftar(level, kota);
		ListTag mine = ambilan(p);
		String ref = parts[1];
		CompoundTag c = byRef(list, kota.situsId(), ref);
		if (c == null) {
			return;
		}
		switch (parts[0]) {
			case TemaLayar.Kontrak.OP_AMBIL -> {
				if (c.getBooleanOr("tewas", false) || cari(mine, ref) != null) {
					return;
				}
				CompoundTag e = new CompoundTag();
				e.putString("ref", ref);
				e.putLong("situs", kota.situsId());
				mine.add(e);
				IblisData.ubah(server);
				BlockPos at = posisi(level, kota, c, IblisKota.hari(level));
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.buronan.ambil", nama(c), at.getX(), at.getZ()), true);
			}
			case TemaLayar.Kontrak.OP_BATAL -> {
				CompoundTag e = cari(mine, ref);
				if (e != null) {
					mine.remove(e);
					IblisData.ubah(server);
					lepas(kota.situsId() + ":" + c.getIntOr("id", 0));
					IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.kontrak.batal"), true);
				}
			}
			case TemaLayar.Kontrak.OP_SERAH -> {
				if (!c.getBooleanOr("tewas", false) || c.getBooleanOr("diklaim", false) || !p.getUUID().toString().equals(c.getStringOr("pembunuh", ""))) {
					IblisLayar.peringatan(p, Component.translatable("kultivator.kota_tema.iblis.buronan.belum"));
					return;
				}
				c.putBoolean("diklaim", true);
				CompoundTag e = cari(mine, ref);
				if (e != null) {
					mine.remove(e);
				}
				IblisData.ubah(server);
				klaim(p, c);
			}
			default -> {
			}
		}
	}

	private static void klaim(ServerPlayer p, CompoundTag c) {
		long reward = imbalan(p, c);
		NilaiBatu.give(p, reward);
		int rank = pangkat(c);
		EkonomiApi.poinGilda(p, MisiGilda.POIN[rank]);
		String sekte = c.getStringOr("sekte", "");
		if (!sekte.isEmpty()) {
			SekteApi.diplomasi().adjust(p.getUUID(), sekte, SIKAP, "38tk.buronan");
		}
		IblisMeter.tambahInfami(p, INFAMI, "buronan");
		if (p.getRandom().nextDouble() < PELUANG_BARANG) {
			SenjataKota.Bentuk[] shapes = SenjataKota.Bentuk.values();
			ItemStack treasure = SenjataKota.stack(shapes[Math.floorMod(c.getIntOr("look", 0), shapes.length)], SenjataKota.Mutu.values()[Math.min(2,
					SenjataKota.Mutu.values().length - 1)]);
			Barang.beri(p, treasure);
			Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.buronan.pusaka", treasure.getHoverName()).withStyle(ChatFormatting.GOLD));
		}
		Advancements.grant(p, "tempat/tema_iblis_buronan");
		CeritaApi.peristiwa(p, IblisCerita.BURONAN, "klaim");
		IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.buronan.klaim", nama(c), NilaiBatu.text(reward)), true);
	}

	private static ListTag ambilan(ServerPlayer p) {
		return IblisData.daftar(IblisData.pemain(p.level().getServer(), p.getUUID()), "buronan");
	}

	static String ref(long situs, CompoundTag c) {
		return situs + ":" + c.getIntOr("id", 0);
	}

	private static @Nullable CompoundTag cari(ListTag mine, String ref) {
		for (Tag t : mine) {
			if (t instanceof CompoundTag e && ref.equals(e.getStringOr("ref", ""))) {
				return e;
			}
		}
		return null;
	}

	private static @Nullable CompoundTag byRef(ListTag list, long situs, String ref) {
		for (Tag t : list) {
			if (t instanceof CompoundTag c && ref.equals(ref(situs, c))) {
				return c;
			}
		}
		return null;
	}

	// ================================================================== bodies

	/** Every 40 ticks per online player with taken bounties: the fugitive appears within 64 blocks, leaves past 128. */
	static void tickPemain(ServerPlayer p) {
		ListTag mine = IblisData.pemain(p.level().getServer(), p.getUUID()).getListOrEmpty("buronan");
		if (mine.isEmpty() || p.level().dimension() != Level.OVERWORLD) {
			return;
		}
		ServerLevel level = p.level();
		long hari = IblisKota.hari(level);
		for (Tag t : mine) {
			if (!(t instanceof CompoundTag e)) {
				continue;
			}
			TemaKota.Kota kota = IblisKota.dari(level.getServer(), e.getLongOr("situs", 0L));
			if (kota == null) {
				continue;
			}
			CompoundTag c = byRef(daftar(level, kota), kota.situsId(), e.getStringOr("ref", ""));
			if (c == null || c.getBooleanOr("tewas", false)) {
				continue;
			}
			String key = kota.situsId() + ":" + c.getIntOr("id", 0);
			BlockPos at = posisi(level, kota, c, hari);
			double dx = p.getX() - at.getX();
			double dz = p.getZ() - at.getZ();
			double d2 = dx * dx + dz * dz;
			List<KultivatorNpc> live = HIDUP.get(key);
			if (live != null) {
				live.removeIf(n -> n.isRemoved());
				if (d2 > LEPAS * LEPAS || live.isEmpty()) {
					lepas(key);
				}
				continue;
			}
			if (d2 < MUNCUL * MUNCUL) {
				munculkan(level, p, kota, c, at, key);
			}
		}
	}

	private static void munculkan(ServerLevel level, ServerPlayer p, TemaKota.Kota kota, CompoundTag c, BlockPos at, String key) {
		BlockPos ground = IblisKota.di(level, at) != null ? at : TempatApi.groundSpot(level, at.getX(), at.getZ(), p.getBlockY());
		if (ground == null || !level.isPositionEntityTicking(ground)) {
			return;
		}
		int look = c.getIntOr("look", 0);
		int tier = Mth.clamp(c.getIntOr("tier", 3), 1, 8);
		int realm = Mth.clamp(Realm.FOUNDATION.ordinal() + tier - 2, 0, Realm.values().length - 1);
		NpcRole role = Math.floorMod(look, 2) == 0 ? NpcRole.PEMBUNUH_BAYARAN : NpcRole.WARGA_IBLIS;
		KultivatorNpc boss = HidupApi.tubuh(level, kota.situsId(), role, ground, p.getYRot() + 180.0F, look);
		if (boss == null) {
			return;
		}
		Component plate = Component.translatable("kultivator.kota.papan_nama", nama(c), Component.translatable("kultivator.kota_tema.iblis.buronan.gelar"));
		boss.setup(role, 1 + tier / 2, look, realm, 2, boss.dao(), boss.voice(), boss.robe(), plate);
		boss.memory().putString("iblis_buronan", key);
		boss.memory().putBoolean("hold", false);
		boss.setDiamDiTitik(false);
		boss.arm(true);
		TechniquePower.markRealmFoe(boss);
		boss.markHostile(p);
		List<KultivatorNpc> group = new ArrayList<>();
		group.add(boss);
		int guards = 2 + Math.floorMod(look >>> 3, 2);
		for (int i = 0; i < guards; i++) {
			KultivatorNpc g = HidupApi.tubuh(level, kota.situsId(), NpcRole.WARGA_IBLIS, ground.offset(i % 2 == 0 ? 2 : -2, 0, i), p.getYRot() + 180.0F,
					look * 31 + i * 977);
			if (g == null) {
				continue;
			}
			g.setup(NpcRole.WARGA_IBLIS, Math.max(1, tier / 2), g.look(), Math.max(0, realm - 1), 1, g.dao(), g.voice(), g.robe(), g.plate());
			g.memory().putString("iblis_buronan_pengawal", key);
			g.memory().putBoolean("hold", false);
			g.arm(true);
			TechniquePower.markRealmFoe(g);
			g.markHostile(p);
			group.add(g);
		}
		HIDUP.put(key, group);
		HidupApi.klip(boss, GerakId.ANCAM, 0, 40);
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.buronan.ketemu", nama(c)).withStyle(ChatFormatting.DARK_RED));
	}

	private static void lepas(String key) {
		List<KultivatorNpc> live = HIDUP.remove(key);
		if (live != null) {
			for (KultivatorNpc n : live) {
				if (!n.isRemoved()) {
					n.vanish();
				}
			}
		}
	}

	private static void mati(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof KultivatorNpc npc) || !npc.memory().contains("iblis_buronan") || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		String key = npc.memory().getStringOr("iblis_buronan", "");
		lepas(key);
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer sp ? sp : null;
		int colon = key.indexOf(':');
		if (colon < 0) {
			return;
		}
		long situs;
		int id;
		try {
			situs = Long.parseLong(key.substring(0, colon));
			id = Integer.parseInt(key.substring(colon + 1));
		} catch (NumberFormatException e) {
			return;
		}
		CompoundTag town = IblisData.kota(level.getServer(), situs);
		CompoundTag c = IblisData.cari(IblisData.daftar(town, "buronan"), id);
		if (c == null || c.getBooleanOr("tewas", false)) {
			return;
		}
		c.putBoolean("tewas", true);
		c.putLong("tewas_hari", IblisKota.hari(level));
		c.putString("pembunuh", killer == null ? "" : killer.getUUID().toString());
		if (killer == null) {
			c.putBoolean("diklaim", true);
		}
		IblisData.ubah(level.getServer());
		if (killer == null) {
			return;
		}
		Msg.title(killer, Component.translatable("kultivator.kota_tema.iblis.buronan.tewas").withStyle(ChatFormatting.GOLD), nama(c).copy().withStyle(ChatFormatting.YELLOW),
				5, 60, 15);
		EkonomiApi.misiSelesai(killer, JENIS_MISI);
		CeritaApi.peristiwa(killer, IblisCerita.BURONAN, "bunuh");
		CeritaApi.kabar(level, situs, "tema_iblis_buronan_tewas", nama(c), killer.getName().getString());
		TemaKota.Kota kota = IblisKota.di(level, entity.blockPosition());
		if (kota != null && kota.situsId() == situs) {
			// a fugitive is still a citizen: killing him inside the walls is a grudge of the enforcers
			IblisData.setSampai(level.getServer(), situs, killer.getUUID(), "langgar_sampai", ZonaPerjanjian.LANGGAR_TICK);
			IblisMeter.tambahGentar(killer, situs, -10, "bunuh_warga");
		}
	}

	// ================================================================== Kontrak Kepala

	/** Reading a Kontrak Kepala: the strongest living fugitive of the nearest Kota Iblis, taken as a bounty, his roam today. */
	private static InteractionResult bacaKontrak(ServerPlayer p, ItemStack stack) {
		ServerLevel level = p.level();
		TemaKota.Kota kota = IblisKota.terdekat(level, p.blockPosition(), 3000);
		if (kota == null) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.buronan.papan.kosong").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		CompoundTag best = null;
		for (Tag t : daftar(level, kota)) {
			if (t instanceof CompoundTag c && !c.getBooleanOr("tewas", false) && (best == null || c.getIntOr("tier", 0) > best.getIntOr("tier", 0))) {
				best = c;
			}
		}
		if (best == null) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.buronan.papan.kosong").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}
		String ref = ref(kota.situsId(), best);
		ListTag mine = ambilan(p);
		if (cari(mine, ref) == null) {
			CompoundTag e = new CompoundTag();
			e.putString("ref", ref);
			e.putLong("situs", kota.situsId());
			mine.add(e);
			IblisData.ubah(level.getServer());
		}
		BlockPos at = posisi(level, kota, best, IblisKota.hari(level));
		p.sendSystemMessage(Component.translatable("kultivator.kota_tema.iblis.buronan.kontrak_kepala", nama(best), kejahatan(best, level), at.getX(), at.getZ())
				.withStyle(ChatFormatting.DARK_RED));
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	/** Dialog effect {@code iblis_jual_kontrak}: Han buys one Kontrak Kepala for 150 NB (Infami -2). */
	static void jualKontrak(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(TemaItems.KONTRAK_KEPALA)) {
				s.shrink(1);
				NilaiBatu.give(p, HARGA_KONTRAK_KEPALA);
				IblisMeter.tambahInfami(p, -2, "jual_kontrak");
				Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.buronan.jual", NilaiBatu.text(HARGA_KONTRAK_KEPALA)).withStyle(ChatFormatting.GREEN));
				return;
			}
		}
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.buronan.tanpa_kontrak").withStyle(ChatFormatting.GRAY));
	}

	/** {@code uji tema buronan buat}: a fugitive body right by the caller (taken as his bounty). */
	static boolean buatUji(ServerPlayer p) {
		ServerLevel level = p.level();
		TemaKota.Kota kota = IblisKota.terdekat(level, p.blockPosition(), 3000);
		if (kota == null) {
			return false;
		}
		for (Tag t : daftar(level, kota)) {
			if (t instanceof CompoundTag c && !c.getBooleanOr("tewas", false)) {
				String key = kota.situsId() + ":" + c.getIntOr("id", 0);
				lepas(key);
				ListTag mine = ambilan(p);
				String ref = ref(kota.situsId(), c);
				if (cari(mine, ref) == null) {
					CompoundTag e = new CompoundTag();
					e.putString("ref", ref);
					e.putLong("situs", kota.situsId());
					mine.add(e);
				}
				munculkan(level, p, kota, c, p.blockPosition().offset(6, 0, 0), key);
				return true;
			}
		}
		return false;
	}

	/** {@code uji tema buronan daftar}: the fugitives of the nearest Kota Iblis. */
	static List<Component> status(ServerPlayer p) {
		List<Component> out = new ArrayList<>();
		ServerLevel level = p.level();
		TemaKota.Kota kota = IblisKota.terdekat(level, p.blockPosition(), 3000);
		if (kota == null) {
			return out;
		}
		long hari = IblisKota.hari(level);
		for (Tag t : daftar(level, kota)) {
			if (t instanceof CompoundTag c) {
				BlockPos at = posisi(level, kota, c, hari);
				out.add(Component.translatable("kultivator.kota_tema.iblis.cmd.buronan", c.getIntOr("id", 0), nama(c), c.getIntOr("tier", 0),
						c.getBooleanOr("tewas", false) ? "x" : diLuar(c, hari) ? "luar" : "kota", at.getX(), at.getZ()));
			}
		}
		return out;
	}
}
