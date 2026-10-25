package dev.kultivator.dunia.kota42;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.kota42.api.WargaApi;
import dev.kultivator.dunia.kota42.warga.Warga;
import dev.kultivator.dunia.kota42.warga.WargaData;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.TempatInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The player commands of Kota yang Hidup (DESIGN 42.14.2): {@code /kultivasi kotaku ...} (and {@code /cultivate kotaku}),
 * through {@code DuniaCommands.player} ({@code bicara} is taken by {@code Pengembara}). Every sub opens the same screen or
 * runs the same action as its button (one handler); a screen that needs an NPC opens read-only from afar ({@code npcId = -1}).
 * The modules hang their own subs with {@link #daftar} from their init: {@code buku}, {@code warga <nama>} (42A/42U),
 * {@code rumah}, {@code toko}, {@code investasi}, {@code gudang}, {@code kontrak}, {@code jabatan}, {@code perkara},
 * {@code pulang} (42C), {@code acara} (42D), {@code kantor}, {@code pakta}, {@code cabang} (42E), {@code kavling} (42F).
 * <p>
 * Built in: {@code kotaku} and {@code kotaku status} print the place the player stands in (kind, stored residents, mean
 * mood, residents who remember the player).
 */
public final class KotaHidupCommands {

	/** Radius in which the nearest place counts for {@code kotaku status}. */
	public static final int JARAK_TEMPAT = 160;

	private static final Map<String, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>>> SUBS = new LinkedHashMap<>();
	private static boolean registered;

	/** From {@code KotaHidup.init}. */
	static void init() {
		if (registered) {
			return;
		}
		registered = true;
		DuniaCommands.player("kotaku", KotaHidupCommands::node);
	}

	/** Adds {@code /kultivasi kotaku <sub>}; the node's literal should be {@code sub}. */
	public static synchronized void daftar(String sub, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>> node) {
		if (sub != null && node != null) {
			SUBS.put(sub + "#" + SUBS.size(), node);
		}
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("kotaku")
				.executes(KotaHidupCommands::status)
				.then(Commands.literal("status").executes(KotaHidupCommands::status));
		for (Map.Entry<String, Supplier<? extends ArgumentBuilder<CommandSourceStack, ?>>> entry : List.copyOf(SUBS.entrySet())) {
			try {
				ArgumentBuilder<CommandSourceStack, ?> sub = entry.getValue().get();
				if (sub != null) {
					root.then(sub);
				}
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Kota hidup player command {} could not be built", entry.getKey(), exception);
			}
		}
		return root;
	}

	/** The place the player is in or nearest within {@link #JARAK_TEMPAT} blocks, or null (also outside the overworld). */
	public static @Nullable TempatInfo tempatPemain(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || !KotaHidup.duniaKota(level)) {
			return null;
		}
		TempatInfo here = TempatApi.tempatDi(level, player.blockPosition());
		if (here != null) {
			return here;
		}
		List<TempatInfo> near = TempatApi.dekat(level, player.blockPosition(), JARAK_TEMPAT, "");
		return near.isEmpty() ? null : near.get(0);
	}

	/** {@code /kultivasi kotaku status}. */
	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CommandSourceStack source = ctx.getSource();
		if (!KotaConfig.warga().aktif) {
			source.sendFailure(Component.translatable("kultivator.kota42.cmd.mati"));
			return 0;
		}
		TempatInfo tempat = tempatPemain(player);
		if (tempat == null) {
			source.sendFailure(Component.translatable("kultivator.kota42.cmd.tanpa_tempat"));
			return 0;
		}
		List<Warga> warga = WargaApi.wargaTempat(tempat.situsId());
		WargaData data = WargaData.get(source.getServer());
		int index = data.cariPemain(player.getUUID());
		int kenal = 0;
		for (Warga w : warga) {
			if (index >= 0 && w.ingatanDari(index) != null) {
				kenal++;
			}
		}
		int known = kenal;
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.cmd.status.judul").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> Component.translatable("kultivator.kota42.cmd.status.tempat", tempat.jenis(), warga.size(),
				WargaApi.suasanaTempat(tempat.situsId()), known), false);
		if (warga.isEmpty()) {
			source.sendSuccess(() -> Component.translatable("kultivator.kota42.cmd.status.belum").withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private KotaHidupCommands() {
	}
}
