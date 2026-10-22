package dev.kultivator.duel;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.Kultivator;
import dev.kultivator.data.BreakthroughQuality;
import dev.kultivator.data.Rank;
import dev.kultivator.data.Realm;
import dev.kultivator.disciple.DiscipleCultivation;
import dev.kultivator.disciple.DiscipleData;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.disciple.Disciples;
import dev.kultivator.duel.ai.DuelStyle;
import dev.kultivator.duel.ai.DuelistBrain;
import dev.kultivator.duel.ai.DuelistProfile;
import dev.kultivator.duel.ai.DuelistRegistry;
import dev.kultivator.duel.blade.DuelPose;
import dev.kultivator.duel.clash.ClashClock;
import dev.kultivator.duel.clash.ClashDamage;
import dev.kultivator.duel.clash.Fighter;
import dev.kultivator.duel.clash.Fighters;
import dev.kultivator.duel.clash.Posture;
import dev.kultivator.duel.clash.SwingDir;
import dev.kultivator.duel.clash.WeaponClass;
import dev.kultivator.duel.net.DuelNet;
import dev.kultivator.duel.net.DuelPayloads;
import dev.kultivator.duel.session.DuelChallenges;
import dev.kultivator.duel.session.DuelRecords;
import dev.kultivator.duel.session.DuelRuleSet;
import dev.kultivator.duel.session.DuelSession;
import dev.kultivator.duel.session.DuelSessions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The test commands of the sword duel (DESIGN 35.8.4), op only: {@code /kultivasi uji duel ...}. The visual previews
 * ({@code lambat}, {@code percikan}, {@code intro}, {@code akhir}, {@code bilah}, {@code pose}, {@code bc},
 * {@code gelanggang}, ...) only send a {@code DuelPreview} to the player who typed them; modules V, A and U draw them.
 */
final class DuelTests {

	/** The chips of {@code CheatHelp}'s "duel" group. */
	static final List<String> CHIPS = List.of("duel boneka [gaya] [ranah] [tahap] [senjata]", "duel boneka diam", "duel boneka jaga",
			"duel boneka serang <arah>", "duel boneka lawan", "duel mulai <entitas> [ranah]", "duel pasangan <a> <b>", "duel stop", "duel info",
			"duel jendela", "duel postur <0-100>", "duel ikat", "duel lambat [tick]", "duel percikan [hasil] [warna]", "duel intro",
			"duel akhir", "duel jejak", "duel kunci", "duel kejut", "duel decal", "duel bilah", "duel pose <id>", "duel pose semua", "duel bc",
			"duel gelanggang [radius]", "duel catatan reset [pemain]", "duel muat");

	private static final List<String> STYLES = List.of("agresif", "bertahan", "penipu", "santo");
	private static final List<String> WEAPONS = List.of("pedang", "kapak", "tombak", "gada", "tangan");
	private static final List<String> DIRS = List.of("atas", "atas_kanan", "kanan", "bawah_kanan", "bawah", "bawah_kiri", "kiri", "atas_kiri",
			"tusuk", "putar");
	private static final Set<UUID> BLADE_DEBUG = new HashSet<>();

	static void add(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("duel")
				.then(Commands.literal("boneka")
						.executes(ctx -> dummy(self(ctx), "bertahan", -1, 0, "pedang"))
						.then(Commands.literal("diam").executes(ctx -> dummyMode(self(ctx), DuelistBrain.MODE_STILL, null)))
						.then(Commands.literal("jaga").executes(ctx -> dummyMode(self(ctx), DuelistBrain.MODE_GUARD, null)))
						.then(Commands.literal("lawan").executes(ctx -> dummyMode(self(ctx), DuelistBrain.MODE_FIGHT, null)))
						.then(Commands.literal("serang")
								.then(Commands.argument("arah", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DIRS, builder))
										.executes(ctx -> dummyMode(self(ctx), DuelistBrain.MODE_REPEAT,
												dir(StringArgumentType.getString(ctx, "arah"))))))
						.then(Commands.argument("gaya", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(STYLES, builder))
								.executes(ctx -> dummy(self(ctx), StringArgumentType.getString(ctx, "gaya"), -1, 0, "pedang"))
								.then(Commands.argument("ranah", IntegerArgumentType.integer(0, Realm.values().length - 1))
										.executes(ctx -> dummy(self(ctx), StringArgumentType.getString(ctx, "gaya"),
												IntegerArgumentType.getInteger(ctx, "ranah"), 0, "pedang"))
										.then(Commands.argument("tahap", IntegerArgumentType.integer(0, 3))
												.executes(ctx -> dummy(self(ctx), StringArgumentType.getString(ctx, "gaya"),
														IntegerArgumentType.getInteger(ctx, "ranah"), IntegerArgumentType.getInteger(ctx, "tahap"), "pedang"))
												.then(Commands.argument("senjata", StringArgumentType.word())
														.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(WEAPONS, builder))
														.executes(ctx -> dummy(self(ctx), StringArgumentType.getString(ctx, "gaya"),
																IntegerArgumentType.getInteger(ctx, "ranah"), IntegerArgumentType.getInteger(ctx, "tahap"),
																StringArgumentType.getString(ctx, "senjata"))))))))
				.then(Commands.literal("mulai")
						.then(Commands.argument("target", EntityArgument.entity())
								.executes(ctx -> start(self(ctx), EntityArgument.getEntity(ctx, "target"), null))
								.then(Commands.argument("ranah", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("bebas", "handicap", "tekan"), builder))
										.executes(ctx -> start(self(ctx), EntityArgument.getEntity(ctx, "target"), StringArgumentType.getString(ctx, "ranah"))))))
				.then(Commands.literal("pasangan")
						.then(Commands.argument("a", EntityArgument.entity())
								.then(Commands.argument("b", EntityArgument.entity())
										.executes(ctx -> pair(self(ctx), EntityArgument.getEntity(ctx, "a"), EntityArgument.getEntity(ctx, "b"))))))
				.then(Commands.literal("stop").executes(ctx -> stop(self(ctx))))
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource(), self(ctx))))
				.then(Commands.literal("jendela").executes(ctx -> windows(ctx.getSource(), self(ctx))))
				.then(Commands.literal("postur")
						.then(Commands.argument("nilai", IntegerArgumentType.integer(0, 100))
								.executes(ctx -> posture(self(ctx), IntegerArgumentType.getInteger(ctx, "nilai")))))
				.then(Commands.literal("ikat").executes(ctx -> bind(self(ctx))))
				.then(Commands.literal("lambat")
						.executes(ctx -> preview(self(ctx), DuelIds.PV_SLOWMO, 20, 0.35F, 0))
						.then(Commands.argument("tick", IntegerArgumentType.integer(1, 30))
								.executes(ctx -> preview(self(ctx), DuelIds.PV_SLOWMO, IntegerArgumentType.getInteger(ctx, "tick"), 0.35F, 0))))
				.then(Commands.literal("percikan")
						.executes(ctx -> preview(self(ctx), DuelIds.PV_SPARK, DuelIds.OUT_CLASH, 1.0F, WeaponClass.PEDANG.sparkRgb))
						.then(Commands.argument("hasil", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(outcomeNames(), builder))
								.executes(ctx -> preview(self(ctx), DuelIds.PV_SPARK, outcome(StringArgumentType.getString(ctx, "hasil")), 1.0F,
										WeaponClass.PEDANG.sparkRgb))
								.then(Commands.argument("warna", StringArgumentType.word())
										.executes(ctx -> preview(self(ctx), DuelIds.PV_SPARK, outcome(StringArgumentType.getString(ctx, "hasil")), 1.0F,
												color(StringArgumentType.getString(ctx, "warna")))))))
				.then(Commands.literal("intro").executes(ctx -> preview(self(ctx), DuelIds.PV_INTRO, 0, 1.0F, 0)))
				.then(Commands.literal("akhir").executes(ctx -> preview(self(ctx), DuelIds.PV_FINISHER, 0, 1.0F, 0)))
				.then(Commands.literal("jejak").executes(ctx -> preview(self(ctx), DuelIds.PV_TRAIL, 0, 1.0F, 0)))
				.then(Commands.literal("kunci").executes(ctx -> preview(self(ctx), DuelIds.PV_BIND, 0, 1.0F, 0)))
				.then(Commands.literal("kejut").executes(ctx -> preview(self(ctx), DuelIds.PV_SHOCK, 0, 1.0F, 0)))
				.then(Commands.literal("decal").executes(ctx -> preview(self(ctx), DuelIds.PV_DECAL, 0, 1.0F, 0)))
				.then(Commands.literal("bilah").executes(ctx -> bladeDebug(self(ctx))))
				.then(Commands.literal("pose")
						.then(Commands.literal("semua").executes(ctx -> preview(self(ctx), DuelIds.PV_ALL_POSES, 0, 1.0F, 0)))
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(poseNames(), builder))
								.executes(ctx -> pose(self(ctx), StringArgumentType.getString(ctx, "id")))))
				.then(Commands.literal("bc").executes(ctx -> preview(self(ctx), DuelIds.PV_BC_INFO, 0, 0.0F, 0)))
				.then(Commands.literal("gelanggang")
						.executes(ctx -> preview(self(ctx), DuelIds.PV_RING, DuelConfig.get().ringRadiusDefault, 1.0F, 0))
						.then(Commands.argument("radius", IntegerArgumentType.integer(6, 12))
								.executes(ctx -> preview(self(ctx), DuelIds.PV_RING, IntegerArgumentType.getInteger(ctx, "radius"), 1.0F, 0))))
				.then(Commands.literal("catatan")
						.then(Commands.literal("reset")
								.executes(ctx -> resetRecords(ctx.getSource(), null))
								.then(Commands.argument("pemain", EntityArgument.player())
										.executes(ctx -> resetRecords(ctx.getSource(), EntityArgument.getPlayer(ctx, "pemain"))))))
				.then(Commands.literal("muat").executes(ctx -> reload(ctx.getSource()))));
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static void say(ServerPlayer player, Component message) {
		player.sendSystemMessage(message);
	}

	// ================================================================ the Duel Dummy

	/**
	 * {@code boneka [gaya] [ranah] [tahap] [senjata]}: a RIVAL body ("Boneka Duel · gaya") 6 blocks in front, holding the
	 * training sword (or the weapon asked), and a test duel (PAIR_UJI, short intro) against it. Realm −1 = the player's.
	 */
	private static int dummy(ServerPlayer player, String styleId, int realmIndex, int stage, String weaponId) {
		DuelStyle style = DuelStyle.byId(styleId);
		if (style == null) {
			say(player, Component.translatable("kultivator.duel.cmd.bad_style", styleId).withStyle(ChatFormatting.RED));
			return 0;
		}
		DuelSession existing = DuelSessions.byEntity(player);
		if (existing != null) {
			existing.cancel(Component.translatable("kultivator.duel.reason.test"));
		}
		ServerLevel level = player.level();
		Rank own = Posture.rank(player);
		Realm realm = realmIndex < 0 ? (own != null ? own.realm() : Realm.MORTAL) : Realm.values()[realmIndex];
		int st = Math.max(0, Math.min(realm.stages() - 1, realmIndex < 0 && own != null ? own.stage() : stage));
		DiscipleData data = DiscipleData.fresh(level.getRandom());
		data.setRank(new Rank(realm, 0, st));
		for (Realm r : Realm.values()) {
			if (!r.isMortal() && r.ordinal() <= realm.ordinal()) {
				data.setQuality(r, BreakthroughQuality.NORMAL);
			}
		}
		data.setQi(DiscipleCultivation.maxQi(data));
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		Vec3 at = dev.kultivator.duel.session.DuelRing.ground(level, player.position().add(flat.scale(6.0D)), player.getY());
		Component name = Component.translatable("kultivator.duel.dummy.name", Component.translatable(style.langKey()));
		DiscipleEntity body = Disciples.spawnUnowned(level, at, data, name, level.getRandom().nextInt(8), level.getRandom().nextBoolean(),
				DiscipleEntity.Role.RIVAL, null);
		if (body == null) {
			say(player, Component.translatable("kultivator.duel.cmd.dummy_failed").withStyle(ChatFormatting.RED));
			return 0;
		}
		ItemStack weapon = weaponStack(weaponId);
		body.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
		WeaponClass weaponClass = WeaponClass.of(weapon);
		DuelistRegistry.override(body, new DuelistProfile(style, new Rank(realm, 0, st).step(), weaponClass, style.colorRgb,
				"grunt.male.attack", DuelRecords.DEFAULT_HONOUR, style.surrender, 0, 0, null, false));
		DuelRuleSet rules = DuelRuleSet.defaults().withIntro(false).withRadius(DuelConfig.get().ringRadiusDefault);
		Component why = DuelChallenges.startDirect(player, player, body, DuelIds.PAIR_UJI, rules);
		if (why != null) {
			body.discard();
			say(player, Component.translatable("kultivator.duel.msg.refused", why).withStyle(ChatFormatting.RED));
			return 0;
		}
		DuelSession session = DuelSessions.byEntity(body);
		if (session != null) {
			session.setDummy(body);
		}
		say(player, Component.translatable("kultivator.duel.cmd.dummy", Component.translatable(style.langKey()), realm.displayName(), st + 1,
				weaponId).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	/** The test weapon (created only when asked; the training sword of module A when it exists). */
	private static ItemStack weaponStack(String id) {
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "kapak", "axe" -> new ItemStack(Items.IRON_AXE);
			case "tombak", "spear" -> new ItemStack(Items.TRIDENT);
			case "gada", "mace" -> new ItemStack(Items.MACE);
			case "tangan", "hand" -> ItemStack.EMPTY;
			default -> {
				Item training = BuiltInRegistries.ITEM.getValue(Kultivator.id("pedang_latih"));
				yield training != null && training != Items.AIR ? new ItemStack(training) : new ItemStack(Items.IRON_SWORD);
			}
		};
	}

	private static int dummyMode(ServerPlayer player, int mode, @Nullable SwingDir dir) {
		DuelSession session = DuelSessions.byEntity(player);
		LivingEntity other = session != null ? session.partnerOf(player) : null;
		Fighter fighter = other != null ? Fighters.peek(other) : null;
		if (fighter == null || fighter.brain() == null) {
			say(player, Component.translatable("kultivator.duel.cmd.no_dummy").withStyle(ChatFormatting.RED));
			return 0;
		}
		fighter.brain().setMode(mode, dir);
		say(player, Component.translatable("kultivator.duel.cmd.dummy_mode." + mode, dir == null ? "" : dir.name().toLowerCase(Locale.ROOT))
				.withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static @Nullable SwingDir dir(String id) {
		try {
			return SwingDir.valueOf(id.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return SwingDir.KANAN;
		}
	}

	// ================================================================ direct starts

	private static int start(ServerPlayer player, Entity target, @Nullable String realm) {
		if (!(target instanceof LivingEntity living) || living == player) {
			say(player, Component.translatable("kultivator.duel.reason.no_target").withStyle(ChatFormatting.RED));
			return 0;
		}
		int pair = DuelChallenges.pairOf(player, living, null);
		if (pair < 0) {
			pair = DuelIds.PAIR_NPC;
		}
		DuelRuleSet rules = DuelRuleSet.suggested(Posture.realm(player) - Posture.realm(living)).withRadius(DuelConfig.get().ringRadiusDefault);
		DuelRuleSet.RealmRule rule = DuelCommands.realmRule(realm);
		if (rule != null) {
			rules = rules.withRealm(rule);
		}
		Component why = DuelChallenges.startDirect(player, player, living, pair, rules);
		if (why != null) {
			say(player, Component.translatable("kultivator.duel.msg.refused", why).withStyle(ChatFormatting.RED));
			return 0;
		}
		return 1;
	}

	private static int pair(ServerPlayer player, Entity a, Entity b) {
		if (!(a instanceof LivingEntity la) || !(b instanceof LivingEntity lb) || la == lb) {
			say(player, Component.translatable("kultivator.duel.reason.no_target").withStyle(ChatFormatting.RED));
			return 0;
		}
		DuelRuleSet rules = DuelRuleSet.suggested(Posture.realm(la) - Posture.realm(lb)).withRadius(DuelConfig.get().ringRadiusDefault)
				.withCrowd(true);
		Component why = DuelChallenges.startDirect(player, la, lb, DuelIds.PAIR_TONTON, rules);
		if (why != null) {
			say(player, Component.translatable("kultivator.duel.msg.refused", why).withStyle(ChatFormatting.RED));
			return 0;
		}
		return 1;
	}

	private static int stop(ServerPlayer player) {
		int count = DuelSessions.cancelAll(player, Component.translatable("kultivator.duel.reason.test"));
		say(player, Component.translatable("kultivator.duel.cmd.stopped", count).withStyle(ChatFormatting.YELLOW));
		return count;
	}

	// ================================================================ diagnostics

	private static int info(CommandSourceStack source, ServerPlayer player) {
		long now = ClashClock.now();
		DuelSession session = DuelSessions.byEntity(player);
		if (session == null) {
			session = DuelSessions.watchedBy(player);
		}
		Fighter own = Fighters.get(player);
		source.sendSuccess(() -> Component.translatable("kultivator.duel.cmd.info_head", DuelSessions.count(), DuelChallenges.openCount(),
				Fighters.all().size()).withStyle(ChatFormatting.GOLD), false);
		if (session != null) {
			DuelSession s = session;
			source.sendSuccess(() -> Component.literal("#" + s.id() + " " + s.phase() + " pair=" + s.pair() + " "
					+ s.rules().realm() + "/" + s.rules().jurus() + "/" + s.rules().win() + " r=" + s.rules().radius()
					+ " t=" + s.phaseTicks(now) + (s.sectArena() ? " arena" : "")).withStyle(ChatFormatting.YELLOW), false);
			for (LivingEntity side : new LivingEntity[] {s.a(), s.b()}) {
				Fighter f = Fighters.peek(side);
				if (f != null) {
					source.sendSuccess(() -> Component.literal(side.getName().getString() + ": " + line(f, now)).withStyle(ChatFormatting.GRAY), false);
					if (f.brain() != null) {
						source.sendSuccess(() -> Component.literal("  AI " + f.brain().summary() + " presses=" + f.brain().statParries)
								.withStyle(ChatFormatting.DARK_AQUA), false);
					}
				}
			}
		} else {
			source.sendSuccess(() -> Component.literal(player.getName().getString() + ": " + line(own, now)).withStyle(ChatFormatting.GRAY), false);
		}
		source.sendSuccess(() -> Component.literal("latency=" + own.latency() + "ms comp=" + own.comp() + " input=" + own.inputMode()
				+ " predicted " + own.statPredictedRight + "/" + own.statPredicted).withStyle(ChatFormatting.GRAY), false);
		for (long[] result : Duel.recent()) {
			source.sendSuccess(() -> Component.literal("  t" + result[0] + " " + ClashDamage.outcomeName((int) result[1]) + " " + result[2] + "→"
					+ result[3]).withStyle(ChatFormatting.DARK_GRAY), false);
		}
		return 1;
	}

	private static String line(Fighter f, long now) {
		return String.format(Locale.ROOT, "posture %.1f/%.1f state=%d chain=%d weapon=%s ctx=%d hits=%d perfect=%d deflect=%d binds=%d",
				f.posture(), f.postureMax(), f.state(now), f.chain(), f.weapon(), f.context(), f.statHits, f.statPerfects, f.statDeflects,
				f.statBindsWon);
	}

	/** {@code jendela}: the effective windows (with HANDICAP), the latency and the last ten deflect timings. */
	private static int windows(CommandSourceStack source, ServerPlayer player) {
		Fighter fighter = Fighters.get(player);
		DuelRuleSet rules = Fighters.rules(fighter);
		DuelSession session = DuelSessions.byEntity(player);
		LivingEntity partner = session != null ? session.partnerOf(player) : null;
		int window = Math.max(3, 5 + fighter.weapon().parryWindowDelta + ClashDamage.handicapWindow(player, partner, rules));
		source.sendSuccess(() -> Component.translatable("kultivator.duel.cmd.windows", window, 1, fighter.latency(), fighter.comp(),
				fighter.spamLocked(ClashClock.now()) ? "ya" : "-").withStyle(ChatFormatting.GOLD), false);
		for (Fighter.WindowLog entry : fighter.windowLog()) {
			source.sendSuccess(() -> Component.literal("  t" + entry.tick() + " impact−tEff=" + entry.delta() + " /" + entry.window() + " → "
					+ ClashDamage.outcomeName(entry.outcome()) + " (" + entry.latency() + "ms, comp " + entry.comp() + ")")
					.withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int posture(ServerPlayer player, int percent) {
		Fighters.setPosture(player, percent / 100.0F);
		say(player, Component.translatable("kultivator.duel.cmd.posture", percent).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int bind(ServerPlayer player) {
		LivingEntity nearest = null;
		double best = 16.0D;
		for (LivingEntity other : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4.0D),
				e -> e != player && e.isAlive())) {
			double distance = other.distanceToSqr(player);
			if (distance < best) {
				best = distance;
				nearest = other;
			}
		}
		if (nearest == null || !Fighters.forceBind(player, nearest)) {
			say(player, Component.translatable("kultivator.duel.cmd.no_bind").withStyle(ChatFormatting.RED));
			return 0;
		}
		return 1;
	}

	// ================================================================ previews

	private static int preview(ServerPlayer player, int kind, int arg, float f, int rgb) {
		DuelNet.send(player, new DuelPayloads.DuelPreview(kind, arg, f, rgb));
		return 1;
	}

	private static int bladeDebug(ServerPlayer player) {
		boolean on = BLADE_DEBUG.add(player.getUUID());
		if (!on) {
			BLADE_DEBUG.remove(player.getUUID());
		}
		say(player, Component.translatable(on ? "kultivator.duel.cmd.blade_on" : "kultivator.duel.cmd.blade_off").withStyle(ChatFormatting.GREEN));
		return preview(player, DuelIds.PV_BLADE_DEBUG, on ? 1 : 0, 0.0F, 0);
	}

	private static int pose(ServerPlayer player, String id) {
		DuelPose pose;
		try {
			pose = DuelPose.valueOf(id.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			say(player, Component.translatable("kultivator.duel.cmd.bad_pose", id).withStyle(ChatFormatting.RED));
			return 0;
		}
		return preview(player, DuelIds.PV_POSE, pose.ordinal(), player.getId(), 0);
	}

	private static List<String> poseNames() {
		List<String> names = new ArrayList<>();
		for (DuelPose pose : DuelPose.values()) {
			names.add(pose.name().toLowerCase(Locale.ROOT));
		}
		return names;
	}

	private static List<String> outcomeNames() {
		List<String> names = new ArrayList<>();
		for (int i = 0; i < DuelIds.OUTCOME_COUNT; i++) {
			names.add(ClashDamage.outcomeName(i).toLowerCase(Locale.ROOT));
		}
		return names;
	}

	private static int outcome(String name) {
		for (int i = 0; i < DuelIds.OUTCOME_COUNT; i++) {
			if (ClashDamage.outcomeName(i).equalsIgnoreCase(name)) {
				return i;
			}
		}
		return DuelIds.OUT_CLASH;
	}

	/** A colour by name (emas, merah, biru, hijau, ungu, putih) or hex (FFAA00). */
	private static int color(String id) {
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "emas", "gold" -> 0xFFD060;
			case "merah", "red" -> 0xFF5040;
			case "biru", "blue" -> 0x60A0FF;
			case "hijau", "green" -> 0x70E080;
			case "ungu", "purple" -> 0xB070F0;
			case "putih", "white" -> 0xFFFFFF;
			default -> {
				try {
					yield Integer.parseInt(id.startsWith("#") ? id.substring(1) : id, 16) & 0xFFFFFF;
				} catch (NumberFormatException exception) {
					yield WeaponClass.PEDANG.sparkRgb;
				}
			}
		};
	}

	// ================================================================ records and config

	private static int resetRecords(CommandSourceStack source, @Nullable ServerPlayer who) {
		DuelRecords.get(source.getServer()).reset(who == null ? null : who.getUUID());
		source.sendSuccess(() -> Component.translatable("kultivator.duel.cmd.records_reset", who == null ? Component.literal("*")
				: who.getDisplayName()).withStyle(ChatFormatting.YELLOW), true);
		return 1;
	}

	private static int reload(CommandSourceStack source) {
		boolean ok = DuelConfig.reload();
		source.sendSuccess(() -> Component.translatable(ok ? "kultivator.duel.cmd.reloaded" : "kultivator.duel.cmd.reload_failed",
				DuelConfig.file().toString()).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), true);
		return ok ? 1 : 0;
	}

	private DuelTests() {
	}
}
