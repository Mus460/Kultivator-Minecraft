package dev.kultivator.pusakalangit;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.kehancuran.Kehancuran;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /kultivasi uji pusaka_langit ...} (op, DESIGN 29.11): {@code beri}, {@code bypass nyala|mati},
 * {@code <pedang|tiang|bintang|gunung> [kekuatan] [kecepatan] [kerusakan]} (a test cast at the aim: {@code CastOptions.TEST},
 * no mastery, no reward; it carves only with {@code uji teknik kehancuran nyala} and the own switch on), {@code semua}
 * (all four, 20 s apart, each 48 blocks further along the look), {@code monumen <pedang|tiang> [skala]},
 * {@code monumen daftar|hapus_semua|umur <hari>}, {@code cabut}, {@code beban} and {@code reset}.
 */
public final class TitahTests {

	private static final String KEY = "kultivator.pusaka_langit.test.";
	/** {@code semua}: one decree every 20 s. */
	private static final int TOUR_GAP = 400;
	private static final double TOUR_STEP = 48.0D;

	/** One queued decree of {@code semua}. */
	private record Planned(UUID player, Titah.Skill skill, long due, Vec3 at) {
	}

	private static final List<Planned> TOUR = new ArrayList<>();

	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pusaka_langit");
		root.then(Commands.literal("beri").executes(ctx -> give(self(ctx))));
		root.then(Commands.literal("bypass")
				.then(Commands.literal("nyala").executes(ctx -> bypass(self(ctx), true)))
				.then(Commands.literal("mati").executes(ctx -> bypass(self(ctx), false))));
		for (Titah.Skill skill : Titah.Skill.values()) {
			root.then(Commands.literal(skill.key())
					.executes(ctx -> cast(self(ctx), skill, 1.0F, 1.0F, 1.0F))
					.then(Commands.argument("kekuatan", FloatArgumentType.floatArg(0.1F, 10.0F))
							.executes(ctx -> cast(self(ctx), skill, FloatArgumentType.getFloat(ctx, "kekuatan"), 1.0F, 1.0F))
							.then(Commands.argument("kecepatan", FloatArgumentType.floatArg(0.3F, 3.0F))
									.executes(ctx -> cast(self(ctx), skill, FloatArgumentType.getFloat(ctx, "kekuatan"),
											FloatArgumentType.getFloat(ctx, "kecepatan"), 1.0F))
									.then(Commands.argument("kerusakan", FloatArgumentType.floatArg(0.0F, 100.0F))
											.executes(ctx -> cast(self(ctx), skill, FloatArgumentType.getFloat(ctx, "kekuatan"),
													FloatArgumentType.getFloat(ctx, "kecepatan"), FloatArgumentType.getFloat(ctx, "kerusakan") / 100.0F))))));
		}
		root.then(Commands.literal("semua").executes(ctx -> tour(self(ctx))));
		LiteralArgumentBuilder<CommandSourceStack> monument = Commands.literal("monumen");
		monument.then(Commands.literal("pedang")
				.executes(ctx -> plant(self(ctx), TitahMonumentEntity.MODEL_SWORD, 12.0F))
				.then(Commands.argument("skala", FloatArgumentType.floatArg(1.0F, 32.0F))
						.executes(ctx -> plant(self(ctx), TitahMonumentEntity.MODEL_SWORD, FloatArgumentType.getFloat(ctx, "skala")))));
		monument.then(Commands.literal("tiang")
				.executes(ctx -> plant(self(ctx), TitahMonumentEntity.MODEL_PILLAR, 10.0F))
				.then(Commands.argument("skala", FloatArgumentType.floatArg(1.0F, 32.0F))
						.executes(ctx -> plant(self(ctx), TitahMonumentEntity.MODEL_PILLAR, FloatArgumentType.getFloat(ctx, "skala")))));
		monument.then(Commands.literal("daftar").executes(ctx -> listAll(ctx.getSource())));
		monument.then(Commands.literal("hapus_semua").executes(ctx -> removeAll(ctx.getSource())));
		monument.then(Commands.literal("umur")
				.then(Commands.argument("hari", IntegerArgumentType.integer(1, 30))
						.executes(ctx -> age(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "hari")))));
		root.then(monument);
		root.then(Commands.literal("cabut").executes(ctx -> pull(self(ctx))));
		root.then(Commands.literal("beban").executes(ctx -> load(ctx.getSource())));
		root.then(Commands.literal("reset").executes(ctx -> reset(self(ctx))));
		uji.then(root);
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int give(ServerPlayer player) {
		TitahItems.give(player, TitahItems.boundSeal(player));
		Msg.chat(player, Component.translatable(KEY + "given").withStyle(ChatFormatting.GOLD));
		return 1;
	}

	private static int bypass(ServerPlayer player, boolean on) {
		Titah.setBypass(player, on);
		Msg.chat(player, Component.translatable(on ? KEY + "bypass.on" : KEY + "bypass.off").withStyle(ChatFormatting.YELLOW));
		TitahSettings.sync(player);
		return 1;
	}

	private static int cast(ServerPlayer player, Titah.Skill skill, float kekuatan, float kecepatan, float kerusakan) {
		Titah.Settings stored = TitahSettings.raw(dev.kultivator.data.Cultivators.get(player), skill);
		Titah.Settings settings = new Titah.Settings(kekuatan, kerusakan, kecepatan, stored.hancur(), stored.monumen());
		boolean done = Titah.castWith(player, skill, true, settings, null);
		if (!done) {
			Msg.chat(player, Component.translatable(KEY + "refused", skill.displayName()).withStyle(ChatFormatting.RED));
			return 0;
		}
		Titah.Settings used = TitahSettings.sanitize(settings, 10.0F);
		TitahSettings.Readout readout = TitahSettings.readout(skill, used, dev.kultivator.KultivatorConfig.get());
		Msg.chat(player, Component.translatable(KEY + "cast", skill.displayName(), readout.line(), Kehancuran.selfOn(player)
				? Component.translatable(KEY + "real") : Component.translatable(KEY + "visual")).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** {@code semua}: Pedang, Tiang, Bintang, Gunung, 20 s apart, at 48, 96, 144 and 192 blocks along the flat look. */
	private static int tour(ServerPlayer player) {
		TOUR.removeIf(planned -> planned.player().equals(player.getUUID()));
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0D, look.z);
		flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
		long now = player.level().getGameTime();
		for (Titah.Skill skill : Titah.Skill.values()) {
			int i = skill.ordinal();
			Vec3 at = player.position().add(flat.scale(TOUR_STEP * (i + 1)));
			TOUR.add(new Planned(player.getUUID(), skill, now + (long) TOUR_GAP * i, at));
		}
		Msg.chat(player, Component.translatable(KEY + "tour", TOUR_GAP / 20).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** Runs the due decrees of {@code semua}. */
	static void tick(MinecraftServer server) {
		if (TOUR.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Planned planned : List.copyOf(TOUR)) {
			if (now < planned.due()) {
				continue;
			}
			TOUR.remove(planned);
			ServerPlayer player = server.getPlayerList().getPlayer(planned.player());
			if (player == null) {
				continue;
			}
			Vec3 at = ground(player.level(), planned.at());
			if (at == null) {
				Msg.chat(player, Component.translatable(KEY + "unloaded", planned.skill().displayName()).withStyle(ChatFormatting.GRAY));
				continue;
			}
			Titah.Settings stored = TitahSettings.raw(dev.kultivator.data.Cultivators.get(player), planned.skill());
			Titah.castWith(player, planned.skill(), true, new Titah.Settings(1.0F, stored.kerusakan(), 1.0F, stored.hancur(), stored.monumen()), at);
		}
	}

	private static @Nullable Vec3 ground(ServerLevel level, Vec3 at) {
		net.minecraft.core.BlockPos column = net.minecraft.core.BlockPos.containing(at);
		if (!level.isLoaded(column)) {
			return null;
		}
		return new Vec3(at.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), at.z);
	}

	private static int plant(ServerPlayer player, byte model, float scale) {
		Vec3 at = Titah.aim(player);
		if (at == null) {
			Msg.chat(player, Component.translatable("kultivator.pusaka_langit.refuse.aim", dev.kultivator.KultivatorConfig.get().titahRange)
					.withStyle(ChatFormatting.RED));
			return 0;
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - at.x), player.getZ() - at.z));
		int id = TitahMonuments.create(player.level(), player, model, at, yaw, scale, model == TitahMonumentEntity.MODEL_SWORD ? 10.0F : 0.0F, true,
				false);
		Msg.chat(player, Component.translatable(id >= 0 ? KEY + "planted" : KEY + "plant_failed", id).withStyle(ChatFormatting.GOLD));
		return id >= 0 ? 1 : 0;
	}

	private static int listAll(CommandSourceStack source) {
		List<TitahMonuments.View> all = TitahMonuments.list(source.getServer(), null);
		source.sendSuccess(() -> Component.translatable(KEY + "list", all.size()).withStyle(ChatFormatting.GOLD), false);
		for (TitahMonuments.View view : all) {
			Component line = Component.literal(view.ownerName() + " ").append(PusakaLangit.line(view));
			source.sendSuccess(() -> line, false);
		}
		return all.size();
	}

	private static int removeAll(CommandSourceStack source) {
		int n = TitahMonuments.removeAll(source.getServer());
		source.sendSuccess(() -> Component.translatable(KEY + "removed", n).withStyle(ChatFormatting.YELLOW), true);
		return n;
	}

	private static int age(CommandSourceStack source, int days) {
		int n = TitahMonuments.age(source.getServer(), days);
		source.sendSuccess(() -> Component.translatable(KEY + "aged", n, days).withStyle(ChatFormatting.YELLOW), true);
		return n;
	}

	/** {@code cabut}: finishes the nearest own pull at once (or pulls the nearest own weapon within 16 blocks). */
	private static int pull(ServerPlayer player) {
		ServerLevel level = player.level();
		List<TitahMonumentEntity> near = level.getEntitiesOfClass(TitahMonumentEntity.class, new AABB(player.blockPosition()).inflate(16.0D),
				entity -> !entity.isRemoved() && player.getUUID().equals(entity.owner()));
		TitahMonumentEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (TitahMonumentEntity entity : near) {
			double distance = entity.distanceToSqr(player) - (entity.pulling() ? 1.0E6D : 0.0D);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = entity;
			}
		}
		if (best == null) {
			Msg.chat(player, Component.translatable(KEY + "no_monument").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		best.finishPull(player);
		return 1;
	}

	private static int load(CommandSourceStack source) {
		int[] jobs = Kehancuran.jobCounts();
		int[] monuments = TitahMonuments.counts(source.getServer());
		source.sendSuccess(() -> Component.translatable(KEY + "load", jobs[0], jobs[1], jobs[2], monuments[0], monuments[1], monuments[2])
				.withStyle(ChatFormatting.GOLD), false);
		java.util.Map<String, Integer> perPlayer = new java.util.TreeMap<>();
		for (TitahMonuments.View view : TitahMonuments.list(source.getServer(), null)) {
			perPlayer.merge(view.ownerName(), 1, Integer::sum);
		}
		perPlayer.forEach((name, count) -> source.sendSuccess(() -> Component.literal("  " + name + ": " + count).withStyle(ChatFormatting.GRAY), false));
		return monuments[0];
	}

	private static int reset(ServerPlayer player) {
		TitahSettings.reset(player);
		HeavenDecreeSealItem.refreshView(player);
		TitahSettings.sync(player);
		Msg.chat(player, Component.translatable(KEY + "reset").withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	static void clearAll() {
		TOUR.clear();
	}

	private TitahTests() {
	}
}
