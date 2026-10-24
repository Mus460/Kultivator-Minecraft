package dev.kultivator.client.dunia.tempat.tema.model;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.Kultivator;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.dunia.tempat.tema.TemaEntities;
import dev.kultivator.sosok.PropId;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The client check command {@code /temamodel} of the themed-town models (DESIGN 38.T.9, module 38TV-M; nothing is sent
 * to the server and nothing in the world changes). The 30 s line-up of every themed entity is the server's
 * {@code /uji tema model}; this command answers the two questions that line-up cannot:
 * <ul>
 * <li>{@code /temamodel info}: renderers in place, themed entities drawn per frame (last second), how many of them are
 * past the small-animation LOD, emissive passes per frame, and the LOD distances for the current effect quality (the
 * weak-laptop check);</li>
 * <li>{@code /temamodel cek}: every hand prop 41-62, every {@code kultivator:tema/*} and {@code kultivator:dagang/*}
 * item and the theme entity textures are present in the loaded resource packs (names of the missing ones, at most 8).</li>
 * </ul>
 */
final class TemaModelPerintah {

	private static final String K = "kultivator.tempat_tema_model.perintah.";
	private static final int DAFTAR_MAKS = 8;
	/** Theme entity textures written by tools/gen_tempat_tema_textures.py (bands + glow). */
	private static final int TEKSTUR_MIN = 60;

	private TemaModelPerintah() {
	}

	static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("temamodel")
				.executes(TemaModelPerintah::info)
				.then(ClientCommands.literal("info").executes(TemaModelPerintah::info))
				.then(ClientCommands.literal("cek").executes(TemaModelPerintah::cek)));
	}

	private static int info(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		int fps = Math.max(1, Minecraft.getInstance().getFps());
		int[] n = TemaModelStat.detikLalu();
		float skala = EffectQuality.minimal() ? 0.75F : 1.0F;
		source.sendFeedback(Component.translatable(K + "info.judul").withStyle(ChatFormatting.GOLD));
		source.sendFeedback(Component.translatable(K + "info.renderer", TemaModelClient.terdaftar(), TemaEntities.semua().size()));
		source.sendFeedback(Component.translatable(K + "info.bingkai", satuDesimal(n[0], fps), satuDesimal(n[1], fps), satuDesimal(n[2], fps), fps));
		source.sendFeedback(Component.translatable(K + "info.lod", Math.round(64 * skala), Math.round(128 * skala), Math.round(192 * skala),
				EffectQuality.minimal() ? 24 : 96, EffectQuality.level().displayName()));
		return 1;
	}

	private static String satuDesimal(int perDetik, int fps) {
		return String.format(java.util.Locale.ROOT, "%.1f", perDetik / (float) fps);
	}

	private static int cek(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		ResourceManager resources = Minecraft.getInstance().getResourceManager();
		List<String> hilang = new ArrayList<>();
		int prop = 0;
		for (int id = PropId.TEMA_FIRST; id <= PropId.TEMA_LAST; id++) {
			String path = PropId.jalurModel(id);
			if (path == null) {
				hilang.add("prop " + id);
				continue;
			}
			prop++;
			if (resources.getResource(Kultivator.id("items/" + path + ".json")).isEmpty()) {
				hilang.add(path);
			}
		}
		int item = 0;
		for (Identifier key : BuiltInRegistries.ITEM.keySet()) {
			if (!Kultivator.MOD_ID.equals(key.getNamespace())) {
				continue;
			}
			String path = key.getPath();
			if (!path.startsWith("tema/") && !path.startsWith("dagang/")) {
				continue;
			}
			item++;
			if (resources.getResource(Kultivator.id("items/" + path + ".json")).isEmpty()) {
				hilang.add(path);
			}
		}
		int tekstur = resources.listResources("textures/entity/tempat/tema",
				id -> Kultivator.MOD_ID.equals(id.getNamespace()) && id.getPath().endsWith(".png")).size();
		if (tekstur < TEKSTUR_MIN) {
			hilang.add("textures/entity/tempat/tema (" + tekstur + "/" + TEKSTUR_MIN + ")");
		}
		if (hilang.isEmpty()) {
			source.sendFeedback(Component.translatable(K + "cek.ok", prop, item, tekstur).withStyle(ChatFormatting.GREEN));
			return 1;
		}
		String daftar = String.join(", ", hilang.subList(0, Math.min(DAFTAR_MAKS, hilang.size())));
		if (hilang.size() > DAFTAR_MAKS) {
			daftar = daftar + ", ...";
		}
		source.sendError(Component.translatable(K + "cek.kurang", hilang.size(), daftar));
		return 0;
	}
}
