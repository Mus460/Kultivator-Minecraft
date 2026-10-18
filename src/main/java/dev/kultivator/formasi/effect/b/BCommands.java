package dev.kultivator.formasi.effect.b;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.elite.EliteMonsters;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.Cores;
import dev.kultivator.formasi.effect.AlarmKind;
import dev.kultivator.formasi.spec.Family;
import dev.kultivator.formasi.spec.FormationSpec;
import dev.kultivator.formasi.stack.Stacking;
import dev.kultivator.sect.Sect;
import dev.kultivator.sect.Sects;
import dev.kultivator.sect.hall.Halls;
import dev.kultivator.util.Msg;
import dev.kultivator.util.NoGrief;
import dev.kultivator.util.Targeting;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Module B's commands (DESIGN 24.10): {@code formasi simpul [nama <nama>]} and {@code formasi pindah <nama>}, and the
 * test commands {@code uji formasi musuh|bersih|alarm|teleport|simpul|sembunyi|pembunuh|mutlak|resonansi}. No command
 * places or breaks a block; test mobs are only zombies, husks, skeletons and spiders, tagged, griefless, without drops,
 * and vanish after 120 s.
 */
public final class BCommands {

	public static final String TEST_TAG = "kultivator.uji_formasi";
	public static final int TEST_MOB_TICKS = 2400;
	public static final double TEST_DISTANCE = 24.0D;
	public static final int MAX_TEST_MOBS = 16;
	public static final double LOOK_RANGE = 8.0D;
	private static final List<EntityType<? extends Mob>> TEST_TYPES = List.of(EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.SKELETON,
			EntityTypes.SPIDER);

	private record TestMob(ResourceKey<Level> dim, UUID id, long until) {
	}

	private static final List<TestMob> TEST_MOBS = new ArrayList<>();

	static void init() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
			if (entity != null && entity.entityTags().contains(TEST_TAG)) {
				drops.clear();
			}
		});
	}

	/** Every server tick: test mobs vanish after 120 s. */
	static void tick(MinecraftServer server) {
		if (TEST_MOBS.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		Iterator<TestMob> it = TEST_MOBS.iterator();
		while (it.hasNext()) {
			TestMob test = it.next();
			ServerLevel level = server.getLevel(test.dim());
			if (level == null) {
				it.remove();
				continue;
			}
			if (level.getGameTime() >= test.until()) {
				Entity entity = level.getEntity(test.id());
				if (entity != null) {
					entity.discard();
				}
				it.remove();
			}
		}
	}

	static void clearAll() {
		TEST_MOBS.clear();
	}

	// ================================================================== player commands

	static void addCommands(LiteralArgumentBuilder<CommandSourceStack> formasi) {
		formasi.then(Commands.literal("simpul")
				.executes(ctx -> listNodes(ctx.getSource().getPlayerOrException(), false))
				.then(Commands.literal("nama")
						.then(Commands.argument("nama", StringArgumentType.greedyString())
								.executes(ctx -> renameLooked(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nama"))))));
		formasi.then(Commands.literal("pindah")
				.then(Commands.argument("nama", StringArgumentType.greedyString())
						.executes(ctx -> travelByName(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "nama"), false))));
	}

	/** The owner whose nodes {@code player} uses: their own, else their sect master's. */
	private static UUID ownerFor(ServerPlayer player) {
		if (!TransportNetwork.nodes(player.getUUID()).isEmpty()) {
			return player.getUUID();
		}
		Sect sect = Sects.sectOf(player);
		return sect == null ? player.getUUID() : sect.master();
	}

	private static int listNodes(ServerPlayer player, boolean test) {
		UUID owner = ownerFor(player);
		List<CoreView> nodes = TransportNetwork.nodes(owner);
		if (nodes.isEmpty()) {
			Msg.chat(player, Component.translatable("kultivator.formasib.node.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		CoreView here = standingOn(player);
		Msg.chat(player, Component.translatable("kultivator.formasib.node.list", nodes.size()).withStyle(ChatFormatting.GOLD));
		ServerLevel level = player.level();
		int index = 1;
		for (CoreView node : nodes) {
			boolean sameDim = node.dim().equals(level.dimension());
			long distance = sameDim ? Math.round(Math.sqrt(node.eye().distSqr(player.blockPosition()))) : -1L;
			MutableComponent line = Component.translatable("kultivator.formasib.node.line", index, TransportNetwork.name(node),
					BUtil.spec(node) == null ? Component.literal(node.spec()) : BUtil.spec(node).name(), node.state().label(),
					Msg.number(node.dayaCenti() / 100.0D), distance);
			if (test) {
				line.append(Component.translatable("kultivator.formasib.test.node_extra", sameDim && level.isLoaded(node.eye()) ? "✔" : "✘",
						here == null ? "-" : Msg.number(TransportNetwork.cost(here, node))));
			}
			if (here != null && here.id() == node.id()) {
				line.append(Component.translatable("kultivator.formasib.node.here"));
			}
			Msg.chat(player, line);
			index++;
		}
		return nodes.size();
	}

	/** The transport node whose eye is under the player's feet (one or two blocks down), or null. */
	private static @Nullable CoreView standingOn(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		for (int dy = 1; dy <= 2; dy++) {
			CoreView core = Cores.at(level, feet.below(dy));
			if (core != null && BUtil.spec(core) != null && BUtil.spec(core).family() == Family.PERPINDAHAN) {
				return core;
			}
		}
		return null;
	}

	private static @Nullable CoreView looked(ServerPlayer player) {
		HitResult hit = player.pick(LOOK_RANGE, 1.0F, false);
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			return Cores.at(player.level(), block.getBlockPos());
		}
		return null;
	}

	private static int renameLooked(ServerPlayer player, String name) {
		CoreView core = looked(player);
		if (core == null || BUtil.spec(core) == null || BUtil.spec(core).family() != Family.PERPINDAHAN) {
			Msg.chat(player, Component.translatable("kultivator.formasib.node.look").withStyle(ChatFormatting.RED));
			return 0;
		}
		Component why = TransportNetwork.rename(player, core, name);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return 0;
		}
		Msg.chat(player, Component.translatable("kultivator.formasib.node.renamed", name.trim()).withStyle(ChatFormatting.GREEN));
		return 1;
	}

	/** A node of {@code owner} by name, by its number in the list, or by "#id". */
	private static @Nullable CoreView byName(UUID owner, String raw) {
		String wanted = raw.trim().toLowerCase(Locale.ROOT);
		List<CoreView> nodes = TransportNetwork.nodes(owner);
		try {
			if (wanted.startsWith("#")) {
				int id = Integer.parseInt(wanted.substring(1));
				for (CoreView node : nodes) {
					if (node.id() == id) {
						return node;
					}
				}
				return null;
			}
			int number = Integer.parseInt(wanted);
			return number >= 1 && number <= nodes.size() ? nodes.get(number - 1) : null;
		} catch (NumberFormatException ignored) {
			// A name, not a number.
		}
		for (CoreView node : nodes) {
			if (TransportNetwork.name(node).getString().toLowerCase(Locale.ROOT).equals(wanted)
					|| node.ext().getStringOr("name", "").toLowerCase(Locale.ROOT).equals(wanted)) {
				return node;
			}
		}
		return null;
	}

	private static int travelByName(ServerPlayer player, String name, boolean instant) {
		CoreView from = standingOn(player);
		if (from == null && instant) {
			double best = Double.MAX_VALUE;
			for (CoreView node : TransportNetwork.nodes(ownerFor(player))) {
				double d = node.dim().equals(player.level().dimension()) ? node.eye().distSqr(player.blockPosition()) : Double.MAX_VALUE;
				if (d < best && d <= 64.0D * 64.0D) {
					best = d;
					from = node;
				}
			}
		}
		if (from == null) {
			Msg.chat(player, Component.translatable("kultivator.formasib.trip.refuse.stand").withStyle(ChatFormatting.RED));
			return 0;
		}
		CoreView to = byName(from.owner(), name);
		if (to == null) {
			Msg.chat(player, Component.translatable("kultivator.formasib.node.unknown", name).withStyle(ChatFormatting.RED));
			return 0;
		}
		Component why = TransportNetwork.travel(player, from.id(), to.id(), instant);
		if (why != null) {
			Msg.chat(player, why.copy().withStyle(ChatFormatting.RED));
			return 0;
		}
		return 1;
	}

	// ================================================================== test commands

	static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		uji.then(Commands.literal("musuh")
				.then(Commands.argument("n", IntegerArgumentType.integer(1, MAX_TEST_MOBS))
						.executes(ctx -> spawnTest(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n"), false))
						.then(Commands.literal("elit")
								.executes(ctx -> spawnTest(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n"), true)))));
		uji.then(Commands.literal("bersih").executes(ctx -> clean(ctx.getSource().getPlayerOrException())));
		uji.then(Commands.literal("alarm")
				.executes(ctx -> testAlarm(ctx.getSource().getPlayerOrException(), AlarmKind.BREACH))
				.then(Commands.literal("orang").executes(ctx -> testAlarm(ctx.getSource().getPlayerOrException(), AlarmKind.STRANGER)))
				.then(Commands.literal("monster").executes(ctx -> testAlarm(ctx.getSource().getPlayerOrException(), AlarmKind.BREACH)))
				.then(Commands.literal("elit").executes(ctx -> testAlarm(ctx.getSource().getPlayerOrException(), AlarmKind.ELITE))));
		uji.then(Commands.literal("teleport")
				.then(Commands.argument("simpul", StringArgumentType.greedyString())
						.executes(ctx -> travelByName(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "simpul"), true))));
		uji.then(Commands.literal("simpul").executes(ctx -> listNodes(ctx.getSource().getPlayerOrException(), true)));
		uji.then(Commands.literal("sembunyi").executes(ctx -> concealReport(ctx.getSource().getPlayerOrException())));
		uji.then(Commands.literal("pembunuh").executes(ctx -> killReport(ctx.getSource().getPlayerOrException())));
		uji.then(Commands.literal("mutlak")
				.executes(ctx -> pretendAbsolute(ctx.getSource().getPlayerOrException(), -1))
				.then(Commands.argument("n", IntegerArgumentType.integer(0, GrandArrayEffect.A2_FULL))
						.executes(ctx -> pretendAbsolute(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n")))));
		uji.then(Commands.literal("resonansi").executes(ctx -> resonance(ctx.getSource().getPlayerOrException())));
	}

	private static int spawnTest(ServerPlayer player, int n, boolean elite) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		if (flat.lengthSqr() < 1.0E-4D) {
			flat = new Vec3(0.0D, 0.0D, 1.0D);
		}
		flat = flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
		int spawned = 0;
		for (int i = 0; i < n; i++) {
			EntityType<? extends Mob> type = TEST_TYPES.get(i % TEST_TYPES.size());
			Mob mob = type.create(level, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			Vec3 target = player.position().add(flat.scale(TEST_DISTANCE)).add(side.scale((i - (n - 1) / 2.0D) * 2.0D));
			Vec3 spot = Targeting.safeSpot(level, mob, target);
			if (spot == null) {
				mob.discard();
				continue;
			}
			float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - spot.x), player.getZ() - spot.z));
			mob.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(spot)), EntitySpawnReason.EVENT,
					mob instanceof Zombie ? new Zombie.ZombieGroupData(false, false) : null);
			for (Entity rider : new ArrayList<>(mob.getPassengers())) {
				rider.discard();
			}
			mob.addTag(NoGrief.TAG);
			mob.addTag(TEST_TAG);
			if (mob instanceof Zombie zombie) {
				zombie.setCanBreakDoors(false);
			}
			if (!level.addFreshEntity(mob)) {
				continue;
			}
			if (elite && spawned == 0) {
				EliteMonsters.makeElite(mob, 1, null, 0);
			}
			mob.getNavigation().moveTo(player, 1.0D);
			TEST_MOBS.add(new TestMob(level.dimension(), mob.getUUID(), level.getGameTime() + TEST_MOB_TICKS));
			spawned++;
		}
		Msg.chat(player, Component.translatable("kultivator.formasib.test.spawned", spawned, Math.round(TEST_DISTANCE)).withStyle(ChatFormatting.GOLD));
		return spawned;
	}

	private static int clean(ServerPlayer player) {
		int removed = 0;
		ServerLevel level = player.level();
		List<Entity> tagged = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity != null && entity.entityTags().contains(TEST_TAG)) {
				tagged.add(entity);
			}
		}
		for (Entity entity : tagged) {
			entity.discard();
			removed++;
		}
		TEST_MOBS.removeIf(test -> test.dim().equals(level.dimension()));
		Msg.chat(player, Component.translatable("kultivator.formasib.test.cleaned", removed).withStyle(ChatFormatting.GOLD));
		return removed;
	}

	private static int testAlarm(ServerPlayer player, AlarmKind kind) {
		CoreView best = null;
		double bestDistance = Double.MAX_VALUE;
		for (CoreView core : Cores.of(ownerFor(player))) {
			FormationSpec spec = BUtil.spec(core);
			boolean alarm = spec != null && (spec.family() == Family.PERINGATAN || ConcealEffect.M2.equals(spec.id()));
			if (!alarm || !core.dim().equals(player.level().dimension())) {
				continue;
			}
			double d = core.eye().distSqr(player.blockPosition());
			if (d < bestDistance) {
				bestDistance = d;
				best = core;
			}
		}
		if (best == null) {
			Msg.chat(player, Component.translatable("kultivator.formasib.test.no_alarm").withStyle(ChatFormatting.RED));
			return 0;
		}
		AlarmEffect.queueTest(best, kind);
		Msg.chat(player, Component.translatable("kultivator.formasib.test.alarm_queued", BUtil.spec(best).name()).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int concealReport(ServerPlayer player) {
		ServerLevel level = player.level();
		int lines = 0;
		for (ServerPlayer viewer : level.players()) {
			int[] hidden = ConcealEffect.concealedFrom(viewer);
			if (hidden.length == 0) {
				continue;
			}
			MutableComponent names = Component.empty();
			for (int i = 0; i < hidden.length; i++) {
				Entity entity = level.getEntity(hidden[i]);
				if (i > 0) {
					names.append(", ");
				}
				names.append(entity == null ? Component.literal("#" + hidden[i]) : entity.getDisplayName());
			}
			Msg.chat(player, Component.translatable("kultivator.formasib.test.conceal_line", viewer.getDisplayName(), names));
			lines++;
		}
		if (lines == 0) {
			Msg.chat(player, Component.translatable("kultivator.formasib.test.conceal_none").withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	private static int killReport(ServerPlayer player) {
		UUID owner = ownerFor(player);
		int hall = Math.max(1, Halls.level(owner));
		double cap = Stacking.KILL_DPS_BASE + Stacking.KILL_DPS_PER_LEVEL * hall;
		Msg.chat(player, Component.translatable("kultivator.formasib.test.kill_head", Msg.number(cap), hall).withStyle(ChatFormatting.GOLD));
		List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0D), mob -> mob instanceof Enemy && mob.isAlive());
		int shown = 0;
		for (Mob mob : mobs) {
			if (shown++ >= 8) {
				break;
			}
			Msg.chat(player, Component.translatable("kultivator.formasib.test.kill_line", mob.getDisplayName(), Msg.number(mob.getHealth()),
					Msg.number(mob.getMaxHealth()), Msg.number(Stacking.killBudget(mob, owner, hall))));
		}
		return shown;
	}

	private static int pretendAbsolute(ServerPlayer player, int n) {
		UUID owner = ownerFor(player);
		GrandArrayEffect.pretend(owner, n);
		Msg.chat(player, (n < 0 ? Component.translatable("kultivator.formasib.test.absolute_off")
				: Component.translatable("kultivator.formasib.test.absolute", n)).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int resonance(ServerPlayer player) {
		UUID owner = ownerFor(player);
		GrandArrayEffect.Grand grand = GrandArrayEffect.grand(owner);
		GrandArrayEffect.Absolute absolute = GrandArrayEffect.absolute(owner);
		Component yes = Component.translatable("kultivator.formasib.test.yes").withStyle(ChatFormatting.GREEN);
		Component no = Component.translatable("kultivator.formasib.test.no").withStyle(ChatFormatting.RED);
		if (grand == null) {
			Msg.chat(player, Component.translatable("kultivator.formasib.test.a1_none").withStyle(ChatFormatting.GRAY));
		} else {
			Msg.chat(player, Component.translatable("kultivator.formasib.test.a1", grand.complete() ? yes : no, grand.agung() ? yes : no,
					grand.taiji() ? yes : no, grand.liangyi() ? yes : no, grand.sixiang() ? yes : no,
					Msg.number(GrandArrayEffect.A1_BONUS)));
		}
		Integer pretend = GrandArrayEffect.pretended(owner);
		Msg.chat(player, Component.translatable("kultivator.formasib.test.a2", absolute.n(), absolute.built(), absolute.arts(),
				absolute.active() ? yes : no, Msg.number(GrandArrayEffect.A2_PER_N * absolute.n()),
				pretend == null ? "-" : String.valueOf(pretend)));
		return 1;
	}

	private BCommands() {
	}
}
