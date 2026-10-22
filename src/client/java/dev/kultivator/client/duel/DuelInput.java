package dev.kultivator.client.duel;

import dev.kultivator.client.ClientCultivation;
import dev.kultivator.client.duel.compat.BetterCombatBridge;
import dev.kultivator.client.duel.ui.DuelSettings;
import dev.kultivator.data.Realm;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.clash.ClashTiming;
import dev.kultivator.duel.clash.SwingDir;
import dev.kultivator.duel.clash.SwingKind;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.net.DuelPayloads;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The player's duel input (DESIGN 35.3.2, 35.3.3, 35.8.1): reads the mouse, the attack and use buttons and the duel keys,
 * turns them into {@code DuelInput} messages (at most 24 per second, rising {@code seq}), and predicts the player's own
 * swings and deflects for the poses and sparks (35.8.2).
 * <p>
 * Modes ({@link DuelSettings.InputMode}, resolved by {@link #resolvedMode()}):
 * <ul>
 * <li><b>KLASIK</b> (formal duel, no Better Combat): {@code DuelInputMixin} cancels {@code Minecraft.startAttack} and
 * {@code continueAttack} while the player holds a weapon in the FIGHT phase (nothing is mined or swung by vanilla). A
 * tap of the attack button is a light cut ({@code SWING}, sent on release, within 3 ticks); held 3 ticks it starts a heavy
 * charge ({@code CHARGE_START}) and its release swings it ({@code CHARGE_RELEASE}). The direction is the mouse movement
 * of the last 150 ms ({@code SwingDir.fromMouse}, the screen pixels after sensitivity estimated from the yaw and pitch
 * turned: vanilla turns 0.15° per pixel); crouching makes a thrust.</li>
 * <li><b>IKUTI_BC</b>: Better Combat swings by itself; {@link BetterCombatBridge} reports each swing as {@code BC_SWING}.</li>
 * <li><b>VANILLA</b>: attacks are vanilla; only deflect and guard are added.</li>
 * </ul>
 * Deflect: a tap of the use button with a weapon (main hand without its own use, off hand empty or without a use and no
 * shield; with Better Combat only while the off hand is empty) or of the {@code parry} key; held 5 ticks it guards. Outside
 * a formal duel the use button only deflects with the setting "Tangkis di luar duel" on and the crosshair on nothing
 * (doors, chests and villagers keep their click). During a Blade Lock the attack presses push (counted, at most 10/s)
 * and deflect is the sudden release; during one's own windup deflect is a feint. A crouch tapped while moving is the
 * Shadow Step (without Combat Roll); deflect + crouch held 2 s surrenders.
 */
public final class DuelInput {

	/** Ticks of holding the attack button before a heavy charge begins (a tap is shorter). */
	public static final int CHARGE_AFTER = 3;
	/** Ticks of holding deflect before it becomes a guard (35.3.2). */
	public static final int GUARD_AFTER = 5;
	/** Ticks of deflect + crouch to surrender (35.2.5). */
	public static final int SURRENDER_TICKS = 40;
	/** The Shadow Step push in blocks per tick (35.3.2). */
	private static final double DODGE_PUSH = 0.55D;
	/** Messages per second (35.3.2, 35.8.2). */
	private static final double RATE = 24.0D;
	/** Blade-lock pushes per second that count (35.3.7). */
	private static final int PUSH_CAP = 10;
	/** Ticks of mouse movement that choose the direction (150 ms). */
	private static final int MOUSE_TICKS = 3;
	/** Vanilla turns the view 0.15° per (sensitivity-scaled) pixel. */
	private static final double DEGREES_PER_PIXEL = 0.15D;

	private static int seq;
	private static double tokens = RATE;
	private static long ticks;

	// the mouse: yaw/pitch turned per tick (a ring of the last ticks)
	private static final float[] YAW = new float[MOUSE_TICKS];
	private static final float[] PITCH = new float[MOUSE_TICKS];
	private static int mouseIndex;
	private static float lastYaw = Float.NaN;
	private static float lastPitch;

	// attack button (KLASIK)
	private static boolean attackHeld;
	private static int attackTicks;
	private static boolean chargeSent;
	private static boolean attackWasDown;

	// use button / parry key
	private static boolean useOwned;
	private static int useTicks;
	private static boolean guardSent;
	private static boolean keyHeld;
	private static int keyTicks;

	// combo, blade lock, dodge, surrender
	private static int comboIndex;
	private static long lastLightAt = Long.MIN_VALUE / 2;
	private static @Nullable SwingDir lastLightDir;
	private static int sameDirCount;
	private static int pendingPushes;
	private static final Deque<Long> PUSH_TIMES = new ArrayDeque<>();
	private static long dodgeReadyAt;
	private static boolean sneakWasDown;
	private static int surrenderTicks;
	private static boolean surrenderSent;

	// own swings: the predicted ones waiting for the server's echo, and the latest known
	private static final Deque<Integer> PREDICTED = new ArrayDeque<>();
	private static @Nullable DuelPayloads.DuelSwing ownSwing;
	private static int predictedCounter;

	// the last deflect press (for the HUD's spam hint)
	private static long lastParryAt = Long.MIN_VALUE / 2;
	private static int lastPrediction = -1;

	static void init() {
		BetterCombatBridge.listen(DuelInput::bcSwing);
	}

	static void clear() {
		attackHeld = false;
		chargeSent = false;
		useOwned = false;
		guardSent = false;
		keyHeld = false;
		pendingPushes = 0;
		PUSH_TIMES.clear();
		PREDICTED.clear();
		ownSwing = null;
		surrenderTicks = 0;
		surrenderSent = false;
		lastYaw = Float.NaN;
	}

	// ================================================================== mode

	/** The mode in use now: {@code DuelIds.INPUT_KLASIK}, {@code INPUT_IKUTI_BC} or {@code INPUT_VANILLA}. */
	public static int resolvedMode() {
		boolean bc = BetterCombatBridge.installed();
		boolean formal = DuelClient.inSession();
		return switch (DuelSettings.input()) {
			case OTOMATIS -> bc ? DuelIds.INPUT_IKUTI_BC : formal ? DuelIds.INPUT_KLASIK : DuelIds.INPUT_VANILLA;
			// Better Combat owns the attack button: the classic cancel never runs beside it (35.9 U, DuelInputMixin).
			case KLASIK -> bc ? DuelIds.INPUT_IKUTI_BC : formal ? DuelIds.INPUT_KLASIK : DuelIds.INPUT_VANILLA;
			case IKUTI_BC -> bc ? DuelIds.INPUT_IKUTI_BC : DuelIds.INPUT_VANILLA;
			case VANILLA -> DuelIds.INPUT_VANILLA;
		};
	}

	private static @Nullable LocalPlayer player() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null || minecraft.gui.screen() != null || minecraft.player.isSpectator() ? null : minecraft.player;
	}

	private static int phase() {
		DuelPayloads.DuelSessionSync s = DuelClient.localSession();
		return s == null ? -1 : s.phase();
	}

	private static boolean inBind(LocalPlayer player) {
		return DuelClient.bind(player.getId()) != null;
	}

	/** True when the local player has duel context: a formal session, a live posture bar, or a partner (sparring). */
	private static boolean context(LocalPlayer player) {
		return DuelClient.inSession() || DuelClient.fighter(player.getId()) != null || DuelClient.partnerOf(player.getId()) >= 0;
	}

	// ================================================================== mixin hooks

	/**
	 * {@code Minecraft.startAttack} at HEAD: true cancels vanilla. KLASIK in a formal duel (weapon held) swallows every
	 * click (the duel reads the button itself); in a Blade Lock any mode but Better Combat turns the click into a push.
	 */
	public static boolean onAttackPress() {
		LocalPlayer player = player();
		if (player == null) {
			return false;
		}
		int mode = resolvedMode();
		if (inBind(player) && mode != DuelIds.INPUT_IKUTI_BC) {
			pendingPushes++;
			return true;
		}
		if (!ownsAttack(player, mode)) {
			return false;
		}
		if (phase() == DuelIds.PHASE_FIGHT && !attackHeld) {
			attackHeld = true;
			attackTicks = 0;
			chargeSent = false;
		}
		return true;
	}

	/** {@code Minecraft.continueAttack} at HEAD: true cancels (no mining or swinging while KLASIK owns the button). */
	public static boolean onAttackContinue() {
		LocalPlayer player = player();
		if (player == null) {
			return false;
		}
		int mode = resolvedMode();
		return inBind(player) && mode != DuelIds.INPUT_IKUTI_BC || ownsAttack(player, mode);
	}

	private static boolean ownsAttack(LocalPlayer player, int mode) {
		if (mode != DuelIds.INPUT_KLASIK || BetterCombatBridge.installed()) {
			return false;
		}
		int phase = phase();
		boolean active = phase == DuelIds.PHASE_INTRO || phase == DuelIds.PHASE_COUNTDOWN || phase == DuelIds.PHASE_FIGHT
				|| phase == DuelIds.PHASE_FINISHER;
		return active && WeaponClass.of(player) != WeaponClass.TANGAN_KOSONG;
	}

	/**
	 * {@code Minecraft.startUseItem} at HEAD: true cancels vanilla. The first call of a press sends the deflect (or the
	 * feint, or the lock release); the repeats while held are swallowed; {@link #tick} turns a long hold into a guard.
	 */
	public static boolean onUsePress() {
		LocalPlayer player = player();
		if (player == null) {
			return false;
		}
		if (useOwned) {
			return true;
		}
		if (!useIsParry(player)) {
			return false;
		}
		useOwned = true;
		useTicks = 0;
		guardSent = false;
		pressParry(player);
		return true;
	}

	/** The conditions of 35.3.2 for the use button to deflect. */
	private static boolean useIsParry(LocalPlayer player) {
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		if (WeaponClass.of(main) == WeaponClass.TANGAN_KOSONG || main.getUseAnimation() != ItemUseAnimation.NONE) {
			return false;
		}
		if (!off.isEmpty() && (off.getUseAnimation() != ItemUseAnimation.NONE || off.has(DataComponents.BLOCKS_ATTACKS))) {
			return false;
		}
		if (BetterCombatBridge.installed() && !off.isEmpty()) {
			// Better Combat's dual wielding attacks with the right click: use the parry key instead.
			return false;
		}
		int phase = phase();
		if (phase == DuelIds.PHASE_FIGHT) {
			return true;
		}
		if (phase >= DuelIds.PHASE_GATHER && phase <= DuelIds.PHASE_FINISHER) {
			// Held by the duel (intro, countdown, finisher): swallow the click, send nothing.
			return true;
		}
		HitResult hit = Minecraft.getInstance().hitResult;
		return DuelSettings.parryOutside() && (hit == null || hit.getType() == HitResult.Type.MISS);
	}

	// ================================================================== tick

	static void tick(Minecraft minecraft) {
		ticks++;
		tokens = Math.min(RATE, tokens + RATE / 20.0D);
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		sampleMouse(player);
		boolean free = minecraft.gui.screen() == null && !player.isSpectator();
		while (DuelKeys.CAMERA.consumeClick()) {
			if (free) {
				DuelSettings.Camera mode = DuelSettings.nextCamera(!DuelClient.inSession());
				player.sendOverlayMessage(Component.translatable("kultivator.duel.ui.camera_now",
						Component.translatable("kultivator.duel.setting.camera." + mode.id())));
			}
		}
		if (!free) {
			releaseAll(player);
			return;
		}
		tickAttack(minecraft, player);
		tickUse(minecraft, player);
		tickParryKey(player);
		tickBind(minecraft, player);
		tickDodge(minecraft, player);
		tickSurrender(minecraft, player);
	}

	/** A screen opened or the player became a spectator: let go of everything held. */
	private static void releaseAll(LocalPlayer player) {
		if (attackHeld && chargeSent) {
			send(player, DuelIds.IN_CHARGE_RELEASE, SwingDir.ATAS.ordinal(), 0);
		}
		attackHeld = false;
		chargeSent = false;
		if (guardSent) {
			send(player, DuelIds.IN_GUARD_OFF, 0, 0);
		}
		guardSent = false;
		useOwned = false;
		keyHeld = false;
		surrenderTicks = 0;
	}

	private static void tickAttack(Minecraft minecraft, LocalPlayer player) {
		boolean down = minecraft.options.keyAttack.isDown();
		int mode = resolvedMode();
		// Better Combat in a lock: count the presses by their edges (its attack is never cancelled).
		if (mode == DuelIds.INPUT_IKUTI_BC && inBind(player) && down && !attackWasDown) {
			pendingPushes++;
		}
		attackWasDown = down;
		if (!attackHeld) {
			return;
		}
		if (phase() != DuelIds.PHASE_FIGHT || !ownsAttack(player, mode)) {
			attackHeld = false;
			if (chargeSent) {
				send(player, DuelIds.IN_CHARGE_RELEASE, heavyDir(player).ordinal(), 0);
			}
			chargeSent = false;
			return;
		}
		if (down) {
			attackTicks++;
			if (!chargeSent && attackTicks >= CHARGE_AFTER) {
				chargeSent = true;
				// arg: the ticks already held, so the server may date the charge back to the press
				send(player, DuelIds.IN_CHARGE_START, SwingDir.ATAS.ordinal(), CHARGE_AFTER);
				predictOwn(player, SwingKind.BERAT, SwingDir.ATAS, true, true);
			}
			return;
		}
		attackHeld = false;
		if (chargeSent) {
			chargeSent = false;
			SwingDir dir = heavyDir(player);
			send(player, DuelIds.IN_CHARGE_RELEASE, dir.ordinal(), 0);
			predictOwn(player, dir == SwingDir.TUSUK ? SwingKind.TUSUK : SwingKind.BERAT, dir, false, true);
		} else {
			SwingDir dir = lightDir(player);
			send(player, DuelIds.IN_SWING, dir.ordinal(), 0);
			predictOwn(player, dir == SwingDir.TUSUK ? SwingKind.TUSUK : SwingKind.RINGAN, dir, false, false);
		}
	}

	private static void tickUse(Minecraft minecraft, LocalPlayer player) {
		if (!useOwned) {
			return;
		}
		if (minecraft.options.keyUse.isDown()) {
			useTicks++;
			if (useTicks == GUARD_AFTER && !guardSent) {
				guardSent = true;
				send(player, DuelIds.IN_GUARD_ON, 0, 0);
			}
			return;
		}
		useOwned = false;
		if (guardSent) {
			guardSent = false;
			send(player, DuelIds.IN_GUARD_OFF, 0, 0);
		}
	}

	private static void tickParryKey(LocalPlayer player) {
		while (DuelKeys.PARRY.consumeClick()) {
			if (!keyHeld) {
				keyHeld = true;
				keyTicks = 0;
				pressParry(player);
			}
		}
		if (!keyHeld) {
			return;
		}
		if (DuelKeys.parryDown()) {
			keyTicks++;
			if (keyTicks == GUARD_AFTER && !guardSent) {
				guardSent = true;
				send(player, DuelIds.IN_GUARD_ON, 0, 0);
			}
			return;
		}
		keyHeld = false;
		if (guardSent && !useOwned) {
			guardSent = false;
			send(player, DuelIds.IN_GUARD_OFF, 0, 0);
		}
	}

	/** One deflect press: the lock release, a feint during one's own windup, or a deflect (with its prediction). */
	private static void pressParry(LocalPlayer player) {
		lastParryAt = ticks;
		if (inBind(player)) {
			send(player, DuelIds.IN_BIND_RELEASE, 0, 0);
			return;
		}
		int phase = phase();
		if (phase >= DuelIds.PHASE_GATHER && phase < DuelIds.PHASE_FIGHT || phase == DuelIds.PHASE_FINISHER) {
			return;
		}
		if (feintPossible()) {
			send(player, DuelIds.IN_FEINT, 0, 0);
			if (attackHeld && chargeSent) {
				attackHeld = false;
				chargeSent = false;
			}
			return;
		}
		double pressTick = DuelClock.serverTickNow(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		DuelPayloads.DuelFighterSync self = DuelClient.fighter(player.getId());
		boolean spam = self != null && (self.flags() & DuelIds.FF_SPAM) != 0;
		WeaponClass weapon = WeaponClass.of(player);
		int window = spam ? ClashTiming.SPAM_WINDOW : ClashTiming.PARRY_WINDOW + weapon.parryWindowDelta;
		lastPrediction = weapon.parriesBlades ? DuelClient.predictParry(player, pressTick, window, !spam) : -1;
		int flags = lastPrediction == DuelIds.OUT_PERFECT ? DuelIds.INF_PREDICT_PERFECT : 0;
		send(player, DuelIds.IN_PARRY, 0, flags);
	}

	/** Own swing still before {@code impact − FEINT_LAST}, or a heavy charge being held. */
	private static boolean feintPossible() {
		DuelPayloads.DuelSwing own = ownSwing;
		if (attackHeld && chargeSent) {
			return true;
		}
		if (own == null || (own.flags() & (DuelIds.SW_FEINT_CANCEL | DuelIds.SW_REJECTED)) != 0) {
			return false;
		}
		SwingKind kind = SwingKind.byOrdinal(own.kind());
		if (kind == SwingKind.PENENTU || kind == SwingKind.BALIK || (own.flags() & DuelIds.SW_PERILOUS) != 0) {
			return false;
		}
		if (own.windup() == DuelIds.WINDUP_CHARGING) {
			return true;
		}
		double now = DuelClock.serverTickNow(0.0F);
		return now < (double) own.startTick() + own.windup() - ClashTiming.FEINT_LAST;
	}

	/** Blade Lock pushes: sent every 2 ticks with their count, at most 10 per second counted. */
	private static void tickBind(Minecraft minecraft, LocalPlayer player) {
		if (!inBind(player)) {
			pendingPushes = 0;
			PUSH_TIMES.clear();
			return;
		}
		while (!PUSH_TIMES.isEmpty() && ticks - PUSH_TIMES.peekFirst() >= 20L) {
			PUSH_TIMES.pollFirst();
		}
		if (pendingPushes <= 0 || ticks % 2L != 0L) {
			return;
		}
		int room = PUSH_CAP - PUSH_TIMES.size();
		int count = Math.min(pendingPushes, Math.max(0, room));
		pendingPushes = 0;
		if (count <= 0) {
			return;
		}
		for (int i = 0; i < count; i++) {
			PUSH_TIMES.addLast(ticks);
		}
		send(player, DuelIds.IN_BIND_PUSH, 0, count);
	}

	/** The Shadow Step (without Combat Roll): a crouch tapped while moving on the ground. */
	private static void tickDodge(Minecraft minecraft, LocalPlayer player) {
		boolean sneak = minecraft.options.keyShift.isDown();
		boolean pressed = sneak && !sneakWasDown;
		sneakWasDown = sneak;
		if (!pressed || ticks < dodgeReadyAt || FabricLoader.getInstance().isModLoaded("combat_roll")) {
			return;
		}
		if (!player.onGround() || attackHeld || inBind(player) || !context(player) || phase() >= DuelIds.PHASE_GATHER && phase() != DuelIds.PHASE_FIGHT) {
			return;
		}
		Vec2 move = player.input.getMoveVector();
		if (move.lengthSquared() < 0.01F) {
			return;
		}
		// The move vector is (strafe, forward) in the player's frame; turn it to the world by the yaw.
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double sin = Mth.sin(yaw);
		double cos = Mth.cos(yaw);
		double strafe = move.x;
		double forward = move.y;
		double wx = strafe * cos - forward * sin;
		double wz = forward * cos + strafe * sin;
		double length = Math.sqrt(wx * wx + wz * wz);
		if (length < 1.0E-4D) {
			return;
		}
		wx /= length;
		wz /= length;
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(wx * DODGE_PUSH, motion.y, wz * DODGE_PUSH);
		dodgeReadyAt = ticks + ClashTiming.DODGE_COOLDOWN;
		int octant = Math.floorMod(Math.round((float) (Math.toDegrees(Math.atan2(wz, wx)) / 45.0D)), 8);
		send(player, DuelIds.IN_DODGE, 0, octant);
	}

	/** Deflect + crouch held for 2 s in FIGHT: surrender (the HUD shows the ring filling). */
	private static void tickSurrender(Minecraft minecraft, LocalPlayer player) {
		boolean holding = phase() == DuelIds.PHASE_FIGHT && minecraft.options.keyShift.isDown()
				&& (DuelKeys.parryDown() || useOwned && minecraft.options.keyUse.isDown());
		if (!holding) {
			surrenderTicks = 0;
			surrenderSent = false;
			return;
		}
		surrenderTicks++;
		if (surrenderTicks >= SURRENDER_TICKS && !surrenderSent) {
			surrenderSent = true;
			send(player, DuelIds.IN_SURRENDER_HOLD, 0, 0);
		}
	}

	// ================================================================== Better Combat

	/** A Better Combat swing (IKUTI_BC): reported as {@code BC_SWING} while the player has duel context. */
	private static void bcSwing(@Nullable SwingDir dir, int windup, boolean heavy, boolean offhand, String animation) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || resolvedMode() != DuelIds.INPUT_IKUTI_BC || !context(player)) {
			return;
		}
		WeaponClass weapon = WeaponClass.of(offhand ? player.getOffhandItem() : player.getMainHandItem());
		int base = weapon.lightWindup + (heavy ? 2 : 0);
		int clamped = Mth.clamp(windup, Math.max(3, base - 2), base + 2);
		SwingDir chosen = dir != null ? dir : SwingDir.combo(comboIndex++);
		int flags = (offhand ? DuelIds.INF_BC_OFFHAND : 0) | (heavy ? DuelIds.INF_BC_HEAVY : 0);
		send(player, DuelIds.IN_BC_SWING, chosen.ordinal(), flags, clamped);
	}

	// ================================================================== own swings

	/** The server's echo of one of the player's swings: the oldest prediction is settled. */
	static void serverSwing(DuelPayloads.DuelSwing p) {
		ownSwing = p;
		if (!PREDICTED.isEmpty() && p.windup() != DuelIds.WINDUP_CHARGING) {
			PREDICTED.pollFirst();
		}
	}

	/** Starts the pose and trail of a swing at once (35.8.2); the server's {@code DuelSwing} corrects it. */
	private static void predictOwn(LocalPlayer player, SwingKind kind, SwingDir dir, boolean charging, boolean heavy) {
		if (resolvedMode() != DuelIds.INPUT_KLASIK) {
			return;
		}
		WeaponClass weapon = WeaponClass.of(player);
		DuelPayloads.DuelFighterSync self = DuelClient.fighter(player.getId());
		boolean riposte = self != null && (self.flags() & DuelIds.FF_CELAH) != 0 && !heavy;
		SwingKind shown = riposte ? SwingKind.BALIK : kind;
		int windup;
		if (charging) {
			windup = DuelIds.WINDUP_CHARGING;
		} else if (riposte) {
			windup = 3;
		} else if (heavy) {
			windup = weapon.heavyRelease;
		} else {
			windup = Math.max(4, weapon.lightWindup - realmSpeedUp());
		}
		int id = -1 - (predictedCounter++ & 0x3FFF);
		PREDICTED.addLast(id);
		while (PREDICTED.size() > 4) {
			PREDICTED.pollFirst();
		}
		int flags = heavy ? DuelIds.SW_HEAVY : 0;
		if (riposte) {
			flags |= DuelIds.SW_RIPOSTE;
		}
		DuelPayloads.DuelSwing predicted = new DuelPayloads.DuelSwing(player.getId(), id, shown.ordinal(), dir.ordinal(), weapon.ordinal(), windup,
				(int) DuelClock.serverTick(), flags, 0);
		ownSwing = predicted;
		DuelBus.swing(predicted, true);
	}

	/** Light windup −1 from Nascent Soul, −2 from Immortal Ascension (35.3.1). */
	private static int realmSpeedUp() {
		if (!ClientCultivation.received()) {
			return 0;
		}
		Realm realm = ClientCultivation.data().realm();
		return realm.atLeast(Realm.IMMORTAL_ASCENSION) ? 2 : realm.atLeast(Realm.NASCENT_SOUL) ? 1 : 0;
	}

	// ================================================================== direction

	/** Records how far the view turned this tick (the mouse after sensitivity). */
	private static void sampleMouse(LocalPlayer player) {
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		if (Float.isNaN(lastYaw)) {
			lastYaw = yaw;
			lastPitch = pitch;
		}
		mouseIndex = (mouseIndex + 1) % MOUSE_TICKS;
		YAW[mouseIndex] = Mth.wrapDegrees(yaw - lastYaw);
		PITCH[mouseIndex] = pitch - lastPitch;
		lastYaw = yaw;
		lastPitch = pitch;
	}

	/** Screen pixels moved in the last 150 ms: x to the right, y downwards. */
	private static double[] mouseDelta() {
		double yaw = 0.0D;
		double pitch = 0.0D;
		for (int i = 0; i < MOUSE_TICKS; i++) {
			yaw += YAW[i];
			pitch += PITCH[i];
		}
		return new double[]{yaw / DEGREES_PER_PIXEL, pitch / DEGREES_PER_PIXEL};
	}

	/** The direction of a light cut: crouch = thrust, the mouse, the default combo; three alike → spin for heavy blades. */
	private static SwingDir lightDir(LocalPlayer player) {
		if (player.isShiftKeyDown()) {
			return SwingDir.TUSUK;
		}
		if (ticks - lastLightAt > 20L) {
			comboIndex = 0;
			sameDirCount = 0;
			lastLightDir = null;
		}
		double[] d = mouseDelta();
		SwingDir dir = SwingDir.fromMouse(d[0], d[1], SwingDir.combo(comboIndex));
		comboIndex = (comboIndex + 1) % ClashTiming.COMBO_MAX;
		sameDirCount = dir == lastLightDir ? sameDirCount + 1 : 1;
		lastLightDir = dir;
		lastLightAt = ticks;
		WeaponClass weapon = WeaponClass.of(player);
		if (sameDirCount >= 3 && (weapon == WeaponClass.PEDANG_BERAT || weapon == WeaponClass.KAPAK)) {
			sameDirCount = 0;
			return SwingDir.PUTAR;
		}
		return dir;
	}

	/** The direction of a heavy cut: crouch = thrust (the Death Thrust), the mouse, else from above. */
	private static SwingDir heavyDir(LocalPlayer player) {
		if (player.isShiftKeyDown()) {
			return SwingDir.TUSUK;
		}
		double[] d = mouseDelta();
		return SwingDir.fromMouse(d[0], d[1], SwingDir.ATAS);
	}

	// ================================================================== sending

	private static void send(LocalPlayer player, int kind, int dir, int arg) {
		send(player, kind, dir, 0, arg);
	}

	private static void send(LocalPlayer player, int kind, int dir, int extraFlags, int arg) {
		// Pushes, guard releases and surrender are never dropped by the rate limit (they are rare or already capped).
		boolean must = kind == DuelIds.IN_GUARD_OFF || kind == DuelIds.IN_CHARGE_RELEASE || kind == DuelIds.IN_SURRENDER_HOLD;
		if (tokens < 1.0D && !must) {
			return;
		}
		tokens = Math.max(0.0D, tokens - 1.0D);
		int flags = extraFlags;
		if (player.isSprinting()) {
			flags |= DuelIds.INF_RUN;
		}
		if (player.input.keyPresses.forward()) {
			flags |= DuelIds.INF_FORWARD;
		}
		if (!player.onGround()) {
			flags |= DuelIds.INF_AIR;
		}
		if (player.isShiftKeyDown()) {
			flags |= DuelIds.INF_CROUCH;
		}
		seq = (seq + 1) & 0xFFFF;
		int clientTick = (int) (DuelClock.serverTick() & 0xFFFFL);
		DuelClient.send(new DuelPayloads.DuelInput(kind, dir & 0xFF, flags & 0xFF, seq, clientTick, Mth.clamp(arg, 0, 255)));
	}

	// ================================================================== HUD queries

	/** 0..1 while deflect + crouch is held toward a surrender. */
	public static float surrenderProgress() {
		return Math.min(1.0F, surrenderTicks / (float) SURRENDER_TICKS);
	}

	/** 0..1 of a heavy charge being held (KLASIK), 0 when none. */
	public static float chargeProgress() {
		if (!attackHeld || !chargeSent) {
			return 0.0F;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		WeaponClass weapon = player == null ? WeaponClass.PEDANG : WeaponClass.of(player);
		return Math.min(1.0F, (attackTicks - CHARGE_AFTER) / (float) Math.max(1, weapon.heavyFull));
	}

	/** Ticks since the last deflect press. */
	public static long sinceParry() {
		return ticks - lastParryAt;
	}

	/** The outcome the last deflect press predicted ({@code DuelIds.OUT_*}), or -1. */
	public static int lastPrediction() {
		return lastPrediction;
	}

	private DuelInput() {
	}
}
