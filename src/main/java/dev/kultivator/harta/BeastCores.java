package dev.kultivator.harta;

import dev.kultivator.alchemy.PillQuality;
import dev.kultivator.beast.BeastData;
import dev.kultivator.beast.SpiritBeasts;
import dev.kultivator.cultivation.PillToxicity;
import dev.kultivator.cultivation.Progression;
import dev.kultivator.dao.Comprehension;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Element;
import dev.kultivator.data.XpSource;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inti Binatang, beast cores with an element (DESIGN 19.3.3; drops by P7-tempur, uses here).
 * <p>
 * Sources: cores are graded, valuable energy nuclei (BTTH ch. 11, 14; RMJ ch. 397, 529); absorbing a core leaves
 * violent, unmastered energy behind (Coiling Dragon Book 8 ch. 57; BTTH ch. 11) - that is the toxin here; feeding a
 * spirit beast (Coiling Dragon Book 8 ch. 57, Haeru). Root attribute matching is inspired by Tales of Demons and Gods
 * ch. 13. The grade names, the element on a core and all numbers are game design.
 */
public final class BeastCores {

	/** Grades: 1 Low (Rendah), 2 Mid (Menengah), 3 High (Tinggi), 4 King (Raja); an elite drops grade = its tier. */
	public static final int LOW = 1;
	public static final int MID = 2;
	public static final int HIGH = 3;
	public static final int KING = 4;

	/** Absorbing (index grade − 1): XP share of the bar, its cap, the toxin and the comprehension for an affine Dao. */
	public static final double[] XP_SHARE = {0.03D, 0.06D, 0.10D, 0.15D};
	public static final double[] XP_CAP = {150.0D, 900.0D, 5000.0D, 25000.0D};
	public static final int[] TOXIN = {6, 10, 16, 28};
	public static final double[] COMPREHENSION = {0.5D, 1.0D, 2.0D, 4.0D};
	/** XP factor when the core's element is in the player's root. */
	public static final double ROOT_BONUS = 1.5D;
	public static final int USE_COOLDOWN = 20;
	/** Feeding a spirit beast: beast XP by grade, x{@link #BEAST_MATCH} for a matching element, cooldown per beast. */
	public static final double[] BEAST_XP = {60.0D, 150.0D, 400.0D, 1000.0D};
	public static final double BEAST_MATCH = 1.5D;
	public static final int FEED_COOLDOWN = 200;
	/** Kantor Misi contribution points per core. */
	public static final double[] MISSION_PK = {3.0D, 10.0D, 30.0D, 120.0D};
	/** As an alchemy catalyst: success bonus and quality floor (null = none). */
	public static final double[] CATALYST_CHANCE = {0.05D, 0.08D, 0.12D, 0.18D};
	private static final PillQuality[] QUALITY_FLOOR = {null, PillQuality.MID, PillQuality.HIGH, PillQuality.PERFECT};
	/** Colour of a core without element. */
	public static final int NEUTRAL_COLOR = 0xC8A060;

	/** Beast entity UUID -> game time it may eat a core again. */
	private static final Map<UUID, Long> FED_UNTIL = new HashMap<>();

	static void init() {
		UseEntityCallback.EVENT.register(BeastCores::onUseEntity);
	}

	// ================================================================ the stack

	/** A core of {@code element} and {@code grade} (1-4). */
	public static ItemStack create(Element element, int grade) {
		ItemStack stack = new ItemStack(HartaItems.coreItem(grade));
		if (element != null) {
			stack.set(HartaComponents.CORE_ELEMENT, element.id());
			stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(element.color())));
		}
		return stack;
	}

	/** Drops one core of {@code element} and {@code grade} as an item at {@code pos} (block-safe item entity). */
	public static void drop(ServerLevel level, Vec3 pos, Element element, int grade) {
		ItemStack stack = create(element, grade);
		if (stack.isEmpty()) {
			return;
		}
		ItemEntity entity = new ItemEntity(level, pos.x, pos.y + 0.25D, pos.z, stack);
		entity.setDeltaMovement((level.getRandom().nextDouble() - 0.5D) * 0.1D, 0.25D, (level.getRandom().nextDouble() - 0.5D) * 0.1D);
		entity.setDefaultPickUpDelay();
		entity.addTag(NoGrief.TAG);
		entity.setPortalCooldown();
		level.addFreshEntity(entity);
		Fx.send(level, pos.add(0.0D, 0.5D, 0.0D), FxType.TREASURE, HartaFxIds.CORE_DROP, element == null ? -1 : element.ordinal(), grade);
	}

	/** True for a beast core of any grade. */
	public static boolean isCore(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BeastCoreItem;
	}

	/** The grade 1-4 of a core, 0 for anything else. */
	public static int grade(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BeastCoreItem core ? core.grade() : 0;
	}

	/** The element of a core, null for anything else. */
	public static @Nullable Element element(ItemStack stack) {
		if (!isCore(stack)) {
			return null;
		}
		String id = stack.get(HartaComponents.CORE_ELEMENT);
		return id == null ? null : Element.byId(id);
	}

	/** "Rendah", "Menengah", "Tinggi", "Raja". */
	public static Component gradeName(int grade) {
		return Component.translatable("kultivator.harta.core_grade." + Math.max(1, Math.min(4, grade)));
	}

	/** The Dao an element is affine to: Metal Sword, Fire Fire, Water and Ice Ice, Lightning, Earth Body, Wood Life, Wind Shadow, Space. */
	public static Dao affinity(Element element) {
		return switch (element) {
			case METAL -> Dao.SWORD;
			case FIRE -> Dao.FIRE;
			case WATER, ICE -> Dao.ICE;
			case LIGHTNING -> Dao.LIGHTNING;
			case EARTH -> Dao.BODY;
			case WOOD -> Dao.LIFE;
			case WIND -> Dao.SHADOW;
			case SPACE -> Dao.SPACE;
		};
	}

	/** Alchemy catalyst: the success bonus of a core of {@code grade}. */
	public static double catalystChance(int grade) {
		return grade < 1 || grade > 4 ? 0.0D : CATALYST_CHANCE[grade - 1];
	}

	/** Alchemy catalyst: the lowest pill quality a core of {@code grade} allows, or null. */
	public static @Nullable PillQuality qualityFloor(int grade) {
		return grade < 1 || grade > 4 ? null : QUALITY_FLOOR[grade - 1];
	}

	/** The XP absorbing a core of {@code grade} and {@code element} would give before multipliers and toxicity (pure). */
	public static double absorbXp(CultivationData data, int grade, @Nullable Element element) {
		int index = Math.max(1, Math.min(4, grade)) - 1;
		double xp = Math.min(data.rank().xpRequired() * XP_SHARE[index], XP_CAP[index]);
		if (element != null && data.rootTested() && (data.rootElements() & element.bit()) != 0) {
			xp *= ROOT_BONUS;
		}
		return xp;
	}

	// ================================================================ absorbing

	/** Sneak + use: XP, comprehension for the affine Dao, toxin. False (nothing used) when refused. */
	static boolean absorb(ServerPlayer player, ItemStack stack) {
		if (!isCore(stack) || player.getCooldowns().isOnCooldown(stack)) {
			return false;
		}
		CultivationData data = Cultivators.get(player);
		if (data.deviating()) {
			Msg.actionBar(player, Component.translatable("kultivator.harta.core.deviating").withStyle(ChatFormatting.RED));
			return false;
		}
		int grade = grade(stack);
		int index = grade - 1;
		Element element = element(stack);
		Component name = stack.getHoverName().copy();
		player.getCooldowns().addCooldown(stack, USE_COOLDOWN);
		stack.consume(1, player);

		double efficacy = Math.max(0.0D, Math.min(1.0D, PillToxicity.effectiveness(data)));
		double gained = Progression.grantXp(player, absorbXp(data, grade, element) * efficacy, XpSource.PILL);
		double insight = 0.0D;
		Dao chosen = data.dao();
		if (element != null && chosen != null && affinity(element) == chosen) {
			insight = Comprehension.grant(player, chosen, COMPREHENSION[index]);
		}
		Component line = insight > 0.0D
				? Component.translatable("kultivator.harta.core.absorbed_dao", name, Msg.number(Math.max(1.0D, gained)), Comprehension.format(insight),
				chosen.displayName())
				: Component.translatable("kultivator.harta.core.absorbed", name, Msg.number(Math.max(1.0D, gained)));
		Msg.actionBar(player, line.copy().withColor(element == null ? NEUTRAL_COLOR : element.color()));
		if (efficacy < 0.995D) {
			Msg.chat(player, Component.translatable("kultivator.harta.core.efficacy", Msg.number(efficacy * 100.0D + 1.0E-6D))
					.withStyle(ChatFormatting.GRAY));
		}
		ServerLevel level = player.level();
		Fx.send(level, player.position(), FxType.TREASURE, HartaFxIds.CORE_ABSORB, element == null ? -1 : element.ordinal(), grade);
		level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
		level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
				0.6F, 0.9F + 0.1F * grade);
		Cultivators.dirty(player);
		PillToxicity.add(player, TOXIN[index]);
		return true;
	}

	// ================================================================ feeding a spirit beast

	/**
	 * A core used on one of your spirit beasts (main hand). The client answers SUCCESS for any wolf or fox while a core is
	 * held, so the wolf's own sit toggle never runs for a core; the server decides.
	 */
	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity,
			@Nullable EntityHitResult hitResult) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!isCore(stack) || BeastData.kindOf(entity) < 0 || !(entity instanceof Mob mob) || !mob.isAlive()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		return feed(serverPlayer, mob, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	private static boolean feed(ServerPlayer player, Mob beast, ItemStack stack) {
		BeastData data = SpiritBeasts.data(beast);
		if (data == null) {
			Msg.actionBar(player, Component.translatable("kultivator.harta.core.not_beast").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!data.ownedBy(player)) {
			Msg.actionBar(player, Component.translatable("kultivator.harta.core.other_beast").withStyle(ChatFormatting.RED));
			return false;
		}
		long now = player.level().getGameTime();
		Long until = FED_UNTIL.get(beast.getUUID());
		if (until != null && until > now) {
			Msg.actionBar(player, Component.translatable("kultivator.harta.core.beast_full", (until - now + 19L) / 20L).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (FED_UNTIL.size() > 256) {
			FED_UNTIL.values().removeIf(time -> time <= now);
		}
		int grade = grade(stack);
		Element element = element(stack);
		boolean match = element != null && (data.kind() == BeastData.WOLF ? element == Element.LIGHTNING || element == Element.METAL
				: data.kind() == BeastData.FOX && (element == Element.FIRE || element == Element.WOOD));
		double xp = BEAST_XP[grade - 1] * (match ? BEAST_MATCH : 1.0D);
		Component name = stack.getHoverName().copy();
		stack.consume(1, player);
		FED_UNTIL.put(beast.getUUID(), now + FEED_COOLDOWN);
		SpiritBeasts.addXp(beast, xp);
		Msg.actionBar(player, Component.translatable(match ? "kultivator.harta.core.fed_match" : "kultivator.harta.core.fed", beast.getDisplayName(),
				name, Msg.number(xp)).withColor(element == null ? NEUTRAL_COLOR : element.color()));
		ServerLevel level = player.level();
		Fx.send(level, beast.position(), player.position(), FxType.TREASURE, HartaFxIds.CORE_FEED, element == null ? -1 : element.ordinal(), grade);
		level.playSound(null, beast.getX(), beast.getY() + 0.5D, beast.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 1.1F);
		level.playSound(null, beast.getX(), beast.getY() + 0.5D, beast.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.2F);
		return true;
	}

	/** Forgets the feeding cooldowns (server stop). */
	static void clear() {
		FED_UNTIL.clear();
	}

	/** Advancement checks for a core held by {@code player} (inventory scan). */
	static void checkAdvancements(ServerPlayer player, ItemStack stack) {
		Advancements.grant(player, "first_beast_core");
		if (grade(stack) >= KING) {
			Advancements.grant(player, "king_core");
		}
	}

	private BeastCores() {
	}
}
