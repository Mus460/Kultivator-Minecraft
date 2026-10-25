package dev.kultivator.sekte.gabung;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.sekte.api.SekteApi;
import dev.kultivator.sekte.gabung.api.GabungApi;
import dev.kultivator.sekte.gabung.api.StatusAnggota;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The player commands of Gabung Sekte (DESIGN 41.13.2): {@code /kultivasi gabung ...} (and {@code /cultivate gabung}),
 * through {@code DuniaCommands.player}. Every sub opens the same screen or runs the same action as its button (one
 * handler), so the modules hang their own subs with {@link #daftar} from their init: {@code direktori}, {@code info},
 * {@code lamar}, {@code batal} (41B), {@code papan}, {@code paviliun}, {@code kediaman}, {@code jatah} (41C),
 * {@code kartu}, {@code pangkat}, {@code naik}, {@code pamit}, {@code daftarkan}, {@code lepaskan} (41A/41U),
 * {@code tantang}, {@code politik}, {@code sumbang}, {@code tamu} (41D), {@code shifu} (41B).
 * <p>
 * Built in: {@code gabung} and {@code gabung status} print the player's membership in chat (sect, rank, PK, Shifu bond,
 * violation record; or the open application; or the directory size).
 */
public final class GabungCommands {

	private static final Map<String, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>>> SUBS = new LinkedHashMap<>();
	private static boolean registered;

	/** From {@code Gabung.init} (module on). */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		DuniaCommands.player("gabung", GabungCommands::node);
	}

	/** Adds {@code /kultivasi gabung <sub>}; the node's literal should be {@code sub}. */
	public static synchronized void daftar(String sub, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>> node) {
		if (sub != null && node != null) {
			SUBS.put(sub + "#" + SUBS.size(), node);
		}
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gabung")
				.executes(GabungCommands::status)
				.then(Commands.literal("status").executes(GabungCommands::status));
		for (Map.Entry<String, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>>> entry : List.copyOf(SUBS.entrySet())) {
			try {
				ArgumentBuilder<CommandSourceStack, ?> sub = entry.getValue().get();
				if (sub != null) {
					root.then(sub);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Gabung player command {} could not be built", entry.getKey(), exception);
			}
		}
		return root;
	}

	/** {@code /kultivasi gabung status}. */
	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CommandSourceStack source = ctx.getSource();
		if (!Gabung.aktif()) {
			source.sendFailure(Component.translatable("kultivator.gabung.cmd.mati"));
			return 0;
		}
		MinecraftServer server = source.getServer();
		Keanggotaan k = GabungApi.anggota(player);
		k.sentuh(player, GabungApi.hari(server));
		long hari = GabungApi.hari(server);
		source.sendSuccess(() -> Component.translatable("kultivator.gabung.cmd.status.judul").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		if (k.status.anggota()) {
			Component sekte = nama(k.sekte);
			source.sendSuccess(() -> Component.translatable("kultivator.gabung.cmd.status.anggota", sekte, k.pangkat.nama(),
					GabungApi.kelasSekte(k.sekte).nama(), GabungApi.corak(k.sekte).nama()), false);
			source.sendSuccess(() -> Component.translatable("kultivator.gabung.cmd.status.pk", k.pk, k.pkTotal, k.pkBulan)
					.withStyle(ChatFormatting.AQUA), false);
			source.sendSuccess(() -> Component.translatable("kultivator.gabung.cmd.status.ikatan", k.ikatanShifu, k.pelanggaran,
					k.umurAnggota(hari)).withStyle(ChatFormatting.GRAY), false);
			if (k.status == StatusAnggota.MENYAMAR && !k.samaranSekte.isEmpty()) {
				source.sendSuccess(() -> Component.translatable("kultivator.gabung.cmd.status.menyamar", nama(k.samaranSekte),
						Math.max(0L, k.samaranSampai - hari)).withStyle(ChatFormatting.DARK_PURPLE), false);
			}
		} else if (k.status == StatusAnggota.CALON) {
			source.sendSuccess(() -> Component.translatable("kultivator.gabung.cmd.status.calon", nama(k.sekte), k.pintu.nama(),
					Math.max(0L, k.hariUjian - hari), Math.max(0L, k.calonSampai - hari)), false);
		} else {
			int known = Long.bitCount(k.dikenal);
			source.sendSuccess(() -> Component.translatable("kultivator.gabung.cmd.status.bebas", known), false);
		}
		GabungData.get(server).setDirty();
		return 1;
	}

	private static Component nama(String sekte) {
		Component name = sekte == null || sekte.isEmpty() ? null : SekteApi.diplomasi().name(sekte);
		return name == null ? Component.literal(sekte == null ? "?" : sekte) : name;
	}

	private GabungCommands() {
	}
}
