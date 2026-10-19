package dev.kultivator.batin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.kultivator.KultivatorConfig;
import dev.kultivator.abadi.Abadi;
import dev.kultivator.alam.Alam;
import dev.kultivator.ascension.Ascension;
import dev.kultivator.ascension.Stars;
import dev.kultivator.cultivation.Tribulation;
import dev.kultivator.data.CultivationData;
import dev.kultivator.data.Cultivators;
import dev.kultivator.data.Dao;
import dev.kultivator.musuh.SectRaids;
import dev.kultivator.network.Payloads;
import dev.kultivator.purba.Purba;
import dev.kultivator.technique.spec.run.Codex;
import dev.kultivator.util.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dunia Batin, Jurus Ciptaan Sendiri and Jalan Abadi (DESIGN 26.6, 26.7, 26.9; owned by P9-batin): the inner world born at
 * the ascension to Alam Abadi ({@link InnerWorld}, {@link LandSpirit}, {@link InnerCalamity}; pure data, zero ticking of its
 * own), the three self-created arts ({@link Creation}, {@link CiptaanArts}, {@link OwnArt}) and, in {@code goals.*}, Jalan
 * chapters 11-15 with the new daily chores. The Batin and Ciptaan tablets read the {@code DuniaSync.CH_BATIN} data.
 * <p>
 * Sumber: Reverend Insanity ch. 409 "Managing the Blessed Land"; I Shall Seal the Heavens ch. 1379; Battle Through the
 * Heavens ch. 262 and ch. 363; Coiling Dragon. Every number is game design.
 */
public final class Batin {

	/** Sub-compound of {@code CultivationData.extra()} owned by this module (DESIGN 26.13). */
	public static final String EXTRA_KEY = "batin";

	private static final Set<UUID> DIRTY = new HashSet<>();

	// ================================================================== init and entry points

	/**
	 * Registers the module's items, entity, listeners and name providers; called from {@code Kultivator.onInitialize} before
	 * {@code ModItems.init()}. ({@link CiptaanArts} is registered by {@code TechniqueRegistry} itself.)
	 */
	public static void init() {
		BatinItems.init();
		FabricDefaultAttributeRegistry.register(BatinItems.METEOR, BatinMeteorEntity.createAttributes());
		InnerCalamity.init();
		Creation.init();
		Abadi.addAscendListener(player -> {
			if (KultivatorConfig.get().innerWorld) {
				InnerWorld.birth(player, -1, false);
			}
		});
		Codex.addNameProvider(Batin::ownArtName);
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, flag) -> reason != EntitySpawnReason.LOAD
				|| !entity.entityTags().contains(InnerCalamity.TAG));
		ServerPlayerEvents.JOIN.register(player -> {
			// An Immortal who ascended before the inner world existed gets it now.
			if (KultivatorConfig.get().innerWorld && !has(player) && Abadi.ascended(player)) {
				InnerWorld.birth(player, -1, false);
			}
			InnerWorld.catchUp(player);
			dirty(player);
		});
		ServerPlayerEvents.LEAVE.register(player -> DIRTY.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DIRTY.clear());
		ServerTickEvents.END_SERVER_TICK.register(Batin::tickServer);
	}

	private static void tickServer(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % 100 == 37 && KultivatorConfig.get().innerWorld) {
			for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
				if (!has(player)) {
					continue;
				}
				if (InnerWorld.catchUp(player) > 0) {
					Cultivators.dirty(player);
				}
				InnerCalamity.check(player);
			}
		}
		if (tick % 10 == 4 && !DIRTY.isEmpty()) {
			for (UUID id : List.copyOf(DIRTY)) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player != null) {
					Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_BATIN, syncTag(player));
				}
			}
			DIRTY.clear();
		}
	}

	/** Marks the player's Batin data for a resync (sent within half a second). */
	static void dirty(ServerPlayer player) {
		DIRTY.add(player.getUUID());
	}

	/** True while the player is in a run, raid, tribulation, ascension, calamity, primordial fight, wrath, defence or ritual. */
	static boolean busy(ServerPlayer player) {
		return Alam.inRun(player) || SectRaids.active(player.getUUID()) || Tribulation.isActive(player) || Ascension.isActive(player)
				|| Purba.inCalamity(player) || Purba.inFight(player) || Purba.inWrath(player) || Abadi.inAscension(player)
				|| InnerCalamity.active(player.getUUID()) || Creation.inRitual(player);
	}

	/** Action {@code BATIN}: text = "op[:param]"; "sync" answers with the {@code DuniaSync.CH_BATIN} data. */
	public static void handleAction(ServerPlayer player, String text) {
		String op = text == null ? "" : text;
		if ("sync".equals(op)) {
			if (has(player)) {
				InnerWorld.catchUp(player);
			}
			Payloads.DuniaSync.send(player, Payloads.DuniaSync.CH_BATIN, syncTag(player));
			return;
		}
		if (!KultivatorConfig.get().innerWorld) {
			Msg.chat(player, Component.translatable("kultivator.batin.off").withStyle(ChatFormatting.GRAY));
			return;
		}
		String[] parts = op.split(":", 7);
		try {
			switch (parts[0]) {
				case "plant" -> InnerWorld.plant(player, Integer.parseInt(parts[1]));
				case "harvest" -> InnerWorld.harvest(player, Integer.parseInt(parts[1]));
				case "brew" -> InnerWorld.brew(player, parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
				case "fortify" -> InnerWorld.fortify(player);
				case "expand" -> InnerWorld.expand(player);
				case "withdraw" -> InnerWorld.withdraw(player, parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
				case "feed" -> InnerWorld.feed(player);
				case "take" -> InnerWorld.takeOutput(player);
				case "create" -> {
					if (parts.length == 7) {
						Creation.start(player, Integer.parseInt(parts[1]), Dao.byOrdinal(Integer.parseInt(parts[2])), Dao.byOrdinal(Integer.parseInt(parts[3])),
								Creation.Form.byOrdinal(Integer.parseInt(parts[4])), "1".equals(parts[5]), parts[6]);
					}
				}
				case "refine" -> Creation.startRefine(player, Integer.parseInt(parts[1]));
				case "delete" -> Creation.destroy(player, Integer.parseInt(parts[1]), true);
				case "cancel" -> Creation.cancel(player);
				default -> {
				}
			}
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException exception) {
			// A malformed action from a modified client: nothing to do.
		}
		dirty(player);
	}

	/** The data of the Batin and Ciptaan tablets (DESIGN 26.10), ≤ 8 KB. */
	static CompoundTag syncTag(ServerPlayer player) {
		CompoundTag tag = new CompoundTag();
		KultivatorConfig config = KultivatorConfig.get();
		CultivationData data = Cultivators.get(player);
		CompoundTag batin = InnerWorld.view(data);
		boolean born = InnerWorld.born(data);
		long gameTime = player.level().getServer().overworld().getGameTime();
		long day = gameTime / 24000L;
		tag.putBoolean("on", config.innerWorld);
		tag.putBoolean("born", born);
		tag.putBoolean("ascended", Abadi.ascended(player));
		tag.putLong("day", day);
		if (born) {
			int grade = InnerWorld.grade(batin);
			tag.putInt("grade", grade);
			ListTag plots = new ListTag();
			for (int i = 0; i < batin.getListOrEmpty("plots").size(); i++) {
				CompoundTag plot = batin.getListOrEmpty("plots").getCompoundOrEmpty(i);
				CompoundTag out = new CompoundTag();
				String kind = plot.getStringOr("k", "");
				out.putString("k", kind);
				out.putInt("a", plot.getIntOr("a", 0));
				out.putInt("s", plot.getIntOr("s", InnerWorld.STATE_OK));
				out.putLong("u", Math.max(0L, plot.getLongOr("u", 0L) - day));
				if (!kind.isEmpty()) {
					ItemStack stack = InnerWorld.herbStack(kind, 1);
					out.putString("n", stack.isEmpty() ? "" : stack.getItem().getDescriptionId());
					out.putInt("c", InnerWorld.herbColor(kind));
				}
				plots.add(out);
			}
			tag.put("plots", plots);
			tag.putInt("max_plots", InnerWorld.MAX_PLOTS);
			int foxes = batin.getIntOr("foxes", 0);
			tag.putInt("foxes", foxes);
			tag.putInt("fox_cap", InnerWorld.foxCap(batin));
			tag.putInt("fert", (int) Math.round(InnerWorld.fertility(foxes) * 100.0D));
			tag.putInt("pearls", batin.getIntOr("pearls", 0));
			tag.putInt("pearl_items", InnerWorld.count(player, BatinItems.IMMORTAL_ESSENCE_PEARL));
			tag.putInt("pearl_rate", InnerWorld.GRADE_PEARLS[grade]);
			tag.putInt("wd", batin.getLongOr("wd_day", -1L) == day ? batin.getIntOr("wd_n", 0) : 0);
			tag.putInt("brew", batin.getLongOr("br_day", -1L) == day ? batin.getIntOr("br_n", 0) : 0);
			tag.putInt("harvests", batin.getIntOr("harvests", 0));
			CompoundTag rock = batin.getCompoundOrEmpty("rock").copy();
			tag.put("rock", rock);
			CompoundTag cal = new CompoundTag();
			CompoundTag stored = batin.getCompoundOrEmpty("cal");
			int n = Math.max(1, stored.getIntOr("n", 1));
			long due = stored.getLongOr("due", -1L);
			cal.putInt("n", n);
			cal.putInt("type", InnerCalamity.typeOf(n));
			cal.putBoolean("off", config.innerWorldCalamityDays <= 0 || due < 0L);
			cal.putLong("in", due < 0L ? -1L : Math.max(0L, due * 24000L - gameTime));
			cal.putInt("fortify", stored.getIntOr("fortify", 0));
			cal.putInt("chance", (int) Math.round(InnerCalamity.surviveChance(batin) * 100.0D));
			cal.putInt("live", InnerCalamity.liveType(player.getUUID()));
			cal.putInt("live_left", InnerCalamity.ticksLeft(player));
			tag.put("cal", cal);
			tag.putInt("spirit", batin.getIntOr("spirit", 0));
			tag.put("log", batin.getListOrEmpty("log").copy());
			tag.putInt("batu", InnerWorld.count(player, InnerWorld.registryItem(InnerWorld.BATU_ID)));
		}
		// The Ciptaan tablet: the three slots and the creator's live numbers.
		ListTag arts = new ListTag();
		for (int slot = 0; slot < CiptaanArts.SLOTS; slot++) {
			Creation.Art art = Creation.art(data, slot);
			CompoundTag entry = new CompoundTag();
			if (art != null) {
				entry.putString("name", art.name());
				entry.putInt("a", art.a().ordinal());
				entry.putInt("b", art.b().ordinal());
				entry.putInt("f", art.form().ordinal());
				entry.putInt("q", (int) Math.round(art.q() * 100.0D));
			}
			arts.add(entry);
		}
		tag.put("ciptaan", arts);
		CompoundTag creator = new CompoundTag();
		creator.putBoolean("peak", data.rank().isMax());
		int[] comp = new int[Dao.values().length];
		for (Dao dao : Dao.values()) {
			comp[dao.ordinal()] = (int) Math.floor(data.comprehension(dao));
		}
		creator.putIntArray("comp", comp);
		creator.putInt("kinds", Purba.distinctKinds(player));
		double bar = Stars.barSize(player);
		creator.putDouble("qi", data.storedQi());
		creator.putDouble("cost_qi", bar * Creation.QI_SHARE);
		creator.putDouble("refine_qi", bar * Creation.REFINE_QI_SHARE);
		creator.putInt("pearls", InnerWorld.pearlsAvailable(player));
		creator.putInt("material", InnerWorld.countTag(player, InnerWorld.PRIMORDIAL_MATERIAL));
		creator.putInt("ritual", Creation.ritualSeconds(player));
		tag.put("creator", creator);
		return tag;
	}

	/** {@code /kultivasi batin info|seduh <n>}, {@code ciptaan daftar|hapus <1-3>} and the debug nodes. */
	public static void addCommands(LiteralArgumentBuilder<CommandSourceStack> root, LiteralArgumentBuilder<CommandSourceStack> debug) {
		root.then(Commands.literal("batin")
				.executes(ctx -> info(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("info").executes(ctx -> info(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("seduh")
						.then(Commands.argument("n", IntegerArgumentType.integer(1, InnerWorld.BREW_PER_DAY))
								.executes(ctx -> {
									InnerWorld.brew(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n"));
									return 1;
								}))));
		root.then(Commands.literal("ciptaan")
				.executes(ctx -> listArts(ctx.getSource().getPlayerOrException()))
				.then(Commands.literal("daftar").executes(ctx -> listArts(ctx.getSource().getPlayerOrException())))
				.then(Commands.literal("hapus")
						.then(Commands.argument("slot", IntegerArgumentType.integer(1, CiptaanArts.SLOTS))
								.executes(ctx -> Creation.destroy(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot") - 1, true)
										? 1 : 0))));
		debug.then(Commands.literal("batin").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			ctx.getSource().sendSuccess(() -> Component.literal(InnerWorld.view(Cultivators.get(player)).toString()), false);
			return 1;
		}));
		debug.then(Commands.literal("ciptaan")
				.then(Commands.argument("slot", IntegerArgumentType.integer(1, CiptaanArts.SLOTS))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							Creation.Art art = Creation.art(player, IntegerArgumentType.getInteger(ctx, "slot") - 1);
							if (art == null) {
								ctx.getSource().sendFailure(Msg.prefixed(Component.translatable("kultivator.batin.ciptaan.empty_slot")));
								return 0;
							}
							for (Component line : OwnArt.describe(art)) {
								ctx.getSource().sendSuccess(() -> line, false);
							}
							return 1;
						})));
	}

	/** {@code batin info}: grade, plots, foxes, pearls, rockmen and the next calamity in chat. */
	private static int info(ServerPlayer player) {
		CultivationData data = Cultivators.get(player);
		if (!InnerWorld.born(data)) {
			Msg.chat(player, Component.translatable("kultivator.batin.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		InnerWorld.catchUp(player);
		CompoundTag batin = InnerWorld.view(data);
		int grade = InnerWorld.grade(batin);
		ListTag plots = batin.getListOrEmpty("plots");
		int planted = 0;
		for (int i = 0; i < plots.size(); i++) {
			if (!plots.getCompoundOrEmpty(i).getStringOr("k", "").isEmpty()) {
				planted++;
			}
		}
		Msg.chat(player, Component.translatable("kultivator.batin.info.head", LandSpirit.name(batin),
				Component.translatable("kultivator.batin.grade." + InnerWorld.GRADES[grade])).withStyle(ChatFormatting.GOLD));
		Msg.chat(player, Component.translatable("kultivator.batin.info.plots", planted, plots.size(), InnerWorld.usablePlots(plots))
				.withStyle(ChatFormatting.WHITE));
		int foxes = batin.getIntOr("foxes", 0);
		Msg.chat(player, Component.translatable("kultivator.batin.info.foxes", foxes, InnerWorld.foxCap(batin),
				Math.round(InnerWorld.fertility(foxes) * 100.0D)).withStyle(ChatFormatting.WHITE));
		Msg.chat(player, Component.translatable("kultivator.batin.info.pearls", batin.getIntOr("pearls", 0), InnerWorld.PEARL_CAP,
				InnerWorld.GRADE_PEARLS[grade]).withStyle(ChatFormatting.WHITE));
		CompoundTag rock = batin.getCompoundOrEmpty("rock");
		Msg.chat(player, rock.getBooleanOr("awake", false)
				? Component.translatable("kultivator.batin.info.rock", rock.getIntOr("i", 0) + rock.getIntOr("g", 0) + rock.getIntOr("d", 0),
						InnerWorld.ROCK_BUFFER, rock.getIntOr("mid", 0), rock.getIntOr("high", 0), rock.getIntOr("batu", 0)).withStyle(ChatFormatting.WHITE)
				: Component.translatable("kultivator.batin.info.rock_sleep", rock.getIntOr("wake", 0), InnerWorld.ROCK_WAKE).withStyle(ChatFormatting.GRAY));
		CompoundTag cal = batin.getCompoundOrEmpty("cal");
		long due = cal.getLongOr("due", -1L);
		if (due < 0L || KultivatorConfig.get().innerWorldCalamityDays <= 0) {
			Msg.chat(player, Component.translatable("kultivator.batin.info.cal_off").withStyle(ChatFormatting.GRAY));
		} else {
			int n = Math.max(1, cal.getIntOr("n", 1));
			long gameTime = player.level().getServer().overworld().getGameTime();
			long years = Math.max(0L, (due * 24000L - gameTime) * InnerWorld.YEARS_PER_DAY / 24000L);
			Msg.chat(player, Component.translatable("kultivator.batin.info.cal", n,
					Component.translatable("kultivator.batin.cal.type." + InnerCalamity.TYPE_IDS[InnerCalamity.typeOf(n)]), years,
					cal.getIntOr("fortify", 0), Math.round(InnerCalamity.surviveChance(batin) * 100.0D)).withStyle(ChatFormatting.YELLOW));
		}
		dirty(player);
		return 1;
	}

	/** {@code ciptaan daftar}: the three slots in chat. */
	private static int listArts(ServerPlayer player) {
		Msg.chat(player, Component.translatable("kultivator.batin.ciptaan.list_head").withStyle(ChatFormatting.GOLD));
		for (int slot = 0; slot < CiptaanArts.SLOTS; slot++) {
			Creation.Art art = Creation.art(player, slot);
			if (art == null) {
				Msg.chat(player, Component.translatable("kultivator.batin.ciptaan.list_empty", slot + 1).withStyle(ChatFormatting.GRAY));
			} else {
				Msg.chat(player, Component.translatable("kultivator.batin.ciptaan.list_line", slot + 1, art.name(), art.a().displayName(),
						art.b().displayName(), art.form().displayName(), Math.round(art.q() * 100.0D)).withStyle(ChatFormatting.WHITE));
			}
		}
		return 1;
	}

	/** {@code /kultivasi uji batin|ciptaan ...} (DESIGN 26.11). */
	public static void addTestCommands(LiteralArgumentBuilder<CommandSourceStack> uji) {
		BatinTests.register(uji);
	}

	/** The module's part of the "Fase 9" test shulker ({@code uji kit}): 32 pearls and a land expansion token. */
	public static List<ItemStack> kitItems() {
		List<ItemStack> list = new ArrayList<>();
		list.add(new ItemStack(BatinItems.IMMORTAL_ESSENCE_PEARL, 32));
		list.add(new ItemStack(BatinItems.LAND_EXPANSION_TOKEN, 1));
		return list;
	}

	// ================================================================== public contract (26.15.2)

	/** True once the player's inner world was born. */
	public static boolean has(ServerPlayer player) {
		return InnerWorld.born(Cultivators.get(player));
	}

	/** True while a live earthly-calamity defence runs around the player. */
	public static boolean inDefence(ServerPlayer player) {
		return InnerCalamity.active(player.getUUID());
	}

	/** Immortal essence pearls in the inner world's counter (0-99). */
	public static int pearls(ServerPlayer player) {
		return InnerWorld.pearls(Cultivators.get(player));
	}

	/** Adds one plot (the {@code land_expansion_token}); true when added (born and below 16 plots). */
	public static boolean addPlot(ServerPlayer player) {
		return InnerWorld.addPlot(player);
	}

	/** The name the player gave the self-created art {@code specId} ({@code own_art_1..3}), or null. */
	public static @Nullable String ownArtName(ServerPlayer player, String specId) {
		int slot = CiptaanArts.slotOf(specId);
		if (slot < 0 || player == null) {
			return null;
		}
		Creation.Art art = Creation.art(player, slot);
		return art == null ? null : art.name();
	}

	private Batin() {
	}
}
