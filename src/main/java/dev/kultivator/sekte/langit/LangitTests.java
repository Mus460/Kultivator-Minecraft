package dev.kultivator.sekte.langit;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kultivator.disciple.DiscipleEntity;
import dev.kultivator.formasi.core.CoreView;
import dev.kultivator.formasi.core.Cores;
import dev.kultivator.sect.build.api.AuditReport;
import dev.kultivator.sect.build.api.EngineApi;
import dev.kultivator.sect.build.blueprint.Glaze;
import dev.kultivator.sekte.langit.api.LangitApi;
import dev.kultivator.sekte.langit.api.LangitConfig;
import dev.kultivator.sekte.langit.api.SkyHallView;
import dev.kultivator.sekte.langit.main.LangitTestHooks;
import dev.kultivator.sekte.langit.npc.LangitNpcPlan;
import dev.kultivator.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /kultivasi uji langit_sekte ...} (DESIGN 37.7.9, module 37G; op level 2): every test of Sekte Langit in one node,
 * registered through {@code DuniaCommands.test("langit_sekte", LangitTests::node)}. The build, ascent and showcase tests
 * call the build module's {@code EngineApi} (37B), the fall and ferry tests the movement module (37M) through
 * {@code LangitApi}, the events and NPC sky sects this module. The chip list is {@link #CHIPS} (the {@code sekte_langit}
 * group of {@code command.CheatHelp}).
 */
public final class LangitTests {

	public static final List<String> CHIPS = List.of("langit_sekte info", "langit_sekte pratinjau <1-9> [tinggi <y>] [putar <0-3>]",
			"langit_sekte pamer <1-9> tingkat <0-9> [tinggi <y>]", "langit_sekte bangun <0-9> [instan|cepat]", "langit_sekte naik [paksa]",
			"langit_sekte tp <tahta|kaki|puncak|feri>", "langit_sekte tp dermaga <i>", "langit_sekte jatuh [tinggi] [terjun]",
			"langit_sekte jatuh murid", "langit_sekte feri", "langit_sekte formasi", "langit_sekte patroli", "langit_sekte tangga", "langit_sekte bajak [1-3]", "langit_sekte longbo",
			"langit_sekte utusan", "langit_sekte tamu", "langit_sekte sengsara", "langit_sekte halo", "langit_sekte pesta",
			"langit_sekte npc daftar", "langit_sekte npc cari", "langit_sekte npc tp <i>", "langit_sekte audit [1-9]", "langit_sekte reset");

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("langit_sekte").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		root.then(Commands.literal("info").executes(ctx -> info(self(ctx))));
		root.then(Commands.literal("pratinjau").then(Commands.argument("n", IntegerArgumentType.integer(1, 9))
				.executes(ctx -> preview(ctx, Integer.MIN_VALUE, 0))
				.then(Commands.literal("tinggi").then(Commands.argument("y", IntegerArgumentType.integer(-64, 400))
						.executes(ctx -> preview(ctx, IntegerArgumentType.getInteger(ctx, "y"), 0))
						.then(Commands.literal("putar").then(Commands.argument("rot", IntegerArgumentType.integer(0, 3))
								.executes(ctx -> preview(ctx, IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "rot")))))))
				.then(Commands.literal("putar").then(Commands.argument("rot", IntegerArgumentType.integer(0, 3))
						.executes(ctx -> preview(ctx, Integer.MIN_VALUE, IntegerArgumentType.getInteger(ctx, "rot")))))));
		root.then(Commands.literal("pamer").then(Commands.argument("n", IntegerArgumentType.integer(1, 9))
				.then(Commands.literal("tingkat").then(Commands.argument("t", IntegerArgumentType.integer(0, 9))
						.executes(ctx -> showcase(ctx, Integer.MIN_VALUE))
						.then(Commands.literal("tinggi").then(Commands.argument("y", IntegerArgumentType.integer(-64, 400))
								.executes(ctx -> showcase(ctx, IntegerArgumentType.getInteger(ctx, "y")))))))));
		root.then(Commands.literal("bangun").then(Commands.argument("t", IntegerArgumentType.integer(0, 9))
				.executes(ctx -> build(ctx, 0))
				.then(Commands.literal("instan").executes(ctx -> build(ctx, 1)))
				.then(Commands.literal("cepat").executes(ctx -> build(ctx, 2)))));
		root.then(Commands.literal("naik").executes(ctx -> ascend(self(ctx), false))
				.then(Commands.literal("paksa").executes(ctx -> ascend(self(ctx), true))));
		LiteralArgumentBuilder<CommandSourceStack> tp = Commands.literal("tp");
		for (String where : new String[]{"tahta", "kaki", "puncak", "feri"}) {
			tp.then(Commands.literal(where).executes(ctx -> teleport(self(ctx), where, 0)));
		}
		tp.then(Commands.literal("dermaga").then(Commands.argument("i", IntegerArgumentType.integer(0, 8))
				.executes(ctx -> teleport(self(ctx), "dermaga", IntegerArgumentType.getInteger(ctx, "i")))));
		root.then(tp);
		root.then(Commands.literal("jatuh").executes(ctx -> fall(self(ctx), 4, false))
				.then(Commands.literal("murid").executes(ctx -> fallDisciple(self(ctx))))
				.then(Commands.literal("terjun").executes(ctx -> fall(self(ctx), 4, true)))
				.then(Commands.argument("tinggi", IntegerArgumentType.integer(1, 120))
						.executes(ctx -> fall(self(ctx), IntegerArgumentType.getInteger(ctx, "tinggi"), false))
						.then(Commands.literal("terjun").executes(ctx -> fall(self(ctx), IntegerArgumentType.getInteger(ctx, "tinggi"), true)))));
		root.then(Commands.literal("feri").executes(ctx -> report(self(ctx), LangitTestHooks.ferry(self(ctx)))));
		root.then(Commands.literal("formasi").executes(ctx -> formasi(self(ctx))));
		// 37M movement tests (GerakUji): a sword patrol now, the First Climb of the nearest disciple.
		root.then(Commands.literal("patroli").executes(ctx -> say(self(ctx), dev.kultivator.sekte.langit.gerak.GerakUji.patroli(self(ctx)))));
		root.then(Commands.literal("tangga").executes(ctx -> say(self(ctx), dev.kultivator.sekte.langit.gerak.GerakUji.tangga(self(ctx)))));
		root.then(Commands.literal("bajak").executes(ctx -> report(self(ctx), LangitTestHooks.bajak(self(ctx), 0, false)))
				.then(Commands.argument("n", IntegerArgumentType.integer(1, 3))
						.executes(ctx -> report(self(ctx), LangitTestHooks.bajak(self(ctx), IntegerArgumentType.getInteger(ctx, "n"), false)))));
		root.then(Commands.literal("longbo").executes(ctx -> report(self(ctx), LangitTestHooks.bajak(self(ctx), 1, true))));
		root.then(Commands.literal("utusan").executes(ctx -> report(self(ctx), LangitTestHooks.utusan(self(ctx)))));
		root.then(Commands.literal("tamu").executes(ctx -> report(self(ctx), LangitTestHooks.tamu(self(ctx)))));
		root.then(Commands.literal("sengsara").executes(ctx -> report(self(ctx), LangitTestHooks.sengsara(self(ctx)))));
		root.then(Commands.literal("halo").executes(ctx -> report(self(ctx), LangitTestHooks.halo(self(ctx)))));
		root.then(Commands.literal("pesta").executes(ctx -> report(self(ctx), LangitTestHooks.pesta(self(ctx)))));
		root.then(Commands.literal("npc")
				.then(Commands.literal("daftar").executes(ctx -> npcList(self(ctx))))
				.then(Commands.literal("cari").executes(ctx -> npcNearest(self(ctx))))
				.then(Commands.literal("tp").then(Commands.argument("i", IntegerArgumentType.integer(0, LangitNpcPlan.MAX - 1))
						.executes(ctx -> npcTeleport(self(ctx), IntegerArgumentType.getInteger(ctx, "i"))))));
		root.then(Commands.literal("audit").executes(ctx -> audit(self(ctx), null))
				.then(Commands.argument("n", IntegerArgumentType.integer(1, 9)).executes(ctx -> audit(self(ctx), 10 + IntegerArgumentType.getInteger(ctx, "n")))));
		root.then(Commands.literal("reset").executes(ctx -> reset(self(ctx))));
		return root;
	}

	private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int say(ServerPlayer player, Component line) {
		Msg.chat(player, Msg.prefixed(line.copy().withStyle(ChatFormatting.AQUA)));
		return 1;
	}

	private static int report(ServerPlayer player, @Nullable Component refused) {
		if (refused != null) {
			Msg.chat(player, Msg.prefixed(refused.copy().withStyle(ChatFormatting.YELLOW)));
			return 0;
		}
		Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.ok").withStyle(ChatFormatting.GREEN)));
		return 1;
	}

	// ================================================================== build (37B)

	private static int preview(CommandContext<CommandSourceStack> ctx, int y, int rot) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		int yP = y != Integer.MIN_VALUE ? y : LangitConfig.get().tinggiAsal;
		long ticket = EngineApi.get().previewSky(player, 10 + n, rot, Glaze.TINTA, yP, 0, 0, true);
		Msg.chat(player, Msg.prefixed(Component.translatable(ticket != 0L ? "kultivator.sekte_langit.uji.pratinjau" : "kultivator.sekte_langit.uji.ditolak",
				n, yP)).withStyle(ticket != 0L ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
		return ticket != 0L ? 1 : 0;
	}

	private static int showcase(CommandContext<CommandSourceStack> ctx, int y) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		int n = IntegerArgumentType.getInteger(ctx, "n");
		int tier = IntegerArgumentType.getInteger(ctx, "t");
		int yP = y != Integer.MIN_VALUE ? y : LangitConfig.get().tinggiAsal;
		BlockPos origin = new BlockPos(player.getBlockX(), yP, player.getBlockZ());
		// Through Founding so "/kultivasi uji sekte pamer konfirmasi" finds the pending showcase.
		boolean opened = dev.kultivator.sect.build.flow.Founding.previewAt(player, 10 + n, tier, 0, origin, dev.kultivator.sect.build.flow.Founding.Mode.SHOWCASE);
		Msg.chat(player, Msg.prefixed(Component.translatable(opened ? "kultivator.sekte_langit.uji.pamer" : "kultivator.sekte_langit.uji.ditolak",
				n, yP)).withStyle(opened ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
		return opened ? 1 : 0;
	}

	private static int build(CommandContext<CommandSourceStack> ctx, int mode) throws CommandSyntaxException {
		ServerPlayer player = self(ctx);
		return report(player, EngineApi.get().testBuildSky(player, IntegerArgumentType.getInteger(ctx, "t"), mode));
	}

	private static int ascend(ServerPlayer player, boolean force) {
		return report(player, EngineApi.get().testAscend(player, force));
	}

	private static int audit(ServerPlayer player, @Nullable Integer template) {
		AuditReport report = EngineApi.get().audit(template);
		Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.audit", template == null ? "11-19" : String.valueOf(template),
				report.emissions(), report.violations())).withStyle(report.ok() ? ChatFormatting.GREEN : ChatFormatting.RED));
		int shown = 0;
		for (Component line : report.lines()) {
			if (shown++ >= 12) {
				break;
			}
			player.sendSystemMessage(line.copy().withStyle(ChatFormatting.GRAY));
		}
		return report.ok() ? 1 : 0;
	}

	private static int reset(ServerPlayer player) {
		int removed = EngineApi.get().showcaseRemove(player, true);
		Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.reset", removed)).withStyle(ChatFormatting.GRAY));
		return removed;
	}

	// ================================================================== info and teleports

	private static int info(ServerPlayer player) {
		SkyHallView view = LangitApi.get().skyHall(player.getUUID());
		if (view == null) {
			view = LangitApi.get().at(player.level(), player.blockPosition());
		}
		if (view == null) {
			Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_aula")).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		BlockPos o = view.origin();
		player.sendSystemMessage(Component.translatable("kultivator.sekte_langit.uji.info", view.template(), view.tier(), view.tethered() ? "ya" : "tidak",
				view.ascent(), view.yP(), view.skyFloorY(), o.getX() + " " + o.getY() + " " + o.getZ()).withStyle(ChatFormatting.AQUA));
		int sectionFloor = ((view.skyFloorY() >> 4) << 4);
		player.sendSystemMessage(Component.translatable("kultivator.sekte_langit.uji.info2", box(view.islandBox()), box(view.volume()),
				view.kakiBox() == null ? "-" : box(view.kakiBox()), sectionFloor, view.grandFormation() ? "ya" : "tidak",
				view.gateClosed() ? "ya" : "tidak").withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal(LangitTestHooks.eventInfo(player, view.owner())).withStyle(ChatFormatting.DARK_GRAY));
		return 1;
	}

	private static String box(net.minecraft.world.level.levelgen.structure.BoundingBox b) {
		return b.minX() + "," + b.minY() + "," + b.minZ() + " .. " + b.maxX() + "," + b.maxY() + "," + b.maxZ();
	}

	private static int teleport(ServerPlayer player, String where, int index) {
		UUID owner = player.getUUID();
		Map<String, BlockPos> anchors = LangitApi.get().anchors(owner);
		BlockPos target = switch (where) {
			case "tahta" -> anchors.getOrDefault("arrival", anchors.get("tahta"));
			case "kaki" -> anchors.getOrDefault("tangga_kaki", anchors.get("arrival_kaki"));
			case "puncak" -> anchors.get("tangga_puncak");
			case "feri" -> anchors.get("feri_atas");
			default -> {
				BlockPos found = null;
				int i = 0;
				for (Map.Entry<String, BlockPos> entry : anchors.entrySet()) {
					if (entry.getKey().startsWith("dermaga_") && i++ == index) {
						found = entry.getValue();
					}
				}
				yield found;
			}
		};
		if (target == null) {
			Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.tanpa_jangkar", where)).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		player.teleportTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
		player.resetFallDistance();
		return 1;
	}

	private static int fall(ServerPlayer player, int height, boolean dive) {
		SkyHallView view = LangitApi.get().skyHall(player.getUUID());
		if (view == null) {
			view = LangitApi.get().at(player.level(), player.blockPosition());
		}
		if (view == null) {
			Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.peristiwa.tanpa_aula")).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		Vec3 out = outsideEdge(view, player.position());
		player.teleportTo(out.x, view.skyFloorY() - height, out.z);
		player.setDeltaMovement(0.0D, -1.0D, 0.0D);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
		Msg.chat(player, Msg.prefixed(Component.translatable(dive ? "kultivator.sekte_langit.uji.jatuh_terjun" : "kultivator.sekte_langit.uji.jatuh", height))
				.withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int fallDisciple(ServerPlayer player) {
		SkyHallView view = LangitApi.get().skyHall(player.getUUID());
		ServerLevel level = player.level();
		DiscipleEntity nearest = null;
		double best = 64.0D * 64.0D;
		for (DiscipleEntity disciple : level.getEntitiesOfClass(DiscipleEntity.class, player.getBoundingBox().inflate(64.0D),
				d -> player.getUUID().equals(d.ownerId()))) {
			double d = disciple.distanceToSqr(player);
			if (d < best) {
				best = d;
				nearest = disciple;
			}
		}
		if (view == null || nearest == null) {
			Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.tanpa_murid")).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		Vec3 out = outsideEdge(view, nearest.position());
		nearest.teleportTo(out.x, view.skyFloorY() - 4.0D, out.z);
		nearest.setDeltaMovement(0.0D, -1.0D, 0.0D);
		return 1;
	}

	/** A point 4 blocks outside the island box, on the side of {@code from}. */
	private static Vec3 outsideEdge(SkyHallView view, Vec3 from) {
		Vec3 centre = Vec3.atBottomCenterOf(view.origin());
		Vec3 dir = from.subtract(centre).multiply(1.0D, 0.0D, 1.0D);
		if (dir.lengthSqr() < 1.0E-3D) {
			dir = new Vec3(1.0D, 0.0D, 0.0D);
		}
		dir = dir.normalize();
		double half = Math.max(view.islandBox().getXSpan(), view.islandBox().getZSpan()) * 0.5D;
		return centre.add(dir.scale(half + 4.0D));
	}

	private static int formasi(ServerPlayer player) {
		int n = 0;
		for (CoreView core : Cores.of(player.getUUID())) {
			if ("awan_transfer".equals(core.spec()) || "jaring_awan".equals(core.spec())) {
				n++;
				player.sendSystemMessage(Component.translatable("kultivator.sekte_langit.uji.formasi", core.spec(),
						core.eye().getX() + " " + core.eye().getY() + " " + core.eye().getZ(), core.state().name()).withStyle(ChatFormatting.GRAY));
			}
		}
		if (n == 0) {
			Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.formasi_kosong")).withStyle(ChatFormatting.YELLOW));
		}
		return n;
	}

	// ================================================================== NPC sky sects

	private static int npcList(ServerPlayer player) {
		ServerLevel level = player.level().getServer().overworld();
		List<LangitNpcPlan.Istana> list = LangitNpcPlan.of(level.getSeed());
		Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.npc_jumlah", list.size())).withStyle(ChatFormatting.GOLD));
		for (LangitNpcPlan.Istana istana : list) {
			BlockPos c = istana.column();
			boolean old = level.hasChunk(istana.chunkX(), istana.chunkZ()) && !generatedWith(level, istana);
			player.sendSystemMessage(Component.translatable("kultivator.sekte_langit.uji.npc_baris", istana.index(), LangitTestHooks.npcName(istana),
					istana.gate().tier(), c.getX() + " " + c.getZ(), Component.translatable(old ? "kultivator.sekte_langit.uji.npc_lama"
							: "kultivator.sekte_langit.uji.npc_baru")).withStyle(ChatFormatting.GRAY));
		}
		return list.size();
	}

	/** True when one of the palace's possible centres (loaded) has its top above the cloud line. */
	private static boolean generatedWith(ServerLevel level, LangitNpcPlan.Istana istana) {
		return palaceTop(level, istana, false) != null;
	}

	/** The highest palace column top among the four possible centres (loading them when {@code load}), or null. */
	private static @Nullable BlockPos palaceTop(ServerLevel level, LangitNpcPlan.Istana istana, boolean load) {
		BlockPos best = null;
		for (BlockPos c : istana.centres()) {
			if (!load && !level.hasChunk(c.getX() >> 4, c.getZ() >> 4)) {
				continue;
			}
			if (load) {
				level.getChunk(c.getX() >> 4, c.getZ() >> 4);
			}
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getX(), c.getZ());
			if (top >= LangitNpcPlan.Y_MIN - 30 && (best == null || top > best.getY())) {
				best = new BlockPos(c.getX(), top, c.getZ());
			}
		}
		return best;
	}

	private static int npcNearest(ServerPlayer player) {
		ServerLevel level = player.level().getServer().overworld();
		LangitNpcPlan.Istana istana = LangitNpcPlan.near(level.getSeed(), player.getX(), player.getZ(), 1.0E7D);
		if (istana == null) {
			Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.npc_jumlah", 0)).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		BlockPos c = istana.column();
		int distance = (int) Math.sqrt(player.distanceToSqr(c.getX() + 0.5D, player.getY(), c.getZ() + 0.5D));
		Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.npc_dekat", LangitTestHooks.npcName(istana),
				c.getX() + " " + c.getZ(), distance)).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int npcTeleport(ServerPlayer player, int index) {
		ServerLevel level = player.level().getServer().overworld();
		LangitNpcPlan.Istana istana = LangitNpcPlan.get(level.getSeed(), index);
		if (istana == null || player.level() != level) {
			Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.npc_jumlah", 0)).withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		// loads (and in a new world generates) the four possible centres, then stands on the palace top or high above
		BlockPos top = palaceTop(level, istana, true);
		BlockPos c = top != null ? top : istana.column();
		double y = top != null ? top.getY() + 1.0D : LangitNpcPlan.Y_MAX + 20.0D;
		player.teleportTo(c.getX() + 0.5D, y, c.getZ() + 0.5D);
		player.resetFallDistance();
		if (top == null) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 400, 0));
			Msg.chat(player, Msg.prefixed(Component.translatable("kultivator.sekte_langit.uji.npc_lama")).withStyle(ChatFormatting.YELLOW));
		}
		return 1;
	}

	private LangitTests() {
	}
}
