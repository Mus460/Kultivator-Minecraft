package dev.kultivator.sekte.mandiri;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.Kultivator;
import dev.kultivator.sekte.SekteCommands;
import dev.kultivator.sekte.mandiri.api.GildaMurid;
import dev.kultivator.sekte.mandiri.api.Kegiatan;
import dev.kultivator.sekte.mandiri.api.MandiriApi;
import dev.kultivator.sekte.mandiri.api.MandiriIds;
import dev.kultivator.sekte.mandiri.api.PasarMurid;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The command trees of Murid Mandiri (DESIGN 39.10.4, 39.F.8): {@code /kultivasi uji perguruan mandiri ...} (op, through
 * {@code SekteCommands.test}) and {@code /kultivasi perguruan mandiri ...} (every player, through
 * {@code SekteCommands.player}). Every DESIGN 39 module hangs its own sub-tree here with {@link #uji} / {@link #pemain}
 * from its init and appends its chips to {@link #CHIPS}, so nobody edits this file or {@code command/CheatHelp} (group
 * {@code perguruan_mandiri} reads the live list). 39F's tree is {@code uji perguruan mandiri seteru ...}.
 * <p>
 * Built-in: {@code uji perguruan mandiri} (the chips, clickable) and {@code uji perguruan mandiri modul} (which parts of
 * DESIGN 39 have landed: switch, executors per activity, market, guild, providers).
 */
public final class MandiriCommands {

	/** Chips of group {@code perguruan_mandiri} of {@code /kultivasi uji}; live list, modules append from their init. */
	public static final List<String> CHIPS = new CopyOnWriteArrayList<>(List.of("perguruan mandiri", "perguruan mandiri modul"));

	private static final Map<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> UJI = new LinkedHashMap<>();
	private static final Map<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> PEMAIN = new LinkedHashMap<>();
	private static boolean registered;

	/** From {@code Mandiri.init} (switch on). The trees are built when Brigadier asks, so later {@link #uji} calls count. */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		SekteCommands.test(MandiriIds.MODUL, MandiriCommands::ujiRoot);
		SekteCommands.player(MandiriIds.MODUL, MandiriCommands::pemainRoot);
	}

	/** Adds the op sub-tree {@code uji perguruan mandiri <literal>}; call from a module's init. */
	public static synchronized void uji(String literal, Supplier<LiteralArgumentBuilder<CommandSourceStack>> node) {
		if (literal != null && node != null) {
			UJI.put(literal + "#" + UJI.size(), node);
		}
	}

	/** Adds the player sub-tree {@code perguruan mandiri <literal>} ({@code bebaskan}, {@code panggil}, {@code jelmaan}). */
	public static synchronized void pemain(String literal, Supplier<LiteralArgumentBuilder<CommandSourceStack>> node) {
		if (literal != null && node != null) {
			PEMAIN.put(literal + "#" + PEMAIN.size(), node);
		}
	}

	/** Appends chips (each starting with {@code "perguruan mandiri "}). */
	public static void chips(String... chips) {
		if (chips != null) {
			CHIPS.addAll(List.of(chips));
		}
	}

	private static LiteralArgumentBuilder<CommandSourceStack> ujiRoot() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(MandiriIds.MODUL)
				.executes(ctx -> help(ctx.getSource()))
				.then(Commands.literal("modul").executes(ctx -> modul(ctx.getSource())));
		attach(root, UJI, "test");
		return root;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> pemainRoot() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(MandiriIds.MODUL)
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.sekte.mandiri.inti.cmd.pemain")
							.withStyle(ChatFormatting.GOLD), false);
					return 1;
				});
		attach(root, PEMAIN, "player");
		return root;
	}

	private static void attach(LiteralArgumentBuilder<CommandSourceStack> root,
			Map<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> nodes, String what) {
		for (Map.Entry<String, Supplier<LiteralArgumentBuilder<CommandSourceStack>>> entry : List.copyOf(nodes.entrySet())) {
			try {
				LiteralArgumentBuilder<CommandSourceStack> node = entry.getValue().get();
				if (node != null) {
					root.then(node);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Mandiri {} command {} could not be built", what, entry.getKey(), exception);
			}
		}
	}

	/** {@code uji perguruan mandiri}: every chip of DESIGN 39, six per line. */
	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.mandiri.inti.cmd.judul").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		MutableComponent line = Component.literal(" ");
		int inLine = 0;
		for (String chip : CHIPS) {
			if (inLine > 0) {
				line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(Component.literal(chip).withStyle(ChatFormatting.AQUA));
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

	/** {@code uji perguruan mandiri modul}: which parts are live. */
	private static int modul(CommandSourceStack source) {
		boolean on = MandiriApi.aktif();
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.mandiri.inti.cmd.modul.aktif",
				Component.translatable(on ? "kultivator.sekte.cmd.module.on" : "kultivator.sekte.cmd.module.off"))
				.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
		MutableComponent ready = Component.literal("");
		MutableComponent missing = Component.literal("");
		int count = 0;
		for (Kegiatan kegiatan : Kegiatan.values()) {
			if (kegiatan == Kegiatan.KOSONG) {
				continue;
			}
			MutableComponent target = MandiriKegiatan.ada(kegiatan) ? ready : missing;
			if (!target.getSiblings().isEmpty()) {
				target.append(Component.literal(", "));
			}
			target.append(Component.translatable(kegiatan.kunciNama()).append(Component.literal(" (" + kegiatan.pemilik() + ")")));
			if (MandiriKegiatan.ada(kegiatan)) {
				count++;
			}
		}
		int total = Kegiatan.values().length - 1;
		int done = count;
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.mandiri.inti.cmd.modul.pelaksana", done, total)
				.withStyle(ChatFormatting.YELLOW), false);
		if (!ready.getSiblings().isEmpty()) {
			source.sendSuccess(() -> Component.literal(" + ").withStyle(ChatFormatting.GREEN).append(ready.withStyle(ChatFormatting.GRAY)), false);
		}
		if (!missing.getSiblings().isEmpty()) {
			source.sendSuccess(() -> Component.literal(" - ").withStyle(ChatFormatting.RED).append(missing.withStyle(ChatFormatting.DARK_GRAY)), false);
		}
		boolean pasar = MandiriApi.pasar() != PasarMurid.NONE;
		boolean gilda = MandiriApi.gilda() != GildaMurid.NONE;
		int tujuan = MandiriApi.tujuanProviders().size();
		int pertemuan = MandiriApi.pertemuanProviders().size();
		source.sendSuccess(() -> Component.translatable("kultivator.sekte.mandiri.inti.cmd.modul.bagian",
				Component.translatable(pasar ? "kultivator.sekte.cmd.module.on" : "kultivator.sekte.cmd.module.off"),
				Component.translatable(gilda ? "kultivator.sekte.cmd.module.on" : "kultivator.sekte.cmd.module.off"),
				tujuan, pertemuan).withStyle(ChatFormatting.GRAY), false);
		return on ? 1 : 0;
	}

	private MandiriCommands() {
	}
}
