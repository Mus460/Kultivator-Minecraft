package dev.kultivator.sekte.kepribadian;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.duel.session.DuelSessions;
import dev.kultivator.harta.HartaComponents;
import dev.kultivator.harta.SpiritHerbItem;
import dev.kultivator.harta.SpiritStoneItem;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.HatiService;
import dev.kultivator.sekte.api.HatiService.Keadaan;
import dev.kultivator.sekte.api.Ikatan;
import dev.kultivator.sekte.api.Kenangan;
import dev.kultivator.sekte.api.Perlakuan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.Sifat;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceDisciples;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module P, Kepribadian &amp; Kesetiaan (DESIGN 36.2.1 and 36.4, items 36 and 38): every disciple gets a heart: 2-3
 * traits ({@link Watak}), a contentment that moves once a day ({@link Kepuasan}) and a loyalty from contentment and the
 * master bond ({@link Kesetiaan}); badly treated it grows restless, then defiant (arms crossed, refusing duties and half
 * the orders), then prepares to leave and, after three days of warnings, leaves in one of five ways
 * ({@link KepergianMurid}); the ones that left wait in {@link YangPergi}. Contract C36.P1-P5:
 * {@code SekteApi.setHati}, the bus listeners, the "pulang:&lt;soul&gt;" return, the Hati tab's info key
 * {@code sekte_hati}, the rebel duel through {@code DuelChallenges.startDirect}.
 * <p>
 * Budget (DESIGN 36.3.9): the daily step once per disciple and day on its {@code tick20}, the blow detection O(1), the
 * mood sync every 2 s to players with disciples near them, the departure tick every 10 ticks only while one is pending.
 * <p>
 * Sumber: the casts of the cultivation novels (Han Li in <i>A Record of a Mortal's Journey</i>, Bai Xiaochun in <i>A Will
 * Eternal</i>, Xiao Yan and Yao Lao in <i>Battle Through the Heavens</i>, Wang Lin in <i>Renegade Immortal</i>) and the
 * betrayal of Han Feng, Yao Lao's disciple (<i>Battle Through the Heavens</i>).
 */
public final class Kepribadian {

	/** Info key of the Hati tab (DESIGN 36.3.8). */
	public static final String INFO_KEY = "sekte_hati";
	/** Scene beats of P (EpicIds.EXTRA 100-109). */
	public static final int BEAT_REBEL = 100;
	public static final int BEAT_SUBMIT = 101;
	public static final int BEAT_LEFT = 102;
	/** After an accepted order a defiant disciple obeys this long before it sulks again (ticks). */
	public static final int OBEY_TICKS = 6000;
	public static final int APOLOGY_DAYS = 3;
	public static final int GIFTS_PER_DAY = 3;
	public static final double STELE_REACH = 12.0D;
	public static final int WANDER_GAP_DAYS = 15;
	public static final double WANDER_CHANCE = 0.15D;
	public static final double LURE_CHANCE = 0.20D;
	public static final int MEMORIES_SHOWN = 6;

	private static final Map<UUID, Long> OBEY = new HashMap<>();
	private static final Map<UUID, Long> BLOW = new HashMap<>();
	private static final Map<UUID, Long> BOW_UNTIL = new HashMap<>();
	private static final Map<UUID, int[]> SENT_MOODS = new HashMap<>();
	private static boolean done;
	private static int ticks;

	/** The service other modules read (contract C36.P1). */
	static final HatiService SERVICE = new HatiService() {
		@Override
		public List<Sifat> traits(DiscipleData data) {
			return on() ? Hati.traits(data) : List.of();
		}

		@Override
		public int contentment(DiscipleData data) {
			return on() ? Hati.contentment(data) : 70;
		}

		@Override
		public int loyalty(DiscipleData data) {
			return on() ? Kesetiaan.loyalty(data) : 100;
		}

		@Override
		public Keadaan state(DiscipleData data) {
			return on() && Hati.known(data) ? Kesetiaan.state(data) : Keadaan.SETIA;
		}

		@Override
		public double workFactor(DiscipleData data) {
			return Watak.workFactor(data);
		}

		@Override
		public @Nullable Component summary(DiscipleData data) {
			if (!on() || !Hati.known(data)) {
				return null;
			}
			MutableComponent line = Component.empty();
			List<Sifat> traits = Hati.traits(data);
			for (int i = 0; i < traits.size(); i++) {
				if (i > 0) {
					line.append(", ");
				}
				line.append(Component.translatable(traits.get(i).key()));
			}
			return line.append(Component.translatable("kultivator.sekte.kepribadian.ringkas", Hati.contentment(data),
					Component.translatable("kultivator.sekte.keadaan." + Kesetiaan.state(data).name().toLowerCase(java.util.Locale.ROOT))));
		}
	};

	public static void init() {
		if (done) {
			return;
		}
		done = true;
		KepribadianConfig.load();
		if (!SekteContent.on(SekteIds.P)) {
			return;
		}
		KepribadianPayloads.init();
		Watak.registerHooks();
		DiscipleHooks.addTick20(Kepribadian::tick20);
		DiscipleHooks.addOnTaught((entity, master, technique) -> careFrom(entity, Kepuasan.CARE_TEACH, KepribadianPayloads.ICON_STAR));
		DiscipleHooks.addOnFed((entity, master, stack) -> careFrom(entity, Kepuasan.CARE_PILL, KepribadianPayloads.ICON_HEART));
		DiscipleHooks.addModeBlocker(Kepribadian::modeBlocker);
		DiscipleHooks.addInfo((entity, master, tag) -> {
			if (on() && entity.role() == DiscipleEntity.Role.DISCIPLE) {
				tag.put(INFO_KEY, info(entity, master));
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer master) {
				onBlow(master, entity);
			}
			return InteractionResult.PASS;
		});
		registerBus();
		ServerTickEvents.END_SERVER_TICK.register(Kepribadian::serverTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			KepergianMurid.clear();
			OBEY.clear();
			BLOW.clear();
			BOW_UNTIL.clear();
			SENT_MOODS.clear();
		});
		KepribadianCommands.init();
		SekteApi.setHati(SERVICE);
	}

	static boolean on() {
		return KepribadianConfig.get().aktif;
	}

	// ================================================================== the disciple's second

	private static void tick20(DiscipleEntity e) {
		if (!on() || e.role() != DiscipleEntity.Role.DISCIPLE || e.isReleasing() || e.departed()
				|| !(e.level() instanceof ServerLevel level)) {
			return;
		}
		UUID owner = e.ownerId();
		if (owner == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		DiscipleData data = e.data();
		long today = Ingatan.today(server);
		if (!Hati.known(data)) {
			meet(e, today);
			return;
		}
		if (YangPergi.hasQueue(server, owner)) {
			YangPergi.drain(server, owner, e);
			afterChange(e, false);
		}
		long day = Hati.getLong(data, "day", today);
		if (day < today) {
			runDays(server, e, owner, day, today);
		}
		ServerPlayer master = e.owner();
		if (master != null && master.level() == level) {
			double distSq = master.distanceToSqr(e);
			if (e.isMeditatingNow() && distSq <= 8.0D * 8.0D && Hati.getLong(data, "md", Long.MIN_VALUE) != today) {
				Hati.putLong(data, "md", today);
				Kepuasan.care(data, today, Kepuasan.CARE_MEDITATE);
			}
			sacrificed(server, e, master, distSq, today);
		}
		behaviour(server, e, master);
	}

	/** First meeting: traits rolled, contentment 70, today's day stamp (also for disciples older than P). */
	private static void meet(DiscipleEntity e, long today) {
		DiscipleData data = e.data();
		Hati.setTraits(data, Watak.roll(data, e.getRandom()));
		Hati.setContentment(data, Hati.START);
		Hati.putLong(data, "day", today);
		Hati.putLong(data, "last", today);
		Hati.putInt(data, "low", 0);
		Hati.putInt(data, "state", Keadaan.SETIA.ordinal());
	}

	/** The daily step for every day since the last one (at most 5 for a disciple that was away). */
	static void runDays(MinecraftServer server, DiscipleEntity e, UUID owner, long day, long today) {
		int n = (int) Math.min(5L, Math.max(0L, today - day));
		for (int i = 0; i < n; i++) {
			oneDay(server, e, owner, today - n + i, today);
		}
		Hati.putLong(e.data(), "day", today);
		afterChange(e, true);
	}

	private static void oneDay(MinecraftServer server, DiscipleEntity e, UUID owner, long ended, long today) {
		DiscipleData data = e.data();
		KepribadianConfig cfg = KepribadianConfig.get();
		Kepuasan.daily(server, e, owner, ended, today);
		long last = Hati.getLong(data, "last", ended);
		long lastNeglect = Hati.getLong(data, "ng", Long.MIN_VALUE / 4);
		if (ended - last >= cfg.neglectDays && ended - lastNeglect >= cfg.neglectDays) {
			Hati.putLong(data, "ng", ended);
			SekteBus.mistreated(server, owner, e.soulId(), Perlakuan.DIABAIKAN, SekteIds.P + ".abai");
		}
		Sifat mended = Watak.trialShift(data, today);
		if (mended != null) {
			Ingatan.remember(server, data, SekteIds.P + ".ujian_hati", null, 6, "kultivator.sekte.kepribadian.ingat.ujian_hati",
					mended.opposite().id());
			ServerPlayer master = server.getPlayerList().getPlayer(owner);
			if (master != null) {
				Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.ujian_hati", e.getDisplayName(),
						Component.translatable(mended.key()), Component.translatable(mended.opposite().key())).withStyle(ChatFormatting.GOLD));
			}
		}
		if (!Hati.getString(data, "bujuk").isEmpty() && today - Hati.getLong(data, "bujukDay", today) > KepergianMurid.LURE_DAYS) {
			Hati.putString(data, "bujuk", "");
		}
		int loyalty = Kesetiaan.loyalty(data);
		if (!Kesetiaan.bound(data) && loyalty < Kesetiaan.threshold(data)) {
			Hati.putInt(data, "low", Hati.getInt(data, "low", 0) + 1);
		} else {
			Hati.putInt(data, "low", 0);
		}
		ServerPlayer master = server.getPlayerList().getPlayer(owner);
		if (master != null && Hati.has(data, Sifat.AMBISIUS) && Hati.contentmentExact(data) >= 50.0D
				&& data.realm().ordinal() >= Cultivators.get(master).realm().ordinal()
				&& today - Hati.getLong(data, "kembara", Long.MIN_VALUE / 4) >= WANDER_GAP_DAYS && e.getRandom().nextDouble() < WANDER_CHANCE) {
			KepergianMurid.askWander(server, e, master);
		}
	}

	/** Re-reads the state after any change: events, lines, bubbles, advancements (contract C36.P2: LOYALTY_CHANGED). */
	static void afterChange(DiscipleEntity e, boolean daily) {
		if (!(e.level() instanceof ServerLevel level) || !Hati.known(e.data())) {
			return;
		}
		DiscipleData data = e.data();
		UUID owner = e.ownerId();
		MinecraftServer server = level.getServer();
		ServerPlayer master = owner == null ? null : server.getPlayerList().getPlayer(owner);
		if (master != null && Hati.contentment(data) >= 90) {
			Advancements.grant(master, "sekte/kepribadian_puas");
		}
		Keadaan before = Keadaan.values()[Math.max(0, Math.min(Keadaan.values().length - 1, Hati.getInt(data, "state", 0)))];
		Keadaan now = Kesetiaan.state(data);
		if (before == now) {
			return;
		}
		Hati.putInt(data, "state", now.ordinal());
		if (owner != null) {
			SekteBus.LOYALTY_CHANGED.invoker().onLoyaltyChanged(server, owner, e.soulId(), before, now);
		}
		Component name = e.getDisplayName();
		boolean worse = now.ordinal() > before.ordinal();
		if (worse) {
			switch (now) {
				case GELISAH -> {
					VoiceDisciples.line(e, "tired", 600);
					KepribadianPayloads.pop(e, KepribadianPayloads.ICON_CLOUD);
					SekteLog.log(server, owner, SekteIds.P, Component.translatable("kultivator.sekte.kepribadian.log.gelisah", name));
				}
				case MEMBANGKANG -> {
					KepribadianPayloads.pop(e, KepribadianPayloads.ICON_STORM);
					SekteLog.log(server, owner, SekteIds.P, Component.translatable("kultivator.sekte.kepribadian.log.membangkang", name));
					if (master != null) {
						Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.peringatan.membangkang", name).withStyle(ChatFormatting.GOLD));
					}
				}
				case AKAN_PERGI -> {
					KepribadianPayloads.pop(e, KepribadianPayloads.ICON_ALERT);
					SekteLog.log(server, owner, SekteIds.P, Component.translatable("kultivator.sekte.kepribadian.log.akan_pergi", name));
					if (master != null) {
						Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.peringatan.akan_pergi", name,
								Math.max(1, Kesetiaan.daysLeft(data))).withStyle(ChatFormatting.RED));
					}
				}
				default -> {
				}
			}
			return;
		}
		if (before == Keadaan.MEMBANGKANG || before == Keadaan.AKAN_PERGI) {
			if (master != null) {
				Advancements.grant(master, "sekte/kepribadian_rukun");
				Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.pulih", name,
						Component.translatable("kultivator.sekte.keadaan." + now.name().toLowerCase(java.util.Locale.ROOT))).withStyle(ChatFormatting.GREEN));
			}
			restoreFromSulk(e);
		}
		if (now == Keadaan.SETIA) {
			VoiceDisciples.line(e, "loyal", 600);
			KepribadianPayloads.pop(e, KepribadianPayloads.ICON_HEART);
			SekteLog.log(server, owner, SekteIds.P, Component.translatable("kultivator.sekte.kepribadian.log.tenang", name));
		}
	}

	// ================================================================== behaviour (defiance)

	private static void behaviour(MinecraftServer server, DiscipleEntity e, @Nullable ServerPlayer master) {
		DiscipleData data = e.data();
		UUID soul = e.soulId();
		boolean busy = KepergianMurid.busy(soul);
		Keadaan state = Kesetiaan.state(data);
		boolean defiant = state == Keadaan.MEMBANGKANG || state == Keadaan.AKAN_PERGI;
		long now = server.overworld().getGameTime();
		if (defiant && !busy) {
			Long obey = OBEY.get(soul);
			boolean obeying = obey != null && obey > now;
			int activity = e.activity();
			boolean free = activity == DiscipleEntity.ACTIVITY_NONE
					|| activity >= DiscipleEntity.ACTIVITY_WATER && activity <= DiscipleEntity.ACTIVITY_FARM;
			if (!obeying && free && e.getTarget() == null && !data.breakingThrough() && !DuelRules.inDuel(e) && DuelSessions.byEntity(e) == null) {
				if (Hati.getString(data, "modeLama").isEmpty()) {
					Hati.putString(data, "modeLama", e.mode().id());
				}
				if (e.mode() != DiscipleEntity.Mode.STAY) {
					e.setMode(DiscipleEntity.Mode.STAY);
				}
				e.setActivity(SekteIds.ACT_SULK, sulkSpot(e));
				if (activity != DiscipleEntity.ACTIVITY_NONE && master != null) {
					Msg.chat(master, Component.translatable("kultivator.sekte.kepribadian.tolak.tugas", e.getDisplayName()).withStyle(ChatFormatting.YELLOW));
				}
			}
		} else if (!busy && e.activity() == SekteIds.ACT_SULK) {
			restoreFromSulk(e);
		}
		if (master != null && state == Keadaan.AKAN_PERGI && !busy
				&& Hati.getInt(data, "low", 0) >= KepribadianConfig.get().hariPergi) {
			KepergianMurid.begin(server, e, master);
		}
	}

	private static void restoreFromSulk(DiscipleEntity e) {
		if (e.activity() == SekteIds.ACT_SULK) {
			e.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		DiscipleData data = e.data();
		DiscipleEntity.Mode old = DiscipleEntity.Mode.byId(Hati.getString(data, "modeLama"));
		if (old != null && e.mode() == DiscipleEntity.Mode.STAY && old != DiscipleEntity.Mode.STAY) {
			e.setMode(old);
		}
		Hati.putString(data, "modeLama", "");
	}

	/** Where a defiant disciple sulks: a few blocks from the hall stele when it is near its hall, else where it stands. */
	private static BlockPos sulkSpot(DiscipleEntity e) {
		UUID owner = e.ownerId();
		Halls.HallView hall = owner == null ? null : Halls.hallOf(owner);
		if (hall == null || !hall.active() || !(e.level() instanceof ServerLevel level) || !level.dimension().equals(hall.dim())
				|| e.distanceToSqr(Vec3.atBottomCenterOf(hall.pos())) > 64.0D * 64.0D) {
			return e.blockPosition();
		}
		long h = e.soulId().getLeastSignificantBits();
		double angle = Math.floorMod(h, 360L) * Math.PI / 180.0D;
		double r = 5.0D + Math.floorMod(h >>> 9, 4L);
		int x = hall.pos().getX() + (int) Math.round(Math.cos(angle) * r);
		int z = hall.pos().getZ() + (int) Math.round(Math.sin(angle) * r);
		if (!level.isLoaded(new BlockPos(x, hall.pos().getY(), z))) {
			return e.blockPosition();
		}
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** Half the orders of a defiant disciple are refused; an accepted one lets it obey for 5 minutes. */
	private static @Nullable String modeBlocker(DiscipleEntity e, DiscipleEntity.Mode mode) {
		if (!on() || e.role() != DiscipleEntity.Role.DISCIPLE || !Hati.known(e.data()) || !Kesetiaan.defiant(e.data())) {
			return null;
		}
		if (KepergianMurid.busy(e.soulId())) {
			return "kultivator.sekte.kepribadian.tolak.pergi";
		}
		if (e.getRandom().nextBoolean()) {
			KepribadianPayloads.pop(e, KepribadianPayloads.ICON_STORM);
			return "kultivator.sekte.kepribadian.tolak.perintah";
		}
		if (e.level() instanceof ServerLevel level) {
			OBEY.put(e.soulId(), level.getServer().overworld().getGameTime() + OBEY_TICKS);
		}
		if (e.activity() == SekteIds.ACT_SULK) {
			e.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		Hati.putString(e.data(), "modeLama", "");
		return null;
	}

	// ================================================================== detection (P itself)

	/** The master struck their own disciple (the blow itself is refused by the disciple's invulnerability). */
	private static void onBlow(ServerPlayer master, Entity target) {
		if (!on() || !(target instanceof DiscipleEntity e) || e.role() != DiscipleEntity.Role.DISCIPLE || !e.isOwnedBy(master)
				|| e.isReleasing() || DuelRules.dueling(master, e) || DuelSessions.byEntity(master) != null || DuelSessions.byEntity(e) != null) {
			return;
		}
		long now = master.level().getServer().overworld().getGameTime();
		Long last = BLOW.get(e.soulId());
		if (last != null && now - last < KepribadianConfig.get().pukulCooldownDetik * 20L) {
			return;
		}
		BLOW.put(e.soulId(), now);
		SekteBus.mistreated(master.level().getServer(), master.getUUID(), e.soulId(), Perlakuan.DIPUKUL, SekteIds.P + ".pukul");
	}

	/** DIKORBANKAN: the master stood by (within 16 blocks, not striking for 10 s) while it fell below 20% in a fight. */
	private static void sacrificed(MinecraftServer server, DiscipleEntity e, ServerPlayer master, double distSq, long today) {
		if (distSq > 16.0D * 16.0D || e.getHealth() >= e.getMaxHealth() * 0.2F || e.getHealth() <= 0.0F) {
			return;
		}
		boolean fighting = e.getLastHurtByMobTimestamp() > 0 && e.tickCount - e.getLastHurtByMobTimestamp() < 60;
		boolean masterIdle = master.getLastHurtMobTimestamp() <= 0 || master.tickCount - master.getLastHurtMobTimestamp() > 200;
		if (!fighting || !masterIdle || DuelRules.inDuel(e) || DuelSessions.byEntity(e) != null
				|| e.activity() == DiscipleEntity.ACTIVITY_DUELLIST || Hati.getLong(e.data(), "kb", Long.MIN_VALUE) == today) {
			return;
		}
		Hati.putLong(e.data(), "kb", today);
		SekteBus.mistreated(server, master.getUUID(), e.soulId(), Perlakuan.DIKORBANKAN, SekteIds.P + ".dikorbankan");
	}

	private static void careFrom(DiscipleEntity e, double amount, int icon) {
		if (!on() || e.role() != DiscipleEntity.Role.DISCIPLE || !(e.level() instanceof ServerLevel level) || !Hati.known(e.data())) {
			return;
		}
		Kepuasan.care(e.data(), Ingatan.today(level.getServer()), amount);
		KepribadianPayloads.pop(e, icon);
	}

	// ================================================================== the bus (contract C36.P2)

	private static void registerBus() {
		SekteBus.DISCIPLE_MISTREATED.register((server, owner, soul, kind, severity, source) -> {
			if (!on()) {
				return;
			}
			DiscipleEntity e = loaded(owner, soul);
			if (e == null || !Hati.known(e.data())) {
				YangPergi.queue(server, owner, soul, kind, severity, source);
				return;
			}
			Kepuasan.mistreat(server, e, kind, severity, source, owner);
			KepribadianPayloads.pop(e, KepribadianPayloads.ICON_TEAR);
			if (kind == Perlakuan.DIPUKUL && Hati.has(e.data(), Sifat.PENAKUT)) {
				VoiceDisciples.line(e, "fear", 200);
			}
			afterChange(e, false);
		});
		SekteBus.DISCIPLE_HONORED.register((server, owner, soul, reason, amount) -> {
			DiscipleEntity e = on() ? loaded(owner, soul) : null;
			if (e != null && Hati.known(e.data())) {
				Kepuasan.care(e.data(), Ingatan.today(server), Math.max(1, Math.min(10, amount)));
				KepribadianPayloads.pop(e, KepribadianPayloads.ICON_STAR);
			}
		});
		SekteBus.RELATION_CHANGED.register((server, owner, a, b, before, now) -> {
			if (!on()) {
				return;
			}
			long today = Ingatan.today(server);
			for (UUID soul : new UUID[]{a, b}) {
				DiscipleEntity e = loaded(owner, soul);
				if (e == null || !Hati.known(e.data())) {
					continue;
				}
				DiscipleData data = e.data();
				UUID other = soul.equals(a) ? b : a;
				if (before.positive() && now == Ikatan.DENDAM) {
					// Betrayed by a friend (DESIGN 36.2.1): a loving heart may turn vengeful.
					Kepuasan.change(data, today, -4.0D);
					if (Hati.has(data, Sifat.PENYAYANG) && e.getRandom().nextDouble() < 0.25D) {
						Hati.swap(data, Sifat.PENYAYANG, Sifat.PENDENDAM);
						Ingatan.remember(server, data, SekteIds.P + ".dikhianati", other, -8, "kultivator.sekte.kepribadian.ingat.dikhianati", "");
					}
				} else if (now.positive() && !before.positive()) {
					Kepuasan.change(data, today, 2.0D);
				}
				afterChange(e, false);
			}
		});
		SekteBus.ANCESTOR_RITE.register((server, owner, kind, attendees) -> {
			if (!on()) {
				return;
			}
			long today = Ingatan.today(server);
			for (UUID soul : attendees) {
				DiscipleEntity e = loaded(owner, soul);
				if (e != null && Hati.known(e.data())) {
					Kepuasan.change(e.data(), today, 2.0D);
					Ingatan.remember(server, e.data(), SekteIds.P + ".upacara", null, 2, "kultivator.sekte.kepribadian.ingat.upacara", "");
					afterChange(e, false);
				}
			}
		});
		SekteBus.RIVAL_SECT_ACTION.register((server, owner, sectId, action, magnitude) -> {
			if (!on() || owner == null || action != AksiSekte.MEREKRUT) {
				return;
			}
			lure(server, owner, sectId);
		});
		SekteBus.MISSION_RETURNED.register((server, owner, result) -> {
			if (!on() || result.storyArgs().isEmpty()) {
				return;
			}
			String first = result.storyArgs().get(0);
			if (first != null && first.startsWith("pulang:")) {
				UUID soul = YangPergi.parse(first.substring("pulang:".length()));
				if (soul != null) {
					YangPergi.bringBack(server, owner, soul, result.missionId());
				}
			}
		});
		SekteBus.DISCIPLE_STUDIED.register((server, owner, soul, technique, learned) -> {
			DiscipleEntity e = on() && learned ? loaded(owner, soul) : null;
			if (e != null && Hati.known(e.data())) {
				Kepuasan.change(e.data(), Ingatan.today(server), 2.0D);
				afterChange(e, false);
			}
		});
		SekteBus.ELDER_APPOINTED.register((server, owner, soul, office) -> {
			DiscipleEntity e = on() && office != null ? loaded(owner, soul) : null;
			if (e != null && Hati.known(e.data())) {
				Kepuasan.change(e.data(), Ingatan.today(server), 5.0D);
				Ingatan.remember(server, e.data(), SekteIds.P + ".diangkat", owner, 4, "kultivator.sekte.kepribadian.ingat.diangkat", office.id());
				afterChange(e, false);
			}
		});
		SekteBus.HEIR_NAMED.register((server, owner, soul, previous) -> {
			DiscipleEntity e = on() ? loaded(owner, soul) : null;
			if (e != null && Hati.known(e.data())) {
				Kepuasan.change(e.data(), Ingatan.today(server), 8.0D);
				Ingatan.remember(server, e.data(), SekteIds.P + ".pewaris", owner, 8, "kultivator.sekte.kepribadian.ingat.pewaris", "");
				afterChange(e, false);
			}
		});
		SekteBus.SUCCESSION.register((server, owner, newMaster) -> everyone(server, owner, 3.0D, 3.0D, null, 0));
		SekteBus.DISCIPLE_BURIED.register((server, owner, soul, name) -> everyone(server, owner, -2.0D, -4.0D,
				"kultivator.sekte.kepribadian.ingat.berduka", -1));
	}

	/** A sect-wide moment: every loaded disciple of {@code owner} changes contentment ({@code loving} for Penyayang). */
	private static void everyone(MinecraftServer server, UUID owner, double amount, double loving, @Nullable String memoryKey, int weight) {
		if (!on()) {
			return;
		}
		long today = Ingatan.today(server);
		for (DiscipleEntity e : Disciples.loaded(owner)) {
			if (e.role() != DiscipleEntity.Role.DISCIPLE || !Hati.known(e.data())) {
				continue;
			}
			Kepuasan.change(e.data(), today, Hati.has(e.data(), Sifat.PENYAYANG) ? loving : amount);
			if (memoryKey != null) {
				Ingatan.remember(server, e.data(), SekteIds.P + ".sekte", null, weight, memoryKey, "");
			}
			afterChange(e, false);
		}
	}

	/**
	 * Module D's MEREKRUT: each restless (Gelisah) disciple is tempted with 20%: contentment -12, a memory, and the sect is
	 * remembered for ten days, so that if it leaves after the usual warnings it defects there (rule 36.0.4: never at once).
	 */
	static int lure(MinecraftServer server, UUID owner, String sectId) {
		long today = Ingatan.today(server);
		int tempted = 0;
		Component sectName = SekteApi.diplomasi().name(sectId);
		for (DiscipleEntity e : Disciples.loaded(owner)) {
			DiscipleData data = e.data();
			if (e.role() != DiscipleEntity.Role.DISCIPLE || !Hati.known(data) || Kesetiaan.bound(data)
					|| Kesetiaan.state(data) != Keadaan.GELISAH || e.getRandom().nextDouble() >= LURE_CHANCE) {
				continue;
			}
			Kepuasan.change(data, today, -12.0D);
			Hati.putString(data, "bujuk", sectId);
			Hati.putLong(data, "bujukDay", today);
			Ingatan.remember(server, data, SekteIds.P + ".dibujuk", null, -2, "kultivator.sekte.kepribadian.ingat.dibujuk",
					sectName == null ? sectId : sectName.getString());
			SekteLog.log(server, owner, SekteIds.P, Component.translatable("kultivator.sekte.kepribadian.log.dibujuk", e.getDisplayName(),
					sectName == null ? Component.literal(sectId) : sectName));
			afterChange(e, false);
			tempted++;
		}
		return tempted;
	}

	// ================================================================== the screen's actions

	static void onAction(ServerPlayer player, KepribadianPayloads.Aksi aksi) {
		if (!on()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (aksi.action() == KepribadianPayloads.A_LIST) {
			Advancements.grant(player, "sekte/kepribadian_sifat");
			KepribadianPayloads.send(player, list(server, player));
			return;
		}
		Entity entity = player.level().getEntity(aksi.entityId());
		if (!(entity instanceof DiscipleEntity e) || !e.isAlive() || e.isReleasing() || e.role() != DiscipleEntity.Role.DISCIPLE
				|| !e.isOwnedBy(player) || !Hati.known(e.data())) {
			return;
		}
		double range = aksi.action() == KepribadianPayloads.A_VIEW ? Disciples.VIEW_RANGE : Disciples.ACTION_RANGE;
		if (player.distanceToSqr(e) > range * range) {
			Msg.chat(player, Component.translatable("kultivator.disciples.too_far", e.getDisplayName(), (int) range).withStyle(ChatFormatting.YELLOW));
			return;
		}
		long today = Ingatan.today(server);
		switch (aksi.action()) {
			case KepribadianPayloads.A_VIEW -> Advancements.grant(player, "sekte/kepribadian_sifat");
			case KepribadianPayloads.A_TALK -> talk(server, player, e, today);
			case KepribadianPayloads.A_GIFT -> gift(server, player, e, today);
			case KepribadianPayloads.A_APOLOGY -> apologise(server, player, e, today);
			case KepribadianPayloads.A_EXPEL -> {
				if ("confirm".equals(aksi.arg())) {
					KepergianMurid.expel(server, player, e);
					return;
				}
			}
			default -> {
				return;
			}
		}
		if (e.isAlive() && !e.isRemoved()) {
			Disciples.sendInfo(player, e);
		}
	}

	/** Bicara: once a day, +4 (Rendah Hati +6), and the disciple says what is on its heart. */
	private static void talk(MinecraftServer server, ServerPlayer player, DiscipleEntity e, long today) {
		DiscipleData data = e.data();
		if (Hati.getLong(data, "talk", Long.MIN_VALUE) == today) {
			Msg.chat(player, Component.translatable("kultivator.sekte.kepribadian.bicara.sudah", e.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		Component line = talkLine(e);
		Hati.putLong(data, "talk", today);
		Kepuasan.change(data, today, Hati.has(data, Sifat.RENDAH_HATI) ? 6.0D : 4.0D);
		Ingatan.remember(server, data, SekteIds.P + ".bicara", player.getUUID(), 1, "kultivator.sekte.kepribadian.ingat.bicara", "");
		e.getLookControl().setLookAt(player, 30.0F, 30.0F);
		VoiceDisciples.line(e, "thanks", 200);
		KepribadianPayloads.pop(e, KepribadianPayloads.ICON_HEART);
		Msg.chat(player, Component.translatable("kultivator.sekte.kepribadian.bicara.kata", e.getDisplayName(), line).withStyle(ChatFormatting.WHITE));
		afterChange(e, false);
	}

	/** What the disciple says: a line of one of its traits (half the time) or of its state. */
	private static Component talkLine(DiscipleEntity e) {
		DiscipleData data = e.data();
		List<Sifat> traits = Hati.traits(data);
		if (!traits.isEmpty() && e.getRandom().nextBoolean()) {
			Sifat sifat = traits.get(e.getRandom().nextInt(traits.size()));
			return Component.translatable("kultivator.sekte.kepribadian.bicara.sifat." + sifat.id() + "." + e.getRandom().nextInt(2));
		}
		String state = Kesetiaan.state(data).name().toLowerCase(java.util.Locale.ROOT);
		return Component.translatable("kultivator.sekte.kepribadian.bicara." + state + "." + e.getRandom().nextInt(3));
	}

	/** Hadiah: the item in the main hand (a pill is fed, a herb or a spirit stone is given), 3 a day, +2..+6 by its worth. */
	private static void gift(MinecraftServer server, ServerPlayer player, DiscipleEntity e, long today) {
		DiscipleData data = e.data();
		ItemStack stack = player.getMainHandItem();
		int worth = giftValue(stack);
		if (worth <= 0) {
			Msg.chat(player, Component.translatable("kultivator.sekte.kepribadian.hadiah.bukan").withStyle(ChatFormatting.YELLOW));
			return;
		}
		int given = Hati.getLong(data, "gd", Long.MIN_VALUE) == today ? Hati.getInt(data, "gn", 0) : 0;
		if (given >= GIFTS_PER_DAY) {
			Msg.chat(player, Component.translatable("kultivator.sekte.kepribadian.hadiah.cukup", e.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		Component itemName = stack.getHoverName();
		if (Disciples.isPill(stack)) {
			if (!DiscipleCultivation.feed(e, player, stack)) {
				return;
			}
		} else if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		Hati.putLong(data, "gd", today);
		Hati.putInt(data, "gn", given + 1);
		Kepuasan.change(data, today, worth);
		Ingatan.remember(server, data, SekteIds.P + ".hadiah", player.getUUID(), Math.max(1, worth / 2), "kultivator.sekte.kepribadian.ingat.hadiah",
				itemName.getString());
		e.getLookControl().setLookAt(player, 30.0F, 30.0F);
		VoiceDisciples.line(e, "thanks", 200);
		KepribadianPayloads.pop(e, KepribadianPayloads.ICON_HEART);
		if (e.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.3D, e.getZ(), 3, 0.3D, 0.2D, 0.3D, 0.0D);
		}
		Msg.chat(player, Component.translatable("kultivator.sekte.kepribadian.hadiah.terima", e.getDisplayName(), itemName, worth)
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		afterChange(e, false);
	}

	/** The worth of a gift: spirit stones 2/3/5/6 by grade, herbs 2-6 by age, a pill 3; 0 for anything else. */
	static int giftValue(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		if (stack.getItem() instanceof SpiritStoneItem stone) {
			int[] worth = {2, 3, 5, 6};
			return worth[Math.max(0, Math.min(3, stone.grade()))];
		}
		if (stack.getItem() instanceof SpiritHerbItem) {
			int age = stack.getOrDefault(HartaComponents.HERB_AGE, 100);
			return age >= 1000 ? 6 : age >= 500 ? 4 : age >= 100 ? 3 : 2;
		}
		return Disciples.isPill(stack) ? 3 : 0;
	}

	/** Minta Maaf di Aula: master and disciple before the hall stele, both bow; +10, a memory +5, once every 3 days. */
	private static void apologise(MinecraftServer server, ServerPlayer player, DiscipleEntity e, long today) {
		DiscipleData data = e.data();
		long last = Hati.getLong(data, "maaf", Long.MIN_VALUE / 4);
		if (today - last < APOLOGY_DAYS) {
			Msg.chat(player, Component.translatable("kultivator.sekte.kepribadian.maaf.tunggu", APOLOGY_DAYS - (today - last)).withStyle(ChatFormatting.GRAY));
			return;
		}
		Halls.HallView hall = Halls.hallOf(player.getUUID());
		Vec3 stele = hall == null ? null : Vec3.atBottomCenterOf(hall.pos());
		if (hall == null || !player.level().dimension().equals(hall.dim()) || player.position().distanceToSqr(stele) > STELE_REACH * STELE_REACH
				|| e.position().distanceToSqr(stele) > (STELE_REACH + 4.0D) * (STELE_REACH + 4.0D)) {
			Msg.chat(player, Component.translatable("kultivator.sekte.kepribadian.maaf.aula").withStyle(ChatFormatting.YELLOW));
			return;
		}
		Hati.putLong(data, "maaf", today);
		Kepuasan.change(data, today, 10.0D);
		Ingatan.remember(server, data, SekteIds.P + ".maaf", player.getUUID(), 5, "kultivator.sekte.kepribadian.ingat.maaf", "");
		if (e.activity() == DiscipleEntity.ACTIVITY_NONE || e.activity() == SekteIds.ACT_SULK) {
			e.setActivity(SekteIds.ACT_BOW, null);
			BOW_UNTIL.put(e.soulId(), server.overworld().getGameTime() + 60L);
		}
		e.getLookControl().setLookAt(player, 30.0F, 30.0F);
		VoiceDisciples.line(e, "greet", 200);
		KepribadianPayloads.pop(e, KepribadianPayloads.ICON_HEART);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.END_ROD, stele.x, stele.y + 1.2D, stele.z, 12, 0.6D, 0.6D, 0.6D, 0.01D);
		level.playSound(null, stele.x, stele.y, stele.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.5F, 1.2F);
		Msg.chat(player, Component.translatable("kultivator.sekte.kepribadian.maaf.ok", e.getDisplayName()).withStyle(ChatFormatting.GREEN));
		SekteLog.log(server, player.getUUID(), SekteIds.P, Component.translatable("kultivator.sekte.kepribadian.log.maaf", e.getDisplayName()));
		afterChange(e, false);
	}

	// ================================================================== the tab's info (contract C36.P4)

	static CompoundTag info(DiscipleEntity e, ServerPlayer master) {
		DiscipleData data = e.data();
		CompoundTag tag = new CompoundTag();
		MinecraftServer server = master.level().getServer();
		long today = Ingatan.today(server);
		tag.putBoolean("known", Hati.known(data));
		ListTag traits = new ListTag();
		for (Sifat sifat : Hati.traits(data)) {
			traits.add(StringTag.valueOf(sifat.id()));
		}
		tag.put("sifat", traits);
		tag.putInt("k", Hati.contentment(data));
		tag.putInt("loy", Kesetiaan.loyalty(data));
		tag.putInt("bond", DisciplePaths.bond(data));
		tag.putInt("state", Kesetiaan.state(data).ordinal());
		tag.putInt("thr", Kesetiaan.threshold(data));
		tag.putInt("left", Kesetiaan.daysLeft(data));
		tag.putInt("low", Hati.getInt(data, "low", 0));
		tag.putBoolean("talk", Hati.getLong(data, "talk", Long.MIN_VALUE) != today);
		int given = Hati.getLong(data, "gd", Long.MIN_VALUE) == today ? Hati.getInt(data, "gn", 0) : 0;
		tag.putInt("gifts", Math.max(0, GIFTS_PER_DAY - given));
		long maaf = Hati.getLong(data, "maaf", Long.MIN_VALUE / 4);
		tag.putInt("maaf", (int) Math.max(0L, Math.min(99L, APOLOGY_DAYS - (today - maaf))));
		tag.putBoolean("sealed", Kesetiaan.bound(data));
		tag.putDouble("wf", Watak.workFactor(data));
		tag.putDouble("care", Hati.careOf(data, today));
		tag.putInt("neglect", (int) Math.max(0L, Math.min(99L, today - Hati.getLong(data, "last", today))));
		tag.putInt("nd", KepribadianConfig.get().neglectDays);
		KepergianMurid.Pending pending = KepergianMurid.pending(e.soulId());
		tag.putInt("pend", pending == null ? 0 : pending.fase.ordinal() + 1);
		if (!Hati.getString(data, "bujuk").isEmpty()) {
			Component sect = SekteApi.diplomasi().name(Hati.getString(data, "bujuk"));
			tag.putString("bujuk", sect == null ? Hati.getString(data, "bujuk") : sect.getString());
		}
		ListTag memories = new ListTag();
		List<Kenangan> strongest = new ArrayList<>(Ingatan.of(data));
		strongest.sort(Comparator.comparingInt((Kenangan k) -> -Math.abs(k.weight())).thenComparingLong(k -> -k.day()));
		for (int i = 0; i < Math.min(MEMORIES_SHOWN, strongest.size()); i++) {
			Kenangan k = strongest.get(i);
			CompoundTag m = new CompoundTag();
			m.putString("t", k.text());
			m.putString("a", k.arg());
			m.putInt("w", k.weight());
			m.putLong("ago", Math.max(0L, today - k.day()));
			m.putString("m", k.module());
			memories.add(m);
		}
		tag.put("mem", memories);
		return tag;
	}

	// ================================================================== the hub list

	static KepribadianPayloads.Daftar list(MinecraftServer server, ServerPlayer player) {
		List<KepribadianPayloads.Baris> rows = new ArrayList<>();
		for (DiscipleEntity e : Disciples.loaded(player.getUUID())) {
			if (e.role() != DiscipleEntity.Role.DISCIPLE || !Hati.known(e.data())) {
				continue;
			}
			DiscipleData data = e.data();
			StringBuilder traits = new StringBuilder();
			for (Sifat sifat : Hati.traits(data)) {
				if (!traits.isEmpty()) {
					traits.append(',');
				}
				traits.append(sifat.id());
			}
			int flags = (SoulSealCheck.sealed(data) ? KepribadianPayloads.F_SEALED : 0) | (DisciplePaths.isHeir(data) ? KepribadianPayloads.F_HEIR : 0)
					| (KepergianMurid.busy(e.soulId()) ? KepribadianPayloads.F_PENDING : 0);
			rows.add(new KepribadianPayloads.Baris(e.getDisplayName(), Kesetiaan.state(data).ordinal(), Kesetiaan.loyalty(data),
					Hati.contentment(data), Kesetiaan.daysLeft(data), traits.toString(), flags, e.getId()));
			if (rows.size() >= KepribadianPayloads.MAX_ROWS) {
				break;
			}
		}
		rows.sort(Comparator.comparingInt(KepribadianPayloads.Baris::loyalty));
		List<KepribadianPayloads.Pergi> gone = new ArrayList<>();
		for (YangPergi.Entry entry : YangPergi.list(server, player.getUUID())) {
			gone.add(new KepribadianPayloads.Pergi(entry.name(), entry.how().id(), entry.day()));
		}
		return new KepribadianPayloads.Daftar(rows, gone, Ingatan.today(server));
	}

	/** The soul-seal test without pulling the class twice into every call site. */
	private static final class SoulSealCheck {
		static boolean sealed(DiscipleData data) {
			return dev.kultivator.disciple.path.SoulSeal.sealed(data);
		}
	}

	// ================================================================== the server tick

	private static void serverTick(MinecraftServer server) {
		if (!on()) {
			return;
		}
		ticks++;
		if (ticks % 10 == 0) {
			KepergianMurid.tick(server);
			endBows(server);
		}
		if (ticks % 40 == 0) {
			syncMoods(server, ticks % 200 == 0);
		}
	}

	private static void endBows(MinecraftServer server) {
		if (BOW_UNTIL.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		BOW_UNTIL.entrySet().removeIf(entry -> {
			if (entry.getValue() > now) {
				return false;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				DiscipleEntity e = loaded(player.getUUID(), entry.getKey());
				if (e != null && e.activity() == SekteIds.ACT_BOW) {
					e.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				}
			}
			return true;
		});
	}

	/** The thought bubbles: the moods of the disciples within {@code jarakGelembung} of each player, nearest first. */
	private static void syncMoods(MinecraftServer server, boolean force) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		List<DiscipleEntity> all = new ArrayList<>();
		for (ServerPlayer owner : players) {
			for (DiscipleEntity e : Disciples.loaded(owner.getUUID())) {
				if (e.role() == DiscipleEntity.Role.DISCIPLE && Hati.known(e.data())) {
					all.add(e);
				}
			}
		}
		double range = KepribadianConfig.get().jarakGelembung;
		for (ServerPlayer viewer : players) {
			List<DiscipleEntity> near = new ArrayList<>();
			for (DiscipleEntity e : all) {
				if (e.level() == viewer.level() && e.distanceToSqr(viewer) <= range * range && mood(e) != KepribadianPayloads.ICON_NONE) {
					near.add(e);
				}
			}
			near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(viewer)));
			int n = Math.min(KepribadianPayloads.MAX_MOODS, near.size());
			int[] ids = new int[n];
			int[] icons = new int[n];
			for (int i = 0; i < n; i++) {
				ids[i] = near.get(i).getId();
				icons[i] = mood(near.get(i));
			}
			int[] key = new int[n * 2];
			for (int i = 0; i < n; i++) {
				key[i * 2] = ids[i];
				key[i * 2 + 1] = icons[i];
			}
			int[] last = SENT_MOODS.get(viewer.getUUID());
			if (!force && last != null && Arrays.equals(last, key)) {
				continue;
			}
			if (n == 0 && last == null) {
				continue;
			}
			SENT_MOODS.put(viewer.getUUID(), key);
			KepribadianPayloads.send(viewer, new KepribadianPayloads.Suasana(ids, icons));
		}
		SENT_MOODS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	/** The persistent bubble of a disciple: a bundle while it walks out, else its state (a heart when very content). */
	static int mood(DiscipleEntity e) {
		KepergianMurid.Pending pending = KepergianMurid.pending(e.soulId());
		if (pending != null && (pending.exit != null || e.activity() == SekteIds.ACT_LEAVING)) {
			return KepribadianPayloads.ICON_BUNDLE;
		}
		DiscipleData data = e.data();
		return switch (Kesetiaan.state(data)) {
			case AKAN_PERGI -> KepribadianPayloads.ICON_ALERT;
			case MEMBANGKANG -> KepribadianPayloads.ICON_STORM;
			case GELISAH -> KepribadianPayloads.ICON_CLOUD;
			case SETIA -> Hati.contentment(data) >= 90 ? KepribadianPayloads.ICON_HEART : KepribadianPayloads.ICON_NONE;
		};
	}

	// ================================================================== helpers

	static @Nullable DiscipleEntity loaded(@Nullable UUID owner, @Nullable UUID soul) {
		if (owner == null || soul == null) {
			return null;
		}
		for (DiscipleEntity e : Disciples.loaded(owner)) {
			if (soul.equals(e.soulId()) && e.role() == DiscipleEntity.Role.DISCIPLE) {
				return e;
			}
		}
		return null;
	}

	static void logError(String what, RuntimeException exception) {
		Kultivator.LOGGER.error("Sekte kepribadian: {} failed", what, exception);
	}

	private Kepribadian() {
	}
}
