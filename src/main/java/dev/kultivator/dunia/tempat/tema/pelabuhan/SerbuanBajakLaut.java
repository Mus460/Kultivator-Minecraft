package dev.kultivator.dunia.tempat.tema.pelabuhan;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.kota.KotaData;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.tempat.cerita.AdeganPayload;
import dev.kultivator.dunia.tempat.cerita.CeritaApi;
import dev.kultivator.dunia.tempat.ekonomi.Barang;
import dev.kultivator.dunia.tempat.ekonomi.EkonomiApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.dunia.tempat.tema.TemaKalender;
import dev.kultivator.dunia.tempat.tema.TemaMekanisme;
import dev.kultivator.musuh.RogueCultivators;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
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

/**
 * Serbuan Bajak Laut (DESIGN 38.T.3.4): Kapten Hiu Hitam's black-sailed junk ({@link KapalBajakLautEntity}) comes in at
 * night, drops its gangway in 10 ticks at a free mooring of the Dermaga Agung, and a band of sea rogues
 * ({@code RogueCultivators.spawnBand}, role raid, {@code 6 + 3 x players} a wave, at most 18 alive) storms the quay and
 * the warehouses; the guards and sailors fight, the residents shelter ({@code HidupApi.timpaJadwal(..., "pertahanan")}).
 * <ul>
 * <li><b>When</b>: every 18 +- 4 world days at night (calendar event {@code bajak_laut}, 14000-22000), or the night after
 * a player refused the bribe... and stage 4 of {@code tema_pelabuhan_pelita} ({@link #siapkanQuest}: 3 waves, 4 when the
 * player took Zhao Wanli's bribe; Hei Sha watches from his deck). No raids once Hei Sha's fate is settled
 * ({@code bajak_berhenti}). A raid waits while a player in the town is busy ({@code Sibuk.why}) or has had his share of
 * enemy events today ({@code Sibuk.enemyRefusal}); it counts as an enemy event ({@code tema.pelabuhan_bajak}).</li>
 * <li><b>Won</b> (every wave beaten): Nama Baik +4 for everyone who stood on the quays (+8 for the chain's player),
 * {@code 25 x (band + 1)} NB from the town treasury, a roll of {@code kultivator:tema/pelabuhan/bajak_laut} each;
 * mission {@code bajak_laut} done; quest event {@code pelabuhan_bajak menang}.</li>
 * <li><b>Lost</b> (the night ends or every defender left): the Pasar Seberang closes for a day and one merchant junk
 * skips its next call ({@link JadwalKapal#batalkan}); quest event {@code pelabuhan_bajak kalah}.</li>
 * </ul>
 * No block ever changes: the pirates carry no fire or powder and the rogues' techniques are visual without the player's
 * own Mode Kehancuran.
 */
public final class SerbuanBajakLaut {

	public static final String ACARA = "bajak_laut";
	public static final String SIBUK = "tema.pelabuhan_bajak";
	public static final int DARI = 14000;
	public static final int SAMPAI = 22000;
	public static final int MAKS_HIDUP = 18;
	public static final int JARAK_BELA = 64;
	public static final ResourceKey<LootTable> RAMPASAN = ResourceKey.create(Registries.LOOT_TABLE, Kultivator.id("tema/pelabuhan/bajak_laut"));

	enum Fase {
		DATANG, TITIAN, GELOMBANG, SELESAI
	}

	/** One raid on one town. */
	static final class Serbuan {
		final long situs;
		final int gelombangTotal;
		final @Nullable UUID questPemain;
		final Vec3 laut;
		final Vec3 tambat;
		final Vec3 darat;
		final float yaw;
		Fase fase = Fase.DATANG;
		long mulai;
		int gelombang;
		int hidup;
		@Nullable KapalBajakLautEntity kapal;
		@Nullable KultivatorNpc heiSha;
		final Set<UUID> pembela = new HashSet<>();
		long terakhirBela;

		Serbuan(long situs, int gelombangTotal, @Nullable UUID questPemain, Vec3 laut, Vec3 tambat, Vec3 darat, float yaw, long mulai) {
			this.situs = situs;
			this.gelombangTotal = gelombangTotal;
			this.questPemain = questPemain;
			this.laut = laut;
			this.tambat = tambat;
			this.darat = darat;
			this.yaw = yaw;
			this.mulai = mulai;
			this.terakhirBela = mulai;
		}
	}

	private static final Map<Long, Serbuan> AKTIF = new HashMap<>();
	private static boolean initialised;

	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		TemaKalender.daftar(PelabuhanKota.TEMA, ACARA, DARI, SAMPAI, SerbuanBajakLaut::hariSerbuan, new TemaKalender.Pelaksana() {
			@Override
			public void mulai(ServerLevel level, long situsId, boolean dipaksa) {
				mulaiSerbuan(level, situsId, null, 2, dipaksa);
			}

			@Override
			public void akhir(ServerLevel level, long situsId) {
				Serbuan s = AKTIF.get(situsId);
				if (s != null && s.fase != Fase.SELESAI && s.questPemain == null) {
					selesai(level, s, false);
				}
			}
		});
		Sibuk.registerEnemy(SIBUK, SerbuanBajakLaut::terlibat);
		ServerTickEvents.END_SERVER_TICK.register(SerbuanBajakLaut::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> AKTIF.clear());
	}

	/** Raid days: the town's next raid day (18 +- 4 after the last), not once raids stopped, not without a live town. */
	private static boolean hariSerbuan(ServerLevel level, long hari, long situs, BlockPos pusat) {
		MinecraftServer server = level.getServer();
		if (!PelabuhanKota.cfg().serbuanBajakLaut || berhenti(server, situs)) {
			return false;
		}
		CompoundTag kota = PelabuhanKota.kota(server, situs);
		long next = kota.getLongOr("bajak_berikut", Long.MIN_VALUE);
		if (next == Long.MIN_VALUE) {
			next = hari + 6L + Math.floorMod(PelabuhanKota.hash(situs, hari, 380406L), 9L);
			kota.putLong("bajak_berikut", next);
			PelabuhanKota.ubah(server);
		}
		return hari == next;
	}

	/** True once Hei Sha's fate is settled in that town (no more raids). */
	public static boolean berhenti(MinecraftServer server, long situs) {
		return PelabuhanKota.kota(server, situs).getBooleanOr("bajak_berhenti", false);
	}

	/** True while the player stands in a raid (a defender within 64 blocks of the quay). */
	static boolean terlibat(ServerPlayer player) {
		for (Serbuan s : AKTIF.values()) {
			if (s.fase != Fase.SELESAI && player.position().distanceToSqr(s.darat) < JARAK_BELA * JARAK_BELA) {
				return true;
			}
		}
		return false;
	}

	/** Stage 4 of the chain: the raid comes the next night (or tonight when it is already dark). */
	static void siapkanQuest(MinecraftServer server, long situs, ServerPlayer player, int waves) {
		CompoundTag kota = PelabuhanKota.kota(server, situs);
		CompoundTag q = new CompoundTag();
		q.putString("pemain", player.getUUID().toString());
		q.putInt("gelombang", waves);
		long jam = PelabuhanKota.jam(player.level());
		q.putLong("hari", PelabuhanKota.hari(player.level()) + (jam >= DARI ? 0L : 0L));
		kota.put("bajak_quest", q);
		PelabuhanKota.ubah(server);
		Msg.chat(player, Component.translatable("kultivator.kota_tema.pelabuhan.bajak.firasat").withStyle(ChatFormatting.DARK_AQUA));
	}

	// ================================================================== start

	/** Starts a raid; false when no mooring could be found (the town's quays not loaded) or it must wait. */
	static boolean mulaiSerbuan(ServerLevel level, long situs, @Nullable ServerPlayer quest, int waves, boolean dipaksa) {
		if (AKTIF.containsKey(situs)) {
			return false;
		}
		PelabuhanKota.Hidup kota = PelabuhanKota.cari(situs);
		if (kota == null) {
			return false;
		}
		List<ServerPlayer> near = level.getPlayers(p -> kota.jarak(p.position()) < PelabuhanKota.JANGKAU_KOTA && !p.isSpectator());
		if (near.isEmpty()) {
			return false;
		}
		if (!dipaksa) {
			for (ServerPlayer p : near) {
				if ((quest == null || p != quest) && (Sibuk.why(p) != null || Sibuk.enemyRefusal(p) != null)) {
					// a busy player: the raid waits for another night (no stacking of dangers)
					PelabuhanKota.kota(level.getServer(), situs).putLong("bajak_berikut", PelabuhanKota.hari(level) + 1L);
					PelabuhanKota.ubah(level.getServer());
					return false;
				}
			}
		}
		DuniaAnchorEntity berth = tambatanBebas(level.getServer(), kota);
		if (berth == null) {
			return false;
		}
		double r = Math.toRadians(berth.getYRot());
		Vec3 maju = new Vec3(-Math.sin(r), 0.0D, Math.cos(r));
		Vec3 side = new Vec3(-Math.cos(r), 0.0D, -Math.sin(r));
		Double surface = JadwalKapal.permukaanAir(level, berth.blockPosition());
		double y = surface == null ? level.getSeaLevel() : surface;
		Vec3 tambat = new Vec3(berth.getX(), y, berth.getZ());
		Vec3 laut = tambat.subtract(maju.scale(140.0D));
		Vec3 darat = new Vec3(berth.getX(), berth.getY() + 1.0D, berth.getZ()).add(side.scale(4.0D)).add(maju.scale(10.0D));
		Serbuan s = new Serbuan(situs, Math.max(1, waves), quest == null ? null : quest.getUUID(), laut, tambat, darat, berth.getYRot(), level.getGameTime());
		KapalBajakLautEntity junk = TemaEntities.KAPAL_BAJAK_LAUT.create(level, EntitySpawnReason.EVENT);
		if (junk != null && DuniaBudget.mayAdd(level, TemaMekanisme.JENIS)) {
			junk.snapTo(laut.x, laut.y, laut.z, berth.getYRot(), 0.0F);
			junk.ikat(situs, berth.blockPosition().asLong());
			junk.setKeadaan(KapalBajakLautEntity.KEADAAN_BERLAYAR);
			if (level.addFreshEntity(junk)) {
				DuniaBudget.track(TemaMekanisme.JENIS, junk);
				s.kapal = junk;
			}
		}
		AKTIF.put(situs, s);
		HidupApi.timpaJadwal(situs, "pertahanan", level.getGameTime() + 9000L);
		for (ServerPlayer p : near) {
			Sibuk.enemyTaken(p, "tema_pelabuhan_bajak");
			Msg.title(p, Component.translatable("kultivator.kota_tema.pelabuhan.bajak.judul").withColor(0xE05040),
					Component.translatable("kultivator.kota_tema.pelabuhan.bajak.sub").withColor(0xF0C8C0), 10, 60, 20);
			p.level().playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.9F, 0.8F);
			AdeganPayload.kirim(p, "tema_pelabuhan_bajak", s.kapal, laut, 0);
		}
		CeritaApi.kabar(level, situs, "tema_pelabuhan_bajak_datang");
		return true;
	}

	/** A mooring with no merchant junk tied up (the middle one first), or null. */
	private static @Nullable DuniaAnchorEntity tambatanBebas(MinecraftServer server, PelabuhanKota.Hidup kota) {
		DuniaAnchorEntity any = null;
		for (int n : new int[] {1, 0, 2}) {
			DuniaAnchorEntity a = kota.jangkar(PelabuhanAnchors.TAMBATAN, n);
			if (a == null) {
				continue;
			}
			if (any == null) {
				any = a;
			}
			if (!JadwalKapal.tertambat(server, kota.situs, n)) {
				return a;
			}
		}
		return any;
	}

	// ================================================================== the fight

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (server.getTickCount() % 40 == 13) {
			mulaiQuestBilaMalam(level);
		}
		if (AKTIF.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Iterator<Serbuan> it = AKTIF.values().iterator(); it.hasNext(); ) {
			Serbuan s = it.next();
			if (s.fase == Fase.SELESAI) {
				pergiKapal(level, s, now);
				if (s.kapal == null || s.kapal.isRemoved()) {
					it.remove();
				}
				continue;
			}
			try {
				langkah(level, s, now);
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Pirate raid failed", exception);
				it.remove();
			}
		}
	}

	private static void langkah(ServerLevel level, Serbuan s, long now) {
		long age = now - s.mulai;
		KapalBajakLautEntity junk = s.kapal;
		switch (s.fase) {
			case DATANG -> {
				double p = Math.min(1.0D, age / 600.0D);
				double ease = 1.0D - (1.0D - p) * (1.0D - p);
				if (junk != null && !junk.isRemoved()) {
					junk.pindah(s.laut.add(s.tambat.subtract(s.laut).scale(ease)), s.yaw);
				}
				if (p >= 1.0D) {
					s.fase = Fase.TITIAN;
					s.mulai = now;
					if (junk != null) {
						junk.setKeadaan(KapalBajakLautEntity.KEADAAN_TITIAN_TURUN);
					}
					level.playSound(null, BlockPos.containing(s.darat), SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.HOSTILE, 1.5F, 0.6F);
				}
			}
			case TITIAN -> {
				if (age >= 10L) {
					s.fase = Fase.GELOMBANG;
					s.mulai = now;
					gelombang(level, s);
				}
			}
			case GELOMBANG -> {
				pembela(level, s, now);
				if (s.hidup <= 0) {
					if (s.gelombang >= s.gelombangTotal) {
						selesai(level, s, true);
					} else {
						gelombang(level, s);
					}
				} else if (now - s.terakhirBela > 2400L || age > 9000L) {
					selesai(level, s, false);
				}
			}
			default -> {
			}
		}
	}

	/** Players near the quay are the defenders (for the rewards and the lost check). */
	private static void pembela(ServerLevel level, Serbuan s, long now) {
		for (ServerPlayer p : level.getPlayers(pp -> pp.position().distanceToSqr(s.darat) < JARAK_BELA * JARAK_BELA && pp.isAlive())) {
			s.pembela.add(p.getUUID());
			s.terakhirBela = now;
		}
	}

	/** The next wave down the gangway. */
	private static void gelombang(ServerLevel level, Serbuan s) {
		s.gelombang++;
		List<ServerPlayer> defenders = level.getPlayers(pp -> pp.position().distanceToSqr(s.darat) < JARAK_BELA * JARAK_BELA);
		int players = Math.max(1, defenders.size());
		int count = Math.min(MAKS_HIDUP, 6 + 3 * players);
		int[] tiers = new int[count];
		for (int i = 0; i < count; i++) {
			tiers[i] = i == 0 && s.gelombang == s.gelombangTotal ? 3 : i < 2 ? 2 : 1;
		}
		int realm = 0;
		for (ServerPlayer p : defenders) {
			realm = Math.max(realm, Cultivators.get(p).realm().ordinal());
		}
		final Serbuan raid = s;
		int spawned = RogueCultivators.spawnBand(level, s.darat, tiers, realm, RogueCultivators.ROLE_RAID, BlockPos.containing(s.darat), cleared -> raid.hidup = 0);
		s.hidup = spawned;
		if (spawned == 0) {
			// nobody could stand on the quay (peaceful, cap): the wave counts as beaten
			s.hidup = 0;
		}
		for (ServerPlayer p : defenders) {
			Msg.actionBar(p, Component.translatable("kultivator.kota_tema.pelabuhan.bajak.gelombang", s.gelombang, s.gelombangTotal)
					.withStyle(ChatFormatting.RED));
		}
		if (s.gelombang == 1) {
			KultivatorNpc guard = null;
			for (KultivatorNpc npc : level.getEntitiesOfClass(KultivatorNpc.class, new net.minecraft.world.phys.AABB(BlockPos.containing(s.darat)).inflate(40.0D))) {
				guard = npc;
				break;
			}
			if (guard != null && VoiceEvents.exists("tema.pelabuhan.pirates")) {
				Voice.say(guard, "tema.pelabuhan.pirates", null);
			}
		}
		if (s.questPemain != null && s.gelombang == Math.min(3, s.gelombangTotal) && s.heiSha == null && s.kapal != null) {
			// Kapten Hiu Hitam on his own deck: he watches, taunts and sails off with the junk (the duel comes in bab 6)
			KultivatorNpc hei = PelabuhanTokoh.panggil(level, s.situs, PelabuhanTokoh.HEI_SHA, s.kapal.blockPosition().above(2), s.yaw + 180.0F);
			if (hei != null) {
				hei.memory().putBoolean("hold", true);
				hei.memory().putBoolean(PelabuhanCerita.KEBAL, true);
				s.heiSha = hei;
				if (VoiceEvents.exists("tema.pelabuhan.black_shark")) {
					Voice.say(hei, "tema.pelabuhan.black_shark", null);
				}
				for (ServerPlayer p : defenders) {
					Msg.chat(p, Component.translatable("kultivator.kota_tema.pelabuhan.bajak.deng_kenal").withStyle(ChatFormatting.GRAY));
				}
			}
		}
	}

	private static void selesai(ServerLevel level, Serbuan s, boolean menang) {
		s.fase = Fase.SELESAI;
		s.mulai = level.getGameTime();
		HidupApi.timpaJadwal(s.situs, "", 0L);
		MinecraftServer server = level.getServer();
		PelabuhanKota.Hidup kota = PelabuhanKota.cari(s.situs);
		long town = kota != null ? kota.town : s.situs;
		CompoundTag state = PelabuhanKota.kota(server, s.situs);
		state.putLong("bajak_berikut", PelabuhanKota.hari(level) + 14L + Math.floorMod(PelabuhanKota.hash(s.situs, PelabuhanKota.hari(level), 380407L), 9L));
		if (s.questPemain != null) {
			state.remove("bajak_quest");
		}
		if (!menang) {
			state.putLong("seberang_tutup", PelabuhanKota.hari(level));
			JadwalKapal.batalkan(level, s.situs);
		}
		PelabuhanKota.ubah(server);
		for (UUID id : s.pembela) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			if (menang) {
				int rep = id.equals(s.questPemain) ? 8 : 4;
				KotaData.get(server).addReputation(town, id, rep);
				long pay = 25L * (Barang.pita(p) + 1L);
				EkonomiApi.beri(p, pay, "pelabuhan.bajak");
				rampasan(level, p);
				EkonomiApi.misiSelesai(p, "bajak_laut");
				Advancements.grant(p, "tempat/tema_pelabuhan_bajak");
				Msg.title(p, Component.translatable("kultivator.kota_tema.pelabuhan.bajak.menang").withColor(0xF0D070),
						Component.translatable("kultivator.kota_tema.pelabuhan.bajak.menang_sub", rep, pay).withColor(0xF8ECC8), 10, 60, 20);
			} else {
				Msg.chat(p, Component.translatable("kultivator.kota_tema.pelabuhan.bajak.kalah").withStyle(ChatFormatting.RED));
			}
			CeritaApi.peristiwa(p, "pelabuhan_bajak", menang ? "menang" : "kalah");
		}
		if (s.questPemain != null && !s.pembela.contains(s.questPemain)) {
			ServerPlayer p = server.getPlayerList().getPlayer(s.questPemain);
			if (p != null) {
				CeritaApi.peristiwa(p, "pelabuhan_bajak", menang ? "menang" : "kalah");
			}
		}
		CeritaApi.kabar(level, s.situs, menang ? "tema_pelabuhan_bajak_menang" : "tema_pelabuhan_bajak_kalah");
		if (s.heiSha != null && !s.heiSha.isRemoved()) {
			s.heiSha.vanish();
		}
		if (s.kapal != null) {
			s.kapal.setKeadaan(KapalBajakLautEntity.KEADAAN_MEMUDAR);
		}
	}

	/** The pirate junk backs off and fades out to sea, then goes. */
	private static void pergiKapal(ServerLevel level, Serbuan s, long now) {
		if (s.kapal == null || s.kapal.isRemoved()) {
			return;
		}
		long age = now - s.mulai;
		double p = Math.min(1.0D, age / 700.0D);
		s.kapal.pindah(s.tambat.add(s.laut.subtract(s.tambat).scale(p * p)), s.yaw);
		if (p >= 1.0D) {
			TemaMekanisme.lepas(s.kapal);
		}
	}

	/** One roll of the spoils table for a defender. */
	private static void rampasan(ServerLevel level, ServerPlayer player) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(RAMPASAN);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, player.position())
				.withOptionalParameter(LootContextParams.THIS_ENTITY, player)
				.create(LootContextParamSets.CHEST);
		for (ItemStack stack : new ArrayList<>(table.getRandomItems(params))) {
			PelabuhanKota.beri(player, stack);
		}
	}

	/** The chain's raid starts once it is dark in the player's harbour (from 14000). */
	private static void mulaiQuestBilaMalam(ServerLevel level) {
		long jam = PelabuhanKota.jam(level);
		if (jam < DARI || jam >= SAMPAI) {
			return;
		}
		MinecraftServer server = level.getServer();
		for (PelabuhanKota.Hidup kota : PelabuhanKota.semua()) {
			if (AKTIF.containsKey(kota.situs)) {
				continue;
			}
			CompoundTag q = PelabuhanKota.kota(server, kota.situs).getCompoundOrEmpty("bajak_quest");
			if (q.isEmpty()) {
				continue;
			}
			ServerPlayer player;
			try {
				player = server.getPlayerList().getPlayer(UUID.fromString(q.getStringOr("pemain", "")));
			} catch (IllegalArgumentException e) {
				player = null;
			}
			if (player == null || kota.jarak(player.position()) > PelabuhanKota.JANGKAU_KOTA) {
				continue;
			}
			mulaiSerbuan(level, kota.situs, player, q.getIntOr("gelombang", 3), true);
		}
	}

	/** Tests: the raid of a town now (forced; {@code uji tema acara pelabuhan bajak_laut}). */
	static boolean aktif(long situs) {
		return AKTIF.containsKey(situs);
	}

	private SerbuanBajakLaut() {
	}
}
