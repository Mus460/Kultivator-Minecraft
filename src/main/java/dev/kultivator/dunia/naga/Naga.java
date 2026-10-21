package dev.kultivator.dunia.naga;

import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.BeastItems;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.data.Cultivators;
import dev.kultivator.dunia.DuniaBudget;
import dev.kultivator.dunia.DuniaConfig;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.fx.Fx;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Module B of DESIGN 34, Naga dan Binatang: 57 Naga Liar (wild Jiao in the sky, the Selendang and the contest of wills,
 * the tamed Jiao) and 58 Evolusi Binatang Roh (tiers 4-6 through the Kesengsaraan Binatang Roh, the bloodline arts and
 * the tier 6 passives). Wired by one line in {@code DuniaContent.init()}; registers its own config section
 * ({@code naga}), entities, items, payloads, events and {@code /kultivasi uji naga|evolusi} nodes, and implements
 * {@code BeastEvolutionService}. Runtime code never changes a block (rule W1); every Jiao and tribulation effect is an
 * entity effect, damage, a particle or a sound.
 */
public final class Naga {

	private static NagaConfig config = new NagaConfig();
	private static boolean initialised;
	/** Beast UUID → last Sisik / Mutiara feeding of a wolf or fox (shares the 30 s rule of the pill). */
	private static final Map<UUID, Long> FED = new HashMap<>();

	/** From {@code DuniaContent.init()} (both physical sides). */
	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		config = DuniaConfig.section("naga", NagaConfig.class, NagaConfig::new, NagaConfig::clamp);
		NagaEntities.init();
		NagaItems.init();
		BeastEvolution.init();
		NagaPayloads.init();
		DuniaApi.setBeasts(new BeastEvolution());
		DuniaBudget.setCap(DuniaBudget.DRAGON, config.worldCap);
		DuniaConfig.onReload(() -> DuniaBudget.setCap(DuniaBudget.DRAGON, config.worldCap));
		WildDragons.init();
		WillContest.init();
		BeastTribulation.init();
		BloodlineArts.init();
		SpiritBeasts.addEvolutionListener(BeastEvolution::onEvolved);
		SpiritBeasts.addReadyListener(BeastEvolution::onReady);
		// before the default phase: a sneak-use with the offering (a King core among it) must start the tribulation, not
		// feed the core to the beast (harta.BeastCores answers King cores in the default phase)
		net.minecraft.resources.Identifier early = dev.kultivator.Kultivator.id("naga_use_early");
		UseEntityCallback.EVENT.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(early, Naga::onUseEntity);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(Naga::onKill);
		ServerPlayerEvents.LEAVE.register(player -> {
			WillContest.clearFor(player);
			TamedJiao.onOwnerGone(player, null);
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
				TamedJiao.onOwnerGone(player, "kultivator.naga.dismiss.dimension"));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				TamedJiao.onOwnerGone(player, null);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(TamedJiao::dismissAll);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TamedJiao.clear();
			FED.clear();
		});
		NagaCommands.init();
	}

	/** Section {@code naga} of {@code config/kultivator-dunia.json} (reloads in place). */
	public static NagaConfig config() {
		return config;
	}

	// ================================================================== interactions

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity,
			@Nullable EntityHitResult hitResult) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (entity instanceof NagaLiarEntity dragon) {
			if (level.isClientSide()) {
				return stack.is(NagaItems.SELENDANG) || dragon.isTamed() ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return player instanceof ServerPlayer serverPlayer ? useDragon(serverPlayer, dragon, stack) : InteractionResult.PASS;
		}
		BeastData data = entity.getAttached(BeastData.TYPE);
		if (data == null || !(entity instanceof Mob beast) || !data.ownedBy(player)) {
			return InteractionResult.PASS;
		}
		boolean feed = stack.getItem() instanceof NagaItems.FeedItem;
		boolean sneakingStatus = player.isShiftKeyDown() && data.tier() >= BeastData.MAX_TIER
				&& (stack.isEmpty() || data.tier() < BeastData.TOP_TIER && BeastEvolution.isOfferingItem(stack, data.kind(), data.tier() + 1));
		if (!feed && !sneakingStatus) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (feed) {
			feedBeast(serverPlayer, beast, stack);
			return InteractionResult.SUCCESS;
		}
		if (stack.isEmpty()) {
			status(serverPlayer, beast);
		} else {
			BeastTribulation.start(serverPlayer, beast, false, null);
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult useDragon(ServerPlayer player, NagaLiarEntity dragon, ItemStack stack) {
		if (stack.is(NagaItems.SELENDANG)) {
			return WillContest.begin(player, dragon, stack, null) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (!dragon.isTamed()) {
			return InteractionResult.PASS;
		}
		boolean owner = player.getUUID().equals(dragon.ownerId());
		if (!owner) {
			LivingEntity rider = dragon.getControllingPassenger();
			if (rider != null && config.mount && dragon.getPassengers().size() < 2) {
				player.startRiding(dragon);
				return InteractionResult.SUCCESS;
			}
			Msg.actionBar(player, Component.translatable("kultivator.naga.not_yours").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			TamedJiao.Record record = TamedJiao.get(player);
			if (record != null && record.tier() < BeastData.TOP_TIER && BeastEvolution.isOfferingItem(stack, BeastData.JIAO, record.tier() + 1)) {
				BeastTribulation.start(player, dragon, false, null);
			} else {
				status(player, dragon);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(BeastItems.SPIRIT_BEAST_PILL) || stack.getItem() instanceof NagaItems.FeedItem) {
			TamedJiao.feed(player, dragon, stack);
			return InteractionResult.SUCCESS;
		}
		if (!config.mount) {
			Msg.actionBar(player, Component.translatable("kultivator.naga.mount.off").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		if (dragon.hasFlag(NagaLiarEntity.FLAG_TRIBULATION)) {
			return InteractionResult.SUCCESS;
		}
		if (player.startRiding(dragon)) {
			Msg.actionBar(player, Component.translatable("kultivator.naga.mount.hint").withStyle(ChatFormatting.AQUA));
		}
		return InteractionResult.SUCCESS;
	}

	/** Sisik or Mutiara Jiao given to one's own wolf or fox (+60 / +300 spirit XP, one per 30 s). */
	private static void feedBeast(ServerPlayer player, Mob beast, ItemStack stack) {
		if (!(stack.getItem() instanceof NagaItems.FeedItem food)) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = FED.get(beast.getUUID());
		if (last != null && now - last < SpiritBeasts.PILL_COOLDOWN_TICKS && now >= last) {
			Msg.actionBar(player, Component.translatable("kultivator.beasts.pill.digesting", beast.getDisplayName(),
					(SpiritBeasts.PILL_COOLDOWN_TICKS - (now - last) + 19L) / 20L).withStyle(ChatFormatting.YELLOW));
			return;
		}
		FED.put(beast.getUUID(), now);
		stack.consume(1, player);
		ServerLevel level = player.level();
		level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 1.0F);
		level.playSound(null, beast.getX(), beast.getY(), beast.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.4F);
		BeastData before = SpiritBeasts.data(beast);
		Fx.burst(level, beast.position().add(0.0D, beast.getBbHeight() * 0.6D, 0.0D),
				SpiritBeasts.tierColor(before == null ? 0 : before.kind(), before == null ? 0 : before.tier()), 14, 1.2F);
		BeastData after = SpiritBeasts.addXp(beast, food.xp());
		if (after != null) {
			double next = after.nextTierXp();
			Msg.chat(player, next < 0.0D
					? Component.translatable("kultivator.beasts.pill.xp_max", beast.getDisplayName(), Msg.number(food.xp())).withStyle(ChatFormatting.AQUA)
					: Component.translatable("kultivator.beasts.pill.xp", beast.getDisplayName(), Msg.number(food.xp()), Msg.number(after.xp()),
							Msg.number(next)).withStyle(ChatFormatting.AQUA));
		}
	}

	/** Sneak-use with an empty hand: where the beast stands on its way to the next tier. */
	static void status(ServerPlayer player, LivingEntity beast) {
		BeastData data = BeastEvolution.dataOf(beast, player);
		if (data == null) {
			return;
		}
		int color = SpiritBeasts.tierColor(data.kind(), data.tier());
		Msg.chat(player, Component.translatable("kultivator.naga.status.head", beast.getDisplayName(), data.title(), data.tier()).withColor(color));
		if (data.tier() >= BeastData.TOP_TIER) {
			Msg.chat(player, Component.translatable("kultivator.naga.status.top").withStyle(ChatFormatting.GOLD));
			return;
		}
		int next = data.tier() + 1;
		Msg.chat(player, Component.translatable("kultivator.naga.status.xp", Msg.number(data.xp()), Msg.number(BeastData.TIER_XP[next]))
				.withStyle(data.xp() >= BeastData.TIER_XP[next] ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		boolean realm = Cultivators.get(player).realm().atLeast(BeastData.TIER_REALM[next]);
		Msg.chat(player, Component.translatable("kultivator.naga.status.realm", BeastData.TIER_REALM[next].displayName())
				.withStyle(realm ? ChatFormatting.GREEN : ChatFormatting.RED));
		if (data.nextNeedsTribulation()) {
			boolean has = BeastEvolution.resolve(player, BeastEvolution.offering(data.kind(), next), false) != null;
			Msg.chat(player, Component.translatable("kultivator.naga.status.offering", BeastEvolution.describe(BeastEvolution.offering(data.kind(),
					next))).withStyle(has ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			long luka = BeastEvolution.lukaUntil(beast, player) - player.level().getGameTime();
			if (luka > 0L) {
				Msg.chat(player, Component.translatable("kultivator.naga.evolusi.luka", Msg.number(luka / 1200.0D)).withStyle(ChatFormatting.RED));
			}
			Msg.chat(player, Component.translatable("kultivator.naga.status.how").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	// ================================================================== spirit XP of the tamed Jiao

	private static void onKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		if (killed instanceof NagaLiarEntity) {
			return;
		}
		if (killer instanceof NagaLiarEntity dragon && dragon.isTamed()) {
			ServerPlayer owner = dragon.serverOwner();
			if (owner != null) {
				TamedJiao.addXp(owner, killed.getMaxHealth());
			}
			return;
		}
		if (killer instanceof ServerPlayer player && killed instanceof Enemy) {
			NagaLiarEntity dragon = TamedJiao.active(player);
			if (dragon != null && dragon.level() == player.level() && dragon.distanceToSqr(player) <= 16.0D * 16.0D) {
				TamedJiao.addXp(player, killed.getMaxHealth() * 0.5D);
			}
		}
	}

	private Naga() {
	}
}
