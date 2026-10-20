package dev.kultivator.pusakalangit;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.network.Payloads;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/**
 * Phase 10b "Pusaka Langit" (in game: Titah Langit, DESIGN 29): the module's entry. {@link #init} registers the seal, the
 * monument blocks and entity ({@link TitahItems}), the monument registry's upkeep and the session cleanup;
 * {@link #handleAction} answers {@code Actions.TITAH} (the jade-scroll menu, C10b.5); {@link #addCommands} adds
 * {@code /kultivasi titah} and {@code /kultivasi titah daftar}. The decrees are registered by {@code TitahArts} in the
 * technique registry. Server code only; the client side lives in {@code client.pusakalangit}.
 */
public final class PusakaLangit {

	private static final String KEY = "kultivator.pusaka_langit.";

	public static void init() {
		TitahItems.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
				TitahMonuments.tick(server);
				TitahTests.tick(server);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: Titah upkeep failed", exception);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> Titah.forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Titah.clearAll();
			TitahTests.clearAll();
		});
	}

	/** {@code Actions.TITAH}: {@code arg = op << 16 | skill << 8 | field}, {@code text} = the value (C10b.5). */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		TitahSettings.handle(player, action.arg(), action.text());
	}

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("titah")
				.executes(ctx -> open(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("daftar").executes(ctx -> list(ctx.getSource().getPlayerOrException()))));
	}

	/** {@code /kultivasi titah}: the menu, when a seal of the player's is in the inventory. */
	private static int open(ServerPlayer player) {
		if (!HeavenDecreeSealItem.carries(player)) {
			Msg.actionBar(player, Component.translatable(KEY + "menu.no_seal").withColor(0xFF8A7A));
			return 0;
		}
		HeavenDecreeSealItem.openMenu(player);
		return 1;
	}

	/** {@code /kultivasi titah daftar}: the player's planted weapons with their coordinates and days left. */
	private static int list(ServerPlayer player) {
		List<TitahMonuments.View> mine = TitahMonuments.list(player.level().getServer(), player.getUUID());
		if (mine.isEmpty()) {
			Msg.chat(player, Component.translatable(KEY + "list.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Msg.chat(player, Component.translatable(KEY + "list.head", mine.size()).withStyle(ChatFormatting.GOLD));
		for (TitahMonuments.View view : mine) {
			Msg.chat(player, line(view));
		}
		return mine.size();
	}

	/** One list line: model, place, days left, blocks held. */
	static Component line(TitahMonuments.View view) {
		Component model = Component.translatable(KEY + "model." + Math.max(0, Math.min(3, view.model())));
		return Component.translatable(KEY + "list.line", view.id(), model, view.pos().getX(), view.pos().getY(), view.pos().getZ(), view.dimension(),
				String.format(Locale.ROOT, "%.1f", view.daysLeft()), view.blocks(),
				view.revoked() ? Component.translatable(KEY + "list.revoked") : view.test() ? Component.translatable(KEY + "list.test")
						: Component.empty()).withStyle(ChatFormatting.GRAY);
	}

	private PusakaLangit() {
	}
}
