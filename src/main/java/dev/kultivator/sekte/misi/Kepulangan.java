package dev.kultivator.sekte.misi;

import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.TreasureBirth;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.fx.Fx;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.flow.LumbungOverflow;
import dev.kultivator.sect.event.Expeditions;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.SectRanks;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.Kenangan;
import dev.kultivator.sekte.api.MisiHasil;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.sekte.SekteSuara;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The end of a story mission (DESIGN 36.2.7, butir 43 and 44): the remaining stages, the result ({@code api/MisiHasil},
 * {@code SekteBus.MISSION_RETURNED}), what each member brings home (written into its stored data by the
 * {@code Halls} return handler), the youths ({@code Halls.addCandidates}), the treasures (the shared expedition loot
 * table into the Lumbung, a treasure birth through {@code DuniaApi.treasures()} as the "map"), and the homecoming: the
 * team appears outside the gate, walks to the {@code arrival} anchor and lines up ({@code ACT_MUSTER}), the leader
 * greets, the short scene plays (beats 160 / 161) and the master gets the Gulungan Laporan.
 * <p>
 * Nobody dies here: a hurt member comes back at 30% health with a 3-minute Qi Deviation, like {@code Expeditions}.
 */
final class Kepulangan {

	/** A hurt member arrives at this share of its health, with this Qi Deviation (ticks), as an expedition's. */
	static final float HURT_HEALTH = 0.30F;
	static final int HURT_DEVIATION = 3600;
	/** The line-up at the arrival anchor lasts this long once the member stands there (10 s). */
	static final long MUSTER_TICKS = 200L;
	/** A member that never reaches its spot is released after this long. */
	static final long MUSTER_TIMEOUT = 900L;
	/** An arrival record whose body never shows up is dropped after this long. */
	static final long ARRIVAL_TIMEOUT = 2400L;
	/** The scene plays only for a master this close to the leader. */
	static final double SCENE_RANGE = 64.0D;
	/** Youths per successful recruit roll: 1 (60%), 2 (30%), 3 (10%). */
	static final double ONE = 0.60D;
	static final double TWO = 0.30D;
	static final double OFFICE_RECRUIT = 0.10D;
	static final double SEARCH_WANDERER = 0.35D;
	static final double SEARCH_RUNAWAY = 0.20D;
	static final double SEARCH_LEGEND = 0.15D;
	static final double LICIK_EXTRA = 0.10D;
	static final int REPORT_ITEMS = 8;

	// ================================================================== finishing

	/**
	 * Resolves the rest of the mission and everything it brings (once). Called by the tick when the mission is due, or by
	 * the return handler when a member arrives first.
	 */
	static void finish(MinecraftServer server, UUID owner, CompoundTag mission) {
		if (mission.getBooleanOr("finished", false)) {
			return;
		}
		TemplatMisi tpl = TemplatMisi.byId(mission.getStringOr("tpl", ""));
		if (tpl == null) {
			mission.putBoolean("finished", true);
			MisiData.dirty();
			return;
		}
		RandomSource random = server.overworld().getRandom();
		boolean recalled = mission.getBooleanOr("ditarik", false);
		if (!recalled) {
			for (int stage = mission.getIntOr("done", 0); stage < 3; stage++) {
				JalanCerita.roll(owner, mission, stage, random);
			}
		}
		int tier = Math.max(1, Math.min(9, mission.getIntOr("tier", 1)));
		int grade = Math.min(3, JalanCerita.successes(mission));
		TemplatMisi.Pendekatan stance = TemplatMisi.Pendekatan.byId(mission.getStringOr("sikap", ""));
		List<CompoundTag> team = MisiData.team(mission);
		List<UUID> souls = new ArrayList<>();
		List<UUID> hurt = new ArrayList<>();
		List<UUID> left = new ArrayList<>();
		int licik = 0;
		for (CompoundTag member : team) {
			UUID soul = MisiData.soul(member);
			if (soul == null) {
				continue;
			}
			souls.add(soul);
			if (member.getIntOr("hurt_at", -1) >= 0) {
				hurt.add(soul);
			}
			if (member.getBooleanOr("left", false)) {
				left.add(soul);
			}
			if (JalanCerita.traits(member).contains(Sifat.LICIK)) {
				licik++;
			}
		}
		CompoundTag report = new CompoundTag();
		report.putInt("id", mission.getIntOr("id", 0));
		report.putString("tpl", tpl.id());
		report.putInt("tier", tier);
		report.putString("sikap", stance.id());
		report.putInt("grade", grade);
		report.putLong("day", Ingatan.today(server));
		report.putInt("place", mission.getIntOr("place", 0));
		report.putInt("foe", mission.getIntOr("foe", 0));
		report.putBoolean("ditarik", recalled);
		report.put("stages", mission.getListOrEmpty("stages").copy());
		ListTag teamTag = new ListTag();
		for (CompoundTag member : team) {
			CompoundTag entry = new CompoundTag();
			MisiData.putName(entry, "name", MisiData.name(member, "name"));
			entry.putString("soul", member.getStringOr("soul", ""));
			entry.putBoolean("hurt", member.getIntOr("hurt_at", -1) >= 0);
			entry.putBoolean("left", member.getBooleanOr("left", false));
			entry.putInt("color", member.getIntOr("color", 0xFFFFFF));
			teamTag.add(entry);
		}
		report.put("team", teamTag);
		for (String key : new String[]{"sect_name", "target_name"}) {
			if (mission.contains(key)) {
				MisiData.putName(report, key, MisiData.name(mission, key));
			}
		}
		if (mission.contains("village")) {
			report.putString("village", mission.getStringOr("village", ""));
		}

		// ---- the hall: renown, PK, the auction's refund
		if (grade > 0) {
			Halls.addRenown(owner, 8 * tier * grade, "kultivator.sekte.misi.renown");
			Halls.addPool(owner, 4 * tier * grade);
		} else if (mission.getIntOr("price", 0) > 0) {
			Halls.addPool(owner, mission.getIntOr("price", 0) / 2);
			report.putInt("refund", mission.getIntOr("price", 0) / 2);
		}

		// ---- youths (butir 44)
		int recruits = 0;
		if (grade >= 2) {
			double chance = MisiConfig.get().peluangCalon
					+ (SekteApi.tetua().holder(owner, Jabatan.TETUA_LUAR) != null ? OFFICE_RECRUIT : 0.0D);
			if (tpl.has(TemplatMisi.Flags.VILLAGE)) {
				chance *= 2.0D;
			}
			if (grade == 3) {
				chance *= 1.5D;
			}
			if (random.nextDouble() < Math.min(1.0D, chance)) {
				double roll = random.nextDouble();
				recruits = roll < ONE ? 1 : roll < ONE + TWO ? 2 : 3;
				List<Halls.CandidateSpec> specs = new ArrayList<>();
				String village = mission.getStringOr("village", "");
				String origin = !village.isEmpty() ? village : "kultivator.sekte.misi.asal." + tpl.id();
				for (int i = 0; i < recruits; i++) {
					DiscipleData youth = DiscipleData.fresh(random);
					if (grade == 3 && random.nextDouble() < 0.30D) {
						youth.setRootGrade(youth.rootGrade().up());
					}
					specs.add(new Halls.CandidateSpec(youth, -1, 0.50D, -1.0D, false, "sekte_misi", origin, grade == 3 ? 10 : 5));
				}
				Halls.addCandidates(owner, specs);
			}
		}
		report.putInt("recruits", recruits);

		// ---- treasures into the Lumbung (the expedition loot table, shared read-only)
		List<ItemStack> loot = new ArrayList<>();
		if (grade > 0 && !tpl.has(TemplatMisi.Flags.SEARCH)) {
			int rolls = grade;
			for (int i = 0; i < licik; i++) {
				if (random.nextDouble() < LICIK_EXTRA) {
					rolls++;
				}
			}
			if (tpl.has(TemplatMisi.Flags.AUCTION) && grade < 2) {
				rolls = 0;
			}
			for (int i = 0; i < rolls; i++) {
				List<ItemStack> drop = Expeditions.lootTable(tpl.lootType(), tier, random);
				if (drop.isEmpty()) {
					continue;
				}
				if (i == 0) {
					loot.addAll(drop);
				} else {
					loot.add(drop.get(random.nextInt(drop.size())));
				}
			}
		}
		ListTag items = new ListTag();
		int worst = LumbungOverflow.LUMBUNG;
		Component example = null;
		for (ItemStack stack : loot) {
			if (stack.isEmpty()) {
				continue;
			}
			Component name = Component.translatable("kultivator.sekte.misi.lapor.item", stack.getCount(), stack.getHoverName());
			if (items.size() < REPORT_ITEMS) {
				CompoundTag line = new CompoundTag();
				MisiData.putName(line, "text", name);
				items.add(line);
			}
			int where = LumbungOverflow.store(owner, stack);
			if (where > worst) {
				worst = where;
				example = stack.getHoverName();
			}
		}
		report.put("items", items);
		report.putInt("more", Math.max(0, loot.size() - REPORT_ITEMS));
		int treasures = loot.size();

		// ---- the treasure "map": a treasure birth far from the hall (module L), only on a legendary end
		if (grade == 3 && tpl.has(TemplatMisi.Flags.MAP) && random.nextDouble() < (tpl == TemplatMisi.KABAR ? 0.40D : 0.60D)) {
			TreasureBirth birth = treasureMap(server, owner, tier, random);
			if (birth != null) {
				CompoundTag map = new CompoundTag();
				map.putInt("x", birth.pos().getX());
				map.putInt("z", birth.pos().getZ());
				map.putString("kind", birth.kind());
				report.put("map", map);
				treasures++;
			}
		}
		report.putInt("treasures", treasures);

		// ---- the missing disciple (contract C36.X4)
		List<String> storyArgs = new ArrayList<>();
		if (tpl.has(TemplatMisi.Flags.SEARCH)) {
			UUID target = MisiData.uuid(mission.getStringOr("target", ""));
			boolean wanderer = "mengembara".equals(mission.getStringOr("target_how", ""));
			double chance = (wanderer ? SEARCH_WANDERER : SEARCH_RUNAWAY) + (grade == 3 ? SEARCH_LEGEND : 0.0D);
			boolean found = target != null && grade >= 2 && random.nextDouble() < chance;
			if (mission.getIntOr("paksa", -1) >= 2 && target != null) {
				found = true;
			}
			report.putBoolean("found", found);
			if (found) {
				storyArgs.add("pulang:" + target);
				MisiData.removeSearchable(owner, target);
			}
		}
		storyArgs.add(tpl.id());
		storyArgs.add(Integer.toString(tier));

		// ---- module D: the standing with the sect the team met
		String sect = mission.getStringOr("sect", "");
		if (!sect.isEmpty()) {
			int delta;
			if (tpl == TemplatMisi.UTUSAN) {
				// T13: one envoy formula for the board's envoys and module D's "Kirim Utusan": D's +5 (+3 for the Elder of
				// External Affairs) scaled by the report (grade 0 offends: -2; 1 half; 2 full; 3 one and a half).
				int base = 5 + (mission.getBooleanOr("luar", false) ? 3 : 0);
				delta = grade == 0 ? -2 : grade == 1 ? base / 2 : grade == 2 ? base : base * 3 / 2;
			} else if (tpl.has(TemplatMisi.Flags.SPY)) {
				delta = grade >= 2 ? 0 : -6;
			} else if (tpl.has(TemplatMisi.Flags.FOE_SECT)) {
				delta = grade >= 2 ? -2 : -1;
			} else {
				delta = stance == TemplatMisi.Pendekatan.DAMAI ? 2 : stance == TemplatMisi.Pendekatan.LICIK ? -1 : 0;
			}
			if (delta != 0) {
				SekteApi.diplomasi().adjust(owner, sect, delta, "misi." + tpl.id());
			}
			report.putInt("standing", delta);
		}

		// ---- the members: what the return handler writes, honours, ties, memories quoted in the report
		CompoundTag effects = MisiData.section(owner).getCompound("efek").orElse(null);
		if (effects == null) {
			effects = new CompoundTag();
			MisiData.section(owner).put("efek", effects);
		}
		double baseShare = tier <= 3 ? 0.15D : tier <= 6 ? 0.25D : 0.35D;
		for (int i = 0; i < team.size(); i++) {
			CompoundTag member = team.get(i);
			UUID soul = MisiData.soul(member);
			if (soul == null) {
				continue;
			}
			boolean isHurt = hurt.contains(soul);
			boolean isLeft = left.contains(soul);
			CompoundTag effect = new CompoundTag();
			effect.putDouble("share", baseShare * (grade == 0 ? 0.15D : grade / 3.0D));
			effect.putInt("pk", 8 * tier * grade + (i == 0 && grade > 0 ? 5 : 0));
			effect.putFloat("hp", isHurt ? HURT_HEALTH : 1.0F);
			effect.putInt("dev", isHurt ? HURT_DEVIATION * (isLeft ? 2 : 1) : 0);
			effect.putInt("grade", grade);
			effect.putString("tpl", tpl.id());
			effect.putLong("day", Ingatan.today(server));
			if (isLeft) {
				effect.putString("mem", "misi.ditinggal");
				effect.putInt("w", -6);
			} else if (isHurt) {
				effect.putString("mem", "misi.luka");
				effect.putInt("w", -6);
			} else if (grade >= 2) {
				effect.putString("mem", "misi.sukses");
				effect.putInt("w", grade == 3 ? 6 : 4);
			} else {
				effect.putString("mem", "misi.gagal");
				effect.putInt("w", -2);
			}
			effects.put(soul.toString(), effect);
			if (grade >= 2 && !isLeft) {
				SekteBus.honored(server, owner, soul, "misi.sukses", 2 + grade + (i == 0 ? 1 : 0));
			}
			if (isLeft) {
				SekteBus.mistreated(server, owner, soul, Perlakuan.DIKORBANKAN, "misi.ditinggal");
			}
		}
		for (int i = 0; i < souls.size(); i++) {
			for (int j = i + 1; j < souls.size(); j++) {
				UUID a = souls.get(i);
				UUID b = souls.get(j);
				if (left.contains(a) != left.contains(b)) {
					SekteApi.hubungan().adjust(owner, a, b, -8, "misi.ditinggal");
				} else if (grade >= 2) {
					SekteApi.hubungan().adjust(owner, a, b, 4, "misi.sepenanggungan");
				}
			}
		}
		report.put("quotes", quotes(owner, team));

		// ---- the bus, the log, the history
		mission.putBoolean("finished", true);
		mission.put("laporan", report);
		Component sectName = mission.contains("sect_name") ? MisiData.name(mission, "sect_name") : null;
		MisiHasil result = new MisiHasil(SekteIds.X + "." + tpl.id(), souls, grade, recruits, treasures,
				tpl.key() + ".s3." + (grade >= 2 ? "ok" : "gagal"), storyArgs, sect.isEmpty() ? null : sect, hurt);
		MisiData.addHistory(owner, report);
		MisiData.dirty();
		Component leader = team.isEmpty() ? Component.literal("?") : MisiData.name(team.get(0), "name");
		SekteLog.log(server, owner, SekteIds.X, Component.translatable("kultivator.sekte.misi.log.selesai", Component.translatable(tpl.key()),
				Component.translatable("kultivator.sekte.misi.grade." + grade), leader, recruits, treasures));
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.misi.selesai", Component.translatable(tpl.key()),
					Component.translatable("kultivator.sekte.misi.grade." + grade), recruits, treasures)
					.withStyle(grade >= 2 ? ChatFormatting.GOLD : grade == 1 ? ChatFormatting.YELLOW : ChatFormatting.RED));
			LumbungOverflow.tell(master, owner, worst, example);
			if (sectName != null && report.getIntOr("standing", 0) != 0) {
				int delta = report.getIntOr("standing", 0);
				Msg.chat(master, Component.translatable(delta > 0 ? "kultivator.sekte.misi.standing.naik" : "kultivator.sekte.misi.standing.turun",
						sectName, Math.abs(delta)).withStyle(ChatFormatting.GRAY));
			}
		}
		SekteBus.MISSION_RETURNED.invoker().onMissionReturned(server, owner, result);
	}

	/** Up to two strong memories of the members (not X's own) quoted at the end of the report. */
	private static ListTag quotes(UUID owner, List<CompoundTag> team) {
		ListTag list = new ListTag();
		for (CompoundTag member : team) {
			if (list.size() >= 2) {
				break;
			}
			UUID soul = MisiData.soul(member);
			DiscipleData data = soul == null ? null : storedData(owner, soul);
			if (data == null) {
				continue;
			}
			Kenangan best = null;
			for (Kenangan k : Ingatan.of(data)) {
				if (k.text().isEmpty() || k.kind().startsWith(SekteIds.X + ".")) {
					continue;
				}
				if (best == null || Math.abs(k.weight()) > Math.abs(best.weight())) {
					best = k;
				}
			}
			if (best != null) {
				CompoundTag quote = new CompoundTag();
				MisiData.putName(quote, "name", MisiData.name(member, "name"));
				quote.putString("text", best.text());
				quote.putString("arg", best.arg());
				quote.putInt("w", best.weight());
				list.add(quote);
			}
		}
		return list;
	}

	/** The cultivation data of an away member, read from its {@code Halls} away record (read-only). */
	static @Nullable DiscipleData storedData(UUID owner, UUID soul) {
		for (Tag tag : Halls.section(owner, "core").getListOrEmpty("away")) {
			if (tag instanceof CompoundTag rec && soul.equals(rec.read("soul", UUIDUtil.CODEC).orElse(null))) {
				CompoundTag disciple = rec.getCompoundOrEmpty("stored").getCompoundOrEmpty("disciple");
				return disciple.isEmpty() ? null : DiscipleData.load(disciple);
			}
		}
		return null;
	}

	/** A treasure birth 250-500 blocks from the hall in the hall's dimension (null when module L refuses). */
	private static @Nullable TreasureBirth treasureMap(MinecraftServer server, UUID owner, int tier, RandomSource random) {
		Halls.HallView hall = Halls.hallOf(owner);
		ServerLevel level = hall == null ? null : server.getLevel(hall.dim());
		if (level == null) {
			return null;
		}
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double distance = 250.0D + random.nextDouble() * 250.0D;
		BlockPos pos = BlockPos.containing(hall.pos().getX() + Math.cos(angle) * distance, hall.pos().getY(),
				hall.pos().getZ() + Math.sin(angle) * distance);
		return DuniaApi.treasures().spawn(level, pos, Mth.clamp(1 + tier / 3, 1, 4), "");
	}

	// ================================================================== the return handler (Halls, contract C36.X3)

	/** {@code Halls.ReturnHandler} of reason {@link Misi#REASON}: finishes the mission and writes the member's share. */
	static @Nullable Vec3 onReturn(ServerLevel level, UUID owner, CompoundTag stored, CompoundTag info) {
		MinecraftServer server = level.getServer();
		UUID soul = stored.read("soul", UUIDUtil.CODEC).orElse(null);
		int id = info.getIntOr("mid", -1);
		CompoundTag mission = MisiData.mission(owner, id);
		if (mission != null) {
			finish(server, owner, mission);
		}
		if (soul == null) {
			return null;
		}
		CompoundTag section = MisiData.section(owner);
		CompoundTag effects = section.getCompoundOrEmpty("efek");
		CompoundTag effect = effects.getCompoundOrEmpty(soul.toString());
		float hp = 1.0F;
		if (!effect.isEmpty()) {
			DiscipleData data = DiscipleData.load(stored.getCompoundOrEmpty("disciple"));
			apply(server, data, effect);
			stored.put("disciple", data.save());
			hp = effect.getFloatOr("hp", 1.0F);
			effects.remove(soul.toString());
		}
		int index = 0;
		boolean left = false;
		if (mission != null) {
			List<CompoundTag> team = MisiData.team(mission);
			for (int i = 0; i < team.size(); i++) {
				if (soul.toString().equals(team.get(i).getStringOr("soul", ""))) {
					team.get(i).putBoolean("back", true);
					left = team.get(i).getBooleanOr("left", false);
					index = i;
				}
			}
		}
		CompoundTag arrival = new CompoundTag();
		arrival.putString("soul", soul.toString());
		arrival.putInt("mid", id);
		arrival.putFloat("hp", hp);
		arrival.putLong("since", level.getGameTime());
		arrival.putBoolean("lead", index == 0 && !left);
		arrival.putBoolean("left", left);
		arrival.putString("tpl", mission == null ? "" : mission.getStringOr("tpl", ""));
		Vec3 gate = null;
		// The walk in through the gate only while the master is online (the line-up is watched from their tick).
		if (mission != null && !left && MisiConfig.get().kembaliLewatGerbang && server.getPlayerList().getPlayer(owner) != null) {
			Vec3[] spots = gateSpots(level, owner, index, MisiData.team(mission).size());
			if (spots != null) {
				gate = spots[0];
				arrival.putBoolean("muster", true);
				arrival.putDouble("mx", spots[1].x);
				arrival.putDouble("my", spots[1].y);
				arrival.putDouble("mz", spots[1].z);
			}
		}
		MisiData.list(section, "datang").add(arrival);
		MisiData.dirty();
		return gate;
	}

	/** Writes one member's share into its data (bar share, PK, deviation, the memory, X's history). */
	private static void apply(MinecraftServer server, DiscipleData data, CompoundTag effect) {
		DisciplePaths.grantBarShare(data, effect.getDoubleOr("share", 0.0D));
		int pk = effect.getIntOr("pk", 0);
		if (pk > 0) {
			SectRanks.addPk(data, pk, "kultivator.sekte.misi.pk");
		}
		int dev = effect.getIntOr("dev", 0);
		if (dev > 0) {
			data.setDeviationTicks(Math.max(data.deviationTicks(), dev));
		}
		String kind = effect.getStringOr("mem", "");
		if (!kind.isEmpty()) {
			TemplatMisi tpl = TemplatMisi.byId(effect.getStringOr("tpl", ""));
			Ingatan.remember(server, data, kind, null, effect.getIntOr("w", 0), "kultivator.sekte.misi.ingat." + kind.substring(kind.indexOf('.') + 1),
					tpl == null ? "" : tpl.id());
		}
		CompoundTag history = data.extraSection(SekteIds.EXTRA_X);
		int grade = effect.getIntOr("grade", 0);
		history.putInt("n", history.getIntOr("n", 0) + 1);
		if (grade >= 2) {
			history.putInt("ok", history.getIntOr("ok", 0) + 1);
		}
		if (grade == 3) {
			history.putInt("legenda", history.getIntOr("legenda", 0) + 1);
		}
		history.putLong("last", effect.getLongOr("day", 0L));
		history.putString("tpl", effect.getStringOr("tpl", ""));
	}

	/**
	 * Where member {@code index} of {@code size} appears (outside the gate: the {@code arrival} anchor of the built plan
	 * pushed {@code jarakGerbang} blocks away from the stele) and where it lines up (a row across the path at the anchor).
	 * Null when the hall is not active and loaded in {@code level}.
	 */
	static @Nullable Vec3[] gateSpots(ServerLevel level, UUID owner, int index, int size) {
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall == null || !hall.active() || !level.dimension().equals(hall.dim()) || !level.isLoaded(hall.pos())) {
			return null;
		}
		Vec3 stele = Vec3.atBottomCenterOf(hall.pos());
		BlockPos anchor = EngineApi.get().anchor(owner, "arrival");
		Vec3 arrival;
		Vec3 dir;
		if (anchor != null) {
			arrival = Vec3.atBottomCenterOf(anchor);
			Vec3 d = new Vec3(arrival.x - stele.x, 0.0D, arrival.z - stele.z);
			dir = d.lengthSqr() < 1.0D ? new Vec3(0.0D, 0.0D, 1.0D) : d.normalize();
		} else {
			// No built plan: in front of the stele toward the side the master usually stands (south by default).
			dir = new Vec3(0.0D, 0.0D, 1.0D);
			arrival = stele.add(dir.scale(8.0D));
		}
		Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
		double offset = (index - (size - 1) / 2.0D) * 1.6D;
		Vec3 muster = arrival.add(side.scale(offset));
		Vec3 out = arrival.add(dir.scale(MisiConfig.get().jarakGerbang)).add(side.scale(offset * 0.6D));
		if (!level.isLoaded(BlockPos.containing(out)) || !level.isLoaded(BlockPos.containing(muster))) {
			return null;
		}
		return new Vec3[]{out, muster};
	}

	// ================================================================== the homecoming (server tick, per owner)

	/** Applies health and the line-up to arrived bodies, fires the scene, releases the line and hands over reports. */
	static void tick(MinecraftServer server, UUID owner, @Nullable ServerPlayer master) {
		CompoundTag section = MisiData.section(owner);
		ListTag arrivals = section.getListOrEmpty("datang");
		long now = server.overworld().getGameTime();
		for (int i = arrivals.size() - 1; i >= 0; i--) {
			if (!(arrivals.get(i) instanceof CompoundTag arrival)) {
				arrivals.remove(i);
				continue;
			}
			UUID soul = MisiData.uuid(arrival.getStringOr("soul", ""));
			DiscipleEntity body = soul == null ? null : TimMisi.find(owner, soul);
			if (body == null) {
				if (now - arrival.getLongOr("since", now) > ARRIVAL_TIMEOUT) {
					arrivals.remove(i);
					MisiData.dirty();
				}
				continue;
			}
			if (!arrival.getBooleanOr("applied", false)) {
				arrival.putBoolean("applied", true);
				arrival.putLong("at", now);
				float hp = arrival.getFloatOr("hp", 1.0F);
				if (hp < 1.0F) {
					body.setHealth(Math.max(1.0F, body.getMaxHealth() * hp));
				}
				if (arrival.getBooleanOr("left", false)) {
					SekteSuara.line(body, "abandoned", "fear", 200);
				}
				if (arrival.getBooleanOr("muster", false)) {
					BlockPos spot = BlockPos.containing(arrival.getDoubleOr("mx", body.getX()), arrival.getDoubleOr("my", body.getY()),
							arrival.getDoubleOr("mz", body.getZ()));
					body.setActivity(SekteIds.ACT_MUSTER, spot);
				}
				if (body.level() instanceof ServerLevel level) {
					Fx.ring(level, body.position().add(0.0D, 0.1D, 0.0D), body.data().rootColor(), 1.4F);
				}
				MisiData.dirty();
				continue;
			}
			if (!arrival.getBooleanOr("muster", false)) {
				arrivals.remove(i);
				MisiData.dirty();
				continue;
			}
			BlockPos spot = body.activitySpot();
			boolean there = spot != null && body.distanceToSqr(Vec3.atBottomCenterOf(spot)) <= 4.0D;
			if (there && !arrival.contains("reached")) {
				arrival.putLong("reached", now);
				if (arrival.getBooleanOr("lead", false)) {
					boolean envoy = TemplatMisi.UTUSAN.id().equals(arrival.getStringOr("tpl", ""));
					SekteSuara.line(body, envoy ? "envoy_return" : "mission_report", "greet", 200);
					leaderHome(server, owner, master, body, arrival.getIntOr("mid", -1));
				}
				MisiData.dirty();
			}
			long reached = arrival.getLongOr("reached", Long.MAX_VALUE);
			if (now - reached >= MUSTER_TICKS || now - arrival.getLongOr("at", now) >= MUSTER_TIMEOUT) {
				if (body.activity() == SekteIds.ACT_MUSTER) {
					body.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				}
				if (arrival.getBooleanOr("lead", false) && !arrival.contains("reached")) {
					leaderHome(server, owner, master, body, arrival.getIntOr("mid", -1));
				}
				arrivals.remove(i);
				MisiData.dirty();
			}
		}
		handOver(server, owner, master);
	}

	/** The leader stands at the anchor: the scene (beat 160, or 161 for a legendary end), once per mission. */
	private static void leaderHome(MinecraftServer server, UUID owner, @Nullable ServerPlayer master, DiscipleEntity leader, int id) {
		CompoundTag mission = MisiData.mission(owner, id);
		if (mission == null || mission.getBooleanOr("scene", false)) {
			return;
		}
		mission.putBoolean("scene", true);
		MisiData.dirty();
		CompoundTag report = mission.getCompoundOrEmpty("laporan");
		int grade = report.getIntOr("grade", 0);
		if (leader.level() instanceof ServerLevel level) {
			Vec3 at = leader.position();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.6F, 1.2F);
			Fx.burst(level, at.add(0.0D, 1.2D, 0.0D), grade == 3 ? 0xFFD76A : 0xBFE8FF, grade == 3 ? 36 : 20, 1.2F);
			if (MisiConfig.get().adegan && master != null && master.level() == level && master.distanceToSqr(leader) <= SCENE_RANGE * SCENE_RANGE) {
				int beat = grade == 3 ? Misi.BEAT_LEGEND : Misi.BEAT_HOME;
				EpicMoments.send(level, EpicIds.EXTRA, beat, leader, master, at, level.getGameTime(), grade, report.getIntOr("recruits", 0), 0.0F,
						3, 0);
			}
		}
	}

	/**
	 * Hands the report scroll to the master once every member that is not left behind is home and released (or at once
	 * when the master is far: then the mission's report waits in {@code tertunda} until they are online).
	 */
	private static void handOver(MinecraftServer server, UUID owner, @Nullable ServerPlayer master) {
		ListTag arrivals = MisiData.section(owner).getListOrEmpty("datang");
		for (CompoundTag mission : MisiData.active(owner)) {
			if (!mission.getBooleanOr("finished", false)) {
				continue;
			}
			int id = mission.getIntOr("id", -1);
			boolean waiting = false;
			for (CompoundTag member : MisiData.team(mission)) {
				if (!member.getBooleanOr("back", false) && !member.getBooleanOr("left", false)) {
					waiting = true;
				}
			}
			for (Tag tag : arrivals) {
				if (tag instanceof CompoundTag arrival && arrival.getIntOr("mid", -2) == id && arrival.getBooleanOr("muster", false)) {
					waiting = true;
				}
			}
			// A member whose record never came back (forgotten, its body already in the world) does not hold the report
			// longer than two days past the end.
			if (waiting && server.overworld().getGameTime() - mission.getLongOr("end", 0L) < 48000L) {
				continue;
			}
			CompoundTag report = mission.getCompoundOrEmpty("laporan").copy();
			MisiData.removeMission(owner, id);
			if (master != null) {
				deliver(server, master, report);
			} else {
				MisiData.addPending(owner, report);
			}
			return;
		}
		if (master != null) {
			ListTag pending = MisiData.section(owner).getListOrEmpty("tertunda");
			if (!pending.isEmpty() && pending.get(0) instanceof CompoundTag report) {
				pending.remove(0);
				MisiData.dirty();
				deliver(server, master, report);
			}
		}
	}

	/** The scroll into the master's hands (at the feet when the inventory is full), the advancements and the chat line. */
	static void deliver(MinecraftServer server, ServerPlayer master, CompoundTag report) {
		ItemStack scroll = GulunganLaporan.create(report);
		if (!master.getInventory().add(scroll) && !scroll.isEmpty()) {
			ItemEntity item = new ItemEntity(master.level(), master.getX(), master.getY() + 0.5D, master.getZ(), scroll);
			item.addTag(NoGrief.TAG);
			item.setNoPickUpDelay();
			master.level().addFreshEntity(item);
		}
		master.level().playSound(null, master.getX(), master.getY(), master.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9F, 0.9F);
		TemplatMisi tpl = TemplatMisi.byId(report.getStringOr("tpl", ""));
		Msg.chat(master, Component.translatable("kultivator.sekte.misi.gulungan.terima",
				tpl == null ? Component.literal("?") : Component.translatable(tpl.key())).withStyle(ChatFormatting.GOLD));
		Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + "misi_pulang");
		if (report.getIntOr("recruits", 0) > 0 || report.getBooleanOr("found", false) || report.contains("map")
				|| report.getIntOr("treasures", 0) > 0) {
			Advancements.grant(master, SekteIds.ADVANCEMENT_FOLDER + "misi_bawa");
		}
		if (report.getIntOr("recruits", 0) > 0) {
			Msg.chat(master, Component.translatable("kultivator.sekte.misi.calon", report.getIntOr("recruits", 0)).withStyle(ChatFormatting.AQUA));
		}
		if (report.contains("map")) {
			CompoundTag map = report.getCompoundOrEmpty("map");
			Msg.chat(master, Component.translatable("kultivator.sekte.misi.peta", map.getIntOr("x", 0), map.getIntOr("z", 0))
					.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	private Kepulangan() {
	}
}
