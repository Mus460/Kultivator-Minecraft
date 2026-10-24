package dev.kultivator.sect.hall;

import dev.kultivator.KultivatorConfig;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.Technique;
import dev.kultivator.data.XpSource;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.DiscipleTechniques;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.SectData;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.event.SectEvents;
import dev.kultivator.sect.event.Tournament;
import dev.kultivator.sect.event.VoidRealm;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Jenjang Sekte (DESIGN 20.3.6) and the disciple side of Poin Kontribusi (20.3.7), kept in {@code DiscipleData.extra().sect}:
 * rank, PK, promotions and demotions, the Pelindung Sekte designation, the elders' aura and teaching, kill PK, and the
 * {@code DiscipleHooks} the ranks bring (XP factor, orders, technique slots, the disciple limit).
 * <p>
 * Sources: <i>Renegade Immortal</i> (grey honorary disciples), <i>A Record of a Mortal's Journey</i> (the retest after half
 * a year; ch. 871 the grand elder title belongs to the strongest), <i>I Shall Seal the Heavens</i> ch. 27-31 (Outer and
 * Inner Sects, the promotion training), <i>Battle Through the Heavens</i> (Outer and Inner Academy, Grand Elder Su Qian),
 * <i>Martial Peak</i> (the trial disciple; contribution points from the sect), <i>Return of the Mount Hua Sect</i>
 * (generations, instructors and elders), <i>Soul Land</i> (two Titled Douluo protectors).
 */
public final class SectRanks {

	/** Sub-compound of {@code DiscipleData.extra()} owned by module A. */
	public static final String EXTRA = "sect";

	public static final int TERCATAT = 0;
	public static final int LUAR = 1;
	public static final int DALAM = 2;
	public static final int INTI = 3;
	public static final int TETUA = 4;
	public static final int TETUA_AGUNG = 5;
	public static final int MAX_RANK = TETUA_AGUNG;
	/** Technique slots per rank (-1 = all); they limit only new teaching. */
	public static final int[] SLOTS = {2, 3, 5, -1, -1, -1};
	/** XP factor per rank ({@code rankFactor}, replaces the phase-5 0.6). */
	public static final double[] XP_FACTOR = {0.55D, 0.60D, 0.70D, 0.80D, 0.90D, 1.00D};
	/** PK the disciple pays to be promoted INTO each rank. */
	public static final int[] PK_COST = {0, 30, 150, 600, 1500, 0};
	/** Realm needed to be promoted into each rank. */
	public static final Realm[] REALM_NEEDED = {Realm.MORTAL, Realm.QI_REFINING, Realm.FOUNDATION, Realm.GOLDEN_CORE, Realm.NASCENT_SOUL,
			Realm.NASCENT_SOUL};
	/** Robe trim colour per rank: grey, white, blue, jade, violet, white-gold. */
	public static final int[] TRIM = {0x9A9A9A, 0xEDEDED, 0x4A8CFF, 0x3FC48A, 0xA06CFF, 0xFFE9A8};
	private static final String[] RANK_ID = {"tercatat", "luar", "dalam", "inti", "tetua", "tetua_agung"};

	/** Elders' aura: disciples meditating within this range get {@link #ELDER_AURA} (Tetua Agung: the whole territory). */
	public static final double ELDER_AURA_RANGE = 16.0D;
	public static final double ELDER_AURA = 1.15D;
	public static final double GRAND_ELDER_AURA = 1.2D;
	/** Elders teach juniors from this share of their own qi, once per day each (Mount Hua). */
	public static final double ELDER_TEACH_QI = 0.3D;
	public static final double ELDER_TEACH_RANGE = 16.0D;
	/** Tetua: at most one, two from this hall level. */
	public static final int TWO_ELDERS_LEVEL = 5;
	/** Pelindung Sekte: at most one, two from this hall level. */
	public static final int TWO_PROTECTORS_LEVEL = 7;
	/** Kill PK: {@code floor(max health x 0.05)}, at least 1 for a monster, at most this per disciple per day. */
	public static final double KILL_PK_SHARE = 0.05D;
	public static final int KILL_PK_DAILY = 120;
	/** Anugerah Ketua amounts the screen offers. */
	public static final int[] GRANT_AMOUNTS = {10, 50, 100, 500};
	/** Void Realm floor that also opens Murid Inti. */
	public static final int INTI_VOID_FLOOR = 10;

	public static void init() {
		DiscipleHooks.addRankFactor(data -> XP_FACTOR[rank(data)]);
		DiscipleHooks.addModeBlocker(SectRanks::modeBlocker);
		DiscipleHooks.addTeachBlocker((entity, master, technique) -> slotsFull(entity.data()) ? "kultivator.hall.why.slots" : null);
		DiscipleHooks.addOnKill(SectRanks::onKill);
		DiscipleHooks.addXpFactor((entity, data, source) -> aura(entity, source));
		DiscipleHooks.addTick20(SectRanks::tick20);
		DiscipleHooks.addCap(player -> Halls.cap(player.getUUID()));
		DiscipleHooks.addInfo((entity, master, tag) -> {
			DiscipleData data = entity.data();
			CompoundTag hall = new CompoundTag();
			hall.putInt("rank", rank(data));
			hall.putInt("pk", pk(data));
			hall.putInt("slots", SLOTS[rank(data)]);
			hall.putBoolean("protector", isProtector(data));
			hall.putInt("trim", TRIM[rank(data)]);
			tag.put("hall", hall);
		});
	}

	// ================================================================== reads (DESIGN 20.4.5)

	/** 0 Tercatat, 1 Luar, 2 Dalam, 3 Inti, 4 Tetua, 5 Tetua Agung (phase-5 disciples: by realm, see {@link #migratedRank}). */
	public static int rank(DiscipleData data) {
		CompoundTag tag = data.extra().getCompoundOrEmpty(EXTRA);
		if (!tag.contains("rank")) {
			return migratedRank(data);
		}
		return Math.max(0, Math.min(MAX_RANK, tag.getIntOr("rank", LUAR)));
	}

	/** The disciple's own Poin Kontribusi. */
	public static int pk(DiscipleData data) {
		return Math.max(0, data.extra().getCompoundOrEmpty(EXTRA).getIntOr("pk", 0));
	}

	/** Adds (or with a negative amount takes) PK; {@code reasonKey} is a translation key (kept as the last reason). */
	public static void addPk(DiscipleData data, int amount, String reasonKey) {
		ensure(data);
		CompoundTag tag = data.extraSection(EXTRA);
		tag.putInt("pk", (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) pk(data) + amount)));
		if (reasonKey != null && !reasonKey.isEmpty()) {
			tag.putString("pk_reason", reasonKey);
		}
	}

	/** Module B reports the Latihan Khusus result (DESIGN 20.3.15): a pass promotes to Murid Dalam (PK are spent now). */
	public static void onPromotionTrialResult(DiscipleEntity entity, boolean passed) {
		DiscipleData data = entity.data();
		ServerPlayer master = entity.owner();
		if (!passed) {
			if (master != null) {
				Msg.chat(master, Component.translatable("kultivator.hall.rank.trial_failed", entity.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		ensure(data);
		data.extraSection(EXTRA).putBoolean("trial", true);
		if (master != null && rank(data) == LUAR) {
			Msg.chat(master, Component.translatable("kultivator.hall.rank.trial_passed", entity.getDisplayName()).withStyle(ChatFormatting.GREEN));
			promote(master, entity, true);
		}
	}

	/** True for a Pelindung Sekte (protector). */
	public static boolean isProtector(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getBooleanOr("protector", false);
	}

	/** True once the Paviliun's Manual Tubuh Abadi was bought for this disciple (module C reads it, DESIGN 20.3.17). */
	public static boolean hasCodexManual(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getBooleanOr("codex", false);
	}

	public static boolean trialPassed(DiscipleData data) {
		return data.extra().getCompoundOrEmpty(EXTRA).getBooleanOr("trial", false);
	}

	/** Technique slots of the rank (-1 = all). */
	public static int slots(DiscipleData data) {
		return SLOTS[rank(data)];
	}

	public static boolean slotsFull(DiscipleData data) {
		int slots = slots(data);
		return slots >= 0 && data.learnedIds().size() >= slots;
	}

	public static MutableComponent rankName(int rank) {
		int index = Math.max(0, Math.min(MAX_RANK, rank));
		return Component.translatable("kultivator.hall.rank." + RANK_ID[index]).withColor(TRIM[index]);
	}

	/** Phase-5 disciples without {@code extra.sect}: Luar below Foundation, Dalam at Foundation-Golden Core, Inti from Nascent Soul. */
	public static int migratedRank(DiscipleData data) {
		Realm realm = data.realm();
		if (realm.atLeast(Realm.NASCENT_SOUL)) {
			return INTI;
		}
		return realm.atLeast(Realm.FOUNDATION) ? DALAM : LUAR;
	}

	// ================================================================== writes

	/** Gives the disciple its {@code extra.sect} when it has none (migration, PK 0). */
	static void ensure(DiscipleData data) {
		CompoundTag tag = data.extraSection(EXTRA);
		if (!tag.contains("rank")) {
			tag.putInt("rank", migratedRank(data));
			if (!tag.contains("pk")) {
				tag.putInt("pk", 0);
			}
			if (!tag.contains("joined")) {
				tag.putLong("joined", Halls.gameTime());
			}
		}
	}

	/** A new member of the sect at {@code rank} (Sect Hall acceptance). */
	static void setJoined(DiscipleData data, int rank) {
		CompoundTag tag = data.extraSection(EXTRA);
		tag.putInt("rank", Math.max(0, Math.min(MAX_RANK, rank)));
		tag.putInt("pk", 0);
		tag.putLong("joined", Halls.gameTime());
		tag.putBoolean("protector", false);
	}

	static void setRank(DiscipleData data, int rank) {
		ensure(data);
		data.extraSection(EXTRA).putInt("rank", Math.max(0, Math.min(MAX_RANK, rank)));
	}

	static void setCodexManual(DiscipleData data) {
		ensure(data);
		data.extraSection(EXTRA).putBoolean("codex", true);
	}

	// ================================================================== hooks

	/** Tercatat follow or meditate only (duties are activities, not orders); Jaga Aula is the Pelindung's post. */
	private static @Nullable String modeBlocker(DiscipleEntity entity, DiscipleEntity.Mode mode) {
		DiscipleData data = entity.data();
		if (mode == DiscipleEntity.Mode.GUARD_HALL && !isProtector(data)) {
			return "kultivator.hall.why.guard_protector";
		}
		if (rank(data) == TERCATAT && mode != DiscipleEntity.Mode.FOLLOW && mode != DiscipleEntity.Mode.MEDITATE) {
			return "kultivator.hall.why.mode_tercatat";
		}
		return null;
	}

	/** Kill PK: {@code floor(max health x 0.05)}, at least 1 for a monster, capped per day. */
	private static void onKill(DiscipleEntity entity, LivingEntity victim) {
		if (!(victim instanceof Enemy) || entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return;
		}
		DiscipleData data = entity.data();
		ensure(data);
		CompoundTag tag = data.extraSection(EXTRA);
		long day = Halls.dayIndex();
		if (tag.getLongOr("pk_day", -1L) != day) {
			tag.putLong("pk_day", day);
			tag.putInt("pk_today", 0);
		}
		int today = tag.getIntOr("pk_today", 0);
		int gain = Math.min(KILL_PK_DAILY - today, Math.max(1, (int) Math.floor(victim.getMaxHealth() * KILL_PK_SHARE)));
		if (gain <= 0) {
			return;
		}
		tag.putInt("pk_today", today + gain);
		addPk(data, gain, "kultivator.hall.pk.kill");
	}

	/** The elders' aura on meditation XP: x1.15 within 16 of a Tetua, x1.2 inside the territory with a Tetua Agung there. */
	private static double aura(@Nullable DiscipleEntity entity, XpSource source) {
		if (entity == null || source != XpSource.MEDITATION || entity.role() != DiscipleEntity.Role.DISCIPLE) {
			return 1.0D;
		}
		UUID owner = entity.ownerId();
		if (owner == null) {
			return 1.0D;
		}
		double best = 1.0D;
		Boolean inside = null;
		for (DiscipleEntity other : Disciples.loaded(owner)) {
			if (other == entity || other.level() != entity.level()) {
				continue;
			}
			int rank = rank(other.data());
			if (rank == TETUA_AGUNG) {
				if (inside == null) {
					inside = Halls.inTerritory(owner, entity.level(), entity.position());
				}
				if (inside && Halls.inTerritory(owner, other.level(), other.position())) {
					best = Math.max(best, GRAND_ELDER_AURA);
					continue;
				}
			}
			if (rank >= TETUA && other.distanceToSqr(entity) <= ELDER_AURA_RANGE * ELDER_AURA_RANGE) {
				best = Math.max(best, ELDER_AURA);
			}
		}
		return best;
	}

	/** Once a second: migration, the synced rank and talent, the hall's rank registry, the Pelindung's regeneration, teaching. */
	private static void tick20(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		ensure(data);
		int rank = rank(data);
		entity.setSyncedSectRank(rank);
		if (!Talents.tested(data)) {
			// A disciple has nothing hidden any more (phase-5 disciples and blind acceptances).
			Talents.setTested(data, true);
		}
		entity.setSyncedTalent(Talents.packed(data));
		UUID owner = entity.ownerId();
		if (owner == null) {
			return;
		}
		Halls.noteRank(owner, entity.soulId(), rank, isProtector(data), DisciplePaths.power(data));
		GrandFormation.checkGuard(entity);
		if (isProtector(data) && entity.level() instanceof ServerLevel && Halls.inTerritory(owner, entity.level(), entity.position())) {
			entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, true, false, true), entity);
		}
		if (rank >= TETUA && (entity.tickCount + entity.getId()) % 1200 < 20) {
			elderTeaches(entity);
		}
	}

	/**
	 * A Tetua teaches one junior nearby an art it knows, paid from 30% of its own qi, once per day. Murid Mandiri (DESIGN
	 * 39.5.5): with the master offline the elders still teach while the hall's Mandiri policy is not Patuh (the line goes
	 * to the morning report and the junior's diary instead of the chat).
	 */
	private static void elderTeaches(DiscipleEntity elder) {
		DiscipleData data = elder.data();
		CompoundTag tag = data.extraSection(EXTRA);
		long day = Halls.dayIndex();
		if (tag.getLongOr("teach_day", -1L) == day || data.breakingThrough() || elder.getTarget() != null) {
			return;
		}
		ServerPlayer master = elder.owner();
		UUID owner = elder.ownerId();
		if (owner == null || master == null && !teachesWithoutMaster(elder)) {
			return;
		}
		double cost = DiscipleCultivation.maxQi(data) * ELDER_TEACH_QI;
		if (data.qi() + 1.0E-6D < cost) {
			return;
		}
		for (DiscipleEntity junior : Disciples.loaded(owner)) {
			if (junior == elder || junior.level() != elder.level() || rank(junior.data()) >= TETUA
					|| junior.distanceToSqr(elder) > ELDER_TEACH_RANGE * ELDER_TEACH_RANGE || junior.data().breakingThrough()) {
				continue;
			}
			for (Technique technique : data.learnedTechniques()) {
				if (!canLearnFromElder(junior, master, technique)) {
					continue;
				}
				data.setQi(data.qi() - cost);
				junior.data().learn(technique);
				tag.putLong("teach_day", day);
				if (elder.level() instanceof ServerLevel level) {
					Dao dao = technique.dao();
					int colour = dao == null ? 0xFFD54A : dao.color();
					Fx.beam(level, elder.position().add(0.0D, 1.2D, 0.0D), junior.position().add(0.0D, 1.2D, 0.0D), colour, 0.1F);
					Fx.burst(level, junior.position().add(0.0D, 1.0D, 0.0D), colour, 16, 0.8F);
					level.playSound(null, junior.getX(), junior.getY(), junior.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 0.8F, 1.2F);
				}
				Component line = Component.translatable("kultivator.hall.rank.elder_taught", elder.getDisplayName(), junior.getDisplayName(),
						technique.displayName());
				if (master != null) {
					Msg.chat(master, line.copy().withStyle(ChatFormatting.AQUA));
				} else {
					taughtWithoutMaster(elder, junior, owner, technique, line);
				}
				return;
			}
		}
	}

	/** Murid Mandiri is on and the elder's effective policy lets the hall live without its master (not Patuh). */
	private static boolean teachesWithoutMaster(DiscipleEntity elder) {
		return dev.kultivator.sekte.mandiri.api.MandiriApi.aktif()
				&& dev.kultivator.sekte.mandiri.api.MandiriApi.kebijakan(elder).mode().hidup();
	}

	/** The offline lesson: the Tetua's morning report line and an entry in the junior's diary (DESIGN 39.5.5, 39.8.1). */
	private static void taughtWithoutMaster(DiscipleEntity elder, DiscipleEntity junior, UUID owner, Technique technique, Component line) {
		net.minecraft.server.MinecraftServer server = elder.level().getServer();
		dev.kultivator.sekte.SekteLog.log(server, owner, dev.kultivator.sekte.mandiri.api.MandiriIds.MODUL, line);
		long clock = server == null ? 0L : server.overworld().getOverworldClockTime();
		int hour = (int) Math.floorMod(clock / 1000L + 6L, 24L);
		dev.kultivator.sekte.mandiri.api.MandiriApi.catat(owner, junior.soulId(), new dev.kultivator.sekte.mandiri.api.EntriHarian(
				Math.floorDiv(clock, 24000L), hour, "ajar", "ajar.tetua", List.of(elder.getName().getString(),
				"t:" + dev.kultivator.data.TechniqueRegistry.legacy(technique).id()), dev.kultivator.sekte.mandiri.api.EntriHarian.Suasana.SENANG, false));
	}

	private static boolean canLearnFromElder(DiscipleEntity junior, @Nullable ServerPlayer master, Technique technique) {
		DiscipleData data = junior.data();
		if (!DiscipleTechniques.teachable(technique) || data.knows(technique) || !data.realm().atLeast(technique.minRealm()) || slotsFull(data)) {
			return false;
		}
		Dao dao = technique.dao();
		if (dao != null && dao != data.dao() && !DiscipleHooks.daoBypass(data)) {
			return false;
		}
		return DiscipleHooks.teachBlocker(junior, master, technique) == null;
	}

	// ================================================================== promotions (the Jenjang tablet)

	/** One line of a promotion checklist; {@code trial} marks the Latihan Khusus line (the promotion itself starts it). */
	record Requirement(boolean ok, Component text, boolean trial) {
		Requirement(boolean ok, Component text) {
			this(ok, text, false);
		}
	}

	/** Every requirement for {@code entity}'s next rank (empty at the top). */
	static List<Requirement> requirements(ServerPlayer master, DiscipleData data, UUID soul) {
		int rank = rank(data);
		if (rank >= MAX_RANK) {
			return List.of();
		}
		int target = rank + 1;
		java.util.ArrayList<Requirement> list = new java.util.ArrayList<>();
		Realm needed = REALM_NEEDED[target];
		boolean realmOk = target == TETUA_AGUNG
				? data.realm().ordinal() > Realm.NASCENT_SOUL.ordinal() || data.realm() == Realm.NASCENT_SOUL && data.stage() >= 1
				: data.realm().atLeast(needed);
		Component realmText = target == TETUA_AGUNG
				? new Rank(Realm.NASCENT_SOUL, 0, 1).displayName()
				: needed.displayName();
		list.add(new Requirement(realmOk, Component.translatable("kultivator.hall.req.realm", realmText)));
		if (PK_COST[target] > 0) {
			list.add(new Requirement(pk(data) >= PK_COST[target], Component.translatable("kultivator.hall.req.pk", pk(data), PK_COST[target])));
		}
		UUID owner = master.getUUID();
		switch (target) {
			case LUAR -> {
				long need = retestTicks();
				long since = Halls.gameTime() - data.extra().getCompoundOrEmpty(EXTRA).getLongOr("joined", 0L);
				boolean ok = since >= need;
				list.add(new Requirement(ok, ok ? Component.translatable("kultivator.hall.req.retest_done")
						: Component.translatable("kultivator.hall.req.retest", Halls.clock(need - since))));
			}
			case DALAM -> list.add(new Requirement(trialPassed(data), Component.translatable(trialPassed(data)
					? "kultivator.hall.req.trial_done" : "kultivator.hall.req.trial"), true));
			case INTI -> {
				boolean top = Tournament.hasTitle(data, "top3") || Tournament.hasTitle(data, "nomor_satu")
						|| Tournament.hasTitle(data, "juara_peringkat") || VoidRealm.bestFloor(data) >= INTI_VOID_FLOOR;
				list.add(new Requirement(top, Component.translatable("kultivator.hall.req.inti", INTI_VOID_FLOOR)));
			}
			case TETUA -> {
				int max = maxElders(owner);
				int count = Halls.countRank(master, TETUA, soul);
				list.add(new Requirement(count < max, Component.translatable("kultivator.hall.req.elder_seats", count, max)));
			}
			case TETUA_AGUNG -> {
				int power = DisciplePaths.power(data);
				boolean strongest = power >= Halls.strongestPower(master, soul);
				list.add(new Requirement(strongest, Component.translatable("kultivator.hall.req.strongest", power)));
				int count = Halls.countRank(master, TETUA_AGUNG, soul);
				list.add(new Requirement(count < 1, Component.translatable("kultivator.hall.req.grand_seat", count)));
			}
			default -> {
			}
		}
		return list;
	}

	// ================================================================== Murid Mandiri: self-made rank requests (DESIGN 39.5.5)

	/**
	 * The checklist of {@link #requirements} for a request the disciple makes itself (DESIGN 39.5.5). {@code master} may be
	 * null: the realm, PK, retest, trial and Murid Inti lines need no master; the seat counts of Tetua and Tetua Agung read
	 * the master's living list, so without the master online they show one unmet line "waits for the master".
	 */
	static List<Requirement> requirementsSelf(@Nullable ServerPlayer master, DiscipleData data, UUID soul) {
		if (master != null) {
			return requirements(master, data, soul);
		}
		int rank = rank(data);
		if (rank >= MAX_RANK) {
			return List.of();
		}
		int target = rank + 1;
		java.util.ArrayList<Requirement> list = new java.util.ArrayList<>();
		Realm needed = REALM_NEEDED[target];
		boolean realmOk = target == TETUA_AGUNG
				? data.realm().ordinal() > Realm.NASCENT_SOUL.ordinal() || data.realm() == Realm.NASCENT_SOUL && data.stage() >= 1
				: data.realm().atLeast(needed);
		Component realmText = target == TETUA_AGUNG ? new Rank(Realm.NASCENT_SOUL, 0, 1).displayName() : needed.displayName();
		list.add(new Requirement(realmOk, Component.translatable("kultivator.hall.req.realm", realmText)));
		if (PK_COST[target] > 0) {
			list.add(new Requirement(pk(data) >= PK_COST[target], Component.translatable("kultivator.hall.req.pk", pk(data), PK_COST[target])));
		}
		switch (target) {
			case LUAR -> {
				long need = retestTicks();
				long since = Halls.gameTime() - data.extra().getCompoundOrEmpty(EXTRA).getLongOr("joined", 0L);
				boolean ok = since >= need;
				list.add(new Requirement(ok, ok ? Component.translatable("kultivator.hall.req.retest_done")
						: Component.translatable("kultivator.hall.req.retest", Halls.clock(need - since))));
			}
			case DALAM -> list.add(new Requirement(trialPassed(data), Component.translatable(trialPassed(data)
					? "kultivator.hall.req.trial_done" : "kultivator.hall.req.trial"), true));
			case INTI -> {
				boolean top = Tournament.hasTitle(data, "top3") || Tournament.hasTitle(data, "nomor_satu")
						|| Tournament.hasTitle(data, "juara_peringkat") || VoidRealm.bestFloor(data) >= INTI_VOID_FLOOR;
				list.add(new Requirement(top, Component.translatable("kultivator.hall.req.inti", INTI_VOID_FLOOR)));
			}
			default -> list.add(new Requirement(false, Component.translatable("kultivator.sekte.mandiri.tumbuh.req.guru")));
		}
		return list;
	}

	/**
	 * Why the disciple cannot be promoted by its own request now (the first unmet line of {@link #requirementsSelf}), or
	 * null when every line holds. Luar to Murid Dalam needs the Latihan Khusus passed first: the trial itself is started
	 * by the master ({@link #promoteByMaster}), so an unpassed trial is an unmet line here.
	 */
	public static @Nullable Component promoteBlockerSelf(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (rank(data) >= MAX_RANK) {
			return Component.translatable("kultivator.hall.rank.top", entity.getDisplayName());
		}
		for (Requirement requirement : requirementsSelf(entity.owner(), data, entity.soulId())) {
			if (!requirement.ok()) {
				return requirement.text();
			}
		}
		return null;
	}

	/**
	 * Murid Mandiri: true when the self-made request is held only by lines that need the master (the Latihan Khusus of
	 * Luar to Murid Dalam, the seat counts of Tetua and Tetua Agung while the master is offline), so the request goes to
	 * the master instead of waiting for the disciple to grow. False when nothing holds it or a line it must meet itself
	 * (realm, PK, retest, tournament or Void floor, a full seat) is unmet.
	 */
	public static boolean onlyMasterLinesSelf(DiscipleEntity entity) {
		DiscipleData data = entity.data();
		if (rank(data) >= MAX_RANK) {
			return false;
		}
		boolean held = false;
		for (Requirement requirement : requirementsSelf(entity.owner(), data, entity.soulId())) {
			if (requirement.ok()) {
				continue;
			}
			boolean masterLine = requirement.trial()
					|| requirement.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
					&& "kultivator.sekte.mandiri.tumbuh.req.guru".equals(t.getKey());
			if (!masterLine) {
				return false;
			}
			held = true;
		}
		return held;
	}

	/** The unmet lines of the self-made request, for the disciple's notice and the test command (empty when none). */
	public static List<Component> unmetSelf(DiscipleEntity entity) {
		java.util.ArrayList<Component> out = new java.util.ArrayList<>();
		for (Requirement requirement : requirementsSelf(entity.owner(), entity.data(), entity.soulId())) {
			if (!requirement.ok()) {
				out.add(requirement.text());
			}
		}
		return out;
	}

	/** The next rank index (1 Luar ... 5 Tetua Agung), or -1 at the top. */
	public static int nextRank(DiscipleData data) {
		int rank = rank(data);
		return rank >= MAX_RANK ? -1 : rank + 1;
	}

	/**
	 * Murid Mandiri (DESIGN 39.5.5): the disciple's approved request promotes it one rank exactly like {@link #promote}
	 * (every requirement, its own PK at {@code PK_COST}, the hall registry, the robe flash and bell, E's "passed over")
	 * with the master nullable: the chat, the sect line and the advancements only when the master is online. True when
	 * promoted.
	 */
	public static boolean promoteSelf(DiscipleEntity entity) {
		ServerPlayer master = entity.owner();
		UUID owner = entity.ownerId();
		DiscipleData data = entity.data();
		if (owner == null || entity.role() != DiscipleEntity.Role.DISCIPLE || promoteBlockerSelf(entity) != null) {
			return false;
		}
		ensure(data);
		int rank = rank(data);
		int target = rank + 1;
		int cost = PK_COST[target];
		if (pk(data) < cost) {
			return false;
		}
		addPk(data, -cost, "kultivator.hall.pk.promotion");
		setRank(data, target);
		entity.setSyncedSectRank(target);
		Halls.noteRank(owner, entity.soulId(), target, isProtector(data), DisciplePaths.power(data));
		if (master != null) {
			celebrate(master, entity, target, true);
			passedOver(master, entity, rank);
			Advancements.grant(master, "first_promotion");
			if (target == TETUA_AGUNG) {
				Advancements.grant(master, "grand_elder");
			}
		} else if (entity.level() instanceof ServerLevel level) {
			Vec3 pos = entity.position();
			Fx.send(level, pos, FxType.HALL, HallFxIds.PROMOTION, TRIM[target], target);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 1.0F);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 1.3F);
			Halls.log(owner, Component.translatable("kultivator.hall.rank.promoted", entity.getName(), rankName(target)));
		}
		return true;
	}

	/** The master answers a disciple's request (a chat click): the old {@link #promote}, the Latihan Khusus included. */
	public static boolean promoteByMaster(ServerPlayer master, DiscipleEntity entity) {
		return entity != null && entity.isOwnedBy(master) && promote(master, entity, false);
	}

	/** Half a recruitment cycle: Fanren's retest of an unofficial disciple. */
	public static long retestTicks() {
		return KultivatorConfig.get().recruitmentIntervalDays * 24000L / 2L;
	}

	public static int maxElders(UUID owner) {
		return Halls.level(owner) >= TWO_ELDERS_LEVEL ? 2 : 1;
	}

	public static int maxProtectors(UUID owner) {
		return Halls.level(owner) >= TWO_PROTECTORS_LEVEL ? 2 : 1;
	}

	/**
	 * The master promotes the disciple one rank: every requirement must hold; Luar to Dalam first runs the Latihan Khusus
	 * (module B). Costs the disciple's own PK. {@code afterTrial}: called by a passed trial (no new trial is started).
	 */
	static boolean promote(ServerPlayer master, DiscipleEntity entity, boolean afterTrial) {
		DiscipleData data = entity.data();
		ensure(data);
		int rank = rank(data);
		if (rank >= MAX_RANK) {
			Msg.chat(master, Component.translatable("kultivator.hall.rank.top", entity.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return false;
		}
		int target = rank + 1;
		for (Requirement requirement : requirements(master, data, entity.soulId())) {
			if (requirement.trial() && !afterTrial) {
				continue;
			}
			if (!requirement.ok()) {
				Msg.chat(master, Component.translatable("kultivator.hall.rank.not_ready", entity.getDisplayName(), requirement.text())
						.withStyle(ChatFormatting.YELLOW));
				return false;
			}
		}
		if (target == DALAM && !trialPassed(data)) {
			if (afterTrial) {
				return false;
			}
			if (!Halls.nearStele(master.getUUID(), entity, 6.0D)) {
				Msg.chat(master, Component.translatable("kultivator.hall.rank.trial_at_stele", entity.getDisplayName()).withStyle(ChatFormatting.YELLOW));
				return false;
			}
			if (SectEvents.startPromotionTrial(entity, master)) {
				Msg.chat(master, Component.translatable("kultivator.hall.rank.trial_started", entity.getDisplayName()).withStyle(ChatFormatting.GOLD));
			} else {
				Msg.chat(master, Component.translatable("kultivator.hall.rank.trial_unavailable", entity.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			}
			return false;
		}
		int cost = PK_COST[target];
		if (pk(data) < cost) {
			Msg.chat(master, Component.translatable("kultivator.hall.rank.not_ready", entity.getDisplayName(),
					Component.translatable("kultivator.hall.req.pk", pk(data), cost)).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		addPk(data, -cost, "kultivator.hall.pk.promotion");
		setRank(data, target);
		entity.setSyncedSectRank(target);
		Halls.noteRank(master.getUUID(), entity.soulId(), target, isProtector(data), DisciplePaths.power(data));
		celebrate(master, entity, target, true);
		passedOver(master, entity, rank);
		Advancements.grant(master, "first_promotion");
		if (target == TETUA_AGUNG) {
			Advancements.grant(master, "grand_elder");
		}
		return true;
	}

	/**
	 * Sekte yang Hidup module E (DESIGN 36.2.6): a junior promoted past seniors. Every loaded disciple of the master that
	 * joined earlier, still stands at or below the promoted one's old rank and has a stronger master bond feels treated
	 * unfairly ({@code TIDAK_ADIL}, at most three per promotion, from Murid Dalam up). No number of the ranks changes.
	 */
	private static void passedOver(ServerPlayer master, DiscipleEntity promoted, int oldRank) {
		if (oldRank + 1 < DALAM || !dev.kultivator.sekte.SekteContent.on(dev.kultivator.sekte.SekteIds.E)) {
			return;
		}
		long joined = promoted.data().extra().getCompoundOrEmpty(EXTRA).getLongOr("joined", 0L);
		int bond = DisciplePaths.bond(promoted.data());
		int told = 0;
		for (DiscipleEntity other : Disciples.loaded(master.getUUID())) {
			if (other == promoted || other.role() != DiscipleEntity.Role.DISCIPLE || told >= 3) {
				continue;
			}
			DiscipleData data = other.data();
			if (rank(data) <= oldRank && data.extra().getCompoundOrEmpty(EXTRA).getLongOr("joined", Long.MAX_VALUE) < joined
					&& DisciplePaths.bond(data) > bond) {
				dev.kultivator.sekte.api.SekteBus.mistreated(master.level().getServer(), master.getUUID(), other.soulId(),
						dev.kultivator.sekte.api.Perlakuan.TIDAK_ADIL, "tetua.promosi");
				told++;
			}
		}
	}

	/** Demotion is free, refunds nothing and stops at Tercatat. */
	static boolean demote(ServerPlayer master, DiscipleEntity entity) {
		DiscipleData data = entity.data();
		int rank = rank(data);
		if (rank <= TERCATAT) {
			Msg.chat(master, Component.translatable("kultivator.hall.rank.bottom", entity.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return false;
		}
		setRank(data, rank - 1);
		entity.setSyncedSectRank(rank - 1);
		Halls.noteRank(master.getUUID(), entity.soulId(), rank - 1, isProtector(data), DisciplePaths.power(data));
		celebrate(master, entity, rank - 1, false);
		// Sekte yang Hidup module E (DESIGN 36.2.6): a demotion shames the disciple in front of the sect (P hears it).
		if (dev.kultivator.sekte.SekteContent.on(dev.kultivator.sekte.SekteIds.E)) {
			dev.kultivator.sekte.api.SekteBus.mistreated(master.level().getServer(), master.getUUID(), entity.soulId(),
					dev.kultivator.sekte.api.Perlakuan.DIPERMALUKAN, "tetua.turun");
		}
		if (rank - 1 == TERCATAT && entity.mode() != DiscipleEntity.Mode.FOLLOW && entity.mode() != DiscipleEntity.Mode.MEDITATE) {
			entity.setMode(DiscipleEntity.Mode.FOLLOW);
		}
		return true;
	}

	/** Appoints or relieves a Pelindung Sekte: Dao Union or a star, at most 1 (2 from hall level 7); the post is Jaga Aula. */
	static boolean toggleProtector(ServerPlayer master, DiscipleEntity entity) {
		DiscipleData data = entity.data();
		ensure(data);
		CompoundTag tag = data.extraSection(EXTRA);
		if (isProtector(data)) {
			tag.putBoolean("protector", false);
			if (entity.mode() == DiscipleEntity.Mode.GUARD_HALL) {
				entity.setMode(DiscipleEntity.Mode.FOLLOW);
			}
			Halls.noteRank(master.getUUID(), entity.soulId(), rank(data), false, DisciplePaths.power(data));
			Msg.chat(master, Component.translatable("kultivator.hall.protector.relieved", entity.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return true;
		}
		Component why = protectorBlocker(master, data, entity.soulId());
		if (why != null) {
			Msg.chat(master, why.copy().withStyle(ChatFormatting.YELLOW));
			return false;
		}
		tag.putBoolean("protector", true);
		Halls.noteRank(master.getUUID(), entity.soulId(), rank(data), true, DisciplePaths.power(data));
		entity.setMode(DiscipleEntity.Mode.GUARD_HALL);
		if (entity.level() instanceof ServerLevel level) {
			Fx.send(level, entity.position(), FxType.HALL, HallFxIds.PROMOTION, 0xFFD54A, 7.0F);
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 0.8F);
		}
		Msg.chat(master, Component.translatable("kultivator.hall.protector.appointed", entity.getDisplayName()).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Why the disciple cannot be appointed Pelindung now, or null. */
	static @Nullable Component protectorBlocker(ServerPlayer master, DiscipleData data, UUID soul) {
		if (!data.realm().atLeast(Realm.DAO_UNION) && DisciplePaths.stars(data) < 1) {
			return Component.translatable("kultivator.hall.protector.need_realm");
		}
		int max = maxProtectors(master.getUUID());
		if (Halls.countProtectors(master, soul) >= max) {
			return Component.translatable("kultivator.hall.protector.full", max);
		}
		return null;
	}

	/** The robe flash, the bell and the sect chat line of a rank change. */
	private static void celebrate(ServerPlayer master, DiscipleEntity entity, int rank, boolean up) {
		if (entity.level() instanceof ServerLevel level) {
			Vec3 pos = entity.position();
			Fx.send(level, pos, FxType.HALL, HallFxIds.PROMOTION, TRIM[rank], rank);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, up ? 1.0F : 0.6F, up ? 1.0F : 0.7F);
			if (up) {
				level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.5F, 1.3F);
			}
		}
		Component line = Component.translatable(up ? "kultivator.hall.rank.promoted" : "kultivator.hall.rank.demoted", entity.getDisplayName(),
				rankName(rank));
		Msg.chat(master, line.copy().withStyle(up ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		if (up && master.level().getServer() != null) {
			Sect sect = SectData.get(master.level().getServer()).sectOf(master.getUUID());
			if (sect != null) {
				Set<UUID> except = new HashSet<>();
				except.add(master.getUUID());
				Sects.tell(master.level().getServer(), sect, Msg.prefixed(line), except);
			}
		}
	}

	/** Anugerah Ketua: moves pool PK to the disciple 1:1 (Martial Peak). */
	static boolean grant(ServerPlayer master, DiscipleEntity entity, int amount) {
		if (amount <= 0 || !Halls.spendPool(master.getUUID(), amount)) {
			Msg.chat(master, Component.translatable("kultivator.hall.pk.pool_short", amount).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		addPk(entity.data(), amount, "kultivator.hall.pk.grant");
		Halls.log(master.getUUID(), Component.translatable("kultivator.hall.log.grant", entity.getName(), amount));
		Msg.chat(master, Component.translatable("kultivator.hall.pk.granted", amount, entity.getDisplayName()).withStyle(ChatFormatting.AQUA));
		if (entity.level() instanceof ServerLevel level) {
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.4F);
		}
		return true;
	}

	/** The checklist as tag lines {ok, text} for the screen. */
	static ListTag requirementTags(ServerPlayer master, DiscipleData data, UUID soul) {
		ListTag list = new ListTag();
		for (Requirement requirement : requirements(master, data, soul)) {
			CompoundTag line = new CompoundTag();
			line.putBoolean("ok", requirement.ok());
			line.store("text", ComponentSerialization.CODEC, requirement.text());
			list.add(line);
		}
		return list;
	}

	private SectRanks() {
	}
}
