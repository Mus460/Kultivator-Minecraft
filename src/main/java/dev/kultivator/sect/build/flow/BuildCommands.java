package dev.kultivator.sect.build.flow;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.Kultivator;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.formasi.FormationRegistry;
import dev.kultivator.formasi.integrate.SectBuildBridge;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.sect.build.api.AuditReport;
import dev.kultivator.sect.build.api.Bahan;
import dev.kultivator.sect.build.api.Bill;
import dev.kultivator.sect.build.api.ComponentView;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.FormasiAnchor;
import dev.kultivator.sect.build.api.PlanStats;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.hall.HallItems;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import dev.kultivator.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Every command of DESIGN 21.22, all calling {@link EngineApi} for the engine's part:
 * <ul>
 * <li><b>Player</b> ({@code /kultivasi aula bangun ...}, merged into module A's {@code aula} node): status, templat,
 * pratinjau, putar, konfirmasi [ya], batal, setor [semua|butuh], jeda, lanjut, prioritas, naik, perbaiki, bongkar
 * [ya], lepaskan [ya], plus the buttons' helpers ganti, sendiri, pindah, periksa-ulang, lewati, tanpa-terhalang.</li>
 * <li><b>Debug</b> ({@code /kultivasi uji aula bangun ...}): tingkat-set, komponen selesai, bank, rencana ulang.</li>
 * <li><b>Test</b> ({@code /kultivasi uji sekte ...}, op): templat, tingkat [instan|cepat], sumber, pamer (+ konfirmasi,
 * hapus, daftar), hologram, bandingkan, info, periksa, audit, kamera, upacara, kecepatan, naik, bongkar, reset, misi,
 * pekerja. Every test placement still passes preview and confirm and goes to the engine's undo log.</li>
 * </ul>
 * Source: the user asked for test commands (the DESIGN 20.0 style).
 */
public final class BuildCommands {

	public static final String[] DIRECTIONS = {"utara", "timur", "selatan", "barat"};
	private static final SuggestionProvider<CommandSourceStack> DIRECTION = (ctx, builder) -> SharedSuggestionProvider.suggest(DIRECTIONS, builder);
	private static final SuggestionProvider<CommandSourceStack> SPECS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(FormationRegistry.all().stream().map(FormationSpec::id), builder);
	private static final SuggestionProvider<CommandSourceStack> GLAZE = (ctx, builder) ->
			SharedSuggestionProvider.suggest(Stream.of(Glaze.values()).map(Glaze::id), builder);
	private static final SuggestionProvider<CommandSourceStack> COMPONENTS = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		List<String> ids = new ArrayList<>();
		if (player != null) {
			for (ComponentView component : EngineApi.get().components(player.getUUID())) {
				if (component.state() != ComponentView.STATE_DONE) {
					ids.add(component.id());
				}
			}
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};
	/** An audit report is polled this often (ticks) and at most this many times. */
	private static final int AUDIT_POLL = 40;
	private static final int AUDIT_POLLS = 450;

	private record AuditJob(UUID player, @Nullable Integer template, int polls) {
	}

	private static final Map<UUID, AuditJob> AUDITS = new HashMap<>();

	// ================================================================== player and debug

	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		LiteralArgumentBuilder<CommandSourceStack> bangun = Commands.literal("bangun").executes(ctx -> status(self(ctx)));
		bangun.then(Commands.literal("templat").executes(ctx -> {
			ServerPlayer player = self(ctx);
			Founding.openScreen(player, EngineApi.get().template(player.getUUID()));
			return 1;
		}));
		bangun.then(Commands.literal("pratinjau").then(Commands.argument("t", IntegerArgumentType.integer(1, SectTemplates.COUNT))
				.executes(ctx -> preview(ctx, false, false))
				.then(Commands.argument("arah", StringArgumentType.word()).suggests(DIRECTION)
						.executes(ctx -> preview(ctx, true, false))
						.then(Commands.argument("glasir", StringArgumentType.word()).suggests(GLAZE).executes(ctx -> preview(ctx, true, true))))));
		bangun.then(Commands.literal("putar").executes(ctx -> {
			Founding.rotate(self(ctx));
			return 1;
		}));
		bangun.then(Commands.literal("konfirmasi").executes(ctx -> {
			Founding.askConsent(self(ctx));
			return 1;
		}).then(Commands.literal("ya").executes(ctx -> Founding.confirm(self(ctx), 0L) ? 1 : 0)));
		bangun.then(Commands.literal("batal").executes(ctx -> {
			Founding.cancel(self(ctx), true);
			return 1;
		}));
		bangun.then(Commands.literal("setor").executes(ctx -> depositHeld(self(ctx)))
				.then(Commands.literal("semua").executes(ctx -> {
					ServerPlayer player = self(ctx);
					Deposits.depositAll(player, player.getUUID());
					return 1;
				}))
				.then(Commands.literal("butuh").executes(ctx -> {
					ServerPlayer player = self(ctx);
					Deposits.depositNeeded(player, player.getUUID());
					return 1;
				})));
		bangun.then(Commands.literal("jeda").executes(ctx -> pause(self(ctx), true)));
		bangun.then(Commands.literal("lanjut").executes(ctx -> pause(self(ctx), false)));
		bangun.then(Commands.literal("prioritas").then(Commands.argument("komponen", StringArgumentType.word()).suggests(COMPONENTS)
				.executes(ctx -> componentOp(self(ctx), StringArgumentType.getString(ctx, "komponen"), 0))));
		bangun.then(Commands.literal("lewati").then(Commands.argument("komponen", StringArgumentType.word()).suggests(COMPONENTS)
				.executes(ctx -> componentOp(self(ctx), StringArgumentType.getString(ctx, "komponen"), 1))));
		bangun.then(Commands.literal("tanpa-terhalang").then(Commands.argument("komponen", StringArgumentType.word()).suggests(COMPONENTS)
				.executes(ctx -> componentOp(self(ctx), StringArgumentType.getString(ctx, "komponen"), 2))));
		bangun.then(Commands.literal("periksa-ulang").executes(ctx -> {
			ServerPlayer player = self(ctx);
			EngineApi.get().recheck(player.getUUID());
			Msg.chat(player, Component.translatable("kultivator.sectbuild.recheck.done").withStyle(ChatFormatting.AQUA));
			return 1;
		}));
		bangun.then(Commands.literal("naik").then(Commands.argument("t", IntegerArgumentType.integer(2, SectTemplates.SKY_LAST)).executes(ctx ->
				Founding.preview(self(ctx), IntegerArgumentType.getInteger(ctx, "t"), -1, null, Founding.Mode.UPGRADE, false) ? 1 : 0)));
		bangun.then(Commands.literal("perbaiki").executes(ctx -> repair(self(ctx))));
		bangun.then(Commands.literal("bongkar")
				.then(Commands.literal("bangunan").executes(ctx -> deconstruct(self(ctx), false, false))
						.then(Commands.literal("ya").executes(ctx -> deconstruct(self(ctx), false, true))))
				.then(Commands.literal("total").executes(ctx -> deconstruct(self(ctx), true, false))
						.then(Commands.literal("ya").executes(ctx -> deconstruct(self(ctx), true, true)))));
		bangun.then(Commands.literal("lepaskan").executes(ctx -> {
			Relocation.abandon(self(ctx), false);
			return 1;
		}).then(Commands.literal("ya").executes(ctx -> {
			Relocation.abandon(self(ctx), true);
			return 1;
		})));
		bangun.then(Commands.literal("ganti").executes(ctx -> {
			Founding.change(self(ctx));
			return 1;
		}));
		bangun.then(Commands.literal("sendiri").executes(ctx -> {
			ServerPlayer player = self(ctx);
			FlowData.setFlag(player.getUUID(), FlowData.F_SELF_BUILD, true);
			Msg.chat(player, Component.translatable("kultivator.sectbuild.self_build").withStyle(ChatFormatting.GRAY));
			return 1;
		}));
		bangun.then(Commands.literal("pindah")
				.then(Commands.literal("bongkar").executes(ctx -> {
					Relocation.choose(self(ctx), true);
					return 1;
				}))
				.then(Commands.literal("tinggalkan").executes(ctx -> {
					Relocation.choose(self(ctx), false);
					return 1;
				}))
				.then(Commands.literal("batal").executes(ctx -> {
					Relocation.cancelMove(self(ctx));
					return 1;
				})));
		// Sekte Langit (DESIGN 37.7.8): /kultivasi aula langit ...
		root.then(Commands.literal("aula").then(bangun).then(LangitRancang.node()));

		LiteralArgumentBuilder<CommandSourceStack> debugBangun = Commands.literal("bangun");
		debugBangun.then(Commands.literal("tingkat-set").then(Commands.argument("n", IntegerArgumentType.integer(0, SectTemplates.TIERS)).executes(ctx -> {
			ServerPlayer player = self(ctx);
			EngineApi.get().setTierCounter(player.getUUID(), IntegerArgumentType.getInteger(ctx, "n"));
			Halls.refreshBuildings(player.getUUID());
			return done(ctx);
		})));
		debugBangun.then(Commands.literal("komponen").then(Commands.literal("selesai").executes(ctx -> {
			boolean ok = EngineApi.get().finishOpen(self(ctx).getUUID());
			return ok ? done(ctx) : fail(ctx, "kultivator.sectbuild.component.none_open");
		})));
		debugBangun.then(Commands.literal("bank").then(Commands.argument("ticks", IntegerArgumentType.integer(0, 72000)).executes(ctx -> {
			EngineApi.get().setBank(self(ctx).getUUID(), IntegerArgumentType.getInteger(ctx, "ticks"));
			return done(ctx);
		})));
		debugBangun.then(Commands.literal("rencana").then(Commands.literal("ulang").executes(ctx -> {
			EngineApi.get().replan(self(ctx).getUUID());
			return done(ctx);
		})));
		debug.then(Commands.literal("aula").then(debugBangun));
	}

	// ================================================================== test

	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		LiteralArgumentBuilder<CommandSourceStack> sekte = Commands.literal("sekte");
		sekte.then(Commands.literal("templat").then(Commands.argument("t", IntegerArgumentType.integer(1, SectTemplates.COUNT))
				.executes(ctx -> testTemplate(ctx, false, false, false))
				.then(Commands.literal("paksa").executes(ctx -> testTemplate(ctx, false, false, true)))
				.then(Commands.argument("arah", StringArgumentType.word()).suggests(DIRECTION)
						.executes(ctx -> testTemplate(ctx, true, false, false))
						.then(Commands.literal("paksa").executes(ctx -> testTemplate(ctx, true, false, true)))
						.then(Commands.argument("glasir", StringArgumentType.word()).suggests(GLAZE)
								.executes(ctx -> testTemplate(ctx, true, true, false))
								.then(Commands.literal("paksa").executes(ctx -> testTemplate(ctx, true, true, true)))))));
		sekte.then(Commands.literal("tingkat").then(Commands.argument("n", IntegerArgumentType.integer(0, SectTemplates.TIERS))
				.executes(ctx -> testBuild(ctx, 0))
				.then(Commands.literal("instan").executes(ctx -> testBuild(ctx, 1)))
				.then(Commands.literal("cepat").executes(ctx -> testBuild(ctx, 2)))));
		sekte.then(Commands.literal("sumber").executes(ctx -> source(self(ctx), -1))
				.then(Commands.literal("penuh").executes(ctx -> source(self(ctx), -1)))
				.then(Commands.argument("jumlah", IntegerArgumentType.integer(1, (int) Bahan.MAX_UNITS))
						.executes(ctx -> source(self(ctx), IntegerArgumentType.getInteger(ctx, "jumlah")))));
		sekte.then(Commands.literal("pamer")
				.then(Commands.literal("konfirmasi").executes(ctx -> Founding.confirmShowcase(self(ctx)) ? 1 : 0))
				.then(Commands.literal("hapus").executes(ctx -> showcaseRemove(ctx, false))
						.then(Commands.literal("semua").executes(ctx -> showcaseRemove(ctx, true))))
				.then(Commands.literal("daftar").executes(ctx -> showcaseList(self(ctx))))
				.then(Commands.argument("t", IntegerArgumentType.integer(1, SectTemplates.SKY_LAST))
						.executes(ctx -> showcase(ctx, SectTemplates.TIERS, null))
						.then(Commands.literal("tingkat").then(Commands.argument("n", IntegerArgumentType.integer(1, SectTemplates.TIERS))
								.executes(ctx -> showcase(ctx, IntegerArgumentType.getInteger(ctx, "n"), null))
								.then(Commands.argument("arah", StringArgumentType.word()).suggests(DIRECTION)
										.executes(ctx -> showcase(ctx, IntegerArgumentType.getInteger(ctx, "n"), StringArgumentType.getString(ctx, "arah"))))))
						.then(Commands.argument("arah", StringArgumentType.word()).suggests(DIRECTION)
								.executes(ctx -> showcase(ctx, SectTemplates.TIERS, StringArgumentType.getString(ctx, "arah"))))));
		sekte.then(Commands.literal("hologram").then(Commands.argument("t", IntegerArgumentType.integer(1, SectTemplates.SKY_LAST))
				.executes(ctx -> hologram(ctx, SectTemplates.TIERS))
				.then(Commands.argument("n", IntegerArgumentType.integer(1, SectTemplates.TIERS))
						.executes(ctx -> hologram(ctx, IntegerArgumentType.getInteger(ctx, "n"))))));
		sekte.then(Commands.literal("bandingkan").executes(ctx -> {
			EngineApi.get().compareAll(self(ctx));
			return done(ctx);
		}));
		sekte.then(Commands.literal("info").executes(ctx -> siteInfo(self(ctx)))
				.then(Commands.argument("t", IntegerArgumentType.integer(1, SectTemplates.SKY_LAST))
						.executes(ctx -> planInfo(self(ctx), IntegerArgumentType.getInteger(ctx, "t")))));
		sekte.then(Commands.literal("periksa").then(Commands.argument("t", IntegerArgumentType.integer(1, SectTemplates.SKY_LAST))
				.executes(ctx -> check(ctx, null))
				.then(Commands.argument("arah", StringArgumentType.word()).suggests(DIRECTION)
						.executes(ctx -> check(ctx, StringArgumentType.getString(ctx, "arah"))))));
		sekte.then(Commands.literal("audit").executes(ctx -> audit(self(ctx), null))
				.then(Commands.argument("t", IntegerArgumentType.integer(1, SectTemplates.SKY_LAST))
						.executes(ctx -> audit(self(ctx), IntegerArgumentType.getInteger(ctx, "t")))));
		sekte.then(Commands.literal("kamera").then(Commands.argument("view", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
			EngineApi.get().camera(self(ctx), IntegerArgumentType.getInteger(ctx, "view"));
			return done(ctx);
		})));
		// 6b Titik Formasi (DESIGN 24.6.2): lists the built anchors, or queues a pattern on one (a test site builds it free).
		sekte.then(Commands.literal("titik").executes(ctx -> titikList(self(ctx)))
				.then(Commands.argument("titik", StringArgumentType.word()).then(Commands.argument("pola", StringArgumentType.word()).suggests(SPECS)
						.executes(ctx -> titikBuild(self(ctx), StringArgumentType.getString(ctx, "titik"), StringArgumentType.getString(ctx, "pola"))))));
		sekte.then(Commands.literal("upacara").executes(ctx -> ceremony(self(ctx), -1))
				.then(Commands.argument("n", IntegerArgumentType.integer(1, SectTemplates.TIERS))
						.executes(ctx -> ceremony(self(ctx), IntegerArgumentType.getInteger(ctx, "n")))));
		sekte.then(Commands.literal("kecepatan").then(Commands.argument("x", IntegerArgumentType.integer(1, 50)).executes(ctx -> {
			ServerPlayer player = self(ctx);
			Builders.setSpeed(player.getUUID(), IntegerArgumentType.getInteger(ctx, "x"));
			EngineApi.get().setRateMultiplier(player.getUUID(), Builders.factor(player.getUUID()));
			Msg.chat(player, Component.translatable("kultivator.sectbuild.test.speed", IntegerArgumentType.getInteger(ctx, "x")).withStyle(ChatFormatting.AQUA));
			return 1;
		})));
		sekte.then(Commands.literal("naik").then(Commands.argument("t", IntegerArgumentType.integer(2, SectTemplates.COUNT)).executes(ctx -> {
			ServerPlayer player = self(ctx);
			int template = IntegerArgumentType.getInteger(ctx, "t");
			int oldTier = Math.max(EngineApi.get().tier(player.getUUID()), FlowData.guaranteeTier(player.getUUID()));
			Component error = EngineApi.get().testUpgrade(player, template);
			if (error != null) {
				ctx.getSource().sendFailure(error);
				return 0;
			}
			if (oldTier > 0) {
				FlowData.setGuarantee(player.getUUID(), oldTier, FlowData.UNTIL_REACHED, "upgrade");
			}
			// The history line follows from Ceremonies.onConfirmed once the upgrade really begins.
			Halls.refreshBuildings(player.getUUID());
			return done(ctx);
		})));
		sekte.then(Commands.literal("bongkar").executes(ctx -> {
			ServerPlayer player = self(ctx);
			Component error = EngineApi.get().testDeconstruct(player);
			if (error != null) {
				ctx.getSource().sendFailure(error);
				return 0;
			}
			Halls.refreshBuildings(player.getUUID());
			return done(ctx);
		}));
		sekte.then(Commands.literal("reset").executes(ctx -> reset(self(ctx))));
		sekte.then(Commands.literal("misi").executes(ctx -> {
			ServerPlayer player = self(ctx);
			BuildMissions.roll(player.getUUID(), player.getRandom());
			Msg.chat(player, Component.translatable("kultivator.sectbuild.test.offers", BuildMissions.offers(player.getUUID()).size())
					.withStyle(ChatFormatting.AQUA));
			return 1;
		}).then(Commands.literal("selesai").executes(ctx -> {
			ServerPlayer player = self(ctx);
			int count = GatherExpeditions.finishAll(ctx.getSource().getServer(), player.getUUID());
			Msg.chat(player, Component.translatable("kultivator.sectbuild.test.parties", count).withStyle(ChatFormatting.AQUA));
			return count;
		})));
		// Sekte Langit (DESIGN 37.7.8): pamer, hologram, info, periksa and audit take 11-19 too.
		sekte.then(LangitRancang.testNode());
		sekte.then(Commands.literal("pekerja").executes(ctx -> allBuilders(self(ctx))));
		uji.then(sekte);
	}

	// ================================================================== player command bodies

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int done(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.sectbuild.test.done")), false);
		return 1;
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String key) {
		ctx.getSource().sendFailure(Component.translatable(key));
		return 0;
	}

	/** {@code aula bangun}: template, tier, open component, shortfall and state. */
	static int status(ServerPlayer player) {
		UUID owner = player.getUUID();
		EngineApi engine = EngineApi.get();
		int template = engine.template(owner);
		if (template <= 0) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.status.none").withStyle(ChatFormatting.GRAY));
			player.sendSystemMessage(Ceremonies.button("kultivator.sectbuild.button.choose_template", "/kultivasi aula bangun templat"));
			return 0;
		}
		SiteView site = engine.site(owner);
		int tier = engine.tier(owner);
		player.sendSystemMessage(Component.translatable("kultivator.sectbuild.status.header", SectTemplates.name(template), tier, SectTemplates.TIERS,
				tier < SectTemplates.TIERS ? SectTemplates.tierName(tier + 1) : SectTemplates.tierName(SectTemplates.TIERS))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (site != null) {
			player.sendSystemMessage(SiteStatus.statusText(owner, site).withStyle(SiteStatus.color(site.status())));
			player.sendSystemMessage(Component.translatable("kultivator.sectbuild.status.rate", String.format(Locale.ROOT, "%.1f", site.rate()),
					Builders.activeCount(owner), site.bank() > 0L ? Fmt.clock(site.bank()) : "-").withStyle(ChatFormatting.GRAY));
		}
		List<ComponentView> components = engine.components(owner);
		ComponentView open = SiteStatus.open(components);
		if (open != null) {
			player.sendSystemMessage(Component.translatable("kultivator.sectbuild.status.open", open.name(), Fmt.percent(open.progress(), 1.0D))
					.withStyle(ChatFormatting.AQUA));
		}
		Bill shortfall = SiteStatus.nextShortfall(owner);
		ComponentView next = SiteStatus.next(components);
		if (next != null && !shortfall.isEmpty()) {
			player.sendSystemMessage(Component.translatable("kultivator.sectbuild.status.short", next.name(), Fmt.joined(shortfall, 6))
					.withStyle(ChatFormatting.YELLOW));
			player.sendSystemMessage(Ceremonies.button("kultivator.sectbuild.button.deposit_needed", "/kultivasi aula bangun setor butuh"));
		}
		return 1;
	}

	private static int preview(CommandContext<CommandSourceStack> ctx, boolean withDirection, boolean withGlaze) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		int template = IntegerArgumentType.getInteger(ctx, "t");
		int rot = withDirection ? rotation(StringArgumentType.getString(ctx, "arah")) : -1;
		Glaze glaze = withGlaze ? Glaze.byId(StringArgumentType.getString(ctx, "glasir")) : null;
		return Founding.preview(player, template, rot, glaze, Founding.Mode.FOUND, false) ? 1 : 0;
	}

	/** "utara" ... "barat" as the rotation whose gate faces that way (-1 when unknown). */
	static int rotation(String word) {
		return switch (word.toLowerCase(Locale.ROOT)) {
			case "utara", "north" -> SectTemplates.rotFor(Direction.NORTH);
			case "timur", "east" -> SectTemplates.rotFor(Direction.EAST);
			case "selatan", "south" -> SectTemplates.rotFor(Direction.SOUTH);
			case "barat", "west" -> SectTemplates.rotFor(Direction.WEST);
			default -> -1;
		};
	}

	private static int depositHeld(ServerPlayer player) {
		UUID owner = player.getUUID();
		if (Halls.hallOf(owner) == null) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.no_hall").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		Deposits.depositOne(player, owner, player.getInventory().getSelectedSlot());
		return 1;
	}

	private static int pause(ServerPlayer player, boolean paused) {
		if (!EngineApi.get().pause(player.getUUID(), paused)) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.upgrade.no_template").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		Msg.chat(player, Component.translatable(paused ? "kultivator.sectbuild.paused" : "kultivator.sectbuild.resumed").withStyle(ChatFormatting.AQUA));
		return 1;
	}

	/** 0 prioritas, 1 lewati, 2 tanpa-terhalang. */
	private static int componentOp(ServerPlayer player, String id, int op) {
		OpResult result = componentAction(player.getUUID(), id, op);
		Msg.chat(player, result.message());
		return result.ok() ? 1 : 0;
	}

	/** The outcome of a component action and its message (aqua when done, yellow when refused). */
	public record OpResult(boolean ok, Component message) {
	}

	/**
	 * Runs 0 prioritas, 1 lewati or 2 tanpa-terhalang for the owner's component. Prioritizing a skipped component queues
	 * it again; skipping is refused for a component that is neither blocked nor waiting for materials, and its message
	 * says that a skipped part does not count for the tier.
	 */
	public static OpResult componentAction(UUID owner, String id, int op) {
		EngineApi engine = EngineApi.get();
		int before = -1;
		for (ComponentView view : engine.components(owner)) {
			if (view.id().equals(id)) {
				before = view.state();
				break;
			}
		}
		boolean ok = switch (op) {
			case 0 -> engine.prioritize(owner, id);
			case 1 -> engine.skip(owner, id);
			default -> engine.finishWithoutBlocked(owner, id);
		};
		String key;
		if (ok) {
			key = switch (op) {
				case 0 -> before == ComponentView.STATE_SKIPPED ? "kultivator.sectbuild.prio.requeued" : "kultivator.sectbuild.prio.done";
				case 1 -> "kultivator.sectbuild.skip.done_rules";
				default -> "kultivator.sectbuild.finish_blocked.done";
			};
		} else if (op == 1 && before >= 0 && before != ComponentView.STATE_DONE && before != ComponentView.STATE_SKIPPED) {
			key = "kultivator.sectbuild.skip.not_allowed";
		} else {
			key = "kultivator.sectbuild.component.not_found";
		}
		return new OpResult(ok, Component.translatable(key).withStyle(ok ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
	}

	/** Periksa &amp; Perbaiki: inspects the finished components (off the server thread); the engine reports what it queued. */
	public static int repair(ServerPlayer player) {
		int inspected = EngineApi.get().repair(player.getUUID());
		if (inspected == -2) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.repair.busy").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (inspected < 0) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.upgrade.no_template").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		Msg.chat(player, Component.translatable(inspected == 0 ? "kultivator.sectbuild.repair.none" : "kultivator.sectbuild.repair.queued", inspected)
				.withStyle(ChatFormatting.AQUA));
		return inspected;
	}

	private static int deconstruct(ServerPlayer player, boolean total, boolean yes) {
		Relocation.deconstruct(player, total, yes);
		return 1;
	}

	// ================================================================== test command bodies

	private static int testTemplate(CommandContext<CommandSourceStack> ctx, boolean withDirection, boolean withGlaze, boolean force)
			throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		if (Halls.hallOf(player.getUUID()) == null) {
			Deposits.give(player, new ItemStack(HallItems.SECT_HALL_STELE_ITEM));
			Msg.chat(player, Component.translatable("kultivator.sectbuild.test.no_hall").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		int template = IntegerArgumentType.getInteger(ctx, "t");
		int rot = withDirection ? rotation(StringArgumentType.getString(ctx, "arah")) : -1;
		Glaze glaze = withGlaze ? Glaze.byId(StringArgumentType.getString(ctx, "glasir")) : null;
		return Founding.preview(player, template, rot, glaze, Founding.Mode.TEST, force) ? 1 : 0;
	}

	private static int testBuild(CommandContext<CommandSourceStack> ctx, int mode) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		Component error = EngineApi.get().testBuild(player, IntegerArgumentType.getInteger(ctx, "n"), mode);
		if (error != null) {
			ctx.getSource().sendFailure(error);
			return 0;
		}
		Halls.refreshBuildings(player.getUUID());
		return done(ctx);
	}

	/** {@code sumber penuh}: the remaining bill + 10% and all exact items; a number: that many units in every category. */
	private static int source(ServerPlayer player, int units) {
		UUID owner = player.getUUID();
		if (Halls.hallOf(owner) == null) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.no_hall").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (units > 0) {
			for (Bahan cat : Bahan.values()) {
				Ledger.INSTANCE.add(owner, cat, units * Bahan.CENTI);
			}
			Msg.chat(player, Component.translatable("kultivator.sectbuild.test.source", units).withStyle(ChatFormatting.AQUA));
			return 1;
		}
		Bill need = EngineApi.get().template(owner) > 0 ? EngineApi.get().remainingBill(owner, 0) : Bill.EMPTY;
		Bill.Builder extra = Bill.builder();
		for (Bahan cat : Bahan.values()) {
			extra.add(cat, need.centi(cat) + need.centi(cat) / 10L);
		}
		need.exact().forEach(extra::add);
		Ledger.INSTANCE.grant(owner, 0L, extra.build());
		Msg.chat(player, Component.translatable("kultivator.sectbuild.test.source_full", Fmt.joined(need, 9)).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int showcase(CommandContext<CommandSourceStack> ctx, int tier, @Nullable String direction) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		int template = IntegerArgumentType.getInteger(ctx, "t");
		BlockHitResult hit = Targeting.blockHit(player, 64.0D);
		BlockPos origin = hit.getType() == HitResult.Type.MISS ? player.blockPosition() : hit.getBlockPos().above();
		int rot = direction == null ? -1 : rotation(direction);
		return Founding.previewAt(player, template, tier, rot, origin, Founding.Mode.SHOWCASE) ? 1 : 0;
	}

	private static int showcaseRemove(CommandContext<CommandSourceStack> ctx, boolean all) throws CommandSyntaxException {
		int removed = EngineApi.get().showcaseRemove(self(ctx), all);
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.sectbuild.test.showcase_removed", removed)), false);
		return removed;
	}

	private static int showcaseList(ServerPlayer player) {
		List<SiteView> list = EngineApi.get().showcases();
		Msg.chat(player, Component.translatable("kultivator.sectbuild.test.showcases", list.size()).withStyle(ChatFormatting.GOLD));
		for (SiteView site : list) {
			player.sendSystemMessage(Component.translatable("kultivator.sectbuild.test.showcase_line", SectTemplates.name(site.template()), site.tier(),
					site.stele().getX() + " " + site.stele().getY() + " " + site.stele().getZ()).withStyle(ChatFormatting.GRAY));
		}
		return list.size();
	}

	private static int hologram(CommandContext<CommandSourceStack> ctx, int tier) throws CommandSyntaxException {
		EngineApi.get().hologram(self(ctx), IntegerArgumentType.getInteger(ctx, "t"), tier);
		return done(ctx);
	}

	private static int check(CommandContext<CommandSourceStack> ctx, @Nullable String direction) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		int template = IntegerArgumentType.getInteger(ctx, "t");
		int rot = direction == null ? -1 : rotation(direction);
		return Founding.previewAt(player, template, SectTemplates.TIERS, rot, player.blockPosition(), Founding.Mode.CHECK) ? 1 : 0;
	}

	/** {@code uji sekte info}: the own site. */
	private static int siteInfo(ServerPlayer player) {
		UUID owner = player.getUUID();
		int result = status(player);
		SiteView site = EngineApi.get().site(owner);
		if (site != null) {
			player.sendSystemMessage(Component.translatable("kultivator.sectbuild.test.site", SectTemplates.id(site.template()), site.rotation(),
					site.glaze().id(), site.state(), site.status(), site.blocked(), site.openId() == null ? "-" : site.openId(), site.test())
					.withStyle(ChatFormatting.GRAY));
		}
		player.sendSystemMessage(Component.translatable("kultivator.sectbuild.test.ledger", Fmt.joined(Ledger.INSTANCE.balance(owner), 12))
				.withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.translatable("kultivator.sectbuild.test.coupling", dev.kultivator.sect.build.SectBuild.buildingCap(owner), FlowData.frozenScanCap(owner),
				FlowData.guaranteeTier(owner)).withStyle(ChatFormatting.GRAY));
		return result;
	}

	/** {@code uji sekte titik}: the finished Titik Formasi anchors of the player's site and what stands on each. */
	private static int titikList(ServerPlayer player) {
		List<FormasiAnchor> anchors = SectBuildBridge.anchors(player.getUUID());
		if (anchors.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.test.titik_none").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		for (FormasiAnchor anchor : anchors) {
			player.sendSystemMessage(Component.translatable("kultivator.sectbuild.test.titik_row", anchor.id(), anchor.centre().getX(), anchor.centre().getY(),
					anchor.centre().getZ(), anchor.radius(), anchor.tier(), anchor.pola().isEmpty() ? "-" : anchor.pola() + "/" + anchor.state()).withStyle(ChatFormatting.GRAY));
		}
		return anchors.size();
	}

	/** {@code uji sekte titik <id> <pola>}: asks the engine for the pattern on an anchor (rotation: the way the player faces). */
	private static int titikBuild(ServerPlayer player, String anchorId, String specId) {
		FormasiAnchor anchor = null;
		for (FormasiAnchor each : SectBuildBridge.anchors(player.getUUID())) {
			if (each.id().equals(anchorId)) {
				anchor = each;
			}
		}
		FormationSpec spec = FormationRegistry.byId(specId);
		if (anchor == null || spec == null) {
			Msg.chat(player, Component.translatable("kultivator.formasi.pola.bad").withStyle(ChatFormatting.RED));
			return 0;
		}
		int rot = switch (player.getDirection()) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
		Component why = SectBuildBridge.request(player.getUUID(), anchor, spec, rot);
		Msg.chat(player, why != null ? why.copy().withStyle(ChatFormatting.YELLOW)
				: Component.translatable("kultivator.formasi.pola.queued", spec.name(), anchor.id()).withStyle(ChatFormatting.GREEN));
		return why == null ? 1 : 0;
	}

	/** {@code uji sekte info <t>}: the real plan numbers. */
	private static int planInfo(ServerPlayer player, int template) {
		PlanStats stats = EngineApi.get().stats(template);
		if (stats.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.test.no_stats", SectTemplates.name(template)).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		player.sendSystemMessage(Component.translatable("kultivator.sectbuild.test.plan", SectTemplates.name(template), stats.width(), stats.depth(),
				stats.above(), stats.below(), stats.totalComponents(), stats.totalOps(), String.format(Locale.ROOT, "%.0f", stats.minutes()))
				.withStyle(ChatFormatting.GOLD));
		for (int tier = 1; tier <= SectTemplates.TIERS; tier++) {
			player.sendSystemMessage(Component.translatable("kultivator.sectbuild.test.plan_tier", tier, stats.components(tier), stats.blocks(tier),
					stats.ops(tier), Fmt.joined(stats.bill(tier), 12)).withStyle(ChatFormatting.GRAY));
		}
		player.sendSystemMessage(Component.translatable("kultivator.sectbuild.test.plan_total", Fmt.joined(stats.total(), 24)).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int audit(ServerPlayer player, @Nullable Integer template) {
		AuditReport report = EngineApi.get().audit(template);
		if (report.done() || !EngineApi.registered()) {
			printAudit(player, report);
			return report.ok() ? 1 : 0;
		}
		AUDITS.put(player.getUUID(), new AuditJob(player.getUUID(), template, 0));
		Msg.chat(player, Component.translatable("kultivator.sectbuild.test.audit_running").withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static void printAudit(ServerPlayer player, AuditReport report) {
		Component which = report.template() == null ? Component.translatable("kultivator.sectbuild.test.audit_all") : SectTemplates.name(report.template());
		player.sendSystemMessage(Component.translatable("kultivator.sectbuild.test.audit", which, report.emissions(), report.violations(), report.millis())
				.withStyle(report.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
		for (Component line : report.lines()) {
			player.sendSystemMessage(line.copy().withStyle(style -> style.getColor() == null ? style.withColor(ChatFormatting.GRAY) : style));
		}
	}

	/** Polls running audits every 2 s (at most 15 minutes). */
	static void tick(MinecraftServer server) {
		if (AUDITS.isEmpty() || server.getTickCount() % AUDIT_POLL != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, AuditJob>> it = AUDITS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, AuditJob> entry = it.next();
			AuditJob job = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(job.player());
			if (player == null || job.polls() >= AUDIT_POLLS) {
				it.remove();
				continue;
			}
			AuditReport report;
			try {
				report = EngineApi.get().audit(job.template());
			} catch (RuntimeException exception) {
				Kultivator.LOGGER.error("Polling a sect audit failed", exception);
				it.remove();
				continue;
			}
			if (report.done()) {
				it.remove();
				printAudit(player, report);
			} else {
				entry.setValue(new AuditJob(job.player(), job.template(), job.polls() + 1));
			}
		}
	}

	private static int ceremony(ServerPlayer player, int tier) {
		UUID owner = player.getUUID();
		EngineApi engine = EngineApi.get();
		int template = Math.max(1, engine.template(owner));
		int shown = tier > 0 ? tier : Math.max(1, engine.tier(owner));
		Ceremonies.playTier(owner, template, shown, false);
		return 1;
	}

	private static int reset(ServerPlayer player) {
		UUID owner = player.getUUID();
		if (!EngineApi.get().reset(owner)) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.upgrade.no_template").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		Builders.releaseAll(owner);
		FlowData.closeHistory(owner);
		FlowData.clearGuarantee(owner);
		FlowData.setFrozenScanCap(owner, -1);
		FlowData.of(owner).remove("missions");
		FlowData.dirty();
		Halls.refreshBuildings(owner);
		Msg.chat(player, Component.translatable("kultivator.sectbuild.test.reset").withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int allBuilders(ServerPlayer player) {
		UUID owner = player.getUUID();
		int count = 0;
		for (DiscipleEntity disciple : Disciples.loaded(owner)) {
			if (disciple.role() != DiscipleEntity.Role.DISCIPLE || Builders.souls(owner).size() >= Builders.MAX) {
				continue;
			}
			if (Builders.isBuilder(owner, disciple.soulId()) || Builders.blocker(disciple) == null && Builders.assign(player, owner, disciple)) {
				count++;
			}
		}
		Msg.chat(player, Component.translatable("kultivator.sectbuild.test.builders", count, Builders.MAX).withStyle(ChatFormatting.AQUA));
		return count;
	}

	static void clearSession() {
		AUDITS.clear();
	}

	private BuildCommands() {
	}
}
