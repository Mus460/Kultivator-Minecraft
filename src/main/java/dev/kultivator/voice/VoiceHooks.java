package dev.kultivator.voice;

import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.disciple.DiscipleBreakthrough;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.epic.EpicIds;
import dev.kultivator.musuh.RogueCultivatorEntity;
import dev.kultivator.purba.PrimordialBeastEntity;
import dev.kultivator.technique.spec.dsl.Phase;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Where the world speaks (module V). Zero-edit listeners (breakthroughs, disciples, deaths, damage, dimension changes, the
 * once-a-second scan around each player) plus the one-line hooks other files call. Every hook only reads its arguments,
 * never throws and never changes gameplay; all of them end in {@link Voice}.
 */
public final class VoiceHooks {

	private static final UUID NOBODY = new UUID(0L, 0L);
	private static final double BOSS_SCAN = 64.0D;
	private static final double BOSS_HEARD = 96.0D;
	/** A jump longer than this between two scans (same dimension) counts as a long journey. */
	private static final double JOURNEY = 128.0D;

	private record Seen(ResourceKey<Level> dim, Vec3 pos) {
	}

	private static final Map<UUID, Seen> LAST = new HashMap<>();
	private static final Set<UUID> AWAKENED = new HashSet<>();
	private static final Set<UUID> ENRAGED = new HashSet<>();

	static void init() {
		Breakthrough.addListener(new Breakthrough.Listener() {
			@Override
			public void onSuccess(ServerPlayer p, Rank before, Rank now, BreakthroughQuality q, int kind, boolean test) {
				Voice.guard(() -> breakthrough(p, before, now));
			}

			@Override
			public void onFail(ServerPlayer p, boolean hardcoreDrop, boolean test) {
				Voice.heaven(p, "tribulation_fail");
			}
		});
		DiscipleHooks.addBreakthroughListener(new DiscipleHooks.BreakthroughListener() {
			@Override
			public void onEnd(DiscipleEntity entity, boolean success, DiscipleBreakthrough.Kind kind) {
				if (success) {
					Voice.later(30, () -> VoiceDisciples.line(entity, "breakthrough", 200));
				}
			}
		});
		DiscipleHooks.addOnHurt((entity, source, damage) -> Voice.guard(() -> {
			if (damage >= 1.0F && entity.isAlive() && entity.getRandom().nextFloat() < 0.35F) {
				VoiceDisciples.grunt(entity, "hurt");
			}
		}));
		DiscipleHooks.addOnTaught((entity, master, technique) -> Voice.guard(() -> VoiceDisciples.line(entity, "thanks", 400)));
		DiscipleHooks.addOnFed((entity, master, stack) -> Voice.guard(() -> VoiceDisciples.line(entity, "thanks", 400)));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> Voice.guard(() -> death(entity, source)));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
				Voice.guard(() -> damaged(entity, source, damageTaken)));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> Voice.guard(() -> {
			Seen seen = LAST.get(player.getUUID());
			if (seen != null && seen.dim().equals(origin.dimension())) {
				VoiceDisciples.farewell(player, origin, seen.pos());
			}
			LAST.put(player.getUUID(), new Seen(destination.dimension(), player.position()));
		}));
		ServerTickEvents.END_SERVER_TICK.register(VoiceHooks::tick);
		ServerPlayerEvents.LEAVE.register(player -> LAST.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STARTED.register(Voice::start);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			Voice.clear();
			VoiceDisciples.clear();
			LAST.clear();
			AWAKENED.clear();
			ENRAGED.clear();
		});
	}

	// ================================================================== one-line hooks

	/**
	 * Every epic moment beat ({@code EpicHooks.send}, before its own checks, so voices play with or without the 7b
	 * cinematics): the tribulation, the heart demon, the Pengakuan Langit voice, wrath, secret realms, the ascension, bosses.
	 */
	public static void onMoment(@Nullable ServerLevel level, int moment, int beat, @Nullable Entity subject, @Nullable ServerPlayer viewer,
								@Nullable Vec3 pos, int a, int b, int flags) {
		Voice.guard(() -> moment(level, moment, beat, subject, viewer, pos, a, b));
	}

	/**
	 * A technique event of the server cast path ({@code Caster.send}); {@code root} is false for echoes and sub-casts. The
	 * voice of Heaven proclaims the Titah decrees and witnesses Abadi and Dewa ultimates (once a minute per caster); a
	 * disciple sometimes shouts its art.
	 */
	public static void techCast(@Nullable LivingEntity caster, @Nullable TechniqueSpec spec, @Nullable Phase phase, boolean root) {
		Voice.guard(() -> {
			if (caster == null || spec == null || !root || (phase != Phase.CAST && phase != Phase.CHARGE)
					|| !(caster.level() instanceof ServerLevel level)) {
				return;
			}
			if (caster instanceof ServerPlayer player) {
				Vec3 at = player.position();
				if (spec.titah()) {
					String line = decreeLine(spec.id());
					if (Voice.ready(player.getUUID(), "decree", 200)) {
						Voice.heavenNear(level, at, "decree", 128.0D);
						if (line != null) {
							Voice.heavenNear(level, at, line, 128.0D);
						}
					}
				} else if (spec.pamungkas() && (spec.grade() == TechniqueGrade.ABADI || spec.grade() == TechniqueGrade.DEWA)
						&& Voice.ready(player.getUUID(), "witness", 1200)) {
					Voice.heavenNear(level, at, "witness", 64.0D);
				}
			} else if (caster instanceof DiscipleEntity disciple && phase == Phase.CAST) {
				VoiceDisciples.technique(disciple);
			}
		});
	}

	/** A Dao comprehension level or an enlightenment. */
	public static void daoInsight(@Nullable ServerPlayer player) {
		Voice.heaven(player, "dao_insight");
	}

	/** A sect is founded: Heaven blesses it for its founder and everyone near. */
	public static void sectFounded(@Nullable ServerPlayer founder) {
		if (founder != null) {
			Voice.heavenNear(founder.level(), founder.position(), "sect_founded", 64.0D);
		}
	}

	/** Rogue cultivators spring an ambush: the first of them shouts. */
	public static void rogueAmbush(@Nullable List<? extends Entity> band) {
		if (band != null && !band.isEmpty()) {
			Entity first = band.get(0);
			Voice.later(10, () -> Voice.say(first, "rogue.ambush", null));
		}
	}

	/** A rogue cultivator flees. */
	public static void rogueFlee(@Nullable Entity rogue) {
		if (rogue != null && Voice.ready(NOBODY, "rogue_flee_" + rogue.level().dimension().identifier(), 100)) {
			Voice.sayHere(rogue, "rogue.flee", null);
		}
	}

	/** An inheritance trial starts ({@code won} false) or is won: the old hermit's spirit speaks. */
	public static void inheritance(@Nullable ServerPlayer player, boolean won) {
		Voice.inner(player, won ? "elder.inheritance" : "elder.welcome", (Component) null);
	}

	/** A cave abode is set: the old hermit gives advice. */
	public static void caveAbode(@Nullable ServerPlayer player) {
		Voice.inner(player, "elder.advice", (Component) null);
	}

	/** A new disciple joined (recruited or accepted): it swears loyalty. */
	public static void discipleLoyal(@Nullable DiscipleEntity disciple) {
		if (disciple != null) {
			Voice.later(40, () -> VoiceDisciples.line(disciple, "loyal", 1));
		}
	}

	/** A protective formation awakens: a disciple near it reports. */
	public static void formationReady(@Nullable ServerPlayer owner, @Nullable Vec3 at) {
		Voice.guard(() -> {
			if (owner == null || at == null) {
				return;
			}
			DiscipleEntity d = VoiceDisciples.nearest(owner, at, 24.0D);
			if (d != null) {
				Voice.later(30, () -> VoiceDisciples.line(d, "formation", 600));
			}
		});
	}

	/** The decree's own line after "天の勅命を、ここに下す！". */
	private static @Nullable String decreeLine(String specId) {
		return switch (specId) {
			case "titah_pedang" -> "decree_sword";
			case "titah_tiang" -> "decree_spear";
			case "titah_bintang" -> "decree_stars";
			case "titah_gunung" -> "decree_lift";
			default -> null;
		};
	}

	// ================================================================== listeners

	private static void breakthrough(ServerPlayer p, Rank before, Rank now) {
		String key;
		double radius;
		if (now.realm() != before.realm()) {
			key = now.realm() == Realm.IMMORTAL_ASCENSION ? "immortal_ascend" : now.realm() == Realm.TRUE_IMMORTAL ? "true_immortal" : "realm";
			radius = now.realm().atLeast(Realm.IMMORTAL_ASCENSION) ? 160.0D : 48.0D;
			Voice.heavenNear(p.level(), p.position(), key, radius);
		} else {
			Voice.heaven(p, "breakthrough");
		}
		DiscipleEntity d = VoiceDisciples.nearest(p, p.position(), 24.0D);
		if (d != null) {
			Voice.later(90, () -> VoiceDisciples.line(d, "cheer", 600));
		}
	}

	private static void death(LivingEntity entity, DamageSource source) {
		if (entity instanceof DiscipleEntity disciple) {
			VoiceDisciples.grunt(disciple, "death");
		} else if (entity instanceof PrimordialBeastEntity) {
			Voice.boom(entity, "boss.defeat", BOSS_HEARD);
			AWAKENED.remove(entity.getUUID());
			ENRAGED.remove(entity.getUUID());
		} else if (entity instanceof RogueCultivatorEntity) {
			Entity killer = source.getEntity();
			UUID by = killer != null ? killer.getUUID() : NOBODY;
			if (Voice.ready(by, "rogue_defeat", 200)) {
				Voice.sayHere(entity, "rogue.defeat", null);
			}
		}
	}

	private static void damaged(LivingEntity entity, DamageSource source, float taken) {
		if (entity instanceof PrimordialBeastEntity boss && boss.isAlive() && boss.getHealth() <= boss.getMaxHealth() * 0.5F
				&& ENRAGED.add(boss.getUUID())) {
			Voice.later(10, () -> Voice.boom(boss, "boss.enraged", BOSS_HEARD));
		}
		Entity by = source.getEntity();
		if (by instanceof PrimordialBeastEntity boss && taken >= 6.0F && boss.isAlive() && Voice.ready(boss.getUUID(), "roar", 200)) {
			Voice.boom(boss, "boss.roar", BOSS_HEARD);
		} else if (by instanceof DiscipleEntity disciple && taken > 0.0F && disciple.getRandom().nextFloat() < 0.12F) {
			VoiceDisciples.grunt(disciple, "attack");
		}
	}

	private static void tick(MinecraftServer server) {
		Voice.tick(server);
		long now = Voice.now();
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			if (player.isRemoved() || (now + player.getId()) % 20L != 0L) {
				continue;
			}
			Voice.guard(() -> scan(player));
		}
		if (AWAKENED.size() > 512) {
			AWAKENED.clear();
		}
		if (ENRAGED.size() > 512) {
			ENRAGED.clear();
		}
	}

	private static void scan(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 at = player.position();
		Seen seen = LAST.get(player.getUUID());
		if (seen != null && seen.dim().equals(level.dimension()) && seen.pos().distanceToSqr(at) > JOURNEY * JOURNEY) {
			// a long teleport: the disciples left at the old spot call after their master
			VoiceDisciples.farewell(player, level, seen.pos());
		}
		LAST.put(player.getUUID(), new Seen(level.dimension(), at));
		if (player.isSpectator()) {
			return;
		}
		VoiceDisciples.scan(player);
		List<PrimordialBeastEntity> bosses = level.getEntitiesOfClass(PrimordialBeastEntity.class, player.getBoundingBox().inflate(BOSS_SCAN),
				LivingEntity::isAlive);
		for (PrimordialBeastEntity boss : bosses) {
			if (AWAKENED.add(boss.getUUID())) {
				Voice.boom(boss, "boss.awaken", BOSS_HEARD);
			} else if (boss.getTarget() instanceof Player && boss.getRandom().nextInt(3) == 0 && Voice.ready(boss.getUUID(), "taunt", 900)) {
				Voice.boom(boss, "boss.taunt", BOSS_HEARD);
			}
		}
	}

	private static void moment(@Nullable ServerLevel level, int moment, int beat, @Nullable Entity subject, @Nullable ServerPlayer viewer,
							   @Nullable Vec3 pos, int a, int b) {
		Vec3 at = pos != null ? pos : viewer != null ? viewer.position() : null;
		switch (moment) {
			case EpicIds.TRIBULATION -> {
				if (beat == EpicIds.TRIB_OMEN) {
					Voice.heavenNear(level, at, "tribulation_begin", 64.0D);
				} else if (beat == EpicIds.TRIB_END_OK) {
					Voice.heaven(viewer, "tribulation_pass");
				} else if (beat == EpicIds.TRIB_END_FAIL) {
					Voice.heaven(viewer, "tribulation_fail");
				}
			}
			case EpicIds.ASCENSION -> {
				if (beat == EpicIds.ASC_VOICE) {
					Voice.heavenNear(level, at, "true_immortal", 160.0D);
				}
			}
			case EpicIds.HEART_DEMON -> heartDemon(viewer, beat);
			case EpicIds.BOSS -> {
				if (beat == EpicIds.BOSS_KILL && subject != null) {
					if (a == EpicIds.BOSS_DEMON_KING || a == EpicIds.BOSS_DEMONIC_ELDER) {
						Voice.boom(subject, "demon.death", BOSS_HEARD);
					} else if (a == EpicIds.BOSS_REALM_GUARDIAN || a == EpicIds.BOSS_HEAVEN_GENERAL || a == EpicIds.BOSS_INHERITANCE) {
						Voice.boom(subject, "boss.defeat", BOSS_HEARD);
					}
				}
			}
			case EpicIds.CHEAT_DEATH -> {
				if (subject instanceof DiscipleEntity disciple) {
					Voice.later(20, () -> VoiceDisciples.line(disciple, "injured", 600));
				} else if (subject instanceof ServerPlayer saved) {
					DiscipleEntity d = VoiceDisciples.nearest(saved, saved.position(), 24.0D);
					if (d != null) {
						VoiceDisciples.line(d, "careful", 600);
					}
				}
			}
			case EpicIds.EXTRA -> extra(level, beat, subject, viewer, at);
			default -> {
			}
		}
	}

	private static void heartDemon(@Nullable ServerPlayer player, int beat) {
		if (player == null) {
			return;
		}
		switch (beat) {
			case EpicIds.HD_WARNING -> Voice.inner(player, "demon.whisper", (Component) null);
			case EpicIds.HD_APPEAR -> {
				Voice.inner(player, "demon.growl", (Component) null);
				if (Voice.isReady(player.getUUID(), "inner:demon.tempt")) {
					Voice.inner(player, "demon.tempt", (Component) null);
				} else {
					Voice.inner(player, "demon.waver", (Component) null);
				}
			}
			case EpicIds.HD_WON -> Voice.inner(player, "demon.lose", (Component) null);
			case EpicIds.HD_LOST -> {
				Voice.inner(player, "demon.win", (Component) null);
				Voice.inner(player, "demon.laugh", (Component) null);
			}
			default -> {
			}
		}
	}

	private static void extra(@Nullable ServerLevel level, int beat, @Nullable Entity subject, @Nullable ServerPlayer viewer, @Nullable Vec3 at) {
		switch (beat) {
			case EpicIds.EXTRA_REALM_GATE -> Voice.heavenNear(level, at, "secret_realm", 96.0D);
			case EpicIds.EXTRA_ASCEND_OMEN -> Voice.heavenNear(level, at, "tribulation_begin", 96.0D);
			case EpicIds.EXTRA_ASCEND_PASS -> Voice.heavenNear(level, at, "immortal_ascend", 160.0D);
			case EpicIds.EXTRA_WRATH_OMEN -> Voice.heavenNear(level, at, "wrath", 96.0D);
			case EpicIds.EXTRA_PLAYER_TRAMPLING -> Voice.heaven(viewer, "witness");
			case EpicIds.EXTRA_INNER_CALAMITY -> {
				if (viewer != null && Voice.ready(viewer.getUUID(), "inner_calamity", 1200)) {
					Voice.heaven(viewer, "calamity");
				}
			}
			case EpicIds.EXTRA_PRIMORDIAL_OMEN -> {
				if (viewer != null && Voice.ready(viewer.getUUID(), "primordial_omen", 600)) {
					Voice.inner(viewer, "boss.roar", (Component) null);
				}
			}
			case EpicIds.EXTRA_PRIMORDIAL_PHASE -> {
				if (subject != null && Voice.ready(subject.getUUID(), "enraged_phase", 600)) {
					ENRAGED.add(subject.getUUID());
					Voice.boom(subject, "boss.enraged", BOSS_HEARD);
				}
			}
			case EpicIds.EXTRA_VILLAGE_OATH -> Voice.inner(viewer, "elder.welcome", VoicePayload.KIND_VILLAGE);
			case EpicIds.EXTRA_RAID_OMEN -> {
				if (viewer != null && Voice.ready(viewer.getUUID(), "raid_omen", 2400)) {
					Voice.inner(viewer, "rogue.ambush", (Component) null);
				}
			}
			case EpicIds.EXTRA_DISCIPLE_ULT -> {
				if (subject instanceof DiscipleEntity disciple) {
					VoiceDisciples.line(disciple, "technique", 200);
				}
			}
			default -> {
			}
		}
	}

	private VoiceHooks() {
	}
}
