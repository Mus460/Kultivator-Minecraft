package dev.kultivator.client.duel.sound;

import dev.kultivator.client.duel.DuelBus;
import dev.kultivator.client.duel.DuelClient;
import dev.kultivator.client.duel.ui.DuelSettings;
import dev.kultivator.duel.DuelIds;
import dev.kultivator.duel.net.DuelPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The onlookers of a duel (DESIGN 35.6.3), client side: when the session has a crowd (the sect arena with seated
 * disciples, the crowd rule, or at least two players watching), three {@code VILLAGER_CELEBRATE} voices at pitch
 * 0.85-1.15 and volume 0.15, 0, 3 and 7 ticks apart, rise from around the ring on a chain of 6, a heavy Perfect Deflect,
 * a Guard Break and the end of the duel; a low breath ({@code ELYTRA_FLYING} at 0.05) runs through the crowd when a Blade
 * Lock begins; a snare roll answers the Sword Dance (chain 4). The disciples' spoken cheers are the server's
 * ({@code DuelVoices.cheer}). Setting "Sorak penonton" turns this off.
 */
public final class DuelCrowd implements DuelBus.Listener {

	private static final DuelCrowd INSTANCE = new DuelCrowd();
	private static final RandomSource RANDOM = RandomSource.create();
	/** The chain each fighter showed last (cheers fire when it reaches 4 and 6). */
	private static final Map<Integer, Integer> CHAINS = new HashMap<>();
	private static final Map<Integer, Long> LAST_CHEER = new HashMap<>();
	private static long ticks;

	public static void init() {
		DuelBus.register(INSTANCE);
	}

	public static void tick() {
		ticks++;
		if (ticks % 600L == 0L) {
			LAST_CHEER.values().removeIf(at -> ticks - at > 600L);
		}
	}

	public static void clear() {
		CHAINS.clear();
		LAST_CHEER.clear();
	}

	@Override
	public void fighter(DuelPayloads.DuelFighterSync p) {
		int before = CHAINS.getOrDefault(p.entityId(), 0);
		CHAINS.put(p.entityId(), p.chain());
		if (p.chain() <= before) {
			return;
		}
		DuelPayloads.DuelSessionSync session = DuelClient.session(p.entityId());
		if (session == null || !crowded(session)) {
			return;
		}
		Vec3 center = new Vec3(session.cx(), session.cy() + 1.0D, session.cz());
		if (p.chain() == 4 && before < 4) {
			snareRoll(center);
		}
		if (p.chain() == 6 && before < 6) {
			cheer(session, center);
		}
	}

	@Override
	public void clash(DuelPayloads.DuelClash p, boolean predicted) {
		if (predicted) {
			// the crowd reacts to what the server decided (a matched prediction still arrives as the real clash)
			return;
		}
		int outcome = p.outcome();
		boolean heavyPerfect = outcome == DuelIds.OUT_PERFECT && p.power() >= 160;
		if (!heavyPerfect && outcome != DuelIds.OUT_GUARD_BREAK && outcome != DuelIds.OUT_BIND && outcome != DuelIds.OUT_MIKIRI) {
			return;
		}
		DuelPayloads.DuelSessionSync session = DuelClient.session(p.aId());
		if (session == null) {
			session = DuelClient.session(p.bId());
		}
		if (session == null || !crowded(session)) {
			return;
		}
		Vec3 center = new Vec3(session.cx(), session.cy() + 1.0D, session.cz());
		if (outcome == DuelIds.OUT_BIND) {
			// the crowd holds its breath
			DuelSoundPlayer.schedule(2L, () -> DuelSoundPlayer.playAt(center, SoundEvents.ELYTRA_FLYING, 0.05F, 0.5F));
		} else {
			cheer(session, center);
		}
	}

	@Override
	public void result(DuelPayloads.DuelResult p) {
		if (p.reason() == DuelIds.RES_BATAL) {
			return;
		}
		DuelPayloads.DuelSessionSync session = DuelClient.sessionById(p.sessionId());
		if (session == null || !crowded(session)) {
			return;
		}
		Vec3 center = new Vec3(session.cx(), session.cy() + 1.0D, session.cz());
		cheer(session, center);
		DuelSoundPlayer.schedule(14L, () -> DuelSoundPlayer.playAt(center, SoundEvents.VILLAGER_CELEBRATE, 0.12F, 1.1F));
	}

	/** Three voices from three sides of the ring, 0, 3 and 7 ticks apart (at most one cheer per 2 s per session). */
	private static void cheer(DuelPayloads.DuelSessionSync session, Vec3 center) {
		long last = LAST_CHEER.getOrDefault(session.sessionId(), Long.MIN_VALUE / 2);
		if (ticks - last < 40L) {
			return;
		}
		LAST_CHEER.put(session.sessionId(), ticks);
		float radius = session.radius() + 2.5F;
		int[] delays = {0, 3, 7};
		for (int i = 0; i < delays.length; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
			Vec3 at = center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
			float pitch = 0.85F + RANDOM.nextFloat() * 0.3F;
			DuelSoundPlayer.schedule(delays[i], () -> DuelSoundPlayer.playAt(at, SoundEvents.VILLAGER_CELEBRATE, 0.15F, pitch));
		}
	}

	/** The snare roll of the Sword Dance: six strokes, two ticks apart, swelling. */
	private static void snareRoll(Vec3 center) {
		for (int i = 0; i < 6; i++) {
			float volume = 0.35F + i * 0.1F;
			DuelSoundPlayer.schedule(i * 2L, () -> DuelSoundPlayer.playAt(center, SoundEvents.NOTE_BLOCK_SNARE.value(), volume, 1.0F));
		}
	}

	/** A crowd: the setting is on, and the arena seats disciples, the crowd rule is set, or two players are watching. */
	private static boolean crowded(DuelPayloads.DuelSessionSync session) {
		if (!DuelSettings.crowd()) {
			return false;
		}
		if ((session.flags() & (DuelIds.SF_CROWD | DuelIds.SF_SECT_ARENA)) != 0) {
			return true;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return false;
		}
		Vec3 center = new Vec3(session.cx(), session.cy(), session.cz());
		double r = session.radius() + 16.0D;
		int watching = 0;
		for (Player player : level.players()) {
			if (player.getId() != session.aId() && player.getId() != session.bId() && player.position().distanceToSqr(center) <= r * r) {
				watching++;
			}
		}
		return watching >= 2;
	}

	/** True when {@code entity} is an onlooker of {@code session} (not a duellist, within the ring + 16). */
	static boolean watching(DuelPayloads.DuelSessionSync session, Entity entity) {
		Vec3 center = new Vec3(session.cx(), session.cy(), session.cz());
		double r = session.radius() + 16.0D;
		return entity.getId() != session.aId() && entity.getId() != session.bId() && entity.position().distanceToSqr(center) <= r * r;
	}

	private DuelCrowd() {
	}
}
