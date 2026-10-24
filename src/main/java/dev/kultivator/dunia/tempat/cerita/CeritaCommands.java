package dev.kultivator.dunia.tempat.cerita;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.kultivator.dunia.DuniaCommands;
import dev.kultivator.dunia.DuniaSites;
import dev.kultivator.dunia.kota.KultivatorNpc;
import dev.kultivator.dunia.kota.NpcRole;
import dev.kultivator.dunia.tempat.TempatApi;
import dev.kultivator.dunia.tempat.hidup.HidupApi;
import dev.kultivator.dunia.tempat.ui.LembarKunci;
import dev.kultivator.dunia.tempat.ui.TempatLayar;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /kultivasi uji cerita} (DESIGN 38.D.10, op, through {@code DuniaCommands.test}):
 * <pre>
 * dialog &lt;id&gt;                 open a dialog with the nearest NPC (or a fresh body of its speaker)
 * quest &lt;id&gt; [tahap]         start a quest / jump to a stage (selesai, gagal too)
 * quest &lt;id&gt; maju|lupa       finish the current stage / forget the record
 * bendera &lt;id&gt; [on|off]      show or set a story flag
 * rumor [tambah &lt;templat&gt;]   live rumours here / spread one
 * tokoh [panggil &lt;id&gt;]       where the recurring characters were last seen / call one here
 * alur &lt;bab&gt;                 jump the capital arc here (or the nearest) to a chapter
 * jurnal                     open the journal
 * </pre>
 */
final class CeritaCommands {

	private static final SuggestionProvider<CommandSourceStack> DIALOGS = (ctx, b) -> SharedSuggestionProvider.suggest(TempatDialog.dialogIds(), b);
	private static final SuggestionProvider<CommandSourceStack> QUESTS = (ctx, b) -> {
		List<String> ids = new ArrayList<>();
		for (Quest q : CeritaReload.quests()) {
			ids.add(q.id());
		}
		return SharedSuggestionProvider.suggest(ids, b);
	};
	private static final SuggestionProvider<CommandSourceStack> TOKOH = (ctx, b) -> SharedSuggestionProvider.suggest(Tokoh.ids(), b);
	private static final SuggestionProvider<CommandSourceStack> TEMPLATES = (ctx, b) -> SharedSuggestionProvider.suggest(CeritaReload.kabar().keySet(), b);

	static void init() {
		DuniaCommands.test("cerita", CeritaCommands::node);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("cerita")
				.then(Commands.literal("dialog").then(Commands.argument("id", StringArgumentType.greedyString()).suggests(DIALOGS)
						.executes(CeritaCommands::dialog)))
				.then(Commands.literal("quest").then(Commands.argument("id", StringArgumentType.word()).suggests(QUESTS)
						.executes(ctx -> quest(ctx, null))
						.then(Commands.argument("tahap", StringArgumentType.word()).executes(ctx -> quest(ctx, StringArgumentType.getString(ctx, "tahap"))))))
				.then(Commands.literal("bendera").then(Commands.argument("id", StringArgumentType.word())
						.executes(ctx -> bendera(ctx, null))
						.then(Commands.literal("on").executes(ctx -> bendera(ctx, true)))
						.then(Commands.literal("off").executes(ctx -> bendera(ctx, false)))))
				.then(Commands.literal("rumor").executes(CeritaCommands::rumor)
						.then(Commands.literal("tambah").then(Commands.argument("templat", StringArgumentType.word()).suggests(TEMPLATES)
								.executes(CeritaCommands::rumorTambah))))
				.then(Commands.literal("tokoh").executes(CeritaCommands::tokoh)
						.then(Commands.literal("panggil").then(Commands.argument("id", StringArgumentType.word()).suggests(TOKOH)
								.executes(CeritaCommands::panggil))))
				.then(Commands.literal("alur").then(Commands.argument("bab", IntegerArgumentType.integer(1, AlurTakhta.BAB_TERAKHIR))
						.executes(CeritaCommands::alur)))
				.then(Commands.literal("jurnal").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					TempatLayar.buka(player, LembarKunci.Jurnal.LAYAR, -1, JurnalCerita.lembar(player));
					return 1;
				}));
	}

	private static int dialog(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id");
		Dialog dialog = CeritaReload.dialog(id);
		if (dialog == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.cerita.uji.dialog_tidak_ada", id));
			return 0;
		}
		KultivatorNpc npc = speaker(player, dialog);
		if (npc == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.cerita.uji.tanpa_npc"));
			return 0;
		}
		return TempatDialog.buka(player, npc, id) ? 1 : 0;
	}

	/** The nearest NPC that may say the dialog, else a fresh body of its first speaker in front of the player. */
	private static @Nullable KultivatorNpc speaker(ServerPlayer player, Dialog dialog) {
		for (KultivatorNpc npc : player.level().getEntitiesOfClass(KultivatorNpc.class, new AABB(player.blockPosition()).inflate(8.0D), KultivatorNpc::isAlive)) {
			if (dialog.pembicara(Tokoh.dari(npc), npc.role().id())) {
				return npc;
			}
		}
		for (String who : dialog.pembicara()) {
			if (who.startsWith("tokoh:")) {
				KultivatorNpc body = panggilTokoh(player, who.substring(6));
				if (body != null) {
					return body;
				}
			} else if (who.startsWith("peran:")) {
				NpcRole role = role(who.substring(6));
				if (role != null) {
					return body(player, role);
				}
			}
		}
		return null;
	}

	private static @Nullable NpcRole role(String id) {
		for (NpcRole r : NpcRole.values()) {
			if (r.id().equals(id)) {
				return r;
			}
		}
		return null;
	}

	private static @Nullable KultivatorNpc body(ServerPlayer player, NpcRole role) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		double angle = player.getYRot() * Math.PI / 180.0D;
		int x = (int) Math.floor(player.getX() - Math.sin(angle) * 3.0D);
		int z = (int) Math.floor(player.getZ() + Math.cos(angle) * 3.0D);
		BlockPos ground = TempatApi.groundSpot(level, x, z, player.blockPosition().getY());
		long town = TempatDialog.situs(player);
		return HidupApi.tubuh(level, town, role, ground == null ? player.blockPosition() : ground, player.getYRot() + 180.0F);
	}

	private static @Nullable KultivatorNpc panggilTokoh(ServerPlayer player, String id) {
		Tokoh.Data data = Tokoh.byId(id);
		if (data == null) {
			return null;
		}
		KultivatorNpc body = body(player, data.peran());
		if (body != null) {
			Tokoh.tandai(body, id);
		}
		return body;
	}

	private static int quest(CommandContext<CommandSourceStack> ctx, @Nullable String stage) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id");
		Quest quest = CeritaReload.quest(id);
		if (quest == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.cerita.uji.quest_tidak_ada", id));
			return 0;
		}
		boolean ok;
		if (stage == null) {
			ok = QuestTracker.mulai(player, id, TempatDialog.situs(player));
		} else if ("maju".equals(stage)) {
			ok = QuestTracker.maju(player, id);
		} else if ("lupa".equals(stage)) {
			QuestTracker.lupakan(player, id);
			ok = true;
		} else {
			ok = QuestTracker.lompat(player, id, stage);
		}
		int now = QuestTracker.tahap(player, id);
		String node = QuestTracker.simpul(player, id);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.cerita.uji.quest", id, now, node), false);
		return ok ? 1 : 0;
	}

	private static int bendera(CommandContext<CommandSourceStack> ctx, @Nullable Boolean set) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id");
		CeritaData data = CeritaData.get(ctx.getSource().getServer());
		if (set != null) {
			data.setFlag(player.getUUID(), id, set);
		}
		boolean on = data.flag(player.getUUID(), id);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.cerita.uji.bendera", id,
				Component.translatable(on ? "kultivator.cerita.uji.ya" : "kultivator.cerita.uji.tidak")), false);
		return 1;
	}

	private static int rumor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		long site = TempatDialog.situs(player);
		List<Component> all = KabarTempat.semua(player, site);
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.cerita.uji.rumor", all.size(), Long.toHexString(site)), false);
		for (Component line : all) {
			ctx.getSource().sendSuccess(() -> Component.literal(" - ").append(line), false);
		}
		return all.size();
	}

	private static int rumorTambah(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "templat");
		KabarTempat.kabar(ctx.getSource().getLevel(), TempatDialog.situs(player), id, player.getName().getString());
		return rumor(ctx);
	}

	private static int tokoh(CommandContext<CommandSourceStack> ctx) {
		int seen = 0;
		for (String id : Tokoh.ids()) {
			Tokoh.Jejak where = Tokoh.jejak(id);
			Component name = Tokoh.nama(ctx.getSource().getServer(), id, where == null ? 0L : where.situs());
			if (where == null) {
				ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.cerita.uji.tokoh_belum", name, id), false);
			} else {
				seen++;
				ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.cerita.uji.tokoh", name, id, where.pos().getX(), where.pos().getY(),
						where.pos().getZ(), where.dimensi().identifier().toString()), false);
			}
		}
		return seen;
	}

	private static int panggil(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id");
		KultivatorNpc body = panggilTokoh(player, id);
		if (body == null) {
			ctx.getSource().sendFailure(Component.translatable("kultivator.cerita.uji.tanpa_npc"));
			return 0;
		}
		return 1;
	}

	private static int alur(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int bab = IntegerArgumentType.getInteger(ctx, "bab");
		long site = AlurTakhta.ibuKotaDi(player);
		if (site == 0L) {
			site = AlurTakhta.terdekat(player);
		}
		if (site == 0L) {
			// no capital yet (tests before 38KS): the player's spot stands in for one
			site = DuniaSites.idOf(player.level().dimension(), player.blockPosition(), "kota/kota_besar");
		}
		AlurTakhta.tiba(player, site);
		AlurTakhta.lompat(ctx.getSource().getServer(), site, bab);
		long finalSite = site;
		ctx.getSource().sendSuccess(() -> Component.translatable("kultivator.cerita.uji.alur", bab, Long.toHexString(finalSite)), true);
		return 1;
	}

	private CeritaCommands() {
	}
}
