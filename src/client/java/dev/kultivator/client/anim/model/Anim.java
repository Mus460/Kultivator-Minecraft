package dev.kultivator.client.anim.model;

/**
 * The animations of a 3D technique prop (polish plan 3.2), played by {@link ModelFx} with Display interpolation re-keyed
 * every tick. Parameters per kind ({@link AnimStep#a()}, {@link AnimStep#b()}, {@link AnimStep#to()}):
 * <ul>
 * <li>SPIN: a = degrees per tick, b = axis (0 Y, 1 X, 2 Z)</li>
 * <li>BOB: a = amplitude (blocks), b = period (ticks)</li>
 * <li>GROW: a = the starting fraction of the scale (easeOutCubic, no overshoot; held at a before it starts); SHRINK: to nothing (easeInCubic)</li>
 * <li>FALL: a = height (easeInQuad drop; add a STAMP for the landing squash); RISE: a = depth it rises from (easeOutCubic)</li>
 * <li>SLIDE: travel to {@code to} (b = 1: easeOutCubic, facing the travel; b = 2: easeInQuad, keeping the prop's own turn
 * (a falling sun); else linear, facing the travel)</li>
 * <li>THRUST: a = distance along the facing (out with easeOutBack, a 3 tick hold, then it fades)</li>
 * <li>SWING: a = degrees about the side axis; b = period (repeating) or 0 (one sweep)</li>
 * <li>TOPPLE: falls over by 90 degrees about the side axis (easeInQuad)</li>
 * <li>OPEN / CLOSE: the width grows from 0.1 (held closed before it starts) / shrinks to 0.1 (parasols, fans, bags, cymbals)</li>
 * <li>SHATTER: swells by 30% over 2 ticks and bursts into shards of its own texture</li>
 * <li>PULSE: one swell up and back over the duration (a, at most 0.1); never stacked; a PULSE longer than 20 ticks is ignored (idle props stay steady)</li>
 * <li>FLAP: a = degrees, b = period (wings: roll about the body axis)</li>
 * <li>ORBIT: a = radius, b = degrees per second (about the position or the followed entity)</li>
 * <li>LUNGE: 4 ticks forward 2 blocks towards {@code to}, then back</li>
 * <li>STAMP: a 2 tick squash (landing)</li>
 * <li>DISSOLVE: shrinks a little while bleeding particles, then ends</li>
 * <li>TILT: a = degrees about the prop's own side axis (smoothstep over the duration, held after; several add up): a
 * petal unfolding or folding back</li>
 * <li>REVOLVE: a = degrees per tick about the world vertical through the position (eased in over 2 s), b = radius: the
 * prop stands that far out along its front (b &ge; 0) or along its side, flying forward (b &lt; 0); its turn follows</li>
 * <li>FLATTEN: a = the final height factor, b = the final width factor (easeOutCubic): a sun squashing into a disc</li>
 * </ul>
 */
public enum Anim { NONE, SPIN, BOB, GROW, SHRINK, FALL, RISE, SLIDE, THRUST, SWING, TOPPLE, OPEN, CLOSE, SHATTER, PULSE, FLAP, ORBIT, LUNGE, STAMP, DISSOLVE,
	TILT, REVOLVE, FLATTEN }
