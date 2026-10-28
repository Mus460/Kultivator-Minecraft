package dev.kultivator.dunia.tempat.kotabesar;

import dev.kultivator.disciple.DuelRules;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.kota.Kota;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.kota.Towns;
import dev.kultivator.dunia.tempat.Kandidat;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatEvents;
import dev.kultivator.dunia.tempat.Titik;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.cerita.TempatDialog;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.ekonomi.KasKota;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The capital's law (DESIGN 38.B.5.2): the crime record ({@link KotaBesarData} {@code kejahatan}, 0-200 points, minus 2
 * every world day), witnesses, the four standings, guards that shadow, halt or hunt, the magistrate's fine and the cells.
 * <ul>
 * <li><b>Crimes</b> ({@link #kejahatan}): only counted when seen — a guard body within 24 blocks or a townsfolk body
 * within 16 with line of sight, or a resident the schedule puts in that lot ({@link HidupApi#saksi}). Each point also
 * costs half a point of Nama Baik; that loss is remembered so paying the fine returns half of it.</li>
 * <li><b>Standings</b>: 10+ Suspected (one guard shadows the player for 30 s and warns him), 25+ Wanted (the posters go
 * up, guards halt him on sight and demand the fine, shops, stations and docks refuse him), 60+ Heavily Wanted (guards
 * attack on sight and a Guard Captain of the fourth tier is sent to hunt him in the district).</li>
 * <li><b>Settling</b>: the fine {@code points x 3 x (band + 1)} NB at the Yamen or to the guard who stops him (points to
 * 0, half the lost Nama Baik back, the fine to the city purse), or surrender: a free cell, the door shut, {@code points x
 * 6 s} (at most 6 minutes, {@link Sibuk} {@code tempat.sel}), nothing taken. Leaving the cell early by any means is an
 * escape: +20 points and Wanted.</li>
 * </ul>
 * There is no player bounty on players (DESIGN 0 rule 2): only NPCs hunt. Every check runs from {@code KotaBesar}'s tick
 * every 20 ticks and only for players near a capital; nothing here changes a block.
 */
public final class Hukum {

	// crime kinds (lang kultivator.kota_besar.kejahatan.<jenis>) and their points (38.B.5.2)
	public static final String LUKAI_WARGA = "lukai_warga";
	public static final String BUNUH_WARGA = "bunuh_warga";
	public static final String BUNUH_PENJAGA = "bunuh_penjaga";
	public static final String CURI = "curi";
	public static final String RUSAK = "rusak";
	public static final String ISTANA_MALAM = "istana_malam";
	public static final String TIDUR_GRATIS = "tidur_gratis";
	public static final String LOMPAT_TEMBOK = "lompat_tembok";
	public static final String DUEL_LIAR = "duel_liar";
	public static final String TOLAK_TAGIHAN = "tolak_tagihan";
	public static final String KABUR = "kabur";

	public static final int POIN_LUKAI_WARGA = 5;
	public static final int POIN_BUNUH_WARGA = 20;
	public static final int POIN_BUNUH_PENJAGA = 30;
	public static final int POIN_CURI = 4;
	/** Per 10 blocks of recorded damage. */
	public static final int POIN_RUSAK = 2;
	public static final int POIN_ISTANA_MALAM = 6;
	public static final int POIN_TIDUR_GRATIS = 2;
	public static final int POIN_LOMPAT_TEMBOK = 3;
	public static final int POIN_DUEL_LIAR = 2;
	public static final int POIN_KABUR = 20;

	public static final int DICURIGAI = 10;
	public static final int BURONAN = 25;
	public static final int BURONAN_BERAT = 60;
	/** Points lost each world day. */
	public static final int SUSUT_HARIAN = 2;
	/** Cell time per point (ticks) and its cap. */
	public static final long SEL_PER_POIN = 120L;
	public static final long SEL_MAKS = 7200L;
	/** Nama Baik needed to walk the palace district at night. */
	public static final int NB_ISTANA = 50;
	public static final double SAKSI_PENJAGA = 24.0D;
	/** A guard who shadows a suspect keeps it up this long (ticks). */
	private static final long BUNTUT_TICK = 600L;
	/** A guard who halted a wanted player may halt him again after this (ticks). */
	private static final long TAHAN_JEDA = 1200L;

	private record Buntut(UUID guard, long sampai) {
	}

	/** Cell doors of the prisoners (player -> leaf). */
	private static final Map<UUID, GerbangKotaEntity> PINTU = new HashMap<>();
	private static final Map<UUID, Buntut> BUNTUT = new HashMap<>();
	private static final Map<UUID, Long> TAHAN = new HashMap<>();
	/** Players already charged for the palace at night this night (day number). */
	private static final Map<UUID, Long> ISTANA = new HashMap<>();
	/** Duel sessions already charged (player -> game time of the charge). */
	private static final Map<UUID, Long> DUEL = new HashMap<>();
	private static final Set<UUID> KAPTEN_DIUTUS = new HashSet<>();
	private static long hariSusut = Long.MIN_VALUE;

	static void init() {
		Sibuk.register("tempat.sel", Hukum::diSel);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Hukum::luka);
		ServerLivingEntityEvents.AFTER_DEATH.register(Hukum::mati);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl && !sp.isSpectator()) {
				bukaPeti(sl, sp, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
		TempatEvents.TEMPAT_RUSAK.register(Hukum::rusak);
		TempatLayar.daftar(LembarKunci.Yamen.LAYAR, Hukum::aksi, Hukum::bangun);
		CeritaApi.daftarJasa(Hukum::jasa);
	}

	static void lupakan() {
		for (GerbangKotaEntity g : PINTU.values()) {
			if (!g.isRemoved()) {
				g.discard();
			}
		}
		PINTU.clear();
		BUNTUT.clear();
		TAHAN.clear();
		ISTANA.clear();
		DUEL.clear();
		KAPTEN_DIUTUS.clear();
		hariSusut = Long.MIN_VALUE;
	}

	static void keluar(UUID id) {
		GerbangKotaEntity g = PINTU.remove(id);
		if (g != null && !g.isRemoved()) {
			g.discard();
		}
		BUNTUT.remove(id);
		TAHAN.remove(id);
		ISTANA.remove(id);
		DUEL.remove(id);
	}

	private static boolean aktif() {
		return KotaBesarConfig.get().hukum && KotaBesarConfig.hidup();
	}

	// ================================================================== the record

	public static int poin(MinecraftServer server, long town, UUID player) {
		return KotaBesarData.get(server).kejahatan(town, player);
	}

	/** 0 clean, 1 suspected, 2 wanted, 3 heavily wanted. */
	public static int tingkat(int poin) {
		return poin >= BURONAN_BERAT ? 3 : poin >= BURONAN ? 2 : poin >= DICURIGAI ? 1 : 0;
	}

	public static String status(int poin) {
		return switch (tingkat(poin)) {
			case 3 -> LembarKunci.Yamen.ST_BURONAN_BERAT;
			case 2 -> LembarKunci.Yamen.ST_BURONAN;
			case 1 -> LembarKunci.Yamen.ST_DICURIGAI;
			default -> LembarKunci.Yamen.ST_BERSIH;
		};
	}

	public static boolean buronan(ServerPlayer player, long town) {
		return town != 0L && aktif() && poin(player.level().getServer(), town, player.getUUID()) >= BURONAN;
	}

	/** The fine that clears the record: {@code points x 3 x (band + 1)} NB. */
	public static long denda(ServerPlayer player, long town) {
		return (long) poin(player.level().getServer(), town, player.getUUID()) * 3L * (Barang.pita(player) + 1);
	}

	/** True when {@code town} is a capital (crimes elsewhere belong to the town module's Nama Baik only). */
	static boolean ibuKota(MinecraftServer server, long town) {
		return town != 0L && Towns.isKotaBesar(server, town);
	}

	/**
	 * A crime of kind {@code jenis} worth {@code poin} by {@code player} in capital {@code town} at {@code at}. With
	 * {@code perluSaksi} the crime counts only when witnessed. Returns the new points (or the old ones when not counted).
	 */
	public static int kejahatan(ServerPlayer player, long town, String jenis, int poin, BlockPos at, boolean perluSaksi) {
		MinecraftServer server = player.level().getServer();
		if (!aktif() || poin <= 0 || player.isCreative() || player.isSpectator() || player instanceof FakePlayer || !ibuKota(server, town)) {
			return town == 0L ? 0 : poin(server, town, player.getUUID());
		}
		ServerLevel level = player.level();
		if (perluSaksi && !disaksikan(level, at, player)) {
			return poin(server, town, player.getUUID());
		}
		KotaBesarData data = KotaBesarData.get(server);
		int before = data.kejahatan(town, player.getUUID());
		int after = data.setKejahatan(town, player.getUUID(), before + poin);
		int added = after - before;
		int nbLoss = (added + 1) / 2;
		if (nbLoss > 0) {
			KotaData.get(server).addReputation(town, player.getUUID(), -nbLoss);
			data.setHilang(town, player.getUUID(), data.hilang(town, player.getUUID()) + nbLoss);
		}
		player.sendSystemMessage(Component.translatable("kultivator.kota_besar.hukum.dicatat",
				Component.translatable("kultivator.kota_besar.kejahatan." + jenis), added, after).withStyle(ChatFormatting.RED));
		catat(server, town, player.getUUID(), jenis);
		KotaBesarTampil.ingatNama(player, town);
		TempatEvents.fireKejahatan(player, town, jenis);
		int lama = tingkat(before);
		int baru = tingkat(after);
		if (baru > lama) {
			naikTingkat(level, player, town, baru);
		}
		return after;
	}

	/** A guard body within 24 blocks, or the townsfolk witness rule of 38NL. */
	static boolean disaksikan(ServerLevel level, BlockPos at, ServerPlayer player) {
		for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new AABB(at).inflate(SAKSI_PENJAGA),
				n -> n.isAlive() && n.role().kelompok == NpcRole.Kelompok.PENJAGA)) {
			if (npc.hasLineOfSight(player)) {
				return true;
			}
		}
		return HidupApi.saksi(level, at, player);
	}

	private static void naikTingkat(ServerLevel level, ServerPlayer player, long town, int tingkat) {
		Component status = Component.translatable("kultivator.kota_besar.hukum.status." + status(tingkatPoin(tingkat)));
		Msg.title(player, status.copy().withStyle(tingkat >= 2 ? ChatFormatting.DARK_RED : ChatFormatting.GOLD),
				Component.translatable("kultivator.kota_besar.hukum.status." + status(tingkatPoin(tingkat)) + ".sub"), 10, 50, 20);
		KultivatorNpc guard = JamMalam.penjagaDekat(level, player.blockPosition(), SAKSI_PENJAGA);
		if (guard != null) {
			Voice.say(guard, tingkat >= 2 ? "kotabesar.wanted" : "kotabesar.suspect", null);
			guard.say(Component.translatable(tingkat >= 2 ? "kultivator.kota_besar.hukum.seru_buronan" : "kultivator.kota_besar.hukum.seru_curiga"), 80);
		}
		if (tingkat >= 2) {
			KotaBesarTampil.poster(level.getServer(), town);
			CeritaApi.kabar(level, town, "kota_besar.buronan", player.getDisplayName());
		}
	}

	private static int tingkatPoin(int tingkat) {
		return switch (tingkat) {
			case 3 -> BURONAN_BERAT;
			case 2 -> BURONAN;
			case 1 -> DICURIGAI;
			default -> 0;
		};
	}

	/** Clears the record (fine paid or sentence served): points 0, {@code kembali} of the lost Nama Baik returned. */
	static void bersihkan(MinecraftServer server, long town, UUID player, double kembali) {
		KotaBesarData data = KotaBesarData.get(server);
		int lost = data.hilang(town, player);
		data.setKejahatan(town, player, 0);
		data.setHilang(town, player, 0);
		int back = (int) Math.floor(lost * kembali);
		if (back > 0) {
			KotaData.get(server).addReputation(town, player, back);
		}
		BUNTUT.remove(player);
		TAHAN.remove(player);
		KAPTEN_DIUTUS.remove(player);
		KotaBesarTampil.poster(server, town);
	}

	// ================================================================== crime sources

	private static void luka(LivingEntity entity, DamageSource source, float baseDamage, float taken, boolean blocked) {
		if (!(entity instanceof KultivatorNpc npc) || taken <= 0.0F || !(source.getEntity() instanceof ServerPlayer player)
				|| !(npc.level() instanceof ServerLevel level) || !npc.isAlive()) {
			return;
		}
		if (DuelRules.dueling(npc, player) || !npc.role().townsfolk() && npc.role().kelompok != NpcRole.Kelompok.PENJAGA) {
			return;
		}
		long town = npc.town();
		if (!ibuKota(level.getServer(), town)) {
			return;
		}
		long now = level.getGameTime();
		CompoundTag m = npc.memory();
		if (now - m.getLongOr("kb_luka", -1000L) < 200L) {
			return;
		}
		m.putLong("kb_luka", now);
		boolean seen = npc.hasLineOfSight(player) || disaksikan(level, npc.blockPosition(), player);
		if (seen) {
			kejahatan(player, town, LUKAI_WARGA, POIN_LUKAI_WARGA, npc.blockPosition(), false);
		}
	}

	private static void mati(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof KultivatorNpc npc) || !(source.getEntity() instanceof ServerPlayer player)
				|| !(npc.level() instanceof ServerLevel level)) {
			return;
		}
		if (DuelRules.dueling(npc, player)) {
			return;
		}
		long town = npc.town();
		if (!ibuKota(level.getServer(), town)) {
			return;
		}
		boolean guard = npc.role().kelompok == NpcRole.Kelompok.PENJAGA;
		if (!guard && !npc.role().townsfolk()) {
			return;
		}
		kejahatan(player, town, guard ? BUNUH_PENJAGA : BUNUH_WARGA, guard ? POIN_BUNUH_PENJAGA : POIN_BUNUH_WARGA, npc.blockPosition(), true);
	}

	/** Opening a chest of a {@code peti_milik} point while seen is theft. */
	private static void bukaPeti(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (!aktif() || player.isCreative() || level.dimension() != Level.OVERWORLD) {
			return;
		}
		if (!(level.getBlockEntity(pos) instanceof BaseContainerBlockEntity)) {
			return;
		}
		Kandidat k = KotaBesarKota.di(level, pos);
		if (k == null) {
			return;
		}
		long town = KotaBesarKota.situs(k);
		for (Titik t : TempatApi.titik(level, town, "peti_milik")) {
			if (t.pos().equals(pos) || t.pos().distManhattan(pos) <= 1) {
				kejahatan(player, town, CURI, POIN_CURI, pos, true);
				return;
			}
		}
	}

	/** Recorded damage by a player (38PR): 2 points per 10 blocks, if a witness saw him. */
	private static void rusak(ServerLevel level, long situsId, net.minecraft.world.level.levelgen.structure.BoundingBox gugus, int blok,
			@Nullable UUID pelaku) {
		if (pelaku == null || blok < 10 || !ibuKota(level.getServer(), situsId)) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(pelaku);
		if (player == null || player.level() != level) {
			return;
		}
		BlockPos at = gugus.getCenter();
		kejahatan(player, situsId, RUSAK, POIN_RUSAK * (blok / 10), at, true);
	}

	// ================================================================== tick (every 20 ticks)

	static void tick(MinecraftServer server) {
		if (!aktif()) {
			return;
		}
		long day = Kota.day(server);
		if (hariSusut == Long.MIN_VALUE) {
			hariSusut = day;
		} else if (day != hariSusut) {
			susut(server, (int) Math.min(30L, day - hariSusut));
			hariSusut = day;
		}
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		// a prisoner who respawned (or was taken) out of the capital is no longer "in a cell"; the sentence stays, the next visit judges it
		for (UUID id : List.copyOf(PINTU.keySet())) {
			ServerPlayer prisoner = server.getPlayerList().getPlayer(id);
			if (prisoner != null && (prisoner.level() != overworld || KotaBesarKota.di(overworld, prisoner.blockPosition()) == null)) {
				GerbangKotaEntity door = PINTU.remove(id);
				if (door != null && !door.isRemoved()) {
					door.discard();
				}
			}
		}
		for (ServerPlayer p : overworld.players()) {
			if (p.isSpectator()) {
				continue;
			}
			Kandidat k = KotaBesarKota.di(overworld, p.blockPosition());
			if (k == null) {
				continue;
			}
			long town = KotaBesarKota.situs(k);
			sel(overworld, p, town, now);
			if (p.isCreative()) {
				continue;
			}
			istanaMalam(overworld, p, k, town, day);
			duelLiar(overworld, p, k, town, now);
			int poin = poin(server, town, p.getUUID());
			switch (tingkat(poin)) {
				case 1 -> buntuti(overworld, p, town, now);
				case 2 -> tahan(overworld, p, town, now);
				case 3 -> buru(overworld, p, k, town, now);
				default -> {
				}
			}
		}
	}

	private static void susut(MinecraftServer server, int hari) {
		KotaBesarData data = KotaBesarData.get(server);
		for (long town : data.kotaIds()) {
			for (String id : data.pelaku(town)) {
				try {
					UUID uuid = UUID.fromString(id);
					if (!data.sel(town, uuid).isEmpty()) {
						continue;
					}
					int left = data.setKejahatan(town, uuid, data.kejahatan(town, uuid) - SUSUT_HARIAN * hari);
					if (left == 0) {
						data.setHilang(town, uuid, 0);
					}
				} catch (IllegalArgumentException ignored) {
					// not a player id
				}
			}
			KotaBesarTampil.poster(server, town);
		}
	}

	private static void istanaMalam(ServerLevel level, ServerPlayer p, Kandidat k, long town, long day) {
		if (!JamMalam.malam(level.getServer()) || !"istana".equals(KotaBesarKota.distrik(k, p.blockPosition()))) {
			return;
		}
		if (KotaData.get(level.getServer()).reputation(town, p.getUUID()) >= NB_ISTANA || ISTANA.getOrDefault(p.getUUID(), -1L) == day) {
			return;
		}
		int[] l = KotaBesarKota.lokal(k, p.getBlockX(), p.getBlockZ());
		if (l[1] > -96) {
			return;
		}
		if (disaksikan(level, p.blockPosition(), p)) {
			ISTANA.put(p.getUUID(), day);
			kejahatan(p, town, ISTANA_MALAM, POIN_ISTANA_MALAM, p.blockPosition(), false);
		}
	}

	private static void duelLiar(ServerLevel level, ServerPlayer p, Kandidat k, long town, long now) {
		if (!DuelRules.inDuel(p) || !KotaBesarKota.dalamTembok(k, p.blockPosition())) {
			return;
		}
		String d = KotaBesarKota.distrik(k, p.blockPosition());
		if ("arena".equals(d) || TurnamenAgung.diLapangan(level, p)) {
			return;
		}
		Long last = DUEL.get(p.getUUID());
		if (last != null && now - last < 2400L) {
			return;
		}
		DUEL.put(p.getUUID(), now);
		kejahatan(p, town, DUEL_LIAR, POIN_DUEL_LIAR, p.blockPosition(), true);
	}

	/** Suspected: one guard shadows the player for 30 s at four blocks and warns him once. */
	private static void buntuti(ServerLevel level, ServerPlayer p, long town, long now) {
		Buntut b = BUNTUT.get(p.getUUID());
		if (b != null && now > b.sampai()) {
			if (now > b.sampai() + 6000L) {
				BUNTUT.remove(p.getUUID());
			}
			return;
		}
		KultivatorNpc guard = b == null ? null : level.getEntity(b.guard()) instanceof KultivatorNpc g && g.isAlive() ? g : null;
		if (guard == null) {
			guard = JamMalam.penjagaDekat(level, p.blockPosition(), SAKSI_PENJAGA);
			if (guard == null || !guard.hasLineOfSight(p)) {
				return;
			}
			BUNTUT.put(p.getUUID(), new Buntut(guard.getUUID(), now + BUNTUT_TICK));
			guard.say(Component.translatable("kultivator.kota_besar.hukum.awas"), 80);
			Voice.say(guard, "kotabesar.suspect", null);
			Msg.actionBar(p, Component.translatable("kultivator.kota_besar.hukum.dibuntuti").withStyle(ChatFormatting.GOLD));
		}
		HidupApi.tatap(guard, p);
		if (guard.distanceTo(p) > 4.5F && guard.getTarget() == null) {
			guard.getNavigation().moveTo(p, 0.9D);
		} else {
			guard.getNavigation().stop();
		}
	}

	/** Wanted: a guard who sees him walks up and, close enough, halts him with the fine (Yamen screen). */
	private static void tahan(ServerLevel level, ServerPlayer p, long town, long now) {
		if (diSel(p)) {
			return;
		}
		KultivatorNpc guard = JamMalam.penjagaDekat(level, p.blockPosition(), SAKSI_PENJAGA);
		if (guard == null || !guard.hasLineOfSight(p) || guard.getTarget() != null) {
			return;
		}
		HidupApi.tatap(guard, p);
		if (guard.distanceTo(p) > 3.0F) {
			guard.getNavigation().moveTo(p, 1.1D);
			return;
		}
		guard.getNavigation().stop();
		if (now - TAHAN.getOrDefault(p.getUUID(), -TAHAN_JEDA) < TAHAN_JEDA) {
			return;
		}
		TAHAN.put(p.getUUID(), now);
		guard.say(Component.translatable("kultivator.kota_besar.hukum.berhenti"), 100);
		Voice.say(guard, "kotabesar.wanted", null);
		CompoundTag lembar = bangun(p, guard.getId());
		if (lembar != null) {
			TempatLayar.buka(p, LembarKunci.Yamen.LAYAR, guard.getId(), lembar);
		}
	}

	/** Heavily wanted: every guard in sight attacks, and one Guard Captain (fourth tier) is sent once per standing. */
	private static void buru(ServerLevel level, ServerPlayer p, Kandidat k, long town, long now) {
		if (diSel(p)) {
			return;
		}
		for (KultivatorNpc guard : level.getEntitiesOfClass(KultivatorNpc.class, p.getBoundingBox().inflate(SAKSI_PENJAGA),
				n -> n.isAlive() && n.role().kelompok == NpcRole.Kelompok.PENJAGA && n.town() == town)) {
			if (guard.hasLineOfSight(p) && !guard.isHostileTo(p.getUUID())) {
				guard.markHostile(p);
				guard.setTarget(p);
			}
		}
		if (KAPTEN_DIUTUS.contains(p.getUUID())) {
			return;
		}
		String distrik = KotaBesarKota.distrik(k, p.blockPosition());
		if (distrik == null) {
			return;
		}
		Vec3 from = p.position().add(p.getLookAngle().scale(-12.0D));
		BlockPos spot = TempatApi.groundSpot(level, Mth.floor(from.x), Mth.floor(from.z), p.getBlockY());
		if (spot == null) {
			spot = p.blockPosition().offset(8, 0, 8);
		}
		KultivatorNpc kapten = HidupApi.tubuh(level, town, NpcRole.KAPTEN_PENJAGA, spot, p.getYRot() + 180.0F);
		if (kapten == null) {
			return;
		}
		KAPTEN_DIUTUS.add(p.getUUID());
		int realm = Math.max(kapten.realm(), dev.kultivator.data.Cultivators.get(p).realm().ordinal());
		kapten.setup(NpcRole.KAPTEN_PENJAGA, 4, kapten.look(), realm, 3, kapten.dao(), kapten.voice(), kapten.robe(), kapten.plate());
		kapten.memory().putBoolean("kb_pemburu", true);
		kapten.setLonely(96.0D, 2400);
		TechniquePower.markRealmFoe(kapten);
		kapten.markHostile(p);
		kapten.setTarget(p);
		kapten.say(Component.translatable("kultivator.kota_besar.hukum.kapten"), 100);
		Voice.say(kapten, "kotabesar.wanted", null);
		Msg.title(p, Component.translatable("kultivator.kota_besar.hukum.diburu").withStyle(ChatFormatting.DARK_RED),
				Component.translatable("kultivator.kota_besar.hukum.diburu.sub"), 10, 60, 20);
	}

	// ================================================================== settling

	/** Pays the fine; {@code npcId} the guard or magistrate taking it (-1 at the desk). True when paid. */
	public static boolean bayarDenda(ServerPlayer player, long town) {
		MinecraftServer server = player.level().getServer();
		int poin = poin(server, town, player.getUUID());
		if (poin <= 0) {
			return false;
		}
		long fine = denda(player, town);
		if (!EkonomiApi.bayar(player, fine, "kotabesar.denda")) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_besar.hukum.kurang", Barang.nb(fine)).withStyle(ChatFormatting.RED));
			return false;
		}
		KasKota.tambah(server, town, fine);
		bersihkan(server, town, player.getUUID(), 0.5D);
		for (KultivatorNpc guard : player.level().getEntitiesOfClass(KultivatorNpc.class, player.getBoundingBox().inflate(48.0D),
				n -> n.town() == town && n.isHostileTo(player.getUUID()))) {
			guard.forgive(player.getUUID());
			if (guard.getTarget() == player) {
				guard.setTarget(null);
			}
			if (guard.memory().getBooleanOr("kb_pemburu", false)) {
				guard.vanish();
			}
		}
		player.sendSystemMessage(Component.translatable("kultivator.kota_besar.hukum.lunas", Barang.nb(fine)).withStyle(ChatFormatting.GREEN));
		KultivatorNpc near = JamMalam.penjagaDekat(player.level(), player.blockPosition(), 8.0D);
		if (near != null) {
			Voice.say(near, "kotabesar.fine_paid", null);
		}
		return true;
	}

	/** True while {@code player} serves a sentence in any capital. */
	public static boolean diSel(ServerPlayer player) {
		return PINTU.containsKey(player.getUUID());
	}

	/** Gives himself up: a free cell of {@code town}, the door shut, {@code points x 6 s}. False when no cell is loaded. */
	public static boolean serahkanDiri(ServerPlayer player, long town) {
		MinecraftServer server = player.level().getServer();
		int poin = poin(server, town, player.getUUID());
		if (poin <= 0 || diSel(player)) {
			return false;
		}
		KotaBesarAnchors.KotaHidup kota = KotaBesarAnchors.kota(town);
		ServerLevel level = player.level();
		if (kota == null || kota.sel.isEmpty()) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_besar.hukum.sel_jauh").withStyle(ChatFormatting.RED));
			return false;
		}
		int[] cell = null;
		DuniaAnchorEntity anchor = null;
		int nomor = -1;
		Set<Integer> taken = new HashSet<>();
		KotaBesarData data = KotaBesarData.get(server);
		for (String id : data.tahanan(town)) {
			try {
				taken.add(data.sel(town, UUID.fromString(id)).getIntOr("nomor", -1));
			} catch (IllegalArgumentException ignored) {
				// skip
			}
		}
		outer:
		for (DuniaAnchorEntity a : kota.sel) {
			List<int[]> pts = KotaBesarAnchors.titikSel(a);
			for (int i = 0; i < pts.size(); i++) {
				if (!taken.contains(i)) {
					cell = pts.get(i);
					anchor = a;
					nomor = i;
					break outer;
				}
			}
		}
		if (cell == null) {
			Msg.actionBar(player, Component.translatable("kultivator.kota_besar.hukum.sel_penuh").withStyle(ChatFormatting.RED));
			return false;
		}
		int putar = KotaBesarAnchors.putarDi(level, anchor);
		BlockPos in = KotaBesarAnchors.dari(anchor, putar, cell[0], cell[1], cell[2]);
		BlockPos door = KotaBesarAnchors.dari(anchor, putar, cell[3], cell[4], cell[5]);
		long ticks = Math.min(SEL_MAKS, poin * SEL_PER_POIN);
		long now = level.getGameTime();
		CompoundTag sel = new CompoundTag();
		sel.putLong("sampai", now + ticks);
		sel.putInt("nomor", nomor);
		sel.putInt("poin", poin);
		sel.putLong("in", in.asLong());
		sel.putLong("pintu", door.asLong());
		data.setSel(town, player.getUUID(), sel);
		TempatLayar.tutup(player);
		JamMalam.abaikan(player, ticks + 200L);
		player.teleportTo(in.getX() + 0.5D, in.getY(), in.getZ() + 0.5D);
		pasangPintu(level, player.getUUID(), town, in, door);
		player.sendSystemMessage(Component.translatable("kultivator.kota_besar.hukum.masuk_sel", ticks / 20L).withStyle(ChatFormatting.GRAY));
		KultivatorNpc near = JamMalam.penjagaDekat(level, door, 16.0D);
		if (near != null) {
			Voice.say(near, "kotabesar.jail", null);
			near.say(Component.translatable("kultivator.kota_besar.hukum.sel_kata"), 80);
		}
		level.playSound(null, door, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.8F);
		return true;
	}

	private static void pasangPintu(ServerLevel level, UUID player, long town, BlockPos in, BlockPos door) {
		GerbangKotaEntity old = PINTU.remove(player);
		if (old != null && !old.isRemoved()) {
			old.discard();
		}
		GerbangKotaEntity g = KotaBesarEntities.GERBANG_KOTA.create(level, EntitySpawnReason.EVENT);
		if (g == null) {
			return;
		}
		boolean alongX = door.getX() == in.getX();
		g.snapTo(door.getX() + 0.5D, door.getY(), door.getZ() + 0.5D, alongX ? 0.0F : 90.0F, 0.0F);
		g.pasang(town, GerbangKotaEntity.NAMA_SEL, 1, 2, false);
		if (level.addFreshEntity(g)) {
			PINTU.put(player, g);
		}
	}

	/** Every 20 ticks for a player in a capital: the sentence, the escape, the release. */
	private static void sel(ServerLevel level, ServerPlayer p, long town, long now) {
		KotaBesarData data = KotaBesarData.get(level.getServer());
		CompoundTag sel = data.sel(town, p.getUUID());
		if (sel.isEmpty()) {
			return;
		}
		BlockPos in = BlockPos.of(sel.getLongOr("in", 0L));
		BlockPos door = BlockPos.of(sel.getLongOr("pintu", 0L));
		long until = sel.getLongOr("sampai", 0L);
		GerbangKotaEntity g = PINTU.get(p.getUUID());
		if (now >= until) {
			data.setSel(town, p.getUUID(), null);
			bersihkan(level.getServer(), town, p.getUUID(), 0.0D);
			if (g != null) {
				g.setBuka(true);
				Voice.later(60, () -> {
					GerbangKotaEntity gone = PINTU.remove(p.getUUID());
					if (gone != null && !gone.isRemoved()) {
						gone.discard();
					}
				});
			}
			JamMalam.abaikan(p, 400L);
			p.sendSystemMessage(Component.translatable("kultivator.kota_besar.hukum.bebas").withStyle(ChatFormatting.GREEN));
			return;
		}
		if (g == null || g.isRemoved()) {
			pasangPintu(level, p.getUUID(), town, in, door);
		}
		double dx = p.getX() - (in.getX() + 0.5D);
		double dz = p.getZ() - (in.getZ() + 0.5D);
		if (dx * dx + dz * dz > 16.0D || Math.abs(p.getY() - in.getY()) > 4.0D) {
			kabur(level, p, town);
			return;
		}
		long left = (until - now) / 20L;
		if (left % 15L == 0L || left <= 5L) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_besar.hukum.sisa_sel", left).withStyle(ChatFormatting.GRAY));
		}
	}

	private static void kabur(ServerLevel level, ServerPlayer p, long town) {
		KotaBesarData data = KotaBesarData.get(level.getServer());
		data.setSel(town, p.getUUID(), null);
		GerbangKotaEntity g = PINTU.remove(p.getUUID());
		if (g != null && !g.isRemoved()) {
			g.discard();
		}
		int now = data.kejahatan(town, p.getUUID());
		data.setKejahatan(town, p.getUUID(), Math.max(BURONAN, now) + POIN_KABUR);
		p.sendSystemMessage(Component.translatable("kultivator.kota_besar.hukum.kabur", POIN_KABUR).withStyle(ChatFormatting.DARK_RED));
		TempatEvents.fireKejahatan(p, town, KABUR);
		naikTingkat(level, p, town, tingkat(data.kejahatan(town, p.getUUID())));
	}

	/** A prisoner pushes at his own cell door (right click): the time left; anybody else: the guard says no. */
	static void ketukSel(ServerPlayer player, GerbangKotaEntity g) {
		CompoundTag sel = KotaBesarData.get(player.level().getServer()).sel(g.town(), player.getUUID());
		if (!sel.isEmpty()) {
			long left = Math.max(0L, sel.getLongOr("sampai", 0L) - player.level().getGameTime()) / 20L;
			Msg.actionBar(player, Component.translatable("kultivator.kota_besar.hukum.sisa_sel", left).withStyle(ChatFormatting.GRAY));
		} else {
			Msg.actionBar(player, Component.translatable("kultivator.kota_besar.hukum.sel_terkunci").withStyle(ChatFormatting.GRAY));
		}
	}

	// ================================================================== Yamen screen (38.U.3.9)

	private static long townDari(ServerPlayer p, int npcId) {
		if (npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc npc && ibuKota(p.level().getServer(), npc.town())) {
			return npc.town();
		}
		Kandidat k = KotaBesarKota.di(p.level(), p.blockPosition());
		return k == null ? 0L : KotaBesarKota.situs(k);
	}

	private static @Nullable CompoundTag bangun(ServerPlayer p, int npcId) {
		long town = townDari(p, npcId);
		if (town == 0L) {
			return null;
		}
		MinecraftServer server = p.level().getServer();
		CompoundTag tag = KotaBesarTampil.kepala(p, town, LembarKunci.GAYA_BATU, LembarKunci.DISTRIK_PUSAT);
		LembarKunci.teks(tag, LembarKunci.JUDUL, Component.translatable("kultivator.kota_besar.yamen.judul"));
		Kandidat k = KotaBesarKota.dariSitus(server.overworld(), town);
		if (k != null) {
			LembarKunci.teks(tag, LembarKunci.SUBJUDUL, KotaBesarNames.hakim(server.overworld().getSeed(), k.index()));
		}
		if (npcId >= 0 && p.level().getEntity(npcId) instanceof KultivatorNpc npc) {
			tag.put(LembarKunci.NPC, KotaBesarTampil.kartu(npc));
		}
		CompoundTag sel = KotaBesarData.get(server).sel(town, p.getUUID());
		if (!sel.isEmpty()) {
			CompoundTag s = new CompoundTag();
			s.putLong(LembarKunci.SISA_TICK, Math.max(0L, sel.getLongOr("sampai", 0L) - p.level().getGameTime()));
			tag.put(LembarKunci.Yamen.SEL, s);
			return tag;
		}
		int poin = poin(server, town, p.getUUID());
		tag.putInt(LembarKunci.Yamen.POIN, poin);
		tag.putString(LembarKunci.STATUS, status(poin));
		if (poin > 0) {
			tag.putLong(LembarKunci.Yamen.DENDA, denda(p, town));
			LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.yamen.catatan",
					Barang.nb(denda(p, town)), Math.min(SEL_MAKS, poin * SEL_PER_POIN) / 20L));
		} else {
			LembarKunci.teks(tag, LembarKunci.CATATAN, Component.translatable("kultivator.kota_besar.yamen.bersih"));
		}
		ListTag riwayat = new ListTag();
		CompoundTag catatan = KotaBesarData.get(server).bagian(town, "catatan").getCompoundOrEmpty(p.getUUID().toString());
		for (String kunci : catatan.keySet()) {
			if (riwayat.size() >= 8) {
				break;
			}
			CompoundTag row = new CompoundTag();
			LembarKunci.teks(row, LembarKunci.NAMA, Component.translatable("kultivator.kota_besar.kejahatan." + kunci));
			row.putInt(LembarKunci.JUMLAH, catatan.getIntOr(kunci, 0));
			riwayat.add(row);
		}
		if (!riwayat.isEmpty()) {
			tag.put(LembarKunci.RIWAYAT, riwayat);
		}
		return tag;
	}

	private static void aksi(ServerPlayer p, int npcId, String op) {
		long town = townDari(p, npcId);
		if (town == 0L) {
			return;
		}
		String verb = LembarKunci.verb(op);
		switch (verb) {
			case LembarKunci.Yamen.OP_DENDA -> {
				if (bayarDenda(p, town)) {
					CompoundTag tag = bangun(p, npcId);
					if (tag != null) {
						LembarKunci.teks(tag, LembarKunci.PESAN, Component.translatable("kultivator.kota_besar.yamen.lunas"));
						tag.putString(LembarKunci.PESAN_JENIS, LembarKunci.PESAN_OK);
						TempatLayar.segarkan(p, LembarKunci.Yamen.LAYAR, npcId, tag);
					}
				}
			}
			case LembarKunci.Yamen.OP_SERAH -> serahkanDiri(p, town);
			default -> {
			}
		}
	}

	/** The "Yamen" entry of the dialog's services: magistrates always, guards of a capital while the record is not clean. */
	private static List<TempatDialog.JasaEntri> jasa(ServerPlayer p, KultivatorNpc npc) {
		List<TempatDialog.JasaEntri> out = new ArrayList<>();
		if (!aktif() || !ibuKota(p.level().getServer(), npc.town())) {
			return out;
		}
		NpcRole r = npc.role();
		int poin = poin(p.level().getServer(), npc.town(), p.getUUID());
		boolean hakim = r == NpcRole.HAKIM;
		boolean guard = r.kelompok == NpcRole.Kelompok.PENJAGA && poin > 0;
		if (hakim || guard) {
			out.add(new TempatDialog.JasaEntri(LembarKunci.Yamen.LAYAR, Component.translatable("kultivator.kota_besar.jasa.yamen"),
					LembarKunci.Ikon.YAMEN, poin > 0 ? Component.translatable("kultivator.kota_besar.jasa.yamen.denda", Barang.nb(denda(p, npc.town())))
					: Component.translatable("kultivator.kota_besar.jasa.yamen.catatan"), false, null));
		}
		return out;
	}

	/** Remembers the kinds of crimes per player for the Yamen record (count per kind). */
	static void catat(MinecraftServer server, long town, UUID player, String jenis) {
		KotaBesarData data = KotaBesarData.get(server);
		CompoundTag all = data.bagian(town, "catatan");
		CompoundTag mine = all.getCompoundOrEmpty(player.toString());
		mine.putInt(jenis, mine.getIntOr(jenis, 0) + 1);
		all.put(player.toString(), mine);
		data.putBagian(town, "catatan", all);
	}

	private Hukum() {
	}
}
