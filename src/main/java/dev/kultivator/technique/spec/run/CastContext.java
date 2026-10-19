package dev.kultivator.technique.spec.run;

import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.technique.spec.CastLedger;
import dev.kultivator.technique.spec.DamageContext;
import dev.kultivator.technique.spec.TechniquePower;
import dev.kultivator.technique.spec.dsl.Payload;
import dev.kultivator.technique.spec.dsl.Phase;
import dev.kultivator.technique.spec.dsl.Step;
import dev.kultivator.technique.spec.dsl.Val;
import dev.kultivator.util.PortalIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One running cast (DESIGN 22.2.6 step 3): the snapshot taken when it started and the operations steps and specials use.
 * The spine defines this contract; P6c-mesin's runner implements it ({@code PlayerCaster}, {@code DiscipleCaster}).
 * Every operation keeps the safety rules of 22.3.5: hostiles only through {@code Targeting} /
 * {@code DiscipleTechniques.mayHit}, damage only through the choke points ({@code TechniquePower.finalDamage}), no
 * block is ever changed (destruction only through {@link #destroy} into {@code Kehancuran.submitSelf}), movement only
 * where {@link #moveAllowed} says so, rays and teleports only along loaded paths.
 */
public interface CastContext {

	// ================================================================== identity

	TechniqueSpec spec();

	/** The casting entity: a player or a disciple. */
	LivingEntity caster();

	/** The casting player, or null for a disciple. */
	@Nullable ServerPlayer player();

	/** The casting disciple, or null for a player. */
	@Nullable DiscipleEntity disciple();

	/** The player the cast belongs to: the caster, or the disciple's master (null when offline). */
	@Nullable ServerPlayer master();

	ServerLevel level();

	CastOptions options();

	/** Every client draws the same random shape from it. */
	long seed();

	long startTick();

	/** Ticks since the cast started. */
	int age();

	// ================================================================== snapshot

	/** P of 22.5.1 at the cast (arena casts use the legacy power instead, inside the choke points). */
	double power();

	/** Mastery level 0-4 of this spec (0 for echoes and replays' mastery gain, but their bonuses stay). */
	int mastery();

	/** A/D/N, tier index, tier gap and realm gap (all 1 / 0 in an arena). */
	TechniquePower.Factors factors();

	/** Tier index t of 22.5.3. */
	int tier();

	/** Visual tier 0-5 of 22.4.7. */
	int visualTier();

	/** This cast carves (22.11.2), decided once at the snapshot. */
	boolean kehancuran();

	/** The caster is in a duel or sparring: legacy power, A = D = N = 1, no bonuses (22.14.5). */
	boolean arena();

	/** This run is an ECHO (no destruction, no mastery). */
	boolean echo();

	/** Damage factor of this run: 1, or the echo / replay / extra-cast factor. */
	double factor();

	/** Current stage of a STAGES spec (1-based; 0 without stages). */
	int stage();

	/** Share of a CHARGE that was completed (1 when not charged). */
	double chargeShare();

	/** Where the cast started. */
	Vec3 origin();

	Vec3 eye();

	Vec3 look();

	/** The aim point: a read-only clip along a loaded path. */
	Vec3 aim();

	/** The locked target, or null. */
	@Nullable LivingEntity target();

	/** The end of the last travelling shape, dash or blink (anchor END); the origin before any. */
	Vec3 end();

	/** Sets the END anchor (a special's travel ended here). */
	void markEnd(Vec3 point);

	/** Records the segment a dash or blink travelled (PATH shapes, {@code TRENCH path} carving). */
	void markPath(Vec3 from, Vec3 to);

	/** Records an afterimage point (anchor AFTERIMAGE). */
	void addAfterimage(Vec3 point);

	/** The centre of the cast's own FIELD, DOMAIN or AURA (anchor AREA); the caster's position when it has none. */
	Vec3 areaCentre();

	/** The scaled radius of the cast's own FIELD, DOMAIN or AURA; 0 when it has none. */
	double areaRadius();

	/** Damage this cast dealt per target (boss, heart-demon and arena caps). */
	CastLedger ledger();

	/** The cast's portal cache (never move a mob within 8 blocks of a portal). */
	PortalIndex.Cache portals();

	/** Scratch data for a special (session only, never saved). */
	CompoundTag state();

	// ================================================================== scaled reads (22.5.3, 22.6, 22.15)

	/** An area value: base × A × mastery area, at most cap and the radius hard cap. */
	double area(double base, double cap);

	/** A duration in ticks: base × D × mastery duration, at most cap. */
	int duration(double base, double cap);

	/** A count: base × N, rounded, at most cap. */
	int count(double base, double cap);

	double area(Val value, double hardCap);

	int duration(Val value, double hardCap);

	int count(Val value, double hardCap);

	/** {@code lin(base, perTier, cap)} on the tier index (no A/D/N on top). */
	double lin(double base, double perTier, double cap);

	// ================================================================== targets

	/** May this cast hurt {@code entity} ({@code precise}: calm neutrals allowed, 22.3.5 rule 2)? */
	boolean mayHit(Entity entity, boolean precise);

	/** Hostiles within {@code radius} of {@code centre}, nearest first (one entity query). */
	List<LivingEntity> hostiles(Vec3 centre, double radius, boolean precise);

	/** Allies within {@code radius}: the caster, players, own disciples, pets (never monsters). */
	List<LivingEntity> allies(Vec3 centre, double radius);

	/** May {@code target} be moved: not on the never-moved list and not within 8 blocks of a portal. */
	boolean moveAllowed(LivingEntity target);

	/** A safe standing spot for the caster at or near {@code near} (loaded chunks only), or null. */
	@Nullable Vec3 safeSpot(Vec3 near);

	// ================================================================== effects

	/** A damage context for {@code coef} × P on {@code target} with this cast's snapshot, ledger and factor (not applied). */
	DamageContext damage(LivingEntity target, double coef);

	/** Applies a damage context through the caster's choke point. True when damage landed. */
	boolean hurt(DamageContext context);

	/** {@code DMG coef} on {@code target} through the choke point. */
	boolean hurt(LivingEntity target, double coef, boolean precise);

	/** Applies one hostile payload to {@code target}. */
	void apply(LivingEntity target, Payload.Hit payload);

	/** Applies one payload to the caster. */
	void applySelf(Payload.Boon payload);

	/** Applies one payload to an ally. */
	void applyAlly(LivingEntity ally, Payload.Boon payload);

	/** Runs a timeline step now with this context (its own tick offsets are scheduled from now). */
	void run(Step step);

	/** Sends one {@code TechFx} event of this spec. */
	void fx(Phase phase, Vec3 at, Vec3 to, float radius, float length, int count, int duration);

	/**
	 * A TechFx v2 event (DESIGN 27.4): {@code target} the entity it is about (-1 none), {@code index}/{@code of} its place
	 * in a group (of 1 = single), {@code aux} by flag or phase, {@code flags} the event's own TechFx FLAG_* bits.
	 */
	void fx(Phase phase, Vec3 at, Vec3 to, float radius, float length, int count, int duration, int target, int index, int of, int aux,
			int flags);

	/** Plays the spec's sound cues of {@code phase} at {@code at}. */
	void sound(Phase phase, Vec3 at);

	/**
	 * A two-Dao art (DESIGN 27.4): every later TechFx event of this cast carries {@code FLAG_TWO_DAO} with the second Dao's
	 * RGB in {@code aux}, so the client paints the art in both colours (-1 clears it).
	 */
	void twoDao(int rgb);

	/** Runs {@code action} after {@code delayTicks} unless the cast ended meanwhile. */
	void schedule(int delayTicks, Runnable action);

	/** Submits the spec's Kehancuran shapes at this anchor (no-op unless {@link #kehancuran()}); true when queued. */
	boolean destroy(Vec3 anchor, Vec3 direction);

	/** Ids of the distinct specs the caster cast in the last {@code seconds} seconds, newest first (echo wheel). */
	List<String> recentCasts(int seconds);

	/** Runs another spec's timeline inside this cast at {@code factor} (one boss cap, no mastery, no Kehancuran). */
	boolean replay(TechniqueSpec other, double factor);

	/** Ends the cast (END FX and onEnd steps run for DONE). */
	void end(EndReason reason);

	boolean ended();
}
