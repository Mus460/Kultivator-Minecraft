package dev.kultivator.duel.session;

import dev.kultivator.alam.Alam;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.duel.DuelConfig;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.ai.DuelistBrain;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.clash.ClashClock;
import dev.kultivator.duel.clash.Posture;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.net.DuelNet;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.duel.voice.DuelVoices;
import dev.kultivator.sect.Sparring;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The INVITE phase of formal duels (DESIGN 35.2.2): challenges from players (screen, command), offers from wandering
 * NPCs, the answers (a player answers through the offer pop-up, the chat chips or the command; a disciple or an NPC
 * answers 20-40 ticks later through {@link DuelistBrain#answer}), expiry after {@code answerSeconds}, and the 10 s
 * anti-spam after a refusal. An accepted challenge becomes a {@link DuelSession}.
 */
public final class DuelChallenges {

	/** One open challenge. */
	public static final class Challenge {
		final int id;
		/** Who challenges: a player, or the NPC that offered. */
		final LivingEntity from;
		/** Who answers: a player, a disciple, an NPC. */
		final LivingEntity to;
		/** The second disciple of a watched duel (TONTON); null otherwise. */
		final @Nullable LivingEntity second;
		/** The player behind the challenge (the challenger, or the player an NPC offered to). */
		final ServerPlayer player;
		final int pair;
		final DuelRuleSet rules;
		final long created;
		final long expires;
		final long aiAnswerAt;

		Challenge(int id, LivingEntity from, LivingEntity to, @Nullable LivingEntity second, ServerPlayer player, int pair, DuelRuleSet rules,
				long created, long expires, long aiAnswerAt) {
			this.id = id;
			this.from = from;
			this.to = to;
			this.second = second;
			this.player = player;
			this.pair = pair;
			this.rules = rules;
			this.created = created;
			this.expires = expires;
			this.aiAnswerAt = aiAnswerAt;
		}

		public int id() {
			return id;
		}

		public LivingEntity from() {
			return from;
		}

		public LivingEntity to() {
			return to;
		}

		public int pair() {
			return pair;
		}

		public DuelRuleSet rules() {
			return rules;
		}

		/** True when the NPC offered it to the player (the player answers). */
		boolean npcOffer() {
			return !(from instanceof ServerPlayer);
		}
	}

	/** Refused by the same target: no new challenge for this long (anti-spam). */
	public static final int DECLINE_COOLDOWN = 200;
	/** NPC offers reach players this close who hold a blade. */
	public static final double NPC_OFFER_RANGE = 8.0D;
	/** An NPC offer scan runs this often; each eligible NPC offers with this chance. */
	private static final int NPC_SCAN_EVERY = 100;
	private static final float NPC_OFFER_CHANCE = 0.25F;

	private static final Map<Integer, Challenge> OPEN = new LinkedHashMap<>();
	private static final Map<String, Long> DECLINED = new HashMap<>();
	private static final Map<UUID, Long> NPC_OFFERED = new HashMap<>();
	private static int nextId = 1;

	// ================================================================ challenge (C35.S2)

	/**
	 * {@code challenger} challenges {@code target} (and for a watched duel of two own disciples, {@code second}) with
	 * {@code rules}. Null when the challenge was sent; otherwise the reason it was refused.
	 */
	public static @Nullable Component challenge(ServerPlayer challenger, LivingEntity target, @Nullable LivingEntity second, DuelRuleSet rules) {
		DuelConfig config = DuelConfig.get();
		if (!config.enabled || !config.formalDuels) {
			return Component.translatable("kultivator.duel.reason.off");
		}
		Component self = selfProblem(challenger);
		if (self != null) {
			return self;
		}
		if (target == null || target == challenger || !target.isAlive() || target.level() != challenger.level()) {
			return Component.translatable("kultivator.duel.reason.no_target");
		}
		if (challenger.distanceTo(target) > config.challengeRange) {
			return Component.translatable("kultivator.duel.reason.too_far", (int) config.challengeRange);
		}
		int pair = pairOf(challenger, target, second);
		if (pair < 0) {
			return Component.translatable("kultivator.duel.reason.cannot", target.getDisplayName());
		}
		Component targetProblem = targetProblem(challenger, target, pair);
		if (targetProblem != null) {
			return targetProblem;
		}
		if (pair == DuelIds.PAIR_TONTON) {
			if (second == null || second == target || second.level() != challenger.level() || challenger.distanceTo(second) > config.challengeRange) {
				return Component.translatable("kultivator.duel.reason.no_target");
			}
			Component secondProblem = targetProblem(challenger, second, pair);
			if (secondProblem != null) {
				return secondProblem;
			}
		}
		if (pair == DuelIds.PAIR_MURID || pair == DuelIds.PAIR_TONTON) {
			// A disciple never stakes items (35.2.4); Honour only between master and disciple.
			if (rules.stake().items() || pair == DuelIds.PAIR_TONTON && rules.stake() != DuelRuleSet.StakeKind.TANPA) {
				rules = rules.withoutStake();
			}
		}
		Component stake = DuelStakes.validate(challenger, target, pair, rules);
		if (stake != null) {
			return stake;
		}
		if (!DuelSessions.room()) {
			return Component.translatable("kultivator.duel.reason.full");
		}
		long now = ClashClock.now();
		Long declined = DECLINED.get(key(challenger, target));
		if (declined != null && now - declined < DECLINE_COOLDOWN) {
			return Component.translatable("kultivator.duel.reason.spam");
		}
		// One open challenge per challenger: a new one replaces the old.
		OPEN.values().removeIf(open -> open.from == challenger);
		int id = nextId();
		boolean aiAnswers = !(target instanceof ServerPlayer);
		ServerLevel level = (ServerLevel) challenger.level();
		long aiAt = aiAnswers ? now + 20 + level.getRandom().nextInt(21) : Long.MAX_VALUE;
		Challenge open = new Challenge(id, challenger, target, second, challenger, pair, rules, now,
				now + config.answerTicks(), aiAt);
		OPEN.put(id, open);
		if (target instanceof ServerPlayer targetPlayer) {
			sendOffer(open, targetPlayer, challenger);
		}
		Msg.chat(challenger, Component.translatable(pair == DuelIds.PAIR_TONTON ? "kultivator.duel.msg.watch_sent" : "kultivator.duel.msg.sent",
				target.getDisplayName(), second == null ? Component.empty() : second.getDisplayName()).withColor(0xE8D9A8));
		return null;
	}

	/** The payload of the challenge screen: ids resolved in the player's level, the rules clamped. */
	public static void fromPayload(ServerPlayer player, DuelPayloads.DuelChallenge payload) {
		ServerLevel level = player.level();
		Entity target = level.getEntity(payload.targetId());
		Entity second = payload.secondId() >= 0 ? level.getEntity(payload.secondId()) : null;
		if (!(target instanceof LivingEntity living)) {
			Msg.chat(player, Component.translatable("kultivator.duel.msg.refused", Component.translatable("kultivator.duel.reason.no_target"))
					.withColor(0xE0A060));
			return;
		}
		DuelRuleSet rules = DuelRuleSet.of(payload);
		Component why = challenge(player, living, second instanceof LivingEntity secondLiving ? secondLiving : null, rules);
		if (why != null) {
			Msg.chat(player, Component.translatable("kultivator.duel.msg.refused", why).withColor(0xE0A060));
		}
	}

	/**
	 * {@code player}'s answer (C35.S2): to a challenge ({@code TERIMA}, {@code TOLAK}, {@code TARIK} for the challenger's
	 * own) or to the running duel ({@code MENYERAH}, {@code TUNDA}). {@code id} ≤ 0 means "the newest one for me".
	 */
	public static void answer(ServerPlayer player, int id, int answer) {
		switch (answer) {
			case DuelIds.ANSWER_MENYERAH -> {
				dev.kultivator.duel.Duel.surrender(player);
				return;
			}
			case DuelIds.ANSWER_TUNDA -> {
				DuelSession session = DuelSessions.byEntity(player);
				if (session != null) {
					session.tunda(player);
				}
				return;
			}
			case DuelIds.ANSWER_TARIK -> {
				Challenge own = id > 0 ? OPEN.get(id) : newestFrom(player);
				if (own != null && own.from == player) {
					OPEN.remove(own.id);
					Msg.chat(player, Component.translatable("kultivator.duel.msg.withdrawn").withColor(0xE8D9A8));
					if (own.to instanceof ServerPlayer target) {
						Msg.chat(target, Component.translatable("kultivator.duel.msg.withdrawn_other", player.getDisplayName()).withColor(0xE8D9A8));
					}
				}
				return;
			}
			default -> {
			}
		}
		Challenge challenge = id > 0 ? OPEN.get(id) : newestFor(player);
		if (challenge == null || answerer(challenge) != player) {
			Msg.chat(player, Component.translatable("kultivator.duel.msg.no_challenge").withColor(0xE0A060));
			return;
		}
		OPEN.remove(challenge.id);
		if (answer == DuelIds.ANSWER_TERIMA) {
			accept(challenge, null);
		} else {
			decline(challenge, null);
		}
	}

	/** {@code npc} offers {@code player} a duel (35.7.6): at most once per player per {@code npcOfferMinutes}. */
	public static boolean npcOffer(LivingEntity npc, ServerPlayer player) {
		DuelConfig config = DuelConfig.get();
		if (!config.enabled || !config.formalDuels || !config.npcOffers || npc == null || !npc.isAlive() || npc.level() != player.level()) {
			return false;
		}
		long now = ClashClock.now();
		Long last = NPC_OFFERED.get(player.getUUID());
		if (last != null && now - last < config.npcOfferMinutes * 1200L) {
			return false;
		}
		DuelistProfile profile = DuelistRegistry.profile(npc);
		if (profile == null || npc.distanceTo(player) > NPC_OFFER_RANGE + 4.0D || selfProblem(player) != null
				|| DuelSessions.byEntity(npc) != null || DuelRules.inDuel(npc) || !DuelSessions.room() || !WeaponClass.of(player).blade()) {
			return false;
		}
		for (Challenge open : OPEN.values()) {
			if (open.player == player || open.from == npc || open.to == npc) {
				return false;
			}
		}
		NPC_OFFERED.put(player.getUUID(), now);
		int gap = Posture.realm(player) - Posture.realm(npc);
		DuelRuleSet rules = DuelRuleSet.suggested(gap).withRadius(config.ringRadiusDefault)
				.withStake(DuelRuleSet.StakeKind.KEHORMATAN, 0, 0, null);
		if (!config.honour) {
			rules = rules.withoutStake();
		}
		Challenge open = new Challenge(nextId(), npc, player, null, player, DuelIds.PAIR_NPC, rules, now, now + config.answerTicks(), Long.MAX_VALUE);
		OPEN.put(open.id, open);
		sendOffer(open, player, npc);
		String line = profile.style() == dev.kultivator.duel.ai.DuelStyle.SANTO_PEDANG ? "kota.wanderer_dao" : "kota.rival_claim";
		DuelVoices.npcLine(npc, VoiceEvents.exists(line) ? line : DuelIds.LINE_OPEN);
		return true;
	}

	// ================================================================ accept / decline

	private static void accept(Challenge challenge, @Nullable Component aiReason) {
		LivingEntity a = challenge.pair == DuelIds.PAIR_TONTON ? challenge.to : challenge.from;
		LivingEntity b = challenge.pair == DuelIds.PAIR_TONTON ? challenge.second : challenge.to;
		if (a == null || b == null || !a.isAlive() || !b.isAlive() || a.level() != b.level()) {
			Msg.chat(challenge.player, Component.translatable("kultivator.duel.msg.refused", Component.translatable("kultivator.duel.reason.gone",
					b == null ? Component.empty() : b.getDisplayName())).withColor(0xE0A060));
			return;
		}
		Component problem = selfProblem(challenge.player);
		if (problem == null && b instanceof ServerPlayer pb && pb != challenge.player) {
			problem = selfProblem(pb);
		}
		if (problem == null && a instanceof ServerPlayer pa && pa != challenge.player) {
			problem = selfProblem(pa);
		}
		// The slips: the challenger's from the rules, the other's from their hand or inventory now.
		String slipA = null;
		String slipB = null;
		if (problem == null && challenge.rules.stake() == DuelRuleSet.StakeKind.GULUNGAN) {
			slipA = a instanceof ServerPlayer ? challenge.rules.slipSpec() : null;
			slipB = b instanceof ServerPlayer pb ? DuelStakes.ownSlip(pb) : null;
			if (b instanceof ServerPlayer && slipB == null) {
				problem = Component.translatable("kultivator.duel.reason.stake_slip");
			}
			if (!(b instanceof ServerPlayer)) {
				DuelistProfile profile = DuelistRegistry.profile(b);
				slipB = profile != null ? profile.slipSpec() : null;
			}
		}
		if (problem == null && challenge.rules.stake() == DuelRuleSet.StakeKind.BATU_ROH) {
			for (LivingEntity side : new LivingEntity[] {a, b}) {
				if (side instanceof ServerPlayer player && !DuelStakes.has(player, challenge.rules, null)) {
					problem = Component.translatable("kultivator.duel.reason.stake_lost", player.getDisplayName());
				}
			}
		}
		if (problem != null) {
			notifyBoth(challenge, Component.translatable("kultivator.duel.msg.refused", problem).withColor(0xE0A060));
			return;
		}
		Component[] why = new Component[1];
		ServerPlayer challenger = challenge.pair == DuelIds.PAIR_TONTON || challenge.from instanceof ServerPlayer ? challenge.player : null;
		DuelSession session = DuelSessions.start(challenge.pair, challenge.rules, a, b, challenger, slipA, slipB, why);
		if (session == null) {
			notifyBoth(challenge, Component.translatable("kultivator.duel.msg.refused", why[0] == null ? Component.empty() : why[0])
					.withColor(0xE0A060));
			return;
		}
		notifyBoth(challenge, Component.translatable("kultivator.duel.msg.accepted", challenge.to.getDisplayName()).withColor(0x9FE0A0));
	}

	private static void decline(Challenge challenge, @Nullable Component reason) {
		DECLINED.put(key(challenge.from, challenge.to), ClashClock.now());
		Component line = reason == null ? Component.translatable("kultivator.duel.msg.declined", challenge.to.getDisplayName())
				: Component.translatable("kultivator.duel.msg.declined_why", challenge.to.getDisplayName(), reason);
		notifyBoth(challenge, line.copy().withColor(0xE0A060));
	}

	private static void notifyBoth(Challenge challenge, Component message) {
		if (challenge.from instanceof ServerPlayer from && !from.hasDisconnected()) {
			Msg.chat(from, message);
		}
		if (challenge.to instanceof ServerPlayer to && to != challenge.from && !to.hasDisconnected()) {
			Msg.chat(to, message);
		}
	}

	// ================================================================ tick

	/** Every server tick: expiries, the AI answers, the NPC offer scan. */
	public static void tick(MinecraftServer server) {
		long now = ClashClock.now();
		if (!OPEN.isEmpty()) {
			List<Challenge> due = new ArrayList<>();
			for (Iterator<Challenge> it = OPEN.values().iterator(); it.hasNext(); ) {
				Challenge open = it.next();
				if (!open.from.isAlive() || !open.to.isAlive() || open.player.hasDisconnected()) {
					it.remove();
					continue;
				}
				if (now >= open.expires) {
					it.remove();
					notifyBoth(open, Component.translatable("kultivator.duel.msg.expired").withColor(0xB0A890));
					continue;
				}
				if (now >= open.aiAnswerAt) {
					it.remove();
					due.add(open);
				}
			}
			for (Challenge open : due) {
				Component reason = DuelistBrain.answer(open.to, open.player, open.rules, open.pair);
				if (reason == null) {
					accept(open, null);
				} else {
					decline(open, reason);
				}
			}
		}
		if (now % NPC_SCAN_EVERY == 0) {
			scanOffers(server, now);
		}
		if (now % 1200 == 0) {
			DECLINED.values().removeIf(time -> now - time > DECLINE_COOLDOWN);
			long gap = DuelConfig.get().npcOfferMinutes * 1200L;
			NPC_OFFERED.values().removeIf(time -> now - time > gap);
		}
	}

	/** NPCs that offer duels look for a blade-holding player within 8 blocks (35.7.6). */
	private static void scanOffers(MinecraftServer server, long now) {
		DuelConfig config = DuelConfig.get();
		if (!config.enabled || !config.formalDuels || !config.npcOffers) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || player.isCreative() || !WeaponClass.of(player).blade()) {
				continue;
			}
			Long last = NPC_OFFERED.get(player.getUUID());
			if (last != null && now - last < config.npcOfferMinutes * 1200L) {
				continue;
			}
			ServerLevel level = player.level();
			AABB box = player.getBoundingBox().inflate(NPC_OFFER_RANGE);
			for (LivingEntity npc : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !(e instanceof ServerPlayer))) {
				DuelistProfile profile = DuelistRegistry.profile(npc);
				if (profile == null || !profile.offersChallenges()) {
					continue;
				}
				if (level.getRandom().nextFloat() < NPC_OFFER_CHANCE) {
					npcOffer(npc, player);
				}
				break;
			}
		}
	}

	/** A player left: their open challenges go. */
	public static void forget(ServerPlayer player) {
		OPEN.values().removeIf(open -> open.player == player || open.from == player || open.to == player);
	}

	public static void clear() {
		OPEN.clear();
		DECLINED.clear();
		NPC_OFFERED.clear();
	}

	// ================================================================ direct starts (tests)

	/**
	 * Starts a duel at once without the invite ({@code uji duel mulai}, {@code boneka}, {@code pasangan}): the same
	 * session, the same checks of the session itself. Null when started, else the reason.
	 */
	public static @Nullable Component startDirect(@Nullable ServerPlayer challenger, LivingEntity a, LivingEntity b, int pair, DuelRuleSet rules) {
		Component[] why = new Component[1];
		DuelSession session = DuelSessions.start(pair, rules.stake().items() ? rules.withoutStake() : rules, a, b, challenger, null, null, why);
		return session == null ? (why[0] == null ? Component.translatable("kultivator.duel.reason.error") : why[0]) : null;
	}

	// ================================================================ checks

	/** Why {@code player} cannot duel now, or null. */
	public static @Nullable Component selfProblem(ServerPlayer player) {
		if (player.isSpectator() || player.isCreative()) {
			return Component.translatable("kultivator.duel.reason.mode", player.getDisplayName());
		}
		if (DuelSessions.byEntity(player) != null || DuelRules.inDuel(player) || Sparring.inDuel(player)) {
			return Component.translatable("kultivator.duel.reason.busy");
		}
		if (Alam.inRun(player) || Alam.isRealmLevel(player.level())) {
			return Component.translatable("kultivator.duel.reason.realm", player.getDisplayName());
		}
		return null;
	}

	/** The pair kind of a challenge (35.2.1), or −1 when {@code target} cannot be challenged. */
	public static int pairOf(ServerPlayer challenger, LivingEntity target, @Nullable LivingEntity second) {
		if (target instanceof ServerPlayer) {
			return DuelIds.PAIR_PVP;
		}
		if (target instanceof DiscipleEntity disciple && disciple.role() == DiscipleEntity.Role.DISCIPLE) {
			if (!disciple.isOwnedBy(challenger)) {
				return -1;
			}
			if (second instanceof DiscipleEntity other && other != disciple && other.isOwnedBy(challenger)
					&& other.role() == DiscipleEntity.Role.DISCIPLE) {
				return DuelIds.PAIR_TONTON;
			}
			return DuelIds.PAIR_MURID;
		}
		return DuelistRegistry.challengeable(target) ? DuelIds.PAIR_NPC : -1;
	}

	/** Why {@code target} cannot be challenged by {@code challenger} now, or null. */
	private static @Nullable Component targetProblem(ServerPlayer challenger, LivingEntity target, int pair) {
		if (DuelSessions.byEntity(target) != null || DuelRules.inDuel(target)) {
			return Component.translatable("kultivator.duel.reason.target_busy", target.getDisplayName());
		}
		if (target instanceof ServerPlayer player) {
			if (!challenger.level().isPvpAllowed()) {
				return Component.translatable("kultivator.duel.reason.pvp_off");
			}
			if (!challenger.canHarmPlayer(player)) {
				return Component.translatable("kultivator.duel.reason.team", player.getDisplayName());
			}
			if (Sparring.inDuel(player)) {
				return Component.translatable("kultivator.duel.reason.target_busy", target.getDisplayName());
			}
			if (player.isSpectator() || player.isCreative()) {
				return Component.translatable("kultivator.duel.reason.mode", player.getDisplayName());
			}
			for (Challenge open : OPEN.values()) {
				if (open.to == player && open.from != challenger) {
					return Component.translatable("kultivator.duel.reason.target_busy", target.getDisplayName());
				}
			}
		}
		if (target instanceof DiscipleEntity disciple && disciple.activity() != 0 && disciple.activity() != DiscipleEntity.ACTIVITY_DUELLIST
				&& pair != DuelIds.PAIR_NPC) {
			return Component.translatable("kultivator.duel.reason.disciple_busy", target.getDisplayName());
		}
		return null;
	}

	// ================================================================ helpers

	private static void sendOffer(Challenge open, ServerPlayer to, LivingEntity from) {
		DuelRuleSet rules = open.rules;
		int expires = (int) Math.max(0L, Math.min(32767L, open.expires - open.created));
		DuelPayloads.DuelChallengeOffer offer = new DuelPayloads.DuelChallengeOffer(open.id, from.getId(), from.getDisplayName(),
				DuelSession.rankLine(from), rules.bytes(), rules.stake().ordinal(), rules.stakeGrade(), rules.stakeCount(),
				DuelStakes.slipName(rules.slipSpec()), expires, open.pair);
		DuelNet.send(to, offer);
		MutableComponent accept = Component.translatable("kultivator.duel.chip.accept").withStyle(style -> style.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand("/kultivasi duel terima " + open.id)));
		MutableComponent refuse = Component.translatable("kultivator.duel.chip.decline").withStyle(style -> style.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent.RunCommand("/kultivasi duel tolak " + open.id)));
		Component stake = DuelStakes.line(rules, null);
		Msg.chat(to, Component.translatable("kultivator.duel.msg.offer", from.getDisplayName(), DuelSession.rankLine(from),
				Component.translatable("kultivator.duel.rule.realm." + rules.realm().ordinal()), stake, accept, refuse).withColor(0xE8D9A8));
	}

	private static int nextId() {
		int id = nextId++;
		if (nextId > 1_000_000_000) {
			nextId = 1;
		}
		return id;
	}

	private static String key(Entity from, Entity to) {
		return from.getUUID() + "|" + to.getUUID();
	}

	private static LivingEntity answerer(Challenge challenge) {
		return challenge.npcOffer() ? challenge.player : challenge.to;
	}

	/** The newest open challenge {@code player} has to answer. */
	public static @Nullable Challenge newestFor(ServerPlayer player) {
		Challenge newest = null;
		for (Challenge open : OPEN.values()) {
			if (answerer(open) == player && (newest == null || open.created >= newest.created)) {
				newest = open;
			}
		}
		return newest;
	}

	private static @Nullable Challenge newestFrom(ServerPlayer player) {
		Challenge newest = null;
		for (Challenge open : OPEN.values()) {
			if (open.from == player && (newest == null || open.created >= newest.created)) {
				newest = open;
			}
		}
		return newest;
	}

	/** Open challenges (for {@code uji duel info}). */
	public static int openCount() {
		return OPEN.size();
	}

	private DuelChallenges() {
	}
}
