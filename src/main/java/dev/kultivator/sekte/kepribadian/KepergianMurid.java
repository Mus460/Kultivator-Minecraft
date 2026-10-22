package dev.kultivator.sekte.kepribadian;

import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.epic.EpicMoments;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.HatiService.Keadaan;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.Kepergian;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.sekte.api.Sikap;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a disciple leaves the sect (DESIGN 36.2.1, {@code api.Kepergian}), always only after the warnings of rule 36.0.4
 * (Gelisah, Membangkang, then {@code hariPergi} days below the threshold), never a soul-sealed disciple or the heir,
 * never while it is away, breaking through or in a duel, and only while its master is online:
 * <ul>
 * <li><b>Berkhianat</b>: a Licik heart while an NPC sect is hostile or at war, or any heart an NPC sect lured in the last
 * ten days (module D's {@code MEREKRUT}); it leaves at once in black smoke and module D takes it in.</li>
 * <li><b>Memberontak</b>: a Berani or Sombong heart walks up to its master and challenges them to an honour duel (the
 * Sword Duel of DESIGN 35, stopped at 10%, nobody dies). The master wins: it submits (contentment 40, a memory "Tunduk").
 * The master loses, refuses or lets a minute pass: it leaves, and a sworn sibling below 50 loyalty may go with it.</li>
 * <li><b>Minggat</b>: everyone else waits for the night and walks out of the gate with a bundle.</li>
 * <li><b>Mengembara</b>: an Ambisius heart at or above its master's realm and still content asks leave to wander; the
 * master allows (it leaves in peace and may come home) or refuses (it feels shamed).</li>
 * <li><b>Diusir</b>: the master's "Usir dari Sekte" (confirmed twice); everyone else loses 3 contentment (Penyayang 6).</li>
 * </ul>
 * The body goes through {@code Disciples.depart} (reason {@code sekte_pergi}), the master's list frees its place, and
 * the tablet data waits in {@link YangPergi}. Server thread only.
 * <p>
 * Sumber: Han Feng, Yao Lao's disciple who betrayed his master (<i>Battle Through the Heavens</i>).
 */
public final class KepergianMurid {

	/** Away reason of a departed disciple (also the {@code Disciples.depart} reason). */
	public static final String REASON = "sekte_pergi";
	/** A challenge or a wander request waits this long for the master's answer (ticks). */
	public static final int ANSWER_TICKS = 1200;
	/** A deserter walks this far out, and gives up walking after this long. */
	public static final int WALK_DISTANCE = 20;
	public static final int WALK_TICKS = 600;
	public static final int LEAVING_POSE_TICKS = 40;
	/** The rebel looks for its master this long before it slips away instead (ticks: one in-game day). */
	public static final int REBEL_PATIENCE = 24000;
	public static final int MAX_DUEL_RETRIES = 3;
	/** A lure of module D counts this many days. */
	public static final int LURE_DAYS = 10;

	/** The order is the Hati tab's {@code pend} value minus one (lang {@code tab.pend.1-6}). */
	enum Fase {
		TANTANG, DUEL, JALAN, PAMIT_POSE, PAMIT, SEGERA
	}

	static final class Pending {
		final UUID owner;
		final UUID soul;
		Fase fase;
		Kepergian how;
		@Nullable String toSect;
		long since;
		long deadline;
		boolean offered;
		boolean decided;
		boolean masterWon;
		int retries;
		/** A test start: the deserter does not wait for the night, and kindness meanwhile does not stop it. */
		boolean anyTime;
		@Nullable BlockPos exit;

		Pending(UUID owner, UUID soul, Fase fase, Kepergian how, long now) {
			this.owner = owner;
			this.soul = soul;
			this.fase = fase;
			this.how = how;
			this.since = now;
			this.deadline = now + ANSWER_TICKS;
		}
	}

	private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();

	static boolean busy(UUID soul) {
		return PENDING.containsKey(soul);
	}

	static @Nullable Pending pending(UUID soul) {
		return PENDING.get(soul);
	}

	static void clear() {
		PENDING.clear();
	}

	// ================================================================== checks

	/** Why the disciple cannot leave right now (a lang key), or null. */
	static @Nullable String blocker(DiscipleEntity e, @Nullable ServerPlayer master, boolean expel) {
		KepribadianConfig cfg = KepribadianConfig.get();
		if (!cfg.aktif) {
			return "kultivator.sekte.kepribadian.blok.mati";
		}
		if (!expel && !cfg.bolehPergi) {
			return "kultivator.sekte.kepribadian.blok.boleh";
		}
		if (master == null || master.hasDisconnected()) {
			return "kultivator.sekte.kepribadian.blok.guru";
		}
		DiscipleData data = e.data();
		if (Kesetiaan.bound(data)) {
			return "kultivator.sekte.kepribadian.blok.terikat";
		}
		if (e.role() != DiscipleEntity.Role.DISCIPLE || e.isRemoved() || !e.isAlive() || e.isReleasing() || e.departed()) {
			return "kultivator.sekte.kepribadian.blok.tubuh";
		}
		if (data.breakingThrough()) {
			return "kultivator.sekte.kepribadian.blok.terobosan";
		}
		if (DuelSessions.byEntity(e) != null || DuelRules.inDuel(e)) {
			return "kultivator.sekte.kepribadian.blok.duel";
		}
		return null;
	}

	// ================================================================== the start

	/** The disciple has been below the threshold long enough: choose how it goes and start (no-op when busy). */
	static void begin(MinecraftServer server, DiscipleEntity e, ServerPlayer master) {
		UUID soul = e.soulId();
		if (busy(soul) || blocker(e, master, false) != null) {
			return;
		}
		long now = server.overworld().getGameTime();
		DiscipleData data = e.data();
		String sect = defectTarget(server, master.getUUID(), data);
		if (sect != null) {
			// Left on the next departure tick, never inside the disciple's own tick.
			Pending p = new Pending(master.getUUID(), soul, Fase.SEGERA, Kepergian.BERKHIANAT, now);
			p.toSect = sect;
			PENDING.put(soul, p);
			return;
		}
		if (Hati.has(data, Sifat.BERANI) || Hati.has(data, Sifat.SOMBONG)) {
			Pending p = new Pending(master.getUUID(), soul, Fase.TANTANG, Kepergian.MEMBERONTAK, now);
			p.deadline = now + REBEL_PATIENCE;
			PENDING.put(soul, p);
			return;
		}
		Pending p = new Pending(master.getUUID(), soul, Fase.JALAN, Kepergian.MINGGAT, now);
		p.deadline = Long.MAX_VALUE;
		PENDING.put(soul, p);
	}

	/**
	 * Test start ({@code uji perguruan kepribadian pergi}): the chosen way at once, skipping the warning days (the other
	 * checks stay). Returns a lang key of why it could not start, or null.
	 */
	static @Nullable String forceStart(MinecraftServer server, DiscipleEntity e, ServerPlayer master, Kepergian how) {
		String why = blocker(e, master, how == Kepergian.DIUSIR);
		if (why != null) {
			return why;
		}
		PENDING.remove(e.soulId());
		long now = server.overworld().getGameTime();
		switch (how) {
			case BERKHIANAT -> {
				List<String> sects = SekteApi.diplomasi().sects();
				String sect = defectTarget(server, master.getUUID(), e.data());
				leave(server, master, e, how, sect != null ? sect : sects.isEmpty() ? null : sects.get(0), null);
			}
			case MEMBERONTAK -> {
				Pending p = new Pending(master.getUUID(), e.soulId(), Fase.TANTANG, how, now);
				p.deadline = now + REBEL_PATIENCE;
				p.anyTime = true;
				PENDING.put(e.soulId(), p);
			}
			case MINGGAT -> {
				Pending p = new Pending(master.getUUID(), e.soulId(), Fase.JALAN, how, now);
				p.deadline = Long.MAX_VALUE;
				p.anyTime = true;
				PENDING.put(e.soulId(), p);
			}
			case MENGEMBARA -> {
				Hati.putLong(e.data(), "kembara", Long.MIN_VALUE / 4);
				askWander(server, e, master);
			}
			case DIUSIR -> expel(server, master, e);
		}
		return null;
	}

	/** The NPC sect it would defect to: the sect that lured it lately, or (Licik) the most hostile one; null for none. */
	static @Nullable String defectTarget(MinecraftServer server, UUID owner, DiscipleData data) {
		List<String> sects = SekteApi.diplomasi().sects();
		if (sects.isEmpty()) {
			return null;
		}
		String lure = Hati.getString(data, "bujuk");
		long lureDay = Hati.getLong(data, "bujukDay", Long.MIN_VALUE / 4);
		if (!lure.isEmpty() && Ingatan.today(server) - lureDay <= LURE_DAYS && sects.contains(lure)) {
			return lure;
		}
		if (!Hati.has(data, Sifat.LICIK)) {
			return null;
		}
		String worst = null;
		int lowest = Integer.MAX_VALUE;
		for (String id : sects) {
			Sikap stance = SekteApi.diplomasi().stance(owner, id);
			if (stance != Sikap.BERMUSUHAN && stance != Sikap.PERANG) {
				continue;
			}
			int standing = SekteApi.diplomasi().standing(owner, id);
			if (standing < lowest) {
				lowest = standing;
				worst = id;
			}
		}
		return worst;
	}

	/** An ambitious disciple asks leave to wander (DESIGN 36.2.1); the master answers in chat. */
	static void askWander(MinecraftServer server, DiscipleEntity e, ServerPlayer master) {
		UUID soul = e.soulId();
		if (busy(soul) || blocker(e, master, false) != null || master.level() != e.level() || master.distanceToSqr(e) > 48.0D * 48.0D) {
			return;
		}
		long now = server.overworld().getGameTime();
		Pending p = new Pending(master.getUUID(), soul, Fase.PAMIT, Kepergian.MENGEMBARA, now);
		PENDING.put(soul, p);
		Hati.putLong(e.data(), "kembara", Ingatan.today(server));
		e.getLookControl().setLookAt(master, 30.0F, 30.0F);
		VoiceDisciples.line(e, "careful", 200);
		Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.pamit.minta", e.getDisplayName(),
				button("kultivator.sekte.kepribadian.pamit.izinkan", "/kultivasi perguruan murid izin ya", ChatFormatting.GREEN),
				button("kultivator.sekte.kepribadian.pamit.tolak", "/kultivasi perguruan murid izin tidak", ChatFormatting.RED))
				.withStyle(ChatFormatting.GOLD));
	}

	// ================================================================== answers (player commands)

	/** {@code /kultivasi perguruan murid tantangan terima|tolak}: the newest challenge of {@code master}. */
	static int answerChallenge(ServerPlayer master, boolean accept) {
		Pending p = newest(master.getUUID(), Fase.TANTANG, true);
		DiscipleEntity e = p == null ? null : body(p);
		if (p == null || e == null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.tantang.tidak_ada").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		MinecraftServer server = master.level().getServer();
		if (!accept) {
			Ingatan.remember(server, e.data(), SekteIds.P + ".ditolak_tantang", master.getUUID(), -3,
					"kultivator.sekte.kepribadian.ingat.ditolak_tantang", "");
			leave(server, master, e, Kepergian.MEMBERONTAK, null, companion(server, master.getUUID(), e));
			return 1;
		}
		Component why = DuelChallenges.selfProblem(master);
		if (why == null) {
			int gap = e.data().realm().ordinal() - Cultivators.get(master).realm().ordinal();
			DuelRuleSet base = DuelRuleSet.suggested(gap);
			DuelRuleSet rules = new DuelRuleSet(base.realm(), DuelRuleSet.JurusRule.PEDANG_SAJA, DuelRuleSet.WinRule.SEPULUH_PERSEN, 9,
					2400, true, DuelRuleSet.StakeKind.KEHORMATAN, 0, 0, null, true);
			e.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			why = DuelChallenges.startDirect(master, master, e, DuelIds.PAIR_MURID, rules);
		}
		if (why != null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.tantang.gagal", why).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		p.fase = Fase.DUEL;
		p.decided = false;
		Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.tantang.mulai", e.getDisplayName()).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** {@code /kultivasi perguruan murid izin ya|tidak}: the newest wander request of {@code master}. */
	static int answerWander(ServerPlayer master, boolean allow) {
		Pending p = newest(master.getUUID(), Fase.PAMIT, false);
		DiscipleEntity e = p == null ? null : body(p);
		if (p == null || e == null) {
			Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.pamit.tidak_ada").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		MinecraftServer server = master.level().getServer();
		if (!allow) {
			PENDING.remove(p.soul);
			SekteBus.DISCIPLE_MISTREATED.invoker().onMistreated(server, master.getUUID(), p.soul, Perlakuan.DIPERMALUKAN, 3,
					SekteIds.P + ".tolak_kembara");
			Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.pamit.ditolak", e.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return 1;
		}
		// A quiet bow before it goes (the pose of a deserter, without the night).
		p.fase = Fase.PAMIT_POSE;
		p.deadline = server.overworld().getGameTime() + LEAVING_POSE_TICKS;
		e.setMode(DiscipleEntity.Mode.STAY);
		e.setActivity(SekteIds.ACT_LEAVING, null);
		e.getLookControl().setLookAt(master, 30.0F, 30.0F);
		VoiceDisciples.line(e, "farewell", 0);
		return 1;
	}

	private static @Nullable Pending newest(UUID owner, Fase fase, boolean offeredOnly) {
		Pending found = null;
		for (Pending p : PENDING.values()) {
			if (p.owner.equals(owner) && p.fase == fase && (!offeredOnly || p.offered) && (found == null || p.since >= found.since)) {
				found = p;
			}
		}
		return found;
	}

	// ================================================================== the tick (every 10 ticks)

	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		List<Runnable> after = new ArrayList<>();
		Iterator<Pending> it = PENDING.values().iterator();
		while (it.hasNext()) {
			Pending p = it.next();
			ServerPlayer master = server.getPlayerList().getPlayer(p.owner);
			DiscipleEntity e = body(p);
			if (e == null || master == null) {
				// Unloaded or the master left: start over on its next tick20 (the counter is kept in its data).
				it.remove();
				continue;
			}
			if (!p.anyTime && p.fase != Fase.DUEL && p.fase != Fase.PAMIT && p.fase != Fase.PAMIT_POSE && p.fase != Fase.SEGERA
					&& Kesetiaan.state(e.data()) != Keadaan.AKAN_PERGI) {
				// Cared for in time: it stays.
				it.remove();
				resetWalk(e);
				continue;
			}
			switch (p.fase) {
				case TANTANG -> tickChallenge(server, p, e, master, now, after);
				case DUEL -> tickDuel(server, p, e, master, after);
				case JALAN -> tickWalk(server, p, e, master, now, after);
				case SEGERA -> after.add(() -> leave(server, master, e, p.how, p.toSect, null));
				case PAMIT_POSE -> {
					if (now >= p.deadline) {
						after.add(() -> leave(server, master, e, Kepergian.MENGEMBARA, null, null));
					}
				}
				case PAMIT -> {
					if (now >= p.deadline) {
						it.remove();
						Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.pamit.lewat", e.getDisplayName()).withStyle(ChatFormatting.GRAY));
					}
				}
			}
		}
		for (Runnable run : after) {
			run.run();
		}
	}

	private static void tickChallenge(MinecraftServer server, Pending p, DiscipleEntity e, ServerPlayer master, long now, List<Runnable> after) {
		boolean near = master.level() == e.level() && master.distanceToSqr(e) <= 32.0D * 32.0D;
		if (!p.offered) {
			if (now >= p.deadline) {
				// Never met its master in a whole day: it slips away instead.
				p.fase = Fase.JALAN;
				p.how = Kepergian.MINGGAT;
				p.deadline = Long.MAX_VALUE;
				return;
			}
			// Never in real danger (rule 36.0.6): not within 10 s of a mob's blow, not in a duel, not asleep.
			boolean danger = master.getLastHurtByMobTimestamp() > 0 && master.tickCount - master.getLastHurtByMobTimestamp() < 200
					|| DuelChallenges.selfProblem(master) != null || master.isSleeping();
			if (!near || danger) {
				return;
			}
			p.offered = true;
			p.deadline = now + ANSWER_TICKS;
			e.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
			if (!e.mode().follows()) {
				e.setMode(DiscipleEntity.Mode.FOLLOW);
			}
			e.getLookControl().setLookAt(master, 30.0F, 30.0F);
			VoiceDisciples.line(e, "rebel", 0);
			bubble(e, KepribadianPayloads.ICON_STORM);
			Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.tantang.ajak", e.getDisplayName(),
					button("kultivator.sekte.kepribadian.tantang.terima", "/kultivasi perguruan murid tantangan terima", ChatFormatting.GREEN),
					button("kultivator.sekte.kepribadian.tantang.tolak", "/kultivasi perguruan murid tantangan tolak", ChatFormatting.RED))
					.withStyle(ChatFormatting.RED));
			if (e.level() instanceof ServerLevel level) {
				EpicMoments.send(level, EpicIds.EXTRA, Kepribadian.BEAT_REBEL, e, master, e.position(), level.getGameTime(), 0, 0, 0.0F, 3, 0);
			}
			SekteLog.log(server, p.owner, SekteIds.P, Component.translatable("kultivator.sekte.kepribadian.log.tantang", e.getDisplayName()));
			return;
		}
		if (now >= p.deadline) {
			Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.tantang.lewat", e.getDisplayName()).withStyle(ChatFormatting.GRAY));
			after.add(() -> leave(server, master, e, Kepergian.MEMBERONTAK, null, companion(server, p.owner, e)));
		}
	}

	private static void tickDuel(MinecraftServer server, Pending p, DiscipleEntity e, ServerPlayer master, List<Runnable> after) {
		DuelSession session = DuelSessions.byEntity(e);
		if (session != null) {
			if (session.decided() && !p.decided) {
				p.decided = true;
				// No public winner accessor: the duel stops the loser at its 10% line, so the higher health share won.
				p.masterWon = share(master) >= share(e);
			}
			return;
		}
		if (p.decided) {
			// Removed after the loop (never while iterating PENDING): leave() removes it itself, the submit runnable here.
			if (p.masterWon) {
				after.add(() -> {
					PENDING.remove(p.soul);
					submit(server, master, e);
				});
			} else {
				after.add(() -> leave(server, master, e, Kepergian.MEMBERONTAK, null, companion(server, p.owner, e)));
			}
			return;
		}
		// Cancelled before a result (someone left the ring, the server was full): it asks again a little later.
		p.retries++;
		if (p.retries >= MAX_DUEL_RETRIES) {
			after.add(() -> leave(server, master, e, Kepergian.MEMBERONTAK, null, companion(server, p.owner, e)));
			return;
		}
		long now = server.overworld().getGameTime();
		p.fase = Fase.TANTANG;
		p.offered = false;
		p.deadline = now + REBEL_PATIENCE;
	}

	private static void tickWalk(MinecraftServer server, Pending p, DiscipleEntity e, ServerPlayer master, long now, List<Runnable> after) {
		if (!(e.level() instanceof ServerLevel level)) {
			return;
		}
		if (p.exit == null) {
			long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
			boolean night = time >= 13000L && time < 23000L;
			if (!night && !p.anyTime) {
				return;
			}
			p.exit = exitSpot(level, e, p.owner);
			p.since = now;
			e.setActivity(SekteIds.ACT_SULK, p.exit);
			return;
		}
		boolean arrived = e.distanceToSqr(Vec3.atBottomCenterOf(p.exit)) <= 2.5D * 2.5D;
		if (e.activity() != SekteIds.ACT_LEAVING && (arrived || now - p.since > WALK_TICKS)) {
			e.setMode(DiscipleEntity.Mode.STAY);
			e.setActivity(SekteIds.ACT_LEAVING, null);
			p.deadline = now + LEAVING_POSE_TICKS;
			VoiceDisciples.line(e, "leave_night", 0);
			return;
		}
		if (e.activity() == SekteIds.ACT_LEAVING && now >= p.deadline) {
			after.add(() -> leave(server, master, e, Kepergian.MINGGAT, null, null));
		}
	}

	/** A spot {@link #WALK_DISTANCE} blocks further from the hall stele (or the master), on the ground. */
	private static BlockPos exitSpot(ServerLevel level, DiscipleEntity e, UUID owner) {
		Vec3 from = e.position();
		Vec3 centre = null;
		Halls.HallView hall = Halls.hallOf(owner);
		if (hall != null && level.dimension().equals(hall.dim())) {
			centre = Vec3.atBottomCenterOf(hall.pos());
		} else {
			ServerPlayer master = level.getServer().getPlayerList().getPlayer(owner);
			if (master != null && master.level() == level) {
				centre = master.position();
			}
		}
		double dx = centre == null ? 1.0D : from.x - centre.x;
		double dz = centre == null ? 0.0D : from.z - centre.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 0.5D) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
			dx = Math.cos(angle);
			dz = Math.sin(angle);
			len = 1.0D;
		}
		int x = (int) Math.floor(from.x + dx / len * WALK_DISTANCE);
		int z = (int) Math.floor(from.z + dz / len * WALK_DISTANCE);
		int y = level.isLoaded(new BlockPos(x, e.getBlockY(), z)) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
				: e.getBlockY();
		return new BlockPos(x, y, z);
	}

	private static void resetWalk(DiscipleEntity e) {
		if (e.activity() == SekteIds.ACT_LEAVING || e.activity() == SekteIds.ACT_SULK) {
			e.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
	}

	private static float share(net.minecraft.world.entity.LivingEntity entity) {
		float max = entity.getMaxHealth();
		return max <= 0.0F ? 0.0F : entity.getHealth() / max;
	}

	/** The master won the rebel's duel: the disciple bows its head and stays (contentment 40, a memory "Tunduk"). */
	static void submit(MinecraftServer server, ServerPlayer master, DiscipleEntity e) {
		DiscipleData data = e.data();
		Hati.setContentment(data, 40.0D);
		Hati.putInt(data, "low", 0);
		Hati.putLong(data, "last", Ingatan.today(server));
		Ingatan.remember(server, data, SekteIds.P + ".tunduk", master.getUUID(), 6, "kultivator.sekte.kepribadian.ingat.tunduk",
				master.getName().getString());
		VoiceDisciples.line(e, "submit", 0);
		bubble(e, KepribadianPayloads.ICON_HEART);
		Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.tantang.tunduk", e.getDisplayName()).withStyle(ChatFormatting.GREEN));
		SekteLog.log(server, master.getUUID(), SekteIds.P, Component.translatable("kultivator.sekte.kepribadian.log.tunduk", e.getDisplayName()));
		if (e.level() instanceof ServerLevel level) {
			EpicMoments.send(level, EpicIds.EXTRA, Kepribadian.BEAT_SUBMIT, e, master, e.position(), level.getGameTime(), 0, 0, 0.0F, 2, 0);
		}
		Kepribadian.afterChange(e, true);
	}

	/** A sworn sibling below 50 loyalty that goes with a rebel (50%), or null. */
	static @Nullable UUID companion(MinecraftServer server, UUID owner, DiscipleEntity rebel) {
		for (UUID kin : SekteApi.hubungan().with(owner, rebel.soulId(), Ikatan.SAUDARA_ANGKAT)) {
			DiscipleEntity other = Kepribadian.loaded(owner, kin);
			if (other != null && Kesetiaan.loyalty(other.data()) < 50 && rebel.getRandom().nextBoolean()
					&& blocker(other, server.getPlayerList().getPlayer(owner), false) == null) {
				return kin;
			}
		}
		return null;
	}

	// ================================================================== the departure itself

	/**
	 * The disciple leaves: farewell effects, {@code Disciples.depart}, its place in the master's list freed, the tablet kept
	 * in {@link YangPergi}, a hall-log line, {@code SekteBus.DISCIPLE_LEFT}. A companion (sworn sibling) follows the same
	 * way. False when it could not leave (the blocker's line goes to the master).
	 */
	static boolean leave(MinecraftServer server, ServerPlayer master, DiscipleEntity e, Kepergian how, @Nullable String toSect,
			@Nullable UUID companion) {
		PENDING.remove(e.soulId());
		String why = blocker(e, master, how == Kepergian.DIUSIR);
		if (why != null) {
			Msg.chat(master, Component.translatable(why, e.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		UUID owner = master.getUUID();
		UUID soul = e.soulId();
		Component name = e.getDisplayName();
		long today = Ingatan.today(server);
		farewellFx(e, how);
		if (how == Kepergian.MENGEMBARA) {
			VoiceDisciples.line(e, "farewell", 0);
		}
		CompoundTag tablet = Disciples.depart(e, REASON);
		if (tablet.isEmpty()) {
			return false;
		}
		freeSlot(master, soul);
		YangPergi.add(server, owner, soul, name, how, today, tablet, toSect);
		Component sectName = toSect == null ? null : SekteApi.diplomasi().name(toSect);
		String key = "kultivator.sekte.kepribadian.pergi." + how.id();
		Msg.chat(master, Component.translatable(key, name, sectName == null ? Component.literal(toSect == null ? "" : toSect) : sectName)
				.withStyle(how == Kepergian.MENGEMBARA ? ChatFormatting.GOLD : ChatFormatting.RED));
		SekteLog.log(server, owner, SekteIds.P, Component.translatable("kultivator.sekte.kepribadian.log.pergi", name,
				Component.translatable(how.key())));
		if (how != Kepergian.DIUSIR && how != Kepergian.MENGEMBARA && master.level() == e.level() && master.distanceToSqr(e) <= 48.0D * 48.0D) {
			EpicMoments.send(master.level(), EpicIds.EXTRA, Kepribadian.BEAT_LEFT, null, master, e.position(), master.level().getGameTime(),
					0, 0, 0.0F, 2, 0);
		}
		SekteBus.DISCIPLE_LEFT.invoker().onLeft(server, owner, soul, name, how, toSect, companion);
		grieve(server, owner, soul, name, how);
		if (companion != null) {
			DiscipleEntity kin = Kepribadian.loaded(owner, companion);
			if (kin != null) {
				Ingatan.remember(server, kin.data(), SekteIds.P + ".ikut_pergi", soul, -4, "kultivator.sekte.kepribadian.ingat.ikut_pergi",
						name.getString());
				leave(server, master, kin, how, toSect, null);
			}
		}
		return true;
	}

	/** The master expels {@code e} ("Usir dari Sekte", confirmed twice on the screen). */
	static boolean expel(MinecraftServer server, ServerPlayer master, DiscipleEntity e) {
		if (Kesetiaan.bound(e.data())) {
			Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.blok.terikat", e.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		UUID soul = e.soulId();
		Component name = e.getDisplayName();
		if (!leave(server, master, e, Kepergian.DIUSIR, null, null)) {
			return false;
		}
		long today = Ingatan.today(server);
		for (DiscipleEntity other : Disciples.loaded(master.getUUID())) {
			if (other.role() != DiscipleEntity.Role.DISCIPLE || !Hati.known(other.data())) {
				continue;
			}
			double loss = Hati.has(other.data(), Sifat.PENYAYANG) ? 6.0D : 3.0D;
			Kepuasan.change(other.data(), today, -loss);
			Ingatan.remember(server, other.data(), SekteIds.P + ".usir", soul, -2, "kultivator.sekte.kepribadian.ingat.usir", name.getString());
			Kepribadian.afterChange(other, false);
		}
		return true;
	}

	/** Friends and kin of a disciple that left grieve a little (DESIGN 36.2.1: a sect is not indifferent). */
	private static void grieve(MinecraftServer server, UUID owner, UUID soul, Component name, Kepergian how) {
		if (how == Kepergian.DIUSIR) {
			return;
		}
		long today = Ingatan.today(server);
		for (Ikatan tie : new Ikatan[]{Ikatan.SAHABAT, Ikatan.SAUDARA_ANGKAT, Ikatan.KEKASIH, Ikatan.PASANGAN_DAO}) {
			for (UUID friend : SekteApi.hubungan().with(owner, soul, tie)) {
				DiscipleEntity other = Kepribadian.loaded(owner, friend);
				if (other == null || !Hati.known(other.data())) {
					continue;
				}
				Kepuasan.change(other.data(), today, -Kepuasan.GRIEF_FRIEND_LEFT);
				Ingatan.remember(server, other.data(), SekteIds.P + ".teman_pergi", soul, -3, "kultivator.sekte.kepribadian.ingat.teman_pergi",
						name.getString());
				Kepribadian.afterChange(other, false);
			}
		}
	}

	private static void farewellFx(DiscipleEntity e, Kepergian how) {
		if (!(e.level() instanceof ServerLevel level)) {
			return;
		}
		double x = e.getX();
		double y = e.getY() + 0.9D;
		double z = e.getZ();
		switch (how) {
			case BERKHIANAT -> {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 30, 0.4D, 0.8D, 0.4D, 0.02D);
				level.sendParticles(ParticleTypes.SOUL, x, y, z, 10, 0.3D, 0.6D, 0.3D, 0.02D);
				level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6F, 0.6F);
			}
			case MINGGAT -> {
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 6, 0.3D, 0.4D, 0.3D, 0.01D);
				level.playSound(null, x, y, z, SoundEvents.WOOL_STEP, SoundSource.NEUTRAL, 0.5F, 0.8F);
			}
			case MENGEMBARA -> {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 12, 0.4D, 0.6D, 0.4D, 0.02D);
				level.playSound(null, x, y, z, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.5F, 1.3F);
			}
			default -> {
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, x, y + 0.6D, z, 5, 0.3D, 0.2D, 0.3D, 0.02D);
				level.sendParticles(ParticleTypes.CLOUD, x, y, z, 16, 0.4D, 0.6D, 0.4D, 0.02D);
				level.playSound(null, x, y, z, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.5F, 0.7F);
			}
		}
	}

	/**
	 * Frees the disciple's place in the master's list (the list entry {@code Disciples.depart} left "away"): a disciple
	 * that left the sect no longer counts toward the limit. The list lives in the master's cultivation data under
	 * {@code Disciples.EXTRA} / {@code list} (the format {@code Disciples} reads), and the away entry is removed by soul.
	 */
	static void freeSlot(ServerPlayer master, UUID soul) {
		CompoundTag root = Cultivators.get(master).extra().getCompound(Disciples.EXTRA).orElse(null);
		if (root == null) {
			return;
		}
		ListTag list = root.getListOrEmpty("list");
		String id = soul.toString();
		boolean removed = false;
		for (int i = list.size() - 1; i >= 0; i--) {
			if (list.get(i) instanceof CompoundTag entry && id.equals(entry.getStringOr("id", ""))) {
				list.remove(i);
				removed = true;
			}
		}
		if (removed) {
			root.put("list", list);
			Cultivators.dirty(master);
		}
	}

	// ================================================================== helpers

	static @Nullable DiscipleEntity body(Pending p) {
		return Kepribadian.loaded(p.owner, p.soul);
	}

	static void bubble(DiscipleEntity e, int icon) {
		KepribadianPayloads.pop(e, icon);
	}

	private static Component button(String key, String command, ChatFormatting color) {
		MutableComponent text = Component.translatable(key);
		return text.withStyle(style -> style.withColor(color).withBold(true).withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	private KepergianMurid() {
	}
}
