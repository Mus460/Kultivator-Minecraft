package dev.kultivator.disciple.path;

import dev.kultivator.Kultivator;
import dev.kultivator.data.Cultivators;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.disciple.DuelRules;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.registry.ModItems;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Segel Jiwa, the Soul Seal (DESIGN 20.3.18). Sumber: <i>Tales of Demons and Gods</i>: a new disciple forms a Soul Seal
 * with the master for loyalty.
 * <p>
 * With bond 50 or more the master sneak-uses a Giok Segel Jiwa on the disciple for a 5 s seal-glyph ritual (at most
 * {@code 3 + floor(hall level / 3)} sealed disciples). A sealed disciple can be recalled from anywhere in the same
 * dimension (cooldown 60 s, safe spots only), gets +10% max health and damage, and when the master drops below 30%
 * health every sealed disciple within 48 blocks gets Speed II and Strength I for 10 s and turns to ASSIST until the
 * fight is over.
 */
public final class SoulSeal {

	public static final int BASE_MAX_SEALED = 3;
	public static final int RITUAL_TICKS = 100;
	public static final double RITUAL_RANGE = 6.0D;
	public static final int RECALL_COOLDOWN = 1200;
	public static final double HEALTH_BONUS = 0.10D;
	public static final double DAMAGE_BONUS = 1.10D;
	public static final double MAX_QI_BONUS = 0.10D;
	public static final double DANGER_HEALTH = 0.30D;
	public static final double DANGER_RANGE = 48.0D;
	public static final int DANGER_EFFECT_TICKS = 200;
	/** The danger call answers at most this often per master (ticks). */
	public static final int DANGER_COOLDOWN = 300;
	/** The fight counts as over after the master was not hurt for this long (ticks). */
	public static final int CALM_TICKS = 200;

	private static final Identifier HEALTH_ID = Kultivator.id("path_seal_health");
	/** extra.path key: the order a danger call replaced with ASSIST (given back when no rally runs any more). */
	private static final String RALLY_PREV = "rally_prev";

	/** Giok Segel Jiwa (shapeless: amethyst shard, echo shard, gold ingot, paper). */
	public static final Item ITEM = ModItems.register("soul_seal_jade", SealJadeItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

	private record Ritual(DiscipleEntity entity, UUID master, Vec3 at, int[] ticks) {
	}

	/** A disciple sent to ASSIST by the danger call: its order before, to give back when the fight is over. */
	private record Rally(DiscipleEntity entity, DiscipleEntity.Mode mode, @Nullable BlockPos post) {
	}

	private static final Map<UUID, Ritual> RITUALS = new HashMap<>();
	private static final Map<UUID, Rally> RALLIED = new HashMap<>();
	/** Master UUID -> game time of the last danger call and of the last hurt. */
	private static final Map<UUID, Long> LAST_CALL = new HashMap<>();
	private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

	/** Loads the class (registers the item). */
	static void init() {
	}

	static void register() {
		DiscipleHooks.addStats((entity, sink) -> {
			if (sealed(entity.data())) {
				sink.add(Attributes.MAX_HEALTH, HEALTH_ID, HEALTH_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			}
		});
		DiscipleHooks.addDamageFactor((entity, target, technique) -> sealed(entity.data()) ? DAMAGE_BONUS : 1.0D);
		DiscipleHooks.addMaxQiFactor(data -> sealed(data) ? 1.0D + MAX_QI_BONUS : 1.0D);
		DiscipleHooks.addFeedHandler((entity, master, stack) -> {
			if (!stack.is(ITEM)) {
				return false;
			}
			use(master, entity, stack);
			return true;
		});
	}

	public static boolean sealed(DiscipleData data) {
		return DisciplePaths.tag(data).getBooleanOr("sealed", false);
	}

	/** How many disciples the master may seal: {@code 3 + floor(hall level / 3)}. */
	static int maxSealed(ServerPlayer master) {
		return BASE_MAX_SEALED + Halls.level(master.getUUID()) / 3;
	}

	/** Sealed souls still in the master's disciple list (living or fallen). */
	static int sealedCount(ServerPlayer master) {
		Set<String> listed = listedSouls(master);
		int count = 0;
		for (Tag tag : DisciplePaths.playerTag(master).getListOrEmpty("sealed")) {
			if (tag.asString().map(listed::contains).orElse(false)) {
				count++;
			}
		}
		return count;
	}

	/** The soul ids of the master's disciple list ({@code CultivationData.extra().disciples.list}, read only). */
	static Set<String> listedSouls(ServerPlayer master) {
		Set<String> souls = new HashSet<>();
		CompoundTag root = Cultivators.get(master).extra().getCompoundOrEmpty(Disciples.EXTRA);
		for (Tag tag : root.getListOrEmpty("list")) {
			if (tag instanceof CompoundTag entry) {
				souls.add(entry.getStringOr("id", ""));
			}
		}
		return souls;
	}

	/** Why the disciple cannot be sealed now (a translation key), or null. */
	static @Nullable String blocker(ServerPlayer master, DiscipleEntity entity) {
		DiscipleData data = entity.data();
		String p = DisciplePaths.PREFIX;
		if (sealed(data)) {
			return p + "why.sealed_already";
		}
		if (DisciplePaths.bond(data) < Bond.SEAL_BOND) {
			return p + "why.seal_bond";
		}
		if (sealedCount(master) >= maxSealed(master)) {
			return p + "why.seal_max";
		}
		if (data.breakingThrough() || Calamity.running(entity) || RITUALS.containsKey(entity.soulId())) {
			return p + "why.busy";
		}
		return null;
	}

	// ================================================================== ritual

	/** The jade used on a disciple (sneak-use, or the screen's feed button): starts the 5 s ritual and takes the jade. */
	static void use(ServerPlayer master, DiscipleEntity entity, ItemStack stack) {
		String why = blocker(master, entity);
		if (why != null) {
			Msg.chat(master, Component.translatable(why, DisciplePaths.name(entity), Bond.SEAL_BOND, maxSealed(master)).withColor(DisciplePaths.REFUSE));
			return;
		}
		if (!master.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		RITUALS.put(entity.soulId(), new Ritual(entity, master.getUUID(), entity.position(), new int[]{0}));
		entity.getLookControl().setLookAt(master, 30.0F, 30.0F);
		if (entity.level() instanceof ServerLevel level) {
			DisciplePaths.sound(level, entity.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.8F);
			DisciplePaths.sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.6F);
		}
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "seal.start", DisciplePaths.name(entity)).withColor(DisciplePaths.JADE));
	}

	static void tick(MinecraftServer server) {
		if (!RITUALS.isEmpty()) {
			for (Map.Entry<UUID, Ritual> entry : List.copyOf(RITUALS.entrySet())) {
				tickRitual(server, entry.getKey(), entry.getValue());
			}
		}
		if (!RALLIED.isEmpty() && server.getTickCount() % 20 == 0) {
			calmDown(server);
		}
	}

	/** Returns the jade taken at the ritual's start (a refused or broken ritual). */
	private static void giveJadeBack(ServerPlayer master) {
		ItemStack back = new ItemStack(ITEM);
		if (!master.hasInfiniteMaterials() && !master.getInventory().add(back) && !back.isEmpty()) {
			// Never through a portal (an End portal would build its platform) and never on a plate.
			ItemEntity item = new ItemEntity(master.level(), master.getX(), master.getY() + 0.5D, master.getZ(), back);
			item.setDefaultPickUpDelay();
			item.addTag(NoGrief.TAG);
			item.setPortalCooldown();
			master.level().addFreshEntity(item);
		}
	}

	private static void tickRitual(MinecraftServer server, UUID soul, Ritual ritual) {
		DiscipleEntity entity = ritual.entity();
		ServerPlayer master = server.getPlayerList().getPlayer(ritual.master());
		boolean broken = entity.isRemoved() || !entity.isAlive() || master == null || !DisciplePaths.near(master, entity, RITUAL_RANGE)
				|| entity.position().distanceToSqr(ritual.at()) > 4.0D;
		if (broken) {
			RITUALS.remove(soul);
			if (master != null) {
				Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "seal.broken", DisciplePaths.name(entity)).withColor(DisciplePaths.REFUSE));
				giveJadeBack(master);
			}
			return;
		}
		int ticks = ++ritual.ticks()[0];
		entity.getNavigation().stop();
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (ticks % 10 == 0) {
			Fx.send(level, entity.position(), master.position().add(0.0D, 1.0D, 0.0D), FxType.STAR, PathFxIds.SEAL_GLYPH, 0,
					ticks / (float) RITUAL_TICKS);
		}
		if (ticks % 20 == 0) {
			DisciplePaths.sound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.8F + ticks / 100.0F);
		}
		if (ticks < RITUAL_TICKS) {
			return;
		}
		RITUALS.remove(soul);
		if (blocker(master, entity) != null && !sealed(entity.data())) {
			// The limit was reached meanwhile (another ritual ended first).
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "why.seal_max", DisciplePaths.name(entity), Bond.SEAL_BOND,
					maxSealed(master)).withColor(DisciplePaths.REFUSE));
			giveJadeBack(master); // the jade was taken when the ritual started
			return;
		}
		DisciplePaths.section(entity.data()).putBoolean("sealed", true);
		ListTag list = DisciplePaths.playerSection(master).getListOrEmpty("sealed");
		ListTag write = list.copy();
		write.add(net.minecraft.nbt.StringTag.valueOf(entity.soulId().toString()));
		DisciplePaths.playerSection(master).put("sealed", write);
		Cultivators.dirty(master);
		entity.refreshSynced();
		DisciplePaths.syncPower(entity);
		Fx.send(level, entity.position(), FxType.STAR, PathFxIds.SEALED, 0, 1.0F);
		DisciplePaths.sound(level, entity.position(), SoundEvents.BELL_RESONATE, 1.0F, 1.3F);
		DisciplePaths.sound(level, entity.position(), SoundEvents.BEACON_ACTIVATE, 0.8F, 1.5F);
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "seal.done", DisciplePaths.name(entity)).withColor(DisciplePaths.GOLD));
		Advancements.grant(master, "soul_seal");
	}

	/** Ritual progress 0..1 for the screen, or -1. */
	static float ritualProgress(DiscipleEntity entity) {
		Ritual ritual = RITUALS.get(entity.soulId());
		return ritual == null ? -1.0F : ritual.ticks()[0] / (float) RITUAL_TICKS;
	}

	// ================================================================== recall

	/** Recalls the sealed disciple with entity id {@code param} to its master's side (same dimension, cooldown 60 s). */
	static void recall(ServerPlayer master, String param) {
		int id;
		try {
			id = Integer.parseInt(param);
		} catch (NumberFormatException exception) {
			return;
		}
		if (!(master.level().getEntity(id) instanceof DiscipleEntity entity) || !entity.isOwnedBy(master) || !entity.isAlive()
				|| entity.role() != DiscipleEntity.Role.DISCIPLE) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "why.recall_gone").withColor(DisciplePaths.REFUSE));
			return;
		}
		DiscipleData data = entity.data();
		Component name = DisciplePaths.name(entity);
		long now = DisciplePaths.now(master.level().getServer());
		long ready = DisciplePaths.tag(data).getCompoundOrEmpty("cd").getLongOr("recall", 0L);
		String why = null;
		if (!sealed(data)) {
			why = "why.recall_unsealed";
		} else if (now < ready) {
			why = "why.recall_cooldown";
		} else if (data.breakingThrough() || Calamity.running(entity) || DuelRules.inDuel(entity) || entity.isReleasing()) {
			why = "why.busy";
		}
		if (why != null) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + why, name, (ready - now + 19L) / 20L).withColor(DisciplePaths.REFUSE));
			return;
		}
		if (!entity.teleportNear(master)) {
			Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "why.recall_no_spot", name).withColor(DisciplePaths.REFUSE));
			return;
		}
		DisciplePaths.child(DisciplePaths.section(data), "cd").putLong("recall", now + RECALL_COOLDOWN);
		if (entity.holdsActivitySpot()) {
			entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
		}
		if (!entity.mode().follows()) {
			entity.setMode(DiscipleEntity.Mode.FOLLOW);
		}
		entity.setTarget(null);
		Fx.send(master.level(), entity.position(), master.position().add(0.0D, 1.0D, 0.0D), FxType.STAR, PathFxIds.SEAL_GLYPH, 0, 1.0F);
		Msg.chat(master, Component.translatable(DisciplePaths.PREFIX + "seal.recalled", name).withColor(DisciplePaths.JADE));
	}

	// ================================================================== the master in danger

	/** The master was hurt: below 30% health every sealed disciple within 48 blocks rushes to help. */
	static void onMasterHurt(ServerPlayer master) {
		MinecraftServer server = master.level().getServer();
		long now = DisciplePaths.now(server);
		LAST_HURT.put(master.getUUID(), now);
		if (master.getHealth() >= master.getMaxHealth() * DANGER_HEALTH || now - LAST_CALL.getOrDefault(master.getUUID(), -100000L) < DANGER_COOLDOWN) {
			return;
		}
		boolean any = false;
		for (DiscipleEntity entity : Disciples.loaded(master.getUUID())) {
			if (!sealed(entity.data()) || !DisciplePaths.near(master, entity, DANGER_RANGE) || entity.data().breakingThrough()
					|| Calamity.running(entity) || DuelRules.inDuel(entity) || entity.isReleasing()) {
				continue;
			}
			entity.addEffect(new MobEffectInstance(MobEffects.SPEED, DANGER_EFFECT_TICKS, 1), master);
			entity.addEffect(new MobEffectInstance(MobEffects.STRENGTH, DANGER_EFFECT_TICKS, 0), master);
			if (entity.mode() != DiscipleEntity.Mode.ASSIST && DiscipleHooks.modeBlocker(entity, DiscipleEntity.Mode.ASSIST) == null) {
				if (RALLIED.putIfAbsent(entity.soulId(), new Rally(entity, entity.mode(), entity.post())) == null) {
					// Saved with the disciple: an unload or a restart mid-fight still gives the old order back (tick20).
					DisciplePaths.section(entity.data()).putString(RALLY_PREV, entity.mode().id());
				}
				if (entity.holdsActivitySpot()) {
					entity.setActivity(DiscipleEntity.ACTIVITY_NONE, null);
				}
				entity.setMode(DiscipleEntity.Mode.ASSIST);
			}
			any = true;
		}
		if (any) {
			LAST_CALL.put(master.getUUID(), now);
			Msg.actionBar(master, Component.translatable(DisciplePaths.PREFIX + "seal.danger").withStyle(ChatFormatting.GOLD));
			DisciplePaths.sound(master.level(), master.position(), SoundEvents.BELL_BLOCK, 1.0F, 1.6F);
		}
	}

	/** Gives the rallied disciples their old orders back once the master was not hurt for 10 s and they are not fighting. */
	private static void calmDown(MinecraftServer server) {
		long now = DisciplePaths.now(server);
		for (Map.Entry<UUID, Rally> entry : List.copyOf(RALLIED.entrySet())) {
			Rally rally = entry.getValue();
			DiscipleEntity entity = rally.entity();
			if (entity.isRemoved() || !entity.isAlive()) {
				RALLIED.remove(entry.getKey());
				continue;
			}
			UUID owner = entity.ownerId();
			long hurt = owner == null ? 0L : LAST_HURT.getOrDefault(owner, 0L);
			if (now - hurt < CALM_TICKS || entity.getTarget() != null || entity.mode() != DiscipleEntity.Mode.ASSIST) {
				if (entity.mode() != DiscipleEntity.Mode.ASSIST) {
					// The master gave another order meanwhile: that order stays.
					RALLIED.remove(entry.getKey());
					DisciplePaths.section(entity.data()).remove(RALLY_PREV);
				}
				continue;
			}
			RALLIED.remove(entry.getKey());
			DisciplePaths.section(entity.data()).remove(RALLY_PREV);
			BlockPos post = rally.post();
			if (post != null && rally.mode() != DiscipleEntity.Mode.FOLLOW && entity.level() instanceof ServerLevel level && level.isLoaded(post)) {
				Vec3 spot = Targeting.safeSpot(level, entity, Vec3.atBottomCenterOf(post));
				if (spot != null) {
					Vec3 old = entity.position();
					entity.snapTo(spot.x, spot.y, spot.z, entity.getYRot(), entity.getXRot());
					entity.getNavigation().stop();
					Fx.burst(level, old.add(0.0D, 1.0D, 0.0D), entity.data().rootColor(), 12, 0.6F);
					Fx.burst(level, spot.add(0.0D, 1.0D, 0.0D), entity.data().rootColor(), 16, 0.7F);
				}
			}
			entity.setMode(rally.mode());
		}
	}

	/** The seal works through the hooks and the danger call; here only a rally cut off by an unload or a restart ends. */
	static void tick20(DiscipleEntity entity, @Nullable ServerPlayer master) {
		CompoundTag path = DisciplePaths.tag(entity.data());
		if (!path.contains(RALLY_PREV) || RALLIED.containsKey(entity.soulId())) {
			return;
		}
		DiscipleEntity.Mode previous = DiscipleEntity.Mode.byId(path.getStringOr(RALLY_PREV, ""));
		DisciplePaths.section(entity.data()).remove(RALLY_PREV);
		if (previous != null && previous != DiscipleEntity.Mode.ASSIST && entity.mode() == DiscipleEntity.Mode.ASSIST) {
			entity.setMode(previous);
		}
	}

	static void clear() {
		RITUALS.clear();
		RALLIED.clear();
		LAST_CALL.clear();
		LAST_HURT.clear();
	}

	// ================================================================== the item

	/** Giok Segel Jiwa: sneak-use it on your disciple. */
	public static class SealJadeItem extends Item {

		public SealJadeItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer serverPlayer) {
				Msg.actionBar(serverPlayer, Component.translatable(DisciplePaths.PREFIX + "seal.item_hint").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.PASS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			builder.accept(Component.translatable(DisciplePaths.PREFIX + "seal.tooltip", Bond.SEAL_BOND).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable(DisciplePaths.PREFIX + "seal.tooltip_effect").withStyle(ChatFormatting.DARK_AQUA));
			builder.accept(Component.translatable(DisciplePaths.PREFIX + "seal.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private SoulSeal() {
	}
}
