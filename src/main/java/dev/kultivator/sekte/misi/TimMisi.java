package dev.kultivator.sekte.misi;

import dev.kultivator.desa.Vassals;
import dev.kultivator.desa.VillageView;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.event.Expeditions;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.sekte.mandiri.jalan.Jelmaan;
import dev.kultivator.sosok.Gaya;
import dev.kultivator.sosok.GerakId;
import dev.kultivator.sosok.PropId;
import dev.kultivator.util.Msg;
import dev.kultivator.sekte.SekteSuara;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The story board and the teams (DESIGN 36.2.7): the day's offers, who may go, the departure through
 * {@code Halls.storeAway} (reason {@link Misi#REASON}, contract C36.X3), the stage news on the way and the two recalls
 * ("Tarik Pulang" with the wounded, a quarter day slower, or without them, which counts as abandoning them:
 * {@code DIKORBANKAN}).
 */
final class TimMisi {

	/** Places of the stories ({@code kultivator.sekte.misi.tempat.<n>}). */
	static final int PLACES = 16;
	/** Generic foes of the fights ({@code kultivator.sekte.misi.lawan.<n>}). */
	static final int FOES = 12;
	/** Disciples farther than this from the stele cannot be sent. */
	static final double NEAR = 64.0D;
	/** Lelang Kota costs this many hall PK per mission tier up front. */
	static final int AUCTION_PK_PER_TIER = 30;
	/** A recall with the wounded takes a quarter day (they are carried); without them a few seconds. */
	static final long RECALL_WITH_WOUNDED = 6000L;
	static final long RECALL_FAST = 100L;

	// ================================================================== board

	static int offersPerDay(int hallLevel) {
		int[] per = MisiConfig.get().papanPerHari;
		return per[hallLevel <= 3 ? 0 : hallLevel <= 6 ? 1 : 2];
	}

	/** T* = 1 + the strongest realm among the loaded disciples and the members away on story missions (1-9). */
	static int strongestTier(UUID owner) {
		int best = 0;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			best = Math.max(best, disciple.data().realm().ordinal());
		}
		for (CompoundTag mission : MisiData.active(owner)) {
			for (CompoundTag member : MisiData.team(mission)) {
				best = Math.max(best, member.getIntOr("realm", 0));
			}
		}
		return Math.max(1, Math.min(9, 1 + best));
	}

	/** The offers of today, rolled once per in-game day (missed days never stack). */
	static ListTag board(MinecraftServer server, UUID owner) {
		CompoundTag section = MisiData.section(owner);
		long day = Ingatan.today(server);
		if (section.getLongOr("papan_hari", Long.MIN_VALUE) != day || !section.contains("papan")) {
			roll(server, owner, day, server.overworld().getRandom());
		}
		return MisiData.list(section, "papan");
	}

	static void roll(MinecraftServer server, UUID owner, long day, RandomSource random) {
		CompoundTag section = MisiData.section(owner);
		int top = strongestTier(owner);
		List<TemplatMisi> open = new ArrayList<>();
		for (TemplatMisi tpl : TemplatMisi.values()) {
			if (available(owner, tpl, top)) {
				open.add(tpl);
			}
		}
		ListTag board = new ListTag();
		int count = Math.min(open.size(), offersPerDay(Math.max(1, Halls.level(owner))));
		for (int i = 0; i < count; i++) {
			TemplatMisi tpl = open.remove(random.nextInt(open.size()));
			CompoundTag offer = new CompoundTag();
			offer.putString("tpl", tpl.id());
			int tier = Math.max(1, Math.min(9, top - 1 + random.nextInt(3)));
			offer.putInt("tier", Math.max(tpl.minTier(), Math.min(tpl.maxTier(), tier)));
			offer.putInt("place", random.nextInt(PLACES));
			offer.putInt("foe", random.nextInt(FOES));
			offer.putBoolean("taken", false);
			decorate(server, owner, tpl, offer, random);
			board.add(offer);
		}
		section.put("papan", board);
		section.putLong("papan_hari", day);
		MisiData.dirty();
	}

	private static boolean available(UUID owner, TemplatMisi tpl, int top) {
		if (tpl.minTier() > top + 1) {
			return false;
		}
		if (tpl.has(TemplatMisi.Flags.SECT) && SekteApi.diplomasi().sects().isEmpty() && DuniaApi.rivals().all().isEmpty()) {
			return false;
		}
		if (tpl.has(TemplatMisi.Flags.SEARCH) && MisiData.searchable(owner).isEmpty()) {
			return false;
		}
		return !tpl.has(TemplatMisi.Flags.AUCTION) || Halls.level(owner) >= 2;
	}

	/** Names the offer's NPC sect / rival, village or missing disciple. */
	private static void decorate(MinecraftServer server, UUID owner, TemplatMisi tpl, CompoundTag offer, RandomSource random) {
		if (tpl.has(TemplatMisi.Flags.SECT) || tpl.has(TemplatMisi.Flags.FOE_SECT) || random.nextDouble() < 0.25D) {
			pickSect(owner, tpl, offer, random);
		}
		if (tpl.has(TemplatMisi.Flags.VILLAGE)) {
			List<VillageView> villages = Vassals.views(owner);
			if (!villages.isEmpty()) {
				VillageView village = villages.get(random.nextInt(villages.size()));
				offer.putString("village", village.name());
			}
		}
		if (tpl.has(TemplatMisi.Flags.SEARCH)) {
			List<CompoundTag> wanted = MisiData.searchable(owner);
			if (!wanted.isEmpty()) {
				CompoundTag target = wanted.get(random.nextInt(wanted.size()));
				offer.putString("target", target.getStringOr("soul", ""));
				MisiData.putName(offer, "target_name", MisiData.name(target, "name"));
				offer.putString("target_how", target.getStringOr("how", ""));
			}
		}
	}

	/**
	 * An NPC sect of module D (an envoy prefers a friendly or neutral one, an infiltration a hostile one), else a Dunia
	 * rival group (name only, no standing). Nothing when neither exists.
	 */
	private static void pickSect(UUID owner, TemplatMisi tpl, CompoundTag offer, RandomSource random) {
		List<String> sects = SekteApi.diplomasi().sects();
		if (!sects.isEmpty()) {
			List<String> preferred = new ArrayList<>();
			for (String sect : sects) {
				Sikap stance = SekteApi.diplomasi().stance(owner, sect);
				boolean hostile = stance == Sikap.PERANG || stance == Sikap.BERMUSUHAN || stance == Sikap.DINGIN;
				if (tpl.has(TemplatMisi.Flags.FOE_SECT) == hostile) {
					preferred.add(sect);
				}
			}
			List<String> from = preferred.isEmpty() ? sects : preferred;
			String sect = from.get(random.nextInt(from.size()));
			Component name = SekteApi.diplomasi().name(sect);
			offer.putString("sect", sect);
			MisiData.putName(offer, "sect_name", name != null ? name : Component.literal(sect));
			return;
		}
		List<String> rivals = DuniaApi.rivals().all();
		if (!rivals.isEmpty()) {
			String rival = rivals.get(random.nextInt(rivals.size()));
			Component name = DuniaApi.rivals().name(rival);
			MisiData.putName(offer, "sect_name", name != null ? name : Component.literal(rival));
		}
	}

	// ================================================================== departure

	/** True when the disciple may be chosen at all (free, near, ranked, willing, not already away). */
	static @Nullable String blocker(UUID owner, DiscipleEntity disciple) {
		if (!disciple.isAlive() || disciple.isRemoved() || disciple.role() != DiscipleEntity.Role.DISCIPLE || disciple.isReleasing()
				|| disciple.data().breakingThrough() || DuelRules.inDuel(disciple)) {
			return "kultivator.sekte.misi.cek.sibuk";
		}
		int act = disciple.activity();
		if (act > DiscipleEntity.ACTIVITY_FARM && act != SekteIds.ACT_MUSTER) {
			return "kultivator.sekte.misi.cek.tugas";
		}
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !disciple.level().dimension().equals(hall.dim())
				|| disciple.distanceToSqr(Vec3.atCenterOf(hall.pos())) > NEAR * NEAR) {
			return "kultivator.sekte.misi.cek.jauh";
		}
		if (SectRanks.rank(disciple.data()) < SectRanks.LUAR) {
			return "kultivator.sekte.misi.cek.tercatat";
		}
		if (SekteApi.hati().defiant(disciple.data())) {
			return "kultivator.sekte.misi.cek.membangkang";
		}
		if (MisiData.missionOf(owner, disciple.soulId()) != null) {
			return "kultivator.sekte.misi.cek.pergi";
		}
		return null;
	}

	/** The highest tier a team whose best sect rank is {@code rank} may take (Expeditions' rule: Luar 2, Dalam 5, Inti+ 9). */
	static int maxTier(int rank) {
		return rank >= SectRanks.INTI ? 9 : rank == SectRanks.DALAM ? 5 : 2;
	}

	static @Nullable DiscipleEntity find(UUID owner, UUID soul) {
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.soulId().equals(soul)) {
				return disciple;
			}
		}
		return null;
	}

	/**
	 * Sends a team on board offer {@code index}. {@code souls} the members (the first leads). Tells the player why not and
	 * returns false when it cannot go.
	 */
	static boolean send(ServerPlayer player, int index, TemplatMisi.Pendekatan stance, List<UUID> souls) {
		UUID owner = player.getUUID();
		MinecraftServer server = player.level().getServer();
		MisiConfig cfg = MisiConfig.get();
		if (!cfg.aktif) {
			return deny(player, "kultivator.sekte.misi.cek.mati");
		}
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.active()) {
			return deny(player, "kultivator.sekte.misi.cek.aula");
		}
		if (MisiData.active(owner).size() >= cfg.misiMaksAktif) {
			return deny(player, Component.translatable("kultivator.sekte.misi.cek.penuh", cfg.misiMaksAktif));
		}
		ListTag board = board(server, owner);
		if (index < 0 || index >= board.size() || !(board.get(index) instanceof CompoundTag offer) || offer.getBooleanOr("taken", false)) {
			return deny(player, "kultivator.sekte.misi.cek.hilang");
		}
		TemplatMisi tpl = TemplatMisi.byId(offer.getStringOr("tpl", ""));
		if (tpl == null) {
			return deny(player, "kultivator.sekte.misi.cek.hilang");
		}
		return depart(player, tpl, offer, stance, souls);
	}

	/**
	 * T13: module D's "Kirim Utusan" travels as an envoy mission (template {@code utusan}) to {@code sectId}: one envoy, the
	 * sect's tier capped by the envoy's sect rank, the peaceful stance, stage news and a written report like any story
	 * mission. {@code officer}: the Elder of External Affairs goes (a better standing on return).
	 */
	static boolean sendEnvoy(ServerPlayer player, String sectId, UUID soul, boolean officer) {
		UUID owner = player.getUUID();
		DiscipleEntity envoy = find(owner, soul);
		if (envoy == null) {
			return deny(player, "kultivator.sekte.misi.cek.hilang_murid");
		}
		TemplatMisi tpl = TemplatMisi.UTUSAN;
		int cap = Math.min(tpl.maxTier(), maxTier(SectRanks.rank(envoy.data())));
		int tier = Math.max(tpl.minTier(), Math.min(cap, Math.max(1, SekteApi.diplomasi().tier(sectId))));
		RandomSource random = player.getRandom();
		CompoundTag offer = new CompoundTag();
		offer.putString("tpl", tpl.id());
		offer.putInt("tier", tier);
		offer.putInt("place", random.nextInt(PLACES));
		offer.putInt("foe", random.nextInt(FOES));
		offer.putString("sect", sectId);
		Component name = SekteApi.diplomasi().name(sectId);
		MisiData.putName(offer, "sect_name", name != null ? name : Component.literal(sectId));
		if (officer) {
			offer.putBoolean("luar", true);
		}
		return depart(player, tpl, offer, TemplatMisi.Pendekatan.DAMAI, List.of(soul));
	}

	/** The departure of a team on {@code offer} (a board offer, or module D's envoy offer). */
	private static boolean depart(ServerPlayer player, TemplatMisi tpl, CompoundTag offer, TemplatMisi.Pendekatan stance, List<UUID> souls) {
		UUID owner = player.getUUID();
		MinecraftServer server = player.level().getServer();
		MisiConfig cfg = MisiConfig.get();
		Halls.HallView hall = Halls.hallOf(owner);
		if (!cfg.aktif || hall == null || !hall.active()) {
			return deny(player, !cfg.aktif ? "kultivator.sekte.misi.cek.mati" : "kultivator.sekte.misi.cek.aula");
		}
		if (MisiData.active(owner).size() >= cfg.misiMaksAktif) {
			return deny(player, Component.translatable("kultivator.sekte.misi.cek.penuh", cfg.misiMaksAktif));
		}
		if (souls.isEmpty() || souls.size() > cfg.timMaks) {
			return deny(player, Component.translatable("kultivator.sekte.misi.cek.ukuran", cfg.timMaks));
		}
		List<DiscipleEntity> members = new ArrayList<>();
		int bestRank = 0;
		for (UUID soul : souls) {
			DiscipleEntity disciple = find(owner, soul);
			if (disciple == null) {
				return deny(player, "kultivator.sekte.misi.cek.hilang_murid");
			}
			String why = blocker(owner, disciple);
			if (why != null) {
				return deny(player, Component.translatable(why, disciple.getDisplayName()));
			}
			if (!members.contains(disciple)) {
				members.add(disciple);
			}
			bestRank = Math.max(bestRank, SectRanks.rank(disciple.data()));
		}
		// DESIGN 36.2.2: two disciples with a grudge (module R's Dendam) refuse to travel in one team.
		for (int i = 0; i < members.size(); i++) {
			for (int j = i + 1; j < members.size(); j++) {
				DiscipleEntity a = members.get(i);
				DiscipleEntity b = members.get(j);
				if (SekteApi.hubungan().kind(owner, a.soulId(), b.soulId()) == dev.kultivator.sekte.api.Ikatan.DENDAM) {
					return deny(player, Component.translatable("kultivator.sekte.misi.cek.dendam", a.getDisplayName(), b.getDisplayName()));
				}
			}
		}
		int tier = Math.max(1, Math.min(9, offer.getIntOr("tier", 1)));
		if (tier > maxTier(bestRank)) {
			return deny(player, Component.translatable("kultivator.sekte.misi.cek.jenjang", tier, maxTier(bestRank)));
		}
		int price = tpl.has(TemplatMisi.Flags.AUCTION) ? AUCTION_PK_PER_TIER * tier : 0;
		if (price > 0 && !Halls.spendPool(owner, price)) {
			return deny(player, Component.translatable("kultivator.sekte.misi.cek.pk", price));
		}
		ServerLevel level = player.level();
		long now = server.overworld().getGameTime();
		int id = MisiData.nextId(owner);
		CompoundTag mission = new CompoundTag();
		mission.putInt("id", id);
		mission.putString("tpl", tpl.id());
		mission.putInt("tier", tier);
		mission.putString("sikap", stance.id());
		mission.putLong("start", now);
		mission.putLong("end", now + tpl.ticks());
		mission.putLong("len", tpl.ticks());
		mission.putLong("day", Ingatan.today(server));
		mission.putInt("place", offer.getIntOr("place", 0));
		mission.putInt("foe", offer.getIntOr("foe", 0));
		mission.putInt("price", price);
		for (String key : new String[]{"sect", "village", "target", "target_how"}) {
			if (offer.contains(key)) {
				mission.putString(key, offer.getStringOr(key, ""));
			}
		}
		for (String key : new String[]{"sect_name", "target_name"}) {
			if (offer.contains(key)) {
				MisiData.putName(mission, key, MisiData.name(offer, key));
			}
		}
		if (offer.getBooleanOr("luar", false)) {
			mission.putBoolean("luar", true);
		}
		ListTag team = new ListTag();
		List<UUID> sent = new ArrayList<>();
		Component leaderName = members.get(0).getDisplayName();
		SekteSuara.line(members.get(0), "depart", "careful", 200);
		for (DiscipleEntity member : members) {
			team.add(snapshot(member));
		}
		mission.put("team", team);
		MisiData.list(MisiData.section(owner), "aktif").add(mission);
		for (DiscipleEntity member : members) {
			UUID soul = member.soulId();
			Expeditions.releaseErrands(owner, soul);
			Vec3 at = member.position();
			Fx.burst(level, at.add(0.0D, 1.0D, 0.0D), member.data().rootColor(), 18, 0.8F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 0.7F, 0.9F);
			CompoundTag info = new CompoundTag();
			info.putInt("mid", id);
			info.putString("tpl", tpl.id());
			Halls.storeAway(owner, member, now + tpl.ticks(), Misi.REASON, info);
			sent.add(soul);
		}
		offer.putBoolean("taken", true);
		MisiData.dirty();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.1F);
		Msg.chat(player, Component.translatable("kultivator.sekte.misi.berangkat", Component.translatable(tpl.key()), tier, leaderName,
				sent.size(), Component.translatable(stance.key()), String.format(java.util.Locale.ROOT, "%.1f", tpl.ticks() / 24000.0D))
				.withStyle(ChatFormatting.AQUA));
		if (price > 0) {
			Msg.chat(player, Component.translatable("kultivator.sekte.misi.lelang.bayar", price).withStyle(ChatFormatting.GRAY));
		}
		SekteLog.log(server, owner, SekteIds.X, Component.translatable("kultivator.sekte.misi.log.berangkat", Component.translatable(tpl.key()),
				tier, leaderName, sent.size()));
		SekteBus.MISSION_SENT.invoker().onMissionSent(server, owner, tpl.id(), List.copyOf(sent));
		return true;
	}

	// ================================================================== the team at its place (M2-ANIM)

	/**
	 * M2-ANIM, about once a second per online master ({@code Misi.tickOwner}): the members of every running story mission are away
	 * (pure data), so while a player is within 128 blocks of the place of the mission's current third each member is shown by a
	 * {@code Jelmaan} stage body. The place: the NPC sect's gate for an envoy or a spy (from the second third, the first is the
	 * road), the vassal village's bell for a village errand, else a fixed spot 140 to 159 blocks from the stele. The clip follows
	 * the stage kind: the sword routine in a fight, folded arms in a scheme, the bow of an envoy at a gate (talking hands elsewhere),
	 * the spear at its post when scouting, the herb sickle on a cliff for the herb mission. A wounded member wears the bandage.
	 */
	static void tampilkan(MinecraftServer server, UUID owner) {
		Halls.HallView hall = Halls.hallOf(owner);
		ServerLevel hallLevel = hall == null || hall.dim() == null ? null : server.getLevel(hall.dim());
		if (hallLevel == null) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (CompoundTag mission : MisiData.active(owner)) {
			TemplatMisi tpl = TemplatMisi.byId(mission.getStringOr("tpl", ""));
			long start = mission.getLongOr("start", now);
			long len = Math.max(60L, mission.getLongOr("len", 24000L));
			if (tpl == null || mission.getBooleanOr("finished", false) || mission.getBooleanOr("ditarik", false) || now >= start + len) {
				continue;
			}
			int stage = (int) Math.max(0L, Math.min(2L, (now - start) * 3L / len));
			TemplatMisi.Jenis kind = tpl.stage(stage);
			int id = mission.getIntOr("id", 0);
			ServerLevel level = hallLevel;
			BlockPos site = null;
			boolean gate = false;
			String sect = mission.getStringOr("sect", "");
			if (!sect.isEmpty() && (tpl == TemplatMisi.UTUSAN || tpl == TemplatMisi.SAMAR)) {
				site = SekteApi.diplomasi().gate(sect);
				if (site != null) {
					gate = true;
					level = server.overworld();
				}
			} else if (tpl.has(TemplatMisi.Flags.VILLAGE)) {
				for (VillageView village : Vassals.views(owner)) {
					if (village.name().equals(mission.getStringOr("village", ""))) {
						site = village.bell();
					}
				}
			}
			if (gate && kind == TemplatMisi.Jenis.JELAJAH) {
				continue; // still on the road to the gate
			}
			if (site == null && Jelmaan.dekatPemain(hallLevel, hall.pos(), 300.0D)) {
				site = Jelmaan.titikLuar(hallLevel, hall.pos(), owner.getLeastSignificantBits() * 31L + id, 140);
			}
			if (site == null || !Jelmaan.dekatPemain(level, site, 140.0D)) {
				continue;
			}
			Jelmaan.Adegan work = switch (kind) {
				case TEMPUR -> Jelmaan.Adegan.diam(GerakId.LATIH_JURUS, 0, 0);
				case LICIK -> Jelmaan.Adegan.diam(gate ? GerakId.SIKAP_TUNDUK : GerakId.SIKAP_LIPAT_TANGAN, 0, 0);
				case DAMAI -> gate ? Jelmaan.Adegan.diam(GerakId.HORMAT, GerakId.V_HORMAT_BAOQUAN, 0).berulang(160)
						: Jelmaan.Adegan.diam(GerakId.BICARA_TANGAN, 0, 0);
				case JELAJAH -> tpl == TemplatMisi.HERBA ? Jelmaan.Adegan.diam(GerakId.PETIK_HERBA_TEBING, 0, PropId.SABIT_HERBA)
						: Jelmaan.Adegan.diam(GerakId.BERDIRI_JAGA, 0, PropId.TOMBAK);
			};
			List<CompoundTag> team = MisiData.team(mission);
			for (int i = 0; i < team.size(); i++) {
				CompoundTag member = team.get(i);
				UUID soul = MisiData.soul(member);
				if (soul == null || member.getBooleanOr("back", false) || member.getBooleanOr("left", false)) {
					continue;
				}
				Jelmaan.Adegan shown = member.getIntOr("hurt_at", -1) >= 0 && stage >= member.getIntOr("hurt_at", -1) ? work.terluka(Gaya.LUKA_LENGAN) : work;
				Jelmaan.tunjukkan(level, "misi:" + owner + ":" + id + ":" + soul, owner, soul, null, Jelmaan.sekitar(level, site, i), site, shown, 100);
			}
		}
	}

	/** What the stage rolls read of a member, taken when it leaves (it is pure data while away). */
	static CompoundTag snapshot(DiscipleEntity member) {
		CompoundTag entry = new CompoundTag();
		DiscipleData data = member.data();
		entry.putString("soul", member.soulId().toString());
		MisiData.putName(entry, "name", member.getDisplayName());
		entry.putInt("realm", data.realm().ordinal());
		entry.putInt("rank", SectRanks.rank(data));
		entry.put("sifat", JalanCerita.traitTag(SekteApi.hati().traits(data)));
		entry.putDouble("wf", SekteApi.hati().workFactor(data));
		entry.putInt("color", data.rootColor());
		return entry;
	}

	/**
	 * The three stage chances of offer {@code index} for {@code souls} with {@code stance} (the board's preview), or null
	 * when the offer or a member is unknown. Pure computation, nothing is saved.
	 */
	static @Nullable CompoundTag preview(MinecraftServer server, UUID owner, int index, TemplatMisi.Pendekatan stance, List<UUID> souls) {
		ListTag board = board(server, owner);
		if (index < 0 || index >= board.size() || !(board.get(index) instanceof CompoundTag offer)) {
			return null;
		}
		CompoundTag mission = new CompoundTag();
		mission.putString("tpl", offer.getStringOr("tpl", ""));
		mission.putInt("tier", offer.getIntOr("tier", 1));
		mission.putString("sikap", stance.id());
		ListTag team = new ListTag();
		String firstBlock = "";
		Component blockedName = Component.empty();
		for (UUID soul : souls) {
			DiscipleEntity disciple = find(owner, soul);
			if (disciple == null) {
				continue;
			}
			team.add(snapshot(disciple));
			String why = blocker(owner, disciple);
			if (why != null && firstBlock.isEmpty()) {
				firstBlock = why;
				blockedName = disciple.getDisplayName();
			}
		}
		if (team.isEmpty()) {
			return null;
		}
		mission.put("team", team);
		CompoundTag out = new CompoundTag();
		out.putInt("offer", index);
		out.putString("sikap", stance.id());
		StringBuilder ids = new StringBuilder();
		for (UUID soul : souls) {
			ids.append(ids.isEmpty() ? "" : ",").append(soul);
		}
		out.putString("souls", ids.toString());
		double all = 1.0D;
		for (int stage = 0; stage < 3; stage++) {
			JalanCerita.Rincian r = JalanCerita.chance(owner, mission, stage);
			CompoundTag part = new CompoundTag();
			part.putDouble("total", r.total());
			part.putDouble("base", r.base());
			part.putDouble("realm", r.realm());
			part.putDouble("stance", r.stance());
			part.putDouble("traits", r.traits());
			part.putDouble("ties", r.ties());
			part.putDouble("team", r.team());
			part.putDouble("work", r.work());
			part.putDouble("office", r.office());
			out.put("s" + stage, part);
			all *= r.total();
		}
		out.putDouble("legend", all);
		int bestRank = 0;
		for (CompoundTag member : MisiData.compounds(team)) {
			bestRank = Math.max(bestRank, member.getIntOr("rank", 0));
		}
		out.putBoolean("rank_ok", offer.getIntOr("tier", 1) <= maxTier(bestRank));
		out.putString("blocker", firstBlock);
		MisiData.putName(out, "blocker_name", blockedName);
		return out;
	}

	private static boolean deny(ServerPlayer player, String key) {
		return deny(player, Component.translatable(key));
	}

	private static boolean deny(ServerPlayer player, Component why) {
		Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
		return false;
	}

	// ================================================================== recall

	/**
	 * "Tarik Pulang": {@code withWounded} brings the whole team home (a quarter day when someone is hurt, they are
	 * carried); without them only the healthy come home at once and the wounded are left to limp home at the mission's end
	 * ({@code DIKORBANKAN} for each of them, DESIGN 36.2.7). The mission ends with the stages done so far.
	 */
	static boolean recall(ServerPlayer player, int id, boolean withWounded) {
		UUID owner = player.getUUID();
		CompoundTag mission = MisiData.mission(owner, id);
		if (mission == null || mission.getBooleanOr("finished", false) || mission.getBooleanOr("ditarik", false)) {
			return deny(player, "kultivator.sekte.misi.tarik.tidak");
		}
		long now = player.level().getServer().overworld().getGameTime();
		boolean anyHurt = false;
		for (CompoundTag member : MisiData.team(mission)) {
			anyHurt |= member.getIntOr("hurt_at", -1) >= 0;
		}
		mission.putBoolean("ditarik", true);
		long back = withWounded && anyHurt ? now + RECALL_WITH_WOUNDED : now + RECALL_FAST;
		mission.putLong("end", Math.min(mission.getLongOr("end", back), back));
		int left = 0;
		for (CompoundTag member : MisiData.team(mission)) {
			UUID soul = MisiData.soul(member);
			if (soul == null) {
				continue;
			}
			boolean hurt = member.getIntOr("hurt_at", -1) >= 0;
			if (!withWounded && hurt) {
				member.putBoolean("left", true);
				left++;
				continue;
			}
			MisiData.moveReturn(owner, soul, back);
			Disciples.setAwayUntil(player.level().getServer(), owner, soul, back);
		}
		MisiData.dirty();
		TemplatMisi tpl = TemplatMisi.byId(mission.getStringOr("tpl", ""));
		Component what = tpl == null ? Component.literal("?") : Component.translatable(tpl.key());
		Msg.chat(player, Component.translatable(left > 0 ? "kultivator.sekte.misi.tarik.tinggal" : "kultivator.sekte.misi.tarik.ya", what, left)
				.withStyle(left > 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
		return true;
	}

	private TimMisi() {
	}
}
