package dev.kultivator.dunia.dimensi.bawah;

import dev.kultivator.Kultivator;
import dev.kultivator.alam.Alam;
import dev.kultivator.cultivation.Breakthrough;
import dev.kultivator.cultivation.Meditation;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.dunia.DuniaEvents;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SkyKind;
import dev.kultivator.dunia.api.SoulService;
import dev.kultivator.dunia.dimensi.Dimensi;
import dev.kultivator.dunia.dimensi.DimensiConfig;
import dev.kultivator.dunia.dimensi.DimensiData;
import dev.kultivator.dunia.dimensi.DimensiItems;
import dev.kultivator.dunia.dimensi.DimensiLevels;
import dev.kultivator.dunia.dimensi.DimensiNet;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.DeathGuard;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Perjalanan Arwah, the soul journey into Dunia Bawah (DESIGN 34.5.13). Journey to the West ch. 3 and 10-11 (Sun Wukong
 * and Emperor Taizong's souls led before the Yama Kings), Liaozhai "Xi Fangping" (a soul goes down to plead its father's
 * case).
 * <ol>
 * <li>Entry (Nascent Soul+, {@code dimensi.minRealm}): at night, sneak 3 s at a Kuil Gerbang Hantu's incense altar while
 * holding a Dupa Arwah (consumed); during L's Gerhana Darah no incense is needed. {@code uji bawah masuk} skips it.</li>
 * <li>The body stays where the player knelt as a vanilla {@link Mannequin} with the player's profile, crouching,
 * immovable, nameplated, D's {@link #BODY_TAG}; its damage is always cancelled, and a hit pulls the soul back.</li>
 * <li>The soul arrives on Teras Wangxiang (x 0, y 52, z 0 of {@code kultivator:dunia_bawah}) with 20 minutes per incense
 * ({@code incenseMinutes}). No hunger loss; the client paints a pale soul vignette and a timer.</li>
 * <li>The way back: the timer, the Jalan Pulang gate on the terrace, {@code uji bawah pulang}, or a lethal hit: the
 * {@code REALM} death guard (before the Totem, never against {@code /kill}) returns the soul to its body at 1 HP with
 * <b>Luka Jiwa</b> (Weakness I and −20 % Qi regeneration for 10 minutes). Never a real death, no item is lost.</li>
 * <li>Logging out as a soul: on the next login the player wakes in the body (the mannequin is removed then, or when
 * its chunk next loads). No chunk is kept loaded for the body: it is only a picture; the return point lives in
 * {@code extra.bawah.ret}.</li>
 * </ol>
 * Nothing here changes a block.
 */
public final class SoulJourney {

	public static final String BODY_TAG = "kultivator.tubuh_arwah";
	public static final String OWNER_TAG_PREFIX = "kultivator.tubuh.";
	public static final int SNEAK_TICKS = 60;
	public static final long LUKA_TICKS = 20L * 60L * 10L;
	public static final double LUKA_QI_FACTOR = 0.8D;
	public static final double LENTERA_BONUS = 1.10D;

	public static final int BY_TIMER = 0;
	public static final int BY_GATE = 1;
	public static final int BY_DEATH = 2;
	public static final int BY_BODY = 3;
	public static final int BY_COMMAND = 4;
	public static final int BY_LOGIN = 5;

	private static final Map<UUID, Integer> SNEAKING = new HashMap<>();
	private static final Set<UUID> MOVING = new HashSet<>();
	/** Bodies whose souls came home while their chunk was unloaded: removed when it loads. */
	private static final Set<UUID> STALE_BODIES = new HashSet<>();

	static void init() {
		DeathGuard.register(DeathGuard.Stage.REALM, SoulJourney::preventDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof Mannequin body && body.entityTags().contains(BODY_TAG)) {
				bodyHit(body, source);
				return false;
			}
			return true;
		});
		ServerPlayerEvents.JOIN.register(player -> Dimensi.schedule(10, () -> joinCheck(player)));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			if (isSoul(player) && !MOVING.contains(player.getUUID()) && !DimensiLevels.isBawah(destination)) {
				// left Dunia Bawah some other way (an op's teleport): the journey ends where the player now stands
				endWithoutTeleport(player);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mannequin body && body.entityTags().contains(BODY_TAG) && level instanceof ServerLevel serverLevel) {
				Dimensi.schedule(1, () -> checkBody(serverLevel, body));
			}
		});
		Qi.addRegenFactor(player -> lukaLeft(player) > 0L ? LUKA_QI_FACTOR : 1.0D);
		Meditation.addGatheringProvider(SoulJourney::lanternFactor);
		DuniaApi.setSouls(new SoulService() {
			@Override
			public boolean isSoul(ServerPlayer player) {
				return SoulJourney.isSoul(player);
			}
		});
	}

	static void clear() {
		SNEAKING.clear();
		MOVING.clear();
		STALE_BODIES.clear();
	}

	// ================================================================== state

	static CompoundTag data(ServerPlayer player) {
		return DimensiData.extra(player, DimensiData.BAWAH);
	}

	/** True while the player's soul walks Dunia Bawah (their body is a mannequin). */
	public static boolean isSoul(ServerPlayer player) {
		return !data(player).getStringOr("body", "").isEmpty();
	}

	/** Ticks of soul time left (0 when not a soul). */
	public static long soulLeft(ServerPlayer player) {
		if (!isSoul(player)) {
			return 0L;
		}
		return Math.max(0L, data(player).getLongOr("until", 0L) - DimensiData.now(player.level().getServer()));
	}

	/** Ticks of Luka Jiwa left. */
	public static long lukaLeft(ServerPlayer player) {
		return Math.max(0L, data(player).getLongOr("luka", 0L) - DimensiData.now(player.level().getServer()));
	}

	private static double lanternFactor(ServerPlayer player) {
		Realm realm = Cultivators.get(player).realm();
		if (!realm.atLeast(Realm.NASCENT_SOUL) || realm.atLeast(Realm.IMMORTAL_ASCENSION)) {
			return 1.0D;
		}
		if (!DimensiData.night(player.level().getServer()) || !DimensiData.carries(player, DimensiItems.LENTERA_ARWAH)) {
			return 1.0D;
		}
		return LENTERA_BONUS;
	}

	// ================================================================== entry

	/** Why the player may not leave the body now, or null. */
	public static @Nullable Component blocker(ServerPlayer player, boolean free) {
		DimensiConfig.Settings cfg = DimensiConfig.get();
		if (!DuniaSpine.enabled() || !cfg.underworld) {
			return Component.translatable("kultivator.dimensi.why.closed");
		}
		if (DimensiLevels.bawah(player.level().getServer()) == null) {
			return Component.translatable("kultivator.dimensi.why.missing");
		}
		if (isSoul(player)) {
			return Component.translatable("kultivator.dimensi.why.already");
		}
		CultivationData data = Cultivators.get(player);
		Realm min = DimensiConfig.realm(cfg.minRealm, Realm.NASCENT_SOUL);
		if (!free && !data.realm().atLeast(min)) {
			return Component.translatable("kultivator.dimensi.why.realm", min.displayName());
		}
		if (DimensiLevels.isBawah(player.level()) || DimensiLevels.isLaut(player.level()) || Alam.isRealmLevel(player.level())) {
			return Component.translatable("kultivator.dimensi.why.here");
		}
		if (data.breakingThrough() || Breakthrough.isBusy(player) || Tribulation.isActive(player) || DuelRules.inDuel(player)
				|| Alam.inRun(player) || player.isPassenger() || data.flyingSword() || player.isFallFlying()) {
			return Component.translatable("kultivator.dimensi.why.busy");
		}
		if (!free && Dimensi.recentlyHurt(player)) {
			return Component.translatable("kultivator.dimensi.why.hurt");
		}
		return null;
	}

	/** True when the blood eclipse holds the player's level (the gate needs no incense then). */
	public static boolean bloodEclipse(ServerLevel level) {
		try {
			return DuniaApi.sky().active(level) == SkyKind.BLOOD_ECLIPSE;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	/** The altar anchor's pass (every 20 ticks): sneaking players kneel; 3 s later the soul leaves. */
	static void altarPass(ServerLevel level, Vec3 altar, java.util.List<ServerPlayer> within) {
		boolean eclipse = bloodEclipse(level);
		for (ServerPlayer player : within) {
			if (player.position().distanceToSqr(altar) > 3.0D * 3.0D || !player.isShiftKeyDown()) {
				SNEAKING.remove(player.getUUID());
				continue;
			}
			boolean incense = DimensiData.holds(player, DimensiItems.DUPA_ARWAH);
			if (!eclipse && !incense) {
				Msg.actionBar(player, Component.translatable("kultivator.dimensi.altar.need_incense").withColor(0xB9A3D9));
				continue;
			}
			if (!eclipse && !DimensiData.night(level.getServer())) {
				Msg.actionBar(player, Component.translatable("kultivator.dimensi.altar.need_night").withColor(0xB9A3D9));
				continue;
			}
			Component why = blocker(player, false);
			if (why != null) {
				Msg.actionBar(player, why.copy().withStyle(ChatFormatting.GRAY));
				SNEAKING.remove(player.getUUID());
				continue;
			}
			int held = SNEAKING.merge(player.getUUID(), 20, Integer::sum);
			if (held < SNEAK_TICKS) {
				Msg.actionBar(player, Component.translatable("kultivator.dimensi.altar.kneel", (SNEAK_TICKS - held) / 20 + 1).withColor(0xC9B8FF));
				DimensiNet.fx(level, DimensiNet.FX_SOUL_LEAVE, player.position(), held, 32.0D);
				continue;
			}
			SNEAKING.remove(player.getUUID());
			if (!eclipse && !DimensiData.takeOne(player, DimensiItems.DUPA_ARWAH)) {
				continue;
			}
			enter(player, false);
		}
	}

	/** Leaves the body for Dunia Bawah ({@code free}: the test command, no gates). True when the soul arrived. */
	public static boolean enter(ServerPlayer player, boolean free) {
		Component why = blocker(player, free);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.GRAY));
			return false;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel target = DimensiLevels.bawah(server);
		if (target == null) {
			return false;
		}
		ServerLevel home = player.level();
		Mannequin body = makeBody(home, player);
		if (body == null) {
			Msg.chat(player, Component.translatable("kultivator.dimensi.why.body").withStyle(ChatFormatting.GRAY));
			return false;
		}
		CompoundTag data = data(player);
		DimensiLevels.saveReturn(player, data, "ret");
		data.putString("body", body.getUUID().toString());
		data.putLong("until", DimensiData.now(server) + 20L * 60L * DimensiConfig.get().incenseMinutes);
		DimensiData.dirty(player);
		DimensiNet.fx(home, DimensiNet.FX_SOUL_LEAVE, player.position(), SNEAK_TICKS, 48.0D);
		home.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.4F, 0.6F);
		MOVING.add(player.getUUID());
		ServerPlayer moved;
		try {
			moved = DimensiLevels.send(player, target, DimensiLevels.BAWAH_ARRIVAL, 180.0F, 0.0F);
		} finally {
			MOVING.remove(player.getUUID());
		}
		ServerPlayer who = moved != null ? moved : player;
		who.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, true, false));
		DimensiNet.fx(target, DimensiNet.FX_ARRIVE_BAWAH, DimensiLevels.BAWAH_ARRIVAL, 0, 64.0D);
		sendState(who);
		Msg.title(who, Component.translatable("kultivator.dimensi.bawah.title").withColor(0xC9B8FF),
				Component.translatable("kultivator.dimensi.bawah.subtitle", DimensiConfig.get().incenseMinutes).withColor(0x9A8FB0), 10, 60, 20);
		Voice.heaven(who, "heaven.secret_realm");
		DuniaEvents.fireSoulJourney(who, true);
		return true;
	}

	/** The body: a crouching, immovable vanilla Mannequin with the player's skin and a name plate. */
	private static @Nullable Mannequin makeBody(ServerLevel level, ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("immovable", true);
		tag.putString("pose", "crouching");
		tag.putBoolean("hide_description", true);
		Entity made;
		try {
			made = EntityType.loadEntityRecursive(EntityTypes.MANNEQUIN, tag, level, EntitySpawnReason.EVENT, entity -> entity);
		} catch (RuntimeException exception) {
			Kultivator.LOGGER.warn("Kultivator: the soul's body could not be made", exception);
			return null;
		}
		if (!(made instanceof Mannequin body)) {
			return null;
		}
		body.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
		body.setYHeadRot(player.getYRot());
		body.setYBodyRot(player.getYRot());
		body.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
		body.addTag(BODY_TAG);
		body.addTag(OWNER_TAG_PREFIX + player.getUUID());
		body.setCustomName(Component.translatable("kultivator.dimensi.tubuh", player.getName()).withColor(0xB9A3D9));
		body.setCustomNameVisible(true);
		body.setSilent(true);
		if (!level.addFreshEntity(body)) {
			return null;
		}
		return body;
	}

	// ================================================================== the way back

	/** The soul returns to its body for {@code reason} ({@code BY_*}). */
	public static void returnToBody(ServerPlayer player, int reason) {
		if (!isSoul(player)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		CompoundTag data = data(player);
		TeleportTransition back = DimensiLevels.savedReturn(server, data, "ret", DimensiLevels.BAWAH);
		UUID body = parseUuid(data.getStringOr("body", ""));
		data.remove("body");
		data.remove("ret");
		data.remove("until");
		if (reason == BY_DEATH) {
			data.putLong("luka", DimensiData.now(server) + LUKA_TICKS);
		}
		DimensiData.dirty(player);
		ServerPlayer who = player;
		if (DimensiLevels.isBawah(player.level())) {
			MOVING.add(player.getUUID());
			try {
				ServerPlayer moved = DimensiLevels.sendHome(player, back);
				if (moved != null) {
					who = moved;
				}
			} finally {
				MOVING.remove(player.getUUID());
			}
		}
		removeBody(server, back, body);
		who.setDeltaMovement(Vec3.ZERO);
		who.fallDistance = 0.0D;
		if (reason == BY_DEATH) {
			who.setHealth(1.0F);
			who.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) LUKA_TICKS, 0));
			Msg.chat(who, Component.translatable("kultivator.dimensi.pulang.luka").withColor(0xD08080));
		} else {
			Msg.chat(who, Component.translatable("kultivator.dimensi.pulang." + reasonId(reason)).withColor(0xB9A3D9));
		}
		ServerLevel level = who.level();
		DimensiNet.fx(level, DimensiNet.FX_SOUL_RETURN, who.position(), reason, 48.0D);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 1.4F);
		sendState(who);
		DuniaEvents.fireSoulJourney(who, false);
	}

	private static String reasonId(int reason) {
		return switch (reason) {
			case BY_GATE -> "gerbang";
			case BY_BODY -> "tubuh";
			case BY_COMMAND -> "perintah";
			case BY_LOGIN -> "login";
			default -> "waktu";
		};
	}

	/** Ends the journey where the player now stands (they left Dunia Bawah some other way). */
	private static void endWithoutTeleport(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		CompoundTag data = data(player);
		TeleportTransition back = DimensiLevels.savedReturn(server, data, "ret", DimensiLevels.BAWAH);
		UUID body = parseUuid(data.getStringOr("body", ""));
		data.remove("body");
		data.remove("ret");
		data.remove("until");
		DimensiData.dirty(player);
		removeBody(server, back, body);
		sendState(player);
		DuniaEvents.fireSoulJourney(player, false);
	}

	private static void removeBody(MinecraftServer server, @Nullable TeleportTransition back, @Nullable UUID body) {
		if (body == null) {
			return;
		}
		if (back != null) {
			Entity entity = back.newLevel().getEntity(body);
			if (entity != null) {
				entity.discard();
				return;
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(body);
			if (entity != null) {
				entity.discard();
				return;
			}
		}
		STALE_BODIES.add(body);
	}

	/** A body loaded: removed when its soul already came home (or no soul owns it any more). */
	private static void checkBody(ServerLevel level, Mannequin body) {
		if (body.isRemoved()) {
			return;
		}
		if (STALE_BODIES.remove(body.getUUID())) {
			body.discard();
			return;
		}
		UUID owner = null;
		for (String tag : body.entityTags()) {
			if (tag.startsWith(OWNER_TAG_PREFIX)) {
				owner = parseUuid(tag.substring(OWNER_TAG_PREFIX.length()));
			}
		}
		if (owner == null) {
			body.discard();
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (player != null && !body.getUUID().toString().equals(data(player).getStringOr("body", ""))) {
			body.discard();
		}
	}

	/** A hit on the body pulls the soul back (the body itself never takes damage). */
	private static void bodyHit(Mannequin body, DamageSource source) {
		if (body.level().isClientSide() || source.getEntity() == null) {
			return;
		}
		for (String tag : body.entityTags()) {
			if (tag.startsWith(OWNER_TAG_PREFIX)) {
				UUID owner = parseUuid(tag.substring(OWNER_TAG_PREFIX.length()));
				ServerPlayer player = owner == null ? null : body.level().getServer().getPlayerList().getPlayer(owner);
				if (player != null && isSoul(player) && body.getUUID().toString().equals(data(player).getStringOr("body", ""))) {
					Dimensi.schedule(1, () -> returnToBody(player, BY_BODY));
				}
			}
		}
	}

	/** The REALM death guard: a lethal hit on a soul returns it to its body at 1 HP with Luka Jiwa. */
	private static boolean preventDeath(ServerPlayer player, DamageSource source, float amount) {
		if (!isSoul(player)) {
			return false;
		}
		player.setHealth(1.0F);
		player.setRemainingFireTicks(0);
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		Dimensi.schedule(1, () -> returnToBody(player, BY_DEATH));
		return true;
	}

	/** On login: a soul whose player logged out wakes in the body. */
	private static void joinCheck(ServerPlayer player) {
		if (player.isRemoved()) {
			return;
		}
		if (isSoul(player)) {
			if (DimensiLevels.isBawah(player.level())) {
				returnToBody(player, BY_LOGIN);
			} else {
				endWithoutTeleport(player);
			}
		} else {
			sendState(player);
		}
	}

	// ================================================================== tick

	static void tick(MinecraftServer server, long ticks) {
		if (ticks % 20L != 0L) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isSoul(player)) {
				continue;
			}
			if (!DimensiLevels.isBawah(player.level())) {
				endWithoutTeleport(player);
				continue;
			}
			// a soul does not hunger
			player.getFoodData().setSaturation(Math.max(player.getFoodData().getSaturationLevel(), 5.0F));
			long left = soulLeft(player);
			if (left <= 0L) {
				returnToBody(player, BY_TIMER);
				continue;
			}
			if (left <= 20L * 60L && left > 20L * 59L) {
				Msg.chat(player, Component.translatable("kultivator.dimensi.waktu.semenit").withColor(0xE0B070));
			}
			if (ticks % 100L == 0L) {
				sendState(player);
			}
		}
	}

	/** {@code State}: soul flag, ticks left, the body's place (the homesick compass of Teras Wangxiang), Luka Jiwa. */
	public static void sendState(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		boolean soul = isSoul(player);
		tag.putBoolean("soul", soul);
		tag.putLong("left", soulLeft(player));
		tag.putLong("luka", lukaLeft(player));
		if (soul) {
			CompoundTag ret = data(player).getCompoundOrEmpty("ret");
			tag.putDouble("bx", ret.getDoubleOr("x", 0.0D));
			tag.putDouble("bz", ret.getDoubleOr("z", 0.0D));
			tag.putString("bdim", ret.getStringOr("dim", ""));
		}
		dev.kultivator.dunia.dimensi.laut.SeaGate.writeState(player, tag);
		DimensiNet.send(player, new DimensiNet.State(tag));
	}

	/** Giok Arwah: mends Luka Jiwa and gives back a quarter of the Qi. */
	public static boolean eatJade(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		boolean luka = lukaLeft(player) > 0L;
		data(player).remove("luka");
		player.removeEffect(MobEffects.WEAKNESS);
		double max = Qi.max(data);
		data.setQi(Math.min(max, data.qi() + max * 0.25D));
		Cultivators.dirty(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.7F);
		Msg.actionBar(player, Component.translatable(luka ? "kultivator.dimensi.giok.luka" : "kultivator.dimensi.giok.qi").withColor(0xB9E0D0));
		sendState(player);
		return true;
	}

	/** Grants the Naihe advancement to a soul that crosses the bridge. */
	static void crossedNaihe(ServerPlayer player) {
		Advancements.grant(player, "dunia/naihe_crossed");
	}

	static @Nullable UUID parseUuid(String text) {
		try {
			return text == null || text.isEmpty() ? null : UUID.fromString(text);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private SoulJourney() {
	}
}
