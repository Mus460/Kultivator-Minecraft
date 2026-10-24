package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ekonomi.JenisMisi;
import dev.kultivator.dunia.tempat.ekonomi.MisiGilda;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.bersama.TemaLayar;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sarang Kontrak (DESIGN 38.T.7.7, 38.T.7.11 bab 3): the assassins' guild of the Kota Iblis, its contract board
 * ({@code tema_kontrak}, anchor {@code kota_tema/kontrak}, also Nyonya Bai She) and the Shadow Oil store of chapter 3
 * (anchor {@code kota_tema/gudang_minyak}, six {@link TongMinyakEntity} barrels).
 * <p>
 * Contracts are offered <b>only</b> against: demon beasts and elites of the wild ({@code binatang}), rival demonic
 * cultivator bands ({@code iblis_saingan}, rogue cultivators of variant DEMONIC with role QUEST), bandit bands
 * ({@code begal}, rogues of role QUEST at a real bandit camp when one is known) and righteous hunter squads that hunt the
 * town's people ({@code pemburu}, PEMBURU_LURUS bodies by the hunters' camp). Never townsfolk, villagers, disciples or
 * other players: the board has no such kind and the foes are made for the contract.
 * <ul>
 * <li>Five contracts a day per town (seeded by town and day), ranks from the player's band ({@code MisiGilda.PITA_MIN}),
 * reward {@code MisiGilda} rank reward x 1.3 (the midpoint of the band's range), Infami +5 (beasts +1), Gentar +3.</li>
 * <li>At most 2 running, 3 world days each. The foes appear when the player comes within 64 blocks of the mark and
 * vanish if he leaves for 128; when the last one falls the contract is ready to hand in here.</li>
 * </ul>
 */
public final class SarangKontrak {

	public static final String DISTRIK = "sarang_kontrak";
	public static final int TAWARAN = 5;
	public static final int AKTIF_MAKS = 2;
	public static final int BATAS_HARI = 3;
	public static final double FAKTOR = 1.3D;
	public static final int INFAMI = 5;
	public static final int INFAMI_BINATANG = 1;
	public static final int GENTAR = 3;
	public static final double MUNCUL_JARAK = 64.0D;
	public static final double LEPAS_JARAK = 128.0D;
	public static final String TAG = "kultivator.iblis_kontrak";

	public static final String BINATANG = "binatang";
	public static final String SAINGAN = "iblis_saingan";
	public static final String BEGAL = "begal";
	public static final String PEMBURU = "pemburu";
	static final String[] JENIS = {BINATANG, BINATANG, SAINGAN, BEGAL, PEMBURU};

	/** Barrel spots round the anchor (tools/gen_tempat_tema_iblis.py GUDANG_TONG). */
	static final int[][] TONG = {{-4, -4}, {0, -4}, {4, -4}, {-4, 4}, {0, 4}, {4, 4}};
	/** Days until Bai She restocks a sabotaged store. */
	public static final int TONG_PULIH_HARI = 7;

	/** One contract on today's board. */
	record Tawaran(int id, String jenis, int pangkat, long imbalan, int dx, int dz, int look, int tier) {

		int infami() {
			return BINATANG.equals(jenis) ? INFAMI_BINATANG : INFAMI;
		}
	}

	/** Live foes of a running contract: key "player|contract". */
	private static final Map<String, List<LivingEntity>> MUSUH = new HashMap<>();
	/** Town id -> the barrel bodies. */
	private static final Map<Long, TongMinyakEntity[]> TONG_HIDUP = new HashMap<>();

	private SarangKontrak() {
	}

	static void init() {
		TemaLayar.daftar(TemaLayar.Kontrak.LAYAR, SarangKontrak::bangun, SarangKontrak::aksi);
		ServerLivingEntityEvents.AFTER_DEATH.register(SarangKontrak::mati);
	}

	static void clear() {
		MUSUH.clear();
		TONG_HIDUP.clear();
	}

	// ================================================================== the board

	/** Today's offers of a town (a pure function of town, day and the player's band). */
	static List<Tawaran> papan(long situs, long hari, int band) {
		List<Tawaran> out = new ArrayList<>();
		int top = 0;
		for (int r = 0; r < MisiGilda.PITA_MIN.length; r++) {
			if (band >= MisiGilda.PITA_MIN[r]) {
				top = r;
			}
		}
		for (int i = 0; i < TAWARAN; i++) {
			long h = (situs * 0x9E3779B97F4A7C15L) ^ (hari * 0xC2B2AE3D27D4EB4FL) ^ ((i + 1) * 0x165667B19E3779F9L);
			h ^= h >>> 29;
			String jenis = JENIS[(int) Math.floorMod(h + i, (long) JENIS.length)];
			int pangkat = Mth.clamp(top - (int) Math.floorMod(h >>> 11, 2L), JenisMisi.E, JenisMisi.S);
			double angle = Math.floorMod(h >>> 17, 360L) * Math.PI / 180.0D;
			int dist = 180 + (int) Math.floorMod(h >>> 27, 320L);
			int look = (int) (h >>> 32);
			int tier = Mth.clamp(1 + pangkat * 3 / 5 + (int) Math.floorMod(h >>> 41, 2L), 1, 4);
			out.add(new Tawaran(i + 1, jenis, pangkat, imbalan(band, pangkat), (int) (Math.cos(angle) * dist), (int) (Math.sin(angle) * dist), look, tier));
		}
		return out;
	}

	/** {@code MisiGilda} rank reward for the band (the midpoint of the town board's range) x 1.3. */
	static long imbalan(int band, int pangkat) {
		long low;
		long high;
		if (band <= 1) {
			low = 6L;
			high = 12L;
		} else if (band == 2) {
			low = 15L;
			high = 30L;
		} else {
			low = 40L;
			high = 80L;
			for (int b = 4; b <= Math.min(band, 6); b++) {
				low *= 2L;
				high *= 2L;
			}
		}
		double mid = (low + high) / 2.0D * MisiGilda.PENGALI[Mth.clamp(pangkat, 0, 5)];
		return Math.max(1L, Math.round(mid * FAKTOR));
	}

	/** The world mark of an offer (a bandit camp or the hunters' camp when one is known near). */
	static BlockPos tanda(ServerLevel level, TemaKota.Kota kota, Tawaran t) {
		BlockPos base = kota.pusat().offset(t.dx(), 0, t.dz());
		String prefix = BEGAL.equals(t.jenis()) ? "pos/kemah_begal" : PEMBURU.equals(t.jenis()) ? "kota_tema/pos_pemburu" : null;
		if (prefix != null) {
			List<DuniaSites.Site> near = DuniaSites.near(level, kota.pusat(), 1600, prefix);
			if (!near.isEmpty()) {
				DuniaSites.Site s = near.get(Math.floorMod(t.look(), near.size()));
				return s.pos().offset(PEMBURU.equals(t.jenis()) ? 24 : 0, 0, PEMBURU.equals(t.jenis()) ? 10 : 0);
			}
		}
		return base;
	}

	private static ListTag aktif(ServerPlayer p) {
		return IblisData.daftar(IblisData.pemain(p.level().getServer(), p.getUUID()), "kontrak");
	}

	private static String kunci(UUID player, CompoundTag c) {
		return player + "|" + c.getLongOr("situs", 0L) + ":" + c.getLongOr("hari", 0L) + ":" + c.getIntOr("id", 0);
	}

	// ================================================================== the screen

	private static @Nullable CompoundTag bangun(ServerPlayer p, int npcId) {
		KultivatorNpc npc = IblisAnchors.npc(p, npcId);
		if (npc != null && (TokohIblis.adalah(npc, TokohIblis.HAN_QINGSHUANG) || npc.memory().getBooleanOr("iblis_pemburu", false))) {
			return BuronanIblis.bangun(p, npc);
		}
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null) {
			return BuronanIblis.bangun(p, npc);
		}
		CompoundTag tag = IblisLayar.kepala(p, "kultivator.kota_tema.iblis.layar.kontrak", LembarKunci.GAYA_KERTAS, npc);
		if (Gerbang.diburu(p, kota.situsId())) {
			IblisLayar.kunci(tag, Component.translatable("kultivator.kota_tema.iblis.kontrak.diburu"), null);
			return tag;
		}
		ServerLevel level = p.level();
		long hari = IblisKota.hari(level);
		int band = IblisKota.band(p);
		ListTag taken = aktif(p);
		ListTag rows = new ListTag();
		for (Tawaran t : papan(kota.situsId(), hari, band)) {
			BlockPos at = tanda(level, kota, t);
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, Integer.toString(t.id()));
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.kota_tema.iblis.kontrak.jenis." + t.jenis()));
			LembarKunci.teks(row, TemaLayar.Kontrak.SASARAN, sasaran(t));
			row.putString("jenis", t.jenis());
			row.putString(TemaLayar.Kontrak.PANGKAT, String.valueOf(JenisMisi.HURUF.charAt(t.pangkat())));
			row.putLong(TemaLayar.Kontrak.IMBALAN, t.imbalan());
			row.putInt(TemaLayar.Kontrak.INFAMI, t.infami());
			row.putInt(TemaLayar.Kontrak.GENTAR, GENTAR);
			row.putInt("jarak", (int) Math.sqrt(at.distSqr(p.blockPosition())));
			row.putInt("x", at.getX());
			row.putInt("z", at.getZ());
			boolean mine = cari(taken, kota.situsId(), hari, t.id()) != null;
			row.putString(LembarKunci.KEADAAN, mine ? LembarKunci.K_HABIS : taken.size() >= AKTIF_MAKS ? LembarKunci.K_BATAS
					: band < MisiGilda.PITA_MIN[t.pangkat()] ? LembarKunci.K_TERKUNCI : LembarKunci.K_OK);
			rows.add(row);
		}
		tag.put(TemaLayar.Kontrak.KONTRAK, rows);
		tag.put(TemaLayar.Kontrak.AKTIF_KONTRAK, aktifLembar(p, level, hari));
		return tag;
	}

	static ListTag aktifLembar(ServerPlayer p, ServerLevel level, long hari) {
		ListTag out = new ListTag();
		for (Tag tag : aktif(p)) {
			if (!(tag instanceof CompoundTag c)) {
				continue;
			}
			CompoundTag row = new CompoundTag();
			row.putString(LembarKunci.ID, c.getLongOr("situs", 0L) + ":" + c.getLongOr("hari", 0L) + ":" + c.getIntOr("id", 0));
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.kota_tema.iblis.kontrak.jenis." + c.getStringOr("jenis", BINATANG)));
			row.putInt("sisa_hari", (int) Math.max(0L, c.getLongOr("hari", hari) + BATAS_HARI - hari));
			row.putFloat("kemajuan", c.getBooleanOr("selesai", false) ? 1.0F : c.getBooleanOr("muncul", false) ? 0.5F : 0.0F);
			row.putInt("x", c.getIntOr("x", 0));
			row.putInt("z", c.getIntOr("z", 0));
			out.add(row);
		}
		return out;
	}

	private static Component sasaran(Tawaran t) {
		return switch (t.jenis()) {
			case BINATANG -> Component.translatable("kultivator.kota_tema.iblis.kontrak.sasaran.binatang." + Math.floorMod(t.look(), 6), t.tier());
			case SAINGAN -> Component.translatable("kultivator.kota_tema.iblis.kontrak.sasaran.saingan",
					Component.translatable("kultivator.kota_tema.iblis.kontrak.geng." + Math.floorMod(t.look() >>> 4, 8)), KotaNames.person(t.look(), false));
			case BEGAL -> Component.translatable("kultivator.kota_tema.iblis.kontrak.sasaran.begal", KotaNames.person(t.look() ^ 0x5bd1e995, false));
			default -> Component.translatable("kultivator.kota_tema.iblis.kontrak.sasaran.pemburu", 2 + Math.floorMod(t.look() >>> 6, 3));
		};
	}

	private static @Nullable CompoundTag cari(ListTag list, long situs, long hari, int id) {
		for (Tag tag : list) {
			if (tag instanceof CompoundTag c && c.getLongOr("situs", 0L) == situs && c.getLongOr("hari", 0L) == hari && c.getIntOr("id", 0) == id) {
				return c;
			}
		}
		return null;
	}

	private static @Nullable CompoundTag cari(ListTag list, String ref) {
		for (Tag tag : list) {
			if (tag instanceof CompoundTag c && ref.equals(c.getLongOr("situs", 0L) + ":" + c.getLongOr("hari", 0L) + ":" + c.getIntOr("id", 0))) {
				return c;
			}
		}
		return null;
	}

	private static void aksi(ServerPlayer p, int npcId, String op) {
		KultivatorNpc npc = IblisAnchors.npc(p, npcId);
		String[] parts = LembarKunci.urai(op);
		if (npc != null && (TokohIblis.adalah(npc, TokohIblis.HAN_QINGSHUANG) || npc.memory().getBooleanOr("iblis_pemburu", false))
				|| IblisKota.di(p) == null) {
			BuronanIblis.aksi(p, npc, parts);
			return;
		}
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota == null || parts.length < 2) {
			return;
		}
		ServerLevel level = p.level();
		MinecraftServer server = level.getServer();
		long hari = IblisKota.hari(level);
		ListTag taken = aktif(p);
		switch (parts[0]) {
			case TemaLayar.Kontrak.OP_AMBIL -> {
				if (Gerbang.diburu(p, kota.situsId())) {
					IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.kontrak.diburu"), false);
					return;
				}
				int id = (int) IblisLayar.angka(parts, 1, -1L);
				Tawaran t = null;
				for (Tawaran each : papan(kota.situsId(), hari, IblisKota.band(p))) {
					if (each.id() == id) {
						t = each;
					}
				}
				if (t == null || cari(taken, kota.situsId(), hari, id) != null) {
					return;
				}
				if (taken.size() >= AKTIF_MAKS) {
					IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.kontrak.penuh", AKTIF_MAKS), false);
					return;
				}
				if (IblisKota.band(p) < MisiGilda.PITA_MIN[t.pangkat()]) {
					IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.pasar.pita", MisiGilda.PITA_MIN[t.pangkat()]), false);
					return;
				}
				BlockPos at = tanda(level, kota, t);
				CompoundTag c = new CompoundTag();
				c.putLong("situs", kota.situsId());
				c.putLong("hari", hari);
				c.putInt("id", t.id());
				c.putString("jenis", t.jenis());
				c.putInt("pangkat", t.pangkat());
				c.putLong("imbalan", t.imbalan());
				c.putInt("tier", t.tier());
				c.putInt("look", t.look());
				c.putInt("x", at.getX());
				c.putInt("y", at.getY());
				c.putInt("z", at.getZ());
				taken.add(c);
				IblisData.ubah(server);
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.kontrak.ambil", sasaran(t), at.getX(), at.getZ()), true);
			}
			case TemaLayar.Kontrak.OP_SERAH -> {
				CompoundTag c = cari(taken, parts[1]);
				if (c == null) {
					return;
				}
				if (!c.getBooleanOr("selesai", false)) {
					IblisLayar.peringatan(p, Component.translatable("kultivator.kota_tema.iblis.kontrak.belum"));
					return;
				}
				taken.remove(c);
				IblisData.ubah(server);
				long reward = c.getLongOr("imbalan", 1L);
				NilaiBatu.give(p, reward);
				String jenis = c.getStringOr("jenis", BINATANG);
				IblisMeter.tambahInfami(p, BINATANG.equals(jenis) ? INFAMI_BINATANG : INFAMI, "kontrak");
				IblisMeter.tambahGentar(p, kota.situsId(), GENTAR, "kontrak");
				Advancements.grant(p, "tempat/tema_iblis_kontrak");
				CeritaApi.peristiwa(p, IblisCerita.KONTRAK, jenis);
				IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.kontrak.serah", NilaiBatu.text(reward)), true);
			}
			case TemaLayar.Kontrak.OP_BATAL -> {
				CompoundTag c = cari(taken, parts[1]);
				if (c != null) {
					buangMusuh(kunci(p.getUUID(), c));
					taken.remove(c);
					IblisData.ubah(server);
					IblisLayar.pesan(p, Component.translatable("kultivator.kota_tema.iblis.kontrak.batal"), true);
				}
			}
			default -> {
			}
		}
	}

	// ================================================================== the hunt of a contract

	/** Every 40 ticks per online player with contracts: deadlines, spawning foes by the mark, despawning when far. */
	static void tickPemain(ServerPlayer p) {
		ListTag taken = IblisData.pemain(p.level().getServer(), p.getUUID()).getListOrEmpty("kontrak");
		if (taken.isEmpty()) {
			return;
		}
		ServerLevel level = p.level();
		long hari = IblisKota.hari(level);
		List<CompoundTag> expired = new ArrayList<>();
		for (Tag tag : taken) {
			if (!(tag instanceof CompoundTag c)) {
				continue;
			}
			String key = kunci(p.getUUID(), c);
			if (!c.getBooleanOr("selesai", false) && hari - c.getLongOr("hari", hari) >= BATAS_HARI) {
				expired.add(c);
				buangMusuh(key);
				continue;
			}
			if (c.getBooleanOr("selesai", false) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
				continue;
			}
			Vec3 mark = new Vec3(c.getIntOr("x", 0) + 0.5D, c.getIntOr("y", p.getBlockY()), c.getIntOr("z", 0) + 0.5D);
			double d2 = horiz(p.position(), mark);
			List<LivingEntity> foes = MUSUH.get(key);
			if (foes != null) {
				foes.removeIf(e -> e.isRemoved());
				if (d2 > LEPAS_JARAK * LEPAS_JARAK) {
					buangMusuh(key);
				}
				continue;
			}
			if (d2 < MUNCUL_JARAK * MUNCUL_JARAK) {
				List<LivingEntity> made = munculkan(level, p, c, mark);
				if (!made.isEmpty()) {
					MUSUH.put(key, made);
					c.putBoolean("muncul", true);
					IblisData.ubah(level.getServer());
					Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.kontrak.dekat").withStyle(ChatFormatting.DARK_RED));
				}
			}
		}
		if (!expired.isEmpty()) {
			for (CompoundTag c : expired) {
				taken.remove(c);
				p.sendSystemMessage(Component.translatable("kultivator.kota_tema.iblis.kontrak.gagal").withStyle(ChatFormatting.GRAY));
			}
			IblisData.ubah(level.getServer());
		}
	}

	private static double horiz(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	private static List<LivingEntity> munculkan(ServerLevel level, ServerPlayer p, CompoundTag c, Vec3 mark) {
		List<LivingEntity> out = new ArrayList<>();
		BlockPos ground = TempatApi.groundSpot(level, Mth.floor(mark.x), Mth.floor(mark.z), p.getBlockY());
		if (ground == null || !level.isPositionEntityTicking(ground)) {
			return out;
		}
		Vec3 at = Vec3.atBottomCenterOf(ground);
		int tier = Mth.clamp(c.getIntOr("tier", 1), 1, 4);
		int realm = Math.max(0, Cultivators.get(p).realm().ordinal() - 1 + Math.floorMod(c.getIntOr("look", 0), 2));
		String tag = TAG + "." + c.getIntOr("id", 0);
		switch (c.getStringOr("jenis", BINATANG)) {
			case BINATANG -> {
				EntityType<? extends Mob> type = tier >= 3 ? EntityTypes.RAVAGER : EntityTypes.POLAR_BEAR;
				Mob beast = type.create(level, EntitySpawnReason.EVENT);
				if (beast != null) {
					beast.snapTo(at.x, at.y, at.z, p.getYRot() + 180.0F, 0.0F);
					EliteMonsters.makeElite(beast, tier, Element.values()[Math.floorMod(c.getIntOr("look", 0), Element.values().length)], realm);
					beast.setPersistenceRequired();
					beast.addTag(tag);
					TechniquePower.markRealmFoe(beast);
					if (level.addFreshEntity(beast)) {
						beast.setTarget(p);
						out.add(beast);
					}
				}
			}
			case SAINGAN, BEGAL -> {
				boolean demonic = SAINGAN.equals(c.getStringOr("jenis", ""));
				int n = 2 + Math.floorMod(c.getIntOr("look", 0) >>> 3, 2);
				for (int i = 0; i < n; i++) {
					Vec3 spot = at.add((i - n / 2.0D) * 2.0D, 0.0D, (i % 2) * 2.0D);
					Mob rogue = RogueCultivators.spawn(level, spot, i == 0 ? tier : Math.max(1, tier - 1), realm,
							demonic ? RogueCultivators.VARIANT_DEMONIC : RogueCultivators.VARIANT_ROGUE, RogueCultivators.ROLE_QUEST, ground);
					if (rogue != null) {
						rogue.addTag(tag);
						rogue.setTarget(p);
						out.add(rogue);
					}
				}
			}
			default -> out.addAll(PemburuLurus.reguKontrak(level, at, p, tier, 2 + Math.floorMod(c.getIntOr("look", 0) >>> 6, 3), tag));
		}
		if (!out.isEmpty()) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
		}
		return out;
	}

	private static void buangMusuh(String key) {
		List<LivingEntity> foes = MUSUH.remove(key);
		if (foes == null) {
			return;
		}
		for (LivingEntity e : foes) {
			if (e.isRemoved()) {
				continue;
			}
			if (e instanceof KultivatorNpc npc) {
				npc.vanish();
			} else {
				e.discard();
			}
		}
	}

	private static void mati(LivingEntity entity, DamageSource source) {
		if (MUSUH.isEmpty() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		for (Map.Entry<String, List<LivingEntity>> e : new ArrayList<>(MUSUH.entrySet())) {
			List<LivingEntity> foes = e.getValue();
			if (!foes.remove(entity)) {
				continue;
			}
			foes.removeIf(f -> f.isRemoved() || f.isDeadOrDying());
			if (!foes.isEmpty()) {
				return;
			}
			MUSUH.remove(e.getKey());
			UUID who = UUID.fromString(e.getKey().substring(0, e.getKey().indexOf('|')));
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(who);
			if (p == null) {
				return;
			}
			String ref = e.getKey().substring(e.getKey().indexOf('|') + 1);
			CompoundTag c = cari(aktif(p), ref);
			if (c != null) {
				c.putBoolean("selesai", true);
				IblisData.ubah(level.getServer());
				Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.kontrak.tuntas").withStyle(ChatFormatting.DARK_RED),
						Component.translatable("kultivator.kota_tema.iblis.kontrak.tuntas.sub").withStyle(ChatFormatting.GRAY), 5, 50, 15);
			}
			return;
		}
	}

	// ================================================================== the Shadow Oil store (chapter 3)

	/** Anchor {@code kota_tema/gudang_minyak}: the six barrels (broken ones stay broken until Bai She restocks). */
	static void nearbyGudang(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, List<ServerPlayer> within) {
		MinecraftServer server = level.getServer();
		CompoundTag town = IblisData.kota(server, kota.situsId());
		long hari = IblisKota.hari(level);
		if (town.getIntOr("minyak_pecah", 0) != 0 && hari - town.getLongOr("minyak_hari", hari) >= TONG_PULIH_HARI) {
			town.putInt("minyak_pecah", 0);
			IblisData.ubah(server);
		}
		TongMinyakEntity[] bodies = TONG_HIDUP.computeIfAbsent(kota.situsId(), k -> new TongMinyakEntity[TONG.length]);
		int pecah = town.getIntOr("minyak_pecah", 0);
		for (int i = 0; i < TONG.length; i++) {
			if (bodies[i] != null && !bodies[i].isRemoved()) {
				bodies[i].setKeadaan((pecah & (1 << i)) != 0 ? TongMinyakEntity.KEADAAN_PECAH : TongMinyakEntity.KEADAAN_UTUH);
				continue;
			}
			Vec3 at = anchor.position().add(TONG[i][0], 0.0D, TONG[i][1]);
			TongMinyakEntity barrel = TemaMekanisme.muncul(level, TemaEntities.TONG_MINYAK, at, anchor.getYRot() + i * 47.0F, kota, anchor.blockPosition());
			if (barrel == null) {
				return;
			}
			barrel.setVarian(TongMinyakEntity.VARIAN_BIASA);
			barrel.setKeadaan((pecah & (1 << i)) != 0 ? TongMinyakEntity.KEADAAN_PECAH : TongMinyakEntity.KEADAAN_UTUH);
			barrel.setNomor(i);
			bodies[i] = IblisAnchors.simpan(anchor, barrel);
		}
	}

	/** A player strikes barrel {@code nomor}: only on chapter 3 of the chain before the choice, else a warning. */
	static void pukul(ServerPlayer p, TongMinyakEntity barrel) {
		if (!(p.level() instanceof ServerLevel level) || barrel.keadaan() == TongMinyakEntity.KEADAAN_PECAH) {
			return;
		}
		TemaKota.Kota kota = IblisKota.dari(level.getServer(), barrel.situsId());
		if (kota == null) {
			return;
		}
		if (!"minyak".equals(QuestTracker.simpul(p, IblisCerita.QUEST)) || !QuestTracker.aktif(p, IblisCerita.QUEST)) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.minyak.jangan").withStyle(ChatFormatting.GRAY));
			return;
		}
		MinecraftServer server = level.getServer();
		CompoundTag town = IblisData.kota(server, kota.situsId());
		int pecah = town.getIntOr("minyak_pecah", 0) | (1 << Mth.clamp(barrel.nomor(), 0, TONG.length - 1));
		town.putInt("minyak_pecah", pecah);
		town.putLong("minyak_hari", IblisKota.hari(level));
		IblisData.ubah(server);
		barrel.setKeadaan(TongMinyakEntity.KEADAAN_PECAH);
		level.playSound(null, barrel.getX(), barrel.getY(), barrel.getZ(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.playSound(null, barrel.getX(), barrel.getY(), barrel.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 0.5F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, barrel.getX(), barrel.getY() + 0.8D, barrel.getZ(), 14, 0.3D, 0.4D, 0.3D, 0.02D);
		int count = Integer.bitCount(pecah);
		Msg.actionBar(p, Component.translatable("kultivator.kota_tema.iblis.minyak.pecah", count, TONG.length).withStyle(ChatFormatting.DARK_GRAY));
		if (count >= TONG.length) {
			IblisCerita.sabotase(p, kota);
		} else if (count == 2) {
			// the clerk notices the second barrel: two enforcers come to look (they fight only if struck)
			panggilPenjaga(level, barrel.position(), p);
		}
	}

	/** Lines for {@code uji tema iblis kontrak}: the caller's running contracts and today's board of this town. */
	static List<Component> status(ServerPlayer p) {
		List<Component> out = new ArrayList<>();
		TemaKota.Kota kota = IblisKota.di(p);
		if (kota != null) {
			for (Tawaran t : papan(kota.situsId(), IblisKota.hari(p.level()), IblisKota.band(p))) {
				BlockPos at = tanda(p.level(), kota, t);
				out.add(Component.translatable("kultivator.kota_tema.iblis.cmd.kontrak", t.id(), t.jenis(), String.valueOf(JenisMisi.HURUF.charAt(t.pangkat())),
						t.imbalan(), at.getX(), at.getZ()));
			}
		}
		out.add(Component.literal(String.valueOf(aktif(p).size())));
		return out;
	}

	/** The clerk's men turn to look when barrels start breaking (a warning, no hostility yet). */
	private static void panggilPenjaga(ServerLevel level, Vec3 at, ServerPlayer p) {
		for (KultivatorNpc n : level.getEntitiesOfClass(KultivatorNpc.class, new AABB(at, at).inflate(20.0D),
				n -> n.isAlive() && n.role() == NpcRole.PEMBUNUH_BAYARAN)) {
			HidupApi.klip(n, GerakId.MARAH_TUNJUK, 0, 40);
			HidupApi.tatap(n, p);
		}
	}
}
