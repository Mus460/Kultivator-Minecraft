package dev.kultivator.sekte.diplomasi;

import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.Sikap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The life of the NPC sects (DESIGN 36.2.9), spread thin for a weak laptop: one sect's days are simulated every
 * {@code simulasiDetik} seconds (growth +2..+6 resources a day, a tier every {@code 150 × tier}, feuds between NPC sects
 * that cost the loser resources and sometimes a tier), one online hall per second gets its day (standing drift, weekly
 * ally gifts, vassal tribute, forced tribute, the war step of {@link Perang}, then 0-{@code peristiwaPerHari} events
 * weighted by the stance), and every five seconds the online players are checked against the planned gates (first
 * contact). Nothing here touches a block.
 */
public final class Simulasi {

	/** Days of growth caught up per turn (a world closed for a month does not stall the server). */
	public static final int CATCH_UP = 30;
	/** The morning: a hall's events come after this time of day. */
	public static final long MORNING = 1000L;
	/** First contact range around a planned gate (blocks, horizontal). */
	public static final double MEET_RANGE = 160.0D;

	private static int sectCursor;
	private static int hallCursor;

	static void clear() {
		sectCursor = 0;
		hallCursor = 0;
	}

	// ================================================================== founding

	/** Founds the sects of this world once (from the seeded plan), then keeps the plan the world was founded with. */
	static void ensureFounded(MinecraftServer server) {
		DiplomasiData data = DiplomasiData.get(server);
		if (!data.founded) {
			DiplomasiConfig config = Diplomasi.config();
			long seed = server.overworld().getSeed();
			GerbangPlan.Plan plan = GerbangPlan.build(seed, config.jumlahSekte, config.jarakMin);
			data.sects.clear();
			for (GerbangPlan.Entry entry : plan.entries()) {
				data.sects.add(SekteNpc.founded(entry));
			}
			data.planSeed = seed;
			data.planCount = config.jumlahSekte;
			data.planMinDist = config.jarakMin;
			data.founded = true;
			long day = Diplomasi.today(server);
			for (SekteNpc sect : data.sects) {
				sect.grownDay = day;
			}
			data.addNews(day, Component.translatable(GeneratorNama.KEY + "berita.berdiri", data.sects.size()));
			data.setDirty();
		}
		linkRivals(data);
		keepPlanOf(server);
	}

	/**
	 * T12: every sect without a Dunia rival group gets the group whose home region lies nearest its gate (a group not yet
	 * taken first, then the nearest of all), stored by group id. Older worlds linked by index kept their links; their
	 * sects past the group count (7-8) get one here.
	 */
	static void linkRivals(DiplomasiData data) {
		List<String> groups = DuniaApi.rivals().all();
		if (groups.isEmpty()) {
			return;
		}
		java.util.Set<String> taken = new java.util.HashSet<>();
		for (SekteNpc sect : data.sects) {
			if (!sect.rivalLink.isEmpty()) {
				taken.add(sect.rivalLink);
			}
		}
		boolean changed = false;
		for (SekteNpc sect : data.sects) {
			if (!sect.rivalLink.isEmpty()) {
				continue;
			}
			String free = nearestGroup(groups, sect, taken, true);
			String pick = free != null ? free : nearestGroup(groups, sect, taken, false);
			if (pick != null) {
				sect.rivalLink = pick;
				taken.add(pick);
				changed = true;
			}
		}
		if (changed) {
			data.setDirty();
		}
	}

	private static @Nullable String nearestGroup(List<String> groups, SekteNpc sect, java.util.Set<String> taken, boolean freeOnly) {
		String best = null;
		double bestD = Double.MAX_VALUE;
		for (String id : groups) {
			if (freeOnly && taken.contains(id)) {
				continue;
			}
			net.minecraft.core.BlockPos region = DuniaApi.rivals().region(id);
			double d = region == null ? Double.MAX_VALUE / 2 : Math.pow(region.getX() - sect.blockX, 2) + Math.pow(region.getZ() - sect.blockZ, 2);
			if (d < bestD) {
				bestD = d;
				best = id;
			}
		}
		return best;
	}

	/** The placement must keep planning the gates of the sects this world already has, whatever the config says now. */
	static void keepPlanOf(MinecraftServer server) {
		DiplomasiData data = DiplomasiData.get(server);
		if (data.founded) {
			GerbangPlan.configure(data.planCount, data.planMinDist, Diplomasi.config().strukturAktif);
		}
	}

	// ================================================================== tick

	static void tick(MinecraftServer server) {
		if (Diplomasi.server() != server) {
			return;
		}
		long t = server.getTickCount();
		try {
			if (t % (Diplomasi.config().simulasiDetik * 20L) == 0L) {
				growNext(server);
			}
			if (t % 20L == 5L) {
				dailyNext(server);
			}
			if (t % 20L == 10L) {
				Utusan.second(server);
				KetuaSekte.second(server);
				Perang.second(server);
			}
			if (t % 100L == 15L) {
				proximity(server);
			}
		} catch (RuntimeException exception) {
			Diplomasi.warn("simulation tick failed", exception);
		}
	}

	// ================================================================== the sects' own days

	private static void growNext(MinecraftServer server) {
		DiplomasiData data = DiplomasiData.get(server);
		if (data.sects.isEmpty()) {
			return;
		}
		if (sectCursor >= data.sects.size()) {
			sectCursor = 0;
		}
		SekteNpc sect = data.sects.get(sectCursor++);
		grow(server, data, sect, Diplomasi.today(server));
	}

	/** Simulates the sect's days up to {@code today} (at most {@link #CATCH_UP}). */
	static void grow(MinecraftServer server, DiplomasiData data, SekteNpc sect, long today) {
		if (sect.grownDay < 0L) {
			sect.grownDay = today;
		}
		RandomSource random = server.overworld().getRandom();
		int days = 0;
		while (sect.grownDay < today && days++ < CATCH_UP) {
			sect.grownDay++;
			int gain = 2 + random.nextInt(5);
			if (sect.tuan != null) {
				gain = Math.max(1, gain / 2);
			}
			sect.sumber += gain;
			feudDay(server, data, sect, random, sect.grownDay);
			if (sect.tier < SekteNpc.MAX_TIER && sect.sumber >= sect.nextTierCost()) {
				sect.sumber -= sect.nextTierCost();
				sect.tier++;
				data.addNews(sect.grownDay, Component.translatable(GeneratorNama.KEY + "berita.tumbuh", sect.name(), sect.tier));
				Diplomasi.fire(server, null, sect, AksiSekte.TUMBUH);
			}
		}
		if (sect.grownDay < today) {
			sect.grownDay = today;
		}
		data.setDirty();
	}

	/** One day of the sect's feud with another NPC sect (news only, DESIGN 36.2.9 "Perang NPC"). */
	private static void feudDay(MinecraftServer server, DiplomasiData data, SekteNpc sect, RandomSource random, long day) {
		SekteNpc enemy = data.sect(sect.musuhNpc);
		if (enemy == null) {
			sect.musuhNpc = -1;
			if (data.sects.size() > 1 && sect.agresi > 40 && random.nextInt(100) < 3 + sect.agresi / 25) {
				SekteNpc target = pickEnemy(data, sect, random);
				if (target != null) {
					sect.musuhNpc = target.index;
					sect.musuhSampai = day + 3 + random.nextInt(4);
					data.addNews(day, Component.translatable(GeneratorNama.KEY + "berita.perang_npc", sect.name(), target.name()));
					Diplomasi.fire(server, null, sect, AksiSekte.PERANG_NPC);
				}
			}
			return;
		}
		int mine = sect.strength() + random.nextInt(120);
		int theirs = enemy.strength() + random.nextInt(120);
		SekteNpc loser = mine >= theirs ? enemy : sect;
		SekteNpc winner = loser == sect ? enemy : sect;
		int loss = 4 + random.nextInt(7);
		loser.sumber -= loss;
		winner.sumber += 3;
		if (day >= sect.musuhSampai) {
			sect.musuhNpc = -1;
			data.addNews(day, Component.translatable(GeneratorNama.KEY + "berita.perang_npc_usai", winner.name(), loser.name()));
			if (loser.sumber < 0 && loser.tier > 1) {
				loser.tier--;
				loser.sumber = 50 * loser.tier;
				data.addNews(day, Component.translatable(GeneratorNama.KEY + "berita.turun", loser.name(), loser.tier));
			}
		}
		if (loser.sumber < -100) {
			loser.sumber = -100;
		}
	}

	private static @Nullable SekteNpc pickEnemy(DiplomasiData data, SekteNpc sect, RandomSource random) {
		List<SekteNpc> pool = new ArrayList<>();
		for (SekteNpc other : data.sects) {
			if (other == sect || other.musuhNpc >= 0) {
				continue;
			}
			pool.add(other);
			if (other.aliran != sect.aliran) {
				// Righteous and demonic paths clash more often.
				pool.add(other);
				pool.add(other);
			}
		}
		return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
	}

	// ================================================================== the halls' days

	private static void dailyNext(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		if (hallCursor >= players.size()) {
			hallCursor = 0;
		}
		ServerPlayer player = players.get(hallCursor++);
		UUID owner = player.getUUID();
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.active()) {
			return;
		}
		long clock = server.overworld().getOverworldClockTime();
		long today = Math.floorDiv(clock, 24000L);
		if (Math.floorMod(clock, 24000L) < MORNING) {
			return;
		}
		DiplomasiData data = DiplomasiData.get(server);
		if (data.hall(owner).getLongOr("hari", -1L) >= today) {
			return;
		}
		data.hall(owner).putLong("hari", today);
		data.setDirty();
		daily(server, data, owner, player, today);
	}

	/** One world day of a hall (also {@code uji perguruan diplomasi simulasi}). */
	static void daily(MinecraftServer server, DiplomasiData data, UUID owner, @Nullable ServerPlayer player, long today) {
		RandomSource random = server.overworld().getRandom();
		for (SekteNpc sect : data.sects) {
			Relasi r = data.relasi(owner, sect);
			Standing.drift(sect, r, today);
			weekly(server, owner, sect, r, today);
			Perang.daily(server, data, owner, sect, r, today, random);
		}
		Utusan.expire(server, owner);
		int level = Halls.level(owner);
		boolean early = early(owner);
		// T11: a young hall (below mulaiAulaLevel) gets at most one quiet event a day.
		int count = rollCount(early ? Math.min(1, Diplomasi.config().peristiwaPerHari) : Diplomasi.config().peristiwaPerHari, random);
		for (int i = 0; i < count; i++) {
			SekteNpc sect = pickSect(data, owner, random, level);
			if (sect != null) {
				event(server, data, owner, sect, data.relasi(owner, sect), random, today);
			}
		}
		data.setDirty();
	}

	private static void weekly(MinecraftServer server, UUID owner, SekteNpc sect, Relasi r, long today) {
		if (r.pakta == Relasi.PAKTA_SEKUTU && today >= r.sekutuHadiahDay + 7L) {
			r.sekutuHadiahDay = today;
			int pk = sect.tier * 20;
			Halls.addPool(owner, pk);
			r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.sekutu_hadiah", pk));
			Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.sekutu_hadiah", sect.name(), pk), ChatFormatting.GREEN);
			Diplomasi.fire(server, owner, sect, AksiSekte.HADIAH);
		}
		if (r.bawahan && today >= r.upetiMingguDay + 7L) {
			r.upetiMingguDay = today;
			int pk = sect.tier * 20;
			Halls.addPool(owner, pk);
			r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.upeti_bawahan", pk));
			Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.upeti_bawahan", sect.name(), pk), ChatFormatting.GOLD);
			Diplomasi.fire(server, owner, sect, AksiSekte.HADIAH);
		}
		if (r.upetiPaksa > 0 && today >= r.upetiPaksaDay) {
			r.upetiPaksa--;
			r.upetiPaksaDay = today + 7L;
			int pool = poolOf(server, owner);
			int pk = Math.min(300, pool / 20);
			if (pk > 0) {
				Halls.spendPool(owner, pk);
			}
			r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.upeti_paksa", pk, r.upetiPaksa));
			Diplomasi.log(server, owner, Component.translatable(GeneratorNama.KEY + "log.upeti_paksa", sect.name(), pk).withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** The PK in the hall pool (read only, from the hall's saved core). */
	static int poolOf(MinecraftServer server, UUID owner) {
		CompoundTag core = HallData.get(server).core(owner);
		return core == null ? 0 : Math.max(0, core.getIntOr("pool", 0));
	}

	private static int rollCount(int max, RandomSource random) {
		if (max <= 0) {
			return 0;
		}
		int roll = random.nextInt(100);
		int count = roll < 35 ? 0 : roll < 80 ? 1 : 2;
		if (max > 2 && roll >= 95) {
			count = 3;
		}
		return Math.min(max, count);
	}

	/** True while the hall is below {@code mulaiAulaLevel} (T11): no demand, raid, war, ambush or poaching yet. */
	static boolean early(UUID owner) {
		return Halls.level(owner) < Diplomasi.config().mulaiAulaLevel;
	}

	/**
	 * A sect for today's event: stronger feelings and a known gate weigh more; vassals stay quiet. Only sects already met
	 * or of tier at most {@code hallLevel + 2} come (T11: an unknown tier-9 sect does not knock on a new hall's door).
	 */
	private static @Nullable SekteNpc pickSect(DiplomasiData data, UUID owner, RandomSource random, int hallLevel) {
		int total = 0;
		int[] weights = new int[data.sects.size()];
		for (int i = 0; i < weights.length; i++) {
			SekteNpc sect = data.sects.get(i);
			Relasi r = data.relasi(owner, sect);
			if (!r.ditemui && !r.perang && sect.tier > hallLevel + 2) {
				continue;
			}
			int w = r.bawahan ? 1 : 2 + Math.abs(r.standing) / 20 + (r.perang ? 4 : 0) + (r.ditemui ? 1 : 0);
			weights[i] = w;
			total += w;
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0) {
				return data.sects.get(i);
			}
		}
		return null;
	}

	/** One NPC sect event for the hall, from the table of its stance (DESIGN 36.2.9). */
	static void event(MinecraftServer server, DiplomasiData data, UUID owner, SekteNpc sect, Relasi r, RandomSource random, long today) {
		Sikap sikap = r.sikap();
		boolean damai = r.pakta != Relasi.PAKTA_TIDAK || r.bawahan;
		boolean away = !SekteApi.misi().awaySouls(owner).isEmpty();
		boolean strong = sect.strength() > Diplomasi.kekuatanAula(server, owner);
		List<Peristiwa> table = new ArrayList<>();
		if (early(owner) && !r.perang) {
			// T11: a hall below mulaiAulaLevel meets only the quiet half of the table.
			add(table, Peristiwa.UTUSAN, 3);
			add(table, Peristiwa.HADIAH, sikap == Sikap.BERMUSUHAN || sikap == Sikap.DINGIN ? 0 : 1);
			add(table, Peristiwa.TANTANG, sikap == Sikap.BERMUSUHAN ? 0 : 1);
			add(table, Peristiwa.PAKTA, (sikap == Sikap.NETRAL || sikap == Sikap.RAMAH) && r.pakta == Relasi.PAKTA_TIDAK
					&& r.standing >= Standing.PAKTA_DAMAI_MIN ? 1 : 0);
			apply(server, data, owner, sect, r, table.get(random.nextInt(table.size())), random, today);
			return;
		}
		switch (sikap) {
			case PERANG -> {
				add(table, Peristiwa.MEREKRUT, 2);
				add(table, Peristiwa.SERGAP, away ? 2 : 0);
				add(table, Peristiwa.DAMAI, strong ? 0 : 2);
				add(table, Peristiwa.KETUA, 1);
			}
			case BERMUSUHAN -> {
				add(table, Peristiwa.SERANGAN, damai ? 0 : sect.iblis() || sect.agresi > 60 ? 3 : 1);
				add(table, Peristiwa.MEREKRUT, damai ? 0 : 2);
				add(table, Peristiwa.SERGAP, damai || !away ? 0 : 2);
				add(table, Peristiwa.PERANG, !damai && Diplomasi.config().perangAktif && r.standing < -70 && sect.agresi > 50 ? 2 : 0);
				add(table, Peristiwa.TUNTUT, !damai && strong ? 2 : 0);
				add(table, Peristiwa.UTUSAN, 1);
			}
			case DINGIN -> {
				add(table, Peristiwa.UTUSAN, 3);
				add(table, Peristiwa.TANTANG, 2);
				add(table, Peristiwa.MEREKRUT, damai ? 0 : 1);
				add(table, Peristiwa.SERGAP, damai || !away ? 0 : 1);
				add(table, Peristiwa.TUNTUT, !damai && strong && sect.agresi > 50 ? 1 : 0);
			}
			case NETRAL -> {
				add(table, Peristiwa.UTUSAN, 4);
				add(table, Peristiwa.TANTANG, 2);
				add(table, Peristiwa.HADIAH, 1);
				add(table, Peristiwa.PAKTA, r.pakta == Relasi.PAKTA_TIDAK && r.standing >= Standing.PAKTA_DAMAI_MIN ? 1 : 0);
			}
			case RAMAH -> {
				add(table, Peristiwa.HADIAH, 3);
				add(table, Peristiwa.UTUSAN, 3);
				add(table, Peristiwa.TANTANG, 2);
				add(table, Peristiwa.PAKTA, r.pakta == Relasi.PAKTA_TIDAK ? 2 : 0);
				add(table, Peristiwa.SEKUTU, r.pakta != Relasi.PAKTA_SEKUTU && !sect.iblis() && r.standing >= Standing.PAKTA_SEKUTU_MIN ? 1 : 0);
			}
			case SEKUTU -> {
				add(table, Peristiwa.HADIAH, 3);
				add(table, Peristiwa.UTUSAN, 2);
				add(table, Peristiwa.TANTANG, 2);
			}
		}
		if (table.isEmpty()) {
			return;
		}
		apply(server, data, owner, sect, r, table.get(random.nextInt(table.size())), random, today);
	}

	private static void add(List<Peristiwa> table, Peristiwa event, int weight) {
		for (int i = 0; i < weight; i++) {
			table.add(event);
		}
	}

	/** Carries out one event ({@code uji perguruan diplomasi aksi} calls it directly). */
	static void apply(MinecraftServer server, DiplomasiData data, UUID owner, SekteNpc sect, Relasi r, Peristiwa event, RandomSource random,
			long today) {
		switch (event) {
			case UTUSAN -> {
				Utusan.offer(server, owner, sect, Utusan.SALAM);
				Diplomasi.fire(server, owner, sect, AksiSekte.UTUSAN);
			}
			case HADIAH -> {
				int pk = sect.tier * 15;
				Halls.addPool(owner, pk);
				r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.hadiah_datang", pk));
				Diplomasi.log(server, owner, Component.translatable(GeneratorNama.KEY + "log.hadiah_datang", sect.name(), pk));
				Diplomasi.tell(server, owner, Component.translatable(GeneratorNama.KEY + "pesan.hadiah_datang", sect.name(), pk), ChatFormatting.GREEN);
				Diplomasi.fire(server, owner, sect, AksiSekte.HADIAH);
			}
			case TANTANG -> {
				// T10: one door only. The envoy's offer (answered in the Diplomasi screen or by talking to the envoy) holds
				// the challenge; TANTANGAN is no longer fired, so module T does not open a second prompt in chat.
				Utusan.offer(server, owner, sect, Utusan.TANTANG);
				Diplomasi.fire(server, owner, sect, AksiSekte.UTUSAN);
			}
			case KETUA -> Utusan.offer(server, owner, sect, Utusan.KETUA);
			case SERANGAN -> Perang.serangan(server, data, owner, sect, r, random, today);
			case SERGAP -> {
				r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.sergap"));
				Diplomasi.log(server, owner, Component.translatable(GeneratorNama.KEY + "log.sergap", sect.name()).withStyle(ChatFormatting.RED));
				Diplomasi.fire(server, owner, sect, AksiSekte.SERGAP_MISI);
			}
			case MEREKRUT -> {
				r.note(today, Component.translatable(GeneratorNama.KEY + "kronik.merekrut"));
				Diplomasi.log(server, owner, Component.translatable(GeneratorNama.KEY + "log.merekrut", sect.name()).withStyle(ChatFormatting.YELLOW));
				Diplomasi.fire(server, owner, sect, AksiSekte.MEREKRUT);
			}
			case PERANG -> Perang.nyatakan(server, owner, sect, false);
			case PAKTA -> Utusan.offer(server, owner, sect, Utusan.PAKTA);
			case SEKUTU -> Utusan.offer(server, owner, sect, Utusan.SEKUTU);
			case DAMAI -> Utusan.offer(server, owner, sect, Utusan.DAMAI);
			case TUNTUT -> Utusan.offer(server, owner, sect, Utusan.TUNTUT);
			case TUMBUH -> {
				if (sect.tier < SekteNpc.MAX_TIER) {
					sect.tier++;
					data.addNews(today, Component.translatable(GeneratorNama.KEY + "berita.tumbuh", sect.name(), sect.tier));
					Diplomasi.fire(server, null, sect, AksiSekte.TUMBUH);
				}
			}
			case PERANG_NPC -> {
				sect.musuhNpc = -1;
				sect.agresi = Math.max(sect.agresi, 41);
				SekteNpc target = pickEnemy(data, sect, random);
				if (target != null) {
					sect.musuhNpc = target.index;
					sect.musuhSampai = today + 3 + random.nextInt(4);
					data.addNews(today, Component.translatable(GeneratorNama.KEY + "berita.perang_npc", sect.name(), target.name()));
					Diplomasi.fire(server, null, sect, AksiSekte.PERANG_NPC);
				}
			}
		}
		data.setDirty();
	}

	/** The sect that likes the hall least (a defector's new home when P names none). */
	static @Nullable SekteNpc mostHostile(DiplomasiData data, UUID owner) {
		SekteNpc worst = null;
		int low = Integer.MAX_VALUE;
		for (SekteNpc sect : data.sects) {
			int s = data.relasi(owner, sect).standing;
			if (s < low) {
				low = s;
				worst = sect;
			}
		}
		return worst;
	}

	// ================================================================== first contact by walking to a gate

	private static void proximity(MinecraftServer server) {
		DiplomasiData data = DiplomasiData.get(server);
		if (data.sects.isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level() != server.overworld() || player.isSpectator()) {
				continue;
			}
			for (SekteNpc sect : data.sects) {
				double dx = player.getX() - sect.blockX;
				double dz = player.getZ() - sect.blockZ;
				if (dx * dx + dz * dz <= MEET_RANGE * MEET_RANGE) {
					Relasi r = data.peek(player.getUUID(), sect.id);
					if (r == null || !r.ditemui) {
						Diplomasi.meet(server, player.getUUID(), sect);
						player.sendSystemMessage(Component.translatable(GeneratorNama.KEY + "pesan.wilayah", sect.name(),
								Component.translatable(data.relasi(player.getUUID(), sect).sikap().key())).withStyle(ChatFormatting.GOLD));
					}
				}
			}
		}
	}

	/** Raids during a war use this to see whether a raid is already planned (for the screen). */
	static long nextRaid(UUID owner) {
		return SectRaids.nextRaidDay(owner);
	}

	private Simulasi() {
	}
}
