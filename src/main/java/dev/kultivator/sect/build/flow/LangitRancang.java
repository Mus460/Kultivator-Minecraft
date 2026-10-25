package dev.kultivator.sect.build.flow;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Realm;
import dev.kultivator.kehancuran.WorldBreaker;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.api.FootMode;
import dev.kultivator.sect.build.api.SectTemplates;
import dev.kultivator.sect.build.api.SiteView;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sect.build.engine.BuildEngine;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Designing a sky hall (DESIGN 37.3.1-37.3.4) and the {@code /kultivasi aula langit} commands (DESIGN 37.7.8).
 * <ul>
 * <li>The gate of 37.3.3: the leader's realm (config {@code sekteLangit.gerbangRanah}), and for path A (a ground site
 * already stands) ground tier 6, hall level 6, an active Grand Formation, {@code n <= g + 1}, no raid, no Kehancuran, the
 * ground site not moving, upgrading or coming down; path B (no site yet) only up to {@code awanAwalMaks}.</li>
 * <li>The design per player: sky template, rotation, plaza height {@code yP} (band of {@link LangitPlan}) and the shift
 * {@code dx, dz} from the ground stele; every change asks the engine for a new preview through {@link Founding}, whose
 * hologram, consent and confirm are shared with the ground.</li>
 * <li>The offering ({@link LangitSesaji}) is taken when the design is confirmed and given back when it is dropped before
 * the first block stands.</li>
 * </ul>
 * Source: <i>Journey to the West</i> ch. 4 (the register of heaven) for the invitation; the rest is game design.
 */
public final class LangitRancang {

	/** An invitation lasts this many world days. */
	public static final int INVITE_DAYS = 1;

	/** One player's sky design. */
	private static final class Design {
		int sky;
		int rot;
		int yP;
		int dx;
		int dz;
		boolean test;

		Design(int sky, int rot, int yP, int dx, int dz, boolean test) {
			this.sky = sky;
			this.rot = rot;
			this.yP = yP;
			this.dx = dx;
			this.dz = dz;
			this.test = test;
		}
	}

	private static final Map<UUID, Design> DESIGNS = new HashMap<>();

	// ================================================================== gate

	/** The leader's realm a sky template asks for (DESIGN 37.3.3). */
	public static Realm realmFor(int sky) {
		int n = sky > 10 ? sky - 10 : sky;
		if (n <= 3) {
			return Realm.GOLDEN_CORE;
		}
		if (n <= 6) {
			return Realm.NASCENT_SOUL;
		}
		return n <= 8 ? Realm.DEITY_TRANSFORMATION : Realm.DAO_UNION;
	}

	/** Why {@code player} may not design sky template {@code sky} now (founding), or null. */
	static @Nullable Component blocker(ServerPlayer player, int sky) {
		if (!LangitConfig.on()) {
			return Component.translatable("kultivator.sekte_langit.bangun.mati");
		}
		if (!SectTemplates.isSky(sky)) {
			return Component.translatable("kultivator.sectbuild.template.unknown");
		}
		UUID owner = player.getUUID();
		EngineApi engine = EngineApi.get();
		int n = sky - 10;
		if (LangitConfig.get().gerbangRanah && Cultivators.get(player).realm().ordinal() < realmFor(sky).ordinal()) {
			return Component.translatable("kultivator.sekte_langit.bangun.ranah", SectTemplates.name(sky), realmFor(sky).displayName());
		}
		SiteView skySite = engine.skySite(owner);
		if (skySite != null && !(BuildEngine.INSTANCE.skyUntouched(owner) && skySite.tethered())) {
			return Component.translatable("kultivator.sekte_langit.bangun.sudah", SectTemplates.name(skySite.template()));
		}
		int current = engine.template(owner);
		if (SectTemplates.isSky(current) && skySite == null) {
			return Component.translatable("kultivator.sekte_langit.bangun.sudah", SectTemplates.name(current));
		}
		if (current <= 0) {
			// Path B: founding straight on the clouds, small designs only.
			int max = LangitConfig.get().awanAwalMaks;
			return n <= max ? null : Component.translatable("kultivator.sekte_langit.bangun.jalan_b", max);
		}
		if (SectTemplates.isSky(current)) {
			// Redesigning an untouched path B island: still small designs only.
			int max = LangitConfig.get().awanAwalMaks;
			return n <= max ? null : Component.translatable("kultivator.sekte_langit.bangun.jalan_b", max);
		}
		// Path A: lifting the mountain.
		if (SectRaids.active(owner)) {
			return Component.translatable("kultivator.sekte_langit.bangun.serbuan");
		}
		if (WorldBreaker.busy()) {
			return Component.translatable("kultivator.sekte_langit.bangun.kehancuran");
		}
		SiteView ground = engine.site(owner);
		if (ground != null && (ground.state() == SiteView.STATE_RELOCATING || ground.state() == SiteView.STATE_UPGRADING
				|| ground.state() == SiteView.STATE_DECONSTRUCTING)) {
			return Component.translatable("kultivator.sekte_langit.bangun.sibuk");
		}
		if (n > current + 1) {
			return Component.translatable("kultivator.sekte_langit.bangun.loncat", SectTemplates.name(current + 1 + 10));
		}
		if (engine.tier(owner) < 6) {
			return Component.translatable("kultivator.sekte_langit.bangun.tingkat", 6);
		}
		if (Halls.level(owner) < 6) {
			return Component.translatable("kultivator.sekte_langit.bangun.level", 6);
		}
		if (!LangitBangun.grandActive(player.level().getServer(), owner)) {
			return Component.translatable("kultivator.sekte_langit.bangun.formasi");
		}
		return null;
	}

	// ================================================================== the design

	/** "Rancang A{@code n}": a fresh design over the ground stele at the default height, gate facing the player. */
	public static boolean design(ServerPlayer player, int n, boolean test) {
		int sky = n + 10;
		Halls.HallView hall = Halls.hallOf(player.getUUID());
		ServerLevel level = hall == null ? null : player.level().getServer().getLevel(hall.dim());
		if (hall == null || level == null) {
			Msg.chat(player, Component.translatable("kultivator.sectbuild.no_hall").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		int rot = Founding.autoRotation(player, hall.pos());
		Design design = new Design(sky, rot, LangitPlan.defaultHeight(level, sky), 0, 0, test);
		DESIGNS.put(player.getUUID(), design);
		return show(player, design);
	}

	private static boolean show(ServerPlayer player, Design design) {
		boolean started = Founding.previewSky(player, design.sky, design.rot, design.yP, design.dx, design.dz, design.test);
		if (started) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.rancang.letak", SectTemplates.name(design.sky), design.yP, design.dx, design.dz)
					.withStyle(ChatFormatting.AQUA));
		}
		return started;
	}

	private static @Nullable Design current(ServerPlayer player) {
		Design design = DESIGNS.get(player.getUUID());
		if (design == null) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.rancang.tidak_ada").withStyle(ChatFormatting.YELLOW));
		}
		return design;
	}

	/** "Putar" of a sky design (called by {@code Founding.rotate}). */
	static void turn(ServerPlayer player) {
		Design design = current(player);
		if (design != null) {
			design.rot = (design.rot + 1) % 4;
			show(player, design);
		}
	}

	/** The plaza height: an absolute y, clamped to the level (the validator marks a height out of the band red). */
	static void height(ServerPlayer player, int y) {
		Design design = current(player);
		if (design == null) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		design.yP = Math.max(LangitPlan.PLAZA_MIN, Math.min(LangitPlan.bandMax(level, design.sky), y));
		show(player, design);
	}

	public static void heightStep(ServerPlayer player, int steps) {
		Design design = current(player);
		if (design != null) {
			height(player, design.yP + steps * LangitPlan.HEIGHT_STEP);
		}
	}

	/** The throne's shift from the ground stele, each axis clamped to {@code offsetMaks}. */
	static void shift(ServerPlayer player, int dx, int dz) {
		Design design = current(player);
		if (design == null) {
			return;
		}
		int max = LangitPlan.offsetMax();
		design.dx = Math.max(-max, Math.min(max, dx));
		design.dz = Math.max(-max, Math.min(max, dz));
		show(player, design);
	}

	/** Forgets the player's design (the preview went or the site was founded). */
	static void forget(UUID player) {
		DESIGNS.remove(player);
	}

	/** The design's numbers for the screen and the hologram: {sky, rot, yP, dx, dz}, or null. */
	public static int @Nullable [] numbers(UUID player) {
		Design design = DESIGNS.get(player);
		return design == null ? null : new int[]{design.sky, design.rot, design.yP, design.dx, design.dz};
	}

	/** "Bubarkan rancangan": the tethered sky site goes; untouched with the offering back, else down with refunds. */
	static int dissolve(ServerPlayer player) {
		UUID owner = player.getUUID();
		boolean untouched = BuildEngine.INSTANCE.skyUntouched(owner);
		if (!BuildEngine.INSTANCE.releaseSky(owner, false)) {
			Msg.chat(player, Component.translatable("kultivator.blueprint.error.langit_no_sky").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		if (untouched) {
			LangitSesaji.refund(player);
		} else {
			LangitSesaji.settle(owner);
		}
		Halls.log(owner, Component.translatable("kultivator.sekte_langit.log.bubar"));
		Msg.chat(player, Component.translatable(untouched ? "kultivator.sekte_langit.rancang.bubar" : "kultivator.sekte_langit.rancang.bubar_bongkar")
				.withStyle(ChatFormatting.GRAY));
		return 1;
	}

	// ================================================================== info

	static int info(ServerPlayer player) {
		UUID owner = player.getUUID();
		SkyHallView view = LangitApi.get().skyHall(owner);
		SiteView site = EngineApi.get().skySite(owner);
		if (view == null && site == null) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.info.tidak_ada").withStyle(ChatFormatting.GRAY));
			Msg.chat(player, Ceremonies.button("kultivator.sectbuild.button.langit_rancang", "/kultivasi aula langit sesaji"));
			return 0;
		}
		int template = view != null ? view.template() : site.template();
		int tier = view != null ? view.tier() : site.tier();
		Msg.chat(player, Component.translatable("kultivator.sekte_langit.info.kepala", SectTemplates.name(template), tier, SectTemplates.TIERS)
				.withStyle(ChatFormatting.AQUA));
		if (view != null) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.info.letak", view.origin().getX(), view.yP(), view.origin().getZ(),
					view.skyFloorY()).withStyle(ChatFormatting.GRAY));
			String mode = view.tethered() ? view.ascent() == 1 ? "menanti" : "tertambat" : "naik";
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.info.mode." + mode).withStyle(ChatFormatting.GRAY));
			Msg.chat(player, Component.translatable(view.gateClosed() ? "kultivator.sekte_langit.info.gerbang_tutup" : "kultivator.sekte_langit.info.gerbang_buka")
					.withStyle(ChatFormatting.GRAY));
			if (view.grandFormation()) {
				Msg.chat(player, Component.translatable("kultivator.sekte_langit.info.formasi").withStyle(ChatFormatting.GOLD));
			}
		}
		if (BuildEngine.INSTANCE.hasFoot(owner)) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.info.kaki").withStyle(ChatFormatting.GRAY));
		}
		if (LangitSesaji.held(owner)) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.info.sesaji_tertahan").withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	static int bill(ServerPlayer player, int n) {
		int sky = n + 10;
		LangitSesaji.Bill bill = LangitSesaji.bill(sky);
		Msg.chat(player, Component.translatable("kultivator.sekte_langit.sesaji.tagihan", SectTemplates.name(sky), LangitSesaji.describe(bill))
				.withStyle(ChatFormatting.GOLD));
		MutableComponent missing = LangitSesaji.missing(player, bill);
		Msg.chat(player, missing == null ? Component.translatable("kultivator.sekte_langit.sesaji.cukup").withStyle(ChatFormatting.GREEN)
				: Component.translatable("kultivator.sekte_langit.sesaji.kurang", missing).withStyle(ChatFormatting.RED));
		Component why = blocker(player, sky);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.YELLOW));
		} else {
			Msg.chat(player, Ceremonies.button("kultivator.sectbuild.button.langit_rancang", "/kultivasi aula langit rancang " + n));
		}
		return 1;
	}

	// ================================================================== the gate and the register

	private static boolean ownsSky(ServerPlayer player) {
		if (LangitApi.get().skyHall(player.getUUID()) == null) {
			Msg.chat(player, Component.translatable("kultivator.blueprint.error.langit_no_sky").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		return true;
	}

	static int gate(ServerPlayer player, boolean closed) {
		if (!ownsSky(player)) {
			return 0;
		}
		LangitBangun.setGateClosed(player.getUUID(), closed);
		Msg.chat(player, Component.translatable(closed ? "kultivator.sekte_langit.gerbang.tutup" : "kultivator.sekte_langit.gerbang.buka")
				.withStyle(ChatFormatting.AQUA));
		if (closed) {
			SkyHallView view = LangitApi.get().skyHall(player.getUUID());
			if (view != null && !view.grandFormation() && LangitApi.get().anchor(player.getUUID(), "gerbang_langit") == null) {
				Msg.chat(player, Component.translatable("kultivator.sekte_langit.gerbang.belum_berdiri").withStyle(ChatFormatting.GRAY));
			}
		}
		return 1;
	}

	static int invite(ServerPlayer player, ServerPlayer guest) {
		if (!ownsSky(player)) {
			return 0;
		}
		LangitBangun.invite(player.getUUID(), guest.getUUID(), FlowData.day() + INVITE_DAYS);
		Msg.chat(player, Component.translatable("kultivator.sekte_langit.undang.dikirim", guest.getName()).withStyle(ChatFormatting.AQUA));
		Msg.chat(guest, Component.translatable("kultivator.sekte_langit.undang.diterima", player.getName()).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	static int foot(ServerPlayer player, boolean dismantle) {
		if (!BuildEngine.INSTANCE.footAction(player.getUUID(), dismantle)) {
			Msg.chat(player, Component.translatable("kultivator.sekte_langit.kaki.tidak_ada").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		Msg.chat(player, Component.translatable(dismantle ? "kultivator.sekte_langit.kaki.bongkar" : "kultivator.sekte_langit.kaki.lepas")
				.withStyle(ChatFormatting.GRAY));
		Halls.log(player.getUUID(), Component.translatable(dismantle ? "kultivator.sekte_langit.kaki.bongkar" : "kultivator.sekte_langit.kaki.lepas"));
		return 1;
	}

	// ================================================================== commands

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	/** {@code /kultivasi aula langit ...} (merged into the {@code aula} node by {@code BuildCommands}). */
	static LiteralArgumentBuilder<CommandSourceStack> node() {
		LiteralArgumentBuilder<CommandSourceStack> langit = Commands.literal("langit").executes(ctx -> info(self(ctx)));
		langit.then(Commands.literal("info").executes(ctx -> info(self(ctx))));
		langit.then(Commands.literal("rancang").then(Commands.argument("n", IntegerArgumentType.integer(1, SectTemplates.COUNT))
				.executes(ctx -> design(self(ctx), IntegerArgumentType.getInteger(ctx, "n"), false) ? 1 : 0)));
		langit.then(Commands.literal("sesaji").executes(ctx -> {
			int[] numbers = numbers(self(ctx).getUUID());
			return bill(self(ctx), numbers == null ? 1 : numbers[0] - 10);
		}).then(Commands.argument("n", IntegerArgumentType.integer(1, SectTemplates.COUNT))
				.executes(ctx -> bill(self(ctx), IntegerArgumentType.getInteger(ctx, "n")))));
		langit.then(Commands.literal("tinggi")
				.then(Commands.literal("naik").executes(ctx -> {
					heightStep(self(ctx), 1);
					return 1;
				}))
				.then(Commands.literal("turun").executes(ctx -> {
					heightStep(self(ctx), -1);
					return 1;
				}))
				.then(Commands.argument("y", IntegerArgumentType.integer(LangitPlan.PLAZA_MIN, 2032)).executes(ctx -> {
					height(self(ctx), IntegerArgumentType.getInteger(ctx, "y"));
					return 1;
				})));
		langit.then(Commands.literal("geser").then(Commands.argument("dx", IntegerArgumentType.integer(-96, 96))
				.then(Commands.argument("dz", IntegerArgumentType.integer(-96, 96)).executes(ctx -> {
					shift(self(ctx), IntegerArgumentType.getInteger(ctx, "dx"), IntegerArgumentType.getInteger(ctx, "dz"));
					return 1;
				}))));
		langit.then(Commands.literal("putar").executes(ctx -> {
			turn(self(ctx));
			return 1;
		}));
		langit.then(Commands.literal("pratinjau").executes(ctx -> {
			Design design = current(self(ctx));
			return design != null && show(self(ctx), design) ? 1 : 0;
		}));
		langit.then(Commands.literal("konfirmasi").executes(ctx -> {
			Founding.askConsent(self(ctx));
			return 1;
		}).then(Commands.literal("ya").executes(ctx -> Founding.confirm(self(ctx), 0L) ? 1 : 0)));
		langit.then(Commands.literal("batal").executes(ctx -> {
			Founding.cancel(self(ctx), true);
			forget(self(ctx).getUUID());
			return 1;
		}));
		langit.then(Commands.literal("bubarkan").executes(ctx -> {
			ServerPlayer player = self(ctx);
			Msg.chat(player, Component.translatable(BuildEngine.INSTANCE.skyUntouched(player.getUUID()) ? "kultivator.sekte_langit.rancang.bubar_tanya"
					: "kultivator.sekte_langit.rancang.bubar_tanya_bongkar").withStyle(ChatFormatting.GOLD));
			Msg.chat(player, Ceremonies.button("kultivator.sectbuild.button.langit_bubar", "/kultivasi aula langit bubarkan ya"));
			return 1;
		}).then(Commands.literal("ya").executes(ctx -> dissolve(self(ctx)))));
		langit.then(Commands.literal("naik")
				.then(Commands.literal("tetap").executes(ctx -> {
					LangitAngkat.confirm(self(ctx), FootMode.TETAP);
					return 1;
				}))
				.then(Commands.literal("bongkar").executes(ctx -> {
					LangitAngkat.confirm(self(ctx), FootMode.BONGKAR);
					return 1;
				}))
				.then(Commands.literal("tinggalkan").executes(ctx -> {
					LangitAngkat.confirm(self(ctx), FootMode.TINGGALKAN);
					return 1;
				}))
				.then(Commands.literal("batal").executes(ctx -> {
					LangitAngkat.cancel(self(ctx));
					return 1;
				})));
		langit.then(Commands.literal("kaki")
				.then(Commands.literal("bongkar").executes(ctx -> foot(self(ctx), true)))
				.then(Commands.literal("lepaskan").executes(ctx -> foot(self(ctx), false))));
		langit.then(Commands.literal("gerbang")
				.then(Commands.literal("tutup").executes(ctx -> gate(self(ctx), true)))
				.then(Commands.literal("buka").executes(ctx -> gate(self(ctx), false))));
		langit.then(Commands.literal("undang").then(Commands.argument("pemain", EntityArgument.player())
				.executes(ctx -> invite(self(ctx), EntityArgument.getPlayer(ctx, "pemain")))));
		return langit;
	}

	/** {@code /kultivasi uji sekte langit ...} (op): a test design, building tiers quickly, rising at once. */
	static LiteralArgumentBuilder<CommandSourceStack> testNode() {
		LiteralArgumentBuilder<CommandSourceStack> langit = Commands.literal("langit");
		langit.then(Commands.literal("templat").then(Commands.argument("n", IntegerArgumentType.integer(1, SectTemplates.COUNT))
				.executes(ctx -> design(self(ctx), IntegerArgumentType.getInteger(ctx, "n"), true) ? 1 : 0)));
		langit.then(Commands.literal("tingkat").then(Commands.argument("t", IntegerArgumentType.integer(0, SectTemplates.TIERS))
				.executes(ctx -> testTier(ctx, 0))
				.then(Commands.literal("instan").executes(ctx -> testTier(ctx, 1)))
				.then(Commands.literal("cepat").executes(ctx -> testTier(ctx, 2)))));
		langit.then(Commands.literal("naik").executes(ctx -> testRise(ctx, false))
				.then(Commands.literal("paksa").executes(ctx -> testRise(ctx, true))));
		langit.then(Commands.literal("info").executes(ctx -> info(self(ctx))));
		return langit;
	}

	private static int testTier(CommandContext<CommandSourceStack> ctx, int mode) throws CommandSyntaxException {
		Component error = EngineApi.get().testBuildSky(self(ctx), IntegerArgumentType.getInteger(ctx, "t"), mode);
		if (error != null) {
			ctx.getSource().sendFailure(error);
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Msg.prefixed(Component.translatable("kultivator.sectbuild.test.done")), false);
		return 1;
	}

	private static int testRise(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		SiteView sky = EngineApi.get().skySite(player.getUUID());
		if (sky == null || !sky.tethered()) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.blueprint.error.langit_no_sky"));
			return 0;
		}
		ServerLevel level = player.level().getServer().getLevel(sky.dim());
		if (level == null || !Halls.relocateHall(player, level, sky.stele())) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.blueprint.error.langit_not_on_throne"));
			return 0;
		}
		int oldTier = EngineApi.get().tier(player.getUUID());
		boolean pathA = EngineApi.get().template(player.getUUID()) != sky.template();
		Component error = EngineApi.get().testAscend(player, force);
		if (error != null) {
			ctx.getSource().sendFailure(error);
			return 0;
		}
		LangitAngkat.ascended(player, level, sky, sky.stele(), oldTier, pathA ? FootMode.TETAP : null);
		return 1;
	}

	static void clear() {
		DESIGNS.clear();
	}

	private LangitRancang() {
	}
}
