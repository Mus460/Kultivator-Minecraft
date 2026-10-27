package dev.kultivator.dunia.tempat.tema.oasis;

import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.QuestTracker;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaItems;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.TemaNet;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Badai Pasir, the local sandstorm of one Kota Oasis (DESIGN 38.T.4.6). Never the world weather: no
 * {@code setWeatherParameters}, no time or tick-rate change, no block. It is 38TK-O state plus client drawing
 * ({@code TemaNet.BADAI}, drawn by 38TV-F under {@code EffectQuality}/{@code EpicBudget}).
 * <ul>
 * <li>Schedule: every {@code temaOasis.badaiHariMin..badaiHariMaks} world days per town (hashed per town, daytime),
 * calendar event {@code badai_pasir} 03000-06600 (09:00-12:36): a 60 s omen (sand wall on the horizon, whistling wind,
 * the caravan master's shout), then 2400 ticks of storm in a 600-block radius. A quest ({@code oasis_badai}) or the test
 * command {@code uji tema acara oasis badai_pasir mulai} starts one at once.</li>
 * <li>NPCs shelter (schedule override {@code tema.badai_pasir}), camels kneel ({@link UntaKafilahEntity}), caravans on the
 * road stop ({@link KafilahUnta}), the Gerbang Giok closes (through {@link #gerbangTutup}).</li>
 * <li>A player outside the walls who sees the sky and has no Restu Mata Air gets "Tertiup Pasir" (Slowness I renewed every
 * 10 s) and 0.5 damage every 20 s without anything on the head (Kerudung Pasir or any helmet); inside the walls nothing.</li>
 * <li>Afterwards 2-4 Temuan Pasir lie on the dunes 60-300 blocks from town ({@link TemuanPasirEntity}, re-made from the
 * town data whenever a player comes near, gone after two days).</li>
 * <li>Sibuk: the storm does not hold a player inside the walls; outside, during the storm proper, it counts as the live
 * enemy event of the shared pressure so no ambush starts in the storm ({@code tema_oasis_badai}).</li>
 * </ul>
 */
public final class BadaiPasir {

	public static final String ACARA = "badai_pasir";
	/** Storm radius around the town centre (38.T.4.6). */
	public static final int JARI = 600;
	/** Omen length before the storm proper (60 s). */
	public static final int PERTANDA = 1200;
	/** Storm proper length (2400 ticks). */
	public static final int PANJANG = 2400;
	/** Calendar window (time of day) of the omen + storm. */
	public static final int DARI = 3000;
	public static final int SAMPAI = DARI + PERTANDA + PANJANG;
	/** Slowness renewal and duration. */
	private static final int LAMBAT_SETIAP = 200;
	private static final int LAMBAT_TICK = 240;
	/** Head-uncovered damage cadence and amount. */
	private static final int LUKA_SETIAP = 400;
	private static final float LUKA = 0.5F;
	/** Temuan Pasir: how many, how far, how long. */
	private static final int TEMUAN_MIN = 2;
	private static final int TEMUAN_MAKS = 4;
	private static final int TEMUAN_DEKAT = 60;
	private static final int TEMUAN_JAUH = 300;
	private static final int TEMUAN_HARI = 2;
	private static final double TEMUAN_MUNCUL = 48.0D;

	/** One running storm. */
	static final class Badai {
		final long situs;
		final long mulai;
		final long badaiMulai;
		final long akhir;
		final BlockPos pusat;
		final Set<UUID> dikirim = new HashSet<>();
		boolean seruan;

		Badai(long situs, long mulai, BlockPos pusat) {
			this.situs = situs;
			this.mulai = mulai;
			this.badaiMulai = mulai + PERTANDA;
			this.akhir = badaiMulai + PANJANG;
			this.pusat = pusat;
		}

		boolean pertanda(long now) {
			return now < badaiMulai;
		}

		CompoundTag paket(boolean start) {
			CompoundTag tag = new CompoundTag();
			tag.putBoolean("mulai", start);
			tag.putInt("x", pusat.getX());
			tag.putInt("z", pusat.getZ());
			tag.putInt("r", JARI);
			tag.putLong("tick", badaiMulai);
			tag.putInt("panjang", PANJANG);
			tag.putFloat("kuat", 1.0F);
			return tag;
		}
	}

	private static final Map<Long, Badai> AKTIF = new HashMap<>();
	private static final Map<UUID, Long> LUKA_TERAKHIR = new HashMap<>();
	/** Temuan bodies on the dunes (data id -> entity). */
	private static final Map<String, TemuanPasirEntity> TEMUAN = new HashMap<>();

	static void init() {
		TemaKalender.daftar(OasisKota.TEMA, ACARA, DARI, SAMPAI, BadaiPasir::hariBadai, new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				BadaiPasir.mulai(level, situsId, dipaksa);
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				BadaiPasir.akhir(level, situsId);
			}
		});
		Sibuk.registerEnemy("tema_oasis_badai", BadaiPasir::diLuarSaatBadai);
	}

	static void reset() {
		AKTIF.clear();
		LUKA_TERAKHIR.clear();
		TEMUAN.clear();
		DAUN.clear();
	}

	// ================================================================== schedule

	/** Calendar picker: today is this town's storm day (switches on, hashed 4-8 day gaps). */
	static boolean hariBadai(ServerLevel level, long hari, long situs, BlockPos pusat) {
		if (!TemaConfig.oasis().badaiPasir || !TemaConfig.inti().bahaya) {
			return false;
		}
		MinecraftServer server = level.getServer();
		CompoundTag data = OasisKota.kota(server, situs);
		long next = data.getLongOr("badai_hari", Long.MIN_VALUE);
		if (next == Long.MIN_VALUE || next < hari) {
			next = hari + jeda(situs, hari);
			data.putLong("badai_hari", next);
			OasisKota.ubah(server);
		}
		return next == hari;
	}

	private static int jeda(long situs, long hari) {
		int min = TemaConfig.oasis().badaiHariMin;
		int max = Math.max(min, TemaConfig.oasis().badaiHariMaks);
		return min + (int) Math.floorMod(OasisKota.hash(situs, hari, 380415L), (long) (max - min + 1));
	}

	// ================================================================== start / end

	/** Starts the omen of a storm in town {@code situs} (also from a quest or a test); false when none could start. */
	public static boolean mulai(ServerLevel level, long situs, boolean dipaksa) {
		OasisKota.Hidup kota = OasisKota.cari(level.getServer(), situs);
		if (kota == null || AKTIF.containsKey(kota.situs)) {
			return false;
		}
		if (!dipaksa && (!TemaConfig.oasis().badaiPasir || !TemaConfig.inti().bahaya)) {
			return false;
		}
		long now = level.getGameTime();
		Badai badai = new Badai(kota.situs, now, kota.pusat);
		AKTIF.put(kota.situs, badai);
		TariPutar.batal(level, kota.situs);
		HidupApi.timpaJadwal(kota.situs, "tema." + ACARA, badai.akhir);
		MinecraftServer server = level.getServer();
		CompoundTag data = OasisKota.kota(server, kota.situs);
		data.putLong("badai_terakhir", OasisKota.hari(level));
		OasisKota.ubah(server);
		kirimSemua(level, badai, true);
		level.playSound(null, kota.pusat, SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, 2.5F, 0.45F);
		return true;
	}

	/** Ends the storm of {@code situs} (time over, forced, or the calendar's end); drops the Temuan Pasir. */
	public static void akhir(ServerLevel level, long situs) {
		OasisKota.Hidup kota = OasisKota.cari(level.getServer(), situs);
		long key = kota != null ? kota.situs : situs;
		Badai badai = AKTIF.remove(key);
		if (badai == null) {
			return;
		}
		HidupApi.timpaJadwal(key, "", 0L);
		kirimSemua(level, badai, false);
		MinecraftServer server = level.getServer();
		CompoundTag data = OasisKota.kota(server, key);
		long hari = OasisKota.hari(level);
		data.putLong("badai_hari", hari + jeda(key, hari));
		sebarTemuan(level, badai, data, hari);
		OasisKota.ubah(server);
		for (ServerPlayer p : pemainDekat(level, badai.pusat, JARI)) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.oasis.badai.reda").withStyle(ChatFormatting.GOLD));
			QuestTracker.peristiwa(p, "oasis_badai", "reda");
		}
	}

	private static void kirimSemua(ServerLevel level, Badai badai, boolean start) {
		CompoundTag paket = badai.paket(start);
		for (ServerPlayer p : pemainDekat(level, badai.pusat, JARI + 64)) {
			TemaNet.kirim(p, TemaNet.BADAI, badai.situs, paket);
			if (start) {
				badai.dikirim.add(p.getUUID());
				Msg.chat(p, Component.translatable("kultivator.kota_tema.oasis.badai.pertanda").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	// ================================================================== ticking (every 20 ticks, from Oasis)

	static void tick(MinecraftServer server) {
		if (AKTIF.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<Long, Badai>> it = AKTIF.entrySet().iterator();
		while (it.hasNext()) {
			Badai badai = it.next().getValue();
			OasisKota.Hidup kota = OasisKota.cari(server, badai.situs);
			ServerLevel level = kota == null ? null : server.getLevel(kota.dimensi);
			if (level == null) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (now >= badai.akhir) {
				// the calendar's own end may come later (or never when forced): end it here
				akhir(level, badai.situs);
				it = AKTIF.entrySet().iterator();
				continue;
			}
			if (!badai.pertanda(now) && !badai.seruan) {
				badai.seruan = true;
				seru(level, badai);
			}
			for (ServerPlayer p : pemainDekat(level, badai.pusat, JARI + 64)) {
				if (badai.dikirim.add(p.getUUID())) {
					TemaNet.kirim(p, TemaNet.BADAI, badai.situs, badai.paket(true));
				}
				if (!badai.pertanda(now) && p.position().distanceTo(Vec3.atCenterOf(badai.pusat)) <= JARI) {
					tiup(level, kota, p, now);
				}
			}
		}
	}

	/** "Badai pasir! Suruh unta berlutut, masuk ke dalam!" from the nearest caravan man or desert guard. */
	private static void seru(ServerLevel level, Badai badai) {
		AABB box = new AABB(badai.pusat).inflate(96.0D, 32.0D, 96.0D);
		KultivatorNpc best = null;
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, box,
				n -> n.role() == NpcRole.KAFILAH_BASHI || n.role() == NpcRole.PENGAWAL_GURUN || n.role() == NpcRole.PENUNTUN_UNTA)) {
			best = npc;
			if (npc.role() == NpcRole.KAFILAH_BASHI) {
				break;
			}
		}
		if (best != null) {
			Voice.say(best, "tema.oasis.sandstorm", null);
			best.say(Component.translatable("kultivator.kota_tema.oasis.badai.seru"), 80);
		}
	}

	private static void tiup(ServerLevel level, OasisKota.Hidup kota, ServerPlayer p, long now) {
		if (p.isCreative() || p.isSpectator() || kota.dalamTembok(p.blockPosition())) {
			return;
		}
		if (!level.canSeeSky(BlockPos.containing(p.getEyePosition())) || KuilAir.restuAktif(p)) {
			return;
		}
		if (now % LAMBAT_SETIAP < 20) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LAMBAT_TICK, 0, true, true, true));
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.oasis.badai.tertiup").withStyle(ChatFormatting.YELLOW));
		}
		if (p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
			long last = LUKA_TERAKHIR.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2);
			if (now - last >= LUKA_SETIAP) {
				LUKA_TERAKHIR.put(p.getUUID(), now);
				p.hurtServer(level, level.damageSources().generic(), LUKA);
				Msg.actionBar(p, Component.translatable("kultivator.kota_tema.oasis.badai.tanpa_kerudung").withStyle(ChatFormatting.RED));
			}
		}
	}

	// ================================================================== queries

	/** True while {@code pos} is inside the radius of a storm proper (not the omen). */
	public static boolean aktifDi(ServerLevel level, BlockPos pos) {
		long now = level.getGameTime();
		for (Badai badai : AKTIF.values()) {
			if (!badai.pertanda(now) && badai.pusat.distSqr(pos) <= (double) JARI * JARI) {
				OasisKota.Hidup kota = OasisKota.cari(level.getServer(), badai.situs);
				if (kota != null && kota.dimensi.equals(level.dimension())) {
					return true;
				}
			}
		}
		return false;
	}

	/** True while town {@code situs} has a storm or its omen. */
	public static boolean aktif(long situs) {
		return AKTIF.containsKey(situs);
	}

	/**
	 * The Gerbang Giok stays shut while true: an omen or a storm of the town with place or town id {@code id}. Read by the
	 * gate curfew loop ({@code JamMalam.harusBuka}, one hook line requested from 38KS/F2).
	 */
	public static boolean gerbangTutup(long id) {
		if (AKTIF.containsKey(id)) {
			return true;
		}
		for (Badai badai : AKTIF.values()) {
			OasisKota.Hidup kota = OasisKota.cari(null, badai.situs);
			if (kota != null && kota.town == id) {
				return true;
			}
		}
		return false;
	}

	/** Leaf bodies of the Gerbang Giok per gate anchor. */
	private static final Map<UUID, List<PintuPasirEntity>> DAUN = new HashMap<>();

	/**
	 * Anchor {@code tema_oasis/gerbang} (the middle of the arch, data {@code lebar} 7, facing out): while an omen or a storm
	 * of the town runs, four leaves ({@code pintu_pasir} variant {@code GERBANG}) close the arch; afterwards they go.
	 */
	static void gerbangDekat(ServerLevel level, DuniaAnchorEntity anchor) {
		long situs = OasisAnchors.situsOf(level, anchor);
		List<PintuPasirEntity> daun = DAUN.computeIfAbsent(anchor.getUUID(), k -> new java.util.ArrayList<>());
		daun.removeIf(PintuPasirEntity::isRemoved);
		if (!gerbangTutup(situs)) {
			if (!daun.isEmpty()) {
				for (PintuPasirEntity d : daun) {
					TemaMekanisme.lepas(d);
				}
				daun.clear();
				level.playSound(null, anchor.blockPosition(), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 1.4F, 0.6F);
			}
			return;
		}
		if (!daun.isEmpty()) {
			return;
		}
		int lebar = Math.max(3, OasisAnchors.angka(anchor, "lebar", 7));
		double rad = Math.toRadians(anchor.getYRot());
		// the arch runs across the anchor's facing: step along (cos, sin) of the yaw
		double ax = Math.cos(rad);
		double az = Math.sin(rad);
		TemaKota.Kota born = TemaKota.dari(level.getServer(), situs);
		int n = (lebar + 1) / 2;
		for (int i = 0; i < n; i++) {
			double off = -lebar / 2.0D + 1.0D + i * 2.0D;
			Vec3 at = new Vec3(anchor.getX() + ax * off, anchor.getY(), anchor.getZ() + az * off);
			PintuPasirEntity d = TemaMekanisme.muncul(level, TemaEntities.PINTU_PASIR, at, anchor.getYRot(), born, anchor.blockPosition());
			if (d == null) {
				break;
			}
			d.jadiGerbang();
			daun.add(d);
		}
		level.playSound(null, anchor.blockPosition(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 1.6F, 0.5F);
	}

	static void gerbangLepas(DuniaAnchorEntity anchor) {
		List<PintuPasirEntity> daun = DAUN.remove(anchor.getUUID());
		if (daun != null) {
			for (PintuPasirEntity d : daun) {
				TemaMekanisme.lepas(d);
			}
		}
	}

	/** Sibuk: the player stands outside the walls inside a storm proper. */
	static boolean diLuarSaatBadai(ServerPlayer p) {
		if (AKTIF.isEmpty() || !(p.level() instanceof ServerLevel level) || !aktifDi(level, p.blockPosition())) {
			return false;
		}
		return !OasisKota.dalamTembok(level, p.blockPosition());
	}

	/** Game ticks until the storm proper of {@code situs} ends (0 when none). */
	public static long sisa(ServerLevel level, long situs) {
		Badai badai = AKTIF.get(situs);
		return badai == null ? 0L : Math.max(0L, badai.akhir - level.getGameTime());
	}

	// ================================================================== Temuan Pasir

	private static void sebarTemuan(ServerLevel level, Badai badai, CompoundTag data, long hari) {
		ListTag list = data.getListOrEmpty("temuan");
		// old finds blow away with the next storm
		list.clear();
		long seed = OasisKota.hash(badai.situs, hari, 380451L);
		int n = TEMUAN_MIN + (int) Math.floorMod(seed, (long) (TEMUAN_MAKS - TEMUAN_MIN + 1));
		for (int i = 0; i < n; i++) {
			double angle = OasisKota.acak(seed, i, 11L) * Math.PI * 2.0D;
			double dist = TEMUAN_DEKAT + OasisKota.acak(seed, i, 12L) * (TEMUAN_JAUH - TEMUAN_DEKAT);
			CompoundTag t = new CompoundTag();
			t.putString("id", badai.situs + ":" + hari + ":" + i);
			t.putInt("x", badai.pusat.getX() + (int) Math.round(Math.cos(angle) * dist));
			t.putInt("z", badai.pusat.getZ() + (int) Math.round(Math.sin(angle) * dist));
			// a map plate one time in four, the rest coins and mural shards
			double roll = OasisKota.acak(seed, i, 13L);
			t.putInt("varian", roll < 0.25D ? TemuanPasirEntity.VARIAN_PETA : roll < 0.6D ? TemuanPasirEntity.VARIAN_MURAL : TemuanPasirEntity.VARIAN_KOIN);
			t.putLong("sampai", hari + TEMUAN_HARI);
			list.add(t);
		}
		data.put("temuan", list);
	}

	/** Every 20 ticks: makes the bodies of the finds near players, drops expired ones. */
	static void tickTemuan(MinecraftServer server) {
		for (OasisKota.Hidup kota : OasisKota.semua()) {
			ServerLevel level = server.getLevel(kota.dimensi);
			if (level == null) {
				continue;
			}
			CompoundTag data = OasisKota.kota(server, kota.situs);
			ListTag list = data.getListOrEmpty("temuan");
			if (list.isEmpty()) {
				continue;
			}
			long hari = OasisKota.hari(level);
			for (int i = list.size() - 1; i >= 0; i--) {
				CompoundTag t = list.getCompoundOrEmpty(i);
				String id = t.getStringOr("id", "");
				if (t.getLongOr("sampai", 0L) < hari) {
					list.remove(i);
					TemuanPasirEntity old = TEMUAN.remove(id);
					if (old != null) {
						old.discard();
					}
					OasisKota.ubah(server);
					continue;
				}
				TemuanPasirEntity body = TEMUAN.get(id);
				if (body != null && !body.isRemoved()) {
					continue;
				}
				int x = t.getIntOr("x", 0);
				int z = t.getIntOr("z", 0);
				if (!level.hasChunk(x >> 4, z >> 4) || !adaPemain(level, x, z, TEMUAN_MUNCUL)) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				TemaKota.Kota born = TemaKota.dari(server, kota.situs);
				TemuanPasirEntity e = TemaMekanisme.muncul(level, TemaEntities.TEMUAN_PASIR, new Vec3(x + 0.5D, y - 0.15D, z + 0.5D),
						(float) (OasisKota.acak(x, z, 7L) * 360.0D), born, new BlockPos(x, y, z));
				if (e != null) {
					e.setVarian(t.getIntOr("varian", TemuanPasirEntity.VARIAN_KOIN));
					e.setDataId(id);
					TEMUAN.put(id, e);
				}
			}
		}
	}

	/** A player picks up a find: gives its item, forgets it. */
	static void ambil(ServerPlayer player, TemuanPasirEntity e) {
		if (e.keadaan() != TemuanPasirEntity.KEADAAN_TERTANAM || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		CompoundTag data = OasisKota.kota(server, e.situsId());
		ListTag list = data.getListOrEmpty("temuan");
		for (int i = list.size() - 1; i >= 0; i--) {
			if (e.dataId().equals(list.getCompoundOrEmpty(i).getStringOr("id", ""))) {
				list.remove(i);
			}
		}
		OasisKota.ubah(server);
		TEMUAN.remove(e.dataId());
		ItemStack hadiah = switch (e.varian()) {
			case TemuanPasirEntity.VARIAN_PETA -> new ItemStack(TemaItems.LEMPENG_PETA);
			case TemuanPasirEntity.VARIAN_MURAL -> new ItemStack(TemaItems.PECAHAN_MURAL, 1 + player.getRandom().nextInt(2));
			default -> new ItemStack(TemaItems.KOIN_KUNO, 2 + player.getRandom().nextInt(4));
		};
		Component nama = hadiah.getHoverName();
		OasisKota.beri(player, hadiah);
		e.setKeadaan(TemuanPasirEntity.KEADAAN_DIAMBIL);
		e.hilangSetelah(20);
		level.playSound(null, e.blockPosition(), SoundEvents.SUSPICIOUS_SAND_BREAK, SoundSource.BLOCKS, 1.0F, 1.1F);
		Msg.actionBar(player, Component.translatable("kultivator.kota_tema.oasis.temuan.dapat", nama).withStyle(ChatFormatting.GOLD));
		QuestTracker.peristiwa(player, "oasis_temuan", Integer.toString(e.varian()));
	}

	// ================================================================== helpers

	static List<ServerPlayer> pemainDekat(ServerLevel level, BlockPos at, double r) {
		double r2 = r * r;
		List<ServerPlayer> out = new java.util.ArrayList<>();
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - (at.getX() + 0.5D);
			double dz = p.getZ() - (at.getZ() + 0.5D);
			if (dx * dx + dz * dz <= r2) {
				out.add(p);
			}
		}
		return out;
	}

	private static boolean adaPemain(ServerLevel level, int x, int z, double r) {
		double r2 = r * r;
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - (x + 0.5D);
			double dz = p.getZ() - (z + 0.5D);
			if (dx * dx + dz * dz <= r2 && !p.isSpectator()) {
				return true;
			}
		}
		return false;
	}

	private BadaiPasir() {
	}
}
