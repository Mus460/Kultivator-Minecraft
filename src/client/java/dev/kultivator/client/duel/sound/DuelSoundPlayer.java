package dev.kultivator.client.duel.sound;

import dev.kultivator.client.duel.DuelBus;
import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.duel.DuelClock;
import dev.kultivator.client.epic.sound.MusicCue;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.clash.SwingKind;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.sound.DuelSounds;
import dev.kultivator.sound.FilmSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Every sword-duel sound of DESIGN 35.6.1 (contract C35.U5), played <b>on the client</b> from the same payloads every
 * client in range receives, so a clang lands on the frame of its sparks (pattern C30.4 {@code FilmSound}). The visual
 * module never plays a sound; this listener of {@link DuelBus} does:
 * <ul>
 * <li>{@link #clash}: the layered cue of each {@code DuelClash} outcome at the contact point (mono, positional, on
 * {@link SoundSource#PLAYERS}); the duellists themselves also hear the stereo layer of the table (bell, thud, sub boom)
 * without a position. A Deathblow is silent for 3 ticks first (the {@code SilentImpact} pattern, after Gu Long's duel on
 * the Forbidden City roofs: one cut, then the sound).</li>
 * <li>{@link #swing}: the whoosh of each weapon class at the start of the strike (never for a Better Combat swing,
 * which plays its own), the qi hum, the riser of a heavy charge (own swings), the cut-short whoosh of a feint and the
 * release of a Sword Qi Wave.</li>
 * <li>the steel-on-steel scrape of a Blade Lock every 3 ticks (pitch rising with the pressure) with a chain rattle every
 * 5 ticks;</li>
 * <li>the presentation of a formal duel: intro wind, taiko, riser and gong (35.4.8), the countdown drum, the start beat,
 * the finisher's whoosh and sheathing click (35.4.9), the bout and triumph music cues (35.6.3).</li>
 * </ul>
 * At most {@link #MAX_PER_TICK} cues per client tick (a cue with all its layers counts once; the cinematic beats are
 * never dropped). Positional cues farther than 48 blocks from the camera are skipped.
 */
public final class DuelSoundPlayer implements DuelBus.Listener {

	public static final int MAX_PER_TICK = 4;
	private static final double HEAR_RANGE = 48.0D;
	/** Bout music: below the tribulation and ultimate cues (7b uses 50+ for those). */
	private static final int MUSIC_PRIO = 30;

	private static final DuelSoundPlayer INSTANCE = new DuelSoundPlayer();
	private static final RandomSource RANDOM = RandomSource.create();

	private record Task(long due, boolean force, Runnable action) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static int playedThisTick;
	private static long ticks;
	/** The last phase seen per session (to fire the beats once per phase change). */
	private static final Map<Integer, Integer> PHASES = new HashMap<>();
	/** Active blade locks: key (a, b) → the client tick of the last sync, and the last pressure. */
	private static final Map<Long, int[]> BINDS = new HashMap<>();

	/** Registers the listener (from {@code DuelClient.init}). */
	public static void init() {
		DuelBus.register(INSTANCE);
	}

	/** Once per client tick: runs due tasks, the Blade Lock loop, and resets the cue counter. */
	public static void tick(Minecraft minecraft) {
		ticks++;
		playedThisTick = 0;
		if (minecraft.level == null) {
			TASKS.clear();
			BINDS.clear();
			return;
		}
		if (!TASKS.isEmpty()) {
			List<Task> due = new ArrayList<>();
			for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
				Task task = it.next();
				if (task.due() <= ticks) {
					due.add(task);
					it.remove();
				}
			}
			for (Task task : due) {
				if (task.force() || playedThisTick < MAX_PER_TICK) {
					try {
						task.action().run();
					} catch (RuntimeException ignored) {
						// a sound is never worth an error
					}
				}
			}
		}
		tickBinds(minecraft.level);
	}

	/** Disconnect, dimension change. */
	public static void clear() {
		TASKS.clear();
		PHASES.clear();
		BINDS.clear();
		MusicCue.release(MusicCue.Cue.M_BOUT);
	}

	// ================================================================== bus

	@Override
	public void swing(DuelPayloads.DuelSwing p, boolean predicted) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		boolean own = isLocal(p.entityId());
		if (predicted && !own) {
			return;
		}
		if ((p.flags() & DuelIds.SW_REJECTED) != 0) {
			return;
		}
		if ((p.flags() & DuelIds.SW_FEINT_CANCEL) != 0) {
			Entity e = level.getEntity(p.entityId());
			if (e != null && take(false)) {
				at(e.position().add(0.0D, 1.2D, 0.0D), FilmSounds.WHOOSH, 0.3F, 1.8F);
			}
			return;
		}
		if (p.windup() == DuelIds.WINDUP_CHARGING) {
			// A heavy charge begins: the riser is for the charger alone (35.6.1).
			if (own && take(false)) {
				self(FilmSounds.RISER_SHORT, 0.35F, 1.0F);
			}
			return;
		}
		if ((p.flags() & DuelIds.SW_BC) != 0) {
			// Better Combat plays its own swing sound; only the qi hum and the wave are ours.
			if ((p.flags() & DuelIds.SW_QI) != 0) {
				scheduleStrike(p, true);
			}
			return;
		}
		scheduleStrike(p, false);
	}

	/** Plays the class whoosh (and qi layers) when the strike begins: 2 ticks before the impact. */
	private static void scheduleStrike(DuelPayloads.DuelSwing p, boolean qiOnly) {
		long impact = (long) p.startTick() + p.windup();
		long now = DuelClock.known() ? DuelClock.serverTick() : impact;
		long delay = Mth.clamp(impact - 2L - now, 0L, 40L);
		int entityId = p.entityId();
		WeaponClass weapon = WeaponClass.byOrdinal(p.weaponClass());
		boolean qi = (p.flags() & DuelIds.SW_QI) != 0;
		boolean wave = (p.flags() & DuelIds.SW_QI_WAVE) != 0;
		SwingKind kind = SwingKind.byOrdinal(p.kind());
		later(delay, false, () -> {
			ClientLevel level = Minecraft.getInstance().level;
			Entity e = level == null ? null : level.getEntity(entityId);
			if (e == null || !take(false)) {
				return;
			}
			Vec3 pos = e.position().add(0.0D, e.getBbHeight() * 0.65D, 0.0D);
			if (!qiOnly) {
				whoosh(pos, weapon, kind);
			}
			if (qi) {
				at(pos, FilmSounds.HUM_ENERGY, 0.25F, 1.1F + RANDOM.nextFloat() * 0.1F);
			}
		});
		if (wave) {
			later(delay + 2L, false, () -> {
				ClientLevel level = Minecraft.getInstance().level;
				Entity e = level == null ? null : level.getEntity(entityId);
				if (e == null || !take(false)) {
					return;
				}
				Vec3 pos = e.position().add(0.0D, e.getBbHeight() * 0.6D, 0.0D);
				at(pos, DuelSounds.QI_WAVE, 0.9F, 0.8F);
				at(pos, SoundEvents.BREEZE_WHIRL, 0.4F, 1.0F);
			});
		}
	}

	/** The whoosh of each weapon class (35.6.1, "Desau per kelas senjata"). */
	private static void whoosh(Vec3 pos, WeaponClass weapon, SwingKind kind) {
		float heavy = kind.heavy() || kind == SwingKind.PENENTU ? 0.85F : 1.0F;
		switch (weapon) {
			case PEDANG -> {
				at(pos, SoundEvents.PLAYER_ATTACK_SWEEP, 0.45F, 1.3F * heavy + jitter(0.05F));
				at(pos, FilmSounds.WHOOSH, 0.35F, 1.4F * heavy + jitter(0.06F));
			}
			case PEDANG_BERAT -> at(pos, FilmSounds.WHOOSH, 0.5F, 0.9F * heavy + jitter(0.05F));
			case KAPAK -> at(pos, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.85F * heavy + jitter(0.05F));
			case TOMBAK -> at(pos, SoundEvents.SPEAR_ATTACK.value(), 0.6F, 1.0F * heavy + jitter(0.05F));
			case GADA -> at(pos, SoundEvents.MACE_SMASH_AIR, 0.6F, 1.1F * heavy + jitter(0.05F));
			case TANGAN_KOSONG -> at(pos, SoundEvents.PLAYER_ATTACK_WEAK, 0.5F, 1.0F + jitter(0.08F));
		}
	}

	@Override
	public void clash(DuelPayloads.DuelClash p, boolean predicted) {
		if (!predicted && (p.flags() & DuelIds.CF_PREDICTED_MATCH) != 0) {
			// Already heard as the defender's prediction (35.8.2): never twice.
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Vec3 pos = new Vec3(p.x(), p.y(), p.z());
		boolean self = isLocal(p.aId()) || isLocal(p.bId());
		int outcome = p.outcome();
		boolean heavy = p.power() >= 160;
		WeaponClass weaponA = weaponOf(p.aId());
		WeaponClass weaponB = weaponOf(p.bId());
		boolean bare = weaponA == WeaponClass.TANGAN_KOSONG && weaponB == WeaponClass.TANGAN_KOSONG;
		if (outcome == DuelIds.OUT_DEATHBLOW) {
			deathblow(pos, self, p.aId());
			return;
		}
		if (!near(pos) || !take(outcome == DuelIds.OUT_PERFECT || outcome == DuelIds.OUT_GUARD_BREAK || outcome == DuelIds.OUT_POSTURE_BREAK)) {
			return;
		}
		switch (outcome) {
			case DuelIds.OUT_HIT -> {
				if (weaponA == WeaponClass.TANGAN_KOSONG) {
					at(pos, SoundEvents.PLAYER_ATTACK_STRONG, 0.7F, 1.1F + jitter(0.05F));
				} else {
					at(pos, heavy ? SoundEvents.PLAYER_ATTACK_CRIT : DuelSounds.HIT, 0.9F, 1.0F + jitter(0.05F));
				}
			}
			case DuelIds.OUT_BLOCK -> {
				at(pos, DuelSounds.BLOCK, heavy ? 1.0F : 0.85F, (heavy ? 1.1F : 1.3F) + jitter(0.05F));
				if (self) {
					self(FilmSounds.THUD, 0.35F, heavy ? 0.9F : 1.15F);
				}
			}
			case DuelIds.OUT_DEFLECT -> {
				at(pos, DuelSounds.PARRY, 1.0F, 1.0F + jitter(0.06F));
				at(pos, SoundEvents.TRIDENT_HIT, 0.5F, 1.4F + jitter(0.05F));
			}
			case DuelIds.OUT_PERFECT -> {
				at(pos, DuelSounds.PARRY_PERFECT, 1.1F, 1.0F + jitter(0.03F));
				at(pos, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7F, 1.4F);
				at(pos, SoundEvents.BELL_RESONATE, 0.3F, 1.8F);
				if (self) {
					self(FilmSounds.BELL, 0.5F, 1.5F);
				}
			}
			case DuelIds.OUT_CLASH, DuelIds.OUT_CLASH_MENANG -> {
				if (bare) {
					at(pos, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8F, 1.0F + jitter(0.06F));
				} else if (heavy || outcome == DuelIds.OUT_CLASH_MENANG) {
					heavyClang(pos);
				} else {
					at(pos, DuelSounds.CLASH, 1.0F, 0.95F + RANDOM.nextFloat() * 0.2F);
					at(pos, SoundEvents.ANVIL_LAND, 0.25F, 1.7F + jitter(0.05F));
				}
			}
			case DuelIds.OUT_BIND -> heavyClang(pos);
			case DuelIds.OUT_BIND_MENANG_A, DuelIds.OUT_BIND_MENANG_B, DuelIds.OUT_BIND_LEPAS -> {
				heavyClang(pos);
				at(pos, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.8F + jitter(0.05F));
			}
			case DuelIds.OUT_BIND_RELEASE_TRICK -> {
				at(pos, DuelSounds.PARRY, 0.9F, 1.2F);
				at(pos, FilmSounds.WHOOSH, 0.35F, 1.6F);
			}
			case DuelIds.OUT_GUARD_BREAK -> {
				at(pos, DuelSounds.GUARD_BREAK, 1.0F, 1.0F);
				at(pos, SoundEvents.ANVIL_HIT, 0.6F, 0.6F);
				if (self) {
					self(FilmSounds.SUB_BOOM, 0.55F, 1.0F);
				}
			}
			case DuelIds.OUT_POSTURE_BREAK -> {
				at(pos, DuelSounds.POSTURE_BREAK, 1.0F, 1.0F);
				at(pos, SoundEvents.IRON_GOLEM_ATTACK, 0.7F, 0.6F);
			}
			case DuelIds.OUT_EVADE -> at(pos, FilmSounds.WHOOSH, 0.3F, 1.2F + jitter(0.08F));
			case DuelIds.OUT_MIKIRI -> {
				at(pos, DuelSounds.PARRY_PERFECT, 1.0F, 0.9F);
				at(pos, SoundEvents.ANVIL_LAND, 0.4F, 0.9F);
				at(pos, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 0.9F);
				if (self) {
					self(FilmSounds.THUD, 0.5F, 0.8F);
				}
			}
			case DuelIds.OUT_QI_DEFLECT -> {
				at(pos, DuelSounds.PARRY, 0.9F, 1.1F);
				at(pos, FilmSounds.ZAP, 0.5F, 1.1F);
			}
			case DuelIds.OUT_QI_REFLECT -> {
				at(pos, DuelSounds.QI_REFLECT, 1.0F, 1.0F);
				at(pos, SoundEvents.BREEZE_DEFLECT, 0.8F, 1.0F);
			}
			case DuelIds.OUT_COMPANION_DEFLECT -> {
				at(pos, DuelSounds.PARRY, 0.8F, 1.5F);
				at(pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.6F);
			}
			default -> {
				// WHIFF: the swing's whoosh already played
			}
		}
	}

	private static void heavyClang(Vec3 pos) {
		at(pos, DuelSounds.CLASH_HEAVY, 1.1F, 1.0F + jitter(0.04F));
		at(pos, SoundEvents.HEAVY_CORE_HIT, 0.6F, 1.0F);
		at(pos, SoundEvents.ANVIL_LAND, 0.35F, 1.1F);
	}

	/** Silence for 3 ticks, then the boom (35.6.1 {@code duel.deathblow}). */
	private static void deathblow(Vec3 pos, boolean self, int attackerId) {
		later(3L, true, () -> {
			playedThisTick++;
			at(pos, DuelSounds.DEATHBLOW, 1.2F, 0.8F);
			at(pos, SoundEvents.PLAYER_ATTACK_STRONG, 0.9F, 0.7F);
			if (self) {
				self(FilmSounds.SUB_BOOM, 0.8F, 0.9F);
			} else {
				at(pos, SoundEvents.WARDEN_ATTACK_IMPACT, 0.4F, 1.2F);
			}
		});
		// The attacker's kiai is the server's (DuelVoices.kiai); the whoosh of the deathblow swing already played.
	}

	@Override
	public void bind(DuelPayloads.DuelBindSync p) {
		long key = pairKey(p.aId(), p.bId());
		if ((p.flags() & DuelIds.BF_END) != 0) {
			BINDS.remove(key);
			return;
		}
		int[] state = BINDS.computeIfAbsent(key, k -> new int[]{p.aId(), p.bId(), 0, 0});
		state[2] = (int) ticks;
		state[3] = p.pressure();
	}

	/** Every tick of a live lock: the scrape every 3 ticks, the chain every 5. */
	private static void tickBinds(ClientLevel level) {
		if (BINDS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<Long, int[]>> it = BINDS.entrySet().iterator(); it.hasNext(); ) {
			int[] s = it.next().getValue();
			if ((int) ticks - s[2] > 6) {
				it.remove();
				continue;
			}
			Entity a = level.getEntity(s[0]);
			Entity b = level.getEntity(s[1]);
			if (a == null || b == null) {
				continue;
			}
			Vec3 pos = a.position().add(b.position()).scale(0.5D).add(0.0D, Math.min(a.getBbHeight(), b.getBbHeight()) * 0.7D, 0.0D);
			if (!near(pos)) {
				continue;
			}
			float strain = Math.min(1.0F, Math.abs(s[3]) / 100.0F);
			if (ticks % 3L == 0L && take(false)) {
				at(pos, DuelSounds.SCRAPE, 0.45F + 0.3F * strain, 1.6F + 0.3F * strain + jitter(0.03F));
			}
			if (ticks % 5L == 0L && take(false)) {
				at(pos, SoundEvents.CHAIN_HIT, 0.2F, 0.8F + jitter(0.05F));
			}
		}
	}

	@Override
	public void session(DuelPayloads.DuelSessionSync p) {
		Integer before = PHASES.put(p.sessionId(), p.phase());
		if (p.phase() == DuelIds.PHASE_ENDED) {
			PHASES.remove(p.sessionId());
			MusicCue.release(MusicCue.Cue.M_BOUT);
			return;
		}
		if (before != null && before == p.phase()) {
			return;
		}
		boolean self = isLocal(p.aId()) || isLocal(p.bId());
		Vec3 center = new Vec3(p.cx(), p.cy() + 1.2D, p.cz());
		boolean film = (p.flags() & DuelIds.SF_INTRO_FILM) != 0;
		switch (p.phase()) {
			case DuelIds.PHASE_INTRO -> intro(center, self, film, p.phaseTicksLeft());
			case DuelIds.PHASE_COUNTDOWN -> countdown(center, p.phaseTicksLeft());
			case DuelIds.PHASE_FIGHT -> later(0L, true, () -> {
				// 始: a double drum and a short snare as the fight begins.
				at(center, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.4F, 0.6F);
				at(center, SoundEvents.NOTE_BLOCK_SNARE.value(), 0.9F, 0.9F);
			});
			case DuelIds.PHASE_FINISHER -> finisher(p, self);
			default -> {
			}
		}
	}

	/** 35.4.8: wind and taiko at 0 and 10 (and 55 with the film), the riser at 80, the gong at 100 (40 without film). */
	private static void intro(Vec3 center, boolean self, boolean film, int ticksLeft) {
		int length = film ? 100 : 40;
		int elapsed = Mth.clamp(length - ticksLeft, 0, length);
		beat(0, elapsed, () -> {
			at(center, DuelSounds.INTRO_WIND, 0.25F, 0.6F);
			at(center, SoundEvents.CHERRY_LEAVES_FALL, 0.6F, 1.0F);
			at(center, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.4F, 0.5F);
		});
		beat(10, elapsed, () -> at(center, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.4F, 0.5F));
		if (film) {
			beat(55, elapsed, () -> at(center, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.5F, 0.55F));
			if (self) {
				beat(80, elapsed, () -> self(FilmSounds.RISER_SHORT, 0.45F, 1.0F));
			}
		}
		beat(length, elapsed, () -> {
			if (self) {
				self(DuelSounds.GONG, 0.7F, 1.0F);
			} else {
				at(center, SoundEvents.BELL_BLOCK, 1.0F, 0.6F);
			}
		});
		MusicCue.play(MusicCue.Cue.M_BOUT, 0, MUSIC_PRIO);
	}

	/** 三 二 一: a drum every second of the 60-tick countdown. */
	private static void countdown(Vec3 center, int ticksLeft) {
		int elapsed = Mth.clamp(60 - ticksLeft, 0, 60);
		for (int beat = 0; beat < 60; beat += 20) {
			beat(beat, elapsed, () -> at(center, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.2F, 0.7F));
		}
	}

	/** 35.4.9: the slowed whoosh at 3, the sheathing click of the winner at ~32, triumph music at 40. */
	private static void finisher(DuelPayloads.DuelSessionSync p, boolean self) {
		int elapsed = Mth.clamp(60 - p.phaseTicksLeft(), 0, 60);
		Vec3 center = new Vec3(p.cx(), p.cy() + 1.2D, p.cz());
		boolean surrender = (p.flags() & DuelIds.SF_SURRENDER) != 0;
		if (!surrender) {
			beat(3, elapsed, () -> {
				if (self) {
					self(FilmSounds.WHOOSH, 0.5F, 0.5F);
				} else {
					at(center, FilmSounds.WHOOSH, 0.45F, 0.5F);
				}
			});
		}
		beat(32, elapsed, () -> {
			// The winner is unknown until DuelResult; the click is placed at the centre (both stand within 3 blocks).
			at(center, DuelSounds.SHEATHE, 0.3F, 1.8F);
			at(center, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.4F, 1.0F);
		});
		beat(40, elapsed, () -> {
			MusicCue.release(MusicCue.Cue.M_BOUT);
			MusicCue.play(MusicCue.Cue.M_TRIUMPH, 12, MUSIC_PRIO);
		});
	}

	private static void beat(int at, int elapsed, Runnable action) {
		if (at >= elapsed) {
			later(at - elapsed, true, () -> {
				playedThisTick++;
				action.run();
			});
		}
	}

	// ================================================================== helpers

	private static boolean isLocal(int entityId) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.player.getId() == entityId;
	}

	private static WeaponClass weaponOf(int entityId) {
		DuelPayloads.DuelSwing swing = DuelClient.swing(entityId);
		if (swing != null) {
			return WeaponClass.byOrdinal(swing.weaponClass());
		}
		DuelPayloads.DuelFighterSync fighter = DuelClient.fighter(entityId);
		if (fighter != null) {
			return WeaponClass.byOrdinal(fighter.weaponClass());
		}
		ClientLevel level = Minecraft.getInstance().level;
		Entity e = level == null ? null : level.getEntity(entityId);
		return e instanceof net.minecraft.world.entity.LivingEntity living ? WeaponClass.of(living) : WeaponClass.TANGAN_KOSONG;
	}

	/** Counts one cue against the per-tick cap; {@code important} cues may exceed it by one. */
	private static boolean take(boolean important) {
		if (playedThisTick >= MAX_PER_TICK + (important ? 1 : 0)) {
			return false;
		}
		playedThisTick++;
		return true;
	}

	private static boolean near(Vec3 pos) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		return camera == null || camera.position().distanceToSqr(pos) <= HEAR_RANGE * HEAR_RANGE;
	}

	/** A mono, positioned layer on the Players source. */
	private static void at(Vec3 pos, SoundEvent event, float volume, float pitch) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null && event != null) {
			level.playLocalSound(pos.x, pos.y, pos.z, event, SoundSource.PLAYERS, volume, Mth.clamp(pitch, 0.5F, 2.0F), false);
		}
	}

	/** A stereo layer for the listener alone (no position), on the Players source. */
	private static void self(SoundEvent event, float volume, float pitch) {
		if (event == null) {
			return;
		}
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event.location(), SoundSource.PLAYERS, volume,
				Mth.clamp(pitch, 0.5F, 2.0F), SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true));
	}

	private static void later(long delay, boolean force, Runnable action) {
		if (delay <= 0L) {
			if (force || playedThisTick < MAX_PER_TICK) {
				action.run();
			}
			return;
		}
		TASKS.add(new Task(ticks + delay, force, action));
	}

	private static float jitter(float amount) {
		return (RANDOM.nextFloat() * 2.0F - 1.0F) * amount;
	}

	private static long pairKey(int a, int b) {
		int lo = Math.min(a, b);
		int hi = Math.max(a, b);
		return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
	}

	/** Positional layer for other U classes (crowd). */
	static void playAt(Vec3 pos, SoundEvent event, float volume, float pitch) {
		at(pos, event, volume, pitch);
	}

	/** Schedules a crowd layer (counts against the cap when it plays). */
	static void schedule(long delay, Runnable action) {
		later(delay, false, () -> {
			if (take(false)) {
				action.run();
			}
		});
	}

	/** The listener (for tests). */
	public static @Nullable DuelBus.Listener listener() {
		return INSTANCE;
	}

	private DuelSoundPlayer() {
	}
}
