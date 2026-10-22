package dev.kultivator.dunia.naga;

import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The spoken lines of module B through the existing {@code voice/**} API (DESIGN 34.8). Phase 34 has its own Japanese
 * lines ({@code naga.subdued}, {@code naga.tribulation}, {@code naga.evolved}, {@code naga.trib_failed},
 * {@code naga.jiao_roar}); each call names the reused line of earlier phases as its fallback, so a build whose
 * {@code sounds.json} / {@code VoiceEvents} do not carry the new key yet plays the old line instead of nothing. The
 * {@code Voice} helpers keep their rate limits (one line per speaker at a time, Heaven's queue per player).
 */
final class NagaVoice {

	static final String SUBDUED = "naga.subdued";
	static final String TRIBULATION = "naga.tribulation";
	static final String EVOLVED = "naga.evolved";
	static final String TRIB_FAILED = "naga.trib_failed";
	static final String JIAO_ROAR = "naga.jiao_roar";

	/** {@code wanted} when it is a registered voice event, else {@code fallback}. */
	static String pick(String wanted, String fallback) {
		return VoiceEvents.exists(wanted) ? wanted : fallback;
	}

	/**
	 * Heaven speaks to every player within {@code radius} of {@code pos}: the new line captioned "Langit" when it exists,
	 * else the reused heaven line ({@code fallback}, e.g. {@code heaven.witness}) with its own queue and echo.
	 */
	static void heaven(ServerLevel level, Vec3 pos, String wanted, String fallback, double radius) {
		if (!VoiceEvents.exists(wanted)) {
			Voice.heavenNear(level, pos, fallback, radius);
			return;
		}
		double r2 = radius * radius;
		Component speaker = Component.translatable("kultivator.naga.voice.heaven");
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (player.position().distanceToSqr(pos) <= r2) {
				Voice.inner(player, wanted, speaker);
			}
		}
	}

	/** A big speaker's line heard by everyone within {@code radius} (the Jiao's roar), the new key when it exists. */
	static void boom(Entity speaker, String wanted, String fallback, double radius) {
		Voice.boom(speaker, pick(wanted, fallback), radius);
	}

	private NagaVoice() {
	}
}
