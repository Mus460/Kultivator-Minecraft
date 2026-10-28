package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.Kultivator;
import dev.kultivator.util.Advancements;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The beast tide, the capital's defence (DESIGN 38.B.6.3). The calendar ({@link KotaBesarKalender}) starts it at dusk;
 * with a player within {@link KotaBesarKota#HIDUP} blocks it runs live, otherwise as data (a rumour of the outcome).
 * <ol>
 * <li><b>Omen</b> (one world hour): the war drum on the Drum Tower, every gate shut early, eight guards up on the wall
 * walk ({@code jaga} points) above the attacked gate, the townsfolk sent home (schedule override through
 * {@code TempatEvents.ACARA}), the line {@code kotabesar.beast_tide}.</li>
 * <li><b>Three waves</b> an hour apart outside one gate (taken in turn: south, east, west): zombies that cannot break
 * doors, skeletons and spiders (never creepers or endermen, DESIGN 0), plus one elite per wave number at the realm of the
 * strongest defender, {@code 8 + 4 x players} per wave, at most {@link #HIDUP_MAKS} alive, spawned 24+ blocks outside the
 * wall on standing ground. Monsters with nobody to fight walk to the shut gate and batter it ({@link GerbangKotaEntity#pukul}).</li>
 * <li><b>Outcome</b>: every wave slain with the gate standing is a victory (Nama Baik +5, +10 for the greatest damage dealer,
 * {@code 30 x (band + 1)} NB each from the city purse as far as it holds); a broken gate or a tide still standing at the
 * end wounds the city (the 38 shops of the capital close for a day, a rumour spreads). No block ever changes: the gate is
 * an entity and the wall is never touched.</li>
 * </ol>
 * Players within 64 blocks of the attacked gate are busy ({@link Sibuk} {@code tempat.pertahanan}, an enemy event for the
 * daily pressure).
 */
public final class GelombangBinatang {

	public static final String TAG = "kultivator.gelombang";
	/** Omen length before the first wave (ticks). */
	public static final long PERTANDA = 1000L;
	/** Gap between waves. */
	public static final long JEDA_GELOMBANG = 1000L;
	/** The tide gives up this long after the first wave. */
	public static final long BATAS = 3400L;
	public static final int GELOMBANG = 3;
	public static final int HIDUP_MAKS = 24;
	public static final int PENJAGA_TEMBOK = 8;
	public static final double SIBUK_JARAK = 64.0D;

	private static final class Serbuan {
		final long town;
		final ResourceKey<Level> dimensi;
		final Kandidat kandidat;
		final long mulai;
		final String gerbang;
		final BlockPos gerbangPos;
		final Vec3 luar;
		int gelombang;
		int kuota;
		int lahir;
		long berikut;
		boolean roboh;
		boolean selesai;
		final Set<UUID> monster = new HashSet<>();
		final List<UUID> penjaga = new ArrayList<>();
		final Map<UUID, Float> jasa = new HashMap<>();

		Serbuan(long town, ResourceKey<Level> dimensi, Kandidat kandidat, long mulai, String gerbang, BlockPos gerbangPos, Vec3 luar) {
			this.town = town;
			this.dimensi = dimensi;
			this.kandidat = kandidat;
			this.mulai = mulai;
			this.gerbang = gerbang;
			this.gerbangPos = gerbangPos;
			this.luar = luar;
		}

		boolean serbu(long now) {
			return now >= mulai + PERTANDA && !selesai;
		}
	}

	private static final Map<Long, Serbuan> AKTIF = new HashMap<>();
	private static boolean dengar;

	static void init() {
		dengarkan();
	}

	/** 42E ({@code SerbuanKota}): the listeners also when the capital module never started. Safe to call twice. */
	public static void initTempat() {
		dengarkan();
	}

	private static void dengarkan() {
		if (dengar) {
			return;
		}
		dengar = true;
		Sibuk.registerEnemy("tempat.pertahanan", GelombangBinatang::terlibat);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity.entityTags().contains(TAG) && source.getEntity() instanceof ServerPlayer p) {
				for (Serbuan s : AKTIF.values()) {
					if (s.monster.contains(entity.getUUID())) {
						s.jasa.merge(p.getUUID(), taken, Float::sum);
					}
				}
				for (SerbuanTempat s : TEMPAT.values()) {
					if (s.monster.contains(entity.getUUID())) {
						s.jasa.merge(p.getUUID(), taken, Float::sum);
					}
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(GelombangBinatang::mati);
		// raiders are persistent: those a stopped server saved (or a finished raid left in an unloaded chunk) never come back
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD
				|| !entity.entityTags().contains(TAG) || masihDilacak(entity.getUUID()));
	}

	/** True while an active raid still tracks {@code id} (a chunk that reloads mid-fight keeps its raiders). */
	private static boolean masihDilacak(UUID id) {
		for (Serbuan s : AKTIF.values()) {
			if (s.monster.contains(id)) {
				return true;
			}
		}
		for (SerbuanTempat s : TEMPAT.values()) {
			if (s.monster.contains(id)) {
				return true;
			}
		}
		return false;
	}

	static void lupakan() {
		AKTIF.clear();
		TEMPAT.clear();
	}

	/** True while a tide holds the gates of {@code town} shut (omen and waves). */
	public static boolean tutup(long town) {
		Serbuan s = AKTIF.get(town);
		return s != null && !s.selesai;
	}

	/** True while the waves are attacking {@code town}. */
	public static boolean serbu(long town) {
		Serbuan s = AKTIF.get(town);
		return s != null && !s.selesai && s.gelombang > 0;
	}

	/** True when {@code player} stands within 64 blocks of a gate under attack. */
	public static boolean terlibat(ServerPlayer player) {
		for (Serbuan s : AKTIF.values()) {
			if (!s.selesai && player.level().dimension() == s.dimensi && player.blockPosition().closerThan(s.gerbangPos, SIBUK_JARAK)) {
				return true;
			}
		}
		for (SerbuanTempat s : TEMPAT.values()) {
			if (!s.selesai && player.level().dimension() == s.dimensi && player.blockPosition().closerThan(s.depan, SIBUK_JARAK)) {
				return true;
			}
		}
		return false;
	}

	/** The gate of a live tide broke (the leaf calls it). */
	static void gerbangRoboh(ServerLevel level, GerbangKotaEntity g) {
		Serbuan s = AKTIF.get(g.town());
		if (s == null || s.roboh) {
			return;
		}
		s.roboh = true;
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(g.blockPosition(), 160.0D)) {
				Msg.title(p, Component.translatable("kultivator.kota_besar.gelombang.roboh").withStyle(ChatFormatting.DARK_RED),
						Component.translatable("kultivator.kota_besar.gelombang.roboh.sub"), 10, 60, 20);
			}
		}
		HidupApi.bahaya(level, g.blockPosition(), 240);
	}

	// ================================================================== start

	/**
	 * Starts the tide at capital {@code k}: live when {@code hidup} (and a gate is loaded), else as data. {@code kali} is the
	 * running count of tides (picks the gate in turn).
	 */
	static void mulai(ServerLevel level, Kandidat k, long town, boolean hidup, int kali) {
		if (AKTIF.containsKey(town)) {
			return;
		}
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		GerbangKotaEntity gate = hidup && kota != null ? pilihGerbang(kota, kali) : null;
		if (gate == null) {
			hasilData(level, k, town);
			return;
		}
		Vec3 out = new Vec3(gate.getX() - k.pusatX(), 0.0D, gate.getZ() - k.pusatZ()).normalize();
		Serbuan s = new Serbuan(town, level.dimension(), k, level.getGameTime(), gate.nama(), gate.blockPosition(), out);
		AKTIF.put(town, s);
		for (GerbangKotaEntity g : kota.gerbang) {
			g.pulihkan();
			g.setBuka(false);
		}
		BlockPos drum = kota.genderang != null ? kota.genderang : KotaBesarKota.pusatDistrik(level, k, "pusat");
		for (int i = 0; i < 16; i++) {
			int beat = i;
			Voice.later(i * 8, () -> level.playSound(null, drum, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 5.0F,
					beat % 2 == 0 ? 0.5F : 0.65F));
		}
		KultivatorNpc guard = JamMalam.penjagaDekat(level, gate.blockPosition(), 32.0D);
		if (guard != null) {
			Voice.say(guard, "kotabesar.beast_tide", null);
			guard.say(Component.translatable("kultivator.kota_besar.gelombang.seru"), 100);
		}
		Component nama = KotaBesarKota.nama(level.getServer(), k);
		for (ServerPlayer p : level.players()) {
			if (KotaBesarKota.jarak(k, p.blockPosition()) <= KotaBesarKota.HIDUP + 64) {
				Msg.title(p, Component.translatable("kultivator.kota_besar.gelombang.judul").withStyle(ChatFormatting.RED),
						Component.translatable("kultivator.kota_besar.gelombang.pertanda", nama,
								Component.translatable("kultivator.kota_besar.gerbang.nama." + gate.nama())), 10, 80, 20);
			}
		}
		naikkanPenjaga(level, s);
		HidupApi.bahaya(level, KotaBesarKota.pusatDistrik(level, k, "gerbang"), (int) ((PERTANDA + BATAS) / 20L));
		TempatEvents.fireAcara(level, town, LembarKunci.Acara.ACARA_PERTAHANAN, true);
		kirim(level, s);
	}

	private static @Nullable GerbangKotaEntity pilihGerbang(KotaBesarAnchors.KotaHidup kota, int kali) {
		List<GerbangKotaEntity> luar = new ArrayList<>();
		for (GerbangKotaEntity g : kota.gerbang) {
			if (!g.isRemoved() && !g.sel() && ("selatan_agung".equals(g.nama()) || "samping".equals(g.nama()))) {
				luar.add(g);
			}
		}
		if (luar.isEmpty()) {
			return null;
		}
		luar.sort((a, b) -> Long.compare(a.blockPosition().asLong(), b.blockPosition().asLong()));
		return luar.get(Math.floorMod(kali, luar.size()));
	}

	private static void naikkanPenjaga(ServerLevel level, Serbuan s) {
		List<Titik> jaga = new ArrayList<>(TempatApi.titik(level, s.town, "jaga"));
		jaga.sort((a, b) -> Double.compare(a.pos().distSqr(s.gerbangPos), b.pos().distSqr(s.gerbangPos)));
		for (Titik t : jaga) {
			if (s.penjaga.size() >= PENJAGA_TEMBOK) {
				break;
			}
			if (t.pos().distSqr(s.gerbangPos) > 64.0D * 64.0D) {
				break;
			}
			KultivatorNpc npc = HidupApi.tubuh(level, s.town, NpcRole.PENJAGA, t.pos(), t.hadap().toYRot());
			if (npc != null) {
				npc.memory().putBoolean("hold", true);
				npc.arm(true);
				npc.setLonely(128.0D, 6000);
				s.penjaga.add(npc.getUUID());
			}
		}
	}

	// ================================================================== tick (every 20 ticks)

	static void tick(MinecraftServer server) {
		if (AKTIF.isEmpty()) {
			return;
		}
		for (Iterator<Serbuan> it = AKTIF.values().iterator(); it.hasNext(); ) {
			Serbuan s = it.next();
			ServerLevel level = server.getLevel(s.dimensi);
			if (level == null) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (s.selesai) {
				it.remove();
				continue;
			}
			if (now < s.mulai + PERTANDA) {
				continue;
			}
			if (s.gelombang == 0 || s.gelombang < GELOMBANG && now >= s.berikut) {
				gelombangBaru(level, s, now);
			}
			bersihkanMati(level, s);
			spawnSisa(level, s);
			gerakkan(level, s);
			if (s.gelombang >= GELOMBANG && s.lahir >= s.kuota && s.monster.isEmpty()) {
				akhir(level, s, !s.roboh);
				it.remove();
				continue;
			}
			if (now >= s.mulai + PERTANDA + BATAS) {
				akhir(level, s, false);
				it.remove();
				continue;
			}
			if (now % 40L < 20L) {
				kirim(level, s);
			}
		}
	}

	private static List<ServerPlayer> pembela(ServerLevel level, Serbuan s) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && KotaBesarKota.jarak(s.kandidat, p.blockPosition()) <= KotaBesarKota.HIDUP + 64) {
				out.add(p);
			}
		}
		return out;
	}

	private static void gelombangBaru(ServerLevel level, Serbuan s, long now) {
		s.gelombang++;
		s.berikut = now + JEDA_GELOMBANG;
		int players = Math.max(1, pembela(level, s).size());
		s.kuota = 8 + 4 * players;
		s.lahir = 0;
		level.playSound(null, s.gerbangPos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6.0F, 0.8F + 0.1F * s.gelombang);
		for (ServerPlayer p : pembela(level, s)) {
			Msg.title(p, Component.translatable("kultivator.kota_besar.gelombang.ke", s.gelombang, GELOMBANG).withStyle(ChatFormatting.RED),
					Component.translatable("kultivator.kota_besar.gelombang.ke.sub", s.kuota), 5, 40, 10);
		}
	}

	private static void spawnSisa(ServerLevel level, Serbuan s) {
		int room = HIDUP_MAKS - s.monster.size();
		RandomSource r = level.getRandom();
		int realm = 0;
		for (ServerPlayer p : pembela(level, s)) {
			realm = Math.max(realm, Cultivators.get(p).realm().ordinal());
		}
		int batch = Math.min(4, room);
		for (int i = 0; i < batch && s.lahir < s.kuota; i++) {
			double side = (r.nextDouble() - 0.5D) * 40.0D;
			double depth = 26.0D + r.nextDouble() * 14.0D;
			Vec3 lat = new Vec3(-s.luar.z, 0.0D, s.luar.x);
			Vec3 at = Vec3.atCenterOf(s.gerbangPos).add(s.luar.scale(depth)).add(lat.scale(side));
			BlockPos spot = TempatApi.groundSpot(level, Mth.floor(at.x), Mth.floor(at.z), s.gerbangPos.getY());
			if (spot == null) {
				continue;
			}
			boolean elite = s.lahir < s.gelombang;
			EntityType<? extends Mob> type = pilihJenis(r);
			Mob mob = type.create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			mob.snapTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, r.nextFloat() * 360.0F, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
			if (mob instanceof Zombie zombie) {
				zombie.setCanBreakDoors(false);
			}
			mob.setPersistenceRequired();
			mob.addTag(TAG);
			if (elite) {
				EliteMonsters.makeElite(mob, s.gelombang, null, realm);
				TechniquePower.markRealmFoe(mob);
			}
			if (level.addFreshEntity(mob)) {
				s.monster.add(mob.getUUID());
				s.lahir++; // counted only once it stands in the world (an unloaded spot spawns nothing)
			}
		}
	}

	private static EntityType<? extends Mob> pilihJenis(RandomSource r) {
		int roll = r.nextInt(100);
		if (roll < 45) {
			return EntityTypes.ZOMBIE;
		}
		return roll < 75 ? EntityTypes.SKELETON : EntityTypes.SPIDER;
	}

	private static void bersihkanMati(ServerLevel level, Serbuan s) {
		// a raider in an unloaded chunk is not dead: with no defender around the count stands still
		boolean jaga = !pembela(level, s).isEmpty();
		s.monster.removeIf(id -> {
			Entity e = level.getEntity(id);
			return e == null ? jaga : !e.isAlive();
		});
	}

	/** Monsters without a fight walk to the gate; at the shut gate they batter it. */
	private static void gerakkan(ServerLevel level, Serbuan s) {
		GerbangKotaEntity gate = null;
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(s.town);
		if (kota != null) {
			for (GerbangKotaEntity g : kota.gerbang) {
				if (s.gerbang.equals(g.nama()) && g.blockPosition().closerThan(s.gerbangPos, 2.0D)) {
					gate = g;
				}
			}
		}
		for (UUID id : s.monster) {
			if (!(level.getEntity(id) instanceof Mob mob)) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == null || !target.isAlive()) {
				LivingEntity pick = sasaran(level, mob);
				if (pick != null) {
					mob.setTarget(pick);
				} else if (gate != null && !gate.buka()) {
					if (mob.distanceTo(gate) > 3.5F) {
						mob.getNavigation().moveTo(gate.getX(), gate.getY(), gate.getZ(), 1.0D);
					} else {
						gate.pukul(level, mob, (float) Math.max(1.0D, mob.getAttributeValue(Attributes.ATTACK_DAMAGE)));
					}
				} else if (s.roboh) {
					BlockPos inside = KotaBesarKota.pusatDistrik(level, s.kandidat, "gerbang");
					mob.getNavigation().moveTo(inside.getX(), inside.getY(), inside.getZ(), 1.0D);
				}
			}
		}
	}

	private static @Nullable LivingEntity sasaran(ServerLevel level, Mob mob) {
		LivingEntity best = null;
		double bestSq = 20.0D * 20.0D;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(20.0D),
				o -> o.isAlive() && (o instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator()
						|| o instanceof KultivatorNpc n && n.role().kelompok == NpcRole.Kelompok.PENJAGA))) {
			double d = e.distanceToSqr(mob);
			if (d < bestSq && mob.hasLineOfSight(e)) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	private static void mati(LivingEntity victim, DamageSource source) {
		if (!victim.entityTags().contains(TAG)) {
			return;
		}
		for (Serbuan s : AKTIF.values()) {
			s.monster.remove(victim.getUUID());
		}
		for (SerbuanTempat s : TEMPAT.values()) {
			s.monster.remove(victim.getUUID());
		}
	}

	// ================================================================== end

	private static void akhir(ServerLevel level, Serbuan s, boolean menang) {
		s.selesai = true;
		MinecraftServer server = level.getServer();
		for (UUID id : s.monster) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive()) {
				e.discard();
			}
		}
		s.monster.clear();
		for (UUID id : s.penjaga) {
			Entity e = level.getEntity(id);
			if (e instanceof KultivatorNpc npc && npc.isAlive()) {
				npc.vanish();
			}
		}
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(s.town);
		if (kota != null) {
			for (GerbangKotaEntity g : kota.gerbang) {
				g.pulihkan();
			}
		}
		Component nama = KotaBesarKota.nama(server, s.kandidat);
		List<ServerPlayer> pembela = pembela(level, s);
		if (menang) {
			UUID top = null;
			float best = 0.0F;
			for (Map.Entry<UUID, Float> e : s.jasa.entrySet()) {
				if (e.getValue() > best) {
					best = e.getValue();
					top = e.getKey();
				}
			}
			for (ServerPlayer p : pembela) {
				boolean utama = p.getUUID().equals(top);
				int nb = utama ? 10 : 5;
				int value = KotaData.get(server).addReputation(s.town, p.getUUID(), nb);
				long want = 30L * (Barang.pita(p) + 1);
				long paid = KasKota.ambil(server, s.town, want);
				if (paid > 0L) {
					EkonomiApi.beri(p, paid, "kotabesar.pertahanan");
				}
				Msg.title(p, Component.translatable("kultivator.kota_besar.gelombang.menang").withStyle(ChatFormatting.GOLD),
						Component.translatable("kultivator.kota_besar.gelombang.menang.sub", nama), 10, 70, 20);
				p.sendSystemMessage(Component.translatable(utama ? "kultivator.kota_besar.gelombang.hadiah_utama" : "kultivator.kota_besar.gelombang.hadiah",
						nb, value, Barang.nb(paid)).withStyle(ChatFormatting.GOLD));
				Advancements.grant(p, "tempat/pertahanan");
				KotaBesarData.get(server).beriGelar(p.getUUID(), "pembela_kota");
			}
			KultivatorNpc guard = JamMalam.penjagaDekat(level, s.gerbangPos, 48.0D);
			if (guard != null) {
				Voice.say(guard, "kotabesar.defense_won", null);
			}
			CeritaApi.kabar(level, s.town, "kota_besar.pertahanan_menang", nama);
		} else {
			KotaBesarData data = KotaBesarData.get(server);
			data.putLong(s.town, "tutup_sampai", Kota.day(server) + 1L);
			for (ServerPlayer p : pembela) {
				Msg.title(p, Component.translatable("kultivator.kota_besar.gelombang.kalah").withStyle(ChatFormatting.DARK_RED),
						Component.translatable("kultivator.kota_besar.gelombang.kalah.sub", nama), 10, 70, 20);
			}
			CeritaApi.kabar(level, s.town, "kota_besar.kota_terluka", nama);
		}
		KotaBesarData.get(server).putString(s.town, "pertahanan_hasil", menang ? "menang" : "kalah");
		TempatEvents.fireAcara(level, s.town, LembarKunci.Acara.ACARA_PERTAHANAN, false);
		CompoundTag off = new CompoundTag();
		off.putBoolean("aktif", false);
		KotaBesarPayload.sekitar(level, Vec3.atCenterOf(s.gerbangPos), "pertahanan", off);
		Kultivator.LOGGER.debug("Beast tide at {} ended: {}", s.town, menang ? "won" : "lost");
	}

	/** No player near: the city's guards meet the tide alone (70 % hold); the rumour tells. */
	private static void hasilData(ServerLevel level, Kandidat k, long town) {
		MinecraftServer server = level.getServer();
		long day = Kota.day(server);
		boolean menang = Math.floorMod((town ^ day) * 0x9E3779B97F4A7C15L >>> 33, 100L) < 70L;
		Component nama = KotaBesarKota.nama(server, k);
		if (!menang) {
			KotaBesarData.get(server).putLong(town, "tutup_sampai", day + 1L);
		}
		KotaBesarData.get(server).putString(town, "pertahanan_hasil", menang ? "menang" : "kalah");
		CeritaApi.kabar(level, town, menang ? "kota_besar.pertahanan_menang" : "kota_besar.kota_terluka", nama);
	}

	/** End a running tide at once (command); false when none. */
	static boolean hentikan(ServerLevel level, long town, boolean menang) {
		Serbuan s = AKTIF.remove(town);
		if (s == null) {
			return false;
		}
		akhir(level, s, menang);
		return true;
	}

	private static void kirim(ServerLevel level, Serbuan s) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("aktif", true);
		tag.putDouble("x", s.gerbangPos.getX() + 0.5D);
		tag.putDouble("z", s.gerbangPos.getZ() + 0.5D);
		tag.putInt("y", s.gerbangPos.getY());
		tag.putInt("gelombang", s.gelombang);
		tag.putInt("total", GELOMBANG);
		tag.putInt("sisa", s.monster.size() + Math.max(0, s.kuota - s.lahir));
		tag.putBoolean("roboh", s.roboh);
		tag.putFloat("gerbang", gerbangSehat(s));
		KotaBesarPayload.sekitar(level, Vec3.atCenterOf(s.gerbangPos), "pertahanan", tag);
	}

	private static float gerbangSehat(Serbuan s) {
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(s.town);
		if (kota != null) {
			for (GerbangKotaEntity g : kota.gerbang) {
				if (s.gerbang.equals(g.nama()) && g.blockPosition().closerThan(s.gerbangPos, 2.0D)) {
					return 1.0F - g.retak();
				}
			}
		}
		return s.roboh ? 0.0F : 1.0F;
	}

	/** True while the 38 shops of {@code town} are shut after a lost defence. */
	public static boolean tokoTutup(MinecraftServer server, long town) {
		return Kota.day(server) <= KotaBesarData.get(server).getLong(town, "tutup_sampai", Long.MIN_VALUE);
	}

	// ================================================================== 42E: a raid on a protected town (DESIGN 42.8.2)

	/** Raid kinds of {@link #mulai(ServerLevel, long, int, String, Consumer)}: beasts, bandits, demonic cultivators. */
	public static final String JENIS_BINATANG = "binatang";
	public static final String JENIS_BEGAL = "begal";
	public static final String JENIS_IBLIS = "iblis";
	/** Omen of a town raid (the raiders gather outside), then the time the town must hold. */
	public static final long TEMPAT_PERTANDA = 600L;
	public static final long TEMPAT_BATAS = 3600L;

	/** A live raid on a town without the capital's gate entities: the waves come at one edge of the place's box. */
	private static final class SerbuanTempat {
		final long situs;
		final ResourceKey<Level> dimensi;
		final BoundingBox kotak;
		final BlockPos pusat;
		final BlockPos depan;
		final Vec3 luar;
		final int kekuatan;
		final String jenis;
		final long mulai;
		final @Nullable Consumer<Boolean> selesaiFn;
		int gelombang;
		int kuota;
		int lahir;
		long berikut;
		boolean selesai;
		final Set<UUID> monster = new HashSet<>();
		final Map<UUID, Float> jasa = new HashMap<>();

		SerbuanTempat(long situs, ResourceKey<Level> dimensi, BoundingBox kotak, BlockPos pusat, BlockPos depan, Vec3 luar, int kekuatan,
				String jenis, long mulai, @Nullable Consumer<Boolean> selesaiFn) {
			this.situs = situs;
			this.dimensi = dimensi;
			this.kotak = kotak;
			this.pusat = pusat;
			this.depan = depan;
			this.luar = luar;
			this.kekuatan = kekuatan;
			this.jenis = jenis;
			this.mulai = mulai;
			this.selesaiFn = selesaiFn;
		}
	}

	private static final Map<Long, SerbuanTempat> TEMPAT = new HashMap<>();

	/** {@link #mulai(ServerLevel, long, int, String, Consumer)} without a result listener. */
	public static boolean mulai(ServerLevel level, long situs, int kekuatan, String jenis) {
		return mulai(level, situs, kekuatan, jenis, null);
	}

	/**
	 * Starts a live raid on place {@code situs} (42E {@code SerbuanKota}): after a short omen, three waves of {@code jenis}
	 * ({@link #JENIS_BINATANG} spiders and the dead, {@link #JENIS_BEGAL} rogue cultivators, {@link #JENIS_IBLIS} demonic
	 * ones) gather 22-34 blocks outside one edge of the place and march on its centre; {@code kekuatan} sets the wave size
	 * ({@code clamp(kekuatan / 25, 6, 18)} plus 2 per defender) and the elites. The town holds when every wave is slain
	 * before {@link #TEMPAT_BATAS}; {@code selesai} hears {@code true} then, {@code false} when time runs out. Nothing ever
	 * breaks a block (zombies cannot break doors, no creepers). False (nothing started) when a raid already runs there, the
	 * place is unknown or no player is within 96 blocks of it (the caller then decides the raid as data).
	 */
	public static boolean mulai(ServerLevel level, long situs, int kekuatan, String jenis, @Nullable Consumer<Boolean> selesai) {
		initTempat();
		if (TEMPAT.containsKey(situs)) {
			return false;
		}
		BoundingBox box = kotakTempat(level, situs);
		if (box == null) {
			return false;
		}
		BlockPos tengah = box.getCenter();
		boolean dekat = false;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && box.inflatedBy(96, 64, 96).isInside(p.blockPosition())) {
				dekat = true;
				break;
			}
		}
		if (!dekat) {
			return false;
		}
		int sisi = (int) Math.floorMod(situs ^ Kota.day(level.getServer()) * 31L, 4L);
		int x = switch (sisi) {
			case 0 -> box.minX();
			case 1 -> box.maxX();
			default -> tengah.getX();
		};
		int z = switch (sisi) {
			case 2 -> box.minZ();
			case 3 -> box.maxZ();
			default -> tengah.getZ();
		};
		BlockPos depan = TempatApi.groundSpot(level, x, z, tengah.getY());
		if (depan == null) {
			depan = new BlockPos(x, tengah.getY(), z);
		}
		Vec3 luar = new Vec3(depan.getX() - tengah.getX(), 0.0D, depan.getZ() - tengah.getZ());
		luar = luar.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : luar.normalize();
		BlockPos pusat = TempatApi.groundSpot(level, tengah.getX(), tengah.getZ(), tengah.getY());
		SerbuanTempat s = new SerbuanTempat(situs, level.dimension(), box, pusat == null ? tengah : pusat, depan, luar, Math.max(1, kekuatan),
				jenis == null ? JENIS_BINATANG : jenis, level.getGameTime(), selesai);
		TEMPAT.put(situs, s);
		for (int i = 0; i < 12; i++) {
			int beat = i;
			BlockPos at = depan;
			Voice.later(i * 10, () -> level.playSound(null, at, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 4.0F,
					beat % 2 == 0 ? 0.5F : 0.6F));
		}
		HidupApi.bahaya(level, depan, (int) ((TEMPAT_PERTANDA + TEMPAT_BATAS) / 20L));
		return true;
	}

	/** True while a live 42E raid runs at {@code situs} (omen and waves). */
	public static boolean serbuTempat(long situs) {
		SerbuanTempat s = TEMPAT.get(situs);
		return s != null && !s.selesai;
	}

	/** The edge the raiders of {@code situs} come at, or null. */
	public static @Nullable BlockPos depanTempat(long situs) {
		SerbuanTempat s = TEMPAT.get(situs);
		return s == null ? null : s.depan;
	}

	/** Raiders of {@code situs} still alive or to come. */
	public static int sisaTempat(long situs) {
		SerbuanTempat s = TEMPAT.get(situs);
		return s == null ? 0 : s.monster.size() + Math.max(0, s.kuota - s.lahir) + Math.max(0, GELOMBANG - s.gelombang) * s.kuota;
	}

	/** Ends a live town raid at once (command); false when none. */
	public static boolean hentikanTempat(ServerLevel level, long situs, boolean menang) {
		SerbuanTempat s = TEMPAT.remove(situs);
		if (s == null) {
			return false;
		}
		akhirTempat(level, s, menang);
		return true;
	}

	/** The box of a place: the union of its loaded pieces, else 40 blocks around its site; null when unknown. */
	public static @Nullable BoundingBox kotakTempat(ServerLevel level, long situs) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (dev.kultivator.dunia.tempat.PotonganInfo p : TempatApi.potongan(level, situs)) {
			BoundingBox b = p.kotak();
			minX = Math.min(minX, b.minX());
			minY = Math.min(minY, b.minY());
			minZ = Math.min(minZ, b.minZ());
			maxX = Math.max(maxX, b.maxX());
			maxY = Math.max(maxY, b.maxY());
			maxZ = Math.max(maxZ, b.maxZ());
		}
		if (minX <= maxX) {
			return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
		}
		dev.kultivator.dunia.DuniaSites.Site site = dev.kultivator.dunia.DuniaSites.byId(level.getServer(), situs);
		if (site == null || site.dim() != level.dimension()) {
			return null;
		}
		BlockPos c = site.pos();
		return new BoundingBox(c.getX() - 40, c.getY() - 16, c.getZ() - 40, c.getX() + 40, c.getY() + 24, c.getZ() + 40);
	}

	/** Every second from 42E ({@code SerbuanKota.tick}); independent of the capital's switch. */
	public static void tickTempat(MinecraftServer server) {
		if (TEMPAT.isEmpty()) {
			return;
		}
		for (Iterator<SerbuanTempat> it = TEMPAT.values().iterator(); it.hasNext(); ) {
			SerbuanTempat s = it.next();
			ServerLevel level = server.getLevel(s.dimensi);
			if (level == null || s.selesai) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (now < s.mulai + TEMPAT_PERTANDA) {
				continue;
			}
			if (s.gelombang == 0 || s.gelombang < GELOMBANG && now >= s.berikut && s.lahir >= s.kuota) {
				gelombangTempat(level, s, now);
			}
			boolean jaga = !pembelaTempat(level, s).isEmpty();
			s.monster.removeIf(id -> {
				Entity e = level.getEntity(id);
				return e == null ? jaga : !e.isAlive();
			});
			spawnTempat(level, s);
			gerakkanTempat(level, s);
			if (s.gelombang >= GELOMBANG && s.lahir >= s.kuota && s.monster.isEmpty()) {
				akhirTempat(level, s, true);
				it.remove();
			} else if (now >= s.mulai + TEMPAT_PERTANDA + TEMPAT_BATAS) {
				akhirTempat(level, s, false);
				it.remove();
			}
		}
	}

	private static List<ServerPlayer> pembelaTempat(ServerLevel level, SerbuanTempat s) {
		List<ServerPlayer> out = new ArrayList<>();
		BoundingBox area = s.kotak.inflatedBy(64, 48, 64);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && area.isInside(p.blockPosition())) {
				out.add(p);
			}
		}
		return out;
	}

	private static void gelombangTempat(ServerLevel level, SerbuanTempat s, long now) {
		s.gelombang++;
		s.berikut = now + JEDA_GELOMBANG;
		int players = Math.max(1, pembelaTempat(level, s).size());
		s.kuota = Mth.clamp(s.kekuatan / 25, 6, 18) + 2 * (players - 1);
		s.lahir = 0;
		level.playSound(null, s.depan, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 6.0F, 0.8F + 0.1F * s.gelombang);
		for (ServerPlayer p : pembelaTempat(level, s)) {
			Msg.title(p, Component.translatable("kultivator.kota_besar.gelombang.ke", s.gelombang, GELOMBANG).withStyle(ChatFormatting.RED),
					Component.translatable("kultivator.kota_besar.gelombang.ke.sub", s.kuota), 5, 40, 10);
		}
	}

	private static void spawnTempat(ServerLevel level, SerbuanTempat s) {
		int room = HIDUP_MAKS - s.monster.size();
		RandomSource r = level.getRandom();
		int realm = 0;
		for (ServerPlayer p : pembelaTempat(level, s)) {
			realm = Math.max(realm, Cultivators.get(p).realm().ordinal());
		}
		int batch = Math.min(4, room);
		Vec3 lat = new Vec3(-s.luar.z, 0.0D, s.luar.x);
		for (int i = 0; i < batch && s.lahir < s.kuota; i++) {
			Vec3 at = Vec3.atCenterOf(s.depan).add(s.luar.scale(22.0D + r.nextDouble() * 12.0D)).add(lat.scale((r.nextDouble() - 0.5D) * 40.0D));
			BlockPos spot = TempatApi.groundSpot(level, Mth.floor(at.x), Mth.floor(at.z), s.depan.getY());
			if (spot == null) {
				continue;
			}
			boolean elite = s.lahir < s.gelombang;
			Mob mob;
			if (JENIS_BINATANG.equals(s.jenis)) {
				int roll = r.nextInt(100);
				EntityType<? extends Mob> type = roll < 50 ? EntityTypes.SPIDER : roll < 70 ? EntityTypes.CAVE_SPIDER : EntityTypes.ZOMBIE;
				mob = type.create(level, EntitySpawnReason.EVENT);
				if (mob == null) {
					continue;
				}
				mob.snapTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, r.nextFloat() * 360.0F, 0.0F);
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
				if (mob instanceof Zombie zombie) {
					zombie.setCanBreakDoors(false);
				}
				mob.setPersistenceRequired();
				mob.addTag(TAG);
				if (elite) {
					EliteMonsters.makeElite(mob, s.gelombang, null, realm);
					TechniquePower.markRealmFoe(mob);
				}
				if (!level.addFreshEntity(mob)) {
					continue;
				}
			} else {
				int variant = JENIS_IBLIS.equals(s.jenis) ? dev.kultivator.musuh.RogueCultivators.VARIANT_DEMONIC
						: dev.kultivator.musuh.RogueCultivators.VARIANT_ROGUE;
				int tier = elite ? Math.min(3, 1 + s.gelombang / 2 + (s.kekuatan >= 300 ? 1 : 0)) : 1;
				mob = dev.kultivator.musuh.RogueCultivators.spawn(level, Vec3.atBottomCenterOf(spot), tier, realm, variant,
						dev.kultivator.musuh.RogueCultivators.ROLE_RAID, s.pusat);
				if (mob == null) {
					continue;
				}
				mob.addTag(TAG);
			}
			s.monster.add(mob.getUUID());
			s.lahir++;
		}
	}

	/** Raiders without a fight march on the centre of the place (they never touch a block). */
	private static void gerakkanTempat(ServerLevel level, SerbuanTempat s) {
		for (UUID id : s.monster) {
			if (!(level.getEntity(id) instanceof Mob mob)) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target == null || !target.isAlive()) {
				LivingEntity pick = sasaran(level, mob);
				if (pick != null) {
					mob.setTarget(pick);
				} else if (mob.getNavigation().isDone()) {
					BlockPos goal = mob.blockPosition().closerThan(s.depan, 6.0D) || s.kotak.isInside(mob.blockPosition()) ? s.pusat : s.depan;
					mob.getNavigation().moveTo(goal.getX() + 0.5D, goal.getY(), goal.getZ() + 0.5D, 1.0D);
				}
			}
		}
	}

	private static void akhirTempat(ServerLevel level, SerbuanTempat s, boolean menang) {
		s.selesai = true;
		for (UUID id : s.monster) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive()) {
				e.discard();
			}
		}
		s.monster.clear();
		Kultivator.LOGGER.debug("Town raid at {} ended: {}", s.situs, menang ? "held" : "lost");
		if (s.selesaiFn != null) {
			try {
				s.selesaiFn.accept(menang);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Town raid result listener failed at {}", s.situs, exception);
			}
		}
	}

	/** The player who dealt the most damage to the raiders of the running raid at {@code situs}, or null. */
	public static @Nullable UUID jasaTerbesarTempat(long situs) {
		SerbuanTempat s = TEMPAT.get(situs);
		if (s == null) {
			return null;
		}
		UUID top = null;
		float best = 0.0F;
		for (Map.Entry<UUID, Float> e : s.jasa.entrySet()) {
			if (e.getValue() > best) {
				best = e.getValue();
				top = e.getKey();
			}
		}
		return top;
	}

	private GelombangBinatang() {
	}
}
