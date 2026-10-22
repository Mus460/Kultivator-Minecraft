package dev.kultivator.sekte;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The commands of Sekte yang Hidup (DESIGN 36.3.4), registered in their own {@code CommandRegistrationCallback} phase so
 * nothing edits {@code command/CultivateCommand} (Brigadier merges the {@code uji} literal with CultivateCommand's, the
 * {@code DuniaCommands} pattern):
 * <ul>
 * <li>{@code /kultivasi uji perguruan} (op): the grouped help (every module's chips), {@code muat} (reload
 * {@code config/kultivator-sekte.json}), {@code modul} (which module services are live), then one sub-tree per module,
 * {@code uji perguruan <module id>} ({@link SekteIds#MODULES}), added with {@link #test}.</li>
 * <li>{@code /kultivasi perguruan} (everyone): a short help, then the player sub-commands of the modules, added with
 * {@link #player} (for example {@code perguruan murid <nama>} of P, {@code perguruan lambang} of S).</li>
 * </ul>
 * Both roots ({@code kultivasi}, {@code cultivate}) get the same nodes. A node supplier that throws is logged and
 * skipped. {@link #CHIPS} feeds the {@code perguruan} group of {@code /kultivasi uji} ({@code command/CheatHelp}); each
 * module adds its chips with {@link #chips} from its init, text starting with {@code "perguruan <module> "}.
 */
public final class SekteCommands {

	private static final Identifier PHASE = Kultivator.id("sekte_commands");
	private static final Map<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> TESTS = new LinkedHashMap<>();
	private static final Map<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> PLAYERS = new LinkedHashMap<>();
	private static boolean registered;

	/** Chips of {@code /kultivasi uji} (group {@code perguruan}); live list, modules append from their init. */
	public static final List<String> CHIPS = new CopyOnWriteArrayList<>(List.of("perguruan", "perguruan muat", "perguruan modul"));

	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
		CommandRegistrationCallback.EVENT.register(PHASE, (dispatcher, buildContext, selection) -> register(dispatcher));
	}

	/**
	 * An op-only test sub-tree {@code uji perguruan <literal>}; {@code literal} should be the module id
	 * ({@link SekteIds#MODULES}) so the trees never collide.
	 */
	public static void test(String literal, Supplier<LiteralArgumentBuilder<CommandSourceStack>> node) {
		if (literal != null && node != null) {
			TESTS.put(literal + "#" + TESTS.size(), node);
		}
	}

	/** A player sub-command {@code /kultivasi perguruan <literal>}. */
	public static void player(String literal, Supplier<LiteralArgumentBuilder<CommandSourceStack>> node) {
		if (literal != null && node != null) {
			PLAYERS.put(literal + "#" + PLAYERS.size(), node);
		}
	}

	/** Adds chips to the {@code perguruan} group of {@code /kultivasi uji} (each starting with {@code "perguruan "}). */
	public static void chips(String... chips) {
		if (chips != null) {
			CHIPS.addAll(List.of(chips));
		}
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		for (String root : new String[]{"kultivasi", "cultivate"}) {
			LiteralArgumentBuilder<CommandSourceStack> perguruanUji = Commands.literal("perguruan")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(ctx -> help(ctx.getSource(), root))
					.then(Commands.literal("muat").executes(ctx -> reload(ctx.getSource())))
					.then(Commands.literal("modul").executes(ctx -> modules(ctx.getSource())));
			for (Map.Entry<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> entry : TESTS.entrySet()) {
				try {
					LiteralArgumentBuilder<CommandSourceStack> node = entry.getValue().get();
					if (node != null) {
						perguruanUji.then(node.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)));
					}
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Sekte test command {} could not be built", entry.getKey(), exception);
				}
			}
			LiteralArgumentBuilder<CommandSourceStack> uji = Commands.literal("uji").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(perguruanUji);
			LiteralArgumentBuilder<CommandSourceStack> perguruan = Commands.literal("perguruan").executes(ctx -> playerHelp(ctx.getSource()));
			for (Map.Entry<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> entry : PLAYERS.entrySet()) {
				try {
					LiteralArgumentBuilder<CommandSourceStack> node = entry.getValue().get();
					if (node != null) {
						perguruan.then(node);
					}
				} catch (RuntimeException exception) {
					Kultivator.LOGGER.error("Sekte player command {} could not be built", entry.getKey(), exception);
				}
			}
			dispatcher.register(Commands.literal(root).then(uji).then(perguruan));
		}
	}

	/** {@code uji perguruan}: every chip of this phase, clickable (six per line). */
	private static int help(CommandSourceStack source, String root) {
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.cmd.help.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.cmd.help.intro").withStyle(ChatFormatting.GRAY), false);
		MutableComponent line = Component.literal(" ");
		int inLine = 0;
		for (String chip : CHIPS) {
			if (inLine > 0) {
				line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(chip(root, chip));
			if (++inLine >= 6) {
				MutableComponent done = line;
				source.sendSuccess(() -> done, false);
				line = Component.literal(" ");
				inLine = 0;
			}
		}
		if (inLine > 0) {
			MutableComponent done = line;
			source.sendSuccess(() -> done, false);
		}
		return 1;
	}

	private static Component chip(String root, String chip) {
		int cut = chip.length();
		for (int i = 0; i < chip.length(); i++) {
			char c = chip.charAt(i);
			if (c == '<' || c == '[') {
				cut = i;
				break;
			}
		}
		String command = "/" + root + " uji " + chip.substring(0, cut).trim() + (cut < chip.length() ? " " : "");
		return Component.literal(chip).withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("/" + root + " uji " + chip))));
	}

	private static int reload(CommandSourceStack source) {
		boolean ok = SekteConfig.reload();
		source.sendSuccess(() -> Component.translatable(ok ? "kultivator.sekte.cmd.reload.ok" : "kultivator.sekte.cmd.reload.fail",
				SekteConfig.file().getFileName().toString()).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), true);
		return ok ? 1 : 0;
	}

	/** {@code uji perguruan modul}: one line per module, live (service registered) or missing. */
	private static int modules(CommandSourceStack source) {
		List<String> live = new ArrayList<>(dev.kultivator.sekte.api.SekteApi.liveModules());
		for (String module : SekteIds.MODULES) {
			boolean on = live.contains(module);
			source.sendSuccess(() -> Component.translatable("kultivator.sekte.cmd.module", module,
					Component.translatable(on ? "kultivator.sekte.cmd.module.on" : "kultivator.sekte.cmd.module.off"))
					.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
		}
		return live.size();
	}

	private static int playerHelp(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.cmd.player.help").withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private SekteCommands() {
	}
}
