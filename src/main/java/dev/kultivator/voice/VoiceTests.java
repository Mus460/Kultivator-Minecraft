package dev.kultivator.voice;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.network.Net;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * {@code /cultivate uji suara <key>} (op, under the existing {@code uji} literal): plays one voice line to the player at
 * once, without any cooldown; worded non-positional groups (heaven, demon, boss, elder) come from the sky, the others from
 * the player's own position. {@code uji suara} alone lists the groups.
 */
public final class VoiceTests {

	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("suara")
				.executes(ctx -> {
					Msg.chat(ctx.getSource().getPlayerOrException(), Component.translatable("kultivator.voice.test.usage").withStyle(ChatFormatting.GRAY));
					return 1;
				})
				.then(Commands.argument("key", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(VoiceEvents.keys(), builder))
						.executes(VoiceTests::play)));
	}

	private static int play(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String key = VoiceEvents.normalize(StringArgumentType.getString(ctx, "key"));
		if (!VoiceEvents.exists(key)) {
			Msg.chat(player, Component.translatable("kultivator.voice.test.unknown", key).withStyle(ChatFormatting.RED));
			return 0;
		}
		int kind = Voice.kindOf(key);
		boolean sky = kind == VoicePayload.KIND_HEAVEN || kind == VoicePayload.KIND_DEMON || kind == VoicePayload.KIND_BOSS
				|| kind == VoicePayload.KIND_ELDER;
		String caption = VoiceEvents.captionOf(key);
		Vec3 at = player.getEyePosition();
		Net.send(player, new VoicePayload("voice." + key, caption == null ? "" : caption, sky ? Optional.empty() : Optional.of(player.getDisplayName()),
				kind, !sky, sky ? -1 : player.getId(), at.x, at.y, at.z, sky ? 1.0F : 1.5F, 1.0F));
		Msg.chat(player, Component.translatable("kultivator.voice.test.played", "voice." + key).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private VoiceTests() {
	}
}
