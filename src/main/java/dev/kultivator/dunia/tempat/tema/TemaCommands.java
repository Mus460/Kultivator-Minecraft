package dev.kultivator.dunia.tempat.tema;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The themed-town test root {@code /kultivasi uji tema} (DESIGN 38.T.13.2; op, through {@code DuniaCommands.test};
 * chip {@code tema} is 38W's). Theme agents and 38TK inti attach their sub-nodes with {@link #daftar} from their own init
 * and never edit this class: e.g. 38TK-P {@code daftar("pelabuhan", () -> Commands.literal("kapal")...)}, 38TK-G
 * {@code lift}, 38TK-O {@code unta}, 38TK-A {@code perahu}, 38TK-I {@code arena}, {@code taruhan}, {@code infami},
 * {@code gentar}, {@code buronan}, {@code pemburu}; inti {@code rencana}, {@code putusan}, {@code cari}, {@code distrik},
 * {@code lot}, {@code cek}, {@code acara}, {@code dagang}, {@code kontrak}, {@code alur}, {@code model}, {@code fx},
 * {@code kepadatan}. A literal registered twice keeps both (brigadier merges children of the same literal).
 * <p>
 * {@code uji tema} alone lists the registered sub-commands as clickable chips. Every test works while the behaviour is
 * switched off (previews) and none of them touches a block, the weather or the time.
 */
public final class TemaCommands {

	private record Simpul(String tema, Supplier<LiteralArgumentBuilder<CommandSourceStack>> node) {
	}

	private static final Map<String, Simpul> NODES = new LinkedHashMap<>();
	private static boolean initialised;

	/** Registers the root with {@code DuniaCommands}; from {@link KotaTema#init()}. */
	static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		DuniaCommands.test("tema", TemaCommands::root);
	}

	/**
	 * Attaches a sub-node under {@code uji tema}. {@code tema} is the owning theme id (or {@code inti}) for the help list;
	 * the node's own literal is the sub-command word.
	 */
	public static void daftar(String tema, Supplier<LiteralArgumentBuilder<CommandSourceStack>> node) {
		if (tema != null && node != null) {
			NODES.put(tema + "#" + NODES.size(), new Simpul(tema, node));
		}
	}

	private static LiteralArgumentBuilder<CommandSourceStack> root() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tema")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> help(ctx.getSource()));
		for (Map.Entry<String, Simpul> entry : NODES.entrySet()) {
			try {
				LiteralArgumentBuilder<CommandSourceStack> node = entry.getValue().node().get();
				if (node != null) {
					root.then(node);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Themed test command {} could not be built", entry.getKey(), exception);
			}
		}
		return root;
	}

	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.tema.cmd.help.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		List<String> words = new ArrayList<>();
		Map<String, List<String>> byTheme = new LinkedHashMap<>();
		for (Simpul simpul : NODES.values()) {
			try {
				LiteralArgumentBuilder<CommandSourceStack> node = simpul.node().get();
				if (node != null && !words.contains(node.getLiteral())) {
					words.add(node.getLiteral());
					byTheme.computeIfAbsent(simpul.tema(), k -> new ArrayList<>()).add(node.getLiteral());
				}
			} catch (RuntimeException ignored) {
				// reported when the tree was built
			}
		}
		if (byTheme.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("kultivator.tema.cmd.help.none").withStyle(ChatFormatting.GRAY), false);
			return 1;
		}
		for (Map.Entry<String, List<String>> entry : byTheme.entrySet()) {
			MutableComponent line = Component.literal(entry.getKey() + ": ").withStyle(ChatFormatting.GRAY);
			for (String word : entry.getValue()) {
				String command = "/kultivasi uji tema " + word + " ";
				line.append(Component.literal("[" + word + "] ").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
						.withClickEvent(new ClickEvent.SuggestCommand(command))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))));
			}
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	private TemaCommands() {
	}
}
