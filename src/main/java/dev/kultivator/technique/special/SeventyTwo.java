package dev.kultivator.technique.special;

import dev.kultivator.Kultivator;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.technique.spec.dsl.Hook;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Shape;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.run.CastContext;
import dev.kultivator.technique.spec.run.EndReason;
import dev.kultivator.technique.spec.run.SpecialTechnique;
import dev.kultivator.util.LoadedPath;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static dev.kultivator.technique.spec.dsl.Dsl.*;

/**
 * {@code seventy_two}, the special of {@code seventy_two_transformations} (DESIGN 22.2.7, 22.9.9; Journey to the West
 * ch. 2, 6: the seventy-two earthly transformations, last as a temple against Erlang Shen). The first cast takes the
 * tiger form; every recast within {@link #WINDOW} ticks of the last change cycles to the next of the five forms (ST 5).
 * A form lasts {@link #FORM_TICKS_BASE}→{@link #FORM_TICKS_CAP} ticks (D-scaled) through the runner's TRANSFORM (one form
 * at a time, head-room and IN_WALL checks) and drains lin(3, −0.3/tier, ≥ 1) qi per second here.
 * <ul>
 * <li><b>Harimau</b>: scale 1.1, Speed II, Strength I, melee hits add 0.6 × P;</li>
 * <li><b>Elang</b>: scale 0.5, Slow Falling; a recast while airborne and looking down is a dive: DASH down, then
 * STRIKE(r3) of 2.5 × P (and the DISC r3 d1 carving, hawk dive only);</li>
 * <li><b>Ikan</b>: Water Breathing and Speed II;</li>
 * <li><b>Serangga</b>: scale 0.25; monsters within 16 lose track of the caster at the change; a safe teleport when the
 * caster would be stuck after growing back;</li>
 * <li><b>Kuil</b>: own movement −100% (a transient modifier, never saved), Resistance III, Regeneration II, and 0.4 × P to
 * melee attackers.</li>
 * </ul>
 * The 10 s cooldown starts when the form ends ({@link #blocker}). Mastery level 3 halves the drain, level 4 removes it
 * (both off in an arena). Contract with the runner: each change is a new cast; the spec's timeline holds only an empty
 * keep-alive step at {@link #KEEP_ALIVE_TICKS}, so the cast (and its TRANSFORM) lives while the form does; this handler
 * ends the previous cast before a change and ends the cast when the form runs out or the qi does.
 */
public final class SeventyTwo implements SpecialTechnique {

	public static final String ID = "seventy_two";

	/** The five forms in cast order (ST 1-5). */
	public enum Form {
		HARIMAU("harimau", 0xFF9A2A), ELANG("elang", 0xE8D8B0), IKAN("ikan", 0x3FA9FF), SERANGGA("serangga", 0x9AE65A),
		KUIL("kuil", 0xC8B48A);

		private final String id;
		private final int color;

		Form(String id, int color) {
			this.id = id;
			this.color = color;
		}

		public String id() {
			return id;
		}

		public int color() {
			return color;
		}
	}

	public static final int WINDOW = 100;
	public static final double FORM_TICKS_BASE = 1200.0D;
	public static final double FORM_TICKS_CAP = 1800.0D;
	public static final double DRAIN_BASE = 3.0D;
	public static final double DRAIN_PER_TIER = -0.3D;
	public static final double DRAIN_MIN = 1.0D;
	/** "cd 10s after the form ends". */
	public static final int COOLDOWN_AFTER = 200;
	/** A recast as a hawk while looking at least this far down (look y) dives. */
	public static final double DIVE_LOOK = -0.6D;
	public static final double DIVE_DASH = 16.0D;
	public static final int DIVE_STRIKE_DELAY = 5;
	public static final double DIVE_COEF = 2.5D;
	public static final double TIGER_COEF = 0.6D;
	public static final double TEMPLE_COEF = 0.4D;
	public static final double INSECT_UNTARGET = 16.0D;
	/** Ticks after a change or revert before the stuck check (the scale settles first). */
	public static final int SAFE_CHECK_DELAY = 2;
	/** Longer than the longest form, so the cast lives as long as its form. */
	public static final int KEEP_ALIVE_TICKS = 2400;
	public static final Identifier STILL_ID = Kultivator.id("seventy_two_still");

	private static final String KEY = "kultivator.technique.seventy_two_transformations.";

	private static final Map<Form, Payload.Transform> TRANSFORMS = new HashMap<>();
	/** The open form of each caster (session only). */
	private static final Map<UUID, State> STATES = new HashMap<>();
	/** Game time each caster's last form ended (the 10 s cooldown). */
	private static final Map<UUID, Long> ENDED = new HashMap<>();
	/** Casters to check for being stuck after a scale change, by game time. */
	private static final Map<UUID, Long> SAFE_CHECKS = new HashMap<>();

	static {
		Payload.Hit tigerHit = damage(TIGER_COEF);
		TRANSFORMS.put(Form.HARIMAU, transform("seventy_two_harimau", (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP).scale(1.1D).with(
				buff(Payload.BuffKind.SPEED, 2, (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP),
				buff(Payload.BuffKind.STRENGTH, 1, (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP),
				hook(Hook.onMelee(at(0).shape(target().pick(Shape.Target.Pick.HIT)).hit(tigerHit)).icd(10))));
		TRANSFORMS.put(Form.ELANG, transform("seventy_two_elang", (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP).scale(0.5D).with(
				buff(Payload.BuffKind.SLOW_FALLING, 1, (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP)));
		TRANSFORMS.put(Form.IKAN, transform("seventy_two_ikan", (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP).with(
				buff(Payload.BuffKind.WATER_BREATHING, 1, (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP),
				buff(Payload.BuffKind.SPEED, 2, (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP)));
		TRANSFORMS.put(Form.SERANGGA, transform("seventy_two_serangga", (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP).scale(0.25D));
		TRANSFORMS.put(Form.KUIL, transform("seventy_two_kuil", (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP).with(
				buff(Payload.BuffKind.RESISTANCE, 3, (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP),
				buff(Payload.BuffKind.REGENERATION, 2, (int) FORM_TICKS_BASE, (int) FORM_TICKS_CAP),
				hook(Hook.onHurt(at(0).shape(target().pick(Shape.Target.Pick.HIT)).hit(damage(TEMPLE_COEF))).icd(10))));
		ServerTickEvents.END_SERVER_TICK.register(SeventyTwo::tickAll);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STATES.clear();
			ENDED.clear();
			SAFE_CHECKS.clear();
		});
	}

	private static final class State {
		private Form form = Form.HARIMAU;
		private CastContext ctx;
		private long lastChange;
		private long end;
		private long nextDrain;
		private double drain;
		private boolean free;
	}

	/** The TRANSFORM payload of {@code form} (as the runner applies it). */
	public static Payload.Transform transformOf(Form form) {
		return TRANSFORMS.get(form);
	}

	@Override
	public @Nullable Component blocker(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return Component.translatable(KEY + "why.player");
		}
		UUID id = player.getUUID();
		if (STATES.containsKey(id) || ctx.options().free()) {
			return null;
		}
		Long ended = ENDED.get(id);
		long left = ended == null ? 0L : ended + COOLDOWN_AFTER - ctx.level().getGameTime();
		if (left > 0L) {
			return Component.translatable(KEY + "why.cooldown", String.format(Locale.ROOT, "%.1f", left / 20.0D));
		}
		return null;
	}

	@Override
	public boolean cast(CastContext ctx) {
		ServerPlayer player = ctx.player();
		if (player == null) {
			return false;
		}
		UUID id = player.getUUID();
		long now = ctx.level().getGameTime();
		State state = STATES.get(id);
		if (state != null && state.form == Form.ELANG && !player.onGround() && !player.isInWater() && ctx.look().y < DIVE_LOOK) {
			dive(ctx);
			return true;
		}
		Form next = state != null && now - state.lastChange <= WINDOW ? Form.values()[(state.form.ordinal() + 1) % Form.values().length]
				: Form.HARIMAU;
		if (state != null) {
			// The old form ends first (its cast and TRANSFORM), so the new one never overlaps it.
			close(player, state, false, EndReason.REPLACED);
		}
		open(ctx, player, next, now);
		return true;
	}

	@Override
	public void end(CastContext ctx, EndReason reason) {
		UUID id = ctx.caster().getUUID();
		State state = STATES.get(id);
		if (state != null && state.ctx == ctx && ctx.caster() instanceof ServerPlayer player) {
			close(player, state, true, reason);
		}
	}

	// ================================================================== forms

	private static void open(CastContext ctx, ServerPlayer player, Form form, long now) {
		int ticks = ctx.duration(FORM_TICKS_BASE, FORM_TICKS_CAP);
		ctx.applySelf(transformOf(form));
		State state = new State();
		state.form = form;
		state.ctx = ctx;
		state.lastChange = now;
		state.end = now + Math.max(20, ticks);
		state.nextDrain = now + 20L;
		state.drain = drain(ctx);
		state.free = ctx.options().free();
		STATES.put(player.getUUID(), state);
		if (form == Form.SERANGGA) {
			ctx.run(at(0).shape(nova().radius(INSECT_UNTARGET)).hit(untarget()));
		}
		if (form == Form.KUIL) {
			still(player, true);
		}
		ctx.fx(Phase.extra(form.ordinal()), player.position(), player.position(), 1.5F, 0.0F, form.color(), 10);
		ctx.sound(Phase.extra(form.ordinal()), player.position());
		Msg.actionBar(player, Component.translatable(KEY + "form", Component.translatable(KEY + "form." + form.id())).withColor(form.color()));
	}

	/**
	 * Ends a form: forgets it, lifts the temple's stillness, starts the 10 s cooldown and ends its cast (reverting the
	 * TRANSFORM) with {@code reason} unless {@code fromEnd} (the runner already ends it). A revert from a small form gets a
	 * stuck check.
	 */
	private static void close(ServerPlayer player, State state, boolean fromEnd, EndReason reason) {
		UUID id = player.getUUID();
		if (STATES.get(id) == state) {
			STATES.remove(id);
		}
		still(player, false);
		long now = player.level().getGameTime();
		ENDED.put(id, now);
		if (state.form != Form.HARIMAU && state.form != Form.IKAN) {
			SAFE_CHECKS.put(id, now + SAFE_CHECK_DELAY);
		}
		if (!fromEnd && !state.ctx.ended()) {
			state.ctx.applySelf(revert());
			state.ctx.end(reason);
		}
	}

	/** Qi per second of a form: lin(3, −0.3/tier, ≥ 1); mastery 3 halves it, mastery 4 removes it (not in an arena). */
	private static double drain(CastContext ctx) {
		double perSecond = Math.max(DRAIN_MIN, ctx.lin(DRAIN_BASE, DRAIN_PER_TIER, DRAIN_MIN));
		if (!ctx.arena()) {
			if (ctx.mastery() >= 4) {
				return 0.0D;
			}
			if (ctx.mastery() >= 3) {
				return perSecond * 0.5D;
			}
		}
		return perSecond;
	}

	/** The temple's stillness: own movement −100% as a transient modifier (never saved). */
	private static void still(ServerPlayer player, boolean on) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (on) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(STILL_ID, -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			speed.removeModifier(STILL_ID);
		}
	}

	// ================================================================== the hawk dive

	/** A separate short cast: DASH down along the look, then STRIKE(r3) and the hawk-dive carving; it ends itself. */
	private static void dive(CastContext ctx) {
		ctx.applySelf(dash(DIVE_DASH).iframes(6).noFall(60));
		ctx.schedule(DIVE_STRIKE_DELAY, () -> {
			if (ctx.ended()) {
				return;
			}
			Step blow = at(0).shape(strike().range(3).arc(220)).hit(damage(DIVE_COEF)).phase(5);
			ctx.run(blow);
			ctx.destroy(ctx.caster().position(), new Vec3(0.0D, -1.0D, 0.0D));
			ctx.end(EndReason.DONE);
		});
	}

	// ================================================================== server tick

	private static void tickAll(MinecraftServer server) {
		if (STATES.isEmpty() && SAFE_CHECKS.isEmpty()) {
			return;
		}
		List<Object[]> closing = new ArrayList<>();
		for (Map.Entry<UUID, State> entry : List.copyOf(STATES.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			State state = entry.getValue();
			if (player == null || player.isRemoved() || !player.isAlive()) {
				STATES.remove(entry.getKey());
				continue;
			}
			long now = player.level().getGameTime();
			if (state.ctx.ended() || now >= state.end) {
				closing.add(new Object[]{player, state, state.ctx.ended(), EndReason.DONE});
				continue;
			}
			if (state.drain > 0.0D && now >= state.nextDrain) {
				state.nextDrain = now + 20L;
				if (!state.free && !Qi.trySpend(player, state.drain)) {
					Msg.actionBar(player, Component.translatable(KEY + "no_qi"));
					closing.add(new Object[]{player, state, false, EndReason.CANCELLED});
				}
			}
		}
		for (Object[] item : closing) {
			close((ServerPlayer) item[0], (State) item[1], (Boolean) item[2], (EndReason) item[3]);
		}
		if (!SAFE_CHECKS.isEmpty()) {
			for (Map.Entry<UUID, Long> entry : List.copyOf(SAFE_CHECKS.entrySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null || player.isRemoved() || !player.isAlive()) {
					SAFE_CHECKS.remove(entry.getKey());
					continue;
				}
				if (player.level().getGameTime() >= entry.getValue()) {
					SAFE_CHECKS.remove(entry.getKey());
					unstick(player);
				}
			}
		}
	}

	/** After growing back: a safe teleport when the caster's box is inside blocks (loaded chunks only). */
	private static void unstick(ServerPlayer player) {
		ServerLevel level = player.level();
		if (level.noCollision(player)) {
			return;
		}
		Vec3 here = player.position();
		Vec3 spot = Targeting.safeSpot(level, player, here);
		if (spot == null || !level.isLoaded(BlockPos.containing(spot)) || !LoadedPath.loaded(level, here, spot)) {
			return;
		}
		player.teleportTo(level, spot.x, spot.y, spot.z, Relative.ROTATION, 0.0F, 0.0F, false);
		player.resetFallDistance();
	}
}
