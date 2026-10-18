package dev.kultivator.technique.source;

import com.mojang.serialization.Codec;
import dev.kultivator.Kultivator;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.data.Realm;
import dev.kultivator.data.TechniqueGrade;
import dev.kultivator.data.TechniqueRegistry;
import dev.kultivator.data.TechniqueSpec;
import dev.kultivator.dao.DaoPaths;
import dev.kultivator.dao.TechniqueBooks;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.DiscipleHooks;
import dev.kultivator.disciple.path.DisciplePaths;
import dev.kultivator.fx.Fx;
import dev.kultivator.fx.FxType;
import dev.kultivator.registry.ModItems;
import dev.kultivator.technique.spec.run.Mastery;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Slip Giok, the jade slip that holds one technique (DESIGN 22.8.3; item {@code jade_slip}, component
 * {@code kultivator:technique} = the spec id). Right-click learns it when every gate allows ("Isi slip ini terlalu dalam
 * untukmu" plus the missing gates otherwise); a known art gives +25 mastery instead. Used on one's own disciple it
 * teaches without master qi under the disciple's gates (22.13.2), or gives that disciple +25 mastery. Dewa,
 * disciple-only and fusion-pair specs never sit in a slip.
 * <p>
 * Sources: <i>A Record of a Mortal's Journey</i>: jade slips store arts, are read by spiritual sense, traded and gifted
 * (ch. 1269); Han Li declines an art that contradicts his cultivation (ch. 1782).
 */
public final class JadeSlips {

	/** Mastery points a slip of an already known art gives (22.6). */
	public static final int MASTERY_DUPLICATE = 25;
	private static final String PREFIX = "kultivator.sumber.slip.";

	/** {@code kultivator:technique}: the spec id a slip holds (saved and synced). */
	public static final DataComponentType<String> TECHNIQUE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Kultivator.id("technique"), DataComponentType.<String>builder()
					.persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.STRING_UTF8)
					.build());

	public static final Item JADE_SLIP = ModItems.register("jade_slip", SlipItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.RARE));

	/** Player -> game time a disciple took a slip from their hand (the item use of the same click is then skipped). */
	private static final Map<UUID, Long> FED = new HashMap<>();

	public static void init() {
		DiscipleHooks.addFeedHandler(JadeSlips::feed);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FED.clear());
	}

	// ================================================================== the stack

	/** A slip holding {@code specId}; empty for unknown specs and for specs no slip may hold (Dewa, disciple-only, pair). */
	public static ItemStack stack(String specId) {
		TechniqueSpec spec = TechniqueRegistry.byId(specId);
		if (spec == null || !spec.slipAllowed()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(JADE_SLIP);
		stack.set(TECHNIQUE, spec.id());
		stack.set(DataComponents.RARITY, rarity(spec.grade()));
		return stack;
	}

	/** The spec id a slip holds, or null. */
	public static @Nullable String specOf(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(JADE_SLIP)) {
			return null;
		}
		String id = stack.get(TECHNIQUE);
		return id == null || id.isEmpty() ? null : id;
	}

	/** The spec a slip holds, or null when empty or unknown. */
	public static @Nullable TechniqueSpec specOfStack(ItemStack stack) {
		String id = specOf(stack);
		return id == null ? null : TechniqueRegistry.byId(id);
	}

	private static Rarity rarity(TechniqueGrade grade) {
		return switch (grade) {
			case HUANG -> Rarity.COMMON;
			case XUAN -> Rarity.UNCOMMON;
			case DI -> Rarity.RARE;
			default -> Rarity.EPIC;
		};
	}

	// ================================================================== reading it yourself

	private static InteractionResult read(ServerPlayer player, ItemStack stack) {
		Long fed = FED.get(player.getUUID());
		if (fed != null && player.level().getGameTime() - fed <= 1L) {
			// The same click already went to the master's own disciple.
			return InteractionResult.PASS;
		}
		TechniqueSpec spec = specOfStack(stack);
		if (spec == null) {
			Msg.chat(player, Component.translatable(PREFIX + "blank").withStyle(ChatFormatting.GRAY));
			return InteractionResult.PASS;
		}
		if (!spec.slipAllowed()) {
			Msg.chat(player, Component.translatable(PREFIX + "sealed", LearnRules.name(spec)).withStyle(ChatFormatting.GRAY));
			return InteractionResult.PASS;
		}
		CultivationData data = Cultivators.get(player);
		if (data.knows(spec.id())) {
			consume(player, stack);
			Mastery.add(player, spec.id(), MASTERY_DUPLICATE, Mastery.Source.SLIP);
			Msg.chat(player, Component.translatable(PREFIX + "known", LearnRules.name(spec), MASTERY_DUPLICATE).withStyle(ChatFormatting.YELLOW));
			sound(player.level(), player.position(), 1.3F);
			Cultivators.syncNow(player);
			return InteractionResult.SUCCESS;
		}
		List<Component> missing = LearnRules.missing(data, spec);
		if (!missing.isEmpty()) {
			// Han Li declines an art that does not fit his cultivation: the slip stays whole.
			Msg.chat(player, Component.translatable(PREFIX + "too_deep").withStyle(ChatFormatting.GOLD));
			Msg.chat(player, Component.translatable(PREFIX + "missing", LearnRules.name(spec), DaoPaths.join(missing)).withStyle(ChatFormatting.GRAY));
			player.level().playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(), SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.PLAYERS,
					0.8F, 0.6F);
			return InteractionResult.PASS;
		}
		consume(player, stack);
		LearnRules.learn(player, spec, "slip");
		Advancements.grant(player, "jade_slip");
		return InteractionResult.SUCCESS;
	}

	// ================================================================== giving it to a disciple

	/** The master right-clicks their own disciple with a slip: it teaches without master qi (22.13.2). */
	private static boolean feed(DiscipleEntity entity, ServerPlayer master, ItemStack stack) {
		if (!stack.is(JADE_SLIP)) {
			return false;
		}
		FED.put(master.getUUID(), master.level().getGameTime());
		TechniqueSpec spec = specOfStack(stack);
		Component name = entity.getDisplayName();
		if (spec == null) {
			Msg.chat(master, Component.translatable(PREFIX + "blank").withStyle(ChatFormatting.GRAY));
			return true;
		}
		DiscipleData data = entity.data();
		if (data.knows(spec.id())) {
			consume(master, stack);
			Mastery.addDisciple(data, spec.id(), MASTERY_DUPLICATE);
			Msg.chat(master, Component.translatable(PREFIX + "disciple_known", name, LearnRules.name(spec), MASTERY_DUPLICATE)
					.withStyle(ChatFormatting.AQUA));
			sound(entity.level(), entity.position(), 1.3F);
			return true;
		}
		Component why = discipleBlocker(entity, spec);
		if (why != null) {
			Msg.chat(master, Component.translatable(PREFIX + "disciple_refuse", name, LearnRules.name(spec), why).withColor(0xFF9A7A));
			return true;
		}
		consume(master, stack);
		data.learn(spec.id());
		if (entity.level() instanceof ServerLevel level) {
			Vec3 from = master.position().add(0.0D, 1.2D, 0.0D);
			Vec3 to = entity.position().add(0.0D, 1.2D, 0.0D);
			if (master.level() == level) {
				Fx.beam(level, from, to, LearnRules.color(spec), 0.12F);
			}
			Fx.send(level, entity.position(), FxType.DAO_CHOSEN, spec.dao() == null ? -1 : spec.dao().ordinal(), TechniqueBooks.FX_LEARNED, 0.0F);
			sound(level, entity.position(), 1.1F);
		}
		Msg.chat(master, Component.translatable(PREFIX + "disciple_learned", name, LearnRules.name(spec), LearnRules.gradeLine(spec))
				.withStyle(ChatFormatting.AQUA));
		Advancements.grant(master, "disciple_taught");
		return true;
	}

	/**
	 * Why the disciple cannot take this art from a slip (null when it can): never-teachable arts, the realm, the Dao (or
	 * a Dao bypass), comprehension, the star gate (Y★n, Dewa stars), Abadi arts below Dewa Abadi, a running breakthrough.
	 */
	public static @Nullable Component discipleBlocker(DiscipleEntity entity, TechniqueSpec spec) {
		DiscipleData data = entity.data();
		if (!spec.slipAllowed() || !spec.teachable()) {
			return Component.translatable(PREFIX + "why.not_teachable");
		}
		if (data.rank().step() < spec.minRank().step()) {
			return Component.translatable(PREFIX + "why.realm", spec.minRank().displayName().copy().withColor(spec.minRealm().color()));
		}
		if (spec.grade() == TechniqueGrade.ABADI && !data.realm().atLeast(Realm.TRUE_IMMORTAL)) {
			return Component.translatable(PREFIX + "why.realm", Realm.TRUE_IMMORTAL.displayName().copy().withColor(Realm.TRUE_IMMORTAL.color()));
		}
		Dao dao = spec.dao();
		if (dao != null && dao != data.dao() && !DiscipleHooks.daoBypass(data)) {
			return Component.translatable(PREFIX + "why.dao", DaoPaths.colored(dao));
		}
		if (spec.minComprehension() > 0.0D && data.comprehension() + 1.0E-9D < spec.minComprehension()) {
			return Component.translatable(PREFIX + "why.comprehension", LearnRules.format(spec.minComprehension()),
					LearnRules.format(data.comprehension()));
		}
		int stars = DisciplePaths.stars(data);
		int needStars = Math.max(spec.teachStars(), spec.dewa() ? spec.dewaStars() : 0);
		if (needStars > 0 && stars < needStars) {
			return Component.translatable(PREFIX + "why.stars", needStars, stars);
		}
		if (data.breakingThrough()) {
			return Component.translatable(PREFIX + "why.busy");
		}
		return null;
	}

	// ================================================================== helpers

	private static void consume(Player player, ItemStack stack) {
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
	}

	private static void sound(Level level, Vec3 at, float pitch) {
		level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, pitch);
		level.playSound(null, at.x, at.y + 1.0D, at.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	// ================================================================== the item

	/** The slip: its art's name, grade, type and Sumber in the tooltip; right-click reads it. */
	public static class SlipItem extends Item {

		public SlipItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			try {
				return read(serverPlayer, player.getItemInHand(hand));
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Could not read the jade slip", exception);
				return InteractionResult.PASS;
			}
		}

		@Override
		public Component getName(ItemStack stack) {
			String id = specOf(stack);
			if (id == null) {
				return super.getName(stack);
			}
			return Component.translatable("item.kultivator.jade_slip.named", Component.translatable("kultivator.technique." + id));
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
				TooltipFlag flag) {
			TechniqueSpec spec = specOfStack(stack);
			if (spec == null) {
				builder.accept(Component.translatable(PREFIX + "tooltip.blank").withStyle(ChatFormatting.GRAY));
			} else {
				builder.accept(LearnRules.gradeLine(spec));
				builder.accept(spec.source().copy().withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				builder.accept(Component.translatable(PREFIX + "tooltip.use").withStyle(ChatFormatting.GRAY));
				builder.accept(Component.translatable(PREFIX + "tooltip.disciple").withStyle(ChatFormatting.DARK_GRAY));
			}
			builder.accept(Component.translatable(PREFIX + "tooltip.source").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		}
	}

	private JadeSlips() {
	}
}
