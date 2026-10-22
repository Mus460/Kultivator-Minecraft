package dev.kultivator.sekte.tetua;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.network.Actions;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.hall.Candidates;
import dev.kultivator.sect.hall.HallData;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sect.hall.Talents;
import dev.kultivator.sekte.Ingatan;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.SekteConfig;
import dev.kultivator.sekte.SekteContent;
import dev.kultivator.sekte.SekteIds;
import dev.kultivator.sekte.SekteLog;
import dev.kultivator.sekte.api.AksiSekte;
import dev.kultivator.sekte.api.Jabatan;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.api.TetuaService;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Module E, Tetua &amp; Pewaris (DESIGN 36.2.6, butir 42 and 50; contracts C36.E1-E3): the entry, wired with one line in
 * {@code SekteContent}. It sets {@code SekteApi.setTetua} (office holders, {@code officeFactor} for P, A, W and X, the
 * heir), listens on the bus ({@code SECT_LOG} for the morning report, {@code LOYALTY_CHANGED}, {@code WORK_DONE},
 * {@code HEIR_NAMED} from the existing {@code Heir}, {@code DISCIPLE_LEFT}, {@code RIVAL_SECT_ACTION(UTUSAN)} for the
 * Outer Affairs elder), fires {@code ELDER_APPOINTED} and {@code SUCCESSION}, and runs the elders once a second per
 * disciple ({@code DiscipleHooks.addTick20}) and a few cheap server ticks (the ceremony and the report walk each tick, the
 * report check every 2 s, the founder and the offices every 30 s per online master). Its payloads, commands and config
 * section are its own; nothing here references a client class.
 */
public final class Tetua {

	/** Beats of the 7b scenes (EXTRA 150-159 belong to E). */
	public static final int BEAT_THRONE = EpicIds.EXTRA_SEKTE_FIRST + 50;
	public static final int BEAT_REPORT = EpicIds.EXTRA_SEKTE_FIRST + 51;
	public static final int BEAT_APPOINT = EpicIds.EXTRA_SEKTE_FIRST + 52;

	private static TetuaConfig config = new TetuaConfig();
	private static boolean initialised;
	private static long ticks;

	private static boolean content;

	/** Payload types (K1): always, whatever the switches say. */
	public static void registerContent() {
		if (content) {
			return;
		}
		content = true;
		TetuaPayloads.init();
	}

	public static void init() {
		registerContent();
		if (initialised || !SekteContent.on(SekteIds.E)) {
			return;
		}
		initialised = true;
		config = SekteConfig.section(SekteIds.E, TetuaConfig.class, TetuaConfig::new, TetuaConfig::clamp);
		SekteApi.setTetua(new Service());
		TetuaCommands.init();
		DiscipleHooks.addTick20(Tetua::tick20);
		Halls.addStatusLineProvider(Tetua::statusLine);

		SekteBus.SECT_LOG.register(LaporanPagi::note);
		SekteBus.LOYALTY_CHANGED.register(LaporanPagi::onLoyalty);
		SekteBus.WORK_DONE.register(LaporanPagi::onWork);
		SekteBus.HEIR_NAMED.register(Generasi::onHeirNamed);
		SekteBus.DISCIPLE_LEFT.register((server, owner, soul, name, how, toSect, companion) -> onLeft(server, owner, soul, name));
		SekteBus.RIVAL_SECT_ACTION.register((server, owner, sect, action, magnitude) -> {
			if (owner != null && action == AksiSekte.UTUSAN) {
				UrusanHarian.envoyArrived(owner, sect);
			}
			if (owner != null && Generasi.founder(owner)) {
				Generasi.greetAll(server, owner);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(Tetua::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	static TetuaConfig config() {
		return config;
	}

	// ================================================================== the service (C36.E1, E3)

	private static final class Service implements TetuaService {
		@Override
		public @Nullable Jabatan office(DiscipleData data) {
			return data == null ? null : TetuaData.discipleOffice(data);
		}

		@Override
		public Map<Jabatan, UUID> offices(UUID owner) {
			return owner == null ? Map.of() : TetuaData.offices(owner);
		}

		@Override
		public @Nullable UUID holder(UUID owner, Jabatan office) {
			return owner == null || office == null ? null : TetuaData.holder(owner, office);
		}

		@Override
		public double officeFactor(UUID owner, Jabatan office) {
			return JabatanTetua.factor(owner, office);
		}

		@Override
		public @Nullable UUID heir(UUID owner) {
			return owner == null ? null : Generasi.heir(owner);
		}
	}

	// ================================================================== disciples

	/** Once a second per disciple: the heir and the Ketua's name, the office's check, the desk and the affair. */
	private static void tick20(DiscipleEntity entity) {
		if (entity.role() != DiscipleEntity.Role.DISCIPLE || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		UUID owner = entity.ownerId();
		if (owner == null || Halls.hallOf(owner) == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		try {
			Generasi.tick20(server, owner, entity);
			Jabatan office = JabatanTetua.refresh(server, owner, entity);
			if (office == null) {
				if (entity.activity() == SekteIds.ACT_OFFICE && !busy(entity)) {
					// a desk or a ceremony row left over from before a restart
					entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				}
				return;
			}
			UrusanHarian.duty(server, owner, entity, office);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sekte tetua tick failed for {}", entity.soulId(), exception);
		}
	}

	/** True while the ceremony or the morning report has the disciple. */
	static boolean busy(DiscipleEntity entity) {
		return PenyerahanTakhta.part(entity) || LaporanPagi.walking(entity);
	}

	/**
	 * Free for E's walks: a living listed disciple, idle or at its desk, not fighting, breaking through, duelling,
	 * sulking, on a tournament, a shift or a mission. {@code anyOrder} also takes followers (the ceremony, the report).
	 */
	static boolean free(DiscipleEntity entity, boolean anyOrder) {
		if (entity.isRemoved() || !entity.isAlive() || entity.role() != DiscipleEntity.Role.DISCIPLE || entity.isReleasing()) {
			return false;
		}
		int activity = entity.activity();
		if (activity != DiscipleEntity.ACTIVITY_NONE && activity != SekteIds.ACT_OFFICE) {
			return false;
		}
		DiscipleEntity.Mode mode = entity.mode();
		if (!anyOrder && (mode == DiscipleEntity.Mode.FOLLOW || mode == DiscipleEntity.Mode.ASSIST)) {
			return false;
		}
		DiscipleData data = entity.data();
		UUID owner = entity.ownerId();
		if (entity.getTarget() != null || data.breakingThrough() || DuelRules.inDuel(entity) || owner == null) {
			return false;
		}
		UUID soul = entity.soulId();
		return !SekteApi.hati().defiant(data) && !SekteApi.turnamen().busy(owner, soul) && !SekteApi.kebun().working(owner, soul)
				&& !SekteApi.misi().away(owner, soul) && Halls.inTerritory(owner, entity.level(), entity.position());
	}

	/** {@code DISCIPLE_LEFT}: its office empties; an heir or Ketua who left is no longer one. */
	private static void onLeft(MinecraftServer server, UUID owner, UUID soul, Component name) {
		Jabatan office = TetuaData.officeOf(owner, soul);
		if (office != null) {
			JabatanTetua.release(server, owner, soul, office, null, 0, null);
			SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.log.left", name, Component.translatable(office.key())));
		}
		CompoundTag mine = TetuaData.hall(owner);
		if (soul.toString().equals(mine.getStringOr(TetuaData.HEIR, ""))) {
			mine.remove(TetuaData.HEIR);
			mine.remove(TetuaData.HEIR_NAME);
			TetuaData.dirty();
		}
		if (Generasi.isMaster(owner, soul)) {
			mine.remove(TetuaData.MASTER);
			mine.remove(TetuaData.MASTER_NAME);
			TetuaData.dirty();
		}
	}

	// ================================================================== server ticks

	private static void serverTick(MinecraftServer server) {
		ticks++;
		try {
			PenyerahanTakhta.tick(server);
			LaporanPagi.tickWalks(server);
			if (ticks % 40L == 7L) {
				LaporanPagi.tick(server);
			}
			// every 30 s per online master, spread over the ticks by player
			List<ServerPlayer> players = server.getPlayerList().getPlayers();
			if (!players.isEmpty() && ticks % 20L == 3L) {
				int slot = (int) ((ticks / 20L) % 30L);
				for (int i = slot; i < players.size(); i += 30) {
					ServerPlayer player = players.get(i);
					if (Halls.hallOf(player.getUUID()) != null) {
						Generasi.tickOwner(server, player);
						autoAccept(server, player);
					}
				}
			}
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.error("Sekte tetua server tick failed", exception);
		}
	}

	/**
	 * Pelaksana: with {@code terimaOtomatis} on, the Grand Elder (or the Ketua) accepts the best candidate that passed
	 * the climb while the master is away from the hall, once a day, through the hall's own acceptance (cap, fee rules
	 * and welcome unchanged). Candidates that would need the servant fee are left for the master.
	 */
	private static void autoAccept(MinecraftServer server, ServerPlayer master) {
		if (!config.terimaOtomatis) {
			return;
		}
		UUID owner = master.getUUID();
		if (Halls.inTerritory(owner, master.level(), master.position()) || SekteApi.tangga().running(owner)) {
			return;
		}
		CompoundTag mine = TetuaData.hall(owner);
		long today = Ingatan.today(server);
		if (mine.getLongOr(TetuaData.AUTO_DAY, -1L) == today) {
			return;
		}
		UUID actor = TetuaData.holder(owner, Jabatan.TETUA_AGUNG);
		if (actor == null) {
			actor = Generasi.master(owner);
		}
		DiscipleEntity elder = actor == null ? null : JabatanTetua.loaded(owner, actor);
		if (elder == null || !Halls.inTerritory(owner, elder.level(), elder.position())) {
			return;
		}
		if (Disciples.aliveCount(Cultivators.get(master)) >= Disciples.cap(master)) {
			return;
		}
		CompoundTag core = Halls.section(owner, HallData.CORE);
		int best = -1;
		int bestScore = Integer.MIN_VALUE;
		Component bestName = null;
		for (Tag tag : core.getListOrEmpty("candidates")) {
			if (!(tag instanceof CompoundTag rec)) {
				continue;
			}
			DiscipleData data = DiscipleData.load(rec.getCompoundOrEmpty("data"));
			boolean special = rec.getBooleanOr("token", false) || rec.getBooleanOr("champion", false);
			if (!special && Candidates.verdict(data) != Candidates.VERDICT_PASSED) {
				continue;
			}
			int score = data.rootGrade().ordinal() * 100 + Talents.tekad(data);
			if (score > bestScore) {
				bestScore = score;
				best = rec.getIntOr("id", -1);
				bestName = rec.read("name", net.minecraft.network.chat.ComponentSerialization.CODEC).orElse(null);
			}
		}
		if (best < 0) {
			return;
		}
		mine.putLong(TetuaData.AUTO_DAY, today);
		TetuaData.dirty();
		int before = core.getListOrEmpty("candidates").size();
		Halls.handleAction(master, new Payloads.Action(Actions.HALL, Halls.OP_ACCEPT, Integer.toString(best)));
		if (Halls.section(owner, HallData.CORE).getListOrEmpty("candidates").size() < before) {
			Component who = bestName == null ? Component.translatable("kultivator.sekte.tetua.someone") : bestName;
			SekteLog.log(server, owner, SekteIds.E, Component.translatable("kultivator.sekte.tetua.log.auto_accept", elder.getName(), who));
			Msg.chat(master, Component.translatable("kultivator.sekte.tetua.auto_accept", elder.getName(), who).withStyle(ChatFormatting.GOLD));
		}
	}

	/** The Aula tablet's status line: the generation and the Founding Patriarch, else the offices filled. */
	private static @Nullable Component statusLine(UUID owner) {
		int generation = Generasi.generation(owner);
		if (generation >= 2 && Generasi.master(owner) != null) {
			return Component.translatable("kultivator.sekte.tetua.status.generation", generation, TetuaData.name(TetuaData.hall(owner), TetuaData.MASTER_NAME))
					.withColor(Generasi.GOLD);
		}
		int filled = TetuaData.offices(owner).size();
		return filled == 0 ? null : Component.translatable("kultivator.sekte.tetua.status.offices", filled, Jabatan.values().length);
	}

	private static void clear() {
		PenyerahanTakhta.clear();
		LaporanPagi.clear();
		UrusanHarian.clear();
		ticks = 0L;
	}

	private Tetua() {
	}
}
