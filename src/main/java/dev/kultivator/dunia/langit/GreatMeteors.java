package dev.kultivator.dunia.langit;

import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.network.Net;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Meteor Agung (DESIGN 34.5.8, detail pass): the one great meteor of a Hujan Meteor Roh night. At a point of the night
 * fixed by the event seed (35-70 % of it) it is sent at a random outdoor player of the dimension and lands 48-140 blocks
 * away (a loaded chunk; a second later again when none was found). It falls for 7 seconds from 150-180 blocks up, so
 * every player within 1 000 blocks hears the announcement, sees it cross the sky (the client draws it sky-projected
 * beyond the tracking range from {@link LangitFxPayload#KIND_GREAT_INCOMING}) and can race to it.
 * <p>
 * The landing is presentation and items only, like every meteor (rule W3): a big flash, a shock that shoves whatever
 * stands within 8 blocks (no damage, no fire, never a block), a crater glow drawn by the client for a minute, 3-5 Batu
 * Meteor Roh and one Inti Meteor Agung, and a live map site of tier 2. One per night: the seed is saved in
 * {@link LangitData} so a restart cannot drop a second one.
 */
final class GreatMeteors {

	/** Where in the night the great one falls: from 35 % of it, spread over another 35 % by the event seed. */
	static final double FROM = 0.35D;
	static final double SPAN = 0.35D;
	static final int NEAR = 48;
	static final int FAR = 140;
	static final double ANNOUNCE_RANGE = 1000.0D;
	static final double SHOCK_RANGE = 8.0D;
	static final int STONES_MIN = 3;
	static final int STONES_MAX = 5;
	/** The live site of a great landing lasts twice a common one (20 minutes). */
	static final int SITE_TICKS = SkyEvents.METEOR_SITE_TICKS * 2;

	/** Every 20 ticks while Hujan Meteor Roh runs in {@code level}. */
	static void tick(ServerLevel level, LangitData.Dim dim, LangitConfig config) {
		if (dim.greatSeed == dim.seed || (!dim.forced && (!config.meteorShower || config.meteorsPerPlayer <= 0))) {
			return;
		}
		long ct = SkyEvents.clock(level);
		double fraction = FROM + ((dim.seed >>> 8) & 255) / 255.0D * SPAN;
		long at = dim.start + (long) ((dim.end - dim.start) * fraction);
		if (ct < at) {
			return;
		}
		ServerPlayer target = pick(level);
		if (target == null) {
			return;
		}
		MeteorRohEntity meteor = SkyEvents.fall(level, target, -1.0D, true);
		if (meteor == null) {
			return;
		}
		dim.greatSeed = dim.seed;
		LangitData.get(level.getServer()).changed();
		announce(level, meteor);
	}

	/** {@code uji langit agung}: a Meteor Agung 40 blocks ahead of {@code player} now (null when no loaded ground). */
	static @Nullable MeteorRohEntity force(ServerLevel level, ServerPlayer player) {
		MeteorRohEntity meteor = SkyEvents.fall(level, player, 40.0D, true);
		if (meteor != null) {
			announce(level, meteor);
		}
		return meteor;
	}

	/** A random outdoor, living, non-spectator player of {@code level}, or null. */
	private static @Nullable ServerPlayer pick(ServerLevel level) {
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.isAlive() && level.canSeeSky(player.blockPosition().above())) {
				candidates.add(player);
			}
		}
		return candidates.isEmpty() ? null : candidates.get(level.getRandom().nextInt(candidates.size()));
	}

	/** The chat line, the voice of Heaven and the sky streak for every player within {@link #ANNOUNCE_RANGE}. */
	private static void announce(ServerLevel level, MeteorRohEntity meteor) {
		BlockPos landing = meteor.landing();
		Vec3 at = Vec3.atBottomCenterOf(landing);
		Vector3fc offset = meteor.offset();
		int[] path = {Math.round(offset.x() * 16.0F), Math.round(offset.y() * 16.0F), Math.round(offset.z() * 16.0F), meteor.fallTicks()};
		LangitFxPayload fx = new LangitFxPayload(LangitFxPayload.KIND_GREAT_INCOMING, at.x, at.y, at.z, meteor.seed(), path);
		for (ServerPlayer player : List.copyOf(level.players())) {
			double distance = TreasureBirths.horizontal(player.position(), at);
			if (distance > ANNOUNCE_RANGE) {
				continue;
			}
			Net.send(player, fx);
			Msg.chat(player, Component.translatable("kultivator.langit.agung.incoming", TreasureBirths.direction(player.position(), at),
					(int) Math.round(distance)).withColor(0xA8F0FF));
			Langit.heaven(player, "langit.meteor_shower", "witness");
		}
		Kultivator.LOGGER.info("A Meteor Agung falls to {} in {}", landing.toShortString(), SkyEvents.key(level));
	}

	/** The great landing: burst, shock, crater glow, the stones and the core, the live site. Nothing in the world changes. */
	static void landed(ServerLevel level, MeteorRohEntity meteor) {
		BlockPos landing = meteor.landing();
		Vec3 at = Vec3.atBottomCenterOf(landing);
		RandomSource random = level.getRandom();
		LangitFxPayload fx = LangitFxPayload.at(LangitFxPayload.KIND_GREAT_IMPACT, at.x, at.y, at.z, meteor.seed());
		for (ServerPlayer viewer : PlayerLookup.around(level, at, 320.0D)) {
			Net.send(viewer, fx);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 4.0F, 0.55F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.AMBIENT, 4.0F, 0.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 2.0F, 0.45F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 1.6F, 0.6F);
		// the shock: a shove outwards, no damage, nothing set alight
		for (Entity entity : level.getEntities((Entity) null, new AABB(at, at).inflate(SHOCK_RANGE), e -> e.isAlive() && e.isPushable())) {
			Vec3 away = entity.position().subtract(at);
			double length = Math.max(0.3D, away.horizontalDistance());
			double strength = 0.9D * (1.0D - Math.min(1.0D, length / SHOCK_RANGE)) + 0.2D;
			entity.push(away.x / length * strength, 0.35D + 0.2D * strength, away.z / length * strength);
		}
		int stones = STONES_MIN + random.nextInt(STONES_MAX - STONES_MIN + 1);
		drop(level, at, new ItemStack(LangitItems.BATU_METEOR_ROH, stones), random);
		drop(level, at, new ItemStack(LangitItems.INTI_METEOR_AGUNG), random);
		DuniaSites.publishLive(level, "langit/meteor", landing, 2, level.getGameTime() + SITE_TICKS);
		for (ServerPlayer player : List.copyOf(level.players())) {
			double distance = TreasureBirths.horizontal(player.position(), at);
			if (distance <= 300.0D && distance > 24.0D) {
				Msg.chat(player, Component.translatable("kultivator.langit.agung.landed", TreasureBirths.direction(player.position(), at),
						(int) Math.round(distance)).withColor(0x8FE6FF));
			}
		}
	}

	private static void drop(ServerLevel level, Vec3 at, ItemStack stack, RandomSource random) {
		ItemEntity item = new ItemEntity(level, at.x, at.y + 0.6D, at.z, stack);
		item.setPickUpDelay(30);
		item.setExtendedLifetime();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		item.setDeltaMovement(Math.cos(angle) * 0.12D, 0.35D, Math.sin(angle) * 0.12D);
		level.addFreshEntity(item);
	}

	private GreatMeteors() {
	}
}
