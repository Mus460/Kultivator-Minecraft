package dev.kultivator.client.duel;

import dev.kultivator.Kultivator;
import dev.kultivator.client.duel.compat.BetterCombatBridge;
import dev.kultivator.client.duel.sound.DuelCrowd;
import dev.kultivator.client.duel.sound.DuelSoundPlayer;
import dev.kultivator.client.duel.ui.ChallengeOfferToast;
import dev.kultivator.client.duel.ui.DuelHud;
import dev.kultivator.client.duel.ui.DuelResultCard;
import dev.kultivator.client.duel.ui.DuelScreen;
import dev.kultivator.client.duel.ui.DuelSettings;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The client side of the sword duel (DESIGN 35.9 module U, contracts C35.U1-U3): receives every server-to-client duel
 * payload, keeps a mirror of the sessions, fighters, swings, blade locks and challenges in range, posts each payload on
 * {@link DuelBus} for the visual (V) and animation (A) modules and U's own sound, crowd and HUD, and sends the player's
 * inputs, challenges, answers and settings. It also owns the defender's prediction of 35.8.2: a Deflect or Perfect
 * Deflect the local client is sure of is shown at once and reconciled with the server's {@code DuelClash}.
 * <p>
 * {@link #init()} (from {@code KultivatorClient}) registers the receivers, the keys, the HUD element (before the chat,
 * hidden with F1 and while a film hides the HUD), the page Gulungan › Dunia › Duel (DESIGN 43.5), then calls
 * {@code DuelFx.init()} (V) and {@code DuelAnim.init()} (A). Client thread only.
 */
public final class DuelClient {

	/** A session not refreshed for this many ticks is forgotten (the server re-sends every 40). */
	private static final int SESSION_STALE = 120;
	/** A fighter not refreshed for this many ticks outside a session is forgotten. */
	private static final int FIGHTER_STALE = 600;
	/** A swing older than this is forgotten. */
	private static final int SWING_STALE = 100;
	/** A blade lock without a sync for this many ticks is over. */
	private static final int BIND_STALE = 8;
	/** A prediction without its clash this long after the impact is withdrawn. */
	private static final int PREDICTION_TIMEOUT = 12;

	private static final Map<Integer, DuelPayloads.DuelSessionSync> SESSIONS = new HashMap<>();
	private static final Map<Integer, Long> SESSION_AT = new HashMap<>();
	private static final Map<Integer, Integer> SESSION_OF = new HashMap<>();
	/** Sessions that just ended (kept 10 s for the result card and the crowd). */
	private static final Map<Integer, DuelPayloads.DuelSessionSync> RECENT = new HashMap<>();
	private static final Map<Integer, Long> RECENT_AT = new HashMap<>();
	private static final Map<Integer, DuelPayloads.DuelFighterSync> FIGHTERS = new HashMap<>();
	private static final Map<Integer, Long> FIGHTER_AT = new HashMap<>();
	private static final Map<Integer, DuelPayloads.DuelSwing> SWINGS = new HashMap<>();
	private static final Map<Integer, Long> SWING_AT = new HashMap<>();
	private static final Map<Integer, DuelPayloads.DuelBindSync> BINDS = new HashMap<>();
	private static final Map<Integer, Long> BIND_AT = new HashMap<>();
	private static final List<Offer> OFFERS = new ArrayList<>();
	private static final List<Prediction> PREDICTIONS = new ArrayList<>();
	private static @Nullable DuelPayloads.DuelResult lastResult;
	private static int lastPrefsSent = -1;
	private static int predictedTotal;
	private static int predictedRight;
	/** The player's own record from the server ({@code DuelCatatan}, 43.14 S2), or null until asked and answered. */
	private static @Nullable CompoundTag catatan;

	/** A challenge waiting for an answer: the offer and the client tick it arrived. */
	public record Offer(DuelPayloads.DuelChallengeOffer offer, long receivedAt) {
		/** Ticks until it lapses (by the client's clock). */
		public int ticksLeft() {
			return (int) Math.max(0L, offer.expiresTicks() - (DuelClock.clientTicks() - receivedAt));
		}
	}

	/** The defender's own prediction of a result (35.8.2). */
	private record Prediction(int attackerId, int swingId, int outcome, long impact, long madeAt) {
	}

	private static boolean initialized;

	// ================================================================== init

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		DuelKeys.init();
		ClientPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelSessionSync.TYPE, (p, context) -> onSession(p));
		ClientPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelFighterSync.TYPE, (p, context) -> onFighter(p));
		ClientPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelSwing.TYPE, (p, context) -> onSwing(p));
		ClientPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelClash.TYPE, (p, context) -> onClash(p));
		ClientPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelBindSync.TYPE, (p, context) -> onBind(p));
		ClientPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelResult.TYPE, (p, context) -> onResult(p));
		ClientPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelChallengeOffer.TYPE, (p, context) -> onOffer(p));
		ClientPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelPreview.TYPE, (p, context) -> onPreview(p));
		ClientPlayNetworking.registerGlobalReceiver(DuelPayloads.DuelCatatan.TYPE, (p, context) -> catatan = p.data());
		ClientTickEvents.END_CLIENT_TICK.register(DuelClient::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			lastPrefsSent = -1;
			client.execute(DuelClient::sendPrefs);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(DuelClient::clearAll));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Kultivator.id("duel_hud"), new DuelHud());
		DuelSettings.onPrefsChanged(DuelClient::sendPrefs);
		DuelSoundPlayer.init();
		DuelCrowd.init();
		DuelHud.init();
		BetterCombatBridge.init();
		DuelInput.init();
		// Gulungan › Dunia › Duel (35.8.5, 43.5): challenge, running duel, record.
		DuelScreen.daftar();
		// The visual and animation modules (C35.U1).
		dev.kultivator.client.duel.fx.DuelFx.init();
		dev.kultivator.client.duel.anim.DuelAnim.init();
	}

	// ================================================================== receivers

	private static void onSession(DuelPayloads.DuelSessionSync p) {
		DuelClock.observe(p.serverTick(), true);
		DuelPayloads.DuelSessionSync before = SESSIONS.put(p.sessionId(), p);
		SESSION_AT.put(p.sessionId(), DuelClock.clientTicks());
		if (before != null && (before.aId() != p.aId() || before.bId() != p.bId())) {
			SESSION_OF.remove(before.aId());
			SESSION_OF.remove(before.bId());
		}
		if (p.aId() >= 0) {
			SESSION_OF.put(p.aId(), p.sessionId());
		}
		if (p.bId() >= 0) {
			SESSION_OF.put(p.bId(), p.sessionId());
		}
		DuelBus.session(p);
		if (involvesLocal(p) && (before == null || before.phase() != p.phase())) {
			DuelHud.phaseChanged(p, before == null ? -1 : before.phase());
			sendPrefsIfModeChanged();
		}
		if (p.phase() == DuelIds.PHASE_ENDED) {
			dropSession(p.sessionId());
			sendPrefsIfModeChanged();
		}
	}

	private static void onFighter(DuelPayloads.DuelFighterSync p) {
		FIGHTERS.put(p.entityId(), p);
		FIGHTER_AT.put(p.entityId(), DuelClock.clientTicks());
		DuelBus.fighter(p);
	}

	private static void onSwing(DuelPayloads.DuelSwing p) {
		DuelClock.observe(p.startTick(), false);
		SWINGS.put(p.entityId(), p);
		SWING_AT.put(p.entityId(), DuelClock.clientTicks());
		if (isLocal(p.entityId())) {
			DuelInput.serverSwing(p);
		}
		DuelBus.swing(p, false);
	}

	private static void onClash(DuelPayloads.DuelClash p) {
		DuelClock.observe(p.serverTick(), true);
		DuelPayloads.DuelClash post = p;
		Prediction match = null;
		for (Prediction prediction : PREDICTIONS) {
			if (prediction.attackerId() == p.aId() && prediction.swingId() == p.swingA()) {
				match = prediction;
				break;
			}
		}
		if (match != null) {
			PREDICTIONS.remove(match);
			predictedTotal++;
			if (match.outcome() == p.outcome()) {
				predictedRight++;
				post = new DuelPayloads.DuelClash(p.aId(), p.bId(), p.swingA(), p.swingB(), p.outcome(), p.x(), p.y(), p.z(), p.dirA(), p.dirB(),
						p.power(), p.rgbA(), p.rgbB(), p.serverTick(), p.seed(), p.flags() | DuelIds.CF_PREDICTED_MATCH);
			} else {
				// The sparks of the wrong guess fade without a second sound; the true result plays as usual.
				DuelBus.correction(match.swingId(), p.outcome());
			}
		}
		DuelBus.clash(post, false);
		DuelHud.onClash(post, false);
	}

	private static void onBind(DuelPayloads.DuelBindSync p) {
		BINDS.put(p.aId(), p);
		BINDS.put(p.bId(), p);
		long now = DuelClock.clientTicks();
		BIND_AT.put(p.aId(), now);
		BIND_AT.put(p.bId(), now);
		if ((p.flags() & DuelIds.BF_END) != 0) {
			BINDS.remove(p.aId());
			BINDS.remove(p.bId());
		}
		DuelBus.bind(p);
	}

	private static void onResult(DuelPayloads.DuelResult p) {
		lastResult = p;
		DuelBus.result(p);
		DuelResultCard.show(p, sessionById(p.sessionId()));
	}

	private static void onOffer(DuelPayloads.DuelChallengeOffer p) {
		OFFERS.removeIf(offer -> offer.offer().challengeId() == p.challengeId());
		OFFERS.add(new Offer(p, DuelClock.clientTicks()));
		ChallengeOfferToast.show(p);
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(offerLine(p));
		}
	}

	private static void onPreview(DuelPayloads.DuelPreview p) {
		if (p.kind() == DuelIds.PV_BC_INFO) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				player.sendSystemMessage(BetterCombatBridge.info());
				player.sendSystemMessage(Component.translatable("kultivator.duel.ui.predict.stats", predictedRight, predictedTotal)
						.withStyle(ChatFormatting.GRAY));
			}
		}
		DuelBus.preview(p);
	}

	/** The chat line of a challenge: who, the rules in short, then the [Terima] [Tolak] chips (35.2.2). */
	private static Component offerLine(DuelPayloads.DuelChallengeOffer p) {
		MutableComponent line = Component.translatable("kultivator.duel.ui.offer.chat", p.name().copy().withStyle(ChatFormatting.GOLD), p.rankLine())
				.withStyle(ChatFormatting.YELLOW);
		line.append(" ");
		line.append(chip("kultivator.duel.ui.offer.accept", "/kultivasi duel terima", ChatFormatting.GREEN));
		line.append(" ");
		line.append(chip("kultivator.duel.ui.offer.decline", "/kultivasi duel tolak", ChatFormatting.RED));
		return line;
	}

	private static Component chip(String key, String command, ChatFormatting color) {
		return Component.translatable(key).withStyle(Style.EMPTY.withColor(color).withBold(true)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	// ================================================================== tick

	private static void tick(Minecraft minecraft) {
		DuelClock.tick();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		long now = DuelClock.clientTicks();
		pruneMaps(minecraft, now);
		pruneRecent(now);
		pruneOffers();
		prunePredictions();
		DuelInput.tick(minecraft);
		DuelSoundPlayer.tick(minecraft);
		DuelCrowd.tick();
		DuelHud.tick(minecraft);
		ChallengeOfferToast.tick();
		DuelResultCard.tick();
	}

	private static void pruneMaps(Minecraft minecraft, long now) {
		for (Iterator<Map.Entry<Integer, Long>> it = SESSION_AT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Long> entry = it.next();
			if (now - entry.getValue() > SESSION_STALE) {
				int id = entry.getKey();
				it.remove();
				DuelPayloads.DuelSessionSync gone = SESSIONS.remove(id);
				if (gone != null) {
					if (SESSION_OF.getOrDefault(gone.aId(), -1) == id) {
						SESSION_OF.remove(gone.aId());
					}
					if (SESSION_OF.getOrDefault(gone.bId(), -1) == id) {
						SESSION_OF.remove(gone.bId());
					}
				}
			}
		}
		for (Iterator<Map.Entry<Integer, Long>> it = FIGHTER_AT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Long> entry = it.next();
			int id = entry.getKey();
			boolean stale = now - entry.getValue() > (SESSION_OF.containsKey(id) ? FIGHTER_STALE * 4L : FIGHTER_STALE);
			if (stale || minecraft.level.getEntity(id) == null && now - entry.getValue() > 40L) {
				it.remove();
				FIGHTERS.remove(id);
			}
		}
		for (Iterator<Map.Entry<Integer, Long>> it = SWING_AT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Long> entry = it.next();
			if (now - entry.getValue() > SWING_STALE) {
				SWINGS.remove(entry.getKey());
				it.remove();
			}
		}
		for (Iterator<Map.Entry<Integer, Long>> it = BIND_AT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Long> entry = it.next();
			if (now - entry.getValue() > BIND_STALE) {
				BINDS.remove(entry.getKey());
				it.remove();
			}
		}
	}

	private static void pruneRecent(long now) {
		for (Iterator<Map.Entry<Integer, Long>> it = RECENT_AT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Long> entry = it.next();
			if (now - entry.getValue() > 200L) {
				RECENT.remove(entry.getKey());
				it.remove();
			}
		}
	}

	private static void pruneOffers() {
		OFFERS.removeIf(offer -> offer.ticksLeft() <= 0);
	}

	private static void prunePredictions() {
		if (PREDICTIONS.isEmpty()) {
			return;
		}
		long server = DuelClock.serverTick();
		for (Iterator<Prediction> it = PREDICTIONS.iterator(); it.hasNext(); ) {
			Prediction prediction = it.next();
			if (server - prediction.impact() > PREDICTION_TIMEOUT || DuelClock.clientTicks() - prediction.madeAt() > 60L) {
				it.remove();
				predictedTotal++;
				DuelBus.correction(prediction.swingId(), DuelIds.OUT_WHIFF);
			}
		}
	}

	private static void dropSession(int sessionId) {
		DuelPayloads.DuelSessionSync gone = SESSIONS.remove(sessionId);
		SESSION_AT.remove(sessionId);
		if (gone != null) {
			RECENT.put(sessionId, gone);
			RECENT_AT.put(sessionId, DuelClock.clientTicks());
			if (SESSION_OF.getOrDefault(gone.aId(), -1) == sessionId) {
				SESSION_OF.remove(gone.aId());
			}
			if (SESSION_OF.getOrDefault(gone.bId(), -1) == sessionId) {
				SESSION_OF.remove(gone.bId());
			}
		}
	}

	private static void clearAll() {
		SESSIONS.clear();
		SESSION_AT.clear();
		SESSION_OF.clear();
		RECENT.clear();
		RECENT_AT.clear();
		FIGHTERS.clear();
		FIGHTER_AT.clear();
		SWINGS.clear();
		SWING_AT.clear();
		BINDS.clear();
		BIND_AT.clear();
		OFFERS.clear();
		PREDICTIONS.clear();
		lastResult = null;
		catatan = null;
		lastPrefsSent = -1;
		DuelClock.reset();
		DuelInput.clear();
		DuelSoundPlayer.clear();
		DuelCrowd.clear();
		DuelHud.clear();
		ChallengeOfferToast.clear();
		DuelResultCard.clear();
	}

	// ================================================================== prediction (35.8.2)

	/**
	 * Called by {@link DuelInput} when the local player pressed deflect: if an opponent's swing at us is clearly inside
	 * the window, show the result now. Returns the predicted outcome, or -1.
	 */
	static int predictParry(LocalPlayer player, double pressTick, int window, boolean perfectAllowed) {
		if (!DuelClock.offsetStable()) {
			return -1;
		}
		int partner = partnerOf(player.getId());
		if (partner < 0) {
			return -1;
		}
		DuelPayloads.DuelSwing swing = SWINGS.get(partner);
		if (swing == null || swing.windup() == DuelIds.WINDUP_CHARGING
				|| (swing.flags() & (DuelIds.SW_FEINT_CANCEL | DuelIds.SW_REJECTED | DuelIds.SW_PERILOUS_SAPU)) != 0) {
			return -1;
		}
		dev.kultivator.duel.clash.SwingKind kind = dev.kultivator.duel.clash.SwingKind.byOrdinal(swing.kind());
		if (kind.unblockable()) {
			return -1;
		}
		for (Prediction prediction : PREDICTIONS) {
			if (prediction.attackerId() == partner && prediction.swingId() == swing.swingId()) {
				return -1;
			}
		}
		net.minecraft.world.entity.Entity attacker = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(partner);
		if (attacker == null || attacker.distanceTo(player) > 6.0F || !facing(player, attacker, 80.0F)) {
			return -1;
		}
		long impact = (long) swing.startTick() + swing.windup();
		double delta = impact - pressTick;
		int outcome;
		if (perfectAllowed && delta >= 0.25D && delta <= 1.25D) {
			outcome = DuelIds.OUT_PERFECT;
		} else if ((swing.flags() & DuelIds.SW_PERILOUS_MAUT) == 0 && delta >= 2.0D && delta <= window - 1.0D) {
			outcome = DuelIds.OUT_DEFLECT;
		} else {
			return -1;
		}
		PREDICTIONS.add(new Prediction(partner, swing.swingId(), outcome, impact, DuelClock.clientTicks()));
		net.minecraft.world.phys.Vec3 mine = player.position().add(0.0D, player.getBbHeight() * 0.7D, 0.0D);
		net.minecraft.world.phys.Vec3 toward = attacker.position().add(0.0D, attacker.getBbHeight() * 0.7D, 0.0D).subtract(mine);
		net.minecraft.world.phys.Vec3 at = mine.add(toward.normalize().scale(Math.min(0.9D, toward.length() * 0.45D)));
		dev.kultivator.duel.clash.WeaponClass attackerWeapon = dev.kultivator.duel.clash.WeaponClass.byOrdinal(swing.weaponClass());
		dev.kultivator.duel.clash.WeaponClass mineWeapon = dev.kultivator.duel.clash.WeaponClass.of(player);
		DuelPayloads.DuelClash predicted = new DuelPayloads.DuelClash(partner, player.getId(), swing.swingId(), -1, outcome, (float) at.x,
				(float) at.y, (float) at.z, swing.dir(), 255, outcome == DuelIds.OUT_PERFECT ? 200 : 130,
				swing.qiRgb() != 0 ? swing.qiRgb() : attackerWeapon.sparkRgb, mineWeapon.sparkRgb, (int) impact,
				(int) (impact * 31L + partner) & 0x7FFF, 0);
		DuelBus.clash(predicted, true);
		DuelHud.onClash(predicted, true);
		return outcome;
	}

	private static boolean facing(LocalPlayer player, net.minecraft.world.entity.Entity target, float degrees) {
		double dx = target.getX() - player.getX();
		double dz = target.getZ() - player.getZ();
		if (dx * dx + dz * dz < 1.0E-6D) {
			return true;
		}
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
		return Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - player.getYRot())) <= degrees;
	}

	// ================================================================== queries (C35.U2)

	/** The session {@code entityId} takes part in, or null. */
	public static @Nullable DuelPayloads.DuelSessionSync session(int entityId) {
		Integer id = SESSION_OF.get(entityId);
		return id == null ? null : SESSIONS.get(id);
	}

	/** A session by its id (also right after it ended, while its last sync is kept), or null. */
	public static @Nullable DuelPayloads.DuelSessionSync sessionById(int sessionId) {
		DuelPayloads.DuelSessionSync live = SESSIONS.get(sessionId);
		return live != null ? live : RECENT.get(sessionId);
	}

	/** Every session in range. */
	public static Collection<DuelPayloads.DuelSessionSync> sessions() {
		return Collections.unmodifiableCollection(SESSIONS.values());
	}

	/** The local player's own session, or null. */
	public static @Nullable DuelPayloads.DuelSessionSync localSession() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? null : session(player.getId());
	}

	/** The session the local player watches (the nearest in range he is not part of), or null. */
	public static @Nullable DuelPayloads.DuelSessionSync watchedSession() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		DuelPayloads.DuelSessionSync best = null;
		double bestDistance = Double.MAX_VALUE;
		for (DuelPayloads.DuelSessionSync s : SESSIONS.values()) {
			if (s.aId() == player.getId() || s.bId() == player.getId() || s.phase() == DuelIds.PHASE_ENDED || s.phase() == DuelIds.PHASE_INVITE) {
				continue;
			}
			double d = player.distanceToSqr(s.cx(), s.cy(), s.cz());
			if (d < bestDistance && d <= 48.0D * 48.0D) {
				best = s;
				bestDistance = d;
			}
		}
		return best;
	}

	/** The latest posture and state of {@code entityId}, or null. */
	public static @Nullable DuelPayloads.DuelFighterSync fighter(int entityId) {
		return FIGHTERS.get(entityId);
	}

	/** The latest swing of {@code entityId} (≤ 5 s old), or null. */
	public static @Nullable DuelPayloads.DuelSwing swing(int entityId) {
		return SWINGS.get(entityId);
	}

	/** The live blade lock {@code entityId} is in, or null. */
	public static @Nullable DuelPayloads.DuelBindSync bind(int entityId) {
		return BINDS.get(entityId);
	}

	/** The duel partner of {@code entityId} (session or live blade lock), or -1. */
	public static int partnerOf(int entityId) {
		DuelPayloads.DuelSessionSync s = session(entityId);
		if (s != null && s.phase() != DuelIds.PHASE_ENDED) {
			return s.aId() == entityId ? s.bId() : s.bId() == entityId ? s.aId() : -1;
		}
		DuelPayloads.DuelBindSync bind = BINDS.get(entityId);
		if (bind != null) {
			return bind.aId() == entityId ? bind.bId() : bind.aId();
		}
		return -1;
	}

	/** True while the local player fights in a formal duel (phase FIGHT). */
	public static boolean inFight() {
		DuelPayloads.DuelSessionSync s = localSession();
		return s != null && s.phase() == DuelIds.PHASE_FIGHT;
	}

	/** True while the local player is in any phase of a formal duel after the invitation. */
	public static boolean inSession() {
		DuelPayloads.DuelSessionSync s = localSession();
		return s != null && s.phase() != DuelIds.PHASE_INVITE && s.phase() != DuelIds.PHASE_ENDED;
	}

	/** Ticks left of a session's phase, counted on from its last sync. */
	public static int phaseTicksLeft(DuelPayloads.DuelSessionSync s) {
		long at = SESSION_AT.getOrDefault(s.sessionId(), DuelClock.clientTicks());
		return (int) Math.max(0L, s.phaseTicksLeft() - (DuelClock.clientTicks() - at));
	}

	/** Ticks left of a session's time limit (during FIGHT), counted on from its last sync. */
	public static int fightTicksLeft(DuelPayloads.DuelSessionSync s) {
		long at = SESSION_AT.getOrDefault(s.sessionId(), DuelClock.clientTicks());
		long elapsed = s.phase() == DuelIds.PHASE_FIGHT ? DuelClock.clientTicks() - at : 0L;
		return (int) Math.max(0L, s.fightTicksLeft() - elapsed);
	}

	/** The challenges waiting for an answer, oldest first. */
	public static List<Offer> offers() {
		return Collections.unmodifiableList(OFFERS);
	}

	public static @Nullable DuelPayloads.DuelResult lastResult() {
		return lastResult;
	}

	public static boolean isLocal(int entityId) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getId() == entityId;
	}

	private static boolean involvesLocal(DuelPayloads.DuelSessionSync s) {
		return isLocal(s.aId()) || isLocal(s.bId());
	}

	// ================================================================== sending

	/** True when the server has the duel (its payloads are registered on both sides). */
	public static boolean connected() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.getConnection() != null && ClientPlayNetworking.canSend(DuelPayloads.DuelInput.TYPE);
	}

	static void send(CustomPacketPayload payload) {
		if (connected()) {
			ClientPlayNetworking.send(payload);
		}
	}

	/** Answers challenge {@code challengeId} ({@code DuelIds.ANSWER_*}). */
	public static void answer(int challengeId, int answer) {
		send(new DuelPayloads.DuelAnswer(challengeId, answer, 0));
		if (answer == DuelIds.ANSWER_TERIMA || answer == DuelIds.ANSWER_TOLAK) {
			OFFERS.removeIf(offer -> offer.offer().challengeId() == challengeId);
			ChallengeOfferToast.dismiss(challengeId);
		}
	}

	/** Asks the server for the player's own record (answered with a {@code DuelCatatan}: 43.14 S2). */
	public static void mintaCatatan() {
		send(new DuelPayloads.DuelAnswer(0, DuelPayloads.DuelAnswer.ANSWER_CATATAN, 0));
	}

	/** The record the server last sent, or null. */
	public static @Nullable CompoundTag catatan() {
		return catatan;
	}

	/** Sends a challenge (the challenge page). */
	public static void challenge(DuelPayloads.DuelChallenge challenge) {
		send(challenge);
	}

	/** Sends the settings the server needs (kiai, the input mode in use, intro, deflect outside duels). */
	public static void sendPrefs() {
		if (!connected()) {
			return;
		}
		int kiai = switch (DuelSettings.kiai()) {
			case PRIA -> DuelIds.KIAI_MALE;
			case WANITA -> DuelIds.KIAI_FEMALE;
			default -> DuelIds.KIAI_OFF;
		};
		int mode = DuelInput.resolvedMode();
		int flags = (DuelSettings.intro() ? DuelIds.PREF_INTRO_FILM : 0) | (DuelSettings.parryOutside() ? DuelIds.PREF_PARRY_OUTSIDE : 0)
				| DuelIds.PREF_VISUAL_MODULE;
		int packed = kiai | mode << 4 | flags << 8;
		lastPrefsSent = packed;
		ClientPlayNetworking.send(new DuelPayloads.DuelPrefs(kiai, mode, flags));
	}

	/** The resolved input mode depends on being in a formal duel: re-send when it flips. */
	static void sendPrefsIfModeChanged() {
		if (lastPrefsSent < 0 || (lastPrefsSent >> 4 & 0xF) != DuelInput.resolvedMode()) {
			sendPrefs();
		}
	}

	private DuelClient() {
	}
}
