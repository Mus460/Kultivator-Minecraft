package dev.kultivator.musuh;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Element;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The module's test commands (DESIGN 25.11): {@code uji perampok <1-4> [n] [unsur] [varian]}, {@code uji perampok hapus},
 * {@code uji sergap} and {@code uji serbuan peringatan|mulai [gelombang]|menang|kalah|segel <0-100>|data|jadwal}.
 */
final class MusuhTests {

	private static final String[] ELEMENT_WORDS = {"logam", "kayu", "air", "api", "tanah", "petir", "es", "angin", "ruang"};
	private static final String[] VARIANT_WORDS = {"liar", "hantu", "energi", "iblis"};
	private static final SuggestionProvider<CommandSourceStack> ELEMENTS = (ctx, builder) -> {
		List<String> words = new ArrayList<>(List.of(ELEMENT_WORDS));
		words.add("acak");
		return SharedSuggestionProvider.suggest(words, builder);
	};
	private static final SuggestionProvider<CommandSourceStack> VARIANTS = (ctx, builder) -> SharedSuggestionProvider.suggest(VARIANT_WORDS, builder);

	static void add(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("perampok")
				.then(Commands.literal("hapus").executes(ctx -> remove(ctx.getSource().getPlayerOrException())))
				.then(Commands.argument("tingkat", IntegerArgumentType.integer(1, 4))
						.executes(ctx -> spawn(ctx, 1, null, null))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 8))
								.executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "n"), null, null))
								.then(Commands.argument("unsur", StringArgumentType.word()).suggests(ELEMENTS)
										.executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "n"), StringArgumentType.getString(ctx, "unsur"), null))
										.then(Commands.argument("varian", StringArgumentType.word()).suggests(VARIANTS)
												.executes(ctx -> spawn(ctx, IntegerArgumentType.getInteger(ctx, "n"), StringArgumentType.getString(ctx, "unsur"),
														StringArgumentType.getString(ctx, "varian"))))))));
		uji.then(Commands.literal("sergap").executes(ctx -> ambush(ctx.getSource().getPlayerOrException())));
		uji.then(Commands.literal("serbuan")
				.then(Commands.literal("peringatan").executes(ctx -> result(ctx, SectRaids.testOmen(ctx.getSource().getPlayerOrException()), "omen")))
				.then(Commands.literal("mulai")
						.executes(ctx -> result(ctx, SectRaids.testStart(ctx.getSource().getPlayerOrException(), 1), "start"))
						.then(Commands.argument("gelombang", IntegerArgumentType.integer(1, 5))
								.executes(ctx -> result(ctx, SectRaids.testStart(ctx.getSource().getPlayerOrException(),
										IntegerArgumentType.getInteger(ctx, "gelombang")), "start"))))
				.then(Commands.literal("menang").executes(ctx -> result(ctx, SectRaids.testForce(ctx.getSource().getPlayerOrException(), true), "win")))
				.then(Commands.literal("kalah").executes(ctx -> result(ctx, SectRaids.testForce(ctx.getSource().getPlayerOrException(), false), "lose")))
				.then(Commands.literal("segel")
						.then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100))
								.executes(ctx -> result(ctx, SectRaids.testMeter(ctx.getSource().getPlayerOrException(),
										IntegerArgumentType.getInteger(ctx, "nilai")), "meter"))))
				.then(Commands.literal("data").executes(ctx -> result(ctx, SectRaids.testData(ctx.getSource().getPlayerOrException()), "data")))
				.then(Commands.literal("jadwal").executes(ctx -> schedule(ctx.getSource().getPlayerOrException()))));
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx, int count, @Nullable String elementWord, @Nullable String variantWord)
			throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		int tier = IntegerArgumentType.getInteger(ctx, "tingkat");
		int sect = -1;
		if (elementWord != null && !elementWord.equalsIgnoreCase("acak")) {
			Element element = parseElement(elementWord);
			if (element == null) {
				return fail(player, "kultivator.musuh.test.bad_element", elementWord);
			}
			sect = RogueCultivatorEntity.sectOf(element);
		}
		int variant = RogueCultivators.VARIANT_ROGUE;
		if (variantWord != null) {
			variant = List.of(VARIANT_WORDS).indexOf(variantWord.toLowerCase(Locale.ROOT));
			if (variant < 0) {
				return fail(player, "kultivator.musuh.test.bad_variant", variantWord);
			}
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		Vec3 centre = player.position().add(flat.scale(6.0D));
		int[] tiers = new int[count];
		java.util.Arrays.fill(tiers, tier);
		int realm = Cultivators.get(player).realm().ordinal();
		RogueCultivators.Band band = RogueCultivators.band(level, centre, tiers, realm, RogueCultivators.ROLE_TEST, null, sect, variant, cleared -> {
		});
		if (band == null) {
			return fail(player, "kultivator.musuh.test.no_spawn", "");
		}
		RogueCultivatorEntity first = band.mobs.get(0);
		Msg.chat(player, Component.translatable("kultivator.musuh.test.spawned", band.mobs.size(), first.plate(), Msg.number(first.getMaxHealth()),
				Msg.number(RogueSkills.skillDamage(first))).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable("kultivator.musuh.tier." + tier + ".source").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		return band.mobs.size();
	}

	private static int remove(ServerPlayer player) {
		int removed = 0;
		for (RogueCultivatorEntity mob : RogueCultivators.near(player.level(), player.position(), 64.0D)) {
			mob.vanish(false);
			removed++;
		}
		Msg.chat(player, Component.translatable("kultivator.musuh.test.removed", removed).withStyle(ChatFormatting.GRAY));
		return removed;
	}

	private static int ambush(ServerPlayer player) {
		String refusal = Ambushes.roll(player, true);
		if (refusal != null) {
			return fail(player, refusal, "");
		}
		Msg.chat(player, Component.translatable("kultivator.musuh.test.ambush").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int result(CommandContext<CommandSourceStack> ctx, boolean ok, String what) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		if (!ok) {
			return fail(player, "kultivator.musuh.test.raid_refused", what);
		}
		Msg.chat(player, Component.translatable("kultivator.musuh.test.raid." + what).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int schedule(ServerPlayer player) {
		CompoundTag info = SectRaids.infoTag(player.getUUID());
		if (!info.getBooleanOr("has_hall", false)) {
			return fail(player, "kultivator.musuh.info.no_hall", "");
		}
		CompoundTag section = Halls.section(player.getUUID(), SectRaids.SECTION);
		Msg.chat(player, Component.translatable("kultivator.musuh.test.schedule", info.getLongOr("today", 0L), section.getLongOr("next_day", -1L),
				section.getIntOr("state", 0), section.getIntOr("sect", -1), section.getIntOr("count", 0), info.getIntOr("interval", 0),
				info.getBooleanOr("alarm", false) ? "+" : "-", info.getIntOr("score", 0), info.getIntOr("lr", 1), info.getIntOr("chance", 10))
				.withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static @Nullable Element parseElement(String word) {
		String lower = word.toLowerCase(Locale.ROOT);
		for (int i = 0; i < ELEMENT_WORDS.length; i++) {
			if (ELEMENT_WORDS[i].equals(lower)) {
				return Element.values()[i];
			}
		}
		return Element.byId(lower);
	}

	private static int fail(ServerPlayer player, String key, String arg) {
		Msg.chat(player, Component.translatable(key, arg).withStyle(ChatFormatting.RED));
		return 0;
	}

	private MusuhTests() {
	}
}
