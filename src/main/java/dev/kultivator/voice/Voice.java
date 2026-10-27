package dev.kultivator.voice;

import dev.kultivator.network.Net;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The voice API (server side). Every line is played by the receiving client itself ({@link VoicePayload}), on
 * {@code SoundSource.VOICE} (the vanilla "Voice/Speech" slider), with a caption at the bottom of the screen; the client's
 * "Suara bicara" setting can mute it. Nothing here changes gameplay: a call only sends packets, never throws, and is
 * rate-limited so the world never chatters over itself:
 * <ul>
 *   <li>the voice of Heaven queues per listener (one line every {@link #HEAVEN_GAP} ticks, at most {@link #HEAVEN_QUEUE}
 *   ticks ahead; the same key at most once per {@link #HEAVEN_SAME_KEY} ticks per listener);</li>
 *   <li>a speaker says one line at a time ({@link #SPEAKER_GAP});</li>
 *   <li>callers add their own per-key cooldowns with {@link #ready}.</li>
 * </ul>
 */
public final class Voice {

	/** Positional lines are heard and captioned within this many blocks of the speaker. */
	public static final double SAY_RADIUS = 24.0D;
	/** The voice of Heaven: ticks between two lines for one listener (the longest line plus most of its echo). */
	public static final int HEAVEN_GAP = 80;
	/** A heaven line further ahead than this in a listener's queue is dropped. */
	public static final int HEAVEN_QUEUE = 240;
	public static final int HEAVEN_SAME_KEY = 200;
	/** Ticks a speaker stays busy after a line. */
	public static final int SPEAKER_GAP = 50;
	/** Positional volume: the variable-range event is heard up to 16 × volume blocks. */
	private static final float SAY_VOLUME = 1.5F;

	private record Task(long when, Runnable action) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static final Map<UUID, Long> SPEAKER_BUSY = new HashMap<>();
	private static final Map<String, Long> COOLDOWNS = new HashMap<>();
	private static final Map<UUID, Long> HEAVEN_NEXT = new HashMap<>();
	private static long now;
	private static @Nullable MinecraftServer server;

	// ================================================================== the voice of Heaven

	/** The voice of Heaven to one player: non-positional, with its long echo, captioned "Langit". */
	public static void heaven(@Nullable ServerPlayer player, String key) {
		guard(() -> heavenTo(player, key, VoicePayload.KIND_HEAVEN, null));
	}

	/** The voice of Heaven to every player within {@code radius} blocks of {@code pos} in {@code level}. */
	public static void heavenNear(@Nullable ServerLevel level, @Nullable Vec3 pos, String key, double radius) {
		guard(() -> {
			if (level == null || pos == null) {
				return;
			}
			double r2 = radius * radius;
			int kind = VoiceEvents.normalize(key).startsWith("demon.") ? VoicePayload.KIND_DEMON : VoicePayload.KIND_HEAVEN; // a demon key must not get "heaven."
			for (ServerPlayer player : List.copyOf(level.players())) {
				if (player.position().distanceToSqr(pos) <= r2) {
					heavenTo(player, key, kind, null);
				}
			}
		});
	}

	/**
	 * A non-positional line of another kind (the heart demon inside one's head, an old hermit's spirit) to one player, queued
	 * like the voice of Heaven. {@code key} is a full key such as {@code demon.tempt} or {@code elder.inheritance}.
	 */
	public static void inner(@Nullable ServerPlayer player, String key, @Nullable Component speaker) {
		guard(() -> heavenTo(player, key, kindOf(key), speaker));
	}

	/** {@link #inner} with another caption kind (e.g. {@link VoicePayload#KIND_VILLAGE} for a village head's elder voice). */
	public static void inner(@Nullable ServerPlayer player, String key, int kind) {
		guard(() -> heavenTo(player, key, kind, null));
	}

	private static void heavenTo(@Nullable ServerPlayer player, String rawKey, int kind, @Nullable Component speaker) {
		if (player == null || player.isRemoved() || player.hasDisconnected()) {
			return;
		}
		String key = full(rawKey, kind == VoicePayload.KIND_HEAVEN ? "heaven." : "");
		if (!VoiceEvents.exists(key) || !ready(player.getUUID(), "inner:" + key, HEAVEN_SAME_KEY)) {
			return;
		}
		UUID id = player.getUUID();
		long next = HEAVEN_NEXT.getOrDefault(id, 0L);
		if (next > now) {
			if (next - now > HEAVEN_QUEUE) {
				return;
			}
			HEAVEN_NEXT.put(id, next + HEAVEN_GAP);
			later((int) (next - now), () -> {
				ServerPlayer still = server == null ? null : server.getPlayerList().getPlayer(id);
				if (still != null) {
					sendInner(still, key, kind, speaker);
				}
			});
			return;
		}
		HEAVEN_NEXT.put(id, now + HEAVEN_GAP);
		sendInner(player, key, kind, speaker);
	}

	private static void sendInner(ServerPlayer player, String key, int kind, @Nullable Component speaker) {
		String caption = VoiceEvents.captionOf(key);
		Vec3 at = player.getEyePosition();
		Net.send(player, new VoicePayload("voice." + key, caption == null ? "" : caption, Optional.ofNullable(speaker), kind, false, -1,
				at.x, at.y, at.z, 1.0F, 1.0F));
	}

	// ================================================================== speakers in the world

	/**
	 * {@code speaker} says {@code eventKey} (e.g. {@code disciple.f2.greet}, {@code rogue.ambush}, {@code grunt.male.hurt}):
	 * a positional mono line that follows the speaker, captioned for every player within {@link #SAY_RADIUS} blocks.
	 * {@code captionKey} null takes the line's own caption; "" shows none. Skipped while the speaker is still talking.
	 */
	public static void say(@Nullable Entity speaker, String eventKey, @Nullable String captionKey) {
		guard(() -> sayNow(speaker, eventKey, captionKey, 1.0F, true));
	}

	/**
	 * {@link #say}, but the sound stays where the speaker is now instead of following it: for a speaker that is about to
	 * vanish (a fleeing rogue's cloud step, a death), whose entity-bound sound would stop with it.
	 */
	public static void sayHere(@Nullable Entity speaker, String eventKey, @Nullable String captionKey) {
		guard(() -> sayNow(speaker, eventKey, captionKey, 1.0F, false));
	}

	/** {@link #say} with a pitch (grunts vary a little). */
	public static void say(@Nullable Entity speaker, String eventKey, @Nullable String captionKey, float pitch) {
		guard(() -> sayNow(speaker, eventKey, captionKey, pitch, true));
	}

	/** True when {@code speaker} can start a line now (not removed, not still talking). */
	public static boolean free(@Nullable Entity speaker) {
		return speaker != null && !speaker.isRemoved() && SPEAKER_BUSY.getOrDefault(speaker.getUUID(), 0L) <= now;
	}

	private static void sayNow(@Nullable Entity speaker, String eventKey, @Nullable String captionKey, float pitch, boolean follow) {
		if (speaker == null || !(speaker.level() instanceof ServerLevel level)) {
			return;
		}
		String key = VoiceEvents.normalize(eventKey);
		if (!VoiceEvents.exists(key)) {
			return;
		}
		UUID id = speaker.getUUID();
		boolean grunt = key.startsWith("grunt.");
		if (!grunt) {
			if (SPEAKER_BUSY.getOrDefault(id, 0L) > now) {
				return;
			}
			SPEAKER_BUSY.put(id, now + SPEAKER_GAP);
		}
		String caption = captionKey == null ? VoiceEvents.captionOf(key) : captionKey;
		Vec3 at = speaker.getEyePosition();
		VoicePayload payload = new VoicePayload("voice." + key, caption == null ? "" : caption, Optional.of(speaker.getDisplayName()), kindOf(key),
				true, follow && speaker.isAlive() ? speaker.getId() : -1, at.x, at.y, at.z, SAY_VOLUME, pitch);
		double r2 = SAY_RADIUS * SAY_RADIUS;
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (player.distanceToSqr(at) <= r2) {
				Net.send(player, payload);
			}
		}
	}

	/**
	 * A non-positional line from a big speaker (an ancient beast's stereo voice fills the sky) for every player within
	 * {@code radius} blocks of it, captioned with its name. Skipped while it is still talking.
	 */
	public static void boom(@Nullable Entity speaker, String eventKey, double radius) {
		guard(() -> {
			if (speaker == null || !(speaker.level() instanceof ServerLevel level)) {
				return;
			}
			String key = VoiceEvents.normalize(eventKey);
			if (!VoiceEvents.exists(key)) {
				return;
			}
			UUID id = speaker.getUUID();
			if (SPEAKER_BUSY.getOrDefault(id, 0L) > now) {
				return;
			}
			SPEAKER_BUSY.put(id, now + SPEAKER_GAP + 20);
			String caption = VoiceEvents.captionOf(key);
			Vec3 at = speaker.position();
			VoicePayload payload = new VoicePayload("voice." + key, caption == null ? "" : caption, Optional.of(speaker.getDisplayName()),
					kindOf(key), false, speaker.getId(), at.x, at.y, at.z, 1.0F, 1.0F);
			double r2 = radius * radius;
			for (ServerPlayer player : List.copyOf(level.players())) {
				if (player.distanceToSqr(at) <= r2) {
					Net.send(player, payload);
				}
			}
		});
	}

	/**
	 * {@code speaker}'s line heard by {@code listener} alone, non-positional (a disciple left behind calls after its master
	 * who is already leaving). Respects the speaker's busy time.
	 */
	public static void callTo(@Nullable ServerPlayer listener, @Nullable Entity speaker, String eventKey) {
		guard(() -> {
			if (listener == null || speaker == null) {
				return;
			}
			String key = VoiceEvents.normalize(eventKey);
			if (!VoiceEvents.exists(key) || SPEAKER_BUSY.getOrDefault(speaker.getUUID(), 0L) > now) {
				return;
			}
			SPEAKER_BUSY.put(speaker.getUUID(), now + SPEAKER_GAP);
			String caption = VoiceEvents.captionOf(key);
			Vec3 at = listener.getEyePosition();
			Net.send(listener, new VoicePayload("voice." + key, caption == null ? "" : caption, Optional.of(speaker.getDisplayName()), kindOf(key),
					false, -1, at.x, at.y, at.z, 0.8F, 1.0F));
		});
	}

	// ================================================================== cooldowns and timers

	/** True (and arms the cooldown) when {@code key} of {@code who} has not been used in the last {@code ticks}. */
	public static boolean ready(UUID who, String key, int ticks) {
		String k = who + "|" + key;
		Long until = COOLDOWNS.get(k);
		if (until != null && until > now) {
			return false;
		}
		COOLDOWNS.put(k, now + Math.max(1, ticks));
		return true;
	}

	/** True when {@code key} of {@code who} is free now, without arming it. */
	public static boolean isReady(UUID who, String key) {
		Long until = COOLDOWNS.get(who + "|" + key);
		return until == null || until <= now;
	}

	/** Runs {@code action} on the server thread {@code ticks} ticks from now (dropped when the server stops). */
	public static void later(int ticks, Runnable action) {
		TASKS.add(new Task(now + Math.max(1, ticks), action));
	}

	/** The server tick count used by every cooldown here. */
	public static long now() {
		return now;
	}

	static @Nullable MinecraftServer server() {
		return server;
	}

	static void start(MinecraftServer s) {
		server = s;
	}

	static void tick(MinecraftServer s) {
		server = s;
		now = s.getTickCount();
		if (!TASKS.isEmpty()) {
			List<Task> due = new ArrayList<>();
			for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
				Task task = it.next();
				if (task.when() <= now) {
					due.add(task);
					it.remove();
				}
			}
			for (Task task : due) {
				guard(task.action());
			}
		}
		if (now % 1200L == 0L) {
			COOLDOWNS.values().removeIf(until -> until <= now);
			SPEAKER_BUSY.values().removeIf(until -> until <= now);
			HEAVEN_NEXT.values().removeIf(until -> until <= now);
		}
	}

	static void clear() {
		TASKS.clear();
		SPEAKER_BUSY.clear();
		COOLDOWNS.clear();
		HEAVEN_NEXT.clear();
		server = null;
	}

	// ================================================================== helpers

	/** The payload kind of a key, by its group. */
	static int kindOf(String rawKey) {
		String key = VoiceEvents.normalize(rawKey);
		if (key.startsWith("heaven.")) {
			return VoicePayload.KIND_HEAVEN;
		} else if (key.startsWith("demon.")) {
			return VoicePayload.KIND_DEMON;
		} else if (key.startsWith("boss.")) {
			return VoicePayload.KIND_BOSS;
		} else if (key.startsWith("elder.")) {
			return VoicePayload.KIND_ELDER;
		} else if (key.startsWith("rogue.")) {
			return VoicePayload.KIND_ROGUE;
		} else if (key.startsWith("disciple.")) {
			return VoicePayload.KIND_DISCIPLE;
		}
		return VoicePayload.KIND_GRUNT;
	}

	/** {@code key} without {@code voice.}, with {@code prefix} added when it is missing (heaven lines may omit "heaven."). */
	private static String full(String rawKey, String prefix) {
		String key = VoiceEvents.normalize(rawKey);
		return !prefix.isEmpty() && !key.startsWith(prefix) ? prefix + key : key;
	}

	static void guard(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException e) {
			dev.kultivator.Kultivator.LOGGER.warn("Kultivator voice: a line was skipped", e);
		}
	}

	private Voice() {
	}
}
