package dev.kultivator.pusakalangit;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import org.jetbrains.annotations.Nullable;

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
				Titah.tick(server);
			} catch (RuntimeException exception) {
				dev.kultivator.Kultivator.LOGGER.error("Kultivator: Titah upkeep failed", exception);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			Titah.forget(player.getUUID());
			TitahMonuments.forgetFar(player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Titah.clearAll();
			TitahTests.clearAll();
			TitahMonuments.forgetFar(null);
		});
	}

	/** {@code Actions.TITAH}: {@code arg = op << 16 | skill << 8 | field}, {@code text} = the value (C10b.5). */
	public static void handleAction(ServerPlayer player, Payloads.Action action) {
		TitahSettings.handle(player, action.arg(), action.text());
	}

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		LiteralArgumentBuilder<CommandSourceStack> tier = Commands.literal("tingkat");
		com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> value = Commands.argument("tingkat",
				IntegerArgumentType.integer(1, TitahSettings.T_MAX));
		value.executes(ctx -> setTier(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "tingkat"), null));
		for (Titah.Skill skill : Titah.Skill.values()) {
			value.then(Commands.literal(skill.key())
					.executes(ctx -> setTier(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "tingkat"), skill)));
		}
		tier.then(value);
		root.then(Commands.literal("titah")
				.executes(ctx -> open(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("daftar").executes(ctx -> list(ctx.getSource().getPlayerOrException())))
				// Phase 10b Tingkat (DESIGN 29.16.3, 29.16.5)
				.then(tier)
				.then(Commands.literal("ya")
						.then(Commands.argument("kode", IntegerArgumentType.integer(0, 99999))
								.executes(ctx -> Titah.confirm(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "kode")))))
				.then(Commands.literal("batal").executes(ctx -> Titah.cancelConfirm(ctx.getSource().getPlayerOrException()))));
	}

	/**
	 * {@code /kultivasi titah tingkat <1-9> [decree]}: the Tingkat of the named decree (or the selected one), clamped to
	 * the player's limit (told when it was cut), answered with the readout.
	 */
	private static int setTier(ServerPlayer player, int wanted, Titah.@Nullable Skill named) {
		dev.kultivator.data.CultivationData data = dev.kultivator.data.Cultivators.get(player);
		Titah.Skill skill = named != null ? named : TitahSettings.selected(data);
		int stored = TitahSettings.setTier(player, skill, wanted);
		if (stored < wanted) {
			String why = Titah.tierWhy(data, Titah.bypass(player));
			Msg.chat(player, Component.translatable(KEY + "tier.clamped", wanted, stored, why.isEmpty() ? Component.empty() : Component.translatable(why))
					.withColor(0xFF8A7A));
		}
		Titah.Settings used = TitahSettings.read(player, skill);
		boolean perm = TitahSettings.permanent(used, dev.kultivator.kehancuran.Kehancuran.selfOn(player));
		TitahSettings.Readout readout = TitahSettings.readout(skill, used, dev.kultivator.KultivatorConfig.get(), perm);
		Msg.chat(player, Component.translatable(KEY + "tier.set", skill.displayName(), readout.component()).withStyle(ChatFormatting.GOLD));
		HeavenDecreeSealItem.refreshView(player);
		TitahSettings.sync(player);
		return stored;
	}

	/**
	 * {@code /kultivasi titah} and the hub's {@code TitahSettings.OP_OPEN} (DESIGN 33.7 C33.N4): the menu, when a seal of
	 * the player's is in the inventory.
	 */
	static int open(ServerPlayer player) {
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
