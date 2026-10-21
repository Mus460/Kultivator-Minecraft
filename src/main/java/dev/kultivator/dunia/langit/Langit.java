package dev.kultivator.dunia.langit;

import dev.kultivator.abadi.Abadi;
import dev.kultivator.cultivation.Qi;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.DuniaConfig;
import dev.kultivator.dunia.DuniaSpine;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.dunia.api.SpiritWeather;
import dev.kultivator.network.Net;
import dev.kultivator.util.Msg;
import dev.kultivator.voice.Voice;
import dev.kultivator.voice.VoiceEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Module L, Langit &amp; Cuaca (DESIGN 34.5.8 Peristiwa Langit Acak, 34.5.9 Pertanda Harta Lahir, 34.5.10 Cuaca Roh;
 * 34.6 L): the init wired by its one line in {@code DuniaContent}, the server tick that drives {@link SkyEvents},
 * {@link TreasureBirths}, {@link SpiritWeathers} and {@link LangitSites}, and the item uses (Batu Meteor Roh, Embun Roh).
 * Implements {@code SkyService} and {@code TreasureService} ({@link LangitService}).
 * <p>
 * Nothing here changes a block, the weather, the time or the tick rate (rule W3); every visual is the client's.
 * Switches: {@code langit.events}, {@code langit.treasureBirth}, {@code langit.weather} and the spine's
 * {@code umum.enabled}.
 */
public final class Langit {

	public static final ResourceKey<Level> ALAM_ABADI = Abadi.IMMORTAL_LEVEL;
	/** Sub-compound of {@code CultivationData.extra()} owned by this module. */
	public static final String EXTRA_KEY = "langit";
	/** Embun Roh: +20 % Qi regeneration for 5 minutes. */
	public static final double DEW_REGEN = 1.2D;
	public static final int DEW_TICKS = 6000;

	private static final Map<String, Long> ONCE = new HashMap<>();
	private static boolean initialised;

	/** From {@code DuniaContent.init()} (both physical sides), before {@code ModItems.init()}. */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		LangitItems.init();
		LangitConfig.get();
		PayloadTypeRegistry.clientboundPlay().register(LangitSkyPayload.TYPE, LangitSkyPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LangitCuacaPayload.TYPE, LangitCuacaPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LangitHartaPayload.TYPE, LangitHartaPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LangitFxPayload.TYPE, LangitFxPayload.CODEC);
		LangitSites.init();
		SealOmens.init();
		Qi.addRegenFactor(Langit::dewFactor);
		LangitService service = new LangitService();
		DuniaApi.setSky(service);
		DuniaApi.setTreasures(service);
		DuniaCommands.test("langit", LangitCommands::langit);
		DuniaCommands.test("harta_lahir", LangitCommands::hartaLahir);
		DuniaCommands.test("cuaca", LangitCommands::cuaca);
		DuniaConfig.onReload(SpiritWeathers::resendAll);

		ServerTickEvents.END_SERVER_TICK.register(Langit::tick);
		ServerEntityEvents.ALLOW_LOAD.register(SkyEvents::onSpawn);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseTaken, taken, blocked) -> {
			if (entity instanceof ServerPlayer player) {
				TreasureBirths.onDamaged(player, taken);
			}
		});
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(SkyEvents::onKill);
		UseItemCallback.EVENT.register(Langit::useItem);
		ServerPlayerEvents.JOIN.register(Langit::syncPlayer);
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> syncPlayer(player));
		ServerPlayerEvents.LEAVE.register(player -> {
			SkyEvents.forget(player);
			SpiritWeathers.forget(player);
			ONCE.keySet().removeIf(key -> key.startsWith(player.getUUID().toString()));
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SkyEvents.clear();
			SpiritWeathers.clear();
			TreasureBirths.clearAll();
			SealOmens.clear();
			ONCE.clear();
		});
	}

	/**
	 * The module's server tick. With the master switch {@code umum.enabled} off the sky still ends a running event and the
	 * weather still sends its clear view (both gate themselves), and live births fade; nothing new starts.
	 */
	private static void tick(MinecraftServer server) {
		SkyEvents.endTick();
		boolean on = DuniaSpine.enabled();
		int tick = server.getTickCount();
		if (tick % 20 == 5) {
			SkyEvents.tick(server);
		}
		if (tick % 40 == 27) {
			SpiritWeathers.tick(server);
		}
		if (on) {
			if (tick % 20 == 13) {
				LangitSites.tick(server);
			}
			if (tick % 40 == 33) {
				SealOmens.tick(server);
			}
			TreasureBirths.tick(server);
		} else if (tick % 100 == 0) {
			TreasureBirths.shutdown(server);
		}
	}

	/** A joining or arriving player learns the sky, the weather and the live treasure of their dimension. */
	private static void syncPlayer(ServerPlayer player) {
		SkyEvents.sync(player);
		SpiritWeathers.resend(player);
		TreasureBirths.sync(player);
	}

	// ================================================================== Batu Meteor Roh

	/** Sneak + use outside meditation: 150 Qi at once (false, nothing used, while meditating or full). */
	static boolean absorbMeteorStone(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) {
			return false;
		}
		CultivationData data = Cultivators.get(player);
		if (data.meditating()) {
			Msg.actionBar(player, Component.translatable("kultivator.langit.meteor.meditating").withColor(0x8FE6FF));
			return false;
		}
		if (data.qi() >= Qi.max(data) - 0.5D) {
			Msg.actionBar(player, Component.translatable("kultivator.langit.meteor.full").withColor(0x8FE6FF));
			return false;
		}
		player.getCooldowns().addCooldown(stack, 20);
		stack.consume(1, player);
		double before = data.qi();
		Qi.restore(player, LangitItems.METEOR_QI);
		double gained = Math.max(0.0D, Cultivators.get(player).qi() - before);
		Cultivators.dirty(player);
		Msg.actionBar(player, Component.translatable("kultivator.langit.meteor.absorbed", Msg.number(gained + 1.0E-6D)).withColor(0x8FE6FF));
		Net.send(player, LangitFxPayload.at(LangitFxPayload.KIND_ABSORB, player.getX(), player.getY() + 1.0D, player.getZ(), 0));
		player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
				SoundSource.PLAYERS, 0.8F, 0.7F);
		return true;
	}

	// ================================================================== Embun Roh

	/** The Qi regeneration factor of a drunk Embun Roh (1 when none runs). */
	private static double dewFactor(ServerPlayer player) {
		CompoundTag own = Cultivators.get(player).extra().getCompoundOrEmpty(EXTRA_KEY);
		long until = own.getLongOr("dew_until", 0L);
		long now = player.level().getGameTime();
		return until > now && until - now <= DEW_TICKS ? DEW_REGEN : 1.0D;
	}

	/** Drinking an Embun Roh: the regeneration boost for 5 minutes (a new one restarts it). */
	static void drinkDew(ServerPlayer player) {
		CompoundTag own = extra(Cultivators.get(player));
		own.putLong("dew_until", player.level().getGameTime() + DEW_TICKS);
		Cultivators.dirty(player);
		Msg.actionBar(player, Component.translatable("kultivator.langit.dew.drunk", Math.round((DEW_REGEN - 1.0D) * 100.0D), DEW_TICKS / 1200)
				.withColor(0xD8B8FF));
	}

	/**
	 * {@code UseItemCallback}: sneak + use an empty bottle outdoors in Kabut Qi Pekat or at Fajar Ungu gathers an Embun
	 * Roh ({@code dewPerDay} per player per day). PASS everywhere else, so vanilla bottles work as always.
	 */
	private static InteractionResult useItem(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Items.GLASS_BOTTLE) || !player.isSecondaryUseActive() || !(player instanceof ServerPlayer serverPlayer)
				|| !(level instanceof ServerLevel serverLevel) || !SpiritWeathers.on(serverLevel)) {
			return InteractionResult.PASS;
		}
		SpiritWeather weather = SpiritWeathers.at(serverLevel, serverPlayer.blockPosition());
		if (weather != SpiritWeather.DENSE_MIST && weather != SpiritWeather.PURPLE_DAWN) {
			return InteractionResult.PASS;
		}
		if (!LangitSites.outdoors(serverPlayer)) {
			Msg.actionBar(serverPlayer, Component.translatable("kultivator.langit.dew.indoors").withStyle(ChatFormatting.GRAY));
			return InteractionResult.PASS;
		}
		LangitConfig config = LangitConfig.get();
		CultivationData data = Cultivators.get(serverPlayer);
		CompoundTag own = extra(data);
		long day = Math.floorDiv(serverLevel.getOverworldClockTime(), (long) SkyEvents.DAY);
		int count = own.getLongOr("dew_day", -1L) == day ? own.getIntOr("dew_n", 0) : 0;
		if (count >= config.dewPerDay) {
			Msg.actionBar(serverPlayer, Component.translatable("kultivator.langit.dew.limit", config.dewPerDay).withStyle(ChatFormatting.YELLOW));
			return InteractionResult.FAIL;
		}
		own.putLong("dew_day", day);
		own.putInt("dew_n", count + 1);
		Cultivators.dirty(serverPlayer);
		stack.consume(1, serverPlayer);
		TreasureBirths.give(serverPlayer, new ItemStack(LangitItems.EMBUN_ROH));
		Msg.actionBar(serverPlayer, Component.translatable("kultivator.langit.dew.gathered", count + 1, config.dewPerDay).withColor(0xD8B8FF));
		serverLevel.playSound(null, serverPlayer.getX(), serverPlayer.getY() + 1.0D, serverPlayer.getZ(), SoundEvents.BOTTLE_FILL,
				SoundSource.PLAYERS, 1.0F, 1.3F);
		Net.send(serverPlayer, LangitFxPayload.at(LangitFxPayload.KIND_DEW, serverPlayer.getX(), serverPlayer.getEyeY(), serverPlayer.getZ(),
				weather.ordinal()));
		return InteractionResult.SUCCESS_SERVER;
	}

	// ================================================================== helpers

	/** The module's own data compound of the player (created on first use). */
	static CompoundTag extra(CultivationData data) {
		CompoundTag own = data.extra().getCompound(EXTRA_KEY).orElse(null);
		if (own == null) {
			own = new CompoundTag();
			data.extra().put(EXTRA_KEY, own);
		}
		return own;
	}

	/** True at most once per {@code ticks} for this player and key (chat lines that should not repeat). */
	static boolean once(ServerPlayer player, String key, int ticks) {
		String id = player.getUUID() + ":" + key;
		long now = player.level().getServer().getTickCount();
		Long last = ONCE.get(id);
		if (last != null && now - last < ticks && now >= last) {
			return false;
		}
		ONCE.put(id, now);
		return true;
	}

	/**
	 * The voice of Heaven to {@code player}: the new line {@code heaven.<newKey>} once the voice module adds it (DESIGN
	 * 34.8: {@code langit.treasure_born}, {@code langit.blood_eclipse}), else the existing {@code fallback} line.
	 */
	static void heaven(ServerPlayer player, @Nullable String newKey, String fallback) {
		if (newKey != null && VoiceEvents.exists("heaven." + newKey)) {
			Voice.heaven(player, "heaven." + newKey);
		} else {
			Voice.heaven(player, fallback);
		}
	}

	private Langit() {
	}
}
