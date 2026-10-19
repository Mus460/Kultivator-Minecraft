package dev.kultivator.tempa;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.Element;
import dev.kultivator.harta.SpiritStones;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code uji pusaka|tempa|roh|cincin ...} test commands of P8-tempa (DESIGN 25.11): every forging, natal, spirit and ring
 * feature can be tried without grinding. Nothing here touches a block.
 */
final class TempaTests {

	private static final SuggestionProvider<CommandSourceStack> FORMS = (ctx, builder) -> SharedSuggestionProvider.suggest(ArtifactItem.FORMS, builder);

	static void add(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("pusaka")
				.then(Commands.literal("natal").executes(ctx -> NatalTreasureSelf.bindNow(Tempa.self(ctx)) ? 1 : 0))
				.then(Commands.argument("bentuk", StringArgumentType.word()).suggests(FORMS)
						.then(Commands.argument("tingkat", IntegerArgumentType.integer(0, 5))
								.executes(ctx -> artifact(ctx, 1, 0, 0, 0))
								.then(Commands.argument("sub", IntegerArgumentType.integer(0, 2))
										.executes(ctx -> artifact(ctx, IntegerArgumentType.getInteger(ctx, "sub"), 0, 0, 0))
										.then(Commands.argument("tempa", IntegerArgumentType.integer(0, 12))
												.executes(ctx -> artifact(ctx, IntegerArgumentType.getInteger(ctx, "sub"), IntegerArgumentType.getInteger(ctx, "tempa"), 0, 0))
												.then(Commands.argument("roh", IntegerArgumentType.integer(0, 3))
														.executes(ctx -> artifact(ctx, IntegerArgumentType.getInteger(ctx, "sub"), IntegerArgumentType.getInteger(ctx, "tempa"),
																IntegerArgumentType.getInteger(ctx, "roh"), 0))
														.then(Commands.argument("umur", IntegerArgumentType.integer(0, WeaponSpirit.MAX_YEARS))
																.executes(ctx -> artifact(ctx, IntegerArgumentType.getInteger(ctx, "sub"),
																		IntegerArgumentType.getInteger(ctx, "tempa"), IntegerArgumentType.getInteger(ctx, "roh"),
																		IntegerArgumentType.getInteger(ctx, "umur"))))))))));
		uji.then(Commands.literal("tempa")
				.then(Commands.literal("tingkat").then(Commands.argument("n", IntegerArgumentType.integer(0, 10)).executes(ctx -> {
					ServerPlayer player = Tempa.self(ctx);
					int level = IntegerArgumentType.getInteger(ctx, "n");
					Forging.setXp(player, Forging.xpFor(level));
					say(player, Component.translatable("kultivator.tempa.uji.skill", Forging.title(level)));
					Tempa.sync(player);
					return 1;
				})))
				.then(Commands.literal("panas").then(Commands.argument("n", IntegerArgumentType.integer(0, 5)).executes(ctx -> {
					ServerPlayer player = Tempa.self(ctx);
					int heat = IntegerArgumentType.getInteger(ctx, "n");
					Heat.pretend(player, heat);
					if (player.containerMenu instanceof ForgeMenu menu) {
						menu.setHeat(heat);
						menu.previewDirty = true;
					}
					say(player, Component.translatable("kultivator.tempa.uji.heat", Heat.name(heat), Heat.PRETEND_TICKS / 20));
					return 1;
				})))
				.then(Commands.literal("selesai").executes(ctx -> {
					ServerPlayer player = Tempa.self(ctx);
					boolean done = Forging.finishNow(player);
					say(player, Component.translatable(done ? "kultivator.tempa.uji.finished" : "kultivator.tempa.uji.no_job"));
					return done ? 1 : 0;
				})));
		uji.then(Commands.literal("roh")
				.then(Commands.literal("umur").then(Commands.argument("tahun", IntegerArgumentType.integer(0, WeaponSpirit.MAX_YEARS)).executes(ctx -> {
					ServerPlayer player = Tempa.self(ctx);
					boolean ok = WeaponSpirit.testYears(player, IntegerArgumentType.getInteger(ctx, "tahun"));
					say(player, Component.translatable(ok ? "kultivator.tempa.uji.years" : "kultivator.tempa.natal.none", IntegerArgumentType.getInteger(ctx, "tahun")));
					return ok ? 1 : 0;
				})))
				.then(Commands.argument("tahap", IntegerArgumentType.integer(0, 3)).executes(ctx -> {
					ServerPlayer player = Tempa.self(ctx);
					boolean ok = WeaponSpirit.testStage(player, IntegerArgumentType.getInteger(ctx, "tahap"));
					say(player, ok ? Component.translatable("kultivator.tempa.uji.stage", ArtifactItem.spiritName(IntegerArgumentType.getInteger(ctx, "tahap")))
							: Component.translatable("kultivator.tempa.natal.none"));
					return ok ? 1 : 0;
				})));
		uji.then(Commands.literal("cincin")
				.then(Commands.literal("buka").executes(ctx -> openAny(Tempa.self(ctx))))
				.then(Commands.argument("tier", IntegerArgumentType.integer(0, 4))
						.executes(ctx -> ring(ctx, false))
						.then(Commands.literal("penuh").executes(ctx -> ring(ctx, true)))));
	}

	private static void say(ServerPlayer player, Component message) {
		Msg.chat(player, message.copy().withStyle(ChatFormatting.AQUA));
	}

	/** {@code uji pusaka <bentuk> <tingkat> [sub] [tempa] [roh] [umur]}. */
	private static int artifact(CommandContext<CommandSourceStack> ctx, int sub, int refine, int stage, int years) throws CommandSyntaxException {
		ServerPlayer player = Tempa.self(ctx);
		String form = StringArgumentType.getString(ctx, "bentuk");
		if (!ArtifactItem.FORMS.contains(form)) {
			say(player, Component.translatable("kultivator.tempa.uji.bad_form", String.join(", ", ArtifactItem.FORMS)));
			return 0;
		}
		int grade = IntegerArgumentType.getInteger(ctx, "tingkat");
		Element[] elements = Element.values();
		Element element = elements[player.getRandom().nextInt(elements.length)];
		ItemStack stack = ArtifactItem.create(form, grade, sub, refine, element, player.getName().getString());
		if (stage > 0 || years > 0) {
			CompoundTag art = ArtifactItem.data(stack);
			CompoundTag spirit = new CompoundTag();
			spirit.putInt("stage", stage);
			spirit.putDouble("years", years);
			spirit.putInt("kind", 0);
			art.put("spirit", spirit);
			ArtifactItem.write(stack, art);
		}
		Component name = stack.getHoverName().copy();
		Inv.give(player, stack);
		say(player, Component.translatable("kultivator.tempa.uji.given", name));
		return 1;
	}

	/** {@code uji cincin <0-4> [penuh]}: a ring of that tier (optionally filled with stones). */
	private static int ring(CommandContext<CommandSourceStack> ctx, boolean full) throws CommandSyntaxException {
		ServerPlayer player = Tempa.self(ctx);
		int tier = IntegerArgumentType.getInteger(ctx, "tier");
		ItemStack ring = StorageRingItem.create(tier, null);
		if (full) {
			List<ItemStack> contents = new ArrayList<>();
			for (int i = 0; i < StorageRingItem.slots(ring); i++) {
				contents.add(SpiritStones.stack(SpiritStones.LOW, 64));
			}
			ring.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
		}
		Component name = ring.getHoverName().copy();
		Inv.give(player, ring);
		say(player, Component.translatable("kultivator.tempa.uji.given", name));
		return 1;
	}

	/** {@code uji cincin buka}: opens the ring in the main hand (or the best one carried), ignoring its imprint. */
	private static int openAny(ServerPlayer player) {
		if (StorageRingItem.is(player.getMainHandItem())) {
			return StorageRingItem.open(player, player.getInventory().getSelectedSlot(), true) ? 1 : 0;
		}
		return StorageRingItem.openBest(player, true) ? 1 : 0;
	}

	private TempaTests() {
	}
}
