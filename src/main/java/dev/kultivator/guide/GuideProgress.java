package dev.kultivator.guide;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.CaveAbode;
import dev.kultivator.cultivation.ImmortalPerks;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.network.Net;
import dev.kultivator.network.Payloads;
import dev.kultivator.sect.build.SectBuild;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kitab Panduan, server side: evaluates the automatic checks of {@link GuideChapters} for a player, keeps the manual
 * ticks, answers {@code Payloads.GuideAction} and adds {@code /kultivasi panduan}.
 * <p>
 * Checks only read data that already exists: the cultivation attachment (rank, root, Dao, meditation time, Heart Demons,
 * Heaven's Acknowledgement, Immortal Stars, cave abode), the mod's advancements, the Sect Hall (founded, level), the
 * disciple list and the sect template (chosen, tier built). Every check is wrapped so a failing module can never throw
 * out of the guide: it simply counts as not done.
 * <p>
 * Manual ticks live in {@code extra.panduan.m} of the player's {@link CultivationData} (saved with the player and kept on
 * death like everything else in it): a compound of step id → true. Ticks of steps that no longer exist are ignored.
 * <p>
 * Syncing: on the command (with {@code open}), on every GuideAction, and every 5 seconds per player when the result
 * changed since the last sync (for the First Steps tracker line and the chapter toast). Clients without the mod are never
 * evaluated.
 * <p>
 * Sumber: Terinspirasi dari bimbingan Yao Lao kepada Xiao Yan (Battle Through the Heavens). Isi panduan desain game.
 */
public final class GuideProgress {

	/** {@code extra.panduan}: the module's sub-compound in the player's cultivation data. */
	public static final String KEY = "panduan";
	/** {@code extra.panduan.m}: the manual ticks (step id → true). */
	public static final String MANUAL = "m";
	/** Ticks between two change checks per player once a first sync went out. */
	private static final int PUSH_INTERVAL = 100;
	/** Ticks between checks while the first sync still waits for the client's channel. */
	private static final int FIRST_INTERVAL = 20;
	/** GuideActions handled per player per tick (a spam guard; the screen sends one per click). */
	private static final int ACTIONS_PER_TICK = 8;
	/** The longest action text: "tick " plus a step id. */
	private static final int MAX_ACTION_LENGTH = 64;

	/** The player's evaluated progress; {@code current} is a 0-based save index of {@link GuideChapters}. */
	public record State(BitSet done, BitSet manual, int current) {

		/** Steps done in chapter {@code index} (0-based). */
		public int doneIn(int index) {
			int count = 0;
			for (GuideChapters.Step step : GuideChapters.chapter(index).steps()) {
				if (done.get(step.index())) {
					count++;
				}
			}
			return count;
		}

		/** True when every step is done. */
		public boolean complete() {
			return current >= GuideChapters.CHAPTER_COUNT;
		}
	}

	/** The last state sent to each player (only after it really went out). */
	private static final Map<UUID, State> LAST_SENT = new HashMap<>();
	private static final Map<UUID, Integer> ACTIONS = new HashMap<>();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GuideProgress::tick);
		ServerPlayerEvents.LEAVE.register(player -> {
			LAST_SENT.remove(player.getUUID());
			ACTIONS.remove(player.getUUID());
		});
	}

	// ================================================================== evaluation

	/** Every step of the player: automatic checks on the server, manual ticks from the saved data. */
	public static State evaluate(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		Set<String> ticks = manualTicks(data);
		BitSet done = new BitSet(GuideChapters.count());
		BitSet manual = new BitSet(GuideChapters.count());
		for (GuideChapters.Step step : GuideChapters.steps()) {
			boolean finished;
			if (step.manual()) {
				finished = ticks.contains(step.id());
				if (finished) {
					manual.set(step.index());
				}
			} else {
				finished = check(player, data, step.check());
			}
			if (finished) {
				done.set(step.index());
			}
		}
		return new State(done, manual, currentChapter(done, player, data));
	}

	/**
	 * The player's current chapter (0-based save index, {@link GuideChapters#CHAPTER_COUNT} when all is done): the first
	 * chapter in display order whose realm and extra check the player meets, whose {@code pastRealm} the player has not
	 * reached yet, and which still has a core step left (see {@link GuideChapters#currentChapter}). Manual and optional
	 * steps never hold it (integration audit T1).
	 */
	public static int currentChapter(BitSet done, ServerPlayer player, CultivationData data) {
		return GuideChapters.currentChapter(done, new GuideChapters.Gate() {
			@Override
			public boolean open(GuideChapters.Chapter chapter) {
				return (chapter.minRealm() == null || reached(data, chapter.minRealm(), 1, 1))
						&& (chapter.requires() == null || check(player, data, chapter.requires()));
			}

			@Override
			public boolean past(GuideChapters.Chapter chapter) {
				return chapter.pastRealm() != null && reached(data, chapter.pastRealm(), 1, 1);
			}
		});
	}

	/** Evaluates one check id (see {@link GuideChapters}); unknown ids and any failure count as not done. */
	public static boolean check(ServerPlayer player, CultivationData data, String check) {
		if (check == null || check.isEmpty()) {
			return false;
		}
		if (check.indexOf('|') >= 0) {
			// alternatives: "adv:a|adv:b" is done when any one is
			for (String alternative : check.split("\\|")) {
				if (check(player, data, alternative)) {
					return true;
				}
			}
			return false;
		}
		try {
			String[] part = check.split(":");
			return switch (part[0]) {
				case "root" -> data.rootTested();
				case "dao" -> data.dao() != null;
				case "cave" -> CaveAbode.read(data) != null;
				case "heart" -> data.heartDemonsDefeated() > 0;
				case "ascended" -> data.ascended();
				case "bar_mortal" -> !data.realm().isMortal() || data.barFull();
				case "hall" -> Halls.hallOf(player.getUUID()) != null;
				case "template" -> SectBuild.templateSize(player.getUUID()) > 0;
				case "adv" -> part.length > 1 && advancement(player, part[1]);
				case "rank" -> part.length > 1 && reached(data, part[1], 1, 1);
				case "at" -> part.length > 3 && reached(data, part[1], Integer.parseInt(part[2]), Integer.parseInt(part[3]));
				case "med" -> part.length > 1 && data.meditationTicks() >= Long.parseLong(part[1]) * 1200L;
				case "hall_level" -> part.length > 1 && Halls.level(player.getUUID()) >= Integer.parseInt(part[1]);
				case "disciples" -> part.length > 1 && Disciples.aliveCount(data) >= Integer.parseInt(part[1]);
				case "tier" -> part.length > 1 && SectBuild.tier(player.getUUID()) >= Integer.parseInt(part[1]);
				case "stars" -> part.length > 1 && ImmortalPerks.stars(data) >= Integer.parseInt(part[1]);
				case "trib" -> part.length > 1 && data.tribulationsSurvived() >= Integer.parseInt(part[1]);
				default -> false;
			};
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** True when the player's rank is at least {@code realm} layer {@code layer} (1-9) stage {@code stage} (1-4). */
	private static boolean reached(CultivationData data, String realmId, int layer, int stage) {
		Realm realm = Realm.byId(realmId);
		if (realm == null) {
			return false;
		}
		Rank target = new Rank(realm, layer - 1, stage - 1);
		return data.rank().step() >= target.step();
	}

	/** True when the player completed the mod's advancement {@code name}. */
	private static boolean advancement(ServerPlayer player, String name) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}
		AdvancementHolder holder = server.getAdvancements().get(Kultivator.id(name));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	// ================================================================== manual ticks

	/** The ids the player ticked by hand (read only; ids of removed steps are included but never shown). */
	public static Set<String> manualTicks(CultivationData data) {
		CompoundTag ticks = data.extra().getCompoundOrEmpty(KEY).getCompoundOrEmpty(MANUAL);
		Set<String> ids = new HashSet<>();
		for (String id : ticks.keySet()) {
			if (ticks.getBooleanOr(id, false)) {
				ids.add(id);
			}
		}
		return ids;
	}

	/** Toggles manual step {@code id}; false when it is not a manual step of the guide. */
	public static boolean toggle(ServerPlayer player, String id) {
		GuideChapters.Step step = GuideChapters.byId(id);
		if (step == null || !step.manual()) {
			return false;
		}
		CultivationData data = Cultivators.get(player);
		CompoundTag extra = data.extra();
		CompoundTag root = extra.getCompoundOrEmpty(KEY).copy();
		CompoundTag ticks = root.getCompoundOrEmpty(MANUAL).copy();
		if (ticks.getBooleanOr(id, false)) {
			ticks.remove(id);
		} else {
			ticks.putBoolean(id, true);
		}
		root.put(MANUAL, ticks);
		extra.put(KEY, root);
		Cultivators.dirty(player);
		return true;
	}

	/** Clears every manual tick ({@code uji panduan reset}). */
	public static void resetManual(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		CompoundTag root = data.extra().getCompoundOrEmpty(KEY).copy();
		root.remove(MANUAL);
		data.extra().put(KEY, root);
		Cultivators.dirty(player);
	}

	// ================================================================== network

	/** Evaluates and sends the player's progress; {@code open} asks the client to open the guide screen. */
	public static void sync(ServerPlayer player, boolean open) {
		if (!ServerPlayNetworking.canSend(player, Payloads.GuideSync.TYPE)) {
			return;
		}
		State state = evaluate(player);
		Net.send(player, new Payloads.GuideSync(state.done(), state.manual(), state.current(), GuideChapters.count(), open));
		LAST_SENT.put(player.getUUID(), state);
	}

	/** A GuideAction from the guide screen: {@code open} or {@code tick <id>}. */
	public static void handle(ServerPlayer player, Payloads.GuideAction action) {
		if (player == null || player.isRemoved() || action == null || action.action() == null) {
			return;
		}
		int used = ACTIONS.merge(player.getUUID(), 1, Integer::sum);
		if (used > ACTIONS_PER_TICK) {
			return;
		}
		String text = action.action().trim();
		if (text.length() > MAX_ACTION_LENGTH) {
			return;
		}
		if (text.equals(Payloads.GuideAction.OPEN)) {
			sync(player, false);
		} else if (text.startsWith(Payloads.GuideAction.TICK + " ")) {
			toggle(player, text.substring(Payloads.GuideAction.TICK.length() + 1).trim());
			// Always answer, so a refused tick snaps the client's box back.
			sync(player, false);
		}
	}

	/** Every 5 seconds per player: a fresh sync when the progress changed (staggered by entity id). */
	private static void tick(MinecraftServer server) {
		ACTIONS.clear();
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isRemoved()) {
				continue;
			}
			State last = LAST_SENT.get(player.getUUID());
			int interval = last == null ? FIRST_INTERVAL : PUSH_INTERVAL;
			if (Math.floorMod(now + player.getId(), interval) != 0) {
				continue;
			}
			if (!ServerPlayNetworking.canSend(player, Payloads.GuideSync.TYPE)) {
				continue;
			}
			State state = evaluate(player);
			if (!state.equals(last)) {
				Net.send(player, new Payloads.GuideSync(state.done(), state.manual(), state.current(), GuideChapters.count(), false));
				LAST_SENT.put(player.getUUID(), state);
			}
		}
	}

	// ================================================================== commands

	/** {@code /kultivasi panduan} (every player) and {@code uji panduan reset|info} (op). */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> uji) {
		root.then(Commands.literal("panduan").executes(ctx -> {
			sync(ctx.getSource().getPlayerOrException(), true);
			return 1;
		}));
		uji.then(Commands.literal("panduan")
				.then(Commands.literal("reset").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					resetManual(player);
					sync(player, false);
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.guide.cmd.reset")), false);
					return 1;
				}))
				.then(Commands.literal("info").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					State state = evaluate(player);
					// "Bab N" is the place in the display order, as the book shows it
					int chapter = state.current() >= GuideChapters.CHAPTER_COUNT ? GuideChapters.CHAPTER_COUNT
							: GuideChapters.chapter(state.current()).number();
					ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.guide.cmd.info",
							String.valueOf(state.done().cardinality()), String.valueOf(GuideChapters.count()),
							String.valueOf(state.manual().cardinality()), String.valueOf(chapter))), false);
					return 1;
				})));
	}

	private GuideProgress() {
	}
}
