package dev.kultivator.dunia.dimensi.laut;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.dunia.DuniaAnchorEntity;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.dimensi.Dimensi;
import dev.kultivator.dunia.dimensi.DimensiConfig;
import dev.kultivator.dunia.dimensi.DimensiData;
import dev.kultivator.dunia.dimensi.DimensiItems;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import dev.kultivator.dunia.dimensi.DimensiNet;
import dev.kultivator.dunia.dimensi.bawah.GhostQuests;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The ways into and out of Laut Tanpa Batas (DESIGN 34.5.14). Liezi, Tang Wen: the five immortal isles float in the
 * eastern sea; Journey to the West ch. 3: Sun Wukong goes down to the Dragon King of the Eastern Sea.
 * <ul>
 * <li><b>Pusaran Laut</b> (an overworld deep-ocean sea gate, {@code laut/pusaran} anchor): a Golden Core cultivator who
 * swims into the whirlpool holding a Mutiara Penunjuk Laut is drawn under and comes up at the arrival dock.</li>
 * <li><b>The ship</b>: module T's route {@code laut} ({@code RouteService.registerDestination}, registered once the
 * server starts so T's service is in place); the captain and everyone aboard arrive at the dock.</li>
 * <li><b>Gerbang Pulang</b> (every island, the dock) and {@code uji laut pulang}: back to where one left the
 * overworld ({@code extra.laut.ret}), else the respawn point.</li>
 * </ul>
 * The Mutiara Penunjuk Laut points to the nearest sea gate (or, at sea, the nearest spirit island) through the spine's
 * {@code DuniaSites.locateFor} (one search per player per 30 s). The Mutiara Penghindar Air, carried, gives Water
 * Breathing in this dimension's water.
 */
public final class SeaGate {

	public static final String ROUTE = "laut";
	public static final TagKey<Structure> GATES = TagKey.create(Registries.STRUCTURE, Kultivator.id("laut/pusaran"));
	public static final TagKey<Structure> ISLANDS = TagKey.create(Registries.STRUCTURE, Kultivator.id("laut/pulau"));
	public static final TagKey<Structure> PALACES = TagKey.create(Registries.STRUCTURE, Kultivator.id("laut/istana"));
	public static final TagKey<Structure> PENGLAI = TagKey.create(Registries.STRUCTURE, Kultivator.id("laut/penglai"));
	public static final TagKey<Structure> SHRINES = TagKey.create(Registries.STRUCTURE, Kultivator.id("bawah/kuil"));
	/** One tag per island kind ({@code laut/pulau_herba} ...), the four structures of the {@code laut/pulau_roh} set. */
	public static final TagKey<Structure> HERB_ISLANDS = TagKey.create(Registries.STRUCTURE, Kultivator.id("laut/pulau_herba"));
	public static final TagKey<Structure> HERMIT_ISLANDS = TagKey.create(Registries.STRUCTURE, Kultivator.id("laut/pulau_pertapa"));
	public static final TagKey<Structure> MARKET_ISLANDS = TagKey.create(Registries.STRUCTURE, Kultivator.id("laut/pulau_pasar"));
	public static final TagKey<Structure> REEF_ISLANDS = TagKey.create(Registries.STRUCTURE, Kultivator.id("laut/pulau_karang"));
	public static final double WHIRL_RADIUS = 5.0D;

	private static boolean routed;

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> registerRoute());
		DuniaEvents.VOYAGE.register((captain, destination, arrived) -> {
			if (arrived && ROUTE.equals(destination)) {
				Advancements.grant(captain, "dunia/sea_arrived");
			}
		});
	}

	private static void registerRoute() {
		if (routed) {
			return;
		}
		routed = true;
		try {
			DuniaApi.routes().registerDestination(ROUTE, Component.translatable("kultivator.dimensi.laut.nama"),
					(captain, level) -> level.dimension() == Level.OVERWORLD && blocker(captain) == null,
					SeaGate::arriveByShip);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: the sea route could not be registered", exception);
		}
	}

	static CompoundTag data(ServerPlayer player) {
		return DimensiData.extra(player, DimensiData.LAUT);
	}

	/** Why the player may not cross into the sea, or null. */
	public static @Nullable Component blocker(ServerPlayer player) {
		DimensiConfig.Settings cfg = DimensiConfig.get();
		if (!DuniaSpine.enabled() || !cfg.sea) {
			return Component.translatable("kultivator.dimensi.why.closed");
		}
		if (DimensiLevels.laut(player.level().getServer()) == null) {
			return Component.translatable("kultivator.dimensi.why.missing");
		}
		Realm min = DimensiConfig.realm(cfg.seaMinRealm, Realm.GOLDEN_CORE);
		if (!Cultivators.get(player).realm().atLeast(min)) {
			return Component.translatable("kultivator.dimensi.why.realm", min.displayName());
		}
		return null;
	}

	/** Crosses into the sea (the return point is where the player stands now, unless already at sea). */
	public static boolean enter(ServerPlayer player, boolean free) {
		if (!free) {
			Component why = blocker(player);
			if (why != null) {
				Msg.chat(player, why.copy().withStyle(ChatFormatting.GRAY));
				return false;
			}
		}
		ServerLevel sea = DimensiLevels.laut(player.level().getServer());
		if (sea == null) {
			return false;
		}
		if (!DimensiLevels.isLaut(player.level())) {
			DimensiLevels.saveReturn(player, data(player), "ret");
			DimensiData.dirty(player);
		}
		ServerPlayer moved = DimensiLevels.send(player, sea, DimensiLevels.LAUT_ARRIVAL, 180.0F, 0.0F);
		ServerPlayer who = moved != null ? moved : player;
		arrived(who);
		return true;
	}

	private static void arrived(ServerPlayer player) {
		ServerLevel level = player.level();
		DimensiNet.fx(level, DimensiNet.FX_ARRIVE_LAUT, player.position(), 0, 64.0D);
		Msg.title(player, Component.translatable("kultivator.dimensi.laut.title").withColor(0x7FE0F0),
				Component.translatable("kultivator.dimensi.laut.subtitle").withColor(0xB0E8F0), 10, 60, 20);
		Advancements.grant(player, "dunia/sea_arrived");
		Voice.heaven(player, "heaven.secret_realm");
		dev.kultivator.dunia.dimensi.bawah.SoulJourney.sendState(player);
	}

	/** The ship's arrival: the captain and every listed entity come up at the dock. */
	private static void arriveByShip(ServerPlayer captain, List<Entity> aboard) {
		ServerLevel sea = DimensiLevels.laut(captain.level().getServer());
		if (sea == null) {
			return;
		}
		for (Entity entity : aboard) {
			if (entity instanceof ServerPlayer passenger && !DimensiLevels.isLaut(passenger.level())) {
				DimensiLevels.saveReturn(passenger, data(passenger), "ret");
				DimensiData.dirty(passenger);
			}
		}
		if (!DimensiLevels.isLaut(captain.level())) {
			DimensiLevels.saveReturn(captain, data(captain), "ret");
			DimensiData.dirty(captain);
		}
		int i = 0;
		for (Entity entity : aboard) {
			if (entity == captain) {
				continue;
			}
			Vec3 at = DimensiLevels.LAUT_ARRIVAL.add((i % 3 - 1) * 2.0D, 1.0D, (i / 3 - 1) * 2.0D);
			Entity moved = DimensiLevels.sendEntity(entity, sea, at);
			if (moved instanceof ServerPlayer passenger) {
				arrived(passenger);
			}
			i++;
		}
		ServerPlayer moved = DimensiLevels.send(captain, sea, DimensiLevels.LAUT_ARRIVAL.add(0.0D, 1.0D, 0.0D), captain.getYRot(), 0.0F);
		arrived(moved != null ? moved : captain);
	}

	/** Back from the sea: to {@code extra.laut.ret}, else the respawn point. */
	public static void leave(ServerPlayer player) {
		CompoundTag data = data(player);
		var back = DimensiLevels.savedReturn(player.level().getServer(), data, "ret", DimensiLevels.LAUT);
		data.remove("ret");
		DimensiData.dirty(player);
		ServerPlayer moved = DimensiLevels.sendHome(player, back);
		ServerPlayer who = moved != null ? moved : player;
		Msg.chat(who, Component.translatable("kultivator.dimensi.laut.pulang").withColor(0x7FE0F0));
		who.level().playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	// ================================================================== the whirlpool

	/** The y of the water surface above an overworld gate anchor (the whirl mark floats there). */
	public static double surfaceAbove(ServerLevel level, BlockPos at) {
		int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ());
		return Math.max(at.getY() + 2, top) - 0.4D;
	}

	/** The gate anchor's pass: a swimmer in the whirl with the pearl is drawn into the sea. */
	static void whirlPass(ServerLevel level, DuniaAnchorEntity anchor, List<ServerPlayer> within) {
		Vec3 base = anchor.position();
		double top = surfaceAbove(level, anchor.blockPosition());
		for (ServerPlayer player : within) {
			double dx = player.getX() - base.x;
			double dz = player.getZ() - base.z;
			if (dx * dx + dz * dz > WHIRL_RADIUS * WHIRL_RADIUS || player.getY() < base.y - 2.0D || player.getY() > top + 2.5D
					|| !player.isInWater()) {
				continue;
			}
			if (!DimensiData.holds(player, DimensiItems.MUTIARA_PENUNJUK_LAUT)) {
				Msg.actionBar(player, Component.translatable("kultivator.dimensi.pusaran.butuh").withColor(0x7FE0F0));
				continue;
			}
			Component why = blocker(player);
			if (why != null) {
				Msg.actionBar(player, why.copy().withStyle(ChatFormatting.GRAY));
				continue;
			}
			DimensiNet.fx(level, DimensiNet.FX_WHIRL, new Vec3(base.x, top, base.z), 1, 64.0D);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_UNDERWATER_LOOP, SoundSource.AMBIENT, 1.0F, 0.6F);
			enter(player, false);
		}
	}

	/** Mutiara Penunjuk Laut: the nearest sea gate (at sea: the nearest spirit island). */
	public static void pointToGate(ServerPlayer player) {
		boolean atSea = DimensiLevels.isLaut(player.level());
		if (!atSea && player.level().dimension() != Level.OVERWORLD) {
			Msg.actionBar(player, Component.translatable("kultivator.dimensi.pusaran.diam").withStyle(ChatFormatting.GRAY));
			return;
		}
		long cooldown = DuniaSites.locateCooldown(player);
		if (cooldown > 0L) {
			Msg.actionBar(player, Component.translatable("kultivator.dimensi.pusaran.lelah", cooldown / 20L + 1).withStyle(ChatFormatting.GRAY));
			return;
		}
		DuniaSites.Site site = DuniaSites.locateFor(player, atSea ? ISLANDS : GATES, 48, atSea ? "laut/pulau" : "laut/pusaran", 3);
		if (site == null) {
			Msg.actionBar(player, Component.translatable("kultivator.dimensi.pusaran.tiada").withStyle(ChatFormatting.GRAY));
			return;
		}
		DuniaSites.discover(player, site.id());
		double dx = site.pos().getX() + 0.5D - player.getX();
		double dz = site.pos().getZ() + 0.5D - player.getZ();
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		Msg.actionBar(player, Component.translatable(atSea ? "kultivator.dimensi.pusaran.pulau" : "kultivator.dimensi.pusaran.arah",
				Component.translatable(GhostQuests.compass(dx, dz)), distance).withColor(0x7FE0F0));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
		Vec3 dir = new Vec3(dx, 0.0D, dz).normalize();
		DimensiNet.fx(player.level(), DimensiNet.FX_BLESS, player.getEyePosition().add(dir.scale(2.0D)), 1, 24.0D);
	}

	// ================================================================== breathing

	/** Every second: the Mutiara Penghindar Air carried in the sea's water gives Water Breathing. */
	static void breathingTick(ServerPlayer player) {
		if (DimensiLevels.isLaut(player.level()) && player.isInWater() && DimensiData.carries(player, DimensiItems.MUTIARA_PENGHINDAR_AIR)) {
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 300, 0, true, false, true));
		}
	}

	/** The sea part of the client state: at sea, the breathing pearl. */
	public static void writeState(ServerPlayer player, CompoundTag tag) {
		tag.putBoolean("sea", DimensiLevels.isLaut(player.level()));
		tag.putBoolean("breath", DimensiData.carries(player, DimensiItems.MUTIARA_PENGHINDAR_AIR));
	}

	/** The fisherman's news: the structure tag of {@code what} (an island kind, {@code istana}, {@code penglai}). */
	public static TagKey<Structure> newsTag(String what) {
		return switch (what) {
			case "herba" -> HERB_ISLANDS;
			case "pertapa" -> HERMIT_ISLANDS;
			case "pasar" -> MARKET_ISLANDS;
			case "karang" -> REEF_ISLANDS;
			case "istana" -> PALACES;
			case "penglai" -> PENGLAI;
			default -> ISLANDS;
		};
	}

	/** {@code uji laut pusaran cari} / shrine search: the structure tag of {@code what}. */
	public static @Nullable TagKey<Structure> tagOf(String what) {
		return switch (what) {
			case "pusaran" -> GATES;
			case "pulau" -> ISLANDS;
			case "istana" -> PALACES;
			case "penglai" -> PENGLAI;
			case "kuil" -> SHRINES;
			default -> null;
		};
	}

	static void reset() {
		routed = false;
	}

	private SeaGate() {
	}

	/** Server helper for {@link Dimensi}: the server of a player. */
	static MinecraftServer server(ServerPlayer player) {
		return player.level().getServer();
	}
}
