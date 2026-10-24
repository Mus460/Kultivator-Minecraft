package dev.kultivator.dunia.tempat.tema.iblis;

import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.KotaNames;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NilaiBatu;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.SenjataKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaConfig;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKota;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.dunia.tempat.tema.TemaSibuk;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.harta.BeastCores;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DeathGuard;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arena Darah (DESIGN 38.T.7.5): the Tangga Darah ladder (ranks 1-9 per player and town), beast bouts and the Tebusan
 * Darah (a captive freed by a bout instead of money). The pit is the 31 x 31 sand of the arena district (canvas 33..63),
 * entered through the four cage gates ({@link PintuArenaEntity}, titik {@code arena_pintu} 0 north, 1 south, 2 west,
 * 3 east).
 * <ul>
 * <li>Fee {@code 8 x (band + 1)} NB into the city purse; at most 5 bouts a day; disciples never follow (Sibuk
 * {@code tema.arena} holds the player).</li>
 * <li>Foe tier {@code clamp(band - 1 + rank / 3, 1, 8)} (Tebusan Darah +1); a named gladiator ("Han si Taring Patah"),
 * realm = tier, combat tier {@code 1 + tier / 2}, a realm foe. At ranks 3 and 6 the ladder bout is an elite beast from
 * the cages.</li>
 * <li>The player never dies: at 5 % HP the bout stops, the overseers drag the player into the gladiators' room with
 * Weakness I for 3 minutes, the fee is lost. A fallen gladiator is carried out.</li>
 * <li>Win: {@code 2.2 x fee} (scaled by {@code temaIblis.arenaEv / 0.90}) from fee + purse, never minted (purse short:
 * 2 x fee), Gentar +5, rank +1; winning rank 9 gives the title "Juara Tangga Darah" and once the gada "Palu Darah
 * Senja" (a city weapon of grade roh_tinggi).</li>
 * </ul>
 */
public final class ArenaDarah {

	public static final String DISTRIK = "arena_darah";
	public static final int LAGA_HARIAN = 5;
	public static final int PERINGKAT_MAKS = 9;
	public static final long BATAS_TICK = 3L * 60L * 20L;
	public static final float HP_HENTI = 0.05F;
	public static final int LEMAH_TICK = 3 * 60 * 20;

	public static final int JENIS_TANGGA = 0;
	public static final int JENIS_BINATANG = 1;
	public static final int JENIS_TEBUSAN = 2;
	public static final int JENIS_TURNAMEN = 3;

	/** Canvas spots of the four cage gates (centre of the 3-wide openings) and their yaw (0 = facing south). */
	static final double[][] PINTU = {{48.5D, 32.5D, 0.0D}, {48.5D, 64.5D, 0.0D}, {32.5D, 48.5D, 90.0D}, {64.5D, 48.5D, 90.0D}};
	/** Canvas spots inside the pit: the player at the south gate, the foe at the north gate. */
	static final double[] PEMAIN = {48.5D, 58.5D};
	static final double[] LAWAN = {48.5D, 38.5D};
	/** The gladiators' room (where a beaten player is dragged). */
	static final double[] RUANG = {48.5D, 74.5D};

	/** One bout. */
	static final class Laga {
		final UUID player;
		final long situs;
		final int jenis;
		final int peringkat;
		final long biaya;
		final int tawanan;
		final long mulai;
		final int tier;
		@Nullable Mob lawan;
		boolean selesai;

		Laga(UUID player, long situs, int jenis, int peringkat, long biaya, int tawanan, long mulai, int tier) {
			this.player = player;
			this.situs = situs;
			this.jenis = jenis;
			this.peringkat = peringkat;
			this.biaya = biaya;
			this.tawanan = tawanan;
			this.mulai = mulai;
			this.tier = tier;
		}
	}

	private static final Map<UUID, Laga> LAGA = new HashMap<>();
	private static final Map<Long, List<PintuArenaEntity>> PINTU_HIDUP = new HashMap<>();
	private static final Map<Long, Vec3> KANDANG = new HashMap<>();

	private ArenaDarah() {
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ArenaDarah::bolehLuka);
		ServerLivingEntityEvents.AFTER_DEATH.register(ArenaDarah::mati);
		DeathGuard.register(DeathGuard.Stage.DUEL, (player, source, amount) -> {
			Laga laga = LAGA.get(player.getUUID());
			if (laga == null || laga.selesai) {
				return false;
			}
			player.setHealth(Math.max(1.0F, player.getMaxHealth() * HP_HENTI));
			kalah(player, laga, "hp");
			return true;
		});
		Sibuk.register("tema.arena", player -> LAGA.containsKey(player.getUUID()));
	}

	static void clear() {
		LAGA.clear();
		PINTU_HIDUP.clear();
		KANDANG.clear();
	}

	// ================================================================== anchors

	/** Anchor {@code kota_tema/arena}: the four cage gates while a player is near the pit. */
	static void nearby(ServerLevel level, DuniaAnchorEntity anchor, TemaKota.Kota kota, List<ServerPlayer> within) {
		List<PintuArenaEntity> doors = PINTU_HIDUP.computeIfAbsent(kota.situsId(), k -> new ArrayList<>());
		doors.removeIf(d -> d.isRemoved());
		if (doors.size() >= PINTU.length) {
			return;
		}
		Vec3 centre = IblisKota.lokal(kota, DISTRIK, 48.5D, 1.0D, 48.5D);
		if (centre == null || !TemaMekanisme.pemainDekat(level, centre, 40.0D)) {
			return;
		}
		for (int i = doors.size(); i < PINTU.length; i++) {
			Vec3 at = IblisKota.lokal(kota, DISTRIK, PINTU[i][0], 1.0D, PINTU[i][1]);
			if (at == null) {
				continue;
			}
			PintuArenaEntity door = TemaMekanisme.muncul(level, TemaEntities.PINTU_ARENA, at, IblisKota.yaw(kota, (float) PINTU[i][2]), kota,
					anchor.blockPosition());
			if (door == null) {
				return;
			}
			door.setKeadaan(PintuArenaEntity.KEADAAN_TERTUTUP);
			door.setVarian(PintuArenaEntity.VARIAN_BIASA);
			doors.add(IblisAnchors.simpan(anchor, door));
		}
	}

	static void ingatKandang(TemaKota.Kota kota, Vec3 pos) {
		KANDANG.put(kota.situsId(), pos);
	}

	static void lupakanKandang(long situsId) {
		KANDANG.remove(situsId);
		PINTU_HIDUP.remove(situsId);
	}

	// ================================================================== the ladder numbers

	public static int peringkat(ServerPlayer p, long situs) {
		return Mth.clamp(IblisData.pemainKota(p.level().getServer(), situs, p.getUUID()).getIntOr("peringkat", 1), 1, PERINGKAT_MAKS);
	}

	public static long biaya(ServerPlayer p) {
		return 8L * (IblisKota.band(p) + 1L);
	}

	/** Prize of a won bout for this fee (never more than fee + purse can give). */
	public static long hadiah(long biaya) {
		return Math.round(biaya * 2.2D * TemaConfig.iblis().arenaEv / 0.90D);
	}

	public static int tierLawan(ServerPlayer p, int peringkat, boolean tebusan) {
		return Mth.clamp(IblisKota.band(p) - 1 + peringkat / 3 + (tebusan ? 1 : 0), 1, 8);
	}

	/** Bouts left today. */
	public static int sisaHariIni(ServerPlayer p, long situs) {
		return Math.max(0, LAGA_HARIAN - IblisData.hariIni(p.level().getServer(), situs, p.getUUID(), "laga", IblisKota.hari(p.level())));
	}

	/** Why the player may not enter the pit now, or null. */
	public static @Nullable Component alasan(ServerPlayer p, TemaKota.Kota kota) {
		return alasan(p, kota, JENIS_TANGGA);
	}

	/** {@link #alasan(ServerPlayer, TemaKota.Kota)} for a bout kind (the Blood Tournament does not count the 5 a day). */
	public static @Nullable Component alasan(ServerPlayer p, TemaKota.Kota kota, int jenis) {
		if (LAGA.containsKey(p.getUUID())) {
			return Component.translatable("kultivator.kota_tema.iblis.arena.sudah");
		}
		if (IblisKota.band(p) < 2) {
			return Component.translatable("kultivator.kota_tema.iblis.gerbang.lemah");
		}
		if (jenis != JENIS_TURNAMEN && sisaHariIni(p, kota.situsId()) <= 0) {
			return Component.translatable("kultivator.kota_tema.iblis.arena.habis", LAGA_HARIAN);
		}
		if (Gerbang.diburu(p, kota.situsId())) {
			return Component.translatable("kultivator.kota_tema.iblis.arena.diburu");
		}
		String sibuk = Sibuk.whyExcept(p, "tema.arena");
		if (sibuk != null) {
			return Sibuk.text(sibuk);
		}
		return null;
	}

	public static boolean sedang(ServerPlayer p) {
		return LAGA.containsKey(p.getUUID());
	}

	/** True while any player fights a bout in this town's pit. */
	public static boolean adaLaga(long situs) {
		for (Laga laga : LAGA.values()) {
			if (laga.situs == situs) {
				return true;
			}
		}
		return false;
	}

	// ================================================================== a bout

	/** Starts a bout; returns the refusal or null. {@code tawanan} is the captive of a Tebusan Darah (-1 none). */
	public static @Nullable Component mulai(ServerPlayer p, TemaKota.Kota kota, int jenis, int tawanan) {
		Component no = alasan(p, kota, jenis);
		if (no != null) {
			return no;
		}
		MinecraftServer server = p.level().getServer();
		int rank = peringkat(p, kota.situsId());
		boolean beast = jenis == JENIS_BINATANG || jenis == JENIS_TANGGA && (rank == 3 || rank == 6);
		int realJenis = beast ? JENIS_BINATANG : jenis;
		long fee = jenis == JENIS_TEBUSAN || jenis == JENIS_TURNAMEN ? 0L : biaya(p);
		if (fee > 0L && !NilaiBatu.pay(p, fee)) {
			return Component.translatable("kultivator.kota_tema.iblis.uang_kurang", NilaiBatu.text(fee));
		}
		IblisData.tambahKas(server, kota.situsId(), fee);
		ServerLevel level = p.level();
		Vec3 stand = IblisKota.lokal(kota, DISTRIK, PEMAIN[0], 1.0D, PEMAIN[1]);
		Vec3 foeAt = IblisKota.lokal(kota, DISTRIK, LAWAN[0], 1.0D, LAWAN[1]);
		if (stand == null || foeAt == null) {
			NilaiBatu.give(p, fee);
			return Component.translatable("kultivator.kota_tema.iblis.arena.tutup");
		}
		int tier = jenis == JENIS_TURNAMEN ? PestaDarah.tierBabak(p, kota.situsId()) : tierLawan(p, rank, jenis == JENIS_TEBUSAN);
		Laga laga = new Laga(p.getUUID(), kota.situsId(), realJenis, rank, fee, tawanan, level.getGameTime(), tier);
		Mob foe = beast ? binatang(level, foeAt, kota, tier) : gladiator(level, foeAt, kota, tier, rank, p);
		if (foe == null) {
			NilaiBatu.give(p, fee);
			IblisData.ambilKas(server, kota.situsId(), fee);
			return Component.translatable("kultivator.kota_tema.iblis.arena.tutup");
		}
		laga.lawan = foe;
		LAGA.put(p.getUUID(), laga);
		if (jenis != JENIS_TURNAMEN) {
			IblisData.hitungHariIni(server, kota.situsId(), p.getUUID(), "laga", IblisKota.hari(level));
		}
		TemaSibuk.tahan(p, TemaSibuk.ARENA, (int) BATAS_TICK + 200);
		p.teleportTo(level, stand.x, stand.y, stand.z, java.util.Set.of(), IblisKota.yaw(kota, 180.0F), 0.0F, true);
		bukaPintu(kota.situsId(), 0);
		level.playSound(null, stand.x, stand.y, stand.z, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0F, 0.8F);
		KultivatorNpc tu = tuGang(level, stand);
		Voice.say(tu != null ? tu : p, "tema.iblis.arena_open", null);
		Component judul = Component.translatable("kultivator.kota_tema.iblis.arena.mulai." + (beast ? "binatang" : jenis == JENIS_TEBUSAN ? "tebusan" : jenis == JENIS_TURNAMEN ? "turnamen" : "tangga"),
				jenis == JENIS_TURNAMEN ? PestaDarah.babak(p.getUUID()) : rank);
		Msg.title(p, judul.copy().withStyle(ChatFormatting.DARK_RED), foe.getDisplayName().copy().withStyle(ChatFormatting.RED), 5, 50, 10);
		penonton(level, stand, true);
		return null;
	}

	private static @Nullable Mob gladiator(ServerLevel level, Vec3 at, TemaKota.Kota kota, int tier, int rank, ServerPlayer p) {
		int look = (int) (kota.situsId() * 7919L) ^ (rank * 0x9E3779B9) ^ p.getUUID().hashCode() ^ (int) (IblisKota.hari(level) * 131L);
		KultivatorNpc npc = HidupApi.tubuh(level, kota.situsId(), NpcRole.GLADIATOR_IBLIS, net.minecraft.core.BlockPos.containing(at), 0.0F, look);
		if (npc == null) {
			return null;
		}
		Component nama = namaGladiator(look);
		npc.setup(NpcRole.GLADIATOR_IBLIS, 1 + tier / 2, look, tier, Math.floorMod(look, 4), Dao.values()[Math.floorMod(look >>> 9, Dao.COUNT)],
				Math.floorMod(look >>> 3, 6), 0, nama);
		npc.memory().putBoolean("hold", false);
		npc.memory().putBoolean("iblis_arena", true);
		npc.setDiamDiTitik(false);
		npc.arm(true);
		TechniquePower.markRealmFoe(npc);
		npc.markHostile(p);
		return npc;
	}

	/** "Han «si Taring Patah»": a town name and one of 16 nicknames. */
	public static Component namaGladiator(int look) {
		return Component.translatable("kultivator.kota_tema.iblis.gladiator.nama", KotaNames.person(look, false),
				Component.translatable("kultivator.kota_tema.iblis.julukan." + Math.floorMod(look >>> 13, 16)));
	}

	private static @Nullable Mob binatang(ServerLevel level, Vec3 at, TemaKota.Kota kota, int tier) {
		Mob beast = EntityTypes.RAVAGER.create(level, EntitySpawnReason.EVENT);
		if (beast == null) {
			return null;
		}
		beast.snapTo(at.x, at.y, at.z, IblisKota.yaw(kota, 0.0F), 0.0F);
		beast.setPersistenceRequired();
		int eliteTier = Mth.clamp(1 + tier / 2, 1, 4);
		EliteMonsters.makeElite(beast, eliteTier, Element.values()[Math.floorMod((int) kota.situsId() + tier, Element.values().length)], tier);
		beast.setCustomName(Component.translatable("kultivator.kota_tema.iblis.arena.binatang", tier));
		beast.addTag("kultivator.iblis_arena");
		TechniquePower.markRealmFoe(beast);
		if (!level.addFreshEntity(beast)) {
			return null;
		}
		return beast;
	}

	/** Every 5 ticks: timeouts, a player who left the pit, the gates' animation. */
	static void tick(MinecraftServer server) {
		if (!LAGA.isEmpty()) {
			Iterator<Map.Entry<UUID, Laga>> it = LAGA.entrySet().iterator();
			List<Object[]> akhir = new ArrayList<>();
			while (it.hasNext()) {
				Laga laga = it.next().getValue();
				ServerPlayer p = server.getPlayerList().getPlayer(laga.player);
				TemaKota.Kota kota = p == null ? null : IblisKota.dari(server, laga.situs);
				if (p == null || kota == null || !p.isAlive()) {
					buangLawan(laga);
					it.remove();
					continue;
				}
				Vec3 centre = IblisKota.lokal(kota, DISTRIK, 48.5D, 1.0D, 48.5D);
				long now = p.level().getGameTime();
				if (laga.lawan == null || laga.lawan.isRemoved()) {
					akhir.add(new Object[] {p, laga, laga.lawan != null && laga.lawan.isDeadOrDying() ? "menang" : "lari"});
				} else if (centre == null || p.level() != laga.lawan.level() || p.position().distanceToSqr(centre) > 24.0D * 24.0D) {
					akhir.add(new Object[] {p, laga, "keluar"});
				} else if (now - laga.mulai > BATAS_TICK) {
					akhir.add(new Object[] {p, laga, "waktu"});
				} else if (laga.lawan instanceof KultivatorNpc npc && npc.getTarget() == null) {
					npc.markHostile(p);
				} else if (laga.lawan.getTarget() == null) {
					laga.lawan.setTarget(p);
				}
			}
			for (Object[] o : akhir) {
				ServerPlayer p = (ServerPlayer) o[0];
				Laga laga = (Laga) o[1];
				if ("menang".equals(o[2])) {
					menang(p, laga);
				} else {
					kalah(p, laga, (String) o[2]);
				}
			}
		}
		long now = server.overworld().getGameTime();
		for (List<PintuArenaEntity> doors : PINTU_HIDUP.values()) {
			for (PintuArenaEntity d : doors) {
				long since = IblisKota.sejak(d, now);
				switch (d.keadaan()) {
					case PintuArenaEntity.KEADAAN_NAIK -> {
						d.setNilai(Math.min(1.0F, since / 32.0F));
						if (since >= 32L) {
							d.setKeadaan(PintuArenaEntity.KEADAAN_TERBUKA);
						}
					}
					case PintuArenaEntity.KEADAAN_TERBUKA -> {
						if (since >= 80L) {
							d.setKeadaan(PintuArenaEntity.KEADAAN_JATUH);
							if (d.level() instanceof ServerLevel level) {
								level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.2F, 0.5F);
							}
						}
					}
					case PintuArenaEntity.KEADAAN_JATUH -> {
						d.setNilai(Math.max(0.0F, 1.0F - since / 8.0F));
						if (since >= 8L) {
							d.setKeadaan(PintuArenaEntity.KEADAAN_TERTUTUP);
						}
					}
					default -> {
					}
				}
			}
		}
	}

	/** Raises cage gate {@code nomor} (0 north: the foe's gate) of town {@code situs}. */
	static void bukaPintu(long situs, int nomor) {
		List<PintuArenaEntity> doors = PINTU_HIDUP.get(situs);
		if (doors == null || nomor >= doors.size()) {
			return;
		}
		PintuArenaEntity d = doors.get(nomor);
		if (!d.isRemoved() && d.keadaan() == PintuArenaEntity.KEADAAN_TERTUTUP) {
			d.setKeadaan(PintuArenaEntity.KEADAAN_NAIK);
			if (d.level() instanceof ServerLevel level) {
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.2F, 0.6F);
			}
		}
	}

	private static boolean bolehLuka(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof ServerPlayer p)) {
			return true;
		}
		Laga laga = LAGA.get(p.getUUID());
		if (laga == null || laga.selesai) {
			return true;
		}
		if (p.getHealth() - amount <= p.getMaxHealth() * HP_HENTI) {
			kalah(p, laga, "hp");
			return false;
		}
		return true;
	}

	private static void mati(LivingEntity entity, DamageSource source) {
		for (Laga laga : LAGA.values()) {
			if (laga.lawan == entity && !laga.selesai) {
				MinecraftServer server = entity.level().getServer();
				ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(laga.player);
				if (p != null) {
					menang(p, laga);
				}
				return;
			}
		}
	}

	private static void menang(ServerPlayer p, Laga laga) {
		if (laga.selesai) {
			return;
		}
		laga.selesai = true;
		LAGA.remove(p.getUUID());
		TemaSibuk.lepas(p, TemaSibuk.ARENA);
		MinecraftServer server = p.level().getServer();
		TemaKota.Kota kota = IblisKota.dari(server, laga.situs);
		long prize = 0L;
		if (laga.biaya > 0L) {
			long want = hadiah(laga.biaya);
			prize = IblisData.ambilKas(server, laga.situs, want);
			if (prize < want) {
				IblisData.tambahKas(server, laga.situs, prize);
				prize = IblisData.ambilKas(server, laga.situs, 2L * laga.biaya);
			}
			NilaiBatu.give(p, prize);
		}
		IblisMeter.tambahGentar(p, laga.situs, 5, "arena");
		ServerLevel level = p.level();
		KultivatorNpc tu = tuGang(level, p.position());
		Voice.say(tu != null ? tu : p, "tema.iblis.arena_win", null);
		penonton(level, p.position(), false);
		Msg.title(p, Component.translatable("kultivator.kota_tema.iblis.arena.menang").withStyle(ChatFormatting.GOLD),
				prize > 0L ? Component.translatable("kultivator.kota_tema.iblis.arena.hadiah", NilaiBatu.text(prize)).withStyle(ChatFormatting.YELLOW)
						: Component.empty(), 5, 60, 15);
		CeritaApi.peristiwa(p, IblisCerita.MENANG_ARENA, laga.jenis == JENIS_TEBUSAN ? "tebusan" : laga.jenis == JENIS_BINATANG ? "binatang" : "tangga");
		if (laga.jenis == JENIS_BINATANG) {
			ItemStack core = BeastCores.create(Element.values()[Math.floorMod((int) laga.situs + laga.tier, Element.values().length)],
					Mth.clamp(1 + laga.tier / 3, 1, 3));
			Barang.beri(p, core);
		}
		if (laga.jenis == JENIS_TEBUSAN) {
			if (kota != null) {
				KandangTawanan.bebaskan(p, kota, laga.tawanan, true);
			}
		} else if (laga.jenis != JENIS_TURNAMEN) {
			naikPeringkat(p, laga);
		}
		PestaDarah.lagaSelesai(p, laga.situs, true);
		buangLawan(laga);
	}

	private static void naikPeringkat(ServerPlayer p, Laga laga) {
		MinecraftServer server = p.level().getServer();
		var pk = IblisData.pemainKota(server, laga.situs, p.getUUID());
		if (laga.peringkat >= PERINGKAT_MAKS) {
			if (IblisData.beriGelar(p, IblisData.GELAR_JUARA)) {
				Msg.broadcast(server, Component.translatable("kultivator.kota_tema.iblis.arena.juara", p.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
				Advancements.grant(p, "tempat/tema_juara_tangga_darah");
			}
			if (!pk.getBooleanOr("palu", false)) {
				pk.putBoolean("palu", true);
				Barang.beri(p, paluDarahSenja());
			}
		} else {
			pk.putInt("peringkat", laga.peringkat + 1);
			Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.arena.naik", laga.peringkat + 1).withStyle(ChatFormatting.RED));
		}
		IblisData.ubah(server);
	}

	/** The unique arena gada "Palu Darah Senja" (a city weapon gada of grade roh_tinggi, named, with lore). */
	public static ItemStack paluDarahSenja() {
		ItemStack s = SenjataKota.stack(SenjataKota.Bentuk.GADA, SenjataKota.Mutu.ROH_TINGGI);
		s.set(DataComponents.CUSTOM_NAME, Component.translatable("kultivator.kota_tema.iblis.palu").withStyle(ChatFormatting.DARK_RED));
		s.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("kultivator.kota_tema.iblis.palu.lore").withStyle(ChatFormatting.GRAY))));
		return s;
	}

	static void kalah(ServerPlayer p, Laga laga, String why) {
		if (laga.selesai) {
			return;
		}
		laga.selesai = true;
		LAGA.remove(p.getUUID());
		TemaSibuk.lepas(p, TemaSibuk.ARENA);
		MinecraftServer server = p.level().getServer();
		TemaKota.Kota kota = IblisKota.dari(server, laga.situs);
		if (kota != null && !"lari".equals(why)) {
			Vec3 room = IblisKota.lokal(kota, DISTRIK, RUANG[0], 1.0D, RUANG[1]);
			if (room != null) {
				p.teleportTo(p.level(), room.x, room.y, room.z, java.util.Set.of(), p.getYRot(), 0.0F, true);
			}
			p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LEMAH_TICK, 0));
		}
		if ("lari".equals(why) && laga.biaya > 0L) {
			NilaiBatu.give(p, laga.biaya);
			IblisData.ambilKas(server, laga.situs, laga.biaya);
		}
		Msg.chat(p, Component.translatable("kultivator.kota_tema.iblis.arena.kalah." + why).withStyle(ChatFormatting.RED));
		PestaDarah.lagaSelesai(p, laga.situs, false);
		buangLawan(laga);
	}

	private static void buangLawan(Laga laga) {
		Mob foe = laga.lawan;
		laga.lawan = null;
		if (foe == null || foe.isRemoved()) {
			return;
		}
		if (foe instanceof KultivatorNpc npc) {
			npc.vanish();
		} else {
			foe.discard();
		}
	}

	/** The spectators cheer (clip sorak_arena on the townsfolk near the pit) or fall silent. */
	private static void penonton(ServerLevel level, Vec3 at, boolean sorak) {
		for (KultivatorNpc n : level.getEntitiesOfClass(KultivatorNpc.class, new net.minecraft.world.phys.AABB(at, at).inflate(40.0D, 16.0D, 40.0D),
				n -> n.isAlive() && n.getTarget() == null && !n.memory().getBooleanOr("iblis_arena", false))) {
			if (n.role() == NpcRole.TAWANAN) {
				HidupApi.klip(n, GerakId.TAKUT_SEMBUNYI, 0, 120);
			} else if (sorak || n.getRandom().nextInt(3) == 0) {
				HidupApi.klip(n, sorak ? GerakId.SORAK_ARENA : GerakId.TEPUK_TANGAN, 0, 100);
			}
		}
	}

	private static @Nullable KultivatorNpc tuGang(ServerLevel level, Vec3 near) {
		for (KultivatorNpc n : level.getEntitiesOfClass(KultivatorNpc.class, new net.minecraft.world.phys.AABB(near, near).inflate(64.0D),
				n -> TokohIblis.adalah(n, TokohIblis.TU_GANG))) {
			return n;
		}
		return null;
	}
}
