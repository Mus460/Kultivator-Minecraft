package dev.kultivator.client.epic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.kultivator.client.EffectQuality;
import dev.kultivator.client.epic.camera.CinematicDirector;
import dev.kultivator.client.epic.camera.ShotGuard;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The client command {@code /sinematik} (DESIGN 23.15.3, no op): camera mode, VFX scale, Mode Rekam, soft geometry under a
 * shader pack, the profile and frame-guard info, and a reset of every 7b setting (which also clears the frame-guard
 * memory). A fallback for the Settings page.
 */
final class EpicClientCommands {

	private static final String K = "kultivator.momen.cmd.";

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("sinematik");
		for (EpicSettings.Camera camera : EpicSettings.Camera.values()) {
			root.then(ClientCommands.literal(camera.id()).executes(context -> {
				EpicSettings.setCamera(camera);
				if (camera == EpicSettings.Camera.MATI) {
					CinematicDirector.abort();
				}
				return ok(context, Component.translatable(K + "camera", Component.translatable("kultivator.momen.setting.camera." + camera.id())));
			}));
		}
		LiteralArgumentBuilder<FabricClientCommandSource> vfx = ClientCommands.literal("vfx");
		for (String step : new String[]{"0.5", "1", "1.5"}) {
			float value = Float.parseFloat(step);
			vfx.then(ClientCommands.literal(step).executes(context -> {
				EpicSettings.setVfx(value);
				return ok(context, Component.translatable(K + "vfx", "×" + EpicSettings.vfx()));
			}));
		}
		root.then(vfx);
		root.then(toggle("rekam", EpicSettings.RECORD, "record"));
		root.then(toggle("paket", EpicSettings.PACK_GEOMETRY, "pack"));
		root.then(ClientCommands.literal("info").executes(EpicClientCommands::info));
		root.then(ClientCommands.literal("reset").executes(context -> {
			EpicSettings.resetAll();
			EpicBudget.resetSession();
			return ok(context, Component.translatable(K + "reset"));
		}));
		dispatcher.register(root);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, String key, String id) {
		return ClientCommands.literal(name)
				.executes(context -> set(context, key, id, !EpicSettings.flag(key)))
				.then(ClientCommands.literal("nyala").executes(context -> set(context, key, id, true)))
				.then(ClientCommands.literal("mati").executes(context -> set(context, key, id, false)));
	}

	private static int set(CommandContext<FabricClientCommandSource> context, String key, String id, boolean on) {
		EpicSettings.setFlag(key, on);
		return ok(context, Component.translatable(K + id + (on ? ".on" : ".off")));
	}

	private static int info(CommandContext<FabricClientCommandSource> context) {
		FabricClientCommandSource source = context.getSource();
		boolean pack = ShaderCompat.packOn();
		source.sendFeedback(Component.translatable(K + "info.title").withStyle(ChatFormatting.GOLD));
		source.sendFeedback(Component.translatable(K + "info.profile",
				Component.translatable(pack ? K + "info.pack" : K + "info.vanilla"),
				Component.translatable(ShaderCompat.irisFound() ? K + "info.iris_yes" : K + "info.iris_no")).withStyle(ChatFormatting.GRAY));
		int tier = EpicBudget.tier();
		Component tierName = tier < 0 ? Component.translatable(K + "info.no_geometry") : EffectQuality.Level.values()[tier].displayName();
		source.sendFeedback(Component.translatable(K + "info.tier", tierName, EffectQuality.level().displayName()).withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.translatable(K + "info.guard", String.format(java.util.Locale.ROOT, "%.1f", EpicBudget.baselineMs()),
				EpicBudget.dropsThisSession(), String.format(java.util.Locale.ROOT, "%.2f", EpicBudget.cpuMs())).withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.translatable(K + "info.budget", EpicBudget.quadsLastFrame(), EpicBudget.quadCap(), EpicBudget.largeLastFrame(),
				EpicBudget.largeCap(), EpicDirector.sceneCount()).withStyle(ChatFormatting.GRAY));
		source.sendFeedback(Component.translatable(K + "info.camera", Component.translatable("kultivator.momen.setting.camera." + CinematicDirector.mode().id()),
				ShotGuard.reason().isEmpty() ? "-" : ShotGuard.reason()).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int ok(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendFeedback(message.copy().withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private EpicClientCommands() {
	}
}
