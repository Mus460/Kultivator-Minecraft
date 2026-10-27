package dev.kultivator.sekte.diplomasi;

import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.epic.EpicHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Raids and wars of the NPC sects (DESIGN 36.2.9). Raids go through the existing {@code musuh.SectRaids} schedule and
 * its numbers (the {@link SectRaids.Director} only names the raiders after the sect, pulls a war raid forward, adds the
 * allies' defence to a raid settled as data and hears each result). A war brings a raid every 3-5 days; below hall level
 * 3, where raids never come, the sect fights border skirmishes settled as data instead. The war ends with peace, with the
 * hall's defeat after three sacked raids (forced tribute for three weeks, never blocks or disciples), or with the defeat
 * of the enemy sect master ({@link KetuaSekte}): the sect drops two tiers and becomes the hall's vassal (standing 30,
 * weekly tribute). Allies send up to two guards to a live raid; a guard duels one raider at a time under the duel rules
 * (stopping at 10%) and the beaten raider flees.
 */
public final class Perang {

	/** A beaten war makes the hall pay this many weekly tributes. */
	public static final int UPETI_PAKSA_KALI = 3;
	/** Sacked raids that lose a war. */
	public static final int KALAH_BATAS = 3;
	public static final int BEAT_PERANG = EpicIds.EXTRA_SEKTE_FIRST + 80;
	public static final int BEAT_SEKUTU = EpicIds.EXTRA_SEKTE_FIRST + 81;
	public static final int BEAT_MENANG = EpicIds.EXTRA_SEKTE_FIRST + 82;
	/** Hall tag key: the sect behind the owner's next raid. */
	static final String PENYERBU = "penyerbu";

	private static final Map<UUID, List<DiscipleEntity>> GUARDS = new HashMap<>();

	static void init() {
		SectRaids.setDirector(new SectRaids.Director() {
			@Override
			public int pickSect(UUID owner) {
				return Perang.pickSect(owner);
			}

			@Override
			public @Nullable Component name(UUID owner, int sect) {
				return Perang.raiderName(owner, sect);
			}

			@Override
			public int allyDefense(UUID owner) {
				return Perang.allyDefense(owner);
			}

			@Override
			public void onResult(UUID owner, int result) {
				Perang.onResult(owner, result);
			}
		});
		SectRaids.addListener(new SectRaids.RaidListener() {
			@Override
			public void onStart(UUID owner) {
				spawnGuards(owner);
			}

			@Override
			public void onEnd(UUID owner, boolean repelled) {
				dismissGuards(owner);
			}
		});
	}

	static void clear() {
		for (List<DiscipleEntity> list : GUARDS.values()) {
			for (DiscipleEntity guard : list) {
				guard.discard();
			}
		}
		GUARDS.clear();
	}

	// ================================================================== declaring, peace, victory, defeat

	/** War between the hall and the sect ({@code byPlayer}: the player declared it). */
	static void nyatakan(MinecraftServer server, UUID owner, SekteNpc sect, boolean byPlayer) {
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		if (r.perang || !Diplomasi.config().perangAktif) {
			return;
		}
		long today = Diplomasi.today(server);
		r.perang = true;
		r.perangDay = today;
		r.perangKalah = 0;
		r.perangMenang = 0;
		r.pakta = Relasi.PAKTA_TIDAK;
		r.bawahan = false;
		r.standing = Math.min(r.standing, -70);
		r.note(today, Component.translatable(GeneratorNama.KEY + (byPlayer ? "kronik.perang_kita" : "kronik.perang_mereka")));
		Component line = Component.translatable(GeneratorNama.KEY + (byPlayer ? "log.perang_kita" : "log.perang_mereka"), sect.name());
		Diplomasi.log(server, owner, line.copy().withStyle(ChatFormatting.DARK_RED));
		Diplomasi.tell(server, owner, line, ChatFormatting.DARK_RED);
		jadwal(server, data, owner, sect, r, today, server.overworld().getRandom());
		Diplomasi.fire(server, owner, sect, AksiSekte.PERANG);
		scene(server, owner, BEAT_PERANG, sect, null);
		data.setDirty();
	}

	/** Peace (offered by them and accepted, or by the player and accepted). */
	static void damai(MinecraftServer server, UUID owner, SekteNpc sect) {
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		if (!r.perang) {
			return;
		}
		long today = Diplomasi.today(server);
		r.perang = false;
		r.standing = Math.max(r.standing, -20);
		clearRaider(data, owner, sect);
		r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.damai"));
		Component line = Component.translatable(GeneratorNama.KEY + "log.damai", sect.name());
		Diplomasi.log(server, owner, line);
		Diplomasi.tell(server, owner, line, ChatFormatting.GREEN);
		Diplomasi.fire(server, owner, sect, AksiSekte.DAMAI);
		data.setDirty();
	}

	/** The enemy sect master is beaten (at its gate or at the hall): the sect falls two tiers and becomes a vassal. */
	static void menang(MinecraftServer server, UUID owner, SekteNpc sect, @Nullable ServerPlayer player) {
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		long today = Diplomasi.today(server);
		int before = sect.tier;
		sect.tier = Math.max(1, sect.tier - 2);
		sect.sumber = 0;
		sect.tuan = owner;
		sect.ketuaKalah++;
		r.perang = false;
		r.bawahan = true;
		r.pakta = Relasi.PAKTA_DAMAI;
		r.standing = 30;
		r.upetiMingguDay = today;
		clearRaider(data, owner, sect);
		int pk = before * 100;
		Halls.addPool(owner, pk);
		Halls.addRenown(owner, before * 50, GeneratorNama.KEY + "renown.menang");
		r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.menang", pk));
		data.addNews(today, Component.translatable(GeneratorNama.KEY + "berita.takluk", sect.name(), sect.tier));
		Component line = Component.translatable(GeneratorNama.KEY + "log.menang", sect.name(), pk);
		Diplomasi.log(server, owner, line.copy().withStyle(ChatFormatting.GOLD));
		Diplomasi.tell(server, owner, line, ChatFormatting.GOLD);
		Diplomasi.fire(server, owner, sect, AksiSekte.DAMAI);
		ServerPlayer online = player != null ? player : server.getPlayerList().getPlayer(owner);
		if (online != null) {
			Advancements.grant(online, SekteIds.ADVANCEMENT_FOLDER + "diplomasi_menang");
		}
		scene(server, owner, BEAT_MENANG, sect, null);
		data.setDirty();
	}

	/** The hall lost the war (three sacked raids): forced tribute, never blocks, renown or disciples. */
	static void kalah(MinecraftServer server, UUID owner, SekteNpc sect) {
		DiplomasiData data = DiplomasiData.get(server);
		Relasi r = data.relasi(owner, sect);
		long today = Diplomasi.today(server);
		r.perang = false;
		r.standing = -40;
		r.upetiPaksa = UPETI_PAKSA_KALI;
		r.upetiPaksaDay = today + 7L;
		clearRaider(data, owner, sect);
		int pk = Math.min(500, Simulasi.poolOf(server, owner) / 10);
		if (pk > 0) {
			Halls.spendPool(owner, pk);
		}
		sect.sumber += 40;
		r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.kalah", pk));
		Component line = Component.translatable(GeneratorNama.KEY + "log.kalah", sect.name(), pk, UPETI_PAKSA_KALI);
		Diplomasi.log(server, owner, line.copy().withStyle(ChatFormatting.DARK_RED));
		Diplomasi.tell(server, owner, line, ChatFormatting.DARK_RED);
		Diplomasi.fire(server, owner, sect, AksiSekte.DAMAI);
		data.setDirty();
	}

	// ================================================================== the war's days

	static void daily(MinecraftServer server, DiplomasiData data, UUID owner, SekteNpc sect, Relasi r, long today, RandomSource random) {
		if (!r.perang) {
			return;
		}
		if (!Diplomasi.config().perangAktif) {
			damai(server, owner, sect);
			return;
		}
		if (Halls.level(owner) < SectRaids.MIN_HALL_LEVEL) {
			if (today >= r.perangSeranganDay) {
				pertempuranData(server, data, owner, sect, r, random, today);
				r.perangSeranganDay = today + 3 + random.nextInt(3);
			}
		} else if (today > r.perangSeranganDay + 1L && SectRaids.nextRaidDay(owner) > today + 5L) {
			// The asked raid did not come (raids off, a raid was pending): ask again.
			jadwal(server, data, owner, sect, r, today, random);
		}
		long days = today - r.perangDay;
		if (days >= 6 && days % 4 == 2 && !sect.gateBorn()) {
			// No mountain gate in this world: the sect master comes to the hall to settle it in person.
			Utusan.offer(server, owner, sect, Utusan.KETUA);
		}
	}

	/** Asks the raid schedule for the next war raid in 3-5 days. */
	private static void jadwal(MinecraftServer server, DiplomasiData data, UUID owner, SekteNpc sect, Relasi r, long today, RandomSource random) {
		int days = 3 + random.nextInt(3);
		r.perangSeranganDay = today + days;
		if (Halls.level(owner) >= SectRaids.MIN_HALL_LEVEL && SectRaids.callRaid(server, owner, days)) {
			// S14: the raider is named only when the raid schedule really took the raid.
			data.hall(owner).putString(PENYERBU, sect.id);
		}
		data.setDirty();
	}

	/** A raid by a hostile sect outside a war (the event {@code SERANGAN}). */
	static void serangan(MinecraftServer server, DiplomasiData data, UUID owner, SekteNpc sect, Relasi r, RandomSource random, long today) {
		if (r.pakta != Relasi.PAKTA_TIDAK || r.bawahan) {
			return;
		}
		if (Halls.level(owner) < SectRaids.MIN_HALL_LEVEL) {
			pertempuranData(server, data, owner, sect, r, random, today);
			return;
		}
		if (!SectRaids.callRaid(server, owner, 2 + random.nextInt(2))) {
			// S14: the raid schedule refused (raids off, a raid already planned, pending or live): no false "raid ready";
			// the clash is settled at the border instead.
			pertempuranData(server, data, owner, sect, r, random, today);
			return;
		}
		data.hall(owner).putString(PENYERBU, sect.id);
		r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.serangan_siap"));
		Component line = Component.translatable(GeneratorNama.KEY + "log.serangan_siap", sect.name());
		Diplomasi.log(server, owner, line.copy().withStyle(ChatFormatting.RED));
		Diplomasi.tell(server, owner, line, ChatFormatting.RED);
		Diplomasi.fire(server, owner, sect, AksiSekte.SERANGAN);
		data.setDirty();
	}

	/** A border skirmish settled as data (halls below level 3): PK won or lost, a war tally. */
	static void pertempuranData(MinecraftServer server, DiplomasiData data, UUID owner, SekteNpc sect, Relasi r, RandomSource random, long today) {
		int ours = Diplomasi.kekuatanAula(server, owner) + random.nextInt(150);
		int theirs = (int) (sect.strength() * 0.6D) + random.nextInt(150);
		Component line;
		if (ours >= theirs) {
			int pk = sect.tier * 10;
			Halls.addPool(owner, pk);
			if (r.perang) {
				r.perangMenang++;
			}
			r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.tapal_menang", pk));
			line = Component.translatable(GeneratorNama.KEY + "log.tapal_menang", sect.name(), pk).withStyle(ChatFormatting.GOLD);
		} else {
			int pk = Math.min(150, Simulasi.poolOf(server, owner) / 20);
			if (pk > 0) {
				Halls.spendPool(owner, pk);
			}
			if (r.perang) {
				r.perangKalah++;
			}
			r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.tapal_kalah", pk));
			line = Component.translatable(GeneratorNama.KEY + "log.tapal_kalah", sect.name(), pk).withStyle(ChatFormatting.RED);
		}
		Diplomasi.log(server, owner, line);
		Diplomasi.tell(server, owner, line, ChatFormatting.WHITE);
		Diplomasi.fire(server, owner, sect, AksiSekte.SERANGAN);
		if (r.perang && r.perangKalah >= KALAH_BATAS) {
			kalah(server, owner, sect);
		}
		data.setDirty();
	}

	// ================================================================== the raid director

	private static @Nullable SekteNpc raider(DiplomasiData data, UUID owner) {
		String id = data.hall(owner).getStringOr(PENYERBU, "");
		return id.isEmpty() ? null : data.sect(id);
	}

	private static void clearRaider(DiplomasiData data, UUID owner, SekteNpc sect) {
		if (sect.id.equals(data.hall(owner).getStringOr(PENYERBU, ""))) {
			data.hall(owner).remove(PENYERBU);
		}
	}

	static int pickSect(UUID owner) {
		MinecraftServer server = Diplomasi.server();
		if (server == null) {
			return -1;
		}
		DiplomasiData data = DiplomasiData.get(server);
		SekteNpc sect = raider(data, owner);
		if (sect == null) {
			// An ordinary raid: a hostile demonic sect claims it more often than not.
			List<SekteNpc> hostile = new ArrayList<>();
			for (SekteNpc s : data.sects()) {
				Relasi r = data.peek(owner, s.id);
				int standing = r == null ? Standing.awal(s) : r.standing;
				boolean bound = r != null && (r.pakta != Relasi.PAKTA_TIDAK || r.bawahan);
				if (s.iblis() && standing < Standing.PERANG_MAX && !bound) {
					hostile.add(s);
				}
			}
			RandomSource random = server.overworld().getRandom();
			if (hostile.isEmpty() || random.nextInt(100) >= 60) {
				return -1;
			}
			sect = hostile.get(random.nextInt(hostile.size()));
			data.hall(owner).putString(PENYERBU, sect.id);
			data.setDirty();
		}
		Relasi r = data.relasi(owner, sect);
		r.note(Diplomasi.today(server), Component.translatable(GeneratorNama.KEY + "kronik.pertanda"));
		Diplomasi.fire(server, owner, sect, AksiSekte.SERANGAN);
		return sect.raiderSect;
	}

	static @Nullable Component raiderName(UUID owner, int raidSect) {
		MinecraftServer server = Diplomasi.server();
		if (server == null) {
			return null;
		}
		SekteNpc sect = raider(DiplomasiData.get(server), owner);
		return sect != null && Math.floorMod(sect.raiderSect, RogueCultivatorEntity.SECTS) == Math.floorMod(raidSect, RogueCultivatorEntity.SECTS)
				? sect.name() : null;
	}

	static int allyDefense(UUID owner) {
		MinecraftServer server = Diplomasi.server();
		if (server == null || Diplomasi.config().penjagaSekutu <= 0) {
			return 0;
		}
		DiplomasiData data = DiplomasiData.get(server);
		String induk = dev.kultivator.sekte.gabung.politik.Cabang.induk(server, owner);
		int score = 0;
		for (SekteNpc sect : data.sects()) {
			Relasi r = data.peek(owner, sect.id);
			if (r != null && r.pakta == Relasi.PAKTA_SEKUTU) {
				// D-41AD (41.8.2): the mother sect of a branch hall stands twice as strong behind it
				score += sect.tier * (sect.id.equals(induk) ? 8 : 4);
			}
		}
		return score;
	}

	static void onResult(UUID owner, int result) {
		MinecraftServer server = Diplomasi.server();
		if (server == null) {
			return;
		}
		DiplomasiData data = DiplomasiData.get(server);
		SekteNpc sect = raider(data, owner);
		if (sect == null) {
			return;
		}
		data.hall(owner).remove(PENYERBU);
		Relasi r = data.relasi(owner, sect);
		long today = Diplomasi.today(server);
		boolean sacked = result == SectRaids.RES_SACKED || result == SectRaids.RES_DATA_SACKED;
		boolean repelled = result == SectRaids.RES_REPELLED || result == SectRaids.RES_DATA_REPELLED;
		r.note(today, Component.translatable(GeneratorNama.KEY + (sacked ? "kronik.serbuan_jarah" : repelled ? "kronik.serbuan_tolak" : "kronik.serbuan_tahan")));
		if (repelled) {
			// D-41AD (41.8.4): a hall that holds against the enemy of its member's sect adds +10 to the sect's war score
			String mother = dev.kultivator.sekte.gabung.api.GabungApi.anggota(owner).sekteAktif();
			dev.kultivator.sekte.diplomasi.DiplomasiGabung.Perang war = mother == null ? null
					: dev.kultivator.sekte.diplomasi.DiplomasiGabung.perang(server, mother);
			if (war != null && war.lawan().equals(sect.id)) {
				dev.kultivator.sekte.diplomasi.DiplomasiGabung.perangSkor(server, mother, 10, "aula_tahan");
			}
		}
		if (r.perang) {
			if (sacked) {
				r.perangKalah++;
			} else if (repelled) {
				r.perangMenang++;
				sect.sumber -= 10 * sect.tier;
			}
			if (r.perangKalah >= KALAH_BATAS) {
				kalah(server, owner, sect);
			} else {
				jadwal(server, data, owner, sect, r, today, server.overworld().getRandom());
			}
		} else {
			Standing.adjust(server, owner, sect, -20, "serangan");
		}
		data.setDirty();
	}

	// ================================================================== allied guards in a live raid

	private static void spawnGuards(UUID owner) {
		MinecraftServer server = Diplomasi.server();
		Halls.HallView hall = Halls.hallOf(owner);
		int max = Diplomasi.config().penjagaSekutu;
		if (server == null || hall == null || max <= 0) {
			return;
		}
		// D-41AD (41.8.2): a branch hall also gets its mother sect's guards (2 -> 4), mother's guards come first
		String induk = dev.kultivator.sekte.gabung.politik.Cabang.induk(server, owner);
		if (induk != null) {
			max += 2;
		}
		ServerLevel level = server.getLevel(hall.dim());
		if (level == null) {
			return;
		}
		DiplomasiData data = DiplomasiData.get(server);
		List<DiscipleEntity> guards = new ArrayList<>();
		List<SekteNpc> order = new ArrayList<>(data.sects());
		if (induk != null) {
			order.sort(java.util.Comparator.comparing((SekteNpc x) -> !x.id.equals(induk)));
		}
		for (SekteNpc sect : order) {
			Relasi r = data.peek(owner, sect.id);
			if (r == null || r.pakta != Relasi.PAKTA_SEKUTU) {
				continue;
			}
			for (int i = 0; i < (sect.id.equals(induk) ? 4 : 2) && guards.size() < max; i++) {
				double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
				BlockPos at = Tubuh.ground(level, hall.pos().offset((int) Math.round(Math.cos(angle) * 4.0D), 0, (int) Math.round(Math.sin(angle) * 4.0D)));
				DiscipleEntity guard = Tubuh.spawn(level, Vec3.atBottomCenterOf(at), sect, sect.memberRankStep(i) + 2, 40 + i,
						Component.translatable(GeneratorNama.KEY + "penjaga.nama", GeneratorNama.member(sect.lambang, 40 + i), sect.name()), at, true);
				if (guard != null) {
					guards.add(guard);
				}
			}
			if (guards.size() >= max) {
				break;
			}
		}
		if (!guards.isEmpty()) {
			GUARDS.put(owner, guards);
			Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.penjaga", guards.size()), ChatFormatting.AQUA);
		}
	}

	private static void dismissGuards(UUID owner) {
		List<DiscipleEntity> guards = GUARDS.remove(owner);
		if (guards == null) {
			return;
		}
		for (DiscipleEntity guard : guards) {
			Tubuh.vanish(guard);
		}
	}

	/** Once a second: each idle guard takes on the nearest raider that is not already in a duel. */
	static void second(MinecraftServer server) {
		if (GUARDS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, List<DiscipleEntity>> entry : List.copyOf(GUARDS.entrySet())) {
			UUID owner = entry.getKey();
			if (!SectRaids.active(owner)) {
				dismissGuards(owner);
				continue;
			}
			entry.getValue().removeIf(guard -> guard.isRemoved() || !guard.isAlive());
			List<RogueCultivatorEntity> raiders = SectRaids.liveRaiders(owner);
			for (DiscipleEntity guard : entry.getValue()) {
				if (DuelRules.inDuel(guard)) {
					continue;
				}
				RogueCultivatorEntity best = null;
				double bestD = 32.0D * 32.0D;
				for (RogueCultivatorEntity raider : raiders) {
					if (!raider.isAlive() || DuelRules.inDuel(raider) || raider.level() != guard.level()) {
						continue;
					}
					double d = raider.distanceToSqr(guard);
					if (d < bestD) {
						bestD = d;
						best = raider;
					}
				}
				if (best == null) {
					continue;
				}
				RogueCultivatorEntity foe = best;
				if (DuelRules.begin(guard, foe, 20 * 60, (winner, loser, reason) -> {
					if (loser == foe && reason == DuelRules.Reason.DEFEAT) {
						foe.vanish(true);
					}
				})) {
					guard.setTarget(foe);
				}
			}
		}
	}

	// ================================================================== scenes

	/** A 7b scene for the owner (short, skippable, Penuh/Singkat/Mati and danger guards apply on the client). */
	static void scene(MinecraftServer server, UUID owner, int beat, SekteNpc sect, @Nullable net.minecraft.world.entity.Entity subject) {
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player == null) {
			return;
		}
		Vec3 pos = subject != null ? subject.position() : player.position();
		EpicHooks.extra(player.level(), beat, player, subject, pos, sect.tier, sect.index);
	}

	/** For the screen: the hall tag key of the next raider (read only). */
	static String raiderId(DiplomasiData data, UUID owner) {
		CompoundTag hall = data.hall(owner);
		return hall.getStringOr(PENYERBU, "");
	}

	private Perang() {
	}
}
