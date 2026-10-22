package dev.kultivator.duel.clash;

import dev.kultivator.duel.DuelIds;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One swing on the server (DESIGN 35.3.4): who, against whom, what, when. {@link #impact} = {@link #start} +
 * {@link #windup}; the result is decided at {@link #resolveAt} (impact + the defender's compensation + 1) by
 * {@link Exchange#resolve}. A heavy charge is a swing with {@link #charging} set and no impact yet. Session only.
 */
public final class Swing {

	/** Per fighter, 0..32767, sent in {@code DuelSwing.swingId}. */
	public final int id;
	public final Fighter owner;
	/** The swing's intended target (the duel partner, or the nearest foe in front); re-checked at resolution. */
	public @Nullable LivingEntity target;
	public SwingKind kind;
	public SwingDir dir;
	public WeaponClass weapon;
	public int windup;
	public long start;
	public long impact;
	public long resolveAt;
	/** {@code DuelIds.SW_*}. */
	public int flags;
	public int qiRgb;
	/** Posture multiplier on top of the kind (Dao, Tebasan Langit, HANDICAP...). */
	public float postureMul = 1.0F;
	/** True while a heavy charge is held (no impact yet). */
	public boolean charging;
	/** The charge began at this tick (heavy swings). */
	public long chargeStart;
	/** The charge reached full at this tick (−1 not yet). */
	public long chargeFull = -1L;
	public boolean resolved;
	public boolean cancelled;
	/** The outcome once resolved ({@code DuelIds.OUT_*}), −1 before; a BC swing that was not stopped stays −1 (BC hits). */
	public int outcome = -1;
	public long resolvedAt;
	/** The {@code DuelInput.seq} that started it (−1 for AI and BC). */
	public int seq = -1;

	public Swing(int id, Fighter owner) {
		this.id = id;
		this.owner = owner;
	}

	public boolean heavy() {
		return (flags & DuelIds.SW_HEAVY) != 0;
	}

	public boolean qi() {
		return (flags & DuelIds.SW_QI) != 0;
	}

	public boolean bc() {
		return (flags & DuelIds.SW_BC) != 0;
	}

	public boolean riposte() {
		return (flags & DuelIds.SW_RIPOSTE) != 0;
	}

	public int perilous() {
		if ((flags & DuelIds.SW_PERILOUS_GUNUNG) != 0) {
			return DuelIds.PERIL_GUNUNG;
		}
		if ((flags & DuelIds.SW_PERILOUS_MAUT) != 0) {
			return DuelIds.PERIL_MAUT;
		}
		if ((flags & DuelIds.SW_PERILOUS_SAPU) != 0) {
			return DuelIds.PERIL_SAPU;
		}
		return DuelIds.PERIL_NONE;
	}

	/** True while the swing still waits for its impact or its resolution. */
	public boolean live() {
		return !resolved && !cancelled;
	}

	/** The posture L or H of this swing's weapon (H for the heavy family and perilous swings). */
	public float basePosture() {
		return heavy() || kind.heavy() ? weapon.postureHeavy : weapon.postureLight;
	}

	/** 0..255 strength for the visuals. */
	public int power() {
		return switch (kind) {
			case PENENTU -> 255;
			case QI -> 210;
			case BERAT -> 170;
			case BALIK -> 140;
			case TUSUK -> 110;
			case TERBANG -> 70;
			default -> heavy() ? 170 : 90;
		};
	}
}
