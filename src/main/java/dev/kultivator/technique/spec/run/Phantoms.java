package dev.kultivator.technique.spec.run;

import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Payload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SUMMON (DESIGN 22.3.3): virtual phantoms, never entities. At most 8 per cast, 12 per caster and 48 on the server; each
 * attacks at least 10 ticks apart with one entity query (its {@code attack} step, anchored at the phantom: anchor
 * SUMMON). Modes: FOLLOW (around the caster in the summon's pattern), ANCHOR (stays where it was summoned) and CLING
 * (rides a hostile until it dies, then picks the next). Phantoms cannot be hurt; every client draws them from a
 * {@code Payloads.PhantomSync} (on spawn, every 10 ticks, empty at the end; DESIGN 27.4) and lunges them on the PHANTOM
 * event of each attack.
 */
public final class Phantoms {

	static final class Phantom {
		final Caster source;
		final Payload.Summon summon;
		final int index;
		final int count;
		Vec3 pos;
		final Vec3 anchor;
		@Nullable LivingEntity cling;
		final long until;
		long nextAttack;
		final double range;
		final int interval;
		final PhantomKind kind;
		float yaw;

		Phantom(Caster source, Payload.Summon summon, int index, int count, Vec3 pos, long until, double range, int interval) {
			this.kind = kindOf(source, summon);
			this.source = source;
			this.summon = summon;
			this.index = index;
			this.count = count;
			this.pos = pos;
			this.anchor = pos;
			this.until = until;
			this.range = range;
			this.interval = Math.max(10, interval);
			this.nextAttack = source.caster().level().getGameTime() + 10L + index * 3L;
		}
	}

	private static final List<Phantom> PHANTOMS = new ArrayList<>();
	/** Ticks a slow phantom's warning comes before its attack. */
	private static final int PRE_ATTACK = 10;
	/** Casts whose phantoms the clients currently know (an empty sync goes out once they are all gone). */
	private static final java.util.Set<Caster> SYNCED = new java.util.HashSet<>();

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PHANTOMS.clear();
			SYNCED.clear();
		});
	}

	/** Summons up to {@code count} phantoms within the caps; returns how many came. */
	static int summon(Caster source, Payload.Summon summon, int count, int ticks, double range, Vec3 at) {
		LivingEntity owner = source.caster();
		int ofCaster = 0;
		int ofCast = 0;
		for (Phantom phantom : PHANTOMS) {
			if (phantom.source.caster() == owner) {
				ofCaster++;
			}
			if (phantom.source == source) {
				ofCast++;
			}
		}
		int room = Math.min(TechniquePower.PHANTOMS_PER_CAST - ofCast, Math.min(TechniquePower.PHANTOMS_PER_CASTER - ofCaster,
				TechniquePower.PHANTOMS_SERVER - PHANTOMS.size()));
		int made = Math.max(0, Math.min(count, room));
		long until = owner.level().getGameTime() + Math.max(1, ticks);
		for (int i = 0; i < made; i++) {
			Vec3 pos = summon.mode() == Payload.SummonMode.ANCHOR ? at : slot(owner.position(), summon.pattern(), i, made, 0L);
			PHANTOMS.add(new Phantom(source, summon, i, made, pos, until, range, summon.interval()));
		}
		return made;
	}

	static void tick() {
		if (PHANTOMS.isEmpty()) {
			if (!SYNCED.isEmpty()) {
				for (Caster gone : List.copyOf(SYNCED)) {
					sync(gone);
				}
			}
			return;
		}
		for (Phantom phantom : List.copyOf(PHANTOMS)) {
			Caster source = phantom.source;
			if (!source.casterValid() || source.aborted()) {
				PHANTOMS.remove(phantom);
				source.phantomGone();
				continue;
			}
			LivingEntity owner = source.caster();
			long now = owner.level().getGameTime();
			if (now >= phantom.until) {
				PHANTOMS.remove(phantom);
				source.phantomGone();
				continue;
			}
			Vec3 before = phantom.pos;
			switch (phantom.summon.mode()) {
				case FOLLOW -> phantom.pos = slot(owner.position(), phantom.summon.pattern(), phantom.index, phantom.count, now);
				case CLING -> {
					if (phantom.cling != null && (!phantom.cling.isAlive() || phantom.cling.isRemoved())) {
						phantom.cling = null;
					}
					if (phantom.cling == null && now >= phantom.nextAttack) {
						// a new host is searched only when the phantom would attack (≥ 10 ticks apart, 22.15.1), never a player
						for (LivingEntity candidate : source.hostiles(owner.position(), phantom.range, false)) {
							if (!(candidate instanceof net.minecraft.world.entity.player.Player)) {
								phantom.cling = candidate;
								break;
							}
						}
					}
					phantom.pos = phantom.cling == null ? slot(owner.position(), phantom.summon.pattern(), phantom.index, phantom.count, now)
							: phantom.cling.getBoundingBox().getCenter();
				}
				default -> {
				}
			}
			Vec3 moved = phantom.pos.subtract(before);
			if (moved.horizontalDistanceSqr() > 1.0E-4D) {
				phantom.yaw = (float) (Math.atan2(moved.z, moved.x) * (180.0D / Math.PI)) - 90.0F;
			} else if (phantom.summon.mode() == Payload.SummonMode.FOLLOW) {
				phantom.yaw = owner.getYRot();
			}
			if (phantom.interval >= 30 && now == phantom.nextAttack - PRE_ATTACK) {
				// a slow, heavy phantom (the cosmic avatar's stomp) warns 10 ticks ahead where it will strike
				for (LivingEntity candidate : source.hostiles(phantom.pos, phantom.range, false)) {
					if (!(candidate instanceof net.minecraft.world.entity.player.Player)) {
						source.fx(dev.kultivator.technique.spec.dsl.Phase.PHANTOM, phantom.pos, candidate.position(), 1.0F, 0.0F, 1, PRE_ATTACK,
								candidate.getId(), phantom.index, phantom.count, phantom.kind.ordinal(), dev.kultivator.network.Payloads.TechFx.FLAG_PRE_IMPACT);
						break;
					}
				}
			}
			if (now >= phantom.nextAttack) {
				phantom.nextAttack = now + phantom.interval;
				try {
					source.phantomAttack(phantom);
				} catch (RuntimeException exception) {
					dev.kultivator.Kultivator.LOGGER.error("Kultivator: phantom of {} failed and was ended", source.spec().id(), exception);
					PHANTOMS.remove(phantom);
					source.phantomGone();
				}
			}
		}
		long now = PHANTOMS.isEmpty() ? 0L : PHANTOMS.get(0).source.level.getGameTime();
		java.util.Set<Caster> alive = new java.util.HashSet<>();
		for (Phantom phantom : PHANTOMS) {
			alive.add(phantom.source);
		}
		for (Caster cast : alive) {
			if (now % 10L == (cast.caster.getId() & 7)) {
				sync(cast);
			}
		}
		for (Caster gone : List.copyOf(SYNCED)) {
			if (!alive.contains(gone)) {
				sync(gone);
			}
		}
	}

	/**
	 * Sends the phantoms of {@code cast} to the players around its caster (an empty list once none is left, then the cast
	 * is forgotten).
	 */
	static void sync(Caster cast) {
		List<dev.kultivator.network.Payloads.PhantomSync.Entry> entries = new ArrayList<>();
		long now = cast.level.getGameTime();
		for (Phantom phantom : PHANTOMS) {
			if (phantom.source != cast || entries.size() >= dev.kultivator.network.Payloads.PhantomSync.MAX) {
				continue;
			}
			byte mode = switch (phantom.summon.mode()) {
				case ANCHOR -> PhantomKind.MODE_ANCHOR;
				case CLING -> PhantomKind.MODE_CLING;
				default -> PhantomKind.MODE_FOLLOW;
			};
			entries.add(new dev.kultivator.network.Payloads.PhantomSync.Entry((byte) phantom.index, (byte) phantom.kind.ordinal(),
					(float) phantom.pos.x, (float) phantom.pos.y, (float) phantom.pos.z, phantom.yaw,
					phantom.cling != null ? phantom.cling.getId() : -1, mode, (short) Math.max(0, Math.min(Short.MAX_VALUE, phantom.until - now))));
		}
		if (entries.isEmpty()) {
			if (!SYNCED.remove(cast)) {
				return;
			}
		} else {
			SYNCED.add(cast);
		}
		VisualSync.around(cast.level, cast.caster.position(),
				new dev.kultivator.network.Payloads.PhantomSync(cast.caster.getId(), cast.spec.netId(), entries));
	}

	/** The look of a summon: its kind id, with the clone summons told apart by their art (blood clones, hair clones). */
	static PhantomKind kindOf(Caster source, Payload.Summon summon) {
		if (Payload.Summon.CLONE.equals(summon.kind())) {
			String id = source.spec.id();
			if (id.contains("blood") || source.spec.dao() == dev.kultivator.data.Dao.LIFE) {
				return PhantomKind.BLOOD_CLONE;
			}
			if (id.contains("body_beyond") || id.contains("hair")) {
				return PhantomKind.HAIR_CLONE;
			}
			return PhantomKind.GENERIC_CLONE;
		}
		return switch (summon.kind()) {
			case "ice_emperor" -> PhantomKind.ICE_SCORPION;
			case "snow_emperor" -> PhantomKind.SNOW_EMPRESS;
			default -> PhantomKind.ofSummon(summon.kind());
		};
	}

	/** The kind of a living phantom (the PHANTOM event's {@code aux}). */
	static PhantomKind kind(Phantom phantom) {
		return phantom.kind;
	}

	/** A follow slot around {@code centre}: a ring, a pentagon, a line behind, or a slow orbit. */
	private static Vec3 slot(Vec3 centre, Payload.Pattern pattern, int index, int count, long now) {
		int n = Math.max(1, count);
		return switch (pattern) {
			case LINE -> centre.add((index - (n - 1) / 2.0D) * 1.5D, 1.2D, -1.5D);
			case PENTAGON -> {
				double angle = Math.PI * 2.0D * index / 5.0D;
				yield centre.add(Math.cos(angle) * 2.5D, 1.0D, Math.sin(angle) * 2.5D);
			}
			case RING -> {
				double angle = Math.PI * 2.0D * index / n;
				yield centre.add(Math.cos(angle) * 2.0D, 1.0D, Math.sin(angle) * 2.0D);
			}
			default -> {
				double angle = Math.PI * 2.0D * index / n + now * 0.05D;
				yield centre.add(Math.cos(angle) * 1.8D, 1.4D, Math.sin(angle) * 1.8D);
			}
		};
	}

	/** Ends every phantom of a cast. */
	static void endFrom(Caster source) {
		PHANTOMS.removeIf(phantom -> phantom.source == source);
		if (SYNCED.contains(source)) {
			sync(source);
		}
	}

	/** Living phantoms of one cast. */
	static int of(Caster source) {
		int count = 0;
		for (Phantom phantom : PHANTOMS) {
			if (phantom.source == source) {
				count++;
			}
		}
		return count;
	}

	static int count() {
		return PHANTOMS.size();
	}

	private Phantoms() {
	}
}
