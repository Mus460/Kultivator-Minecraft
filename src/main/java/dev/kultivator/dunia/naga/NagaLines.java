package dev.kultivator.dunia.naga;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.fx.Fx;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * What the Jiao say (config {@code naga.dialogue}). A tamed Jiao of tier 3-4 cannot speak yet: its owner reads its mood
 * (a snort, a rumble, its eyes); a Naga Sejati or Yinglong (tier 5-6) speaks through divine sense, in quotes. Lines are
 * chosen by the moment (summoned by day, at night, in rain or a thunderstorm, its owner hurt; resting; riding through a
 * storm or above the clouds; a fight won) and by the kind, from {@code kultivator.naga.say.<event>.<beast|sage>.<n>}.
 * Wild Jiao warn players who fly too close in their own words per kind, and fall silent before a cultivator far above
 * them. Every remark is rate-limited per owner and event ({@link Voice#ready}).
 */
final class NagaLines {

	/** Variants per event and group (the lang fragment carries exactly these). */
	static final int GREET = 2;
	static final int REST = 3;
	static final int STORM = 2;
	static final int HIGH = 2;
	static final int HURT = 2;
	static final int VICTORY = 2;
	static final int WARN = 2;
	static final int LEMAH = 2;

	/** The event of a summon greeting: {@code greet_storm|rain|night|day}, or {@code greet_hurt} when the owner is badly hurt. */
	static String greetEvent(ServerLevel level, ServerPlayer owner) {
		if (owner.getHealth() < owner.getMaxHealth() * 0.35F) {
			return "greet_hurt";
		}
		if (level.isThundering()) {
			return "greet_storm";
		}
		if (level.isRainingAt(owner.blockPosition())) {
			return "greet_rain";
		}
		return level.isDarkOutside() ? "greet_night" : "greet_day";
	}

	/**
	 * The tamed Jiao's remark on {@code event} to its owner, at most once per {@code gapTicks} for that event. Tier 5+
	 * speaks ("sage"), below it the owner reads its mood ("beast").
	 */
	static void tamed(ServerPlayer owner, NagaLiarEntity dragon, String event, int variants, int gapTicks) {
		tamed(owner, dragon, event, variants, gapTicks, false);
	}

	/** {@link #tamed}; {@code force} (the test command) skips the rate limit. */
	static void tamed(ServerPlayer owner, NagaLiarEntity dragon, String event, int variants, int gapTicks, boolean force) {
		if (!Naga.config().dialogue || !force && !Voice.ready(owner.getUUID(), "naga_say:" + event, gapTicks)) {
			return;
		}
		boolean sage = dragon.tier() >= 5;
		int n = owner.getRandom().nextInt(Math.max(1, variants));
		Component line = Component.translatable("kultivator.naga.say." + event + "." + (sage ? "sage" : "beast") + "." + n);
		MutableComponent text = Component.translatable(sage ? "kultivator.naga.say.format.sage" : "kultivator.naga.say.format.beast",
				dragon.getDisplayName(), line);
		Msg.chat(owner, text.withColor(dragon.kind().glow).withStyle(ChatFormatting.ITALIC));
	}

	/** A wild Jiao's warning to a player flying too close (halfway through the provocation). */
	static Component warn(NagaKind kind, ServerPlayer player) {
		int n = player.getRandom().nextInt(WARN);
		return Component.translatable("kultivator.naga.warn." + kind.id() + "." + n, kind.displayName());
	}

	/** The Lemah line of a wild Jiao of {@code kind}. */
	static Component lemah(NagaKind kind, ServerPlayer player) {
		int n = player.getRandom().nextInt(LEMAH);
		return Component.translatable("kultivator.naga.lemah." + kind.id() + "." + n, kind.displayName());
	}

	/** Variants of the tamed Jiao's sky remarks ({@code sky_meteor|sky_eclipse|sky_moons}). */
	static final int SKY = 2;
	/** Beasts farther than this from their owner do not answer the sky. */
	private static final double SKY_NOTICE = 48.0D;

	/**
	 * Module L's sky event {@code kind} began over {@code level}: every owner's summoned Jiao remarks on it, and their
	 * tier 4+ beasts within 48 blocks answer: a wolf howls at the blood eclipse (the Tiangou of the legend swallows the
	 * moon) and at the twin moons, a fox's tails shine under the twin moons and the meteor shower. Sounds, a ring of light
	 * and a chat line; nothing else changes. Once per owner and event per 2 minutes ({@link Voice#ready}).
	 */
	static void skyEvent(ServerLevel level, SkyKind kind, boolean started) {
		skyEvent(level, kind, started, false);
	}

	/** {@link #skyEvent}; {@code force} ({@code uji naga langit}) skips the rate limits. */
	static void skyEvent(ServerLevel level, SkyKind kind, boolean started, boolean force) {
		if (!started || !Naga.config().dialogue) {
			return;
		}
		String event = switch (kind) {
			case METEOR_SHOWER -> "sky_meteor";
			case BLOOD_ECLIPSE -> "sky_eclipse";
			case TWIN_MOONS -> "sky_moons";
		};
		for (ServerPlayer owner : List.copyOf(level.players())) {
			NagaLiarEntity dragon = TamedJiao.active(owner);
			if (dragon != null && dragon.level() == level) {
				tamed(owner, dragon, event, SKY, 2400, force);
			}
			if (!force && !Voice.ready(owner.getUUID(), "naga_sky_beast:" + event, 2400)) {
				continue;
			}
			for (Mob beast : SpiritBeasts.loadedBeasts(owner.getUUID())) {
				BeastData data = SpiritBeasts.data(beast);
				if (data == null || beast.level() != level || !beast.isAlive() || data.tier() < 4
						|| beast.distanceToSqr(owner) > SKY_NOTICE * SKY_NOTICE) {
					continue;
				}
				String line;
				if (data.isWolf() && kind != SkyKind.METEOR_SHOWER) {
					line = kind == SkyKind.BLOOD_ECLIPSE ? data.tier() >= 5 ? "wolf.eclipse_tiangou" : "wolf.eclipse" : "wolf.moons";
					beast.playAmbientSound();
					level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F,
							0.5F);
				} else if (data.isFox() && kind != SkyKind.BLOOD_ECLIPSE) {
					line = kind == SkyKind.TWIN_MOONS ? "fox.moons" : "fox.meteor";
					level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.4F);
				} else {
					continue;
				}
				int rgb = SpiritBeasts.tierColor(data.kind(), data.tier());
				Fx.ring(level, beast.position().add(0.0D, 0.2D, 0.0D), rgb, 2.5F);
				Fx.burst(level, beast.position().add(0.0D, 1.0D, 0.0D), rgb, 12, 1.0F);
				Msg.chat(owner, Component.translatable("kultivator.naga.sky." + line, beast.getDisplayName()).withColor(rgb)
						.withStyle(ChatFormatting.ITALIC));
			}
		}
	}

	/** Owner → the realm ordinal their summoned Jiao last saw (a breakthrough while it is out is celebrated). */
	private static final java.util.Map<java.util.UUID, Integer> REALM_SEEN = new java.util.HashMap<>();

	static void clear() {
		REALM_SEEN.clear();
	}

	/**
	 * Once a second while summoned: when its owner's realm rose since it last looked, the Jiao celebrates (a roar to the
	 * sky, a ring of its colour, a remark "realm_up"). The first look only remembers the realm.
	 */
	static void realmCheck(ServerLevel level, ServerPlayer owner, NagaLiarEntity dragon) {
		int realm = Cultivators.get(owner).realm().ordinal();
		Integer before = REALM_SEEN.put(owner.getUUID(), realm);
		if (before == null || realm <= before) {
			return;
		}
		level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 1.2F, 1.5F);
		Fx.ring(level, dragon.position(), dragon.kind().glow, 5.0F);
		Fx.burst(level, dragon.position(), dragon.kind().glow, 24, 2.5F);
		tamed(owner, dragon, "realm_up", REALM_UP, 1, false);
	}

	/** Variants of the realm remark. */
	static final int REALM_UP = 2;

	/** True when a wild Jiao is in awe of {@code player} and will not be provoked (Immortal Ascension and above). */
	static boolean awes(ServerPlayer player) {
		return Cultivators.get(player).realm().atLeast(Realm.IMMORTAL_ASCENSION);
	}

	private NagaLines() {
	}
}
