package dev.kultivator.sekte.langit.main;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.api.DuniaApi;
import dev.kultivator.purba.HeavenlyWrath;
import dev.kultivator.sekte.api.SekteBus;
import dev.kultivator.sekte.langit.LangitTests;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.LangitEvents;
import dev.kultivator.sekte.langit.api.LangitIds;
import dev.kultivator.sekte.langit.api.LangitPayloads;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.sekte.langit.gerak.FeriPanggil;
import dev.kultivator.sekte.langit.gerak.LangitJatuh;
import dev.kultivator.sekte.langit.npc.LangitNpcAnchors;
import dev.kultivator.sekte.langit.npc.LangitNpcPack;
import dev.kultivator.util.Advancements;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Sibuk;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.UUID;

/**
 * Module 37G of Sekte Langit (DESIGN 37.7, 37.9.5): gameplay, events, NPC sky sects, UI, guide and tests. One line in
 * {@code sekte.langit.LangitContent}. Registry content (the trophy item, the screen payloads, the palace placement type,
 * the test and player commands) is always registered (K1, audit 4.1); every runtime system below obeys
 * {@code sekteLangit.aktif} and its own switch. Every event asks {@code util.Sibuk} (audit 4.2).
 * <p>
 * Hand-over to DESIGN 38 (Kota Besar): this module owns {@code kultivator.mixins.json} (one entry,
 * {@code ServerLevelPrecipitationMixin}) and the guide's chapter 17 ({@code guide.GuideChapters}, save index 16, shown
 * after chapter 9); 38 appends its own mixin entries and chapters after these.
 */
public final class LangitMain {

	private static boolean initialised;
	/** Stale envoys found on load; discarded on the next tick, not inside the chunk's entity walk. */
	private static final java.util.List<net.minecraft.world.entity.Entity> STALE = new java.util.ArrayList<>();

	public static void init() {
		if (initialised) {
			return;
		}
		initialised = true;
		// K1: registry content never follows a switch.
		LangitItems.init();
		LangitUiPayloads.register();
		LangitNpcPack.registerContent();
		DuniaCommands.test("langit_sekte", LangitTests::node);
		DuniaCommands.player("langit", LangitMain::playerNode);
		if (!LangitConfig.on()) {
			return;
		}
		LangitSites.register();
		LangitSaksi.register();
		LangitSerbu.register();
		LangitTangga.register();
		LangitUi.register();
		LangitNpcPack.register(LangitConfig.get().strukturNpc);
		HeavenlyWrath.addWinListener(LangitUtusan::onWrathWon);
		LangitPayloads.onSaw(LangitMain::saw);
		Sibuk.registerEnemy("bajak_awan", player -> LangitBajak.active(player.getUUID()));
		LangitJatuh.addGuestRule(LangitPeristiwa::guest);
		// audit 4.2 / T4: no ambush, rival band or native may start on a sky island (its own events are this module's)
		DuniaApi.addSafeGround((level, pos) -> LangitConfig.on() && LangitApi.get().at(level, pos) != null);
		LangitEvents.add(new LangitEvents.Listener() {
			@Override
			public void founded(UUID owner, ServerLevel level) {
				grant(level.getServer(), owner, "rancang");
				LangitSyncServer.dirty(owner);
			}

			@Override
			public void tier(UUID owner, ServerLevel level, int tier) {
				if (tier >= 1) {
					grant(level.getServer(), owner, "tapak_awan");
				}
				if (tier >= 9) {
					grant(level.getServer(), owner, "sembilan_langit");
				}
				LangitSyncServer.dirty(owner);
			}

			@Override
			public void ascended(UUID owner, ServerLevel level) {
				grant(level.getServer(), owner, "naik");
				LangitPeristiwa.festivalFor(level.getServer(), owner, LangitPeristiwa.FESTIVAL_AFTER_ASCENT);
				LangitData.get(level.getServer()).chronicle(owner, LangitPeristiwa.today(level.getServer()),
						Component.translatable("kultivator.sekte_langit.pesta.naik"));
				LangitSyncServer.dirty(owner);
			}

			@Override
			public void removed(UUID owner) {
				LangitSyncServer.dirty(owner);
			}

			@Override
			public void changed(UUID owner) {
				LangitSyncServer.dirty(owner);
			}
		});
		SekteBus.TOURNAMENT_ENDED.register((server, owner, result) -> {
			if (LangitApi.get().isSkyHall(owner)) {
				LangitPeristiwa.festivalFor(server, owner, 1);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(LangitMain::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			LangitBajak.onDeath(entity);
			LangitUtusan.onDeath(entity, source);
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && entity.entityTags().contains(LangitUtusan.TAG) && player instanceof ServerPlayer serverPlayer) {
				Msg.chat(serverPlayer, Component.translatable("kultivator.sekte_langit.utusan.bicara").withStyle(ChatFormatting.YELLOW));
				if (LangitApi.get().isSkyHall(serverPlayer.getUUID())) {
					LangitUtusan.offer(serverPlayer);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// an envoy saved by a restart mid-visit is no longer tracked: he goes home
			if (entity.entityTags().contains(LangitUtusan.TAG) && !LangitUtusan.tracked(entity)) {
				STALE.add(entity);
			}
		});
		ServerPlayerEvents.LEAVE.register(LangitSyncServer::forget);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clearAll());
	}

	private static void tick(MinecraftServer server) {
		if (!STALE.isEmpty()) {
			STALE.forEach(net.minecraft.world.entity.Entity::discard);
			STALE.clear();
		}
		long tick = server.overworld().getGameTime();
		LangitSyncServer.tick(server, tick);
		LangitSerbu.tick(server);
		if (tick % 20L != 0L) {
			return;
		}
		LangitPeristiwa.second(server);
		LangitBajak.second(server);
		LangitUtusan.second(server);
		LangitSerbu.second(server);
		LangitSaksi.second(server);
		LangitMurid.second(server);
	}

	private static void clearAll() {
		STALE.clear();
		LangitBajak.clear();
		LangitUtusan.clear();
		LangitPeristiwa.clear();
		LangitSerbu.clear();
		LangitSaksi.clear();
		LangitMurid.clear();
		LangitSyncServer.clear();
		LangitNpcAnchors.clear();
	}

	private static void grant(MinecraftServer server, UUID owner, String name) {
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			Advancements.grant(player, LangitIds.ADV + name);
		}
	}

	/** The client saw the Buddha's Light: granted when the sun is low and the player stands high on a sky island. */
	private static void saw(ServerPlayer player, Integer what) {
		if (what == null || what != LangitIds.SAW_HALO) {
			return;
		}
		ServerLevel level = player.level();
		long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		boolean low = time >= 22000L || time < 2000L || time >= 10500L && time < 13500L;
		SkyHallView view = LangitApi.get().at(level, player.blockPosition());
		if (low && view != null && player.getY() >= view.skyFloorY()) {
			Advancements.grant(player, LangitIds.ADV + "cahaya_buddha");
		}
	}

	// ================================================================== /kultivasi langit

	private static LiteralArgumentBuilder<CommandSourceStack> playerNode() {
		return Commands.literal("langit")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					LangitUi.send(player);
					Msg.chat(player, Component.translatable("kultivator.sekte_langit.layar.buka").withStyle(ChatFormatting.GRAY));
					return 1;
				})
				.then(Commands.literal("feri").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Component refused = LangitConfig.on() ? FeriPanggil.callNear(player) : Component.translatable("kultivator.sekte_langit.mati");
					if (refused != null) {
						Msg.actionBar(player, refused.copy().withStyle(ChatFormatting.YELLOW));
						return 0;
					}
					return 1;
				}))
				.then(Commands.literal("utusan").then(Commands.argument("jawab", StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"terima", "tolak", "tantang", "setor"},
								builder))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							if (!LangitConfig.on()) {
								return 0;
							}
							String answer = StringArgumentType.getString(ctx, "jawab");
							return switch (answer) {
								case "terima", "tolak", "tantang", "setor" -> LangitUtusan.answer(player, answer);
								default -> 0;
							};
						})));
	}

	private LangitMain() {
	}
}
